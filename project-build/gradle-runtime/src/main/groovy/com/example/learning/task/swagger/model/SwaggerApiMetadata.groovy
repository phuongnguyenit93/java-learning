package com.example.learning.task.swagger.model

class SwaggerApiMetadata {

    final String controllerName

    final String methodSignature

    final String methodName

    final List<String> params

    final String path

    final String mapping

    final List<String> httpMethods

    final List<String> mappingPaths

    final List<String> produces

    final List<String> consumes


    SwaggerApiMetadata(
            String controllerName,
            String methodSignature,
            String methodName,
            Collection<String> params,
            String path,
            String mapping,
            Collection<String> httpMethods,
            Collection<String> mappingPaths,
            Collection<String> produces,
            Collection<String> consumes
    ) {

        this.controllerName =
                controllerName

        this.methodSignature =
                methodSignature

        this.methodName =
                methodName

        this.params =
                params.toList()

        this.path =
                path

        this.mapping =
                mapping

        this.httpMethods =
                httpMethods.toList()

        this.mappingPaths =
                mappingPaths.toList()

        this.produces =
                produces.toList()

        this.consumes =
                consumes.toList()
    }
}