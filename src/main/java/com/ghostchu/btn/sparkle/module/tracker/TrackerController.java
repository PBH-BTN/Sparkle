package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.util.BencodeUtil;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.IPUtil;
import inet.ipaddr.IPAddress;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.LockModeType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Controller
@Slf4j
public class TrackerController extends SparkleController {
    private static final Random random = new Random();
    @Autowired
    private HttpServletRequest req;
    @Autowired
    private TrackerService trackerService;
    @Value("${service.tracker.announce-interval}")
    private long announceInterval;
    @Value("${service.tracker.announce-random}")
    private long announceRandomOffset;
    @Value("${service.tracker.announce-retry}")
    private long announceBusyRetryInterval;
    @Value("${service.tracker.announce-retry-random}")
    private long announceBusyRetryRandomInterval;
    @Value("${service.tracker.id}")
    private String instanceTrackerId;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private StringRedisTemplate redisStringTemplate;

    public static String compactPeers(List<TrackerService.Peer> peers, boolean isV6) throws IllegalStateException {
        ByteBuffer buffer = ByteBuffer.allocate((isV6 ? 18 : 6) * peers.size());
        for (TrackerService.Peer peer : peers) {
            String ip = peer.ip();
            try {
                buffer.put(InetAddress.getByName(ip).getAddress());
                int in = peer.port();
                buffer.put((byte) ((in >>> 8) & 0xFF));
                buffer.put((byte) (in & 0xFF));
            } catch (UnknownHostException e) {
                throw new IllegalStateException("incorrect ip format encountered when compact peer ip");
            }
        }
        return new String(buffer.array(), StandardCharsets.ISO_8859_1);
    }

    /**
     * 套他猴子的 BitTorrent 总给我整花活
     *
     * @param queryString 查询字符串
     * @return 使用 ISO_8859_1 进行 URL 解码的 Info Hash 集合
     */
    public static List<byte[]> extractInfoHashes(String queryString) {
        List<byte[]> infoHashes = new ArrayList<>();
        if (queryString == null)
            throw new IllegalArgumentException("No queryString provided");
        String[] params = queryString.split("&");
        for (String param : params) {
            if (param.startsWith("info_hash=")) {
                String encodedHash = param.substring(10);
                byte[] decodedHash = URLDecoder.decode(encodedHash, StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.ISO_8859_1);
                infoHashes.add(decodedHash);
            }
        }

        return infoHashes;
    }

    @GetMapping("/tracker/announce")
    @ResponseBody
    @Lock(LockModeType.WRITE)
    public byte[] announceForward() {
        return announce();
    }

    @GetMapping("/announce")
    @ResponseBody
    @Lock(LockModeType.WRITE)
    public byte[] announce() {
        tickMetrics("announce_req", 1);
        if (req.getQueryString() == null) {
            tickMetrics("announce_req_fails", 1);
            return "Sorry, This is a BitTorrent Tracker, and access announce endpoint via Browser is disallowed and useless.".getBytes(StandardCharsets.UTF_8);
        }
        String userAgent = ua(req);
        if (userAgent != null) {
            if (userAgent.contains("Mozilla")
                || userAgent.contains("Chrome")
                || userAgent.contains("Firefox")
                || userAgent.contains("Safari")
                || userAgent.contains("Edge")
                || userAgent.contains("Opera")) {
                tickMetrics("announce_req_fails", 1);
                return "Sorry, This is a BitTorrent Tracker, and access announce endpoint via Browser is disallowed and useless.".getBytes(StandardCharsets.UTF_8);
            }
        }
        var infoHashes = extractInfoHashes(req.getQueryString());
        Validate.isTrue(infoHashes.size() == 1);
        byte[] infoHash = infoHashes.getFirst();
        Validate.isTrue(infoHash.length == 20);
        Validate.notBlank(req.getParameter("peer_id"));
        byte[] peerId = req.getParameter("peer_id").getBytes(StandardCharsets.ISO_8859_1);
        Validate.isTrue(StringUtils.isNumeric(req.getParameter("port")));
        int port = Integer.parseInt(req.getParameter("port"));
        Validate.isTrue(StringUtils.isNumeric(req.getParameter("uploaded")));
        long uploaded = Long.parseLong(req.getParameter("uploaded"));
        Validate.isTrue(StringUtils.isNumeric(req.getParameter("downloaded")));
        long downloaded = Long.parseLong(req.getParameter("downloaded"));
        var leftBi = new BigInteger(req.getParameter("left") != null ? req.getParameter("left") : "-1");
        long left = leftBi.longValue();
        PeerEvent peerEvent = PeerEvent.EMPTY;
        String event = req.getParameter("event");
        if (event != null) {
            try {
                peerEvent = PeerEvent.valueOf(event);
            } catch (Exception ignored) {
            }
        }
        boolean compact = "1".equals(req.getParameter("compact"));
        boolean requireCrypto = "1".equals(req.getParameter("requirecrypto"));
        boolean supportCrypto = "1".equals(req.getParameter("supportcrypto"));
        long cryptoPort = parseIntIfAvailable(req.getParameter("cryptoport"));
        long azudp = parseIntIfAvailable(req.getParameter("azudp"));
        boolean hide = "1".equals(req.getParameter("hide")); // BitComet extension for no incoming connection
        long azhttp = parseIntIfAvailable(req.getParameter("azhttp"));
        long corrupt = parseIntIfAvailable(req.getParameter("corrupt"));
        String trackerId = req.getParameter("tracker_id");
        boolean azq = "1".equals(req.getParameter("azq"));
        boolean noPeerId = "1".equals(req.getParameter("no_peer_id"));
        String key = ByteUtil.filterUTF8(req.getParameter("key"));
        String azver = ByteUtil.filterUTF8(req.getParameter("azver"));
        long azup = parseIntIfAvailable(req.getParameter("azup"));
        String azas = ByteUtil.filterUTF8(req.getParameter("azas"));
        String aznp = ByteUtil.filterUTF8(req.getParameter("aznp"));
        int numWant = Integer.parseInt(Optional.ofNullable(req.getParameter("num_want")).orElse("50"));
        var reqIpInetAddress = IPUtil.toInet(ip(req));
        List<InetAddress> peerIps = getPossiblePeerIps(req)
                .stream()
                .map(IPUtil::toIPAddress)
                .filter(Objects::nonNull)
                .distinct()
                .filter(ip -> !ip.isLocal() && !ip.isLoopback())
                .map(IPAddress::toInetAddress).toList();

//        // 检查宣告窗口
//        var waitMillis = getWaitMillsUntilAnnounceWindow(ByteUtil.bytesToHex(peerId), ByteUtil.bytesToHex(infoHash));
//        if (waitMillis > 0) {
//            return generateFailureResponse("Re-announce too quickly! Please wait " + (waitMillis / 1000) + " seconds and try again.", waitMillis / 1000);
//        }

        for (InetAddress ip : peerIps) {
            trackerService.scheduleAnnounce(new TrackerService.PeerAnnounce(
                    infoHash,
                    peerId,
                    reqIpInetAddress,
                    ip,
                    port,
                    uploaded,
                    downloaded,
                    left,
                    peerEvent,
                    ua(req),
                    requireCrypto || supportCrypto,
                    key,
                    corrupt,
                    -1,
                    trackerId,
                    cryptoPort,
                    hide,
                    azudp,
                    azhttp,
                    azq,
                    azver,
                    azup,
                    azas,
                    aznp,
                    numWant
            ));
        }
        TrackerService.TrackedPeerList peers = trackerService.fetchPeersFromTorrent(infoHash, peerId, null, numWant);
        tickMetrics("announce_provided_peers", peers.v4().size() + peers.v6().size());
        tickMetrics("announce_provided_peers_ipv4", peers.v4().size());
        tickMetrics("announce_provided_peers_ipv6", peers.v6().size());
        long intervalMillis = generateInterval();
        // 合成响应
        Map<String, Object> map = new HashMap<>();
        map.put("interval", intervalMillis / 1000);
        map.put("complete", peers.seeders());
        map.put("incomplete", peers.leechers());
        map.put("downloaded", peers.downloaded());
        map.put("external ip", ip(req));
        map.put("tracker id", instanceTrackerId);
        if (compact || noPeerId) {
            tickMetrics("announce_return_peers_format_compact", 1);
            map.put("peers", compactPeers(peers.v4(), false));
            if (!peers.v6().isEmpty())
                map.put("peers6", compactPeers(peers.v6(), true));
        } else {
            tickMetrics("announce_return_peers_format_full", 1);
            List<TrackerService.Peer> allPeers = new LinkedList<>(peers.v4());
            allPeers.addAll(peers.v6());
            map.put("peers", new HashMap<>() {{
                for (TrackerService.Peer p : allPeers) {
                    put("peer id", new String(p.peerId(), StandardCharsets.ISO_8859_1));
                    put("ip", p.ip());
                    put("port", p.port());
                }
            }});
        }
        //setNextAnnounceWindow(ByteUtil.bytesToHex(peerId), ByteUtil.bytesToHex(infoHash), intervalMillis);
        tickMetrics("announce_req_success", 1);
        //auditService.log(req, "TRACKER_ANNOUNCE", true, Map.of("hash", infoHash, "user-agent", ua(req)));
        return BencodeUtil.INSTANCE.encode(map);
    }

    private byte[] generateFailureResponse(String reason, long retryAfterSeconds) {
        var map = new HashMap<>();
        map.put("failure reason", reason);
        map.put("retry in", retryAfterSeconds == -1 ? "never" : retryAfterSeconds);
        return BencodeUtil.INSTANCE.encode(map);
    }

    private long getWaitMillsUntilAnnounceWindow(String peerId, String torrentInfoHash) {
        var waitUntil = redisStringTemplate.opsForValue().get("interval-" + peerId + "-" + torrentInfoHash);
        if (waitUntil == null) {
            return 0;
        }
        return Long.parseLong(waitUntil);
    }

    private void setNextAnnounceWindow(String peerId, String torrentInfoHash, long windowLength) {
        redisStringTemplate.opsForValue().set("interval-" + peerId + "-" + torrentInfoHash, String.valueOf(System.currentTimeMillis() + windowLength), Duration.ofMillis(System.currentTimeMillis() + windowLength));
    }

    private long parseIntIfAvailable(String param) {
        if (param == null) {
            return -1;
        }
        return Long.parseLong(param);
    }

    private long generateInterval() {
        var offset = random.nextLong(announceRandomOffset);
        if (random.nextBoolean()) {
            offset = -offset;
        }
        return announceInterval + offset;
    }

    private long generateRetryInterval() {
        var offset = random.nextLong(announceBusyRetryRandomInterval);
        if (random.nextBoolean()) {
            offset = -offset;
        }
        return announceBusyRetryInterval + offset;
    }


    @GetMapping("/tracker/scrape")
    @ResponseBody
    public ResponseEntity<byte[]> scrapeForward() {
        return scrape();
    }

    @GetMapping("/scrape")
    @ResponseBody
    public ResponseEntity<byte[]> scrape() {
        tickMetrics("scrape_req", 1);
        var infoHashes = extractInfoHashes(req.getQueryString());
        var map = new HashMap<>();
        var files = new HashMap<>();
        tickMetrics("scrape_info_hashes", infoHashes.size());
        for (byte[] infoHash : infoHashes) {
            files.put(new String(infoHash, StandardCharsets.ISO_8859_1), new HashMap<>() {{
                var peers = trackerService.scrape(infoHash);
                put("complete", peers.seeders());
                put("incomplete", peers.leechers());
                put("downloaded", peers.downloaded());
            }});
        }
        map.put("files", files);
        map.put("external ip", ip(req));
        //auditService.log(req, "TRACKER_SCRAPE", true, Map.of("hash", infoHashes, "user-agent", ua(req)));
        return ResponseEntity.ok(BencodeUtil.INSTANCE.encode(map));
    }

    public List<String> getPossiblePeerIps(HttpServletRequest req) {
        List<String> found = new ArrayList<>(12);
        found.add(ip(req));
        var ips = req.getParameterValues("ip");
        if (ips != null) {
            found.addAll(List.of(ips));
        }
        var ipv4 = req.getParameterValues("ipv4");
        if (ipv4 != null) {
            found.addAll(List.of(ipv4));
        }
        var ipv6 = req.getParameterValues("ipv6");
        if (req.getParameter("ipv6") != null) {
            found.addAll(List.of(ipv6));
        }
        return found;
    }

    private void tickMetrics(String service, double increment) {
        meterRegistry.counter("sparkle_tracker_" + service).increment(increment);
    }

    private record SparkleTrackerMetricsMessage(
            long seeders,
            long leechers,
            long finishes,
            List<InetAddress> ips
    ) {
        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            ips.stream().filter(inet -> inet instanceof Inet4Address).forEach(net -> joiner.add(net.getHostAddress()));
            ips.stream().filter(inet -> inet instanceof Inet6Address).forEach(net -> joiner.add(net.getHostAddress()));
            return "[Sparkle] S:%s L:%s F:%s, Announce IPs: %s"
                    .formatted(
                            seeders,
                            leechers,
                            finishes,
                            joiner.toString()
                    );
        }
    }
}
