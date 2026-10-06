---
video:
  url: ""
---

# Config Data Imports and Configuration Trees

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

## Importing Additional Config Data with spring.config.import

<!-- VIDEO_SECTION -->

### Scene 1 — Imports Stay Inside Config Data

**Time:** `00:00–00:45`

**Visual:**

Open `application.properties` with `spring.config.import=optional:file:./ops.properties`. Animate `ops.properties` joining the Config Data document set, then feeding the same `Environment` as the importing file.

**Script:**

`spring.config.import` lets one Config Data document pull in another resource. Boot processes that resource as part of Config Data, so its values participate in profile handling and precedence instead of behaving like an arbitrary file read performed later by application code.

**Purpose:**

Place imported resources inside the normal Config Data pipeline.

## How Imported Values Relate to the Importing Document

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Stack the importing document above the imported document and put the same key in both.

**Script:**

Once the resource is part of Config Data, the next question is how its values relate to values in the document that imported it.

**Purpose:**

Move from import discovery to import-local precedence.

### Scene 2 — Imported Values Can Override the Importer

**Time:** `00:55–01:40`

**Visual:**

Show `application.properties: app.name=base` importing `extra.properties: app.name=imported`. Place the imported document immediately below the importer and highlight `imported` as effective. Add a command-line card above both as a broader override.

**Script:**

Boot treats the imported document as inserted immediately below the document that declares the import, and the imported value takes precedence over the same key in that importing document. If several locations are imported, later imports can override earlier ones. Higher-precedence property sources can still override the whole Config Data result.

**Purpose:**

Explain local import ordering while keeping it inside the broader property-source precedence model.

## Fixed Locations vs Import-Relative Locations

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Replace the value comparison with a file tree rooted at `/demo/` and two different import path styles.

**Script:**

Ordering is only useful after Boot finds the resource, and import paths can resolve in two different ways.

**Purpose:**

Bridge import precedence into path resolution.

### Scene 3 — Resolve Relative Imports from the Declaring Document

**Time:** `01:50–02:35`

**Visual:**

Show `/demo/application.properties` importing `core/core.properties`, resolving to `/demo/core/core.properties`. Then show that file importing `extra/extra.properties`, resolving from `/demo/core/`. Contrast both with fixed `file:/etc/config/base.properties`.

**Script:**

A location with a fixed prefix such as `file:` or `classpath:` resolves independently of the declaring document. A plain import is relative to the document that declares it. That means each hop in a relative import chain starts from a new directory, so diagnose the path one document at a time.

**Purpose:**

Differentiate fixed imports from import-relative locations and show how chained relative resolution works.

## Optional Imports and Missing-Resource Behavior

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Keep the path tree and remove the target resource in two parallel launch lanes.

**Script:**

After path resolution comes availability: should a missing resource stop startup, or is absence part of the design?

**Purpose:**

Move from address resolution to the required-versus-optional resource contract.

### Scene 4 — `optional:` Changes the Failure Contract

**Time:** `02:45–03:30`

**Visual:**

Lane A imports `file:./required.properties` and stops on `ConfigDataLocationNotFoundException`. Lane B imports `optional:file:./local-overrides.properties` and continues when the file is absent.

**Script:**

Missing Config Data fails startup by default because Boot assumes the resource is required. Prefix the location with `optional:` only when absence is a valid state, such as a developer-local overlay. Marking required production configuration optional simply pushes the failure later and makes the real cause harder to see.

**Purpose:**

Teach `optional:` as an explicit absence contract rather than a generic error suppressor.

## Importing Extensionless Configuration with Extension Hints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Replace the missing file with an existing file named `/etc/config/myconfig` that has YAML content but no extension.

**Script:**

A resource can exist and still be ambiguous if its filename does not tell Boot which parser to use.

**Purpose:**

Move from resource availability to resource format detection.

### Scene 5 — Extension Hints Tell Boot How to Parse

**Time:** `03:40–04:25`

**Visual:**

Show `spring.config.import=file:/etc/config/myconfig[.yaml]`. Highlight `[.yaml]`, then animate the extensionless file entering the YAML loader.

**Script:**

Some platforms mount configuration files without extensions. The bracketed extension hint tells Boot how to parse that real extensionless resource. It changes format detection, not precedence, and it should match the actual content rather than disguising one format as another.

**Purpose:**

Show how extension hints solve parser selection for extensionless Config Data.

## Configuration Trees and File-per-Property Inputs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Expand the single file into a mounted directory containing one file per value.

**Script:**

Some platforms expose configuration as a directory of individual value files instead of one properties or YAML document.

**Purpose:**

Introduce the file-per-property model that `configtree:` adapts into Boot configuration.

### Scene 6 — Turn a File Tree into Properties

**Time:** `04:35–05:20`

**Visual:**

Show `/etc/config/myapp/username` and `/etc/config/myapp/password`, then `spring.config.import=configtree:/etc/config/`. Transform the paths into `myapp.username` and `myapp.password` entering `Environment`.

**Script:**

A configuration tree maps directories and filenames to property names, which fits mounted-volume patterns where one file holds one value. Boot exposes those values through the normal Environment and binding model. The platform owns how the files were provisioned; Boot owns how that mounted tree enters configuration.

**Purpose:**

Explain `configtree:` as a bridge from file-per-property mounts into normal Boot property resolution.

## Diagnosing Config Data Import Failures

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:20–05:30`

**Visual:**

Collapse the previous examples into four diagnostic cards: address, availability, format, and ordering.

**Script:**

These import features fail for different reasons, so the fastest diagnosis starts by identifying which stage is actually wrong.

**Purpose:**

Synthesize import behavior into a stage-based diagnostic model.

### Scene 7 — Diagnose Imports by Failure Class

**Time:** `05:30–06:20`

**Visual:**

Under `address`, show an unexpected relative path. Under `availability`, show a missing required file. Under `format`, show a wrong extension hint. Under `ordering`, show a loaded value losing to a higher-precedence source. Add a terminal callout for focused `org.springframework.boot.context.config` logging.

**Script:**

Check the import address first, then whether the resource exists and is allowed to be absent, then whether Boot can parse it, and only after successful loading compare ordering. A resource that never loaded and a value that loaded but lost precedence are different failure classes and need different fixes.

**Purpose:**

Give the learner a practical diagnostic order for Config Data import failures.
