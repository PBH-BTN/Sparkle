package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ByteUtil;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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
    @Value("${service.tracker.cleanup-parallel-threshold}")
    private int parallelCleanupThreshold;

    public RedisTrackedPeerRepository(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }


//    public void registerPeers(Map<byte[], Set<TrackedPeer>> announceMap) {
//        long startAt = System.nanoTime();
//        redisTemplate.executePipelined(new SessionCallback<>() {
//            @Override
//            public <K, V> Object execute(RedisOperations<K, V> redisTemplateOperations) throws DataAccessException {
//                RedisOperations<String, TrackedPeer> redisTemplateOperationsForce = (RedisOperations<String, TrackedPeer>) redisTemplateOperations;
//                redisTemplateOperationsForce.executePipelined(new SessionCallback<>() {
//                    @Override
//                    public <K2, V2> Object execute(RedisOperations<K2, V2> generalTemplateOperations) throws DataAccessException {
//                        RedisOperations<String, String> generalTemplateOperationsForce = (RedisOperations<String, String>) generalTemplateOperations;
//                        for (Map.Entry<byte[], Set<TrackedPeer>> entry : announceMap.entrySet()) {
//                            String infoHashString = ByteUtil.bytesToHex(entry.getKey());
//                            var peers = entry.getValue();
//                            redisTemplateOperationsForce.opsForSet().add("tracker_peers:" + infoHashString, peers.toArray(new TrackedPeer[0]));
//                            for (TrackedPeer peer : peers) {
//                                generalTemplateOperationsForce.opsForValue().set("peer_last_seen:" + infoHashString + ":" + peer.toKey(), String.valueOf(System.currentTimeMillis()), Duration.ofMillis(inactiveInterval));
//                            }
//                        }
//                        return null;
//                    }
//                });
//                return null;
//            }
//        });
//        meterRegistry.gauge("tracker_register_peers_cost_ns", System.nanoTime() - startAt);
//    }

    public void registerPeers(Map<byte[], Set<TrackedPeer>> announceMap) {
        long startAt = System.nanoTime();
        for (Map.Entry<byte[], Set<TrackedPeer>> entry : announceMap.entrySet()) {
            String infoHashString = ByteUtil.bytesToHex(entry.getKey());
            var peers = entry.getValue();
            redisTemplate.opsForSet().add("tracker_peers:" + infoHashString, peers.toArray(new TrackedPeer[0]));
            for (TrackedPeer peer : peers) {
                generalRedisTemplate.opsForValue().set("peer_last_seen:" + infoHashString + ":" + peer.toKey(), String.valueOf(System.currentTimeMillis()), Duration.ofMillis(inactiveInterval));
            }
        }
        meterRegistry.gauge("tracker_register_peers_cost_ns", System.nanoTime() - startAt);
    }

    public Set<TrackedPeer> getPeers(byte[] infoHash, int amount) {
        return redisTemplate.opsForSet().distinctRandomMembers("tracker_peers:" + ByteUtil.bytesToHex(infoHash), amount);
    }

    public List<TrackedPeer> scanPeersWithCondition(Function<TrackedPeer, Boolean> condition) {
        List<TrackedPeer> result = new ArrayList<>();
        try (var infoHashCursor = redisTemplate.scan(ScanOptions.scanOptions().match("tracker_peers:*").build())) {
            while (infoHashCursor.hasNext()) {
                var key = infoHashCursor.next();
                try (var peersCursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build())) {
                    while (peersCursor.hasNext()) {
                        var p = peersCursor.next();
                        if (condition.apply(p)) {
                            result.add(p);
                        }
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
        try (var cursor = redisTemplate.opsForSet().scan("tracked_peer:" + ByteUtil.bytesToHex(infoHash), ScanOptions.scanOptions().build())) {
            while (cursor.hasNext()) {
                var peer = cursor.next();
                if (peer.isSeeder()) {
                    seeders++;
                } else {
                    leechers++;
                }
            }
        }
        count.put("seeders", seeders);
        count.put("leechers", leechers);
        return count;
    }

    public UniqueRecords countUniqueRecords() {
        //Set<byte[]> peerIds = new HashSet<>();
        //Set<byte[]> ips = new HashSet<>();
        long infoHashes = 0;
        long peers = 0;
        try (var infoHashCursor = redisTemplate.scan(ScanOptions.scanOptions().match("tracker_peers:*").build())) {
            while (infoHashCursor.hasNext()) {
                var peer = infoHashCursor.next();
                infoHashes++;
                try (var peersCursor = redisTemplate.opsForSet().scan(peer, ScanOptions.scanOptions().build())) {
                    while (peersCursor.hasNext()) {
                        peersCursor.next();
                        //var trackedPeer = peersCursor.next();
                        peers++;
                        //peerIds.add(trackedPeer.getPeerId().getBytes(StandardCharsets.ISO_8859_1));
                        //ips.add(trackedPeer.getPeerIp().getBytes(StandardCharsets.ISO_8859_1));
                    }
                }
            }
        }
        return new UniqueRecords(infoHashes, -1, -1, peers);
    }

    public long cleanup() {
        AtomicLong deleted = new AtomicLong(0);
        var pendingForRemove = scanExpiredPeers();
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> ops) throws DataAccessException {
                RedisOperations<String, TrackedPeer> opsForce = (RedisOperations<String, TrackedPeer>) ops;
                for (Map.Entry<String, Set<TrackedPeer>> entry : pendingForRemove.entrySet()) {
                    for (TrackedPeer trackedPeer : entry.getValue()) {
                        deleted.incrementAndGet();
                        opsForce.opsForSet().remove(entry.getKey(), trackedPeer);
                    }
                }
                return null;
            }
        });
        // check if empty
        for (String key : pendingForRemove.keySet()) {
            if (redisTemplate.opsForSet().size(key) == 0) {
                redisTemplate.unlink(key);
            }
        }
        return deleted.get();
    }

    private Map<String, Set<TrackedPeer>> scanExpiredPeers() {
        Map<String, Set<TrackedPeer>> pendingForRemove = new HashMap<>();
        try (var infoHashCursor = redisTemplate.scan(ScanOptions.scanOptions().match("tracker_peers:*").build())) {
            while (infoHashCursor.hasNext()) {
                var key = infoHashCursor.next();
                try (var peersCursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().build())) {
                    while (peersCursor.hasNext()) {
                        var peer = peersCursor.next();
                        // check if inactive
                        String infoHash = key.substring("tracker_peers:".length());
                        var lastSeenKey = "peer_last_seen:" + infoHash + ":" + peer.toKey();
                        String lastSeen = generalRedisTemplate.opsForValue().get(lastSeenKey);
                        if (lastSeen == null) {
                            // add to pending for remove
                            pendingForRemove.computeIfAbsent("tracker_peers:" + infoHash, k -> new HashSet<>()).add(peer);
                        }
                    }
                }
            }
        }
        return pendingForRemove;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UniqueRecords {
        private long uniqueInfoHashes;
        private long uniquePeerIds;
        private long uniqueIps;
        private long peers;
    }
}
