package com.traceguard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "traceguard.public.rate-limit-per-minute=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicRateLimitTest {

    @Autowired MockMvc mvc;

    @Test
    void publicEndpointIsLimitedPerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/public/verify/TG-X")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/public/verify/TG-X")).andExpect(status().isTooManyRequests());
    }

    @Test
    void limitDoesNotApplyToOtherEndpoints() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(get("/api/organizations")).andExpect(status().isUnauthorized());
        }
    }
}
