package com.traceguard.verify;

import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import com.traceguard.policy.RoutePolicy;
import com.traceguard.policy.RoutePolicy.Step;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compares a tag's journey with the approved route policy: order, allowed locations and time limits. */
@Component
public class RouteChecker {

    public List<Reason> check(List<ScanEvent> events, Map<Long, Checkpoint> checkpoints, RoutePolicy policy) {
        List<Reason> reasons = new ArrayList<>();
        if (policy == null) {
            reasons.add(new Reason("NO_ROUTE_POLICY", null,
                    "No route policy is defined for this product type, so route and timing were not checked", null));
            return reasons;
        }
        if (events.isEmpty()) return reasons;

        List<Step> steps = policy.getSteps();
        int lastIndex = -1;

        for (int i = 0; i < events.size(); i++) {
            ScanEvent cur = events.get(i);
            ScanEvent prev = i == 0 ? null : events.get(i - 1);
            boolean newStop = prev == null || !prev.getCheckpointId().equals(cur.getCheckpointId());

            if (prev != null && cur.getOccurredAt().isBefore(prev.getOccurredAt())) {
                reasons.add(new Reason("TIMING_VIOLATION", Result.SUSPECT,
                        "Event " + cur.getSeq() + " is time-stamped earlier than event " + prev.getSeq(), cur.getSeq()));
            }
            if (!newStop) continue;

            int index = indexOf(steps, cur.getCheckpointId());
            String where = nameOf(checkpoints, cur.getCheckpointId());
            if (index < 0) {
                reasons.add(new Reason("UNAUTHORIZED_LOCATION", Result.SUSPECT,
                        "Scan at " + where + ", which is not on the approved route", cur.getSeq()));
                continue;
            }
            if (index <= lastIndex) {
                reasons.add(new Reason("ROUTE_DEVIATION", Result.SUSPECT,
                        "Product returned to " + where + " after already reaching a later stop", cur.getSeq()));
            } else if (index > lastIndex + 1) {
                reasons.add(new Reason("ROUTE_DEVIATION", Result.SUSPECT,
                        "Approved stop(s) before " + where + " were skipped", cur.getSeq()));
            }
            if (prev != null) {
                Integer limit = steps.get(index).getMaxHoursFromPrevious();
                long hours = Duration.between(prev.getOccurredAt(), cur.getOccurredAt()).toHours();
                if (limit != null && hours > limit) {
                    reasons.add(new Reason("TIMING_VIOLATION", Result.SUSPECT,
                            "Took " + hours + " h to reach " + where + " (limit " + limit + " h)", cur.getSeq()));
                }
            }
            lastIndex = Math.max(lastIndex, index);
        }

        long total = Duration.between(events.get(0).getOccurredAt(), events.get(events.size() - 1).getOccurredAt()).toHours();
        if (total > policy.getMaxTotalHours()) {
            reasons.add(new Reason("TIMING_VIOLATION", Result.SUSPECT,
                    "Journey took " + total + " h in total (limit " + policy.getMaxTotalHours() + " h)", null));
        }
        return reasons;
    }

    private static int indexOf(List<Step> steps, Long checkpointId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getCheckpointId().equals(checkpointId)) return i;
        }
        return -1;
    }

    private static String nameOf(Map<Long, Checkpoint> checkpoints, Long id) {
        Checkpoint c = checkpoints.get(id);
        return c != null ? c.getName() : "checkpoint " + id;
    }
}
