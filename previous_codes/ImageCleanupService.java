/**  
Background Service for maintenance
Runs every 10 seconds to clean up old images
Cleans images older than 5 minutes from:
uploads/
detections/
facematch/
crops/
 */

package com.example.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


//Add this at the end of App.java file

@Service
@Slf4j
public class ImageCleanupService {
 private static final long MAX_AGE_MILLIS = 300 * 1000; // 5 minute
 
 @Value("${cleanup.enabled:true}")
 private boolean cleanupEnabled;
 
 @Scheduled(fixedDelay = 10000) // Run every 30 seconds
 public void cleanupOldImages() {
     if (!cleanupEnabled) {
         return;
     }
     
     log.debug("🧹 Starting cleanup of old images...");
     
     try {
         long now = System.currentTimeMillis();
         AtomicInteger deletedCount = new AtomicInteger(0);
         
         // Clean up all static folders
         String[] folders = {
             "static/uploads",
             "static/detections", 
             "static/facematch",
             "static/signaturecompare",
             "static/crops/Person",
             "static/crops/Signature",
             "static/crops/Expiry_date",
             "static/crops/ID_card"
         };
         
         for (String folder : folders) {
             Path folderPath = Paths.get(folder);
             if (Files.exists(folderPath)) {
                 Files.walk(folderPath)
                     .filter(Files::isRegularFile)
                     .filter(path -> {
                         try {
                             long lastModified = Files.getLastModifiedTime(path).toMillis();
                             return (now - lastModified) > MAX_AGE_MILLIS;
                         } catch (IOException e) {
                             return false;
                         }
                     })
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                             deletedCount.incrementAndGet();
                             log.debug("Deleted: {}", path);
                         } catch (IOException e) {
                        	 log.error("Failed to delete: {}", path, e);
                         }
                     });
             }
         }
         
         if (deletedCount.get() > 0) {
        	 log.info("🧹 Cleanup completed: deleted {} old files", deletedCount.get());
         }
         
     } catch (Exception e) {
    	 log.error("❌ Error during cleanup", e);
     }
 }
}