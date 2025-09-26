package com.example.config;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.PythonProcessService;
import com.opencsv.CSVWriter;

@Service
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    
    @Autowired
    private PythonProcessService pythonProcessService;
    
    private Connection connection;
    private AtomicLong acceptedCounter = new AtomicLong(0);
    
    // Initialize database connection using your sample pattern
    public void initConnection() throws Exception {
        try {
            Properties info = new Properties();
            info.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
            
            String urlStr = info.getProperty("db.URL");
            String portStr = info.getProperty("db.port");
            String schemaStr = info.getProperty("db.schema");
            String username = info.getProperty("db.USER");
            String password = info.getProperty("db.PASS");
            
            String dbURL = urlStr.concat(":").concat(portStr).concat("/").concat(schemaStr);
            
            connection = DriverManager.getConnection(dbURL, username, password);
            logger.info("✅ Database connection successful to: {}", dbURL);
            
        } catch (Exception e) {
            logger.error("❌ Database connection failed", e);
            throw e;
        }
    }
    
    public boolean isDatabaseAvailable() {
        try {
            if (connection == null || connection.isClosed()) {
                initConnection();
            }
            
            // Test connection like in your sample
            PreparedStatement testStmt = connection.prepareStatement("SELECT 1 FROM DUAL");
            ResultSet rs = testStmt.executeQuery();
            boolean available = rs.next();
            rs.close();
            testStmt.close();
            
            return available;
        } catch (Exception e) {
            logger.error("❌ Database test failed", e);
            return false;
        }
    }
    
    public void saveRegistration(String firstName, String lastName, String dob, String verificationStatus) {
        try {
            if (!isDatabaseAvailable()) {
                logger.warn("⚠️ Database not available");
                return;
            }
            
            String insertSQL = "INSERT INTO AIENGINE (ID, FIRST_NAME, LAST_NAME, DOB, VERIFICATION_TIMESTAMP, VERIFICATION_STATUS, CREATED_AT) " +
                              "VALUES (AIENGINE_SEQ.NEXTVAL, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement stmt = connection.prepareStatement(insertSQL);
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setDate(3, java.sql.Date.valueOf(LocalDate.parse(dob)));
            stmt.setTimestamp(4, Timestamp.valueOf(java.time.LocalDateTime.now()));
            stmt.setString(5, verificationStatus);
            stmt.setTimestamp(6, Timestamp.valueOf(java.time.LocalDateTime.now()));
            
            int result = stmt.executeUpdate();
            stmt.close();
            
            if (result > 0) {
                logger.info("✅ Registration saved: {} {}", firstName, lastName);
                
                // Check if ACCEPTED and trigger retrain
                if ("ACCEPTED".equals(verificationStatus)) {
                    handleAcceptedRegistration();
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to save registration", e);
        }
    }
    
    private void handleAcceptedRegistration() {
        long count = acceptedCounter.incrementAndGet();
        logger.info("📊 Accepted registrations counter: {}", count);
        
        if (count >= 10) {
            logger.info("🎯 Reached 10 accepted registrations - triggering model retrain");
            triggerModelRetrain();
            acceptedCounter.set(0); // Reset counter
        }
    }
    
    private void triggerModelRetrain() {
        try {
            String csvPath = exportRegistrationsToCSV();
            logger.info("📁 Exported registrations to: {}", csvPath);
            
            boolean success = pythonProcessService.retrainNameChecker(csvPath);
            
            if (success) {
                logger.info("✅ Model retrained successfully");
            } else {
                logger.error("❌ Model retraining failed");
            }
            
        } catch (Exception e) {
            logger.error("❌ Error during model retrain process", e);
        }
    }
    
    private String exportRegistrationsToCSV() throws Exception {
        File outputDir = new File("output");
    if (!outputDir.exists()) {
        logger.info("📁 Creating output directory: {}", outputDir.getAbsolutePath());
        boolean created = outputDir.mkdirs();
        if (!created) {
            logger.warn("⚠️ Could not create output directory");
        }
    }
    
    String csvPath = "output/registration_data.csv";
    
    String selectSQL = "SELECT FIRST_NAME, LAST_NAME, DOB FROM AIENGINE ORDER BY CREATED_AT DESC";
    PreparedStatement stmt = connection.prepareStatement(selectSQL);
    ResultSet rs = stmt.executeQuery();
    
    try (CSVWriter writer = new CSVWriter(new FileWriter(csvPath))) {
        // Write header
        String[] header = {"first_name", "last_name", "date_of_birth"};
        writer.writeNext(header);
        
        int count = 0;
        // Write data
        while (rs.next()) {
            String[] data = {
                rs.getString("FIRST_NAME"),
                rs.getString("LAST_NAME"),
                rs.getDate("DOB").toString()
            };
            writer.writeNext(data);
            count++;
        }
        
        logger.info("📊 Exported {} registrations to CSV at {}", count, new File(csvPath).getAbsolutePath());
    }
    
    rs.close();
    stmt.close();
    
    return csvPath;
    }
    
    public long countAcceptedRegistrations() {
        try {
            if (!isDatabaseAvailable()) {
                return 0;
            }
            
            String countSQL = "SELECT COUNT(*) FROM AIENGINE WHERE VERIFICATION_STATUS = 'ACCEPTED'";
            PreparedStatement stmt = connection.prepareStatement(countSQL);
            ResultSet rs = stmt.executeQuery();
            
            long count = 0;
            if (rs.next()) {
                count = rs.getLong(1);
            }
            
            rs.close();
            stmt.close();
            
            return count;
            
        } catch (Exception e) {
            logger.error("❌ Failed to count registrations", e);
            return 0;
        }
    }
    
    public List<Registration> getAllRegistrations() {
        List<Registration> registrations = new ArrayList<>();
        
        try {
            if (!isDatabaseAvailable()) {
                return registrations;
            }
            
            String selectSQL = "SELECT ID, FIRST_NAME, LAST_NAME, DOB, VERIFICATION_TIMESTAMP, VERIFICATION_STATUS, CREATED_AT " +
                              "FROM AIENGINE ORDER BY CREATED_AT DESC";
            
            PreparedStatement stmt = connection.prepareStatement(selectSQL);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Registration reg = new Registration();
                reg.setId(rs.getLong("ID"));
                reg.setFirstName(rs.getString("FIRST_NAME"));
                reg.setLastName(rs.getString("LAST_NAME"));
                reg.setDob(rs.getDate("DOB").toLocalDate());
                reg.setVerificationTimestamp(rs.getTimestamp("VERIFICATION_TIMESTAMP").toLocalDateTime());
                reg.setVerificationStatus(rs.getString("VERIFICATION_STATUS"));
                reg.setCreatedAt(rs.getTimestamp("CREATED_AT").toLocalDateTime());
                
                registrations.add(reg);
            }
            
            rs.close();
            stmt.close();
            
        } catch (Exception e) {
            logger.error("❌ Failed to get registrations", e);
        }
        
        return registrations;
    }
    
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("🔌 Database connection closed");
            }
        } catch (Exception e) {
            logger.error("❌ Error closing connection", e);
        }
    }
}