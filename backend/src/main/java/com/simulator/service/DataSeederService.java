package com.simulator.service;

import com.simulator.model.RequestMapping;
import com.simulator.repository.RequestMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class DataSeederService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeederService.class);

    @Value("${simulator.active-dataset}")
    private String activeDataset;

    @Autowired
    private RequestMappingRepository mappingRepository;

    @Autowired
    private MappingService mappingService;

    @Override
    public void run(String... args) {
        seedDefaultMappings();
    }

    public void seedDefaultMappings() {
        List<RequestMapping> existingMappings = mappingRepository.findByDatasetOrderByPriorityDesc(activeDataset);
        
        if (!existingMappings.isEmpty()) {
            logger.info("Dataset '{}' already contains {} mappings, skipping seed", activeDataset, existingMappings.size());
            return;
        }

        logger.info("Seeding default mappings for dataset: {}", activeDataset);
        
        List<RequestMapping> seedMappings = Arrays.asList(
            createHelloMapping(),
            createOrderMapping(),
            createFlakyMapping()
        );

        for (RequestMapping mapping : seedMappings) {
            mapping.setDataset(activeDataset);
            mapping.setCreatedAt(Instant.now());
            mapping.setUpdatedAt(Instant.now());
        }

        mappingRepository.saveAll(seedMappings);
        logger.info("Seeded {} default mappings", seedMappings.size());

        // Reload WireMock with seeded mappings
        mappingService.reloadWireMockMappings();
        logger.info("WireMock reloaded with seeded mappings");
    }

    private RequestMapping createHelloMapping() {
        RequestMapping mapping = new RequestMapping();
        mapping.setId(UUID.randomUUID().toString());
        mapping.setName("Get Hello World");
        mapping.setPriority(5);
        mapping.setEnabled(true);
        mapping.setTags(Arrays.asList("hello", "demo", "get"));

        // Request
        RequestMapping.RequestSpec request = new RequestMapping.RequestSpec();
        request.setMethod("GET");
        request.setPath("/hello");
        request.setHeaders(new HashMap<>());
        request.setQueryParams(new HashMap<>());
        mapping.setRequest(request);

        // Response
        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(200);
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Type", "application/json");
        response.setHeaders(responseHeaders);
        response.setBody("{\"message\": \"world\", \"timestamp\": \"{{now}}\", \"source\": \"API Simulator\"}");
        response.setTemplatingEnabled(true);
        mapping.setResponse(response);

        // Delays
        RequestMapping.DelaySpec delays = new RequestMapping.DelaySpec();
        delays.setMode("fixed");
        delays.setFixedMs(200);
        delays.setErrorRatePercent(0);
        mapping.setDelays(delays);

        return mapping;
    }

    private RequestMapping createOrderMapping() {
        RequestMapping mapping = new RequestMapping();
        mapping.setId(UUID.randomUUID().toString());
        mapping.setName("Create Order - Echo Body");
        mapping.setPriority(5);
        mapping.setEnabled(true);
        mapping.setTags(Arrays.asList("orders", "demo", "post"));

        // Request
        RequestMapping.RequestSpec request = new RequestMapping.RequestSpec();
        request.setMethod("POST");
        request.setPath("/orders");
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        request.setHeaders(requestHeaders);
        request.setQueryParams(new HashMap<>());
        mapping.setRequest(request);

        // Response
        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(201);
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Type", "application/json");
        response.setHeaders(responseHeaders);
        response.setBody("{\"id\": \"{{randomValue type='UUID'}}\", \"status\": \"created\", \"timestamp\": \"{{now}}\", \"receivedData\": {{request.body}}}");
        response.setTemplatingEnabled(true);
        mapping.setResponse(response);

        // Delays - Variable delay
        RequestMapping.DelaySpec delays = new RequestMapping.DelaySpec();
        delays.setMode("variable");
        delays.setVariableMinMs(100);
        delays.setVariableMaxMs(800);
        delays.setErrorRatePercent(0);
        mapping.setDelays(delays);

        return mapping;
    }

    private RequestMapping createFlakyMapping() {
        RequestMapping mapping = new RequestMapping();
        mapping.setId(UUID.randomUUID().toString());
        mapping.setName("Flaky Endpoint - 15% Error Rate");
        mapping.setPriority(5);
        mapping.setEnabled(true);
        mapping.setTags(Arrays.asList("flaky", "demo", "chaos"));

        // Request
        RequestMapping.RequestSpec request = new RequestMapping.RequestSpec();
        request.setMethod("GET");
        request.setPath("/flaky");
        request.setHeaders(new HashMap<>());
        request.setQueryParams(new HashMap<>());
        mapping.setRequest(request);

        // Response
        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(200);
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Type", "application/json");
        response.setHeaders(responseHeaders);
        response.setBody("{\"message\": \"Success! This endpoint works 85% of the time\", \"timestamp\": \"{{now}}\", \"requestId\": \"{{randomValue type='UUID'}}\"}");
        response.setTemplatingEnabled(true);
        mapping.setResponse(response);

        // Delays with chaos injection
        RequestMapping.DelaySpec delays = new RequestMapping.DelaySpec();
        delays.setMode("fixed");
        delays.setFixedMs(300);
        delays.setErrorRatePercent(15); // 15% error rate
        
        // Error response
        RequestMapping.ErrorResponse errorResponse = new RequestMapping.ErrorResponse();
        errorResponse.setStatus(500);
        errorResponse.setBody("{\"error\": \"Service temporarily unavailable\", \"code\": \"FLAKY_ERROR\", \"timestamp\": \"{{now}}\", \"retryAfter\": 30}");
        delays.setErrorResponse(errorResponse);
        
        mapping.setDelays(delays);

        return mapping;
    }
}