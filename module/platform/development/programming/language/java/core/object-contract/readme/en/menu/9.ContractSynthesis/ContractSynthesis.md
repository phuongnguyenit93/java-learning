# Putting the Object Contracts Together

## <a id="contract-synthesis">Putting the Object Contracts Together</a>

The methods and interfaces in this module answer **different questions about the same object**. A correct design starts by deciding which question an API needs answered instead of treating every comparison as the same operation.

### ONE OBJECT, SEVERAL CONTRACTS

Keep the following model:

```text
==
→ same reference identity?

equals
→ same logical value/entity?

hashCode
→ hash-based lookup signal consistent with equals

toString
→ useful diagnostic representation

Comparable
→ one natural ordering owned by the type

Comparator
→ external/alternative ordering policy
```

These methods and interfaces are not merely code an IDE can generate. They are **contracts that collections, algorithms, logging/tooling, and caller code trust**.

### WHICH JAVA API OBSERVES WHICH CONTRACT?

```text
HashSet / HashMap
→ hashCode narrows candidates
→ equals confirms logical equality

TreeSet / TreeMap
→ compareTo or Comparator establishes ordering equivalence

sorting operations
→ compareTo or Comparator establishes order

logs / debuggers / assertion messages
→ toString provides diagnostic text
```

This is why the same object can behave correctly in one API and surprisingly in another when its contracts disagree. A type whose `equals` and ordering intentionally answer different domain questions may be valid, but the difference must be understood before choosing a hash-based or sorted data structure.

### WHEN EQUALITY AND ORDERING INTENTIONALLY DIFFER

`BigDecimal` is a useful cross-module example because its equality and natural ordering deliberately answer different questions:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0

Set<BigDecimal> hashValues = new HashSet<>();
hashValues.add(a);
hashValues.add(b);

Set<BigDecimal> sortedValues = new TreeSet<>();
sortedValues.add(a);
sortedValues.add(b);

System.out.println(hashValues.size());   // 2
System.out.println(sortedValues.size()); // 1
```

The two collections are not contradicting each other internally. `HashSet` observes `equals` + `hashCode`, while `TreeSet` observes natural ordering and therefore treats `compareTo(...) == 0` as one ordering key. Because `BigDecimal` natural ordering is inconsistent with `equals`, a `TreeSet<BigDecimal>` can therefore behave differently from the equality semantics described by the general `Set` contract. The Numbers module owns the deeper `BigDecimal` value/scale model; the lesson here is how **different object contracts become observable through different Java APIs**.

### STABLE STATE IS A CROSS-CUTTING REQUIREMENT

Several failures in this module share the same deeper cause: **the state used by a contract changes while an API is relying on the old result**.

```text
mutable equals/hashCode state
→ hash lookup may no longer find the key

mutable comparison state
→ tree placement may no longer match current ordering

mutable external comparator input
→ repeated comparisons may disagree
```

Immutability is not mandatory for every object, but equality-, hashing-, and ordering-relevant state should remain stable for as long as the object participates in a data structure that depends on that contract.

### DESIGN CHECKLIST

Before using a domain type as a key, set member, sortable value, or diagnostic object, ask:

1. What defines logical equality for this domain type?
2. Does `hashCode` use state consistent with that equality definition?
3. Can equality-relevant state change while the object is inside a hash-based collection?
4. Does the type truly have one natural order, or should ordering be external through `Comparator`?
5. Is the ordering transitive and internally consistent?
6. If ordering is inconsistent with `equals`, is that intentional and documented?
7. Does `toString` expose useful diagnostic state without leaking secrets or becoming a serialization format?

The end-to-end goal is not to memorize five APIs. It is to design objects whose **identity, equality, hashing, representation, and ordering semantics remain predictable to the Java APIs that consume them**.
