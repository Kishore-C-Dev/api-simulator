package com.simulator.wiremock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.simulator.model.RequestMapping;
import com.simulator.service.ConditionalResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ConditionalResponseTransformer extends ResponseTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ConditionalResponseTransformer.class);
    private static final String NAME = "conditional-response";
    
    @Autowired
    private ConditionalResponseService conditionalResponseService;
    
    private final ObjectMapper objectMapper;
    
    public ConditionalResponseTransformer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Register JavaTimeModule for Instant support
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
        try {
            // Extract mapping configuration from parameters
            String mappingJson = parameters.getString("mapping");
            if (mappingJson == null) {
                logger.debug("No mapping configuration found in parameters, returning original response");
                return response;
            }

            RequestMapping mapping = objectMapper.readValue(mappingJson, RequestMapping.class);
            
            // Convert WireMock request headers to map
            Map<String, String> requestHeaders = new HashMap<>();
            request.getAllHeaderKeys().forEach(headerName -> 
                requestHeaders.put(headerName, request.getHeader(headerName))
            );

            // Determine response using conditional logic
            ConditionalResponseService.ConditionalResponse conditionalResponse = 
                conditionalResponseService.determineResponse(mapping, requestHeaders);

            // If it's a conditional response, modify the response accordingly
            if (conditionalResponse.isConditional()) {
                logger.info("Applying conditional response transformation for request with headers: {}", requestHeaders);
                
                Response.Builder responseBuilder = Response.Builder.like(response)
                    .but()
                    .status(conditionalResponse.getStatus())
                    .body(conditionalResponse.getBody());

                return responseBuilder.build();
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error in ConditionalResponseTransformer: {}", e.getMessage(), e);
            return response;
        }
    }

    @Override
    public boolean applyGlobally() {
        return false; // Only apply to specific mappings that request it
    }
}