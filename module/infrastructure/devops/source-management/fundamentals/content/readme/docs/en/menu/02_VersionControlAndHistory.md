<a id="back-to-top"></a>

# Version Control and History

## Menu
- [Version Control Systems: Concept and Responsibilities](#version-control-purpose)
- [Saving Files, Backups, and Version History](#saving-backups-and-version-history)
- [Change Provenance: Content, Author, Time, and Rationale](#change-provenance)
- [Comparing, Inspecting, and Restoring Versions](#compare-inspect-and-restore)

## <a id="version-control-purpose">Version Control Systems: Concept and Responsibilities</a>

<details>
<summary>Click for details</summary>

Folders named `final-1` and `final-2` are weak not because there are too few copies but because they lack a dependable account of how the project changed. A **Version Control System (VCS)** records file states over time and lets people inspect or retrieve identifiable revisions.

Conceptually it provides **recorded checkpoints**, **change context**, **comparisons**, and mechanisms to coordinate different lines of work. Git is a distributed VCS; SVN exemplifies a centralized model. A VCS cannot decide whether a business rule is correct or whether a team should approve it.

**Running example:** An records the tax correction and Binh records the rounding correction as traceable changes that can be examined independently. **Practice:** identify two benefits of a queryable history over a sequence of copied folders.

### References
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Back to top](#back-to-top)

---

## <a id="saving-backups-and-version-history">Saving Files, Backups, and Version History</a>

<details>
<summary>Click for details</summary>

**Saving** writes current file contents. **Backing up** a directory protects data against deletion or device failure. **Version history** additionally connects successive recorded states so that changes can be compared, identified, and inspected.

All three can be useful and none fully replaces the others. A repository can also become corrupted or be lost, so VCS does not eliminate backups. A copy on another machine may exclude unshared or unrecorded edits; its coverage and age matter.

**Example:** `Invoice.java` is saved at 10:00, a project backup is made at 11:00, and a tax change is recorded at 11:30. The backup can restore some data, but not the 11:30 recorded change. **Practice:** draw three layers—working files, backups, recorded history—and state the failure each helps address.

</details>

- [Back to top](#back-to-top)

---

## <a id="change-provenance">Change Provenance: Content, Author, Time, and Rationale</a>

<details>
<summary>Click for details</summary>

A useful source record carries **what** changed, **who** recorded it, **when**, and **why**. Together these form change **provenance**. Real tools expose author and time metadata, but those fields are not infallible identity or compliance proof; consequential changes may require review and stronger controls.

Distinguish the **technical difference** ('tax rate 8% to 10%') from the **business rationale** ('a new tax rule takes effect'). A description that says only 'fix' leaves future maintainers guessing. Linking decisions to source states allows the team to re-evaluate assumptions later.

**Observation:** two identical patches might represent an experiment and an approved legal change; the acceptance decision need not be the same. **Practice:** write a two-sentence change record for `Invoice.java` covering purpose, adjustment, and expected impact.

</details>

- [Back to top](#back-to-top)

---

## <a id="compare-inspect-and-restore">Comparing, Inspecting, and Restoring Versions</a>

<details>
<summary>Click for details</summary>

History becomes valuable when it can be used. A **diff** exposes differences between recorded states, **inspection** shows the content and context of a chosen revision, and **restoration** retrieves a recorded state or its relevant files by an appropriate mechanism. Restoration does not necessarily mean deleting every newer historical record.

**Scenario:** totals differ by one cent after a rounding change. The team compares versions before and after the edit, identifies the relevant lines, reads the stated rationale, and decides whether to correct the newer behavior or restore the older calculation. Reverting source cannot undo invoices already sent to customers.

**Practice:** consider revisions A=100.49, B=100, and C=101. Explain the differences, which revision deserves inspection, and what evidence justifies restoration. Git diff and recovery command details belong to the Git module.

### References
- [Pro Git — Recording Changes to the Repository](https://git-scm.com/book/en/v2/Git-Basics-Recording-Changes-to-the-Repository)

</details>

- [Back to top](#back-to-top)
