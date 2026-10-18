<a id="back-to-top"></a>

# Class Transformation Pipeline

## Menu
- [When Is the Transformation Pipeline Triggered?](#transformation-trigger-points)
- [Transformer Group Ordering](#transformer-order)
- [How One Transformer's Output Becomes the Next Transformer's Input](#transformer-chaining)
- [Retransformation-Capable and Incapable Transformers](#retransform-capability-groups)
- [Reentrancy, Dependencies, and Recursive Instrumentation](#reentrancy-recursion)
- [Designing Idempotent Transformations](#idempotent-transformation)

## <a id="transformation-trigger-points">When Is the Transformation Pipeline Triggered?</a>

<details>
<summary>Click for details</summary>

The transformation pipeline can be triggered by three kinds of operation:

~~~text
new class definition
→ ClassLoader.defineClass or native equivalent

class redefinition
→ Instrumentation.redefineClasses

class retransformation
→ Instrumentation.retransformClasses
~~~

In all three cases transformers run before the resulting definition is applied by the JVM. However, **the input bytes and the set of invoked transformers differ**.

For first load, input originates from bytes passed to defineClass. For redefine, input comes from the ClassDefinition supplied by the caller. For retransform, the JVM starts from the appropriate initial/redefined bytes, automatically reapplies prior results of retransformation-incapable transformers, then invokes retransformation-capable transformers.

When debugging a pipeline, first ask: **is this a load, redefine, or retransform?**

</details>

- [Back to top](#back-to-top)

---

## <a id="transformer-order">Transformer Group Ordering</a>

<details>
<summary>Click for details</summary>

Java 21 defines four transformer groups in this order:

~~~text
1. Java retransformation-incapable transformers
2. native retransformation-incapable transformers
3. Java retransformation-capable transformers
4. native retransformation-capable transformers
~~~

Within each group, transformers run in registration order.

Therefore “registration order” is only globally true **inside a group**. A canRetransform=true transformer can be registered earlier than a canRetransform=false transformer yet still execute after the incapable group.

Native transformers come from the JVMTI ClassFileLoadHook and are mentioned only to complete the ordering model. Writing native/JVMTI agents remains outside this module.

</details>

- [Back to top](#back-to-top)

---

## <a id="transformer-chaining">How One Transformer's Output Becomes the Next Transformer's Input</a>

<details>
<summary>Click for details</summary>

Multiple transformers are **composed by chaining**:

~~~text
input bytes
  ↓ transformer A
bytes A
  ↓ transformer B
bytes B
  ↓ transformer C
final bytes
~~~

The byte[] returned by one transformer becomes classfileBuffer for the next. A transformer therefore does not necessarily see the “original class”; it may see bytes already modified by another agent.

Example:

~~~text
coverage agent
→ injects counters

APM agent
→ receives bytes that already contain counters
→ injects timing spans
~~~

Design consequences:

- avoid depending on exact byte layout unless necessary;
- parsers must tolerate attributes/code added by other tools;
- test agent coexistence;
- preserve semantics outside the transformation's responsibility.

Chaining is one reason bytecode tooling must be robust and version-compatible.

</details>

- [Back to top](#back-to-top)

---

## <a id="retransform-capability-groups">Retransformation-Capable and Incapable Transformers</a>

<details>
<summary>Click for details</summary>

During retransformClasses(), the two Java transformer groups behave very differently.

**canRetransform=false**

- transform() is **not invoked again**;
- the JVM reuses the output produced at the previous load/redefine;
- that previous effect is automatically reapplied.

**canRetransform=true**

- transform() is invoked again;
- input already includes the automatically reused incapable-transformer effects;
- output can change according to current agent configuration/state.

Mental model:

~~~text
initial / latest redefine bytes
        ↓
reuse previous output of non-retransformable transformers
        ↓
invoke retransformable transformers again
        ↓
verify + install
~~~

That is why retransformation capability is chosen when addTransformer is called, not when retransformClasses is requested.

</details>

- [Back to top](#back-to-top)

---

## <a id="reentrancy-recursion">Reentrancy, Dependencies, and Recursive Instrumentation</a>

<details>
<summary>Click for details</summary>

A transformer runs inside the class-definition path, so code it calls can accidentally trigger more class loading.

Example:

~~~text
transform OrderService
→ first use of logger
→ load logger implementation
→ transformer sees logger class
→ transformation code logs again
→ recursive loading / dependency problem
~~~

The API excludes some definitions of classes upon which a registered transformer depends, but agent design must still prevent broader recursion.

Useful techniques:

- initialize critical helpers before registering the transformer;
- denylist agent/tooling packages;
- keep the transform path lightweight and dependency-poor;
- use a ThreadLocal/reentrancy guard when appropriate;
- avoid network I/O or heavy initialization inside callbacks when possible.

Class loading is a concurrent, reentrant environment. Treat transformer code as runtime infrastructure, not as an ordinary request handler.

</details>

- [Back to top](#back-to-top)

---

## <a id="idempotent-transformation">Designing Idempotent Transformations</a>

<details>
<summary>Click for details</summary>

An **idempotent** transformation aims for a stable intended result when it sees the same valid input state. But retransformation must be modeled correctly: for a transformer registered with canRetransform=true, the JVM **does not** feed that transformer's own previous retransformation output back into the next retransformation. It starts again from the initial/latest-redefinition baseline, reapplies outputs of retransformation-incapable transformers, then invokes capable transformers again.

So this is an **incorrect mental model**:

~~~text
retransform #1 → probe A
retransform #2 → probe A + probe A
retransform #3 → probe A + probe A + probe A
~~~

A correctly modeled retransformation-capable transformer does not accumulate its own previous output that way.

Duplicate probes can still occur when the **baseline input already contains instrumentation** from build-time weaving, a replacement definition, retransformation-incapable transformers, or when equivalent transformation logic is registered/run multiple times in one pipeline. In those cases the transformer should recognize existing instrumentation instead of blindly adding another copy.

Strategies include:

- understand the exact baseline bytes guaranteed by the retransformation contract;
- detect markers/instructions/helper calls added by the agent;
- generate transformations deterministically from configuration;
- separate “desired instrumentation state” from “current bytes”;
- test load → retransform → retransform repeatedly.

Idempotence is especially important when an agent coexists with prior instrumentation, multiple transformers, or dynamic configuration. If an agent cannot reason about which bytes enter each pipeline stage, safe restoration becomes much harder.

</details>

- [Back to top](#back-to-top)
