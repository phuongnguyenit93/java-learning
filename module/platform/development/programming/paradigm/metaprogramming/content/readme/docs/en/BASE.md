# Metaprogramming

Study programs that inspect, generate or transform representations of other programs (or themselves) at compile time or runtime.

## Learning goals

Understand the mental model, motivation, execution mechanisms, suitable applications and trade-offs. This module is **language-independent**; concrete libraries and frameworks are examples, not the curriculum's owner.

## Prerequisites

Basic source code, functions, types and compile/runtime concepts; Java reflection is not required.

## Scope boundaries

Owns conceptual code-as-data, AST, macros, code generation, staging and reflection. Java annotation processors, Lombok, bytecode instrumentation, dynamic proxies and Spring internals belong in language/framework-specific modules.

## Learning path

1. **Programs as data:** Distinguish program representations from application data.
2. **Syntax, ASTs and representations:** Contrast source text, syntax trees, semantic models and bytecode.
3. **Staging and execution phases:** Explain compile-time versus runtime generation, validation and execution.
4. **Macros and hygiene:** Explain macro expansion, unintended variable capture and safe transformations.
5. **Source generation and metadata:** Distinguish annotation processing and generated source from metadata inspection.
6. **Runtime reflection and code generation:** Compare introspection, dynamic proxies and runtime code-generation techniques.
7. **Security, testing and trade-offs:** Assess generated-code provenance, debugging, performance and trust boundaries.

> The roadmap is the high-level source of truth. Knowledge/Menu, Quiz, Interview and experiments belong to subsequent workflow steps and are not marked complete here.
