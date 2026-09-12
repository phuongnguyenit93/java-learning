package com.example.learning.task.readme.translate.service.translator.azure

import com.example.learning.secret.CompositeSecretProvider
import com.example.learning.secret.EnvironmentSecretProvider
import com.example.learning.secret.SecretProvider
import com.example.learning.secret.WindowsDpapiSecretProvider

class AzureTranslatorConfigFactory {

    private static final String DEFAULT_ENDPOINT =
            'https://api.cognitive.microsofttranslator.com'

    private static final String DEFAULT_REGION =
            'southeastasia'

    private static final String AZURE_TRANSLATOR_KEY =
            'AZURE_TRANSLATOR_KEY'


    private final SecretProvider secretProvider


    AzureTranslatorConfigFactory() {

        this(
                new CompositeSecretProvider(
                        [
                                new EnvironmentSecretProvider(),
                                new WindowsDpapiSecretProvider()
                        ]
                )
        )
    }


    AzureTranslatorConfigFactory(
            SecretProvider secretProvider
    ) {

        this.secretProvider =
                secretProvider
    }


    AzureTranslatorConfig create() {

        String key =
                secretProvider.findSecret(
                        AZURE_TRANSLATOR_KEY
                )


        if (
                key == null ||
                        key.isBlank()
        ) {

            throw new IllegalStateException(
                    """
Azure Translator secret is not configured.

Required secret:

AZURE_TRANSLATOR_KEY

Windows local setup:

    .\\scripts\\secrets\\set-secret.ps1

This setup is required only once per Windows user/machine.

CI / Docker:

Inject AZURE_TRANSLATOR_KEY into the Gradle process environment.

Documentation:

    secrets/README.md
"""
            )
        }


        return new AzureTranslatorConfig(
                DEFAULT_ENDPOINT,
                DEFAULT_REGION,
                key
        )
    }
}