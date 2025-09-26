package com.example.repository;

import java.sql.Date;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.model.NameRegistrationRequest;
import com.example.model.NameVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class NameVerificationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NameVerificationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveVerification(NameRegistrationRequest request, NameVerificationResult result) {
        String sql = "INSERT INTO name_verification " +
                "(firstname, lastname, dob, decision, reasons, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                request.getFirstname(),
                request.getLastname(),
                request.getDob() != null ? Date.valueOf(request.getDob()) : null,
                result.getDecision(),
                result.getReasons() != null ? String.join(",", result.getReasons()) : null,
                result.getNotes() != null ? toJson(result.getNotes()) : null
        );
    }

    private String toJson(Map<String, Object> notes) {
        try {
            return objectMapper.writeValueAsString(notes);
        } catch (Exception e) {
            return notes.toString();
        }
    }
}
