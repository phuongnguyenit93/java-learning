# Tham chiếu phương thức và hàm tạo

## <a id="method-reference-model">Tham chiếu phương thức là gì?</a>

Tham chiếu phương thức là cách viết ngắn gọn để mô tả một hành vi đã tồn tại dưới dạng phương thức hoặc hàm tạo. Nó giữ cùng vai trò với lambda trong ngữ cảnh có kiểu đích: Java vẫn cần một giao diện hàm để biết hợp đồng tham số và kết quả.

Có thể hiểu đơn giản là **"dùng thao tác đã có này để thực hiện hợp đồng hàm kia"**. Việc viết tham chiếu chưa gọi phương thức ngay lập tức.

```java
Function<String, Integer> parse = Integer::parseInt;

int result = parse.apply("42");
```

Lambda tương đương là `text -> Integer.parseInt(text)`. Tham chiếu phương thức chỉ có lợi khi việc bỏ lớp bọc lambda làm ý định dễ đọc hơn.

## <a id="static-method-reference">Tham chiếu phương thức static</a>

Tham chiếu tới phương thức `static` có dạng `TypeName::staticMethod`. Các tham số của giao diện hàm đích được truyền vào phương thức `static` theo đúng thứ tự.

```java
Function<String, Integer> parse = Integer::parseInt;
BinaryOperator<Integer> max = Math::max;
```

Với `parse`, đối số của `Function.apply(String)` được đưa vào `Integer.parseInt(String)`. Với `max`, hai đối số của `BinaryOperator` trở thành hai đối số của `Math.max`.

Kiểu đích vẫn rất quan trọng khi phương thức `static` bị nạp chồng. Chỉ riêng phần chữ trước và sau `::` chưa đủ để Java luôn chọn được đúng phiên bản nạp chồng.

## <a id="bound-instance-method-reference">Tham chiếu phương thức của một đối tượng cụ thể</a>

Tham chiếu tới phương thức của một đối tượng cụ thể có dạng `object::instanceMethod`. Đối tượng nhận đã được cố định, vì vậy giao diện hàm đích chỉ cần cung cấp các đối số còn lại của phương thức.

```java
String prefix = "ID-";
Function<String, String> addPrefix = prefix::concat;

String value = addPrefix.apply("17"); // "ID-17"
```

Ở đây `prefix` là đối tượng nhận của `concat`. `Function.apply` chỉ cung cấp một đối số `String`.

Vì đối tượng nhận được giữ lại khi tạo tham chiếu, cần cẩn thận nếu đối tượng đó có thể thay đổi. Nhiều lần dùng cùng tham chiếu vẫn có thể quan sát trạng thái mới của chính đối tượng ấy.

## <a id="unbound-instance-method-reference">Tham chiếu phương thức của đối tượng chưa xác định</a>

Tham chiếu kiểu này có dạng `TypeName::instanceMethod`. Chưa có đối tượng nhận cố định, nên giao diện hàm đích phải cung cấp đối tượng nhận ở tham số đầu tiên, rồi mới tới các đối số của phương thức.

```java
Function<String, String> trim = String::trim;
BiPredicate<String, String> startsWith = String::startsWith;

trim.apply("  Java  ");            // "Java"
startsWith.test("Java", "Ja");   // true
```

Với `String::trim`, đầu vào của `Function` trở thành đối tượng `String` nhận lời gọi. Với `String::startsWith`, đối số đầu của `BiPredicate` là đối tượng nhận, đối số thứ hai là tiền tố.

Đây là khác biệt quan trọng giữa `someString::method` và `String::method`.

## <a id="constructor-reference">Tham chiếu hàm tạo</a>

Tham chiếu hàm tạo có dạng `TypeName::new`. Nó cho phép một giao diện hàm mô tả việc tạo đối tượng mà không cần viết lambda chỉ để gọi `new`.

```java
Supplier<List<String>> listFactory = ArrayList::new;
Function<String, StringBuilder> builderFactory = StringBuilder::new;

List<String> names = listFactory.get();
StringBuilder builder = builderFactory.apply("Java");
```

Kiểu đích quyết định dạng hàm tạo cần tìm. `Supplier` không có tham số cần một hàm tạo tương thích không tham số, còn `Function` một tham số cần một hàm tạo tương thích với một đối số.

Cơ chế suy luận kiểu tổng quát (generic) vẫn áp dụng bình thường. Tham chiếu hàm tạo không bỏ qua các quy tắc về quyền truy cập hoặc chọn phiên bản nạp chồng của Java.

Mảng cũng hỗ trợ tham chiếu hàm tạo. Trong trường hợp này, giao diện hàm đích cung cấp độ dài mảng cần tạo:

```java
IntFunction<String[]> stringArray = String[]::new;
String[] values = stringArray.apply(3);
```

Với tham chiếu đã gắn với một đối tượng như `receiver::method`, biểu thức `receiver` được đánh giá ngay khi biểu thức tham chiếu phương thức được đánh giá. Nếu kết quả là `null`, việc tạo tham chiếu đó ném `NullPointerException`; kiểm tra `null` không bị trì hoãn tới lúc gọi phương thức hàm.

## <a id="method-reference-target-adaptation">Cách tham chiếu phương thức khớp với kiểu hàm đích</a>

Một tham chiếu phương thức không có kiểu hàm hữu ích khi đứng riêng. Java diễn giải nó dựa trên giao diện hàm làm kiểu đích rồi kiểm tra xem lời gọi được tham chiếu có đáp ứng được kiểu hàm của giao diện đó hay không.

Các phần cần khớp gồm:

- số lượng và thứ tự tham số của kiểu đích;
- cách ánh xạ đối tượng nhận với phương thức thực thể;
- các phép chuyển đổi tham số mà lời gọi phương thức Java cho phép;
- tính tương thích giữa kết quả phương thức và kết quả của kiểu đích;
- quy tắc chọn nạp chồng khi có nhiều thành viên cùng tên.

```java
Function<String, Integer> decimal = Integer::parseInt;
ToIntFunction<String> decimalPrimitive = Integer::parseInt;
```

Hai tham chiếu đều dùng cùng một thao tác, nhưng hợp đồng đích khác nhau: một cái cho ra `Integer`, cái còn lại cho ra `int` nguyên thủy. Quy tắc đóng hộp/mở hộp (boxing/unboxing) thông thường cùng với kiểu đích quyết định phép gán có hợp lệ hay không.

## <a id="method-reference-result-adaptation">Tính tương thích của kết quả và trường hợp bỏ qua giá trị trả về</a>

Khi kiểu hàm đích có giá trị trả về, phương thức được tham chiếu phải tạo ra kết quả tương thích. Khi kiểu đích trả về `void`, Java có thể gọi một phương thức tương thích rồi bỏ qua kết quả của nó.

```java
Function<String, String> trimAndKeep = String::trim;
Consumer<String> trimAndDiscard = String::trim;
```

Cả hai đều có thể gọi `String.trim()`. `Function` giữ lại `String` được trả về, còn `Consumer` bỏ qua nó.

Việc bỏ kết quả có thể hợp lệ về mặt ngôn ngữ nhưng vẫn gây khó hiểu. Nếu một `Consumer` gọi phương thức có giá trị trả về, người đọc có thể tưởng kết quả bị bỏ quên; chỉ nên dùng khi việc gọi hoặc tác dụng phụ của phương thức mới là mục đích rõ ràng.

## <a id="overloaded-method-reference">Tham chiếu tới phương thức được nạp chồng</a>

Nếu phương thức có nhiều phiên bản nạp chồng, Java dùng kiểu hàm đích để chọn thành viên tương thích. Phần lớn trường hợp hoạt động tự nhiên, nhưng ngữ cảnh kiểu đích chưa rõ hoặc có nhiều phiên bản nạp chồng cùng phù hợp có thể tạo ra sự mơ hồ.

```java
Function<String, Integer> parseInt = Integer::valueOf;
Function<String, Long> parseLong = Long::valueOf;
```

Kiểu ở bên trái cung cấp đủ thông tin để Java hiểu từng tham chiếu. Vấn đề thường xuất hiện khi lời gọi bao quanh cũng bị nạp chồng hoặc khi chưa xác định được kiểu đích.

Trong trường hợp đó, lambda giúp ghi rõ kiểu tham số hoặc lời gọi cụ thể và thường dễ đọc, dễ gỡ lỗi hơn một phép ép kiểu phức tạp quanh tham chiếu phương thức.

## <a id="method-reference-vs-lambda">Dùng tham chiếu phương thức hay lambda?</a>

Nên dùng tham chiếu phương thức khi lambda chỉ chuyển tiếp các đối số tới đúng một phương thức hoặc hàm tạo có sẵn và tên thành viên đó đã diễn đạt hành vi rõ ràng.

```java
names.forEach(System.out::println);       // ngắn và dễ nhận ra

Function<String, String> normalized =
        text -> text == null ? "" : text.trim(); // lambda chứa logic thật sự
```

Lambda thường rõ hơn khi cần kiểm tra điều kiện, đổi thứ tự đối số, dùng hằng số, gọi nhiều thao tác, có luồng điều khiển hoặc cần thể hiện ý định mà tên phương thức hiện có không nói rõ.

Không nên coi tham chiếu phương thức là mục tiêu tự thân. Nó chỉ là cú pháp để tái sử dụng hành vi có sẵn dưới một hợp đồng giao diện hàm; khả năng đọc mới là tiêu chí lựa chọn.
