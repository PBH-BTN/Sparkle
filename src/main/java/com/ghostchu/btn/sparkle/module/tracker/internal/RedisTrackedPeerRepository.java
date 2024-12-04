package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ByteUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
public class RedisTrackedPeerRepository {
    @Autowired
    @Qualifier("redisTemplateTrackedPeer")
    private RedisTemplate<String, TrackedPeer> redisTemplate;
    @Value("${service.tracker.inactive-interval}")
    private long inactiveInterval;

    public void registerPeers(byte[] infoHash, TrackedPeer... peers) {
        // remove exists peers from redis if they have same peerId OR same peerIp and port
        for (TrackedPeer peer : peers) {
            try (var cursor = redisTemplate.opsForSet().scan("tracker_peers:" + ByteUtil.bytesToHex(infoHash), ScanOptions.scanOptions().build())) {
                while (cursor.hasNext()) {
                    var existingPeer = cursor.next();
                    if (existingPeer.getPeerIdHumanReadable().equals(peer.getPeerIdHumanReadable()) ||
                        (existingPeer.getPeerIp().equals(peer.getPeerIp()) && Objects.equals(existingPeer.getPeerPort(), peer.getPeerPort()))) {
                        cursor.remove();
                    }
                }
            }
        }
        redisTemplate.opsForSet().add("tracker_peers:" + new String(infoHash, StandardCharsets.ISO_8859_1), peers);
    }

    public List<TrackedPeer> getPeers(byte[] infoHash, int amount) {
        return redisTemplate.opsForSet().randomMembers("tracker_peers:" + ByteUtil.bytesToHex(infoHash), amount);
    }

    public Map<String, Integer> scrapeTorrent(byte[] infoHash) {
        Map<String, Integer> count = new HashMap<>(2);
        int seeders = 0;
        int leechers = 0;
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:" + ByteUtil.bytesToHex(infoHash))) {
            try (var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build())) {
                while (cursor.hasNext()) {
                    var peer = cursor.next();
                    if (peer.getLeft() == 0) {
                        seeders++;
                    } else {
                        leechers++;
                    }
                }
            }
        }
        count.put("seeders", seeders);
        count.put("leechers", leechers);
        return count;
    }

    public long countPeers() {
        long count = 0;
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            count += redisTemplate.opsForSet().size(key);
        }
        return count;
    }

    public long countUniquePeerIds() {
        Set<byte[]> count = new HashSet<>();
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            try (var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build())) {
                while (cursor.hasNext()) {
                    var peer = cursor.next();
                    count.add(peer.getPeerIdHumanReadable().getBytes(StandardCharsets.ISO_8859_1));
                }
            }
        }
        return count.size();
    }

    public long countUniqueTorrents() {
        return redisTemplate.opsForSet().getOperations().keys("tracker_peers:*").size();
    }

    public long countUniqueIps() {
        Set<byte[]> count = new HashSet<>();
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build());
            while (cursor.hasNext()) {
                var peer = cursor.next();
                count.add(peer.getPeerIp().getAddress());
            }
        }
        return count.size();
    }

    public long cleanup() {
        long removed = 0;
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build());
            while (cursor.hasNext()) {
                var peer = cursor.next();
                // check if inactive
                if (peer.getLastTimeSeen().isBefore(OffsetDateTime.now().minus(inactiveInterval, ChronoUnit.MILLIS))) {
                    cursor.remove();
                    removed++;
                    //redisTemplate.opsForSet().remove(key, peer);
                }
            }
            // check if empty
            if (redisTemplate.opsForSet().size(key) == 0) {
                redisTemplate.delete(key);
            }
        }
        return removed;
    }
}
