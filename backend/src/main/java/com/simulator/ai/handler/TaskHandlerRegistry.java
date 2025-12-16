package com.simulator.ai.handler;

import com.simulator.ai.model.AIRequest;
import com.simulator.model.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registry for task handlers - manages and routes requests to appropriate handlers
 */
@Component
public class TaskHandlerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(TaskHandlerRegistry.class);

    private final List<TaskHandler> handlers = new ArrayList<>();

    /**
     * Register a task handler
     *
     * @param handler The handler to register
     */
    public void registerHandler(TaskHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(TaskHandler::getPriority));
        logger.info("Registered task handler: {} with priority {}", handler.getClass().getSimpleName(), handler.getPriority());
    }

    /**
     * Find the best handler for the given request
     *
     * @param request The AI request
     * @param allMappings All available mappings
     * @return The best matching handler, or null if none found
     */
    public TaskHandler findHandler(AIRequest request, List<RequestMapping> allMappings) {
        logger.debug("Finding handler for task type: {}", request.getTaskType());

        // Try to find a handler that can handle this request
        for (TaskHandler handler : handlers) {
            if (handler.canHandle(request, allMappings)) {
                logger.info("Selected handler: {} (priority: {})",
                    handler.getClass().getSimpleName(), handler.getPriority());
                return handler;
            }
        }

        logger.warn("No handler found for task type: {}", request.getTaskType());
        return null;
    }

    /**
     * Get all registered handlers
     *
     * @return list of registered handlers
     */
    public List<TaskHandler> getAllHandlers() {
        return new ArrayList<>(handlers);
    }
}
