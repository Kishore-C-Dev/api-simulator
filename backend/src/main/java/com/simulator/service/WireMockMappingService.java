package com.simulator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.jayway.jsonpath.JsonPath;
import com.simulator.model.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WireMockMappingService {

    private static final Logger logger = LoggerFactory.getLogger(WireMockMappingService.class);
    private final WireMockServer wireMockServer;
    private final DelayService delayService;
    private final ObjectMapper objectMapper;

    @Autowired
    public WireMockMappingService(WireMockServer wireMockServer, DelayService delayService) {
        this.wireMockServer = wireMockServer;
        this.delayService = delayService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Register JavaTimeModule for Instant support
    }

    public void loadMappings(List<RequestMapping> mappings) {
        wireMockServer.resetAll();
        
        for (RequestMapping mapping : mappings) {
            if (Boolean.TRUE.equals(mapping.getEnabled())) {
                try {
                    createStubMapping(mapping);
                } catch (Exception e) {
                    logger.error("Failed to create stub mapping for {}: {}", mapping.getName(), e.getMessage());
                }
            }
        }
        
        logger.info("Loaded {} enabled mappings into WireMock", mappings.size());
    }

    private void createStubMapping(RequestMapping mapping) {
        MappingBuilder requestBuilder = buildRequestPattern(mapping.getRequest());
        ResponseDefinitionBuilder responseBuilder = buildResponse(mapping);
        
        StubMapping stubMapping = wireMockServer.stubFor(
            requestBuilder.willReturn(responseBuilder)
        );
        stubMapping.setPriority(mapping.getPriority());
        if (mapping.getId() != null) {
            try {
                // Try to parse as UUID first
                stubMapping.setId(UUID.fromString(mapping.getId()));
            } catch (IllegalArgumentException e) {
                // If not a valid UUID, generate one from the ObjectId
                logger.debug("Converting ObjectId {} to UUID for WireMock", mapping.getId());
                // Create a deterministic UUID from the ObjectId
                String uuidString = convertObjectIdToUuid(mapping.getId());
                stubMapping.setId(UUID.fromString(uuidString));
            }
        }
        
        logger.debug("Created stub mapping: {} {} -> {}", 
            mapping.getRequest().getMethod(), 
            mapping.getRequest().getPath(), 
            mapping.getResponse().getStatus());
    }

    private MappingBuilder buildRequestPattern(RequestMapping.RequestSpec request) {
        MappingBuilder builder;
        
        String method = request.getMethod().toUpperCase();
        UrlPattern urlPattern;
        
        if (request.getPath().contains("{") || request.getPath().contains("*")) {
            String regex = request.getPath()
                .replaceAll("\\{[^}]*\\}", "[^/]+")
                .replaceAll("\\*", ".*");
            urlPattern = WireMock.urlMatching(regex);
        } else {
            urlPattern = WireMock.urlEqualTo(request.getPath());
        }

        switch (method) {
            case "GET":
                builder = WireMock.get(urlPattern);
                break;
            case "POST":
                builder = WireMock.post(urlPattern);
                break;
            case "PUT":
                builder = WireMock.put(urlPattern);
                break;
            case "DELETE":
                builder = WireMock.delete(urlPattern);
                break;
            case "PATCH":
                builder = WireMock.patch(urlPattern);
                break;
            default:
                builder = WireMock.any(urlPattern);
        }

        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                builder = builder.withHeader(header.getKey(), WireMock.equalTo(header.getValue()));
            }
        }

        if (request.getQueryParams() != null) {
            for (Map.Entry<String, String> param : request.getQueryParams().entrySet()) {
                builder = builder.withQueryParam(param.getKey(), WireMock.equalTo(param.getValue()));
            }
        }

        if (request.getBodyPatterns() != null) {
            for (RequestMapping.BodyPattern pattern : request.getBodyPatterns()) {
                builder = addBodyPattern(builder, pattern);
            }
        }

        return builder;
    }

    private MappingBuilder addBodyPattern(MappingBuilder builder, RequestMapping.BodyPattern pattern) {
        if ("jsonPath".equals(pattern.getMatcher())) {
            return builder.withRequestBody(WireMock.matchingJsonPath(pattern.getExpr(), WireMock.equalTo(pattern.getExpected())));
        } else if ("regex".equals(pattern.getMatcher())) {
            return builder.withRequestBody(WireMock.matching(pattern.getExpr()));
        } else if ("equalTo".equals(pattern.getMatcher())) {
            return builder.withRequestBody(WireMock.equalTo(pattern.getExpected()));
        }
        return builder;
    }

    private ResponseDefinitionBuilder buildResponse(RequestMapping mapping) {
        RequestMapping.DelaySpec delaySpec = mapping.getDelays();
        
        if (delaySpec != null && delayService.shouldTriggerError(delaySpec) && delaySpec.getErrorResponse() != null) {
            return buildErrorResponse(delaySpec.getErrorResponse());
        }

        ResponseDefinitionBuilder builder = WireMock.aResponse()
            .withStatus(mapping.getResponse().getStatus());

        if (mapping.getResponse().getHeaders() != null) {
            for (Map.Entry<String, String> header : mapping.getResponse().getHeaders().entrySet()) {
                builder = builder.withHeader(header.getKey(), header.getValue());
            }
        }

        if (StringUtils.hasText(mapping.getResponse().getBody())) {
            if (Boolean.TRUE.equals(mapping.getResponse().getTemplatingEnabled())) {
                builder = builder.withBodyFile(null).withBody(mapping.getResponse().getBody()).withTransformers("response-template");
            } else {
                builder = builder.withBody(mapping.getResponse().getBody());
            }
        }

        // Add conditional response transformer if conditional responses are enabled
        if (isConditionalResponsesEnabled(mapping)) {
            try {
                String mappingJson = objectMapper.writeValueAsString(mapping);
                Map<String, Object> transformerParams = new HashMap<>();
                transformerParams.put("mapping", mappingJson);
                builder = builder.withTransformers("conditional-response");
                builder = builder.withTransformerParameters(transformerParams);
                logger.debug("Applied conditional response transformer for mapping: {}", mapping.getName());
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize mapping for conditional response transformer: {}", e.getMessage());
            }
        }

        if (delaySpec != null) {
            long delayMs = calculateDelayMs(delaySpec);
            if (delayMs > 0) {
                builder = builder.withFixedDelay((int) delayMs);
            }
        }

        return builder;
    }

    private ResponseDefinitionBuilder buildErrorResponse(RequestMapping.ErrorResponse errorResponse) {
        ResponseDefinitionBuilder builder = WireMock.aResponse()
            .withStatus(errorResponse.getStatus());
        
        if (StringUtils.hasText(errorResponse.getBody())) {
            builder = builder.withBody(errorResponse.getBody());
        }
        
        return builder.withHeader("Content-Type", "application/json");
    }

    private boolean isConditionalResponsesEnabled(RequestMapping mapping) {
        return mapping.getResponse() != null 
            && mapping.getResponse().getConditionalResponses() != null 
            && Boolean.TRUE.equals(mapping.getResponse().getConditionalResponses().getEnabled());
    }

    /**
     * Converts a MongoDB ObjectId to a deterministic UUID string
     * @param objectId The 24-character MongoDB ObjectId
     * @return A valid UUID string
     */
    private String convertObjectIdToUuid(String objectId) {
        // Pad or truncate the ObjectId to create a 32-character hex string
        String hex = objectId.length() >= 32 ? objectId.substring(0, 32) : 
                     String.format("%-32s", objectId).replace(' ', '0');
        
        // Format as UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        return String.format("%s-%s-%s-%s-%s",
            hex.substring(0, 8),
            hex.substring(8, 12),
            hex.substring(12, 16),
            hex.substring(16, 20),
            hex.substring(20, 32)
        );
    }

    private long calculateDelayMs(RequestMapping.DelaySpec delaySpec) {
        if ("variable".equals(delaySpec.getMode())) {
            int min = delaySpec.getVariableMinMs() != null ? delaySpec.getVariableMinMs() : 0;
            int max = delaySpec.getVariableMaxMs() != null ? delaySpec.getVariableMaxMs() : min;
            if (max <= min) {
                return min;
            }
            return min + (long) (Math.random() * (max - min));
        } else if ("fixed".equals(delaySpec.getMode())) {
            return delaySpec.getFixedMs() != null ? delaySpec.getFixedMs() : 0;
        }
        return 0;
    }
}