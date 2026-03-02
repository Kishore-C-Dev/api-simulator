package com.simulator.ai.model;

import java.util.List;
import java.util.Map;

public class AIRequest {

    private String userPrompt;
    private AITaskType taskType;
    private Map<String, Object> context;
    private String namespace;
    private List<ChatMessage> conversationHistory;

    public static class ChatMessage {
        private String role; // "user" or "assistant"
        private String content;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public enum AITaskType {
        // Mapping CRUD operations
        CREATE_MAPPING,
        MODIFY_MAPPING,
        DELETE_MAPPING,
        LIST_MAPPINGS,
        MOVE_MAPPING,           // Move endpoint to different namespace/workspace
        BULK_UPDATE_MAPPING,    // Update multiple endpoints at once (e.g., add header to all)

        // Mapping helper operations
        SUGGEST_RESPONSE,
        DEBUG_MAPPING,
        EXPLAIN_MAPPING,
        OPTIMIZE_MAPPING,

        // OpenAPI spec generation
        GENERATE_FROM_OPENAPI,  // Generate multiple endpoints from OpenAPI spec YAML

        // Ask mode - payload/endpoint analysis
        ANALYZE_PAYLOAD,         // Analyze request payload and find matching endpoints
        ANALYZE_CURL,           // Parse curl command and check endpoint matching
        CHECK_ENDPOINT_MATCH,   // Check what's missing for endpoint to match

        // Namespace CRUD operations
        CREATE_NAMESPACE,
        MODIFY_NAMESPACE,
        DELETE_NAMESPACE,
        LIST_NAMESPACES,

        // User CRUD operations
        CREATE_USER,
        MODIFY_USER,
        DELETE_USER,
        LIST_USERS,

        // User/Namespace assignment
        ENABLE_DISABLE_USER,
        ASSIGN_NAMESPACE
    }

    public AIRequest() {}

    public AIRequest(String userPrompt, AITaskType taskType) {
        this.userPrompt = userPrompt;
        this.taskType = taskType;
    }

    // Getters and Setters
    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public AITaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(AITaskType taskType) {
        this.taskType = taskType;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public List<ChatMessage> getConversationHistory() {
        return conversationHistory;
    }

    public void setConversationHistory(List<ChatMessage> conversationHistory) {
        this.conversationHistory = conversationHistory;
    }
}
