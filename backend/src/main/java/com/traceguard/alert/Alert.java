package com.traceguard.alert;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    public enum Severity { LOW, MEDIUM, HIGH }

    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false, length = 64)
    private String tagId;

    @Column(nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.OPEN;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Alert() {}

    public Alert(String tagId, String code, Severity severity, String message) {
        this.tagId = tagId;
        this.code = code;
        this.severity = severity;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTagId() { return tagId; }
    public String getCode() { return code; }
    public Severity getSeverity() { return severity; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
