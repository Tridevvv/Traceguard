package com.traceguard.catalog;

import com.traceguard.audit.AuditService;
import com.traceguard.auth.AppUser;
import com.traceguard.auth.Role;
import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import com.traceguard.catalog.CatalogRepositories.BatchRepository;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.common.Errors.BadRequestException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.org.OrgRepositories.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Products, batches and tags. A manufacturer works only on its own organization's products;
 * an admin may act for any organization.
 */
@Service
public class CatalogService {

    public static final int MAX_TAGS_PER_CALL = 1000;

    private final ProductRepository products;
    private final BatchRepository batches;
    private final TagRepository tags;
    private final OrganizationRepository orgs;
    private final AuditService audit;

    public CatalogService(ProductRepository products, BatchRepository batches, TagRepository tags,
                          OrganizationRepository orgs, AuditService audit) {
        this.products = products;
        this.batches = batches;
        this.tags = tags;
        this.orgs = orgs;
        this.audit = audit;
    }

    /** A manufacturer always creates products for its own organization; an admin must name one. */
    public Product createProduct(AppUser me, String name, String productType, Long requestedOrgId) {
        Long orgId = me.getRole() == Role.MANUFACTURER ? me.getOrganizationId() : requestedOrgId;
        if (orgId == null) {
            throw new BadRequestException("organizationId is required");
        }
        if (!orgs.existsById(orgId)) {
            throw new NotFoundException("Organization " + orgId + " not found");
        }
        Product p = products.save(new Product(orgId, name, productType));
        audit.record("PRODUCT_CREATED", p.getName());
        return p;
    }

    public List<Product> products() {
        return products.findAll();
    }

    public Batch createBatch(AppUser me, Long productId, String label) {
        Product p = requireOwnedProduct(me, productId);
        Batch b = batches.save(new Batch(p.getId(), label));
        audit.record("BATCH_CREATED", p.getName() + "/" + b.getLabel());
        return b;
    }

    public List<Batch> batchesOf(Long productId) {
        return batches.findByProductId(productId);
    }

    /** Issues unique tag IDs for a batch (each ID is also what the QR label encodes). */
    @Transactional
    public List<String> issueTags(AppUser me, Long batchId, int count) {
        if (count < 1 || count > MAX_TAGS_PER_CALL) {
            throw new BadRequestException("count must be between 1 and " + MAX_TAGS_PER_CALL);
        }
        Batch b = requireBatch(batchId);
        requireOwnedProduct(me, b.getProductId());
        if (b.isRecalled()) {
            throw new BadRequestException("Batch is recalled; no new tags can be issued");
        }
        List<String> ids = new ArrayList<>(count);
        while (ids.size() < count) {
            String id = "TG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            if (!tags.existsById(id)) {
                tags.save(new Tag(id, batchId));
                ids.add(id);
            }
        }
        audit.record("TAGS_ISSUED", "batch " + batchId + " x" + count);
        return ids;
    }

    public List<String> tagsOf(Long batchId) {
        return tags.findByBatchId(batchId).stream().map(Tag::getTagId).toList();
    }

    /** Flags every product in the batch; verification then reports BATCH_RECALLED for its tags. */
    @Transactional
    public Batch recall(AppUser me, Long batchId) {
        Batch b = requireBatch(batchId);
        requireOwnedProduct(me, b.getProductId());
        b.recall();
        audit.record("BATCH_RECALLED", "batch " + batchId);
        return b;
    }

    private Batch requireBatch(Long batchId) {
        return batches.findById(batchId).orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
    }

    private Product requireOwnedProduct(AppUser me, Long productId) {
        Product p = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
        if (me.getRole() == Role.MANUFACTURER && !p.getOrganizationId().equals(me.getOrganizationId())) {
            // Same message as "not found" so other organizations' IDs are not revealed.
            throw new NotFoundException("Product " + productId + " not found");
        }
        return p;
    }
}
