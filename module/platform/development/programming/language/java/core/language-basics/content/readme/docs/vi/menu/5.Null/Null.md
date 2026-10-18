# Giá trị `null` và an toàn khi dùng tham chiếu

`null` là một giá trị tham chiếu đặc biệt biểu diễn rằng **tham chiếu hiện không nhận diện đối tượng nào**. Nó không phải đối tượng, không phải `String` rỗng và không phải giá trị mặc định chung cho mọi kiểu.

## <a id="null-reference">`null` là giá trị tham chiếu</a>

Biến tham chiếu có thể giữ `null` nếu kiểu/ngữ cảnh cho phép:

```java
String name = null;
```

Biến kiểu nguyên thủy không thể giữ `null`:

```java
int value = null; // không biên dịch được
```

Kiểu tham chiếu có thể mang `null`; hệ quả đối với kiểu bao và thao tác mở hộp sẽ được học ở chương tiếp theo.

### `null` khác giá trị rỗng hoặc giá trị mặc định của nghiệp vụ

```text
null        → không có đối tượng được tham chiếu nhận diện
""          → có đối tượng String với giá trị rỗng
0           → giá trị int hợp lệ
```

Các trạng thái này có ngữ nghĩa khác nhau. Dùng `null` để biểu diễn nhiều ý nghĩa như “không tìm thấy”, “chưa tải”, “không áp dụng”, “lỗi” cùng lúc sẽ làm hợp đồng khó hiểu.

Trường kiểu tham chiếu có giá trị mặc định là `null`, nhưng biến cục bộ kiểu tham chiếu vẫn phải được gán chắc chắn trước khi đọc.

## <a id="null-dereference">Truy cập qua `null`</a>

Nếu mã cố dùng `null` như một đối tượng:

```java
name.length();
```

chương trình ném `NullPointerException` ở thời điểm chạy.

NPE thường không phải vấn đề “Java có null”, mà là ranh giới/hợp đồng không nói rõ giá trị có thể vắng mặt hay mã không kiểm tra bất biến cần thiết.

Nhiều thao tác có thể truy cập tham chiếu gián tiếp:

```java
user.getName();          // gọi phương thức instance
user.name;               // đọc trường instance
```

Khi tìm lỗi NPE, hãy hỏi **tham chiếu nào đang là `null` tại điểm truy cập**, thay vì chỉ thêm kiểm tra `null` ngẫu nhiên ở xa nguồn.

## <a id="null-comparison">So sánh với `null`</a>

Dùng `==`/`!=` để kiểm tra một tham chiếu có mang `null` hay không:

```java
boolean absent = user == null;
boolean present = user != null;
```

Gọi `user.equals(null)` là sai hướng vì nếu `user` chính là `null` thì chương trình đã truy cập qua tham chiếu `null` trước khi vào `equals`.

Sau khi học Luồng điều khiển, phép so sánh này thường được dùng làm điều kiện bảo vệ trước khi truy cập đối tượng. Khi cần so sánh bằng giữa các đối tượng có thể `null`, tiện ích như `Objects.equals(a, b)` có thể diễn đạt ý định rõ hơn, nhưng vẫn phải hiểu hợp đồng về khả năng nhận `null` của bài toán.

## <a id="null-api-design">`null` trong thiết kế API</a>

API nên làm rõ:

- tham số có chấp nhận `null` không;
- giá trị trả về có thể `null` không;
- collection có chứa `null` không;
- null có nghĩa “không có”, “chưa tải”, hay “không hợp lệ”.

Không phải mọi trường hợp “vắng giá trị” đều cần `Optional`, nhưng hợp đồng mơ hồ về `null` làm bên gọi phải đoán và tạo NPE xa nguồn gốc.

### Phát hiện sớm khi `null` không hợp lệ

```java
UserService(UserRepository repository) {
    this.repository = Objects.requireNonNull(repository);
}
```

Nếu `null` vi phạm bất biến, kiểm tra sớm giúp lỗi xuất hiện gần ranh giới gây ra nó.

### `null`, giá trị rỗng và trạng thái vắng mặt phải có hợp đồng

API collection thường nên quyết định rõ:

- trả `null` hay collection rỗng khi không có phần tử;
- có cho phép phần tử `null` không;
- tham số `null` có nghĩa gì;
- trạng thái vắng mặt là trạng thái bình thường hay lỗi.

`Optional` có thể hữu ích cho một số hợp đồng trả về về trạng thái vắng mặt, nhưng không phải lựa chọn thay thế tự động cho mọi trường/tham số có thể `null`. Quan trọng nhất là ngữ nghĩa nhất quán và bên gọi không phải đoán.

Chương tiếp theo giải thích kiểu bao và vì sao unboxing một giá trị `null` có thể thất bại.
