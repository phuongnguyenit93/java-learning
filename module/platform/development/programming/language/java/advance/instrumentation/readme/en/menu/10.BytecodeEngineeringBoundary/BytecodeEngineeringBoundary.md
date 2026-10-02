<a id="back-to-top"></a>

# Bytecode Engineering Boundary

## Menu
- [Bytecode Transformation Model in an Agent](#bytecode-transformation-model)
- [Class Generation vs Transformation](#generation-vs-transformation)
- [Class-File Validity After Transformation](#classfile-validity)
- [How Much Bytecode Engineering Belongs in Instrumentation?](#bytecode-engineering-scope)
- [Class-File API Standardized in Java 24 and the Java Version Boundary](#standard-classfile-api-version-boundary)

## <a id="bytecode-transformation-model">Bytecode Transformation Model in an Agent</a>

<details>
<summary>Click for details</summary>

Inside an agent, bytecode transformation can be modeled as:

~~~text
current class-file bytes
        +
transformation policy
        ↓
new class-file bytes
~~~

Instrumentation does not require an agent to parse constant pools or opcodes manually. It only requires a transformer to return valid bytes. The bytecode-engineering layer is responsible for:

- parsing input;
- locating methods/instructions of interest;
- inserting/removing/replacing code according to policy;
- updating metadata/stack maps where required;
- emitting a new class file.

For a timing probe:

~~~text
method entry
→ record start

all normal/exceptional exits
→ record duration
~~~

The hard part is not a few individual opcodes; it is preserving control flow, stack/local state, and class-file invariants.

</details>

- [Back to top](#back-to-top)

---

## <a id="generation-vs-transformation">Class Generation vs Transformation</a>

<details>
<summary>Click for details</summary>

**Generation** and **transformation** are related but different.

Generation:

~~~text
no previous class definition
→ build a new class from a model/builder
→ emit byte[]
~~~

Transformation:

~~~text
existing class-file bytes
→ preserve most structure
→ replace a subset
→ emit new byte[]
~~~

Java agents usually need transformation because the target class belongs to an application. A bytecode tool may still generate helper, proxy, or auxiliary classes.

This module's boundary is:

- understand transformation well enough to instrument methods correctly;
- understand generation well enough to distinguish use cases;
- do not turn Instrumentation into a general compiler/code-generation course.

Generating a new helper also does **not** bypass structural limits when the goal is to redefine an already-loaded target class.

</details>

- [Back to top](#back-to-top)

---

## <a id="classfile-validity">Class-File Validity After Transformation</a>

<details>
<summary>Click for details</summary>

Resulting class-file bytes must satisfy JVM class-file format and operation-specific constraints.

A bytecode tool often helps compute:

- constant-pool entries;
- instruction encoding;
- branch offsets;
- exception tables;
- stack-map frames;
- attributes.

The agent still owns semantics. A tool may emit a valid call to Helper.record(), but if Helper is not visible to the target class's defining loader/module, verification or linkage can still fail.

Validation should have layers:

~~~text
tool parses/emits
→ isolated verification/load test
→ agent integration test
→ target application smoke test
→ multi-agent / target-JDK compatibility test
~~~

“The bytecode library did not throw” is only the first layer.

</details>

- [Back to top](#back-to-top)

---

## <a id="bytecode-engineering-scope">How Much Bytecode Engineering Belongs in Instrumentation?</a>

<details>
<summary>Click for details</summary>

An Instrumentation learner does not need to become a deep bytecode engineer before writing an agent. The useful level is:

**Know**

- class files contain methods/code/attributes;
- transformers consume and return byte[];
- method bodies have control flow plus operand-stack/local-variable rules;
- verification/linkage happens after transformation;
- class-file versions/attributes evolve with the JDK;
- tooling abstractions have trade-offs.

**Hand off to JVM / bytecode-specialist curriculum**

- detailed constant-pool binary layout;
- deep verifier algorithms;
- every opcode semantic;
- custom optimizer/compiler-backend design.

The goal is enough depth to review transformation risk and debug agent failures, not to write an assembler from scratch.

</details>

- [Back to top](#back-to-top)

---

## <a id="standard-classfile-api-version-boundary">Class-File API Standardized in Java 24 and the Java Version Boundary</a>

<details>
<summary>Click for details</summary>

The Java platform now has a standard Class-File API in java.lang.classfile. Its evolution is:

~~~text
Java 22 → Preview
Java 23 → Second Preview
Java 24 → Standard API
~~~

It provides a model for parsing, generating, and transforming class files and can be an important tooling option for agents on suitable JDKs.

This Instrumentation module does not own the full java.lang.classfile curriculum because:

- Instrumentation is the runtime agent/lifecycle contract;
- Class-File API is a separate platform API for class-file processing;
- a JDK 21 learner still needs ASM, Byte Buddy, or another compatible tool;
- version-specific API evolution belongs to Java Version.

The connection is simply:

~~~text
ClassFileTransformer
→ needs new byte[]
→ Class-File API / ASM / Byte Buddy can produce those bytes
~~~

</details>

- [Back to top](#back-to-top)
