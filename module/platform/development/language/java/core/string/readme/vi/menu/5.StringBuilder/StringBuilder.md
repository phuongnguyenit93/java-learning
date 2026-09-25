# StringBuilder

## <a id="builder-mutable-buffer">Mental model mutable buffer của StringBuilder</a>
`StringBuilder` là mutable sequence dùng để build text mà không tạo String mới cho mọi intermediate append/insert/delete. Nó không phải subtype của String; gọi `toString()` khi cần immutable String result.

## <a id="builder-capacity">Length và capacity</a>
Length là số character hiện có; capacity là internal storage trước khi cần grow. Capacity grow tự động và là implementation/performance concern, không thuộc text value. Pre-size có thể hữu ích khi size dự đoán được nhưng nên đo trước khi micro-optimize.

## <a id="builder-usage">Xây chuỗi incremental hiệu quả</a>
Dùng một builder cho local construction flow, append piece rồi convert một lần. Tránh share mutable builder giữa các caller không liên quan. Fluent append dễ đọc, nhưng formatting phức tạp có thể phù hợp với formatter/template hơn manual delimiter logic.
