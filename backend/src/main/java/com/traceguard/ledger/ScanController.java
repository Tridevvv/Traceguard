package com.traceguard.ledger;

import com.traceguard.audit.AuditService;
import com.traceguard.ledger.LedgerService.ChainReport;
import com.traceguard.reader.ScanReading;
import com.traceguard.reader.SimulatedReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ScanController {

    public record ScanRequest(@NotBlank String tagId, @NotNull Long checkpointId, @NotNull ScanEvent.Type eventType,
                              String nonce, Instant timestamp) {}
    public record SimulatedScanRequest(@NotBlank String tagId, @NotNull Long checkpointId, @NotNull ScanEvent.Type eventType) {}
    public record EventResponse(Long id, String tagId, Long checkpointId, ScanEvent.Type eventType, Instant occurredAt,
                                int seq, String nonce, String prevHash, String hash, String signature) {}

    private final LedgerService ledger;
    private final SimulatedReader reader;
    private final AuditService audit;

    public ScanController(LedgerService ledger, SimulatedReader reader, AuditService audit) {
        this.ledger = ledger;
        this.reader = reader;
        this.audit = audit;
    }

    /** Reader/gateway entry point: creates a signed, hash-linked event. */
    @PostMapping("/scans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER','DISTRIBUTOR','RETAILER')")
    public EventResponse scan(@Valid @RequestBody ScanRequest req) {
        ScanEvent e = ledger.append(req.tagId(), req.checkpointId(), req.eventType(), req.timestamp(), req.nonce());
        audit.record("SCAN", req.tagId() + " @ checkpoint " + req.checkpointId());
        return toResponse(e);
    }

    /** Scan console: the simulated reader supplies the timestamp and nonce. */
    @PostMapping("/scans/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER','DISTRIBUTOR','RETAILER')")
    public EventResponse simulate(@Valid @RequestBody SimulatedScanRequest req) {
        ScanReading r = reader.scan(req.tagId(), req.checkpointId());
        ScanEvent e = ledger.append(r.tagId(), r.checkpointId(), req.eventType(), r.readAt(), r.nonce());
        audit.record("SCAN_SIMULATED", req.tagId() + " @ checkpoint " + req.checkpointId());
        return toResponse(e);
    }

    @GetMapping("/tags/{tagId}/events")
    public List<EventResponse> events(@PathVariable String tagId) {
        return ledger.eventsFor(tagId).stream().map(ScanController::toResponse).toList();
    }

    /** Interim integrity check of one tag's chain; the full verdict engine arrives in Phase 3. */
    @GetMapping("/tags/{tagId}/chain-check")
    public ChainReport chainCheck(@PathVariable String tagId) {
        return ledger.checkChain(tagId);
    }

    private static EventResponse toResponse(ScanEvent e) {
        return new EventResponse(e.getId(), e.getTagId(), e.getCheckpointId(), e.getEventType(), e.getOccurredAt(),
                e.getSeq(), e.getNonce(), e.getPrevHash(), e.getHash(), e.getSignature());
    }
}
