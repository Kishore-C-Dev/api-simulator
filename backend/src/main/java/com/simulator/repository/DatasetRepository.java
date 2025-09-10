package com.simulator.repository;

import com.simulator.model.Dataset;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DatasetRepository extends MongoRepository<Dataset, String> {
    
    Optional<Dataset> findByName(String name);
    
    Optional<Dataset> findByActiveTrue();
}