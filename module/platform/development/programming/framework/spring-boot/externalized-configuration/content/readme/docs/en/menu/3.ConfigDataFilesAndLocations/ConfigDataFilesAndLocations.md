<a id="back-to-top"></a>

# Config Data Files and Search Locations

## Menu
- [What Config Data Contributes to Boot Configuration](#config-data-role)
- [Default Packaged and External Search Locations](#default-search-locations)
- [Packaged Defaults vs External Overrides](#packaged-vs-external-config)
- [application.properties vs application.yaml](#properties-vs-yaml)
- [Changing the Basename with spring.config.name](#spring-config-name)
- [Replacing Search Locations with spring.config.location](#spring-config-location)
- [Adding Search Locations with spring.config.additional-location](#spring-config-additional-location)
- [Files, Directories, Wildcards, and Location Groups](#location-files-directories-groups)
- [Why Config Location Settings Must Be Supplied Early](#early-config-location-inputs)

## <a id="config-data-role">What Config Data Contributes to Boot Configuration</a>

<details>
<summary>Click for details</summary>

Config Data is Spring Boot's model for loading configuration documents before the application context is fully created. It covers familiar files such as `application.properties` and `application.yaml`, their profile-specific variants, explicit search locations, and imported resources.

The key relationship is: Config Data contributes `PropertySource` entries to the same `Environment` used by the rest of the application. It does not create a separate configuration universe. A value loaded from a file still competes with values from environment variables, system properties, command-line arguments, and other sources according to Boot's precedence rules.

Thinking in terms of Config Data is useful because it explains why file discovery, location ordering, profile activation, and imports all affect which candidate values exist before normal application beans consume them.

</details>

- [Back to top](#back-to-top)

---

## <a id="default-search-locations">Default Packaged and External Search Locations</a>

<details>
<summary>Click for details</summary>

By default, Boot looks for `application.properties` and YAML variants in a defined set of places. On the classpath it checks the classpath root and `classpath:/config/`. Outside the packaged application it checks the current directory, its `config/` subdirectory, and immediate child directories under `config/`.

The external locations are useful for deployment-specific overrides because they are considered after packaged locations within Config Data ordering. This lets a jar carry safe defaults while the deployment supplies a different file without rebuilding the artifact.

Example layout:

```text
app.jar
application.properties          # external current-directory config
config/
  application.properties       # external config
  tenant-a/application.properties
```

Boot's default `config/*/` search makes the immediate child directories useful when multiple mounted configuration directories are present.

</details>

- [Back to top](#back-to-top)

---

## <a id="packaged-vs-external-config">Packaged Defaults vs External Overrides</a>

<details>
<summary>Click for details</summary>

The usual deployment pattern is to keep baseline values inside the jar and override only what changes outside it. Boot's Config Data ordering supports exactly that: packaged application properties are considered before external application properties, and profile-specific variants participate in the same model.

For example, the jar might contain:

```properties
orders.timeout=2s
```

while an external `application.properties` contains:

```properties
orders.timeout=5s
```

The external value wins within Config Data. It can still be overridden by a higher-precedence source such as an OS environment variable or command-line argument. That distinction matters: "external file overrides packaged file" is true inside Config Data ordering, but it is not the final rule for the entire `Environment`.

</details>

- [Back to top](#back-to-top)

---

## <a id="properties-vs-yaml">application.properties vs application.yaml</a>

<details>
<summary>Click for details</summary>

Spring Boot supports both Java properties and YAML as application configuration formats. YAML is convenient for hierarchical data; `.properties` keeps every key explicit and often makes precedence comparisons easy to read. Both ultimately contribute property values to the `Environment`.

The practical rule is consistency. If `.properties` and YAML files exist in the same location, Boot 3.3 gives `.properties` precedence over YAML. Relying on that difference deliberately makes configuration harder to reason about, so the official guidance recommends choosing one format for an application where possible.

The file format does not change the higher-level model: source location, profile, import, and property-source precedence still determine which value is effective.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-config-name">Changing the Basename with spring.config.name</a>

<details>
<summary>Click for details</summary>

Boot normally searches for files whose basename is `application`. `spring.config.name` changes that basename. For example:

```bash
java -jar app.jar --spring.config.name=myservice
```

causes Boot to search the configured locations for `myservice.properties` and YAML variants, including relevant profile-specific variants.

This setting changes **what files Boot searches for**, so it must be available before Config Data discovery. That is why `spring.config.name` is an early `Environment` property rather than something you can reliably define inside the very Config Data whose name it controls.

Use it when a different basename is a deliberate deployment convention. Do not use it merely to create another arbitrary layer when the normal `application` files plus profiles/imports already express the requirement clearly.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-config-location">Replacing Search Locations with spring.config.location</a>

<details>
<summary>Click for details</summary>

`spring.config.location` replaces Boot's default Config Data search locations with the locations you provide. It accepts files or directories; directory locations should end in `/` so Boot can append names generated from `spring.config.name`.

```bash
java -jar app.jar \
  --spring.config.location=optional:classpath:/defaults/,optional:file:./runtime-config/
```

The replacement behavior is the important trade-off. Once you set `spring.config.location`, the ordinary default locations are no longer part of the search unless you explicitly include equivalent locations yourself. This is useful for tightly controlled deployments, but it is also a common reason an expected `application.properties` suddenly stops loading.

Locations are processed in declared order, with later locations able to override earlier ones at the same relevant precedence level.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-config-additional-location">Adding Search Locations with spring.config.additional-location</a>

<details>
<summary>Click for details</summary>

`spring.config.additional-location` keeps Boot's normal search locations and appends extra locations after them. Values from those additional locations can therefore override values from the defaults.

```bash
java -jar app.jar \
  --spring.config.additional-location=optional:file:./customer-config/
```

This is usually the safer choice when the deployment wants to layer a small set of overrides on top of the application's normal packaged/external configuration. In contrast, `spring.config.location` expresses "use this location set instead of the defaults."

That distinction is operationally important. If a team intends to add one override directory but accidentally uses `spring.config.location`, it may remove all default search locations and cause apparently unrelated properties to disappear.

</details>

- [Back to top](#back-to-top)

---

## <a id="location-files-directories-groups">Files, Directories, Wildcards, and Location Groups</a>

<details>
<summary>Click for details</summary>

Config locations can point to files or directories. A directory is searched using the configured basename; a file is loaded directly. Boot can also expand wildcard locations for external directories, such as `file:./config/*/`, which is useful when a platform mounts several configuration trees under one parent directory.

Wildcard locations have constraints: they are for external directories, contain a single `*`, and Boot sorts matching locations alphabetically by absolute path. Do not depend on incidental filesystem enumeration order.

Location groups solve a different problem. A semicolon (`;`) groups several locations at the same precedence level, while commas separate successive location groups. This matters especially with profile-specific files because "all files from location A, then location B" can produce a different winner from "A and B are peers, then apply profile last-wins within the group."

Use groups when several locations represent one logical tier, such as multiple classpath locations versus multiple external locations.

</details>

- [Back to top](#back-to-top)

---

## <a id="early-config-location-inputs">Why Config Location Settings Must Be Supplied Early</a>

<details>
<summary>Click for details</summary>

`spring.config.name`, `spring.config.location`, and `spring.config.additional-location` determine how Config Data itself is discovered. Boot therefore reads them very early, before it can load the files those settings control.

Provide them through an already-available `Environment` source such as an OS environment variable, JVM system property, or command-line argument:

```bash
SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./ops/ java -jar app.jar
```

A circular setup does not work conceptually: placing `spring.config.location=...` inside `application.properties` and expecting it to change where that same discovery phase looked for `application.properties` is too late.

When Boot seems to ignore a custom location, check two things first: whether the setting was supplied early enough, and whether `spring.config.location` unintentionally replaced the defaults instead of augmenting them.

</details>

- [Back to top](#back-to-top)
