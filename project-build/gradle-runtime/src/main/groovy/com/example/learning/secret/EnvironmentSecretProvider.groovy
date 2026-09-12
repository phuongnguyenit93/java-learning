package com.example.learning.secret

class EnvironmentSecretProvider
        implements SecretProvider {

    @Override
    String findSecret(
            String name
    ) {

        String value =
                System.getenv(
                        name
                )


        if (
                value == null ||
                        value.isBlank()
        ) {

            return null
        }


        return value.trim()
    }
}