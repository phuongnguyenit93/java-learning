# Step 6 - Video Script Rules

Step 6 xây hoặc refactor **Video Script / Presentation Plan** từ Knowledge đã ổn định. Step number này **giống nhau cho new và refactor module**.

Video là presentation layer của Knowledge:

```text
Knowledge
→ what the learner must understand

Video
→ how that same learning content is presented, demonstrated and narrated
```

Video không phải technical source of truth thứ hai và không được tự mở rộng curriculum.

---

## General Agent Rules context guard

Trước khi thực hiện Step 6:

```text
Nếu context của GENERAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENERAL_AGENT_RULES.md trước khi tiếp tục
```

Không suy đoán orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

Canonical source:

```text
module/.../video/{lang}/menu/**/*.md
```

Structural source of truth:

```text
module/.../readme/{lang}/menu/**/*.md
```

Configured languages:

```text
MODULE_LANGUAGE
```

Video capability:

```text
BUILD_VIDEO=TRUE
```

`BUILD_VIDEO` mặc định là `FALSE`. Khi Step 6 được thực hiện như một learning deliverable của module, enable capability bằng canonical module configuration và chạy repository setup/sync để Video skeleton được tạo theo Knowledge structure. Không hand-edit derived/generated config thay cho canonical config.

`BUILD_VIDEO=TRUE` phụ thuộc `BUILD_README=TRUE` vì README/Knowledge Menu là structural source of truth.

---

## 1. Step 6 position in the learning flow

Canonical dependency:

```text
Step 1  Curriculum
        ↓
Step 2  Roadmap
        ↓
Step 3  Menu
        ↓
Step 4  Knowledge
        ↓
Step 5  API / executable evidence when pedagogically useful
        ↓
Step 6  Video Script / Presentation Plan
        ↓
Step 7  Quiz
        ↓
Step 8  Interview
        ↓
Step 9  Validation / Coverage Review
```

Step 6 starts only after the target Knowledge structure is stable enough to present.

If Step 5 is applicable, Video may reuse its real runtime evidence:

```text
API experiment
terminal output
request / response
exception
state change
timing / trace
```

but Step 6 must not invent fake APIs or runtime behavior merely to make a video more visual.

If Step 5 is not applicable, Video may use:

```text
source code
compiler diagnostics
JShell / CLI output
diagrams
slides
tables
small deterministic examples
```

as long as they accurately present the Knowledge concept.

---

## 2. One Knowledge Menu = one Video

The default unit is:

```text
1 Knowledge Menu Markdown file
        =
1 Video
```

Do **not** create one independent video per H2 by default.

Example:

```text
readme/vi/menu/2.SourceCodeStructure/SourceCodeStructure.md
        ↓
one video
        ↓
video/vi/menu/2.SourceCodeStructure/SourceCodeStructure.md
```

If the Knowledge Menu contains:

```markdown
# Cách một chương trình Java được tổ chức

## Tệp mã nguồn và khai báo kiểu

## Câu lệnh và biểu thức

## Định danh, từ khóa, giá trị viết trực tiếp và chú thích
```

the Video file should remain one video with three video sections:

```text
Video: Cách một chương trình Java được tổ chức

Section 1 → Tệp mã nguồn và khai báo kiểu
Section 2 → Câu lệnh và biểu thức
Section 3 → Định danh, từ khóa, giá trị viết trực tiếp và chú thích
```

Only split one Knowledge Menu into multiple independently published videos when the user explicitly changes the product contract. Do not silently change the one-menu-one-video model because one section happens to be long.

---

## 3. Video Menu mapping must mirror Knowledge

Knowledge owns the Video structure.

Canonical mapping:

```text
readme/{lang}/menu/<relative-path>.md
        ↓ exact relative-path projection
video/{lang}/menu/<same-relative-path>.md
```

Example:

```text
readme/vi/menu/1.LanguagePurpose/LanguagePurpose.md
        ↕
video/vi/menu/1.LanguagePurpose/LanguagePurpose.md

readme/en/menu/1.LanguagePurpose/LanguagePurpose.md
        ↕
video/en/menu/1.LanguagePurpose/LanguagePurpose.md
```

The mapping deliberately has **no stable file id**.

Path is the structural relation.

Normal structure-sync behavior is:

```text
Knowledge exists + Video missing
→ CREATE Video skeleton

Knowledge exists + Video exists
→ KEEP existing Video untouched

Video exists + Knowledge missing
→ WARN orphan
→ KEEP existing Video untouched
```

Because existing Video files are preserved, Step 6 must **not assume an old skeleton is still structurally current**.

Before writing the script, compare the Video file against the latest Knowledge file:

```text
relative path
H1
H2 count
H2 order
H2 meaning/title
configured language
```

If Knowledge added/reordered/renamed an H2 after the Video skeleton was first created, reconcile the Video H2 structure manually while preserving existing authored script and publication metadata where still valid.

If the Knowledge file itself was moved/renamed:

```text
new Video skeleton may already exist at the new path
old Video file may remain as orphan
```

Do not guess an automatic rename. Move/reconcile useful script and `video.url` deliberately, then remove the stale source only after confirming the migration.

---

## 4. Knowledge is the technical source of truth

Video must remain traceable to Knowledge.

Required relationship:

```text
Knowledge H1
→ Video H1

Knowledge H2 order
→ Video H2 order

Knowledge H2 concept
→ Video section/chapter explaining the same concept
```

Step 6 may:

```text
rephrase prose into natural spoken language
add a hook/problem statement
add visual direction
add code/demo flow
add observation and takeaway
add transitions between Knowledge sections
compress repeated wording
sequence examples for presentation clarity
reuse a running example across scenes
```

Step 6 must not:

```text
introduce a new curriculum branch absent from Knowledge
contradict Knowledge
silently change version boundaries or semantics
present speculation as fact
invent API/runtime evidence
turn Video into a second technical textbook with different ownership
```

When Video authoring reveals a real problem upstream:

```text
wrong/missing Menu structure
→ MENU / STRUCTURE GAP
→ return to Step 3

missing/incorrect technical explanation
→ KNOWLEDGE GAP
→ return to Step 4

missing practical experiment that is genuinely valuable
→ API / EVIDENCE GAP
→ return to Step 5 and re-evaluate applicability
```

Do not hide upstream gaps by patching only the narration.

---

## 5. Canonical Video file shape

The generated skeleton is intentionally small.

Typical source:

```markdown
---
video:
  url: ""
---

# OOP Mental Model

<!--
VIDEO SCRIPT FORMAT

... Transition / Scene sample contract ...
-->

## OOP là gì?

<!-- VIDEO_SECTION -->

## Ranh giới trách nhiệm

<!-- VIDEO_SECTION -->
```

`video.url` is publication metadata.

Rules:

```text
video.url blank
→ valid while script/video is not published

video.url already nonblank
→ preserve it unless the user explicitly asks to replace it
```

Step 6 writes presentation content below the matching H2 section markers.

Do not remove the compact hidden format comment unless repository architecture changes; it makes the source self-documenting for future human/AI editing.

---

## 6. Section, Transition and Scene contract

Each Knowledge H2 maps to one Video section.

Minimum contract:

```text
First H2 section
→ >= 1 Scene
→ Transition is optional

Second H2 and later
→ >= 1 Transition
→ >= 1 Scene
```

The number of Scenes is content-driven, not fixed.

Valid examples:

```text
Simple section
└── Scene

Medium section
├── Transition
├── Scene — problem
├── Scene — explanation
└── Scene — takeaway

Complex/runtime section
├── Transition
├── Scene — setup
├── Scene — source code
├── Scene — run/demo
├── Scene — observation
└── Scene — conclusion
```

Do not force every section to have the same scene count.

Do not create extra H2 such as `Intro` or `Conclusion` merely because they are common video patterns if they would break the Knowledge H2 mapping. Put opening/closing presentation work inside suitable Scenes of the first/last Knowledge section unless the Knowledge Menu itself contains corresponding sections.

---

## 7. Canonical Transition format

Transition bridges the previous Knowledge section into the next one.

```markdown
### Transition

**Time:** `02:50–03:00`

**Visual:**

Show the next section title and visually move from the previous model to the new concern.

**Script:**

Chúng ta đã thấy object gom state và behavior lại với nhau.
Nhưng khi có nhiều object cộng tác, câu hỏi tiếp theo là:
trách nhiệm của mỗi object nên dừng ở đâu?

**Purpose:**

Bridge object identity to responsibility boundaries without resetting the learner's context.
```

A good Transition should normally do at least one of these:

```text
carry forward an unresolved question
show why the next section naturally follows
contrast previous vs next concept
move from model → mechanism
move from mechanism → consequence
move from code → runtime observation
```

Avoid empty transitions such as:

```text
"Tiếp theo chúng ta sẽ học X."
```

when there is a meaningful conceptual bridge available.

Transitions should usually be short. Their job is continuity, not another full lecture.

---

## 8. Canonical Scene format

```markdown
### Scene 1 — <optional scene title>

**Time:** `00:00–00:40`

**Visual:**

Describe exactly what the viewer should see.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates or proves.
```

Every authored Scene/Transition must have usable:

```text
Time
Visual
Script
Purpose
```

The Portal build validates these fields once a Video file contains authored scene content.

`Scene title` is optional but recommended when it helps the presenter navigate a long script.

---

## 9. How to write an effective technical video script

The Video script is not README prose with timestamps added.

Write for **spoken explanation + visual evidence**.

### 9.1 Start from a learner question, problem or observable behavior

Prefer:

```text
"Tại sao đoạn code này compile nhưng vẫn có thể fail khi chạy?"
```

over:

```text
"Autounboxing là quá trình chuyển wrapper thành primitive."
```

Definitions still matter, but motivation/problem context should make the definition useful.

### 9.2 One scene should have one dominant learning purpose

Good:

```text
Scene 1 → establish the problem
Scene 2 → explain the mental model
Scene 3 → prove it with code/runtime evidence
Scene 4 → summarize the consequence
```

Weak:

```text
one 4-minute Scene
→ several unrelated concepts
→ multiple code examples
→ no clear visual progression
→ no single takeaway
```

Split a scene when its visual, question or learning purpose materially changes.

### 9.3 Visual must be concrete

Avoid vague direction:

```text
Visual:
Show Java code.
```

Prefer:

```text
Visual:
Open `Main.java` in IntelliJ.
Highlight `Integer number = null`.
Then reveal `int value = number` and run the program.
Freeze on the exception line and highlight the unboxing expression.
```

Useful visual primitives include:

```text
IDE/source code
terminal
HTTP request/response
runtime logs
compiler error
debugger/state view
diagram
timeline
table/comparison
small callout/highlight
title card only when it helps navigation
```

### 9.4 Prefer progressive reveal over code walls

Do not show a large class and narrate every line.

Prefer:

```text
show only the relevant fragment
→ highlight the important expression
→ run/observe
→ connect observation back to the mental model
```

If a whole class is needed for context, zoom/highlight only the lines relevant to the current scene.

### 9.5 Narration should sound spoken, not copied

README may say:

```text
Java is always pass-by-value; for reference types the copied value is the reference value.
```

Video narration can say:

```text
"Ở đây có một điểm rất dễ nhầm. Java vẫn truyền bằng giá trị.
Với object, thứ được copy sang parameter không phải object thứ hai;
đó là giá trị reference đang trỏ tới cùng object."
```

Keep the technical meaning identical while making sentence rhythm natural for speech.

### 9.6 Use a running example when it reduces cognitive load

If several sections can naturally reuse the same example, prefer evolution:

```text
same small program
→ add one concept
→ observe behavior
→ modify one condition
→ compare result
```

instead of resetting to a brand-new domain example every 30 seconds.

Do not force a running example when it makes the concept less clear.

### 9.7 Make runtime evidence explicit

When a demo exists, narration should not stop at "run the code".

Use:

```text
setup
→ trigger
→ observation
→ meaning
→ takeaway
```

Example:

```text
Setup       → `Integer number = null`
Trigger     → assign it to primitive `int`
Observation → NullPointerException occurs at unboxing
Meaning     → compile-time type compatibility does not prove runtime value safety
Takeaway    → wrapper-null handling matters before implicit unboxing
```

### 9.8 Time is an estimate, then a rehearsal artifact

Initial `Time` may be estimated while authoring, but it should be internally coherent and sequential.

Do not create impossible timing such as a dense multi-paragraph explanation in ten seconds.

After actual rehearsal/recording, update timing if needed. The final timestamp should describe the real presentation, not an arbitrary placeholder.

### 9.9 Purpose is not a summary of Script

Weak:

```text
Purpose:
Explain autounboxing.
```

Better:

```text
Purpose:
Show that compile-time wrapper-to-primitive compatibility does not prevent a runtime failure when the wrapper reference is null.
```

`Purpose` should tell a reviewer why the scene exists in the learning flow.

### 9.10 End with a useful handoff

The last scene should normally leave the learner with:

```text
the section/menu takeaway
the boundary of what was learned
why the next Knowledge Menu follows, when there is a natural relationship
```

Avoid generic endings such as:

```text
"Cảm ơn các bạn đã xem video."
```

as the only conclusion. Presentation etiquette can be added during recording; the source script should preserve the learning handoff.

---

## 10. Script quality anti-patterns

Reject or refactor scripts that look like:

```text
README copied almost verbatim into Script
every Scene uses the same static slide
Visual says only "show code" without telling what/why
long code dump with no observation
runtime output shown but never interpreted
many scene changes with no conceptual reason
Transition that merely announces the next heading
new claims that do not exist in Knowledge/source evidence
fake demo created only because "video needs a demo"
five examples proving the same point
speaker notes that depend on hidden context not visible to the learner
```

The learner should be able to answer after each significant scene:

```text
What did I just see?
Why did it behave that way?
What should I remember from it?
```

---

## 11. Knowledge/API evidence selection

Before writing scenes for one Menu, build a small presentation map:

```text
Knowledge section
→ core learner question
→ best explanation mode
→ best visual/evidence
→ takeaway
```

Example:

```text
Primitive vs reference
→ what kind of value is stored?
→ memory/reference mental model
→ diagram + tiny assignment example
→ distinguish value category from object identity

Unboxing null
→ why can valid code fail at runtime?
→ compile-time/runtime boundary
→ code + exception output
→ wrapper compatibility is not null safety
```

Use Step 5 API evidence only when it materially improves the explanation. A Knowledge Menu can have an excellent video with zero HTTP endpoints.

---

## 12. Localization rules

Video uses `MODULE_LANGUAGE`; do not invent a separate Video language list.

For configured languages:

```text
Knowledge path parity
→ required

Knowledge H2 concept/order parity
→ required

technical meaning
→ required

scene count
→ may differ when natural localization/presentation requires it

exact timestamp wording
→ may differ
```

Do not force VI and EN to have word-for-word narration or identical scene segmentation if that makes either language unnatural.

But do not use localization as permission to add/remove technical concepts from one language.

Vietnamese should read as natural Vietnamese technical speech, not English syntax translated word-by-word. English should read as natural technical English.

---

## 13. Existing/refactor Video preservation

Existing Video content may already contain:

```text
published URL
reviewed narration
recording-specific timing
visual direction
scene ordering
manual presenter notes encoded in Script
```

Do not regenerate the entire file from scratch simply because Knowledge changed.

Refactor with preservation:

```text
KEEP valid script
MOVE scenes when Knowledge section moved
SPLIT/MERGE scenes when presentation flow improves
UPDATE technically stale narration
ADD missing Knowledge coverage
PRESERVE nonblank publication URL unless explicitly replacing it
```

If an existing Video conflicts with current Knowledge, Knowledge wins on technical meaning. Report significant script migration rather than silently leaving the contradiction.

---

## 14. Build / Portal projection validation

After authored Video content changes, validate the real build pipeline.

Expected projection shape:

```text
project-portal/build/generated/portal-data/
└── module/{ROUTE_ID}/video/{lang}/
    ├── index.json
    └── content/{categoryId}/script.json
```

Portal projection behavior:

```text
pure skeleton with no authored Scene
→ not published as Video content

authored Video
→ parsed + validated
→ index.json entry
→ script.json projection
→ module-catalog video language entry
```

Validate at minimum:

```text
Video source parses
every authored Scene/Transition has Time/Visual/Script/Purpose
first section has >= 1 Scene
section 2..N has >= 1 Transition + >= 1 Scene
projection category matches the Knowledge Menu
configured/localized path is correct
generated index/script are deterministic
module-catalog exposes only authored Video languages
frontend/static resource build still succeeds when relevant
git diff contains only intended changes
```

Generated Portal data is downstream build output. Do not hand-author learning content inside `project-portal/build/generated/...` or `build/resources/main/static/...`.

---

## 15. Step 6 execution workflow

For each target module:

```text
1. Read GENERAL_AGENT_RULES + Step 6 rules.

2. Inspect canonical module config:
   MODULE_LANGUAGE
   BUILD_README
   BUILD_VIDEO

3. Inspect the finalized Knowledge Menu tree.

4. Ensure BUILD_VIDEO is enabled for the requested Video deliverable.

5. Run repository structure/setup so missing Video skeletons are created.

6. Compare every Video path/H1/H2 with current Knowledge.
   Reconcile stale existing skeletons without destroying authored content.

7. For each Knowledge Menu:
   one Menu file → one Video file.

8. Build a presentation map from Knowledge sections and available Step 5 evidence.

9. Write Transition/Scene content with concrete Visual + spoken Script + Purpose.

10. Review technical accuracy against Knowledge and authoritative evidence.

11. Review presentation quality:
    spoken rhythm
    scene purpose
    visual usefulness
    transitions
    pacing
    repetition

12. Preserve publication URL and existing human-owned content where applicable.

13. Validate Portal Video projection/build.

14. Review VI/EN conceptual parity for configured languages.
```

For a large module, author Menu videos independently only after the canonical Menu/Knowledge structure is stable. Do not let parallel Video workers invent conflicting terminology or alternate curricula.

---

## Completion gate

Step 6 hoàn thành khi:

```text
[ ] BUILD_VIDEO is enabled through canonical config for the requested Video deliverable
[ ] BUILD_README dependency is satisfied
[ ] MODULE_LANGUAGE drives Video localization
[ ] every target Knowledge Menu maps to exactly one Video source file
[ ] Video relative paths mirror Knowledge relative paths
[ ] existing Video files were reconciled against the latest Knowledge H1/H2 structure
[ ] no stable-id/rename guessing mechanism was invented
[ ] each Knowledge H2 maps to a Video section in the same conceptual order
[ ] first Video section has at least one Scene
[ ] every later Video section has at least one Transition and one Scene
[ ] every authored Scene/Transition contains Time, Visual, Script and Purpose
[ ] narration is natural spoken explanation rather than README copy-paste
[ ] visuals are concrete enough for recording/editing
[ ] code/demo scenes contain observation + meaning, not just source display
[ ] Step 5 evidence is reused when valuable, not forced when unnecessary
[ ] Video introduces no technical claim that conflicts with Knowledge
[ ] upstream Menu/Knowledge/API gaps were routed back to the correct step instead of patched only in narration
[ ] nonblank video.url values and valid existing scripts were preserved unless explicitly changed
[ ] configured languages preserve the same technical concepts and Knowledge mapping
[ ] Portal Video projection/build validation passes
[ ] only intended source files changed
```

Downstream:

```text
Knowledge + finalized API evidence + Video presentation layer
        ↓
STEP_7_QUIZ.md / STEP_8_INTERVIEW.md
        ↓
STEP_9_VALIDATION.md
```

