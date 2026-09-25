# hashCode

## <a id="hashcode-contract">Contract hashCode</a>
Nếu hai object equal theo `equals`, chúng bắt buộc có cùng hash code. Object không equal vẫn có thể collision. Trong một execution, repeated call nên consistent khi equality-relevant state không đổi.

## <a id="hash-distribution">Hash distribution và performance</a>
Hash tốt combine equality-relevant field để object phổ biến phân bố hợp lý qua bucket. Không cần và không thể guarantee unique hash. Correctness đến từ equality contract; distribution ảnh hưởng performance của hash table.

## <a id="mutable-key-risk">Rủi ro mutable field trong hashCode</a>
Nếu equality/hash field của key mutate sau khi insert vào hash collection, bucket location không còn match current hash và lookup/remove có thể fail. Ưu tiên immutable key hoặc không mutate key state khi đang stored.
