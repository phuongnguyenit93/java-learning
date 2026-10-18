# Generics

This module builds the mental model for **parametric polymorphism and compile-time type safety** in Java. Generics let one abstraction work across many types while retaining an explicit type contract instead of pushing type checks to runtime.

## Learning flow

1. why Generics exist;
2. generic types and generic methods;
3. bounded type parameters;
4. invariance and subtyping;
5. wildcards and PECS;
6. raw types and legacy boundaries;
7. type erasure and the runtime model;
8. limitations and generic API design.

## Mental model to retain

A generic type argument is primarily a compile-time contract. Because of erasure and invariance, the intuition that `List<Dog>` is a `List<Animal>` is incorrect. A wildcard describes capabilities of a reference, while a type parameter expresses type relationships that a declaration must preserve.

Collections make heavy use of Generics, but this module focuses on type-system semantics and API design.
