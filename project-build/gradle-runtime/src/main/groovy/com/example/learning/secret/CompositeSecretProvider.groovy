package com.example.learning.secret

class CompositeSecretProvider
        implements SecretProvider {

    private final List<SecretProvider> providers


    CompositeSecretProvider(
            Collection<SecretProvider> providers
    ) {

        this.providers =
                providers.toList()
    }


    @Override
    String findSecret(
            String name
    ) {

        for (
                SecretProvider provider :
                        providers
        ) {

            String value =
                    provider.findSecret(
                            name
                    )


            if (
                    value != null &&
                            !value.isBlank()
            ) {

                return value
            }
        }


        return null
    }
}