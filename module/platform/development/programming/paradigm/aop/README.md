# 📂 README MODULE STRUCTURE (EN)

* **1.Introduction**
    * [AOP](readme/en/menu/1.Introduction/AOP.md)
* **2.Terminology**
    * [Terminology](readme/en/menu/2.Terminology/Terminology.md)
* **3.ImplementationModels**
    * [ImplementationModels](readme/en/menu/3.ImplementationModels/ImplementationModels.md)
* **4.Tradeoffs**
    * [Tradeoffs](readme/en/menu/4.Tradeoffs/Tradeoffs.md)

# Aspect-Oriented Programming (AOP)

AOP organizes **cross-cutting concerns** such as logging, timing, or auditing when the same policy applies across multiple components. It does not replace business logic or object-oriented programming: it describes added behavior and the execution points where that behavior is composed with the original work.

**Starting point:** familiarity with calls, execution order, and objects collaborating through responsibilities is enough. OOP is a recommended conceptual foundation; *aspect*, *join point*, *pointcut*, *advice*, and *weaving* are introduced here rather than assumed from Spring AOP.

**Learning sequence:** start with the problem of shared policies scattered through business code and compare AOP with explicit helpers, wrappers, and decorators. Build the aspect–join point–pointcut–advice model, learn how added behavior executes before, after, or around the target, and distinguish implementation models and interception boundaries. Finish by evaluating readability, tests, tracing, and a worked choice between an aspect and explicit composition.

**Scope:** this module teaches AOP as a paradigm. Spring proxy and bean mechanics, AspectJ pointcut syntax and compiler/weaver details, and transaction-management internals belong to their technology owners. The broader separation-of-concerns principle is not fully taught here; the focus is its cross-cutting application.
