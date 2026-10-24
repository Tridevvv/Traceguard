package com.traceguard;

import com.traceguard.anomaly.AnomalyDetector;
import com.traceguard.anomaly.AnomalyProperties;
import com.traceguard.anomaly.Geo;
import com.traceguard.common.Verdicts.Reason;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import com.traceguard.policy.RoutePolicy;
import com.traceguard.policy.RoutePolicy.Step;
import com.traceguard.verify.RouteChecker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests: no Spring, no database. */
class AnomalyAndRouteTest {

    static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z");
    // 1 = Boston, 2 = Albany (about 200 km away), 3 = Chicago (about 1,370 km from Boston)
    static final Map<Long, Checkpoint> CPS = Map.of(
            1L, new Checkpoint(1L, "Factory", "Boston", 42.3601, -71.0589),
            2L, new Checkpoint(1L, "Warehouse", "Albany", 42.6526, -73.7562),
            3L, new Checkpoint(1L, "Pharmacy", "Chicago", 41.8781, -87.6298));

    final AnomalyDetector detector = new AnomalyDetector(new AnomalyProperties(900, 60, 1, 60, 5));
    final RouteChecker router = new RouteChecker();

    /** pairs of (checkpointId, seconds after T0) */
    static List<ScanEvent> events(long[]... stops) {
        List<ScanEvent> list = new ArrayList<>();
        int seq = 1;
        for (long[] s : stops) {
            list.add(new ScanEvent("TG-1", s[0], ScanEvent.Type.RECEIVED, T0.plusSeconds(s[1]), seq, "n" + seq, "p", "h", "s"));
            seq++;
        }
        return list;
    }

    static long hours(long h) { return h * 3600; }

    static List<String> codes(List<Reason> reasons) { return reasons.stream().map(Reason::code).toList(); }

    static RoutePolicy policy() {
        return new RoutePolicy("pharma", 168, List.of(new Step(1L, null), new Step(2L, 48), new Step(3L, 72)));
    }

    // ---- geo ----
    @Test
    void distanceBostonToChicagoIsAboutThirteenHundredKm() {
        double km = Geo.distanceKm(42.3601, -71.0589, 41.8781, -87.6298);
        assertTrue(km > 1300 && km < 1450, "was " + km);
    }

    // ---- anomaly detector ----
    @Test
    void normalJourneyHasNoAnomalies() {
        var e = events(new long[]{1, 0}, new long[]{2, hours(6)}, new long[]{3, hours(30)});
        assertEquals(List.of(), detector.detect(e, CPS));
    }

    @Test
    void sameTagInTwoCitiesSecondsApartIsDuplicatePresence() {
        var found = detector.detect(events(new long[]{1, 0}, new long[]{3, 40}), CPS);
        assertEquals(List.of("DUPLICATE_PRESENCE"), codes(found));
    }

    @Test
    void impossibleSpeedIsFlaggedOnceNotAsDuplicate() {
        // Boston to Albany (~200 km) in 2 minutes: outside the 60 s window, but ~6000 km/h
        var found = detector.detect(events(new long[]{1, 0}, new long[]{2, 120}), CPS);
        assertEquals(List.of("IMPOSSIBLE_TRAVEL"), codes(found));
    }

    @Test
    void twoReadersAtOneSiteAreNotDuplicates() {
        Map<Long, Checkpoint> sameSite = Map.of(
                1L, new Checkpoint(1L, "Dock A", "Boston", 42.3601, -71.0589),
                2L, new Checkpoint(1L, "Dock B", "Boston", 42.3602, -71.0590));
        assertEquals(List.of(), detector.detect(events(new long[]{1, 0}, new long[]{2, 10}), sameSite));
    }

    @Test
    void sixScansInAMinuteIsABurst() {
        var e = events(new long[]{1, 0}, new long[]{1, 5}, new long[]{1, 10}, new long[]{1, 15}, new long[]{1, 20}, new long[]{1, 25});
        assertEquals(List.of("SCAN_BURST"), codes(detector.detect(e, CPS)));
    }

    @Test
    void fiveScansInAMinuteIsFine() {
        var e = events(new long[]{1, 0}, new long[]{1, 5}, new long[]{1, 10}, new long[]{1, 15}, new long[]{1, 20});
        assertEquals(List.of(), detector.detect(e, CPS));
    }

    // ---- route checker ----
    @Test
    void approvedRouteWithinLimitsPasses() {
        var e = events(new long[]{1, 0}, new long[]{2, hours(6)}, new long[]{3, hours(30)});
        assertEquals(List.of(), router.check(e, CPS, policy()));
    }

    @Test
    void partialJourneyInTheRightOrderPasses() {
        assertEquals(List.of(), router.check(events(new long[]{1, 0}, new long[]{2, hours(6)}), CPS, policy()));
    }

    @Test
    void skippedStepIsRouteDeviation() {
        var e = events(new long[]{1, 0}, new long[]{3, hours(20)});
        assertEquals(List.of("ROUTE_DEVIATION"), codes(router.check(e, CPS, policy())));
    }

    @Test
    void goingBackwardIsRouteDeviation() {
        var e = events(new long[]{1, 0}, new long[]{2, hours(6)}, new long[]{1, hours(8)});
        assertEquals(List.of("ROUTE_DEVIATION"), codes(router.check(e, CPS, policy())));
    }

    @Test
    void checkpointOutsidePolicyIsUnauthorizedLocation() {
        var policyWithoutAlbany = new RoutePolicy("pharma", 168, List.of(new Step(1L, null), new Step(3L, 72)));
        var e = events(new long[]{1, 0}, new long[]{2, hours(6)});
        assertEquals(List.of("UNAUTHORIZED_LOCATION"), codes(router.check(e, CPS, policyWithoutAlbany)));
    }

    @Test
    void slowHopIsTimingViolation() {
        var e = events(new long[]{1, 0}, new long[]{2, hours(60)}); // limit is 48 h
        assertEquals(List.of("TIMING_VIOLATION"), codes(router.check(e, CPS, policy())));
    }

    @Test
    void journeyLongerThanTotalLimitIsTimingViolation() {
        var e = events(new long[]{1, 0}, new long[]{2, hours(40)}, new long[]{3, hours(100)}, new long[]{3, hours(200)});
        assertTrue(codes(router.check(e, CPS, policy())).contains("TIMING_VIOLATION"));
    }

    @Test
    void timestampsGoingBackwardsAreTimingViolation() {
        var e = events(new long[]{1, hours(5)}, new long[]{2, hours(1)});
        assertTrue(codes(router.check(e, CPS, policy())).contains("TIMING_VIOLATION"));
    }

    @Test
    void missingPolicyIsInformationalOnly() {
        var found = router.check(events(new long[]{1, 0}), CPS, null);
        assertEquals(List.of("NO_ROUTE_POLICY"), codes(found));
        assertNull(found.get(0).impact(), "must not change the verdict");
    }

    @Test
    void hoursAreMeasuredBetweenEvents() {
        assertEquals(6, Duration.between(T0, T0.plusSeconds(hours(6))).toHours());
    }
}
