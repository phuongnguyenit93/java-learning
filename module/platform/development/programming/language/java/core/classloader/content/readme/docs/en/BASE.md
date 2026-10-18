# Class Loading

This module explains how the JVM finds, defines, and links classes through the **class-loading subsystem**, including why the same binary class name loaded by different defining loaders can represent different runtime types.

## Learning flow

1. the class-loading lifecycle;
2. built-in class loaders;
3. parent delegation;
4. custom class loaders;
5. class identity;
6. context class loaders;
7. resource loading;
8. initialization;
9. unloading and class-loader leaks.

## Why learn this module?

Class loaders underpin plugin architectures, application-server isolation, SPI/resource discovery, and many difficult runtime failures. The module emphasizes delegation, identity, and lifetime rather than merely calling `loadClass`.

## Module boundary

Reflection operates on classes that are already loaded, while the broader JVM module owns execution and memory topics. By the end, the learner should explain which loader defined a class, why apparently identical types can fail a cast, and what can prevent a loader from being unloaded.
