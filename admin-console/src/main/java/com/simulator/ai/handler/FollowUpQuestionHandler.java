package com.simulator.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

/**
 * Handler for follow-up questions that use conversation history
 * Detects when user asks vague questions and uses context from previous messages
 */
@Component
public class FollowUpQuestionHandler extends AbstractTaskHandler {

    @Autowired
    private TaskHandlerRegistry registry;

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${simulator.ai.model}")
    private String model;

    @PostConstruct
    public void registerSelf() {
        registry.registerHandler(this);
    }

    @Override
    public int getPriority() {
        return 5; // Higher priority than generic handlers, lower than specific ones
    }

    @Override
    public AIRequest.AITaskType[] getSupportedTaskTypes() {
        return new AIRequest.AITaskType[]{
            AIRequest.AITaskType.EXPLAIN_MAPPING,
            AIRequest.AITaskType.LIST_MAPPINGS,
            AIRequest.AITaskType.DEBUG_MAPPING,
            AIRequest.AITaskType.ANALYZE_PAYLOAD,
            AIRequest.AITaskType.ANALYZE_CURL,
            AIRequest.AITaskType.CHECK_ENDPOINT_MATCH
        };
    }

    @Override
    public boolean canHandle(AIRequest request, List<RequestMapping> allMappings) {
        // Only handle if:
        // 1. Has conversation history
        // 2. Current question is vague (doesn't mention specific endpoint names/paths)
        // 3. Task type is EXPLAIN_MAPPING or LIST_MAPPINGS

        if (!super.canHandle(request, allMappings)) {
            logger.debug("FollowUpQuestionHandler: Unsupported task type");
            return false;
        }

        if (request.getConversationHistory() == null || request.getConversationHistory().isEmpty()) {
            logger.debug("FollowUpQuestionHandler: No conversation history");
            return false;
        }

        // Use AI to determine if this is a follow-up question
        boolean isFollowUp = isFollowUpQuestionUsingAI(request.getUserPrompt(), request.getConversationHistory());

        if (!isFollowUp) {
            logger.debug("FollowUpQuestionHandler: AI determined this is NOT a follow-up question");
            return false;
        }

        // Check if question is vague (no specific endpoint name/path mentioned)
        boolean hasSpecificEndpoint = hasExplicitEndpointReference(request.getUserPrompt().toLowerCase(), allMappings);

        if (hasSpecificEndpoint) {
            logger.debug("FollowUpQuestionHandler: Has explicit endpoint reference, not a follow-up");
            return false;
        }

        logger.info("FollowUpQuestionHandler: CAN HANDLE - AI confirmed follow-up question with conversation history");
        return true;
    }

    /**
     * Use AI to determine if current question is a follow-up based on conversation history
     */
    private boolean isFollowUpQuestionUsingAI(String currentQuestion, List<AIRequest.ChatMessage> history) {
        try {
            // Build recent conversation context (last 4 messages)
            StringBuilder conversationContext = new StringBuilder();
            int startIndex = Math.max(0, history.size() - 4);

            for (int i = startIndex; i < history.size(); i++) {
                AIRequest.ChatMessage msg = history.get(i);
                conversationContext.append(String.format("%s: %s\n",
                    msg.getRole().toUpperCase(),
                    msg.getContent().length() > 200 ? msg.getContent().substring(0, 200) + "..." : msg.getContent()));
            }

            String systemPrompt = """
                You are analyzing whether a user's question is a follow-up question or an initial/standalone question.

                A FOLLOW-UP question:
                - Refers to previous context ("are there any headers", "what about body patterns", "does it have...")
                - Asks for more details about something already discussed
                - Uses pronouns like "it", "they", "these", "those" without specifying what
                - Asks about properties/attributes without naming the subject

                An INITIAL/STANDALONE question:
                - Explicitly names what to list/show/get ("list memo endpoints", "show order endpoint")
                - Contains the full context needed to understand it
                - Starts with commands like "list", "show", "create", "delete", "get"

                Conversation history:
                %s

                Current question: "%s"

                Respond with ONLY one word: "FOLLOWUP" or "INITIAL"
                """.formatted(conversationContext.toString(), currentQuestion);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(),
                "Is this a follow-up question or initial question?"));

            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.1) // Low temperature for consistent classification
                .maxTokens(10)
                .build();

            String aiResponse = openAiService.createChatCompletion(chatRequest)
                .getChoices().get(0).getMessage().getContent().trim().toUpperCase();

            logger.info("AI classification for '{}': {}",
                currentQuestion.substring(0, Math.min(50, currentQuestion.length())),
                aiResponse);

            return aiResponse.contains("FOLLOWUP");

        } catch (Exception e) {
            logger.error("Error calling AI for follow-up detection, defaulting to false", e);
            return false;
        }
    }

    /**
     * Check if prompt explicitly mentions a specific endpoint name or path
     * This uses a stricter check than identifyTargetEndpoint()
     */
    private boolean hasExplicitEndpointReference(String lowerPrompt, List<RequestMapping> allMappings) {
        // Check for explicit path mentions (e.g., "/api/memo")
        for (RequestMapping mapping : allMappings) {
            if (mapping.getRequest() != null && mapping.getRequest().getPath() != null) {
                String path = mapping.getRequest().getPath().toLowerCase();
                if (lowerPrompt.contains(path)) {
                    return true;
                }
            }
        }

        // Check for explicit endpoint name mentions (full name, not partial words)
        for (RequestMapping mapping : allMappings) {
            if (mapping.getName() != null) {
                String nameLower = mapping.getName().toLowerCase();
                // Only match full name, not partial words
                if (lowerPrompt.contains(nameLower)) {
                    return true;
                }
            }
        }

        return false;
    }


    @Override
    public AIResponse handle(AIRequest request, List<RequestMapping> allMappings) {
        logger.info("FollowUpQuestionHandler processing follow-up question with conversation history");

        // Extract endpoints mentioned in conversation history
        List<RequestMapping> contextEndpoints = extractEndpointsFromHistory(
            request.getConversationHistory(), allMappings);

        if (contextEndpoints.isEmpty()) {
            logger.warn("No endpoints found in conversation history, cannot answer follow-up");
            return AIResponse.builder()
                .success(false)
                .action("error")
                .message("Could not determine which endpoints you're asking about")
                .explanation("Please specify which endpoint(s) you want to know about.")
                .build();
        }

        logger.info("Found {} endpoints from conversation history: {}",
            contextEndpoints.size(),
            contextEndpoints.stream().map(RequestMapping::getName).toList());

        // Build DEEP context from these endpoints with full configuration
        StringBuilder context = new StringBuilder();
        context.append("=== ENDPOINTS FROM PREVIOUS CONVERSATION ===\n\n");

        for (RequestMapping mapping : contextEndpoints) {
            context.append(buildDeepEndpointContext(mapping));
            context.append("\n---\n\n");
        }

        // Use AI to answer the follow-up question with this context
        String systemPrompt = String.format("""
            You are helping a user understand API endpoint configurations.

            The user previously asked about these endpoints. Here is their COMPLETE configuration:

            %s

            **Instructions:**
            1. Answer their follow-up question using the FULL configuration details provided above
            2. Look at ALL aspects: request patterns, response body, headers, conditional responses, delays, etc.
            3. For questions about what an endpoint "does" or "checks", examine:
               - The endpoint name
               - Body patterns (what fields are required)
               - Response body (what data is returned)
               - Conditional responses (different responses based on request ID)
               - Error configurations
            4. Be specific and reference actual configuration values
            5. If the information isn't in the context, say "I don't have that information."

            **Format your answer concisely:**
            - Start with a direct answer to their question
            - Include relevant configuration details
            - Use bullet points for clarity
            """, context.toString());

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), request.getUserPrompt()));

            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.3)
                .maxTokens(500)
                .build();

            String aiAnswer = openAiService.createChatCompletion(chatRequest)
                .getChoices().get(0).getMessage().getContent();

            logger.info("AI answered follow-up question: {}", aiAnswer.substring(0, Math.min(100, aiAnswer.length())));

            return AIResponse.builder()
                .success(true)
                .action("explain")
                .message("Answer based on previous context")
                .explanation(aiAnswer)
                .mappings(contextEndpoints)
                .build();

        } catch (Exception e) {
            logger.error("Error calling OpenAI for follow-up question", e);
            return AIResponse.builder()
                .success(false)
                .action("error")
                .message("Failed to process follow-up question")
                .explanation("Error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Extract endpoints that were mentioned in conversation history
     */
    private List<RequestMapping> extractEndpointsFromHistory(
        List<AIRequest.ChatMessage> history, List<RequestMapping> allMappings) {

        List<RequestMapping> mentioned = new ArrayList<>();

        // Look at recent assistant messages to find endpoint names
        for (int i = history.size() - 1; i >= 0 && i >= history.size() - 3; i--) {
            AIRequest.ChatMessage msg = history.get(i);
            if ("assistant".equals(msg.getRole())) {
                String content = msg.getContent().toLowerCase();

                for (RequestMapping mapping : allMappings) {
                    if (mapping.getName() != null) {
                        String nameLower = mapping.getName().toLowerCase();
                        if (content.contains(nameLower)) {
                            if (!mentioned.contains(mapping)) {
                                mentioned.add(mapping);
                            }
                        }
                    }

                    // Also check by path
                    if (mapping.getRequest() != null && mapping.getRequest().getPath() != null) {
                        String pathLower = mapping.getRequest().getPath().toLowerCase();
                        if (content.contains(pathLower)) {
                            if (!mentioned.contains(mapping)) {
                                mentioned.add(mapping);
                            }
                        }
                    }
                }
            }
        }

        return mentioned;
    }

    /**
     * Build deep context with complete endpoint configuration
     * (Similar to AIService.buildDeepEndpointContext but standalone)
     */
    private String buildDeepEndpointContext(RequestMapping mapping) {
        if (mapping == null) {
            return "No endpoint configuration available.";
        }

        try {
            StringBuilder context = new StringBuilder();

            // Basic Info
            context.append(String.format("**Endpoint Name**: %s\n", mapping.getName()));
            context.append(String.format("**ID**: %s\n", mapping.getId()));
            context.append(String.format("**Priority**: %d\n",
                mapping.getPriority() != null ? mapping.getPriority() : 5));
            context.append(String.format("**Enabled**: %s\n\n", mapping.getEnabled()));

            // Request Configuration
            if (mapping.getRequest() != null) {
                var req = mapping.getRequest();
                context.append("**REQUEST CONFIGURATION:**\n");
                context.append(String.format("  - Method: %s\n", req.getMethod()));
                context.append(String.format("  - Path: %s\n", req.getPath()));

                if (req.getHeaders() != null && !req.getHeaders().isEmpty()) {
                    context.append("  - Required Headers:\n");
                    req.getHeaders().forEach((key, value) ->
                        context.append(String.format("    * %s: %s\n", key, value)));
                }

                if (req.getHeaderPatterns() != null && !req.getHeaderPatterns().isEmpty()) {
                    context.append("  - Header Patterns:\n");
                    req.getHeaderPatterns().forEach((headerName, pattern) ->
                        context.append(String.format("    * %s: matchType=%s, pattern=%s\n",
                            headerName, pattern.getMatchType(), pattern.getPattern())));
                }

                if (req.getBodyPatterns() != null && !req.getBodyPatterns().isEmpty()) {
                    context.append("  - Body Patterns:\n");
                    for (var bodyPattern : req.getBodyPatterns()) {
                        context.append(String.format("    * Type: %s, Expression: %s, Expected: %s\n",
                            bodyPattern.getMatchType(),
                            bodyPattern.getExpr(),
                            bodyPattern.getExpected()));
                    }
                }
                context.append("\n");
            }

            // Response Configuration
            if (mapping.getResponse() != null) {
                var resp = mapping.getResponse();
                context.append("**RESPONSE CONFIGURATION:**\n");
                context.append(String.format("  - Status Code: %d\n",
                    resp.getStatus() != null ? resp.getStatus() : 200));

                if (resp.getHeaders() != null && !resp.getHeaders().isEmpty()) {
                    context.append("  - Response Headers:\n");
                    resp.getHeaders().forEach((key, value) ->
                        context.append(String.format("    * %s: %s\n", key, value)));
                }

                if (resp.getBody() != null) {
                    context.append("  - Response Body:\n");
                    context.append("```json\n");
                    context.append(resp.getBody());
                    context.append("\n```\n");
                }

                // Conditional Responses
                if (resp.getConditionalResponses() != null &&
                    Boolean.TRUE.equals(resp.getConditionalResponses().getEnabled())) {
                    context.append("\n  - **CONDITIONAL RESPONSES** (enabled):\n");
                    context.append(String.format("    Request ID Header: %s\n",
                        resp.getConditionalResponses().getRequestIdHeader()));

                    if (resp.getConditionalResponses().getRequestIdMappings() != null) {
                        context.append("    Mappings:\n");
                        for (var mapping2 : resp.getConditionalResponses().getRequestIdMappings()) {
                            context.append(String.format("      * Request ID '%s':\n", mapping2.getRequestId()));
                            context.append(String.format("        - Status: %d\n",
                                mapping2.getStatus() != null ? mapping2.getStatus() : 200));
                            if (mapping2.getBody() != null) {
                                context.append(String.format("        - Body: %s\n", mapping2.getBody()));
                            }
                        }
                    }
                }
                context.append("\n");
            }

            // Delays Configuration
            if (mapping.getDelays() != null) {
                var delays = mapping.getDelays();
                context.append("**DELAY/CHAOS CONFIGURATION:**\n");
                context.append(String.format("  - Mode: %s\n", delays.getMode()));
                if ("fixed".equals(delays.getMode())) {
                    context.append(String.format("  - Fixed Delay: %dms\n", delays.getFixedMs()));
                } else if ("variable".equals(delays.getMode())) {
                    context.append(String.format("  - Variable Delay: %dms - %dms\n",
                        delays.getVariableMinMs(), delays.getVariableMaxMs()));
                }
                if (delays.getErrorRatePercent() != null && delays.getErrorRatePercent() > 0) {
                    context.append(String.format("  - Error Rate: %d%%\n", delays.getErrorRatePercent()));
                    if (delays.getErrorResponse() != null) {
                        context.append(String.format("  - Error Response Status: %d\n",
                            delays.getErrorResponse().getStatus()));
                        if (delays.getErrorResponse().getBody() != null) {
                            context.append(String.format("  - Error Response Body: %s\n",
                                delays.getErrorResponse().getBody()));
                        }
                    }
                }
                context.append("\n");
            }

            // Tags
            if (mapping.getTags() != null && !mapping.getTags().isEmpty()) {
                context.append(String.format("**Tags**: %s\n", String.join(", ", mapping.getTags())));
            }

            return context.toString();
        } catch (Exception e) {
            logger.error("Error building deep context: {}", e.getMessage(), e);
            return "Error building endpoint context: " + e.getMessage();
        }
    }
}
