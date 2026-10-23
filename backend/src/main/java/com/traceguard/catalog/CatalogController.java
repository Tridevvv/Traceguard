package com.traceguard.catalog;

import com.traceguard.audit.AuditService;
import com.traceguard.auth.AppUser;
import com.traceguard.auth.AppUserRepository;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private static final int MAX_TAGS_PER_CALL = 1000;

    public record ProductRequest(@NotBlank String name, @NotBlank String productType, Long organizationId) {}
    public record ProductResponse(Long id, Long organizationId, String name, String productType) {}
    public record BatchRequest(@NotBlank String label) {}
    public record BatchResponse(Long id, Long productId, String label, boolean recalled) {}
    public record TagsResponse(Long batchId, List<String> tagIds) {}

    private final ProductRepository products;
    private final BatchRepository batches;
    private final TagRepository tags;
    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final AuditService audit;

    public CatalogController(ProductRepository products, BatchRepository batches, TagRepository tags,
                             OrganizationRepository orgs, AppUserRepository users, AuditService audit) {
        this.products = products;
        this.batches = batches;
        this.tags = tags;
        this.orgs = orgs;
        this.users = users;
        this.audit = audit;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest req) {
        AppUser me = currentUser();
        // A manufacturer always creates products for their own organization; an admin must name one.
        Long orgId = me.getRole() == Role.MANUFACTURER ? me.getOrganizationId() : req.organizationId();
        if (orgId == null) {
            throw new BadRequestException("organizationId is required");
        }
        if (!orgs.existsById(orgId)) {
            throw new NotFoundException("Organization " + orgId + " not found");
        }
        Product p = products.save(new Product(orgId, req.name(), req.productType()));
        audit.record("PRODUCT_CREATED", p.getName());
        return toResponse(p);
    }

    @GetMapping("/products")
    public List<ProductResponse> listProducts() {
        return products.findAll().stream().map(CatalogController::toResponse).toList();
    }

    @PostMapping("/products/{productId}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    public BatchResponse createBatch(@PathVariable Long productId, @Valid @RequestBody BatchRequest req) {
        Product p = requireOwnedProduct(productId);
        Batch b = batches.save(new Batch(p.getId(), req.label()));
        audit.record("BATCH_CREATED", p.getName() + "/" + b.getLabel());
        return toResponse(b);
    }

    @GetMapping("/products/{productId}/batches")
    public List<BatchResponse> listBatches(@PathVariable Long productId) {
        return batches.findByProductId(productId).stream().map(CatalogController::toResponse).toList();
    }

    /** Issues unique tag IDs for a batch (each ID is also what the QR label encodes). */
    @PostMapping("/batches/{batchId}/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    @Transactional
    public TagsResponse issueTags(@PathVariable Long batchId, @RequestParam(defaultValue = "1") int count) {
        if (count < 1 || count > MAX_TAGS_PER_CALL) {
            throw new BadRequestException("count must be between 1 and " + MAX_TAGS_PER_CALL);
        }
        Batch b = batches.findById(batchId).orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
        requireOwnedProduct(b.getProductId());
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
        return new TagsResponse(batchId, ids);
    }

    @GetMapping("/batches/{batchId}/tags")
    public TagsResponse listTags(@PathVariable Long batchId) {
        return new TagsResponse(batchId, tags.findByBatchId(batchId).stream().map(Tag::getTagId).toList());
    }

    @PostMapping("/batches/{batchId}/recall")
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    @Transactional
    public BatchResponse recall(@PathVariable Long batchId) {
        Batch b = batches.findById(batchId).orElseThrow(() -> new NotFoundException("Batch " + batchId + " not found"));
        requireOwnedProduct(b.getProductId());
        b.recall();
        audit.record("BATCH_RECALLED", "batch " + batchId);
        return toResponse(b);
    }

    private Product requireOwnedProduct(Long productId) {
        Product p = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
        AppUser me = currentUser();
        if (me.getRole() == Role.MANUFACTURER && !p.getOrganizationId().equals(me.getOrganizationId())) {
            // Same message as "not found" so other organizations' IDs are not revealed.
            throw new NotFoundException("Product " + productId + " not found");
        }
        return p;
    }

    private AppUser currentUser() {
        String email = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getOrganizationId(), p.getName(), p.getProductType());
    }

    private static BatchResponse toResponse(Batch b) {
        return new BatchResponse(b.getId(), b.getProductId(), b.getLabel(), b.isRecalled());
    }
}
