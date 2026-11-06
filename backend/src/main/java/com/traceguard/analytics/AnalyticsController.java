package com.traceguard.analytics;

import com.traceguard.analytics.AnalyticsService.*;
import com.traceguard.auth.AppUser;
import com.traceguard.auth.CurrentUser;
import com.traceguard.auth.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard data. Admins and auditors see everything; a manufacturer sees only its own products' tags.
 * Distributors and retailers have no analytics access.
 */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANUFACTURER')")
public class AnalyticsController {

    private final AnalyticsService service;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public Summary summary() {
        return service.summary(scope());
    }

    /** Scans per day, oldest first, with zero-filled days. {@code days} is 1 to 365 (default 30). */
    @GetMapping("/scan-volume")
    public List<DailyCount> scanVolume(@RequestParam(defaultValue = "30") int days) {
        return service.scanVolume(scope(), days);
    }

    @GetMapping("/verdicts")
    public VerdictsReport verdicts(@RequestParam(defaultValue = "30") int days) {
        return service.verdicts(scope(), days);
    }

    @GetMapping("/alerts")
    public AlertsReport alerts(@RequestParam(defaultValue = "30") int days) {
        return service.alerts(scope(), days);
    }

    @GetMapping("/checkpoints")
    public List<CheckpointActivity> checkpoints(@RequestParam(defaultValue = "30") int days) {
        return service.checkpointActivity(scope(), days);
    }

    private Scope scope() {
        AppUser me = currentUser.get();
        return me.getRole() == Role.MANUFACTURER ? service.scopeOfOrganization(me.getOrganizationId()) : Scope.all();
    }
}
