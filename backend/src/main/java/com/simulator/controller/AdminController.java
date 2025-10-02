package com.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulator.model.RequestMapping;
import com.simulator.model.UserProfile;
import com.simulator.model.Namespace;
import com.simulator.model.EndpointType;
import com.simulator.model.GraphQLSpec;
import com.simulator.service.MappingService;
import com.simulator.service.ConditionalResponseService;
import com.simulator.service.UserService;
import com.simulator.service.SessionManager;
import com.simulator.repository.UserProfileRepository;
import com.simulator.repository.NamespaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

@Controller
@org.springframework.web.bind.annotation.RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private MappingService mappingService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ConditionalResponseService conditionalResponseService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private UserProfileRepository userRepository;
    
    @Autowired
    private NamespaceRepository namespaceRepository;

    @GetMapping("/mappings")
    public ResponseEntity<Page<RequestMapping>> getMappings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "priority") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search) {
        
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<RequestMapping> mappings = mappingService.searchMappings(search, pageable);
        return ResponseEntity.ok(mappings);
    }

    @GetMapping("/mappings/{id}")
    public ResponseEntity<RequestMapping> getMapping(@PathVariable String id) {
        return mappingService.getMapping(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/mappings", consumes = "application/json")
    public ResponseEntity<RequestMapping> createMapping(@RequestBody RequestMapping mapping) {
        try {
            RequestMapping saved = mappingService.saveMapping(mapping);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            logger.error("Failed to create mapping: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/mappings/{id}")
    public ResponseEntity<RequestMapping> updateMapping(@PathVariable String id, @RequestBody RequestMapping mapping) {
        try {
            logger.info("PUT request to /admin/mappings/{} (REST API)", id);
            mapping.setId(id);
            RequestMapping saved = mappingService.saveMapping(mapping);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            logger.error("Failed to update mapping: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/mappings/{id}/form", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<?> updateMappingForm(
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
            @RequestParam(defaultValue = "false") Boolean conditionalResponsesEnabled,
            @RequestParam(required = false, defaultValue = "X-Request-ID") String requestIdHeader,
            @RequestParam(required = false) String conditionalResponsesJson,
            // Advanced pattern matching parameters
            @RequestParam(required = false) String pathMatchingMode,
            @RequestParam(required = false) String pathMatchType,
            @RequestParam(required = false) String pathPattern,
            @RequestParam(defaultValue = "false") Boolean pathIgnoreCase,
            @RequestParam(required = false) String headerMatchingMode,
            @RequestParam(required = false) String headerPatternsJson,
            @RequestParam(required = false) String queryParamMatchingMode,
            @RequestParam(required = false) String queryParamPatternsJson,
            @RequestParam(required = false) String bodyPatternsJson,
            // GraphQL-specific parameters
            @RequestParam(required = false) String endpointType,
            @RequestParam(required = false) String graphqlOperationType,
            @RequestParam(required = false) String graphqlOperationName,
            @RequestParam(required = false) String graphqlSchema,
            @RequestParam(required = false) String graphqlQuery,
            @RequestParam(required = false) String graphqlVariables,
            @RequestParam(required = false) String graphqlData,
            @RequestParam(required = false) String graphqlErrors,
            @RequestParam(required = false) String graphqlExtensions) {
        
        try {
            logger.info("Form POST request to /admin/mappings/{} (HTML Form)", id);
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags,
                endpointType, requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs,
                errorRatePercent, errorStatus, errorBody,
                conditionalResponsesEnabled, requestIdHeader, conditionalResponsesJson,
                pathMatchingMode, pathMatchType, pathPattern, pathIgnoreCase,
                headerMatchingMode, headerPatternsJson, queryParamMatchingMode,
                queryParamPatternsJson, bodyPatternsJson,
                graphqlOperationType, graphqlOperationName, graphqlSchema,
                graphqlQuery, graphqlVariables, graphqlData, graphqlErrors, graphqlExtensions);
            
            mapping.setId(id);
            RequestMapping saved = mappingService.saveMapping(mapping);
            
            // Return a redirect response for form submissions
            return ResponseEntity.status(302).header("Location", "/").build();
        } catch (Exception e) {
            logger.error("Failed to update mapping from form: {}", e.getMessage());
            return ResponseEntity.status(302).header("Location", "/mappings/" + id + "/edit?error=true").build();
        }
    }

    @PostMapping(value = "/mappings/form", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<?> createMappingForm(
            @RequestParam String name,
            @RequestParam Integer priority,
            @RequestParam(defaultValue = "false") Boolean enabled,
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "REST") String endpointType,
            @RequestParam(required = false) String requestMethod,
            @RequestParam(required = false) String requestPath,
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
            @RequestParam(defaultValue = "false") Boolean conditionalResponsesEnabled,
            @RequestParam(required = false, defaultValue = "X-Request-ID") String requestIdHeader,
            @RequestParam(required = false) String conditionalResponsesJson,
            // Advanced pattern matching parameters
            @RequestParam(required = false) String pathMatchingMode,
            @RequestParam(required = false) String pathMatchType,
            @RequestParam(required = false) String pathPattern,
            @RequestParam(defaultValue = "false") Boolean pathIgnoreCase,
            @RequestParam(required = false) String headerMatchingMode,
            @RequestParam(required = false) String headerPatternsJson,
            @RequestParam(required = false) String queryParamMatchingMode,
            @RequestParam(required = false) String queryParamPatternsJson,
            @RequestParam(required = false) String bodyPatternsJson,
            // GraphQL-specific parameters
            @RequestParam(required = false) String graphqlOperationType,
            @RequestParam(required = false) String graphqlOperationName,
            @RequestParam(required = false) String graphqlSchema,
            @RequestParam(required = false) String graphqlQuery,
            @RequestParam(required = false) String graphqlVariables,
            @RequestParam(required = false) String graphqlData,
            @RequestParam(required = false) String graphqlErrors,
            @RequestParam(required = false) String graphqlExtensions) {

        try {
            logger.info("Form POST request to /admin/mappings (HTML Form)");
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags, endpointType,
                requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs,
                errorRatePercent, errorStatus, errorBody,
                conditionalResponsesEnabled, requestIdHeader, conditionalResponsesJson,
                pathMatchingMode, pathMatchType, pathPattern, pathIgnoreCase,
                headerMatchingMode, headerPatternsJson, queryParamMatchingMode,
                queryParamPatternsJson, bodyPatternsJson,
                graphqlOperationType, graphqlOperationName, graphqlSchema,
                graphqlQuery, graphqlVariables, graphqlData, graphqlErrors, graphqlExtensions);
            
            RequestMapping saved = mappingService.saveMapping(mapping);
            
            // Return a redirect response for form submissions
            return ResponseEntity.status(302).header("Location", "/").build();
        } catch (Exception e) {
            logger.error("Failed to create mapping from form: {}", e.getMessage());
            return ResponseEntity.status(302).header("Location", "/mappings/new?error=true").build();
        }
    }

    private RequestMapping buildMappingFromForm(String name, Integer priority, Boolean enabled, String tags,
            String endpointType, String requestMethod, String requestPath, String requestHeaders, String requestQueryParams,
            Integer responseStatus, String responseHeaders, String responseBody, Boolean templatingEnabled,
            String delayMode, Integer fixedMs, Integer variableMinMs, Integer variableMaxMs,
            Integer errorRatePercent, Integer errorStatus, String errorBody,
            Boolean conditionalResponsesEnabled, String requestIdHeader, String conditionalResponsesJson,
            // Advanced pattern matching parameters
            String pathMatchingMode, String pathMatchType, String pathPattern, Boolean pathIgnoreCase,
            String headerMatchingMode, String headerPatternsJson,
            String queryParamMatchingMode, String queryParamPatternsJson,
            String bodyPatternsJson,
            // GraphQL-specific parameters
            String graphqlOperationType, String graphqlOperationName, String graphqlSchema,
            String graphqlQuery, String graphqlVariables, String graphqlData,
            String graphqlErrors, String graphqlExtensions) throws Exception {
        
        RequestMapping mapping = new RequestMapping();
        mapping.setName(name);
        mapping.setPriority(priority != null ? priority : 5);
        mapping.setEnabled(enabled != null ? enabled : true);

        // Set endpoint type
        try {
            mapping.setEndpointType(EndpointType.valueOf(endpointType.toUpperCase()));
        } catch (Exception e) {
            mapping.setEndpointType(EndpointType.REST); // Default fallback
        }

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

        // Build GraphQL spec if this is a GraphQL endpoint
        if (EndpointType.GRAPHQL.equals(mapping.getEndpointType())) {
            GraphQLSpec graphqlSpec = new GraphQLSpec();

            if (graphqlOperationType != null && !graphqlOperationType.trim().isEmpty()) {
                try {
                    graphqlSpec.setOperationType(GraphQLSpec.OperationType.valueOf(graphqlOperationType.toUpperCase()));
                } catch (Exception e) {
                    graphqlSpec.setOperationType(GraphQLSpec.OperationType.QUERY); // Default
                }
            }

            if (graphqlOperationName != null && !graphqlOperationName.trim().isEmpty()) {
                graphqlSpec.setOperationName(graphqlOperationName);
            }

            if (graphqlSchema != null && !graphqlSchema.trim().isEmpty()) {
                graphqlSpec.setSchema(graphqlSchema);
            }

            if (graphqlQuery != null && !graphqlQuery.trim().isEmpty()) {
                graphqlSpec.setQuery(graphqlQuery);
            }

            if (graphqlVariables != null && !graphqlVariables.trim().isEmpty()) {
                try {
                    Map<String, Object> variables = objectMapper.readValue(graphqlVariables, Map.class);
                    graphqlSpec.setVariables(variables);
                } catch (Exception e) {
                    logger.warn("Failed to parse GraphQL variables JSON: {}", e.getMessage());
                }
            }

            mapping.setGraphQLSpec(graphqlSpec);
        }

        // Build response
        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(responseStatus);
        response.setBody(responseBody);
        response.setTemplatingEnabled(templatingEnabled);
        
        if (responseHeaders != null && !responseHeaders.trim().isEmpty()) {
            response.setHeaders(objectMapper.readValue(responseHeaders, Map.class));
        }
        
        // Build conditional responses if enabled
        if (Boolean.TRUE.equals(conditionalResponsesEnabled)) {
            RequestMapping.ConditionalResponses conditionalResponses = new RequestMapping.ConditionalResponses();
            conditionalResponses.setEnabled(true);
            conditionalResponses.setRequestIdHeader(requestIdHeader != null && !requestIdHeader.trim().isEmpty() ? 
                requestIdHeader : "X-Request-ID");
            
            // Parse conditional responses JSON or use default
            if (conditionalResponsesJson != null && !conditionalResponsesJson.trim().isEmpty()) {
                try {
                    RequestMapping.RequestIdMapping[] mappings = objectMapper.readValue(
                        conditionalResponsesJson, RequestMapping.RequestIdMapping[].class);
                    conditionalResponses.setRequestIdMappings(Arrays.asList(mappings));
                } catch (Exception e) {
                    logger.warn("Failed to parse conditional responses JSON, using defaults: {}", e.getMessage());
                    conditionalResponses = conditionalResponseService.createDefaultConditionalResponses();
                }
            } else {
                conditionalResponses = conditionalResponseService.createDefaultConditionalResponses();
            }
            
            response.setConditionalResponses(conditionalResponses);
        }

        // Build GraphQL response spec if this is a GraphQL endpoint
        if (EndpointType.GRAPHQL.equals(mapping.getEndpointType())) {
            RequestMapping.GraphQLResponseSpec graphqlResponse = new RequestMapping.GraphQLResponseSpec();

            if (graphqlData != null && !graphqlData.trim().isEmpty()) {
                try {
                    Object data = objectMapper.readValue(graphqlData, Object.class);
                    graphqlResponse.setData(data);
                } catch (Exception e) {
                    logger.warn("Failed to parse GraphQL data JSON: {}", e.getMessage());
                }
            }

            if (graphqlErrors != null && !graphqlErrors.trim().isEmpty()) {
                try {
                    List<RequestMapping.GraphQLResponseSpec.GraphQLError> errors =
                        objectMapper.readValue(graphqlErrors,
                            objectMapper.getTypeFactory().constructCollectionType(List.class,
                                RequestMapping.GraphQLResponseSpec.GraphQLError.class));
                    graphqlResponse.setErrors(errors);
                } catch (Exception e) {
                    logger.warn("Failed to parse GraphQL errors JSON: {}", e.getMessage());
                }
            }

            if (graphqlExtensions != null && !graphqlExtensions.trim().isEmpty()) {
                try {
                    Map<String, Object> extensions = objectMapper.readValue(graphqlExtensions, Map.class);
                    graphqlResponse.setExtensions(extensions);
                } catch (Exception e) {
                    logger.warn("Failed to parse GraphQL extensions JSON: {}", e.getMessage());
                }
            }

            response.setGraphQLResponse(graphqlResponse);

            // For GraphQL, always set status to 200 and content-type to application/json
            response.setStatus(200);
            if (response.getHeaders() == null) {
                response.setHeaders(new HashMap<>());
            }
            response.getHeaders().put("Content-Type", "application/json");

            // Build GraphQL response body
            Map<String, Object> graphqlResponseBody = new HashMap<>();
            if (graphqlResponse.getData() != null) {
                graphqlResponseBody.put("data", graphqlResponse.getData());
            }
            if (graphqlResponse.getErrors() != null && !graphqlResponse.getErrors().isEmpty()) {
                graphqlResponseBody.put("errors", graphqlResponse.getErrors());
            }
            if (graphqlResponse.getExtensions() != null) {
                graphqlResponseBody.put("extensions", graphqlResponse.getExtensions());
            }

            try {
                response.setBody(objectMapper.writeValueAsString(graphqlResponseBody));
            } catch (Exception e) {
                logger.warn("Failed to serialize GraphQL response body: {}", e.getMessage());
            }
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
                Map<String, RequestMapping.ParameterPattern> headerPatterns = objectMapper.readValue(
                    headerPatternsJson, new TypeReference<Map<String, RequestMapping.ParameterPattern>>() {});
                request.setHeaderPatterns(headerPatterns);
            } catch (Exception e) {
                logger.warn("Failed to parse header patterns JSON: {}", e.getMessage());
            }
        }
        
        // Process Query Parameter Patterns
        if ("advanced".equals(queryParamMatchingMode) && queryParamPatternsJson != null && !queryParamPatternsJson.trim().isEmpty()) {
            try {
                Map<String, RequestMapping.ParameterPattern> queryParamPatterns = objectMapper.readValue(
                    queryParamPatternsJson, new TypeReference<Map<String, RequestMapping.ParameterPattern>>() {});
                request.setQueryParamPatterns(queryParamPatterns);
            } catch (Exception e) {
                logger.warn("Failed to parse query parameter patterns JSON: {}", e.getMessage());
            }
        }
        
        // Process Body Patterns
        if (bodyPatternsJson != null && !bodyPatternsJson.trim().isEmpty()) {
            try {
                List<RequestMapping.BodyPattern> bodyPatterns = objectMapper.readValue(
                    bodyPatternsJson, new TypeReference<List<RequestMapping.BodyPattern>>() {});
                request.setBodyPatterns(bodyPatterns);
            } catch (Exception e) {
                logger.warn("Failed to parse body patterns JSON: {}", e.getMessage());
            }
        }
    }

    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<String> deleteMapping(@PathVariable String id) {
        try {
            mappingService.deleteMapping(id);
            return ResponseEntity.ok("");
        } catch (Exception e) {
            logger.error("Failed to delete mapping: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Delete failed: " + e.getMessage());
        }
    }

    @PostMapping("/mappings/refresh")
    public ResponseEntity<Map<String, Object>> refreshMappings() {
        try {
            mappingService.reloadWireMockMappings();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Mappings reloaded successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to refresh mappings: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importMappings(@RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes());
            RequestMapping[] mappings = objectMapper.readValue(content, RequestMapping[].class);
            
            mappingService.importMappings(Arrays.asList(mappings));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("imported", mappings.length);
            response.put("message", "Successfully imported " + mappings.length + " mappings");
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Failed to import mappings: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Invalid JSON format: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Failed to import mappings: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/export")
    public ResponseEntity<List<RequestMapping>> exportMappings(@RequestParam(required = false) String dataset) {
        try {
            List<RequestMapping> mappings = mappingService.getAllMappings();
            return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=mappings.json")
                .header("Content-Type", "application/json")
                .body(mappings);
        } catch (Exception e) {
            logger.error("Failed to export mappings: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Check if current user is admin
     */
    private boolean isAdmin(HttpSession session) {
        UserProfile user = sessionManager.getCurrentUser(session);
        return user != null && "admin".equals(user.getUserId());
    }
    
    /**
     * User Management Dashboard
     */
    @GetMapping("/users")
    public String userManagement(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(defaultValue = "") String search,
                               HttpSession session,
                               Model model) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("userId"));
        Page<UserProfile> users;
        
        if (search.trim().isEmpty()) {
            users = userRepository.findAllNonDeleted(pageable);
        } else {
            users = userRepository.findByUserIdContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                search, search, search, pageable);
        }
        
        model.addAttribute("users", users);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", users.getTotalPages());
        model.addAttribute("title", "User Management");
        
        // Add current user info for layout
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("userNamespaces", userService.getUserNamespaces(currentUser.getUserId()));
        model.addAttribute("currentNamespace", sessionManager.getCurrentNamespace(session));
        
        return "admin/users";
    }
    
    /**
     * Show create user form
     */
    @GetMapping("/users/new")
    public String showCreateUserForm(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        List<Namespace> allNamespaces = namespaceRepository.findAll();
        model.addAttribute("namespaces", allNamespaces);
        model.addAttribute("title", "Create New User");
        
        // Add current user info for layout
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("userNamespaces", userService.getUserNamespaces(currentUser.getUserId()));
        model.addAttribute("currentNamespace", sessionManager.getCurrentNamespace(session));
        
        return "admin/user-form";
    }
    
    /**
     * Create new user
     */
    @PostMapping("/users")
    public String createUser(@RequestParam String userId,
                           @RequestParam String email,
                           @RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam String password,
                           @RequestParam(required = false) List<String> namespaces,
                           @RequestParam(required = false) String defaultNamespace,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        try {
            // Check if user already exists
            if (userRepository.existsByUserId(userId)) {
                redirectAttributes.addFlashAttribute("error", "User ID already exists: " + userId);
                return "redirect:/admin/users/new";
            }
            
            // Create user
            UserProfile user = userService.createUser(userId, email, firstName, lastName, password);
            
            // Assign namespaces
            if (namespaces != null && !namespaces.isEmpty()) {
                for (String namespace : namespaces) {
                    user.addNamespace(namespace);
                    
                    // Add user to namespace members
                    Optional<Namespace> ns = namespaceRepository.findByName(namespace);
                    if (ns.isPresent()) {
                        ns.get().addMember(userId);
                        namespaceRepository.save(ns.get());
                    }
                }
                
                // Set default namespace
                if (defaultNamespace != null && user.hasNamespace(defaultNamespace)) {
                    user.setDefaultNamespace(defaultNamespace);
                }
            }
            
            userRepository.save(user);
            
            logger.info("Admin {} created new user: {}", sessionManager.getCurrentUser(session).getUserId(), userId);
            redirectAttributes.addFlashAttribute("success", "User created successfully: " + userId);
            
        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
            return "redirect:/admin/users/new";
        }
        
        return "redirect:/admin/users";
    }
    
    /**
     * Show edit user form
     */
    @GetMapping("/users/{userId}/edit")
    public String showEditUserForm(@PathVariable String userId,
                                 HttpSession session,
                                 Model model) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        Optional<UserProfile> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return "redirect:/admin/users";
        }
        
        UserProfile user = userOpt.get();
        List<Namespace> allNamespaces = namespaceRepository.findAll();
        
        model.addAttribute("user", user);
        model.addAttribute("namespaces", allNamespaces);
        model.addAttribute("title", "Edit User: " + userId);
        
        // Add current user info for layout
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("userNamespaces", userService.getUserNamespaces(currentUser.getUserId()));
        model.addAttribute("currentNamespace", sessionManager.getCurrentNamespace(session));
        
        return "admin/user-edit";
    }
    
    /**
     * Update user
     */
    @PostMapping("/users/{userId}")
    public String updateUser(@PathVariable String userId,
                           @RequestParam String email,
                           @RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam(required = false) String password,
                           @RequestParam(required = false) List<String> namespaces,
                           @RequestParam(required = false) String defaultNamespace,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        try {
            Optional<UserProfile> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "User not found: " + userId);
                return "redirect:/admin/users";
            }
            
            UserProfile user = userOpt.get();
            
            // Update basic info
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            
            // Update password if provided
            if (password != null && !password.trim().isEmpty()) {
                user.setPasswordHash(userService.hashPassword(password));
            }
            
            // Remove user from all current namespaces
            List<String> oldNamespaces = List.copyOf(user.getNamespaces());
            for (String namespace : oldNamespaces) {
                Optional<Namespace> ns = namespaceRepository.findByName(namespace);
                if (ns.isPresent()) {
                    ns.get().removeMember(userId);
                    namespaceRepository.save(ns.get());
                }
            }
            user.getNamespaces().clear();
            
            // Add new namespaces
            if (namespaces != null && !namespaces.isEmpty()) {
                for (String namespace : namespaces) {
                    user.addNamespace(namespace);
                    
                    // Add user to namespace members
                    Optional<Namespace> ns = namespaceRepository.findByName(namespace);
                    if (ns.isPresent()) {
                        ns.get().addMember(userId);
                        namespaceRepository.save(ns.get());
                    }
                }
                
                // Set default namespace
                if (defaultNamespace != null && user.hasNamespace(defaultNamespace)) {
                    user.setDefaultNamespace(defaultNamespace);
                }
            }
            
            userRepository.save(user);
            
            logger.info("Admin {} updated user: {}", sessionManager.getCurrentUser(session).getUserId(), userId);
            redirectAttributes.addFlashAttribute("success", "User updated successfully: " + userId);
            
        } catch (Exception e) {
            logger.error("Error updating user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update user: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }
    
    /**
     * Delete user
     */
    @DeleteMapping("/users/{userId}")
    @ResponseBody
    public String deleteUser(@PathVariable String userId,
                           HttpSession session) {
        if (!isAdmin(session)) {
            return "Access denied";
        }
        
        try {
            // Don't allow deleting admin user
            if ("admin".equals(userId)) {
                return "Cannot delete admin user";
            }
            
            Optional<UserProfile> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isEmpty()) {
                return "User not found";
            }
            
            UserProfile user = userOpt.get();
            
            // Remove user from all namespaces
            for (String namespace : user.getNamespaces()) {
                Optional<Namespace> ns = namespaceRepository.findByName(namespace);
                if (ns.isPresent()) {
                    ns.get().removeMember(userId);
                    namespaceRepository.save(ns.get());
                }
            }
            
            // Soft delete: set deleted flag instead of actual deletion
            user.setDeleted(true);
            userRepository.save(user);
            
            logger.info("Admin {} marked user as deleted: {}", sessionManager.getCurrentUser(session).getUserId(), userId);
            return "User marked as deleted successfully";
            
        } catch (Exception e) {
            logger.error("Error deleting user: {}", e.getMessage());
            return "Failed to delete user: " + e.getMessage();
        }
    }
    
    /**
     * Namespace Management Dashboard
     */
    @GetMapping("/namespaces")
    public String namespaceManagement(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(defaultValue = "") String search,
                                    HttpSession session,
                                    Model model) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        Page<Namespace> namespaces;
        
        if (search.trim().isEmpty()) {
            namespaces = namespaceRepository.findAllNonDeleted(pageable);
        } else {
            namespaces = namespaceRepository.findByNameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                search, search, pageable);
        }
        
        model.addAttribute("namespaces", namespaces);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", namespaces.getTotalPages());
        model.addAttribute("title", "Namespace Management");
        
        // Add current user info for layout
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("userNamespaces", userService.getUserNamespaces(currentUser.getUserId()));
        model.addAttribute("currentNamespace", sessionManager.getCurrentNamespace(session));
        
        return "admin/namespaces";
    }
    
    /**
     * Show create namespace form
     */
    @GetMapping("/namespaces/new")
    public String showCreateNamespaceForm(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        model.addAttribute("title", "Create New Namespace");
        
        // Add current user info for layout
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("userNamespaces", userService.getUserNamespaces(currentUser.getUserId()));
        model.addAttribute("currentNamespace", sessionManager.getCurrentNamespace(session));
        
        return "admin/namespace-form";
    }
    
    /**
     * Create new namespace
     */
    @PostMapping("/namespaces")
    public String createNamespace(@RequestParam String name,
                                @RequestParam String displayName,
                                @RequestParam(required = false) String description,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        try {
            // Check if namespace already exists
            if (namespaceRepository.existsByName(name)) {
                redirectAttributes.addFlashAttribute("error", "Namespace already exists: " + name);
                return "redirect:/admin/namespaces/new";
            }
            
            // Create namespace
            Namespace namespace = new Namespace(name, displayName, sessionManager.getCurrentUser(session).getUserId());
            if (description != null && !description.trim().isEmpty()) {
                namespace.setDescription(description);
            }
            
            namespaceRepository.save(namespace);
            
            logger.info("Admin {} created new namespace: {}", sessionManager.getCurrentUser(session).getUserId(), name);
            redirectAttributes.addFlashAttribute("success", "Namespace created successfully: " + name);
            
        } catch (Exception e) {
            logger.error("Error creating namespace: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create namespace: " + e.getMessage());
            return "redirect:/admin/namespaces/new";
        }
        
        return "redirect:/admin/namespaces";
    }
    
    /**
     * Show edit namespace form
     */
    @GetMapping("/namespaces/{name}/edit")
    public String showEditNamespaceForm(@PathVariable String name,
                                      HttpSession session,
                                      Model model) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(name);
        if (namespaceOpt.isEmpty()) {
            return "redirect:/admin/namespaces";
        }
        
        Namespace namespace = namespaceOpt.get();
        List<UserProfile> allUsers = userRepository.findAll();
        
        model.addAttribute("namespace", namespace);
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("title", "Edit Namespace: " + name);
        
        // Add current user info for layout
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("userNamespaces", userService.getUserNamespaces(currentUser.getUserId()));
        model.addAttribute("currentNamespace", sessionManager.getCurrentNamespace(session));
        
        return "admin/namespace-edit";
    }
    
    /**
     * Update namespace
     */
    @PostMapping("/namespaces/{name}")
    public String updateNamespace(@PathVariable String name,
                                @RequestParam String displayName,
                                @RequestParam(required = false) String description,
                                @RequestParam(required = false) List<String> members,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/";
        }
        
        try {
            Optional<Namespace> namespaceOpt = namespaceRepository.findByName(name);
            if (namespaceOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Namespace not found: " + name);
                return "redirect:/admin/namespaces";
            }
            
            Namespace namespace = namespaceOpt.get();
            
            // Update basic info
            namespace.setDisplayName(displayName);
            if (description != null) {
                namespace.setDescription(description);
            }
            
            // Remove namespace from all current users
            List<String> oldMembers = List.copyOf(namespace.getMembers());
            for (String userId : oldMembers) {
                Optional<UserProfile> user = userRepository.findByUserId(userId);
                if (user.isPresent()) {
                    user.get().removeNamespace(name);
                    userRepository.save(user.get());
                }
            }
            namespace.getMembers().clear();
            
            // Add new members
            if (members != null && !members.isEmpty()) {
                for (String userId : members) {
                    namespace.addMember(userId);
                    
                    // Add namespace to user
                    Optional<UserProfile> user = userRepository.findByUserId(userId);
                    if (user.isPresent()) {
                        user.get().addNamespace(name);
                        userRepository.save(user.get());
                    }
                }
            }
            
            namespaceRepository.save(namespace);
            
            logger.info("Admin {} updated namespace: {}", sessionManager.getCurrentUser(session).getUserId(), name);
            redirectAttributes.addFlashAttribute("success", "Namespace updated successfully: " + name);
            
        } catch (Exception e) {
            logger.error("Error updating namespace: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update namespace: " + e.getMessage());
        }
        
        return "redirect:/admin/namespaces";
    }
    
    /**
     * Delete namespace
     */
    @DeleteMapping("/namespaces/{name}")
    @ResponseBody
    public String deleteNamespace(@PathVariable String name,
                                HttpSession session) {
        if (!isAdmin(session)) {
            return "Access denied";
        }
        
        try {
            // Don't allow deleting default namespace
            if ("default".equals(name)) {
                return "Cannot delete default namespace";
            }
            
            Optional<Namespace> namespaceOpt = namespaceRepository.findByName(name);
            if (namespaceOpt.isEmpty()) {
                return "Namespace not found";
            }
            
            Namespace namespace = namespaceOpt.get();
            
            // Remove namespace from all users
            for (String userId : namespace.getMembers()) {
                Optional<UserProfile> user = userRepository.findByUserId(userId);
                if (user.isPresent()) {
                    user.get().removeNamespace(name);
                    userRepository.save(user.get());
                }
            }
            
            // Soft delete: set deleted flag instead of actual deletion
            namespace.setDeleted(true);
            namespaceRepository.save(namespace);
            
            logger.info("Admin {} marked namespace as deleted: {}", sessionManager.getCurrentUser(session).getUserId(), name);
            return "Namespace marked as deleted successfully";
            
        } catch (Exception e) {
            logger.error("Error deleting namespace: {}", e.getMessage());
            return "Failed to delete namespace: " + e.getMessage();
        }
    }
    
}