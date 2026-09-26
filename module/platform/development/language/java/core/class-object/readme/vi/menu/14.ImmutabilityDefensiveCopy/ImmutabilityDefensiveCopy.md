# Immutability và Defensive Copy

Nếu object không thay đổi observable trạng thái sau construction, sharing trở nên dễ suy luận hơn rất nhiều. Nhưng “field final” chưa đủ; ta phải xem cả object graph và reference bị expose ra ngoài.

## <a id="immutable-object-design">Thiết kế Immutable Object</a>

Một immutable object thường:

- thiết lập toàn bộ trạng thái trong constructor/factory;
- không expose mutator làm thay đổi trạng thái;
- dùng `final` cho field khi phù hợp;
- không để mutable internal object bị leak;
- nếu subclass có thể phá immutability, hạn chế extension phù hợp.

```java
final class Profile {
    private final String name;
    ...
}
```

Immutability giúp sharing, caching, hashing và concurrency reasoning đơn giản hơn.

## <a id="defensive-copy-input">Defensive Copy đầu vào</a>

Nếu constructor nhận mutable object và giữ trực tiếp reference, bên gọi vẫn có thể thay đổi trạng thái nội bộ sau construction.

```java
this.roles = new ArrayList<>(roles);
```

Defensive copy trên đầu vào tách quan hệ sở hữu khỏi collection do bên gọi giữ.

Với immutable đầu vào như `String`, không cần copy chỉ để “cho chắc”.

## <a id="defensive-copy-output">Defensive Copy đầu ra</a>

Getter không nên trả reference mutable nội bộ nếu bên gọi không được phép mutate trạng thái.

Có thể dùng:

- immutable/unmodifiable view phù hợp;
- `List.copyOf(...)`;
- copy mới tùy hợp đồng.

Phân biệt unmodifiable view với deep immutability: view có thể cấm bên gọi mutate qua view nhưng underlying data vẫn có thể thay đổi ở nơi khác.

## <a id="deep-immutability">Shallow và Deep Immutability</a>

Object có `final List<Address> addresses` chưa chắc deeply immutable nếu `Address` hoặc list phía sau vẫn mutable.

```text
shallow immutability
→ reference/trạng thái bên ngoài không được gán lại

deep immutability
→ toàn bộ trạng thái có thể truy cập giữ hợp đồng bất biến phù hợp
```

Không phải domain nào cũng cần deep immutability, nhưng quan hệ sở hữu phải rõ để bên gọi biết dữ liệu có thể thay đổi từ đâu.

Kết thúc module, hãy giữ một chuỗi suy nghĩ:

```text
class định nghĩa trạng thái/hành vi
→ constructor tạo trạng thái hợp lệ
→ access modifier kiểm soát ranh giới
→ initialization order quyết định thời điểm trạng thái sẵn sàng
→ reference copy tạo aliasing
→ mutability yêu cầu quan hệ sở hữu rõ
→ immutability/defensive copy giúp sharing an toàn hơn
```
