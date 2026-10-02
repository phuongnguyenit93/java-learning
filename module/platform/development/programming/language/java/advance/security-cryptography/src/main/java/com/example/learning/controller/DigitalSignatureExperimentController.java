package com.example.learning.controller;

import com.example.learning.service.SecurityCryptographyExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.GeneralSecurityException;
import java.util.Map;

@RestController
@RequestMapping("/java/advance/security-cryptography/signature")
public class DigitalSignatureExperimentController {

    private final SecurityCryptographyExperimentService experimentService;

    public DigitalSignatureExperimentController(SecurityCryptographyExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/tamper")
    public Map<String, Object> tamper() throws GeneralSecurityException {
        return experimentService.signatureTamper();
    }
}
