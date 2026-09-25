# Constructors

## <a id="constructor-purpose">Constructor purpose and invariants</a>
A constructor initializes a newly allocated object and should establish the object's invariants before the reference escapes. Constructors are not ordinary methods: they have no return type, use the class name, and participate in a special chaining/initialization process.

## <a id="constructor-overloading">Constructor overloading and chaining</a>
A class can overload constructors. One constructor may delegate to another with `this(...)`, while `super(...)` delegates to a superclass constructor. Constructor invocation must be the first statement where traditional explicit syntax is used, so the object follows one well-defined initialization chain.

## <a id="default-constructor">Default constructor rules</a>
If a class declares no constructor, the compiler may generate a no-argument default constructor whose accessibility matches the class and which invokes an accessible superclass no-arg constructor. As soon as any constructor is declared, that implicit default constructor is not generated.

## <a id="constructor-exceptions">Constructor failure and partially-created state boundary</a>
If a constructor throws, the expression does not return a successfully constructed object reference. However, side effects performed before the failure can remain, and `this` can become observable if it escaped during construction. Avoid publishing `this` or registering callbacks before invariants are complete.
