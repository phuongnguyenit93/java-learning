<a id="back-to-top"></a>

# Agent Loading and Startup

## Menu
- [Startup with -javaagent and premain](#javaagent-premain-flow)
- [Launcher-Agent-Class Before Application main](#launcher-agent-flow)
- [Loading an Agent into a Running JVM with agentmain](#runtime-agentmain-flow)
- [Attach API and the Dynamic Agent Loading Flow](#attach-api-flow)
- [Failure Semantics Across Launch Paths](#launch-failure-semantics)
- [Dynamic Agent Loading Policy on JDK 21](#jdk21-dynamic-agent-loading)

## <a id="javaagent-premain-flow">Startup with -javaagent and premain</a>

<details>
<summary>Click for details</summary>

For a startup agent passed on the command line:

~~~text
java -javaagent:timing-agent.jar=config=prod app.Main
~~~

the JVM reads Premain-Class from the manifest, loads the agent class, and invokes premain **before application main**.

~~~java
public static void premain(String agentArgs, Instrumentation inst) {
    inst.addTransformer(new TimingTransformer());
}
~~~

If multiple -javaagent options are present, premain methods run in command-line order. That can affect transformer registration order and dependencies between agents.

The advantage is deterministic deployment: the agent exists from the beginning and can observe application classes loaded later. The cost is deployment configuration, and startup failure can abort the JVM before application main runs.

</details>

- [Back to top](#back-to-top)

---

## <a id="launcher-agent-flow">Launcher-Agent-Class Before Application main</a>

<details>
<summary>Click for details</summary>

Launcher-Agent-Class is used by an executable JAR:

~~~text
java -jar application.jar
~~~

If the JAR manifest contains:

~~~text
Main-Class: com.example.Main
Launcher-Agent-Class: com.example.agent.BootAgent
~~~

the JVM loads BootAgent and invokes agentmain before Main.main().

Compared with -javaagent:

- it invokes agentmain rather than premain;
- agentArgs is always an empty string;
- the agent is packaged in the executable JAR;
- failure to initialize the agent can abort the JVM before application main.

This is still a **startup agent** even though the method is called agentmain. Therefore “premain means startup, agentmain means dynamic” is not a reliable rule.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-agentmain-flow">Loading an Agent into a Running JVM with agentmain</a>

<details>
<summary>Click for details</summary>

Dynamic loading occurs after the JVM/application has started. The agent JAR needs Agent-Class and the class must define agentmain:

~~~java
public static void agentmain(String agentArgs, Instrumentation inst) {
    if (!inst.isRetransformClassesSupported()) {
        throw new IllegalStateException("Retransformation is required");
    }
    inst.addTransformer(new TimingTransformer(), true);
}
~~~

Unlike a startup agent, a dynamic agent arrives **after** many classes may already be loaded. To affect them, it often must:

1. register an appropriately retransformation-capable transformer;
2. find target classes through getAllLoadedClasses();
3. check isModifiableClass();
4. call retransformClasses(), or use redefineClasses() when replacement bytes are the intended model.

Dynamic loading is useful for on-demand tooling, but it increases the importance of capability checks, rollback design, and attach policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="attach-api-flow">Attach API and the Dynamic Agent Loading Flow</a>

<details>
<summary>Click for details</summary>

The jdk.attach module provides APIs for a tool to connect to another JVM and request operations such as agent loading. Mental model:

~~~text
diagnostic / tooling process
        ↓
VirtualMachine.attach(target)
        ↓
request agent loading
        ↓
target JVM loads agent JAR
        ↓
Agent-Class.agentmain(...)
~~~

The Attach API is a **tooling boundary**, not part of java.lang.instrument itself. java.lang.instrument defines the agent lifecycle; jdk.attach is one JDK mechanism that can initiate that lifecycle in a running JVM.

Attach can be restricted by OS/user permissions, JVM options, containers/process environment, or implementation policy. Knowing a PID does not imply that attachment will succeed.

Production tooling should separate the two failure domains: attach/tool-process failure and agent-initialization failure inside the target JVM.

</details>

- [Back to top](#back-to-top)

---

## <a id="launch-failure-semantics">Failure Semantics Across Launch Paths</a>

<details>
<summary>Click for details</summary>

Failure behavior depends on the launch path:

| Path | Agent initialization failure |
| --- | --- |
| -javaagent / premain | JVM aborts before application main |
| Launcher-Agent-Class | JVM aborts before application main |
| running JVM / agentmain | target JVM does not abort; failure may be ignored/logged |

This affects design:

- a startup agent can fail fast when instrumentation is mandatory;
- a dynamic agent must report status back to the tool/operator because the application continues;
- initialization should be idempotent or guarded if a tool can attach repeatedly;
- partial initialization state should be cleaned up when possible.

Do not reuse one error-handling strategy for premain and agentmain without considering the actual launch path.

</details>

- [Back to top](#back-to-top)

---

## <a id="jdk21-dynamic-agent-loading">Dynamic Agent Loading Policy on JDK 21</a>

<details>
<summary>Click for details</summary>

JDK 21 implements the direction described by JEP 451: when an agent is loaded dynamically into a running JVM, the JVM emits a warning in preparation for a future release where dynamic loading may be disabled by default.

On HotSpot JDK 21:

- dynamic agent loading remains allowed by default;
- the JVM emits a warning on dynamic loading;
- -XX:+EnableDynamicAgentLoading is an explicit opt-in and suppresses the warning;
- -XX:-EnableDynamicAgentLoading can disable dynamic loading.

The design lesson matters more than memorizing flags:

~~~text
startup instrumentation
→ deployment owner explicitly opts in at JVM start

dynamic instrumentation
→ runtime changes after startup
→ should face stricter policy/authorization
~~~

Prefer startup loading when instrumentation is known in advance. Dynamic attach remains valuable for on-demand diagnostics/tooling, but it should not be treated as an unrestricted permanent capability.

</details>

- [Back to top](#back-to-top)
