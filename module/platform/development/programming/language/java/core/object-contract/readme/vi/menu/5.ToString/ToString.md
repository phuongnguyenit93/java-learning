# toString

Không phải object contract nào cũng phục vụ collection. `toString` chủ yếu phục vụ **con người và công cụ chẩn đoán**: log, debugger, assertion failure, test failure message hoặc text hiển thị tạm thời.

Một `toString` tốt giúp ta trả lời nhanh câu hỏi: **object này đang mang trạng thái gì đáng chú ý?** Nó không nên biến thành serialization protocol, security boundary hoặc nơi thực hiện logic nặng.

## <a id="tostring-purpose">Mục đích của toString</a>

Mọi class đều kế thừa `toString()` từ `Object` nếu không override.

### `Object.toString()` MẶC ĐỊNH

Representation mặc định có dạng gần như:

```text
fully.qualified.ClassName@hexHash
```

Ví dụ:

```text
com.example.UserId@5e2de80c
```

Suffix sau `@` đến từ `hashCode()` được biểu diễn ở hệ 16; nó **không phải unique object ID** và có thể collision. Representation mặc định đôi khi hữu ích như một token chẩn đoán thô, nhưng thường không cho ta thấy domain state quan trọng.

Java cũng không cam kết output `toString()` phải ổn định giữa các lần chạy JVM. Đây là thêm một lý do không nên biến nó thành persistence/serialization format.

Vì vậy domain class thường override:

```java
@Override
public String toString() {
    return "UserId[value=" + value + "]";
}
```

Kết quả:

```text
UserId[value=U-100]
```

### VÌ SAO `toString` LÀ MỘT HỢP ĐỒNG?

Không phải vì compiler ép một format cụ thể, mà vì rất nhiều công cụ và code gọi dựa vào kỳ vọng rằng:

- method trả về mô tả hữu ích;
- không có side effect bất ngờ;
- đủ rẻ và đủ an toàn để dùng khi debug/log;
- không làm lộ dữ liệu mà object không nên phơi bày.

### `toString` CÓ THỂ ĐƯỢC GỌI NGẦM

Bạn không phải lúc nào cũng viết `.toString()` trực tiếp:

```java
System.out.println(userId);

String message = "id=" + userId;

logger.info("processing {}", userId);
```

Các API khác nhau có chi tiết invocation riêng, nhưng về mặt mental model, object representation có thể xuất hiện ở nhiều nơi ngoài một lời gọi explicit.

## <a id="tostring-design">Thiết kế Representation hữu ích</a>

Một `toString` tốt thường:

- ngắn gọn nhưng đủ thông tin;
- hiển thị các field quan trọng cho chẩn đoán;
- deterministic hoặc ít nhất ổn định đủ để con người đọc;
- không thực hiện network/database/file I/O;
- không chạy computation đắt đỏ;
- hạn chế throw exception trong lúc logging/debugging.

### BIỂU DIỄN (REPRESENTATION) KHÔNG PHẢI SERIALIZATION

Hai mục tiêu khác nhau:

```text
toString
→ human-readable diagnostic representation

JSON / protobuf / custom serializer
→ machine-readable contract có schema/compatibility policy
```

Không nên parse ngược `toString()` để tái tạo object trừ khi class đó **explicitly** công bố một contract như vậy. Với domain class thông thường, format `toString` có thể thay đổi khi nhu cầu debug thay đổi.

### GIỮ OUTPUT HỮU ÍCH, KHÔNG IN TOÀN BỘ MỌI THỨ

Ví dụ một `Book` có hàng chục field nhưng log thường chỉ cần:

```java
@Override
public String toString() {
    return "Book[id=" + id + ", title=" + title + "]";
}
```

Dump toàn bộ graph lớn có thể làm log nhiễu, chậm và khó đọc.

### CẨN THẬN VỚI OBJECT GRAPH ĐỆ QUY

Nếu hai object trỏ lẫn nhau và cả hai `toString` đều in toàn bộ object còn lại, representation có thể đệ quy vô hạn hoặc tạo output rất lớn.

```text
Parent.toString()
→ in Child
   → Child.toString()
      → lại in Parent
         → ...
```

Vì vậy nên chọn field representation có chủ ý thay vì auto-dump toàn graph.

### RECORD VÀ BIỂU DIỄN ĐƯỢC TẠO TỰ ĐỘNG

Java record cung cấp `toString` tự động dựa trên component. Điều đó tiện lợi cho value carrier, nhưng vẫn cần xem xét security/logging boundary. “Được generate” không đồng nghĩa “an toàn để log mọi nơi”.

## <a id="tostring-sensitive-data">Dữ liệu nhạy cảm và Logging</a>

Không nên đưa password, token, secret, full payment data hoặc thông tin nhạy cảm vào `toString` chỉ vì chúng là field của object.

Ví dụ không nên:

```java
@Override
public String toString() {
    return "LoginRequest[user=" + user
            + ", password=" + password + "]";
}
```

### TẠI SAO RỦI RO LỚN?

`toString` có thể xuất hiện qua:

- logging;
- string concatenation;
- IDE/debugger;
- assertion/test failure;
- exception message;
- collection/object representation gián tiếp.

Một field nhạy cảm lọt vào `toString` có thể bị ghi ra log production mà developer không chủ động gọi nó ở điểm đó.

### CHE DỮ LIỆU (REDACTION) VÀ BIỂU DIỄN AN TOÀN

Nếu cần hiển thị thông tin nhận diện, hãy cân nhắc masked/redacted representation:

```java
@Override
public String toString() {
    return "PaymentCard[last4=" + last4 + "]";
}
```

Security policy cụ thể thuộc module security chuyên sâu hơn, nhưng boundary ở đây rất rõ: **`toString` là diagnostic surface, không phải nơi mặc định expose toàn bộ state**.

Sau cách object tự mô tả, hai chapter cuối chuyển sang một contract khác: **ordering** — object nào đứng trước object nào, và ai sở hữu policy đó.
