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
    
    @Query("{ 'namespace': ?0, 'enabled': ?1, 'deleted': { $ne: true } }")
    List<RequestMapping> findByNamespaceAndEnabledOrderByPriorityDesc(String namespace, Boolean enabled);
    
    @Query("{ 'namespace': ?0, 'deleted': { $ne: true } }")
    List<RequestMapping> findByNamespaceOrderByPriorityDesc(String namespace);
    
    // These methods will respect the Pageable sorting parameters
    @Query("{ 'namespace': ?0, 'name': { $regex: ?1, $options: 'i' }, 'deleted': { $ne: true } }")
    Page<RequestMapping> findByNamespaceAndNameContainingIgnoreCase(String namespace, String name, Pageable pageable);
    
    @Query("{ 'namespace': ?0, 'deleted': { $ne: true } }")
    Page<RequestMapping> findByNamespace(String namespace, Pageable pageable);
    
    // Custom query for search with broader criteria
    @Query("{ 'namespace': ?0, $or: [ {'name': {$regex: ?1, $options: 'i'}}, {'request.path': {$regex: ?1, $options: 'i'}}, {'request.method': {$regex: ?1, $options: 'i'}} ], 'deleted': { $ne: true } }")
    Page<RequestMapping> findByNamespaceAndSearchCriteria(String namespace, String searchTerm, Pageable pageable);
    
    @Query("{ 'namespace': ?0, 'tags': { $in: ?1 }, 'deleted': { $ne: true } }")
    List<RequestMapping> findByNamespaceAndTagsIn(String namespace, List<String> tags);
    
    void deleteByNamespace(String namespace);
}