# Immutability và Defensive Copy

Nếu trạng thái có thể quan sát của object không thay đổi sau khi khởi tạo, việc dùng chung object trở nên dễ suy luận hơn rất nhiều. Nhưng chỉ có field `final` là chưa đủ; ta phải xem cả toàn bộ graph object có thể truy cập và các reference bị đưa ra ngoài.

## <a id="immutable-object-design">Thiết kế Object bất biến</a>

Một object bất biến (immutable object) thường:

- thiết lập toàn bộ trạng thái trong constructor/factory;
- không cung cấp mutator làm thay đổi trạng thái;
- dùng `final` cho field khi phù hợp;
- không để object nội bộ có thể thay đổi bị lộ reference;
- nếu subclass có thể phá tính bất biến, hạn chế khả năng kế thừa một cách phù hợp.

```java
final class BankAccountSnapshot {
    private final String id;
    private final List<String> tags;

    BankAccountSnapshot(String id, List<String> tags) {
        this.id = id;
        this.tags = new ArrayList<>(tags);
    }

    List<String> tags() {
        return List.copyOf(tags);
    }
}
```

Biến thể bất biến của `BankAccount` này thiết lập toàn bộ trạng thái trong constructor và không trả trực tiếp list nội bộ có thể thay đổi ra ngoài.

Tính bất biến giúp việc dùng chung, caching, hashing và suy luận trong mã đồng thời đơn giản hơn.

## <a id="defensive-copy-input">Defensive Copy đầu vào</a>

Nếu constructor nhận một object có thể thay đổi và giữ trực tiếp reference, bên gọi vẫn có thể thay đổi trạng thái nội bộ sau khi quá trình khởi tạo hoàn tất.

```java
this.tags = new ArrayList<>(tags);
```

Defensive copy ở đầu vào tách quyền sở hữu của snapshot khỏi collection do bên gọi giữ:

```java
List<String> source = new ArrayList<>();
BankAccountSnapshot snapshot = new BankAccountSnapshot("A-01", source);

source.add("VIP");
System.out.println(snapshot.tags()); // []
```

Với đầu vào bất biến như `String`, không cần sao chép chỉ để “cho chắc”.

## <a id="defensive-copy-output">Defensive Copy đầu ra</a>

Getter không nên trả trực tiếp reference tới trạng thái nội bộ có thể thay đổi nếu bên gọi không được phép sửa trạng thái đó.

Có thể dùng:

- immutable/unmodifiable view phù hợp;
- `List.copyOf(...)`;
- copy mới tùy hợp đồng.

Trong `BankAccountSnapshot` ở trên, `List.copyOf(tags)` trả về kết quả không cho phép sửa thay vì làm lộ list nội bộ:

```java
snapshot.tags().add("VIP"); // UnsupportedOperationException
```

Phân biệt unmodifiable view với deep immutability: view có thể cấm bên gọi sửa dữ liệu qua view nhưng dữ liệu bên dưới vẫn có thể thay đổi ở nơi khác.

## <a id="deep-immutability">Shallow và Deep Immutability</a>

Object có `final List<Address> addresses` chưa chắc bất biến sâu nếu `Address` hoặc list phía sau vẫn có thể thay đổi. `final` cố định reference của field; nó không tự động “đóng băng” toàn bộ object graph phía sau.

```text
shallow immutability
→ reference/trạng thái bên ngoài không được gán lại

deep immutability
→ toàn bộ trạng thái có thể truy cập giữ hợp đồng bất biến phù hợp
```

Không phải miền nghiệp vụ nào cũng cần deep immutability, nhưng quan hệ sở hữu phải rõ để bên gọi biết dữ liệu có thể thay đổi từ đâu.

Kết thúc module, hãy giữ một chuỗi suy nghĩ:

```text
class định nghĩa trạng thái/hành vi
→ constructor tạo trạng thái hợp lệ
→ access modifier kiểm soát ranh giới
→ initialization order quyết định thời điểm trạng thái sẵn sàng
→ reference copy tạo aliasing
→ mutability yêu cầu quan hệ sở hữu rõ
→ immutability/defensive copy giúp việc dùng chung an toàn hơn
```
