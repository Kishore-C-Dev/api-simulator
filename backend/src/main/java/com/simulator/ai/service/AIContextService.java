package com.simulator.ai.service;

import com.simulator.model.RequestMapping;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIContextService {

    private static final Set<String> STOP_WORDS = Set.of(
        "create", "make", "need", "want", "endpoint", "mapping", "please", "can", "you", "help", "add"
    );

    /**
     * Build context string from relevant mappings
     */
    public String buildContext(List<RequestMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return "No existing mappings in workspace.";
        }

        StringBuilder context = new StringBuilder();
        context.append("EXISTING ENDPOINTS:\n");

        for (RequestMapping mapping : mappings) {
            if (mapping.getRequest() != null) {
                context.append(String.format("- %s %s (Priority: %d, Status: %d)\n",
                    mapping.getRequest().getMethod(),
                    mapping.getRequest().getPath(),
                    mapping.getPriority() != null ? mapping.getPriority() : 5,
                    mapping.getResponse() != null && mapping.getResponse().getStatus() != null
                        ? mapping.getResponse().getStatus() : 200
                ));

                if (mapping.getTags() != null && !mapping.getTags().isEmpty()) {
                    context.append(String.format("  Tags: %s\n", String.join(", ", mapping.getTags())));
                }
            }
        }

        return context.toString();
    }

    /**
     * Extract keywords from user prompt
     */
    public Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(text.toLowerCase().split("\\s+"))
            .filter(word -> word.length() > 2)
            .filter(word -> !STOP_WORDS.contains(word))
            .map(word -> word.replaceAll("[^a-z0-9]", ""))
            .filter(word -> !word.isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * Calculate relevance score of a mapping to keywords
     */
    public double calculateRelevance(RequestMapping mapping, Set<String> keywords) {
        if (keywords.isEmpty()) {
            return 1.0; // All mappings equally relevant if no keywords
        }

        int matches = 0;
        int total = keywords.size();

        String searchText = buildSearchText(mapping).toLowerCase();

        for (String keyword : keywords) {
            if (searchText.contains(keyword)) {
                matches++;
            }
        }

        return (double) matches / total;
    }

    /**
     * Get relevant mappings based on user prompt
     */
    public List<RequestMapping> getRelevantMappings(List<RequestMapping> allMappings, String userPrompt, int maxMappings) {
        Set<String> keywords = extractKeywords(userPrompt);

        return allMappings.stream()
            .map(m -> new ScoredMapping(m, calculateRelevance(m, keywords)))
            .sorted(Comparator.comparingDouble(ScoredMapping::getScore).reversed())
            .limit(maxMappings)
            .map(ScoredMapping::getMapping)
            .toList();
    }

    private String buildSearchText(RequestMapping mapping) {
        StringBuilder text = new StringBuilder();

        if (mapping.getName() != null) {
            text.append(mapping.getName()).append(" ");
        }

        if (mapping.getRequest() != null) {
            if (mapping.getRequest().getPath() != null) {
                text.append(mapping.getRequest().getPath()).append(" ");
            }
            if (mapping.getRequest().getMethod() != null) {
                text.append(mapping.getRequest().getMethod()).append(" ");
            }
        }

        if (mapping.getTags() != null) {
            text.append(String.join(" ", mapping.getTags()));
        }

        return text.toString();
    }

    /**
     * Inner class to hold mapping with relevance score
     */
    private static class ScoredMapping {
        private final RequestMapping mapping;
        private final double score;

        public ScoredMapping(RequestMapping mapping, double score) {
            this.mapping = mapping;
            this.score = score;
        }

        public RequestMapping getMapping() {
            return mapping;
        }

        public double getScore() {
            return score;
        }
    }
}
