# Casting

Casting asks Java to view/convert a value through another type within language rules. Primitive casting and reference casting have different mental models.

## <a id="primitive-casting">Primitive Casting</a>

Widening primitive conversion usually moves to a representation with broader range and often needs no explicit cast:

```java
int x = 10;
long y = x;
```

Narrowing conversion can lose information and normally requires a cast:

```java
long x = 1000L;
int y = (int) x;
```

A cast does not guarantee the value remains semantically valid for the domain.

Widening is a language conversion category, not a universal precision guarantee:

```java
long exact = 9_007_199_254_740_993L;
double approximate = exact;
```

Narrowing may change the value:

```java
int large = 130;
byte small = (byte) large; // -126
```

Compile-time constants also have special narrowing rules when the compiler can prove the value fits:

```java
byte a = 100;
int x = 100;
// byte b = x; // not allowed without a cast
```

## <a id="reference-upcast-downcast">Reference Upcast and Downcast</a>

Upcasting a subtype to a supertype is normally implicit:

```java
Dog dog = new Dog();
Animal animal = dog;
```

The object does not change; only the reference's static type becomes more general.

Downcasting is explicit and may require a runtime type check.

Reference casting does not transform the object itself:

```java
Animal animal = new Dog();
Dog dog = (Dog) animal;
```

No new object is created. The cast requests a different typed view after runtime compatibility is checked. Casting `null` to a reference type is valid and still produces `null`.

## <a id="instanceof-safe-cast">instanceof and Safe Casting</a>

Pattern matching can combine a runtime type test and binding:

```java
if (animal instanceof Dog dog) {
    dog.bark();
}
```

If code repeatedly branches with `instanceof` only to choose subtype behavior, reconsider the abstraction/polymorphism design.

`instanceof` with `null` is always `false`. Pattern variables exist only in the region where the compiler knows the test succeeded.

### Pattern matching is more than removing an explicit cast

The more important idea is that the compiler connects the **type test, binding, and control flow**:

```java
if (animal instanceof Dog dog && dog.isReady()) {
    dog.bark();
}
```

`dog` is available on the right side of `&&` because that operand runs only after the match succeeds.

A pattern variable is not available where the compiler cannot guarantee that the match happened. Keep this mental model:

```text
runtime type test succeeds
        ↓
compiler permits a typed binding
        ↓
binding exists only where success is guaranteed
```

## <a id="class-cast-failure">ClassCastException</a>

If the runtime object is incompatible with the target type, the downcast fails:

```java
Animal animal = new Cat();
Dog dog = (Dog) animal; // ClassCastException
```

The compiler validates possible type relationships; runtime validates the actual object.

```text
compile time → is this cast relationship legal?
runtime      → is the actual object compatible?
```

If a cast is being added only to "make the compiler accept it," re-check the type model. A cast should express real knowledge about the runtime relationship.

The next chapter moves from type conversion to control flow.
