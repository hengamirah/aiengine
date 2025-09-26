/*REST Controller exposing /verify endpoint
    Orchestrates the verification flow:
    1. Receives user data and files (ID, photo, signature)
    2. Uses VerificationService to process uploads
    3. Calls PythonProcessService for name verification and face comparison
    4. Uses DatabaseService to save results
-Acts as the entry point for verification requests
*/

package com.example.controller;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.PythonProcessService;
import com.example.config.DatabaseService;
import com.example.service.VerificationService;
import com.example.util.ComparisonResults;
import com.example.util.FileProcessingResult;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@AllArgsConstructor
@Slf4j
public class VerificationController {

    private final PythonProcessService pythonService;
    private final DatabaseService databaseService;
    private final VerificationService verificationService;

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestParam String first_name,
            @RequestParam String last_name,
            @RequestParam String dob,
            @RequestParam(required = false) MultipartFile id_front,
            @RequestParam(required = false) MultipartFile id_back,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) MultipartFile signature) {

        Map<String, Object> response = new HashMap<>();
        try {
            // 1. Process files
            FileProcessingResult fileResults =
                    verificationService.processUploadedFiles(id_front, id_back, photo, signature);

            // 2. Verify identity
            Map<String, Object> verificationResult =
                    pythonService.verifyName(first_name, last_name, dob);

            // 3. Compare faces
            ComparisonResults comparisons = performAutoComparisons(fileResults);

            // 4. Build response
            response.put("status", "success");
            response.put("files", fileResults.getUploadResults()); // ✅ return uploaded files
            response.put("verification", verificationResult);
            response.put("id_detections", fileResults.getDetections());
            response.put("cropped_elements", fileResults.getCroppedElements());
            response.put("face_comparison", comparisons.getFaceComparison());
            response.put("faces_match", comparisons.isFacesMatch());

            // 5. Save DB entry
            String status = extractVerificationStatus(verificationResult);
            databaseService.saveRegistration(first_name, last_name, dob, status);
            response.put("registration_saved", true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("💥 Verification failed", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private ComparisonResults performAutoComparisons(FileProcessingResult fileResults) throws Exception {
        ComparisonResults results = new ComparisonResults();

        String idPersonPath = fileResults.getCroppedElement("person_from_id");
        String uploadedPhotoPath = fileResults.getCroppedElement("person_from_photo");

        if (idPersonPath != null && uploadedPhotoPath != null &&
            Files.exists(Paths.get(idPersonPath)) && Files.exists(Paths.get(uploadedPhotoPath))) {

            Map<String, Object> faceComparison = pythonService.compareFaces(idPersonPath, uploadedPhotoPath, 0.8);
            results.setFaceComparison(faceComparison);

            boolean facesMatch = Boolean.TRUE.equals(faceComparison.get("is_same_person")) ||
                                 Boolean.TRUE.equals(faceComparison.get("verified"));
            results.setFacesMatch(facesMatch);
        }
        return results;
    }

    private String extractVerificationStatus(Map<String, Object> verificationResult) {
        if (verificationResult == null) return "ERROR";
        if (verificationResult.containsKey("final_decision")) {
            Map<String, Object> finalDecision = (Map<String, Object>) verificationResult.get("final_decision");
            if (finalDecision != null && finalDecision.containsKey("decision")) {
                switch ((String) finalDecision.get("decision")) {
                    case "ACCEPT": return "ACCEPTED";
                    case "REJECT": return "REJECTED";
                    case "REVIEW": return "REVIEW";
                }
            }
        }
        return "UNKNOWN";
    }
}
