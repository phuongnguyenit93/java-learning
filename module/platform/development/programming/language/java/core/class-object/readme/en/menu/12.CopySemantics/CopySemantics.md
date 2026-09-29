# Copy Semantics

“Copy an object” is ambiguous in Java. It can mean copying a reference, creating a shallow copy, or constructing a deep copy.

## <a id="reference-copy">Reference Copy</a>

```java
BankAccount a = new BankAccount("A-01");
BankAccount b = a;
```

No new object is created. Only the reference value is copied, so both variables refer to the same object.

Mutation through one reference is visible through the other.

## <a id="shallow-copy">Shallow Copy</a>

A shallow copy creates a new outer object but copies its field values as they are.

Reference fields are copied as references, so nested mutable objects may still be shared. Suppose `BankAccount` owns a mutable list of tags and its copy constructor copies that list reference directly:

```java
class BankAccount {
    private final String id;
    private final List<String> tags;

    BankAccount(String id, List<String> tags) {
        this.id = id;
        this.tags = tags;
    }

    BankAccount(BankAccount source) {
        this(source.id, source.tags); // shallow: same list reference
    }

    List<String> tags() {
        return tags;
    }
}

BankAccount original = new BankAccount("A-01", new ArrayList<>());
BankAccount copy = new BankAccount(original);

copy.tags().add("VIP");

System.out.println(original.tags()); // [VIP]
```

The two `BankAccount` objects have different identities, but both still refer to the same mutable list.

## <a id="deep-copy">Deep Copy</a>

A deep copy tries to create independent mutable state where ownership requires it. In the running example, the copy constructor could instead use `new ArrayList<>(source.tags)` to give the copied account its own list.

There is no universal deep-copy algorithm for every object graph. The design must decide which nested values are copied, which immutable values may be shared, and how cycles/identity are treated.

Deep copying is therefore an ownership/domain decision rather than blindly cloning everything recursively.

## <a id="copy-strategies">Copy Strategies</a>

Clear alternatives to `clone()` include:

- copy constructors;
- static factories;
- builders initialized from an existing object;
- explicit mapping;
- serialization-based copying only when that contract truly fits.

Good copy APIs make it explicit which state is shared and which state is duplicated.

The next chapter studies the consequence of several references sharing mutable state: aliasing.
