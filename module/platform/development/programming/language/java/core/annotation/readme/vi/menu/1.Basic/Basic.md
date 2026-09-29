# Annotation cơ bản

Trước khi học cú pháp `@Something`, hãy trả lời câu hỏi đơn giản hơn:

> **Annotation dùng để làm gì?**

Trong một chương trình, đôi khi ta không muốn viết thêm **logic thực thi**. Ta chỉ muốn **nói thêm một điều về đoạn code đó để Java hoặc một công cụ khác biết**.

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

Đoạn code trên vẫn có thể compile vì Java hiểu `proccess()` là một method mới. Nhưng ý định của developer đã bị sai mà compiler không biết.

Nếu ta viết:

```java
class Child extends Parent {
    @Override
    void proccess() {
    }
}
```

compiler có thêm thông tin:

```text
"Method này được viết với ý định override method của supertype."
```

và có thể báo lỗi ngay.

Đó chính là ý tưởng cốt lõi của annotation:

> **Annotation là một nhãn/thông tin có cấu trúc được gắn trực tiếp vào code để compiler, tool, framework hoặc chương trình khác có thể đọc và hiểu thêm điều gì đó về code đó.**

Nó không phải là logic của method. Nó là **thông tin về method/class/field/type...**.

Sau khi hiểu ý tưởng này, từ kỹ thuật `metadata` sẽ dễ hiểu hơn:

> **Metadata = dữ liệu mô tả một dữ liệu hoặc một thành phần khác. Annotation chính là một cách Java biểu diễn metadata trên code.**

Một ví dụ khác: giả sử hệ thống muốn đánh dấu những thao tác cần audit. Nếu chỉ dựa vào comment:

```java
// audit this method
void transfer() {
}
```

comment giúp con người đọc, nhưng chương trình không có một contract chuẩn để xử lý comment đó.

Ta có thể dùng naming convention:

```java
void auditTransfer() {
}
```

nhưng lúc này ý nghĩa “cần audit” bị nhét vào tên method. Tên method vừa phải mô tả hành vi, vừa phải mang thêm thông tin cho tooling.

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

Trong module này, `@Audit` sẽ được dùng làm running example để quan sát annotation tiến hóa từ một nhãn đơn giản thành một metadata contract hoàn chỉnh.

### Bạn cần biết gì trước khi học module này?

Module giả định bạn đã biết Java syntax cơ bản như class, method và field. Ví dụ `@Override` dùng khái niệm inheritance/overriding đã học ở OOP; `@SafeVarargs` chạm tới generics; `@FunctionalInterface` chạm tới lambda.

Nhưng bạn **không cần biết trước**:

- annotation là gì;
- reflection API hoạt động thế nào;
- annotation processor là gì;
- Spring/JPA/JUnit xử lý annotation ra sao.

Những cơ chế liên quan sẽ được giải thích đủ ở đúng boundary của module. Khi cần kiến thức sâu hơn, nội dung sẽ chỉ rõ module sở hữu phần đó.

## <a id="annotation-model">Annotation là gì và tại sao cần nó?</a>

### KHÁI NIỆM — hãy hiểu annotation trước, metadata sau

Một annotation thường có dạng:

```java
@Something
class MyClass {
}
```

Đọc nó theo mental model:

```text
code chính
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

compiler / IDE / documentation tool
→ đọc thông tin đó
→ cảnh báo hoặc hiển thị phù hợp
```

### VÌ SAO — nếu không có annotation thì sao?

Ta vẫn có thể giải quyết một phần bài toán bằng những cách khác:

| Cách | Làm được gì? | Điểm yếu |
| --- | --- | --- |
| Comment | Giải thích cho con người | Compiler/framework không có contract chuẩn để hiểu |
| Naming convention | Tool có thể tự parse tên | Trộn metadata vào tên business, dễ sai convention |
| File config/map riêng | Tách metadata khỏi code | Metadata dễ lệch khỏi code khi refactor |
| Annotation | Metadata nằm sát code, có schema và được Java/tool hiểu | Chỉ phù hợp với metadata tương đối tĩnh và có consumer rõ ràng |

Annotation tồn tại vì có rất nhiều tình huống ta cần hỏi:

```text
"Đoạn code này là gì / có đặc điểm gì?"
```

chứ không phải:

```text
"Đoạn code này phải thực thi câu lệnh gì?"
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
→ method này được framework xem như handler cho một HTTP route
```

Hai ví dụ cuối đến từ framework/library bên ngoài JDK, nhưng chúng cho thấy tại sao annotation xuất hiện rất nhiều trong Java application: **framework có thể đọc metadata thay vì bắt developer tự đăng ký mọi thứ bằng code thủ công**.

### AI ĐỌC ANNOTATION?

Annotation tự nó không làm gì cả. Luôn có một **consumer** đọc nó.

Consumer có thể là:

- Java compiler, ví dụ với `@Override`;
- IDE hoặc documentation tool;
- annotation processor chạy khi compile;
- framework đọc annotation bằng reflection;
- code ứng dụng tự đọc annotation runtime.

Mental model quan trọng nhất của cả module:

```text
code
  +
annotation
  ↓
consumer đọc annotation
  ↓
consumer kiểm tra / sinh code / cảnh báo / đăng ký / tạo runtime behavior
```

> **Annotation mô tả. Consumer mới là thứ làm điều gì đó với mô tả đó.**

Đây là lý do nhìn thấy `@Something` chưa đủ để biết nó “làm gì”. Muốn biết chính xác, phải biết **ai là consumer của annotation đó**.

### KHI NÀO NÊN DÙNG ANNOTATION?

Annotation phù hợp khi thông tin:

- gắn tự nhiên với class/method/field/type;
- tương đối ổn định;
- mang tính khai báo hơn là thuật toán;
- cần compiler/tool/framework đọc;
- nên nằm gần code để refactor cùng code.

Ví dụ:

```text
method này là test
class này ánh xạ tới một loại dữ liệu
field này có validation rule
method này cần audit
API này đã deprecated
```

### KHI NÀO KHÔNG NÊN DÙNG?

Không nên biến annotation thành nơi chứa:

- business logic phức tạp;
- dữ liệu thay đổi liên tục ở runtime;
- secret;
- configuration lớn phụ thuộc environment;
- object graph động.

Nếu một thông tin phù hợp hơn với object, database hoặc config file thì nên dùng đúng công cụ đó.

### RANH GIỚI — annotation không phải AOP hay reflection

Annotation thường xuất hiện cùng Spring, JPA, validation, AOP hoặc reflection, nhưng chúng không đồng nghĩa.

```text
annotation
→ metadata

reflection
→ một cơ chế runtime có thể đọc một số metadata

annotation processing
→ cơ chế compile-time có thể đọc metadata và sinh/kiểm tra artifact

framework/AOP
→ consumer có thể dùng metadata để quyết định behavior
```

Module này sở hữu annotation semantics. Reflection sâu hơn thuộc module `reflection`.

### MỐI LIÊN HỆ — các chương phía sau giải quyết câu hỏi gì?

Sau khi biết annotation là **metadata gắn lên code cho một consumer đọc**, toàn bộ module chỉ còn là một chuỗi câu hỏi tự nhiên:

```text
Annotation trông như thế nào và mang được giá trị gì?
→ Syntax / annotation elements
        ↓
Java đã có sẵn những annotation contract nào?
→ Built-in annotations
        ↓
Muốn tạo metadata của riêng mình thì sao?
→ Custom annotation
        ↓
Metadata phải tồn tại đến lúc nào?
→ Retention
        ↓
Annotation được phép đặt ở đâu?
→ Target
        ↓
Annotation type tự mô tả contract của nó bằng cách nào?
→ Meta-annotations
        ↓
Nếu cùng annotation xuất hiện nhiều lần hoặc đi qua superclass thì sao?
→ Repeatable / Inherited
        ↓
Tool compile-time có thể đọc annotation để sinh/kiểm tra code thế nào?
→ Annotation Processing
```

Kết thúc module, bạn cần nhìn một annotation bất kỳ và biết hỏi:

```text
1. Nó đang mô tả điều gì?
2. Ai là consumer?
3. Nó được đặt ở đâu?
4. Nó tồn tại đến giai đoạn nào?
5. Consumer sẽ làm gì với metadata đó?
```

## <a id="annotation-syntax">Cú pháp và annotation element</a>

Một annotation usage bắt đầu bằng `@` và tên annotation:

```java
@Audit(action = "TRANSFER", level = 2)
void transfer() {
}
```

`action` và `level` là **annotation elements**. Chúng giống các giá trị có tên trong schema của annotation, không phải field mutable.

Các dạng syntax thường gặp:

```java
@Audit(action = "TRANSFER", level = 2) // nhiều element
@Role("ADMIN")                         // shorthand cho element tên value
@Transactional                         // marker-style usage
```

Nếu annotation có đúng một element cần truyền và element đó tên `value`, có thể bỏ `value =`:

```java
@Role(value = "ADMIN")
@Role("ADMIN")
```

Hai cách trên tương đương.

Giá trị của annotation phải là giá trị hợp lệ theo type của element và theo quy tắc compile-time của Java. Ví dụ:

```java
@Retry(maxAttempts = 3)
```

Nếu `maxAttempts()` có type `int`, truyền một `String` sẽ bị compiler từ chối.

### Annotation lồng nhau và array

Element có thể chứa annotation khác hoặc array:

```java
@Route(
    path = "/orders",
    roles = {"USER", "ADMIN"},
    cache = @CachePolicy(seconds = 30)
)
```

Với array một phần tử, Java cho phép bỏ `{}` trong annotation usage:

```java
@Roles({"ADMIN"})
@Roles("ADMIN")
```

Đây chỉ là shorthand syntax; model của element vẫn là array.

## <a id="annotation-restrictions">Kiểu dữ liệu được phép</a>

Annotation không cho phép element mang bất kỳ object nào. Java giới hạn element type để metadata có thể biểu diễn ổn định trong class-file/source model.

Các nhóm type hợp lệ là:

- primitive type như `int`, `boolean`, `double`;
- `String`;
- `Class` hoặc dạng parameterized của `Class`, ví dụ `Class<? extends Handler>`;
- enum type;
- annotation type khác;
- array một chiều của một trong các type hợp lệ trên.

Không chỉ return type bị giới hạn; **giá trị được ghi trong annotation usage cũng phải là dạng Java có thể biểu diễn như annotation value ở compile time**: constant expression phù hợp, class literal, enum constant, nested annotation hoặc array các giá trị hợp lệ. Không thể dùng `new SomeObject()` hay gọi một method tùy ý để tính annotation value.

Ví dụ:

```java
public @interface EndpointPolicy {
    String name();
    int timeoutSeconds() default 30;
    Class<? extends Runnable> handler();
    Mode mode() default Mode.SYNC;
    Tag tag() default @Tag("default");
    String[] roles() default {};
}
```

Các dạng sau không hợp lệ:

```java
public @interface Invalid {
    // Object value();          // không hợp lệ
    // List<String> names();   // không hợp lệ
    // String[][] matrix();    // nested array không hợp lệ
}
```

Annotation element cũng không dùng `null` làm giá trị. Nếu domain cần biểu diễn “không có giá trị”, annotation contract phải thiết kế một default/sentinel rõ ràng hoặc tách metadata thành cấu trúc khác.

### Annotation element không được tạo dependency cycle

Mặc dù annotation type khác là một element type hợp lệ, annotation interface không được tự tham chiếu qua element theo kiểu trực tiếp hoặc gián tiếp:

```java
// Không hợp lệ: tự tham chiếu trực tiếp
@interface A {
    A value();
}

// Không hợp lệ: cycle A -> B -> A
@interface B {
    C value();
}

@interface C {
    B value();
}
```

Lý do là schema annotation phải có một cấu trúc hữu hạn mà compiler/class-file model có thể biểu diễn; cyclic annotation element declarations là compile-time error.

### Element không được đụng contract của `Object` / `Annotation`

Annotation element nhìn giống method không tham số, nhưng không được có signature override-equivalent với method `public` hoặc `protected` của `Object` hay `java.lang.annotation.Annotation`.

Ví dụ:

```java
public @interface InvalidMetadata {
    // int hashCode();          // không hợp lệ
    // String toString();       // không hợp lệ
    // Class annotationType();  // không hợp lệ
}
```

Các method như `equals(...)`, `hashCode()`, `toString()` và `annotationType()` đã thuộc contract chung của annotation instance; chúng không phải metadata element mà annotation author được định nghĩa lại.

### Tại sao giới hạn này quan trọng?

Annotation metadata không phải một object graph runtime tùy ý. Nó phải có thể được compiler ghi, tool đọc và class loader/runtime biểu diễn theo format đã định nghĩa. Vì vậy annotation phù hợp với **metadata khai báo nhỏ, ổn định**, không phù hợp để chứa business object phức tạp.

## <a id="annotation-use-sites">Declaration và type-use</a>

Một khác biệt quan trọng là annotation có thể mô tả **declaration** hoặc **type được sử dụng**.

Declaration annotation gắn metadata vào phần tử được khai báo:

```java
@ComponentInfo
class PaymentService {

    @Audit(action = "PAYMENT")
    void pay(@RequestId String request) {
    }
}
```

Mỗi annotation type có thể có target khác nhau: `@ComponentInfo` cho class, `@Audit` cho method, `@RequestId` cho parameter, hoặc nhiều context nếu contract cho phép.

Type-use annotation gắn metadata vào **một lần sử dụng type**:

```java
List<@NonNull String> names;

@NonNull String loadName() {
    return "Java";
}

String @NonNull [] values;
```

Đây là nền tảng cho type checker hoặc static-analysis tool có thể diễn đạt các thuộc tính như nullness trên chính type use thay vì chỉ trên declaration.

Mental model:

```text
declaration annotation
→ mô tả declaration

TYPE_USE annotation
→ mô tả type ở vị trí nó đang được sử dụng
```

Nếu một annotation được target sao cho cùng một source occurrence hợp lệ ở cả declaration context và type context, occurrence đó có thể mang cả hai vai trò theo rule của Java. Vì vậy runtime reflection tách hai hướng quan sát: declaration annotations thường đi qua `AnnotatedElement`, còn type annotations đi qua họ `AnnotatedType`.

Việc annotation **được phép xuất hiện ở đâu** không do nơi sử dụng tự quyết định. Nó được kiểm soát bởi meta-annotation `@Target`, sẽ được học ở các chapter sau.

### Chuyển sang built-in annotations

Sau khi đã hiểu annotation là metadata có schema và cần consumer, bước tiếp theo là nhìn vào những contract Java cung cấp sẵn. Những built-in annotations như `@Override` cho thấy một annotation có thể biến ý định của developer thành điều compiler kiểm tra được.
