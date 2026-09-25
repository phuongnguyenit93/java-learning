# Inheritance

## <a id="is-a-subtyping">Inheritance, subtyping and is-a</a>
Class inheritance creates a subtype relationship: a `Child` can be used where its accessible superclass contract is expected. “Is-a” should mean behavioral substitutability, not merely “shares fields”. If the subtype cannot honor parent expectations, inheritance is the wrong reuse mechanism.

## <a id="inherited-state-behavior">Inherited state and behavior</a>
A subclass inherits accessible behavior and has the superclass portion as part of the same object. Private superclass state still exists but is accessed through superclass behavior rather than directly. Constructors are not inherited; subclass construction explicitly/implicitly invokes a superclass constructor.

## <a id="inheritance-coupling">Inheritance coupling and fragile-base risk</a>
Inheritance couples a subclass to superclass implementation assumptions, protected hooks, initialization, and overridable behavior. A change in a base class can unexpectedly affect subclasses. Prefer inheritance when the abstraction truly defines a subtype contract, not only to reuse code.
