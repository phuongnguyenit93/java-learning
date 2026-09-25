# Giá trị null trong Java

## <a id="null-reference">null như một reference value</a>
`null` là reference value đặc biệt biểu diễn không refer tới object nào. Nó assign được cho reference type, không phải primitive. `null` không có runtime class và không nên xem như một placeholder object.

## <a id="null-dereference">Dereference failure</a>
Truy cập instance member qua `null`, unbox wrapper null hoặc operation khác yêu cầu object sẽ gây `NullPointerException`. JVM hiện đại có message tốt hơn, nhưng prevention vẫn dựa trên contract và validation rõ ràng.

## <a id="null-comparison">So sánh null và control flow</a>
Dùng `==`/`!=` để test reference với `null`. Chỉ gọi instance method sau khi có non-null guarantee. Helper như `Objects.equals` có thể làm equality tolerate null mà không thay đổi null model.

## <a id="null-api-design">Nullability như một vấn đề thiết kế API</a>
Cần quyết định absence có hợp lệ không và document rõ. Trả `null`, throw khi thiếu data, trả empty collection hay dùng `Optional` mang semantics khác nhau; curriculum đầy đủ về `Optional` thuộc functional module. Tránh nullable boundary không được giải thích.
