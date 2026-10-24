package com.traceguard.catalog;

import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public final class CatalogRepositories {

    private CatalogRepositories() {}

    public interface ProductRepository extends JpaRepository<Product, Long> {
        List<Product> findByOrganizationId(Long organizationId);

        /** The manufacturer organization that owns the product a tag belongs to. */
        @Query("select p.organizationId from Product p join Batch b on b.productId = p.id "
                + "join Tag t on t.batchId = b.id where t.tagId = :tagId")
        Optional<Long> organizationOfTag(@Param("tagId") String tagId);
    }

    public interface BatchRepository extends JpaRepository<Batch, Long> {
        List<Batch> findByProductId(Long productId);
    }

    public interface TagRepository extends JpaRepository<Tag, String> {
        List<Tag> findByBatchId(Long batchId);
    }
}
