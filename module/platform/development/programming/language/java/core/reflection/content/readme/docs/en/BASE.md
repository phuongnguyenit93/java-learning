# Reflection

This module explains how Java can **inspect metadata and operate on members at runtime** through the Reflection API. It focuses on capabilities, access boundaries, retained type information, and the costs and risks of dynamic access.

## Learning flow

1. the Reflection mental model;
2. class metadata;
3. fields;
4. methods;
5. constructors;
6. dynamic invocation;
7. access control;
8. generic type inspection;
9. dynamic proxies;
10. limitations and risks.

## Why learn this module?

Reflection underpins many frameworks, serializers, mappers, and testing/tooling systems, but excessive use weakens static checking and refactorability. The learner should know when metadata-driven behavior is appropriate and when direct code or a normal API is clearer.

ClassLoader is responsible for bringing classes into the runtime; Annotations provide metadata; Dynamic Runtime/`java.lang.invoke` is a different runtime mechanism with different goals and contracts.
