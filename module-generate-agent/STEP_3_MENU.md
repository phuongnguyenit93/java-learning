# Step 3 - Menu Rules

Step 3 chỉ dùng cho **new module**. Mục tiêu là ổn định Menu/chapter/title skeleton từ ROADMAP đã approve trước khi viết full Knowledge.

### General Agent Rules context guard

Trước khi thực hiện Step 3:

```text
Nếu context của GENARAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENARAL_AGENT_RULES.md trước khi tiếp tục
```

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

```text
NEW MODULE
→ execute Step 3

REFACTOR MODULE
→ SKIP Step 3
→ Menu restructuring is handled together with Knowledge in STEP_4_KNOWLEDGE.md
```

Canonical source chủ yếu: `module/.../readme/{lang}/menu/**/*.md`.

---

### Bootstrapping a new learning module

For a new concept-oriented learning module, prefer the repository's real lifecycle instead of manually fabricating all support files:

```text
create module directory
    ↓
add local gradle.properties
    ↓
run Gradle/settings synchronization
    ↓
master.json + properties.json are synchronized from canonical schemas
    ↓
set module-owned VALUE fields
    ↓
enable BUILD_README=TRUE
    ↓
run Gradle configuration again
    ↓
README.md + readme/{lang}/BASE.md + LIST.md + menu/ are initialized
    ↓
add human-owned chapter files under readme/{lang}/menu/**
```

Do not hand-create generated registry/structure outputs such as `ModuleListEnum`, `module-structure.txt`, or `STRUCTURE.md`; let the existing generators synchronize them.

For an early curriculum-only phase, it is valid for a module to start as:

```text
MODULE_TYPE=LIBRARY
BUILD_README=TRUE
BUILD_SWAGGER=FALSE
BUILD_QUIZ=FALSE
BUILD_INTERVIEW=FALSE
```

and have no Java source or module-local `build.gradle` yet. Add runtime/source capabilities only when the learning design actually requires them.

#### Current SERVICE_NAME bootstrap caveat

When `SERVICE_NAME` is blank, current settings generation temporarily falls back to the module directory name and validates that value as a generated enum constant (`[A-Za-z_][A-Za-z0-9_]*`). A new directory containing `-`, such as `language-basics`, therefore cannot safely complete its very first metadata synchronization while `SERVICE_NAME` is still blank.

For this current implementation constraint, use an enum-safe temporary directory name for the first sync (for example `language_basics`), set a valid stable `SERVICE_NAME` such as `JAVA_LANGUAGE_BASICS` in the generated `master.json`, then rename the directory to its intended final path and run Gradle again. Do not keep the temporary underscore path as part of the learning taxonomy merely to satisfy this bootstrap detail.

### H1-only chapter scaffolds vs Knowledge sections

It is valid to establish the curriculum outline first with chapter Markdown files containing only their localized H1:

```markdown
# Generics
```

The H1 defines the chapter title/outline, but it is **not yet a Knowledge section** for governance/relation purposes. Canonical Knowledge identity starts only when anchored H2 sections exist:

```markdown
## <a id="type-erasure">Type Erasure</a>
```

Therefore, during an H1-only scaffold phase, do not run `syncMetadataReadme` merely to create empty/useless Knowledge metadata. Add the real anchored H2 sections first, then synchronize `knowledge-metadata.yml`.

The H1-only scaffold phase is also the preferred time to finalize chapter numbering, directory names and Markdown file paths. Before anchored Knowledge sections and cross-surface relations exist, reordering `1.X`, `2.Y`, or renaming a chapter file is still a low-cost curriculum edit. After `readmeRelated`, Quiz/Interview relations, Knowledge metadata or API ordering depend on those paths/anchors, the same rename becomes a reference migration and must update/validate every exact relation. Stabilize the learning path early whenever possible.

---

## Build Menu + title only

This step applies only to a new module.

Derive the detailed Knowledge skeleton from the approved roadmap:

```text
approved roadmap milestone
        ↓
Knowledge category / chapter
        ↓
localized chapter title
        ↓
planned section titles / stable identities
```

Create the Menu/chapter structure and titles, but **do not write the full official Knowledge content yet**.

The purpose is to stabilize:

```text
chapter order
folder/file names
localized H1 titles
planned anchored H2 identities
roadmap → Knowledge structure
```

This is the lowest-cost point to correct chapter numbering, file placement and title structure before downstream metadata and relations depend on them.

## Completion gate

Step 3 hoàn thành khi chapter order, paths, localized H1 titles và planned section identities đủ ổn định để Step 4 viết Knowledge mà không phải redesign Menu từ đầu.

Downstream:

```text
approved Menu skeleton
        ↓
STEP_4_KNOWLEDGE.md
```
