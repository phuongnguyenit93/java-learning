# Dynamic Runtime

Dynamic Runtime is the Java Advanced module focused on the low-level `java.lang.invoke` primitives used for typed invocation, semantically controlled variable access, and runtime linkage. It bridges ordinary Java code with mechanisms used by the JVM, language runtimes, frameworks, and infrastructure code when behavior must be represented or linked more dynamically than a direct call allows.

## Why learn this module?

Reflection can inspect metadata and invoke members dynamically, but it is not Java's only runtime mechanism. Java also provides `MethodType` + `MethodHandle` for typed invocation, `MethodHandles.Lookup` for capability-based lookup and access checking, `CallSite` + `invokedynamic` for runtime linkage, and `VarHandle` for strongly typed variable access.

This module builds the mental model for how those primitives work together, when they fit, and when ordinary direct Java code or Reflection is the simpler choice.

## Prerequisites

You should already understand:

- Java Core classes, methods, fields, exceptions, and basic type-system concepts;
- Reflection at the metadata and member-access level;
- the JVM at the bytecode-execution and linkage mental-model level;
- concurrency fundamentals before going deep into visibility and memory-ordering semantics for `VarHandle` or mutable call sites.

## Learning flow

Study the module in this order:

1. the Dynamic Runtime mental model and module boundaries;
2. `MethodType`, `MethodHandle`, and signature polymorphism;
3. `MethodHandles.Lookup` and access capabilities;
4. MethodHandle adaptation and composition;
5. `CallSite` and changeable targets;
6. `invokedynamic` and bootstrap linkage;
7. `VarHandle` as the parallel variable-access branch;
8. a final decision model, failure model, and module handoffs.

## Module boundaries

Dynamic Runtime owns typed invocation, typed variable handles, the lookup/access model, MethodHandle adaptation and composition, CallSite, and the invokedynamic mental model.

The following topics are handed off:

- Reflection metadata and member inspection → Java Core Reflection;
- constant-pool structure, bytecode execution, and JVM linkage foundations → JVM;
- deep Java Memory Model and happens-before reasoning → Java Concurrency;
- Java 7 feature-introduction history for Method Handles and invokedynamic → Java Version;
- native memory and foreign-function boundaries → Native Interoperability.

The end goal is to trace a runtime invocation/linkage flow end to end and choose the right primitive without treating `java.lang.invoke` as the default choice for ordinary application code.
