/**
 Service handling file processing and detection coordination
    Responsibilities:
    Saves uploaded files to static/uploads/
    Coordinates with PythonImageProcessService for object detection
    Manages cropped elements (ID card, person, signature)
    Creates web-accessible paths for files
 */

package com.example.service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.service.PythonImageProcessService;
import com.example.util.FileProcessingResult;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class ImageVerificationService {

    private static final String UPLOAD_DIR = "static/uploads/";

    private final PythonImageProcessService pythonService;
    // Main method to process uploaded files and return results
    public FileProcessingResult processUploadedFiles(
            MultipartFile idFront, MultipartFile idBack,
            MultipartFile photo, MultipartFile signature) throws IOException {

        FileProcessingResult result = new FileProcessingResult();

        if (idFront != null && !idFront.isEmpty()) processFile(idFront, "id_front", "id_card", result);
        if (idBack != null && !idBack.isEmpty()) processFile(idBack, "id_back", "id_card", result);
        if (photo != null && !photo.isEmpty()) processFile(photo, "photo", "person", result);

        if (signature != null && !signature.isEmpty()) {
            String sigPath = saveUploadedFile(signature, "signature");
            result.addUploadResult("signature", createFileResult(sigPath, false));
            result.addCroppedElement("uploaded_signature", sigPath);
        }
        return result;
    }
    // Helper methods for file processing and result creation by calling python service 
    private void processFile(MultipartFile file, String fileType, String objectType,
                             FileProcessingResult result) throws IOException {

        String filePath = saveUploadedFile(file, fileType);
        List<Map<String, Object>> detections = pythonService.detectObjects(filePath, objectType);

        boolean hasValidDetection = detections.stream()
                .anyMatch(d -> !"no_object_detected".equals(d.get("class_name"))
                        && ((Number) d.getOrDefault("confidence", 0.0)).doubleValue() > 0.3);

        Map<String, Object> fileResult = createFileResult(filePath, hasValidDetection);
        enrichFileResultWithDetections(fileResult, detections);

        result.addUploadResult(fileType, fileResult);
        result.addDetections(fileType, detections);
        extractCroppedElements(fileType, detections, result);

        log.info("{} file processed (valid detection = {})", fileType, hasValidDetection);
    }
    // Save uploaded file to disk and return the saved path
    private String saveUploadedFile(MultipartFile file, String fileType) throws IOException {
        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";

        String newFilename = String.format("%s_%d_%d%s",
                fileType, System.currentTimeMillis(), (int) (Math.random() * 1000), extension);

        Path targetPath = Paths.get(UPLOAD_DIR, newFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("💾 Saved {} to {}", fileType, targetPath);
        return targetPath.toString();
    }
    // Create a result map for the uploaded file with metadata

    private Map<String, Object> createFileResult(String filePath, boolean hasDetection) {
        Map<String, Object> result = new HashMap<>();
        result.put("path", filePath);
        result.put("url", toWebPath(filePath));
        result.put("has_detection", hasDetection);
        result.put("upload_time", Instant.now().toString());
        return result;
    }
    // Enrich the file result with detection details if available
    private void enrichFileResultWithDetections(Map<String, Object> fileResult,
                                                List<Map<String, Object>> detections) {
        if (detections.isEmpty()) return;

        Map<String, Object> first = detections.get(0);

        if (first.containsKey("result_image_path")) {
            fileResult.put("detection_url", toWebPath((String) first.get("result_image_path")));
        }
        fileResult.put("detected_class", first.get("class_name"));
        fileResult.put("detection_confidence", first.get("confidence"));
    }
    // Extract cropped elements from detections and add to result
    private void extractCroppedElements(String fileType, List<Map<String, Object>> detections,
                                        FileProcessingResult result) {
        for (Map<String, Object> detection : detections) {
            String className = (String) detection.get("class_name");
            if (!detection.containsKey("crop_path")) continue;

            String cropPath = (String) detection.get("crop_path");

            if ("Person".equals(className) && "id_front".equals(fileType)) {
                result.addCroppedElement("person_from_id", cropPath);
            } else if ("Person".equals(className) && "photo".equals(fileType)) {
                result.addCroppedElement("person_from_photo", cropPath);
            } else if ("Signature".equals(className) && "id_front".equals(fileType)) {
                result.addCroppedElement("signature", cropPath);
            } else if ("Expiry_date".equals(className) && "id_back".equals(fileType)) {
                result.addCroppedElement("expiry_date", cropPath);
            }
        }
    }
    // Convert file system path to web-accessible URL path
    private String toWebPath(String filePath) {
        return "/" + filePath.replace("\\", "/").replace("static/", "");
    }
}

