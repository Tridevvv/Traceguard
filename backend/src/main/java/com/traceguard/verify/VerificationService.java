package com.traceguard.verify;

import com.traceguard.alert.Alert.Severity;
import com.traceguard.alert.AlertService;
import com.traceguard.anomaly.AnomalyDetector;
import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import com.traceguard.catalog.CatalogRepositories.BatchRepository;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.common.Verdicts.TimelineEntry;
import com.traceguard.common.Verdicts.Verdict;
import com.traceguard.ledger.LedgerService;
import com.traceguard.ledger.LedgerService.ChainReport;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.policy.RoutePolicy;
import com.traceguard.policy.RoutePolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Decides AUTHENTIC / SUSPECT / COUNTERFEIT for a tag. Every check adds machine-readable reasons;
 * the verdict is the worst impact among them.
 */
@Service
public class VerificationService {

    private final TagRepository tags;
    private final BatchRepository batches;
    private final ProductRepository products;
    private final RoutePolicyRepository policies;
    private final CheckpointRepository checkpoints;
    private final LedgerService ledger;
    private final RouteChecker routeChecker;
    private final AnomalyDetector anomalies;
    private final AlertService alerts;

    public VerificationService(TagRepository tags, BatchRepository batches, ProductRepository products,
                               RoutePolicyRepository policies, CheckpointRepository checkpoints, LedgerService ledger,
                               RouteChecker routeChecker, AnomalyDetector anomalies, AlertService alerts) {
        this.tags = tags;
        this.batches = batches;
        this.products = products;
        this.policies = policies;
        this.checkpoints = checkpoints;
        this.ledger = ledger;
        this.routeChecker = routeChecker;
        this.anomalies = anomalies;
        this.alerts = alerts;
    }

    /**
     * @param raiseAlerts when true, every SUSPECT/COUNTERFEIT finding raises an alert (deduplicated while open)
     * @param alertOnUnregistered whether a lookup of a never-issued tag may raise an alert; the public endpoint
     *                            passes false so random guesses cannot flood the alert center
     */
    @Transactional
    public Verdict verify(String tagId, boolean raiseAlerts, boolean alertOnUnregistered) {
        List<Reason> reasons = new ArrayList<>();

        Optional<Tag> tag = tags.findById(tagId);
        if (tag.isEmpty()) {
            Reason r = new Reason("UNREGISTERED_TAG", Result.COUNTERFEIT,
                    "This tag ID was never issued by a manufacturer", null);
            reasons.add(r);
            if (raiseAlerts && alertOnUnregistered) raise(tagId, r);
            return new Verdict(tagId, Result.COUNTERFEIT, reasons, List.of(), Instant.now());
        }

        Batch batch = batches.findById(tag.get().getBatchId()).orElseThrow();
        Product product = products.findById(batch.getProductId()).orElseThrow();
        if (batch.isRecalled()) {
            reasons.add(new Reason("BATCH_RECALLED", Result.SUSPECT,
                    "Batch " + batch.getLabel() + " has been recalled by the manufacturer", null));
        }

        List<ScanEvent> events = ledger.eventsFor(tagId);
        if (events.isEmpty()) {
            reasons.add(new Reason("NO_HISTORY", Result.SUSPECT,
                    "No scans have been recorded for this tag, so its journey cannot be confirmed", null));
        }

        ChainReport chain = ledger.checkChain(tagId);
        chain.issues().forEach(i -> reasons.add(new Reason(i.code(), Result.COUNTERFEIT,
                "Event " + i.seq() + ": " + i.message(), i.seq())));

        Map<Long, Checkpoint> cps = checkpoints.findAllById(
                        events.stream().map(ScanEvent::getCheckpointId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Checkpoint::getId, c -> c));
        RoutePolicy policy = policies.findByProductType(product.getProductType()).orElse(null);

        reasons.addAll(routeChecker.check(events, cps, policy));
        reasons.addAll(anomalies.detect(events, cps));

        Result result = reasons.stream().map(Reason::impact).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(Result.AUTHENTIC);

        if (raiseAlerts) {
            reasons.stream().filter(r -> r.impact() != null).forEach(r -> raise(tagId, r));
        }
        List<TimelineEntry> timeline = events.stream().map(e -> {
            Checkpoint c = cps.get(e.getCheckpointId());
            return new TimelineEntry(e.getSeq(), e.getEventType().name(),
                    c != null ? c.getName() : "Checkpoint " + e.getCheckpointId(),
                    c != null ? c.getLocationName() : "", e.getOccurredAt());
        }).toList();
        return new Verdict(tagId, result, reasons, timeline, Instant.now());
    }

    private void raise(String tagId, Reason r) {
        Severity severity = r.impact() == Result.COUNTERFEIT ? Severity.HIGH : Severity.MEDIUM;
        alerts.raise(tagId, r.code(), severity, r.message());
    }
}
