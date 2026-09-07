package io.docflow.api.core.client.controller;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.PlanTier;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.entity.UsageRecord;
import io.docflow.api.infrastructure.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for MeController (Client Dashboard).
 * Verifies that the current usage statistics are correctly retrieved
 * based on the authenticated client's plan and usage records.
 */
public class MeControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("DASHBOARD: Should return correct usage statistics for the current month")
    void shouldReturnCorrectUsageStatistics() throws Exception {
        String rawKey = "dashboard-test-key";
        Plan freePlan = getFreePlan();

        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Dashboard Corp")
                .apiKeyHash(HashUtils.sha256(rawKey))
                .status(ClientStatus.ACTIVE)
                .plan(freePlan)
                .build());

        String currentMonth = LocalDate.now().toString().substring(0, 7);
        usageRecordRepository.save(UsageRecord.builder()
                .client(client)
                .usageMonth(currentMonth)
                .requestCount(15)
                .build());

        when(clientCacheService.getClientByApiKey(rawKey))
                .thenReturn(Optional.of(ApiClientDto.builder()
                        .id(client.getId())
                        .companyName(client.getCompanyName())
                        .status(ClientStatus.ACTIVE)
                        .monthlyQuota(freePlan.getMonthlyQuota())
                        .planName(PlanTier.FREE)
                        .rateLimitPerMin(freePlan.getRateLimitPerMin())
                        .apiKeyHash(client.getApiKeyHash())
                        .build()));

        mockMvc.perform(get("/api/v1/me/usage")
                .header("X-API-KEY", rawKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName", is("FREE")))
                .andExpect(jsonPath("$.monthlyLimit", is(100)))
                .andExpect(jsonPath("$.usedCredits", is(15)))
                .andExpect(jsonPath("$.remainingCredits", is(85)))
                .andExpect(jsonPath("$.rateLimitPerMinute", is(freePlan.getRateLimitPerMin())));
    }

    @Test
    @DisplayName("DASHBOARD: Should return zero usage when no record exists for current month")
    void shouldReturnZeroUsageWhenNoRecordExists() throws Exception {
        String rawKey = "new-client-key";
        Plan freePlan = getFreePlan();

        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Fresh Crop")
                .apiKeyHash(HashUtils.sha256(rawKey))
                .status(ClientStatus.ACTIVE)
                .plan(freePlan)
                .build());

        when(clientCacheService.getClientByApiKey(rawKey))
                .thenReturn(Optional.of(ApiClientDto.builder()
                        .id(client.getId())
                        .status(ClientStatus.ACTIVE)
                        .monthlyQuota(100)
                        .planName("FREE")
                        .rateLimitPerMin(5)
                        .build()));

        mockMvc.perform(get("/api/v1/me/usage")
                .header("X-API-KEY", rawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCredits", is(0)))
                .andExpect(jsonPath("$.remainingCredits", is(100)));
    }
}
