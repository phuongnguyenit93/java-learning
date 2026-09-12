package com.example.learning.task.readme.translate.service.translator.azure

import java.net.http.HttpResponse

class AzureResponseValidator {

    void validate(
            HttpResponse<String> response
    ) {

        int statusCode =
                response.statusCode()


        if (
                statusCode >= 200 &&
                        statusCode < 300
        ) {

            return
        }


        String responseBody =
                response.body() ?: ''


        String requestId =
                findRequestId(
                        response
                )


        switch (statusCode) {

            case 400:

                throw createBadRequestException(
                        responseBody,
                        requestId
                )


            case 401:

                throw createUnauthorizedException(
                        responseBody,
                        requestId
                )


            case 403:

                throw createForbiddenException(
                        responseBody,
                        requestId
                )


            case 429:

                throw createTooManyRequestsException(
                        response,
                        responseBody,
                        requestId
                )


            case 500:

                throw createInternalServerErrorException(
                        responseBody,
                        requestId
                )


            case 503:

                throw createServiceUnavailableException(
                        responseBody,
                        requestId
                )


            default:

                throw createUnexpectedException(
                        statusCode,
                        responseBody,
                        requestId
                )
        }
    }


    private static IllegalStateException createBadRequestException(
            String responseBody,
            String requestId
    ) {

        return new IllegalStateException(
                """
Azure Translator rejected the request.

HTTP Status:
400 Bad Request

Possible causes:

- Invalid request body
- Invalid language
- Missing required parameter
- Unsupported translation option

Azure response:

${responseBody}

Request ID:
${requestId}
"""
        )
    }


    private static IllegalStateException createUnauthorizedException(
            String responseBody,
            String requestId
    ) {

        return new IllegalStateException(
                """
Azure Translator authentication failed.

HTTP Status:
401 Unauthorized

The configured API key may be:

- invalid
- expired
- revoked
- regenerated
- associated with another Azure resource

Update the local secret with:

    .\\scripts\\secrets\\set-secret.ps1

Secret name:

    AZURE_TRANSLATOR_KEY

Then run the Gradle task again.

Azure response:

${responseBody}

Request ID:
${requestId}
"""
        )
    }


    private static IllegalStateException createForbiddenException(
            String responseBody,
            String requestId
    ) {

        return new IllegalStateException(
                """
Azure Translator rejected the request.

HTTP Status:
403 Forbidden

The API key is not necessarily invalid.

Possible causes:

- Azure subscription is disabled
- Translator resource does not allow this operation
- Free/trial quota has been exhausted
- Resource or region configuration is incorrect

Check your Azure Translator resource and subscription.

Azure response:

${responseBody}

Request ID:
${requestId}
"""
        )
    }


    private static IllegalStateException createTooManyRequestsException(
            HttpResponse<String> response,
            String responseBody,
            String requestId
    ) {

        String retryAfter =
                response.headers()
                        .firstValue(
                                'Retry-After'
                        )
                        .orElse(
                                'N/A'
                        )


        return new IllegalStateException(
                """
Azure Translator request limit exceeded.

HTTP Status:
429 Too Many Requests

Possible causes:

- Too many requests were sent
- Translation quota was exceeded
- Subscription rate limit was exceeded

Retry-After:

${retryAfter}

The API key normally does NOT need to be replaced.

Azure response:

${responseBody}

Request ID:
${requestId}
"""
        )
    }


    private static IllegalStateException createInternalServerErrorException(
            String responseBody,
            String requestId
    ) {

        return new IllegalStateException(
                """
Azure Translator encountered an internal server error.

HTTP Status:
500 Internal Server Error

This is normally an Azure service-side problem.

Azure response:

${responseBody}

Request ID:
${requestId}
"""
        )
    }


    private static IllegalStateException createServiceUnavailableException(
            String responseBody,
            String requestId
    ) {

        return new IllegalStateException(
                """
Azure Translator is temporarily unavailable.

HTTP Status:
503 Service Unavailable

Try the request again later.

Azure response:

${responseBody}

Request ID:
${requestId}
"""
        )
    }


    private static IllegalStateException createUnexpectedException(
            int statusCode,
            String responseBody,
            String requestId
    ) {

        return new IllegalStateException(
                """
Azure Translator request failed.

HTTP Status:
${statusCode}

Azure response:

${responseBody}

Request ID:
${requestId}
"""
        )
    }


    private static String findRequestId(
            HttpResponse<String> response
    ) {

        return response.headers()
                .firstValue(
                        'X-RequestId'
                )
                .orElse(
                        'N/A'
                )
    }
}