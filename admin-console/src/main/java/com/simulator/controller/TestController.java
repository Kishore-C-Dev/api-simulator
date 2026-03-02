package com.simulator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        
        response.put("receivedHeaders", headers);
        response.put("timestamp", Instant.now().toString());
        
        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonNode jsonNode = objectMapper.readTree(body);
                response.put("receivedBody", jsonNode);
            } catch (Exception e) {
                response.put("receivedBody", body);
            }
        } else {
            response.put("receivedBody", null);
        }
        
        return ResponseEntity.ok(response);
    }
}