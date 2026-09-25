# Initialization Order

## <a id="class-initialization-order">Static initialization order</a>
Before static initializers run, static fields have default values. Explicit static field initializers and static blocks then execute in textual order for that class. Superclass initialization normally precedes subclass initialization when the subclass is actively initialized.

## <a id="instance-initialization-order">Instance field/block/constructor order</a>
After the superclass constructor portion completes, the subclass instance fields and instance initializer blocks execute in textual order, then the constructor body runs. Default field values exist before explicit initializers.

## <a id="inheritance-initialization-order">Parent/child initialization order</a>
For `new Child()`, the object is one allocation but construction proceeds through the superclass chain before subclass initialization/body. Static initialization and instance construction are separate lifecycles. Understanding this order explains why calling overridable methods from constructors can observe subclass fields before their explicit initializers have run.
