package com.example.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class NameVerificationResult {
    @JsonProperty("decision")
    private String decision;   // "ACCEPT", "REJECT", "REVIEW", "ERROR"
    
    @JsonProperty("reasons")
    private List<String> reasons;
    
    @JsonProperty("notes")
    private Map<String, Object> notes;
}