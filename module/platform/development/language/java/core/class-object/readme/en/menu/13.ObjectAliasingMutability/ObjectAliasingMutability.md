# Aliasing and Mutability

Aliasing occurs when multiple references point to the same object. With immutable values this is usually safe. With mutable objects, a change through one alias may appear unexpectedly elsewhere.

## <a id="aliasing-model">Multiple References, One Object</a>

```java
BankAccount account = new BankAccount("A-01", new ArrayList<>());
BankAccount alias = account;

alias.tags().add("VIP");
System.out.println(account.tags()); // [VIP]
```

Assignment did not copy the `BankAccount`; it copied the reference.

This connects directly to Java pass-by-value: a method receives a copy of the reference value and can therefore mutate the same object.

## <a id="shared-mutable-state">Shared Mutable State</a>

Shared mutable state complicates reasoning because several locations may change the same object.

Typical consequences include:

- invariants broken outside the owner;
- order-dependent tests;
- concurrency races;
- caches/views changing indirectly;
- unclear mutation responsibility.

Mutability is not inherently wrong; unclear ownership is the bigger problem.

## <a id="aliasing-in-collections">Collection Aliasing</a>

A getter returning an internal mutable collection leaks the reference. For the running `BankAccount` example:

```java
List<String> tags() {
    return tags;
}
```

Callers can now mutate `tags` without going through `BankAccount`'s rules.

The same problem occurs when a constructor stores a caller-owned mutable input directly.

The final chapter addresses this with immutability and defensive copying.
