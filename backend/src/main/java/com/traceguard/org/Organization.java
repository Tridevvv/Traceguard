package com.traceguard.org;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "organizations")
public class Organization {

    public enum Type { MANUFACTURER, DISTRIBUTOR, RETAILER, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Organization() {}

    public Organization(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Type getType() { return type; }
}
