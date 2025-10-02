package com.simulator.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "userProfiles")
public class UserProfile {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String userId;           // Unique user identifier (username)
    
    @Indexed
    private String email;
    
    private String firstName;
    private String lastName;
    private String passwordHash;     // Hashed password for simple auth
    private List<String> namespaces; // Assigned namespace names
    private String defaultNamespace;
    private Instant createdAt;
    private Instant lastLogin;
    private boolean active;
    private boolean deleted;
    
    // Constructors
    public UserProfile() {
        this.namespaces = new ArrayList<>();
        this.active = true;
        this.deleted = false;
        this.createdAt = Instant.now();
    }
    
    public UserProfile(String userId, String email, String firstName, String lastName) {
        this();
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public List<String> getNamespaces() {
        return namespaces;
    }
    
    public void setNamespaces(List<String> namespaces) {
        this.namespaces = namespaces;
    }
    
    public String getDefaultNamespace() {
        return defaultNamespace;
    }
    
    public void setDefaultNamespace(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public boolean isDeleted() {
        return deleted;
    }
    
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    
    // Helper methods
    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return userId;
    }
    
    public boolean hasNamespace(String namespace) {
        return namespaces != null && namespaces.contains(namespace);
    }
    
    public void addNamespace(String namespace) {
        if (namespaces == null) {
            namespaces = new ArrayList<>();
        }
        if (!namespaces.contains(namespace)) {
            namespaces.add(namespace);
        }
        if (defaultNamespace == null) {
            defaultNamespace = namespace;
        }
    }
    
    public void removeNamespace(String namespace) {
        if (namespaces != null) {
            namespaces.remove(namespace);
            if (namespace.equals(defaultNamespace)) {
                defaultNamespace = namespaces.isEmpty() ? null : namespaces.get(0);
            }
        }
    }
    
    @Override
    public String toString() {
        return "UserProfile{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", namespaces=" + namespaces +
                ", defaultNamespace='" + defaultNamespace + '\'' +
                ", active=" + active +
                '}';
    }
}