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

    AGILE_DEVELOPMENT(
            'module:platform:development:software-process:methodology:agile-development',
            'module/platform/development/software-process/methodology/agile-development',
            'LIBRARY',
            'Agile software development principles, iterative delivery and related methods such as Scrum, Kanban, XP and Lean',
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
            'module:platform:development:programming:framework:spring-framework:aspect',
            'module/platform/development/programming/framework/spring-framework/aspect',
            'SERVLET',
            'Spring Aspect-Oriented Programming with proxy-based interception, advice, pointcuts and AOP infrastructure',
            false,
            []
    ),

    ASPECT_ORIENTED_PROGRAMMING(
            'module:platform:development:programming:paradigm:aop',
            'module/platform/development/programming/paradigm/aop',
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
            'module:infrastructure:system:database:engine:document:mongodb',
            'module/infrastructure/system/database/engine/document/mongodb',
            'PLATFORM',
            '',
            false,
            []
    ),

    DATABASE_MYSQL(
            'module:infrastructure:system:database:engine:relational:mysql',
            'module/infrastructure/system/database/engine/relational/mysql',
            'SERVLET',
            '',
            false,
            []
    ),

    DATABASE_ORACLE(
            'module:infrastructure:system:database:engine:relational:oracle',
            'module/infrastructure/system/database/engine/relational/oracle',
            'SERVLET',
            '',
            false,
            []
    ),

    DATABASE_POSTGRESQL(
            'module:infrastructure:system:database:engine:relational:postgresql',
            'module/infrastructure/system/database/engine/relational/postgresql',
            'SERVLET',
            '',
            false,
            []
    ),

    DATA_ORIENTED_PROGRAMMING(
            'module:platform:development:programming:paradigm:data-oriented',
            'module/platform/development/programming/paradigm/data-oriented',
            'LIBRARY',
            'Data-Oriented Programming principles and data-centric program design',
            false,
            []
    ),

    DECLARATIVE_PROGRAMMING(
            'module:platform:development:programming:paradigm:declarative',
            'module/platform/development/programming/paradigm/declarative',
            'LIBRARY',
            'Declarative Programming paradigm, intent-oriented specification and related styles',
            false,
            []
    ),

    DEPENDENCY_INJECTION(
            'module:platform:development:software-design:design-technique:dependency-injection',
            'module/platform/development/software-design/design-technique/dependency-injection',
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
            'module:platform:development:software-design:architecture:domain-modeling:domain-driven-design',
            'module/platform/development/software-design/architecture/domain-modeling/domain-driven-design',
            'LIBRARY',
            'Domain-Driven Design fundamentals, strategic design and tactical design',
            false,
            []
    ),

    DOMAIN_SPECIFIC_LANGUAGE(
            'module:platform:development:programming:language:domain-specific-language',
            'module/platform/development/programming/language/domain-specific-language',
            'LIBRARY',
            'Domain-Specific Language concepts, design, parsing and execution models',
            false,
            []
    ),

    DRIVEN_DEVELOPMENT_METHODOLOGIES(
            'module:platform:development:software-process:methodology:driven-development',
            'module/platform/development/software-process/methodology/driven-development',
            'LIBRARY',
            'Driven development approaches and practices including TDD, BDD, ATDD, FDD and MDD',
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
            'module:infrastructure:system:database:schema-migration:flyway',
            'module/infrastructure/system/database/schema-migration/flyway',
            '',
            '',
            false,
            []
    ),

    FUNCTIONAL_PROGRAMMING(
            'module:platform:development:programming:paradigm:functional',
            'module/platform/development/programming/paradigm/functional',
            'LIBRARY',
            'Functional Programming paradigm concepts and trade-offs',
            false,
            []
    ),

    GLOBAL_EXCEPTION_HANDLER(
            'module:platform:development:programming:framework:spring-framework:global-exception-handler',
            'module/platform/development/programming/framework/spring-framework/global-exception-handler',
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
            'module:infrastructure:system:observability:management-console:hawtio',
            'module/infrastructure/system/observability/management-console/hawtio',
            'LIBRARY',
            '',
            false,
            []
    ),

    HIBERNATE(
            'module:platform:development:data:persistence:orm:hibernate',
            'module/platform/development/data/persistence/orm/hibernate',
            'LIBRARY',
            'Hibernate ORM implementation, unit-of-work behavior and persistence mechanics',
            false,
            []
    ),

    HIKARI_CP(
            'module:infrastructure:system:database:connection-management:hikari-cp',
            'module/infrastructure/system/database/connection-management/hikari-cp',
            'PLATFORM',
            '',
            false,
            []
    ),

    IMPERATIVE_PROGRAMMING(
            'module:platform:development:programming:paradigm:imperative',
            'module/platform/development/programming/paradigm/imperative',
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
            'module:platform:development:data:serialization:jackson',
            'module/platform/development/data/serialization/jackson',
            'SERVLET',
            'Jackson data binding, JSON serialization and deserialization for Java applications',
            false,
            []
    ),

    JAVA_10_APPLICATION_CLASS_DATA_SHARING(
            'module:platform:development:programming:language:java:version:java10:application-class-data-sharing',
            'module/platform/development/programming/language/java/version/java10/application-class-data-sharing',
            'LIBRARY',
            'Java 10 - Application Class-Data Sharing',
            false,
            []
    ),

    JAVA_10_CONTAINER_AWARENESS(
            'module:platform:development:programming:language:java:version:java10:container-awareness',
            'module/platform/development/programming/language/java/version/java10/container-awareness',
            'LIBRARY',
            'Java 10 - Container Awareness',
            false,
            []
    ),

    JAVA_10_LOCAL_VARIABLE_TYPE_INFERENCE(
            'module:platform:development:programming:language:java:version:java10:local-variable-type-inference',
            'module/platform/development/programming/language/java/version/java10/local-variable-type-inference',
            'LIBRARY',
            'Java 10 - Local Variable Type Inference',
            false,
            []
    ),

    JAVA_10_PARALLEL_FULL_GC_FOR_G1(
            'module:platform:development:programming:language:java:version:java10:parallel-full-gc-for-g1',
            'module/platform/development/programming/language/java/version/java10/parallel-full-gc-for-g1',
            'LIBRARY',
            'Java 10 - Parallel Full GC for G1',
            false,
            []
    ),

    JAVA_11_DEPLOYMENT_STACK_REMOVAL(
            'module:platform:development:programming:language:java:version:java11:deployment-stack-removal',
            'module/platform/development/programming/language/java/version/java11/deployment-stack-removal',
            'LIBRARY',
            'Java 11 - Deployment Stack Removal',
            false,
            []
    ),

    JAVA_11_EPSILON_GC(
            'module:platform:development:programming:language:java:version:java11:epsilon-gc',
            'module/platform/development/programming/language/java/version/java11/epsilon-gc',
            'LIBRARY',
            'Java 11 - Epsilon GC',
            false,
            []
    ),

    JAVA_11_FLIGHT_RECORDER(
            'module:platform:development:programming:language:java:version:java11:flight-recorder',
            'module/platform/development/programming/language/java/version/java11/flight-recorder',
            'LIBRARY',
            'Java 11 - Flight Recorder',
            false,
            []
    ),

    JAVA_11_HTTP_CLIENT(
            'module:platform:development:programming:language:java:version:java11:http-client',
            'module/platform/development/programming/language/java/version/java11/http-client',
            'LIBRARY',
            'Java 11 - HTTP Client',
            false,
            []
    ),

    JAVA_11_JAVA_EE_CORBA_REMOVAL(
            'module:platform:development:programming:language:java:version:java11:java-ee-corba-removal',
            'module/platform/development/programming/language/java/version/java11/java-ee-corba-removal',
            'LIBRARY',
            'Java 11 - Java EE and CORBA Module Removal',
            false,
            []
    ),

    JAVA_11_LAMBDA_PARAMETER_VAR_SYNTAX(
            'module:platform:development:programming:language:java:version:java11:lambda-parameter-var-syntax',
            'module/platform/development/programming/language/java/version/java11/lambda-parameter-var-syntax',
            'LIBRARY',
            'Java 11 - Lambda Parameter var Syntax',
            false,
            []
    ),

    JAVA_11_SINGLE_FILE_SOURCE_CODE_LAUNCH(
            'module:platform:development:programming:language:java:version:java11:single-file-source-code-launch',
            'module/platform/development/programming/language/java/version/java11/single-file-source-code-launch',
            'LIBRARY',
            'Java 11 - Single-File Source-Code Launch',
            false,
            []
    ),

    JAVA_11_TLS13(
            'module:platform:development:programming:language:java:version:java11:tls13',
            'module/platform/development/programming/language/java/version/java11/tls13',
            'LIBRARY',
            'Java 11 - TLS 1.3',
            false,
            []
    ),

    JAVA_11_ZGC_EXPERIMENTAL(
            'module:platform:development:programming:language:java:version:java11:zgc-experimental',
            'module/platform/development/programming/language/java/version/java11/zgc-experimental',
            'LIBRARY',
            'Java 11 - ZGC Experimental',
            false,
            []
    ),

    JAVA_14_CMS_GC_REMOVAL(
            'module:platform:development:programming:language:java:version:java14:cms-gc-removal',
            'module/platform/development/programming/language/java/version/java14/cms-gc-removal',
            'LIBRARY',
            'Java 14 - CMS Garbage Collector Removal',
            false,
            []
    ),

    JAVA_14_HELPFUL_NULL_POINTER_EXCEPTIONS(
            'module:platform:development:programming:language:java:version:java14:helpful-null-pointer-exceptions',
            'module/platform/development/programming/language/java/version/java14/helpful-null-pointer-exceptions',
            'LIBRARY',
            'Java 14 - Helpful NullPointerExceptions',
            false,
            []
    ),

    JAVA_14_JFR_EVENT_STREAMING(
            'module:platform:development:programming:language:java:version:java14:jfr-event-streaming',
            'module/platform/development/programming/language/java/version/java14/jfr-event-streaming',
            'LIBRARY',
            'Java 14 - JFR Event Streaming',
            false,
            []
    ),

    JAVA_14_NUMA_AWARE_G1(
            'module:platform:development:programming:language:java:version:java14:numa-aware-g1',
            'module/platform/development/programming/language/java/version/java14/numa-aware-g1',
            'LIBRARY',
            'Java 14 - NUMA-Aware G1',
            false,
            []
    ),

    JAVA_14_PACK200_REMOVAL(
            'module:platform:development:programming:language:java:version:java14:pack200-removal',
            'module/platform/development/programming/language/java/version/java14/pack200-removal',
            'LIBRARY',
            'Java 14 - Pack200 Removal',
            false,
            []
    ),

    JAVA_14_SWITCH_EXPRESSIONS(
            'module:platform:development:programming:language:java:version:java14:switch-expressions',
            'module/platform/development/programming/language/java/version/java14/switch-expressions',
            'LIBRARY',
            'Java 14 - Switch Expressions',
            false,
            []
    ),

    JAVA_15_BIASED_LOCKING_DISABLED(
            'module:platform:development:programming:language:java:version:java15:biased-locking-disabled',
            'module/platform/development/programming/language/java/version/java15/biased-locking-disabled',
            'LIBRARY',
            'Java 15 - Biased Locking Disabled',
            false,
            []
    ),

    JAVA_15_EDDSA(
            'module:platform:development:programming:language:java:version:java15:eddsa',
            'module/platform/development/programming/language/java/version/java15/eddsa',
            'LIBRARY',
            'Java 15 - EdDSA',
            false,
            []
    ),

    JAVA_15_HIDDEN_CLASSES(
            'module:platform:development:programming:language:java:version:java15:hidden-classes',
            'module/platform/development/programming/language/java/version/java15/hidden-classes',
            'LIBRARY',
            'Java 15 - Hidden Classes',
            false,
            []
    ),

    JAVA_15_NASHORN_REMOVAL(
            'module:platform:development:programming:language:java:version:java15:nashorn-removal',
            'module/platform/development/programming/language/java/version/java15/nashorn-removal',
            'LIBRARY',
            'Java 15 - Nashorn Removal',
            false,
            []
    ),

    JAVA_15_SHENANDOAH_PRODUCTION(
            'module:platform:development:programming:language:java:version:java15:shenandoah-production',
            'module/platform/development/programming/language/java/version/java15/shenandoah-production',
            'LIBRARY',
            'Java 15 - Shenandoah Production',
            false,
            []
    ),

    JAVA_15_SOLARIS_SPARC_PORT_REMOVAL(
            'module:platform:development:programming:language:java:version:java15:solaris-sparc-port-removal',
            'module/platform/development/programming/language/java/version/java15/solaris-sparc-port-removal',
            'LIBRARY',
            'Java 15 - Solaris and SPARC Port Removal',
            false,
            []
    ),

    JAVA_15_TEXT_BLOCKS(
            'module:platform:development:programming:language:java:version:java15:text-blocks',
            'module/platform/development/programming/language/java/version/java15/text-blocks',
            'LIBRARY',
            'Java 15 - Text Blocks',
            false,
            []
    ),

    JAVA_15_ZGC_PRODUCTION(
            'module:platform:development:programming:language:java:version:java15:zgc-production',
            'module/platform/development/programming/language/java/version/java15/zgc-production',
            'LIBRARY',
            'Java 15 - ZGC Production',
            false,
            []
    ),

    JAVA_16_ELASTIC_METASPACE(
            'module:platform:development:programming:language:java:version:java16:elastic-metaspace',
            'module/platform/development/programming/language/java/version/java16/elastic-metaspace',
            'LIBRARY',
            'Java 16 - Elastic Metaspace',
            false,
            []
    ),

    JAVA_16_JPACKAGE(
            'module:platform:development:programming:language:java:version:java16:jpackage',
            'module/platform/development/programming/language/java/version/java16/jpackage',
            'LIBRARY',
            'Java 16 - jpackage',
            false,
            []
    ),

    JAVA_16_PATTERN_MATCHING_INSTANCEOF(
            'module:platform:development:programming:language:java:version:java16:pattern-matching-instanceof',
            'module/platform/development/programming/language/java/version/java16/pattern-matching-instanceof',
            'LIBRARY',
            'Java 16 - Pattern Matching for instanceof',
            false,
            []
    ),

    JAVA_16_STRONG_ENCAPSULATION_BY_DEFAULT(
            'module:platform:development:programming:language:java:version:java16:strong-encapsulation-by-default',
            'module/platform/development/programming/language/java/version/java16/strong-encapsulation-by-default',
            'LIBRARY',
            'Java 16 - Strong Encapsulation by Default',
            false,
            []
    ),

    JAVA_16_UNIX_DOMAIN_SOCKET_CHANNELS(
            'module:platform:development:programming:language:java:version:java16:unix-domain-socket-channels',
            'module/platform/development/programming/language/java/version/java16/unix-domain-socket-channels',
            'LIBRARY',
            'Java 16 - Unix-Domain Socket Channels',
            false,
            []
    ),

    JAVA_17_CONTEXT_SPECIFIC_DESERIALIZATION_FILTERS(
            'module:platform:development:programming:language:java:version:java17:context-specific-deserialization-filters',
            'module/platform/development/programming/language/java/version/java17/context-specific-deserialization-filters',
            'LIBRARY',
            'Java 17 - Context-Specific Deserialization Filters',
            false,
            []
    ),

    JAVA_17_ENHANCED_PRNG(
            'module:platform:development:programming:language:java:version:java17:enhanced-prng',
            'module/platform/development/programming/language/java/version/java17/enhanced-prng',
            'LIBRARY',
            'Java 17 - Enhanced Pseudo-Random Number Generators',
            false,
            []
    ),

    JAVA_17_MACOS_AARCH64_PORT(
            'module:platform:development:programming:language:java:version:java17:macos-aarch64-port',
            'module/platform/development/programming/language/java/version/java17/macos-aarch64-port',
            'LIBRARY',
            'Java 17 - macOS AArch64 Port',
            false,
            []
    ),

    JAVA_17_SEALED_CLASSES(
            'module:platform:development:programming:language:java:version:java17:sealed-classes',
            'module/platform/development/programming/language/java/version/java17/sealed-classes',
            'LIBRARY',
            'Java 17 - Sealed Classes',
            false,
            []
    ),

    JAVA_17_SECURITY_MANAGER_DEPRECATED_FOR_REMOVAL(
            'module:platform:development:programming:language:java:version:java17:security-manager-deprecated-for-removal',
            'module/platform/development/programming/language/java/version/java17/security-manager-deprecated-for-removal',
            'LIBRARY',
            'Java 17 - Security Manager Deprecated for Removal',
            false,
            []
    ),

    JAVA_17_STRICT_FLOATING_POINT_SEMANTICS(
            'module:platform:development:programming:language:java:version:java17:strict-floating-point-semantics',
            'module/platform/development/programming/language/java/version/java17/strict-floating-point-semantics',
            'LIBRARY',
            'Java 17 - Always-Strict Floating-Point Semantics',
            false,
            []
    ),

    JAVA_17_STRONG_ENCAPSULATION(
            'module:platform:development:programming:language:java:version:java17:strong-encapsulation',
            'module/platform/development/programming/language/java/version/java17/strong-encapsulation',
            'LIBRARY',
            'Java 17 - Strong Encapsulation',
            false,
            []
    ),

    JAVA_18_CORE_REFLECTION_METHOD_HANDLES(
            'module:platform:development:programming:language:java:version:java18:core-reflection-method-handles',
            'module/platform/development/programming/language/java/version/java18/core-reflection-method-handles',
            'LIBRARY',
            'Java 18 - Core Reflection Reimplemented with Method Handles',
            false,
            []
    ),

    JAVA_18_FINALIZATION_DEPRECATED_FOR_REMOVAL(
            'module:platform:development:programming:language:java:version:java18:finalization-deprecated-for-removal',
            'module/platform/development/programming/language/java/version/java18/finalization-deprecated-for-removal',
            'LIBRARY',
            'Java 18 - Finalization Deprecated for Removal',
            false,
            []
    ),

    JAVA_18_INET_ADDRESS_RESOLVER_SPI(
            'module:platform:development:programming:language:java:version:java18:inet-address-resolver-spi',
            'module/platform/development/programming/language/java/version/java18/inet-address-resolver-spi',
            'LIBRARY',
            'Java 18 - InetAddress Resolver SPI',
            false,
            []
    ),

    JAVA_18_JAVADOC_CODE_SNIPPETS(
            'module:platform:development:programming:language:java:version:java18:javadoc-code-snippets',
            'module/platform/development/programming/language/java/version/java18/javadoc-code-snippets',
            'LIBRARY',
            'Java 18 - Javadoc Code Snippets',
            false,
            []
    ),

    JAVA_18_SIMPLE_WEB_SERVER(
            'module:platform:development:programming:language:java:version:java18:simple-web-server',
            'module/platform/development/programming/language/java/version/java18/simple-web-server',
            'LIBRARY',
            'Java 18 - Simple Web Server',
            false,
            []
    ),

    JAVA_18_UTF8_BY_DEFAULT(
            'module:platform:development:programming:language:java:version:java18:utf8-by-default',
            'module/platform/development/programming/language/java/version/java18/utf8-by-default',
            'LIBRARY',
            'Java 18 - UTF-8 by Default',
            false,
            []
    ),

    JAVA_21_GENERATIONAL_ZGC(
            'module:platform:development:programming:language:java:version:java21:generational-zgc',
            'module/platform/development/programming/language/java/version/java21/generational-zgc',
            'LIBRARY',
            'Java 21 - Generational ZGC',
            false,
            []
    ),

    JAVA_21_KEM_API(
            'module:platform:development:programming:language:java:version:java21:kem-api',
            'module/platform/development/programming/language/java/version/java21/kem-api',
            'LIBRARY',
            'Java 21 - Key Encapsulation Mechanism API',
            false,
            []
    ),

    JAVA_21_PATTERN_MATCHING_SWITCH(
            'module:platform:development:programming:language:java:version:java21:pattern-matching-switch',
            'module/platform/development/programming/language/java/version/java21/pattern-matching-switch',
            'LIBRARY',
            'Java 21 - Pattern Matching for switch',
            false,
            []
    ),

    JAVA_21_RECORD_PATTERNS(
            'module:platform:development:programming:language:java:version:java21:record-patterns',
            'module/platform/development/programming/language/java/version/java21/record-patterns',
            'LIBRARY',
            'Java 21 - Record Patterns',
            false,
            []
    ),

    JAVA_21_SEQUENCED_COLLECTIONS(
            'module:platform:development:programming:language:java:version:java21:sequenced-collections',
            'module/platform/development/programming/language/java/version/java21/sequenced-collections',
            'LIBRARY',
            'Java 21 - Sequenced Collections',
            false,
            []
    ),

    JAVA_21_VIRTUAL_THREADS(
            'module:platform:development:programming:language:java:version:java21:virtual-threads',
            'module/platform/development/programming/language/java/version/java21/virtual-threads',
            'LIBRARY',
            'Java 21 - Virtual Threads',
            false,
            []
    ),

    JAVA_22_FOREIGN_FUNCTION_MEMORY_API(
            'module:platform:development:programming:language:java:version:java22:foreign-function-memory-api',
            'module/platform/development/programming/language/java/version/java22/foreign-function-memory-api',
            'LIBRARY',
            'Java 22 - Foreign Function and Memory API',
            false,
            []
    ),

    JAVA_22_G1_REGION_PINNING(
            'module:platform:development:programming:language:java:version:java22:g1-region-pinning',
            'module/platform/development/programming/language/java/version/java22/g1-region-pinning',
            'LIBRARY',
            'Java 22 - G1 Region Pinning',
            false,
            []
    ),

    JAVA_22_MULTI_FILE_SOURCE_CODE_LAUNCH(
            'module:platform:development:programming:language:java:version:java22:multi-file-source-code-launch',
            'module/platform/development/programming/language/java/version/java22/multi-file-source-code-launch',
            'LIBRARY',
            'Java 22 - Multi-File Source-Code Launch',
            false,
            []
    ),

    JAVA_22_UNNAMED_VARIABLES_AND_PATTERNS(
            'module:platform:development:programming:language:java:version:java22:unnamed-variables-and-patterns',
            'module/platform/development/programming/language/java/version/java22/unnamed-variables-and-patterns',
            'LIBRARY',
            'Java 22 - Unnamed Variables and Patterns',
            false,
            []
    ),

    JAVA_23_GENERATIONAL_ZGC_DEFAULT(
            'module:platform:development:programming:language:java:version:java23:generational-zgc-default',
            'module/platform/development/programming/language/java/version/java23/generational-zgc-default',
            'LIBRARY',
            'Java 23 - Generational ZGC by Default',
            false,
            []
    ),

    JAVA_23_MARKDOWN_DOCUMENTATION_COMMENTS(
            'module:platform:development:programming:language:java:version:java23:markdown-documentation-comments',
            'module/platform/development/programming/language/java/version/java23/markdown-documentation-comments',
            'LIBRARY',
            'Java 23 - Markdown Documentation Comments',
            false,
            []
    ),

    JAVA_24_AOT_CLASS_LOADING_LINKING(
            'module:platform:development:programming:language:java:version:java24:aot-class-loading-linking',
            'module/platform/development/programming/language/java/version/java24/aot-class-loading-linking',
            'LIBRARY',
            'Java 24 - Ahead-of-Time Class Loading and Linking',
            false,
            []
    ),

    JAVA_24_GENERATIONAL_ZGC_ONLY(
            'module:platform:development:programming:language:java:version:java24:generational-zgc-only',
            'module/platform/development/programming/language/java/version/java24/generational-zgc-only',
            'LIBRARY',
            'Java 24 - Generational ZGC Only',
            false,
            []
    ),

    JAVA_24_ML_DSA(
            'module:platform:development:programming:language:java:version:java24:ml-dsa',
            'module/platform/development/programming/language/java/version/java24/ml-dsa',
            'LIBRARY',
            'Java 24 - ML-DSA',
            false,
            []
    ),

    JAVA_24_ML_KEM(
            'module:platform:development:programming:language:java:version:java24:ml-kem',
            'module/platform/development/programming/language/java/version/java24/ml-kem',
            'LIBRARY',
            'Java 24 - ML-KEM',
            false,
            []
    ),

    JAVA_24_SECURITY_MANAGER_DISABLED(
            'module:platform:development:programming:language:java:version:java24:security-manager-disabled',
            'module/platform/development/programming/language/java/version/java24/security-manager-disabled',
            'LIBRARY',
            'Java 24 - Security Manager Permanently Disabled',
            false,
            []
    ),

    JAVA_24_STREAM_GATHERERS(
            'module:platform:development:programming:language:java:version:java24:stream-gatherers',
            'module/platform/development/programming/language/java/version/java24/stream-gatherers',
            'LIBRARY',
            'Java 24 - Stream Gatherers',
            false,
            []
    ),

    JAVA_24_VIRTUAL_THREAD_SYNCHRONIZATION(
            'module:platform:development:programming:language:java:version:java24:virtual-thread-synchronization',
            'module/platform/development/programming/language/java/version/java24/virtual-thread-synchronization',
            'LIBRARY',
            'Java 24 - Synchronize Virtual Threads without Pinning',
            false,
            []
    ),

    JAVA_25_COMPACT_OBJECT_HEADERS(
            'module:platform:development:programming:language:java:version:java25:compact-object-headers',
            'module/platform/development/programming/language/java/version/java25/compact-object-headers',
            'LIBRARY',
            'Java 25 - Compact Object Headers',
            false,
            []
    ),

    JAVA_25_COMPACT_SOURCE_FILES_INSTANCE_MAIN_METHODS(
            'module:platform:development:programming:language:java:version:java25:compact-source-files-instance-main-methods',
            'module/platform/development/programming/language/java/version/java25/compact-source-files-instance-main-methods',
            'LIBRARY',
            'Java 25 - Compact Source Files and Instance Main Methods',
            false,
            []
    ),

    JAVA_25_FLEXIBLE_CONSTRUCTOR_BODIES(
            'module:platform:development:programming:language:java:version:java25:flexible-constructor-bodies',
            'module/platform/development/programming/language/java/version/java25/flexible-constructor-bodies',
            'LIBRARY',
            'Java 25 - Flexible Constructor Bodies',
            false,
            []
    ),

    JAVA_25_MODULE_IMPORT_DECLARATIONS(
            'module:platform:development:programming:language:java:version:java25:module-import-declarations',
            'module/platform/development/programming/language/java/version/java25/module-import-declarations',
            'LIBRARY',
            'Java 25 - Module Import Declarations',
            false,
            []
    ),

    JAVA_25_SCOPED_VALUES(
            'module:platform:development:programming:language:java:version:java25:scoped-values',
            'module/platform/development/programming/language/java/version/java25/scoped-values',
            'LIBRARY',
            'Java 25 - Scoped Values',
            false,
            []
    ),

    JAVA_26_AOT_OBJECT_CACHING_ANY_GC(
            'module:platform:development:programming:language:java:version:java26:aot-object-caching-any-gc',
            'module/platform/development/programming/language/java/version/java26/aot-object-caching-any-gc',
            'LIBRARY',
            'Java 26 - Ahead-of-Time Object Caching with Any GC',
            false,
            []
    ),

    JAVA_26_APPLET_API_REMOVAL(
            'module:platform:development:programming:language:java:version:java26:applet-api-removal',
            'module/platform/development/programming/language/java/version/java26/applet-api-removal',
            'LIBRARY',
            'Java 26 - Applet API Removal',
            false,
            []
    ),

    JAVA_26_FINAL_FIELD_MUTATION_WARNINGS(
            'module:platform:development:programming:language:java:version:java26:final-field-mutation-warnings',
            'module/platform/development/programming/language/java/version/java26/final-field-mutation-warnings',
            'LIBRARY',
            'Java 26 - Final Field Mutation Warnings',
            false,
            []
    ),

    JAVA_26_G1_THROUGHPUT(
            'module:platform:development:programming:language:java:version:java26:g1-throughput',
            'module/platform/development/programming/language/java/version/java26/g1-throughput',
            'LIBRARY',
            'Java 26 - G1 Throughput Improvements',
            false,
            []
    ),

    JAVA_26_HTTP3_CLIENT(
            'module:platform:development:programming:language:java:version:java26:http3-client',
            'module/platform/development/programming/language/java/version/java26/http3-client',
            'LIBRARY',
            'Java 26 - HTTP 3 Client',
            false,
            []
    ),

    JAVA_27_COMPACT_OBJECT_HEADERS_DEFAULT(
            'module:platform:development:programming:language:java:version:java27:compact-object-headers-default',
            'module/platform/development/programming/language/java/version/java27/compact-object-headers-default',
            'LIBRARY',
            'Java 27 - Compact Object Headers by Default',
            false,
            []
    ),

    JAVA_27_G1_DEFAULT_ALL_ENVIRONMENTS(
            'module:platform:development:programming:language:java:version:java27:g1-default-all-environments',
            'module/platform/development/programming/language/java/version/java27/g1-default-all-environments',
            'LIBRARY',
            'Java 27 - G1 Default in All Environments',
            false,
            []
    ),

    JAVA_27_JFR_IN_PROCESS_DATA_REDACTION(
            'module:platform:development:programming:language:java:version:java27:jfr-in-process-data-redaction',
            'module/platform/development/programming/language/java/version/java27/jfr-in-process-data-redaction',
            'LIBRARY',
            'Java 27 - JFR In-Process Data Redaction',
            false,
            []
    ),

    JAVA_27_POST_QUANTUM_HYBRID_TLS(
            'module:platform:development:programming:language:java:version:java27:post-quantum-hybrid-tls',
            'module/platform/development/programming/language/java/version/java27/post-quantum-hybrid-tls',
            'LIBRARY',
            'Java 27 - Post-Quantum Hybrid Key Exchange for TLS 1.3',
            false,
            []
    ),

    JAVA_5_ANNOTATIONS(
            'module:platform:development:programming:language:java:version:java5:annotations',
            'module/platform/development/programming/language/java/version/java5/annotations',
            'LIBRARY',
            'Java 5 - Annotations',
            false,
            []
    ),

    JAVA_5_AUTOBOXING_UNBOXING(
            'module:platform:development:programming:language:java:version:java5:autoboxing-unboxing',
            'module/platform/development/programming/language/java/version/java5/autoboxing-unboxing',
            'LIBRARY',
            'Java 5 - Autoboxing and Unboxing',
            false,
            []
    ),

    JAVA_5_CLASS_DATA_SHARING(
            'module:platform:development:programming:language:java:version:java5:class-data-sharing',
            'module/platform/development/programming/language/java/version/java5/class-data-sharing',
            'LIBRARY',
            'Java 5 - Class Data Sharing',
            false,
            []
    ),

    JAVA_5_CONCURRENCY_UTILITIES(
            'module:platform:development:programming:language:java:version:java5:concurrency-utilities',
            'module/platform/development/programming/language/java/version/java5/concurrency-utilities',
            'LIBRARY',
            'Java 5 - Concurrency Utilities',
            false,
            []
    ),

    JAVA_5_ENHANCED_FOR_LOOP(
            'module:platform:development:programming:language:java:version:java5:enhanced-for-loop',
            'module/platform/development/programming/language/java/version/java5/enhanced-for-loop',
            'LIBRARY',
            'Java 5 - Enhanced For Loop',
            false,
            []
    ),

    JAVA_5_ENUMS(
            'module:platform:development:programming:language:java:version:java5:enums',
            'module/platform/development/programming/language/java/version/java5/enums',
            'LIBRARY',
            'Java 5 - Enums',
            false,
            []
    ),

    JAVA_5_GENERICS(
            'module:platform:development:programming:language:java:version:java5:generics',
            'module/platform/development/programming/language/java/version/java5/generics',
            'LIBRARY',
            'Java 5 - Generics',
            false,
            []
    ),

    JAVA_5_STATIC_IMPORT(
            'module:platform:development:programming:language:java:version:java5:static-import',
            'module/platform/development/programming/language/java/version/java5/static-import',
            'LIBRARY',
            'Java 5 - Static Import',
            false,
            []
    ),

    JAVA_5_VARARGS(
            'module:platform:development:programming:language:java:version:java5:varargs',
            'module/platform/development/programming/language/java/version/java5/varargs',
            'LIBRARY',
            'Java 5 - Varargs',
            false,
            []
    ),

    JAVA_6_ANNOTATION_PROCESSING_API(
            'module:platform:development:programming:language:java:version:java6:annotation-processing-api',
            'module/platform/development/programming/language/java/version/java6/annotation-processing-api',
            'LIBRARY',
            'Java 6 - Annotation Processing API',
            false,
            []
    ),

    JAVA_6_COMPILER_API(
            'module:platform:development:programming:language:java:version:java6:compiler-api',
            'module/platform/development/programming/language/java/version/java6/compiler-api',
            'LIBRARY',
            'Java 6 - Compiler API',
            false,
            []
    ),

    JAVA_6_JDBC4(
            'module:platform:development:programming:language:java:version:java6:jdbc4',
            'module/platform/development/programming/language/java/version/java6/jdbc4',
            'LIBRARY',
            'Java 6 - JDBC 4',
            false,
            []
    ),

    JAVA_6_SCRIPTING_API(
            'module:platform:development:programming:language:java:version:java6:scripting-api',
            'module/platform/development/programming/language/java/version/java6/scripting-api',
            'LIBRARY',
            'Java 6 - Scripting API',
            false,
            []
    ),

    JAVA_6_SERVICE_LOADER(
            'module:platform:development:programming:language:java:version:java6:service-loader',
            'module/platform/development/programming/language/java/version/java6/service-loader',
            'LIBRARY',
            'Java 6 - ServiceLoader',
            false,
            []
    ),

    JAVA_7_BINARY_LITERALS(
            'module:platform:development:programming:language:java:version:java7:binary-literals',
            'module/platform/development/programming/language/java/version/java7/binary-literals',
            'LIBRARY',
            'Java 7 - Binary Literals',
            false,
            []
    ),

    JAVA_7_DIAMOND_OPERATOR(
            'module:platform:development:programming:language:java:version:java7:diamond-operator',
            'module/platform/development/programming/language/java/version/java7/diamond-operator',
            'LIBRARY',
            'Java 7 - Diamond Operator',
            false,
            []
    ),

    JAVA_7_FORK_JOIN(
            'module:platform:development:programming:language:java:version:java7:fork-join',
            'module/platform/development/programming/language/java/version/java7/fork-join',
            'LIBRARY',
            'Java 7 - Fork Join Framework',
            false,
            []
    ),

    JAVA_7_INVOKEDYNAMIC(
            'module:platform:development:programming:language:java:version:java7:invokedynamic',
            'module/platform/development/programming/language/java/version/java7/invokedynamic',
            'LIBRARY',
            'Java 7 - invokedynamic',
            false,
            []
    ),

    JAVA_7_METHOD_HANDLES(
            'module:platform:development:programming:language:java:version:java7:method-handles',
            'module/platform/development/programming/language/java/version/java7/method-handles',
            'LIBRARY',
            'Java 7 - Method Handles',
            false,
            []
    ),

    JAVA_7_MULTI_CATCH(
            'module:platform:development:programming:language:java:version:java7:multi-catch',
            'module/platform/development/programming/language/java/version/java7/multi-catch',
            'LIBRARY',
            'Java 7 - Multi-Catch',
            false,
            []
    ),

    JAVA_7_NIO2(
            'module:platform:development:programming:language:java:version:java7:nio2',
            'module/platform/development/programming/language/java/version/java7/nio2',
            'LIBRARY',
            'Java 7 - NIO.2',
            false,
            []
    ),

    JAVA_7_PRECISE_RETHROW(
            'module:platform:development:programming:language:java:version:java7:precise-rethrow',
            'module/platform/development/programming/language/java/version/java7/precise-rethrow',
            'LIBRARY',
            'Java 7 - Precise Rethrow',
            false,
            []
    ),

    JAVA_7_STRINGS_IN_SWITCH(
            'module:platform:development:programming:language:java:version:java7:strings-in-switch',
            'module/platform/development/programming/language/java/version/java7/strings-in-switch',
            'LIBRARY',
            'Java 7 - Strings in Switch',
            false,
            []
    ),

    JAVA_7_TRY_WITH_RESOURCES(
            'module:platform:development:programming:language:java:version:java7:try-with-resources',
            'module/platform/development/programming/language/java/version/java7/try-with-resources',
            'LIBRARY',
            'Java 7 - Try-With-Resources',
            false,
            []
    ),

    JAVA_7_UNDERSCORES_IN_NUMERIC_LITERALS(
            'module:platform:development:programming:language:java:version:java7:underscores-in-numeric-literals',
            'module/platform/development/programming/language/java/version/java7/underscores-in-numeric-literals',
            'LIBRARY',
            'Java 7 - Underscores in Numeric Literals',
            false,
            []
    ),

    JAVA_8_BASE64_API(
            'module:platform:development:programming:language:java:version:java8:base64-api',
            'module/platform/development/programming/language/java/version/java8/base64-api',
            'LIBRARY',
            'Java 8 - Base64 API',
            false,
            []
    ),

    JAVA_8_COMPLETABLE_FUTURE(
            'module:platform:development:programming:language:java:version:java8:completable-future',
            'module/platform/development/programming/language/java/version/java8/completable-future',
            'LIBRARY',
            'Java 8 - CompletableFuture',
            false,
            []
    ),

    JAVA_8_DATE_TIME_API(
            'module:platform:development:programming:language:java:version:java8:date-time-api',
            'module/platform/development/programming/language/java/version/java8/date-time-api',
            'LIBRARY',
            'Java 8 - Date-Time API',
            false,
            []
    ),

    JAVA_8_DEFAULT_INTERFACE_METHODS(
            'module:platform:development:programming:language:java:version:java8:default-interface-methods',
            'module/platform/development/programming/language/java/version/java8/default-interface-methods',
            'LIBRARY',
            'Java 8 - Default Interface Methods',
            false,
            []
    ),

    JAVA_8_FUNCTIONAL_INTERFACES(
            'module:platform:development:programming:language:java:version:java8:functional-interfaces',
            'module/platform/development/programming/language/java/version/java8/functional-interfaces',
            'LIBRARY',
            'Java 8 - Functional Interfaces',
            false,
            []
    ),

    JAVA_8_LAMBDA_EXPRESSIONS(
            'module:platform:development:programming:language:java:version:java8:lambda-expressions',
            'module/platform/development/programming/language/java/version/java8/lambda-expressions',
            'LIBRARY',
            'Java 8 - Lambda Expressions',
            false,
            []
    ),

    JAVA_8_METASPACE(
            'module:platform:development:programming:language:java:version:java8:metaspace',
            'module/platform/development/programming/language/java/version/java8/metaspace',
            'LIBRARY',
            'Java 8 - Metaspace',
            false,
            []
    ),

    JAVA_8_METHOD_REFERENCES(
            'module:platform:development:programming:language:java:version:java8:method-references',
            'module/platform/development/programming/language/java/version/java8/method-references',
            'LIBRARY',
            'Java 8 - Method References',
            false,
            []
    ),

    JAVA_8_NASHORN_JAVASCRIPT_ENGINE(
            'module:platform:development:programming:language:java:version:java8:nashorn-javascript-engine',
            'module/platform/development/programming/language/java/version/java8/nashorn-javascript-engine',
            'LIBRARY',
            'Java 8 - Nashorn JavaScript Engine',
            false,
            []
    ),

    JAVA_8_OPTIONAL(
            'module:platform:development:programming:language:java:version:java8:optional',
            'module/platform/development/programming/language/java/version/java8/optional',
            'LIBRARY',
            'Java 8 - Optional',
            false,
            []
    ),

    JAVA_8_REPEATABLE_ANNOTATIONS(
            'module:platform:development:programming:language:java:version:java8:repeatable-annotations',
            'module/platform/development/programming/language/java/version/java8/repeatable-annotations',
            'LIBRARY',
            'Java 8 - Repeatable Annotations',
            false,
            []
    ),

    JAVA_8_TYPE_ANNOTATIONS(
            'module:platform:development:programming:language:java:version:java8:type-annotations',
            'module/platform/development/programming/language/java/version/java8/type-annotations',
            'LIBRARY',
            'Java 8 - Type Annotations',
            false,
            []
    ),

    JAVA_9_COLLECTION_FACTORY_METHODS(
            'module:platform:development:programming:language:java:version:java9:collection-factory-methods',
            'module/platform/development/programming/language/java/version/java9/collection-factory-methods',
            'LIBRARY',
            'Java 9 - Collection Factory Methods',
            false,
            []
    ),

    JAVA_9_COMPACT_STRINGS(
            'module:platform:development:programming:language:java:version:java9:compact-strings',
            'module/platform/development/programming/language/java/version/java9/compact-strings',
            'LIBRARY',
            'Java 9 - Compact Strings',
            false,
            []
    ),

    JAVA_9_FLOW_API(
            'module:platform:development:programming:language:java:version:java9:flow-api',
            'module/platform/development/programming/language/java/version/java9/flow-api',
            'LIBRARY',
            'Java 9 - Flow API',
            false,
            []
    ),

    JAVA_9_G1_DEFAULT_GC(
            'module:platform:development:programming:language:java:version:java9:g1-default-gc',
            'module/platform/development/programming/language/java/version/java9/g1-default-gc',
            'LIBRARY',
            'Java 9 - G1 as Default Garbage Collector',
            false,
            []
    ),

    JAVA_9_JLINK(
            'module:platform:development:programming:language:java:version:java9:jlink',
            'module/platform/development/programming/language/java/version/java9/jlink',
            'LIBRARY',
            'Java 9 - jlink',
            false,
            []
    ),

    JAVA_9_JSHELL(
            'module:platform:development:programming:language:java:version:java9:jshell',
            'module/platform/development/programming/language/java/version/java9/jshell',
            'LIBRARY',
            'Java 9 - JShell',
            false,
            []
    ),

    JAVA_9_MULTI_RELEASE_JAR(
            'module:platform:development:programming:language:java:version:java9:multi-release-jar',
            'module/platform/development/programming/language/java/version/java9/multi-release-jar',
            'LIBRARY',
            'Java 9 - Multi-Release JAR',
            false,
            []
    ),

    JAVA_9_PRIVATE_INTERFACE_METHODS(
            'module:platform:development:programming:language:java:version:java9:private-interface-methods',
            'module/platform/development/programming/language/java/version/java9/private-interface-methods',
            'LIBRARY',
            'Java 9 - Private Interface Methods',
            false,
            []
    ),

    JAVA_9_PROCESS_API_UPDATES(
            'module:platform:development:programming:language:java:version:java9:process-api-updates',
            'module/platform/development/programming/language/java/version/java9/process-api-updates',
            'LIBRARY',
            'Java 9 - Process API Updates',
            false,
            []
    ),

    JAVA_9_STACK_WALKING_API(
            'module:platform:development:programming:language:java:version:java9:stack-walking-api',
            'module/platform/development/programming/language/java/version/java9/stack-walking-api',
            'LIBRARY',
            'Java 9 - Stack-Walking API',
            false,
            []
    ),

    JAVA_ABSTRACT_INTERFACE(
            'module:platform:development:programming:language:java:core:abstract-interface',
            'module/platform/development/programming/language/java/core/abstract-interface',
            'SERVLET',
            'Java abstract class and interface',
            false,
            []
    ),

    JAVA_ANNOTATION(
            'module:platform:development:programming:language:java:core:annotation',
            'module/platform/development/programming/language/java/core/annotation',
            'SERVLET',
            'Java annotations',
            false,
            []
    ),

    JAVA_ASYNC_PROGRAMMING(
            'module:platform:development:programming:language:java:concurrency:async-programming',
            'module/platform/development/programming/language/java/concurrency/async-programming',
            'SERVLET',
            'Java asynchronous programming with Future and CompletableFuture',
            false,
            []
    ),

    JAVA_CLASSLOADER(
            'module:platform:development:programming:language:java:core:classloader',
            'module/platform/development/programming/language/java/core/classloader',
            'SERVLET',
            'Java class loading',
            false,
            []
    ),

    JAVA_CLASS_FILE_API(
            'module:platform:development:programming:language:java:version:java24:class-file-api',
            'module/platform/development/programming/language/java/version/java24/class-file-api',
            'LIBRARY',
            'Java 24 standard Class-File API',
            false,
            []
    ),

    JAVA_CLASS_OBJECT(
            'module:platform:development:programming:language:java:core:class-object',
            'module/platform/development/programming/language/java/core/class-object',
            'SERVLET',
            'Java class and object model',
            false,
            []
    ),

    JAVA_COLLECTION(
            'module:platform:development:programming:language:java:core:collection',
            'module/platform/development/programming/language/java/core/collection',
            'SERVLET',
            'Java collections framework',
            false,
            []
    ),

    JAVA_CONCURRENCY_FUNDAMENTALS(
            'module:platform:development:programming:language:java:concurrency:fundamentals',
            'module/platform/development/programming/language/java/concurrency/fundamentals',
            'SERVLET',
            'Java concurrency fundamentals, Thread lifecycle and Java Memory Model',
            false,
            []
    ),

    JAVA_DATE_TIME(
            'module:platform:development:programming:language:java:core:date-time',
            'module/platform/development/programming/language/java/core/date-time',
            'LIBRARY',
            'Java date and time API',
            false,
            []
    ),

    JAVA_DYNAMIC_RUNTIME(
            'module:platform:development:programming:language:java:advance:dynamic-runtime',
            'module/platform/development/programming/language/java/advance/dynamic-runtime',
            'SERVLET',
            'Advanced Java dynamic invocation and runtime linkage',
            false,
            []
    ),

    JAVA_EXCEPTION(
            'module:platform:development:programming:language:java:core:exception',
            'module/platform/development/programming/language/java/core/exception',
            'SERVLET',
            'Java exception handling',
            false,
            []
    ),

    JAVA_EXECUTOR_SERVICE(
            'module:platform:development:programming:language:java:concurrency:executor-service',
            'module/platform/development/programming/language/java/concurrency/executor-service',
            'SERVLET',
            'Java executors, thread pools, scheduling and lifecycle',
            false,
            []
    ),

    JAVA_FORK_JOIN(
            'module:platform:development:programming:language:java:concurrency:fork-join',
            'module/platform/development/programming/language/java/concurrency/fork-join',
            'SERVLET',
            'Java Fork/Join Framework and work-stealing execution',
            false,
            []
    ),

    JAVA_FUNCTIONAL_PROGRAMMING(
            'module:platform:development:programming:language:java:core:functional-programming',
            'module/platform/development/programming/language/java/core/functional-programming',
            'LIBRARY',
            'Java functional programming with functional interfaces, lambdas, method references, Optional and composition',
            false,
            []
    ),

    JAVA_GENERICS(
            'module:platform:development:programming:language:java:core:generics',
            'module/platform/development/programming/language/java/core/generics',
            'SERVLET',
            'Java generics',
            false,
            []
    ),

    JAVA_HIGH_LEVEL_CONCURRENCY_UTILS(
            'module:platform:development:programming:language:java:concurrency:high-level-utils',
            'module/platform/development/programming/language/java/concurrency/high-level-utils',
            'SERVLET',
            'Java synchronization, coordination and concurrent utilities',
            false,
            []
    ),

    JAVA_INSTRUMENTATION(
            'module:platform:development:programming:language:java:advance:instrumentation',
            'module/platform/development/programming/language/java/advance/instrumentation',
            'SERVLET',
            'Java instrumentation, agents and class transformation',
            false,
            []
    ),

    JAVA_IO(
            'module:platform:development:programming:language:java:core:io',
            'module/platform/development/programming/language/java/core/io',
            'LIBRARY',
            'Java I/O and NIO',
            false,
            []
    ),

    JAVA_JVM(
            'module:platform:development:programming:language:java:advance:jvm',
            'module/platform/development/programming/language/java/advance/jvm',
            'LIBRARY',
            'Java Virtual Machine internals',
            false,
            []
    ),

    JAVA_LANGUAGE_BASICS(
            'module:platform:development:programming:language:java:core:language-basics',
            'module/platform/development/programming/language/java/core/language-basics',
            'SERVLET',
            'Java language basics',
            false,
            []
    ),

    JAVA_LOCALIZATION(
            'module:platform:development:programming:language:java:core:localization',
            'module/platform/development/programming/language/java/core/localization',
            'SERVLET',
            'Java localization and internationalization',
            false,
            []
    ),

    JAVA_MODULE_SYSTEM(
            'module:platform:development:programming:language:java:version:java9:module-system',
            'module/platform/development/programming/language/java/version/java9/module-system',
            'LIBRARY',
            'Java Platform Module System',
            false,
            []
    ),

    JAVA_NATIVE_INTEROPERABILITY(
            'module:platform:development:programming:language:java:advance:native-interoperability',
            'module/platform/development/programming/language/java/advance/native-interoperability',
            'SERVLET',
            'Java native interoperability, foreign memory and operating-system boundaries',
            false,
            []
    ),

    JAVA_NETWORKING(
            'module:platform:development:programming:language:java:advance:networking',
            'module/platform/development/programming/language/java/advance/networking',
            'SERVLET',
            'Java networking APIs',
            false,
            []
    ),

    JAVA_NUMBERS(
            'module:platform:development:programming:language:java:core:numbers',
            'module/platform/development/programming/language/java/core/numbers',
            'SERVLET',
            'Java numeric types and arithmetic',
            false,
            []
    ),

    JAVA_OBJECT_CONTRACT(
            'module:platform:development:programming:language:java:core:object-contract',
            'module/platform/development/programming/language/java/core/object-contract',
            'SERVLET',
            'Java object contracts',
            false,
            []
    ),

    JAVA_OOP(
            'module:platform:development:programming:language:java:core:oop',
            'module/platform/development/programming/language/java/core/oop',
            'SERVLET',
            'Java object-oriented programming',
            false,
            []
    ),

    JAVA_RECORD(
            'module:platform:development:programming:language:java:version:java16:record',
            'module/platform/development/programming/language/java/version/java16/record',
            '',
            '',
            false,
            []
    ),

    JAVA_REFLECTION(
            'module:platform:development:programming:language:java:core:reflection',
            'module/platform/development/programming/language/java/core/reflection',
            'SERVLET',
            'Java reflection',
            false,
            []
    ),

    JAVA_RUNTIME_DIAGNOSTICS(
            'module:platform:development:programming:language:java:advance:runtime-diagnostics',
            'module/platform/development/programming/language/java/advance/runtime-diagnostics',
            'SERVLET',
            'Java runtime diagnostics, management and troubleshooting',
            false,
            []
    ),

    JAVA_RUNTIME_EXTENSIBILITY(
            'module:platform:development:programming:language:java:advance:runtime-extensibility',
            'module/platform/development/programming/language/java/advance/runtime-extensibility',
            'SERVLET',
            'Java runtime extensibility, SPI and plugin architecture',
            false,
            []
    ),

    JAVA_SECURITY_CRYPTOGRAPHY(
            'module:platform:development:programming:language:java:advance:security-cryptography',
            'module/platform/development/programming/language/java/advance/security-cryptography',
            'SERVLET',
            'Java security and cryptography APIs',
            false,
            []
    ),

    JAVA_STREAM_API(
            'module:platform:development:programming:language:java:version:java8:stream-api',
            'module/platform/development/programming/language/java/version/java8/stream-api',
            'LIBRARY',
            'Java Stream API',
            false,
            []
    ),

    JAVA_STRING(
            'module:platform:development:programming:language:java:core:string',
            'module/platform/development/programming/language/java/core/string',
            'SERVLET',
            'Java String and text fundamentals',
            false,
            []
    ),

    JAVA_VIRTUAL_THREADS(
            'module:platform:development:programming:language:java:concurrency:virtual-threads',
            'module/platform/development/programming/language/java/concurrency/virtual-threads',
            'SERVLET',
            'Java virtual threads and modern thread-per-task execution',
            false,
            []
    ),

    JDBC(
            'module:platform:development:data:persistence:relational-access:jdbc',
            'module/platform/development/data/persistence/relational-access/jdbc',
            'LIBRARY',
            'Java Database Connectivity API for relational database access',
            false,
            []
    ),

    JPA_SPECIFICATION(
            'module:platform:development:data:persistence:orm:jpa',
            'module/platform/development/data/persistence/orm/jpa',
            'LIBRARY',
            'Jakarta Persistence specification, persistence context, entity lifecycle and ORM contracts',
            false,
            []
    ),

    KAFKA(
            'module:integration:messaging:event-streaming:kafka',
            'module/integration/messaging/event-streaming/kafka',
            '',
            '',
            false,
            []
    ),

    KAFKA_CONSUMER_ACCOUNTANT(
            'module:integration:messaging:event-streaming:kafka:service:consumer:accountant',
            'module/integration/messaging/event-streaming/kafka/service/consumer/accountant',
            '',
            '',
            false,
            []
    ),

    KAFKA_CONSUMER_NOTIFICATION(
            'module:integration:messaging:event-streaming:kafka:service:consumer:notification',
            'module/integration/messaging/event-streaming/kafka/service/consumer/notification',
            '',
            '',
            false,
            []
    ),

    KAFKA_CONTROL(
            'module:integration:messaging:event-streaming:kafka:service:control',
            'module/integration/messaging/event-streaming/kafka/service/control',
            '',
            '',
            false,
            []
    ),

    KAFKA_PRODUCER_BANK(
            'module:integration:messaging:event-streaming:kafka:service:producer:bank',
            'module/integration/messaging/event-streaming/kafka/service/producer/bank',
            'SERVLET',
            '',
            false,
            []
    ),

    KAFKA_SERVER(
            'module:integration:messaging:event-streaming:kafka:service:server',
            'module/integration/messaging/event-streaming/kafka/service/server',
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
            'module:platform:development:software-design:architecture:architectural-pattern:mvc',
            'module/platform/development/software-design/architecture/architectural-pattern/mvc',
            'LIBRARY',
            'Model-View-Controller architectural pattern',
            false,
            []
    ),

    MYBATIS(
            'module:platform:development:data:persistence:relational-access:mybatis',
            'module/platform/development/data/persistence/relational-access/mybatis',
            'LIBRARY',
            'MyBatis SQL mapping and data mapper framework',
            false,
            []
    ),

    OBJECT_MAPPING(
            'module:platform:development:data:data-mapping:object-mapping',
            'module/platform/development/data/data-mapping/object-mapping',
            'SERVLET',
            'Object-to-object mapping concepts and implementations including manual mapping, MapStruct and ModelMapper',
            false,
            []
    ),

    OBJECT_ORIENTED_PROGRAMMING(
            'module:platform:development:programming:paradigm:object-oriented',
            'module/platform/development/programming/paradigm/object-oriented',
            'LIBRARY',
            'Object-Oriented Programming paradigm concepts and object collaboration',
            false,
            []
    ),

    OPEN_REWRITE_GRADLE(
            'module:platform:development:engineering:build-tool:gradle:open-rewrite',
            'module/platform/development/engineering/build-tool/gradle/open-rewrite',
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
            'module:platform:development:programming:paradigm:reactive',
            'module/platform/development/programming/paradigm/reactive',
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

    SOFTWARE_LIFECYCLE_MODELS(
            'module:platform:development:software-process:methodology:lifecycle-models',
            'module/platform/development/software-process/methodology/lifecycle-models',
            'LIBRARY',
            'Software development lifecycle models including Waterfall, Iterative, Incremental, Spiral and V-Model',
            false,
            []
    ),

    SPRINGDOC_OPENAPI(
            'module:platform:development:programming:framework:springdoc:openapi',
            'module/platform/development/programming/framework/springdoc/openapi',
            'LIBRARY',
            'Springdoc OpenAPI knowledge and examples for documenting Spring web APIs',
            false,
            []
    ),

    SPRING_ACTUATOR(
            'project-build:springboot-runtime:actuator',
            'project-build/springboot-runtime/actuator',
            'LIBRARY',
            'Runtime dependency wrapper for Spring Boot Actuator',
            true,
            []
    ),

    SPRING_BATCH(
            'module:platform:development:programming:framework:spring-batch',
            'module/platform/development/programming/framework/spring-batch',
            'LIBRARY',
            'Spring Batch job and step processing, chunk-oriented processing, restartability, fault tolerance and scaling',
            false,
            []
    ),

    SPRING_BOOT_ACTUATOR(
            'module:platform:development:programming:framework:spring-boot:actuator',
            'module/platform/development/programming/framework/spring-boot/actuator',
            'LIBRARY',
            'Spring Boot Actuator knowledge: production-ready endpoints, health, metrics, diagnostics, security and observability',
            false,
            []
    ),

    SPRING_BOOT_AUTO_CONFIGURATION(
            'module:platform:development:programming:framework:spring-boot:auto-configuration',
            'module/platform/development/programming/framework/spring-boot/auto-configuration',
            'LIBRARY',
            'Spring Boot auto-configuration model, conditional registration, ordering, diagnostics and custom auto-configuration',
            false,
            []
    ),

    SPRING_BOOT_EXTERNALIZED_CONFIGURATION(
            'module:platform:development:programming:framework:spring-boot:externalized-configuration',
            'module/platform/development/programming/framework/spring-boot/externalized-configuration',
            'SERVLET',
            'Spring Boot externalized configuration including config data, property precedence, binding, profiles, validation and metadata',
            false,
            []
    ),

    SPRING_BOOT_FUNDAMENTALS(
            'module:platform:development:programming:framework:spring-boot:fundamentals',
            'module/platform/development/programming/framework/spring-boot/fundamentals',
            'LIBRARY',
            'Spring Boot fundamentals including application bootstrap, starters, embedded servers, lifecycle, packaging and development experience',
            false,
            []
    ),

    SPRING_BOOT_NATIVE_IMAGE(
            'module:platform:development:programming:framework:spring-boot:native-image',
            'module/platform/development/programming/framework/spring-boot/native-image',
            'LIBRARY',
            'Spring Boot AOT and native image support including GraalVM integration, runtime hints, testing and trade-offs',
            false,
            []
    ),

    SPRING_BOOT_TESTING(
            'module:platform:development:programming:framework:spring-boot:testing',
            'module/platform/development/programming/framework/spring-boot/testing',
            'LIBRARY',
            'Spring Boot testing support including application context tests, test slices, test auto-configuration and integration testing',
            false,
            []
    ),

    SPRING_CONCURRENCY(
            'module:platform:development:programming:framework:spring-framework:concurrency',
            'module/platform/development/programming/framework/spring-framework/concurrency',
            'SERVLET',
            'Spring task execution, async methods, scheduling, context propagation and virtual-thread integration',
            false,
            []
    ),

    SPRING_CORE_CONTAINER(
            'module:platform:development:programming:framework:spring-framework:core-container',
            'module/platform/development/programming/framework/spring-framework/core-container',
            'SERVLET',
            'Spring Core Container, bean management, dependency injection, scopes, environment, profiles, conditional registration and configuration composition',
            false,
            []
    ),

    SPRING_DATA_JDBC(
            'module:platform:development:programming:framework:spring-data:jdbc',
            'module/platform/development/programming/framework/spring-data/jdbc',
            'LIBRARY',
            'Spring Data JDBC aggregate-oriented relational persistence, repositories, mapping and transactions',
            false,
            []
    ),

    SPRING_DATA_MONGODB(
            'module:platform:development:programming:framework:spring-data:mongodb',
            'module/platform/development/programming/framework/spring-data/mongodb',
            'LIBRARY',
            'Spring Data MongoDB repositories, mapping, queries, aggregation, transactions and reactive integration',
            false,
            []
    ),

    SPRING_DATA_R2DBC(
            'module:platform:development:programming:framework:spring-data:r2dbc',
            'module/platform/development/programming/framework/spring-data/r2dbc',
            'LIBRARY',
            'Spring Data R2DBC reactive relational access, repositories, mapping, transactions and backpressure-aware database workflows',
            false,
            []
    ),

    SPRING_DATA_REDIS(
            'module:platform:development:programming:framework:spring-data:redis',
            'module/platform/development/programming/framework/spring-data/redis',
            'LIBRARY',
            'Spring Data Redis templates, repositories, serialization, caching, pub-sub and reactive access',
            false,
            []
    ),

    SPRING_DEVTOOL(
            'project-build:springboot-runtime:devtools',
            'project-build/springboot-runtime/devtools',
            'LIBRARY',
            'Runtime development-only dependency wrapper for Spring Boot DevTools',
            true,
            []
    ),

    SPRING_INTEGRATION(
            'module:platform:development:programming:framework:spring-integration',
            'module/platform/development/programming/framework/spring-integration',
            'LIBRARY',
            'Spring Integration knowledge for Enterprise Integration Patterns, messaging channels, endpoints, routers, transformers, gateways and adapters',
            false,
            []
    ),

    SPRING_JPA(
            'module:platform:development:programming:framework:spring-data:jpa',
            'module/platform/development/programming/framework/spring-data/jpa',
            'LIBRARY',
            '',
            true,
            []
    ),

    SPRING_MODULITH(
            'module:platform:development:programming:framework:spring-modulith',
            'module/platform/development/programming/framework/spring-modulith',
            'LIBRARY',
            'Spring Modulith application modules, structural verification, module events, testing, observability and documentation',
            false,
            []
    ),

    SPRING_REACTIVE(
            'module:platform:development:programming:framework:spring-framework:reactive',
            'module/platform/development/programming/framework/spring-framework/reactive',
            'PLATFORM',
            'Shared YAML composition module for Spring WebFlux applications',
            false,
            []
    ),

    SPRING_SECURITY_FUNDAMENTALS(
            'module:platform:development:programming:framework:spring-security:fundamentals',
            'module/platform/development/programming/framework/spring-security/fundamentals',
            'LIBRARY',
            'Spring Security fundamentals including filter chain, authentication, authorization, security context, method security and common web protections',
            false,
            []
    ),

    SPRING_SECURITY_OAUTH2(
            'module:platform:development:programming:framework:spring-security:oauth2',
            'module/platform/development/programming/framework/spring-security/oauth2',
            'LIBRARY',
            'Spring Security OAuth2 and OpenID Connect including clients, resource servers, JWT, scopes and authorization-server concepts',
            false,
            []
    ),

    SPRING_SESSION(
            'module:platform:development:programming:framework:spring-session',
            'module/platform/development/programming/framework/spring-session',
            'LIBRARY',
            'Spring Session externalized HTTP session management, repositories, Redis/JDBC backing stores and Spring Security integration',
            false,
            []
    ),

    SPRING_TESTING(
            'module:platform:development:programming:framework:spring-framework:testing',
            'module/platform/development/programming/framework/spring-framework/testing',
            'LIBRARY',
            'Spring TestContext Framework including context configuration, profiles, test properties, context caching, transactions and integration testing',
            false,
            []
    ),

    SPRING_WEB(
            'module:platform:development:programming:framework:spring-framework:web',
            'module/platform/development/programming/framework/spring-framework/web',
            'PLATFORM',
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
