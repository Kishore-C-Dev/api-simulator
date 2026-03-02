package com.simulator.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads .env file from project root and makes variables available to Spring
 */
public class DotEnvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(DotEnvConfig.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            // Try to load .env file from parent directory (project root)
            Dotenv dotenv = Dotenv.configure()
                .directory("../")  // Look in parent directory
                .ignoreIfMissing()
                .load();

            // Create a map of environment variables
            Map<String, Object> envMap = new HashMap<>();
            dotenv.entries().forEach(entry -> {
                envMap.put(entry.getKey(), entry.getValue());
                logger.debug("Loaded from .env: {} = {}", entry.getKey(),
                    entry.getKey().contains("KEY") || entry.getKey().contains("PASSWORD") ? "***" : entry.getValue());
            });

            // Add to Spring environment if we found any variables
            if (!envMap.isEmpty()) {
                ConfigurableEnvironment environment = applicationContext.getEnvironment();
                environment.getPropertySources().addFirst(new MapPropertySource("dotenv", envMap));
                logger.info("Loaded {} properties from .env file", envMap.size());
            }

        } catch (Exception e) {
            logger.warn("Could not load .env file: {}", e.getMessage());
        }
    }
}
