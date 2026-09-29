# Enum

An `enum` is not merely a collection of integer-like constants. Each enum constant is **an instance of the enum type** and may carry fields, constructors, methods, and behavior.

## <a id="enum-type-model">Enum Constants Are Instances</a>

```java
enum AccountStatus {
    ACTIVE, FROZEN, CLOSED
}
```

`AccountStatus.ACTIVE` is a singleton object defined by the enum contract, not an integer alias.

Because each constant has stable identity, `==` is appropriate and commonly preferred for enum comparison.

## <a id="enum-fields-constructors">Enum Fields, Constructors and Methods</a>

Enums may own data and behavior. For example, an account tier can carry a stable external code:

```java
enum AccountTier {
    STANDARD("STD"),
    PREMIUM("PRM");

    private final String code;

    AccountTier(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
```

Enum constructors are used to build declared constants; application code cannot invoke them directly.

## <a id="enum-interface">Enum Implementing Interfaces</a>

An enum may implement interfaces, letting the closed set of constants act as typed implementations of a capability.

```java
interface FeePolicy {
    int feeFor(int amount);
}

enum FeeTier implements FeePolicy {
    STANDARD {
        public int feeFor(int amount) { return amount / 100; }
    },
    PREMIUM {
        public int feeFor(int amount) { return 0; }
    }
}
```

This is often clearer than a large switch when behavior genuinely belongs to each constant.

## <a id="enum-constant-specific">Constant-specific Behavior</a>

Individual constants may provide distinct implementations of abstract/overridable enum methods.

In the `FeeTier` example, calling `feeFor(...)` dispatches to the behavior of the selected constant. That gives polymorphic behavior inside a closed set of known instances.

## <a id="enum-values-valueof">values, valueOf, name and ordinal</a>

`values()` returns constants in declaration order. `valueOf(String)` resolves an exact constant name.

```java
AccountStatus[] all = AccountStatus.values();
AccountStatus status = AccountStatus.valueOf("ACTIVE");
```

`name()` is the declared identifier. `ordinal()` is only the declaration position and should **not** be used as a persistent business/database code because reordering constants changes it.

Use an explicit field for stable external identifiers.

Next we ask what “copying an object” actually means.
