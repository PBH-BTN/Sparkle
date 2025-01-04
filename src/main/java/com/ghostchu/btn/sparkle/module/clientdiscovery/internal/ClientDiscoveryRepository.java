package com.ghostchu.btn.sparkle.module.clientdiscovery.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Repository
public interface ClientDiscoveryRepository extends SparkleCommonRepository<ClientDiscovery, Long> {
    Page<ClientDiscovery> findByOrderByFoundAtDesc(Pageable pageable);

    long countByFoundAtBetween(OffsetDateTime foundAtStart, OffsetDateTime foundAtEnd);

    long deleteAllByClientNameLike(String clientName);

    @Modifying
    @Transactional
    @Query(value = "INSERT into clientdiscovery (hash, clientName, peerId, foundAt) values (:hash, :clientName, :peerId, :foundAt) on conflict (hash) do nothing", nativeQuery = true)
    void saveIgnoreConflict(
            @Param("hash") Long hash,
            @Param("clientName") String clientName,
            @Param("peerId") String peerId,
            @Param("foundAt") OffsetDateTime foundAt
    );

}