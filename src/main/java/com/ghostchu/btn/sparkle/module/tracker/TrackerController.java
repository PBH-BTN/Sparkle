package com.ghostchu.btn.sparkle.module.tracker;

import com.dampcake.bencode.BencodeOutputStream;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import inet.ipaddr.IPAddressString;
import jakarta.persistence.LockModeType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/tracker")
@Slf4j
public class TrackerController extends SparkleController {
    @Autowired
    private HttpServletRequest req;
    @Autowired
    private TrackerService trackerService;
    @Value("${service.tracker.announce-interval}")
    private long announceInterval;

    @GetMapping("/announce")
    @ResponseBody
    @Transactional
    @Lock(LockModeType.WRITE)
    public String announce() throws IOException {
        Validate.notBlank(req.getParameter("info_hash"));
        byte[] infoHash = req.getParameter("info_hash").getBytes(StandardCharsets.ISO_8859_1);
        Validate.notBlank(req.getParameter("peer_id"));
        byte[] peerId = req.getParameter("peer_id").getBytes(StandardCharsets.ISO_8859_1);
        String peerIp = Optional.ofNullable(req.getParameter("ip")).orElse(ip(req));
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
        var peerIpInetAddress = new IPAddressString(peerIp).getAddress().toInetAddress();
        trackerService.executeAnnounce(new TrackerService.PeerAnnounce(
                infoHash,
                peerId,
                reqIpInetAddress,
                peerIpInetAddress,
                port,
                uploaded,
                downloaded,
                left,
                peerEvent,
                ua(req)
        ));
        //var peers = trackerService.fetchPeersFromTorrent(infoHash, peerId, peerIpInetAddress, numWant);
        var peers = trackerService.fetchPeersFromTorrent(infoHash, null, null, numWant);
        log.info(peers.toString());
        // 合成响应
        @Cleanup
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        @Cleanup
        BencodeOutputStream bencoder = new BencodeOutputStream(out);
        bencoder.writeDictionary(new HashMap<>() {{
            put("interval", announceInterval / 1000);
            put("complete", peers.seeders());
            put("incomplete", peers.leechers());
            put("downloaded", peers.downloaded());
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
        }});
        bencoder.flush();
        log.info("Announce ok!");
        return out.toString(StandardCharsets.UTF_8);
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


}
