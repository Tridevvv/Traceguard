package com.traceguard.org;

import org.springframework.data.jpa.repository.JpaRepository;

/** Grouped to keep the small repositories together. */
public final class OrgRepositories {

    private OrgRepositories() {}

    public interface OrganizationRepository extends JpaRepository<Organization, Long> {
        boolean existsByName(String name);
    }

    public interface CheckpointRepository extends JpaRepository<Checkpoint, Long> {}
}
