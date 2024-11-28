package com.ghostchu.btn.sparkle.module.tracker.internal;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TrackerStorage {
    private final long inactiveInterval;
    private final Cache<byte[], Cache<byte[], PeerRegister>> MEMORY_TRACKER_ENGINE;

    public TrackerStorage(
            @Value("${service.tracker.inactive-interval}") long inactiveInterval,
            @Value("${service.tracker.max-peers-return}") long maxPeersReturn,
            @Value("${service.tracker.max-torrents}") long maxTorrents
    ) {
        this.inactiveInterval = inactiveInterval;
        MEMORY_TRACKER_ENGINE = CacheBuilder.newBuilder()
                .maximumSize(maxTorrents)
                .expireAfterAccess(inactiveInterval, TimeUnit.MILLISECONDS)
                .softValues()
                .build();
    }

    public void announce(
            byte[] infoHash,
            InetAddress reqIp,
            byte[] peerId,
            InetAddress peerIp,
            int peerPort,
            long uploadedOffset,
            long downloadedOffset,
            long left,
            PeerEvent lastEvent,
            String userAgent,
            long lastTimeSeen,
            short numWant
    ) throws ExecutionException {
        Cache<byte[], PeerRegister> activePeers = MEMORY_TRACKER_ENGINE.getIfPresent(infoHash);
        if (activePeers == null) {
            activePeers = CacheBuilder.newBuilder()
                    .maximumSize(4096)
                    .expireAfterWrite(inactiveInterval, TimeUnit.MILLISECONDS)
                    .softValues()
                    .build();
            MEMORY_TRACKER_ENGINE.put(infoHash, activePeers);
        }
        // check if same ip register more than 16 peers, if so, remove the oldest one
        if (activePeers.asMap().values().stream().filter(peerRegister -> Arrays.equals(peerRegister.getPeerIp(), peerIp.getAddress())).count() > 6) {
            Cache<byte[], PeerRegister> finalActivePeers = activePeers;
            activePeers.asMap().entrySet().stream()
                    .filter(entry -> Arrays.equals(entry.getValue().getPeerIp(), peerIp.getAddress()))
                    .min((entry1, entry2) -> (int) (entry1.getValue().getLastTimeSeen() - entry2.getValue().getLastTimeSeen()))
                    .ifPresent(entry -> finalActivePeers.invalidate(entry.getKey()));
        }
        PeerRegister peerRegister = new PeerRegister(
                // reqIp,
                peerId,
                peerIp.getAddress(),
                peerPort,
                uploadedOffset,
                0L,
                downloadedOffset,
                0L,
                left,
                //lastEvent,
                //userAgent,
                lastTimeSeen,
                numWant
        );
        PeerRegister lookup = activePeers.get(peerId, () -> peerRegister);
        lookup.setUploaded(lookup.getUploaded() + peerRegister.getUploadedOffset());
        lookup.setDownloaded(lookup.getDownloaded() + peerRegister.getDownloadedOffset());
        activePeers.put(peerId, lookup);
        // return activePeers.asMap();
        // return a map with peersReturn number of peers (random order)
        // return getRandomElements(activePeers.asMap(), Math.min(numWant, (int) peersReturn));
    }

    public Map<byte[], PeerRegister> getPeers(byte[] infoHash) {
        Cache<byte[], PeerRegister> activePeers = MEMORY_TRACKER_ENGINE.getIfPresent(infoHash);
        if (activePeers == null) {
            return Collections.emptyMap();
        }
        return activePeers.asMap();
    }

    public Map<byte[], PeerRegister> getRandomElements(Map<byte[], PeerRegister> originalMap, int n) {
        List<Map.Entry<byte[], PeerRegister>> entryList = new ArrayList<>(originalMap.entrySet());
        Collections.shuffle(entryList); // Shuffle the list
        return entryList.stream().limit(n) // Limit to N elements
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void unregisterPeer(byte[] infoHash, byte[] peerId) {
        Cache<byte[], PeerRegister> activePeers = MEMORY_TRACKER_ENGINE.getIfPresent(infoHash);
        if (activePeers != null) {
            activePeers.invalidate(peerId);
            if (activePeers.size() == 0) {
                MEMORY_TRACKER_ENGINE.invalidate(infoHash);
            }
        }
    }

    public void cleanup() {
        MEMORY_TRACKER_ENGINE.cleanUp();
        MEMORY_TRACKER_ENGINE.asMap().values().forEach(Cache::cleanUp);
        // Remove all values that Cache.size == 0
        MEMORY_TRACKER_ENGINE.asMap().entrySet().removeIf(entry -> entry.getValue().size() == 0);
    }

    public long torrentsCount() {
        return MEMORY_TRACKER_ENGINE.size();
    }

    public long peersCount() {
        return MEMORY_TRACKER_ENGINE.asMap().values().stream().mapToLong(Cache::size).sum();
    }

    public long uniquePeers() {
        return MEMORY_TRACKER_ENGINE.asMap().values().stream().flatMap(cache -> cache.asMap().keySet().stream()).distinct().count();
    }

    public long uniqueIps() {
        return MEMORY_TRACKER_ENGINE.asMap().values().stream().flatMap(cache -> cache.asMap().values().stream().map(PeerRegister::getPeerIp)).distinct().count();
    }
}
