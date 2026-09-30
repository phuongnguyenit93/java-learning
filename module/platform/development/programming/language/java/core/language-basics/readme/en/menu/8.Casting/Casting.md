# Type Conversion and Casting

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
