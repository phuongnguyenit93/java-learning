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

### `null` khác empty/default domain value

```text
null        → không có object được reference nhận diện
""          → có String object/value rỗng
new int[0]  → có array object, length = 0
0           → primitive int value hợp lệ
```

Các trạng thái này có semantics khác nhau. Dùng `null` để biểu diễn nhiều ý nghĩa như "không tìm thấy", "chưa load", "không áp dụng", "lỗi" cùng lúc sẽ làm contract khó hiểu.

Field reference mặc định là `null`, nhưng local reference vẫn cần definite assignment trước khi đọc.

## <a id="null-dereference">Dereference null</a>

Nếu mã cố dùng `null` như một object:

```java
name.length();
```

runtime throw `NullPointerException`.

NPE thường không phải vấn đề “Java có null”, mà là ranh giới/hợp đồng không nói rõ value có thể vắng mặt hay mã không kiểm tra invariant cần thiết.

Nhiều thao tác có thể dereference gián tiếp:

```java
user.getName();          // gọi instance method
user.name;               // đọc instance field
users[0].getName();      // element có thể null

Integer count = null;
int x = count;           // unboxing cũng có thể gây NPE
```

Array reference null và array element null là hai chuyện khác:

```java
String[] a = null;       // không có array object
String[] b = new String[1]; // có array, nhưng b[0] == null
```

Debug NPE nên hỏi **reference nào đang null tại dereference boundary**, không chỉ thêm null check ngẫu nhiên ở xa nguồn.

## <a id="null-comparison">So sánh với null</a>

Dùng `==`/`!=` để kiểm tra null identity:

```java
if (user != null) {
    user.run();
}
```

Gọi `user.equals(null)` là sai hướng vì nếu `user` chính là null thì đã dereference trước khi vào `equals`.

Short-circuit `&&` thường kết hợp tự nhiên với null guard.

`instanceof` với null trả `false`:

```java
Object value = null;
System.out.println(value instanceof String); // false
```

Khi cần value equality với object có thể null, utility như `Objects.equals(a, b)` có thể diễn đạt intent rõ hơn việc tự viết nhiều branch, nhưng hãy vẫn hiểu contract của domain thay vì che mọi null một cách máy móc.

## <a id="null-api-design">Nullability trong API Design</a>

API nên làm rõ:

- parameter có chấp nhận null không;
- return có thể null không;
- collection có chứa null không;
- null có nghĩa “không có”, “chưa tải”, hay “không hợp lệ”.

Không phải mọi absence đều cần `Optional`, nhưng hợp đồng mơ hồ về null làm bên gọi phải đoán và tạo NPE xa nguồn gốc.

### Fail fast khi null không hợp lệ

```java
UserService(UserRepository repository) {
    this.repository = Objects.requireNonNull(repository);
}
```

Nếu null vi phạm invariant, validate sớm giúp lỗi xuất hiện gần boundary gây ra nó.

### Null, empty và absence phải có contract

API collection thường nên quyết định rõ:

- trả `null` hay collection rỗng khi không có phần tử;
- có cho phép element null không;
- parameter null có nghĩa gì;
- absence là normal state hay error.

`Optional` có thể hữu ích cho một số return contract về absence, nhưng không phải replacement tự động cho mọi nullable field/parameter. Quan trọng nhất là semantics nhất quán và caller không phải đoán.

chương cuối ghép các phần đã học thành một mô hình tư duy về **compile-time type, runtime type và những guarantee nằm ở đâu**.
