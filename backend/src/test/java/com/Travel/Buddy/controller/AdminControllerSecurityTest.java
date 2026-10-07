package com.Travel.Buddy.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void financeRefundQueueDeniesUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/refunds"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void financeRefundQueueIsForbiddenToTravelers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/refunds"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void financeRefundQueueIsAvailableToSuperAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/refunds"))
                .andExpect(status().isOk());
    }
}
