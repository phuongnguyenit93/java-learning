<a id="back-to-top"></a>

# Layered JAR and WAR Packaging

## Menu
- [What Problem Do Layered Archives Solve?](#layered-archive-purpose)
- [What Are the Default Spring Boot Archive Layers?](#default-layer-model)
- [Why Does Layer Order Affect Cache Reuse?](#layer-order-and-cache)
- [When Should Layering Be Customized?](#custom-layering)
- [What Does Archive Layering Not Define About Container Runtime Behavior?](#archive-layer-vs-container-runtime)

## <a id="layered-archive-purpose">What Problem Do Layered Archives Solve?</a>

<details>
<summary>Click for details</summary>

Application code changes much more often than most third-party dependencies. If every change is treated as one undifferentiated artifact layer, downstream image builds may need to copy/cache far more content than necessary. A layered Boot archive records logical groups so stable content can be separated from frequently changing application content.

Layering does not change Java dependency semantics. It is packaging metadata intended to make extraction and image construction more cache-friendly.

The layers index is therefore a description for downstream tooling, not a second copy of the application. The same classes and libraries are still present; the index adds a grouping that an extractor or image builder can honor. Removing or changing layer metadata should not change business behavior, but it can materially change rebuild and transfer efficiency.

</details>

- [Back to top](#back-to-top)

---

## <a id="default-layer-model">What Are the Default Spring Boot Archive Layers?</a>

<details>
<summary>Click for details</summary>

Spring Boot's default layered archive model separates content into four conceptual groups:

```text
dependencies
spring-boot-loader
snapshot-dependencies
application
```

Released third-party dependencies tend to change least often. Boot loader content is also stable for a given Boot baseline. Snapshot dependencies are expected to change more often, while application classes/resources usually change most often. The model gives downstream tooling a useful default without requiring every project to invent its own layer taxonomy.

The default grouping is also tied to change frequency, not business architecture. Local module dependencies belong to the application layer, while non-project released dependencies go to dependencies and SNAPSHOT dependencies get their own layer. That means a multi-module build can behave differently from a build that consumes the same internal library only after it has been published to a repository.

This is a packaging decision based on what Boot can observe during the current build. If a team's internal release process changes how project dependencies are resolved, re-check the generated layers index rather than assuming the same file will remain in the same layer.

### References

- [Spring Boot 3.3 Gradle Plugin — Layered Jars and Wars](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging.html#packaging-executable.configuring.layered-archives)

</details>

- [Back to top](#back-to-top)

---

## <a id="layer-order-and-cache">Why Does Layer Order Affect Cache Reuse?</a>

<details>
<summary>Click for details</summary>

Container-image caches are most effective when earlier/base content remains unchanged. If stable dependencies are placed separately from application classes, a source-code edit can invalidate only the application layer instead of forcing every dependency layer to be rebuilt or transferred again.

Conceptually:

```text
least frequently changed
→ dependencies
→ loader
→ snapshot dependencies
→ application
most frequently changed
```

The exact image cache implementation belongs to container tooling, but Boot's layer ordering provides packaging information that those tools can exploit.

Cache benefit appears only when the image-building path preserves those boundaries. If a Dockerfile copies the entire archive as one opaque file into one layer, the archive may be logically layered but the image cache cannot reuse the inner groups independently. The packaging metadata and the image recipe must therefore agree on how the layers are consumed.

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-layering">When Should Layering Be Customized?</a>

<details>
<summary>Click for details</summary>

Customize layers only when the default change-frequency assumptions do not match the project. A large multi-module application, for example, may want internal libraries separated from rapidly changing application code, or a project may have dependencies with a release cadence that justifies a different grouping.

Customization should be driven by measured build/cache behavior and clear ownership. Too many layers create policy that developers must maintain without necessarily improving cache reuse. Start with Boot's defaults, observe the delivery pipeline, then introduce a custom rule when a stable pattern justifies it.

Custom rules have two responsibilities: claim content into named layers and define a complete layer order. If content is claimed by several patterns, rule order determines which layer receives it; content left unclaimed must still have a destination. A custom scheme that omits a referenced layer from the final order is configuration debt rather than an optimization.

Keep layer names meaningful to delivery behavior. Naming layers after every internal package may look precise but couples image caching to source organization. Prefer groupings such as stable internal libraries versus frequently changing application content when those groups reflect real cache behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="archive-layer-vs-container-runtime">What Does Archive Layering Not Define About Container Runtime Behavior?</a>

<details>
<summary>Click for details</summary>

Archive layers describe how files are grouped for packaging/extraction. They do not define container networking, volumes, process isolation, CPU/memory limits, orchestration, registry behavior, or rollout strategy.

```text
Boot archive layers
→ packaging/cache boundary

container runtime / orchestrator
→ process and deployment boundary
```

This separation matters because “layer” is used in both discussions. A Boot layers index can help construct image layers, but it is not itself a container runtime specification.

Use this boundary during troubleshooting. If dependencies land in the wrong logical Boot layer, inspect packaging rules. If the correct image layers exist but runtime filesystem mounts or container limits behave unexpectedly, the artifact has already crossed into container-runtime ownership. Similar vocabulary should not hide different failure domains.

</details>

- [Back to top](#back-to-top)
