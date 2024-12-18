package com.ghostchu.btn.sparkle.util.ipdb;


import com.ghostchu.btn.sparkle.util.HTTPUtil;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class IPDB implements AutoCloseable {
    private final long updateInterval = 2592000000L; // 30天
    private final File directory;
    private final File mmdbCityFile;
    private final File mmdbASNFile;
    private final boolean autoUpdate;
    private final String userAgent;
    private final File mmdbGeoCNFile;
    private Methanol httpClient;
    @Getter
    private DatabaseReader mmdbCity;
    @Getter
    private DatabaseReader mmdbASN;
    private Reader geoCN;
    private List<String> languageTag;

    public IPDB(boolean autoUpdate, String userAgent) throws IllegalArgumentException, IOException {
        this.directory = new File("geoip");
        this.directory.mkdirs();
        this.mmdbCityFile = new File(directory, "GeoIP-City.mmdb");
        this.mmdbASNFile = new File(directory, "GeoIP-ASN.mmdb");
        this.mmdbGeoCNFile = new File(directory, "GeoCN.mmdb");
        this.autoUpdate = autoUpdate;
        this.userAgent = userAgent;
        setupHttpClient();
        if (needUpdateMMDB(mmdbCityFile)) {
            updateMMDB("GeoLite2-City", mmdbCityFile);
        }
        if (needUpdateMMDB(mmdbASNFile)) {
            updateMMDB("GeoLite2-ASN", mmdbASNFile);
        }
        if (needUpdateMMDB(mmdbGeoCNFile)) {
            updateGeoCN(mmdbGeoCNFile);
        }
        loadMMDB();
    }

    @Cacheable(cacheNames = {"geoip#600000"}, key = "#address.hostAddress")
    public IPGeoData query(InetAddress address) {
        IPGeoData geoData = new IPGeoData();
        queryAS(address, geoData);
        queryCountry(address, geoData);
        queryCity(address, geoData);
        if (geoData.getCountryIso() != null) {
            String iso = geoData.getCountryIso();
            if (iso.equalsIgnoreCase("CN") || iso.equalsIgnoreCase("TW")
                    || iso.equalsIgnoreCase("HK") || iso.equalsIgnoreCase("MO")) {
                queryGeoCN(address, geoData);
            }
        }
        return geoData;
    }


    private void queryGeoCN(InetAddress address, IPGeoData geoData) {
        try {
            CNLookupResult cnLookupResult = geoCN.get(address, CNLookupResult.class);
            if (cnLookupResult == null) {
                return;
            }
            // City Data
            String cityName = (cnLookupResult.getProvince() + " " + cnLookupResult.getCity() + " " + cnLookupResult.getDistricts()).trim();
            if (!cityName.isBlank()) {
                geoData.setCityName(cityName);
            }

            Integer code = null;
            if (cnLookupResult.getProvinceCode() != null) {
                code = cnLookupResult.getProvinceCode().intValue();
            }
            if (cnLookupResult.getCityCode() != null) {
                code = cnLookupResult.getCityCode().intValue();
            }
            if (cnLookupResult.getDistrictsCode() != null) {
                code = cnLookupResult.getDistrictsCode().intValue();
            }
            geoData.setCityIso(Long.parseLong("86" + code));
            geoData.setCityCnProvince(cnLookupResult.getProvince());
            geoData.setCityCnCity(cnLookupResult.getCity());
            geoData.setCityCnDistricts(cnLookupResult.getDistricts());
            if (cnLookupResult.getNet() != null && !cnLookupResult.getNet().isBlank()) {
                geoData.setNetType(cnLookupResult.getNet());
            }
            if (cnLookupResult.getIsp() != null && !cnLookupResult.getIsp().isBlank()) {
                geoData.setIsp(cnLookupResult.getIsp());
            }
        } catch (Exception e) {
            log.error("Unable to execute IPDB query", e);
        }
    }


    private void queryCity(InetAddress address, IPGeoData data) {
        try {
            CityResponse cityResponse = mmdbCity.city(address);
            City city = cityResponse.getCity();
            data.setCityName(city.getName());
            data.setCityIso(city.getGeoNameId());
        } catch (Exception ignored) {
        }
    }

    private void queryCountry(InetAddress address, IPGeoData data) {
        try {
            CountryResponse countryResponse = mmdbCity.country(address);
            Country country = countryResponse.getCountry();
            data.setCountryIso(country.getIsoCode());
        } catch (Exception ignored) {
        }
    }


    private void queryAS(InetAddress address, IPGeoData data) {
        try {
            AsnResponse asnResponse = mmdbASN.asn(address);
            data.setAsNetworkPrefixLength(asnResponse.getNetwork().getPrefixLength());
            data.setAsNetworkIpAddress(asnResponse.getNetwork().getNetworkAddress().getHostAddress());
            data.setAsNumber(asnResponse.getAutonomousSystemNumber());
        } catch (Exception ignored) {
        }
    }

    private void updateGeoCN(File mmdbGeoCNFile) throws IOException {
        log.info("Updating database {}", "GeoCN (github.com/ljxi/GeoCN)");
        MutableRequest request = MutableRequest.GET("https://github.com/ljxi/GeoCN/releases/download/Latest/GeoCN.mmdb");
        Path tmp = Files.createTempFile("GeoCN", ".mmdb");
        downloadFile(request, tmp, "GeoCN").join();
        if (!tmp.toFile().exists()) {
            throw new IllegalStateException("Download mmdb database failed!");
        }
        Files.move(tmp, mmdbGeoCNFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }


    private void loadMMDB() throws IOException {
        this.languageTag = List.of("zh", "zh-CN", "en");
        this.mmdbCity = new DatabaseReader.Builder(mmdbCityFile)
                .locales(languageTag).build();
        this.mmdbASN = new DatabaseReader.Builder(mmdbASNFile)
                .locales(languageTag).build();
        this.geoCN = new Reader(mmdbGeoCNFile);
    }

    private void updateMMDB(String databaseName, File target) throws IOException {
        log.info("Downloading database {}", databaseName);
        MutableRequest request = MutableRequest.GET("https://github.com/P3TERX/GeoLite.mmdb/raw/download/" + databaseName + ".mmdb");
        Path tmp = Files.createTempFile(databaseName, ".mmdb");
        downloadFile(request, tmp, databaseName).join();
        if (!tmp.toFile().exists()) {
            throw new IllegalStateException("Download mmdb database failed!");
        }
        Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void setupHttpClient() {
        this.httpClient = Methanol
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(userAgent)
                .defaultHeader("Accept-Encoding", "gzip,deflate")
                .connectTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .build();
    }


    private boolean needUpdateMMDB(File target) {
        if (!target.exists()) {
            return true;
        }
        if (!autoUpdate) {
            return false;
        }
        return System.currentTimeMillis() - target.lastModified() > updateInterval;
    }

    private CompletableFuture<Void> downloadFile(MutableRequest req, Path path, String databaseName) {
        return HTTPUtil.retryableSendProgressTracking(httpClient, req, HttpResponse.BodyHandlers.ofFile(path))
                .thenAccept(r -> {
                    if (r.statusCode() != 200) {
                        log.error("下载 {} 失败：{}", databaseName, r.statusCode() + " - " + r.body());
                    } else {
                        log.info("下载 {} 成功", databaseName);
                    }
                })
                .exceptionally(e -> {
                    log.error("下载 {} 失败", databaseName, e);
                    File file = path.toFile();
                    if (file.exists()) {
                        file.delete(); // 删除下载不完整的文件
                    }
                    return null;
                });
    }

    @Override
    public void close() {
        if (this.mmdbCity != null) {
            try {
                this.mmdbCity.close();
            } catch (IOException ignored) {

            }
        }
        if (this.mmdbASN != null) {
            try {
                this.mmdbASN.close();
            } catch (IOException ignored) {

            }
        }
        if (this.geoCN != null) {
            try {
                this.geoCN.close();
            } catch (IOException ignored) {

            }
        }
    }

    @Getter
    @ToString
    public static class CNLookupResult {
        private final String isp;
        private final String net;
        private final String province;
        private final Long provinceCode;
        private final String city;
        private final Long cityCode;
        private final String districts;
        private final Long districtsCode;

        @MaxMindDbConstructor
        public CNLookupResult(
                @MaxMindDbParameter(name = "isp") String isp,
                @MaxMindDbParameter(name = "net") String net,
                @MaxMindDbParameter(name = "province") String province,
                @MaxMindDbParameter(name = "provinceCode") Object provinceCode,
                @MaxMindDbParameter(name = "city") String city,
                @MaxMindDbParameter(name = "cityCode") Object cityCode,
                @MaxMindDbParameter(name = "districts") String districts,
                @MaxMindDbParameter(name = "districtsCode") Object districtsCode
        ) {
            this.isp = isp;
            this.net = net;
            this.province = province;
            this.provinceCode = Long.parseLong(provinceCode.toString());
            this.city = city;
            this.cityCode = Long.parseLong(cityCode.toString());
            this.districts = districts;
            this.districtsCode = Long.parseLong(districtsCode.toString());
        }
    }

}
