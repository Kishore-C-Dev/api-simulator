package com.simulator.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulator.ai.handler.TaskHandler;
import com.simulator.ai.handler.TaskHandlerRegistry;
import com.simulator.ai.model.AIRequest;
import com.simulator.ai.model.AIResponse;
import com.simulator.model.RequestMapping;
import com.simulator.service.MappingService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "simulator.ai.enabled", havingValue = "true")
public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private MappingService mappingService;

    @Autowired
    private AIContextService contextService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.simulator.service.UserService userService;

    @Autowired
    private com.simulator.repository.NamespaceRepository namespaceRepository;

    @Autowired
    private com.simulator.repository.UserProfileRepository userRepository;

    @Autowired
    private TaskHandlerRegistry taskHandlerRegistry;

    @Value("${simulator.ai.model}")
    private String model;

    @Value("${simulator.ai.max-tokens}")
    private int maxTokens;

    @Value("${simulator.ai.temperature}")
    private double temperature;

    @Value("${simulator.ai.max-context-mappings}")
    private int maxContextMappings;

    /**
     * Generate a mapping from natural language prompt
     */
    public AIResponse generateMapping(AIRequest request) {
        try {
            logger.info("=== AI Request Started ===");
            logger.info("Task Type: {}", request.getTaskType());
            logger.info("User Prompt: {}", request.getUserPrompt());
            logger.info("Namespace: {}", request.getNamespace());

            // Log conversation history if present
            if (request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
                logger.info("Conversation History ({} messages):", request.getConversationHistory().size());
                for (int i = 0; i < request.getConversationHistory().size(); i++) {
                    AIRequest.ChatMessage msg = request.getConversationHistory().get(i);
                    logger.info("  [{}] {}: {}", i + 1, msg.getRole().toUpperCase(),
                        msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "..." : msg.getContent());
                }
            } else {
                logger.info("Conversation History: None");
            }

            // Get relevant existing mappings for context
            logger.info("Fetching mappings for namespace: '{}'", request.getNamespace());
            List<RequestMapping> allMappings = mappingService.getAllMappings(request.getNamespace());
            logger.info("Found {} mappings in namespace '{}'", allMappings.size(), request.getNamespace());

            // Determine task type automatically if not specified
            if (request.getTaskType() == null) {
                AIRequest.AITaskType detectedType = determineTaskType(request.getUserPrompt(), allMappings);
                request.setTaskType(detectedType);
                logger.info("Auto-detected Task Type: {}", detectedType);
            }

            logger.info("Executing Task Type: {}", request.getTaskType());

            // Try to find a handler from the registry first (new strategy pattern)
            TaskHandler handler = taskHandlerRegistry.findHandler(request, allMappings);
            if (handler != null) {
                logger.info("Using strategy handler: {}", handler.getClass().getSimpleName());
                return handler.handle(request, allMappings);
            }

            // Fallback to legacy switch statement for handlers not yet migrated
            logger.info("Using legacy switch handler for task type: {}", request.getTaskType());

            // Handle different task types
            switch (request.getTaskType()) {
                // Mapping CRUD
                case CREATE_MAPPING:
                    return handleCreateMapping(request, allMappings);
                case MODIFY_MAPPING:
                    return handleModifyMapping(request, allMappings);
                case DELETE_MAPPING:
                    return handleDeleteMapping(request, allMappings);
                case MOVE_MAPPING:
                    return handleMoveMapping(request, allMappings);
                case BULK_UPDATE_MAPPING:
                    return handleBulkUpdateMapping(request, allMappings);
                case LIST_MAPPINGS:
                    // Fallback if no handler registered
                    return handleListMappings(request, allMappings);

                // Mapping helpers
                case DEBUG_MAPPING:
                    return handleDebugMapping(request, allMappings);
                case EXPLAIN_MAPPING:
                    // Fallback if no handler registered
                    return handleExplainMapping(request, allMappings);
                case OPTIMIZE_MAPPING:
                    return handleOptimizeMapping(request, allMappings);
                case SUGGEST_RESPONSE:
                    return handleSuggestResponse(request, allMappings);

                // Ask mode - payload/endpoint analysis
                case ANALYZE_PAYLOAD:
                    return handleAnalyzePayload(request, allMappings);
                case ANALYZE_CURL:
                    return handleAnalyzeCurl(request, allMappings);
                case CHECK_ENDPOINT_MATCH:
                    return handleCheckEndpointMatch(request, allMappings);

                // Namespace CRUD
                case CREATE_NAMESPACE:
                    return handleCreateNamespace(request);
                case MODIFY_NAMESPACE:
                    return handleModifyNamespace(request);
                case DELETE_NAMESPACE:
                    return handleDeleteNamespace(request);
                case LIST_NAMESPACES:
                    return handleListNamespaces(request);

                // User CRUD
                case CREATE_USER:
                    return handleCreateUser(request);
                case MODIFY_USER:
                    return handleModifyUser(request);
                case DELETE_USER:
                    return handleDeleteUser(request);
                case LIST_USERS:
                    return handleListUsers(request);

                // User/Namespace management
                case ENABLE_DISABLE_USER:
                    return handleEnableDisableUser(request);
                case ASSIGN_NAMESPACE:
                    return handleAssignNamespace(request);

                default:
                    return AIResponse.error("Unsupported task type: " + request.getTaskType());
            }

        } catch (Exception e) {
            logger.error("Error processing AI request: {}", e.getMessage(), e);
            return AIResponse.error("Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Auto-detect task type from user prompt using AI
     */
    private AIRequest.AITaskType determineTaskType(String prompt, List<RequestMapping> allMappings) {
        try {
            logger.debug("Using AI to determine task type for prompt: '{}'", prompt);

            String systemPrompt = """
                You are a task classification expert for an API Simulator application.

                Analyze the user's request and determine which task type it represents.

                Available task types:

                **API Endpoint/Mapping CRUD:**
                - CREATE_MAPPING: Create a new API endpoint/mapping
                - MODIFY_MAPPING: Update/change an existing endpoint
                - DELETE_MAPPING: Delete/remove an endpoint
                - MOVE_MAPPING: Move/transfer endpoint to different workspace/namespace (e.g., "move memo to demo workspace", "transfer account endpoint to default")
                - BULK_UPDATE_MAPPING: Update multiple/all endpoints at once (e.g., "add header to all endpoints", "set timeout for all", "enable all endpoints")
                - LIST_MAPPINGS: Show ALL endpoints in workspace (ONLY when user says "all endpoints", "list all", "show everything", "what endpoints exist")
                - EXPLAIN_MAPPING: Show details about SPECIFIC endpoint(s) by name or path (e.g., "list memo endpoint", "show memo endpoints", "explain order endpoint", "show /account endpoint")

                KEY DISTINCTION:
                - If user mentions a SPECIFIC name/word (like "memo", "order", "account"), use EXPLAIN_MAPPING
                - If user wants to see EVERYTHING without specifying anything, use LIST_MAPPINGS

                **API Endpoint Helpers:**
                - DEBUG_MAPPING: Debug endpoint issues (not working, errors)
                - OPTIMIZE_MAPPING: Improve endpoint performance/configuration
                - SUGGEST_RESPONSE: Help creating response data

                **OpenAPI Spec Generation:**
                - GENERATE_FROM_OPENAPI: User provides OpenAPI/Swagger spec (YAML/JSON) and wants to generate multiple endpoints for all status codes
                  * Look for keywords: "openapi spec", "swagger", "generate from spec", "yaml", "create endpoints from spec"
                  * User provides YAML/JSON content with API definitions
                  * Goal: Create multiple endpoint variants covering all HTTP status codes

                **Ask Mode - Payload/Endpoint Analysis:**
                - ANALYZE_PAYLOAD: User provides request payload/data and wants to know which endpoint matches or what's missing
                - ANALYZE_CURL: User provides a curl command and wants to understand endpoint matching
                - CHECK_ENDPOINT_MATCH: User provides endpoint name/path and payload to check configuration

                **Namespace CRUD (workspaces, only if NOT about API endpoints):**
                - CREATE_NAMESPACE: Create a new workspace/namespace
                - MODIFY_NAMESPACE: Update/rename a namespace
                - DELETE_NAMESPACE: Delete/remove a namespace
                - LIST_NAMESPACES: Show all namespaces/workspaces

                **User CRUD (user accounts, only if NOT about API endpoints):**
                - CREATE_USER: Register/add a new user account
                - MODIFY_USER: Update user details (name, email, password, etc.)
                - DELETE_USER: Delete/remove a user account
                - LIST_USERS: Show all user accounts

                **User/Namespace Management:**
                - ENABLE_DISABLE_USER: Enable, disable, activate, or deactivate a user
                - ASSIGN_NAMESPACE: Assign a user to a namespace/workspace

                IMPORTANT:
                - Admin tasks (namespace/user) should ONLY be selected if the request is clearly about users or namespaces
                - If the request mentions "endpoint", "api", "mapping", it's likely a mapping task
                - For delete operations, identify WHAT is being deleted (mapping, namespace, or user)
                - For modify operations, identify WHAT is being modified

                Respond with ONLY the task type name (e.g., "CREATE_MAPPING", "DELETE_NAMESPACE", "MODIFY_USER").
                No explanations, just the task type.
                """;

            String aiResponse = callOpenAI(systemPrompt, prompt);
            String taskTypeStr = aiResponse.trim().toUpperCase()
                .replaceAll("```|\\*\\*|##", "")  // Remove markdown
                .trim();

            logger.info("AI determined task type: {}", taskTypeStr);

            try {
                return AIRequest.AITaskType.valueOf(taskTypeStr);
            } catch (IllegalArgumentException e) {
                logger.warn("AI returned invalid task type '{}', defaulting to CREATE_MAPPING", taskTypeStr);
                return AIRequest.AITaskType.CREATE_MAPPING;
            }

        } catch (Exception e) {
            logger.error("Error determining task type with AI, using fallback: {}", e.getMessage());
            // Fallback to simple heuristics
            String lowerPrompt = prompt.toLowerCase();

            // Check for OpenAPI spec
            if ((lowerPrompt.contains("openapi") || lowerPrompt.contains("swagger") || lowerPrompt.contains("generate from spec")) ||
                (prompt.contains("openapi:") || prompt.contains("swagger:"))) {
                return AIRequest.AITaskType.GENERATE_FROM_OPENAPI;
            }

            if (lowerPrompt.contains("namespace") && lowerPrompt.contains("create")) {
                return AIRequest.AITaskType.CREATE_NAMESPACE;
            }
            if (lowerPrompt.contains("user") && lowerPrompt.contains("create")) {
                return AIRequest.AITaskType.CREATE_USER;
            }
            return AIRequest.AITaskType.CREATE_MAPPING;
        }
    }

    /**
     * Handle CREATE_MAPPING task
     */
    private AIResponse handleCreateMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            List<RequestMapping> relevantMappings = contextService.getRelevantMappings(
                allMappings, request.getUserPrompt(), maxContextMappings
            );

            String context = contextService.buildContext(relevantMappings);
            String systemPrompt = buildCreateMappingPrompt(context, request.getNamespace());

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            RequestMapping mapping = parseMappingFromResponse(aiResponse);
            mapping.setNamespace(request.getNamespace());

            return AIResponse.success(mapping);
        } catch (Exception e) {
            logger.error("Error creating mapping: {}", e.getMessage(), e);
            return AIResponse.error("Failed to create mapping: " + e.getMessage());
        }
    }

    /**
     * Handle DEBUG_MAPPING task
     */
    private AIResponse handleDebugMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            String context = buildDetailedContext(allMappings);
            String systemPrompt = String.format("""
                You are an API debugging expert for the API Simulator application.

                WORKSPACE: %s

                %s

                Your task is to help debug API issues. Analyze the problem described by the user and:
                1. Identify potential issues (priority conflicts, pattern matching problems, incorrect paths, etc.)
                2. Explain why the API might not be working as expected
                3. Provide specific, actionable solutions
                4. If relevant, suggest which mappings to check or modify

                Respond in a clear, structured format with:
                - **Problem**: Brief description
                - **Root Cause**: What's causing the issue
                - **Solution**: Step-by-step fix
                - **Related Mappings**: List any relevant mapping IDs or names

                Be concise but thorough.
                """, request.getNamespace(), context);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());

            return AIResponse.builder()
                .success(true)
                .message("Debug analysis complete")
                .explanation(aiResponse)
                .build();
        } catch (Exception e) {
            logger.error("Error debugging: {}", e.getMessage(), e);
            return AIResponse.error("Failed to debug: " + e.getMessage());
        }
    }

    /**
     * Handle EXPLAIN_MAPPING task
     */
    private AIResponse handleExplainMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            String context = buildDetailedContext(allMappings);
            String systemPrompt = String.format("""
                You are an API documentation expert for the API Simulator application.

                WORKSPACE: %s

                %s

                Your task is to explain API mappings and configurations clearly to users.
                Provide clear, easy-to-understand explanations about:
                - What specific endpoints do
                - How they work
                - What responses they return
                - Any special configurations (delays, priorities, patterns, etc.)

                Use simple language and provide examples when helpful.
                """, request.getNamespace(), context);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());

            return AIResponse.builder()
                .success(true)
                .message("Explanation generated")
                .explanation(aiResponse)
                .build();
        } catch (Exception e) {
            logger.error("Error explaining: {}", e.getMessage(), e);
            return AIResponse.error("Failed to explain: " + e.getMessage());
        }
    }

    /**
     * Handle OPTIMIZE_MAPPING task
     */
    private AIResponse handleOptimizeMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            String context = buildDetailedContext(allMappings);
            String systemPrompt = String.format("""
                You are an API optimization expert for the API Simulator application.

                WORKSPACE: %s

                %s

                Your task is to suggest optimizations for API configurations:
                - Better priority ordering
                - More efficient pattern matching
                - Improved response times
                - Better organization and naming

                Provide specific, actionable recommendations with before/after examples.
                """, request.getNamespace(), context);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());

            return AIResponse.builder()
                .success(true)
                .message("Optimization suggestions generated")
                .suggestions(parseOptimizations(aiResponse))
                .explanation(aiResponse)
                .build();
        } catch (Exception e) {
            logger.error("Error optimizing: {}", e.getMessage(), e);
            return AIResponse.error("Failed to optimize: " + e.getMessage());
        }
    }

    /**
     * Handle SUGGEST_RESPONSE task
     */
    private AIResponse handleSuggestResponse(AIRequest request, List<RequestMapping> allMappings) {
        try {
            String systemPrompt = """
                You are an API response design expert.

                Your task is to suggest realistic, well-structured API responses.
                Consider:
                - Proper JSON structure
                - Realistic field names and data types
                - Common API patterns (pagination, metadata, etc.)
                - Appropriate HTTP status codes

                Return ONLY valid JSON, no markdown or explanations.
                """;

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());

            return AIResponse.builder()
                .success(true)
                .message("Response suggestion generated")
                .explanation(aiResponse)
                .build();
        } catch (Exception e) {
            logger.error("Error suggesting response: {}", e.getMessage(), e);
            return AIResponse.error("Failed to suggest response: " + e.getMessage());
        }
    }

    /**
     * Call OpenAI API with conversation history
     */
    private String callOpenAI(String systemPrompt, String userPrompt, List<AIRequest.ChatMessage> conversationHistory) {
        logger.info("--- Calling OpenAI API ---");
        logger.info("Model: {}", model);
        logger.info("Temperature: {}", temperature);
        logger.info("Max Tokens: {}", maxTokens);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));

        logger.info("System Prompt Length: {} characters", systemPrompt.length());
        logger.debug("System Prompt (first 200 chars): {}",
            systemPrompt.length() > 200 ? systemPrompt.substring(0, 200) + "..." : systemPrompt);

        // Add conversation history for context
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            logger.info("Adding {} conversation history messages", conversationHistory.size());
            for (AIRequest.ChatMessage historyMsg : conversationHistory) {
                String role = "user".equals(historyMsg.getRole())
                    ? ChatMessageRole.USER.value()
                    : ChatMessageRole.ASSISTANT.value();
                messages.add(new ChatMessage(role, historyMsg.getContent()));
            }
        }

        // Add current user message
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userPrompt));
        logger.info("Current User Prompt: {}", userPrompt);

        logger.info("Total messages in request: {}", messages.size());

        long startTime = System.currentTimeMillis();

        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
            .model(model)
            .messages(messages)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();

        logger.info("Sending request to OpenAI...");

        String response = openAiService.createChatCompletion(chatRequest)
            .getChoices()
            .get(0)
            .getMessage()
            .getContent();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        logger.info("OpenAI Response received in {} ms", duration);
        logger.info("Response Length: {} characters", response.length());
        logger.info("=== AI RESPONSE START ===");
        logger.info("{}", response);
        logger.info("=== AI RESPONSE END ===");

        return response;
    }

    /**
     * Call OpenAI API without conversation history (for backward compatibility)
     */
    private String callOpenAI(String systemPrompt, String userPrompt) {
        return callOpenAI(systemPrompt, userPrompt, null);
    }

    /**
     * Build detailed context with more information
     */
    private String buildDetailedContext(List<RequestMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return "No existing mappings in workspace.";
        }

        StringBuilder context = new StringBuilder();
        context.append("EXISTING ENDPOINTS:\n\n");

        for (RequestMapping mapping : mappings) {
            context.append(String.format("📍 **%s** (ID: %s)\n", mapping.getName(), mapping.getId()));
            if (mapping.getRequest() != null) {
                context.append(String.format("   Method: %s %s\n",
                    mapping.getRequest().getMethod(),
                    mapping.getRequest().getPath()));
                context.append(String.format("   Priority: %d | Status: %d | Enabled: %s\n",
                    mapping.getPriority() != null ? mapping.getPriority() : 5,
                    mapping.getResponse() != null && mapping.getResponse().getStatus() != null
                        ? mapping.getResponse().getStatus() : 200,
                    mapping.getEnabled() != null ? mapping.getEnabled() : true));

                if (mapping.getTags() != null && !mapping.getTags().isEmpty()) {
                    context.append(String.format("   Tags: %s\n", String.join(", ", mapping.getTags())));
                }

                if (mapping.getDelays() != null && mapping.getDelays().getMode() != null) {
                    context.append(String.format("   Delay: %s\n", mapping.getDelays().getMode()));
                }
            }
            context.append("\n");
        }

        return context.toString();
    }

    /**
     * Parse optimization suggestions from AI response
     */
    private List<String> parseOptimizations(String aiResponse) {
        // Simple parsing - split by lines starting with numbers or bullets
        return List.of(aiResponse.split("\n"));
    }

    /**
     * Handle MODIFY_MAPPING task
     */
    private AIResponse handleModifyMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            // Build context showing all existing mappings
            String context = buildDetailedContext(allMappings);

            // First, identify which mapping to modify
            String identifyPrompt = String.format("""
                You are an API mapping identification expert.

                WORKSPACE: %s

                %s

                Your task is to identify which mapping the user wants to modify.

                Analyze the user's request and respond with ONLY the mapping ID (the MongoDB ObjectId).
                If you cannot identify a specific mapping, respond with "UNKNOWN".

                Look for mentions of:
                - Mapping name
                - Endpoint path (e.g., /api/users)
                - HTTP method (GET, POST, etc.)
                - Tags or descriptions

                Respond with ONLY the ID or "UNKNOWN", nothing else.
                """, request.getNamespace(), context);

            String mappingIdResponse = callOpenAI(identifyPrompt, request.getUserPrompt()).trim();

            if ("UNKNOWN".equals(mappingIdResponse)) {
                return AIResponse.builder()
                    .success(false)
                    .message("Could not identify mapping")
                    .explanation("❓ I couldn't identify which specific endpoint you want to modify. Please specify the endpoint more clearly (by name, path, or show me the list of endpoints first).")
                    .build();
            }

            // Find the mapping
            RequestMapping existingMapping = allMappings.stream()
                .filter(m -> m.getId().equals(mappingIdResponse) ||
                            mappingIdResponse.toLowerCase().contains(m.getName().toLowerCase()))
                .findFirst()
                .orElse(null);

            if (existingMapping == null) {
                // Try by name if ID didn't match
                for (RequestMapping m : allMappings) {
                    if (mappingIdResponse.contains(m.getId()) ||
                        mappingIdResponse.toLowerCase().contains(m.getName().toLowerCase())) {
                        existingMapping = m;
                        break;
                    }
                }
            }

            if (existingMapping == null) {
                return AIResponse.builder()
                    .success(false)
                    .message("Mapping not found")
                    .explanation(String.format("❌ Could not find the mapping to modify. Available endpoints:\n\n%s", context))
                    .build();
            }

            // Now generate the modified mapping
            String existingMappingJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(existingMapping);

            String modifyPrompt = String.format("""
                You are an API mapping modification expert.

                WORKSPACE: %s

                The user wants to modify this existing mapping:

                ```json
                %s
                ```

                USER REQUEST: %s

                Your task is to generate the COMPLETE updated mapping JSON with the requested changes applied.

                IMPORTANT:
                1. Keep all existing fields that are not being modified
                2. Only change what the user requested (e.g., add header patterns, change response, update priority)
                3. Maintain the same ID, name, and namespace
                4. For adding header patterns: use the headerPatterns field as a MAP (not array) with this structure:
                   "headerPatterns": {
                     "Header-Name": {
                       "matchType": "EXACT|REGEX|CONTAINS|EXISTS",
                       "pattern": "value or regex",
                       "ignoreCase": false
                     }
                   }
                   - For EXISTS matchType, pattern can be empty string or null
                   - The key is the header name, the value is the ParameterPattern object
                5. For adding body patterns: append to the bodyPatterns array
                6. Preserve all other configurations
                7. NEVER use an array for headerPatterns - it must be a JSON object/map
                8. When modifying delays, use the COMPLETE object structure:

                   Fixed delay:
                   "delays": {
                     "mode": "fixed",
                     "fixedMs": 200,
                     "errorRatePercent": 0
                   }

                   Variable delay with chaos:
                   "delays": {
                     "mode": "variable",
                     "variableMinMs": 100,
                     "variableMaxMs": 500,
                     "errorRatePercent": 15,
                     "errorResponse": {
                       "status": 500,
                       "body": "{\\"error\\": \\"Service unavailable\\"}"
                     }
                   }

                   CRITICAL: NEVER set "delays" to a plain number like 5000.
                   ALWAYS use the complete object structure with mode, fixedMs, etc.

                Return ONLY the complete updated mapping JSON, no markdown or explanations.
                """, request.getNamespace(), existingMappingJson, request.getUserPrompt());

            String modifiedMappingJson = callOpenAI(modifyPrompt, request.getUserPrompt());
            RequestMapping modifiedMapping = parseMappingFromResponse(modifiedMappingJson);

            // Ensure ID and namespace are preserved
            modifiedMapping.setId(existingMapping.getId());
            modifiedMapping.setNamespace(existingMapping.getNamespace());
            modifiedMapping.setUpdatedAt(java.time.Instant.now());

            // Save the updated mapping
            RequestMapping saved = mappingService.saveMapping(modifiedMapping);

            return AIResponse.builder()
                .success(true)
                .action("modify_complete")
                .mappingId(saved.getId())
                .message("Mapping updated successfully")
                .explanation(String.format("✅ Updated mapping: **%s**\n\nChanges applied based on your request.",
                    saved.getName()))
                .generatedMapping(saved)
                .build();

        } catch (Exception e) {
            logger.error("Error modifying mapping: {}", e.getMessage(), e);
            return AIResponse.error("Failed to modify mapping: " + e.getMessage());
        }
    }

    /**
     * Handle DELETE_MAPPING task
     */
    private AIResponse handleDeleteMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            String context = buildDetailedContext(allMappings);
            String systemPrompt = String.format("""
                You are an API mapping deletion expert.

                WORKSPACE: %s

                %s

                Your task is to identify which mapping(s) the user wants to delete.

                Analyze the request and:
                1. Identify the specific mapping(s) by name, ID, path, or description
                2. Confirm what will be deleted
                3. Warn about any potential impacts

                Respond with:
                - **Mapping to Delete**: Name and ID
                - **Confirmation**: Clear description of what will be removed
                - **Impact**: Any warnings or considerations

                Format: Return the mapping ID on the first line, then explanation.
                """, request.getNamespace(), context);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());

            // Extract mapping ID
            String mappingId = extractMappingId(aiResponse, allMappings);

            return AIResponse.builder()
                .success(true)
                .action("delete")
                .mappingId(mappingId)
                .message("Ready to delete mapping")
                .explanation(aiResponse)
                .build();
        } catch (Exception e) {
            logger.error("Error deleting mapping: {}", e.getMessage(), e);
            return AIResponse.error("Failed to process deletion: " + e.getMessage());
        }
    }

    /**
     * Handle MOVE_MAPPING task
     */
    private AIResponse handleMoveMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            // Get all available namespaces
            var allNamespaces = namespaceRepository.findAll();
            String namespaceList = allNamespaces.stream()
                .map(com.simulator.model.Namespace::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("default");

            String context = buildDetailedContext(allMappings);
            String systemPrompt = String.format("""
                You are helping move an API endpoint from one workspace to another.

                CURRENT WORKSPACE: %s
                AVAILABLE WORKSPACES: %s

                %s

                User wants to: "%s"

                Your task:
                1. Identify WHICH endpoint to move (by name, path, or ID)
                2. Identify TARGET workspace/namespace (where to move it)

                Response format (JSON):
                {
                  "mappingId": "the-mapping-id-here",
                  "mappingName": "Endpoint Name",
                  "targetNamespace": "target-workspace-name",
                  "explanation": "Moving [Endpoint Name] from [current] to [target] workspace."
                }

                If target workspace is not specified or doesn't exist, ask user to clarify.
                """, request.getNamespace(), namespaceList, context, request.getUserPrompt());

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt(), request.getConversationHistory());

            // Strip markdown code blocks if present
            String cleanedResponse = aiResponse.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7); // Remove ```json
            } else if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3); // Remove ```
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            // Parse JSON response
            com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(cleanedResponse);
            String mappingId = jsonResponse.get("mappingId").asText();
            String targetNamespace = jsonResponse.get("targetNamespace").asText();
            String explanation = jsonResponse.get("explanation").asText();

            // Validate target namespace exists
            boolean namespaceExists = allNamespaces.stream()
                .anyMatch(ns -> ns.getName().equalsIgnoreCase(targetNamespace));

            if (!namespaceExists) {
                return AIResponse.error("Target workspace '" + targetNamespace + "' does not exist. Available: " + namespaceList);
            }

            // Move the mapping
            RequestMapping movedMapping = mappingService.moveMapping(mappingId, targetNamespace);

            return AIResponse.builder()
                .success(true)
                .action("move")
                .mappingId(mappingId)
                .message("Endpoint moved successfully")
                .explanation(explanation + "\n\n✅ **Move completed!**")
                .build();

        } catch (Exception e) {
            logger.error("Error moving mapping: {}", e.getMessage(), e);
            return AIResponse.error("Failed to move endpoint: " + e.getMessage());
        }
    }

    /**
     * Handle BULK_UPDATE_MAPPING task - update multiple endpoints at once
     */
    private AIResponse handleBulkUpdateMapping(AIRequest request, List<RequestMapping> allMappings) {
        try {
            String context = buildDetailedContext(allMappings);
            String systemPrompt = String.format("""
                You are performing a bulk update on multiple API endpoints.

                WORKSPACE: %s
                TOTAL ENDPOINTS: %d

                %s

                User wants to: "%s"

                Your task:
                1. Determine WHAT to update (e.g., add header, change priority, enable/disable)
                2. Determine WHICH endpoints to update:
                   - If user says "all endpoints" or just the update type → targetEndpoints="all"
                   - If user mentions specific keyword (e.g., "all memo endpoints") → targetEndpoints="subset"
                   - **CRITICAL**: For subset, include ALL endpoint IDs that match the criteria
                   - Match by name containing keyword (case-insensitive)
                3. Return JSON with update instructions

                Response format (JSON):
                {
                  "updateType": "add_header|remove_header|set_priority|enable|disable|add_delay|etc",
                  "targetEndpoints": "all|subset",
                  "endpointIds": ["id1", "id2", "id3"] (if subset, include ALL matching IDs),
                  "updateDetails": {
                    "headerName": "requestId",
                    "headerValue": "required",
                    "priority": 10,
                    etc...
                  },
                  "affectedCount": number,
                  "summary": "Brief description of what will be updated"
                }

                **IMPORTANT RULES:**
                - For "add header X exists" or "add required header X" → use headerValue="required"
                - When matching by keyword, search ALL endpoint names (case-insensitive)
                - Include EVERY matching endpoint ID in the endpointIds array
                - Double-check you haven't missed any matching endpoints

                Examples:
                - "add mandatory header requestId for all endpoints" →
                  {
                    "updateType": "add_header",
                    "targetEndpoints": "all",
                    "endpointIds": [],
                    "updateDetails": {"headerName": "requestId", "headerValue": "required"},
                    "affectedCount": %d,
                    "summary": "Adding required header 'requestId' to all %d endpoints"
                  }

                - "add requestId header exists for all memo endpoints" →
                  Look for ALL endpoints with "memo" in name (case-insensitive)
                  Find: "Memo Endpoint - Default Error" (ID: xxx), "Memo Endpoint - Success" (ID: yyy), "Memo Endpoint - Missing" (ID: zzz)
                  {
                    "updateType": "add_header",
                    "targetEndpoints": "subset",
                    "endpointIds": ["xxx", "yyy", "zzz"],
                    "updateDetails": {"headerName": "requestId", "headerValue": "required"},
                    "affectedCount": 3,
                    "summary": "Adding required header 'requestId' to all 3 memo endpoints"
                  }
                """, request.getNamespace(), allMappings.size(), context, request.getUserPrompt(),
                    allMappings.size(), allMappings.size());

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt(), request.getConversationHistory());

            // Strip markdown
            String cleanedResponse = aiResponse.trim();
            if (cleanedResponse.startsWith("```json")) cleanedResponse = cleanedResponse.substring(7);
            else if (cleanedResponse.startsWith("```")) cleanedResponse = cleanedResponse.substring(3);
            if (cleanedResponse.endsWith("```")) cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            cleanedResponse = cleanedResponse.trim();

            // Parse JSON
            com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(cleanedResponse);
            String updateType = jsonResponse.get("updateType").asText();
            String targetEndpoints = jsonResponse.get("targetEndpoints").asText();
            String summary = jsonResponse.get("summary").asText();

            // Determine which endpoints to update
            List<RequestMapping> endpointsToUpdate = new ArrayList<>();
            if ("all".equals(targetEndpoints)) {
                endpointsToUpdate.addAll(allMappings);
                logger.info("Target: ALL endpoints ({} total)", allMappings.size());
            } else {
                com.fasterxml.jackson.databind.JsonNode ids = jsonResponse.get("endpointIds");
                logger.info("Target: SUBSET of endpoints");
                logger.info("AI provided {} endpoint IDs to update", ids.size());

                for (com.fasterxml.jackson.databind.JsonNode idNode : ids) {
                    String id = idNode.asText();
                    logger.debug("Looking for endpoint with ID: {}", id);

                    allMappings.stream()
                        .filter(m -> m.getId().equals(id))
                        .findFirst()
                        .ifPresentOrElse(
                            mapping -> {
                                endpointsToUpdate.add(mapping);
                                logger.info("✓ Found endpoint to update: {} (ID: {})", mapping.getName(), mapping.getId());
                            },
                            () -> logger.warn("✗ Endpoint ID not found: {}", id)
                        );
                }

                logger.info("Total endpoints to update: {}", endpointsToUpdate.size());
            }

            // Apply updates based on type
            int updatedCount = 0;
            com.fasterxml.jackson.databind.JsonNode updateDetails = jsonResponse.get("updateDetails");

            for (RequestMapping mapping : endpointsToUpdate) {
                boolean modified = false;

                logger.info("Applying bulk update to endpoint: {} (ID: {})", mapping.getName(), mapping.getId());

                switch (updateType) {
                    case "add_header":
                        if (mapping.getRequest() == null) {
                            mapping.setRequest(new RequestMapping.RequestSpec());
                        }

                        String headerName = updateDetails.get("headerName").asText();
                        String headerValue = updateDetails.get("headerValue").asText();

                        // Determine if this is a header pattern (EXISTS, REGEX, etc.) or exact match
                        if ("required".equalsIgnoreCase(headerValue) || "exists".equalsIgnoreCase(headerValue)) {
                            // Add as header pattern with EXISTS match
                            if (mapping.getRequest().getHeaderPatterns() == null) {
                                mapping.getRequest().setHeaderPatterns(new java.util.HashMap<>());
                            }

                            RequestMapping.ParameterPattern pattern = new RequestMapping.ParameterPattern();
                            pattern.setMatchType(RequestMapping.ParameterPattern.MatchType.EXISTS);
                            pattern.setPattern(""); // Empty pattern for EXISTS
                            pattern.setIgnoreCase(false);

                            mapping.getRequest().getHeaderPatterns().put(headerName, pattern);
                            logger.info("Added header pattern '{}' with EXISTS match", headerName);
                        } else {
                            // Add as exact match header
                            if (mapping.getRequest().getHeaders() == null) {
                                mapping.getRequest().setHeaders(new java.util.HashMap<>());
                            }
                            mapping.getRequest().getHeaders().put(headerName, headerValue);
                            logger.info("Added exact match header '{}': '{}'", headerName, headerValue);
                        }

                        modified = true;
                        break;

                    case "set_priority":
                        int priority = updateDetails.get("priority").asInt();
                        mapping.setPriority(priority);
                        modified = true;
                        break;

                    case "enable":
                        mapping.setEnabled(true);
                        modified = true;
                        break;

                    case "disable":
                        mapping.setEnabled(false);
                        modified = true;
                        break;
                }

                if (modified) {
                    mapping.setUpdatedAt(java.time.Instant.now());
                    RequestMapping saved = mappingService.saveMapping(mapping, request.getNamespace());
                    logger.info("✓ Saved updated endpoint: {} (ID: {})", saved.getName(), saved.getId());
                    updatedCount++;
                }
            }

            logger.info("Bulk updated {} endpoints: {}", updatedCount, summary);

            return AIResponse.builder()
                .success(true)
                .action("bulk_update")
                .message(String.format("Updated %d endpoints", updatedCount))
                .explanation(summary + "\n\n✅ **Bulk update completed!** Updated " + updatedCount + " endpoint(s).")
                .build();

        } catch (Exception e) {
            logger.error("Error in bulk update: {}", e.getMessage(), e);
            return AIResponse.error("Failed to perform bulk update: " + e.getMessage());
        }
    }

    /**
     * Handle LIST_MAPPINGS task
     */
    private AIResponse handleListMappings(AIRequest request, List<RequestMapping> allMappings) {
        try {
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
                .message(String.format("Listed %d mappings", allMappings.size()))
                .explanation(listText.toString())
                .mappings(allMappings)
                .build();
        } catch (Exception e) {
            logger.error("Error listing mappings: {}", e.getMessage(), e);
            return AIResponse.error("Failed to list mappings: " + e.getMessage());
        }
    }

    /**
     * Extract mapping ID from AI response
     */
    private String extractMappingId(String aiResponse, List<RequestMapping> allMappings) {
        // Try to find mapping ID or name in the response
        for (RequestMapping mapping : allMappings) {
            if (aiResponse.contains(mapping.getId()) ||
                aiResponse.toLowerCase().contains(mapping.getName().toLowerCase())) {
                return mapping.getId();
            }
        }
        return null;
    }

    /**
     * Build system prompt for creating mappings
     */
    private String buildCreateMappingPrompt(String context, String namespace) {
        return String.format("""
            You are an expert API mapping assistant for the API Simulator application.

            Your task is to generate valid RequestMapping configurations based on user requests.

            WORKSPACE: %s

            %s

            RULES:
            1. Analyze existing endpoints to maintain consistent patterns
            2. Use appropriate HTTP methods (GET, POST, PUT, DELETE, PATCH)
            3. Set priority based on specificity (lower = higher priority, default = 5)
            4. Include realistic response bodies with proper JSON structure
            5. Add appropriate tags for categorization
            6. Use templating for dynamic values:
               - Path segments: {{request.pathSegments.[2]}}
               - Random UUID: {{randomValue type='UUID'}}
               - JSON body field: {{{jsonPath request.body '$.fieldName'}}} (use triple braces)
               - Entire request body: {{request.body}}
               - Current timestamp: {{now}}
            7. Set reasonable default delays (100-500ms)

            OUTPUT FORMAT (JSON only, no markdown):
            {
              "name": "Descriptive endpoint name",
              "endpointType": "REST" or "GRAPHQL",
              "priority": 5,
              "enabled": true,
              "tags": ["category", "operation"],
              "request": {
                "method": "GET|POST|PUT|DELETE|PATCH",
                "path": "/api/resource/{id}",
                "headers": {},
                "headerPatterns": {
                  "Header-Name": {
                    "matchType": "EXACT|REGEX|CONTAINS|EXISTS",
                    "pattern": "value",
                    "ignoreCase": false
                  }
                },
                "queryParams": {},
                "queryParamPatterns": {
                  "paramName": {
                    "matchType": "EXACT|REGEX|CONTAINS|EXISTS",
                    "pattern": "value",
                    "ignoreCase": false
                  }
                }
              },
              "response": {
                "status": 200,
                "headers": {"Content-Type": "application/json"},
                "body": "{\\"key\\": \\"value\\"}",
                "templatingEnabled": true
              },
              "delays": {
                "mode": "fixed",
                "fixedMs": 200,
                "errorRatePercent": 0
              }
            }

            DELAY CONFIGURATION OPTIONS:
            - Fixed delay:
              "delays": {
                "mode": "fixed",
                "fixedMs": 200,
                "errorRatePercent": 0
              }

            - Variable delay with error rate:
              "delays": {
                "mode": "variable",
                "variableMinMs": 100,
                "variableMaxMs": 500,
                "errorRatePercent": 15,
                "errorResponse": {
                  "status": 500,
                  "body": "{\\"error\\": \\"Service unavailable\\"}"
                }
              }

            - No delay (null or omit the delays field)

            CRITICAL: When modifying delays, NEVER use a plain number like "delays": 5000.
            ALWAYS use the complete object structure shown above with mode, fixedMs, etc.
            }

            IMPORTANT: headerPatterns and queryParamPatterns must be JSON objects (maps), NOT arrays.

            Return ONLY the JSON object, no additional text or markdown formatting.
            """, namespace, context);
    }

    /**
     * Parse OpenAI response to RequestMapping
     */
    private RequestMapping parseMappingFromResponse(String response) throws Exception {
        // Clean up response (remove markdown code blocks if present)
        String cleanJson = response.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        }
        if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        // Parse JSON to RequestMapping
        return objectMapper.readValue(cleanJson, RequestMapping.class);
    }

    // ========== ADMIN TASK HANDLERS ==========

    /**
     * Handle CREATE_NAMESPACE task
     */
    private AIResponse handleCreateNamespace(AIRequest request) {
        try {
            String systemPrompt = """
                You are an admin assistant for the API Simulator application.

                Your task is to extract namespace creation details from the user's request.

                Analyze the request and extract:
                - Namespace name (lowercase, no spaces, use hyphens)
                - Display name (user-friendly name)
                - Description

                Respond in JSON format:
                {
                  "name": "namespace-name",
                  "displayName": "Display Name",
                  "description": "Description of the namespace"
                }

                Return ONLY the JSON object.
                """;

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());

            // Parse the namespace data
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();
            com.simulator.model.Namespace namespace = objectMapper.readValue(cleanJson, com.simulator.model.Namespace.class);

            // Check if namespace already exists
            if (namespaceRepository.findByName(namespace.getName()).isPresent()) {
                return AIResponse.builder()
                    .success(false)
                    .message("Namespace already exists")
                    .explanation(String.format("❌ A namespace with name '%s' already exists. Please choose a different name.", namespace.getName()))
                    .build();
            }

            // Set defaults
            namespace.setActive(true);
            namespace.setCreatedAt(java.time.Instant.now());
            namespace.setOwner("admin"); // Set current user as owner

            // Save namespace
            com.simulator.model.Namespace saved = namespaceRepository.save(namespace);

            return AIResponse.builder()
                .success(true)
                .action("create_namespace")
                .message("Namespace created successfully")
                .explanation(String.format("✅ Created namespace: **%s** (%s)\n\n%s",
                    saved.getDisplayName() != null ? saved.getDisplayName() : saved.getName(),
                    saved.getName(),
                    saved.getDescription() != null ? saved.getDescription() : ""))
                .build();
        } catch (Exception e) {
            logger.error("Error creating namespace: {}", e.getMessage(), e);
            return AIResponse.error("Failed to create namespace: " + e.getMessage());
        }
    }

    /**
     * Handle CREATE_USER task
     */
    private AIResponse handleCreateUser(AIRequest request) {
        try {
            String systemPrompt = """
                You are an admin assistant for the API Simulator application.

                Extract user creation details from the request:
                - User ID (username, lowercase, no spaces)
                - First name
                - Last name
                - Email
                - Password (if not provided, suggest "password123")

                Respond in JSON:
                {
                  "userId": "username",
                  "firstName": "First",
                  "lastName": "Last",
                  "email": "user@example.com",
                  "password": "password123"
                }

                Return ONLY the JSON.
                """;

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();

            // Parse JSON to Map to extract password
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> userData = objectMapper.readValue(cleanJson, java.util.Map.class);

            String userId = (String) userData.get("userId");
            String firstName = (String) userData.get("firstName");
            String lastName = (String) userData.get("lastName");
            String email = (String) userData.get("email");
            String password = (String) userData.get("password");

            // Check if user exists
            if (userRepository.findByUserId(userId).isPresent()) {
                return AIResponse.builder()
                    .success(false)
                    .message("User already exists")
                    .explanation(String.format("❌ User '%s' already exists.", userId))
                    .build();
            }

            // Create user via UserService (handles password hashing)
            userService.createUser(userId, email, firstName, lastName, password);

            return AIResponse.builder()
                .success(true)
                .action("create_user")
                .message("User created successfully")
                .explanation(String.format("✅ Created user: **%s %s** (@%s)\n📧 %s\n🔑 Password: %s",
                    firstName, lastName, userId,
                    email != null ? email : "No email",
                    password))
                .build();
        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage(), e);
            return AIResponse.error("Failed to create user: " + e.getMessage());
        }
    }

    /**
     * Handle ENABLE_DISABLE_USER task
     */
    private AIResponse handleEnableDisableUser(AIRequest request) {
        try {
            String allUsers = userRepository.findAll().stream()
                .map(u -> String.format("- %s (@%s) - %s",
                    u.getFirstName() + " " + u.getLastName(),
                    u.getUserId(),
                    u.isActive() ? "Active" : "Disabled"))
                .collect(java.util.stream.Collectors.joining("\n"));

            String systemPrompt = String.format("""
                You are an admin assistant.

                Users in system:
                %s

                Determine from the user's request:
                1. Which user to enable/disable
                2. The action (enable or disable)

                Respond in JSON:
                {
                  "userId": "username",
                  "action": "enable" or "disable",
                  "reason": "brief reason"
                }

                Return ONLY JSON.
                """, allUsers);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();
            var actionData = objectMapper.readValue(cleanJson, java.util.Map.class);

            String userId = (String) actionData.get("userId");
            String action = (String) actionData.get("action");

            var userOpt = userRepository.findByUserId(userId);
            if (userOpt.isEmpty()) {
                return AIResponse.error("User not found: " + userId);
            }

            com.simulator.model.UserProfile user = userOpt.get();
            boolean enable = "enable".equalsIgnoreCase(action);
            user.setActive(enable);
            userRepository.save(user);

            return AIResponse.builder()
                .success(true)
                .action("user_status")
                .message(String.format("User %s", enable ? "enabled" : "disabled"))
                .explanation(String.format("%s User **%s** (@%s) has been %s",
                    enable ? "✅" : "🚫",
                    user.getFirstName() + " " + user.getLastName(),
                    user.getUserId(),
                    enable ? "enabled" : "disabled"))
                .build();
        } catch (Exception e) {
            logger.error("Error enabling/disabling user: {}", e.getMessage(), e);
            return AIResponse.error("Failed to update user status: " + e.getMessage());
        }
    }

    /**
     * Handle ASSIGN_NAMESPACE task
     */
    private AIResponse handleAssignNamespace(AIRequest request) {
        try {
            String namespaces = namespaceRepository.findAll().stream()
                .map(ns -> String.format("- %s (%s)", ns.getDisplayName() != null ? ns.getDisplayName() : ns.getName(), ns.getName()))
                .collect(java.util.stream.Collectors.joining("\n"));

            String users = userRepository.findAll().stream()
                .map(u -> String.format("- %s (@%s)", u.getFirstName() + " " + u.getLastName(), u.getUserId()))
                .collect(java.util.stream.Collectors.joining("\n"));

            String systemPrompt = String.format("""
                Namespaces:
                %s

                Users:
                %s

                Extract:
                - User ID to assign
                - Namespace name to assign

                JSON format:
                {
                  "userId": "username",
                  "namespaceName": "namespace-name"
                }
                """, namespaces, users);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();
            var data = objectMapper.readValue(cleanJson, java.util.Map.class);

            String userId = (String) data.get("userId");
            String namespaceName = (String) data.get("namespaceName");

            var userOpt = userRepository.findByUserId(userId);
            var namespaceOpt = namespaceRepository.findByName(namespaceName);

            if (userOpt.isEmpty()) {
                return AIResponse.error("User not found: " + userId);
            }
            if (namespaceOpt.isEmpty()) {
                return AIResponse.error("Namespace not found: " + namespaceName);
            }

            com.simulator.model.UserProfile user = userOpt.get();
            com.simulator.model.Namespace namespace = namespaceOpt.get();

            // Add user to namespace members
            if (!namespace.getMembers().contains(userId)) {
                namespace.getMembers().add(userId);
                namespaceRepository.save(namespace);
            }

            // Add namespace to user's namespaces
            if (!user.getNamespaces().contains(namespaceName)) {
                user.getNamespaces().add(namespaceName);
                userRepository.save(user);
            }

            return AIResponse.builder()
                .success(true)
                .action("assign_namespace")
                .message("Namespace assigned")
                .explanation(String.format("✅ Assigned **%s** (@%s) to namespace **%s**",
                    user.getFirstName() + " " + user.getLastName(),
                    user.getUserId(),
                    namespace.getDisplayName() != null ? namespace.getDisplayName() : namespace.getName()))
                .build();
        } catch (Exception e) {
            logger.error("Error assigning namespace: {}", e.getMessage(), e);
            return AIResponse.error("Failed to assign namespace: " + e.getMessage());
        }
    }

    /**
     * Handle LIST_NAMESPACES task
     */
    private AIResponse handleListNamespaces(AIRequest request) {
        try {
            var namespaces = namespaceRepository.findAll();
            if (namespaces.isEmpty()) {
                return AIResponse.builder()
                    .success(true)
                    .message("No namespaces found")
                    .explanation("📋 No namespaces configured in the system.")
                    .build();
            }

            StringBuilder list = new StringBuilder("📁 **Namespaces:**\n\n");
            for (var ns : namespaces) {
                list.append(String.format("**%s** (`%s`)\n",
                    ns.getDisplayName() != null ? ns.getDisplayName() : ns.getName(),
                    ns.getName()));
                if (ns.getDescription() != null) {
                    list.append(String.format("   └─ %s\n", ns.getDescription()));
                }
                list.append(String.format("   └─ Members: %d | %s\n\n",
                    ns.getMembers() != null ? ns.getMembers().size() : 0,
                    ns.isActive() ? "✅ Active" : "❌ Inactive"));
            }

            return AIResponse.builder()
                .success(true)
                .action("list_namespaces")
                .message(String.format("Listed %d namespaces", namespaces.size()))
                .explanation(list.toString())
                .build();
        } catch (Exception e) {
            logger.error("Error listing namespaces: {}", e.getMessage(), e);
            return AIResponse.error("Failed to list namespaces: " + e.getMessage());
        }
    }

    /**
     * Handle LIST_USERS task
     */
    private AIResponse handleListUsers(AIRequest request) {
        try {
            var users = userRepository.findAll();
            if (users.isEmpty()) {
                return AIResponse.builder()
                    .success(true)
                    .message("No users found")
                    .explanation("👥 No users in the system.")
                    .build();
            }

            StringBuilder list = new StringBuilder("👥 **Users:**\n\n");
            for (var user : users) {
                list.append(String.format("**%s %s** (@%s)\n",
                    user.getFirstName(), user.getLastName(), user.getUserId()));
                if (user.getEmail() != null) {
                    list.append(String.format("   └─ 📧 %s\n", user.getEmail()));
                }
                list.append(String.format("   └─ Namespaces: %d | %s\n\n",
                    user.getNamespaces() != null ? user.getNamespaces().size() : 0,
                    user.isActive() ? "✅ Active" : "❌ Disabled"));
            }

            return AIResponse.builder()
                .success(true)
                .action("list_users")
                .message(String.format("Listed %d users", users.size()))
                .explanation(list.toString())
                .build();
        } catch (Exception e) {
            logger.error("Error listing users: {}", e.getMessage(), e);
            return AIResponse.error("Failed to list users: " + e.getMessage());
        }
    }

    /**
     * Handle MODIFY_NAMESPACE task
     */
    private AIResponse handleModifyNamespace(AIRequest request) {
        try {
            var namespaces = namespaceRepository.findAll();
            String namespacesStr = namespaces.stream()
                .map(ns -> String.format("- %s (%s)", ns.getDisplayName() != null ? ns.getDisplayName() : ns.getName(), ns.getName()))
                .collect(java.util.stream.Collectors.joining("\n"));

            String systemPrompt = String.format("""
                Namespaces:
                %s

                Extract from the user's request:
                - Which namespace to modify (by name)
                - What to change (displayName, description, active status)
                - New values

                JSON format:
                {
                  "namespaceName": "namespace-name",
                  "displayName": "New Display Name" (optional),
                  "description": "New description" (optional),
                  "active": true/false (optional)
                }
                """, namespacesStr);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();
            var data = objectMapper.readValue(cleanJson, java.util.Map.class);

            String namespaceName = (String) data.get("namespaceName");
            var namespaceOpt = namespaceRepository.findByName(namespaceName);

            if (namespaceOpt.isEmpty()) {
                return AIResponse.error("Namespace not found: " + namespaceName);
            }

            com.simulator.model.Namespace namespace = namespaceOpt.get();

            // Update fields if provided
            if (data.containsKey("displayName")) {
                namespace.setDisplayName((String) data.get("displayName"));
            }
            if (data.containsKey("description")) {
                namespace.setDescription((String) data.get("description"));
            }
            if (data.containsKey("active")) {
                namespace.setActive((Boolean) data.get("active"));
            }

            namespaceRepository.save(namespace);

            return AIResponse.builder()
                .success(true)
                .action("modify_namespace")
                .message("Namespace updated")
                .explanation(String.format("✅ Updated namespace **%s**",
                    namespace.getDisplayName() != null ? namespace.getDisplayName() : namespace.getName()))
                .build();
        } catch (Exception e) {
            logger.error("Error modifying namespace: {}", e.getMessage(), e);
            return AIResponse.error("Failed to modify namespace: " + e.getMessage());
        }
    }

    /**
     * Handle DELETE_NAMESPACE task
     */
    private AIResponse handleDeleteNamespace(AIRequest request) {
        try {
            var namespaces = namespaceRepository.findAll();
            String namespacesStr = namespaces.stream()
                .map(ns -> String.format("- %s (%s)", ns.getDisplayName() != null ? ns.getDisplayName() : ns.getName(), ns.getName()))
                .collect(java.util.stream.Collectors.joining("\n"));

            String systemPrompt = String.format("""
                Namespaces:
                %s

                Extract which namespace to delete from the user's request.

                JSON format:
                {
                  "namespaceName": "namespace-name"
                }
                """, namespacesStr);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();
            var data = objectMapper.readValue(cleanJson, java.util.Map.class);

            String namespaceName = (String) data.get("namespaceName");
            var namespaceOpt = namespaceRepository.findByName(namespaceName);

            if (namespaceOpt.isEmpty()) {
                return AIResponse.error("Namespace not found: " + namespaceName);
            }

            com.simulator.model.Namespace namespace = namespaceOpt.get();

            // Check if namespace has mappings
            List<RequestMapping> mappings = mappingService.getAllMappings(namespaceName);
            if (!mappings.isEmpty()) {
                return AIResponse.builder()
                    .success(false)
                    .message("Cannot delete namespace")
                    .explanation(String.format("❌ Cannot delete namespace **%s** because it contains %d API mappings. Please delete all mappings first.",
                        namespace.getDisplayName() != null ? namespace.getDisplayName() : namespace.getName(),
                        mappings.size()))
                    .build();
            }

            return AIResponse.builder()
                .success(true)
                .action("delete_namespace")
                .mappingId(namespaceName)  // Use mappingId field to store namespace name for frontend
                .message("Ready to delete namespace")
                .explanation(String.format("🗑️ Ready to delete namespace: **%s** (%s)\n\n⚠️ This action cannot be undone.",
                    namespace.getDisplayName() != null ? namespace.getDisplayName() : namespace.getName(),
                    namespace.getName()))
                .build();
        } catch (Exception e) {
            logger.error("Error deleting namespace: {}", e.getMessage(), e);
            return AIResponse.error("Failed to delete namespace: " + e.getMessage());
        }
    }

    /**
     * Handle MODIFY_USER task
     */
    private AIResponse handleModifyUser(AIRequest request) {
        try {
            var users = userRepository.findAll();
            String usersStr = users.stream()
                .map(u -> String.format("- %s %s (@%s)", u.getFirstName(), u.getLastName(), u.getUserId()))
                .collect(java.util.stream.Collectors.joining("\n"));

            String systemPrompt = String.format("""
                Users:
                %s

                Extract from the user's request:
                - Which user to modify (by userId)
                - What to change (firstName, lastName, email, password)
                - New values

                JSON format:
                {
                  "userId": "username",
                  "firstName": "New First" (optional),
                  "lastName": "New Last" (optional),
                  "email": "new@example.com" (optional),
                  "password": "newpassword" (optional)
                }
                """, usersStr);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();
            var data = objectMapper.readValue(cleanJson, java.util.Map.class);

            String userId = (String) data.get("userId");
            var userOpt = userRepository.findByUserId(userId);

            if (userOpt.isEmpty()) {
                return AIResponse.error("User not found: " + userId);
            }

            com.simulator.model.UserProfile user = userOpt.get();

            // Update fields if provided
            boolean updated = false;
            if (data.containsKey("firstName")) {
                user.setFirstName((String) data.get("firstName"));
                updated = true;
            }
            if (data.containsKey("lastName")) {
                user.setLastName((String) data.get("lastName"));
                updated = true;
            }
            if (data.containsKey("email")) {
                user.setEmail((String) data.get("email"));
                updated = true;
            }
            if (data.containsKey("password")) {
                // Hash password using UserService
                String password = (String) data.get("password");
                user.setPasswordHash(userService.hashPassword(password));
                updated = true;
            }

            if (updated) {
                userRepository.save(user);
            }

            return AIResponse.builder()
                .success(true)
                .action("modify_user")
                .message("User updated")
                .explanation(String.format("✅ Updated user **%s %s** (@%s)",
                    user.getFirstName(), user.getLastName(), user.getUserId()))
                .build();
        } catch (Exception e) {
            logger.error("Error modifying user: {}", e.getMessage(), e);
            return AIResponse.error("Failed to modify user: " + e.getMessage());
        }
    }

    /**
     * Handle DELETE_USER task
     */
    private AIResponse handleDeleteUser(AIRequest request) {
        try {
            var users = userRepository.findAll();
            String usersStr = users.stream()
                .map(u -> String.format("- %s %s (@%s)", u.getFirstName(), u.getLastName(), u.getUserId()))
                .collect(java.util.stream.Collectors.joining("\n"));

            String systemPrompt = String.format("""
                Users:
                %s

                Extract which user to delete from the user's request.

                JSON format:
                {
                  "userId": "username"
                }
                """, usersStr);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt());
            String cleanJson = aiResponse.trim().replaceAll("```json|```", "").trim();
            var data = objectMapper.readValue(cleanJson, java.util.Map.class);

            String userId = (String) data.get("userId");
            var userOpt = userRepository.findByUserId(userId);

            if (userOpt.isEmpty()) {
                return AIResponse.error("User not found: " + userId);
            }

            com.simulator.model.UserProfile user = userOpt.get();

            // Prevent deleting admin user
            if ("admin".equals(userId)) {
                return AIResponse.builder()
                    .success(false)
                    .message("Cannot delete admin")
                    .explanation("❌ Cannot delete the admin user account.")
                    .build();
            }

            return AIResponse.builder()
                .success(true)
                .action("delete_user")
                .mappingId(userId)  // Use mappingId field to store userId for frontend
                .message("Ready to delete user")
                .explanation(String.format("🗑️ Ready to delete user: **%s %s** (@%s)\n\n⚠️ This action cannot be undone.",
                    user.getFirstName(), user.getLastName(), user.getUserId()))
                .build();
        } catch (Exception e) {
            logger.error("Error deleting user: {}", e.getMessage(), e);
            return AIResponse.error("Failed to delete user: " + e.getMessage());
        }
    }

    // ========== ASK MODE HANDLERS ==========

    /**
     * Handle ANALYZE_PAYLOAD task - analyze request payload and find matching endpoints
     */
    private AIResponse handleAnalyzePayload(AIRequest request, List<RequestMapping> allMappings) {
        try {
            logger.info("Handling ANALYZE_PAYLOAD task");

            // First, identify the most relevant endpoint(s)
            RequestMapping targetMapping = identifyTargetEndpoint(request.getUserPrompt(), allMappings);

            // For followup questions, try to extract endpoint from conversation history
            boolean isFollowupQuestion = request.getConversationHistory() != null && !request.getConversationHistory().isEmpty();
            if (targetMapping == null && isFollowupQuestion) {
                logger.info("No endpoint identified in current prompt, checking conversation history for context");
                targetMapping = extractEndpointFromHistory(request.getConversationHistory(), allMappings);
                if (targetMapping != null) {
                    logger.info("Endpoint identified from conversation history: {} (ID: {})", targetMapping.getName(), targetMapping.getId());
                }
            }

            String context;
            if (targetMapping != null) {
                logger.info("Target endpoint identified: {} (ID: {})", targetMapping.getName(), targetMapping.getId());
                logger.info("Endpoint path: {} {}", targetMapping.getRequest().getMethod(), targetMapping.getRequest().getPath());

                // For followup questions, include both deep context of target AND summary of all workspace endpoints
                if (isFollowupQuestion) {
                    context = buildDeepEndpointContext(targetMapping) + "\n\n" +
                              "=== ALL WORKSPACE ENDPOINTS (for context) ===\n\n" +
                              buildDetailedContext(allMappings);
                    logger.info("Built combined context: deep endpoint + all workspace endpoints");
                } else {
                    context = buildDeepEndpointContext(targetMapping);
                }
                logger.info("Built context ({} characters)", context.length());
            } else {
                logger.warn("No specific endpoint identified, using general context of all workspace endpoints");
                // Use general context if no specific endpoint identified
                context = buildDetailedContext(allMappings);
                logger.info("Built general context for {} mappings", allMappings.size());
            }

            String systemPrompt = String.format("""
                You are an API endpoint debugging expert for the API Simulator application.

                WORKSPACE: %s

                %s

                **Instructions:**
                1. Identify the endpoint from user's message
                2. Extract request payload fields from user's message
                3. Compare with response template variables
                4. Detect mismatches (case, typos, missing fields, syntax errors)

                **Response Style - BE CONCISE:**
                - Start with ONE LINE summary of the issue
                - Show ONLY the specific mismatch
                - Provide the fix (corrected field name or template)
                - Optionally add a brief test command

                **Format (Keep it SHORT):**
                ```
                ❌ **Issue**: [One line problem description]

                **Fix**:
                Option 1: Change request field to `customerId` (camelCase)
                Option 2: Change template to {{{jsonPath request.body '$.customerid'}}}

                **Test**:
                curl -X POST http://localhost:9999/path -d '{"customerId":"123"}'
                ```

                **IMPORTANT:**
                - DO NOT provide lengthy explanations unless user asks "explain more" or "why"
                - DO NOT repeat endpoint configuration (it's in context)
                - DO NOT list all fields, only the problematic one
                - BE DIRECT and ACTIONABLE
                - If user's previous message asked for more details, THEN provide full explanation
                - Use conversation history to determine if user wants brief or detailed response

                **Conversation Context:**
                - Previous messages are provided in conversation history
                - If user says "explain", "why", "more details", provide detailed breakdown
                - Otherwise, stay concise
                """, request.getNamespace(), context);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt(), request.getConversationHistory());

            logger.info("ANALYZE_PAYLOAD completed successfully");

            return AIResponse.builder()
                .success(true)
                .message("Deep payload analysis complete")
                .explanation(aiResponse)
                .mappingId(targetMapping != null ? targetMapping.getId() : null)
                .build();
        } catch (Exception e) {
            logger.error("Error analyzing payload: {}", e.getMessage(), e);
            return AIResponse.error("Failed to analyze payload: " + e.getMessage());
        }
    }

    /**
     * Handle ANALYZE_CURL task - parse curl command and check endpoint matching
     */
    private AIResponse handleAnalyzeCurl(AIRequest request, List<RequestMapping> allMappings) {
        try {
            logger.info("Handling ANALYZE_CURL task");

            // Extract path from curl to identify endpoint
            RequestMapping targetMapping = identifyTargetEndpoint(request.getUserPrompt(), allMappings);

            // For followup questions, try to extract endpoint from conversation history
            boolean isFollowupQuestion = request.getConversationHistory() != null && !request.getConversationHistory().isEmpty();
            if (targetMapping == null && isFollowupQuestion) {
                logger.info("No endpoint identified in current prompt, checking conversation history for context");
                targetMapping = extractEndpointFromHistory(request.getConversationHistory(), allMappings);
                if (targetMapping != null) {
                    logger.info("Endpoint identified from conversation history: {} (ID: {})", targetMapping.getName(), targetMapping.getId());
                }
            }

            String context;
            if (targetMapping != null) {
                logger.info("Target endpoint identified from curl: {} (ID: {})", targetMapping.getName(), targetMapping.getId());

                // For followup questions, include both deep context of target AND summary of all workspace endpoints
                if (isFollowupQuestion) {
                    context = buildDeepEndpointContext(targetMapping) + "\n\n" +
                              "=== ALL WORKSPACE ENDPOINTS (for context) ===\n\n" +
                              buildDetailedContext(allMappings);
                    logger.info("Built combined context: deep endpoint + all workspace endpoints");
                } else {
                    context = buildDeepEndpointContext(targetMapping);
                }
                logger.info("Built context ({} characters)", context.length());
            } else {
                logger.warn("No specific endpoint identified from curl, using general context of all workspace endpoints");
                context = buildDetailedContext(allMappings);
                logger.info("Built general context for {} mappings", allMappings.size());
            }

            String systemPrompt = String.format("""
                You are a curl command debugging expert for the API Simulator application.

                WORKSPACE: %s

                %s

                **Your Task:**
                1. Extract from curl: method, path, headers, request body JSON
                2. Find the EXACT matching endpoint by path and method
                3. Validate ONLY the configured/required fields in endpoint's bodyPatterns:
                   - **CRITICAL**: Look at the "Body Patterns" section in endpoint config above
                   - Check ONLY if those specific fields exist in request JSON
                   - **IGNORE** all other fields in request - they are allowed and don't matter
                   - Extra headers are allowed (don't validate them)
                   - Extra query params are allowed (don't validate them)
                   - Extra JSON fields are allowed (don't validate them)

                4. Identify ONLY missing required fields from bodyPatterns (if any)

                **VALIDATION RULES:**
                - If endpoint has NO body patterns configured → ✅ ANY request body is valid
                - If endpoint has body patterns (e.g., "$.account_number") → ONLY check if that field exists
                - **DO NOT** validate fields NOT in bodyPatterns
                - **DO NOT** complain about extra fields (amount, memo, etc.) unless they're in bodyPatterns
                - Only flag MISSING fields that are in bodyPatterns

                **Example:**
                Endpoint bodyPatterns: [{"matchType": "JSONPATH", "expr": "$.account_number"}]
                Request: {"account_number": "123", "amount": 100, "memo": "test"}
                Result: ✅ **Matches** (account_number is present, other fields don't matter)

                **Response Style - BE CONCISE:**
                - If all bodyPattern fields present: "✅ **Matches**: [Endpoint Name]"
                - If missing bodyPattern fields: Show what's MISSING + corrected curl

                **IMPORTANT:**
                - ONLY validate fields from "Body Patterns (must match)" section
                - IGNORE all other request attributes/fields
                - DO NOT use field names from conversation history
                - DO NOT validate fields not in bodyPatterns
                - Focus ONLY on what's in bodyPatterns config

                **Format (Keep it SHORT):**
                ```
                ✅ **Matches**: [Endpoint Name] - Request will succeed

                OR

                ❌ **Missing**: [field1, field2] (from bodyPatterns)

                **Corrected Curl**:
                curl -X POST http://localhost:9999/path \\
                  -H "Content-Type: application/json" \\
                  -d '{"required_field":"value", "any_extra_fields":"are_perfectly_fine"}'
                ```

                **Conversation Context:**
                - If user asks "explain", "why", "how" - provide full analysis
                - Otherwise, be brief and direct
                """, request.getNamespace(), context);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt(), request.getConversationHistory());

            logger.info("ANALYZE_CURL completed successfully");

            return AIResponse.builder()
                .success(true)
                .message("Deep curl analysis complete")
                .explanation(aiResponse)
                .mappingId(targetMapping != null ? targetMapping.getId() : null)
                .build();
        } catch (Exception e) {
            logger.error("Error analyzing curl: {}", e.getMessage(), e);
            return AIResponse.error("Failed to analyze curl command: " + e.getMessage());
        }
    }

    /**
     * Handle CHECK_ENDPOINT_MATCH task - check endpoint configuration against payload
     */
    private AIResponse handleCheckEndpointMatch(AIRequest request, List<RequestMapping> allMappings) {
        try {
            logger.info("Handling CHECK_ENDPOINT_MATCH task");

            // Identify the specific endpoint user is asking about
            RequestMapping targetMapping = identifyTargetEndpoint(request.getUserPrompt(), allMappings);

            // For followup questions, try to extract endpoint from conversation history
            boolean isFollowupQuestion = request.getConversationHistory() != null && !request.getConversationHistory().isEmpty();
            if (targetMapping == null && isFollowupQuestion) {
                logger.info("No endpoint identified in current prompt, checking conversation history for context");
                targetMapping = extractEndpointFromHistory(request.getConversationHistory(), allMappings);
                if (targetMapping != null) {
                    logger.info("Endpoint identified from conversation history: {} (ID: {})", targetMapping.getName(), targetMapping.getId());
                }
            }

            String context;
            if (targetMapping != null) {
                logger.info("Target endpoint identified: {} (ID: {})", targetMapping.getName(), targetMapping.getId());

                // For followup questions, include both deep context of target AND summary of all workspace endpoints
                if (isFollowupQuestion) {
                    context = buildDeepEndpointContext(targetMapping) + "\n\n" +
                              "=== ALL WORKSPACE ENDPOINTS (for context) ===\n\n" +
                              buildDetailedContext(allMappings);
                    logger.info("Built combined context: deep endpoint + all workspace endpoints");
                } else {
                    context = buildDeepEndpointContext(targetMapping);
                }
                logger.info("Built context ({} characters)", context.length());
            } else {
                logger.warn("No specific endpoint identified, using general context of all workspace endpoints");
                context = buildDetailedContext(allMappings);
                logger.info("Built general context for {} mappings", allMappings.size());
            }

            String systemPrompt = String.format("""
                You are an endpoint validation expert for the API Simulator application.

                WORKSPACE: %s

                %s

                **Instructions:**
                1. Identify the endpoint from user's message
                2. Extract user's request details (payload, headers, etc.)
                3. Find the specific issue (field mismatch, pattern issue, etc.)

                **Response Style - BE CONCISE:**
                - ONE LINE stating the exact problem
                - Show ONLY the problematic field
                - Provide 2 fix options (change request OR change template)
                - Keep it short and actionable

                **Format (Keep it SHORT):**
                ```
                ❌ **Issue**: Field name mismatch - request has `customerid` but template expects `customerId`

                **Fix**:
                Option 1: Change request → `"customerId": "123"`
                Option 2: Change template → {{{jsonPath request.body '$.customerid'}}}

                **Test**:
                curl -X POST http://localhost:9999/account -d '{"customerId":"123"}'
                ```

                **IMPORTANT:**
                - DO NOT list all fields, ONLY the problematic one
                - DO NOT explain Handlebars/JSONPath unless asked
                - DO NOT provide full config breakdown (it's in context)
                - BE DIRECT: "Change X to Y"
                - Check conversation history for context
                - If user said "explain", "why", "how does this work" - THEN provide details
                - Otherwise, just the pinpointed issue and fix

                **Conversation Context:**
                - Previous messages show if user wants brief or detailed responses
                - If user asks for explanation, provide full analysis with examples
                - If this is first message or no "explain" keywords, be concise
                """, request.getNamespace(), context);

            String aiResponse = callOpenAI(systemPrompt, request.getUserPrompt(), request.getConversationHistory());

            logger.info("CHECK_ENDPOINT_MATCH completed successfully");

            return AIResponse.builder()
                .success(true)
                .message("Deep endpoint validation complete")
                .explanation(aiResponse)
                .mappingId(targetMapping != null ? targetMapping.getId() : null)
                .build();
        } catch (Exception e) {
            logger.error("Error checking endpoint match: {}", e.getMessage(), e);
            return AIResponse.error("Failed to check endpoint match: " + e.getMessage());
        }
    }

    /**
     * Identify the target endpoint from user's prompt
     */
    private RequestMapping identifyTargetEndpoint(String userPrompt, List<RequestMapping> allMappings) {
        logger.debug("Attempting to identify target endpoint from user prompt");

        if (allMappings == null || allMappings.isEmpty()) {
            logger.debug("No mappings available to identify");
            return null;
        }

        logger.debug("Searching through {} mappings", allMappings.size());
        String lowerPrompt = userPrompt.toLowerCase();

        // Try to find endpoint by exact path match
        for (RequestMapping mapping : allMappings) {
            if (mapping.getRequest() != null && mapping.getRequest().getPath() != null) {
                String path = mapping.getRequest().getPath();
                if (lowerPrompt.contains(path.toLowerCase())) {
                    logger.info("Endpoint identified by path match: {} -> {}", path, mapping.getName());
                    return mapping;
                }
            }
        }

        // Try to find by name
        for (RequestMapping mapping : allMappings) {
            if (mapping.getName() != null && lowerPrompt.contains(mapping.getName().toLowerCase())) {
                logger.info("Endpoint identified by name match: {}", mapping.getName());
                return mapping;
            }
        }

        // Try to find by ID
        for (RequestMapping mapping : allMappings) {
            if (mapping.getId() != null && lowerPrompt.contains(mapping.getId())) {
                logger.info("Endpoint identified by ID match: {}", mapping.getId());
                return mapping;
            }
        }

        // Return first mapping as fallback if only one exists
        if (allMappings.size() == 1) {
            logger.info("Only one mapping exists, using as default: {}", allMappings.get(0).getName());
            return allMappings.get(0);
        }

        logger.debug("No specific endpoint could be identified");
        return null;
    }

    /**
     * Build deep context with complete endpoint configuration
     */
    private String buildDeepEndpointContext(RequestMapping mapping) {
        if (mapping == null) {
            return "No endpoint configuration available.";
        }

        try {
            StringBuilder context = new StringBuilder();
            context.append("=== COMPLETE ENDPOINT CONFIGURATION ===\n\n");

            // Basic Info
            context.append(String.format("**Endpoint Name**: %s\n", mapping.getName()));
            context.append(String.format("**ID**: %s\n", mapping.getId()));
            context.append(String.format("**Priority**: %d (lower = higher precedence)\n",
                mapping.getPriority() != null ? mapping.getPriority() : 5));
            context.append(String.format("**Enabled**: %s\n", mapping.getEnabled()));
            context.append(String.format("**Type**: %s\n\n",
                mapping.getEndpointType() != null ? mapping.getEndpointType() : "REST"));

            // Request Configuration
            if (mapping.getRequest() != null) {
                var req = mapping.getRequest();
                context.append("**REQUEST CONFIGURATION:**\n");
                context.append(String.format("  - Method: %s\n", req.getMethod()));
                context.append(String.format("  - Path: %s\n", req.getPath()));

                if (req.getPathPattern() != null) {
                    context.append(String.format("  - Path Pattern Type: %s\n", req.getPathPattern().getMatchType()));
                    context.append(String.format("  - Path Pattern: %s\n", req.getPathPattern().getPattern()));
                }

                if (req.getHeaders() != null && !req.getHeaders().isEmpty()) {
                    context.append("  - Required Headers:\n");
                    req.getHeaders().forEach((key, value) ->
                        context.append(String.format("    * %s: %s\n", key, value)));
                }

                if (req.getHeaderPatterns() != null && !req.getHeaderPatterns().isEmpty()) {
                    context.append("  - Header Patterns (must match):\n");
                    req.getHeaderPatterns().forEach((headerName, pattern) ->
                        context.append(String.format("    * %s: matchType=%s, pattern=%s\n",
                            headerName, pattern.getMatchType(), pattern.getPattern())));
                }

                if (req.getQueryParams() != null && !req.getQueryParams().isEmpty()) {
                    context.append("  - Required Query Params:\n");
                    req.getQueryParams().forEach((key, value) ->
                        context.append(String.format("    * %s: %s\n", key, value)));
                }

                if (req.getQueryParamPatterns() != null && !req.getQueryParamPatterns().isEmpty()) {
                    context.append("  - Query Param Patterns (must match):\n");
                    req.getQueryParamPatterns().forEach((paramName, pattern) ->
                        context.append(String.format("    * %s: matchType=%s, pattern=%s\n",
                            paramName, pattern.getMatchType(), pattern.getPattern())));
                }

                if (req.getBodyPatterns() != null && !req.getBodyPatterns().isEmpty()) {
                    context.append("  - Body Patterns (must match):\n");
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
                context.append(String.format("  - Templating Enabled: %s\n", resp.getTemplatingEnabled()));

                if (resp.getHeaders() != null && !resp.getHeaders().isEmpty()) {
                    context.append("  - Response Headers:\n");
                    resp.getHeaders().forEach((key, value) ->
                        context.append(String.format("    * %s: %s\n", key, value)));
                }

                if (resp.getBody() != null) {
                    context.append("  - Response Body Template:\n");
                    context.append("```json\n");
                    context.append(resp.getBody());
                    context.append("\n```\n");

                    // Analyze template variables
                    context.append("\n  - Template Variables Analysis:\n");
                    String body = resp.getBody();

                    // Find Handlebars variables {{...}}
                    java.util.regex.Pattern handlebarsPattern = java.util.regex.Pattern.compile("\\{\\{([^}]+)\\}\\}");
                    java.util.regex.Matcher matcher = handlebarsPattern.matcher(body);
                    java.util.Set<String> variables = new java.util.HashSet<>();
                    while (matcher.find()) {
                        variables.add(matcher.group(1).trim());
                    }
                    if (!variables.isEmpty()) {
                        context.append("    * Handlebars variables: ");
                        context.append(String.join(", ", variables));
                        context.append("\n");
                    }

                    // Find JSONPath expressions {{{jsonPath...}}}
                    java.util.regex.Pattern jsonPathPattern = java.util.regex.Pattern.compile(
                        "\\{\\{\\{jsonPath\\s+request\\.body\\s+'\\$\\.([^']+)'\\}\\}\\}");
                    matcher = jsonPathPattern.matcher(body);
                    java.util.Set<String> jsonPaths = new java.util.HashSet<>();
                    while (matcher.find()) {
                        jsonPaths.add(matcher.group(1));
                    }
                    if (!jsonPaths.isEmpty()) {
                        context.append("    * JSONPath fields from request body: ");
                        context.append(String.join(", ", jsonPaths));
                        context.append("\n");
                    }
                }

                context.append("\n");
            }

            // Delays Configuration
            if (mapping.getDelays() != null) {
                var delays = mapping.getDelays();
                context.append("**DELAY CONFIGURATION:**\n");
                context.append(String.format("  - Mode: %s\n", delays.getMode()));
                if ("fixed".equals(delays.getMode())) {
                    context.append(String.format("  - Fixed Delay: %dms\n", delays.getFixedMs()));
                } else if ("variable".equals(delays.getMode())) {
                    context.append(String.format("  - Variable Delay: %dms - %dms\n",
                        delays.getVariableMinMs(), delays.getVariableMaxMs()));
                }
                if (delays.getErrorRatePercent() != null && delays.getErrorRatePercent() > 0) {
                    context.append(String.format("  - Error Rate: %d%%\n", delays.getErrorRatePercent()));
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

    /**
     * Extract endpoint from conversation history
     * Looks for endpoint mentions (path, name, ID) in previous messages
     */
    private RequestMapping extractEndpointFromHistory(List<AIRequest.ChatMessage> conversationHistory, List<RequestMapping> allMappings) {
        logger.debug("Extracting endpoint from conversation history ({} messages)", conversationHistory.size());

        if (conversationHistory == null || conversationHistory.isEmpty() || allMappings == null || allMappings.isEmpty()) {
            return null;
        }

        // Search through conversation history (most recent first)
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            AIRequest.ChatMessage msg = conversationHistory.get(i);
            String content = msg.getContent().toLowerCase();

            // Try to find endpoint by path
            for (RequestMapping mapping : allMappings) {
                if (mapping.getRequest() != null && mapping.getRequest().getPath() != null) {
                    String path = mapping.getRequest().getPath();
                    if (content.contains(path.toLowerCase())) {
                        logger.info("Found endpoint by path '{}' in conversation history message #{}", path, i + 1);
                        return mapping;
                    }
                }
            }

            // Try to find by name
            for (RequestMapping mapping : allMappings) {
                if (mapping.getName() != null && content.contains(mapping.getName().toLowerCase())) {
                    logger.info("Found endpoint by name '{}' in conversation history message #{}", mapping.getName(), i + 1);
                    return mapping;
                }
            }

            // Try to find by ID
            for (RequestMapping mapping : allMappings) {
                if (mapping.getId() != null && content.contains(mapping.getId())) {
                    logger.info("Found endpoint by ID '{}' in conversation history message #{}", mapping.getId(), i + 1);
                    return mapping;
                }
            }
        }

        logger.debug("No endpoint found in conversation history");
        return null;
    }
}
