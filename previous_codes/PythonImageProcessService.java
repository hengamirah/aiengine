package com.example.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Service
@Data

public class PythonImageProcessService {
    private static final Logger logger = LoggerFactory.getLogger(PythonProcessService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Script paths
    @Value("${python.base_path}")
    private String basePath;
        
    private static String ID_PREDICTOR_SCRIPT;
    private static String CROP_ID_SCRIPT;
    private static String FACEMATCH_SCRIPT;

    // Model paths
    private static String MODEL_PATH;
    private static String ID_CARD_MODEL;
    private static String ID_CARD_BLUR_MODEL;
    private static String PERSON_MODEL;
    private static String PERSON_BLUR_MODEL;

    // Persistent server configuration
    private Process persistentServerProcess = null;
    private BufferedWriter serverInput = null;
    private BufferedReader serverOutput = null;
    private boolean serverRunning = false;
    private final Object serverLock = new Object();
    
    // temporary init before moving to restful services
    public void init() {

        // Scripts
        ID_PREDICTOR_SCRIPT = basePath + "ID_predictor_onnx.py";
        CROP_ID_SCRIPT      = basePath + "crop_id.py";
        FACEMATCH_SCRIPT    = basePath + "facematch.py";

        // Models
        MODEL_PATH          = basePath + "model/";
        ID_CARD_MODEL       = MODEL_PATH + "id_card_detection.onnx";
        ID_CARD_BLUR_MODEL  = MODEL_PATH + "id_card_blur_detection.onnx";
        PERSON_MODEL        = MODEL_PATH + "person_detection.onnx";
        PERSON_BLUR_MODEL   = MODEL_PATH + "person_blur_detection.onnx";
    }
    // Start persistent server process
    @PostConstruct
    public void startPersistentServer() {
    	this.init();
        logger.info("🚀 Starting Python model server...");
        
        // Check if model_server.py exists first
        Path serverScript = Paths.get(basePath + "model_server.py");
        if (!Files.exists(serverScript)) {
            logger.warn("⚠️ model_server.py not found. Using fallback mode only.");
            return; // Skip server startup
        }
        
        try {
            synchronized (serverLock) {
                // Start the persistent server
                ProcessBuilder pb = new ProcessBuilder(
                    "python", "-u", basePath + "model_server.py"
                );
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                
                persistentServerProcess = pb.start();
                
                // Check if process started successfully
                if (!persistentServerProcess.isAlive()) {
                    logger.error("❌ Python server process failed to start");
                    return;
                }
                serverInput = new BufferedWriter(new OutputStreamWriter(persistentServerProcess.getOutputStream()));
                serverOutput = new BufferedReader(new InputStreamReader(persistentServerProcess.getInputStream()));
                
                // ✅ Create separate error stream reader
                BufferedReader errorStream = new BufferedReader(new InputStreamReader(persistentServerProcess.getErrorStream()));
                
                // ✅ Start background thread to read stderr (logs)
                Thread errorReaderThread = new Thread(() -> {
                    try {
                        String errorLine;
                        while ((errorLine = errorStream.readLine()) != null) {
                            // Log Python stderr messages with proper filtering
                            if (errorLine.contains("ready for requests") || 
                                errorLine.contains("SUCCESS") || 
                                errorLine.contains("ERROR") ||
                                errorLine.contains("CRITICAL") ||
                                errorLine.contains("Models loaded") ||
                                errorLine.contains("Logic checker")) {
                                logger.info("🐍 {}", errorLine);
                            }
                        }
                    } catch (IOException e) {
                        logger.debug("Error stream reader ended: {}", e.getMessage());
                    }
                });
                errorReaderThread.setDaemon(true);
                errorReaderThread.start();
                
                serverRunning = true;
                
                // Wait for ready message by monitoring the error stream
                boolean readyMessageSeen = false;
                for (int i = 0; i < 30; i++) {
                    Thread.sleep(1000);
                    // Check if the server is ready by sending a health check
                    if (i > 5) {  // Give it a few seconds to start
                        try {
                            Map<String, Object> healthResponse = sendServerRequest(
                                Map.of("command", "health"), 5000
                            );
                            if (healthResponse != null) {
                                logger.info("🏥 Health check response: {}", healthResponse);
                                
                                // Check if models are actually loaded
                                Boolean modelsLoaded = (Boolean) healthResponse.get("models_loaded");
                                if (Boolean.TRUE.equals(modelsLoaded)) {
                                    readyMessageSeen = true;
                                    logger.info("✅ Server ready with models loaded");
                                    break;
                                } else {
                                    logger.warn("⚠️ Server responding but models not loaded yet");
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Health check failed on attempt {}: {}", i, e.getMessage());
                        }
                    }
                }
                
                if (!readyMessageSeen) {
                    logger.error("❌ Server failed to load models after 30 seconds");
                    serverRunning = false;
                    stopPersistentServer();
                } else {
                    logger.info("✅ Server ready and responding with models loaded");
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to start server: {}", e.getMessage());
            logger.info("🔄 Will use fallback mode for all requests");
            stopPersistentServer();
        }
    }
    
    @PreDestroy
    public void stopPersistentServer() {
        synchronized (serverLock) {
            if (serverRunning) {
                logger.info("🛑 Stopping persistent server...");
                
                try {
                    // Send shutdown command
                    sendServerRequest(Map.of("command", "shutdown"), 2000);
                } catch (Exception e) {
                    logger.warn("Failed to send shutdown command: {}", e.getMessage());
                }
                
                // Clean up streams
                try {
                    if (serverInput != null) serverInput.close();
                    if (serverOutput != null) serverOutput.close();
                } catch (IOException e) {
                    logger.warn("Error closing streams: {}", e.getMessage());
                }
                
                // Terminate process
                if (persistentServerProcess != null && persistentServerProcess.isAlive()) {
                    persistentServerProcess.destroyForcibly();
                }
                
                serverRunning = false;
                serverInput = null;
                serverOutput = null;
                persistentServerProcess = null;
            }
        }
    }
    // Send request to persistent server and get response
    private Map<String, Object> sendServerRequest(Map<String, Object> request, int timeoutMillis) throws IOException, InterruptedException {
        synchronized (serverLock) {
            if (!serverRunning || serverInput == null || serverOutput == null) {
                throw new IllegalStateException("Server not running");
            }
            
            // Send request as JSON line
            String requestJson = objectMapper.writeValueAsString(request);
            serverInput.write(requestJson);
            serverInput.newLine();
            serverInput.flush();
            
            // Wait for response with timeout
            long startTime = System.currentTimeMillis();
            StringBuilder responseBuilder = new StringBuilder();
            while (true) {
                if (serverOutput.ready()) {
                    String line = serverOutput.readLine();
                    if (line != null) {
                        responseBuilder.append(line);
                        // Assume single-line JSON response
                        break;
                    }
                }
                
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    throw new IOException("Timeout waiting for server response");
                }
                
                Thread.sleep(50); // Avoid busy wait
            }
            
            String responseJson = responseBuilder.toString().trim();
            if (responseJson.isEmpty()) {
                throw new IOException("Empty response from server");
            }
            
            // Parse JSON response
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
                return response;
            } catch (Exception e) {
                throw new IOException("Failed to parse JSON response: " + e.getMessage());
            }
        }
    }
        /**
     * Detect objects (ID card or person) with optional element extraction
     */
    public List<Map<String, Object>> detectObjects(String imagePath, String objectType) {
        logger.info("🔍 Detecting {} in: {}", objectType, imagePath);
        
        // Check if image file exists
        if (!Files.exists(Paths.get(imagePath))) {
            logger.error("❌ Image file not found: {}", imagePath);
            return Collections.singletonList(createDetectionError("Image file not found: " + imagePath));
        }
        

        String modelPath, blurModelPath;
        if ("person".equalsIgnoreCase(objectType)) {
            modelPath = PERSON_MODEL;
            blurModelPath = PERSON_BLUR_MODEL;
            logger.debug("Using PERSON detection models");
        } else {
            modelPath = ID_CARD_MODEL;
            blurModelPath = ID_CARD_BLUR_MODEL;
            logger.debug("Using ID_CARD detection models");
        }
        
        
        List<String> command = Arrays.asList(
            "python", "-u", ID_PREDICTOR_SCRIPT,
            "--model", modelPath,
            "--img", imagePath,
            "--blur_model", blurModelPath,
            "--object_type", objectType  // ✅ This ensures correct object type is passed
        );
        
       try {
        PythonResult result = executePythonScript(command, 60);
        
        if (!result.isSuccess()) {
            String errorMsg = extractErrorMessage(result.getOutput(), "Object detection");
            logger.error("❌ Detection failed: {}", errorMsg);
            return Collections.singletonList(createDetectionError(errorMsg));
        }
        
        List<Map<String, Object>> detections = parseJsonDetections(result.getOutput());
        logger.info("📊 Found {} detections", detections.size());
        
        // ✅ FIX: Apply web path generation to all detections
        for (int i = 0; i < detections.size(); i++) {
            Map<String, Object> detection = detections.get(i);
            
            // Fix crop_path to web_path if it exists
            if (detection.containsKey("crop_path")) {
                String cropPath = (String) detection.get("crop_path");
                String webPath = generateWebPath(cropPath);
                detection.put("web_path", webPath);
                
                logger.debug("🌐 Detection {} crop path: {} -> web path: {}", i + 1, cropPath, webPath);
            }
        }
        
        // Log only summary
        if (!detections.isEmpty()) {
            Map<String, Object> firstDetection = detections.get(0);
            String className = (String) firstDetection.get("class_name");
            double confidence = ((Number) firstDetection.getOrDefault("confidence", 0.0)).doubleValue();
            logger.info("✅ Main detection: {} (confidence: {:.2f})", className, confidence);
        }
        
        // Element extraction for ID cards
        if ("id_card".equalsIgnoreCase(objectType) && shouldExtractElements(detections)) {
            logger.info("🔍 Extracting ID elements...");
            List<Map<String, Object>> elements = extractIdElements(imagePath);
            
            if (!elements.isEmpty()) {
                // ✅ FIX: Apply web path generation to extracted elements too
                for (Map<String, Object> element : elements) {
                    if (element.containsKey("crop_path")) {
                        String cropPath = (String) element.get("crop_path");
                        String webPath = generateWebPath(cropPath);
                        element.put("web_path", webPath);
                        
                        logger.debug("🌐 Element crop path: {} -> web path: {}", cropPath, webPath);
                    }
                }
                
                logger.info("✂️ Extracted {} elements: {}", 
                    elements.size(),
                    elements.stream()
                        .map(e -> e.get("class_name"))
                        .collect(java.util.stream.Collectors.toList()));
                        
                detections = mergeDetectionResults(detections, elements);
            }
        }
        // For person photos
        else if ("person".equalsIgnoreCase(objectType) && !detections.isEmpty()) {
            logger.info("✅ Person detected in photo, checking for face crop...");
            
            for (Map<String, Object> detection : detections) {
                if (detection.containsKey("crop_path")) {
                    String cropPath = (String) detection.get("crop_path");
                    logger.info("✅ Found person crop from detection: {}", cropPath);
                    
                    // Check if crop file actually exists
                    if (Files.exists(Paths.get(cropPath))) {
                        logger.info("✅ Crop file exists and accessible");
                        try {
                            long cropSize = Files.size(Paths.get(cropPath));
                            logger.info("📄 Crop file size: {} bytes", cropSize);
                        } catch (IOException e) {
                            logger.warn("⚠️ Could not get crop file size: {}", e.getMessage());
                        }
                    } else {
                        logger.error("❌ Crop file does not exist: {}", cropPath);
                    }
                } else {
                    logger.warn("⚠️ Person detection missing crop_path");
                }
            }
        }
        
        // ✅ REMOVE: Redundant logging
        return detections;
        
    } catch (Exception e) {
        logger.error("❌ Detection failed: {}", e.getMessage());
        return Collections.singletonList(createDetectionError(e.getMessage()));
    }
}
    /**
     * Extract ID card elements (face, signature, expiry date)
     */
    public List<Map<String, Object>> extractIdElements(String imagePath) {
        logger.info("✂️ Extracting ID elements from: {}", imagePath);
        
        // Check if image file exists
        if (!Files.exists(Paths.get(imagePath))) {
            logger.error("❌ Image file not found: {}", imagePath);
            return Collections.singletonList(createDetectionError("Image file not found: " + imagePath));
        }
        
        List<String> command = Arrays.asList(
            "python", "-u", CROP_ID_SCRIPT,
            "--img", imagePath
        );
        
        try {
            PythonResult result = executePythonScript(command, 60);
            
            if (!result.isSuccess()) {
                String errorMsg = extractErrorMessage(result.getOutput(), "Element extraction");
                logger.error("❌ Extraction failed: {}", errorMsg);
                return Collections.singletonList(createDetectionError(errorMsg));
            }
            
            List<Map<String, Object>> elements = parseJsonDetections(result.getOutput());
            logger.info("📊 Extracted {} elements", elements.size());
            
            // ✅ FIX: Apply web path generation to all elements
            for (int i = 0; i < elements.size(); i++) {
                Map<String, Object> element = elements.get(i);
                
                // Fix crop_path to web_path if it exists
                if (element.containsKey("crop_path")) {
                    String cropPath = (String) element.get("crop_path");
                    String webPath = generateWebPath(cropPath);
                    element.put("web_path", webPath);
                    
                    logger.debug("🌐 Element {} crop path: {} -> web path: {}", i + 1, cropPath, webPath);
                }
            }
            
            // Log only summary
            if (!elements.isEmpty()) {
                Map<String, Object> firstElement = elements.get(0);
                String className = (String) firstElement.get("class_name");
                double confidence = ((Number) firstElement.getOrDefault("confidence", 0.0)).doubleValue();
                logger.info("✅ Main extracted element: {} (confidence: {:.2f})", className, confidence);
            }
            
            return elements;
            
        } catch (Exception e) {
            logger.error("❌ Extraction failed: {}", e.getMessage());
            return Collections.singletonList(createDetectionError(e.getMessage()));
        }

    
}

}