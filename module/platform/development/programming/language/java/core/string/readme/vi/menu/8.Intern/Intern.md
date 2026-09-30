# String.intern

String Pool có thể dùng chung định danh tham chiếu cho literal và một số String đã được chuẩn hóa. `String.intern()` là API cho phép yêu cầu một **tham chiếu chuẩn trong pool** tương ứng với cùng nội dung văn bản.

## <a id="intern-semantics">String.intern làm gì?</a>

Khi gọi:

```java
String canonical = value.intern();
```

JVM trả về tham chiếu đại diện trong String Pool cho chuỗi có cùng nội dung.

Mô hình tư duy:

```text
value.intern()
        │
        ├── pool đã có String equals(value)
        │      → trả tham chiếu chuẩn hóa đã có trong pool
        │
        └── chưa có
               → value trở thành đại diện chuẩn hóa
               → trả tham chiếu chuẩn hóa
```

Điều này không thay đổi nội dung của `value` và cũng không làm `String` trở nên có thể thay đổi. `intern()` chỉ liên quan tới **định danh tham chiếu và chuẩn hóa**, không thay đổi ngữ nghĩa so sánh nội dung.

### VÌ SAO intern() tồn tại?

`intern()` cho phép mã ứng dụng yêu cầu một tham chiếu chuẩn cho các String có cùng nội dung. Nó có thể hữu ích trong một số khối lượng xử lý có tập giá trị lặp lại nhiều và số lượng giá trị khác nhau được kiểm soát.

Nếu mục tiêu chỉ là “hai chuỗi có cùng nội dung?”, `equals` đã là API đúng; không cần gọi `intern()` trước khi so sánh.

## <a id="intern-identity">Tham chiếu chuẩn hóa</a>

Ví dụ:

```java
String a = new String("java");
String b = a.intern();
String c = "java";

System.out.println(b == c); // true
```

Sau `intern()`, `b` dùng tham chiếu chuẩn trong pool cho nội dung `"java"`.

Nhưng logic nghiệp vụ vẫn nên dùng `equals` khi câu hỏi là **nội dung có bằng nhau không**. Không nên chuyển mọi phép so sánh sang định danh tham chiếu chỉ vì có `intern()`.

`intern()` nối trực tiếp với cơ chế literal trong String Pool:

```java
String runtime = new String("java");
String canonical = runtime.intern();
String literal = "java";

System.out.println(canonical == literal); // true
```

Đây là thí nghiệm hợp lệ về định danh tham chiếu vì chuẩn hóa chính là khái niệm đang được quan sát.

## <a id="intern-tradeoffs">Đánh đổi khi dùng intern()</a>

`intern()` có thể giảm số đối tượng tham chiếu khác nhau cho một tập String lặp lại nhiều, nhưng không phải tối ưu mặc định cho mọi ứng dụng.

Chi phí/cân nhắc gồm:

- chi phí tra cứu và chuẩn hóa;
- tăng quy mô và chi phí quản lý của bảng String khi intern nhiều giá trị;
- tăng áp lực bộ nhớ/tra cứu nếu dữ liệu có số lượng giá trị khác nhau rất lớn;
- làm mã phụ thuộc không cần thiết vào định danh tham chiếu.

### Số lượng giá trị khác nhau lớn và đầu vào không tin cậy

Nếu các giá trị được intern gần như đều duy nhất:

```text
user-000001
user-000002
user-000003
...
```

việc chuẩn hóa có thể không đem lại lợi ích loại bỏ trùng lặp đáng kể nhưng vẫn thêm chi phí tra cứu và áp lực lên cấu trúc quản lý String đã intern.

Đặc biệt không nên intern dữ liệu do người dùng kiểm soát có số lượng giá trị khác nhau rất lớn chỉ vì “tiết kiệm bộ nhớ”.

### Không dựa vào quan niệm cũ về JVM

Các câu kiểu “interned strings luôn nằm ở PermGen” hoặc “đã intern thì chắc chắn bị giữ mãi đến khi JVM kết thúc” đều không phải hợp đồng của ngôn ngữ Java. Vị trí lưu, cấu trúc bảng và hành vi thu gom là chi tiết triển khai của JVM.

Ứng dụng nên cân nhắc theo:

```text
định danh tham chiếu chuẩn hóa
chi phí tra cứu
đặc điểm bảng String và bộ nhớ
số lượng giá trị khác nhau trong khối lượng xử lý
```

Chỉ intern khi khối lượng xử lý và đo đạc thực tế cho thấy lợi ích rõ ràng, hoặc hợp đồng thực sự cần một tham chiếu chuẩn hóa.

Chương tiếp theo rời khỏi bài toán định danh tham chiếu để xây mô hình Unicode bên trong `String`: **`char`, điểm mã Unicode và ký tự người dùng nhìn thấy khác nhau thế nào?** Sau đó mô-đun mới đi qua ranh giới văn bản ↔ byte bằng Charset.
