package com.ghostchu.btn.sparkle.module.clientdiscovery;

import com.ghostchu.btn.sparkle.module.clientdiscovery.internal.ClientDiscovery;
import com.ghostchu.btn.sparkle.module.clientdiscovery.internal.ClientDiscoveryRepository;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Collection;

@Service
@Slf4j
public class ClientDiscoveryService {
    private final ClientDiscoveryRepository clientDiscoveryRepository;
    @Autowired
    private MeterRegistry meterRegistry;

    public ClientDiscoveryService(ClientDiscoveryRepository clientDiscoveryRepository) {
        this.clientDiscoveryRepository = clientDiscoveryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_UNCOMMITTED)
    @Async
    public void handleIdentities(OffsetDateTime timeForFoundAt, OffsetDateTime timeForLastSeenAt, Collection<ClientIdentity> clientIdentities) {
        meterRegistry.counter("sparkle_client_discovery_processed").increment();
        for (ClientIdentity ci : clientIdentities) {
            try {
                clientDiscoveryRepository.saveIgnoreConflict(ci.hash(), ByteUtil.filterUTF8(ci.getClientName()), ByteUtil.filterUTF8(ci.getPeerId()), timeForFoundAt);
            } catch (Exception e) {
                log.error("Failed to save client discovery: {}:{}", e.getClass().getName(), e.getMessage());
            }
        }
    }

    @Cacheable(value = "clientDiscoveryMetrics#1800000", key = "#from+'-'+#to")
    public ClientDiscoveryMetrics getMetrics(OffsetDateTime from, OffsetDateTime to) {
        return new ClientDiscoveryMetrics(
                clientDiscoveryRepository.count(),
                clientDiscoveryRepository.countByFoundAtBetween(from, to)
        );
    }

    public ClientDiscoveryDto toDto(ClientDiscovery clientDiscovery) {
        return ClientDiscoveryDto.builder()
                .hash(clientDiscovery.getHash())
                .clientName(clientDiscovery.getClientName())
                .peerId(clientDiscovery.getPeerId())
                .foundAt(clientDiscovery.getFoundAt())
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

    public record ClientDiscoveryMetrics(
            long total,
            long recent
    ) implements Serializable {
    }
}
