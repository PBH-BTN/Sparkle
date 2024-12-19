package com.ghostchu.btn.sparkle.module.clientdiscovery.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface ClientDiscoveryRepository extends SparkleCommonRepository<ClientDiscovery, Long> {
    Page<ClientDiscovery> findByOrderByFoundAtDesc(Pageable pageable);

    long countByFoundAtBetween(OffsetDateTime foundAtStart, OffsetDateTime foundAtEnd);

    long deleteAllByClientNameLike(String clientName);
}