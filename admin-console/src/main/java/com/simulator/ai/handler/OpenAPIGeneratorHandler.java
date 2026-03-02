package com.simulator.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.simulator.ai.model.AIRequest;
import com.simulator.ai.model.AIResponse;
import com.simulator.model.RequestMapping;
import com.simulator.service.MappingService;
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
import java.util.Map;

/**
 * Handler for generating multiple endpoint mappings from OpenAPI spec
 * Creates endpoints for all status codes defined in the spec
 */
@Component
public class OpenAPIGeneratorHandler extends AbstractTaskHandler {

    @Autowired
    private TaskHandlerRegistry registry;

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private MappingService mappingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${simulator.ai.model}")
    private String model;

    @Value("${simulator.ai.max-tokens}")
    private int maxTokens;

    @Value("${simulator.ai.temperature}")
    private double temperature;

    @PostConstruct
    public void registerSelf() {
        registry.registerHandler(this);
    }

    @Override
    public int getPriority() {
        return 10; // High priority for OpenAPI generation
    }

    @Override
    public AIRequest.AITaskType[] getSupportedTaskTypes() {
        return new AIRequest.AITaskType[]{
            AIRequest.AITaskType.GENERATE_FROM_OPENAPI
        };
    }

    @Override
    public AIResponse handle(AIRequest request, List<RequestMapping> allMappings) {
        logger.info("OpenAPIGeneratorHandler processing OpenAPI spec generation");

        try {
            // Extract OpenAPI spec YAML from user prompt
            String openAPISpec = extractOpenAPISpec(request.getUserPrompt());

            if (openAPISpec == null || openAPISpec.trim().isEmpty()) {
                String helpMessage = "❌ **Could not find OpenAPI spec in your message**\n\n" +
                    "Please provide an OpenAPI/Swagger spec in one of these formats:\n\n" +
                    "**Option 1: With code blocks**\n" +
                    "```yaml\n" +
                    "openapi: 3.0.0\n" +
                    "info:\n" +
                    "  title: My API\n" +
                    "paths:\n" +
                    "  /users:\n" +
                    "    get:\n" +
                    "      responses:\n" +
                    "        '200':\n" +
                    "          description: Success\n" +
                    "```\n\n" +
                    "**Option 2: Direct YAML**\n" +
                    "Just paste the YAML starting with `openapi:` or `swagger:`\n\n" +
                    "**Example:**\n" +
                    "```\n" +
                    "Generate endpoints from this OpenAPI spec:\n" +
                    "```yaml\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  /api/users:\n" +
                    "    post:\n" +
                    "      summary: Create User\n" +
                    "      responses:\n" +
                    "        '201':\n" +
                    "          description: Created\n" +
                    "        '400':\n" +
                    "          description: Bad Request\n" +
                    "```\n" +
                    "```";

                return AIResponse.builder()
                    .success(false)
                    .message("No OpenAPI spec provided")
                    .explanation(helpMessage)
                    .build();
            }

            logger.info("OpenAPI spec extracted, length: {} characters", openAPISpec.length());

            // Parse OpenAPI spec to understand endpoints and status codes
            List<EndpointSpec> endpointSpecs = parseOpenAPISpec(openAPISpec);

            if (endpointSpecs.isEmpty()) {
                return AIResponse.builder()
                    .success(false)
                    .message("No endpoints found in OpenAPI spec")
                    .explanation("Could not extract any endpoints from the provided OpenAPI spec")
                    .build();
            }

            logger.info("Found {} endpoint specs in OpenAPI", endpointSpecs.size());

            // Generate mappings for each endpoint and status code
            List<RequestMapping> generatedMappings = new ArrayList<>();
            int totalGenerated = 0;

            for (EndpointSpec spec : endpointSpecs) {
                logger.info("Generating mappings for: {} {}", spec.method, spec.path);
                logger.info("Status codes to generate: {}", spec.statusCodes);

                for (String statusCode : spec.statusCodes) {
                    RequestMapping mapping = generateMappingForStatusCode(
                        spec, statusCode, request.getNamespace()
                    );

                    if (mapping != null) {
                        // Save the mapping
                        RequestMapping saved = mappingService.saveMapping(mapping, request.getNamespace());
                        generatedMappings.add(saved);
                        totalGenerated++;
                        logger.info("✓ Generated mapping: {} - {} {}",
                            saved.getName(), spec.method, spec.path);
                    }
                }
            }

            // Reload WireMock with new mappings
            mappingService.reloadWireMockMappings();

            StringBuilder explanation = new StringBuilder();
            explanation.append(String.format("✅ **Generated %d endpoint mappings** from OpenAPI spec\n\n", totalGenerated));
            explanation.append("**Summary:**\n");

            for (EndpointSpec spec : endpointSpecs) {
                int count = spec.statusCodes.size();
                explanation.append(String.format("- `%s %s`: %d variants (%s)\n",
                    spec.method, spec.path, count, String.join(", ", spec.statusCodes)));
            }

            return AIResponse.builder()
                .success(true)
                .action("generate_from_openapi")
                .message(String.format("Generated %d endpoints", totalGenerated))
                .explanation(explanation.toString())
                .mappings(generatedMappings)
                .build();

        } catch (Exception e) {
            logger.error("Error generating mappings from OpenAPI spec", e);
            return AIResponse.builder()
                .success(false)
                .message("Failed to generate from OpenAPI spec")
                .explanation("Error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Extract OpenAPI spec YAML from user prompt
     */
    private String extractOpenAPISpec(String userPrompt) {
        logger.info("Extracting OpenAPI spec from prompt (length: {} chars)", userPrompt.length());
        logger.debug("Prompt content (first 200 chars): {}",
            userPrompt.substring(0, Math.min(200, userPrompt.length())));

        // Look for YAML content between ```yaml or ```yml markers
        String[] markers = {"```yaml", "```yml", "```"};

        for (String marker : markers) {
            int startIdx = userPrompt.indexOf(marker);
            if (startIdx != -1) {
                startIdx += marker.length();
                int endIdx = userPrompt.indexOf("```", startIdx);
                if (endIdx != -1) {
                    String extracted = userPrompt.substring(startIdx, endIdx).trim();
                    logger.info("Extracted OpenAPI spec from {} markers ({} chars)", marker, extracted.length());
                    return extracted;
                }
            }
        }

        // Look for openapi: or swagger: keyword anywhere in the text
        String lowerPrompt = userPrompt.toLowerCase();
        int openapiIdx = lowerPrompt.indexOf("openapi:");
        int swaggerIdx = lowerPrompt.indexOf("swagger:");

        if (openapiIdx != -1) {
            String yamlContent = userPrompt.substring(openapiIdx).trim();
            logger.info("Found 'openapi:' keyword, extracting from that point ({} chars)", yamlContent.length());
            return yamlContent;
        }

        if (swaggerIdx != -1) {
            String yamlContent = userPrompt.substring(swaggerIdx).trim();
            logger.info("Found 'swagger:' keyword, extracting from that point ({} chars)", yamlContent.length());
            return yamlContent;
        }

        // If prompt starts with version info or paths, it might be YAML without openapi: keyword
        if (userPrompt.trim().startsWith("version:") ||
            userPrompt.trim().startsWith("info:") ||
            userPrompt.trim().startsWith("paths:")) {
            logger.info("Found YAML structure keywords, using entire prompt");
            return userPrompt.trim();
        }

        logger.warn("Could not extract OpenAPI spec from prompt");
        return null;
    }

    /**
     * Parse OpenAPI spec and extract endpoint specifications
     */
    private List<EndpointSpec> parseOpenAPISpec(String yamlContent) {
        List<EndpointSpec> specs = new ArrayList<>();

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> openAPIDoc = yamlMapper.readValue(yamlContent, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> paths = (Map<String, Object>) openAPIDoc.get("paths");

            if (paths == null) {
                logger.warn("No 'paths' section found in OpenAPI spec");
                return specs;
            }

            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> pathItem = (Map<String, Object>) pathEntry.getValue();

                for (String method : List.of("get", "post", "put", "delete", "patch")) {
                    if (pathItem.containsKey(method)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> operation = (Map<String, Object>) pathItem.get(method);

                        EndpointSpec spec = new EndpointSpec();
                        spec.path = path;
                        spec.method = method.toUpperCase();
                        spec.summary = (String) operation.get("summary");
                        spec.description = (String) operation.get("description");
                        spec.statusCodes = extractStatusCodes(operation);
                        spec.requestBody = extractRequestBodySchema(operation);
                        spec.responses = extractResponseSchemas(operation);

                        if (!spec.statusCodes.isEmpty()) {
                            specs.add(spec);
                            logger.debug("Parsed endpoint: {} {} with {} status codes",
                                spec.method, spec.path, spec.statusCodes.size());
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error parsing OpenAPI spec: {}", e.getMessage(), e);
        }

        return specs;
    }

    /**
     * Extract all status codes from OpenAPI operation
     */
    private List<String> extractStatusCodes(Map<String, Object> operation) {
        List<String> statusCodes = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");

        if (responses != null) {
            for (String statusCode : responses.keySet()) {
                if (!statusCode.equals("default")) {
                    statusCodes.add(statusCode);
                }
            }
        }

        return statusCodes;
    }

    /**
     * Extract request body schema from operation
     */
    private Map<String, Object> extractRequestBodySchema(Map<String, Object> operation) {
        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = (Map<String, Object>) operation.get("requestBody");

        if (requestBody != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) requestBody.get("content");

            if (content != null && content.containsKey("application/json")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = (Map<String, Object>) jsonContent.get("schema");
                return schema;
            }
        }

        return null;
    }

    /**
     * Extract response schemas for all status codes
     */
    private Map<String, Map<String, Object>> extractResponseSchemas(Map<String, Object> operation) {
        Map<String, Map<String, Object>> responseSchemas = new java.util.HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");

        if (responses != null) {
            for (Map.Entry<String, Object> entry : responses.entrySet()) {
                String statusCode = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> response = (Map<String, Object>) entry.getValue();

                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) response.get("content");

                if (content != null && content.containsKey("application/json")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> schema = (Map<String, Object>) jsonContent.get("schema");

                    if (schema != null) {
                        responseSchemas.put(statusCode, schema);
                    }
                }
            }
        }

        return responseSchemas;
    }

    /**
     * Generate a RequestMapping for a specific status code using AI
     */
    private RequestMapping generateMappingForStatusCode(
        EndpointSpec spec, String statusCode, String namespace
    ) {
        try {
            int statusCodeInt = Integer.parseInt(statusCode);
            Map<String, Object> responseSchema = spec.responses.get(statusCode);

            // Get AI to generate structured response data
            EndpointGenerationData generationData = getAIGenerationData(spec, statusCode, responseSchema);

            // Build RequestMapping from structured data
            RequestMapping mapping = buildMappingFromGenerationData(
                generationData, spec, statusCodeInt, namespace
            );

            return mapping;

        } catch (Exception e) {
            logger.error("Error generating mapping for {} {} - status {}: {}",
                spec.method, spec.path, statusCode, e.getMessage());
            return null;
        }
    }

    /**
     * Use AI to generate structured data for endpoint
     */
    private EndpointGenerationData getAIGenerationData(
        EndpointSpec spec, String statusCode, Map<String, Object> responseSchema
    ) throws Exception {

        String systemPrompt = buildGenerationPrompt(spec, statusCode, responseSchema);
        String userPrompt = "Generate endpoint data";

        String aiResponse = callOpenAI(systemPrompt, userPrompt);

        // Parse JSON response
        String cleanJson = aiResponse.trim();
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

        return objectMapper.readValue(cleanJson, EndpointGenerationData.class);
    }

    /**
     * Build RequestMapping from AI-generated structured data
     */
    private RequestMapping buildMappingFromGenerationData(
        EndpointGenerationData data,
        EndpointSpec spec,
        int statusCode,
        String namespace
    ) {
        RequestMapping mapping = new RequestMapping();

        // Basic info
        mapping.setName(data.name);
        mapping.setNamespace(namespace);
        mapping.setPriority(data.priority);
        mapping.setEnabled(true);
        mapping.setTags(data.tags);

        // Request
        RequestMapping.RequestSpec request = new RequestMapping.RequestSpec();
        request.setMethod(spec.method);
        request.setPath(spec.path);

        if (data.requiredHeaders != null && !data.requiredHeaders.isEmpty()) {
            request.setHeaders(data.requiredHeaders);
        }

        mapping.setRequest(request);

        // Response
        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(statusCode);
        response.setHeaders(Map.of("Content-Type", "application/json"));

        if (data.responseBody != null) {
            try {
                // Convert response body to formatted JSON string
                String responseBodyJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(data.responseBody);
                response.setBody(responseBodyJson);
            } catch (Exception e) {
                logger.warn("Could not serialize response body", e);
                response.setBody("{}");
            }
        } else {
            response.setBody("{}");
        }

        mapping.setResponse(response);

        // Delays (optional)
        if (data.fixedDelayMs != null && data.fixedDelayMs > 0) {
            RequestMapping.DelaySpec delays = new RequestMapping.DelaySpec();
            delays.setMode("fixed");
            delays.setFixedMs(data.fixedDelayMs);
            mapping.setDelays(delays);
        }

        mapping.setCreatedAt(java.time.Instant.now());
        mapping.setUpdatedAt(java.time.Instant.now());

        return mapping;
    }

    /**
     * Build AI prompt for generating endpoint mapping
     */
    private String buildGenerationPrompt(EndpointSpec spec, String statusCode, Map<String, Object> responseSchema) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are generating structured data for an API simulator endpoint from OpenAPI specification.\n\n");

        prompt.append("**Endpoint Details:**\n");
        prompt.append(String.format("- Method: %s\n", spec.method));
        prompt.append(String.format("- Path: %s\n", spec.path));
        prompt.append(String.format("- Status Code: %s\n", statusCode));

        if (spec.summary != null) {
            prompt.append(String.format("- Summary: %s\n", spec.summary));
        }
        if (spec.description != null) {
            prompt.append(String.format("- Description: %s\n", spec.description));
        }

        if (responseSchema != null) {
            try {
                String schemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseSchema);
                prompt.append("\n**Response Schema:**\n");
                prompt.append("```json\n");
                prompt.append(schemaJson);
                prompt.append("\n```\n");
            } catch (Exception e) {
                logger.warn("Could not serialize response schema", e);
            }
        }

        if (spec.requestBody != null) {
            try {
                String requestSchemaJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec.requestBody);
                prompt.append("\n**Request Body Schema:**\n");
                prompt.append("```json\n");
                prompt.append(requestSchemaJson);
                prompt.append("\n```\n");
            } catch (Exception e) {
                logger.warn("Could not serialize request schema", e);
            }
        }

        prompt.append("\n**Your Task:**\n");
        prompt.append("Generate structured data in the following JSON format:\n\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append(String.format("  \"name\": \"%s - %s %s\",\n",
            spec.summary != null ? spec.summary : (spec.method + " " + spec.path),
            getStatusDescription(statusCode),
            statusCode));
        prompt.append(String.format("  \"priority\": %d,\n", calculatePriority(statusCode)));
        prompt.append("  \"tags\": [\"openapi-generated\", \"auto\"],\n");
        prompt.append("  \"requiredHeaders\": {},\n");
        prompt.append("  \"responseBody\": { /* Generate realistic response matching schema */ },\n");
        prompt.append("  \"fixedDelayMs\": 0\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("**Instructions:**\n");
        prompt.append("1. **name**: Descriptive endpoint name\n");
        prompt.append("2. **priority**: Already set correctly\n");
        prompt.append("3. **tags**: Keep as shown\n");
        prompt.append("4. **requiredHeaders**: Leave empty {} unless request requires specific headers\n");
        prompt.append("5. **responseBody**: Generate realistic JSON matching the response schema\n");
        prompt.append("   - Use realistic values (not placeholders like 'string' or 'number')\n");
        prompt.append("   - For IDs: use UUIDs like \"550e8400-e29b-41d4-a716-446655440000\"\n");
        prompt.append("   - For timestamps: use ISO format like \"2024-01-15T10:30:00Z\"\n");
        prompt.append("   - For names: use realistic names like \"John Doe\"\n");
        prompt.append("   - For emails: use realistic emails like \"user@example.com\"\n");
        prompt.append("   - For error responses: include \"error\" field with appropriate message\n");
        prompt.append("6. **fixedDelayMs**: Set to 0 for no delay\n\n");

        prompt.append("**IMPORTANT:** Return ONLY the JSON object, no markdown code blocks, no explanations.\n");

        return prompt.toString();
    }

    /**
     * Get human-readable status description
     */
    private String getStatusDescription(String statusCode) {
        return switch (statusCode) {
            case "200" -> "Success";
            case "201" -> "Created";
            case "204" -> "No Content";
            case "400" -> "Bad Request";
            case "401" -> "Unauthorized";
            case "403" -> "Forbidden";
            case "404" -> "Not Found";
            case "409" -> "Conflict";
            case "422" -> "Validation Error";
            case "500" -> "Server Error";
            case "503" -> "Service Unavailable";
            default -> "Status " + statusCode;
        };
    }

    /**
     * Calculate priority based on status code
     * Success responses = lower priority (5)
     * Error responses = higher priority (3)
     */
    private int calculatePriority(String statusCode) {
        int code = Integer.parseInt(statusCode);
        if (code >= 200 && code < 300) {
            return 5; // Success - lower priority
        } else if (code >= 400) {
            return 3; // Error - higher priority
        }
        return 5;
    }

    /**
     * Call OpenAI to generate mapping
     */
    private String callOpenAI(String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userPrompt));

        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
            .model(model)
            .messages(messages)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();

        return openAiService.createChatCompletion(chatRequest)
            .getChoices()
            .get(0)
            .getMessage()
            .getContent();
    }

    /**
     * Internal class to hold endpoint specification from OpenAPI
     */
    private static class EndpointSpec {
        String path;
        String method;
        String summary;
        String description;
        List<String> statusCodes = new ArrayList<>();
        Map<String, Object> requestBody;
        Map<String, Map<String, Object>> responses = new java.util.HashMap<>();
    }

    /**
     * Internal class to hold AI-generated endpoint data
     */
    private static class EndpointGenerationData {
        private String name;
        private Integer priority;
        private List<String> tags;
        private Map<String, String> requiredHeaders;
        private Object responseBody;
        private Integer fixedDelayMs;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public Map<String, String> getRequiredHeaders() { return requiredHeaders; }
        public void setRequiredHeaders(Map<String, String> requiredHeaders) { this.requiredHeaders = requiredHeaders; }

        public Object getResponseBody() { return responseBody; }
        public void setResponseBody(Object responseBody) { this.responseBody = responseBody; }

        public Integer getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(Integer fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    }
}
