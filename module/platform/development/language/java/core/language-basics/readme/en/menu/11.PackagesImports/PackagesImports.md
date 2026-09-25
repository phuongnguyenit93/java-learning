# Packages and Imports

## <a id="package-namespace">Packages as namespaces</a>
A package contributes to a type's fully qualified name and groups related types. Package structure also participates in package-private/protected access rules. A directory convention normally mirrors the package name, but the language concept is the declared package.

## <a id="import-resolution">Imports and name resolution</a>
Imports let source use simple type names; they do not copy code or create runtime dependencies by themselves. `java.lang` is implicitly imported, the current package is visible, and conflicting simple names require qualification.

## <a id="static-import">Static import</a>
Static import allows unqualified access to selected static members. It can improve DSL-like code or constants but can also hide ownership. Use it when the imported name remains obvious in context.

## <a id="package-access">Package-private access boundary</a>
Omitting an access modifier gives package-private access. Types or members are then accessible only from the same package. This is useful for implementation collaboration inside a package without exposing the surface publicly.
