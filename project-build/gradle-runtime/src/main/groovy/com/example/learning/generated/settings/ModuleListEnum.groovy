package com.example.learning.generated.settings


enum ModuleListEnum {

    ABSTRACT_INTERFACE(
            'module:platform:development:language:java:core:abstract-interface',
            'module/platform/development/language/java/core/abstract-interface',
            '',
            '',
            false,
            []
    ),

    ADMIN_SERVER(
            'module:microservice:module:infrastructure:admin-server',
            'module/microservice/module/infrastructure/admin-server',
            'APPLICATION',
            '',
            false,
            []
    ),

    API_GATEWAY(
            'module:microservice:module:infrastructure:api-gateway',
            'module/microservice/module/infrastructure/api-gateway',
            'APPLICATION',
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
            '',
            '',
            false,
            []
    ),

    AZURE_TRANSLATE(
            'module:platform:support:document:translate:azure',
            'module/platform/support/document/translate/azure',
            'APPLICATION',
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
            'APPLICATION',
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
            'APPLICATION',
            '',
            false,
            []
    ),

    DATABASE_ORACLE(
            'module:infrastructure:system:database:rdbms:oracle',
            'module/infrastructure/system/database/rdbms/oracle',
            'APPLICATION',
            '',
            false,
            []
    ),

    DATABASE_POSTGRESQL(
            'module:infrastructure:system:database:rdbms:postgresql',
            'module/infrastructure/system/database/rdbms/postgresql',
            'APPLICATION',
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
            'APPLICATION',
            '',
            false,
            []
    ),

    FLEXMARK_MARKDOWN(
            'module:platform:support:document:markdown:flexmark',
            'module/platform/support/document/markdown/flexmark',
            'APPLICATION',
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

    GLOBAL_SWAGGER_CONFIG(
            'project-build:springboot-runtime:swagger',
            'project-build/springboot-runtime/swagger',
            'LIBRARY',
            'Global swagger config for all project that bind',
            true,
            []
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
            'APPLICATION',
            'A service of microservice app . This service store inventory info',
            false,
            ['SPRING_JPA', 'GLOBAL_EXCEPTION_HANDLER', 'EUREKA_CLIENT']
    ),

    JAVA_RECORD(
            'module:platform:development:language:java:version:java16:record',
            'module/platform/development/language/java/version/java16/record',
            '',
            '',
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
            'APPLICATION',
            '',
            false,
            []
    ),

    KAFKA_SERVER(
            'module:integration:broker:kafka:service:server',
            'module/integration/broker/kafka/service/server',
            'APPLICATION',
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
            'APPLICATION',
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
            'APPLICATION',
            'To quickly convert between 2 Java Object (Entity to DTO) by matching key field',
            true,
            []
    ),

    OBJECT_MAPPER(
            'module:platform:development:language:java:mapping:object-mapper',
            'module/platform/development/language/java/mapping/object-mapper',
            'APPLICATION',
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
            'APPLICATION',
            'A service of microservice app . This service to handle order from user',
            false,
            ['SPRING_JPA', 'EUREKA_CLIENT', 'GLOBAL_EXCEPTION_HANDLER']
    ),

    PRODUCT_SERVICE(
            'module:microservice:module:service:product-service',
            'module/microservice/module/service/product-service',
            'APPLICATION',
            '',
            false,
            ['EUREKA_CLIENT', 'GLOBAL_EXCEPTION_HANDLER']
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
            'APPLICATION',
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
            '',
            '',
            false,
            []
    ),

    THREAD(
            'module:platform:development:paradigm:concurrency:thread',
            'module/platform/development/paradigm/concurrency/thread',
            'APPLICATION',
            '',
            false,
            []
    ),

    properties(
            'module:platform:development:framework:spring:basic:properties',
            'module/platform/development/framework/spring/basic/properties',
            'APPLICATION',
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
