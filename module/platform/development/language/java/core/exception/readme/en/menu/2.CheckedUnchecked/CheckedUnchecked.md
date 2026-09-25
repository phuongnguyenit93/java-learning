# Checked and Unchecked Exceptions

## <a id="checked-exception">Checked exception compile-time contract</a>
Checked exceptions are `Exception` subtypes that are not `RuntimeException`. If a reachable operation can throw one, source code must catch it or declare it according to Java's compile-time rules. This makes the failure part of the method's static contract.

## <a id="unchecked-exception">RuntimeException semantics</a>
`RuntimeException` and its subclasses are unchecked: callers are not forced to catch/declare them. They often represent programming/precondition/state problems, but libraries may also use them for operational failures. “Unchecked” describes compiler handling, not severity or recoverability.

## <a id="checked-vs-unchecked-design">Choosing checked vs unchecked</a>
Choose based on the API contract: can/should typical callers make a meaningful recovery decision at the call site, and does forcing declaration improve clarity? Avoid converting everything to unchecked merely to remove `throws`, but also avoid checked exceptions that every layer mechanically rethrows without useful handling.
