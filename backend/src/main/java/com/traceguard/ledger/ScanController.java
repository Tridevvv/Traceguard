package com.traceguard.ledger;

import com.traceguard.audit.AuditService;
import com.traceguard.ledger.LedgerService.ChainReport;
import com.traceguard.reader.ScanReading;
import com.traceguard.reader.SimulatedReader;
import com.traceguard.verify.VerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    public record ScanRequest(@NotBlank String tagId, @NotNull Long checkpointId, @NotNull ScanEvent.Type eventType,
                              String nonce, Instant timestamp) {}
    /** {@code timestamp} is optional; set it to simulate a scan that happened earlier (useful for demos and tests). */
    public record SimulatedScanRequest(@NotBlank String tagId, @NotNull Long checkpointId, @NotNull ScanEvent.Type eventType,
                                       Instant timestamp) {}
    public record EventResponse(Long id, String tagId, Long checkpointId, ScanEvent.Type eventType, Instant occurredAt,
                                int seq, String nonce, String prevHash, String hash, String signature) {}

    private final LedgerService ledger;
    private final SimulatedReader reader;
    private final VerificationService verification;
    private final AuditService audit;

    public ScanController(LedgerService ledger, SimulatedReader reader, VerificationService verification, AuditService audit) {
        this.ledger = ledger;
        this.reader = reader;
        this.verification = verification;
        this.audit = audit;
    }

    /** Reader/gateway entry point: creates a signed, hash-linked event, then checks the tag for anomalies. */
    @PostMapping("/scans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER','DISTRIBUTOR','RETAILER')")
    public EventResponse scan(@Valid @RequestBody ScanRequest req) {
        ScanEvent e = ledger.append(req.tagId(), req.checkpointId(), req.eventType(), req.timestamp(), req.nonce());
        audit.record("SCAN", req.tagId() + " @ checkpoint " + req.checkpointId());
        checkAfterScan(req.tagId());
        return toResponse(e);
    }

    /** Scan console: the simulated reader supplies the nonce and, unless given, the timestamp. */
    @PostMapping("/scans/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER','DISTRIBUTOR','RETAILER')")
    public EventResponse simulate(@Valid @RequestBody SimulatedScanRequest req) {
        ScanReading r = reader.scan(req.tagId(), req.checkpointId());
        Instant at = req.timestamp() != null ? req.timestamp() : r.readAt();
        ScanEvent e = ledger.append(r.tagId(), r.checkpointId(), req.eventType(), at, r.nonce());
        audit.record("SCAN_SIMULATED", req.tagId() + " @ checkpoint " + req.checkpointId());
        checkAfterScan(req.tagId());
        return toResponse(e);
    }

    @GetMapping("/tags/{tagId}/events")
    public List<EventResponse> events(@PathVariable String tagId) {
        return ledger.eventsFor(tagId).stream().map(ScanController::toResponse).toList();
    }

    /** Integrity-only check of one tag's chain; /api/verify gives the full verdict. */
    @GetMapping("/tags/{tagId}/chain-check")
    public ChainReport chainCheck(@PathVariable String tagId) {
        return ledger.checkChain(tagId);
    }

    /** Runs the full verification after every scan so cloned tags and bad routes raise alerts immediately. */
    private void checkAfterScan(String tagId) {
        try {
            verification.verify(tagId, true, true);
        } catch (RuntimeException ex) {
            // The scan itself is already recorded; a failing check must not turn it into an error response.
            log.error("Post-scan verification failed for {}", tagId, ex);
        }
    }

    private static EventResponse toResponse(ScanEvent e) {
        return new EventResponse(e.getId(), e.getTagId(), e.getCheckpointId(), e.getEventType(), e.getOccurredAt(),
                e.getSeq(), e.getNonce(), e.getPrevHash(), e.getHash(), e.getSignature());
    }
}
