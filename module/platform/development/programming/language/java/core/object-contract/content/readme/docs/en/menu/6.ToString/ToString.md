# Text Representation with toString

Imagine a failed operation that prints only `Ticket@5e2de80c`. You know which Java class was involved, but not which ticket needs attention. A useful `toString()` turns an object into a short, readable description at the moment someone inspects it.

This chapter treats `toString` as the **text-representation contract** alongside `equals` and `hashCode`. Equality asks whether objects represent the same logical value; hashing supports hash-based lookup; `toString` helps people diagnose what an object contains. The text itself does not determine equality or hashing.

## <a id="tostring-purpose">Purpose of toString</a>

Every Java class inherits `public String toString()` from `Object` unless it overrides it. Calling this method asks the object for a textual representation. The Java API recommends a concise, informative string that is easy for a person to read, but it does not enforce a domain-specific format.

### From the default output to a meaningful label

The implementation inherited from `Object` returns:

```text
getClass().getName() + "@" + Integer.toHexString(hashCode())
```

That is a runtime class name, an `@` sign, and the object's **hash code expressed in hexadecimal**. It is not a guaranteed unique ID or a memory address. In particular, if a class overrides `hashCode()`, this suffix uses that overridden result. Two distinct objects can have the same hash code.

Save the following as `ToStringBasics.java` and run it with Java 21 (`javac ToStringBasics.java`, then `java ToStringBasics`):

```java
class RawTicket {
    // A constant hash is for a predictable demonstration only.
    @Override
    public int hashCode() {
        return 7;
    }
}

class Ticket {
    private final String code;

    Ticket(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return "Ticket[code=" + code + "]";
    }
}

public class ToStringBasics {
    public static void main(String[] args) {
        RawTicket raw = new RawTicket();
        Ticket ticket = new Ticket("T-42");

        System.out.println(raw);
        System.out.println(ticket);
        System.out.println("ticket=" + ticket);
        System.out.println(String.valueOf((Object) null));
    }
}
```

Output:

```text
RawTicket@7
Ticket[code=T-42]
ticket=Ticket[code=T-42]
null
```

`RawTicket` inherits `Object.toString()`: its deliberately fixed hash code makes `@7` predictable. `Ticket` overrides `toString()`, so both printing the object and concatenating it with a string expose its useful `code`. Changing the code supplied to the constructor would change the diagnostic label; changing only a hash code would not change `Ticket`'s chosen format. Constant hash codes are **not** a recommendation for implementing `hashCode()` in production.

### Why an override matters to callers

`System.out.println(ticket)`, string concatenation such as `"ticket=" + ticket`, and `String.valueOf(ticket)` can request an object's text without an explicit `ticket.toString()` call. Logging frameworks, debuggers, assertion failures, and collection formatting can also expose the representation, although each API has its own invocation rules.

The final output line illustrates an important distinction: `String.valueOf((Object) null)` returns the text `"null"`, while calling `toString()` directly on a `null` reference throws `NullPointerException`.

`@Override` asks the compiler to verify that a method really overrides an inherited declaration; it cannot certify whether the resulting description is clear, fast, or safe. A normal domain object's `toString()` should remain a human-oriented description. The JDK does not promise that its format will remain stable across releases or JVM runs, and an application should not treat it as an object's persistent identifier.

### References

- [Java SE 21 — Object.toString()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#toString())
- [Java SE 21 — String.valueOf(Object)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#valueOf(java.lang.Object))

## <a id="tostring-design">Designing a Useful Representation</a>

Once you decide to override `toString()`, choose the information a developer actually needs to recognize the object and its relevant state. A book might need its ID and title, not every field or every object reachable from it. A helpful representation is usually **short, understandable, inexpensive, and predictable enough for diagnosis**.

### Choose a small diagnostic view

This runnable example prints a book and two linked nodes. A node's `next` field refers to another node, and the links form a cycle:

```java
public class ToStringDesign {
    static final class Book {
        private final String id;
        private final String title;
        private final int pages;

        Book(String id, String title, int pages) {
            this.id = id;
            this.title = title;
            this.pages = pages;
        }

        @Override
        public String toString() {
            return "Book[id=" + id + ", title=" + title + "]";
        }
    }

    static final class Node {
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Node[name=" + name
                    + ", next=" + (next == null ? "none" : next.name) + "]";
        }
    }

    public static void main(String[] args) {
        Book book = new Book("B-7", "Java Basics", 340);
        Node first = new Node("first");
        Node second = new Node("second");
        first.next = second;
        second.next = first;

        System.out.println(book);
        System.out.println(first);
        System.out.println(second);
    }
}
```

Save it as `ToStringDesign.java`, compile and run it as above. Output:

```text
Book[id=B-7, title=Java Basics]
Node[name=first, next=second]
Node[name=second, next=first]
```

The book has a `pages` field, but the output omits it because ID and title were chosen as the useful summary. Both nodes have a `next` reference: `first → second → first`. When `first.toString()` runs, it reads only `second.name`; it does **not** ask `second` to produce its complete `toString()`. That boundary lets both nodes print once despite the cycle.

Had the node used `", next=" + next`, converting `first` would call `second.toString()`, which would call `first.toString()` again, repeating until a `StackOverflowError`. This is a reasoning example of a dangerous alternative, deliberately **not** executed. The same risk occurs indirectly when nested object or collection representations call each other. Prefer stable IDs, names, or a limited summary; use a dedicated cycle-aware formatter when a full graph is genuinely required.

### Representation is not serialization

The two jobs have different promises:

| `toString()` for diagnosis | JSON, protobuf, or a dedicated data format |
| --- | --- |
| Selected details that help a human read a log or failure | Explicit fields and rules meant for software to consume |
| Can change as diagnostics improve | Requires its own compatibility and parsing policy |
| Should avoid surprising cost and sensitive values | Must apply its own schema, validation, and data-exposure rules |

If another process must reconstruct a `Book`, define a real data format instead of splitting the string `Book[id=..., title=...]`. A slight punctuation change in a diagnostic label should not break persistence or integrations.

A practical override should also avoid disk, database, or network I/O; expensive traversal; and unexpected mutations. Error handling or logging may invoke it when the program is already failing, so throwing a new exception from formatting can obscure the original failure. Output may change if mutable state changes, and that alone does not violate the contract; aim for *repeatable formatting for a given visible state*, not an invented guarantee that object state never changes.

## <a id="tostring-sensitive-data">Sensitive Data and Logging</a>

Readable output has a wider audience than its author might expect. A password, access token, payment credential, personal identifier, or confidential field included in `toString()` can flow into logs, exception messages, assertion failures, debuggers, and nested container output. A useful diagnostic must therefore show **only information approved for that destination**.

### Observe a leak through an indirect call

Java records are concise data carriers. Unless overridden, the compiler provides `toString()` using the record's component names and values; it does **not** automatically redact secrets.

Save this as `ToStringSecurity.java` and run it with Java 21:

```java
import java.util.List;

public class ToStringSecurity {
    record ExposedLogin(String user, String password) {}

    record SafeLogin(String user, String password) {
        @Override
        public String toString() {
            return "SafeLogin[user=" + user + ", password=<redacted>]";
        }
    }

    public static void main(String[] args) {
        ExposedLogin exposed = new ExposedLogin("ana", "demo-secret");
        SafeLogin safe = new SafeLogin("ana", "demo-secret");

        System.out.println(exposed);
        System.out.println("attempt=" + safe);
        System.out.println(List.of(exposed));
    }
}
```

Java 21 output:

```text
ExposedLogin[user=ana, password=demo-secret]
attempt=SafeLogin[user=ana, password=<redacted>]
[ExposedLogin[user=ana, password=demo-secret]]
```

The first line exposes the illustrative secret because the generated record representation includes `password`. The second uses an explicit override that hides it. The third shows that `List.toString()` can expose its elements' representations even if the call site never mentions `ExposedLogin.toString()`. The exact syntax of a generated record's output is not a parsing guarantee and may change in the JDK.

Redaction here controls **only this textual method**; it does not erase the field from memory or prevent another logger, serializer, debugger, or API from disclosing it. For many diagnostic events, the safest choice is to log a separate minimal message or request ID instead of logging the credential-bearing object at all. If partial identifiers are allowed, apply the application's data-handling rules consistently; never assume that masking a few characters makes every value safe.

### Practical review before printing

- **Necessity:** Do the selected fields actually help investigate the event? Omit the rest.
- **Exposure:** Could any printed field be a secret, personal value, or a reference to an object whose `toString()` exposes one?
- **Behavior:** Can formatting recurse through a cycle, trigger expensive I/O, mutate state, or fail while handling another error?
- **Consumer:** Is a person reading a diagnostic, or does another program need stable structured data? The latter needs an explicit data contract.

An intentional `toString()` improves diagnostics, but it is **not a security boundary or a serialization protocol**. The next chapters turn from describing objects to ordering them with `Comparable` and `Comparator`.

### References

- [Java SE 21 — Record.toString()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html#toString())
