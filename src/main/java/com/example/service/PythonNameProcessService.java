package com.example.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.model.NameVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PythonNameProcessService {
    private static final Logger logger = LoggerFactory.getLogger(PythonNameProcessService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 30;

    @Value("${python.base_path}")
    private String pythonBasePath;

    public NameVerificationResult verifyName(String firstName, String lastName) {
        try {
            Path scriptPath = Paths.get(pythonBasePath, "name_verification.py");
            if (!Files.exists(scriptPath)) {
                return createErrorResult("Python script not found at: " + scriptPath);
            }

            String pythonCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
            
            String[] command = {
                pythonCmd,
                scriptPath.toString(),
                firstName != null ? firstName : "",
                lastName != null ? lastName : ""
            };

            logger.debug("[CMD] Executing command: {}", String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("NAME_VERIF_DEBUG", "true");
            pb.redirectErrorStream(false);
            
            Process process = pb.start();
            
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return createErrorResult("Python script timed out");
            }

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            
            logger.debug("[OUT] Python stdout: {}", stdout.length() > 500 ? stdout.substring(0, 500) + "..." : stdout);
            if (!stderr.isEmpty()) {
                logger.debug("[ERR] Python stderr: {}", stderr.length() > 500 ? stderr.substring(0, 500) + "..." : stderr);
            }

            if (process.exitValue() != 0) {
                return createErrorResult("Python script failed. Stderr: " + stderr + ". Stdout: " + stdout);
            }

            Map<String, Object> result = parseJsonResult(stdout);
            return convertMapToNameVerificationResult(result);

        } catch (Exception e) {
            logger.error("[FAIL] Name verification failed", e);
            return createErrorResult("System error: " + e.getMessage());
        }
    }

    private String readStream(java.io.InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            logger.warn("[WARN] Error reading stream: {}", e.getMessage());
            return "";
        }
    }

    private Map<String, Object> parseJsonResult(String jsonOutput) {
        try {
            if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
                return createErrorMap("Empty output from Python script");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(jsonOutput.trim(), Map.class);
            
            // Validate required fields
            if (!result.containsKey("decision") || !result.containsKey("reasons")) {
                logger.error("Invalid JSON structure from Python script: {}", jsonOutput);
                return createErrorMap("Invalid response format from Python script");
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("[FAIL] Failed to parse JSON output: {}", jsonOutput, e);
            return createErrorMap("JSON parse error: " + e.getMessage());
        }
    }

    private NameVerificationResult convertMapToNameVerificationResult(Map<String, Object> map) {
        NameVerificationResult result = new NameVerificationResult();
        
        result.setDecision((String) map.get("decision"));
        
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) map.get("reasons");
        result.setReasons(reasons != null ? reasons : Collections.emptyList());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> notes = (Map<String, Object>) map.get("notes");
        result.setNotes(notes != null ? notes : Collections.emptyMap());
        
        
        
        return result;
    }

    private Map<String, Object> createErrorMap(String reason) {
        return Map.of(
            "decision", "ERROR",
            "reasons", Arrays.asList(reason),
            "notes", Map.of(
                "error", true,
                "timestamp", LocalDateTime.now().toString()
            )
        );
    }

    private NameVerificationResult createErrorResult(String reason) {
        NameVerificationResult result = new NameVerificationResult();
        result.setDecision("ERROR");
        result.setReasons(Arrays.asList(reason));
        result.setNotes(Map.of("error", true));

        return result;
    }
}

