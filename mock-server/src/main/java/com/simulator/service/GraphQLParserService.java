package com.simulator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulator.model.GraphQLSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GraphQLParserService {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLParserService.class);
    private final ObjectMapper objectMapper;

    // Pattern to extract operation type and name from GraphQL queries
    private static final Pattern OPERATION_PATTERN = Pattern.compile(
        "^\\s*(query|mutation|subscription)\\s*([a-zA-Z_][a-zA-Z0-9_]*)?",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    public GraphQLParserService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GraphQLRequest parseGraphQLRequest(String requestBody) {
        logger.debug("Parsing GraphQL request: {}", requestBody);

        try {
            JsonNode rootNode = objectMapper.readTree(requestBody);

            String query = rootNode.path("query").asText();
            String operationName = rootNode.has("operationName") && !rootNode.path("operationName").isNull()
                ? rootNode.path("operationName").asText() : null;

            Map<String, Object> variables = new HashMap<>();
            if (rootNode.has("variables") && !rootNode.path("variables").isNull()) {
                JsonNode variablesNode = rootNode.path("variables");
                variables = objectMapper.convertValue(variablesNode, Map.class);
            }

            // Extract operation type from query if not provided in operationName
            GraphQLSpec.OperationType operationType = extractOperationType(query);

            // If operationName is not provided, try to extract it from query
            if (operationName == null) {
                operationName = extractOperationName(query);
            }

            return new GraphQLRequest(query, operationType, operationName, variables);

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse GraphQL request: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid GraphQL request format", e);
        } catch (Exception e) {
            logger.error("Unexpected error parsing GraphQL request: {}", e.getMessage());
            throw new RuntimeException("Failed to parse GraphQL request", e);
        }
    }

    public boolean matchesGraphQLSpec(GraphQLRequest request, GraphQLSpec spec) {
        logger.debug("Matching GraphQL request against spec");

        // Check operation type
        if (spec.getOperationType() != null && !spec.getOperationType().equals(request.getOperationType())) {
            logger.debug("Operation type mismatch: expected {}, got {}",
                spec.getOperationType(), request.getOperationType());
            return false;
        }

        // Check operation name if specified
        if (spec.getOperationName() != null && !spec.getOperationName().trim().isEmpty()) {
            if (request.getOperationName() == null ||
                !spec.getOperationName().equals(request.getOperationName())) {
                logger.debug("Operation name mismatch: expected {}, got {}",
                    spec.getOperationName(), request.getOperationName());
                return false;
            }
        }

        // Check query pattern if specified
        if (spec.getQuery() != null && !spec.getQuery().trim().isEmpty()) {
            if (!matchesQuery(request.getQuery(), spec.getQuery(), spec.getQueryPattern())) {
                logger.debug("Query pattern mismatch");
                return false;
            }
        }

        // Check variables pattern if specified
        if (spec.getVariables() != null && !spec.getVariables().isEmpty()) {
            if (!matchesVariables(request.getVariables(), spec.getVariables())) {
                logger.debug("Variables pattern mismatch");
                return false;
            }
        }

        logger.debug("GraphQL request matches spec");
        return true;
    }

    private GraphQLSpec.OperationType extractOperationType(String query) {
        if (query == null || query.trim().isEmpty()) {
            return GraphQLSpec.OperationType.QUERY; // Default to query
        }

        Matcher matcher = OPERATION_PATTERN.matcher(query.trim());
        if (matcher.find()) {
            String operationType = matcher.group(1).toLowerCase();
            switch (operationType) {
                case "mutation":
                    return GraphQLSpec.OperationType.MUTATION;
                case "subscription":
                    return GraphQLSpec.OperationType.SUBSCRIPTION;
                default:
                    return GraphQLSpec.OperationType.QUERY;
            }
        }

        return GraphQLSpec.OperationType.QUERY; // Default if no explicit operation type
    }

    private String extractOperationName(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = OPERATION_PATTERN.matcher(query.trim());
        if (matcher.find()) {
            return matcher.group(2); // Operation name group
        }

        return null;
    }

    private boolean matchesQuery(String requestQuery, String specQuery, GraphQLSpec.QueryPattern queryPattern) {
        if (specQuery == null || specQuery.trim().isEmpty()) {
            return true; // No pattern to match
        }

        String normalizedRequestQuery = normalizeQuery(requestQuery);
        String normalizedSpecQuery = normalizeQuery(specQuery);

        if (queryPattern != null && queryPattern.getMatchType() != null) {
            switch (queryPattern.getMatchType()) {
                case EXACT:
                    return normalizedSpecQuery.equals(normalizedRequestQuery);
                case CONTAINS:
                    return normalizedRequestQuery.contains(normalizedSpecQuery);
                case AST_MATCH:
                    // For now, fallback to contains matching
                    // TODO: Implement proper AST matching in future
                    return normalizedRequestQuery.contains(normalizedSpecQuery);
                default:
                    return normalizedSpecQuery.equals(normalizedRequestQuery);
            }
        }

        // Default to exact match
        return normalizedSpecQuery.equals(normalizedRequestQuery);
    }

    private boolean matchesVariables(Map<String, Object> requestVariables, Map<String, Object> specVariables) {
        if (specVariables == null || specVariables.isEmpty()) {
            return true; // No variables pattern to match
        }

        if (requestVariables == null || requestVariables.isEmpty()) {
            return false; // Spec expects variables but request has none
        }

        // Check if all spec variables are present in request variables
        for (Map.Entry<String, Object> specEntry : specVariables.entrySet()) {
            String key = specEntry.getKey();
            Object expectedValue = specEntry.getValue();

            if (!requestVariables.containsKey(key)) {
                return false; // Required variable missing
            }

            Object actualValue = requestVariables.get(key);
            if (expectedValue != null && !expectedValue.equals(actualValue)) {
                // For now, just check equality. Could add pattern matching later
                return false;
            }
        }

        return true;
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }

        // Remove extra whitespace, normalize line endings
        return query.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("\\r\\n|\\r|\\n", " ");
    }

    public static class GraphQLRequest {
        private final String query;
        private final GraphQLSpec.OperationType operationType;
        private final String operationName;
        private final Map<String, Object> variables;

        public GraphQLRequest(String query, GraphQLSpec.OperationType operationType,
                            String operationName, Map<String, Object> variables) {
            this.query = query;
            this.operationType = operationType;
            this.operationName = operationName;
            this.variables = variables != null ? variables : new HashMap<>();
        }

        public String getQuery() { return query; }
        public GraphQLSpec.OperationType getOperationType() { return operationType; }
        public String getOperationName() { return operationName; }
        public Map<String, Object> getVariables() { return variables; }

        @Override
        public String toString() {
            return String.format("GraphQLRequest{operationType=%s, operationName='%s', query='%s', variables=%s}",
                operationType, operationName, query, variables);
        }
    }
}