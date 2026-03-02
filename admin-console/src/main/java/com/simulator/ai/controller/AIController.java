package com.simulator.ai.controller;

import com.simulator.ai.model.AIRequest;
import com.simulator.ai.model.AIResponse;
import com.simulator.ai.service.AIService;
import com.simulator.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/ai")
@ConditionalOnProperty(name = "simulator.ai.enabled", havingValue = "true")
public class AIController {

    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    @Autowired
    private AIService aiService;

    @Autowired
    private SessionManager sessionManager;

    @Value("${simulator.ai.enabled}")
    private boolean aiEnabled;

    /**
     * Generate mapping from natural language
     */
    @PostMapping("/generate")
    public ResponseEntity<AIResponse> generateMapping(
        @RequestBody AIRequest request,
        HttpSession session
    ) {
        try {
            logger.info("AI generate request: {}", request.getUserPrompt());

            // Check if AI is enabled
            if (!aiEnabled) {
                return ResponseEntity.ok(AIResponse.error("AI Assistant is not enabled. Set AI_ENABLED=true and configure OPENAI_API_KEY."));
            }

            // Get current namespace from session
            String namespace = sessionManager.getCurrentNamespace(session);
            if (namespace == null) {
                namespace = "default";
            }
            request.setNamespace(namespace);

            // Task type will be auto-detected in AIService
            // Don't set a default here to allow auto-detection

            // Generate mapping
            AIResponse response = aiService.generateMapping(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error in AI generate endpoint: {}", e.getMessage(), e);
            return ResponseEntity.ok(AIResponse.error("An error occurred: " + e.getMessage()));
        }
    }

    /**
     * Check if AI is enabled
     */
    @GetMapping("/status")
    public ResponseEntity<AIStatusResponse> getStatus() {
        return ResponseEntity.ok(new AIStatusResponse(aiEnabled, "OpenAI"));
    }

    /**
     * AI status response
     */
    public static class AIStatusResponse {
        private boolean enabled;
        private String provider;

        public AIStatusResponse(boolean enabled, String provider) {
            this.enabled = enabled;
            this.provider = provider;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }
}
