package com.simulator.ai.config;

import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "simulator.ai.enabled", havingValue = "true")
public class AIConfig {

    @Value("${simulator.ai.api-key}")
    private String apiKey;

    @Bean
    public OpenAiService openAiService() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured. Set OPENAI_API_KEY environment variable.");
        }
        return new OpenAiService(apiKey, Duration.ofSeconds(60));
    }
}
