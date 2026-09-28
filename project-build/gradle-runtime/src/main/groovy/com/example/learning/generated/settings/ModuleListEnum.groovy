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
            'module:platform:development:framework:spring:aspect',
            'module/platform/development/framework/spring/aspect',
            'SERVLET',
            'Spring Aspect-Oriented Programming with proxy-based interception, advice, pointcuts and AOP infrastructure',
            false,
            []
    ),

    ASPECT_ORIENTED_PROGRAMMING(
            'module:platform:development:paradigm:aop',
            'module/platform/development/paradigm/aop',
            'LIBRARY',
            'Aspect-Oriented Programming concepts, cross-cutting concerns, terminology and implementation models',
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

    DATA_ORIENTED_PROGRAMMING(
            'module:platform:development:paradigm:data-oriented',
            'module/platform/development/paradigm/data-oriented',
            'LIBRARY',
            'Data-Oriented Programming principles and data-centric program design',
            false,
            []
    ),

    DECLARATIVE_PROGRAMMING(
            'module:platform:development:paradigm:declarative',
            'module/platform/development/paradigm/declarative',
            'LIBRARY',
            'Declarative Programming paradigm, intent-oriented specification and related styles',
            false,
            []
    ),

    DEPENDENCY_INJECTION(
            'module:platform:development:design-pattern:dependency-injection',
            'module/platform/development/design-pattern/dependency-injection',
            'LIBRARY',
            'Dependency Injection and Inversion of Control design concepts',
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

    DOMAIN_DRIVEN_DESIGN(
            'module:platform:development:architecture:domain-driven-design',
            'module/platform/development/architecture/domain-driven-design',
            'LIBRARY',
            'Domain-Driven Design fundamentals, strategic design and tactical design',
            false,
            []
    ),

    DOMAIN_SPECIFIC_LANGUAGE(
            'module:platform:development:language:domain-specific-language',
            'module/platform/development/language/domain-specific-language',
            'LIBRARY',
            'Domain-Specific Language concepts, design, parsing and execution models',
            false,
            []
    ),

    DRIVEN_DEVELOPMENT_METHODOLOGIES(
            'module:platform:development:methodology:driven-development',
            'module/platform/development/methodology/driven-development',
            'LIBRARY',
            'Software development methodologies including TDD, BDD, ATDD, FDD and MDD',
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

    FUNCTIONAL_PROGRAMMING(
            'module:platform:development:paradigm:functional',
            'module/platform/development/paradigm/functional',
            'LIBRARY',
            'Functional Programming paradigm concepts and trade-offs',
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

    HIBERNATE(
            'module:platform:development:persistence:orm:hibernate',
            'module/platform/development/persistence/orm/hibernate',
            'LIBRARY',
            'Hibernate ORM implementation, unit-of-work behavior and persistence mechanics',
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

    IMPERATIVE_PROGRAMMING(
            'module:platform:development:paradigm:imperative',
            'module/platform/development/paradigm/imperative',
            'LIBRARY',
            'Imperative Programming paradigm, state, commands and procedural organization',
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

    JACKSON(
            'module:platform:development:serialization:jackson',
            'module/platform/development/serialization/jackson',
            'SERVLET',
            'Jackson data binding, JSON serialization and deserialization for Java applications',
            false,
            []
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
            'SERVLET',
            'Java annotations',
            false,
            []
    ),

    JAVA_ASYNC_PROGRAMMING(
            'module:platform:development:language:java:concurrency:async-programming',
            'module/platform/development/language/java/concurrency/async-programming',
            'SERVLET',
            'Java asynchronous programming with Future and CompletableFuture',
            false,
            []
    ),

    JAVA_CLASSLOADER(
            'module:platform:development:language:java:core:classloader',
            'module/platform/development/language/java/core/classloader',
            'SERVLET',
            'Java class loading',
            false,
            []
    ),

    JAVA_CLASS_FILE_API(
            'module:platform:development:language:java:version:java24:class-file-api',
            'module/platform/development/language/java/version/java24/class-file-api',
            'LIBRARY',
            'Java 24 standard Class-File API',
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
            'SERVLET',
            'Java collections framework',
            false,
            []
    ),

    JAVA_CONCURRENCY_FUNDAMENTALS(
            'module:platform:development:language:java:concurrency:fundamentals',
            'module/platform/development/language/java/concurrency/fundamentals',
            'SERVLET',
            'Java concurrency fundamentals, Thread lifecycle and Java Memory Model',
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

    JAVA_DYNAMIC_RUNTIME(
            'module:platform:development:language:java:advance:dynamic-runtime',
            'module/platform/development/language/java/advance/dynamic-runtime',
            'LIBRARY',
            'Advanced Java dynamic invocation and runtime linkage',
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

    JAVA_EXECUTOR_SERVICE(
            'module:platform:development:language:java:concurrency:executor-service',
            'module/platform/development/language/java/concurrency/executor-service',
            'SERVLET',
            'Java executors, thread pools, scheduling and lifecycle',
            false,
            []
    ),

    JAVA_FORK_JOIN(
            'module:platform:development:language:java:concurrency:fork-join',
            'module/platform/development/language/java/concurrency/fork-join',
            'SERVLET',
            'Java Fork/Join Framework and work-stealing execution',
            false,
            []
    ),

    JAVA_FUNCTIONAL_PROGRAMMING(
            'module:platform:development:language:java:core:functional-programming',
            'module/platform/development/language/java/core/functional-programming',
            'LIBRARY',
            'Java functional programming with functional interfaces, lambdas, method references, Optional and composition',
            false,
            []
    ),

    JAVA_GENERICS(
            'module:platform:development:language:java:core:generics',
            'module/platform/development/language/java/core/generics',
            'SERVLET',
            'Java generics',
            false,
            []
    ),

    JAVA_HIGH_LEVEL_CONCURRENCY_UTILS(
            'module:platform:development:language:java:concurrency:high-level-utils',
            'module/platform/development/language/java/concurrency/high-level-utils',
            'SERVLET',
            'Java synchronization, coordination and concurrent utilities',
            false,
            []
    ),

    JAVA_INSTRUMENTATION(
            'module:platform:development:language:java:advance:instrumentation',
            'module/platform/development/language/java/advance/instrumentation',
            'LIBRARY',
            'Java instrumentation, agents and class transformation',
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
            'SERVLET',
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

    JAVA_NATIVE_INTEROPERABILITY(
            'module:platform:development:language:java:advance:native-interoperability',
            'module/platform/development/language/java/advance/native-interoperability',
            'LIBRARY',
            'Java native interoperability, foreign memory and operating-system boundaries',
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
            'SERVLET',
            'Java reflection',
            false,
            []
    ),

    JAVA_RUNTIME_DIAGNOSTICS(
            'module:platform:development:language:java:advance:runtime-diagnostics',
            'module/platform/development/language/java/advance/runtime-diagnostics',
            'LIBRARY',
            'Java runtime diagnostics, management and troubleshooting',
            false,
            []
    ),

    JAVA_RUNTIME_EXTENSIBILITY(
            'module:platform:development:language:java:advance:runtime-extensibility',
            'module/platform/development/language/java/advance/runtime-extensibility',
            'LIBRARY',
            'Java runtime extensibility, SPI and plugin architecture',
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

    JAVA_VIRTUAL_THREADS(
            'module:platform:development:language:java:concurrency:virtual-threads',
            'module/platform/development/language/java/concurrency/virtual-threads',
            'SERVLET',
            'Java virtual threads and modern thread-per-task execution',
            false,
            []
    ),

    JDBC(
            'module:platform:development:persistence:relational-access:jdbc',
            'module/platform/development/persistence/relational-access/jdbc',
            'LIBRARY',
            'Java Database Connectivity API for relational database access',
            false,
            []
    ),

    JPA_SPECIFICATION(
            'module:platform:development:persistence:orm:jpa',
            'module/platform/development/persistence/orm/jpa',
            'LIBRARY',
            'Jakarta Persistence specification, persistence context, entity lifecycle and ORM contracts',
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

    MICROSERVICE(
            'module:microservice',
            'module/microservice',
            '',
            'This is a microservice service system app',
            false,
            []
    ),

    MODEL_VIEW_CONTROLLER(
            'module:platform:development:architecture:mvc',
            'module/platform/development/architecture/mvc',
            'LIBRARY',
            'Model-View-Controller architectural pattern',
            false,
            []
    ),

    MYBATIS(
            'module:platform:development:persistence:relational-access:mybatis',
            'module/platform/development/persistence/relational-access/mybatis',
            'LIBRARY',
            'MyBatis SQL mapping and data mapper framework',
            false,
            []
    ),

    OBJECT_MAPPING(
            'module:platform:development:data-mapping:object-mapping',
            'module/platform/development/data-mapping/object-mapping',
            'SERVLET',
            'Object-to-object mapping concepts and implementations including manual mapping, MapStruct and ModelMapper',
            false,
            []
    ),

    OBJECT_ORIENTED_PROGRAMMING(
            'module:platform:development:paradigm:object-oriented',
            'module/platform/development/paradigm/object-oriented',
            'LIBRARY',
            'Object-Oriented Programming paradigm concepts and object collaboration',
            false,
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

    REACTIVE_PROGRAMMING(
            'module:platform:development:paradigm:reactive',
            'module/platform/development/paradigm/reactive',
            'LIBRARY',
            'Reactive Programming paradigm, data flow and backpressure concepts',
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

    SPRING_ACTUATOR(
            'module:platform:development:framework:spring:actuator',
            'module/platform/development/framework/spring/actuator',
            '',
            'To check and monitor system info when running',
            true,
            []
    ),

    SPRING_CONCURRENCY(
            'module:platform:development:framework:spring:concurrency',
            'module/platform/development/framework/spring/concurrency',
            'SERVLET',
            'Spring task execution, async methods, scheduling, context propagation and virtual-thread integration',
            false,
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
