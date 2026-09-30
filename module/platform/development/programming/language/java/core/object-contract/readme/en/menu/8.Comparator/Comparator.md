# Alternate Ordering with Comparator

`Comparable` places one natural order inside a type. `Comparator<T>` moves ordering policy **outside the type**, allowing the same data to be viewed through many orderings without changing the domain class.

This is an important design distinction:

```text
Comparable
→ the type owns one natural order

Comparator
→ the caller/context owns an ordering policy
```

## <a id="external-order">Custom Ordering</a>

The same `Book` values may be sorted by:

- title;
- publication date;
- price;
- rating;
- a compound business key.

The domain class should not need to change for every view:

```java
Comparator<Book> byTitle =
        Comparator.comparing(Book::title);

Comparator<Book> byPrice =
        Comparator.comparing(Book::price);
```

Choosing `byTitle`, `byPrice`, or another comparator changes **how callers order the books**. It does not rewrite `Book.equals` or redefine the type's domain equality. Ordering and logical equality are separate policies even when a sorted collection makes their interaction observable.

A correct `Comparator` also imposes a **total order** over the set of values it is designed to compare. If the policy permits `null`, the comparator itself must define where `null` belongs in that order.

### NATURAL ORDER AND BUSINESS VIEW ARE NOT THE SAME THING

A type may have a natural order and still need contextual comparators.

For example, `Version` may naturally order by semantic version while a UI needs to display releases by date. `Comparable` should not be redefined merely to serve one screen.

## <a id="comparator-composition">Comparator Composition</a>

Java provides factory and composition helpers that let you describe ordering as a sequence of policies.

```java
Comparator<Book> byTitleThenDate =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt);
```

### TIE-BREAKERS

If a comparator only checks title:

```text
Book("Java", 2022)
Book("Java", 2024)
```

it may return `0` even though the two books are different domain values. For list sorting that simply means they tie under this comparator. For `TreeSet`/`TreeMap`, `compare(...) == 0` may cause them to be treated as the same ordering key.

Design tie-breakers intentionally:

```java
Comparator<Book> byTitleThenDateThenId =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt)
                  .thenComparing(Book::id);
```

### PRIMITIVE-SPECIALIZED HELPERS

When the key is primitive, specialized helpers avoid unnecessary boxing and express intent clearly:

```java
Comparator<Book> byPages =
        Comparator.comparingInt(Book::pages);

Comparator<Book> bySales =
        Comparator.comparingLong(Book::sales);

Comparator<Book> byRating =
        Comparator.comparingDouble(Book::rating);
```

### `reversed()` AND COMPOSITION ORDER

```java
Comparator<Book> newestFirst =
        Comparator.comparing(Book::publishedAt)
                  .reversed();
```

Read comparator expressions as policy pipelines. The position of `reversed()` matters, especially once several comparison stages are composed.

For example, these two policies are different:

```java
// Reverse the complete chain: title descending, then date descending.
Comparator<Book> allDescending =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt)
                  .reversed();

// Keep title ascending, but reverse only the date tie-breaker.
Comparator<Book> titleAscDateDesc =
        Comparator.comparing(Book::title)
                  .thenComparing(
                          Comparator.comparing(Book::publishedAt).reversed()
                  );
```

Composition therefore expresses **which criterion is primary, which criterion only breaks ties, and which part of the policy is reversed**. Reversing a composed comparator is not the same operation as reversing one individual comparison stage.

### NULL HANDLING IS AN EXPLICIT POLICY

```java
Comparator<Book> byTitleNullLast =
        Comparator.comparing(
                Book::title,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
```

`nullsFirst` and `nullsLast` let the caller state null semantics explicitly instead of relying on accidental failure.

## <a id="comparator-contract">The Comparator Contract</a>

A comparator must define a coherent ordering.

### SIGN ANTISYMMETRY

If:

```text
compare(a, b) < 0
```

then the reverse comparison must give:

```text
compare(b, a) > 0
```

More generally, swapping the arguments must reverse the sign. Otherwise the comparator could claim that `a` comes before `b` while also claiming that `b` comes before `a`.

### TRANSITIVITY

If a comparator says:

```text
a < b
b < c
```

it must preserve:

```text
a < c
```

A circular “rock-paper-scissors” comparator does not define a valid total ordering for sorting or tree structures.

### VALUES THAT COMPARE AS ZERO MUST BE ORDERING-CONSISTENT

If:

```text
compare(a, b) == 0
```

then for a third value `c`, the sign of:

```text
compare(a, c)
```

must agree with the sign of:

```text
compare(b, c)
```

This makes values that are equivalent under the ordering behave as one coherent ordering class.

### CONSISTENCY WITH EQUALITY

The ordering imposed by a `Comparator` is **consistent with `equals`** when:

```text
(compare(a, b) == 0)
        ↕ equivalent
a.equals(b)
```

A comparator is not strictly required to preserve this consistency, but callers must understand the consequences when it does not, especially in `TreeSet` and `TreeMap`. Those collections still have well-defined ordering-based behavior, yet they may no longer satisfy the general `Set`/`Map` contract if ordering equivalence disagrees with `equals`.

The same two mismatch directions introduced with `Comparable` still apply when ordering comes from a `Comparator`:

```text
a.equals(b) == false
compare(a, b) == 0

a.equals(b) == true
compare(a, b) != 0
```

The first can make a sorted collection treat two logically different values as one ordering key; the second can make it retain both values even though `equals` says they are equal. The comparator-specific difference is ownership: this ordering policy is supplied externally by the caller and may vary by context rather than being fixed as the type's natural order.

If an ordering is intentionally inconsistent with `equals`, document that fact clearly so callers do not confuse sorted-collection key semantics with the object's logical equality.

```java
Set<Book> books = new TreeSet<>(Comparator.comparing(Book::title));
```

Two different books with the same title may collapse to one ordering key in that `TreeSet`. That is not a `TreeSet` bug; the comparator told the collection that the values compare as equal.

### MUTABLE COMPARISON STATE

Using mutable fields in a comparator can break ordered-collection expectations if those fields change after insertion.

```text
insert with price = 10
        ↓
tree placement based on price 10
        ↓
mutate price = 1000
        ↓
object state no longer matches its old ordering position
```

The same risk exists when a comparator depends on mutable external configuration, current time, or other state that can change between comparisons. Prefer stable ordering-relevant state and a stable comparison policy while objects are stored in tree-based structures.

## <a id="sorting-stability-boundary">Sorting Stability</a>

A stable sort preserves the encounter order of elements that compare as equal.

For input:

```text
[A(priority=1), B(priority=1), C(priority=2)]
```

if sorting only compares `priority` and the algorithm/API guarantees stability, `A` remains before `B` because they tie under the comparator.

### KEEP TWO CONTRACTS SEPARATE

```text
sort stability
→ property of the sorting algorithm/API

comparator consistency
→ property of the ordering function
```

A good comparator does not automatically make a sort stable. A stable sorting algorithm also cannot repair a comparator that violates transitivity.

Do not assume every sorting API is stable unless its contract says so.

With both natural ordering and caller-defined ordering in place, the final chapter reconnects ordering with identity, equality, hashing, text representation, and mutable-state risks so the whole module can be used as one decision model.
