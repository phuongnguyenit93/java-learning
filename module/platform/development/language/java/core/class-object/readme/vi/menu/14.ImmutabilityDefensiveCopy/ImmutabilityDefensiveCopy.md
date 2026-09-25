# Immutability và Defensive Copy

## <a id="immutable-object-design">Thiết kế immutable object</a>
Observable state của immutable object không đổi sau khi construct thành công. Thiết kế thường giữ field private/final, establish invariant trong construction, không có mutator và không expose mutable internal object. `final` trên reference không tự làm nested object immutable.

## <a id="defensive-copy-input">Defensive copy ở input</a>
Nếu constructor/method nhận mutable value và dùng nó làm internal state, hãy copy khi object cần sở hữu snapshot độc lập. Nếu lưu reference trực tiếp, caller có thể mutate object đó sau này và âm thầm thay internal state.

## <a id="defensive-copy-output">Defensive copy ở output</a>
Trả mutable internal object sẽ leak alias. Tùy contract, hãy trả immutable representation, independent copy hoặc documented view. `Collections.unmodifiableList(internal)` là read-only view chứ không nhất thiết immutable snapshot.

## <a id="deep-immutability">Shallow và deep immutability</a>
Object có final field vẫn có thể chỉ shallow immutable nếu field refer tới mutable object mà alias khác có thể thay đổi. Deep immutability yêu cầu reachable state liên quan abstraction phải immutable hoặc exclusive-owned và không mutate sau construction.
