package com.traceguard.org;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final OrgService service;

    public OrgController(OrgService service) {
        this.service = service;
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public OrgResponse createOrg(@Valid @RequestBody OrgRequest req) {
        return toResponse(service.createOrganization(req.name(), req.type()));
    }

    @GetMapping("/organizations")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<OrgResponse> listOrgs() {
        return service.organizations().stream().map(OrgController::toResponse).toList();
    }

    @PostMapping("/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CheckpointResponse createCheckpoint(@Valid @RequestBody CheckpointRequest req) {
        return toResponse(service.createCheckpoint(req.organizationId(), req.name(), req.locationName(),
                req.latitude(), req.longitude()));
    }

    @GetMapping("/checkpoints")
    public List<CheckpointResponse> listCheckpoints() {
        return service.checkpoints().stream().map(OrgController::toResponse).toList();
    }

    private static OrgResponse toResponse(Organization o) {
        return new OrgResponse(o.getId(), o.getName(), o.getType());
    }

    private static CheckpointResponse toResponse(Checkpoint c) {
        return new CheckpointResponse(c.getId(), c.getOrganizationId(), c.getName(), c.getLocationName(),
                c.getLatitude(), c.getLongitude(), c.getPublicKey());
    }
}
