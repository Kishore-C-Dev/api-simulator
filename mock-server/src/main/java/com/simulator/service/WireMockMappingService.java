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
import com.simulator.model.EndpointType;
import com.simulator.service.GraphQLResponseGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WireMockMappingService {

    private static final Logger logger = LoggerFactory.getLogger(WireMockMappingService.class);
    private final WireMockServer wireMockServer;
    private final DelayService delayService;
    private final ObjectMapper objectMapper;
    private final GraphQLResponseGenerator graphQLResponseGenerator;

    @Autowired
    public WireMockMappingService(WireMockServer wireMockServer, DelayService delayService,
                                 GraphQLResponseGenerator graphQLResponseGenerator) {
        this.wireMockServer = wireMockServer;
        this.delayService = delayService;
        this.graphQLResponseGenerator = graphQLResponseGenerator;
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
        // Handle GraphQL endpoints differently
        if (EndpointType.GRAPHQL.equals(mapping.getEndpointType())) {
            createGraphQLStubMapping(mapping);
            return;
        }

        // Handle regular REST endpoints
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

    private void createGraphQLStubMapping(RequestMapping mapping) {
        logger.debug("Creating GraphQL stub mapping for: {}", mapping.getName());

        // Get GraphQL endpoint path from mapping or use default /graphql
        String graphqlPath = "/graphql"; // Default fallback
        if (mapping.getRequest() != null && mapping.getRequest().getPath() != null
                && !mapping.getRequest().getPath().trim().isEmpty()) {
            graphqlPath = mapping.getRequest().getPath();
        }

        // Create a POST request to the specified GraphQL path with JSON body pattern matching
        MappingBuilder requestBuilder = WireMock.post(WireMock.urlEqualTo(graphqlPath))
            .withHeader("Content-Type", WireMock.matching("application/json.*"));

        // Add GraphQL-specific body patterns if available
        if (mapping.getGraphQLSpec() != null) {
            addGraphQLBodyPatterns(requestBuilder, mapping.getGraphQLSpec());
        }

        // Create GraphQL response using the GraphQLResponseGenerator
        ResponseDefinitionBuilder responseBuilder = buildGraphQLResponse(mapping);

        StubMapping stubMapping = wireMockServer.stubFor(
            requestBuilder.willReturn(responseBuilder)
        );
        stubMapping.setPriority(mapping.getPriority());

        if (mapping.getId() != null) {
            try {
                stubMapping.setId(UUID.fromString(mapping.getId()));
            } catch (IllegalArgumentException e) {
                String uuidString = convertObjectIdToUuid(mapping.getId());
                stubMapping.setId(UUID.fromString(uuidString));
            }
        }

        logger.debug("Created GraphQL stub mapping: {} -> GraphQL Response", mapping.getName());
    }

    private void addGraphQLBodyPatterns(MappingBuilder builder, com.simulator.model.GraphQLSpec spec) {
        // Match GraphQL operation type and name if specified
        if (spec.getOperationType() != null) {
            String operationType = spec.getOperationType().name().toLowerCase();
            builder.withRequestBody(WireMock.matchingJsonPath("$.query", WireMock.containing(operationType)));
        }

        if (spec.getOperationName() != null && !spec.getOperationName().trim().isEmpty()) {
            // Match either explicit operationName field or operation name in query
            builder.withRequestBody(WireMock.or(
                WireMock.matchingJsonPath("$.operationName", WireMock.equalTo(spec.getOperationName())),
                WireMock.matchingJsonPath("$.query", WireMock.containing(spec.getOperationName()))
            ));
        }

        // Match query pattern if specified
        if (spec.getQuery() != null && !spec.getQuery().trim().isEmpty()) {
            // Use contains matching instead of regex to avoid issues with GraphQL syntax
            String query = spec.getQuery().trim();
            // For GraphQL queries, use a contains match instead of regex to avoid curly brace issues
            builder.withRequestBody(WireMock.matchingJsonPath("$.query", WireMock.containing(query)));
        }

        // Match variables if specified
        if (spec.getVariables() != null && !spec.getVariables().isEmpty()) {
            for (Map.Entry<String, Object> var : spec.getVariables().entrySet()) {
                String jsonPath = "$.variables." + var.getKey();
                if (var.getValue() != null) {
                    builder.withRequestBody(WireMock.matchingJsonPath(jsonPath, WireMock.equalTo(var.getValue().toString())));
                } else {
                    builder.withRequestBody(WireMock.matchingJsonPath(jsonPath));
                }
            }
        }
    }

    private ResponseDefinitionBuilder buildGraphQLResponse(RequestMapping mapping) {
        ResponseDefinitionBuilder builder = WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json");

        // Use a custom transformer that leverages the GraphQLResponseGenerator
        try {
            String mappingJson = objectMapper.writeValueAsString(mapping);
            Map<String, Object> transformerParams = new HashMap<>();
            transformerParams.put("mapping", mappingJson);
            builder = builder.withTransformers("graphql-response");
            builder = builder.withTransformerParameters(transformerParams);

            // Set a default GraphQL response body as fallback
            String fallbackResponse = "{\"data\": null, \"errors\": [{\"message\": \"GraphQL transformer not available\"}]}";
            if (mapping.getResponse() != null && mapping.getResponse().getGraphQLResponse() != null) {
                try {
                    // Create a basic response from the GraphQL data
                    Map<String, Object> response = new HashMap<>();
                    if (mapping.getResponse().getGraphQLResponse().getData() != null) {
                        response.put("data", mapping.getResponse().getGraphQLResponse().getData());
                    }
                    if (mapping.getResponse().getGraphQLResponse().getErrors() != null) {
                        response.put("errors", mapping.getResponse().getGraphQLResponse().getErrors());
                    }
                    if (mapping.getResponse().getGraphQLResponse().getExtensions() != null) {
                        response.put("extensions", mapping.getResponse().getGraphQLResponse().getExtensions());
                    }
                    fallbackResponse = objectMapper.writeValueAsString(response);
                } catch (Exception e) {
                    logger.warn("Failed to serialize GraphQL response: {}", e.getMessage());
                }
            }
            builder = builder.withBody(fallbackResponse);

            logger.debug("Applied GraphQL response transformer for mapping: {}", mapping.getName());
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize mapping for GraphQL response transformer: {}", e.getMessage());
        }

        // Add delay if configured
        if (mapping.getDelays() != null) {
            long delayMs = calculateDelayMs(mapping.getDelays());
            if (delayMs > 0) {
                builder = builder.withFixedDelay((int) delayMs);
            }
        }

        return builder;
    }

    private MappingBuilder buildRequestPattern(RequestMapping.RequestSpec request) {
        MappingBuilder builder;
        
        String method = request.getMethod().toUpperCase();
        UrlPattern urlPattern = buildUrlPattern(request);

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

        // Add header patterns (backward compatible)
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                builder = builder.withHeader(header.getKey(), WireMock.equalTo(header.getValue()));
            }
        }

        // Add advanced header patterns
        if (request.getHeaderPatterns() != null) {
            for (Map.Entry<String, RequestMapping.ParameterPattern> entry : request.getHeaderPatterns().entrySet()) {
                builder = addHeaderPattern(builder, entry.getKey(), entry.getValue());
            }
        }

        // Add query parameter patterns (backward compatible)
        if (request.getQueryParams() != null) {
            for (Map.Entry<String, String> param : request.getQueryParams().entrySet()) {
                builder = builder.withQueryParam(param.getKey(), WireMock.equalTo(param.getValue()));
            }
        }

        // Add advanced query parameter patterns
        if (request.getQueryParamPatterns() != null) {
            for (Map.Entry<String, RequestMapping.ParameterPattern> entry : request.getQueryParamPatterns().entrySet()) {
                builder = addQueryParamPattern(builder, entry.getKey(), entry.getValue());
            }
        }

        if (request.getBodyPatterns() != null) {
            for (RequestMapping.BodyPattern pattern : request.getBodyPatterns()) {
                builder = addBodyPattern(builder, pattern);
            }
        }

        return builder;
    }

    private UrlPattern buildUrlPattern(RequestMapping.RequestSpec request) {
        // Check if advanced path pattern is specified
        if (request.getPathPattern() != null) {
            return buildAdvancedUrlPattern(request.getPathPattern());
        }

        // Fallback to legacy path handling
        if (request.getPath() != null) {
            if (request.getPath().contains("{") || request.getPath().contains("*")) {
                String regex = request.getPath()
                    .replaceAll("\\{[^}]*\\}", "[^/]+")
                    .replaceAll("\\*", ".*");
                return WireMock.urlMatching(regex);
            } else {
                return WireMock.urlEqualTo(request.getPath());
            }
        }

        return WireMock.urlMatching(".*"); // Match all if no path specified
    }

    private UrlPattern buildAdvancedUrlPattern(RequestMapping.PathPattern pathPattern) {
        String pattern = pathPattern.getPattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            return WireMock.urlMatching(".*");
        }

        switch (pathPattern.getMatchType()) {
            case EXACT:
                return WireMock.urlEqualTo(pattern);
                
            case WILDCARD:
                String regexPattern = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".?");
                return pathPattern.isIgnoreCase() ? 
                    WireMock.urlMatching("(?i)" + regexPattern) : 
                    WireMock.urlMatching(regexPattern);
                    
            case REGEX:
                return pathPattern.isIgnoreCase() ? 
                    WireMock.urlMatching("(?i)" + pattern) : 
                    WireMock.urlMatching(pattern);
                    
            default:
                return WireMock.urlEqualTo(pattern);
        }
    }

    private MappingBuilder addHeaderPattern(MappingBuilder builder, String headerName, RequestMapping.ParameterPattern pattern) {
        switch (pattern.getMatchType()) {
            case EXISTS:
                return builder.withHeader(headerName, WireMock.matching(".*"));
                
            case EXACT:
                return pattern.isIgnoreCase() ?
                    builder.withHeader(headerName, WireMock.equalToIgnoreCase(pattern.getPattern())) :
                    builder.withHeader(headerName, WireMock.equalTo(pattern.getPattern()));
                    
            case CONTAINS:
                return pattern.isIgnoreCase() ?
                    builder.withHeader(headerName, WireMock.matching("(?i).*" + Pattern.quote(pattern.getPattern()) + ".*")) :
                    builder.withHeader(headerName, WireMock.containing(pattern.getPattern()));
                    
            case REGEX:
                String regexPattern = pattern.isIgnoreCase() ? 
                    "(?i)" + pattern.getPattern() : pattern.getPattern();
                return builder.withHeader(headerName, WireMock.matching(regexPattern));
                
            default:
                return builder.withHeader(headerName, WireMock.equalTo(pattern.getPattern()));
        }
    }

    private MappingBuilder addQueryParamPattern(MappingBuilder builder, String paramName, RequestMapping.ParameterPattern pattern) {
        switch (pattern.getMatchType()) {
            case EXISTS:
                return builder.withQueryParam(paramName, WireMock.matching(".*"));
                
            case EXACT:
                return pattern.isIgnoreCase() ?
                    builder.withQueryParam(paramName, WireMock.equalToIgnoreCase(pattern.getPattern())) :
                    builder.withQueryParam(paramName, WireMock.equalTo(pattern.getPattern()));
                    
            case CONTAINS:
                return pattern.isIgnoreCase() ?
                    builder.withQueryParam(paramName, WireMock.matching("(?i).*" + Pattern.quote(pattern.getPattern()) + ".*")) :
                    builder.withQueryParam(paramName, WireMock.containing(pattern.getPattern()));
                    
            case REGEX:
                String regexPattern = pattern.isIgnoreCase() ? 
                    "(?i)" + pattern.getPattern() : pattern.getPattern();
                return builder.withQueryParam(paramName, WireMock.matching(regexPattern));
                
            default:
                return builder.withQueryParam(paramName, WireMock.equalTo(pattern.getPattern()));
        }
    }

    private MappingBuilder addBodyPattern(MappingBuilder builder, RequestMapping.BodyPattern pattern) {
        // Support both old matcher string and new MatchType enum
        RequestMapping.BodyPattern.MatchType matchType = pattern.getMatchType();
        
        // Backward compatibility: if matchType is null, try to infer from matcher string
        if (matchType == null && pattern.getMatcher() != null) {
            switch (pattern.getMatcher().toLowerCase()) {
                case "jsonpath":
                    matchType = RequestMapping.BodyPattern.MatchType.JSONPATH;
                    break;
                case "regex":
                    matchType = RequestMapping.BodyPattern.MatchType.REGEX;
                    break;
                case "equalto":
                case "exact":
                    matchType = RequestMapping.BodyPattern.MatchType.EXACT;
                    break;
                case "contains":
                    matchType = RequestMapping.BodyPattern.MatchType.CONTAINS;
                    break;
                default:
                    matchType = RequestMapping.BodyPattern.MatchType.EXACT;
            }
        }
        
        if (matchType == null) {
            return builder;
        }

        switch (matchType) {
            case JSONPATH:
                if (pattern.getExpected() != null && !pattern.getExpected().isEmpty()) {
                    return builder.withRequestBody(WireMock.matchingJsonPath(pattern.getExpr(), WireMock.equalTo(pattern.getExpected())));
                } else {
                    return builder.withRequestBody(WireMock.matchingJsonPath(pattern.getExpr()));
                }
                
            case REGEX:
                String regexPattern = pattern.isIgnoreCase() ? 
                    "(?i)" + pattern.getExpected() : pattern.getExpected();
                return builder.withRequestBody(WireMock.matching(regexPattern));
                
            case EXACT:
                return pattern.isIgnoreCase() ?
                    builder.withRequestBody(WireMock.equalToIgnoreCase(pattern.getExpected())) :
                    builder.withRequestBody(WireMock.equalTo(pattern.getExpected()));
                    
            case CONTAINS:
                return pattern.isIgnoreCase() ?
                    builder.withRequestBody(WireMock.matching("(?i).*" + Pattern.quote(pattern.getExpected()) + ".*")) :
                    builder.withRequestBody(WireMock.containing(pattern.getExpected()));
                    
            case XPATH:
                // WireMock supports XPath matching
                if (pattern.getExpected() != null && !pattern.getExpected().isEmpty()) {
                    return builder.withRequestBody(WireMock.matchingXPath(pattern.getExpr(), WireMock.equalTo(pattern.getExpected())));
                } else {
                    return builder.withRequestBody(WireMock.matchingXPath(pattern.getExpr()));
                }
                
            default:
                return builder;
        }
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