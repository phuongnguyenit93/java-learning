<a id="back-to-top"></a>

# ClassFileTransformer

## Menu
- [ClassFileTransformer Contract](#transformer-contract)
- [Transform Input Context](#transform-input-context)
- [Transform Results: New Bytecode, No Change, or Failure](#transform-result-contract)
- [Exceptions and Transformer Failure Behavior](#transform-failure-contract)
- [Selecting Targets by Class, Loader, and Module](#target-selection)
- [Transformer Removal and removeTransformer Limitations](#transformer-removal)

## <a id="transformer-contract">ClassFileTransformer Contract</a>

<details>
<summary>Click for details</summary>

ClassFileTransformer is the callback contract between the JVM Instrumentation pipeline and an agent's bytecode transformation code. After registration through Instrumentation.addTransformer(), the JVM can invoke transform when:

- a class is defined for the first time;
- a class is redefined;
- a class is retransformed, when the transformer was registered with canRetransform=true.

The key point is that a transformer runs **before the new class definition is verified and applied by the JVM**. Its output must therefore be a valid class file and must respect the semantics of the current operation.

A minimal transformer:

~~~java
final class TimingTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {

        if (!"com/example/OrderService".equals(className)) {
            return null;
        }
        return transformOrderService(classfileBuffer);
    }
}
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="transform-input-context">Transform Input Context</a>

<details>
<summary>Click for details</summary>

The callback provides enough context for an agent to decide whether the class should be touched:

- module: the module containing the class;
- loader: defining loader, null for the bootstrap loader;
- className: JVM internal-form name such as java/util/List;
- classBeingRedefined: null for initial load, non-null for redefine/retransform;
- protectionDomain: the class protection context;
- classfileBuffer: input bytes in class-file format.

className uses slashes rather than dots:

~~~java
if (className != null
        && className.startsWith("com/example/")) {
    // candidate
}
~~~

Do not target only by className in applications with multiple loaders. The same binary name defined by two different loaders represents two different Class objects. For complex agents, module + loader + className is a safer selection tuple.

</details>

- [Back to top](#back-to-top)

---

## <a id="transform-result-contract">Transform Results: New Bytecode, No Change, or Failure</a>

<details>
<summary>Click for details</summary>

A transformer has three practical outcomes.

**No change**

~~~java
return null;
~~~

The JVM keeps the current bytes.

**Change**

~~~java
return transformedBytes;
~~~

The contract says a transformer **should** create and return a new well-formed byte array when it performs a transformation; the strict requirement is that the input classfileBuffer must not be modified.

**Malformed input**

~~~java
throw new IllegalClassFormatException("...");
~~~

The output cannot be merely “close enough.” After the transformation chain, the JVM still verifies and links the class; format, version, or linkage problems can fail the overall operation.

Bytecode tools should parse/build their own representation and emit a new array, which also makes retransformation behavior easier to reason about.

</details>

- [Back to top](#back-to-top)

---

## <a id="transform-failure-contract">Exceptions and Transformer Failure Behavior</a>

<details>
<summary>Click for details</summary>

If transform() throws an uncaught exception, **subsequent transformers are still called** and the JVM still attempts the load/redefine/retransform. For that transformer, the effect is essentially the same as returning null.

Therefore, throwing RuntimeException is not a reliable way to “stop the pipeline.”

A defensive pattern is:

~~~java
try {
    return transformerEngine.transform(classfileBuffer);
} catch (Throwable ex) {
    agentLogger.error("Transform failed for " + className, ex);
    return null;
}
~~~

Catching Throwable is not a default recommendation for ordinary application code, but ClassFileTransformer documentation explicitly calls it out as a way to prevent unexpected unchecked failures in transformer code.

IllegalClassFormatException remains useful when the input truly is malformed because it improves logging/debugging even though transformation processing can continue.

</details>

- [Back to top](#back-to-top)

---

## <a id="target-selection">Selecting Targets by Class, Loader, and Module</a>

<details>
<summary>Click for details</summary>

A good transformer **filters early**. Parsing every class before deciding whether it is a target increases startup cost and risk.

~~~java
if (className == null) {
    return null;
}
if (!className.startsWith("com/example/order/")) {
    return null;
}
if (loader == null) {
    return null; // skip bootstrap classes for this agent
}
if (module != null && module.isNamed()
        && !"com.example.app".equals(module.getName())) {
    return null;
}
~~~

Real filters often include:

- package/class allowlists;
- denylists for agent/helper/tooling classes;
- class-loader identity/type;
- module name;
- marker annotation/interface when metadata is available;
- current agent enabled/disabled state.

Target selection is part of correctness, not merely a performance optimization.

</details>

- [Back to top](#back-to-top)

---

## <a id="transformer-removal">Transformer Removal and removeTransformer Limitations</a>

<details>
<summary>Click for details</summary>

removeTransformer(transformer) removes the most recently added matching registration and reports whether a match was found.

Two facts are commonly missed:

1. already-transformed classes **do not revert automatically**;
2. callbacks may still arrive after removal because class loading is multi-threaded.

That is why a transformer should have defensive state:

~~~java
if (!enabled.get()) {
    return null;
}
~~~

A shutdown flow often looks like:

~~~text
disable new work
→ remove transformer
→ coordinate remaining agent work if necessary
→ optionally restore affected classes via retransform/redefine
→ release helper resources
~~~

Restoration is a separate operation; removeTransformer only unregisters the transformer for future processing within the API's guarantees.

</details>

- [Back to top](#back-to-top)
