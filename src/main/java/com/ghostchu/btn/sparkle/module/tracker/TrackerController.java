package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.util.BencodeUtil;
import com.ghostchu.btn.sparkle.util.WarningSender;
import com.google.common.net.HostAndPort;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Controller
@Slf4j
public class TrackerController extends SparkleController {
    private static final Random random = new Random();
    private final Semaphore parallelAnnounceSemaphore;
    private final WarningSender warningSender = new WarningSender(5000);
    private final int announceRequestMaxWait;
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
    @Value("${service.tracker.maintenance}")
    private boolean trackerMaintenance;
    @Value("${service.tracker.maintenance-message}")
    private String trackerMaintenanceMessage;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private StringRedisTemplate redisStringTemplate;
    @Value("${service.tracker.max-peers-return}")
    private int maxPeersReturn;

    public TrackerController(@Value("${service.tracker.max-parallel-announce-service-requests}")
                             int maxParallelAnnounceServiceRequests,
                             @Value("${service.tracker.announce-requests-max-wait}")
                             int announceRequestMaxWait) {
        this.parallelAnnounceSemaphore = new Semaphore(maxParallelAnnounceServiceRequests);
        this.announceRequestMaxWait = announceRequestMaxWait;
    }

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
        List<byte[]> infoHashes = new ArrayList<>(1);
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
    public ResponseEntity<byte[]> announceForward() throws InterruptedException {
        return announce();
    }

    @GetMapping("/announce")
    @ResponseBody
    public ResponseEntity<byte[]> announce() throws InterruptedException {
        if (trackerMaintenance) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(generateFailureResponse(trackerMaintenanceMessage, 86400));
        }
        tickMetrics("announce_req", 1);
        if (req.getQueryString() == null) {
            return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Sorry, This is a BitTorrent Tracker, and access announce endpoint via Browser is disallowed and useless.".getBytes(StandardCharsets.UTF_8));
        }
        String userAgent = ua(req);
        if (userAgent != null) {
            if (userAgent.contains("Mozilla")) {
                return ResponseEntity
                        .badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Sorry, This is a BitTorrent Tracker, and access announce endpoint via Browser is disallowed and useless.".getBytes(StandardCharsets.UTF_8));
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
        boolean compact = "1".equals(req.getParameter("compact")) || "1".equals(req.getParameter("no_peer_id"));
        PeerEvent peerEvent = PeerEvent.EMPTY;
        String event = req.getParameter("event");
        if (event != null) {
            peerEvent = PeerEvent.fromString(event);
        }
        int numWant = Integer.parseInt(Optional.ofNullable(req.getParameter("num_want")).orElse("50"));
        var reqIpInetAddress = ip(req);
        List<InetAddress> peerIps = getPossiblePeerIps(req)
                .stream()
                .map(s -> {
                    try {
                        return InetAddress.getByName(s.getHost());
                    } catch (UnknownHostException e) {
                        //log.warn("Failed to parse peer IP: {}", s.getHost());
                        return null;
                    }
                })
                .distinct()
                .filter(Objects::nonNull)
                .filter(ip -> !ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && !ip.isAnyLocalAddress() && !ip.isMulticastAddress())
                .toList();
        try {
            if (!parallelAnnounceSemaphore.tryAcquire(announceRequestMaxWait, TimeUnit.MILLISECONDS)) {
                tickMetrics("announce_req_fails", 1);
                long retryAfterSeconds = generateRetryInterval() / 1000;
                if (warningSender.sendIfPossible()) {
                    log.warn("[Tracker Busy] Too many queued requests, queue size: {}", parallelAnnounceSemaphore.getQueueLength());
                }
                return ResponseEntity.status(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(generateFailureResponse("Tracker is busy (too many queued requests), you have scheduled retry after " + retryAfterSeconds + " seconds", retryAfterSeconds));
            }

            for (InetAddress ip : peerIps) {
                if (!trackerService.scheduleAnnounce(new TrackerService.PeerAnnounce(
                        infoHash,
                        peerId,
                        reqIpInetAddress,
                        ip.getHostAddress(),
                        port,
                        uploaded,
                        downloaded,
                        left,
                        peerEvent,
                        ua(req)
                ))) {
                    tickMetrics("announce_req_fails", 1);
                    if (warningSender.sendIfPossible()) {
                        log.warn("[Tracker Busy] Disk flush queue is full!");
                    }
                    long retryAfterSeconds = generateRetryInterval() / 1000;
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(generateFailureResponse("Tracker is busy (disk flush queue is full), you have scheduled retry after " + retryAfterSeconds + " seconds", retryAfterSeconds));
                }
            }
            TrackerService.TrackedPeerList peers;
            if (peerEvent != PeerEvent.STOPPED) {
                peers = trackerService.fetchPeersFromTorrent(infoHash, peerId, null, Math.min(maxPeersReturn, numWant));
            } else {
                peers = new TrackerService.TrackedPeerList(Collections.emptyList(), Collections.emptyList(), 0L, 0L, 0L);
            }
            tickMetrics("announce_provided_peers", peers.v4().size() + peers.v6().size());
            tickMetrics("announce_provided_peers_ipv4", peers.v4().size());
            tickMetrics("announce_provided_peers_ipv6", peers.v6().size());
            long intervalMillis = generateInterval();
            // 合成响应
            Map<String, Object> map = new HashMap<>(8);
            map.put("interval", intervalMillis / 1000);
            map.put("complete", peers.seeders());
            map.put("incomplete", peers.leechers());
            map.put("downloaded", peers.downloaded());
            map.put("external ip", ip(req));
            map.put("tracker id", instanceTrackerId);

            if (compact) {
                tickMetrics("announce_return_peers_format_compact", 1);
                map.put("peers", compactPeers(peers.v4(), false));
                if (!peers.v6().isEmpty())
                    map.put("peers6", compactPeers(peers.v6(), true));
            } else {
                tickMetrics("announce_return_peers_format_full", 1);
                List<TrackerService.Peer> allPeers = new ArrayList<>(peers.v4());
                allPeers.addAll(peers.v6());
                map.put("peers", new HashMap<>() {{
                    for (TrackerService.Peer p : allPeers) {
                        put("peer id", new String(p.peerId(), StandardCharsets.ISO_8859_1));
                        put("ip", p.ip());
                        put("port", p.port());
                    }
                }});
            }
            tickMetrics("announce_req_success", 1);
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(BencodeUtil.INSTANCE.encode(map));
        } finally {
            parallelAnnounceSemaphore.release();
        }
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


//    @GetMapping("/tracker/scrape")
//    @ResponseBody
//    public ResponseEntity<byte[]> scrapeForward() throws InterruptedException {
//        return scrape();
//    }

//    @GetMapping("/scrape")
//    @ResponseBody
//    public ResponseEntity<byte[]> scrape() throws InterruptedException {
//        if (trackerMaintenance) {
//            return ResponseEntity.ok(generateFailureResponse(trackerMaintenanceMessage, 86400));
//        }
//        tickMetrics("scrape_req", 1);
//        var infoHashes = extractInfoHashes(req.getQueryString());
//        var map = new HashMap<>();
//        var files = new HashMap<>();
//        tickMetrics("scrape_info_hashes", infoHashes.size());
//        for (byte[] infoHash : infoHashes) {
//            files.put(new String(infoHash, StandardCharsets.ISO_8859_1), new HashMap<>() {{
//                var peers = trackerService.scrape(infoHash);
//                put("complete", peers.seeders());
//                put("incomplete", peers.leechers());
//                put("downloaded", peers.downloaded());
//            }});
//        }
//        map.put("files", files);
//        map.put("external ip", ip(req));
//        //auditService.log(req, "TRACKER_SCRAPE", true, Map.of("hash", infoHashes, "user-agent", ua(req)));
//        return ResponseEntity.ok(BencodeUtil.INSTANCE.encode(map));
//    }

    public List<HostAndPort> getPossiblePeerIps(HttpServletRequest req) {
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
        List<HostAndPort> hap = new ArrayList<>(found.size());
        for (String s : found) {
            try {
                hap.add(HostAndPort.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return hap;
    }

    private void tickMetrics(String service, double increment) {
        meterRegistry.counter("sparkle_tracker_" + service).increment(increment);
    }
}
