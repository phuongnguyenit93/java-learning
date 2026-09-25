# Access Modifiers

## <a id="access-levels">private/package/protected/public</a>
`private` restricts access to the declaring top-level/nest context according to Java rules; no modifier gives package-private access; `protected` combines same-package access with controlled subclass access; `public` exposes the member wherever the declaring type is accessible. Access control is checked at compile time and reflection has its own access boundary.

## <a id="protected-cross-package">Protected access across packages</a>
Across packages, a subclass does not gain arbitrary access to a protected member through any superclass instance. Access is tied to subclass context and the qualifying expression rules. This subtlety is why `protected` should not be described simply as “package + subclasses everywhere”.

## <a id="encapsulation-boundary">Access control as encapsulation boundary</a>
Modifiers define visibility, but encapsulation is broader: invariants, mutation pathways, ownership, and API design matter too. `private` fields with unrestricted setters are technically hidden but may still expose a weak object model.
