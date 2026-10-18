# Generic Invariance and Subtyping

This is one of the most surprising parts of generics at first. We already know:

```java
Dog dog = new Dog();
Animal animal = dog; // perfectly legal
```

`Dog` is a subtype of `Animal`. The new question is: **does that relationship automatically transfer through a generic type?**

Java therefore makes generic types **invariant by default**:

```text
Dog is a subtype of Animal
does not imply
Box<Dog> is a subtype of Box<Animal>
```

The next chapter introduces wildcards as the mechanism for selectively restoring the flexibility an API needs without breaking that guarantee.

## <a id="generic-invariance-intro">From Subtyping to Invariance</a>

Suppose:

```java
class Animal {}
class Dog extends Animal {}
```

`Dog` is a subtype of `Animal`, but:

```java
Box<Dog> dogs = new Box<>();
// Box<Animal> animals = dogs; // compile error
```

`Box<Dog>` is **not** automatically a subtype of `Box<Animal>`.

The intuition is mutation safety:

```java
Box<Animal> animals = dogs; // imagine this were legal
animals.set(new Animal());  // legal for Box<Animal>
```

The original `Box<Dog>` could now contain an `Animal` that is not a `Dog`.

This is **invariance**. The next Wildcards chapter introduces controlled flexibility without losing type safety.

## <a id="generic-declaration-subtyping">Generic Types Still Have Inheritance</a>

“Generics are invariant” does **not** mean generic classes and interfaces lose their declared inheritance relationships.

If the declarations are related:

```java
interface Container<T> {}

class ArrayContainer<T> implements Container<T> {}
```

then the subtype relation is preserved for the **same type argument**:

```java
ArrayContainer<String> child = new ArrayContainer<>();
Container<String> parent = child; // OK
```

What invariance rejects is inventing a subtype relationship merely because the **type arguments** are related:

```text
Dog <: Animal

but

Container<Dog> </: Container<Animal>
```

Keep both ideas together:

```text
declared inheritance still works
+
type-argument inheritance does not automatically transfer through a generic type
```

### MINIMAL `List<E>` MENTAL MODEL USED FROM THIS POINT ON

The Collection module comes immediately after Generics, so this module does **not** teach List implementations or performance here. For now, treat:

```text
List<E>
→ a container holding many E values

get(...)
→ read an E

add(E)
→ write an E

size()
→ read the number of elements
```

From this point on, the Invariance/Wildcard/PECS examples use only this minimal read/write model.

## <a id="generic-invariance">Generic Invariance</a>

The same rule demonstrated with `Box<T>` applies to `List<T>`:

```java
class Animal {}
class Dog extends Animal {}

List<Dog> dogs = new ArrayList<>();
// List<Animal> animals = dogs; // compile error
```

Even though `Dog <: Animal`, Java does not infer:

```text
List<Dog> <: List<Animal>
```

This is **the same invariance rule**, now shown with `List` because the next chapter uses it to introduce wildcards and PECS.

## <a id="variance-vs-arrays">Generic Invariance vs Array Covariance</a>

Java arrays are covariant:

```java
Integer[] integers = {1, 2};
Number[] numbers = integers; // compiles
```

The runtime then has to check stores:

```java
numbers[0] = 3.14; // ArrayStoreException
```

Arrays retain their component type at runtime, so the JVM can detect the bad store.

Generics choose a different trade-off:

```java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // compile error
```

The unsafe relationship is rejected statically instead of being accepted and guarded by a runtime store check.

## <a id="variance-safety">Why Invariance Preserves Safety</a>

Imagine `List<Dog>` could be assigned to `List<Animal>`:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs; // imagine this were legal
animals.add(new Animal());

Dog dog = dogs.get(0); // original guarantee is broken
```

The problem exists because `List` both **produces** and **consumes** elements. Global covariance would make mutation unsafe.

Mutable `List` makes the safety motivation especially obvious, but Java's rule is broader: generic classes and interfaces are invariant by default even when one particular API happens to be read-only. Java does not infer variance separately from each declaration's methods; when use-site flexibility is needed, wildcards express it explicitly.

So invariance is not an arbitrary restriction. It prevents Java from inventing subtype relationships between parameterized types merely because their type arguments are related.

Next, use this exact problem to ask: **how can an API accept a related family of generic types without weakening type safety?** That is the role of wildcards.
