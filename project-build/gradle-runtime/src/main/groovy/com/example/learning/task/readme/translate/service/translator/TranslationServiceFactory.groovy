package com.example.learning.task.readme.translate.service.translator

import com.example.learning.task.readme.translate.config.TranslationProvider
import com.example.learning.task.readme.translate.service.translator.azure.AzureTranslationService
import com.example.learning.task.readme.translate.service.translator.azure.AzureTranslatorConfig
import com.example.learning.task.readme.translate.service.translator.azure.AzureTranslatorConfigFactory

class TranslationServiceFactory {

    static TranslationServiceInterface create(
            TranslationProvider provider) {

        switch (provider) {

            case TranslationProvider.AZURE:

                return createAzureService()


            case TranslationProvider.GOOGLE:

                return createGoogleService()


            default:

                throw new IllegalArgumentException(
                        "Unsupported translation provider: ${provider}"
                )
        }
    }


    private static TranslationServiceInterface createAzureService() {
        AzureTranslatorConfig config = new AzureTranslatorConfigFactory().create()
        return new AzureTranslationService(config)
    }


    private static TranslationServiceInterface createGoogleService() {
        return null
    }
}