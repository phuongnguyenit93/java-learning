# Encapsulation

## <a id="encapsulation-model">Encapsulation is more than private fields</a>
Encapsulation means controlling access to state and implementation so callers interact through a stable abstraction. Private fields help, but a class with unrestricted getters/setters can still expose every internal decision. The real boundary is what callers are allowed to know and change.

## <a id="invariant-protection">Protecting invariants</a>
An invariant is a condition that must remain true for a valid object. Constructors/factories establish it and public methods must preserve it. Instead of exposing raw mutation, provide operations that can validate complete state transitions.

```java
account.withdraw(amount); // can enforce amount > 0 and balance rules
```

## <a id="tell-dont-ask-boundary">Behavior-oriented API vs data exposure</a>
“Tell, don't ask” is a design heuristic: ask an object to perform behavior rather than pulling all data out and making every decision elsewhere. It is not an absolute rule; reporting/read models legitimately expose data. Use it to notice when domain rules are leaking across boundaries.
