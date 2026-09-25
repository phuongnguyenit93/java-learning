# Variables and Scope

## <a id="variable-kinds-and-lifetime">Local, parameter, field and static variable lifetime</a>
Local variables and parameters belong to an invocation or block and have no automatic default value. Instance fields belong to an object. Static fields belong to the class and are shared by its instances within the same defining class loader.

Lifetime and visibility are different ideas: a field may outlive a method invocation, while a local variable is only in lexical scope inside its block. Object lifetime depends on reachability, not on the lexical scope of one reference variable.

## <a id="scope-and-shadowing">Scope, shadowing and name resolution</a>
Lexical scope determines where a name can be referenced. A local variable or parameter can shadow a field. Use `this.field` when shadowing is intentional and important. Excessive shadowing makes code harder to reason about because the same identifier can refer to different storage locations.

## <a id="definite-assignment">Definite assignment rules</a>
The compiler performs definite-assignment analysis for local variables and blank `final` variables. A local variable must be provably assigned on every control-flow path before it is read.

```java
int value;
if (ready) value = 10;
// System.out.println(value); // compile error
```

This is a compile-time guarantee, not a runtime initialization step. Fields are different because object/class initialization gives them default values before constructors or explicit initializers run.
