package com.simulator.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "namespaces")
public class Namespace {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "name", unique = true)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "members", columnDefinition = "jsonb")
    private List<String> members;

    @Column(name = "owner")
    private String owner;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "active")
    private boolean active;

    @Column(name = "deleted")
    private boolean deleted;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

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
        this.members.add(owner);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

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
