<a id="back-to-top"></a>

# Annotation runtime

## Menu
- [AnnotatedElement và các đối tượng hỗ trợ annotation](#runtime-annotations-001)
- [Retention SOURCE, CLASS, RUNTIME và giới hạn quan sát](#runtime-annotations-002)
- [getAnnotation, getDeclaredAnnotation và isAnnotationPresent](#runtime-annotations-003)
- [getAnnotations khác getDeclaredAnnotations](#runtime-annotations-004)
- [@Inherited chỉ áp dụng theo các quy tắc kế thừa annotation của lớp](#runtime-annotations-005)
- [Annotation trên interface hoặc phương thức không tự truyền như annotation lớp](#runtime-annotations-006)
- [Repeatable annotation và container annotation](#runtime-annotations-007)
- [getAnnotationsByType khác getDeclaredAnnotationsByType](#runtime-annotations-008)
- [Đọc giá trị phần tử annotation qua phương thức của annotation type](#runtime-annotations-009)
- [Method.getDefaultValue cho phần tử có giá trị mặc định](#runtime-annotations-010)
- [equals, hashCode và annotationType của annotation](#runtime-annotations-011)
- [Annotation không tự thực thi validation hay hành vi nghiệp vụ](#runtime-annotations-012)

## <a id="runtime-annotations-001">AnnotatedElement và các đối tượng hỗ trợ annotation</a>

<details>
<summary>Xem chi tiết</summary>

# AnnotatedElement và các đối tượng hỗ trợ annotation trong Java Reflection

Trong Java, annotation là metadata gắn với khai báo hoặc với vị trí sử dụng một kiểu. Reflection có thể đọc các annotation được giữ ở runtime thông qua giao diện **`java.lang.reflect.AnnotatedElement`**. Thay vì mỗi loại đối tượng Reflection có một cách đọc hoàn toàn khác nhau, giao diện này thống nhất các thao tác như `getAnnotation(...)`, `getAnnotations()`, `getDeclaredAnnotations()` và `getAnnotationsByType(...)`.

Bài học trả lời ba câu hỏi: **đối tượng nào triển khai `AnnotatedElement`**, **cách tra cứu annotation chính xác ở runtime**, và **vì sao kết quả thay đổi theo retention, inheritance, repeatable annotation và vị trí annotation**.

## 1. AnnotatedElement là gì?

`AnnotatedElement` là interface thuộc package `java.lang.reflect`. Nó mô tả một phần tử có thể mang annotation và cung cấp API đọc metadata annotation bằng Reflection.

Một số phương thức quan trọng:

```java
<T extends Annotation> T getAnnotation(Class<T> annotationType);
Annotation[] getAnnotations();
<T extends Annotation> T getDeclaredAnnotation(Class<T> annotationType);
Annotation[] getDeclaredAnnotations();
<T extends Annotation> T[] getAnnotationsByType(Class<T> annotationType);
<T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationType);
boolean isAnnotationPresent(Class<? extends Annotation> annotationType);
```

Đây là các chữ ký **minh họa rút gọn** để học API, không phải đoạn source độc lập để biên dịch. `Annotation` là interface `java.lang.annotation.Annotation`.

Điểm quan trọng:

- Mọi phương thức đọc annotation trả về dữ liệu **có thể quan sát ở runtime**; `@Retention(RetentionPolicy.SOURCE)` và `CLASS` không bảo đảm có thể đọc bằng Reflection runtime.
- `getAnnotation(T.class)` trả **một annotation** thuộc kiểu `T` hoặc `null` nếu kiểu đó không “present” theo định nghĩa API.
- `isAnnotationPresent(T.class)` tương đương về mặt kết quả với `getAnnotation(T.class) != null`.
- `getAnnotations()` trả mảng các annotation “present”. `getDeclaredAnnotations()` chỉ lấy annotation **trực tiếp hiện diện** trên chính phần tử.
- `getAnnotationsByType(T.class)` và `getDeclaredAnnotationsByType(T.class)` hỗ trợ đặc biệt cho **repeatable annotation**: chúng có thể mở container annotation để lấy nhiều phần tử `T`.
- Các mảng annotation trả về có thể được người gọi sửa mà không thay đổi kết quả trả về từ những lần tra cứu khác; đối tượng annotation là immutable và serializable theo hợp đồng API.

Khái niệm “present”, “directly present”, “indirectly present” và “associated” có ý nghĩa kỹ thuật, đặc biệt khi annotation được phép kế thừa hoặc lặp lại. Vì vậy không được coi tất cả các phương thức trên là từ đồng nghĩa.

## 2. Những đối tượng Reflection nào hỗ trợ AnnotatedElement?

Không chỉ `Class<?>`, nhiều đối tượng metadata khác cũng triển khai `AnnotatedElement`:

| Loại đối tượng | Cách lấy ví dụ | Annotation đọc được ở đâu? |
| --- | --- | --- |
| `Class<?>` | `Book.class` | Khai báo class, interface, enum, annotation type hoặc record |
| `Method` | `Book.class.getDeclaredMethod("read")` | Khai báo method |
| `Constructor<?>` | `Book.class.getDeclaredConstructor()` | Khai báo constructor |
| `Field` | `Book.class.getDeclaredField("title")` | Khai báo field |
| `Parameter` | `method.getParameters()[0]` | Khai báo formal parameter |
| `Package` | `Book.class.getPackage()` | Annotation đặt trên package, thường trong `package-info.java` |
| `Module` | `Book.class.getModule()` | Annotation áp dụng cho module declaration |
| `RecordComponent` | `Person.class.getRecordComponents()[0]` | Annotation của record component (Java 16+) |
| `AnnotatedType` và các subtype | `method.getAnnotatedReturnType()` | Annotation ở **vị trí sử dụng kiểu** (type-use) |

`Method` và `Constructor<?>` cùng kế thừa `Executable`; các đối tượng thuộc nhóm này còn có `getParameters()`, `getParameterAnnotations()`, `getAnnotatedParameterTypes()`, `getAnnotatedExceptionTypes()` và API metadata chữ ký khác. `Field` có `getAnnotatedType()` để lấy type-use annotation trên kiểu của field.

Một điểm phân biệt quan trọng: **annotation trên khai báo method** không đồng nhất với **annotation trên kiểu trả về của method**. Cả `Method` lẫn `AnnotatedType` đều là `AnnotatedElement`, nhưng mỗi đối tượng đại diện cho **một vị trí khác nhau** trong source/class file.

Ví dụ:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface NonBlank {}

public @NonBlank String getName() {
    return "Ada";
}
```

Đây là đoạn minh họa cần import `java.lang.annotation.*` và đặt trong một class hợp lệ. `@NonBlank` gắn với **việc dùng kiểu trả về `String`**. Muốn đọc, truy cập:

```java
Method m = SomeClass.class.getDeclaredMethod("getName");
boolean found =
        m.getAnnotatedReturnType().isAnnotationPresent(NonBlank.class);
```

Đây là đoạn minh họa; `SomeClass` phải được thay bằng class chứa `getName`, xử lý checked exception và import `Method`. Nếu chỉ gọi `m.isAnnotationPresent(NonBlank.class)`, bạn đang hỏi về **method declaration**, không phải return type-use annotation.

## 3. Retention và Target quyết định annotation có thể được đọc ở đâu

Một annotation type thường được khai báo với các **meta-annotation**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Audit {
    String value();
}
```

Đây là khai báo annotation có thể đặt trong source hợp lệ. `@Target` xác định vị trí cú pháp được phép gắn; `@Retention` xác định mức giữ lại metadata.

| RetentionPolicy | Có trong source | Có trong class file | Reflection runtime tiêu chuẩn đọc được? |
| --- | --- | --- | --- |
| `SOURCE` | Có | Không cần được lưu | Không |
| `CLASS` | Có | Được lưu trong class file | Không phải runtime-visible qua API chuẩn |
| `RUNTIME` | Có | Được lưu | Có, trên đối tượng/phần tử tương ứng |

Nếu **không khai báo `@Retention`**, retention mặc định của annotation type là `CLASS`, không phải `RUNTIME`.

Một số `ElementType` thường gặp: `TYPE`, `METHOD`, `FIELD`, `CONSTRUCTOR`, `PARAMETER`, `PACKAGE`, `MODULE`, `RECORD_COMPONENT`, `TYPE_USE`, `TYPE_PARAMETER` và `ANNOTATION_TYPE`.

Phân biệt `TYPE` (annotation của khai báo kiểu), `TYPE_USE` (annotation của vị trí sử dụng kiểu) và `TYPE_PARAMETER` (annotation của khai báo type parameter như `<@X T>`). Một annotation type có thể khai báo nhiều target. Trong nhiều ngữ cảnh, một annotation `TYPE_USE` xuất hiện cạnh khai báo có thể còn được trình biên dịch xử lý theo quy tắc target liên quan; do đó khi cần quan sát chuẩn xác, hãy kiểm tra đúng đối tượng `AnnotatedElement`, không suy luận chỉ dựa vào chỗ chữ `@` xuất hiện trên dòng.

## 4. Chương trình Java 17+ hoàn chỉnh: Class, Method, Field, Parameter và AnnotatedType

Tạo tệp **`AnnotatedElementDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class AnnotatedElementDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD,
            ElementType.FIELD, ElementType.PARAMETER})
    @interface Label {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Role {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(Tags.class)
    @interface Tag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Tags {
        Tag[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface NonBlank {
    }

    @Label("base")
    @Role("reader")
    @Tag("red")
    @Tag("blue")
    static class Base {
        @Label("field")
        public String id;

        @Label("method")
        public @NonBlank String read(@Label("argument") String input) {
            return input;
        }
    }

    static class Child extends Base {
    }

    public static void main(String[] args) throws NoSuchMethodException,
            NoSuchFieldException {
        AnnotatedElement element = Base.class;
        System.out.println("base label: "
                + element.getAnnotation(Label.class).value());

        System.out.println("child role: "
                + Child.class.getAnnotation(Role.class).value());
        System.out.println("child declared role: "
                + (Child.class.getDeclaredAnnotation(Role.class) != null));
        System.out.println("child label: "
                + (Child.class.getAnnotation(Label.class) != null));

        Tag[] tags = Base.class.getAnnotationsByType(Tag.class);
        System.out.println("expanded tags: " + tags.length);
        System.out.println("single tag lookup: "
                + (Base.class.getAnnotation(Tag.class) != null));
        System.out.println("container present: "
                + (Base.class.getDeclaredAnnotation(Tags.class) != null));

        Method method = Base.class.getDeclaredMethod("read", String.class);
        Field field = Base.class.getDeclaredField("id");
        Parameter parameter = method.getParameters()[0];

        System.out.println("method label: "
                + method.getAnnotation(Label.class).value());
        System.out.println("field label: "
                + field.getAnnotation(Label.class).value());
        System.out.println("parameter label: "
                + parameter.getAnnotation(Label.class).value());

        System.out.println("return type-use annotation: "
                + method.getAnnotatedReturnType()
                        .isAnnotationPresent(NonBlank.class));
        System.out.println("method declaration has NonBlank: "
                + method.isAnnotationPresent(NonBlank.class));

        System.out.println("label retention: "
                + Label.class.getAnnotation(Retention.class).value());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac AnnotatedElementDemo.java
java AnnotatedElementDemo
```

Stdout dự kiến:

```text
base label: base
child role: reader
child declared role: false
child label: false
expanded tags: 2
single tag lookup: false
container present: true
method label: method
field label: field
parameter label: argument
return type-use annotation: true
method declaration has NonBlank: false
label retention: RUNTIME
```

Giải thích:

1. `Base.class` có `@Label("base")` nên có thể được gán cho biến kiểu `AnnotatedElement` và gọi `getAnnotation(Label.class)`.
2. `Role` có `@Inherited` và được gắn trên `Base`. `Child.class.getAnnotation(Role.class)` nhận metadata role từ superclass; `Child.class.getDeclaredAnnotation(Role.class)` trả `null` vì `Child` không trực tiếp khai báo nó.
3. `Label` không có `@Inherited`. Vì vậy `Child.class.getAnnotation(Label.class)` là `null` dù `Base` mang nhãn đó.
4. Hai annotation `@Tag` có thể lặp. Compiler lưu chúng qua container `@Tags`; `getAnnotationsByType(Tag.class)` mở container được hai phần tử, trong khi `getAnnotation(Tag.class)` không tự mở container theo cùng cách.
5. `Method`, `Field` và `Parameter` đều là `AnnotatedElement`. Mỗi đối tượng cho annotation trên đúng khai báo mà nó đại diện.
6. `getAnnotatedReturnType()` trả đối tượng `AnnotatedType` có thể mang `@NonBlank`, còn chính khai báo `Method` không mang annotation declaration `NonBlank`.
7. Annotation type `Label.class` cũng là một `Class`, nên Reflection có thể hỏi meta-annotation `@Retention` của chính annotation type; kết quả là enum `RUNTIME`.

Chương trình dùng `getParameters()` để lấy đối tượng `Parameter`. **Không cần tùy chọn `javac -parameters` chỉ để đọc annotation parameter**; tùy chọn đó chủ yếu giúp lưu tên parameter nguồn để `Parameter.getName()` có thể hiển thị đúng tên khai báo thay vì tên tổng hợp.

## 5. Phân biệt getAnnotation và getDeclaredAnnotation

`getAnnotation(T.class)` và `getDeclaredAnnotation(T.class)` khác nhau khi có **annotation được kế thừa trên class**:

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Category {
    String value();
}

@Category("base")
class BaseType {
}

class DerivedType extends BaseType {
}
```

Đây là ví dụ khai báo cần import `java.lang.annotation.*`, không chứa `main`.

Kết quả:

```java
Category inherited = DerivedType.class.getAnnotation(Category.class);
Category direct = DerivedType.class.getDeclaredAnnotation(Category.class);

System.out.println(inherited.value()); // base
System.out.println(direct == null);      // true
```

Mã này chỉ chạy khi đặt trong phương thức có thể truy cập các type đã khai báo. `@Inherited` **không** có nghĩa method, field hoặc interface luôn truyền annotation cho phần tử tương ứng ở class con:

- `@Inherited` chỉ ảnh hưởng cơ chế inheritance annotation **khi phần tử được kiểm tra là class**, theo superclass chain.
- Annotation trên interface không được class implementing “thừa kế” theo `@Inherited`.
- Một method override không tự nhận annotation của method bị override. Nếu công cụ cần fallback lên method lớp cha, phải tự thiết kế bước tra cứu theo chữ ký.
- Field che khuất field khác cũng không được tự gán annotation của field lớp cha.

Nếu một class con có annotation cùng kiểu được khai báo trực tiếp, annotation đó thắng đường inheritance đối với kiểu annotation đó; không cộng gộp tùy ý các khai báo trực tiếp và inherited trong API tra cứu một annotation.

## 6. getAnnotations / getDeclaredAnnotations: tập nào được trả?

`getAnnotations()` trả các annotation “present”; `getDeclaredAnnotations()` chỉ trả những annotation “directly present”. Hai mảng có thể khác nhau khi một class kế thừa annotation được đánh dấu `@Inherited`.

Trong chương trình ở mục 4, `Child.class.getAnnotations()` nhìn thấy `Role` được kế thừa; `Child.class.getDeclaredAnnotations()` không chứa `Role`.

Nếu một annotation type là repeatable, các bản xuất hiện lặp nhiều lần có thể được mã hóa bằng một **container annotation**; khi đó `getAnnotations()` hoặc `getDeclaredAnnotations()` có thể cho bạn **container** chứ không phải từng annotation thành phần. Vì thế khi muốn đọc tất cả `@Tag`, dùng `getAnnotationsByType(Tag.class)` thay vì tự ép mảng `getAnnotations()` sang `Tag[]`.

Không dựa vào thứ tự mảng `getAnnotations()` làm phần cốt lõi của logic chung. Nếu xuất báo cáo hoặc tạo UI, hãy sắp xếp/định danh theo annotation type và thuộc tính có ý nghĩa thay vì phụ thuộc thứ tự tình cờ.

## 7. Repeatable annotation và hai biến thể ByType

Một annotation được lặp lại khi khai báo `@Repeatable(Container.class)` và có container hợp lệ:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Flags.class)
@interface Flag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Flags {
    Flag[] value();
}
```

Đây là snippet độc lập về khai báo annotation type, cần import `java.lang.annotation.*`. Ví dụ sử dụng:

```java
@Flag("fast")
@Flag("verified")
class Task {
}
```

Phân biệt:

| API | Ý nghĩa |
| --- | --- |
| `getAnnotation(Flag.class)` | Tìm một `Flag` “present”; không tự mở container để trả hai `Flag` |
| `getDeclaredAnnotation(Flag.class)` | Tìm một `Flag` trực tiếp hiện diện; không tự mở container |
| `getAnnotationsByType(Flag.class)` | Lấy các `Flag` “associated”, có mở container và có áp dụng inheritance nếu điều kiện thỏa |
| `getDeclaredAnnotationsByType(Flag.class)` | Lấy `Flag` trực tiếp hoặc gián tiếp qua container **trên chính phần tử**, không tìm superclass |

`Task.class.getAnnotationsByType(Flag.class)` và `Task.class.getDeclaredAnnotationsByType(Flag.class)` đều cho hai phần tử trong ví dụ vì cả hai được khai báo trên chính `Task`.

Nếu muốn repeatable annotation **cũng kế thừa** từ superclass, phải thiết kế tương thích `@Inherited` cho annotation thành phần và container theo yêu cầu Java, rồi xét quy tắc “associated”. Đừng giả định đơn giản rằng khi class con có thêm annotation cùng type thì kết quả ByType tự ghép toàn bộ các annotation của superclass: annotation trực tiếp hoặc gián tiếp tại lớp con có thể **che kết quả từ superclass** đối với cùng kiểu annotation.

## 8. Annotation trên declaration khác annotation trên type use

Đây là điểm dễ nhầm nhất vì cả hai được truy cập qua `AnnotatedElement`.

### Annotation khai báo

Một annotation có target `METHOD`:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Logged {
}
```

gắn với khai báo method:

```java
@Logged
public String find() {
    return "found";
}
```

Đây là các snippet minh họa cần đặt trong ngữ cảnh class thích hợp. Để đọc, lấy `Method` rồi gọi `method.isAnnotationPresent(Logged.class)`.

### Annotation ở vị trí sử dụng kiểu

Một annotation có target `TYPE_USE`:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface ImportantType {
}
```

có thể gắn với kiểu trả về, kiểu field, kiểu của parameter, type argument trong generic, array component, wildcard, superclass/interface use tùy cú pháp hợp lệ.

| Vị trí | API phù hợp |
| --- | --- |
| Kiểu trả về method | `Method.getAnnotatedReturnType()` |
| Kiểu của field | `Field.getAnnotatedType()` |
| Kiểu formal parameter | `Executable.getAnnotatedParameterTypes()` |
| Kiểu throws được khai báo | `Executable.getAnnotatedExceptionTypes()` |
| Kiểu superclass | `Class.getAnnotatedSuperclass()` |
| Các kiểu interface | `Class.getAnnotatedInterfaces()` |
| Kiểu parameterized và type arguments | `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` |
| Kiểu array có annotation | `AnnotatedArrayType.getAnnotatedGenericComponentType()` |
| Wildcard bounds mang annotation | `AnnotatedWildcardType.getAnnotatedLowerBounds()` / `getAnnotatedUpperBounds()` |

Ví dụ `List<@ImportantType String>` không có nghĩa toàn bộ khai báo `Field` được đánh dấu `@ImportantType`. Để tìm annotation trên **type argument `String`**, cần đi từ `field.getAnnotatedType()`, kiểm tra `AnnotatedParameterizedType`, rồi đọc phần tử `getAnnotatedActualTypeArguments()[0]`. Truy cập đơn thuần `field.getAnnotation(ImportantType.class)` là đang hỏi sai vị trí.

Sự khác biệt còn xuất hiện ở type parameter declaration như `class Box<@Tag T>` nếu `Tag` cho phép `TYPE_PARAMETER`. Khi đó có thể đọc annotation từ `TypeVariable<?>` hoặc vị trí type-use phù hợp theo metadata; không lấy bằng `Box.class.getAnnotation(Tag.class)` và mong đó là annotation của class.

## 9. Package, Module, RecordComponent và annotations ở các vị trí khác

### Package

Package annotation thường được khai báo trong tệp `package-info.java`:

```java
@com.example.annotations.PublicPackage
package com.example.domain;
```

Đây là ví dụ **mô tả cấu trúc**; annotation type phải tồn tại, có `@Target(PACKAGE)` và `@Retention(RUNTIME)` để quan sát runtime qua `Package`. Trong chương trình có class thuộc package:

```java
Package p = SomeDomainClass.class.getPackage();
boolean present = p.isAnnotationPresent(PublicPackage.class);
```

Đây là snippet, không phải chương trình độc lập. `getPackage()` có thể cần xử lý null/điều kiện class loader khi viết công cụ chung, và không nên tìm annotation package bằng cách lấy annotation trên một class bất kỳ trong package.

### Module

`java.lang.Module` triển khai `AnnotatedElement`. Các annotation với target `MODULE` có thể đặt trên module declaration trong `module-info.java` (tuân theo cú pháp). Ví dụ `SomeClass.class.getModule().getAnnotations()` đọc annotation của module, **không phải** của class.

### Record component

`RecordComponent` (Java 16+) là đối tượng Reflection từ `Class.getRecordComponents()`. Nó đại diện cho một **thành phần record**, khác với backing field, accessor method hoặc constructor parameter dù compiler tạo các thành phần liên quan. Nếu muốn xem annotation trên record component, hãy chọn đúng `RecordComponent` và xác định `@Target(RECORD_COMPONENT)`. Java cũng có các quy tắc lan truyền annotation hợp lệ trên các phần tử được suy ra từ record component; công cụ không nên mặc định rằng kết quả khi đọc field, accessor, constructor parameter và record component sẽ luôn giống nhau.

## 10. Các đối tượng annotation và thuộc tính bên trong

Khi có được một annotation:

```java
Label label = Base.class.getAnnotation(Label.class);
String value = label.value();
Class<? extends Annotation> annotationType = label.annotationType();
```

Đây là snippet dùng `Label`/`Base` trong ví dụ mục 4 và import `java.lang.annotation.Annotation`. Annotation instance hiện thực giao diện annotation tương ứng, vì vậy các phần tử như `value()` được gọi như method đọc thuộc tính.

API `Annotation` còn có `annotationType()`, `equals(...)`, `hashCode()` và `toString()`. Không nên dựa vào tên runtime class thực thi annotation; implementation có thể dùng proxy hoặc chi tiết nội bộ JDK. Hãy dựa vào annotation interface và các giá trị attribute được tài liệu hóa.

Một annotation attribute có thể là primitive, String, Class, enum, annotation hoặc mảng của các kiểu hợp lệ. Nếu dùng class file hoặc thư viện thay đổi không đồng bộ, đọc attribute có thể gặp lỗi metadata như `TypeNotPresentException`, `EnumConstantNotPresentException`, `AnnotationTypeMismatchException`, `IncompleteAnnotationException` hoặc `AnnotationFormatError` tùy nguyên nhân. Công cụ xử lý plugin/class file do bên khác cung cấp nên có đường chẩn đoán thích hợp, không coi mọi annotation luôn có cấu trúc hoàn hảo.

## 11. Quy trình khám phá annotation đúng mục tiêu

Khi phát triển framework/validator/documentation generator:

1. **Chọn phần tử:** class, method, constructor, field, parameter, package, module, record component hoặc type-use. Đây là bước quan trọng nhất.
2. **Xác minh retention:** annotation type bạn muốn đọc phải có `@Retention(RUNTIME)`; nếu metadata không runtime-visible, Reflection sẽ không thấy như mong đợi.
3. **Chọn phạm vi:** `getDeclaredAnnotation(s)` nếu chỉ xét trực tiếp; `getAnnotation(s)` nếu muốn semantics “present” và chấp nhận inheritance trên class khi áp dụng.
4. **Chọn số lượng:** với annotation repeatable, dùng `getAnnotationsByType` hoặc `getDeclaredAnnotationsByType` để đọc được các phần tử trong container.
5. **Kiểm tra khai báo hay type-use:** method annotation nằm ở `Method`, còn return type annotation ở `Method.getAnnotatedReturnType()`.
6. **Xét cấu trúc kế thừa:** override method không tự kế thừa annotation method của cha; hãy viết chính sách quét hierarchy nếu framework yêu cầu.
7. **Phân biệt metadata với quyền thực thi:** đọc annotation không tự cấp quyền gọi private method, thay đổi field, hay vượt quyền JPMS.
8. **Đọc attribute an toàn:** chú ý annotation có thể thiếu ở runtime, chỉ có mặt ở vị trí khác hoặc cấu trúc class file không tương thích.
9. **Kiểm thử với nhiều kiểu phần tử:** ít nhất thử annotation trực tiếp, annotation không kế thừa, repeatable container, method/parameter và type-use.

## 12. Những lỗi phổ biến

| Ngộ nhận | Cách hiểu đúng |
| --- | --- |
| Mọi annotation ở source đều đọc được bằng Reflection | Chỉ annotation được giữ ở runtime và tại vị trí phù hợp mới nhìn thấy qua API |
| `@Retention` mặc định là RUNTIME | Mặc định là CLASS |
| `Class<?>` là kiểu duy nhất hỗ trợ `AnnotatedElement` | `Method`, `Field`, `Constructor`, `Parameter`, `Package`, `Module`, `RecordComponent`, `AnnotatedType`… cũng hỗ trợ |
| `getAnnotations()` và `getDeclaredAnnotations()` luôn giống nhau | Class có thể kế thừa annotation `@Inherited` mà không khai báo trực tiếp |
| `@Inherited` có hiệu lực cho method override | Chỉ áp dụng cho quy tắc inheritance annotation trên class qua superclass |
| `getAnnotation(Tag.class)` trả hết repeatable tags | Dùng `getAnnotationsByType(Tag.class)` để mở container |
| Lớp con tự động gộp repeatable annotations của cha | Quy tắc “associated” không đơn giản là nối mọi cấp; annotation tại class con có thể che kết quả cha |
| Annotation của kiểu trả về nằm trên Method declaration | Phải đọc `method.getAnnotatedReturnType()` |
| Annotation của parameter cần `javac -parameters` | Tùy chọn đó chủ yếu cho tên parameter; annotation runtime của parameter có API riêng |
| Annotation trên superclass method tự xuất hiện khi class con override | Method override là khai báo mới; cần chính sách tìm qua hierarchy nếu nghiệp vụ yêu cầu |
| Đọc được private method annotation tức có quyền invoke method | Khả năng đọc metadata và khả năng truy cập gọi method là hai câu hỏi riêng |
| Có thể ép mọi object `AnnotatedElement` thành `Class<?>` | Các loại metadata khác nhau cùng implement interface; cần `instanceof` hoặc contract chung |

## 13. Tự kiểm tra

1. **Vì sao `Child.class.getAnnotation(Role.class)` thấy `Role` nhưng `getDeclaredAnnotation(Role.class)` không thấy?** Vì `Role` có `@Inherited` trên `Base` và `Child` không tự khai báo nó.
2. **Nếu annotation type dùng `@Retention(CLASS)`, có dùng `getAnnotation(...)` trên class đang chạy để đọc ổn định không?** Không. Metadata đó không phải runtime-visible qua Reflection chuẩn.
3. **Hai `@Tag` trên cùng class có phải trả một phần tử từ `getAnnotation(Tag.class)` không?** Không; repeatable annotations thường được lưu qua container và phải đọc bằng `getAnnotationsByType(Tag.class)`.
4. **`Field` và `Method` có thể được lưu trong cùng biến interface `AnnotatedElement` không?** Có. Cả hai triển khai interface.
5. **Annotation `@NonBlank` gắn vào return type `String` phải đọc ở đâu?** `method.getAnnotatedReturnType()`, không phải trực tiếp annotation declaration của `Method`.
6. **Muốn tìm annotation trên kiểu `String` trong `List<@X String>` phải làm gì?** Đi qua `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` từ annotated type của phần tử chứa kiểu đó.
7. **Một method override không mang `@Label` của method lớp cha, phải làm gì nếu framework muốn fallback?** Duyệt method hierarchy theo chữ ký và chính sách rõ ràng; đừng giả định `@Inherited` áp dụng cho method.
8. **Tại sao đọc `Parameter.getAnnotation(...)` không cần tên parameter trong class file?** Vì annotation metadata của parameter được lưu và đọc tách khỏi việc lưu tên nguồn bằng `-parameters`.
9. **Có được phép dùng `getAnnotation(...)` làm cách vượt quyền gọi method không public không?** Không. Metadata annotation không sửa quyền truy cập Reflection hoặc module.

**Ghi nhớ:** `AnnotatedElement` là giao diện thống nhất để **đọc annotation của đúng phần tử**. Chọn đúng đối tượng (declaration hay type-use), xác nhận runtime retention, phân biệt trực tiếp/kế thừa và dùng API `ByType` cho repeatable annotation để tránh bỏ sót metadata.

### Tài liệu chính thức

- [Java SE 17 — AnnotatedElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html)
- [Java SE 17 — AnnotatedType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html)
- [Java SE 17 — Executable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html)
- [Java SE 17 — RecordComponent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/RecordComponent.html)
- [Java Language Specification — Annotation interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-002">Retention SOURCE, CLASS, RUNTIME và giới hạn quan sát</a>

<details>
<summary>Xem chi tiết</summary>

# Retention SOURCE, CLASS, RUNTIME và giới hạn quan sát của annotation trong Java

## 1. Retention trả lời câu hỏi gì?

Annotation bổ sung metadata cho khai báo hoặc vị trí dùng kiểu trong mã Java. Tuy nhiên, **không phải annotation nào xuất hiện trong mã nguồn cũng còn tồn tại ở runtime**, và không phải cứ có annotation trong class file thì Reflection thông thường sẽ đọc được.

`java.lang.annotation.RetentionPolicy` xác định **thời điểm đến đâu compiler phải giữ annotation**. Annotation type được khai báo với meta-annotation `@Retention(...)`:

~~~java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface Audit {
}
~~~

Trong ví dụ, các chỗ được đánh dấu `@Audit` sẽ được lưu trong class file theo dạng có thể quan sát bởi Reflection tại runtime. Nếu không khai báo `@Retention` trên annotation type thì **mặc định là `RetentionPolicy.CLASS`**, không phải `RUNTIME`.

Có ba chính sách:

| Chính sách | Trong mã nguồn | Trong class file do `javac` sinh | Reflection chuẩn lúc chạy |
| --- | --- | --- | --- |
| `SOURCE` | Có | Không giữ annotation ở vị trí sử dụng | Không tìm thấy |
| `CLASS` | Có | Có, nhưng không yêu cầu JVM duy trì để Reflection thấy | Không tìm thấy thông qua `getAnnotation` thông thường |
| `RUNTIME` | Có | Có, ở dạng metadata runtime-visible | Có thể tìm thấy khi truy vấn **đúng đối tượng/vị trí** và đúng API |

**Quan trọng:** “`RUNTIME`” là **điều kiện cần** để Java Reflection đọc annotation từ class file, nhưng không bảo đảm mọi cách gọi `getAnnotation` đều trả về nó. Cần xét annotation được đặt ở class, method, field, parameter, record component hay vị trí **TYPE_USE**, và liệu API đang hỏi đúng vị trí đó hay chưa.

## 2. Vai trò của `@Retention` và `@Target` khác nhau

Hai meta-annotation trả lời hai câu hỏi khác nhau:

- `@Retention`: annotation tồn tại tới giai đoạn nào?
- `@Target`: annotation được phép đặt ở loại vị trí nào? Ví dụ `TYPE`, `METHOD`, `FIELD`, `PARAMETER`, `CONSTRUCTOR`, `TYPE_USE`, `TYPE_PARAMETER` hoặc `ANNOTATION_TYPE`.

Ví dụ:

~~~java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface RunMe {}

class Jobs {
    @RunMe
    public void run() {}
}
~~~

`RunMe` chỉ cho phép gắn trên method và có thể được Reflection đọc tại `Jobs.class.getDeclaredMethod("run").getAnnotation(RunMe.class)`. Nhưng `Jobs.class.getAnnotation(RunMe.class)` không có nhiệm vụ tìm annotation nằm trên method, nên kết quả sẽ là `null`.

Không được suy rằng `@Retention(RUNTIME)` có nghĩa annotation **tự xuất hiện ở mọi API Reflection liên quan tới class**.

## 3. Ví dụ Java 17+ độc lập: ba mức retention

Tạo file **`RetentionDemo.java`** với đầy đủ nội dung:

~~~java
import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@interface SourceMark {}

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@interface ClassMark {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface RuntimeMark {
    String value();
}

@SourceMark
@ClassMark
@RuntimeMark("ready")
class Service {}

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface InheritedFlag {}

@InheritedFlag
class Parent {}

class Child extends Parent {}

public class RetentionDemo {
    public static void main(String[] args) {
        System.out.println("source found: "
                + Service.class.isAnnotationPresent(SourceMark.class));
        System.out.println("class found: "
                + Service.class.isAnnotationPresent(ClassMark.class));
        System.out.println("runtime found: "
                + Service.class.isAnnotationPresent(RuntimeMark.class));
        System.out.println("runtime value: "
                + Service.class.getAnnotation(RuntimeMark.class).value());
        System.out.println("declared annotation count: "
                + Service.class.getDeclaredAnnotations().length);

        System.out.println("Child declared inherited: "
                + (Child.class.getDeclaredAnnotation(InheritedFlag.class) != null));
        System.out.println("Child inherited lookup: "
                + (Child.class.getAnnotation(InheritedFlag.class) != null));
        System.out.println("SourceMark policy: "
                + SourceMark.class.getAnnotation(Retention.class).value());
    }
}
~~~

Từ thư mục chứa file trên, chạy:

~~~bash
javac --release 17 RetentionDemo.java
java RetentionDemo
~~~

Stdout chính xác, đã kiểm chứng bằng cách biên dịch Java 17 và chạy chương trình:

~~~text
source found: false
class found: false
runtime found: true
runtime value: ready
declared annotation count: 1
Child declared inherited: false
Child inherited lookup: true
SourceMark policy: SOURCE
~~~

**Giải thích:**

1. `Service` được gắn cả ba annotation trong nguồn. Reflection chỉ thấy `RuntimeMark` vì nó được giữ với `RUNTIME`. `getDeclaredAnnotations().length` bằng `1`, không phải `3`.
2. `SourceMark` không nằm trong class file của `Service` do compiler sinh theo chính sách `SOURCE`.
3. `ClassMark` vẫn có metadata trong class file, nhưng `Class.getAnnotation(ClassMark.class)` không trả về nó vì annotation không có runtime visibility.
4. `RuntimeMark("ready")` có một phần tử `value()`. Khi lấy `RuntimeMark` qua Reflection, có thể gọi `value()` để nhận `"ready"`.
5. `Child.class.getDeclaredAnnotation(InheritedFlag.class)` trả `null` vì `Child` không trực tiếp khai báo annotation. `Child.class.getAnnotation(InheritedFlag.class)` lại tìm thấy annotation được **kế thừa theo quy tắc `@Inherited`** từ `Parent`.
6. `SourceMark.class.getAnnotation(Retention.class).value()` cho ra `SOURCE`, vì ở đây ta đọc **meta-annotation `@Retention` trên chính annotation type `SourceMark`**, không đọc lần sử dụng `@SourceMark` trên `Service`. Đây là hai đối tượng bị hỏi khác nhau.

Đoạn code sử dụng `@Inherited` nhằm minh họa thêm giới hạn quan sát: retention cho phép annotation hiện diện, còn cách truy vấn có xét khai báo thừa kế hay không là một trục khác.

## 4. Đối chiếu bytecode: CLASS khác SOURCE ở đâu?

Sau khi biên dịch file ở phần 3, có thể xem class file:

~~~bash
javap -v -p Service
~~~

Trong phần thông tin annotation của kết quả `javap -v`, bạn sẽ thấy hai nhóm chính:

~~~text
RuntimeVisibleAnnotations:
    RuntimeMark(
        value="ready"
    )
RuntimeInvisibleAnnotations:
    ClassMark
~~~

Đây là **đoạn trích đã giản lược** từ output `javap`, chỉ giữ những dòng thể hiện nhóm retention; output thật còn chứa constant pool, tên kiểu đã encode, chỉ số constant và nhiều metadata khác.

- `RuntimeVisibleAnnotations` chứa `RuntimeMark`, tương ứng chính sách `RUNTIME`.
- `RuntimeInvisibleAnnotations` chứa `ClassMark`, tương ứng chính sách `CLASS`. Tên “invisible” ở đây nói về **Reflection runtime**, không có nghĩa không thể dùng công cụ đọc class file.
- Không có chỗ dùng `SourceMark` trên `Service` trong metadata annotation của class file. Annotation source có thể được **annotation processor** thấy trong quá trình biên dịch dù sau đó không được giữ trong class file.

**Cẩn trọng về cách kiểm tra:** `javap -v` đọc dữ liệu class file; `Class.getAnnotation(...)` dùng Reflection của JVM đang chạy. Hai công cụ phục vụ hai giai đoạn khác nhau. Việc `javap` nhìn thấy `ClassMark` **không mâu thuẫn** với việc `getAnnotation(ClassMark.class) == null`.

Với bytecode đã qua các trình biên dịch, bộ xử lý hoặc công cụ chỉnh sửa class file khác, metadata thực tế có thể thay đổi. Bảng retention mô tả hành vi Java thông thường khi compiler tuân theo quy tắc ngôn ngữ, không cho phép tự kết luận về mọi class file được chỉnh sửa bằng công cụ ngoài.

## 5. SOURCE: còn hữu ích dù Reflection không thấy

`RetentionPolicy.SOURCE` phù hợp với annotation có giá trị **khi biên dịch** và không cần đi vào ứng dụng đang chạy. Một annotation processor có thể truy cập các phần tử mã nguồn trong quá trình compilation bằng `javax.annotation.processing` và model `javax.lang.model`.

Những ứng dụng điển hình:

- Kiểm tra quy tắc source hoặc lint lúc compile.
- Sinh mã nguồn, metadata hoặc tài liệu từ annotation ở compile time.
- Đưa tín hiệu cho trình biên dịch hoặc công cụ xây dựng mà runtime không cần sử dụng.

Ví dụ:

~~~java
import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@interface GenerateAdapter {}

@GenerateAdapter
class CustomerDto {}
~~~

Đây là **đoạn minh họa annotation và class**, không có `main` và không tự tạo output. Nếu một annotation processor được cấu hình để xử lý `GenerateAdapter`, nó có thể thấy annotation khi compile; nhưng sau khi biên dịch, `CustomerDto.class.getAnnotation(GenerateAdapter.class)` vẫn trả `null`.

**Không nhầm hai phát biểu:**

1. “Annotation `SOURCE` không tồn tại tại runtime trong class được biên dịch thông thường” — đúng.
2. “Annotation `SOURCE` không bao giờ có tác dụng” — sai: nó có thể ảnh hưởng quá trình phân tích và sinh mã lúc compile.

Một annotation type `SOURCE` không tự kích hoạt code generation. Phải có compiler/plugin/annotation processor hoặc công cụ tương ứng thực sự xử lý nó.

## 6. CLASS: có trong class file nhưng không được Reflection chuẩn nhìn thấy

`RetentionPolicy.CLASS` dành cho metadata cần được lưu vào class file nhưng không cần được VM làm sẵn qua API Reflection chuẩn. Nó là **mặc định nếu bỏ `@Retention`** trên annotation type.

Ví dụ:

~~~java
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@interface BytecodeTag {}  // Mặc định RetentionPolicy.CLASS

@BytecodeTag
class AnnotatedClass {}
~~~

Đoạn trên là mã minh họa không có `main`. `BytecodeTag` sẽ được lưu tại lần sử dụng trên `AnnotatedClass` trong class file thông thường, nhưng `AnnotatedClass.class.getAnnotation(BytecodeTag.class)` không phát hiện annotation đó ở runtime.

Một bytecode analyzer/instrumenter có thể **đọc trực tiếp class file** và diễn giải annotation `CLASS` bằng thư viện phân tích bytecode (hoặc `javap` để quan sát thủ công). Điều này khác hoàn toàn việc Reflection có quyền tìm annotation đó trong JVM.

Câu hỏi cần đặt ra khi chọn retention:

- Metadata chỉ cần tới compile time? Chọn `SOURCE`.
- Metadata cần được bytecode tooling đọc, nhưng không cần Reflection runtime? Có thể chọn `CLASS`.
- Ứng dụng hoặc framework cần truy vấn annotation trên class, method, field v.v. lúc chạy? Chọn `RUNTIME` và xác định đúng vị trí truy vấn.

## 7. RUNTIME: cần thiết nhưng phải hỏi đúng nơi

Một annotation `RUNTIME` có thể được quan sát theo **vị trí gắn annotation**, nhưng Reflection có nhiều đối tượng tương ứng:

| Annotation được gắn ở đâu? | API Reflection thường dùng |
| --- | --- |
| Class/interface | `SomeClass.class.getAnnotation(...)` hoặc `getDeclaredAnnotations()` |
| Method | `Method.getAnnotation(...)`, `Method.getDeclaredAnnotations()` |
| Field | `Field.getAnnotation(...)`, `Field.getDeclaredAnnotations()` |
| Constructor | `Constructor.getAnnotation(...)` |
| Parameter declaration | `Parameter.getAnnotation(...)` qua `Executable.getParameters()` |
| Type use (generic argument, array component, return type...) | Các dạng `AnnotatedType`, ví dụ `Field.getAnnotatedType()` hoặc `Method.getAnnotatedReturnType()` |
| Record component | `RecordComponent.getAnnotation(...)` hoặc `getAnnotatedType()` tùy target |

Ví dụ phương thức:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Handler {}

class Controller {
    @Handler
    public void run() {}
}
~~~

Mã này cần import `java.lang.annotation.*`; là đoạn khai báo minh họa không có `main`. Với method được khai báo trên `Controller`:

~~~java
boolean present = Controller.class
        .getDeclaredMethod("run")
        .isAnnotationPresent(Handler.class);
~~~

Đoạn trên là một biểu thức minh họa trong method có xử lý `NoSuchMethodException`. Kết quả `true` vì truy vấn đúng **Method**, không phải `Controller.class`.

### TYPE_USE không phải lúc nào là annotation của field declaration

Một annotation đánh dấu generic type argument như `List<@NonNull String>` được gắn vào **vị trí sử dụng kiểu `String`**. Với `@Target(TYPE_USE)`, không nên mặc định gọi `Field.getAnnotation(NonNull.class)` để tìm nó; cần đi qua `AnnotatedType` của field và các type argument.

Phần 9 cung cấp ví dụ chạy được về tình huống này.

## 8. @Inherited và phạm vi kế thừa annotation

`@Inherited` là meta-annotation đặc biệt, nhưng **không tự làm annotation xuất hiện trong method/field hoặc interface của class con**.

Quy tắc cơ bản:

- Chỉ có ý nghĩa cho annotation trên **class declaration** được truy vấn thông qua cơ chế kế thừa áp dụng cho class; không làm interface annotation tự động di truyền sang class triển khai interface.
- `getAnnotation(...)` và `getAnnotations()` có thể nhìn annotation `@Inherited` từ superclass nếu subclass không khai báo annotation loại đó theo cách tương ứng.
- `getDeclaredAnnotation(...)` và `getDeclaredAnnotations()` chỉ nhìn annotation có mặt trực tiếp trên class đang xét, không tính annotation kế thừa.
- Nếu subclass khai báo annotation cùng loại phù hợp, annotation trực tiếp của nó được dùng theo quy tắc truy vấn.
- `@Inherited` không giải quyết việc annotation bị retention `SOURCE` hoặc `CLASS` làm không thể thấy qua Reflection runtime: muốn truy vấn lúc chạy vẫn cần `RUNTIME`.

Chương trình phần 3 minh họa đúng sự khác biệt: `Child.class.getDeclaredAnnotation(InheritedFlag.class)` trả `null`, nhưng `Child.class.getAnnotation(InheritedFlag.class)` không `null`.

Khi một framework tự quét interface, method, field, override hierarchy, nó có thể định nghĩa quy tắc **tự tìm trong cây kế thừa** ngoài phạm vi Java Reflection mặc định; đừng nhầm hành vi riêng của framework với `@Inherited`.

## 9. Annotation lặp lại và TYPE_USE: ví dụ mở rộng độc lập

Lưu toàn bộ chương trình sau vào file **`RetentionExtraDemo.java`**:

~~~java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Tags.class)
@interface Tag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Tags {
    Tag[] value();
}

@Tag("a")
@Tag("b")
class Tagged {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeUseMark {}

class TypeSample {
    List<@TypeUseMark String> names;
}

public class RetentionExtraDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("direct tag count: "
                + (Tagged.class.getAnnotation(Tag.class) == null ? 0 : 1));
        System.out.println("by type count: "
                + Tagged.class.getAnnotationsByType(Tag.class).length);
        System.out.println("container present: "
                + (Tagged.class.getDeclaredAnnotation(Tags.class) != null));

        Field field = TypeSample.class.getDeclaredField("names");
        AnnotatedParameterizedType t =
                (AnnotatedParameterizedType) field.getAnnotatedType();

        System.out.println("field declaration marked: "
                + field.isAnnotationPresent(TypeUseMark.class));
        System.out.println("type argument marked: "
                + t.getAnnotatedActualTypeArguments()[0]
                        .isAnnotationPresent(TypeUseMark.class));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 RetentionExtraDemo.java
java RetentionExtraDemo
~~~

Stdout đã được xác minh:

~~~text
direct tag count: 0
by type count: 2
container present: true
field declaration marked: false
type argument marked: true
~~~

**Điều này minh họa hai giới hạn khác nhau:**

1. `@Tag` là **repeatable annotation**. Khi dùng hai lần trên một class, compiler lưu annotation container `@Tags` tại vị trí thích hợp. `getAnnotation(Tag.class)` không nhất thiết trả một trong hai `Tag`; dùng `getAnnotationsByType(Tag.class)` để lấy cả hai. Trong ví dụ, container xuất hiện trong `getDeclaredAnnotation(Tags.class)`.
2. `@TypeUseMark` nằm trên đối số kiểu `String` trong `List<@TypeUseMark String>`. `Field.isAnnotationPresent(TypeUseMark.class)` trả `false` bởi field declaration không được annotate theo target đó. Nhưng qua `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()[0]`, Reflection đọc được annotation ở vị trí type argument.

Với repeatable annotations, annotation chứa và annotation con phải được khai báo `@Retention`/`@Target` phù hợp. Chỉ một trong hai được đánh `RUNTIME` không phải giải pháp đủ để runtime luôn khôi phục danh sách annotation lặp lại.

Ngoài ra, chú ý phân biệt `getAnnotationsByType(...)` (có thể tính trực tiếp và gián tiếp qua container) với `getDeclaredAnnotationsByType(...)` (chỉ những annotation liên quan tới vị trí khai báo hiện tại; không đi theo inheritance của superclass). Chọn API đúng mục tiêu.

## 10. Retention không đồng nghĩa đủ điều kiện quan sát mọi thứ

Một số câu hỏi chẩn đoán thường gặp:

**“Tôi đã dùng `@Retention(RUNTIME)` nhưng `getAnnotation()` vẫn trả `null`.”**

Kiểm tra lần lượt: annotation gắn đúng vào class/method/field/parameter hay TYPE_USE không; có gọi `getAnnotation` trên đúng `AnnotatedElement` không; có dùng container của repeatable annotation không; có phụ thuộc nhầm `@Inherited` qua interface hay method override không; annotation đã thực sự có trong class file được load chưa.

**“`javap -v` thấy annotation mà Java Reflection không tìm thấy.”**

Kiểm tra annotation nằm dưới `RuntimeInvisibleAnnotations` (chính sách `CLASS`), hay vị trí khác như `RuntimeVisibleTypeAnnotations`. Nếu là `CLASS`, sự khác biệt là đúng theo thiết kế. Nếu là TYPE_USE, cần dùng `AnnotatedType` đúng vị trí.

**“Annotation `SOURCE` không tìm được ở runtime nên annotation processor không thể dùng.”**

Sai. Annotation processor làm việc lúc compile và dùng source/language model; nó không cần `Class.getAnnotation`. Phạm vi retention và phạm vi tool chạy là hai vấn đề riêng.

**“Thấy một annotation type có `@Retention(SOURCE)` ở runtime nghĩa là SOURCE annotation vẫn runtime-visible.”**

Sai. Có thể đọc `@Retention` **trên chính annotation type**, vì meta-annotation `Retention` tự được giữ ở runtime. Điều đó **không biến những lần sử dụng annotation SOURCE trên đối tượng khác thành runtime-visible**.

**“Retention CLASS và RUNTIME giống nhau vì đều có trong .class.”**

Sai: class file có phân loại các attribute visible/invisible; Reflection runtime chỉ exposes thông tin được định nghĩa là runtime-visible thông qua đúng API.

## 11. Bài tập tự kiểm tra

1. Nếu bỏ `@Retention` khỏi `RuntimeMark` trong chương trình phần 3, kết quả `runtime found` và `declared annotation count` sẽ thay đổi như thế nào? Vì sao?
2. Nếu đổi `@Retention(RetentionPolicy.CLASS)` của `ClassMark` sang `RUNTIME`, dòng `class found` sẽ thay đổi thế nào? `javap -v` sẽ chuyển annotation đó sang nhóm attribute nào?
3. Với `@InheritedFlag` chỉ có trên `Parent`, vì sao hai lời gọi `Child.class.getDeclaredAnnotation(...)` và `Child.class.getAnnotation(...)` có kết quả khác nhau?
4. Tại sao `@Tag("a") @Tag("b")` cần `getAnnotationsByType(Tag.class)` để thấy cả hai?
5. Với `List<@TypeUseMark String>`, phải hỏi `AnnotatedType` ở cấp nào để thấy `TypeUseMark`?
6. Nếu công cụ đọc class file thấy `@ClassMark` nhưng `Service.class.isAnnotationPresent(ClassMark.class)` trả false, hành vi nào là phù hợp với retention policy?

## Tài liệu chuẩn

- [Java SE 17 — RetentionPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)
- [Java SE 17 — Retention](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Retention.html)
- [Java SE 17 — AnnotatedElement (getAnnotation, getAnnotationsByType)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html)
- [Java SE 17 — AnnotatedParameterizedType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html)
- [JLS 17 §9.6.4.2 — @Retention và quy tắc retention](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.2)
- [JLS 17 §9.6.4.3 — @Inherited](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.3)
- [JVMS 17 §4.7 — RuntimeVisibleAnnotations / RuntimeInvisibleAnnotations](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-003">getAnnotation, getDeclaredAnnotation và isAnnotationPresent</a>

<details>
<summary>Xem chi tiết</summary>

# getAnnotation, getDeclaredAnnotation và isAnnotationPresent trong Java Reflection

## 1. Mục tiêu: tra cứu annotation tại runtime đúng phạm vi

Annotation trong Java là metadata gắn với class, interface, method, field, constructor, parameter, package hoặc vị trí sử dụng kiểu, tùy khai báo `@Target`. Framework thường dùng annotation để đánh dấu component, router, converter, validation, transaction, mapping và các quy tắc nghiệp vụ. Ba API cốt lõi trên `java.lang.reflect.AnnotatedElement` là:

- `<T extends Annotation> T getAnnotation(Class<T> annotationClass)`: lấy annotation loại yêu cầu nếu nó **hiện diện** (present) trên phần tử theo quy tắc Reflection; nếu không có thì trả `null`.
- `<T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass)`: lấy annotation loại yêu cầu được **gắn trực tiếp trên phần tử** (directly present), không tìm annotation thừa kế; nếu không có thì trả `null`.
- `boolean isAnnotationPresent(Class<? extends Annotation> annotationClass)`: hỏi annotation loại đó có **hiện diện** trên phần tử không. Theo hợp đồng, kết quả tương đương `getAnnotation(annotationClass) != null`.

Khác biệt lớn nhất giữa `getAnnotation` và `getDeclaredAnnotation` thể hiện ở **annotation trên class có `@Inherited`**. Với nhiều phần tử khác, như method và field, hai API thường cho cùng kết quả vì Java **không tự kế thừa annotation của method/field bị override hoặc bị che**.

**Điểm xuất phát:** muốn Reflection đọc được annotation, annotation đó thông thường phải khai báo `@Retention(RetentionPolicy.RUNTIME)`. Nếu annotation chỉ có retention `SOURCE` hoặc `CLASS`, ba API runtime nêu trên không trả annotation ấy, ngay cả khi source có dấu `@MyMarker`.

## 2. Điều kiện để ba API đọc được annotation

Annotation phải được lưu với `@Retention(RetentionPolicy.RUNTIME)` và truy vấn trên **đúng phần tử**. Mặc định khi bỏ `@Retention` là `CLASS`; Reflection runtime không trả những lần sử dụng annotation chỉ có `SOURCE` hoặc `CLASS` retention.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Feature { String name(); }
```

Đây là fragment cần import `java.lang.annotation.*`. `@Target(TYPE)` cho phép gắn `Feature` trên khai báo kiểu; hỏi `Method.getAnnotation(Feature.class)` không tìm annotation đang nằm trên một class. Bài retention riêng giải thích class-file visibility và các trường hợp `TYPE_USE`.

## 3. `AnnotatedElement` là interface chung cho những loại nào?

`Class<?>`, `Method`, `Field`, `Constructor<?>`, `Parameter`, `Package`, `Module` và nhiều đối tượng Reflection khác thực hiện hoặc được xem qua `AnnotatedElement` (cần đối chiếu phiên bản Java thực tế cho các loại chuyên biệt). Điều này cho phép viết tiện ích:

~~~java
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

// Mảnh minh họa, đặt trong một class hoặc method thích hợp.
static <A extends Annotation> A findAnnotation(
        AnnotatedElement element, Class<A> type) {
    return element.getAnnotation(type);
}
~~~

Hàm generic giữ đúng kiểu annotation ở kết quả: nếu gọi `findAnnotation(User.class, Feature.class)` thì kiểu trả về là `Feature`, không cần ép kiểu raw. `getAnnotation` **không** gọi logic nghiệp vụ của class/method; nó chỉ đọc metadata. Đọc annotation trên method private cũng **không đòi `setAccessible(true)`**: quyền gọi private method là việc khác với việc đọc annotation metadata của đối tượng `Method` đã lấy được qua `getDeclaredMethod`.

Một vài ví dụ đối tượng cần chọn chính xác:

- `SomeClass.class`: tra annotation trên class.
- `SomeClass.class.getDeclaredMethod("run")`: tra annotation trên method khai báo trực tiếp.
- `SomeClass.class.getDeclaredField("id")`: tra annotation trên field khai báo trực tiếp.
- `SomeClass.class.getDeclaredConstructor()`: tra annotation trên constructor khớp chữ ký.
- `method.getParameters()[i]`: tra annotation trên formal parameter tương ứng.

Không lấy annotation của một parameter bằng cách gọi `method.getAnnotation(ParamMarker.class)`: lời gọi này tra **method**, không tra tham số của method.

## 4. Phân biệt “directly present” và “present”

Thuật ngữ trong `AnnotatedElement` quan trọng hơn nghĩa tiếng Việt của từ “có”:

1. Annotation loại `A` **directly present** trên một phần tử nếu metadata trực tiếp của phần tử chứa annotation `A` (xét đúng quy tắc với annotation repeatable).
2. Annotation loại `A` **present** nếu nó directly present; ngoài ra, với **class**, annotation loại `A` được đánh dấu `@Inherited` có thể present nhờ đi qua chuỗi **superclass** khi class không có annotation `A` trực tiếp.
3. `getDeclaredAnnotation(A.class)` chỉ xét mức **directly present**; không đi tìm trong superclass.
4. `getAnnotation(A.class)` xét mức **present**, nên có thể nhận annotation `A` từ superclass nếu các điều kiện `@Inherited` thỏa.
5. `isAnnotationPresent(A.class)` tương đương `getAnnotation(A.class) != null`. Nó **không** kiểm tra riêng mức declared.

Một quan niệm sai: “`getDeclaredAnnotation` = tìm trong method viết trực tiếp, còn `getAnnotation` = tìm cả method superclass”. **Không đúng.** Cơ chế `@Inherited` của Core Reflection chỉ tự tìm annotation ở cấp class và chuỗi superclass của **class đó**; nó không khiến method override thừa kế annotation của method cha.

### Bảng nhanh theo phần tử

| Tình huống | `getAnnotation(A.class)` | `getDeclaredAnnotation(A.class)` | `isAnnotationPresent(A.class)` |
| --- | --- | --- | --- |
| Class có `@A` trực tiếp, `A` có runtime retention | Trả `A` | Trả `A` | `true` |
| Subclass không khai báo `@A`; superclass có `@A` và `A` có `@Inherited` | Có thể trả annotation thừa kế | `null` | `true` |
| Subclass không khai báo `@A`; superclass có `@A` nhưng `A` không `@Inherited` | `null` | `null` | `false` |
| Method override không gắn `@A`; method cha có `@A` | `null` trên method override | `null` | `false` |
| Field con che field cha nhưng không gắn `@A` | `null` trên field con | `null` | `false` |
| Annotation trên source có `RetentionPolicy.CLASS` | `null` | `null` | `false` |
| Repeatable `@A` xuất hiện hai lần và được lưu qua container `@As` | Dùng `getAnnotationsByType(A.class)` để lấy đủ hai annotation; `getAnnotation(A.class)` không tự unpack container | Không tự unpack container | Không phải phép hỏi “có bất kỳ A nào trong container” |

## 5. `@Inherited` thay đổi kết quả trên class như thế nào?

Với annotation type `Policy` có `@Inherited`, `@Retention(RUNTIME)` và `@Target(TYPE)`, nếu `Base` mang `@Policy("base")` còn `Child extends Base` không khai báo `Policy`, `Child.class.getAnnotation(Policy.class)` thấy `"base"` nhưng `getDeclaredAnnotation(Policy.class)` trả `null`. Nếu `Child` ghi `@Policy("child")`, giá trị cục bộ được ưu tiên.

Quy tắc này chỉ tra **chuỗi superclass của class**. Annotation trên interface hoặc method bị override không được tự sao chép; method không bị override vẫn có thể được tìm bằng `getMethod` và mang annotation gốc của superclass. Bài về `@Inherited` và bài interface/method dành riêng cho các trường hợp đó. Ví dụ đầy đủ ngay sau đây kiểm chứng cả hai kết quả.

## 6. Chương trình Java 17+ hoàn chỉnh: so sánh ba API

Lưu file **`AnnotationLookupDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AnnotationLookupDemo {
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Role {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface LocalOnly {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Checked {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Column {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.CONSTRUCTOR)
    @interface Create {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(Labels.class)
    @interface Label {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Labels {
        Label[] value();
    }

    @Role("base")
    @LocalOnly
    static class Base {
        @Column
        private int id;

        @Create
        Base() {}

        @Checked
        public void execute() {}

        @Checked
        public void unchanged() {}
    }

    static class Child extends Base {
        @Override
        public void execute() {}
    }

    @Role("child")
    static class ExplicitChild extends Base {}

    @Role("interface")
    interface MarkedInterface {}

    static class ImplementsInterface implements MarkedInterface {}

    @Label("one")
    @Label("two")
    static class MultipleLabels {}

    public static void main(String[] args) throws Exception {
        Role inherited = Child.class.getAnnotation(Role.class);
        System.out.println("child role=" + inherited.value());
        System.out.println("child declared role="
                + (Child.class.getDeclaredAnnotation(Role.class) != null));
        System.out.println("child has role="
                + Child.class.isAnnotationPresent(Role.class));
        System.out.println("child has local="
                + Child.class.isAnnotationPresent(LocalOnly.class));
        System.out.println("explicit child role="
                + ExplicitChild.class.getAnnotation(Role.class).value());
        System.out.println("interface role inherited="
                + ImplementsInterface.class.isAnnotationPresent(Role.class));

        Method overridden = Child.class.getDeclaredMethod("execute");
        Method inheritedMethod = Child.class.getMethod("unchanged");
        System.out.println("overridden method checked="
                + overridden.isAnnotationPresent(Checked.class));
        System.out.println("inherited method owner="
                + inheritedMethod.getDeclaringClass().getSimpleName());
        System.out.println("inherited method checked="
                + inheritedMethod.isAnnotationPresent(Checked.class));

        Field field = Base.class.getDeclaredField("id");
        Constructor<Base> constructor = Base.class.getDeclaredConstructor();
        System.out.println("field column="
                + (field.getDeclaredAnnotation(Column.class) != null));
        System.out.println("constructor create="
                + constructor.isAnnotationPresent(Create.class));

        System.out.println("repeatable getAnnotation="
                + (MultipleLabels.class.getAnnotation(Label.class) != null));
        System.out.println("repeatable container="
                + MultipleLabels.class.isAnnotationPresent(Labels.class));
        System.out.println("repeatable byType="
                + MultipleLabels.class.getAnnotationsByType(Label.class).length);
        System.out.println("repeatable declaredByType="
                + MultipleLabels.class
                    .getDeclaredAnnotationsByType(Label.class).length);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 AnnotationLookupDemo.java
java AnnotationLookupDemo
~~~

Kết quả dự kiến trên Java 17+:

~~~text
child role=base
child declared role=false
child has role=true
child has local=false
explicit child role=child
interface role inherited=false
overridden method checked=false
inherited method owner=Base
inherited method checked=true
field column=true
constructor create=true
repeatable getAnnotation=false
repeatable container=true
repeatable byType=2
repeatable declaredByType=2
~~~

### Đọc kết quả từng nhóm

- `Child` **không** có `@Role` trực tiếp, nhưng `@Role` được đánh dấu `@Inherited` trên loại annotation và superclass `Base` có `@Role("base")`. Vì vậy `getAnnotation` trả bản từ cha, `getDeclaredAnnotation` trả `null`, `isAnnotationPresent` trả `true`.
- `@LocalOnly` không có `@Inherited` nên `Child.class.isAnnotationPresent(LocalOnly.class)` trả `false` dù `Base` có annotation này.
- `ExplicitChild` ghi `@Role("child")` trực tiếp, nên giá trị gần nhất là `child`, không phải `base`.
- `ImplementsInterface` implements một interface có `@Role("interface")`. `@Inherited` **không áp dụng qua quan hệ interface**; kết quả `false`.
- `Child.execute()` override method cha nhưng không có `@Checked`, do đó nó **không** tự thừa kế annotation method. Với `unchanged()`, `getMethod` trả về chính đối tượng method đã khai báo tại `Base` (vì Child không override), nên tìm được `@Checked`. Đây là việc Reflection chọn **method khác**, không phải quy tắc thừa kế annotation cho method override.
- `Base.id` là field private có `@Column`; tìm qua `Base.class.getDeclaredField("id")` rồi đọc annotation được, không cần mở quyền bằng `setAccessible(true)`.
- `Base()` có `@Create` và Reflection tìm được trên đối tượng `Constructor`.
- Hai `@Label` được lưu thông qua annotation container `@Labels`, nên `getAnnotation(Label.class)` không tự trả một phần tử; `getAnnotationsByType(Label.class)` / `getDeclaredAnnotationsByType(Label.class)` giải nén được cả hai.

## 7. Khi annotation được lặp lại

Với một `@Repeatable(Labels.class)`, hai `@Label` thường được compiler lưu trong container `@Labels`. Bởi vậy `getAnnotation(Label.class)` và `isAnnotationPresent(Label.class)` có thể lần lượt là `null` và `false`, dù lớp có hai giá trị nhãn. Muốn đọc đủ các nhãn, dùng `getAnnotationsByType(Label.class)`; muốn chỉ đọc trên phần tử hiện tại, dùng `getDeclaredAnnotationsByType(Label.class)`.

Container và quy tắc kế thừa của repeatable annotations được giải thích trong hai bài chuyên biệt. `getAnnotation` cũng không tự quét meta-annotations được đặt trên một annotation type: framework muốn tìm stereotype/composed annotations cần thuật toán riêng.

## 8. Lấy annotation trên class và method: chú ý override

Tình huống:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Validate {}

class Parent {
    @Validate
    public void save() {}
}

class Child extends Parent {
    @Override
    public void save() {}
}
~~~

Đây là mảnh minh họa yêu cầu import tương ứng; nó không phải một chương trình độc lập. Khi chọn `Child.class.getDeclaredMethod("save")`, đó là **method của Child**, không được gắn `@Validate`, vì vậy cả `getAnnotation(Validate.class)` và `getDeclaredAnnotation(Validate.class)` đều trả `null`.

Nếu framework muốn hỗ trợ “annotation method được thừa kế” thì phải **tự định nghĩa thuật toán**: lần theo superclass/interface phù hợp, ánh xạ phương thức override qua tên và parameter types, cân nhắc bridge method, modifier, visibility, generic erasure và trường hợp annotation xung đột. Đừng viết một vòng quét đơn giản dựa vào tên method duy nhất, vì overload, override và default interface methods có thể khiến lựa chọn sai.

Ngoài ra:

- `getMethod(...)` tìm public method có thể được kế thừa; nó có thể trả một `Method` với `getDeclaringClass()` là superclass. Đọc annotation ở object đó là đọc annotation của **declaration thực tế**.
- `getDeclaredMethod(...)` tìm method được khai báo trong class chỉ định, không tự đi superclass. Nếu class không khai báo method được yêu cầu, ném `NoSuchMethodException`.
- Với method có cùng tên nhưng khác parameter types, hãy tra cứu bằng chữ ký đầy đủ, ví dụ `getDeclaredMethod("save", String.class)`.
- `@Inherited` không có hiệu lực lên `@Target(METHOD)` theo quy tắc Reflection; nó là cơ chế áp dụng cho annotation trên class.

## 9. Những điều cần kiểm tra trước khi dùng kết quả vào nghiệp vụ

### 9.1. Annotation type, retention và target

Khi không tìm thấy annotation:

1. Kiểm tra import annotation đúng package và annotation class thực sự mong muốn.
2. Kiểm tra `@Retention(RUNTIME)`. Không có retention tường minh thì mặc định `CLASS`.
3. Kiểm tra `@Target` và vị trí annotation trong source: class, method, field, constructor, parameter hay `TYPE_USE`.
4. Kiểm tra phần tử Reflection đang đọc chính xác: class nào, method signature nào, field của declaring class nào.
5. Nếu muốn annotation thừa kế, xác nhận `@Inherited` có gắn trên loại annotation và tra trên **class** với quan hệ superclass.
6. Nếu annotation là repeatable, ưu tiên `getAnnotationsByType` thay vì `getAnnotation`.
7. Nếu muốn metadata trên interface hoặc meta-annotation, triển khai chiến lược riêng. Core Reflection không tự dò toàn bộ cây interface/meta-annotation.

### 9.2. Tránh `NullPointerException` khi lấy giá trị

`getAnnotation(...)` và `getDeclaredAnnotation(...)` có thể trả `null`, nên không viết:

~~~java
// Code minh họa có thể lỗi khi không có annotation.
String name = element.getAnnotation(Feature.class).name();
~~~

Thay bằng nhánh kiểm tra:

~~~java
// Mảnh minh họa; element là AnnotatedElement.
Feature feature = element.getAnnotation(Feature.class);
if (feature != null) {
    String name = feature.name();
    // Dùng name theo quy tắc nghiệp vụ.
}
~~~

Nếu chỉ cần biết có annotation hay không, gọi `isAnnotationPresent(Feature.class)` sẽ rõ ý đồ. Nếu cần nội dung member như `value()`, gọi `getAnnotation` một lần và kiểm tra null để tránh tra cứu lặp không cần thiết.

### 9.3. Đọc annotation không bảo đảm mọi member value hợp lệ

Kết quả annotation là object đại diện metadata runtime với các element methods. Trong trường hợp class file/annotation declaration không còn tương thích, việc đọc element có thể gây các lỗi như `TypeNotPresentException`, `EnumConstantNotPresentException`, `AnnotationTypeMismatchException` hoặc `IncompleteAnnotationException`. Điều này không biến `getAnnotation` thành một API thao tác nghiệp vụ: khi lấy cấu hình từ annotation, framework nên báo lỗi metadata với tên annotation, phần tử và member bị lỗi, thay vì bỏ qua.

`getAnnotation(null)`, `getDeclaredAnnotation(null)` và `isAnnotationPresent(null)` không phải cách kiểm tra “bất kỳ annotation nào”; API yêu cầu class annotation không null, và có thể ném `NullPointerException`.

### 9.4. Không dùng `getDeclaredAnnotation` để đoán quyền truy cập Java

Từ “declared” nghĩa là “annotation trực tiếp ở phần tử này”, không có nghĩa chỉ dùng cho private/protected member. Một annotation trên public class vẫn có thể lấy qua `getDeclaredAnnotation`. Tương tự, annotation trên private method có thể được đọc từ `Method` đã lấy bằng `getDeclaredMethod` mà không cần mở quyền để gọi method.

### 9.5. Kế thừa annotation khác kế thừa thành viên

- Field hiding: field cùng tên ở class con và field ở cha là **hai khai báo khác nhau**, không tự chia sẻ annotation.
- Method overriding: method mới ở class con không tự thừa kế annotation trên method cha.
- Constructors: không được kế thừa, nên annotation trên constructor cha không chuyển sang constructor con.
- Parameter annotations: không tự kế thừa theo vị trí tham số của method override.
- Class inheritance: chỉ annotation type có `@Inherited` mới được Core Reflection xét theo superclass, với các điều kiện tương ứng.

## 10. Khuyến nghị thiết kế bộ tra cứu annotation

Đối với framework, hãy viết rõ **chính sách tìm kiếm** thay vì dùng một hàm `findAnnotation` mơ hồ.

**Declared-only policy:** khi kiểm tra một declaration đúng vị trí, dùng `getDeclaredAnnotation` hoặc `getDeclaredAnnotationsByType`. Thích hợp cho cấu hình cần gắn trực tiếp trên class cụ thể để tránh vô tình nhận quy tắc từ superclass.

**Java-present policy:** khi muốn đúng ngữ nghĩa sẵn có của Java, dùng `getAnnotation` hoặc `isAnnotationPresent`; với class + `@Inherited`, annotation từ superclass hợp lệ. Với repeatable annotations, dùng các API `ByType` thích hợp.

**Framework-custom policy:** khi muốn quét cả interfaces, overridden methods hoặc meta-annotations, xác định thuật toán tìm kiếm và thứ tự ưu tiên riêng, có kiểm thử với diamond inheritance, annotation mâu thuẫn, method overload, bridge methods và classloader. Không giả định `isAnnotationPresent` đã làm công việc đó.

Ví dụ quyết định policy:

| Mục tiêu | Chính sách khuyến nghị |
| --- | --- |
| Class hiện tại có `@Configuration` do chính nó khai báo? | `getDeclaredAnnotation(Configuration.class) != null` |
| Class có annotation `@Inherited` từ superclass được Java công nhận? | `getAnnotation(...)` hoặc `isAnnotationPresent(...)` |
| Mọi route marker repeatable trên class này? | `getDeclaredAnnotationsByType(Route.class)` |
| Tìm annotation trên method interface và override của implementation? | Thuật toán framework riêng; không dựa vào `getAnnotation` trên method override. |
| Tìm annotation marker nằm bên trong meta-annotation? | Duyệt annotation types/meta-annotations có kiểm soát. |
| Cần đọc một annotation có giá trị cấu hình? | `getAnnotation`, kiểm tra null, rồi đọc các element methods. |

## 11. Bài tập kiểm chứng

1. Trong demo, xóa `@Inherited` khỏi định nghĩa `Role`: dự đoán kết quả `child role` và `child has role`; sửa mã để xử lý `null` trước khi chạy.
2. Gắn `@Role("child-own")` trực tiếp lên `Child` rồi so sánh `getAnnotation` và `getDeclaredAnnotation` ở chính `Child`.
3. Gắn `@Checked` trực tiếp lên `Child.execute()` để thấy annotation trên method override không còn phụ thuộc superclass.
4. Dùng `Child.class.getMethod("unchanged").getDeclaringClass()` để giải thích tại sao annotation trên `Base.unchanged()` vẫn đọc được.
5. Thử annotation với `RetentionPolicy.CLASS` hoặc bỏ `@Retention` và quan sát `getAnnotation(...) == null`.
6. Thử hai `@Label` so với một `@Label`; kiểm tra `getAnnotation(Label.class)`, `isAnnotationPresent(Label.class)` và `getAnnotationsByType(Label.class)`.
7. Tạo interface có `@Role` và một class implements interface; chứng minh `@Inherited` không áp dụng qua interface.
8. Tạo annotation gắn trên formal parameter bằng `@Target(PARAMETER)`, đọc qua `method.getParameters()[0].getAnnotation(...)` thay vì đọc trên `Method`.
9. Viết hàm lấy annotation theo declared-only và một hàm theo Java-present rồi chạy cùng lớp `Base/Child` để so sánh API contract.

## 12. Tài liệu tham khảo

- [Java SE 17 — AnnotatedElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html): `getAnnotation`, `getDeclaredAnnotation`, `isAnnotationPresent`, repeatable annotation và các định nghĩa present/directly present.
- [Java SE 17 — Inherited](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html): kế thừa annotation theo superclass của class, không phải từ interface hoặc method.
- [Java SE 17 — Retention](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Retention.html): retention policy và mặc định `CLASS`.
- [Java SE 17 — Repeatable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Repeatable.html): cơ chế annotation container và các API `ByType`.
- [Java Language Specification 17 — `9.6 Annotation Types và `9.7 Annotations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html): định nghĩa, khả năng áp dụng và ngữ nghĩa annotations.

**Ghi nhớ:** `getDeclaredAnnotation` chỉ đọc annotation gắn trực tiếp; `getAnnotation` đọc annotation *present* (có thể theo superclass đối với class và loại annotation có `@Inherited`); `isAnnotationPresent` chỉ hỏi cùng điều kiện present đó. Muốn lặp annotation, tra interface hoặc method override, phải chọn API/policy phù hợp thay vì kỳ vọng ba getter tự làm mọi việc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-004">getAnnotations khác getDeclaredAnnotations</a>

<details>
<summary>Xem chi tiết</summary>

# `getAnnotations()` khác `getDeclaredAnnotations()`

Khi đọc annotation bằng Reflection, bạn cần xác định hai điều: **đang hỏi annotation của phần tử nào** và **có chấp nhận annotation được kế thừa hay không**. Hai phương thức cùng trả về `Annotation[]`, nhưng trả lời hai câu hỏi khác nhau. Sự khác biệt dễ thấy nhất khi phần tử đang xét là một lớp có lớp cha.

Ví dụ, lớp cha mang một chính sách chung, còn lớp con không khai báo chính sách riêng. Một công cụ kiểm tra cấu hình có thể muốn lấy chính sách áp dụng cho lớp con; một công cụ phân tích mã lại chỉ muốn biết chính lớp con khai báo những gì. Chọn sai phương thức khiến hai công cụ hiểu khác nhau cùng một lớp.

## 1. Ý nghĩa và điều kiện nhìn thấy annotation

| Phương thức | Câu hỏi cần trả lời | Kết quả không có annotation |
| --- | --- | --- |
| `getDeclaredAnnotations()` | Phần tử này có những annotation hiện diện trực tiếp nào? | Mảng rỗng |
| `getAnnotations()` | Phần tử này có những annotation hiện diện nào, bao gồm kế thừa khi quy tắc cho phép? | Mảng rỗng |

Với `Class<?>`, `getDeclaredAnnotations()` bỏ qua annotation kế thừa. `getAnnotations()` có thể bổ sung annotation từ lớp cha; nó không đơn giản gom mọi annotation trong cây kiểu. Đây là ngữ nghĩa của [API `Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getAnnotations()).

**`Declared` nói về nơi khai báo, không nói về mức truy cập.** Nó không có nghĩa là chỉ lấy annotation `private`, hay lấy thêm annotation bị ẩn. Hai API này cũng không liệt kê annotation của tất cả phương thức, trường dữ liệu và tham số bên trong lớp. Muốn đọc annotation của phương thức, bạn phải có đối tượng `Method` tương ứng.

Điều kiện chung là annotation cần được giữ lại lúc chạy bằng `@Retention(RetentionPolicy.RUNTIME)`. `SOURCE` bị loại khi biên dịch; `CLASS` không được cung cấp để đọc qua các API Reflection này. [Các chính sách lưu giữ annotation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/RetentionPolicy.html) quyết định điều đó.

Nếu không khai báo `@Retention`, chính sách mặc định là `CLASS`, không phải `RUNTIME`. Vì vậy, nhìn thấy `@SomeAnnotation` trong mã nguồn chưa chứng minh Reflection sẽ tìm thấy nó. Xem [quy tắc mặc định của `@Retention`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/Retention.html).

## 2. Kế thừa annotation trên lớp hoạt động ra sao?

Để annotation trên lớp cha được xét cho lớp con, **kiểu annotation** phải được đánh dấu `@Inherited`. Dấu này được đặt trên khai báo `@interface`, không đặt lên lớp con để yêu cầu nó tự nhận mọi annotation.

Với một kiểu annotation không lặp, có thể hình dung quá trình tìm như sau:

1. Nếu lớp hiện tại khai báo annotation đó, dùng giá trị tại lớp hiện tại.
2. Nếu chưa có và kiểu annotation có `@Inherited`, xét lớp cha trực tiếp.
3. Tiếp tục theo chuỗi lớp cha cho đến khi tìm thấy hoặc không còn lớp cha.

Cơ chế này chỉ đi qua **lớp cha**, không lấy annotation từ interface được triển khai. Nó cũng không làm annotation trên phương thức, trường dữ liệu hay constructor tự truyền xuống thành viên khác. [Đặc tả `@Inherited`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/Inherited.html) giới hạn phạm vi như vậy.

Chẳng hạn, nếu `Parent` có `@Policy("parent")`, thì `Child extends Parent` có thể nhận chính sách đó khi `Policy` có `@Inherited`. Nếu `Child` tự khai báo `@Policy("child")`, việc truy vấn `Policy` chọn chính sách tại `Child`; không trả thêm giá trị `"parent"` như một danh sách thứ hai.

## 3. Thí nghiệm đầy đủ trên Java 17+

Chương trình sau đặt các trường hợp cạnh nhau: annotation thường, annotation có `@Inherited`, interface, phương thức được kế thừa, phương thức ghi đè và annotation lặp.

Lưu toàn bộ vào **`AnnotationLookupDemo.java`**:

```java
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;

public class AnnotationLookupDemo {
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Policy {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface LocalTag {
    }

    @Policy("parent")
    @LocalTag
    public static class Parent {
        @Policy("method-parent")
        public void work() {
        }
    }

    public static class Child extends Parent {
    }

    @Policy("child")
    public static class OwnPolicy extends Parent {
    }

    @Policy("interface")
    public interface Contract {
    }

    public static class Impl implements Contract {
    }

    public static class Overrides extends Parent {
        @Override
        public void work() {
        }
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(Labels.class)
    public @interface Label {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Labels {
        Label[] value();
    }

    @Label("red")
    @Label("blue")
    public static class Tagged {
    }

    public static class TaggedChild extends Tagged {
    }

    @Label("green")
    public static class OwnTagged extends Tagged {
    }

    private static String names(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .map(a -> a.annotationType().getSimpleName())
                .sorted()
                .toList()
                .toString();
    }

    private static String labels(Label[] annotations) {
        return Arrays.stream(annotations)
                .map(Label::value)
                .toList()
                .toString();
    }

    private static void show(Class<?> type) {
        System.out.println(type.getSimpleName() + " annotations = "
                + names(type.getAnnotations()));
        System.out.println(type.getSimpleName() + " declared = "
                + names(type.getDeclaredAnnotations()));
    }

    public static void main(String[] args) throws NoSuchMethodException {
        show(Parent.class);
        show(Child.class);
        show(OwnPolicy.class);
        System.out.println("Child policy = "
                + Child.class.getAnnotation(Policy.class).value());
        System.out.println("OwnPolicy policy = "
                + OwnPolicy.class.getAnnotation(Policy.class).value());
        System.out.println("Impl annotations = "
                + names(Impl.class.getAnnotations()));

        Method inherited = Child.class.getMethod("work");
        Method overridden = Overrides.class.getMethod("work");
        System.out.println("Child.work declaring = "
                + inherited.getDeclaringClass().getSimpleName());
        System.out.println("Child.work annotations = "
                + names(inherited.getAnnotations()));
        System.out.println("Child.work declared = "
                + names(inherited.getDeclaredAnnotations()));
        System.out.println("Overrides.work annotations = "
                + names(overridden.getAnnotations()));
        System.out.println("Overrides.work declared = "
                + names(overridden.getDeclaredAnnotations()));

        show(Tagged.class);
        System.out.println("Tagged getAnnotation(Label) = "
                + Tagged.class.getAnnotation(Label.class));
        System.out.println("Tagged labels = "
                + labels(Tagged.class.getAnnotationsByType(Label.class)));
        System.out.println("TaggedChild declared = "
                + names(TaggedChild.class.getDeclaredAnnotations()));
        System.out.println("TaggedChild labels = "
                + labels(TaggedChild.class.getAnnotationsByType(Label.class)));
        System.out.println("TaggedChild declared labels = "
                + labels(TaggedChild.class.getDeclaredAnnotationsByType(Label.class)));
        System.out.println("OwnTagged labels = "
                + labels(OwnTagged.class.getAnnotationsByType(Label.class)));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 AnnotationLookupDemo.java
java AnnotationLookupDemo
```

Kết quả:

```text
Parent annotations = [LocalTag, Policy]
Parent declared = [LocalTag, Policy]
Child annotations = [Policy]
Child declared = []
OwnPolicy annotations = [Policy]
OwnPolicy declared = [Policy]
Child policy = parent
OwnPolicy policy = child
Impl annotations = []
Child.work declaring = Parent
Child.work annotations = [Policy]
Child.work declared = [Policy]
Overrides.work annotations = []
Overrides.work declared = []
Tagged annotations = [Labels]
Tagged declared = [Labels]
Tagged getAnnotation(Label) = null
Tagged labels = [red, blue]
TaggedChild declared = []
TaggedChild labels = [red, blue]
TaggedChild declared labels = []
OwnTagged labels = [green]
```

Hàm `names()` chủ động sắp xếp tên annotation để kết quả dễ đối chiếu. Không dùng vị trí trong mảng annotation làm quy tắc ưu tiên. Hàm `labels()` giữ thứ tự các giá trị đọc được để quan sát trường hợp annotation lặp.

## 4. Đọc kết quả trên lớp

### Lớp cha và lớp con không khai báo riêng

`Parent` trực tiếp mang `LocalTag` và `Policy`, nên cả hai lời gọi đều thấy hai annotation.

`Child` không khai báo annotation nào, do đó `getDeclaredAnnotations()` cho `[]`. Tuy vậy, `getAnnotations()` vẫn thấy `Policy` vì kiểu annotation này có `@Inherited`. `LocalTag` không có dấu đó nên không xuất hiện khi hỏi `Child`.

Điều cần phân biệt là **annotation tìm thấy khi truy vấn** và **annotation được khai báo trực tiếp**. `Child` không được chèn thêm một khai báo `@Policy` vào mã nguồn chỉ vì `getAnnotations()` tìm thấy nó qua lớp cha.

### Lớp con khai báo chính sách riêng

`OwnPolicy` trực tiếp khai báo `@Policy("child")`. Kết quả từ `getAnnotation(Policy.class).value()` là `"child"`; trong khi `Child` nhận `"parent"`.

Bạn có thể dùng sự khác biệt này để chọn giữa hai quy tắc ứng dụng:

- Đọc cấu hình áp dụng theo kế thừa của Java: truy vấn không có `Declared`.
- Chỉ chấp nhận cấu hình được khai báo tại lớp hiện tại: truy vấn có `Declared`.

Nếu cần biết annotation bắt nguồn từ lớp nào, mảng kết quả không mang sẵn đường đi tìm kiếm. Công cụ cần kiểm tra khai báo trực tiếp từng lớp trong chuỗi lớp cha, thay vì đoán từ thứ tự mảng.

### Interface không truyền annotation cho lớp triển khai

`Contract` có `@Policy("interface")`, nhưng `Impl.class.getAnnotations()` vẫn trả mảng rỗng. `@Inherited` trên `Policy` không thay đổi điều đó.

Nếu ứng dụng muốn áp dụng chính sách từ interface, đó phải là quy tắc do ứng dụng tự thiết kế: duyệt interface nào, xử lý nhiều interface ra sao và giải quyết xung đột thế nào. Không được coi quy tắc đó là hành vi mặc định của `Class.getAnnotations()`.

## 5. Phương thức được kế thừa không phải annotation được kế thừa

Đây là chỗ dễ suy luận sai nhất trong ví dụ.

`Child.class.getMethod("work")` tìm thấy phương thức công khai do `Parent` khai báo. Đối tượng `Method` trả về vẫn đại diện cho **`Parent.work`**, thể hiện qua `getDeclaringClass()` in `Parent`. Vì chính phương thức này trực tiếp mang `@Policy`, cả `getAnnotations()` lẫn `getDeclaredAnnotations()` của nó đều thấy `Policy`.

Ngược lại, `Overrides` khai báo một phương thức `work()` mới để ghi đè. Phương thức đó không mang `@Policy`, nên cả hai lời gọi đều cho `[]`. `@Override` có trong mã nguồn không xuất hiện trong kết quả vì nó không được giữ lại ở runtime.

Do đó, không thể kết luận rằng annotation trên phương thức đã tự truyền xuống chỉ vì bạn tìm thấy annotation khi gọi `Child.class.getMethod(...)`. Trước hết hãy kiểm tra **đối tượng `Method` đang đại diện cho khai báo nào**. Ý nghĩa này gắn với [API `Method.getDeclaringClass()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html#getDeclaringClass()).

Với đối tượng `Method` chuẩn, `getAnnotations()` và `getDeclaredAnnotations()` cùng xét annotation trực tiếp trên phương thức được đại diện; chúng không tự hợp nhất annotation từ phương thức bị ghi đè ở lớp cha.

## 6. Annotation lặp: vì sao thấy container?

`Label` được đánh dấu `@Repeatable(Labels.class)`, trong đó `Labels` là kiểu annotation chứa mảng `Label[]`. Khi `Tagged` có hai `@Label`, trình biên dịch biểu diễn chúng qua annotation chứa `Labels`. [Vai trò của `@Repeatable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/Repeatable.html) là liên kết kiểu lặp với kiểu chứa.

Kết quả thí nghiệm vì vậy là:

- `getDeclaredAnnotations()` thấy `[Labels]`, không phải hai phần tử `Label` rời nhau.
- `getAnnotations()` trên `Tagged` cũng thấy `[Labels]`.
- `getAnnotation(Label.class)` cho `null` trong trường hợp hai `Label` đang nằm bên trong container.
- `getAnnotationsByType(Label.class)` cho hai giá trị `red`, `blue`.

Trong thuật ngữ API, container hiện diện **trực tiếp** (*directly present*); các annotation lặp nằm bên trong hiện diện **gián tiếp** (*indirectly present*). Hai phương thức số nhiều không có `ByType` không tự bóc container thành những phần tử con. Chọn `...AnnotationsByType(Label.class)` khi cần danh sách `Label` logic. Xem [ngữ nghĩa của `AnnotatedElement`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedElement.html).

Trong chương trình, cả `Label` lẫn `Labels` đều có `@Inherited` và `RUNTIME`. Kiểu chứa phải tuân thủ các ràng buộc tương ứng, bao gồm việc có `@Inherited` khi kiểu lặp có dấu này; đó là yêu cầu được quy định cho [annotation lặp và kiểu chứa](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html#jls-9.6.3).

Quan sát hai lớp con:

- `TaggedChild` không khai báo `Label`: `getAnnotationsByType(Label.class)` nhận `[red, blue]`, còn `getDeclaredAnnotationsByType(Label.class)` cho `[]`.
- `OwnTagged` có một `@Label("green")`: truy vấn `getAnnotationsByType(Label.class)` cho `[green]`, không nối tiếp `[red, blue]` từ lớp cha.

Vì thế, “lấy theo kế thừa” không đồng nghĩa “cộng dồn mọi giá trị của toàn bộ cây lớp”. Đặc biệt, đừng tự suy ra quy tắc annotation lặp từ kết quả của `getAnnotations()`; hãy dùng API theo kiểu annotation cần đọc.

## 7. Chọn đúng API cho mục đích sử dụng

| Bạn cần gì? | API phù hợp |
| --- | --- |
| Liệt kê annotation trực tiếp trên phần tử | `getDeclaredAnnotations()` |
| Liệt kê annotation hiện diện, có xét kế thừa hợp lệ trên lớp | `getAnnotations()` |
| Lấy một annotation cụ thể trực tiếp trên phần tử | `getDeclaredAnnotation(MyAnnotation.class)` |
| Lấy một annotation cụ thể, có xét kế thừa hợp lệ | `getAnnotation(MyAnnotation.class)` |
| Lấy các annotation lặp của một kiểu chỉ tại phần tử hiện tại | `getDeclaredAnnotationsByType(MyAnnotation.class)` |
| Lấy các annotation lặp của một kiểu, có xét kế thừa hợp lệ | `getAnnotationsByType(MyAnnotation.class)` |

Nhóm trả về mảng cho mảng rỗng khi không có kết quả; nhóm trả về một annotation cho `null`. Khi đã biết kiểu cần tìm, truy vấn theo kiểu giúp tránh duyệt và so sánh tên annotation bằng chuỗi.

`isAnnotationPresent(MyAnnotation.class)` tương ứng với việc kiểm tra `getAnnotation(MyAnnotation.class) != null`. Vì vậy, không dùng nó để kết luận rằng một annotation lặp chắc chắn không tồn tại khi nhiều phần tử đang được biểu diễn qua container; cần xét kết quả `getAnnotationsByType(...)` trong trường hợp đó.

## 8. Lỗi thường gặp và cách tự kiểm tra

| Suy luận sai | Cách kiểm tra đúng |
| --- | --- |
| `getAnnotations()` lấy mọi annotation từ lớp cha | Kiểm tra `@Inherited` trên từng kiểu annotation |
| Có `@Inherited` là tự nhận annotation từ interface | Kiểm tra đường kế thừa lớp; interface không nằm trong cơ chế này |
| `Declared` liên quan đến `public` hay `private` | Xác định phần tử thực sự khai báo annotation |
| Phương thức ghi đè nhận annotation của phương thức cha | Kiểm tra annotation trên chính khai báo ghi đè |
| Tìm phương thức qua lớp con thì `Method` thuộc lớp con | Đọc `Method.getDeclaringClass()` |
| Thấy annotation trong mã nguồn thì Reflection đọc được | Kiểm tra `@Retention(RUNTIME)` |
| Mỗi `@Label` luôn là một phần tử trong `Annotation[]` | Xem container và dùng API `ByType` |
| Annotation trên annotation tự được hợp nhất vào lớp sử dụng | Phân biệt phần tử đang truy vấn; API không tự duyệt toàn bộ meta-annotation |

Để tự kiểm chứng bài học, hãy sửa từng chi tiết nhỏ trong thí nghiệm rồi dự đoán kết quả trước khi chạy:

1. Bỏ `@Inherited` khỏi `Policy`: `Child` còn thấy `Policy` không?
2. Thêm `@Policy("own-method")` lên `Overrides.work()`: cả hai API trên phương thức trả những gì?
3. Chuyển `LocalTag` sang `RetentionPolicy.CLASS`: `Parent` còn liệt kê được nó không?
4. Chỉ giữ một `@Label` trên `Tagged`: vì sao kết quả truy vấn annotation đơn có thể khác trường hợp hai `@Label`?

Khi một thay đổi làm annotation không còn hiện diện, hãy kiểm tra `null` trước khi gọi `.value()` trên kết quả truy vấn đơn. Nếu bỏ `@Inherited` khỏi `Policy` mà giữ nguyên lời gọi `Child.class.getAnnotation(Policy.class).value()`, chương trình sẽ gặp `NullPointerException` tại lời gọi đó.

Điểm cần nắm là **phạm vi khai báo**, **quy tắc kế thừa** và **cách biểu diễn annotation lặp**. Xác định đủ ba yếu tố này trước khi chọn phương thức Reflection sẽ giúp kết quả truy vấn có ý nghĩa rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-005">@Inherited chỉ áp dụng theo các quy tắc kế thừa annotation của lớp</a>

<details>
<summary>Xem chi tiết</summary>

# @Inherited chỉ áp dụng theo các quy tắc kế thừa annotation của lớp trong Java Reflection

Trong Java, **@Inherited** là một *meta-annotation* thuộc gói **java.lang.annotation**, được đặt trên **khai báo annotation type** để cho phép Reflection nhìn thấy annotation đó trên **lớp con** khi annotation được khai báo trên **lớp cha**. Tuy nhiên, phạm vi kế thừa của @Inherited hẹp hơn trực giác: nó áp dụng cho **annotation trên khai báo class** khi tra cứu theo **chuỗi superclass**, không tự truyền annotation từ interface, không truyền annotation của method/field sang method/field có cùng tên ở subclass, và không tạo bản sao annotation thực sự trong class file của lớp con.

Ví dụ: nếu annotation type **@Category** được đánh dấu @Inherited, class Base có @Category("service") và class Child extends Base chưa khai báo @Category, thì **Child.class.getAnnotation(Category.class)** tìm thấy annotation từ Base. Nhưng **Child.class.getDeclaredAnnotation(Category.class)** trả null, vì Child không trực tiếp khai báo @Category.

Để hiểu đúng hành vi Reflection, cần phân biệt ba thứ: **annotation được khai báo ở đâu**, **annotation type có @Inherited và @Retention(RUNTIME) hay không**, và **API tra cứu có xét annotation kế thừa không**.

## 1. Khai báo annotation kế thừa đúng cách

Ví dụ khai báo annotation dành cho class:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Category {
    String value();
}
~~~

Ý nghĩa từng meta-annotation:

- **@Inherited**: cho phép truy vấn annotation trên class con đi ngược chuỗi superclass nếu class hiện tại không trực tiếp có annotation cùng type.
- **@Retention(RetentionPolicy.RUNTIME)**: giữ annotation để Reflection đọc ở runtime. Nếu không khai báo @Retention, giá trị mặc định là CLASS; annotation có thể nằm trong class file nhưng không được Reflection cung cấp qua các API runtime thông thường.
- **@Target(ElementType.TYPE)**: cho phép annotation xuất hiện trên khai báo type, gồm class và interface. Điều này không có nghĩa annotation trên interface sẽ được kế thừa sang class triển khai.
- **@interface Category**: khai báo một *annotation interface*, còn từ khóa @Inherited là chú thích trên chính annotation interface đó, không phải trên class Base.

Sử dụng:

~~~java
@Category("service")
class Base {}

class Child extends Base {}
~~~

Các truy vấn sau có ý nghĩa khác nhau:

~~~java
Category inherited = Child.class.getAnnotation(Category.class);         // từ Base
Category direct = Child.class.getDeclaredAnnotation(Category.class);   // null
~~~

Hai đoạn mã này là các phần minh họa, cần đặt chung trong một chương trình hoặc cùng package có khai báo Category, Base và Child.

**Không có sự sao chép annotation vào Child.** @Inherited tác động lên quy tắc *tra cứu* trong Reflection, không thay đổi cấu trúc class, method, field hoặc mã bytecode đã biên dịch của subclass.

## 2. Quy tắc kế thừa chỉ đi qua superclass của class

Đối với một annotation type A có @Inherited và được giữ ở runtime, khi gọi **SomeClass.class.getAnnotation(A.class)**, quy tắc khái quát là:

1. Nếu class đang xét có annotation A trực tiếp, trả A của chính class đó.
2. Nếu không có A trực tiếp, kiểm tra **superclass** gần nhất.
3. Tiếp tục đi lên các superclass cho đến khi tìm thấy A phù hợp hoặc hết chuỗi superclass (đến Object).
4. Nếu không tìm được, trả **null**.

Quy tắc này **chỉ dành cho annotation của class declaration**. Nó không duyệt các implemented interfaces hay các superinterfaces.

Ví dụ:

~~~text
@Category("root") Base
                  │
                Middle
                  │
                Leaf

Leaf.class.getAnnotation(Category.class) -> Category("root")
~~~

Nếu Middle khai báo **@Category("middle")**, thì Leaf sẽ nhìn thấy **"middle"** vì đây là annotation gần Leaf hơn. Nếu Leaf tự khai báo **@Category("leaf")**, kết quả của Leaf là **"leaf"**. Một annotation trực tiếp của cùng type trên subclass **che** annotation cùng type kế thừa, chứ Java không cộng gộp cả hai.

### Interface và implements không tạo kế thừa @Inherited

~~~java
@Category("contract")
interface Contract {}

class ContractImpl implements Contract {}
~~~

Mặc dù Category có @Inherited, **ContractImpl.class.getAnnotation(Category.class)** trả null. Thậm chí khi một interface con extends interface cha, @Inherited **không** biến annotation trên superinterface thành annotation kế thừa trên subinterface.

Lý do là @Inherited được định nghĩa cho truy vấn trên **khai báo class** theo **superclass chain**. Quan hệ implements/extends giữa interfaces không tạo đường tra cứu này.

Một framework có thể chủ động quét interfaces hoặc meta-annotations theo quy tắc riêng, nhưng đó là tính năng của **framework**, không phải hành vi mặc định của Class.getAnnotation trong Java core.

### Nested class không tự kế thừa annotation của enclosing class

~~~java
@Category("outer")
class Outer {
    static class Nested {}
}
~~~

Nested nằm trong Outer về mặt khai báo, nhưng không **extends Outer**. Vì thế Nested.class.getAnnotation(Category.class) không nhận "outer" chỉ nhờ quan hệ lồng nhau. **Kế thừa class** và **lồng class** là hai quan hệ khác nhau.

## 3. API Reflection: getAnnotation và getDeclaredAnnotation

Interface **AnnotatedElement** cung cấp API chung để đọc annotation trên Class, Method, Field, Constructor, Parameter và các loại reflective element khác. Nhưng hành vi kế thừa phụ thuộc **loại element đang truy vấn**.

| API | Tìm annotation trực tiếp? | Có thể thấy annotation kế thừa từ superclass? | Ghi chú |
| --- | --- | --- | --- |
| **Class.getAnnotation(A.class)** | Có | Có, nếu A có @Inherited | Trả A hoặc null |
| **Class.isAnnotationPresent(A.class)** | Có | Có, nếu A có @Inherited | Tương đương getAnnotation(A.class) != null |
| **Class.getAnnotations()** | Có | Có, với type inheritable theo quy tắc present | Trả Annotation[] |
| **Class.getDeclaredAnnotation(A.class)** | Có | **Không** | Chỉ annotation trực tiếp |
| **Class.getDeclaredAnnotations()** | Có | **Không** | Không duyệt superclass |
| **Class.getAnnotationsByType(A.class)** | Có | Có theo quy tắc **associated**, kể cả @Repeatable | Trả A[] |
| **Class.getDeclaredAnnotationsByType(A.class)** | Có | **Không** | Mở container @Repeatable nhưng không kế thừa |
| **Method/Field/Constructor.getAnnotation(A.class)** | Có | **Không theo @Inherited** | Annotation trên thành viên không được chuyển sang thành viên khác |

Một số thuật ngữ trong Javadoc AnnotatedElement:

- **Directly present (trực tiếp)**: annotation type A được biểu diễn trực tiếp trên element trong thuộc tính runtime annotation tương ứng.
- **Indirectly present (gián tiếp)**: annotation A là một phần tử bên trong **container annotation** của @Repeatable trên element.
- **Present**: trực tiếp hiện diện hoặc được tìm theo superclass khi A inheritable và element là class.
- **Associated**: trực tiếp/gián tiếp hiện diện, hoặc được tìm theo superclass nếu A inheritable và class hiện tại không có A trực tiếp/gián tiếp.

Phân biệt *present* và *associated* đặc biệt quan trọng với **annotation lặp**: getAnnotation/getAnnotations dùng quan hệ *present*, còn getAnnotationsByType dùng quan hệ *associated*.

### Ví dụ ngắn phân biệt declared và inherited

~~~java
@Category("base")
class Base {}

class Child extends Base {}

boolean visible = Child.class.isAnnotationPresent(Category.class);       // true
boolean own = Child.class.getDeclaredAnnotation(Category.class) != null; // false
~~~

Child có thể “thấy” Category khi được hỏi qua getAnnotation nhưng không **khai báo** Category. Đây không phải lỗi, mà là hai chế độ tra cứu khác nhau.

## 4. Đừng nhầm kế thừa thành viên với kế thừa annotation

`@Inherited` chỉ điều chỉnh annotation trên **khai báo class**, kể cả khi annotation type có `@Target(METHOD)`. `Child.class.getDeclaredMethod("run").getAnnotation(Audited.class)` là `null` nếu `Child.run()` override `Base.run()` nhưng chính `Child.run()` không được đánh dấu `@Audited`.

Ngược lại, nếu `Child` **không override**, `Child.class.getMethod("run")` có thể trả chính `Method` do `Base` khai báo. Khi đó `method.getDeclaringClass() == Base.class`, và annotation của `Base.run()` vẫn có mặt **trên method gốc**. Field công khai được kế thừa hoạt động tương tự khi Reflection trả `Field` của superclass; field bị hide là khai báo mới. Constructor không được kế thừa, parameter và type-use annotation cũng không tự truyền. Bài interface/method đi sâu vào cách duyệt override và interface khi framework muốn tự tìm.

## 5. Ví dụ Java 17+ chạy hoàn chỉnh

Ví dụ duy nhất này minh họa cùng lúc: class annotation kế thừa qua superclass; annotation không có @Inherited; annotation trên interface; annotation của method override; và @Repeatable có @Inherited.

Lưu toàn bộ thành file **InheritedLessonDemo.java**:

~~~java
import java.lang.annotation.*;
import java.lang.reflect.Method;

public class InheritedLessonDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @interface Feature { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Local { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Inherited
    @interface Audited {}

    @Feature("base")
    @Local("base-only")
    static class Base {
        @Audited public void execute() {}
    }
    static class Child extends Base {}
    @Feature("child")
    static class TaggedChild extends Base {}

    @Feature("interface")
    interface Service {}
    static class ServiceImpl implements Service {}

    static class OverrideChild extends Base {
        @Override public void execute() {}
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Repeatable(Roles.class)
    @interface Role { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @interface Roles { Role[] value(); }

    @Role("reader") @Role("writer")
    static class RoleBase {}
    static class RoleChild extends RoleBase {}
    @Role("admin")
    static class RoleOwn extends RoleBase {}

    public static void main(String[] args) throws Exception {
        System.out.println("inherited feature=" + Child.class.getAnnotation(Feature.class).value());
        System.out.println("declared feature=" + (Child.class.getDeclaredAnnotation(Feature.class) != null));
        System.out.println("noninherited local=" + (Child.class.getAnnotation(Local.class) != null));
        System.out.println("override annotation=" + TaggedChild.class.getAnnotation(Feature.class).value());
        System.out.println("interface annotation inherited=" + (ServiceImpl.class.getAnnotation(Feature.class) != null));

        Method overridden = OverrideChild.class.getDeclaredMethod("execute");
        Method unchanged = Child.class.getMethod("execute");
        System.out.println("overridden method annotated=" + overridden.isAnnotationPresent(Audited.class));
        System.out.println("inherited method annotated=" + unchanged.isAnnotationPresent(Audited.class));
        System.out.println("method declaring=" + unchanged.getDeclaringClass().getSimpleName());

        System.out.println("repeatable inherited=" + RoleChild.class.getAnnotationsByType(Role.class).length);
        System.out.println("repeatable declared=" + RoleChild.class.getDeclaredAnnotationsByType(Role.class).length);
        System.out.println("repeatable getAnnotation null=" + (RoleChild.class.getAnnotation(Role.class) == null));
        System.out.println("repeatable local overrides=" + RoleOwn.class.getAnnotationsByType(Role.class)[0].value());
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file:

~~~bash
javac --release 17 -encoding UTF-8 InheritedLessonDemo.java
java InheritedLessonDemo
~~~

Stdout đã được đối chiếu khi biên dịch bằng **javac --release 17** và chạy trên JDK 24:

~~~text
inherited feature=base
declared feature=false
noninherited local=false
override annotation=child
interface annotation inherited=false
overridden method annotated=false
inherited method annotated=true
method declaring=Base
repeatable inherited=2
repeatable declared=0
repeatable getAnnotation null=true
repeatable local overrides=admin
~~~

Đọc kết quả:

1. **inherited feature=base**: Child không có @Feature trực tiếp nên Class.getAnnotation đi lên Base để tìm @Feature("base").
2. **declared feature=false**: Class.getDeclaredAnnotation chỉ hỏi annotation trực tiếp trên Child.
3. **noninherited local=false**: @Local có runtime retention nhưng thiếu @Inherited, vì vậy không được Class.getAnnotation của Child tìm qua superclass.
4. **override annotation=child**: TaggedChild tự khai báo @Feature("child"), annotation trực tiếp gần nhất thắng @Feature("base") của Base.
5. **interface annotation inherited=false**: ServiceImpl implements Service nhưng không nhận @Feature trên Service.
6. **overridden method annotated=false**: method execute được OverrideChild override không nhận @Audited, dù @Audited type có @Inherited.
7. **inherited method annotated=true** và **method declaring=Base**: Child không override, getMethod trả Method gốc do Base khai báo, nên annotation gốc vẫn hiện diện.
8. **repeatable inherited=2**: RoleChild không khai báo Role, nên getAnnotationsByType tìm hai annotation Role ở superclass và mở container Roles.
9. **repeatable declared=0**: Không có Role trực tiếp/gián tiếp trong khai báo RoleChild.
10. **repeatable getAnnotation null=true**: trên RoleBase có hai @Role được compiler biểu diễn dưới **container @Roles**, vì thế getAnnotation(Role.class) không tự mở container để tìm phần tử Role. Dùng getAnnotationsByType(Role.class).
11. **repeatable local overrides=admin**: RoleOwn khai báo một Role riêng nên getAnnotationsByType(Role.class) lấy local Role("admin") và không cộng gộp hai Role ở superclass.

### Thử đổi annotation type

Trong chương trình trên, bỏ **@Inherited** khỏi @Feature rồi biên dịch lại. Khi đó Child.class.getAnnotation(Feature.class) sẽ trả null, vì @Feature không còn inheritable; dòng gọi .value() trực tiếp sẽ gây **NullPointerException**. Để thử nghiệm an toàn, hãy sửa dòng in thành kiểm tra null trước khi đọc value.

Tương tự, đổi RetentionPolicy.RUNTIME của Feature thành RetentionPolicy.CLASS thì các truy vấn runtime thông thường sẽ không còn tìm được @Feature dù vẫn giữ @Inherited: cả **retention** và **inheritance lookup** đều phải phù hợp.

## 6. @Inherited kết hợp @Repeatable: cần nhìn container

Từ Java 8, annotation type có thể được đánh dấu **@Repeatable(Container.class)** để xuất hiện nhiều lần trên cùng element. Khi dùng cả @Inherited, phải hiểu **base annotation** và **container annotation**.

Khai báo mẫu đúng:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Repeatable(Roles.class)
@interface Role {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@interface Roles {
    Role[] value();
}
~~~

Nếu **Role** có @Inherited, **container Roles cũng phải có @Inherited** theo quy tắc JLS về containing annotation type; nếu container không có, compiler từ chối định nghĩa @Repeatable không hợp lệ.

Với:

~~~java
@Role("reader")
@Role("writer")
class Parent {}

class Child extends Parent {}
~~~

Compiler biểu diễn hai @Role trên Parent dưới một **container @Roles**. Khi đọc annotation cần quan tâm:

| Phương thức trên Child.class | Kết quả điển hình |
| --- | --- |
| **getAnnotationsByType(Role.class)** | Mảng hai Role ("reader", "writer") kế thừa theo quy tắc associated |
| **getDeclaredAnnotationsByType(Role.class)** | Mảng rỗng vì Child không trực tiếp khai báo Role |
| **getAnnotation(Role.class)** | null trong trường hợp Parent có hai Role được chứa bởi Roles |
| **getAnnotation(Roles.class)** | Có thể tìm container Roles được kế thừa |
| **getDeclaredAnnotation(Roles.class)** | null nếu Child không khai báo container Roles |

**Không đồng nhất getAnnotation(Role.class) với getAnnotationsByType(Role.class)**. Hàm thứ nhất chỉ tra một annotation *present* theo mô hình AnnotatedElement, còn hàm thứ hai có thể **mở container** và xử lý nhiều annotation liên quan (associated).

Với một @Role("admin") trực tiếp trên Child, getAnnotationsByType(Role.class) trả một Role trực tiếp của Child; nó không nối tiếp các Role inherited của Parent. Kế thừa annotation không tự gộp tất cả giá trị dọc cây class.

Một framework dùng annotation có thể thiết kế cơ chế merge riêng, nhưng nếu chỉ muốn hành vi Java chuẩn, hãy để getAnnotationsByType xử lý các annotation lặp và kế thừa, thay vì viết vòng quét tự động cộng dồn superclass.

## 7. Ranh giới của `@Inherited`

| Tình huống | `@Inherited` tự áp dụng? |
| --- | --- |
| `class Child extends Parent` với annotation trên `Parent` | Có, nếu annotation runtime và Child không có annotation cùng type. |
| `class Impl implements Interface` hoặc `SubInterface extends Interface` | Không, vì không phải superclass chain của class. |
| Method override, field hiding, constructor, formal parameter | Không; mỗi declaration có metadata riêng. |
| Nested class trong enclosing class, package/module annotation, type-use | Không; quan hệ lồng nhau/vị trí kiểu khác kế thừa lớp. |
| Annotation gắn lên một annotation type khác | Không; `Class.getAnnotation` không tự duyệt meta-annotation graph. |

Khi framework muốn mở rộng các đường tìm kiếm này, cần định nghĩa ưu tiên và xung đột, thay vì gán khả năng đó cho `@Inherited` của Java.

## 8. Ứng dụng thực tế khi quét annotation runtime

Giả sử một framework quét các class để tìm cấu hình kế thừa:

- **Nếu annotation có @Inherited và thiết kế yêu cầu kế thừa cấu hình từ class cha**: dùng Class.getAnnotation(A.class) hoặc Class.getAnnotationsByType(A.class) với annotation repeatable.
- **Nếu chỉ muốn biết cấu hình được ghi trực tiếp trên class**: dùng Class.getDeclaredAnnotation(A.class) hoặc Class.getDeclaredAnnotationsByType(A.class).
- **Nếu framework cần đọc annotation trên interfaces**: chủ động dùng getInterfaces()/getGenericInterfaces() hoặc tiện ích xử lý phân cấp interface; @Inherited không tự giúp.
- **Nếu cần đọc annotation trên method override**: xác định method đang xét và khi thích hợp truy vết overridden declarations/superinterfaces với chính sách hợp nhất rõ ràng.
- **Nếu dùng composed/meta-annotations**: phải duyệt annotation types; Java Class.getAnnotation không làm sẵn việc này.
- **Nếu xử lý annotation lặp**: dùng getAnnotationsByType để mở container, tránh chỉ lấy getAnnotation.
- **Nếu annotation không có runtime retention**: Reflection không thể dựa vào Class.getAnnotation để tìm; xem lại cấu hình @Retention hoặc dùng phân tích bytecode nếu bài toán thực sự cần metadata CLASS.

Ví dụ viết một helper đơn giản cho class inheritance:

~~~java
static <A extends java.lang.annotation.Annotation> A inheritedOnClass(
        Class<?> type, Class<A> annotationType) {
    return type.getAnnotation(annotationType);
}
~~~

Đây là method fragment. Hàm này sử dụng chính cơ chế chuẩn của Reflection, **không** tự đi qua interfaces hoặc methods. Nếu annotationType không được đánh dấu @Inherited, kết quả chỉ là annotation trực tiếp của class (không tìm superclass theo @Inherited).

### Lưu ý khi framework cho annotation nhiều nguồn

Nếu muốn gộp cả superclass, interface và annotation trên method, cần định nghĩa một hợp đồng riêng: ưu tiên class hiện tại hay superclass, có merge nhiều cấu hình hay không, khi gặp hai interface khai báo khác nhau thì bên nào thắng, và có đọc meta-annotations không. Đừng gọi tất cả những cách gộp này là hành vi chuẩn của @Inherited.

Việc quét sâu cũng có rủi ro hiệu năng và thứ tự: cache kết quả theo class/annotation type khi dùng nhiều lần, xử lý chu trình trong graph meta-annotations, và định nghĩa quy tắc ưu tiên rõ ràng. Với quy tắc đơn giản theo superclass thì API Class.getAnnotation đã xử lý.

## 9. Các lỗi thường gặp

1. **Đánh dấu @Inherited trên annotation type rồi chờ method annotation tự kế thừa:** không đúng. Nó chỉ áp dụng cho class declaration.
2. **Cho rằng annotated interface làm class implements tự nhận annotation:** không đúng; Class.getAnnotation không tìm qua interfaces.
3. **Quên @Retention(RUNTIME):** annotation sẽ không xuất hiện qua Reflection runtime thông thường.
4. **Nhầm getDeclaredAnnotation với getAnnotation:** declared bỏ qua inherited annotation, kể cả annotation type có @Inherited.
5. **Nhầm getMethod("run") tìm được annotation là vì method override kế thừa annotation:** cần kiểm tra Method.getDeclaringClass(); có thể đó chính là method của superclass.
6. **Giả định @Inherited tự merge annotation cùng type từ nhiều cấp:** annotation trực tiếp của subclass che annotation inherited cùng type; với repeatable, getAnnotationsByType trả nhóm liên quan gần nhất theo quy tắc associated.
7. **Dùng getAnnotation để đọc annotation repeatable có nhiều giá trị:** có thể nhận null vì compiler lưu container. Dùng getAnnotationsByType.
8. **Quên @Inherited trên container của một repeatable inheritable annotation:** cấu hình @Repeatable như vậy không hợp lệ theo JLS.
9. **Cho rằng @Inherited quét các meta-annotations:** không đúng. Đây là logic framework hoặc scanner phải tự thực hiện.
10. **Coi mọi annotation về runtime đều giống nhau:** @Target, @Retention và loại element được truy vấn đều ảnh hưởng đến kết quả.

## 10. Bài tập tự kiểm tra

**Câu 1.** @Feature được khai báo @Inherited và @Retention(RUNTIME). Base có @Feature("A"), Child extends Base không khai báo annotation. Child.class.getAnnotation(Feature.class) là gì?

**Đáp án:** Feature("A"). Class.getDeclaredAnnotation(Feature.class) trên Child trả null.

**Câu 2.** Nếu Child có @Feature("B") thì getAnnotation ở Child trả A hay B?

**Đáp án:** B. Annotation trực tiếp tại Child ưu tiên hơn loại cùng type trên superclass.

**Câu 3.** Interface Contract có @Feature("I"), class Impl implements Contract. Impl.class.getAnnotation(Feature.class) có trả I không?

**Đáp án:** Không. @Inherited không đi qua implemented interfaces.

**Câu 4.** Base.run được đánh dấu annotation @Audited có @Inherited. Child override run nhưng không ghi @Audited. Child.class.getDeclaredMethod("run").isAnnotationPresent(Audited.class) trả gì?

**Đáp án:** false. @Inherited không truyền annotation của method override.

**Câu 5.** Nếu Child **không override** run và Child.class.getMethod("run") tìm thấy public method ở Base, annotation @Audited có thể được đọc không?

**Đáp án:** Có, vì Method được trả về thực sự là của Base. Kiểm tra Method.getDeclaringClass() để xác nhận; đây không phải annotation inheritance giữa hai Method.

**Câu 6.** Superclass có @Role("read") và @Role("write") với Role @Repeatable và @Inherited. Subclass không khai báo Role. Nên dùng API nào để lấy đủ hai giá trị?

**Đáp án:** Class.getAnnotationsByType(Role.class). Hàm này mở container Roles và xem xét quy tắc associated.

**Câu 7.** Annotation @Meta đánh dấu annotation type @Label. Class Sample có @Label nhưng không có @Meta. @Inherited trên Meta có làm Sample.class.getAnnotation(Meta.class) tự tìm được không?

**Đáp án:** Không. @Inherited không tự quét meta-annotations; cần một thuật toán quét annotation type riêng.

**Ghi nhớ:** **@Inherited chỉ thêm quy tắc tra cứu annotation runtime của khai báo class theo chuỗi superclass.** Luôn kiểm tra **@Retention(RUNTIME)**, phân biệt **getAnnotation** với **getDeclaredAnnotation**, không suy diễn sang interface/method/field/constructor, và dùng **getAnnotationsByType** cho repeatable annotations.

### Tài liệu tham khảo

- Java SE 17 — java.lang.annotation.Inherited: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html
- JLS 17 — §9.6.4.3 @Inherited và §9.6.3 Repeatable Annotation Interfaces: https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html
- Java SE 17 — java.lang.reflect.AnnotatedElement, các định nghĩa directly present/present/associated: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html
- Java SE 17 — java.lang.Class, getAnnotation/getAnnotationsByType/getDeclaredAnnotation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 — java.lang.annotation.Repeatable: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Repeatable.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-006">Annotation trên interface hoặc phương thức không tự truyền như annotation lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation trên interface hoặc phương thức không tự truyền như annotation lớp

Trong Java Reflection, annotation đặt trên **lớp**, **interface** và **phương thức** không tuân theo cùng một quy tắc kế thừa. Meta-annotation `@Inherited` chỉ khiến annotation trên **class cha** có thể được tìm khi truy vấn **class con** theo chuỗi superclass. Nó **không** tự truyền annotation từ interface sang implementing class, từ superinterface sang subinterface, hay từ method cha/interface sang method override/implementation.

Vì vậy, khi xử lý **annotation runtime**, cần phân biệt ba việc: annotation **được khai báo trực tiếp** tại đâu, method Reflection đang biểu diễn **method nào**, và ứng dụng có yêu cầu thêm quy tắc tìm kiếm ngoài Java Reflection chuẩn hay không. Sự khác biệt quyết định kết quả của `getAnnotation`, `getDeclaredAnnotation` và các API liên quan.

## 1. Retention, Target và Inherited là ba khái niệm khác nhau

Định nghĩa một annotation có thể đặt trên type/method và đọc ở runtime:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Label {
    String value();
}
```

Đoạn trên là **định nghĩa minh họa**, lưu trong `Label.java` nếu công bố annotation top-level `public`. Ví dụ hoàn chỉnh ở mục 4 khai báo `Label` bên trong class để chỉ dùng một file.

| Meta-annotation | Tác dụng | Không có nghĩa là |
| --- | --- | --- |
| `@Retention(RUNTIME)` | Giữ annotation để các API Reflection có thể đọc lúc chạy | Annotation sẽ được kế thừa |
| `@Target(TYPE)` / `@Target(METHOD)` | Cho phép áp dụng annotation ở vị trí type hoặc method | Annotation trên interface/method sẽ tự truyền |
| `@Inherited` | Khi truy vấn annotation của một **class**, nếu class chưa khai báo nó, Reflection có thể tìm ở **superclass** | Interface, method, field, parameter tự kế thừa annotation |

`@Inherited` chỉ là meta-annotation gắn với **định nghĩa annotation type**. Dù `Label` được phép dùng trên method (`@Target(METHOD)`), hiệu ứng `@Inherited` **vẫn không áp dụng cho method**.

Một annotation muốn được quan sát bằng `Class.getAnnotation` hoặc `Method.getAnnotation` phải có runtime retention. Retention mặc định nếu bỏ `@Retention` là `CLASS`, không phù hợp với ví dụ đọc annotation lúc chạy.

## 2. Mốc so sánh: annotation trên superclass

Giả sử `Label` có `@Inherited`, `@Retention(RUNTIME)` và `@Target(TYPE)`, `Base` mang `@Label("base-class")` còn `Child extends Base` không khai báo nhãn. Khi hỏi `Child.class.getAnnotation(Label.class)`, Reflection nhận nhãn từ `Base`; `Child.class.getDeclaredAnnotation(Label.class)` trả `null`. Nếu `Child` tự mang `@Label("child-class")`, nhãn riêng thắng.

Đây là **quy tắc tra annotation tại class declaration**, không sao chép annotation vào bytecode của `Child`. Phần tiếp theo đối chiếu quy tắc này với interface và method, là hai trường hợp Java Reflection xử lý khác.

## 3. Interface và method không tự kế thừa annotation

### 3.1. Interface được annotatе trực tiếp, class triển khai không tự có

```java
@Label("contract")
interface Contract {}

class Implementation implements Contract {}
```

`Contract.class.getAnnotation(Label.class)` trả annotation trực tiếp trên `Contract`. `Implementation.class.getAnnotation(Label.class)` trả `null` nếu `Implementation` không có annotation tương ứng từ superclass. Tương tự, `interface Sub extends Contract` cũng không tự có annotation của `Contract` theo `@Inherited`.

Nếu một framework muốn coi annotation của interface có hiệu lực trên implementing class, framework cần **chủ động tìm trong interfaces**, rồi áp dụng chính sách riêng về thứ tự và xung đột.

### 3.2. Method override không tự có annotation của parent method

```java
class Parent {
    @Label("parent-operation")
    public void execute() {}
}
class Child extends Parent {
    @Override
    public void execute() {}
}
```

`Child.class.getDeclaredMethod("execute").getAnnotation(Label.class)` trả `null` vì method `Child.execute` được khai báo mới và không tự có `@Label` của `Parent.execute`. Dù `Label` mang `@Inherited`, Reflection không tự duyệt overridden method.

Method interface cũng tương tự: annotation trên `Contract.execute()` không tự xuất hiện ở `Implementation.execute()`.

### 3.3. Kế thừa method mà **không override** là trường hợp khác

```java
class Parent {
    @Label("parent-method")
    public void work() {}
}
class Child extends Parent {}
```

`Child.class.getMethod("work")` có thể trả `Method` khai báo tại `Parent`; `getDeclaringClass()` cho thấy `Parent.class`. Nếu đọc `getAnnotation(Label.class)` trên chính `Method` đó, ta vẫn thấy `@Label("parent-method")` vì **đang đọc metadata method của Parent**, không phải annotation đã được sao chép lên một method khác tại Child.

Các ví dụ ở mục 3 là tình huống minh họa riêng biệt; không đặt nhiều class `Parent` trùng tên trong cùng một file. Mục tiếp theo có chương trình chạy hoàn chỉnh, phân biệt các trường hợp bằng tên rõ ràng.

## 4. Ví dụ Java 17+ có thể biên dịch/chạy

Tạo tệp `AnnotationInheritanceDemo.java` với nội dung:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class AnnotationInheritanceDemo {
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Label {
        String value();
    }

    @Label("base-class")
    static class Base {
        @Label("base-inherited-method")
        public void inheritedMethod() {}

        @Label("base-override-method")
        public void overriddenMethod() {}
    }

    static class Child extends Base {
        @Override
        public void overriddenMethod() {}
    }

    @Label("interface-type")
    interface Contract {
        @Label("interface-method")
        void execute();
    }

    static class Implementation implements Contract {
        @Override
        public void execute() {}
    }

    public static void main(String[] args) throws Exception {
        System.out.println("child.class=" +
                Child.class.getAnnotation(Label.class).value());
        System.out.println("child.declaredAbsent=" +
                (Child.class.getDeclaredAnnotation(Label.class) == null));

        System.out.println("interface.direct=" +
                Contract.class.getAnnotation(Label.class).value());
        System.out.println("implementation.interfaceAbsent=" +
                (Implementation.class.getAnnotation(Label.class) == null));

        Method inherited =
                Child.class.getMethod("inheritedMethod");
        System.out.println("inheritedMethod.owner=" +
                inherited.getDeclaringClass().getSimpleName());
        System.out.println("inheritedMethod.label=" +
                inherited.getAnnotation(Label.class).value());

        Method overridden =
                Child.class.getMethod("overriddenMethod");
        System.out.println("overriddenMethod.owner=" +
                overridden.getDeclaringClass().getSimpleName());
        System.out.println("overriddenMethod.labelAbsent=" +
                (overridden.getAnnotation(Label.class) == null));
        System.out.println("parentMethod.label=" +
                Base.class.getMethod("overriddenMethod")
                        .getAnnotation(Label.class).value());

        Method implemented =
                Implementation.class.getMethod("execute");
        System.out.println("implementedMethod.labelAbsent=" +
                (implemented.getAnnotation(Label.class) == null));
        System.out.println("interfaceMethod.label=" +
                Contract.class.getMethod("execute")
                        .getAnnotation(Label.class).value());
    }
}
```

Chạy ở thư mục chứa tệp, với Java 17 trở lên:

```bash
javac AnnotationInheritanceDemo.java
java AnnotationInheritanceDemo
```

Kết quả stdout:

```text
child.class=base-class
child.declaredAbsent=true
interface.direct=interface-type
implementation.interfaceAbsent=true
inheritedMethod.owner=Base
inheritedMethod.label=base-inherited-method
overriddenMethod.owner=Child
overriddenMethod.labelAbsent=true
parentMethod.label=base-override-method
implementedMethod.labelAbsent=true
interfaceMethod.label=interface-method
```

**Giải thích từng nhóm kết quả:**

1. `Child` kế thừa **kết quả tra cứu annotation ở cấp class** từ `Base` do `Label` mang `@Inherited`. `getDeclaredAnnotation` vẫn trả `null` vì `Child` không tự khai báo.
2. `Contract` được gắn `@Label` trực tiếp nên đọc được `interface-type`; `Implementation` triển khai `Contract` nhưng không tự nhận annotation đó.
3. `inheritedMethod` không bị `Child` override; `Child.class.getMethod(...)` trả đối tượng `Method` của `Base` nên `getDeclaringClass()` là `Base` và `Label` vẫn có trên method ấy.
4. `overriddenMethod` là method khai báo ở `Child`; annotation `Label` không được truyền từ method `Base`. Truy vấn trực tiếp `Base.overriddenMethod` vẫn có `base-override-method`.
5. `Implementation.execute` không tự có annotation của `Contract.execute`. Muốn đọc `interface-method` phải truy vấn method khai báo trên interface.

Trong ví dụ chỉ **đọc metadata**, không cần gọi `setAccessible(true)` hoặc thực thi các method đích. Kết quả không phụ thuộc vào thứ tự trả về của các mảng Reflection.

## 5. Chọn API theo phần tử đang truy vấn

| API | Khi nào dùng? |
| --- | --- |
| `getAnnotation(A.class)` / `isAnnotationPresent(A.class)` | Hỏi annotation *present* trên phần tử; chỉ khi phần tử là class mới có thể xét `@Inherited`. |
| `getDeclaredAnnotation(A.class)` | Chỉ hỏi annotation được khai báo trực tiếp, không tìm superclass. |
| `getAnnotations()` / `getDeclaredAnnotations()` | Liệt kê annotation theo phạm vi tương ứng, không tự mở container repeatable. |
| `getAnnotationsByType(A.class)` / `getDeclaredAnnotationsByType(A.class)` | Đọc cả các `@Repeatable` gián tiếp trong container, có hoặc không xét inheritance phù hợp. |

Trên một `Method` đại diện cho khai báo override, cả hai getter một-annotation đều đọc **method hiện tại**; nếu cần annotation trên method cha/interface, phải chủ động duyệt quan hệ method. Không suy ra một method đã nhận annotation kế thừa chỉ vì `getMethod()` trả một khai báo từ superclass.

## 6. Khi muốn lấy annotation method cha, phải tự duyệt

Một thư viện đôi khi muốn method override dùng annotation khai báo trên method cha. Đây là **quy tắc tự thiết kế**; Java Reflection mặc định không làm vậy.

Ví dụ helper tìm annotation trực tiếp trên các method trùng tên/chữ ký trong superclass chain:

```java
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

static <A extends Annotation> A findFromClassHierarchy(
        Class<?> start,
        String methodName,
        Class<?>[] parameterTypes,
        Class<A> annotationType) {
    for (Class<?> current = start;
         current != null;
         current = current.getSuperclass()) {
        try {
            Method method = current.getDeclaredMethod(
                    methodName, parameterTypes);
            A annotation = method.getDeclaredAnnotation(annotationType);
            if (annotation != null) {
                return annotation;
            }
        } catch (NoSuchMethodException ignored) {
            // Lớp này không khai báo method với chữ ký đã tìm.
        }
    }
    return null;
}
```

Đây là **helper minh họa** cần đặt bên trong một class và không có `main`. Nó có thể tìm `@Label("base-override-method")` ở `Base.overriddenMethod` khi bắt đầu từ `Child` và không thấy `Label` tại method `Child`.

**Giới hạn quan trọng:** đoạn này tìm theo **tên và kiểu tham số giống nhau**, chưa chứng minh hai method có quan hệ override hợp lệ theo Java. Nó không loại trường hợp `private`, `static`, synthetic/bridge method, không xét visibility, và không duyệt các interface. Nếu framework cần ngữ nghĩa kế thừa annotation chặt chẽ, phải xử lý riêng các điều kiện này, đặc biệt với generics, covariant return và nhiều superclass/interface.

## 7. Muốn tìm annotation của interface, cũng cần chính sách riêng

Trong chương trình ở mục 4:

```java
Label interfaceLabel =
        Contract.class.getAnnotation(Label.class);
Method interfaceMethod =
        Contract.class.getMethod("execute");
Label methodLabel =
        interfaceMethod.getAnnotation(Label.class);
```

Đoạn này là **mã minh họa**, cần khai báo `Contract`/`Label`, import `Method` và xử lý `NoSuchMethodException`. Nó đọc trực tiếp annotation ở interface và method do interface khai báo; không làm `Implementation` mang annotation ấy.

Nếu class động triển khai nhiều interface, có thể truy cập `Class.getInterfaces()` để tự duyệt; cần xem xét superinterface và các interface do superclass triển khai khi bài toán cần, cũng như tránh thăm lặp. Nếu hai interface có cùng annotation nhưng khác giá trị, ứng dụng phải xác định quy tắc ưu tiên/xung đột. **Không có một chính sách Java Reflection mặc định** rằng annotation interface nào sẽ được “truyền” cho implementation.

Các framework có thể triển khai cơ chế merged-annotation, tìm annotation trên interface hoặc method hierarchy theo chính sách riêng. Đó là hành vi của framework, không thay đổi hợp đồng `@Inherited` hay `Class.getAnnotation` trong Java chuẩn. Khi phụ thuộc framework, cần kiểm tra đúng phiên bản và API cụ thể.

## 8. Các lỗi hiểu sai thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| `@Inherited` giúp annotation interface truyền sang implementing class | Chỉ xét superclass khi truy vấn class; interface không tự truyền |
| `@Inherited` giúp method override nhận annotation từ method cha | Không áp dụng cho method |
| `@Target(TYPE, METHOD)` làm annotation có cơ chế kế thừa ở cả hai nơi | Target chỉ giới hạn vị trí đặt annotation |
| `@Retention(RUNTIME)` có nghĩa là annotation được kế thừa | Runtime retention chỉ giúp Reflection nhìn thấy |
| `Child.getMethod("work").getAnnotation(...)` thấy annotation nên method con đã được annotate | Nếu không override, đối tượng `Method` có thể là method của superclass |
| `getAnnotation` và `getDeclaredAnnotation` là tương đương | `getDeclaredAnnotation` chỉ đọc khai báo trực tiếp; `getAnnotation` trên class có thể xét `@Inherited` |
| Annotation trên `Contract.execute` tự xuất hiện ở `Implementation.execute` | Phải truy vấn interface method hoặc có cơ chế tìm riêng |
| Method có `@Override` sẽ được copy annotation từ parent | `@Override` là kiểm tra tại biên dịch, không tự sao chép runtime annotation |
| API framework tìm thấy interface annotation tức là Reflection chuẩn cũng vậy | Framework có thể duyệt hierarchy theo quy tắc bổ sung |

## 9. Checklist xử lý annotation runtime

1. **Xác định phần tử chứa annotation:** class, interface, method hay phần tử khác? Cơ chế `@Inherited` chỉ áp dụng với class qua superclass.
2. **Kiểm tra retention:** cần `RetentionPolicy.RUNTIME` cho việc đọc annotation qua Reflection lúc chạy.
3. **Kiểm tra target:** `ElementType.TYPE` cho class/interface, `METHOD` cho method; target không quy định inheritance.
4. **Chọn đúng API:** `getDeclaredAnnotation` để đọc trực tiếp; `getAnnotation` khi cần semantics “present” của element.
5. **Nếu đọc method kế thừa, xem `getDeclaringClass()`:** có thể đang lấy metadata của method cha chứ không phải annotation được truyền.
6. **Nếu đọc method override/implement, đừng trông chờ @Inherited:** tự kiểm tra parent method/interface method khi chính sách ứng dụng cần.
7. **Nếu có nhiều nguồn annotation, xác định ưu tiên rõ ràng:** superclass, interfaces, overridden methods, repeatable container và xung đột phải có contract.
8. **Kiểm chứng bằng test cho framework:** kết quả từ cơ chế tìm kiếm mở rộng không thể suy ra chỉ từ API Reflection chuẩn.

## 10. Tài liệu gốc

- [`@Inherited` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html): phạm vi inheritance giới hạn ở annotation trên class và superclass.
- [`AnnotatedElement` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html): ý nghĩa của annotation present, declared, associated và các API truy vấn.

**Ghi nhớ:** `@Inherited` chỉ có hiệu lực khi **truy vấn annotation trên một class qua superclass chain**. Interface và method có annotation của riêng chúng; lớp triển khai interface hoặc phương thức override không tự nhận annotation đó. Khi cần hành vi truyền từ interface/method, phải xây quy tắc tìm kiếm riêng và kiểm tra rõ declaring class, quan hệ override, độ ưu tiên và runtime retention.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-007">Repeatable annotation và container annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Repeatable annotation và container annotation trong Java Reflection

## 1. Vì sao một annotation có thể xuất hiện nhiều lần?

Thông thường, một annotation được gắn một lần lên một thành phần Java. Nhưng có những tình huống cần **nhiều cấu hình cùng loại** trên một class, method, field hoặc một vị trí cho phép khác: nhiều route cho một endpoint, nhiều vai trò truy cập, nhiều lịch chạy, nhiều điều kiện kiểm thử.

Từ Java 8, Java hỗ trợ **repeatable annotation** thông qua meta-annotation `@Repeatable`. Ví dụ:

```java
@Tag("stable")
@Tag("audited")
class OrderService { }
```

Muốn dùng cú pháp này, `Tag` phải được khai báo là repeatable và liên kết với một **container annotation** có phần tử `value()` trả về mảng `Tag[]`. Compiler sẽ xử lý nhiều `@Tag` cùng vị trí theo cơ chế container. Khi đọc metadata bằng Reflection, lập trình viên cần hiểu sự khác nhau giữa **annotation khai báo trong source**, **container được lưu trong class file** và **các API đọc một/multiple annotation**.

Các mục tiêu quan trọng của bài:

1. Khai báo đúng `@Repeatable(Container.class)` và loại container tương ứng.
2. Hiểu việc compiler gom nhiều annotation cùng kiểu vào container.
3. Dùng `getAnnotationsByType` và `getDeclaredAnnotationsByType` để truy vấn **đầy đủ** các annotation lặp lại.
4. Phân biệt `getAnnotation`, `getAnnotations`, `getDeclaredAnnotation`, container annotation và repeatable element.
5. Hiểu `@Retention`, `@Target`, `@Inherited`, thứ tự phần tử và trường hợp không lặp.
6. Tránh các sai sót thường gặp trong framework dùng runtime Reflection.

## 2. Ba thành phần tạo nên repeatable annotation

### 2.1. Annotation lặp được: `@Tag`

Annotation chính mô tả một đơn vị cấu hình. Muốn đọc được ở runtime, dùng `@Retention(RetentionPolicy.RUNTIME)`; muốn cho phép áp dụng lên class và method, dùng `@Target` tương ứng.

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Tags.class)
@interface Tag {
    String value();
}
```

Điểm quyết định là `@Repeatable(Tags.class)`: nó khai báo **`Tags` là kiểu chứa các phần tử `Tag`**. `@Repeatable` là meta-annotation áp dụng trên **khai báo annotation type**, không phải annotation dùng trực tiếp lên `OrderService`.

### 2.2. Container annotation: `@Tags`

Container chứa một mảng annotation chính:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Tags {
    Tag[] value();
}
```

`value()` phải trả về **`Tag[]` đúng kiểu**. Không thể thay nó bằng `String[]`, `List<Tag>` hay `Object[]`. Phần tử này là cầu nối mà compiler và API Reflection dùng để đóng gói và giải nén các bản `@Tag`.

Một container có thể có các phần tử khác, nhưng những phần tử bổ sung phải có **default value** phù hợp với quy tắc khai báo container của Java. Thiết kế đơn giản, ít bất ngờ nhất là chỉ có `Tag[] value();`.

Chú ý `@Retention` và `@Target` được áp dụng lên **cả hai khai báo**. Container phải đáp ứng các ràng buộc meta-annotation tương thích do Java quy định. Để đọc annotation lặp tại runtime, giữ **cả repeatable type và container type ở `RUNTIME`**. Nếu repeatable type là `@Inherited`, container cũng phải được khai báo `@Inherited`. Nếu repeatable type dùng `@Documented`, container cũng cần `@Documented`.

### 2.3. Cú pháp sử dụng

Sau khi khai báo đúng, chương trình có thể gắn nhiều `@Tag` cùng vị trí:

```java
@Tag("stable")
@Tag("audited")
class OrderService {
    @Tag("read")
    @Tag("cache")
    public void load() { }
}
```

Hai lần gắn `@Tag` là **hai giá trị annotation riêng**, theo thứ tự được khai báo. Source code vẫn trực quan: không cần tự viết container mỗi lần có nhiều bản.

Cách viết container tường minh cũng hợp lệ:

```java
@Tags({
    @Tag("stable"),
    @Tag("audited")
})
class ExplicitOrderService { }
```

Khi dùng `getAnnotationsByType(Tag.class)`, hai cách thể hiện này thường có kết quả như nhau. Tuy nhiên code dùng `getAnnotation(Tag.class)` hoặc duyệt `getDeclaredAnnotations()` có thể nhận kết quả rất khác so với trực giác nếu không hiểu container.

## 3. Compiler làm gì khi annotation được lặp?

Khi compiler gặp:

```java
@Tag("stable")
@Tag("audited")
class OrderService { }
```

Nó xử lý hai annotation `@Tag` theo cơ chế container tương ứng với cấu trúc ý tưởng:

```java
@Tags({
    @Tag("stable"),
    @Tag("audited")
})
class OrderService { }
```

Đây là **mô hình tương đương về cách lưu metadata**, không phải bước chỉnh source file hay yêu cầu lập trình viên gọi constructor container. Trong class file, nhiều `@Tag` cùng vị trí được biểu diễn thông qua `@Tags` theo quy tắc Java.

Do vậy có hai mức nhìn:

| Góc nhìn | Nhìn thấy |
|---|---|
| Source Java | Hai `@Tag` đặt liên tiếp |
| Metadata được chứa | Một container `@Tags` có phần tử `Tag[] value()` gồm hai giá trị |
| `getDeclaredAnnotation(Tags.class)` | Đọc được container nếu nó trực tiếp hiện diện |
| `getDeclaredAnnotationsByType(Tag.class)` | API giải nén và trả lại cả hai `@Tag` |
| `getDeclaredAnnotation(Tag.class)` | Có thể trả `null` vì `@Tag` không trực tiếp hiện diện, chỉ hiện diện gián tiếp qua container |

Cách nhìn này giải thích vì sao mã cũ dùng `getAnnotation(Tag.class)` có thể bắt đầu **không tìm thấy annotation** sau khi annotation đó được làm repeatable và được sử dụng nhiều lần trên cùng một thành phần.

Không nên dựa vào việc tự parse tên annotation trong bytecode khi `AnnotatedElement` đã có các API chuẩn để lấy các giá trị gốc.

## 4. Những quy tắc khai báo container phải tuân thủ

Java không chấp nhận tùy ý một cặp annotation làm repeatable/container. Một số quy tắc then chốt:

| Ràng buộc | Ví dụ phù hợp | Vì sao cần |
|---|---|---|
| Container là một annotation type | `@interface Tags` | Compiler phải nhận diện được kiểu chứa |
| Có phần tử `value()` trả đúng mảng kiểu repeatable | `Tag[] value();` | Chứa các instance `@Tag` |
| Các phần tử container khác (nếu có) đều có default value | `String group() default "";` | Cho phép compiler tạo container hợp lệ khi gặp nhiều `@Tag` |
| Retention của container không ngắn hơn retention của kiểu được lặp | `RUNTIME` / `RUNTIME` | Không làm mất thông tin cần lưu |
| Target của container tương thích với nơi kiểu được lặp có thể xuất hiện | Cùng `TYPE`, `METHOD` trong ví dụ | Container phải hợp lệ tại vị trí compiler đóng gói |
| Nếu repeatable type có `@Inherited`, container cũng có `@Inherited` | Cả hai được đánh dấu | Bảo toàn hành vi kế thừa được hỗ trợ |
| Nếu repeatable type có `@Documented`, container cũng có `@Documented` | Cả hai được đánh dấu | Bảo đảm hợp đồng hiển thị trong tài liệu |

Với bài học cơ bản, **đặt retention và target giống nhau trên cặp annotation** là phương án dễ đọc và tránh sai lệch. Quy tắc ngôn ngữ thực tế cho phép một số quan hệ target không hoàn toàn giống nhau nhưng phải thỏa JLS; đừng suy rằng mọi tập target bất kỳ đều hợp lệ.

### Ví dụ không hợp lệ: sai kiểu mảng

```java
// Sai: container không chứa Tag[] ở value().
// @Repeatable(BadTags.class)
// @interface Tag { String value(); }
// @interface BadTags { String[] value(); }
```

### Ví dụ không hợp lệ: không đủ runtime retention

```java
// Không phù hợp để đọc các giá trị bằng runtime Reflection:
// @Retention(RetentionPolicy.RUNTIME)
// @Repeatable(BadTags.class)
// @interface Tag { String value(); }
//
// @Retention(RetentionPolicy.CLASS)
// @interface BadTags { Tag[] value(); }
```

Hai khối trên chỉ là **phản ví dụ chú thích**, không đưa vào file chương trình chạy. Tùy loại vi phạm, compiler sẽ báo lỗi khai báo repeatable/container.

## 5. Retention của repeatable annotation và container

Khi muốn dùng runtime Reflection, cả repeatable annotation lẫn container phải có retention thích hợp (`RUNTIME` trong các ví dụ này). `@Repeatable` chỉ cho phép **lặp**, không tự đổi retention. Nếu thiếu `@Retention` thì mặc định là `CLASS`, và các API runtime không thấy những annotation đó. Xem bài retention để phân biệt xử lý annotation lúc compile và lúc chạy.

## 6. API Reflection: đọc một annotation hay đọc nhiều annotation?

Các kiểu `Class`, `Method`, `Field`, `Constructor`, `Parameter` và một số thành phần Reflection khác triển khai giao diện `java.lang.reflect.AnnotatedElement`. Các API tiêu biểu:

| API | Loại annotation được xét | Có giải nén container không? | Kế thừa đối với class |
|---|---|---|---|
| `getAnnotation(A.class)` | Annotation *present* kiểu `A` | **Không** tự giải nén `A` từ container | Có theo quy tắc `@Inherited` và khái niệm *present* |
| `getDeclaredAnnotation(A.class)` | Annotation **directly present** kiểu `A` | **Không** | Không |
| `getAnnotationsByType(A.class)` | Annotation **associated** kiểu `A` | **Có** nếu `A` repeatable | Có thể có theo `@Inherited` |
| `getDeclaredAnnotationsByType(A.class)` | Annotation trực tiếp hoặc gián tiếp qua container | **Có** | Không |
| `getAnnotations()` | Các annotation *present* dạng mảng | **Không** tự trải phẳng repeated elements | Có thể có |
| `getDeclaredAnnotations()` | Các annotation trực tiếp dạng mảng | **Không** tự trải phẳng repeated elements | Không |
| `isAnnotationPresent(A.class)` | Tương đương `getAnnotation(A.class) != null` | **Không** | Theo `getAnnotation` |

Bảng dùng các thuật ngữ trong Javadoc `AnnotatedElement` Java 17.

**Quy tắc thực hành:** Nếu annotation type được khai báo `@Repeatable` và mục tiêu là thu thập **tất cả các giá trị**, hãy gọi:

```java
Tag[] tags = element.getAnnotationsByType(Tag.class);
```

hoặc khi chỉ muốn các annotation do chính element khai báo:

```java
Tag[] localTags = element.getDeclaredAnnotationsByType(Tag.class);
```

Ở đây `element` có kiểu `AnnotatedElement` phù hợp. Hai phương thức đều trả **mảng**; không có annotation tương ứng thì mảng **rỗng**, không phải `null`. Khác biệt chính là phương thức không có `Declared` có thể xét kế thừa trong tình huống class + `@Inherited`.

### Vì sao `getAnnotation(Tag.class)` có thể trả `null`?

Với **hai** `@Tag` đặt liên tiếp, compiler thường lưu một `@Tags` container. Khi đó `Tag` chỉ **indirectly present** chứ không **directly present** trên element. `getAnnotation(Tag.class)` không “nhìn xuyên” container, nên trên chính element đó có thể trả `null`, dù `getAnnotationsByType(Tag.class)` vẫn trả hai phần tử.

Ngược lại, với **một** `@Tag("one")` đơn lẻ, `Tag` thường được lưu trực tiếp và `getAnnotation(Tag.class)` có thể trả annotation đó. Đây là khác biệt quan trọng giữa **một lần** và **nhiều lần** gắn cùng annotation type.

## 7. Vì sao kết quả `getAnnotation` khác `getAnnotationsByType`?

Trong thuật ngữ `AnnotatedElement`, `@Tag` đơn lẻ có thể **directly present**, còn nhiều `@Tag` được gói trong một `@Tags` container khiến chúng **indirectly present**. `getAnnotation(Tag.class)` xét *present*, không tự trải phẳng container; `getAnnotationsByType(Tag.class)` xét *associated*, bao gồm annotation trực tiếp, gián tiếp và fallback kế thừa hợp lệ.

Thử ba trạng thái: không có tag, chỉ một `@Tag`, và hai `@Tag`. Với hai tag, `getAnnotation(Tag.class)` có thể `null` dù `getAnnotationsByType(Tag.class).length == 2`. Khác biệt giữa hai biến thể `ByType` và cách xử lý `@Inherited` được minh họa riêng trong bài kế tiếp.

## 8. Container annotation khi truy vấn trực tiếp

Container vẫn là một annotation type độc lập. Có thể truy cập nó bằng Reflection:

```java
Tags container = OrderService.class.getDeclaredAnnotation(Tags.class);

if (container != null) {
    for (Tag tag : container.value()) {
        System.out.println(tag.value());
    }
}
```

Đoạn trên giả định khai báo `OrderService` có hai `@Tag` và các annotation type tương ứng, không phải chương trình có `main` độc lập.

Đây là cách giải nén thủ công. Trong phần lớn trường hợp, `getDeclaredAnnotationsByType(Tag.class)` hoặc `getAnnotationsByType(Tag.class)` ngắn gọn và đúng hợp đồng hơn. Ưu điểm của API by-type:

- Hoạt động cả khi chỉ có **một `@Tag` trực tiếp**, không tồn tại container.
- Hoạt động khi có **nhiều `@Tag` gián tiếp qua container**.
- Hoạt động khi người dùng khai báo **`@Tags({ ... })` tường minh**.
- Tuân thủ cách sắp xếp các annotation repeatable mà `AnnotatedElement` quy định.
- Với `getAnnotationsByType`, xét kế thừa đúng quy tắc cho class được chú thích bằng `@Inherited`.

**Đừng tự assume container luôn có mặt.** Nếu element chỉ có một annotation gốc, việc đọc `getDeclaredAnnotation(Tags.class)` thường trả `null`. Nên xem container là cơ chế lưu giữ, không phải API duy nhất để truy vấn mọi trường hợp.

## 9. Thứ tự các repeatable annotations có ý nghĩa

Với:

```java
@Tag("first")
@Tag("second")
@Tag("third")
class Example { }
```

`getDeclaredAnnotationsByType(Tag.class)` trả các annotation theo thứ tự `first`, `second`, `third` tương ứng. Khi đọc từ container, thứ tự dựa vào thứ tự phần tử trong `value()`. Java `AnnotatedElement` quy định thứ tự như thể các phần tử gián tiếp được đặt ở vị trí của container tương ứng.

Điều này quan trọng nếu annotation diễn tả một **pipeline có thứ tự**, danh sách bộ lọc, các rule đánh giá theo thứ tự hoặc các điểm cấu hình lặp. Tuy nhiên, thứ tự annotation không mặc nhiên biến thành quy tắc thứ tự nghiệp vụ: framework cần tuyên bố rõ **có dùng thứ tự nguồn hay không**.

Nếu thứ tự làm thay đổi logic, hãy kiểm thử nó. Không tự sắp xếp các `@Tag` theo alphabet trừ khi đặc tả framework yêu cầu, bởi như vậy có thể thay đổi ý nghĩa của cấu hình được khai báo.

## 10. `@Inherited` kết hợp với repeatable annotation

Để một `@Tag` repeatable được kế thừa khi tra cứu trên **class**, đánh dấu `@Inherited` trên **cả `Tag` và container `Tags`**, cùng retention/target hợp lệ. Với `@Tag("a") @Tag("b")` trên `Parent` và `Child extends Parent` không có tag riêng, `Child.class.getAnnotationsByType(Tag.class)` trả `[a,b]`, còn `getDeclaredAnnotationsByType(Tag.class)` trả mảng rỗng.

```java
@Inherited @Repeatable(Tags.class) @Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Tag { String value(); }

@Inherited @Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Tags { Tag[] value(); }
```

Nếu `Child` khai báo `@Tag("local")`, `getAnnotationsByType(Tag.class)` ưu tiên `[local]`: cơ chế inheritance là fallback, không tự cộng mọi giá trị từ superclass. Nếu muốn merge nhiều tầng hoặc quét interface/method override, framework phải tự làm. Ví dụ chạy ở mục 13 chứng minh các nhánh này.

## 11. Repeatable annotation có thể đặt ở những phần tử nào?

Vị trí hợp lệ vẫn do `@Target` của cả annotation và container quyết định: `TYPE` (class), `METHOD` (method), `FIELD`, `CONSTRUCTOR`, `PARAMETER` hoặc `TYPE_USE`. Để đọc lặp trên method, lấy đúng đối tượng `Method` rồi gọi `method.getDeclaredAnnotationsByType(Tag.class)`; với type-use, lấy đúng `AnnotatedType` trước khi gọi API.

```java
Method method = SomeClass.class.getDeclaredMethod("load");
Tag[] tags = method.getDeclaredAnnotationsByType(Tag.class);
```

Đây là fragment cần class/phương thức và import `Method` hợp lệ. `@Inherited` không làm annotation trên method override tự di truyền. AnnotatedElement và vị trí TYPE_USE được giải thích kỹ ở bài đầu của chương.

## 12. Cách thiết kế API xử lý repeatable annotation

Trong một annotation scanner hoặc framework, nên thực hiện theo thứ tự:

1. **Làm rõ element:** Class, method, field, constructor, parameter hay type use? Chọn `AnnotatedElement` tương ứng.
2. **Kiểm tra retention:** Annotation cần đọc ở runtime phải có `RUNTIME` phù hợp.
3. **Dùng by-type API:** `getDeclaredAnnotationsByType` để lấy annotation của chính declaration; `getAnnotationsByType` khi chủ đích muốn xét inheritance trên class.
4. **Không phụ thuộc vào container là null/non-null:** Một annotation trực tiếp và nhiều annotation gián tiếp có cách lưu khác nhau.
5. **Giữ nguyên thứ tự:** Nếu cấu hình dùng thứ tự, xử lý theo mảng do API trả về.
6. **Định nghĩa quy tắc trùng lặp:** Hai tag có cùng giá trị có được chấp nhận không? Java cho phép các annotation repeatable có nội dung trùng; framework phải quy định có gộp hay báo lỗi.
7. **Không nhầm @Inherited với merge:** Nếu muốn lấy cả lớp con lẫn lớp cha, tự duyệt hierarchy và nêu chính sách.
8. **Phân biệt annotation gốc và container:** Khi hỗ trợ metadata introspection, có thể cần khảo sát container riêng, nhưng xử lý business annotations nên ưu tiên by-type API.
9. **Cẩn thận với meta-annotations:** Nếu muốn scanner quét annotation trên chính một annotation type, cần logic riêng (ví dụ xử lý stereotype/composed annotations); `@Repeatable` tự nó không cung cấp cơ chế “recursive meta-annotation scanning”.

Một utility tối giản dùng được với nhiều kiểu element:

```java
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

public final class AnnotationReaders {
    private AnnotationReaders() { }

    public static <A extends Annotation> A[] locallyDeclared(
            AnnotatedElement element, Class<A> annotationType) {
        return element.getDeclaredAnnotationsByType(annotationType);
    }
}
```

Đây là lớp tiện ích độc lập có thể biên dịch, nhưng không có `main` riêng. Nó cố ý dùng `Declared` để tránh suy diễn annotation từ superclass. Khi muốn inheritance của `Class` có `@Inherited`, có thể chọn `getAnnotationsByType`, nhưng nên đặt tên helper khác để contract rõ ràng.

## 13. Chương trình Java 17+ chạy được: container, method và inheritance

Ví dụ chạy độc lập này chứng minh:

- Compiler đóng gói hai `@Tag` vào `@Tags`.
- `getDeclaredAnnotation(Tag.class)` có thể trả `null` dù có hai tag.
- `getDeclaredAnnotationsByType`/`getAnnotationsByType` giải nén được cả hai giá trị.
- `@Inherited` cho phép class con đọc tag từ superclass nếu class con không có tag riêng.
- Class con có tag riêng sẽ **không** tự cộng dồn tag từ superclass.
- Container khai báo tường minh cũng được by-type API xử lý.
- Truy vấn annotation trên method làm việc tương tự mà không cần giả định inheritance của method.

### 13.1. Mã nguồn

Lưu toàn bộ nội dung sau vào **`RepeatableDemo.java`**:

```java
import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RepeatableDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Inherited
    @Repeatable(Tags.class)
    @interface Tag { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Inherited
    @interface Tags { Tag[] value(); }

    @Tag("stable") @Tag("audited")
    static class Base {
        @Tag("read") @Tag("cache")
        public void run() { }
    }

    static class Child extends Base { }

    @Tag("local")
    static class Local extends Base { }

    @Tags({@Tag("manual-a"), @Tag("manual-b")})
    static class Explicit { }

    static String values(Tag[] tags) {
        return Arrays.stream(tags).map(Tag::value)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("base byType: "
                + values(Base.class.getAnnotationsByType(Tag.class)));
        System.out.println("base direct single: "
                + (Base.class.getDeclaredAnnotation(Tag.class) != null));
        System.out.println("base container: "
                + (Base.class.getDeclaredAnnotation(Tags.class) != null));
        System.out.println("base declared count: "
                + Base.class.getDeclaredAnnotations().length);
        System.out.println("child byType: "
                + values(Child.class.getAnnotationsByType(Tag.class)));
        System.out.println("child declared byType: "
                + Child.class.getDeclaredAnnotationsByType(Tag.class).length);
        System.out.println("local byType: "
                + values(Local.class.getAnnotationsByType(Tag.class)));
        System.out.println("explicit byType: "
                + values(Explicit.class.getAnnotationsByType(Tag.class)));

        Method method = Base.class.getDeclaredMethod("run");
        System.out.println("method byType: "
                + values(method.getDeclaredAnnotationsByType(Tag.class)));
        System.out.println("method container: "
                + (method.getDeclaredAnnotation(Tags.class) != null));
        System.out.println("method direct single: "
                + (method.getDeclaredAnnotation(Tag.class) != null));
    }
}
```

### 13.2. Biên dịch và chạy

Dùng Java Development Kit 17 hoặc phiên bản tương thích mới hơn:

```bash
javac --release 17 RepeatableDemo.java
java RepeatableDemo
```

Chương trình đã được biên dịch với `javac --release 17` và chạy thực tế. Kết quả:

```text
base byType: [stable, audited]
base direct single: false
base container: true
base declared count: 1
child byType: [stable, audited]
child declared byType: 0
local byType: [local]
explicit byType: [manual-a, manual-b]
method byType: [read, cache]
method container: true
method direct single: false
```

### 13.3. Diễn giải đầu ra

**`base byType`:** `Base` có hai `@Tag`, nên `getAnnotationsByType(Tag.class)` trả đủ hai giá trị theo thứ tự khai báo.

**`base direct single: false`:** Mặc dù `Base` có hai cấu hình tag, `getDeclaredAnnotation(Tag.class)` không tìm được `@Tag` trực tiếp vì các giá trị nằm gián tiếp trong container.

**`base container: true` và `base declared count: 1`:** Compiler biểu diễn hai `@Tag` qua một `@Tags` container trực tiếp. Vì `Base` không có annotation trực tiếp nào khác ngoài container đó, `getDeclaredAnnotations().length` là `1`.

**`child byType` và `child declared byType`:** `Child` không khai báo `@Tag`; nhờ `@Inherited` trên cặp annotation, `getAnnotationsByType(Tag.class)` lấy được hai tag từ `Base`. `getDeclaredAnnotationsByType` trả mảng rỗng vì chính `Child` không khai báo tag nào.

**`local byType: [local]`:** `Local` có một tag riêng; `getAnnotationsByType` chọn tag local, không cộng thêm `stable` hoặc `audited` từ `Base`.

**`explicit byType`:** Container viết tường minh `@Tags({ ... })` cũng được Reflection giải nén thành hai `@Tag`.

**`method byType`:** `Base.run` có hai `@Tag` khai báo trên method; `getDeclaredAnnotationsByType` đọc đúng hai giá trị, `getDeclaredAnnotation(Tags.class)` đọc được container và `getDeclaredAnnotation(Tag.class)` không có giá trị trực tiếp.

### 13.4. Một thay đổi nhỏ để tự thử

Đổi dòng `@Tag("stable") @Tag("audited")` trước `Base` thành chỉ `@Tag("stable")`, sau đó biên dịch lại. Khi chỉ có **một annotation trực tiếp**, `Base.class.getDeclaredAnnotation(Tag.class) != null` thường sẽ là `true`; `getDeclaredAnnotation(Tags.class) != null` là `false`; `getAnnotationsByType(Tag.class)` vẫn trả mảng chứa một phần tử.

Thử nghiệm đó cho thấy vì sao **API by-type là lựa chọn ổn định** cho thư viện có thể nhận 0, 1 hoặc nhiều cấu hình lặp.

## 14. Các lỗi thường gặp trong dự án

| Lỗi | Nguyên nhân | Cách đúng |
|---|---|---|
| Gắn hai `@Tag` nhưng quên `@Repeatable` | Compiler không biết container hợp lệ | Thêm `@Repeatable(Tags.class)` vào kiểu `Tag` |
| Container khai báo `String[] value()` | Không đúng `Tag[]` | Khai báo `Tag[] value();` |
| Chỉ gắn `RUNTIME` trên `Tag` | Container không có retention tương thích | Gắn `@Retention(RUNTIME)` cho cả hai |
| `Tag` có `@Inherited` nhưng container thiếu | Vi phạm ràng buộc meta-annotation | Gắn `@Inherited` cho container |
| Dùng `getAnnotation(Tag.class)` để lấy nhiều tag | Phương thức không giải nén container | Dùng `getAnnotationsByType(Tag.class)` |
| Duyệt `getDeclaredAnnotations()` rồi cast trực tiếp thành `Tag` | Thường phần tử thực tế là container `Tags` | Dùng by-type hoặc kiểm tra `annotation.annotationType()` |
| Dùng `getDeclaredAnnotation(Tags.class)` và cho rằng luôn tồn tại | Chỉ một `@Tag` có thể lưu trực tiếp | Truy vấn `Tag` bằng by-type API |
| Nghĩ `getDeclaredAnnotationsByType` tự kế thừa | `Declared` chỉ lấy local annotations | Cân nhắc `getAnnotationsByType` trên class với `@Inherited` |
| Nghĩ tag trên subclass tự cộng tag từ parent | API associated inheritance là fallback | Muốn merge thì duyệt hierarchy và định nghĩa chính sách |
| Mong `@Inherited` ảnh hưởng annotation của method | Cơ chế áp dụng cho class inheritance | Tự quét overridden methods nếu cần |
| Dùng `@Target(TYPE)` rồi gắn lên field | Annotation không được phép ở vị trí đó | Chọn target phù hợp |
| Nhầm `RUNTIME` là bắt buộc cho compile-time processor | Annotation processing có thể diễn ra khi compile | Chọn retention theo nhu cầu, dùng RUNTIME cho runtime Reflection |
| Dựa vào thứ tự của danh sách mọi annotation type | Không phải contract thứ tự xử lý nghiệp vụ | Chỉ dùng thứ tự repeatables theo API khi đặc tả cần; tự thiết kế thứ tự cho metadata khác |
| Sử dụng container như một “mảng mutable” được chỉnh trong runtime | Annotation metadata không phải nơi cập nhật cấu hình đang chạy | Xử lý giá trị đọc được, không cố sửa annotation proxy/class metadata |

Một lưu ý bảo trì: khi **nâng cấp một annotation vốn không repeatable thành repeatable**, source code cũ dùng một annotation có thể vẫn hoạt động, nhưng thêm bản lặp thứ hai làm cách biểu diễn Reflection chuyển sang container. Mọi scanner cũ chỉ sử dụng `getAnnotation` hoặc `getDeclaredAnnotations` có thể phải được cập nhật.

## 15. Câu hỏi tự kiểm tra

1. Tại sao Java cần `@Repeatable(Tags.class)` dù source có thể viết hai `@Tag` trên một class?
2. Container phải có phần tử `value()` trả về kiểu gì?
3. Khi hai `@Tag` được lặp trên một class, `getDeclaredAnnotation(Tag.class)` và `getDeclaredAnnotationsByType(Tag.class)` có thể trả kết quả khác nhau ra sao?
4. Vì sao annotation phải có `@Retention(RUNTIME)` nếu framework muốn đọc qua Reflection khi chương trình đang chạy?
5. Sự khác nhau giữa `getAnnotationsByType` và `getDeclaredAnnotationsByType` khi có `@Inherited`?
6. Nếu subclass khai báo một `@Tag("local")`, Reflection có tự động trả thêm các `@Tag` của superclass không?
7. `@Inherited` có giúp method override tự kế thừa annotation của method bị override không?
8. Khi nào cần dùng `@Target({TYPE, METHOD})` trên cả repeatable type và container?
9. Nếu một method có hai `@Tag` nhưng chương trình chỉ thấy một `@Tags` qua `getDeclaredAnnotations()`, đây có phải lỗi compiler không?
10. Để đọc cấu hình repeatable ổn định với 0/1/n annotation, nên gọi API nào?
11. Khi nào khai báo `@Tags({@Tag("A"), @Tag("B")})` tường minh có cùng hiệu quả đọc by-type với hai `@Tag` liên tiếp?
12. Với framework cần gộp annotation trên class con và nhiều superclass, vì sao phải tự thiết kế logic merge?

**Ghi nhớ:** `@Repeatable(Container.class)` cho phép **nhiều annotation cùng kiểu trên một element**; container chứa chúng trong mảng `value()`. Muốn đọc tất cả các giá trị đúng cách bằng Java Reflection, ưu tiên `getAnnotationsByType` hoặc `getDeclaredAnnotationsByType`. Hãy xem xét riêng retention, target và inheritance; đừng nhầm annotation gián tiếp trong container với annotation hiện diện trực tiếp.

Tài liệu chính thức Java 17: [`Repeatable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Repeatable.html), [`AnnotatedElement`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html), [`Inherited`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html), [`Retention`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Retention.html), [JLS `9.6.3 — Repeatable Annotation Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.3) và [JLS `9.7.5 — Multiple Annotations of the Same Interface](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.5).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-008">getAnnotationsByType khác getDeclaredAnnotationsByType</a>

<details>
<summary>Xem chi tiết</summary>

# getAnnotationsByType khác getDeclaredAnnotationsByType trong Java Reflection

## 1. Vấn đề: tìm annotation theo loại không chỉ là đọc danh sách annotation

Khi sử dụng Java Reflection, một lớp, phương thức hoặc field có thể mang **runtime annotation**. Nếu annotation được khai báo lặp lại nhờ `@Repeatable`, một phần tử có thể có nhiều annotation cùng loại. Nếu annotation được đánh dấu `@Inherited`, một **class** có thể quan sát annotation từ superclass khi không khai báo annotation cùng loại ở chính mình.

Hai API trong `java.lang.reflect.AnnotatedElement` giải quyết hai phạm vi khác nhau:

- `getAnnotationsByType(Class<T> annotationClass)`: tìm tất cả annotation **associated** của một loại tại phần tử. Với `Class`, kết quả có thể bao gồm annotation kế thừa từ superclass theo luật `@Inherited`.
- `getDeclaredAnnotationsByType(Class<T> annotationClass)`: tìm tất cả annotation của loại đó **directly present** hoặc **indirectly present** tại **chính phần tử được truy vấn**, không lấy từ superclass.

Điểm dễ nhầm là cả hai đều hỗ trợ **repeatable annotation** bằng cách mở rộng (*unpack*) container annotation. Khác biệt quan trọng của chúng là **ngữ nghĩa kế thừa**, chứ không phải “một phương thức hiểu repeatable, phương thức kia không”.

> Trong bài, “declared” nghĩa là metadata nằm trên phần tử hiện tại; nó **không** nghĩa là annotation có visibility `private`, và cũng không tương đương với `getDeclaredAnnotations()` vì phương thức không có `ByType` thường không tự mở rộng container.

## 2. Bốn khái niệm: trực tiếp, gián tiếp, present, associated

Để hiểu chính xác hai API, cần phân biệt các trạng thái annotation:

| Thuật ngữ | Ý nghĩa thực tế | Ví dụ |
| --- | --- | --- |
| **Directly present** | Annotation của đúng loại xuất hiện trực tiếp trong metadata runtime của phần tử | `@Label("x")` khai báo một lần |
| **Indirectly present** | Annotation được chứa trong **containing annotation** của một loại `@Repeatable` | Hai `@Label` được compiler đóng vào `@Labels` |
| **Present** | Directly present hoặc annotation kế thừa theo quy tắc `@Inherited` áp dụng cho `Class` | `Child` kế thừa một annotation hiện diện trên `Parent` |
| **Associated** | Directly present, indirectly present, hoặc được kế thừa theo quy tắc áp dụng nếu trên phần tử hiện tại không có annotation của loại đó theo cách trực tiếp/gián tiếp | `Child.class.getAnnotationsByType(Label.class)` có thể trả các `Label` ở `Parent` |

Cụ thể:

- `getDeclaredAnnotationsByType(T.class)` trả các annotation loại `T` **trực tiếp và gián tiếp** trên phần tử đang xét.
- `getAnnotationsByType(T.class)` trả các annotation loại `T` **associated** với phần tử; nếu phần tử là `Class` và `T` có `@Inherited`, phương thức có thể xét superclass.
- Nếu class con **đã có** annotation loại `T` trực tiếp hoặc gián tiếp, kết quả của `getAnnotationsByType` ưu tiên tập **cục bộ** này, **không tự gộp** với tập cùng loại ở superclass.
- `@Inherited` không làm annotation trên **method, field hoặc interface** tự được kế thừa theo cùng cách như annotation trên class.

### Repeatable annotation và container

Với kiểu annotation lặp lại:

```java
@Repeatable(Labels.class)
@interface Label {
    String value();
}

@interface Labels {
    Label[] value();
}
```

Đây là phần minh họa cú pháp, chưa có `@Retention(RUNTIME)` nên **chưa đủ** để đọc qua Reflection ở runtime. Phần ví dụ runnable bên dưới bổ sung đầy đủ metadata.

Hai annotation:

```java
@Label("alpha")
@Label("beta")
class Service {}
```

thường được thể hiện trong class file bằng **container** `@Labels` với mảng `value()` chứa hai `@Label`. Do đó:

- `Service.class.getDeclaredAnnotationsByType(Label.class)` thấy hai `Label`.
- `Service.class.getDeclaredAnnotation(Label.class)` thường trả `null` vì runtime metadata lưu **container `Labels`**, không lưu hai `Label` như hai annotation trực tiếp độc lập.
- `Service.class.getDeclaredAnnotation(Labels.class)` thấy container `Labels`.
- `Service.class.getDeclaredAnnotations()` có thể cho biết container `Labels`, không tự bung ra thành hai `Label`.

Đây là lý do cần sử dụng API có hậu tố **`ByType`** khi muốn lấy danh sách các annotation repeatable theo **loại annotation bên trong**, thay vì tự dò và unpack container.

## 3. So sánh nhanh các API Reflection

| API | Loại trả về | Mở rộng annotation repeatable? | Xét kế thừa? |
| --- | --- | --- | --- |
| `getAnnotation(T.class)` | `T` hoặc `null` | Không phải API chuyên để unpack tất cả phần tử trong container | Có thể, theo `@Inherited` và luật present trên `Class` |
| `getDeclaredAnnotation(T.class)` | `T` hoặc `null` | Không tự trả nhiều annotation được chứa trong container | Không |
| `getAnnotationsByType(T.class)` | `T[]` | **Có** | **Có thể**, với `Class` và `@Inherited` |
| `getDeclaredAnnotationsByType(T.class)` | `T[]` | **Có** | **Không** |
| `getAnnotations()` | `Annotation[]` | Không tự chuyển container thành nhiều `T` | Có thể phản ánh annotation kế thừa theo quy tắc runtime |
| `getDeclaredAnnotations()` | `Annotation[]` | Không tự chuyển container thành nhiều `T` | Không |

**Dấu hiệu lựa chọn:** khi đọc một repeatable annotation từ class và muốn xem annotation hiệu lực sau kế thừa, dùng `getAnnotationsByType`. Khi viết công cụ phân tích **đúng annotation đặt trên class hiện tại**, dùng `getDeclaredAnnotationsByType`.

Các API có `Declared` không đồng nghĩa “chỉ trả annotation thuộc class chưa biên dịch”; `Class`, `Method`, `Field` và các đối tượng `AnnotatedElement` khác đều có thể cung cấp các phương thức này theo phạm vi tương ứng.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu nguyên chương trình sau vào **`AnnotationByTypeDemo.java`**. Chương trình có annotation `Label` vừa `@Repeatable` vừa `@Inherited`, và container `Labels` cũng có retention runtime/`@Inherited`. Các class kiểm thử gồm:

- `Parent`: hai nhãn lặp lại.
- `Child`: không khai báo `Label` riêng, nên có thể thấy nhãn từ superclass khi dùng API associated.
- `OwnChild`: một nhãn riêng, che tập nhãn inherited của `Parent` đối với truy vấn by-type.
- `TwoChild`: hai nhãn riêng, kiểm tra container local.
- `ContainerChild`: khai báo trực tiếp container `@Labels`.
- `Implementation`: triển khai interface đã đánh dấu, chứng minh `@Inherited` không chạy qua interface.

```java
import java.lang.annotation.*;
import java.util.*;
import java.util.stream.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Repeatable(Labels.class)
@interface Label {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@interface Labels {
    Label[] value();
}

@Label("parent-a")
@Label("parent-b")
class Parent {}

class Child extends Parent {}

@Label("child")
class OwnChild extends Parent {}

@Label("local-a")
@Label("local-b")
class TwoChild extends Parent {}

@Labels({@Label("explicit-a"), @Label("explicit-b")})
class ContainerChild extends Parent {}

@Label("interface-only")
interface Contract {}

class Implementation implements Contract {}

public class AnnotationByTypeDemo {
    private static String names(Label[] labels) {
        return Arrays.stream(labels)
                .map(Label::value)
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static void describe(Class<?> type) {
        System.out.println(type.getSimpleName()
                + ": all=" + names(type.getAnnotationsByType(Label.class))
                + " declared="
                + names(type.getDeclaredAnnotationsByType(Label.class)));
    }

    public static void main(String[] args) {
        describe(Parent.class);
        describe(Child.class);
        describe(OwnChild.class);
        describe(TwoChild.class);
        describe(ContainerChild.class);
        describe(Implementation.class);

        System.out.println("parent direct single: "
                + (Parent.class.getDeclaredAnnotation(Label.class) != null));
        System.out.println("parent declared container: "
                + (Parent.class.getDeclaredAnnotation(Labels.class) != null));
        System.out.println("child inherited container: "
                + (Child.class.getAnnotation(Labels.class) != null));
        System.out.println("child declared container: "
                + (Child.class.getDeclaredAnnotation(Labels.class) != null));
    }
}
```

Biên dịch bằng **Java 17** hoặc JDK mới hơn:

```bash
javac --release 17 AnnotationByTypeDemo.java
java AnnotationByTypeDemo
```

**Output chính xác** khi chạy chương trình độc lập:

```text
Parent: all=[parent-a, parent-b] declared=[parent-a, parent-b]
Child: all=[parent-a, parent-b] declared=[]
OwnChild: all=[child] declared=[child]
TwoChild: all=[local-a, local-b] declared=[local-a, local-b]
ContainerChild: all=[explicit-a, explicit-b] declared=[explicit-a, explicit-b]
Implementation: all=[] declared=[]
parent direct single: false
parent declared container: true
child inherited container: true
child declared container: false
```

Hàm `names` sắp xếp nhãn theo tên để output ổn định và dễ đọc, không phụ thuộc vào cách trình bày thứ tự của các annotation trong ví dụ. Khi cần bảo toàn thứ tự xuất hiện của các annotation repeatable, không được tùy tiện sắp xếp kết quả như hàm trình diễn này.

### 4.1. Parent: hai API đều trả hai annotation

`Parent` tự khai báo hai `@Label`. Trong metadata runtime, compiler dùng container `@Labels` cho tập lặp lại. Hai API `getAnnotationsByType(Label.class)` và `getDeclaredAnnotationsByType(Label.class)` đều unpack container thành hai `Label`. Bởi vậy cả `all` và `declared` đều là `[parent-a, parent-b]`.

### 4.2. Child: chỉ API không có Declared mới kế thừa

`Child extends Parent` nhưng không khai báo `@Label`. Do `Label` có `@Inherited`, lời gọi:

```java
Child.class.getAnnotationsByType(Label.class)
```

trả các nhãn associated từ superclass: `[parent-a, parent-b]`.

Trong khi đó:

```java
Child.class.getDeclaredAnnotationsByType(Label.class)
```

trả **mảng rỗng**, vì `Child` không khai báo `Label` trực tiếp hay gián tiếp. Hai API khác nhau về **phạm vi**, không khác nhau về khả năng unpack container.

### 4.3. OwnChild: annotation cục bộ không hợp nhất với superclass

`OwnChild` có `@Label("child")` trực tiếp. Vì đã có `Label` cục bộ, `getAnnotationsByType(Label.class)` trả `[child]`, **không** trả `[parent-a, parent-b, child]`. Phương thức declared cũng trả `[child]`.

Đây là khác biệt thực tế với một số kỳ vọng “annotation từ các tầng được gộp tất cả”. Đối với **một loại repeatable annotation**, quy tắc `@Inherited` của truy vấn associated không tự thêm annotation của superclass khi class hiện tại đã có annotation loại đó.

Nếu nghiệp vụ thật sự muốn tập annotation **gộp từ mọi lớp trong hierarchy**, cần tự duyệt `getSuperclass()` và áp dụng chính sách gộp, xung đột, thứ tự theo yêu cầu ứng dụng.

### 4.4. TwoChild: có nhiều annotation cục bộ

`TwoChild` khai báo hai `@Label`. Cả hai API trả đúng hai annotation cục bộ; các nhãn ở `Parent` không được thêm. `getDeclaredAnnotationsByType` trả được cả hai dù class file chứa container chứ không nhất thiết chứa hai annotation `Label` tách rời.

### 4.5. ContainerChild: khai báo container cũng được unpack

`ContainerChild` viết trực tiếp:

```java
@Labels({@Label("explicit-a"), @Label("explicit-b")})
```

Mỗi `Label` bên trong được xem là *indirectly present* theo luật repeatable. Hai API by-type đều trả `[explicit-a, explicit-b]`. Vì vậy **không nhất thiết** người viết source phải dùng cú pháp `@Label` lặp lại; container hợp lệ cũng được nhận diện.

### 4.6. Implementation: @Inherited không chạy qua interface

`Contract` là một interface có `@Label("interface-only")`. `Implementation implements Contract` **không** kế thừa annotation qua interface trong luật `@Inherited`. Vì vậy cả hai truy vấn của `Implementation.class` trả mảng rỗng.

`@Inherited` chỉ có hiệu lực khi tìm annotation trên **class**, đi theo quan hệ superclass thích hợp; không biến interface annotation thành annotation mặc định cho mọi class triển khai.

### 4.7. Vì sao getDeclaredAnnotation(Label.class) ở Parent trả null?

Hai `@Label` trên `Parent` đã được biểu diễn bằng containing annotation `@Labels`. Do đó `Parent.class.getDeclaredAnnotation(Label.class)` không nhìn thấy một annotation `Label` **directly present** đơn lẻ và trả `null`. Nhưng `getDeclaredAnnotation(Labels.class)` trả container thật, nên in `true`.

`Child.class.getAnnotation(Labels.class)` là `true` bởi container `Labels` cũng có `@Inherited`, trong khi `getDeclaredAnnotation(Labels.class)` ở Child là `null`. Cùng một hiện tượng nhưng đối với **loại annotation container**.

**Cảnh báo:** Nếu chỉ gọi `getAnnotation(Label.class)` khi annotation repeatable xuất hiện hai lần, bạn có thể tưởng rằng annotation không tồn tại. Hãy dùng `getAnnotationsByType(Label.class)` nếu muốn lấy toàn bộ instance của loại repeatable.

## 5. Kiểm tra retention trước khi so sánh hai API

Cả `Label` và `Labels` trong chương trình đều có `@Retention(RUNTIME)` để runtime Reflection nhìn thấy repeatable annotation. Nếu annotation không có retention runtime phù hợp, cả hai phương thức `ByType` có thể trả mảng rỗng; đó là lỗi về metadata visibility, không chứng tỏ một API xử lý container khác API còn lại. Các quy tắc khai báo container thuộc bài repeatable trước.

## 6. Phạm vi kế thừa trong hai API `ByType`

`getAnnotationsByType(T.class)` có thể tìm tới superclass **khi phần tử là class**, annotation type `T` có `@Inherited`, và class hiện tại không có annotation loại `T` trực tiếp/gián tiếp. `getDeclaredAnnotationsByType(T.class)` luôn giới hạn ở phần tử hiện tại.

Điều đó **không** làm annotation từ interface được implements, method được override hoặc field bị hide tự xuất hiện trên phần tử mới. Nếu `getMethod()` trả method gốc khai báo ở superclass, annotation đọc được vẫn thuộc chính `Method` đó. Khi framework muốn gộp từ nhiều lớp/interface hoặc method hierarchy, cần chính sách traversal và giải quyết xung đột riêng.

## 7. Quy tắc chọn API theo mục đích

| Nhu cầu thực tế | API phù hợp | Lý do |
| --- | --- | --- |
| Đọc tất cả `@Label` có hiệu lực trên một class, kể cả inherited theo luật Java | `clazz.getAnnotationsByType(Label.class)` | Kết quả associated, hỗ trợ repeatable và `@Inherited` |
| Chỉ đọc `@Label` viết trên chính class này | `clazz.getDeclaredAnnotationsByType(Label.class)` | Không đi ngược superclass |
| Tìm tất cả annotation của một loại repeatable ở method hiện tại | `method.getDeclaredAnnotationsByType(Label.class)` hoặc `getAnnotationsByType` tùy ngữ nghĩa | Không tự kế thừa annotation từ method override |
| Kiểm tra có một annotation **trực tiếp** kiểu `T` hay không | `getDeclaredAnnotation(T.class)` | Nhưng không đủ khi repeatable được đóng trong container |
| Xem container vật lý của repeatable annotation | `getDeclaredAnnotation(Container.class)` | Kiểm tra container được khai báo |
| Duyệt toàn bộ annotation metadata được declared, kể cả loại khác | `getDeclaredAnnotations()` | Trả danh sách annotation metadata; không thay thế by-type để unpack |
| Muốn gộp annotation của **mọi lớp** trong inheritance chain, dù lớp con đã có | Tự duyệt `getSuperclass()` và `getDeclaredAnnotationsByType` | Quy tắc by-type associated không tự gộp các tầng |
| Muốn lấy annotation từ interface hoặc overridden methods theo chính sách framework | Tự duyệt hierarchy và chọn quy tắc xung đột | `@Inherited` không đáp ứng yêu cầu này |

### Mẫu đọc metadata class cục bộ

```java
static Label[] localLabels(Class<?> clazz) {
    return clazz.getDeclaredAnnotationsByType(Label.class);
}
```

### Mẫu đọc các annotation associated

```java
static Label[] effectiveLabels(Class<?> clazz) {
    return clazz.getAnnotationsByType(Label.class);
}
```

Hai đoạn là method Java minh họa có thể đặt trong một class thích hợp, sử dụng `Label` được khai báo trong ví dụ chính. Hãy đặt tên `localLabels` hoặc `effectiveLabels` có chủ ý; một tên chung như `findLabels` thường khiến người đọc không biết hàm có bao gồm inherited hay không.

## 8. Nếu muốn kết quả gộp từ tất cả superclass thì phải tự duyệt

Nhiều framework muốn **kết hợp** các annotation từ class hiện tại và mọi superclass, ngay cả khi lớp hiện tại có annotation cùng loại. `getAnnotationsByType` **không** đáp ứng đầy đủ quy tắc gộp này.

Ví dụ một tiện ích nhận các instance cục bộ theo thứ tự từ class con lên class cha:

```java
static java.util.List<Label> collectEveryLevel(Class<?> type) {
    java.util.List<Label> result = new java.util.ArrayList<>();

    for (Class<?> current = type;
         current != null;
         current = current.getSuperclass()) {

        Label[] local =
                current.getDeclaredAnnotationsByType(Label.class);
        java.util.Collections.addAll(result, local);
    }
    return result;
}
```

Đây là một **phương thức Java minh họa** sử dụng `Label` đã khai báo. Đặt nó trong một class hoặc tiện ích của ứng dụng. Với `OwnChild.class`, tiện ích này thu được `child`, `parent-a`, `parent-b` theo thứ tự tầng và thứ tự các annotation trả về ở mỗi tầng.

Điểm cần ghi nhớ:

- Đây là **quy tắc ứng dụng tự định nghĩa**, không phải hành vi mặc định của `getAnnotationsByType`.
- Có thể cần loại trùng, cho lớp con ưu tiên, cho phép override hoặc hợp nhất giá trị annotation theo domain.
- Ví dụ trên duyệt superclass, **không** duyệt interface. Muốn interface phải thêm logic riêng, tránh lặp trong đồ thị interface và quyết định thứ tự.
- Không chọn `getAnnotationsByType` ở mỗi tầng nếu định gộp trực tiếp: annotation inherited có thể bị đếm lại ở nhiều tầng. `getDeclaredAnnotationsByType` thích hợp hơn để thu **chỉ metadata mỗi tầng**.

## 9. Phần khác biệt với getDeclaredAnnotations()

Khi có một `@Repeatable(Label)`, nhiều người tìm bằng:

```java
for (Annotation a : clazz.getDeclaredAnnotations()) {
    System.out.println(a.annotationType());
}
```

Nếu repeatable `Label` xuất hiện hai lần, danh sách có thể chứa **`Labels`**, là container, chứ không phải hai object `Label` được phẳng hóa. Mặt khác, `getDeclaredAnnotationsByType(Label.class)` được thiết kế để trả hai `Label`.

Vì thế:

- Nếu muốn **mọi annotation metadata loại bất kỳ**, `getDeclaredAnnotations()` hợp lý.
- Nếu muốn **tất cả instance của một loại repeatable cụ thể**, dùng `getDeclaredAnnotationsByType(T.class)`.
- Nếu muốn **tất cả instance liên quan tới một loại, bao gồm thừa kế phù hợp**, dùng `getAnnotationsByType(T.class)`.

Tương tự, `getAnnotations()` là danh sách các annotation hiện diện theo ngữ nghĩa API đó, **không** phải hàm luôn flatten mọi container repeatable thành các phần tử bên trong. Không dùng `getAnnotations().length` để suy luận chắc chắn số repeatable annotations.

## 10. Một số điểm dễ gây hiểu lầm

### 10.1. Có @Inherited nhưng class con vẫn không thấy annotation

Hãy kiểm tra bốn điều:

1. Annotation thật sự có `@Retention(RUNTIME)` không?
2. Annotation loại chính có `@Inherited` không? Nếu repeatable, container được khai báo nhất quán chưa?
3. Phần tử đang truy vấn có thật là **class subclass** theo quan hệ `extends` không, hay chỉ `implements` một interface?
4. Class con có annotation cùng loại **cục bộ** không? Nếu có, phương thức by-type associated trả tập local, không cộng tập superclass.

### 10.2. Có nhiều @Label nhưng getAnnotation(Label.class) trả null

Đó là tình huống thường gặp khi compiler dùng container `@Labels` cho repeatable annotation. Dùng `getAnnotationsByType(Label.class)` hoặc `getDeclaredAnnotationsByType(Label.class)` mới là cách truy xuất nhiều instance.

### 10.3. Không có Declared nghĩa là luôn đọc hết mọi ancestor?

**Không.** `getAnnotationsByType` hỗ trợ ngữ nghĩa `@Inherited` của class, nhưng khi có tập annotation cùng loại local, không tự gộp các tập ở superclass. Khi không có `@Inherited`, nó cũng không tự “quét toàn bộ hierarchy” để trả mọi annotation.

### 10.4. getDeclaredAnnotationsByType chỉ đọc annotation không lặp?

**Sai.** Nó cũng hỗ trợ repeatable annotation, gồm annotation **indirectly present** bên trong container. “Declared” giới hạn **phần tử**, không giới hạn **số lượng annotations**.

### 10.5. Thêm @Inherited trên class được annotate là đủ?

**Sai.** `@Inherited` là meta-annotation dành cho khai báo loại annotation, không phải modifier tùy ý thêm vào `Child` để kích hoạt kế thừa.

### 10.6. @Inherited có tác dụng lên method override?

**Không.** Kế thừa metadata method theo cách tùy chỉnh là công việc của framework. Java Reflection không tự sao chép annotation từ method ở superclass sang method override.

### 10.7. Phụ thuộc thứ tự mảng trả về?

Với repeatable annotations, thứ tự local có thể mang ý nghĩa theo container/source. Hãy xác định quy tắc thứ tự nếu dùng kết quả cho pipeline. Ví dụ chạy ở phần 4 **sắp xếp** để tạo stdout ổn định, không chứng minh rằng JVM luôn trả mảng theo thứ tự từ điển.

### 10.8. Đã compile có annotation thì Reflection phải thấy?

**Không.** Retention `SOURCE`/`CLASS` không đảm bảo runtime Reflection thấy annotation. Nếu số lượng trả về là zero, kiểm tra `@Retention(RUNTIME)` trước khi kết luận API lỗi.

## 11. Lưu ý khi thiết kế framework

Một framework có thể cần hai hành vi khác nhau:

**Chính sách A — inherited, ưu tiên class gần nhất:** Lấy `getAnnotationsByType(T.class)` từ class cần xử lý. Đây gần với ngữ nghĩa annotation associated được Java định nghĩa; phù hợp khi muốn class con có thể thay thế metadata parent bằng giá trị cục bộ.

**Chính sách B — thu mọi tầng:** Duyệt `getSuperclass()` và lấy `getDeclaredAnnotationsByType(T.class)` trên từng class. Đây là chính sách độc lập của framework; cần quyết định merge, order và override rõ ràng.

Nếu annotation nằm trên method hoặc interface và framework muốn kế thừa theo kiểu riêng (ví dụ đọc route annotation ở interface), nên viết thuật toán traversal chuyên biệt. Đừng hy vọng rằng `@Inherited` hoặc `getAnnotationsByType` sẽ làm điều đó cho mọi phần tử.

Việc “effective annotation” ở một số framework như công cụ dependency injection hoặc web routing có thể có ngữ nghĩa **khác** Java Reflection chuẩn (tìm meta-annotation, composed annotation, alias, multiple levels). Khi cần đúng hành vi framework, dùng API của framework; khi cần đúng luật Java Reflection, dựa trên `AnnotatedElement` và `@Inherited`.

## 12. Tự kiểm tra

**Câu 1.** Trên một class có hai `@Label`, `getDeclaredAnnotationsByType(Label.class)` có trả cả hai không?

**Đáp án:** Có. API mở rộng repeatable container để lấy annotation indirectly present trên chính class.

**Câu 2.** `Child extends Parent` không khai báo `Label`, `Label` có `@Inherited`. Hai API trả gì?

**Đáp án:** `Child.class.getAnnotationsByType(Label.class)` có thể trả các `Label` từ Parent; `Child.class.getDeclaredAnnotationsByType(Label.class)` trả rỗng.

**Câu 3.** Nếu `Child` tự khai báo một `@Label("child")`, `getAnnotationsByType` có trả cả Label ở Parent không?

**Đáp án:** Không tự gộp. Tập local của Child được ưu tiên cho loại annotation đó.

**Câu 4.** Vì sao `getDeclaredAnnotation(Label.class)` trên Parent có hai `@Label` có thể trả null?

**Đáp án:** Compiler biểu diễn các annotation lặp lại bằng containing annotation `Labels`; `getDeclaredAnnotation` không phải API flatten nhiều repeatable annotations.

**Câu 5.** `Implementation implements Contract` có tự nhận `@Label` từ interface `Contract` nhờ `@Inherited` không?

**Đáp án:** Không. `@Inherited` không truyền annotation qua `implements`.

**Câu 6.** Phương thức override có tự kế thừa annotation của method superclass khi gọi `getAnnotationsByType` trên `Method` con không?

**Đáp án:** Không. Quy tắc `@Inherited` của Java Reflection áp dụng cho class, không chuyển annotation của method cha sang method override.

**Câu 7.** Vì sao có annotation trong source nhưng hai API by-type đều trả mảng rỗng?

**Đáp án:** Có thể annotation thiếu `@Retention(RUNTIME)`, truy vấn nhầm phần tử/loại, class con không có annotation cục bộ và annotation không `@Inherited`, hoặc chỉ tồn tại trên interface/method khác.

**Câu 8.** Muốn lấy tất cả `Label` ở cả Child và Parent, ngay cả khi Child đã khai báo riêng, dùng gì?

**Đáp án:** Duyệt hierarchy theo `getSuperclass()` và lấy `getDeclaredAnnotationsByType(Label.class)` ở mỗi tầng, sau đó áp dụng quy tắc kết hợp của ứng dụng.

**Ghi nhớ:** **Cả `getAnnotationsByType` và `getDeclaredAnnotationsByType` đều hiểu repeatable annotation**. API có `Declared` chỉ trả những annotation trực tiếp hoặc gián tiếp **trên chính phần tử**; API không có `Declared` có thể áp dụng **`@Inherited` theo superclass khi truy vấn class**, nhưng **không mặc định hợp nhất** tập annotation local với tập ở mọi superclass. Muốn gộp theo chính sách khác, hãy duyệt hierarchy có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-009">Đọc giá trị phần tử annotation qua phương thức của annotation type</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc giá trị phần tử annotation qua phương thức của annotation type

## 1. Annotation element là phương thức không tham số

Trong Java, annotation type được khai báo bằng `@interface`. Bên trong có thể khai báo các **annotation elements** dưới dạng phương thức **không có tham số**, trả về kiểu được ngôn ngữ cho phép. Ví dụ:

~~~java
@interface Route {
    String value();
    int priority() default 1;
}
~~~

`value()` và `priority()` không phải các field public cũng không phải những phương thức nghiệp vụ có thân hàm. Chúng là các **element declaration** của annotation type. Khi một class hay method được gắn annotation:

~~~java
@Route(value = "/orders", priority = 5)
class OrderEndpoint {
}
~~~

Java lưu các cặp tên–giá trị annotation thích hợp trong metadata của phần tử được đánh dấu. Nếu annotation có **`RetentionPolicy.RUNTIME`**, Reflection có thể đọc một instance annotation và gọi **phương thức element của annotation đó** để lấy giá trị:

~~~java
Route route = OrderEndpoint.class.getAnnotation(Route.class);
if (route != null) {
    System.out.println(route.value());    // /orders
    System.out.println(route.priority()); // 5
}
~~~

Các mảnh ví dụ này giải thích nguyên lý; chương trình Java 17 hoàn chỉnh ở phần 4 có khai báo import, retention và tất cả kiểu liên quan.

Điểm cốt lõi: **Đọc giá trị annotation là gọi element method trên annotation instance**. Không cần tìm một field có cùng tên, không dùng `Field.get`, và bản thân thao tác đọc metadata **không thực hiện nghiệp vụ** được mô tả bởi annotation.

## 2. Hai đối tượng cần phân biệt: annotation type và annotation instance

Giả sử có `@Rule("checkout")` gắn trên `Checkout.class`:

- **Annotation type** là `Rule.class`. Nó là đối tượng `Class<Rule>` đại diện định nghĩa `@interface Rule`, chứa các element method khai báo như `String value()` và `int retries() default 3`.
- **Annotation instance** là giá trị `Rule rule = Checkout.class.getAnnotation(Rule.class)`, đại diện thông tin annotation gắn trên lớp `Checkout`. Nó triển khai interface annotation và cung cấp giá trị của các element.
- **Đối tượng được annotation đánh dấu** là `Checkout.class`, không phải `rule`. Metadata thuộc về phần tử được gắn annotation (class, method, field, parameter, ...).

Luồng xử lý thường gặp:

~~~text
Định nghĩa @interface Rule
       |
       v
Dùng @Rule(...) trên một phần tử
       |
       v
Class file có annotation metadata (nếu retention cho phép)
       |
       v
Reflection: element.getAnnotation(Rule.class)
       |
       v
Rule annotation = ...
       |
       +-- annotation.value()
       +-- annotation.retries()
       +-- annotation.tags()
       +-- annotation.owner().name()
~~~

**Sai lầm hay gặp:** viết `Rule.class.getDeclaredMethod("retries")` rồi nghĩ chỉ có object `Method` đã chứa giá trị `5` của annotation trên `Checkout`. Thực tế `Method` đó là mô tả **element method** dùng chung cho cả annotation type. Muốn đọc **giá trị tại nơi sử dụng cụ thể**, phải gọi method trên đúng **annotation instance**. `Method.getDefaultValue()` lại là một API khác: nó mô tả **giá trị mặc định đã khai báo ở annotation type**, không phải giá trị override tại một nơi sử dụng.

## 3. Những kiểu trả về hợp lệ của annotation element

Một annotation element chỉ có thể trả về một trong những loại sau:

1. Kiểu primitive: `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`.
2. `String`.
3. `Class` (kể cả các dạng `Class<?>` được ngôn ngữ cho phép).
4. Một kiểu `enum`.
5. Một kiểu annotation khác (nested annotation value).
6. Mảng **một chiều** của một trong các loại trên.

Bảng ví dụ:

| Khai báo element | Cách dùng tại annotation | Kiểu nhận được khi đọc |
| --- | --- | --- |
| `String value();` | `@Rule("checkout")` | `String` |
| `int retries() default 3;` | `@Rule(retries = 5, ...)` | `int` |
| `boolean enabled() default true;` | `enabled = false` | `boolean` |
| `Level level() default Level.LOW;` | `level = Level.HIGH` | `Level` |
| `Class<?> handler() default String.class;` | `handler = Integer.class` | `Class<?>` |
| `String[] tags() default {};` | `tags = {"fast", "safe"}` | `String[]` |
| `Owner owner() default @Owner(name = "system");` | `owner = @Owner(name = "core")` | `Owner` |

Java không chấp nhận một annotation element trả về `List<String>`, `Map`, `Object`, mảng nhiều chiều hay kiểu được phép khác dưới dạng tổng quát không hợp lệ. Thay vì trả `List<String>`, khai báo `String[]` và chuyển sang collection trong code ứng dụng nếu cần.

Ngoài ra, **`null` không phải giá trị annotation element hợp lệ**. Element bắt buộc được chỉ rõ tại nơi sử dụng nếu không có default, hoặc lấy default khi bỏ qua. Với element dạng `String[]`, mảng rỗng `{}` khác với `null`. Element method không khai báo tham số hay `throws` clause, và không có thân thực thi như method thông thường.

## 4. Ví dụ Java 17 hoàn chỉnh

Tạo file **`AnnotationElementsDemo.java`**. Chương trình định nghĩa annotation với nhiều loại element, gắn một annotation có giá trị tường minh và một annotation dùng default, rồi đọc bằng phương thức của annotation type.

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;

enum Level {
    LOW, HIGH
}

@Retention(RetentionPolicy.RUNTIME)
@interface Owner {
    String name();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Rule {
    String value();
    int retries() default 3;
    Level level() default Level.LOW;
    Class<?> handler() default String.class;
    String[] tags() default {};
    Owner owner() default @Owner(name = "system");
}

@Rule(
        value = "checkout",
        retries = 5,
        level = Level.HIGH,
        handler = Integer.class,
        tags = {"fast", "safe"},
        owner = @Owner(name = "core"))
class Checkout {
}

@Rule("fallback")
class Fallback {
}

public class AnnotationElementsDemo {
    public static void main(String[] args) throws Exception {
        Rule rule = Checkout.class.getAnnotation(Rule.class);

        System.out.println("present=" + (rule != null));
        System.out.println("annotationType="
                + rule.annotationType().getSimpleName());
        System.out.println("value=" + rule.value());
        System.out.println("retries=" + rule.retries());
        System.out.println("level=" + rule.level());
        System.out.println("handler=" + rule.handler().getSimpleName());
        System.out.println("tags=" + Arrays.toString(rule.tags()));
        System.out.println("owner=" + rule.owner().name());

        Rule fallback = Fallback.class.getAnnotation(Rule.class);
        System.out.println("fallback-retries=" + fallback.retries());
        System.out.println("fallback-level=" + fallback.level());
        System.out.println("fallback-handler="
                + fallback.handler().getSimpleName());
        System.out.println("fallback-tags="
                + Arrays.toString(fallback.tags()));
        System.out.println("fallback-owner=" + fallback.owner().name());

        Method retriesMethod = Rule.class.getDeclaredMethod("retries");
        System.out.println("reflective-retries="
                + retriesMethod.invoke(rule));
        System.out.println("declared-default="
                + retriesMethod.getDefaultValue());

        System.out.println("missing="
                + (String.class.getAnnotation(Rule.class) == null));
    }
}
~~~

Biên dịch/chạy từ thư mục chứa file:

~~~bash
javac --release 17 AnnotationElementsDemo.java
java AnnotationElementsDemo
~~~

Output:

~~~text
present=true
annotationType=Rule
value=checkout
retries=5
level=HIGH
handler=Integer
tags=[fast, safe]
owner=core
fallback-retries=3
fallback-level=LOW
fallback-handler=String
fallback-tags=[]
fallback-owner=system
reflective-retries=5
declared-default=3
missing=true
~~~

Hành vi của ví dụ đã được xác minh qua Java Compiler ở mức Java 17 và chạy thử logic tương đương trong JShell. Bài dùng các annotation ở package mặc định để không cần file `module-info.java`; trong dự án thực tế nên khai báo package rõ ràng.

### Phân tích output

1. `@Retention(RUNTIME)` trên `Rule` khiến `Checkout.class.getAnnotation(Rule.class)` có thể lấy annotation được khai báo, nên `rule != null` là `true`. `annotationType()` trả `Rule.class`.
2. `rule.value()` là `"checkout"`, chính giá trị ghi trong `@Rule(value = "checkout", ...)`. `rule.retries()` trả primitive `int` bằng 5, không lấy default 3 vì annotation tại `Checkout` có ghi đè.
3. `rule.level()` trả enum `Level.HIGH`; `rule.handler()` trả `Integer.class`; `rule.tags()` trả mảng `String[]` chứa hai giá trị; `rule.owner()` trả **annotation instance lồng nhau** kiểu `Owner`, rồi `.name()` trả `"core"`.
4. `@Rule("fallback")` là cú pháp viết gọn cho element mang tên `value`. Vì các element khác đều có `default`, có thể bỏ qua chúng khi gắn annotation trên `Fallback`. Do đó `fallback.retries()` nhận 3, `fallback.tags()` trả mảng rỗng và `fallback.owner().name()` là `"system"`.
5. `Rule.class.getDeclaredMethod("retries")` lấy `Method` mô tả element method. `retriesMethod.invoke(rule)` trả **giá trị tại annotation instance `rule`**, tức 5; vì gọi qua `Method.invoke` có kiểu trả về `Object`, primitive `int` được boxing thành `Integer` trong quá trình phản chiếu.
6. `retriesMethod.getDefaultValue()` đọc **default trên khai báo element** (`3`). Nó không thay đổi theo nơi annotation được gắn. Nếu element không khai báo default, API này trả `null`.
7. `String.class.getAnnotation(Rule.class)` trả `null` vì annotation `Rule` không hiện diện trên `String.class` theo quy tắc tìm kiếm tương ứng. Code thực tế nên luôn xử lý tình huống annotation vắng mặt.

Đọc annotation element **không gọi một handler nghiệp vụ** vì nó chỉ trả về metadata. Ví dụ `handler = Integer.class` lưu một class literal; nó **không tạo `Integer` instance**, không khởi chạy logic xử lý nào.

## 5. Cách đọc gọn: gọi element method theo kiểu tĩnh

Nếu bạn biết annotation type tại thời điểm viết chương trình, cách đơn giản nhất là:

~~~java
Rule rule = SomeClass.class.getAnnotation(Rule.class);
if (rule != null) {
    String route = rule.value();
    int retries = rule.retries();
    Level level = rule.level();
    String[] tags = rule.tags();
}
~~~

Đoạn mã này chỉ minh họa, giả định `SomeClass` được gắn `@Rule` và các kiểu đã được định nghĩa như mục 4.

Ưu điểm của **typed API**:

- Compiler kiểm tra đúng tên method, đúng kiểu trả về.
- Không cần ép kiểu `Object` từ `Method.invoke`.
- Dễ đọc và dễ refactor khi annotation type đổi tên element.
- Các giá trị mảng/enum/class/nested annotation giữ đúng kiểu khai báo, giảm lỗi runtime.

Đối với annotation bắt buộc phải có, tùy API có thể dùng `getDeclaredAnnotation` để lấy annotation khai báo trực tiếp trên phần tử, hoặc `getAnnotation` cho cách tìm kiếm theo quy tắc `AnnotatedElement`. Dù dùng API nào, nếu không có annotation phù hợp thì kết quả là `null` (đối với `getAnnotation`/`getDeclaredAnnotation` đơn lẻ) — **không gọi `rule.value()` trước khi kiểm tra**.

Không nên dùng `field.get(...)` trên annotation instance để lấy `value` hay `retries`. Cấu trúc annotation type được công bố bằng **element method**, không có quy tắc public field cùng tên để đọc.

## 6. Đọc động: introspection element methods và `Method.invoke`

Một framework có thể không biết trước tất cả tên element trong annotation type. Khi đó có thể nhận `Annotation annotation` và duyệt element methods:

~~~java
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

static void printElements(Annotation annotation) throws Exception {
    Method[] methods = annotation.annotationType().getDeclaredMethods();
    Arrays.sort(methods, Comparator.comparing(Method::getName));

    for (Method method : methods) {
        Object actualValue = method.invoke(annotation);
        Object defaultValue = method.getDefaultValue();

        System.out.println(method.getName()
                + " -> " + stringify(actualValue)
                + " (default=" + stringify(defaultValue) + ")");
    }
}

static String stringify(Object value) {
    if (value == null) return "null";
    if (value instanceof Object[] array) return Arrays.deepToString(array);
    if (value instanceof int[] array) return Arrays.toString(array);
    if (value instanceof long[] array) return Arrays.toString(array);
    if (value instanceof boolean[] array) return Arrays.toString(array);
    if (value instanceof char[] array) return Arrays.toString(array);
    if (value instanceof byte[] array) return Arrays.toString(array);
    if (value instanceof short[] array) return Arrays.toString(array);
    if (value instanceof float[] array) return Arrays.toString(array);
    if (value instanceof double[] array) return Arrays.toString(array);
    return String.valueOf(value);
}
~~~

Đây là **các phương thức minh họa để chèn vào một class**, không phải file có `main` độc lập. `printElements` chỉ cần một instance của annotation đã lấy từ Reflection. Nếu muốn dùng riêng các method này, hãy đặt chúng vào một class Java hợp lệ và truyền annotation tương ứng.

Các điểm quan trọng:

- `annotation.annotationType()` trả **đúng annotation interface**; nó là lựa chọn rõ nghĩa hơn `annotation.getClass()` khi muốn tra cứu các element. Một annotation instance ở runtime có thể do JDK triển khai dưới dạng proxy/implementation, nhưng **implementation class không phải hợp đồng element của bạn**.
- `getDeclaredMethods()` trên annotation type trả các element method khai báo trên chính annotation type. Không dựa vào thứ tự mảng trả về; sắp xếp theo tên nếu cần log ổn định.
- `Method.invoke(annotation)` gọi element method trên **annotation instance**. Nếu kết quả là primitive, `invoke` trả wrapper; nếu là mảng, nhận đối tượng mảng với đúng loại; nếu là annotation lồng nhau, nhận annotation instance.
- `Method.getDefaultValue()` trả default được **khai báo** trên element, hoặc `null` nếu không có default. Nó không cho biết giá trị thực tế tại mỗi lần dùng annotation.
- Nếu cần phân biệt một giá trị bằng đúng default do không chỉ rõ khi gắn annotation hay do người viết **ghi tường minh giá trị trùng default**, Reflection thường chỉ cho thấy **giá trị đã hợp thành**. Đừng dùng `actualValue.equals(defaultValue)` để kết luận người viết có/không gõ element tại source.
- Đừng coi `Annotation.toString()` là giao diện serialization dữ liệu ổn định. Hãy đọc các element cụ thể và tự định dạng khi muốn xuất JSON/log/API.

Mã lặp trên dùng `instanceof Object[]` để hiển thị các mảng reference; riêng mảng primitive có từng nhánh `Arrays.toString`. Nếu chỉ viết `String.valueOf(value)` cho một mảng, có thể nhìn thấy chuỗi tên kiểu/hash thay vì nội dung mảng.

## 7. Phân biệt default với giá trị instance

Trong `@interface RetryPolicy { int attempts() default 3; String label(); }`, phần tử `attempts()` có default là `3`, còn `label()` bắt buộc được cung cấp. `RetryPolicy.class.getDeclaredMethod("attempts").getDefaultValue()` trả `Integer(3)`; gọi `attempts()` trên annotation instance trả giá trị **hiệu lực** tại nơi sử dụng, có thể khác 3. `getDefaultValue()` trả `null` cho `label()` vì không khai báo default, không có nghĩa `label()` của annotation hợp lệ trả null.

Giá trị hiệu lực bằng default không chứng minh người viết đã bỏ qua element tại source. Bài riêng về `Method.getDefaultValue()` phân tích đầy đủ các kiểu dữ liệu, annotation evolution và phân biệt default method của interface.

## 8. Trường hợp `value` đặc biệt và những nhầm lẫn về cú pháp

Element tên `value` được Java cho phép viết gọn khi nó là element duy nhất được chỉ định tại nơi gắn annotation:

~~~java
@Rule("checkout")
class A {
}
~~~

Nó tương đương `@Rule(value = "checkout")` (các element còn lại dùng default nếu có). Nhưng nếu cần khai báo thêm element, dùng dạng đặt tên:

~~~java
@Rule(value = "checkout", retries = 5)
class B {
}
~~~

Không được hiểu `value()` là hàm tự mang ý nghĩa nghiệp vụ hoặc là phương thức đặc biệt tự chạy. Nó chỉ có quy tắc cú pháp tiện lợi khi khai báo annotation. Cú pháp `@Rule("checkout", retries = 5)` không phải kiểu rút gọn được cho phép trong Java; phải viết tên `value =` khi cung cấp thêm cặp element–value.

**Một annotation có thể có nhiều element**, nhưng chỉ duy nhất element `value` có cú pháp viết tắt này. Các element không có default phải được truyền tại nơi gắn annotation, dù tên của chúng không phải `value`.

## 9. Đọc trên đúng phần tử runtime

Để lấy annotation trên **method**, dùng `Method` từ `clazz.getDeclaredMethod(...)` rồi đọc bằng `method.getAnnotation(MyAnnotation.class)`; gọi `clazz.getAnnotation(...)` chỉ hỏi annotation của **class**. Đối với field, constructor, parameter hoặc TYPE_USE cũng phải chọn đúng đối tượng Reflection/`AnnotatedType`. `@Retention(RUNTIME)` bảo đảm metadata phù hợp được giữ để đọc; `@Target` chỉ hạn chế nơi dùng, không tự tạo hành vi. Các API declared, inherited và repeatable được giải thích ở phần đầu chương.

## 10. Lỗi runtime cần xử lý khi đọc annotation

Thao tác đọc annotation element thông thường đơn giản, nhưng mã framework xử lý class file từ nhiều phiên bản có thể gặp ngoại lệ:

| Tình huống | API liên quan | Cách hiểu/xử lý |
| --- | --- | --- |
| Annotation không tồn tại tại phần tử | `getAnnotation(...)` | Trả `null`; kiểm tra trước khi gọi element method |
| Annotation không giữ ở `RUNTIME` | `getAnnotation(...)` | Metadata không sẵn có với Reflection runtime, không tự suy ra tên/giá trị |
| Đọc method element từ annotation instance đúng kiểu | `rule.value()`, `rule.retries()` | Trả giá trị theo kiểu khai báo |
| Lấy default của element không có default | `Method.getDefaultValue()` | Trả `null`; không có nghĩa một annotation instance hợp lệ có giá trị `null` |
| Gọi `Method.invoke` với instance hoặc đối số sai | `invoke(...)` | Có thể ném `IllegalArgumentException` hoặc ngoại lệ Reflection liên quan |
| Class literal trong annotation tham chiếu kiểu không còn tồn tại | Element trả `Class<?>` | Có thể ném `TypeNotPresentException` khi truy cập element |
| Enum element tham chiếu enum constant đã bị loại ở runtime | Element trả `enum` | Có thể ném `EnumConstantNotPresentException` |
| Type khai báo element đã thay đổi không tương thích với metadata còn lưu | Đọc element từ class file cũ | Có thể ném `AnnotationTypeMismatchException` |
| Một required element bị thiếu do thay đổi không tương thích giữa các phiên bản | Lời gọi element method | Có thể ném `IncompleteAnnotationException` |

Khi dùng `Method.invoke`, ngoại lệ do method được gọi ném có thể được **bọc trong `InvocationTargetException`**, nên nếu cần diagnostic phải kiểm tra `getCause()`. Nếu chương trình giả định tên element từ cấu hình và gọi `getDeclaredMethod("name")` sai, API tra cứu có thể ném `NoSuchMethodException`; đó không phải là lỗi vì annotation instance không có giá trị.

Các trường hợp bất thường thường gắn với class file không đồng bộ phiên bản, thiếu dependency, class loader hoặc bytecode bị biến đổi. Không nên viết một bộ xử lý annotation chung rồi bắt mọi exception để silently bỏ qua metadata bắt buộc.

## 11. Đọc member value chưa thực hiện nghiệp vụ

Với `@MinimumAge(18)`, `annotation.value()` chỉ trả số `18`; nó không tự đọc một field, so tuổi hoặc từ chối input. Tương tự, element `handler()` trả `Class<?>` không tự khởi tạo và chạy handler. Cần validator/framework hoặc caller đọc cấu hình, lấy dữ liệu thực tế và ra quyết định. Bài cuối chương có ví dụ chạy được và chỉ rõ các ranh giới validation.

## 12. Checklist khi đọc annotation element trong ứng dụng

1. **Xác định nơi annotation được gắn:** class, method, field, constructor hay parameter; tra cứu đúng phần tử.
2. **Kiểm tra retention:** cần `RUNTIME` để đọc bằng Reflection runtime.
3. **Chọn đúng annotation type:** dùng `getAnnotation(MyAnno.class)` và kiểm tra `null`.
4. **Ưu tiên typed accessor:** `anno.value()`, `anno.level()`, `anno.tags()` nếu biết type; tránh Reflection hai lần không cần thiết.
5. **Nếu xử lý tổng quát:** dùng `annotation.annotationType().getDeclaredMethods()`, `method.invoke(annotation)` và `getDefaultValue()` đúng mục đích.
6. **Đừng nhầm default và actual:** default trên khai báo không chứng minh annotation instance hiện dùng default.
7. **Xử lý giá trị nhiều kiểu:** primitive/wrapper, `String`, `Class<?>`, enum, annotation lồng nhau, mảng một chiều; định dạng array đúng.
8. **Cân nhắc class loader và phiên bản:** thay đổi element signature hoặc enum/class được tham chiếu có thể khiến code đọc gặp exception ở runtime.
9. **Giữ biệt lập metadata và nghiệp vụ:** cần code/framework cụ thể để biến giá trị annotation thành validation, routing, xử lý giao dịch hoặc behavior.
10. **Kiểm thử trên artifact thực tế:** khi nhiều module hoặc công cụ bytecode tham gia, xác minh annotation thật sự được lưu và có giá trị phù hợp trong class file đã phát hành.

## 13. Tự kiểm tra

**Câu 1:** Annotation type khai báo `int retries() default 3;`. `Rule rule` trên `Checkout` ghi `retries = 5`. `rule.retries()` và `Rule.class.getDeclaredMethod("retries").getDefaultValue()` trả gì? **Lần lượt 5 và 3.**

**Câu 2:** `getAnnotation(MyAnno.class)` trả `null`. Có thể gọi `.value()` không? **Không.** Phải kiểm tra annotation có tồn tại tại đúng phần tử và giữ ở runtime.

**Câu 3:** Element khai báo `String[] tags() default {};`. Gọi `annotation.tags()` nhận `List<String>` hay `String[]`? **`String[]`**, phải dùng `Arrays.toString`/duyệt mảng nếu muốn hiển thị nội dung.

**Câu 4:** Vì sao gọi `rule.owner().name()` hợp lệ? **Vì `owner()` trả một annotation instance kiểu `Owner`**, mà `Owner` có element `name()` trả `String`.

**Câu 5:** Có thể dùng `annotation.getClass().getDeclaredMethods()` để coi mọi method tìm được là element chuẩn không? **Không nên.** Dùng `annotation.annotationType()` để lấy contract annotation type, tránh nhầm implementation class.

**Câu 6:** `handler()` trả `Integer.class`. Có nghĩa ứng dụng đã tạo `Integer` và chạy một handler không? **Không.** Đây chỉ là giá trị class literal; hành vi xử lý phải do caller quyết định.

**Câu 7:** `Method.getDefaultValue()` là `null` thì `annotation.element()` chắc cũng `null`? **Không.** Nó chỉ có nghĩa element không khai báo default; một annotation instance hợp lệ phải có giá trị theo quy tắc sử dụng annotation.

**Ghi nhớ:** **Element của annotation là phương thức không tham số trên annotation type.** Lấy annotation instance từ đúng phần tử (`getAnnotation`), rồi gọi phương thức element (`annotation.value()` hoặc `Method.invoke(annotation)`) để đọc **giá trị thực tế**. Chỉ dùng `Method.getDefaultValue()` khi muốn đọc **giá trị mặc định trong khai báo**, và không nhầm metadata với hành vi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-010">Method.getDefaultValue cho phần tử có giá trị mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Method.getDefaultValue: lấy giá trị mặc định của phần tử annotation bằng Java Reflection

## 1. Khái niệm và mục đích

Một annotation được khai báo bằng `@interface`. Mỗi phần tử (*annotation element*) được viết dưới dạng một khai báo phương thức **không có tham số**, ví dụ `int retries()` hoặc `String name()`. Phần tử có thể khai báo **giá trị mặc định** bằng từ khóa `default`:

```java
@interface Config {
    String name();                  // Bắt buộc cung cấp khi dùng annotation.
    int retries() default 3;       // Giá trị mặc định là 3.
    boolean enabled() default true;
}
```

Trong Reflection, từng phần tử được biểu diễn bằng một đối tượng `java.lang.reflect.Method`. Phương thức:

```java
Object defaultValue = method.getDefaultValue();
```

đọc **giá trị mặc định được khai báo trên chính phần tử annotation** mà `Method` đại diện, không cần khởi tạo đối tượng của lớp được gắn annotation.

**Quy tắc quan trọng nhất:**

- Nếu `Method` đại diện cho phần tử annotation **có khai báo `default`**, `getDefaultValue()` trả giá trị mặc định tương ứng dưới dạng `Object`.
- Nếu phần tử **không có giá trị mặc định**, kết quả là **`null`**.
- Nếu `Method` **không đại diện cho một phần tử khai báo trực tiếp trong kiểu annotation**, kết quả cũng là **`null`**.
- Nếu kiểu phần tử là primitive, giá trị trả về được **boxing** thành wrapper tương ứng. Ví dụ `int retries() default 3` → `Integer.valueOf(3)`.
- `getDefaultValue()` **không lấy giá trị đã ghi đè tại chỗ sử dụng annotation**. Để lấy giá trị hiệu lực của một annotation đang gắn trên lớp/phương thức, hãy đọc đối tượng annotation.

Đây là API hữu ích khi tạo trình đọc metadata, trình kiểm tra cấu hình, tài liệu hóa annotation, công cụ validation, scanner hoặc framework dựa trên Reflection.

## 2. Phân biệt giá trị mặc định và giá trị hiệu lực

Xét:

```java
@Retention(RetentionPolicy.RUNTIME)
@interface Task {
    String name();
    int retries() default 3;
}

@Task(name = "backup", retries = 7)
class BackupTask {}

@Task(name = "cleanup")
class CleanupTask {}
```

Với phần tử `Task.retries()`:

- **Giá trị mặc định của khai báo:** `3`. Đọc qua `Task.class.getDeclaredMethod("retries").getDefaultValue()`.
- **Giá trị hiệu lực trên `BackupTask`:** `7`, vì nơi sử dụng đã ghi đè.
- **Giá trị hiệu lực trên `CleanupTask`:** `3`, vì nơi sử dụng bỏ qua `retries` nên Java áp dụng default.

```java
Method retries = Task.class.getDeclaredMethod("retries");
Object declaredDefault = retries.getDefaultValue(); // Integer(3)

Task backup = BackupTask.class.getAnnotation(Task.class);
int usedByBackup = backup.retries(); // 7

Task cleanup = CleanupTask.class.getAnnotation(Task.class);
int usedByCleanup = cleanup.retries(); // 3
```

Các đoạn trên là **mã minh họa** và cần các import `java.lang.annotation.*`, `java.lang.reflect.*`. Chương trình độc lập đầy đủ sẽ có ở phần 4.

`@Retention(RetentionPolicy.RUNTIME)` cần thiết để lấy **annotation đã gắn** bằng `getAnnotation(...)` khi chương trình chạy. Trong khi đó, `getDefaultValue()` đọc metadata trên `Method` của chính kiểu annotation; không phải là thao tác lấy annotation đang gắn trên một phần tử sử dụng.

**Không thể dựa vào `getDefaultValue()` để xác định tại nơi sử dụng một giá trị hiệu lực có được viết tường minh hay được kế thừa từ default.** Nếu người viết ghi `retries = 3` tường minh, giá trị hiệu lực vẫn là `3`.

## 3. API, kết quả và các kiểu dữ liệu

Chữ ký Java SE 17:

```java
public Object getDefaultValue()
```

| Khai báo phần tử | Giá trị `getDefaultValue()` | Kiểu runtime của kết quả |
| --- | --- | --- |
| `int retries() default 3;` | `3` | `Integer` |
| `boolean enabled() default true;` | `true` | `Boolean` |
| `String label() default "java";` | `"java"` | `String` |
| `String[] tags() default {"a","b"};` | Mảng gồm `"a"`, `"b"` | `String[]` |
| `Class<?> handler() default String.class;` | `String.class` | `Class<?>` |
| `Priority priority() default Priority.HIGH;` | `Priority.HIGH` | Enum `Priority` |
| `Meta meta() default @Meta;` | Đối tượng annotation mặc định | Kiểu annotation `Meta` |
| `String name();` | `null` | Không có giá trị mặc định |

Vì return type là `Object`, phải **kiểm tra/kết hợp với `method.getReturnType()`** trước khi ép kiểu. Ví dụ:

```java
Method method = Config.class.getDeclaredMethod("retries");
Object value = method.getDefaultValue();

if (value instanceof Integer retries) {
    System.out.println(retries);
}
```

Phương thức phần tử trả `int` nhưng `getDefaultValue()` trả một `Integer`. Với kiểu trả về là mảng primitive, ví dụ `int[] levels() default {1,2}`, giá trị trả về là **`int[]`**, không phải `Integer[]`: boxing chỉ áp dụng cho **một giá trị primitive**, không biến đổi cả mảng.

Các loại phần tử annotation hợp lệ theo quy tắc ngôn ngữ Java gồm primitive (không có `void`), `String`, `Class` hoặc dạng tham số cho phép, enum, kiểu annotation và mảng một chiều của các kiểu đó. Không dùng kiểu phần tử bất kỳ như `List<String>` hoặc `Map`, và không thể dùng `null` làm giá trị mặc định hợp lệ.

## 4. Ví dụ Java 17+ chạy được và stdout chính xác

Lưu chương trình sau dưới tên **`AnnotationDefaultDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;

public class AnnotationDefaultDemo {
    enum Priority { LOW, HIGH }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Meta {
        String author() default "team";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Config {
        String name();                              // Không có default.
        int retries() default 3;
        boolean enabled() default true;
        String[] tags() default {"core", "reflection"};
        Class<?> handler() default String.class;
        Priority priority() default Priority.HIGH;
        Meta meta() default @Meta;
    }

    @Config(name = "service", retries = 7, tags = {"prod"})
    static class Service {}

    @Config(name = "backup")
    static class Backup {}

    interface Greeter {
        default String greet() {
            return "hi";
        }
    }

    public static void main(String[] args) throws Exception {
        Method name = Config.class.getDeclaredMethod("name");
        Method retries = Config.class.getDeclaredMethod("retries");

        System.out.println("name default: " + name.getDefaultValue());

        Object retryDefault = retries.getDefaultValue();
        System.out.println("retries default: " + retryDefault
                + " (" + retryDefault.getClass().getSimpleName() + ")");

        System.out.println("enabled default: "
                + Config.class.getDeclaredMethod("enabled").getDefaultValue());

        System.out.println("tags default: " + Arrays.toString(
                (String[]) Config.class.getDeclaredMethod("tags")
                        .getDefaultValue()));

        System.out.println("handler default: "
                + ((Class<?>) Config.class.getDeclaredMethod("handler")
                        .getDefaultValue()).getSimpleName());

        System.out.println("priority default: "
                + Config.class.getDeclaredMethod("priority").getDefaultValue());

        Meta nested = (Meta) Config.class.getDeclaredMethod("meta")
                .getDefaultValue();
        System.out.println("meta.author default: " + nested.author());

        Config service = Service.class.getAnnotation(Config.class);
        Config backup = Backup.class.getAnnotation(Config.class);

        System.out.println("service retries: " + service.retries());
        System.out.println("backup retries: " + backup.retries());
        System.out.println("service tags: "
                + Arrays.toString(service.tags()));
        System.out.println("backup tags: "
                + Arrays.toString(backup.tags()));

        System.out.println("retries via invoke: " + retries.invoke(service));

        Method defaultMethod = Greeter.class.getMethod("greet");
        System.out.println("interface default method isDefault: "
                + defaultMethod.isDefault());
        System.out.println("interface default method getDefaultValue: "
                + defaultMethod.getDefaultValue());

        System.out.println("ordinary method getDefaultValue: "
                + String.class.getMethod("length").getDefaultValue());
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac AnnotationDefaultDemo.java
java AnnotationDefaultDemo
```

Stdout:

```text
name default: null
retries default: 3 (Integer)
enabled default: true
tags default: [core, reflection]
handler default: String
priority default: HIGH
meta.author default: team
service retries: 7
backup retries: 3
service tags: [prod]
backup tags: [core, reflection]
retries via invoke: 7
interface default method isDefault: true
interface default method getDefaultValue: null
ordinary method getDefaultValue: null
```

### Giải thích các dòng đáng chú ý

1. `Config.name()` **không** khai báo `default`, nên `name.getDefaultValue()` trả `null`. Phần tử `name` vẫn bắt buộc khai báo khi dùng `@Config`.
2. `Config.retries()` khai báo `default 3`, nên kết quả là đối tượng wrapper `Integer` mang giá trị `3`.
3. Mảng mặc định `tags` là `String[]`. Dùng `Arrays.toString` để hiển thị nội dung; nếu gọi `toString()` trực tiếp trên mảng sẽ không có định dạng liệt kê phần tử mong muốn.
4. Default của `Class<?> handler()` là một `Class`, của `Priority priority()` là một giá trị enum, và của `Meta meta()` là đối tượng annotation tương ứng.
5. Trên `Service`, giá trị `retries` hiệu lực là `7` (ghi đè). Trên `Backup`, giá trị hiệu lực là `3` (default).
6. `retries.invoke(service)` gọi phần tử trên **đối tượng annotation instance** và trả giá trị hiệu lực `7`; nó **khác** `retries.getDefaultValue()`, vốn luôn mô tả default của phần tử.
7. `Greeter.greet()` là **default method của interface** có thân phương thức; `isDefault()` trả `true`, nhưng `getDefaultValue()` vẫn trả `null`. Hai nghĩa của chữ *default* không liên quan.
8. `String.length()` là phương thức thông thường, không phải phần tử annotation; `getDefaultValue()` trả `null`.

## 5. Phải lấy đúng Method của kiểu annotation

Giả sử có `@Config` gắn trên `Service`, cần tránh ba nhầm lẫn:

```java
// Đúng: lấy phần tử đã khai báo trong annotation type.
Method element = Config.class.getDeclaredMethod("retries");
Object defaultValue = element.getDefaultValue();

// Đúng: lấy annotation instance trên nơi sử dụng.
Config annotation = Service.class.getAnnotation(Config.class);
int actual = annotation.retries();

// Sai mục đích: tìm "retries" trên lớp Service.
// Service.class.getDeclaredMethod("retries") thường không tồn tại.
```

Annotation instance có thể được runtime triển khai bằng proxy hoặc kỹ thuật nội bộ, nhưng để đọc default ta nên sử dụng **`Config.class`** hoặc `annotation.annotationType()` và `getDeclaredMethod(elementName)`, thay vì suy ra cấu trúc implementation của đối tượng annotation.

Để phân tích toàn bộ default của annotation type, dùng:

```java
// Phương thức tiện ích minh họa, đặt trong một lớp Java có các import cần thiết.
static void printDefaults(Class<? extends java.lang.annotation.Annotation> type) {
    for (java.lang.reflect.Method element : type.getDeclaredMethods()) {
        Object value = element.getDefaultValue();

        System.out.println(element.getName()
                + " : " + element.getReturnType().getTypeName()
                + " => " + (value == null ? "<required>" : value));
    }
}
```

Đây là **mã minh họa**, không phải một chương trình có `main`. Chương trình xử lý metadata thực tế nên bổ sung bộ định dạng cho **mảng** bằng `Arrays.toString` hoặc `Arrays.deepToString` theo loại dữ liệu, và **sắp xếp** tên phần tử để có kết quả ổn định (vì `getDeclaredMethods()` không đảm bảo thứ tự).

Để tránh lỗi ép kiểu, hãy phân loại `value` bằng `instanceof`, kết hợp `element.getReturnType()` và xử lý `null` trước khi giải nén.

## 6. Null nghĩa là “không khai báo default”, không phải giá trị mặc định null

Annotation element **không thể có** `default null` hợp lệ trong mã nguồn Java. Vì vậy:

```java
@interface Notice {
    String message();                // Không có default.
    String optional() default "";    // Có default là chuỗi rỗng.
    String[] tags() default {};      // Có default là mảng rỗng.
}
```

Bảng phân biệt:

| Khai báo | `getDefaultValue()` | Có default? |
| --- | --- | --- |
| `String message();` | `null` | Không |
| `String optional() default "";` | `""` | Có |
| `String[] tags() default {};` | Mảng `String[]` độ dài `0` | Có |
| `int count() default 0;` | `Integer(0)` | Có |
| `boolean active() default false;` | `Boolean.FALSE` | Có |

Do đó, **không** dùng kiểm tra như `if (!Boolean.TRUE.equals(defaultValue))` để kết luận thiếu default, vì `false` là một default hoàn toàn hợp lệ. Quy tắc đơn giản: `value == null` nghĩa là không có default theo hợp đồng của `getDefaultValue()`.

Lưu ý `getDefaultValue()` được gọi trên `Method` thông thường cũng trả `null`; nếu cần phân biệt “phần tử bắt buộc” với “không phải phần tử annotation”, hãy kiểm tra `method.getDeclaringClass().isAnnotation()`.

## 7. Không nhầm với các khái niệm “default” khác

| Cụm từ/API | Ý nghĩa | Liên quan đến `Method.getDefaultValue()`? |
| --- | --- | --- |
| `@interface X { int n() default 3; }` | Giá trị mặc định của phần tử annotation | **Có**, trả `3` được boxing |
| `interface X { default void run() {} }` | Default method của interface có thân | **Không**, `getDefaultValue()` thường `null` |
| `Method.isDefault()` | Method có phải default method của interface hay không | API **khác** hoàn toàn |
| `@Retention(RUNTIME)` | Annotation được lưu để truy xuất ở runtime tại nơi sử dụng | Không tự tạo default; hữu ích cho `getAnnotation` |
| `@Target(TYPE)` | Giới hạn vị trí sử dụng annotation | Không quyết định giá trị mặc định |
| `Class.getAnnotation(X.class)` | Trả annotation instance đang gắn trên phần tử | Đọc giá trị **hiệu lực** qua `annotation.element()` |
| `Method.invoke(annotationInstance)` | Gọi phương thức phần tử trên annotation instance | Trả giá trị **hiệu lực**; không chỉ default |

**Lưu ý về `@Retention`:** Khi công cụ muốn đọc annotation **đã gắn** ở runtime, khai báo annotation với `@Retention(RetentionPolicy.RUNTIME)`. Nếu chỉ có metadata của một kiểu annotation được nạp, Reflection có thể đọc thông tin về các phương thức phần tử và default của chính kiểu đó mà không cần lấy một annotation instance ở nơi sử dụng.

## 8. Quy tắc default trong annotation và tính tương thích

Annotation có nhiều ràng buộc riêng so với phương thức Java thông thường:

- Phần tử annotation không nhận tham số và không khai báo `throws`.
- Giá trị mặc định cần là biểu thức phù hợp với loại phần tử và các quy tắc hằng/giá trị annotation của Java. Không thể dùng giá trị runtime tùy ý như `default new java.util.Date()`.
- Mảng mặc định chỉ mô tả **một chiều kiểu phần tử hợp lệ**; nếu muốn biểu diễn cấu trúc phức tạp, cân nhắc annotation lồng nhau hoặc thiết kế schema khác.
- Phần tử không có `default` phải được cung cấp khi sử dụng annotation trong mã nguồn, trừ khi có cú pháp cung cấp tương ứng.
- Default thuộc **khai báo annotation type**, không phải được lưu lặp lại thành một giá trị tường minh ở mỗi vị trí sử dụng annotation.

Một hệ quả quan trọng khi tiến hóa thư viện: nếu thay `default 3` thành `default 5` trong phiên bản mới của annotation, các vị trí sử dụng **không khai báo `retries` tường minh** có thể nhận giá trị hiệu lực mới khi chạy với bản annotation mới. Ngược lại, nơi đã ghi `retries = 7` tiếp tục dùng `7` theo khai báo sử dụng của nó. Bởi vậy thay đổi default có thể là **thay đổi hành vi**, dù không cần sửa mọi nơi sử dụng annotation.

Khi viết framework hoặc scanner: xử lý giá trị default như một phần của hợp đồng cấu hình; lưu rõ phiên bản/chính sách nếu default ảnh hưởng đến nghiệp vụ. Không sửa lại `Method` hoặc coi kết quả `getDefaultValue()` là thiết lập runtime có thể ghi đè bằng Reflection.

## 9. Các lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `getDefaultValue()` trả `null` cho `name()` | Phần tử bắt buộc, không khai báo default | Xác định thiếu default; đọc từ annotation instance nếu đã gắn |
| Trả `3` nhưng nơi sử dụng annotation đặt `retries = 7` | Đang đọc default của khai báo, không phải giá trị hiệu lực | Dùng `annotation.retries()` hoặc `Method.invoke(annotation)` |
| `getDefaultValue()` trả `null` cho default method interface | `default` của interface method khác default của annotation | Dùng `Method.isDefault()` để nhận dạng default method |
| `ClassCastException` khi ép default của `int` về `String` | Primitive được boxing thành `Integer` | Kiểm tra `getReturnType()`, `instanceof` trước khi cast |
| In mảng `tags` ra dạng `[Ljava.lang.String;@...` | Gọi `Object.toString()` của mảng | Dùng `Arrays.toString((String[]) value)` |
| Tưởng `default ""` hoặc `default {}` là không có default | Giá trị rỗng vẫn là default hợp lệ | Chỉ kiểm tra `getDefaultValue() == null` |
| `NoSuchMethodException` khi tìm element qua lớp sử dụng | Element thuộc **annotation type**, không thuộc lớp được gắn | Tìm qua `Config.class.getDeclaredMethod(...)` |
| `getAnnotation(Config.class)` trả `null` | Annotation không gắn/không được giữ ở runtime/vị trí sai | Kiểm tra `@Retention(RUNTIME)` và vị trí khai báo |
| Default chứa `Class` không còn khả dụng lúc đọc | Phụ thuộc kiểu bị thiếu hoặc lỗi nạp lớp | Theo tài liệu API, `getDefaultValue()` có thể ném `TypeNotPresentException` |
| Kết quả duyệt element thay đổi thứ tự | `getDeclaredMethods()` không bảo đảm thứ tự | Sắp xếp theo tên khi xuất báo cáo hoặc serialize |

### Các lỗi khi classpath hoặc binary metadata không còn tương thích

Tài liệu `Method.getDefaultValue()` của Java SE 17 nêu `TypeNotPresentException` nếu giá trị mặc định loại `Class` tham chiếu tới kiểu không còn tải được. Ngoài ra, khi phân tích annotation từ thư viện không đồng nhất phiên bản, có thể xuất hiện lỗi liên kết/định dạng metadata hoặc các lỗi khi truy cập giá trị annotation instance. Không nên giả định mọi annotation trong môi trường plugin đều có binary metadata hợp lệ.

Trong ứng dụng bình thường, **không cần bắt** các lỗi hiếm để đọc một annotation do dự án tự kiểm soát; trong công cụ quét bytecode/library bất kỳ, hãy quyết định chính sách xử lý lỗi có chủ đích và cung cấp thông tin về kiểu annotation/element gây vấn đề.

## 10. Tự kiểm tra kiến thức

1. **Phần tử `int timeout() default 0;` trả gì qua `getDefaultValue()`?** Đối tượng `Integer` có giá trị `0`, không phải `null`.
2. **Phần tử `String id();` trả gì?** `null`, do không có default; nơi sử dụng phải khai báo `id` khi cần.
3. **`@Config(retries = 9)` làm thay đổi `getDefaultValue()` của `retries` thành 9 không?** Không; default trên annotation type không đổi. Giá trị hiệu lực ở nơi sử dụng là `9`.
4. **`String[] tags() default {};` có default không?** Có; trả mảng rỗng, khác với `null`.
5. **Một default method của interface có `Method.isDefault() == true` có nghĩa `getDefaultValue()` khác `null` không?** Không, hai API phục vụ hai khái niệm khác nhau.
6. **Muốn xem tất cả default trên một `@interface`, làm gì?** Duyệt `annotationType.getDeclaredMethods()`, gọi `getDefaultValue()` cho từng `Method`, kiểm tra `null` và định dạng đúng theo kiểu.
7. **Muốn lấy giá trị cuối cùng áp dụng trên một lớp đã gắn annotation thì sao?** Lấy annotation qua `getAnnotation(...)`, sau đó gọi phần tử trên instance; không chỉ đọc `Method.getDefaultValue()`.

**Ghi nhớ:** `Method.getDefaultValue()` lấy **giá trị mặc định trong khai báo phần tử annotation**, trả `null` nếu không có default hoặc không phải phần tử annotation. Nó không tự đọc giá trị được ghi đè trên đối tượng được gắn annotation, và không liên quan tới `Method.isDefault()` của interface.

Tài liệu tham khảo: [Java SE 17 — Method.getDefaultValue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#getDefaultValue()), [JLS §9.6.2 — Defaults for Annotation Type Elements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.2), [Java SE 17 — Annotation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Annotation.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-011">equals, hashCode và annotationType của annotation</a>

<details>
<summary>Xem chi tiết</summary>

# `equals`, `hashCode` và `annotationType` của annotation trong Java

## 1. Ba phương thức trả lời ba câu hỏi khác nhau

Khi Reflection đọc một annotation ở runtime, kết quả không chỉ là vài trường metadata; nó là một **đối tượng thực thi giao diện annotation**. Mọi annotation interface đều ngầm mở rộng `java.lang.annotation.Annotation`, nên đối tượng thu được có ba phương thức trọng tâm:

| Phương thức | Câu hỏi cần trả lời | Ý nghĩa |
| --- | --- | --- |
| `annotationType()` | “Annotation này được khai báo bởi **annotation interface** nào?” | Trả `Class<? extends Annotation>` của loại annotation, chẳng hạn `Info.class` |
| `equals(Object other)` | “Hai annotation có **cùng loại và cùng giá trị các thành viên** không?” | So sánh theo **giá trị ngữ nghĩa**, không theo địa chỉ đối tượng |
| `hashCode()` | “Giá trị băm tương thích với quy tắc `equals` của annotation là gì?” | Được đặc tả bằng công thức trên tất cả thành viên, kể cả thành viên dùng giá trị mặc định |

Ví dụ:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Info {
    String name();
    int level() default 1;
}
~~~

Đây là đoạn khai báo minh họa, cần import `java.lang.annotation.*`. Một annotation `@Info(name = "core")` sẽ có **hai thành viên** trong hợp đồng so sánh: `name = "core"` và `level = 1`. Việc không ghi rõ `level` trong mã nguồn **không xóa thành viên này** khỏi `equals` hoặc `hashCode`.

Tại runtime, thông thường lấy annotation qua các API của `AnnotatedElement`:

~~~java
Info info = SomeClass.class.getAnnotation(Info.class);
if (info != null) {
    System.out.println(info.annotationType() == Info.class);
}
~~~

Đoạn minh họa trên giả định có lớp `SomeClass` và annotation `@Info` giữ lại đến runtime. `getAnnotation` trả `null` nếu không tìm thấy annotation phù hợp; đừng gọi `equals`, `hashCode` hoặc `annotationType` trước khi xử lý trường hợp ấy.

## 2. `annotationType()` khác `getClass()` như thế nào?

Một annotation runtime thường được JDK hiện thực bằng **đối tượng của lớp implementation do runtime tạo**, chứ không phải instance có lớp triển khai trùng với annotation interface.

- `info.annotationType()` trả đúng **`Info.class`**.
- `info.getClass()` trả **lớp implementation runtime**, có thể là lớp proxy/implementation phụ thuộc JDK. Không nên đặt kỳ vọng vào tên package, tên class hoặc định dạng implementation của nó.
- `info.annotationType() == Info.class` là cách kiểm tra annotation interface đang được biểu diễn.
- `info.getClass() == Info.class` không phải phép kiểm tra đúng loại annotation: `Info` là một interface, không phải implementation class của object.

Sử dụng `Class<? extends Annotation>` cho kiểu annotation tổng quát:

~~~java
Annotation any = element.getAnnotation(Info.class);
if (any != null) {
    Class<? extends Annotation> kind = any.annotationType();
    System.out.println(kind == Info.class);
}
~~~

Đây là đoạn minh họa bên trong phương thức có `AnnotatedElement element` và `Info` phù hợp. Không dùng `getClass()` làm khóa định danh loại annotation khi xây framework quét metadata.

**Phân biệt thêm:** `annotationType()` **không** trả lớp nơi annotation được đặt. Nếu `@Info` nằm trên `Service`, `annotationType()` vẫn là `Info.class`, **không** phải `Service.class`. Muốn biết đối tượng được gắn annotation, bạn phải giữ riêng tham chiếu `AnnotatedElement` tương ứng.

## 3. Hợp đồng `equals`: bằng nhau khi nào?

Javadoc `java.lang.annotation.Annotation` quy định hai annotation bằng nhau nếu và chỉ nếu:

1. Đối tượng đem so sánh biểu diễn **cùng một annotation interface**.
2. **Tất cả các thành viên tương ứng đều có giá trị bằng nhau** theo quy tắc dành cho từng kiểu.

Không yêu cầu hai annotation phải nằm trên cùng class, cùng field hoặc cùng method. Không yêu cầu hai giá trị được viết giống hệt nhau trong mã nguồn nếu chúng mang cùng giá trị sau khi áp dụng default.

### 3.1. Giá trị mặc định cũng tham gia so sánh

~~~java
@Info(name = "core")
class A { }

@Info(name = "core", level = 1)
class B { }
~~~

Giả sử `Info.level()` có `default 1` như ở mục 1. Annotation của `A` và `B` **bằng nhau** theo `equals` vì giá trị tất cả thành viên giống nhau. Khi gọi `level()` từ annotation của `A`, API trả 1.

Điều này khác với việc so sánh nguyên văn source code: `@Info(name = "core")` và `@Info(name = "core", level = 1)` không giống hệt về cách viết, nhưng có **giá trị annotation tương đương**.

### 3.2. So sánh từng kiểu thành viên

| Kiểu thành viên | Quy tắc `equals` của annotation |
| --- | --- |
| Primitive `boolean`, `byte`, `short`, `char`, `int`, `long` | So theo giá trị bằng toán tử `==` |
| `float`, `double` | Theo `Float.valueOf(x).equals(Float.valueOf(y))` hoặc `Double.valueOf(x).equals(Double.valueOf(y))` |
| `String`, `Class`, enum | Dùng `equals` của giá trị tương ứng |
| Annotation lồng trong annotation | So sánh `equals` **đệ quy** |
| Mảng một chiều của các kiểu được phép | Dùng overload thích hợp của `Arrays.equals` |

Cần chú ý **điểm đặc biệt của số thực**: theo hợp đồng annotation, `NaN` được coi là bằng chính nó, còn `+0.0` và `-0.0` được phân biệt khi so sánh thành viên kiểu `float`/`double`. Quy tắc này khác với cách dùng toán tử số học `==` thông thường trong Java.

### 3.3. Array member so **nội dung**, không so identity

Nếu annotation có thành viên:

~~~java
int[] ids() default {1, 2};
~~~

thì `ids = {1, 2}` bằng `ids = {1, 2}` dù các mảng được tạo ở những lần truy cập khác nhau. Nhưng `{1, 2}` **không bằng** `{2, 1}`, vì `Arrays.equals` so sánh **theo thứ tự phần tử**.

Không dùng `array1 == array2` hay `array1.equals(array2)` để tự mô phỏng hợp đồng annotation: cả hai cách ấy chỉ so tham chiếu mảng. Với `int[]`, nên dùng `Arrays.equals(int[], int[])`; với mảng reference hợp lệ, dùng overload `Arrays.equals(Object[], Object[])`. Hợp đồng annotation chọn overload tương ứng tự động.

Đặc biệt, annotation member **không thể là một array nhiều chiều**; tập kiểu hợp lệ được giới hạn trong primitive, `String`, `Class`, enum, annotation interface và mảng một chiều của các kiểu ấy. Giá trị member không thể là `null`.

### 3.4. Cùng tên các thành viên chưa đủ

Hai annotation interfaces khác nhau:

~~~java
@interface Alpha { String value(); }
@interface Beta  { String value(); }
~~~

dù đều được sử dụng dưới dạng `@Alpha("x")` và `@Beta("x")`, **không bằng nhau**. `annotationType()` khác nhau, nên điều kiện thứ nhất của `equals` thất bại. Tên member hay nội dung giống hệt cũng không biến hai loại annotation khác nhau thành một.

## 4. Hợp đồng `hashCode` của annotation

`Annotation.hashCode()` **không phải** mã băm identity của đối tượng proxy. Nó được định nghĩa theo tổng giá trị băm của **mọi thành viên annotation**, bao gồm member dùng default.

Với thành viên tên `n` và giá trị `v`:

~~~text
memberHash = (127 * n.hashCode()) XOR valueHash(v)

annotationHash = tổng memberHash trên tất cả thành viên
~~~

- `n.hashCode()` là `String.hashCode()` của **tên member**, ví dụ `"name"`, không phải tên annotation interface.
- Với primitive, `valueHash(v)` là `hashCode()` của wrapper tương ứng: `Integer`, `Boolean`, `Float`, `Double`, ...
- Với `String`, `Class`, enum, hoặc annotation lồng, dùng `v.hashCode()` (annotation lồng lại tính theo cùng hợp đồng).
- Với array member, dùng overload tương ứng của `Arrays.hashCode`; **không dùng `Object.hashCode` của mảng**.
- Tổng số học trong Java dùng số nguyên `int`; overflow được phép theo quy tắc số nguyên Java.

Ví dụ annotation:

~~~java
@interface Info {
    String name();
    int[] ids() default {1, 2};
}
~~~

Với `@Info(name = "core")`, công thức băm tương ứng là:

~~~java
int expectedHash =
        (127 * "name".hashCode() ^ "core".hashCode()) +
        (127 * "ids".hashCode() ^ Arrays.hashCode(new int[]{1, 2}));
~~~

Đây là đoạn minh họa trong phương thức có import `java.util.Arrays`, không phải ứng dụng riêng. Toán tử XOR `^` áp dụng cho từng member, sau đó **cộng** hai memberHash. Giá trị `ids` mặc định `{1, 2}` **vẫn tham gia** vào `hashCode`.

**Hệ quả bắt buộc:** nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` phải là `true`. Chiều ngược lại **không được bảo đảm**, bởi hai giá trị khác nhau vẫn có thể trùng hash. Không dùng một hashCode đơn độc làm bằng chứng rằng hai annotation bằng nhau.

Không nên hardcode một mã băm số nguyên cụ thể khi xây logic nghiệp vụ; dùng `equals`, và nếu cần tự tính để học thì áp dụng đúng công thức của Javadoc.

## 5. Ví dụ hoàn chỉnh Java 17+: equality, defaults, arrays và HashMap

Tạo file **`AnnotationIdentityDemo.java`**:

~~~java
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AnnotationIdentityDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Info {
        String name();
        int[] ids() default {1, 2};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface OtherInfo {
        String name();
        int[] ids() default {1, 2};
    }

    @Info(name = "core")
    static class DefaultValues { }

    @Info(name = "core", ids = {1, 2})
    static class ExplicitValues { }

    @Info(name = "core", ids = {2, 1})
    static class Reordered { }

    @Info(name = "other")
    static class DifferentName { }

    @OtherInfo(name = "core")
    static class DifferentType { }

    public static void main(String[] args) {
        Info first = DefaultValues.class.getAnnotation(Info.class);
        Info equivalent = ExplicitValues.class.getAnnotation(Info.class);
        Info reordered = Reordered.class.getAnnotation(Info.class);
        Info different = DifferentName.class.getAnnotation(Info.class);
        Annotation other = DifferentType.class.getAnnotation(OtherInfo.class);

        int expectedHash =
                (127 * "name".hashCode() ^ "core".hashCode()) +
                (127 * "ids".hashCode() ^ Arrays.hashCode(new int[]{1, 2}));

        Map<Info, String> index = new HashMap<>();
        index.put(first, "found");

        System.out.println("same annotation type: " +
                (first.annotationType() == Info.class));
        System.out.println("getClass is annotation type: " +
                (first.getClass() == Info.class));
        System.out.println("omitted default equals explicit: " +
                first.equals(equivalent));
        System.out.println("matching hashCode: " +
                (first.hashCode() == equivalent.hashCode()));
        System.out.println("contract hash formula: " +
                (first.hashCode() == expectedHash));
        System.out.println("reversed array: " + first.equals(reordered));
        System.out.println("different member: " + first.equals(different));
        System.out.println("different annotation interface: " +
                first.equals(other));
        System.out.println("map lookup: " + index.get(equivalent));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AnnotationIdentityDemo.java
java AnnotationIdentityDemo
~~~

Đầu ra:

~~~text
same annotation type: true
getClass is annotation type: false
omitted default equals explicit: true
matching hashCode: true
contract hash formula: true
reversed array: false
different member: false
different annotation interface: false
map lookup: found
~~~

**Giải thích từng phép kiểm tra:**

1. `first.annotationType() == Info.class` là `true`. Ngược lại, `first.getClass() == Info.class` là `false` bởi `getClass()` chỉ lớp implementation của annotation object, không phải chính annotation interface.
2. `@Info(name = "core")` và `@Info(name = "core", ids = {1, 2})` có cùng loại và cùng tất cả member values. `ids` mặc định được đưa vào hợp đồng, nên `equals` là `true`.
3. Hai annotation tương đương có `hashCode` bằng nhau. Công thức `127 * memberName.hashCode() XOR memberValueHash`, cộng qua mọi member, trả đúng hash của `first`.
4. `{2, 1}` và `{1, 2}` chứa cùng tập phần tử nhưng **khác thứ tự**, nên `equals` trả `false`.
5. Thay `name` từ `"core"` sang `"other"` làm khác member value, vì thế `equals` trả `false`.
6. `@OtherInfo(name = "core")` có cùng tên và default member values nhưng là **annotation interface khác**; `equals` vẫn `false`.
7. `HashMap<Info, String>` lưu theo `first` và truy xuất bằng `equivalent` thành công nhờ hợp đồng `equals`/`hashCode`, **không cần cùng identity đối tượng**.

Ví dụ chủ động không in tên lớp proxy hoặc thứ tự `toString()`, bởi đó không phải thuộc tính nên dựa vào khi thiết kế ứng dụng.

## 6. Khác biệt giữa annotation interface và giá trị annotation

Một annotation interface là khai báo kiểu, ví dụ:

~~~java
@interface Security {
    String role();
    boolean enabled() default true;
}
~~~

Mỗi đối tượng annotation runtime chứa các **giá trị thành viên** theo khai báo ấy. `role()` và `enabled()` là các member method không có tham số, không phải Java fields thông thường có thể đặt giá trị tùy ý.

- `Security.class`: token kiểu (annotation interface).
- `someAnnotation.annotationType()`: token kiểu annotation mà object biểu diễn.
- `someAnnotation.role()`: đọc member value từ annotation object.
- `someAnnotation.equals(other)`: so sánh **cả loại và toàn bộ member values**.
- `someAnnotation.hashCode()`: hash từ toàn bộ member values theo công thức chuẩn.

Trên annotation interface, các member method có thể khai báo giá trị `default`. Điều này không giống từ khóa `default` cho Java interface method có body. Annotation member không thực hiện logic nghiệp vụ động như method thông thường.

**Cần `@Retention(RetentionPolicy.RUNTIME)`** khi muốn lấy annotation từ Reflection runtime bằng các API như `Class.getAnnotation`, `Method.getDeclaredAnnotation`, `Field.getAnnotation`. Annotation có retention `SOURCE` hoặc `CLASS` không được hiển thị như annotation runtime bình thường qua các API này.

`@Target` xác định annotation có thể đặt ở đâu. Nó không thay đổi công thức `equals` và `hashCode` cho những annotation cùng loại đã được đọc.

## 7. Dùng `annotationType()`, `equals` và `hashCode` trong framework

### Phân nhóm annotation theo loại

Một framework scan nhiều thành viên có thể dùng:

~~~java
Annotation annotation = element.getAnnotation(Security.class);
if (annotation != null) {
    Class<? extends Annotation> type = annotation.annotationType();
    // type là Security.class.
}
~~~

Đây là đoạn minh họa trong phương thức đã có `AnnotatedElement element`, với `Security` được khai báo trong ví dụ trên và đã giữ tới runtime.

Nếu cần tạo tập các **annotation interfaces có mặt**, dùng `Set<Class<? extends Annotation>>` chứa `annotationType()`, thay vì `getClass()`. Cùng một annotation interface có thể xuất hiện dưới nhiều annotation object khác nhau; kiểu và giá trị là hai chiều dữ liệu riêng.

### Dùng annotation làm khóa

Nhờ hợp đồng so sánh theo giá trị, một annotation runtime có thể được dùng như khóa `Map` hoặc phần tử `Set`, trong phạm vi các dữ liệu annotation phù hợp:

~~~java
Map<Info, String> descriptions = new HashMap<>();
descriptions.put(infoA, "configuration");
String found = descriptions.get(infoB);
~~~

Đoạn này minh họa với `infoA` và `infoB` đã có kiểu `Info`. Nếu chúng `equals`, phép tra cứu có thể tìm cùng entry.

Dù vậy, **không sử dụng `annotation.hashCode()` thay `equals` để quyết định tương đương**: hash có thể va chạm. Nếu tạo implementation tùy chỉnh của annotation interface, hãy tuân thủ đầy đủ hợp đồng `Annotation` (đặc biệt là so sánh mảng, default values, kiểu annotation và công thức hash). Triển khai thiếu chính xác có thể khiến `HashMap` hoặc `HashSet` hoạt động sai.

### Khi chỉ muốn lấy tất cả annotation?

`element.getAnnotations()`, `getDeclaredAnnotations()`, `getAnnotationsByType(...)` phục vụ **tìm annotation** với các quy tắc riêng về khai báo trực tiếp, kế thừa hoặc repeatable annotation. Sau khi có một annotation object, hợp đồng `equals`, `hashCode`, `annotationType` của nó vẫn là hợp đồng trong `java.lang.annotation.Annotation`.

Không nhầm thao tác **lọc nơi đặt annotation** với thao tác **so sánh giá trị annotation**. Hai annotation giống nhau gắn lên các class khác nhau vẫn có thể `equals == true`.

## 8. Những sai lầm dễ gặp

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Dùng `a == b` để xác nhận hai annotation có cùng nội dung | `==` so identity của object | Dùng `a.equals(b)` |
| Dùng `a.getClass()` để xác định loại annotation | Trả implementation class | Dùng `a.annotationType()` |
| Chỉ so member được ghi tường minh | Member dùng default cũng có giá trị | So mọi member theo hợp đồng |
| Dùng `intArray.equals(otherIntArray)` | Mảng không override `Object.equals` để so nội dung | Dùng `Arrays.equals` |
| Xem `ids={1,2}` bằng `ids={2,1}` | Thứ tự trong array member có ý nghĩa | So theo vị trí |
| Dùng `hashCode` bằng nhau để khẳng định annotation bằng nhau | Có thể có hash collision | So bằng `equals` |
| Nhầm `annotationType()` với class được gắn annotation | Một bên là loại annotation, bên kia là annotated element | Giữ riêng `AnnotatedElement` |
| Lệ thuộc kết quả `annotation.toString()` hoặc tên proxy | Javadoc không yêu cầu chuỗi trình bày/implementation cố định | Đọc member values theo API |
| Tự tính hash array bằng `array.hashCode()` | Hash identity không đúng hợp đồng | Dùng `Arrays.hashCode` thích hợp |
| Không kiểm tra `getAnnotation` trả `null` | Khi không có annotation, gọi member sẽ ném NPE | Kiểm tra `null` trước |

### Lưu ý nâng cao: `float` và `double`

Với annotation có member `double threshold()`, không được mô phỏng `equals` bằng cách dùng thuần toán tử `==`. Theo Javadoc của `Annotation.equals`:

- `Double.NaN` so với `Double.NaN` được xem là **bằng** ở member annotation.
- `+0.0` và `-0.0` được xem là **không bằng** ở member annotation.
- `hashCode` cho member số thực dùng `Float.hashCode` / `Double.hashCode` thông qua wrapper, phù hợp với quy tắc `equals`.

Đây là lý do phải dùng hợp đồng `Annotation` thay vì tự viết một bộ so sánh “có vẻ tương đương”.

## 9. Checklist khi làm việc với annotation runtime

1. Xác định annotation có `@Retention(RUNTIME)` và target phù hợp.
2. Lấy annotation bằng đúng Reflection API: `getAnnotation`, `getDeclaredAnnotation` hay `getAnnotationsByType` tùy trường hợp.
3. Kiểm tra kết quả khác `null`.
4. Nếu cần **định danh annotation interface**, dùng `annotationType()`.
5. Nếu cần **so sánh hai annotation**, dùng `equals()`, không dùng identity, không chỉ so `toString()`.
6. Nếu cần làm khóa trong `HashMap`/`HashSet`, tin dùng cặp `equals`/`hashCode` tuân thủ đặc tả; đừng dùng một số hash làm định danh duy nhất.
7. Khi kiểm thử, có ít nhất một trường hợp **giá trị mặc định**, một trường hợp **array member**, một trường hợp **khác annotation interface**.
8. Không viết logic dựa vào class name hoặc cấu trúc implementation nội bộ của annotation proxy.

### Câu hỏi tự kiểm tra

**1.** `@Config(value="x")` với `int level() default 1` có bằng `@Config(value="x", level=1)` không?  
**Có**, nếu là cùng annotation interface và tất cả member values khác cũng giống nhau.

**2.** Hai annotation đặt trên hai class khác nhau có thể `equals` không?  
**Có**, vì nơi đặt annotation không phải member value tham gia so sánh.

**3.** Một annotation có `int[] ids()` trả `{1,2}`, annotation khác trả `{2,1}`. Chúng có bằng nhau không?  
**Không**, vì `Arrays.equals` xét thứ tự các phần tử.

**4.** Khi `a.equals(b)` là `true`, `a.hashCode() == b.hashCode()` có bắt buộc không?  
**Có**. Nhưng cùng hashCode **không** đủ suy ra `equals`.

**5.** Muốn biết annotation được khai báo bằng `@interface Role` hay `@interface Policy`, nên dùng API nào?  
**`annotationType()`**, không phải `getClass()`.

**6.** Mã băm của một member mảng được tính qua `array.hashCode()` hay `Arrays.hashCode(array)`?  
**`Arrays.hashCode(array)`**, dùng overload phù hợp với loại mảng.

**7.** Annotation không có `@Retention(RUNTIME)` thì Reflection chắc chắn nhìn thấy nó ở runtime?  
**Không.** Cần cấu hình retention phù hợp để các API Reflection thông thường trả annotation.

**Ghi nhớ cuối:** `annotationType()` định danh **loại annotation**; `equals()` so **loại và toàn bộ member values** (kể cả default và nội dung mảng); `hashCode()` mã hóa đúng hợp đồng ấy. Cả ba thuộc `java.lang.annotation.Annotation`, không dựa vào identity hoặc tên lớp implementation runtime.

### Tài liệu chính thức

- [Java SE 17 — `java.lang.annotation.Annotation`: `equals`, `hashCode`, `annotationType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Annotation.html)
- [Java SE 17 — `AnnotatedElement`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html)
- [Java SE 17 — `RetentionPolicy`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)
- [Java Language Specification §9.6 — Annotation Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-annotations-012">Annotation không tự thực thi validation hay hành vi nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation không tự thực thi validation hay hành vi nghiệp vụ

## 1. Annotation là metadata, không phải lệnh tự chạy

Java annotation dùng để **gắn thông tin có cấu trúc** lên chương trình: class, field, method, constructor, parameter và các phần tử khác được `@Target` cho phép. Ví dụ:

~~~java
class Registration {
    @RequiredText
    String username;

    @Minimum(18)
    int age;
}
~~~

Đoạn code trên chỉ là minh họa, với hai annotation tự định nghĩa `@RequiredText` và `@Minimum` sẽ được trình bày đầy đủ trong ví dụ runnable phía dưới.

Khi class được tạo hoặc field được gán giá trị, Java **không tự động** đọc `@RequiredText`, tự kiểm tra chuỗi trắng, đọc `@Minimum(18)` rồi từ chối tuổi nhỏ hơn 18. Annotation chỉ mô tả một *quy tắc tiềm năng*. Muốn quy tắc đó có hiệu lực, **phải có thành phần hiểu metadata và thực thi hành vi**:

- Compiler hoặc annotation processor đọc annotation tại thời điểm biên dịch, khi được thiết kế để làm việc đó.
- Framework/container/runner đọc annotation ở runtime rồi gọi một validation engine, interceptor, mapper, test runner hoặc logic nghiệp vụ thích hợp.
- Chính ứng dụng dùng Reflection để tra cứu annotation và **chủ động** chạy mã kiểm tra/tác động.

Cần tách ba tầng khái niệm:

1. **Khai báo**: `@Minimum(18)` đính kèm giá trị 18 vào metadata.
2. **Đọc metadata**: `field.getAnnotation(Minimum.class)` trả thông tin annotation nếu nó có thể được truy cập ở runtime.
3. **Áp dụng quy tắc**: code đọc `minimum.value()`, so sánh giá trị field, quyết định chấp nhận/từ chối và xử lý hậu quả.

Chỉ có tầng 1 không tự sinh ra tầng 3. Ngay cả tầng 2 cũng không làm validation chạy nếu chương trình không dùng metadata để ra quyết định.

**Nói chính xác:** Bản thân một annotation tự định nghĩa không chứa cơ chế “kích hoạt khi giá trị thay đổi”. Java có một số annotation chuẩn được compiler hay công cụ hiểu theo nghĩa riêng, như `@Override` (compiler kiểm tra phương thức override) hoặc `@Deprecated` (cảnh báo sử dụng API). Đây vẫn là **công cụ khác xử lý metadata**, không phải annotation tự chạy một hàm validate ở runtime.

## 2. `@Retention(RUNTIME)` giúp đọc được annotation, nhưng không tạo hành vi

Để Reflection đọc được annotation từ class file, kiểu annotation thường cần `@Retention(RetentionPolicy.RUNTIME)`:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface RequiredText {
}
~~~

Đây là định nghĩa annotation có thể được Reflection đọc ở runtime; **nó chưa kiểm tra bất cứ giá trị nào**.

| Retention policy | Ý nghĩa | `getAnnotation(...)` runtime |
| --- | --- | --- |
| `SOURCE` | Annotation chỉ thuộc phạm vi mã nguồn, không lưu vào class file | Không thấy |
| `CLASS` | Annotation có thể nằm trong class file nhưng không được lưu để Reflection truy cập ở runtime | Không thấy |
| `RUNTIME` | Metadata được giữ để Reflection có thể tra cứu ở runtime | Có thể thấy nếu annotation tồn tại tại phần tử được tra cứu |

`@Target(ElementType.FIELD)` giới hạn vị trí được phép đặt `@RequiredText` lên **field**, không tuyên bố field nào bắt buộc phải chứa chuỗi khác rỗng. Nếu đặt nhầm lên method, compiler có thể từ chối vì `@Target`; đó là quy tắc cú pháp/metadata chứ không phải validation dữ liệu người dùng.

Khi một kiểu annotation không khai báo `@Retention`, mặc định là `RetentionPolicy.CLASS`. Vì thế, nếu viết annotation chỉ để runtime Reflection xử lý, cần xem rõ retention. Tuy nhiên, **`RUNTIME` chỉ là điều kiện để đọc metadata, không phải trigger** kích hoạt engine.

## 3. Ví dụ Java 17+ chạy độc lập: có annotation nhưng vẫn phải gọi validator

Tạo một file duy nhất tên **`AnnotationValidationDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface RequiredText {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Minimum {
    int value();
}

class Signup {
    @RequiredText
    public String username;

    @Minimum(18)
    public int age;

    Signup(String username, int age) {
        this.username = username;
        this.age = age;
    }
}

public class AnnotationValidationDemo {
    static List<String> validate(Object bean) throws IllegalAccessException {
        List<String> errors = new ArrayList<>();
        Field[] fields = bean.getClass().getDeclaredFields();

        // Thứ tự getDeclaredFields() không được bảo đảm.
        Arrays.sort(fields, Comparator.comparing(Field::getName));

        for (Field field : fields) {
            if (field.isAnnotationPresent(RequiredText.class)) {
                Object value = field.get(bean);
                if (!(value instanceof String text) || text.isBlank()) {
                    errors.add(field.getName() + ": required");
                }
            }

            Minimum minimum = field.getAnnotation(Minimum.class);
            if (minimum != null) {
                int value = field.getInt(bean);
                if (value < minimum.value()) {
                    errors.add(field.getName()
                            + ": minimum " + minimum.value());
                }
            }
        }
        return errors;
    }

    static void register(Signup signup) {
        // Chỉ mô phỏng một hành vi nghiệp vụ bằng cách in thông báo.
        System.out.println("registered username="
                + signup.username.trim() + ", age=" + signup.age);
    }

    public static void main(String[] args) throws Exception {
        Signup invalid = new Signup("  ", 16);

        System.out.println("annotation present: "
                + Signup.class.getDeclaredField("username")
                        .isAnnotationPresent(RequiredText.class));

        System.out.println("without validation:");
        register(invalid);

        List<String> errors = validate(invalid);
        System.out.println("errors: " + errors);
        if (errors.isEmpty()) {
            register(invalid);
        } else {
            System.out.println("rejected by explicit validator");
        }

        Signup valid = new Signup("Lan", 23);
        if (validate(valid).isEmpty()) {
            register(valid);
        }
    }
}
~~~

Từ thư mục chứa file, biên dịch và chạy:

~~~bash
javac --release 17 AnnotationValidationDemo.java
java AnnotationValidationDemo
~~~

Output:

~~~text
annotation present: true
without validation:
registered username=, age=16
errors: [age: minimum 18, username: required]
rejected by explicit validator
registered username=Lan, age=23
~~~

### Phân tích chính xác điều vừa xảy ra

1. `@RequiredText` và `@Minimum(18)` chỉ là các annotation đã khai báo trên hai field. Đối tượng `Signup("  ", 16)` vẫn được khởi tạo bình thường: constructor không tự gọi validator.
2. `isAnnotationPresent(RequiredText.class)` trả `true` vì annotation trên field có retention `RUNTIME`. **Nhưng phép đọc metadata này không kiểm tra giá trị `username`**.
3. `register(invalid)` được gọi trực tiếp **trước** khi validation. Nó vẫn chạy, dù `username` toàn khoảng trắng và `age` nhỏ hơn 18. Hàm `register` trong ví dụ chỉ in thông điệp; nếu là nghiệp vụ thật, đây có thể là điểm tạo dữ liệu sai.
4. Chỉ khi code chủ động gọi `validate(invalid)`, validator mới duyệt các field, tra cứu annotation, đọc giá trị bằng Reflection và thực hiện `if`/so sánh.
5. `Minimum.value()` trả giá trị cấu hình **18**; code `value < minimum.value()` mới là **điều kiện kiểm tra**. Nếu điều kiện đúng, validator chủ động thêm thông báo lỗi.
6. `RequiredText` không có member nào, nhưng validator quy ước rằng field được đánh dấu phải là `String` không-null và không chỉ chứa khoảng trắng. Quy tắc nằm trong **code của validator**, không tự sinh ra từ tên annotation.
7. `getDeclaredFields()` không hứa thứ tự trả về. Ví dụ sắp xếp field theo tên trước khi validate nên danh sách lỗi có thứ tự xác định: `age` rồi `username`.
8. Khi truyền object hợp lệ `Signup("Lan", 23)` và validator trả danh sách rỗng, chương trình mới gọi `register(valid)`. Chính nhánh điều khiển `if (errors.isEmpty())` quyết định cho phép thực hiện nghiệp vụ.

Logic tương đương đã được kiểm tra bằng Java 17 API trên JDK, cho đúng kết quả ở trên. Các field trong ví dụ là public để tập trung vào hành vi annotation; với private field, validator còn phải tuân thủ **Reflection access check** và ranh giới module.

## 4. Vì sao metadata không tự bảo vệ bất biến của object?

Annotation được gắn trên **phần tử khai báo**; nó không tự biến phép gán thành setter có điều kiện, không tự thay constructor, và không tự cài interceptor vào mọi lời gọi Java.

Ví dụ có thể tạo và thay đổi object ở nhiều đường:

~~~java
Signup user = new Signup("Lan", 23);
user.age = -1;                   // Field vẫn nhận giá trị -1.
user.username = "  ";           // Annotation không chặn phép gán.
~~~

Đây là đoạn minh họa sử dụng class `Signup` từ chương trình trên, không phải file độc lập. Bởi vì `Signup` khai báo field public và setter/constructor không kiểm tra, mọi caller có quyền Java đều có thể gán giá trị sai, dù annotation tồn tại.

Một validation engine chỉ có hiệu lực **tại những điểm được gọi**. Nếu bạn kiểm tra object trước khi lưu nhưng về sau object bị thay đổi hoặc một luồng code khác bỏ qua bước kiểm tra, dữ liệu xấu vẫn có thể đi qua. Validation ở **ranh giới tin cậy và điểm ghi dữ liệu** quan trọng hơn việc chỉ gắn annotation.

Trong thiết kế production, thường nên:

- Tạo bất biến trực tiếp trong constructor/factory, setter hoặc domain method khi đó là điều kiện luôn phải đúng.
- Gọi validation có chủ đích ở API boundary và trước tác vụ nhạy cảm (ghi database, chuyển tiền, cấp quyền, tạo tài nguyên...).
- Kết hợp ràng buộc cơ sở dữ liệu khi thích hợp, ví dụ `NOT NULL`, `CHECK` hoặc unique constraint. Annotation ở mã Java **không thay thế** những kiểm soát của database.
- Không giả định một đối tượng hợp lệ ở thời điểm kiểm tra sẽ mãi hợp lệ nếu sau đó cho phép mutation.

Điểm khác nhau về vai trò: **annotation biểu diễn chính sách; engine/logic mới thi hành chính sách**.

## 5. Java Reflection đọc annotation bằng API nào?

Một vài API tiêu biểu của `java.lang.reflect.AnnotatedElement` và `Field`:

| API | Công dụng | Có tự chạy validation không? |
| --- | --- | --- |
| `field.isAnnotationPresent(RequiredText.class)` | Hỏi annotation có hiện diện theo quy tắc Reflection không | Không |
| `field.getAnnotation(Minimum.class)` | Lấy annotation instance hoặc `null` | Không |
| `field.getAnnotations()` | Lấy annotation runtime hiện diện trên phần tử theo quy tắc API | Không |
| `field.getDeclaredAnnotations()` | Lấy annotation khai báo trực tiếp trên phần tử | Không |
| `clazz.getDeclaredFields()` | Liệt kê field khai báo trực tiếp trong class | Không |
| `minimum.value()` | Đọc giá trị cấu hình của annotation `@Minimum(...)` | Không |
| `field.get(bean)` / `field.getInt(bean)` | Đọc giá trị runtime của field | Không tự so sánh với annotation |

Annotation có các **element** như `int value();` — đây là thuộc tính cấu hình, không phải thân một hàm xử lý nghiệp vụ. Trong ví dụ `@Minimum(18)`, lời gọi `minimum.value()` đơn thuần trả `18`. Nếu code không thực hiện `if (age < 18)` hoặc tương đương, không có hành vi từ chối.

Lưu ý về inheritance: `@Inherited` chỉ liên quan đến một số quy tắc tìm annotation trên **class**, không có nghĩa annotation đặt trên field/method của superclass tự sao chép sang field/method ở subclass. Một annotation scanner phải quyết định có duyệt superclass, interface, method override hoặc nested object hay không; JVM không tự xây traversal cho nghiệp vụ của ứng dụng.

## 6. Compiler, annotation processor và runtime framework đóng vai trò gì?

### 6.1 Compiler nhận biết một số annotation chuẩn

Ví dụ:

~~~java
class Parent {
    void run() {}
}

class Child extends Parent {
    @Override
    void run() {}
}
~~~

Với `@Override`, compiler kiểm tra phương thức thật sự override/implement method phù hợp. Nếu sai, code có thể không biên dịch. Đó là hành vi của **compiler hiểu annotation chuẩn**; `@Override` không cung cấp một thân method được tự chạy ở runtime. `@Deprecated` cũng được công cụ dùng để cảnh báo các API không khuyến khích.

### 6.2 Annotation processor lúc compile time

Một annotation processor được đăng ký và kích hoạt có thể đọc annotation rồi kiểm tra mã, phát diagnostic hoặc tạo source/resource. Ví dụ framework có thể cung cấp processor cho `@GenerateBuilder`, nhưng việc **chỉ định nghĩa** `@GenerateBuilder` không tự tạo builder nếu không có processor/build pipeline xử lý nó.

Processor thường làm việc trên mô hình compiler (`javax.annotation.processing`, `javax.lang.model`), không phải bằng cách gọi method nghiệp vụ đang chạy. Retention `SOURCE` hoặc `CLASS` có thể đủ cho một số công cụ compile-time; không phải mọi annotation đều cần `RUNTIME`.

### 6.3 Runtime framework/container

Framework có thể đọc metadata lúc khởi tạo hoặc tại từng lời gọi và tổ chức:

- Validator cho các constraint annotations.
- Interceptor/proxy cho transaction, logging, metrics hoặc security.
- Bộ ánh xạ JSON/ORM để liên kết field/property với dữ liệu.
- Test runner tìm các method đánh dấu bằng test annotation rồi chủ động chạy.

Ví dụ `@Transactional` ở một framework cụ thể chỉ có ý nghĩa khi framework tạo và quản lý cơ chế transaction tương ứng. Nếu tự tạo instance bằng `new` ở ngoài container, gọi method từ một đường không đi qua proxy/interceptor, hoặc không cài/chạy framework cần thiết, hiệu ứng mong đợi có thể không xuất hiện. Hành vi chính xác phụ thuộc framework và cấu hình, không phải quy tắc tự chạy chung của Java annotations.

Không nên suy rằng một annotation có tên mang tính mệnh lệnh (như `@Validate`, `@Cacheable`, `@Secure`, `@Audit`) sẽ tự động thực hiện mệnh lệnh ấy.

## 7. Liên hệ với Bean Validation / Jakarta Validation

Trong hệ sinh thái Java, các annotation như `@NotNull`, `@NotBlank`, `@Size`, `@Min` thường gắn với **Jakarta Bean Validation**. Nhưng để các constraint có tác dụng phải có **validation provider/engine** và một điểm thực sự gọi hoặc tích hợp engine.

Quy trình khái niệm:

~~~text
Đối tượng có constraint annotations
           |
           v
Validation provider đọc metadata
           |
           v
Engine kiểm tra các giá trị hiện tại
           |
       +---+---+
       |       |
      hợp lệ   lỗi
       |       |
       v       v
tiếp tục     trả violations / báo lỗi
~~~

Nếu sử dụng Jakarta Validation trong ứng dụng, có thể gọi một `Validator` do provider cung cấp, ví dụ dưới đây là **mã minh họa cần Jakarta Validation API và provider tương ứng**, không phải chương trình Java SE chạy độc lập:

~~~java
// jakarta.validation.Validator validator = ...; // do provider cấp
// Set<ConstraintViolation<UserInput>> errors = validator.validate(input);
// if (!errors.isEmpty()) {
//     // Dừng/ báo lỗi có chủ đích.
// }
~~~

Trong Java SE thuần, không có quy tắc rằng tên annotation tự chế `@RequiredText` hoặc `@Minimum` tương đương constraint chuẩn của Jakarta. Ví dụ `AnnotationValidationDemo` ở mục 3 dùng **validator riêng**. Còn framework như Spring có thể tích hợp validation tại những điểm cụ thể (ví dụ xử lý request, method validation khi đã bật và cấu hình hợp lệ). Không được suy từ việc gắn annotation rằng mọi direct method call hoặc assignment trong toàn ứng dụng đều tự được kiểm tra.

Đặc biệt cần phân biệt:

- **Định nghĩa annotation constraint**: nêu điều kiện cần đạt.
- **Cài validator**: xác định thuật toán đánh giá.
- **Kích hoạt validation**: quyết định *khi nào*, *đối tượng nào*, *nhóm nào* được kiểm tra và *làm gì khi lỗi*.

Ba phần phải phối hợp mới tạo thành cơ chế validation thực sự.

## 8. Từ validation đến hành vi nghiệp vụ: annotation không thay người điều phối

Validation và nghiệp vụ không đồng nhất. Một annotation có thể mô tả điều kiện đầu vào, nhưng **kể cả dữ liệu hợp lệ**, hệ thống vẫn cần tự quyết định nghiệp vụ:

~~~java
List<String> errors = validate(signup);
if (!errors.isEmpty()) {
    throw new IllegalArgumentException(errors.toString());
}
register(signup);
~~~

Đây là đoạn minh họa dùng lại `validate`, `register` và `Signup` từ chương trình đầy đủ. Nếu bỏ nhánh `if` hoặc caller gọi thẳng `register`, logic nghiệp vụ vẫn có thể thực hiện trên dữ liệu không hợp lệ.

Một số câu hỏi phải do thiết kế ứng dụng/engine trả lời, annotation không tự quyết định:

1. Kiểm tra khi nhận HTTP request, trước khi ghi DB, trước khi gửi sự kiện, hay mọi thời điểm?
2. Trả lỗi thế nào: exception, HTTP 400/422, danh sách vi phạm, hay tích hợp UI?
3. Validation failure có rollback transaction, hủy event hoặc giữ audit log không?
4. Có cần kiểm tra cross-field (`startDate <= endDate`), quyền theo user, dữ liệu bên ngoài và trạng thái hiện hành không?
5. Nếu dữ liệu được tạo qua job, import, message queue hoặc Reflection thay vì HTTP, có phải gọi lại validator không?

Framework có thể cung cấp câu trả lời mặc định hoặc extension points, nhưng **câu trả lời đến từ framework và cấu hình**. Không phải thuộc tính tự nhiên của mọi annotation.

## 9. Các lỗi thường gặp

| Hiểu lầm / lỗi | Thực tế | Cách khắc phục |
| --- | --- | --- |
| “Đã đánh dấu `@RequiredText` thì constructor tự chặn null/blank.” | Annotation metadata không chặn phép gán/khởi tạo. | Gọi validator rõ ràng hoặc kiểm tra invariant trong constructor/factory. |
| “Có `@Retention(RUNTIME)` là validation tự chạy.” | `RUNTIME` chỉ giúp Reflection đọc annotation. | Viết/cấu hình component xử lý và một điểm gọi thích hợp. |
| “Đọc `getAnnotation(...)` sẽ làm rule có hiệu lực.” | `getAnnotation` chỉ trả metadata. | Đọc các element rồi thực thi kiểm tra và xử lý kết quả. |
| “`@Min(18)` tự chặn mọi phép gán vào field.” | Field vẫn có thể thay đổi nếu code có quyền gán. | Kiểm tra ở ranh giới ghi hoặc áp dụng invariant trong domain model. |
| “Cài thư viện annotations đủ rồi, không cần provider.” | Constraint cần engine và đường kích hoạt. | Kiểm tra dependency, provider, tích hợp framework và invocation path. |
| “`@Transactional` luôn hoạt động trong mọi direct call.” | Nhiều framework dùng proxy/interceptor và chỉ áp dụng ở đường gọi được quản lý. | Hiểu cơ chế intercept, cấu hình và lifecycle đối tượng. |
| “Annotation khai báo ở superclass chắc chắn được quét ở subclass.” | Quy tắc kế thừa annotation tùy loại phần tử/API và scanner. | Xem `@Inherited` đúng phạm vi, duyệt class hierarchy nếu cần. |
| “Validation đã chạy một lần thì object không thể sai nữa.” | Mutable object có thể thay đổi sau kiểm tra. | Thiết kế immutable/invariant và validate tại điểm tin cậy. |
| “Dữ liệu đã validate là được quyền xử lý nghiệp vụ.” | Hợp lệ kiểu dữ liệu không đồng nghĩa đủ thẩm quyền hay đáp ứng điều kiện business. | Tách input validation, authorization, business rules và transaction. |

### Lưu ý về Reflection access

Nếu validator quét private field, `Field.get(bean)` vẫn chịu kiểm tra truy cập Java. `getDeclaredFields()` có thể tìm được metadata, nhưng không bảo đảm caller được đọc field. `trySetAccessible()` có thể thất bại khi ranh giới named module không mở cho deep reflection. **Không** khắc phục lỗi quyền truy cập bằng cách bỏ validation một cách âm thầm; nên thiết kế public accessor/API hoặc điều chỉnh cấu hình module phù hợp, nếu có thẩm quyền.

### Lưu ý về nguồn tin cậy và bảo mật

Không dùng annotation và validator do ứng dụng tự gọi như **lớp bảo vệ duy nhất** cho permission, tiền bạc, số lượng tồn kho hoặc integrity. Hệ thống cần kiểm tra quyền, trạng thái giao dịch và các ràng buộc ở đúng nơi không thể bị bypass bởi các luồng vào khác. Annotation mô tả có thể là một phần kiến trúc đó, nhưng không có bảo đảm hệ thống sẽ tự thực thi chúng.

## 10. Checklist khi thiết kế annotation runtime

1. Xác định annotation dành cho compile-time, runtime hay tooling; nếu runtime Reflection cần đọc, chọn `@Retention(RUNTIME)`.
2. Xác định vị trí đặt với `@Target`: class, field, method, parameter, constructor, v.v.
3. Định nghĩa rõ ý nghĩa các element, giá trị mặc định, cách xử lý khi dữ liệu null/sai kiểu và thông báo lỗi.
4. Xây dựng hoặc chọn engine đọc metadata; nếu dùng custom annotation, phải có mã xử lý thực tế.
5. Chỉ rõ **điểm gọi**: constructor/factory, HTTP boundary, service method, transaction boundary, trước lưu DB, test runner, v.v.
6. Bảo đảm mọi đường vào cần thiết đều đi qua validation; không để direct call hoặc mutation quan trọng bypass.
7. Định nghĩa kết quả khi vi phạm (throw, return violations, reject/rollback, ghi log) và phân biệt với lỗi nghiệp vụ khác.
8. Kiểm thử cả trường hợp annotation **có mặt nhưng engine không chạy** và trường hợp engine chạy rồi từ chối đúng dữ liệu.
9. Kiểm tra Reflection access/module và class file cuối cùng trong môi trường triển khai.
10. Không trông chờ tên annotation, retention hay Reflection metadata tự tạo side effect.

## 11. Tự kiểm tra

**Câu 1:** `@Minimum(18)` có nghĩa JVM tự ném exception khi field `age` được đặt thành 16 không? **Không.** Chỉ có engine hoặc logic thực tế được gọi mới đánh giá và từ chối.

**Câu 2:** `field.isAnnotationPresent(Minimum.class)` trả `true`. Chứng minh validation đã chạy chưa? **Chưa.** Nó chỉ xác nhận có metadata runtime tương ứng.

**Câu 3:** `@Retention(RUNTIME)` khác `@Target(FIELD)` thế nào? **`Retention`** điều khiển mức lưu giữ annotation; **`Target`** điều khiển vị trí hợp lệ của annotation.

**Câu 4:** Tại sao trong chương trình ví dụ object tuổi 16 vẫn được “registered” ở lần gọi đầu? **Vì chương trình gọi nghiệp vụ trực tiếp trước khi gọi validator.** Không có cơ chế tự chạy validation do riêng annotation sinh ra.

**Câu 5:** Nếu dùng constraint của Jakarta Validation nhưng quên chạy/tích hợp provider thì điều gì xảy ra? **Annotation có thể vẫn tồn tại, nhưng không có bảo đảm constraint tự được đánh giá** trên mọi object hay mọi phép gán.

**Câu 6:** Annotation có thể tác động tới chương trình không? **Có, khi compiler, processor, framework hoặc chính ứng dụng được thiết kế để đọc và xử lý nó.** Không nên nhầm “không tự thực thi” với “không có tác dụng khi được công cụ tích hợp”.

**Ghi nhớ:** Annotation là **metadata về quy tắc hoặc ý định**. `@Retention(RUNTIME)` làm metadata có thể được Reflection quan sát; `getAnnotation` đọc metadata; **engine/caller** mới đánh giá validation, chặn dữ liệu và thực hiện hành vi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)
