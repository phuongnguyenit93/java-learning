# Java Virtual Machine (JVM)

The JVM module builds the runtime foundation for Java Advanced: how the JVM consumes class-file code, forms runtime state, executes methods through frames and operand stacks, manages heap memory and garbage collection, optimizes hot code through interpretation and JIT compilation, and why total JVM-process memory extends beyond the Java heap.

A recurring theme is the distinction between the **contract defined by the JVM Specification** and **implementation details of a concrete VM such as HotSpot**. This prevents a particular garbage-collection algorithm, memory layout, JIT strategy, or VM flag from being mistaken for a universal Java rule.

## Why learn this module?

Java source code hides most of the machinery underneath execution. When you need to reason about why a class cannot yet execute, how a method consumes stack state, when an object can be reclaimed, why hot code behaves differently from startup code, or why process memory grows while the heap is not full, a language-only mental model is no longer enough.

This module provides the foundation that Dynamic Runtime, Instrumentation, Native Interoperability, and Runtime Diagnostics build upon without reteaching the JVM execution model.

## Prerequisites

You should already understand:

- Java Core classes, objects, methods, exceptions, and basic type-system concepts;
- ClassLoader fundamentals at the level of knowing that classes must be loaded before the JVM can use them;
- concurrency fundamentals sufficient to distinguish thread-private runtime state from shared state.

Deep Java Memory Model and happens-before reasoning remain owned by Java Concurrency; deep ClassLoader API and lifecycle mechanics remain owned by Java Core ClassLoader.

## Learning flow

Study the module in this order:

1. understand why the JVM exists and how the class-file format acts as its execution contract;
2. follow class files, bytecode, constant pools, and the loading/linking/initialization boundary before execution;
3. build the runtime-data-area, JVM-stack, and stack-frame model;
4. understand the managed heap, object allocation, reachability, and garbage collection;
5. understand interpretation, profiling, JIT compilation, and adaptive optimization;
6. expand from Java-heap memory to the memory and resources of the whole JVM process;
7. place HotSpot ergonomics and VM options inside the correct specification-vs-implementation boundary;
8. synthesize an end-to-end model from class-file input to observable runtime behavior.

## Module boundaries

JVM owns the execution/runtime mental model, the class-file and bytecode runtime view, runtime data areas, stack frames, object-allocation and garbage-collection concepts, interpreter/JIT/optimization concepts, native-memory awareness, and VM ergonomics at the level required to explain runtime behavior.

The following topics are handed off:

- ClassLoader APIs, delegation, and deep class-loading lifecycle mechanics → Java Core ClassLoader;
- the Java Memory Model, happens-before, and concurrency correctness → Java Concurrency;
- `MethodHandle`, `CallSite`, `VarHandle`, and the runtime-linkage programming model → Dynamic Runtime;
- Java agents and class transformation → Instrumentation;
- JNI, the Foreign Function & Memory API, and native-call programming → Native Interoperability;
- heap dumps, GC logs, JFR, NMT, and troubleshooting workflows → Runtime Diagnostics.

The end goal is to view a Java program as a complete JVM process: trace code from class-file input into execution state, objects from allocation to reclamation, hot code from interpretation to optimized machine code, and recognize when a problem crosses the JVM module boundary into a neighboring owner.
