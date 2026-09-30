# Putting Java Language Basics Together

## <a id="language-basics-synthesis">One End-to-End Mental Model</a>

After studying each mechanism separately, connect them into one reasoning chain. When reading Java code, ask:

```text
Where does this code live in the program structure?
        ↓
What value and type does this expression/variable have?
        ↓
Is the name in scope here?
        ↓
Are conversion, boxing/unboxing, or null involved?
        ↓
Which operators and control-flow decisions determine execution?
        ↓
What values are stored in arrays or passed to methods?
        ↓
What has the compiler already decided?
        ↓
What still has to be checked at runtime?
```

This model is more useful than memorizing disconnected syntax because it explains both successful code and compile/runtime failures.

When reading difficult Java code, ask in order: what is the static type, is the value primitive or reference, which conversion context applies, what does the compiler decide, what remains for runtime, and is a mutation changing a local variable or shared object state?

The module's final chain is:

```text
source-code structure
→ primitive/reference values
→ variables/scope/lifetime
→ null
→ wrappers/boxing/unboxing
→ expressions/operators/casting
→ control flow
→ arrays
→ methods/varargs/pass-by-value
→ packages/imports
→ compile-time/runtime type boundaries
→ end-to-end synthesis
```

## <a id="end-to-end-value-flow">Trace a Value Through the Program</a>

Take one value and follow it: it may begin as a literal or expression result, be assigned to a variable, converted or boxed, stored in an array, copied into a method parameter through pass-by-value, and finally affect state or a control-flow decision.

Worked example:

```java
static void addBonus(int[] scores, int bonus) {
    if (scores.length > 0) {
        scores[0] = scores[0] + bonus;
    }
}

int base = 10;                    // literal 10 → int variable
int bonus = base + 5;             // expression produces int value 15
int[] scores = {base};            // value 10 is stored in an array element
addBonus(scores, bonus);          // copies the array reference value and primitive value 15
boolean passed = scores[0] > 20;  // visible mutation → boolean expression → later control flow
```

Trace it end to end:

```text
literal 10
→ base = 10
→ base + 5 produces bonus = 15
→ scores[0] starts as 10
→ the call copies the scores reference + primitive 15 into parameters
→ the method mutates the shared array object: scores[0] becomes 25
→ the caller still refers to that array and observes 25
→ scores[0] > 20 produces true
→ that boolean can drive the next control-flow decision
```

This single trace connects declaration, expression evaluation, array mutation, method transfer, pass-by-value, and a control-flow result.

For reference values, always separate two questions:

1. which **reference value** is stored in the variable;
2. what **state** belongs to the referenced object.

That distinction explains assignment, aliasing, mutation, null, array covariance, casting, and many runtime behaviors that beginners otherwise conflate.

## <a id="language-basics-next-boundaries">Boundaries to the Next Modules</a>

Language Basics provides the foundation rather than the full depth of every adjacent topic:

- **Class & Object / OOP** expands state, behavior, inheritance, polymorphism, and dynamic dispatch;
- **Generics** expands compile-time type safety and parameterized types;
- **Collections** expands dynamic data structures and collection contracts;
- **Exception** expands failure modeling and handling;
- **Numbers** expands overflow, floating point, BigInteger, BigDecimal, and rounding;
- **String** expands textual data representation and APIs.

When Language Basics introduces one of these only as a boundary, keep the explanation foundational and hand deeper treatment to the owning module.
