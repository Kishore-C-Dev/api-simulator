package com.simulator.ai.handler;

import com.simulator.ai.model.AIRequest;
import com.simulator.ai.model.AIResponse;
import com.simulator.model.RequestMapping;

import java.util.List;

/**
 * Strategy interface for handling different AI task types
 */
public interface TaskHandler {

    /**
     * Check if this handler can process the given request
     *
     * @param request The AI request
     * @param allMappings All available mappings in the namespace
     * @return true if this handler can process the request
     */
    boolean canHandle(AIRequest request, List<RequestMapping> allMappings);

    /**
     * Get the priority of this handler (lower number = higher priority)
     * Used when multiple handlers can handle the same request
     *
     * @return priority value (default: 100)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Handle the AI request and return a response
     *
     * @param request The AI request
     * @param allMappings All available mappings in the namespace
     * @return AI response
     */
    AIResponse handle(AIRequest request, List<RequestMapping> allMappings);

    /**
     * Get the task types this handler supports
     *
     * @return array of supported task types
     */
    AIRequest.AITaskType[] getSupportedTaskTypes();
}
