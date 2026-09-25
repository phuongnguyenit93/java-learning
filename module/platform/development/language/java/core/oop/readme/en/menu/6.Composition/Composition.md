# Composition

## <a id="composition-has-a">Composition and has-a</a>
Composition builds behavior by holding references to collaborating objects rather than inheriting implementation. A service can delegate a responsibility to a strategy, repository, formatter, or policy object and replace that collaborator without changing its own type hierarchy.

## <a id="object-relationships">Dependency, association, aggregation and composition as modeling relationships</a>
These terms describe modeling strength, not four special Java runtime features. A dependency is usually temporary use; association is a longer-lived relationship; aggregation communicates weak/shared ownership; composition communicates strong ownership/lifecycle. Java represents all of them with ordinary references and application conventions.

## <a id="association-dependency">Dependency vs longer-lived association</a>
A method parameter/local collaborator can represent a dependency for one operation, while a field often represents an association that forms part of object state. The distinction helps reason about coupling and lifetime, but should not be forced when it adds no design value.

## <a id="ownership-lifecycle">Ownership/lifecycle strength in aggregation/composition modeling</a>
Composition usually means the parent conceptually owns the part and the part's lifecycle is tied to it; aggregation allows independently existing/shared parts. Java GC does not enforce UML ownership, so the contract must be communicated by API design, mutation rules, and object creation/retention choices.

## <a id="composition-vs-inheritance">Composition vs inheritance trade-offs</a>
Inheritance gives subtype polymorphism and protected/shared implementation but creates tight coupling to a base class. Composition gives runtime configurability and smaller contracts but requires explicit delegation. Prefer composition for behavior reuse unless a true subtype relationship is valuable.

## <a id="delegation">Delegation as behavior reuse</a>
Delegation means forwarding work to another object that owns the relevant behavior. It keeps responsibilities separate and allows substitution through interfaces. Excessive pass-through layers can also become noise, so delegate where it represents a real boundary.
