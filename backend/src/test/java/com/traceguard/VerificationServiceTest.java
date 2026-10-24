package com.traceguard;

import com.traceguard.alert.Alert;
import com.traceguard.alert.AlertRepository;
import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import com.traceguard.catalog.CatalogRepositories.BatchRepository;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.common.Verdicts.Verdict;
import com.traceguard.crypto.KeyService;
import com.traceguard.ledger.LedgerService;
import com.traceguard.ledger.ScanEvent.Type;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.org.OrgRepositories.OrganizationRepository;
import com.traceguard.org.Organization;
import com.traceguard.policy.RoutePolicy;
import com.traceguard.policy.RoutePolicy.Step;
import com.traceguard.policy.RoutePolicyRepository;
import com.traceguard.verify.VerificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end verdict tests against the real services and an in-memory database. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VerificationServiceTest {

    @Autowired VerificationService verification;
    @Autowired LedgerService ledger;
    @Autowired OrganizationRepository orgs;
    @Autowired CheckpointRepository checkpoints;
    @Autowired RoutePolicyRepository policies;
    @Autowired ProductRepository products;
    @Autowired BatchRepository batches;
    @Autowired TagRepository tags;
    @Autowired AlertRepository alerts;
    @Autowired KeyService keys;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    long factory, warehouse, store;
    Batch batch;
    final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        Organization org = orgs.save(new Organization("Org-" + UUID.randomUUID(), Organization.Type.MANUFACTURER));
        factory = checkpoint(org, "Factory", 42.3601, -71.0589);
        warehouse = checkpoint(org, "Warehouse", 42.6526, -73.7562);
        store = checkpoint(org, "Pharmacy", 41.8781, -87.6298);
        String type = "pharma-" + UUID.randomUUID();
        policies.save(new RoutePolicy(type, 168, List.of(new Step(factory, null), new Step(warehouse, 48), new Step(store, 72))));
        Product p = products.save(new Product(org.getId(), "Aspirin", type));
        batch = batches.save(new Batch(p.getId(), "B-1"));
    }

    private long checkpoint(Organization org, String name, double lat, double lon) {
        Checkpoint c = checkpoints.save(new Checkpoint(org.getId(), name, name + " site", lat, lon));
        c.setPublicKey(keys.generateFor(c.getId()));
        return c.getId();
    }

    private String tag() {
        String id = "TG-" + UUID.randomUUID().toString().substring(0, 12);
        tags.save(new Tag(id, batch.getId()));
        return id;
    }

    private void scan(String tag, long cp, Type type, Duration ago) {
        ledger.append(tag, cp, type, now.minus(ago), null);
    }

    private String genuineTag() {
        String t = tag();
        scan(t, factory, Type.REGISTERED, Duration.ofHours(30));
        scan(t, warehouse, Type.RECEIVED, Duration.ofHours(24));
        scan(t, store, Type.DELIVERED, Duration.ofHours(2));
        return t;
    }

    private static List<String> codes(Verdict v) { return v.reasons().stream().map(Reason::code).toList(); }

    @Test
    void genuineJourneyIsAuthentic() {
        Verdict v = verification.verify(genuineTag(), false, true);
        assertEquals(Result.AUTHENTIC, v.verdict(), v.reasons().toString());
        assertEquals(3, v.timeline().size());
        assertEquals("Factory", v.timeline().get(0).checkpointName());
    }

    @Test
    void clonedTagIsCounterfeitAndRaisesHighAlert() {
        String t = tag();
        scan(t, factory, Type.REGISTERED, Duration.ofHours(10));
        ledger.append(t, store, Type.RECEIVED, now.minus(Duration.ofHours(10)).plusSeconds(40), null);

        Verdict v = verification.verify(t, true, true);

        assertEquals(Result.COUNTERFEIT, v.verdict());
        assertTrue(codes(v).contains("DUPLICATE_PRESENCE"));
        Alert a = alerts.findFirstByTagIdAndCodeAndStatus(t, "DUPLICATE_PRESENCE", Alert.Status.OPEN).orElseThrow();
        assertEquals(Alert.Severity.HIGH, a.getSeverity());
    }

    @Test
    void repeatedVerificationDoesNotDuplicateOpenAlerts() {
        String t = tag();
        scan(t, factory, Type.REGISTERED, Duration.ofHours(10));
        ledger.append(t, store, Type.RECEIVED, now.minus(Duration.ofHours(10)).plusSeconds(40), null);

        verification.verify(t, true, true);
        verification.verify(t, true, true);

        long count = alerts.findAll().stream().filter(a -> a.getTagId().equals(t) && a.getCode().equals("DUPLICATE_PRESENCE")).count();
        assertEquals(1, count);
    }

    @Test
    void skippedStepIsSuspect() {
        String t = tag();
        scan(t, factory, Type.REGISTERED, Duration.ofHours(40));
        scan(t, store, Type.RECEIVED, Duration.ofHours(20));
        Verdict v = verification.verify(t, false, true);
        assertEquals(Result.SUSPECT, v.verdict(), v.reasons().toString());
        assertTrue(codes(v).contains("ROUTE_DEVIATION"));
    }

    @Test
    void editedEventIsCounterfeit() {
        String t = genuineTag();
        em.flush();
        jdbc.update("update events set checkpoint_id = ? where tag_id = ? and seq = 2", store, t);
        em.clear();

        Verdict v = verification.verify(t, false, true);

        assertEquals(Result.COUNTERFEIT, v.verdict());
        assertTrue(codes(v).contains("HASH_MISMATCH"), codes(v).toString());
    }

    @Test
    void recalledBatchIsSuspect() {
        String t = genuineTag();
        batch.recall();
        batches.save(batch);
        Verdict v = verification.verify(t, false, true);
        assertEquals(Result.SUSPECT, v.verdict());
        assertTrue(codes(v).contains("BATCH_RECALLED"));
    }

    @Test
    void tagWithoutScansIsSuspect() {
        Verdict v = verification.verify(tag(), false, true);
        assertEquals(Result.SUSPECT, v.verdict());
        assertTrue(codes(v).contains("NO_HISTORY"));
    }

    @Test
    void unknownTagIsCounterfeit() {
        Verdict v = verification.verify("TG-NEVER-ISSUED", false, true);
        assertEquals(Result.COUNTERFEIT, v.verdict());
        assertEquals(List.of("UNREGISTERED_TAG"), codes(v));
    }

    @Test
    void publicLookupOfUnknownTagRaisesNoAlert() {
        String id = "TG-GUESS-" + UUID.randomUUID().toString().substring(0, 8);
        verification.verify(id, true, false);
        assertTrue(alerts.findAll().stream().noneMatch(a -> a.getTagId().equals(id)));
    }

    @Test
    void staffLookupOfUnknownTagRaisesHighAlert() {
        String id = "TG-FAKE-" + UUID.randomUUID().toString().substring(0, 8);
        verification.verify(id, true, true);
        assertTrue(alerts.findAll().stream().anyMatch(a -> a.getTagId().equals(id) && a.getSeverity() == Alert.Severity.HIGH));
    }

    @Test
    void productTypeWithoutPolicyStillVerifiesWithAnInformationalNote() {
        Product p = products.save(new Product(products.findById(batch.getProductId()).orElseThrow().getOrganizationId(), "NoPolicy", "type-" + UUID.randomUUID()));
        Batch b = batches.save(new Batch(p.getId(), "B-X"));
        String id = "TG-NP-" + UUID.randomUUID().toString().substring(0, 8);
        tags.save(new Tag(id, b.getId()));
        scan(id, factory, Type.REGISTERED, Duration.ofHours(5));

        Verdict v = verification.verify(id, false, true);

        assertEquals(Result.AUTHENTIC, v.verdict());
        assertTrue(codes(v).contains("NO_ROUTE_POLICY"));
    }
}
