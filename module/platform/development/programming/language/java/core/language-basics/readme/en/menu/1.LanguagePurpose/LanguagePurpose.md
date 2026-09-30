# Java Language Basics: The Big Picture

## <a id="language-basics-roadmap">What Does Java Language Basics Teach?</a>

A Java program is more than a collection of syntactically valid statements. To read and write code deliberately, a learner needs to understand how Java **represents values, assigns types, organizes names, controls execution, and moves data through method calls**.

This module builds that foundation before larger topics such as object-oriented programming, Generics, Collections, and runtime mechanisms. Without it, later concepts easily become disconnected syntax instead of a coherent explanation of what the compiler and runtime are doing.

The learning journey is:

```text
How is a Java program organized?
        ↓
What kinds of values does Java manipulate?
Primitive / Reference
        ↓
Where do variables exist and where are names visible?
Variables / Scope / Lifetime
        ↓
What does it mean for a reference to identify no object?
null
        ↓
How do primitive values participate in object-based APIs?
Wrapper / Boxing / Unboxing
        ↓
How are values combined and converted?
Expressions / Operators / Casting
        ↓
How does execution choose a path?
Control Flow
        ↓
How does Java represent a fixed-size sequence?
Arrays
        ↓
How is behavior invoked and how does data cross a call boundary?
Methods / Varargs / Pass-by-Value
        ↓
How are type names organized and resolved?
package / import
        ↓
What is decided at compile time and what is still checked at runtime?
Type-system boundaries
        ↓
Put everything together into one mental model
```

By the end, the learner should be able to connect **values → variables → expressions → control flow → arrays → method calls → name organization → type checks** rather than treating each as an isolated chapter.
