package com.simulator.service;

import com.simulator.config.NamespaceConfig;
import com.simulator.model.RequestMapping;
import com.simulator.repository.RequestMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockServerInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(MockServerInitializer.class);

    @Autowired
    private NamespaceConfig namespaceConfig;

    @Autowired
    private RequestMappingRepository mappingRepository;

    @Autowired
    private WireMockMappingService wireMockMappingService;

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Initializing mock server for namespace: {}", namespaceConfig.getNamespace());
        loadMappingsForNamespace();
    }

    public int loadMappingsForNamespace() {
        String namespace = namespaceConfig.getNamespace();
        List<RequestMapping> enabledMappings =
            mappingRepository.findByNamespaceAndEnabledOrderByPriorityDesc(namespace, true);
        wireMockMappingService.loadMappings(enabledMappings);
        logger.info("Loaded {} enabled mappings for namespace '{}'", enabledMappings.size(), namespace);
        return enabledMappings.size();
    }

    public int getEnabledMappingCount() {
        return mappingRepository.findByNamespaceAndEnabledOrderByPriorityDesc(
            namespaceConfig.getNamespace(), true).size();
    }

    public String getNamespace() {
        return namespaceConfig.getNamespace();
    }
}
