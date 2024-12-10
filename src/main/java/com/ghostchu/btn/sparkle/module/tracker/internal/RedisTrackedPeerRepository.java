package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ByteUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
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


    public void registerPeers(Map<byte[], Set<TrackedPeer>> announceMap) {
        long startAt = System.nanoTime();
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> redisTemplateOperations) throws DataAccessException {
                RedisOperations<String, TrackedPeer> redisTemplateOperationsForce = (RedisOperations<String, TrackedPeer>) redisTemplateOperations;
                redisTemplateOperationsForce.executePipelined(new SessionCallback<>() {
                    @Override
                    public <K2, V2> Object execute(RedisOperations<K2, V2> generalTemplateOperations) throws DataAccessException {
                        RedisOperations<String, String> generalTemplateOperationsForce = (RedisOperations<String, String>) generalTemplateOperations;
                        for (Map.Entry<byte[], Set<TrackedPeer>> entry : announceMap.entrySet()) {
                            String infoHashString = ByteUtil.bytesToHex(entry.getKey());
                            var peers = entry.getValue();
                            redisTemplateOperationsForce.opsForSet().add("tracker_peers:" + infoHashString, peers.toArray(new TrackedPeer[0]));
                            for (TrackedPeer peer : peers) {
                                generalTemplateOperationsForce.opsForValue().set("peer_last_seen:" + infoHashString + ":" + peer.toKey(), String.valueOf(System.currentTimeMillis()), Duration.ofMillis(inactiveInterval));
                            }
                        }
                        return null;
                    }
                });
                return null;
            }
        });
        meterRegistry.gauge("tracker_register_peers_cost_ns", System.nanoTime() - startAt);
    }

    public Set<TrackedPeer> getPeers(byte[] infoHash, int amount) {
        return redisTemplate.opsForSet().distinctRandomMembers("tracker_peers:" + ByteUtil.bytesToHex(infoHash), amount);
    }

    public List<TrackedPeer> scanPeersWithCondition(Function<TrackedPeer, Boolean> condition) {
        List<TrackedPeer> result = new ArrayList<>();
        for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
            try (var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().count(10000).build())) {
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
            try (var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().count(300).build())) {
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
            try (var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().count(10000).build())) {
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
            var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().count(10000).build());
            while (cursor.hasNext()) {
                var peer = cursor.next();
                count.add(peer.getPeerIp());
            }
        }
        return count.size();
    }

    public long cleanup() {
        Semaphore semaphore = new Semaphore(parallelCleanupThreshold);
        AtomicLong deleted = new AtomicLong(0);
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String key : redisTemplate.opsForSet().getOperations().keys("tracker_peers:*")) {
                exec.submit(() -> {
                    List<TrackedPeer> pendingForRemove = new ArrayList<>();
                    var cursor = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().count(10000).build());
                    while (cursor.hasNext()) {
                        var peer = cursor.next();
                        // check if inactive
                        String infoHash = key.substring("tracker_peers:".length());
                        var lastSeenKey = "peer_last_seen:" + infoHash + ":" + peer.toKey();
                        String lastSeen = generalRedisTemplate.opsForValue().get(lastSeenKey);
                        if (lastSeen == null) {
                            // key indicator has been expired
                            pendingForRemove.add(peer);
                        } else {
                            // and we check if it's inactive
                            if (System.currentTimeMillis() - Long.parseLong(lastSeen) > inactiveInterval) {
                                pendingForRemove.add(peer);
                                generalRedisTemplate.opsForValue().getAndDelete(lastSeenKey);
                            }
                        }
                    }
                    redisTemplate.executePipelined(new SessionCallback<>() {
                        @Override
                        public <K, V> Object execute(RedisOperations<K, V> ops) throws DataAccessException {
                            for (TrackedPeer trackedPeer : pendingForRemove) {
                                deleted.incrementAndGet();
                                ops.opsForSet().remove((K) key, trackedPeer);
                            }
                            return null;
                        }
                    });
                    // check if empty
                    if (redisTemplate.opsForSet().size(key) == 0) {
                        redisTemplate.delete(key);
                    }
                });
            }

        }
        return deleted.get();


    }

}
