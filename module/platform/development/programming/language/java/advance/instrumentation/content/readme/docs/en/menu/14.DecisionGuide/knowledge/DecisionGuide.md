<a id="back-to-top"></a>

# Decision Guide and Synthesis

## Menu
- [When Should You Use Instrumentation?](#when-to-use-instrumentation)
- [When Should You Avoid Instrumentation?](#when-not-to-use-instrumentation)
- [Startup Agent or Dynamic Attach?](#startup-vs-dynamic-agent-choice)
- [Redefine or Retransform?](#redefine-vs-retransform-choice)
- [Instrumentation vs Reflection and Proxies](#instrumentation-vs-reflection-proxy)
- [Instrumentation vs Runtime Diagnostics](#instrumentation-vs-runtime-diagnostics)
- [When to Hand Off to JVMTI or a Native Agent](#jvmti-native-agent-handoff)
- [End-to-End Java Agent Mental Model](#end-to-end-agent-flow)

## <a id="when-to-use-instrumentation">When Should You Use Instrumentation?</a>

<details>
<summary>Click for details</summary>

Instrumentation fits when the requirement lives at the **class-definition/runtime boundary**, not merely in ordinary business APIs.

Signals include:

- cross-cutting probes across many classes without editing source;
- instrumenting dependency/framework code;
- applying logic at class-load time;
- changing method implementation of loaded classes within JVM limits;
- enforcing an agent policy across applications/modules.

Checklist:

~~~text
can source modification/proxy solve it?
        ↓ no
need class-load or bytecode-level interception?
        ↓ yes
can accept a privileged in-process agent?
        ↓ yes
can test JDK/class-loader/module compatibility?
        ↓ yes
Instrumentation is a reasonable candidate
~~~

Do not choose it merely because “agents are powerful.” Choose it because the problem belongs to the boundary Instrumentation owns.

</details>

- [Back to top](#back-to-top)

---

## <a id="when-not-to-use-instrumentation">When Should You Avoid Instrumentation?</a>

<details>
<summary>Click for details</summary>

Avoid Instrumentation when a simpler, clearer mechanism is sufficient.

Examples:

- you own the source and only need metrics around a few methods → explicit decorator/interceptor may be simpler;
- a Spring bean call needs cross-cutting behavior → framework proxy/AOP may fit;
- you only need annotation/metadata inspection → Reflection;
- you need GC/thread/heap diagnosis → JFR/jcmd/Runtime Diagnostics;
- you need arbitrary object-schema/inheritance changes → redefine/retransform is insufficient;
- you need deep native VM events → JVMTI/native-agent boundary.

Anti-pattern:

~~~text
"I don't want to modify source"
→ therefore use a bytecode agent for everything
~~~

Hidden complexity includes class-loader problems, JDK compatibility, startup cost, and difficult debugging. Instrumentation should be a **deliberate infrastructure choice**, not a default shortcut.

</details>

- [Back to top](#back-to-top)

---

## <a id="startup-vs-dynamic-agent-choice">Startup Agent or Dynamic Attach?</a>

<details>
<summary>Click for details</summary>

Choose a startup agent when the requirement is known before deployment:

- always-on observability;
- coverage during tests;
- mandatory security/runtime policy;
- need to observe early class loading;
- desire explicit deployer opt-in.

Choose dynamic attach when:

- investigating on demand;
- tooling must attach to an already-running process;
- restart is not immediately possible;
- instrumentation is temporary.

Comparison:

| | Startup | Dynamic |
| --- | --- | --- |
| policy | explicit at JVM launch | runtime mutation after launch |
| early classes | visible from the start | may need retransformation |
| failure | can abort startup | target JVM generally continues |
| JDK direction | stable deployment model | increasingly explicit policy |

If an agent is a permanent production dependency, startup loading is usually easier to reason about and audit.

</details>

- [Back to top](#back-to-top)

---

## <a id="redefine-vs-retransform-choice">Redefine or Retransform?</a>

<details>
<summary>Click for details</summary>

Choose according to where the desired bytes come from:

~~~text
have concrete replacement class bytes?
→ redefineClasses

want the transformer pipeline to recompute instrumentation?
→ retransformClasses
~~~

Retransform fits:

- enabling/disabling probes;
- dynamic configuration;
- attaching after classes are already loaded;
- applying existing transformer logic to loaded classes.

Redefine fits:

- debugger/fix-and-continue style replacement;
- tools that already compiled/generated a full replacement definition.

Both:

- require JVM capability;
- require a modifiable class;
- obey structural limits;
- do not reset object/static state;
- allow active frames to continue old code.

Choose by semantics, not merely by which API is available.

</details>

- [Back to top](#back-to-top)

---

## <a id="instrumentation-vs-reflection-proxy">Instrumentation vs Reflection and Proxies</a>

<details>
<summary>Click for details</summary>

The three mechanisms act at different layers:

| Mechanism | Intervention point | Natural fit |
| --- | --- | --- |
| Reflection | runtime metadata/member access | inspect/invoke when type/member is selected dynamically |
| Proxy/AOP | calls crossing a wrapper/interceptor boundary | cross-cutting behavior at interface/bean/framework boundaries |
| Instrumentation | class-file definition/bytecode | inject/replace logic regardless of whether calls cross a proxy |

For OrderService:

~~~text
read @Timed annotation
→ Reflection

intercept a call through a Spring proxy
→ Proxy/AOP

instrument the method even when object is created directly
→ Instrumentation
~~~

They can also cooperate: an agent may inspect metadata through reflection and then use Instrumentation to alter bytecode.

</details>

- [Back to top](#back-to-top)

---

## <a id="instrumentation-vs-runtime-diagnostics">Instrumentation vs Runtime Diagnostics</a>

<details>
<summary>Click for details</summary>

The two modules answer different questions:

~~~text
Instrumentation
→ how is code/a probe inserted into the runtime?

Runtime Diagnostics
→ what does the resulting evidence mean and how is root cause found?
~~~

If the question is:

- “how do I add a timing probe to an already-loaded class?” → Instrumentation;
- “why is service latency high?” → Runtime Diagnostics;
- “is the agent itself increasing latency?” → both: Instrumentation explains probe overhead; Diagnostics measures/evaluates runtime evidence.

This boundary also encourages a better tool architecture: keep data-collection/probe logic separate from analysis/presentation where practical.

</details>

- [Back to top](#back-to-top)

---

## <a id="jvmti-native-agent-handoff">When to Hand Off to JVMTI or a Native Agent</a>

<details>
<summary>Click for details</summary>

Java Instrumentation does not expose every JVM tooling capability.

When a requirement needs:

- low-level VM events unavailable through java.lang.instrument;
- deeper native-method/runtime hooks;
- JVMTI-level heap/thread/GC events;
- a native agent operating outside/before the Java-level agent model;
- control beyond the redefine/retransform contract;

the next boundary is JVMTI/native agents.

Mental-model handoff:

~~~text
Java Instrumentation
→ high-level Java agent + class-file transformation

JVMTI
→ native VM tooling interface with a broader event/capability surface
~~~

Do not try to “work around” Instrumentation constraints with unsupported bytecode tricks. If the contract is insufficient, choose the correct lower-level tooling layer.

</details>

- [Back to top](#back-to-top)

---

## <a id="end-to-end-agent-flow">End-to-End Java Agent Mental Model</a>

<details>
<summary>Click for details</summary>

The whole module can be assembled into one flow:

~~~text
1. package agent JAR
        ↓
2. choose launch path
   -javaagent / Launcher-Agent-Class / dynamic attach
        ↓
3. JVM calls premain or agentmain
        ↓
4. receive Instrumentation
        ↓
5. check capabilities + install transformer
        ↓
6. target class load/redefine/retransform
        ↓
7. transformer receives class-file bytes
        ↓
8. bytecode tool emits valid transformed bytes
        ↓
9. JVM verifies + links + installs definition
        ↓
10. probes/helper runtime execute
        ↓
11. operate safely: overhead, loader/module visibility, policy
        ↓
12. remove/rollback/retransform when needed
~~~

If the learner can explain every arrow, identify where failures can occur, and know which topics must be handed off, the mental model is strong enough for later API experiments, Quiz, and Interview work.

</details>

- [Back to top](#back-to-top)
