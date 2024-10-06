package com.ghostchu.btn.sparkle.module.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.util.BencodeUtil;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import jakarta.persistence.LockModeType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/tracker")
@Slf4j
public class TrackerController extends SparkleController {
    @Autowired
    private HttpServletRequest req;
    @Autowired
    private TrackerService trackerService;
    @Autowired
    private HttpServletResponse resp;
    @Value("${service.tracker.announce-interval}")
    private long announceInterval;
    @Autowired
    private ObjectMapper jacksonObjectMapper;

    @GetMapping("/announce")
    @ResponseBody
    @Transactional
    @Lock(LockModeType.WRITE)
    public byte[] announce() {
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
        Validate.isTrue(StringUtils.isNumeric(req.getParameter("left")));
        long left = Long.parseLong(req.getParameter("left"));
        PeerEvent peerEvent = PeerEvent.EMPTY;
        String event = req.getParameter("event");
        if (event != null) {
            try {
                peerEvent = PeerEvent.valueOf(event);
            } catch (Exception ignored) {
            }
        }
        boolean compact = "1".equals(req.getParameter("compact"));
        int numWant = Integer.parseInt(Optional.ofNullable(req.getParameter("num_want")).orElse("50"));
        var reqIpInetAddress = new IPAddressString(ip(req)).getAddress().toInetAddress();
        List<InetAddress> peerIps = getPossiblePeerIps(req)
                .stream()
                .distinct()
                .map(ip -> new IPAddressString(ip).getAddress())
                .filter(Objects::nonNull)
                .map(IPAddress::toInetAddress).toList();
        for (InetAddress ip : peerIps) {
            try {
                trackerService.executeAnnounce(new TrackerService.PeerAnnounce(
                        infoHash,
                        peerId,
                        reqIpInetAddress,
                        ip,
                        port,
                        uploaded,
                        downloaded,
                        left,
                        peerEvent,
                        ua(req)
                ));
            } catch (Exception e) {
                log.error("Unable to handle Torrent announce", e);
            }
        }
        var peers = trackerService.fetchPeersFromTorrent(infoHash, null, null, numWant);

        // 合成响应
        var map = new HashMap<>() {{
            put("interval", announceInterval / 1000);
            put("complete", peers.seeders());
            put("incomplete", peers.leechers());
            put("downloaded", peers.downloaded());
            put("external ip", ip(req));
            // put("warning message", new SparkleTrackerMetricsMessage(peers.seeders(),peers.leechers(), peers.downloaded(), peerIps).toString());
            if (compact) {
                put("peers", compactPeers(peers.v4(), false));
                if (!peers.v6().isEmpty())
                    put("peers6", compactPeers(peers.v6(), true));
            } else {
                List<TrackerService.Peer> allPeers = new ArrayList<>(peers.v4());
                allPeers.addAll(peers.v6());
                put("peers", new HashMap<>() {{
                    for (TrackerService.Peer p : allPeers) {
                        put("peer id", new String(p.peerId(), StandardCharsets.ISO_8859_1));
                        put("ip", p.ip());
                        put("port", p.port());
                    }
                }});
            }
        }};
        return BencodeUtil.INSTANCE.encode(map);
    }

    @GetMapping("/scrape")
    @ResponseBody
    public ResponseEntity<byte[]> scrape() {
        var infoHashes = extractInfoHashes(req.getQueryString());
        var map = new LinkedHashMap<>();
        var files = new TreeMap<>();
        for (byte[] infoHash : infoHashes) {
            files.put(new String(infoHash, StandardCharsets.ISO_8859_1), new TreeMap<>() {{
                var peers = trackerService.scrape(infoHash);
                put("complete", peers.seeders() + 15);
                put("incomplete", peers.leechers() + 15);
                put("downloaded", peers.downloaded() + 15);
            }});
        }
        map.put("files", files);
        map.put("external ip", ip(req));
        return ResponseEntity.ok(BencodeUtil.INSTANCE.encode(map));
    }

    public List<String> getPossiblePeerIps(HttpServletRequest req) {
        List<String> found = new ArrayList<>();
        found.add(ip(req));
        if (req.getParameter("ip") != null) {
            found.addAll(List.of(req.getParameterValues("ip")));
        }
        if (req.getParameter("ipv4") != null) {
            found.addAll(List.of(req.getParameterValues("ipv4")));
        }
        if (req.getParameter("ipv6") != null) {
            found.addAll(List.of(req.getParameterValues("ipv6")));
        }
        return found;
    }

    public static String compactPeers(List<TrackerService.Peer> peers, boolean isV6) throws IllegalStateException {
        ByteBuffer buffer = ByteBuffer.allocate((isV6 ? 18 : 6) * peers.size());
        for (TrackerService.Peer peer : peers) {
            String ip = peer.ip();
            try {
                for (byte address : InetAddress.getByName(ip).getAddress()) {
                    buffer.put(address);
                }
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
     * @param queryString 查询字符串
     * @return 使用 ISO_8859_1 进行 URL 解码的 Info Hash 集合
     */
    public static List<byte[]> extractInfoHashes(String queryString) {
        List<byte[]> infoHashes = new ArrayList<>();
        String[] params = queryString.split("&");
        for (String param : params) {
            if (param.startsWith("info_hash=")) {
                String encodedHash = param.substring("info_hash=".length());
                byte[] decodedHash = URLDecoder.decode(encodedHash, StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.ISO_8859_1);
                infoHashes.add(decodedHash);
            }
        }

        return infoHashes;
    }

    private record SparkleTrackerMetricsMessage(
            long seeders,
            long leechers,
            long finishes,
            List<InetAddress> ips
    ){
        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ","[","]");
            ips.stream().filter(inet->inet instanceof Inet4Address).forEach(net->joiner.add(net.getHostAddress()));
            ips.stream().filter(inet->inet instanceof Inet6Address).forEach(net->joiner.add(net.getHostAddress()));
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
