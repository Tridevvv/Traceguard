package com.traceguard;

import com.traceguard.alert.Alert;
import com.traceguard.analytics.AnalyticsService;
import com.traceguard.analytics.AnalyticsService.*;
import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import com.traceguard.catalog.CatalogRepositories.BatchRepository;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.crypto.KeyService;
import com.traceguard.ledger.LedgerService;
import com.traceguard.ledger.ScanEvent.Type;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.org.OrgRepositories.OrganizationRepository;
import com.traceguard.org.Organization;
import com.traceguard.verify.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Other tests share the in-memory database, so every assertion uses a Scope limited to this test's own tags.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyticsServiceTest {

    @Autowired AnalyticsService analytics;
    @Autowired VerificationService verification;
    @Autowired LedgerService ledger;
    @Autowired OrganizationRepository orgs;
    @Autowired CheckpointRepository checkpoints;
    @Autowired ProductRepository products;
    @Autowired BatchRepository batches;
    @Autowired TagRepository tags;
    @Autowired KeyService keys;

    final Instant now = Instant.now();
    Organization org;
    long boston, albany, chicago;
    Batch batch;
    String genuine, cloned, untouched;
    Scope mine;

    @BeforeEach
    void setUp() {
        org = orgs.save(new Organization("Org-" + UUID.randomUUID(), Organization.Type.MANUFACTURER));
        boston = checkpoint("Factory", "Boston", 42.3601, -71.0589);
        albany = checkpoint("Warehouse", "Albany", 42.6526, -73.7562);
        chicago = checkpoint("Pharmacy", "Chicago", 41.8781, -87.6298);
        Product p = products.save(new Product(org.getId(), "Aspirin", "type-" + UUID.randomUUID()));
        batch = batches.save(new Batch(p.getId(), "B-1"));

        genuine = tag();
        ledger.append(genuine, boston, Type.REGISTERED, now.minus(Duration.ofDays(2)), null);
        ledger.append(genuine, albany, Type.RECEIVED, now.minus(Duration.ofDays(2)).plus(Duration.ofHours(1)), null);

        cloned = tag();
        ledger.append(cloned, boston, Type.REGISTERED, now.minus(Duration.ofMinutes(10)), null);
        ledger.append(cloned, chicago, Type.RECEIVED, now.minus(Duration.ofMinutes(10)).plusSeconds(30), null);

        untouched = tag();
        mine = new Scope(Set.of(genuine, cloned, untouched));
    }

    private long checkpoint(String name, String location, double lat, double lon) {
        Checkpoint c = checkpoints.save(new Checkpoint(org.getId(), name, location, lat, lon));
        c.setPublicKey(keys.generateFor(c.getId()));
        return c.getId();
    }

    private String tag() {
        String id = "TG-" + UUID.randomUUID().toString().substring(0, 12);
        tags.save(new Tag(id, batch.getId()));
        return id;
    }

    private static LocalDate today() {
        return Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
    }

    @Test
    void scanVolumeIsZeroFilledOldestFirstAndEndsToday() {
        List<DailyCount> v = analytics.scanVolume(mine, 7);

        assertEquals(7, v.size());
        assertEquals(today().minusDays(6), v.get(0).date());
        assertEquals(today(), v.get(6).date());
        assertEquals(4, v.stream().mapToLong(DailyCount::count).sum());
        assertEquals(2, v.stream().filter(d -> d.date().equals(today().minusDays(2))).findFirst().orElseThrow().count());
        assertEquals(0, v.get(0).count());
    }

    @Test
    void scanVolumeWindowIsClamped() {
        assertEquals(1, analytics.scanVolume(mine, 0).size());
        assertEquals(AnalyticsService.MAX_DAYS, analytics.scanVolume(mine, 100_000).size());
    }

    @Test
    void verdictCountsUseTheLatestOutcomePerTag() {
        verification.verify(genuine, false, true);
        verification.verify(cloned, false, true);
        verification.verify(cloned, false, true);   // verifying again must not double count

        VerdictsReport r = analytics.verdicts(mine, 7);

        assertEquals(1, r.current().authentic());
        assertEquals(1, r.current().counterfeit());
        assertEquals(0, r.current().suspect());
        assertEquals(1, r.current().unverified());  // 'untouched' was never verified
        DailyVerdicts todayRow = r.daily().get(r.daily().size() - 1);
        assertEquals(today(), todayRow.date());
        assertEquals(1, todayRow.authentic());
        assertEquals(1, todayRow.counterfeit(), "repeat of the same verdict within a minute is stored once");
    }

    @Test
    void alertsAreGroupedByCodeAndSeverity() {
        verification.verify(cloned, true, true);

        AlertsReport r = analytics.alerts(mine, 7);

        assertEquals(1, r.open());
        assertTrue(r.byCode().stream().anyMatch(n -> n.name().equals("DUPLICATE_PRESENCE") && n.count() == 1));
        assertTrue(r.bySeverity().stream().anyMatch(n -> n.name().equals(Alert.Severity.HIGH.name()) && n.count() >= 1));
        assertEquals(1, r.daily().stream().mapToLong(DailyCount::count).sum());
    }

    @Test
    void checkpointActivityCountsScansAndIsRankedBusiestFirst() {
        List<CheckpointActivity> a = analytics.checkpointActivity(mine, 7);

        assertEquals(3, a.size());
        assertEquals(boston, a.get(0).checkpointId());   // genuine + cloned each start in Boston
        assertEquals(2, a.get(0).scans());
        assertEquals("Factory", a.get(0).name());
        assertEquals("Boston", a.get(0).locationName());
        assertTrue(a.get(0).scans() >= a.get(1).scans());
    }

    @Test
    void summaryCountsOnlyTheScope() {
        Summary s = analytics.summary(mine);

        assertEquals(3, s.tagsIssued());
        assertEquals(2, s.scansLast24Hours());   // the cloned tag's two scans, not the 2-day-old ones
        assertEquals(2, s.scansLast7Days() - 2);
    }

    @Test
    void manufacturerScopeContainsOnlyItsOwnTags() {
        Scope own = analytics.scopeOfOrganization(org.getId());
        assertTrue(own.includes(genuine) && own.includes(cloned) && own.includes(untouched));
        assertFalse(own.includes("TG-SOMEONE-ELSE"));
        assertFalse(Scope.all().tagIds() != null);
        assertTrue(Scope.all().includes("TG-ANYTHING"));
    }

    @Test
    void organizationWithoutAnIdSeesNothing() {
        Scope none = analytics.scopeOfOrganization(null);
        assertFalse(none.includes(genuine));
        assertEquals(0, analytics.summary(none).tagsIssued());
        assertEquals(0, analytics.scanVolume(none, 7).stream().mapToLong(DailyCount::count).sum());
    }
}
