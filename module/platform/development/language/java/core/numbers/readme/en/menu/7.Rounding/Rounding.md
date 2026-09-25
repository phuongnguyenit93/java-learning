# Rounding

## <a id="rounding-modes">RoundingMode semantics</a>
`RoundingMode` defines how a discarded fraction changes the retained value. `UP`/`DOWN` are away/toward zero, `CEILING`/`FLOOR` follow positive/negative infinity, and HALF modes define tie behavior. `UNNECESSARY` asserts that no rounding should be needed and throws otherwise.

## <a id="rounding-at-boundaries">When rounding actually occurs</a>
Rounding occurs only when an operation or representation policy discards precision/scale. Repeatedly rounding intermediate values can produce a different final result from rounding once at the defined business boundary. Keep full precision through internal steps unless the domain says otherwise.

## <a id="financial-rounding-policy">Rounding policy as domain decision</a>
There is no universally correct financial rounding mode. Tax, interest, currency conversion, invoice-line rounding, and settlement may use different rules. Encode the policy explicitly, test boundary/tie cases, and keep it near the domain decision instead of scattering `setScale(2, ...)` throughout code.
