package com.ghostchu.btn.sparkle.torrent;

import com.ghostchu.btn.sparkle.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.torrent.internal.TorrentRepository;
import org.springframework.stereotype.Service;

@Service
public class TorrentService {

    private final TorrentRepository torrentRepository;

    public TorrentService(TorrentRepository torrentRepository) {
        this.torrentRepository = torrentRepository;
    }

    public Torrent createOrGetTorrent(String torrentIdentifier, long torrentSize){
        var torrentOptional =  torrentRepository.findByIdentifierAndSize(torrentIdentifier,torrentSize);
        if(torrentOptional.isPresent()){
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
