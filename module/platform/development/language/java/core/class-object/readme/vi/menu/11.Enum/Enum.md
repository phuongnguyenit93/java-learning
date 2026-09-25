# Enum

## <a id="enum-type-model">Enum constant là instance</a>
Enum là special class với tập named instance cố định. Mỗi constant là singleton instance cho initialized enum class, có thể compare bằng identity và có `name()` ổn định. Enum có thể implement interface và định nghĩa behavior như class khác trong các language restriction riêng.

## <a id="enum-fields-constructors">Field, constructor và method trong enum</a>
Enum constructor không được caller gọi trực tiếp. Nó initialize constant khai báo trong enum, có thể nhận argument, gán final field và enforce invariant. Field/method hữu ích khi mỗi constant mang domain metadata hoặc common behavior.

## <a id="enum-interface">Enum implement interface</a>
Enum có thể implement nhiều interface, giúp fixed constants được dùng qua abstraction. Điều này phù hợp với strategy có closed set implementation trong khi consumer không phụ thuộc concrete constant name.

## <a id="enum-constant-specific">Behavior riêng theo constant</a>
Constant có thể có class body và override behavior. Pattern này hỗ trợ strategy nhỏ gọn, nhưng workflow business lớn ẩn trong enum constant sẽ khó maintain; chỉ dùng khi behavior thực sự intrinsic với finite set.

## <a id="enum-values-valueof">Boundary values/valueOf/name/ordinal</a>
Compiler sinh `values()` trả constant theo declaration order; `valueOf` resolve exact constant name. `ordinal()` chỉ là positional metadata và không nên persist làm business identifier vì reorder constant sẽ đổi value. External storage/protocol nên dùng stable code explicit.
