package com.example.learning.controller;

import com.example.learning.service.SecurityCryptographyExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.GeneralSecurityException;
import java.util.Map;

@RestController
@RequestMapping("/java/advance/security-cryptography/aead")
public class AeadExperimentController {

    private final SecurityCryptographyExperimentService experimentService;

    public AeadExperimentController(SecurityCryptographyExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/tamper-detection")
    public Map<String, Object> tamperDetection() throws GeneralSecurityException {
        return experimentService.aeadTamperDetection();
    }
}
