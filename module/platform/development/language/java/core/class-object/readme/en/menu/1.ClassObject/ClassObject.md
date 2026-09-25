# Class and Object

## <a id="class-object-model">Class vs object mental model</a>
A class defines a type and implementation blueprint: fields, methods, constructors, nested types, and initialization logic. An object is a runtime instance with its own identity and instance state. Many objects can share one class definition while holding different field values.

## <a id="fields-methods-state">State, behavior and instance identity</a>
Instance fields represent per-object state; instance methods operate using an implicit `this` reference. Static members belong to the class-level context instead. Two objects can contain equal state while remaining distinct identities.

## <a id="object-reference-lifecycle">Reference reachability and object lifetime boundary</a>
Variables hold references, not embedded objects. Object lifetime is governed by reachability and GC rather than lexical scope or an explicit destructor. Resource lifetime is a separate concern: files, sockets, and similar resources should be closed deterministically instead of waiting for GC.
