package com.simulator.wiremock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.simulator.model.RequestMapping;
import com.simulator.service.DelayService;
import com.simulator.service.GraphQLParserService;
import com.simulator.service.GraphQLResponseGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GraphQLResponseTransformer extends ResponseDefinitionTransformer {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLResponseTransformer.class);
    private final ObjectMapper objectMapper;
    private final GraphQLParserService graphQLParserService;
    private final GraphQLResponseGenerator graphQLResponseGenerator;
    private final DelayService delayService;

    @Autowired
    public GraphQLResponseTransformer(ObjectMapper objectMapper,
                                    GraphQLParserService graphQLParserService,
                                    GraphQLResponseGenerator graphQLResponseGenerator,
                                    DelayService delayService) {
        this.objectMapper = objectMapper;
        this.graphQLParserService = graphQLParserService;
        this.graphQLResponseGenerator = graphQLResponseGenerator;
        this.delayService = delayService;
    }

    @Override
    public String getName() {
        return "graphql-response";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition,
                                      FileSource files, Parameters parameters) {
        try {
            // Extract the mapping from parameters
            String mappingJson = parameters.getString("mapping");
            if (mappingJson == null) {
                logger.warn("No mapping parameter found for GraphQL transformer");
                return responseDefinition;
            }

            RequestMapping mapping = objectMapper.readValue(mappingJson, RequestMapping.class);

            // Parse the GraphQL request
            String requestBody = request.getBodyAsString();
            GraphQLParserService.GraphQLRequest graphQLRequest =
                graphQLParserService.parseGraphQLRequest(requestBody);

            // Generate the GraphQL response
            String generatedResponse = graphQLResponseGenerator.generateGraphQLResponse(mapping, graphQLRequest);

            // Determine HTTP status code - check if error was triggered
            int httpStatus = 200; // Default GraphQL status
            if (mapping.getDelays() != null && delayService.shouldTriggerError(mapping.getDelays())
                && mapping.getDelays().getErrorResponse() != null
                && mapping.getDelays().getErrorResponse().getStatus() != null) {
                httpStatus = mapping.getDelays().getErrorResponse().getStatus();
            }

            // Return the new response definition with the generated GraphQL response
            return ResponseDefinitionBuilder.like(responseDefinition)
                .withStatus(httpStatus)
                .withBody(generatedResponse)
                .withHeader("Content-Type", "application/json")
                .build();

        } catch (Exception e) {
            logger.error("Error in GraphQL response transformer: {}", e.getMessage(), e);

            // Return error response in GraphQL format
            String errorResponse = "{\n" +
                "  \"data\": null,\n" +
                "  \"errors\": [{\n" +
                "    \"message\": \"Internal server error: " + e.getMessage() + "\",\n" +
                "    \"extensions\": {\n" +
                "      \"code\": \"INTERNAL_ERROR\"\n" +
                "    }\n" +
                "  }]\n" +
                "}";

            return ResponseDefinitionBuilder.like(responseDefinition)
                .withBody(errorResponse)
                .withHeader("Content-Type", "application/json")
                .build();
        }
    }
}