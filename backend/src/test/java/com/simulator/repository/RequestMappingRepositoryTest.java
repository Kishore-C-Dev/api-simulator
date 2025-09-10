package com.simulator.repository;

import com.simulator.model.RequestMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class RequestMappingRepositoryTest {

    @Autowired
    private RequestMappingRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByDatasetAndEnabledOrderByPriorityDesc_ShouldReturnEnabledMappingsInPriorityOrder() {
        RequestMapping mapping1 = createTestMapping("1", "dataset1", true, 3);
        RequestMapping mapping2 = createTestMapping("2", "dataset1", true, 5);
        RequestMapping mapping3 = createTestMapping("3", "dataset1", false, 7);
        RequestMapping mapping4 = createTestMapping("4", "dataset2", true, 4);

        repository.saveAll(Arrays.asList(mapping1, mapping2, mapping3, mapping4));

        List<RequestMapping> result = repository.findByDatasetAndEnabledOrderByPriorityDesc("dataset1", true);

        assertEquals(2, result.size());
        assertEquals("2", result.get(0).getId()); // Priority 5
        assertEquals("1", result.get(1).getId()); // Priority 3
    }

    @Test
    void findByDatasetOrderByPriorityDesc_ShouldReturnAllMappingsInPriorityOrder() {
        RequestMapping mapping1 = createTestMapping("1", "dataset1", true, 3);
        RequestMapping mapping2 = createTestMapping("2", "dataset1", false, 5);
        RequestMapping mapping3 = createTestMapping("3", "dataset2", true, 7);

        repository.saveAll(Arrays.asList(mapping1, mapping2, mapping3));

        List<RequestMapping> result = repository.findByDatasetOrderByPriorityDesc("dataset1");

        assertEquals(2, result.size());
        assertEquals("2", result.get(0).getId()); // Priority 5
        assertEquals("1", result.get(1).getId()); // Priority 3
    }

    @Test
    void findByDatasetAndNameContainingIgnoreCase_ShouldReturnMatchingMappings() {
        RequestMapping mapping1 = createTestMapping("1", "dataset1", true, 5);
        mapping1.setName("Get Customer");
        RequestMapping mapping2 = createTestMapping("2", "dataset1", true, 5);
        mapping2.setName("Create Order");
        RequestMapping mapping3 = createTestMapping("3", "dataset1", true, 5);
        mapping3.setName("Get Orders");

        repository.saveAll(Arrays.asList(mapping1, mapping2, mapping3));

        Page<RequestMapping> result = repository.findByDatasetAndNameContainingIgnoreCase(
                "dataset1", "order", PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().anyMatch(m -> m.getName().equals("Create Order")));
        assertTrue(result.getContent().stream().anyMatch(m -> m.getName().equals("Get Orders")));
    }

    @Test
    void findByDataset_ShouldReturnPagedResults() {
        for (int i = 0; i < 15; i++) {
            RequestMapping mapping = createTestMapping(String.valueOf(i), "dataset1", true, 5);
            repository.save(mapping);
        }

        Page<RequestMapping> page1 = repository.findByDataset("dataset1", PageRequest.of(0, 10));
        Page<RequestMapping> page2 = repository.findByDataset("dataset1", PageRequest.of(1, 10));

        assertEquals(15, page1.getTotalElements());
        assertEquals(2, page1.getTotalPages());
        assertEquals(10, page1.getContent().size());
        assertEquals(5, page2.getContent().size());
    }

    @Test
    void findByDatasetAndTagsIn_ShouldReturnMappingsWithMatchingTags() {
        RequestMapping mapping1 = createTestMapping("1", "dataset1", true, 5);
        mapping1.setTags(Arrays.asList("api", "customer"));
        RequestMapping mapping2 = createTestMapping("2", "dataset1", true, 5);
        mapping2.setTags(Arrays.asList("order", "payment"));
        RequestMapping mapping3 = createTestMapping("3", "dataset1", true, 5);
        mapping3.setTags(Arrays.asList("customer", "admin"));

        repository.saveAll(Arrays.asList(mapping1, mapping2, mapping3));

        List<RequestMapping> result = repository.findByDatasetAndTagsIn("dataset1", Arrays.asList("customer"));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(m -> m.getId().equals("1")));
        assertTrue(result.stream().anyMatch(m -> m.getId().equals("3")));
    }

    @Test
    void deleteByDataset_ShouldRemoveAllMappingsFromDataset() {
        RequestMapping mapping1 = createTestMapping("1", "dataset1", true, 5);
        RequestMapping mapping2 = createTestMapping("2", "dataset1", true, 5);
        RequestMapping mapping3 = createTestMapping("3", "dataset2", true, 5);

        repository.saveAll(Arrays.asList(mapping1, mapping2, mapping3));

        repository.deleteByDataset("dataset1");

        List<RequestMapping> remaining = repository.findAll();
        assertEquals(1, remaining.size());
        assertEquals("3", remaining.get(0).getId());
    }

    private RequestMapping createTestMapping(String id, String dataset, boolean enabled, int priority) {
        RequestMapping mapping = new RequestMapping();
        mapping.setId(id);
        mapping.setName("Test Mapping " + id);
        mapping.setDataset(dataset);
        mapping.setEnabled(enabled);
        mapping.setPriority(priority);
        mapping.setCreatedAt(Instant.now());
        mapping.setUpdatedAt(Instant.now());

        RequestMapping.RequestSpec request = new RequestMapping.RequestSpec();
        request.setMethod("GET");
        request.setPath("/test/" + id);
        request.setHeaders(new HashMap<>());
        request.setQueryParams(new HashMap<>());
        mapping.setRequest(request);

        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(200);
        response.setBody("{\"id\": \"" + id + "\"}");
        response.setHeaders(new HashMap<>());
        mapping.setResponse(response);

        return mapping;
    }
}