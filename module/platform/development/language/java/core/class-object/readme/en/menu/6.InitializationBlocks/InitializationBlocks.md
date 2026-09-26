# Initialization Blocks

Java can initialize state through field initializers, static blocks, instance initializer blocks, and constructors. Understanding the role of each location prevents construction code from becoming hard to follow.

## <a id="static-initializer">Static Initializer</a>

```java
static {
    ...
}
```

runs when the class is initialized, not once per object.

It can support static setup that is awkward as a single expression, but complex logic or I/O in static initialization makes startup and failure handling harder to control.

## <a id="instance-initializer">Instance Initializer</a>

```java
{
    ...
}
```

runs for each object creation after superclass construction and before the current constructor body according to initialization rules.

It can share logic across constructors, but constructors/helpers are often easier to read.

## <a id="initializer-use-cases">When to Use Initializers</a>

Use initializer blocks because they make the lifecycle clearer, not simply because the language allows them.

```text
simple field initializer
→ clear default state

constructor/helper
→ validation and context-rich initialization

initializer block
→ only when it genuinely clarifies shared initialization
```

The next chapter combines these pieces into the exact initialization order.
