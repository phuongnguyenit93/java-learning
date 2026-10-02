<a id="back-to-top"></a>

# Java Native Interface

## Menu
- [What Is JNI and Why Does It Exist?](#jni-definition-purpose)
- [Native Method Declaration and Binding](#jni-native-method-binding)
- [JNIEnv and the JNI Function Interface](#jni-environment)
- [Type Mapping and Data Access Across the JNI Boundary](#jni-types-data-boundary)
- [Local References, Global References, and GC](#jni-references)
- [Java Exceptions and the Native Error Boundary](#jni-exception-boundary)
- [Native Threads and the JVM Thread Boundary](#jni-thread-boundary)

## <a id="jni-definition-purpose">What Is JNI and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

JNI, the Java Native Interface, is the long-standing standard interface between the JVM and native code. It lets Java declare methods whose implementations live in native libraries and lets native code call back into JVM services through a defined function interface.

A typical Java-to-native flow is:

```text
Java calls a native method
        ↓
JVM resolves the native implementation
        ↓
native function receives JNIEnv* + Java arguments
        ↓
native code uses JNI functions when it must interact with Java objects
        ↓
result or pending Java exception crosses back
```

JNI exists because native code must not treat JVM-managed objects as ordinary C memory. Objects may move, be collected, and obey VM rules that raw native pointers do not understand.

The important mental model is that JNI is a **bridge contract**. It defines how values, references, threads, methods, fields, and exceptions cross the Java/native boundary while keeping the JVM involved in managed-object operations.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-native-method-binding">Native Method Declaration and Binding</a>

<details>
<summary>Click for details</summary>

A Java native method has a Java signature but no Java implementation body:

```java
final class NativeMath {
    static {
        System.loadLibrary("native_math");
    }

    static native int add(int left, int right);
}
```

`javac -h` can generate a JNI C/C++ header. A matching implementation follows JNI naming and parameter conventions:

```c
JNIEXPORT jint JNICALL
Java_com_example_NativeMath_add(
        JNIEnv* env,
        jclass type,
        jint left,
        jint right) {
    return left + right;
}
```

The JVM can resolve native methods through JNI's naming convention, including a long form used when overloaded methods need disambiguation. Native libraries can also bind methods explicitly with `RegisterNatives`.

Library loading and method binding are separate stages. A library may load successfully while the method still fails to link because the expected native implementation is absent or incorrectly declared.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-environment">JNIEnv and the JNI Function Interface</a>

<details>
<summary>Click for details</summary>

`JNIEnv` is the native thread's gateway to JVM services. It exposes the JNI function interface for operations such as finding classes, creating strings, reading fields, calling Java methods, managing references, and checking exceptions.

Native methods receive a `JNIEnv*` from the JVM:

```c
JNIEXPORT void JNICALL
Java_com_example_Bridge_run(JNIEnv* env, jobject self) {
    jclass cls = (*env)->FindClass(env, "java/lang/String");
    /* ... */
}
```

The crucial rule is that a `JNIEnv*` belongs to the current attached native thread. Do not cache one and use it from another thread. Native threads must obtain their own environment through the `JavaVM` attachment contract.

The environment also carries pending-exception state for the thread. After a JNI operation raises an exception, native code must follow the JNI rules for checking, clearing, replacing, or propagating it instead of continuing as if nothing happened.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-types-data-boundary">Type Mapping and Data Access Across the JNI Boundary</a>

<details>
<summary>Click for details</summary>

JNI maps Java primitive values to JNI-defined native types such as `jint`, `jlong`, and `jdouble`. Object-like Java values cross the boundary as JNI reference types such as `jobject`, `jclass`, `jstring`, and array references.

These references are handles governed by JNI rules. A `jstring` is not simply a permanent `char*`, and a Java array is not guaranteed to be a stable native array pointer.

JNI therefore provides explicit access operations:

```text
jstring
  → GetStringUTFChars
  → use borrowed/native view
  → ReleaseStringUTFChars

jintArray — elements path
  → GetIntArrayElements
  → process borrowed/copied elements
  → ReleaseIntArrayElements

jintArray — region path
  → GetIntArrayRegion into caller-owned buffer
  → process copied region
  → no ReleaseIntArrayElements for that region buffer
```

Some access methods may copy; others may expose or pin VM-managed storage depending on the operation and implementation. Use the matching lifetime contract for the operation: element access has a corresponding release, while region access copies into the caller's buffer.

Think in terms of **managed access with a defined lifetime**, not “JNI gave native code ownership of a Java object's memory.”

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-references">Local References, Global References, and GC</a>

<details>
<summary>Click for details</summary>

JNI references connect native code to Java objects while still allowing the JVM to manage those objects.

**Local references** are thread-local references associated with a JNI local frame. During a Java → native method call, the JVM releases that call's local references when the native method returns. A native thread attached through the Invocation API has no such native-method return boundary for arbitrary JNI work, so its local references can remain until they are deleted, their local frame is popped, or the thread detaches. Long-lived attached threads must therefore clean up local references explicitly.

**Global references** remain valid independently of a particular local frame or attached-thread lifetime and can be retained by native state. They are created with `NewGlobalRef` and must be released with `DeleteGlobalRef`.

**Weak global references** do not keep the referent alive. A simple check followed by later use is racy because the referent can be collected between those operations. Before dereferencing a weak global, promote it to a strong local/global reference—for example with `NewLocalRef`—and test the promoted reference for `NULL`. A successful promotion keeps the object alive for the lifetime of that strong reference.

Reference kind therefore expresses lifetime:

```text
temporary work inside one JNI frame → local reference
must outlive the local frame/thread → global reference
must not keep object alive          → weak global reference
```

Leaking a global reference can keep a Java object reachable indefinitely. Persisting a local reference beyond its valid frame can leave native code holding an invalid handle; conversely, attached native threads should not assume locals are auto-freed between arbitrary JNI calls.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-exception-boundary">Java Exceptions and the Native Error Boundary</a>

<details>
<summary>Click for details</summary>

Java exceptions and native error reporting are different mechanisms. JNI exposes operations such as `ExceptionCheck`, `ExceptionOccurred`, `ExceptionDescribe`, `ExceptionClear`, `Throw`, and `ThrowNew` so native code can participate in Java exception semantics deliberately. `ExceptionDescribe` is diagnostic but has an important side effect: it describes and then clears the pending exception.

When a JNI operation raises a Java exception, it becomes **pending** on the current thread. Native code should usually stop ordinary JNI work and return so that the exception can propagate, unless it has a deliberate recovery or translation path.

```c
jclass cls = (*env)->FindClass(env, "com/example/Missing");
if ((*env)->ExceptionCheck(env)) {
    return; // propagate the pending Java exception
}
```

Native libraries may instead report failures with status codes, `errno`-style values, output parameters, or library-specific error objects. The integration layer must define how recoverable native failures become Java results or exceptions.

A native segmentation fault is different again: it is not a Java exception and may crash the entire JVM process. JNI can translate error protocols, but it cannot make arbitrary native memory faults recoverable.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-thread-boundary">Native Threads and the JVM Thread Boundary</a>

<details>
<summary>Click for details</summary>

When a Java thread enters a native method, that thread is already attached to the JVM and receives a valid `JNIEnv*`.

A thread created independently by native code must attach before using JNI:

```text
native thread starts
      ↓
AttachCurrentThread or AttachCurrentThreadAsDaemon
      ↓
obtain this thread's JNIEnv*
      ↓
perform JNI work
      ↓
DetachCurrentThread before the native thread exits
```

The process-wide `JavaVM*` is the handle used for attachment and environment lookup. The `JNIEnv*` returned for one thread must not be reused by another.

Attachment is also a lifecycle concern. Native thread pools may live much longer than a single Java call, and daemon versus non-daemon attachment affects how those threads participate in JVM shutdown.

Design the native thread lifecycle first, then align attach/detach operations with it.

That thread boundary leads directly to the JNI Invocation API: the next chapter reverses the usual direction and asks how a native application hosts a JVM and manages VM-wide startup, attachment, and shutdown.

</details>

- [Quay lại đầu trang](#back-to-top)
