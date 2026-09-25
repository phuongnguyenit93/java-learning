# throw and throws

## <a id="throw-statement">throw statement</a>
`throw expression` transfers control by throwing one `Throwable` object. Code after an unconditional throw in the same path is unreachable. Throw the most meaningful exception object available and include context without leaking sensitive data.

## <a id="throws-clause">throws declaration</a>
A `throws` clause declares exception types a method may propagate under the checked-exception rules. It does not throw anything by itself and it does not guarantee that the method will actually fail. Unchecked exceptions may be documented in `throws` but need not be declared for compilation.

## <a id="precise-rethrow">Precise rethrow typing</a>
Java's compiler can infer a narrower set of checked exceptions when a catch parameter is effectively final and the caught values came from known alternatives. Rethrowing the catch variable can therefore preserve a more precise `throws` contract than its syntactic catch type suggests.

## <a id="override-throws-rules">Overriding methods may not broaden checked exceptions</a>
An overriding method cannot add a broader/new checked exception that the parent contract did not allow. Otherwise code compiled against the parent type could encounter a checked failure it was never required to handle.

## <a id="checked-exception-narrowing">Overrides may keep, narrow or remove checked exceptions</a>
A child override may declare the same checked exception, a subtype, or no checked exception. Unchecked exceptions are not constrained by the same rule. This is part of behavioral substitutability: using the subtype through a parent reference must remain compatible with the parent's checked contract.
