<a id="back-to-top"></a>

# Centralized and Distributed Models

## Menu
- [Centralized Version Control: Server-and-Client History Model](#centralized-vcs-model)
- [Distributed Version Control: Repository History in Local Copies](#distributed-vcs-model)
- [Local Work and Collaboration Trade-offs](#centralized-distributed-tradeoffs)
- [History Availability and Copy Governance](#history-availability-and-governance)
- [Server-Outage Scenario: Availability of Local Version History](#offline-history-availability-scenario)

## <a id="centralized-vcs-model">Centralized Version Control: Server-and-Client History Model</a>

<details>
<summary>Click for details</summary>

After establishing why history matters, the next design question is **where history is stored**. In a **centralized VCS (CVCS)**, a shared server maintains the authoritative versioned repository while clients obtain files and exchange changes through that server. Apache Subversion is a familiar example.

A central server can simplify administration and make the shared location explicit. The trade-off is dependency on server and network availability for operations needing centralized history. It does **not** mean developers cannot edit ordinary local files while offline.

**Evidence:** An downloads `Invoice.java` and travels without connectivity. An can edit the file, but some history and sharing operations must wait for the server. **Practice:** sketch a central server with three clients and label where repository history is maintained.

### References
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Back to top](#back-to-top)

---

## <a id="distributed-vcs-model">Distributed Version Control: Repository History in Local Copies</a>

<details>
<summary>Click for details</summary>

A **distributed VCS (DVCS)** addresses part of this dependency by providing local repository copies with history, not only the newest file state. Git is a familiar example. In common use, contributors can inspect history and record local revisions without contacting a shared server for every operation.

'Distributed' does **not** mean a team has no shared source. It may designate a remote repository for coordination and enforce permissions and reviews there. Local copies must also be protected: multiple history copies reduce some risks but do not replace verified backups.

**Example:** Binh records a rounding change in a local repository while offline, then shares it later. **Check:** distinguish the ability to record a local revision from the authority to include it in shared source.

### References
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Back to top](#back-to-top)

---

## <a id="centralized-distributed-tradeoffs">Local Work and Collaboration Trade-offs</a>

<details>
<summary>Click for details</summary>

The key distinction is **local access to recorded history and the collaboration model**, not 'one system has servers and the other does not'. Both types can use a central shared coordination point. A team using Git can still apply centralized approval policy.

| Dimension | Centralized | Distributed |
| --- | --- | --- |
| Repository history on client | Commonly server-dependent | Usually stored in local copies |
| Offline history recording | Tool-dependent and often limited | Normally available locally |
| Team coordination | Central server repository | May designate a shared remote |
| Administration | Central service governance | Copies, synchronization, and acceptance rules |

**Trade-off:** a team on a tightly controlled network has different constraints from an intermittently connected team. **Practice:** identify one flexibility benefit and one new responsibility introduced by distributed copies.

</details>

- [Back to top](#back-to-top)

---

## <a id="history-availability-and-governance">History Availability and Copy Governance</a>

<details>
<summary>Click for details</summary>

Multiple history copies are not necessarily identical. An may have new local revisions, Binh may have only older history, and the designated shared repository may hold a third state. Separate **history availability**, **completeness of a copy**, and **authority to accept team changes**.

Copy governance includes protecting devices, safeguarding confidential data, maintaining backups, and defining the shared acceptance point. If a secret was recorded and copied into several histories, deleting it from the newest working file does not automatically remove it from those recorded copies.

**Scenario:** An's disk fails. Shared revisions can be recovered from another repository, but an unshared local revision may be lost. **Practice:** explain which copies can support recovery and why an arbitrary clone must not be assumed to be a complete backup of everything.

</details>

- [Back to top](#back-to-top)

---

## <a id="offline-history-availability-scenario">Server-Outage Scenario: Availability of Local Version History</a>

<details>
<summary>Click for details</summary>

Suppose the shared server is unavailable for two hours. A centralized client can usually still edit downloaded working files but may be unable to perform operations requiring the server. A local Git repository with history can inspect its existing revisions and record new local work. Neither arrangement can use the unavailable server to exchange changes at that moment.

**Important limit:** Binh recorded a fix in a separate local repository before losing connectivity. The fix does **not automatically appear** in An's copy. Offline work, synchronization, and acceptance are distinct events.

**Practice:** build a table headed *action | server needed? | guarantees seeing Binh's change?* for editing files, reading existing history, recording locally, and updating shared state. This prepares the local-versus-remote model in the next chapter.

</details>

- [Back to top](#back-to-top)
