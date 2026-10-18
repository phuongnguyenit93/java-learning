<a id="back-to-top"></a>

# Packaging Trade-offs and Deployment Handoff

## Menu
- [How Do You Choose Between an Executable JAR, WAR, Unpacked Archive, and OCI Image?](#artifact-strategy)
- [How Should the Delivery Environment Influence the Packaging Choice?](#delivery-environment-fit)
- [What Is the Boundary Between Boot Packaging and Deployment Operations?](#deployment-ownership-boundary)
- [Where Does Normal JVM Packaging Hand Off to the Native-Image Path?](#normal-jvm-vs-native-handoff)
- [How Does the End-to-End Boot Build and Packaging Flow Fit Together?](#end-to-end-build-flow)
- [How Do You Classify a Failure Across Plugin, Packaging, Image, and Deployment Boundaries?](#build-failure-classification)

## <a id="artifact-strategy">How Do You Choose Between an Executable JAR, WAR, Unpacked Archive, and OCI Image?</a>

<details>
<summary>Click for details</summary>

Choose the delivery artifact from the environment's execution contract:

```text
standalone JVM process
→ executable JAR is the default

traditional external servlet container
→ WAR

platform benefits from extracted dependencies/files
→ unpacked/extracted executable archive

container-based delivery contract
→ OCI image
```

These forms are not maturity levels. An executable JAR can be a production artifact; an OCI image is useful when the platform expects containers; a WAR remains valid when external-container deployment is an explicit requirement.

Also consider what the artifact needs from the target environment. A raw executable JAR requires a compatible JVM outside the artifact; a Buildpacks image normally carries the selected runtime inside the image; a WAR relies on the external servlet-container contract. Packaging choice therefore moves responsibility between the artifact and platform even when the application code is identical.

</details>

- [Back to top](#back-to-top)

---

## <a id="delivery-environment-fit">How Should the Delivery Environment Influence the Packaging Choice?</a>

<details>
<summary>Click for details</summary>

Start from how the artifact will be started, updated, cached, observed, and secured. A VM-based service manager may only need a JAR and JDK. A PaaS may extract the application automatically. Kubernetes normally consumes container images. A corporate servlet environment may mandate WAR deployment.

Do not introduce containers merely because Boot can build an image, and do not insist on a raw JAR when the platform's native delivery unit is an OCI image. Packaging should minimize adaptation between build output and the runtime platform while preserving clear ownership.

The best format minimizes accidental transformation after the build. If a platform always converts JARs into images, decide whether that conversion belongs in a central platform or whether Boot should produce the final image earlier. Every extra repackaging step creates another place where identity, provenance, configuration, or dependency content can drift.

</details>

- [Back to top](#back-to-top)

---

## <a id="deployment-ownership-boundary">What Is the Boundary Between Boot Packaging and Deployment Operations?</a>

<details>
<summary>Click for details</summary>

Boot packaging creates the unit to deliver. Deployment operations decide **where, when, and under what policy** that unit runs.

```text
Boot build tooling
→ produce JAR/WAR/image

deployment infrastructure
→ store/promote artifact
→ inject deployment configuration/secrets
→ schedule processes
→ route traffic
→ roll out / roll back / scale
```

The boundary is useful during incidents. A malformed `BOOT-INF` layout is a packaging problem; an image that cannot be pulled because of registry policy is deployment infrastructure; an application that starts but fails bean creation is runtime/application configuration.

An immutable handoff makes ownership clearer. The build stage should identify the exact JAR checksum or image digest it produced; deployment should promote that same unit rather than rebuilding source independently. Rebuilding during deployment mixes build and rollout responsibilities and makes a production artifact harder to trace back to verified build evidence.

</details>

- [Back to top](#back-to-top)

---

## <a id="normal-jvm-vs-native-handoff">Where Does Normal JVM Packaging Hand Off to the Native-Image Path?</a>

<details>
<summary>Click for details</summary>

This module's default path assumes the application will run on a JVM: executable JAR/WAR or a container image containing a JVM runtime. Native image changes that model because application analysis and AOT processing participate in creating a platform-specific native executable.

The build plugin may expose the command or Buildpacks entry point that triggers native production, but once the learner asks about closed-world behavior, runtime hints, AOT-generated assets, native compatibility, or native tests, the `native-image` module owns the explanation.

The output form reveals when the handoff occurred. A JVM image still contains a JVM and runs normal bytecode packaging; a native image contains a platform-specific executable produced after AOT/native analysis. The fact that both outputs can be wrapped in an OCI image does not make their build semantics equivalent.

</details>

- [Back to top](#back-to-top)

---

## <a id="end-to-end-build-flow">How Does the End-to-End Boot Build and Packaging Flow Fit Together?</a>

<details>
<summary>Click for details</summary>

The complete JVM-oriented flow can now be read as one decision chain:

```text
declare application dependencies
→ align versions with Boot baseline
→ use Boot plugin integration
→ bootRun / spring-boot:run for development
→ bootJar/bootWar/repackage for executable artifact
→ optionally use layered/extracted packaging
→ optionally build OCI image with Buildpacks
→ hand artifact/image to deployment infrastructure
```

Each stage answers a different question. Keeping them separate makes failures easier to locate and prevents development convenience tasks from becoming accidental production contracts.

</details>

- [Back to top](#back-to-top)

---

## <a id="build-failure-classification">How Do You Classify a Failure Across Plugin, Packaging, Image, and Deployment Boundaries?</a>

<details>
<summary>Click for details</summary>

Use the latest successful boundary as evidence:

```text
dependency resolution / plugin application fails
→ build tool or Boot plugin setup

compile succeeds, bootJar/repackage fails
→ Boot packaging/main-class/archive configuration

JAR runs, bootBuildImage fails
→ Buildpacks/builder/container-engine/image configuration

image builds locally, registry publish/pull fails
→ registry/credentials/deployment infrastructure

artifact launches, ApplicationContext fails
→ application runtime/configuration/auto-configuration
```

This classification is more useful than treating every failed command in `./gradlew` or `mvn` as the same “build problem.” Diagnose by the subsystem that had not yet completed its responsibility.

Add one more checkpoint before blaming deployment: validate the exact immutable output that crossed the handoff. For a JAR, record its checksum and launch that same file. For an image, record the digest that was built and the digest that the runtime pulled. A matching name with a mutable tag is weaker evidence than a matching digest.

This turns failure classification into a chain of verifiable artifacts rather than a sequence of assumptions. If the CI-built digest runs locally but a cluster pulls another digest, the Boot image build may be correct and the deployment promotion/tagging path is the real owner.

### References

- [Spring Boot 3.3 — Container Images](https://docs.spring.io/spring-boot/3.3/reference/packaging/container-images/)
- [Spring Boot 3.3 — Efficient Deployments](https://docs.spring.io/spring-boot/3.3/reference/packaging/efficient.html)

</details>

- [Back to top](#back-to-top)
