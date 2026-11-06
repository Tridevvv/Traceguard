package com.traceguard.analytics;

import com.traceguard.common.Verdicts.Result;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One stored verification outcome for a registered tag; the source for the verdict analytics. */
@Entity
@Immutable
@Table(name = "verification_results", indexes = {
        @Index(name = "idx_verification_tag", columnList = "tag_id"),
        @Index(name = "idx_verification_time", columnList = "created_at")
})
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false, length = 64)
    private String tagId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Result verdict;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VerificationRecord() {}

    public VerificationRecord(String tagId, Result verdict, Instant createdAt) {
        this.tagId = tagId;
        this.verdict = verdict;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getTagId() { return tagId; }
    public Result getVerdict() { return verdict; }
    public Instant getCreatedAt() { return createdAt; }
}
