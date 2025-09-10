package com.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TestController.class)
class TestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void echo_WithJsonBody_ShouldReturnParsedJson() throws Exception {
        String jsonBody = "{\"message\": \"Hello\", \"number\": 42}";

        mockMvc.perform(post("/test/echo")
                .contentType("application/json")
                .header("X-Custom-Header", "test-value")
                .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.receivedHeaders['content-type']").value("application/json"))
                .andExpect(jsonPath("$.receivedHeaders['x-custom-header']").value("test-value"))
                .andExpect(jsonPath("$.receivedBody.message").value("Hello"))
                .andExpect(jsonPath("$.receivedBody.number").value(42))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void echo_WithInvalidJson_ShouldReturnAsString() throws Exception {
        String invalidJson = "{invalid json}";

        mockMvc.perform(post("/test/echo")
                .contentType("application/json")
                .content(invalidJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.receivedBody").value(invalidJson))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void echo_WithEmptyBody_ShouldReturnNullBody() throws Exception {
        mockMvc.perform(post("/test/echo")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.receivedBody").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void echo_WithMultipleHeaders_ShouldCaptureAllHeaders() throws Exception {
        mockMvc.perform(post("/test/echo")
                .header("Authorization", "Bearer token123")
                .header("X-Tenant", "demo")
                .header("Accept", "application/json")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedHeaders['authorization']").value("Bearer token123"))
                .andExpect(jsonPath("$.receivedHeaders['x-tenant']").value("demo"))
                .andExpect(jsonPath("$.receivedHeaders['accept']").value("application/json"));
    }
}