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

## <a id="reference-upcast-downcast">Reference Upcast and Downcast</a>

Upcasting a subtype to a supertype is normally implicit:

```java
Dog dog = new Dog();
Animal animal = dog;
```

The object does not change; only the reference's static type becomes more general.

Downcasting is explicit and may require a runtime type check.

## <a id="instanceof-safe-cast">instanceof and Safe Casting</a>

Pattern matching can combine a runtime type test and binding:

```java
if (animal instanceof Dog dog) {
    dog.bark();
}
```

If code repeatedly branches with `instanceof` only to choose subtype behavior, reconsider the abstraction/polymorphism design.

## <a id="class-cast-failure">ClassCastException</a>

If the runtime object is incompatible with the target type, the downcast fails:

```java
Animal animal = new Cat();
Dog dog = (Dog) animal; // ClassCastException
```

The compiler validates possible type relationships; runtime validates the actual object.

The next chapter moves from type conversion to control flow.
