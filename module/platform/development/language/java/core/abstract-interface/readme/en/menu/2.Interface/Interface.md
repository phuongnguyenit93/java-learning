# Interface

## <a id="interface-contract">Interface as behavioral contract</a>
An interface names a role/contract that classes, enums, records, or proxies can implement. Consumers can depend on the behavior without knowing the implementation class. An interface does not imply statelessness; it simply does not carry per-instance fields of its own.

## <a id="interface-fields">Interface fields are public static final</a>
Fields declared in an interface are implicitly `public static final` and must be initialized. They are constants associated with the interface, not instance state. Mutable objects behind such references remain mutable, so avoid exposing mutable global state as interface fields.

## <a id="interface-method-kinds">Abstract/default/static/private method kinds</a>
Interfaces may declare abstract instance methods, default instance methods, static methods, and private helper methods. Each has different inheritance/dispatch behavior: abstract/default methods participate in instance contracts, static methods belong to the interface type, and private methods support internal reuse only.

## <a id="interface-implementation">Implementing multiple contracts</a>
A class can implement multiple interfaces, allowing one object to play several roles without multiple class inheritance. Conflicting inherited defaults must be resolved explicitly, while unrelated abstract contracts can simply be implemented together.
