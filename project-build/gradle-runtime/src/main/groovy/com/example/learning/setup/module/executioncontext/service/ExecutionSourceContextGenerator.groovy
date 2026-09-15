package com.example.learning.setup.module.executioncontext.service

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import groovy.io.FileType
import groovy.json.JsonOutput


class ExecutionSourceContextGenerator {

    private static final ParserConfiguration.LanguageLevel SOURCE_LANGUAGE_LEVEL =
            ParserConfiguration.LanguageLevel.JAVA_21


    private static final Set<String> CONTROLLER_ANNOTATIONS =
            [
                    'Controller',
                    'RestController'
            ] as Set


    private static final Set<String> MAPPING_ANNOTATIONS =
            [
                    'GetMapping',
                    'PostMapping',
                    'PutMapping',
                    'DeleteMapping',
                    'PatchMapping',
                    'RequestMapping'
            ] as Set


    private final JavaParser javaParser =
            new JavaParser(
                    new ParserConfiguration()
                            .setLanguageLevel(
                                    SOURCE_LANGUAGE_LEVEL
                            )
            )


    Map generate(
            File sourceDirectory,
            File outputFile
    ) {

        List<Map> parsedClasses =
                parseClasses(
                        sourceDirectory
                )


        Map<String, Map> classByFqcn =
                parsedClasses.collectEntries {
                    Map info ->

                        [(info.fqcn): info]
                }


        Map<String, Map> methods =
                new TreeMap<>()


        parsedClasses
                .findAll {
                    Map classInfo ->

                        isController(
                                classInfo.declaration as ClassOrInterfaceDeclaration
                        )
                }
                .sort {
                    Map left,
                    Map right ->

                        left.fqcn <=>
                                right.fqcn
                }
                .each {
                    Map controllerInfo ->

                        collectControllerMethods(
                                sourceDirectory,
                                controllerInfo,
                                classByFqcn,
                                methods
                        )
                }


        Map result =
                new LinkedHashMap()


        result.version =
                1


        result.methods =
                methods


        writeIfChanged(
                outputFile,
                result
        )


        return result
    }


    private List<Map> parseClasses(
            File sourceDirectory
    ) {

        List<File> javaFiles =
                []


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

                normalizeRelativePath(
                        sourceDirectory,
                        file
                )
        }


        List<Map> result =
                []


        javaFiles.each {
            File javaFile ->

                def parseResult =
                        javaParser.parse(
                                javaFile
                        )


                if (
                        !parseResult.successful ||
                                parseResult.result.empty
                ) {

                    throw new IllegalStateException(
                            "Unable to parse Java source '${javaFile.absolutePath}' with language level ${SOURCE_LANGUAGE_LEVEL}. Problems: ${parseResult.problems}"
                    )
                }


                CompilationUnit compilationUnit =
                        parseResult.result.get()


                String packageName =
                        compilationUnit.packageDeclaration
                                .map {
                                    it.nameAsString
                                }
                                .orElse(
                                        ''
                                )


                compilationUnit
                        .types
                        .findAll {
                            it instanceof ClassOrInterfaceDeclaration
                        }
                        .each {
                            ClassOrInterfaceDeclaration declaration ->

                                String fqcn =
                                        packageName.isBlank()
                                                ? declaration.nameAsString
                                                : packageName +
                                                '.' +
                                                declaration.nameAsString


                                result.add(
                                        [
                                                fqcn           : fqcn,
                                                packageName    : packageName,
                                                declaration    : declaration,
                                                compilationUnit: compilationUnit,
                                                file           : javaFile,
                                                sourcePath     : normalizeRelativePath(
                                                        sourceDirectory,
                                                        javaFile
                                                ),
                                                source         : javaFile.getText(
                                                        'UTF-8'
                                                )
                                        ]
                                )
                        }
        }


        return result
    }


    private void collectControllerMethods(
            File sourceDirectory,
            Map controllerInfo,
            Map<String, Map> classByFqcn,
            Map<String, Map> target
    ) {

        ClassOrInterfaceDeclaration controller =
                controllerInfo.declaration as ClassOrInterfaceDeclaration


        Map<String, String> fieldTypes =
                new LinkedHashMap<>()


        controller.fields.each {
            field ->

                field.variables.each {
                    variable ->

                        fieldTypes[
                                variable.nameAsString
                        ] =
                                variable.typeAsString
                }
        }


        controller.methods.each {
            MethodDeclaration method ->

                if (!hasMappingAnnotation(method)) {
                    return
                }


                List<Map> relatedSources =
                        resolveRelatedSources(
                                method,
                                controllerInfo,
                                fieldTypes,
                                classByFqcn
                        )


                String signature =
                        buildMethodSignature(
                                method
                        )


                String methodKey =
                        controllerInfo.fqcn +
                        '#' +
                        signature


                Map methodContext =
                        new LinkedHashMap()


                methodContext.controllerClass =
                        controllerInfo.fqcn


                methodContext.method =
                        method.nameAsString


                methodContext.signature =
                        signature


                methodContext.sourcePath =
                        controllerInfo.sourcePath


                methodContext.documentation =
                        method.javadocComment
                                .map {
                                    it.content.trim()
                                }
                                .orElse(
                                        ''
                                )


                methodContext.controllerMethodSource =
                        method.toString()


                methodContext.relatedSources =
                        relatedSources


                target[
                        methodKey
                ] =
                        methodContext
        }
    }


    private List<Map> resolveRelatedSources(
            MethodDeclaration method,
            Map controllerInfo,
            Map<String, String> fieldTypes,
            Map<String, Map> classByFqcn
    ) {

        Set<String> usedFields =
                new LinkedHashSet<>()


        method
                .findAll(
                        MethodCallExpr.class
                )
                .each {
                    MethodCallExpr call ->

                        if (
                                call.scope.present &&
                                        call.scope.get() instanceof NameExpr
                        ) {

                            String fieldName =
                                    (call.scope.get() as NameExpr)
                                            .nameAsString


                            if (
                                    fieldTypes.containsKey(
                                            fieldName
                                    )
                            ) {

                                usedFields.add(
                                        fieldName
                                )
                            }
                        }
                }


        List<Map> relatedSources =
                []


        usedFields.each {
            String fieldName ->

                String typeName =
                        simpleTypeName(
                                fieldTypes[
                                        fieldName
                                ]
                        )


                String fqcn =
                        resolveTypeFqcn(
                                typeName,
                                controllerInfo,
                                classByFqcn
                        )


                Map relatedClass =
                        fqcn == null
                                ? null
                                : classByFqcn[
                                fqcn
                        ]


                if (relatedClass == null) {
                    return
                }


                relatedSources.add(
                        [
                                field      : fieldName,
                                className  : fqcn,
                                sourcePath : relatedClass.sourcePath,
                                source     : relatedClass.source
                        ]
                )
        }


        return relatedSources.sort {
            Map left,
            Map right ->

                left.className <=>
                        right.className
        }
    }


    private static String resolveTypeFqcn(
            String simpleTypeName,
            Map controllerInfo,
            Map<String, Map> classByFqcn
    ) {

        CompilationUnit compilationUnit =
                controllerInfo.compilationUnit as CompilationUnit


        String importedType =
                compilationUnit.imports
                        .find {
                            imported ->

                                !imported.asterisk &&
                                        imported.nameAsString.endsWith(
                                                '.' +
                                                simpleTypeName
                                        )
                        }
                        ?.nameAsString


        if (
                importedType != null &&
                        classByFqcn.containsKey(
                                importedType
                        )
        ) {

            return importedType
        }


        String samePackageType =
                controllerInfo.packageName == null ||
                        controllerInfo.packageName.toString().isBlank()
                        ? simpleTypeName
                        : controllerInfo.packageName +
                        '.' +
                        simpleTypeName


        if (
                classByFqcn.containsKey(
                        samePackageType
                )
        ) {

            return samePackageType
        }


        List<String> matches =
                classByFqcn
                        .keySet()
                        .findAll {
                            String fqcn ->

                                fqcn.endsWith(
                                        '.' +
                                        simpleTypeName
                                ) ||
                                        fqcn ==
                                                simpleTypeName
                        }
                        .sort()


        return matches.size() == 1
                ? matches.first()
                : null
    }


    private static String simpleTypeName(
            String typeName
    ) {

        String value =
                typeName
                        ?.trim()
                        ?: ''


        int genericStart =
                value.indexOf(
                        '<'
                )


        if (genericStart >= 0) {

            value =
                    value.substring(
                            0,
                            genericStart
                    )
        }


        int packageSeparator =
                value.lastIndexOf(
                        '.'
                )


        return packageSeparator >= 0
                ? value.substring(
                packageSeparator + 1
        )
                : value
    }


    private static boolean hasMappingAnnotation(
            MethodDeclaration method
    ) {

        return method.annotations.any {
            AnnotationExpr annotation ->

                MAPPING_ANNOTATIONS.contains(
                        simpleAnnotationName(
                                annotation
                        )
                )
        }
    }


    private static boolean isController(
            ClassOrInterfaceDeclaration declaration
    ) {

        return declaration.annotations.any {
            AnnotationExpr annotation ->

                CONTROLLER_ANNOTATIONS.contains(
                        simpleAnnotationName(
                                annotation
                        )
                )
        }
    }


    private static String simpleAnnotationName(
            AnnotationExpr annotation
    ) {

        return annotation
                .nameAsString
                .tokenize('.')
                .last()
    }


    private static String buildMethodSignature(
            MethodDeclaration method
    ) {

        List<String> parameterTypes =
                method.parameters.collect {
                    parameter ->

                        String type =
                                simpleTypeName(
                                        parameter.type
                                                .toString()
                                                .replaceAll(
                                                        /\s+/,
                                                        ''
                                                )
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


    private static String normalizeRelativePath(
            File sourceDirectory,
            File file
    ) {

        return sourceDirectory
                .toPath()
                .relativize(
                        file.toPath()
                )
                .toString()
                .replace(
                        '\\',
                        '/'
                )
    }


    private static void writeIfChanged(
            File outputFile,
            Map content
    ) {

        String rendered =
                JsonOutput.prettyPrint(
                        JsonOutput.toJson(
                                content
                        )
                ) +
                '\n'


        if (
                outputFile.isFile() &&
                        outputFile.getText(
                                'UTF-8'
                        ) ==
                        rendered
        ) {

            return
        }


        if (
                outputFile.parentFile != null &&
                        !outputFile.parentFile.exists() &&
                        !outputFile.parentFile.mkdirs()
        ) {

            throw new IllegalStateException(
                    "Unable to create execution-context resource directory: ${outputFile.parentFile.absolutePath}"
            )
        }


        outputFile.setText(
                rendered,
                'UTF-8'
        )
    }
}
