
/**
 This class manages results from file processing and object detection:
 Central container for all file processing results
    Tracks:
    Upload results (success/failure, paths)
    Object detections (ID card, person, etc.)
    Cropped elements (faces, signatures, etc.)
    Used by VerificationService to collect results and pass to controller

 */
package com.example.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

//Add these helper classes at the end of App.java file (outside of VerificationController)

@Data
public class FileProcessingResult {
 private final Map<String, Object> uploadResults = new HashMap<>();
 private final Map<String, List<Map<String, Object>>> detections = new HashMap<>();
 private final Map<String, String> croppedElements = new HashMap<>();
 
 public void addUploadResult(String fileType, Map<String, Object> result) {
     uploadResults.put(fileType, result);
 }
 
 public void addDetections(String fileType, List<Map<String, Object>> detectionList) {
     detections.put(fileType, detectionList);
 }
 
 public void addCroppedElement(String elementType, String path) {
     croppedElements.put(elementType, path);
 }
  
 public String getCroppedElement(String elementType) {
     return croppedElements.get(elementType);
 }
 
 public boolean hasCroppedElement(String elementType) {
     return croppedElements.containsKey(elementType);
 }

}