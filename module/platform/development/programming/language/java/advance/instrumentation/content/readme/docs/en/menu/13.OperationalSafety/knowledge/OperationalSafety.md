<a id="back-to-top"></a>

# Operational Safety and Failure Modes

## Menu
- [Agent Trust Boundary and Security Risk](#agent-trust-security)
- [Startup Cost and Runtime Overhead](#startup-runtime-overhead)
- [ClassLoader, Module Visibility, and Helper Classes](#classloader-module-visibility)
- [Dynamic Attach Policy and Deployment Control](#dynamic-attach-policy)
- [removeTransformer and Class-Loading Races](#remove-transformer-race)
- [Conditional Rollback with Redefine or Retransform](#rollback-strategy)
- [Compatibility Testing Across JDK and Class-File Versions](#compatibility-testing)

## <a id="agent-trust-security">Agent Trust Boundary and Security Risk</a>

<details>
<summary>Click for details</summary>

A Java agent is privileged in-process code. It can observe runtime classes, alter bytecode, and share the application's security/failure domain.

Control:

- where the artifact comes from;
- checksums/signatures/version pinning;
- who may add -javaagent or attach to the process;
- which configuration/secrets the agent can read;
- which telemetry leaves the process;
- audit records for enablement/dynamic attach.

The java.lang.instrument package specification explicitly places responsibility for verifying agent trustworthiness on those deploying agents/tools.

Practical threat model:

~~~text
malicious/compromised agent
→ arbitrary in-process observation/modification
→ impact comparable to a highly privileged component
~~~

Do not review an agent as if it were a harmless logging library simply because its initial use case is monitoring.

</details>

- [Back to top](#back-to-top)

---

## <a id="startup-runtime-overhead">Startup Cost and Runtime Overhead</a>

<details>
<summary>Click for details</summary>

Overhead appears in two phases.

**Startup / class loading**

- matcher/filter work for each class;
- parsing/transformation/emission;
- verification and altered JIT warm-up;
- extra cost from multiple chained agents.

**Runtime**

- injected probe instructions;
- helper calls;
- allocations;
- timestamp/counter/locking;
- telemetry buffering/export.

A good agent measures its own overhead:

~~~text
baseline application
vs
agent loaded with no targets
vs
agent with representative instrumentation
~~~

Early targeting, lightweight transform callbacks, and runtime batching/sampling reduce cost.

An observability agent that significantly slows the application can distort the very behavior it is trying to measure.

</details>

- [Back to top](#back-to-top)

---

## <a id="classloader-module-visibility">ClassLoader, Module Visibility, and Helper Classes</a>

<details>
<summary>Click for details</summary>

ClassLoader/module visibility is a frequent production failure source.

Suppose a target is:

~~~text
plugin class
defined by PluginClassLoader
in named module plugin.foo
~~~

and transformed bytecode calls:

~~~text
AgentRuntime.record()
~~~

If AgentRuntime is visible only from the system loader, the target may fail to resolve the helper.

Possible designs include:

- a minimal bootstrap helper;
- helper injection into the target loader;
- appending a JAR to the appropriate loader search;
- redefineModule to add required reads/opens/exports.

Do not “solve” this by putting the whole agent JAR on bootstrap search. The API warns that bootstrap-search JARs should contain only classes/resources intended for bootstrap definition; collisions can cause IllegalAccessError and difficult failures.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-attach-policy">Dynamic Attach Policy and Deployment Control</a>

<details>
<summary>Click for details</summary>

Dynamic attach modifies a JVM **after deployment startup**, so its policy should be stricter and more explicit than a startup agent.

JDK 21 warns on dynamic agent loading in the direction established by JEP 451. The integrity model is:

~~~text
startup -javaagent
→ deployer opted in before application start

dynamic attach
→ runtime mutation on demand
→ requires explicit authorization
~~~

Production policy should define:

- which users/tools may attach;
- which environments allow it;
- alert/audit behavior;
- JVM flag configuration;
- fallback when future JDK defaults become stricter.

Do not build automation around the assumption that dynamic attach will remain unrestricted by default forever.

</details>

- [Back to top](#back-to-top)

---

## <a id="remove-transformer-race">removeTransformer and Class-Loading Races</a>

<details>
<summary>Click for details</summary>

removeTransformer has an explicit race: because class loading is multi-threaded, a transformer **can still receive callbacks after removal**.

Even with:

~~~java
enabled.set(false);
inst.removeTransformer(transformer);
~~~

the transformer should check:

~~~java
if (!enabled.get()) {
    return null;
}
~~~

If cleanup immediately releases a resource that a late callback still uses, the agent can race with itself.

Safe shutdown may require:

- atomic enabled state;
- an in-flight callback counter;
- deliberate resource lifetime;
- bounded callback work;
- idempotent cleanup.

Instrumentation code executes in a concurrent runtime path, not a simple sequential lifecycle.

</details>

- [Back to top](#back-to-top)

---

## <a id="rollback-strategy">Conditional Rollback with Redefine or Retransform</a>

<details>
<summary>Click for details</summary>

Rollback is not the same as removeTransformer.

To remove instrumentation from already-transformed classes, an agent needs a strategy:

~~~text
disable transformer behavior
→ remove registration when appropriate
→ identify affected classes
→ retransform/redefine toward desired uninstrumented form
→ verify success
~~~

For a retransformation-capable transformer, a common approach is to change configuration to “do not instrument” and retransform targets. Remember that earlier retransformation-incapable transformations are still automatically reused.

With redefine, the agent may preserve/restore known baseline bytes, but baseline management becomes complex with multiple agents or previous redefinitions.

Test rollback as a feature:

- active-frame transition;
- per-call atomicity: if one redefineClasses/retransformClasses invocation throws, none of the classes in that supplied set are changed;
- partial progress across a multi-call/chunked workflow: earlier calls may already have succeeded before a later call fails;
- helper visibility after rollback;
- telemetry-state cleanup;
- multiple agents modifying the same class.

</details>

- [Back to top](#back-to-top)

---

## <a id="compatibility-testing">Compatibility Testing Across JDK and Class-File Versions</a>

<details>
<summary>Click for details</summary>

Compatibility testing is multi-dimensional:

~~~text
runtime JDK
× application/framework version
× class-file version
× bytecode tool version
× agent configuration
× coexistence with other agents
~~~

A minimum suite should cover:

- startup agent;
- dynamic attach when supported;
- target class loaded before/after agent;
- repeated retransformation;
- disable/rollback;
- multiple class loaders;
- named modules;
- unsupported/unmodifiable class;
- exceptional exit from transformed methods;
- multiple-agent ordering.

A production agent should expose self-diagnostics:

- agent version;
- enabled instrumentations;
- transform success/failure counts;
- skipped unsupported classes;
- current JDK/tool version.

The more transparent the agent is supposed to be to application code, the more important its own diagnostics become.

</details>

- [Back to top](#back-to-top)
