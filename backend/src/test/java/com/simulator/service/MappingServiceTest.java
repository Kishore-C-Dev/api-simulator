package com.simulator.service;

import com.simulator.model.RequestMapping;
import com.simulator.repository.RequestMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MappingServiceTest {

    @Mock
    private RequestMappingRepository mappingRepository;

    @Mock
    private WireMockMappingService wireMockMappingService;

    private MappingService mappingService;

    @BeforeEach
    void setUp() {
        mappingService = new MappingService();
        ReflectionTestUtils.setField(mappingService, "mappingRepository", mappingRepository);
        ReflectionTestUtils.setField(mappingService, "wireMockMappingService", wireMockMappingService);
        ReflectionTestUtils.setField(mappingService, "activeDataset", "test");
    }

    @Test
    void getAllMappings_ShouldReturnMappingsFromActiveDataset() {
        List<RequestMapping> expectedMappings = Arrays.asList(
            createTestMapping("1"),
            createTestMapping("2")
        );
        when(mappingRepository.findByDatasetOrderByPriorityDesc("test")).thenReturn(expectedMappings);

        List<RequestMapping> result = mappingService.getAllMappings();

        assertEquals(expectedMappings, result);
        verify(mappingRepository).findByDatasetOrderByPriorityDesc("test");
    }

    @Test
    void getEnabledMappings_ShouldReturnOnlyEnabledMappings() {
        List<RequestMapping> expectedMappings = Arrays.asList(createTestMapping("1"));
        when(mappingRepository.findByDatasetAndEnabledOrderByPriorityDesc("test", true))
            .thenReturn(expectedMappings);

        List<RequestMapping> result = mappingService.getEnabledMappings();

        assertEquals(expectedMappings, result);
        verify(mappingRepository).findByDatasetAndEnabledOrderByPriorityDesc("test", true);
    }

    @Test
    void searchMappings_WithQuery_ShouldSearchByNameContaining() {
        RequestMapping mapping = createTestMapping("1");
        Page<RequestMapping> expectedPage = new PageImpl<>(Arrays.asList(mapping));
        when(mappingRepository.findByDatasetAndNameContainingIgnoreCase(eq("test"), eq("search"), any(Pageable.class)))
            .thenReturn(expectedPage);

        Page<RequestMapping> result = mappingService.searchMappings("search", Pageable.unpaged());

        assertEquals(expectedPage, result);
        verify(mappingRepository).findByDatasetAndNameContainingIgnoreCase(eq("test"), eq("search"), any(Pageable.class));
    }

    @Test
    void searchMappings_WithoutQuery_ShouldReturnAllFromDataset() {
        RequestMapping mapping = createTestMapping("1");
        Page<RequestMapping> expectedPage = new PageImpl<>(Arrays.asList(mapping));
        when(mappingRepository.findByDataset(eq("test"), any(Pageable.class)))
            .thenReturn(expectedPage);

        Page<RequestMapping> result = mappingService.searchMappings(null, Pageable.unpaged());

        assertEquals(expectedPage, result);
        verify(mappingRepository).findByDataset(eq("test"), any(Pageable.class));
    }

    @Test
    void getMapping_ShouldReturnMappingById() {
        RequestMapping mapping = createTestMapping("1");
        when(mappingRepository.findById("1")).thenReturn(Optional.of(mapping));

        Optional<RequestMapping> result = mappingService.getMapping("1");

        assertTrue(result.isPresent());
        assertEquals(mapping, result.get());
        verify(mappingRepository).findById("1");
    }

    @Test
    void saveMapping_NewMapping_ShouldSetCreatedAtAndDataset() {
        RequestMapping mapping = createTestMapping(null);
        RequestMapping savedMapping = createTestMapping("1");
        when(mappingRepository.save(any(RequestMapping.class))).thenReturn(savedMapping);

        RequestMapping result = mappingService.saveMapping(mapping);

        assertNotNull(mapping.getCreatedAt());
        assertNotNull(mapping.getUpdatedAt());
        assertEquals("test", mapping.getDataset());
        assertEquals(savedMapping, result);
        verify(mappingRepository).save(mapping);
        verify(wireMockMappingService).loadMappings(anyList());
    }

    @Test
    void saveMapping_ExistingMapping_ShouldUpdateUpdatedAt() {
        RequestMapping mapping = createTestMapping("1");
        RequestMapping savedMapping = createTestMapping("1");
        when(mappingRepository.save(any(RequestMapping.class))).thenReturn(savedMapping);

        RequestMapping result = mappingService.saveMapping(mapping);

        assertNotNull(mapping.getUpdatedAt());
        assertEquals("test", mapping.getDataset());
        assertEquals(savedMapping, result);
        verify(mappingRepository).save(mapping);
        verify(wireMockMappingService).loadMappings(anyList());
    }

    @Test
    void deleteMapping_ShouldDeleteAndReloadWireMock() {
        mappingService.deleteMapping("1");

        verify(mappingRepository).deleteById("1");
        verify(wireMockMappingService).loadMappings(anyList());
    }

    @Test
    void reloadWireMockMappings_ShouldLoadEnabledMappings() {
        List<RequestMapping> enabledMappings = Arrays.asList(createTestMapping("1"));
        when(mappingRepository.findByDatasetAndEnabledOrderByPriorityDesc("test", true))
            .thenReturn(enabledMappings);

        mappingService.reloadWireMockMappings();

        verify(wireMockMappingService).loadMappings(enabledMappings);
    }

    @Test
    void importMappings_ShouldSetDatasetAndSaveAll() {
        List<RequestMapping> mappings = Arrays.asList(
            createTestMapping(null),
            createTestMapping(null)
        );

        mappingService.importMappings(mappings);

        for (RequestMapping mapping : mappings) {
            assertEquals("test", mapping.getDataset());
            assertNotNull(mapping.getCreatedAt());
            assertNotNull(mapping.getUpdatedAt());
        }
        verify(mappingRepository).saveAll(mappings);
        verify(wireMockMappingService).loadMappings(anyList());
    }

    private RequestMapping createTestMapping(String id) {
        RequestMapping mapping = new RequestMapping();
        mapping.setId(id);
        mapping.setName("Test Mapping");
        mapping.setPriority(5);
        mapping.setEnabled(true);
        
        RequestMapping.RequestSpec request = new RequestMapping.RequestSpec();
        request.setMethod("GET");
        request.setPath("/test");
        mapping.setRequest(request);
        
        RequestMapping.ResponseSpec response = new RequestMapping.ResponseSpec();
        response.setStatus(200);
        response.setBody("{\"test\": true}");
        mapping.setResponse(response);
        
        return mapping;
    }
}