# 📂 README MODULE STRUCTURE (EN)

* **1.ConfigurationMentalModel**
    * [ConfigurationMentalModel](readme/en/menu/1.ConfigurationMentalModel/ConfigurationMentalModel.md)
* **2.PropertySourcesAndPrecedence**
    * [PropertySourcesAndPrecedence](readme/en/menu/2.PropertySourcesAndPrecedence/PropertySourcesAndPrecedence.md)
* **3.ConfigDataFilesAndLocations**
    * [ConfigDataFilesAndLocations](readme/en/menu/3.ConfigDataFilesAndLocations/ConfigDataFilesAndLocations.md)
* **4.ConfigDataImportsAndTrees**
    * [ConfigDataImportsAndTrees](readme/en/menu/4.ConfigDataImportsAndTrees/ConfigDataImportsAndTrees.md)
* **5.ProfilesAndActivation**
    * [ProfilesAndActivation](readme/en/menu/5.ProfilesAndActivation/ProfilesAndActivation.md)
* **6.EnvironmentValueAndPlaceholders**
    * [EnvironmentValueAndPlaceholders](readme/en/menu/6.EnvironmentValueAndPlaceholders/EnvironmentValueAndPlaceholders.md)
* **7.ConfigurationProperties**
    * [ConfigurationProperties](readme/en/menu/7.ConfigurationProperties/ConfigurationProperties.md)
* **8.RelaxedAndComplexBinding**
    * [RelaxedAndComplexBinding](readme/en/menu/8.RelaxedAndComplexBinding/RelaxedAndComplexBinding.md)
* **9.ValidationAndMetadata**
    * [ValidationAndMetadata](readme/en/menu/9.ValidationAndMetadata/ValidationAndMetadata.md)
* **10.DiagnosticsAndSynthesis**
    * [DiagnosticsAndSynthesis](readme/en/menu/10.DiagnosticsAndSynthesis/DiagnosticsAndSynthesis.md)

# Spring Boot Externalized Configuration

This module builds the mental model for how Spring Boot accepts configuration from multiple sources, resolves an effective value, loads Config Data, activates profiles, exposes individual values, and binds structured configuration into typed objects.

## What You Will Learn

You will learn to reason about property-source precedence, packaged and external Config Data, location and import rules, profiles, `Environment`, placeholders, `@Value`, `@ConfigurationProperties`, relaxed and complex binding, validation, configuration metadata, and the failure stages that make configuration problems diagnosable.

## Prerequisites

You should already understand the Spring Boot bootstrap mental model and basic Spring bean/application-context concepts. Detailed Spring Framework `Environment` internals, generic Bean Validation internals, Spring Cloud Config, and production secret-management systems are intentionally outside this module.

## Learning Flow

1. Build the externalized-configuration and `Environment` mental model.
2. Learn how property sources compete and how precedence determines the effective value.
3. Understand Config Data files, default search locations, custom locations, and early location inputs.
4. Add imports, optional resources, extension hints, and configuration trees.
5. Apply profiles and profile-specific configuration without using profiles as a universal deployment switch.
6. Consume individual values through `Environment`, placeholders, and `@Value`.
7. Model grouped configuration with `@ConfigurationProperties`.
8. Bind relaxed names, nested objects, collections, maps, and converted value types.
9. Validate configuration and generate metadata for tooling.
10. Diagnose loading, activation, binding, conversion, and validation failures as one end-to-end resolution pipeline.

## Module Boundary

This module owns Spring Boot configuration loading, precedence, Config Data, profiles, Boot-facing consumption, type-safe binding, validation integration, and metadata. Spring Framework implementation internals, test-only override mechanics, Spring Cloud Config, generic secret management, and deployment-platform configuration remain with their dedicated owners.
