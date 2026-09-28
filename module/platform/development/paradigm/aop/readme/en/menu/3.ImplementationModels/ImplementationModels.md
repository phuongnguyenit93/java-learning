# AOP Implementation Models

## <a id="aop-compile-time">1. Compile-time weaving</a>

The aspect is combined with the target during the build or compilation process. The resulting artifact already contains the additional behavior before the application starts.

## <a id="aop-load-time">2. Load-time weaving</a>

Classes are adjusted when they are loaded into the runtime. This allows composition to happen later than compile time but before normal execution begins.

## <a id="aop-runtime">3. Runtime interception</a>

Some implementations apply cross-cutting behavior at runtime through an intermediate layer or interception mechanism.

Mental model:

```text
caller
  ↓
interception boundary
  ↓
cross-cutting behavior
  ↓
target
```

## <a id="aop-model-boundary">4. Why distinguish these models?</a>

Different implementation models have different capabilities and limitations.

A runtime interception model may observe only certain invocation boundaries, while a weaving model may affect a wider execution structure.

When learning a specific AOP framework, it is therefore important to separate the **AOP concept** from the **framework's implementation mechanism**.
