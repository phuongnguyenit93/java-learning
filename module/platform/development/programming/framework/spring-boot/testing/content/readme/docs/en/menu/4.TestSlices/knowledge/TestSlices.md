<a id="back-to-top"></a>

# Choosing the Smallest Useful Boot Test Slice

## Menu
- [What Problem Does a Boot Test Slice Solve?](#test-slice-purpose)
- [How Does a Slice Restrict Component Scanning and Context Scope?](#test-slice-context-boundary)
- [How Does a Slice Select Purpose-Specific Auto-Configuration?](#test-slice-auto-configuration-model)
- [Why Should a Test Start from One `@...Test` Slice?](#single-slice-rule)
- [How Do You Choose the Smallest Slice That Still Proves the Behavior?](#smallest-slice-decision)
- [When Does a Slice Need to Hand Off to a Full Context?](#slice-full-context-handoff)

## <a id="test-slice-purpose">What Problem Does a Boot Test Slice Solve?</a>

<details>
<summary>Click for details</summary>
A Boot test slice loads a deliberately restricted application context for one class of behavior. Instead of starting every application bean and every applicable auto-configuration, the slice selects the components and test infrastructure relevant to a focused layer such as MVC, WebFlux, JPA, or JDBC.

The benefit is not only speed. A smaller context makes the boundary under test explicit and reduces unrelated failures. The trade-off is that behavior depending on omitted layers cannot be proven by that slice.

### References

- [Spring Boot 3.3 — Test Slices](https://docs.spring.io/spring-boot/3.3/appendix/test-auto-configuration/slices.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="test-slice-context-boundary">How Does a Slice Restrict Component Scanning and Context Scope?</a>

<details>
<summary>Click for details</summary>
Slice annotations use type-exclusion filters and focused scanning rules so only relevant application components are discovered. A web slice, for example, selects controller-oriented infrastructure rather than every service and repository in the application.

This restriction is intentional. When a collaborator is absent, first ask whether the slice was designed to exclude it before treating the missing bean as a broken application.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-slice-auto-configuration-model">How Does a Slice Select Purpose-Specific Auto-Configuration?</a>

<details>
<summary>Click for details</summary>
Each slice imports a curated set of test and application auto-configurations appropriate to its goal. The exact list differs across annotations and is documented in Boot's test-auto-configuration appendix.

This is why a slice can still feel “Boot-aware” even though it is not a full `@SpringBootTest` context: selected Boot configuration remains active, but unrelated auto-configuration is intentionally absent.

</details>

- [Back to top](#back-to-top)

---

## <a id="single-slice-rule">Why Should a Test Start from One `@...Test` Slice?</a>

<details>
<summary>Click for details</summary>
Boot does not support using multiple `@...Test` slice annotations on the same test. Choose one slice as the primary test boundary instead of stacking slices together.

Choose the slice that best represents the behavior, then add narrowly targeted support from another slice through the relevant `@AutoConfigure...` annotation when available. Use `@ImportAutoConfiguration`, `@Import` for user configuration, or mock/test beans only when the test specifically needs that additional support.

</details>

- [Back to top](#back-to-top)

---

## <a id="smallest-slice-decision">How Do You Choose the Smallest Slice That Still Proves the Behavior?</a>

<details>
<summary>Click for details</summary>
Start from the observable behavior rather than the production package structure. If the test proves controller mapping and serialization, a web slice may be enough. If it proves JPA mappings and queries, a data slice is a better boundary. If it proves cross-layer startup and wiring, use the full context.

The smallest useful slice is the smallest context that still contains every boundary whose cooperation the assertion depends on. Making a context smaller than that only produces false confidence or excessive mocking.

</details>

- [Back to top](#back-to-top)

---

## <a id="slice-full-context-handoff">When Does a Slice Need to Hand Off to a Full Context?</a>

<details>
<summary>Click for details</summary>
Move to `@SpringBootTest` when the behavior fundamentally crosses slice boundaries, depends on broad auto-configuration, requires production-like startup, or needs an actual web server. Avoid continuously importing more production configuration into a slice until it silently resembles the full application.

The handoff is a design decision: slices prove focused integration; full contexts prove cooperation across a larger Boot application boundary.

</details>

- [Back to top](#back-to-top)
