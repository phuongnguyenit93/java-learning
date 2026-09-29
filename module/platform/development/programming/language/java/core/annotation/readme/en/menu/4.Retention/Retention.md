# Retention Policies

An annotation can be useful during source compilation, remain in the generated class file, or stay available to runtime reflection. Retention policy makes that lifetime explicit.

```text
source code
    ↓ compile
class file
    ↓ load
runtime Class model

SOURCE  → source-processing lifetime
CLASS   → class-file lifetime
RUNTIME → class-file + runtime reflection lifetime
```

## <a id="retention-source">SOURCE Retention</a>

`RetentionPolicy.SOURCE` means the annotation is discarded by the compiler rather than recorded in the generated class file.

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface GeneratedCheck {
}
```

SOURCE retention fits metadata whose consumer operates while source is being compiled, for example certain compiler checks or annotation processors.

`@Override` and `@SuppressWarnings` are familiar JDK examples with SOURCE retention. Their purpose is fulfilled during compilation; runtime code does not need to discover them reflectively.

### RELATION — SOURCE does not mean “processors cannot see it”

Annotation processing happens during compilation, before SOURCE-retained metadata is discarded from the output class file.

```text
source annotation
→ compiler/processor can observe it during compilation
→ annotation omitted from class file
→ runtime reflection cannot retrieve it later
```

This distinction becomes important in the Annotation Processing chapter.

## <a id="retention-class">CLASS Retention</a>

`RetentionPolicy.CLASS` records the annotation in the class file but does not require the JVM to expose it through runtime reflection.

```java
@Retention(RetentionPolicy.CLASS)
public @interface BytecodeHint {
}
```

CLASS is also the default when an annotation type declares no `@Retention` meta-annotation.

This lifetime is useful when bytecode tools or build-time post-processors need the metadata after Java compilation but the application itself does not need reflective access.

```text
compiler output
→ annotation attribute remains in .class data

ordinary runtime reflection
→ do not design around retrieving it
```

The presence of metadata in a class file and reflective visibility are therefore different contracts.

## <a id="retention-runtime">RUNTIME Retention</a>

`RetentionPolicy.RUNTIME` records the annotation in the class file and makes it available for runtime reflective inspection.

For the running auditing example:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

A runtime component can then inspect a method:

```java
Method method = PaymentService.class.getDeclaredMethod("pay");
Audit audit = method.getAnnotation(Audit.class);

if (audit != null) {
    System.out.println(audit.action());
}
```

RUNTIME retention only makes retrieval possible. It does not cause the JVM to invoke business behavior automatically.

Detailed reflection APIs, accessibility rules, and generic runtime metadata belong to the reflection module. Here the important boundary is the annotation lifetime contract.

### EDGE CASE — local declarations and type annotations are different channels

Declaration annotations on local-variable declarations and lambda formal-parameter declarations are not retained in the binary through the ordinary declaration-annotation channel, even when the annotation type declares `CLASS` or `RUNTIME` retention. A type annotation on the type used in such a context is a separate metadata channel with its own class-file/runtime representation rules.

So “RUNTIME retention” does not mean every source annotation occurrence automatically becomes runtime declaration metadata. Retention must always be read together with target and use-site semantics.

## <a id="retention-use-case">Choose Retention by Consumer</a>

Choose retention from the consumer's latest required observation point:

| Consumer need | Typical retention |
| --- | --- |
| Compiler check or source annotation processor only | `SOURCE` |
| Class-file/bytecode tooling after compilation | `CLASS` |
| Runtime framework or reflective library | `RUNTIME` |

Do not select `RUNTIME` merely because it feels more capable. Runtime retention enlarges the metadata that survives into deployed class files and exposes it to runtime consumers even when no runtime use exists.

For `@Audit`, a runtime interceptor that inspects methods requires `RUNTIME`. A compile-time generator that turns the same metadata into generated code could work with `SOURCE` or `CLASS` depending on its pipeline.

### PRACTICE — ask when, not where

Retention answers **when the metadata must still exist**. It does not answer where the annotation may be written. The next chapter handles that separate question through `@Target` and `ElementType`.
