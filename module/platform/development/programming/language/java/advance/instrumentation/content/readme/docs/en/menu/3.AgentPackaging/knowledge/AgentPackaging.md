<a id="back-to-top"></a>

# Agent Packaging

## Menu
- [Agent JAR Structure](#agent-jar)
- [Java Agent Manifest](#agent-manifest)
- [Premain-Class, Agent-Class, and Launcher-Agent-Class](#agent-entry-points)
- [Agent Arguments](#agent-arguments)
- [Declaring Redefine, Retransform, and Native-Method-Prefix Capabilities](#agent-capability-declarations)
- [Agent Packaged with an Executable JAR](#executable-jar-agent)

## <a id="agent-jar">Agent JAR Structure</a>

<details>
<summary>Click for details</summary>

A Java agent is commonly packaged as a dedicated JAR containing:

- an agent class with premain/agentmain;
- transformers and helper classes;
- a manifest that identifies the agent entry point;
- any dependencies required by the chosen packaging strategy.

Example:

~~~text
timing-agent.jar
├── META-INF/MANIFEST.MF
└── com/example/agent/
    ├── TimingAgent.class
    └── TimingTransformer.class
~~~

An agent JAR is not simply “imported by the application.” The JVM reads its manifest and loads the agent through the java.lang.instrument lifecycle. Because the agent shares the application process, packaging must account for dependency conflicts and class-loader visibility.

Under the package contract, classes in the agent JAR are loaded by the system class loader and belong to that loader's unnamed module. Helpers placed through Boot-Class-Path or appendToBootstrapClassLoaderSearch() live under different bootstrap-loader visibility. That distinction explains many failures where the main agent can see a helper but transformed code cannot resolve it.

</details>

- [Back to top](#back-to-top)

---

## <a id="agent-manifest">Java Agent Manifest</a>

<details>
<summary>Click for details</summary>

The manifest connects a JAR to the agent lifecycle. Important attributes include:

~~~text
Premain-Class
Agent-Class
Launcher-Agent-Class
Can-Redefine-Classes
Can-Retransform-Classes
Can-Set-Native-Method-Prefix
Boot-Class-Path
~~~

Not every agent needs every attribute. A minimal -javaagent startup agent may only need Premain-Class. If the agent intends to call retransformClasses, the manifest must request Can-Retransform-Classes: true and the JVM must also support that capability.

The manifest is a **declaration of intent**, not an unconditional guarantee. Runtime code should still query Instrumentation before using optional capabilities.

</details>

- [Back to top](#back-to-top)

---

## <a id="agent-entry-points">Premain-Class, Agent-Class, and Launcher-Agent-Class</a>

<details>
<summary>Click for details</summary>

Three manifest attributes select the agent class for three launch paths:

- **Premain-Class**: used with -javaagent; the JVM invokes premain before application main.
- **Agent-Class**: used when loading into a running JVM; the JVM invokes agentmain.
- **Launcher-Agent-Class**: used for an agent packaged in an executable JAR; the JVM invokes agentmain before application main.

One agent JAR may contain both Premain-Class and Agent-Class to support startup and dynamic loading.

Example:

~~~text
Manifest-Version: 1.0
Premain-Class: com.example.agent.TimingAgent
Agent-Class: com.example.agent.TimingAgent
Can-Retransform-Classes: true
~~~

A common trap is assuming Launcher-Agent-Class means dynamic attach because it also calls agentmain. It is still an executable-JAR startup path.

</details>

- [Back to top](#back-to-top)

---

## <a id="agent-arguments">Agent Arguments</a>

<details>
<summary>Click for details</summary>

With -javaagent, the text after the equals sign is delivered to premain as **one String**:

~~~text
java -javaagent:timing-agent.jar=include=com.example,debug=true app.jar
~~~

The agent owns the format and parsing:

~~~java
public static void premain(String agentArgs, Instrumentation inst) {
    AgentConfig config = AgentConfig.parse(agentArgs);
}
~~~

Java does not define a standard configuration syntax for agentArgs. A production parser should:

- validate keys and values;
- provide safe defaults;
- avoid logging secrets;
- fail with useful diagnostics for malformed input;
- avoid accidental dependence on the working directory when paths are accepted.

For Launcher-Agent-Class, the package specification defines agentArgs as the empty string. That matters when the same agentmain implementation is reused.

</details>

- [Back to top](#back-to-top)

---

## <a id="agent-capability-declarations">Declaring Redefine, Retransform, and Native-Method-Prefix Capabilities</a>

<details>
<summary>Click for details</summary>

The capability manifest entries do not simply turn on an API globally. They declare that the agent **needs** a capability:

~~~text
Can-Redefine-Classes
Can-Retransform-Classes
Can-Set-Native-Method-Prefix
~~~

The JVM must also support the requested capability. The safe pattern is:

~~~java
if (inst.isRetransformClassesSupported()) {
    inst.addTransformer(transformer, true);
}
~~~

Registering a retransformation-capable transformer when retransformation is not supported can fail with UnsupportedOperationException.

Keep this rule:

~~~text
manifest declaration
        +
JVM support
        ↓
runtime capability
~~~

Do not assume that behavior observed on one HotSpot version is guaranteed by every JVM implementation.

</details>

- [Back to top](#back-to-top)

---

## <a id="executable-jar-agent">Agent Packaged with an Executable JAR</a>

<details>
<summary>Click for details</summary>

An executable JAR may package both the application main class and a Java agent. Launcher-Agent-Class tells the JVM which agent should run **before application main**.

Example manifest:

~~~text
Main-Class: com.example.app.Main
Launcher-Agent-Class: com.example.agent.BootAgent
~~~

The JVM invokes:

~~~java
public static void agentmain(String agentArgs, Instrumentation inst)
~~~

before application main; for this launch path, agentArgs is an empty string.

This is useful when an application wants a built-in startup agent without requiring an operator to add -javaagent. The trade-off is tighter deployment coupling: the application artifact now contains a privileged runtime component whose trust, versioning, and failure behavior must be reviewed as part of startup.

</details>

- [Back to top](#back-to-top)
