package com.simulator.service;

import com.simulator.model.UserProfile;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    private static final String SESSION_USER_KEY = "currentUser";
    private static final String SESSION_NAMESPACE_KEY = "currentNamespace";
    private static final String SESSION_LOGIN_TIME_KEY = "loginTime";
    
    /**
     * Set current user in session
     */
    public void setCurrentUser(HttpSession session, UserProfile user) {
        if (session != null && user != null) {
            session.setAttribute(SESSION_USER_KEY, user);
            session.setAttribute(SESSION_LOGIN_TIME_KEY, Instant.now());
            
            // Set default namespace if user has any
            if (user.getDefaultNamespace() != null) {
                session.setAttribute(SESSION_NAMESPACE_KEY, user.getDefaultNamespace());
            } else if (user.getNamespaces() != null && !user.getNamespaces().isEmpty()) {
                session.setAttribute(SESSION_NAMESPACE_KEY, user.getNamespaces().get(0));
            }
            
            logger.info("User {} logged in with namespace {}", user.getUserId(), getCurrentNamespace(session));
        }
    }
    
    /**
     * Set current namespace in session
     */
    public void setCurrentNamespace(HttpSession session, String namespace) {
        if (session != null && namespace != null) {
            UserProfile user = getCurrentUser(session);
            if (user != null && user.hasNamespace(namespace)) {
                session.setAttribute(SESSION_NAMESPACE_KEY, namespace);
                logger.info("User {} switched to namespace {}", user.getUserId(), namespace);
            } else {
                logger.warn("User {} attempted to switch to unauthorized namespace {}", 
                    user != null ? user.getUserId() : "unknown", namespace);
            }
        }
    }
    
    /**
     * Get current user from session
     */
    public UserProfile getCurrentUser(HttpSession session) {
        if (session != null) {
            return (UserProfile) session.getAttribute(SESSION_USER_KEY);
        }
        return null;
    }
    
    /**
     * Get current namespace from session
     */
    public String getCurrentNamespace(HttpSession session) {
        if (session != null) {
            return (String) session.getAttribute(SESSION_NAMESPACE_KEY);
        }
        return null;
    }
    
    /**
     * Get login time from session
     */
    public Instant getLoginTime(HttpSession session) {
        if (session != null) {
            return (Instant) session.getAttribute(SESSION_LOGIN_TIME_KEY);
        }
        return null;
    }
    
    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated(HttpSession session) {
        return getCurrentUser(session) != null;
    }
    
    /**
     * Check if user has access to namespace
     */
    public boolean hasNamespaceAccess(HttpSession session, String namespace) {
        UserProfile user = getCurrentUser(session);
        return user != null && user.hasNamespace(namespace);
    }
    
    /**
     * Clear session (logout)
     */
    public void clearSession(HttpSession session) {
        if (session != null) {
            UserProfile user = getCurrentUser(session);
            if (user != null) {
                logger.info("User {} logged out", user.getUserId());
            }
            session.invalidate();
        }
    }
    
    /**
     * Get user display name for UI
     */
    public String getUserDisplayName(HttpSession session) {
        UserProfile user = getCurrentUser(session);
        return user != null ? user.getDisplayName() : "Unknown User";
    }
}