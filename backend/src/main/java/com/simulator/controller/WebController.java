package com.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulator.model.RequestMapping;
import com.simulator.model.UserProfile;
import com.simulator.model.Namespace;
import com.simulator.service.MappingService;
import com.simulator.service.SessionManager;
import com.simulator.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    @Autowired
    private MappingService mappingService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private UserService userService;
    
    public WebController() {
        logger.info("WebController loaded with form endpoints");
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpSession session,
            Model model) {
        
        // Check authentication
        if (!sessionManager.isAuthenticated(session)) {
            return "redirect:/login";
        }
        
        UserProfile user = sessionManager.getCurrentUser(session);
        String currentNamespace = sessionManager.getCurrentNamespace(session);
        
        // If no namespace selected, redirect to login to handle namespace assignment
        if (currentNamespace == null) {
            logger.warn("User {} has no accessible namespace", user.getUserId());
            return "redirect:/login";
        }
        
        // Build sort configuration - default is name ASC, then priority ASC
        Sort sort;
        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        
        if ("name".equals(sortBy)) {
            sort = Sort.by(direction, "name")
                      .and(Sort.by(Sort.Direction.ASC, "priority"));
        } else if ("priority".equals(sortBy)) {
            sort = Sort.by(direction, "priority")
                      .and(Sort.by(Sort.Direction.ASC, "name"));
        } else if ("method".equals(sortBy)) {
            sort = Sort.by(direction, "request.method")
                      .and(Sort.by(Sort.Direction.ASC, "name"));
        } else if ("path".equals(sortBy)) {
            sort = Sort.by(direction, "request.path")
                      .and(Sort.by(Sort.Direction.ASC, "name"));
        } else {
            // Default sorting: name ASC, then priority ASC (this should apply when no sortBy is specified)
            sort = Sort.by(Sort.Direction.ASC, "name")
                      .and(Sort.by(Sort.Direction.ASC, "priority"));
        }
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<RequestMapping> mappings = mappingService.searchMappings(currentNamespace, search, pageable);
        
        // Get user's namespaces for dropdown
        List<Namespace> userNamespaces = userService.getUserNamespaces(user.getUserId());
        
        model.addAttribute("mappings", mappings);
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", mappings.getTotalPages());
        
        // Add user and namespace info
        model.addAttribute("currentUser", user);
        model.addAttribute("currentNamespace", currentNamespace);
        model.addAttribute("userNamespaces", userNamespaces);
        
        return "dashboard";
    }

    @GetMapping("/mappings/new")
    public String newMapping(HttpSession session, Model model) {
        // Check authentication
        if (!sessionManager.isAuthenticated(session)) {
            return "redirect:/login";
        }
        model.addAttribute("mapping", new RequestMapping());
        model.addAttribute("isEdit", false);
        // Add empty JSON strings for new mappings
        model.addAttribute("requestHeadersJson", "{}");
        model.addAttribute("requestQueryParamsJson", "{}");
        model.addAttribute("responseHeadersJson", "{}");
        model.addAttribute("conditionalResponsesJson", "[]");
        // Add default advanced pattern values
        model.addAttribute("pathMatchingMode", "simple");
        model.addAttribute("pathMatchType", "EXACT");
        model.addAttribute("pathPattern", "");
        model.addAttribute("pathIgnoreCase", false);
        model.addAttribute("headerMatchingMode", "simple");
        model.addAttribute("headerPatternsJson", "{}");
        model.addAttribute("queryParamMatchingMode", "simple");
        model.addAttribute("queryParamPatternsJson", "{}");
        model.addAttribute("bodyPatternsJson", "[]");
        return "mapping-form";
    }

    @GetMapping("/mappings/new/graphql")
    public String newGraphQLMapping(HttpSession session, Model model) {
        // Check authentication
        if (!sessionManager.isAuthenticated(session)) {
            return "redirect:/login";
        }
        model.addAttribute("mapping", new RequestMapping());
        model.addAttribute("isEdit", false);
        model.addAttribute("isGraphQL", true);

        // Add empty GraphQL-specific data
        model.addAttribute("graphqlData", "{}");
        model.addAttribute("graphqlErrors", "[]");
        model.addAttribute("graphqlExtensions", "{}");
        model.addAttribute("graphqlVariables", "{}");

        return "graphql-mapping-form";
    }

    @GetMapping("/mappings/{id}/edit")
    public String editMapping(@PathVariable String id, Model model) {
        return mappingService.getMapping(id)
            .map(mapping -> {
                try {
                    // Serialize maps to JSON strings for display in textareas
                    model.addAttribute("mapping", mapping);
                    model.addAttribute("isEdit", true);
                    
                    // Add JSON-serialized versions for textareas
                    if (mapping.getRequest() != null && mapping.getRequest().getHeaders() != null) {
                        model.addAttribute("requestHeadersJson", objectMapper.writeValueAsString(mapping.getRequest().getHeaders()));
                    } else {
                        model.addAttribute("requestHeadersJson", "{}");
                    }
                    
                    if (mapping.getRequest() != null && mapping.getRequest().getQueryParams() != null) {
                        model.addAttribute("requestQueryParamsJson", objectMapper.writeValueAsString(mapping.getRequest().getQueryParams()));
                    } else {
                        model.addAttribute("requestQueryParamsJson", "{}");
                    }
                    
                    if (mapping.getResponse() != null && mapping.getResponse().getHeaders() != null) {
                        model.addAttribute("responseHeadersJson", objectMapper.writeValueAsString(mapping.getResponse().getHeaders()));
                    } else {
                        model.addAttribute("responseHeadersJson", "{}");
                    }
                    
                    // Add conditional responses JSON for editing
                    if (mapping.getResponse() != null 
                        && mapping.getResponse().getConditionalResponses() != null 
                        && mapping.getResponse().getConditionalResponses().getRequestIdMappings() != null) {
                        model.addAttribute("conditionalResponsesJson", objectMapper.writeValueAsString(mapping.getResponse().getConditionalResponses().getRequestIdMappings()));
                    } else {
                        model.addAttribute("conditionalResponsesJson", "[]");
                    }
                    
                    // Add advanced pattern data for editing
                    if (mapping.getRequest() != null) {
                        // Path pattern
                        if (mapping.getRequest().getPathPattern() != null) {
                            model.addAttribute("pathMatchingMode", "advanced");
                            model.addAttribute("pathMatchType", mapping.getRequest().getPathPattern().getMatchType().name());
                            model.addAttribute("pathPattern", mapping.getRequest().getPathPattern().getPattern());
                            model.addAttribute("pathIgnoreCase", mapping.getRequest().getPathPattern().isIgnoreCase());
                        } else {
                            model.addAttribute("pathMatchingMode", "simple");
                            model.addAttribute("pathMatchType", "EXACT");
                            model.addAttribute("pathPattern", "");
                            model.addAttribute("pathIgnoreCase", false);
                        }
                        
                        // Header patterns
                        if (mapping.getRequest().getHeaderPatterns() != null && !mapping.getRequest().getHeaderPatterns().isEmpty()) {
                            model.addAttribute("headerMatchingMode", "advanced");
                            model.addAttribute("headerPatternsJson", objectMapper.writeValueAsString(mapping.getRequest().getHeaderPatterns()));
                        } else {
                            model.addAttribute("headerMatchingMode", "simple");
                            model.addAttribute("headerPatternsJson", "{}");
                        }
                        
                        // Query param patterns
                        if (mapping.getRequest().getQueryParamPatterns() != null && !mapping.getRequest().getQueryParamPatterns().isEmpty()) {
                            model.addAttribute("queryParamMatchingMode", "advanced");
                            model.addAttribute("queryParamPatternsJson", objectMapper.writeValueAsString(mapping.getRequest().getQueryParamPatterns()));
                        } else {
                            model.addAttribute("queryParamMatchingMode", "simple");
                            model.addAttribute("queryParamPatternsJson", "{}");
                        }
                        
                        // Body patterns
                        if (mapping.getRequest().getBodyPatterns() != null && !mapping.getRequest().getBodyPatterns().isEmpty()) {
                            model.addAttribute("bodyPatternsJson", objectMapper.writeValueAsString(mapping.getRequest().getBodyPatterns()));
                        } else {
                            model.addAttribute("bodyPatternsJson", "[]");
                        }
                    } else {
                        // Default values for new mappings
                        model.addAttribute("pathMatchingMode", "simple");
                        model.addAttribute("pathMatchType", "EXACT");
                        model.addAttribute("pathPattern", "");
                        model.addAttribute("pathIgnoreCase", false);
                        model.addAttribute("headerMatchingMode", "simple");
                        model.addAttribute("headerPatternsJson", "{}");
                        model.addAttribute("queryParamMatchingMode", "simple");
                        model.addAttribute("queryParamPatternsJson", "{}");
                        model.addAttribute("bodyPatternsJson", "[]");
                    }

                    // Check if this is a GraphQL mapping
                    if (mapping.getEndpointType() != null && mapping.getEndpointType().name().equals("GRAPHQL")) {
                        model.addAttribute("isGraphQL", true);
                        // Add GraphQL-specific data for editing
                        if (mapping.getResponse() != null && mapping.getResponse().getGraphQLResponse() != null) {
                            var graphqlResponse = mapping.getResponse().getGraphQLResponse();
                            if (graphqlResponse.getData() != null) {
                                model.addAttribute("graphqlData", objectMapper.writeValueAsString(graphqlResponse.getData()));
                            } else {
                                model.addAttribute("graphqlData", "{}");
                            }
                            if (graphqlResponse.getErrors() != null) {
                                model.addAttribute("graphqlErrors", objectMapper.writeValueAsString(graphqlResponse.getErrors()));
                            } else {
                                model.addAttribute("graphqlErrors", "[]");
                            }
                            if (graphqlResponse.getExtensions() != null) {
                                model.addAttribute("graphqlExtensions", objectMapper.writeValueAsString(graphqlResponse.getExtensions()));
                            } else {
                                model.addAttribute("graphqlExtensions", "{}");
                            }
                        } else {
                            model.addAttribute("graphqlData", "{}");
                            model.addAttribute("graphqlErrors", "[]");
                            model.addAttribute("graphqlExtensions", "{}");
                        }

                        if (mapping.getGraphQLSpec() != null && mapping.getGraphQLSpec().getVariables() != null) {
                            model.addAttribute("graphqlVariables", objectMapper.writeValueAsString(mapping.getGraphQLSpec().getVariables()));
                        } else {
                            model.addAttribute("graphqlVariables", "{}");
                        }

                        return "graphql-mapping-form";
                    }

                    return "mapping-form";
                } catch (Exception e) {
                    return "redirect:/";
                }
            })
            .orElse("redirect:/");
    }

    @GetMapping("/test")
    public String testPanel() {
        return "test-panel";
    }

    @GetMapping("/import-export")
    public String importExport() {
        return "import-export";
    }

    // Form-based endpoints for web UI
    @PostMapping("/mappings/form")
    public String createMappingForm(
            @RequestParam String name,
            @RequestParam Integer priority,
            @RequestParam(defaultValue = "false") Boolean enabled,
            @RequestParam(required = false) String tags,
            @RequestParam String requestMethod,
            @RequestParam String requestPath,
            @RequestParam(required = false) String requestHeaders,
            @RequestParam(required = false) String requestQueryParams,
            @RequestParam Integer responseStatus,
            @RequestParam(required = false) String responseHeaders,
            @RequestParam String responseBody,
            @RequestParam(defaultValue = "false") Boolean templatingEnabled,
            @RequestParam(required = false) String delayMode,
            @RequestParam(required = false) Integer fixedMs,
            @RequestParam(required = false) Integer variableMinMs,
            @RequestParam(required = false) Integer variableMaxMs,
            @RequestParam(defaultValue = "0") Integer errorRatePercent,
            @RequestParam(required = false) Integer errorStatus,
            @RequestParam(required = false) String errorBody,
            // Advanced pattern matching parameters
            @RequestParam(required = false) String pathMatchingMode,
            @RequestParam(required = false) String pathMatchType,
            @RequestParam(required = false) String pathPattern,
            @RequestParam(defaultValue = "false") Boolean pathIgnoreCase,
            @RequestParam(required = false) String headerMatchingMode,
            @RequestParam(required = false) String headerPatternsJson,
            @RequestParam(required = false) String queryParamMatchingMode,
            @RequestParam(required = false) String queryParamPatternsJson,
            @RequestParam(required = false) String bodyPatternsJson) {
        
        try {
            logger.info("Creating mapping from form submission");
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags, 
                requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs, 
                errorRatePercent, errorStatus, errorBody,
                pathMatchingMode, pathMatchType, pathPattern, pathIgnoreCase,
                headerMatchingMode, headerPatternsJson, queryParamMatchingMode,
                queryParamPatternsJson, bodyPatternsJson);
            
            mappingService.saveMapping(mapping);
            return "redirect:/";
        } catch (Exception e) {
            logger.error("Failed to create mapping from form: {}", e.getMessage());
            return "redirect:/mappings/new?error=true";
        }
    }
    
    @PostMapping("/mappings/{id}/form")
    public String updateMappingForm(
            @PathVariable String id,
            @RequestParam String name,
            @RequestParam Integer priority,
            @RequestParam(defaultValue = "false") Boolean enabled,
            @RequestParam(required = false) String tags,
            @RequestParam String requestMethod,
            @RequestParam String requestPath,
            @RequestParam(required = false) String requestHeaders,
            @RequestParam(required = false) String requestQueryParams,
            @RequestParam Integer responseStatus,
            @RequestParam(required = false) String responseHeaders,
            @RequestParam String responseBody,
            @RequestParam(defaultValue = "false") Boolean templatingEnabled,
            @RequestParam(required = false) String delayMode,
            @RequestParam(required = false) Integer fixedMs,
            @RequestParam(required = false) Integer variableMinMs,
            @RequestParam(required = false) Integer variableMaxMs,
            @RequestParam(defaultValue = "0") Integer errorRatePercent,
            @RequestParam(required = false) Integer errorStatus,
            @RequestParam(required = false) String errorBody,
            // Advanced pattern matching parameters
            @RequestParam(required = false) String pathMatchingMode,
            @RequestParam(required = false) String pathMatchType,
            @RequestParam(required = false) String pathPattern,
            @RequestParam(defaultValue = "false") Boolean pathIgnoreCase,
            @RequestParam(required = false) String headerMatchingMode,
            @RequestParam(required = false) String headerPatternsJson,
            @RequestParam(required = false) String queryParamMatchingMode,
            @RequestParam(required = false) String queryParamPatternsJson,
            @RequestParam(required = false) String bodyPatternsJson) {
        
        try {
            logger.info("Updating mapping {} from form submission", id);
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags, 
                requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs, 
                errorRatePercent, errorStatus, errorBody,
                pathMatchingMode, pathMatchType, pathPattern, pathIgnoreCase,
                headerMatchingMode, headerPatternsJson, queryParamMatchingMode,
                queryParamPatternsJson, bodyPatternsJson);
            
            mapping.setId(id);
            mappingService.saveMapping(mapping);
            return "redirect:/";
        } catch (Exception e) {
            logger.error("Failed to update mapping from form: {}", e.getMessage());
            return "redirect:/mappings/" + id + "/edit?error=true";
        }
    }
    
    private RequestMapping buildMappingFromForm(String name, Integer priority, Boolean enabled, String tags,
            String requestMethod, String requestPath, String requestHeaders, String requestQueryParams,
            Integer responseStatus, String responseHeaders, String responseBody, Boolean templatingEnabled,
            String delayMode, Integer fixedMs, Integer variableMinMs, Integer variableMaxMs,
            Integer errorRatePercent, Integer errorStatus, String errorBody,
            String pathMatchingMode, String pathMatchType, String pathPattern, Boolean pathIgnoreCase,
            String headerMatchingMode, String headerPatternsJson,
            String queryParamMatchingMode, String queryParamPatternsJson,
            String bodyPatternsJson) throws Exception {
        
        RequestMapping mapping = new RequestMapping();
        mapping.setName(name);
        mapping.setPriority(priority != null ? priority : 5);
        mapping.setEnabled(enabled != null ? enabled : true);
        
        // Parse tags
        if (tags != null && !tags.trim().isEmpty()) {
            mapping.setTags(Arrays.asList(tags.split("\\s*,\\s*")));
        }
        
        // Build request
        RequestMapping.RequestSpec request = new RequestMapping.RequestSpec();
        request.setMethod(requestMethod);
        request.setPath(requestPath);
        
        if (requestHeaders != null && !requestHeaders.trim().isEmpty()) {
            request.setHeaders(objectMapper.readValue(requestHeaders, Map.class));
        }
        
        if (requestQueryParams != null && !requestQueryParams.trim().isEmpty()) {
            request.setQueryParams(objectMapper.readValue(requestQueryParams, Map.class));
        }
        
        // Process advanced pattern matching
        processAdvancedPatterns(request, pathMatchingMode, pathMatchType, pathPattern, pathIgnoreCase,
                              headerMatchingMode, headerPatternsJson, queryParamMatchingMode, 
                              queryParamPatternsJson, bodyPatternsJson);
        
        mapping.setRequest(request);
        
        // Build response
        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(responseStatus);
        response.setBody(responseBody);
        response.setTemplatingEnabled(templatingEnabled);
        
        if (responseHeaders != null && !responseHeaders.trim().isEmpty()) {
            response.setHeaders(objectMapper.readValue(responseHeaders, Map.class));
        }
        
        mapping.setResponse(response);
        
        // Build delays
        if (delayMode != null) {
            RequestMapping.DelaySpec delays = new RequestMapping.DelaySpec();
            delays.setMode(delayMode);
            delays.setFixedMs(fixedMs);
            delays.setVariableMinMs(variableMinMs);
            delays.setVariableMaxMs(variableMaxMs);
            delays.setErrorRatePercent(errorRatePercent);
            
            if (errorStatus != null && errorBody != null) {
                RequestMapping.ErrorResponse errorResponse = new RequestMapping.ErrorResponse();
                errorResponse.setStatus(errorStatus);
                errorResponse.setBody(errorBody);
                delays.setErrorResponse(errorResponse);
            }
            
            mapping.setDelays(delays);
        }
        
        return mapping;
    }
    
    private void processAdvancedPatterns(RequestMapping.RequestSpec request, 
                                       String pathMatchingMode, String pathMatchType, String pathPattern, Boolean pathIgnoreCase,
                                       String headerMatchingMode, String headerPatternsJson,
                                       String queryParamMatchingMode, String queryParamPatternsJson,
                                       String bodyPatternsJson) throws Exception {
        
        // Process Path Pattern
        if ("advanced".equals(pathMatchingMode) && pathMatchType != null && pathPattern != null) {
            RequestMapping.PathPattern pathPatternObj = new RequestMapping.PathPattern();
            pathPatternObj.setMatchType(RequestMapping.PathPattern.MatchType.valueOf(pathMatchType.toUpperCase()));
            pathPatternObj.setPattern(pathPattern);
            pathPatternObj.setIgnoreCase(pathIgnoreCase != null ? pathIgnoreCase : false);
            request.setPathPattern(pathPatternObj);
        }
        
        // Process Header Patterns
        if ("advanced".equals(headerMatchingMode) && headerPatternsJson != null && !headerPatternsJson.trim().isEmpty()) {
            try {
                java.util.Map<String, RequestMapping.ParameterPattern> headerPatterns = objectMapper.readValue(
                    headerPatternsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, RequestMapping.ParameterPattern>>() {});
                request.setHeaderPatterns(headerPatterns);
            } catch (Exception e) {
                logger.warn("Failed to parse header patterns JSON: {}", e.getMessage());
            }
        }
        
        // Process Query Parameter Patterns
        if ("advanced".equals(queryParamMatchingMode) && queryParamPatternsJson != null && !queryParamPatternsJson.trim().isEmpty()) {
            try {
                java.util.Map<String, RequestMapping.ParameterPattern> queryParamPatterns = objectMapper.readValue(
                    queryParamPatternsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, RequestMapping.ParameterPattern>>() {});
                request.setQueryParamPatterns(queryParamPatterns);
            } catch (Exception e) {
                logger.warn("Failed to parse query parameter patterns JSON: {}", e.getMessage());
            }
        }
        
        // Process Body Patterns
        if (bodyPatternsJson != null && !bodyPatternsJson.trim().isEmpty()) {
            try {
                java.util.List<RequestMapping.BodyPattern> bodyPatterns = objectMapper.readValue(
                    bodyPatternsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<RequestMapping.BodyPattern>>() {});
                request.setBodyPatterns(bodyPatterns);
            } catch (Exception e) {
                logger.warn("Failed to parse body patterns JSON: {}", e.getMessage());
            }
        }
    }
}