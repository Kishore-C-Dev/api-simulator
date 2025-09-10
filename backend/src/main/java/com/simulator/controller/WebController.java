package com.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulator.model.RequestMapping;
import com.simulator.service.MappingService;
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
import java.util.Map;

@Controller
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    @Autowired
    private MappingService mappingService;

    @Autowired
    private ObjectMapper objectMapper;
    
    public WebController() {
        logger.info("WebController loaded with form endpoints");
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            Model model) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "priority"));
        Page<RequestMapping> mappings = mappingService.searchMappings(search, pageable);
        
        model.addAttribute("mappings", mappings);
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", mappings.getTotalPages());
        
        return "dashboard";
    }

    @GetMapping("/mappings/new")
    public String newMapping(Model model) {
        model.addAttribute("mapping", new RequestMapping());
        model.addAttribute("isEdit", false);
        // Add empty JSON strings for new mappings
        model.addAttribute("requestHeadersJson", "{}");
        model.addAttribute("requestQueryParamsJson", "{}");
        model.addAttribute("responseHeadersJson", "{}");
        return "mapping-form";
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
            @RequestParam(required = false) String errorBody) {
        
        try {
            logger.info("Creating mapping from form submission");
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags, 
                requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs, 
                errorRatePercent, errorStatus, errorBody);
            
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
            @RequestParam(required = false) String errorBody) {
        
        try {
            logger.info("Updating mapping {} from form submission", id);
            RequestMapping mapping = buildMappingFromForm(name, priority, enabled, tags, 
                requestMethod, requestPath, requestHeaders, requestQueryParams,
                responseStatus, responseHeaders, responseBody, templatingEnabled,
                delayMode, fixedMs, variableMinMs, variableMaxMs, 
                errorRatePercent, errorStatus, errorBody);
            
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
            Integer errorRatePercent, Integer errorStatus, String errorBody) throws Exception {
        
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
}