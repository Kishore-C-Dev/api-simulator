package com.simulator.service;

import com.simulator.model.UserProfile;
import com.simulator.model.Namespace;
import com.simulator.repository.UserProfileRepository;
import com.simulator.repository.NamespaceRepository;
import com.simulator.repository.RequestMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemoDataSeeder implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DemoDataSeeder.class);
    
    @Autowired
    private UserProfileRepository userRepository;
    
    @Autowired
    private NamespaceRepository namespaceRepository;
    
    @Autowired
    private RequestMappingRepository mappingRepository;
    
    @Autowired
    private UserService userService;
    
    @Override
    public void run(String... args) throws Exception {
        createDemoUsersAndNamespaces();
        migrateLegacyData();
    }
    
    private void createDemoUsersAndNamespaces() {
        logger.info("Setting up demo users and namespaces...");
        
        // Create namespaces first
        createNamespaceIfNotExists("default", "Default Workspace", "System default namespace");
        createNamespaceIfNotExists("team-alpha", "Team Alpha", "Team Alpha development workspace");
        createNamespaceIfNotExists("team-beta", "Team Beta", "Team Beta development workspace");
        createNamespaceIfNotExists("shared", "Shared APIs", "Shared APIs across teams");
        
        // Create demo users
        createUserIfNotExists("admin", "admin@example.com", "Admin", "User", "password123", 
                            List.of("default", "team-alpha", "team-beta", "shared"));
        createUserIfNotExists("developer", "dev@example.com", "Jane", "Developer", "password123", 
                            List.of("team-alpha"));
        createUserIfNotExists("tester", "test@example.com", "John", "Tester", "password123", 
                            List.of("team-beta", "shared"));
        
        logger.info("Demo data setup completed");
    }
    
    private void createNamespaceIfNotExists(String name, String displayName, String description) {
        if (!namespaceRepository.existsByName(name)) {
            Namespace namespace = new Namespace(name, displayName, "admin");
            namespace.setDescription(description);
            namespaceRepository.save(namespace);
            logger.info("Created namespace: {}", name);
        }
    }
    
    private void createUserIfNotExists(String userId, String email, String firstName, String lastName, 
                                     String password, List<String> namespaces) {
        if (!userRepository.existsByUserId(userId)) {
            try {
                UserProfile user = userService.createUser(userId, email, firstName, lastName, password);
                
                // Assign namespaces
                for (String namespace : namespaces) {
                    user.addNamespace(namespace);
                    
                    // Add user to namespace members
                    namespaceRepository.findByName(namespace).ifPresent(ns -> {
                        ns.addMember(userId);
                        namespaceRepository.save(ns);
                    });
                }
                
                user.setDefaultNamespace(namespaces.get(0));
                userRepository.save(user);
                
                logger.info("Created user: {} with namespaces: {}", userId, namespaces);
            } catch (Exception e) {
                logger.error("Failed to create user {}: {}", userId, e.getMessage());
            }
        }
    }
    
    /**
     * Migrate legacy data from dataset to namespace
     */
    private void migrateLegacyData() {
        logger.info("Checking for legacy data migration...");
        
        // Find mappings that still use the old 'dataset' field but have null namespace
        var legacyMappings = mappingRepository.findAll().stream()
                .filter(mapping -> mapping.getNamespace() == null)
                .toList();
        
        if (!legacyMappings.isEmpty()) {
            logger.info("Migrating {} legacy mappings from dataset to namespace", legacyMappings.size());
            
            for (var mapping : legacyMappings) {
                // Set namespace to "default" for all legacy mappings
                mapping.setNamespace("default");
                mappingRepository.save(mapping);
            }
            
            logger.info("Legacy data migration completed");
        }
    }
}