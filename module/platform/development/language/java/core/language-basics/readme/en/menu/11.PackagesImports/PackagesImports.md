# Packages and Imports

As a codebase grows, type names need organization to avoid collisions and define boundaries. A package is part of a type's **fully qualified name**; an import only lets source code use shorter names.

## <a id="package-namespace">Packages as Namespaces</a>

```java
package com.example.order;
```

makes `Order` part of the fully qualified name `com.example.order.Order`.

Different packages may contain the same simple class name.

Packages also participate in access control, not merely folder organization.

## <a id="import-resolution">Imports and Name Resolution</a>

```java
import java.util.List;
```

does not load the class into the JVM. It helps the compiler/source resolver map the simple name `List` to a type.

If simple names conflict, use a fully qualified name where necessary. `java.lang` is implicitly imported; types in the same package also need no import.

## <a id="static-import">Static Import</a>

Static imports allow an unqualified use of a static member:

```java
import static java.lang.Math.max;
```

They can improve readability when the member's origin is obvious, but overuse can make source ownership unclear.

## <a id="package-access">Package-private Access</a>

Omitting an access modifier gives package-private visibility where the language allows it.

That lets types within one package collaborate without exposing every helper/member as public API.

A package can therefore form an encapsulation boundary for a group of types.

The next chapter returns to the special reference value `null`.
