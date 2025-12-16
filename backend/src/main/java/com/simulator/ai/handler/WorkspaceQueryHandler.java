package com.simulator.ai.handler;

import com.simulator.ai.model.AIRequest;
import com.simulator.ai.model.AIResponse;
import com.simulator.model.RequestMapping;
import com.simulator.repository.NamespaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.List;

/**
 * Handler for workspace/namespace-related queries
 * Answers questions like "which workspace am I in?", "what is the current workspace?"
 */
@Component
public class WorkspaceQueryHandler extends AbstractTaskHandler {

    @Autowired
    private TaskHandlerRegistry registry;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @PostConstruct
    public void registerSelf() {
        registry.registerHandler(this);
    }

    @Override
    public int getPriority() {
        return 10; // Standard priority
    }

    @Override
    public AIRequest.AITaskType[] getSupportedTaskTypes() {
        return new AIRequest.AITaskType[]{
            AIRequest.AITaskType.LIST_NAMESPACES
        };
    }

    @Override
    public AIResponse handle(AIRequest request, List<RequestMapping> allMappings) {
        logger.info("WorkspaceQueryHandler processing workspace query");

        String currentNamespace = request.getNamespace() != null ? request.getNamespace() : "default";

        // Check if user is asking about current workspace
        String lowerPrompt = request.getUserPrompt().toLowerCase();
        if (lowerPrompt.contains("current") || lowerPrompt.contains("which") ||
            lowerPrompt.contains("what workspace") || lowerPrompt.contains("querying")) {

            // Answer current workspace question
            String message = String.format("You are currently in the **`%s`** workspace.", currentNamespace);

            if (allMappings != null && !allMappings.isEmpty()) {
                message += String.format("\n\nThis workspace contains **%d endpoints**.", allMappings.size());
            }

            return AIResponse.builder()
                .success(true)
                .action("info")
                .message("Current workspace")
                .explanation(message)
                .build();
        } else {
            // List all workspaces
            var allNamespaces = namespaceRepository.findAll();

            StringBuilder listText = new StringBuilder();
            listText.append(String.format("📁 **Available workspaces:** (%d total)\n\n", allNamespaces.size()));

            for (int i = 0; i < allNamespaces.size(); i++) {
                var ns = allNamespaces.get(i);
                boolean isCurrent = currentNamespace.equals(ns.getName());

                listText.append(String.format("%d. **%s**%s\n",
                    i + 1,
                    ns.getName(),
                    isCurrent ? " ← **(Current)**" : ""));

                if (ns.getDisplayName() != null && !ns.getDisplayName().equals(ns.getName())) {
                    listText.append(String.format("   └─ %s\n", ns.getDisplayName()));
                }

                if (ns.getDescription() != null) {
                    listText.append(String.format("   └─ %s\n", ns.getDescription()));
                }

                listText.append("\n");
            }

            return AIResponse.builder()
                .success(true)
                .action("list")
                .message(String.format("Found %d workspaces", allNamespaces.size()))
                .explanation(listText.toString())
                .build();
        }
    }
}
