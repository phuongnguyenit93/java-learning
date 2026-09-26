# equals

Sau khi phân biệt identity và logical equality, câu hỏi tiếp theo là: **nếu một class muốn tự định nghĩa “hai object được xem là bằng nhau”, method `equals` phải tuân những quy tắc nào?**

## <a id="equals-contract">Hợp đồng equals</a>

Một cách triển khai `equals` đúng phải giữ các tính chất cơ bản:

```text
reflexive
x.equals(x) luôn true

symmetric
x.equals(y) và y.equals(x) phải cho cùng kết quả

transitive
nếu x == y về mặt equality và y == z
thì x cũng phải equal z

consistent
kết quả ổn định khi trạng thái liên quan không đổi

null
x.equals(null) phải false
```

Compiler không ép bạn giữ các luật này, nhưng collection và library mã mặc định tin rằng object của bạn giữ hợp đồng.

## <a id="equals-implementation">Cách triển khai equals</a>

Một cách triển khai kiểu value object thường đi theo flow:

```text
1. kiểm tra cùng identity
2. kiểm tra type compatibility
3. so sánh các field định nghĩa equality
```

Ví dụ:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof UserId that)) return false;
    return Objects.equals(value, that.value);
}
```

Việc chọn `getClass()` hay `instanceof` ảnh hưởng tới ngữ nghĩa khi inheritance tham gia. Quan trọng nhất là `equals` và `hashCode` phải dựa trên cùng tập dữ liệu định nghĩa equality.

## <a id="equals-inheritance-risk">Equality và Inheritance</a>

Inheritance làm equality khó hơn khi class con thêm trạng thái mới có ý nghĩa đối với equality.

Ví dụ class cha chỉ so `id`, còn class con muốn so thêm `region`. Khi đó rất dễ xuất hiện tình huống:

```text
parent.equals(child) == true
child.equals(parent) == false
```

và symmetry bị phá.

Với value ngữ nghĩa phức tạp, composition, closed hierarchy hoặc class-based equality thường an toàn hơn việc mở rộng một concrete value class tùy ý.

chương tiếp theo trả lời câu hỏi: **nếu equals đã nói hai object bằng nhau, hash-based collection cần thêm thông tin gì để tìm chúng nhanh?**
