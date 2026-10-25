# Optional và biểu diễn sự vắng mặt

## <a id="optional-purpose">Vì sao Optional tồn tại?</a>

API thường cần diễn đạt rằng "có thể không có giá trị". Trả về `null` có thể biểu diễn điều đó, nhưng người gọi rất dễ bỏ quên trường hợp vắng mặt vì bản thân kiểu dữ liệu không nhắc họ phải xử lý nó.

`Optional<T>` đưa khả năng vắng mặt vào chính kiểu trả về. Có thể hình dung nó như một hộp chứa đúng một giá trị khác `null`, hoặc không chứa giá trị nào.

```java
Optional<User> findUser(String id) {
    // trả về Optional.empty() khi không tìm thấy người dùng
}
```

`Optional` không xóa `null` khỏi Java và cũng không phải lớp bọc nên dùng cho mọi tham chiếu. Nó hữu ích nhất ở những ranh giới API nơi việc không có kết quả là tình huống bình thường và có thể dự đoán trước.

## <a id="optional-creation">Tạo giá trị Optional</a>

Chọn cách tạo dựa trên điều ta biết về giá trị đầu vào.

```java
Optional<String> none = Optional.empty();
Optional<String> known = Optional.of("Java");
Optional<String> maybe = Optional.ofNullable(valueFromLegacyApi);
```

`Optional.of(value)` yêu cầu `value` khác `null`; nếu không nó ném `NullPointerException`. `Optional.ofNullable(value)` chuyển `null` thành `Optional.empty()`.

Không nên dùng `ofNullable` theo thói quen cho mọi giá trị. Nếu dữ liệu lẽ ra bắt buộc phải khác `null`, việc chuyển im lặng sang trạng thái rỗng có thể che một lỗi thật sự.

Với API làm việc nhiều với kiểu nguyên thủy, JDK còn có `OptionalInt`, `OptionalLong` và `OptionalDouble`. Các kiểu này biểu diễn kết quả nguyên thủy có thể vắng mặt mà không phải bọc dữ liệu trong `Integer`, `Long` hoặc `Double`. Chúng là các kiểu riêng với tập API nhỏ hơn, không phải lớp con của `Optional<T>`.

## <a id="optional-null-boundary">Optional và ranh giới null</a>

Bản thân một `Optional` thông thường nên khác `null`. Nếu một phương thức trả về `null` thay vì `Optional.empty()`, người gọi lại phải xử lý hai dạng "không có giá trị", làm mất ý nghĩa của hợp đồng `Optional`.

`Optional` cũng quy định rõ cách `null` tương tác với phép biến đổi:

- `map` biến kết quả `null` của hàm ánh xạ thành `Optional.empty()`;
- `flatMap` yêu cầu hàm ánh xạ trả về một `Optional` khác `null` và sẽ ném `NullPointerException` nếu hàm ánh xạ trả về `null`.

```java
Optional<String> name = Optional.of("Java");
Optional<Integer> length = name.map(String::length);
```

Ở ranh giới với API cũ có thể trả về `null`, `ofNullable` thường là cầu nối phù hợp để đi vào luồng xử lý dùng `Optional`.

## <a id="optional-presence">Có giá trị và không có giá trị</a>

`isPresent()` và `isEmpty()` cho biết `Optional` đang có hay không có giá trị.

`isPresent()` đã có từ Java 8, còn `isEmpty()` được bổ sung từ Java 11. Mã phải biên dịch với Java 8 không thể dùng `isEmpty()`.

```java
if (user.isPresent()) {
    System.out.println(user.get().name());
}
```

Đoạn mã trên hợp lệ, nhưng nếu liên tục kiểm tra rồi gọi `get()`, ta dễ quay lại kiểu rẽ nhánh thủ công mà `Optional` đang cố làm rõ. Khi phù hợp, `map`, `filter`, `ifPresent` hoặc các phương thức lấy giá trị thay thế giữ quyết định "có hay không" gắn với chính `Optional`.

Kiểm tra trực tiếp vẫn hợp lý khi luồng điều khiển xung quanh dễ hiểu hơn theo cách đó. `Optional` là công cụ thiết kế API, không phải quy tắc cấm dùng `if`.

## <a id="optional-transform-filter">Biến đổi và lọc giá trị Optional</a>

`map` biến đổi giá trị khi nó tồn tại. Nếu `Optional` rỗng, hàm ánh xạ không được gọi và kết quả tiếp tục là `Optional.empty()`.

```java
Optional<String> normalizedName = user
        .map(User::name)
        .map(String::trim);
```

Nếu hàm biến đổi vốn đã trả về `Optional`, dùng `flatMap` để tránh tạo `Optional<Optional<T>>`.

```java
Optional<Address> address = user.flatMap(User::address);
```

`filter` giữ lại giá trị hiện có khi điều kiện trả về `true`; nếu không, kết quả trở thành `Optional.empty()`.

```java
Optional<User> active = user.filter(User::isActive);
```

Điểm hữu ích là trường hợp vắng mặt tự động bỏ qua phép biến đổi hoặc điều kiện mà không cần kiểm tra `null` ở từng bước.

## <a id="optional-consumption">Xử lý khi giá trị tồn tại</a>

Khi chỉ cần thực hiện một hành động nếu có giá trị, `ifPresent` diễn đạt đúng ý định đó.

```java
user.ifPresent(found -> audit.log("Found " + found.id()));
```

Nếu cả hai trường hợp đều cần hành động, `ifPresentOrElse` giữ hai nhánh cạnh nhau.

```java
user.ifPresentOrElse(
        this::displayUser,
        () -> displayMessage("User not found")
);
```

Các phương thức này phù hợp cho hành động kết thúc. Nếu vẫn cần một giá trị cho bước tính toán tiếp theo, nên dùng phép biến đổi hoặc phương án thay thế thay vì tạo biến tạm có thể thay đổi bên trong `ifPresent`.

`ifPresentOrElse` được bổ sung sau API `Optional` ban đầu của Java 8, cụ thể từ Java 9. Mô-đun Java Core này trình bày API ổn định hiện tại; mô-đun Java 8 giữ vai trò giải thích bối cảnh theo phiên bản khi tính năng được giới thiệu.

## <a id="optional-fallback-and-failure">Chiến lược dùng giá trị thay thế và báo thất bại</a>

Chọn phương án thay thế dựa trên việc giá trị thay thế có rẻ, tốn kém, tiếp tục là `Optional` hay cần biến sự vắng mặt thành lỗi.

```java
String a = userName.orElse("anonymous");
String b = userName.orElseGet(this::loadDefaultName);
Optional<String> c = userName.or(this::lookupSecondaryName);
String d = userName.orElseThrow(UserNotFoundException::new);
```

Đối số của `orElse(value)` được tính trước khi gọi phương thức, kể cả khi `Optional` đang có giá trị. `orElseGet(supplier)` chỉ gọi hàm cung cấp khi không có giá trị. Khác biệt này quan trọng nếu phương án thay thế tốn tài nguyên hoặc có tác dụng phụ.

`or` là lựa chọn tính khi cần khi phương án thay thế cũng là một `Optional`. `orElseThrow` biến trường hợp vắng mặt thành thất bại và có thể tạo ngoại lệ phù hợp với ranh giới API.

`Optional.or` cũng được bổ sung từ Java 9. Nếu dự án phải giữ khả năng tương thích mã nguồn với Java 8, không nên giả định mọi phương thức của `Optional` hiện tại đều đã tồn tại ở phiên bản đó.

## <a id="optional-api-design">Optional tại ranh giới API</a>

`Optional` phù hợp nhất làm kiểu trả về khi "không tìm thấy kết quả" là khả năng bình thường mà người gọi cần nhìn thấy ngay trong hợp đồng phương thức.

```java
Optional<Order> findOrder(OrderId id);
```

Nó thường ít hữu ích hơn nếu bị dùng cho mọi trường hoặc tham số. Với tham số, đôi khi các phương thức tách biệt, nạp chồng, đối tượng yêu cầu hoặc quy ước có thể nhận null thể hiện miền nghiệp vụ rõ hơn. Với phương thức trả về tập hợp, tập hợp rỗng thường đã diễn đạt tự nhiên "không có phần tử", nên `Optional<List<T>>` thường không cần thiết.

Đây là hướng dẫn thiết kế chứ không phải giới hạn của trình biên dịch. Câu hỏi chính là: `Optional` có làm ý nghĩa của sự vắng mặt rõ hơn cho người gọi hay không?

## <a id="optional-misuse">Các cách dùng Optional không phù hợp</a>

Lỗi phổ biến xuất hiện khi `Optional` trở thành thủ tục hình thức thay vì mô hình rõ ràng cho sự vắng mặt.

- Trả về `null` từ phương thức khai báo `Optional<T>` tạo ra hai cách biểu diễn cùng một trường hợp vắng mặt.
- Gọi `get()` khi chưa chắc có giá trị có thể ném `NoSuchElementException` và thường che mất quy tắc chọn giá trị thay thế hoặc báo lỗi thực sự cần có.
- Bọc một giá trị vào `Optional` rồi lập tức tháo ra chỉ làm mã dài hơn mà không cải thiện hợp đồng.
- Dùng `Optional` cho mọi trường, tham số và tập hợp có thể làm lớp bọc lan khắp mã mà không làm miền nghiệp vụ rõ hơn.
- Đặt tác dụng phụ trong `map` khiến một phép biến đổi trông thuần túy nhưng thực tế lại thay đổi trạng thái; nếu tác dụng phụ mới là mục tiêu, nên đặt nó ở hành động kết thúc hoặc ranh giới rõ ràng.

Hãy dùng `Optional` khi hợp đồng API cần thể hiện rõ khả năng vắng mặt ngay trong kiểu dữ liệu. Nó làm trường hợp vắng mặt hiện rõ với người gọi nhưng không ép người gọi phải xử lý theo một cách duy nhất. Ở chỗ khác, chọn cách biểu diễn trực tiếp nhất cho miền nghiệp vụ.
