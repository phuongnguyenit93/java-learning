---
video:
  url: ""
---

# Config Data Files and Search Locations

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## What Config Data Contributes to Boot Configuration

<!-- VIDEO_SECTION -->

### Scene 1 — Config Data Feeds the Same Environment

**Time:** `00:00–00:45`

**Visual:**

Show `application.properties`, `application.yaml`, a profile-specific file, and an imported resource flowing into a `Config Data` box, then into ordered `PropertySource` entries and the shared `Environment`.

**Script:**

Config Data is Boot's early-loading model for configuration documents. It covers familiar application files, profile variants, explicit locations, and imports, then contributes those values to the same Environment used by every other property source. File discovery changes which candidates exist; precedence still decides which candidate is effective.

**Purpose:**

Place Config Data inside the overall Environment model instead of treating files as a separate configuration system.

## Default Packaged and External Search Locations

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Zoom from the Config Data box into a file tree showing packaged and external locations.

**Script:**

Before changing any search setting, it helps to know where Boot already looks by default.

**Purpose:**

Move from Config Data's role to its default discovery behavior.

### Scene 2 — Know the Default Search Set

**Time:** `00:55–01:40`

**Visual:**

Show `classpath:/`, `classpath:/config/`, `file:./`, `file:./config/`, and `file:./config/*/`. Group the classpath entries as packaged and the file entries as external.

**Script:**

Boot checks the classpath root and `classpath:/config/` for packaged configuration. Outside the application, it checks the current directory, its `config` directory, and immediate child directories below `config`. Those external locations are useful because they can provide deployment-specific values without rebuilding the jar.

**Purpose:**

Make the default packaged and external search locations concrete before custom location settings are introduced.

## Packaged Defaults vs External Overrides

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Keep one packaged and one external search location, then place the same key in both.

**Script:**

Those default locations are designed for a common pattern: keep portable defaults with the artifact and deployment differences outside it.

**Purpose:**

Connect default search locations to the packaged-default plus external-override deployment model.

### Scene 3 — One Jar, External Deployment Values

**Time:** `01:50–02:35`

**Visual:**

Show `app.jar` containing `app.region=us-east`, then `./config/application.properties` containing `app.region=eu-west`. Launch once and highlight `eu-west` as effective.

**Script:**

The jar can carry a safe baseline while an external file supplies the value for a specific deployment. Because the external Config Data participates later within the file ordering, its candidate can override the packaged default. The application code does not need to know which file supplied the value.

**Purpose:**

Demonstrate how external Config Data overrides packaged defaults while preserving the same application artifact.

## application.properties vs application.yaml

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Replace the single external file with side-by-side properties and YAML examples containing equivalent keys.

**Script:**

The search model works with both properties and YAML, but format choice still affects readability and same-location conflicts.

**Purpose:**

Introduce format choice without changing the Config Data mental model.

### Scene 4 — Two Formats, One Config Data Model

**Time:** `02:45–03:30`

**Visual:**

Show equivalent `server.port` and nested `client.timeout` configuration in `.properties` and `.yaml`. Add a warning callout: both formats in the same location -> `.properties` takes precedence.

**Script:**

Both formats feed the same Config Data system. Properties are flat and explicit; YAML can make nested data easier to scan. Prefer one format consistently. If both formats exist in the same location, Boot gives the properties file precedence, which can make a YAML value appear to be ignored.

**Purpose:**

Clarify format equivalence and the same-location precedence rule.

## Changing the Basename with spring.config.name

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Morph the default `application.*` filenames into a custom basename placeholder.

**Script:**

If the default `application` basename is not appropriate, Boot can change what filename it searches for.

**Purpose:**

Move from file format to discovery-name customization.

### Scene 5 — Change What Basename Boot Searches

**Time:** `03:40–04:25`

**Visual:**

Launch with `--spring.config.name=orders` and transform `application.properties` into `orders.properties`, plus its YAML and profile-specific variants.

**Script:**

`spring.config.name` changes the basename used during Config Data discovery. Supplying `orders` makes Boot search the configured locations for `orders.properties`, YAML variants, and their profile-specific forms. Because this setting controls discovery itself, Boot has to know it before Config Data loading begins.

**Purpose:**

Show how `spring.config.name` changes discovery and why it is an early configuration input.

## Replacing Search Locations with spring.config.location

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Return to the default search list and draw a large replacement arrow to `file:./ops/`.

**Script:**

Changing the basename keeps the search locations. The next option changes the locations themselves.

**Purpose:**

Differentiate filename customization from search-location replacement.

### Scene 6 — `spring.config.location` Replaces Defaults

**Time:** `04:35–05:20`

**Visual:**

Show `--spring.config.location=optional:file:./ops/`. Cross out the default location stack and keep only `./ops/` feeding Config Data.

**Script:**

`spring.config.location` replaces the default search set. That can create a very explicit deployment contract, but it also means the normal packaged and external locations are no longer searched unless you include them yourself. Treat this property as replacement, not as “one more directory.”

**Purpose:**

Make the replacement semantics of `spring.config.location` unmistakable.

## Adding Search Locations with spring.config.additional-location

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:20–05:30`

**Visual:**

Restore the default search stack and append a new `./ops/` card instead of replacing anything.

**Script:**

If the goal is to keep the normal defaults and add one deployment directory, Boot has a separate setting for that.

**Purpose:**

Contrast replacement with extension of the default search locations.

### Scene 7 — Additional Means Extend

**Time:** `05:30–06:15`

**Visual:**

Show `--spring.config.additional-location=file:./ops/` beside the untouched default search list. Highlight `./ops/` as an added later candidate.

**Script:**

`spring.config.additional-location` preserves the normal search locations and adds new ones. That fits the common pattern of packaged defaults plus a dedicated operational override directory. The distinction is simple: `location` replaces the search set; `additional-location` extends it.

**Purpose:**

Teach when custom Config Data should extend rather than replace Boot's defaults.

## Files, Directories, Wildcards, and Location Groups

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:15–06:25`

**Visual:**

Expand the added location card into examples for file, directory, wildcard, and grouped locations.

**Script:**

Once custom locations are in play, their syntax becomes part of the configuration behavior.

**Purpose:**

Introduce the structural differences between location forms.

### Scene 8 — File and Directory Locations Have Different Meaning

**Time:** `06:25–06:47`

**Visual:**

Compare `file:./extra.properties` with `file:./config/`. Highlight that a file names one resource while a directory lets Boot append configured basenames.

**Script:**

A file location names one resource. A directory asks Boot to derive the configured basenames from that directory, so the trailing slash changes the meaning of the location. Before adding more syntax, first decide whether Boot should load one named resource or search a directory using its normal basenames.

**Purpose:**

Separate the basic file-versus-directory discovery decision from the more advanced wildcard and grouping rules.

### Scene 9 — Wildcards Expand Discovery; Groups Shape Precedence

**Time:** `06:47–07:10`

**Visual:**

Extend the directory example to `file:./config/*/`, reveal two discovered child directories, then bracket two semicolon-separated locations into one visual precedence group.

**Script:**

Wildcards can discover immediate external subdirectories without listing each one manually. Location groups solve a different problem: they tell Boot which locations should be considered at the same precedence level. Wildcard expansion changes what is discovered; grouping changes how discovered locations participate in ordering.

**Purpose:**

Keep wildcard discovery and location-group precedence conceptually distinct while showing how both extend the basic location model.

## Why Config Location Settings Must Be Supplied Early

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:10–07:20`

**Visual:**

Turn the location examples into a startup timeline and move all three `spring.config.*` discovery controls before the Config Data search step.

**Script:**

All of these settings control discovery, which creates one final timing rule: they must exist before the search they are changing.

**Purpose:**

Tie custom discovery settings back to startup timing.

### Scene 10 — Avoid Circular Discovery

**Time:** `07:20–08:05`

**Visual:**

Place `spring.config.name`, `spring.config.location`, and `spring.config.additional-location` before `search Config Data`. Show environment, JVM system property, and command-line arrows entering before that point. Draw a circular warning around “file contains the setting that is needed to discover the same file.”

**Script:**

These properties decide what Boot is about to search, so they have to arrive from an early source such as the environment, a JVM system property, or the command line. Relying on a file whose own discovery depends on the same setting creates a circular model. Discovery controls have to be known before discovery begins.

**Purpose:**

Explain why Config Data location controls must be supplied early and why self-discovery is circular.
