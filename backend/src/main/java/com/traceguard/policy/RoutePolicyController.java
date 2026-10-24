package com.traceguard.policy;

import com.traceguard.audit.AuditService;
import com.traceguard.common.Errors.BadRequestException;
import com.traceguard.common.Errors.ConflictException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.policy.RoutePolicy.Step;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/route-policies")
public class RoutePolicyController {

    public record StepDto(@NotNull Long checkpointId, @Min(1) Integer maxHoursFromPrevious) {}
    public record PolicyRequest(@NotBlank String productType, @Min(1) int maxTotalHours, @NotEmpty List<@Valid StepDto> steps) {}
    public record UpdateRequest(@Min(1) int maxTotalHours, @NotEmpty List<@Valid StepDto> steps) {}
    public record PolicyResponse(Long id, String productType, int maxTotalHours, List<StepDto> steps) {}

    private final RoutePolicyRepository policies;
    private final CheckpointRepository checkpoints;
    private final AuditService audit;

    public RoutePolicyController(RoutePolicyRepository policies, CheckpointRepository checkpoints, AuditService audit) {
        this.policies = policies;
        this.checkpoints = checkpoints;
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyResponse create(@Valid @RequestBody PolicyRequest req) {
        if (policies.existsByProductType(req.productType())) {
            throw new ConflictException("A route policy for product type '" + req.productType() + "' already exists");
        }
        RoutePolicy p = policies.save(new RoutePolicy(req.productType(), req.maxTotalHours(), toSteps(req.steps())));
        audit.record("ROUTE_POLICY_CREATED", p.getProductType());
        return toResponse(p);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PolicyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        RoutePolicy p = policies.findById(id).orElseThrow(() -> new NotFoundException("Route policy " + id + " not found"));
        p.replace(req.maxTotalHours(), toSteps(req.steps()));
        audit.record("ROUTE_POLICY_UPDATED", p.getProductType());
        return toResponse(p);
    }

    @GetMapping
    public List<PolicyResponse> list() {
        return policies.findAll().stream().map(RoutePolicyController::toResponse).toList();
    }

    private List<Step> toSteps(List<StepDto> dtos) {
        var seen = new HashSet<Long>();
        for (StepDto d : dtos) {
            if (!checkpoints.existsById(d.checkpointId())) {
                throw new NotFoundException("Checkpoint " + d.checkpointId() + " not found");
            }
            if (!seen.add(d.checkpointId())) {
                throw new BadRequestException("Checkpoint " + d.checkpointId() + " appears twice in the route");
            }
        }
        return dtos.stream().map(d -> new Step(d.checkpointId(), d.maxHoursFromPrevious())).toList();
    }

    private static PolicyResponse toResponse(RoutePolicy p) {
        return new PolicyResponse(p.getId(), p.getProductType(), p.getMaxTotalHours(),
                p.getSteps().stream().map(s -> new StepDto(s.getCheckpointId(), s.getMaxHoursFromPrevious())).toList());
    }
}
