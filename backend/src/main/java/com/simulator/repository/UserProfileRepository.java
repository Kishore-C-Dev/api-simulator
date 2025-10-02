package com.simulator.repository;

import com.simulator.model.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    
    /**
     * Find user by userId (username)
     */
    Optional<UserProfile> findByUserId(String userId);
    
    /**
     * Find user by email
     */
    Optional<UserProfile> findByEmail(String email);
    
    /**
     * Find all active and non-deleted users
     */
    List<UserProfile> findByActiveTrueAndDeletedFalse();
    
    /**
     * Find users by namespace
     */
    @Query("{ 'namespaces': ?0, 'active': true, 'deleted': { $ne: true } }")
    List<UserProfile> findByNamespace(String namespace);
    
    /**
     * Find users with multiple namespaces
     */
    @Query("{ 'namespaces': { $in: ?0 }, 'active': true, 'deleted': { $ne: true } }")
    List<UserProfile> findByNamespaceIn(List<String> namespaces);
    
    /**
     * Check if userId exists
     */
    boolean existsByUserId(String userId);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Find users by first name or last name (case insensitive)
     */
    @Query("{ $or: [ { 'firstName': { $regex: ?0, $options: 'i' } }, { 'lastName': { $regex: ?0, $options: 'i' } } ], 'active': true, 'deleted': { $ne: true } }")
    List<UserProfile> findByNameContaining(String name);
    
    /**
     * Search users by userId, firstName, or lastName with pagination
     */
    @Query("{ $or: [ { 'userId': { $regex: ?0, $options: 'i' } }, { 'firstName': { $regex: ?1, $options: 'i' } }, { 'lastName': { $regex: ?2, $options: 'i' } } ], 'deleted': { $ne: true } }")
    Page<UserProfile> findByUserIdContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
        String userId, String firstName, String lastName, Pageable pageable);
    
    /**
     * Find all non-deleted users with pagination
     */
    @Query("{ 'deleted': { $ne: true } }")
    Page<UserProfile> findAllNonDeleted(Pageable pageable);
}