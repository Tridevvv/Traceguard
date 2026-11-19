package com.traceguard.anomaly;

import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.verify.VerificationCheck;
import com.traceguard.verify.VerificationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** SCAN_BURST: more scans of one tag inside a short window than normal handling produces. */
@Component
@Order(60)
public class ScanBurstRule implements VerificationCheck {

    private final AnomalyProperties props;

    public ScanBurstRule(AnomalyProperties props) {
        this.props = props;
    }

    @Override
    public List<Reason> check(VerificationContext ctx) {
        return check(ctx.events());
    }

    /** Sliding window over the events (oldest first); reports the first burst only, one finding per tag is enough. */
    public List<Reason> check(List<ScanEvent> events) {
        int start = 0;
        for (int end = 0; end < events.size(); end++) {
            while (Duration.between(events.get(start).getOccurredAt(), events.get(end).getOccurredAt()).getSeconds()
                    > props.burstWindowSeconds()) {
                start++;
            }
            int count = end - start + 1;
            if (count > props.burstMaxScans()) {
                return List.of(new Reason("SCAN_BURST", Result.SUSPECT,
                        count + " scans within " + props.burstWindowSeconds() + " s (limit " + props.burstMaxScans() + ")",
                        events.get(end).getSeq()));
            }
        }
        return List.of();
    }
}
