package com.example.sporty.features.points.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.exception.points.PointOperationCancelledException;
import com.example.sporty.features.commons.exception.points.PointOperationConflictException;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.points.service.PointService;

@WebMvcTest(controllers = PointInternalController.class, properties = {
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PointInternalControllerTest {
    static final String ID = "fe5ae5fa-1a7d-4821-b491-5b3ef47153d2";
    @Autowired MockMvc mvc;
    @MockitoBean PointService service;

    @Test void deductPassesOperationKey() throws Exception {
        when(service.deduct(1L, 2000L, ID)).thenReturn(8000L);
        mvc.perform(post("/internal/points/1/deduct").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":2000,\"operationId\":\"" + ID + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(8000));
        verify(service).deduct(1L, 2000L, ID);
    }
    @Test void refundPassesOperationKey() throws Exception {
        when(service.refund(1L, 2000L, ID)).thenReturn(10000L);
        mvc.perform(post("/internal/points/1/refund").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":2000,\"operationId\":\"" + ID + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(10000));
        verify(service).refund(1L, 2000L, ID);
    }
    @Test void cancelIsAvailableToInternalClientWithoutUserToken() throws Exception {
        when(service.cancel(1L, 2000L, ID)).thenReturn(10000L);
        mvc.perform(post("/internal/points/1/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":2000,\"operationId\":\"" + ID + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(10000));
        verify(service).cancel(1L, 2000L, ID);
    }
    @Test void cancellationRequiresValidKeyAndPositiveAmount() throws Exception {
        for (String body : new String[]{"{\"amount\":2000}",
                "{\"amount\":2000,\"operationId\":\"invalid\"}",
                "{\"amount\":0,\"operationId\":\"" + ID + "\"}"}) {
            mvc.perform(post("/internal/points/1/cancel").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }
    @Test void oldAmountOnlyRequestsRemainCompatibleDuringPointFirstDeployment() throws Exception {
        when(service.deduct(1L, 2000L, null)).thenReturn(8000L);
        mvc.perform(post("/internal/points/1/deduct").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":2000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(8000));
        verify(service).deduct(1L, 2000L, null);
    }
    @Test void cancelledDebitHasDistinctConflictCode() throws Exception {
        when(service.deduct(1L, 2000L, ID)).thenThrow(new PointOperationCancelledException());
        mvc.perform(post("/internal/points/1/deduct").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":2000,\"operationId\":\"" + ID + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POINT_OPERATION_CANCELLED"));
    }
    @Test void reusedKeyWithDifferentParametersHasDistinctConflictCode() throws Exception {
        when(service.refund(1L, 2000L, ID)).thenThrow(new PointOperationConflictException());
        mvc.perform(post("/internal/points/1/refund").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":2000,\"operationId\":\"" + ID + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POINT_OPERATION_CONFLICT"));
    }
}
