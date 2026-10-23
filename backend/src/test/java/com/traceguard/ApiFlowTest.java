package com.traceguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    static final String ADMIN_EMAIL = "admin@test.local";
    static final String ADMIN_PASSWORD = "TestAdmin#2026";
    static final String PASSWORD = "Passw0rd!x";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

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

    private String createUser(String adminToken, String role, Long orgId) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        String org = orgId == null ? "null" : orgId.toString();
        call(post("/api/auth/register"), adminToken,
                "{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\",\"organizationId\":%s}".formatted(email, PASSWORD, role, org), 201);
        return login(email, PASSWORD);
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        call(post("/api/auth/login"), null,
                "{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(ADMIN_EMAIL), 401);
    }

    @Test
    void protectedEndpointNeedsToken() throws Exception {
        call(get("/api/organizations"), null, null, 401);
        call(get("/api/organizations"), "not-a-real-token", null, 401);
    }

    @Test
    void retailerCannotCreateOrganizationOrUsers() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String retailer = createUser(admin, "RETAILER", null);
        call(post("/api/organizations"), retailer, "{\"name\":\"X\",\"type\":\"RETAILER\"}", 403);
        call(post("/api/auth/register"), retailer,
                "{\"email\":\"a@b.com\",\"password\":\"Passw0rd!x\",\"role\":\"ADMIN\"}", 403);
    }

    @Test
    void auditorIsReadOnly() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String auditor = createUser(admin, "AUDITOR", null);
        call(get("/api/audit-log"), auditor, null, 200);
        call(post("/api/products"), auditor, "{\"name\":\"P\",\"productType\":\"t\",\"organizationId\":1}", 403);
        call(post("/api/scans/simulate"), auditor, "{\"tagId\":\"x\",\"checkpointId\":1,\"eventType\":\"RECEIVED\"}", 403);
    }

    @Test
    void fullSupplyChainFlowProducesValidChain() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        long orgId = call(post("/api/organizations"), admin,
                "{\"name\":\"Acme-%s\",\"type\":\"MANUFACTURER\"}".formatted(UUID.randomUUID()), 201).get("id").asLong();
        long factory = call(post("/api/checkpoints"), admin,
                "{\"organizationId\":%d,\"name\":\"Factory\",\"locationName\":\"Boston\",\"latitude\":42.36,\"longitude\":-71.06}".formatted(orgId), 201)
                .get("id").asLong();
        long warehouse = call(post("/api/checkpoints"), admin,
                "{\"organizationId\":%d,\"name\":\"Warehouse\",\"locationName\":\"Albany\",\"latitude\":42.65,\"longitude\":-73.75}".formatted(orgId), 201)
                .get("id").asLong();

        String manufacturer = createUser(admin, "MANUFACTURER", orgId);
        String distributor = createUser(admin, "DISTRIBUTOR", null);

        long productId = call(post("/api/products"), manufacturer,
                "{\"name\":\"Aspirin\",\"productType\":\"pharma\"}", 201).get("id").asLong();
        long batchId = call(post("/api/products/%d/batches".formatted(productId)), manufacturer,
                "{\"label\":\"B-001\"}", 201).get("id").asLong();
        JsonNode issued = call(post("/api/batches/%d/tags?count=2".formatted(batchId)), manufacturer, null, 201);
        assertEquals(2, issued.get("tagIds").size());
        String tagId = issued.get("tagIds").get(0).asText();

        call(post("/api/scans/simulate"), manufacturer,
                "{\"tagId\":\"%s\",\"checkpointId\":%d,\"eventType\":\"REGISTERED\"}".formatted(tagId, factory), 201);
        JsonNode second = call(post("/api/scans/simulate"), distributor,
                "{\"tagId\":\"%s\",\"checkpointId\":%d,\"eventType\":\"RECEIVED\"}".formatted(tagId, warehouse), 201);
        assertEquals(2, second.get("seq").asInt());

        JsonNode events = call(get("/api/tags/%s/events".formatted(tagId)), distributor, null, 200);
        assertEquals(2, events.size());
        assertEquals(events.get(0).get("hash").asText(), events.get(1).get("prevHash").asText());

        JsonNode check = call(get("/api/tags/%s/chain-check".formatted(tagId)), distributor, null, 200);
        assertTrue(check.get("valid").asBoolean(), check.toString());

        // Recall flags the batch and blocks further tag issuing.
        assertTrue(call(post("/api/batches/%d/recall".formatted(batchId)), manufacturer, null, 200).get("recalled").asBoolean());
        call(post("/api/batches/%d/tags?count=1".formatted(batchId)), manufacturer, null, 400);
    }

    @Test
    void manufacturerCannotTouchAnotherOrganizationsProduct() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        long orgA = call(post("/api/organizations"), admin,
                "{\"name\":\"A-%s\",\"type\":\"MANUFACTURER\"}".formatted(UUID.randomUUID()), 201).get("id").asLong();
        long orgB = call(post("/api/organizations"), admin,
                "{\"name\":\"B-%s\",\"type\":\"MANUFACTURER\"}".formatted(UUID.randomUUID()), 201).get("id").asLong();
        String manA = createUser(admin, "MANUFACTURER", orgA);
        String manB = createUser(admin, "MANUFACTURER", orgB);

        long productA = call(post("/api/products"), manA, "{\"name\":\"PA\",\"productType\":\"t\"}", 201).get("id").asLong();

        call(post("/api/products/%d/batches".formatted(productA)), manB, "{\"label\":\"hack\"}", 404);
    }
}
