package com.simulator.service;

import com.simulator.model.UserProfile;
import com.simulator.model.Namespace;
import com.simulator.repository.UserProfileRepository;
import com.simulator.repository.NamespaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserProfileRepository userRepository;
    
    @Autowired
    private NamespaceRepository namespaceRepository;
    
    /**
     * Authenticate user with userId and password
     */
    public Optional<UserProfile> authenticate(String userId, String password) {
        try {
            Optional<UserProfile> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent()) {
                UserProfile user = userOpt.get();
                if (user.isActive() && verifyPassword(password, user.getPasswordHash())) {
                    // Update last login time
                    user.setLastLogin(Instant.now());
                    userRepository.save(user);
                    logger.info("User {} authenticated successfully", userId);
                    return Optional.of(user);
                }
            }
            logger.warn("Authentication failed for user {}", userId);
        } catch (Exception e) {
            logger.error("Error during authentication for user {}: {}", userId, e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Create new user
     */
    public UserProfile createUser(String userId, String email, String firstName, String lastName, String password) {
        if (userRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User ID already exists: " + userId);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        
        UserProfile user = new UserProfile(userId, email, firstName, lastName);
        user.setPasswordHash(hashPassword(password));
        
        UserProfile savedUser = userRepository.save(user);
        logger.info("Created new user: {}", userId);
        return savedUser;
    }
    
    /**
     * Get user by userId
     */
    public Optional<UserProfile> getUserByUserId(String userId) {
        return userRepository.findByUserId(userId);
    }
    
    /**
     * Get user's accessible namespaces
     */
    public List<Namespace> getUserNamespaces(String userId) {
        return namespaceRepository.findByMember(userId);
    }
    
    /**
     * Assign namespace to user
     */
    public void assignNamespaceToUser(String userId, String namespace) {
        Optional<UserProfile> userOpt = userRepository.findByUserId(userId);
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespace);
        
        if (userOpt.isPresent() && namespaceOpt.isPresent()) {
            UserProfile user = userOpt.get();
            Namespace ns = namespaceOpt.get();
            
            user.addNamespace(namespace);
            ns.addMember(userId);
            
            userRepository.save(user);
            namespaceRepository.save(ns);
            
            logger.info("Assigned namespace {} to user {}", namespace, userId);
        } else {
            throw new IllegalArgumentException("User or namespace not found");
        }
    }
    
    /**
     * Remove namespace from user
     */
    public void removeNamespaceFromUser(String userId, String namespace) {
        Optional<UserProfile> userOpt = userRepository.findByUserId(userId);
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespace);
        
        if (userOpt.isPresent() && namespaceOpt.isPresent()) {
            UserProfile user = userOpt.get();
            Namespace ns = namespaceOpt.get();
            
            user.removeNamespace(namespace);
            ns.removeMember(userId);
            
            userRepository.save(user);
            namespaceRepository.save(ns);
            
            logger.info("Removed namespace {} from user {}", namespace, userId);
        }
    }
    
    /**
     * Hash password using SHA-256 (simple implementation for MVP)
     */
    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
    
    /**
     * Verify password against hash
     */
    private boolean verifyPassword(String password, String hash) {
        return hashPassword(password).equals(hash);
    }
    
    /**
     * Get all active users
     */
    public List<UserProfile> getAllActiveUsers() {
        return userRepository.findByActiveTrueAndDeletedFalse();
    }
    
    /**
     * Deactivate user
     */
    public void deactivateUser(String userId) {
        Optional<UserProfile> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isPresent()) {
            UserProfile user = userOpt.get();
            user.setActive(false);
            userRepository.save(user);
            logger.info("Deactivated user: {}", userId);
        }
    }
}