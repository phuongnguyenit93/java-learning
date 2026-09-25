# Access Modifier

## <a id="access-levels">private/package/protected/public</a>
`private` giới hạn access theo declaring class/nest rule; không modifier tạo package-private; `protected` kết hợp same-package access với controlled subclass access; `public` mở member ở nơi declaring type accessible. Access control chủ yếu được compiler check; reflection có boundary riêng.

## <a id="protected-cross-package">Protected access xuyên package</a>
Ở package khác, subclass không tự động được access protected member qua mọi superclass instance. Access còn phụ thuộc subclass context và qualifying expression rule. Vì vậy không nên mô tả `protected` đơn giản là “package + subclass ở mọi nơi”.

## <a id="encapsulation-boundary">Access control như một encapsulation boundary</a>
Modifier định nghĩa visibility nhưng encapsulation rộng hơn: invariant, mutation path, ownership và API design cũng quan trọng. Field `private` nhưng setter unrestricted vẫn có thể tạo object model yếu.
