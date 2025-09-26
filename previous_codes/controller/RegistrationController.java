package com.example.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simple-registration")
public class RegistrationController {
    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);
    
    @Autowired
    private DatabaseService databaseService;
    
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testRegistration(@RequestBody Map<String, String> request) {
        logger.info("🧪 Testing simple registration");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String firstName = request.getOrDefault("first_name", "TestUser");
            String lastName = request.getOrDefault("last_name", "TestLast");
            String dob = request.getOrDefault("dob", "1990-01-01");
            
            // Save with ACCEPTED status
            databaseService.saveRegistration(firstName, lastName, dob, "ACCEPTED");
            
            long acceptedCount = databaseService.countAcceptedRegistrations();
            
            response.put("status", "success");
            response.put("message", "Registration saved successfully");
            response.put("accepted_count", acceptedCount);
            response.put("database_available", databaseService.isDatabaseAvailable());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Test failed", e);
            response.put("status", "error");
            response.put("message", "Test failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/simulate-10")
    public ResponseEntity<Map<String, Object>> simulate10Accepted() {
        logger.info("🎯 Simulating 10 accepted registrations");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Save 10 test registrations
            for (int i = 1; i <= 10; i++) {
                String firstName = "User" + i;
                String lastName = "Test" + i;
                String dob = "199" + (i % 10) + "-01-" + String.format("%02d", i);
                
                databaseService.saveRegistration(firstName, lastName, dob, "ACCEPTED");
                logger.info("📝 Saved {}/10: {} {}", i, firstName, lastName);
            }
            
            long acceptedCount = databaseService.countAcceptedRegistrations();
            
            response.put("status", "success");
            response.put("message", "10 registrations simulated");
            response.put("total_accepted_count", acceptedCount);
            response.put("retrain_triggered", true);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Simulation failed", e);
            response.put("status", "error");
            response.put("message", "Simulation failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean dbAvailable = databaseService.isDatabaseAvailable();
            long acceptedCount = dbAvailable ? databaseService.countAcceptedRegistrations() : 0;
            
            response.put("database_available", dbAvailable);
            response.put("accepted_registrations_count", acceptedCount);
            response.put("status", "success");
            
            if (dbAvailable) {
                List<Registration> recentRegistrations = databaseService.getAllRegistrations();
                response.put("total_registrations", recentRegistrations.size());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Failed to get status", e);
            response.put("status", "error");
            response.put("message", "Failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}