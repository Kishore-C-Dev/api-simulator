package com.simulator.controller;

import com.simulator.model.UserProfile;
import com.simulator.model.Namespace;
import com.simulator.service.UserService;
import com.simulator.service.SessionManager;
import com.simulator.repository.NamespaceRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private NamespaceRepository namespaceRepository;
    
    /**
     * Show login page
     */
    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        // If already authenticated, redirect to dashboard
        if (sessionManager.isAuthenticated(session)) {
            return "redirect:/";
        }
        return "login";
    }
    
    /**
     * Process login form
     */
    @PostMapping("/login")
    public String authenticate(@RequestParam String userId, 
                             @RequestParam String password,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            Optional<UserProfile> userOpt = userService.authenticate(userId, password);
            if (userOpt.isPresent()) {
                UserProfile user = userOpt.get();
                sessionManager.setCurrentUser(session, user);
                
                logger.info("User {} logged in successfully", userId);
                return "redirect:/";
            } else {
                redirectAttributes.addFlashAttribute("error", "Invalid credentials");
                return "redirect:/login";
            }
        } catch (Exception e) {
            logger.error("Login error for user {}: {}", userId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Login failed. Please try again.");
            return "redirect:/login";
        }
    }
    
    /**
     * Switch namespace
     */
    @PostMapping("/switch-namespace")
    public String switchNamespace(@RequestParam String namespace,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        try {
            UserProfile user = sessionManager.getCurrentUser(session);
            if (user == null) {
                return "redirect:/login";
            }
            
            if (user.hasNamespace(namespace)) {
                sessionManager.setCurrentNamespace(session, namespace);
                logger.info("User {} switched to namespace {}", user.getUserId(), namespace);
            } else {
                redirectAttributes.addFlashAttribute("error", "Access denied to namespace: " + namespace);
            }
        } catch (Exception e) {
            logger.error("Error switching namespace: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to switch namespace");
        }
        return "redirect:/";
    }
    
    /**
     * Logout
     */
    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        sessionManager.clearSession(session);
        redirectAttributes.addFlashAttribute("message", "Successfully logged out");
        return "redirect:/login";
    }
    
    /**
     * AJAX endpoint to get current user info
     */
    @GetMapping("/api/auth/user")
    @ResponseBody
    public Object getCurrentUser(HttpSession session) {
        UserProfile user = sessionManager.getCurrentUser(session);
        if (user != null) {
            return new UserInfo(
                user.getUserId(),
                user.getDisplayName(),
                sessionManager.getCurrentNamespace(session),
                userService.getUserNamespaces(user.getUserId())
            );
        }
        return null;
    }
    
    // Helper class for API response
    public static class UserInfo {
        private String userId;
        private String displayName;
        private String currentNamespace;
        private List<Namespace> namespaces;
        
        public UserInfo(String userId, String displayName, String currentNamespace, List<Namespace> namespaces) {
            this.userId = userId;
            this.displayName = displayName;
            this.currentNamespace = currentNamespace;
            this.namespaces = namespaces;
        }
        
        // Getters
        public String getUserId() { return userId; }
        public String getDisplayName() { return displayName; }
        public String getCurrentNamespace() { return currentNamespace; }
        public List<Namespace> getNamespaces() { return namespaces; }
    }
}