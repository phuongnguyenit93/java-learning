<a id="back-to-top"></a>

# Profiles and Profile-Specific Configuration

## Menu
- [What Profiles Solve and What They Do Not](#profile-purpose)
- [Active and Default Profiles](#active-default-profiles)
- [Profile-Specific Config Data Files](#profile-specific-files)
- [Profile-Specific Documents](#profile-specific-documents)
- [Document Activation with spring.config.activate.on-profile](#on-profile-activation)
- [Profile Includes and Profile Groups](#profile-includes-groups)
- [Where Profile Activation Properties May Be Declared](#profile-declaration-restrictions)
- [Multiple Active Profiles and Last-Wins Behavior](#multiple-profile-precedence)
- [Profiles vs Ordinary Property Overrides](#profile-boundary)

## <a id="profile-purpose">What Profiles Solve and What They Do Not</a>

<details>
<summary>Click for details</summary>

Profiles let an application select configuration variants for named situations such as `dev`, `staging`, or `prod`. In this module, the important role of a profile is **configuration activation**: profile-specific files or documents can contribute different values while the same application artifact is reused.

Profiles are useful when a coherent group of settings belongs together under one named mode. They are less useful when only one ordinary property needs to change. A deployment does not need a new profile for every hostname, timeout, or credential; normal property overrides already solve those cases with less indirection.

Spring's broader profile mechanism can also control bean/configuration activation through `@Profile`, but this chapter stays focused on Boot externalized configuration. The central question is which configuration documents become active and how their values participate in precedence.

</details>

- [Back to top](#back-to-top)

---

## <a id="active-default-profiles">Active and Default Profiles</a>

<details>
<summary>Click for details</summary>

`spring.profiles.active` selects the profiles that should be active. It is an ordinary `Environment` property, so normal property-source precedence applies: a higher-precedence source can replace a value from a lower-precedence source.

```properties
spring.profiles.active=dev,local
```

or at launch:

```bash
java -jar app.jar --spring.profiles.active=prod
```

If no profile is explicitly active, Spring uses the default profile named `default`. Boot exposes `spring.profiles.default` if the application needs a different default, including `none` when no default profile should be used.

Active/default profiles choose which configuration variants are eligible; they do not bypass property precedence. Once a document is active, its values still compete using Config Data and overall property-source ordering.

</details>

- [Back to top](#back-to-top)

---

## <a id="profile-specific-files">Profile-Specific Config Data Files</a>

<details>
<summary>Click for details</summary>

Boot automatically considers profile-specific variants using the naming pattern `application-{profile}`. With YAML, activating `prod` means both `application.yaml` and `application-prod.yaml` can participate. The same rule applies to properties files.

Profile-specific files are loaded from the same search locations as the non-profile-specific application files and override their non-specific counterparts. This supports a clear layering model:

```text
application.properties          # shared baseline
application-prod.properties     # prod-specific differences
```

Avoid copying the entire baseline into every profile file. Repeating all values creates drift and makes it harder to see what a profile actually changes. Keep shared defaults in the non-profile-specific document and place only meaningful differences in profile-specific configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="profile-specific-documents">Profile-Specific Documents</a>

<details>
<summary>Click for details</summary>

One physical properties or YAML file can contain multiple logical documents. Boot processes those documents separately, so a later document can be activated only for a specific profile while the first document remains unconditional.

```properties
app.mode=standard
#---
spring.config.activate.on-profile=prod
app.mode=hardened
```

With `prod` active, the second document participates and can override the earlier `app.mode`. Without `prod`, only the first document contributes that value.

Multi-document files are useful when a small profile-specific variation belongs close to the baseline. Separate `application-{profile}` files are often clearer when the variant is large or independently maintained. Choose based on readability; both mechanisms still feed the same Config Data and precedence model.

</details>

- [Back to top](#back-to-top)

---

## <a id="on-profile-activation">Document Activation with spring.config.activate.on-profile</a>

<details>
<summary>Click for details</summary>

`spring.config.activate.on-profile` is an activation condition for a **configuration document**. The document is included only when its profile expression matches the active profiles.

```yaml
app:
  mode: standard
---
spring:
  config:
    activate:
      on-profile: "prod | staging"
app:
  mode: managed
```

This is different from `spring.profiles.active`. `spring.profiles.active` selects profiles for the application; `spring.config.activate.on-profile` asks whether the current document should contribute configuration under the profiles that are already active.

Keeping those directions separate prevents circular configuration such as a document trying to activate the very profile required for that document to exist.

</details>

- [Back to top](#back-to-top)

---

## <a id="profile-includes-groups">Profile Includes and Profile Groups</a>

<details>
<summary>Click for details</summary>

`spring.profiles.include` adds profiles in addition to those selected by `spring.profiles.active`. This is useful for shared cross-cutting profile sets:

```properties
spring.profiles.include[0]=common
spring.profiles.include[1]=observability
```

Profile groups solve a related naming problem. A group gives several fine-grained profiles one logical name:

```properties
spring.profiles.group.production[0]=proddb
spring.profiles.group.production[1]=prodmq
```

Activating `production` then activates the grouped profiles as well. Groups are useful when callers should select one meaningful mode while the application keeps independent profile units internally.

Do not turn include/group relationships into an opaque dependency graph. Keep them small enough that someone reading `--spring.profiles.active=production` can still determine which configuration becomes active.

</details>

- [Back to top](#back-to-top)

---

## <a id="profile-declaration-restrictions">Where Profile Activation Properties May Be Declared</a>

<details>
<summary>Click for details</summary>

Boot restricts profile-selection properties to **non-profile-specific documents**. In Boot 3.3, `spring.profiles.active`, `spring.profiles.default`, `spring.profiles.include`, and `spring.profiles.group` must not be declared in a profile-specific file or in a document activated by `spring.config.activate.on-profile`.

This invalid pattern illustrates why:

```properties
spring.config.activate.on-profile=prod
spring.profiles.active=metrics
```

The document exists only after `prod` is already active, yet it tries to redefine the active-profile set from inside conditional configuration. Boot rejects that self-referential model.

Declare profile selection/grouping in unconditional configuration or provide it through a higher-level `Environment` source such as the command line. Use profile-specific documents to supply values for an already-selected profile, not to choose the profile that activates them.

</details>

- [Back to top](#back-to-top)

---

## <a id="multiple-profile-precedence">Multiple Active Profiles and Last-Wins Behavior</a>

<details>
<summary>Click for details</summary>

When several profiles are active, Boot uses a **last-wins** strategy for profile-specific Config Data. With:

```properties
spring.profiles.active=prod,live
```

values from `application-live.properties` can override competing values from `application-prod.properties`.

The detail that matters in advanced location setups is that last-wins applies at the **location-group level**. Comma-separated location groups and semicolon-separated locations within the same group can therefore produce different file-processing orders. This is why location groups were introduced before profiles in the learning flow.

Do not treat multiple active profiles as unordered labels. Their order can affect the effective configuration. If two profiles routinely compete for the same key, verify that their order expresses a deliberate precedence rather than an accidental launch convention.

</details>

- [Back to top](#back-to-top)

---

## <a id="profile-boundary">Profiles vs Ordinary Property Overrides</a>

<details>
<summary>Click for details</summary>

Use a profile when a named configuration **variant** activates a coherent set of related differences. Use ordinary property overrides when the deployment simply needs a different value.

Good profile fit:

```text
prod
→ production database mode
→ production messaging mode
→ production-specific feature set
```

Ordinary override fit:

```text
orders.timeout=5s
server.port=9090
partner.base-url=https://...
```

If every cluster, customer, region, and secret gets its own profile, profiles become a second configuration system layered on top of property precedence. That increases the number of combinations a team must reason about. Prefer the smallest mechanism that expresses the requirement: direct override for individual values, profile-specific configuration for meaningful named variants, and external configuration systems only when their separate capabilities are actually needed.

</details>

- [Back to top](#back-to-top)
