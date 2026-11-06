package com.traceguard.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.List;

/** Append-only: no delete or update methods are exposed. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog save(AuditLog entry);
    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
    List<AuditLog> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(Instant from, Instant to, Pageable page);
}
