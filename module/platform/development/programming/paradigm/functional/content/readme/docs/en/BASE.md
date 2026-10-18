# Functional Programming

Functional programming organizes computation as transformations of explicit inputs into results. It favors pure functions and immutable values so that the outcome of a calculation is easier to predict. This is a **programming paradigm**, not a requirement to use a particular language or Java library.

## Why learn it

Shared mutable state and effects scattered through business logic make changes, tests, and debugging harder to understand. Functional style offers a way to keep computations predictable while handling real-world input/output and changing application state deliberately. Imperative and object-oriented approaches remain useful and can coexist with functional code.

## Starting point

You should be able to read simple values, variables, conditions, collections and function calls. The earlier imperative and declarative paradigms provide useful comparisons, but this module introduces its own terms—purity, referential transparency, first-class functions and composition—from the beginning. Java lambda syntax and Stream APIs are **not** prerequisites.

## Learning journey

1. **Purpose and mental model:** the input → transformation → output perspective, its motivation, and how it relates to familiar programming styles.
2. **Pure functions:** explicit inputs and observable effects; predictable results and referential transparency.
3. **Immutable values:** snapshots, changes in application state, sharing, and their practical costs.
4. **Functions as values and composition:** higher-order functions, reusable transformations and the conceptual meaning of map/filter/reduce.
5. **Effect boundaries:** separate pure logic from I/O, external services, errors and state coordination.
6. **Design decisions:** evaluate performance, clarity, testing, and the suitability of mixed programming styles.

The chapter Menu contains the actual anchored lessons; this overview is orientation rather than a substitute for those lessons.

## Scope and handoff

We study the general functional *model* through language-neutral reasoning and small conceptual examples. Java functional interfaces, lambdas, method references, `Optional` and JDK Stream mechanics belong to Java language modules. Time-varying reactive streams belong to Reactive Programming; generic data/behavior separation belongs to Data-Oriented Programming. Neither concurrency mechanics nor library operator catalogs are taught here.
