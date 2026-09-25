# Varargs

## <a id="varargs-array-model">Varargs are arrays</a>
A declaration such as `void log(String... values)` is compiled as an array parameter. Callers may pass zero or more elements, or an explicit compatible array. Inside the method, `values` is an array and may itself be `null` if the caller explicitly passes a null array.

## <a id="varargs-overload">Varargs and overload resolution</a>
Varargs is considered after fixed-arity overload phases. Adding a varargs overload can interact with existing overloads in non-obvious ways, especially around zero arguments, arrays, boxing, and `null`. Keep overload sets small and unsurprising.

## <a id="varargs-generics-warning">Generic varargs and heap-pollution boundary</a>
Because generic element types may be non-reifiable while varargs uses an array, generic varargs can expose heap-pollution risks. `@SafeVarargs` is a promise by the author that the implementation is safe; it is not a switch that makes unsafe code safe.
