# What Are Annotations and Why Does Java Need Them?

## <a id="annotation-model">What Is an Annotation and Why Does It Exist?</a>

Before learning `@Something` syntax, answer a simpler question:

> **What is an annotation for?**

Sometimes a program does not need more **execution logic**. It needs to **say something about existing code so the compiler, a tool, or a framework can understand that fact**.

Consider an overriding mistake:

```java
class Parent {
    void process() {
    }
}

class Child extends Parent {
    void proccess() { // typo
    }
}
```

Java can accept `proccess()` as a new method. The compiler does not know that the developer intended to override `process()`.

Add an annotation:

```java
class Child extends Parent {
    @Override
    void proccess() {
    }
}
```

Now the compiler receives an extra statement:

```text
"This method is intended to override a supertype method."
```

and can reject the mistake.

That is the core idea:

> **An annotation is a structured label/information attached directly to code so a compiler, tool, framework, or another program can learn something about that code.**

The annotation is not the method's business logic. It is **information about the method/class/field/type**.

The technical word `metadata` simply means **data that describes other data or program elements**. Annotations are Java's language-level way to attach such metadata to code.

The module will reuse `@Audit` as a running example:

```java
@Audit(action = "TRANSFER")
void transfer() {
}
```

`transfer()` is the behavior. `@Audit(...)` says something about that behavior for another consumer to interpret.

### What do you need to know before this module?

This module assumes basic Java syntax such as classes, methods, and fields. `@Override` refers to inheritance/overriding from OOP; `@SafeVarargs` touches generics; `@FunctionalInterface` touches lambdas.

You do **not** need prior knowledge of:

- what annotations are;
- how reflection APIs work;
- what an annotation processor is;
- how frameworks such as Spring, JPA, or JUnit consume annotations.

The module explains the annotation-side contract at the point where each mechanism first matters and explicitly hands deeper mechanics to their owning modules.

### REINFORCE THE MODEL — read annotations as structured descriptive metadata

A simple annotation looks like:

```java
@Something
class MyClass {
}
```

Read it as:

```text
MyClass
→ the program element

@Something
→ extra information about MyClass
```

For example:

```java
@Deprecated
class LegacyPayment {
}
```

`@Deprecated` does not delete or disable the class. It gives tools a machine-readable fact:

```text
LegacyPayment
→ still an ordinary class

@Deprecated
→ this API is discouraged for continued use

compiler / IDE / documentation tool
→ reads that fact
→ warns or presents it appropriately
```

### WHY — what if annotations did not exist?

Part of the problem can be solved with simpler mechanisms:

| Mechanism | What it can do | Limitation |
| --- | --- | --- |
| Comment | Explain intent to a human | Compiler/framework has no structured contract to interpret it |
| Naming convention | A tool can parse names | Metadata is mixed into business names and conventions drift |
| Separate config/map | Keeps metadata outside source | Configuration can drift away from code during refactoring |
| Annotation | Keeps typed, machine-readable metadata next to code | Best for relatively static metadata with a clear consumer |

Annotations exist for questions such as:

```text
"What is special about this piece of code?"
```

rather than:

```text
"Which statements should this method execute?"
```

Examples:

```text
@Override
→ this method intends to override

@Deprecated
→ this API is discouraged

@Test
→ a test framework should treat this method as a test

@GetMapping("/users")
→ a web framework should treat this method as an HTTP handler
```

The last two are library/framework examples rather than JDK annotations, but they show why annotations are so common in Java applications: **a framework can discover declarations from metadata instead of requiring developers to register everything manually in imperative code**.

### WHO — who actually reads an annotation?

An annotation does nothing by itself. A **consumer** must read it.

Consumers include:

- the Java compiler, such as for `@Override`;
- IDEs and documentation tools;
- annotation processors during compilation;
- frameworks using runtime metadata;
- application code performing explicit runtime inspection.

The most important mental model in the module is:

```text
code
  +
annotation
  ↓
consumer reads annotation
  ↓
consumer validates / warns / generates / registers / creates runtime behavior
```

> **The annotation describes. The consumer acts on that description.**

Seeing `@Something` alone does not tell you its behavior; you must know **who consumes it and what contract that consumer defines**.

This also keeps three concepts separate:

```text
annotation
→ metadata

reflection
→ one runtime mechanism that can read some annotation metadata

framework / AOP
→ a consumer may use annotation metadata to decide behavior
```

Annotation is therefore not another name for reflection, AOP, interception, dependency injection, or validation.

### RELATION — how does the rest of the module fit together?

Once annotation means **metadata on code for a consumer**, the chapter order becomes a cause-and-effect story:

```text
What can an annotation look like and contain?
→ Syntax / elements
        ↓
Which contracts does Java already provide?
→ Built-in annotations
        ↓
How do we define our own metadata vocabulary?
→ Custom annotation
        ↓
How long must metadata survive?
→ Retention
        ↓
Where is the annotation legal?
→ Target
        ↓
How does an annotation type describe its own contract?
→ Meta-annotations
        ↓
What changes when annotations repeat or participate in superclass lookup?
→ Repeatable / Inherited
        ↓
How can a compile-time tool consume annotations to validate/generate artifacts?
→ Annotation Processing
        ↓
When are annotations the clearest design, and when should another mechanism be used?
→ When Should You Use Annotations?
```

By the end of the module, you should be able to look at any annotation and ask:

```text
1. What does it describe?
2. Who consumes it?
3. Where may it be used?
4. How long does it survive?
5. What does the consumer do with that metadata?
6. Is an annotation actually the clearest representation for this problem?
```

## <a id="annotation-syntax">Annotation Syntax and Elements</a>

An annotation use begins with `@` followed by the annotation type name:

```java
@Override
public String toString() {
    return "payment";
}
```

A custom annotation may declare named **elements**. Supplying an annotation is similar to supplying a small immutable metadata record:

```java
@Audit(action = "PAYMENT", level = 2)
void pay() {
}
```

If an annotation has an element named `value`, Java permits a shorthand when that is the only element being supplied:

```java
@Label("core")
class PaymentService {
}
```

instead of:

```java
@Label(value = "core")
class PaymentService {
}
```

Multiple annotation elements are separated by commas, and array-valued elements use braces when multiple values are present:

```java
@RolesAllowed({"admin", "auditor"})
void export() {
}
```

Annotation values are part of the declaration metadata. They are not arbitrary runtime expressions evaluated when the annotated method executes.

The values written in an annotation use must also be representable by Java's annotation-value model at compile time: compatible constant expressions, class literals, enum constants, nested annotations, or arrays of valid annotation values. An annotation value cannot be produced with an arbitrary method call or `new SomeObject()` expression.

The next chapter examines annotations from the JDK itself. They show an important pattern: an annotation becomes useful only when a consumer gives its metadata a precise contract.
