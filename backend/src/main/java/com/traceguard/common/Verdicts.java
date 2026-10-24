package com.traceguard.common;

import java.time.Instant;
import java.util.List;

/** Result types shared by the route checker, anomaly detector and verification engine. */
public final class Verdicts {

    private Verdicts() {}

    /** Ordered from best to worst: the overall verdict is the worst impact among the reasons. */
    public enum Result { AUTHENTIC, SUSPECT, COUNTERFEIT }

    /**
     * One machine-readable finding. {@code impact} is the verdict this finding pushes towards;
     * null means informational only (it never changes the verdict).
     */
    public record Reason(String code, Result impact, String message, Integer eventSeq) {}

    public record TimelineEntry(int seq, String eventType, String checkpointName, String locationName, Instant occurredAt) {}

    public record Verdict(String tagId, Result verdict, List<Reason> reasons, List<TimelineEntry> timeline, Instant verifiedAt) {}
}
