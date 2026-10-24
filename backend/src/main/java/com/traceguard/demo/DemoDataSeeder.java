package com.traceguard.demo;

import com.traceguard.auth.AppUser;
import com.traceguard.auth.AppUserRepository;
import com.traceguard.auth.Role;
import com.traceguard.catalog.Catalog.Batch;
import com.traceguard.catalog.Catalog.Product;
import com.traceguard.catalog.Catalog.Tag;
import com.traceguard.catalog.CatalogRepositories.BatchRepository;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.crypto.KeyService;
import com.traceguard.ledger.LedgerService;
import com.traceguard.ledger.ScanEvent.Type;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import com.traceguard.org.OrgRepositories.OrganizationRepository;
import com.traceguard.org.Organization;
import com.traceguard.policy.RoutePolicy;
import com.traceguard.policy.RoutePolicy.Step;
import com.traceguard.policy.RoutePolicyRepository;
import com.traceguard.verify.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Loads a ready-made demo supply chain with one tag per verdict scenario. Active only with the "dev" profile
 * and traceguard.demo.seed=true; never runs in production or tests.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "traceguard.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrganizationRepository orgs;
    private final CheckpointRepository checkpoints;
    private final KeyService keys;
    private final RoutePolicyRepository policies;
    private final ProductRepository products;
    private final BatchRepository batches;
    private final TagRepository tags;
    private final LedgerService ledger;
    private final VerificationService verification;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final String password;

    public DemoDataSeeder(OrganizationRepository orgs, CheckpointRepository checkpoints, KeyService keys,
                          RoutePolicyRepository policies, ProductRepository products, BatchRepository batches,
                          TagRepository tags, LedgerService ledger, VerificationService verification,
                          AppUserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc,
                          @Value("${traceguard.demo.password}") String password) {
        this.orgs = orgs;
        this.checkpoints = checkpoints;
        this.keys = keys;
        this.policies = policies;
        this.products = products;
        this.batches = batches;
        this.tags = tags;
        this.ledger = ledger;
        this.verification = verification;
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (orgs.existsByName("Demo Pharma Inc.")) return;
        Instant now = Instant.now();

        Organization pharma = orgs.save(new Organization("Demo Pharma Inc.", Organization.Type.MANUFACTURER));
        Organization logistics = orgs.save(new Organization("Demo Logistics", Organization.Type.DISTRIBUTOR));
        Organization pharmacy = orgs.save(new Organization("Demo Pharmacy", Organization.Type.RETAILER));

        Checkpoint factory = checkpoint(pharma, "Factory", "Boston, MA", 42.3601, -71.0589);
        Checkpoint warehouse = checkpoint(logistics, "Warehouse", "Albany, NY", 42.6526, -73.7562);
        Checkpoint store = checkpoint(pharmacy, "Pharmacy", "Chicago, IL", 41.8781, -87.6298);

        policies.save(new RoutePolicy("pharma", 168, List.of(
                new Step(factory.getId(), null), new Step(warehouse.getId(), 48), new Step(store.getId(), 72))));

        user("manufacturer@demo.local", Role.MANUFACTURER, pharma.getId());
        user("distributor@demo.local", Role.DISTRIBUTOR, logistics.getId());
        user("retailer@demo.local", Role.RETAILER, pharmacy.getId());
        user("auditor@demo.local", Role.AUDITOR, null);

        Product aspirin = products.save(new Product(pharma.getId(), "Aspirin 100mg", "pharma"));
        Batch main = batches.save(new Batch(aspirin.getId(), "DEMO-1"));
        Batch recalled = batches.save(new Batch(aspirin.getId(), "DEMO-2"));
        recalled.recall();
        batches.save(recalled);

        long f = factory.getId(), w = warehouse.getId(), p = store.getId();

        // AUTHENTIC: full approved route within every time limit.
        tag("TG-DEMO-GENUINE", main);
        scan("TG-DEMO-GENUINE", f, Type.REGISTERED, now.minus(Duration.ofHours(30)));
        scan("TG-DEMO-GENUINE", w, Type.RECEIVED, now.minus(Duration.ofHours(24)));
        scan("TG-DEMO-GENUINE", p, Type.DELIVERED, now.minus(Duration.ofHours(2)));

        // COUNTERFEIT: the same tag in Boston and Chicago 40 seconds apart (cloned tag).
        tag("TG-DEMO-CLONED", main);
        scan("TG-DEMO-CLONED", f, Type.REGISTERED, now.minus(Duration.ofHours(10)));
        scan("TG-DEMO-CLONED", p, Type.RECEIVED, now.minus(Duration.ofHours(10)).plusSeconds(40));

        // COUNTERFEIT: Boston to Albany (about 200 km) in two minutes.
        tag("TG-DEMO-FAST", main);
        scan("TG-DEMO-FAST", f, Type.REGISTERED, now.minus(Duration.ofHours(3)));
        scan("TG-DEMO-FAST", w, Type.RECEIVED, now.minus(Duration.ofHours(3)).plusSeconds(120));

        // SUSPECT: the warehouse step was skipped.
        tag("TG-DEMO-DETOUR", main);
        scan("TG-DEMO-DETOUR", f, Type.REGISTERED, now.minus(Duration.ofHours(40)));
        scan("TG-DEMO-DETOUR", p, Type.RECEIVED, now.minus(Duration.ofHours(20)));

        // SUSPECT: 100 hours to reach the warehouse (limit 48).
        tag("TG-DEMO-LATE", main);
        scan("TG-DEMO-LATE", f, Type.REGISTERED, now.minus(Duration.ofHours(200)));
        scan("TG-DEMO-LATE", w, Type.RECEIVED, now.minus(Duration.ofHours(100)));

        // COUNTERFEIT: a genuine journey whose second event is then edited directly in the database.
        tag("TG-DEMO-TAMPERED", main);
        scan("TG-DEMO-TAMPERED", f, Type.REGISTERED, now.minus(Duration.ofHours(30)));
        scan("TG-DEMO-TAMPERED", w, Type.RECEIVED, now.minus(Duration.ofHours(24)));
        scan("TG-DEMO-TAMPERED", p, Type.DELIVERED, now.minus(Duration.ofHours(2)));
        jdbc.update("update events set checkpoint_id = ? where tag_id = ? and seq = 2", p, "TG-DEMO-TAMPERED");

        // SUSPECT: genuine journey, but the manufacturer recalled the batch.
        tag("TG-DEMO-RECALLED", recalled);
        scan("TG-DEMO-RECALLED", f, Type.REGISTERED, now.minus(Duration.ofHours(30)));
        scan("TG-DEMO-RECALLED", w, Type.RECEIVED, now.minus(Duration.ofHours(24)));

        // SUSPECT: issued but never scanned.
        tag("TG-DEMO-NOHISTORY", main);

        for (String id : List.of("TG-DEMO-GENUINE", "TG-DEMO-CLONED", "TG-DEMO-FAST", "TG-DEMO-DETOUR", "TG-DEMO-LATE",
                "TG-DEMO-TAMPERED", "TG-DEMO-RECALLED", "TG-DEMO-NOHISTORY")) {
            verification.verify(id, true, true);
        }
        log.info("Demo data loaded: 8 demo tags (TG-DEMO-*), users manufacturer/distributor/retailer/auditor@demo.local");
    }

    private Checkpoint checkpoint(Organization org, String name, String location, double lat, double lon) {
        Checkpoint c = checkpoints.save(new Checkpoint(org.getId(), name, location, lat, lon));
        c.setPublicKey(keys.generateFor(c.getId()));
        return checkpoints.save(c);
    }

    private void user(String email, Role role, Long orgId) {
        users.save(new AppUser(email, encoder.encode(password), role, orgId));
    }

    private void tag(String id, Batch batch) {
        tags.save(new Tag(id, batch.getId()));
    }

    private void scan(String tagId, long checkpointId, Type type, Instant at) {
        ledger.append(tagId, checkpointId, type, at, null);
    }
}
