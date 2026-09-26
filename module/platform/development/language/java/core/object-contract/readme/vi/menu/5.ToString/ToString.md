# toString

Không phải hợp đồng nào của object cũng phục vụ collection. `toString` chủ yếu phục vụ **con người và công cụ chẩn đoán**: log, debugger, test lỗi message hoặc text hiển thị tạm thời.

## <a id="tostring-purpose">Mục đích của toString</a>

`Object.toString()` mặc định cho thông tin gần với class + identity. Domain class thường override để hiển thị trạng thái hữu ích hơn.

Ví dụ:

```java
@Override
public String toString() {
    return "UserId[value=" + value + "]";
}
```

Mục tiêu chính là giúp người đọc hiểu object khi debug hoặc log, không phải tạo một serialization format bền vững.

## <a id="tostring-design">Thiết kế Representation hữu ích</a>

Một `toString` tốt thường:

- ngắn gọn;
- có tính ổn định tương đối;
- hiển thị các field quan trọng;
- không thực hiện I/O bất ngờ;
- không chạy computation đắt đỏ;
- hạn chế throw exception trong lúc logging/debugging.

Nếu format cần machine-readable và có compatibility hợp đồng, hãy dùng serializer chuyên dụng thay vì dựa vào `toString`.

## <a id="tostring-sensitive-data">Dữ liệu nhạy cảm và Logging</a>

Không nên đưa password, token, secret, full payment data hoặc thông tin nhạy cảm vào `toString` chỉ vì chúng là field của object.

`toString` có thể bị gọi ngầm bởi:

- logging;
- string concatenation;
- IDE/debugger;
- exception message hoặc assertion.

Vì vậy bề mặt rò rỉ dữ liệu rộng hơn những nơi ta gọi explicit.

Sau cách object tự mô tả, hai chương cuối chuyển sang **cách object được sắp xếp**.
