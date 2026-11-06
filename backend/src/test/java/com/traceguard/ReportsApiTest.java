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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role rules and output formats of /api/reports and /api/analytics. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MvcResult call(MockHttpServletRequestBuilder req, String token, String body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req).andExpect(status().is(expected)).andReturn();
    }

    private JsonNode jsonOf(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return jsonOf(call(post("/api/auth/login"), null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), 200)).get("token").asText();
    }

    private String admin() throws Exception {
        return login("admin@test.local", "TestAdmin#2026");
    }

    private String user(String admin, String role, Long orgId) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        call(post("/api/auth/register"), admin, "{\"email\":\"%s\",\"password\":\"Passw0rd!x\",\"role\":\"%s\",\"organizationId\":%s}"
                .formatted(email, role, orgId == null ? "null" : orgId.toString()), 201);
        return login(email, "Passw0rd!x");
    }

    private long org(String admin) throws Exception {
        return jsonOf(call(post("/api/organizations"), admin,
                "{\"name\":\"R-%s\",\"type\":\"MANUFACTURER\"}".formatted(UUID.randomUUID()), 201)).get("id").asLong();
    }

    /** Creates a product with two tags for the manufacturer and returns the first tag ID. */
    private String tagOf(String manufacturer) throws Exception {
        long product = jsonOf(call(post("/api/products"), manufacturer,
                "{\"name\":\"P\",\"productType\":\"t-%s\"}".formatted(UUID.randomUUID()), 201)).get("id").asLong();
        long batch = jsonOf(call(post("/api/products/%d/batches".formatted(product)), manufacturer, "{\"label\":\"B\"}", 201)).get("id").asLong();
        return jsonOf(call(post("/api/batches/%d/tags?count=2".formatted(batch)), manufacturer, null, 201)).get("tagIds").get(0).asText();
    }

    @Test
    void auditCsvHasHeaderAndTheAdminsOwnActions() throws Exception {
        String admin = admin();
        MvcResult r = call(get("/api/reports/audit?type=actions&format=csv"), admin, null, 200);

        assertTrue(r.getResponse().getContentType().startsWith("text/csv"));
        assertTrue(r.getResponse().getHeader("Content-Disposition").contains("attachment"));
        String csv = new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿Time (UTC),User,Action,Target\r\n"), csv);
        assertTrue(csv.contains("LOGIN"), "the admin's own login is in the activity log");
    }

    @Test
    void auditPdfIsAPdf() throws Exception {
        MvcResult r = call(get("/api/reports/audit?type=alerts&format=pdf"), admin(), null, 200);
        assertEquals("application/pdf", r.getResponse().getContentType());
        assertEquals("%PDF-", new String(r.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1));
    }

    @Test
    void ledgerReportWorksForAuditors() throws Exception {
        String auditor = user(admin(), "AUDITOR", null);
        call(get("/api/reports/audit?type=events"), auditor, null, 200);
    }

    @Test
    void onlyAdminsAndAuditorsMayExportAuditReports() throws Exception {
        String admin = admin();
        call(get("/api/reports/audit"), user(admin, "RETAILER", null), null, 403);
        call(get("/api/reports/audit"), user(admin, "MANUFACTURER", org(admin)), null, 403);
        call(get("/api/reports/audit"), null, null, 401);
    }

    @Test
    void badParametersAreRejected() throws Exception {
        String admin = admin();
        call(get("/api/reports/audit?type=passwords"), admin, null, 400);
        call(get("/api/reports/audit?format=xlsx"), admin, null, 400);
        call(get("/api/reports/audit?from=2026-10-10&to=2026-10-01"), admin, null, 400);
        call(get("/api/reports/audit?from=2020-01-01&to=2026-01-01"), admin, null, 400);
    }

    @Test
    void manufacturerMayExportOnlyItsOwnTagHistory() throws Exception {
        String admin = admin();
        String manA = user(admin, "MANUFACTURER", org(admin));
        String manB = user(admin, "MANUFACTURER", org(admin));
        String tag = tagOf(manA);

        String csv = new String(call(get("/api/reports/tags/" + tag), manA, null, 200).getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertTrue(csv.contains(tag) && csv.contains("SUSPECT"), csv);   // issued but never scanned

        call(get("/api/reports/tags/" + tag), manB, null, 404);
        call(get("/api/reports/tags/TG-NO-SUCH-TAG"), admin, null, 404);
        call(get("/api/reports/tags/" + tag + "?format=pdf"), admin, null, 200);
    }

    @Test
    void analyticsAreScopedByRole() throws Exception {
        String admin = admin();
        String manA = user(admin, "MANUFACTURER", org(admin));
        String manB = user(admin, "MANUFACTURER", org(admin));
        tagOf(manA);

        assertEquals(2, jsonOf(call(get("/api/analytics/summary"), manA, null, 200)).get("tagsIssued").asLong());
        assertEquals(0, jsonOf(call(get("/api/analytics/summary"), manB, null, 200)).get("tagsIssued").asLong());
        assertTrue(jsonOf(call(get("/api/analytics/summary"), admin, null, 200)).get("tagsIssued").asLong() >= 2);

        assertEquals(7, jsonOf(call(get("/api/analytics/scan-volume?days=7"), admin, null, 200)).size());
        call(get("/api/analytics/verdicts"), user(admin, "AUDITOR", null), null, 200);
        call(get("/api/analytics/alerts"), admin, null, 200);
        call(get("/api/analytics/checkpoints"), admin, null, 200);
        call(get("/api/analytics/summary"), user(admin, "RETAILER", null), null, 403);
        call(get("/api/analytics/summary"), user(admin, "DISTRIBUTOR", null), null, 403);
    }
}
