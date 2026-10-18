# Runtime Extensibility

Runtime Extensibility is the Java Advanced area focused on how a Java host extends behavior at runtime through **service contracts**, **provider discovery**, and **plugin boundaries** instead of hard-coding every implementation. The module progresses from SPIs and `ServiceLoader` to plugin architecture, lifecycle, isolation, compatibility, and `ModuleLayer`.

## Why learn this module?

A small application can construct implementations directly. Once a system needs implementations to be deployed, selected, or replaced independently, that coupling becomes a limitation. Java provides the service-provider model and `ServiceLoader` so a host can depend on a contract rather than concrete providers; JPMS and `ModuleLayer` extend the model to modular runtime plugins.

This module separates **discovery** from **selection**, **provider loading** from **plugin lifecycle**, and **runtime extensibility** from neighboring mechanisms such as deep class loading or instrumentation.

## Prerequisites

You should already understand:

- Java Core interfaces or abstract classes, exceptions, resource lifecycle, collections, and generics;
- ClassLoader concepts at the class-identity, visibility, and delegation mental-model level;
- basic Reflection metadata concepts;
- basic JPMS concepts around named modules, `module-info.java`, `requires`, and readability; this module develops the service-directive (`uses` / `provides`), service-binding, and `ModuleLayer` pieces needed for runtime extensibility.

## Learning flow

Study the module in this order:

1. the Runtime Extensibility mental model and module boundaries;
2. SPI and service-provider contracts;
3. `ServiceLoader` discovery, lazy loading, caching, errors, and class-loader context;
4. provider discovery and selection strategies;
5. provider deployment on the class path and module path;
6. plugin architecture and extension points;
7. plugin lifecycle;
8. isolation and shared/private dependency boundaries;
9. host-plugin version compatibility;
10. `Configuration` and `ModuleLayer` for modular runtime plugins;
11. mechanism-selection synthesis and important design pitfalls.

## Module boundaries

Runtime Extensibility owns service-provider contracts, `ServiceLoader`, provider discovery and selection, plugin architecture/lifecycle/isolation/compatibility, and integration with the class path, module path, and `ModuleLayer`.

The following topics are handed off:

- deep class-loading lifecycle, delegation, and custom-loader mechanics → Java Core ClassLoader;
- the complete JPMS descriptor/readability/resolution curriculum → Java Version Module System;
- Java 6 ServiceLoader feature-introduction history → Java Version;
- framework-specific plugin systems → the corresponding framework/module owner;
- bytecode transformation and Java Agents → Instrumentation.

The end goal is to design an extension flow end to end from contract → deployment → discovery → selection → activation → isolation/failure handling while choosing the simplest mechanism that correctly satisfies the requirement.
