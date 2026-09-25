# StringBuilder

## <a id="builder-mutable-buffer">StringBuilder mutable buffer model</a>
`StringBuilder` is a mutable sequence used to build text without creating a new String for every intermediate append/insert/delete. It is not a String subtype; call `toString()` when an immutable String result is needed.

## <a id="builder-capacity">Length vs capacity</a>
Length is the number of characters currently stored; capacity is the internal storage available before growth is needed. Capacity grows automatically and is an implementation/performance concern, not part of the text value. Pre-sizing can help when size is predictable, but measure before micro-optimizing.

## <a id="builder-usage">Efficient incremental construction</a>
Use one builder for a local construction flow, append pieces, then convert once. Avoid sharing a mutable builder across unrelated callers. Fluent append calls improve readability, but complex formatting may be clearer with formatters/templates rather than manual delimiter logic.
