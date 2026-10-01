# Ranh giới với Stream API

## <a id="stream-functional-interface-bridge">Vì sao Stream API nhận giao diện hàm?</a>

Stream API cần bên gọi cung cấp các hành vi như "giữ lại phần tử này", "biến đổi giá trị này" hoặc "thực hiện hành động này với từng giá trị". Giao diện hàm giúp các hành vi đó có kiểu Java cụ thể.

Ví dụ, bên gọi có thể truyền các hợp đồng đã học từ `java.util.function`:

```java
Predicate<String> keep = name -> !name.isBlank();
Function<String, String> normalize = String::trim;
Consumer<String> print = System.out::println;

names.stream().filter(keep);
names.stream().map(normalize);
names.stream().forEach(print);
```

Mối liên hệ quan trọng trong mô-đun này nằm ở hệ thống kiểu: lambda và tham chiếu phương thức trở thành giá trị của các giao diện hàm đó, rồi được truyền vào phương thức của Stream.

Cách Stream lên lịch, trì hoãn, ghép hoặc thực thi các thao tác thuộc kiến thức riêng của Stream API.

## <a id="stream-behavior-contracts">Các hợp đồng hành vi thường được Stream sử dụng</a>

Các phương thức Stream khác nhau yêu cầu các hợp đồng hành vi khác nhau. Chỉ cần đọc kiểu giao diện hàm trong chữ ký phương thức, ta thường đã hiểu được vai trò của hành vi cần truyền vào.

- `Predicate<T>` trả lời câu hỏi đúng/sai về một giá trị.
- `Function<T, R>` biến đổi một giá trị sang dạng khác.
- `Consumer<T>` thực hiện hành động mà không trả kết quả qua hợp đồng hàm.
- `Supplier<T>` có thể cung cấp giá trị khi API cần một hành vi tạo dữ liệu.

Các giao diện này không thuộc riêng Stream. Chúng là hợp đồng Java dùng chung và còn xuất hiện trong tiện ích tập hợp, API bất đồng bộ, mã ứng dụng và nhiều thư viện khác.

Ở chương này, mục tiêu là nhận ra đúng hợp đồng. Phân loại thao tác và quy tắc thực thi chi tiết của Stream thuộc mô-đun Stream API.

## <a id="stream-lambda-method-reference">Lambda và tham chiếu phương thức trong mã Stream</a>

Trong mã dùng Stream, giao diện hàm thường xuất hiện thông qua cú pháp lambda hoặc tham chiếu phương thức.

```java
names.stream()
        .filter(name -> !name.isBlank())
        .map(String::trim);
```

Ở đây lambda có thể đáp ứng `Predicate<String>`, còn `String::trim` có thể đáp ứng hàm biến đổi mà `map` yêu cầu.

Ví dụ này chỉ nhằm chứng minh mối liên hệ: **phương thức Stream -> tham số giao diện hàm -> lambda/tham chiếu phương thức**. Ngữ nghĩa của chuỗi xử lý Stream thuộc mô-đun Stream API.

## <a id="collection-vs-stream-boundary">Dữ liệu Collection và quá trình xử lý Stream</a>

`Collection` trước hết là cấu trúc dữ liệu sở hữu hoặc cho phép truy cập các phần tử. `Stream` là khái niệm trừu tượng do API cung cấp để mô tả việc xử lý trên một nguồn phần tử; nó không đơn giản là một loại tập hợp khác.

Từ góc nhìn lập trình hàm trong Java, điểm cần giữ là Stream cho phép tham số hóa quá trình xử lý bằng hành vi. Nguồn dữ liệu có thể đến từ tập hợp, nhưng `Predicate`, `Function` hoặc `Consumer` được truyền vào vẫn là những giá trị giao diện hàm đã học trong mô-đun này.

Các câu hỏi như khi nào phần tử được xử lý, các thao tác được phân loại ra sao hoặc kết quả được gom như thế nào đều cần quy tắc riêng của Stream và nên học ở mô-đun chuyên biệt.

## <a id="stream-mechanics-boundary">Phần nào thuộc mô-đun Stream API?</a>

Mô-đun này sở hữu các cơ chế lập trình hàm mà Stream API *sử dụng*: giao diện hàm, lambda, tham chiếu phương thức, kiểu đích, bắt giữ biến và kết hợp hành vi.

Mô-đun Stream API chuyên biệt sở hữu các cơ chế của Stream, bao gồm:

- cách tạo Stream và nguồn Stream;
- ngữ nghĩa của thao tác trung gian và thao tác kết thúc;
- tính lười và thời điểm thực thi;
- phép rút gọn và bộ thu thập (`Collector`);
- thứ tự phần tử và hành vi liên quan đến thứ tự;
- Stream song song và các ràng buộc đi kèm.

Các chủ đề đó chỉ được nêu tên ở đây để xác định ranh giới, không được triển khai chi tiết. Ranh giới này giúp tránh hai nhầm lẫn phổ biến: lambda không phải là Stream, và biết cú pháp lambda chưa đồng nghĩa với hiểu cơ chế thực thi của Stream.

## <a id="stream-learning-handoff">Khi nào nên chuyển sang học mô-đun Stream API?</a>

Nên chuyển sang mô-đun Stream API khi câu hỏi còn lại là **Stream xử lý dữ liệu như thế nào**, thay vì **Java biểu diễn hành vi truyền vào Stream ra sao**.

Tiếp tục học trong mô-đun này nếu câu hỏi là:

- Vì sao tham số có kiểu `Predicate<T>` hoặc `Function<T, R>`?
- Vì sao lambda này tương thích với lời gọi phương thức này nhưng không tương thích với lời gọi khác?
- Tham chiếu phương thức này có khớp với kiểu hàm đích không?
- Lambda bên trong phải tuân theo quy tắc bắt giữ biến nào?

Chuyển sang Stream API khi câu hỏi liên quan tới vòng đời của Stream, ngữ nghĩa thao tác, thời điểm thực thi, cách gom kết quả, thứ tự hoặc xử lý song song.
