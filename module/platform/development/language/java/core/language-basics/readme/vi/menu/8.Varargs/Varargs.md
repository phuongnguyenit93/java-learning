# Varargs

## <a id="varargs-array-model">Varargs thực chất là array</a>
Declaration như `void log(String... values)` được compile thành array parameter. Caller có thể truyền 0-n element hoặc một compatible array rõ ràng. Trong method, `values` là array và thậm chí có thể là `null` nếu caller chủ động truyền null array.

## <a id="varargs-overload">Varargs và overload resolution</a>
Varargs được xét sau các fixed-arity overload phase. Thêm varargs overload có thể tương tác bất ngờ với overload cũ, nhất là zero argument, array, boxing và `null`. Overload set nên nhỏ và dễ đoán.

## <a id="varargs-generics-warning">Generic varargs và heap-pollution boundary</a>
Generic element type có thể non-reifiable trong khi varargs dùng array, nên generic varargs có nguy cơ heap pollution. `@SafeVarargs` là lời cam kết của author rằng implementation safe; annotation không biến code unsafe thành safe.
