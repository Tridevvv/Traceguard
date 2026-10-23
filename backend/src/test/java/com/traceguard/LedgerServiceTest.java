package com.traceguard;

import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import com.traceguard.catalog.CatalogRepositories.BatchRepository;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.common.Errors.ConflictException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.crypto.HashUtil;
import com.traceguard.crypto.KeyService;
import com.traceguard.ledger.LedgerService;
import com.traceguard.ledger.LedgerService.ChainReport;
import com.traceguard.ledger.ScanEvent;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.Organization;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.org.OrgRepositories.OrganizationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerServiceTest {

    @Autowired LedgerService ledger;
    @Autowired OrganizationRepository orgs;
    @Autowired CheckpointRepository checkpoints;
    @Autowired ProductRepository products;
    @Autowired BatchRepository batches;
    @Autowired TagRepository tags;
    @Autowired KeyService keys;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    String tagId;
    long factory;
    long warehouse;

    @BeforeEach
    void setUp() {
        Organization org = orgs.save(new Organization("Org-" + UUID.randomUUID(), Organization.Type.MANUFACTURER));
        factory = checkpoint(org, "Factory");
        warehouse = checkpoint(org, "Warehouse");
        Product p = products.save(new Product(org.getId(), "Aspirin", "pharma"));
        Batch b = batches.save(new Batch(p.getId(), "B-1"));
        tagId = "TG-" + UUID.randomUUID().toString().substring(0, 12);
        tags.save(new Tag(tagId, b.getId()));
    }

    private long checkpoint(Organization org, String name) {
        Checkpoint c = checkpoints.save(new Checkpoint(org.getId(), name, name + " site", 42.0, -71.0));
        c.setPublicKey(keys.generateFor(c.getId()));
        return c.getId();
    }

    @Test
    void eventsAreSequencedAndLinkedByHash() {
        ScanEvent first = ledger.append(tagId, factory, ScanEvent.Type.REGISTERED, null, null);
        ScanEvent second = ledger.append(tagId, warehouse, ScanEvent.Type.RECEIVED, null, null);

        assertEquals(1, first.getSeq());
        assertEquals(HashUtil.GENESIS_HASH, first.getPrevHash());
        assertEquals(2, second.getSeq());
        assertEquals(first.getHash(), second.getPrevHash());
        assertEquals(64, second.getHash().length());
    }

    @Test
    void untouchedChainPassesCheck() {
        ledger.append(tagId, factory, ScanEvent.Type.REGISTERED, null, null);
        ledger.append(tagId, warehouse, ScanEvent.Type.RECEIVED, null, null);
        ledger.append(tagId, warehouse, ScanEvent.Type.IN_TRANSIT, null, null);

        ChainReport report = ledger.checkChain(tagId);

        assertTrue(report.valid(), report.problems().toString());
        assertEquals(3, report.eventCount());
    }

    @Test
    void editingAnEventBreaksTheChain() {
        ledger.append(tagId, factory, ScanEvent.Type.REGISTERED, null, null);
        ledger.append(tagId, warehouse, ScanEvent.Type.RECEIVED, null, null);
        ledger.append(tagId, warehouse, ScanEvent.Type.IN_TRANSIT, null, null);
        em.flush();

        // Simulate an attacker editing event 2 directly in the database.
        jdbc.update("update events set checkpoint_id = ? where tag_id = ? and seq = 2", factory, tagId);
        em.clear();

        ChainReport report = ledger.checkChain(tagId);

        assertFalse(report.valid());
        assertTrue(report.problems().stream().anyMatch(p -> p.startsWith("Event 2") && p.contains("tampered")),
                report.problems().toString());
    }

    @Test
    void forgedSignatureIsDetected() {
        ledger.append(tagId, factory, ScanEvent.Type.REGISTERED, null, null);
        em.flush();

        jdbc.update("update events set signature = ? where tag_id = ?", "AAAA", tagId);
        em.clear();

        ChainReport report = ledger.checkChain(tagId);

        assertFalse(report.valid());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("signature")));
    }

    @Test
    void replayedNonceIsRejected() {
        ledger.append(tagId, factory, ScanEvent.Type.REGISTERED, null, "nonce-1");
        assertThrows(ConflictException.class,
                () -> ledger.append(tagId, warehouse, ScanEvent.Type.RECEIVED, null, "nonce-1"));
    }

    @Test
    void unregisteredTagIsRejected() {
        assertThrows(NotFoundException.class,
                () -> ledger.append("TG-DOES-NOT-EXIST", factory, ScanEvent.Type.REGISTERED, null, null));
    }

    @Test
    void emptyChainIsValid() {
        ChainReport report = ledger.checkChain(tagId);
        assertTrue(report.valid());
        assertEquals(List.of(), report.problems());
    }
}
