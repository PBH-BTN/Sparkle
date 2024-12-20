package com.ghostchu.btn.sparkle.module.torrent;

import com.ghostchu.btn.sparkle.module.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.module.torrent.internal.TorrentRepository;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class TorrentService {

    private Cache<String, Torrent> torrentCache = CacheBuilder
            .newBuilder()
            .concurrencyLevel(20)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();
    private final TorrentRepository torrentRepository;

    public TorrentService(TorrentRepository torrentRepository) {
        this.torrentRepository = torrentRepository;
    }

    @Modifying
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    //@Cacheable(value = "torrent#600000", key = "#torrentIdentifier+'-'+#torrentSize")
    public Torrent createOrGetTorrent(String torrentIdentifier, long torrentSize) {
        var t = torrentCache.getIfPresent(torrentIdentifier + "@" + torrentSize);
        if (t == null) {
            var torrentOptional = torrentRepository.findByIdentifierAndSize(torrentIdentifier, torrentSize);
            if (torrentOptional.isPresent()) {
                return torrentOptional.get();
            }
            Torrent torrent = new Torrent(null, torrentIdentifier, torrentSize);
            t = torrentRepository.save(torrent);
            torrentCache.put(torrentIdentifier + "@" + torrentSize, t);
        }
        return t;
    }

    public TorrentDto toDto(Torrent torrent) {
        return TorrentDto.builder()
                .id(torrent.getId())
                .identifier(torrent.getIdentifier())
                .size(torrent.getSize())
                .build();
    }
}
