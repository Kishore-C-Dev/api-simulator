package com.simulator.ai.handler;

import com.simulator.ai.model.AIRequest;
import com.simulator.ai.model.AIResponse;
import com.simulator.model.RequestMapping;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unified handler for querying/listing/explaining endpoints
 * Intelligently detects if user wants specific or all endpoints
 */
@Component
public class QueryMappingHandler extends AbstractTaskHandler {

    @Autowired
    private TaskHandlerRegistry registry;

    @Autowired
    private OpenAiService openAiService;

    @Value("${simulator.ai.model}")
    private String model;

    @PostConstruct
    public void registerSelf() {
        registry.registerHandler(this);
    }

    @Override
    public int getPriority() {
        return 10; // Higher priority for query operations
    }

    @Override
    public AIRequest.AITaskType[] getSupportedTaskTypes() {
        return new AIRequest.AITaskType[]{
            AIRequest.AITaskType.LIST_MAPPINGS,
            AIRequest.AITaskType.EXPLAIN_MAPPING
        };
    }

    @Override
    public boolean canHandle(AIRequest request, List<RequestMapping> allMappings) {
        // Support LIST_MAPPINGS and EXPLAIN_MAPPING
        if (!super.canHandle(request, allMappings)) {
            return false;
        }

        return true;
    }

    @Override
    public AIResponse handle(AIRequest request, List<RequestMapping> allMappings) {
        logger.info("QueryMappingHandler processing request: '{}'", request.getUserPrompt());
        logger.info("Total mappings available: {}", allMappings != null ? allMappings.size() : 0);

        if (allMappings != null && !allMappings.isEmpty()) {
            logger.info("Available endpoint names: {}",
                allMappings.stream().map(RequestMapping::getName).toList());
        }

        String lowerPrompt = request.getUserPrompt().toLowerCase();

        // Check if user is asking for all endpoints in workspace
        // Patterns: "list all endpoints", "list default namespace endpoints", "show workspace endpoints"
        if (isAskingForAllEndpoints(lowerPrompt)) {
            logger.info("✓ User is asking for ALL endpoints in workspace");
            return handleAllEndpointsQuery(request, allMappings);
        }

        // Check if user wants all endpoints that match a pattern (e.g., "list memo endpoints")
        List<RequestMapping> matchingEndpoints = identifyAllMatchingEndpoints(request.getUserPrompt(), allMappings);

        if (matchingEndpoints.size() == 1) {
            // User is asking about a SPECIFIC endpoint
            logger.info("✓ User is querying SPECIFIC endpoint: {}", matchingEndpoints.get(0).getName());
            return handleSpecificEndpointQuery(request, matchingEndpoints.get(0));
        } else if (matchingEndpoints.size() > 1) {
            // User is asking about MULTIPLE endpoints with same keyword
            logger.info("✓ User is querying {} endpoints matching pattern", matchingEndpoints.size());
            return handleMultipleEndpointsQuery(request, matchingEndpoints);
        } else {
            // User is asking about ALL endpoints
            logger.info("✗ No specific endpoint identified - showing ALL endpoints");
            return handleAllEndpointsQuery(request, allMappings);
        }
    }

    /**
     * Check if user is explicitly asking for all endpoints in workspace
     */
    private boolean isAskingForAllEndpoints(String lowerPrompt) {
        String[] allEndpointPatterns = {
            "all endpoints",
            "all mappings",
            "workspace endpoints",
            "namespace endpoints",
            "default namespace",
            "demo namespace",
            "current workspace",
            "this workspace"
        };

        for (String pattern : allEndpointPatterns) {
            if (lowerPrompt.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find all endpoints matching the user's query
     * Uses AI to intelligently filter endpoints based on full query criteria
     */
    private List<RequestMapping> identifyAllMatchingEndpoints(String userPrompt, List<RequestMapping> allMappings) {
        if (allMappings == null || allMappings.isEmpty()) {
            return List.of();
        }

        // Use AI to intelligently filter endpoints based on the full query
        return filterEndpointsUsingAI(userPrompt, allMappings);
    }

    /**
     * Use AI to intelligently filter endpoints based on query criteria
     * Considers name, status code, response type, and other attributes
     */
    private List<RequestMapping> filterEndpointsUsingAI(String userPrompt, List<RequestMapping> allMappings) {
        try {
            // Build endpoint summary for AI
            StringBuilder endpointsSummary = new StringBuilder();
            for (int i = 0; i < allMappings.size(); i++) {
                RequestMapping m = allMappings.get(i);
                endpointsSummary.append(String.format("%d. Name: \"%s\", Method: %s, Path: %s, Status: %d, Enabled: %s\n",
                    i,
                    m.getName(),
                    m.getRequest() != null ? m.getRequest().getMethod() : "?",
                    m.getRequest() != null ? m.getRequest().getPath() : "?",
                    m.getResponse() != null && m.getResponse().getStatus() != null ? m.getResponse().getStatus() : 200,
                    m.getEnabled()));
            }

            String systemPrompt = """
                You are filtering API endpoints. Return ONLY matching endpoint indexes as comma-separated numbers.

                Available endpoints:
                %s

                User query: "%s"

                Filtering rules:
                - Name/keyword filter (e.g., "memo", "account"): name contains that word
                - Status code filter (e.g., "200", "success", "error"):
                  * "success" = status 200-299
                  * "error" = status 400-599
                  * Specific number = exact match
                - Enabled filter: "enabled"/"active" = true, "disabled" = false
                - Apply ALL criteria (AND logic)

                RESPONSE FORMAT (CRITICAL):
                - Return ONLY comma-separated indexes: "1,2,3" or "1"
                - NO explanations, NO text, ONLY numbers
                - If no match: "NONE"

                Examples:
                Query: "list memo endpoints" → Answer: "1,2,3"
                Query: "which memo endpoint returns success with status 200" → Answer: "2"
                Query: "show error endpoints" → Answer: "0,4"

                Now return ONLY the matching indexes for the user query above.
                """.formatted(endpointsSummary.toString(), userPrompt);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), "Indexes only:"));

            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.1) // Very low temperature for precise, deterministic filtering
                .maxTokens(30) // Short response - just indexes
                .build();

            String aiResponse = openAiService.createChatCompletion(chatRequest)
                .getChoices().get(0).getMessage().getContent().trim();

            logger.info("AI endpoint filtering: '{}' → Indexes: '{}'", userPrompt, aiResponse);

            // Clean up response - extract just numbers
            // Handle cases like "1,2,3" or "The indexes are: 1,2,3" or "1, 2, 3"
            String cleanedResponse = aiResponse
                .replaceAll("[^0-9,]", "") // Remove all non-digit, non-comma characters
                .trim();

            // Parse indexes
            if (cleanedResponse.isEmpty() || aiResponse.equalsIgnoreCase("NONE")) {
                logger.debug("No matching indexes found");
                return List.of();
            }

            List<RequestMapping> matches = new ArrayList<>();
            String[] indexes = cleanedResponse.split(",");
            for (String indexStr : indexes) {
                try {
                    indexStr = indexStr.trim();
                    if (indexStr.isEmpty()) continue;

                    int index = Integer.parseInt(indexStr);
                    if (index >= 0 && index < allMappings.size()) {
                        matches.add(allMappings.get(index));
                        logger.info("✓ Matched endpoint [{}]: {}", index, allMappings.get(index).getName());
                    } else {
                        logger.warn("Index {} out of range (0-{})", index, allMappings.size() - 1);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid index in AI response: '{}'", indexStr);
                }
            }

            logger.info("AI filtering completed: {} endpoints matched", matches.size());
            return matches;

        } catch (Exception e) {
            logger.error("Error filtering endpoints with AI, falling back to keyword matching", e);
            // Fallback to simple keyword matching
            return fallbackKeywordMatching(userPrompt, allMappings);
        }
    }

    /**
     * Fallback keyword matching if AI fails
     */
    private List<RequestMapping> fallbackKeywordMatching(String userPrompt, List<RequestMapping> allMappings) {
        List<String> keywords = extractKeywordsUsingAI(userPrompt, allMappings);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<RequestMapping> matches = new ArrayList<>();
        for (RequestMapping mapping : allMappings) {
            if (mapping.getName() != null) {
                String mappingNameLower = mapping.getName().toLowerCase();
                for (String keyword : keywords) {
                    if (mappingNameLower.contains(keyword)) {
                        matches.add(mapping);
                        break;
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Use AI to extract meaningful keywords that identify specific endpoints
     * Filters out common words, workspace names, and action verbs
     */
    private List<String> extractKeywordsUsingAI(String userPrompt, List<RequestMapping> allMappings) {
        try {
            // Build list of available endpoint names for context
            String endpointNames = allMappings.stream()
                .map(RequestMapping::getName)
                .limit(20) // Limit to avoid token overflow
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");

            String systemPrompt = """
                You are extracting endpoint identifiers from a user's query.

                Available endpoints in the system: %s

                User query: "%s"

                Your task: Extract words that identify WHICH specific endpoint(s) the user wants.

                EXTRACT these:
                - Words that appear in endpoint names (memo, account, order, customer, hello, etc.)
                - Path segments from URLs (/memo, /account, /api/orders)
                - Any word that helps identify a specific endpoint

                IGNORE these:
                - Action verbs: list, show, get, explain, describe, create, delete, update
                - Generic terms: endpoint, endpoints, mapping, mappings, api, all
                - Workspace/namespace: default, demo, workspace, namespace, current
                - Questions: what, which, are, is, have, has, any, where
                - Properties: body, patterns, headers, response, request, status, configured

                Response format:
                - If user mentions specific endpoints: Return comma-separated keywords (e.g., "memo" or "account, order")
                - If user wants ALL endpoints (no specific identifier): Return "NONE"

                Examples:
                Query: "list memo endpoints" → Answer: memo
                Query: "show order endpoint" → Answer: order
                Query: "list all endpoints" → Answer: NONE
                Query: "list default namespace endpoints" → Answer: NONE
                Query: "explain /api/account" → Answer: account
                Query: "what are configured body patterns" → Answer: NONE
                Query: "show hello world endpoint" → Answer: hello

                Now extract keywords from the user query above. Respond with ONLY the keywords or NONE.
                """.formatted(endpointNames, userPrompt);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), "Extract keywords:"));

            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.3) // Balanced temperature for accurate extraction
                .maxTokens(100)
                .build();

            String aiResponse = openAiService.createChatCompletion(chatRequest)
                .getChoices().get(0).getMessage().getContent().trim();

            logger.debug("AI keyword extraction: '{}' → '{}'", userPrompt, aiResponse);

            // Parse response
            if (aiResponse.equalsIgnoreCase("NONE") || aiResponse.isEmpty()) {
                return List.of();
            }

            // Split by comma and clean up
            return Arrays.stream(aiResponse.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();

        } catch (Exception e) {
            logger.error("Error extracting keywords with AI, falling back to empty list", e);
            return List.of();
        }
    }

    /**
     * Handle query about a specific endpoint
     */
    private AIResponse handleSpecificEndpointQuery(AIRequest request, RequestMapping mapping) {
        StringBuilder details = new StringBuilder();
        details.append(String.format("📍 **%s**\n\n", mapping.getName()));

        if (mapping.getRequest() != null) {
            details.append(String.format("**Method**: `%s`\n", mapping.getRequest().getMethod()));
            details.append(String.format("**Path**: `%s`\n", mapping.getRequest().getPath()));
            details.append(String.format("**Workspace**: `%s`\n", mapping.getNamespace() != null ? mapping.getNamespace() : "default"));
            details.append(String.format("**Priority**: %d\n", mapping.getPriority() != null ? mapping.getPriority() : 5));
            details.append(String.format("**Status**: %s\n\n",
                Boolean.TRUE.equals(mapping.getEnabled()) ? "✅ Enabled" : "❌ Disabled"));

            // Add request details
            if (mapping.getRequest().getHeaders() != null && !mapping.getRequest().getHeaders().isEmpty()) {
                details.append("**Required Headers**:\n");
                mapping.getRequest().getHeaders().forEach((key, value) ->
                    details.append(String.format("  - `%s: %s`\n", key, value)));
                details.append("\n");
            }

            if (mapping.getRequest().getBodyPatterns() != null && !mapping.getRequest().getBodyPatterns().isEmpty()) {
                details.append("**Body Patterns**:\n");
                for (var pattern : mapping.getRequest().getBodyPatterns()) {
                    details.append(String.format("  - %s: `%s`\n", pattern.getMatchType(), pattern.getExpr()));
                }
                details.append("\n");
            }
        }

        // Add response details
        if (mapping.getResponse() != null) {
            details.append(String.format("**Response Status**: %d\n",
                mapping.getResponse().getStatus() != null ? mapping.getResponse().getStatus() : 200));

            if (mapping.getResponse().getBody() != null) {
                details.append("\n**Response Body**:\n```json\n");
                details.append(mapping.getResponse().getBody());
                details.append("\n```\n");
            }
        }

        // Add delays info
        if (mapping.getDelays() != null && mapping.getDelays().getMode() != null) {
            details.append(String.format("\n**Delay**: %s", mapping.getDelays().getMode()));
            if ("fixed".equals(mapping.getDelays().getMode())) {
                details.append(String.format(" (%dms)", mapping.getDelays().getFixedMs()));
            } else if ("variable".equals(mapping.getDelays().getMode())) {
                details.append(String.format(" (%d-%dms)",
                    mapping.getDelays().getVariableMinMs(), mapping.getDelays().getVariableMaxMs()));
            }
            details.append("\n");
        }

        return AIResponse.builder()
            .success(true)
            .action("show_details")
            .message("Endpoint details")
            .explanation(details.toString())
            .mappingId(mapping.getId())
            .build();
    }

    /**
     * Handle query about multiple matching endpoints
     */
    private AIResponse handleMultipleEndpointsQuery(AIRequest request, List<RequestMapping> matchingMappings) {
        StringBuilder listText = new StringBuilder();

        // Show workspace context
        String workspace = request.getNamespace() != null ? request.getNamespace() : "default";
        listText.append(String.format("📋 **Found %d matching endpoints in workspace `%s`:**\n\n",
            matchingMappings.size(), workspace));

        for (int i = 0; i < matchingMappings.size(); i++) {
            RequestMapping m = matchingMappings.get(i);
            listText.append(String.format("%d. **%s**\n", i + 1, m.getName()));
            if (m.getRequest() != null) {
                listText.append(String.format("   └─ `%s %s`\n",
                    m.getRequest().getMethod(),
                    m.getRequest().getPath()));

                // Add mandatory headers info
                if (m.getRequest().getHeaders() != null && !m.getRequest().getHeaders().isEmpty()) {
                    listText.append(String.format("   └─ **Required Headers**: %s\n",
                        String.join(", ", m.getRequest().getHeaders().keySet())));
                } else {
                    listText.append("   └─ **Required Headers**: None\n");
                }

                listText.append(String.format("   └─ Status: %d | Priority: %d | %s\n",
                    m.getResponse() != null && m.getResponse().getStatus() != null ? m.getResponse().getStatus() : 200,
                    m.getPriority() != null ? m.getPriority() : 5,
                    Boolean.TRUE.equals(m.getEnabled()) ? "✅ Enabled" : "❌ Disabled"));
            }
            listText.append("\n");
        }

        return AIResponse.builder()
            .success(true)
            .action("list")
            .message(String.format("Found %d matching endpoints", matchingMappings.size()))
            .explanation(listText.toString())
            .mappings(matchingMappings)
            .build();
    }

    /**
     * Handle query about all endpoints
     */
    private AIResponse handleAllEndpointsQuery(AIRequest request, List<RequestMapping> allMappings) {
        if (allMappings.isEmpty()) {
            return AIResponse.builder()
                .success(true)
                .action("list")
                .message("No mappings found")
                .explanation("📋 You currently have no API mappings configured in this workspace.")
                .mappings(List.of())
                .build();
        }

        StringBuilder listText = new StringBuilder();
        listText.append(String.format("📋 **Found %d endpoints in your workspace:**\n\n", allMappings.size()));

        for (int i = 0; i < allMappings.size(); i++) {
            RequestMapping m = allMappings.get(i);
            listText.append(String.format("%d. **%s**\n", i + 1, m.getName()));
            if (m.getRequest() != null) {
                listText.append(String.format("   └─ `%s %s`\n",
                    m.getRequest().getMethod(),
                    m.getRequest().getPath()));
                listText.append(String.format("   └─ Status: %d | Priority: %d | %s\n",
                    m.getResponse() != null && m.getResponse().getStatus() != null ? m.getResponse().getStatus() : 200,
                    m.getPriority() != null ? m.getPriority() : 5,
                    Boolean.TRUE.equals(m.getEnabled()) ? "✅ Enabled" : "❌ Disabled"));
            }
            listText.append("\n");
        }

        return AIResponse.builder()
            .success(true)
            .action("list")
            .message(String.format("Found %d endpoints", allMappings.size()))
            .explanation(listText.toString())
            .mappings(allMappings)
            .build();
    }
}
