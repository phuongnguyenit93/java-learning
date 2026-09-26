# Copy Semantics

Trong Java, câu “copy object” rất dễ gây hiểu nhầm vì có ít nhất ba việc khác nhau: **copy reference, shallow copy và deep copy**.

## <a id="reference-copy">Copy Reference</a>

```java
Profile a = new Profile();
Profile b = a;
```

Đây không tạo object mới. Chỉ reference value được copy, nên `a` và `b` cùng trỏ tới một object.

Mutation qua một reference sẽ được quan sát qua reference còn lại.

## <a id="shallow-copy">Shallow Copy</a>

Shallow copy tạo outer object mới nhưng copy các field value như hiện có.

Với reference field, reference được copy chứ object con không tự động được nhân bản.

```text
original ------> mutable Address
copy     ------> cùng Address
```

Vì vậy outer identity khác nhưng nested mutable trạng thái vẫn có thể bị share.

## <a id="deep-copy">Deep Copy</a>

Deep copy cố gắng tạo object graph độc lập cho các phần mutable cần tách quan hệ sở hữu.

Không có một “deep copy” universal cho mọi class. Ta phải quyết định:

- object con nào cần copy;
- object immutable nào có thể share;
- cycle/reference identity được xử lý thế nào.

Deep copy vì vậy là một quyết định domain/quan hệ sở hữu, không chỉ là recursive clone máy móc.

## <a id="copy-strategies">Các chiến lược Copy</a>

Các lựa chọn thường rõ ràng hơn `clone()`:

- copy constructor;
- static factory;
- builder từ object cũ;
- explicit mapper;
- serialization-based copy chỉ khi hợp đồng thật sự phù hợp.

Strategy tốt làm rõ field nào được share, field nào được copy và invariant nào được kiểm tra lại.

chương tiếp theo tập trung vào hậu quả khi **nhiều reference cùng giữ mutable object**: aliasing.
