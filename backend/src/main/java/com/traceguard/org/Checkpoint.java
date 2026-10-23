package com.traceguard.org;

import jakarta.persistence.*;

@Entity
@Table(name = "checkpoints")
public class Checkpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "location_name", nullable = false, length = 200)
    private String locationName;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    /** Base64 X.509 ECDSA public key used to verify this checkpoint's event signatures. */
    @Column(name = "public_key", length = 512)
    private String publicKey;

    @Column(nullable = false)
    private boolean active = true;

    protected Checkpoint() {}

    public Checkpoint(Long organizationId, String name, String locationName, double latitude, double longitude) {
        this.organizationId = organizationId;
        this.name = name;
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getLocationName() { return locationName; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public boolean isActive() { return active; }
}
