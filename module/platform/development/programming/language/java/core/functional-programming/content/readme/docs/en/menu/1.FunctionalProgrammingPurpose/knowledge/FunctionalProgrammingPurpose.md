# What Functional Programming in Java Is and Why It Matters

## <a id="functional-programming-purpose">What Is Functional Programming in Java?</a>

In Java, functional programming means writing some parts of a program by **describing behavior that can be passed around, selected, combined, and executed later**. Java does not turn functions into a separate top-level type. Instead, a behavior value is represented through a **functional interface**, and lambdas or method references provide an implementation for that interface's single functional contract.

The key shift is from only passing data to also passing **what should be done with the data**. A method can therefore receive behavior as an input just as it receives a `String`, `List`, or domain object.

```java
boolean isAdult(int age) {
    return age >= 18;
}

Predicate<Integer> rule = age -> age >= 18;
```

The first form defines behavior as a normal method. The second form packages equivalent behavior behind the `Predicate<Integer>` contract so another API can receive it as a value.

Java remains a multi-paradigm language. Functional style is one tool alongside object-oriented and imperative code; it is especially useful when the variation in a task is the **behavior itself**.

## <a id="hard-coded-behavior-problem">The Problem with Hard-Coded Behavior</a>

Suppose a method contains every business rule directly inside its control flow:

```java
List<String> selectNames(List<String> names, String mode) {
    List<String> result = new ArrayList<>();

    for (String name : names) {
        if (mode.equals("LONG") && name.length() >= 5) {
            result.add(name);
        } else if (mode.equals("STARTS_WITH_A") && name.startsWith("A")) {
            result.add(name);
        }
    }

    return result;
}
```

The traversal and the selection rule are mixed together. Every new rule requires modifying the method, adding another branch, and retesting behavior that was otherwise unchanged.

The functional-programming mechanism addresses this kind of variation by separating two responsibilities:

- the method owns **when and where** a rule is applied;
- the caller supplies **which rule** should run.

This does not mean every `if` statement should become a lambda. Behavior parameterization is valuable when a meaningful operation is expected to vary or be reused.

## <a id="behavior-before-lambdas">Before Lambdas: Anonymous Classes as Behavior Objects</a>

Java could pass behavior before lambda expressions existed. The common technique was an anonymous class implementing an interface:

```java
Comparator<String> byLength = new Comparator<String>() {
    @Override
    public int compare(String left, String right) {
        return Integer.compare(left.length(), right.length());
    }
};
```

This works because the anonymous object has a normal Java type and its `compare` method contains the behavior. The drawback is ceremony: class construction, an override, parameter types, and method syntax surround a very small piece of logic.

Lambdas did not invent behavior passing. They provide a concise language form for supplying an implementation when the target type is a functional interface:

```java
Comparator<String> byLength =
        (left, right) -> Integer.compare(left.length(), right.length());
```

Understanding this relationship helps avoid a common misconception: a lambda is not "a method without a name" floating independently from Java's type system. It still needs a target functional-interface type.

## <a id="behavior-parameterization">Passing Behavior as a Parameter</a>

Once behavior has a type, a method can receive that behavior as a parameter:

```java
List<String> select(
        List<String> names,
        Predicate<String> rule) {

    List<String> result = new ArrayList<>();
    for (String name : names) {
        if (rule.test(name)) {
            result.add(name);
        }
    }
    return result;
}
```

Callers can now choose the variation without changing `select`:

```java
select(names, name -> name.length() >= 5);
select(names, name -> name.startsWith("A"));
```

This pattern is often called **behavior parameterization**: the stable algorithm stays in one place while a changing policy or action is supplied from outside.

The same idea appears throughout Java APIs in callbacks, comparators, event handlers, validation rules, deferred work, and many other contracts. Later chapters explain the type rules that make these values safe.

## <a id="functional-interface-bridge">Functional Interfaces as the Typed Bridge</a>

A lambda does not carry enough information by itself to define its complete Java type. The surrounding context supplies a **target type**, and that target must be a compatible functional interface.

```java
Predicate<String> nonEmpty = text -> !text.isEmpty();
Function<String, Integer> length = text -> text.length();
```

Both lambdas have one parameter, but they represent different contracts:

- `Predicate<String>` accepts a `String` and produces a `boolean`;
- `Function<String, Integer>` accepts a `String` and produces an `Integer`.

The functional interface is therefore the typed bridge between ordinary Java APIs and concise behavior syntax. It tells the compiler the parameter types, result expectations, and relevant checked-exception contract that the lambda or method reference must satisfy.

Chapter 2 develops this contract in detail before Chapter 3 applies it to lambda target typing.

## <a id="functional-multi-paradigm-boundary">Functional Style inside Multi-Paradigm Java</a>

Functional style in Java complements existing Java design rather than replacing it. A typical application may use:

- objects to model domain state and responsibilities;
- imperative statements for clear step-by-step workflows;
- functional interfaces and lambdas where behavior itself should vary;
- ordinary methods whenever naming and reuse make the code clearer.

For example, a service object can keep state and dependencies while accepting a `Predicate<Order>` to customize one decision. The presence of a lambda does not make the entire design "purely functional".

This module therefore teaches **Java's functional mechanisms**. Language-neutral theory such as referential transparency, pure functions, and immutability as paradigm-wide principles belongs to the dedicated Functional Programming paradigm module. Those ideas may be referenced here only when they help explain a Java-specific choice.

## <a id="functional-style-use-cases">When Functional Style Helps</a>

Functional style tends to help when the changing part of a design can be expressed as a small, well-defined behavior contract. Common examples include:

- choosing a condition with `Predicate<T>`;
- transforming one value into another with `Function<T, R>`;
- supplying work that produces a value later with `Supplier<T>`;
- passing ordering logic with `Comparator<T>`;
- registering callbacks or actions;
- composing small behaviors into a larger operation.

It is less helpful when converting a straightforward stateful workflow into many tiny lambdas makes control flow harder to follow. A lambda should reduce ceremony or make variation explicit; it should not hide important business steps merely to appear "functional".

A practical question is: **does passing this behavior make the caller's intent and the reusable code clearer?** If yes, a functional interface is often a good fit.

## <a id="functional-programming-module-boundary">What This Module Owns</a>

This Java Core module owns the stable Java mechanics needed to work with behavior as typed values:

- functional-interface and SAM rules;
- lambda expressions and target typing;
- variable capture and lambda scope;
- standard interfaces in `java.util.function`;
- method and constructor references;
- Java-level behavior composition;
- `Optional` as a Java API for explicit absence;
- practical issues such as side effects, checked exceptions, readability, and choosing an appropriate style.

Two neighboring areas are intentionally boundaries:

- language-neutral functional-programming theory is owned by the Functional Programming paradigm module;
- Stream pipeline semantics, laziness, reduction, collectors, ordering, and parallel processing are owned by the Stream API module.

This separation keeps the mental model clear: first learn how Java represents and types behavior, then reuse those mechanics in APIs that consume behavior.
