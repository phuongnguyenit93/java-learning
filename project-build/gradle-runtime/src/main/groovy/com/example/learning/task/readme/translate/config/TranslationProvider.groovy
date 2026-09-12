package com.example.learning.task.readme.translate.config

enum TranslationProvider {
    AZURE,
    GOOGLE

    static TranslationProvider from(
            String value
    ) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    'Translation provider must not be empty'
            )
        }


        try {
            return valueOf(value.trim().toUpperCase())
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    """
Unsupported translation provider: ${value}

Supported providers:
${values()*.name()*.toLowerCase().join(', ')}
"""
            )
        }
    }
}