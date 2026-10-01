# Biểu thức lambda và kiểu đích

## <a id="lambda-expression-model">Biểu thức lambda là gì?</a>

Biểu thức lambda là cú pháp Java dùng để cung cấp hành vi cho một giao diện hàm tương thích.

```java
Predicate<String> nonEmpty = text -> !text.isEmpty();
```

Phần `text -> !text.isEmpty()` không tự khai báo một phương thức có tên và cũng không tự tạo ra một kiểu hàm độc lập. Kiểu đích `Predicate<String>` ở bên ngoài cho trình biên dịch biết lambda nhận `String` và phải tạo ra kết quả `boolean`.

Khi biểu thức lambda được đánh giá, kết quả là một giá trị tham chiếu tới thực thể của kiểu giao diện hàm đích. Java không bảo đảm mỗi lần đánh giá đều cấp phát một đối tượng mới, vì vậy mã nên phụ thuộc vào hợp đồng giao diện thay vì dựa vào lớp hiện thực tại thời điểm chạy hoặc danh tính đối tượng.

Mô hình tư duy quan trọng là: **lambda chỉ có nghĩa đầy đủ khi đặt trong một ngữ cảnh cung cấp giao diện hàm đích**.

## <a id="lambda-syntax-forms">Các dạng cú pháp lambda</a>

Lambda gồm danh sách tham số, dấu `->`, rồi tới thân biểu thức hoặc thân khối.

```java
// Không có tham số
Supplier<Long> now = () -> System.currentTimeMillis();

// Một tham số; có thể bỏ dấu ngoặc tròn
Predicate<String> nonEmpty = text -> !text.isEmpty();

// Nhiều tham số
Comparator<String> byLength =
        (left, right) -> Integer.compare(left.length(), right.length());

// Thân khối
Function<String, Integer> parse = text -> {
    String trimmed = text.trim();
    return Integer.parseInt(trimmed);
};
```

Thân biểu thức phù hợp khi một biểu thức đã diễn đạt rõ hành vi. Thân khối phù hợp khi cần nhiều câu lệnh, biến cục bộ hoặc `return` tường minh.

Không nên cố ép nhiều bước nghiệp vụ vào một biểu thức chỉ để lambda ngắn hơn. Mục tiêu là giảm phần cú pháp thừa nhưng vẫn giữ luồng xử lý dễ đọc.

## <a id="target-typing">Cơ chế xác định kiểu theo ngữ cảnh (Target Typing)</a>

Lambda là biểu thức **được xác định kiểu theo ngữ cảnh (target typed)**. Trình biên dịch dùng giao diện hàm mà ngữ cảnh đang yêu cầu để hiểu lambda.

```java
Predicate<String> rule = text -> text.length() > 3;
```

Từ `Predicate<String>`, trình biên dịch biết `text` là `String` và thân lambda phải tương thích với kết quả `boolean`.

Cùng một đoạn lambda có thể mang các kiểu khác nhau nếu kiểu đích khác nhau:

```java
Callable<String> first = () -> "done";
Supplier<String> second = () -> "done";
```

Thân lambda giống nhau, nhưng hai biến có kiểu Java và hợp đồng API khác nhau.

Vì việc xác định kiểu theo ngữ cảnh (target typing) là bắt buộc, lambda thường không thể đứng độc lập khi không có ngữ cảnh xác định một giao diện hàm phù hợp.

## <a id="lambda-parameter-typing">Kiểu tham số tường minh và kiểu được suy luận</a>

Trong phần lớn trường hợp, trình biên dịch suy ra kiểu tham số lambda từ kiểu hàm của giao diện đích:

```java
BiFunction<Integer, Integer, Integer> add = (left, right) -> left + right;
```

Cũng có thể viết kiểu tường minh:

```java
BiFunction<Integer, Integer, Integer> add =
        (Integer left, Integer right) -> left + right;
```

Từ Java 11, có thể dùng `var` cho tham số lambda khi cần chú thích (annotation) hoặc muốn thể hiện thống nhất kiểu khai báo:

```java
BiFunction<Integer, Integer, Integer> add =
        (var left, var right) -> left + right;
```

Trong cùng một danh sách tham số lambda, phải dùng nhất quán một kiểu khai báo. Không trộn tham số được suy luận với tham số khai báo kiểu tường minh hoặc `var`.

Suy luận kiểu chỉ giúp bỏ lặp cú pháp; tham số lambda vẫn có kiểu thời điểm biên dịch rõ ràng do giao diện hàm đích quyết định.

## <a id="lambda-body-result-compatibility">Tính tương thích của thân lambda và kết quả</a>

Thân lambda phải phù hợp với kiểu kết quả mà kiểu hàm đích yêu cầu.

Với hợp đồng có trả giá trị, thân dạng biểu thức phải tạo ra giá trị tương thích. Xét riêng về cấu trúc, thân dạng khối **tương thích với việc trả giá trị (value-compatible)** khi nó không thể kết thúc bình thường và mọi câu lệnh `return` trong khối đều có dạng `return biểu_thức;`. Khi khớp với một kiểu kết quả đích cụ thể, từng biểu thức kết quả đó còn phải tương thích với kiểu đích:

```java
Function<String, Integer> length = text -> text.length();

Function<String, Integer> parsedLength = text -> {
    String trimmed = text.trim();
    return trimmed.length();
};

Supplier<String> alwaysFails = () -> {
    throw new IllegalStateException("failed");
};
```

Khối `alwaysFails` vẫn hợp lệ với kiểu đích có trả giá trị dù không bao giờ đi tới `return`, vì khối này không thể kết thúc bình thường mà luôn ném ngoại lệ.

Với hợp đồng trả `void`, thân dạng khối không trả về giá trị. Thân dạng biểu thức cũng có thể là một **biểu thức được phép dùng như câu lệnh (statement expression)**; nếu biểu thức đó tạo ra giá trị thì kết quả sẽ bị bỏ qua:

```java
Consumer<String> printer = text -> System.out.println(text);
Consumer<String> trimAndDiscard = text -> text.trim();

Consumer<String> normalizedPrinter = text -> {
    String normalized = text.trim();
    System.out.println(normalized);
};
```

Ở ví dụ `text.trim()`, phương thức trả về `String` nhưng kết quả bị bỏ qua vì kiểu hàm đích trả `void`. Điều này không có nghĩa mọi biểu thức tạo giá trị đều tương thích với `void`; Java áp dụng quy tắc riêng cho statement expression.

Lambda dạng khối vẫn phải tuân theo quy tắc về khả năng đi tới câu lệnh (reachability) và `return` của Java. Nếu kiểu đích yêu cầu một giá trị, khối không được kết thúc bình thường bằng cách đi tới dấu `}` cuối cùng.

Ngoại lệ được kiểm tra (checked exception) cũng bị giới hạn bởi kiểu hàm đích; phần đó sẽ được xử lý ở chương tổng hợp cuối mô-đun.

## <a id="lambda-target-contexts">Kiểu đích của lambda đến từ đâu?</a>

Kiểu đích thường đến từ ngữ cảnh gán biến, đối số khi gọi phương thức, giá trị trả về hoặc phép ép kiểu (cast) tường minh.

```java
// Ngữ cảnh gán
Predicate<String> valid = text -> !text.isBlank();

// Ngữ cảnh gọi phương thức
runWhenReady(() -> startService());

// Ngữ cảnh trả về
Predicate<String> buildRule() {
    return text -> text.length() >= 3;
}

// Ngữ cảnh ép kiểu
Object rule = (Predicate<String>) text -> !text.isEmpty();
```

Trong mỗi trường hợp, cấu trúc Java xung quanh cung cấp giao diện hàm mà lambda phải tương thích. Từ đó trình biên dịch mới xác định kiểu tham số và hợp đồng kết quả.

Nếu ngữ cảnh không chỉ ra được một kiểu đích tương thích duy nhất, trình biên dịch có thể báo lỗi thay vì tự đoán ý định của lập trình viên.

## <a id="lambda-execution-timing">Khai báo hành vi và thực thi hành vi</a>

Tạo một giá trị lambda không đồng nghĩa với việc thân lambda chạy ngay.

```java
Supplier<String> message = () -> {
    System.out.println("building message");
    return "ready";
};
```

Sau dòng trên, hành vi đã được tạo nhưng chuỗi `building message` chưa được in. Thân lambda chỉ chạy khi phương thức hàm được gọi:

```java
String value = message.get();
```

Điểm khác biệt này rất quan trọng với công việc trì hoãn. Lambda có thể giữ lại cấu hình ở thời điểm tạo, rồi được một phương thức hoặc thành phần khác gọi ở thời điểm sau.

Không nên suy luận rằng "truyền lambda vào" đồng nghĩa với "lambda chạy ngay". Thời điểm thực thi do API nhận giá trị giao diện hàm quyết định.

## <a id="lambda-overload-ambiguity">Mơ hồ khi nạp chồng với lambda</a>

Việc nạp chồng có thể trở nên mơ hồ nếu cùng một hình dạng lambda tương thích với nhiều tham số có kiểu giao diện hàm.

```java
void use(Callable<String> task) { }
void use(Supplier<String> task) { }

// use(() -> "done"); // mơ hồ
```

Cả hai kiểu đích đều mô tả hành vi không nhận tham số và trả về `String`, nên bản thân lambda chưa đủ để chọn nạp chồng.

Có thể chỉ rõ kiểu đích bằng phép ép kiểu (cast):

```java
use((Supplier<String>) () -> "done");
```

hoặc gán lambda vào một biến có kiểu rõ ràng trước khi gọi phương thức.

Nếu tự thiết kế API, nên tránh nạp chồng chỉ khác nhau bằng các giao diện hàm không liên quan nhưng có cùng hình dạng lambda. Kiểu API này khiến lời gọi đơn giản trở nên khó hiểu cho cả trình biên dịch lẫn người đọc.
