package com.simulator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.simulator.model.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GraphQLResponseGenerator {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLResponseGenerator.class);
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private final ObjectMapper objectMapper;
    private final DelayService delayService;

    @Autowired
    public GraphQLResponseGenerator(ObjectMapper objectMapper, DelayService delayService) {
        this.objectMapper = objectMapper;
        this.delayService = delayService;
    }

    public String generateGraphQLResponse(RequestMapping mapping, GraphQLParserService.GraphQLRequest request) {
        logger.debug("Generating GraphQL response for mapping: {}", mapping.getName());

        try {
            // Check if error should be triggered based on error rate
            if (mapping.getDelays() != null && delayService.shouldTriggerError(mapping.getDelays())) {
                logger.debug("Triggering error response for mapping: {} due to error rate", mapping.getName());
                return generateErrorResponseFromDelaySpec(mapping.getDelays());
            }

            // Create context for template processing
            Map<String, Object> templateContext = createTemplateContext(request, mapping);

            // Build the GraphQL response object
            ObjectNode response = objectMapper.createObjectNode();

            // Add data field
            if (mapping.getResponse() != null && mapping.getResponse().getGraphQLResponse() != null) {
                var graphqlResponse = mapping.getResponse().getGraphQLResponse();

                // Process data field
                if (graphqlResponse.getData() != null) {
                    Object processedData = processTemplate(graphqlResponse.getData(), templateContext, mapping);
                    if (processedData instanceof String) {
                        try {
                            JsonNode dataNode = objectMapper.readTree((String) processedData);
                            response.set("data", dataNode);
                        } catch (JsonProcessingException e) {
                            // If not valid JSON, treat as string value
                            response.put("data", (String) processedData);
                        }
                    } else {
                        JsonNode dataNode = objectMapper.valueToTree(processedData);
                        response.set("data", dataNode);
                    }
                } else {
                    response.putNull("data");
                }

                // Add errors field if present
                if (graphqlResponse.getErrors() != null && !graphqlResponse.getErrors().isEmpty()) {
                    List<RequestMapping.GraphQLResponseSpec.GraphQLError> processedErrors =
                        processErrorsTemplate(graphqlResponse.getErrors(), templateContext, mapping);
                    JsonNode errorsNode = objectMapper.valueToTree(processedErrors);
                    response.set("errors", errorsNode);
                }

                // Add extensions field if present
                if (graphqlResponse.getExtensions() != null && !graphqlResponse.getExtensions().isEmpty()) {
                    Map<String, Object> processedExtensions =
                        processExtensionsTemplate(graphqlResponse.getExtensions(), templateContext, mapping);
                    JsonNode extensionsNode = objectMapper.valueToTree(processedExtensions);
                    response.set("extensions", extensionsNode);
                }
            } else if (mapping.getResponse() != null && mapping.getResponse().getBody() != null) {
                // Fallback: use regular response body if GraphQL response not configured
                logger.debug("Using response body as GraphQL response template for mapping: {}", mapping.getName());
                String responseBody = mapping.getResponse().getBody();
                String processedResponse = (String) processTemplate(responseBody, templateContext, mapping);

                try {
                    JsonNode responseNode = objectMapper.readTree(processedResponse);
                    // If the response body is already a full GraphQL response, use it directly
                    if (responseNode.has("data")) {
                        return processedResponse;
                    } else {
                        // Otherwise, wrap it in a data field
                        response.set("data", responseNode);
                    }
                } catch (JsonProcessingException e) {
                    logger.error("Error parsing response body as JSON: {}", e.getMessage());
                    response.putNull("data");
                }
            } else {
                // No response configuration found
                response.putNull("data");
                logger.warn("No GraphQL response configuration found for mapping: {}", mapping.getName());
            }

            String responseJson = objectMapper.writeValueAsString(response);
            logger.debug("Generated GraphQL response: {}", responseJson);

            return responseJson;

        } catch (Exception e) {
            logger.error("Error generating GraphQL response for mapping {}: {}", mapping.getName(), e.getMessage(), e);
            return generateErrorResponse("Internal Server Error", "Failed to generate response");
        }
    }

    private Map<String, Object> createTemplateContext(GraphQLParserService.GraphQLRequest request, RequestMapping mapping) {
        Map<String, Object> context = new HashMap<>();

        // Add GraphQL request context
        context.put("query", request.getQuery());
        context.put("operationType", request.getOperationType().name());
        context.put("operationName", request.getOperationName());
        context.put("variables", request.getVariables());

        // Add mapping context
        context.put("mappingName", mapping.getName());
        context.put("mappingId", mapping.getId());

        // Add common template helpers
        context.put("timestamp", System.currentTimeMillis());
        context.put("iso8601", java.time.Instant.now().toString());
        context.put("uuid", java.util.UUID.randomUUID().toString());

        return context;
    }

    private Object processTemplate(Object template, Map<String, Object> context, RequestMapping mapping) {
        if (template == null) {
            return null;
        }

        // Check if templating is enabled
        boolean templatingEnabled = mapping.getResponse() != null
            && Boolean.TRUE.equals(mapping.getResponse().getTemplatingEnabled());

        if (!templatingEnabled) {
            return template;
        }

        try {
            if (template instanceof String) {
                return processStringTemplate((String) template, context);
            } else if (template instanceof Map) {
                Map<String, Object> templateMap = (Map<String, Object>) template;
                Map<String, Object> processedMap = new HashMap<>();
                for (Map.Entry<String, Object> entry : templateMap.entrySet()) {
                    processedMap.put(entry.getKey(), processTemplate(entry.getValue(), context, mapping));
                }
                return processedMap;
            } else if (template instanceof List) {
                List<Object> templateList = (List<Object>) template;
                return templateList.stream()
                    .map(item -> processTemplate(item, context, mapping))
                    .toList();
            } else {
                return template;
            }
        } catch (Exception e) {
            logger.error("Error processing template: {}", e.getMessage(), e);
            return template; // Return original if template processing fails
        }
    }

    private String processStringTemplate(String template, Map<String, Object> context) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(0); // {{variable.name}}
            String variablePath = matcher.group(1).trim(); // variable.name

            Object value = getValueFromContext(variablePath, context);
            String replacement = value != null ? String.valueOf(value) : "";

            result = result.replace(placeholder, replacement);
        }

        return result;
    }

    private Object getValueFromContext(String path, Map<String, Object> context) {
        String[] parts = path.split("\\.");
        Object current = context;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null; // Path not found
            }
        }

        return current;
    }

    private List<RequestMapping.GraphQLResponseSpec.GraphQLError> processErrorsTemplate(
            List<RequestMapping.GraphQLResponseSpec.GraphQLError> errors,
            Map<String, Object> context, RequestMapping mapping) {

        return errors.stream().map(error -> {
            RequestMapping.GraphQLResponseSpec.GraphQLError processedError =
                new RequestMapping.GraphQLResponseSpec.GraphQLError();

            processedError.setMessage((String) processTemplate(error.getMessage(), context, mapping));
            processedError.setPath(error.getPath());
            processedError.setLocations(error.getLocations());

            if (error.getExtensions() != null) {
                Map<String, Object> processedExtensions =
                    (Map<String, Object>) processTemplate(error.getExtensions(), context, mapping);
                processedError.setExtensions(processedExtensions);
            }

            return processedError;
        }).toList();
    }

    private Map<String, Object> processExtensionsTemplate(Map<String, Object> extensions,
            Map<String, Object> context, RequestMapping mapping) {

        Map<String, Object> processedExtensions = new HashMap<>();
        for (Map.Entry<String, Object> entry : extensions.entrySet()) {
            processedExtensions.put(entry.getKey(), processTemplate(entry.getValue(), context, mapping));
        }
        return processedExtensions;
    }

    public String generateErrorResponse(String message, String code) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.putNull("data");

            ObjectNode error = objectMapper.createObjectNode();
            error.put("message", message);
            if (code != null) {
                ObjectNode extensions = objectMapper.createObjectNode();
                extensions.put("code", code);
                error.set("extensions", extensions);
            }

            response.set("errors", objectMapper.createArrayNode().add(error));

            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            logger.error("Failed to generate error response", e);
            return "{\"data\":null,\"errors\":[{\"message\":\"Internal Server Error\"}]}";
        }
    }

    public boolean isValidGraphQLResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }

        try {
            JsonNode responseNode = objectMapper.readTree(response);

            // A valid GraphQL response must have at least a 'data' field
            return responseNode.has("data");

        } catch (JsonProcessingException e) {
            logger.debug("Invalid JSON in GraphQL response: {}", e.getMessage());
            return false;
        }
    }

    public String normalizeGraphQLResponse(String response) {
        try {
            JsonNode responseNode = objectMapper.readTree(response);

            // Ensure required fields exist
            if (!responseNode.has("data")) {
                ((ObjectNode) responseNode).putNull("data");
            }

            return objectMapper.writeValueAsString(responseNode);
        } catch (JsonProcessingException e) {
            logger.error("Failed to normalize GraphQL response: {}", e.getMessage());
            return generateErrorResponse("Invalid Response Format", "INVALID_RESPONSE");
        }
    }

    private String generateErrorResponseFromDelaySpec(RequestMapping.DelaySpec delaySpec) {
        try {
            if (delaySpec.getErrorResponse() != null && delaySpec.getErrorResponse().getBody() != null) {
                // Use the configured error response body
                String errorBody = delaySpec.getErrorResponse().getBody();

                // Check if it's already a valid GraphQL response
                if (isValidGraphQLResponse(errorBody)) {
                    return errorBody;
                }

                // If not, wrap it in a GraphQL response structure
                ObjectNode response = objectMapper.createObjectNode();
                response.putNull("data");

                try {
                    JsonNode errorBodyNode = objectMapper.readTree(errorBody);
                    if (errorBodyNode.has("errors")) {
                        response.set("errors", errorBodyNode.get("errors"));
                    } else {
                        // Create a generic error with the body as message
                        ObjectNode error = objectMapper.createObjectNode();
                        error.put("message", errorBody);
                        if (delaySpec.getErrorResponse().getStatus() != null) {
                            ObjectNode extensions = objectMapper.createObjectNode();
                            extensions.put("code", "HTTP_" + delaySpec.getErrorResponse().getStatus());
                            error.set("extensions", extensions);
                        }
                        response.set("errors", objectMapper.createArrayNode().add(error));
                    }
                } catch (JsonProcessingException e) {
                    // If error body is not JSON, create a simple error message
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("message", errorBody);
                    if (delaySpec.getErrorResponse().getStatus() != null) {
                        ObjectNode extensions = objectMapper.createObjectNode();
                        extensions.put("code", "HTTP_" + delaySpec.getErrorResponse().getStatus());
                        error.set("extensions", extensions);
                    }
                    response.set("errors", objectMapper.createArrayNode().add(error));
                }

                return objectMapper.writeValueAsString(response);
            } else {
                // Generate a default error response
                String message = "Service temporarily unavailable";
                String code = "SERVICE_UNAVAILABLE";
                if (delaySpec.getErrorResponse() != null && delaySpec.getErrorResponse().getStatus() != null) {
                    code = "HTTP_" + delaySpec.getErrorResponse().getStatus();
                }
                return generateErrorResponse(message, code);
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to generate error response from delay spec: {}", e.getMessage());
            return generateErrorResponse("Internal Server Error", "INTERNAL_ERROR");
        }
    }
}