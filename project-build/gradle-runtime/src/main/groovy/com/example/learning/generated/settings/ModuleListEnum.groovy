package com.example.learning.generated.settings


enum ModuleListEnum {

    ADMIN_SERVER(
            'module:microservice:module:infrastructure:admin-server',
            'module/microservice/module/infrastructure/admin-server',
            'SERVLET',
            '',
            false,
            []
    ),

    API_GATEWAY(
            'module:microservice:module:infrastructure:api-gateway',
            'module/microservice/module/infrastructure/api-gateway',
            'SERVLET',
            '',
            false,
            []
    ),

    ARTHAS(
            'module:infrastructure:system:observability:diagnostic:runtime-analysis:arthas',
            'module/infrastructure/system/observability/diagnostic/runtime-analysis/arthas',
            'LIBRARY',
            '',
            false,
            []
    ),

    ASPECT(
            'module:platform:development:paradigm:aop',
            'module/platform/development/paradigm/aop',
            'SERVLET',
            '',
            false,
            []
    ),

    AZURE_TRANSLATE(
            'module:platform:support:document:translate:azure',
            'module/platform/support/document/translate/azure',
            'SERVLET',
            'Translate using Azure service',
            true,
            []
    ),

    CHECKSTYLE(
            'module:platform:code-quality:static-analysis:checkstyle',
            'module/platform/code-quality/static-analysis/checkstyle',
            'LIBRARY',
            '',
            false,
            []
    ),

    CONFIG_SERVER(
            'module:microservice:module:infrastructure:config-server',
            'module/microservice/module/infrastructure/config-server',
            'SERVLET',
            '',
            false,
            ['SPRING_ACTUATOR', 'EUREKA_CLIENT']
    ),

    DATABASE_MONGODB(
            'module:infrastructure:system:database:nosql:mongoDB',
            'module/infrastructure/system/database/nosql/mongoDB',
            'PLATFORM',
            '',
            false,
            []
    ),

    DATABASE_MYSQL(
            'module:infrastructure:system:database:rdbms:mysql',
            'module/infrastructure/system/database/rdbms/mysql',
            'SERVLET',
            '',
            false,
            []
    ),

    DATABASE_ORACLE(
            'module:infrastructure:system:database:rdbms:oracle',
            'module/infrastructure/system/database/rdbms/oracle',
            'SERVLET',
            '',
            false,
            []
    ),

    DATABASE_POSTGRESQL(
            'module:infrastructure:system:database:rdbms:postgresql',
            'module/infrastructure/system/database/rdbms/postgresql',
            'SERVLET',
            '',
            false,
            []
    ),

    DISTRIBUTED_TRACING(
            'module:microservice:module:platform:tracing',
            'module/microservice/module/platform/tracing',
            'LIBRARY',
            '',
            false,
            []
    ),

    EUREKA_CLIENT(
            'module:microservice:module:infrastructure:eureka-client',
            'module/microservice/module/infrastructure/eureka-client',
            'LIBRARY',
            'Library use for register,discovery,heartbeat service in microservice system',
            true,
            []
    ),

    EUREKA_SERVER(
            'module:microservice:module:infrastructure:eureka-server',
            'module/microservice/module/infrastructure/eureka-server',
            'SERVLET',
            '',
            false,
            []
    ),

    FLEXMARK_MARKDOWN(
            'module:platform:support:document:markdown:flexmark',
            'module/platform/support/document/markdown/flexmark',
            'SERVLET',
            '',
            true,
            []
    ),

    FLYWAY(
            'module:infrastructure:system:database:migration:flyway',
            'module/infrastructure/system/database/migration/flyway',
            '',
            '',
            false,
            []
    ),

    FORK_JOIN_WORK_STEALING(
            'module:platform:development:paradigm:concurrency:forkjoin-workstealing',
            'module/platform/development/paradigm/concurrency/forkjoin-workstealing',
            '',
            '',
            false,
            []
    ),

    GLOBAL_EXCEPTION_HANDLER(
            'module:platform:development:framework:spring:global-exception-handler',
            'module/platform/development/framework/spring/global-exception-handler',
            'LIBRARY',
            'Module stored custom exception handler case. Use by @EnableCustomExceptionHandler',
            true,
            []
    ),

    GLOBAL_EXECUTION_CONTEXT(
            'project-build:springboot-runtime:execution-context',
            'project-build/springboot-runtime/execution-context',
            'LIBRARY',
            'Stack-neutral execution capture models, store and generated source-context lookup',
            true,
            []
    ),

    GLOBAL_EXECUTION_CONTEXT_SERVLET(
            'project-build:springboot-runtime:execution-context-servlet',
            'project-build/springboot-runtime/execution-context-servlet',
            'LIBRARY',
            'Spring MVC/Servlet adapter for execution capture, log correlation and source-context lookup',
            true,
            ['GLOBAL_EXECUTION_CONTEXT']
    ),

    GLOBAL_SWAGGER_CONFIG(
            'project-build:springboot-runtime:swagger',
            'project-build/springboot-runtime/swagger',
            'LIBRARY',
            'Stack-neutral Swagger core configuration shared by Servlet and Reactive adapters',
            true,
            []
    ),

    GLOBAL_SWAGGER_REACTIVE(
            'project-build:springboot-runtime:swagger-reactive',
            'project-build/springboot-runtime/swagger-reactive',
            'LIBRARY',
            'Spring WebFlux adapter for global Swagger configuration',
            true,
            ['GLOBAL_SWAGGER_CONFIG']
    ),

    GLOBAL_SWAGGER_SERVLET(
            'project-build:springboot-runtime:swagger-servlet',
            'project-build/springboot-runtime/swagger-servlet',
            'LIBRARY',
            'Spring MVC/Servlet adapter for global Swagger configuration',
            true,
            ['GLOBAL_SWAGGER_CONFIG']
    ),

    HAWTIO(
            'module:infrastructure:system:observability:console:hawtio',
            'module/infrastructure/system/observability/console/hawtio',
            'LIBRARY',
            '',
            false,
            []
    ),

    HIKARI_CP(
            'module:infrastructure:system:database:connection-pool:hikariCP',
            'module/infrastructure/system/database/connection-pool/hikariCP',
            'PLATFORM',
            '',
            false,
            []
    ),

    INVENTORY_SERVICE(
            'module:microservice:module:service:inventory-service',
            'module/microservice/module/service/inventory-service',
            'SERVLET',
            'A service of microservice app . This service store inventory info',
            false,
            ['SPRING_JPA', 'GLOBAL_EXCEPTION_HANDLER', 'EUREKA_CLIENT']
    ),

    JAVA_ABSTRACT_INTERFACE(
            'module:platform:development:language:java:core:abstract-interface',
            'module/platform/development/language/java/core/abstract-interface',
            'SERVLET',
            'Java abstract class and interface',
            false,
            []
    ),

    JAVA_ANNOTATION(
            'module:platform:development:language:java:core:annotation',
            'module/platform/development/language/java/core/annotation',
            'LIBRARY',
            'Java annotations',
            false,
            []
    ),

    JAVA_CLASSLOADER(
            'module:platform:development:language:java:core:classloader',
            'module/platform/development/language/java/core/classloader',
            'LIBRARY',
            'Java class loading',
            false,
            []
    ),

    JAVA_CLASS_OBJECT(
            'module:platform:development:language:java:core:class-object',
            'module/platform/development/language/java/core/class-object',
            'SERVLET',
            'Java class and object model',
            false,
            []
    ),

    JAVA_COLLECTION(
            'module:platform:development:language:java:core:collection',
            'module/platform/development/language/java/core/collection',
            'LIBRARY',
            'Java collections framework',
            false,
            []
    ),

    JAVA_DATE_TIME(
            'module:platform:development:language:java:core:date-time',
            'module/platform/development/language/java/core/date-time',
            'LIBRARY',
            'Java date and time API',
            false,
            []
    ),

    JAVA_EXCEPTION(
            'module:platform:development:language:java:core:exception',
            'module/platform/development/language/java/core/exception',
            'SERVLET',
            'Java exception handling',
            false,
            []
    ),

    JAVA_FUNCTIONAL_PROGRAMMING(
            'module:platform:development:paradigm:functional',
            'module/platform/development/paradigm/functional',
            'LIBRARY',
            'Java functional programming',
            false,
            []
    ),

    JAVA_GENERICS(
            'module:platform:development:language:java:core:generics',
            'module/platform/development/language/java/core/generics',
            'LIBRARY',
            'Java generics',
            false,
            []
    ),

    JAVA_IO(
            'module:platform:development:language:java:core:io',
            'module/platform/development/language/java/core/io',
            'LIBRARY',
            'Java I/O and NIO',
            false,
            []
    ),

    JAVA_JVM(
            'module:platform:development:language:java:advance:jvm',
            'module/platform/development/language/java/advance/jvm',
            'LIBRARY',
            'Java Virtual Machine internals',
            false,
            []
    ),

    JAVA_LANGUAGE_BASICS(
            'module:platform:development:language:java:core:language-basics',
            'module/platform/development/language/java/core/language-basics',
            'SERVLET',
            'Java language basics',
            false,
            []
    ),

    JAVA_LOCALIZATION(
            'module:platform:development:language:java:core:localization',
            'module/platform/development/language/java/core/localization',
            'LIBRARY',
            'Java localization and internationalization',
            false,
            []
    ),

    JAVA_MODULE_SYSTEM(
            'module:platform:development:language:java:version:java9:module-system',
            'module/platform/development/language/java/version/java9/module-system',
            'LIBRARY',
            'Java Platform Module System',
            false,
            []
    ),

    JAVA_NETWORKING(
            'module:platform:development:language:java:advance:networking',
            'module/platform/development/language/java/advance/networking',
            'LIBRARY',
            'Java networking APIs',
            false,
            []
    ),

    JAVA_NUMBERS(
            'module:platform:development:language:java:core:numbers',
            'module/platform/development/language/java/core/numbers',
            'SERVLET',
            'Java numeric types and arithmetic',
            false,
            []
    ),

    JAVA_OBJECT_CONTRACT(
            'module:platform:development:language:java:core:object-contract',
            'module/platform/development/language/java/core/object-contract',
            'SERVLET',
            'Java object contracts',
            false,
            []
    ),

    JAVA_OOP(
            'module:platform:development:language:java:core:oop',
            'module/platform/development/language/java/core/oop',
            'SERVLET',
            'Java object-oriented programming',
            false,
            []
    ),

    JAVA_RECORD(
            'module:platform:development:language:java:version:java16:record',
            'module/platform/development/language/java/version/java16/record',
            '',
            '',
            false,
            []
    ),

    JAVA_REFLECTION(
            'module:platform:development:language:java:core:reflection',
            'module/platform/development/language/java/core/reflection',
            'LIBRARY',
            'Java reflection',
            false,
            []
    ),

    JAVA_SECURITY_CRYPTOGRAPHY(
            'module:platform:development:language:java:advance:security-cryptography',
            'module/platform/development/language/java/advance/security-cryptography',
            'LIBRARY',
            'Java security and cryptography APIs',
            false,
            []
    ),

    JAVA_STREAM_API(
            'module:platform:development:language:java:version:java8:stream-api',
            'module/platform/development/language/java/version/java8/stream-api',
            'LIBRARY',
            'Java Stream API',
            false,
            []
    ),

    JAVA_STRING(
            'module:platform:development:language:java:core:string',
            'module/platform/development/language/java/core/string',
            'SERVLET',
            'Java String and text fundamentals',
            false,
            []
    ),

    KAFKA(
            'module:integration:broker:kafka',
            'module/integration/broker/kafka',
            '',
            '',
            false,
            []
    ),

    KAFKA_CONSUMER_ACCOUNTANT(
            'module:integration:broker:kafka:service:consumer:accountant',
            'module/integration/broker/kafka/service/consumer/accountant',
            '',
            '',
            false,
            []
    ),

    KAFKA_CONSUMER_NOTIFICATION(
            'module:integration:broker:kafka:service:consumer:notification',
            'module/integration/broker/kafka/service/consumer/notification',
            '',
            '',
            false,
            []
    ),

    KAFKA_CONTROL(
            'module:integration:broker:kafka:service:control',
            'module/integration/broker/kafka/service/control',
            '',
            '',
            false,
            []
    ),

    KAFKA_PRODUCER_BANK(
            'module:integration:broker:kafka:service:producer:bank',
            'module/integration/broker/kafka/service/producer/bank',
            'SERVLET',
            '',
            false,
            []
    ),

    KAFKA_SERVER(
            'module:integration:broker:kafka:service:server',
            'module/integration/broker/kafka/service/server',
            'SERVLET',
            '',
            false,
            []
    ),

    LIBRARY_SAMPLE(
            'internal:library:sample',
            'internal/library/sample',
            '',
            '',
            false,
            []
    ),

    MAPSTRUCT(
            'module:platform:development:language:java:mapping:mapstruct',
            'module/platform/development/language/java/mapping/mapstruct',
            'SERVLET',
            '',
            true,
            []
    ),

    MICROSERVICE(
            'module:microservice',
            'module/microservice',
            '',
            'This is a microservice service system app',
            false,
            []
    ),

    MODEL_MAPPER(
            'module:platform:development:language:java:mapping:model-mapper',
            'module/platform/development/language/java/mapping/model-mapper',
            'SERVLET',
            'To quickly convert between 2 Java Object (Entity to DTO) by matching key field',
            true,
            []
    ),

    OBJECT_MAPPER(
            'module:platform:development:language:java:mapping:object-mapper',
            'module/platform/development/language/java/mapping/object-mapper',
            'SERVLET',
            'Convert Java Object to JSON (or XML , YAML)',
            true,
            []
    ),

    OPEN_REWRITE_GRADLE(
            'module:platform:development:build-tool:gradle:open-rewrite',
            'module/platform/development/build-tool/gradle/open-rewrite',
            '',
            '',
            false,
            []
    ),

    ORDER_SERVICE(
            'module:microservice:module:service:order-service',
            'module/microservice/module/service/order-service',
            'SERVLET',
            'A service of microservice app . This service to handle order from user',
            false,
            ['SPRING_JPA', 'EUREKA_CLIENT', 'GLOBAL_EXCEPTION_HANDLER']
    ),

    PRODUCT_SERVICE(
            'module:microservice:module:service:product-service',
            'module/microservice/module/service/product-service',
            'SERVLET',
            '',
            false,
            ['EUREKA_CLIENT', 'GLOBAL_EXCEPTION_HANDLER']
    ),

    PROJECT_PORTAL(
            'project-portal',
            'project-portal',
            'SERVLET',
            'Repository-level Java Learning portal hosting the React learning experience',
            false,
            []
    ),

    PROMETHEUS_GRAFANA(
            'module:microservice:module:deployments:prometheus-grafana',
            'module/microservice/module/deployments/prometheus-grafana',
            'LIBRARY',
            '',
            false,
            []
    ),

    RESILIENCE_4J(
            'module:microservice:module:platform:resilience4j',
            'module/microservice/module/platform/resilience4j',
            'LIBRARY',
            '',
            false,
            []
    ),

    SCHEDULE(
            'module:platform:development:paradigm:concurrency:schedule:task',
            'module/platform/development/paradigm/concurrency/schedule/task',
            'SERVLET',
            '',
            false,
            []
    ),

    SPRING_ACTUATOR(
            'module:platform:development:framework:spring:actuator',
            'module/platform/development/framework/spring/actuator',
            '',
            'To check and monitor system info when running',
            true,
            []
    ),

    SPRING_DEVTOOL(
            'module:platform:development:framework:spring:devtool',
            'module/platform/development/framework/spring/devtool',
            '',
            'Extension for developer to auto restart and apply code to app at develop phase',
            true,
            []
    ),

    SPRING_JPA(
            'module:platform:development:framework:spring:data:jpa',
            'module/platform/development/framework/spring/data/jpa',
            'LIBRARY',
            '',
            true,
            []
    ),

    SPRING_REACTIVE(
            'module:platform:development:framework:spring:reactive',
            'module/platform/development/framework/spring/reactive',
            'PLATFORM',
            'Shared YAML composition module for Spring WebFlux applications',
            false,
            []
    ),

    SPRING_SWAGGER(
            'module:platform:development:framework:spring:swagger',
            'module/platform/development/framework/spring/swagger',
            'LIBRARY',
            'To auto generate list RestfulAPI to front-end page',
            true,
            []
    ),

    SPRING_WEB(
            'module:platform:development:framework:spring:web',
            'module/platform/development/framework/spring/web',
            'PLATFORM',
            '',
            false,
            []
    ),

    THREAD(
            'module:platform:development:paradigm:concurrency:thread',
            'module/platform/development/paradigm/concurrency/thread',
            'SERVLET',
            '',
            false,
            []
    ),

    properties(
            'module:platform:development:framework:spring:basic:properties',
            'module/platform/development/framework/spring/basic/properties',
            'SERVLET',
            '',
            false,
            []
    );


    final String modulePath

    final String relativePath

    final String moduleType

    final String description

    final boolean isModuleDepend

    final List<String> moduleDependList


    ModuleListEnum(
            String modulePath,
            String relativePath,
            String moduleType,
            String description,
            boolean isModuleDepend,
            List<String> moduleDependList
    ) {

        this.modulePath =
                modulePath

        this.relativePath =
                relativePath

        this.moduleType =
                moduleType

        this.description =
                description

        this.isModuleDepend =
                isModuleDepend

        this.moduleDependList =
                (
                        moduleDependList
                                ?: []
                ).asImmutable()
    }


    static ModuleListEnum findByName(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            return null
        }


        String normalized =
                value.trim()


        return values().find {
            ModuleListEnum module ->

                module
                        .name()
                        .equalsIgnoreCase(
                                normalized
                        )
        }
    }
}
