# Enum

## <a id="enum-type-model">Enum constants are instances</a>
An enum is a special class with a fixed set of named instances. Each constant is a singleton instance per initialized enum class, can be compared by identity, and has a stable `name()`. Enums can implement interfaces and define behavior just like other classes within their language restrictions.

## <a id="enum-fields-constructors">Enum fields, constructors and methods</a>
Enum constructors are not invoked directly by callers. They initialize constants declared in the enum and can accept arguments, populate final fields, and enforce invariants. Enum fields and methods are useful when each constant carries domain metadata or common behavior.

## <a id="enum-interface">Enum implementing interfaces</a>
An enum can implement one or more interfaces, making the fixed constants usable through an abstraction. This is useful for strategies with a closed set of implementations while keeping consumers decoupled from concrete constant names.

## <a id="enum-constant-specific">Constant-specific behavior</a>
A constant can provide a class body that overrides behavior. This supports a compact strategy-like design, but large business workflows hidden inside enum constants can become difficult to maintain; use it for behavior truly intrinsic to the finite set.

## <a id="enum-values-valueof">values/valueOf/name/ordinal boundaries</a>
Compiler-generated `values()` returns constants in declaration order; `valueOf` resolves the exact constant name. `ordinal()` is positional metadata and should not be persisted as a durable business identifier because reordering constants changes it. Prefer explicit stable codes when external storage/protocols are involved.
