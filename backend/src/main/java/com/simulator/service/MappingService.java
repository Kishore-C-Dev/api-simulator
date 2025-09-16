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

    public List<RequestMapping> getAllMappings() {
        return mappingRepository.findByDatasetOrderByPriorityDesc(activeDataset);
    }

    public List<RequestMapping> getEnabledMappings() {
        return mappingRepository.findByDatasetAndEnabledOrderByPriorityDesc(activeDataset, true);
    }

    public Page<RequestMapping> searchMappings(String query, Pageable pageable) {
        if (query != null && !query.trim().isEmpty()) {
            // Use broader search criteria (name, path, method) with sorting support
            return mappingRepository.findByDatasetAndSearchCriteria(activeDataset, query.trim(), pageable);
        }
        // For no search, use the generic findByDataset which respects Pageable sorting
        return mappingRepository.findByDataset(activeDataset, pageable);
    }

    public Optional<RequestMapping> getMapping(String id) {
        return mappingRepository.findById(id);
    }

    public RequestMapping saveMapping(RequestMapping mapping) {
        if (mapping.getId() == null) {
            mapping.setCreatedAt(Instant.now());
        }
        mapping.setUpdatedAt(Instant.now());
        mapping.setDataset(activeDataset);
        
        RequestMapping saved = mappingRepository.save(mapping);
        reloadWireMockMappings();
        return saved;
    }

    public void deleteMapping(String id) {
        mappingRepository.deleteById(id);
        reloadWireMockMappings();
    }

    public void reloadWireMockMappings() {
        try {
            List<RequestMapping> enabledMappings = getEnabledMappings();
            wireMockMappingService.loadMappings(enabledMappings);
            logger.info("Reloaded {} WireMock mappings for dataset: {}", enabledMappings.size(), activeDataset);
        } catch (Exception e) {
            logger.error("Failed to reload WireMock mappings: {}", e.getMessage(), e);
        }
    }

    public void importMappings(List<RequestMapping> mappings) {
        for (RequestMapping mapping : mappings) {
            mapping.setDataset(activeDataset);
            mapping.setCreatedAt(Instant.now());
            mapping.setUpdatedAt(Instant.now());
        }
        mappingRepository.saveAll(mappings);
        reloadWireMockMappings();
    }

    public void clearDataset(String dataset) {
        mappingRepository.deleteByDataset(dataset);
        if (dataset.equals(activeDataset)) {
            reloadWireMockMappings();
        }
    }
}