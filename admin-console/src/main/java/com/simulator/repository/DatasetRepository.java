package com.simulator.repository;

import com.simulator.model.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, String> {

    Optional<Dataset> findByName(String name);

    Optional<Dataset> findByActiveTrue();
}
