# How a Java Program Is Organized

Before studying value and type rules in depth, a beginner needs a map of **where Java code lives inside a program**. This chapter provides enough source anatomy to read small programs; class/object design belongs to the dedicated downstream module.

## <a id="source-file-type-structure">Source Files and Type Declarations</a>

Java source is written in `.java` files. A source file can contain a package declaration, imports, and one or more type declarations subject to Java's language rules.

```java
package demo;

import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

At a foundational level, read the structure from the outside inward:

```text
source file
→ package/import
→ type declaration
→ member
→ method/constructor body
→ statement/expression
```

Packages and imports are only identified here so the learner can read a source file. Namespace behavior, name resolution, and package-level access are covered in a later milestone.

## <a id="statement-expression-model">Statements and Expressions</a>

An **expression** produces or contributes to a value. A **statement** represents an action or control step in the program.

```java
int total = price * quantity;
```

Here, `price * quantity` is an expression; the entire variable declaration ending in `;` is a statement.

Not every expression can stand alone as a statement, and one statement may contain multiple expressions. This distinction becomes important for operators, conditions, loops, and method calls.

## <a id="lexical-elements">Identifiers, Keywords, Literals, and Comments</a>

Java source also contains smaller lexical elements:

- **identifier**: a programmer-defined name for a variable, method, type, and so on;
- **keyword**: a reserved word with language meaning such as `class`, `if`, `return`, or `new`;
- **literal**: source syntax that directly denotes a value, such as `10`, `true`, `'A'`, or `"Java"`;
- **comment**: text for human readers that does not become executable behavior.

The goal is not to memorize the whole grammar here. It is to have enough vocabulary to look at a small Java program and recognize what role each piece plays.
