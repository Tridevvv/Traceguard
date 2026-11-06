package com.traceguard.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Optional<Alert> findFirstByTagIdAndCodeAndStatus(String tagId, String code, Alert.Status status);
    List<Alert> findByStatusOrderByIdDesc(Alert.Status status);
    List<Alert> findAllByOrderByIdDesc();
    long countByStatus(Alert.Status status);
    List<Alert> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);
    List<Alert> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(Instant from, Instant to, Pageable page);
}
