package com.simulator.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "namespaces")
public class Namespace {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String name;            // e.g., "team-a", "project-x"
    
    private String displayName;     // e.g., "Team Alpha", "Project X"
    private String description;
    private List<String> members;   // User IDs with access
    private String owner;           // Owner user ID
    private Instant createdAt;
    private boolean active;
    private boolean deleted;
    
    // Constructors
    public Namespace() {
        this.members = new ArrayList<>();
        this.active = true;
        this.deleted = false;
        this.createdAt = Instant.now();
    }
    
    public Namespace(String name, String displayName, String owner) {
        this();
        this.name = name;
        this.displayName = displayName;
        this.owner = owner;
        this.members.add(owner); // Owner is automatically a member
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getMembers() {
        return members;
    }
    
    public void setMembers(List<String> members) {
        this.members = members;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
    public boolean hasMember(String userId) {
        return members != null && members.contains(userId);
    }
    
    public void addMember(String userId) {
        if (members == null) {
            members = new ArrayList<>();
        }
        if (!members.contains(userId)) {
            members.add(userId);
        }
    }
    
    public void removeMember(String userId) {
        if (members != null) {
            members.remove(userId);
        }
    }
    
    public boolean isOwner(String userId) {
        return owner != null && owner.equals(userId);
    }
    
    public String getEffectiveDisplayName() {
        return displayName != null && !displayName.trim().isEmpty() ? displayName : name;
    }
    
    @Override
    public String toString() {
        return "Namespace{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", members=" + members +
                ", owner='" + owner + '\'' +
                ", active=" + active +
                '}';
    }
}