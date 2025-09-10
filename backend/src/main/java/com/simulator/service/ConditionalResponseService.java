package com.simulator.service;

import com.simulator.model.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ConditionalResponseService {

    private static final Logger logger = LoggerFactory.getLogger(ConditionalResponseService.class);

    public static class ConditionalResponse {
        private final Integer status;
        private final String body;
        private final Map<String, String> headers;
        private final boolean isConditional;

        public ConditionalResponse(Integer status, String body, Map<String, String> headers, boolean isConditional) {
            this.status = status;
            this.body = body;
            this.headers = headers;
            this.isConditional = isConditional;
        }

        public Integer getStatus() { return status; }
        public String getBody() { return body; }
        public Map<String, String> getHeaders() { return headers; }
        public boolean isConditional() { return isConditional; }
    }

    /**
     * Determines the response based on conditional logic
     * @param mapping The request mapping configuration
     * @param requestHeaders Headers from the incoming request
     * @return ConditionalResponse with appropriate status, body, and headers
     */
    public ConditionalResponse determineResponse(RequestMapping mapping, Map<String, String> requestHeaders) {
        RequestMapping.ResponseSpec response = mapping.getResponse();
        RequestMapping.ConditionalResponses conditionalResponses = response.getConditionalResponses();

        // If conditional responses are not enabled, return default response
        if (conditionalResponses == null || !Boolean.TRUE.equals(conditionalResponses.getEnabled())) {
            logger.debug("Conditional responses not enabled for mapping: {}", mapping.getName());
            return new ConditionalResponse(response.getStatus(), response.getBody(), response.getHeaders(), false);
        }

        String requestIdHeader = conditionalResponses.getRequestIdHeader();
        if (requestIdHeader == null || requestIdHeader.trim().isEmpty()) {
            logger.warn("Conditional responses enabled but no request ID header configured for mapping: {}", mapping.getName());
            return new ConditionalResponse(response.getStatus(), response.getBody(), response.getHeaders(), false);
        }

        // Look for the request ID in the headers
        String requestId = null;
        for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
            if (requestIdHeader.equalsIgnoreCase(header.getKey())) {
                requestId = header.getValue();
                break;
            }
        }

        if (requestId == null) {
            logger.debug("Request ID header '{}' not found in request headers, using default response", requestIdHeader);
            return new ConditionalResponse(response.getStatus(), response.getBody(), response.getHeaders(), false);
        }

        logger.info("Found request ID '{}' in header '{}' for mapping '{}'", requestId, requestIdHeader, mapping.getName());

        // Find matching request ID mapping
        if (conditionalResponses.getRequestIdMappings() != null) {
            for (RequestMapping.RequestIdMapping idMapping : conditionalResponses.getRequestIdMappings()) {
                if (requestId.equals(idMapping.getRequestId())) {
                    logger.info("Using conditional response for request ID '{}': status={}, body='{}'", 
                              requestId, idMapping.getStatus(), idMapping.getBody());
                    
                    // Merge headers - conditional response headers override default ones
                    Map<String, String> mergedHeaders = response.getHeaders() != null ? 
                        Map.copyOf(response.getHeaders()) : Map.of();
                    if (idMapping.getHeaders() != null) {
                        mergedHeaders = Map.copyOf(response.getHeaders() != null ? response.getHeaders() : Map.of());
                        mergedHeaders = new java.util.HashMap<>(mergedHeaders);
                        mergedHeaders.putAll(idMapping.getHeaders());
                    }
                    
                    return new ConditionalResponse(
                        idMapping.getStatus() != null ? idMapping.getStatus() : response.getStatus(),
                        idMapping.getBody() != null ? idMapping.getBody() : response.getBody(),
                        mergedHeaders,
                        true
                    );
                }
            }
        }

        logger.debug("No matching request ID mapping found for '{}', using default response", requestId);
        return new ConditionalResponse(response.getStatus(), response.getBody(), response.getHeaders(), false);
    }

    /**
     * Creates default conditional mappings for common scenarios
     */
    public RequestMapping.ConditionalResponses createDefaultConditionalResponses() {
        RequestMapping.ConditionalResponses conditionalResponses = new RequestMapping.ConditionalResponses();
        conditionalResponses.setEnabled(true);
        conditionalResponses.setRequestIdHeader("X-Request-ID");
        
        // Create default mappings for success, client error, and server error
        java.util.List<RequestMapping.RequestIdMapping> mappings = new java.util.ArrayList<>();
        
        // Success response
        RequestMapping.RequestIdMapping successMapping = new RequestMapping.RequestIdMapping();
        successMapping.setRequestId("success-200");
        successMapping.setStatus(200);
        successMapping.setBody("{\"status\":\"success\",\"message\":\"Request processed successfully\",\"requestId\":\"success-200\"}");
        mappings.add(successMapping);
        
        // Client error response
        RequestMapping.RequestIdMapping clientErrorMapping = new RequestMapping.RequestIdMapping();
        clientErrorMapping.setRequestId("error-400");
        clientErrorMapping.setStatus(400);
        clientErrorMapping.setBody("{\"status\":\"error\",\"message\":\"Bad request - invalid parameters\",\"requestId\":\"error-400\",\"errorCode\":\"INVALID_PARAMETERS\"}");
        mappings.add(clientErrorMapping);
        
        // Server error response
        RequestMapping.RequestIdMapping serverErrorMapping = new RequestMapping.RequestIdMapping();
        serverErrorMapping.setRequestId("error-500");
        serverErrorMapping.setStatus(500);
        serverErrorMapping.setBody("{\"status\":\"error\",\"message\":\"Internal server error - service unavailable\",\"requestId\":\"error-500\",\"errorCode\":\"SERVICE_UNAVAILABLE\"}");
        mappings.add(serverErrorMapping);
        
        conditionalResponses.setRequestIdMappings(mappings);
        return conditionalResponses;
    }
}