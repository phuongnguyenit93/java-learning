# Packages and Imports

As a codebase grows, type names need organization to avoid collisions and define boundaries. A package is part of a type's **fully qualified name**; an import only lets source code use shorter names.

## <a id="package-namespace">Packages as Namespaces</a>

```java
package com.example.order;
```

makes `Order` part of the fully qualified name `com.example.order.Order`.

Different packages may contain the same simple class name.

Packages also participate in access control, not merely folder organization.

By convention, source directories mirror package names:

```text
src/main/java/com/example/order/Order.java
                    ↓
package com.example.order;
```

The important language concept is still that the package declaration contributes to the fully qualified name. The directory layout is a strong tooling/build convention rather than the definition of a package itself.

Production code normally uses lower-case, reverse-domain-style package names and avoids the unnamed/default package except for tiny experiments.

## <a id="import-resolution">Imports and Name Resolution</a>

```java
import java.util.List;
```

does not load the class into the JVM. It helps the compiler/source resolver map the simple name `List` to a type.

If simple names conflict, use a fully qualified name where necessary. `java.lang` is implicitly imported; types in the same package also need no import.

Wildcard imports do not include subpackages:

```java
import java.util.*;
```

does not import types from `java.util.concurrent`.

Imports also do not cause the JVM to eagerly load every matching class; they are source-level name-resolution declarations.

Package names may look hierarchical, but **a subpackage does not inherit package access**:

```text
com.example
com.example.internal
```

are separate packages for access-control purposes. A package-private member in `com.example` is not automatically accessible from `com.example.internal` merely because the names share a prefix.

Java has no general import-alias syntax, so conflicting simple names are commonly resolved by using a fully qualified name for one of the types.

## <a id="static-import">Static Import</a>

Static imports allow an unqualified use of a static member:

```java
import static java.lang.Math.max;
```

They can improve readability when the member's origin is obvious, but overuse can make source ownership unclear.

Static imports may target fields or methods:

```java
import static java.lang.Math.PI;
import static java.lang.Math.max;
```

They do not change access control; the imported member must still be accessible.

## <a id="package-access">Package-private Access</a>

Omitting an access modifier gives package-private visibility where the language allows it.

That lets types within one package collaborate without exposing every helper/member as public API.

A package can therefore form an encapsulation boundary for a group of types.

Helpers that do not belong in the public contract often do not need `public` visibility:

```java
class OrderValidator {
    boolean valid(Object order) {
        return order != null;
    }
}
```

This keeps external API surface smaller while allowing collaboration inside the package. Detailed access-modifier rules belong to later class/OOP modules; here the key model is package = namespace + possible access boundary.

### Package-name hierarchy is not an access hierarchy

For example:

```java
// package com.example;
class InternalHelper {
    static void run() { }
}
```

code declared in:

```java
package com.example.internal;
```

is not considered to be "inside the parent package" `com.example` for package-private access. Crossing that package boundary requires an appropriately visible API rather than relying on a shared name prefix.

The next chapter returns to the special reference value `null`.
