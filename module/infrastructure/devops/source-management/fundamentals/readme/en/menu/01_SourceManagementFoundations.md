<a id="back-to-top"></a>

# Source Management Foundations

## Menu
- [Source Management: Concept and Scope](#source-management-definition)
- [Purpose of Source Management in Team Collaboration](#why-manage-shared-source)
- [Risks of Unmanaged Manual Source Sharing](#unmanaged-source-problems)
- [Example: Two Developers Exchanging Edited Files and Versioned Folders](#manual-file-sharing-scenario)
- [Source, Versions, Repositories, and History](#foundational-source-terms)
- [Shared Source, Traceability, and Accountability](#shared-source-and-accountability)

## <a id="source-management-definition">Source Management: Concept and Scope</a>

<details>
<summary>Click for details</summary>

When one person edits a project, keeping the latest folder seems sufficient. With several contributors, the team must know which revision was accepted, where a change came from, and whether earlier states can be recovered. **Source management** is the discipline of preserving, tracing, sharing, and governing the acceptance of source changes.

It covers project files worth tracking, recorded history, and collaboration rules. It is **not** a synonym for a Git hosting website and does not itself compile or deploy software. Version control preserves history; team governance determines which proposed changes are reviewed and accepted.

**Learning route:** begin with manual source-sharing problems and the terms *source, revision, repository, history* in this chapter. Next learn what version control preserves and how centralized and distributed systems differ; then distinguish working files from remote repositories, version-control tools from hosting/team policies, and finally follow a change from personal edit to accepted shared source. Git commands and concrete PR procedures belong to later dedicated modules.

**Evidence:** two developers change an invoice calculation. Keeping both files does not identify the approved calculation. **Practice:** write three questions about provenance, accepted state, and accountability that a folder named `final` cannot answer.

### References
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Back to top](#back-to-top)

---

## <a id="why-manage-shared-source">Purpose of Source Management in Team Collaboration</a>

<details>
<summary>Click for details</summary>

The key benefit is not additional storage but **less ambiguity in collaboration**. An changes a tax rule while Binh changes rounding; an integrator needs to establish whether the edits work together, why they were made, and who accepted them.

A team needs **traceability** of changes, **coordination** of parallel work, and **conditional acceptance** before updating shared source. Recorded history supplies technical evidence; review and ownership agreements supply a decision trail. Without those agreements, a developer's personal state can be mistaken for the team's result.

**Observation:** following a regression, the team can identify the accepted version, contributor, and decision. **Practice:** draft two simple rules for proposing and accepting a defect fix in a three-person team.

</details>

- [Back to top](#back-to-top)

---

## <a id="unmanaged-source-problems">Risks of Unmanaged Manual Source Sharing</a>

<details>
<summary>Click for details</summary>

A file exchanged over chat is only a snapshot at send time. It does not prove the file is current or contains teammates' changes. A name like `project-final-v3-fixed` is not a history. Replacing a file can silently erase another person's valid edit to the same lines.

Another risk is **divergence**: each contributor believes their copy is definitive. A third is **missing evidence**: nobody can say when a defect entered or why a competing change won. Backups reduce data-loss risk but do not independently supply a decision history.

**Observe:** three archives contain different `Order.java` files. The newest modification timestamp does not prove business correctness. **Practice:** classify what can go wrong as lost work, missing provenance, or unclear acceptance.

</details>

- [Back to top](#back-to-top)

---

## <a id="manual-file-sharing-scenario">Example: Two Developers Exchanging Edited Files and Versioned Folders</a>

<details>
<summary>Click for details</summary>

At 09:00 An and Binh receive the same `shop` folder. An changes tax from 8% to 10%; Binh changes rounding. At 11:00 An sends `shop-final.zip`; at 11:05 Binh sends `shop-final-new.zip`. The integrator chooses the later-looking file and accidentally loses the tax change.

The problem is not Binh's sending time. Both edits started from the same baseline but were **never compared and deliberately integrated**. The team needs the starting state, each developer's differences, and an explicit decision to retain or reject each edit.

**Paper exercise:** draw a table with *contributor | baseline | intended change | accepted result*. Mark missing evidence and propose a review step before integration. Recorded version history is the next piece of this solution.

</details>

- [Back to top](#back-to-top)

---

## <a id="foundational-source-terms">Source, Versions, Repositories, and History</a>

<details>
<summary>Click for details</summary>

**Source** means the files that describe or configure software. A **revision** is an identifiable state in source history, not necessarily a published product version such as `1.2.0`. A **repository** is where a version-control system maintains tracked files and their history; not every directory is one.

**Version history** connects recorded states and their context. In Git, a commit records a state with descriptive information, but commands, commits' internals, branches, and references belong to the Git module. A **working file** may have edits that are not yet part of recorded history.

**Example:** a developer's `Invoice.java` has two new lines, while the last recorded repository state retains the old calculation. **Check:** distinguish 'saved a file', 'recorded a revision', and 'released a product'.

</details>

- [Back to top](#back-to-top)

---

## <a id="shared-source-and-accountability">Shared Source, Traceability, and Accountability</a>

<details>
<summary>Click for details</summary>

**Shared source** is the state and history the team recognizes for coordinated work, not every change uploaded to a server. Accountability calls for the proposer, purpose, review findings, and conditions for accepting a change.

Separate three events: **recorded locally**, **shared for inspection**, and **accepted into shared state**. A shared proposal can still be unsuitable for the product. Technical history alone cannot prove that behavior meets requirements; review and testing have separate roles.

**Useful evidence:** a tax-fix description, contributor, reviewer feedback, and the accepted shared revision. **Practice:** classify which records belong in version history and which require team governance or a collaboration platform.

</details>

- [Back to top](#back-to-top)
