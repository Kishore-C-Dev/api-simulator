package com.simulator.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.simulator.wiremock.ConditionalResponseTransformer;
import com.simulator.wiremock.GraphQLResponseTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WireMockConfig {

    @Value("${simulator.wiremock.port:9999}")
    private int wireMockPort;

    @Value("${simulator.wiremock.admin-port:9998}")
    private int adminPort;

    @Autowired
    private ConditionalResponseTransformer conditionalResponseTransformer;

    @Autowired
    private GraphQLResponseTransformer graphQLResponseTransformer;

    @Bean
    public WireMockServer wireMockServer() {
        WireMockServer server = new WireMockServer(
            WireMockConfiguration.options()
                .port(wireMockPort)
                .enableBrowserProxying(false)
                .globalTemplating(true)
                .extensions(conditionalResponseTransformer, graphQLResponseTransformer)
        );

        server.start();
        return server;
    }
}