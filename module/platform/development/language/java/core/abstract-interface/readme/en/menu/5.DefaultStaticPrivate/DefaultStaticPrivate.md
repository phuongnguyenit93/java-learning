# Default, Static and Private Interface Methods

## <a id="default-method">Default methods</a>
A default method is an inheritable instance implementation declared in an interface. It lets an interface evolve by adding behavior without immediately forcing every existing implementation to add a method body, while preserving virtual instance dispatch.

## <a id="static-interface-method">Static interface methods</a>
Static interface methods belong to the interface type and are not inherited as instance methods by implementing classes. Call them through the interface name. They are useful for factories, validators, and utilities tightly related to the contract.

## <a id="private-interface-method">Private interface helpers</a>
Private interface methods allow default/static methods to share implementation details without exposing another contract member to implementers. They are internal code-reuse helpers and cannot be overridden or called by implementing classes.

## <a id="default-method-evolution">Interface evolution compatibility</a>
Default methods were introduced partly so libraries could evolve interfaces while retaining source/binary compatibility for many existing implementations. They do not make every interface change compatible: adding abstract methods, creating default conflicts, or changing contracts can still break clients.
