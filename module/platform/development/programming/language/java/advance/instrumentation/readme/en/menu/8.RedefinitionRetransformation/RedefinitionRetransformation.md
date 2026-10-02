<a id="back-to-top"></a>

# Redefinition and Retransformation

## Menu
- [What Is Redefinition For?](#redefine-purpose)
- [What Is Retransformation For?](#retransform-purpose)
- [Redefine vs Retransform](#redefine-vs-retransform)
- [Active Stack Frames When Methods Change](#active-frame-behavior)
- [Existing Instance and Static State After Class Modification](#existing-state-behavior)

## <a id="redefine-purpose">What Is Redefinition For?</a>

<details>
<summary>Click for details</summary>

Redefinition replaces a class definition with **class-file bytes supplied by the caller**:

~~~java
byte[] replacement = loadCompiledReplacement();
ClassDefinition definition =
        new ClassDefinition(OrderService.class, replacement);

inst.redefineClasses(definition);
~~~

Mental model:

~~~text
caller already has replacement class bytes
        ↓
redefineClasses(...)
        ↓
registered transformers participate according to pipeline rules
        ↓
JVM verifies + installs new definition
~~~

A typical use case is fix-and-continue/debugging or tooling that already has a concrete replacement definition. The Java API explicitly points out that when the goal is to instrument existing class bytes, retransformClasses is often the better model.

Redefinition requires the manifest/JVM redefine capability.

</details>

- [Back to top](#back-to-top)

---

## <a id="retransform-purpose">What Is Retransformation For?</a>

<details>
<summary>Click for details</summary>

Retransformation is used to **run the transformation process again for an already-loaded class**.

~~~java
if (inst.isRetransformClassesSupported()
        && inst.isModifiableClass(OrderService.class)) {
    inst.retransformClasses(OrderService.class);
}
~~~

The agent does not pass replacement bytes to retransformClasses. The JVM builds the pipeline input from the appropriate initial/latest-redefinition bytes, reuses previous output from retransformation-incapable transformers, then invokes retransformation-capable transformers again.

“Initial bytes” are not a byte-for-byte promise of the original .class file. The API allows constant-pool layout/order to differ and some attributes to differ or be absent while bytecode references remain semantically corresponding. Transformation code should therefore reason from the class-file model rather than exact raw layout.

This fits dynamic configuration well:

~~~text
agent attaches
→ register retransformation-capable transformer
→ find already-loaded OrderService
→ retransform
→ timing probe appears
~~~

If configuration later changes, another retransformation can generate bytes representing the new desired instrumentation state, provided the transformation design supports it.

</details>

- [Back to top](#back-to-top)

---

## <a id="redefine-vs-retransform">Redefine vs Retransform</a>

<details>
<summary>Click for details</summary>

The core distinction is:

| | Redefine | Retransform |
| --- | --- | --- |
| caller supplies replacement bytes | yes | no |
| transformation pipeline involved | yes, on supplied definition | yes, with retransformation rules |
| retransformation-incapable transformer | invoked during redefine | not invoked again; previous result reused |
| natural fit | concrete replacement definition exists | recompute instrumentation for loaded class |

Practical rule:

~~~text
"I have specific class bytes I want to install"
→ redefine

"I want the instrumentation pipeline to recompute this loaded class"
→ retransform
~~~

Both require a modifiable class and both remain subject to structural constraints. Do not choose one merely because its name sounds more powerful.

</details>

- [Back to top](#back-to-top)

---

## <a id="active-frame-behavior">Active Stack Frames When Methods Change</a>

<details>
<summary>Click for details</summary>

When a method is redefined/retransformed while an invocation is already executing, the JVM **does not migrate the active stack frame to new bytecode mid-call**.

~~~text
Thread A enters OrderService.placeOrder()
        ↓
old bytecode is executing

Thread B triggers retransform
        ↓
new definition installed

Thread A
→ continues old bytecode for that invocation

new invocation
→ uses new bytecode
~~~

For a short period, an old invocation and new invocations can therefore execute different method versions.

Agent/probe logic must tolerate that transition. “retransform returned” does not mean every active stack frame is now executing the new code.

</details>

- [Back to top](#back-to-top)

---

## <a id="existing-state-behavior">Existing Instance and Static State After Class Modification</a>

<details>
<summary>Click for details</summary>

Redefine/retransform changes the **class definition**, not the identity/state of existing objects.

The Java API specifies that:

- existing instances are not replaced;
- static variables retain their values;
- class initialization does not rerun merely because of redefine/retransform.

Suppose OrderService has:

~~~java
static int requestCount = 42;
private final String region = "ap-southeast";
~~~

Retransforming method bodies does not reset requestCount to zero or rerun constructors for existing OrderService objects.

This helps explain why structural modification is tightly restricted: the JVM must preserve class/object identity and existing state while method implementation may change.

If a tool needs to migrate object schema/state, Java Instrumentation redefine/retransform is not the right mechanism.

</details>

- [Back to top](#back-to-top)
