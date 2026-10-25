# Alternate Ordering with Comparator

`Comparable` places one natural order inside a type. `Comparator<T>` moves ordering policy **outside the type**, allowing the same data to be viewed through many orderings without changing the domain class.

A comparator answers a concrete question: **given two values, which should appear first in this particular view?** Like `compareTo`, `compare(a, b)` returns a negative number, zero, or a positive number depending on whether `a` comes before, ties with, or comes after `b`. Only the sign matters.

This is an important design distinction:

```text
Comparable
→ the type owns one natural order

Comparator
→ the caller/context owns an ordering policy
```

## <a id="external-order">Custom Ordering</a>

Imagine a book catalog displayed to readers by title but displayed to shoppers by price. The **books are the same objects**; only the ordering requested by the caller changes. Other views might sort by:

- title;
- publication date;
- price;
- rating;
- a compound business key.

The domain class should not need to change for every view. This is a complete Java 21 example; save it as `ComparatorCustomOrderDemo.java`, then run `javac --release 21 ComparatorCustomOrderDemo.java` and `java ComparatorCustomOrderDemo`:

```java
import java.util.Comparator;
import java.util.List;

public class ComparatorCustomOrderDemo {
    record Book(int id, String title, int year, int priceCents) {}

    public static void main(String[] args) {
        List<Book> books = List.of(
                new Book(1, "Java", 2022, 3000),
                new Book(2, "Algorithms", 2024, 4500),
                new Book(3, "Java", 2024, 2500)
        );

        Comparator<Book> byTitle = Comparator.comparing(Book::title);
        Comparator<Book> byPrice = Comparator.comparingInt(Book::priceCents);

        System.out.println("title: " +
                books.stream().sorted(byTitle).map(Book::id).toList());
        System.out.println("price: " +
                books.stream().sorted(byPrice).map(Book::id).toList());
        System.out.println("original: " + books.stream().map(Book::id).toList());
    }
}
```

Output (numbers are book IDs):

```text
title: [2, 1, 3]
price: [3, 1, 2]
original: [1, 2, 3]
```

`Comparator.comparing(Book::title)` extracts each title and compares those strings; `comparingInt(Book::priceCents)` compares numeric prices without boxing. `"Algorithms"` sorts before `"Java"`, whereas the cheapest price is ID 3. `stream().sorted(...)` creates a sorted result without modifying the original list, so the same catalog can serve both views. IDs 1 and 3 tie by title; their observed relative order is explained in the Sorting Stability section.

Choosing `byTitle`, `byPrice`, or another comparator changes **how callers order the books**. It does not rewrite the record's generated `equals` or redefine domain equality: the books with IDs 1 and 3 remain unequal records even though their titles compare as equal. Ordering and logical equality are separate policies, although sorted collections make their interaction observable.

A correct `Comparator` also imposes a **total order of its comparison groups** over the values it is designed to compare: a result of zero puts two values in the same ordering group, even when `equals` says they differ. If the policy permits `null`, the comparator must define where that value belongs.

### NATURAL ORDER AND BUSINESS VIEW ARE NOT THE SAME THING

A type may have a natural order and still need contextual comparators. Choose `Comparable` when a type owns one widely understood default order; choose `Comparator` when the caller owns a view, needs alternative orders, or cannot modify the class.

For example, `Version` may naturally order by semantic version while a UI needs to display releases by date. `Comparable` should not be redefined merely to serve one screen.

## <a id="comparator-composition">Comparator Composition</a>

Java provides factory and composition helpers that let you describe ordering as a sequence of policies.

```java
// Add these declarations inside main() of the Book example above.
Comparator<Book> byTitleThenYear =
        Comparator.comparing(Book::title)
                  .thenComparingInt(Book::year);
```

First compare titles. **Only when the titles compare as zero** does `thenComparingInt` inspect the year: among IDs 1 and 3, both titled `"Java"`, ID 1 (2022) comes before ID 3 (2024). For IDs 2 and 1, the titles already decide the order, so the year cannot override it. Each tie-breaker narrows a comparison group left by the earlier criteria.

### TIE-BREAKERS

If a comparator only checks title:

```text
Book(id=1, title="Java", year=2022)
Book(id=3, title="Java", year=2024)
```

it may return `0` even though the two books are different domain values. For list sorting that simply means they tie under this comparator. For `TreeSet`/`TreeMap`, `compare(...) == 0` may cause them to be treated as the same ordering key.

Design tie-breakers intentionally:

```java
Comparator<Book> byTitleThenYearThenId =
        Comparator.comparing(Book::title)
                  .thenComparingInt(Book::year)
                  .thenComparingInt(Book::id);
```

This order uses title first, year second, and ID only when both earlier keys tie. If IDs are unique in your domain, the last stage prevents *different IDs* from collapsing to one `TreeSet` ordering key. Adding `id` does **not** automatically make this comparator consistent with all possible `equals` implementations; decide which fields define equality before promising consistency.

### PRIMITIVE-SPECIALIZED HELPERS

When the key is primitive, specialized helpers avoid unnecessary boxing and express intent clearly:

```java
Comparator<Book> priceAscending = Comparator.comparingInt(Book::priceCents);
Comparator<Book> byYearThenId =
        Comparator.comparingInt(Book::year)
                  .thenComparingInt(Book::id);
```

For `long` and `double` keys, Java also provides `comparingLong` / `thenComparingLong` and `comparingDouble` / `thenComparingDouble`. Avoid implementing numeric comparison as `a.priceCents() - b.priceCents()`; subtraction can overflow and produce the wrong sign.

### `reversed()` AND COMPOSITION ORDER

```java
Comparator<Book> newestFirst =
        Comparator.comparingInt(Book::year)
                  .reversed();
```

Read comparator expressions as policy pipelines. The position of `reversed()` matters, especially once several comparison stages are composed.

For example, these two policies are different:

```java
// Reverse the complete chain: title, year, AND id descending.
Comparator<Book> allDescending =
        Comparator.comparing(Book::title)
                  .thenComparingInt(Book::year)
                  .thenComparingInt(Book::id)
                  .reversed();

// Keep title ascending, but reverse ONLY the year tie-breaker.
Comparator<Book> yearDescending =
        Comparator.comparingInt(Book::year).reversed();
Comparator<Book> titleAscYearDesc =
        Comparator.comparing(Book::title)
                  .thenComparing(yearDescending);
```

Using the earlier books, `allDescending` produces IDs `[3, 1, 2]`: `"Java"` comes before `"Algorithms"`, and within `"Java"` 2024 comes before 2022. `titleAscYearDesc` produces `[2, 3, 1]`: `"Algorithms"` remains first, but 2024 now comes before 2022 within `"Java"`.

Composition therefore expresses **which criterion is primary, which criterion only breaks ties, and which part of the policy is reversed**. Reversing a composed comparator is not the same operation as reversing one individual comparison stage.

### NULL HANDLING IS AN EXPLICIT POLICY

```java
// Place null titles last, while still requiring a non-null Book.
Comparator<Book> byTitleNullLast =
        Comparator.comparing(
                Book::title,
                Comparator.nullsLast(Comparator.naturalOrder())
        );

// Also permit a null Book value and place it after actual books.
Comparator<Book> booksNullLast = Comparator.nullsLast(byTitleNullLast);
```

`nullsFirst` and `nullsLast` let the caller state null semantics explicitly instead of relying on accidental failure. These are two separate questions: can a `Book` itself be `null`, and can a *non-null* book have a `null` title? The inner comparator handles the title; the outer comparator handles the book. The original `byTitle` rejects either situation with a `NullPointerException` rather than silently assigning an order.

## <a id="comparator-contract">The Comparator Contract</a>

A comparator must define a coherent ordering **for every value in the set it claims to support**, not merely produce plausible output for one sample list. The following properties concern the *sign* of `compare(a, b)`; the exact negative or positive number is not significant.

### SIGN ANTISYMMETRY

If:

```text
compare(a, b) < 0
```

then the reverse comparison must give:

```text
compare(b, a) > 0
```

More generally, `sign(compare(a, b))` must equal `-sign(compare(b, a))`; this also requires both directions to return zero together. Otherwise the comparator could claim that `a` comes before `b` while also claiming that `b` comes before `a`.

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

A circular “rock-paper-scissors” comparator does not define a valid total ordering for sorting or tree structures. A comparator cannot say `a < b`, `b < c`, and `c < a` for the same unchanging values.

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

This makes values that are equivalent under the ordering behave as one coherent ordering class. The two `"Java"` books from the opening example both compare after the `"Algorithms"` book by title; no third title may distinguish between those two members of the `"Java"` group unless a tie-breaker is explicitly part of the comparator.

### A BROKEN COMPARATOR: INTEGER OVERFLOW

The tempting comparator `(a, b) -> a - b` is incorrect for unrestricted `int` values. Consider `Integer.MAX_VALUE` and `-1`: subtraction overflows in both directions and returns a negative result in each case. Both comparisons claim their *first* argument should come before the other, violating sign antisymmetry. Use `Integer.compare`, `Comparator.naturalOrder()`, or `comparingInt` instead. The runnable program below demonstrates the exact signs.

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

This complete example uses Java 21 records, whose generated `equals` checks both components. Save as `ComparatorContractDemo.java` and run with `javac --release 21 ComparatorContractDemo.java` and `java ComparatorContractDemo`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class ComparatorContractDemo {
    record Book(int id, String title) {}

    public static void main(String[] args) {
        Book first = new Book(1, "Java");
        Book second = new Book(2, "Java");
        Comparator<Book> byTitle = Comparator.comparing(Book::title);

        System.out.println("equals: " + first.equals(second));
        System.out.println("compare == 0: " + (byTitle.compare(first, second) == 0));

        TreeSet<Book> tree = new TreeSet<>(byTitle);
        System.out.println("tree adds: " + tree.add(first) + ", " + tree.add(second));
        System.out.println("tree size: " + tree.size());

        List<Book> list = new ArrayList<>(List.of(first, second));
        list.sort(byTitle);
        System.out.println("list size: " + list.size());

        Comparator<Integer> broken = (a, b) -> a - b;
        System.out.println("broken signs: " +
                Integer.signum(broken.compare(Integer.MAX_VALUE, -1)) + ", " +
                Integer.signum(broken.compare(-1, Integer.MAX_VALUE)));
        System.out.println("safe signs: " +
                Integer.signum(Integer.compare(Integer.MAX_VALUE, -1)) + ", " +
                Integer.signum(Integer.compare(-1, Integer.MAX_VALUE)));
    }
}
```

```text
equals: false
compare == 0: true
tree adds: true, false
tree size: 1
list size: 2
broken signs: -1, -1
safe signs: 1, -1
```

`TreeSet` first inserts ID 1, then rejects ID 2 because `byTitle` reports zero; a sorted `List` keeps both elements. That is not a `TreeSet` bug: the comparator told the tree that they are one ordering key. The arithmetic example shows an entirely different defect: subtraction creates an internally invalid comparator even if the objects' equality design is fine. Fix ordering arithmetic separately from deciding which domain values should count as equal.

### MUTABLE COMPARISON STATE

Using mutable fields in a comparator can break ordered-collection expectations if those fields change after insertion. This is a risk even for a comparator whose mathematical rules were otherwise correct at the moment of insertion.

```text
insert with price = 10
        ↓
tree placement based on price 10
        ↓
mutate price = 1000
        ↓
object state no longer matches its old ordering position
```

The tree does not automatically remove and reinsert that entry at its new position. Lookups, iteration order, or removal can therefore behave unexpectedly. The same risk exists when a comparator depends on mutable external configuration, current time, or other state that can change between comparisons. Prefer stable ordering-relevant state and a stable comparison policy while objects are stored in tree-based structures; if the key must change, remove it **before mutation** and reinsert afterward while maintaining a consistent search policy.

## <a id="sorting-stability-boundary">Sorting Stability</a>

A stable sort preserves the **input encounter order** of elements that compare as equal. Stability is useful when an ordering policy deliberately leaves ties: a customer queue can sort by priority while preserving the arrival order of requests with the same priority.

For input:

```text
[A(priority=1), C(priority=2), B(priority=1), D(priority=0)]
```

if sorting only compares `priority` and the algorithm/API guarantees stability, the resulting order is `[D, A, B, C]`. `D` moves ahead because its priority is lowest; `A` remains before `B` because they tie and `A` arrived earlier.

Java 21 specifies that `List.sort` is stable. Save this full example as `ComparatorStabilityDemo.java` and run it with `javac --release 21 ComparatorStabilityDemo.java` and `java ComparatorStabilityDemo`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class ComparatorStabilityDemo {
    record Ticket(String code, int priority) {}

    public static void main(String[] args) {
        List<Ticket> incoming = new ArrayList<>(List.of(
                new Ticket("A", 1),
                new Ticket("C", 2),
                new Ticket("B", 1),
                new Ticket("D", 0)
        ));
        Comparator<Ticket> byPriority = Comparator.comparingInt(Ticket::priority);

        incoming.sort(byPriority);
        System.out.println("list: " + incoming.stream().map(Ticket::code).toList());

        TreeSet<Ticket> uniquePriorities = new TreeSet<>(byPriority);
        uniquePriorities.addAll(incoming);
        System.out.println("tree: " +
                uniquePriorities.stream().map(Ticket::code).toList());
    }
}
```

```text
list: [D, A, B, C]
tree: [D, A, C]
```

`List.sort` keeps both tied tickets and preserves their earlier A-before-B order. `TreeSet` has a different purpose: it keeps one element per **comparator ordering key**, so it rejects B after A because both have priority 1. No sort-stability guarantee can make `TreeSet` retain duplicates that its comparator treats as the same key.

### KEEP TWO CONTRACTS SEPARATE

```text
sort stability
→ property of the sorting algorithm/API

comparator consistency
→ property of the ordering function
```

A good comparator does not automatically make a sort stable. A stable sorting algorithm also cannot repair a comparator that violates transitivity. Adding a tie-breaker (for example, `thenComparing(Ticket::code)`) makes A and B *non-tied*, so their order is chosen by the comparator rather than protected by stability.

Check the API rather than assuming every ordering operation is stable: Java's `List.sort`, `Collections.sort`, and `Arrays.sort` for object arrays are stable; `Stream.sorted` is stable for **ordered streams**, while unordered streams have no stability guarantee. The concept also requires a meaningful prior encounter order, such as a list or ordered stream. A `TreeSet` is a sorted set rather than a stable list-sorting operation.

With both natural ordering and caller-defined ordering in place, the final chapter reconnects ordering with identity, equality, hashing, text representation, and mutable-state risks so the whole module can be used as one decision model.
