package com.simulator.repository;

import com.simulator.model.RequestMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestMappingRepository extends MongoRepository<RequestMapping, String> {
    
    List<RequestMapping> findByDatasetAndEnabledOrderByPriorityDesc(String dataset, Boolean enabled);
    
    List<RequestMapping> findByDatasetOrderByPriorityDesc(String dataset);
    
    Page<RequestMapping> findByDatasetAndNameContainingIgnoreCase(String dataset, String name, Pageable pageable);
    
    Page<RequestMapping> findByDataset(String dataset, Pageable pageable);
    
    @Query("{ 'dataset': ?0, 'tags': { $in: ?1 } }")
    List<RequestMapping> findByDatasetAndTagsIn(String dataset, List<String> tags);
    
    void deleteByDataset(String dataset);
}