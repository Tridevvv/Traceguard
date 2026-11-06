package com.traceguard.catalog;

import com.traceguard.auth.CurrentUser;
import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    public record ProductRequest(@NotBlank String name, @NotBlank String productType, Long organizationId) {}
    public record ProductResponse(Long id, Long organizationId, String name, String productType) {}
    public record BatchRequest(@NotBlank String label) {}
    public record BatchResponse(Long id, Long productId, String label, boolean recalled) {}
    public record TagsResponse(Long batchId, List<String> tagIds) {}

    private final CatalogService service;
    private final CurrentUser currentUser;

    public CatalogController(CatalogService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest req) {
        return toResponse(service.createProduct(currentUser.get(), req.name(), req.productType(), req.organizationId()));
    }

    @GetMapping("/products")
    public List<ProductResponse> listProducts() {
        return service.products().stream().map(CatalogController::toResponse).toList();
    }

    @PostMapping("/products/{productId}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    public BatchResponse createBatch(@PathVariable Long productId, @Valid @RequestBody BatchRequest req) {
        return toResponse(service.createBatch(currentUser.get(), productId, req.label()));
    }

    @GetMapping("/products/{productId}/batches")
    public List<BatchResponse> listBatches(@PathVariable Long productId) {
        return service.batchesOf(productId).stream().map(CatalogController::toResponse).toList();
    }

    @PostMapping("/batches/{batchId}/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    public TagsResponse issueTags(@PathVariable Long batchId, @RequestParam(defaultValue = "1") int count) {
        return new TagsResponse(batchId, service.issueTags(currentUser.get(), batchId, count));
    }

    @GetMapping("/batches/{batchId}/tags")
    public TagsResponse listTags(@PathVariable Long batchId) {
        return new TagsResponse(batchId, service.tagsOf(batchId));
    }

    @PostMapping("/batches/{batchId}/recall")
    @PreAuthorize("hasAnyRole('ADMIN','MANUFACTURER')")
    public BatchResponse recall(@PathVariable Long batchId) {
        return toResponse(service.recall(currentUser.get(), batchId));
    }

    private static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getOrganizationId(), p.getName(), p.getProductType());
    }

    private static BatchResponse toResponse(Batch b) {
        return new BatchResponse(b.getId(), b.getProductId(), b.getLabel(), b.isRecalled());
    }
}
