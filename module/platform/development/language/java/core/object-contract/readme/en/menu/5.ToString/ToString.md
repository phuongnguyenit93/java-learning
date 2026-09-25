# toString

## <a id="tostring-purpose">toString purpose</a>
`toString` provides a human-oriented diagnostic representation of an object. `Object.toString` exposes class/identity-like information; domain classes often override it to show meaningful state useful in logs, debugging, and tests.

## <a id="tostring-design">Useful deterministic representation</a>
A good representation is concise, stable enough for humans, and makes important fields understandable without implying it is a serialization format. Avoid expensive computation, hidden I/O, or behavior that can throw unexpectedly during logging/debugging.

## <a id="tostring-sensitive-data">Sensitive-data/logging boundary</a>
Never include passwords, tokens, secrets, full payment data, or other sensitive values merely because they are fields. `toString` is often called implicitly by logging, concatenation, IDEs, and error handling, so its data exposure surface is wider than explicit call sites suggest.
