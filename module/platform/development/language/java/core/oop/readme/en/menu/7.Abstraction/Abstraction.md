# Abstraction

## <a id="abstraction-model">Abstraction exposes essential behavior</a>
An abstraction presents the concepts and operations a caller needs while hiding implementation details that should be free to change. Interfaces, abstract classes, concrete classes, and even carefully designed functions can all form abstractions; the quality comes from the contract, not the keyword.

## <a id="program-to-abstraction">Program to abstraction</a>
Depending on an interface or stable supertype allows callers to work with multiple implementations and reduces knowledge of construction/internal details. Do not introduce an interface mechanically for every class; use one where multiple implementations, testing boundaries, architecture, or a stable role justify it.

## <a id="abstraction-leak">Leaky abstractions and trade-offs</a>
An abstraction leaks when callers must understand implementation details to use it correctly: hidden performance cliffs, provider-specific errors, ordering assumptions, or lifecycle constraints. No abstraction hides everything, so expose constraints that materially affect correctness and keep irrelevant details private.
