# equals

## <a id="equals-contract">Contract reflexive/symmetric/transitive/consistent/null</a>
Implementation `equals` đúng phải reflexive, symmetric, transitive, consistent khi state liên quan không đổi và trả false với `null`. Collection/library assume các law này dù compiler không enforce.

## <a id="equals-implementation">Implementation equals điển hình</a>
Value-style implementation thường check identity fast-path, type compatibility rồi compare toàn bộ field định nghĩa equality. Chọn `getClass` hay `instanceof` ảnh hưởng inheritance semantics. Dùng null-safe comparison cho nullable component và giữ `hashCode` dựa trên cùng equality state.

## <a id="equals-inheritance-risk">Rủi ro equality với inheritance</a>
Extend concrete value class bằng equality-relevant state mới có thể phá symmetry/transitivity. Parent có thể xem child equal trong khi child yêu cầu extra field. Khi value semantics xung đột inheritance, ưu tiên composition, closed hierarchy hoặc class-based equality cẩn thận.
