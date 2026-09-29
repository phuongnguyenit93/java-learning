# Aspect-Oriented Programming (AOP)

## <a id="aop-what">1. What is AOP?</a>

Aspect-Oriented Programming is a way to organize software by separating behavior that **cuts across many parts of a system** from the primary business logic.

These behaviors are commonly called **cross-cutting concerns**, such as logging, tracing, timing, auditing, or policy checks.

## <a id="aop-why">2. Why does AOP exist?</a>

When the same concern appears in many places, business code can become repetitive and mixed with logic that is not part of the main domain behavior.

For example, dozens of use cases may all need timing and auditing. Repeating that logic in every method makes later policy changes harder and less consistent.

## <a id="aop-without">3. How is the problem solved without AOP?</a>

A simpler approach is to call helpers, wrappers, or decorators explicitly:

```text
business code
→ call logging helper
→ call audit helper
→ run primary logic
```

This is completely valid and is often clearer when the concern appears in only a few places.

## <a id="aop-limit">4. Why can the simpler approach become insufficient?</a>

When one concern spans many modules or execution boundaries, manual helper calls create duplication and depend on developers remembering to apply the rule everywhere.

AOP turns the concern into a separate unit and describes **where it applies** instead of inserting the same logic into every business method.

## <a id="aop-solution">5. How does AOP solve it?</a>

General mental model:

```text
business behavior
        +
cross-cutting behavior
        ↓
composition mechanism
        ↓
effective runtime behavior
```

AOP does not replace business logic. It adds behavior at selected points in an execution model.

## <a id="aop-when">6. When should it be used?</a>

AOP is useful when a concern genuinely crosses many parts of a system and the selection rule can be described clearly.

It should not be used merely to avoid writing a few lines of code. If behavior is a critical business flow that should remain visible in the control flow, explicit code is usually easier to read and maintain.
