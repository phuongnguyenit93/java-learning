# Imperative Programming

## <a id="imperative-what">1. What is Imperative Programming?</a>

Imperative Programming describes a program as a sequence of **commands that change state** over time.

```text
current state
→ command
→ new state
→ next command
```

## <a id="imperative-why">2. Why does it exist?</a>

Computers execute instructions in sequence and often update memory or state while running. Imperative Programming maps naturally to that execution model, making step-by-step algorithms straightforward to express.

## <a id="imperative-before">3. What problem does it solve?</a>

When a problem requires explicit control over operation order, branching, loops, and mutation, imperative style makes each execution step visible.

## <a id="imperative-solution">4. Mental model</a>

```text
do A
then B
if condition → do C
repeat D
update state
```

Common concepts include assignment, mutable state, control flow, procedures, and explicit sequencing.

## <a id="imperative-boundary">5. Boundary with other paradigms</a>

Imperative is not absolutely opposed to OOP or Functional Programming. An object-oriented program may still be highly imperative, and functional code may contain imperative sections.

It is an axis describing **how computation is expressed**, not an exclusive category.
