# this and super

## <a id="this-reference">this reference</a>
Inside an instance context, `this` refers to the current receiver object. It disambiguates fields from shadowing parameters, can be passed as a value, and can select another constructor with `this(...)`. It is unavailable in a static context because no receiver object exists there.

## <a id="super-access">super member/constructor access</a>
`super` selects superclass behavior from the current object context. It can invoke an accessible superclass constructor or bypass an override to call a superclass implementation. It does not create or refer to a second “parent object”; the runtime object is still one object.

## <a id="constructor-chaining-order">this()/super() constructor chaining rules</a>
Every constructor chain ultimately invokes a superclass constructor. A constructor delegates either to another constructor in the same class or directly to a superclass constructor, not both as competing first steps. The superclass portion initializes before the subclass constructor body runs.
