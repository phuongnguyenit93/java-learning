# Biểu diễn văn bản với toString

Không phải quy ước nào của Object cũng phục vụ cấu trúc dữ liệu. `toString` chủ yếu phục vụ **con người và công cụ chẩn đoán**: log, trình gỡ lỗi, thông báo lỗi của phép kiểm tra (`assertion`) hoặc kiểm thử, và văn bản hiển thị tạm thời.

Một `toString` tốt giúp ta trả lời nhanh câu hỏi: **đối tượng này đang mang trạng thái gì đáng chú ý?** Nó không nên biến thành giao thức tuần tự hóa dữ liệu, ranh giới bảo mật hoặc nơi thực hiện xử lý nặng.

## <a id="tostring-purpose">Mục đích của toString</a>

Mọi lớp đều kế thừa `toString()` từ `Object` nếu không ghi đè.

### `Object.toString()` MẶC ĐỊNH

Biểu diễn mặc định có dạng gần như:

```text
fully.qualified.ClassName@hexHash
```

Ví dụ:

```text
com.example.UserId@5e2de80c
```

Phần sau dấu `@` đến từ `hashCode()` được biểu diễn ở hệ 16; nó **không phải mã định danh duy nhất của đối tượng** và có thể bị trùng. Biểu diễn mặc định đôi khi hữu ích như một dấu hiệu chẩn đoán thô, nhưng thường không cho ta thấy trạng thái nghiệp vụ quan trọng.

Biểu diễn mặc định của `Object.toString()` không phải một định dạng lưu trữ ổn định để có thể dựa vào giữa các lần chạy JVM. Một lớp hoàn toàn có thể ghi đè `toString()` và chủ đích công bố định dạng ổn định, nhưng đoạn mã sử dụng chỉ nên dựa vào sự ổn định đó khi chính lớp đã nêu rõ cam kết này.

Vì vậy lớp nghiệp vụ thường ghi đè:

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

### VÌ SAO `toString` LÀ MỘT QUY ƯỚC?

JDK không bắt buộc một định dạng riêng cố định cho mọi lớp. Ở mức `Object`, `toString` được dùng để trả về biểu diễn văn bản của đối tượng và tài liệu Java khuyến nghị kết quả nên ngắn gọn, có thông tin hữu ích và dễ đọc với con người.

Ngoài kỳ vọng API đó, mã nguồn thực tế thường áp dụng thêm các **nguyên tắc thiết kế** vì `toString` có thể được gọi trong luồng log hoặc gỡ lỗi. Vì vậy một `toString` thực dụng nên:

- phương thức trả về mô tả hữu ích;
- không có tác dụng phụ bất ngờ;
- đủ nhẹ và đủ an toàn để dùng khi gỡ lỗi hoặc ghi log;
- không làm lộ dữ liệu mà đối tượng không nên phơi bày.

Ba ý cuối không phải các luật ngôn ngữ riêng mà Java cưỡng chế. Chúng là nguyên tắc kỹ thuật giúp `toString` an toàn và hữu ích trong môi trường thực tế.

### `toString` CÓ THỂ ĐƯỢC GỌI NGẦM

Bạn không phải lúc nào cũng viết `.toString()` trực tiếp:

```java
System.out.println(userId);

String message = "id=" + userId;

logger.info("processing {}", userId);
```

Các API khác nhau có cách gọi chi tiết riêng, nhưng về mặt mô hình tư duy, biểu diễn của đối tượng có thể xuất hiện ở nhiều nơi ngoài một lời gọi trực tiếp.

## <a id="tostring-design">Thiết kế biểu diễn hữu ích</a>

Một `toString` tốt thường:

- ngắn gọn nhưng đủ thông tin;
- hiển thị các trường dữ liệu quan trọng cho chẩn đoán;
- có kết quả xác định hoặc ít nhất đủ ổn định để con người đọc;
- không thực hiện I/O mạng, cơ sở dữ liệu hoặc tệp;
- không thực hiện tính toán tốn kém;
- hạn chế ném ngoại lệ trong lúc ghi log hoặc gỡ lỗi.

### BIỂU DIỄN VĂN BẢN KHÔNG PHẢI TUẦN TỰ HÓA DỮ LIỆU

Hai mục tiêu khác nhau:

```text
toString
→ biểu diễn chẩn đoán dành cho con người

JSON / protobuf / bộ tuần tự hóa chuyên dụng
→ định dạng dành cho máy với lược đồ (schema) và chính sách tương thích riêng
```

Không nên phân tích ngược `toString()` để tái tạo đối tượng trừ khi lớp đó **công bố rõ ràng** một quy ước như vậy. Với lớp nghiệp vụ thông thường, định dạng `toString` có thể thay đổi khi nhu cầu gỡ lỗi thay đổi.

### GIỮ KẾT QUẢ HỮU ÍCH, KHÔNG IN TOÀN BỘ MỌI THỨ

Ví dụ một `Book` có hàng chục trường dữ liệu nhưng log thường chỉ cần:

```java
@Override
public String toString() {
    return "Book[id=" + id + ", title=" + title + "]";
}
```

In toàn bộ đồ thị đối tượng lớn có thể làm log nhiễu, chậm và khó đọc.

### CẨN THẬN VỚI ĐỒ THỊ ĐỐI TƯỢNG ĐỆ QUY

Nếu hai đối tượng trỏ lẫn nhau và cả hai `toString` đều in toàn bộ đối tượng còn lại, biểu diễn có thể đệ quy vô hạn hoặc tạo kết quả rất lớn.

```text
Parent.toString()
→ in Child
   → Child.toString()
      → lại in Parent
         → ...
```

Vì vậy nên chọn trường dữ liệu cần biểu diễn có chủ ý thay vì tự động in toàn bộ đồ thị đối tượng.

### RECORD VÀ BIỂU DIỄN ĐƯỢC TẠO TỰ ĐỘNG

Java record cung cấp `toString` tự động dựa trên các thành phần (`component`). Điều đó tiện lợi cho kiểu dữ liệu chuyên chở giá trị, nhưng vẫn cần xem xét ranh giới bảo mật và ghi log. “Được sinh tự động” không đồng nghĩa “an toàn để log ở mọi nơi”.

## <a id="tostring-sensitive-data">Dữ liệu nhạy cảm và ghi log</a>

Không nên đưa mật khẩu, token, bí mật, đầy đủ dữ liệu thanh toán hoặc thông tin nhạy cảm vào `toString` chỉ vì chúng là trường dữ liệu của đối tượng.

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

- ghi log;
- nối chuỗi;
- IDE/trình gỡ lỗi;
- lỗi của phép kiểm tra (`assertion`) hoặc kiểm thử;
- thông báo ngoại lệ;
- biểu diễn gián tiếp của cấu trúc dữ liệu hoặc đối tượng.

Một trường dữ liệu nhạy cảm lọt vào `toString` có thể bị ghi ra log ở môi trường vận hành thực tế dù lập trình viên không chủ động gọi nó tại điểm đó.

### CHE DỮ LIỆU (REDACTION) VÀ BIỂU DIỄN AN TOÀN

Nếu cần hiển thị thông tin nhận diện, hãy cân nhắc biểu diễn đã che hoặc lược bỏ dữ liệu nhạy cảm:

```java
@Override
public String toString() {
    return "PaymentCard[last4=" + last4 + "]";
}
```

Chính sách bảo mật cụ thể thuộc mô-đun bảo mật chuyên sâu hơn, nhưng ranh giới ở đây rất rõ: **`toString` là bề mặt chẩn đoán, không phải nơi mặc định phơi bày toàn bộ trạng thái**.

Sau cách đối tượng tự mô tả, hai chương cuối chuyển sang một nhóm quy ước khác: **thứ tự sắp xếp** — đối tượng nào đứng trước đối tượng nào, và ai sở hữu chính sách đó.
