package com.simulator.model;

import java.util.Map;

public class GraphQLSpec {

    public enum OperationType {
        QUERY,
        MUTATION,
        SUBSCRIPTION
    }

    private OperationType operationType;
    private String operationName;
    private String schema;
    private String query;
    private Map<String, Object> variables;
    private QueryPattern queryPattern;

    public static class QueryPattern {
        public enum MatchType { EXACT, AST_MATCH, CONTAINS }

        private MatchType matchType;
        private String pattern;
        private boolean ignoreVariables;
        private boolean ignoreFragments;

        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }

        public boolean isIgnoreVariables() { return ignoreVariables; }
        public void setIgnoreVariables(boolean ignoreVariables) { this.ignoreVariables = ignoreVariables; }

        public boolean isIgnoreFragments() { return ignoreFragments; }
        public void setIgnoreFragments(boolean ignoreFragments) { this.ignoreFragments = ignoreFragments; }
    }

    public OperationType getOperationType() { return operationType; }
    public void setOperationType(OperationType operationType) { this.operationType = operationType; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public QueryPattern getQueryPattern() { return queryPattern; }
    public void setQueryPattern(QueryPattern queryPattern) { this.queryPattern = queryPattern; }
}