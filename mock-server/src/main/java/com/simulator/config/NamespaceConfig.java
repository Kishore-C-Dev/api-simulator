package com.simulator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NamespaceConfig {

    @Value("${simulator.namespace:default}")
    private String namespace;

    public String getNamespace() {
        return namespace;
    }
}
