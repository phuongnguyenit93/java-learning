<a id="back-to-top"></a>

# JNI Invocation API

## Menu
- [What Is the Invocation API For?](#jni-invocation-purpose)
- [VM Initialization Options and Default Arguments](#jni-vm-init-args)
- [Creating and Hosting a JVM from a Native Application](#jni-create-jvm)
- [Discovering JVMs Created in the Current Process](#jni-get-created-vms)
- [GetEnv, Thread Attachment/Detachment, and Daemon Attachment](#jni-attach-detach-thread)
- [DestroyJavaVM and VM Termination](#jni-destroy-jvm)
- [JNI_OnLoad, JNI_OnUnload, and Library/Version Management](#jni-library-lifecycle)
- [Lifecycle and Responsibilities When Embedding a JVM](#jni-invocation-lifecycle)

## <a id="jni-invocation-purpose">What Is the Invocation API For?</a>

<details>
<summary>Click for details</summary>

The JNI Invocation API is used when a **native application hosts a JVM** or when native code needs the VM-level thread attachment interface. This reverses the common JNI direction: the native process can start the JVM and then call Java code.

Its main lifecycle looks like:

```text
prepare VM options
      ↓
JNI_CreateJavaVM
      ↓
JavaVM* + initial JNIEnv*
      ↓
GetEnv / attach / detach native threads
      ↓
DestroyJavaVM during orderly shutdown when appropriate
```

The Invocation API also provides current-process VM discovery through `JNI_GetCreatedJavaVMs`.

The central distinction is between a VM-wide handle and a thread-local environment. `JavaVM*` represents the invocation interface for the JVM; `JNIEnv*` represents JNI access for one attached thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-vm-init-args">VM Initialization Options and Default Arguments</a>

<details>
<summary>Click for details</summary>

An embedding application creates a JVM with `JavaVMInitArgs` and an array of `JavaVMOption` entries. The arguments include the requested JNI version and startup options.

```c
JavaVMOption options[1];
options[0].optionString = "-Djava.class.path=app.jar";

JavaVMInitArgs args;
args.version = JNI_VERSION_1_8;
args.nOptions = 1;
args.options = options;
args.ignoreUnrecognized = JNI_FALSE;
```

`JNI_GetDefaultJavaVMInitArgs` is part of the Invocation API for obtaining/validating default VM initialization information for a requested version.

Startup configuration belongs to the host lifecycle. Unsupported VM options, an invalid class path, or an unsupported requested JNI version can prevent the JVM from starting before application code runs.

Keep initialization options explicit and centrally owned by the host rather than scattering option strings throughout native code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-create-jvm">Creating and Hosting a JVM from a Native Application</a>

<details>
<summary>Click for details</summary>

`JNI_CreateJavaVM` creates a JVM inside the current native process. On success it returns:

- a `JavaVM*` for VM-level operations;
- a `JNIEnv*` for the thread that created the JVM.

```c
JavaVM* vm;
JNIEnv* env;

jint rc = JNI_CreateJavaVM(
    &vm,
    (void**)&env,
    &args
);
if (rc != JNI_OK) {
    /* startup failed */
}
```

The Invocation API does **not support creating multiple JVMs in one process**. Design the host around one JVM instance per process rather than treating `JNI_CreateJavaVM` as a factory for independent VMs.

The creating thread can then use `env` to find classes, call Java methods, and create/manage Java objects under the normal JNI rules.

Creating the JVM is only the beginning. The native host still owns bootstrap errors, Java exception handling, thread attachment policy, native resource lifetime, and shutdown coordination.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-get-created-vms">Discovering JVMs Created in the Current Process</a>

<details>
<summary>Click for details</summary>

`JNI_GetCreatedJavaVMs` reports JVMs that were created in the **current process**. It is useful when native code is running in a process that may already host a JVM and needs a `JavaVM*` rather than creating another VM.

```c
JavaVM* vms[1];
jsize count = 0;

jint rc = JNI_GetCreatedJavaVMs(vms, 1, &count);
if (rc == JNI_OK && count > 0) {
    JavaVM* vm = vms[0];
}
```

This is not a machine-wide JVM discovery API. It does not list other Java processes.

Finding the `JavaVM*` also does not mean the current native thread is attached. The next step is to call `GetEnv` and attach the thread when necessary.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-attach-detach-thread">GetEnv, Thread Attachment/Detachment, and Daemon Attachment</a>

<details>
<summary>Click for details</summary>

`JavaVM::GetEnv` checks the current thread's attachment state for a requested JNI version. It can return a valid `JNIEnv*` when the thread is attached, `JNI_EDETACHED` when it is not, or `JNI_EVERSION` for an unsupported version.

```text
GetEnv
  ├─ JNI_OK        → use returned JNIEnv*
  ├─ JNI_EDETACHED → AttachCurrentThread / AttachCurrentThreadAsDaemon
  └─ JNI_EVERSION  → fix requested JNI version
```

`AttachCurrentThread` attaches the current native thread as a normal Java-attached thread. `AttachCurrentThreadAsDaemon` gives it daemon semantics for JVM termination. `DetachCurrentThread` removes the association before the native thread exits; the thread cannot detach while Java methods are still present on its call stack.

Align attachment with the real worker-thread lifecycle. A long-lived native worker should not attach and detach around every tiny JNI call, while a short-lived thread should not terminate while still attached.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-destroy-jvm">DestroyJavaVM and VM Termination</a>

<details>
<summary>Click for details</summary>

`DestroyJavaVM` requests orderly JVM termination in an embedding scenario. It is not a force-kill operation.

Shutdown depends on live non-daemon activity. A host cannot assume the VM will instantly vanish while Java or attached non-daemon work is still keeping it alive.

A practical shutdown design is:

```text
stop accepting new native/Java work
      ↓
finish application tasks
      ↓
release application-owned native/JNI resources
      ↓
detach native workers as appropriate
      ↓
request DestroyJavaVM
```

Do not use VM destruction as a substitute for correct per-resource cleanup. Global JNI references, native allocations, and thread ownership should already have defined lifetimes.

Some hosts intentionally keep the JVM alive until process exit. In those designs, process lifetime is the final boundary and `DestroyJavaVM` may not be part of the normal path.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-library-lifecycle">JNI_OnLoad, JNI_OnUnload, and Library/Version Management</a>

<details>
<summary>Click for details</summary>

`JNI_OnLoad` and `JNI_OnUnload` are optional hooks associated with a JNI native library's lifecycle.

`JNI_OnLoad` runs when the JVM loads the library. It can initialize library state, cache the `JavaVM*`, register native methods, and return the JNI interface version the library expects:

```c
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    /* initialize library state */
    return JNI_VERSION_1_8;
}
```

If the library returns an unsupported version, loading fails.

`JNI_OnUnload` gives the library an opportunity to release library-wide resources when the JVM unloads it as part of the native-library/class-loader lifecycle. The JNI specification warns that this callback runs in an unknown context, so cleanup should be conservative: avoid arbitrary Java callbacks or assumptions about thread/class-loader state.

These callbacks are broad lifecycle hooks. Per-request, per-object, per-buffer, or per-callback resources should still be released according to their own ownership rules rather than waiting for library unload.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-invocation-lifecycle">Lifecycle and Responsibilities When Embedding a JVM</a>

<details>
<summary>Click for details</summary>

Embedding a JVM makes the native application responsible for a managed runtime inside its process. A robust host therefore assigns ownership at every stage.

| Stage | Native host responsibility |
| --- | --- |
| configuration | choose JNI version and VM options |
| startup | create the JVM and handle startup failure |
| Java calls | manage references and pending exceptions |
| native workers | obtain per-thread `JNIEnv*` through attach/GetEnv |
| resources | release JNI and native state at the correct lifetime |
| shutdown | stop work, detach threads, and coordinate VM termination |

Keep two invariants in mind:

1. `JavaVM*` is the VM-level handle; `JNIEnv*` is thread-specific.
2. Java exceptions and fatal native process failures belong to different failure domains.

Treat JVM embedding as a real runtime lifecycle with explicit bootstrap, thread participation, error translation, and cleanup. That is more reliable than viewing the Invocation API as a handful of unrelated C functions.

After completing the JNI model in both directions, the next chapter introduces FFM as a different Java-facing model for foreign memory and C-style function contracts, rather than as a replacement for JVM embedding.

</details>

- [Quay lại đầu trang](#back-to-top)
