package com.simulator.ai.handler;

import com.simulator.ai.model.AIRequest;
import com.simulator.model.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Abstract base class for task handlers with common utility methods
 */
public abstract class AbstractTaskHandler implements TaskHandler {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean canHandle(AIRequest request, List<RequestMapping> allMappings) {
        if (request.getTaskType() == null) {
            return false;
        }

        // Check if this handler supports the task type
        return Arrays.asList(getSupportedTaskTypes()).contains(request.getTaskType());
    }

    /**
     * Identify target endpoint from user prompt
     *
     * @param userPrompt User's message
     * @param allMappings All available mappings
     * @return Identified mapping or null
     */
    protected RequestMapping identifyTargetEndpoint(String userPrompt, List<RequestMapping> allMappings) {
        if (allMappings == null || allMappings.isEmpty()) {
            logger.debug("No mappings available to identify");
            return null;
        }

        String lowerPrompt = userPrompt.toLowerCase();
        logger.debug("Attempting to identify endpoint from prompt: '{}'", userPrompt);
        logger.debug("Available mappings: {}", allMappings.stream()
            .map(RequestMapping::getName)
            .toList());

        // Common words to ignore when matching
        List<String> commonWords = Arrays.asList(
            "endpoint", "endpoints", "mapping", "mappings", "api", "list", "show",
            "explain", "describe", "get", "post", "put", "delete", "and", "the", "a", "an"
        );

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

        // Try to find by name (full or partial match)
        for (RequestMapping mapping : allMappings) {
            if (mapping.getName() != null) {
                String mappingNameLower = mapping.getName().toLowerCase();

                // Check for full name match
                if (lowerPrompt.contains(mappingNameLower)) {
                    logger.info("Endpoint identified by full name match: {}", mapping.getName());
                    return mapping;
                }
            }
        }

        // Partial word match - find ALL matching words and score them
        RequestMapping bestMatch = null;
        int bestScore = 0;
        String bestWord = null;

        for (RequestMapping mapping : allMappings) {
            if (mapping.getName() != null) {
                String mappingNameLower = mapping.getName().toLowerCase();
                String[] words = mappingNameLower.split("\\s+");

                for (String word : words) {
                    // Skip common words and short words
                    if (commonWords.contains(word) || word.length() <= 2) {
                        continue;
                    }

                    // Check if this word appears in the prompt
                    if (lowerPrompt.contains(word)) {
                        // Score based on word length (longer words = more specific = higher score)
                        int score = word.length();

                        if (score > bestScore) {
                            bestScore = score;
                            bestMatch = mapping;
                            bestWord = word;
                        }
                    }
                }
            }
        }

        if (bestMatch != null) {
            logger.info("Endpoint identified by partial name match: '{}' in '{}' (score: {})",
                bestWord, bestMatch.getName(), bestScore);
            return bestMatch;
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

        logger.warn("No specific endpoint could be identified from prompt: '{}'", userPrompt);
        logger.debug("Searched through {} mappings without finding a match", allMappings.size());
        return null;
    }

    /**
     * Check if user wants information about a specific endpoint or all endpoints
     *
     * @param userPrompt User's message
     * @param allMappings All available mappings
     * @return true if asking about specific endpoint, false if asking about all
     */
    protected boolean isSpecificEndpointQuery(String userPrompt, List<RequestMapping> allMappings) {
        return identifyTargetEndpoint(userPrompt, allMappings) != null;
    }
}
