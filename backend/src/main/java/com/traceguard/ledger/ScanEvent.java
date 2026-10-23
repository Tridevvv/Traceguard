package com.traceguard.ledger;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One signed, hash-linked ledger entry. Immutable: Hibernate never issues an UPDATE for it. */
@Entity
@Immutable
@Table(name = "events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_events_tag_seq", columnNames = {"tag_id", "seq"}),
        @UniqueConstraint(name = "uq_events_tag_nonce", columnNames = {"tag_id", "nonce"})
})
public class ScanEvent {

    public enum Type { REGISTERED, RECEIVED, IN_TRANSIT, DELIVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false, length = 64)
    private String tagId;

    @Column(name = "checkpoint_id", nullable = false)
    private Long checkpointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private Type eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 64)
    private String nonce;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64)
    private String hash;

    /** Base64 ECDSA signature over {@link #hash}. */
    @Column(nullable = false, length = 256)
    private String signature;

    protected ScanEvent() {}

    public ScanEvent(String tagId, Long checkpointId, Type eventType, Instant occurredAt, int seq,
                     String nonce, String prevHash, String hash, String signature) {
        this.tagId = tagId;
        this.checkpointId = checkpointId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.seq = seq;
        this.nonce = nonce;
        this.prevHash = prevHash;
        this.hash = hash;
        this.signature = signature;
    }

    public Long getId() { return id; }
    public String getTagId() { return tagId; }
    public Long getCheckpointId() { return checkpointId; }
    public Type getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getSeq() { return seq; }
    public String getNonce() { return nonce; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
    public String getSignature() { return signature; }
}
