# Xử lý Annotation tại thời điểm biên dịch

Cho đến đây, ta đã thấy mã chạy ở thời gian chạy có thể đọc một Annotation `RUNTIME`. Nhưng có những bài toán **không nên đợi ứng dụng chạy**:

```text
siêu dữ liệu sai
→ muốn quá trình build thất bại ngay

boilerplate có thể suy ra từ mã nguồn
→ muốn sinh mã trước thời gian chạy

công cụ cần tạo tài nguyên/chỉ mục từ các khai báo
→ muốn đầu ra đó có sẵn sau khi biên dịch
```

Reflection ở thời gian chạy là quá muộn cho các bài toán này. Ta cần một thành phần đọc chạy **ngay trong quá trình biên dịch**.

Xử lý Annotation tại thời điểm biên dịch là cơ chế cho phép công cụ đọc Annotation trong mã nguồn/mô hình ngôn ngữ để:

- kiểm tra quy tắc;
- phát thông báo chẩn đoán của trình biên dịch;
- sinh mã nguồn/class file/tài nguyên mới.

Nó không cần ứng dụng chạy và không phải Reflection.

Một ví dụ thực tế:

```text
lập trình viên viết @GenerateMapper
        ↓
javac/công cụ biên dịch phát hiện processor
        ↓
processor đọc mô hình của type được gắn Annotation
        ↓
sinh OrderMapperGenerated.java
        ↓
mã nguồn được sinh tham gia quá trình biên dịch
```

## <a id="processing-rounds">Xử lý Annotation diễn ra qua nhiều vòng</a>

Xử lý Annotation không phải “quét Annotation một lần rồi gọi processor một lần”.

Mô hình đúng:

```text
vòng 1
→ các phần tử gốc ban đầu
→ processor có thể sinh mã nguồn/class file

vòng 2
→ mã nguồn/class file được sinh trở thành đầu vào mới
→ processor chạy tiếp

...

vòng cuối
→ không còn mã nguồn mới cần xử lý
→ processingOver() = true
```

Ví dụ processor sinh:

```text
Order
→ OrderMapperGenerated
→ type được sinh lại có Annotation khác
→ processor phù hợp có thể thấy Annotation đó ở vòng sau
```

`RoundEnvironment.processingOver()` cho biết đã đến vòng cuối. `errorRaised()` cho biết vòng trước đã phát sinh lỗi.

Processor đã được gọi ở một vòng sẽ tiếp tục được công cụ gọi ở các vòng sau, kể cả vòng cuối, và tập Annotation truyền vào có thể rỗng.

Vì vậy processor phải được thiết kế **an toàn khi chạy qua nhiều vòng**.

## <a id="processor-contract">Processor và AbstractProcessor</a>

API chuẩn trải trên hai package chính:

- `javax.annotation.processing` — vòng đời và dịch vụ của annotation processor;
- `javax.lang.model` — mô hình khai báo/type mà trình biên dịch cung cấp cho processor.

Một processor thường kế thừa `AbstractProcessor`:

```java
public final class MapperProcessor extends AbstractProcessor {

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        // đọc mô hình tại thời điểm biên dịch
        return true;
    }
}
```

Trước khi dùng các API đó, hãy có mô hình tư duy:

```text
Element
→ một khai báo trong mã nguồn/mô hình, ví dụ class, method, field, parameter

TypeElement
→ Element đại diện cho khai báo type như class/interface/record/annotation interface

TypeMirror
→ biểu diễn một Java type tại thời điểm biên dịch

AnnotationMirror
→ biểu diễn một Annotation trong mô hình của trình biên dịch

Elements
→ dịch vụ tiện ích để truy vấn mô hình khai báo

Types
→ dịch vụ tiện ích để truy vấn/so sánh mô hình type
```

Processor được khởi tạo bằng `ProcessingEnvironment`, từ đó truy cập:

- `Elements` — dịch vụ tiện ích cho mô hình khai báo;
- `Types` — dịch vụ tiện ích cho mô hình type;
- `Messager` — phát thông báo chẩn đoán của trình biên dịch;
- `Filer` — sinh mã nguồn/class file/tài nguyên;
- các tùy chọn và thông tin phiên bản mã nguồn.

### Giá trị trả về của `process()`

`process(...)` trả về:

```text
true
→ processor nhận quyền xử lý (claim) các kiểu Annotation được đưa vào
→ processor sau không tiếp tục được hỏi về chính tập đã được processor trước nhận xử lý

false
→ Annotation chưa bị processor nhận quyền xử lý
→ processor khác vẫn có thể xử lý
```

Vì vậy “luôn trả về `true`” không phải thực hành mặc định tốt. Việc nhận quyền xử lý (claim) là **quy tắc phối hợp** giữa các processor.

Processor làm việc với mô hình tại thời điểm biên dịch như `Element`, `TypeMirror`, `AnnotationMirror`; nó không nên giả định class của ứng dụng đã được nạp thành `Class<?>` ở thời gian chạy.

## <a id="supported-types-source-version">Các Annotation được hỗ trợ và phiên bản mã nguồn</a>

Processor phải công bố các kiểu Annotation mà nó hỗ trợ:

```java
@SupportedAnnotationTypes("com.example.GenerateMapper")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class MapperProcessor extends AbstractProcessor {
}
```

Hoặc override API tương ứng.

Hai thông tin này có ý nghĩa khác nhau:

```text
các kiểu Annotation được hỗ trợ
→ processor quan tâm Annotation nào

phiên bản mã nguồn được hỗ trợ
→ processor hiểu mức ngôn ngữ/mã nguồn đến đâu
```

`@SupportedSourceVersion` không phải cách đổi mức tương thích mã nguồn của project. Nó chỉ mô tả khả năng của processor.

Processor có thể hỗ trợ wildcard `"*"`. Processor kiểu này có ngữ nghĩa đặc biệt và có thể được gọi ngay cả khi không có Annotation phù hợp xuất hiện, nên chỉ dùng khi thật sự cần.

### Bộ xử lý được trình biên dịch tìm thấy bằng cách nào?

Processor cũng là mã Java, nên nó phải **được biên dịch trước** và có mặt trên processor path/cấu hình build trước khi nó có thể xử lý mã nguồn của ứng dụng.

Mô hình tư duy:

```text
mã nguồn processor
→ biên dịch thành thư viện/JAR của processor
        ↓
công cụ build / javac đưa thư viện lên processor path
        ↓
javac phát hiện processor
        ↓
processor được khởi tạo
        ↓
process(...) chạy qua các vòng
```

Trình biên dịch có thể biết processor bằng các cách như:

- đăng ký service provider, ví dụ `META-INF/services/javax.annotation.processing.Processor`;
- khai báo Java module bằng `provides ... with ...`;
- cấu hình processor tường minh của trình biên dịch/công cụ build.

Vì vậy chỉ viết một class `extends AbstractProcessor` trong mã nguồn ứng dụng **không tự động đảm bảo** processor đó sẽ chạy.

### Tùy chọn của bộ xử lý với `@SupportedOptions`

Processor có thể công bố các tùy chọn mà nó hiểu:

```java
@SupportedOptions("mapper.debug")
@SupportedAnnotationTypes("com.example.GenerateMapper")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class MapperProcessor extends AbstractProcessor {
}
```

Trình biên dịch/công cụ build có thể truyền tùy chọn theo dạng tương đương:

```text
-Amapper.debug=true
```

Processor đọc chúng qua:

```java
String debug =
    processingEnv.getOptions().get("mapper.debug");
```

Tùy chọn là **đầu vào của processor**, không phải phần tử Annotation. Dùng tùy chọn cho cấu hình ở cấp build; dùng phần tử Annotation cho siêu dữ liệu gắn với một khai báo/type cụ thể.

## <a id="generated-source">Sinh mã nguồn và tài nguyên</a>

Để ghép toàn bộ mô hình tư duy, xem một luồng tối thiểu từ đầu đến cuối.

Annotation phía ứng dụng:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateMapper {
}
```

Mã nguồn sử dụng Annotation:

```java
@GenerateMapper
class Order {
}
```

Processor tìm phần tử được gắn Annotation và sinh type mới:

```java
public final class MapperProcessor extends AbstractProcessor {

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        for (Element element :
                roundEnv.getElementsAnnotatedWith(GenerateMapper.class)) {

            String generatedName =
                element.getSimpleName() + "MapperGenerated";

            try {
                JavaFileObject file =
                    processingEnv.getFiler()
                        .createSourceFile(generatedName, element);

                try (Writer writer = file.openWriter()) {
                    writer.write(
                        "final class " + generatedName + " {}"
                    );
                }
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    e.getMessage(),
                    element
                );
            }
        }

        return true;
    }
}
```

Luồng quan sát được:

```text
@GenerateMapper trên Order
→ roundEnv tìm thấy Order
→ processor gọi Filer
→ sinh OrderMapperGenerated.java
→ đóng file
→ trình biên dịch thấy mã nguồn được sinh ở vòng sau
→ mã nguồn được sinh được biên dịch như mã Java bình thường
```

`Filer` cung cấp API chuẩn:

```java
JavaFileObject file =
    processingEnv.getFiler()
        .createSourceFile("com.example.OrderMapperGenerated", sourceElement);

try (Writer writer = file.openWriter()) {
    writer.write("""
        package com.example;

        final class OrderMapperGenerated {
        }
        """);
}
```

Sau khi đầu ra được đóng, mã nguồn được sinh có thể tham gia vòng tiếp theo và cuối cùng được biên dịch như mã Java bình thường.

`Filer` còn có thể tạo class file hoặc tài nguyên.

Khi có phần tử nguồn tương ứng, nên truyền nó cho `Filer` nếu phù hợp để công cụ build có thêm thông tin phụ thuộc.

Lỗi/cảnh báo kiểm tra hợp lệ nên được phát qua `Messager`:

```java
processingEnv.getMessager().printMessage(
    Diagnostic.Kind.ERROR,
    "@GenerateMapper requires a class",
    element
);
```

Mã được sinh không phải “phép màu lúc chạy”. Nó là đầu ra được tạo trong quá trình build và phải biên dịch/hoạt động như mã nguồn bình thường.

## <a id="processing-vs-reflection">Xử lý Annotation và Reflection khác nhau</a>

So sánh:

| | Xử lý Annotation khi biên dịch | Reflection |
| --- | --- | --- |
| Thời điểm | khi biên dịch | thời gian chạy |
| Mô hình chính | `Element`, `TypeMirror`, `AnnotationMirror` | `Class`, `Method`, `Field`, `AnnotatedElement` |
| Annotation `SOURCE` | đọc được | không |
| Cần class được nạp | không | có |
| Sinh mã nguồn trước khi biên dịch xong | có thể | không phải vai trò |
| Trường hợp sử dụng | kiểm tra/sinh mã | kiểm tra/gọi động ở thời gian chạy |

Điểm rất quan trọng:

> Chính sách `SOURCE` hoàn toàn hữu ích cho annotation processor.

Không nên gắn `RUNTIME` chỉ vì processor cần đọc Annotation.

### Phần tử kiểu `Class` tại thời điểm biên dịch

Với Annotation có phần tử:

```java
Class<?> target();
```

Processor không nên dựa vào việc gọi annotation proxy rồi nạp class của người dùng. Trong ngữ cảnh biên dịch, cách tổng quát và an toàn hơn là làm việc với `AnnotationMirror` / `TypeMirror`. Một số cách truy cập qua proxy có thể dẫn tới `MirroredTypeException` hoặc `MirroredTypesException` vì type đang tồn tại dưới dạng mô hình của trình biên dịch chứ chưa phải class ở thời gian chạy.

## <a id="processor-pitfalls">Rủi ro và tính xác định của bộ xử lý</a>

Annotation processor nằm trong quy trình build nên tính ổn định rất quan trọng.

### 1. Không sinh cùng file nhiều lần

Nếu mỗi vòng lại gọi:

```java
createSourceFile("com.example.OrderMapperGenerated")
```

cho cùng type, processor có thể gặp `FilerException`. Hãy thiết kế quá trình sinh mã dựa trên đầu vào ổn định và theo dõi đầu ra đã sinh khi cần.

### 2. Không phụ thuộc thứ tự processor/collection không ổn định

Đầu ra nên có tính xác định:

```text
cùng mã nguồn + cùng tùy chọn
→ cùng đầu ra được sinh
```

Tránh timestamp, định danh ngẫu nhiên hoặc thứ tự từ collection không đảm bảo thứ tự nếu chúng không mang ý nghĩa nghiệp vụ/kỹ thuật cần thiết.

### 3. Không ghi đè mã nguồn của người dùng

Processor nên tạo đầu ra mới qua `Filer`, không dùng annotation processing như cơ chế sửa mã nguồn hiện có.

### 4. Tôn trọng nhiều vòng xử lý

Type được sinh có thể chỉ xuất hiện ở vòng sau. Đừng kết luận “thiếu type” quá sớm nếu chính quy trình đang sinh nó.

Vòng cuối (`processingOver() == true`) phù hợp để dọn dẹp/phát chẩn đoán cuối; không nên sinh type mà bạn kỳ vọng sẽ còn một vòng xử lý bình thường khác sau đó.

### 5. Nhận quyền xử lý (claim) Annotation có chủ đích

Chỉ trả về `true` khi processor thật sự muốn nhận quyền xử lý kiểu Annotation đó. Claim quá rộng có thể ngăn processor khác tham gia.

### 6. Tách mô hình tư duy giữa thời điểm biên dịch và thời gian chạy

```text
annotation processor
→ sinh mã / kiểm tra tại thời điểm biên dịch

framework ở thời gian chạy
→ đọc siêu dữ liệu RUNTIME khi ứng dụng chạy
```

Hai cơ chế có thể cùng dùng Annotation nhưng giải quyết bài toán khác nhau.

Chương tiếp theo tổng hợp toàn bộ cơ chế đã học để trả lời câu hỏi cuối cùng: **khi nào Annotation thực sự là lựa chọn phù hợp, và khi nào một API, interface hoặc cấu hình tường minh sẽ rõ ràng hơn?**
