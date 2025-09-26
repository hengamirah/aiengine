package com.example.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Registration {
    private Long id;
    private String firstName;
    private String lastName;
    private LocalDate dob;
    private LocalDateTime verificationTimestamp;
    private String verificationStatus;
    private LocalDateTime createdAt;
    
    // Constructors
    public Registration() {}
    
    public Registration(String firstName, String lastName, LocalDate dob, String verificationStatus) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dob = dob;
        this.verificationStatus = verificationStatus;
        this.createdAt = LocalDateTime.now();
        this.verificationTimestamp = LocalDateTime.now();
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    
    public LocalDateTime getVerificationTimestamp() { return verificationTimestamp; }
    public void setVerificationTimestamp(LocalDateTime verificationTimestamp) { 
        this.verificationTimestamp = verificationTimestamp; 
    }
    
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { 
        this.verificationStatus = verificationStatus; 
    }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}