package com.ghostchu.btn.sparkle.module.tracker.internal;


import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TrackerStorage {
    private final long inactiveInterval;
    private final long peersReturn;
    private Cache<byte[], Cache<byte[], PeerRegister>> MEMORY_TRACKER_ENGINE;

    public TrackerStorage(
            @Value("${service.tracker.inactive-interval}") long inactiveInterval,
            @Value("${service.tracker.max-peers-return}") long maxPeersReturn
    ) {
        this.inactiveInterval = inactiveInterval;
        this.peersReturn = maxPeersReturn;
        MEMORY_TRACKER_ENGINE = CacheBuilder.newBuilder()
                .expireAfterAccess(inactiveInterval, TimeUnit.MILLISECONDS)
                .build();
    }

    public Map<byte[], PeerRegister> announce(
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
            IPGeoData geoIP,
            short numWant
    ) throws ExecutionException {
        Cache<byte[], PeerRegister> activePeers = MEMORY_TRACKER_ENGINE.getIfPresent(infoHash);
        if (activePeers == null) {
            activePeers = CacheBuilder.newBuilder()
                    .maximumSize(4096)
                    .expireAfterWrite(inactiveInterval, TimeUnit.MILLISECONDS)
                    .build();
            MEMORY_TRACKER_ENGINE.put(infoHash, activePeers);
        }
        PeerRegister peerRegister = new PeerRegister(
                reqIp,
                peerId,
                peerIp,
                peerPort,
                uploadedOffset,
                0L,
                downloadedOffset,
                0L,
                left,
                lastEvent,
                userAgent,
                lastTimeSeen,
                geoIP,
                numWant
        );
        PeerRegister lookup = activePeers.get(peerId, () -> peerRegister);
        lookup.setUploaded(lookup.getUploaded() + peerRegister.getUploadedOffset());
        lookup.setDownloaded(lookup.getDownloaded() + peerRegister.getDownloadedOffset());
        activePeers.put(peerId, lookup);
        //return activePeers.asMap();
        //return a map with peersReturn number of peers (random order)
        return getRandomElements(activePeers.asMap(), Math.min(numWant, (int) peersReturn));
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
        }
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
