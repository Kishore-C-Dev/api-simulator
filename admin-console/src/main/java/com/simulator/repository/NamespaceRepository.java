package com.simulator.repository;

import com.simulator.model.Namespace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NamespaceRepository extends JpaRepository<Namespace, String> {

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
    @Query(value = "SELECT * FROM namespaces WHERE active = true AND deleted = false AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(members) m WHERE m = :userId)", nativeQuery = true)
    List<Namespace> findByMember(@Param("userId") String userId);

    /**
     * Find namespaces by owner
     */
    List<Namespace> findByOwnerAndActiveTrueAndDeletedFalse(String owner);

    /**
     * Find namespaces by name pattern (case insensitive)
     */
    @Query(value = "SELECT * FROM namespaces WHERE active = true AND name ILIKE '%' || :namePattern || '%'", nativeQuery = true)
    List<Namespace> findByNameContaining(@Param("namePattern") String namePattern);

    /**
     * Find namespaces by display name pattern (case insensitive)
     */
    @Query(value = "SELECT * FROM namespaces WHERE active = true AND display_name ILIKE '%' || :displayNamePattern || '%'", nativeQuery = true)
    List<Namespace> findByDisplayNameContaining(@Param("displayNamePattern") String displayNamePattern);

    /**
     * Check if namespace name exists
     */
    boolean existsByName(String name);

    /**
     * Find multiple namespaces by names
     */
    @Query("SELECT n FROM Namespace n WHERE n.name IN :names AND n.active = true")
    List<Namespace> findByNameInAndActiveTrue(@Param("names") List<String> names);

    /**
     * Search namespaces by name or display name with pagination
     */
    @Query(value = "SELECT * FROM namespaces WHERE deleted = false AND (name ILIKE '%' || :name || '%' OR display_name ILIKE '%' || :displayName || '%')", nativeQuery = true)
    Page<Namespace> findByNameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            @Param("name") String name,
            @Param("displayName") String displayName,
            Pageable pageable);

    /**
     * Find all non-deleted namespaces with pagination
     */
    @Query("SELECT n FROM Namespace n WHERE (n.deleted = false OR n.deleted IS NULL)")
    Page<Namespace> findAllNonDeleted(Pageable pageable);
}
