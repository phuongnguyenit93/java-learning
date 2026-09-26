# Enum

An `enum` is not merely a collection of integer-like constants. Each enum constant is **an instance of the enum type** and may carry fields, constructors, methods, and behavior.

## <a id="enum-type-model">Enum Constants Are Instances</a>

```java
enum Status {
    NEW, PAID, CANCELLED
}
```

`Status.NEW` is a singleton object defined by the enum contract, not an integer alias.

Because each constant has stable identity, `==` is appropriate and commonly preferred for enum comparison.

## <a id="enum-fields-constructors">Enum Fields, Constructors and Methods</a>

Enums may own data and behavior:

```java
enum Currency {
    USD(2), JPY(0);

    private final int fractionDigits;

    Currency(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }
}
```

Enum constructors are used to build declared constants; application code cannot invoke them directly.

## <a id="enum-interface">Enum Implementing Interfaces</a>

An enum may implement interfaces, letting the closed set of constants act as typed implementations of a capability.

This is often clearer than a large switch when behavior genuinely belongs to each constant.

## <a id="enum-constant-specific">Constant-specific Behavior</a>

Individual constants may provide distinct implementations of abstract/overridable enum methods.

That gives polymorphic behavior inside a closed set of known instances.

## <a id="enum-values-valueof">values, valueOf, name and ordinal</a>

`values()` returns constants in declaration order. `valueOf(String)` resolves an exact constant name.

`name()` is the declared identifier. `ordinal()` is only the declaration position and should **not** be used as a persistent business/database code because reordering constants changes it.

Use an explicit field for stable external identifiers.

Next we ask what “copying an object” actually means.
