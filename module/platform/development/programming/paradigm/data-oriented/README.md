# 📂 README MODULE STRUCTURE (EN)

* **1.DataOrientedProgramming**
    * [DataOrientedProgramming](readme/en/menu/1.DataOrientedProgramming/DataOrientedProgramming.md)
* **2.CodeDataSeparation**
    * [CodeDataSeparation](readme/en/menu/2.CodeDataSeparation/CodeDataSeparation.md)
* **3.GenericData**
    * [GenericData](readme/en/menu/3.GenericData/GenericData.md)
* **4.ImmutableData**
    * [ImmutableData](readme/en/menu/4.ImmutableData/ImmutableData.md)
* **5.DataSchema**
    * [DataSchema](readme/en/menu/5.DataSchema/DataSchema.md)
* **6.DataFlow**
    * [DataFlow](readme/en/menu/6.DataFlow/DataFlow.md)
* **7.JavaPerspective**
    * [JavaPerspective](readme/en/menu/7.JavaPerspective/JavaPerspective.md)
* **8.Tradeoffs**
    * [Tradeoffs](readme/en/menu/8.Tradeoffs/Tradeoffs.md)

# Data-Oriented Programming (DOP)

Data-oriented programming treats application **data as something that can be represented, inspected and transformed independently of the operations that use it**. This module primarily follows the four principles described by Yehonathan Sharvit: separate code and data; use generic, inspectable structures; treat data as immutable; and keep optional data schemas separate from representation.

## Motivation and boundaries

When values are tightly coupled to behavior-bearing objects, reusing the same information across different operations can become difficult. The data-first approach offers simpler inspection and transformation but trades off some encapsulation and static guarantees. It complements, rather than invalidates, object-oriented and functional programming.

The term **DOP** has more than one established usage. The Java/Project Amber perspective emphasizes transparent, immutable and valid data variants (often modeled with records and sealed hierarchies), whereas Sharvit focuses on generic data and independent schemas. Both differ from **data-oriented design (DOD)** focused on cache behavior, memory layout and throughput.

## Starting point

You should know basic values, functions, collections, and the elementary roles of objects and data. The object-oriented and functional paradigm modules supply a useful comparison, but the chapter sequence explicitly introduces DOP's own four principles. No knowledge of Java records, database schemas, JSON Schema libraries or cache-level optimization is assumed.

## Learning journey

1. **Purpose and core model:** the data-first view, motivation, the four principles and terminology boundaries.
2. **Separate code and data:** operations remain reusable, while data remains inspectable.
3. **Generic data structures:** maps, lists, nested shapes, flexible representation and failure risks.
4. **Immutable data:** state changes produce new data versions; application state may still evolve.
5. **Schemas and validation:** describe expected shapes separately and check data at trust boundaries.
6. **Complete application flow:** receive, validate, transform and hand off data while controlling effects.
7. **Java/Project Amber perspective:** recognize its related but distinct typed transparent model without teaching Java syntax.
8. **Design trade-offs:** compare object-centric and functional alternatives and separate DOP from memory-layout DOD.

The chapter Menu contains the stable anchored lessons; this overview guides the route rather than duplicating their future teaching.

## Handoff

Java record/sealed/pattern syntax belongs to Java language/version modules; domain-driven modeling belongs to software-design modules; SQL/ORM, JSON serialization and persistence belong to their owners. Low-level cache, SIMD, layout or ECS optimization is **not** the DOP programming model taught here. The anchored Knowledge chapters linked above already contain the conceptual examples and step-by-step explanations.
