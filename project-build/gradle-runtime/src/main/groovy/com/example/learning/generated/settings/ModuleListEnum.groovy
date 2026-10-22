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
            'SERVLET',
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

    COLLABORATION_AZURE_DEVOPS(
            'module:infrastructure:devops:source-management:collaboration-platform:azure-devops',
            'module/infrastructure/devops/source-management/collaboration-platform/azure-devops',
            'LIBRARY',
            'Azure DevOps collaboration with emphasis on Azure Repos: repositories, pull requests, code review, branch policies, permissions and project workflows',
            false,
            []
    ),

    COLLABORATION_BITBUCKET(
            'module:infrastructure:devops:source-management:collaboration-platform:bitbucket',
            'module/infrastructure/devops/source-management/collaboration-platform/bitbucket',
            'LIBRARY',
            'Bitbucket repository hosting and collaboration: repositories, pull requests, code review, workspace/project permissions, branch restrictions and team workflows',
            false,
            []
    ),

    COLLABORATION_GITHUB(
            'module:infrastructure:devops:source-management:collaboration-platform:github',
            'module/infrastructure/devops/source-management/collaboration-platform/github',
            'LIBRARY',
            'GitHub repository hosting and collaboration: repositories, pull requests, code review, permissions, branch protection, issues, releases and organization workflows',
            false,
            []
    ),

    COLLABORATION_GITLAB(
            'module:infrastructure:devops:source-management:collaboration-platform:gitlab',
            'module/infrastructure/devops/source-management/collaboration-platform/gitlab',
            'LIBRARY',
            'GitLab repository hosting and collaboration: repositories, merge requests, code review, permissions, protected branches, issues, releases and group/project workflows',
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
            'SERVLET',
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

    DEVOPS_ALERTING_STRATEGY(
            'module:infrastructure:devops:reliability-operations:alerting-strategy',
            'module/infrastructure/devops/reliability-operations/alerting-strategy',
            'LIBRARY',
            'Alerting strategy: actionable signals, symptom versus cause alerts, severity, routing, noise reduction and escalation design',
            false,
            []
    ),

    DEVOPS_ARGO_CD(
            'module:infrastructure:devops:runtime-delivery:gitops:argo-cd',
            'module/infrastructure/devops/runtime-delivery/gitops/argo-cd',
            'LIBRARY',
            'Argo CD for GitOps delivery: applications, sync, reconciliation, health, drift, promotion and rollback on Kubernetes',
            false,
            []
    ),

    DEVOPS_ARTIFACT_MANAGEMENT_FUNDAMENTALS(
            'module:infrastructure:devops:delivery-automation:artifact-management:fundamentals',
            'module/infrastructure/devops/delivery-automation/artifact-management/fundamentals',
            'LIBRARY',
            'Artifact management fundamentals: immutable build outputs, repositories, coordinates, provenance, retention, promotion and consumption',
            false,
            []
    ),

    DEVOPS_BACKSTAGE(
            'module:infrastructure:devops:platform-engineering:backstage',
            'module/infrastructure/devops/platform-engineering/backstage',
            'LIBRARY',
            'Backstage developer portal: software catalog, templates, plugins, ownership metadata, developer workflows and platform integration',
            false,
            []
    ),

    DEVOPS_BRANCHING_STRATEGY(
            'module:infrastructure:devops:source-management:repository-strategy:branching-strategy',
            'module/infrastructure/devops/source-management/repository-strategy/branching-strategy',
            'LIBRARY',
            'Branching strategy for team delivery: trunk-based development, GitHub Flow, Git Flow, branch lifetime, integration and protection tradeoffs',
            false,
            []
    ),

    DEVOPS_CAPACITY_PLANNING(
            'module:infrastructure:devops:reliability-operations:capacity-planning',
            'module/infrastructure/devops/reliability-operations/capacity-planning',
            'LIBRARY',
            'Capacity planning: demand, saturation, headroom, scaling thresholds, forecasting, bottlenecks and cost-performance tradeoffs',
            false,
            []
    ),

    DEVOPS_CI_CD_FUNDAMENTALS(
            'module:infrastructure:devops:delivery-automation:ci-cd:fundamentals',
            'module/infrastructure/devops/delivery-automation/ci-cd/fundamentals',
            'LIBRARY',
            'CI/CD fundamentals: pipeline stages, triggers, jobs, quality gates, artifacts, environments, promotion and feedback loops',
            false,
            []
    ),

    DEVOPS_CONFIGURATION_MANAGEMENT_FUNDAMENTALS(
            'module:infrastructure:devops:infrastructure-management:configuration-management:fundamentals',
            'module/infrastructure/devops/infrastructure-management/configuration-management/fundamentals',
            'LIBRARY',
            'Configuration management fundamentals: desired configuration, idempotency, inventory, convergence, drift and host configuration automation',
            false,
            []
    ),

    DEVOPS_CONTAINERIZATION_FUNDAMENTALS(
            'module:infrastructure:devops:runtime-delivery:containerization:fundamentals',
            'module/infrastructure/devops/runtime-delivery/containerization/fundamentals',
            'LIBRARY',
            'Containerization fundamentals: images, containers, isolation, layers, registries, runtime lifecycle and portability tradeoffs',
            false,
            []
    ),

    DEVOPS_CONTAINER_REGISTRY(
            'module:infrastructure:devops:delivery-automation:artifact-management:container-registry',
            'module/infrastructure/devops/delivery-automation/artifact-management/container-registry',
            'LIBRARY',
            'Container registry concepts: OCI images, repositories, tags versus digests, immutability, retention, authentication and promotion',
            false,
            []
    ),

    DEVOPS_DELIVERY_AUTOMATION_FUNDAMENTALS(
            'module:infrastructure:devops:delivery-automation:fundamentals',
            'module/infrastructure/devops/delivery-automation/fundamentals',
            'LIBRARY',
            'Delivery automation fundamentals: continuous integration, delivery flow, automation boundaries, feedback and promotion through delivery stages',
            false,
            []
    ),

    DEVOPS_DEPLOYMENT_FUNDAMENTALS(
            'module:infrastructure:devops:runtime-delivery:deployment:fundamentals',
            'module/infrastructure/devops/runtime-delivery/deployment/fundamentals',
            'LIBRARY',
            'Deployment fundamentals: rollout lifecycle, deployment safety, environment promotion, rollback, verification and delivery risk',
            false,
            []
    ),

    DEVOPS_DISASTER_RECOVERY(
            'module:infrastructure:devops:reliability-operations:disaster-recovery',
            'module/infrastructure/devops/reliability-operations/disaster-recovery',
            'LIBRARY',
            'Disaster recovery: failure domains, backups, restore verification, RTO, RPO, failover and recovery planning',
            false,
            []
    ),

    DEVOPS_DOCKER_COMPOSE(
            'module:infrastructure:devops:runtime-delivery:containerization:docker-compose',
            'module/infrastructure/devops/runtime-delivery/containerization/docker-compose',
            'LIBRARY',
            'Docker Compose for multi-container environments: services, networks, volumes, configuration, dependencies and local integration workflows',
            false,
            []
    ),

    DEVOPS_ENVIRONMENT_MANAGEMENT_FUNDAMENTALS(
            'module:infrastructure:devops:infrastructure-management:environment-management:fundamentals',
            'module/infrastructure/devops/infrastructure-management/environment-management/fundamentals',
            'LIBRARY',
            'Environment management fundamentals: local, development, staging and production boundaries, configuration ownership and promotion',
            false,
            []
    ),

    DEVOPS_ENVIRONMENT_STRATEGY(
            'module:infrastructure:devops:infrastructure-management:environment-management:environment-strategy',
            'module/infrastructure/devops/infrastructure-management/environment-management/environment-strategy',
            'LIBRARY',
            'Environment strategy: environment topology, parity, promotion, ephemeral environments, configuration separation and lifecycle tradeoffs',
            false,
            []
    ),

    DEVOPS_FUNDAMENTALS(
            'module:infrastructure:devops:fundamentals',
            'module/infrastructure/devops/fundamentals',
            'LIBRARY',
            'DevOps fundamentals: lifecycle, culture, feedback loops, source-to-production flow, automation, delivery, operations and cross-cutting practices',
            false,
            []
    ),

    DEVOPS_GITOPS_FUNDAMENTALS(
            'module:infrastructure:devops:runtime-delivery:gitops:fundamentals',
            'module/infrastructure/devops/runtime-delivery/gitops/fundamentals',
            'LIBRARY',
            'GitOps fundamentals: declarative desired state, Git as change source, reconciliation, drift detection, pull-based delivery and auditability',
            false,
            []
    ),

    DEVOPS_GOLDEN_PATH(
            'module:infrastructure:devops:platform-engineering:golden-path',
            'module/infrastructure/devops/platform-engineering/golden-path',
            'LIBRARY',
            'Golden paths for software delivery: recommended workflows, templates, standards, guardrails, escape hatches and continuous improvement',
            false,
            []
    ),

    DEVOPS_HELM(
            'module:infrastructure:devops:runtime-delivery:orchestration:helm',
            'module/infrastructure/devops/runtime-delivery/orchestration/helm',
            'LIBRARY',
            'Helm for Kubernetes packaging: charts, templates, values, releases, dependencies, upgrade and rollback workflows',
            false,
            []
    ),

    DEVOPS_IAC_FUNDAMENTALS(
            'module:infrastructure:devops:infrastructure-management:infrastructure-as-code:fundamentals',
            'module/infrastructure/devops/infrastructure-management/infrastructure-as-code/fundamentals',
            'LIBRARY',
            'Infrastructure as Code fundamentals: declarative versus imperative models, desired state, idempotency, state, planning and drift',
            false,
            []
    ),

    DEVOPS_INCIDENT_MANAGEMENT(
            'module:infrastructure:devops:reliability-operations:incident-management',
            'module/infrastructure/devops/reliability-operations/incident-management',
            'LIBRARY',
            'Incident management: detection, triage, command, communication, mitigation, recovery and operational coordination',
            false,
            []
    ),

    DEVOPS_INFRASTRUCTURE_MANAGEMENT_FUNDAMENTALS(
            'module:infrastructure:devops:infrastructure-management:fundamentals',
            'module/infrastructure/devops/infrastructure-management/fundamentals',
            'LIBRARY',
            'Infrastructure management fundamentals: provisioning, configuration, environments, desired state, drift and infrastructure lifecycle automation',
            false,
            []
    ),

    DEVOPS_INTERNAL_DEVELOPER_PLATFORM(
            'module:infrastructure:devops:platform-engineering:internal-developer-platform',
            'module/infrastructure/devops/platform-engineering/internal-developer-platform',
            'LIBRARY',
            'Internal Developer Platform concepts: platform capabilities, interfaces, abstractions, paved roads, ownership, governance and developer experience',
            false,
            []
    ),

    DEVOPS_MONOREPO_POLYREPO(
            'module:infrastructure:devops:source-management:repository-strategy:monorepo-polyrepo',
            'module/infrastructure/devops/source-management/repository-strategy/monorepo-polyrepo',
            'LIBRARY',
            'Repository topology strategy: monorepo versus polyrepo ownership, dependency boundaries, tooling, scaling and delivery tradeoffs',
            false,
            []
    ),

    DEVOPS_ORCHESTRATION_FUNDAMENTALS(
            'module:infrastructure:devops:runtime-delivery:orchestration:fundamentals',
            'module/infrastructure/devops/runtime-delivery/orchestration/fundamentals',
            'LIBRARY',
            'Container orchestration fundamentals: scheduling, desired state, service discovery, scaling, rollout, recovery and control-plane concepts',
            false,
            []
    ),

    DEVOPS_PLATFORM_ENGINEERING_FUNDAMENTALS(
            'module:infrastructure:devops:platform-engineering:fundamentals',
            'module/infrastructure/devops/platform-engineering/fundamentals',
            'LIBRARY',
            'Platform engineering fundamentals: internal platforms, product thinking, developer experience, self-service, golden paths and platform ownership',
            false,
            []
    ),

    DEVOPS_PLATFORM_SELF_SERVICE(
            'module:infrastructure:devops:platform-engineering:self-service',
            'module/infrastructure/devops/platform-engineering/self-service',
            'LIBRARY',
            'Self-service platform workflows: catalog-driven provisioning, standardized actions, guardrails, automation, permissions and developer autonomy',
            false,
            []
    ),

    DEVOPS_POSTMORTEM(
            'module:infrastructure:devops:reliability-operations:postmortem',
            'module/infrastructure/devops/reliability-operations/postmortem',
            'LIBRARY',
            'Blameless postmortems: timeline, contributing factors, corrective actions, learning loops and follow-through after incidents',
            false,
            []
    ),

    DEVOPS_RELEASE_MANAGEMENT(
            'module:infrastructure:devops:delivery-automation:release-management',
            'module/infrastructure/devops/delivery-automation/release-management',
            'LIBRARY',
            'Release management: release candidates, approvals, promotion, changelogs, immutability, rollback and release coordination',
            false,
            []
    ),

    DEVOPS_RELIABILITY_OPERATIONS_FUNDAMENTALS(
            'module:infrastructure:devops:reliability-operations:fundamentals',
            'module/infrastructure/devops/reliability-operations/fundamentals',
            'LIBRARY',
            'Reliability and operations fundamentals: service health, operational readiness, feedback, failure handling, recovery and continuous improvement',
            false,
            []
    ),

    DEVOPS_RUNTIME_DELIVERY_FUNDAMENTALS(
            'module:infrastructure:devops:runtime-delivery:fundamentals',
            'module/infrastructure/devops/runtime-delivery/fundamentals',
            'LIBRARY',
            'Runtime delivery fundamentals: packaging, deployment targets, orchestration, rollout, runtime configuration and operational delivery flow',
            false,
            []
    ),

    DEVOPS_SLI_SLO_SLA(
            'module:infrastructure:devops:reliability-operations:sli-slo-sla',
            'module/infrastructure/devops/reliability-operations/sli-slo-sla',
            'LIBRARY',
            'SLI, SLO and SLA fundamentals: service indicators, objectives, agreements, error budgets and reliability decision making',
            false,
            []
    ),

    DEVOPS_SOURCE_MANAGEMENT_FUNDAMENTALS(
            'module:infrastructure:devops:source-management:fundamentals',
            'module/infrastructure/devops/source-management/fundamentals',
            'LIBRARY',
            'Source management fundamentals: version control, repository hosting, collaboration workflows and repository strategy boundaries',
            false,
            []
    ),

    DEVOPS_VERSIONING(
            'module:infrastructure:devops:delivery-automation:versioning',
            'module/infrastructure/devops/delivery-automation/versioning',
            'LIBRARY',
            'Software versioning for delivery: semantic versioning, snapshots, prereleases, build metadata, tags and version lifecycle',
            false,
            []
    ),

    DEVSECOPS_CONTAINER_SECURITY(
            'module:infrastructure:devops:devsecops:container-security',
            'module/infrastructure/devops/devsecops/container-security',
            'LIBRARY',
            'Container delivery security: image hardening, scanning, provenance, least privilege, runtime boundaries and policy enforcement',
            false,
            []
    ),

    DEVSECOPS_DEPENDENCY_SCANNING(
            'module:infrastructure:devops:devsecops:dependency-scanning',
            'module/infrastructure/devops/devsecops/dependency-scanning',
            'LIBRARY',
            'Dependency scanning: software composition analysis, vulnerable dependencies, advisories, remediation and policy gates',
            false,
            []
    ),

    DEVSECOPS_FUNDAMENTALS(
            'module:infrastructure:devops:devsecops:fundamentals',
            'module/infrastructure/devops/devsecops/fundamentals',
            'LIBRARY',
            'DevSecOps fundamentals: integrating security controls, feedback and ownership throughout the software delivery lifecycle',
            false,
            []
    ),

    DEVSECOPS_INFRASTRUCTURE_SECURITY(
            'module:infrastructure:devops:devsecops:infrastructure-security',
            'module/infrastructure/devops/devsecops/infrastructure-security',
            'LIBRARY',
            'Infrastructure security in DevOps: IaC scanning, policy as code, misconfiguration detection, review and enforcement',
            false,
            []
    ),

    DEVSECOPS_PIPELINE_SECURITY(
            'module:infrastructure:devops:devsecops:pipeline-security',
            'module/infrastructure/devops/devsecops/pipeline-security',
            'LIBRARY',
            'Pipeline security: credentials, permissions, runner trust, untrusted input, artifact integrity and protected delivery workflows',
            false,
            []
    ),

    DEVSECOPS_SAST_DAST(
            'module:infrastructure:devops:devsecops:sast-dast',
            'module/infrastructure/devops/devsecops/sast-dast',
            'LIBRARY',
            'SAST and DAST in delivery pipelines: static and dynamic analysis scope, placement, findings, gating and tradeoffs',
            false,
            []
    ),

    DEVSECOPS_SECRET_SCANNING(
            'module:infrastructure:devops:devsecops:secret-scanning',
            'module/infrastructure/devops/devsecops/secret-scanning',
            'LIBRARY',
            'Secret scanning in source and delivery workflows: detection, prevention, remediation, rotation and false-positive handling',
            false,
            []
    ),

    DEVSECOPS_SOFTWARE_SUPPLY_CHAIN(
            'module:infrastructure:devops:devsecops:software-supply-chain',
            'module/infrastructure/devops/devsecops/software-supply-chain',
            'LIBRARY',
            'Software supply-chain security: provenance, SBOM, signing, attestations, dependency trust, build integrity and release verification',
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
            'project-build:springboot-runtime:exception-handler-servlet',
            'project-build/springboot-runtime/exception-handler-servlet',
            'LIBRARY',
            'Servlet runtime auto-configuration for shared MVC, Feign, and Mongo exception handling',
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
            'SERVLET',
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

    JAVA_STRING(
            'module:platform:development:programming:language:java:core:string',
            'module/platform/development/programming/language/java/core/string',
            'SERVLET',
            'Java String and text fundamentals',
            false,
            []
    ),

    JAVA_VERSION_JAVA10(
            'module:platform:development:programming:language:java:version:java10',
            'module/platform/development/programming/language/java/version/java10',
            'LIBRARY',
            'Java 10',
            false,
            []
    ),

    JAVA_VERSION_JAVA11(
            'module:platform:development:programming:language:java:version:java11',
            'module/platform/development/programming/language/java/version/java11',
            'LIBRARY',
            'Java 11',
            false,
            []
    ),

    JAVA_VERSION_JAVA14(
            'module:platform:development:programming:language:java:version:java14',
            'module/platform/development/programming/language/java/version/java14',
            'LIBRARY',
            'Java 14',
            false,
            []
    ),

    JAVA_VERSION_JAVA15(
            'module:platform:development:programming:language:java:version:java15',
            'module/platform/development/programming/language/java/version/java15',
            'LIBRARY',
            'Java 15',
            false,
            []
    ),

    JAVA_VERSION_JAVA16(
            'module:platform:development:programming:language:java:version:java16',
            'module/platform/development/programming/language/java/version/java16',
            'LIBRARY',
            'Java 16',
            false,
            []
    ),

    JAVA_VERSION_JAVA17(
            'module:platform:development:programming:language:java:version:java17',
            'module/platform/development/programming/language/java/version/java17',
            'LIBRARY',
            'Java 17',
            false,
            []
    ),

    JAVA_VERSION_JAVA18(
            'module:platform:development:programming:language:java:version:java18',
            'module/platform/development/programming/language/java/version/java18',
            'LIBRARY',
            'Java 18',
            false,
            []
    ),

    JAVA_VERSION_JAVA21(
            'module:platform:development:programming:language:java:version:java21',
            'module/platform/development/programming/language/java/version/java21',
            'LIBRARY',
            'Java 21',
            false,
            []
    ),

    JAVA_VERSION_JAVA22(
            'module:platform:development:programming:language:java:version:java22',
            'module/platform/development/programming/language/java/version/java22',
            'LIBRARY',
            'Java 22',
            false,
            []
    ),

    JAVA_VERSION_JAVA23(
            'module:platform:development:programming:language:java:version:java23',
            'module/platform/development/programming/language/java/version/java23',
            'LIBRARY',
            'Java 23',
            false,
            []
    ),

    JAVA_VERSION_JAVA24(
            'module:platform:development:programming:language:java:version:java24',
            'module/platform/development/programming/language/java/version/java24',
            'LIBRARY',
            'Java 24',
            false,
            []
    ),

    JAVA_VERSION_JAVA25(
            'module:platform:development:programming:language:java:version:java25',
            'module/platform/development/programming/language/java/version/java25',
            'LIBRARY',
            'Java 25',
            false,
            []
    ),

    JAVA_VERSION_JAVA26(
            'module:platform:development:programming:language:java:version:java26',
            'module/platform/development/programming/language/java/version/java26',
            'LIBRARY',
            'Java 26',
            false,
            []
    ),

    JAVA_VERSION_JAVA27(
            'module:platform:development:programming:language:java:version:java27',
            'module/platform/development/programming/language/java/version/java27',
            'LIBRARY',
            'Java 27',
            false,
            []
    ),

    JAVA_VERSION_JAVA5(
            'module:platform:development:programming:language:java:version:java5',
            'module/platform/development/programming/language/java/version/java5',
            'LIBRARY',
            'Java 5',
            false,
            []
    ),

    JAVA_VERSION_JAVA6(
            'module:platform:development:programming:language:java:version:java6',
            'module/platform/development/programming/language/java/version/java6',
            'LIBRARY',
            'Java 6',
            false,
            []
    ),

    JAVA_VERSION_JAVA7(
            'module:platform:development:programming:language:java:version:java7',
            'module/platform/development/programming/language/java/version/java7',
            'LIBRARY',
            'Java 7',
            false,
            []
    ),

    JAVA_VERSION_JAVA8(
            'module:platform:development:programming:language:java:version:java8',
            'module/platform/development/programming/language/java/version/java8',
            'LIBRARY',
            'Java 8',
            false,
            []
    ),

    JAVA_VERSION_JAVA9(
            'module:platform:development:programming:language:java:version:java9',
            'module/platform/development/programming/language/java/version/java9',
            'LIBRARY',
            'Java 9',
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
            'SERVLET',
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

    SECURITY_AUTHENTICATION_API_KEY_HMAC_AUTHENTICATION(
            'module:infrastructure:system:security:authentication:api-key-hmac-authentication',
            'module/infrastructure/system/security/authentication/api-key-hmac-authentication',
            'LIBRARY',
            'Security - API Key and HMAC Authentication',
            false,
            []
    ),

    SECURITY_AUTHENTICATION_CERTIFICATE_AUTHENTICATION(
            'module:infrastructure:system:security:authentication:certificate-authentication',
            'module/infrastructure/system/security/authentication/certificate-authentication',
            'LIBRARY',
            'Security - Certificate Authentication',
            false,
            []
    ),

    SECURITY_AUTHENTICATION_MULTI_FACTOR_AUTHENTICATION(
            'module:infrastructure:system:security:authentication:multi-factor-authentication',
            'module/infrastructure/system/security/authentication/multi-factor-authentication',
            'LIBRARY',
            'Security - Multi-Factor Authentication',
            false,
            []
    ),

    SECURITY_AUTHENTICATION_PASSKEYS_WEBAUTHN(
            'module:infrastructure:system:security:authentication:passkeys-webauthn',
            'module/infrastructure/system/security/authentication/passkeys-webauthn',
            'LIBRARY',
            'Security - Passkeys, WebAuthn and FIDO2',
            false,
            []
    ),

    SECURITY_AUTHENTICATION_PASSWORD_AUTHENTICATION(
            'module:infrastructure:system:security:authentication:password-authentication',
            'module/infrastructure/system/security/authentication/password-authentication',
            'LIBRARY',
            'Security - Password Authentication',
            false,
            []
    ),

    SECURITY_AUTHENTICATION_SESSION_SECURITY(
            'module:infrastructure:system:security:authentication:session-security',
            'module/infrastructure/system/security/authentication/session-security',
            'LIBRARY',
            'Security - Session Security',
            false,
            []
    ),

    SECURITY_AUTHENTICATION_TOKEN_BASED_AUTHENTICATION(
            'module:infrastructure:system:security:authentication:token-based-authentication',
            'module/infrastructure/system/security/authentication/token-based-authentication',
            'LIBRARY',
            'Security - Token-Based Authentication',
            false,
            []
    ),

    SECURITY_AUTHORIZATION_ABAC(
            'module:infrastructure:system:security:authorization:abac',
            'module/infrastructure/system/security/authorization/abac',
            'LIBRARY',
            'Security - Attribute-Based Access Control (ABAC)',
            false,
            []
    ),

    SECURITY_AUTHORIZATION_OBJECT_LEVEL_AUTHORIZATION(
            'module:infrastructure:system:security:authorization:object-level-authorization',
            'module/infrastructure/system/security/authorization/object-level-authorization',
            'LIBRARY',
            'Security - Object-Level Authorization',
            false,
            []
    ),

    SECURITY_AUTHORIZATION_POLICY_BASED_ACCESS_CONTROL(
            'module:infrastructure:system:security:authorization:policy-based-access-control',
            'module/infrastructure/system/security/authorization/policy-based-access-control',
            'LIBRARY',
            'Security - Policy-Based Access Control',
            false,
            []
    ),

    SECURITY_AUTHORIZATION_PRIVILEGED_ACCESS_MANAGEMENT(
            'module:infrastructure:system:security:authorization:privileged-access-management',
            'module/infrastructure/system/security/authorization/privileged-access-management',
            'LIBRARY',
            'Security - Privileged Access Management (PAM)',
            false,
            []
    ),

    SECURITY_AUTHORIZATION_RBAC(
            'module:infrastructure:system:security:authorization:rbac',
            'module/infrastructure/system/security/authorization/rbac',
            'LIBRARY',
            'Security - Role-Based Access Control (RBAC)',
            false,
            []
    ),

    SECURITY_FUNDAMENTALS(
            'module:infrastructure:system:security:fundamentals',
            'module/infrastructure/system/security/fundamentals',
            'LIBRARY',
            'Security - Security Fundamentals',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_APPLICATION_DEFAULT_CREDENTIALS(
            'module:infrastructure:system:security:identity-access-management:application-default-credentials',
            'module/infrastructure/system/security/identity-access-management/application-default-credentials',
            'LIBRARY',
            'Security - Application Default Credentials (ADC)',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_IAM(
            'module:infrastructure:system:security:identity-access-management:iam',
            'module/infrastructure/system/security/identity-access-management/iam',
            'LIBRARY',
            'Security - Identity and Access Management (IAM)',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_KEYCLOAK(
            'module:infrastructure:system:security:identity-access-management:keycloak',
            'module/infrastructure/system/security/identity-access-management/keycloak',
            'LIBRARY',
            'Security - Keycloak',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_OAUTH2_OIDC(
            'module:infrastructure:system:security:identity-access-management:oauth2-oidc',
            'module/infrastructure/system/security/identity-access-management/oauth2-oidc',
            'LIBRARY',
            'Security - OAuth 2.0 and OpenID Connect (OIDC)',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_SCIM_PROVISIONING(
            'module:infrastructure:system:security:identity-access-management:scim-provisioning',
            'module/infrastructure/system/security/identity-access-management/scim-provisioning',
            'LIBRARY',
            'Security - SCIM Provisioning and Identity Lifecycle',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_SERVICE_ACCOUNT(
            'module:infrastructure:system:security:identity-access-management:service-account',
            'module/infrastructure/system/security/identity-access-management/service-account',
            'LIBRARY',
            'Security - Service Account',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_SPIFFE_SPIRE(
            'module:infrastructure:system:security:identity-access-management:spiffe-spire',
            'module/infrastructure/system/security/identity-access-management/spiffe-spire',
            'LIBRARY',
            'Security - SPIFFE and SPIRE Workload Identity',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_SSO_SAML(
            'module:infrastructure:system:security:identity-access-management:sso-saml',
            'module/infrastructure/system/security/identity-access-management/sso-saml',
            'LIBRARY',
            'Security - Single Sign-On (SSO) and SAML 2.0',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_TOKEN_LIFECYCLE(
            'module:infrastructure:system:security:identity-access-management:token-lifecycle',
            'module/infrastructure/system/security/identity-access-management/token-lifecycle',
            'LIBRARY',
            'Security - Token Lifecycle',
            false,
            []
    ),

    SECURITY_IDENTITY_ACCESS_MANAGEMENT_WORKLOAD_IDENTITY_FEDERATION(
            'module:infrastructure:system:security:identity-access-management:workload-identity-federation',
            'module/infrastructure/system/security/identity-access-management/workload-identity-federation',
            'LIBRARY',
            'Security - Workload Identity Federation',
            false,
            []
    ),

    SECURITY_KEY_MANAGEMENT_KMS_HSM(
            'module:infrastructure:system:security:key-management:kms-hsm',
            'module/infrastructure/system/security/key-management/kms-hsm',
            'LIBRARY',
            'Security - Key Management Service (KMS) and Hardware Security Module (HSM)',
            false,
            []
    ),

    SECURITY_KEY_MANAGEMENT_PKI_CERTIFICATE_LIFECYCLE(
            'module:infrastructure:system:security:key-management:pki-certificate-lifecycle',
            'module/infrastructure/system/security/key-management/pki-certificate-lifecycle',
            'LIBRARY',
            'Security - Public Key Infrastructure (PKI) and Certificate Lifecycle',
            false,
            []
    ),

    SECURITY_SECRETS_MANAGEMENT_HASHICORP_VAULT(
            'module:infrastructure:system:security:secrets-management:hashicorp-vault',
            'module/infrastructure/system/security/secrets-management/hashicorp-vault',
            'LIBRARY',
            'Security - HashiCorp Vault',
            false,
            []
    ),

    SECURITY_SECRETS_MANAGEMENT_SECRET_LIFECYCLE(
            'module:infrastructure:system:security:secrets-management:secret-lifecycle',
            'module/infrastructure/system/security/secrets-management/secret-lifecycle',
            'LIBRARY',
            'Security - Secret Lifecycle',
            false,
            []
    ),

    SECURITY_SECRETS_MANAGEMENT_WINDOWS_DPAPI(
            'module:infrastructure:system:security:secrets-management:windows-dpapi',
            'module/infrastructure/system/security/secrets-management/windows-dpapi',
            'LIBRARY',
            'Security - Windows Data Protection API (DPAPI)',
            false,
            []
    ),

    SECURITY_TOKEN_SECURITY_JWT(
            'module:infrastructure:system:security:token-security:jwt',
            'module/infrastructure/system/security/token-security/jwt',
            'LIBRARY',
            'Security - JSON Web Token (JWT)',
            false,
            []
    ),

    SECURITY_ZERO_TRUST(
            'module:infrastructure:system:security:zero-trust',
            'module/infrastructure/system/security/zero-trust',
            'LIBRARY',
            'Security - Zero Trust Security Architecture',
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

    SOURCE_CONTROL_GIT(
            'module:infrastructure:devops:source-management:source-control:git',
            'module/infrastructure/devops/source-management/source-control/git',
            'LIBRARY',
            'Git source control fundamentals: repository model, commits, branches, merges, rebases, tags, remotes and collaboration workflows',
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
            'SERVLET',
            'Spring Boot Actuator knowledge: production-ready endpoints, health, metrics, diagnostics, security and observability',
            false,
            []
    ),

    SPRING_BOOT_APPLICATION_RUNTIME(
            'module:platform:development:programming:framework:spring-boot:application-runtime',
            'module/platform/development/programming/framework/spring-boot/application-runtime',
            'SERVLET',
            'Spring Boot application runtime lifecycle, execution, logging, TLS, Docker Compose integration, and runtime customization',
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

    SPRING_BOOT_BUILD_TOOLING_PACKAGING(
            'module:platform:development:programming:framework:spring-boot:build-tooling-packaging',
            'module/platform/development/programming/framework/spring-boot/build-tooling-packaging',
            'LIBRARY',
            'Spring Boot build plugins, executable packaging, layered archives, OCI images, Buildpacks, and deployment handoff',
            false,
            []
    ),

    SPRING_BOOT_EXTERNALIZED_CONFIGURATION(
            'module:platform:development:programming:framework:spring-boot:externalized-configuration',
            'module/platform/development/programming/framework/spring-boot/externalized-configuration',
            'LIBRARY',
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

    SPRING_BOOT_WEB_RUNTIME(
            'module:platform:development:programming:framework:spring-boot:web-runtime',
            'module/platform/development/programming/framework/spring-boot/web-runtime',
            'SERVLET',
            'Spring Boot web runtime, embedded servers, server auto-configuration, TLS, proxy handling, and graceful shutdown',
            false,
            []
    ),

    SPRING_CACHE(
            'module:platform:development:programming:framework:spring-framework:cache',
            'module/platform/development/programming/framework/spring-framework/cache',
            'SERVLET',
            'Spring Framework cache abstraction, declarative caching, key resolution and cache-provider integration boundary',
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

    SPRING_DATA_ACCESS(
            'module:platform:development:programming:framework:spring-framework:data-access',
            'module/platform/development/programming/framework/spring-framework/data-access',
            'SERVLET',
            'Spring Framework DAO support, JDBC and R2DBC core data-access infrastructure including JdbcClient, JdbcTemplate and DatabaseClient',
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

    SPRING_JMS(
            'module:platform:development:programming:framework:spring-framework:jms',
            'module/platform/development/programming/framework/spring-framework/jms',
            'SERVLET',
            'Spring Framework JMS access, JmsTemplate, listener containers, annotation-driven listeners and transaction integration',
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

    SPRING_MESSAGING(
            'module:platform:development:programming:framework:spring-framework:messaging',
            'module/platform/development/programming/framework/spring-framework/messaging',
            'SERVLET',
            'Spring Framework messaging foundation plus WebSocket/STOMP and RSocket application messaging infrastructure',
            false,
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
            'REACTIVE',
            'Spring WebFlux streaming, context propagation and blocking-boundary learning experiments',
            false,
            []
    ),

    SPRING_SECURITY(
            'module:platform:development:programming:framework:spring-security',
            'module/platform/development/programming/framework/spring-security',
            'LIBRARY',
            'Spring Security framework architecture and implementation: filters, authentication, authorization, method security, web protections, OAuth2 and OIDC integrations; framework-independent security concepts belong to infrastructure/system/security',
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

    SPRING_TRANSACTION_MANAGEMENT(
            'module:platform:development:programming:framework:spring-framework:transaction-management',
            'module/platform/development/programming/framework/spring-framework/transaction-management',
            'SERVLET',
            'Spring Framework transaction abstraction, declarative and programmatic transaction management, propagation, rollback and resource synchronization',
            false,
            []
    ),

    SPRING_VALIDATION_DATA_BINDING(
            'module:platform:development:programming:framework:spring-framework:validation-data-binding',
            'module/platform/development/programming/framework/spring-framework/validation-data-binding',
            'SERVLET',
            'Spring Framework validation, data binding, type conversion, formatting and Bean Validation integration',
            false,
            []
    ),

    SPRING_WEB(
            'module:platform:development:programming:framework:spring-framework:web',
            'module/platform/development/programming/framework/spring-framework/web',
            'SERVLET',
            'Spring MVC Servlet-stack request processing, binding, validation, error handling, async execution, synchronous HTTP clients and declarative HTTP service interfaces',
            false,
            []
    ),

    SYSTEM_OBSERVABILITY_ALERTING_FUNDAMENTALS(
            'module:infrastructure:system:observability:alerting:fundamentals',
            'module/infrastructure/system/observability/alerting/fundamentals',
            'LIBRARY',
            'Technical alerting fundamentals: rule evaluation, thresholds, grouping, routing, silencing, inhibition, notification delivery and alert lifecycle',
            false,
            []
    ),

    SYSTEM_OBSERVABILITY_ALERTMANAGER(
            'module:infrastructure:system:observability:alerting:alertmanager',
            'module/infrastructure/system/observability/alerting/alertmanager',
            'LIBRARY',
            'Prometheus Alertmanager: alert grouping, routing, receivers, inhibition, silences, deduplication and notification delivery',
            false,
            []
    ),

    SYSTEM_OBSERVABILITY_FUNDAMENTALS(
            'module:infrastructure:system:observability:fundamentals',
            'module/infrastructure/system/observability/fundamentals',
            'LIBRARY',
            'Observability fundamentals: monitoring versus observability, telemetry signals, instrumentation, correlation, context propagation, cardinality and telemetry pipelines',
            false,
            []
    ),

    SYSTEM_OBSERVABILITY_OPENTELEMETRY(
            'module:infrastructure:system:observability:instrumentation:opentelemetry',
            'module/infrastructure/system/observability/instrumentation/opentelemetry',
            'LIBRARY',
            'OpenTelemetry instrumentation and telemetry pipeline concepts: traces, metrics, logs, context propagation, OTLP, SDKs, auto-instrumentation and Collector',
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
