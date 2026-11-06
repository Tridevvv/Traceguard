package com.traceguard.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, Long> {

    Optional<VerificationRecord> findFirstByTagIdOrderByIdDesc(String tagId);

    List<VerificationRecord> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    /** The most recent outcome of every tag that has been verified. */
    @Query("select r from VerificationRecord r where r.id in (select max(r2.id) from VerificationRecord r2 group by r2.tagId)")
    List<VerificationRecord> latestPerTag();
}
