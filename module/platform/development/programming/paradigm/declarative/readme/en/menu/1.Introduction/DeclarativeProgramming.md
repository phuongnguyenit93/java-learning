# Declarative Programming

## <a id="declarative-what">1. What is Declarative Programming?</a>

Declarative Programming emphasizes describing the **desired result, constraints, or rules** instead of fully specifying each execution step.

```text
WHAT is desired
→ runtime / engine / implementation decides HOW
```

## <a id="declarative-why">2. Why does it exist?</a>

In many domains, the author cares more about intent than execution detail. Separating the two can make expressions shorter and allows the implementation to choose or optimize the execution strategy.

## <a id="declarative-before">3. What if imperative code is used instead?</a>

Imperative code can produce the same result by describing each step explicitly.

Declarative style becomes useful when execution details are repetitive, complex, or better handled by an engine or framework.

## <a id="declarative-solution">4. Mental model</a>

```text
desired condition / result
        ↓
declaration
        ↓
engine chooses execution strategy
```

## <a id="declarative-relations">5. Relationship with other styles</a>

Declarative Programming is an umbrella concept. Functional Programming is often highly declarative, but the two are not identical.

Logic programming, query languages, and rule or configuration systems are also commonly declarative.

This module owns only the **mental model and relationships**; concrete DSLs and technologies remain with their canonical owners.
