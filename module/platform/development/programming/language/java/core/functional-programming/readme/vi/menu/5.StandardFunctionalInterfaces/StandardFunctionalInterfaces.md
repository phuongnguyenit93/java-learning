# Các giao diện hàm chuẩn

## <a id="java-util-function-overview">Các nhóm giao diện trong java.util.function</a>

Java cung cấp nhiều giao diện hàm dùng lại được trong gói `java.util.function`, nhờ vậy không cần tự tạo giao diện mới cho mọi hình dạng hành vi quen thuộc.

Khi chọn giao diện, câu hỏi quan trọng không phải là "nhớ được bao nhiêu tên API", mà là **hành vi có hợp đồng như thế nào**:

- biến một giá trị thành giá trị khác → `Function`;
- trả lời một điều kiện đúng/sai → `Predicate`;
- nhận giá trị rồi thực hiện hành động → `Consumer`;
- tạo ra giá trị mà không nhận đầu vào → `Supplier`;
- đầu vào và đầu ra cùng kiểu → có thể dùng giao diện toán tử;
- cần hai đầu vào → xem các nhóm `Bi...`.

```java
Function<String, Integer> length = String::length;
Predicate<String> nonEmpty = text -> !text.isEmpty();
Consumer<String> printer = System.out::println;
Supplier<UUID> idFactory = UUID::randomUUID;
```

Đây vẫn là các giao diện Java thông thường có một hợp đồng hàm. Lambda và tham chiếu phương thức chỉ là cú pháp ngắn gọn để cung cấp phần cài đặt cho hợp đồng đó.

## <a id="function-contract">Function&lt;T, R&gt;</a>

`Function<T, R>` mô tả phép biến đổi từ một giá trị đầu vào kiểu `T` thành một kết quả kiểu `R`.

Phương thức hàm của nó có dạng:

```java
R apply(T value);
```

Ví dụ:

```java
Function<String, Integer> length = text -> text.length();

int result = length.apply("Java"); // 4
```

Dùng `Function` khi hợp đồng cốt lõi là **đầu vào → đầu ra**. Đầu ra có thể cùng kiểu với đầu vào, nhưng nếu quan hệ đó luôn đúng thì `UnaryOperator<T>` thường diễn đạt ý định rõ hơn.

`Function` cũng phù hợp với việc kết hợp hành vi vì đầu ra của hàm này có thể trở thành đầu vào của hàm khác. Quy tắc kết hợp sẽ được học ở chương sau.

## <a id="predicate-contract">Predicate&lt;T&gt;</a>

`Predicate<T>` mô tả một điều kiện áp dụng lên giá trị kiểu `T`. Kết quả luôn là `boolean`.

```java
Predicate<String> nonEmpty = text -> !text.isEmpty();
Predicate<Integer> adult = age -> age >= 18;

boolean accepted = adult.test(20); // true
```

Phương thức hàm là `boolean test(T value)`.

Dùng `Predicate` khi hành vi trả lời một câu hỏi như "giá trị này có hợp lệ không?", "có khớp không?" hoặc "có nên chấp nhận không?". Đặt tên biến hoặc điều kiện `Predicate` theo ý nghĩa nghiệp vụ thường giúp nơi gọi đọc tự nhiên hơn.

Predicate có thể được kết hợp bằng `and`, `or`, `negate`; phần kết hợp sẽ được xử lý ở chương riêng để chương này tập trung vào việc chọn đúng hợp đồng trước.

## <a id="consumer-contract">Consumer&lt;T&gt;</a>

`Consumer<T>` nhận một giá trị và không trả kết quả. Phương thức hàm có dạng:

```java
void accept(T value);
```

Ví dụ:

```java
Consumer<String> printer = text -> System.out.println(text);
printer.accept("hello");
```

Consumer thường đại diện cho một hành động có tác động quan sát được như ghi nhật ký, gửi dữ liệu, cập nhật đối tượng hoặc gọi dịch vụ khác.

Vì `Consumer` trả `void`, nó không phù hợp khi bên gọi cần nhận lại một giá trị đã biến đổi. Trường hợp đó thường nên dùng `Function` hoặc một hợp đồng có đầu ra rõ ràng hơn.

Tác dụng phụ là hợp lệ trong mã theo phong cách hàm của Java, nhưng vị trí của nó ảnh hưởng tới khả năng đọc và kiểm thử. Chương tổng hợp cuối mô-đun sẽ quay lại đánh đổi này.

## <a id="supplier-contract">Supplier&lt;T&gt;</a>

`Supplier<T>` tạo ra một giá trị mà không nhận tham số qua phương thức hàm:

```java
T get();
```

Ví dụ:

```java
Supplier<UUID> idFactory = UUID::randomUUID;
UUID id = idFactory.get();
```

Ý tưởng quan trọng là **trì hoãn việc tạo giá trị**. Khi truyền `Supplier<T>`, ta truyền cách tạo ra giá trị thay vì một giá trị đã được tính sẵn.

```java
Supplier<String> expensiveMessage = () -> buildExpensiveMessage();
```

Việc `get()` có được gọi hay không và gọi lúc nào do mã nhận hàm cung cấp quyết định. Mô hình này hữu ích cho cơ chế tạo, phương án thay thế chỉ tính khi cần, bộ cung cấp dữ liệu kiểm thử và các trường hợp tạo dữ liệu theo yêu cầu.

## <a id="operator-contracts">UnaryOperator&lt;T&gt; và BinaryOperator&lt;T&gt;</a>

Các giao diện toán tử giữ quan hệ cùng kiểu: `UnaryOperator<T>` là biến thể chuyên biệt của `Function<T, T>`, còn `BinaryOperator<T>` là biến thể chuyên biệt của `BiFunction<T, T, T>`.

`UnaryOperator<T>` nhận một giá trị `T` và tạo ra một giá trị `T`:

```java
UnaryOperator<String> normalize = text -> text.trim().toLowerCase();
```

`BinaryOperator<T>` nhận hai giá trị `T` và tạo ra một giá trị `T`:

```java
BinaryOperator<Integer> max = (left, right) -> Math.max(left, right);
```

Dùng các giao diện này khi quan hệ "cùng kiểu" mang ý nghĩa. `UnaryOperator<String>` nói rõ "String vào, String ra" hơn `Function<String, String>`, còn `BinaryOperator<Integer>` thể hiện việc gộp hai giá trị cùng miền kiểu thành một giá trị cùng kiểu.

## <a id="bi-functional-interfaces">BiFunction, BiConsumer và BiPredicate</a>

Nhóm `Bi...` mô tả các hợp đồng quen thuộc có hai đầu vào.

```java
BiFunction<BigDecimal, Integer, BigDecimal> multiply =
        (price, quantity) -> price.multiply(BigDecimal.valueOf(quantity));

BiPredicate<String, Integer> minimumLength =
        (text, minimum) -> text.length() >= minimum;

BiConsumer<Map<String, Integer>, String> increment =
        (counts, key) -> counts.merge(key, 1, Integer::sum);
```

Hình dạng tương ứng:

- `BiFunction<T, U, R>`: hai đầu vào, một đầu ra;
- `BiPredicate<T, U>`: hai đầu vào, kết quả `boolean`;
- `BiConsumer<T, U>`: hai đầu vào, không có đầu ra.

`java.util.function` không có sẵn một họ `TriFunction` tổng quát. Nếu hành vi cần rất nhiều tham số, trước khi tạo giao diện nhiều tham số riêng nên cân nhắc xem một đối tượng miền nghiệp vụ nhỏ có thể làm hợp đồng dễ hiểu hơn không.

## <a id="primitive-specializations">Các biến thể chuyên biệt cho kiểu nguyên thủy</a>

Giao diện hàm tổng quát (generic) làm việc với kiểu tham chiếu. Vì vậy khi dùng kiểu nguyên thủy thông qua kiểu như `Function<Integer, Integer>`, chương trình có thể phải đóng hộp/mở hộp (boxing/unboxing).

`java.util.function` cung cấp các biến thể chuyên biệt cho những hình dạng `int`, `long` và `double` thường gặp, ví dụ:

```java
IntPredicate positive = value -> value > 0;
IntUnaryOperator square = value -> value * value;
ToIntFunction<String> length = String::length;
IntFunction<String> label = value -> "#" + value;
```

Các giao diện này không tạo ra một mô hình lập trình khác. Chúng vẫn biểu diễn cùng kiểu hành vi, nhưng giúp tránh đối tượng bọc không cần thiết trong mã xử lý nhiều giá trị kiểu nguyên thủy.

Không cần tự động chọn biến thể chuyên biệt ở mọi nơi dùng kiểu nguyên thủy. Ưu tiên chúng khi API đã dùng biến thể đó hoặc khi chi phí đóng hộp thật sự đáng quan tâm; nếu không, độ rõ ràng của hợp đồng vẫn là tiêu chí chính.

## <a id="generic-input-output-flow">Quan hệ kiểu đầu vào và đầu ra với Generics</a>

Các tham số kiểu của giao diện hàm chuẩn làm rõ hướng dữ liệu đi qua hành vi.

Với `Function<T, R>`:

```text
T  ──apply──>  R
```

Với `BiFunction<T, U, R>`:

```text
T + U  ──apply──>  R
```

Với `Predicate<T>`, `T` là đầu vào còn `boolean` là đầu ra cố định. Với `Consumer<T>`, `T` là đầu vào và không có đầu ra. Với `Supplier<T>`, không có tham số đầu vào còn `T` là giá trị được tạo ra.

Quan hệ này rất hữu ích khi đọc API dùng kiểu tổng quát (generic):

```java
Function<Order, BigDecimal> total = Order::total;
```

Chỉ nhìn vào kiểu đã có thể hiểu luồng chính: `Order` đi vào và `BigDecimal` đi ra, trước cả khi đọc thân lambda.

Khi học cách kết hợp ở chương sau, việc theo dõi kiểu đầu ra của hành vi trước có khớp kiểu đầu vào của hành vi sau hay không cũng dựa trên mô hình tư duy này.

## <a id="functional-interfaces-outside-java-util-function">Giao diện hàm bên ngoài java.util.function</a>

`java.util.function` là thư viện chính cho các dạng hàm tổng quát, nhưng không phải mọi giao diện hàm của JDK đều nằm trong gói này.

Ví dụ:

```java
Runnable task = () -> doWork();
Callable<String> loader = () -> loadValue();
Comparator<String> byLength =
        (left, right) -> Integer.compare(left.length(), right.length());
```

`Runnable`, `Callable<V>` và `Comparator<T>` thuộc các gói hoặc miền API khác nhau, nhưng mỗi giao diện đều có một hợp đồng hàm tương thích nên vẫn có thể làm kiểu đích cho lambda hoặc tham chiếu phương thức.

Điều cần học là nhận diện **hợp đồng giao diện hàm**, không phải ghi nhớ rằng mọi kiểu đích của lambda đều phải đến từ `java.util.function`.

## <a id="standard-vs-custom-functional-interface">Giao diện hàm chuẩn hay giao diện theo miền nghiệp vụ?</a>

Nên ưu tiên giao diện hàm chuẩn khi chính hợp đồng của nó đã diễn đạt đủ rõ ý định:

```java
Predicate<Order> eligible;
Function<Order, BigDecimal> totalCalculator;
Supplier<Clock> clockSupplier;
```

Nên tạo giao diện hàm theo miền nghiệp vụ khi ý nghĩa nghiệp vụ đủ quan trọng để cần một tên hợp đồng riêng:

```java
@FunctionalInterface
interface FraudRule {
    boolean isSuspicious(Payment payment);
}
```

Đây là quyết định về khả năng đọc và thiết kế API, không phải cuộc thi giảm số lượng kiểu tự định nghĩa.

Dùng giao diện chuẩn khi:

- hình dạng đầu vào/đầu ra đã nói đủ rõ hành vi;
- bên gọi hưởng lợi từ từ vựng Java quen thuộc;
- các phép kết hợp chuẩn có ích.

Dùng giao diện riêng khi:

- vai trò nghiệp vụ cần một tên rõ ràng;
- hợp đồng cần ngoại lệ được kiểm tra (checked exception) hoặc cam kết riêng của miền nghiệp vụ;
- dùng `Function`/`Predicate` làm mất ý nghĩa quan trọng ở API.

Dù chọn dạng nào, hợp đồng hàm vẫn nên tập trung vào một hành vi thống nhất và dễ hiểu.
