# Java Core Detailed Curriculum Map

> Curriculum blueprint. This file defines the target Java Core learning structure and pedagogical flow. Some modules may already have implementation/content, but this map remains the planning source for curriculum refactoring and later waves; it is not generated runtime content.

## 1. Scope

This map covers all 16 real modules under `module/platform/development/language/java/core`:

```text
language-basics
numbers
class-object
oop
abstract-interface
object-contract
string
exception
generics
collection
date-time
io
localization
annotation
reflection
classloader
```

The existing chapter paths are treated as the curriculum skeleton and should remain stable unless a later human review explicitly changes the taxonomy.

### Pedagogical model applied by this map

This map now answers both:

```text
WHAT concepts belong in each Java Core module?
        +
HOW should a learner be led through those concepts?
```

The detailed H2/API/Quiz/Interview plans remain the technical coverage baseline, but every module section must also define its beginner entry point, major terminology roles, learning narrative and chapter-to-chapter flow.

The curriculum uses three layers:

```text
Layer 1 — module mental model / roadmap
→ what is this topic?
→ why does it exist?
→ what major terminology will appear?
→ how do the terms connect?
→ what learning order should the reader follow?

Layer 2 — concept / chapter story
→ what problem exists before the concept?
→ why is the concept useful / why did it arise?
→ how does it relate to the module and neighboring concepts?
→ which Java mechanism/type/API expresses it?
→ what focused code demonstrates the idea?

Layer 3 — technical depth
→ syntax / API surface
→ compile-time and runtime behavior
→ rules / edge cases / pitfalls / trade-offs
→ deeper code/runtime evidence
```

The intended learning narrative is:

```text
WHAT → WHY → RELATION → HOW → EVIDENCE → PRACTICE
```

For Vietnamese Knowledge, that chain describes **learning roles**, not mandatory English labels. VI should be written as natural Vietnamese technical prose and may surface the flow as `KHÁI NIỆM → VÌ SAO → MỐI LIÊN HỆ → CƠ CHẾ → MINH CHỨNG → THỰC HÀNH`. Ordinary English explanatory words should be translated when a clear Vietnamese equivalent exists; retain English mainly for Java keywords/API/type/member names and technical terms the learner should recognize/search. On first use, a useful pattern is Vietnamese first with English in parentheses, e.g. `đa hình (polymorphism)`, `đóng gói (encapsulation)`, `kiểu con (subtype)`, then use Vietnamese naturally afterward.

The first chapter or equivalent entry chapter of every nontrivial Java Core module must provide a beginner-readable orientation and terminology roadmap before deep mechanics. Major chapters must explain motivation and chapter relationships before technical rules.

This is especially important where one module mixes domain/design concepts with Java mechanisms. The map explicitly classifies those roles so file placement does not accidentally imply a false conceptual hierarchy.

Do not interpret H2/API/Quiz/Interview coverage counts as proof of pedagogical completeness. Counts describe coverage only; roadmap, motivation, transitions and evidence are separate quality dimensions.

The implementation dependency is:

```text
chapter/H1
    ↓
module mental model / terminology roadmap
    ↓
chapter problem / motivation / transition story
    ↓
anchored H2 Knowledge sections
    ↓
knowledge-metadata.yml
    ↓
Java learning implementation + API experiments where useful
    ↓
Swagger API documentation + exact README relations
    ↓
Quiz
    ↓
Interview
    ↓
integrated coverage review
```

## 2. Global design rules

### 2.1 Three-layer learning contract

Every module must be planned and later authored through the same three layers:

```text
Layer 1 — Module orientation
→ "what is this thing?"
→ why the topic exists
→ major terminology
→ terminology relationships
→ learning order

Layer 2 — Chapter/concept story
→ problem before the concept
→ why the concept is useful
→ relation to the module and neighboring concepts
→ Java mechanism/type/API that expresses it
→ focused code question/evidence

Layer 3 — Technical depth
→ syntax/API
→ compile-time/runtime semantics
→ rules/edge cases
→ pitfalls/trade-offs
→ production/debugging implications when relevant
```

The map uses the following per-module planning fields before the technical Knowledge table:

```text
Entry chapter contract
Major terminology roles
Learning roadmap
Running example / evidence strategy
```

### 2.2 Knowledge first

Every real learning concept must exist in README Knowledge before Quiz/Interview relations or API-to-Knowledge mappings are finalized.

Each Knowledge section uses one stable anchored H2:

```markdown
## <a id="stable-anchor">Localized title</a>
```

VI and EN must use the same anchor id for the same concept while the visible title remains naturally localized.

“Naturally localized” means VI is not expected to mirror EN word-for-word. VI should minimize unnecessary English inside explanatory sentences; EN should remain natural English. Technical meaning, concept order, anchor identity and relation targets must remain equivalent even when the wording and pedagogical headings differ.

The visible H2 title is also the Portal Knowledge label, so it should be concise and scan-friendly. Name the concept or direct learner question rather than encoding a full explanatory sentence. The body owns the detailed claim, motivation, comparison, and caveats. For example, prefer `OOP là gì?`, `Đóng gói là gì?`, `Dynamic Dispatch`, `Rủi ro của kế thừa` and their natural EN equivalents over long statement-style titles. A title wording improvement must preserve the existing stable anchor unless the concept identity itself truly changes.

Anchors should describe concepts, not implementation details. Avoid anchors such as `demo-1`, `example-api`, or controller names.

### 2.3 API is evidence, not curriculum completeness

The target implementation may convert a module from `LIBRARY` to `SERVLET` when meaningful runtime experiments justify API Docs. This map may propose APIs for all 16 modules, but an API is only implemented when it proves or makes observable a real Knowledge concept.

Do not enforce `1 H2 = 1 endpoint`.

Prefer one controller per coherent experiment domain, with several related methods, rather than one controller per chapter or one endpoint per fact.

Typical API response should expose learning evidence, for example:

```text
input
observed values
runtime type/state/order
comparison result
trace/events
exception details when intentionally demonstrated
learning conclusion fields when useful
```

Avoid endpoints whose only result is a static explanation string already present in README.

### 2.4 Swagger relationship contract

For every learning API that survives implementation review:

```text
controller
→ exact README chapter file

methodSignature
→ exact anchored H2
```

Swagger `execution` should explain concept → runtime flow → why → focused code evidence → observation → conclusion.

### 2.5 Quiz design

Quiz focuses primarily on prediction, behavior, constraints, misconceptions and code-reading rather than terminology recall.

Every question must have exactly four stable internal answers `A/B/C/D`, explanation per answer, `correctAnswerId`, `aiGenerated=true`, `reviewed=false`, and only exact relations that genuinely exist.

Suggested question ranges in this map are quality targets, not quotas.

### 2.6 Interview design

Interview is not a prose rewrite of Quiz. It should cover:

```text
mental model
explain why
compare alternatives
runtime behavior
pitfalls
trade-offs
production reasoning
debugging/review reasoning
```

Each generated item begins as `aiGenerated=true`, `reviewed=false`.

### 2.7 Cross-module ownership

Use one primary home per concept. Cross-module links are encouraged; duplicated full curricula are not.

Important boundaries for Java Core:

```text
functional programming / lambda / Optional
→ paradigm/functional

Stream API
→ java/version/java8/stream-api

JPMS
→ java/version/java9/module-system

JVM / GC / JIT / Java Memory Model
→ java/advance/jvm

networking APIs
→ java/advance/networking

security / cryptography APIs
→ java/advance/security-cryptography

threading / concurrency primitives
→ Java/concurrency and paradigm/concurrency modules
```

Core modules may reference those topics only where necessary to explain a boundary.

## 3. Proposed implementation waves

### Wave 1 — Language and object foundation

```text
language-basics
numbers
class-object
oop
abstract-interface
object-contract
string
exception
```

### Wave 2 — Type and data mechanisms

```text
generics
collection
annotation
reflection
classloader
```

### Wave 3 — Standard-library domains

```text
date-time
io
localization
```

Within each wave, stabilize README anchors before implementing relations downstream.

Within each module, however, the order is now:

```text
module orientation / terminology roadmap
        ↓
chapter problem + motivation + transition story
        ↓
stable Knowledge anchors + technical depth
        ↓
API evidence
        ↓
Quiz / Interview
```

Do not start the anchor/API/assessment phase from the chapter names alone. The learning narrative is an upstream input.

## 4. Module detail maps

Each module section now has two planning layers before its technical Knowledge map:

```text
Layer 1 — Module orientation
→ entry chapter contract
→ major terminology roles
→ module learning roadmap
→ running-example/evidence strategy

Layer 2 — Chapter story
→ the learner question/problem each chapter answers
→ why that chapter belongs at that point in the sequence

Layer 3 — Technical map
→ anchored H2 sections
→ API experiments
→ Quiz coverage
→ Interview coverage
```

The detailed sections below are therefore not just topic inventories; they are the blueprint for later README refactoring.


### 4.1 `language-basics`

**API applicability:** Yes — runtime type/casting/pass-by-value/array/method behavior are observable.

**Suggested assessment size:** Quiz 30–40; Interview 20–28. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.PrimitiveReference/PrimitiveReference.md` must first orient the learner to Java's basic execution/value model before going into primitive/reference details. It should answer: what values exist in Java, how variables hold them, how methods consume them, and why later topics such as casting, arrays, null and dispatch depend on this model.

**Major terminology roles:**

```text
primitive / reference
→ value-model foundation

scope / lifetime
→ where names and values exist

conversion / casting / boxing
→ how values cross type boundaries

control flow / methods
→ how execution moves and behavior is invoked

array / null / package
→ important language/runtime boundaries

compile-time type vs runtime type
→ bridge from basic syntax into Java's type system and later OOP
```

**Learning roadmap:**

```text
What kind of values does Java manipulate?
Primitive vs Reference
        ↓
Where do values live and which name can see them?
Variables & Scope
        ↓
How do primitives participate in object APIs?
Wrapper / Boxing
        ↓
How are values combined and converted?
Operators → Casting
        ↓
How does execution choose a path?
Control Flow
        ↓
How is reusable behavior called?
Methods → Varargs → Pass-by-Value
        ↓
How does Java represent repeated values?
Arrays
        ↓
How are names organized and access bounded?
Packages / Imports
        ↓
What does "no object" mean for a reference?
Null
        ↓
How do declared type, runtime type and dispatch fit together?
Type System Mental Model
```

**Running example / evidence strategy:** use a few tiny recurring values (`int`, `Integer`, `String`, a simple `User` object and arrays of them) and evolve the same calls through assignment, conversion, method invocation, mutation/reassignment and runtime-type checks. Avoid inventing a domain model just to demonstrate syntax.

#### Layer 2 — Chapter story

Each chapter should answer one learner question before technical rules:

```text
PrimitiveReference     → What does a Java variable actually hold?
VariablesScope         → Where can that variable be used, and for how long?
WrapperBoxing          → How can primitive values participate in object-oriented/generic APIs?
Operators              → How are values combined, compared and promoted?
Casting                → What happens when one type must become another?
ControlFlow            → How does the program choose what executes next?
Methods                → How is behavior named, selected and invoked?
Varargs                → How can a method accept a variable number of arguments?
PassByValue            → What exactly gets copied into a method call?
Arrays                 → How does Java model a fixed-size sequence of values?
PackagesImports        → How are names grouped and visibility bounded?
Null                   → What does the absence of an object mean at runtime?
TypeSystemMentalModel  → Which guarantees are compile-time, and which checks happen at runtime?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.PrimitiveReference/PrimitiveReference.md` | `#primitive-vs-reference-model` — Primitive vs reference value model<br>`#primitive-ranges-and-defaults` — Primitive ranges, literals and defaults<br>`#reference-value-semantics` — What a reference value actually stores |
| `2.VariablesScope/VariablesScope.md` | `#variable-kinds-and-lifetime` — Local, parameter, field and static variable lifetime<br>`#scope-and-shadowing` — Scope, shadowing and name resolution<br>`#definite-assignment` — Definite assignment rules |
| `3.WrapperBoxing/WrapperBoxing.md` | `#wrapper-types` — Wrapper types and object semantics<br>`#boxing-unboxing` — Boxing and unboxing<br>`#wrapper-caching` — Wrapper caches and identity pitfalls<br>`#unboxing-null` — Null unboxing and NullPointerException |
| `4.Operators/Operators.md` | `#numeric-promotion` — Numeric promotion<br>`#short-circuit-operators` — Short-circuit boolean operators<br>`#bitwise-shift` — Bitwise and shift operators<br>`#precedence-side-effects` — Precedence, evaluation order and side effects |
| `5.Casting/Casting.md` | `#primitive-casting` — Widening and narrowing primitive conversions<br>`#reference-upcast-downcast` — Reference upcast and downcast<br>`#instanceof-safe-cast` — instanceof and safe casting<br>`#class-cast-failure` — ClassCastException boundaries |
| `6.ControlFlow/ControlFlow.md` | `#branching-model` — if/switch branching model<br>`#loop-control` — for/while/do-while and break/continue<br>`#switch-expression` — switch expression and yield<br>`#control-flow-pitfalls` — Control-flow readability and common pitfalls |
| `7.Methods/Methods.md` | `#method-signature` — Method signature, parameters and return<br>`#method-invocation-conversion` — Method invocation conversions: identity, primitive/reference widening, boxing/unboxing and varargs applicability<br>`#overload-resolution-phases` — Overload resolution phases and why fixed-arity candidates are considered before varargs fallback<br>`#most-specific-overload` — Selecting the most-specific applicable overload<br>`#null-overload-ambiguity` — `null` arguments and ambiguous unrelated reference overloads<br>`#method-call-evaluation` — Argument evaluation order<br>`#recursion-stack` — Recursion and call-stack cost |
| `8.Varargs/Varargs.md` | `#varargs-array-model` — Varargs are arrays<br>`#varargs-overload` — Varargs and overload resolution<br>`#varargs-generics-warning` — Generic varargs and heap-pollution boundary |
| `9.PassByValue/PassByValue.md` | `#java-pass-by-value` — Java is always pass-by-value<br>`#reference-copy-mutation` — Copied references and visible object mutation<br>`#reassignment-vs-mutation` — Parameter reassignment vs object mutation |
| `10.Arrays/Arrays.md` | `#array-type-model` — Array type, length and covariance<br>`#array-initialization` — Array creation and initialization<br>`#array-covariance-risk` — Array covariance and ArrayStoreException<br>`#multidimensional-arrays` — Multidimensional arrays are arrays of arrays |
| `11.PackagesImports/PackagesImports.md` | `#package-namespace` — Packages as namespaces<br>`#import-resolution` — Imports and name resolution<br>`#static-import` — Static import<br>`#package-access` — Package-private access boundary |
| `12.Null/Null.md` | `#null-reference` — null as a reference value<br>`#null-dereference` — Dereference failure<br>`#null-comparison` — Null comparison and control flow<br>`#null-api-design` — Nullability as an API-design concern |
| `13.TypeSystemMentalModel/TypeSystemMentalModel.md` | `#compile-time-vs-runtime-type` — Compile-time type vs runtime type<br>`#assignment-compatibility` — Assignment compatibility<br>`#overload-vs-override-dispatch` — Overload selection vs override dispatch<br>`#type-system-boundaries` — Type-system guarantees and runtime checks |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `TypeSystemController` | `inspectRuntimeType(Object)` | `#compile-time-vs-runtime-type` | Show declared/observed runtime type and instanceof results. |
| `CastingController` | `safeAndUnsafeCast(String)` | `#reference-upcast-downcast` | Compare legal upcast, guarded downcast and failing cast. |
| `PassByValueController` | `mutateAndReassign()` | `#reference-copy-mutation` | Trace object mutation vs local reference reassignment. |
| `WrapperController` | `wrapperIdentity()` | `#wrapper-caching` | Compare == and equals across cached/non-cached wrappers. |
| `ArrayController` | `covarianceFailure()` | `#array-covariance-risk` | Demonstrate covariant assignment and runtime ArrayStoreException. |
| `MethodDispatchController` | `overloadVsOverride()` | `#overload-vs-override-dispatch` | Show compile-time overload choice vs runtime override dispatch. |
| `OverloadResolutionController` | `selectionMatrix()` | `#overload-resolution-phases` | Run a fixed set of overload calls and expose which overload wins across widening, boxing and varargs cases; include a documented compile-time-only ambiguous `null` example rather than trying to execute uncompilable code. |
| `OperatorController` | `shortCircuit()` | `#short-circuit-operators` | Expose evaluation trace proving short-circuit behavior. |

#### Quiz coverage

prediction of casts/promotions; wrapper identity; short-circuit/evaluation order; method-invocation conversion; overload-resolution phases and most-specific selection; `null` overload ambiguity; pass-by-value; array covariance; null/unboxing; compile-time vs runtime type.

#### Interview coverage

explain Java pass-by-value; overload applicability/specificity and why widening/boxing/varargs produce different results; overload vs override; primitive/reference model; array covariance trade-off; wrapper pitfalls; definite assignment; null/API design; runtime checks vs compile-time guarantees.


### 4.2 `numbers`

**API applicability:** Yes — numeric edge cases are ideal deterministic experiments.

**Suggested assessment size:** Quiz 28–36; Interview 18–24. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.NumericModel/NumericModel.md` must explain that Java has several numeric representations because no single representation can simultaneously provide unlimited range, exact decimal behavior, high performance and compact storage.

**Major terminology roles:**

```text
integer types
→ exact whole-number arithmetic within a fixed range

floating point
→ approximate binary representation for wide-range real-number computation

BigInteger
→ arbitrary-range integer arithmetic

BigDecimal
→ explicit decimal value + scale model for exact decimal workflows

precision / scale / rounding
→ policies controlling representation and arithmetic outcome

Random / SecureRandom
→ different randomness contracts; not interchangeable
```

**Learning roadmap:**

```text
What kinds of numeric problems exist?
Numeric Model
        ↓
What goes wrong with fixed-width integers?
Integer Overflow
        ↓
Why does 0.1 + 0.2 surprise people?
Floating Point
        ↓
What if integer range is not enough?
BigInteger
        ↓
What if exact decimal semantics matter?
BigDecimal
        ↓
How do precision and scale affect a decimal value?
Precision & Scale
        ↓
When and how should results be rounded?
Rounding
        ↓
Why can numerically equal BigDecimal values behave differently in equality/collections?
BigDecimal Comparison
        ↓
Which math helpers already exist?
Math
        ↓
How do pseudo-random and security randomness differ?
Random → SecureRandom
```

**Running example / evidence strategy:** reuse three value families: counters/IDs for integers, sensor/scientific values for floating point, and money/rates for BigDecimal. Use code to make representation failures visible before introducing the safer alternative.

#### Layer 2 — Chapter story

```text
NumericModel            → Which numeric representation matches which problem?
IntegerOverflow         → Why can "exact" integer arithmetic still produce a wrong wrapped result?
FloatingPoint           → Why are many decimal fractions only approximations in binary?
BigInteger              → How do we remove fixed-width integer range limits?
BigDecimal              → How do we represent decimal quantities with explicit scale?
PrecisionScale          → What do precision and scale actually control?
Rounding                → Why is rounding a policy decision rather than a formatting afterthought?
BigDecimalComparison    → Why do equals and numerical comparison answer different questions?
Math                    → Which common numeric operations should use standard helpers?
Random                  → What does deterministic pseudo-randomness mean?
SecureRandom            → When does unpredictability become part of the contract?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.NumericModel/NumericModel.md` | `#numeric-type-model` — Java numeric type model<br>`#integer-vs-floating` — Integer vs floating-point semantics<br>`#numeric-conversions` — Numeric conversions and promotion |
| `2.IntegerOverflow/IntegerOverflow.md` | `#integer-overflow-wraparound` — Integer overflow and wraparound<br>`#checked-arithmetic` — Checked arithmetic with exact methods<br>`#boundary-values` — MIN/MAX boundary reasoning |
| `3.FloatingPoint/FloatingPoint.md` | `#binary-floating-point` — Binary floating-point representation<br>`#precision-rounding-error` — Precision and rounding error<br>`#nan-infinity-negative-zero` — NaN, infinity and negative zero<br>`#floating-point-comparison` — Floating-point comparison strategies |
| `4.BigInteger/BigInteger.md` | `#big-integer-model` — Arbitrary-precision integer model<br>`#big-integer-immutability` — BigInteger immutability<br>`#big-integer-operations` — Core arithmetic and conversion boundaries |
| `5.BigDecimal/BigDecimal.md` | `#big-decimal-model` — BigDecimal unscaled value and scale<br>`#big-decimal-construction` — String/valueOf/double construction<br>`#big-decimal-arithmetic` — Arithmetic and non-terminating division |
| `6.PrecisionScale/PrecisionScale.md` | `#precision-vs-scale` — Precision vs scale<br>`#scale-transformations` — setScale, movePoint and normalization<br>`#math-context` — MathContext and precision control |
| `7.Rounding/Rounding.md` | `#rounding-modes` — RoundingMode semantics<br>`#rounding-at-boundaries` — When rounding actually occurs<br>`#financial-rounding-policy` — Rounding policy as domain decision |
| `8.BigDecimalComparison/BigDecimalComparison.md` | `#big-decimal-equals` — BigDecimal equals includes scale<br>`#big-decimal-compareto` — compareTo numerical comparison<br>`#big-decimal-collections` — BigDecimal keys/sorting implications |
| `9.Math/Math.md` | `#math-core-functions` — Core Math functions<br>`#exact-arithmetic-methods` — Exact integer arithmetic helpers<br>`#strictmath-boundary` — Math vs StrictMath boundary |
| `10.Random/Random.md` | `#pseudo-random-model` — Pseudo-random model and seed<br>`#threadlocal-random-boundary` — Random vs ThreadLocalRandom boundary<br>`#random-not-security` — Why ordinary PRNG is not security |
| `11.SecureRandom/SecureRandom.md` | `#secure-random-purpose` — SecureRandom purpose<br>`#entropy-seeding` — Entropy and seeding mental model<br>`#security-boundary` — Security usage boundary and handoff to cryptography module |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `IntegerArithmeticController` | `overflow()` | `#integer-overflow-wraparound` | Return MAX_VALUE + 1 plus checked addExact result. |
| `FloatingPointController` | `precision()` | `#precision-rounding-error` | Expose 0.1 + 0.2, decimal rendering and tolerance comparison. |
| `FloatingPointController` | `specialValues()` | `#nan-infinity-negative-zero` | Compare NaN/infinity/-0.0 behavior. |
| `BigDecimalController` | `construction()` | `#big-decimal-construction` | Compare new BigDecimal(double), String and valueOf. |
| `BigDecimalController` | `comparison()` | `#big-decimal-equals` | Show equals vs compareTo and scale. |
| `BigDecimalController` | `divisionAndRounding()` | `#rounding-modes` | Show non-terminating division failure and explicit rounding. |
| `RandomController` | `seededSequence()` | `#pseudo-random-model` | Show deterministic seeded PRNG vs SecureRandom boundary. |

#### Quiz coverage

overflow prediction; floating-point special values; BigDecimal construction/scale/equals; rounding modes; exact arithmetic; PRNG vs SecureRandom.

#### Interview coverage

why money uses BigDecimal; equals vs compareTo implications; binary floating-point; overflow handling; precision vs scale; rounding policy; SecureRandom boundary.


### 4.3 `class-object`

**API applicability:** Yes — initialization, aliasing, copy and enum behavior can be traced safely.

**Suggested assessment size:** Quiz 36–48; Interview 24–32. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.ClassObject/ClassObject.md` must start from the simplest question: what is a class, what is an object, and why Java groups state and behavior into instances. The chapter must introduce the vocabulary used by all later chapters before explaining object lifecycle details.

**Major terminology roles:**

```text
class
→ blueprint/type that defines state and behavior

object / instance
→ runtime entity with identity and state

constructor / initialization
→ how a valid object comes into existence

this / super
→ current instance and superclass construction/member context

static / instance
→ class-level vs object-level state/behavior

aliasing / copying / immutability
→ how object references and mutable state are shared or isolated
```

**Learning roadmap:**

```text
What are class and object?
Class & Object
        ↓
How is an object created in a valid state?
Constructor
        ↓
How does construction move through current/superclass context?
this / super
        ↓
Who is allowed to see or change members?
Access Modifiers
        ↓
Which members belong to the class and which to each object?
static / final
        ↓
What runs before constructor bodies?
Initialization Blocks → Initialization Order
        ↓
What really happens during object creation?
Object Creation Lifecycle
        ↓
How can classes be nested and capture surrounding context?
Nested / Inner Classes
        ↓
What common contract does every object inherit?
Object Class
        ↓
How does Java model a fixed set of typed constants?
Enum
        ↓
What does copying an object actually mean?
Copy Semantics
        ↓
Why can two references accidentally share mutable state?
Aliasing & Mutability
        ↓
How do we design objects that are safe to share?
Immutability & Defensive Copy
```

**Running example / evidence strategy:** evolve a small `BankAccount`/`Profile`-style class from plain fields into constructor-established invariants, static/instance state, nested helpers, copying and immutable variants. Keep the same object identity visible across chapters to make aliasing and lifecycle concrete.

#### Layer 2 — Chapter story

```text
ClassObject                  → What is the difference between a type definition and a runtime object?
Constructor                  → How does an object establish valid initial state?
ThisSuper                    → How does one object participate in constructor/member chains?
AccessModifier               → Which parts of object state/behavior should callers be allowed to access?
StaticFinal                  → What belongs to the class versus each instance, and what can no longer change?
InitializationBlocks         → What initialization work exists outside constructor bodies?
InitializationOrder          → In what order do class, superclass, field and constructor initialization steps run?
ObjectCreationLifecycle      → What complete sequence turns allocation into a usable object?
NestedInnerClass             → When does a type logically belong inside another type and what context can it capture?
ObjectClass                  → Which universal object behaviors does Java provide?
Enum                         → How do we represent a closed set of domain values as real types?
CopySemantics                → Does copying duplicate an object, its fields, or merely a reference?
ObjectAliasingMutability     → What breaks when multiple references can mutate the same state?
ImmutabilityDefensiveCopy    → How can ownership and state sharing be made safer?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.ClassObject/ClassObject.md` | `#class-object-model` — Class vs object mental model<br>`#fields-methods-state` — State, behavior and instance identity<br>`#object-reference-lifecycle` — Reference reachability and object lifetime boundary |
| `2.Constructor/Constructor.md` | `#constructor-purpose` — Constructor purpose and invariants<br>`#constructor-overloading` — Constructor overloading and chaining<br>`#default-constructor` — Default constructor rules<br>`#constructor-exceptions` — Constructor failure and partially-created state boundary |
| `3.ThisSuper/ThisSuper.md` | `#this-reference` — this reference<br>`#super-access` — super member/constructor access<br>`#constructor-chaining-order` — this()/super() constructor chaining rules |
| `4.AccessModifier/AccessModifier.md` | `#access-levels` — private/package/protected/public<br>`#protected-cross-package` — Protected access across packages<br>`#encapsulation-boundary` — Access control as encapsulation boundary |
| `5.StaticFinal/StaticFinal.md` | `#static-vs-instance` — Static vs instance members<br>`#final-variable-reference` — final primitive/reference semantics<br>`#static-initialization` — Static member initialization<br>`#constants-design` — Constants and compile-time constants |
| `6.InitializationBlocks/InitializationBlocks.md` | `#static-initializer` — Static initializer<br>`#instance-initializer` — Instance initializer<br>`#initializer-use-cases` — Initializer use cases and readability |
| `7.InitializationOrder/InitializationOrder.md` | `#class-initialization-order` — Static initialization order<br>`#instance-initialization-order` — Instance field/block/constructor order<br>`#inheritance-initialization-order` — Parent/child initialization order |
| `8.ObjectCreationLifecycle/ObjectCreationLifecycle.md` | `#allocation-initialization-construction` — Allocation → initialization → construction mental model<br>`#constructor-dynamic-dispatch-risk` — Calling overridable methods during construction<br>`#this-escape` — this escape during construction |
| `9.NestedInnerClass/NestedInnerClass.md` | `#static-nested-class` — Static nested class<br>`#inner-class` — Inner class and outer instance<br>`#local-anonymous-class` — Local and anonymous classes<br>`#capture-semantics` — Captured local variables |
| `10.ObjectClass/ObjectClass.md` | `#object-root-type` — Object as root reference type<br>`#object-core-methods` — getClass/toString/equals/hashCode overview<br>`#clone-finalize-boundary` — Legacy clone/finalization boundary and safer alternatives |
| `11.Enum/Enum.md` | `#enum-type-model` — Enum constants are instances<br>`#enum-fields-constructors` — Enum fields, constructors and methods<br>`#enum-interface` — Enum implementing interfaces<br>`#enum-constant-specific` — Constant-specific behavior<br>`#enum-values-valueof` — values/valueOf/name/ordinal boundaries |
| `12.CopySemantics/CopySemantics.md` | `#reference-copy` — Reference copy<br>`#shallow-copy` — Shallow copy<br>`#deep-copy` — Deep copy<br>`#copy-strategies` — Copy constructor/factory/manual mapping strategies |
| `13.ObjectAliasingMutability/ObjectAliasingMutability.md` | `#aliasing-model` — Multiple references to one mutable object<br>`#shared-mutable-state` — Shared mutable state consequences<br>`#aliasing-in-collections` — Aliasing through collections and returned references |
| `14.ImmutabilityDefensiveCopy/ImmutabilityDefensiveCopy.md` | `#immutable-object-design` — Immutable object design<br>`#defensive-copy-input` — Defensive copy on input<br>`#defensive-copy-output` — Defensive copy on output<br>`#deep-immutability` — Shallow vs deep immutability |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `InitializationController` | `traceOrder()` | `#inheritance-initialization-order` | Return deterministic static/instance/constructor trace. |
| `ConstructionController` | `constructorDispatchRisk()` | `#constructor-dynamic-dispatch-risk` | Show overridable method seeing uninitialized subclass state. |
| `AliasingController` | `mutateAlias()` | `#aliasing-model` | Show two references observing same mutation. |
| `CopyController` | `shallowVsDeep()` | `#shallow-copy` | Compare nested mutable state after shallow/deep copy. |
| `ImmutabilityController` | `defensiveCopy()` | `#defensive-copy-output` | Demonstrate leaked vs copied mutable collection. |
| `EnumController` | `enumBehavior()` | `#enum-constant-specific` | Show fields/methods/interface/per-constant behavior. |
| `NestedClassController` | `capture()` | `#capture-semantics` | Expose outer/captured-state relationship. |

#### Quiz coverage

initialization order; constructor chaining; access rules; static/final; nested/inner capture; enum semantics; aliasing; copy depth; immutability and defensive copy.

#### Interview coverage

class vs object; constructor invariants; this escape; protected semantics; static/final misconceptions; initialization order; aliasing; immutable design; enum design; clone alternatives.


### 4.4 `oop`

**API applicability:** Yes — dispatch/substitutability/composition are observable; keep examples domain-neutral.

**Suggested assessment size:** Quiz 24–32; Interview 18–24. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.MentalModel/MentalModel.md` must explicitly answer **"OOP là gì?"** before naming pillars. It should explain OOP as organizing software around objects that own state, behavior and responsibilities, then show why the later concepts exist as tools for managing object boundaries and collaboration.

**Major terminology roles:**

```text
encapsulation
→ OOP design concept: an object protects its state/invariants behind behavior

abstraction
→ OOP design concept: expose the contract a caller needs, hide irrelevant implementation

inheritance
→ subtype/reuse mechanism; one way to establish an is-a relationship

polymorphism
→ OOP design/runtime concept: one abstraction can exhibit multiple implementations

overriding + dynamic dispatch
→ Java mechanisms that make subtype polymorphism observable at runtime

overloading
→ compile-time Java mechanism; taught nearby mainly because it is commonly confused with overriding

composition / delegation
→ object-collaboration alternative to inheritance for behavior reuse and change
```

**Learning roadmap:**

```text
Why organize code around objects at all?
OOP Mental Model
        ↓
If an object owns state, how does it keep that state valid?
Encapsulation
        ↓
How can one type be treated as another compatible type?
Inheritance / Subtyping
        ↓
How can the same contract execute different behavior?
Polymorphism
        ↓
How does Java distinguish compile-time method choice from runtime behavior choice?
Overloading vs Overriding / Dispatch vs Hiding
        ↓
What if inheritance creates too much coupling?
Composition / Delegation
        ↓
How do callers depend on essential behavior instead of implementation detail?
Abstraction
```

**Running example / evidence strategy:** use one small evolving model such as `PaymentMethod`/`CheckoutService` or `Account` variants. Start with exposed state and branching, then progressively introduce encapsulation, subtype contracts, overriding/dynamic dispatch and finally composition. The example must make each concept solve a visible problem rather than merely demonstrate syntax.

#### Layer 2 — Chapter story

```text
MentalModel
→ Start with procedural data + scattered rules and ask why object ownership/responsibility can localize change.

Encapsulation
→ Public mutable state lets any caller violate invariants; move rules behind object behavior.

Inheritance
→ Repeated behavior and subtype contracts create a need for "is-a" relationships, but inheritance also creates coupling.

Polymorphism
→ If callers depend on a common supertype, they should not need type-specific if/else for every implementation.

OverloadingOverriding
→ Once multiple methods/types exist, distinguish compile-time overload selection from runtime override dispatch and from static/field hiding.

Composition
→ When inheritance is only used for reuse, collaboration/delegation often produces a smaller and more replaceable design.

Abstraction
→ Pull the lesson together: callers should depend on stable responsibilities/contracts, not concrete implementation details.
```

**Authoring note:** overloading must never be introduced as an OOP "pillar". Its place in this module is comparative: it helps explain why overriding/dynamic dispatch is the mechanism relevant to runtime polymorphism.

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.MentalModel/MentalModel.md` | `#oop-object-collaboration` — Objects as collaborating state + behavior<br>`#oop-boundaries` — Responsibility and boundaries<br>`#oop-vs-procedural` — OOP vs procedural decomposition trade-off |
| `2.Encapsulation/Encapsulation.md` | `#encapsulation-model` — Encapsulation is more than private fields<br>`#invariant-protection` — Protecting invariants<br>`#tell-dont-ask-boundary` — Behavior-oriented API vs data exposure |
| `3.Inheritance/Inheritance.md` | `#is-a-subtyping` — Inheritance, subtyping and is-a<br>`#inherited-state-behavior` — Inherited state/behavior<br>`#inheritance-coupling` — Inheritance coupling and fragile-base risk |
| `4.Polymorphism/Polymorphism.md` | `#subtype-polymorphism` — Subtype polymorphism<br>`#dynamic-dispatch` — Runtime dynamic dispatch<br>`#substitutability` — Substitutability and behavioral expectations |
| `5.OverloadingOverriding/OverloadingOverriding.md` | `#overloading-compile-time` — Overloading is compile-time selection<br>`#overriding-runtime` — Overriding is runtime dispatch<br>`#covariant-return` — Covariant return types<br>`#override-rules` — Visibility/final/static/private overriding boundaries<br>`#static-method-hiding` — Static methods are hidden, not overridden<br>`#field-hiding` — Field selection follows the compile-time reference type, not runtime dispatch<br>`#dispatch-vs-hiding` — Instance-method dispatch vs static-method and field hiding |
| `6.Composition/Composition.md` | `#composition-has-a` — Composition and has-a<br>`#object-relationships` — Dependency, association, aggregation and composition as object-modeling relationships rather than special Java runtime constructs<br>`#association-dependency` — Dependency vs longer-lived association<br>`#ownership-lifecycle` — Ownership/lifecycle strength in aggregation/composition modeling<br>`#composition-vs-inheritance` — Composition vs inheritance trade-offs<br>`#delegation` — Delegation as behavior reuse |
| `7.Abstraction/Abstraction.md` | `#abstraction-model` — Abstraction exposes essential behavior<br>`#program-to-abstraction` — Program to abstraction<br>`#abstraction-leak` — Leaky abstractions and trade-offs |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `PolymorphismController` | `dispatch()` | `#dynamic-dispatch` | Base reference invoking subtype implementation. |
| `DispatchController` | `overloadVsOverride()` | `#overloading-compile-time` | Contrast overload compile-time choice with override runtime dispatch. |
| `DispatchController` | `dispatchVsHiding()` | `#dispatch-vs-hiding` | With `Parent x = new Child()`, compare an overridden instance method with a hidden static method and hidden field to make runtime dispatch vs compile-time selection observable. |
| `CompositionController` | `strategySwap()` | `#composition-vs-inheritance` | Swap composed behavior without changing consumer. |
| `EncapsulationController` | `invariantProtection()` | `#invariant-protection` | Compare controlled behavior with raw state mutation attempt. |
| `SubstitutabilityController` | `substituteImplementations()` | `#substitutability` | Run same contract against multiple implementations and compare behavior. |

#### Quiz coverage

is-a vs has-a; dynamic dispatch; overload/override rules; static-method hiding; field hiding; dispatch vs hiding; substitutability; dependency/association/aggregation/composition modeling; ownership/lifecycle; composition trade-offs; encapsulation/invariants; abstraction leakage.

#### Interview coverage

four OOP pillars without slogans; inheritance vs composition; LSP-style substitutability reasoning; encapsulation vs data hiding; runtime dispatch vs static/field hiding; association/aggregation/composition ownership semantics; fragile base class; abstraction trade-offs.

### 4.5 `abstract-interface`

**API applicability:** Yes — method dispatch/default-method conflicts/interface contracts are observable.

**Suggested assessment size:** Quiz 22–30; Interview 16–22. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.AbstractClass/AbstractClass.md` must begin with a short module-level orientation before deep abstract-class rules: what problem "programming to a contract" solves, why Java has both abstract classes and interfaces, and how the remaining chapters compare the two mechanisms.

**Major terminology roles:**

```text
abstract class
→ partial implementation + shared state/behavior + incomplete contract

interface
→ explicit capability/role contract with multiple-implementation flexibility

default/static/private interface methods
→ interface evolution and reusable contract-side behavior

multiple interface inheritance
→ combining roles without multiple class inheritance

default-method conflict
→ Java rule for ambiguous inherited behavior
```

**Learning roadmap:**

```text
Why do we need a type that defines behavior without being directly instantiable?
Abstract Class
        ↓
What if we want a contract without committing to one class hierarchy?
Interface
        ↓
When should each mechanism be chosen?
Abstract Class vs Interface
        ↓
How can contracts themselves form hierarchies?
Interface Inheritance
        ↓
How can interfaces evolve behavior without breaking every implementation?
Default / Static / Private Interface Methods
        ↓
What happens when multiple inherited interface behaviors collide?
Multiple Inheritance / Conflict Resolution
```

**Running example / evidence strategy:** use one capability family such as `PaymentMethod`, `Refundable`, and a partially implemented base class. Let the example demonstrate why shared state favors an abstract class while cross-cutting capability contracts favor interfaces.

#### Layer 2 — Chapter story

```text
AbstractClass          → How do we share implementation/state while still requiring subclasses to complete behavior?
Interface              → How do unrelated classes promise the same capability?
AbstractVsInterface    → Which mechanism matches state-sharing, hierarchy and contract needs?
InterfaceInheritance   → How do larger contracts compose from smaller roles?
DefaultStaticPrivate   → How can interface APIs evolve and share helper behavior safely?
MultipleInheritance    → How does Java combine multiple interface contracts and resolve default conflicts?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.AbstractClass/AbstractClass.md` | `#abstract-class-model` — Abstract class as partial implementation + state<br>`#abstract-method` — Abstract method contract<br>`#abstract-constructor` — Abstract class constructor and initialization<br>`#abstract-class-limits` — Instantiation and inheritance limits |
| `2.Interface/Interface.md` | `#interface-contract` — Interface as behavioral contract<br>`#interface-fields` — Interface fields are public static final<br>`#interface-method-kinds` — Abstract/default/static/private method kinds<br>`#interface-implementation` — Implementing multiple contracts |
| `3.AbstractVsInterface/AbstractVsInterface.md` | `#abstract-vs-interface-state` — State and constructor differences<br>`#abstract-vs-interface-inheritance` — Single class inheritance vs multiple interface inheritance<br>`#selection-guidance` — When to prefer abstract class or interface |
| `4.InterfaceInheritance/InterfaceInheritance.md` | `#interface-extends-interface` — Interface inheritance<br>`#multiple-interface-hierarchy` — Multiple parent interfaces<br>`#interface-redeclaration` — Redeclaration/refinement of inherited methods |
| `5.DefaultStaticPrivate/DefaultStaticPrivate.md` | `#default-method` — Default methods<br>`#static-interface-method` — Static interface methods<br>`#private-interface-method` — Private interface helpers<br>`#default-method-evolution` — Interface evolution compatibility |
| `6.MultipleInheritance/MultipleInheritance.md` | `#default-method-conflict` — Default-method conflict resolution<br>`#class-wins-rule` — Class method wins over interface default<br>`#explicit-super-interface` — InterfaceName.super dispatch<br>`#diamond-interface` — Diamond-shaped interface inheritance |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `AbstractClassController` | `templateBehavior()` | `#abstract-class-model` | Show shared state/implementation plus subtype hook. |
| `InterfaceController` | `multipleContracts()` | `#interface-implementation` | One class implementing multiple independent interfaces. |
| `DefaultMethodController` | `dispatch()` | `#default-method` | Show inherited default behavior and override. |
| `DefaultMethodController` | `resolveConflict()` | `#default-method-conflict` | Demonstrate explicit conflict resolution. |
| `InterfaceEvolutionController` | `defaultCompatibility()` | `#default-method-evolution` | Show added default method preserving existing implementation. |

#### Quiz coverage

abstract instantiation; state/constructor rules; interface field/method rules; multiple interface inheritance; default/static/private semantics; conflict resolution.

#### Interview coverage

interface vs abstract class trade-offs; why default methods exist; diamond conflicts; class-wins rule; program-to-interface; interface evolution and compatibility.


### 4.6 `object-contract`

**API applicability:** Yes — collection behavior makes broken contracts observable.

**Suggested assessment size:** Quiz 24–32; Interview 18–24. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.IdentityEquality/IdentityEquality.md` must explain what an "object contract" is: methods inherited from `Object` and ordering contracts influence how other Java APIs interpret an object. The learner should know before the details that equality, hashing, string representation and ordering are not isolated utility methods.

**Major terminology roles:**

```text
identity
→ "is this the same object?"

logical equality
→ "should these two objects represent the same value/entity?"

hashCode
→ hash-based lookup contract coupled to equals

toString
→ diagnostic/text representation contract

Comparable
→ natural ordering owned by the type

Comparator
→ external/alternative ordering strategy
```

**Learning roadmap:**

```text
Same reference or same logical value?
Identity vs Equality
        ↓
How should a class define logical equality?
equals
        ↓
How do hash-based collections use equality efficiently?
hashCode
        ↓
Why must equals and hashCode agree?
equals + hashCode Contract
        ↓
How should an object describe itself for humans/tools?
toString
        ↓
How can a type define one natural order?
Comparable
        ↓
How can callers define alternate orderings?
Comparator
```

**Running example / evidence strategy:** use a value object such as `Money`, `UserId`, or `BookKey`. Put the same instances into `HashSet`, `HashMap`, `TreeSet` and sorting operations so broken contracts produce visible behavior.

#### Layer 2 — Chapter story

```text
IdentityEquality → Before overriding anything, distinguish object identity from domain equality.
Equals           → Define what "same logical value" means and which laws callers rely on.
HashCode         → Explain why hash containers need a stable bucket signal.
EqualsHashCode   → Show that equality and hashing form one contract, not two independent methods.
ToString         → Move from machine identity to useful human-readable diagnostics without leaking secrets.
Comparable       → Define a type's natural ordering and its consistency expectations.
Comparator       → Separate ordering policy from the type when multiple views/orders are needed.
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.IdentityEquality/IdentityEquality.md` | `#identity-vs-equality` — Object identity vs logical equality<br>`#reference-equality` — Reference equality with ==<br>`#value-object-equality` — Value-object equality mental model |
| `2.Equals/Equals.md` | `#equals-contract` — equals contract: reflexive/symmetric/transitive/consistent/null<br>`#equals-implementation` — Typical equals implementation<br>`#equals-inheritance-risk` — Inheritance and equality symmetry risk |
| `3.HashCode/HashCode.md` | `#hashcode-contract` — hashCode contract<br>`#hash-distribution` — Hash distribution and performance<br>`#mutable-key-risk` — Mutable fields used in hashCode |
| `4.EqualsHashCode/EqualsHashCode.md` | `#equals-hashcode-consistency` — Equal objects must share hash code<br>`#hash-collection-lookup` — HashMap/HashSet lookup mechanics boundary<br>`#broken-contract-effects` — Observable failures from broken contract |
| `5.ToString/ToString.md` | `#tostring-purpose` — toString purpose<br>`#tostring-design` — Useful deterministic representation<br>`#tostring-sensitive-data` — Sensitive-data/logging boundary |
| `6.Comparable/Comparable.md` | `#natural-order` — Natural ordering<br>`#compareto-contract` — compareTo ordering contract<br>`#compareto-equals-consistency` — Consistency with equals and sorted collections |
| `7.Comparator/Comparator.md` | `#external-order` — External/custom ordering<br>`#comparator-composition` — thenComparing/reversed/null handling<br>`#comparator-contract` — Comparator transitivity/consistency<br>`#sorting-stability-boundary` — Sorting behavior and stability boundary |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `EqualityController` | `identityVsEquality()` | `#identity-vs-equality` | Compare == and equals for distinct logically equal objects. |
| `HashContractController` | `brokenHashSetLookup()` | `#broken-contract-effects` | Show failed lookup after violating equals/hashCode consistency. |
| `HashContractController` | `mutableKey()` | `#mutable-key-risk` | Mutate key field after insertion and observe lookup failure. |
| `OrderingController` | `naturalVsCustom()` | `#external-order` | Compare Comparable natural order with Comparator alternatives. |
| `OrderingController` | `compareToEquals()` | `#compareto-equals-consistency` | Show sorted-set implications when ordering is inconsistent with equals. |

#### Quiz coverage

identity vs equality; equals laws; hash consistency; mutable keys; hash collection consequences; Comparable vs Comparator; comparison contract.

#### Interview coverage

designing equals/hashCode; inheritance equality traps; why hashCode matters; mutable map keys; natural vs custom ordering; compareTo consistency; safe toString.


### 4.7 `string`

**API applicability:** Yes — pool/Unicode/encoding/regex behavior is highly observable.

**Suggested assessment size:** Quiz 28–38; Interview 18–26. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.Immutability/Immutability.md` must first explain what a Java `String` represents, why text handling is more complex than "an array of chars", and why immutability is the foundation for pooling, sharing and safe API use.

**Major terminology roles:**

```text
String immutability
→ existing String values do not change in place

String pool / intern
→ canonical identity optimization for selected equal strings

encoding / Charset
→ mapping between Java text and external bytes

UTF-16 code unit / Unicode code point / grapheme
→ different levels of "character" representation

StringBuilder / StringBuffer
→ mutable construction tools, not mutable String variants

regex
→ pattern-matching language over text

text block
→ source-code syntax for multiline String literals
```

**Learning roadmap:**

```text
What is a String value and why is it immutable?
Immutability
        ↓
Why can equal literals sometimes share identity?
String Pool
        ↓
How should text values actually be compared?
Equality
        ↓
What happens when Strings are repeatedly combined?
Concatenation
        ↓
How do we build text efficiently when mutation is useful?
StringBuilder → StringBuffer
        ↓
What does canonicalizing a String identity mean?
intern
        ↓
How does text become bytes and bytes become text?
Encoding / Charset
        ↓
Why is Java char not always one user-visible character?
Unicode / Code Point / Grapheme / Normalization
        ↓
How do we describe text patterns?
Regex
        ↓
How does Java make multiline source text easier to write?
Text Blocks
```

**Running example / evidence strategy:** carry a small piece of user text through equality, concatenation, UTF-8 encoding/decoding, supplementary Unicode, normalization and regex validation. Make every representation boundary observable with lengths, code points and bytes.

#### Layer 2 — Chapter story

```text
Immutability      → Why can text be safely shared and reused without defensive mutation rules?
StringPool        → If String is immutable, when can the runtime safely share equal literal instances?
Equality          → Why does pooled identity not change the rule that text values use equals?
Concatenation     → If String cannot mutate, what objects/results are created during text building?
StringBuilder     → How do we explicitly use one mutable buffer for incremental construction?
StringBuffer      → What synchronization difference exists, and what concurrency problem does it not solve?
Intern            → What does explicit canonicalization do and what trade-offs does it introduce?
Encoding          → Why does external I/O require an explicit mapping between text and bytes?
UnicodeCodePoint  → Why do char, code point and user-perceived character differ, and why can canonical-equivalent text compare differently?
Regex             → When is pattern matching useful, and where can backtracking become a cost/risk?
TextBlocks        → How does source representation improve without changing String runtime semantics?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.Immutability/Immutability.md` | `#string-immutability` — Why String is immutable<br>`#immutability-consequences` — Sharing, hashing and thread-safety consequences<br>`#string-operation-new-value` — String operations return new values |
| `2.StringPool/StringPool.md` | `#string-pool-model` — String pool mental model<br>`#literal-vs-new` — Literal vs new String<br>`#pool-identity` — Pool identity and compile-time constants |
| `3.Equality/Equality.md` | `#string-equals` — String content equality<br>`#string-reference-equality` — Why == is not content comparison<br>`#case-insensitive-boundary` — Case-insensitive comparison and locale boundary |
| `4.Concatenation/Concatenation.md` | `#concat-semantics` — String concatenation semantics<br>`#compile-time-concat` — Compile-time constant concatenation<br>`#runtime-concat` — Runtime concatenation and implementation boundary<br>`#loop-concat-cost` — Repeated concatenation cost |
| `5.StringBuilder/StringBuilder.md` | `#builder-mutable-buffer` — StringBuilder mutable buffer model<br>`#builder-capacity` — Length vs capacity<br>`#builder-usage` — Efficient incremental construction |
| `6.StringBuffer/StringBuffer.md` | `#buffer-synchronization` — StringBuffer synchronization<br>`#builder-vs-buffer` — StringBuilder vs StringBuffer trade-off<br>`#thread-safety-boundary` — Why synchronized methods do not solve all composition concerns |
| `7.Intern/Intern.md` | `#intern-semantics` — String.intern semantics<br>`#intern-identity` — Canonical pool reference<br>`#intern-tradeoffs` — Interning trade-offs and memory considerations |
| `8.Encoding/Encoding.md` | `#text-vs-bytes` — Text vs bytes mental model<br>`#charset-encode-decode` — Charset encode/decode<br>`#default-charset-risk` — Default charset portability risk<br>`#malformed-input` — Malformed/unmappable input boundary |
| `9.UnicodeCodePoint/UnicodeCodePoint.md` | `#utf16-char-model` — Java char and UTF-16<br>`#code-point` — Unicode code point<br>`#surrogate-pairs` — Surrogate pairs<br>`#unicode-iteration` — Correct code-point iteration<br>`#code-unit-code-point-grapheme` — UTF-16 code unit vs Unicode code point vs user-perceived grapheme cluster<br>`#unicode-normalization` — Unicode normalization forms and `Normalizer`<br>`#canonical-equivalence` — Canonically equivalent text can have different code-point sequences and binary/String equality |
| `10.Regex/Regex.md` | `#pattern-matcher` — Pattern/Matcher model<br>`#regex-groups` — Groups and captures<br>`#regex-quantifiers` — Greedy/reluctant quantifiers<br>`#regex-performance` — Backtracking and performance pitfalls |
| `11.TextBlocks/TextBlocks.md` | `#text-block-syntax` — Text block syntax<br>`#incidental-whitespace` — Incidental indentation<br>`#escape-processing` — Escapes and line terminators<br>`#text-block-not-template` — Text blocks are not string templates |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `StringPoolController` | `identity()` | `#pool-identity` | Compare literals, new String, compile-time concat and intern. |
| `StringConcatController` | `constructionTrace()` | `#loop-concat-cost` | Compare result/counters for repeated concat vs StringBuilder. |
| `EncodingController` | `roundTrip()` | `#charset-encode-decode` | Encode/decode same text under explicit charsets. |
| `EncodingController` | `wrongCharset()` | `#default-charset-risk` | Demonstrate mojibake from mismatched charset. |
| `UnicodeController` | `charVsCodePoint()` | `#surrogate-pairs` | Show length/char count/code-point count for supplementary character. |
| `UnicodeController` | `normalization()` | `#canonical-equivalence` | Compare precomposed and combining-mark forms before and after NFC/NFD normalization, including code points and equality. |
| `RegexController` | `groups()` | `#regex-groups` | Return matches/groups for controlled input. |
| `RegexController` | `backtracking()` | `#regex-performance` | Bounded safe example showing problematic pattern cost characteristics. |

#### Quiz coverage

immutability; pool identity; == vs equals; concatenation constants; builder/buffer; intern; charset mismatch; UTF-16 code units/code points/grapheme boundary; Unicode normalization/canonical equivalence; regex groups/quantifiers; text-block whitespace.

#### Interview coverage

why String immutable; pool behavior; encoding bugs; char/code point/grapheme distinctions; canonical equivalence and normalization; StringBuilder vs StringBuffer; intern trade-offs; regex performance; text blocks.


### 4.8 `exception`

**API applicability:** Yes — propagation/resource-close/suppression/wrapping can be demonstrated deterministically.

**Suggested assessment size:** Quiz 28–38; Interview 20–28. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.Throwable/Throwable.md` must explain Java's failure model before hierarchy trivia: normal control flow assumes success, exceptions represent abnormal completion, and Java needs a structured way to propagate failure information across call boundaries.

**Major terminology roles:**

```text
Throwable
→ root model for exceptional completion

checked exception
→ compile-time handling/declaration contract

unchecked exception
→ runtime failure not subject to checked handling rules

throw / throws
→ create/propagate a failure vs declare a possible checked failure

try / catch / finally
→ handle and clean up around exceptional control flow

try-with-resources / suppressed exception
→ deterministic resource cleanup while preserving multiple failures

exception translation / chaining
→ change abstraction vocabulary without losing root cause
```

**Learning roadmap:**

```text
How does Java represent abnormal failure?
Throwable
        ↓
Which failures become part of the compile-time method contract?
Checked vs Unchecked
        ↓
How is a failure thrown and declared?
throw / throws
        ↓
How does failure move through the call stack?
Propagation
        ↓
How do we handle failure and still perform cleanup?
try / catch / finally
        ↓
How do resources close safely even when multiple things fail?
try-with-resources
        ↓
What happens when the body and cleanup both throw?
Suppressed Exceptions
        ↓
When should we create a domain/application exception?
Custom Exceptions
        ↓
Where should failures be translated, logged, recovered or allowed to propagate?
Exception Design
```

**Running example / evidence strategy:** use a small service operation that reads/parses data and crosses a repository/service boundary. Let it fail in controlled ways so propagation, wrapping, cause preservation, cleanup order and suppressed exceptions can all be observed from one story.

#### Layer 2 — Chapter story

```text
Throwable          → What changes in control flow when normal execution cannot continue?
CheckedUnchecked   → Which failures should the compiler force callers to acknowledge?
ThrowThrows        → How do we signal a failure and communicate the method contract?
Propagation        → If nobody handles it here, where does the exception go?
TryCatchFinally    → How do we recover/translate while guaranteeing cleanup behavior?
TryWithResources   → How can resource ownership be expressed so cleanup is automatic and ordered?
SuppressedException→ How do we preserve the primary failure when cleanup also fails?
CustomException    → When does a new exception type add meaningful abstraction/context?
ExceptionDesign    → Where should applications catch, translate, log, retry, recover or terminate?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.Throwable/Throwable.md` | `#throwable-hierarchy` — Throwable hierarchy<br>`#error-vs-exception` — Error vs Exception<br>`#stack-trace-cause` — Stack trace, cause and causal chain |
| `2.CheckedUnchecked/CheckedUnchecked.md` | `#checked-exception` — Checked exception compile-time contract<br>`#unchecked-exception` — RuntimeException semantics<br>`#checked-vs-unchecked-design` — Choosing checked vs unchecked |
| `3.ThrowThrows/ThrowThrows.md` | `#throw-statement` — throw statement<br>`#throws-clause` — throws declaration<br>`#precise-rethrow` — Precise rethrow typing and why the compiler can preserve narrower checked types<br>`#override-throws-rules` — Overriding methods may not broaden checked exceptions declared by the parent contract<br>`#checked-exception-narrowing` — Overrides may keep, narrow or remove checked exceptions while unchecked exceptions are not constrained the same way |
| `4.Propagation/Propagation.md` | `#exception-propagation` — Stack unwinding and propagation<br>`#catch-selection` — Catch selection by type<br>`#exception-chaining` — Wrapping with preserved cause<br>`#lost-cause-pitfall` — Lost-cause anti-pattern |
| `5.TryCatchFinally/TryCatchFinally.md` | `#try-catch-flow` — try/catch control flow<br>`#finally-semantics` — finally execution semantics<br>`#return-finally` — return/throw interactions with finally<br>`#multi-catch` — Multi-catch and alternatives |
| `6.TryWithResources/TryWithResources.md` | `#autocloseable` — AutoCloseable contract<br>`#resource-close-order` — Reverse resource close order<br>`#effective-final-resource` — Java 9 effective-final resource usage<br>`#twr-vs-finally` — Try-with-resources vs manual finally |
| `7.SuppressedException/SuppressedException.md` | `#primary-vs-suppressed` — Primary vs suppressed exception<br>`#get-suppressed` — Inspecting suppressed exceptions<br>`#close-failure` — Close failure during another failure |
| `8.CustomException/CustomException.md` | `#custom-exception-purpose` — When custom exceptions add meaning<br>`#exception-context` — Preserving useful context and cause<br>`#exception-hierarchy-design` — Designing a small domain exception hierarchy |
| `9.ExceptionDesign/ExceptionDesign.md` | `#exception-boundaries` — Translate exceptions at abstraction boundaries<br>`#do-not-swallow` — Do not swallow failures<br>`#logging-boundary` — Logging once at responsible boundary<br>`#exception-as-control-flow` — Avoid exceptions as normal control flow<br>`#cleanup-and-recovery` — Recovery vs cleanup vs propagation |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `PropagationController` | `propagate()` | `#exception-propagation` | Return bounded causal-chain trace from nested calls. |
| `PropagationController` | `wrapCause()` | `#exception-chaining` | Compare preserved vs lost cause. |
| `FinallyController` | `returnInteraction()` | `#return-finally` | Show why return/throw in finally is dangerous. |
| `ResourceController` | `closeOrder()` | `#resource-close-order` | Trace reverse close order. |
| `ResourceController` | `suppressed()` | `#primary-vs-suppressed` | Primary body failure plus close failure and getSuppressed. |
| `ExceptionDesignController` | `translateBoundary()` | `#exception-boundaries` | Translate low-level failure while preserving cause/context. |

#### Quiz coverage

Throwable hierarchy; checked/unchecked; throw vs throws; precise rethrow; overriding `throws` rules and checked-exception narrowing; propagation/catch ordering; finally return; TWR close order; suppressed exceptions; cause preservation; exception design.

#### Interview coverage

checked vs unchecked trade-off; checked-exception rules when overriding; precise rethrow; stack unwinding; why TWR is safer; suppressed exceptions; wrapping/translation; logging boundaries; custom exception design; swallow/rethrow pitfalls.

### 4.9 `generics`

**API applicability:** Yes — variance, PECS, raw types and erasure can be demonstrated with runtime evidence.

**Suggested assessment size:** Quiz 28–38; Interview 20–28. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.GenericType/GenericType.md` must begin by explaining the problem generics solve: without parameterized types, APIs either lose type information into `Object` or duplicate the same container/algorithm for many types. The learner should understand "type as a parameter" before wildcard syntax appears.

**Major terminology roles:**

```text
type parameter
→ placeholder type owned by a generic declaration

type argument
→ concrete type supplied by a use site

bounded type parameter
→ restricts which type arguments are legal

wildcard
→ describes an unknown family of compatible generic types at a use site

PECS
→ reasoning heuristic for producer/consumer variance

invariance
→ List<Dog> is not a subtype of List<Animal>

type erasure
→ compile-time generic safety with limited runtime generic type identity
```

**Learning roadmap:**

```text
Why not just store Object everywhere?
Generic Type
        ↓
How can one algorithm introduce its own type variable?
Generic Method
        ↓
How do we constrain the capabilities a type parameter must have?
Bounded Type
        ↓
How can an API accept a family of generic types without knowing the exact type?
Wildcards
        ↓
How do producer and consumer directions affect wildcard choice?
PECS
        ↓
Why is List<Dog> not a List<Animal>?
Invariance
        ↓
What happens when old raw types bypass generic guarantees?
Raw Types
        ↓
Where did generic type information go at runtime?
Type Erasure
        ↓
Which operations remain impossible or unsafe because of erasure/invariance?
Generic Limitations
```

**Running example / evidence strategy:** evolve `Box<T>` into a small repository/collection-style API and reuse a simple `Animal`/`Dog` hierarchy to demonstrate bounds, invariance, wildcards and PECS. Compile-time-invalid examples should be shown as code evidence rather than faked runtime endpoints.

#### Layer 2 — Chapter story

```text
GenericType    → How do we preserve type information while reusing one data structure/API?
GenericMethod  → How can one method be generic even when its class is not?
BoundedType    → How can generic code safely call capabilities that not every type has?
Wildcards      → How do we describe "some unknown subtype/supertype" at an API boundary?
PECS           → Why does reading and writing push wildcard bounds in different directions?
Invariance     → Why would generic subtyping be unsafe if List<Dog> were assignable to List<Animal>?
RawTypes       → How can legacy/raw usage escape compile-time safety and reintroduce runtime failure?
TypeErasure    → How does Java implement generics while preserving older bytecode/runtime compatibility?
Limitations    → Which restrictions follow from erasure, non-reifiable types and invariant mutation?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.GenericType/GenericType.md` | `#generic-type-purpose` — Why generic types exist<br>`#type-parameter` — Type parameter mental model<br>`#generic-invariance-intro` — Generic invariance introduction<br>`#generic-api-design` — Designing generic APIs |
| `2.GenericMethod/GenericMethod.md` | `#generic-method-syntax` — Generic method syntax<br>`#type-inference` — Generic method type inference<br>`#static-generic-method` — Static generic methods<br>`#generic-method-vs-type` — Method type parameter vs class type parameter |
| `3.BoundedType/BoundedType.md` | `#upper-bounded-type` — Upper-bounded type parameter<br>`#multiple-bounds` — Multiple bounds<br>`#recursive-bound` — Recursive/self bounds<br>`#bound-api-capability` — Bounds expose safe capabilities |
| `4.Wildcards/Wildcards.md` | `#unbounded-wildcard` — Unbounded wildcard<br>`#extends-wildcard` — Upper-bounded wildcard<br>`#super-wildcard` — Lower-bounded wildcard<br>`#wildcard-capture` — Wildcard capture |
| `5.PECS/PECS.md` | `#pecs-rule` — Producer extends, consumer super<br>`#read-from-producer` — Reading from extends<br>`#write-to-consumer` — Writing to super<br>`#pecs-api-design` — Applying PECS in method signatures |
| `6.Invariance/Invariance.md` | `#generic-invariance` — List<Integer> is not List<Number><br>`#variance-vs-arrays` — Generics invariance vs array covariance<br>`#variance-safety` — Why invariance preserves type safety |
| `7.RawTypes/RawTypes.md` | `#raw-type-compatibility` — Raw types for legacy compatibility<br>`#unchecked-warning` — Unchecked warnings<br>`#heap-pollution` — Heap pollution<br>`#raw-type-boundary` — Contain raw-type boundaries |
| `8.TypeErasure/TypeErasure.md` | `#erasure-model` — Type erasure mental model<br>`#erased-runtime-type` — Runtime type information after erasure<br>`#bridge-method` — Bridge methods<br>`#non-reifiable-types` — Reifiable vs non-reifiable types |
| `9.Limitations/Limitations.md` | `#no-generic-primitives` — No primitive type arguments<br>`#no-new-type-parameter` — Cannot instantiate T directly<br>`#generic-array-limit` — Generic array restrictions<br>`#static-type-parameter-limit` — Static context and type parameters<br>`#generic-exception-limit` — Generic exception restrictions |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `VarianceController` | `invariance()` | `#generic-invariance` | Show why List<Integer> cannot be passed as List<Number> while arrays differ. |
| `WildcardController` | `producerConsumer()` | `#pecs-rule` | Expose readable/writable operations for extends/super lists. |
| `RawTypeController` | `heapPollution()` | `#heap-pollution` | Controlled raw assignment causing later ClassCastException. |
| `ErasureController` | `inspectRuntimeTypes()` | `#erased-runtime-type` | Compare runtime Class of List<String> and List<Integer>. |
| `ErasureController` | `bridgeMethod()` | `#bridge-method` | Reflect synthetic bridge method generated for generic override. |
| `GenericMethodController` | `inference()` | `#type-inference` | Show inferred type from arguments/target context. |

#### Quiz coverage

type inference; bounds; wildcard read/write rules; PECS; invariance vs arrays; raw types; heap pollution; erasure; reifiable types; language limitations.

#### Interview coverage

why generics are invariant; PECS reasoning; erasure trade-offs; bridge methods; raw types; wildcard capture; API design with bounds; why generic arrays are restricted.


### 4.10 `collection`

**API applicability:** Yes — ordering, uniqueness, hashing, iteration and mutability are observable.

**Suggested assessment size:** Quiz 34–46; Interview 22–30. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.Hierarchy/Hierarchy.md` must explain why collections exist before listing interfaces: programs need different data structures because ordering, uniqueness, lookup, queueing and mutation have different contracts and costs. The hierarchy is a map of those capabilities, not a memorization tree.

**Major terminology roles:**

```text
Collection / Iterable
→ common sequence/group behavior

List
→ ordered, indexable, duplicate-friendly sequence

Set
→ uniqueness contract

Map
→ key-to-value association; not a subtype of Collection

Queue / Deque
→ insertion/removal-order-oriented data structures

iterator
→ traversal protocol separated from collection implementation

hashing / ordering / equality
→ contracts that determine lookup, uniqueness and sorted behavior

mutable / immutable collection
→ who owns structural change
```

**Learning roadmap:**

```text
Why are there many collection interfaces instead of one container?
Collection Hierarchy
        ↓
Need ordered positional values?
List
        ↓
Need uniqueness?
Set
        ↓
Need lookup by key?
Map
        ↓
Need FIFO/LIFO/double-ended operations?
Queue / Deque
        ↓
How do we traverse without knowing implementation details?
Iteration
        ↓
How do ordering and sorting contracts work?
Ordering / Sorting
        ↓
Why do equals/hashCode/comparison change collection behavior?
Equality / Hashing
        ↓
Who is allowed to mutate the collection?
Mutable vs Immutable
        ↓
What happens when structure changes during iteration?
Fail-Fast behavior
        ↓
Which implementation matches the actual workload?
Implementation Choice
        ↓
When can enum-specific structures be more precise/efficient?
Enum Collections
```

**Running example / evidence strategy:** use one small `Order`/`User` dataset and ask the same business questions with different structures: preserve order, remove duplicates, index by id, process a queue, sort by value. Let observable behavior and operation needs drive the choice of interface/implementation.

#### Layer 2 — Chapter story

```text
Hierarchy             → Which collection contract describes the operation we actually need?
List                  → When does encounter/index order matter and duplicates remain valid?
Set                   → How is uniqueness defined and what equality contract powers it?
Map                   → Why is key lookup a different abstraction from a collection of values?
QueueDeque            → How do processing order and endpoint operations shape the data structure?
Iteration             → How can algorithms traverse structures through a common protocol?
OrderingSorting       → Who owns natural order, custom order and sorted-container behavior?
EqualityHashing       → Why can broken equals/hashCode/comparison make values appear missing or duplicated?
MutableImmutable      → How do mutation ownership and defensive views affect API safety?
FailFast              → Why can structural mutation during traversal invalidate iterator assumptions?
ImplementationChoice  → How do operation complexity, ordering and memory constraints guide ArrayList/HashSet/etc. choice?
EnumCollections       → Why can a closed enum key/value universe enable specialized implementations?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.Hierarchy/Hierarchy.md` | `#collection-hierarchy` — Collection/List/Set/Queue hierarchy<br>`#map-separate-hierarchy` — Why Map is not Collection<br>`#interface-vs-implementation` — Program to collection interface<br>`#collection-characteristics` — Ordering, duplicates, nulls and mutability dimensions |
| `2.List/List.md` | `#list-semantics` — Ordered positional collection<br>`#arraylist-model` — ArrayList resizing/random access<br>`#linkedlist-model` — LinkedList node model and trade-offs<br>`#list-equality` — List equality is order-sensitive |
| `3.Set/Set.md` | `#set-semantics` — Uniqueness semantics<br>`#hashset-model` — HashSet hashing boundary<br>`#linkedhashset-order` — LinkedHashSet insertion order<br>`#treeset-order` — TreeSet sorted order and comparison contract |
| `4.Map/Map.md` | `#map-semantics` — Key/value mapping semantics<br>`#hashmap-model` — HashMap hashing/equality boundary<br>`#linkedhashmap-order` — LinkedHashMap encounter/access order<br>`#treemap-order` — TreeMap sorted-key model<br>`#map-compute-merge` — compute/merge style updates |
| `5.QueueDeque/QueueDeque.md` | `#queue-semantics` — FIFO queue semantics<br>`#deque-semantics` — Double-ended queue<br>`#exception-vs-special-value` — add/remove/element vs offer/poll/peek<br>`#priority-queue` — PriorityQueue heap-order boundary |
| `6.Iteration/Iteration.md` | `#iterator-contract` — Iterator contract<br>`#enhanced-for` — Enhanced for uses iteration<br>`#iterator-remove` — Safe iterator removal<br>`#spliterator-boundary` — Spliterator boundary and Stream handoff |
| `7.OrderingSorting/OrderingSorting.md` | `#encounter-order` — Encounter order<br>`#natural-ordering` — Natural ordering<br>`#comparator-ordering` — Comparator-based ordering<br>`#stable-sort` — Stable sort and tie behavior |
| `8.EqualityHashing/EqualityHashing.md` | `#element-equality-effects` — Element equals/hashCode affects collections<br>`#hash-bucket-boundary` — Hash-bucket lookup mental model<br>`#sorted-equality-boundary` — Sorted collections use comparison ordering<br>`#mutable-element-risk` — Mutable keys/elements and lookup invariants |
| `9.MutableImmutable/MutableImmutable.md` | `#modifiable-vs-unmodifiable` — Modifiable vs unmodifiable view<br>`#immutable-factory` — List.of/Set.of/Map.of semantics<br>`#copy-of` — copyOf snapshot/reuse boundary<br>`#view-vs-copy` — View vs defensive copy |
| `10.FailFast/FailFast.md` | `#structural-modification` — Structural modification<br>`#fail-fast-best-effort` — Fail-fast is best-effort bug detection<br>`#concurrent-modification-exception` — ConcurrentModificationException semantics<br>`#iterator-safe-mutation` — Safe mutation through iterator |
| `11.ImplementationChoice/ImplementationChoice.md` | `#list-choice` — ArrayList vs LinkedList<br>`#set-choice` — HashSet vs LinkedHashSet vs TreeSet<br>`#map-choice` — HashMap vs LinkedHashMap vs TreeMap<br>`#queue-choice` — ArrayDeque vs LinkedList vs PriorityQueue<br>`#choice-by-characteristics` — Choose by semantics before micro-performance |
| `12.EnumCollections/EnumCollections.md` | `#enumset-model` — EnumSet bit-vector-like specialized set<br>`#enummap-model` — EnumMap specialized map<br>`#enum-collection-benefits` — Type safety/order/performance benefits |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `ListController` | `arrayVsLinked()` | `#arraylist-model` | Show access/insertion operation traces without pretending benchmark precision. |
| `SetController` | `uniquenessAndOrder()` | `#set-semantics` | Compare HashSet/LinkedHashSet/TreeSet output characteristics. |
| `MapController` | `orderingAndCompute()` | `#map-compute-merge` | Demonstrate insertion/access/sorted order plus compute/merge. |
| `QueueController` | `queueOperations()` | `#exception-vs-special-value` | Compare throwing and sentinel operation pairs. |
| `IterationController` | `failFast()` | `#concurrent-modification-exception` | Trigger bounded fail-fast behavior then show iterator.remove safe path. |
| `ImmutabilityController` | `viewVsCopy()` | `#view-vs-copy` | Compare unmodifiable view with independent copy after source mutation. |
| `HashingController` | `mutableKey()` | `#mutable-element-risk` | Observe lookup instability after mutable hash key change. |
| `EnumCollectionController` | `specializedCollections()` | `#enum-collection-benefits` | Compare semantics/order of EnumSet/EnumMap. |

#### Quiz coverage

hierarchy; implementation characteristics; list/set/map/queue contracts; ordering; hashing/equality; fail-fast; immutable factories; views vs copies; implementation choice.

#### Interview coverage

how to choose collection; HashMap conceptual lookup; TreeSet/TreeMap comparison contract; mutable keys; fail-fast meaning; ArrayList vs LinkedList; unmodifiable vs immutable; PriorityQueue ordering.


### 4.11 `date-time`

**API applicability:** Yes — zones, DST, arithmetic and deterministic Clock experiments are valuable.

**Suggested assessment size:** Quiz 30–40; Interview 20–28. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.MentalModel/MentalModel.md` must distinguish the major meanings of time before API details: a calendar date, a wall-clock time, a local date-time, an instant on the global timeline, and a time-zone rule set are different concepts and should not be collapsed into one "timestamp" idea.

**Major terminology roles:**

```text
LocalDate / LocalTime / LocalDateTime
→ human calendar/wall-clock values without a global timeline position

Instant
→ machine-oriented point on the UTC timeline

ZoneId / ZoneOffset
→ time-zone rules vs one concrete UTC offset

ZonedDateTime / OffsetDateTime
→ date-time combined with zone rules or a fixed offset

Duration / Period
→ timeline-based amount vs calendar-based amount

Clock
→ injectable source of current time for deterministic code/tests
```

**Learning roadmap:**

```text
What kind of "time" does the problem actually mean?
Date-Time Mental Model
        ↓
Need only a calendar date?
LocalDate
        ↓
Need only wall-clock time?
LocalTime
        ↓
Need both but still no global timeline meaning?
LocalDateTime
        ↓
Need one precise point on the timeline?
Instant
        ↓
How do UTC offset and named zone rules differ?
ZoneOffset / ZoneId
        ↓
How do we attach offset/zone context to date-time values?
ZonedDateTime / OffsetDateTime
        ↓
How do we represent elapsed time vs calendar amount?
Duration / Period
        ↓
How do we parse and format intentionally?
Formatting / Parsing
        ↓
How do arithmetic and comparison change across types/zones?
Arithmetic / Comparison
        ↓
How do we make "now" testable?
Clock
        ↓
How do legacy Date/Calendar APIs interoperate and where do defaults/DST cause bugs?
Legacy Interop / Pitfalls
```

**Running example / evidence strategy:** use a meeting/booking scenario with a user-local schedule and a stored global instant. Reuse it to show local date-time ambiguity, zone conversion, DST gap/overlap, duration/period differences and deterministic `Clock` tests.

#### Layer 2 — Chapter story

```text
MentalModel           → Before choosing a class, what semantic kind of time does the domain own?
LocalDate             → How do we represent a date that intentionally has no time-of-day or zone?
LocalTime             → How do we represent wall-clock time without assuming a date/timeline position?
LocalDateTime         → When is "2026-09-25 09:00" meaningful even though it is not yet a global instant?
Instant               → How do we represent one unambiguous machine timeline point?
ZoneOffsetZoneId      → Why is +07:00 not the same thing as Asia/Ho_Chi_Minh rules?
ZonedOffsetDateTime   → When should the value carry named-zone rules versus only an offset?
DurationPeriod        → Why is 24 hours different from one calendar day around DST/calendar rules?
FormattingParsing     → How do text representation rules stay explicit and deterministic?
ArithmeticComparison  → Which arithmetic operates on calendar fields and which on timeline distance?
Clock                 → How do we remove hidden dependency on the system clock from business logic/tests?
LegacyInteropPitfalls → How do Date/Calendar/default zone/DST assumptions leak bugs into modern code?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.MentalModel/MentalModel.md` | `#date-time-domains` — Human calendar time vs machine timeline<br>`#immutable-date-time` — java.time immutability<br>`#choose-date-time-type` — Choose type by domain meaning |
| `2.LocalDate/LocalDate.md` | `#local-date-model` — Date without time/zone<br>`#local-date-arithmetic` — Date arithmetic<br>`#calendar-validity` — Calendar validation and month-end behavior |
| `3.LocalTime/LocalTime.md` | `#local-time-model` — Time-of-day without date/zone<br>`#local-time-precision` — Nanosecond precision<br>`#local-time-wrap` — Time arithmetic wraps within day |
| `4.LocalDateTime/LocalDateTime.md` | `#local-date-time-model` — Local date+time without zone<br>`#local-date-time-ambiguity` — Why local date-time is not an instant<br>`#local-date-time-use-cases` — Appropriate local use cases |
| `5.Instant/Instant.md` | `#instant-model` — UTC timeline instant<br>`#epoch-time` — Epoch seconds/millis/nanos<br>`#instant-use-cases` — Machine timestamps and persistence boundary |
| `6.ZoneOffsetZoneId/ZoneOffsetZoneId.md` | `#zone-offset` — Fixed offset<br>`#zone-id` — Region-based ZoneId<br>`#zone-rules` — ZoneRules and changing offsets |
| `7.ZonedOffsetDateTime/ZonedOffsetDateTime.md` | `#zoned-date-time` — ZonedDateTime combines local fields + zone rules<br>`#offset-date-time` — OffsetDateTime fixed offset semantics<br>`#same-instant-vs-same-local` — Same instant vs same local date-time<br>`#zone-conversion` — withZoneSameInstant vs local reinterpretation |
| `8.DurationPeriod/DurationPeriod.md` | `#duration-time-based` — Duration is time-based<br>`#period-date-based` — Period is date-based<br>`#duration-vs-period` — DST/month-length consequences |
| `9.FormattingParsing/FormattingParsing.md` | `#date-time-formatter` — DateTimeFormatter immutability<br>`#format-patterns` — Patterns vs predefined formatters<br>`#strict-smart-lenient` — Resolver styles<br>`#parse-target-type` — Parsing into correct temporal type |
| `10.ArithmeticComparison/ArithmeticComparison.md` | `#temporal-arithmetic` — plus/minus operations<br>`#between-semantics` — ChronoUnit/Duration/Period between<br>`#date-time-comparison` — isBefore/isAfter/compareTo<br>`#business-calendar-boundary` — Business-day logic is separate policy |
| `11.Clock/Clock.md` | `#clock-abstraction` — Clock abstracts current time<br>`#fixed-clock-testing` — Fixed Clock for deterministic tests<br>`#clock-injection` — Inject Clock instead of calling now everywhere |
| `12.LegacyInteropPitfalls/LegacyInteropPitfalls.md` | `#legacy-date-calendar` — Date/Calendar legacy mental model<br>`#legacy-conversion` — Conversion to/from Instant<br>`#system-default-zone-risk` — System default time-zone risk<br>`#dst-gap-overlap` — DST gaps and overlaps<br>`#timestamp-storage-boundary` — Store instant vs local business time deliberately |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `InstantController` | `timelineVsLocal()` | `#local-date-time-ambiguity` | Show one LocalDateTime mapping differently under zones. |
| `ZoneController` | `sameInstantVsLocal()` | `#same-instant-vs-same-local` | Compare zone conversion semantics. |
| `DstController` | `gapAndOverlap()` | `#dst-gap-overlap` | Use a known region/date to show gap/overlap resolution. |
| `DurationPeriodController` | `compareAcrossDst()` | `#duration-vs-period` | Contrast plus 24h vs plus 1 day across DST. |
| `ClockController` | `fixedNow()` | `#fixed-clock-testing` | Same deterministic now across repeated calls. |
| `FormatterController` | `resolverStyle()` | `#strict-smart-lenient` | Parse edge date under resolver styles. |

#### Quiz coverage

type choice; local vs instant; offset vs zone; same instant/local; Duration vs Period; formatter parsing; Clock; DST; default-zone pitfalls.

#### Interview coverage

Instant vs LocalDateTime vs ZonedDateTime; storing timestamps; DST bugs; Duration vs Period; why Clock injection matters; ZoneId vs ZoneOffset; formatter thread safety; legacy migration.


### 4.12 `io`

**API applicability:** Yes — controlled temp resources can demonstrate stream/channel/resource semantics safely.

**Suggested assessment size:** Quiz 30–40; Interview 20–28. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.MentalModel/MentalModel.md` must explain I/O as data moving between a source and sink across a resource boundary. Before naming classes, learners must distinguish bytes from characters, buffering from storage, blocking from non-blocking mechanisms, and resource lifetime from ordinary object lifetime.

**Major terminology roles:**

```text
InputStream / OutputStream
→ byte-oriented I/O

Reader / Writer
→ character-oriented I/O with text decoding/encoding concerns

buffering
→ reduce expensive underlying operations by batching data

File / Path / Files
→ legacy path object vs modern filesystem API model

channel / buffer
→ NIO data-transfer model separated from stream abstraction

FileChannel
→ file-oriented channel operations and random-access capabilities

AutoCloseable / try-with-resources
→ deterministic ownership/release of external resources
```

**Learning roadmap:**

```text
What is moving, from where to where, and who owns the resource?
I/O Mental Model
        ↓
Is the payload raw bytes?
Byte Streams
        ↓
Is the payload text?
Character Streams
        ↓
Why do many tiny reads/writes cost more?
Buffered I/O
        ↓
How did Java historically represent filesystem paths?
File
        ↓
What is the modern filesystem API?
Path / Files
        ↓
How does NIO model data with Channel + Buffer?
Buffers / Channels
        ↓
What file-specific operations become possible with FileChannel?
FileChannel
        ↓
How do we guarantee close/release behavior?
Resource Management
        ↓
What does Java object serialization do and why is it a boundary-heavy feature?
Serialization
        ↓
How do we choose the simplest correct I/O abstraction?
Choosing I/O
```

**Running example / evidence strategy:** move one UTF-8 text payload from memory to a temporary file and back, first as bytes, then characters, then buffered/channel-based forms. Keep charset and resource ownership explicit so the learner sees why String/Unicode knowledge matters.

#### Layer 2 — Chapter story

```text
MentalModel        → What are source, sink, payload unit and resource lifetime?
ByteStreams        → When must data remain opaque bytes rather than text?
CharacterStreams   → How do bytes become characters through a Charset-aware layer?
BufferedIO         → Why does batching improve throughput and where does flushing matter?
File               → What does the legacy File abstraction represent and what are its limitations?
PathFiles          → How do Path and Files separate path identity from filesystem operations?
BuffersChannels    → Why does NIO separate storage of data from the conduit that moves it?
FileChannel        → When do random access, position and channel-specific file operations matter?
ResourceManagement → Why must external handles be closed deterministically instead of waiting for GC?
Serialization      → What does object serialization promise, and why is it not a general persistence/network format recommendation?
ChoosingIO         → Given payload, scale and operation pattern, which abstraction is simplest and correct?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.MentalModel/MentalModel.md` | `#io-data-flow` — I/O as data flow between source/sink<br>`#bytes-vs-characters` — Byte vs character abstraction<br>`#blocking-io-boundary` — Blocking I/O mental model<br>`#resource-lifecycle` — I/O resources have lifecycle |
| `2.ByteStreams/ByteStreams.md` | `#inputstream-outputstream` — InputStream/OutputStream model<br>`#read-contract` — read return values and EOF<br>`#partial-read-write` — Reads/writes may be partial<br>`#byte-stream-use-cases` — Binary data use cases |
| `3.CharacterStreams/CharacterStreams.md` | `#reader-writer` — Reader/Writer model<br>`#charset-bridge` — InputStreamReader/OutputStreamWriter charset bridge<br>`#character-buffering` — Character buffering |
| `4.BufferedIO/BufferedIO.md` | `#buffering-purpose` — Why buffering reduces calls<br>`#flush-semantics` — flush semantics<br>`#buffer-size-tradeoff` — Buffer size trade-off<br>`#buffered-wrappers` — BufferedInputStream/Reader/Writer |
| `5.File/File.md` | `#legacy-file-model` — java.io.File is path-like metadata API<br>`#file-path-limitations` — Legacy File limitations<br>`#file-api-boundary` — Why prefer Path/Files for modern code |
| `6.PathFiles/PathFiles.md` | `#path-model` — Path is filesystem path abstraction<br>`#resolve-normalize` — resolve/normalize/relativize<br>`#files-operations` — Files read/write/copy/move/delete<br>`#file-attributes` — Attributes and metadata<br>`#directory-stream-walk` — Directory listing/walking and resource concerns |
| `7.BuffersChannels/BuffersChannels.md` | `#buffer-state` — Buffer position/limit/capacity<br>`#flip-clear-compact` — flip/clear/compact<br>`#channel-model` — Channel read/write model<br>`#bytebuffer-types` — Heap vs direct ByteBuffer boundary |
| `8.FileChannel/FileChannel.md` | `#filechannel-random-access` — Random-position I/O<br>`#filechannel-transfer` — transferTo/transferFrom<br>`#file-lock-boundary` — File locking boundary<br>`#memory-mapped-boundary` — Memory-mapped file boundary |
| `9.ResourceManagement/ResourceManagement.md` | `#closeable-lifecycle` — Closeable/AutoCloseable<br>`#try-with-resources-io` — Try-with-resources for I/O<br>`#resource-ownership` — Who owns and closes a stream<br>`#close-wrapper-chain` — Closing wrapper chains |
| `10.Serialization/Serialization.md` | `#java-serialization-model` — Object serialization model<br>`#serializable-graph` — Serializable object graph<br>`#serialversionuid` — serialVersionUID/version compatibility<br>`#transient-field` — transient fields<br>`#serialization-security-risk` — Native serialization security/compatibility risks |
| `11.ChoosingIO/ChoosingIO.md` | `#choose-stream-reader-channel` — Choose stream/reader/channel/path by problem<br>`#memory-vs-streaming` — Streaming vs loading whole content<br>`#charset-explicit` — Make charset explicit<br>`#io-error-handling` — I/O error/partial-operation handling<br>`#io-performance-boundary` — Measure before optimizing I/O |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `StreamController` | `byteVsCharacter()` | `#bytes-vs-characters` | Round-trip binary/text and expose byte/char counts. |
| `ReadContractController` | `eofAndPartialRead()` | `#read-contract` | Controlled custom stream proves EOF/read-count semantics. |
| `PathFilesController` | `resolveNormalize()` | `#resolve-normalize` | Return path transformation results without touching arbitrary filesystem. |
| `BufferController` | `stateTransitions()` | `#flip-clear-compact` | Expose position/limit/capacity after put/flip/get/compact. |
| `ResourceController` | `closeOrder()` | `#resource-ownership` | Trace nested resource close behavior. |
| `SerializationController` | `roundTrip()` | `#java-serialization-model` | Serialize safe sample to byte[] then deserialize and inspect transient field. |
| `SerializationController` | `versionRisk()` | `#serialization-security-risk` | Explain/observe metadata, without deserializing untrusted input. |

#### Quiz coverage

byte vs char; EOF/read contract; charset bridges; buffering/flush; Path operations; Buffer state machine; channels; resource ownership; serialization graph/version/transient risks.

#### Interview coverage

InputStream vs Reader; explicit charset; Path/Files vs File; flip/clear/compact; blocking I/O; try-with-resources ownership; serialization risks; streaming large files.

### 4.13 `localization`

**API applicability:** Yes — locale-sensitive formatting, bundles, fallback and collation are directly observable.

**Suggested assessment size:** Quiz 26–34; Interview 18–24. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.MentalModel/MentalModel.md` must explain the difference between internationalization and localization before any API names: software should keep domain data/logic independent from user-facing language, number, currency, date and collation conventions, then apply locale-sensitive presentation at the correct boundary.

**Major terminology roles:**

```text
Locale
→ language/script/region preference/context, not a time zone

language tag
→ interoperable textual representation of locale components

ResourceBundle
→ localized message/resource lookup with fallback

MessageFormat
→ parameterized locale-aware message templates

Number/Currency formatting
→ locale-sensitive presentation of numeric values

Collator
→ locale-sensitive text ordering/comparison

fallback/default locale
→ resolution behavior that can become a hidden dependency
```

**Learning roadmap:**

```text
Why should domain data be independent from presentation locale?
Localization Mental Model
        ↓
What does Locale actually identify?
Locale
        ↓
How is locale information represented across systems?
Language Tags
        ↓
How do we load localized text/resources?
ResourceBundle
        ↓
How do localized messages safely include parameters?
MessageFormat
        ↓
How do numbers vary by locale?
Number Formatting
        ↓
How do currency value and currency display differ?
Currency
        ↓
Why does alphabetic ordering differ by locale?
Collator
        ↓
How does locale affect date-time presentation without replacing ZoneId?
Date-Time Localization
        ↓
What happens when an exact localized resource is missing?
Fallback
        ↓
Why are system/default locale assumptions dangerous?
Locale Pitfalls
```

**Running example / evidence strategy:** reuse one product/order presentation with the same canonical domain values rendered for `vi-VN` and `en-US`: message text, decimal amount, currency, date and sorting. Keep time-zone conversion separate so Locale vs ZoneId does not blur.

#### Layer 2 — Chapter story

```text
MentalModel          → Which parts of an application are canonical domain data and which are locale-sensitive presentation?
Locale               → What preference/context does a Locale carry, and what does it explicitly not carry?
LanguageTags         → How do language/script/region components travel through a standard textual form?
ResourceBundle       → How can messages vary by locale without hard-coded if/else trees?
MessageFormat        → How do dynamic values enter translated messages without concatenation assumptions?
NumberFormatting     → Why do grouping, decimal separators and digit conventions vary?
Currency             → Why is a currency code/value distinct from locale-specific formatting?
Collator             → Why does lexical order require language-sensitive rules rather than raw Unicode order?
DateTimeLocalization → How do date/time display conventions vary while the underlying instant/zone semantics stay separate?
Fallback             → How does resource resolution degrade from specific locale to more general/default resources?
LocalePitfalls       → Where do default locale, case conversion and hidden environment assumptions create bugs?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.MentalModel/MentalModel.md` | `#i18n-vs-l10n` — Internationalization vs localization<br>`#locale-sensitive-data` — Locale-sensitive presentation vs locale-neutral domain data<br>`#localization-boundaries` — What belongs in localization layer |
| `2.Locale/Locale.md` | `#locale-model` — Locale language/script/region model<br>`#locale-construction` — Locale constructors/builders/factories<br>`#default-locale` — Default Locale categories and risks<br>`#locale-equality` — Locale identity/equality |
| `3.LanguageTags/LanguageTags.md` | `#bcp47-language-tag` — BCP 47 language tag mental model<br>`#for-language-tag` — Locale.forLanguageTag<br>`#language-script-region` — Language/script/region subtags<br>`#canonicalization-boundary` — Canonicalization and validity boundary |
| `4.ResourceBundle/ResourceBundle.md` | `#resourcebundle-model` — ResourceBundle lookup model<br>`#bundle-naming` — Bundle naming and candidate locales<br>`#properties-vs-class-bundle` — Properties vs class-based bundle<br>`#bundle-cache` — ResourceBundle caching boundary |
| `5.MessageFormat/MessageFormat.md` | `#messageformat-model` — MessageFormat placeholders<br>`#messageformat-types` — number/date/choice formatting boundary<br>`#quote-escaping` — Apostrophe quoting/escaping<br>`#messageformat-locale` — Locale-specific MessageFormat |
| `6.NumberFormatting/NumberFormatting.md` | `#number-format` — Locale-sensitive NumberFormat<br>`#decimal-format` — DecimalFormat patterns and symbols<br>`#parsing-numbers` — Locale-sensitive number parsing<br>`#formatting-vs-domain-value` — Formatting must not change domain numeric meaning |
| `7.Currency/Currency.md` | `#currency-model` — Currency code/default fraction digits<br>`#currency-vs-locale` — Locale suggests currency but is not currency<br>`#currency-format` — Currency NumberFormat<br>`#money-boundary` — Currency formatting vs monetary-domain modeling |
| `8.Collator/Collator.md` | `#collator-model` — Locale-sensitive text comparison<br>`#collation-strength` — Collation strength/decomposition<br>`#collator-vs-string-order` — Collator vs Unicode/code-unit ordering<br>`#sorting-user-text` — Sorting user-visible text |
| `9.DateTimeLocalization/DateTimeLocalization.md` | `#localized-date-format` — Localized date/time styles<br>`#locale-vs-zone` — Locale vs ZoneId responsibilities<br>`#localized-pattern` — Localized pattern generation<br>`#localized-parsing` — Locale-sensitive parsing boundary |
| `10.Fallback/Fallback.md` | `#bundle-candidate-chain` — ResourceBundle candidate/fallback chain<br>`#default-locale-fallback` — Default Locale fallback<br>`#base-bundle` — Base bundle role<br>`#missing-resource` — MissingResourceException and missing keys |
| `11.LocalePitfalls/LocalePitfalls.md` | `#turkish-i` — Locale-sensitive case conversion and Turkish-I style pitfalls<br>`#default-locale-production-risk` — Machine default Locale changes behavior<br>`#format-parse-roundtrip` — Formatting is not always a stable machine serialization format<br>`#translation-key-design` — Stable message keys and parameterized messages |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `LocaleController` | `compareLocales()` | `#locale-model` | Expose language/script/region and default-category values. |
| `BundleController` | `resolveBundle()` | `#bundle-candidate-chain` | Show candidate locale chain and selected message. |
| `MessageController` | `format()` | `#messageformat-locale` | Same message/arguments rendered under multiple locales. |
| `NumberLocalizationController` | `formatAndParse()` | `#number-format` | Format same BigDecimal under locales and parse controlled text. |
| `CurrencyController` | `currencyVsLocale()` | `#currency-vs-locale` | Show locale-derived currency plus explicit Currency differences. |
| `CollationController` | `sort()` | `#collator-vs-string-order` | Compare String natural order with locale Collator order. |
| `DateTimeLocalizationController` | `format()` | `#locale-vs-zone` | Same instant rendered with independent locale and zone settings. |

#### Quiz coverage

Locale structure/defaults; language tags; ResourceBundle lookup/fallback; MessageFormat escaping; number/currency formatting; Collator; locale vs zone; default-locale pitfalls.

#### Interview coverage

i18n vs l10n; Locale vs Currency vs ZoneId; ResourceBundle fallback; why default Locale is dangerous; MessageFormat; locale-sensitive comparison; separating domain data from presentation.


### 4.14 `annotation`

**API applicability:** Yes, partially — runtime-retained annotations are observable; compile-time processing remains primarily Knowledge/code evidence.

**Suggested assessment size:** Quiz 24–32; Interview 18–24. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.Basic/Basic.md` must first explain what metadata is and why code sometimes needs machine-readable declarations about other code. Only after that should it introduce `@` syntax and built-in annotations.

**Major terminology roles:**

```text
annotation
→ structured metadata attached to program elements

annotation element
→ typed metadata value declared by an annotation type

retention
→ how long metadata survives: source, class file or runtime

target
→ where an annotation is legal

meta-annotation
→ annotation that configures another annotation type

annotation processing
→ compile-time consumer that can validate/generate artifacts

reflection
→ runtime consumer for RUNTIME-retained metadata; owned deeply by reflection module
```

**Learning roadmap:**

```text
Why attach metadata to code instead of encoding everything in naming/conventions?
Annotation Basics
        ↓
Which metadata contracts does Java already provide?
Built-In Annotations
        ↓
How do we define our own metadata vocabulary?
Custom Annotation
        ↓
How long must that metadata survive?
Retention
        ↓
Where should it be legal to use?
Target
        ↓
How do annotations configure annotation types themselves?
Meta-Annotations
        ↓
How do repeatable and inherited annotations change lookup semantics?
Repeatable / Inherited
        ↓
How can compile-time tools consume metadata and generate code/resources?
Annotation Processing
```

**Running example / evidence strategy:** define one small annotation family such as `@Audit`, `@FeatureFlag`, or `@ColumnAlias`. Reuse it to demonstrate target, retention, repeatability and runtime inspection, then show a separate compile-time processor example without pretending compile-time behavior is a runtime API feature.

#### Layer 2 — Chapter story

```text
Basic                → What problem does structured metadata solve compared with comments or naming conventions?
BuiltIn              → Which compiler/tool contracts already use annotations and what guarantees do they add?
CustomAnnotation     → How do we define domain/framework metadata with a precise schema?
Retention            → Which consumer needs the metadata, and therefore how long must it survive?
Target               → How do we prevent metadata from appearing on meaningless program elements?
MetaAnnotation       → How do annotations declare their own retention, target, documentation and inheritance behavior?
RepeatableInherited  → How do multiple values and class-hierarchy lookup change retrieval semantics?
AnnotationProcessing → How can compile-time tools validate or generate code from metadata before the application runs?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.Basic/Basic.md` | `#annotation-model` — Annotation as metadata<br>`#annotation-syntax` — Annotation syntax and elements<br>`#annotation-restrictions` — Allowed annotation element types<br>`#annotation-use-sites` — Declaration/type-use boundary |
| `2.BuiltIn/BuiltIn.md` | `#override-annotation` — @Override compiler contract<br>`#deprecated-annotation` — @Deprecated and documentation<br>`#suppresswarnings` — @SuppressWarnings scope and responsibility<br>`#safevarargs` — @SafeVarargs and generic varargs boundary<br>`#functionalinterface` — @FunctionalInterface compiler check |
| `3.CustomAnnotation/CustomAnnotation.md` | `#declare-annotation` — Declaring annotation types<br>`#annotation-elements-defaults` — Elements and default values<br>`#marker-annotation` — Marker annotations<br>`#custom-annotation-design` — Designing meaningful metadata |
| `4.Retention/Retention.md` | `#retention-source` — SOURCE retention<br>`#retention-class` — CLASS retention<br>`#retention-runtime` — RUNTIME retention<br>`#retention-use-case` — Choose retention by consumer |
| `5.Target/Target.md` | `#elementtype-targets` — ElementType targets<br>`#type-use-annotation` — TYPE_USE<br>`#target-design` — Restrict annotations to valid contexts |
| `6.MetaAnnotation/MetaAnnotation.md` | `#retention-meta` — @Retention<br>`#target-meta` — @Target<br>`#documented-meta` — @Documented<br>`#inherited-meta` — @Inherited boundary<br>`#repeatable-meta` — @Repeatable |
| `7.RepeatableInherited/RepeatableInherited.md` | `#repeatable-container` — Repeatable annotations and container<br>`#get-annotations-by-type` — Reflection retrieval of repeated annotations<br>`#inherited-class-only` — @Inherited applies to class inheritance only<br>`#annotation-inheritance-boundaries` — Method/interface inheritance boundaries |
| `8.AnnotationProcessing/AnnotationProcessing.md` | `#processing-rounds` — Compile-time annotation-processing rounds<br>`#processor-contract` — Processor/AbstractProcessor contract<br>`#supported-types-source-version` — Supported annotation types/source version<br>`#generated-source` — Generated source/resource output<br>`#processing-vs-reflection` — Compile-time processing vs runtime reflection<br>`#processor-pitfalls` — Determinism and generated-code pitfalls |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `AnnotationInspectionController` | `retention()` | `#retention-use-case` | Reflect annotations with RUNTIME while explaining SOURCE/CLASS absence. |
| `RepeatableAnnotationController` | `repeatable()` | `#get-annotations-by-type` | Return repeated annotations through reflection. |
| `InheritedAnnotationController` | `inheritance()` | `#inherited-class-only` | Compare class annotation inheritance with method/interface boundaries. |
| `MetaAnnotationController` | `inspectTargets()` | `#elementtype-targets` | Reflect meta-annotations on custom annotation type. |
| `BuiltInAnnotationController` | `compilerContracts()` | `#override-annotation` | Runtime response references compiled examples; compile-time effects documented as code evidence, not faked runtime behavior. |

#### Quiz coverage

annotation syntax/types; built-ins; retention; target; meta-annotations; repeatable/inherited semantics; compile-time processing vs reflection; processor rounds.

#### Interview coverage

why annotations exist; retention choice; @Inherited limitations; repeatable annotations; annotation processing lifecycle; runtime reflection vs compile-time code generation; framework metadata design.


### 4.15 `reflection`

**API applicability:** Yes — reflection is itself runtime inspection, making APIs directly educational.

**Suggested assessment size:** Quiz 30–40; Interview 22–30. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.Basic/Basic.md` must explain reflection as a deliberate escape from ordinary static access: code can inspect types/members and invoke behavior dynamically when the concrete structure is not known at compile time. It must also establish why frameworks use reflection and why ordinary application code should not default to it.

**Major terminology roles:**

```text
Class<?> / metadata
→ runtime description of a loaded Java type

Field / Method / Constructor
→ reflective descriptors for members

invoke / newInstance / get / set
→ dynamic operations driven by metadata instead of direct syntax

AccessibleObject / access checks
→ reflective access still interacts with language/module encapsulation

Type / ParameterizedType / TypeVariable / WildcardType
→ retained generic signature metadata model

dynamic proxy / InvocationHandler
→ runtime-generated interface implementation and interception mechanism

MethodHandle
→ neighboring dynamic invocation mechanism, not duplicated as full curriculum here
```

**Learning roadmap:**

```text
Why would code inspect other code at runtime?
Reflection Basics
        ↓
What information does a Class object expose?
Class Metadata
        ↓
How do we inspect/read/write fields dynamically?
Fields
        ↓
How do we discover and invoke methods dynamically?
Methods
        ↓
How do we discover and call constructors dynamically?
Constructors
        ↓
Can reflection bypass normal access control, and where does JPMS stop it?
Access Control
        ↓
What generic information remains available after erasure?
Generic Type Inspection
        ↓
How does dynamic invocation differ from ordinary compile-time calls?
Dynamic Invocation
        ↓
What safety, performance and maintainability costs appear?
Limitations / Risks
        ↓
How can the runtime synthesize an interface implementation that intercepts calls?
Dynamic Proxy
```

**Running example / evidence strategy:** use one small project-owned service/model class and inspect the same type throughout metadata, fields, methods, constructors and generic signatures. Finish with an interface-based proxy that records invocation flow so the learner can connect reflection metadata to framework-style behavior.

#### Layer 2 — Chapter story

```text
Basic                 → What do we gain and lose when structure is discovered dynamically instead of referenced directly?
ClassMetadata         → What is known about a loaded type at runtime?
Fields                → How can state be discovered/read/written when field names/types are metadata?
Methods               → How can a method be selected and invoked without a direct Java call expression?
Constructors          → How can objects be instantiated when constructor choice is discovered dynamically?
AccessControl         → Why does reflection not mean "encapsulation no longer exists", especially under JPMS?
GenericTypeInspection → If generics are erased for execution, what signature metadata can tools still inspect?
DynamicInvocation     → Which conversions/failures appear when invocation is driven dynamically?
LimitationsRisks      → When do lost compile-time safety, hidden coupling and runtime cost outweigh flexibility?
DynamicProxy          → How do frameworks implement interface interception without writing each implementation class manually?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.Basic/Basic.md` | `#reflection-model` — Reflection runtime metadata model<br>`#class-object-entrypoints` — Obtaining Class objects<br>`#reflection-use-cases` — Framework/tooling use cases<br>`#reflection-cost-boundary` — Complexity/performance/encapsulation costs |
| `2.ClassMetadata/ClassMetadata.md` | `#class-names` — Name/simpleName/canonicalName/typeName<br>`#class-kind` — isInterface/isEnum/isRecord/isArray/etc.<br>`#modifiers` — Modifier inspection<br>`#superclass-interfaces` — Superclass/interface metadata<br>`#declared-vs-public-members` — Declared vs inherited public members |
| `3.Fields/Fields.md` | `#field-discovery` — getField vs getDeclaredField<br>`#field-read-write` — Field get/set<br>`#field-modifiers` — static/final/volatile metadata<br>`#field-type-metadata` — Raw/generic field type |
| `4.Methods/Methods.md` | `#method-discovery` — getMethod vs getDeclaredMethod<br>`#method-signature-metadata` — Return/parameter/exception metadata<br>`#method-invoke` — Method.invoke<br>`#invocation-exception` — InvocationTargetException boundary<br>`#varargs-reflection` — Varargs reflection boundary |
| `5.Constructors/Constructors.md` | `#constructor-discovery` — Constructor discovery<br>`#constructor-newinstance` — Constructor.newInstance<br>`#constructor-metadata` — Parameters/modifiers/exceptions<br>`#constructor-reflection-failure` — Instantiation/access/target failure modes |
| `6.AccessControl/AccessControl.md` | `#language-vs-reflective-access` — Language access vs reflective access checks<br>`#try-set-accessible` — trySetAccessible/setAccessible<br>`#strong-encapsulation-boundary` — JPMS strong encapsulation boundary<br>`#accessible-object-risk` — Encapsulation/security maintenance risk |
| `7.GenericTypeInspection/GenericTypeInspection.md` | `#type-interface` — Type hierarchy: Class/ParameterizedType/TypeVariable/WildcardType/GenericArrayType<br>`#parameterized-type` — ParameterizedType arguments<br>`#type-variable-bounds` — TypeVariable and bounds<br>`#wildcard-reflection` — Wildcard upper/lower bounds<br>`#erasure-vs-signature-metadata` — Erasure vs retained generic signature metadata |
| `8.DynamicInvocation/DynamicInvocation.md` | `#reflective-dispatch` — Reflective invocation flow<br>`#argument-conversion` — Boxing/unboxing/widening boundary in invocation<br>`#method-handle-boundary` — Reflection vs MethodHandle boundary<br>`#dynamic-invocation-design` — When dynamic invocation is justified |
| `9.LimitationsRisks/LimitationsRisks.md` | `#compile-time-safety-loss` — Loss of compile-time safety<br>`#encapsulation-breakage` — Encapsulation breakage<br>`#reflection-performance` — Performance and caching metadata<br>`#native-image-boundary` — Closed-world/native-image configuration boundary<br>`#reflection-maintainability` — Refactorability and maintainability |
| `10.DynamicProxy/DynamicProxy.md` | `#jdk-proxy-model` — JDK dynamic proxy model<br>`#invocation-handler` — InvocationHandler flow<br>`#proxy-interfaces` — Interface-based proxy requirement<br>`#object-methods-proxy` — equals/hashCode/toString through handler boundary<br>`#proxy-limitations` — Final/class/static/constructor boundary and Spring AOP handoff |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `ClassMetadataController` | `inspectType()` | `#class-kind` | Return type kind/modifiers/superclass/interfaces for fixed sample classes. |
| `FieldReflectionController` | `inspectAndMutate()` | `#field-read-write` | Read/write controlled mutable field and return metadata. |
| `MethodReflectionController` | `invoke()` | `#method-invoke` | Invoke controlled method and expose result/exception wrapping. |
| `ConstructorReflectionController` | `construct()` | `#constructor-newinstance` | Create sample instance reflectively and expose constructor metadata. |
| `GenericReflectionController` | `genericSignature()` | `#parameterized-type` | Inspect nested parameterized/wildcard/type-variable examples. |
| `AccessReflectionController` | `accessChecks()` | `#try-set-accessible` | Show canAccess/trySetAccessible on safe project-owned classes. |
| `DynamicProxyController` | `intercept()` | `#invocation-handler` | Proxy interface, trace handler, delegate and return result. |

#### Quiz coverage

Class entrypoints; declared vs inherited members; Field/Method/Constructor APIs; InvocationTargetException; access checks; Type subinterfaces; erasure/signatures; dynamic proxy; reflection risks.

#### Interview coverage

how frameworks use reflection; getMethod vs getDeclaredMethod; InvocationTargetException; generic Type hierarchy; JPMS effects; dynamic proxy internals; reflection vs MethodHandle; native-image/refactoring concerns.


### 4.16 `classloader`

**API applicability:** Yes — loader chain, resource lookup, initialization and identity can be observed; unloading/leaks need bounded demonstrations.

**Suggested assessment size:** Quiz 28–38; Interview 22–30. These are coverage ranges, not quotas.

#### Layer 1 — Module orientation

**Entry chapter contract:** `1.Lifecycle/Lifecycle.md` must first explain what a ClassLoader is and why Java needs a loading subsystem at all: source code becomes class bytes, but the JVM still needs to locate, define, link and initialize types before they can execute. The learner must know that "class exists on disk" and "Class object is loaded/initialized" are different states.

**Major terminology roles:**

```text
loading / linking / initialization
→ lifecycle stages from class bytes to executable initialized type

defining ClassLoader
→ loader that creates the runtime Class identity

parent delegation
→ default lookup strategy that asks parent loaders first

custom ClassLoader
→ alternate byte source/isolation mechanism

class identity
→ binary class name + defining loader

TCCL
→ thread-associated loader used by discovery/framework scenarios

resource loading
→ classpath resource lookup, related to but distinct from class definition

unloading / leaks
→ Class objects unload only with their defining loader reachability
```

**Learning roadmap:**

```text
How do class bytes become a usable runtime type?
Loading / Linking / Initialization
        ↓
Which loaders already exist in a normal JVM?
Built-In ClassLoaders
        ↓
Why do they ask parents before defining classes themselves?
Parent Delegation
        ↓
When would an application need a custom byte source or isolated namespace?
Custom ClassLoader
        ↓
Why can two classes with the same name still be incompatible types?
Class Identity
        ↓
Why do frameworks sometimes use the thread context ClassLoader?
Context ClassLoader
        ↓
How are non-class resources found through class/loader paths?
Resource Loading
        ↓
What actually triggers static initialization and what happens on failure?
Initialization
        ↓
Why can plugin/redeploy systems retain whole class-loader graphs?
Unloading / Leaks
```

**Running example / evidence strategy:** frame the module around a tiny plugin system. Load one plugin class through the application loader, then isolated custom loaders; observe loader identity, resource lookup, TCCL handoff, initialization trace and finally the references that would prevent unloading.

#### Layer 2 — Chapter story

```text
Lifecycle             → Which lifecycle stages exist between class bytes and initialized runtime behavior?
BuiltInClassLoaders   → Which standard loaders own JDK/platform/application classes?
ParentDelegation      → Why does parent-first lookup protect consistency and core-type identity?
CustomClassLoader     → How can plugins/containers define types from alternate locations or isolated namespaces?
ClassIdentity         → Why is binary name alone insufficient to decide whether two runtime types are the same?
ContextClassLoader    → How can parent-owned framework code discover child/application providers?
ResourceLoading       → How do class-relative and loader-root resource paths differ from filesystem paths?
Initialization        → What triggers `<clinit>`, how is it synchronized, and what happens after failure?
UnloadingLeaks        → Why does retaining one loader often retain all of its classes/resources and break redeploy/plugin cleanup?
```

#### Knowledge map

| Existing chapter | Proposed anchored H2 sections |
| --- | --- |
| `1.Lifecycle/Lifecycle.md` | `#loading-linking-initialization` — Loading → linking → initialization<br>`#linking-phases` — Verification, preparation and resolution<br>`#initialization-trigger` — Active-use initialization triggers<br>`#load-vs-initialize` — Loading a class is not the same as initializing it |
| `2.BuiltInClassLoaders/BuiltInClassLoaders.md` | `#bootstrap-loader` — Bootstrap loader<br>`#platform-loader` — Platform ClassLoader<br>`#application-loader` — Application/System ClassLoader<br>`#loader-chain` — Built-in parent chain and null bootstrap representation |
| `3.ParentDelegation/ParentDelegation.md` | `#parent-delegation` — Parent-first delegation algorithm<br>`#delegation-purpose` — Consistency/security reasons<br>`#child-first-boundary` — Child-first/custom strategies and risks<br>`#protected-packages-boundary` — Core-package loading boundary |
| `4.CustomClassLoader/CustomClassLoader.md` | `#classloader-contract` — ClassLoader loadClass/findClass contract<br>`#define-class` — defineClass byte definition<br>`#custom-source` — Loading bytes from custom source<br>`#custom-loader-safety` — Synchronization/package/security concerns |
| `5.ClassIdentity/ClassIdentity.md` | `#class-identity-rule` — Type identity = binary name + defining loader<br>`#same-name-different-type` — Same name under different loaders<br>`#class-cast-loader-failure` — ClassCastException across loader boundaries<br>`#loader-boundary-api` — Shared API types must cross compatible loader boundaries |
| `6.ContextClassLoader/ContextClassLoader.md` | `#tccl-purpose` — Thread Context ClassLoader purpose<br>`#tccl-discovery` — Provider/framework discovery<br>`#tccl-lifecycle` — Save/set/restore TCCL<br>`#tccl-leak-risk` — Long-lived thread context-loader leak risk |
| `7.ResourceLoading/ResourceLoading.md` | `#class-resource` — Class.getResource path semantics<br>`#loader-resource` — ClassLoader.getResource semantics<br>`#resource-enumeration` — Multiple resources/enumeration<br>`#resource-stream-lifecycle` — Resource stream lifecycle<br>`#classpath-vs-filesystem` — Classpath resource is not necessarily a File |
| `8.Initialization/Initialization.md` | `#clinit-model` — <clinit> conceptual model<br>`#initialization-once` — Initialization once per Class object<br>`#initialization-locking` — Initialization synchronization boundary<br>`#initialization-failure` — ExceptionInInitializerError and erroneous class state<br>`#constant-no-init` — Compile-time constant access may not initialize class |
| `9.UnloadingLeaks/UnloadingLeaks.md` | `#class-unloading` — Classes unload with defining loader reachability<br>`#loader-retention` — What retains a ClassLoader<br>`#static-threadlocal-leaks` — Static/ThreadLocal/listener/cache leak patterns<br>`#redeploy-leak` — Application redeploy/plugin lifecycle leaks<br>`#unloading-observation` — Why unloading is GC-dependent, not deterministic |

#### Proposed API experiments

| Controller | Experiment / method concept | Primary Knowledge anchor | Observation |
| --- | --- | --- | --- |
| `ClassLoaderChainController` | `inspectChain()` | `#loader-chain` | Return loaders for project class, JDK class and platform class. |
| `ClassLifecycleController` | `loadWithoutInit()` | `#load-vs-initialize` | Compare Class.forName initialize=false/true using safe trace holder. |
| `ResourceLoadingController` | `resolve()` | `#class-resource` | Compare Class-relative and ClassLoader-root resource lookup. |
| `CustomClassLoaderController` | `sameNameIdentity()` | `#same-name-different-type` | Load same binary name via isolated custom loaders and compare Class identity. |
| `ClassIdentityController` | `crossLoaderCast()` | `#class-cast-loader-failure` | Safely expose why same-name types cannot be cast across defining loaders. |
| `ContextClassLoaderController` | `temporaryTccl()` | `#tccl-lifecycle` | Set/restore TCCL in request thread with try/finally and return trace. |
| `InitializationController` | `failureState()` | `#initialization-failure` | Controlled class initialization failure observed once then erroneous-state behavior. |

#### Quiz coverage

loading/linking/init; built-in loaders; delegation; custom loaders; defining-loader identity; TCCL; resource paths; initialization triggers/failures; unloading/leak roots.

#### Interview coverage

class loading lifecycle; parent delegation; why same class name can be different type; TCCL purpose; Class.getResource vs ClassLoader.getResource; initialization triggers; plugin/redeploy leaks; unloading limitations.

## 5. Proposed scope summary

| Module | Chapters | Proposed H2 Knowledge sections | Proposed API experiments | Quiz target | Interview target |
| --- | ---: | ---: | ---: | ---: | ---: |
| `language-basics` | 13 | 51 | 8 | 30–40 | 20–28 |
| `numbers` | 11 | 34 | 7 | 28–36 | 18–24 |
| `class-object` | 14 | 49 | 7 | 36–48 | 24–32 |
| `oop` | 7 | 28 | 6 | 24–32 | 18–24 |
| `abstract-interface` | 6 | 22 | 5 | 22–30 | 16–22 |
| `object-contract` | 7 | 22 | 5 | 24–32 | 18–24 |
| `string` | 11 | 41 | 8 | 28–38 | 18–26 |
| `exception` | 9 | 34 | 6 | 28–38 | 20–28 |
| `generics` | 9 | 36 | 6 | 28–38 | 20–28 |
| `collection` | 12 | 49 | 8 | 34–46 | 22–30 |
| `date-time` | 12 | 41 | 6 | 30–40 | 20–28 |
| `io` | 11 | 45 | 7 | 30–40 | 20–28 |
| `localization` | 11 | 43 | 7 | 26–34 | 18–24 |
| `annotation` | 8 | 35 | 5 | 24–32 | 18–24 |
| `reflection` | 10 | 45 | 7 | 30–40 | 22–30 |
| `classloader` | 9 | 39 | 7 | 28–38 | 22–30 |
| **Total** | **160** | **614** | **105** | **450–602** | **314–430** |

These totals are planning bounds, not delivery quotas. During implementation an API experiment or assessment item may be removed when it proves redundant, weak or artificial.

The numeric table measures **coverage scope only**. It intentionally has no numeric "pedagogy score". A module with all planned H2/API/Quiz/Interview items can still fail acceptance if its entry roadmap, concept motivation or chapter transitions are incoherent.

## 6. Important curriculum dependencies

The modules are independent Gradle learning modules, but the curriculum has conceptual dependencies that should influence implementation order and cross-links:

```text
language-basics / type system
    ↓
class-object
    ↓
oop + abstract-interface
    ↓
generics
    ↓
collection

class-object
    ↓
object-contract
    ↓
collection hashing / ordering

string encoding + Unicode
    ↓
io character streams
    ↓
localization presentation

annotation
    ↓
reflection runtime metadata

language-basics runtime type
    +
class-object initialization
    +
reflection Class metadata
    ↓
classloader lifecycle / identity
```

Cross-links should point to the primary module rather than repeating the full explanation. Examples:

- `collection` may rely on `object-contract` for full `equals/hashCode` laws;
- `io` may rely on `string` for full charset/Unicode explanation;
- `reflection` may link to `generics` for erasure before explaining retained generic signature metadata;
- `classloader` may link to `class-object` for initialization fundamentals and to `reflection` for `Class` inspection;
- `annotation` should link to `reflection` only for runtime inspection, while compile-time annotation processing stays owned by `annotation`;
- `numbers/SecureRandom` should stop at the randomness boundary and link to `java/advance/security-cryptography` for cryptographic use;
- `string/StringBuffer` should not duplicate concurrency curriculum; synchronization details beyond its API boundary belong to concurrency modules.

## 7. Implementation acceptance gates

The implementation should not move to the next learning surface merely because files exist.

### Gate 0 — Pedagogical plan ready

Before rewriting real README Knowledge for one module:

```text
entry chapter can explain the module to a beginner without assuming its vocabulary
major terminology is listed and each term has a role in the module
domain/design concepts are distinguished from Java mechanisms/comparison/boundary concepts
chapter order has an explainable cause-and-effect learning narrative
each major chapter has a problem/motivation question
important chapter-to-chapter transitions are known
running example/evidence strategy is selected when continuity adds value
```

If the module still reads like a list of unrelated terms, do not proceed merely because the H2 inventory is complete.

### Gate A — Knowledge ready

For one module:

```text
all planned chapters populated in VI + EN
all intended H2 anchors present with VI/EN parity
no duplicate anchors
entry/equivalent first chapter explains what the module is, why it exists and what vocabulary follows
important terms explain why they belong in the module before deep mechanics
concept progression and chapter transitions are coherent
technical rules are attached to an established mental model rather than presented as isolated facts
code examples answer a learning question / demonstrate a problem or behavior
running examples remain consistent where the plan calls for continuity
code examples compile conceptually and do not contradict current Java 21 behavior
knowledge-metadata synchronized
aiGenerated=true / reviewed=false retained until human review
```

### Gate B — API ready

```text
only meaningful runtime experiments implemented
controller grouping follows learning domain
experiments are deterministic/safe/bounded
no arbitrary filesystem/network/security side effects
each learning method maps to a real Knowledge anchor
Swagger summary/description/execution explains the experiment independently
```

For APIs that intentionally demonstrate failure, return or capture the failure in a controlled way where possible instead of destabilizing the application.

### Gate C — Quiz ready

```text
questions sample all important concepts without mechanically covering every H2
behavior/code-reading questions dominate over trivia
four stable A/B/C/D answers
all explanations useful
relations exact or blank
no duplicate questions disguised by wording
VI/EN parity
```

### Gate D — Interview ready

```text
questions test explanation/comparison/trade-offs/runtime reasoning
not prose copies of Quiz
reference answers are technically complete but concise
relations exact or blank
VI/EN parity
```

### Gate E — Integrated coverage review

Build a module matrix:

```text
Knowledge concept / major term
→ learner knows why it exists?
→ relationship to module/previous concept is explicit?
→ Java mechanism is identified correctly?
→ code/runtime evidence? (when useful)
→ API evidence? (optional)
→ Quiz coverage?
→ Interview coverage?
→ cross-module boundary correct?
```

Classify findings as:

```text
MUST FIX
→ incorrect concept, missing foundational Knowledge, invalid relation, broken API experiment,
  misleading assessment, or a nontrivial module with no usable beginner roadmap

SHOULD FIX
→ weak motivation, definition-first explanation, unclear terminology relationship,
  poor chapter transition/order, disconnected examples, thin assessment coverage, duplicated content

OPTIONAL
→ enrichment that adds value but is not required for a coherent Java Core curriculum
```

The final goal is not maximum counts. The final goal is that every Java Core module teaches one coherent story:

```text
WHAT → WHY → RELATION → HOW → EVIDENCE → PRACTICE
```

and every downstream surface can be traced back to technically correct Knowledge inside that story.
