package com.traceguard.alert;

import com.traceguard.alert.Alert.Severity;
import com.traceguard.alert.Alert.Status;
import com.traceguard.audit.AuditService;
import com.traceguard.auth.AppUser;
import com.traceguard.auth.AppUserRepository;
import com.traceguard.common.Errors.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    public record AlertResponse(Long id, String tagId, String code, Severity severity, Status status, String message,
                                Instant createdAt, Instant updatedAt) {}
    public record StatusRequest(@NotNull Status status) {}

    private final AlertService service;
    private final AppUserRepository users;
    private final AuditService audit;

    public AlertController(AlertService service, AppUserRepository users, AuditService audit) {
        this.service = service;
        this.users = users;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER','AUDITOR')")
    public List<AlertResponse> list(@RequestParam(required = false) Status status) {
        return service.visibleTo(currentUser(), status).stream().map(AlertController::toResponse).toList();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    public AlertResponse update(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        Alert a = service.updateStatus(currentUser(), id, req.status());
        audit.record("ALERT_" + req.status(), a.getCode() + " on " + a.getTagId());
        return toResponse(a);
    }

    private AppUser currentUser() {
        String email = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static AlertResponse toResponse(Alert a) {
        return new AlertResponse(a.getId(), a.getTagId(), a.getCode(), a.getSeverity(), a.getStatus(), a.getMessage(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
