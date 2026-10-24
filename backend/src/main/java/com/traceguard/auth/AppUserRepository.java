package com.traceguard.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<AppUser> findByRoleAndEnabledTrue(Role role);
    List<AppUser> findByRoleAndOrganizationIdAndEnabledTrue(Role role, Long organizationId);
}
