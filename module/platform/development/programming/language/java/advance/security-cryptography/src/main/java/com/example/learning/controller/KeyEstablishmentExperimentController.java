package com.example.learning.controller;

import com.example.learning.service.SecurityCryptographyExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.GeneralSecurityException;
import java.util.Map;

@RestController
@RequestMapping("/java/advance/security-cryptography/key-establishment")
public class KeyEstablishmentExperimentController {

    private final SecurityCryptographyExperimentService experimentService;

    public KeyEstablishmentExperimentController(SecurityCryptographyExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/compare")
    public Map<String, Object> compare() throws GeneralSecurityException {
        return experimentService.compareKeyEstablishment();
    }
}
