# Pass-by-Value in Java

## <a id="java-pass-by-value">Java is always pass-by-value</a>
Every argument is copied into a parameter. For primitives, the primitive value is copied. For objects, the copied value is the reference value. Java does not pass a caller variable itself by reference.

## <a id="reference-copy-mutation">Copied references and visible object mutation</a>
When the copied reference still identifies the same mutable object, a callee can mutate that object and the caller observes the changed state.

```java
void add(List<String> x) { x.add("A"); }
```

The visible mutation does not make Java pass-by-reference; caller and callee simply hold copied references to one object.

## <a id="reassignment-vs-mutation">Parameter reassignment vs object mutation</a>
Reassigning a parameter changes only the callee's local parameter variable. It does not change which object the caller variable refers to. This distinction is the simplest test for the pass-by-value model.
