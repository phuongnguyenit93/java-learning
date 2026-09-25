# Type Casting in Java

## <a id="primitive-casting">Widening and narrowing primitive conversions</a>
Widening primitive conversion is often implicit. Narrowing is explicit because information may be lost through truncation, wraparound, or floating-to-integral conversion.

```java
int n = 100;
long wide = n;
byte narrow = (byte) 130; // -126
```

Casting does not validate range; validate first or use exact conversion helpers when correctness requires it.

## <a id="reference-upcast-downcast">Reference upcast and downcast</a>
Upcasting a subtype reference to a supertype is type-safe and usually implicit. Downcasting asks the runtime to verify that the referenced object is compatible with the requested subtype. The cast changes the compile-time view of the reference, not the runtime class of the object.

## <a id="instanceof-safe-cast">instanceof and safe casting</a>
Use `instanceof` when behavior truly depends on runtime type. Pattern matching can bind the narrowed variable after a successful test. If many type branches accumulate, reconsider whether polymorphism would model the behavior better.

## <a id="class-cast-failure">ClassCastException boundaries</a>
A syntactically legal downcast can still fail at runtime. `ClassCastException` means the actual object is not an instance of the target type. Prefer stronger contracts, generics, polymorphism, or a prior compatibility check over using exceptions as normal type discovery.
