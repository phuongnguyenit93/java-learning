# Annotation là gì và vì sao Java cần nó?

## <a id="annotation-model">Annotation là gì và tại sao cần nó?</a>

Trước khi học cú pháp `@Something`, hãy trả lời câu hỏi đơn giản hơn:

> **Annotation dùng để làm gì?**

Trong một chương trình, đôi khi ta không muốn viết thêm **logic thực thi**. Ta chỉ muốn **nói thêm một điều về đoạn mã đó để Java hoặc một công cụ khác biết**.

Ví dụ, ta viết một method với ý định override method của class cha:

```java
class Parent {
    void process() {
    }
}

class Child extends Parent {
    void proccess() { // gõ sai tên
    }
}
```

Đoạn mã trên vẫn có thể biên dịch vì Java hiểu `proccess()` là một method mới. Nhưng ý định của lập trình viên đã bị sai mà trình biên dịch không biết.

Nếu ta viết:

```java
class Child extends Parent {
    @Override
    void proccess() {
    }
}
```

trình biên dịch có thêm thông tin:

```text
"Method này được viết với ý định override method của supertype."
```

và có thể báo lỗi ngay.

Đó chính là ý tưởng cốt lõi của annotation:

> **Annotation là một nhãn/thông tin có cấu trúc được gắn trực tiếp vào mã nguồn để trình biên dịch, công cụ, framework hoặc chương trình khác có thể đọc và hiểu thêm điều gì đó về mã nguồn đó.**

Nó không phải là logic của method. Nó là **thông tin về method/class/field/type...**.

Sau khi hiểu ý tưởng này, thuật ngữ kỹ thuật `metadata` (siêu dữ liệu) sẽ dễ hiểu hơn:

> **Metadata (siêu dữ liệu) = dữ liệu mô tả một dữ liệu hoặc một thành phần khác. Annotation chính là một cách Java biểu diễn siêu dữ liệu trên mã nguồn.**

Một ví dụ khác: giả sử hệ thống muốn đánh dấu những thao tác cần audit. Nếu chỉ dựa vào chú thích trong mã:

```java
// audit this method
void transfer() {
}
```

Chú thích trong mã giúp con người đọc, nhưng chương trình không có một quy tắc có cấu trúc để xử lý nội dung đó.

Ta có thể dùng quy ước đặt tên:

```java
void auditTransfer() {
}
```

nhưng lúc này ý nghĩa “cần audit” bị nhét vào tên method. Tên method vừa phải mô tả hành vi, vừa phải mang thêm thông tin cho công cụ.

Annotation tách hai việc đó ra:

```java
@Audit(action = "TRANSFER")
void transfer() {
}
```

```text
transfer()
→ hành vi nghiệp vụ

@Audit(...)
→ thông tin mô tả thêm về transfer()
```

Trong module này, `@Audit` sẽ được dùng làm ví dụ xuyên suốt để quan sát Annotation tiến hóa từ một nhãn đơn giản thành một cấu trúc siêu dữ liệu có quy tắc rõ ràng.

### Bạn cần biết gì trước khi học module này?

Module giả định bạn đã biết cú pháp Java cơ bản như class, method và field. Ví dụ `@Override` dùng khái niệm kế thừa/ghi đè đã học ở OOP; `@SafeVarargs` chạm tới Generics; `@FunctionalInterface` chạm tới lambda.

Nhưng bạn **không cần biết trước**:

- annotation là gì;
- reflection API hoạt động thế nào;
- annotation processor là gì;
- Spring/JPA/JUnit xử lý annotation ra sao.

Những cơ chế liên quan sẽ được giải thích đủ trong đúng ranh giới của module. Khi cần kiến thức sâu hơn, nội dung sẽ chỉ rõ module nào sở hữu phần đó.

### CỦNG CỐ KHÁI NIỆM — đọc Annotation như thông tin mô tả có cấu trúc

Một annotation thường có dạng:

```java
@Something
class MyClass {
}
```

Đọc nó theo mô hình tư duy:

```text
mã nguồn chính
→ MyClass

annotation
→ một mẩu thông tin bổ sung nói điều gì đó về MyClass
```

Ví dụ:

```java
@Deprecated
class LegacyPayment {
}
```

Ý nghĩa không phải:

```text
@Deprecated
→ tự động xóa class
```

mà là:

```text
LegacyPayment
→ vẫn là một class bình thường

@Deprecated
→ nói thêm rằng class này không còn được khuyến nghị sử dụng

trình biên dịch / IDE / công cụ tài liệu
→ đọc thông tin đó
→ cảnh báo hoặc hiển thị phù hợp
```

### VÌ SAO — nếu không có annotation thì sao?

Ta vẫn có thể giải quyết một phần bài toán bằng những cách khác:

| Cách | Làm được gì? | Điểm yếu |
| --- | --- | --- |
| Chú thích trong mã | Giải thích cho con người | Trình biên dịch/framework không có cấu trúc chuẩn để hiểu |
| Quy ước đặt tên | Công cụ có thể tự phân tích tên | Trộn siêu dữ liệu vào tên nghiệp vụ, dễ lệch quy ước |
| Tệp cấu hình/bảng ánh xạ riêng | Tách siêu dữ liệu khỏi mã nguồn | Siêu dữ liệu dễ lệch khỏi mã nguồn khi tái cấu trúc |
| Annotation | Siêu dữ liệu nằm sát mã nguồn, có cấu trúc và được Java/công cụ hiểu | Chỉ phù hợp với siêu dữ liệu tương đối tĩnh và có thành phần đọc rõ ràng |

Annotation tồn tại vì có rất nhiều tình huống ta cần hỏi:

```text
"Đoạn mã này là gì / có đặc điểm gì?"
```

chứ không phải:

```text
"Đoạn mã này phải thực thi câu lệnh gì?"
```

Ví dụ:

```text
@Override
→ method này có ý định override

@Deprecated
→ API này không còn được khuyến nghị sử dụng

@Test
→ method này là một test mà test framework cần chạy

@GetMapping("/users")
→ method này được framework xem như bộ xử lý cho một đường dẫn HTTP
```

Hai ví dụ cuối đến từ framework/thư viện bên ngoài JDK, nhưng chúng cho thấy tại sao Annotation xuất hiện rất nhiều trong ứng dụng Java: **framework có thể đọc siêu dữ liệu thay vì bắt lập trình viên tự đăng ký mọi thứ bằng mã nguồn thủ công**.

### AI ĐỌC ANNOTATION?

Annotation tự nó không làm gì cả. Luôn có một **thành phần đọc** nó.

Thành phần đọc có thể là:

- trình biên dịch Java, ví dụ với `@Override`;
- IDE hoặc công cụ tài liệu;
- annotation processor chạy khi biên dịch;
- framework đọc annotation bằng reflection;
- mã ứng dụng tự đọc Annotation tại thời gian chạy.

Mô hình tư duy quan trọng nhất của cả module:

```text
mã nguồn
  +
annotation
  ↓
thành phần đọc Annotation
  ↓
thành phần đọc kiểm tra / sinh mã / cảnh báo / đăng ký / tạo hành vi lúc chạy
```

> **Annotation mô tả. Thành phần đọc mới là thứ làm điều gì đó với mô tả đó.**

Đây là lý do nhìn thấy `@Something` chưa đủ để biết nó “làm gì”. Muốn biết chính xác, phải biết **ai là thành phần đọc Annotation đó**.

### RANH GIỚI — annotation không phải AOP hay reflection

Annotation thường xuất hiện cùng Spring, JPA, validation, AOP hoặc reflection, nhưng chúng không đồng nghĩa.

```text
annotation
→ siêu dữ liệu

reflection
→ một cơ chế tại thời gian chạy có thể đọc một số siêu dữ liệu

annotation processing
→ cơ chế tại thời điểm biên dịch có thể đọc siêu dữ liệu và sinh/kiểm tra đầu ra

framework/AOP
→ thành phần đọc có thể dùng siêu dữ liệu để quyết định hành vi
```

Module này sở hữu ngữ nghĩa Annotation. Reflection sâu hơn thuộc module `reflection`.

### MỐI LIÊN HỆ — các chương phía sau giải quyết câu hỏi gì?

Sau khi biết Annotation là **siêu dữ liệu gắn lên mã nguồn cho một thành phần đọc**, toàn bộ module chỉ còn là một chuỗi câu hỏi tự nhiên:

```text
Annotation trông như thế nào và mang được giá trị gì?
→ Cú pháp / phần tử Annotation
        ↓
Java đã có sẵn những Annotation và quy tắc nào?
→ Built-in annotations
        ↓
Muốn tạo siêu dữ liệu của riêng mình thì sao?
→ Custom annotation
        ↓
Siêu dữ liệu phải tồn tại đến lúc nào?
→ Retention
        ↓
Annotation được phép đặt ở đâu?
→ Target
        ↓
Kiểu Annotation tự mô tả quy tắc của nó bằng cách nào?
→ Meta-annotations
        ↓
Nếu cùng annotation xuất hiện nhiều lần hoặc đi qua superclass thì sao?
→ Repeatable / Inherited
        ↓
Công cụ tại thời điểm biên dịch có thể đọc Annotation để sinh/kiểm tra mã nguồn thế nào?
→ Annotation Processing
        ↓
Khi nào Annotation là lựa chọn phù hợp và khi nào nên dùng cơ chế khác?
→ Khi nào nên dùng Annotation?
```

Kết thúc module, bạn cần nhìn một Annotation bất kỳ và biết hỏi:

```text
1. Nó đang mô tả điều gì?
2. Ai là thành phần đọc?
3. Nó được đặt ở đâu?
4. Nó tồn tại đến giai đoạn nào?
5. Thành phần đọc sẽ làm gì với siêu dữ liệu đó?
6. Annotation có thực sự là cách biểu diễn rõ ràng nhất cho bài toán này không?
```

## <a id="annotation-syntax">Cú pháp và phần tử Annotation</a>

Một lần sử dụng Annotation bắt đầu bằng `@` và tên Annotation:

```java
@Audit(action = "TRANSFER", level = 2)
void transfer() {
}
```

`action` và `level` là **phần tử Annotation (annotation elements)**. Chúng giống các giá trị có tên trong cấu trúc của Annotation, không phải field có thể thay đổi tùy ý.

Các dạng cú pháp thường gặp:

```java
@Audit(action = "TRANSFER", level = 2) // nhiều phần tử
@Role("ADMIN")                         // cách viết gọn cho phần tử tên value
@Transactional                         // cách viết dạng marker
```

Nếu Annotation có đúng một phần tử cần truyền và phần tử đó tên `value`, có thể bỏ `value =`:

```java
@Role(value = "ADMIN")
@Role("ADMIN")
```

Hai cách trên tương đương.

Giá trị của Annotation phải hợp lệ theo type của phần tử và theo quy tắc tại thời điểm biên dịch của Java. Ví dụ:

```java
@Retry(maxAttempts = 3)
```

Nếu phần tử `maxAttempts()` có type `int`, truyền một `String` sẽ bị trình biên dịch từ chối.

### Annotation lồng nhau và mảng

Phần tử có thể chứa Annotation khác hoặc mảng:

```java
@Route(
    path = "/orders",
    roles = {"USER", "ADMIN"},
    cache = @CachePolicy(seconds = 30)
)
```

Với mảng một phần tử, Java cho phép bỏ `{}` khi sử dụng Annotation:

```java
@Roles({"ADMIN"})
@Roles("ADMIN")
```

Đây chỉ là cú pháp viết gọn; mô hình của phần tử vẫn là mảng.

### Chuyển sang các Annotation có sẵn

Sau khi đã hiểu Annotation là siêu dữ liệu có cấu trúc và cần một thành phần đọc, bước tiếp theo là nhìn vào những Annotation Java cung cấp sẵn. Những Annotation như `@Override` cho thấy ý định của lập trình viên có thể được biến thành điều trình biên dịch kiểm tra được.
