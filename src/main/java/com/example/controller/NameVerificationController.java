package com.example.controller;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.model.NameRegistrationRequest;
import com.example.model.NameVerificationResult;
import com.example.service.NameVerificationService;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/verify-name")
public class NameVerificationController {

    private final NameVerificationService nameVerificationService;

    public NameVerificationController(NameVerificationService nameVerificationService) {
        this.nameVerificationService = nameVerificationService;
    }

    private static final Logger logger = LoggerFactory.getLogger(NameVerificationController.class);

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<NameVerificationResult> verifyName(@RequestBody NameRegistrationRequest request) {
        logger.debug("Received request for name verification: firstname={}, lastname={}",
            request.getFirstname(), request.getLastname());

        try {
            // Delegate to the Java service which verifies and persists
            NameVerificationResult result = nameVerificationService.verifyAndSave(request);
            logger.debug("Verification result: {}", result);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .body(result);
        } catch (Exception e) {
            logger.error("Error processing name verification", e);
            NameVerificationResult errorResult = new NameVerificationResult();
            errorResult.setDecision("ERROR");
            errorResult.setReasons(Arrays.asList("Internal server error"));
            errorResult.setNotes(Map.of("error", e.getMessage()));
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .body(errorResult);
        }
    }

    @GetMapping(path = "/health", produces = "application/json")
    public ResponseEntity<NameVerificationResult> health() {
        NameVerificationResult r = new NameVerificationResult();
        r.setDecision("OK");
        r.setReasons(Arrays.asList("Service running"));
        r.setNotes(Map.of(
            "status", "healthy",
            "timestamp", LocalDateTime.now().toString()
        ));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(r);
    }

}
