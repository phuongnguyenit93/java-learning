# this and super

Inside instance code, Java needs a way to refer to **the current object** and to **superclass construction/member context**. `this` and `super` express those roles.

## <a id="this-reference">this</a>

`this` is the reference to the current object in instance context.

Common uses include:

- distinguishing fields from same-named parameters;
- passing the current object elsewhere;
- invoking another constructor through `this(...)`;
- returning the current instance in fluent APIs when appropriate.

```java
this.name = name;
```

Static context has no `this` because there is no implicit current instance.

## <a id="super-access">super</a>

`super` accesses superclass constructors/members according to Java's lookup rules.

```java
super(provider);
super.toString();
```

`super` is not a second object nested inside the subclass object. It changes how the source refers to superclass context on the same object.

## <a id="constructor-chaining-order">this()/super() Chaining</a>

Every constructor chain eventually reaches a superclass constructor.

```text
this(...)
→ another constructor in the same class
→ eventually super(...)
```

Constructor invocation follows special first-step rules in Java construction.

Understanding this chain prepares us for initialization order later. First, the next chapter asks who may access each member.
