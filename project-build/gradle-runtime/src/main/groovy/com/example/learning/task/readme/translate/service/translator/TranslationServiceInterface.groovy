package com.example.learning.task.readme.translate.service.translator

interface TranslationServiceInterface {
    List<String> translate(
            List<String> texts,
            String inputLanguage,
            String outputLanguage
    )
}