<a id="back-to-top"></a>

# Local and Shared Source

## Menu
- [Working Files Versus Versioned History](#working-files-vs-versioned-history)
- [Local and Remote Repositories](#local-and-remote-repositories)
- [Synchronization and Divergent Histories](#synchronization-and-divergence)
- [Accepted Shared Source: Concept and Criteria](#accepted-source-of-truth)

## <a id="working-files-vs-versioned-history">Working Files Versus Versioned History</a>

<details>
<summary>Click for details</summary>

During the day, the contents of `Invoice.java` visible to An are **working files**. An can save them repeatedly without producing a new recorded revision. Recorded history is an identifiable checkpoint; editable files may change continuously.

This distinction explains why a defect visible on An's computer need not appear in shared source. If an unrecorded edit is lost, an earlier recorded revision can help compare what existed, but it cannot magically recreate edits that were never preserved. Git also has an index/staging area, whose detailed mechanics belong to the separate Git module.

**Check:** An changes ten lines and saves the file. Binh views the agreed shared repository and still sees the old formula. Explain how both observations can be true and which additional events are needed for Binh to see the edit.

</details>

- [Back to top](#back-to-top)

---

## <a id="local-and-remote-repositories">Local and Remote Repositories</a>

<details>
<summary>Click for details</summary>

A **local repository** lives in a contributor's environment and can hold that contributor's recorded history. A **remote repository** is another repository designated for exchanging revisions over a network; 'remote' describes a relationship, not necessarily a public hosting service.

With Git, An can record local history and deliberately publish appropriate changes to a remote. Binh can deliberately receive history from that remote. Git's exact sharing commands belong in the Git module. Do not confuse a working directory with a local repository or a remote storage location with permission to approve changes.

**Paper model:** draw boxes for `An local`, `shared remote`, and `Binh local`. Add arrows for intentional exchange, not automatic propagation. **Observation:** even if the remote changed, Binh's working files need not have changed.

### References
- [Pro Git — Working with Remotes](https://git-scm.com/book/en/v2/Git-Basics-Working-with-Remotes)

</details>

- [Back to top](#back-to-top)

---

## <a id="synchronization-and-divergence">Synchronization and Divergent Histories</a>

<details>
<summary>Click for details</summary>

**Synchronization** is deliberate exchange and reconciliation of states between repository copies according to tool behavior and team rules. It does not happen merely because two repositories share a project name. **Divergence** occurs when An and Binh record different developments from a common earlier state.

Suppose revision A has an 8% tax rule. An creates B (10% tax) and Binh creates C (new rounding). Both began from A, so B cannot be assumed to contain C. Sharing requires examining and integrating the changes, possibly resolving overlapping edits. Receiving history does not itself constitute accepting it into the team branch.

**Practice:** draw `A → B` and `A → C`. Sketch a desired result D that includes both legitimate changes and state what must be checked before D becomes accepted shared source.

</details>

- [Back to top](#back-to-top)

---

## <a id="accepted-source-of-truth">Accepted Shared Source: Concept and Criteria</a>

<details>
<summary>Click for details</summary>

In a distributed model, repository copies can have similar technical capabilities but **different authority to represent a project**. A team designates a repository and relevant state/branch as its **agreed shared source of truth** for a purpose, together with rules and people responsible for acceptance.

'Accepted' does not mean every included change is flawless, nor that it has been deployed to users. It means the agreed acceptance conditions have been followed. A product maintaining several supported versions might have more than one accepted maintenance line; detailed branching decisions belong elsewhere.

**Evidence:** the team recognizes revision D containing the tax and rounding fixes after required review, while B and C remain individual proposals. **Practice:** state where a teammate should look for accepted source and how to handle two conflicting repository copies.

</details>

- [Back to top](#back-to-top)
