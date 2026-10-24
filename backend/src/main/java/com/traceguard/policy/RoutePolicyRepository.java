package com.traceguard.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoutePolicyRepository extends JpaRepository<RoutePolicy, Long> {
    Optional<RoutePolicy> findByProductType(String productType);
    boolean existsByProductType(String productType);
}
