# Class Initialization Triggers and Failure

Loading and linking prepare a class for use without necessarily running its static setup. Initialization is the point at which the JVM executes that setup. This separation allows frameworks to inspect or stage types without automatically performing every static side effect they contain.

Initialization is also a concurrency and failure boundary: the JVM coordinates it so a particular runtime class is initialized once, and a failed initialization leaves that class in an erroneous state.

## <a id="initialization-trigger">Active Use Triggers Initialization</a>

Initialization is delayed until the JVM sees an **active use** that requires the class or interface to be initialized. The purpose of the delay is practical: loading metadata does not always mean the program needs to execute that type's static setup immediately.

Common initialization triggers include:

- creating an instance of a class with `new`;
- invoking a `static` method declared by the class or interface;
- reading a `static` field declared by the class or interface when that field is not a compile-time constant;
- assigning a `static` field declared by the class or interface;
- reflective operations that explicitly request initialization, such as `Class.forName("...")` with initialization enabled.

Before a class is initialized, its superclass is initialized first and the JVM also initializes the required superinterfaces that declare default methods according to the initialization rules. This does not mean every superinterface is initialized. Initializing an interface also does not automatically initialize all of its superinterfaces merely because they are inherited.

For example:

```java
final class PluginBootstrap {
    static {
        System.out.println("PluginBootstrap initialized");
    }

    static void start() {
        System.out.println("plugin system started");
    }
}

public class Demo {
    public static void main(String[] args) {
        PluginBootstrap.start();
    }
}
```

The first invocation of `PluginBootstrap.start()` requires `PluginBootstrap` to be initialized before the method executes. The static block therefore runs first.

Later sections in this chapter go deeper into `<clinit>`, synchronization, failure state, and compile-time constants. The key relationship is that **loading makes a class available to the runtime; active use is what normally forces initialization**.

## <a id="constant-no-init">Compile-time Constants May Avoid Initialization</a>

One active-use rule has an important exception: reading a `static final` **constant variable** can be compiled as the constant value itself and therefore may not initialize the declaring class.

Before the constant case, keep two other important **non-triggers** in mind:

```java
Class<?> type = PluginConstants.class;
```

Obtaining the class literal does **not by itself initialize** `PluginConstants`.

Also, if source code writes `SubType.SOME_STATIC_FIELD` but that field is actually declared by `SuperType`, then for a static-field access that **really triggers initialization**—for example, reading a non-constant static field—the initialized type is the **class/interface that declares the field**. Merely naming a subclass on the left side does not initialize that subclass. If the field is a compile-time constant, the exception below still applies: the read may initialize neither `SuperType` nor `SubType`.

```java
final class PluginConstants {
    static {
        System.out.println("PluginConstants initialized");
    }

    static final int API_VERSION = 3;
    static final String NAME = "hello";

    static final Integer BOXED_VERSION = 3;
    static final int RUNTIME_VERSION = Integer.parseInt("3");
}
```

These reads can behave differently:

```java
System.out.println(PluginConstants.API_VERSION);     // may be inlined; no init required
System.out.println(PluginConstants.NAME);            // may be inlined; no init required

System.out.println(PluginConstants.BOXED_VERSION);   // initializes class
System.out.println(PluginConstants.RUNTIME_VERSION); // initializes class
```

A Java constant variable must satisfy specific language rules: roughly, it is a `final` primitive or `String` initialized with a compile-time constant expression. `Integer` is a reference type outside that constant-variable rule, and `Integer.parseInt("3")` is a runtime call.

Inlining also has a binary-compatibility consequence. If a library changes a public compile-time constant from `3` to `4`, already-compiled client bytecode may still contain `3` until the client is recompiled.

This section reinforces the lifecycle distinction from chapter 1: mentioning or even reading something through a class name does not automatically prove that initialization occurred. Observe the exact operation and the exact runtime class identity.

## <a id="clinit-model">The `<clinit>` Model</a>

At source level, Java lets a class declare static field initializers and static initializer blocks:

```java
final class PluginEnvironment {
    static String mode = loadMode();

    static {
        System.out.println("mode = " + mode);
    }

    private static String loadMode() {
        return System.getProperty("plugin.mode", "safe");
    }
}
```

The JVM class-file model represents class or interface initialization through a special method named `<clinit>` when one is needed. You do not declare or invoke `<clinit>` directly in Java source; the compiler derives it from static initialization work.

Within one class, static field initializers and static blocks execute in their source order:

```java
final class OrderTrace {
    static int first = trace("first", 1);

    static {
        trace("block", 2);
    }

    static int second = trace("second", 3);

    private static int trace(String name, int value) {
        System.out.println(name);
        return value;
    }
}
```

When initialization occurs, the observable order is `first`, `block`, `second`.

This is conceptually later than preparation. During preparation, static storage already exists with JVM default values. During initialization, source-level initialization expressions assign their intended values and execute static blocks.

## <a id="initialization-once">Once per Runtime `Class` Object</a>

The JVM initializes a particular class identity at most once successfully. Repeated active uses do not rerun its static initialization.

```java
final class PluginMetrics {
    static {
        System.out.println("initializing PluginMetrics");
    }

    static void record() {}
}

PluginMetrics.record();
PluginMetrics.record();
```

The initialization message appears once for that runtime class.

The Class Identity chapter explains why the phrase **that runtime class** matters. If two different ClassLoaders each define `demo.plugins.PluginMetrics`, the JVM has two different `Class<?>` identities:

```text
(demo.plugins.PluginMetrics, loader A)
(demo.plugins.PluginMetrics, loader B)
```

Each identity has its own static fields and its own initialization lifecycle. Therefore the same class bytes can execute static initialization once under loader A and once again under loader B.

This is a common source of surprises in plugin systems and containers. A "singleton" implemented with a static field is singleton-like only inside one particular class identity, not automatically across every ClassLoader in the JVM.

The prerequisite-ordering rules described earlier still apply to each of these runtime identities. The important point here is ownership of state: each distinct runtime class identity has its own initialization state and reaches the initialized state independently.

## <a id="initialization-locking">Initialization Is Synchronized</a>

Two threads can reach the first active use at nearly the same time. The JVM therefore coordinates class initialization using a lock associated with the runtime class initialization process.

Conceptually:

```text
Thread A                         Thread B
   │                               │
first active use                first active use
   │                               │
owns initialization               └─ waits
   │
runs static initialization
   │
completes successfully
   │                               │
   └───────────────────────────────┴─ both continue with initialized class
```

This is why application code does not need to add its own lock merely to guarantee that a class's static initializer executes once.

Recursive requests from the thread already performing initialization are handled by the JVM initialization algorithm rather than starting a second initialization. Other threads wait until the first initialization either succeeds or fails.

The coordination also creates a design warning: static initialization should avoid large dependency graphs, blocking I/O, or lock cycles. Two classes whose initializers wait on each other through different threads can contribute to initialization deadlocks that are difficult to diagnose.

Prefer simple deterministic static setup. If startup requires external services, retries, cancellation, or explicit shutdown, an application lifecycle component is often a clearer place than a static initializer.

## <a id="initialization-failure">Failure Creates an Erroneous Class State</a>

Initialization failure is not treated like an ordinary method call that can simply be retried on the next access.

Consider:

```java
final class BrokenPluginConfig {
    static final String TOKEN = loadToken();

    private static String loadToken() {
        throw new IllegalStateException("missing plugin token");
    }
}
```

On the first active use, the runtime exception escapes the class initialization logic. Because the thrown value is not already an `Error`, the JVM reports the initialization failure as an `ExceptionInInitializerError`:

```java
try {
    System.out.println(BrokenPluginConfig.TOKEN);
} catch (ExceptionInInitializerError error) {
    System.out.println(error.getCause());
}
```

The class is then considered **erroneous** for that defining loader. A later active use does not rerun `loadToken()` as a retry mechanism. It fails again, commonly with `NoClassDefFoundError` indicating that the class could not be initialized.

```text
first active use
  → initialization code throws
  → ExceptionInInitializerError for a non-Error cause
  → class identity becomes erroneous

later active use of same class identity
  → no retry of <clinit>
  → NoClassDefFoundError / initialization failure state
```

If initialization itself throws an `Error`, the JVM does not need to wrap that first failure in `ExceptionInInitializerError`; the exact first throwable therefore depends on what escaped the initializer.

In an isolated plugin architecture, unloading the failed loader and creating a **new loader that defines a new class identity** can produce a fresh initialization lifecycle. The old erroneous class itself does not become healthy again.

This behavior is why important recoverable configuration should usually be validated explicitly rather than hidden inside static initialization.

That runtime identity also owns the initialization state just discussed. The final chapter follows that identity to its lifecycle boundary: when a plugin is stopped, when can the defining ClassLoader and the classes it owns actually become reclaimable, and which longer-lived references can prevent that from happening?
