package com.example.learning.task.swagger.model

class SwaggerScanResult {

    final Map<
            String,
            Map<String, SwaggerApiMetadata>
            > apiByController

    final Set<String> parameterNames


    SwaggerScanResult(
            Map<String, Map<String, SwaggerApiMetadata>> apiByController,
            Collection<String> parameterNames
    ) {

        this.apiByController =
                apiByController

        this.parameterNames =
                new LinkedHashSet<>(
                        parameterNames
                )
    }


    int getApiCount() {

        return apiByController
                .values()
                .sum {
                    it.size()
                } ?: 0
    }
}