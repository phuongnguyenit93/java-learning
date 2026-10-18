# Java Instrumentation

This module builds an end-to-end model of **Java agents and the JVM Instrumentation mechanism**: how agents are packaged and started, how the JVM provides Instrumentation, where ClassFileTransformer participates in class loading and modification, and why redefine/retransform operations remain constrained by JVM structural rules.

A recurring theme is the boundary between **the instrumentation mechanism** and neighboring domains. Instrumentation owns Java-agent lifecycle and bytecode injection/transformation; JVM owns the class-file and execution model; ClassLoader owns deep class-loading semantics; Runtime Diagnostics owns runtime-evidence interpretation and troubleshooting workflows. Profilers, coverage tools, monitoring systems, and APM agents may use Instrumentation to inject probes and collect data, but that does not make the entire diagnostics or observability domain part of this module.

## Why learn this module?

Reflection and proxies solve only some forms of runtime adaptation. When a tool must intercept class loading, inject probes without changing application source, alter bytecode of already-loaded classes, or operate across many application classes through an agent, the learner needs a lower-level model that is closer to the JVM.

Instrumentation is also the foundation behind many production tools such as profilers, coverage agents, monitoring/APM agents, and other runtime tooling. This module focuses on the **mechanism and its design boundaries**, not on a particular vendor product.

## Prerequisites

Learners should already understand:

- Java Core classes, methods, exceptions, JAR/manifest basics, and resource lifetime;
- JVM fundamentals including class files, bytecode, and runtime execution;
- ClassLoader fundamentals sufficient to understand that a class is loaded in a concrete loader/context before instrumentation acts on it;
- Java Module System fundamentals, especially module readability, exports, and opens, before the redefineModule section;
- enough concurrency fundamentals to recognize that class loading and transformer callbacks can occur across multiple threads.

Deep knowledge of ASM, Byte Buddy, or JVMTI is not required beforehand. They appear only where needed to place Java Instrumentation inside the correct implementation boundary.

## Learning flow

Study the module in this order:

1. understand what Instrumentation is, why it exists, and where its boundary lies;
2. build the Java Agent model and the contract between an agent class and the JVM;
3. understand agent JARs, manifests, entry points, arguments, and capability declarations;
4. follow the three major launch paths: -javaagent/premain, executable-JAR launch with Launcher-Agent-Class, and runtime loading through agentmain/Attach API;
5. understand the Instrumentation API in terms of capabilities, loaded-class discovery, and per-class modifiability;
6. learn the ClassFileTransformer contract;
7. trace the transformation pipeline, transformer ordering, chaining, and retransformation behavior;
8. distinguish redefine from retransform and understand their effects on executing code;
9. understand structural limits, verification/linkage constraints, and unmodifiable classes;
10. place bytecode engineering inside the correct Instrumentation scope;
11. use ASM and Byte Buddy as supporting tools rather than standalone curricula;
12. connect the mechanism to profiling, coverage, monitoring, tracing, and agent-based tooling;
13. evaluate operational safety, dynamic-attach policy, overhead, rollback, and compatibility;
14. synthesize a decision model for choosing Instrumentation versus Reflection/Proxy, Runtime Diagnostics, or a handoff to JVMTI/native agents.

## Module boundaries

Instrumentation owns the Java Agent model, startup/attach lifecycle, java.lang.instrument.Instrumentation, ClassFileTransformer, the transformation pipeline, redefine/retransform, and the operational trade-offs directly associated with agents.

The following topics are handed off:

- class-file format, bytecode semantics, and JVM execution internals → JVM;
- deep class-loading lifecycle and delegation → Java Core ClassLoader;
- deep JPMS/module readability, exports, opens, and module-system semantics → Java Version / Java 9 Module System;
- runtime-evidence interpretation, dumps/JFR/tooling, and troubleshooting workflows → Runtime Diagnostics;
- standalone ASM or Byte Buddy curricula → outside Instrumentation; they appear here only as supporting implementation tools;
- the Class-File API (preview in Java 22/23, standardized in Java 24) → Java Version / Java 24 Class-File API;
- JVMTI and native-agent internals → the native/JVM tooling boundary, referenced only when the Java Instrumentation API is no longer sufficient.

The end goal is to explain a Java Agent end to end: packaging → launch/attach → obtaining Instrumentation → registering transformers → transforming/retransforming/redefining classes → respecting structural and operational constraints → deciding when this mechanism is actually the right tool.
