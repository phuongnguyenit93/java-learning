# String Pool

Vì String bất biến, JVM có thể an toàn dùng chung một số String có cùng nội dung thay vì luôn tạo đối tượng mới. Đây là ý tưởng nền tảng của **String Pool**.

## <a id="string-pool-model">String Pool là gì?</a>

String Pool lưu các tham chiếu chuẩn hóa cho một số String, đặc biệt là String literal và giá trị được `intern()`.

### VÌ SAO — vì sao String Pool tồn tại?

String xuất hiện cực nhiều trong mã nguồn: tên lớp, khóa, thông báo, đường dẫn, token, hằng số... Nếu literal giống nhau luôn tạo đối tượng mới, JVM sẽ giữ nhiều đối tượng có cùng nội dung mà không mang thêm ý nghĩa.

Vì String bất biến, Java có thể chuẩn hóa một số giá trị về cùng tham chiếu đại diện:

```text
những giá trị String bất biến có cùng nội dung
        ↓
chia sẻ một định danh tham chiếu chuẩn hóa an toàn
        ↓
String pool
```

```java
String a = "java";
String b = "java";
```

Vì hai literal có cùng nội dung được intern, `a` và `b` tham chiếu cùng một đối tượng String chuẩn trong pool.

Với String literal, đây không chỉ là một tối ưu ngẫu nhiên: literal và hằng String được intern theo hợp đồng của Java. Việc JVM lưu pool ở đâu trong bộ nhớ là chi tiết triển khai; người học không nên gắn mô hình tư duy với PermGen hay một cấu trúc dữ liệu nội bộ cụ thể.

Điểm cần nhớ là pool là cơ chế **tối ưu và chuẩn hóa định danh tham chiếu**, không thay đổi hợp đồng so sánh nội dung văn bản: muốn so nội dung vẫn dùng `equals`.

Pool cũng không phải một `Map` công khai mà ứng dụng có thể duyệt, xóa hay sửa trực tiếp. Hãy dùng nó như mô hình tư duy cho tham chiếu chuẩn hóa.

## <a id="literal-vs-new">Literal và new String</a>

```java
String a = "java";
String b = new String("java");
```

`a` trỏ tới literal trong pool, còn `new String(...)` yêu cầu tạo một đối tượng String mới.

Vì vậy:

```java
System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

Không dùng `new String("...")` nếu chỉ cần một literal bình thường; nó thường thêm đối tượng không cần thiết.

`new` không làm String trở nên có thể thay đổi:

```java
String x = new String("java");
x.toUpperCase();

System.out.println(x); // java
```

Hàm khởi tạo (constructor) chỉ thay đổi cách đối tượng được tạo, không thay đổi quy ước bất biến.

## <a id="pool-identity">String Pool và định danh tham chiếu</a>

Phép nối là biểu thức hằng tại thời điểm biên dịch tạo ra một hằng String được intern, nên có thể dùng cùng tham chiếu chuẩn hóa với literal tương ứng:

```java
String a = "ja" + "va";
String b = "java";
```

Trong khi phép nối phụ thuộc giá trị chỉ biết khi chạy không có cùng bảo đảm về định danh tham chiếu.

Quy tắc chính xác về **biểu thức hằng (constant expression)**, biến `final` và phép nối tại thời điểm biên dịch được trình bày ở chương Nối chuỗi; tại đây chỉ cần giữ mô hình tư duy rằng định danh tham chiếu trong String Pool phụ thuộc vào cách giá trị được tạo, còn so sánh nội dung vẫn dùng `equals`.

### Quy tắc thực dụng

```text
cùng đối tượng?
→ ==

cùng nội dung văn bản?
→ equals

cần tham chiếu chuẩn hóa trong pool một cách có chủ ý?
→ intern(), với đánh đổi rõ ràng
```

Đừng viết logic nghiệp vụ dựa trên định danh tham chiếu trong pool. String Pool là cơ chế chuẩn hóa/tái sử dụng tham chiếu của Java/JVM; với literal và hằng String, việc intern còn gắn với contract của Java chứ không chỉ là tối ưu triển khai. **So sánh nội dung văn bản vẫn dùng `equals`**.

Chương tiếp theo tập trung trực tiếp vào quy tắc so sánh String.
