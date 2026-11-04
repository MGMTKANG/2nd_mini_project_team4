package com.example.sporty.features.points.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.points.service.PointService;

@WebMvcTest(controllers = PointOperationController.class, properties = {
        "jwt.secret=test-only-signing-secret-with-at-least-32-bytes",
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PointOperationControllerTest {
    private static final String ID = "fe5ae5fa-1a7d-4821-b491-5b3ef47153d2";
    @Autowired private MockMvc mvc;
    @MockitoBean private PointService service;

    @ParameterizedTest
    @ValueSource(strings = {"deduct", "refund", "cancel"})
    void keyedOperationsAreAccessibleWithoutUserTokenAndForwardTheKey(String action) throws Exception {
        switch (action) {
            case "deduct" -> when(service.deduct(1L, 2000L, ID)).thenReturn(8000L);
            case "refund" -> when(service.refund(1L, 2000L, ID)).thenReturn(10000L);
            case "cancel" -> when(service.cancel(1L, 2000L, ID)).thenReturn(10000L);
        }
        mvc.perform(post(path(action)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":2000,\"operationId\":\"" + ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(action.equals("deduct") ? 8000 : 10000));
        switch (action) {
            case "deduct" -> verify(service).deduct(1L, 2000L, ID);
            case "refund" -> verify(service).refund(1L, 2000L, ID);
            case "cancel" -> verify(service).cancel(1L, 2000L, ID);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"deduct", "refund", "cancel"})
    void keyedOperationsRejectMissingBlankOrMalformedKeyBeforeMovingMoney(String action) throws Exception {
        for (String body : new String[]{"{\"amount\":2000}",
                "{\"amount\":2000,\"operationId\":null}",
                "{\"amount\":2000,\"operationId\":\"\"}",
                "{\"amount\":2000,\"operationId\":\"invalid\"}"}) {
            mvc.perform(post(path(action)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deduct", "refund", "cancel"})
    void keyedOperationsRejectMissingZeroOrNegativeAmount(String action) throws Exception {
        for (String body : new String[]{"{\"operationId\":\"" + ID + "\"}",
                "{\"amount\":0,\"operationId\":\"" + ID + "\"}",
                "{\"amount\":-1,\"operationId\":\"" + ID + "\"}"}) {
            mvc.perform(post(path(action)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    private static String path(String action) { return "/internal/points/1/operations/" + action; }
}
