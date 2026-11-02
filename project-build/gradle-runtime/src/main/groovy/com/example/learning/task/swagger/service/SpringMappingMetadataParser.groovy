package com.example.learning.task.swagger.service

import com.example.learning.task.swagger.model.SwaggerMappingMetadata
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ArrayInitializerExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import com.github.javaparser.ast.expr.StringLiteralExpr

class SpringMappingMetadataParser {

    private static final Map<String, String> DIRECT_HTTP_METHODS = [

            GetMapping   : 'GET',
            PostMapping  : 'POST',
            PutMapping   : 'PUT',
            DeleteMapping: 'DELETE',
            PatchMapping : 'PATCH'

    ]


    SwaggerMappingMetadata parse(
            AnnotationExpr annotation
    ) {

        if (annotation == null) {

            return new SwaggerMappingMetadata()
        }


        String annotationName =
                simpleName(
                        annotation
                )


        List<String> paths =
                extractPaths(
                        annotation
                )


        List<String> produces =
                extractAttributeValues(
                        annotation,
                        'produces'
                )


        List<String> consumes =
                extractAttributeValues(
                        annotation,
                        'consumes'
                )


        List<String> httpMethods = []


        if (
                DIRECT_HTTP_METHODS
                        .containsKey(
                                annotationName
                        )
        ) {

            httpMethods.add(
                    DIRECT_HTTP_METHODS[
                            annotationName
                    ]
            )
        }
        else if (
                annotationName ==
                        'RequestMapping'
        ) {

            httpMethods.addAll(
                    extractRequestMethods(
                            annotation
                    )
            )
        }


        return new SwaggerMappingMetadata(
                httpMethods,
                paths,
                produces,
                consumes
        )
    }


    SwaggerMappingMetadata combine(
            SwaggerMappingMetadata controllerMapping,
            SwaggerMappingMetadata methodMapping
    ) {

        List<String> effectiveMethods =
                !methodMapping.httpMethods.isEmpty()
                        ? methodMapping.httpMethods
                        : controllerMapping.httpMethods


        List<String> effectivePaths =
                combinePaths(
                        controllerMapping.mappingPaths,
                        methodMapping.mappingPaths
                )


        List<String> effectiveProduces =
                !methodMapping.produces.isEmpty()
                        ? methodMapping.produces
                        : controllerMapping.produces


        List<String> effectiveConsumes =
                !methodMapping.consumes.isEmpty()
                        ? methodMapping.consumes
                        : controllerMapping.consumes


        return new SwaggerMappingMetadata(
                effectiveMethods,
                effectivePaths,
                effectiveProduces,
                effectiveConsumes
        )
    }


    private static List<String> extractPaths(
            AnnotationExpr annotation
    ) {

        List<String> paths = []


        if (
                annotation instanceof
                        SingleMemberAnnotationExpr
        ) {

            paths.addAll(
                    extractValues(
                            annotation.memberValue
                    )
            )


            return paths.unique()
        }


        paths.addAll(
                extractAttributeValues(
                        annotation,
                        'value'
                )
        )


        paths.addAll(
                extractAttributeValues(
                        annotation,
                        'path'
                )
        )


        return paths.unique()
    }


    private static List<String> extractRequestMethods(
            AnnotationExpr annotation
    ) {

        List<String> rawMethods =
                extractAttributeValues(
                        annotation,
                        'method'
                )


        return rawMethods.collect {
            String value ->

                value
                        .tokenize('.')
                        .last()
                        .trim()
        }
                .unique()
    }


    private static List<String> extractAttributeValues(
            AnnotationExpr annotation,
            String attributeName
    ) {

        if (
                !(annotation instanceof
                        NormalAnnotationExpr)
        ) {

            return []
        }


        def pair =
                annotation.pairs.find {
                    it.nameAsString ==
                            attributeName
                }


        if (pair == null) {

            return []
        }


        return extractValues(
                pair.value
        )
    }


    private static List<String> extractValues(
            Expression expression
    ) {

        if (
                expression instanceof
                        ArrayInitializerExpr
        ) {

            return expression.values
                    .collectMany {
                        extractValues(
                                it
                        )
                    }
        }


        if (
                expression instanceof
                        StringLiteralExpr
        ) {

            return [
                    expression.asString()
            ]
        }


        /*
         * Ví dụ:
         *
         * MediaType.APPLICATION_JSON_VALUE
         * RequestMethod.GET
         * Routes.USER_PATH
         *
         * Nếu chưa dùng Java Symbol Solver,
         * giữ nguyên expression để không mất metadata.
         */
        return [
                expression.toString()
        ]
    }


    private static List<String> combinePaths(
            Collection<String> controllerPaths,
            Collection<String> methodPaths
    ) {

        if (
                controllerPaths.isEmpty() &&
                        methodPaths.isEmpty()
        ) {

            return []
        }


        if (controllerPaths.isEmpty()) {

            return methodPaths.toList()
        }


        if (methodPaths.isEmpty()) {

            return controllerPaths.toList()
        }


        List<String> result = []


        controllerPaths.each {
            String controllerPath ->

                methodPaths.each {
                    String methodPath ->

                        result.add(
                                joinPath(
                                        controllerPath,
                                        methodPath
                                )
                        )
                }
        }


        return result.unique()
    }


    private static String joinPath(
            String left,
            String right
    ) {

        if (left == null || left.isBlank()) {
            return right
        }


        if (right == null || right.isBlank()) {
            return left
        }


        String normalizedLeft =
                left.endsWith('/')
                        ? left.substring(
                        0,
                        left.length() - 1
                )
                        : left


        String normalizedRight =
                right.startsWith('/')
                        ? right
                        : "/${right}"


        return normalizedLeft +
                normalizedRight
    }


    private static String simpleName(
            AnnotationExpr annotation
    ) {

        return annotation
                .nameAsString
                .tokenize('.')
                .last()
    }
}