package com.traceguard.verify;

import com.traceguard.audit.AuditService;
import com.traceguard.common.Verdicts.Verdict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VerifyController {

    private final VerificationService verification;
    private final AuditService audit;

    public VerifyController(VerificationService verification, AuditService audit) {
        this.verification = verification;
        this.audit = audit;
    }

    /** Staff verification: any signed-in role. SUSPECT/COUNTERFEIT findings raise alerts. */
    @GetMapping("/verify/{tagId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER','DISTRIBUTOR','RETAILER','AUDITOR')")
    public Verdict verify(@PathVariable String tagId) {
        Verdict v = verification.verify(tagId, true, true);
        audit.record("VERIFY", tagId + " -> " + v.verdict());
        return v;
    }

    /**
     * Public consumer verification (no login, rate limited). Raises alerts for known tags with problems, but
     * never for unknown tag IDs, so guessing IDs cannot flood the alert center.
     */
    @GetMapping("/public/verify/{tagId}")
    public Verdict publicVerify(@PathVariable String tagId) {
        return verification.verify(tagId, true, false);
    }
}
