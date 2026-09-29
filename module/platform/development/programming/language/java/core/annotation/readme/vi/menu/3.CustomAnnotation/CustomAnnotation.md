# Custom Annotation

Custom annotation cho phép ứng dụng hoặc framework định nghĩa **metadata vocabulary của riêng mình**. Nhưng việc tạo một annotation mới không nên bắt đầu từ câu hỏi “cú pháp `@interface` viết thế nào?”, mà từ câu hỏi:

> Consumer nào sẽ đọc metadata này, và nó cần biết điều gì?

Running example của module:

```java
@Audit(action = "TRANSFER", level = 2)
void transfer() {
}
```

## <a id="declare-annotation">Khai báo annotation type</a>

Custom annotation được khai báo bằng `@interface`:

```java
public @interface Audit {
    String action();
    int level() default 1;
}
```

Mỗi method-like declaration bên trong định nghĩa một **annotation element**.

Annotation interface có semantics riêng, không phải một interface thường chỉ vì cú pháp có chữ `interface`. Nó có superinterface trực tiếp là `java.lang.annotation.Annotation`; developer không tự viết `extends Annotation` để biến một interface thường thành annotation type.

Các element cũng có restrictions riêng:

- không có parameter;
- không có type parameter;
- không có `throws`;
- return type phải thuộc nhóm annotation element type hợp lệ.

Annotation interface bản thân cũng không generic và không khai báo một `extends` clause tùy ý. Element methods có contract chuyên biệt tương đương các accessor metadata, không phải nơi định nghĩa `static`/`default`/`private` behavior như interface thường.

Ví dụ usage:

```java
@Audit(action = "TRANSFER", level = 2)
public void transfer() {
}
```

Compiler kiểm tra tên element, type của value và việc đã cung cấp các element bắt buộc hay chưa.

### Annotation type chỉ định nghĩa schema

Khai báo `@Audit` không tự ghi log:

```text
@Audit declaration
→ định nghĩa metadata schema

@Audit usage
→ gắn metadata cụ thể vào code

consumer
→ đọc metadata và quyết định làm gì
```

Consumer runtime có thể là framework/reflection. Consumer compile-time có thể là annotation processor. Nếu không có consumer, metadata vẫn chỉ là metadata.

## <a id="annotation-elements-defaults">Element và default value</a>

Element không có default là bắt buộc. Giữ nguyên running example: `action` không có default nên bắt buộc, còn `level` có default nên có thể bỏ qua khi sử dụng:

```java
public @interface Audit {
    String action();
    int level() default 1;
}
```

Usage tối thiểu chỉ cần cung cấp `action`:

```java
@Audit(action = "TRANSFER")
```

Có thể khai báo default:

```java
public @interface Audit {
    String action();
    int level() default 1;
    String[] tags() default {};
}
```

Khi đó:

```java
@Audit(action = "TRANSFER")
```

sẽ dùng `level = 1` và `tags = {}`.

### Default không phải `default method`

Từ khóa `default` ở annotation element chỉ định **default metadata value**, không có semantics giống `default method` của interface thường.

Một chi tiết quan trọng: default value thuộc annotation type, không được copy trực tiếp vào từng annotation usage thiếu giá trị đó. Khi annotation được đọc, default hiện tại của annotation type được áp dụng. Vì vậy thay đổi default có thể thay đổi giá trị quan sát được từ class đã compile trước đó nếu usage không ghi value explicit.

Đây là lý do default cũng là một phần của **compatibility contract**.

Annotation element không nhận `null`. Nếu cần trạng thái “không có giá trị”, hãy thiết kế default/sentinel có nghĩa rõ ràng, ví dụ:

```java
// Ví dụ sentinel nếu domain thật sự cần:
// enum AuditLevel { DEFAULT, LOW, HIGH }
```

Tránh các magic value như `"N/A"` hoặc `-1` nếu consumer phải tự đoán semantics.

## <a id="marker-annotation">Marker annotation</a>

Marker annotation là annotation interface **không có element**:

```java
public @interface Audited {
}
```

Usage:

```java
@Audited
class PaymentService {
}
```

Ý nghĩa thường là presence/absence:

```text
có @Audited
→ opt in vào một contract

không có @Audited
→ không opt in
```

Cần phân biệt marker annotation type với **marker syntax**. Một annotation có các element nhưng tất cả đều có default vẫn có thể được viết như:

```java
@Feature
```

nhưng annotation type đó không phải marker thực sự vì schema vẫn có element.

Marker phù hợp khi metadata thật sự chỉ cần một boolean semantic. Nếu bắt đầu xuất hiện nhiều biến thể, nên dùng một schema có nghĩa thay vì tạo hàng loạt marker gần giống nhau.

## <a id="custom-annotation-design">Thiết kế metadata có ý nghĩa</a>

Một custom annotation tốt nên trả lời bốn câu hỏi:

1. **Consumer là ai?** compiler processor, runtime framework hay tooling?
2. **Metadata mô tả điều gì?** policy, mapping, capability hay contract?
3. **Nó hợp lệ ở đâu?** class, method, field, parameter, type use?
4. **Nó cần tồn tại bao lâu?** source, class file hay runtime?

Ví dụ:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Contract này nói rõ:

```text
consumer      → runtime component
use site      → method
lifetime      → phải tồn tại đến runtime
schema        → action + level
```

### Java kiểm tra schema, không kiểm tra toàn bộ business meaning

Compiler có thể biết `level()` là `int` hay một element khác có type cụ thể, nhưng không tự biết:

```text
timeout phải > 0
action phải thuộc naming convention nội bộ
hai element này không được xuất hiện cùng nhau
```

Những business constraints như vậy phải được consumer/processor validate và nên được document rõ.

### Tránh “configuration object bằng annotation”

Annotation nên chứa metadata khai báo tương đối nhỏ và ổn định. Nếu annotation có hàng chục element phụ thuộc lẫn nhau, nhiều sentinel đặc biệt và logic override phức tạp, nó đang trở thành một configuration language khó tiến hóa.

Khi dữ liệu:

- lớn;
- động theo environment;
- cần secret;
- thay đổi thường xuyên mà không muốn recompile;

thì config file, database hoặc runtime object thường phù hợp hơn annotation.

### Chuyển sang retention

Ta đã có metadata schema. Câu hỏi tiếp theo là: **consumer cần metadata tồn tại đến giai đoạn nào?** Đây chính là trách nhiệm của retention policy.
