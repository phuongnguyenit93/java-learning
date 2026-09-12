package com.example.learning.service;

public interface AzureTranslationInterface {
    String translate(
            String text,
            String sourceLanguage,
            String targetLanguage
    );
}
