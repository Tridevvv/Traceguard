package com.traceguard.org;

import com.traceguard.audit.AuditService;
import com.traceguard.common.Errors.ConflictException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.crypto.KeyService;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.org.OrgRepositories.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Organizations and checkpoints. Every new checkpoint gets its own ECDSA key pair. */
@Service
public class OrgService {

    private final OrganizationRepository orgs;
    private final CheckpointRepository checkpoints;
    private final KeyService keys;
    private final AuditService audit;

    public OrgService(OrganizationRepository orgs, CheckpointRepository checkpoints, KeyService keys, AuditService audit) {
        this.orgs = orgs;
        this.checkpoints = checkpoints;
        this.keys = keys;
        this.audit = audit;
    }

    public Organization createOrganization(String name, Organization.Type type) {
        if (orgs.existsByName(name)) {
            throw new ConflictException("Organization name already exists");
        }
        Organization o = orgs.save(new Organization(name, type));
        audit.record("ORG_CREATED", o.getName());
        return o;
    }

    public List<Organization> organizations() {
        return orgs.findAll();
    }

    @Transactional
    public Checkpoint createCheckpoint(Long organizationId, String name, String locationName, double latitude, double longitude) {
        if (!orgs.existsById(organizationId)) {
            throw new NotFoundException("Organization " + organizationId + " not found");
        }
        Checkpoint c = checkpoints.save(new Checkpoint(organizationId, name, locationName, latitude, longitude));
        c.setPublicKey(keys.generateFor(c.getId()));
        audit.record("CHECKPOINT_CREATED", c.getName());
        return c;
    }

    public List<Checkpoint> checkpoints() {
        return checkpoints.findAll();
    }
}
