# Nested and Inner Classes

## <a id="static-nested-class">Static nested class</a>
A static nested class is namespaced inside another class but has no implicit outer-instance reference. It behaves much like another top-level class with additional access/naming relationships to its enclosing class. Use it when a helper type logically belongs to the enclosing API but does not need an enclosing object.

## <a id="inner-class">Inner class and outer instance</a>
A non-static member inner class is associated with an enclosing instance and can access its members, including private ones under Java nest access rules. Creating it normally requires an outer instance, and retaining the inner object can also retain the outer object.

## <a id="local-anonymous-class">Local and anonymous classes</a>
Local classes are declared inside a block; anonymous classes create an unnamed subtype/implementation at the expression site. They are useful for one-off behavior but can become noisy compared with lambdas when only a functional interface implementation is needed; full lambda semantics belong to the functional module.

## <a id="capture-semantics">Captured local variables</a>
Local/anonymous classes can capture local variables only when those variables are final or effectively final. The captured value is stable even though referenced mutable objects may still mutate. This restriction avoids pretending a changing stack local is shared as a normal mutable variable.
