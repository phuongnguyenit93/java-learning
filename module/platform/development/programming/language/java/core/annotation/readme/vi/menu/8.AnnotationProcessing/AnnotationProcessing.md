# Annotation Processing

Cho đến đây, ta đã thấy runtime code có thể đọc một `RUNTIME` annotation. Nhưng có những bài toán **không nên đợi application chạy**:

```text
metadata sai
→ muốn build fail ngay

boilerplate có thể suy ra từ source
→ muốn sinh code trước runtime

tool cần tạo resource/index từ declarations
→ muốn artifact đó có sẵn sau compilation
```

Runtime reflection là quá muộn cho các bài toán này. Ta cần một consumer chạy **ngay trong quá trình compile**.

Annotation processing là cơ chế compile-time cho phép tool đọc annotation trong source/language model để:

- validate contract;
- phát compiler diagnostic;
- sinh source/class/resource mới.

Nó không cần application chạy và không phải reflection.

Một ví dụ thực tế:

```text
developer viết @GenerateMapper
        ↓
javac/compile tool phát hiện processor
        ↓
processor đọc model của type được annotate
        ↓
sinh OrderMapperGenerated.java
        ↓
generated source tham gia compilation
```

## <a id="processing-rounds">Annotation processing diễn ra theo nhiều round</a>

Processing không phải “scan annotation một lần rồi gọi processor một lần”.

Mô hình đúng:

```text
round 1
→ root elements ban đầu
→ processor có thể sinh source/class

round 2
→ generated source/class trở thành input mới
→ processor chạy tiếp

...

final round
→ không còn source mới cần xử lý
→ processingOver() = true
```

Ví dụ processor sinh:

```text
Order
→ OrderMapperGenerated
→ generated type lại có annotation khác
→ processor phù hợp có thể thấy annotation đó ở round sau
```

`RoundEnvironment.processingOver()` cho biết đã đến round cuối. `errorRaised()` cho biết round trước đã phát sinh lỗi.

Processor đã được gọi ở một round sẽ tiếp tục được công cụ gọi ở các round sau, kể cả round cuối, và tập annotation truyền vào có thể rỗng.

Vì vậy processor phải được thiết kế **an toàn khi chạy qua nhiều round**.

## <a id="processor-contract">Processor và AbstractProcessor</a>

API chuẩn trải trên hai package chính:

- `javax.annotation.processing` — lifecycle và dịch vụ của annotation processor;
- `javax.lang.model` — mô hình declaration/type mà compiler cung cấp cho processor.

Một processor thường extend `AbstractProcessor`:

```java
public final class MapperProcessor extends AbstractProcessor {

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        // inspect compile-time model
        return true;
    }
}
```

Trước khi dùng các API đó, hãy có mental model:

```text
Element
→ một declaration trong source/model, ví dụ class, method, field, parameter

TypeElement
→ Element đại diện cho type declaration như class/interface/record/annotation interface

TypeMirror
→ biểu diễn một Java type ở compile time

AnnotationMirror
→ biểu diễn một annotation trong compiler model

Elements
→ dịch vụ tiện ích để truy vấn declaration model

Types
→ dịch vụ tiện ích để truy vấn/so sánh type model
```

Processor được khởi tạo bằng `ProcessingEnvironment`, từ đó truy cập:

- `Elements` — dịch vụ tiện ích cho declaration model;
- `Types` — dịch vụ tiện ích cho type model;
- `Messager` — phát compiler diagnostic;
- `Filer` — sinh source/class/resource;
- options và source-version information.

### Return value của `process()`

`process(...)` trả về:

```text
true
→ processor nhận quyền xử lý (claim) các annotation type được đưa vào
→ processor sau không tiếp tục được hỏi về chính tập đã được processor trước nhận xử lý

false
→ annotation chưa bị processor nhận quyền xử lý
→ processor khác vẫn có thể xử lý
```

Vì vậy “luôn return true” không phải thực hành mặc định tốt. Việc nhận quyền xử lý (claim) là **hợp đồng phối hợp** giữa các processor.

Processor làm việc với mô hình compile-time như `Element`, `TypeMirror`, `AnnotationMirror`; nó không nên giả định class của ứng dụng đã được load thành runtime `Class<?>`.

## <a id="supported-types-source-version">Supported annotation types và source version</a>

Processor phải công bố annotation types mà nó hỗ trợ:

```java
@SupportedAnnotationTypes("com.example.GenerateMapper")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class MapperProcessor extends AbstractProcessor {
}
```

Hoặc override API tương ứng.

Hai contract khác nhau:

```text
supported annotation types
→ processor quan tâm annotation nào

supported source version
→ processor hiểu language/source level đến đâu
```

`@SupportedSourceVersion` không phải cách đổi source compatibility của project. Nó chỉ nói về capability của processor.

Processor có thể support wildcard `"*"`. Universal processor có semantics đặc biệt và có thể được invoke ngay cả khi không có annotation present, nên chỉ dùng khi thật sự cần.

### Processor được compiler tìm thấy bằng cách nào?

Processor cũng là Java code, nên nó phải **được compile trước** và có mặt trên processor path/build configuration trước khi nó có thể xử lý source của application.

Mental model:

```text
processor source
→ compile thành processor library/JAR
        ↓
build tool / javac đưa library lên processor path
        ↓
javac discover processor
        ↓
processor được initialize
        ↓
process(...) chạy qua các round
```

Compiler có thể biết processor bằng các cách như:

- service-provider registration, ví dụ `META-INF/services/javax.annotation.processing.Processor`;
- Java module declaration dùng `provides ... with ...`;
- cấu hình processor explicit của compiler/build tool.

Vì vậy chỉ viết một class `extends AbstractProcessor` trong source application **không tự động đảm bảo** processor đó sẽ chạy.

### Processor options với `@SupportedOptions`

Processor có thể công bố các option mà nó hiểu:

```java
@SupportedOptions("mapper.debug")
@SupportedAnnotationTypes("com.example.GenerateMapper")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class MapperProcessor extends AbstractProcessor {
}
```

Compiler/build tool có thể truyền option theo dạng tương đương:

```text
-Amapper.debug=true
```

Processor đọc chúng qua:

```java
String debug =
    processingEnv.getOptions().get("mapper.debug");
```

Option là **input của processor**, không phải annotation element. Dùng option cho build-level configuration; dùng annotation element cho metadata gắn với một declaration/type cụ thể.

## <a id="generated-source">Sinh source và resource</a>

Để ghép toàn bộ mental model, xem một flow tối thiểu end-to-end.

Annotation phía application:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateMapper {
}
```

Source sử dụng annotation:

```java
@GenerateMapper
class Order {
}
```

Processor tìm element được annotate và sinh type mới:

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

Flow quan sát được:

```text
@GenerateMapper trên Order
→ roundEnv tìm thấy Order
→ processor gọi Filer
→ sinh OrderMapperGenerated.java
→ đóng file
→ compiler thấy generated source ở round sau
→ generated source được compile như Java source bình thường
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

Sau khi output được đóng, generated source có thể tham gia round tiếp theo và cuối cùng được compile như Java source bình thường.

`Filer` còn có thể tạo class file hoặc resource.

Khi có originating element, nên truyền nó cho `Filer` nếu phù hợp để build tool có thêm dependency information.

Validation error/warning nên phát qua `Messager`:

```java
processingEnv.getMessager().printMessage(
    Diagnostic.Kind.ERROR,
    "@GenerateMapper requires a class",
    element
);
```

Generated code không phải “runtime magic”. Nó là artifact được tạo trong build và phải compile/behave như source bình thường.

## <a id="processing-vs-reflection">Processing và reflection khác nhau</a>

So sánh:

| | Annotation processing | Reflection |
| --- | --- | --- |
| Thời điểm | compile time | runtime |
| Model chính | `Element`, `TypeMirror`, `AnnotationMirror` | `Class`, `Method`, `Field`, `AnnotatedElement` |
| SOURCE annotation | đọc được | không |
| Cần class được load | không | có |
| Sinh source trước compile | có thể | không phải vai trò |
| Use case | validate/generate | runtime inspection/invocation |

Điểm rất quan trọng:

> `SOURCE` retention hoàn toàn hữu ích cho annotation processor.

Không nên gắn `RUNTIME` chỉ vì processor cần đọc annotation.

### `Class`-valued element ở compile time

Với annotation có element:

```java
Class<?> target();
```

processor không nên dựa vào việc gọi annotation proxy rồi load user class. Trong compile-time context, cách tổng quát và an toàn hơn là làm việc với `AnnotationMirror` / `TypeMirror`. Một số proxy-style access có thể dẫn tới `MirroredTypeException` hoặc `MirroredTypesException` vì type đang tồn tại dưới dạng compiler model chứ chưa phải runtime class.

## <a id="processor-pitfalls">Pitfalls và tính deterministic</a>

Annotation processor nằm trong build pipeline nên tính ổn định rất quan trọng.

### 1. Không sinh cùng file nhiều lần

Nếu mỗi round lại gọi:

```java
createSourceFile("com.example.OrderMapperGenerated")
```

cho cùng type, processor có thể gặp `FilerException`. Hãy thiết kế generation theo stable input và theo dõi artifact đã sinh khi cần.

### 2. Không phụ thuộc thứ tự processor/collection ngẫu nhiên

Output nên deterministic:

```text
cùng source + cùng options
→ cùng generated output
```

Tránh timestamp, random identifier hoặc thứ tự từ unordered collection nếu chúng không mang semantic.

### 3. Không overwrite source của người dùng

Processor nên tạo artifact mới qua `Filer`, không dùng annotation processing như cơ chế sửa source hiện có.

### 4. Tôn trọng nhiều round

Generated type có thể chỉ xuất hiện ở round sau. Đừng kết luận “thiếu type” quá sớm nếu chính pipeline đang sinh nó.

Final round (`processingOver() == true`) phù hợp để cleanup/report final diagnostics; không nên sinh type mà bạn kỳ vọng sẽ có thêm processing round sau đó.

### 5. Nhận quyền xử lý (claim) annotation có chủ đích

Return `true` chỉ khi processor thật sự muốn nhận quyền xử lý annotation type đó. Claim quá rộng có thể ngăn processor khác tham gia.

### 6. Tách build-time và runtime mental model

```text
annotation processor
→ compile-time code generation / validation

runtime framework
→ đọc RUNTIME metadata khi application chạy
```

Hai cơ chế có thể cùng dùng annotation nhưng giải quyết bài toán khác nhau.

### Kết thúc module

Toàn bộ module có thể được nhìn như một pipeline metadata:

```text
định nghĩa metadata vocabulary
→ elements + defaults
→ retention: metadata sống bao lâu
→ target: metadata được đặt ở đâu
→ meta-annotations: cấu hình annotation contract
→ repeatable/inherited: lookup semantics
→ compile-time processor hoặc runtime consumer đọc metadata
```

Khi đi sâu vào runtime inspection, module `reflection` là nơi sở hữu cơ chế reflection chi tiết. Annotation module dừng ở contract metadata và cách các consumer tương tác với contract đó.
