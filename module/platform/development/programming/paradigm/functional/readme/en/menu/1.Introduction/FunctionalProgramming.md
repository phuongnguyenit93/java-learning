# Functional Programming

## <a id="functional-what">1. What is Functional Programming?</a>

Functional Programming is a paradigm that organizes programs around **functions, data transformations, and composition** rather than making mutation and state-changing command sequences the center of the design.

The goal is not to make everything a function, but to make large portions of logic understandable as:

```text
input
→ transformation
→ output
```

## <a id="functional-why">2. Why does it exist?</a>

When logic depends heavily on shared mutable state, the result of one piece of code can be affected by changes occurring elsewhere and at different times.

Functional Programming reduces that dependency by favoring functions with explicit inputs and outputs, limiting side effects, and encouraging immutable data.

## <a id="functional-without">3. What happens without it?</a>

Imperative programming remains completely valid. Programs can mutate variables, update objects, and control flow through statements.

The problem appears when mutation and side effects spread so widely that reasoning, testing, or concurrent execution becomes difficult.

## <a id="functional-solution">4. How does Functional Programming help?</a>

Mental model:

```text
explicit input
    ↓
pure or controlled transformation
    ↓
explicit output
    ↓
compose transformations
```

Important ideas include pure functions, immutability, first-class and higher-order functions, composition, and referential transparency.

## <a id="functional-when">5. When is it useful?</a>

The paradigm is especially useful for data transformations, pipelines, business rules expressible as small functions, and logic that should be easy to test.

It does not eliminate side effects. I/O, databases, networks, and stateful interaction still exist; the goal is to **isolate and control them**.
