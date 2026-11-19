package com.traceguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traceguard.anomaly.MovementRule;
import com.traceguard.anomaly.ScanBurstRule;
import com.traceguard.verify.ChainIntegrityCheck;
import com.traceguard.verify.HistoryCheck;
import com.traceguard.verify.RecallCheck;
import com.traceguard.verify.RouteChecker;
import com.traceguard.verify.VerificationCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end workflows through the REST API, the verification engine and the database together:
 * a route-checked journey, tampering detected and handled in the alert center, a recall seen by consumers,
 * and the wiring of the verification checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowIntegrationTest {

    static final String PASSWORD = "Passw0rd!x";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired List<VerificationCheck> checks;

    String admin;
    String manufacturer;
    long factory, warehouse, pharmacy;
    String productType;
    long batchId;

    /** A fresh manufacturer with three nearby checkpoints, one product and one batch for every test. */
    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@test.local", "TestAdmin#2026");
        long org = call(post("/api/organizations"), admin,
                "{\"name\":\"W-%s\",\"type\":\"MANUFACTURER\"}".formatted(UUID.randomUUID()), 201).get("id").asLong();
        factory = checkpoint(org, "Factory", 42.36, -71.06);
        warehouse = checkpoint(org, "Warehouse", 42.65, -73.75);
        pharmacy = checkpoint(org, "Pharmacy", 42.89, -78.88);

        String email = "maker-" + UUID.randomUUID() + "@test.local";
        call(post("/api/auth/register"), admin,
                "{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"MANUFACTURER\",\"organizationId\":%d}".formatted(email, PASSWORD, org), 201);
        manufacturer = login(email, PASSWORD);

        productType = "type-" + UUID.randomUUID();
        long product = call(post("/api/products"), manufacturer,
                "{\"name\":\"Insulin\",\"productType\":\"%s\"}".formatted(productType), 201).get("id").asLong();
        batchId = call(post("/api/products/%d/batches".formatted(product)), manufacturer, "{\"label\":\"B-7\"}", 201)
                .get("id").asLong();
    }

    @Test
    void journeyOnTheApprovedRouteIsAuthenticAndASkippedStopIsSuspect() throws Exception {
        call(post("/api/route-policies"), admin,
                ("{\"productType\":\"%s\",\"maxTotalHours\":72,\"steps\":[{\"checkpointId\":%d},"
                        + "{\"checkpointId\":%d,\"maxHoursFromPrevious\":24},{\"checkpointId\":%d,\"maxHoursFromPrevious\":24}]}")
                        .formatted(productType, factory, warehouse, pharmacy), 201);
        List<String> tags = issueTags(2);
        Instant start = Instant.now().minus(Duration.ofHours(10)).truncatedTo(ChronoUnit.SECONDS);

        scan(tags.get(0), factory, "REGISTERED", start);
        scan(tags.get(0), warehouse, "RECEIVED", start.plus(Duration.ofHours(3)));
        scan(tags.get(0), pharmacy, "DELIVERED", start.plus(Duration.ofHours(6)));
        JsonNode genuine = call(get("/api/verify/" + tags.get(0)), manufacturer, null, 200);
        assertEquals("AUTHENTIC", genuine.get("verdict").asText(), genuine.toString());
        assertEquals(List.of("REGISTERED", "RECEIVED", "DELIVERED"), field(genuine.get("timeline"), "eventType"));

        scan(tags.get(1), factory, "REGISTERED", start);
        scan(tags.get(1), pharmacy, "DELIVERED", start.plus(Duration.ofHours(6)));
        JsonNode detour = call(get("/api/verify/" + tags.get(1)), manufacturer, null, 200);
        assertEquals("SUSPECT", detour.get("verdict").asText());
        assertEquals(List.of("ROUTE_DEVIATION"), field(detour.get("reasons"), "code"));

        // The product-history report carries the same finding.
        String csv = mvc.perform(get("/api/reports/tags/" + tags.get(1)).header("Authorization", "Bearer " + manufacturer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("ROUTE_DEVIATION"), csv);
    }

    @Test
    void tamperedEventIsCounterfeitRaisesAHighAlertAndTheAlertCanBeResolved() throws Exception {
        String tag = issueTags(1).get(0);
        Instant start = Instant.now().minus(Duration.ofHours(5)).truncatedTo(ChronoUnit.SECONDS);
        scan(tag, factory, "REGISTERED", start);
        scan(tag, warehouse, "RECEIVED", start.plus(Duration.ofHours(3)));

        // Someone edits the stored event directly in the database, bypassing the append-only API.
        jdbc.update("UPDATE events SET occurred_at = ? WHERE tag_id = ? AND seq = 1",
                java.sql.Timestamp.from(start.minus(Duration.ofDays(1))), tag);

        JsonNode verdict = call(get("/api/verify/" + tag), manufacturer, null, 200);
        assertEquals("COUNTERFEIT", verdict.get("verdict").asText());
        assertTrue(field(verdict.get("reasons"), "code").contains("HASH_MISMATCH"), verdict.toString());

        JsonNode alert = openAlert(tag, "HASH_MISMATCH");
        assertNotNull(alert, "a HASH_MISMATCH alert should be open");
        assertEquals("HIGH", alert.get("severity").asText());

        call(patch("/api/alerts/" + alert.get("id").asLong()), manufacturer, "{\"status\":\"RESOLVED\"}", 200);
        assertNull(openAlert(tag, "HASH_MISMATCH"));
    }

    @Test
    void recalledBatchShowsAsSuspectToConsumersWithoutLogin() throws Exception {
        String tag = issueTags(1).get(0);
        scan(tag, factory, "REGISTERED", Instant.now().minus(Duration.ofHours(1)));

        assertEquals("AUTHENTIC", call(get("/api/public/verify/" + tag), null, null, 200).get("verdict").asText());

        call(post("/api/batches/%d/recall".formatted(batchId)), manufacturer, null, 200);
        JsonNode after = call(get("/api/public/verify/" + tag), null, null, 200);
        assertEquals("SUSPECT", after.get("verdict").asText());
        assertTrue(field(after.get("reasons"), "code").contains("BATCH_RECALLED"));
    }

    @Test
    void everyVerificationCheckIsRegisteredAndRunsInItsDocumentedOrder() {
        assertEquals(List.of(RecallCheck.class, HistoryCheck.class, ChainIntegrityCheck.class, RouteChecker.class,
                        MovementRule.class, ScanBurstRule.class),
                checks.stream().map(c -> (Class<?>) org.springframework.aop.support.AopUtils.getTargetClass(c)).toList());
    }

    // ---- helpers ----

    private JsonNode call(MockHttpServletRequestBuilder req, String token, String body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        MvcResult r = mvc.perform(req).andExpect(status().is(expected)).andReturn();
        String text = r.getResponse().getContentAsString();
        return text.isBlank() ? null : json.readTree(text);
    }

    private String login(String email, String password) throws Exception {
        return call(post("/api/auth/login"), null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), 200).get("token").asText();
    }

    private long checkpoint(long org, String name, double lat, double lon) throws Exception {
        return call(post("/api/checkpoints"), admin,
                "{\"organizationId\":%d,\"name\":\"%s\",\"locationName\":\"%s\",\"latitude\":%s,\"longitude\":%s}"
                        .formatted(org, name, name, lat, lon), 201).get("id").asLong();
    }

    private List<String> issueTags(int count) throws Exception {
        JsonNode ids = call(post("/api/batches/%d/tags?count=%d".formatted(batchId, count)), manufacturer, null, 201).get("tagIds");
        List<String> out = new ArrayList<>();
        ids.forEach(n -> out.add(n.asText()));
        return out;
    }

    private void scan(String tag, long checkpoint, String type, Instant at) throws Exception {
        call(post("/api/scans/simulate"), manufacturer,
                "{\"tagId\":\"%s\",\"checkpointId\":%d,\"eventType\":\"%s\",\"timestamp\":\"%s\"}".formatted(tag, checkpoint, type, at), 201);
    }

    private JsonNode openAlert(String tag, String code) throws Exception {
        JsonNode open = call(get("/api/alerts?status=OPEN"), manufacturer, null, 200);
        return StreamSupport.stream(open.spliterator(), false)
                .filter(a -> a.get("tagId").asText().equals(tag) && a.get("code").asText().equals(code))
                .findFirst().orElse(null);
    }

    private static List<String> field(JsonNode array, String name) {
        return StreamSupport.stream(array.spliterator(), false).map(n -> n.get(name).asText()).toList();
    }
}
