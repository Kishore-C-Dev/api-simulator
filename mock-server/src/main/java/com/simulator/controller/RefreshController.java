package com.simulator.controller;

import com.simulator.service.MockServerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class RefreshController {

    private static final Logger logger = LoggerFactory.getLogger(RefreshController.class);

    @Autowired
    private MockServerInitializer mockServerInitializer;

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            int count = mockServerInitializer.loadMappingsForNamespace();
            return ResponseEntity.ok(Map.of(
                "message", "WireMock mappings refreshed successfully",
                "namespace", mockServerInitializer.getNamespace(),
                "mappingsLoaded", count
            ));
        } catch (Exception e) {
            logger.error("Error refreshing WireMock mappings", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to refresh: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "namespace", mockServerInitializer.getNamespace(),
            "status", "running",
            "enabledMappingsInDb", mockServerInitializer.getEnabledMappingCount()
        ));
    }
}
