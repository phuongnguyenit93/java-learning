package com.example.learning.task.readme.translate.service.translator.azure

import com.example.learning.task.readme.translate.service.translator.TranslationServiceInterface
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class AzureTranslationService implements TranslationServiceInterface {

    private static final int MAX_BATCH_ITEMS =
            25

    private static final int MAX_BATCH_CHARACTERS =
            5000


    private final AzureTranslatorConfig config

    private final AzureResponseValidator responseValidator

    private final HttpClient httpClient


    AzureTranslationService(
            AzureTranslatorConfig config
    ) {

        this.config = config

        this.httpClient =
                HttpClient.newHttpClient()

        this.responseValidator = new AzureResponseValidator()
    }

    AzureTranslationService(
            AzureTranslatorConfig config,
            AzureResponseValidator responseValidator
    ) {

        this.config = config

        this.httpClient =
                HttpClient.newHttpClient()

        this.responseValidator =
                responseValidator
    }


    List<String> translate(
            List<String> texts,
            String inputLanguage,
            String outputLanguage
    ) {

        if (texts.isEmpty()) {
            return []
        }


        List<List<String>> batches =
                createBatches(texts)


        List<String> results = []


        batches.eachWithIndex { batch, index ->

            println(
                    "Azure batch ${index + 1}/${batches.size()} " +
                            "- ${batch.size()} items"
            )


            results.addAll(
                    translateBatch(
                            batch,
                            inputLanguage,
                            outputLanguage
                    )
            )
        }


        return results
    }


    private List<String> translateBatch(
            List<String> texts,
            String inputLanguage,
            String outputLanguage
    ) {

        String body =
                JsonOutput.toJson(
                        texts.collect {
                            [
                                    Text: it
                            ]
                        }
                )


        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                buildUri(
                                        inputLanguage,
                                        outputLanguage
                                )
                        )
                        .header(
                                'Content-Type',
                                'application/json; charset=UTF-8'
                        )
                        .header(
                                'Ocp-Apim-Subscription-Key',
                                config.key
                        )
                        .header(
                                'Ocp-Apim-Subscription-Region',
                                config.region
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                body,
                                                StandardCharsets.UTF_8
                                        )
                        )
                        .build()


        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers
                                .ofString(
                                        StandardCharsets.UTF_8
                                )
                )

        responseValidator.validate(
                response
        )

        def json =
                new JsonSlurper()
                        .parseText(
                                response.body()
                        )


        if (json.size() != texts.size()) {

            throw new IllegalStateException(
                    """
Azure Translator response count mismatch.

Request:
${texts.size()}

Response:
${json.size()}
"""
            )
        }


        return json.collect {

            it.translations[0].text
        }
    }


    private URI buildUri(
            String inputLanguage,
            String outputLanguage
    ) {

        String from =
                URLEncoder.encode(
                        inputLanguage,
                        StandardCharsets.UTF_8
                )


        String to =
                URLEncoder.encode(
                        outputLanguage,
                        StandardCharsets.UTF_8
                )


        String endpoint =
                config.endpoint
                        .replaceAll(
                                '/+$',
                                ''
                        )


        return URI.create(
                "${endpoint}/translate" +
                        '?api-version=3.0' +
                        "&from=${from}" +
                        "&to=${to}"
        )
    }


    private List<List<String>> createBatches(
            List<String> texts
    ) {

        List<List<String>> batches = []

        List<String> currentBatch = []

        int currentCharacters = 0


        texts.each { text ->

            if (
                    text.length()
                            > MAX_BATCH_CHARACTERS
            ) {

                throw new IllegalArgumentException(
                        """
Translation text exceeds Azure batch limit.

Characters:
${text.length()}

Text:
${text}
"""
                )
            }


            boolean itemLimitReached =
                    currentBatch.size()
                            >= MAX_BATCH_ITEMS


            boolean characterLimitReached =
                    currentCharacters
            + text.length()
                    > MAX_BATCH_CHARACTERS


            if (
                    !currentBatch.isEmpty() &&
                            (
                                    itemLimitReached ||
                                            characterLimitReached
                            )
            ) {

                batches.add(
                        currentBatch
                )


                currentBatch = []

                currentCharacters = 0
            }


            currentBatch.add(
                    text
            )

            currentCharacters +=
                    text.length()
        }


        if (!currentBatch.isEmpty()) {

            batches.add(
                    currentBatch
            )
        }


        return batches
    }
}