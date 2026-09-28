# Object-Oriented Programming

## <a id="oop-what">1. What is OOP?</a>

Object-Oriented Programming is a paradigm that organizes programs around **objects with identity, state, behavior, and responsibility**, with objects collaborating through clear contracts.

OOP does not mean simply splitting code into many classes. A class is only one mechanism that some languages use to build an object model.

## <a id="oop-why">2. Why does OOP exist?</a>

When data and the rules operating on that data are scattered across a system, changing one business rule can affect many unrelated places.

OOP tries to place related state and behavior behind the appropriate responsibility boundary so that change is easier to contain.

## <a id="oop-before">3. What is the simpler alternative?</a>

Procedural programming can organize software around procedures and separate data structures. This is highly effective for many problems and is not inherently inferior to OOP.

OOP becomes useful when a domain contains many entities or collaborators with non-trivial lifecycle, responsibility, and behavior.

## <a id="oop-solution">4. How does OOP help?</a>

```text
state + behavior
      ↓
responsible object
      ↓
collaboration through contracts
```

Common concepts include encapsulation, abstraction, composition, inheritance/subtyping, and polymorphism.

## <a id="oop-boundary">5. Boundary with Java</a>

This module owns only the **language-neutral OOP paradigm**.

Keywords, access modifiers, class/interface mechanics, and Java-specific dispatch belong to `language/java/core/oop` and related Java Core modules.
