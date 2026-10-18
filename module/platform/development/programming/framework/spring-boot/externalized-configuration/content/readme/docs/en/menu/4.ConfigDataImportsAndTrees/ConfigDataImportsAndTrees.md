<a id="back-to-top"></a>

# Config Data Imports and Configuration Trees

## Menu
- [Importing Additional Config Data with spring.config.import](#spring-config-import)
- [How Imported Values Relate to the Importing Document](#import-precedence)
- [Fixed Locations vs Import-Relative Locations](#fixed-vs-relative-imports)
- [Optional Imports and Missing-Resource Behavior](#optional-imports)
- [Importing Extensionless Configuration with Extension Hints](#extension-hints)
- [Configuration Trees and File-per-Property Inputs](#configtree-imports)
- [Diagnosing Config Data Import Failures](#import-failure-model)

## <a id="spring-config-import">Importing Additional Config Data with spring.config.import</a>

<details>
<summary>Click for details</summary>

`spring.config.import` lets one Config Data document pull in additional configuration. This is useful when configuration is split by responsibility, generated outside the main application file, or mounted separately by the deployment platform.

```properties
spring.config.import=optional:file:./ops.properties
```

The import participates in Config Data processing rather than acting like an arbitrary runtime file read. Imported data becomes part of the configuration documents that feed the `Environment`, so its values take part in precedence and profile handling.

An import is processed when Boot discovers the declaring document, and a particular resource is loaded only once even if it is declared more than once. Keep imports intentional: they should clarify configuration composition, not create a long hidden chain that makes ownership hard to trace.

</details>

- [Back to top](#back-to-top)

---

## <a id="import-precedence">How Imported Values Relate to the Importing Document</a>

<details>
<summary>Click for details</summary>

An imported document is treated as if it were inserted immediately below the document that declares the import, and values from the imported document take precedence over values in the importing document.

```properties
# application.properties
app.name=base
spring.config.import=extra.properties
```

```properties
# extra.properties
app.name=imported
```

The effective `app.name` from those two documents is `imported`. The physical position of the `spring.config.import` line within a single document does not change that relationship. If several locations are listed in one import, they are processed in declaration order and later imports can override earlier imports.

This local import ordering still sits inside the broader property-source model, so a higher-precedence environment variable or command-line argument can override the imported value.

</details>

- [Back to top](#back-to-top)

---

## <a id="fixed-vs-relative-imports">Fixed Locations vs Import-Relative Locations</a>

<details>
<summary>Click for details</summary>

Boot distinguishes **fixed** imports from **import-relative** imports. A location that starts with `/` or a URL-style prefix such as `file:` or `classpath:` is fixed and resolves independently of the document that declares it. Other locations are resolved relative to the importing document.

```properties
# /demo/application.properties
spring.config.import=core/core.properties
```

This resolves relative to `/demo/`, so Boot looks for `/demo/core/core.properties`. If that imported file then contains `spring.config.import=extra/extra.properties`, the second import is relative to `/demo/core/`.

The `optional:` prefix does not change whether a location is fixed or relative. Relative imports are convenient for self-contained configuration bundles; fixed imports are clearer when the resource has one deployment-wide address regardless of where the declaring document lives.

</details>

- [Back to top](#back-to-top)

---

## <a id="optional-imports">Optional Imports and Missing-Resource Behavior</a>

<details>
<summary>Click for details</summary>

By default, a configured Config Data location that cannot be found causes startup to fail with `ConfigDataLocationNotFoundException`. That fail-fast behavior is useful when the missing file is required for correct operation.

Prefix a location with `optional:` only when absence is an accepted state:

```properties
spring.config.import=optional:file:./local-overrides.properties
```

Now the application can start even if the file is missing. The same prefix works with `spring.config.location` and `spring.config.additional-location`.

Do not mark required production configuration optional merely to make startup succeed. Doing so moves the failure later, often to a missing property, failed binding, or incorrect runtime behavior, and makes the original cause harder to see. Use `optional:` for truly optional overlays, developer-local files, or platform resources whose absence is expected by design.

</details>

- [Back to top](#back-to-top)

---

## <a id="extension-hints">Importing Extensionless Configuration with Extension Hints</a>

<details>
<summary>Click for details</summary>

Some platforms mount files without an extension, so Boot cannot infer whether the content should be parsed as properties or YAML. Config Data extension hints solve that ambiguity by attaching a format hint to the import location.

```properties
spring.config.import=file:/etc/config/myconfig[.yaml]
```

Boot reads the extensionless `/etc/config/myconfig` resource using the YAML loader. For the Spring Boot 3.3 baseline used by this module, the documented extension-hint form is the bracket suffix shown above: `[.yaml]`.

Use a hint when the external resource genuinely lacks an extension because of platform constraints. It is not a mechanism for disguising one format as another or bypassing normal naming conventions. The hint affects how the resource is loaded; it does not change its precedence relative to other configuration sources.

</details>

- [Back to top](#back-to-top)

---

## <a id="configtree-imports">Configuration Trees and File-per-Property Inputs</a>

<details>
<summary>Click for details</summary>

A configuration tree represents a directory where each file contributes a property value. This matches common mounted-volume patterns: a platform writes one file per value, and Boot exposes those files through the normal `Environment` model.

Given:

```text
/etc/config/myapp/
  username
  password
```

an import such as:

```properties
spring.config.import=optional:configtree:/etc/config/
```

can expose `myapp.username` and `myapp.password`. Folder and file names form the property name; files containing dots map naturally to dotted keys. Values can be bound as `String` or `byte[]` depending on the consumer.

`configtree:` is a Boot bridge from mounted files to configuration. It does not make this module the owner of Kubernetes, Docker secrets, or secret-management policy. Those platforms decide how data is provisioned; Boot decides how the mounted data enters the `Environment`.

</details>

- [Back to top](#back-to-top)

---

## <a id="import-failure-model">Diagnosing Config Data Import Failures</a>

<details>
<summary>Click for details</summary>

Import failures are easiest to diagnose by separating **address**, **availability**, **format**, and **ordering** problems.

- If Boot reports a missing Config Data location, verify the path and whether absence should really be optional.
- If a relative import resolves somewhere unexpected, start from the directory of the declaring document and follow the chain one import at a time.
- If an extensionless resource cannot be parsed, confirm that the extension hint matches the real content.
- If a value is loaded but loses, compare the import's local precedence with other imported documents and then with higher-precedence property sources.

Enable focused logging for `org.springframework.boot.context.config` when file-discovery evidence is needed. The important distinction is that "resource not loaded" and "resource loaded but overridden" are different failure classes and should be investigated differently.

</details>

- [Back to top](#back-to-top)
