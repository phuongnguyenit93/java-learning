<a id="back-to-top"></a>

# Java Agent

## Menu
- [Java Agent Model](#java-agent-model)
- [Agent Class Contract with the JVM](#agent-class-contract)
- [How the JVM Provides Instrumentation to an Agent](#instrumentation-service-handoff)
- [Agent Startup Shapes](#agent-startup-shapes)
- [Agents as Trusted Code and the Responsibility Boundary](#agent-trust-boundary)

## <a id="java-agent-model">Java Agent Model</a>

<details>
<summary>Click for details</summary>

A Java agent is a Java component started through a JVM-defined contract so it can perform work before or while an application runs. Unlike a normal library, application code does not necessarily call the agent. The JVM invokes the agent entry point and can provide an Instrumentation instance.

Mental model:

~~~text
agent JAR
  ↓ manifest selects agent class
agent class
  ↓ premain or agentmain
Instrumentation instance
  ↓
register transformer / inspect loaded classes / request retransform-redefine
~~~

An agent usually lives inside the same process as the application. A bug, deadlock, memory leak, or dependency conflict in the agent can therefore affect the application directly. Treat the agent as **in-process runtime infrastructure**, not as an isolated service.

</details>

- [Back to top](#back-to-top)

---

## <a id="agent-class-contract">Agent Class Contract with the JVM</a>

<details>
<summary>Click for details</summary>

An agent class does not implement a mandatory interface. Instead, the JVM looks for static methods with specification-defined signatures:

~~~java
public static void premain(String agentArgs, Instrumentation inst)

public static void agentmain(String agentArgs, Instrumentation inst)
~~~

One-argument String variants are also valid. If the Instrumentation-taking form exists, the JVM prefers it. That is how the agent receives the runtime capability handle during startup.

Important parts of the contract:

- the method must be public static;
- the method name depends on the launch path;
- initialization should normally complete and return promptly;
- long-running work should be managed explicitly instead of blocking premain/agentmain;
- exception behavior differs between startup and live-JVM loading.

There is no JVM-managed “agent object lifecycle” comparable to a dependency-injection bean. The practical lifecycle is defined by the static entry point and whatever state the agent creates.

</details>

- [Back to top](#back-to-top)

---

## <a id="instrumentation-service-handoff">How the JVM Provides Instrumentation to an Agent</a>

<details>
<summary>Click for details</summary>

The Instrumentation instance is a capability object provided by the JVM. Agent code does not construct or implement it; the API explicitly says Instrumentation is not intended for implementation outside java.instrument.

A minimal pattern looks like:

~~~java
public final class TimingAgent {
    private static volatile Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
        inst.addTransformer(new TimingTransformer());
    }
}
~~~

Once acquired, the instance can be retained and later used to:

- add/remove transformers;
- inspect loaded classes;
- query capabilities;
- request retransformation or redefinition;
- extend bootstrap/system class-loader search;
- expand module relationships with redefineModule.

This handoff is the bridge from “the JVM started my agent” to “the agent can interact with class definitions in this runtime.”

</details>

- [Back to top](#back-to-top)

---

## <a id="agent-startup-shapes">Agent Startup Shapes</a>

<details>
<summary>Click for details</summary>

Three startup shapes matter:

| Shape | Manifest | Entry point | Timing |
| --- | --- | --- | --- |
| command-line agent | Premain-Class | premain | before application main |
| executable-JAR agent | Launcher-Agent-Class | agentmain | before application main |
| dynamic agent | Agent-Class | agentmain | after JVM startup |

The first two are **startup-time** mechanisms: deployment declares the agent before the application begins. A dynamic agent requires implementation support for attach/loading and is subject to increasingly explicit JDK policy.

Because both Launcher-Agent-Class and dynamic loading use agentmain, do not assume they have identical failure behavior or argument handling. Reason from the **launch path**, not only the method name.

</details>

- [Back to top](#back-to-top)

---

## <a id="agent-trust-boundary">Agents as Trusted Code and the Responsibility Boundary</a>

<details>
<summary>Click for details</summary>

An agent executes in the same JVM and may alter application class bytecode. The package specification therefore places responsibility for verifying an agent's trustworthiness on those deploying the agent or the tool that loads it.

Typical risks include:

- an agent JAR replaced or obtained from an untrusted source;
- a transformer accidentally targeting JDK/framework classes;
- helper classes being loaded by an unexpected loader;
- an agent reading sensitive process data;
- invalid bytecode breaking application startup or execution;
- dynamic attach creating a runtime modification path outside normal deployment controls.

The security mental model is simple: **an agent has highly privileged in-process reach**. Provenance, version pinning, attach controls, audit logging, and rollback planning matter as much as transformer correctness.

</details>

- [Back to top](#back-to-top)
