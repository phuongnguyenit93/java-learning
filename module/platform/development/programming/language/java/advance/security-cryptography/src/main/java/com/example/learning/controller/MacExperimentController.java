package com.example.learning.controller;

import com.example.learning.service.SecurityCryptographyExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.GeneralSecurityException;
import java.util.Map;

@RestController
@RequestMapping("/java/advance/security-cryptography/mac")
public class MacExperimentController {

    private final SecurityCryptographyExperimentService experimentService;

    public MacExperimentController(SecurityCryptographyExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/tamper")
    public Map<String, Object> tamper() throws GeneralSecurityException {
        return experimentService.macTamper();
    }
}
