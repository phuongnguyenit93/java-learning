# Access, Encapsulation, and Module Boundaries

The earlier chapters showed that reflection can discover `Field`, `Method`, and `Constructor` objects from runtime metadata instead of using direct source-level calls. That raises an important question: if `getDeclaredMethod()` can see a `private` method, does `private` still matter?

The key idea is that **discovering metadata is not the same as being allowed to use the underlying member**. Reflection can describe runtime structure, while reading a field, invoking a method, or constructing an object still goes through access control. `AccessibleObject` offers a controlled way to request suppression of some language access checks, and the Java Module System can still deny that request.

We keep the **same public-class/public-constructor baseline** used by the previous chapters; this chapter changes only the member being accessed so a beginner does not confuse reflective-access failure with a silently changed sample type:

```java
record PaymentRequest(String orderId, long amount) {}

public class PaymentService {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    public String pay(PaymentRequest request) {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
```

## <a id="language-vs-reflective-access">Language Access and Reflective Access</a>

With ordinary Java code, the compiler checks `public`, `protected`, package-private, and `private` access at the call site. A class outside `PaymentService` cannot write `service.internalStatus()` because that call violates Java access rules.

Reflection separates the operation into two stages. First comes **discovery**: obtain a descriptor for the member. Then comes **use**: read or write a field, invoke a method, or call a constructor. `getDeclaredMethod()` may return a descriptor for a private method, but `Method.invoke()` still performs access checks unless those checks have been suppressed.

```java
PaymentService service = new PaymentService("demo");
Method method = PaymentService.class.getDeclaredMethod("internalStatus");

System.out.println(method.getName());          // internalStatus
System.out.println(method.canAccess(service)); // commonly false for an outside caller

method.invoke(service); // IllegalAccessException if the caller has no access
```

This is why merely seeing a private member through reflection does not erase encapsulation. A descriptor is metadata; permission to operate on the member is a separate boundary.

`canAccess(obj)` asks whether the caller can currently use that reflected member. For instance fields and methods, `obj` must be a compatible instance; for static members and constructors, it must be `null`. This is clearer than the old `isAccessible()` API, whose name only reported whether access-check suppression was enabled.

## <a id="try-set-accessible">trySetAccessible and setAccessible</a>

`Field`, `Method`, and `Constructor` inherit from `AccessibleObject`. A reflected object can carry a flag that suppresses Java language access checks when the runtime allows it.

```java
Method method = PaymentService.class.getDeclaredMethod("internalStatus");

if (method.trySetAccessible()) {
    Object value = method.invoke(new PaymentService("demo"));
    System.out.println(value);
}
```

`trySetAccessible()` attempts to enable access and returns `true` when it succeeds. If the current boundary does not permit deep reflective access, it returns `false`. `setAccessible(true)` requests the same outcome but fails with `InaccessibleObjectException` when access cannot be enabled.

Neither API should be read as “make private public.” The member's modifiers do not change. These calls only control whether the reflected object may suppress language access checks when used.

Before the JPMS details, keep a two-layer mental model:

~~~text
Java access rules
public / protected / package-private / private
        ↓
module boundary (when named modules are used)
exports → lets other modules use a package's public API
opens   → permits deep reflection into that package
~~~

`exports` and `opens` solve different problems. A package can expose a public API while still refusing deep reflection into its private internals.

Inside the same module, reflection can generally enable access to members of classes in that module. An **unnamed module** is the implicit module used by classpath code without a `module-info.java`; an **open module** is a named module declared with `open module ...`, making its packages open for deep reflection by default. Packages in those cases are open for this purpose. Across other named modules, `exports` and `opens` become part of the decision.

A practical pattern is to **attempt access and handle failure explicitly**:

```java
Method method = PaymentService.class.getDeclaredMethod("internalStatus");

if (!method.trySetAccessible()) {
    throw new IllegalStateException("PaymentService is not open for reflective access");
}

String status = (String) method.invoke(service);
```

This is safer than assuming `setAccessible(true)` always works and discovering the module boundary only after deployment.

## <a id="strong-encapsulation-boundary">The JPMS Strong-Encapsulation Boundary</a>

The Java Platform Module System adds a boundary above package access. For named modules, two terms matter:

- `exports` allows other modules to use a package's **public API**;
- `opens` allows other modules to perform **deep reflection** on non-public members in that package.

For example:

```java
module payment.core {
    exports com.example.payment.api;
    opens com.example.payment.internal to payment.framework;
}
```

`payment.framework` may reflect deeply into `com.example.payment.internal`, while unrelated modules do not automatically receive that privilege. If a package is exported but not opened, its public API can be used according to export rules, but code in another module cannot simply force access to private, package-private, protected instance members, or protected constructors with `setAccessible(true)`.

That is the role of strong encapsulation: a module can distinguish public API from packages intentionally opened for deep reflection. Reflection remains powerful, but it is not a universal bypass for module boundaries.

At launch time, a JVM option can temporarily open a package to a specific consumer module:

```text
--add-opens payment.core/com.example.payment.internal=payment.framework
```

That is a runtime/deployment choice, not a reason for a library to casually depend on private internals. If a framework needs long-term reflective access, the module descriptor or integration contract should express that boundary deliberately.

## <a id="accessible-object-risk">Risks of Suppressing Access Checks</a>

Suppressing access checks enables framework-style behavior: serializers, dependency-injection containers, or mappers can work with constructors and fields that ordinary application code does not call directly. The cost is tighter coupling to implementation details.

If a tool depends on `provider`, `processedCount`, or `internalStatus()`, those members are still private in the Java language, yet they have become an implicit contract for that tool. Renaming a field, changing a constructor, moving a package, or enabling stronger module encapsulation can then break the integration at runtime instead of producing a compile-time error.

Reflection also does not make every `final` field writable. In Java 21, some final fields are non-modifiable through `AccessibleObject`, including `static final` fields, final fields of records, and final fields of hidden classes; enabling access may permit reads without granting write access to those fields.

When designing reflective code:

1. prefer a public/protected contract or interface when the structure is already known;
2. use deep reflection only at boundaries that truly require dynamic behavior;
3. check `trySetAccessible()` instead of assuming access can be forced;
4. centralize member names and access logic in an adapter or metadata layer;
5. treat module openness as part of the integration contract.

With the access boundary established, the next chapter moves to another kind of metadata: generic types. Java erases much generic information for execution, but it retains enough signature metadata for reflection to inspect declarations.
