package com.traceguard.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Optional<Alert> findFirstByTagIdAndCodeAndStatus(String tagId, String code, Alert.Status status);
    List<Alert> findByStatusOrderByIdDesc(Alert.Status status);
    List<Alert> findAllByOrderByIdDesc();
}
