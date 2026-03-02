package com.simulator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class MockServerNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(MockServerNotificationService.class);

    private final List<String> mockServerUrls;
    private final HttpClient httpClient;

    public MockServerNotificationService(
            @Value("${simulator.mock-server.urls:}") String urls) {
        if (urls != null && !urls.isBlank()) {
            this.mockServerUrls = List.of(urls.split(","));
        } else {
            this.mockServerUrls = Collections.emptyList();
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        logger.info("MockServerNotificationService configured with {} mock server(s)", mockServerUrls.size());
    }

    @Async
    public void notifyRefreshAsync() {
        for (String url : mockServerUrls) {
            try {
                String endpoint = url.trim() + "/refresh";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                logger.info("Notified mock server {} to refresh, status: {}", url.trim(), response.statusCode());
            } catch (Exception e) {
                logger.warn("Failed to notify mock server {} to refresh: {}", url.trim(), e.getMessage());
            }
        }
    }

    public Map<String, Object> refreshAllServers() {
        Map<String, Object> results = new HashMap<>();
        for (String url : mockServerUrls) {
            try {
                String endpoint = url.trim() + "/refresh";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                results.put(url.trim(), Map.of("status", response.statusCode(), "body", response.body()));
            } catch (Exception e) {
                results.put(url.trim(), Map.of("status", "error", "message", e.getMessage()));
            }
        }
        return results;
    }
}
