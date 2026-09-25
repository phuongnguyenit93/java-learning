# Abstract Class

## <a id="abstract-class-model">Abstract class as partial implementation + state</a>
An abstract class can define instance state, constructors, concrete methods, and abstract methods. It is useful when related subclasses share both a common contract and meaningful implementation/state that belongs in one inheritance hierarchy.

## <a id="abstract-method">Abstract method contract</a>
An abstract method declares a signature without an implementation body. A concrete subclass must implement the inherited abstract contract unless another inherited concrete implementation satisfies it. The method still participates in normal overriding and visibility/throws rules.

## <a id="abstract-constructor">Abstract class constructor and initialization</a>
An abstract class cannot be instantiated directly, but its constructor runs as part of constructing every concrete subclass. It initializes the superclass portion and can enforce superclass invariants. Avoid calling overridable methods from it for the same construction-order reasons as any superclass constructor.

## <a id="abstract-class-limits">Instantiation and inheritance limits</a>
Abstractness prevents direct instantiation, not use as a reference type. A class can extend only one class, abstract or concrete. Use an abstract class when a real base implementation/state is part of the model; do not use it merely to group unrelated helper methods.
