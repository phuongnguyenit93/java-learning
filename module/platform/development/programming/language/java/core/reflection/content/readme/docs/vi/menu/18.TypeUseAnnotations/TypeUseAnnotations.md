<a id="back-to-top"></a>

# Annotation trên kiểu

## Menu
- [Phân biệt declaration annotation với type-use annotation](#type-use-annotations-001)
- [AnnotatedType và kiểu nền bên dưới](#type-use-annotations-002)
- [AnnotatedParameterizedType và annotation trên đối số kiểu](#type-use-annotations-003)
- [AnnotatedArrayType và annotation ở các chiều mảng](#type-use-annotations-004)
- [AnnotatedWildcardType và annotation trên bound](#type-use-annotations-005)
- [AnnotatedTypeVariable và các bound có annotation](#type-use-annotations-006)
- [getAnnotatedReturnType, getAnnotatedExceptionTypes và receiver type](#type-use-annotations-007)
- [Annotation trên superclass và interface được khai báo](#type-use-annotations-008)
- [Xây dựng trình duyệt cây kiểu mà vẫn giữ vị trí annotation](#type-use-annotations-009)

## <a id="type-use-annotations-001">Phân biệt declaration annotation với type-use annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt declaration annotation với type-use annotation trong Java Reflection

## 1. Vì sao cần phân biệt hai nơi gắn annotation?

Trong Java, annotation có thể mô tả **một khai báo** (*declaration*) hoặc **một vị trí sử dụng kiểu** (*type use*). Nhìn qua source, hai annotation đều bắt đầu bằng ký hiệu `@`, nhưng ý nghĩa và đường truy cập qua Reflection khác nhau.

Ví dụ:

```java
@Decl("field")
private @Use("type") List<@Use("element") String> names;
```

Đây là đoạn mã minh họa **nằm trong một class**, giả định `Decl` được phép đặt trên khai báo field, `Use` được phép đặt trên type use, và các annotation có retention phù hợp.

- `@Decl("field")` mô tả **khai báo field `names`**.
- `@Use("type")` mô tả **kiểu `List<...>` mà field sử dụng**.
- `@Use("element")` mô tả **kiểu đối số generic `String`** bên trong `List`.

Nếu chỉ gọi `field.getDeclaredAnnotations()`, chương trình có thể thấy declaration annotation mà **không** thấy annotation trên type-use. Muốn tìm type-use annotation, phải lấy cấu trúc `AnnotatedType` qua `field.getAnnotatedType()` và, khi cần, đi sâu vào type argument.

Bài học hướng tới ba năng lực:

1. Phân biệt vị trí và ngữ nghĩa của *declaration annotation* với *type-use annotation*.
2. Chọn đúng `@Target`, `@Retention` và API Reflection để đọc annotation ở Java 17+.
3. Không hiểu nhầm metadata runtime thành cơ chế kiểm tra kiểu tự động hoặc coi các loại annotation là hoàn toàn loại trừ nhau.

## 2. Declaration annotation là gì?

*Declaration annotation* gắn với **một khai báo chương trình**: class, interface, method, constructor, field, formal parameter, type parameter, v.v. Nó thường cung cấp metadata cho compiler, công cụ build hoặc framework.

Một số ví dụ quen thuộc:

- `@Override` trên khai báo method: compiler kiểm tra method có override/satisfy đúng hợp đồng hay không.
- `@Deprecated` trên khai báo class/member: mô tả API không còn khuyến nghị sử dụng.
- `@Entity` (trong thư viện ORM) trên class: một ví dụ về annotation mô tả declaration.
- Một annotation `@Route` tùy chỉnh trên method: metadata về điểm cuối.
- `@Qualifier` hoặc annotation tùy chỉnh trên parameter declaration: mô tả cách injection.

Annotation được phép đặt ở vị trí nào do meta-annotation `@Target` quy định.

| `ElementType` | Phạm vi khai báo điển hình |
| --- | --- |
| `TYPE` | Khai báo class, interface, enum, record và những kiểu tương ứng |
| `FIELD` | Khai báo field |
| `METHOD` | Khai báo method |
| `CONSTRUCTOR` | Khai báo constructor |
| `PARAMETER` | Khai báo formal parameter |
| `TYPE_PARAMETER` | Khai báo tham số kiểu generic như `T` |
| `LOCAL_VARIABLE` | Khai báo biến cục bộ |
| `ANNOTATION_TYPE` | Khai báo một annotation type |
| `PACKAGE` | Khai báo package |
| `MODULE` | Khai báo module |

Các mục trong bảng là một số `ElementType` liên quan đến đề bài; Java còn có `TYPE_USE` dành cho những vị trí sử dụng kiểu. Đừng hiểu `TYPE` và `TYPE_USE` là hai cách viết đồng nghĩa.

Định nghĩa annotation dành riêng cho khai báo field:

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ColumnInfo {
    String value();
}
```

Dùng trên field:

```java
class Book {
    @ColumnInfo("book_title")
    private String title;
}
```

Sau khi compile, Reflection có thể tìm:

```java
java.lang.reflect.Field f = Book.class.getDeclaredField("title");
ColumnInfo info = f.getDeclaredAnnotation(ColumnInfo.class);
System.out.println(info.value()); // book_title
```

Đoạn truy vấn Reflection là **mã minh họa trong một phương thức** đã xử lý/khai báo `NoSuchFieldException`. Vì `ColumnInfo` có retention `RUNTIME` và gắn trên *field declaration*, đọc annotation trực tiếp từ `Field` là hợp lý.

## 3. Type-use annotation là gì?

*Type-use annotation* gắn với **một lần dùng kiểu** (*use of a type*): kiểu của field, kiểu trả về, kiểu parameter, kiểu đối số generic, thành phần mảng, kiểu superclass/interface, wildcard bound, cast, `new` expression và những ngữ cảnh type use khác theo luật Java.

Định nghĩa:

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface MarkType {
    String value();
}
```

Các vị trí minh họa:

```java
class Basket {
    private @MarkType("field-type") String title;

    private java.util.List<@MarkType("element") String> labels;

    public @MarkType("return-type") String find(
            @MarkType("parameter-type") String key) {
        return key;
    }
}
```

Trong ví dụ, các dấu `@MarkType` áp dụng vào **vị trí kiểu** chứ không đơn giản là annotation của `Field` hoặc `Method`. Nếu muốn đọc `@MarkType("element")` trên `String` trong `List<String>`, phải đi vào cấu trúc `AnnotatedParameterizedType`.

Điểm quan trọng:

- `@Target(TYPE_USE)` cho phép annotation xuất hiện ở các **ngữ cảnh type use**; nó không biến annotation thành modifier `public`, `private` hay một kiểu mới tại runtime.
- `String` và `@MarkType String` vẫn dùng cùng kiểu Java `String` về mặt runtime class identity. Annotation chỉ thêm metadata.
- Compiler hoặc công cụ phân tích static có thể dùng type-use annotations để kiểm tra nullness, ownership, thread-safety, v.v., **nếu** có bộ kiểm tra hỗ trợ. Java/JVM không tự biết `@NonNull` tùy chỉnh phải chặn null ra sao.
- Một annotation có `@Retention(RUNTIME)` có thể được Reflection đọc tại **những vị trí type use mà API Reflection biểu diễn được**. Không phải mọi annotation trong mọi biểu thức source đều có API Reflection mức cao để truy vấn.

## 4. Một bảng đối chiếu vị trí và API

| Điều muốn quan sát | Đối tượng Reflection | API chính |
| --- | --- | --- |
| Annotation trên khai báo class | `Class<?>` | `getDeclaredAnnotations()`, `getDeclaredAnnotation(T.class)` |
| Annotation trên khai báo field | `Field` | `getDeclaredAnnotations()`, `getDeclaredAnnotation(T.class)` |
| Annotation trên **kiểu của field** | `Field` → `AnnotatedType` | `field.getAnnotatedType().getAnnotations()` |
| Annotation trên khai báo method | `Method` | `method.getDeclaredAnnotations()` |
| Annotation trên **kiểu trả về method** | `Method` → `AnnotatedType` | `method.getAnnotatedReturnType().getAnnotations()` |
| Annotation trên khai báo parameter | `Parameter` | `parameter.getAnnotations()` hoặc `getDeclaredAnnotations()` |
| Annotation trên **kiểu của parameter** | `Parameter` → `AnnotatedType` | `parameter.getAnnotatedType().getAnnotations()` |
| Annotation trên kiểu exception trong `throws` | `Executable`/`Method` | `getAnnotatedExceptionTypes()` |
| Annotation trên kiểu superclass | `Class<?>` | `getAnnotatedSuperclass()` |
| Annotation trên kiểu implemented interfaces | `Class<?>` | `getAnnotatedInterfaces()` |
| Annotation trên đối số generic như `List<@T String>` | `AnnotatedParameterizedType` | `getAnnotatedActualTypeArguments()` |
| Annotation trên thành phần kiểu mảng | `AnnotatedArrayType` | `getAnnotatedGenericComponentType()` |
| Annotation trên bound của wildcard | `AnnotatedWildcardType` | `getAnnotatedUpperBounds()`/`getAnnotatedLowerBounds()` |
| Annotation trên bound của type variable | `TypeVariable<?>` | `getAnnotatedBounds()` |
| Annotation trên khai báo type parameter `T` | `TypeVariable<?>` | `getAnnotations()`/`getDeclaredAnnotations()` |

**Không nhầm:** `Field.getGenericType()` trả về `Type` có thông tin về generic signature nhưng **không đủ** để lấy toàn bộ annotation nằm trên từng phần của kiểu. Khi cần annotation, dùng họ `getAnnotated...` và các subtype của `AnnotatedType`.

## 5. Ví dụ hoàn chỉnh Java 17+: một annotation gắn declaration, một annotation gắn type use

Lưu nguyên nội dung sau vào tệp **`AnnotationLocationDemo.java`**. Chương trình dùng hai annotation độc lập:

- `@Decl`: chỉ áp dụng trên các **declaration** như class, field, method và parameter.
- `@Use`: chỉ khai báo `@Target(TYPE_USE)` để đánh dấu những **vị trí sử dụng kiểu**.

```java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD,
         ElementType.METHOD, ElementType.PARAMETER})
@interface Decl {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Use {
    String value();
}

class Base {}

@Decl("class")
class Example extends @Use("base") Base
              implements @Use("interface") Runnable {

    @Decl("field")
    private @Use("field-type") List<@Use("element") String> names;

    @Decl("method")
    public @Use("return") List<@Use("return-element") String> process(
            @Decl("parameter") @Use("param-type") String input) {
        return List.of(input);
    }

    @Override
    public void run() {}
}

public class AnnotationLocationDemo {
    private static String declared(AnnotatedElement element) {
        Decl annotation = element.getDeclaredAnnotation(Decl.class);
        return annotation == null ? "-" : annotation.value();
    }

    private static String typeUse(AnnotatedType type) {
        Use annotation = type.getAnnotation(Use.class);
        return annotation == null ? "-" : annotation.value();
    }

    public static void main(String[] args) throws Exception {
        Class<Example> type = Example.class;
        Field field = type.getDeclaredField("names");
        Method method = type.getDeclaredMethod("process", String.class);
        Parameter parameter = method.getParameters()[0];

        AnnotatedParameterizedType fieldType =
                (AnnotatedParameterizedType) field.getAnnotatedType();
        AnnotatedParameterizedType returnType =
                (AnnotatedParameterizedType) method.getAnnotatedReturnType();

        System.out.println("class declaration: " + declared(type));
        System.out.println("field declaration: " + declared(field));
        System.out.println("field type: " + typeUse(fieldType));
        System.out.println("generic argument: "
                + typeUse(fieldType.getAnnotatedActualTypeArguments()[0]));
        System.out.println("method declaration: " + declared(method));
        System.out.println("method return type: " + typeUse(returnType));
        System.out.println("return argument: "
                + typeUse(returnType.getAnnotatedActualTypeArguments()[0]));
        System.out.println("parameter declaration: "
                + declared(parameter));
        System.out.println("parameter type: "
                + typeUse(parameter.getAnnotatedType()));
        System.out.println("superclass type: "
                + typeUse(type.getAnnotatedSuperclass()));
        System.out.println("interface type: "
                + typeUse(type.getAnnotatedInterfaces()[0]));

        System.out.println("field use on declaration: "
                + (field.getDeclaredAnnotation(Use.class) != null));
        System.out.println("field decl on type: "
                + (field.getAnnotatedType().getAnnotation(Decl.class) != null));
    }
}
```

Biên dịch theo chuẩn Java 17 và chạy:

```bash
javac --release 17 AnnotationLocationDemo.java
java AnnotationLocationDemo
```

Output từ chương trình:

```text
class declaration: class
field declaration: field
field type: field-type
generic argument: element
method declaration: method
method return type: return
return argument: return-element
parameter declaration: parameter
parameter type: param-type
superclass type: base
interface type: interface
field use on declaration: false
field decl on type: false
```

Ví dụ cố tình không tạo instance `Example`; chỉ dùng metadata ở runtime. Tất cả class/annotation phụ được đặt trong cùng một file Java để `javac` biên dịch được mà không cần thêm thư viện.

## 6. Giải thích cách đọc kết quả

### 6.1. Class declaration

`@Decl("class")` được gắn trên khai báo `class Example` vì `Decl` cho phép `ElementType.TYPE`.

```java
Decl d = Example.class.getDeclaredAnnotation(Decl.class);
```

Đây là truy vấn trực tiếp annotation của **Class**. Nó khác annotation ở `extends @Use Base` hoặc `implements @Use Runnable`: hai chỗ đó là **type use** của superclass/interface và phải tìm qua `getAnnotatedSuperclass()`, `getAnnotatedInterfaces()`.

### 6.2. Field declaration và field type

Ví dụ:

```java
@Decl("field")
private @Use("field-type") List<@Use("element") String> names;
```

Ba mục metadata khác nhau:

1. `field.getDeclaredAnnotation(Decl.class)` trả `@Decl("field")`.
2. `field.getAnnotatedType().getAnnotation(Use.class)` trả `@Use("field-type")`.
3. `((AnnotatedParameterizedType) field.getAnnotatedType()).getAnnotatedActualTypeArguments()[0].getAnnotation(Use.class)` trả `@Use("element")` trên đối số generic `String`.

**Vì sao không thể chỉ gọi `field.getDeclaredAnnotations()`?** Vì API đó hỏi annotation gắn trực tiếp vào **khai báo field**. Annotation dùng để mô tả **kiểu field** và **kiểu phần tử generic** nằm trong một nhánh metadata khác, được biểu diễn qua `AnnotatedType`.

### 6.3. Method declaration và return type

`@Decl("method")` đánh dấu method declaration `process`. `@Use("return")` đánh dấu **kiểu trả về `List<...>`**; `@Use("return-element")` đánh dấu **generic type argument String** của return type.

Do đó:

```java
method.getDeclaredAnnotation(Decl.class); // @Decl("method")
method.getAnnotatedReturnType()
      .getAnnotation(Use.class);           // @Use("return")
```

Muốn thấy `return-element`, lấy `AnnotatedParameterizedType` và duyệt `getAnnotatedActualTypeArguments()`. API `method.getReturnType()` chỉ trả `List.class` và không chứa vị trí annotation trên `String`.

### 6.4. Parameter declaration và parameter type

Trong khai báo:

```java
@Decl("parameter") @Use("param-type") String input
```

Hai annotation ở hai ngữ nghĩa khác nhau:

- `parameter.getDeclaredAnnotation(Decl.class)`: annotation của **parameter declaration**.
- `parameter.getAnnotatedType().getAnnotation(Use.class)`: annotation của **kiểu String** được parameter sử dụng.

Không nên suy luận rằng `method.getParameterAnnotations()` đủ để đọc mọi type-use annotation. API đó cung cấp annotation trên các parameter declaration; để lấy annotation của kiểu tham số, dùng `getAnnotatedParameterTypes()` hoặc `Parameter.getAnnotatedType()`.

### 6.5. Annotated superclass và interfaces

`Example extends @Use("base") Base implements @Use("interface") Runnable` chứa hai type-use annotation ở **vị trí dùng kiểu**. Lấy:

```java
AnnotatedType parent = Example.class.getAnnotatedSuperclass();
AnnotatedType[] interfaces = Example.class.getAnnotatedInterfaces();
```

Sau đó kiểm tra `getAnnotation(Use.class)` trên từng `AnnotatedType`.

Điểm khác biệt: `Example.class.getDeclaredAnnotations()` mô tả annotation của **declaration class Example**, không thay thế các API annotated superclass/interface.

### 6.6. Hai dòng false là bằng chứng gì?

- `field.getDeclaredAnnotation(Use.class) == null`: trong ví dụ này, `@Use("field-type")` không xuất hiện như annotation của **field declaration**.
- `field.getAnnotatedType().getAnnotation(Decl.class) == null`: `@Decl("field")` không mô tả **kiểu field**.

Hai dòng cuối in `false`. Chúng minh họa sự phân tách metadata rõ ràng vì ví dụ sử dụng hai annotation có `@Target` riêng biệt.

**Không tổng quát hóa thành “một annotation không thể vừa là declaration annotation vừa là type-use annotation”.** Trong các ngữ cảnh và `@Target` khác, cùng loại annotation có thể được phép đặt ở cả declaration và type use. Phần tiếp theo giải thích giới hạn này.

## 7. @Target quyết định vị trí hợp lệ, không chỉ là tên annotation

Một annotation có thể được khai báo:

```java
@Target(ElementType.FIELD)
@interface FieldOnly {}

@Target(ElementType.TYPE_USE)
@interface TypeOnly {}

@Target({ElementType.FIELD, ElementType.TYPE_USE})
@interface EitherLocation {}
```

Đây là đoạn minh họa về `@Target` (giả định đã `import java.lang.annotation.*;`). Khi thiếu `@Retention(RUNTIME)`, chúng không phải ví dụ phù hợp để truy vấn runtime bằng Reflection; chỉ dùng để so sánh target.

| Target | Ý định |
| --- | --- |
| `FIELD` | Gắn annotation trên field declaration |
| `TYPE_USE` | Gắn annotation ở nơi kiểu được dùng, ví dụ kiểu field/generic argument |
| `FIELD, TYPE_USE` | Annotation có thể được sử dụng ở cả hai dạng vị trí theo quy tắc Java |
| `PARAMETER` | Mô tả parameter declaration |
| `TYPE_PARAMETER` | Mô tả khai báo type parameter generic |
| `TYPE` | Mô tả khai báo class/interface/những kiểu phù hợp |

### Một nuance quan trọng: TYPE_USE không hoàn toàn tách rời declaration

Theo luật Java, `TYPE_USE` còn áp dụng được tại **một số vị trí khai báo kiểu**, bao gồm khai báo class/interface và type parameter. Do đó, từ dòng source có vẻ “đặt annotation trước tên class” không thể khẳng định annotation đó chỉ là một declaration annotation và không thể là type-use annotation.

Hơn nữa, một annotation khai báo target gồm **cả `FIELD` và `TYPE_USE`** có thể khiến annotation ở vị trí phù hợp được compiler ghi nhận cho cả declaration và type use. Khi soạn công cụ phân tích, hãy xác định rõ **metadata bạn muốn đọc** (declaration hay type position), dùng API tương ứng, và kiểm tra output của compiler nếu ngữ cảnh có nhiều `@Target`.

Hai loại metadata là hai **chiều mô tả**, không phải hai nhóm Java type loại trừ nhau bằng nhãn tuyệt đối.

## 8. @Retention ảnh hưởng trực tiếp đến Reflection

`@Target` xác định **nơi đặt annotation hợp lệ**; `@Retention` xác định **thông tin được giữ đến giai đoạn nào**.

| Retention policy | Annotation được giữ đến đâu? | Runtime Reflection |
| --- | --- | --- |
| `SOURCE` | Dành cho source/processor, thường không lưu trong class file | Không đọc được tại runtime |
| `CLASS` | Có thể lưu trong class file nhưng không phải runtime-visible | Không được API Reflection chuẩn trả lại |
| `RUNTIME` | Lưu trong class file dưới metadata runtime-visible phù hợp | Có thể đọc được qua Reflection **nếu API hỗ trợ vị trí đó** |

Ví dụ runnable dùng:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Use { String value(); }
```

Nếu bỏ `RetentionPolicy.RUNTIME`, annotation mặc định retention `CLASS` và các lời gọi `getAnnotation(Use.class)` trong chương trình thường không thấy type-use metadata. Vì vậy, khi nhận kết quả `null`, cần kiểm tra **retention**, **target**, **đúng phần tử/type position**, và **metadata sau biên dịch** trước khi nghĩ Reflection gặp lỗi.

Không nên mặc định “có trong source” = “có trong runtime Reflection”.

## 9. Định tuyến theo hình dạng `AnnotatedType`

Khi một kiểu phức hợp chứa nhiều vị trí annotation, trước hết lấy `AnnotatedType` từ nơi khai báo (`Field.getAnnotatedType()`, `Method.getAnnotatedReturnType()`, v.v.). `getType()` trả **kiểu nền** dạng `Type`; `getAnnotations()` chỉ trả annotation của **vị trí hiện tại**, không tự gom các nhánh con.

| Nếu nút là | Cách truy cập annotation nằm sâu hơn |
| --- | --- |
| `AnnotatedParameterizedType` | `getAnnotatedActualTypeArguments()` cho từng đối số generic; `getAnnotatedOwnerType()` khi có owner |
| `AnnotatedArrayType` | `getAnnotatedGenericComponentType()` để xuống từng chiều rồi tới phần tử |
| `AnnotatedWildcardType` | `getAnnotatedUpperBounds()` / `getAnnotatedLowerBounds()` cho `? extends` / `? super` |
| `AnnotatedTypeVariable` | `getAnnotatedBounds()` cho ràng buộc của biến kiểu |
| `AnnotatedType` đơn giản | Đọc annotation của nút; có thể không có nhánh con |

Ví dụ, `List<@Use("item") String>` có annotation ở **đối số String**, nên phải đi qua `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()[0]`, không hỏi `List.class` hay `Field.getDeclaredAnnotations()`. Tương tự, `? extends @Use("bound") Number` có marker ở upper bound, còn `String @Use("dimension") []` đánh dấu chiều mảng.

Một type parameter như `<@ParameterMark T extends @Use("bound") Number>` có **annotation khai báo trên T** (đọc qua `TypeVariable.getAnnotations()`) và **annotation type-use trên bound** (đọc qua `TypeVariable.getAnnotatedBounds()`): đây là hai nơi riêng biệt. Các bài sau xây ví dụ sâu cho từng dạng nút, receiver/exception/return type và hierarchy.

Khi viết trình duyệt đệ quy tổng quát, cần giữ **đường dẫn của từng vị trí**, khám phá owner khi có, và chống vòng lặp bound kiểu `T extends Comparable<T>`. Chương “Xây dựng trình duyệt cây kiểu mà vẫn giữ vị trí annotation” cung cấp visitor có bảo vệ chu trình; không dùng đệ quy bounds vô điều kiện trong production.

## 10. Declaration và type-use ở parameter khác nhau như thế nào?

Giả sử một framework injection dùng annotation `@FromHeader` trên parameter declaration và một static checker dùng `@NonNull` dạng type use.

```java
void send(
    @FromHeader("X-Request") @NonNull String requestId) {
}
```

Đoạn là **minh họa**: hai loại annotation chưa được định nghĩa trong bài, vì vậy không phải chương trình độc lập. `@FromHeader` mô tả **nguồn dữ liệu của parameter**; `@NonNull` mô tả **hợp đồng của kiểu String tại vị trí sử dụng**.

Một framework có thể đọc:

```java
Parameter parameter = method.getParameters()[0];
Annotation[] declaration = parameter.getDeclaredAnnotations();
Annotation[] typeUse = parameter.getAnnotatedType().getAnnotations();
```

Nếu mỗi annotation được định nghĩa đúng `@Target`/`@Retention`, hai mảng có thể phản ánh metadata khác nhau. Không dùng `getParameterAnnotations()` làm đường duy nhất để thu thập contract type-use, và không dùng `getAnnotatedParameterTypes()` để thay thế thông tin injection gắn với parameter declaration.

## 11. Declaration annotation khác type-use annotation ở method

Xem ý nghĩa của hai annotation:

```java
@Transaction
public @NonNull String findUser() {
    return "Ada";
}
```

Đây là đoạn minh họa, chưa định nghĩa `Transaction`/`NonNull`.

- `@Transaction` mô tả **method declaration**: yêu cầu giao dịch (nếu framework hỗ trợ).
- `@NonNull` mô tả **kiểu trả về String**: thông tin giúp công cụ static-checker hoặc framework kiểm tra kết quả (nếu framework áp dụng).

API:

```java
method.getDeclaredAnnotation(Transaction.class);
method.getAnnotatedReturnType().getAnnotation(NonNull.class);
```

Lựa chọn API xuất phát từ câu hỏi: “Thông tin này mô tả **lời khai báo một method** hay **kiểu của giá trị trả về**?”

Với generic return `List<@NonNull String>`, `@NonNull` còn có thể gắn vào **type argument**, phải duyệt `AnnotatedParameterizedType`.

## 12. Type-use annotation không mặc nhiên thay đổi hành vi Java

Giả sử một annotation có tên `@NonNull`:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface NonNull {}
```

Nếu không có checker/framework chuyên biệt, Java/JVM **không tự ném lỗi** chỉ vì một biến `@NonNull String` nhận giá trị null. Annotation là metadata, không phải cơ chế tự động thêm câu lệnh `if (value == null) throw ...`.

Với các annotation kiểu @Nullable/@NonNull, hiệu lực còn tùy **bộ công cụ**: static analysis, annotation processor, compiler plugin, library runtime validation, v.v. Đừng đánh đồng việc Reflection **đọc thấy metadata** với việc JVM tự thi hành hợp đồng đó.

Tương tự, type-use annotation không tạo ra một `Class<?>` mới: `String.class` vẫn là `String.class` dù những chỗ dùng kiểu `String` được gắn các annotation khác nhau.

## 13. Ranh giới của Reflection: không đọc được mọi type use trong body

Các vị trí type use xuất hiện ở:

- Kiểu của field, kiểu trả về và tham số method.
- Generic arguments và các bound.
- Superclass/interface được khai báo.
- Kiểu trong `throws`.
- Kiểu tại `new`, cast, `instanceof`, local variable, lambda và nhiều expression khác.

Java Reflection cung cấp API khá phong phú cho **declaration signatures** và các annotated types liên quan, nhưng **không phải trình phân tích toàn bộ method body**. Annotation trên local variable, cast hay expression trong bytecode thường cần công cụ đọc class file/bytecode như ASM nếu muốn phân tích đúng type annotation attributes và vị trí target.

Do đó một scanner chỉ chạy `Class.getDeclaredFields()` rồi `Field.getAnnotatedType()` **không thể chứng minh** rằng trong thân mọi method không có type-use annotation khác. Phạm vi công cụ là metadata API thực sự cung cấp.

Với runtime annotation trong class file, sự phân tách thường tương ứng với các attribute liên quan đến declaration annotations và **runtime-visible type annotations**; chúng không được xem là cùng một danh sách phẳng.

## 14. Những lỗi phổ biến khi dùng Reflection với annotation

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Dùng `Field.getAnnotations()` để tìm type annotation của `List<@X String>` | Field declaration khác type argument | Dùng `getAnnotatedType()` rồi duyệt `AnnotatedParameterizedType` |
| Dùng `Method.getAnnotations()` để tìm `@X` trên return type | Method declaration khác return type | Dùng `getAnnotatedReturnType()` |
| Dùng `Parameter.getAnnotations()` để tìm mọi type-use annotation | Parameter declaration không bao quát annotation trên kiểu | Dùng `Parameter.getAnnotatedType()` |
| Quên `Retention(RUNTIME)` | Metadata không runtime-visible | Định nghĩa retention đúng khi cần runtime Reflection |
| Chỉ xem `getReturnType()` hoặc `getGenericReturnType()` | Không lấy annotation từng vị trí type use | Sử dụng `getAnnotatedReturnType()` và subtype liên quan |
| Coi `TYPE` tương đương `TYPE_USE` | Hai ngữ nghĩa và target không đồng nhất | Phân biệt khai báo class với các vị trí sử dụng kiểu |
| Coi annotation type-use là JVM validation | Metadata không tự bắt lỗi | Tích hợp checker hoặc runtime validation nếu cần |
| Nghĩ `TYPE_USE` không bao giờ xuất hiện ở declaration | Một số type declaration/type parameter declaration cũng hợp lệ | Hiểu quy tắc `@Target` và kiểm tra metadata đúng API |
| Chỉ quan sát outermost generic type | Bỏ sót annotation trên nested type arguments | Duyệt đệ quy `AnnotatedParameterizedType`, array/wildcard/bounds |
| Giả định Reflection hiểu annotation trong method body | API mức cao không biểu diễn mọi vị trí expression/local | Dùng bytecode scanner khi yêu cầu bao phủ body |
| Dựa vào vị trí hiển thị của dấu `@` để đoán storage | Có context áp dụng kép hoặc annotation nhiều target | Xác định ý nghĩa annotation và kiểm tra class file nếu cần |

## 15. Mẫu xử lý thông tin theo đúng vai trò

### 15.1. Xây dựng field scanner

Khi quét field, tách hai luồng:

```java
static void inspectField(java.lang.reflect.Field field) {
    // Metadata thuộc chính khai báo field:
    java.lang.annotation.Annotation[] fieldAnnotations =
            field.getDeclaredAnnotations();

    // Metadata trên type use của field:
    java.lang.reflect.AnnotatedType type = field.getAnnotatedType();
    java.lang.annotation.Annotation[] typeAnnotations =
            type.getAnnotations();

    System.out.println("Declaration: " + fieldAnnotations.length);
    System.out.println("Type use: " + typeAnnotations.length);
}
```

Đây là method Java hoàn chỉnh khi đặt trong một class, không bao gồm việc duyệt các generic arguments: nếu `field.getAnnotatedType()` là `AnnotatedParameterizedType`, cần đọc các type argument riêng theo mục 9.

### 15.2. Đọc type-use annotation trong generic

Nếu scanner cần biết annotation trên từng đối số trực tiếp của `List` hoặc `Map`, hãy đọc theo từng nút:

```java
static void inspectArguments(java.lang.reflect.Field field) {
    if (field.getAnnotatedType()
            instanceof java.lang.reflect.AnnotatedParameterizedType p) {
        for (java.lang.reflect.AnnotatedType arg
                : p.getAnnotatedActualTypeArguments()) {
            System.out.println(arg.getType().getTypeName()
                    + ": " + java.util.Arrays.toString(arg.getAnnotations()));
        }
    }
}
```

Đoạn này xử lý **một cấp đối số generic** và không tự gom annotation sâu hơn. Nếu cần duyệt mảng, wildcard, nested generic, owner và type-variable bounds, sử dụng visitor có đường dẫn/cycle guard ở bài cuối chương. Cách giới hạn phạm vi giữ ví dụ an toàn trước recursive bounds và không làm mất trọng tâm declaration vs type use.

### 15.3. Khi chỉ cần declaration metadata

Nếu annotation thiết kế rõ `@Target(FIELD)`, hãy đọc trực tiếp từ `Field`:

```java
ColumnInfo column = field.getDeclaredAnnotation(ColumnInfo.class);
```

Không cần duyệt `AnnotatedType` chỉ để tìm declaration annotation. Hai hệ API nên được sử dụng bổ trợ đúng mục tiêu.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Khác biệt giữa `@Decl` trên field declaration và `@Use` trên kiểu của field là gì?

**Đáp án:** `@Decl` mô tả member được khai báo, còn `@Use` mô tả một vị trí sử dụng kiểu. Với runtime Reflection, `Field.getDeclaredAnnotation` và `Field.getAnnotatedType().getAnnotation` đọc hai vị trí tương ứng.

**Câu 2.** Muốn đọc `@X` từ `List<@X String>`, có đủ chỉ gọi `field.getDeclaredAnnotations()` không?

**Đáp án:** Không. Cần lấy `field.getAnnotatedType()`, cast đúng loại `AnnotatedParameterizedType`, duyệt `getAnnotatedActualTypeArguments()` rồi đọc annotation trên type argument `String`.

**Câu 3.** Method có `@Audit` trên declaration và `@NonNull` trên return type. Nên dùng API nào?

**Đáp án:** `method.getDeclaredAnnotation(Audit.class)` cho declaration; `method.getAnnotatedReturnType().getAnnotation(NonNull.class)` cho return type.

**Câu 4.** `@Target(TYPE_USE)` có tự bảo đảm JVM cấm gán null cho biến được annotate `@NonNull` không?

**Đáp án:** Không. Annotation là metadata; phải có checker/framework thực thi quy tắc.

**Câu 5.** `@Target(TYPE)` và `@Target(TYPE_USE)` có đồng nghĩa không?

**Đáp án:** Không. `TYPE` chủ yếu dành cho type declarations, còn `TYPE_USE` dành cho vị trí sử dụng kiểu và cũng được phép ở một số khai báo kiểu/type parameter theo luật Java.

**Câu 6.** Vì sao `@Retention(RUNTIME)` quan trọng?

**Đáp án:** Nếu annotation không được giữ đến runtime, Reflection chuẩn không thể đọc nó ở thời điểm chương trình chạy, dù annotation xuất hiện trong source.

**Câu 7.** Có thể dùng `getAnnotatedSuperclass()` để đọc annotation trên `extends @X Base` không?

**Đáp án:** Có. Đây là metadata tại vị trí **type use** của superclass, khác annotation trên declaration của subclass.

**Câu 8.** Java Reflection có thể liệt kê mọi type-use annotation trên cast và local variable trong body method không?

**Đáp án:** Không qua các API mức cao về signature thông thường. Muốn đọc metadata loại này đầy đủ cần công cụ phân tích bytecode/class file phù hợp.

**Câu 9.** Một annotation có `@Target({FIELD, TYPE_USE})` có thể xuất hiện liên quan đến cả declaration và type-use metadata không?

**Đáp án:** Có những ngữ cảnh mà annotation áp dụng được cho cả hai dạng. Vì vậy cần xem `@Target`, ngữ cảnh cú pháp và metadata thực của class file; không khẳng định hai miền hoàn toàn loại trừ nhau.

**Ghi nhớ:** *Declaration annotation* trả lời “thành phần chương trình nào được khai báo và mô tả ra sao?”, còn *type-use annotation* trả lời “kiểu đang được sử dụng tại vị trí nào và mang metadata gì?”. Trong Reflection, API trên `Class`/`Field`/`Method`/`Parameter` đọc declaration metadata; họ API `getAnnotated...` và `AnnotatedType` đọc metadata của những **type positions**. Chọn API theo vị trí, bảo đảm `@Target`/`@Retention` phù hợp và đừng đồng nhất metadata với cơ chế validation tự động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-002">AnnotatedType và kiểu nền bên dưới</a>

<details>
<summary>Xem chi tiết</summary>

# `AnnotatedType` và kiểu nền bên dưới trong Java Reflection

## 1. `AnnotatedType` dùng để làm gì?

Từ Java 8, annotation có thể xuất hiện ngay tại **vị trí sử dụng một kiểu** (*type use*): trên kiểu phần tử generic, kiểu của trường, kiểu trả về, phần tử mảng, wildcard, giới hạn type variable, v.v. Reflection cung cấp giao diện `java.lang.reflect.AnnotatedType` để giữ cả hai phần thông tin:

1. **Kiểu nền** (*underlying type*): trả qua `getType()`, là một `java.lang.reflect.Type`.
2. **Annotation gắn tại chính vị trí sử dụng kiểu đó**: truy cập bằng `getAnnotation(...)`, `getAnnotations()`, `getDeclaredAnnotations()` trên nút `AnnotatedType`.

Ví dụ:

~~~java
List<@NonNull String> names;
~~~

Nếu `@NonNull` là annotation áp dụng cho `TYPE_USE` và có `RetentionPolicy.RUNTIME`, Reflection có thể đọc annotation ở **kiểu đối số `String`**, không phải mặc nhiên ở `List`, tên biến `names` hay mọi đối tượng `String` khác.

Điểm quan trọng nhất: **`AnnotatedType` không phải là một `Type` mới đã được thay đổi**. Nó là lớp biểu diễn vị trí kiểu *kèm annotation*. Gọi `getType()` để lấy `Type` tương ứng; gọi các API `Annotated...` để đi vào cấu trúc con mà vẫn giữ annotation tại từng nút.

## 2. So sánh `Type`, `AnnotatedType` và annotation khai báo

| Thành phần | Câu hỏi nó trả lời | Ví dụ |
| --- | --- | --- |
| `Class<?>` | Kiểu runtime hoặc lớp của trường khi xem dưới dạng raw type | `List.class`, `String[].class` |
| `Type` | Biểu diễn một kiểu Java có thể chứa generic | `List<String>`, `? extends Number`, `T` |
| `AnnotatedType` | Kiểu được **sử dụng tại một vị trí**, cùng annotation ở vị trí ấy | `@Mark List<@Mark String>` |
| `AnnotatedElement` của `Field`/`Method` | Annotation khai báo trên **phần tử** trường/phương thức | `@Deprecated` trên phương thức |

Một `AnnotatedType` chính nó thực hiện `AnnotatedElement`, nên có `getAnnotation()`, `getAnnotations()`. Nhưng điều đó **không khiến nó đồng nhất với `Field` hay `Method`**.

Ví dụ:

~~~java
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface Mark { }

class Example {
    List<@Mark String> values;
}
~~~

Đoạn mã trên cần import `java.lang.annotation.*` và `java.util.List`; đây là minh họa, chưa phải chương trình độc lập.

- `Example.class.getDeclaredField("values").getDeclaredAnnotations()` kiểm tra annotation khai báo trực tiếp **trên trường**; trong ví dụ không có annotation declaration tại đó.
- `field.getAnnotatedType()` biểu diễn việc sử dụng `List<...>`. Nếu `List` không có annotation trực tiếp, `getAnnotation(Mark.class)` trên nút gốc là `null`.
- `((AnnotatedParameterizedType) field.getAnnotatedType()).getAnnotatedActualTypeArguments()[0]` biểu diễn `String`, và chính nút này có `@Mark`.

**`field.getAnnotatedType().getType()` chỉ lấy cấu trúc `Type`; không được dùng kết quả đó để hỏi annotation của từng vị trí generic.**

## 3. Lấy `AnnotatedType` từ đâu?

| Nguồn Reflection | API | Vị trí type-use được mô tả |
| --- | --- | --- |
| Field | `Field.getAnnotatedType()` | Kiểu của trường |
| Method | `Method.getAnnotatedReturnType()` | Kiểu trả về |
| Method | `Method.getAnnotatedParameterTypes()` | Mỗi kiểu tham số theo thứ tự |
| Method/Constructor | `getAnnotatedExceptionTypes()` | Các kiểu exception khai báo trong `throws` |
| Constructor/Method | `getAnnotatedReceiverType()` | Kiểu receiver, nếu áp dụng |
| Class | `Class.getAnnotatedSuperclass()` | Kiểu `extends` |
| Class | `Class.getAnnotatedInterfaces()` | Các kiểu `implements` hoặc `extends` interface |
| Type variable | `TypeVariable.getAnnotatedBounds()` | Các kiểu bound của type variable |
| Record component (Java 16+) | `RecordComponent.getAnnotatedType()` | Kiểu thành phần record |

Không phải API nào cũng đảm bảo trả về một đối tượng không `null`: chẳng hạn `getAnnotatedSuperclass()` có thể trả `null` khi không có superclass tương ứng. Các API trả mảng có thể trả mảng rỗng. Kiểm tra giá trị và cấu trúc thực tế trước khi ép kiểu.

Với một field generic:

~~~java
Field field = Box.class.getDeclaredField("labels");

Class<?> raw = field.getType();          // List.class
Type generic = field.getGenericType();  // List<String>
AnnotatedType annotated = field.getAnnotatedType();
Type base = annotated.getType();         // Type biểu diễn List<String>
~~~

Đây là đoạn minh họa, phải có `Box`, import `java.lang.reflect.*` và xử lý `NoSuchFieldException`.

- `getType()` của **Field** trả `Class<?>`, nên trường `List<String>` thường được thấy dưới dạng raw `List.class`.
- `getGenericType()` của **Field** trả `Type` và giữ cấu trúc generic đã khai báo.
- `getType()` của **AnnotatedType** cũng trả `Type` mô tả kiểu nền tại nút đó. Đối với `List<String>`, nó là một `ParameterizedType`, không bắt buộc là `Class`.
- Đừng ép `(Class<?>) annotated.getType()` một cách mù quáng; nếu kiểu là `List<String>`, phép ép kiểu có thể gây `ClassCastException`.

## 4. Phân cấp `AnnotatedType`: đi theo đúng hình dạng kiểu nền

Các giao diện con chuyên biệt cho phép duyệt từng loại kiểu:

| Giao diện | Kiểu nền thường gặp | API lấy nút con chứa annotation |
| --- | --- | --- |
| `AnnotatedParameterizedType` | `ParameterizedType`, ví dụ `List<String>` | `getAnnotatedActualTypeArguments()` |
| `AnnotatedArrayType` | `Class` của mảng hoặc `GenericArrayType` | `getAnnotatedGenericComponentType()` |
| `AnnotatedWildcardType` | `WildcardType`, ví dụ `? extends Number` | `getAnnotatedUpperBounds()`, `getAnnotatedLowerBounds()` |
| `AnnotatedTypeVariable` | `TypeVariable`, ví dụ `T` | `getAnnotatedBounds()` |

Đây là một **cây vị trí kiểu**, không phải một mảng annotation phẳng. Ví dụ:

~~~java
List<@A ? extends @B Number>
~~~

Có thể quan sát các nút (nếu `@A`, `@B` là type-use annotations giữ tới runtime):

1. Gốc `List<...>`: `AnnotatedParameterizedType`.
2. Đối số generic `? extends Number`: `AnnotatedWildcardType`, nhận `@A` trên wildcard.
3. Upper bound `Number`: một `AnnotatedType` riêng, nhận `@B`.

Nếu chỉ hỏi `getAnnotations()` ở gốc `List`, bạn **không tự động nhận** annotation trên wildcard hoặc bound. Phải đi vào những nút con tương ứng.

### Type variable và kiểu được thay vào runtime

Với khai báo:

~~~java
class Repository<T extends @A Number> { }
~~~

Annotation `@A` gắn trên **bound `Number`** của type parameter `T`, không phải lúc nào cũng nằm trên chính `T`. Đọc bằng:

~~~java
TypeVariable<?> t = Repository.class.getTypeParameters()[0];
AnnotatedType bound = t.getAnnotatedBounds()[0];
~~~

Vì **Java generic dùng type erasure**, `AnnotatedType` không nói cho bạn kiểu đối tượng thực tế của mọi phần tử được lưu trong `List<T>` tại runtime. Nó cho biết **khai báo kiểu và type-use annotations có thể giữ được bằng metadata**. Không nhầm thông tin khai báo với kiểm tra giá trị động hoặc khả năng ép kiểu.

## 5. Ví dụ hoàn chỉnh Java 17+: kiểu nền và annotation ở từng nút

Lưu file **`AnnotatedTypeLesson.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.*;
import java.util.List;

public class AnnotatedTypeLesson {
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Mark {
        String value();
    }

    static class Box<T extends @Mark("bound") Number> {
        public @Mark("list") List<@Mark("element") String> labels;
        public @Mark("component") String @Mark("dimension") [] tags;
        public List<@Mark("wildcard") ? extends @Mark("upper") Number> numbers;

        public @Mark("return") String echo(@Mark("param") String word) {
            return word;
        }
    }

    static String mark(AnnotatedType type) {
        Mark annotation = type.getAnnotation(Mark.class);
        return annotation == null ? "(none)" : annotation.value();
    }

    public static void main(String[] args) throws Exception {
        Field labels = Box.class.getDeclaredField("labels");
        AnnotatedType root = labels.getAnnotatedType();
        System.out.println("root Type=" + root.getType().getTypeName());
        System.out.println("root Mark=" + mark(root));

        AnnotatedParameterizedType parameterized =
                (AnnotatedParameterizedType) root;
        AnnotatedType element =
                parameterized.getAnnotatedActualTypeArguments()[0];
        System.out.println("argument Type=" + element.getType().getTypeName());
        System.out.println("argument Mark=" + mark(element));

        Field tags = Box.class.getDeclaredField("tags");
        AnnotatedArrayType array =
                (AnnotatedArrayType) tags.getAnnotatedType();
        System.out.println("array Type=" + array.getType().getTypeName());
        System.out.println("dimension Mark=" + mark(array));
        System.out.println("component Mark="
                + mark(array.getAnnotatedGenericComponentType()));

        Field numbers = Box.class.getDeclaredField("numbers");
        AnnotatedParameterizedType num =
                (AnnotatedParameterizedType) numbers.getAnnotatedType();
        AnnotatedWildcardType wildcard =
                (AnnotatedWildcardType) num.getAnnotatedActualTypeArguments()[0];
        System.out.println("wildcard Type="
                + wildcard.getType().getTypeName());
        System.out.println("wildcard Mark=" + mark(wildcard));
        System.out.println("upper Mark="
                + mark(wildcard.getAnnotatedUpperBounds()[0]));

        TypeVariable<Class<Box>> var = Box.class.getTypeParameters()[0];
        AnnotatedType bound = var.getAnnotatedBounds()[0];
        System.out.println("bound Type=" + bound.getType().getTypeName());
        System.out.println("bound Mark=" + mark(bound));

        Method echo = Box.class.getDeclaredMethod("echo", String.class);
        System.out.println("return Mark="
                + mark(echo.getAnnotatedReturnType()));
        System.out.println("param Mark="
                + mark(echo.getAnnotatedParameterTypes()[0]));
        System.out.println("declared field annotations="
                + labels.getDeclaredAnnotations().length);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AnnotatedTypeLesson.java
java AnnotatedTypeLesson
~~~

Kết quả stdout từ chương trình trên:

~~~text
root Type=java.util.List<java.lang.String>
root Mark=list
argument Type=java.lang.String
argument Mark=element
array Type=java.lang.String[]
dimension Mark=dimension
component Mark=component
wildcard Type=? extends java.lang.Number
wildcard Mark=wildcard
upper Mark=upper
bound Type=java.lang.Number
bound Mark=bound
return Mark=return
param Mark=param
declared field annotations=0
~~~

### Diễn giải kết quả

- `labels` có kiểu nền `List<String>`: `root.getType()` hiển thị cấu trúc generic, đồng thời `mark(root)` lấy `@Mark("list")` ở vị trí kiểu `List`.
- Nút con của `AnnotatedParameterizedType` có kiểu nền `String` với `@Mark("element")`, không phải annotation của chính field.
- `String @Mark("dimension") []` có `AnnotatedArrayType` ở gốc cho **chiều mảng**, còn `@Mark("component") String` gắn ở kiểu **phần tử**. Hai vị trí annotation là khác nhau.
- Ở `List<@Mark("wildcard") ? extends @Mark("upper") Number>`, wildcard mang `"wildcard"`; upper bound `Number` mang `"upper"`.
- Bound của `T extends Number` mang `@Mark("bound")`; annotation trên return type và parameter type lần lượt là `"return"` và `"param"`.
- `labels.getDeclaredAnnotations().length == 0` vì annotation trong ví dụ nằm ở **type-use**, không phải annotation khai báo trực tiếp của field. Kết quả này minh họa vì sao chỉ đọc annotation từ `Field` chưa đủ.

Chương trình trên không dùng logic suy đoán theo tên lớp của các implementation nội bộ `Type`. `getTypeName()` giúp in hình dạng kiểu một cách dễ hiểu; API gốc vẫn là `Type`.

## 6. Mảng nhiều chiều: annotation nằm trên từng chiều

Java hỗ trợ gắn type-use annotation lên cả kiểu phần tử và từng chiều mảng:

~~~java
@Mark("element") String @Mark("outer") [] @Mark("inner") [] matrix;
~~~

Đoạn khai báo trên chỉ minh họa trong lớp có annotation `@Mark` như ví dụ hoàn chỉnh. Khi Reflection mô tả `matrix`:

1. `field.getAnnotatedType()` biểu diễn **kiểu mảng ngoài cùng** (`AnnotatedArrayType`) và giữ `@Mark("outer")`.
2. `getAnnotatedGenericComponentType()` đi xuống một chiều; với mảng 2 chiều, nút này vẫn là `AnnotatedArrayType`, có `@Mark("inner")`.
3. Đi xuống thêm một lần thì tới kiểu phần tử `String` với `@Mark("element")`.

Cách ghi `String @Mark("x") []` khác về **vị trí annotation** so với `@Mark("x") String[]`. Nếu cần diễn giải field mảng chính xác, phải đi theo các `AnnotatedArrayType` thay vì chỉ xem `String[][].class`. Trên các kiểu mảng generic, underlying type có thể là `GenericArrayType`; không nên giả định luôn là `Class<?>[]`.

## 7. Khi nào nên lấy `getType()`, khi nào phải duyệt `AnnotatedType`?

### Chỉ cần cấu trúc kiểu?

Nếu một thư viện chỉ cần biết field là `List<String>` để ánh xạ dữ liệu, `Field.getGenericType()` hoặc `AnnotatedType.getType()` cung cấp cấu trúc `Type` thích hợp. Khi muốn biết phần tử generic, kiểm tra `instanceof ParameterizedType` rồi gọi `getActualTypeArguments()`. Kết quả là **`Type`**, không chứa trực tiếp type-use annotation của từng nhánh.

### Cần quy tắc được đánh dấu bởi annotation?

Nếu framework dùng `@NonNull`, `@Sensitive`, `@Validated` hoặc một annotation tùy chỉnh để điều khiển kiểm tra schema, nó cần duyệt **các nút AnnotatedType** và đọc annotation đúng vị trí. Dạng mô tả an toàn:

~~~java
AnnotatedType type = field.getAnnotatedType();

if (type instanceof AnnotatedParameterizedType parameterized) {
    for (AnnotatedType argument :
            parameterized.getAnnotatedActualTypeArguments()) {
        // Kiểm tra annotation tại MỖI đối số kiểu, rồi tiếp tục đệ quy.
    }
}
~~~

Đây là đoạn minh họa bên trong phương thức có `Field field`. Đừng ép mọi `AnnotatedType` sang `AnnotatedParameterizedType`; kiểu nền có thể là `Class`, `TypeVariable`, `GenericArrayType`, `WildcardType`, hoặc dạng khác. Khi cần xử lý sâu hơn, phân nhánh theo từng giao diện `Annotated...` tương ứng.

### Type-use metadata không đồng nghĩa validation tự động

Một annotation `@NonNull` trên type argument **không khiến JVM tự động cấm null**, và `AnnotatedType.getAnnotation` chỉ đọc metadata. Muốn kiểm tra dữ liệu thật cần cơ chế thực thi do thư viện hoặc ứng dụng triển khai. Ngay cả khi annotation hiện diện, `Type` không thay thế được việc kiểm tra phần tử runtime.

Tương tự, annotation tại vị trí `List<@NonNull String>` không biến generic của Java thành *reified generics*. Do erasure, JVM không có thông tin runtime để chứng minh toàn bộ các phần tử của một `List` bất kỳ thực sự là `String`.

## 8. Điều kiện để Reflection đọc được annotation

Định nghĩa thường dùng:

~~~java
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface NonNull { }
~~~

Cần chú ý:

- `TYPE_USE` cho phép annotation xuất hiện trên các **vị trí sử dụng kiểu**. `TYPE_PARAMETER` là target riêng cho việc khai báo biến kiểu, ví dụ ngay trên chữ `T` của `class Box<@A T>`; nếu cần cả hai, khai báo `@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})`.
- `RetentionPolicy.RUNTIME` cần thiết nếu muốn đọc annotation từ Reflection lúc chạy. `SOURCE` không tồn tại trong class file; `CLASS` được ghi vào class file nhưng không hiện diện trong Reflection runtime thông thường.
- Compiler có thể đưa type-use annotations vào thuộc tính metadata trong class file; thông tin hiển thị phụ thuộc việc khai báo đúng target/retention và phần tử Java thật sự được biên dịch.
- `getAnnotation(MyAnnotation.class)` trả `null` khi annotation không có ở vị trí đó. Nếu annotation nằm trên **nút con**, chỉ hỏi tại nút gốc không đủ.
- Các annotation áp dụng lên phương thức/lớp **khác** với annotation áp dụng lên kiểu. Một annotation có thể cho phép nhiều target, và cần chọn API theo câu hỏi thực tế.

**Không xem `getType()` là công cụ để lấy annotation:** `Type` là thông tin kiểu nền. Muốn đi qua metadata type-use, phải giữ và duyệt `AnnotatedType`.

## 9. Lỗi phổ biến

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “`AnnotatedType` luôn có `Class<?> getType()`.” | `getType()` trả `Type`; generic thường là `ParameterizedType`, wildcard là `WildcardType`, v.v. |
| “Annotation trên `List<@A String>` nằm ở `List`.” | `@A` nằm trên **đối số `String`**; đi qua `getAnnotatedActualTypeArguments()`. |
| “Đọc `field.getAnnotations()` là đủ cho mọi annotation liên quan.” | API này hỏi annotation **trên phần tử field**; type-use annotation phải hỏi qua `getAnnotatedType()`. |
| “`@A String[]` và `String @A []` giống nhau.” | Annotation có thể gắn vào **thành phần kiểu khác nhau**: phần tử hoặc chiều mảng. |
| “Mọi `AnnotatedType` đều là `AnnotatedParameterizedType`.” | Phải kiểm tra `instanceof` trước khi ép kiểu. |
| “Không thấy annotation nghĩa là class file không có annotation.” | Có thể đang hỏi sai nút, target không thích hợp hoặc retention không phải `RUNTIME`. |
| “`getType()` của `AnnotatedType` cho biết generic type argument của object hiện tại.” | Nó mô tả **kiểu khai báo tại vị trí Reflection**; Java generics không reify mọi đối số kiểu runtime. |
| “`isAnnotationPresent` trên root tự kiểm tra cả cây.” | Không: kiểm tra **nút hiện tại**; muốn kiểm tra con phải duyệt rõ ràng. |

## 10. Checklist thực hành

1. **Xác định vị trí khai báo** cần soi: field, method parameter, return type, superclass, interface, type-variable bound, array hoặc record component.
2. **Lấy `AnnotatedType` đúng API**. Không thay bằng `getAnnotations()` trên phần tử khi cần type-use.
3. **Đọc `getType()`** để hiểu kiểu nền đang được mô tả; dùng `instanceof` cho `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType` khi cần.
4. **Đọc annotation trên đúng nút** bằng `getAnnotation`/`getAnnotations`.
5. **Duyệt các nút con** qua các interface `AnnotatedParameterizedType`, `AnnotatedArrayType`, `AnnotatedWildcardType`, `AnnotatedTypeVariable`; với inner class có thể xem `getAnnotatedOwnerType()`.
6. **Xác nhận `@Target(TYPE_USE)` và `@Retention(RUNTIME)`** của annotation muốn đọc.
7. **Không suy ra logic validation hoặc khả năng ép kiểu runtime chỉ từ metadata**; các quy tắc đó do ứng dụng triển khai.

### Câu hỏi tự kiểm tra

**1.** Với `List<@Mark String>`, hỏi annotation ở `field.getAnnotatedType()` có chắc tìm được `@Mark` không?  
**Không.** Annotation nằm ở nút generic argument `String`; cần duyệt `getAnnotatedActualTypeArguments()[0]`.

**2.** `AnnotatedType.getType()` trả về `Class<?>` cho `List<String>`?  
**Không mặc định.** Nó trả `Type`, trong trường hợp này thường là `ParameterizedType` mang `List<String>`.

**3.** `String @Mark []` thì `@Mark` gắn vào đâu?  
**Chiều mảng**, do đó kiểm tra nút `AnnotatedArrayType`. `@Mark String[]` có vị trí annotation khác.

**4.** Cách tìm annotation trên `? extends @Mark Number` là gì?  
Vào `AnnotatedWildcardType.getAnnotatedUpperBounds()[0]`, rồi hỏi `getAnnotation(Mark.class)`.

**5.** Annotation có `@Retention(CLASS)` có thể đọc từ `AnnotatedType` bằng Reflection runtime không?  
**Không theo Reflection thông thường.** Muốn đọc tại runtime, cần `RUNTIME`.

**6.** `Field.getType()` và `Field.getAnnotatedType().getType()` luôn giống nhau về mức thông tin generic?  
**Không.** API đầu trả raw `Class<?>`; API sau trả `Type` nền của vị trí kiểu, có thể giữ cấu trúc generic.

**Kết luận:** `AnnotatedType` là **một nút dùng kiểu đã được gắn annotation**. `getType()` trả **kiểu nền ở nút đó**, còn `getAnnotation()` và các interface `Annotated...` giúp đọc các annotation xuất hiện **đúng từng vị trí** trong cây generic, mảng, wildcard và type-variable bounds.

### Tài liệu tham khảo chính thức

- [JDK 17 — `AnnotatedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html)
- [JDK 17 — `AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html)
- [JDK 17 — `AnnotatedArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedArrayType.html)
- [JDK 17 — `AnnotatedWildcardType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedWildcardType.html)
- [JDK 17 — `AnnotatedTypeVariable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedTypeVariable.html)
- [JDK 17 — `Field.getAnnotatedType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#getAnnotatedType())
- [JLS 17 §9.7.4 — Where Annotations May Appear](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.4)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-003">AnnotatedParameterizedType và annotation trên đối số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# AnnotatedParameterizedType và annotation trên đối số kiểu trong Java Reflection

Trong Java Reflection, `AnnotatedParameterizedType` giúp đọc **annotation gắn với một lần sử dụng kiểu có tham số generic** (*parameterized type use*), bao gồm cả annotation đặt trên **từng đối số kiểu**. Đây là khác biệt quan trọng so với chỉ lấy `ParameterizedType`.

Ví dụ, hai khai báo sau đều có kiểu generic `List<String>`, nhưng mang thông tin type-use annotation khác nhau:

```java
List<@Tag("element") String> first;
@Tag("container") List<String> second;
```

Đây là ví dụ cú pháp rời; annotation `Tag` phải được khai báo với `@Target(ElementType.TYPE_USE)` và retention thích hợp. Trong `first`, annotation gắn với **`String` ở vị trí đối số generic**. Trong `second`, annotation gắn với **chính lần dùng kiểu `List<String>`**. Muốn đọc đúng từng vị trí, ta cần `AnnotatedType` và `AnnotatedParameterizedType`, không chỉ `Field.getType()` hoặc `Field.getGenericType()`.

## 1. Bối cảnh: type-use annotation là gì?

Java 8 giới thiệu type annotations và các API tương ứng cho Reflection. Annotation có thể được gắn không chỉ vào *declaration* như class, method hoặc field, mà còn vào **vị trí sử dụng một kiểu** (*type use*): kiểu trả về, kiểu của trường, đối số generic, phần tử mảng, wildcard bound, v.v.

Một annotation dùng trong bài:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface Tag {
    String value();
}
```

Đoạn này minh họa phần khai báo annotation; chương trình chạy đầy đủ ở mục 4 khai báo `Tag` bên trong class để biên dịch độc lập.

Hai meta-annotation có vai trò khác nhau:

- `@Target(ElementType.TYPE_USE)` cho phép xuất hiện ở vị trí sử dụng kiểu, chẳng hạn `List<@Tag("x") String>`.
- `@Retention(RetentionPolicy.RUNTIME)` yêu cầu giữ annotation để Reflection có thể đọc ở runtime. Nếu thiếu `@Retention`, giá trị mặc định là `CLASS`: compiler có thể lưu thông tin trong class file nhưng các API Reflection runtime sẽ **không trả về** annotation ấy như kỳ vọng.

Không phải annotation nào xuất hiện gần một field cũng nhất thiết là annotation trên đối số kiểu. Cần xét chính xác **vị trí trong cây kiểu** mà annotation được gắn.

**Lưu ý:** Java compiler không tự kiểm tra hay thực thi ý nghĩa nghiệp vụ của `@Tag`. Nó chỉ ghi và cung cấp metadata theo hợp đồng ngôn ngữ; việc hiểu `"element"` là quy tắc xác thực, serialization hay nullness cần một framework/tool riêng.

## 2. AnnotatedType, ParameterizedType và AnnotatedParameterizedType

Ba API có nhiệm vụ khác nhau:

| API | Thuộc package | Diễn giải |
| --- | --- | --- |
| `Type` | `java.lang.reflect` | Giao diện chung đại diện cho một kiểu Java; có thể là `Class`, `ParameterizedType`, `TypeVariable`, v.v. |
| `ParameterizedType` | `java.lang.reflect` | Metadata của kiểu generic có đối số thực, chẳng hạn `List<String>`; `getActualTypeArguments()` trả `Type[]` |
| `AnnotatedType` | `java.lang.reflect` | **Một lần sử dụng kiểu** có thể mang type-use annotations; `getType()` trả kiểu nền `Type`, `getAnnotations()` đọc annotation tại **nút hiện tại** |
| `AnnotatedParameterizedType` | `java.lang.reflect` | Một `AnnotatedType` biểu diễn **lần sử dụng kiểu parameterized**; `getAnnotatedActualTypeArguments()` trả từng đối số dưới dạng `AnnotatedType[]`, nhờ đó giữ lại annotation tại mỗi đối số |

Ví dụ dữ liệu:

```java
Map<@Tag("key") String, List<@Tag("item") Integer>> values;
```

Cây kiểu có nhiều cấp:

```text
Map<String, List<Integer>>     (kiểu parameterized ngoài cùng)
├─ argument[0]: String         (@Tag("key") trên String)
└─ argument[1]: List<Integer>  (kiểu parameterized lồng bên trong)
   └─ argument[0]: Integer     (@Tag("item") trên Integer)
```

Nếu chỉ gọi `ParameterizedType.getActualTypeArguments()`, ta lấy được `String` và `List<Integer>` dưới dạng các đối tượng `Type`, nhưng **không đọc trực tiếp được annotation gắn tại từng lần dùng kiểu** từ `Type` ấy. Để lấy `@Tag("key")` và `@Tag("item")`, cần duyệt **cây `AnnotatedType`**.

### Annotation của chính kiểu và annotation của đối số là hai vị trí khác nhau

Xét:

```java
@Tag("outer") List<@Tag("element") String> names;
```

- Nút ngoài `AnnotatedType` đại diện cho lần dùng `List<String>` có `@Tag("outer")`.
- Vì nút ngoài là `AnnotatedParameterizedType`, có thể gọi `getAnnotatedActualTypeArguments()`.
- Phần tử thứ nhất trong mảng trả về đại diện cho lần dùng `String` và có `@Tag("element")`.

`getAnnotations()` trên **nút ngoài** không tự gom annotation của mọi nút con. Ngược lại, annotation trên `String` không tự biến thành annotation của `List`. Phải đọc theo đúng cấp.

## 3. Lấy AnnotatedParameterizedType từ đâu?

Thông thường ta không trực tiếp tạo một đối tượng `AnnotatedParameterizedType`, mà lấy `AnnotatedType` từ Reflection, sau đó **kiểm tra loại nút** trước khi ép kiểu.

| Nơi type use xuất hiện | API bắt đầu lấy `AnnotatedType` |
| --- | --- |
| Kiểu khai báo của field | `Field.getAnnotatedType()` |
| Kiểu trả về của method | `Method.getAnnotatedReturnType()` |
| Kiểu của từng tham số method/constructor | `Method.getAnnotatedParameterTypes()` hoặc `Constructor.getAnnotatedParameterTypes()` |
| Kiểu của từng tham số qua đối tượng `Parameter` | `Parameter.getAnnotatedType()` |
| Kiểu superclass có thể mang annotation | `Class.getAnnotatedSuperclass()` |
| Các superinterface có thể mang annotation | `Class.getAnnotatedInterfaces()` |
| Kiểu của exception khai báo ở executable | `Executable.getAnnotatedExceptionTypes()` |

Ví dụ cơ bản trên một field `names`:

```java
Field field = Sample.class.getDeclaredField("names");
AnnotatedType annotated = field.getAnnotatedType();

if (annotated instanceof AnnotatedParameterizedType parameterized) {
    AnnotatedType[] args = parameterized.getAnnotatedActualTypeArguments();
    // Mỗi args[i] là một type use, có thể có annotation riêng.
}
```

Đây là mảnh mã minh họa, cần `Sample`, import các kiểu Reflection và xử lý `NoSuchFieldException`. Kể cả một generic field `List<String>` **không gắn annotation nào**, `getAnnotatedType()` vẫn có thể là `AnnotatedParameterizedType`; annotation array rỗng không có nghĩa nút không phải parameterized.

Ngược lại, field `String single` có type use thông thường `String` (không có type argument), nên không nên ép mù quáng nút đó thành `AnnotatedParameterizedType`.

**Chú ý:** Một `AnnotatedType` có thể thuộc nhiều dạng cụ thể như `AnnotatedArrayType`, `AnnotatedWildcardType` hoặc `AnnotatedTypeVariable`. Chỉ cast sang `AnnotatedParameterizedType` khi `instanceof` xác nhận loại nút.

## 4. Ví dụ Java 17+ đầy đủ và đầu ra xác định

Tạo tệp `AnnotatedTypeDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class AnnotatedTypeDemo {
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tag {
        String value();
    }

    public static class Sample {
        public @Tag("outer") List<@Tag("element") String> names;

        public Map<@Tag("key") String,
                   List<@Tag("nested") Integer>> scores;

        public List<String> plain;
        public String single;

        public void accept(List<@Tag("parameter") String> values) {}

        public List<@Tag("result") String> read() {
            return List.of();
        }
    }

    static String label(AnnotatedType type) {
        Tag tag = type.getAnnotation(Tag.class);
        return tag == null ? "none" : tag.value();
    }

    public static void main(String[] args) throws Exception {
        Field names = Sample.class.getDeclaredField("names");
        System.out.println("erased=" + names.getType().getTypeName());
        System.out.println("generic=" + names.getGenericType().getTypeName());

        AnnotatedType annotated = names.getAnnotatedType();
        System.out.println("root=" + label(annotated));
        System.out.println(
                "parameterized="
                + (annotated instanceof AnnotatedParameterizedType)
        );

        AnnotatedType first =
                ((AnnotatedParameterizedType) annotated)
                .getAnnotatedActualTypeArguments()[0];
        System.out.println(
                "argument=" + first.getType().getTypeName()
                + ",tag=" + label(first)
        );

        AnnotatedParameterizedType scores =
                (AnnotatedParameterizedType) Sample.class
                .getDeclaredField("scores").getAnnotatedType();

        AnnotatedType[] pair =
                scores.getAnnotatedActualTypeArguments();
        System.out.println("key=" + label(pair[0]));

        AnnotatedType nested =
                ((AnnotatedParameterizedType) pair[1])
                .getAnnotatedActualTypeArguments()[0];
        System.out.println("nested=" + label(nested));

        AnnotatedType plain =
                Sample.class.getDeclaredField("plain").getAnnotatedType();
        System.out.println(
                "plain parameterized="
                + (plain instanceof AnnotatedParameterizedType)
        );
        System.out.println(
                "plain argument="
                + label(((AnnotatedParameterizedType) plain)
                        .getAnnotatedActualTypeArguments()[0])
        );
        System.out.println(
                "single parameterized="
                + (Sample.class.getDeclaredField("single")
                        .getAnnotatedType()
                        instanceof AnnotatedParameterizedType)
        );

        Method accept =
                Sample.class.getDeclaredMethod("accept", List.class);
        System.out.println(
                "method parameter="
                + label(((AnnotatedParameterizedType)
                        accept.getAnnotatedParameterTypes()[0])
                        .getAnnotatedActualTypeArguments()[0])
        );

        Method read = Sample.class.getDeclaredMethod("read");
        System.out.println(
                "method result="
                + label(((AnnotatedParameterizedType)
                        read.getAnnotatedReturnType())
                        .getAnnotatedActualTypeArguments()[0])
        );
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac AnnotatedTypeDemo.java
java AnnotatedTypeDemo
```

Đầu ra:

```text
erased=java.util.List
generic=java.util.List<java.lang.String>
root=outer
parameterized=true
argument=java.lang.String,tag=element
key=key
nested=nested
plain parameterized=true
plain argument=none
single parameterized=false
method parameter=parameter
method result=result
```

Giải thích:

1. `Field.getType()` chỉ cho class đã erasure: `java.util.List`.
2. `Field.getGenericType()` cho `java.util.List<java.lang.String>`, nhưng chưa phải một cây nút chứa type-use annotations.
3. `Field.getAnnotatedType()` trả nút ngoài; `label(annotated)` đọc `@Tag("outer")`.
4. `getAnnotatedActualTypeArguments()[0]` trả nút của `String` trong `List<...>`; nút ấy có `@Tag("element")`.
5. Field `scores` chứa `Map` với **hai đối số kiểu**. Nút thứ nhất gắn `key`, nút thứ hai là `List<Integer>`; phải đi tiếp vào nút thứ hai mới lấy được `nested` trên `Integer`.
6. Field `plain` chứng minh việc **không có annotation** trên generic argument không làm mất tính chất parameterized. Field `single` chứng minh không phải mọi `AnnotatedType` đều ép được thành `AnnotatedParameterizedType`.
7. `getAnnotatedParameterTypes()[0]` và `getAnnotatedReturnType()` cũng có thể dẫn đến type argument được annotation như field.

Chương trình đọc metadata bằng Reflection, **không cần instance của `Sample`** và không gọi `accept()`/`read()`. Nó cũng không dựa vào thứ tự của `getDeclaredFields()` hay `getDeclaredMethods()`, vì luôn tra cứu từng field/method bằng tên và kiểu tham số tương ứng.

## 5. Cách đọc annotation ở từng đối số kiểu

`AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` trả về `AnnotatedType[]`. Vị trí mảng tương ứng với **vị trí đối số generic được khai báo trong type use**, ví dụ:

```java
Map<@Tag("key") String, @Tag("value") Integer> data;
```

- `args[0]` biểu diễn `String`; `args[0].getAnnotation(Tag.class)` có `value() == "key"`.
- `args[1]` biểu diễn `Integer`; `args[1].getAnnotation(Tag.class)` có `value() == "value"`.

Khi không có annotation `Tag` tại nút đang xét, `getAnnotation(Tag.class)` trả `null`. Dùng `getAnnotations()` để đọc các type-use annotations hiện diện ở **chính nút ấy**, hoặc `getDeclaredAnnotations()` khi cần các annotation được khai báo trực tiếp tại nút. Cần kiểm tra `@Retention(RUNTIME)` của các annotation muốn đọc.

Một hình thức xử lý có kiểm tra an toàn:

```java
AnnotatedType typeUse = field.getAnnotatedType();

if (typeUse instanceof AnnotatedParameterizedType pt) {
    AnnotatedType[] typeArgs = pt.getAnnotatedActualTypeArguments();

    for (int i = 0; i < typeArgs.length; i++) {
        AnnotatedType argument = typeArgs[i];
        Tag tag = argument.getAnnotation(Tag.class);
        // tag có thể null nếu đối số thứ i không được gắn @Tag.
    }
}
```

Đây là đoạn minh họa trong ngữ cảnh đã có `Field field` và annotation `Tag` trong scope. Nếu cần lấy annotation trên **container type** `List` hoặc `Map`, truy vấn `typeUse.getAnnotation(Tag.class)` trước khi duyệt các phần tử; không nên dùng `field.getAnnotation(Tag.class)` để thay thế.

### Field annotation khác type-use annotation

Một annotation có `@Target(ElementType.FIELD)` nhắm vào **khai báo field** và được tra qua `Field.getAnnotation`. Một annotation chỉ có `@Target(ElementType.TYPE_USE)` nhắm vào **lần dùng kiểu** và được tra qua `Field.getAnnotatedType().getAnnotation` hoặc các nút con.

Nếu annotation có nhiều target như `FIELD` và `TYPE_USE`, cần đọc tài liệu về **ngữ cảnh cú pháp và vị trí annotation** để xác định nó được lưu ở đâu. Không nên suy rằng `field.getAnnotations()` luôn chứa mọi annotation trên generic arguments.

Điều tương tự xảy ra với method: `method.getAnnotations()` nói về **annotation trên khai báo method**, còn `method.getAnnotatedReturnType()` nói về **annotation trên kiểu trả về**. Với tham số, `Parameter.getAnnotation` liên quan annotation trên khai báo parameter, khác với `Parameter.getAnnotatedType()` của kiểu tham số ấy.

## 6. Kiểu generic lồng nhau: phải duyệt đệ quy

Một `Map<String, List<Integer>>` là cây nhiều cấp. `getAnnotatedActualTypeArguments()` của `Map` **chỉ trả về hai nút con trực tiếp**: `String` và `List<Integer>`; nó không tự flatten đến `Integer`.

Một thuật toán duyệt kiểu tổng quát có thể phân nhánh theo các subinterface:

| Kiểu nút | API để tiếp tục duyệt |
| --- | --- |
| `AnnotatedParameterizedType` | `getAnnotatedActualTypeArguments()`; khi cần nested owner: `getAnnotatedOwnerType()` |
| `AnnotatedArrayType` | `getAnnotatedGenericComponentType()` |
| `AnnotatedWildcardType` | `getAnnotatedUpperBounds()`, `getAnnotatedLowerBounds()` |
| `AnnotatedTypeVariable` | `getAnnotatedBounds()` |
| `AnnotatedType` thông thường | Đọc annotation ngay tại nút; có thể không có nút con cần duyệt |

Ví dụ (mảnh mã minh họa không độc lập):

```java
static void visit(AnnotatedType type) {
    // Xử lý annotation trên chính type tại đây.

    if (type instanceof AnnotatedParameterizedType pt) {
        for (AnnotatedType arg : pt.getAnnotatedActualTypeArguments()) {
            visit(arg);
        }
    } else if (type instanceof AnnotatedArrayType at) {
        visit(at.getAnnotatedGenericComponentType());
    } else if (type instanceof AnnotatedWildcardType wt) {
        for (AnnotatedType upper : wt.getAnnotatedUpperBounds()) {
            visit(upper);
        }
        for (AnnotatedType lower : wt.getAnnotatedLowerBounds()) {
            visit(lower);
        }
    } else if (type instanceof AnnotatedTypeVariable tv) {
        for (AnnotatedType bound : tv.getAnnotatedBounds()) {
            visit(bound);
        }
    }
}
```

Đoạn này cần import các interface `AnnotatedType`, `AnnotatedParameterizedType`, `AnnotatedArrayType`, `AnnotatedWildcardType`, `AnnotatedTypeVariable`; giả định đã đặt trong class Java. **Lưu ý quan trọng:** Type-variable bounds có thể tự tham chiếu hoặc tạo đồ thị kiểu lặp (ví dụ `T extends Comparable<T>`), vì vậy một thuật toán duyệt metadata production cần **phát hiện chu trình / giới hạn tập nút đã thăm** thay vì đệ quy vô hạn. `visit` ở trên chỉ trình bày các nhánh API, chưa phải thư viện duyệt hoàn chỉnh.

Nếu generic argument là `List<@Tag String>`, nút argument là **`AnnotatedParameterizedType`**, không phải nút leaf. Nếu là `? extends @Tag Number`, cần đi vào `AnnotatedWildcardType.getAnnotatedUpperBounds()`. Nếu là `String @Tag []`, cần chú ý annotation gắn vào vị trí mảng hay thành phần mảng theo cú pháp Java; đường đi nằm ở `AnnotatedArrayType`.

## 7. getAnnotatedOwnerType() và nested generic types

Với một lần dùng kiểu nested/member type, annotation có thể nằm trên **owner**. `AnnotatedParameterizedType.getAnnotatedOwnerType()` trả về biểu diễn annotated type của kiểu bao ngoài nếu có, thay vì gộp annotation của owner vào các type arguments của nested type.

Ví dụ ý tưởng:

```java
class Outer<T> {
    class Inner<U> {}
}
```

Nếu code dùng một kiểu như `Outer<String>.Inner<Integer>` và có annotation gắn vào lần sử dụng `Outer<String>`, một công cụ phân tích metadata phải quan sát thêm `getAnnotatedOwnerType()` của nút Inner. Nó không nên giả định annotation owner xuất hiện trong `Inner`'s `getAnnotatedActualTypeArguments()`.

Đối với kiểu top-level không có owner, `getAnnotatedOwnerType()` trả `null`. Tài liệu Oracle cũng lưu ý rằng `getAnnotatedActualTypeArguments()` **có thể là mảng rỗng** trong một số trường hợp hiếm, chẳng hạn nút biểu diễn non-parameterized member type lồng bên trong parameterized type. Vì vậy đừng tự suy ra số phần tử chỉ dựa vào việc cast thành `AnnotatedParameterizedType`; luôn dùng độ dài mảng thực tế.

Hợp đồng `getAnnotatedOwnerType()` có từ Java 9; ví dụ và kiến thức trong bài áp dụng tốt với Java 17+.

## 8. Giới hạn và những điều API không làm

**Không thay đổi runtime generic erasure:** `List<@Tag String>` và `List<String>` vẫn có cùng raw class `List.class`. Annotation không làm JVM tạo một class mới tên `ListOfTagString`, cũng không thay đổi nội dung các phần tử `List`.

**Không tự xác thực giá trị:** Chỉ có `@Tag` không khiến Java tự kiểm tra từng String trong list. Nếu cần enforce rule, framework cần code để đọc annotation đúng vị trí và kiểm tra dữ liệu.

**Không giải generic type variable cho từng subclass:** `Box<T>` được thừa kế dưới dạng `Box<String>` không tự biến mọi `TypeVariable` trên khai báo của `Box` thành `String` khi gọi Reflection trên declaration gốc. Muốn hiểu giá trị type argument theo một subclass, cần xét mapping `TypeVariable` và generic superclass/interface riêng.

**Không đảm bảo annotations luôn có thể được đọc:** Nếu retention không phải `RUNTIME`, Reflection sẽ không thấy annotation như mong muốn; class-file biến đổi, giới hạn metadata hoặc các hình thức type use khác cũng cần được xử lý đúng contract.

**Không phải lúc nào cũng là `AnnotatedParameterizedType`:** `int`, `String`, `String[]`, wildcard/type variable đều có thể yêu cầu những dạng `AnnotatedType` khác. Kiểm tra bằng `instanceof`.

**Không trộn vị trí và tên annotation:** Annotation cùng loại `Tag` có thể xuất hiện đồng thời ở container, type argument, wildcard bound và array dimension với giá trị khác nhau. Đọc annotation ở sai nút dễ dẫn đến báo cáo metadata sai dù code không ném exception.

## 9. Sai lầm phổ biến và cách khắc phục

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| Dùng `Field.getType()` để tìm annotation trên `List<@Tag String>` | `getType()` chỉ trả kiểu raw sau erasure; dùng `getAnnotatedType()` |
| Dùng `Field.getGenericType()` rồi nghĩ `ParameterizedType` mang đầy đủ type-use annotations | `ParameterizedType` mang cấu trúc generic; muốn annotation ở từng vị trí dùng `AnnotatedParameterizedType` |
| Đọc `annotated.getAnnotations()` và cho rằng có toàn bộ annotation của các type argument | Chỉ đọc annotation ở nút đang xét; cần duyệt `getAnnotatedActualTypeArguments()` |
| Thấy `getAnnotations()` rỗng thì khẳng định không phải generic | Generic type use vẫn có thể là `AnnotatedParameterizedType` nhưng không mang annotation |
| Cast mọi `AnnotatedType` sang `AnnotatedParameterizedType` | Kiểm tra `instanceof`, vì có array, wildcard, type variable và kiểu thông thường |
| Tưởng `List<@Tag String>` luôn bắt buộc các phần tử mang điều kiện `Tag` | Type-use annotation là metadata; runtime enforcement do ứng dụng/framework quyết định |
| Chỉ duyệt type arguments của `Map` rồi bỏ qua `List<Integer>` ở cấp trong | Duyệt đệ quy cây annotated types; chú ý chu trình của type variables |
| Tưởng `getAnnotatedOwnerType()` luôn trả một kiểu | Với top-level type, thường là `null` |
| Quên `@Retention(RUNTIME)` | Annotation không xuất hiện qua Reflection runtime nếu retention không phù hợp |
| Dùng `Method.getAnnotations()` thay cho type-use annotation ở kiểu trả về | Dùng `Method.getAnnotatedReturnType()` rồi duyệt đúng cấp |

## 10. Tự kiểm tra kiến thức

1. Với `List<@Tag String>`, lấy annotation gắn lên `String` bằng API nào? **`Field.getAnnotatedType()` → kiểm tra/cast `AnnotatedParameterizedType` → `getAnnotatedActualTypeArguments()[0].getAnnotation(Tag.class)`.**
2. `AnnotatedParameterizedType` là subinterface của gì? **`AnnotatedType`** (và gián tiếp kế thừa `AnnotatedElement`).
3. `ParameterizedType.getActualTypeArguments()` trả về kiểu dữ liệu gì? **`Type[]`**, khác với **`AnnotatedType[]`** của `getAnnotatedActualTypeArguments()`.
4. Với `@Tag("outer") List<@Tag("inner") String>`, `getAnnotation(Tag.class)` trên nút ngoài cho gì? **`outer`**, không phải `inner`.
5. Vì sao annotation dùng để đọc bằng Reflection cần `@Retention(RUNTIME)`? **Để runtime Reflection truy vấn được annotation.**
6. Không có annotation ở generic argument thì `getAnnotatedType()` có còn là `AnnotatedParameterizedType` không? **Có thể có**, vì hình dạng parameterized độc lập với việc annotation có hiện diện.
7. Trong `Map<String, List<@Tag Integer>>`, làm sao đến `Integer`? **Đi vào đối số thứ hai của Map, rồi vào đối số thứ nhất của List**.
8. Annotation trên `? extends @Tag Number` nằm ở nút nào? **Nút upper bound** của `AnnotatedWildcardType`.
9. Có được cast `String` plain type use thẳng sang `AnnotatedParameterizedType` không? **Không**, nên dùng `instanceof`.
10. Type-use annotation có tự xác nhận dữ liệu trong một `List` thỏa rule không? **Không**, nó là metadata mà công cụ khác có thể diễn giải.

## 11. Tài liệu chính thức

- [Oracle Java SE 21 — `AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html): `getAnnotatedActualTypeArguments()`, `getAnnotatedOwnerType()` và lưu ý mảng rỗng trong nested type.
- [Oracle Java SE 21 — `AnnotatedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedType.html): `getType()`, `getAnnotations()`, `getAnnotation()` và các subinterface.
- [Oracle Java SE 21 — `Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html): `getType()`, `getGenericType()`, `getAnnotatedType()`.
- [Oracle Java SE 21 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/ParameterizedType.html): đối số kiểu generic chưa gắn thông tin annotation theo từng type use.
- [Java Language Specification SE 21 — §9.7 Annotations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html#jls-9.7): vị trí annotation và type-use annotation.

**Ghi nhớ:** `ParameterizedType` cho biết **các kiểu generic nào** được sử dụng, còn `AnnotatedParameterizedType` cho phép biết **annotation nằm trên chính generic type use hoặc từng đối số kiểu ở vị trí nào**. Muốn đọc đúng `List<@Tag String>` hay generic lồng nhau, phải duyệt cây `AnnotatedType` thay vì chỉ xem raw type hoặc một annotation array ở nút ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-004">AnnotatedArrayType và annotation ở các chiều mảng</a>

<details>
<summary>Xem chi tiết</summary>

# `AnnotatedArrayType` và annotation ở các chiều mảng — Java Reflection

## 1. Vấn đề: một mảng nhiều chiều có nhiều vị trí được annotation

Trong Java, annotation có `@Target(ElementType.TYPE_USE)` có thể nằm **trên kiểu phần tử** hoặc **trên từng chiều của kiểu mảng**. Ví dụ:

~~~java
@Pos("element") String @Pos("outer") [] @Pos("inner") [] matrix;
~~~

Giả sử `@Pos` là annotation TYPE_USE được giữ ở runtime. Khai báo trên có ba vị trí độc lập:

- `@Pos("outer")`: vị trí **mảng ngoài cùng**, có kiểu nền `String[][]`.
- `@Pos("inner")`: vị trí **mảng thành phần**, có kiểu nền `String[]`.
- `@Pos("element")`: vị trí **kiểu phần tử cuối cùng**, có kiểu nền `String`.

Nếu chỉ gọi `Field.getType()` hoặc `Field.getGenericType()`, ta nhận cấu trúc kiểu nhưng **không truy vấn được annotation riêng tại từng chiều**. Java Reflection biểu diễn cây này bằng `AnnotatedType`, cụ thể là các nút `AnnotatedArrayType`.

**Quy tắc ghi nhớ:** một mảng có `n` chiều cần được duyệt qua `n` nút `AnnotatedArrayType` trước khi tới kiểu phần tử, có thể được annotation riêng.

## 2. `AnnotatedArrayType` là gì?

`java.lang.reflect.AnnotatedArrayType` là giao diện con của `AnnotatedType` (từ Java 8) mô tả một **vị trí sử dụng kiểu mảng**, kèm các type-use annotations của **chính vị trí đó**.

Hai thao tác quan trọng:

~~~java
AnnotatedType root = field.getAnnotatedType();
Type underlying = root.getType();

if (root instanceof AnnotatedArrayType array) {
    AnnotatedType component =
            array.getAnnotatedGenericComponentType();
}
~~~

Đây là đoạn minh họa trong một phương thức có biến `Field field`, import `java.lang.reflect.*`; chưa phải chương trình độc lập.

- `root.getType()` trả **kiểu nền** dạng `java.lang.reflect.Type`. Với `String[][]`, giá trị đó thông thường là `Class` của kiểu mảng; với `T[]`, nó có thể là `GenericArrayType`.
- `root.getAnnotation(Pos.class)` hỏi annotation nằm tại **nút root hiện tại**, không tự gộp annotation của các chiều bên trong.
- `getAnnotatedGenericComponentType()` đi xuống **chính xác một mức kiểu thành phần**, nhưng vẫn giữ metadata type-use annotation tại mức tiếp theo.
- Sau khi đi qua đủ chiều, ta đến kiểu phần tử như `String`, `int`, `T` hoặc `List<String>`. Nút cuối không nhất thiết là `AnnotatedArrayType`.
- `AnnotatedArrayType.getAnnotatedOwnerType()` trả `null`: một kiểu mảng không phải trường hợp owner type của một member class.

Javadoc của `AnnotatedArrayType` không chỉ dành cho mảng có annotation. Ngay cả khi một chiều **không có annotation**, nó vẫn có thể là nút `AnnotatedArrayType`; khi đó `getAnnotations().length == 0` và `getAnnotation(Pos.class) == null`.

## 3. Phân biệt annotation trên phần tử và trên dấu `[]`

So sánh ba khai báo:

~~~java
@Pos("element") String[] first;
String @Pos("dimension") [] second;
@Pos("element") String @Pos("dimension") [] third;
~~~

Giả sử chúng nằm trong một class dùng annotation `@Pos` có `TYPE_USE` và `RUNTIME`.

| Khai báo | Annotation ở nút mảng `String[]` | Annotation ở nút phần tử `String` |
| --- | --- | --- |
| `@Pos("element") String[] first` | Không | `element` |
| `String @Pos("dimension") [] second` | `dimension` | Không |
| `@Pos("element") String @Pos("dimension") [] third` | `dimension` | `element` |

**Đặt annotation trước `String` không tương đương đặt annotation trước `[]`.**

Mã kiểm tra từng nơi:

~~~java
AnnotatedType root = field.getAnnotatedType();
Pos onArray = root.getAnnotation(Pos.class);

AnnotatedType component =
        ((AnnotatedArrayType) root).getAnnotatedGenericComponentType();
Pos onElement = component.getAnnotation(Pos.class);
~~~

Ví dụ chỉ minh họa khi `field` là field mảng, `Pos` khả dụng và đã khai báo/xử lý ngoại lệ ở phần gọi Reflection. Không ép kiểu sang `AnnotatedArrayType` khi field thực tế không có kiểu mảng.

### Với mảng hai chiều

~~~java
@Pos("element") String @Pos("outer") [] @Pos("inner") [] matrix;
~~~

Ta đi theo thứ tự:

~~~text
AnnotatedArrayType: String[][]   → @Pos("outer")
    │ getAnnotatedGenericComponentType()
    ▼
AnnotatedArrayType: String[]     → @Pos("inner")
    │ getAnnotatedGenericComponentType()
    ▼
AnnotatedType:      String      → @Pos("element")
~~~

Chiều ngoài là **nút đầu tiên** mà `Field.getAnnotatedType()` trả về, không phải chiều cuối. Vì thế đừng đảo thứ tự annotation trong lúc tạo schema JSON hoặc thông báo lỗi.

Đối với ba chiều trở lên, áp dụng cùng quy tắc đệ quy: mỗi lần gọi `getAnnotatedGenericComponentType()` bỏ đi một chiều mảng bên ngoài và chuyển xuống nút kiểu thành phần kế tiếp.

## 4. Lấy metadata annotation ở mức nào?

Có ba câu hỏi khác nhau:

| Câu hỏi | API |
| --- | --- |
| Field được khai báo với kiểu gì, bỏ qua generic? | `field.getType()` |
| Field có cấu trúc generic gì? | `field.getGenericType()` |
| Type-use annotation đặt trên chiều mảng và phần tử nào? | `field.getAnnotatedType()`, sau đó duyệt `AnnotatedArrayType` |

Một `Field` cũng là `AnnotatedElement`, nhưng:

~~~java
field.getDeclaredAnnotations();
~~~

hỏi annotation khai báo **trên chính field**, không tự gom annotation type-use trên `String` hay từng `[]`.

Ngay cả khi `field.getDeclaredAnnotations()` trả mảng trống, `field.getAnnotatedType()` và các node con vẫn có thể chứa annotation ở các vị trí kiểu. Đó không phải dữ liệu mâu thuẫn; chúng mô tả **hai mục tiêu annotation khác nhau**.

Chú ý `getAnnotatedType().getType()` trả `Type`, không luôn là `Class<?>`. Nếu làm việc với kiểu generic array như `T[]`, ép trực tiếp sang `Class<?>` có thể gây `ClassCastException`.

## 5. Chương trình Java 17+ hoàn chỉnh: duyệt từng chiều mảng

Lưu mã sau vào **`AnnotatedArrayDemo.java`**:

~~~java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

public class AnnotatedArrayDemo {
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Pos {
        String value();
    }

    static class Sample<T extends @Pos("bound") Number> {
        public @Pos("element") String
                @Pos("outer") [] @Pos("inner") [] matrix;

        public List<
                @Pos("member-element") String
                @Pos("member-dimension") []> groups;

        public T @Pos("generic-dimension") [] genericArray;

        public @Pos("return-element") String
                @Pos("return-dimension") [] echo(
                    @Pos("param-element") String
                    @Pos("param-dimension") [] words) {
            return words;
        }
    }

    static String ann(AnnotatedType type) {
        Pos p = type.getAnnotation(Pos.class);
        return p == null ? "-" : p.value();
    }

    static void walk(String label, AnnotatedType type) {
        int depth = 0;
        while (type instanceof AnnotatedArrayType array) {
            System.out.println(label + "[" + depth + "] "
                    + type.getType().getTypeName()
                    + " @" + ann(type));
            type = array.getAnnotatedGenericComponentType();
            depth++;
        }
        System.out.println(label + "[" + depth + "] "
                + type.getType().getTypeName()
                + " @" + ann(type));
    }

    public static void main(String[] args) throws Exception {
        Field matrix = Sample.class.getDeclaredField("matrix");
        walk("matrix", matrix.getAnnotatedType());
        System.out.println("field annotations="
                + matrix.getDeclaredAnnotations().length);

        Field groups = Sample.class.getDeclaredField("groups");
        AnnotatedParameterizedType parameterized =
                (AnnotatedParameterizedType) groups.getAnnotatedType();
        walk("groups.arg",
                parameterized.getAnnotatedActualTypeArguments()[0]);

        Field generic = Sample.class.getDeclaredField("genericArray");
        System.out.println("generic underlying is GenericArrayType="
                + (generic.getAnnotatedType().getType()
                        instanceof GenericArrayType));
        walk("generic", generic.getAnnotatedType());

        TypeVariable<?> t = Sample.class.getTypeParameters()[0];
        System.out.println("T.bound @"
                + ann(t.getAnnotatedBounds()[0]));

        Method echo = Sample.class.getDeclaredMethod(
                "echo", String[].class);
        walk("return", echo.getAnnotatedReturnType());
        walk("parameter", echo.getAnnotatedParameterTypes()[0]);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AnnotatedArrayDemo.java
java AnnotatedArrayDemo
~~~

Stdout:

~~~text
matrix[0] java.lang.String[][] @outer
matrix[1] java.lang.String[] @inner
matrix[2] java.lang.String @element
field annotations=0
groups.arg[0] java.lang.String[] @member-dimension
groups.arg[1] java.lang.String @member-element
generic underlying is GenericArrayType=true
generic[0] T[] @generic-dimension
generic[1] T @-
T.bound @bound
return[0] java.lang.String[] @return-dimension
return[1] java.lang.String @return-element
parameter[0] java.lang.String[] @param-dimension
parameter[1] java.lang.String @param-element
~~~

### Giải thích đầy đủ

1. **`matrix`**: `walk` đi từ kiểu `String[][]` xuống `String[]`, rồi tới `String`. Annotation ở mỗi nút lần lượt là `outer`, `inner`, `element`. Tên kiểu nền được in bằng `Type.getTypeName()`, không phụ thuộc `toString()` của implementation Reflection.
2. **`field annotations=0`**: annotation `@Pos` chỉ có target `TYPE_USE`. Nó gắn lên các phần của **kiểu field**, không phải annotation khai báo riêng trên đối tượng `Field`.
3. **`groups`**: kiểu ngoài cùng là `List<...>`, nên root là `AnnotatedParameterizedType`. Phải đi vào `getAnnotatedActualTypeArguments()[0]`, rồi mới gặp `AnnotatedArrayType` biểu diễn `String[]`.
4. **`genericArray`**: kiểu nền là `T[]`, được biểu diễn bằng `GenericArrayType`, không phải `Class` của một mảng đã biết kiểu phần tử cụ thể. Nhưng `AnnotatedArrayType` vẫn giúp đi từ `T[]` xuống type variable `T`.
5. **`T.bound`**: annotation `@Pos("bound")` ở khai báo `T extends Number`; nó **không tự động xuất hiện trên mọi lần dùng `T`**, nên `generic[1]` có `@-`. Phải lấy bound bằng `TypeVariable.getAnnotatedBounds()`.
6. **Method**: `getAnnotatedReturnType()` và `getAnnotatedParameterTypes()` cũng giữ annotation theo từng chiều mảng; không chỉ field mới làm được.

Ví dụ này kiểm tra metadata, không tạo đối tượng `Sample` và không đọc nội dung mảng; Reflection đọc được cấu trúc khai báo **mà không cần giá trị mảng thực tế**.

## 6. Generic array, parameterized type và kiểu thành phần

Có hai kiểu mảng cần phân biệt khi xem `Type` nền:

| Khai báo | `AnnotatedType` root | `getType()` root thường là |
| --- | --- | --- |
| `String[][]` | `AnnotatedArrayType` | `Class` đại diện `String[][].class` |
| `int[]` | `AnnotatedArrayType` | `Class` đại diện `int[].class` |
| `T[]` trong generic class | `AnnotatedArrayType` | `GenericArrayType` |
| `List<String>[]` trong khai báo hợp lệ | `AnnotatedArrayType` | `GenericArrayType` |
| `List<String[]>` | `AnnotatedParameterizedType` ở root | `ParameterizedType`; nút generic argument mới là `AnnotatedArrayType` |

Trường hợp `List<String[]>`:

~~~text
AnnotatedParameterizedType: List<String[]>
    └── AnnotatedArrayType: String[]
            └── AnnotatedType: String
~~~

Trong khi với `List<String>[]`:

~~~text
AnnotatedArrayType: List<String>[]
    └── AnnotatedParameterizedType: List<String>
            └── AnnotatedType: String
~~~

Điểm mấu chốt: hai khai báo này **khác vị trí mảng trong cây kiểu**. Một cái là **danh sách các mảng**; cái kia là **mảng các danh sách**. Việc ép kiểu root phải dựa trên cấu trúc thực tế, không dựa vào tên field hoặc giả định “có generic thì root là `AnnotatedParameterizedType`”.

Khi muốn tính số chiều của một nút mảng, duyệt `while (type instanceof AnnotatedArrayType)` và đếm số lần đi xuống. Với một cây generic phức tạp, việc duyệt đầy đủ cần thêm nhánh cho `AnnotatedParameterizedType`, `AnnotatedWildcardType`, `AnnotatedTypeVariable`; riêng `AnnotatedArrayType` chỉ giải quyết đoạn đường qua các chiều mảng.

## 7. Vì sao không dùng `Class.getComponentType()` thay thế?

Có hai API có tên gần giống nhưng nhiệm vụ khác nhau.

`Class.getComponentType()`:

~~~java
Class<?> component = String[][].class.getComponentType();
// String[].class
~~~

trả **Class kiểu thành phần** của một kiểu mảng runtime. Nó không giữ type-use annotations và không trực tiếp biểu diễn `T[]` có kiểu nền generic.

`AnnotatedArrayType.getAnnotatedGenericComponentType()`:

~~~java
AnnotatedType node = field.getAnnotatedType();
AnnotatedType next =
        ((AnnotatedArrayType) node).getAnnotatedGenericComponentType();
~~~

trả **AnnotatedType của kiểu thành phần**, tức vẫn truy vấn được annotation gắn với chiều tiếp theo hoặc kiểu phần tử. Trong trình phân tích schema cần giữ annotation theo vị trí, API thứ hai là quan trọng.

**Đừng chỉ đi xuống `getType()` rồi tìm cách khôi phục annotation:** thông tin annotation không được mã hóa thành nội dung của `Class`, `ParameterizedType` hay `GenericArrayType` nền. Bạn phải đi qua **cây `AnnotatedType`** song song với cây `Type`.

## 8. Annotation ở các dấu ngoặc mảng sau tên biến

Java cho phép cú pháp mảng kiểu cũ với `[]` sau tên biến, ví dụ:

~~~java
// Không khuyến khích cho mã mới:
String[] names[];
~~~

Dạng này có thể gây nhầm lẫn về vị trí chiều mảng và annotation, nhất là khi trộn với `[]` đặt gần kiểu. Trong mã mới, ưu tiên đưa toàn bộ các chiều mảng vào phần khai báo **type** trước tên biến:

~~~java
String[][] names;
~~~

Khi có type-use annotation, đặt dấu chú thích sát chiều mảng mà nó mô tả:

~~~java
@Pos("element") String @Pos("outer") [] @Pos("inner") [] names;
~~~

Nếu đang phân tích source code của thư viện hoặc bộ sinh mã, đừng suy luận vị trí annotation chỉ bằng cách đếm chuỗi `[]` trong văn bản; hãy kiểm tra kiểu tại vị trí sử dụng theo quy tắc Java và metadata biên dịch. Thứ tự chiều có thể khó đọc khi dùng cú pháp đặt bracket sau tên biến.

## 9. Bẫy của `@Target` và `@Retention`

Một annotation muốn được đọc tại runtime trong **vị trí sử dụng kiểu mảng** nên có:

~~~java
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface Pos {
    String value();
}
~~~

Đây là mảnh khai báo cần import `java.lang.annotation.*` và đặt trong ngữ cảnh Java phù hợp.

- `TYPE_USE` cho phép annotation gắn trên kiểu phần tử và các chiều mảng. `ElementType.FIELD` chỉ dành cho vị trí khai báo field, không thay cho `TYPE_USE`.
- `RetentionPolicy.RUNTIME` cần thiết nếu muốn `AnnotatedType.getAnnotation()` đọc được annotation khi JVM đang chạy. `CLASS` vẫn ghi metadata vào class file nhưng không hiển thị qua Java Reflection runtime thông thường; `SOURCE` không giữ qua biên dịch.
- `@Target({FIELD, TYPE_USE})` cho phép dùng annotation ở nhiều vị trí. Khi đó cần xác định chính xác bạn muốn metadata của declaration hay metadata của type-use.
- `AnnotatedType.getAnnotation(Pos.class)` chỉ kiểm tra **nút hiện tại**. Nếu `null`, hãy duyệt các chiều hoặc các generic argument tương ứng, thay vì kết luận toàn bộ field không có annotation.
- Không có lời gọi nào trên `AnnotatedArrayType` làm JVM tự động kiểm tra các phần tử mảng có `null` hay không. Annotation là metadata; validation phải được ứng dụng/framework cài đặt riêng.

## 10. Những lỗi thường gặp khi viết code Reflection

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “`String[][]` là một mảng, chỉ cần đọc annotation ở root.” | Có **hai chiều**, cần hai nút `AnnotatedArrayType`, rồi đến kiểu phần tử. |
| “`@A String[]` và `String @A []` mang ý nghĩa type-use như nhau.” | `@A` đầu tiên gắn trên phần tử; dạng sau gắn trên chiều mảng. |
| “`getAnnotatedGenericComponentType()` luôn trả `AnnotatedArrayType`.” | Sau chiều cuối, có thể trả AnnotatedType của `String`, `T`, `List<...>`, v.v. |
| “`AnnotatedArrayType.getType()` luôn là `Class`.” | `T[]` và mảng của một parameterized type có thể dùng `GenericArrayType`. |
| “Annotation ở bound của `T` tự xuất hiện ở `T[]`.” | Bound annotation và annotation của lần sử dụng `T` là các vị trí khác nhau. |
| “Đọc `field.getDeclaredAnnotations()` là thấy annotation trên `[]`.” | Cần `field.getAnnotatedType()` và duyệt node mảng. |
| “`getAnnotation()` trên một chiều tìm xuyên mọi chiều còn lại.” | Chỉ hỏi node hiện tại; phải đệ quy qua component. |
| “Không có annotation trong kết quả tức kiểu mảng không được annotation.” | Có thể đang ở sai node hoặc annotation không có retention runtime. |
| “Type-use annotation sẽ được JVM tự động thực thi để validate.” | Reflection chỉ cung cấp metadata; mã ứng dụng phải triển khai quy tắc nghiệp vụ. |

## 11. Mẫu duyệt mảng dùng trong ứng dụng

Một tiện ích **chỉ duyệt đường mảng**, dùng cho mọi trường mảng có thể có annotation ở từng chiều:

~~~java
static void visitArray(AnnotatedType type) {
    int dimension = 0;
    while (type instanceof AnnotatedArrayType array) {
        // Ở đây đọc annotation của chiều mảng hiện tại.
        // Ví dụ: type.getAnnotations(), type.getType().getTypeName()
        dimension++;
        type = array.getAnnotatedGenericComponentType();
    }

    // Đã tới loại phần tử, vẫn có thể có annotation riêng.
    // Nếu là AnnotatedParameterizedType, có thể tiếp tục duyệt generics.
}
~~~

Đây là method minh họa trong một lớp có import `java.lang.reflect.*`; `dimension` đang biểu thị số mức mảng đã đi qua, không phải chỉ số chiều quy ước của một ứng dụng cụ thể.

Khi thiết kế schema/validator:

1. Lấy `Field.getAnnotatedType()` hoặc API tương đương cho method parameter, return type, record component.
2. Nếu nút là `AnnotatedArrayType`, lấy annotation tại **nút chiều mảng** rồi đi xuống `getAnnotatedGenericComponentType()`.
3. Nếu nút là `AnnotatedParameterizedType`, đi vào `getAnnotatedActualTypeArguments()` để kiểm tra mảng nằm trong generic argument.
4. Nếu nút là `AnnotatedWildcardType` hoặc `AnnotatedTypeVariable`, duyệt bounds đúng API.
5. Tách phần đọc metadata khỏi phần **đánh giá dữ liệu runtime**. Type-use annotation không tự kiểm tra nội dung mảng.
6. Kiểm tra `instanceof` trước khi ép kiểu, và không đánh đồng `Type` nền với `AnnotatedType`.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `@A String @B [] @C [] matrix` chứa annotation tại những vị trí nào?  
`@B` ở chiều mảng ngoài `String[][]`; `@C` ở chiều trong `String[]`; `@A` ở kiểu phần tử `String`.

**Câu 2.** `field.getAnnotatedType().getAnnotation(A.class)` không thấy `@A` thì có nghĩa không có annotation nào trên các chiều mảng?  
**Không.** Có thể annotation ở node con, phải đi theo `getAnnotatedGenericComponentType()`.

**Câu 3.** `T[]` với `T` là type parameter có `getType()` nền thường thuộc loại nào?  
**`GenericArrayType`**. Nhưng nút annotated root vẫn có thể là `AnnotatedArrayType`.

**Câu 4.** `List<String[]>` có root là `AnnotatedArrayType` không?  
**Không.** Root là `AnnotatedParameterizedType`; đi vào type argument mới gặp `AnnotatedArrayType`.

**Câu 5.** `List<String>[]` có root là `AnnotatedArrayType` không?  
**Có.** Đây là mảng của danh sách; component là parameterized type.

**Câu 6.** `Class.getComponentType()` và `AnnotatedArrayType.getAnnotatedGenericComponentType()` có hoán đổi được khi cần annotation mỗi chiều?  
**Không.** API `Class` không giữ annotation type-use của component; API `AnnotatedArrayType` có thể giữ được.

**Câu 7.** Một annotation có `@Target(TYPE_USE)` nhưng `@Retention(CLASS)` có đọc bằng `AnnotatedType.getAnnotation()` tại runtime không?  
**Không qua Reflection runtime thông thường.** Cần `RUNTIME`.

**Kết luận:** `AnnotatedArrayType` biểu diễn **từng mức của kiểu mảng đã được annotation**. Gọi `getAnnotation()` ở từng node, sau đó dùng `getAnnotatedGenericComponentType()` để đi từ chiều ngoài vào chiều trong và tới kiểu phần tử. Nhờ vậy không làm mất annotation khi phân tích mảng nhiều chiều, mảng generic hoặc mảng nằm trong generic argument.

### Tài liệu chính thức

- [JDK 17 — `AnnotatedArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedArrayType.html)
- [JDK 17 — `AnnotatedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html)
- [JDK 17 — `AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html)
- [JDK 17 — `GenericArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html)
- [JDK 17 — `Field.getAnnotatedType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#getAnnotatedType())
- [JLS 17 §10.1 — Array Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.1)
- [JLS 17 §9.7.4 — Where Annotations May Appear](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.4)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-005">AnnotatedWildcardType và annotation trên bound</a>

<details>
<summary>Xem chi tiết</summary>

# AnnotatedWildcardType và annotation trên bound trong Java Reflection

## 1. Bối cảnh: annotation có thể nằm ngay trên dấu `?` hoặc trên bound

Trong Java, wildcard là đối số kiểu dùng trong generic, chẳng hạn `List<?>`, `List<? extends Number>` và `List<? super Integer>`. Từ Java 8, annotation có `@Target(ElementType.TYPE_USE)` có thể xuất hiện **tại những vị trí sử dụng kiểu** (type use), bao gồm wildcard và kiểu của bound.

Hai vị trí khác nhau:

```java
List<@Mark("wildcard") ? extends @Mark("bound") Number> values;
```

- `@Mark("wildcard")` nằm **trên chính wildcard `?`**.
- `@Mark("bound")` nằm **trên kiểu `Number`** sau từ khóa `extends`.

Hai annotation này không thay thế nhau, dù cùng xuất hiện trong một khai báo generic. Khi dùng Reflection:

- `AnnotatedWildcardType.getAnnotation(Mark.class)` đọc annotation gắn với `?`.
- `AnnotatedWildcardType.getAnnotatedUpperBounds()[0].getAnnotation(Mark.class)` đọc annotation gắn với upper bound `Number`.

Nếu chỉ lấy `WildcardType` qua `Field.getGenericType()` rồi gọi `getUpperBounds()`, bạn quan sát được **kiểu của bound**, nhưng **không đọc được annotation type-use của bound** bằng API `Type` thuần. Muốn giữ thông tin annotation theo vị trí, cần nhánh `AnnotatedType` và các interface con.

**Mục tiêu:** hiểu `AnnotatedWildcardType`, truy cập annotation ở wildcard/upper bound/lower bound, trường hợp bound ngầm định, duyệt generic lồng nhau và các giới hạn của metadata runtime trong Java 17+.

## 2. Wildcard và bound theo ngôn ngữ Java

Ba hình thức thường gặp:

| Viết trong Java | Upper bound | Lower bound | Ý nghĩa kiểu |
| --- | --- | --- | --- |
| `?` | `Object` ngầm định | Không có lower bound tường minh | Wildcard không giới hạn tường minh |
| `? extends Number` | `Number` tường minh | Không có lower bound tường minh | Đối số kiểu chưa biết, bị chặn trên bởi `Number` |
| `? super Integer` | `Object` ngầm định | `Integer` tường minh | Đối số kiểu chưa biết, bị chặn dưới bởi `Integer` |

Ví dụ về annotation tại từng vị trí:

```java
List<@Mark("any") ?> any;
List<@Mark("wild") ? extends @Mark("upper") Number> upper;
List<@Mark("wild") ? super @Mark("lower") Integer> lower;
List<? extends @Mark("onlyUpper") Number> onlyUpper;
```

Các ví dụ là **khai báo field minh họa**; `Mark` cần được định nghĩa với `TYPE_USE` và `RUNTIME` như trong ví dụ đầy đủ ở mục 5.

Hãy chú ý cú pháp:

- `@Mark("wild") ?`: annotation ở **wildcard node**.
- `? extends @Mark("upper") Number`: annotation ở **upper-bound type node**.
- `? super @Mark("lower") Integer`: annotation ở **lower-bound type node**.
- `?` không có bound tường minh nhưng theo Reflection vẫn có upper bound là `Object` không được annotate.

Với cú pháp wildcard Java hiện nay, một wildcard không vừa có `extends` vừa có `super`, và không viết được một intersection bound dạng `? extends A & B` trong Java source. Tuy nhiên, các phương thức Reflection trả về **mảng** các bound; mã xử lý nên duyệt mảng thay vì tự giả định mọi runtime chỉ có phần tử [0].

## 3. `AnnotatedWildcardType` nằm ở đâu trong Reflection?

`java.lang.reflect.AnnotatedWildcardType` là interface mở rộng `AnnotatedType` (từ Java 8), đại diện cho wildcard type argument có thể được annotate; các upper/lower bounds cũng có thể mang annotation.

Các API chính:

| API | Trả về | Điều cần nhớ |
| --- | --- | --- |
| `AnnotatedWildcardType.getType()` | `Type` (thường là `WildcardType`) | Cho hình dạng generic, không phải nơi đọc annotation trên bound |
| `getAnnotations()` | `Annotation[]` | Annotation ở chính node wildcard |
| `getAnnotation(Mark.class)` | `Mark` hoặc `null` | Trực tiếp hỏi marker tại wildcard |
| `getAnnotatedUpperBounds()` | `AnnotatedType[]` | Bound phía trên **có thể có annotation riêng** |
| `getAnnotatedLowerBounds()` | `AnnotatedType[]` | Bound phía dưới **có thể có annotation riêng** |
| `getAnnotatedOwnerType()` | `null` với wildcard | Không phải API để truy cập upper/lower bound |

`AnnotatedWildcardType` thuộc nhánh **annotated**. Nó khác `java.lang.reflect.WildcardType`:

```java
WildcardType wildcard = (WildcardType) annotatedWildcard.getType();
Type[] upperTypes = wildcard.getUpperBounds();
Type[] lowerTypes = wildcard.getLowerBounds();

AnnotatedType[] annotatedUpper =
        annotatedWildcard.getAnnotatedUpperBounds();
AnnotatedType[] annotatedLower =
        annotatedWildcard.getAnnotatedLowerBounds();
```

Đây là **đoạn minh họa**: cần import `java.lang.reflect.*` và có biến `annotatedWildcard` đã được xác định.

- `WildcardType.getUpperBounds()` và `getLowerBounds()` trả kiểu generic thuần (`Type`), ví dụ `Number.class` hoặc `Integer.class`.
- `AnnotatedWildcardType.getAnnotatedUpperBounds()` và `getAnnotatedLowerBounds()` trả `AnnotatedType`, vừa chứa kiểu, vừa cho phép đọc annotation tại vị trí type-use của bound.
- `AnnotatedType.getType()` của bound cho kiểu liên quan; `AnnotatedType.getAnnotation(...)` cho annotation gắn **đúng bound đó**.

Nếu cần annotation ở wildcard **lẫn** annotation ở bound, không được chọn một trong hai loại metadata rồi bỏ loại kia.

## 4. Đi tới wildcard đúng cách từ Field, Method, Parameter

Giả sử có field:

```java
List<@Mark("wildcard") ? extends @Mark("bound") Number> numbers;
```

Đường đi từ field tới type annotation:

```text
Field numbers
  └── getAnnotatedType(): AnnotatedParameterizedType [List<...>]
       └── getAnnotatedActualTypeArguments()[0]
            └── AnnotatedWildcardType [? extends Number]
                 ├── getAnnotation(Mark.class)       -> "wildcard"
                 ├── getAnnotatedUpperBounds()
                 │     └── AnnotatedType [Number]
                 │           └── getAnnotation(Mark.class) -> "bound"
                 └── getAnnotatedLowerBounds()       -> empty
```

Dưới đây là các bước Reflection đúng cấu trúc:

```java
Field field = Sample.class.getDeclaredField("numbers");

// Không phải (AnnotatedWildcardType) field.getAnnotatedType():
// field là List<...>, lớp ngoài cùng là parameterized type.
AnnotatedParameterizedType listType =
        (AnnotatedParameterizedType) field.getAnnotatedType();

AnnotatedType argument = listType.getAnnotatedActualTypeArguments()[0];
AnnotatedWildcardType wildcard = (AnnotatedWildcardType) argument;

Mark wildcardMark = wildcard.getAnnotation(Mark.class);
AnnotatedType bound = wildcard.getAnnotatedUpperBounds()[0];
Mark boundMark = bound.getAnnotation(Mark.class);
```

Đoạn trên chỉ minh họa và cần khai báo `Sample.numbers`, định nghĩa `Mark`, các import `java.lang.reflect.*` và xử lý checked exceptions. Mã hoàn chỉnh chạy được nằm ở mục 5.

**Vì sao phải qua `AnnotatedParameterizedType` trước?** Bởi vì kiểu ngoài cùng là `List<...>`. Một đối số kiểu của `List` mới là wildcard `?`. Ép trực tiếp `field.getAnnotatedType()` thành `AnnotatedWildcardType` sẽ gây `ClassCastException`.

Các điểm xuất phát khác, tùy wildcard xuất hiện ở đâu:

- **Field:** `Field.getAnnotatedType()`.
- **Tham số method/constructor:** `Parameter.getAnnotatedType()` hoặc `Executable.getAnnotatedParameterTypes()`.
- **Kiểu trả về method:** `Method.getAnnotatedReturnType()`.
- **Superclass/interface generic có annotation:** `Class.getAnnotatedSuperclass()` và `Class.getAnnotatedInterfaces()`.
- **Ngoại lệ ở throws clause:** `Executable.getAnnotatedExceptionTypes()` khi cần.

Ở mỗi nhánh, tiếp tục dựa vào kiểu interface **thực** của `AnnotatedType`: `AnnotatedParameterizedType`, `AnnotatedWildcardType`, `AnnotatedArrayType` hoặc `AnnotatedTypeVariable`. Không giả định cấu trúc mọi khai báo giống nhau.

## 5. Ví dụ Java 17+ hoàn chỉnh: wildcard, extends, super và generic lồng nhau

Lưu tệp **`AnnotatedWildcardDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark {
    String value();
}

public class AnnotatedWildcardDemo {
    List<@Mark("question") ? extends @Mark("upper") Number> producers;

    List<@Mark("question") ? super @Mark("lower") Integer> consumers;

    List<@Mark("any") ?> unknown;

    List<? extends @Mark("onlyUpper") Number> onlyBound;

    Map<String, List<@Mark("nestedW") ?
            extends @Mark("nestedUpper") Number>> nested;

    static String mark(AnnotatedType type) {
        Mark annotation = type.getAnnotation(Mark.class);
        return annotation == null ? "-" : annotation.value();
    }

    static void show(String fieldName) throws NoSuchFieldException {
        Field field = AnnotatedWildcardDemo.class
                .getDeclaredField(fieldName);

        AnnotatedParameterizedType list =
                (AnnotatedParameterizedType) field.getAnnotatedType();

        AnnotatedWildcardType wildcard = (AnnotatedWildcardType)
                list.getAnnotatedActualTypeArguments()[0];

        System.out.println(fieldName + " wildcard=" + mark(wildcard));

        for (AnnotatedType upper : wildcard.getAnnotatedUpperBounds()) {
            System.out.println("  upper=" + upper.getType().getTypeName()
                    + " mark=" + mark(upper));
        }

        for (AnnotatedType lower : wildcard.getAnnotatedLowerBounds()) {
            System.out.println("  lower=" + lower.getType().getTypeName()
                    + " mark=" + mark(lower));
        }

        System.out.println("  lower count="
                + wildcard.getAnnotatedLowerBounds().length);
    }

    public static void main(String[] args) throws Exception {
        show("producers");
        show("consumers");
        show("unknown");
        show("onlyBound");

        Field field = AnnotatedWildcardDemo.class
                .getDeclaredField("nested");

        AnnotatedParameterizedType mapType =
                (AnnotatedParameterizedType) field.getAnnotatedType();

        AnnotatedParameterizedType listType =
                (AnnotatedParameterizedType)
                mapType.getAnnotatedActualTypeArguments()[1];

        AnnotatedWildcardType nestedWildcard =
                (AnnotatedWildcardType)
                listType.getAnnotatedActualTypeArguments()[0];

        System.out.println("nested wildcard=" + mark(nestedWildcard));
        AnnotatedType nestedUpper =
                nestedWildcard.getAnnotatedUpperBounds()[0];

        System.out.println("nested upper="
                + nestedUpper.getType().getTypeName()
                + " mark=" + mark(nestedUpper));
    }
}
```

Biên dịch và chạy:

```bash
javac AnnotatedWildcardDemo.java
java AnnotatedWildcardDemo
```

**Stdout mong đợi:**

```text
producers wildcard=question
  upper=java.lang.Number mark=upper
  lower count=0
consumers wildcard=question
  upper=java.lang.Object mark=-
  lower=java.lang.Integer mark=lower
  lower count=1
unknown wildcard=any
  upper=java.lang.Object mark=-
  lower count=0
onlyBound wildcard=-
  upper=java.lang.Number mark=onlyUpper
  lower count=0
nested wildcard=nestedW
nested upper=java.lang.Number mark=nestedUpper
```

### Giải thích kết quả

1. Với `producers`, annotation `question` nằm trên wildcard; `upper` nằm trên `Number`. `getAnnotatedUpperBounds()` có `Number`, không có lower bound tường minh.
2. Với `consumers`, lower bound `Integer` mang `lower`. Upper bound mặc định là `Object` không annotate, mặc dù khai báo dùng `super`.
3. Với `unknown`, wildcard vẫn có upper bound mặc định `Object`; `getAnnotatedLowerBounds()` trả mảng rỗng.
4. Với `onlyBound`, wildcard không có annotation riêng, nên helper `mark(wildcard)` in `-`. Nhưng bound `Number` có annotation `onlyUpper`, chứng tỏ vị trí annotation phải được đọc độc lập.
5. Với `nested`, kiểu ngoài cùng là `Map`. Đối số thứ hai là `List<...>`, và đối số đầu của `List` mới là wildcard. Cần **hai lần** qua `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` để tìm đến wildcard và bound cần đọc.

Các field được khai báo để phục vụ phân tích kiểu; chúng không cần được khởi tạo thành `List` hay `Map` thực. Reflection ở đây đọc metadata của *declaration*, không đọc phần tử của một collection runtime.

### Nếu chỉ có `WildcardType` thì sao?

Một đoạn như:

```java
Field f = AnnotatedWildcardDemo.class
        .getDeclaredField("producers");

java.lang.reflect.ParameterizedType p =
        (java.lang.reflect.ParameterizedType) f.getGenericType();

java.lang.reflect.WildcardType w =
        (java.lang.reflect.WildcardType)
        p.getActualTypeArguments()[0];

System.out.println(w.getUpperBounds()[0].getTypeName());
```

sẽ cho `java.lang.Number` nhưng **không giữ annotation `@Mark("upper")` ngay trên `Type` này**. Để đọc annotation ở upper bound, đi theo `Field.getAnnotatedType()` như ví dụ chạy được ở trên.

Đoạn mã này chỉ minh họa Reflection generic thuần, giả định tên class và field đã được định nghĩa theo ví dụ trước; cần chạy trong method có xử lý `NoSuchFieldException`.

## 6. Quy tắc upper/lower bound ngầm định

### `?` không có giới hạn tường minh

```java
List<?> values;
```

Đối với wildcard này:

- `getAnnotatedUpperBounds()` trả upper bound `Object` **không mang annotation**, do Java không viết upper bound tường minh.
- `getAnnotatedLowerBounds()` trả mảng độ dài `0`.

Đừng hiểu mảng lower rỗng là “không tồn tại quan hệ kiểu”; nó chỉ nói **không có lower bound được khai báo tường minh**. Tương tự, sự xuất hiện của `Object` **không chứng minh mã nguồn đã viết `extends Object`**.

### `? extends Number`

```java
List<? extends @Mark("limit") Number> values;
```

- Upper bound là `Number`, với annotation `limit`.
- Lower bounds rỗng.
- Wildcard bản thân có thể không có annotation nào.

### `? super Integer`

```java
List<? super @Mark("floor") Integer> values;
```

- Lower bound là `Integer`, với annotation `floor`.
- Upper bound phản ánh mặc định là `Object` (không annotate).
- `getAnnotatedUpperBounds().length` không nên bị hiểu là `0` chỉ vì khai báo sử dụng `super`.

### Tránh khẳng định mọi array đều chỉ có một phần tử

Theo tài liệu Java, hiện tại wildcard source có tối đa một upper bound hoặc một lower bound tường minh, nhưng API vẫn sử dụng `AnnotatedType[]` và lưu ý caller nên sẵn sàng tiếp nhận nhiều bound ở tương lai. Duyệt với vòng `for` sẽ rõ ràng và bền hơn so với ép luôn `[0]`. Chỉ dùng `[0]` khi bạn vừa kiểm tra độ dài hoặc chủ động làm ví dụ cho cú pháp Java cụ thể.

## 7. Annotation trên wildcard không phải annotation trên field

Xét một trường hợp có hai `@Target`:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ColumnInfo {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeInfo {
    String value();
}

class Record {
    @ColumnInfo("scores")
    List<@TypeInfo("wild") ? extends
            @TypeInfo("bound") Number> scores;
}
```

Đây là **mảnh mã minh họa**; để biên dịch độc lập cần các import từ `java.lang.annotation` và `java.util.List`.

Các vị trí đọc khác nhau:

| Vị trí annotation | API chính |
| --- | --- |
| `@ColumnInfo` trên declaration của field `scores` | `Field.getAnnotation(ColumnInfo.class)` |
| `@TypeInfo("wild")` trên dấu `?` | `AnnotatedWildcardType.getAnnotation(TypeInfo.class)` |
| `@TypeInfo("bound")` trên `Number` | `getAnnotatedUpperBounds()[0].getAnnotation(TypeInfo.class)` |

Không dùng `field.getAnnotations()` rồi kỳ vọng nó trả annotation type-use ở wildcard hoặc bound. Những annotation đó thuộc **các vị trí con trong cây kiểu đã annotate**, không phải cùng node với declaration của field.

Tương tự, `AnnotatedParameterizedType.getAnnotations()` trên node `List<...>` không tự “thu gom” mọi annotation nằm sâu ở `?` hay bound. Muốn lấy toàn bộ, phải duyệt cây kiểu.

## 8. Bound là kiểu parameterized, array hoặc type variable

`AnnotatedType` của bound không nhất thiết chỉ đại diện cho `Class<?>` đơn giản.

### Bound chứa generic lồng nhau

```java
List<? extends @Mark("boundList")
        List<@Mark("element") String>> nestedBound;
```

Đây là khai báo field minh họa có ý nghĩa type-use. Trong Reflection, upper bound của wildcard là một `AnnotatedParameterizedType` đại diện `List<String>`:

1. `AnnotatedWildcardType.getAnnotatedUpperBounds()[0]` lấy node bound.
2. `bound.getAnnotation(Mark.class)` có thể cho `boundList`.
3. Ép bound thành `AnnotatedParameterizedType` rồi gọi `getAnnotatedActualTypeArguments()[0]` để lấy node `String`.
4. `getAnnotation(Mark.class)` trên node `String` cho `element`.

Chỉ hỏi `bound.getAnnotations()` mà bỏ qua các actual type arguments sẽ bỏ sót annotation sâu hơn.

### Bound là kiểu mảng

```java
List<? extends @Mark("element") Number[]> arrayBound;
```

Đọc kiểu array cần xem đúng vị trí annotation trên component và dimension; `AnnotatedArrayType.getAnnotatedGenericComponentType()` giúp đi vào kiểu phần tử hoặc dimension bên trong theo cấu trúc Reflection. Ví dụ annotation trên `Number` và annotation trên dấu ngoặc mảng `Number @Mark("dimension") []` là **hai vị trí type-use khác nhau**. Không nên kết luận vị trí chỉ bằng `toString()` của `Type`.

### Bound là type variable

```java
class Catalog<T extends Number> {
    List<? extends T> values;
}
```

Upper bound của wildcard này là kiểu biến `T`. Nếu cần xem *ràng buộc khai báo của T* (ví dụ `T extends Number`), tiếp tục khảo sát `AnnotatedTypeVariable.getAnnotatedBounds()` khi có thông tin liên quan.

Phân biệt:

- **Bound của wildcard:** `? extends T` — dùng `AnnotatedWildcardType.getAnnotatedUpperBounds()`.
- **Bound khai báo của type variable:** `T extends Number` — dùng `TypeVariable.getAnnotatedBounds()` hoặc `AnnotatedTypeVariable.getAnnotatedBounds()` từ góc nhìn annotated type variable.

Hai cấp bound không phải cùng một node. Với loại recursive bounds (ví dụ `T extends Comparable<T>`), khi viết thuật toán duyệt đệ quy tổng quát phải tránh vòng lặp vô hạn bằng cách theo dõi node/type đã thăm.

## 9. Ví dụ đọc wildcard tại method parameter và return type

Các API không bị giới hạn ở field. Ví dụ:

```java
class Service {
    java.util.List<@Mark("returnWild") ?
            extends @Mark("returnBound") Number> find() {
        return java.util.List.of();
    }

    void accept(java.util.List<@Mark("paramWild") ?
            super @Mark("paramBound") Integer> values) {
    }
}
```

Đây là đoạn minh họa bổ sung, dùng annotation `Mark` ở mục 5.

Với method `find`:

```java
Method method = Service.class.getDeclaredMethod("find");
AnnotatedParameterizedType returned =
        (AnnotatedParameterizedType) method.getAnnotatedReturnType();
AnnotatedWildcardType wildcard =
        (AnnotatedWildcardType)
        returned.getAnnotatedActualTypeArguments()[0];
AnnotatedType bound = wildcard.getAnnotatedUpperBounds()[0];
```

Với method `accept`:

```java
Method method = Service.class
        .getDeclaredMethod("accept", java.util.List.class);
AnnotatedParameterizedType parameterized =
        (AnnotatedParameterizedType)
        method.getParameters()[0].getAnnotatedType();
AnnotatedWildcardType wildcard =
        (AnnotatedWildcardType)
        parameterized.getAnnotatedActualTypeArguments()[0];
AnnotatedType bound = wildcard.getAnnotatedLowerBounds()[0];
```

Cần `import java.lang.reflect.*;` và xử lý `NoSuchMethodException`. Ví dụ đầy đủ runnable ở mục 5 đã trình bày các bước tương tự trên field.

Điều này minh họa rằng `getAnnotatedReturnType()` và `Parameter.getAnnotatedType()` là **cổng vào cấu trúc annotated type**, còn `getAnnotatedUpperBounds()/getAnnotatedLowerBounds()` chỉ xuất hiện sau khi tìm được node wildcard.

## 10. Giới hạn metadata: không phải annotation nào cũng đọc được bằng Reflection

Muốn type-use annotation có thể đọc ở runtime, khai báo thông thường cần:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark {
    String value();
}
```

Nếu không khai báo `@Retention`, mặc định là `RetentionPolicy.CLASS`: annotation có thể được compiler lưu trong class file nhưng **không được API core Reflection trả về như annotation runtime**. Với `RetentionPolicy.SOURCE`, annotation thường bị loại bỏ trước khi tạo class file. Nếu một thư viện sửa hoặc làm mờ bytecode, thông tin có thể bị thay đổi.

Không được hiểu việc `getAnnotation(Mark.class) == null` là chắc chắn trong source không từng có annotation: có thể do retention, metadata bị bỏ, vị trí tra cứu sai, loại `@Target` không phù hợp, hoặc annotation class không khả dụng.

### Type erasure và việc đọc generic annotations

`List<? extends Number>` và `List<? super Integer>` khi chạy đều là đối tượng thuộc kiểu thô `List` ở JVM. Reflection đọc cấu trúc generic từ metadata khai báo như `Signature` và các type annotation attributes liên quan. Các annotation này mô tả **kiểu được khai báo**, không phải annotation runtime của từng phần tử thực tế bên trong một danh sách.

Ví dụ, `List<@Mark ? extends Number>` không có nghĩa mọi `Number` trong list là một đối tượng được gắn `@Mark`; annotation ở đây là thông tin trong **type use của đối số generic**.

Một `java.lang.reflect.Type` thuần có thể nêu đúng generic bound nhưng không thể giữ vị trí annotation; dùng `AnnotatedType` khi câu hỏi là annotation trên kiểu và bound.

### Khi class file metadata không đủ

Các tình huống có thể gặp:

- Generic `Signature` bị loại bỏ hoặc hỏng, khiến việc phân tích type argument không còn đáng tin.
- Type-use annotation có `CLASS` hoặc `SOURCE` retention, nên API runtime không thấy.
- Obfuscation hoặc bytecode transformation làm thay đổi metadata.
- Kiểu tham chiếu trong generic signature không còn tồn tại, có thể phát sinh `TypeNotPresentException` hay ngoại lệ phản ánh phù hợp.
- Parser cố ép kiểu node sai, ví dụ ép một `AnnotatedArrayType` thành `AnnotatedWildcardType`.

Trường hợp metadata không tồn tại thì hãy coi dữ liệu là **không xác định** thay vì tự suy ra annotation đã/không đã viết trong source. Cần thông tin trước runtime thì dùng công cụ biên dịch/source AST hoặc class-file reader với mục đích phù hợp.

## 11. Kiểm tra class file bằng `javap`

Sau khi biên dịch chương trình `AnnotatedWildcardDemo.java` ở mục 5:

```bash
javap -v -p AnnotatedWildcardDemo
```

Tìm các phần liên quan:

- `Signature` lưu biểu diễn generic, chẳng hạn wildcard upper/lower type arguments.
- `RuntimeVisibleTypeAnnotations` lưu type-use annotations có runtime retention và thông tin **type path/target** để chỉ ra vị trí annotation: wildcard, bound, node generic sâu hơn.
- Một số annotation không có runtime retention có thể được lưu trong `RuntimeInvisibleTypeAnnotations` tùy retention; core Reflection không hiển thị chúng theo cách giống runtime-visible annotations.

`javap` hữu ích để đối chiếu vị trí với dữ liệu class file, nhưng không thay thế API `AnnotatedWildcardType` khi bạn viết logic Reflection ở runtime.

Nếu `getAnnotation(...)` không thấy annotation, hãy xác định thật rõ **đang hỏi node nào** trước khi kết luận annotation thiếu: field declaration, `List`, wildcard `?`, upper bound hay lower bound.

## 12. Các lỗi thường gặp

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Cast `field.getAnnotatedType()` trực tiếp thành `AnnotatedWildcardType` | Field là `List<...>`, không phải wildcard | Đi qua `AnnotatedParameterizedType` và lấy type argument |
| Dùng `Field.getGenericType()` để đọc annotation bound | `Type` thuần không mang annotation type-use theo node | Dùng `getAnnotatedType()` |
| Gọi `wildcard.getAnnotation(Mark.class)` để tìm annotation trên `Number` | Annotation ở bound, không ở dấu `?` | Hỏi `getAnnotatedUpperBounds()[0].getAnnotation(...)` |
| Với `? super Integer`, giả định upper bounds rỗng | Upper bound ngầm định là `Object` | Kiểm tra upper và lower độc lập |
| Với `?`, giả định không có bound nào | Upper bound ngầm định vẫn là `Object` | Đọc `getAnnotatedUpperBounds()` |
| Mọi wildcard đều là đối số số 0 | Map/nested generics có thể có nhiều type arguments | Duyệt đúng cây annotated types |
| Chỉ duyệt bound một cấp | Bound có thể là `List<@Mark String>` hay array/type variable | Duyệt `AnnotatedParameterizedType`, `AnnotatedArrayType`, `AnnotatedTypeVariable` khi cần |
| Mong `getAnnotations()` của `Field` chứa annotation trên `?` | Declaration annotation khác type-use annotation | Dùng annotated type APIs |
| Thiếu `@Retention(RUNTIME)` nhưng đòi Reflection thấy marker | Retention mặc định `CLASS` | Dùng `RUNTIME` khi cần đọc runtime |
| Coi annotation generic là annotation của object runtime | Thông tin thuộc type use khai báo | Tách metadata khai báo khỏi giá trị runtime |
| Phụ thuộc `getAnnotatedUpperBounds()[0]` cho mọi hoàn cảnh | API trả mảng; kiểu thực tế có thể khác | Kiểm tra độ dài và duyệt vòng `for` |
| Coi mọi `AnnotatedType` là `AnnotatedWildcardType` | Hệ phân loại Reflection có nhiều nhánh | Dùng `instanceof` / kiểm tra class trước khi cast |

## 13. Bài tập tự kiểm tra

**Câu 1.** Trong `List<@Mark("A") ? extends @Mark("B") Number>`, nơi nào đọc được `A` và `B`?

**Đáp án:** `A` ở `AnnotatedWildcardType.getAnnotation(Mark.class)`; `B` ở `getAnnotatedUpperBounds()[0].getAnnotation(Mark.class)`.

**Câu 2.** `List<? super @Mark("LOW") Integer>` có upper bound nào ở runtime?

**Đáp án:** Có upper bound `Object` không annotate, và lower bound `Integer` mang `LOW`.

**Câu 3.** Tại sao `Field.getAnnotation(Mark.class)` không phải API phù hợp để đọc `@Mark` đặt ngay trên dấu `?`?

**Đáp án:** Vì đó là annotation **type-use** trên wildcard node trong generic signature; nó không trực tiếp annotate declaration của `Field`.

**Câu 4.** `getAnnotatedLowerBounds().length == 0` cho biết điều gì?

**Đáp án:** Không có lower bound **tường minh** trong cách phản ánh wildcard đó. Đừng suy ra wildcard không có upper bound.

**Câu 5.** Nếu field là `Map<String, List<? extends Number>>`, có thể cast ngay kết quả `field.getAnnotatedType()` thành `AnnotatedWildcardType` không?

**Đáp án:** Không. Đầu tiên là `AnnotatedParameterizedType` của `Map`; type argument thứ hai là `List` cũng là `AnnotatedParameterizedType`; wildcard nằm ở type argument của `List`.

**Câu 6.** Với `@Retention(CLASS)`, core Reflection có bảo đảm trả annotation ở bound không?

**Đáp án:** Không. Class file có thể giữ metadata nhưng annotation không có runtime retention để Reflection hiển thị như runtime annotation.

**Câu 7.** Phải chọn `getAnnotatedUpperBounds()` hay `getAnnotatedLowerBounds()` cho `? extends @Mark Number` và `? super @Mark Integer`?

**Đáp án:** `extends` → upper; `super` → lower.

## 14. Ghi nhớ

**`AnnotatedWildcardType` mô tả wildcard `?` có thể được annotate, và mỗi bound là một `AnnotatedType` có thể được annotate độc lập.** Để đọc đầy đủ metadata, đi theo đường từ field/method/parameter tới `AnnotatedParameterizedType`, lấy node `AnnotatedWildcardType`, hỏi annotation trên wildcard, rồi duyệt upper/lower bounds. Wildcard không có bound tường minh vẫn có upper bound `Object`; `? super T` cũng có upper bound `Object` và lower bound `T`.

Khi gặp generic lồng nhau, hãy duyệt cây `AnnotatedType` chứ không cố bóc mọi annotation bằng `Type` thuần hay ép kiểu mù quáng. Metadata chỉ đáng tin ở mức runtime nếu retention và class file thực sự giữ các thông tin cần thiết.

**Tài liệu tham khảo chuẩn:**

- [Java SE 17 — `AnnotatedWildcardType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedWildcardType.html)
- [Java SE 17 — `AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html)
- [Java SE 17 — `AnnotatedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html)
- [Java Language Specification `4.5.1 — Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-006">AnnotatedTypeVariable và các bound có annotation</a>

<details>
<summary>Xem chi tiết</summary>

# AnnotatedTypeVariable và các bound có annotation trong Java Reflection

## 1. Bài toán: annotation nằm ở vị trí nào của một type variable?

Java hỗ trợ annotation trên **kiểu được sử dụng** (*type-use annotation*) và trên **khai báo tham số kiểu** (*type-parameter declaration*). Khi dùng Reflection để đọc generic, cần phân biệt ba vị trí:

1. Annotation trên **khai báo biến kiểu**: `<@ParameterMark T ...>`.
2. Annotation trên **bound của biến kiểu**: `T extends @BoundMark Number`.
3. Annotation trên **lần sử dụng biến kiểu**: `@UseMark T value` hoặc `Comparable<@UseMark T>`.

Chúng không tự động là cùng một annotation, cũng không nhất thiết nằm trên cùng đối tượng Reflection.

Ví dụ:

```java
class Sample<@ParameterMark T extends @BoundMark Number
        & Comparable<@UseMark T>> {
    @UseMark T value;
}
```

Ở đây:

- `@ParameterMark` gắn với chính **khai báo** `T`.
- `@BoundMark` gắn với **kiểu Number được dùng làm upper bound** của `T`.
- `@UseMark` bên trong `Comparable<@UseMark T>` gắn với **lần dùng `T` làm type argument**.
- `@UseMark` trong `@UseMark T value` gắn với **kiểu của field**, không tự biến thành annotation khai báo của `T`.

Để đọc đúng các vị trí ấy, Java cung cấp `TypeVariable`, `AnnotatedType`, `AnnotatedTypeVariable` và `AnnotatedParameterizedType`.

**Trọng tâm:** `AnnotatedTypeVariable` đại diện cho *một lần sử dụng kiểu là type variable* (chẳng hạn `@UseMark T`), đồng thời cho phép đọc các **annotated upper bounds** của biến kiểu đó bằng `getAnnotatedBounds()`. Đây không phải một `Class<?>` của `T`: generic type variable và class runtime là hai khái niệm khác nhau.

## 2. Các API liên quan và quan hệ giữa chúng

| API | Kiểu trả về | Công dụng |
| --- | --- | --- |
| `Class.getTypeParameters()` | `TypeVariable<?>[]` | Các biến kiểu được khai báo bởi class/interface |
| `Method.getTypeParameters()` | `TypeVariable<Method>[]` | Các biến kiểu được khai báo bởi generic method |
| `Constructor.getTypeParameters()` | `TypeVariable<Constructor<?>>[]` | Các biến kiểu được khai báo bởi generic constructor |
| `TypeVariable.getName()` | `String` | Tên như `T`, `U` |
| `TypeVariable.getBounds()` | `Type[]` | Các upper bounds, **không kèm annotation use-site** |
| `TypeVariable.getAnnotatedBounds()` | `AnnotatedType[]` | Các upper bounds **có thể mang type-use annotation** |
| `TypeVariable.getAnnotation(...)` | annotation hoặc `null` | Annotation trên **khai báo biến kiểu** |
| `Field.getGenericType()` | `Type` | Kiểu generic của field (có thể là `TypeVariable`), chưa diễn tả vị trí type-use annotation |
| `Field.getAnnotatedType()` | `AnnotatedType` | Kiểu của field cùng annotation tại vị trí sử dụng |
| `Method.getAnnotatedReturnType()` | `AnnotatedType` | Kiểu trả về, kể cả annotation tại vị trí sử dụng |
| `Executable.getAnnotatedParameterTypes()` | `AnnotatedType[]` | Các kiểu tham số cùng annotation |
| `AnnotatedTypeVariable.getAnnotatedBounds()` | `AnnotatedType[]` | Các bound có annotation gắn với type variable được tham chiếu |
| `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` | `AnnotatedType[]` | Các type arguments có annotation nằm **bên trong** kiểu tham số hóa |
| `AnnotatedType.getType()` | `Type` | Kiểu nền mà một `AnnotatedType` đại diện |
| `AnnotatedType.getAnnotation(...)` | annotation hoặc `null` | Annotation tại **chính vị trí kiểu** mà đối tượng này đại diện |

Lưu ý: `AnnotatedTypeVariable` là **interface con của `AnnotatedType`**, không phải một đối tượng có thể tạo tùy ý bằng `new AnnotatedTypeVariable(...)`. Ta nhận nó qua các API Reflection, rồi kiểm tra bằng `instanceof`.

### Phân biệt hai lối vào thường gặp

**Lối vào từ khai báo:**

```java
TypeVariable<?> t = Sample.class.getTypeParameters()[0];
AnnotatedType[] annotatedBounds = t.getAnnotatedBounds();
```

**Lối vào từ vị trí dùng:**

```java
Field field = Sample.class.getDeclaredField("value");
AnnotatedType fieldType = field.getAnnotatedType();
if (fieldType instanceof AnnotatedTypeVariable variableUse) {
    AnnotatedType[] bounds = variableUse.getAnnotatedBounds();
}
```

Cả hai đều có thể giúp quan sát những bound thuộc khai báo `T`, nhưng **vị trí dùng** còn mang annotation riêng trên `@UseMark T`. Đừng đọc mỗi `getAnnotatedBounds()` rồi kết luận mọi annotation của field đã được lấy đủ.

Các mảnh code trong mục này minh họa API, cần được đặt trong phương thức có xử lý `NoSuchFieldException` và cùng khai báo class/annotation thích hợp. Chương trình đầy đủ ở mục 4.

## 3. Điều kiện để annotation có mặt khi Reflection chạy

Cần khai báo annotation phù hợp:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_PARAMETER)
@interface ParameterMark {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface BoundMark {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface UseMark {}
```

Đây là **đoạn minh họa**, yêu cầu import `java.lang.annotation.*`. Trong chương trình hoàn chỉnh bên dưới, các khai báo này nằm trong cùng file.

- `ElementType.TYPE_PARAMETER` nhắm vào **khai báo** biến kiểu như `<@ParameterMark T>`.
- `ElementType.TYPE_USE` nhắm vào **vị trí sử dụng kiểu**, gồm bound `extends @BoundMark Number`, type argument `Comparable<@UseMark T>` hoặc field `@UseMark T value`.
- `RetentionPolicy.RUNTIME` cho phép Reflection nhìn thấy annotation ở runtime. Nếu annotation chỉ có `CLASS` retention hoặc `SOURCE` retention, các API runtime không cung cấp nó như trong ví dụ.
- `@Inherited` không tự làm type-use annotation lan truyền qua giới hạn generic; không nên dùng nó để suy luận về annotated bounds.
- Nếu dùng repeatable annotation, cân nhắc `getAnnotationsByType(...)` thay vì chỉ `getAnnotation(...)`.

**Nhớ phân biệt:** một annotation trên **khai báo T** không tự được copy vào từng lần dùng T; annotation trên **bound của T** cũng không có nghĩa là mọi field kiểu T có annotation ấy tại vị trí của field.

## 4. Ví dụ Java 17+ hoàn chỉnh và kiểm chứng được

Tạo file **`AnnotatedTypeVariableDemo.java`**:

```java
import java.lang.annotation.*;
import java.lang.reflect.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_PARAMETER)
@interface ParameterMark {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface BoundMark {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface UseMark {}

public class AnnotatedTypeVariableDemo {
    static class Sample<@ParameterMark T extends @BoundMark Number
            & Comparable<@UseMark T>> {
        @UseMark T value;
    }

    static class Unbounded<V> {}

    static <@ParameterMark U extends @BoundMark CharSequence>
            @UseMark U echo(@UseMark U value) {
        return value;
    }

    public static void main(String[] args) throws Exception {
        TypeVariable<?> variable = Sample.class.getTypeParameters()[0];
        System.out.println("Variable: " + variable.getName());
        System.out.println("Type-parameter annotation: "
                + (variable.getAnnotation(ParameterMark.class) != null));
        System.out.println("Bounds count: " + variable.getBounds().length);

        AnnotatedType[] bounds = variable.getAnnotatedBounds();
        for (int i = 0; i < bounds.length; i++) {
            System.out.println("Bound " + i + ": "
                    + bounds[i].getType().getTypeName());
            System.out.println("  @BoundMark: "
                    + (bounds[i].getAnnotation(BoundMark.class) != null));
        }

        AnnotatedParameterizedType comparable =
                (AnnotatedParameterizedType) bounds[1];
        AnnotatedType argument =
                comparable.getAnnotatedActualTypeArguments()[0];
        System.out.println("Comparable argument @UseMark: "
                + (argument.getAnnotation(UseMark.class) != null));
        System.out.println("Comparable argument is variable: "
                + (argument instanceof AnnotatedTypeVariable));

        Field field = Sample.class.getDeclaredField("value");
        AnnotatedType fieldType = field.getAnnotatedType();
        System.out.println("Field erased type: "
                + field.getType().getSimpleName());
        System.out.println("Field generic variable: "
                + (field.getGenericType() instanceof TypeVariable));
        System.out.println("Field is variable: "
                + (fieldType instanceof AnnotatedTypeVariable));
        System.out.println("Field use @UseMark: "
                + (fieldType.getAnnotation(UseMark.class) != null));

        AnnotatedTypeVariable reference =
                (AnnotatedTypeVariable) fieldType;
        System.out.println("Field type variable: "
                + reference.getType().getTypeName());
        System.out.println("Field variable bounds: "
                + reference.getAnnotatedBounds().length);

        Method method = AnnotatedTypeVariableDemo.class.getDeclaredMethod(
                "echo", CharSequence.class);
        TypeVariable<?> methodVar = method.getTypeParameters()[0];
        System.out.println("Method variable: " + methodVar.getName());
        System.out.println("Method bound @BoundMark: "
                + (methodVar.getAnnotatedBounds()[0]
                        .getAnnotation(BoundMark.class) != null));
        System.out.println("Method parameter is variable: "
                + (method.getAnnotatedParameterTypes()[0]
                        instanceof AnnotatedTypeVariable));
        System.out.println("Method return @UseMark: "
                + (method.getAnnotatedReturnType()
                        .getAnnotation(UseMark.class) != null));

        AnnotatedType defaultBound =
                Unbounded.class.getTypeParameters()[0]
                        .getAnnotatedBounds()[0];
        System.out.println("Unbounded default: "
                + defaultBound.getType().getTypeName());
        System.out.println("Unbounded annotations: "
                + defaultBound.getAnnotations().length);
    }
}
```

Biên dịch/chạy từ thư mục chứa file bằng JDK 17 hoặc mới hơn:

```bash
javac --release 17 AnnotatedTypeVariableDemo.java
java AnnotatedTypeVariableDemo
```

**Stdout thực tế (đã biên dịch và chạy bằng JDK 21 với `--release 17`):**

```text
Variable: T
Type-parameter annotation: true
Bounds count: 2
Bound 0: java.lang.Number
  @BoundMark: true
Bound 1: java.lang.Comparable<T>
  @BoundMark: false
Comparable argument @UseMark: true
Comparable argument is variable: true
Field erased type: Number
Field generic variable: true
Field is variable: true
Field use @UseMark: true
Field type variable: T
Field variable bounds: 2
Method variable: U
Method bound @BoundMark: true
Method parameter is variable: true
Method return @UseMark: true
Unbounded default: java.lang.Object
Unbounded annotations: 0
```

### Giải thích bốn phát hiện quan trọng

**(1) Khai báo `T` và bound `Number` là hai nơi khác nhau.**

`variable.getAnnotation(ParameterMark.class)` trả annotation trên **khai báo type parameter** `T`. Trong khi đó, `bounds[0].getAnnotation(BoundMark.class)` trả annotation trên **kiểu Number đang làm bound**. Nếu dùng `variable.getAnnotation(BoundMark.class)` để hỏi “Number có annotation không?”, bạn đã hỏi sai đối tượng Reflection.

**(2) Annotated bound có thể là generic/parameterized type.**

Bound thứ hai là `Comparable<T>`. Cờ `@UseMark` không nằm trên chính đối tượng `bounds[1]` (vỏ `Comparable`); nó nằm **bên trong type argument `T`**. Vì vậy cần cast đúng kiểu:

```java
AnnotatedParameterizedType apt =
        (AnnotatedParameterizedType) bounds[1];
AnnotatedType inner = apt.getAnnotatedActualTypeArguments()[0];
boolean marked = inner.isAnnotationPresent(UseMark.class);
```

Với trường hợp này, `inner` còn là `AnnotatedTypeVariable` vì type argument được sử dụng là `T`. Nếu chỉ gọi `bounds[1].getAnnotations()` rồi dừng, bạn sẽ bỏ sót annotation lồng sâu.

**(3) Một field kiểu `T` vừa có generic type vừa có annotated type.**

`field.getType()` trả `Number.class` do **type erasure**: trong `T extends Number & Comparable<T>`, erasure của `T` là first bound `Number`. `field.getGenericType()` vẫn biểu diễn `T` dưới dạng `TypeVariable`. `field.getAnnotatedType()` trả một `AnnotatedTypeVariable` có annotation trên lần dùng `@UseMark T`.

Ba API nói về **ba tầng thông tin khác nhau**, không mâu thuẫn:

| Lệnh trên field `@UseMark T value` | Thông tin nhận được |
| --- | --- |
| `field.getType()` | Runtime erased type: `Number.class` |
| `field.getGenericType()` | Generic type: `TypeVariable` tên `T` |
| `field.getAnnotatedType()` | `AnnotatedTypeVariable` biểu diễn `@UseMark T` |
| `((AnnotatedTypeVariable) field.getAnnotatedType()).getAnnotatedBounds()` | Hai bound: `@BoundMark Number` và `Comparable<@UseMark T>` |

**(4) Generic method cũng có type variable riêng.**

`echo` khai báo `<@ParameterMark U extends @BoundMark CharSequence>`. Do erasure của `U` là `CharSequence`, muốn tìm method bằng `getDeclaredMethod` phải truyền `CharSequence.class`, không phải `Object.class` hoặc một lớp bất kỳ của giá trị được gọi.

`method.getTypeParameters()[0]` đại diện cho **khai báo U**; `getAnnotatedParameterTypes()[0]` và `getAnnotatedReturnType()` đại diện cho **vị trí dùng U ở parameter/return**. Chương trình quan sát cả ba vị trí này.

## 5. Cách dùng `AnnotatedTypeVariable.getAnnotatedBounds()`

Giả sử từ field, parameter hoặc return type có `AnnotatedType annotated`:

```java
if (annotated instanceof AnnotatedTypeVariable variableUse) {
    for (AnnotatedType bound : variableUse.getAnnotatedBounds()) {
        System.out.println(bound.getType().getTypeName());
        for (Annotation mark : bound.getAnnotations()) {
            System.out.println(mark.annotationType().getName());
        }
    }
}
```

Đây là **đoạn minh họa** cần import `java.lang.annotation.Annotation` và `java.lang.reflect.*`; biến `annotated` do caller cung cấp.

Quy trình đúng:

1. Kiểm tra bằng `instanceof AnnotatedTypeVariable` trước khi cast. Field có thể là `AnnotatedParameterizedType`, `AnnotatedArrayType`, `AnnotatedWildcardType` hoặc kiểu không generic chứ không phải type variable.
2. Lấy `getType()`; với `AnnotatedTypeVariable`, đối tượng `Type` nền tương ứng là một `TypeVariable<?>`.
3. Gọi `getAnnotatedBounds()` để lấy **từng upper bound**, theo đúng thứ tự khai báo.
4. Trên mỗi bound, đọc annotation gắn **trực tiếp tại bound** bằng `getAnnotations()`, `getAnnotation(...)` hoặc `getAnnotationsByType(...)`.
5. Nếu bound có generic type arguments, cast có kiểm tra sang `AnnotatedParameterizedType` rồi duyệt `getAnnotatedActualTypeArguments()` để tìm annotation lồng bên trong.
6. Nếu cần biết annotation tại **lần dùng type variable**, đọc `variableUse.getAnnotations()`; chúng không được thay thế bằng annotation trên bound.

### Bound nhiều thành phần

Java hỗ trợ upper bound dạng intersection:

```java
class Sample<T extends Number & Comparable<T>> {}
```

`T` có hai upper bounds theo thứ tự `Number` và `Comparable<T>`. `TypeVariable.getBounds()` và `getAnnotatedBounds()` đều biểu diễn **hai vị trí**; khác nhau ở chỗ API annotated bảo tồn metadata annotation trên cách dùng từng kiểu bound.

Không được giả định chỉ có một bound. Đối với intersection, class bound (nếu có) phải đứng đầu, còn các interface bounds xuất hiện tiếp theo theo quy tắc Java. Các annotation tương ứng được gắn với đúng thành phần mà chúng đứng trước.

Nếu **không khai báo bound**:

```java
class Plain<T> {}
```

bound mặc định của `T` là `Object` và không có annotation. `getAnnotatedBounds()` trả mảng chứa một `AnnotatedType` cho `Object` không được annotate; nó **không** trả mảng rỗng. Điều này được chứng minh bởi `Unbounded<V>` trong ví dụ chạy ở mục 4.

## 6. Đọc annotation khai báo khác đọc annotation trên kiểu

| Cú pháp trong source | Nơi lấy annotation | Thực thể metadata |
| --- | --- | --- |
| `<@ParameterMark T>` | `typeVariable.getAnnotation(ParameterMark.class)` | Khai báo biến kiểu |
| `T extends @BoundMark Number` | `typeVariable.getAnnotatedBounds()[0].getAnnotation(BoundMark.class)` | Upper bound |
| `Comparable<@UseMark T>` | Cast bound `AnnotatedParameterizedType` rồi lấy annotated argument | Type argument trong bound |
| `@UseMark T value` | `field.getAnnotatedType().getAnnotation(UseMark.class)` | Lần dùng T trong field |
| `@UseMark U echo(...)` | `method.getAnnotatedReturnType().getAnnotation(UseMark.class)` | Lần dùng U ở return type |
| `echo(@UseMark U value)` | `method.getAnnotatedParameterTypes()[0].getAnnotation(UseMark.class)` | Lần dùng U ở parameter |

**Tránh nhầm:** `TypeVariable` là `AnnotatedElement` và có thể trả annotation từ khai báo type parameter; nhưng `AnnotatedTypeVariable` triển khai `AnnotatedType` và biểu diễn annotation gắn vào **type use cụ thể**. Cùng tên `getAnnotation(...)` nhưng câu hỏi đặt cho hai đối tượng là khác nhau.

Một số annotation có `@Target` cho nhiều nơi, ví dụ vừa `TYPE_PARAMETER` vừa `TYPE_USE`. Khi ấy việc kiểm tra vị trí vẫn quan trọng; tên annotation giống nhau không có nghĩa runtime luôn lưu chúng tại cùng một loại target.

## 7. Generic erasure: Annotation trên bound không làm thay đổi runtime class

Có hai vấn đề độc lập:

- **Generic signature** mô tả `T extends Number & Comparable<T>`. Reflection truy cập qua `TypeVariable` và các `Type` con.
- **Runtime erased type** của field `T value` là `Number` khi `Number` là bound đầu. Kiểu bị xóa không lưu “đối tượng này là một subtype cụ thể của T” ở runtime.
- **TYPE_USE annotation** mô tả chú giải về những vị trí kiểu trong chữ ký, không tự thực thi ràng buộc kiểm tra dữ liệu khi runtime chạy.

Ví dụ nếu `@BoundMark` mang nghĩa nghiệp vụ “đã được xác thực”, Reflection chỉ đọc được marker đó. Việc kiểm tra dữ liệu thật sự vẫn cần validator/framework riêng; JVM không thay thế code kiểm tra dựa chỉ vào type-use marker tùy chỉnh.

Một generic field chứa type variable không thể được dùng để suy ra tự động `T` là `Integer` hay `Long` trên một object cụ thể do type erasure. Nếu cần actual type arguments của một superclass/interface đã được parameterize, phải phân tích `ParameterizedType` từ vị trí khai báo sử dụng phù hợp và cân nhắc kế thừa; đó là bài toán khác với đọc annotation trên upper bound.

## 8. Khi bound là parameterized, array hoặc wildcard type

`AnnotatedType` có các subtype chuyên biệt và mỗi subtype mở thêm tầng cấu trúc:

| Hình dạng kiểu | Subtype nên kiểm tra | Cách đi sâu |
| --- | --- | --- |
| `T` | `AnnotatedTypeVariable` | `getAnnotatedBounds()` |
| `List<@A String>` | `AnnotatedParameterizedType` | `getAnnotatedActualTypeArguments()` |
| `@A String[]` hoặc `String @A []` | `AnnotatedArrayType` | `getAnnotatedGenericComponentType()` |
| `? extends @A Number` | `AnnotatedWildcardType` | `getAnnotatedUpperBounds()` |
| `? super @A Integer` | `AnnotatedWildcardType` | `getAnnotatedLowerBounds()` |

Mỗi cấp có thể mang annotation riêng. Không nên chỉ gọi `getAnnotations()` ở nút ngoài cùng rồi kết luận đã lấy đủ mọi annotation lồng bên trong.

**Đặc biệt với recursive bounds:**

```java
interface Node<T extends Comparable<T>> {}
```

Một type variable có thể xuất hiện lại bên trong bound của chính nó (*F-bounded polymorphism*). Nếu viết trình duyệt metadata đệ quy từ `T` → `Comparable<T>` → `T` → bound..., sẽ lặp vô hạn nếu không đánh dấu đã thăm.

Khi viết trình duyệt tổng quát, cần lưu dấu các khai báo `TypeVariable<?>` đã đi qua theo ngữ cảnh (chẳng hạn tập các variable đã thăm); phân biệt **annotation trên lần dùng hiện tại** và **việc mở rộng bound khai báo**. Có thể vẫn ghi lại annotation của nút dùng trước khi quyết định không duyệt lại các bound của nó.

`AnnotatedTypeVariable.getAnnotatedOwnerType()` trả `null` theo hợp đồng API; owner type dành cho những dạng nested member type khác, không phải cơ chế tìm khai báo của `T`. Nếu cần biết biến kiểu `T` được khai báo ở class/method/constructor nào, dùng `((TypeVariable<?>) annotated.getType()).getGenericDeclaration()`.

## 9. Áp dụng trong công cụ phân tích generic/annotation

Một schema generator, validation framework hoặc API documentation tool có thể dùng annotated bounds để hiểu ràng buộc về dạng kiểu và metadata ở đúng vị trí. Quy trình thực tế:

1. Xác định nguồn: type variable khai báo của **class**, **method** hoặc **constructor**, hay type variable được dùng tại **field/parameter/return**.
2. Lấy metadata khai báo qua `getTypeParameters()` khi cần type parameter annotations.
3. Lấy annotated type của vị trí dùng qua `getAnnotatedType()`, `getAnnotatedReturnType()` hoặc `getAnnotatedParameterTypes()`.
4. Phân nhánh theo subtype `AnnotatedType` bằng `instanceof`; với variable, đọc `getAnnotatedBounds()`; với parameterized bound, duyệt annotated type arguments.
5. Phân biệt annotation trên khai báo type variable, trên bound và trên type use. Không chuyển annotation từ vị trí này sang vị trí khác khi chưa có quy tắc nghiệp vụ rõ ràng.
6. Đối với classfile từ compiler khác hoặc thư viện không có RUNTIME retention, chấp nhận metadata annotation vắng mặt mà không tự suy diễn rằng annotation chưa từng tồn tại ở source.
7. Chống vòng lặp khi F-bounded/recursive generics; xử lý `TypeNotPresentException` và `MalformedParameterizedTypeException` khi metadata generic hỏng hoặc tham chiếu kiểu không khả dụng.
8. Viết test trên classfile thực tế để xác nhận cách khai báo annotation, thứ tự bound và vị trí nested annotation.

### Một helper nhỏ để báo cáo annotated bounds

Đặt method sau trong class tiện ích có import `java.lang.reflect.*` và `java.lang.annotation.*`:

```java
static void printDirectBoundAnnotations(TypeVariable<?> variable) {
    for (AnnotatedType bound : variable.getAnnotatedBounds()) {
        System.out.println("Bound: " + bound.getType().getTypeName());
        for (Annotation annotation : bound.getDeclaredAnnotations()) {
            System.out.println("  @" + annotation.annotationType().getSimpleName());
        }
    }
}
```

Đây là **mảnh code minh họa**, chỉ đọc annotation gắn trực tiếp vào từng **bound ở lớp ngoài**, không duyệt sâu type arguments. Với `Comparable<@UseMark T>`, nếu muốn nhận `@UseMark`, phải bổ sung bước duyệt `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` như mục 4.

## 10. Những nhầm lẫn phổ biến

| Nhầm lẫn | Kết luận đúng |
| --- | --- |
| “`TypeVariable.getBounds()` trả cả annotation.” | `getBounds()` trả `Type[]`; dùng `getAnnotatedBounds()` để lấy annotation |
| “`AnnotatedTypeVariable` là class runtime tương đương T.” | Đây là metadata của type-variable use; `getType()` là `TypeVariable`, không phải `Class<?>` cụ thể |
| “Annotation của `<@P T>` là annotation của mọi bound T.” | Annotation trên khai báo và annotation trên bound nằm ở hai vị trí khác nhau |
| “`T extends @A Number` làm mọi `@T` field có `@A`.” | Annotation trên bound không tự chuyển sang vị trí field |
| “Đọc `bounds[1].getAnnotations()` là đủ lấy cả `Comparable<@A T>`.” | Annotation type argument lồng trong `AnnotatedParameterizedType` |
| “Không khai báo `extends` thì annotated bounds rỗng.” | Có bound `Object` mặc định, không annotate |
| “Một biến kiểu chỉ có một bound.” | Có thể có nhiều upper bounds nối bằng `&` |
| “`field.getType()` trả TypeVariable.” | `getType()` trả erased `Class<?>`; dùng `getGenericType()` |
| “Annotation TYPE_USE mặc định đọc được ở runtime.” | Muốn Reflection đọc được thì cần `RetentionPolicy.RUNTIME` |
| “F-bound có thể mở rộng đệ quy không giới hạn.” | Dễ tạo vòng lặp; cần đánh dấu type variable đã duyệt |
| “Metadata type-use tự validate dữ liệu.” | Marker được lưu để công cụ khác diễn giải, không tự chạy validator |

## 11. Câu hỏi tự kiểm tra

1. Với `class Box<@ParameterMark T extends @BoundMark Number> {}`, muốn đọc `@ParameterMark` và `@BoundMark` dùng hai API nào?
2. Vì sao `Field.getGenericType()` và `Field.getAnnotatedType()` cung cấp thông tin khác nhau?
3. Với `Comparable<@UseMark T>` là upper bound, `@UseMark` nằm trên đối tượng `AnnotatedType` của `Comparable` hay trên type argument bên trong?
4. Nếu một type variable không khai báo `extends`, `getAnnotatedBounds()` có bao nhiêu phần tử và đó là kiểu gì?
5. `AnnotatedTypeVariable.getAnnotatedBounds()` có ý nghĩa gì khi `AnnotatedTypeVariable` được lấy từ field `@UseMark T value`?
6. Vì sao cần `@Target(TYPE_USE)` và `@Retention(RUNTIME)` đối với annotation muốn đọc trên bound qua Reflection?
7. Một field kiểu `T extends Number & Comparable<T>` có `getType()` là `T` hay `Number.class`? Làm thế nào đọc lại `T` ở mức generic?
8. Điều gì có thể xảy ra nếu trình duyệt bound tự mở rộng `T extends Comparable<T>` mà không phát hiện chu trình?

**Đáp án:** (1) `TypeVariable.getAnnotation(...)` và `TypeVariable.getAnnotatedBounds()[0].getAnnotation(...)`; (2) một bên là loại generic nền, bên kia có type-use annotations; (3) type argument bên trong; (4) một `Object` không annotate; (5) trả annotated upper bounds của T, còn annotation trực tiếp tại field đọc trên chính `AnnotatedTypeVariable`; (6) đúng target và runtime visibility; (7) `Number.class`, dùng `getGenericType()`/`getAnnotatedType()`; (8) lặp vô hạn/stack overflow.

## 12. Kết luận

**AnnotatedTypeVariable giúp phân tích một lần sử dụng type variable có annotation và tìm các upper bounds có thể được annotate.** Để đọc chính xác, hãy tách ba tầng: *type-parameter declaration*, *bound type use* và *variable type use*. Sử dụng `TypeVariable.getAnnotatedBounds()` hoặc `AnnotatedTypeVariable.getAnnotatedBounds()` cho bound, và dùng các subtype `AnnotatedParameterizedType`, `AnnotatedArrayType`, `AnnotatedWildcardType` để tìm annotation bên trong cấu trúc kiểu phức tạp. Chỉ dữ liệu generic/annotation còn hiện diện ở classfile runtime mới đọc được; Reflection không tự loại bỏ type erasure hoặc thay thế kiểm tra nghiệp vụ.

Tài liệu chính thức:

- [Java SE 21 — AnnotatedTypeVariable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedTypeVariable.html).
- [Java SE 21 — TypeVariable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/TypeVariable.html).
- [Java SE 21 — AnnotatedType](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedType.html).
- [Java SE 21 — AnnotatedParameterizedType](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html).
- [JLS 17 `4.4 — Type Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.4) và [JLS 17 `9.7.4 — Where Annotations May Appear](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.4).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-007">getAnnotatedReturnType, getAnnotatedExceptionTypes và receiver type</a>

<details>
<summary>Xem chi tiết</summary>

# getAnnotatedReturnType, getAnnotatedExceptionTypes và receiver type trong Java Reflection

Khi học Java Reflection, việc đọc metadata của một phương thức không chỉ dừng ở `getReturnType()` hay `getExceptionTypes()`. Java còn cho phép gắn annotation trực tiếp lên **vị trí sử dụng kiểu** (*type use*), ví dụ annotation trên kiểu trả về, kiểu exception trong mệnh đề `throws`, hoặc kiểu `this` của một instance method.

Ba API quan trọng thuộc `java.lang.reflect.Executable` là:

- `getAnnotatedReturnType()`: đọc **lần sử dụng kiểu trả về** cùng type-use annotations của nó.
- `getAnnotatedExceptionTypes()`: đọc các **lần sử dụng kiểu exception trong `throws`**, giữ thứ tự khai báo và annotation tại từng vị trí.
- `getAnnotatedReceiverType()`: đọc **kiểu receiver** (kiểu của `this` theo quy tắc ngôn ngữ), bao gồm annotation trên *explicit receiver parameter* khi có; kết quả có thể là `null` nếu executable không thể có receiver parameter.

Cả ba đều làm việc với `AnnotatedType` hoặc mảng `AnnotatedType[]`; **không cần gọi phương thức đích** để lấy thông tin. Chúng mô tả metadata ở thời điểm khai báo, **không** tự thi hành kiểm tra nghiệp vụ nào đối với annotation.

## 1. Điều kiện để đọc type-use annotation tại runtime

Annotation nhắm đến vị trí **dùng một kiểu** phải cho phép `ElementType.TYPE_USE`. Để Reflection nhìn thấy tại runtime, cần thêm `RetentionPolicy.RUNTIME`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface Mark {
    String value();
}
```

Đoạn trên là **khai báo minh họa**; chương trình độc lập bên dưới sẽ khai báo annotation `Mark` trong class của chính nó.

`@Target(TYPE_USE)` cho phép các cú pháp như:

```java
public @Mark("result") String read() { return "ok"; }
public void save() throws @Mark("io") java.io.IOException {}
```

Hai dòng chỉ minh họa vị trí type-use, chưa phải chương trình chạy độc lập và method `save` không ném `IOException` mặc dù khai báo `throws`. Java cho phép khai báo một checked exception mà phần thân method không nhất thiết phải ném exception đó.

**Phân biệt:** annotation gắn trên **khai báo method** với `ElementType.METHOD` được đọc bằng `Method.getAnnotation` hoặc `getDeclaredAnnotations()`. Annotation gắn trên **kiểu trả về** cần `getAnnotatedReturnType()`. Tương tự, annotation trên một **khai báo tham số** khác với annotation trên **kiểu của tham số**. Không được suy rằng `Method.getAnnotations()` sẽ trả toàn bộ annotation bên trong generic return type hay mệnh đề `throws`.

## 2. Bức tranh tổng quát về các API của Executable

`Executable` là lớp cha của `Method` và `Constructor`. Nó cung cấp các API về danh sách tham số, ngoại lệ, receiver và annotated types.

| Câu hỏi | API | Kiểu trả về của Reflection |
| --- | --- | --- |
| Phương thức trả kiểu nào sau erasure? | `Method.getReturnType()` | `Class<?>` |
| Kiểu trả về có cấu trúc generic nào? | `Method.getGenericReturnType()` | `Type` |
| Type-use annotations nằm ở đâu trên kiểu trả về? | `Method.getAnnotatedReturnType()` | `AnnotatedType` |
| Có những kiểu exception nào trong `throws`? | `Executable.getExceptionTypes()` | `Class<?>[]` |
| Các exception types có khai báo generic nào? | `Executable.getGenericExceptionTypes()` | `Type[]` |
| Annotation trên từng kiểu exception trong `throws`? | `Executable.getAnnotatedExceptionTypes()` | `AnnotatedType[]` |
| Kiểu receiver có annotation nào? | `Executable.getAnnotatedReceiverType()` | `AnnotatedType` hoặc `null` |
| Type-use annotations trên kiểu của formal parameters? | `Executable.getAnnotatedParameterTypes()` | `AnnotatedType[]` |

Mỗi `AnnotatedType` biểu diễn một **nút trong cây sử dụng kiểu**. Phương thức `getType()` trả về `Type` nền, còn `getAnnotation(SomeAnnotation.class)` đọc annotation nằm **ngay tại nút đó**. Nếu annotation nằm trên type argument hoặc bound sâu hơn, cần đi vào các nút con bằng API chuyên biệt như `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()`.

Đây không phải một trình đánh giá giá trị lúc chạy: `getAnnotatedExceptionTypes()` không chờ method ném exception, và `getAnnotatedReturnType()` không chờ method trả về giá trị.

## 3. getAnnotatedReturnType(): annotation trên kiểu trả về

Giả sử một method khai báo:

```java
public @Mark("result") List<@Mark("element") String> read() {
    return List.of("ok");
}
```

Đây là đoạn minh họa trong ngữ cảnh có `List` và `Mark`.

- `method.getReturnType()` trả `List.class` (generic bị **erasure**).
- `method.getGenericReturnType()` biểu diễn `List<String>` với các tham số generic nhưng chưa cho biết trực tiếp annotation tại từng vị trí.
- `method.getAnnotatedReturnType()` biểu diễn **lần dùng `List<String>`**; trên nút ngoài có `@Mark("result")`.
- Vì kiểu ngoài là parameterized, có thể dùng `AnnotatedParameterizedType` và `getAnnotatedActualTypeArguments()[0]` để đến nút **`String`**; ở đó mới có `@Mark("element")`.

Ví dụ đường truy cập metadata:

```java
Method method = Service.class.getDeclaredMethod("read");
AnnotatedType returnUse = method.getAnnotatedReturnType();
Mark outer = returnUse.getAnnotation(Mark.class);

if (returnUse instanceof AnnotatedParameterizedType pt) {
    AnnotatedType stringUse =
            pt.getAnnotatedActualTypeArguments()[0];
    Mark inner = stringUse.getAnnotation(Mark.class);
}
```

Đoạn này cần imports `java.lang.reflect.*`, class `Service` và `Mark` đã khai báo, đồng thời xử lý `NoSuchMethodException`; ví dụ chạy đầy đủ có ở mục 6.

**Cần nhớ:** `getAnnotatedReturnType()` vẫn trả một `AnnotatedType` với method không có annotation trên return type. Mảng `getAnnotations()` rỗng chỉ có nghĩa nút hiện tại không mang annotation được phản chiếu, không có nghĩa không tồn tại return type.

Với phương thức `void ping()`, kiểu trả về vẫn được biểu diễn bằng `void.class`; `getAnnotatedReturnType().getType()` biểu diễn `void`. Nó **không phải** `null` hay `Void.class`.

`Executable.getAnnotatedReturnType()` cũng áp dụng cho **constructor**, nhưng ngữ nghĩa khác: đối tượng `AnnotatedType` biểu diễn **kiểu đối tượng được xây dựng**, không phải một kiểu return được khai báo như method. Không nên gọi `getReturnType()` trên `Constructor`, vì phương thức đó thuộc `Method`, không phải `Constructor`.

## 4. getAnnotatedExceptionTypes(): annotation trong throws

Ví dụ một phương thức khai báo:

```java
public void load()
        throws @Mark("io") IOException,
               @Mark("state") IllegalStateException {
}
```

Đoạn này minh họa khai báo method, giả định import `IOException` và `Mark`. Dù thân phương thức trống, hai kiểu xuất hiện trong mệnh đề `throws` vẫn là metadata có thể đọc.

`method.getAnnotatedExceptionTypes()` trả về **`AnnotatedType[]` theo đúng thứ tự các exception types được liệt kê trong mệnh đề `throws`**:

| Chỉ số | `getType()` của phần tử | Annotation tại phần tử |
| --- | --- | --- |
| `0` | `java.io.IOException` | `@Mark("io")` |
| `1` | `java.lang.IllegalStateException` | `@Mark("state")` |

Tương phản:

- `getExceptionTypes()` trả `Class<?>[]`, cho biết các lớp exception được khai báo nhưng không mang thông tin type-use annotations ngay trong phần tử `Class`.
- `getGenericExceptionTypes()` trả `Type[]` khi cần thông tin kiểu tổng quát, chẳng hạn một type variable kiểu `E extends Exception` trong `throws E`.
- `getAnnotatedExceptionTypes()` trả `AnnotatedType[]`, cho phép đọc annotation trên lần dùng từng exception type, và với trường hợp phù hợp có thể duyệt các dạng `AnnotatedTypeVariable`.

**Giới hạn rất quan trọng:** Mảng này chứa các exception types **được khai báo trong `throws`**, không phải danh sách tất cả lỗi thực tế có thể phát sinh khi method chạy. Một method có `throws IOException` nhưng chưa từng ném nó vẫn trả metadata; ngược lại, method không khai báo `throws` vẫn có thể ném `RuntimeException` hoặc `Error`. Nếu không có kiểu exception nào trong `throws`, mảng kết quả dài **0**, không phải `null`.

Mảng `AnnotatedType[]` của exception types giữ **thứ tự khai báo**, khác với một số API Reflection trả mảng method/field mà thứ tự không được bảo đảm như thứ tự source.

## 5. Receiver type là gì? Vì sao khác với parameter thông thường?

Với instance method, **receiver** là đối tượng mà method sẽ thực thi trên đó, thường được nhắc đến bằng `this`. Java cho phép viết một **explicit receiver parameter** nhằm gắn type-use annotation lên kiểu receiver:

```java
class Service {
    public void action(@Mark("receiver") Service this) {
        // this là đối tượng nhận lời gọi action().
    }
}
```

Đây là đoạn minh họa trong ngữ cảnh `Mark` đã khai báo. Receiver parameter có cú pháp giống một tham số ở chữ ký, nhưng **không** phải formal parameter mang giá trị độc lập được truyền bằng lời gọi. Trong ví dụ:

- `action()` vẫn được gọi bằng `service.action()`, **không truyền thêm một đối số** cho `this`.
- `Method.getParameterCount()` của `action` vẫn bằng **0**.
- `Method.getParameterTypes()` và `getAnnotatedParameterTypes()` không thêm một phần tử cho explicit receiver.
- `getAnnotatedReceiverType()` cung cấp `AnnotatedType` có thể chứa `@Mark("receiver")`.
- Việc thêm receiver parameter không thay đổi overload signature của method và không cho phép gọi static method bằng receiver.

Với method `read(@Mark("receiver") Service this)` có annotation, ta có thể đọc:

```java
AnnotatedType receiverUse = method.getAnnotatedReceiverType();
Mark tag = receiverUse.getAnnotation(Mark.class);
```

**Các trường hợp cần phân biệt** theo hợp đồng JDK 21:

| Loại executable | `getAnnotatedReceiverType()` |
| --- | --- |
| Instance method có explicit receiver annotation | Một `AnnotatedType` chứa annotation trên kiểu receiver |
| Instance method không viết receiver parameter (hoặc receiver không mang annotation) | Vẫn là `AnnotatedType`, nhưng **không có annotation** tại receiver |
| Static method | `null` vì không thể có receiver parameter |
| Constructor của top-level class hoặc static member class | `null` |
| Constructor của non-static inner member class | Có thể trả `AnnotatedType` biểu diễn **enclosing receiver type**, kể cả không ghi explicit receiver annotation |
| Constructor của local/anonymous class | `null` theo hợp đồng API |

Vì vậy, **`getAnnotatedReceiverType() == null` không đồng nghĩa “không viết annotation”**. Một instance method không viết explicit receiver vẫn trả `AnnotatedType` *không có annotation*; còn static method trả `null` vì không có receiver theo loại executable. Ngược lại, một `AnnotatedType` không có annotation không chứng minh trong source đã khai báo một explicit receiver parameter.

Với inner-class constructor, quy tắc ngữ pháp receiver khác instance method thông thường (receiver gắn với enclosing instance), nên không thể máy móc dùng kiểu `Inner this` như với method. Hãy xem JLS nếu cần phân tích chi tiết receiver của constructor.

**Receiver type không phải kiểu runtime của object cụ thể:** `getAnnotatedReceiverType()` mô tả kiểu receiver theo khai báo, không kiểm tra `receiver.getClass()` của object khi chạy, cũng không thay đổi dynamic dispatch.

## 6. Ví dụ Java 17+ đầy đủ, chạy được

Tạo tệp `AnnotatedExecutableDemo.java`:

```java
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.List;

public class AnnotatedExecutableDemo {
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Mark {
        String value();
    }

    public static class Service {
        public @Mark("result") List<@Mark("element") String> read(
                @Mark("receiver") Service this)
                throws @Mark("io") IOException,
                       @Mark("state") IllegalStateException {
            return List.of("ok");
        }

        public String plain() {
            return "plain";
        }

        public static void ping() {}

        public void noThrows() {}

        public Service() {}
    }

    private static String mark(AnnotatedType type) {
        if (type == null) {
            return "null";
        }
        Mark ann = type.getAnnotation(Mark.class);
        return ann == null ? "none" : ann.value();
    }

    public static void main(String[] args) throws Exception {
        Method read = Service.class.getDeclaredMethod("read");

        AnnotatedType ret = read.getAnnotatedReturnType();
        System.out.println(
                "return.raw=" + read.getReturnType().getTypeName()
        );
        System.out.println("return.tag=" + mark(ret));

        AnnotatedType elem =
                ((AnnotatedParameterizedType) ret)
                .getAnnotatedActualTypeArguments()[0];
        System.out.println(
                "return.argument="
                + elem.getType().getTypeName() + ":" + mark(elem)
        );

        AnnotatedType[] exceptions =
                read.getAnnotatedExceptionTypes();
        System.out.println("exceptions=" + exceptions.length);

        for (int i = 0; i < exceptions.length; i++) {
            System.out.println(
                    "throws[" + i + "]="
                    + exceptions[i].getType().getTypeName()
                    + ":" + mark(exceptions[i])
            );
        }

        AnnotatedType receiver = read.getAnnotatedReceiverType();
        System.out.println("receiver.tag=" + mark(receiver));
        System.out.println(
                "receiver.type="
                + ((Class<?>) receiver.getType()).getSimpleName()
        );
        System.out.println(
                "receiver.isFormalParameter=" + read.getParameterCount()
        );

        Method plain = Service.class.getDeclaredMethod("plain");
        System.out.println(
                "plain.receiver.tag="
                + mark(plain.getAnnotatedReceiverType())
        );

        Method ping = Service.class.getDeclaredMethod("ping");
        System.out.println(
                "static.receiver=" + mark(ping.getAnnotatedReceiverType())
        );
        System.out.println(
                "static.return.type="
                + ping.getAnnotatedReturnType().getType().getTypeName()
        );

        Method noThrows = Service.class.getDeclaredMethod("noThrows");
        System.out.println(
                "noThrows=" + noThrows.getAnnotatedExceptionTypes().length
        );

        System.out.println(
                "constructor.receiver="
                + mark(Service.class.getDeclaredConstructor()
                        .getAnnotatedReceiverType())
        );
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac AnnotatedExecutableDemo.java
java AnnotatedExecutableDemo
```

Kết quả mong đợi trên Java 17+:

```text
return.raw=java.util.List
return.tag=result
return.argument=java.lang.String:element
exceptions=2
throws[0]=java.io.IOException:io
throws[1]=java.lang.IllegalStateException:state
receiver.tag=receiver
receiver.type=Service
receiver.isFormalParameter=0
plain.receiver.tag=none
static.receiver=null
static.return.type=void
noThrows=0
constructor.receiver=null
```

Giải thích:

1. `getReturnType()` cho `List.class` sau erasure. `getAnnotatedReturnType()` giữ `@Mark("result")` tại nút ngoài của kiểu trả về.
2. `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()[0]` đi xuống `String` để tìm `@Mark("element")`, thay vì trộn annotation của container và element.
3. `getAnnotatedExceptionTypes()` trả hai kiểu `IOException` và `IllegalStateException` theo thứ tự khai báo, với giá trị `io` và `state` tương ứng.
4. `getAnnotatedReceiverType()` của `read` giữ annotation `receiver` trên `Service this`. Class cơ sở của receiver có simple name `Service`.
5. `getParameterCount() == 0` chứng minh receiver khai báo tường minh **không** phải formal parameter mà người gọi phải truyền vào.
6. `plain()` là instance method, không viết explicit receiver: API vẫn trả một `AnnotatedType` nhưng không có annotation `Mark`, nên nhãn là `none`.
7. `ping()` là static method: receiver type là `null`; return type là `void`.
8. `noThrows()` không có `throws`: mảng `getAnnotatedExceptionTypes()` rỗng.
9. Constructor `Service()` thuộc **static member class**, nên không có receiver kiểu enclosing instance và `getAnnotatedReceiverType()` trả `null`.

Chương trình chỉ đọc metadata; không gọi `read()` hay `plain()`, không cần tạo một đối tượng `Service`. Mỗi method được tìm bằng tên cụ thể, nên không phụ thuộc thứ tự của `getDeclaredMethods()`.

## 7. Nếu kiểu trả về/exception là generic hoặc phức hợp

`AnnotatedType` là một họ interface. Đọc annotation của **nút ngoài** bằng `getAnnotation` chưa đủ nếu annotation ở các thành phần bên trong.

Với một return type có đối số generic:

```java
public List<@Mark("item") String> items() {
    return List.of();
}
```

Cần kiểm tra `getAnnotatedReturnType() instanceof AnnotatedParameterizedType`, sau đó duyệt `getAnnotatedActualTypeArguments()` để lấy annotation tại `String`.

Khi return type là mảng, wildcard hay type variable, các nhánh khác của họ `AnnotatedType` cần xử lý riêng:

| Loại nút | API duyệt thông tin con |
| --- | --- |
| `AnnotatedParameterizedType` | `getAnnotatedActualTypeArguments()` và khi phù hợp `getAnnotatedOwnerType()` |
| `AnnotatedArrayType` | `getAnnotatedGenericComponentType()` |
| `AnnotatedWildcardType` | `getAnnotatedUpperBounds()`, `getAnnotatedLowerBounds()` |
| `AnnotatedTypeVariable` | `getAnnotatedBounds()` |

Ví dụ method generic:

```java
public <E extends Exception> void call() throws E {}
```

Đây là đoạn minh họa, không phải chương trình độc lập. `getGenericExceptionTypes()` có thể trả một `TypeVariable` đại diện cho `E`, còn `getAnnotatedExceptionTypes()` biểu diễn **lần sử dụng `E` trong `throws`** dưới dạng `AnnotatedType`, có thể là `AnnotatedTypeVariable`. Annotation đặt trên **bound** `E extends Exception` là thông tin ở **vị trí bound** khác với annotation đặt trên **lần dùng `E` trong `throws`**.

Nếu viết bộ duyệt metadata đệ quy trên các bounds, cần chú ý chu trình type variable (ví dụ `T extends Comparable<T>`) và có cơ chế visited-set phù hợp. Đừng ép kiểu mọi `AnnotatedType` thành `AnnotatedParameterizedType`.

## 8. Ba loại annotation dễ bị đọc nhầm

Một framework kiểm tra annotation trên method nên tách ít nhất ba câu hỏi:

| Vị trí cần đọc | API phù hợp | Ví dụ minh họa |
| --- | --- | --- |
| Annotation trên **khai báo method** | `method.getAnnotation(DeclarationAnnotation.class)` | `@Deprecated` ngay trước khai báo method |
| Annotation trên **kiểu trả về** | `method.getAnnotatedReturnType().getAnnotation(TypeUseAnnotation.class)` | `@Mark("result") List<String> read()` |
| Annotation trên **đối số của kiểu trả về** | `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()[i]` | `List<@Mark("item") String>` |
| Annotation trên **kiểu exception** | `method.getAnnotatedExceptionTypes()[i].getAnnotation(...)` | `throws @Mark("io") IOException` |
| Annotation trên **kiểu receiver** | `method.getAnnotatedReceiverType().getAnnotation(...)` (nếu khác `null`) | `method(@Mark("this") C this)` |
| Annotation trên **kiểu formal parameter** | `method.getAnnotatedParameterTypes()[i]` | `save(@Mark("argument") String s)` |

Ở hàng receiver, `C` tượng trưng cho declaring class của instance method; mã minh họa chỉ thể hiện cú pháp, không dùng làm class Java độc lập.

Nếu annotation được khai báo với **nhiều target** (`METHOD`, `PARAMETER`, `TYPE_USE`...), cần kiểm tra vị trí cú pháp và quy tắc ghi metadata của compiler, thay vì mặc định nó được lưu ở một API duy nhất. Nếu không có `@Retention(RUNTIME)`, việc không nhìn thấy annotation qua Reflection runtime chưa chứng minh annotation vắng mặt trong source hoặc class file.

## 9. Lỗi phổ biến và cách khắc phục

**Lỗi 1 — Dùng `getReturnType()` để đọc annotation trong `List<@Mark String>`.** `getReturnType()` chỉ trả `Class<?> = List.class`; dùng `getAnnotatedReturnType()` rồi duyệt đến generic argument.

**Lỗi 2 — Đọc `getExceptionTypes()` rồi cho rằng đã có annotation trên `throws`.** Kết quả chỉ là `Class<?>[]`; dùng `getAnnotatedExceptionTypes()` để có `AnnotatedType[]`.

**Lỗi 3 — Tưởng `getAnnotatedExceptionTypes()` liệt kê lỗi sẽ xảy ra lúc chạy.** Nó chỉ phản ánh **`throws` clause**; không chạy phương thức, không quan sát exception phát sinh thực tế.

**Lỗi 4 — Đồng nhất `getAnnotatedReceiverType() == null` với receiver không được annotation.** Instance method có thể trả `AnnotatedType` không có annotation; `null` được dùng khi executable **không thể có receiver parameter**, ví dụ static method.

**Lỗi 5 — Nghĩ explicit receiver là một tham số bình thường.** `Service this` không làm tăng `getParameterCount()` và không buộc người gọi truyền thêm `this`.

**Lỗi 6 — Tưởng receiver type phản ánh runtime subclass.** Metadata ghi kiểu receiver theo khai báo; object khi chạy có thể thuộc lớp con, còn dynamic dispatch là vấn đề riêng.

**Lỗi 7 — Không xử lý mảng rỗng hoặc `null`.** `getAnnotatedExceptionTypes()` có thể trả mảng rỗng; `getAnnotatedReceiverType()` có thể trả `null`. Kiểm tra trước khi lấy `[0]` hoặc `getAnnotation`.

**Lỗi 8 — Nhầm annotation khai báo method với annotation trên kiểu trả về.** `Method.getAnnotations()` không tự bao gồm annotation tại tất cả các type-use positions.

**Lỗi 9 — Bỏ qua `RetentionPolicy.RUNTIME`.** `TYPE_USE` chỉ cho phép đặt annotation; không bảo đảm annotation xuất hiện qua Reflection nếu retention không phù hợp.

**Lỗi 10 — Xem `AnnotatedType` luôn là một kiểu đơn giản.** Return type có thể parameterized, array, wildcard hoặc type variable; phải đi đúng cấu trúc cây kiểu, không cast bừa.

## 10. Tự kiểm tra

1. Phương thức có kiểu trả về `@Mark("top") List<@Mark("item") String>`. Đọc `top` ở đâu? **Tại `getAnnotatedReturnType()`**; đọc `item` ở **nút type argument thứ nhất**.
2. `throws @Mark("io") IOException` có `getExceptionTypes()[0]` trả annotation không? **Không**, phần tử là `Class`; cần `getAnnotatedExceptionTypes()[0]`.
3. Method không khai báo `throws` thì `getAnnotatedExceptionTypes()` trả gì? **Mảng rỗng**, không phải `null`.
4. Static method có receiver type không? **Không**, `getAnnotatedReceiverType()` trả `null`.
5. Instance method không có explicit receiver annotation thì receiver type có bắt buộc `null` không? **Không**; API trả `AnnotatedType` không có annotation trên receiver.
6. `void act(@Mark("x") C this)` khai báo bao nhiêu **formal parameters**? **0**; receiver không tính là formal parameter thông thường.
7. `getAnnotatedReceiverType()` có phải kiểu runtime chính xác của đối tượng đang gọi không? **Không**; nó là metadata trên kiểu receiver theo khai báo.
8. `getAnnotatedReturnType()` có chạy phương thức và xem đối tượng được trả về không? **Không**; chỉ đọc cấu trúc khai báo.
9. Với method `<E extends Exception> void call() throws E`, nên xem API nào để thấy cấu trúc kiểu generic ở `throws`? **`getGenericExceptionTypes()`**, và dùng **`getAnnotatedExceptionTypes()`** khi cần annotation trên lần dùng kiểu.
10. Vì sao annotation `@Target(TYPE_USE)` nhưng không `@Retention(RUNTIME)` có thể không đọc được bằng Reflection? **Vì quyền đặt annotation và khả năng giữ annotation đến runtime là hai thuộc tính độc lập.**

## 11. Tài liệu chính thức

- [Oracle Java SE 21 — `Executable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html): các hợp đồng `getAnnotatedReturnType()`, `getAnnotatedExceptionTypes()`, `getAnnotatedReceiverType()`, kiểu receiver của method và constructor.
- [Oracle Java SE 21 — `Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): thông tin method, return type, generic return type và annotation.
- [Oracle Java SE 21 — `AnnotatedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedType.html): `getType()`, annotation tại mỗi type-use node.
- [Oracle Java SE 21 — `AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html): annotation trên các đối số generic.
- [Java Language Specification SE 21 — §8.4.1 Formal Parameters](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.1): receiver parameters của instance methods.
- [Java Language Specification SE 21 — §9.7 Annotations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html#jls-9.7): annotation tại vị trí type use.

**Ghi nhớ:** `getAnnotatedReturnType()` nhìn vào **kiểu trả về**; `getAnnotatedExceptionTypes()` nhìn vào **những kiểu ghi trong `throws`**; `getAnnotatedReceiverType()` nhìn vào **kiểu của receiver `this`**. Cả ba đọc *annotation trên vị trí dùng kiểu*, không phải cùng một nhóm annotation của khai báo method, và luôn phải xét đúng nút `AnnotatedType` cũng như khả năng trả mảng rỗng hoặc `null`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-008">Annotation trên superclass và interface được khai báo</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation trên superclass và interface được khai báo

## 1. Vấn đề: annotation nằm trên *kiểu được sử dụng* hay trên *khai báo kiểu*?

Trong Java, annotation có thể gắn vào **khai báo lớp/interface** hoặc vào **vị trí sử dụng một kiểu** trong mệnh đề `extends` và `implements`. Đây là hai nơi khác nhau dù cùng liên quan tới quan hệ kế thừa.

~~~java
// Mảnh minh họa cú pháp. Các annotation đã được định nghĩa phù hợp.
@Category("domain")                    // Annotation của declaration: Base
class Base {}

class Child extends @Edge("inheritance") Base {}
//                  ^ Annotation trên type use "Base" tại cạnh Child -> Base.
~~~

- `@Category` mô tả khai báo `Base`; tra cứu qua `Base.class.getAnnotation(Category.class)`.
- `@Edge` mô tả **việc dùng kiểu `Base` làm superclass của `Child`**, không tự gắn vào `Base.class` và không phải annotation declaration của `Child`.
- Nếu có `AnotherChild extends @Edge("another") Base`, hai cạnh có thể mang annotation khác nhau mà `Base` vẫn là một khai báo duy nhất.
- Với `implements Interface` hoặc `interface Sub extends Interface`, ý tưởng giống hệt: annotation tại cạnh sử dụng interface, không phải annotation trên chính interface declaration.

Chủ đề này thuộc **type-use annotations** (`@Target(ElementType.TYPE_USE)`), được hỗ trợ từ Java 8. Java Reflection sử dụng `AnnotatedType` để trả về annotation ở các vị trí sử dụng kiểu, chứ không chỉ trả `Class<?>`.

## 2. API chính: getAnnotatedSuperclass và getAnnotatedInterfaces

| API Java Reflection | Kết quả | Phạm vi |
| --- | --- | --- |
| `clazz.getSuperclass()` | `Class<?>` của superclass trực tiếp (nếu có) | Không có type-use annotations, không giữ generic args. |
| `clazz.getGenericSuperclass()` | `Type` của superclass trực tiếp | Có generic declaration, **không chứa annotation tại type-use**. |
| `clazz.getAnnotatedSuperclass()` | `AnnotatedType` mô tả *việc dùng superclass* của class | Có thể đọc TYPE_USE annotation trên `extends` và trên generic argument lồng bên trong. |
| `clazz.getInterfaces()` | `Class<?>[]` chứa các superinterfaces **trực tiếp** | Không chứa TYPE_USE annotation hoặc generic args. |
| `clazz.getGenericInterfaces()` | `Type[]` chứa các superinterfaces trực tiếp | Giữ generic args nhưng không chứa TYPE_USE annotation. |
| `clazz.getAnnotatedInterfaces()` | `AnnotatedType[]` chứa các *lần dùng interface* trực tiếp | Có TYPE_USE annotation trên `implements`/`extends` và các type arguments. |

Các API `getAnnotatedSuperclass()`, `getAnnotatedInterfaces()` được khai báo trên `java.lang.Class`. Hai API này khác hẳn `Class.getAnnotations()` và `Class.getDeclaredAnnotations()`: các phương thức sau đọc **declaration annotations của đối tượng Class**, không phải annotation trên cạnh superclass/interface. 

Theo hợp đồng Java SE 17:

- Với class thông thường, `getAnnotatedSuperclass()` mô tả superclass trực tiếp. Nếu superclass không được khai báo có annotation, nó vẫn có thể trả `AnnotatedType` với mảng annotation **rỗng**. Ví dụ một class không viết `extends` vẫn có superclass `Object` ngầm.
- `getAnnotatedSuperclass()` trả **null** với `Object.class`, interface, array class, primitive và `void.class`.
- `getAnnotatedInterfaces()` trả mảng theo **thứ tự khai báo** trong `implements` hoặc `extends` của interface. Không có direct superinterface thì mảng rỗng.
- Các API `getAnnotatedInterfaces()` không tự nối toàn bộ interface gián tiếp mà superclass đã implement. Muốn duyệt transitive hierarchy, người viết công cụ phải duyệt tiếp các nút cha.

Ví dụ `class Child implements A, B` thì `getAnnotatedInterfaces()[0]` phản ánh cách dùng `A`, phần tử `[1]` là `B`. Không nên phụ thuộc index một cách mù quáng khi mã nguồn có thể đổi thứ tự; hãy so `AnnotatedType.getType()` với kiểu đang tìm hoặc xác định đường đi rõ ràng.

## 3. Khai báo annotation đúng cho TYPE_USE

Annotation trên superclass/interface use phải cho phép target `TYPE_USE`. Nếu muốn đọc bằng Reflection lúc chạy, cần `@Retention(RetentionPolicy.RUNTIME)`. Ví dụ:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Edge {
    String value();
}
~~~

Đây là mảnh mã khai báo riêng để minh họa. Nếu ghép với ví dụ độc lập ở phần 6, **không** khai báo trùng annotation `Edge`.

Cần tách bạch:

| Vị trí | Ví dụ | Target thích hợp | API đọc |
| --- | --- | --- | --- |
| Khai báo lớp | `@Role class Base {}` | `TYPE` | `Base.class.getAnnotation(Role.class)` |
| Type use tại superclass | `class Child extends @Edge Base {}` | `TYPE_USE` | `Child.class.getAnnotatedSuperclass().getAnnotation(Edge.class)` |
| Type use tại implemented interface | `class Child implements @Edge Contract {}` | `TYPE_USE` | Duyệt `Child.class.getAnnotatedInterfaces()` rồi `getAnnotation(Edge.class)` |
| Type use tại superinterface | `interface Sub extends @Edge Contract {}` | `TYPE_USE` | `Sub.class.getAnnotatedInterfaces()` |
| Type argument lồng trong superclass | `extends Base<@Edge String>` | `TYPE_USE` | `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` |
| Khai báo type parameter | `class Base<@Marker T> {}` | `TYPE_PARAMETER` | `TypeVariable.getAnnotations()` (ngữ nghĩa declaration khác type-use). |

Không coi `@Target(TYPE)` và `@Target(TYPE_USE)` là tương đương. Có thể khai báo một annotation cho nhiều target nếu thật sự cần, nhưng phải hiểu API nào được yêu cầu và vị trí annotation được sử dụng. Một annotation có `RetentionPolicy.CLASS` có thể có trong class file mà API Reflection runtime không cung cấp nó. `RetentionPolicy.SOURCE` không được giữ tới class file.

## 4. AnnotatedType biểu diễn gì?

`java.lang.reflect.AnnotatedType` là một `AnnotatedElement` mô tả *cách dùng kiểu*, vì vậy có:

- `getType()`: `java.lang.reflect.Type` phía dưới, có thể là `Class<?>` hoặc `ParameterizedType`.
- `getAnnotation(Edge.class)`, `isAnnotationPresent(...)`, `getAnnotations()`, `getDeclaredAnnotations()`: đọc annotation gắn trên **lần sử dụng kiểu đang được đại diện**.
- `getAnnotatedOwnerType()`: khi là kiểu thành viên và có enclosing owner type được biểu diễn, cho phép khảo sát phần owner (có thể null).

Khi superclass/interface có generic arguments, kiểu trả về thường là `AnnotatedParameterizedType`, với `getAnnotatedActualTypeArguments()` giữ annotation của từng tham số kiểu. Chẳng hạn:

~~~java
// Đã có Base<T>, Contract<T> và @Edge cho phép TYPE_USE.
class Child extends @Edge("super") Base<@Edge("arg") String>
        implements @Edge("contract") Contract<@Edge("generic-arg") Integer> {}
~~~

Phải đọc **hai tầng**:

1. `Child.class.getAnnotatedSuperclass().getAnnotation(Edge.class)` đọc `@Edge("super")`.
2. Ép superclass annotated type sang `AnnotatedParameterizedType` rồi lấy argument `[0].getAnnotation(Edge.class)` để đọc `@Edge("arg")`.

Tương tự, `getAnnotatedInterfaces()[0].getAnnotation(Edge.class)` đọc annotation tại interface use, còn annotation của `Integer` là ở annotated generic argument. **Annotation ngoài và annotation trong không tự hợp nhất**: `getAnnotations()` trên outer type use không tự trả annotation của mọi nested type argument.

Ngoài `AnnotatedParameterizedType`, các subtype liên quan gồm `AnnotatedArrayType`, `AnnotatedWildcardType`, `AnnotatedTypeVariable`. Nếu muốn hỗ trợ tất cả cách viết generic phức tạp, parser phải duyệt đệ quy. Đừng cast trực tiếp sang `Class<?>` hoặc `AnnotatedParameterizedType` khi không kiểm tra bằng `instanceof`; kiểu raw hoặc non-generic thường không phải annotated parameterized type.

## 5. @Inherited KHÔNG phải cơ chế kế thừa annotation trên cạnh extends/implements

`java.lang.annotation.Inherited` thường gây nhầm lẫn ở phần này.

Annotation khai báo:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@java.lang.annotation.Inherited
@interface Role {
    String value();
}
~~~

`@Inherited` chỉ định **declaration annotation** có thể được tìm theo **superclass chain** khi truy vấn trên khai báo class. Nếu `Base` có `@Role`, class con không tự khai báo `@Role` nhưng `Child.class.getAnnotation(Role.class)` có thể trả annotation từ `Base`. Điều này không có nghĩa annotation được chép vào class file của `Child`: `Child.class.getDeclaredAnnotation(Role.class)` vẫn null khi Child không khai báo trực tiếp.

Ba giới hạn cần nhớ:

- **Không kế thừa từ interface** qua `@Inherited`: `class Impl implements @SomeUse Interface` không nhận declaration annotation `@Role` của interface chỉ bởi implements nó.
- **Không tự lan truyền type-use annotation trên cạnh**: nếu `Child extends @Edge Base` thì `Grandchild extends Child` không tự có `@Edge` tại cạnh `Grandchild -> Child`. Muốn biết annotation của cạnh `Child -> Base`, phải chủ động duyệt đến `Child.class.getAnnotatedSuperclass()`.
- **Không áp dụng để tìm annotation trên mọi vị trí cú pháp**: `@Inherited` được định nghĩa cho việc truy vấn declaration annotation của class; không dùng nó như một bộ duyệt `AnnotatedType` hoặc thuật toán tìm annotation trên interface.

Đây chính là lý do một công cụ thiết kế policy đặt trên cạnh inheritance cần lưu ngữ cảnh **lớp khai báo cạnh**; còn công cụ xử lý class-level marker cần xét `getAnnotation`/`getDeclaredAnnotation` và quy tắc `@Inherited`. Hai nhiệm vụ khác nhau không nên dùng cùng một quy tắc “thừa kế annotation”.

## 6. Ví dụ Java 17+ hoàn chỉnh: superclass, interface và generic argument

Lưu file `AnnotatedHierarchyDemo.java`:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;

public class AnnotatedHierarchyDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface Edge {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Role {
        String value();
    }

    @Role("base-declaration")
    static class Base<T> {
    }

    @Role("interface-declaration")
    interface Contract<T> {
    }

    static class Child
            extends @Edge("superclass-edge") Base<@Edge("superclass-arg") String>
            implements @Edge("interface-edge") Contract<@Edge("interface-arg") Integer> {
    }

    static class Grandchild extends Child {
    }

    static class OnlyInterface implements Contract<String> {
    }

    interface SubContract extends @Edge("superinterface-edge") Contract<Long> {
    }

    static String edge(AnnotatedType annotated) {
        Edge value = annotated.getAnnotation(Edge.class);
        return value == null ? "-" : value.value();
    }

    static String role(Class<?> type) {
        Role value = type.getAnnotation(Role.class);
        return value == null ? "-" : value.value();
    }

    public static void main(String[] args) {
        AnnotatedType superclass = Child.class.getAnnotatedSuperclass();
        AnnotatedType interfaceUse = Child.class.getAnnotatedInterfaces()[0];

        System.out.println("Child superclass edge=" + edge(superclass));
        System.out.println("Child superclass argument="
                + edge(((AnnotatedParameterizedType) superclass)
                        .getAnnotatedActualTypeArguments()[0]));
        System.out.println("Child interface edge=" + edge(interfaceUse));
        System.out.println("Child interface argument="
                + edge(((AnnotatedParameterizedType) interfaceUse)
                        .getAnnotatedActualTypeArguments()[0]));

        System.out.println("SubContract superinterface edge="
                + edge(SubContract.class.getAnnotatedInterfaces()[0]));
        System.out.println("Base declaration role=" + role(Base.class));
        System.out.println("Child inherited role=" + role(Child.class));
        System.out.println("Child declared role="
                + (Child.class.getDeclaredAnnotation(Role.class) != null));
        System.out.println("OnlyInterface inherited role=" + role(OnlyInterface.class));
        System.out.println("Grandchild role=" + role(Grandchild.class));
        System.out.println("Grandchild direct edge="
                + edge(Grandchild.class.getAnnotatedSuperclass()));
        System.out.println("Grandchild direct interfaces="
                + Grandchild.class.getAnnotatedInterfaces().length);
        System.out.println("Interface has superclass="
                + (Contract.class.getAnnotatedSuperclass() != null));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 AnnotatedHierarchyDemo.java
java AnnotatedHierarchyDemo
~~~

Đầu ra kỳ vọng trên Java 17+:

~~~text
Child superclass edge=superclass-edge
Child superclass argument=superclass-arg
Child interface edge=interface-edge
Child interface argument=interface-arg
SubContract superinterface edge=superinterface-edge
Base declaration role=base-declaration
Child inherited role=base-declaration
Child declared role=false
OnlyInterface inherited role=-
Grandchild role=base-declaration
Grandchild direct edge=-
Grandchild direct interfaces=0
Interface has superclass=false
~~~

Giải thích từng nhóm kết quả:

- `Child` có **một superclass trực tiếp** và một **direct implemented interface**, mỗi cạnh gắn `@Edge` riêng. Reflection trả về annotation chính xác tại vị trí tương ứng.
- `Base<@Edge String>` / `Contract<@Edge Integer>` giữ `@Edge` ở type argument; do đó phải đi qua `AnnotatedParameterizedType` để đọc chúng.
- `SubContract` là một **interface** mở rộng interface khác. Nó không có class superclass (`getAnnotatedSuperclass()==null`) nhưng `getAnnotatedInterfaces()` vẫn phản ánh cạnh `extends Contract<Long>`.
- `Role` là declaration annotation của `Base` và `Contract`. `Child` kế thừa `Role("base-declaration")` từ superclass nhờ `@Inherited`, nhưng `getDeclaredAnnotation` trên `Child` không trả nó.
- `OnlyInterface` không có superclass được đánh dấu `Role`; `@Role` trên `Contract` **không chuyển sang class implement** theo `@Inherited`.
- `Grandchild` tiếp tục được truy vấn declaration annotation `Role` dọc class superclass chain, nhưng direct superclass edge (`Grandchild -> Child`) **không** có `@Edge`, nên `edge(...)` trả `-`.
- `Grandchild.getAnnotatedInterfaces().length == 0` vì Grandchild **không trực tiếp khai báo implements**. Điều đó không có nghĩa một `Grandchild` không phải `Contract`: nó vẫn kế thừa implementation interface qua `Child` trong type hierarchy.

Nếu thay `@Retention(RUNTIME)` của `Edge` thành `CLASS`, runtime Reflection sẽ không thấy các marker đó nữa. Thứ tự type arguments trong mảng trả về tương ứng với thứ tự trong khai báo `Base<T>`/`Contract<T>`.

## 7. Duyệt nhiều cấp đúng cách

`getAnnotatedSuperclass()` và `getAnnotatedInterfaces()` cung cấp **các cạnh trực tiếp của node hiện tại**, không duyệt closure của toàn bộ đồ thị inheritance. Một framework tìm type-use annotations qua phân cấp có thể làm như sau:

1. Chọn class/interface gốc `root` cần phân tích.
2. Tại mỗi node `current`, đọc `current.getAnnotatedSuperclass()` (nếu khác null), xử lý annotation của **cạnh đi tới superclass**.
3. Duyệt `current.getAnnotatedInterfaces()` theo thứ tự trả về, xử lý annotation của **từng cạnh interface trực tiếp**.
4. Từ mỗi annotated edge, lấy `getType()` để xác định type đích. Nếu là `ParameterizedType`, dùng `getRawType()` để xác định class/interface cần duyệt tiếp; nếu là `Class<?>`, dùng trực tiếp.
5. Khi cần generic argument annotation, phân nhánh `instanceof AnnotatedParameterizedType` rồi duyệt `getAnnotatedActualTypeArguments()`, và sâu hơn với array/wildcard/nested owner type khi thích hợp.
6. Duyệt tiếp các class/interface đích theo chính sách ứng dụng (class chain riêng, interface graph riêng hoặc cả hai). Có thể gặp cùng một interface theo **nhiều đường đi**, vì vậy lưu visited để tránh lặp nếu chỉ cần node duy nhất; nếu nghiệp vụ đánh giá **từng cạnh có annotation riêng**, phải giữ thông tin đường đi/cạnh thay vì vô tình gộp tất cả.
7. Đừng tự động đẩy annotation từ cạnh cha xuống mọi cạnh con: semantics của `@Edge` do ứng dụng định nghĩa; Java chỉ lưu và cung cấp metadata, **không thực thi policy runtime thay người viết framework**.

Một hàm chỉ cần xác định kiểu raw từ `AnnotatedType` có thể dùng:

~~~java
// Đoạn mã để đặt trong class có import java.lang.reflect.*.
// Nó chỉ giải raw class cho Class và ParameterizedType, không phải resolver
// tổng quát cho mọi TypeVariable, GenericArrayType hay WildcardType.
static Class<?> rawClass(AnnotatedType annotated) {
    java.lang.reflect.Type type = annotated.getType();
    if (type instanceof Class<?> cls) {
        return cls;
    }
    if (type instanceof java.lang.reflect.ParameterizedType pt
            && pt.getRawType() instanceof Class<?> cls) {
        return cls;
    }
    throw new IllegalArgumentException("Unsupported raw type: " + type);
}
~~~

Code trên là đoạn tiện ích minh họa, không phải chương trình độc lập. Chưa xử lý mọi `Type` và không thể thay thế bộ phân tích generic đầy đủ; cách phân nhánh này là chủ ý để các trường hợp chưa hỗ trợ được báo lỗi rõ ràng thay vì ép kiểu sai.

### Khi gặp nhiều declaration annotation khác nhau

Nếu mục tiêu là “class này có `@Role` không?”, dùng `Class.getAnnotation(Role.class)` theo ngữ nghĩa `@Inherited`. Nếu mục tiêu là “cạnh extends/implements này có `@Edge` không?”, **đọc annotated hierarchy edges**. Framework nên ghi rõ chính sách khi:

- interface A được kế thừa gián tiếp qua nhiều nhánh;
- superclass và interface có annotation cùng tên nhưng ý nghĩa khác;
- một type-use annotation nằm ở outer type, type argument hoặc inner component;
- một class không khai báo `extends` tường minh nhưng có superclass `Object`;
- các annotation không được giữ đến runtime.

## 8. Những lỗi phổ biến cần tránh

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “`Base.class.getAnnotation(Edge.class)` đọc `extends @Edge Base`.” | Annotation trên **cạnh của Child** phải đọc từ `Child.class.getAnnotatedSuperclass()`. |
| “`getGenericSuperclass()` là đủ để lấy TYPE_USE annotation.” | API trả `Type`, không mang lớp annotation; dùng `getAnnotatedSuperclass()`. |
| “Annotation trên `implements` sẽ tự xuất hiện trên interface declaration.” | Đó là metadata của lần dùng interface trong class đang khai báo. |
| “`@Inherited` làm annotation từ interface truyền sang class implement.” | Sai: `@Inherited` chỉ xét superclass chain của class declaration. |
| “`@Inherited` sao chép annotation TYPE_USE xuống mọi class con.” | Sai: type-use annotation tại mỗi cạnh là metadata độc lập. |
| “`getAnnotatedInterfaces()` liệt kê mọi interface được kế thừa gián tiếp.” | Sai: chỉ direct superinterfaces được class/interface khai báo. |
| “Nếu `getAnnotatedSuperclass()` trả không null thì nhất định có annotation.” | Sai: có thể đại diện superclass không có annotation, ví dụ `Object` ngầm định. |
| “`@Target(TYPE)` đủ cho type-use trong `extends/implements`.” | Cần `TYPE_USE` cho vị trí type-use. |
| “`AnnotatedParameterizedType.getAnnotations()` gồm annotation của mọi generic argument.” | Sai: phải đọc từng `getAnnotatedActualTypeArguments()`. |
| “Có annotation trong source là Reflection thấy.” | Cần retention thích hợp (`RUNTIME`) và metadata hợp lệ. |

**Tình huống thực tế:** một dependency injection framework muốn xác định cách xử lý adapter theo đường implement `Contract<T>` có thể dùng edge annotation như metadata định tuyến. Ngược lại một validator muốn tìm class-level `@Entity` có thể chỉ cần `Class.getAnnotation(...)` (và quy tắc `@Inherited` của annotation đó). Chọn đúng vị trí metadata sẽ ngăn nhầm annotation giữa hai đối tượng và hai lớp trách nhiệm.

## 9. Bài tập thực hành

1. Thêm interface `Loggable` rồi viết `implements @Edge("api") Contract<String>, @Edge("log") Loggable`; in từng phần tử `getAnnotatedInterfaces()` để thấy thứ tự.
2. Thử `class Plain {}` và kiểm tra `Plain.class.getAnnotatedSuperclass()` khác null nhưng không có `@Edge`.
3. Thử `Object.class.getAnnotatedSuperclass()`, `Contract.class.getAnnotatedSuperclass()`, `int.class.getAnnotatedSuperclass()` và so sánh với class thường.
4. Bỏ `@Inherited` khỏi `Role` trong demo, chạy lại và giải thích vì sao `Child.class.getAnnotation(Role.class)` không nhận marker từ `Base`.
5. Giữ `@Inherited` trên `Role` nhưng xóa `@Role` của `Base`; chỉ giữ marker trên `Contract` và chứng minh `OnlyInterface`/`Child` không tự thừa hưởng annotation từ interface.
6. Thay type argument `String` bằng `List<@Edge("nested") String>` ở superclass, triển khai traversal hai tầng `AnnotatedParameterizedType`.
7. Tạo `Grandchild extends @Edge("direct-grandchild") Child`, kiểm chứng annotation của `Grandchild -> Child` độc lập với annotation của `Child -> Base`.
8. Xây hàm liệt kê các cạnh trực tiếp: tên class nguồn, kiểu đích, annotation trên cạnh và annotation trên generic arguments; phân biệt direct và transitive.
9. Với `@Retention(CLASS)` hoặc `SOURCE`, quan sát việc annotation không xuất hiện qua runtime Reflection mặc dù cú pháp source hợp lệ.

## 10. Tài liệu chính thức

- [Java SE 17 — Class.getAnnotatedSuperclass / getAnnotatedInterfaces](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): hợp đồng superclass/interface trực tiếp, null và thứ tự.
- [Java SE 17 — AnnotatedType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html): type-use annotation và kiểu nền tảng.
- [Java SE 17 — AnnotatedParameterizedType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html): generic arguments có annotation riêng.
- [Java SE 17 — @Inherited](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html): kế thừa declaration annotation theo superclass chain, không qua interface.
- [JLS Java 17 — Type Annotations `9.7.4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.4): annotation trên type use.
- [JLS Java 17 — Inheriting Annotations `9.6.4.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.3): phạm vi của `@Inherited`.

**Điểm cốt lõi:** `Class.getAnnotation` đọc **annotation khai báo trên class** (có quy tắc `@Inherited` riêng), còn `getAnnotatedSuperclass` và `getAnnotatedInterfaces` đọc **annotation tại từng lần sử dụng superclass/interface trực tiếp**. Phải phân biệt hai lớp metadata này, và đi vào `AnnotatedParameterizedType` nếu annotation nằm trên generic argument.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-use-annotations-009">Xây dựng trình duyệt cây kiểu mà vẫn giữ vị trí annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng trình duyệt cây kiểu mà vẫn giữ vị trí annotation

Trong Java Reflection, một kiểu như **List<String>** không chỉ có tên kiểu và type arguments: từng **vị trí sử dụng kiểu** (type-use) có thể mang annotation riêng. Đặc biệt trong cấu trúc nested generics, wildcards, arrays, owner types và type-variable bounds, **vị trí** annotation quan trọng không kém **giá trị** annotation. Một trình duyệt chỉ gom các annotation thành một danh sách phẳng sẽ làm mất ngữ nghĩa.

Bài học thuộc chủ đề **Annotation trên kiểu** và hướng dẫn cách xây dựng một *visitor* đi qua cây **AnnotatedType**, lưu đường dẫn cho từng nút và không làm nhầm annotation ở kiểu gốc với annotation trên kiểu con. Mục tiêu là dùng được trong trình hiển thị metadata, serializer, validator, bộ phân tích generic hoặc công cụ sinh schema.

## 1. Annotation trên declaration và annotation trên type-use

Hai loại vị trí phải được phân biệt:

- **Declaration annotation** gắn với khai báo class, field, method, constructor, parameter hoặc type parameter. Ví dụ annotation target FIELD được đọc từ Field.getDeclaredAnnotations().
- **Type-use annotation** gắn với một vị trí sử dụng kiểu, ví dụ List có annotation riêng, String bên trong List có annotation riêng, hoặc một chiều của mảng có annotation riêng. Metadata này được truy cập qua AnnotatedType và các biến thể.

Ví dụ Java minh họa:

~~~java
import java.lang.annotation.*;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Column {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Tag {
    String value();
}

class Example {
    @Column
    @Tag("list") List<@Tag("element") String> names;
}
~~~

Ở đây **@Column** gắn vào khai báo field; **@Tag("list")** thuộc nút kiểu gốc List<String>; **@Tag("element")** thuộc type argument String. Nếu kiểm tra duy nhất Field.getDeclaredAnnotations(), ta không thu được cây annotation gắn trên tất cả các vị trí generic.

**Điều kiện quan trọng:** core Reflection ở runtime chỉ thấy những annotation có RetentionPolicy.RUNTIME. Annotation với SOURCE hoặc CLASS retention có thể tồn tại ở source/class file nhưng không hiện diện qua các lời gọi Reflection runtime tương ứng. Khi cần phân tích class file sâu hơn, phải dùng công cụ bytecode khác.

## 2. Vì sao chỉ duyệt Type không đủ?

**Type** cùng các dạng **ParameterizedType**, **TypeVariable**, **WildcardType**, **GenericArrayType** cung cấp cấu trúc của kiểu. Tuy nhiên, tự duyệt Field.getGenericType() rồi tách các thành phần Type không giúp bảo toàn đầy đủ annotations gắn ở từng type-use.

Thay vì:

~~~java
Type onlyStructure = field.getGenericType();
~~~

hãy lấy root có annotation:

~~~java
AnnotatedType annotatedRoot = field.getAnnotatedType();
~~~

**AnnotatedType.getType()** vẫn trả cấu trúc Type của nút hiện tại, nhưng **AnnotatedType.getAnnotationsByType(...)** và **getDeclaredAnnotations()** đọc annotation tại **chính nút** đó. Muốn xuống type arguments, array dimensions, bounds hoặc owner, phải dùng API chuyên biệt trên các subtype.

Chẳng hạn:

~~~text
@Tag("list") List<@Tag("element") String>
                 |
field            |      annotation = list
└── field.arg[0] |      annotation = element
~~~

Không được báo rằng cả hai annotation cùng nằm trên List hoặc cùng nằm trên String. Việc phân biệt hai nút là mục đích chính của visitor.

## 3. Các dạng nút phải hỗ trợ

| Kiểu node Reflection | Cấu trúc mẫu | Lời gọi lấy con | Quy ước đường dẫn |
|---|---|---|---|
| AnnotatedParameterizedType | List<String>, Map<K,V> | getAnnotatedActualTypeArguments() | .arg[0], .arg[1] |
| AnnotatedArrayType | String[] hoặc String[][] | getAnnotatedGenericComponentType() | .component |
| AnnotatedWildcardType | ? extends Number; ? super Integer | getAnnotatedUpperBounds(), getAnnotatedLowerBounds() | .upper[0], .lower[0] |
| AnnotatedTypeVariable | T trong List<T> | getAnnotatedBounds() | .bound[0] |
| AnnotatedType có owner | Outer.Inner<String> | getAnnotatedOwnerType() | .owner |
| Nút leaf | String, Number, int | Không duyệt con | Giữ node và annotation tại node |

Ở mọi nhánh, visitor thực hiện theo thứ tự: **đọc và xuất annotation tại node hiện tại**, sau đó **đi vào các node con**. Một annotation trên node cha không tự động kế thừa xuống node con.

Với **Map<@A String, @B List<@C Integer>>**, các đường dẫn tương ứng là:

~~~text
field.arg[0]                 -> @A trên String
field.arg[1]                 -> @B trên List<Integer>
field.arg[1].arg[0]          -> @C trên Integer
~~~

Đường dẫn được dựng từ các cạnh thật trong mô hình Reflection, không phải bằng cách tìm chuỗi con trong Type.getTypeName().

## 4. Thiết kế node và đường dẫn

Trong một công cụ thực tế, bản ghi cho mỗi node tối thiểu gồm:

| Thuộc tính | Ví dụ | Tác dụng |
|---|---|---|
| path | field.arg[0].upper[0] | Giữ chính xác vị trí |
| kind | WILDCARD, PARAMETERIZED, ARRAY... | Mô tả dạng node |
| typeName | java.lang.Number | Kiểu tại node |
| annotations | Tag("upper") | Chỉ annotation tại node |
| declarationSource | Field, return, parameter... | Tránh nhầm root khác nhau |

Java 17 cho phép dùng record để biểu diễn:

~~~java
record TypeNode(
    String path,
    String kind,
    String typeName,
    java.util.List<String> annotations
) {}
~~~

Đây là một khai báo record minh họa; visitor trong phần tiếp theo in trực tiếp để giảm mã phụ trợ. Khi cần xuất JSON, vẽ cây hay lưu database, hãy thay lệnh in bằng việc tạo các TypeNode tương ứng.

**Không dùng Class.getAnnotations() trên raw class của type argument để thay thế.** Một type-use annotation trên String trong field này thuộc **vị trí String ở field**, không phải annotation declaration của class java.lang.String và cũng không phải property vĩnh viễn của đối tượng String.class.

## 5. Ví dụ Java 17+ hoàn chỉnh: visitor có bảo vệ chu trình

Tạo tệp **AnnotatedTypeTreeDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnnotatedTypeTreeDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    @interface Tag {
        String value();
    }

    static class Sample<
            @Tag("declaration") T extends @Tag("bound") Number> {

        @Tag("container")
        List<@Tag("element") String> names;

        List<@Tag("wildcard") ? extends @Tag("upper") Number> numbers;

        String @Tag("outer") [] @Tag("inner") [] matrix;

        List<@Tag("use-T") T> generic;
    }

    static String tags(AnnotatedType node) {
        List<String> values = new ArrayList<>();
        for (Tag tag : node.getAnnotationsByType(Tag.class)) {
            values.add(tag.value());
        }
        return values.isEmpty() ? "-" : String.join(",", values);
    }

    static String kind(AnnotatedType node) {
        if (node instanceof AnnotatedParameterizedType) {
            return "PARAMETERIZED";
        }
        if (node instanceof AnnotatedArrayType) {
            return "ARRAY";
        }
        if (node instanceof AnnotatedWildcardType) {
            return "WILDCARD";
        }
        if (node instanceof AnnotatedTypeVariable) {
            return "TYPE_VARIABLE";
        }
        return "CLASS";
    }

    static void walk(AnnotatedType node, String path,
                     Set<TypeVariable<?>> active) {
        System.out.println(path + " [" + kind(node)
                + "] @" + tags(node));

        AnnotatedType owner = node.getAnnotatedOwnerType();
        if (owner != null) {
            walk(owner, path + ".owner", active);
        }

        if (node instanceof AnnotatedParameterizedType p) {
            AnnotatedType[] args = p.getAnnotatedActualTypeArguments();
            for (int i = 0; i < args.length; i++) {
                walk(args[i], path + ".arg[" + i + "]", active);
            }
        } else if (node instanceof AnnotatedArrayType a) {
            walk(a.getAnnotatedGenericComponentType(),
                    path + ".component", active);
        } else if (node instanceof AnnotatedWildcardType w) {
            AnnotatedType[] upper = w.getAnnotatedUpperBounds();
            for (int i = 0; i < upper.length; i++) {
                walk(upper[i], path + ".upper[" + i + "]", active);
            }
            AnnotatedType[] lower = w.getAnnotatedLowerBounds();
            for (int i = 0; i < lower.length; i++) {
                walk(lower[i], path + ".lower[" + i + "]", active);
            }
        } else if (node instanceof AnnotatedTypeVariable tv) {
            TypeVariable<?> variable = (TypeVariable<?>) tv.getType();
            if (!active.add(variable)) {
                System.out.println(path + ".bounds [CYCLE]");
                return;
            }
            try {
                AnnotatedType[] bounds = tv.getAnnotatedBounds();
                for (int i = 0; i < bounds.length; i++) {
                    walk(bounds[i], path + ".bound[" + i + "]",
                            active);
                }
            } finally {
                active.remove(variable);
            }
        }
    }

    static void inspect(String fieldName) throws Exception {
        System.out.println("=== " + fieldName + " ===");
        AnnotatedType root =
                Sample.class.getDeclaredField(fieldName)
                        .getAnnotatedType();
        walk(root, "field", new HashSet<>());
    }

    public static void main(String[] args) throws Exception {
        inspect("names");
        inspect("numbers");
        inspect("matrix");
        inspect("generic");

        TypeVariable<?> declared =
                Sample.class.getTypeParameters()[0];
        System.out.print("T declaration @");
        Tag tag = declared.getAnnotation(Tag.class);
        System.out.println(tag == null ? "-" : tag.value());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp với JDK 17 trở lên:

~~~bash
javac AnnotatedTypeTreeDemo.java
java AnnotatedTypeTreeDemo
~~~

**Stdout dự kiến theo API chuẩn của Java 17+:**

~~~text
=== names ===
field [PARAMETERIZED] @container
field.arg[0] [CLASS] @element
=== numbers ===
field [PARAMETERIZED] @-
field.arg[0] [WILDCARD] @wildcard
field.arg[0].upper[0] [CLASS] @upper
=== matrix ===
field [ARRAY] @outer
field.component [ARRAY] @inner
field.component.component [CLASS] @-
=== generic ===
field [PARAMETERIZED] @-
field.arg[0] [TYPE_VARIABLE] @use-T
field.arg[0].bound[0] [CLASS] @bound
T declaration @declaration
~~~

Đường dẫn không dùng tên lớp triển khai nội bộ của JVM, vì visitor tự phân loại thông qua các interface Reflection. Trong ví dụ, tất cả field đều được khai báo trực tiếp trong Sample nên getDeclaredField(...) phù hợp.

### Giải thích field names

Khai báo **@Tag("container") List<@Tag("element") String> names** tạo hai vị trí riêng:

- **field** là List<String> và mang annotation container.
- **field.arg[0]** là String và mang annotation element.

Nếu chỉ gọi Field.getAnnotations(), bạn không dựng được đường đi này. Nếu chỉ nhìn GenericType, bạn biết có String nhưng mất annotation tại vị trí sử dụng.

### Giải thích field numbers

Khai báo **List<@Tag("wildcard") ? extends @Tag("upper") Number>** gồm:

- Node List, bản thân không có Tag.
- Node wildcard là đối số generic thứ nhất, mang Tag wildcard.
- Upper bound Number của wildcard, mang Tag upper.

Truy cập wildcard bằng AnnotatedWildcardType; **upper bound** lấy từ getAnnotatedUpperBounds(). Nếu dùng **? super Integer**, phải đi vào **getAnnotatedLowerBounds()** và biểu thị nhánh lower; nếu chỉ duyệt upper, bạn sẽ mất annotation của lower bound.

### Giải thích field matrix

Với **String @Tag("outer") [] @Tag("inner") []**, hai cặp ngoặc vuông thuộc hai tầng array khác nhau. Reflection biểu diễn bằng **hai AnnotatedArrayType**:

- field: outer dimension mang Tag outer.
- field.component: inner dimension mang Tag inner.
- field.component.component: component class String, trong ví dụ không có Tag.

Nếu bạn nối các annotation thành danh sách chung [outer, inner], không còn biết mỗi nhãn thuộc chiều nào. Khi xử lý mảng nhiều chiều hoặc type-use validation trên từng chiều, sự khác biệt này có ý nghĩa.

### Giải thích field generic và type parameter T

Field **List<@Tag("use-T") T> generic** có một argument là AnnotatedTypeVariable. Annotation use-T gắn trên **vị trí sử dụng T trong List**, khác với:

- **@Tag("declaration")** gắn trên **khai báo type parameter T** của Sample, đọc qua TypeVariable.getAnnotation(Tag.class).
- **@Tag("bound")** gắn trên **bound Number** của T, đọc qua AnnotatedTypeVariable.getAnnotatedBounds() hoặc TypeVariable.getAnnotatedBounds().

Ba nguồn annotation đều liên quan T, nhưng **không nằm cùng một node**. Một công cụ tổng quát có thể lưu phần declaration ở record riêng thay vì đưa vào cây field.

## 6. Tránh đệ quy vô hạn khi duyệt type variable

Một generic bound có thể tự tham chiếu:

~~~java
class Node<T extends Comparable<T>> {
    T value;
}
~~~

Nếu visitor mở rộng T, rồi Comparable<T>, rồi argument T, lại mở rộng bound Comparable<T> liên tục, nó sẽ không dừng:

~~~text
T
└── bound[0] Comparable<T>
    └── arg[0] T
        └── bound[0] Comparable<T>
            └── ...
~~~

Ví dụ chính dùng **Set<TypeVariable<?>> active** để ghi nhớ các type variable đang nằm trên nhánh duyệt hiện tại. Khi gặp lại một biến đang active, visitor in marker **[CYCLE]** thay vì mở rộng bounds vô hạn.

Kỹ thuật này có hai đặc điểm quan trọng:

1. Visitor **vẫn xuất node occurrence hiện tại** trước khi phát hiện vòng lặp ở khâu mở rộng bounds, nên annotation gắn tại occurrence đó không bị mất.
2. Dùng **finally** để xóa type variable khỏi tập active sau khi duyệt nhánh xong. Một T xuất hiện trên nhánh độc lập khác vẫn được duyệt đầy đủ; không được bỏ qua chỉ vì đã gặp T trước đó trong toàn bộ chương trình.

Một resolver phức tạp có thể dùng cache theo cặp (generic declaration, type-variable binding), node identity và giới hạn độ sâu. Nhưng phải có phương án xử lý chu trình trong cây type bounds; không được lặp không giới hạn.

## 7. Owner type và nested generic types

Nested class có thể mang type-use annotation trên **outer owner**, **inner type** và **type argument của inner**. Ví dụ minh họa:

~~~java
class Outer {
    class Inner<U> {}
}

// Đặt sau khai báo Tag có TYPE_USE target và trong một class hợp lệ:
@Tag("owner") Outer.@Tag("inner") Inner<@Tag("argument") String> ref;
~~~

Cách biểu diễn vị trí đúng:

~~~text
field             -> annotation inner, trên Inner<String>
field.owner       -> annotation owner, trên Outer
field.arg[0]      -> annotation argument, trên String
~~~

Trong Java 9+, **AnnotatedType.getAnnotatedOwnerType()** cho phép visitor truy cập owner nếu tồn tại. Tên kiểu có dấu chấm không phải là phương pháp đủ để xác định owner annotations. Không thể lấy annotation của Outer bằng cách gán nó cho Inner cho tiện.

**Không phải node nào cũng có owner**. getAnnotatedOwnerType() có thể trả null. Cấu trúc của top-level class, static nested class, inner class và parameterized owner có thể khác nhau. Visitor phải duyệt theo metadata thực tế, không tự tạo owner nhân tạo.

Với owner chứa generic arguments, nhánh owner cũng có thể là AnnotatedParameterizedType và phải được duyệt đệ quy; nếu không, annotation của type arguments ở outer type sẽ biến mất.

## 8. Các root khác ngoài Field

Để áp dụng trình duyệt cho một class toàn diện, bạn có thể gọi cùng hàm walk(...) từ nhiều nguồn:

| Nơi khai báo | API trả AnnotatedType hoặc mảng AnnotatedType |
|---|---|
| Field | Field.getAnnotatedType() |
| Method return | Method.getAnnotatedReturnType() |
| Method parameters | Method.getAnnotatedParameterTypes() |
| Constructor parameters | Constructor.getAnnotatedParameterTypes() |
| Method/constructor throws | Executable.getAnnotatedExceptionTypes() |
| Superclass | Class.getAnnotatedSuperclass() |
| Implemented interfaces | Class.getAnnotatedInterfaces() |
| Type parameter bounds | TypeVariable.getAnnotatedBounds() |

Chẳng hạn:

~~~java
class Service {
    public java.util.List<@Tag("returned") String> read(
            java.util.List<@Tag("input") String> values) {
        return values;
    }
}
~~~

Đây là ví dụ class minh họa, cần Tag với TYPE_USE và RUNTIME retention. Với:

~~~java
Method m = Service.class.getMethod("read", java.util.List.class);
AnnotatedType out = m.getAnnotatedReturnType();
AnnotatedType in = m.getAnnotatedParameterTypes()[0];
~~~

Bạn có thể gọi **walk(out, "return", active)** và **walk(in, "parameter[0]", active)**. Annotation returned nằm ở **return.arg[0]**, còn input nằm ở **parameter[0].arg[0]**. Dù cả hai đều là List<String>, chúng là hai **vị trí khai báo khác nhau**.

Cả hai đoạn là phần minh họa, không nằm trong file chương trình chạy độc lập ở mục 5; khi đưa vào chương trình phải thêm import Method/AnnotatedType và xử lý NoSuchMethodException.

### Lưu ý về annotation trên constructor

Constructor không có return value theo cú pháp như một method, nhưng Reflection có **Constructor.getAnnotatedReturnType()** phục vụ metadata được Java API định nghĩa cho constructor. Đừng tự gán nó thành return value của một method thông thường. Các tham số và exception types của constructor vẫn là những root thông dụng hơn khi phân tích annotation trên kiểu.

## 9. Dữ liệu đầu ra nên là cây, không phải tập annotation phẳng

Một công cụ sinh schema có thể lưu:

~~~json
{
  "path": "field.arg[0].upper[0]",
  "kind": "CLASS",
  "typeName": "java.lang.Number",
  "annotations": [
    {"name": "Tag", "value": "upper"}
  ]
}
~~~

Đây là minh họa một node JSON; không phải một object có thể xây đầy đủ chỉ bằng gọi Class.getAnnotations(). Annotation và value phải lấy ở node AnnotatedType tại path tương ứng.

Nếu cần hiển thị trực quan, có thể hiển thị cây bằng thụt dòng; tuy nhiên lưu cả **path ổn định** giúp unit test, so sánh schema và trỏ lỗi chính xác. Với nhiều root, thêm source identifier như field name, method signature hoặc parameter index.

Để export sang JSON một cách thực tế, nên định nghĩa cấu trúc bản ghi rõ ràng, ví dụ:

~~~java
record TypeUseLocation(
        String source,
        String path,
        String kind,
        String typeName,
        java.util.List<String> annotationNames) {}
~~~

Không chỉ serialize một List<Annotation> vì như thế mọi vị trí con bị mất.

## 10. Repeatable annotations và multiple annotations

Java cho phép annotation có target TYPE_USE được khai báo **@Repeatable**, trong trường hợp ấy một node có thể chứa nhiều annotation cùng loại. Visitor có thể dùng **getAnnotationsByType(Tag.class)** khi muốn các instance logic của một annotation cụ thể, kể cả trường hợp được nhóm trong repeatable container.

Nếu viết trình duyệt tổng quát không biết trước Tag là gì, hãy đọc **getDeclaredAnnotations()** của từng AnnotatedType, rồi tùy nhu cầu xử lý/giải nén repeatable containers theo metadata chuẩn. Không nên gộp annotations có cùng tên tại hai **node khác nhau**; việc có cùng loại annotation không biến hai vị trí thành một.

Nếu công cụ cần output ổn định giữa các lần chạy, sắp xếp annotations theo tên đầy đủ và giá trị attributes thay vì dựa vào thứ tự ngẫu nhiên. Thứ tự **nhánh cây** phải được quyết định bằng argument index, array component, bound index và owner relation.

## 11. Các giới hạn quan trọng

**Reflection metadata không thay validation.** Annotation có tên @NotNull hoặc @Tag("element") không tự kiểm tra object trong runtime list. Framework cần logic validation của mình, đặc biệt với nested generics.

**Type erasure vẫn tồn tại.** Một AnnotatedTypeVariable T không biết ngay concrete type parameter của object thực tế. Nếu class Derived extends Base<String>, muốn thay T bằng String trong field khai báo ở Base phải phân giải generic type bindings theo hierarchy, đồng thời bảo toàn các annotation theo đúng các vị trí tương ứng. Đây là bước khác với việc chỉ duyệt AnnotatedType.

**Không giả định mọi Type là Class.** AnnotatedParameterizedType có Type là ParameterizedType; wildcard, type variable và generic array đều có cấu trúc khác nhau. Cast mù node.getType() thành Class<?> có thể gây ClassCastException.

**Ranh giới module và access.** Truy xuất metadata không tự đồng nghĩa được phép đọc private field value hay gọi private method. Nếu công cụ đi xa hơn việc duyệt kiểu, Field.get(), Method.invoke(), Constructor.newInstance() và setAccessible() có các kiểm tra access/JPMS riêng.

**Annotation không có RUNTIME retention** không hiện qua core Reflection tương ứng. Kết luận “không thấy qua visitor” chỉ có nghĩa “không có metadata runtime-visible tại vị trí ấy”, không chứng minh source chưa từng có annotation.

**Array dimensions** có annotation riêng, không được nối chúng lên component type.

**Wildcards** có thể có upper bound hoặc lower bound; phân biệt .upper và .lower.

**TypeVariable declaration** khác occurrence: annotation TYPE_PARAMETER trên khai báo và TYPE_USE ở mỗi vị trí sử dụng T không cùng một nguồn.

**Đường dẫn phải giữ quan hệ từ root**. Nếu hai field cùng chứa annotation @Tag("element") trên String, các annotation ấy thuộc hai vị trí khai báo khác nhau và cần source identifier khác nhau.

## 12. Các lỗi thường gặp

1. **Duyệt duy nhất Type.getTypeName()** rồi cố suy ra annotation từ chữ: không có dữ liệu vị trí type-use.
2. **Chỉ đọc Field.getAnnotations()**: bỏ sót annotation trong generic arguments, wildcard bounds hoặc array dimensions.
3. **Gộp annotation của List và String** thành một danh sách: sai node sở hữu annotation.
4. **Không duyệt AnnotatedParameterizedType**: bỏ qua annotation ở nested generic arguments.
5. **Không duyệt AnnotatedArrayType theo từng tầng**: mất annotation ở từng chiều array.
6. **Duyệt wildcard nhưng bỏ qua upper/lower bounds**: mất annotation hoặc nhầm phương sai.
7. **Coi TypeVariable.getBounds() là đủ**: lấy Type thuần có thể mất annotation trên bound; dùng getAnnotatedBounds().
8. **Coi annotation declaration của T là annotation trên mọi use-site T**: hai loại metadata khác nhau.
9. **Không có cycle detection cho T extends Comparable<T>**: visitor có thể chạy vô hạn.
10. **Bỏ qua getAnnotatedOwnerType()**: annotation trên owner của nested class bị mất.
11. **Bỏ qua RetentionPolicy.RUNTIME**: không hiểu vì sao Reflection không thấy annotation ở source.
12. **Tạo JSON chỉ chứa tên annotations**: mất vị trí và không thể tái tạo semantic type-use.
13. **Dùng class runtime của phần tử collection để tìm type-use annotations**: dữ liệu runtime không phải metadata kiểu khai báo.
14. **Xóa annotations khi thay generic type variables**: có thể làm mất thông tin schema/validation.
15. **Tin rằng thấy annotation nghĩa là runtime đã tự validate**: annotation chỉ là mô tả; validation là cơ chế riêng.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Với Map<@A String, List<@B Integer>>, nếu root là field thì @B nằm ở đâu?

**Đáp án:** Ở **field.arg[1].arg[0]**. Trước tiên đi vào type argument thứ hai của Map, sau đó type argument thứ nhất của List.

**Câu 2.** Với List<@A ? extends @B Number>, @A và @B có cùng một node không?

**Đáp án:** Không. @A nằm ở **field.arg[0]** (wildcard), @B ở **field.arg[0].upper[0]** (bound Number).

**Câu 3.** Với String @A [] @B [], cần duyệt những nhánh nào?

**Đáp án:** Root là AnnotatedArrayType cho chiều ngoài @A; component là AnnotatedArrayType cho chiều trong @B; component tiếp theo là String.

**Câu 4.** Với class Box<@A T extends @B Number> { List<@C T> items; }, ba annotation thuộc đâu?

**Đáp án:** @A trên type-parameter declaration T; @B trên bound Number của T; @C trên vị trí dùng T trong List. Không được gộp.

**Câu 5.** Vì sao visitor cần Set<TypeVariable<?>> active?

**Đáp án:** Để chặn việc mở rộng bounds vô hạn trong cấu trúc tự tham chiếu như T extends Comparable<T>, nhưng vẫn cho phép cùng T xuất hiện hợp lệ ở nhánh khác.

**Câu 6.** Nếu một annotation có retention CLASS, visitor runtime có đảm bảo thấy nó không?

**Đáp án:** Không. Core Reflection chỉ trình bày runtime-visible annotations; phải dùng công cụ đọc class file nếu cần dữ liệu không runtime-visible.

**Câu 7.** Muốn đọc annotation trên method return và trên argument của method parameter thì dùng API nào?

**Đáp án:** Method.getAnnotatedReturnType() và Method.getAnnotatedParameterTypes()[i], sau đó duyệt mỗi root với đường dẫn khác nhau để không trộn vị trí.

## Tài liệu chuẩn

- Java SE API: java.lang.reflect.AnnotatedType, AnnotatedParameterizedType, AnnotatedArrayType, AnnotatedWildcardType, AnnotatedTypeVariable.
- Java SE API: Field.getAnnotatedType(), Method.getAnnotatedReturnType(), Executable.getAnnotatedParameterTypes(), Executable.getAnnotatedExceptionTypes().
- Java SE API: TypeVariable.getAnnotatedBounds(), AnnotatedElement.getAnnotationsByType(), Class.getAnnotatedSuperclass(), Class.getAnnotatedInterfaces().
- Java Language Specification: type-use positions, generic type parameters, annotation targets, annotation retention và recursive bounds.
- Java Virtual Machine Specification: runtime-visible type annotations và metadata class file, khi cần hiểu cơ chế lưu vị trí.

**Ghi nhớ:** trình duyệt cây kiểu phải lấy **AnnotatedType làm điểm khởi đầu**, ghi **annotation ngay tại node hiện tại**, đi tiếp theo các cạnh **arg / component / upper / lower / bound / owner**, đồng thời đánh dấu nguồn declaration và bảo vệ khỏi chu trình. Chỉ như vậy annotation mới được bảo toàn đúng **vị trí** thay vì biến thành tập tên không còn ngữ cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)
