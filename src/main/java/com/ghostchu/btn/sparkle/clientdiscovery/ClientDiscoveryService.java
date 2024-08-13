package com.ghostchu.btn.sparkle.clientdiscovery;

import com.ghostchu.btn.sparkle.clientdiscovery.internal.ClientDiscovery;
import com.ghostchu.btn.sparkle.clientdiscovery.internal.ClientDiscoveryRepository;
import com.ghostchu.btn.sparkle.user.UserService;
import com.ghostchu.btn.sparkle.user.internal.User;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ClientDiscoveryService {
    private final ClientDiscoveryRepository clientDiscoveryRepository;
    private final UserService userService;

    public ClientDiscoveryService(ClientDiscoveryRepository clientDiscoveryRepository, UserService userService) {
        this.clientDiscoveryRepository = clientDiscoveryRepository;
        this.userService = userService;
    }

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    public void handleIdentities(User user, Timestamp timeForFoundAt, Timestamp timeForLastSeenAt, Set<ClientIdentity> clientIdentities) {
        clientDiscoveryRepository.updateLastSeen(clientIdentities.stream().map(ClientIdentity::hash).toList(), timeForLastSeenAt, user);
        var found = clientDiscoveryRepository.findAllById(clientIdentities.stream().map(ClientIdentity::hash).toList());
        List<Long> hashInDatabase = new ArrayList<>();
        found.forEach(clientDiscoveryEntity -> hashInDatabase.add(clientDiscoveryEntity.getHash()));
        var notInDatabase = clientIdentities.stream()
                .filter(c -> !hashInDatabase.contains(c.hash()))
                .map(ci -> new ClientDiscovery(ci.hash(), ci.getClientName(), ci.getPeerId(), timeForFoundAt, user, timeForLastSeenAt, user))
                .toList();
        clientDiscoveryRepository.saveAll(notInDatabase);
    }

    public ClientDiscoveryDto toDto(ClientDiscovery clientDiscovery) {
        return ClientDiscoveryDto.builder()
                .hash(clientDiscovery.getHash())
                .clientName(clientDiscovery.getClientName())
                .peerId(clientDiscovery.getPeerId())
                .foundAt(clientDiscovery.getFoundAt().getTime())
                .foundBy(userService.toDto(clientDiscovery.getFoundBy()))
                .lastSeenAt(clientDiscovery.getLastSeenAt().getTime())
                .lastSeenBy(userService.toDto(clientDiscovery.getLastSeenBy()))
                .build();
    }

    public SparklePage<ClientDiscovery, ClientDiscoveryDto> queryRecent(Pageable of) {
        var page = clientDiscoveryRepository.findByOrderByFoundAtDesc(of);
        return new SparklePage<>(page, ct -> ct.map(this::toDto));
    }

    public SparklePage<ClientDiscovery, ClientDiscoveryDto> query(Specification<ClientDiscovery> specification, Pageable of) {
        var page = clientDiscoveryRepository.findAll(specification, of);
        return new SparklePage<>(page, ct -> ct.map(this::toDto));
    }
}
