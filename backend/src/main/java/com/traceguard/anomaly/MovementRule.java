package com.traceguard.anomaly;

import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import com.traceguard.verify.VerificationCheck;
import com.traceguard.verify.VerificationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Looks at each move between two different checkpoints:
 * DUPLICATE_PRESENCE when the same tag shows up far away within seconds (a cloned tag), and
 * IMPOSSIBLE_TRAVEL when the implied speed is faster than any real shipment.
 */
@Component
@Order(50)
public class MovementRule implements VerificationCheck {

    private final AnomalyProperties props;

    public MovementRule(AnomalyProperties props) {
        this.props = props;
    }

    @Override
    public List<Reason> check(VerificationContext ctx) {
        return check(ctx.events(), ctx.checkpoints());
    }

    public List<Reason> check(List<ScanEvent> events, Map<Long, Checkpoint> cps) {
        List<Reason> out = new ArrayList<>();
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
        return out;
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
