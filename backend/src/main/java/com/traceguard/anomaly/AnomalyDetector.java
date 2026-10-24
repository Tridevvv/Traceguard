package com.traceguard.anomaly;

import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rule-based detectors over one tag's events (oldest first):
 * DUPLICATE_PRESENCE (cloned tag), IMPOSSIBLE_TRAVEL and SCAN_BURST.
 * UNREGISTERED_TAG, UNAUTHORIZED_LOCATION and route/timing rules live in the verification engine and route checker.
 */
@Component
public class AnomalyDetector {

    private final AnomalyProperties props;

    public AnomalyDetector(AnomalyProperties props) {
        this.props = props;
    }

    public List<Reason> detect(List<ScanEvent> events, Map<Long, Checkpoint> checkpoints) {
        List<Reason> reasons = new ArrayList<>();
        detectMovement(events, checkpoints, reasons);
        detectBurst(events, reasons);
        return reasons;
    }

    private void detectMovement(List<ScanEvent> events, Map<Long, Checkpoint> cps, List<Reason> out) {
        for (int i = 1; i < events.size(); i++) {
            ScanEvent prev = events.get(i - 1);
            ScanEvent cur = events.get(i);
            if (prev.getCheckpointId().equals(cur.getCheckpointId())) continue;

            long gapSeconds = Duration.between(prev.getOccurredAt(), cur.getOccurredAt()).getSeconds();
            if (gapSeconds < 0) continue; // out-of-order timestamps are reported by the route checker

            Checkpoint a = cps.get(prev.getCheckpointId());
            Checkpoint b = cps.get(cur.getCheckpointId());
            boolean known = a != null && b != null;
            double km = known ? Geo.distanceKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude()) : Double.NaN;

            boolean farApart = !known || km >= props.duplicateMinKm();
            if (gapSeconds <= props.duplicateWindowSeconds() && farApart) {
                out.add(new Reason("DUPLICATE_PRESENCE", Result.COUNTERFEIT,
                        "Same tag seen at " + name(a, prev) + " and " + name(b, cur) + " only " + gapSeconds
                                + " s apart: it is probably cloned", cur.getSeq()));
            } else if (known && km > 0) {
                double hours = Math.max(gapSeconds, 1) / 3600.0;
                double speed = km / hours;
                if (speed > props.maxSpeedKmh()) {
                    out.add(new Reason("IMPOSSIBLE_TRAVEL", Result.COUNTERFEIT,
                            String.format("%.0f km between %s and %s in %s would need %.0f km/h", km,
                                    name(a, prev), name(b, cur), human(gapSeconds), speed), cur.getSeq()));
                }
            }
        }
    }

    private void detectBurst(List<ScanEvent> events, List<Reason> out) {
        int start = 0;
        for (int end = 0; end < events.size(); end++) {
            while (Duration.between(events.get(start).getOccurredAt(), events.get(end).getOccurredAt()).getSeconds()
                    > props.burstWindowSeconds()) {
                start++;
            }
            int count = end - start + 1;
            if (count > props.burstMaxScans()) {
                out.add(new Reason("SCAN_BURST", Result.SUSPECT,
                        count + " scans within " + props.burstWindowSeconds() + " s (limit " + props.burstMaxScans() + ")",
                        events.get(end).getSeq()));
                return; // one finding per tag is enough
            }
        }
    }

    private static String name(Checkpoint c, ScanEvent e) {
        return c != null ? c.getName() : "checkpoint " + e.getCheckpointId();
    }

    private static String human(long seconds) {
        if (seconds < 120) return seconds + " s";
        if (seconds < 7200) return (seconds / 60) + " min";
        return String.format("%.1f h", seconds / 3600.0);
    }
}
