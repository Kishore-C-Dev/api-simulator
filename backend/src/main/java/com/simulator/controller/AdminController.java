package com.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulator.model.RequestMapping;
import com.simulator.service.MappingService;
import com.simulator.service.ConditionalResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@org.springframework.web.bind.annotation.RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private MappingService mappingService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ConditionalResponseService conditionalResponseService;

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
            @RequestParam(required = false) String conditionalResponsesJson) {
        
        try {
            logger.info("Form POST request to /admin/mappings/{} (HTML Form)", id);
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags, 
                requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs, 
                errorRatePercent, errorStatus, errorBody,
                conditionalResponsesEnabled, requestIdHeader, conditionalResponsesJson);
            
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
            @RequestParam(required = false) String conditionalResponsesJson) {
        
        try {
            logger.info("Form POST request to /admin/mappings (HTML Form)");
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags, 
                requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs, 
                errorRatePercent, errorStatus, errorBody,
                conditionalResponsesEnabled, requestIdHeader, conditionalResponsesJson);
            
            RequestMapping saved = mappingService.saveMapping(mapping);
            
            // Return a redirect response for form submissions
            return ResponseEntity.status(302).header("Location", "/").build();
        } catch (Exception e) {
            logger.error("Failed to create mapping from form: {}", e.getMessage());
            return ResponseEntity.status(302).header("Location", "/mappings/new?error=true").build();
        }
    }

    private RequestMapping buildMappingFromForm(String name, Integer priority, Boolean enabled, String tags,
            String requestMethod, String requestPath, String requestHeaders, String requestQueryParams,
            Integer responseStatus, String responseHeaders, String responseBody, Boolean templatingEnabled,
            String delayMode, Integer fixedMs, Integer variableMinMs, Integer variableMaxMs,
            Integer errorRatePercent, Integer errorStatus, String errorBody,
            Boolean conditionalResponsesEnabled, String requestIdHeader, String conditionalResponsesJson) throws Exception {
        
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
        
        mapping.setRequest(request);
        
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
    
}