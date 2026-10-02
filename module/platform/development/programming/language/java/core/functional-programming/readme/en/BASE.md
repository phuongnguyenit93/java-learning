# Functional Programming in Java

This module introduces how Java represents **behavior as a value** through functional interfaces, lambdas, and method references, then composes that behavior into controlled data-processing pipelines.

## Learning flow

1. why functional style is useful and its core mental model;
2. functional-interface contracts and target typing;
3. lambdas;
4. variable capture, scope, and state;
5. method references;
6. standard functional interfaces;
7. behavior composition and higher-order use;
8. side effects, immutability, and `Optional`;
9. the boundary with the Stream API;
10. trade-offs, pitfalls, and synthesis.

## Why learn this module?

The goal is not to turn Java into a purely functional language. The learner should know when higher-order behavior improves clarity, when captured mutable state makes reasoning harder, and how these abstractions underpin Stream and many framework APIs.

## Module boundary

This module focuses on Java's functional model. Streams appear as an integration boundary; concurrency/parallel execution and reactive programming are separate topics.
