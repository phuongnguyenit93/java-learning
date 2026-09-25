# Multiple Inheritance qua Interface

## <a id="default-method-conflict">Resolve default-method conflict</a>
Nếu các interface không liên quan cung cấp cùng default signature và không default nào specific hơn, implementing class phải override để resolve conflict. Java không tự đoán behavior nào được mong muốn.

## <a id="class-wins-rule">Class method thắng interface default</a>
Concrete method inherit từ class hierarchy có precedence hơn interface default có compatible signature. Interface default là fallback implementation chứ không phải mechanism override existing class behavior.

## <a id="explicit-super-interface">Dispatch bằng InterfaceName.super</a>
Trong implementing-class override, `InterfaceName.super.method()` có thể gọi explicit một directly inherited interface default nếu language rule cho phép. Hữu ích khi class muốn combine default thay vì replace hoàn toàn.

## <a id="diamond-interface">Diamond-shaped interface inheritance</a>
Diamond tự thân không phải vấn đề. Nếu cả hai path inherit cùng most-specific default thì contract vẫn unambiguous. Conflict xuất hiện khi hai unrelated default cạnh tranh. Đây là multiple inheritance của type/behavior, không phải duplicated instance state.
