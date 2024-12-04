package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ByteUtil;
import org.apache.commons.lang3.Validate;
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
import java.util.function.Function;

@Repository
public class RedisTrackedPeerRepository {
    @Autowired
    @Qualifier("redisTemplateTrackedPeer")
    private RedisTemplate<String, TrackedPeer> redisTemplate;
    @Value("${service.tracker.inactive-interval}")
    private long inactiveInterval;

    public void registerPeers(byte[] infoHash, Collection<TrackedPeer> peers) {
        // remove exists peers from redis if they have same peerId OR same peerIp and port
        String infoHashString = ByteUtil.bytesToHex(infoHash);
        List<TrackedPeer> pendingForRemove = new ArrayList<>();
        try (var cursor = redisTemplate.opsForSet().scan("tracker_peers:" + infoHashString, ScanOptions.scanOptions().build())) {
            while (cursor.hasNext()) {
                var existingPeer = cursor.next();
                for (TrackedPeer peer : peers) {
                    Validate.notNull(existingPeer.getPeerId(), "Existing peer's PeerId is null");
                    Validate.notNull(peer.getPeerId(), "Peer's PeerId is null");
                    Validate.notNull(existingPeer.getPeerIp(), "Existing peer's PeerIp is null");
                    Validate.notNull(peer.getPeerIp(), "Peer's PeerIp is null");
                    if (existingPeer.getPeerId().equals(peer.getPeerId()) ||
                        (existingPeer.getPeerIp().equals(peer.getPeerIp()) && Objects.equals(existingPeer.getPeerPort(), peer.getPeerPort()))) {
                        //cursor.remove(); remove isn't work :(
                        pendingForRemove.add(existingPeer);
                    }
                }
            }
        }
        for (TrackedPeer peer : pendingForRemove) {
            redisTemplate.opsForSet().remove("tracker_peers:" + infoHashString, peer);
        }
        redisTemplate.opsForSet().add("tracker_peers:" + infoHashString, peers.toArray(new TrackedPeer[0]));
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
                if (peer.getLastTimeSeen().isBefore(OffsetDateTime.now().minus(inactiveInterval, ChronoUnit.MILLIS))) {
                    // it not working cursor.remove();
                    pendingForRemove.add(peer);
                    //redisTemplate.opsForSet().remove(key, peer);
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
