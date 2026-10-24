package com.traceguard.anomaly;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunable thresholds (traceguard.anomaly.*). */
@ConfigurationProperties("traceguard.anomaly")
public record AnomalyProperties(
        /* Faster than this between two checkpoints is physically impossible (default: airliner speed). */
        @DefaultValue("900") double maxSpeedKmh,
        /* Same tag at two different sites within this many seconds counts as being in two places at once. */
        @DefaultValue("60") long duplicateWindowSeconds,
        /* Checkpoints closer than this are treated as the same site (two readers on one dock). */
        @DefaultValue("1") double duplicateMinKm,
        @DefaultValue("60") long burstWindowSeconds,
        /* More scans than this inside the window is a scan burst. */
        @DefaultValue("5") int burstMaxScans) {
}
