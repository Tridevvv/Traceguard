package com.traceguard.report;

import com.traceguard.alert.Alert;
import com.traceguard.alert.AlertRepository;
import com.traceguard.audit.AuditLog;
import com.traceguard.audit.AuditLogRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.common.Errors.BadRequestException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.common.Verdicts.Verdict;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.ledger.ScanEventRepository;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.verify.VerificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds report content. Date ranges are inclusive UTC days; a report is limited to 10,000 rows. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public static final int MAX_ROWS = 10_000;
    public static final int MAX_RANGE_DAYS = 366;

    private final AuditLogRepository auditLog;
    private final AlertRepository alerts;
    private final ScanEventRepository events;
    private final CheckpointRepository checkpoints;
    private final TagRepository tags;
    private final VerificationService verification;

    public ReportService(AuditLogRepository auditLog, AlertRepository alerts, ScanEventRepository events,
                         CheckpointRepository checkpoints, TagRepository tags, VerificationService verification) {
        this.auditLog = auditLog;
        this.alerts = alerts;
        this.events = events;
        this.checkpoints = checkpoints;
        this.tags = tags;
        this.verification = verification;
    }

    /** Validates the range and returns {startInclusive, endExclusive}. */
    public static Instant[] range(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new BadRequestException("'from' must not be after 'to'");
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BadRequestException("The date range may be at most " + MAX_RANGE_DAYS + " days");
        }
        return new Instant[]{from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()};
    }

    /** Who did what: every recorded user action in the range. */
    public ReportTable userActions(LocalDate from, LocalDate to) {
        Instant[] r = range(from, to);
        List<AuditLog> rows = capped(auditLog.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(r[0], r[1], page()));
        return new ReportTable("User activity audit report", period(from, to),
                List.of("Time (UTC)", "User", "Action", "Target"),
                rows.stream().map(a -> List.of(a.getCreatedAt().toString(), nz(a.getUserEmail()), a.getAction(), nz(a.getTarget()))).toList());
    }

    public ReportTable alertReport(LocalDate from, LocalDate to) {
        Instant[] r = range(from, to);
        List<Alert> rows = capped(alerts.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(r[0], r[1], page()));
        return new ReportTable("Alerts report", period(from, to),
                List.of("Created (UTC)", "Tag", "Code", "Severity", "Status", "Message"),
                rows.stream().map(a -> List.of(a.getCreatedAt().toString(), a.getTagId(), a.getCode(),
                        a.getSeverity().name(), a.getStatus().name(), a.getMessage())).toList());
    }

    /** The ledger itself: every signed event in the range. */
    public ReportTable ledgerReport(LocalDate from, LocalDate to) {
        Instant[] r = range(from, to);
        List<ScanEvent> rows = capped(events.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByIdAsc(r[0], r[1], page()));
        Map<Long, Checkpoint> cps = checkpoints.findAllById(rows.stream().map(ScanEvent::getCheckpointId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Checkpoint::getId, Function.identity()));
        return new ReportTable("Ledger report", period(from, to),
                List.of("Time (UTC)", "Tag", "Seq", "Event", "Checkpoint", "Hash", "Previous hash"),
                rows.stream().map(e -> List.of(e.getOccurredAt().toString(), e.getTagId(), String.valueOf(e.getSeq()),
                        e.getEventType().name(), cps.containsKey(e.getCheckpointId()) ? cps.get(e.getCheckpointId()).getName() : "Checkpoint " + e.getCheckpointId(),
                        e.getHash(), e.getPrevHash())).toList());
    }

    /** One product's history: verdict, reasons and the full timeline. */
    public ReportTable tagHistory(String tagId) {
        if (!tags.existsById(tagId)) throw new NotFoundException("Tag " + tagId + " not found");
        Verdict v = verification.verify(tagId, false, false);
        List<String> notes = new ArrayList<>();
        notes.add("Tag: " + tagId + "   Verdict: " + v.verdict());
        v.reasons().forEach(reason -> notes.add("  " + reason.code() + (reason.impact() == null ? " (info)" : " [" + reason.impact() + "]") + ": " + reason.message()));
        List<List<String>> rows = new ArrayList<>();
        v.timeline().forEach(t -> rows.add(List.of(tagId, v.verdict().name(), String.valueOf(t.seq()), t.eventType(),
                t.checkpointName(), t.locationName(), t.occurredAt().toString())));
        if (rows.isEmpty()) rows.add(List.of(tagId, v.verdict().name(), "", "", "", "", ""));
        return new ReportTable("Product history report", notes,
                List.of("Tag", "Verdict", "Seq", "Event", "Checkpoint", "Location", "Time (UTC)"), rows);
    }

    private static Pageable page() {
        return PageRequest.of(0, MAX_ROWS + 1);
    }

    private static <T> List<T> capped(List<T> rows) {
        if (rows.size() > MAX_ROWS) {
            throw new BadRequestException("The report would exceed " + MAX_ROWS + " rows; choose a shorter date range");
        }
        return rows;
    }

    private static List<String> period(LocalDate from, LocalDate to) {
        return List.of("Period: " + from + " to " + to + " (UTC, inclusive)");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
