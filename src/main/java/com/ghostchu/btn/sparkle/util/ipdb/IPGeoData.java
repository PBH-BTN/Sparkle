package com.ghostchu.btn.sparkle.util.ipdb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class IPGeoData implements Serializable {
    private String cityName;
    private Long cityIso;
    private String cityCnProvince;
    private String cityCnCity;
    private String cityCnDistricts;
    private String countryIso;
    private Long asNumber;
    private String asNetworkIpAddress;
    private Integer asNetworkPrefixLength;
    private String netType;
    private String isp;
}
