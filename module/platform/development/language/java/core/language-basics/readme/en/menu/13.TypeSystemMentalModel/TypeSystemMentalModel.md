# Java Type System Mental Model

## <a id="compile-time-vs-runtime-type">Compile-time type vs runtime type</a>
A reference expression has a compile-time type used for member access, overload resolution, and assignment checks. The object it references may have a more specific runtime class used for overridden instance-method dispatch.

```java
Animal a = new Dog();
// compile-time type: Animal; runtime class: Dog
```

## <a id="assignment-compatibility">Assignment compatibility</a>
Assignments are accepted only when the source can be converted to the target according to Java's type rules. Reference widening is normally safe; downcasting requires an explicit cast and runtime check. Primitive conversions follow their own widening/narrowing rules.

## <a id="overload-vs-override-dispatch">Overload selection vs override dispatch</a>
Overloads are chosen at compile time from declared types and applicable conversions. Overrides are selected at runtime from the actual receiver object after a method signature has already been chosen. Mixing these two stages causes many incorrect predictions.

## <a id="type-system-boundaries">Type-system guarantees and runtime checks</a>
The compiler prevents many incompatible operations, but runtime checks remain where static typing cannot prove safety: downcasts, array stores, null dereference, class-loader identity, and some reflection. A good mental model separates compiler guarantees from runtime validation.
