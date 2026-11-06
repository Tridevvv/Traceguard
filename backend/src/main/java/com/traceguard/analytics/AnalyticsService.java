package com.traceguard.analytics;

import com.traceguard.alert.Alert;
import com.traceguard.alert.AlertRepository;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.ledger.ScanEventRepository;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates for the dashboards. Days are UTC calendar days and the window always ends with today.
 * Aggregation is done in Java over the rows of the window, which is simple and database-independent
 * and fine at this project's scale (the window is capped at one year).
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    public static final int MAX_DAYS = 365;

    /** Which tags a caller may see: everything (admin, auditor) or only an organization's own tags. */
    public record Scope(Set<String> tagIds) {
        public static Scope all() { return new Scope(null); }
        public boolean includes(String tagId) { return tagIds == null || tagIds.contains(tagId); }
    }

    public record DailyCount(LocalDate date, long count) {}
    public record NameCount(String name, long count) {}
    public record VerdictCounts(long authentic, long suspect, long counterfeit, long unverified) {}
    public record DailyVerdicts(LocalDate date, long authentic, long suspect, long counterfeit) {}
    public record VerdictsReport(VerdictCounts current, List<DailyVerdicts> daily) {}
    public record AlertsReport(long open, List<NameCount> byCode, List<NameCount> bySeverity, List<DailyCount> daily) {}
    public record CheckpointActivity(Long checkpointId, String name, String locationName, long scans) {}
    public record Summary(long tagsIssued, long scansLast24Hours, long scansLast7Days, long openAlerts, VerdictCounts verdicts) {}

    private final ScanEventRepository events;
    private final TagRepository tags;
    private final ProductRepository products;
    private final AlertRepository alerts;
    private final VerificationRecordRepository records;
    private final CheckpointRepository checkpoints;
    private final Clock clock;

    public AnalyticsService(ScanEventRepository events, TagRepository tags, ProductRepository products,
                            AlertRepository alerts, VerificationRecordRepository records,
                            CheckpointRepository checkpoints, Clock clock) {
        this.events = events;
        this.tags = tags;
        this.products = products;
        this.alerts = alerts;
        this.records = records;
        this.checkpoints = checkpoints;
        this.clock = clock;
    }

    public Scope scopeOfOrganization(Long organizationId) {
        if (organizationId == null) return new Scope(Set.of());
        return new Scope(new HashSet<>(products.tagIdsOfOrganization(organizationId)));
    }

    public Summary summary(Scope scope) {
        Instant now = clock.instant();
        long day = countEvents(scope, now.minus(Duration.ofHours(24)), now);
        long week = countEvents(scope, now.minus(Duration.ofDays(7)), now);
        long open = alerts.findByStatusOrderByIdDesc(Alert.Status.OPEN).stream().filter(a -> scope.includes(a.getTagId())).count();
        return new Summary(tagsInScope(scope), day, week, open, currentVerdicts(scope));
    }

    public List<DailyCount> scanVolume(Scope scope, int days) {
        Window w = window(days);
        Map<LocalDate, Long> perDay = events.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(w.from, w.to).stream()
                .filter(e -> scope.includes(e.getTagId()))
                .collect(Collectors.groupingBy(e -> day(e.getOccurredAt()), Collectors.counting()));
        return w.days().stream().map(d -> new DailyCount(d, perDay.getOrDefault(d, 0L))).toList();
    }

    public VerdictsReport verdicts(Scope scope, int days) {
        Window w = window(days);
        Map<LocalDate, Map<Result, Long>> perDay = records.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(w.from, w.to).stream()
                .filter(r -> scope.includes(r.getTagId()))
                .collect(Collectors.groupingBy(r -> day(r.getCreatedAt()),
                        Collectors.groupingBy(VerificationRecord::getVerdict, Collectors.counting())));
        List<DailyVerdicts> daily = w.days().stream().map(d -> {
            Map<Result, Long> m = perDay.getOrDefault(d, Map.of());
            return new DailyVerdicts(d, m.getOrDefault(Result.AUTHENTIC, 0L), m.getOrDefault(Result.SUSPECT, 0L),
                    m.getOrDefault(Result.COUNTERFEIT, 0L));
        }).toList();
        return new VerdictsReport(currentVerdicts(scope), daily);
    }

    public AlertsReport alerts(Scope scope, int days) {
        Window w = window(days);
        List<Alert> inWindow = alerts.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(w.from, w.to).stream()
                .filter(a -> scope.includes(a.getTagId())).toList();
        Map<LocalDate, Long> perDay = inWindow.stream().collect(Collectors.groupingBy(a -> day(a.getCreatedAt()), Collectors.counting()));
        long open = alerts.findByStatusOrderByIdDesc(Alert.Status.OPEN).stream().filter(a -> scope.includes(a.getTagId())).count();
        return new AlertsReport(open,
                ranked(inWindow, Alert::getCode),
                ranked(inWindow, a -> a.getSeverity().name()),
                w.days().stream().map(d -> new DailyCount(d, perDay.getOrDefault(d, 0L))).toList());
    }

    public List<CheckpointActivity> checkpointActivity(Scope scope, int days) {
        Window w = window(days);
        Map<Long, Long> perCheckpoint = events.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(w.from, w.to).stream()
                .filter(e -> scope.includes(e.getTagId()))
                .collect(Collectors.groupingBy(ScanEvent::getCheckpointId, Collectors.counting()));
        Map<Long, Checkpoint> byId = checkpoints.findAllById(perCheckpoint.keySet()).stream()
                .collect(Collectors.toMap(Checkpoint::getId, Function.identity()));
        return perCheckpoint.entrySet().stream()
                .map(e -> {
                    Checkpoint c = byId.get(e.getKey());
                    return new CheckpointActivity(e.getKey(), c != null ? c.getName() : "Checkpoint " + e.getKey(),
                            c != null ? c.getLocationName() : "", e.getValue());
                })
                .sorted(Comparator.comparingLong(CheckpointActivity::scans).reversed().thenComparing(CheckpointActivity::name))
                .toList();
    }

    // ---- helpers ----

    private VerdictCounts currentVerdicts(Scope scope) {
        Map<Result, Long> counts = records.latestPerTag().stream()
                .filter(r -> scope.includes(r.getTagId()))
                .collect(Collectors.groupingBy(VerificationRecord::getVerdict, Collectors.counting()));
        long a = counts.getOrDefault(Result.AUTHENTIC, 0L);
        long s = counts.getOrDefault(Result.SUSPECT, 0L);
        long c = counts.getOrDefault(Result.COUNTERFEIT, 0L);
        return new VerdictCounts(a, s, c, Math.max(0, tagsInScope(scope) - a - s - c));
    }

    private long tagsInScope(Scope scope) {
        return scope.tagIds() == null ? tags.count() : scope.tagIds().size();
    }

    private long countEvents(Scope scope, Instant from, Instant to) {
        return events.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(from, to).stream()
                .filter(e -> scope.includes(e.getTagId())).count();
    }

    private static <T> List<NameCount> ranked(List<T> items, Function<T, String> key) {
        return items.stream().collect(Collectors.groupingBy(key, Collectors.counting())).entrySet().stream()
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(NameCount::count).reversed().thenComparing(NameCount::name))
                .toList();
    }

    private static LocalDate day(Instant t) {
        return t.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private record Window(Instant from, Instant to, LocalDate first, int length) {
        List<LocalDate> days() {
            return java.util.stream.IntStream.range(0, length).mapToObj(first::plusDays).toList();
        }
    }

    private Window window(int days) {
        int n = Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate first = today.minusDays(n - 1L);
        return new Window(first.atStartOfDay(ZoneOffset.UTC).toInstant(),
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), first, n);
    }
}
