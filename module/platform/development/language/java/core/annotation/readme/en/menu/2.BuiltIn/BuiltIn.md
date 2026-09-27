# Built-in Annotations

Java ships several annotations whose consumers are already defined by the language, compiler, or documentation toolchain. They are useful examples because each one has a narrow contract: it communicates one fact and a known tool decides what to do with it.

## <a id="override-annotation">`@Override` Compiler Contract</a>

`@Override` asks the compiler to verify that a method really overrides a compatible method from a supertype, or otherwise satisfies the language rule that permits `@Override`.

```java
class Animal {
    void sound() {
    }
}

class Dog extends Animal {
    @Override
    void sound() {
    }
}
```

The practical value appears when code changes or a signature is mistyped:

```java
class Dog extends Animal {
    @Override
    void sounds() { // compile error: does not override the intended method
    }
}
```

Without `@Override`, `sounds()` could silently become an unrelated overload/new method. The annotation converts an intention into a compiler-checked contract.

### BOUNDARY — `@Override` does not change dispatch

Overriding semantics come from Java's type system and method rules. `@Override` verifies the declaration; it does not make a method virtual or change runtime dispatch.

Java 21 also permits `@Override` on an explicitly declared accessor for a record component. That is a specific language rule in addition to the common superclass/interface overriding case.

## <a id="deprecated-annotation">`@Deprecated` and Documentation</a>

`@Deprecated` marks a program element as discouraged for continued use. Compilers can emit deprecation warnings when client code uses it.

```java
@Deprecated(since = "2.0", forRemoval = true)
public void legacyPay() {
}
```

Since Java 9, the annotation can record two useful pieces of metadata:

```text
since
→ version in which the API was deprecated

forRemoval
→ whether removal is intended in a future release
```

The annotation and the Javadoc `@deprecated` tag serve related but different purposes:

```text
@Deprecated
→ machine-readable/compiler-visible deprecation signal

@deprecated Javadoc tag
→ human explanation, replacement path, migration advice
```

A useful public API normally provides both when documentation is available. Deprecation should help callers migrate rather than merely label an API as old.

## <a id="suppresswarnings">`@SuppressWarnings` Scope and Responsibility</a>

`@SuppressWarnings` tells the compiler to suppress selected warning categories within the annotated scope.

```java
@SuppressWarnings("unchecked")
static List<String> cast(Object value) {
    return (List<String>) value;
}
```

The annotation is not proof that the code is safe. It only changes warning reporting. The programmer still owns the reasoning that justifies the suppression.

Prefer the narrowest useful scope:

```text
one local declaration or method
→ warning suppression stays near the reviewed code

whole class or package-scale suppression
→ unrelated future warnings may be hidden
```

The Java Language Specification requires compilers to recognize at least four standard suppression keys: `unchecked`, `deprecation`, `removal`, and `preview`. Compilers may recognize additional implementation-specific keys; for example, `javac` has lint categories such as `rawtypes`. Do not assume every warning string is a portable Java-language key.

### PRACTICE — suppress the cause you understand

Before adding `@SuppressWarnings`, ask whether the warning can be eliminated by improving the type-safe code. Suppression is appropriate when the unsafe-looking operation is required and its invariant is understood; it should not become a default way to silence review feedback.

## <a id="safevarargs">`@SafeVarargs` and Generic Varargs</a>

Generic varargs can create a heap-pollution boundary because varargs are implemented with arrays while generic type arguments are erased.

```java
@SafeVarargs
static <T> List<T> combine(List<T>... parts) {
    return Arrays.stream(parts)
            .flatMap(List::stream)
            .toList();
}
```

`@SafeVarargs` tells callers and the compiler that the method body does not perform potentially unsafe operations on the varargs parameter. This is a **promise by the author**, not a runtime check.

`@SafeVarargs` is only legal on **variable-arity** constructors or on variable-arity methods whose implementation cannot later be replaced by overriding: `static`, `final`, or `private` instance methods.

A misuse can still be unsafe:

```java
@SafeVarargs
static void unsafe(List<String>... lists) {
    Object[] array = lists;
    array[0] = List.of(42); // heap pollution
}
```

The annotation must therefore follow an actual review of the implementation. Detailed heap-pollution and generic-varargs mechanics belong to the generics boundary.

### How is this different from `@SuppressWarnings("unchecked")`?

The warning scope is materially different:

```text
@SuppressWarnings("unchecked")
→ local suppression for warnings belonging to the annotated declaration

@SafeVarargs
→ suppresses the non-reifiable varargs warning on the declaration
→ also suppresses the corresponding unchecked warning at invocation sites
```

That non-local effect is why `@SafeVarargs` is an API-level programmer assertion rather than merely a local warning switch.

## <a id="functionalinterface">`@FunctionalInterface` Compiler Check</a>

`@FunctionalInterface` expresses that an interface is intended to have one abstract function contract and asks the compiler to verify that property.

```java
@FunctionalInterface
interface PaymentRule {
    boolean allow(int amount);

    default PaymentRule negate() {
        return amount -> !allow(amount);
    }
}
```

Default, static, and private interface methods do not add abstract function contracts. Methods that merely correspond to public methods of `Object` also do not make the interface non-functional under the functional-interface rules.

An interface can still be a functional interface without the annotation. Adding `@FunctionalInterface` makes the design intention compiler-checked so an accidental second abstract method is caught early.

Built-in annotations demonstrate the core idea well: metadata is valuable because its consumer has a defined contract. The next chapter applies that idea to metadata vocabularies designed by application and library authors.
