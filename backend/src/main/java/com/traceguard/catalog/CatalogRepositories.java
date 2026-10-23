package com.traceguard.catalog;

import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public final class CatalogRepositories {

    private CatalogRepositories() {}

    public interface ProductRepository extends JpaRepository<Product, Long> {
        List<Product> findByOrganizationId(Long organizationId);
    }

    public interface BatchRepository extends JpaRepository<Batch, Long> {
        List<Batch> findByProductId(Long productId);
    }

    public interface TagRepository extends JpaRepository<Tag, String> {
        List<Tag> findByBatchId(Long batchId);
    }
}
