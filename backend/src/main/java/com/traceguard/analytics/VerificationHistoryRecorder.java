package com.traceguard.analytics;

import com.traceguard.common.Verdicts.Result;
import com.traceguard.verify.VerdictIssued;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Keeps the verification history that the verdict charts are built from (Observer of {@link VerdictIssued}). */
@Component
public class VerificationHistoryRecorder {

    private final VerificationRecordRepository records;

    public VerificationHistoryRecorder(VerificationRecordRepository records) {
        this.records = records;
    }

    /** Stores the outcome, skipping a repeat of the same verdict for the same tag within the last minute. */
    @EventListener
    public void on(VerdictIssued event) {
        if (!event.recordHistory()) return;
        String tagId = event.verdict().tagId();
        Result result = event.verdict().verdict();
        Instant now = Instant.now();
        boolean repeat = records.findFirstByTagIdOrderByIdDesc(tagId)
                .filter(r -> r.getVerdict() == result && r.getCreatedAt().isAfter(now.minusSeconds(60)))
                .isPresent();
        if (!repeat) records.save(new VerificationRecord(tagId, result, now));
    }
}
