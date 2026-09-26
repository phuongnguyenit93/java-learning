# equals

Once identity and logical equality are separated, the next question is: **if a class defines when two objects are logically equal, which laws must `equals` obey?**

## <a id="equals-contract">The equals Contract</a>

A correct `equals` implementation preserves several core properties:

```text
reflexive
x.equals(x) is true

symmetric
x.equals(y) and y.equals(x) agree

transitive
if x equals y and y equals z, then x equals z

consistent
the result stays stable while relevant state is unchanged

null
x.equals(null) is false
```

The compiler does not enforce these laws, but collections and libraries assume your objects honor them.

## <a id="equals-implementation">Implementing equals</a>

A value-style implementation often follows this flow:

```text
1. same-identity fast path
2. type compatibility check
3. compare all fields that define equality
```

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof UserId that)) return false;
    return Objects.equals(value, that.value);
}
```

Choosing `getClass()` vs `instanceof` affects inheritance semantics. Most importantly, `equals` and `hashCode` should use the same equality-relevant state.

## <a id="equals-inheritance-risk">Equality and Inheritance Risks</a>

Inheritance complicates equality when a subclass adds equality-relevant state.

A parent may compare only `id`, while a child also compares `region`. That can easily produce:

```text
parent.equals(child) == true
child.equals(parent) == false
```

which breaks symmetry.

When value semantics and open inheritance fight each other, composition, a closed hierarchy, or carefully class-based equality is often safer.

The next chapter asks what extra information hash-based collections need once equality is defined.
