<a id="back-to-top"></a>

# Bytecode Transformation Tooling

## Menu
- [Principles for Selecting Bytecode Tools](#tool-selection-principles)
- [ASM as a Supporting Implementation Tool](#asm-supporting-role)
- [Byte Buddy as a Supporting Implementation Tool](#byte-buddy-supporting-role)
- [Low-Level Control vs High-Level Abstraction](#low-level-vs-abstraction)
- [Helper-Class Visibility Across Class-Loader Boundaries](#helper-class-visibility)
- [Tool, JDK, and Class-File Version Compatibility](#tooling-version-compatibility)

## <a id="tool-selection-principles">Principles for Selecting Bytecode Tools</a>

<details>
<summary>Click for details</summary>

There is no single bytecode tool that is best for every agent. Choose based on the problem:

~~~text
need low-level instruction/class-file control?
→ a low-level API such as ASM fits

need type/method matching plus advice?
→ a higher-level abstraction such as Byte Buddy often fits

target a newer JDK and can use the standard Class-File API?
→ consider java.lang.classfile on an appropriate baseline
~~~

Important criteria include:

- class-file version support;
- attribute preservation;
- stack-map/frame handling;
- API stability;
- dependency footprint;
- helper/auxiliary-class strategy;
- redefine/retransform integration;
- test/debug ergonomics.

The tool is an implementation choice. Agent lifecycle, targeting, capabilities, and structural limits still come from Instrumentation/JVM semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="asm-supporting-role">ASM as a Supporting Implementation Tool</a>

<details>
<summary>Click for details</summary>

ASM is a low-level bytecode library commonly used to read and write class files through visitor/tree-style APIs. It fits Instrumentation when:

- precise method/instruction control is required;
- transformations are small but performance-sensitive;
- the team already has JVM/bytecode expertise;
- avoiding hidden auxiliary machinery is important.

Mental model:

~~~text
classfileBuffer
→ ClassReader
→ visitor/tree transformation
→ ClassWriter
→ byte[]
~~~

The cost is more responsibility:

- select the correct API/class-file version;
- handle frames/max stack correctly;
- preserve valid control flow;
- retain important attributes;
- test across JDK/compiler outputs.

This module teaches ASM only far enough to understand its supporting implementation role, not the complete library API.

</details>

- [Back to top](#back-to-top)

---

## <a id="byte-buddy-supporting-role">Byte Buddy as a Supporting Implementation Tool</a>

<details>
<summary>Click for details</summary>

Byte Buddy provides a higher-level abstraction around bytecode generation/transformation. Instead of directly manipulating instructions, code often expresses:

~~~text
match type
→ match method
→ apply advice/delegation
→ install through instrumentation
~~~

That is useful for agents needing:

- complex method matching;
- advice/interceptor patterns;
- auxiliary types;
- ready-made Java Agent/Instrumentation integration;
- maintainability for teams that do not specialize in opcodes.

The trade-off is abstraction. When VerifyError, class-loader visibility, or multi-agent conflicts appear, developers still need the underlying class-file/JVM mental model.

Byte Buddy does not remove Instrumentation constraints. It cannot make a JVM-forbidden structural change legal.

</details>

- [Back to top](#back-to-top)

---

## <a id="low-level-vs-abstraction">Low-Level Control vs High-Level Abstraction</a>

<details>
<summary>Click for details</summary>

Think of the tooling levels like this:

| Level | Benefit | Cost |
| --- | --- | --- |
| ASM-like low-level | precise bytecode control, little abstraction | requires strong JVM/class-file expertise |
| Byte Buddy-like high-level | matcher/advice model, easier maintenance | requires understanding generated helpers/abstractions |
| Standard Class-File API | platform API on newer JDKs | tied to JDK baseline/version boundary |

Do not choose by line count alone.

A simple “instrument every @Timed method” agent can be natural with a high-level matcher. A compiler-like tool rewriting unusual instruction patterns may fit a lower-level API better.

Use the **highest abstraction level that still gives precise control over the use case's constraints**.

</details>

- [Back to top](#back-to-top)

---

## <a id="helper-class-visibility">Helper-Class Visibility Across Class-Loader Boundaries</a>

<details>
<summary>Click for details</summary>

A transformation often injects a call to a helper:

~~~text
OrderService.placeOrder()
→ AgentRuntime.onEnter()
→ AgentRuntime.onExit()
~~~

The target class can call that helper only if it is visible/linkable from the target's defining loader/module.

Common strategies include:

- a bootstrap helper JAR via appendToBootstrapClassLoaderSearch;
- helpers on the system/application loader when appropriate;
- auxiliary classes injected into a particular loader;
- redefineModule to add required reads/opens/exports.

A classic pitfall is the same Helper name defined by two class loaders, producing different type identities.

Therefore “the bytecode contains the correct INVOKESTATIC” is not enough. Ask:

~~~text
which loader will resolve the helper?
does the module have the required read/open/export relationship?
are the helper's own dependencies visible?
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="tooling-version-compatibility">Tool, JDK, and Class-File Version Compatibility</a>

<details>
<summary>Click for details</summary>

Tooling versions must keep pace with the target application's class-file versions.

A useful test matrix includes:

~~~text
agent build JDK
× runtime JDK
× application compiler/class-file version
× bytecode tool version
× important framework versions
~~~

An older ASM release may reject a new class-file version; an older Byte Buddy release may not support a newer feature/attribute. Conversely, building an agent on JDK 24 APIs can prevent it from running on a JDK 21 baseline.

Good practice:

- publish the supported JDK range;
- run representative applications on each range in CI;
- pin tool versions;
- upgrade tooling alongside JDK adoption;
- define clear behavior for unknown class-file versions: skip or fail.

</details>

- [Back to top](#back-to-top)
