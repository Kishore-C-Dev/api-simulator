package com.simulator.repository;

import com.simulator.model.Namespace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NamespaceRepository extends MongoRepository<Namespace, String> {
    
    /**
     * Find namespace by name
     */
    Optional<Namespace> findByName(String name);
    
    /**
     * Find all active and non-deleted namespaces
     */
    List<Namespace> findByActiveTrueAndDeletedFalse();
    
    /**
     * Find namespaces where user is a member
     */
    @Query("{ 'members': ?0, 'active': true, 'deleted': { $ne: true } }")
    List<Namespace> findByMember(String userId);
    
    /**
     * Find namespaces by owner
     */
    List<Namespace> findByOwnerAndActiveTrueAndDeletedFalse(String owner);
    
    /**
     * Find namespaces by name pattern (case insensitive)
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'active': true }")
    List<Namespace> findByNameContaining(String namePattern);
    
    /**
     * Find namespaces by display name pattern (case insensitive)
     */
    @Query("{ 'displayName': { $regex: ?0, $options: 'i' }, 'active': true }")
    List<Namespace> findByDisplayNameContaining(String displayNamePattern);
    
    /**
     * Check if namespace name exists
     */
    boolean existsByName(String name);
    
    /**
     * Find multiple namespaces by names
     */
    List<Namespace> findByNameInAndActiveTrue(List<String> names);
    
    /**
     * Search namespaces by name or display name with pagination
     */
    @Query("{ $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'displayName': { $regex: ?1, $options: 'i' } } ], 'deleted': { $ne: true } }")
    Page<Namespace> findByNameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
        String name, String displayName, Pageable pageable);
    
    /**
     * Find all non-deleted namespaces with pagination
     */
    @Query("{ 'deleted': { $ne: true } }")
    Page<Namespace> findAllNonDeleted(Pageable pageable);
}