package com.ghostchu.btn.sparkle.module.clientdiscovery.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;

@Repository
public interface ClientDiscoveryRepository extends SparkleCommonRepository<ClientDiscovery, Long> {
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE ClientDiscovery cd SET cd.lastSeenAt = ?2, cd.lastSeenBy = ?3 WHERE cd.hash IN ?1")
    void updateLastSeen(Collection<Long> ids, OffsetDateTime lastSeenAt, User lastSeenBy);

    Page<ClientDiscovery> findByOrderByFoundAtDesc(Pageable pageable);

    long countByFoundAtBetween(OffsetDateTime foundAtStart, OffsetDateTime foundAtEnd);

    long deleteAllByClientNameLike(String clientName);
}