package com.example.learning.task.swagger.service

import com.example.learning.task.swagger.model.SwaggerApiMetadata
import com.example.learning.task.swagger.model.SwaggerMappingMetadata
import com.example.learning.task.swagger.model.SwaggerScanResult
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import groovy.io.FileType

class SwaggerJavaSourceScanner {

    private static final Set<String> CONTROLLER_ANNOTATIONS = [
            'Controller',
            'RestController'
    ] as Set


    private static final Set<String> MAPPING_ANNOTATIONS = [
            'GetMapping',
            'PostMapping',
            'PutMapping',
            'DeleteMapping',
            'PatchMapping',
            'RequestMapping'
    ] as Set


    private final SpringMappingMetadataParser mappingParser


    SwaggerJavaSourceScanner() {

        this(
                new SpringMappingMetadataParser()
        )
    }


    SwaggerJavaSourceScanner(
            SpringMappingMetadataParser mappingParser
    ) {

        this.mappingParser =
                mappingParser
    }


    SwaggerScanResult scan(
            File sourceDirectory
    ) {

        Map<
                String,
                Map<String, SwaggerApiMetadata>
                > apiByController =
                new LinkedHashMap<>()


        Set<String> parameterNames =
                new LinkedHashSet<>()


        List<File> javaFiles = []


        sourceDirectory.eachFileRecurse(
                FileType.FILES
        ) { File file ->

            if (
                    file.name.endsWith(
                            '.java'
                    )
            ) {

                javaFiles.add(
                        file
                )
            }
        }


        javaFiles.sort {
            File file ->

                sourceDirectory
                        .toPath()
                        .relativize(
                                file.toPath()
                        )
                        .toString()
        }


        javaFiles.each {
            File javaFile ->

                scanJavaFile(
                        sourceDirectory,
                        javaFile,
                        apiByController,
                        parameterNames
                )
        }


        return new SwaggerScanResult(
                apiByController,
                parameterNames
        )
    }


    private void scanJavaFile(
            File sourceDirectory,
            File javaFile,
            Map<String, Map<String, SwaggerApiMetadata>> apiByController,
            Set<String> parameterNames
    ) {

        def compilationUnit =
                StaticJavaParser.parse(
                        javaFile
                )


        compilationUnit
                .findAll(
                        ClassOrInterfaceDeclaration.class
                )
                .each {
                    ClassOrInterfaceDeclaration controller ->

                        if (!isController(controller)) {

                            return
                        }


                        scanController(
                                sourceDirectory,
                                javaFile,
                                controller,
                                apiByController,
                                parameterNames
                        )
                }
    }


    private void scanController(
            File sourceDirectory,
            File javaFile,
            ClassOrInterfaceDeclaration controller,
            Map<String, Map<String, SwaggerApiMetadata>> apiByController,
            Set<String> parameterNames
    ) {

        String controllerName =
                controller.nameAsString


        Map<String, SwaggerApiMetadata> methods =
                apiByController.computeIfAbsent(
                        controllerName
                ) {
                    new LinkedHashMap<>()
                }


        AnnotationExpr controllerMappingAnnotation =
                findAnnotation(
                        controller.annotations,
                        [
                                'RequestMapping'
                        ] as Set
                )


        SwaggerMappingMetadata controllerMapping =
                mappingParser.parse(
                        controllerMappingAnnotation
                )


        String relativePath =
                sourceDirectory
                        .toPath()
                        .relativize(
                                javaFile.toPath()
                        )
                        .toString()
                        .replace(
                                '\\',
                                '/'
                        )


        controller.methods.each {
            MethodDeclaration method ->

                AnnotationExpr mappingAnnotation =
                        findAnnotation(
                                method.annotations,
                                MAPPING_ANNOTATIONS
                        )


                if (mappingAnnotation == null) {

                    return
                }


                SwaggerMappingMetadata methodMapping =
                        mappingParser.parse(
                                mappingAnnotation
                        )


                SwaggerMappingMetadata effectiveMapping =
                        mappingParser.combine(
                                controllerMapping,
                                methodMapping
                        )


                List<String> params =
                        method.parameters.collect {
                            it.nameAsString
                        }


                parameterNames.addAll(
                        params
                )


                String methodSignature =
                        buildMethodSignature(
                                method
                        )


                methods[
                        methodSignature
                ] =
                        new SwaggerApiMetadata(
                                controllerName,
                                methodSignature,
                                method.nameAsString,
                                params,
                                relativePath,
                                mappingAnnotation.toString(),
                                effectiveMapping.httpMethods,
                                effectiveMapping.mappingPaths,
                                effectiveMapping.produces,
                                effectiveMapping.consumes
                        )
        }
    }


    private static String buildMethodSignature(
            MethodDeclaration method
    ) {

        List<String> parameterTypes =
                method.parameters.collect {
                    parameter ->

                        String type =
                                parameter.type
                                        .toString()
                                        .replaceAll(
                                                /\s+/,
                                                ''
                                        )


                        if (parameter.varArgs) {

                            type +=
                                    '...'
                        }


                        return type
                }


        return method.nameAsString +
                '(' +
                parameterTypes.join(',') +
                ')'
    }


    private static boolean isController(
            ClassOrInterfaceDeclaration declaration
    ) {

        return declaration.annotations.any {
            AnnotationExpr annotation ->

                CONTROLLER_ANNOTATIONS.contains(
                        simpleName(
                                annotation
                        )
                )
        }
    }


    private static AnnotationExpr findAnnotation(
            Collection<AnnotationExpr> annotations,
            Set<String> acceptedNames
    ) {

        return annotations.find {
            AnnotationExpr annotation ->

                acceptedNames.contains(
                        simpleName(
                                annotation
                        )
                )
        }
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