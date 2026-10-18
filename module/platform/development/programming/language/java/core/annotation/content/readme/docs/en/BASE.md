# Annotations

This module explains annotations as **structured metadata attached to Java program elements**, from built-in annotations to custom annotations and the rules that control retention, targets, and processing.

## Why learn this module?

Annotations are everywhere in the Java ecosystem, but an annotation does not execute behavior by itself. Understanding its lifecycle and consumer prevents confusion between declaration, runtime reflection, and compile-time annotation processing.

## Learning flow

1. the annotation mental model and syntax;
2. built-in annotations;
3. custom annotations;
4. retention policies;
5. targets;
6. meta-annotations;
7. repeatable and inherited annotations;
8. annotation processing;
9. design guidelines for annotation contracts.

## Module boundary

This module owns the Java annotation language model and metadata contract. Reflection consumes metadata at runtime, while annotation processors operate at compile time. The end goal is to know which metadata exists in which phase and which component actually consumes it.
