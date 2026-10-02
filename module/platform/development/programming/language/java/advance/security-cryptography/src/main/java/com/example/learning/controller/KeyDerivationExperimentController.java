package com.example.learning.controller;

import com.example.learning.service.SecurityCryptographyExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.GeneralSecurityException;
import java.util.Map;

@RestController
@RequestMapping("/java/advance/security-cryptography/key-derivation")
public class KeyDerivationExperimentController {

    private final SecurityCryptographyExperimentService experimentService;

    public KeyDerivationExperimentController(SecurityCryptographyExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping("/salt-effect")
    public Map<String, Object> saltEffect() throws GeneralSecurityException {
        return experimentService.passwordDerivationSaltEffect();
    }
}
