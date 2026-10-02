<a id="back-to-top"></a>

# Structural Limits of Class Modification

## Menu
- [Boundary of Modifying Already-Loaded Classes](#modification-boundary)
- [Limits on Fields, Methods, Signatures, and Hierarchy Changes](#schema-change-limits)
- [Restricted Structural Class-File Attributes](#structural-attribute-limits)
- [Unmodifiable Classes](#unmodifiable-classes)
- [Verification, Linkage, and Format Constraints](#verification-linkage-constraints)
- [JDK and Class-File Version Compatibility](#version-compatibility)

## <a id="modification-boundary">Boundary of Modifying Already-Loaded Classes</a>

<details>
<summary>Click for details</summary>

Once a class is loaded, it is no longer “an arbitrary byte file that can be replaced with any schema.” The JVM is already maintaining:

- Class identity;
- resolved runtime metadata;
- field layout;
- method descriptors;
- active stack frames;
- instances and static state;
- current inheritance/module/class-loader relationships.

Redefine/retransform therefore allow only changes compatible with existing runtime state.

Mental model:

~~~text
method implementation
→ can change within supported limits

class shape / object layout / inheritance identity
→ largely must remain stable
~~~

Instrumentation is powerful for **changing implementation/inserting probes**, not for hot-migrating arbitrary type schemas.

</details>

- [Back to top](#back-to-top)

---

## <a id="schema-change-limits">Limits on Fields, Methods, Signatures, and Hierarchy Changes</a>

<details>
<summary>Click for details</summary>

Major structural changes are restricted, including attempts to:

- add/remove fields;
- add/remove methods;
- change method signatures;
- change class, field, or method modifiers;
- change superclass or implemented interfaces;
- rename the class.

Turning:

~~~java
class OrderService {
    void placeOrder() { ... }
}
~~~

into:

~~~java
class OrderService extends NewBase {
    long agentState;
    void placeOrder(String id) {}
}
~~~

is not the kind of change redefine/retransform should be expected to support.

Agents usually alter **existing method bodies** or call external helper code so object layout remains stable.

</details>

- [Back to top](#back-to-top)

---

## <a id="structural-attribute-limits">Restricted Structural Class-File Attributes</a>

<details>
<summary>Click for details</summary>

Beyond fields, methods, and hierarchy, some class-file attributes carry structural meaning and cannot be arbitrarily changed for an already-loaded class. In modern JVM TI redefine/retransform rules, important examples include:

- NestHost / NestMembers;
- Record;
- PermittedSubclasses.

These are not merely decorative metadata. They participate in nestmate access, record identity/components, and sealed-class hierarchy.

Do not assume:

~~~text
"it is only an attribute"
→ "it is safe to rewrite"
~~~

A bytecode library may happily emit bytes with different attributes; the JVM still decides whether that definition is legal for redefine/retransform.

</details>

- [Back to top](#back-to-top)

---

## <a id="unmodifiable-classes">Unmodifiable Classes</a>

<details>
<summary>Click for details</summary>

Not every Class is modifiable.

The Instrumentation API guarantees that:

- primitive classes are not modifiable;
- array classes are not modifiable;
- a JVM implementation may expose additional unmodifiable classes.

Check first:

~~~java
if (!inst.isModifiableClass(target)) {
    // skip or report unsupported target
    return;
}
~~~

Hidden classes/interfaces **cannot be modified by Java agents or JVMTI agents**. getAllLoadedClasses() can still expose them, so obtaining a Class object does not imply redefine/retransform capability; isModifiableClass() must report false for this case.

A good tool reports “target exists but is not modifiable” instead of exposing only an unexplained UnmodifiableClassException.

</details>

- [Back to top](#back-to-top)

---

## <a id="verification-linkage-constraints">Verification, Linkage, and Format Constraints</a>

<details>
<summary>Click for details</summary>

Transformer output must pass several layers:

~~~text
well-formed class-file format
        ↓
supported class-file version
        ↓
verification
        ↓
name / hierarchy / structural constraints
        ↓
linkage
        ↓
definition installed
~~~

Failures can surface as:

- ClassFormatError;
- UnsupportedClassVersionError;
- NoClassDefFoundError when the class name does not match;
- ClassCircularityError;
- LinkageError;
- UnsupportedOperationException for unsupported structural change.

ClassFileTransformer runs **before** verification. Successfully generating a byte[] with a library does not mean the JVM will accept the definition.

Transformer testing should include negative cases, not only “ASM/Byte Buddy did not throw.”

</details>

- [Back to top](#back-to-top)

---

## <a id="version-compatibility">JDK and Class-File Version Compatibility</a>

<details>
<summary>Click for details</summary>

The class-file format evolves with the JDK. An agent built with an old bytecode library may not understand a newer class-file version or newly introduced attributes.

Useful strategies:

- define and test a supported-JDK matrix;
- update ASM/Byte Buddy when support for newer class-file versions is required;
- avoid stripping attributes that the tool does not understand;
- fail open with null for unsupported classes/versions when the agent is observational only;
- fail fast when instrumentation is a correctness requirement and unsupported versions are unacceptable.

For example:

~~~text
agent compiled on JDK 17
        ≠
automatically safe for every JDK 21/24 class file
~~~

Compatibility includes JVM APIs, class-file versions, and bytecode tooling — not merely the source/target level of the agent JAR.

</details>

- [Back to top](#back-to-top)
