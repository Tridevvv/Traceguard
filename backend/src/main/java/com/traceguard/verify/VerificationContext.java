package com.traceguard.verify;

import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import com.traceguard.policy.RoutePolicy;

import java.util.List;
import java.util.Map;

/**
 * Everything a {@link VerificationCheck} may need about one tag, loaded once by the service.
 *
 * @param events      the tag's ledger events, oldest first
 * @param checkpoints the checkpoints those events refer to, by id
 * @param policy      the route policy for the product type, or null when none is defined
 */
public record VerificationContext(Tag tag, Batch batch, Product product, List<ScanEvent> events,
                                  Map<Long, Checkpoint> checkpoints, RoutePolicy policy) {

    public String tagId() {
        return tag.getTagId();
    }
}
