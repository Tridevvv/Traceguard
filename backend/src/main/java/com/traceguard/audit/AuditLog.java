package com.traceguard.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", length = 254)
    private String userEmail;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 200)
    private String target;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {}

    public AuditLog(String userEmail, String action, String target) {
        this.userEmail = userEmail;
        this.action = action;
        this.target = target;
    }

    public Long getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public Instant getCreatedAt() { return createdAt; }
}
