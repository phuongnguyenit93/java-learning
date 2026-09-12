package com.example.learning.setup.settings.preset.model

class ModuleSetupInfo {

    File directory
    Properties gradleProperties
    String relativePath

    /*
     * Giữ behavior cũ:
     *
     * services/order-service
     *      ↓
     * services:order-service
     *
     * Không chứa ":" ở đầu.
     */
    String modulePath
}