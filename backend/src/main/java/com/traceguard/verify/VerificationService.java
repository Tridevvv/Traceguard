package com.traceguard.verify;

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
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.policy.RoutePolicy;
import com.traceguard.policy.RoutePolicyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Decides AUTHENTIC / SUSPECT / COUNTERFEIT for a tag. It loads the tag's data once, runs every
 * {@link VerificationCheck} bean, and takes the worst impact among their reasons as the verdict.
 * The outcome is then published as a {@link VerdictIssued} event for the alert center and analytics.
 */
@Service
public class VerificationService {

    private final TagRepository tags;
    private final BatchRepository batches;
    private final ProductRepository products;
    private final RoutePolicyRepository policies;
    private final CheckpointRepository checkpoints;
    private final LedgerService ledger;
    private final List<VerificationCheck> checks;
    private final ApplicationEventPublisher events;

    public VerificationService(TagRepository tags, BatchRepository batches, ProductRepository products,
                               RoutePolicyRepository policies, CheckpointRepository checkpoints, LedgerService ledger,
                               List<VerificationCheck> checks, ApplicationEventPublisher events) {
        this.tags = tags;
        this.batches = batches;
        this.products = products;
        this.policies = policies;
        this.checkpoints = checkpoints;
        this.ledger = ledger;
        this.checks = checks;
        this.events = events;
    }

    /**
     * @param raiseAlerts when true, every SUSPECT/COUNTERFEIT finding raises an alert (deduplicated while open)
     * @param alertOnUnregistered whether a lookup of a never-issued tag may raise an alert; the public endpoint
     *                            passes false so random guesses cannot flood the alert center
     */
    @Transactional
    public Verdict verify(String tagId, boolean raiseAlerts, boolean alertOnUnregistered) {
        Optional<Tag> tag = tags.findById(tagId);
        if (tag.isEmpty()) {
            Reason r = new Reason("UNREGISTERED_TAG", Result.COUNTERFEIT,
                    "This tag ID was never issued by a manufacturer", null);
            Verdict verdict = new Verdict(tagId, Result.COUNTERFEIT, List.of(r), List.of(), Instant.now());
            events.publishEvent(new VerdictIssued(verdict, false, raiseAlerts && alertOnUnregistered));
            return verdict;
        }

        VerificationContext ctx = load(tag.get());
        List<Reason> reasons = checks.stream().flatMap(c -> c.check(ctx).stream()).toList();
        Result result = reasons.stream().map(Reason::impact).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(Result.AUTHENTIC);

        Verdict verdict = new Verdict(tagId, result, reasons, timeline(ctx), Instant.now());
        events.publishEvent(new VerdictIssued(verdict, true, raiseAlerts));
        return verdict;
    }

    private VerificationContext load(Tag tag) {
        Batch batch = batches.findById(tag.getBatchId()).orElseThrow();
        Product product = products.findById(batch.getProductId()).orElseThrow();
        List<ScanEvent> history = ledger.eventsFor(tag.getTagId());
        Map<Long, Checkpoint> cps = checkpoints.findAllById(
                        history.stream().map(ScanEvent::getCheckpointId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Checkpoint::getId, c -> c));
        RoutePolicy policy = policies.findByProductType(product.getProductType()).orElse(null);
        return new VerificationContext(tag, batch, product, history, cps, policy);
    }

    private static List<TimelineEntry> timeline(VerificationContext ctx) {
        return ctx.events().stream().map(e -> {
            Checkpoint c = ctx.checkpoints().get(e.getCheckpointId());
            return new TimelineEntry(e.getSeq(), e.getEventType().name(),
                    c != null ? c.getName() : "Checkpoint " + e.getCheckpointId(),
                    c != null ? c.getLocationName() : "", e.getOccurredAt());
        }).toList();
    }
}
