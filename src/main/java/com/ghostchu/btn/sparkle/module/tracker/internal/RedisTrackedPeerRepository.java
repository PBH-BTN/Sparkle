package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ByteUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

@Repository
public class RedisTrackedPeerRepository {
    private final MeterRegistry meterRegistry;
    @Autowired
    @Qualifier("redisTemplateTrackedPeer")
    private RedisTemplate<String, TrackedPeer> redisTemplate;
    @Autowired
    private RedisTemplate<String, String> generalRedisTemplate;
    @Value("${service.tracker.inactive-interval}")
    private long inactiveInterval;

    public RedisTrackedPeerRepository(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void registerPeers(byte[] infoHash, Collection<TrackedPeer> peers) {
        // remove exists peers from redis if they have same peerId OR same peerIp and port
        long startAt = System.nanoTime();
        try {
            String infoHashString = ByteUtil.bytesToHex(infoHash);
            redisTemplate.opsForSet().add("tracker_peers:" + infoHashString, peers.toArray(new TrackedPeer[0]));
            for (TrackedPeer peer : peers) {
                generalRedisTemplate.opsForValue().set("peer_last_seen:" + infoHashString + ":" + peer.toKey(), String.valueOf(System.currentTimeMillis()), Duration.ofMillis(inactiveInterval));
            }
        } finally {
            meterRegistry.gauge("tracker_register_peers_cost_ns", System.nanoTime() - startAt);
        }
    }

    public Set<TrackedPeer> getPeers(byte[] infoHash, int amount) {
        return redisTemplate.opsForSet().distinctRandomMembers("tracker_peers:" + ByteUtil.bytesToHex(infoHash), amount);
    }

    public List<TrackedPeer> scanPeersWithCondition(Function<TrackedPeer, Boolean> condition) {
        List<TrackedPeer> result = new ArrayList<>();
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            try (var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build())) {
                while (cursor.hasNext()) {
                    var peer = cursor.next();
                    if (condition.apply(peer)) {
                        result.add(peer);
                    }
                }
            }
        }
        return result;
    }

    public Map<String, Integer> scrapeTorrent(byte[] infoHash) {
        Map<String, Integer> count = new HashMap<>(2);
        int seeders = 0;
        int leechers = 0;
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:" + ByteUtil.bytesToHex(infoHash))) {
            try (var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().count(100).build())) {
                while (cursor.hasNext()) {
                    var peer = cursor.next();
                    if (peer.isSeeder()) {
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
                    count.add(peer.getPeerId().getBytes(StandardCharsets.ISO_8859_1));
                }
            }
        }
        return count.size();
    }

    public long countUniqueTorrents() {
        return redisTemplate.opsForSet().getOperations().keys("tracker_peers:*").size();
    }

    public long countUniqueIps() {
        Set<String> count = new HashSet<>();
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build());
            while (cursor.hasNext()) {
                var peer = cursor.next();
                count.add(peer.getPeerIp());
            }
        }
        return count.size();
    }

    public long cleanup() {
        long deleted = 0;
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            List<TrackedPeer> pendingForRemove = new ArrayList<>();
            var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build());
            while (cursor.hasNext()) {
                var peer = cursor.next();
                // check if inactive
                String infoHash = key.substring("tracker_peers:".length());
                String lastSeen = generalRedisTemplate.opsForValue().get("peer_last_seen:" + infoHash + ":" + peer.toKey());
                if (lastSeen == null) {
                    // key indicator has been expired
                    pendingForRemove.add(peer);
                } else {
                    // and we check if it's inactive
                    if (System.currentTimeMillis() - Long.parseLong(lastSeen) > inactiveInterval) {
                        pendingForRemove.add(peer);
                    }
                }
            }
            for (TrackedPeer trackedPeer : pendingForRemove) {
                redisTemplate.opsForSet().remove(key, trackedPeer);
                deleted++;
            }
            // check if empty
            if (redisTemplate.opsForSet().size(key) == 0) {
                redisTemplate.delete(key);
            }
        }
        return deleted;
    }
}
