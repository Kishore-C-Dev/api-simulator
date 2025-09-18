package com.simulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "mappings")
public class RequestMapping {

    @Id
    private String id;
    private String name;
    private String dataset;
    private Integer priority;
    private RequestSpec request;
    private ResponseSpec response;
    private DelaySpec delays;
    private Boolean enabled;
    private List<String> tags;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    @JsonProperty("updatedAt")
    private Instant updatedAt;

    public static class RequestSpec {
        private String method;
        private String path;
        private PathPattern pathPattern;
        private Map<String, String> queryParams;
        private Map<String, ParameterPattern> queryParamPatterns;
        private Map<String, String> headers;
        private Map<String, ParameterPattern> headerPatterns;
        private List<BodyPattern> bodyPatterns;

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public PathPattern getPathPattern() { return pathPattern; }
        public void setPathPattern(PathPattern pathPattern) { this.pathPattern = pathPattern; }
        
        public Map<String, String> getQueryParams() { return queryParams; }
        public void setQueryParams(Map<String, String> queryParams) { this.queryParams = queryParams; }
        
        public Map<String, ParameterPattern> getQueryParamPatterns() { return queryParamPatterns; }
        public void setQueryParamPatterns(Map<String, ParameterPattern> queryParamPatterns) { this.queryParamPatterns = queryParamPatterns; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public Map<String, ParameterPattern> getHeaderPatterns() { return headerPatterns; }
        public void setHeaderPatterns(Map<String, ParameterPattern> headerPatterns) { this.headerPatterns = headerPatterns; }
        
        public List<BodyPattern> getBodyPatterns() { return bodyPatterns; }
        public void setBodyPatterns(List<BodyPattern> bodyPatterns) { this.bodyPatterns = bodyPatterns; }
    }

    public static class PathPattern {
        public enum MatchType { EXACT, REGEX, WILDCARD }
        
        private MatchType matchType;
        private String pattern;
        private boolean ignoreCase;

        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }
        
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public boolean isIgnoreCase() { return ignoreCase; }
        public void setIgnoreCase(boolean ignoreCase) { this.ignoreCase = ignoreCase; }
    }

    public static class ParameterPattern {
        public enum MatchType { EXACT, REGEX, CONTAINS, EXISTS }
        
        private MatchType matchType;
        private String pattern;
        private boolean ignoreCase;

        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }
        
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public boolean isIgnoreCase() { return ignoreCase; }
        public void setIgnoreCase(boolean ignoreCase) { this.ignoreCase = ignoreCase; }
    }

    public static class BodyPattern {
        public enum MatchType { EXACT, REGEX, JSONPATH, XPATH, CONTAINS }
        
        private MatchType matchType;
        private String expr;
        private String expected;
        private boolean ignoreCase;

        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }
        
        public String getExpr() { return expr; }
        public void setExpr(String expr) { this.expr = expr; }
        
        public String getExpected() { return expected; }
        public void setExpected(String expected) { this.expected = expected; }
        
        public boolean isIgnoreCase() { return ignoreCase; }
        public void setIgnoreCase(boolean ignoreCase) { this.ignoreCase = ignoreCase; }
        
        // Backward compatibility
        public String getMatcher() { 
            return matchType != null ? matchType.name().toLowerCase() : null; 
        }
        public void setMatcher(String matcher) { 
            if (matcher != null) {
                try {
                    this.matchType = MatchType.valueOf(matcher.toUpperCase());
                } catch (IllegalArgumentException e) {
                    this.matchType = MatchType.EXACT;
                }
            }
        }
    }

    public static class ResponseSpec {
        private Integer status;
        private Map<String, String> headers;
        private String body;
        private Boolean templatingEnabled;
        private ConditionalResponses conditionalResponses;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        
        public Boolean getTemplatingEnabled() { return templatingEnabled; }
        public void setTemplatingEnabled(Boolean templatingEnabled) { this.templatingEnabled = templatingEnabled; }
        
        public ConditionalResponses getConditionalResponses() { return conditionalResponses; }
        public void setConditionalResponses(ConditionalResponses conditionalResponses) { this.conditionalResponses = conditionalResponses; }
    }

    public static class DelaySpec {
        private String mode;
        private Integer fixedMs;
        private Integer variableMinMs;
        private Integer variableMaxMs;
        private Integer errorRatePercent;
        private ErrorResponse errorResponse;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        
        public Integer getFixedMs() { return fixedMs; }
        public void setFixedMs(Integer fixedMs) { this.fixedMs = fixedMs; }
        
        public Integer getVariableMinMs() { return variableMinMs; }
        public void setVariableMinMs(Integer variableMinMs) { this.variableMinMs = variableMinMs; }
        
        public Integer getVariableMaxMs() { return variableMaxMs; }
        public void setVariableMaxMs(Integer variableMaxMs) { this.variableMaxMs = variableMaxMs; }
        
        public Integer getErrorRatePercent() { return errorRatePercent; }
        public void setErrorRatePercent(Integer errorRatePercent) { this.errorRatePercent = errorRatePercent; }
        
        public ErrorResponse getErrorResponse() { return errorResponse; }
        public void setErrorResponse(ErrorResponse errorResponse) { this.errorResponse = errorResponse; }
    }

    public static class ErrorResponse {
        private Integer status;
        private String body;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }
    
    public static class ConditionalResponses {
        private Boolean enabled;
        private String requestIdHeader;
        private List<RequestIdMapping> requestIdMappings;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        
        public String getRequestIdHeader() { return requestIdHeader; }
        public void setRequestIdHeader(String requestIdHeader) { this.requestIdHeader = requestIdHeader; }
        
        public List<RequestIdMapping> getRequestIdMappings() { return requestIdMappings; }
        public void setRequestIdMappings(List<RequestIdMapping> requestIdMappings) { this.requestIdMappings = requestIdMappings; }
    }
    
    public static class RequestIdMapping {
        private String requestId;
        private Integer status;
        private String body;
        private Map<String, String> headers;

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public RequestSpec getRequest() { return request; }
    public void setRequest(RequestSpec request) { this.request = request; }
    
    public ResponseSpec getResponse() { return response; }
    public void setResponse(ResponseSpec response) { this.response = response; }
    
    public DelaySpec getDelays() { return delays; }
    public void setDelays(DelaySpec delays) { this.delays = delays; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}