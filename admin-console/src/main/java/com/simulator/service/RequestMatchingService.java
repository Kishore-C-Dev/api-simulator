package com.simulator.service;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.simulator.model.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class RequestMatchingService {

    private static final Logger logger = LoggerFactory.getLogger(RequestMatchingService.class);

    public boolean matches(HttpServletRequest request, String requestBody, RequestMapping mapping) {
        try {
            RequestMapping.RequestSpec spec = mapping.getRequest();
            
            // Check method
            if (!matchesMethod(request.getMethod(), spec.getMethod())) {
                logger.debug("Method mismatch: {} vs {}", request.getMethod(), spec.getMethod());
                return false;
            }

            // Check path (exact match for backward compatibility)
            if (!matchesPath(request.getRequestURI(), spec)) {
                logger.debug("Path mismatch: {} vs spec", request.getRequestURI());
                return false;
            }

            // Check query parameters
            if (!matchesQueryParams(request, spec)) {
                logger.debug("Query parameters mismatch");
                return false;
            }

            // Check headers
            if (!matchesHeaders(request, spec)) {
                logger.debug("Headers mismatch");
                return false;
            }

            // Check body patterns
            if (!matchesBody(requestBody, spec)) {
                logger.debug("Body patterns mismatch");
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("Error matching request", e);
            return false;
        }
    }

    private boolean matchesMethod(String requestMethod, String specMethod) {
        if (specMethod == null || specMethod.trim().isEmpty()) {
            return true; // No method specified means match all
        }
        return requestMethod.equalsIgnoreCase(specMethod);
    }

    private boolean matchesPath(String requestPath, RequestMapping.RequestSpec spec) {
        // Check exact path match first (backward compatibility)
        if (spec.getPath() != null && !spec.getPath().trim().isEmpty()) {
            return requestPath.equals(spec.getPath());
        }

        // Check advanced path patterns
        if (spec.getPathPattern() != null) {
            return matchesPathPattern(requestPath, spec.getPathPattern());
        }

        return true; // No path specified means match all
    }

    private boolean matchesPathPattern(String requestPath, RequestMapping.PathPattern pathPattern) {
        String pattern = pathPattern.getPattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            return true;
        }

        String targetPath = pathPattern.isIgnoreCase() ? requestPath.toLowerCase() : requestPath;
        String targetPattern = pathPattern.isIgnoreCase() ? pattern.toLowerCase() : pattern;

        switch (pathPattern.getMatchType()) {
            case EXACT:
                return targetPath.equals(targetPattern);
            
            case WILDCARD:
                // Convert wildcard pattern to regex (* -> .*, ? -> .?)
                String regexPattern = targetPattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".?");
                return targetPath.matches(regexPattern);
                
            case REGEX:
                try {
                    Pattern regexComp = pathPattern.isIgnoreCase() ? 
                        Pattern.compile(pattern, Pattern.CASE_INSENSITIVE) : 
                        Pattern.compile(pattern);
                    return regexComp.matcher(requestPath).matches();
                } catch (PatternSyntaxException e) {
                    logger.warn("Invalid regex pattern: {}", pattern, e);
                    return false;
                }
                
            default:
                return false;
        }
    }

    private boolean matchesQueryParams(HttpServletRequest request, RequestMapping.RequestSpec spec) {
        // Check exact query param matches (backward compatibility)
        if (spec.getQueryParams() != null && !spec.getQueryParams().isEmpty()) {
            for (Map.Entry<String, String> entry : spec.getQueryParams().entrySet()) {
                String paramValue = request.getParameter(entry.getKey());
                if (paramValue == null || !paramValue.equals(entry.getValue())) {
                    return false;
                }
            }
        }

        // Check advanced query param patterns
        if (spec.getQueryParamPatterns() != null && !spec.getQueryParamPatterns().isEmpty()) {
            for (Map.Entry<String, RequestMapping.ParameterPattern> entry : spec.getQueryParamPatterns().entrySet()) {
                if (!matchesParameterPattern(request.getParameter(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchesHeaders(HttpServletRequest request, RequestMapping.RequestSpec spec) {
        // Check exact header matches (backward compatibility)
        if (spec.getHeaders() != null && !spec.getHeaders().isEmpty()) {
            for (Map.Entry<String, String> entry : spec.getHeaders().entrySet()) {
                String headerValue = request.getHeader(entry.getKey());
                if (headerValue == null || !headerValue.equals(entry.getValue())) {
                    return false;
                }
            }
        }

        // Check advanced header patterns
        if (spec.getHeaderPatterns() != null && !spec.getHeaderPatterns().isEmpty()) {
            for (Map.Entry<String, RequestMapping.ParameterPattern> entry : spec.getHeaderPatterns().entrySet()) {
                if (!matchesParameterPattern(request.getHeader(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchesParameterPattern(String value, RequestMapping.ParameterPattern pattern) {
        if (pattern == null) {
            return true;
        }

        switch (pattern.getMatchType()) {
            case EXISTS:
                return value != null;
                
            case EXACT:
                if (value == null) return false;
                String targetValue = pattern.isIgnoreCase() ? value.toLowerCase() : value;
                String targetPattern = pattern.isIgnoreCase() ? pattern.getPattern().toLowerCase() : pattern.getPattern();
                return targetValue.equals(targetPattern);
                
            case CONTAINS:
                if (value == null) return false;
                String containsValue = pattern.isIgnoreCase() ? value.toLowerCase() : value;
                String containsPattern = pattern.isIgnoreCase() ? pattern.getPattern().toLowerCase() : pattern.getPattern();
                return containsValue.contains(containsPattern);
                
            case REGEX:
                if (value == null) return false;
                try {
                    Pattern regexComp = pattern.isIgnoreCase() ? 
                        Pattern.compile(pattern.getPattern(), Pattern.CASE_INSENSITIVE) : 
                        Pattern.compile(pattern.getPattern());
                    return regexComp.matcher(value).matches();
                } catch (PatternSyntaxException e) {
                    logger.warn("Invalid regex pattern: {}", pattern.getPattern(), e);
                    return false;
                }
                
            default:
                return false;
        }
    }

    private boolean matchesBody(String requestBody, RequestMapping.RequestSpec spec) {
        if (spec.getBodyPatterns() == null || spec.getBodyPatterns().isEmpty()) {
            return true; // No body patterns specified
        }

        for (RequestMapping.BodyPattern bodyPattern : spec.getBodyPatterns()) {
            if (!matchesBodyPattern(requestBody, bodyPattern)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesBodyPattern(String requestBody, RequestMapping.BodyPattern pattern) {
        if (pattern == null || requestBody == null) {
            return pattern == null; // Both null = match, one null = no match
        }

        switch (pattern.getMatchType()) {
            case EXACT:
                String targetBody = pattern.isIgnoreCase() ? requestBody.toLowerCase() : requestBody;
                String targetPattern = pattern.isIgnoreCase() ? pattern.getExpected().toLowerCase() : pattern.getExpected();
                return targetBody.equals(targetPattern);
                
            case CONTAINS:
                String containsBody = pattern.isIgnoreCase() ? requestBody.toLowerCase() : requestBody;
                String containsPattern = pattern.isIgnoreCase() ? pattern.getExpected().toLowerCase() : pattern.getExpected();
                return containsBody.contains(containsPattern);
                
            case REGEX:
                try {
                    Pattern regexComp = pattern.isIgnoreCase() ? 
                        Pattern.compile(pattern.getExpected(), Pattern.CASE_INSENSITIVE) : 
                        Pattern.compile(pattern.getExpected());
                    return regexComp.matcher(requestBody).find();
                } catch (PatternSyntaxException e) {
                    logger.warn("Invalid regex pattern: {}", pattern.getExpected(), e);
                    return false;
                }
                
            case JSONPATH:
                return matchesJsonPath(requestBody, pattern);
                
            case XPATH:
                // TODO: Implement XPath matching if needed
                logger.warn("XPath matching not yet implemented");
                return false;
                
            default:
                return false;
        }
    }

    private boolean matchesJsonPath(String requestBody, RequestMapping.BodyPattern pattern) {
        try {
            Object value = JsonPath.read(requestBody, pattern.getExpr());
            
            if (pattern.getExpected() == null || pattern.getExpected().isEmpty()) {
                return value != null; // Just check existence
            }

            // Convert value to string for comparison
            String actualValue = value != null ? value.toString() : null;
            if (actualValue == null) {
                return false;
            }

            if (pattern.isIgnoreCase()) {
                return actualValue.toLowerCase().equals(pattern.getExpected().toLowerCase());
            } else {
                return actualValue.equals(pattern.getExpected());
            }

        } catch (PathNotFoundException e) {
            logger.debug("JSONPath not found: {}", pattern.getExpr());
            return false;
        } catch (Exception e) {
            logger.warn("Error evaluating JSONPath: {}", pattern.getExpr(), e);
            return false;
        }
    }
}