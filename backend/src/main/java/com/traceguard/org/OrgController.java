package com.traceguard.org;

import com.traceguard.audit.AuditService;
import com.traceguard.common.Errors.ConflictException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.crypto.KeyService;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.org.OrgRepositories.OrganizationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrgController {

    public record OrgRequest(@NotBlank String name, @NotNull Organization.Type type) {}
    public record OrgResponse(Long id, String name, Organization.Type type) {}
    public record CheckpointRequest(@NotNull Long organizationId, @NotBlank String name,
                                    @NotBlank String locationName, double latitude, double longitude) {}
    public record CheckpointResponse(Long id, Long organizationId, String name, String locationName,
                                     double latitude, double longitude, String publicKey) {}

    private final OrganizationRepository orgs;
    private final CheckpointRepository checkpoints;
    private final KeyService keys;
    private final AuditService audit;

    public OrgController(OrganizationRepository orgs, CheckpointRepository checkpoints, KeyService keys, AuditService audit) {
        this.orgs = orgs;
        this.checkpoints = checkpoints;
        this.keys = keys;
        this.audit = audit;
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public OrgResponse createOrg(@Valid @RequestBody OrgRequest req) {
        if (orgs.existsByName(req.name())) {
            throw new ConflictException("Organization name already exists");
        }
        Organization o = orgs.save(new Organization(req.name(), req.type()));
        audit.record("ORG_CREATED", o.getName());
        return new OrgResponse(o.getId(), o.getName(), o.getType());
    }

    @GetMapping("/organizations")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<OrgResponse> listOrgs() {
        return orgs.findAll().stream().map(o -> new OrgResponse(o.getId(), o.getName(), o.getType())).toList();
    }

    @PostMapping("/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CheckpointResponse createCheckpoint(@Valid @RequestBody CheckpointRequest req) {
        if (!orgs.existsById(req.organizationId())) {
            throw new NotFoundException("Organization " + req.organizationId() + " not found");
        }
        Checkpoint c = checkpoints.save(new Checkpoint(req.organizationId(), req.name(),
                req.locationName(), req.latitude(), req.longitude()));
        c.setPublicKey(keys.generateFor(c.getId()));
        audit.record("CHECKPOINT_CREATED", c.getName());
        return toResponse(c);
    }

    @GetMapping("/checkpoints")
    public List<CheckpointResponse> listCheckpoints() {
        return checkpoints.findAll().stream().map(OrgController::toResponse).toList();
    }

    private static CheckpointResponse toResponse(Checkpoint c) {
        return new CheckpointResponse(c.getId(), c.getOrganizationId(), c.getName(), c.getLocationName(),
                c.getLatitude(), c.getLongitude(), c.getPublicKey());
    }
}
