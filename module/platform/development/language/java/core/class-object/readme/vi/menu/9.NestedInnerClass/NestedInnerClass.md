# Nested và Inner Class

## <a id="static-nested-class">Static nested class</a>
Static nested class được namespace bên trong class khác nhưng không có implicit outer-instance reference. Nó gần giống top-level class với thêm relationship về naming/access với enclosing class. Dùng khi helper type thuộc logical API của outer class nhưng không cần outer object.

## <a id="inner-class">Inner class và outer instance</a>
Non-static member inner class gắn với một enclosing instance và có thể access member của outer, kể cả private theo nest access rule. Tạo inner class thường cần outer instance; giữ inner object cũng có thể giữ outer object reachable.

## <a id="local-anonymous-class">Local class và anonymous class</a>
Local class khai báo trong block; anonymous class tạo unnamed subtype/implementation tại expression site. Chúng hữu ích cho behavior one-off nhưng có thể noisy so với lambda khi chỉ implement functional interface; lambda semantics đầy đủ thuộc functional module.

## <a id="capture-semantics">Captured local variable</a>
Local/anonymous class chỉ capture local variable final hoặc effectively final. Captured value ổn định dù mutable object được refer tới vẫn có thể mutate. Rule này tránh giả vờ một changing stack local được share như ordinary mutable variable.
