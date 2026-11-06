package com.traceguard.policy;

import com.traceguard.audit.AuditService;
import com.traceguard.common.Errors.BadRequestException;
import com.traceguard.common.Errors.ConflictException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.policy.RoutePolicy.Step;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/** One route policy per product type; each step must be an existing checkpoint, used once. */
@Service
public class RoutePolicyService {

    private final RoutePolicyRepository policies;
    private final CheckpointRepository checkpoints;
    private final AuditService audit;

    public RoutePolicyService(RoutePolicyRepository policies, CheckpointRepository checkpoints, AuditService audit) {
        this.policies = policies;
        this.checkpoints = checkpoints;
        this.audit = audit;
    }

    public RoutePolicy create(String productType, int maxTotalHours, List<Step> steps) {
        if (policies.existsByProductType(productType)) {
            throw new ConflictException("A route policy for product type '" + productType + "' already exists");
        }
        validate(steps);
        RoutePolicy p = policies.save(new RoutePolicy(productType, maxTotalHours, steps));
        audit.record("ROUTE_POLICY_CREATED", p.getProductType());
        return p;
    }

    @Transactional
    public RoutePolicy update(Long id, int maxTotalHours, List<Step> steps) {
        RoutePolicy p = policies.findById(id).orElseThrow(() -> new NotFoundException("Route policy " + id + " not found"));
        validate(steps);
        p.replace(maxTotalHours, steps);
        audit.record("ROUTE_POLICY_UPDATED", p.getProductType());
        return p;
    }

    public List<RoutePolicy> list() {
        return policies.findAll();
    }

    private void validate(List<Step> steps) {
        var seen = new HashSet<Long>();
        for (Step s : steps) {
            if (!checkpoints.existsById(s.getCheckpointId())) {
                throw new NotFoundException("Checkpoint " + s.getCheckpointId() + " not found");
            }
            if (!seen.add(s.getCheckpointId())) {
                throw new BadRequestException("Checkpoint " + s.getCheckpointId() + " appears twice in the route");
            }
        }
    }
}
