# Pass-by-Value

Java **always passes arguments by value**. The confusion comes from objects: for a reference argument, the copied value is the **reference value**.

## <a id="java-pass-by-value">Java Is Always Pass-by-Value</a>

Each parameter receives a copy of the argument value.

For a primitive, changing the parameter does not change the caller's variable.

For an object reference, the parameter receives another reference value pointing to the same object.

## <a id="reference-copy-mutation">Reference Copy and Mutation</a>

```java
void rename(User user) {
    user.setName("B");
}
```

The caller observes the mutation because both references point to the same `User` object.

This does **not** make Java pass-by-reference: the method did not receive the caller's variable slot, only a copied reference value.

## <a id="reassignment-vs-mutation">Reassignment vs Mutation</a>

```java
void replace(User user) {
    user = new User("new");
}
```

Reassigning the parameter only changes the local parameter variable. The caller's reference still points to the original object.

This distinction is foundational for aliasing, defensive copying, and API design.

The next chapter applies the same value/reference model to arrays.
