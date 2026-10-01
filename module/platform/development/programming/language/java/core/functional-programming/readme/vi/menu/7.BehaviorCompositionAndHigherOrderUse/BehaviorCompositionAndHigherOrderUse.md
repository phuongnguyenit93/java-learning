# Truyền, trả về và kết hợp hành vi

## <a id="higher-order-methods">Phương thức nhận hoặc trả về hành vi</a>

Java không có một kiểu hàm riêng biệt ở cấp ngôn ngữ. Thay vào đó, phương thức nhận hoặc trả về hành vi thông qua một giao diện hàm như `Function`, `Predicate`, `Consumer` hoặc một giao diện hàm do ứng dụng tự định nghĩa.

Nhờ vậy, Java vẫn hỗ trợ cách lập trình bậc cao theo hướng thực dụng: hành vi có thể được truyền như một giá trị, còn giao diện hàm giữ rõ hợp đồng về tham số và kết quả.

```java
static <T, R> R apply(T value, Function<T, R> operation) {
    return operation.apply(value);
}

String result = apply(" java ", String::trim);
```

Phương thức quyết định *khi nào* hành vi chạy. Bên gọi quyết định *hành vi nào* được truyền vào.

## <a id="passing-behavior">Truyền hành vi vào phương thức</a>

Truyền hành vi phù hợp khi thuật toán chính ổn định nhưng một quyết định bên trong cần thay đổi. Thay vì gắn cứng mọi biến thể bằng nhiều nhánh `if` hoặc nhiều lớp con, phương thức nhận quy tắc thay đổi đó qua tham số.

```java
static List<String> select(
        List<String> values,
        Predicate<String> rule) {
    List<String> result = new ArrayList<>();
    for (String value : values) {
        if (rule.test(value)) {
            result.add(value);
        }
    }
    return result;
}

List<String> longNames = select(names, name -> name.length() >= 5);
```

Bước thiết kế quan trọng là chọn đúng hợp đồng hàm. `Predicate<T>` truyền đạt rõ ý nghĩa "đưa ra quyết định đúng/sai" hơn một giao diện tự tạo với tên phương thức mơ hồ.

## <a id="returning-behavior">Trả hành vi từ phương thức</a>

Phương thức cũng có thể tạo một hành vi rồi trả nó về để dùng sau. Cách này hữu ích khi xây dựng các quy tắc kiểm tra, phép biến đổi, hàm gọi lại hoặc chính sách có thể tái sử dụng.

```java
static Predicate<String> hasMinLength(int min) {
    return text -> text != null && text.length() >= min;
}

Predicate<String> atLeastFive = hasMinLength(5);
boolean valid = atLeastFive.test("Java 21");
```

Tại thời điểm chạy, giá trị trả về là một thực thể của kiểu giao diện hàm đích. Bên gọi nên phụ thuộc vào hợp đồng giao diện thay vì lớp hiện thực hoặc danh tính đối tượng, và không cần biết hành vi đến từ lambda, tham chiếu phương thức hay một lớp có tên.

## <a id="returned-behavior-capture">Trả về hành vi có bắt giữ giá trị từ phạm vi bên ngoài</a>

Hành vi trả về đặc biệt hữu ích khi nó cần nhớ dữ liệu từ lần gọi phương thức đã tạo ra nó. Lambda bắt giữ các giá trị đó và có thể dùng lại về sau.

```java
static Function<String, String> prefixWith(String prefix) {
    return value -> prefix + value;
}

Function<String, String> errorLabel = prefixWith("ERROR: ");
System.out.println(errorLabel.apply("Disk full"));
```

Mỗi lần gọi `prefixWith` tạo ra một hành vi gắn với `prefix` của lần gọi đó. Biến cục bộ được bắt giữ vẫn phải tuân theo quy tắc `final` hoặc không bị gán lại sau khi khởi tạo (effectively final) đã học ở chương trước.

Việc bắt giữ một tham chiếu không làm đối tượng được tham chiếu trở thành bất biến. Nếu hành vi trả về quan sát trạng thái có thể thay đổi, kết quả của nó có thể thay đổi theo các lần chỉnh sửa sau này và trở nên khó suy luận hơn.

## <a id="function-composition">Kết hợp Function với compose và andThen</a>

`Function` cho phép ghép hai phép biến đổi thành một phép biến đổi mới có thể tái sử dụng. Hai phương thức chính khác nhau ở hướng đọc và thứ tự thực thi.

```java
Function<String, String> trim = String::trim;
Function<String, Integer> length = String::length;

Function<String, Integer> trimThenLength = trim.andThen(length);
Function<String, Integer> sameFlow = length.compose(trim);

trimThenLength.apply(" Java "); // 4
```

Với `f.andThen(g)`, dữ liệu đi theo hướng `f -> g`. Với `g.compose(f)`, luồng thực thi cũng là `f -> g`. Có thể kiểm tra thêm bằng kiểu dữ liệu: đầu ra của hàm trước phải tương thích với đầu vào của hàm sau.

Kết hợp hàm hữu ích khi từng bước có ý nghĩa riêng. Nếu chuỗi quá dài và gồm nhiều hàm quá nhỏ, một phương thức thông thường đôi khi lại dễ đọc hơn.

## <a id="predicate-composition">Kết hợp Predicate</a>

`Predicate<T>` cung cấp `and`, `or` và `negate` để ghép các điều kiện nhỏ thành một quy tắc lớn hơn.

```java
Predicate<String> notBlank = text -> text != null && !text.isBlank();
Predicate<String> shortEnough = text -> text.length() <= 20;

Predicate<String> validName = notBlank.and(shortEnough);
```

`and` và `or` có cơ chế dừng sớm: điều kiện thứ hai chỉ được gọi khi kết quả của điều kiện đầu chưa đủ để quyết định kết quả cuối. Điều này rất quan trọng nếu điều kiện sau dựa vào một kiểm tra bảo vệ ở điều kiện trước.

Một `Predicate` nên hoạt động giống một câu hỏi. Nếu bên trong nó có thay đổi trạng thái hoặc I/O, một điều kiện tưởng như đơn giản có thể trở thành thao tác tốn kém hoặc phụ thuộc trạng thái.

## <a id="consumer-composition">Consumer.andThen</a>

`Consumer<T>` biểu diễn hành vi không trả về kết quả theo hợp đồng. `andThen` ghép nhiều hành vi `Consumer` để chạy tuần tự.

```java
Consumer<String> log = value -> System.out.println("LOG: " + value);
Consumer<String> audit = value -> auditStore.add(value);

Consumer<String> logAndAudit = log.andThen(audit);
```

`Consumer` đầu tiên chạy trước `Consumer` thứ hai. Nếu hành vi đầu ném ngoại lệ, hành vi sau không được thực thi.

Vì `Consumer` thường chứa tác dụng phụ, thứ tự của chúng có thể quan sát được. Việc kết hợp nên làm trình tự đó rõ hơn, không che giấu một chuỗi thao tác bên ngoài dễ lỗi.

## <a id="composition-order">Thứ tự kết hợp và chiều dữ liệu</a>

Nhiều lỗi khi kết hợp hành vi xuất phát từ việc chỉ đọc tên phương thức mà không theo dõi dữ liệu. Cách an toàn là viết rõ luồng giá trị trước.

```text
đầu vào
  -> trim
  -> parse
  -> validate
  -> kết quả
```

Sau đó chọn API sao cho luồng đọc khớp với ý định. Với `Function`, `andThen` thường dễ đọc từ trái sang phải, còn `compose` đặt tên hàm chạy sau ở phía trước.

Hệ thống kiểu giúp kiểm tra thêm: nếu một hàm trả về `Integer`, hàm kế tiếp phải nhận `Integer` hoặc kiểu tương thích. Trình biên dịch bắt được nhiều lỗi thứ tự về kiểu, nhưng không thể biết một thứ tự vẫn hợp lệ về kiểu có đúng với quy tắc nghiệp vụ hay không.

## <a id="composition-side-effects">Tác dụng phụ bên trong hành vi được kết hợp</a>

Java cho phép lambda trong chuỗi kết hợp thay đổi đối tượng, ghi nhật ký (log), gọi cơ sở dữ liệu hoặc thực hiện bất kỳ tác dụng phụ nào. Giao diện hàm không ép hành vi phải thuần túy.

Rủi ro là một chuỗi nhìn giống các phép biến đổi dữ liệu nhưng thực tế lại phụ thuộc vào số lần và thứ tự thực thi.

```java
Function<Order, Order> record = order -> {
    auditService.record(order);
    return order;
};
```

Nếu hàm này được kết hợp hoặc tái sử dụng, `auditService.record` chạy mỗi lần hàm được gọi. Điều đó có thể đúng với yêu cầu, nhưng tác dụng phụ nên thể hiện rõ qua tên và vị trí của hành vi.

Khi có thể, nên ưu tiên phép biến đổi không có tác dụng phụ cho phần logic tái sử dụng. Khi tác dụng phụ là cần thiết, giữ nó rõ ràng và gần ranh giới nơi thao tác bên ngoài thực sự xảy ra.
