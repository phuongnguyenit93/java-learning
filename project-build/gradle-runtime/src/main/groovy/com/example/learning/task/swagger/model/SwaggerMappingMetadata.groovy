package com.example.learning.task.swagger.model

class SwaggerMappingMetadata {

    final List<String> httpMethods

    final List<String> mappingPaths

    final List<String> produces

    final List<String> consumes


    SwaggerMappingMetadata(
            Collection<String> httpMethods = [],
            Collection<String> mappingPaths = [],
            Collection<String> produces = [],
            Collection<String> consumes = []
    ) {

        this.httpMethods =
                httpMethods
                        .findAll { it != null }
                        .unique()

        this.mappingPaths =
                mappingPaths
                        .findAll { it != null }
                        .unique()

        this.produces =
                produces
                        .findAll { it != null }
                        .unique()

        this.consumes =
                consumes
                        .findAll { it != null }
                        .unique()
    }
}