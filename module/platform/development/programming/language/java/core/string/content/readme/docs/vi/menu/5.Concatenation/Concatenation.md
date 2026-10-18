# Nối chuỗi String

Toán tử `+` làm việc rất tiện với String, nhưng cần phân biệt **ngữ nghĩa của ngôn ngữ Java** với **chi tiết triển khai của trình biên dịch/JVM**.

## <a id="concat-semantics">Nối String bằng +</a>

Khi một toán hạng là String trong ngữ cảnh nối chuỗi, Java tạo ra một kết quả String biểu diễn nội dung đã nối.

```java
String message = "Hello " + name;
```

String cũ không bị sửa tại chỗ; kết quả là một giá trị String mới về mặt ngữ nghĩa.

### Chuyển đổi sang String trong biểu thức

Phép nối chuỗi có thể nhận giá trị nguyên thủy hoặc giá trị tham chiếu:

```java
String message = "count=" + 42;       // "count=42"
String flag = "active=" + true;       // "active=true"
Object value = null;
String text = "value=" + value;       // "value=null"
```

Java thực hiện chuyển đổi sang String cho toán hạng phù hợp; mã ứng dụng không cần gọi `toString()` thủ công cho mọi phần.

Nhưng điều này cũng có thể che lỗi: nếu `null` là trạng thái nghiệp vụ không hợp lệ, hãy kiểm tra trạng thái thay vì để phép nối chuỗi biến nó thành văn bản `"null"` rồi tiếp tục xử lý.

Khi biểu thức trộn số và String, thứ tự đánh giá toán hạng ảnh hưởng kết quả:

Java đánh giá toán hạng từ trái sang phải. Đây là lý do phép cộng số có thể xảy ra trước khi biểu thức chuyển sang ngữ cảnh nối String.

Tránh nhét tác dụng phụ phức tạp vào cùng một biểu thức; tách bước giúp suy luận và gỡ lỗi rõ hơn.

```java
System.out.println(1 + 2 + "x"); // "3x"
System.out.println("x" + 1 + 2); // "x12"
```

## <a id="compile-time-concat">Nối chuỗi tại thời điểm biên dịch</a>

Nếu toàn bộ biểu thức nối là **biểu thức hằng (constant expression)**, Java đánh giá nó tại thời điểm biên dịch:

```java
String value = "ja" + "va";
```

kết quả là một hằng String và được intern, nên trong ví dụ này nó dùng cùng tham chiếu chuẩn hóa với literal `"java"`.

Đây là lý do `==` trong những ví dụ **biểu thức hằng** như trên có thể cho `true` theo quy ước của Java; không được suy rộng điều đó sang phép nối có giá trị chỉ biết khi chương trình chạy.

Biến hằng cũng có thể tham gia:

```java
final String left = "ja";
String a = left + "va";
String b = "java";

System.out.println(a == b); // true
```

### `final` chưa chắc là hằng số tại thời điểm biên dịch

Một biến `final` chỉ tham gia biểu thức hằng khi nó thực sự là **biến hằng (constant variable)**:

```java
final String prefix = "ja";
String a = prefix + "va";
String b = "java";

System.out.println(a == b); // true
```

Nhưng giá trị chỉ biết khi chương trình chạy thì khác:

```java
final String prefix = args.length > 0 ? args[0] : "ja";
String a = prefix + "va";
String b = "java";

System.out.println(a.equals(b)); // câu hỏi về nội dung
```

`final` nghĩa là tham chiếu không được gán lại; nó không tự động biến mọi biểu thức khi chạy thành hằng số tại thời điểm biên dịch. Điểm quyết định là **biểu thức hằng tại thời điểm biên dịch**, không chỉ từ khóa `final`.

## <a id="runtime-concat">Nối chuỗi khi chương trình chạy</a>

Với giá trị chỉ biết khi chạy, trình biên dịch/JVM có thể dùng các chiến lược khác nhau tùy phiên bản Java, ví dụ hạ xuống cơ chế giống `StringBuilder` hoặc dùng `invokedynamic` cho phép nối chuỗi.

Mã ứng dụng nên phụ thuộc vào **ngữ nghĩa của ngôn ngữ**, không phụ thuộc vào việc bytecode hiện tại dùng đúng lớp hỗ trợ nào.

Vì vậy không nên học quy tắc kiểu:

```text
mọi dấu + với String
→ trình biên dịch luôn tạo StringBuilder
```

Đó không phải hợp đồng của ngôn ngữ. Java hiện đại có thể dùng chiến lược nối khác, và chi tiết triển khai có quyền thay đổi.

## <a id="loop-concat-cost">Chi phí khi nối lặp lại</a>

Trong vòng lặp lớn:

```java
String[] parts = {"java", "-", "core"};
String result = "";
for (String part : parts) {
    result += part;
}
```

mỗi bước có thể tạo thêm String trung gian và chi phí sao chép giá trị.

Mô hình tư duy:

```text
""
 + part1 → kết quả 1
 + part2 → kết quả 2 lớn hơn
 + part3 → kết quả 3 lớn hơn
 ...
```

Khi văn bản tăng dần qua nhiều vòng lặp, cùng một phần tiền tố có thể bị sao chép nhiều lần. Với khối lượng xử lý đủ lớn, tổng chi phí sao chép tăng đáng kể so với dùng một bộ đệm có thể thay đổi.

Nếu đang xây một chuỗi tăng dần qua nhiều bước, `StringBuilder` thể hiện mục đích rõ hơn và thường hiệu quả hơn.

Điều đó **không có nghĩa mọi dấu + đều xấu**:

```java
String fullName = firstName + " " + lastName;
```

Một biểu thức nhỏ và rõ ràng thường nên giữ đơn giản. `StringBuilder` có giá trị nhất khi quá trình xây chuỗi là **tăng dần qua nhiều bước**, đặc biệt trong vòng lặp hoặc nhánh xử lý phức tạp.

Chương tiếp theo đi vào chính bộ đệm có thể thay đổi đó.
