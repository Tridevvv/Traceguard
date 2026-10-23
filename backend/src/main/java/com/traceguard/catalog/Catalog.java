package com.traceguard.catalog;

import jakarta.persistence.*;

import java.time.Instant;

/** Product, batch and tag entities. */
public final class Catalog {

    private Catalog() {}

    @Entity
    @Table(name = "products")
    public static class Product {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "organization_id", nullable = false)
        private Long organizationId;

        @Column(nullable = false, length = 200)
        private String name;

        @Column(name = "product_type", nullable = false, length = 100)
        private String productType;

        protected Product() {}

        public Product(Long organizationId, String name, String productType) {
            this.organizationId = organizationId;
            this.name = name;
            this.productType = productType;
        }

        public Long getId() { return id; }
        public Long getOrganizationId() { return organizationId; }
        public String getName() { return name; }
        public String getProductType() { return productType; }
    }

    @Entity
    @Table(name = "batches")
    public static class Batch {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "product_id", nullable = false)
        private Long productId;

        @Column(nullable = false, length = 100)
        private String label;

        @Column(nullable = false)
        private boolean recalled;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();

        protected Batch() {}

        public Batch(Long productId, String label) {
            this.productId = productId;
            this.label = label;
        }

        public Long getId() { return id; }
        public Long getProductId() { return productId; }
        public String getLabel() { return label; }
        public boolean isRecalled() { return recalled; }
        public void recall() { this.recalled = true; }
    }

    @Entity
    @Table(name = "tags")
    public static class Tag {
        @Id
        @Column(name = "tag_id", length = 64)
        private String tagId;

        @Column(name = "batch_id", nullable = false)
        private Long batchId;

        @Column(name = "issued_at", nullable = false)
        private Instant issuedAt = Instant.now();

        protected Tag() {}

        public Tag(String tagId, Long batchId) {
            this.tagId = tagId;
            this.batchId = batchId;
        }

        public String getTagId() { return tagId; }
        public Long getBatchId() { return batchId; }
    }
}
