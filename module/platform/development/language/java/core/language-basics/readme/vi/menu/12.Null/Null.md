# null

`null` là một reference value đặc biệt biểu diễn rằng **reference hiện không nhận diện object nào**. Nó không phải object, không phải empty String và không phải primitive default chung.

## <a id="null-reference">null là Reference Value</a>

Reference variable có thể giữ `null` nếu type/ngữ cảnh cho phép:

```java
String name = null;
```

Primitive variable không thể giữ `null`:

```java
int value = null; // không compile
```

Wrapper như `Integer` là reference type nên có thể `null`, tạo thêm rủi ro khi unboxing.

## <a id="null-dereference">Dereference null</a>

Nếu mã cố dùng `null` như một object:

```java
name.length();
```

runtime throw `NullPointerException`.

NPE thường không phải vấn đề “Java có null”, mà là ranh giới/hợp đồng không nói rõ value có thể vắng mặt hay mã không kiểm tra invariant cần thiết.

## <a id="null-comparison">So sánh với null</a>

Dùng `==`/`!=` để kiểm tra null identity:

```java
if (user != null) {
    user.run();
}
```

Gọi `user.equals(null)` là sai hướng vì nếu `user` chính là null thì đã dereference trước khi vào `equals`.

Short-circuit `&&` thường kết hợp tự nhiên với null guard.

## <a id="null-api-design">Nullability trong API Design</a>

API nên làm rõ:

- parameter có chấp nhận null không;
- return có thể null không;
- collection có chứa null không;
- null có nghĩa “không có”, “chưa tải”, hay “không hợp lệ”.

Không phải mọi absence đều cần `Optional`, nhưng hợp đồng mơ hồ về null làm bên gọi phải đoán và tạo NPE xa nguồn gốc.

chương cuối ghép các phần đã học thành một mô hình tư duy về **compile-time type, runtime type và những guarantee nằm ở đâu**.
