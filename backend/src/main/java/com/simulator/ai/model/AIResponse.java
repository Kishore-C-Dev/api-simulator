package com.simulator.ai.model;

import com.simulator.model.RequestMapping;
import java.util.List;

public class AIResponse {

    private boolean success;
    private String message;
    private RequestMapping generatedMapping;
    private List<String> suggestions;
    private String explanation;
    private Double confidence;
    private String mappingId;  // ID of mapping to modify/delete
    private String action;     // Action to perform: "create", "modify", "delete", "list"
    private List<RequestMapping> mappings;  // For list operations

    public AIResponse() {}

    public static AIResponse success(RequestMapping mapping) {
        AIResponse response = new AIResponse();
        response.setSuccess(true);
        response.setGeneratedMapping(mapping);
        response.setMessage("Mapping generated successfully");
        return response;
    }

    public static AIResponse error(String message) {
        AIResponse response = new AIResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    public static AIResponseBuilder builder() {
        return new AIResponseBuilder();
    }

    public static class AIResponseBuilder {
        private final AIResponse response = new AIResponse();

        public AIResponseBuilder success(boolean success) {
            response.setSuccess(success);
            return this;
        }

        public AIResponseBuilder message(String message) {
            response.setMessage(message);
            return this;
        }

        public AIResponseBuilder generatedMapping(RequestMapping mapping) {
            response.setGeneratedMapping(mapping);
            return this;
        }

        public AIResponseBuilder suggestions(List<String> suggestions) {
            response.setSuggestions(suggestions);
            return this;
        }

        public AIResponseBuilder explanation(String explanation) {
            response.setExplanation(explanation);
            return this;
        }

        public AIResponseBuilder confidence(Double confidence) {
            response.setConfidence(confidence);
            return this;
        }

        public AIResponseBuilder mappingId(String mappingId) {
            response.setMappingId(mappingId);
            return this;
        }

        public AIResponseBuilder action(String action) {
            response.setAction(action);
            return this;
        }

        public AIResponseBuilder mappings(List<RequestMapping> mappings) {
            response.setMappings(mappings);
            return this;
        }

        public AIResponse build() {
            return response;
        }
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public RequestMapping getGeneratedMapping() {
        return generatedMapping;
    }

    public void setGeneratedMapping(RequestMapping generatedMapping) {
        this.generatedMapping = generatedMapping;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getMappingId() {
        return mappingId;
    }

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<RequestMapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<RequestMapping> mappings) {
        this.mappings = mappings;
    }
}
