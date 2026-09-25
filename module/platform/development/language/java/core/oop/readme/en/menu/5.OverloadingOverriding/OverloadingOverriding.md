# Overloading and Overriding

## <a id="overloading-compile-time">Overloading is compile-time selection</a>
Overloaded methods share a name but differ in parameter types. The compiler chooses a signature using the compile-time argument types and invocation conversions. Runtime object type does not cause Java to reconsider a different overload after compilation.

## <a id="overriding-runtime">Overriding is runtime dispatch</a>
A subclass override provides a new implementation of an inherited instance-method contract with a compatible signature. Once the signature is selected, the runtime receiver class chooses the most specific override body.

## <a id="covariant-return">Covariant return types</a>
An overriding method may narrow a reference return type to a subtype of the parent's declared return type. This improves precision for callers of the subtype while preserving the supertype contract.

## <a id="override-rules">Visibility/final/static/private overriding boundaries</a>
An override cannot reduce accessibility and must respect checked-exception compatibility. `final` instance methods cannot be overridden. `private` methods are not inherited as override targets, and static methods participate in hiding rather than runtime overriding.

## <a id="static-method-hiding">Static methods are hidden, not overridden</a>
When a subclass declares a static method with the same signature, selection is based on the compile-time qualifying type, not receiver runtime class.

```java
Parent p = new Child();
p.staticCall(); // Parent static method
```

Calling static methods through instances is legal in some contexts but misleading; use the declaring class name.

## <a id="field-hiding">Field selection follows the compile-time reference type</a>
Fields are not virtual. If parent and child declare the same field name, the selected field is determined from the compile-time type of the expression. This is hiding, not polymorphic state dispatch.

## <a id="dispatch-vs-hiding">Instance-method dispatch vs static-method and field hiding</a>
For `Parent x = new Child()`, an overridden instance method can execute `Child` behavior, while `x.someField` and a hidden static method resolve through `Parent`. Keeping these mechanisms separate prevents the common misconception that “everything on an object dispatches dynamically”.
