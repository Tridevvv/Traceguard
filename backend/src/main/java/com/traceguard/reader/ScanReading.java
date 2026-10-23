package com.traceguard.reader;

import java.time.Instant;

/** One raw tag read at a checkpoint. The nonce makes every reading unique so replays can be rejected. */
public record ScanReading(String tagId, long checkpointId, Instant readAt, String nonce) {}
