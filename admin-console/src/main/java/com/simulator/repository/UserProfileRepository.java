package com.simulator.repository;

import com.simulator.model.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

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
    @Query(value = "SELECT * FROM api_user_profiles WHERE active = true AND deleted = false AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(namespaces) ns WHERE ns = :namespace)", nativeQuery = true)
    List<UserProfile> findByNamespace(@Param("namespace") String namespace);

    /**
     * Find users with multiple namespaces
     */
    @Query(value = "SELECT * FROM api_user_profiles WHERE active = true AND deleted = false AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(namespaces) ns WHERE ns = ANY(CAST(:namespaces AS text[])))", nativeQuery = true)
    List<UserProfile> findByNamespaceIn(@Param("namespaces") String[] namespaces);

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
    @Query("SELECT u FROM UserProfile u WHERE (LOWER(u.firstName) LIKE LOWER(CONCAT('%',:name,'%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%',:name,'%'))) AND u.active = true AND u.deleted = false")
    List<UserProfile> findByNameContaining(@Param("name") String name);

    /**
     * Search users by userId, firstName, or lastName with pagination
     */
    @Query("SELECT u FROM UserProfile u WHERE (LOWER(u.userId) LIKE LOWER(CONCAT('%',:userId,'%')) OR LOWER(u.firstName) LIKE LOWER(CONCAT('%',:firstName,'%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%',:lastName,'%'))) AND u.deleted = false")
    Page<UserProfile> findByUserIdContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            @Param("userId") String userId,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            Pageable pageable);

    /**
     * Find all non-deleted users with pagination
     */
    @Query("SELECT u FROM UserProfile u WHERE (u.deleted = false OR u.deleted IS NULL)")
    Page<UserProfile> findAllNonDeleted(Pageable pageable);
}
