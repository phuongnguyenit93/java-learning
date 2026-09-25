# Multiple Interface Inheritance

## <a id="default-method-conflict">Default-method conflict resolution</a>
If unrelated interfaces provide the same default signature and neither inherited default is more specific, the implementing class must override the method to resolve the conflict. Java does not guess which behavior was intended.

## <a id="class-wins-rule">Class method wins over interface default</a>
A concrete method inherited from a class hierarchy takes precedence over an interface default with the same compatible signature. Interface defaults are fallback implementations, not a mechanism to override existing class behavior.

## <a id="explicit-super-interface">InterfaceName.super dispatch</a>
Inside an implementing class override, `InterfaceName.super.method()` can explicitly call a directly inherited interface default when language rules allow it. This is useful when the class combines defaults rather than replacing them completely.

## <a id="diamond-interface">Diamond-shaped interface inheritance</a>
A diamond is not inherently a problem. If both paths inherit the same most-specific default, the contract remains unambiguous. Conflicts arise when distinct unrelated defaults compete. This is multiple inheritance of type/behavior, not duplicated instance state.
