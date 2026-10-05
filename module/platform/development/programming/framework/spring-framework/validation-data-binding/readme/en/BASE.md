# Spring Validation and Data Binding

This module explains Spring Framework's reusable infrastructure for turning external or textual values into typed object state, validating that state, and reporting failures in a structured form.

It focuses on property access, type conversion, field formatting, Spring validation, DataBinder orchestration, Jakarta Bean Validation integration, and safe binding decisions.

## Why this module exists

Without a shared binding and validation model, each layer would need to invent its own rules for converting text, applying values to objects, representing failures, and deciding whether the resulting object is valid.

Spring separates these concerns so they can be reused outside any single transport or UI framework.

## Learning flow

1. Understand why validation, binding, conversion, and formatting are separate concerns.
2. Learn the property-access and binding-result model.
3. Learn Spring's general type-conversion system.
4. Add field formatting and locale-sensitive parse/print behavior.
5. Learn Spring Validator, structured errors, hints, and message-code resolution.
6. Connect the pieces through DataBinder, including constructor, property, and Spring Framework 6.1 declarative binding.
7. Integrate Jakarta Bean Validation and executable method validation.
8. Synthesize the module into a safe end-to-end binding decision model.

## Prerequisites

The learner should already understand ordinary Java objects, properties, constructors, annotations, exceptions, and basic Spring container concepts.

## Module boundary

This module owns reusable Spring Framework contracts such as Validator, Errors, BindingResult, DataBinder, ConversionService, the Converter family, Formatter infrastructure, and Spring's Bean Validation integration.

It deliberately stops before transport-specific controller binding:

- Spring MVC owns WebDataBinder, controller argument binding, @InitBinder, and the HTTP-specific validation lifecycle.
- Spring WebFlux owns its reactive controller binding and validation lifecycle.
- Spring Core Container owns MessageSource and i18n after this module has produced structured errors and resolvable message codes.
- Spring AOP owns the proxy/interceptor mechanics used by proxy-based method-validation wiring.

## Expected outcome

After completing the module, the learner should be able to choose the correct conversion, formatting, binding, and validation mechanism; explain how DataBinder coordinates them; reason about Spring Framework 6.1 declarative binding; and design a safe handoff from raw input to typed, validated application objects.
