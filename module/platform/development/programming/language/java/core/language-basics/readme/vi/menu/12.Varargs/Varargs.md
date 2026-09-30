# `varargs` và mảng trong lời gọi phương thức

`varargs` cho phép bên gọi truyền số đối số thay đổi mà không phải tự tạo mảng trong mã nguồn. Nhưng bên trong thân phương thức, `varargs` vẫn có mô hình dựa trên mảng.

## <a id="varargs-array-model">`varargs` dựa trên mảng</a>

```java
void log(String... messages) {
    System.out.println(messages.length);
}
```

Trong thân phương thức, `messages` có kiểu `String[]`.

Bên gọi có thể viết:

```java
log("a", "b");
```

hoặc truyền một mảng tương thích.

Tham số `varargs` phải là tham số cuối cùng của phương thức.

### Bên gọi được chuyển thành mảng như thế nào?

```java
log();
log("a");
log("a", "b");
```

Trong mô hình tư duy của thân phương thức, ba lời gọi trên tương ứng với việc nhận một `String[]` có độ dài 0, 1 và 2. Bên gọi cũng có thể truyền mảng có sẵn:

```java
String[] messages = {"a", "b"};
log(messages);
```

`varargs` chủ yếu là tiện ích cú pháp ở vị trí gọi; khi thiết kế API vẫn cần hiểu ngữ nghĩa mảng, việc cấp phát và ranh giới thay đổi dữ liệu.

### `null` là trường hợp dễ nhầm

```java
log((String[]) null);
```

Phương thức nhận `messages == null`, khác hoàn toàn với `log()` vốn nhận mảng rỗng. API công khai nên quyết định rõ có chấp nhận mảng `null` hay không.

## <a id="varargs-overload">`varargs` và nạp chồng</a>

`varargs` thường được xét như phương án cuối sau các ứng viên có số lượng tham số cố định phù hợp.

Do đó thêm một overload `varargs` có thể ảnh hưởng tập phương thức nạp chồng theo cách không hiển nhiên, đặc biệt khi kết hợp boxing, mở rộng kiểu hoặc `null`.

API công khai có nhiều overload + `varargs` nên được kiểm thử với các lời gọi đại diện để tránh sự mơ hồ ngoài ý muốn.

Ví dụ phương thức có số lượng tham số cố định thường được ưu tiên trước `varargs`:

```java
void send(String value) { }
void send(String... values) { }

send("one"); // chọn overload số lượng tham số cố định
```

Nhưng `null` có thể làm tập overload khó đọc vì `null` tương thích với nhiều kiểu tham chiếu/mảng. Đừng thêm overload chỉ để “tiện” nếu nó làm ngữ nghĩa tại vị trí gọi trở nên mơ hồ.

## <a id="varargs-generics-warning">Mở rộng: generic `varargs` và heap pollution</a>

> Phần này là ranh giới nâng cao với mô-đun Generics. Người mới có thể ghi nhớ rủi ro và quay lại sau khi đã học type erasure và reifiable type.

`varargs` được triển khai qua mảng, còn tham số kiểu generic chịu type erasure. Kết hợp hai cơ chế có thể tạo cảnh báo **heap pollution** với non-reifiable type:

```java
static <T> void addAll(List<T>... lists) { }
```

`@SafeVarargs` chỉ nên dùng khi cách triển khai thực sự không thực hiện thao tác không an toàn trên mảng `varargs`; annotation là một lời cam kết, không phải nút tắt cảnh báo tùy ý.

### Vì sao generic `varargs` nguy hiểm?

Mảng biết kiểu phần tử ở thời điểm chạy, còn tham số generic thường bị type erasure. Với `List<String>...`, thời điểm chạy không thể có một mảng thực sự biết đầy đủ tham số hóa `List<String>` theo cách kiểu trong mã nguồn thể hiện.

Ghi dữ liệu không an toàn qua một mảng alias có thể làm trình biên dịch tin một phần tử là `List<String>` trong khi cấu trúc ở thời điểm chạy chứa tham số hóa khác. Đó là một dạng **heap pollution**.

Quy tắc thực tế:

- ưu tiên API Collection khi số lượng phần tử biến đổi thực sự là một cấu trúc dữ liệu;
- dùng `varargs` khi sự tiện dụng tại vị trí gọi có giá trị rõ ràng;
- với generic `varargs`, không ghi dữ liệu không an toàn vào mảng và không đưa mảng ra cho mã có thể thay đổi sai kiểu;
- chỉ dùng `@SafeVarargs` khi bạn có thể giải thích vì sao cách triển khai thực sự an toàn.

Chương tiếp theo giải thích chính xác **thứ gì được sao chép khi đối số đi vào phương thức**.
