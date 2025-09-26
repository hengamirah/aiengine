package com.example.service;

import com.example.model.NameRegistrationRequest;
import com.example.model.NameVerificationResult;
import com.example.repository.NameVerificationRepository;
import org.springframework.stereotype.Service;

@Service
public class NameVerificationService {

    private final PythonNameProcessService pythonNameProcessService;
    private final NameVerificationRepository repository;

    public NameVerificationService(PythonNameProcessService pythonNameProcessService,
                                   NameVerificationRepository repository) {
        this.pythonNameProcessService = pythonNameProcessService;
        this.repository = repository;
    }

    public NameVerificationResult verifyAndSave(NameRegistrationRequest request) {
        // Step 1: Verify name using Python script
        NameVerificationResult result = pythonNameProcessService.verifyName(
                request.getFirstname(), request.getLastname());

        // Step 2: Save registration + verification result to Oracle DB
        repository.saveVerification(request, result);

        // Step 3: Return the result to be sent back as API response
        return result;
    }
}
