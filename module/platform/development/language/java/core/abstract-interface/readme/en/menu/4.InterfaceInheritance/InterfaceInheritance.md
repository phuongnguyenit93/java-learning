# Interface Inheritance

## <a id="interface-extends-interface">Interface inheritance</a>
An interface can extend one or more interfaces. A subinterface inherits their instance-method contracts and can add new methods or refine compatible return types. Implementers of the subinterface must satisfy the resulting combined contract.

## <a id="multiple-interface-hierarchy">Multiple parent interfaces</a>
Multiple interface inheritance composes types, not object state. It is safe when inherited contracts are compatible. Identical abstract signatures merge naturally; incompatible return types or conflicting defaults can make the hierarchy illegal or require explicit resolution.

## <a id="interface-redeclaration">Redeclaration/refinement of inherited methods</a>
A subinterface may redeclare an inherited method to add documentation/annotations, narrow a covariant return type, or turn a default method back into an abstract requirement. Redeclaration should clarify the contract rather than duplicate it without purpose.
