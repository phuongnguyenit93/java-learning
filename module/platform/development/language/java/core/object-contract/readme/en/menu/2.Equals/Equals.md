# equals Contract

## <a id="equals-contract">equals contract: reflexive/symmetric/transitive/consistent/null</a>
A correct `equals` implementation is reflexive, symmetric, transitive, consistent while relevant state does not change, and returns false for `null`. Collections and other libraries assume these laws even though the compiler cannot enforce them.

## <a id="equals-implementation">Typical equals implementation</a>
A value-style implementation usually checks identity fast-path, type compatibility, then compares all fields that define equality. The chosen type test (`getClass` vs `instanceof`) affects inheritance semantics. Use null-safe comparisons for nullable components and keep `hashCode` based on the same equality state.

## <a id="equals-inheritance-risk">Inheritance and equality symmetry risk</a>
Extending a concrete value class with additional equality-relevant state can make symmetry/transitivity difficult. A parent may consider the child equal while the child requires extra fields. Prefer composition, sealed/closed designs, or careful class-based equality when value semantics and inheritance conflict.
