# Custom Exceptions

## <a id="custom-exception-purpose">When custom exceptions add meaning</a>
Create a custom exception when the failure represents a stable domain/application concept that callers, logs, or handlers benefit from recognizing. Do not create one new class for every message; the type should communicate a meaningful category or contract.

## <a id="exception-context">Preserving useful context and cause</a>
Include the information needed to understand the failed operation—such as an order ID or requested state—while avoiding secrets and giant object dumps. When wrapping a lower-level failure, provide a constructor that accepts the original cause and preserve it.

## <a id="exception-hierarchy-design">Designing a small domain exception hierarchy</a>
Keep hierarchies shallow and purposeful. A common domain base exception can support one handling policy while specific subtypes represent genuinely different recovery/status decisions. Checked vs unchecked should follow the API contract rather than a naming convention.
