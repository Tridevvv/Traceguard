package com.traceguard.reader;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Software reader used for demos and tests: readings are queued by the scan console or test code. */
@Component
public class SimulatedReader implements Reader {

    private final Queue<ScanReading> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(String tagId, long checkpointId) {
        queue.add(new ScanReading(tagId, checkpointId, Instant.now(), UUID.randomUUID().toString()));
    }

    @Override
    public ScanReading read() {
        return queue.poll();
    }

    /** Simulates one tag passing a checkpoint and returns that reading. */
    public ScanReading scan(String tagId, long checkpointId) {
        enqueue(tagId, checkpointId);
        return read();
    }
}
