# this và super

## <a id="this-reference">Reference this</a>
Trong instance context, `this` refer tới receiver object hiện tại. Nó phân biệt field với parameter bị shadow, có thể truyền như value và có thể chọn constructor khác bằng `this(...)`. Static context không có `this` vì không có receiver object.

## <a id="super-access">Truy cập member/constructor qua super</a>
`super` chọn superclass behavior từ current object context. Nó có thể gọi accessible superclass constructor hoặc bypass override để gọi superclass implementation. Nó không tạo hay refer tới một “parent object” khác; runtime vẫn chỉ có một object.

## <a id="constructor-chaining-order">Quy tắc chaining this()/super()</a>
Mọi constructor chain cuối cùng đều gọi superclass constructor. Constructor delegate hoặc sang constructor cùng class hoặc trực tiếp sang superclass constructor, không có hai first-step cạnh tranh. Phần superclass initialize trước khi subclass constructor body chạy.
