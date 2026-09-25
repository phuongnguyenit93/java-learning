# Abstract Class vs Interface

## <a id="abstract-vs-interface-state">State and constructor differences</a>
An abstract class can own instance fields and constructors that initialize shared state. An interface has no per-instance fields/constructors; its fields are constants. If subclasses need one common implementation state with protected invariants, an abstract class may be appropriate.

## <a id="abstract-vs-interface-inheritance">Single class inheritance vs multiple interface inheritance</a>
A class extends only one class but can implement multiple interfaces. Interfaces therefore compose roles more flexibly across otherwise unrelated class hierarchies, while abstract classes are stronger structural commitments.

## <a id="selection-guidance">When to prefer abstract class or interface</a>
Prefer an interface for a capability/role that many implementations can provide and consumers should depend on. Prefer an abstract class when a shared base implementation/state and controlled extension points belong to the model. They can be used together: an abstract class can implement an interface and provide partial implementation.
