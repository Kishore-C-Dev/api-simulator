package com.simulator.repository;

import com.simulator.model.RequestMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestMappingRepository extends JpaRepository<RequestMapping, String> {

    @Query("SELECT m FROM RequestMapping m WHERE m.namespace = :namespace AND m.enabled = :enabled AND (m.deleted = false OR m.deleted IS NULL) ORDER BY m.priority DESC")
    List<RequestMapping> findByNamespaceAndEnabledOrderByPriorityDesc(@Param("namespace") String namespace, @Param("enabled") Boolean enabled);

    @Query("SELECT m FROM RequestMapping m WHERE m.namespace = :namespace AND (m.deleted = false OR m.deleted IS NULL) ORDER BY m.priority DESC")
    List<RequestMapping> findByNamespaceOrderByPriorityDesc(@Param("namespace") String namespace);

    @Query("SELECT m FROM RequestMapping m WHERE m.namespace = :namespace AND LOWER(m.name) LIKE LOWER(CONCAT('%',:name,'%')) AND (m.deleted = false OR m.deleted IS NULL)")
    Page<RequestMapping> findByNamespaceAndNameContainingIgnoreCase(@Param("namespace") String namespace, @Param("name") String name, Pageable pageable);

    @Query("SELECT m FROM RequestMapping m WHERE m.namespace = :namespace AND (m.deleted = false OR m.deleted IS NULL)")
    Page<RequestMapping> findByNamespace(@Param("namespace") String namespace, Pageable pageable);

    @Query(value = "SELECT * FROM mappings WHERE namespace = :namespace AND (deleted = false OR deleted IS NULL) AND (name ILIKE '%' || :term || '%' OR request->>'path' ILIKE '%' || :term || '%' OR request->>'method' ILIKE '%' || :term || '%')", nativeQuery = true)
    Page<RequestMapping> findByNamespaceAndSearchCriteria(@Param("namespace") String namespace, @Param("term") String searchTerm, Pageable pageable);

    @Query(value = "SELECT * FROM mappings WHERE namespace = :namespace AND (deleted = false OR deleted IS NULL) AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(tags) t WHERE t = ANY(CAST(:tags AS text[])))", nativeQuery = true)
    List<RequestMapping> findByNamespaceAndTagsIn(@Param("namespace") String namespace, @Param("tags") String[] tags);

    @Modifying
    @Query("DELETE FROM RequestMapping m WHERE m.namespace = :namespace")
    void deleteByNamespace(@Param("namespace") String namespace);
}
