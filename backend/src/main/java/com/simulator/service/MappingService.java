package com.simulator.service;

import com.simulator.model.RequestMapping;
import com.simulator.repository.RequestMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MappingService {

    private static final Logger logger = LoggerFactory.getLogger(MappingService.class);

    @Value("${simulator.active-dataset}")
    private String activeDataset;

    @Autowired
    private RequestMappingRepository mappingRepository;

    @Autowired
    private WireMockMappingService wireMockMappingService;

    // New namespace-based methods
    public List<RequestMapping> getAllMappings(String namespace) {
        return mappingRepository.findByNamespaceOrderByPriorityDesc(namespace);
    }

    // Default method that uses activeDataset as namespace
    public List<RequestMapping> getAllMappings() {
        return getAllMappings(activeDataset);
    }

    public List<RequestMapping> getEnabledMappings(String namespace) {
        return mappingRepository.findByNamespaceAndEnabledOrderByPriorityDesc(namespace, true);
    }

    public Page<RequestMapping> searchMappings(String namespace, String query, Pageable pageable) {
        if (query != null && !query.trim().isEmpty()) {
            // Use broader search criteria (name, path, method) with sorting support
            return mappingRepository.findByNamespaceAndSearchCriteria(namespace, query.trim(), pageable);
        }
        // For no search, use the generic findByNamespace which respects Pageable sorting
        return mappingRepository.findByNamespace(namespace, pageable);
    }

    // Default method that uses activeDataset as namespace
    public Page<RequestMapping> searchMappings(String query, Pageable pageable) {
        return searchMappings(activeDataset, query, pageable);
    }


    public Optional<RequestMapping> getMapping(String id) {
        return mappingRepository.findById(id);
    }

    public RequestMapping saveMapping(RequestMapping mapping, String namespace) {
        if (mapping.getId() == null) {
            mapping.setCreatedAt(Instant.now());
        }
        mapping.setUpdatedAt(Instant.now());
        mapping.setNamespace(namespace);
        
        RequestMapping saved = mappingRepository.save(mapping);
        reloadWireMockMappings();
        return saved;
    }

    // Default method that uses activeDataset as namespace
    public RequestMapping saveMapping(RequestMapping mapping) {
        return saveMapping(mapping, activeDataset);
    }


    public void deleteMapping(String id) {
        // Soft delete: set deleted flag instead of actual deletion
        Optional<RequestMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isPresent()) {
            RequestMapping mapping = mappingOpt.get();
            mapping.setDeleted(true);
            mapping.setUpdatedAt(Instant.now());
            mappingRepository.save(mapping);
            reloadWireMockMappings();
        }
    }

    public void reloadWireMockMappings() {
        try {
            List<RequestMapping> enabledMappings = getEnabledMappings(activeDataset);
            wireMockMappingService.loadMappings(enabledMappings);
            logger.info("Reloaded {} WireMock mappings for namespace: {}", enabledMappings.size(), activeDataset);
        } catch (Exception e) {
            logger.error("Failed to reload WireMock mappings: {}", e.getMessage(), e);
        }
    }

    public void importMappings(List<RequestMapping> mappings) {
        for (RequestMapping mapping : mappings) {
            mapping.setNamespace(activeDataset); // Use activeDataset as namespace for legacy support
            mapping.setCreatedAt(Instant.now());
            mapping.setUpdatedAt(Instant.now());
        }
        mappingRepository.saveAll(mappings);
        reloadWireMockMappings();
    }

    public void clearDataset(String dataset) {
        mappingRepository.deleteByNamespace(dataset); // Use namespace method
        if (dataset.equals(activeDataset)) {
            reloadWireMockMappings();
        }
    }
}