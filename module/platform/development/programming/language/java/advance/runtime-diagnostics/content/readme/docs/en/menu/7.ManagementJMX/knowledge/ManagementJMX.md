<a id="back-to-top"></a>

# Runtime Monitoring and Management with MXBeans and JMX

## Menu
- [Why the Java Management API Exists](#java-management-purpose)
- [ManagementFactory and Platform MXBeans](#management-factory-and-platform-mxbeans)
- [Thread, Memory, GC, Class Loading, Compilation, Runtime, and OS Metrics](#platform-management-surfaces)
- [MBeans, MXBeans, and the ObjectName Model](#mbean-and-object-name-model)
- [The Platform MBeanServer and Management Access](#platform-mbean-server)
- [Local and Remote JMX](#local-vs-remote-jmx)
- [JConsole and Management Clients](#jconsole-and-management-clients)
- [The Security Boundary of Remote JMX](#remote-jmx-security)

## <a id="java-management-purpose">Why the Java Management API Exists</a>

<details>
<summary>Click for details</summary>

Not every runtime question requires shell access or a dump file. Java provides a standard **management API** that lets application code and management clients read JVM state through stable interfaces.

The management layer exists to:

- expose runtime state as managed objects;
- allow in-process or remote observation;
- provide selected management operations;
- decouple clients from concrete JVM implementation classes.

Mental model:

~~~text
JVM/runtime component
        ↓
platform MXBean / MBean
        ↓
MBeanServer / proxy / direct access
        ↓
application code, JConsole, JMC, or another management client
~~~

In Runtime Diagnostics, MXBeans/JMX are a **structured source of runtime evidence**. They complement rather than replace thread dumps, JFR, or heap dumps.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="management-factory-and-platform-mxbeans">ManagementFactory and Platform MXBeans</a>

<details>
<summary>Click for details</summary>

java.lang.management.ManagementFactory is the primary entry point for JVM **platform MXBeans**.

Example:

~~~java
MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
ThreadMXBean threads = ManagementFactory.getThreadMXBean();
RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
~~~

Platform MXBeans represent runtime components such as:

- ClassLoadingMXBean;
- CompilationMXBean;
- MemoryMXBean;
- MemoryPoolMXBean;
- GarbageCollectorMXBean;
- ThreadMXBean;
- RuntimeMXBean;
- OperatingSystemMXBean.

Some bean types have one instance while others return lists because a JVM can have several memory pools or collectors.

The important diagnostic property is that in-process code can read structured runtime state without parsing command output.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-management-surfaces">Thread, Memory, GC, Class Loading, Compilation, Runtime, and OS Metrics</a>

<details>
<summary>Click for details</summary>

Different platform management surfaces answer different questions:

| Surface | Example question |
| --- | --- |
| ThreadMXBean | platform-thread count, deadlocks, thread info |
| MemoryMXBean | aggregate heap/non-heap usage |
| MemoryPoolMXBean | usage by memory pool |
| GarbageCollectorMXBean | collection count/time |
| ClassLoadingMXBean | loaded/unloaded class counts |
| CompilationMXBean | compilation time when supported |
| RuntimeMXBean | uptime, VM identity, input arguments |
| OperatingSystemMXBean | standard OS information; the JDK extension adds process/system CPU and memory data |

Example:

~~~java
MemoryUsage heap = ManagementFactory
        .getMemoryMXBean()
        .getHeapMemoryUsage();

System.out.println(heap.getUsed());
~~~

There are two interfaces with the same simple name that should not be conflated:

- `java.lang.management.OperatingSystemMXBean` is the standard Java SE management interface. It exposes OS name/version/architecture, available processors, and system load average;
- `com.sun.management.OperatingSystemMXBean` is a JDK extension that adds process CPU time/load, committed virtual memory, and physical/swap-memory information.

`ManagementFactory.getOperatingSystemMXBean()` is typed as the standard interface. On a JDK that implements the extension, the returned object may also implement `com.sun.management.OperatingSystemMXBean`; use extension methods only after an appropriate type check/cast, and do not present them as standard Java SE API.
These are monitoring surfaces, not root-cause engines. Rising heap usage is a signal that still needs GC, heap, or JFR evidence for explanation.

In Java 21, ThreadMXBean supports **platform threads**, not virtual threads, which is an important boundary for Loom-heavy applications.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mbean-and-object-name-model">MBeans, MXBeans, and the ObjectName Model</a>

<details>
<summary>Click for details</summary>

An **MBean** exposes a management interface through JMX. An **MXBean** is an MBean variant whose types are mapped to a standard set of open types, which helps remote clients avoid depending on application-specific classes.

Every managed bean registered in an MBeanServer is identified by an **ObjectName**, conceptually:

~~~text
java.lang:type=Memory
java.lang:type=Threading
java.lang:type=GarbageCollector,name=<collector>
~~~

An ObjectName is a management identity made of a domain and key properties; it is not a Java object reference.

This matters because clients can query beans by name/pattern, access structured attributes/operations, and use the same management model locally or remotely.

You do not need the entire JMX instrumentation specification to use platform MXBeans, but MBean/ObjectName concepts make JConsole and remote JMX behavior much easier to understand.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-mbean-server">The Platform MBeanServer and Management Access</a>

<details>
<summary>Click for details</summary>

The platform MBeanServer is the JVM's built-in registry of MBeans:

~~~java
MBeanServer server = ManagementFactory.getPlatformMBeanServer();
~~~

Platform MXBeans can be accessed in three common ways:

1. directly through ManagementFactory in the same JVM;
2. through a typed MXBean proxy backed by an MBeanServerConnection;
3. indirectly through MBeanServer/MBeanServerConnection and ObjectName.

Direct example:

~~~java
RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
System.out.println(runtime.getUptime());
~~~

A proxy is useful when code wants a typed interface while the actual target may be a remote JVM.

Applications can also register custom MBeans in the platform server. That broader instrumentation design is outside this module's main scope; here the server is primarily part of the diagnostic access path.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-vs-remote-jmx">Local and Remote JMX</a>

<details>
<summary>Click for details</summary>

**Local JMX** observes a JVM from the same host/process environment. **Remote JMX** exposes the management agent through a connector so a client in another process or host can access it.

Local access is simpler and usually has a smaller attack surface. Remote access is useful when:

- shell access to the application host is inconvenient;
- a monitoring system needs MXBean values;
- operators use JConsole or JMC from another machine.

The JDK management agent is commonly configured with system properties controlling ports, authentication, and SSL/TLS.

Remote JMX does not make an application “fully observable.” JMX provides management state; logs, JFR, traces, and business metrics answer different questions.

Containers, NAT, and firewalls can complicate RMI/JMX connectivity, so deployment topology must be considered.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jconsole-and-management-clients">JConsole and Management Clients</a>

<details>
<summary>Click for details</summary>

**JConsole** is a JDK GUI monitoring and management tool. Through JMX it can connect to local or remote JVMs and inspect:

- memory;
- threads;
- classes;
- VM summary information;
- MBeans.

It is useful for exploring the management surface and doing focused interactive checks. Oracle notes that local JConsole itself consumes resources on the monitored host, so remote monitoring is preferred when production isolation matters.

Other clients can include:

- JMC for JMX-based management information;
- custom Java programs using JMXConnector/MBeanServerConnection;
- monitoring integrations that poll selected MXBean attributes.

The UI does not change the meaning of the underlying data. Understanding the MXBean/ObjectName model is still necessary to interpret values correctly.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="remote-jmx-security">The Security Boundary of Remote JMX</a>

<details>
<summary>Click for details</summary>

Remote JMX is a management interface that can expose detailed JVM state and, depending on the bean, management operations. It should not be placed on an untrusted network without controls.

Core practices include:

- require authentication rather than anonymous access;
- use TLS/SSL across untrusted networks;
- restrict source networks with firewall/network policy;
- never publish the management port openly to the Internet;
- protect credentials, keystores, and truststores as secrets;
- expose only the management access actually required.

Disabling authentication and SSL may be convenient for a local lab but is not a production baseline.

Remote management also has operational cost: aggressive polling or expensive operations can load the target JVM. A management API is not automatically zero-impact.

When the problem becomes IAM, certificate lifecycle, or infrastructure-security design, hand off to the corresponding security owner.

</details>

- [Quay lại đầu trang](#back-to-top)
