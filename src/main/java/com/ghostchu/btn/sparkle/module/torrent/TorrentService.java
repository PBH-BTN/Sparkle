package com.ghostchu.btn.sparkle.module.torrent;

import com.ghostchu.btn.sparkle.module.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.module.torrent.internal.TorrentRepository;
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
    //@Cacheable(value = "torrent#600000", key = "#torrentIdentifier+'-'+#torrentSize")
    public Torrent createOrGetTorrent(String torrentIdentifier, long torrentSize, Boolean isPrivate) {
        Torrent t;
        var torrentOptional = torrentRepository.findByIdentifier(torrentIdentifier);
        if (torrentOptional.isEmpty()) {
            t = new Torrent(null, torrentIdentifier, torrentSize, isPrivate);
            t = torrentRepository.save(t);
        } else {
            t = torrentOptional.get();
        }
        if (t.getSize() == -1 && torrentSize != -1) {
            t.setSize(torrentSize);
            t = torrentRepository.save(t);
        }
        if (t.getPrivateTorrent() == null && isPrivate != null) {
            t.setPrivateTorrent(isPrivate);
            t = torrentRepository.save(t);
        }
        return t;
    }

    public TorrentDto toDto(Torrent torrent) {
        return TorrentDto.builder()
                .id(torrent.getId())
                .identifier(torrent.getIdentifier())
                .size(torrent.getSize())
                .privateTorrent(torrent.getPrivateTorrent() != null && torrent.getPrivateTorrent())
                .build();
    }
}
