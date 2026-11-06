package com.traceguard.policy;

import com.traceguard.policy.RoutePolicy.Step;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route-policies")
public class RoutePolicyController {

    public record StepDto(@NotNull Long checkpointId, @Min(1) Integer maxHoursFromPrevious) {}
    public record PolicyRequest(@NotBlank String productType, @Min(1) int maxTotalHours, @NotEmpty List<@Valid StepDto> steps) {}
    public record UpdateRequest(@Min(1) int maxTotalHours, @NotEmpty List<@Valid StepDto> steps) {}
    public record PolicyResponse(Long id, String productType, int maxTotalHours, List<StepDto> steps) {}

    private final RoutePolicyService service;

    public RoutePolicyController(RoutePolicyService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyResponse create(@Valid @RequestBody PolicyRequest req) {
        return toResponse(service.create(req.productType(), req.maxTotalHours(), toSteps(req.steps())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return toResponse(service.update(id, req.maxTotalHours(), toSteps(req.steps())));
    }

    @GetMapping
    public List<PolicyResponse> list() {
        return service.list().stream().map(RoutePolicyController::toResponse).toList();
    }

    private static List<Step> toSteps(List<StepDto> dtos) {
        return dtos.stream().map(d -> new Step(d.checkpointId(), d.maxHoursFromPrevious())).toList();
    }

    private static PolicyResponse toResponse(RoutePolicy p) {
        return new PolicyResponse(p.getId(), p.getProductType(), p.getMaxTotalHours(),
                p.getSteps().stream().map(s -> new StepDto(s.getCheckpointId(), s.getMaxHoursFromPrevious())).toList());
    }
}
