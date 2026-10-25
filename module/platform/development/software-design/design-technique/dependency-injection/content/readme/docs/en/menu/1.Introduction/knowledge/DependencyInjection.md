# Inversion of Control and Dependency Injection

## <a id="ioc-di-what">1. What are IoC and DI?</a>

**Inversion of Control (IoC)** is the principle of moving decisions about creating, selecting, or coordinating dependencies outside the business component.

**Dependency Injection (DI)** is a common technique for implementing that idea: dependencies are supplied from the outside instead of being constructed directly by the component.

## <a id="ioc-di-why">2. Why are they useful?</a>

When a class directly constructs every concrete dependency, creation policy and business behavior become coupled in the same place.

This makes implementation replacement, isolated testing, and application-level composition harder.

## <a id="ioc-di-before">3. What is the simpler approach?</a>

An object can absolutely construct a dependency itself when that dependency is small, stable, and truly an implementation detail.

DI becomes useful when a dependency is a collaborator with its own contract, lifecycle, or environment/test-specific implementation.

## <a id="ioc-di-solution">4. How does DI help?</a>

```text
composition boundary
      ↓ inject
business component
      ↓ uses
dependency contract
```

Creation and wiring are separated from code that uses the dependency.

## <a id="ioc-di-framework">5. DI does not mean Spring</a>

Constructor injection and manual composition are still DI even without a container.

Spring DI is a concrete framework mechanism and belongs to Spring-specific modules rather than owning the DI concept itself.
