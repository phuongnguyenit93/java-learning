# Initialization Blocks

## <a id="static-initializer">Static initializer</a>
A static initializer block runs during class initialization and can perform multi-statement setup for static state. It should remain deterministic and lightweight: expensive I/O, environment-dependent work, or recoverable business initialization usually belongs elsewhere.

## <a id="instance-initializer">Instance initializer</a>
Instance initializer blocks run for every object creation after superclass construction and before the constructor body, interleaved with instance field initializers in textual order. They are shared across constructors, but helper methods or constructor delegation are often clearer.

## <a id="initializer-use-cases">Initializer use cases and readability</a>
Initializers are useful when Java syntax requires initialization near declarations or when anonymous/local-class patterns benefit from shared setup. Because initialization order is subtle, prefer simple field initializers and constructors unless a block materially improves clarity.
