<a id="back-to-top"></a>

# Building OCI Images with Spring Boot

## Menu
- [What Does `bootBuildImage` or Maven `build-image` Produce?](#boot-build-image)
- [How Does a Boot Application Become an OCI Image?](#oci-image-output)
- [Why Does Image Building Need Access to a Docker Daemon or Supported Docker-Compatible Engine?](#image-builder-connection)
- [How Are Image Naming, Tags, and Publication Configured at the Boot Integration Boundary?](#image-publication)

## <a id="boot-build-image">What Does `bootBuildImage` or Maven `build-image` Produce?</a>

<details>
<summary>Click for details</summary>

Gradle's `bootBuildImage` task and Maven's `build-image` goal use Cloud Native Buildpacks to produce an **OCI-compatible application image**. The output contains the application and the runtime layers selected by the builder/buildpacks, plus OCI image configuration needed by a container runtime.

This is different from `bootJar`: the archive is a JVM delivery artifact, while the image is a container delivery artifact. The image build may use an archive as an input internally, but the final handoff is an image address/name rather than only a JAR path.

The task produces an image through the CNB lifecycle rather than translating a Dockerfile. That distinction affects debugging: a failure mentioning builder detection, buildpack phases, or launch metadata belongs to the Buildpacks path; a Dockerfile instruction failure does not. Both may ultimately create OCI images, but the build mechanics are different.

### References

- [Spring Boot 3.3 Gradle Plugin — Packaging OCI Images](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging-oci-image.html)
- [Spring Boot 3.3 Maven Plugin — Packaging OCI Images](https://docs.spring.io/spring-boot/3.3/maven-plugin/build-image.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="oci-image-output">How Does a Boot Application Become an OCI Image?</a>

<details>
<summary>Click for details</summary>

At a learner level, the image path is:

```text
Boot application build input
        ↓
builder + selected buildpacks
        ↓
detect required runtime/build support
        ↓
create reusable layers
        ↓
combine with run image
        ↓
OCI application image
```

The produced image is intended to launch the application with the process metadata prepared by the buildpack. Application teams therefore do not need to manually reproduce Boot's classpath command inside a Dockerfile when they choose the Buildpacks path.

Inspect build logs when the result differs from expectations: they show which buildpacks participated and which important layers or runtime versions were selected.

The resulting image includes process metadata chosen by the buildpacks, so the runtime can start the application using the launch process contributed during the build. This is part of why Buildpacks can provide a standardized application-to-image path: application repositories do not need to duplicate the complete Java launch command and runtime-layer assembly logic.

</details>

- [Back to top](#back-to-top)

---

## <a id="image-builder-connection">Why Does Image Building Need Access to a Docker Daemon or Supported Docker-Compatible Engine?</a>

<details>
<summary>Click for details</summary>

The Boot plugin orchestrates the build, but container images still have to be created, loaded, and optionally published through a supported container-engine connection. By default this usually means a Docker daemon reachable from the environment running Gradle or Maven; supported remote or alternative connection modes depend on the plugin's documented configuration.

This produces a distinct failure class:

```text
Boot application builds normally
but bootBuildImage cannot connect
→ likely container-engine connection/configuration issue
→ not SpringApplication startup
```

CI agents therefore need explicit access and credentials for the image-building environment. A local developer's working Docker Desktop socket is not automatically available to a remote CI runner.

Boot 3.3 explicitly requires a Docker daemon for the Gradle bootBuildImage task and Maven build-image goal. It first inspects Docker CLI configuration/context information and can also use DOCKER_HOST, DOCKER_CONTEXT, TLS-related environment variables, or plugin-specific docker connection settings. This makes the daemon connection a first-class build prerequisite.

Separate three connections when diagnosing failures: the build tool reaching the Docker daemon, the builder container reaching external dependency sources, and the task reaching a registry for pull/publish operations. A proxy or credential fix for one path may have no effect on the other two.

</details>

- [Back to top](#back-to-top)

---

## <a id="image-publication">How Are Image Naming, Tags, and Publication Configured at the Boot Integration Boundary?</a>

<details>
<summary>Click for details</summary>

Boot plugin configuration can set the target image name/tag and can request publication using registry credentials. Treat the image reference as part of the build's delivery contract: a stable naming rule helps CI know which artifact to promote.

Boot's responsibility is to pass the requested name and publish configuration into image production. Registry retention, repository permissions, vulnerability policy, tag immutability, signing, and promotion between environments remain registry/DevOps responsibilities.

Avoid using mutable tags such as `latest` as the only identity for a release. Even when such a convenience tag exists, delivery systems should retain an immutable version/digest association for traceability.

The Gradle task can derive a default image reference from project name/version and accepts additional tags, while publish remains opt-in. Full image references can include registry host, repository path, tag, and digest forms; the naming choice becomes part of the delivery contract shared with CI and deployment systems.

Boot can pass registry credentials for image creation/publication, but secret storage and rotation should come from the build environment or CI secret mechanism rather than source-controlled build files. The plugin needs credentials; it should not become the credential authority.

</details>

- [Back to top](#back-to-top)
