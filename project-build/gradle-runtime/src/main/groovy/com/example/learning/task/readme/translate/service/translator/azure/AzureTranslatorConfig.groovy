package com.example.learning.task.readme.translate.service.translator.azure

class AzureTranslatorConfig {

    final String endpoint

    final String region

    final String key


    AzureTranslatorConfig(
            String endpoint,
            String region,
            String key
    ) {

        this.endpoint =
                endpoint

        this.region =
                region

        this.key =
                key
    }
}