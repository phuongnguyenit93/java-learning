# What Is an Object Contract?

## <a id="object-contract-purpose">What Is an Object Contract?</a>

Imagine an application stores a user's identifier, `U-17`, in a set of known users. Later, another part of the application creates a **new Java object** from the same identifier and asks the set, “Is this user already known?” A human can see that both objects carry `U-17`. The set cannot guess which fields your application considers meaningful. It needs a dependable rule for deciding whether the objects count as the same value.

That dependable rule is an example of an **object contract**: a promise about how an object behaves when other code asks it a question. In this module, *object contract* is an umbrella term, **not** a Java keyword, annotation, special interface, or single formal language construct. Some promises are defined by methods inherited from `java.lang.Object`; others are provided by comparison interfaces or a caller's comparison policy. A contract describes **behavior callers may rely on**, beyond simply providing a method with the right name and return type.

You can start here if you already know what a class, an object, a reference variable, and a method call are (covered in Class & Object). You do **not** need to know how a hash table organizes its entries internally, how to implement every override, or how to write a `record` yet; those details are introduced only where they help the example. The Collections module later explores the data structures in more depth.

### A lookup that depends on the object's promise

This complete Java 21 example tries the same `U-17` lookup with two types. `PlainUserId` is an ordinary class that inherits the default `Object` behavior. `UserId` is a Java `record`: a compact way to declare a data-carrying type for which Java generates component-based `equals`, `hashCode`, and `toString` methods. You only need to notice what those generated methods *promise* here; later chapters teach how to design the methods yourself.

```java
import java.util.HashSet;
import java.util.Set;

public class Main {
    static final class PlainUserId {
        private final String value;

        PlainUserId(String value) {
            this.value = value;
        }
    }

    record UserId(String value) {}

    public static void main(String[] args) {
        PlainUserId plainStored = new PlainUserId("U-17");
        PlainUserId plainRequested = new PlainUserId("U-17");
        Set<PlainUserId> plainIds = new HashSet<>();
        plainIds.add(plainStored);

        UserId stored = new UserId("U-17");
        UserId requested = new UserId("U-17");
        Set<UserId> ids = new HashSet<>();
        ids.add(stored);

        System.out.println(plainStored.equals(plainRequested));
        System.out.println(plainIds.contains(plainRequested));
        System.out.println(stored == requested);
        System.out.println(stored.equals(requested));
        System.out.println(ids.contains(requested));
    }
}
```

Output, in the same order as the five print statements:

```text
false
false
false
true
true
```

Follow the two lookups step by step:

1. Each `new` expression creates a distinct identifier object. Both `PlainUserId` objects store the same text, but the class has not defined *value-based equality*. Its inherited `Object.equals` treats different instances as unequal. Thus `plainStored.equals(plainRequested)` is `false`.
2. `plainIds.add(plainStored)` stores the first plain object. `plainIds.contains(plainRequested)` asks whether an equal element is present, using the set's equality rules; the second plain object is not equal to the stored one, so the answer is `false`. **This does not violate the default `Object` contract**: identity-based equality is valid, just unsuitable if the application's intent is to recognize the identifier text.
3. `stored` and `requested` are also distinct objects, so the reference comparison `stored == requested` is `false`. But a record's generated `equals` compares corresponding component values: both have `value` equal to `"U-17"`, so `stored.equals(requested)` is `true`.
4. When `ids.contains(requested)` runs, `HashSet` uses the object's `hashCode` to locate possible matches and equality to recognize the value it already holds. A record also generates matching hash codes for equal component values. The requested identifier can therefore match the stored identifier, and the result is `true`.

The change from the first lookup to the second is **the equality and hashing behavior provided by the type**, not a change to `HashSet` or to the identifier text. In a regular class, we could write suitable `equals` and `hashCode` methods ourselves; the `equals` and `hashCode` chapters explain that design. A record is a convenient demonstration, but its generated equality uses its components, which is only the right policy when those components really define the domain value.

### The promises Java libraries use

The example introduces the core relationship: when two objects are equal according to `equals`, they **must** produce the same `hashCode`. Equal hash codes alone do **not** prove equality; unrelated objects may have the same hash code. A hash-based collection uses hashing to narrow its search and equality to decide whether a candidate represents the sought value. This is why choosing logical equality and implementing compatible hashing are connected decisions.

Other libraries need different promises. The contracts in this module answer distinct questions:

| Contract | Question other code needs answered | Typical Java consumer |
| --- | --- | --- |
| `equals(Object)` | Do these objects represent the same logical value or domain entity? | Collection membership, value comparison |
| `hashCode()` | Can equal objects be placed and found consistently in hash-based lookup? | `HashSet`, `HashMap` |
| `toString()` | How should this object be described in useful text? | Logging, debugging, diagnostic messages |
| `Comparable.compareTo(...)` | What is this type's **natural order**? | Sorting and naturally ordered sets or maps |
| `Comparator.compare(...)` | What ordering does this particular caller want? | Alternative sorts and caller-configured sorted collections |

`equals`, `hashCode`, and `toString` are methods on `Object` that a class can override. `Comparable` and `Comparator` are **interfaces for ordering**, not methods inherited from `Object`. For example, a `Book` may have one natural order while a caller wants to sort by title, price, or publication year instead. Both natural and caller-supplied comparisons must behave consistently and transitively: if one value comes before a second and the second comes before a third, the first must come before the third. **Ordering equality** (a comparison result of zero) and **logical equality** (`equals` returning `true`) are related questions but are not automatically identical; sorted collections use their ordering policy to decide when elements occupy the same position. Later, `BigDecimal` illustrates an intentional difference between equality and natural ordering.

Likewise, `toString` aims for a useful representation for people and diagnostic tools; its output is not automatically a stable storage or serialization format, and it should not disclose sensitive data just to be informative. Good logs can help diagnose a mistake, but readable text does not repair broken equality or ordering.

Java's compiler generally checks method declarations, types, and signatures; it **cannot prove the semantic promises** behind these operations. Code can compile even though `HashSet` retains domain duplicates, a `HashMap` lookup fails after inconsistent hashing or key mutation, a sorted collection treats values as the same ordering position while `equals` disagrees, or logs mislead a developer. Java library and framework code relies on the behavior the type supplies rather than discovering your domain rules for itself.

### How this chapter leads into the rest of the module

The learning path begins with the meaning of “same”: two references can identify one object, or two distinct objects can represent the same logical value. Once that distinction is clear, the `equals` chapter establishes the laws a class must preserve. The `hashCode` chapter then shows how hashing assists lookup; the following chapter connects both methods so objects stay findable as well as logically equal. Those steps turn the `U-17` observation above into a design you can explain and implement.

Next, `toString` addresses a separate need: making objects understandable to people. `Comparable` introduces a type's natural order, and `Comparator` introduces alternative ordering decisions supplied by callers, with `Book` as a useful example of several possible views. The final synthesis checks that equality, hashing, representation, and ordering fit the same domain design instead of being written as unrelated methods.

The approved chapter journey is:

```text
What is an object contract, and why do Java APIs need these promises?
Object Contract Purpose
        ↓
Same object or same logical value?
Object Identity vs Logical Equality
        ↓
How should a class define logical equality?
The equals Contract
        ↓
How do hash-based collections find objects efficiently?
hashCode and Hash-Based Lookup
        ↓
Why must equals and hashCode agree?
The equals/hashCode Contract
        ↓
How should an object describe itself for humans/tools?
Text Representation with toString
        ↓
How can a type define one natural order?
Natural Ordering with Comparable
        ↓
How can callers define alternative orderings?
Alternate Ordering with Comparator
        ↓
How do all of these promises interact in real Java APIs?
Putting the Object Contracts Together
```

Throughout the module, `UserId` remains the main example for identity, equality, and hashing; `Book` helps explain representation and ordering choices. `BigDecimal` is a documented cross-module example where equality and natural ordering intentionally differ. The goal is not to memorize generated methods but to understand **which promises Java libraries expect objects to keep, and why**.

Check your understanding before moving on:

- Why does `plainIds.contains(plainRequested)` return `false` even though both objects store `"U-17"`? Which behavior would need to change to express value equality?
- Why can `stored == requested` be `false` while both `stored.equals(requested)` and `ids.contains(requested)` are `true`? What role does the hashing promise play?
- Would sorting books by title necessarily mean two books with the same title are logically equal? Which contract answers each question?

After the full module, you should be able to **choose and justify** a `UserId` equality policy, explain why its hash code must agree, predict a `HashSet`/`HashMap` lookup, produce a safe diagnostic representation, and choose a coherent natural or alternate order for `Book`. To begin that work, continue with **Object Identity vs Logical Equality** and explain the difference between the two questions in the example.

### References

- [Oracle Java SE 21 — `Object` (`equals`, `hashCode`, `toString`)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html)
- [Oracle Java SE 21 — `HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html)
- [Oracle Java SE 21 — `Record`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html)
- [Oracle Java SE 21 — `Comparable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html)
