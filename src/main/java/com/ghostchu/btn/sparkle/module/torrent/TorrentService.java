package com.ghostchu.btn.sparkle.module.torrent;

import com.ghostchu.btn.sparkle.module.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.module.torrent.internal.TorrentRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TorrentService {

    private final TorrentRepository torrentRepository;

    public TorrentService(TorrentRepository torrentRepository) {
        this.torrentRepository = torrentRepository;
    }

    @Modifying
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Cacheable(value = "torrent#600000", key = "#torrentIdentifier+'-'+#torrentSize")
    public Torrent createOrGetTorrent(String torrentIdentifier, long torrentSize) {
        var torrentOptional = torrentRepository.findByIdentifierAndSize(torrentIdentifier, torrentSize);
        if (torrentOptional.isPresent()) {
            return torrentOptional.get();
        }
        Torrent torrent = new Torrent(null, torrentIdentifier, torrentSize);
        return torrentRepository.save(torrent);
    }

    public TorrentDto toDto(Torrent torrent) {
        return TorrentDto.builder()
                .id(torrent.getId())
                .identifier(torrent.getIdentifier())
                .size(torrent.getSize())
                .build();
    }
}
