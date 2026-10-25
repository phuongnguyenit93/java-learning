<a id="back-to-top"></a>

# Giới hạn và lựa chọn

## Menu
- [Reflection không khôi phục đầy đủ mã nguồn hoặc thân phương thức](#limitations-alternatives-001)
- [Tên biến cục bộ và thông tin debug không phải API core reflection thông thường](#limitations-alternatives-002)
- [Generic bị xóa và metadata phụ thuộc nội dung class file](#limitations-alternatives-003)
- [Không có API core reflection tự liệt kê mọi lớp trong classpath](#limitations-alternatives-004)
- [Đổi tên, obfuscation và thay đổi nội bộ có thể làm hỏng liên kết theo chuỗi](#limitations-alternatives-005)
- [Kiểm tra quyền và giới hạn dữ liệu đầu vào chọn lớp hoặc phương thức](#limitations-alternatives-006)
- [Phân biệt reflection runtime với annotation processing lúc biên dịch](#limitations-alternatives-007)
- [So sánh với interface, Strategy, ServiceLoader và code generation](#limitations-alternatives-008)
- [Không dùng reflection chỉ để tránh thiết kế API rõ ràng](#limitations-alternatives-009)
- [Đối chiếu hành vi khi dùng runtime hạn chế reflection hoặc biên dịch trước](#limitations-alternatives-010)

## <a id="limitations-alternatives-001">Reflection không khôi phục đầy đủ mã nguồn hoặc thân phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Reflection không khôi phục đầy đủ mã nguồn hoặc thân phương thức

**Java Reflection cho phép quan sát metadata của kiểu và thành viên tại runtime, đồng thời thực hiện những thao tác như đọc field hoặc gọi method khi có quyền. Nó không có API chuẩn để trả về toàn văn Java source code hoặc thân Java source của một phương thức.** Một `java.lang.reflect.Method` mô tả *phương thức nào* (tên, tham số, kiểu trả về, modifiers, annotations...), chứ không cung cấp *các câu lệnh Java nào* đã viết để triển khai phương thức ấy.

Điều này quan trọng khi thiết kế công cụ quét class, trình tạo tài liệu, mapper, serializer, debugger và framework: có những dữ kiện lấy được từ Reflection, những dữ kiện chỉ có thể suy ra từ bytecode khi còn bytecode, và những dữ kiện đã mất sau quá trình biên dịch nên **không thể phục hồi chính xác chỉ từ `.class`**.

## 1. Phân biệt ba lớp thông tin

| Lớp thông tin | Ví dụ | Công cụ/phạm vi |
| --- | --- | --- |
| **Mã nguồn (`.java`)** | Câu lệnh, cách đặt dấu ngoặc, chú thích, cách đặt tên biến cục bộ, imports, biểu thức viết ban đầu | Trình phân tích source/AST khi có tệp nguồn; Reflection không trả những đoạn này |
| **Class file / bytecode (`.class`)** | Constant pool, descriptors, metadata, attributes và thường có mã lệnh JVM trong `Code` attribute của method thông thường | `javap`, thư viện phân tích class file/bytecode, decompiler; đây là dữ liệu sau biên dịch, không phải nguyên văn source |
| **Runtime Reflection** | `Class<?>`, `Method`, `Field`, `Constructor`, tham số, các annotation được giữ ở runtime; đọc/gọi thành viên nếu truy cập hợp lệ | `java.lang.Class`, `java.lang.reflect`; các API công khai để kiểm tra kiểu/thành viên và invocation |

Một đoạn Java source có thể được biên dịch thành nhiều class, synthetic members, lambda-related constructs hoặc các lệnh bytecode không tương ứng một-một với dòng nguồn. Khi chỉ còn `.class`, thông tin đã bị bỏ đi (ví dụ comment/format/imports) không tồn tại để Reflection “trả lại”.

### Reflection cho biết gì?

Ví dụ với `Method m`, các API có ích:

- `m.getName()`, `m.getDeclaringClass()`, `m.getModifiers()`: tên method, lớp khai báo, modifier.
- `m.getParameterTypes()`, `m.getReturnType()`, `m.getExceptionTypes()`: kiểu tham số runtime, kiểu trả về, danh sách ngoại lệ **được khai báo** trong chữ ký.
- `m.getGenericParameterTypes()`, `m.getGenericReturnType()`: thông tin generic được khai báo và còn được lưu ở metadata phù hợp.
- `m.getParameters()`: đối tượng `Parameter`; tên thực sự của tham số chỉ đáng tin nếu `isNamePresent()` báo có.
- `m.getDeclaredAnnotations()`: các annotation khai báo tại method mà JVM cho phép quan sát bằng Reflection, chủ yếu annotation có retention `RUNTIME`.
- `m.isBridge()`, `m.isSynthetic()`: xác định một số phương thức hỗ trợ do compiler/sinh bytecode tạo.

Reflection cũng có thể đọc/ghi giá trị field khi được phép và gọi một phương thức qua `m.invoke(receiver, args...)`. Tuy nhiên, **giá trị hiện tại hoặc kết quả khi gọi không cho biết câu lệnh nguồn đã tạo ra chúng**.

### Reflection không trả về gì?

Không có phương thức chuẩn như `Method.getBody()` hay `Method.getSourceCode()` trong `java.lang.reflect`. Những điều sau không được Reflection chuẩn khôi phục đầy đủ:

- Toàn văn câu lệnh trong method: vòng lặp, nhánh `if`, biểu thức, lambda ở dạng source và các comment.
- Khoảng trắng, thứ tự import, cách viết literal, phong cách dấu ngoặc, các comment/Javadoc ban đầu.
- Tên và scope của mọi biến cục bộ, tên biến tạm compiler sinh; một số tên có thể hiện diện trong debug attributes, nhưng Reflection không cung cấp API đọc một AST hoàn chỉnh từ đó.
- Đồ thị điều khiển (CFG), danh sách lệnh JVM, stack frames, biểu thức gốc hoặc ánh xạ chính xác từ instruction ngược về từng đoạn source.
- Lý do tác giả chọn một thuật toán hoặc ý định nghiệp vụ đằng sau một method.

Ví dụ hai cách cài đặt `square(int x)`, một dùng `x * x`, một dùng `Math.multiplyExact(x, x)`, có thể có **metadata Reflection gần như giống nhau** mặc dù hành vi khác nhau ở trường hợp overflow. Chỉ nhìn chữ ký `int square(int)` không thể suy ra logic bên trong.

## 2. Tại sao `Method.toString()` và `Method.invoke()` không phải mã nguồn?

Giả sử:

~~~java
public String greet(String name) {
    String message = "Hello, " + name;
    return message.trim();
}
~~~

Gọi `getDeclaredMethod("greet", String.class)` tìm đối tượng `Method` ứng với tên và kiểu tham số. `m.toString()` hoặc `m.toGenericString()` tạo **chuỗi mô tả chữ ký** (modifier, kiểu, tên, danh sách tham số/throws...), không in ra các câu lệnh `String message = ...` hay `return ...`.

`m.invoke(object, "Lan")` thực thi method thực tế và có thể trả `"Hello, Lan"`, nhưng Reflection không ghi nhận một “bản source” của method để trả lại. Việc thử vài input/output để đoán thuật toán cũng không thể chứng minh phương thức được viết bằng các câu lệnh nào.

- `getDeclaredMethods()` liệt kê các method khai báo trực tiếp (có thể gồm bridge/synthetic), không giải mã thân method.
- `getMethods()` tìm các public methods có tính đến kế thừa theo API, vẫn không trả source.
- `Method.getDefaultValue()` nếu có là **default của phần tử annotation type**, không phải câu lệnh `return` của một method thông thường.
- `MethodHandle` hoặc `MethodHandles.Lookup` cho phép liên kết/gọi hành vi theo capability/quyền, nhưng cũng không phải API truy xuất source code.

Một method `abstract` không có implementation trực tiếp; `native` triển khai bằng mã ngoài bytecode Java; method trong bytecode được tạo động có thể không có tệp `.java` tương ứng. Vì vậy việc mong chờ Reflection trả “thân Java source” cho mọi method là sai từ mô hình ban đầu.

## 3. Dữ liệu nào có thể mất tùy cách biên dịch?

### 3.1. Tên tham số: `-parameters`

Trong tệp Java:

~~~java
public void send(String recipient) { }
~~~

`Method.getParameters()[0].getName()` **không phải lúc nào cũng trả `"recipient"`**. Nếu method không có metadata tên tham số phù hợp trong class file, Reflection có thể đặt tên tổng hợp như `"arg0"`; hãy kiểm tra `Parameter.isNamePresent()` trước khi dùng tên vào mapping hoặc tài liệu.

Với các method Java thông thường do `javac` biên dịch:

~~~bash
javac -parameters ReflectionLimitsDemo.java
~~~

Tùy chọn `-parameters` lưu tên tham số của methods/constructors vào `MethodParameters` attribute, nên `isNamePresent()` có thể là `true`. Đây là **tên tham số ở chữ ký**, không phải danh sách mọi biến cục bộ hoặc thân method. Trình biên dịch khác, class do công cụ sinh, record constructor và metadata bị biến đổi có thể có những đặc điểm riêng; hãy dùng chính `isNamePresent()` làm nguồn kiểm chứng.

### 3.2. Generic: erasure và `Signature` attribute

Xét `class Box<T> { T echo(T value) { return value; } }`. Trong mô hình runtime erasure, kiểu thực dùng trong descriptor của `echo` là `Object`. `getReturnType()` trả `Object.class`, trong khi `getGenericReturnType()` có thể trả type variable `T` nhờ thông tin generic signature còn trong class file.

Vì vậy cần phân biệt **runtime type đã erasure** với **mô tả generic ở metadata**. Reflection có thể đọc một phần thông tin generic khai báo (nếu còn), nhưng không suy ngược toàn bộ kiểu của biến cục bộ, cách lập trình viên đặt type argument ở các biểu thức, hay mọi ràng buộc đã biến mất sau erasure.

### 3.3. Annotation: `SOURCE`, `CLASS` và `RUNTIME`

| `RetentionPolicy` | Khi biên dịch và chạy | Reflection runtime |
| --- | --- | --- |
| `SOURCE` | Compiler bỏ annotation khỏi class file | Không đọc được bằng Reflection |
| `CLASS` | Annotation được ghi vào class file nhưng không bắt buộc giữ lại ở runtime | Core Reflection thông thường không trả annotation này |
| `RUNTIME` | Annotation được giữ để đọc phản xạ tại runtime | Có thể đọc bằng `getAnnotation`/`getDeclaredAnnotations` |

Việc class file có lưu một annotation `CLASS` không có nghĩa `Method.getDeclaredAnnotations()` sẽ liệt kê nó. Nếu mục tiêu là đọc annotation dành cho compiler hoặc classfile tools, chọn đúng tầng dữ liệu thay vì mở rộng Reflection vô ích.

### 3.4. Debug information có giới hạn riêng

Class file có thể chứa `SourceFile`, `LineNumberTable` và (tùy cách build) `LocalVariableTable`/`LocalVariableTypeTable`. Những attribute này hỗ trợ debugger, ánh xạ số dòng hoặc công cụ bytecode, nhưng **không dựng lại comment, biểu thức Java, scope và AST một cách chính xác**.

`javac -g` yêu cầu sinh thông tin debug phong phú hơn; `javac -g:none` loại nhiều thông tin debug. `javac -parameters` phục vụ metadata tên tham số cho Reflection và **không đồng nghĩa** với `-g`. Stack trace có thể chứa tên file và số dòng khi metadata tương ứng còn đó, nhưng số dòng không phải thân method.

## 4. Ví dụ Java 17+: kiểm tra chính xác giới hạn Reflection

Lưu toàn bộ ví dụ vào tệp **`ReflectionLimitsDemo.java`**:

~~~java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@interface AtRuntime {}

@Retention(RetentionPolicy.CLASS)
@interface InClass {}

@Retention(RetentionPolicy.SOURCE)
@interface InSource {}

public class ReflectionLimitsDemo {
    @AtRuntime @InClass @InSource
    public String greet(String name) {
        String message = "Hello, " + name;
        return message.trim();
    }

    public static class Box<T> {
        public T echo(T value) {
            return value;
        }
    }

    public static void main(String[] args) throws Exception {
        Method greet = ReflectionLimitsDemo.class
                .getDeclaredMethod("greet", String.class);
        System.out.println("method: " + greet.getName());
        System.out.println("return: " + greet.getReturnType().getSimpleName());
        System.out.println("parameter type: " +
                greet.getParameterTypes()[0].getSimpleName());
        System.out.println("name present: " +
                greet.getParameters()[0].isNamePresent());
        System.out.println("parameter name: " +
                greet.getParameters()[0].getName());
        System.out.println("runtime annotation: " +
                greet.isAnnotationPresent(AtRuntime.class));
        System.out.println("class annotation: " +
                greet.isAnnotationPresent(InClass.class));
        System.out.println("source annotation: " +
                greet.isAnnotationPresent(InSource.class));
        System.out.println("invoke: " +
                greet.invoke(new ReflectionLimitsDemo(), "Lan"));
        Method echo = Box.class.getDeclaredMethod("echo", Object.class);
        System.out.println("erased return: " +
                echo.getReturnType().getSimpleName());
        System.out.println("generic return: " +
                echo.getGenericReturnType().getTypeName());
    }
}
~~~

Biên dịch lần đầu **không dùng `-parameters`** để quan sát sự thiếu vắng metadata tên tham số:

~~~bash
javac --release 17 ReflectionLimitsDemo.java
java ReflectionLimitsDemo
~~~

Output đã kiểm chứng với Java 17 source/API:

~~~text
method: greet
return: String
parameter type: String
name present: false
parameter name: arg0
runtime annotation: true
class annotation: false
source annotation: false
invoke: Hello, Lan
erased return: Object
generic return: T
~~~

Đọc kết quả:

1. Có thể xác định tên, return type, parameter type bằng `Method`; **không có câu lệnh nào** trong thân `greet` xuất hiện trong output Reflection.
2. Tham số có tên khai báo là `name` nhưng Reflection chỉ cho `arg0` và `isNamePresent() == false` do lựa chọn biên dịch.
3. Chỉ annotation có `RetentionPolicy.RUNTIME` được trả qua `isAnnotationPresent`.
4. `invoke` trả giá trị thực tế, còn `getReturnType`/`getGenericReturnType` cho biết những góc nhìn khác nhau về kiểu generic.

Nếu biên dịch lại bằng:

~~~bash
javac --release 17 -parameters ReflectionLimitsDemo.java
java ReflectionLimitsDemo
~~~

Hai dòng thay đổi là:

~~~text
name present: true
parameter name: name
~~~

Các dòng khác vẫn như ví dụ trước. Việc này **không** bổ sung cách truy xuất thân `greet` hay biến cục bộ `message` qua `Method`.

## 5. Khi nào dùng `javap`, bytecode reader hay decompiler?

Nếu có class file mà bạn có quyền phân tích và cần xem cách JVM thực thi một method, hãy dùng **công cụ phân tích bytecode**, không kỳ vọng Reflection làm thay.

~~~bash
javap -c -p ReflectionLimitsDemo
javap -v -p ReflectionLimitsDemo
javap -l -p ReflectionLimitsDemo
~~~

- `javap -c -p`: in lệnh JVM bytecode của method, gồm private members; đó là disassembly, không phải text Java source.
- `javap -v -p`: mô tả chi tiết class file, attributes, constant pool và metadata; hữu ích để phân biệt descriptor, `Signature` và attributes khác.
- `javap -l -p`: in thông tin số dòng và local variable tables **nếu có**; không thể tạo lại những thông tin compiler đã không lưu.

Trong ví dụ, `javap -c` có thể cho thấy các lệnh phục vụ nối chuỗi, gọi `trim()` và trả kết quả. Nó không cho biết lập trình viên đã xuống dòng, viết comment hay đặt dấu ngoặc theo phong cách nào. JDK/compiler còn có thể chọn các cách biểu diễn bytecode khác nhau cho mã nguồn tương đương.

Một **Java decompiler** (ví dụ CFR, Vineflower hoặc công cụ tương đương) phân tích bytecode rồi **suy dựng mã nguồn gần đúng** để đọc hiểu. Chương trình decompile có thể khác mã nguồn ban đầu về tên biến, cấu trúc điều khiển, anonymous/lambda, generic, annotation, cú pháp và khả năng biên dịch lại. Không thể xem output của decompiler là bản gốc hoặc dùng nó như bằng chứng duy nhất về source đã được viết.

Nếu cần kiểm tra chính xác source, hãy ưu tiên **tệp `.java` và thông tin build/commit gốc**. Nếu cần vị trí và cấu trúc cú pháp khi source hiện diện, dùng công cụ phân tích AST như API `com.sun.source.tree` trong javac hoặc parser chuyên dụng. AST là đại diện của source đang được phân tích, khác với đối tượng `Method` ở runtime.

### Lựa chọn công cụ theo câu hỏi

| Nhu cầu thực tế | Hướng phù hợp |
| --- | --- |
| Liệt kê tên, chữ ký, kiểu, modifier, runtime annotation | Core Reflection |
| Gọi method, đọc/ghi field tại runtime (được cấp quyền) | Core Reflection hoặc `MethodHandle`/`VarHandle` |
| Biết implementation gồm các câu lệnh JVM nào | `javap -c`, ASM hoặc thư viện đọc class file |
| Hiểu logic của class chỉ còn bytecode | Decompiler kết hợp kiểm tra disassembly và test; ghi rõ tính suy dựng |
| Lấy nguyên văn source, comment, formatting, import | Kho lưu `.java` / artifact source tương ứng |
| Phân tích cú pháp tại thời điểm build | Java compiler AST / parser chạy trên source |
| Biết method chạy qua những nhánh nào trong thực tế | Debugger, tracer, profiling hoặc instrumentation thích hợp |
| Xem số dòng/biến cục bộ để debug | Debug attributes, debugger, `javap -l` khi metadata tồn tại |

## 6. Các ranh giới cần nhớ khi làm công cụ Reflection

**Đừng xem mọi metadata là có sẵn.** Một class được compiler bình thường tạo khác một class do bytecode generator/proxy tạo; class có thể bị obfuscate hoặc biến đổi, generic signatures/debug attributes bị bỏ, annotation có retention khác nhau. `getDeclaredMethods()` có thể chứa bridge/synthetic methods không có một method source riêng. Thứ tự mảng trả về cũng không được bảo đảm: nếu phải xuất schema ổn định, hãy sắp xếp và lọc theo chính sách rõ ràng.

**Đừng coi thấy metadata là có quyền truy cập.** Có thể tìm thấy khai báo private qua `getDeclaredMethod` nhưng khi invoke vẫn cần quyền phù hợp. Java Platform Module System (JPMS) phân biệt `exports` và `opens`; `setAccessible(true)` không tự vượt mọi ranh giới module. Việc đọc metadata, đọc bytecode và gọi method là ba hoạt động có cơ chế và điều kiện riêng.

**Đừng suy luận source từ behavior.** Output có thể phụ thuộc trạng thái, I/O, thời gian, concurrency hoặc thư viện bên ngoài; hai implementation khác nhau có thể cho cùng output ở nhiều input. Reflection invocation không cung cấp bằng chứng về các nhánh chưa chạy, phép biến đổi bytecode hoặc cấu trúc source ban đầu.

**Đừng lấy tên `arg0` làm dữ liệu nghiệp vụ.** Nếu đang thiết kế router, serializer hay mapper phụ thuộc tên tham số, yêu cầu build lưu `-parameters`, hỗ trợ annotation tên logic, hoặc định nghĩa cơ chế ánh xạ cấu hình rõ ràng. Nếu `!isNamePresent()`, nên trả thông báo cụ thể hoặc fallback có kiểm soát thay vì giả định `arg0` là tên tác giả đặt.

## 7. Nhầm lẫn thường gặp và tự kiểm tra

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`getDeclaredMethod` lấy luôn thân method.” | Chỉ lấy đối tượng `Method` mô tả thành viên; không có `getBody` chuẩn. |
| “`Method.toGenericString` là mã Java đầy đủ.” | Nó biểu diễn chữ ký có thông tin generic, không phải implementation. |
| “Dùng `invoke` có thể lấy lại câu lệnh đã chạy.” | `invoke` thực thi hành vi; không trả về source/AST. |
| “Class file chính là source code được đóng gói.” | Class file là kết quả biên dịch, có descriptors/bytecode và một số metadata được giữ lại. |
| “`javap -c` tạo lại nguyên bản `.java`.” | Nó in bytecode; decompiler cũng chỉ suy dựng source gần đúng. |
| “Tên tham số luôn tồn tại trong `Parameter.getName()`.” | Kiểm tra `isNamePresent()`; `-parameters` có thể cần thiết. |
| “Mọi annotation trong source đều đọc được lúc chạy.” | Core Reflection chủ yếu đọc annotation có `RUNTIME` retention. |
| “Generic source luôn được giữ nguyên sau biên dịch.” | Runtime erasure mất nhiều chi tiết; một phần generic declarations có thể được giữ trong signature metadata. |
| “Số dòng stack trace là nguyên văn nội dung source.” | Nó chỉ là metadata vị trí có điều kiện, không phải câu lệnh. |

Tự trả lời ba câu hỏi:

1. Nếu Reflection cho biết một method trả `String` và nhận `String`, liệu bạn có thể biết nó đang dùng `StringBuilder`, phép nối chuỗi hay thư viện bên ngoài? **Không**; chữ ký không tiết lộ thuật toán.
2. Nếu `Parameter.isNamePresent()` bằng `false`, tên `arg0` có được dùng để khẳng định tác giả đặt tên `arg0` không? **Không**; đó có thể là tên tổng hợp.
3. Khi chỉ còn `.class`, làm thế nào xem instruction và vì sao decompile chưa phải source gốc? Xem `javap -c` hoặc bytecode reader; decompiler **suy dựng** biểu diễn Java từ dữ liệu đã biên dịch, còn thông tin đã bị bỏ thì không thể phục hồi nguyên vẹn.

**Ghi nhớ:** Reflection là công cụ cho **metadata và invocation tại runtime**. `javap`/bytecode analysis trả lời câu hỏi về **class file và lệnh JVM**, còn source parser trả lời câu hỏi về **mã Java đang có**. Phân biệt đúng ba lớp này giúp thiết kế công cụ chính xác, không hứa hẹn phục hồi những dữ liệu Java đã không lưu.

**Tài liệu chính thức:** [`Class` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html), [`Method` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html), [`Parameter` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html), [`RetentionPolicy` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/RetentionPolicy.html), [`javap` command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-002">Tên biến cục bộ và thông tin debug không phải API core reflection thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Tên biến cục bộ và thông tin debug không phải API Core Reflection thông thường

## 1. Vấn đề: Reflection thấy được gì trong một phương thức?

Giả sử một phương thức Java có **tham số** `owner`, `amount` và **biến cục bộ** `fee`, `label`:

~~~java
public static int compute(String owner, int amount) {
    int fee = amount / 10;
    String label = owner + ":" + fee;
    return label.length();
}
~~~

Có ba câu hỏi rất khác nhau:

1. **Phương thức nhận những kiểu tham số nào?** Core Reflection trả lời bằng `Method.getParameterTypes()`, `getGenericParameterTypes()`, `getParameterCount()`.
2. **Tên khai báo của tham số là gì?** `Method.getParameters()` trả về các `Parameter`, nhưng tên gốc `owner`, `amount` chỉ đáng tin khi class file có metadata tương ứng, thường do `javac -parameters` tạo.
3. **Trong thân phương thức có các biến cục bộ nào, tên và giá trị của chúng là gì?** **Không có API `Method.getLocalVariables()` trong Core Reflection**. Tên `fee`, `label` có thể được compiler ghi trong thông tin debug tùy chọn của bytecode, nhưng API `java.lang.reflect` thông thường không cung cấp danh sách đó và cũng không cho đọc giá trị local của một stack frame đang chạy.

Sự khác nhau này quan trọng khi viết công cụ inspector, serializer, dependency injection, framework gọi method động, debugger hoặc công cụ phân tích bytecode. Người học cần tách rõ **cấu trúc của class**, **chữ ký phương thức**, **metadata debug** và **trạng thái stack frame tại một thời điểm**.

Bài học chỉ tập trung vào Java Core và giới hạn Reflection; các công cụ bytecode/debug chuyên dụng được giới thiệu để chọn giải pháp khi cần, không triển khai debugger hoàn chỉnh.

## 2. Field, tham số và biến cục bộ là ba loại dữ liệu khác nhau

| Thành phần | Khai báo điển hình | Nơi tồn tại/ý nghĩa | API phù hợp |
| --- | --- | --- | --- |
| **Instance field** | `private String name;` trong class | Trạng thái của một object | `Class.getDeclaredFields()`, `Field.get(obj)` theo quyền |
| **Static field** | `static int counter;` trong class | Trạng thái gắn với class | `Class.getDeclaredFields()`, `Field.get(null)` theo quyền |
| **Formal parameter** | `String owner, int amount` trong khai báo method | Đầu vào được khai báo trong **chữ ký method/constructor** | `Executable.getParameters()`, `Parameter`, `Method.getParameterTypes()` |
| **Local variable** | `int fee = ...;` trong thân method | Biến theo phạm vi ngôn ngữ trong thân method; có thể được biểu diễn tại local slot/stack hoặc tối ưu hóa | Không có API Core Reflection thông thường để liệt kê/đọc như `Field` |
| **Temporary value** | Giá trị trung gian trong phép tính bytecode | Không nhất thiết có tên source hoặc vị trí lưu ổn định | Công cụ bytecode/debug đặc thù, nếu có bằng chứng phù hợp |

**`Field` chỉ mô tả field**, không đại diện cho `int fee` hay `String label` trong method. Do đó, `getDeclaredFields()` không phải giải pháp “quét tất cả biến” trong class.

**`Parameter` mô tả tham số được khai báo của một `Executable`** (`Method` hoặc `Constructor`), không mô tả mọi local variable trong thân method. Dù biến `fee` được dùng để tính toán, nó không xuất hiện trong `getParameters()`.

**Một runtime local cũng không tương đương một “ô nhớ cố định”.** JVM có khái niệm local-variable array của mỗi frame, nhưng việc ánh xạ tên biến source vào slot phụ thuộc compiler, phạm vi bytecode, tối ưu hóa và thông tin debug tùy chọn; không thể suy ra một cách phổ quát từ API Reflection.

## 3. Core Reflection truy cập metadata tham số đến đâu?

Ví dụ API:

~~~java
Method method = Example.class.getDeclaredMethod(
        "compute", String.class, int.class);

Class<?>[] types = method.getParameterTypes();
Parameter[] parameters = method.getParameters();
~~~

Đây là đoạn minh họa cần các import `java.lang.reflect.Method` và `java.lang.reflect.Parameter` cùng class `Example` chứa method tương ứng; ví dụ runnable đầy đủ nằm ở phần sau.

Những API đáng nắm:

| API | Trả về | Điều không thể suy ra |
| --- | --- | --- |
| `Method.getName()` | Tên method | Không cho tên các biến trong thân method |
| `Method.getParameterCount()` | Số tham số được mô tả trong method descriptor | Không cho tên local |
| `Method.getParameterTypes()` | Các `Class<?>` ứng với kiểu tham số runtime | Không giữ đầy đủ generic arguments |
| `Method.getGenericParameterTypes()` | Metadata kiểu generic được khai báo khi có | Không phải metadata local variables |
| `Method.getParameters()` | Mảng `Parameter` theo thứ tự tham số | Không có nghĩa tên khai báo luôn được lưu |
| `Parameter.getName()` | Tên tham số từ metadata nếu có; có thể là tên tổng hợp `arg0`, `arg1` | Không bảo đảm là tên trong mã nguồn |
| `Parameter.isNamePresent()` | `true` nếu class file có tên cho tham số đó trong **MethodParameters** | Không chứng minh thông tin debug của local có mặt |
| `Parameter.getType()` | Kiểu thô runtime của tham số | Không cho giá trị local |
| `Parameter.isSynthetic()`, `isImplicit()` | Cờ mô tả tham số do compiler tạo/ngầm định nếu được ghi nhận | Không phải phép đo mọi biến tạm trong thân method |

**Quy tắc an toàn:** chỉ hiển thị `getName()` như tên tham số gốc khi `isNamePresent()` trả **true**. Nếu `false`, có thể nhận `arg0`, `arg1`… theo vị trí. Các tên này hữu ích cho hiển thị fallback nhưng không nên xem là hợp đồng nghiệp vụ ổn định.

Cờ `-parameters` dành riêng cho việc lưu tên tham số phương thức/constructor trong class file để Reflection đọc. Không suy từ việc stack trace có số dòng rằng tên tham số đã được lưu.

## 4. Ví dụ Java 17+ có thể chạy và so sánh hai cách biên dịch

Lưu nguyên văn chương trình thành **LocalNameDemo.java**:

~~~java
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class LocalNameDemo {
    public static int compute(String owner, int amount) {
        int fee = amount / 10;
        String label = owner + ":" + fee;
        return label.length();
    }

    public static void main(String[] args)
            throws NoSuchMethodException {
        Method method = LocalNameDemo.class.getDeclaredMethod(
                "compute", String.class, int.class);

        System.out.println("method=" + method.getName());
        System.out.println("parameters=" + method.getParameterCount());

        for (Parameter p : method.getParameters()) {
            System.out.println("param=" + p.getName()
                    + ", present=" + p.isNamePresent());
        }

        System.out.println(
                "return=" + method.getReturnType().getSimpleName());
        System.out.println("result=" + compute("Lan", 20));
    }
}
~~~

**Lượt A — biên dịch với cấu hình `javac` mặc định, không bật `-parameters`:**

~~~bash
javac -encoding UTF-8 LocalNameDemo.java
java LocalNameDemo
~~~

Đầu ra của class được biên dịch **không có MethodParameters chứa tên tham số**:

~~~text
method=compute
parameters=2
param=arg0, present=false
param=arg1, present=false
return=int
result=5
~~~

**Lượt B — biên dịch lại cùng tệp với `-parameters`:**

~~~bash
javac -encoding UTF-8 -parameters LocalNameDemo.java
java LocalNameDemo
~~~

Đầu ra:

~~~text
method=compute
parameters=2
param=owner, present=true
param=amount, present=true
return=int
result=5
~~~

`result=5` vì `fee=20/10=2`, chuỗi `"Lan:2"` có năm ký tự.

Trong cả hai lượt, **Core Reflection vẫn nhận đúng số lượng và kiểu tham số**, vẫn tìm được method bằng `getDeclaredMethod("compute", String.class, int.class)`, và `compute` vẫn chạy như nhau. Thứ thay đổi là **tên tham số được lưu trong class file và hiển thị qua Parameter**.

Quan trọng hơn, dù đã bật `-parameters`, chương trình **không có** một lệnh `method.getLocalVariables()` để liệt kê `fee`, `label` qua Reflection. Đây là hai biến nằm **trong thân method**, khác vị trí metadata của `owner`, `amount`.

**Điều kiện để so sánh đúng:** biên dịch lại thật sự cùng class vào thư mục đang chạy và không dùng file `.class` cũ trong classpath. Các build tool/IDE có thể bật `-parameters` hoặc các tùy chọn compiler theo cấu hình dự án; trong bài thực hành này dùng trực tiếp `javac` với tùy chọn như ghi ở trên.

## 5. Thông tin debug trong bytecode: LocalVariableTable

Class file có thể chứa các attribute tùy chọn phục vụ debug. Với tên biến cục bộ, thành phần thường gặp là **LocalVariableTable** nằm trong thuộc tính **Code** của method.

Một entry của **LocalVariableTable** chứa thông tin như:

| Trường | Ý nghĩa |
| --- | --- |
| `start_pc` | Offset bytecode bắt đầu vùng sống được mô tả |
| `length` | Độ dài vùng bytecode nơi entry có ý nghĩa |
| `name_index` | Tham chiếu tên biến trong constant pool |
| `descriptor_index` | Tham chiếu mô tả kiểu JVM của biến |
| `index` | Slot trong local-variable array của frame |

Đây là **metadata gắn với một vùng bytecode**, không phải một tập giá trị luôn tồn tại và truy cập được từ object **Method**. Cùng một slot có thể được sử dụng lại cho biến khác khi phạm vi sống không chồng lên nhau; một biến trong source có thể không có entry phù hợp trong bytecode được tối ưu hoặc biến đổi.

Ngoài **LocalVariableTable**, class file có thể có **LocalVariableTypeTable** để mô tả chữ ký kiểu generic của một số biến. Sự xuất hiện của những bảng này phụ thuộc compiler, chế độ sinh debug và các bước biến đổi class file.

Các loại metadata hay bị lẫn:

| Attribute/cơ chế | Thông tin | Cách tạo/đọc phổ biến |
| --- | --- | --- |
| **MethodParameters** | Tên và cờ một số tham số method/constructor | `javac -parameters`; `Executable.getParameters()` |
| **LocalVariableTable** | Tên/kiểu/slot/vùng bytecode của local, có thể gồm tham số | `javac -g:vars` hoặc `-g`; xem `javap -l`, bytecode parser, debugger |
| **LocalVariableTypeTable** | Signature kiểu generic cho một số local variable | Debug metadata khi được phát hành |
| **LineNumberTable** | Ánh xạ bytecode offset sang dòng nguồn | `javac` debug lines; debugger và stack trace có thể tận dụng |
| **SourceFile** | Tên tệp nguồn | Debug source metadata |
| **StackMapTable** | Thông tin kiểu dùng cho bytecode verification | **Không** phải danh sách tên local source |

Những bảng này **không thay thế nhau**. Đặc biệt, **`-g:vars` không làm `Parameter.isNamePresent()` thành true** theo hợp đồng API `Parameter`: phương thức đó kiểm tra thông tin tên được khai báo qua **MethodParameters**, không dùng LocalVariableTable để tái tạo tên tham số.

## 6. Tùy chọn javac: -parameters khác -g:vars thế nào?

| Lệnh biên dịch | Tên tham số qua Core Reflection | LocalVariableTable |
| --- | --- | --- |
| `javac LocalNameDemo.java` | Thông thường `arg0`, `arg1`, `isNamePresent=false` | Mặc định `javac` không bật thông tin debug local variables |
| `javac -parameters LocalNameDemo.java` | `owner`, `amount`, `isNamePresent=true` | Không vì thế tự yêu cầu `LocalVariableTable` |
| `javac -g:vars LocalNameDemo.java` | Vẫn không có tên tham số qua `Parameter.isNamePresent()` nếu thiếu `-parameters` | Có thông tin debug local theo khả năng compiler |
| `javac -parameters -g:vars LocalNameDemo.java` | Có tên tham số | Có metadata local |
| `javac -g LocalNameDemo.java` | Không tự tương đương `-parameters` | Sinh đầy đủ thông tin debug, gồm local variables |
| `javac -g:none -parameters LocalNameDemo.java` | Vẫn có tên tham số nhờ `-parameters` | Không yêu cầu debug local variables |

**Lưu ý:** `-g:vars` chỉ chọn metadata cho local variables; nếu cần đồng thời số dòng/tệp nguồn, dùng `-g` hoặc tổ hợp phù hợp như `-g:lines,vars,source`.

Để trực tiếp quan sát cấu trúc trong bytecode, hãy chạy lần lượt:

~~~bash
javac -encoding UTF-8 -parameters -g LocalNameDemo.java
javap -v -p LocalNameDemo
javap -c -l -p LocalNameDemo
~~~

Trong `javap -v`, tìm vùng **MethodParameters** của method **compute**; trong phần **Code** của method, tìm **LocalVariableTable**. `javap -l` giúp liệt kê thông tin dòng và bảng local khi có. Dạng rút gọn minh họa sau cho thấy ý tưởng, **không phải bản sao nguyên vẹn tất cả dòng output**:

~~~text
MethodParameters:
  Name    Flags
  owner
  amount

LocalVariableTable:
  Start  Length  Slot  Name    Signature
  ...
  ...           ...   owner   Ljava/lang/String;
  ...           ...   amount  I
  ...           ...   fee     I
  ...           ...   label   Ljava/lang/String;
~~~

Các số `Start`, `Length`, `Slot` phụ thuộc bytecode compiler tạo ra. Không viết test dựa trên số slot và offset giả định ổn định giữa các bản JDK hoặc các tùy chọn compiler.

**Giải thích quan trọng:** nhìn thấy `fee` và `label` bằng công cụ đọc class file chứng minh bytecode có **metadata debug** về chúng. Điều đó **không tạo thêm phương thức** `getLocalVariables()` cho đối tượng Reflection **Method**, và **không có nghĩa có thể đọc giá trị của chúng từ mọi thời điểm hoặc thread**.

## 7. Tên biến và giá trị biến đang chạy là hai bài toán riêng

### Đọc tên biến từ class file

Một công cụ phân tích bytecode có thể đọc bảng **LocalVariableTable** nếu bảng tồn tại. Nó cần giải mã cấu trúc class file và ánh xạ local slot với phạm vi bytecode; đây là công việc của công cụ class-file/bytecode chứ không phải thao tác `Field.get`.

Nếu class được biên dịch thiếu debug metadata, đã bị strip, obfuscate hoặc biến đổi, tên local gốc có thể không còn. **Không thể bảo đảm khôi phục chính xác tên trong source chỉ từ bytecode**. Công cụ decompiler đôi khi đặt tên phỏng đoán; đó không phải tên gốc được chứng minh.

### Đọc giá trị local trong lúc phương thức đang thực thi

Giá trị runtime của local gắn với **stack frame** và thời điểm thực thi, không phải thuộc tính của class hay instance. Debugger cần dừng/quan sát đúng thread và frame rồi dùng cơ chế debug/VM thích hợp; nó còn phụ thuộc khả năng JVM, thông tin debug, trạng thái tối ưu hóa, phạm vi biến và quyền/khả năng kết nối.

Ví dụ trong debugger, `fee` có thể quan sát được khi execution đang dừng tại dòng sử dụng nó. Nhưng nếu method đã return, frame tương ứng không còn để lấy giá trị local như một field của object. `Method` không thể đọc lại giá trị `fee` chỉ nhờ biết tên method.

**Stack trace** cũng không phải dữ liệu local: `StackTraceElement` có tên class, method, tệp nguồn/dòng khi khả dụng, nhưng không chứa tên và giá trị mọi biến cục bộ. `StackWalker` cho khả năng duyệt frame metadata nâng cao, không phải API đọc tất cả local như debugger.

### Công cụ chuyên dụng khi thật sự cần

| Nhu cầu | Hướng công cụ | Giới hạn cần hiểu |
| --- | --- | --- |
| Hiển thị tên/kiểu tham số public API | Core Reflection `Method.getParameters()` + `-parameters` | Metadata tùy chọn, cần kiểm tra `isNamePresent()` |
| Phân tích danh sách local trong bytecode | `javap`, class-file parser; thư viện ASM khi dự án cần | Không bảo đảm debug metadata tồn tại |
| Dừng chương trình và xem stack frame/local | **JDI/JDWP**, IDE debugger | Có thể thiếu thông tin, giá trị phụ thuộc trạng thái frame, có quyền và phiên debug |
| Instrumentation/agent/VM tooling | **JVMTI**, agent chuyên dụng hoặc công cụ quan sát thích hợp | Phạm vi kỹ thuật cao, phụ thuộc năng lực VM và chính sách triển khai |
| Theo dõi giá trị phục vụ nghiệp vụ | Logging/tracing/telemetry do code chủ động xuất ra | Thiết kế dữ liệu cần ghi; tránh thu thập thông tin nhạy cảm |

Với **JDI** (Java Debug Interface), debugger có thể truy vấn local-variable metadata từ method và đọc giá trị ở stack frame khi hợp lệ; thiếu debug metadata có thể dẫn đến **AbsentInformationException**. Đây là API riêng cho debug, **không đồng nghĩa Core Reflection dùng trong ứng dụng thông thường**.

## 8. Hạn chế thực tế đối với tool sinh tự động

### Đừng dựa vào tên tham số khi chưa xác minh

Nếu viết router gọi method theo tên tham số, trình biên dịch không bật `-parameters` có thể chỉ cho `arg0`, `arg1`. Điều này đặc biệt dễ xảy ra khi thư viện được biên dịch ngoài tầm kiểm soát hoặc build pipeline không nhất quán.

Khi tên tham số quan trọng cho hợp đồng API, hãy:
1. Quy định rõ cờ compile cho source bạn kiểm soát.
2. Dùng `Parameter.isNamePresent()` để phát hiện thiếu metadata.
3. Xác định fallback hoặc báo lỗi cấu hình rõ ràng; không đoán tên tham số từ vị trí.
4. Cân nhắc annotation khai báo key ổn định (ví dụ annotation tự định nghĩa) khi đó thực sự là hợp đồng dữ liệu.

### Đừng biến debug metadata thành API hợp đồng

`LocalVariableTable` được thiết kế cho mục đích debug và có thể thiếu hoặc khác giữa các bản build. Không dùng `fee` hay `label` như key ổn định trong serializer, cấu hình, JSON hoặc giao diện người dùng.

Đổi tên local không làm thay đổi chữ ký method theo cách Reflection dùng để tra cứu `getDeclaredMethod`; nhưng nếu một tool phụ thuộc tên từ bytecode debug thì tool đó trở nên mong manh. Một compiler có thể loại bỏ hoặc thay đổi local khi tối ưu hóa, instrumentation cũng có thể viết lại metadata.

### Đừng kết luận metadata phản ánh tất cả source

Một local trong source có thể bị chia phạm vi, gộp slot hoặc không xuất hiện như một biến riêng biệt có thể quan sát. Một số biến do compiler sinh ra cũng có thể xuất hiện, trong khi biến từ source không còn tên. Chỉ dùng những gì class file thực tế ghi nhận; không khẳng định đầy đủ.

### Không cố truy cập local bằng setAccessible(true)

`AccessibleObject.setAccessible(true)` liên quan đến kiểm tra quyền truy cập **member Reflection** như field/method/constructor trong phạm vi được phép; nó không tạo API truy vấn local của một stack frame. Không có `Field` cho biến `fee` để áp dụng `Field.get`.

## 9. Các nhầm lẫn phổ biến

| Nhầm lẫn | Điều đúng | Cách kiểm chứng |
| --- | --- | --- |
| “`getDeclaredFields()` thấy mọi biến trong class” | Chỉ field được khai báo trên class đó; không liệt kê biến bên trong method | So sánh danh sách Field với source có `fee`, `label` |
| “`getParameters()` trả tên local variable” | Nó trả formal parameter của method/constructor | Xem `owner`, `amount` so với `fee`, `label` |
| “Có `-g` tức là `Parameter.isNamePresent()` chắc chắn true” | `-g` và `-parameters` là hai cơ chế metadata khác nhau | Compile hai cấu hình rồi chạy chương trình |
| “`-parameters` lưu tất cả biến cục bộ” | Nó lưu metadata tên tham số, không bật bảng debug local theo mục đích đó | Dùng `javap -v -p` |
| “`LocalVariableTable` có tên thì `Method` đọc được giá trị local” | Metadata bytecode khác dữ liệu một frame runtime | Kiểm tra API thực tế của `java.lang.reflect.Method` |
| “Stack trace sẽ cho tất cả local values” | Stack trace chứa vị trí gọi/metadata frame, không có danh sách giá trị local | In `new Exception().getStackTrace()` |
| “`arg0` là chắc chắn tên tác giả đặt” | Thường là tên fallback khi thiếu MethodParameters | Kiểm tra `isNamePresent()` |
| “Decompiler luôn khôi phục chính xác tên source” | Có thể tạo tên suy đoán khi thiếu debug metadata | So sánh class biên dịch với `-g:none` và `-g` |

## 10. Thực hành theo trình tự

**Bài 1 — Tên tham số:** chạy `LocalNameDemo` sau khi biên dịch không có `-parameters`, rồi bật cờ này. Ghi lại sự thay đổi của `getName()` và `isNamePresent()`; giải thích vì sao `getParameterCount()` không đổi.

**Bài 2 — Metadata debug:** biên dịch `-g:vars` mà không có `-parameters`. Dùng `javap -v -p` để tìm `LocalVariableTable`; chạy `LocalNameDemo` và giải thích vì sao `Parameter.isNamePresent()` vẫn có thể false.

**Bài 3 — Hai loại metadata cùng xuất hiện:** biên dịch `-parameters -g`, kiểm tra **MethodParameters** và **LocalVariableTable** trong `javap -v`. So sánh `owner`/`amount` với `fee`/`label` ở các bảng khác nhau.

**Bài 4 — Không có debug:** biên dịch `-g:none` rồi kiểm tra bằng `javap -l -p`. Nhận xét những thông tin bị thiếu, và vì sao ứng dụng vẫn chạy đúng dù không có tên local source.

**Bài 5 — Field so với local:** thêm `private int counter;` vào class, dùng `LocalNameDemo.class.getDeclaredFields()`. Chỉ ra field **counter** nhưng không có biến local **fee** hoặc **label**.

**Bài 6 — Thiết kế inspector:** viết hàm nhận `Method`, xuất tên method, danh sách kiểu tham số và tên tham số nếu `isNamePresent()`; nếu thiếu tên, in nhãn **parameter #0**, **parameter #1**. Không phụ thuộc tên fallback như thể đó là tên source.

## 11. Tự kiểm tra kiến thức

**Câu 1.** `Method.getParameters()` và `Class.getDeclaredFields()` có đọc được `fee` trong ví dụ không?  
**Đáp án:** Không. `fee` là local trong thân method; hai API trên mô tả formal parameters và field.

**Câu 2.** Vì sao `getName()` có thể là `arg0` ngay khi tên source là `owner`?  
**Đáp án:** Class file có thể thiếu MethodParameters ghi tên gốc; kiểm tra `isNamePresent()`.

**Câu 3.** Cờ `-parameters` khác `-g:vars` thế nào?  
**Đáp án:** `-parameters` lưu tên formal parameters cho Core Reflection; `-g:vars` lưu thông tin debug local variables trong bytecode.

**Câu 4.** Biết tên `label` từ `javap` có đủ để lấy giá trị hiện tại của nó bằng Reflection không?  
**Đáp án:** Không. Debug metadata không đồng nghĩa một frame đang tồn tại và không tạo API đọc giá trị local trong `java.lang.reflect`.

**Câu 5.** Nếu một framework cần key tên tham số ổn định thì nên làm gì?  
**Đáp án:** Kiểm soát build `-parameters`, xác thực metadata hoặc dùng annotation/metadata hợp đồng tường minh.

**Câu 6.** JDI và JVMTI thuộc cùng cấp API với Reflection thông thường không?  
**Đáp án:** Không. Chúng là cơ chế debugger/VM tooling chuyên biệt với điều kiện hoạt động và quyền riêng.

**Tiêu chí hoàn thành:** bạn giải thích được khác biệt **field – formal parameter – local variable**, chạy hai lần biên dịch cho kết quả `isNamePresent()` khác nhau, nhận diện **MethodParameters** và **LocalVariableTable** khi đọc bytecode, và biết khi nào cần debugger hoặc class-file tooling thay vì cố dùng Reflection.

## 12. Nguồn chuẩn để tra cứu

- [Java SE: `java.lang.reflect.Parameter` và `isNamePresent()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html) — ngữ nghĩa tên tham số theo MethodParameters.
- [Java SE: `java.lang.reflect.Executable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html) — API dùng chung cho Method và Constructor.
- [JDK `javac` command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html) — tùy chọn `-parameters`, `-g`, `-g:vars`.
- [JVMS, Class File Format §4.7](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html) — MethodParameters, LocalVariableTable, LocalVariableTypeTable.
- [Java SE: `com.sun.jdi` package](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jdi/com/sun/jdi/package-summary.html) — cơ chế debug ngoài Core Reflection.

Thông tin debug là nguồn dữ liệu hữu ích khi có mặt và được dùng đúng mục đích; **không phải cam kết rằng API Reflection mặc định có thể khôi phục tên hoặc giá trị của mọi local variable trong một phương thức Java**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-003">Generic bị xóa và metadata phụ thuộc nội dung class file</a>

<details>
<summary>Xem chi tiết</summary>

# Generic bị xóa và metadata phụ thuộc nội dung class file trong Java Reflection

## 1. Hai sự thật phải hiểu cùng lúc

Trong Java, **generic dùng cơ chế type erasure (xóa kiểu)**: phần lớn đối số kiểu như `String` trong `List<String>` không trở thành một kiểu runtime riêng của object. Hai đối tượng `new ArrayList<String>()` và `new ArrayList<Integer>()` đều có lớp runtime là `java.util.ArrayList`; gọi `getClass()` không cho biết chúng đã được khai báo với đối số kiểu nào.

Tuy nhiên, **reflection vẫn có thể đọc nhiều thông tin generic của *khai báo***. Ví dụ, khi một lớp khai báo field `List<String> names`, `Field.getGenericType()` có thể báo `java.util.List<java.lang.String>`, dù `Field.getType()` chỉ trả `java.util.List`. Điều này không mâu thuẫn với erasure: thông tin về **khai báo** có thể được trình biên dịch lưu trong metadata của class file, còn đối số kiểu được dùng tại một vị trí tạo instance thông thường thường không lưu theo từng instance.

Điều quyết định chất lượng kết quả phản chiếu là: **chương trình đang hỏi về đối tượng runtime hay về khai báo được lưu trong class file? Metadata cần thiết có thực sự tồn tại và hợp lệ không?**

Ba lớp thông tin cần phân biệt:

1. **Kiểu khai báo tại thời gian biên dịch:** `List<String> names`; compiler dùng đối số `String` để kiểm tra thao tác tại nơi có đủ thông tin kiểu.
2. **Kiểu thực sự của object tại runtime:** ví dụ `ArrayList.class`, không phải một lớp riêng `ArrayList<String>.class`.
3. **Thông tin generic khai báo còn được giữ trong class file:** type parameter `T`, signature của `List<String>`, generic superclass `Base<Integer>`, method `<U> U copy(U value)`; reflection có thể đọc nếu metadata tương ứng được lưu và được diễn giải thành công.

## 2. Type erasure thực sự làm gì?

Trong các trường hợp phổ biến, trình biên dịch xóa đối số generic khỏi type sử dụng trong bytecode runtime và thay type variable bằng **erasure** của nó:

- `T` không có bound → thường erasure là `Object`.
- `T extends Number` → erasure là `Number` (bound đầu tiên xác định erasure trong trường hợp nhiều bound).
- `List<String>` và `List<Integer>` → cùng kiểu raw runtime `List`.
- Compiler có thể chèn phép cast tại vị trí lấy giá trị generic, và có thể tạo **bridge method** để duy trì tính đa hình sau erasure.

Ví dụ ý tưởng:

```java
// Hai đoạn minh họa riêng: không phải chương trình độc lập.
class Box<T extends Number> {
    T value;
}
class TextBox<T> {
    T value;
}
```

Erasure của field `Box.value` là `Number`; của `TextBox.value` là `Object`. Dù vậy, nếu Signature metadata tồn tại, `getGenericType()` của hai field vẫn có thể cho biết type variable `T` trong khai báo.

**Hệ quả thực tế:**

- Không thể viết `List<String>.class` hoặc `List<Integer>.class`; chỉ có `List.class`.
- Không thể dùng `obj instanceof List<String>` do đối số kiểu không reifiable (trừ những trường hợp wildcard dạng reifiable như `List<?>`).
- Việc overload hai phương thức chỉ khác đối số generic, ví dụ `handle(List<String>)` và `handle(List<Integer>)`, gây xung đột chữ ký sau erasure.
- Không thể viết `new T()` chỉ dựa trên type parameter; cần constructor/factory hoặc `Class<T>` truyền vào với giới hạn thích hợp.
- Không thể mặc định tạo mảng `new List<String>[5]`; cách biểu diễn mảng và generics có quy tắc reifiability khác nhau.
- Reflection có thể giúp đọc metadata khai báo, **không tự làm việc kiểm tra phần tử của một `ArrayList` trở thành an toàn ở runtime**. Tham chiếu raw type/unchecked cast vẫn có thể phá vỡ giả định kiểu và dẫn đến `ClassCastException` ở nơi sử dụng.

Erasure không có nghĩa “Java xóa hết mọi ký tự generic khỏi file .class”. Bytecode descriptor và generic Signature phục vụ mục đích khác nhau.

## 3. Metadata trong class file: descriptor và Signature

Một class file có cấu trúc mô tả lớp, field và method để JVM liên kết, kiểm tra và thực thi. Chẳng hạn một field khai báo `List<String> names` thường có:

- **Field descriptor** biểu diễn kiểu erased như `Ljava/util/List;`; JVM dùng biểu diễn cơ bản này khi giải quyết field.
- **Generic `Signature` attribute** biểu diễn thêm đối số kiểu `String` (được sinh bởi trình biên dịch khi cần). Đây là metadata riêng mà API generic reflection có thể phân tích.

Với field `T value` trong `class Box<T extends Number>`, descriptor thể hiện `Number`, còn Signature có thể lưu dạng tham chiếu đến type variable `T`. Với `class IntegerBox extends Box<Integer>`, Signature của `IntegerBox` có thể lưu việc superclass được tham số hóa bằng `Integer`.

**`Signature` là một thuộc tính class file và không được bảo đảm luôn hiện diện.** Công cụ tạo hoặc biến đổi bytecode, obfuscator/shrinker hay việc cung cấp class file thiếu/sai metadata có thể khiến reflection chỉ thấy kiểu đã erasure hoặc không phân tích được generic signature. Vì thế:

- `Field.getType()`, `Method.getReturnType()` và `Method.getParameterTypes()` trả kiểu `Class<?>` ở mức erased/runtime.
- `Field.getGenericType()`, `Method.getGenericReturnType()`, `Method.getGenericParameterTypes()`, `Class.getGenericSuperclass()` và `Class.getGenericInterfaces()` cố đọc thông tin generic trên **khai báo**, nếu class file có metadata phù hợp.
- `Class.getTypeParameters()`, `Method.getTypeParameters()` hoặc `Constructor.getTypeParameters()` mô tả **type variables do thành phần ấy khai báo**, không phải đối số kiểu thật của mọi instance.
- Một phương thức generic chỉ có descriptor runtime chưa đủ để tái dựng toàn bộ tên/bound/đối số generic trong nguồn; metadata nếu thiếu không thể được suy luận chính xác chỉ từ erased descriptor.

Khi Signature không được ghi cho một khai báo, API `getGeneric...` thường chỉ còn thông tin tương ứng với kiểu raw/erased; không có phép biến đổi thông minh nào khôi phục kiểu nguồn tùy ý một cách chắc chắn.

## 4. Ví dụ Java 17+ chạy được: xem erasure và generic metadata cạnh nhau

Tạo tệp `ErasureMetadataDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;

public class ErasureMetadataDemo {
    static class Repository<T extends Number> {
        T count;
        List<String> labels;
        List<? extends Number> numbers;

        public <U> List<U> same(List<U> values) {
            return values;
        }
    }

    static class IntegerRepository extends Repository<Integer> {
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        List<String> words = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        System.out.println("Same runtime class: "
                + (words.getClass() == ids.getClass()));

        Field count = Repository.class.getDeclaredField("count");
        System.out.println("Field erased: "
                + count.getType().getTypeName());
        System.out.println("Field generic: "
                + count.getGenericType().getTypeName());

        Field labels = Repository.class.getDeclaredField("labels");
        System.out.println("List erased: "
                + labels.getType().getTypeName());
        System.out.println("List generic: "
                + labels.getGenericType().getTypeName());

        ParameterizedType parameterized =
                (ParameterizedType) labels.getGenericType();
        System.out.println("Type argument: "
                + parameterized.getActualTypeArguments()[0].getTypeName());

        Field numbers = Repository.class.getDeclaredField("numbers");
        ParameterizedType wildcardList =
                (ParameterizedType) numbers.getGenericType();
        System.out.println("Wildcard: "
                + wildcardList.getActualTypeArguments()[0].getTypeName());

        Type superclass = IntegerRepository.class.getGenericSuperclass();
        System.out.println("Generic superclass: "
                + superclass.getTypeName());

        Method method = Repository.class.getDeclaredMethod("same", List.class);
        System.out.println("Method erased return: "
                + method.getReturnType().getTypeName());
        System.out.println("Method generic return: "
                + method.getGenericReturnType().getTypeName());

        TypeVariable<?> variable = Repository.class.getTypeParameters()[0];
        System.out.println("Type variable: " + variable.getName());
        System.out.println("Type variable bound: "
                + variable.getBounds()[0].getTypeName());
    }
}
```

Biên dịch và chạy từ thư mục chứa file bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ErasureMetadataDemo.java
java ErasureMetadataDemo
```

Output dự kiến:

```text
Same runtime class: true
Field erased: java.lang.Number
Field generic: T
List erased: java.util.List
List generic: java.util.List<java.lang.String>
Type argument: java.lang.String
Wildcard: ? extends java.lang.Number
Generic superclass: ErasureMetadataDemo$Repository<java.lang.Integer>
Method erased return: java.util.List
Method generic return: java.util.List<U>
Type variable: T
Type variable bound: java.lang.Number
```

**Đọc kết quả theo từng lớp:**

1. `words` và `ids` là hai instance khác nhau, nhưng `getClass()` chỉ thấy cùng implementation runtime `ArrayList`; không có tham số kiểu `String`/`Integer` gắn vào từng object.
2. `count.getType()` là `Number.class` vì `T extends Number` có erasure là `Number`. `count.getGenericType()` trả một `TypeVariable` mô tả `T` đã khai báo.
3. `labels.getType()` trả `List.class`, còn `labels.getGenericType()` trả một `ParameterizedType` có đối số `String`. Dữ liệu này đến từ **chữ ký khai báo field trong class file**.
4. `numbers` chứa một đối số kiểu wildcard; generic reflection có thể trả `WildcardType`, nơi upper bound là `Number`. Không được suy ra danh sách này đang chứa giá trị cụ thể nào.
5. `IntegerRepository.class.getGenericSuperclass()` đọc chữ ký của mệnh đề `extends Repository<Integer>`. Đây là metadata thuộc **khai báo subclass**, không phải mọi instance `Repository` tự biết `Integer`.
6. Method `same(List<U>)` có kiểu trả về erased là `List`, trong khi metadata khai báo cho biết `List<U>`. Vì parameter runtime là raw `List`, việc tìm method phải dùng `getDeclaredMethod("same", List.class)`.
7. `getTypeParameters()` mô tả `T` do `Repository` khai báo; `getBounds()` cho biết bound `Number`. Nó không tự suy ra `T = Integer` từ một instance `new Repository<Integer>()`.

## 5. Các kiểu `java.lang.reflect.Type` và cách đọc đúng

API generic reflection dùng `Type` làm kiểu gốc cho những dạng thông tin phong phú hơn `Class<?>`:

| Dạng `Type` | Mô tả | Ví dụ đọc từ khai báo |
| --- | --- | --- |
| `Class<?>` | Kiểu runtime cụ thể hoặc kiểu raw | `String`, `List`, `int[]` |
| `ParameterizedType` | Kiểu khai báo có đối số kiểu | `List<String>`, `Map<String, Integer>` |
| `TypeVariable<?>` | Tham số kiểu được khai báo bởi class/method/constructor | `T` trong `class Box<T>` |
| `WildcardType` | Wildcard với upper/lower bounds | `? extends Number`, `? super Integer` |
| `GenericArrayType` | Kiểu mảng có component generic không reifiable | `T[]`, `List<String>[]` trong khai báo |

Với `ParameterizedType` có thể xem `getRawType()`, `getActualTypeArguments()` và `getOwnerType()`. Với `TypeVariable` có `getBounds()` và `getGenericDeclaration()`; với `WildcardType` có `getUpperBounds()` và `getLowerBounds()`. Các giá trị trả về có thể là đối tượng `Type` lồng nhau nên phải đi theo cây thông tin, không mặc định ép về `Class<?>`.

**Phân biệt `Type.getTypeName()` và runtime class:** chuỗi `java.util.List<java.lang.String>` là cách biểu diễn metadata kiểu tổng quát, không chứng minh có một `Class` mới mang đúng tên ấy; không nên nối chuỗi đó rồi truyền cho `Class.forName`. Reflection `Type` phục vụ đọc/mô tả cấu trúc kiểu, còn các thao tác như `Class.isInstance` hoạt động ở quan hệ kiểu runtime có thể kiểm tra.

Các API `AnnotatedType`, `getAnnotatedType()` và `getAnnotatedReturnType()` dùng khi cần **annotation gắn vào vị trí sử dụng kiểu** (type-use annotation). Chúng là vấn đề metadata bổ sung, không thay đổi nguyên lý erasure; cần policy retention thích hợp để annotation có thể phản chiếu lúc chạy.

## 6. Vì sao không thể lấy `List<String>` từ mọi object?

Xét đoạn code:

```java
// Minh họa đặt trong một method, không phải chương trình độc lập:
List<String> first = new ArrayList<>();
List<Integer> second = new ArrayList<>();
Class<?> firstClass = first.getClass();
Class<?> secondClass = second.getClass();
System.out.println(firstClass == secondClass); // true
```

Thông tin `String` hoặc `Integer` ở biến nguồn là kiến thức compile-time của trình biên dịch; gọi `first.getClass()` chỉ trả `java.util.ArrayList`. Nếu object được đưa qua API `Object` hay raw `List`, API `getClass()` không thể suy ra đối số generic ban đầu chỉ từ dữ liệu runtime. Việc đọc các phần tử hiện có cũng không chứng minh phần tử tương lai có kiểu nào, và danh sách trống không cung cấp bằng chứng kiểu.

**Một ngoại lệ quan trọng là metadata đã được ghi trong khai báo class cụ thể.** Ví dụ một subclass:

```java
// Khai báo ví dụ, không phải chương trình độc lập:
class StringList extends ArrayList<String> {}
```

`StringList.class.getGenericSuperclass()` có thể mô tả `ArrayList<String>` vì đối số `String` được ghi trong **superclass signature của StringList.class**. Cách thường được gọi là *super type token* (chẳng hạn anonymous subclass) tận dụng quy tắc này. Đây **không phải** reification của `ArrayList<String>` cho mọi object; nó lưu/đọc một khai báo lớp mới, và vẫn tùy thuộc metadata class file. Khi anonymous subclass vẫn còn type variable, việc đọc signature có thể chỉ thấy biến kiểu đó, không tự tìm được đối số cuối cùng của một lời gọi generic bất kỳ.

Nếu API thực sự cần kiểu phần tử lúc chạy, giải pháp thường rõ ràng hơn là **truyền token kiểu tường minh** (ví dụ `Class<T>` cho kiểu có thể biểu diễn bằng `Class`, hoặc một cấu trúc `Type`/`TypeToken` đủ phong phú cho dạng generic), hoặc lưu metadata trong schema/descriptor của ứng dụng. Token kiểu không biến JVM thành bộ kiểm tra nội dung mọi collection.

## 7. Metadata phụ thuộc công cụ biên dịch và xử lý bytecode

Tài liệu hoặc framework không nên mặc định rằng class file luôn có đầy đủ thông tin như trong mã nguồn.

- **Generic `Signature`:** các khai báo có generic signature thường được `javac` ghi vào class file; công cụ bytecode transformation, shrinker hoặc obfuscator có thể bỏ hoặc thay đổi thuộc tính này. Khi không có nó, không thể chắc chắn tái tạo được tất cả type argument từ descriptor đã erasure.
- **Debug information:** các thuộc tính như `LineNumberTable`, `LocalVariableTable` và `LocalVariableTypeTable` phục vụ các nhu cầu khác. `javac -g:none` ảnh hưởng thông tin debug; không nên coi nó là lệnh “xóa toàn bộ generic Signature”. Generic metadata được API `Field`/`Method` đọc không đồng nghĩa thông tin biến cục bộ còn đầy đủ.
- **Tên parameter:** `javac -parameters` ghi metadata tên parameter của method/constructor cho `Parameter.getName()` (khi dữ liệu này có mặt). Điều này khác với `getGenericParameterTypes()`, vốn phục vụ kiểu generic của parameter.
- **Annotation:** chỉ những annotation phù hợp điều kiện lưu giữ runtime mới được các API annotation reflection nhìn thấy. `RetentionPolicy.CLASS` vẫn có thể được ghi trong class file nhưng không phải annotation runtime thông thường; `RetentionPolicy.SOURCE` không tồn tại trong class file sau biên dịch. Generic Signature và annotation là hai kênh metadata khác nhau.
- **Bridge và synthetic method:** compiler có thể tạo bridge method do erasure để bảo toàn dispatch đa hình. Khi quét `getDeclaredMethods()`, công cụ có thể nhìn thấy method do compiler sinh; kiểm tra `Method.isBridge()` và `Method.isSynthetic()` khi lựa chọn phương thức nghiệp vụ, tránh đếm trùng hoặc xử lý nhầm method cầu nối.
- **Binary compatibility:** việc sửa/loại metadata generic có thể không thay đổi ngay descriptor JVM của method, nhưng có thể làm hỏng khả năng phân tích của framework, serializer, mapper hoặc generator cần dữ liệu type đầy đủ.

Nếu Signature bị hỏng hoặc trỏ đến type không hiện diện, các API generic reflection có thể thất bại bằng `GenericSignatureFormatError`, `TypeNotPresentException` hoặc `MalformedParameterizedTypeException` (tùy tình huống). Điều này khác với class đã được load nhưng lỗi quyền gọi method; không dùng `setAccessible(true)` như cách khôi phục Signature bị mất.

## 8. Xem class file để đối chiếu API reflection

Dùng chính file của ví dụ `ErasureMetadataDemo.java` đã biên dịch ở phần 4:

```bash
javap -v 'ErasureMetadataDemo$Repository'
```

Trên PowerShell, có thể dùng cùng cú pháp dấu nháy đơn; trên các shell phổ biến dấu nháy đơn cũng ngăn ký tự `$` bị diễn giải là biến. Kết quả dài và phụ thuộc phiên bản compiler, vì vậy **không nên chép nguyên output** để so khớp từng dòng; hãy tìm các mục:

- `descriptor: Ljava/lang/Number;` cho field `count` cùng `Signature` liên quan type variable `T`.
- `descriptor: Ljava/util/List;` cho field `labels` cùng `Signature` mô tả `List<String>`.
- `Signature` ở khai báo class hoặc method để lưu type parameter, bound và generic return/argument.

`javap -v` là cách quan sát bytecode/classfile trực tiếp; `javap -p` chỉ làm rõ thành viên private, không tự hiển thị mọi generic Signature dạng raw. Nếu bytecode đã bị công cụ xử lý sau build, hãy chạy `javap` trên **class file thực sự được đóng gói/triển khai**, không chỉ bản build tại máy phát triển.

Không lấy sự xuất hiện của generic signature trong ví dụ chuẩn do `javac` biên dịch để kết luận mọi compiler, enhancer, proxy hoặc classfile đều giữ metadata tương tự.

## 9. Quy trình quyết định khi thiết kế công cụ reflection

1. **Bạn muốn kiểm tra object hay khai báo?** Nếu cần kiểm tra object có thuộc kiểu runtime nào, dùng `getClass()`, `instanceof` hoặc `Class.isInstance`. Nếu cần type argument trong field/method/superclass, dùng các API generic reflection trên nơi khai báo.
2. **Giá trị kiểu cần nằm trong instance?** Đừng suy ra từ `List.class`; truyền `Class<T>`, `Type` token hoặc schema tường minh. Với các generic lồng nhau, `Class<T>` một mình không đủ để mô tả `List<String>`.
3. **Thông tin có sẵn trong class file thật không?** Kiểm tra `Signature` bằng `javap -v` hoặc công cụ classfile. Chú ý compiler, obfuscation, bytecode weaving và packaging.
4. **`Type` có dạng nào?** Duyệt `ParameterizedType`, `TypeVariable`, `WildcardType` hoặc `GenericArrayType` theo ngữ nghĩa thay vì cast mù quáng về `Class<?>`.
5. **Có thể thất bại khi đọc metadata không?** Xử lý lỗi class/linkage và signature malformed/missing theo cách framework định nghĩa; không coi metadata thiếu là tín hiệu type argument bằng `Object` trong mọi trường hợp.
6. **Có nguy cơ nhầm generic metadata với runtime enforcement?** Kiểm tra dữ liệu ngoài bằng validation/deserialization ở ranh giới nhập dữ liệu. Metadata chỉ giúp mô tả, không làm an toàn thao tác unchecked.

| Nhu cầu | API/giải pháp | Điều cần nhớ |
| --- | --- | --- |
| Biết runtime class của object | `obj.getClass()` | Không khôi phục `List<String>` từ `new ArrayList<String>()` |
| Biết kiểu field đã xóa | `Field.getType()` | Kết quả là `Class<?>` (ví dụ `List.class`) |
| Biết đối số generic khai báo của field | `Field.getGenericType()` + `ParameterizedType` | Phụ thuộc metadata `Signature` hợp lệ |
| Biết bound của class type parameter | `Class.getTypeParameters()` + `TypeVariable.getBounds()` | Là khai báo `T extends ...`, không phải giá trị T của mọi object |
| Biết superclass tham số hóa | `Class.getGenericSuperclass()` | Chỉ có thông tin được ghi trong chữ ký subclass |
| Biết generic return của method | `Method.getGenericReturnType()` | Khác `getReturnType()` ở mức erased |
| Cần truyền `List<String>` qua API | `Type`/`TypeToken`/schema rõ ràng | Không có `List<String>.class` |

## 10. Tự kiểm tra

**Câu 1.** Vì sao `new ArrayList<String>().getClass() == new ArrayList<Integer>().getClass()` cho kết quả `true`?

**Đáp án:** Cả hai cùng kiểu runtime `ArrayList`. `String` và `Integer` ở đối số generic không tạo hai class runtime độc lập.

**Câu 2.** Tại sao `Field.getGenericType()` vẫn trả `List<String>` dù Java xóa kiểu?

**Đáp án:** Reflection đang đọc metadata generic của **khai báo field**, thường được ghi trong `Signature` của class file; nó không đọc một đối số kiểu reified nằm trong mỗi instance.

**Câu 3.** Với `class Box<T extends Number> { T value; }`, `value.getType()` và `value.getGenericType()` trả về gì khi metadata đầy đủ?

**Đáp án:** `getType()` trả `Number.class` theo erasure; `getGenericType()` trả một `TypeVariable` đại diện cho `T`.

**Câu 4.** Nếu công cụ shrinker xóa Signature, có thể dùng `getType()` để chắc chắn dựng lại `List<Map<String, Integer>>` không?

**Đáp án:** Không. Erased descriptor chỉ lưu `List`. Các đối số generic của khai báo nguồn đã bị mất khỏi metadata dùng để tái dựng.

**Câu 5.** Bật `javac -parameters` có bắt buộc để `Field.getGenericType()` đọc `List<String>`?

**Đáp án:** Không. `-parameters` phục vụ tên tham số method/constructor, còn generic type information dùng cơ chế Signature riêng.

**Câu 6.** Một `Class<?>` đã tải thành công có bảo đảm `getGenericSuperclass()` luôn đọc được không?

**Đáp án:** Không. Signature có thể vắng mặt/hỏng hoặc tham chiếu tới kiểu không hiện diện; các API phản chiếu generic có thể trả thông tin ít hơn hoặc ném lỗi thích hợp.

**Kết luận:** Erasure làm cho các object generic thông thường không giữ đối số kiểu riêng tại runtime; generic reflection có thể đọc *khai báo được ghi trong class file*. Khi xây thư viện hay framework, phải biết metadata lấy từ nguồn nào, giới hạn gì và cách ứng xử khi `Signature` không đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-004">Không có API core reflection tự liệt kê mọi lớp trong classpath</a>

<details>
<summary>Xem chi tiết</summary>

# Java Reflection không có API tự liệt kê mọi lớp trong classpath

Một giới hạn quan trọng của **Java Core Reflection** là: **không có phương thức chuẩn như `Class.getAllClassesInClasspath()` để nhận danh sách mọi class mà ứng dụng có thể sử dụng**.

Reflection rất giỏi trả lời câu hỏi về **một kiểu đã biết**: kiểu tên gì, có những constructor, field, method, annotation nào, có thể tạo instance hay gọi method nào. Nhưng câu hỏi “toàn bộ class trong classpath hiện tại là gì?” thuộc về việc **khám phá các nguồn chứa class, cơ chế class loading, cấu trúc module và môi trường chạy**; Reflection không tự làm công việc đó.

Bài này tập trung vào **ranh giới của Core Reflection**, các khái niệm dễ bị lẫn, cách khám phá class trong phạm vi xác định và lựa chọn thiết kế phù hợp khi cần xây dựng plugin scanner, test runner, annotation scanner hoặc framework.

## 1. “Liệt kê mọi lớp” thực sự có thể mang nhiều ý nghĩa

Trước khi chọn API, cần xác định mục tiêu:

| Câu hỏi thực tế | Tập dữ liệu muốn tìm | Có thể giải quyết bằng gì? |
| --- | --- | --- |
| “Class `com.example.User` có thuộc tính nào?” | Metadata của **một class đã biết** | `User.class`, `Class.forName`, `Class.getDeclaredFields` |
| “Class `Service` khai báo các nested class nào?” | Class/interface thành viên được **khai báo bên trong một class** | `Service.class.getDeclaredClasses()` |
| “Trong thư mục `build/classes` có file `.class` nào?” | Những **entry vật lý** trong một cây thư mục xác định | `Files.walk` với `java.nio.file` |
| “JAR `plugins.jar` chứa những entry `.class` nào?” | Entry thuộc **một JAR cụ thể** | `JarFile`, `JarEntry` |
| “Những implementation nào đã đăng ký cho SPI?” | Provider theo cơ chế đăng ký dịch vụ | `ServiceLoader` |
| “Trong module `m` có tài nguyên nào?” | Resource của **module đã xác định** | `ModuleReader.list()` với quyền/môi trường tương ứng |
| “JVM đã nạp những class nào?” | Các class **đã được load** tại thời điểm quan sát | `Instrumentation.getAllLoadedClasses()` qua Java agent, không thuộc Core Reflection |
| “Mọi class có thể được nạp bởi tất cả class loader, cả trong tương lai?” | Tập class tiềm năng có thể thay đổi động | **Không có cách tổng quát, đầy đủ cho mọi môi trường Java** |

Hai tập dữ liệu sau đây khác nhau:

- **Class *có mặt* trong một nguồn**: ví dụ file `com/acme/Plugin.class` nằm trong JAR. Chưa chắc được loader hiện tại tìm thấy, chưa chắc link thành công hay được truy cập.
- **Class *đã được nạp***: JVM đã tạo runtime `Class<?>` tương ứng qua một class loader. Một class được đóng gói trong JAR nhưng chưa hề được dùng có thể **chưa** được nạp.

Không được suy luận rằng mọi class đã nạp đều có file `.class` nằm trên `java.class.path`, hoặc rằng mọi file `.class` trên disk đều là một runtime class hợp lệ, có thể gọi được.

## 2. Core Reflection biết cấu trúc của class đã có tên

Các API quen thuộc:

```java
Class<?> type = String.class;

System.out.println(type.getName());
System.out.println(type.getDeclaredMethods().length > 0);
```

Khi đã có `Class<?>`, ta có thể duyệt:

- `getDeclaredFields()`: field khai báo trực tiếp, kể cả non-public (tìm thấy chưa đồng nghĩa có quyền truy cập).
- `getDeclaredMethods()`: method được khai báo trực tiếp, không tự cộng mọi method thừa kế.
- `getDeclaredConstructors()`: constructor khai báo trực tiếp.
- `getDeclaredAnnotations()`: các annotation hiện diện trực tiếp mà Reflection có thể quan sát tại runtime.
- `getDeclaredClasses()`: **member classes/interfaces khai báo trực tiếp bên trong class này**.

`getDeclaredClasses()` thường bị hiểu sai. Nó **không** duyệt tất cả class trong cùng package, JAR hoặc classpath; nó chỉ mô tả class thành viên theo quan hệ khai báo trong class hiện tại. Local class hoặc anonymous class cũng không được “quét toàn bộ” từ lời gọi này.

Ví dụ:

```java
class Container {
    static class Nested {}
}

class Independent {}
```

`Container.class.getDeclaredClasses()` có thể trả `Container.Nested`; nó không tự trả `Independent` chỉ vì hai class ở cùng file nguồn, cùng package hay cùng thư mục biên dịch.

### Biết tên khác với khám phá tất cả tên

`Class.forName("com.acme.SomeType")` thử tìm class theo **tên đã có**. API này không cung cấp bộ duyệt tên class. Biến thể một tham số có thể kích hoạt khởi tạo class.

```java
// Minh họa cho trường hợp đã biết tên class và có class loader phù hợp.
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Class<?> type = Class.forName("com.acme.SomeType", false, loader);
```

`false` nghĩa là không yêu cầu khởi tạo class trong lệnh này. Nó **không có nghĩa** không load/link, không phát sinh lỗi liên quan class loading, hay class được tìm thấy chắc chắn có thể truy cập từ caller. Nếu class không tồn tại trong phạm vi loader: `ClassNotFoundException`. Khi có vấn đề linkage có thể nhận `LinkageError`.

**Class name chính xác** dùng cú pháp binary name, ví dụ nested class có thể có tên `com.acme.Outer$Inner`; dấu `$` trong tên nhị phân không nhất thiết đồng nghĩa một class member được khai báo theo cách đơn giản.

## 3. Vì sao classpath không phải một danh mục class duy nhất?

### 3.1. Classpath có nhiều nguồn và không mang sẵn “danh sách class”

Ứng dụng Java có thể lấy bytecode từ:

- Cây thư mục chứa các file `.class` sau biên dịch.
- JAR truyền qua `-cp` / `--class-path`; wildcard classpath hoặc manifest `Class-Path` có thể mở rộng nguồn mà runtime dùng.
- Class loader của application server, framework, test runner hoặc hệ thống plugin.
- Nguồn dữ liệu tùy biến: bộ nhớ, network, file mã hóa, lớp được sinh động, v.v.
- **Module path** trong ứng dụng JPMS, vốn có quy tắc riêng; không thể đơn giản đánh đồng với classpath.

`System.getProperty("java.class.path")` phản ánh một cấu hình classpath liên quan launcher/runtime, nhưng **không đại diện đầy đủ mọi nguồn của mọi class loader**. Không nên chỉ tách chuỗi này rồi kết luận đã tìm hết class trong một JVM.

### 3.2. Nhiều class loader có những “tầm nhìn” khác nhau

Hai class loader có thể định nghĩa class mang **cùng binary name** mà vẫn tạo ra **hai runtime type khác nhau**. Identity của một class gắn với cả tên nhị phân và defining class loader.

Ví dụ, plugin loader A có thể tìm thấy `com.plugins.PluginA` trong khi system loader không tìm thấy. Cùng tên đó cũng có thể tồn tại ở plugin loader B nhưng được xem là class khác.

Nhiều class loader không công bố tất cả URL/path nguồn nạp class, và một số nguồn thậm chí không phải file hoặc JAR thông thường. Không có hợp đồng trong `java.lang.ClassLoader` yêu cầu mọi subclass phải cung cấp “liệt kê tất cả class có thể nạp”.

### 3.3. Class có thể chưa được nạp hoặc không có file trực tiếp

Việc JVM nạp class thường diễn ra theo nhu cầu. Chương trình có thể chứa hàng ngàn file `.class` nhưng chỉ thực sự load một phần trong phiên chạy. Ngược lại, runtime có thể tạo class trong bộ nhớ hoặc dùng **hidden class** cho mục đích triển khai; những class đó không nhất thiết có tên có thể tìm lại qua `Class.forName`.

Còn có khác biệt về classpath, module path, các runtime image `jrt:/` và cách đóng gói của framework. Một thuật toán “duyệt tất cả file trong một thư mục” vì vậy không thể mặc định đúng cho mọi môi trường.

## 4. Các API dễ bị tưởng nhầm có chức năng quét classpath

| API | Thực tế trả về | Không bảo đảm |
| --- | --- | --- |
| `Class.forName(name)` | Class tương ứng **một tên đã cung cấp**, nếu tìm/nạp được | Tự tìm mọi tên class |
| `Class.getDeclaredClasses()` | Member classes/interfaces khai báo trực tiếp trong một class | Tất cả class cùng package/JAR/classpath |
| `Package.getPackages()` | Thông tin những package đã được định nghĩa theo cơ chế package runtime | Tên mọi class trong mỗi package |
| `ClassLoader.getDefinedPackages()` | Những package do loader đó đã định nghĩa | Mọi class trong nguồn loader có thể dùng |
| `ClassLoader.getResources(name)` | URL của những **resource có đúng tên được yêu cầu** | Glob `**/*.class` hoặc directory listing phổ quát |
| `ClassLoader.resources(name)` | Stream của các resource có một tên | Tập toàn bộ resource/class |
| `ModuleLayer.boot().modules()` | Các module đã có trong boot layer | Class của tất cả module hay tất cả layer |
| `Instrumentation.getAllLoadedClasses()` | Những class **đã load** tại thời điểm gọi, thông qua agent instrumentation | Tất cả class có thể load trong tương lai |

Đặc biệt, lời gọi sau **không** phải API quét package:

```java
var urls = Thread.currentThread()
        .getContextClassLoader()
        .getResources("com/acme/plugins");
```

Nó tìm resource mang **tên chính xác** `com/acme/plugins` (nếu có), không có nghĩa nó trả danh sách mọi file bên dưới. Trong JAR, “directory entry” có thể không tồn tại; URL có thể dùng nhiều giao thức khác nhau; module có các quy tắc encapsulation riêng đối với resource. Nếu một đoạn scanner dựa vào URL `file:` và `jar:` thì nó mới xử lý **hai loại nguồn được hỗ trợ**, chưa phải toàn bộ classpath.

## 5. Cách làm đúng khi cần một danh mục class trong phạm vi xác định

Trước hết hãy chọn **phạm vi hữu hạn, kiểm soát được**: ví dụ thư mục `out/` hoặc một tập JAR ứng dụng đã chỉ định. Tiếp theo:

1. **Liệt kê nguồn**: xác định chính xác thư mục/JAR/module/plugin location sẽ quét.
2. **Liệt kê entry**: duyệt file `.class` bằng `Files.walk` hoặc `JarFile` (hay API phù hợp riêng từng nguồn).
3. **Chuyển resource path thành ứng viên binary name**: `com/acme/Widget.class` → `com.acme.Widget`.
4. **Lọc và chống trùng**: loại `module-info`, `package-info` nếu cần, xử lý nested class, multi-release JAR và các tên trùng giữa nguồn.
5. **Xác minh khả năng load** với class loader đúng ngữ cảnh khi thật sự cần; thường dùng `Class.forName(name, false, loader)` để tránh yêu cầu khởi tạo ngay.
6. **Kiểm tra contract**: interface, annotation runtime, method/constructor, module accessibility và quyền ứng dụng trước khi instantiate/invoke.
7. **Giới hạn tài nguyên**: giới hạn path, số file, thời gian quét, kích thước JAR, số class ứng viên; ghi nhận lỗi từng nguồn thay vì nuốt exception.

**Đọc tên file** không thực thi class, nhưng **nạp class** có thể gây phát sinh việc đọc bytecode, giải quyết phụ thuộc, linkage error và hành vi tùy chỉnh của class loader. Không nên nạp và khởi tạo bừa bãi mọi tên tìm được chỉ để lọc annotation.

### Ví dụ A — Duyệt một thư mục class

Nếu có output của `javac -d out ...`:

```text
out/
└── com/
    └── demo/
        ├── Alpha.class
        ├── Beta.class
        └── Outer$Nested.class
```

`Files.walk(Path.of("out"))` có thể duyệt những file vật lý trong cây này. Chuyển path tương đối sang dấu chấm cho ra `com.demo.Alpha`, `com.demo.Beta` và `com.demo.Outer$Nested` — đó là **ứng viên theo file**, không phải mọi class loader đều load được từng ứng viên.

Nếu thư mục gốc bị chỉ sai, hoặc class không nằm trực tiếp trên nguồn mà loader sử dụng, tập tên có thể khác với thực tế runtime.

### Ví dụ B — Duyệt một JAR

`JarFile` có thể liệt kê `JarEntry` chứa `.class` trong **JAR cụ thể**. Đó là công cụ đọc nội dung archive, thuộc `java.util.jar`, không phải API liệt kê class của Core Reflection.

Một số chú ý khi quét JAR thực tế:

- `module-info.class` là mô tả module, `package-info.class` thường không phải implementation cần đăng ký.
- Multi-release JAR có thể chứa entry dưới `META-INF/versions/<version>/`. Không được biến nguyên path phiên bản thành binary name như class thông thường. Cần chọn phiên bản phù hợp runtime hoặc dùng công cụ hiểu multi-release.
- JAR trong JAR (*nested/fat JAR*) có thể yêu cầu cơ chế đọc và class loader riêng của framework; không thể coi mọi nested entry là class loadable trực tiếp.
- Cùng một binary name có thể có mặt trong nhiều JAR, nhưng loader thực tế có thể chỉ chọn một phiên bản theo thứ tự/thuật toán riêng.
- Class có trong JAR chưa bảo đảm phụ thuộc đầy đủ, đủ quyền JPMS hay khởi tạo an toàn.

## 6. Chương trình Java 17+: đối chiếu Reflection với việc liệt kê một nguồn

Ví dụ dưới đây làm **hai việc tách biệt**:

- Dùng `Class.forName` để truy cập một class đã biết tên, không khám phá tên.
- Quét **một root do người chạy truyền vào**: thư mục output hoặc một file `.jar`. Chương trình chỉ thu thập **tên ứng viên**, tuyệt đối không tự load mỗi class tìm được.

Để code độc lập và kết quả ổn định, ta tạo một class nested `ExamplePlugin` ngay trong cùng file. Chương trình cố ý chỉ hỗ trợ thư mục và JAR thường; không nhận là scanner tổng quát cho mọi class loader, module hoặc nested/multi-release JAR.

Tạo **`ClasspathInventoryDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClasspathInventoryDemo {
    public static class ExamplePlugin {}

    private static String className(String relative) {
        if (!relative.endsWith(".class")) {
            throw new IllegalArgumentException("Không phải .class: " + relative);
        }
        return relative.substring(0, relative.length() - 6)
                .replace('/', '.').replace('\\', '.');
    }

    public static List<String> findClassNames(Path root) throws IOException {
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .map(Path::toString)
                        .filter(name -> name.endsWith(".class"))
                        .map(ClasspathInventoryDemo::className)
                        .forEach(names::add);
            }
        } else if (Files.isRegularFile(root)
                && root.getFileName().toString().endsWith(".jar")) {
            try (JarFile jar = new JarFile(root.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory()
                            && name.endsWith(".class")
                            && !name.startsWith("META-INF/versions/")) {
                        names.add(className(name));
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Cần thư mục hoặc JAR: " + root);
        }
        Collections.sort(names);
        return List.copyOf(names);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Dùng: java ClasspathInventoryDemo <root>");
        }
        Class<?> known =
                Class.forName("ClasspathInventoryDemo$ExamplePlugin");
        System.out.println("Known: " + known.getName());

        List<String> names = findClassNames(Path.of(args[0]));
        System.out.println("Candidates: " + names.size());
        System.out.println("Contains plugin: "
                + names.contains("ClasspathInventoryDemo$ExamplePlugin"));
    }
}
```

**Biên dịch và chạy trên thư mục** với JDK 17 trở lên:

```bash
javac -encoding UTF-8 -d out ClasspathInventoryDemo.java
java -cp out ClasspathInventoryDemo out
```

**Kết quả:**

```text
Known: ClasspathInventoryDemo$ExamplePlugin
Candidates: 2
Contains plugin: true
```

Có hai file `.class` vì `javac` sinh một cho outer class `ClasspathInventoryDemo` và một cho nested class `ExamplePlugin`.

**Tạo JAR rồi thử cùng chương trình:**

```bash
jar --create --file demo.jar -C out .
java -cp out ClasspathInventoryDemo demo.jar
```

**Kết quả vẫn là:**

```text
Known: ClasspathInventoryDemo$ExamplePlugin
Candidates: 2
Contains plugin: true
```

Trong lệnh thứ hai, class được dùng để chạy chương trình vẫn được load từ `out`, còn `demo.jar` chỉ là **archive được chương trình đọc**. Chúng là hai hoạt động độc lập. Dùng `java -cp demo.jar ClasspathInventoryDemo demo.jar` cũng có thể chạy ví dụ này vì JAR chứa các class vừa biên dịch, nhưng code vẫn chỉ quét đúng một JAR được chỉ định.

**Đọc code cẩn thận:**

- `Class.forName("ClasspathInventoryDemo$ExamplePlugin")` thành công vì đã biết tên nhị phân và class có thể load; lời gọi này **không** phát hiện `ExamplePlugin` bằng cách liệt kê.
- `Files.walk(root)` chỉ duyệt cây thư mục chỉ định; luôn đóng `Stream<Path>` bằng try-with-resources.
- `JarFile.entries()` chỉ liệt kê entry trong một JAR chỉ định; luôn đóng `JarFile`.
- `className` chỉ chuyển tên đường dẫn có đuôi `.class` thành chuỗi. Đây chưa phải kiểm tra tên hợp lệ, chưa kiểm tra metadata và chưa cấp quyền load.
- `Collections.sort` làm thứ tự kết quả ổn định. Những class trùng giữa nhiều root sẽ cần cơ chế hợp nhất riêng.
- Bộ quét JAR trong ví dụ **cố ý bỏ qua** `META-INF/versions/`, nên không dùng nó để báo cáo chính xác toàn bộ lớp phiên bản trong multi-release JAR.
- Thí dụ không xử lý `module-info.class`, `package-info.class` hoặc nested JAR nếu tình cờ xuất hiện; công cụ thực tế phải lọc các entry đó theo mục đích.

Nếu kết quả `Candidates` thay đổi khi chạy trong thư mục chứa file bổ sung, đó là bình thường vì scanner đếm **entry trong root đã chỉ định**, không phải số class đã nạp trong JVM.

## 7. “Tìm các class có annotation” cần thêm một tầng khám phá

Giả sử bạn muốn các class được gắn `@MyPlugin`.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface MyPlugin {}
```

**Đoạn khai báo annotation trên chỉ minh họa**, muốn biên dịch độc lập cần thêm import từ `java.lang.annotation` và đặt trong file hợp lệ.

Không tồn tại:

```java
// PSEUDOCODE: API này KHÔNG tồn tại trong Java Core Reflection.
List<Class<?>> types = Reflection.findAllAnnotatedClasses(MyPlugin.class);
```

Thực tế một scanner phải:

1. Xác định những root/module/source được quét.
2. Tìm entry hoặc metadata ứng viên bằng filesystem/JAR/API phù hợp.
3. Có thể phân tích metadata từ bytecode mà **không load class**, hoặc chọn load class theo class loader và kiểm soát lỗi.
4. Với class đã load được và annotation lưu ở runtime, gọi `clazz.isAnnotationPresent(MyPlugin.class)` / `getAnnotation`.
5. Kiểm tra contract/constructor/quyền truy cập rồi mới tạo instance và thực thi.

`isAnnotationPresent` xử lý **một** `Class<?>`, không tự cung cấp tập `Class<?>` ban đầu.

### Khi nào nên dùng thư viện scanner?

- Nếu cần duyệt cả **classpath phức tạp**, package filtering, nested JAR hoặc các loại loader, hãy cân nhắc thư viện có hỗ trợ môi trường đó, ví dụ **ClassGraph**, hoặc scanner của framework mà dự án đang dùng.
- Nếu đã dùng Spring, cơ chế component scanning/classpath metadata scanning của framework có thể phù hợp; việc Spring tự tìm bean **không biến tính năng đó thành API của `java.lang.reflect`**.
- Với môi trường plugin có contract rõ ràng, thường nên tránh quét toàn bộ classpath: dùng registry rõ ràng, tên plugin trong cấu hình, hoặc **`ServiceLoader`**.

Khi dùng thư viện, cần kiểm tra version/khả năng tương thích với JDK, JPMS, cách đóng gói và custom class loader. Không được kỳ vọng thư viện thứ ba tự phát hiện mọi class có thể xuất hiện động trong tương lai.

## 8. Nếu chỉ muốn plugin: `ServiceLoader` thường đúng bài toán hơn

Nhiều chương trình dùng Reflection scan cả classpath chỉ để tìm mọi implementation của một interface. Nếu bạn kiểm soát hợp đồng đăng ký plugin, **Service Provider Interface (SPI)** là lựa chọn rõ nghĩa hơn:

```java
// Phác thảo: PaymentPlugin là interface mà ứng dụng định nghĩa.
ServiceLoader<PaymentPlugin> plugins =
        ServiceLoader.load(PaymentPlugin.class);

for (PaymentPlugin plugin : plugins) {
    plugin.execute();
}
```

Đây là đoạn minh họa chưa độc lập vì ứng dụng cần định nghĩa `PaymentPlugin` và khai báo provider trong `META-INF/services/<tên.interface>` cho classpath, hoặc `provides ... with ...` trong module descriptor khi dùng named modules.

Điểm khác biệt:

- `ServiceLoader` liệt kê **provider đã được khai báo** cho một service cụ thể, không liệt kê mọi class.
- Có thể khám phá provider rồi khởi tạo/lấy instance theo yêu cầu (phụ thuộc cách sử dụng API).
- Mô hình này thể hiện rõ plugin contract và tránh việc scan mọi class để đoán đâu là plugin.
- Vẫn phải xử lý provider cấu hình sai, thiếu dependency, lỗi khởi tạo và giới hạn của loader/module phù hợp.

Nếu mục tiêu thực sự chỉ là “tìm plugin được đăng ký”, việc thêm một danh sách provider rõ ràng là hành vi thiết kế tốt hơn so với quét classpath không giới hạn.

## 9. Nếu muốn “các class đang được nạp”: Java agent khác Core Reflection

`java.lang.instrument.Instrumentation` có phương thức `getAllLoadedClasses()` cho các class **đã load vào JVM** khi gọi. Cần một instrumentation agent hợp lệ được cấp đối tượng `Instrumentation` (thường thông qua `premain` hoặc `agentmain` và manifest/thiết lập phù hợp).

Phương thức này:

- Không phải API từ `java.lang.Class` hay `java.lang.reflect`.
- Không xuất hiện tùy ý chỉ bằng `new Instrumentation()` (đây là interface mà agent nhận do JVM cung cấp).
- Không liệt kê mọi class có thể load về sau hoặc mọi entry của JAR chưa được dùng.
- Có thể trả những class từ nhiều class loader, kể cả các class không nằm trên application classpath.
- Chỉ trả snapshot tại một thời điểm, không phải bản đồ bất biến cho toàn bộ đời sống ứng dụng.

Đối với công cụ quan sát runtime, profiling và instrumentation, hướng này phù hợp hơn việc cố gắng suy diễn tập class đã load bằng quét file. Nhưng với scanner để tìm plugin có thể **chưa được load**, API này lại không thay thế bước khám phá nguồn.

## 10. Named modules và hình ảnh runtime Java 9+

Với Java Platform Module System (JPMS), đường đi không đơn giản chỉ là `-cp`. `ModuleLayer.boot().modules()` có thể cho biết module trong boot layer, nhưng đó là tập **module**, chưa phải toàn bộ class của mỗi module. Layer tự tạo sau đó cũng không nhất thiết nằm trong boot layer.

Để xem resource bên trong một module **đã được xác định**, hệ sinh thái `java.lang.module` có `ModuleReference.open()` và `ModuleReader.list()`; cách này vẫn phải bắt đầu từ module được giải quyết trong cấu hình chạy và tôn trọng giới hạn API. **Nó không phải phương thức Core Reflection tự liệt kê mọi classpath entry**.

Các class JDK còn có thể được lưu trong runtime image qua `jrt:/` thay vì những JAR kiểu `rt.jar` như các JDK rất cũ. Do đó không nên viết scanner chỉ đi tìm `rt.jar` và kết luận đã liệt kê được toàn bộ JDK.

**Phân biệt:** đọc được tên `.class` trong một module chưa bảo đảm consumer được phép gọi class đó. Java modules dùng `exports` cho API truy cập bình thường và `opens` cho một số trường hợp deep reflection; class loader và module access đều quan trọng.

## 11. Những bẫy thiết kế phổ biến

### Bẫy A — Dùng `ClassLoader.getResources("com/foo")` rồi coi URL trả về là danh sách package

API đó chỉ tìm **resource theo tên**, và một package không nhất thiết có directory entry trong JAR. Nó cũng không bảo đảm trả mọi nguồn class. Hãy giới hạn rõ nguồn, hỗ trợ từng scheme, hoặc dùng scanner có chủ đích.

### Bẫy B — Quét `java.class.path` và tuyên bố “đã tìm mọi class”

System property không mô tả đầy đủ custom loaders, named modules, generated classes, agent activity hoặc môi trường đặc thù. Tên trùng giữa các nguồn cũng đòi hỏi xét loader và thứ tự ưu tiên.

### Bẫy C — Nạp tất cả class để xem annotation

Điều đó có thể làm tăng I/O, memory footprint, gây class loading/linkage error và kích hoạt hành vi ngoài dự kiến khi dùng API có initialize. Ưu tiên lọc bằng metadata/bytecode trước; khi cần load hãy dùng loader đúng và `Class.forName(name, false, loader)`, với xử lý lỗi đầy đủ.

### Bẫy D — Coi file `.class` là plugin an toàn

Một file bytecode có thể có class phụ thuộc bị thiếu, class abstract, quyền truy cập không phù hợp, constructor thiếu hoặc logic nguy hiểm. Cần validate contract, allowlist và chính sách khởi tạo; quét tên chỉ là bước đầu.

### Bẫy E — Quét cả classpath quá rộng trong mỗi request

Classpath có thể rất lớn. Quét toàn bộ trên đường xử lý request vừa tốn chi phí vừa tăng khả năng lỗi. Giới hạn package/root, dùng cache theo vòng đời ứng dụng, chủ động invalidate khi class loader/redeploy thay đổi; với môi trường build kiểm soát được có thể sinh index ngay khi build.

### Bẫy F — Quên sự khác nhau giữa binary name và file path

`com/acme/Outer$Inner.class` là resource path; binary name thường là `com.acme.Outer$Inner`. Không biến dấu `$` thành dấu `.` tùy ý khi load class. Một số tên nội bộ, class local/anonymous, `module-info` hoặc `package-info` cần chính sách riêng.

### Bẫy G — Dựa vào thứ tự duyệt file/JAR

Thứ tự `Files.walk` hoặc `JarFile.entries` không phải thứ tự ưu tiên nghiệp vụ. Sắp xếp kết quả nếu cần output xác định và quy định giải quyết duplicate rõ ràng. Đừng dùng “class đầu tiên tìm thấy” làm plugin được chọn nếu không có contract thứ tự.

## 12. Chọn giải pháp dựa trên mục tiêu

| Mục tiêu | Cách nên bắt đầu |
| --- | --- |
| Biết tên class từ cấu hình | `Class.forName(name, false, loader)` và kiểm tra contract |
| Đọc field/method/annotation của class đã biết | `Class<?>` và Reflection |
| Liệt kê `.class` trong một thư mục/JAR **đã chỉ định** | `Files.walk` / `JarFile` |
| Tìm implementation đã đăng ký cho một interface | `ServiceLoader` / registry |
| Tìm bean/component trong ứng dụng Spring | Component scanning/metadata scanning của Spring |
| Quét package/classpath phức tạp của một ứng dụng cụ thể | Scanner chuyên dụng phù hợp môi trường |
| Thống kê class **đã load** trong một JVM | `Instrumentation` qua Java agent |
| Phân tích resource của **module đã xác định** | `ModuleReader` và module resolution API |
| Muốn mọi class có thể xuất hiện trong mọi loader của JVM | Phải thu hẹp định nghĩa và phạm vi; không có API tổng quát đáp ứng tuyệt đối |

## 13. Ghi nhớ

1. **Reflection bắt đầu từ một `Class<?>` hoặc một tên type đã biết.** Nó cho phép xem cấu trúc và thao tác động với thành viên của type đó, **không** có API core tự liệt kê tất cả class của classpath.
2. **Classpath entry, candidate class name, class loadable, class loaded và class accessible là năm khái niệm khác nhau.** Không tự đồng nhất các tập này.
3. **Khám phá class** là trách nhiệm của filesystem/JAR/module/source index/class loader framework phù hợp với môi trường chạy; mỗi cách chỉ bao phủ phần phạm vi mà nó hiểu.
4. Nếu cần plugin theo hợp đồng, hãy xem xét **`ServiceLoader` hoặc explicit registry** trước khi áp dụng quét rộng.
5. Nếu cần class đã load, xem hướng **Instrumentation**, không suy diễn từ số file `.class`.
6. Công cụ quét đúng cần có **phạm vi rõ, bộ lọc, xử lý class loader/module, lỗi và giới hạn tài nguyên**, không được quảng cáo kết quả quét một directory/JAR là “mọi lớp trong JVM”.

### Tự kiểm tra

- `getDeclaredClasses()` có tìm được các class top-level ở cùng package không? Vì sao?
- `Class.forName("com.example.Widget")` có tự khám phá tên class nào khác không?
- Vì sao `ClassLoader.getResources("com/example")` có thể trả rỗng dù vẫn load được `com.example.Widget`?
- Một file `.class` nằm trong JAR có chắc JVM đã load class đó chưa?
- Nếu hai custom class loader load cùng binary name, ta có chắc nhận một runtime type duy nhất không?
- Khi xây plugin registry, tại sao `ServiceLoader` thường hợp lý hơn quét tất cả JAR?
- `Instrumentation.getAllLoadedClasses()` giải quyết câu hỏi nào và **không** giải quyết câu hỏi nào?
- Vì sao code ví dụ chỉ nói “Candidates: 2” thay vì “All classes in classpath: 2”?

### Tài liệu tham khảo chính thức

- [`Class` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getDeclaredClasses`, `forName` và Reflection theo một kiểu.
- [`ClassLoader` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `getResources`, phạm vi và cơ chế delegation.
- [`JarFile` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html): đọc entry từ một JAR đã chỉ định.
- [`ServiceLoader` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): đăng ký và khám phá provider theo SPI.
- [`Instrumentation` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html): `getAllLoadedClasses` qua agent.
- [`ModuleReader` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleReader.html): đọc và liệt kê resource của module xác định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-005">Đổi tên, obfuscation và thay đổi nội bộ có thể làm hỏng liên kết theo chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Đổi tên, obfuscation và thay đổi nội bộ có thể làm hỏng liên kết theo chuỗi trong Java Reflection

Một điểm mạnh của Java Reflection là có thể tìm lớp, field, constructor hoặc method **theo tên khi chương trình chạy**. Ví dụ `Class.forName("com.example.OrderService")` và `type.getMethod("submit", String.class)` cho phép cấu hình linh hoạt mà không cần tham chiếu trực tiếp đến mọi implementation khi biên dịch.

Điểm yếu tương ứng là **chuỗi tên trở thành một hợp đồng ngầm ở runtime**. Đổi tên lớp, chuyển package, đổi tên method, thay chữ ký, rút gọn/obfuscate bytecode hoặc sửa cấu trúc nội bộ có thể khiến cấu hình cũ ngừng hoạt động. Compiler thường không biết một chuỗi trong JSON, YAML, database hoặc annotation tùy ý đang được dùng để tham chiếu đến phần tử Java nào, nên lỗi chỉ xuất hiện **khi đường chạy Reflection được thực thi**.

Bài học tập trung vào việc nhận diện hợp đồng ngầm đó, giải thích cơ chế lỗi và thiết kế cách liên kết ổn định hơn.

## 1. Liên kết theo kiểu ở compile time và liên kết theo chuỗi ở runtime

### Gọi có kiểm tra kiểu

```java
OrderService service = new OrderService();
service.submit("A-100");
```

Đây là đoạn minh họa giả định `OrderService` có method `submit(String)`. Compiler phải resolve kiểu và chữ ký. Nếu đổi method từ `submit` sang `placeOrder` nhưng chưa sửa nơi gọi, lỗi thường xuất hiện ngay khi biên dịch lại mã nguồn.

### Gọi qua Reflection với chuỗi

```java
Class<?> type = Class.forName("com.example.OrderService");
Object service = type.getDeclaredConstructor().newInstance();
Method method = type.getMethod("submit", String.class);
method.invoke(service, "A-100");
```

Đây là **đoạn minh họa**, không phải một chương trình độc lập; mã thật cần import, xử lý exception và kiểm tra quyền truy cập. Chuỗi `"com.example.OrderService"` và `"submit"` được kiểm tra tại runtime. Nếu lớp đổi thành `com.example.orders.OrderProcessor` hoặc method đổi thành `placeOrder`, compiler **không nhất thiết phát hiện** các chuỗi đã lỗi thời.

Sơ đồ:

```text
Mã/config có chuỗi "com.example.OrderService" + "submit"
                         |
                         v
               Runtime Reflection
                  Class.forName
                   getMethod
                     invoke
                         |
             +-----------+-----------+
             |                       |
     Tên/chữ ký vẫn đúng      Tên/chữ ký đã đổi
             |                       |
       Gọi thành công         Lỗi khi chạy:
                         ClassNotFoundException
                         NoSuchMethodException
                         ... 
```

**Không phải mọi thao tác Reflection đều phụ thuộc chuỗi.** `SomeType.class`, `obj.getClass()` và `Method` đã lấy sẵn không bắt đầu bằng việc tra cứu tên chuỗi. Tuy nhiên, các kiểu đó vẫn phải thỏa yêu cầu tương thích runtime nếu phần implementation/bytecode bị thay đổi.

## 2. Những thay đổi nào làm liên kết dễ gãy?

| Thay đổi | Điểm dễ hỏng | Hậu quả thường gặp |
|---|---|---|
| Đổi tên class | `Class.forName("pkg.OldType")` | `ClassNotFoundException` |
| Di chuyển package | Chuỗi tên nhị phân cũ `pkg.OldType` | Không tìm được lớp, trừ khi có lớp tương thích giữ tên cũ |
| Đổi tên method | `getMethod("oldName", ...)` | `NoSuchMethodException` |
| Đổi kiểu hoặc số tham số | `getMethod("run", String.class)` | Không resolve được chữ ký cũ |
| Đổi constructor | `getDeclaredConstructor(...)` | `NoSuchMethodException` hoặc lỗi instantiation |
| Đổi tên field | `getDeclaredField("oldField")` | `NoSuchFieldException` |
| Chuyển field/method lên superclass | `getDeclaredField`/`getDeclaredMethod` trên lớp cũ | Không còn phần tử **declared** tại nơi tra cứu |
| Đổi mức visibility | `getMethod` chỉ tra cứu public method; `invoke` còn chịu kiểm tra truy cập | Không tìm được hoặc không có quyền gọi |
| Đổi tên sau obfuscation | Metadata runtime đổi thành tên đã bị biến đổi | Chuỗi gốc không khớp với tên runtime |
| Shrink/loại bỏ code hoặc metadata | Plugin/annotation/method chỉ được truy cập động | Thành viên vắng mặt hoặc annotation không còn quan sát được |
| Thay đổi module/package accessibility | Method còn tồn tại nhưng reflection bị ràng buộc | `IllegalAccessException` / `InaccessibleObjectException` |

**Quan trọng:** `Class.getMethod(name, parameterTypes...)` tìm **public method** (kể cả thành viên được kế thừa theo API), trong khi `getDeclaredMethod(...)` tìm method khai báo ngay trên class, kể cả non-public. Hai hàm không tương đương. Chữ ký tra cứu khớp chính xác các kiểu tham số qua `Class<?>`: `int.class` và `Integer.class` khác nhau; `String.class` và `Object.class` cũng khác nhau.

Giả sử method gốc:

```java
public void execute(String id) {}
```

Sau refactor:

```java
public void execute(Object id) {}
```

Một lời gọi `getMethod("execute", String.class)` vẫn thất bại dù `String` là một `Object`; Reflection không dùng quy tắc overload resolution từ mã nguồn để tự tìm `execute(Object)`.

## 3. Obfuscation và shrinking thực sự làm gì?

Các công cụ như ProGuard/R8 hoặc các bộ biến đổi bytecode khác có thể, tùy cấu hình:

- **Đổi tên** class, package, field, method thành tên ngắn hoặc khó đọc.
- **Loại bỏ** class/method bị xem là không được dùng.
- **Biến đổi hoặc loại bỏ metadata/attributes** mà Reflection hoặc framework dựa vào.
- Áp dụng quy tắc **keep** cho lớp và thành viên cần giữ tên/hình dạng; một số công cụ có khả năng thích nghi các chuỗi tên trong trường hợp hỗ trợ, nhưng không thể mặc định mọi chuỗi cấu hình/database đều được cập nhật.

Ví dụ trước obfuscation có `com.example.plugins.ReportPlugin` với method `generate()`. Sau biến đổi, binary name có thể thành `a.b.C`, method có thể thành `a()`. Khi chạy mã:

```java
Class.forName("com.example.plugins.ReportPlugin");
```

runtime tìm **tên được truyền vào**; nó không tự suy ra `a.b.C` là hậu thân của tên cũ.

Phân biệt ba yếu tố:

1. **Bytecode đang được chạy:** chứa tên runtime thực tế sau khi transform.
2. **Chuỗi/metadata sử dụng để tìm:** có thể vẫn là tên cũ trong file cấu hình, annotation, dữ liệu lưu lâu dài hay literal.
3. **Mapping/keep rules của build:** chỉ dẫn công cụ biến đổi giữ hoặc ánh xạ những phần nhất định.

Vì vậy, câu “thư viện bị obfuscate thì Reflection luôn hỏng” là quá mạnh. Nó **có thể vẫn hoạt động** nếu tên cần tìm được giữ nguyên, metadata cần thiết được bảo toàn hoặc các liên kết được cập nhật đúng theo khả năng của công cụ. Ngược lại, việc ứng dụng chạy bình thường ở build debug **không chứng minh** build release đã shrink/obfuscate vẫn hoạt động.

**Các dạng metadata khác cũng cần xét:** annotation phục vụ discovery phải có `@Retention(RetentionPolicy.RUNTIME)` mới được đọc ở runtime; shrinker phải bảo toàn lớp/thành viên và metadata cần thiết theo đúng cấu hình cụ thể. Parameter name (nếu ứng dụng dựa vào) còn phụ thuộc cách biên dịch như `-parameters` và metadata sau biến đổi; không được coi nó là ID nghiệp vụ bền vững.

## 4. Ví dụ Java 17+: refactor method làm hỏng lookup cũ, ID ổn định vẫn dùng được

Ví dụ này giả định một phiên bản cũ có `OldInvoiceService.charge(int)`. Sau refactor, implementation hiện tại đổi thành `InvoiceService.capture(int)`. Ta vẫn có thể biên dịch mã nguồn mới vì tên cũ chỉ nằm trong chuỗi, nhưng tra cứu bằng chuỗi cũ sẽ thất bại tại runtime.

Đồng thời, ta dùng annotation `@Operation(id = "invoice.charge")` như **ID logic ổn định**, tách contract công khai khỏi tên Java của implementation.

Lưu toàn bộ chương trình thành **`StringBindingDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class StringBindingDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Operation {
        String id();
    }

    public static class InvoiceService {
        @Operation(id = "invoice.charge")
        public String capture(int amount) {
            return "Captured " + amount;
        }
    }

    static Map<String, Method> discover(Class<?> type) {
        Map<String, Method> registry = new HashMap<>();

        for (Method method : type.getDeclaredMethods()) {
            Operation metadata = method.getAnnotation(Operation.class);
            if (metadata == null) {
                continue;
            }

            if (!Modifier.isPublic(method.getModifiers())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != int.class
                    || method.getReturnType() != String.class) {
                throw new IllegalArgumentException(
                        "Invalid operation signature: " + method.getName());
            }

            if (registry.putIfAbsent(metadata.id(), method) != null) {
                throw new IllegalArgumentException(
                        "Duplicate operation ID: " + metadata.id());
            }
        }

        return Map.copyOf(registry);
    }

    public static void main(String[] args) throws Exception {
        InvoiceService service = new InvoiceService();

        // Kiểm tra kiểu khi biên dịch: tên mới được tham chiếu trực tiếp.
        System.out.println("Typed: " + service.capture(150));

        // Chuỗi method từ phiên bản cũ vẫn biên dịch được, nhưng lookup hỏng.
        try {
            InvoiceService.class.getMethod("charge", int.class);
        } catch (NoSuchMethodException e) {
            System.out.println("Old method: NoSuchMethodException");
        }

        // Chuỗi binary name cũ trong cấu hình cũng không được tự đổi.
        try {
            Class.forName("StringBindingDemo$OldInvoiceService");
        } catch (ClassNotFoundException e) {
            System.out.println("Old class: ClassNotFoundException");
        }

        // API công khai tham chiếu ID logic, không cần biết tên method.
        Method action = discover(InvoiceService.class).get("invoice.charge");
        if (action == null) {
            throw new IllegalStateException("Missing operation");
        }

        System.out.println("Stable ID: " + action.invoke(service, 150));
        System.out.println("Resolved method: " + action.getName());
    }
}
```

Biên dịch và chạy (Java 17+):

```bash
javac --release 17 -encoding UTF-8 StringBindingDemo.java
java StringBindingDemo
```

**Stdout kỳ vọng:**

```text
Typed: Captured 150
Old method: NoSuchMethodException
Old class: ClassNotFoundException
Stable ID: Captured 150
Resolved method: capture
```

**Giải thích:**

1. Lời gọi `service.capture(150)` được compiler kiểm tra theo API mới. Nếu bạn lại viết `service.charge(150)`, mã nguồn mới sẽ không biên dịch vì method đã đổi tên.
2. Chuỗi `"charge"` vẫn là literal hợp lệ nên compiler không cảnh báo. `getMethod` chỉ tìm member theo đúng tên/chữ ký runtime và ném `NoSuchMethodException`.
3. Tên `StringBindingDemo$OldInvoiceService` không còn tồn tại: `Class.forName` không biết một lớp đã được đổi tên từ phiên bản trước.
4. Discovery tìm method theo annotation có ID logic `"invoice.charge"`; tên Java thật là `capture`. Hai tên phục vụ **hai mục đích** khác nhau.
5. ID trong annotation không phải “phép thuật chống obfuscation”: mã trên vẫn cần method/annotation được giữ lại và discover thành công sau quá trình biến đổi bytecode.

**Giới hạn của ví dụ:** Registry mới chỉ chứng minh liên kết qua ID, không tự cấp quyền gọi method. Ứng dụng thật cần kiểm soát quyền, schema input, target instance, phiên bản API và tính tương thích của ID. Mã ví dụ chạy trong unnamed module/classpath với method public và class public; các module có encapsulation chặt hơn cần cấu hình truy cập phù hợp.

## 5. Các giải pháp giảm phụ thuộc vào chuỗi tên nội bộ

### A. Dùng lời gọi và tham chiếu có kiểm tra kiểu

Khi không cần plugin động, ưu tiên API thường:

```java
InvoiceService service = new InvoiceService();
String output = service.capture(150);
```

Hoặc dùng method reference với functional interface:

```java
java.util.function.IntFunction<String> action = service::capture;
String output = action.apply(150);
```

Đây là **đoạn minh họa** dùng kiểu `InvoiceService` từ ví dụ trên. Compiler kiểm tra method đang tồn tại và chữ ký tương thích khi build. Refactor IDE thường có thể cập nhật usage được nhận diện như symbol; chuỗi tự do trong YAML/database vẫn cần migration riêng.

### B. Tách ID công khai ổn định khỏi tên implementation

Thay vì để cấu hình ghi tên nội bộ:

```yaml
# Liên kết giòn: đổi tên class hoặc method sẽ làm hỏng cấu hình
handlerClass: "com.example.billing.InvoiceService"
handlerMethod: "capture"
```

Một registry nội bộ có thể cung cấp ID:

```yaml
# Liên kết theo hợp đồng ứng dụng, do registry quyết định implementation
operationId: "invoice.charge"
```

Ứng dụng dịch `invoice.charge` thành handler được đăng ký. Người bảo trì có thể đổi tên hoặc cấu trúc class bên trong mà vẫn giữ ID này. **Điều kiện** là ID thực sự được quản trị như một contract ổn định, registry được dựng/kiểm tra đúng và không bị xóa/mất metadata sau khi đóng gói.

Các ID nghiệp vụ cũng có thể cần version như `invoice.charge.v2` nếu input/output hoặc ngữ nghĩa thay đổi không tương thích; đừng cố giữ một ID duy nhất khi nghĩa của action đã thay đổi căn bản.

### C. Dùng interface hoặc ServiceLoader khi phù hợp

Một interface Java công khai tạo contract có kiểm tra kiểu:

```java
public interface InvoiceOperation {
    String capture(int amount);
}
```

Đây là mảnh mã mô tả interface riêng, không phải phần bổ sung cần dán vào file demo ở trên. Với nhiều provider, `ServiceLoader<InvoiceOperation>` có thể khám phá implementations thông qua cấu hình provider/module đúng quy ước. Cơ chế này vẫn có **hợp đồng tên và packaging** trong provider configuration, nên cấu hình `META-INF/services` hoặc khai báo `provides ... with ...` cần được giữ/cập nhật đúng khi refactor và obfuscation.

### D. Keep rules và mapping khi bắt buộc dùng Reflection

Nếu framework yêu cầu tra cứu theo binary name hoặc tên method, cần ghi rõ những symbol/attributes là contract của Reflection; cấu hình shrinker/obfuscator cho **đúng phiên bản công cụ** để giữ chúng. Không nên giả định một quy tắc keep của công cụ này áp dụng y hệt sang công cụ khác.

Khi lưu class name hoặc method name ra database, file cấu hình hay event message, hãy lên kế hoạch migration và tương thích ngược: alias cho ID cũ, chuyển cấu hình, version schema hoặc thời gian chuyển tiếp. Giữ mapping obfuscation phục vụ phân tích stack trace và đối chiếu release, nhưng **mapping không tự sửa các chuỗi cũ** trong cơ sở dữ liệu.

## 6. Tên lớp, method và chữ ký: các bẫy riêng

### Binary name của nested class

Đối với nested class, tên mã nguồn dạng `Outer.Inner` có thể khác binary name `Outer$Inner`. Ví dụ:

```java
Class<?> type = Class.forName("StringBindingDemo$InvoiceService");
```

Trong ví dụ không có package, tên trên hợp lệ. Nếu chuyển class sang package, cần thêm package vào chuỗi. Chèn dấu `$` vào mọi dấu chấm là sai vì dấu chấm phân cách package vẫn phải giữ.

### Method overload và chữ ký

`getMethod("process", String.class)` và `getMethod("process", Object.class)` là **hai lookup khác nhau**. `getMethod` không chọn overload bằng khả năng tự động chuyển đổi tham số như lời gọi Java thông thường. Ngoài ra Reflection lookup không dùng **kiểu trả về** trong danh sách tham số tra cứu; thay đổi return type có thể cần kiểm tra thêm trước khi `invoke`/cast kết quả.

### Inherited, private và module

- `getMethod` trả public member theo hợp đồng API, có thể là method kế thừa.
- `getDeclaredMethod` tìm method khai báo trực tiếp; chuyển logic lên superclass có thể làm lookup này thất bại.
- `getDeclaredMethod` nhìn thấy method private nhưng không bảo đảm gọi được. `setAccessible(true)` có thể không được cho phép bởi module encapsulation, và không nên dùng như chiến lược sửa mọi lỗi thiết kế.

### Binary compatibility khác source compatibility

Một thư viện có thể được cập nhật trong khi client vẫn chạy bytecode **đã biên dịch trước đó**. Nếu xóa hoặc đổi tên method mà bytecode cũ vẫn tham chiếu trực tiếp đến method đó, lỗi linkage như `NoSuchMethodError` có thể xuất hiện (tùy thay đổi và quá trình resolve), khác với `NoSuchMethodException` do Reflection tra cứu bằng chuỗi.

Một bản sửa làm compile lại toàn bộ mã nguồn thành công **chưa đủ** để bảo đảm plugin, dữ liệu hoặc binary client phiên bản cũ còn chạy.

## 7. Kiểm thử bắt buộc ở đúng artifact runtime

Với thành phần có liên kết bằng chuỗi, unit test chỉ chạy trên mã nguồn chưa obfuscate thường bỏ sót lỗi. Một quy trình kiểm tra hiệu quả:

1. **Liệt kê các điểm liên kết động:** `Class.forName`, `getMethod`, `getDeclaredField`, tên provider, tên handler trong YAML/JSON/database, annotation-based discovery và serialization mapping.
2. **Phân loại contract:** symbol nào là chi tiết nội bộ có thể đổi, tên/ID nào được công bố cho plugin hoặc dữ liệu đã lưu.
3. **Refactor an toàn:** cập nhật tham chiếu kiểu; tìm trong configuration, resource và dữ liệu triển khai, không chỉ Java source. Với dữ liệu đã lưu, thực hiện migration có kiểm tra.
4. **Kiểm tra bản build sau shrink/obfuscation:** dùng chính JAR/AAB/artifact deploy, không chỉ debug build. Thực hiện discovery và gọi một tập hợp action đại diện.
5. **Kiểm tra chữ ký:** tên, parameter types, return type, visibility, annotation runtime, constructor, module access và các ngoại lệ.
6. **Kiểm tra tương thích:** chạy config/plugin/binary client ở phiên bản đã hỗ trợ; nếu có thay đổi contract thì đưa ra version/migration/alias rõ ràng.
7. **Theo dõi lỗi runtime:** log action ID, phiên bản artifact và nguyên nhân được phân loại, không log dữ liệu nhạy cảm hay giả định mọi lỗi Reflection đều là `ClassNotFoundException`.

Ví dụ assertion trong integration test (đoạn minh họa):

```java
Class<?> type = Class.forName("com.example.billing.InvoiceService");
Method method = type.getMethod("capture", int.class);
assert method.getReturnType() == String.class;
```

`assert` trong Java **mặc định có thể bị tắt** nếu JVM không chạy với `-ea`. Trong test thực tế hãy dùng assertion của JUnit và phải kiểm tra trên artifact đã biến đổi/đóng gói; chuỗi tên ở ví dụ chỉ là placeholder cho class của dự án.

## 8. Chẩn đoán sự cố khi liên kết theo chuỗi hỏng

| Lỗi | Gợi ý chẩn đoán |
|---|---|
| `ClassNotFoundException` | Binary name sai, package đổi, class loader không thấy lớp, class bị shrink/obfuscate |
| `NoSuchMethodException` | Sai tên, parameter types, dùng nhầm `getMethod` với `getDeclaredMethod`, method bị đổi/xóa |
| `NoSuchFieldException` | Field đổi tên hoặc không được khai báo tại class đang dùng `getDeclaredField` |
| `IllegalAccessException` | Method hiện diện nhưng caller không có quyền invoke theo luật access |
| `InaccessibleObjectException` | Không thể mở quyền truy cập phản chiếu do module encapsulation hoặc giới hạn khác |
| `InvocationTargetException` | Lookup thành công, nhưng **code được gọi** đã ném exception; đọc `getCause()` |
| `NoSuchMethodError` | Lỗi linkage của bytecode client/runtime không tương thích, khác lỗi lookup Reflection |
| `NullPointerException` khi tìm annotation | Có thể annotation vắng do retention không phải RUNTIME, metadata bị strip hoặc tra cứu sai chỗ; xác minh từng nguyên nhân |
| `ClassCastException` | Lookup có thể thành công nhưng kiểu instance hoặc return value không còn phù hợp |

**Đừng “sửa nhanh” bằng cách bắt mọi exception rồi im lặng bỏ qua.** Khi registry không tải được handler thiết yếu, nên báo lỗi tên/ID/phiên bản rõ và dừng cấu hình sai. Một lỗi `InvocationTargetException` cũng không có nghĩa tên method sai: có thể chính nghiệp vụ bị lỗi.

## 9. Cách chọn trong thiết kế lâu dài

| Nhu cầu | Cách ưu tiên |
|---|---|
| Gọi code biết tại compile time | Direct method call, interface, method reference |
| Chọn hành động theo tên nghiệp vụ từ client/config | Registry với **ID công khai ổn định** và schema có version |
| Khám phá plugin implementations | Interface/ServiceLoader hoặc registry rõ ràng, kiểm tra packaging |
| Framework buộc tra cứu tên class/method | Keep rules + runtime integration test với artifact phát hành |
| Dữ liệu cũ chứa tên Java | Migration/alias và kiểm tra tương thích ngược |
| Cần dùng Reflection nhưng tránh khởi tạo class sớm | `Class.forName(name, false, loader)` khi phù hợp, chỉ invoke sau bước kiểm tra |

Một thiết kế tốt giúp **internal refactor** vẫn linh hoạt nhưng có ranh giới rõ với các contract đã công bố ra ngoài. Tên nội bộ chỉ nên trở thành hợp đồng dài hạn khi có lý do chính đáng và có kiểm thử/migration tương ứng.

## 10. Tự kiểm tra

**Câu 1.** Vì sao IDE đổi tên `charge()` thành `capture()` có thể làm chương trình Reflection lỗi dù Java source vẫn biên dịch?

Vì literal `"charge"` được xử lý như dữ liệu chuỗi thông thường. `getMethod("charge", ...)` tra cứu tại runtime; compiler không xác minh chuỗi đó còn trỏ đến method thật.

**Câu 2.** Obfuscator luôn phá Reflection?

**Không.** Nếu symbol được keep, chuỗi được thích nghi đúng hoặc registry không phụ thuộc vào tên nội bộ đã đổi, Reflection vẫn có thể làm việc. Cần kiểm thử chính artifact đã transform.

**Câu 3.** ID `invoice.charge` trong annotation có bảo đảm chạy qua mọi bản build không?

**Không tự động.** Đó là hợp đồng logic do ứng dụng quản lý. Cần đảm bảo annotation runtime, method còn tồn tại, metadata được giữ, registry đăng ký thành công và caller được phân quyền.

**Câu 4.** `getMethod("run", String.class)` có tự tìm `run(Object)` nếu không có overload nhận `String`?

**Không.** Tra cứu dùng đúng kiểu tham số được truyền vào. Chữ ký API đã đổi thì phải cập nhật contract/adapter/config.

**Câu 5.** Vì sao test phải chạy trên bản release obfuscated thay vì chỉ trên classfile do `javac` tạo ra?

Vì chính bước shrink/obfuscation có thể đổi tên hoặc loại bỏ class, method, annotation và metadata, làm hành vi Reflection khác với bản debug.

## Tài liệu chuẩn

- Java SE 17 API: [`Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html) — `forName`, `getMethod`, `getDeclaredMethod` và metadata.
- Java SE 17 API: [`Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html) — `invoke`, signature, lỗi truy cập và `InvocationTargetException`.
- Java SE 17 API: [`ServiceLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html) — provider discovery dựa trên interface.
- Java Language Specification, chương 13 (Binary Compatibility); JVM Specification, chương 5 (Loading, Linking, Initialization).
- Tài liệu shrinker/obfuscator **đúng phiên bản và hệ sinh thái đang dùng** để xác định keep rules, retention của metadata và khả năng thích nghi resource/string.

**Ghi nhớ:** Liên kết bằng chuỗi trao đổi sự linh hoạt khi chạy lấy rủi ro gãy kết nối khi tên hoặc chữ ký nội bộ thay đổi. Hãy dùng contract có kiểm tra kiểu khi có thể, ID logic ổn định khi cần động, và kiểm thử chính artifact phát hành cùng cấu hình/dữ liệu thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-006">Kiểm tra quyền và giới hạn dữ liệu đầu vào chọn lớp hoặc phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra quyền và giới hạn dữ liệu đầu vào khi chọn lớp hoặc phương thức bằng Java Reflection

## 1. Vấn đề: reflection biến tên thành hành vi thực thi

Reflection cho phép chương trình tìm `Class`, `Method`, `Field` hoặc `Constructor` lúc chạy. Nếu tên lớp/phương thức, kiểu tham số hoặc đối tượng đích được chọn **trực tiếp từ request, JSON, query parameter, header, cấu hình bên ngoài hoặc thông điệp hàng đợi**, ứng dụng có thể vô tình tạo một cơ chế **gọi chức năng tùy ý**.

Ví dụ nguy hiểm về mặt thiết kế, **chỉ để phân tích, không dùng trong ứng dụng**:

~~~java
// Ví dụ KHÔNG AN TOÀN. Đây là đoạn trích, không phải chương trình độc lập.
Class<?> clazz = Class.forName(requestedClassName);
Object object = clazz.getDeclaredConstructor().newInstance();
Method method = clazz.getMethod(requestedMethodName, String.class);
Object result = method.invoke(object, requestedArgument);
~~~

Các tên `requestedClassName` và `requestedMethodName` ở đây không được phép do client tùy ý quyết định. Nếu phần mềm chứa nhiều lớp có tác động đến dữ liệu, quyền của người dùng chỉ nên cho phép gọi một tập tác vụ hẹp; đoạn code trên không áp đặt ranh giới đó.

**Mục tiêu bảo vệ:** người gọi chỉ được lựa chọn **hành động đã được công bố**, trên **tài nguyên mà họ có quyền**, với **đối số đã được kiểm tra**, thông qua **receiver và Method do mã đáng tin cậy kiểm soát**.

Một quy tắc quan trọng: **`public` trong Java không có nghĩa “user hiện tại được phép gọi qua API”**. Tương tự, `setAccessible(true)` có liên quan đến kiểm tra truy cập của reflection và giới hạn module, **không hề thực hiện kiểm tra quyền nghiệp vụ**.

## 2. Threat model: phân biệt bốn nguồn dữ liệu

| Thành phần | Nguồn an toàn mong đợi | Rủi ro nếu để client kiểm soát |
| --- | --- | --- |
| **Danh tính và vai trò** | Bối cảnh xác thực đã được server kiểm tra, token được xác minh chữ ký/hiệu lực, session hợp lệ | Client tự khai là `ADMIN`, giả mạo tenant hoặc principal |
| **Tên thao tác** | Key ngắn trong allowlist do server định nghĩa, ví dụ `profile.read` | Gọi method nội bộ như `resetAll` hoặc chọn class ngoài ý định |
| **Receiver/Method** | Instance/Method được server resolve, đăng ký và cache trước | Tự chọn một class hoặc method bất kỳ; tạo object ngoài phạm vi |
| **Đối số nghiệp vụ** | Chuỗi/ID/schema đã parse, validate và được kiểm tra quyền sở hữu | IDOR/BOLA, vượt phạm vi tenant, payload quá lớn, chuyển đổi kiểu sai |

*Authentication* trả lời “caller là ai?”. *Authorization* trả lời “caller có quyền thực hiện **hành động này trên tài nguyên này** không?”. *Input validation* trả lời “dữ liệu có đúng kiểu, đúng dạng, đúng giới hạn không?”. Ba bước phải được thực hiện **độc lập**; một bước không thay thế được bước còn lại.

Ví dụ user có quyền đọc profile của chính mình nhưng **không mặc nhiên** có quyền đọc profile của người khác; admin có thể được cho phép một số thao tác đặc quyền, nhưng mỗi thao tác vẫn cần policy cụ thể. Trong hệ thống đa tenant, kiểm tra tenant của đối tượng đích còn quan trọng hơn chỉ kiểm tra tên method.

## 3. Kiến trúc nên dùng: allowlist hành động, không mở reflection theo tên tùy ý

~~~text
Client:
  action = "profile.read"
  userId = "U-0001"
      |
      v
[1] Lấy danh tính từ phiên/token đã xác thực trên server
      |
      v
[2] Tra action trong allowlist cố định
    "profile.read" -> ProfileService.read(String)
    "account.lock" -> AdminService.lock(String)
      |
      v
[3] Validate input: kiểu, độ dài, pattern, số phần tử...
      |
      v
[4] Authorization: role + quyền trên tài nguyên/tenant
      |
      v
[5] Invoke đúng Method/receiver server đã đăng ký
      |
      v
[6] Trả kết quả công khai phù hợp; log/audit có kiểm soát
~~~

Không đưa tên class đầy đủ `com.company.AdminService` hay chữ ký tùy ý của Java vào giao diện request. Hãy map một mã hành động ổn định sang receiver và method **do server sở hữu**:

- Allowlist cần **tường minh**: ví dụ `"profile.read"` và `"account.lock"`, không quét toàn bộ method `public` rồi tự coi tất cả được phép.
- Cố định signature: `getMethod("read", String.class)` khác `getMethod("read", Object.class)` và khác các overload còn lại.
- Bind **chính sách bảo mật vào route đã đăng ký**; không chỉ kiểm tra tên request rồi chọn `Method` khác sau đó.
- Không có route khớp thì **deny by default**. Đừng fallback sang `Class.forName(userInput)` hay `getDeclaredMethod(userInput)`.
- Nếu không cần reflection, lời gọi Java trực tiếp hoặc router kiểu interface/strategy thường dễ audit và an toàn hơn. Reflection không phải bắt buộc để làm dispatch động.

## 4. Chương trình Java 17+ đầy đủ: chọn phương thức an toàn

Ví dụ tối giản minh họa: hai hành động được công khai, actor có `USER` hoặc `ADMIN`, quyền đọc profile **của chính mình** hoặc của admin, còn khóa tài khoản chỉ admin được phép. Các dịch vụ trong demo trả chuỗi, **không thực sự truy cập hay thay đổi tài khoản**.

Lưu thành **`SafeReflectionRouterDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;

public class SafeReflectionRouterDemo {
    enum Role { USER, ADMIN }
    enum Policy { SELF_OR_ADMIN, ADMIN_ONLY }

    // Actor chỉ được tạo từ bối cảnh xác thực đáng tin cậy trên server.
    record Actor(String userId, Role role) {}

    record Route(Object receiver, Method method, Policy policy) {}

    public static final class ProfileService {
        public String read(String userId) {
            return "profile:" + userId;
        }
    }

    public static final class AdminService {
        public String lock(String userId) {
            return "locked:" + userId;
        }
    }

    // Mã tài nguyên là ASCII cố định, không nhận đường dẫn hoặc class name.
    private static final Pattern USER_ID = Pattern.compile("U-[0-9]{4}");

    // Method/receiver được resolve từ hằng số tin cậy, duy nhất một lần.
    private static final Map<String, Route> ROUTES = buildRoutes();

    private static Map<String, Route> buildRoutes() {
        try {
            ProfileService profile = new ProfileService();
            AdminService admin = new AdminService();

            return Map.of(
                "profile.read",
                new Route(
                    profile,
                    ProfileService.class.getMethod("read", String.class),
                    Policy.SELF_OR_ADMIN),
                "account.lock",
                new Route(
                    admin,
                    AdminService.class.getMethod("lock", String.class),
                    Policy.ADMIN_ONLY)
            );
        } catch (NoSuchMethodException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static String dispatch(Actor actor, String action, String targetId) {
        // Mã action không phải tên class hoặc tên method Java.
        if (action == null || action.length() > 40) {
            throw new IllegalArgumentException("UNSUPPORTED_ACTION");
        }

        Route route = ROUTES.get(action);
        if (route == null) {
            throw new IllegalArgumentException("UNSUPPORTED_ACTION");
        }

        if (targetId == null || !USER_ID.matcher(targetId).matches()) {
            throw new IllegalArgumentException("INVALID_ID");
        }

        // Danh tính phải đến từ hệ thống auth, không phải JSON request.
        if (actor == null || actor.role() == null) {
            throw new SecurityException("DENIED");
        }

        boolean permitted = switch (route.policy()) {
            case ADMIN_ONLY -> actor.role() == Role.ADMIN;
            case SELF_OR_ADMIN -> actor.role() == Role.ADMIN
                    || actor.userId().equals(targetId);
        };

        if (!permitted) {
            throw new SecurityException("DENIED");
        }

        try {
            return (String) route.method().invoke(
                    route.receiver(), targetId);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("SERVER_CONFIGURATION_ERROR", ex);
        } catch (InvocationTargetException ex) {
            // Không trả stack trace hay nội dung exception đích cho client.
            throw new IllegalStateException("OPERATION_FAILED");
        }
    }

    static void show(Actor actor, String action, String userId) {
        try {
            System.out.println(action + " -> "
                    + dispatch(actor, action, userId));
        } catch (IllegalArgumentException | SecurityException ex) {
            System.out.println(action + " -> " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        Actor owner = new Actor("U-0001", Role.USER);
        Actor admin = new Actor("U-9000", Role.ADMIN);

        show(owner, "profile.read", "U-0001");
        show(owner, "profile.read", "U-0002");
        show(owner, "account.lock", "U-0002");
        show(admin, "account.lock", "U-0002");
        show(owner, "java.lang.Runtime.exec", "U-0001");
        show(admin, "profile.read", "U-0001/../../");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 SafeReflectionRouterDemo.java
java SafeReflectionRouterDemo
~~~

Output kỳ vọng:

~~~text
profile.read -> profile:U-0001
profile.read -> DENIED
account.lock -> DENIED
account.lock -> locked:U-0002
java.lang.Runtime.exec -> UNSUPPORTED_ACTION
profile.read -> INVALID_ID
~~~

### Vì sao ví dụ này an toàn hơn cách chọn class/method tùy ý?

1. **Client chọn key, không chọn Class.** `"java.lang.Runtime.exec"` không phải key được đăng ký nên bị từ chối; không có lệnh `Class.forName(action)`.
2. **Receiver và Method cố định.** `ProfileService.class.getMethod(...)` và `AdminService.class.getMethod(...)` chỉ thực thi trên hằng số của server lúc khởi tạo. Không có wildcard như “mọi public method của một class”.
3. **Policy gắn với Route.** `profile.read` chỉ chấp nhận chính chủ hoặc admin; `account.lock` chỉ chấp nhận admin. Việc có tên action hợp lệ **chưa đủ** để được phép thực thi.
4. **Đối số được giới hạn.** `U-[0-9]{4}` là ví dụ về ID hợp lệ có đúng 6 ký tự. Chuỗi giống đường dẫn bị từ chối; regex dùng `matches()` nên phải khớp **toàn bộ** giá trị, không chỉ khớp một phần.
5. **Mọi request vẫn được authorize.** Cache `Method` chỉ để tránh lookup lặp lại; **không cache một kết quả “đã được phép” để dùng cho người gọi khác**.
6. **Không tăng quyền truy cập reflection.** Ví dụ chỉ dùng phương thức `public` được đăng ký. Nó không gọi `setAccessible(true)` hay `trySetAccessible()` để mở các member riêng tư.

**Giới hạn của demo:** `Actor` được tạo trực tiếp trong `main` để mô phỏng môi trường đã xác thực. Trong dịch vụ thật, **không được** dựng `Actor(userId, role)` từ hai trường client tự cung cấp. Phải dùng identity/claims đã được xác minh ở lớp auth, đồng thời kiểm tra đối tượng đích qua database/service theo tenant và quyền sở hữu. `ProfileService` và `AdminService` trong ví dụ chỉ trả chuỗi nên chưa biểu diễn kiểm tra dữ liệu thật, transaction, audit hoặc rate limit.

## 5. Các lớp kiểm tra phải có trong production

### 5.1. Authentication: caller lấy quyền từ đâu?

- Server xác thực session hoặc token: chữ ký, issuer, audience, thời hạn, thu hồi khi hệ thống có cơ chế đó.
- Identity, role và tenant không được quyết định chỉ bằng `request.getParameter("role")` hoặc một JSON field.
- Khi service gọi service, cần phân biệt danh tính người dùng cuối và danh tính máy; service account không tự động được phép thay người dùng thực hiện mọi thao tác.
- Request thiếu hoặc không hợp lệ phải fail closed trước khi gọi member tác động tới dữ liệu.

### 5.2. Authorization: kiểm tra cả thao tác lẫn tài nguyên

| Câu hỏi | Ví dụ |
| --- | --- |
| Caller có quyền gọi action không? | User thường không được gọi `account.lock` |
| Caller có quyền trên **đối tượng này** không? | Được `profile.read` nhưng chỉ với profile thuộc phạm vi được cấp |
| Tài nguyên có thuộc đúng tenant/organization không? | Không đọc tài khoản tenant khác dù ID đúng format |
| Trạng thái tài nguyên có cho phép thao tác không? | Tài khoản đã khóa hoặc đã xóa cần được xử lý theo state machine |
| Có cần kiểm tra lại ngay trước thay đổi không? | Tránh TOCTOU khi quyền hoặc trạng thái dữ liệu có thể thay đổi |
| Ai ghi nhận việc thay đổi? | Audit actor, action, resource ID, outcome; loại bỏ dữ liệu bí mật |

**ID có regex hợp lệ không phải bằng chứng sở hữu.** Ví dụ chỉ minh họa self-or-admin; trong ứng dụng thật, object-level authorization phải thực hiện theo dữ liệu đáng tin cậy. Với multi-tenant, có thể truy vấn tài nguyên bằng **cặp** `(tenantId, resourceId)` rồi kiểm tra quyền trước khi xử lý. Tầng service/domain nên có kiểm tra quyền tương ứng để không bị vượt qua nếu bị gọi từ con đường khác ngoài HTTP router.

### 5.3. Input validation: giới hạn chi phí và bề mặt tấn công

Không chỉ kiểm tra tên class/method. Mọi đối số truyền vào reflection cũng là dữ liệu đầu vào:

- **Allowlist hành động:** chỉ các key đã đăng ký. Giới hạn độ dài, mã hóa và dạng thức; không nhận tên lớp fully qualified tùy ý.
- **Arity và type:** `Method` được chọn phải có đúng chữ ký đăng ký; parse JSON thành DTO/schema cố định trước khi invoke. Không cho client truyền tên các kiểu trong mảng `Class<?>[]` để tự chọn overload.
- **Chuỗi, ID và path:** giới hạn độ dài và tập ký tự; khi cần đường dẫn, chuẩn hóa và kiểm tra path trong root được phép **sau khi resolve**, tránh traversal và symlink ngoài phạm vi.
- **Kích thước và độ sâu:** giới hạn request body, số lượng phần tử collection, nesting depth, số lần gọi, timeout/rate limit. Một action được phép vẫn có thể gây resource exhaustion nếu payload không có trần.
- **Kiểu số và enum:** giới hạn min/max, overflow và giá trị hợp lệ; không dựa vào `Method.invoke` tự bắt mọi lỗi nghiệp vụ.
- **Output:** lọc trường dữ liệu được phép trả về, không serialise toàn bộ object nội bộ hay exception/stacktrace.
- **Ghi log:** không log password, token, PII nhạy cảm hoặc toàn bộ `args`. Có thể log action key, correlation ID và quyết định cấp quyền.

### 5.4. Lựa chọn class trong plugin/configuration

Có ứng dụng thật sự cần cấu hình tên lớp (ví dụ kiến trúc plugin). Khi đó:

1. Phân biệt **config do operator quản lý** và input từ người dùng cuối. Không coi hai nguồn có cùng mức độ tin cậy.
2. Áp dụng allowlist hoặc registry các provider được phê duyệt; kiểm tra nguồn artifact, chữ ký và phiên bản theo mô hình triển khai.
3. Sau `Class.forName(...)`, kiểm tra `expectedInterface.isAssignableFrom(clazz)` và API/constructor được phép. **Type check chỉ xác nhận tương thích kiểu, không chứng minh code đáng tin.**
4. Hạn chế quyền và tài nguyên ở cấp **process/container/OS/service account** cho plugin không đáng tin; class loader riêng **không phải sandbox bảo mật**.
5. Phân quyền và validate đối số riêng cho mỗi capability. Không biến “plugin được load” thành “plugin được phép làm mọi thứ”.
6. Ưu tiên cơ chế đăng ký capability hoặc factory có kiểm soát thay vì cho phép tên class tùy ý từ request.

Lưu ý `Class.forName(String)` không phải thao tác kiểm chứng vô hại: overload một đối số có thể dẫn đến **khởi tạo class** theo ngữ nghĩa API, nên code `static` của class cũng đáng quan tâm. Overload `Class.forName(name, false, loader)` có thể tránh khởi tạo tại bước đó nhưng **không biến việc load class tùy ý thành an toàn**, cũng không thay kiểm soát quyền.

## 6. Reflection access khác quyền nghiệp vụ

Java có nhiều tầng kiểm soát, không được lẫn chúng:

| Tầng | Cơ chế | Không bảo đảm điều gì |
| --- | --- | --- |
| Ngôn ngữ Java | `public`, `protected`, package-private, `private` | Không xác nhận user HTTP có quyền nghiệp vụ |
| Reflection API | `getMethod`/`getDeclaredMethod`, `canAccess`, `trySetAccessible` | Không kiểm tra ownership, role, tenant |
| Java module system (JPMS) | `exports`, `opens` và quy tắc deep reflection | Không xác nhận một request hợp lệ về hành vi |
| Ứng dụng | Authentication, authorization, tenant/object policy, validation, audit | Cần lập trình rõ ràng; JVM không tự hiểu quy tắc nghiệp vụ |

`getDeclaredMethod(...)` có thể **tìm được** private method nhưng không có nghĩa caller được phép invoke. `setAccessible(true)` chỉ yêu cầu bỏ qua một số kiểm tra access **trong phạm vi module cho phép** và có thể ném `InaccessibleObjectException`; không làm một thao tác trở thành đúng về security. Tránh dùng `--add-opens` để mở module chỉ nhằm “chạy được mọi request”.

Ngoài ra, Java reflection có ngoại lệ runtime cần xử lý đúng: `NoSuchMethodException` khi lookup không có kết quả, `IllegalAccessException` khi invoke không được phép truy cập, `IllegalArgumentException` khi receiver/argument sai, và `InvocationTargetException` bao nguyên nhân do thân method ném. Những lỗi này **không thay thế** logic từ chối yêu cầu trước khi thực hiện.

## 7. Anti-pattern và cách thay thế

| Anti-pattern | Vì sao nguy hiểm | Thiết kế thay thế |
| --- | --- | --- |
| `Class.forName(request.className)` trên input tùy ý | Chọn class ngoài tập chức năng dự kiến, có thể kích hoạt initialization | Registry/allowlist class tin cậy hoặc không cho chọn class |
| `clazz.getMethod(request.methodName, ...)` rồi invoke | Public method không đồng nghĩa được expose cho request | Action key cố định → `Route` chứa Method và policy |
| `setAccessible(true)` để gọi private API | Mở rộng khả năng deep reflection; bỏ qua ranh giới thiết kế | Public facade đã được audit; quyền OS/module tối thiểu |
| `isAdmin = request.get("admin")` | Client tự quyết định đặc quyền | Principal và claims từ auth server đã verify |
| Chỉ kiểm tra `id.matches(...)` | Đúng dạng nhưng có thể là tài nguyên người khác | Object/tenant authorization từ nguồn dữ liệu tin cậy |
| Cache quyết định authorize theo method | User khác nhau có quyền khác nhau | Cache metadata, **evaluate policy cho từng lời gọi** |
| Auto-expose mọi method có annotation `@Action` | Annotation không đủ nếu không có kiểm soát review/policy | Registry được phê duyệt, policy rõ ràng, quét có kiểm soát |
| Trả `ex.getCause().toString()` cho client | Rò rỉ nội bộ và có thể lộ dữ liệu | Mã lỗi công khai chuẩn hóa, log nội bộ có bảo vệ |
| So `startsWith("com.safe.")` rồi nhận mọi class | Namespace/package không chứng minh code hoặc capability đáng tin | Allowlist type cụ thể và kiểm soát artifact/source |

## 8. Checklist trước khi đưa vào production

1. Giao diện request chỉ chọn **mã hành động đã công bố**; không nhận tên class, private method hay constructor tùy ý.
2. Registry lưu **receiver + Method + policy** do server thiết lập. Không có route → từ chối.
3. Identity/role/tenant đến từ nguồn **đã xác thực**; không đọc trực tiếp từ payload để xác định quyền.
4. Kiểm tra quyền **mỗi lần gọi** cả theo action lẫn resource/tenant; với thao tác thay đổi trạng thái, xét cả race/TOCTOU.
5. Giới hạn schema/kiểu/độ dài/số lượng/depth/rate; không giao việc validate cho `Method.invoke`.
6. Không dùng `setAccessible` hoặc `--add-opens` như cơ chế phân quyền; coi public API và authorization là hai vấn đề khác nhau.
7. Test các case: action lạ, method overload, user không có role, cross-tenant ID, request kích thước lớn, null input, và exception từ target.
8. Audit các từ chối và thao tác quan trọng bằng dữ liệu tối thiểu; thông báo lỗi cho client không tiết lộ stacktrace/nội bộ.
9. Cache `Method` để giảm lookup là được, **không được bỏ qua policy check** chỉ vì Method đã cache.
10. Với plugin class động, yêu cầu allowlist/quản trị artifact và cơ chế isolation ở cấp hệ thống nếu code không đáng tin.

## 9. Tự kiểm tra

- Vì sao `getMethod(...)` trả được public method nhưng vẫn phải kiểm tra authorization riêng?
- Nếu user có quyền `profile.read`, tại sao họ chưa chắc có quyền với mọi user ID hợp lệ?
- Sự khác nhau giữa **allowlist action** và **lọc package bằng prefix** là gì?
- Vì sao gọi `Class.forName(untrustedName)` từ request có rủi ro ngay cả trước `Method.invoke(...)`?
- Có nên cache cả kết quả `"được phép gọi Method này"` cho mọi user? Vì sao?
- Khi plugin chạy code ngoài tổ chức, chỉ tạo `ClassLoader` mới đã đủ tạo sandbox chưa?

### Tài liệu chính thức

- Java SE 21 — `Class` và `Class.forName`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
- Java SE 21 — `Method` và `Method.invoke`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html
- Java SE 21 — `AccessibleObject`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html
- Java SE 21 — `InaccessibleObjectException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/InaccessibleObjectException.html
- OWASP Authorization Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
- OWASP Input Validation Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-007">Phân biệt reflection runtime với annotation processing lúc biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt Reflection ở runtime với Annotation Processing lúc biên dịch

**Java Reflection** và **Annotation Processing** đều giúp một chương trình/công cụ làm việc với thông tin về kiểu và annotation. Nhưng chúng hoạt động ở **hai giai đoạn khác nhau**, có API, quyền truy cập và đầu ra khác nhau:

- **Reflection ở runtime:** chương trình đã được biên dịch và đang chạy; đọc metadata về `Class`, method, constructor, field hoặc annotation có thể nhìn thấy lúc chạy, rồi có thể tạo instance/gọi method theo quyền truy cập. API tiêu biểu: `Class<?>`, `java.lang.reflect.*`.
- **Annotation Processing lúc biên dịch:** bộ biên dịch chạy processor trên mô hình phần tử nguồn/kiểu (`Element`, `TypeMirror`), kiểm tra annotation và **sinh mã nguồn/tài nguyên hoặc phát chẩn đoán** trước khi ứng dụng chạy. API tiêu biểu: `javax.annotation.processing.*`, `javax.lang.model.*`, `Filer` và `Messager`.

Việc một annotation được dùng để xử lý ở compile time **không đồng nghĩa** ứng dụng có thể đọc annotation đó bằng Reflection; điều này còn tùy thuộc `@Retention`. Ngược lại, việc đọc một annotation có `RUNTIME` retention tại runtime không làm annotation processor tự chạy.

## 1. Đặt hai cơ chế lên cùng trục thời gian

~~~text
Tệp nguồn .java
     |
     v
javac / build
     |
     +-- Annotation processor (0..n rounds)
     |      +-- đọc Element, AnnotationMirror, TypeMirror
     |      +-- kiểm tra cấu trúc / ghi compiler diagnostics
     |      +-- Filer sinh .java hoặc tài nguyên
     |                |
     |                +-- javac tiếp tục xử lý nguồn mới ở vòng tiếp theo
     |
     v
Bytecode .class / JAR
     |
     v
JVM nạp lớp và thực thi ứng dụng
     |
     +-- Reflection lúc chạy
            +-- đọc metadata Class / Method / Field / Constructor
            +-- đọc annotation được giữ tới runtime
            +-- tạo instance / gọi method khi được phép
~~~

Annotation processor tạo mã theo những đầu vào sẵn có ở lúc compile, còn Reflection làm việc với kiểu/lớp đã nạp vào JVM. Không có nghĩa mọi processor chạy đúng một vòng: `javac` có thể tổ chức **nhiều processing rounds** khi những processor tạo thêm tệp nguồn mới. Runtime Reflection không phải một “processing round” khác của compiler.

Hai hoạt động có thể **bổ trợ**: annotation processor sinh mã giúp ứng dụng không cần quét hoặc phản chiếu nhiều ở runtime; hoặc processor sinh metadata để mã runtime tải và sử dụng sau này. Tuy nhiên, chúng không thể tự thay thế nhau trong mọi tình huống.

## 2. So sánh theo những câu hỏi thiết kế thực tế

| Tiêu chí | Reflection runtime | Annotation Processing compile time |
|---|---|---|
| Thời điểm | Sau khi class đã được nạp trong JVM | Trong lúc `javac`/công cụ build xử lý source |
| Đầu vào chủ yếu | `Class` và các reflection member trên kiểu đã có | `Element`/`TypeMirror`, annotation và compiler model |
| Thấy những gì | Metadata được giữ trong class file và API runtime cung cấp | Cấu trúc source/element được compiler cung cấp, kể cả annotation `SOURCE` |
| Phải nạp/chạy lớp ứng dụng? | Thường có `Class<?>`; một số thao tác Reflection có thể kích hoạt code | Không cần khởi tạo các instance ứng dụng để kiểm tra element |
| Khả năng gọi logic nghiệp vụ | Có thể thông qua `Constructor.newInstance`, `Method.invoke` | Không có ý nghĩa như “chạy instance của class đang biên dịch” |
| Sinh tệp dùng cho build | Không phải chức năng cốt lõi của Reflection | Có `Filer` sinh source/class/resource theo quy tắc compiler |
| Phát lỗi tại build | Không trực tiếp; lỗi thường chỉ xuất hiện khi chạy | Có `Messager` báo `ERROR`, cảnh báo, vị trí source |
| Dependency runtime | Phụ thuộc metadata/runtime API được sử dụng | Mã processor không nhất thiết nằm trong runtime artifact |
| Hạn chế chính | Access control/JPMS, tên/chữ ký động, chi phí runtime, retention | Chỉ biết thông tin compile-time; cấu hình processor và generated source cần đúng |
| Công dụng | Plugin loader, DI runtime, serializer, command dispatcher | Generator, type-safe adapters, validation API lúc biên dịch, metadata index |

**Một lựa chọn sai phổ biến:** dùng `Class.forName` và Reflection runtime để phát hiện cấu hình sai mà compiler đã có đủ thông tin để phát hiện. Nếu công cụ có thể kiểm tra hợp đồng theo source, annotation processing có thể đưa lỗi về build. Nhưng nếu phần mở rộng chỉ được chọn khi ứng dụng chạy (ví dụ class do plugin được đưa vào sau), processor tại compile time không thể đoán trước toàn bộ plugin runtime.

## 3. Annotation retention quyết định Reflection có nhìn thấy hay không

Annotation trong Java có `@Retention` từ `java.lang.annotation.RetentionPolicy`:

| Retention | Compiler/processor ở compile time | Class file sau compile | Reflection đọc qua `getAnnotation` |
|---|---|---|---|
| `SOURCE` | Có thể đọc trong processing round thích hợp | Thông thường không được ghi vào class file | **Không** |
| `CLASS` | Có thể đọc qua compiler model | Được giữ trong class file | **Không**, theo Reflection chuẩn |
| `RUNTIME` | Có thể đọc qua compiler model | Được giữ trong class file | **Có**, trên element phù hợp |

`RetentionPolicy.CLASS` là giá trị mặc định nếu annotation không khai báo `@Retention`. Điều này không có nghĩa `CLASS` sẽ tự hiện diện trong kết quả `Class.getAnnotation(...)`.

Một ví dụ annotation chỉ dùng lúc compile:

~~~java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AutoGreeting {}
~~~

Một ví dụ annotation cần được tra cứu lúc chạy:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RuntimeLabel {
    String value();
}
~~~

Hai đoạn trên chỉ là **mảnh minh họa**, bản đầy đủ với imports nằm ở mục 5. Việc chọn retention cần dựa trên nhu cầu:

- `SOURCE`: các quy tắc chỉ dành cho source-time processor; không tăng metadata annotation trên class file đã biên dịch.
- `CLASS`: metadata giữ trong class file để công cụ phân tích bytecode sử dụng; không phải API Reflection chuẩn.
- `RUNTIME`: mã đang chạy cần đọc `Class`/`Method`/`Field` annotations qua Reflection.

**Đừng tăng tất cả annotations lên `RUNTIME` chỉ vì một công cụ dùng annotation processing.** Processor có thể làm việc với annotation `SOURCE`, còn runtime không cần nhìn thấy nó.

`@Target` giới hạn nơi annotation được áp dụng (ví dụ `TYPE`, `METHOD`, `FIELD`); nó độc lập với `@Retention`. Một annotation có `RUNTIME` retention gắn vào method vẫn cần được đọc từ `Method` tương ứng, không nên suy luận rằng `SomeClass.class.getAnnotation(...)` sẽ tự quét toàn bộ method annotations.

## 4. API và mô hình làm việc khác nhau

### 4.1. Phía Reflection

Các lời gọi phổ biến:

~~~java
Class<?> type = Demo.class;
Method[] methods = type.getDeclaredMethods();
RuntimeLabel label = type.getAnnotation(RuntimeLabel.class);
~~~

Đây là **mảnh minh họa**, được giả định đặt trong chương trình có `Demo` và `RuntimeLabel` ở mục 5 cùng `import java.lang.reflect.Method;`.

`Class<?>` mô tả kiểu ở runtime. `getDeclaredMethods()` trả các method khai báo trực tiếp, không bảo đảm thứ tự và không bao gồm method kế thừa. `getAnnotation(RuntimeLabel.class)` tìm một annotation loại `RuntimeLabel` có thể được Reflection nhìn thấy trên đối tượng `Class`.

Nếu cần gọi hành vi:

~~~java
Constructor<?> constructor = type.getConstructor();
Object instance = constructor.newInstance();
Method method = type.getMethod("run");
method.invoke(instance);
~~~

Đây là **mảnh minh họa API**, không phải code chạy với `Demo` của mục 5 vì `Demo` không khai báo `run()`. Tìm được member **không bảo đảm** gọi được: quyền truy cập Java/JPMS, receiver và chữ ký tham số vẫn phải hợp lệ. `Method.invoke` có thể bọc exception do method đích ném bằng `InvocationTargetException`. `Class.forName(...)` có thể nạp và khởi tạo class khi dùng overload một tham số; thao tác Reflection runtime không phải luôn chỉ đọc metadata.

### 4.2. Phía annotation processor

Processor kiểu `AbstractProcessor` nhận:

~~~java
boolean process(
    Set<? extends TypeElement> annotations,
    RoundEnvironment roundEnv)
~~~

API điển hình:

- `RoundEnvironment.getElementsAnnotatedWith(...)`: tìm các element được chú thích trong round hiện tại.
- `Element`: đại diện declaration, ví dụ class/method/field; `TypeElement` đại diện declaration của một type.
- `TypeMirror`: mô tả type trong model compiler, không tương đương việc lấy `Class<?>` bằng runtime.
- `ProcessingEnvironment.getElementUtils()` và `getTypeUtils()`: tra cứu element/type và quan hệ kiểu.
- `Filer.createSourceFile(...)`: tạo tệp Java source output cho compiler xử lý.
- `Messager.printMessage(Diagnostic.Kind.ERROR, ...)`: phát chẩn đoán, có thể khiến build thất bại.
- `RoundEnvironment.processingOver()`: báo round cuối, khi không còn processing rounds bình thường để tạo thêm nguồn.

Processor có thể kiểm tra tên, modifier, kiểu, annotation và nguồn được compiler phân tích mà không cần tạo instance của class dưới kiểm tra. **Không sử dụng `Class.forName` để đọc những lớp source đang được compile như cách thay cho `Element`**: class đó có thể chưa tồn tại dưới dạng bytecode, hoặc bytecode cũ trên classpath có thể khiến kết quả sai.

## 5. Ví dụ Java 17: processor sinh mã, Reflection đọc annotation runtime

Ví dụ có **bốn tệp ở cùng một thư mục**, không khai báo package để việc thử nghiệm độc lập đơn giản. Trong mã sản phẩm nên dùng package rõ ràng và tên annotation đầy đủ cho `@SupportedAnnotationTypes`.

Mục tiêu:

1. `@AutoGreeting` có `SOURCE` retention. Processor nhìn thấy nó lúc compile và sinh ra `GreetingGenerated.java`.
2. `@RuntimeLabel("active")` có `RUNTIME` retention. Ứng dụng dùng Reflection để đọc chuỗi `active`.
3. Mã ứng dụng gọi `GreetingGenerated.message()` như **một Java method bình thường**, không cần `Method.invoke`. Điều này minh họa một lợi ích của code generation: phương thức được biết và kiểm tra kiểu từ lúc build.

### 5.1. Tệp `AutoGreeting.java`

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AutoGreeting {}
~~~

### 5.2. Tệp `RuntimeLabel.java`

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RuntimeLabel {
    String value();
}
~~~

### 5.3. Tệp `GreetingProcessor.java`

~~~java
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("AutoGreeting")
public class GreetingProcessor extends AbstractProcessor {
    private boolean generated;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (generated || roundEnv.processingOver()
                || roundEnv.getElementsAnnotatedWith(AutoGreeting.class).isEmpty()) {
            return false;
        }

        generated = true;
        try {
            JavaFileObject file =
                    processingEnv.getFiler().createSourceFile("GreetingGenerated");
            try (Writer writer = file.openWriter()) {
                writer.write(
                        "public final class GreetingGenerated {\n"
                        + "  public static String message() {\n"
                        + "    return \"Generated at compile time\";\n"
                        + "  }\n"
                        + "}\n");
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR, "Cannot generate source: " + e);
        }
        return true;
    }
}
~~~

Chi tiết cần hiểu:

- `@SupportedAnnotationTypes("AutoGreeting")` thông báo processor xử lý loại annotation nào. Tên ở ví dụ là loại trong **unnamed package**; mã thực tế sử dụng qualified name, ví dụ `"com.example.AutoGreeting"`.
- `process(...)` được compiler gọi trong **processing rounds**. Trường `generated` ngăn việc cố sinh lại cùng tệp khi round tiếp theo diễn ra.
- `Filer.createSourceFile("GreetingGenerated")` khai báo một tệp Java mới dưới thư mục nguồn phát sinh do compiler quản lý. Tệp mới được compiler xem xét/biên dịch khi processing tiếp tục.
- `process(...)` trả `true` khi processor **claim** annotation type mà nó xử lý, ngăn các processor khác tiếp tục nhận annotation types đã claim trong cùng cơ chế dispatch; việc trả `true` không có nghĩa compiler ngừng mọi round.
- `Messager` phát diagnostic với mức `ERROR` khi tạo nguồn thất bại; compiler/build có thể ngừng với trạng thái lỗi.
- Processor là chương trình **đã được biên dịch và nạp cho compiler chạy**. Nó không chỉ là một annotation đặt vào source.

Đây là bản demo một lớp nguồn. Một generator thực tế cần xử lý tên đầu ra theo từng element, collision, incremental build, tính lặp lại, package naming và điều kiện có nhiều annotated types; tránh dùng một cờ chung rồi sinh đúng một file cho mọi type nếu chức năng cần một file/type.

### 5.4. Tệp `Demo.java`

~~~java
@AutoGreeting
@RuntimeLabel("active")
public class Demo {
    public static void main(String[] args) {
        RuntimeLabel runtime = Demo.class.getAnnotation(RuntimeLabel.class);
        AutoGreeting sourceOnly = Demo.class.getAnnotation(AutoGreeting.class);

        System.out.println("runtime label = " + runtime.value());
        System.out.println("SOURCE annotation visible = " + (sourceOnly != null));
        System.out.println("generated method = " + GreetingGenerated.message());
    }
}
~~~

Phần `Demo.class.getAnnotation(AutoGreeting.class)` **biên dịch hợp lệ** vì kiểu annotation `AutoGreeting` có trong classpath. Nhưng annotation này đã có `SOURCE` retention, nên metadata annotation đó không tồn tại trong classfile của `Demo` và kết quả Reflection là `null`. Ngược lại `RuntimeLabel` có `RUNTIME` retention nên `getAnnotation(...)` đọc được `"active"`.

### 5.5. Biên dịch và chạy từng bước

Chạy từ thư mục chứa cả bốn file. Các lệnh dưới đây hoạt động với `javac`/`java` Java 17+; dấu `\` cuối dòng chỉ là cách minh họa shell trong ví dụ nhiều dòng, vì vậy các lệnh thực thi được viết thành **một dòng mỗi lệnh**:

~~~bash
mkdir build
mkdir generated
javac --release 17 -encoding UTF-8 -d build AutoGreeting.java RuntimeLabel.java GreetingProcessor.java
javac --release 17 -encoding UTF-8 -cp build -processorpath build -processor GreetingProcessor -s generated -d build Demo.java
java -cp build Demo
~~~

**Lưu ý Windows:** Tạo thư mục bằng `mkdir` trong PowerShell hoặc CMD cũng được khi thư mục chưa tồn tại. Nếu dùng thử lại ở nơi đã có `build`/`generated`, hãy tạo một thư mục ví dụ mới hoặc xóa riêng output của bài demo để không dùng nhầm `GreetingGenerated.class` từ lần chạy cũ.

Diễn giải hai lệnh `javac`:

1. Lệnh thứ nhất biên dịch annotation types và processor. Compiler cần tìm lớp processor `GreetingProcessor` **đã được biên dịch** ở bước sau.
2. Lệnh thứ hai có `-cp build` để mã `Demo` tìm thấy các annotation và class cần thiết; `-processorpath build` để compiler nạp processor; `-processor GreetingProcessor` kích hoạt chính processor đã chỉ định; `-s generated` chọn nơi lưu **generated source**; `-d build` chọn nơi lưu **compiled classes**.
3. Processor nhận `@AutoGreeting` ở `Demo`, tạo source cho `GreetingGenerated` và compiler biên dịch class sinh ra. `Demo` gọi class này như một dependency bình thường đã được type-check.
4. Lệnh `java` cuối cùng mới chạy chương trình và gọi runtime Reflection.

**Stdout đã kiểm chứng với `javac --release 17`:**

~~~text
runtime label = active
SOURCE annotation visible = false
generated method = Generated at compile time
~~~

Sau biên dịch, bạn có thể thấy các file đầu ra tiêu biểu:

~~~text
build/
  AutoGreeting.class
  RuntimeLabel.class
  GreetingProcessor.class
  Demo.class
  GreetingGenerated.class
generated/
  GreetingGenerated.java
~~~

Đây là **cây file minh họa**, không hiển thị mọi metadata có thể có. Hai class `AutoGreeting` và `RuntimeLabel` có sẵn như các kiểu annotation, nhưng **annotation `@AutoGreeting` gắn trên `Demo`** không nằm trong bytecode `Demo.class` vì retention `SOURCE`. Đừng nhầm “class định nghĩa annotation được biên dịch” với “dấu annotation gắn trên element được giữ lại”.

**Một thử nghiệm có ý nghĩa:** Trong thư mục build sạch, bỏ `-processor GreetingProcessor` hoặc bỏ processor path trong lệnh biên dịch `Demo.java`; compiler có thể không tạo `GreetingGenerated`, và lời gọi tới lớp sinh ra sẽ dẫn tới lỗi không tìm được biểu tượng. Tạo build sạch trước thử nghiệm để tránh output cũ che đi vấn đề. Việc processor tự động được phát hiện còn phụ thuộc cấu hình phiên bản JDK/build tool và service registration; ví dụ này chỉ định `-processor` rõ ràng để không phụ thuộc cơ chế auto-discovery.

## 6. Vòng đời của annotation processor và giới hạn source-time

Một số khác biệt dễ bị bỏ qua:

**Nhiều rounds.** Một processor sinh ra tệp Java ở round trước có thể khiến compiler tạo thêm round để xử lý declarations mới. Không cố tạo cùng một tên tệp qua `Filer` trong nhiều round: việc đó có thể gây `FilerException`. Thiết kế generator nên gắn output với source element và xác định tên duy nhất.

**Không phải runtime classloader.** Compiler model `TypeElement` và `TypeMirror` không bắt buộc tương ứng với một `Class<?>` có thể nạp trong JVM đang chạy processor. Đừng gọi `newInstance` đối với class đang được compile để “thử nghiệm” annotation. Nếu cần kiểm tra method/field theo chữ ký, dùng model `Elements`/`Types` từ compiler.

**Không sửa source do người dùng viết.** Processor nên tạo **tệp mới** qua `Filer` hoặc phát lỗi/warning, không tự ghi đè source đang được compile. Với Java chuẩn JSR 269, processor không phải công cụ biến đổi cú pháp tùy ý của mọi class hoặc bytecode. Một số thư viện dùng compiler internals/AST rewriting, bytecode instrumentation hoặc compiler plugin riêng; không nên coi đó là chức năng mặc định của `AbstractProcessor`.

**Không đảm bảo mọi source đã có tại mọi round.** Processor cần tương thích chu kỳ rounds; nếu phụ thuộc vào output processor khác, phải quản lý thứ tự và availability thay vì giả định tất cả generated classes đã tồn tại ngay round đầu.

**Processor là build-time executable code.** Một dependency processor được compiler thực thi trong môi trường build; cần kiểm soát nguồn dependency, quyền truy cập file/network của môi trường build và tính reproducible như với build plugins. Annotation metadata đầu vào có thể không đáng tin; không coi processor là bộ lọc bảo mật hoàn hảo cho dữ liệu runtime.

## 7. Giới hạn của Reflection runtime

Reflection chỉ làm việc trong phạm vi các lớp/module mà runtime nhìn thấy và các thành viên mà API có thể truy cập. Nó không thể:

- Khôi phục annotation `SOURCE` đã bị bỏ khỏi classfile sau biên dịch.
- Thay compiler để phát lỗi cú pháp/kiểu trước khi chương trình được biên dịch.
- Mặc định truy cập được private members qua JPMS chỉ vì gọi `getDeclaredFields()`.
- Tự sinh `GreetingGenerated.java` trong processing rounds quá khứ. Có thể viết mã sinh nguồn lúc chạy nhưng đó là một quy trình khác, không phải annotation processing của lần build đã xong.
- Tự xác minh đầy đủ quy tắc nghiệp vụ chỉ bằng tên field/kiểu/annotation. Annotation runtime có thể mang metadata, nhưng validation cần một API/engine chủ động thực thi.

`setAccessible(true)` có thể bị từ chối khi package của module khác không được `opens` phù hợp; `IllegalAccessException` hoặc `InaccessibleObjectException` có thể xuất hiện trong từng đường gọi. Lập trình công cụ theo public API thường bền vững hơn việc dựa vào deep reflection.

**Về hiệu năng:** Reflection có chi phí tra cứu/dispatch và hạn chế compile-time type checking; annotation processor có thể sinh mã tường minh để giảm introspection runtime nhưng làm quy trình build phức tạp hơn. Không suy luận rằng generated code luôn nhanh hơn mọi Reflection path: đo workload thực tế, xem khả năng cache metadata và chi phí khởi tạo.

**Về triển khai:** Trong môi trường giới hạn dynamic class metadata, AOT/native image hoặc runtime có chính sách đóng gói đặc biệt, compile-time code generation có thể có lợi. Nhưng khả năng hỗ trợ cụ thể phụ thuộc runtime/toolchain và metadata configuration, không có quy tắc tuyệt đối cho mọi dự án Java.

## 8. Chọn công cụ theo yêu cầu

| Yêu cầu | Lựa chọn khởi đầu hợp lý | Vì sao |
|---|---|---|
| Phát lỗi annotation sai kiểu ngay khi build | Annotation processor | Có compiler model và `Messager.ERROR` |
| Sinh adapter/mapper cố định theo kiểu nguồn | Annotation processor | Mã sinh được compiler kiểm tra kiểu, có thể gọi trực tiếp |
| Tạo tài liệu/index từ annotation trong source | Annotation processor | Trích xuất metadata khi biên dịch, tạo resource |
| Tìm plugin mới xuất hiện khi app đã chạy | Reflection hoặc contract/plugin discovery runtime | Compiler không biết trước tất cả runtime types |
| Đọc annotation dùng để điều khiển xử lý lúc chạy | Reflection + `RUNTIME` retention | Annotation có mặt trong runtime metadata |
| Gọi method theo tên lấy từ cấu hình lúc chạy | Reflection hoặc phương án dispatch runtime được kiểm soát | Tên quyết định sau compile |
| Giảm startup scan khi danh sách type có thể xác định trước | Compile-time index/code generation | Đưa phần khám phá về lúc build |
| Cần contract dùng chung cho plugin có cấu trúc cố định | Interface/factory/`ServiceLoader` trước khi chọn Reflection sâu | Rõ API, giữ kiểm tra kiểu và quyền truy cập |

**Lựa chọn thứ ba thường tốt hơn:** Nếu bạn đã biết interface hoặc service contract, dùng Java bình thường, interface, factory hay `ServiceLoader` có thể đơn giản hơn cả một hệ thống generator hoặc dynamic invocation phức tạp. Không cần áp dụng annotation processing khi việc kiểm tra đã được ngôn ngữ/type system đảm bảo.

Một framework thực tế có thể chọn **hybrid**:

1. Annotation processor sinh registry/adapters cho những type thấy tại build.
2. Registry runtime khởi tạo đối tượng qua constructor/factory và gọi method có kiểu tĩnh.
3. Reflection chỉ xử lý những type động ngoài danh sách build-time nếu use case thật sự yêu cầu.
4. Cả hai đường đều phải kiểm tra module access, hợp đồng API, validate dữ liệu và xử lý lỗi.

## 9. Lỗi thường gặp và cách kiểm chứng

| Hiện tượng | Nguyên nhân có thể | Cách xác minh |
|---|---|---|
| `getAnnotation(MyAnnotation.class)` trả `null` | Annotation `SOURCE`/`CLASS`, sai element, annotation không xuất hiện | Kiểm tra `@Retention`/`@Target` và element được hỏi |
| Processor không chạy | Thiếu `-processor`, không đăng ký service, sai processor path, build tool không cấu hình | Biên dịch sạch, kiểm tra `-processorpath` và processor discovery |
| `cannot find symbol GreetingGenerated` | Generated source không được tạo/biên dịch, hoặc classpath sai | Kiểm tra thư mục `-s`, `-d` và compiler diagnostics |
| `FilerException: Attempt to recreate a file` | Sinh cùng tên nhiều lần trong các rounds hoặc processor khác nhau | Quản lý output names và theo dõi generated elements |
| Runtime ném `ClassNotFoundException` | Class không nằm trong classpath/module path được loader thấy | Kiểm tra packaging và class loader runtime |
| Runtime ném `InaccessibleObjectException` | Deep reflection bị JPMS chặn | Thiết kế qua public API hoặc chính sách `opens` được cấp có chủ đích |
| “Annotation đọc được” nhưng logic không chạy | Reflection chỉ đọc metadata, không tự thực thi handler/validator | Gọi đúng engine hoặc explicit invocation |
| Generated class hiện ra dù đã tắt processor | Class file từ build trước | Tạo output build sạch để kiểm chứng |

### Tự luyện tập

1. Đổi `@Retention(RetentionPolicy.SOURCE)` của `AutoGreeting` thành `CLASS`, rồi thành `RUNTIME`. Trước mỗi lần chạy hãy biên dịch lại sạch; so sánh `Demo.class.getAnnotation(AutoGreeting.class)`.
2. Bỏ `@RuntimeLabel("active")` trên `Demo` và xác nhận `getAnnotation(RuntimeLabel.class)` trả `null`. Hãy sửa chương trình kiểm tra `null` trước khi gọi `value()`; giải thích NPE nếu không kiểm tra.
3. Bỏ `-processor GreetingProcessor` trong lần compile thứ hai trên build sạch. Compiler báo lỗi gì và vì sao?
4. Sửa processor sinh một method trả chuỗi khác rồi build lại. Cần rebuild bước nào trước khi output runtime thay đổi?
5. Sửa `@SupportedAnnotationTypes("AutoGreeting")` sang một tên không khớp. Điều gì xảy ra với class `GreetingGenerated`? Vì sao?
6. Thiết kế annotation `@MinLength(3)` cho method parameter hoặc field: nêu trường hợp nên kiểm tra ở compile time, trường hợp bắt buộc kiểm tra dữ liệu ở runtime. Đừng coi annotation tự tạo ra validation.
7. Khi một plugin được tải từ một JAR mới sau khi ứng dụng đã build, chọn Reflection, code generation hay interface/`ServiceLoader`. Giải thích dữ kiện nào không có ở compile time.
8. Với ứng dụng dùng JPMS, vì sao việc processor đọc source model thành công không chứng minh được runtime có thể gọi private constructor của type đó?

**Ghi nhớ:** Annotation processing là **công việc của compiler** với source/type model để kiểm tra và sinh mã; Reflection là **công việc của JVM ở runtime** với class/member metadata và quyền truy cập. `RetentionPolicy.SOURCE` dành cho compile-time processing; `RetentionPolicy.RUNTIME` cho phép Reflection đọc annotation tương ứng. Chọn cơ chế theo **thời điểm bạn có đủ thông tin**, kiểu kết quả cần tạo, yêu cầu kiểm tra và chi phí duy trì hệ thống.

### Tài liệu Java 17 chính thức

- [Annotation Processing: `AbstractProcessor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/annotation/processing/AbstractProcessor.html)
- [`RoundEnvironment`](https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/annotation/processing/RoundEnvironment.html)
- [`Filer` sinh source và resources](https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/annotation/processing/Filer.html)
- [`RetentionPolicy`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)
- [`Class.getAnnotation` / `AnnotatedElement`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html)
- [`javac` options gồm `-processor` và `-processorpath`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-008">So sánh với interface, Strategy, ServiceLoader và code generation</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh Reflection với interface, Strategy, ServiceLoader và code generation trong Java

## 1. Cùng một bài toán, nhiều cách giải khác nhau

Giả sử một ứng dụng Java cần chọn cách biến đổi chuỗi, xử lý thanh toán hoặc tạo serializer dựa trên cấu hình. Có nhiều mức độ linh hoạt:

- **Interface và đa hình thông thường:** biết hợp đồng từ lúc biên dịch, gọi trực tiếp qua interface.
- **Strategy pattern:** đóng gói nhiều thuật toán cùng hợp đồng và chọn một thuật toán theo ngữ cảnh runtime.
- **`ServiceLoader`:** phát hiện các implementation của một **service interface/abstract class** được khai báo qua cơ chế provider của Java, không cần liệt kê từng implementation trong mã client.
- **Reflection:** truy vấn kiểu và thành viên tại runtime, có thể nạp lớp theo tên và tạo/gọi implementation khi cấu trúc được biết muộn.
- **Code generation:** tạo Java source, bytecode hoặc mã truy cập có kiểu theo schema/annotation/metadata, thường để giảm thao tác runtime lặp lại.

Đây không phải năm phương án hoàn toàn loại trừ nhau. Ví dụ: `ServiceLoader` trả về các implementation interface; ứng dụng có thể lưu chúng trong danh sách Strategy. Framework có thể dùng Reflection lúc khởi động rồi tạo mã hoặc cache dispatcher cho phần xử lý lặp lại.

**Nguyên tắc lựa chọn:** ưu tiên hợp đồng tường minh, kiểm tra được tại compile time; thêm dynamic discovery/introspection khi yêu cầu thực tế bắt buộc hoặc lợi ích vượt chi phí. Reflection giúp xử lý cấu trúc chưa biết trước, nhưng không nên dùng tên method dưới dạng chuỗi khi lời gọi có thể biểu diễn an toàn bằng interface.

## 2. Interface: hợp đồng ổn định và đa hình trực tiếp

Một interface quy định hành vi mà caller sử dụng. Ví dụ:

```java
public interface Transform {
    String apply(String input);
}
```

Implementation:

```java
public class UpperCaseTransform implements Transform {
    @Override
    public String apply(String input) {
        return input.toUpperCase(java.util.Locale.ROOT);
    }
}
```

Client:

```java
Transform transform = new UpperCaseTransform();
System.out.println(transform.apply("Hi")); // HI
```

Ba đoạn trên là **các phần của ví dụ nhiều file** sẽ được trình bày đầy đủ ở phần 7; mỗi `public` top-level type nằm trong file cùng tên.

Lợi ích:

1. Trình biên dịch kiểm tra tên method, kiểu tham số và kiểu trả về.
2. IDE refactor/rename dễ tìm nơi sử dụng và báo lỗi compile khi đổi hợp đồng.
3. Caller chỉ cần biết `Transform`, không phải tìm `Method`, thực hiện `setAccessible` hay tự ép kết quả `Object`.
4. Gọi method thông thường có thể được JIT tối ưu theo đặc điểm runtime; không có chi phí tự tạo metadata Reflection cho từng lời gọi.
5. Có thể tiêm implementation bằng constructor injection, factory hoặc DI container.

Giới hạn của **interface đơn thuần**: ai đó vẫn phải quyết định tạo implementation nào. Nếu cần plugin tự xuất hiện mà mã client không biết tên lúc build, phải thêm cơ chế discovery như `ServiceLoader` hoặc cơ chế DI/registry thích hợp.

Không có lý do dùng Reflection chỉ để gọi `transform.apply(text)` nếu đối tượng đã triển khai `Transform` và hợp đồng đó đáp ứng nhu cầu.

## 3. Strategy pattern: chọn thuật toán runtime mà không phải gọi Reflection

**Strategy** là pattern thiết kế: đưa hành vi biến đổi thành các object cùng interface, rồi chọn object cần dùng khi chương trình chạy. Interface là phần hợp đồng; Strategy mô tả **cách tổ chức và hoán đổi thuật toán**.

Ví dụ registry:

```java
java.util.Map<String, Transform> strategies = java.util.Map.of(
        "upper", new UpperCaseTransform(),
        "lower", new LowerCaseTransform());

Transform selected = strategies.get("lower");
if (selected == null) {
    throw new IllegalArgumentException("Unsupported transform");
}
System.out.println(selected.apply("Hi")); // hi
```

Đoạn minh họa giả định `Transform`, `UpperCaseTransform` và `LowerCaseTransform` đã được định nghĩa. Ví dụ chạy độc lập phía dưới cung cấp đủ file.

Ưu điểm:

- Có thể thay đổi thuật toán ở runtime dựa vào request, config hoặc feature flag mà vẫn giữ lời gọi qua interface.
- Mỗi strategy có test riêng, phụ thuộc có thể tiêm bằng constructor, dễ mô tả ownership và vòng đời.
- Có thể tập trung kiểm tra tên strategy, chính sách fallback và khả năng truy cập ở một điểm chọn.
- Logic điều phối không cần `Class.forName` hoặc `getMethod`.

Giới hạn:

- Registry `Map.of(...)` tĩnh biết trước những class có thể chọn tại build time. Nếu muốn thêm plugin JAR mà không sửa danh sách, cần cơ chế cung cấp/đăng ký strategy động.
- String key của registry vẫn có nguy cơ sai chính tả tại runtime; ứng dụng có thể dùng enum/key có kiểm tra hoặc validate config khi khởi động.
- Chiến lược có thể có state mutable; việc tái sử dụng cùng instance cho nhiều thread đòi hỏi thiết kế thread safety phù hợp.

**Strategy không tự động phát hiện implementation.** Bạn có thể kết hợp Strategy + `ServiceLoader` để thu nhận các nhà cung cấp từ classpath/module path, rồi chọn theo một ID ổn định.

## 4. ServiceLoader: tìm implementation được đăng ký qua SPI

`java.util.ServiceLoader<S>` là cơ chế **Service Provider Interface (SPI)** của JDK. Ứng dụng xác định service contract `S`; một hoặc nhiều provider công bố rằng chúng có thể cung cấp implementation của `S`. Client duyệt và sử dụng provider qua service type, thay vì cần biết tên implementation trước.

```java
java.util.ServiceLoader<Transform> loader =
        java.util.ServiceLoader.load(Transform.class);

for (Transform provider : loader) {
    System.out.println(provider.apply("Hi"));
}
```

Đây là mảnh minh họa. **Thứ tự provider không nên được dùng làm hợp đồng nghiệp vụ**; ví dụ đầy đủ sẽ sắp xếp kết quả để stdout xác định.

### 4.1. Classpath: đăng ký ở META-INF/services

Với ứng dụng chạy trên **classpath** (không phải module có tên), nếu service interface là `Transform` trong unnamed package, resource cần có đường dẫn:

```text
META-INF/services/Transform
```

Nội dung:

```text
UpperCaseTransform
LowerCaseTransform
```

Tên mỗi provider phải là **tên lớp nhị phân** của implementation. Trong dự án thực, nếu interface là `com.example.spi.Transform`, tên file phải là:

```text
META-INF/services/com.example.spi.Transform
```

và từng dòng bên trong ghi tên đầy đủ, ví dụ `com.example.impl.UpperCaseTransform`. Đăng ký provider ở một file cấu hình tự đặt tên sẽ không làm `ServiceLoader` tự nhận ra.

Với provider được triển khai trên **classpath**, dạng đơn giản nhất là public concrete class implement service type và có public constructor không tham số. Provider phải nhìn thấy được từ class loader có trách nhiệm đọc cấu hình. Lỗi package/classpath, thiếu public constructor, sai tên hoặc provider không đúng service type có thể dẫn tới `ServiceConfigurationError` khi duyệt.

### 4.2. Module path: uses và provides

Với Java Platform Module System (JPMS), cú pháp ví dụ (trích đoạn **module descriptors**, không ghép trực tiếp vào chương trình classpath ở phần 7):

```java
// Trong module client, ví dụ: module client.app
uses com.example.spi.Transform;
```

```java
// Trong module provider, ví dụ: module provider.impl
provides com.example.spi.Transform
    with com.example.impl.UpperCaseTransform;
```

Ứng dụng thường thêm `requires` để đọc module chứa service interface. Hai directive `uses` và `provides ... with ...` tham gia liên kết service/provider theo cơ chế module; ví dụ JPMS cũng đòi hỏi khai báo module và cấu trúc package hợp lệ, nên các fragment trên **không phải project độc lập**.

ServiceLoader hỗ trợ named-module provider theo hợp đồng riêng, bao gồm khả năng dùng provider method `public static provider()` trong provider class khi thỏa quy tắc tương ứng; không đồng nhất toàn bộ quy tắc named modules với yêu cầu constructor không tham số của classpath provider.

### 4.3. Discovery, lazy loading và vòng đời

- `ServiceLoader.load(Transform.class)` dùng thread context class loader trong overload phổ biến này; overload `load(service, classLoader)` cho phép chỉ định class loader.
- Việc phát hiện/cài đặt provider thường **lười**; một số lỗi cấu hình chỉ lộ ra khi duyệt iterator hoặc stream/lấy instance, không nhất thiết ngay tại lời gọi `load(...)`.
- ServiceLoader lưu cache provider đã khám phá; `reload()` xóa cache của loader để cho phép tra cứu lại. Không suy rằng mọi JAR mới tự được phát hiện nếu class loader không thấy nó.
- `ServiceLoader` **không thread-safe**; khi nhiều thread dùng cùng một instance, ứng dụng phải đồng bộ hoặc tổ chức loader/registry phù hợp.
- Không dùng “provider đầu tiên” để suy thứ tự ưu tiên khi hệ thống không có quy tắc ordering tường minh; nếu cần, đọc metadata/priority riêng rồi sắp xếp.
- `ServiceConfigurationError` là một `Error`, không phải `Exception`. Vì vậy `catch (Exception)` không phải biện pháp xử lý đầy đủ cho provider config hỏng.

**Ranh giới:** ServiceLoader tìm implementation **đã đăng ký cho service contract**, chứ không tự scan mọi class trong classpath rồi lựa chọn theo tên method/annotation bất kỳ. Khi cần xử lý cấu trúc trường/method chưa có interface chung, Reflection có thể vẫn cần.

## 5. Reflection: khi không có đủ cấu trúc ở compile time

Reflection (`java.lang.Class`, `java.lang.reflect.Method`, `Field`, `Constructor`, ...) cho phép đọc metadata và thực hiện thao tác trên class/member lúc runtime.

Ví dụ cấu hình chứa tên implementation:

```java
String configuredClassName = "UpperCaseTransform";
Class<? extends Transform> implementation =
        Class.forName(configuredClassName).asSubclass(Transform.class);
Transform transform = implementation.getDeclaredConstructor().newInstance();
System.out.println(transform.apply("Hi")); // HI
```

Đây là mảnh code minh họa nằm trong phương thức có `throws ReflectiveOperationException` hoặc catch hợp lệ. Mã dùng `asSubclass(Transform.class)` để kiểm tra class đã nạp thực sự có thể dùng như `Transform`. Sau khi tạo instance, caller lại gọi qua interface bình thường.

### Điểm mạnh

1. Không cần biết trước **tất cả** class cụ thể tại compile time.
2. Hữu ích khi đọc annotation, xác định constructor/method/field, triển khai serializer/mapper, diagnostic tooling hoặc framework injection.
3. Có thể chọn class loader phù hợp với plugin, phân tích cấu trúc ở runtime và xây adapter cho các kiểu không cùng interface.
4. Có thể kết hợp cache metadata như `ClassValue<T>` để tránh tính toán lặp lại.

### Chi phí và giới hạn

- Tên class/method truyền bằng String thường chỉ được xác minh khi chạy; đổi tên API dễ làm hỏng cấu hình khi runtime.
- `getMethod`/`getDeclaredMethod` yêu cầu đúng tên và parameter types; sai overload/visibility sẽ ném checked lookup exception.
- `Method.invoke`, `Constructor.newInstance` có hợp đồng kiểm tra argument, access và wrapper exception (`InvocationTargetException` với lỗi do mã đích ném).
- Phân tích metadata và gọi gián tiếp có overhead; mức độ so với lời gọi trực tiếp phụ thuộc JDK, JIT, số lần gọi, khả năng caching. **Không gán một hệ số chậm cố định** cho mọi trường hợp.
- Các giới hạn JPMS về visibility/`opens` vẫn tồn tại; `setAccessible(true)` không bảo đảm thành công.
- Nạp/khởi tạo class dựa trên tên do người dùng không tin cậy nhập có thể kích hoạt code không mong muốn. Khi tạo plugin, cần allowlist, hợp đồng interface, class loader và chính sách phân quyền phù hợp.
- Với closed-world/native image, Reflection động có thể cần metadata cấu hình/đăng ký trước build; phải theo quy tắc của runtime/compiler đang dùng.

Dùng Reflection tốt khi nó phục vụ **khả năng khám phá/cấu trúc động**. Nếu sau khi nạp xong đã biết một interface, hãy chuyển sang gọi interface để đơn giản hóa phần còn lại.

## 6. Code generation: chuyển việc phân tích sang lúc build hoặc tạo bộ thực thi chuyên biệt

**Code generation** là nhóm kỹ thuật, không phải một API duy nhất:

- **Compile time source generation:** annotation processor (JSR 269), schema compiler hoặc build task sinh ra `.java`/resource trước hoặc trong quá trình compile. Client sau đó gọi generated code có kiểu.
- **Build time bytecode generation:** một công cụ sinh bytecode/class ở bước build trước khi ứng dụng chạy; đôi khi là augmentation của framework.
- **Runtime bytecode generation:** thư viện tạo class/adapter/proxy ở runtime từ metadata đã biết; ví dụ hệ proxy/mock/interceptor. Cách này có thể vẫn cần Reflection/MethodHandle trong giai đoạn phân tích và vẫn phụ thuộc class loader.
- **Ahead-of-time metadata/index generation:** build lập bảng descriptor hoặc index của annotation/type để phần runtime không phải scan rộng. Đây là hướng bổ trợ Reflection chứ không luôn thay thế việc Reflection dùng để gọi.

Ví dụ trước–sau mang tính **ý tưởng thiết kế**, không phải một code generator hoàn chỉnh:

```java
// Trước: runtime tìm method và invoke qua Reflection.
Method method = type.getMethod("apply", String.class);
String value = (String) method.invoke(instance, "Hi");

// Sau: nếu generator biết class/interface và tạo adapter ở build time,
// client gọi qua interface có kiểu. Adapter được generator tạo sẵn.
Transform adapter = generatedFactory.create(config);
String value2 = adapter.apply("Hi");
```

Đoạn trên chỉ minh họa phương án thay thế và giả định có `generatedFactory`, `type`, `instance`, `config` và `Transform` phù hợp. Không phải chương trình Java độc lập.

### Ưu điểm khi generated code phù hợp

- Nhiều lỗi mismatch được phát hiện sớm hơn khi compile generated code.
- Giảm việc quét annotation/Reflection hoặc tìm member động lặp lại khi khởi động/xử lý request.
- Có thể tạo dispatcher/serializer/mapper tối ưu cho schema cố định.
- Dễ kiểm tra những type/member cần dùng trong môi trường AOT khi generator tạo ra registration/metadata có kiểm soát.

### Đổi lại

- Có build step, processor/plugin/compiler và generated artifacts cần quản lý.
- Thay đổi schema hoặc annotation đòi hỏi regenerate và compile lại; cần test tính tái tạo/incremental build.
- Mã sinh có thể khó đọc stack trace, debug, map source hoặc quản lý phiên bản.
- Nếu class thật sự chỉ biết sau khi đã build và runtime cần tạo hành vi mới, source generation lúc build **không tự giải quyết** dynamic runtime discovery.
- Runtime bytecode generation có overhead tạo/lưu class và vẫn có ràng buộc loader/module/security; nó không làm mọi lời gọi tự nhiên nhanh hơn.

**Phân biệt:** `ServiceLoader` phát hiện implementation đã có; code generation **tạo mã** hoặc metadata; Reflection **quan sát/gọi cấu trúc runtime**; Strategy **điều phối thuật toán**. Các nhiệm vụ này có thể bổ trợ nhau trong một kiến trúc.

## 7. Ví dụ Java 17 chạy đầy đủ: bốn cách cho một service

Ví dụ sau minh họa **interface, Strategy, Reflection và ServiceLoader** cùng sử dụng `Transform`. Code generation không được chạy trong ví dụ vì nó cần thêm công cụ build/processor; phần 6 giải thích kiến trúc đó.

Tạo cấu trúc thư mục:

```text
demo/
├── Transform.java
├── UpperCaseTransform.java
├── LowerCaseTransform.java
├── ChoiceDemo.java
└── META-INF/
    └── services/
        └── Transform
```

**File `Transform.java`:**

```java
public interface Transform {
    String apply(String input);
}
```

**File `UpperCaseTransform.java`:**

```java
public class UpperCaseTransform implements Transform {
    @Override
    public String apply(String input) {
        return input.toUpperCase(java.util.Locale.ROOT);
    }
}
```

**File `LowerCaseTransform.java`:**

```java
public class LowerCaseTransform implements Transform {
    @Override
    public String apply(String input) {
        return input.toLowerCase(java.util.Locale.ROOT);
    }
}
```

**File `ChoiceDemo.java`:**

```java
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class ChoiceDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        Transform viaInterface = new UpperCaseTransform();
        System.out.println("interface: " + viaInterface.apply("Hi"));

        Map<String, Transform> strategies = Map.of(
                "upper", new UpperCaseTransform(),
                "lower", new LowerCaseTransform());
        System.out.println("strategy: " + strategies.get("lower").apply("Hi"));

        Class<? extends Transform> implementation =
                Class.forName("UpperCaseTransform").asSubclass(Transform.class);
        Transform viaReflection = implementation.getDeclaredConstructor().newInstance();
        System.out.println("reflection: " + viaReflection.apply("Hi"));

        List<String> providers = ServiceLoader.load(Transform.class)
                .stream()
                .sorted(Comparator.comparing(p -> p.type().getName()))
                .map(p -> p.type().getSimpleName() + "=" + p.get().apply("Hi"))
                .toList();
        System.out.println("ServiceLoader: " + providers);
    }
}
```

**Resource `META-INF/services/Transform`** (văn bản UTF-8, không phải mã Java):

```text
UpperCaseTransform
LowerCaseTransform
```

Biên dịch ở thư mục `demo/`:

```bash
javac --release 17 Transform.java UpperCaseTransform.java LowerCaseTransform.java ChoiceDemo.java
java -cp . ChoiceDemo
```

Kết quả stdout đã kiểm tra:

```text
interface: HI
strategy: hi
reflection: HI
ServiceLoader: [LowerCaseTransform=hi, UpperCaseTransform=HI]
```

**Tại sao bốn dòng này khác nhau?**

1. **`interface`:** client tự tạo `UpperCaseTransform` và gọi `apply`; tên method được kiểm tra khi compile.
2. **`strategy`:** client chọn `"lower"` từ map chứa các đối tượng `Transform`. Cơ chế chọn dựa vào string key của registry, còn thực thi vẫn là lời gọi interface.
3. **`reflection`:** client biết `"UpperCaseTransform"` dưới dạng tên class runtime, kiểm tra `asSubclass`, tạo instance qua constructor rồi gọi interface. Không cần `Method.invoke` cho `apply` vì sau khi có `Transform`, lời gọi trực tiếp là đủ.
4. **`ServiceLoader`:** JDK tìm các provider được khai báo trong resource, tạo các instance và chạy qua interface. Ví dụ dùng `ServiceLoader.stream()` để truy vấn `Provider.type()`, sắp xếp theo tên class nhằm ổn định stdout, rồi `Provider.get()` mới lấy instance.

**Phạm vi ví dụ:** đây là dự án classpath trong **unnamed package** để có thể copy/chạy nhanh, không phải cấu trúc được khuyến nghị cho ứng dụng lớn. Trong dự án thực nên dùng package và cập nhật tên file `META-INF/services/<fully.qualified.ServiceInterface>` tương ứng. Chạy đúng thư mục root để resource `META-INF/services/Transform` nằm trên classpath; chỉ biên dịch các file Java mà quên resource sẽ làm danh sách provider rỗng.

Nếu đổi sang JPMS named modules, cần thêm `module-info.java`, package hợp lệ và directive `uses/provides` như đã trình bày ở phần 4; không trộn hai cách khai báo tùy tiện.

## 8. Bảng so sánh theo nhu cầu kiến trúc

| Tiêu chí | Interface trực tiếp | Strategy | ServiceLoader | Reflection | Code generation |
|---|---|---|---|---|---|
| Hợp đồng dùng ở client | Interface/kiểu compile time | Interface chung của thuật toán | Service interface/abstract class | Có thể chỉ biết tên/metadata runtime | Kiểu/adapter do generator tạo |
| Chọn implementation | Gắn cứng, inject hoặc factory | Registry/context/runtime policy | Provider registration và discovery | Tên class, metadata, loader, quy tắc tự xây | Build/schema/processor hoặc runtime generator |
| Có phát hiện plugin JAR tự đăng ký? | Không tự có | Không tự có | Có, khi JAR/module và provider config hiển thị đúng | Có thể tự xây logic discovery, nhưng `Class.forName` riêng lẻ không scan plugin | Không tự có nếu chỉ generation build time |
| Refactor-safe | Cao | Cao ở API, registry key cần quản lý | Cao ở interface, provider config phải đúng | Dễ lỗi runtime khi dùng string | Cao với generated typed API, tùy chất lượng generator |
| Metadata tùy ý | Hạn chế ở contract | Hạn chế ở contract | Có thể dùng metadata ngoài để chọn | Mạnh: fields/methods/annotations | Có thể trích ở build time hoặc generate index |
| Lỗi điển hình | Compile/type mismatch | Sai key/cấu hình, logic thuật toán | `ServiceConfigurationError`, provider không thấy | `ClassNotFoundException`, `NoSuchMethodException`, `IllegalAccessException`... | Generator/build mismatch, output lỗi thời |
| Hiệu năng | Lời gọi đa hình thông thường | Lời gọi interface + chọn strategy | Chi phí discovery/provider creation, sau đó gọi interface | Lookup/invoke động, có thể cache | Build cost hoặc runtime generation cost; thường giảm xử lý lặp |
| Linh hoạt sau build | Phụ thuộc wiring | Đổi giữa strategy có sẵn | Thêm provider qua cấu hình/module/class loader phù hợp | Có thể xử lý class/metadata chưa biết trước | Build generation cần rebuild; runtime generation linh hoạt hơn |
| Độ phức tạp vận hành | Thấp | Thấp đến vừa | Resource/module/loader/provider lifecycle | Access, lỗi runtime, security, module, classloader | Build pipeline, generated code, debug, versioning |

**Không có thứ hạng hiệu năng hay tính linh hoạt tuyệt đối.** Một ServiceLoader có thể dùng Reflection/constructor nội bộ để tạo provider, trong khi lời gọi sau đó qua interface. Một framework codegen có thể tạo class vào runtime và dùng Reflection để discover metadata ở bước đầu. Đo đúng phần việc người dùng quan tâm trước khi thay kiến trúc.

## 9. Chọn giải pháp qua các câu hỏi thực tế

### 9.1. “Tôi đã biết method cần gọi ở compile time”

**Chọn interface/lời gọi Java trực tiếp.** Nếu có nhiều implementation đổi được, dùng Strategy với factory/registry hoặc DI. Reflection chỉ để gọi một method cố định thường làm mất type safety mà không đem lại khả năng hữu ích.

### 9.2. “Tôi có nhiều thuật toán, mỗi request chọn một cái”

**Chọn Strategy** với interface chung, registry ID có kiểm tra và chính sách fallback. Nếu thuật toán được tiêm bởi framework, vẫn là cùng ý tưởng đa hình. Không cần tạo class dựa theo tên bất kỳ từ user input.

### 9.3. “Người viết plugin đóng gói JAR riêng, app chỉ biết service interface”

**Chọn ServiceLoader** hoặc một plugin framework tương đương. Định nghĩa rõ contract, provider metadata, version compatibility và class loader. Dùng Strategy registry phía trên nếu cần chọn provider theo ID, priority hoặc quyền người dùng.

### 9.4. “Tôi cần đọc field/method/annotation của những class không có interface chung”

**Chọn Reflection** khi cần inspect runtime class, kết hợp cache `ClassValue` hoặc các descriptor đã phân tích khi thao tác lặp lại. Tách bước khám phá khỏi hot path và kiểm soát quyền truy cập/module.

### 9.5. “Tôi có schema/type ổn định, throughput cao, xử lý Reflection lặp lại nhiều”

**Cân nhắc code generation** cho accessor/mapper/serializer/dispatcher nếu profiling xác nhận bottleneck. Đảm bảo generator kiểm tra schema, build tái tạo được artifact và có test tương thích. Nếu lớp chỉ xuất hiện sau build, cân nhắc runtime generation, metadata caching hoặc giải pháp hỗn hợp.

### 9.6. “Tôi phải chạy native image hoặc môi trường đóng”

Ưu tiên thiết kế có **danh sách dependency/type có thể phân tích trước**. Interface và generated code thường dễ kiểm soát; Reflection và dynamic loading có thể cần khai báo metadata/registration bổ sung theo runtime. `ServiceLoader` có thể được hỗ trợ bởi native-image tooling theo cách riêng; phải kiểm thử build thực tế, không mặc định cứ dùng SPI là tự hoạt động như JVM truyền thống.

## 10. Kết hợp các kỹ thuật cho đúng ranh giới

Kiến trúc plugin thực tế có thể đi qua các bước:

```text
Client -> Transform interface (hợp đồng ổn định)
                 |
                 v
          Strategy registry
                 |
      +----------+----------+
      |                     |
  built-in               ServiceLoader
 implementations              |
                                v
                        registered providers
                                |
                                v
                       Transform.apply(...)
```

Trong quá trình khởi động, chương trình:

1. Tạo registry từ implementation sẵn có.
2. Dùng `ServiceLoader` tìm provider mới, kiểm tra ID và capability, tránh trùng key.
3. Đưa provider vào registry dưới interface chung.
4. Khi request tới, chọn strategy theo ID đã được allowlist và gọi interface.
5. Nếu framework còn cần đọc annotation/schema của provider, dùng Reflection **một lần ở bước khám phá**, lưu metadata đã kiểm tra, không bắt mọi request scan lại.

Nếu serialization/model binding là tác vụ nặng theo schema cố định, build tool có thể tạo adapter rồi đăng ký adapter như một provider. Như vậy **code generation và ServiceLoader không mâu thuẫn**, và Reflection có thể đóng vai trò discovery/validation thay vì nằm trên mọi invocation.

## 11. Rủi ro thường gặp và cách xử lý

**Lỗi 1 — Dùng `Class.forName(userInput)` như registry plugin.** Có thể nạp/khởi tạo class ngoài ý muốn và làm khó kiểm soát phiên bản. Nên định nghĩa contract, kiểm tra ID implementation được phép, thực hiện discovery tường minh và bảo vệ class loader.

**Lỗi 2 — Nghĩ interface tự có dynamic discovery.** Interface chỉ là hợp đồng. Muốn plugin được phát hiện, cần `ServiceLoader`, DI/container registration hoặc cơ chế riêng.

**Lỗi 3 — Nghĩ Strategy bắt buộc có Reflection.** Strategy chỉ cần các object implement interface chung. Ví dụ Map registry không dùng Reflection để gọi method.

**Lỗi 4 — Quên `META-INF/services` hoặc ghi sai binary name.** `ServiceLoader` không đọc ngẫu nhiên mọi file cấu hình. Xác minh tên file service, mỗi dòng provider, package, classpath/module path và cấu trúc JAR sau build.

**Lỗi 5 — Giả định ServiceLoader luôn trả một provider duy nhất/theo thứ tự xác định.** Nó có thể thấy nhiều provider. Nếu ứng dụng cần quyết định một provider, phải tự định nghĩa priority/ID/fallback có kiểm tra.

**Lỗi 6 — Dùng `Method.invoke` dù đã có interface.** Sau khi kiểm tra `asSubclass` hoặc ServiceLoader trả `Transform`, cứ gọi `transform.apply(...)` để có type safety.

**Lỗi 7 — Đánh đồng code generation với hiệu năng luôn tốt hơn.** Nếu workload nhỏ, chi phí thêm processor/build/debug có thể vượt lợi ích; cần benchmark đúng môi trường.

**Lỗi 8 — Bỏ qua API và class loader compatibility.** Plugin có thể compile theo một version contract khác runtime; ServiceLoader có thể ném `ServiceConfigurationError`, Reflection `ClassCastException` hoặc `LinkageError`. Quy định version/compatibility và test integration.

**Lỗi 9 — Tưởng Reflection có thể vượt mọi access rule.** `getDeclaredMethod` tìm private member nhưng khả năng gọi vẫn phụ thuộc Java access control và module openness. Không thiết kế plugin contract dựa vào việc ép mở private method của thư viện bất kỳ.

**Lỗi 10 — Để generated code thành nguồn sự thật khó cập nhật.** Định nghĩa schema/annotation contract rõ ràng, để build tạo code tái lập, không sửa tay generated files rồi hy vọng build sau giữ nguyên.

## 12. Checklist quyết định và đo lường

Trước khi chọn, trả lời:

1. Cần biết **hành vi** hay cần biết **cấu trúc class**?
2. Contract có ổn định ở compile time không?
3. Implementation là built-in, lựa chọn sẵn có, hay plugin sẽ xuất hiện động?
4. Cần discovery theo service ID hay theo cấu trúc annotation/field tùy ý?
5. Startup time, hot-path latency, throughput và class loader unloading quan trọng thế nào?
6. Ứng dụng chạy JVM thường, JPMS modular, native image hay container plugin?
7. Ai sở hữu versioning, config validation, fallback và audit lỗi?
8. Có profiling hoặc benchmark chứng minh Reflection là bottleneck cần thay bằng codegen không?

Một cách triển khai kiểm soát được là bắt đầu từ **interface + Strategy**, mở discovery bằng **ServiceLoader** khi có plugin contract, dùng **Reflection** để xử lý metadata thật sự động, và chỉ chuyển phần ổn định/tốn chi phí sang **code generation** sau khi có số đo và test phù hợp.

## 13. Câu hỏi tự kiểm tra

1. **Interface và Strategy khác nhau thế nào?** — Interface quy định hợp đồng; Strategy là cách tổ chức nhiều thuật toán theo hợp đồng và chọn implementation runtime.
2. **ServiceLoader có thay thế hoàn toàn Reflection được không?** — Không. Nó phát hiện provider của service contract, không tự phân tích cấu trúc field/method của class bất kỳ.
3. **Có cần `Method.invoke` sau khi đã tạo được `Transform` bằng `Class.forName`?** — Không. Gọi `transform.apply(...)` qua interface.
4. **Một provider ở classpath nhưng không có file `META-INF/services/<service binary name>` có tự được load không?** — Không theo cơ chế cấu hình classpath của ServiceLoader.
5. **Code generation build time có tự nạp plugin class chỉ tồn tại sau khi đã đóng gói bản build không?** — Không. Nó phải biết schema/type ở build time, hoặc cần cơ chế khác ở runtime.
6. **Nếu `ServiceLoader` provider bị lỗi cấu hình, có chắc `catch (Exception)` bắt được không?** — Không. `ServiceConfigurationError` là `Error`.
7. **Nếu dùng Reflection để đọc metadata ở startup rồi cache adapter, lời gọi hot path phải luôn qua Reflection?** — Không. Có thể chuyển sang interface, generated adapter hoặc cấu trúc đã phân tích.
8. **Vì sao benchmark quan trọng?** — Chi phí load provider, build cache, Reflection invoke và generated adapter khác nhau theo tình huống/JDK; không thể suy hiệu năng chỉ từ tên kỹ thuật.

## 14. Ghi nhớ

- **Interface** là hợp đồng compile time; **Strategy** chọn thuật toán theo hợp đồng đó ở runtime.
- **ServiceLoader** thực hiện discovery các **provider được đăng ký**, không tự scan mọi class.
- **Reflection** cho introspection và invocation linh hoạt khi cấu trúc chưa biết trước, đổi lại phải quản lý runtime name/signature, access, loader và exception.
- **Code generation** tạo mã/metadata chuyên biệt ở build time hoặc runtime; đánh đổi tính linh hoạt, thời gian build, chi phí startup và khả năng debug.
- **Kết hợp có kiểm soát** thường là phương án tốt: giao tiếp qua interface, dùng Strategy cho lựa chọn, ServiceLoader cho extension, Reflection cho khám phá metadata và codegen cho đoạn đã ổn định, có lợi ích đã đo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-009">Không dùng reflection chỉ để tránh thiết kế API rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng reflection chỉ để tránh thiết kế API rõ ràng

## 1. Vấn đề thực sự: có thể gọi động không có nghĩa là nên gọi động

Trong Java, Reflection cho phép chương trình đọc thông tin về class, constructor, field, method và thực hiện một số thao tác lúc runtime. Ví dụ:

```java
Method method = target.getClass().getMethod(
        methodName, String.class, String.class);
method.invoke(target, recipient, message);
```

Đoạn mã này là **mảnh minh họa**, giả định đã có `target`, `methodName`, `recipient`, `message` và import `java.lang.reflect.Method`; caller phải xử lý ngoại lệ Reflection. Nó có thể hữu ích khi method thực sự chỉ được xác định vào runtime. Nhưng nếu hệ thống vốn biết trước hai nghiệp vụ `sendEmail(...)` và `sendPush(...)`, việc nhận chuỗi tên method để chọn một trong hai thường là dấu hiệu **chưa định nghĩa hợp đồng API rõ ràng**.

Reflection là **cơ chế kỹ thuật để thao tác với thành viên đã được mô tả bằng metadata**. Nó không thay thế những quyết định thiết kế:

- Người gọi được phép thực hiện hành động nào?
- Mỗi hành động nhận tham số gì, trả kết quả gì, ném lỗi gì?
- Thành phần nào chịu trách nhiệm kiểm tra dữ liệu và quy tắc nghiệp vụ?
- Chọn triển khai bằng cách nào, tại thời điểm biên dịch hay runtime?
- Khi đổi tên method hoặc thay đổi chữ ký, ai cần biết và hệ thống được kiểm tra ra sao?

Nếu câu trả lời là “ghép chuỗi tên method rồi thử invoke”, các thông tin hợp đồng ấy đang bị đẩy từ mã nguồn và compiler sang runtime.

**Nguyên tắc:** Khi các loại thao tác và đối tượng tương tác đã biết lúc phát triển, hãy ưu tiên API có tên, kiểu dữ liệu và trách nhiệm cụ thể. Dùng Reflection khi nhu cầu thật sự là khám phá/ghép nối thành viên động và đã xác định rõ ranh giới của tính động ấy.

## 2. Vì sao reflection có thể làm API khó hiểu hơn?

### a) Mất kiểm tra kiểu ngay tại nơi gọi

Ví dụ gọi trực tiếp:

```java
notificationService.send(Channel.EMAIL, "lan@example.com", "Hello");
```

Compiler biết tên method `send`, kiểu `Channel`, hai tham số `String` và kiểu trả về. Nếu đổi chữ ký hoặc gọi sai kiểu, nhiều lỗi sẽ xuất hiện ngay khi biên dịch.

Với một hệ thống tự ghép lời gọi:

```java
dispatcher.invoke("sendEmail", "lan@example.com", "Hello");
```

Compiler chỉ thấy `invoke(String, Object...)` hay một chữ ký chung tương tự, không tự kiểm tra sự tồn tại của nghiệp vụ `sendEmail` trên đích thật. Lỗi tên và overload có thể chỉ lộ ra khi đường chạy ấy thực sự được thực hiện.

**Lưu ý:** Reflection API vẫn có kiểm tra runtime; không phải “không kiểm tra kiểu”. Điểm khác biệt là phần lớn sai sót về tên/signature bị phát hiện **muộn hơn** và trở thành trách nhiệm của người viết dispatcher/framework.

### b) Hợp đồng bị thay bằng chuỗi và quy ước ngầm

Một chuỗi như `"sendEmail"` không tự nói lên tham số, kiểu kết quả, quyền gọi, validation hay ý nghĩa nghiệp vụ. Nếu ứng dụng quyết định tên method từ request của client, còn có nguy cơ tạo tập hành động truy cập rộng hơn dự kiến.

Một API tốt thể hiện lựa chọn trong kiểu dữ liệu, chẳng hạn `enum Channel { EMAIL, PUSH }`, và gom quá trình dispatch trong một service có trách nhiệm cụ thể.

### c) Khó bảo trì khi refactor

IDE và compiler có thể hỗ trợ đổi tên method trực tiếp, tìm caller hoặc kiểm tra thay đổi signature. Tên method nằm trong chuỗi runtime có thể không được cập nhật hoặc kiểm tra theo cùng mức độ. Unit test chỉ bao phủ được các nhánh đã chạy; các nhánh reflective bị bỏ sót vẫn có nguy cơ lỗi.

### d) Sai loại thành viên, quyền và classloader

Các API như `getMethod` và `getDeclaredMethod` không giống nhau. `getMethod` tra cứu public method theo quy tắc của nó; `getDeclaredMethod` tìm method khai báo trực tiếp trong class, kể cả non-public, nhưng việc **tìm thấy** không bảo đảm **gọi được**. Overload yêu cầu danh sách kiểu tham số chính xác; primitive `int.class` khác `Integer.class`. Java module, visibility, receiver và classloader còn bổ sung các điều kiện truy cập.

Nếu chọn Reflection chỉ để gọi hai thao tác đã biết, ứng dụng tự nhận thêm nhiều chi phí chẩn đoán này mà không có lợi ích tương xứng.

### e) Khó phân định ngoại lệ và lỗi nghiệp vụ

`Method.invoke(...)` có thể ném `NoSuchMethodException` ở bước tìm (qua `getMethod`), `IllegalAccessException`, `IllegalArgumentException` hoặc `InvocationTargetException` khi gọi. Nếu method đích ném exception, `InvocationTargetException` bọc lỗi gốc và phải phân tích `getCause()`. API kiểu tường minh thường biểu diễn lỗi nghiệp vụ gần với nơi phát sinh hơn và giữ stack trace đơn giản hơn.

Đừng vội gộp mọi trường hợp thành `catch (Exception) { return null; }`: làm vậy che khuất cấu hình sai, lỗi lập trình và lỗi domain.

## 3. Phản ví dụ: dispatcher gọi method theo tên

Giả sử hệ thống gửi thông báo có hai kênh cố định. Một thiết kế dễ mắc lỗi là truyền tên method tới helper dùng Reflection:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class StringMethodDispatcher {
    static void dispatch(
            Object target,
            String methodName,
            String recipient,
            String message)
            throws NoSuchMethodException,
                   IllegalAccessException,
                   InvocationTargetException {
        Method method = target.getClass().getMethod(
                methodName, String.class, String.class);
        method.invoke(target, recipient, message);
    }
}
```

Đây là một class **biên dịch được như thành phần thư viện**, không có `main` nên không phải chương trình chạy độc lập. Để thành công, class đích phải cung cấp public method phù hợp và các điều kiện truy cập runtime phải cho phép gọi.

Một lời gọi kiểu này thường tồn tại trong ứng dụng:

```java
StringMethodDispatcher.dispatch(
        someService, "sendEmail", "lan@example.com", "Hello");
```

Đoạn gọi là minh họa trong method đã có biến `someService`. Cả tên `"sendEmail"` và kiểu `Object` không giúp compiler ràng buộc API gửi thông báo cụ thể. Đổi tên method đích nhưng quên chuỗi khiến `NoSuchMethodException` xảy ra lúc chạy.

Những câu hỏi cần đặt ra trước khi xây dispatcher:

1. Tập hành động có thật sự thay đổi runtime, hay chỉ có hai hoặc ba nhánh đã biết?
2. Vì sao caller được cấp quyền chọn **tên member Java**, thay vì chọn một hành động nghiệp vụ đã công bố?
3. Nếu thêm action mới, có thể cập nhật một interface/enum/registry được kiểm soát không?
4. Khi một action không hợp lệ, muốn lỗi ở compile time, validation time hay tới khi Reflection lookup?
5. Có cần tách quyền truy cập member của Java khỏi quyền thực hiện nghiệp vụ của client không?

Trong trường hợp này, ưu tiên thiết kế API kiểu tường minh sẽ dễ kiểm tra hơn.

## 4. Thiết kế lại: một contract, nhiều implementation

Giả sử Email và Push đều thực hiện cùng một thao tác nghiệp vụ là “gửi thông báo”. Ta có thể biểu diễn điều ấy bằng `interface Sender`, dùng `enum Channel` làm tập lựa chọn công khai và `NotificationService` để phối hợp.

| Thành phần | Trách nhiệm | Lợi ích thiết kế |
| --- | --- | --- |
| `Sender` | Khai báo `send(recipient, body)` | Chữ ký và ý nghĩa chung rõ ràng |
| `EmailSender` / `PushSender` | Thực hiện hành vi cho từng kênh | Thay thế/mock/test độc lập |
| `Channel` | Xác định các lựa chọn hợp lệ | Caller không biết tên method nội bộ |
| `NotificationService` | Chọn sender rồi gọi interface | Kiểm soát điều phối và lỗi unsupported ở một nơi |
| `Map<Channel, Sender>` | Registry của các implementation đã được chọn | Mở rộng nhánh cụ thể mà không tạo dispatcher Reflection |

Điểm quan trọng ở đây là **đa hình (polymorphism)**: sau khi đã chọn một `Sender`, code gọi `sender.send(...)` trực tiếp theo contract. Chuyển từ `EmailSender` sang `PushSender` là thay implementation, không biến tên method thành chuỗi.

### Ví dụ Java 17+ chạy được

Lưu file **`ClearApiDemo.java`**:

```java
import java.util.Map;

public class ClearApiDemo {
    enum Channel { EMAIL, PUSH }

    interface Sender {
        void send(String recipient, String body);
    }

    static final class EmailSender implements Sender {
        @Override
        public void send(String recipient, String body) {
            System.out.println("EMAIL|" + recipient + "|" + body);
        }
    }

    static final class PushSender implements Sender {
        @Override
        public void send(String recipient, String body) {
            System.out.println("PUSH|" + recipient + "|" + body);
        }
    }

    static final class NotificationService {
        private final Map<Channel, Sender> senders;

        NotificationService(Map<Channel, Sender> senders) {
            this.senders = Map.copyOf(senders);
        }

        void send(Channel channel, String recipient, String body) {
            Sender sender = senders.get(channel);
            if (sender == null) {
                throw new IllegalArgumentException(
                        "Unsupported channel: " + channel);
            }
            sender.send(recipient, body);
        }
    }

    public static void main(String[] args) {
        NotificationService service = new NotificationService(Map.of(
                Channel.EMAIL, new EmailSender(),
                Channel.PUSH, new PushSender()
        ));

        service.send(Channel.EMAIL, "lan@example.com", "Hello");
        service.send(Channel.PUSH, "device-42", "Update");
    }
}
```

Biên dịch, chạy:

```bash
javac -encoding UTF-8 ClearApiDemo.java
java ClearApiDemo
```

Kết quả chính xác:

```text
EMAIL|lan@example.com|Hello
PUSH|device-42|Update
```

Giải thích:

1. Caller chỉ biết nghiệp vụ `send(Channel, String, String)`, không cần biết các tên method nội bộ hoặc cấu trúc class implementation.
2. `Map.of(...)` thiết lập registry hai kênh. `Map.copyOf(...)` tạo bản map không thể sửa qua service; điều đó **không** biến bản thân các sender thành immutable hay thread-safe.
3. `NotificationService.send(...)` chọn implementation theo `Channel` rồi gọi `Sender.send(...)` với chữ ký tường minh. Không có `Class.getMethod` hay `Method.invoke`.
4. Nếu sau này thêm kênh SMS, có thể thêm `Channel.SMS`, triển khai `SmsSender implements Sender` và đăng ký. Compiler kiểm tra `SmsSender` thực hiện đủ method của interface.
5. Việc `Map` thiếu một kênh được xử lý tại service bằng `IllegalArgumentException`. Thiết kế thực tế có thể thêm validation recipient/body, audit, retry hoặc domain error tùy nhu cầu, nằm tại các ranh giới rõ ràng.

Với ứng dụng rất nhỏ có vài nhánh ổn định, `switch` trên enum cũng là lựa chọn hợp lệ. Không cần dựng nhiều class hoặc registry chỉ để tránh một biểu thức `switch` đơn giản. Mục tiêu là API **đúng mức trừu tượng**, không phải tạo thêm abstraction cho đẹp.

## 5. Chọn công cụ thay thế theo hợp đồng API

Điểm xuất phát là **dữ liệu và thao tác nào cần được công bố cho caller**, không phải cố loại bỏ mọi dòng Reflection. Trong ví dụ gửi thông báo, caller biết hành động `send` và kiểu `Channel`; việc chọn `EmailSender` hay `PushSender` thuộc trách nhiệm của `NotificationService`. Khi yêu cầu khác đi, lựa chọn cũng khác:

| Nhu cầu thật sự | Cơ chế phù hợp | Hợp đồng giữ được |
| --- | --- | --- |
| Nhiều implementation làm cùng một nghiệp vụ | Interface và đa hình; Strategy nếu chọn theo ngữ cảnh | `Sender.send(String, String)` được compiler kiểm tra |
| Cùng một hành vi nhận vài dạng dữ liệu đã biết khi biên dịch | Overload, generic hoặc phương thức có tên riêng | Caller được kiểm tra chữ ký; tránh `Object...` vô nghĩa |
| Chọn và tạo một implementation từ cấu hình có kiểm soát | Factory, DI hoặc `switch` với registry nhỏ | Caller nhận kiểu `Sender`, không cần biết constructor nội bộ |
| Thêm provider có cùng contract sau khi ứng dụng được đóng gói | Registry/plugin mechanism, có thể dùng `ServiceLoader<Sender>` | Plugin vẫn phải đáp ứng interface và chính sách đăng ký |
| Công bố dữ liệu qua ranh giới module/dịch vụ | DTO, `record`, getter hoặc mapper tường minh | Chỉ lộ trường dữ liệu nằm trong contract, không mở private state |

Ví dụ, method `void write(String text)` và `void write(byte[] bytes)` có thể là hai overload hợp lệ; nếu mỗi nhánh đại diện một hành vi nghiệp vụ hoàn toàn khác, đặt tên cụ thể hơn thay vì ép thành một `execute(Object)`. Với dữ liệu trao đổi, `record UserSummary(String displayName, int points) {}` thể hiện schema rõ hơn việc tự phản chiếu mọi field private của domain object.

Một registry có khóa chuỗi vẫn cần kiểm tra key, quyền và trùng lặp đăng ký. `ServiceLoader` chỉ tìm **provider đã khai báo** trong classpath/module có thể nhìn thấy; nó không tự quét mọi class. Registry/factory cũng không được biến mọi implementation thành một "mega-interface" với hàng chục thao tác không liên quan. Với hai nhánh ổn định, `switch` có thể rõ ràng hơn một plugin framework.

## 6. Khi nào Reflection vẫn có lý do chính đáng?

Reflection phù hợp khi **cấu trúc của type hoặc member thực sự chỉ biết lúc chạy**: serializer/ORM đọc metadata của model bất kỳ; DI/framework phân tích constructor và annotation; công cụ test, debugger hoặc inspector quan sát lớp không biết trước; plugin loader xử lý metadata class mới; adapter tương thích API bên thứ ba có khác biệt giữa các phiên bản.

Ở các trường hợp này, hãy **cô lập bước phản chiếu tại boundary thích hợp**. Chẳng hạn plugin loader có thể dùng Reflection để xác minh class và tạo instance, nhưng sau khi nhận được `Sender` thì phần nghiệp vụ gọi trực tiếp `sender.send(...)`. Các thư viện thao tác private member cần giới hạn quyền truy cập và JPMS `opens`; dispatcher chọn hành động từ request cần allowlist, authorization và schema validation. Thao tác Reflection nào lặp lại nhiều có thể cache metadata đã kiểm tra, nhưng không được cache một quyết định cấp quyền chung cho mọi người dùng.

Nếu class/method đã biết tại compile time mà code vẫn tự tìm chúng bằng tên chuỗi, hãy xem lại hợp đồng trước. Ngược lại, nếu bài toán thật sự là khám phá field/method/annotation của những kiểu chưa biết, một API typed duy nhất không thay được khả năng introspection đó.

## 7. Dùng MethodHandle không tự sửa được thiết kế API mơ hồ

`java.lang.invoke.MethodHandle` và `VarHandle` có những điểm mạnh: thao tác có mô tả kiểu rõ, linh hoạt khi liên kết runtime, hỗ trợ một số tối ưu và memory access khác với Reflection truyền thống.

Tuy nhiên nếu ứng dụng có đầy đủ thông tin để viết:

```java
userService.activate(userId);
```

mà lại xây đường đi:

```text
"activate" -> tìm MethodHandle theo tên -> invoke -> xử lý mọi lỗi kiểu runtime
```

thì việc đổi công cụ từ `Method.invoke` sang `MethodHandle.invoke` không giải quyết vấn đề hợp đồng thiết kế. Cùng một nghiệp vụ vẫn bị chọn bằng chuỗi; lỗi thiếu method vẫn chuyển sang runtime và caller vẫn khó biết phạm vi hành động hợp lệ.

Chọn cơ chế invoke bằng lý do cụ thể: framework/VM integration, call site thực sự động, hoặc dữ liệu benchmark. Đừng chọn Reflection/MethodHandle chỉ để né việc tạo interface, method overload hay adapter cần thiết.

## 8. API rõ ràng giúp bảo vệ ranh giới module và quyền truy cập

Một module tốt thường công bố những gì bên ngoài **được phép làm**, thay vì để caller truy cập tất cả chi tiết triển khai. Ví dụ, `AccountService.closeAccount(accountId)` thể hiện điều kiện nghiệp vụ; việc truy cập field private `status` bằng Reflection rồi tự đổi thành `CLOSED` có thể bỏ qua validation, audit, event hoặc transaction.

Một ranh giới phù hợp:

```text
HTTP / UI / CLI
     |
     v
AccountService.closeAccount(accountId)
     |
     +-- kiểm tra quyền và trạng thái
     +-- thực thi nghiệp vụ/transaction
     +-- ghi audit / phát sự kiện nếu cần
     |
     v
Domain / Persistence
```

Nếu caller dùng `Field.setAccessible(true)` để đổi trạng thái, nó có thể phá vỡ invariants. Với Java module system, `exports` công bố package để truy cập public API, còn `opens` cho phép mức truy cập reflective theo các điều kiện liên quan. Không mở toàn bộ package chỉ vì muốn dùng một field private từ client, trừ khi framework thực sự cần và phạm vi mở đã được cân nhắc.

Một API tường minh còn tạo nơi rõ ràng để áp dụng authorization. Đừng cho chuỗi do người dùng gửi thẳng vào tên method Reflection trên service nội bộ: ngay cả khi một số method được gọi thành công, đó không phải cơ chế kiểm soát hành động nghiệp vụ được thiết kế nghiêm túc.

## 9. Di chuyển từ Reflection dispatcher sang API rõ ràng

Nếu code hiện có đang dựa vào `invoke(name, args)`, có thể chuyển dần mà vẫn giữ behavior đang dùng:

1. **Liệt kê tất cả action thực tế** từ caller/config và test. Ghi rõ tên nghiệp vụ, kiểu input/output, lỗi và permission. Phân biệt action tĩnh với điểm mở rộng thật sự động.
2. **Nhóm các action theo contract**. Nếu nhiều implementation làm cùng việc, tạo interface. Nếu chỉ một số thao tác trên cùng service, thêm method public chuyên trách hoặc overload phù hợp. Không ép nhiều nghiệp vụ khác nhau vào một generic method.
3. **Chọn điểm dispatch typed**. Caller truyền enum hoặc một value object được validate; service dùng `switch`/factory/registry để chọn implementation.
4. **Đưa validation và lỗi về đúng ranh giới**. Kiểm tra dữ liệu đầu vào, unknown action, unsupported version, permission trước khi gọi service. Giữ logic nghiệp vụ ở nơi dễ test.
5. **Thêm test hành vi trước khi thay đường gọi**. Dùng case hợp lệ, sai action, dữ liệu thiếu, exception từ implementation và quyền truy cập; so sánh output và tác dụng phụ.
6. **Thay caller dần**. Nếu phải giữ API chuỗi cho backward compatibility, tạo một adapter nhỏ để parse key thành enum rồi chuyển sang service typed; không lan truyền tên Java method ra các lớp khác.
7. **Xóa dispatcher dư thừa sau khi không còn caller**. Không để song song hai đường thực thi mà một đường bỏ qua validation/audit.

Ví dụ adapter chuyển đổi ở ranh giới, thay vì gọi member Java theo chuỗi:

```java
Channel parseChannel(String text) {
    return switch (text) {
        case "email" -> Channel.EMAIL;
        case "push" -> Channel.PUSH;
        default -> throw new IllegalArgumentException("Unknown channel: " + text);
    };
}
```

Đây là **method minh họa** trong class đã khai báo enum `Channel`, không phải file chạy độc lập. Với `text == null`, `switch` sẽ ném `NullPointerException` trong dạng này; API production nên xác định riêng cách xử lý input null. Ý nghĩa của đoạn code là: parse/validate chuỗi bên ngoài thành kiểu domain một lần; từ đó trở vào, caller dùng kiểu tường minh.

## 10. So sánh hai lựa chọn ở những điểm quan trọng

| Tiêu chí | Gọi member theo chuỗi bằng Reflection | API typed + interface/factory |
| --- | --- | --- |
| Kiểu và chữ ký | Tra cứu/kiểm tra chủ yếu runtime | Compiler kiểm tra phần lớn tại call site |
| Đổi tên method | Chuỗi có thể bị bỏ sót khi refactor | Công cụ đổi tên và compiler hỗ trợ tốt |
| Quyền truy cập | Cần giải quyết access/member/module riêng | Public contract xác định rõ ranh giới sử dụng |
| Lỗi | Có lỗi lookup/invoke, `InvocationTargetException` | Lỗi nghiệp vụ thể hiện trực tiếp hơn |
| Khả năng test | Cần bao phủ từng tên/signature chuỗi | Test theo contract và implementation |
| Tính động | Truy cập thành viên runtime linh hoạt | Chọn implementation runtime qua factory/DI/registry vẫn được |
| Hiệu năng | Có chi phí lookup/invoke; cache có thể giảm lookup | Thường dễ tối ưu và đo hơn, nhưng cần benchmark cho từng bài toán |
| Trường hợp hợp lý | Framework/tooling khám phá metadata thật sự động | Logic nghiệp vụ và tích hợp có contract đã biết |

Bảng là gợi ý lựa chọn kiến trúc, **không** cam kết mọi đường code không dùng Reflection đều nhanh hơn mọi đường có Reflection. JIT, cách gọi, workload và thiết kế cache đều có thể ảnh hưởng. Điều quan trọng là chi phí và tính rõ ràng của hệ thống phải phù hợp nhu cầu.

## 11. Dấu hiệu nên xem lại thiết kế

Nếu gặp các mẫu sau, hãy kiểm tra liệu API tường minh có phù hợp hơn:

- Một service nhận `String methodName`/`Object[] args` chỉ để chọn trong vài method cố định.
- Dùng `getDeclaredField` + `setAccessible(true)` để đọc/ghi state mà module đáng lẽ cung cấp method/DTO rõ ràng.
- Dùng Reflection để khởi tạo những class được biết lúc compile time thay vì constructor/factory/DI.
- Định nghĩa các action kinh doanh bằng tên method Java trong database/API request rồi tự tạo quyền gọi.
- Sau mỗi lần đổi tên method, phải tìm nhiều chuỗi hard-code trên toàn hệ thống.
- Phần lớn code xử lý `ReflectiveOperationException`, cast runtime và kiểm tra signature thay vì xử lý nghiệp vụ.
- Thêm một chức năng buộc phải dán nhiều chuỗi tên Java vào config, dispatcher và UI dù tập chức năng được quản lý tập trung.

Không phải mọi trường hợp xuất hiện một dòng Reflection đều là lỗi thiết kế. Đánh giá dựa trên **phạm vi tính động có thật** và liệu abstraction mới có giảm gánh nặng người đọc lẫn bảo trì hay không.

## 12. Lỗi thường gặp khi refactor

**Thay Reflection bằng một “mega-interface”.** Nếu mọi implementation phải triển khai hàng chục method không liên quan, hợp đồng quá lớn. Hãy tách interface theo khả năng (interface segregation).

**Dùng enum cho plugin không giới hạn.** `enum` thích hợp với tập lựa chọn đóng. Nếu cần provider bên thứ ba được cài thêm runtime, dùng registry/ServiceLoader/extension point typed. Đừng bắt sửa mã lõi chỉ để thêm mỗi plugin khi yêu cầu thực sự là mở rộng động.

**Dùng Object ở mọi API để khỏi Reflection.** `Object execute(Object input)` không tự tốt hơn `Method.invoke` nếu caller vẫn phải đoán kiểu/cast theo quy ước không được công bố. Ưu tiên request/response types rõ ràng; dùng generic hoặc sealed hierarchy khi phù hợp.

**Đưa validation vào controller mà bỏ qua service.** Đổi từ reflective call sang direct call không bảo đảm authorization/invariants. Service phải bảo vệ ranh giới nghiệp vụ phù hợp, kể cả khi có caller ngoài HTTP.

**Không tách adapter legacy.** Nếu bắt buộc nhận tên cũ qua JSON/config, hãy parse và validate tại boundary. Giữ một adapter có kiểm soát rồi cho phần lõi hoạt động trên contract typed.

**Tự tạo registry phức tạp khi chỉ có một nhánh.** API tốt nhất đôi khi chỉ là một method đặt tên rõ ràng. Đánh đổi abstraction lấy tính dễ hiểu, đừng dựng plugin framework để thay thế một lời gọi bình thường.

## 13. Tự kiểm tra và bài tập

1. Hệ thống có ba công thức thuế cố định, chọn theo mã quốc gia. Bạn sẽ chọn `switch`, `Strategy` hay Reflection? Giải thích dựa trên mức độ thay đổi và nhu cầu mở rộng.
2. Một plugin được nạp sau khi ứng dụng khởi động, nhưng plugin phải tuân thủ `ReportExporter.export(Report)`. Vì sao registry/ServiceLoader với contract typed vẫn hỗ trợ tính động?
3. Chỉ ra ba lỗi mà compiler có thể bắt khi gọi `service.send(Channel.EMAIL, String, String)`, nhưng khó bắt sớm với `invoke("sendEmail", Object...)`.
4. Tại sao `MethodHandle` không tự giải quyết vấn đề nếu tên method vẫn lấy từ request của người dùng?
5. Một framework serializer cần truy cập nhiều field của những class không biết trước lúc biên dịch. Đây có phải lý do hợp lý để dùng Reflection? Cần giới hạn quyền/kiểm tra dữ liệu như thế nào?
6. Nếu chuyển hệ thống từ `getDeclaredMethod` sang interface, cần test những gì để bảo toàn hành vi, lỗi, permission và tính tương thích?
7. Từ ví dụ `ClearApiDemo`, thêm một implementation SMS. Bạn muốn thêm enum value hay tạo plugin registry? Điều kiện nào khiến lựa chọn thay đổi?

**Kết quả cần đạt:** nhận ra sự khác nhau giữa nhu cầu **liên kết động thật sự** và việc dùng Reflection để né thiết kế contract; biết ưu tiên interface, overload, factory, DTO, registry hoặc ServiceLoader phù hợp; đồng thời giữ Reflection trong những thành phần thật sự cần khám phá metadata runtime, với ranh giới quyền truy cập và lỗi rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="limitations-alternatives-010">Đối chiếu hành vi khi dùng runtime hạn chế reflection hoặc biên dịch trước</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu hành vi Reflection trên JVM tiêu chuẩn, runtime bị hạn chế và môi trường AOT

## 1. Mục tiêu và phạm vi

Java Reflection cho phép tìm lớp theo tên, đọc annotation, tìm constructor/method/field và gọi chúng ở **thời điểm chạy**. Trên JVM thông thường, thao tác này thường dựa vào các class file có sẵn trên classpath hoặc module path. Nhưng cùng một chương trình có thể hoạt động khác khi:

- chạy trong **named module** của JPMS, chịu quy tắc `exports`, `opens` và quyền truy cập thành viên;
- chạy trong **GraalVM Native Image**, nơi phần lớn cấu trúc ứng dụng được phân tích và đưa vào binary trước khi chạy;
- dùng **Spring AOT** hoặc framework tối ưu trước các quyết định vốn được suy ra bằng quét classpath/Reflection;
- chạy trong một sandbox, runtime tùy biến hoặc nền tảng có quy tắc riêng với nạp lớp, tạo bytecode và quyền Reflection.

Bài học giúp đối chiếu **nguyên nhân, triệu chứng, cách kiểm tra và lựa chọn thay thế**. Một nguyên tắc quan trọng: **“biên dịch trước” không tự động có nghĩa “không hỗ trợ Reflection”**. Phải xác định chính xác công nghệ, phiên bản, chính sách module và loại thao tác động đang dùng.

## 2. Hai câu hỏi độc lập cần hỏi trước khi gọi Reflection

Mọi lời gọi Reflection phụ thuộc ít nhất vào hai yếu tố:

1. **Có gì hiện diện ở runtime?** Class, constructor, method, field, annotation metadata, resource hay proxy class có tồn tại/được giữ lại trong môi trường chạy không? Đây là vấn đề *reachability* hoặc *availability*.
2. **Ai được phép truy cập?** Lookup/caller có quyền đọc module, dùng thành viên public, hay thực hiện *deep reflection* vào thành viên private không? Đây là vấn đề *accessibility*.

Trên HotSpot JVM, có thể có `Class.forName("demo.Plugin")` thành công nhưng `getDeclaredConstructor().newInstance()` thất bại vì constructor không accessible. Trên Native Image, chính `Class.forName(...)` hoặc truy vấn constructor có thể thiếu metadata cần thiết **ngay cả khi source Java và JAR build đều chứa class ấy**.

**Đăng ký reachability metadata không phải cơ chế vượt rào `private` của JPMS.** Ngược lại, mở package bằng `--add-opens` không tự làm Native Image giữ mọi thành viên được gọi động. Hai nhóm điều kiện phải được xét riêng.

## 3. Bảng đối chiếu các môi trường phổ biến

| Môi trường/cấu hình | Khám phá lớp/thành viên ở runtime | Ràng buộc chính | Hướng xử lý |
| --- | --- | --- | --- |
| JVM thông thường, classpath | Có thể nạp class còn sẵn trong đường dẫn và tra cứu bằng Reflection | Classpath, class loader, Java language access | Kiểm tra classloader, tên binary, visibility |
| JVM với JPMS named modules | Có Reflection nhưng phụ thuộc module graph | `reads`, `exports`/`opens` và quyền member | API public, `opens` có chọn lọc, `MethodHandles.Lookup` hợp lệ |
| JVM với `jlink` custom runtime | Reflection vẫn hoạt động cho các lớp được đưa vào | Không có module cần thiết trong runtime image; vẫn có JPMS | Bao gồm module cần thiết lúc tạo runtime |
| JVM với CDS/AppCDS | Reflection vẫn theo quy tắc JVM/JPMS | Archive chủ yếu tối ưu tải và chia sẻ metadata | Không tự suy ra bị hạn chế Reflection |
| GraalVM Native Image | Hỗ trợ Reflection trên các thành phần được phân tích/đăng ký | Phân tích *closed world*, reachability metadata, ranh giới runtime native | Đăng ký đúng class/member hoặc tái cấu trúc static |
| Spring AOT / native | Tạo cấu hình và mã cần thiết ở build time | Quyết định bean/profile/scan thường đã cố định trước khi chạy | `RuntimeHints`, AOT processor, static bean registration |
| Runtime sandbox/tùy biến | Tùy nền tảng/chính sách cụ thể | Có thể hạn chế nạp bytecode, dynamic proxies, quyền truy cập | Đọc contract nền tảng, viết integration test đúng đích |

`javac` dịch `.java` thành `.class` **không làm Reflection biến mất**. `jlink` dựng một runtime gồm các module được chọn; CDS lưu trữ một phần trạng thái lớp để khởi động nhanh hơn. Cả hai khác với *closed-world native compilation*.

## 4. Vì sao Native Image cần biết Reflection từ trước?

GraalVM Native Image phân tích ứng dụng tại **thời điểm build** để xác định thành phần có thể được sử dụng và đưa thành phần cần thiết vào executable. Một lệnh trực tiếp như:

~~~java
new java.util.ArrayList<String>();
~~~

cho phép công cụ nhìn thấy loại cần dùng. Một lệnh động lại khó dự đoán hơn:

~~~java
String className = args[0];
Class<?> type = Class.forName(className);
Object value = type.getDeclaredConstructor().newInstance();
~~~

Khi build, `args[0]` có thể chưa được biết. Công cụ không thể mặc nhiên giữ **mọi class và constructor** của ứng dụng cùng mọi dependency; làm vậy vừa tốn tài nguyên vừa trái mục tiêu tối ưu kích thước.

Đối với các lời gọi Reflection có đối số **hằng hoặc có thể suy luận**, Native Image có thể tính được phần tử cần thiết lúc build. Những phần không suy ra được cần **reachability metadata**. GraalVM hiện đại có thể cho phép cung cấp metadata qua file `reachability-metadata.json`, qua phân tích mã, cấu hình thư viện, tracing agent hoặc API hỗ trợ.

**Không nên khẳng định “hễ dùng `Class.forName` thì Native Image sẽ lỗi”.** Với tên hằng, hoặc khi framework đã cung cấp metadata, lời gọi có thể chạy tốt. Ngược lại, không nên dựa vào kết quả thành công của **một input** để kết luận các tên lớp động khác cũng được đóng gói đầy đủ.

## 5. Ví dụ Java 17 chạy trên JVM: chọn implementation bằng tên lớp

Tạo file `ReflectionModesDemo.java`:

~~~java
public class ReflectionModesDemo {
    public interface Task {
        String run();
    }

    public static class Alpha implements Task {
        public Alpha() {}

        @Override
        public String run() {
            return "alpha";
        }
    }

    public static class Beta implements Task {
        public Beta() {}

        @Override
        public String run() {
            return "beta";
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Pass binary class name");
        }

        Class<? extends Task> type =
                Class.forName(args[0]).asSubclass(Task.class);
        Task task = type.getDeclaredConstructor().newInstance();

        System.out.println("type=" + type.getName());
        System.out.println("result=" + task.run());
    }
}
~~~

Đây là chương trình **độc lập**, gồm `Task` và hai implementation là member class bên trong `ReflectionModesDemo`. Tên nhị phân của member class dùng dấu `$`, không dùng tên canonical dấu chấm.

Trên hệ thống có JDK 17+, chạy trong thư mục chứa source:

~~~bash
javac --release 17 ReflectionModesDemo.java
java ReflectionModesDemo 'ReflectionModesDemo$Alpha'
java ReflectionModesDemo 'ReflectionModesDemo$Beta'
~~~

Với **PowerShell**, dùng dấu nháy đơn cho đối số có `$` như ví dụ để tránh việc shell mở rộng tên biến.

Kết quả lệnh gọi Alpha:

~~~text
type=ReflectionModesDemo$Alpha
result=alpha
~~~

Kết quả lệnh gọi Beta:

~~~text
type=ReflectionModesDemo$Beta
result=beta
~~~

**Đã kiểm chứng:** chương trình trên được biên dịch bằng `javac --release 17` và chạy hai trường hợp trên OpenJDK 21, kết quả khớp chính xác. `Class.forName` tìm lớp, `asSubclass(Task.class)` kiểm tra contract, `getDeclaredConstructor().newInstance()` tạo đối tượng, và lời gọi cuối cùng là **dispatch interface thông thường**. Kiểm tra bằng `asSubclass` không thể thay thế quyền truy cập constructor hoặc cơ chế đăng ký member của Native Image.

## 6. Xây dựng Native Image: cần metadata gì?

Chương trình ví dụ nhận tên class từ tham số runtime, nên khi biên dịch native cần bảo đảm hai kiểu `Alpha` và `Beta` cùng các constructor mà Reflection gọi được ghi nhận.

Với **định dạng metadata hiện đại** của GraalVM, tạo file:

`out/META-INF/native-image/demo/reflection/reachability-metadata.json`

~~~json
{
  "reflection": [
    {
      "type": "ReflectionModesDemo$Alpha",
      "allDeclaredConstructors": true
    },
    {
      "type": "ReflectionModesDemo$Beta",
      "allDeclaredConstructors": true
    }
  ]
}
~~~

`allDeclaredConstructors` cấp khả năng tìm và gọi các constructor được khai báo của class tương ứng trong native binary. Nó **không nâng quyền Java** cho constructor private. Trong chương trình, cả hai constructor đều public và không cần `setAccessible(true)`. Gói metadata này không cần khai báo reflective invocation cho `run()` vì lời gọi `task.run()` đi qua interface được compiler biết trước.

Metadata trên khá rộng đối với constructor; nếu dự án lớn, nên chọn chính xác member thực sự cần dùng theo schema và công cụ của phiên bản GraalVM đang chạy. Tên class trong JSON phải là **binary name đầy đủ**: member class dùng `$`; class có package thì thêm tiền tố package.

Để build demo, cần một **GraalVM có công cụ `native-image`**, toolchain native phù hợp hệ điều hành và Java version tương thích. Trong thư mục chứa source, có thể làm:

~~~bash
mkdir -p out/META-INF/native-image/demo/reflection
javac --release 17 -d out ReflectionModesDemo.java
# Ghi JSON bên trên vào:
# out/META-INF/native-image/demo/reflection/reachability-metadata.json
native-image -cp out ReflectionModesDemo reflection-modes-demo
./reflection-modes-demo 'ReflectionModesDemo$Alpha'
./reflection-modes-demo 'ReflectionModesDemo$Beta'
~~~

Lệnh `mkdir -p` dùng shell Unix. Trên PowerShell, tạo thư mục bằng:

~~~powershell
New-Item -ItemType Directory -Force out\META-INF\native-image\demo\reflection | Out-Null
~~~

Sau khi ghi JSON và biên dịch Java theo cùng các tham số, lệnh build native có thể dùng:

~~~powershell
native-image -cp out ReflectionModesDemo reflection-modes-demo
.\reflection-modes-demo.exe 'ReflectionModesDemo$Alpha'
.\reflection-modes-demo.exe 'ReflectionModesDemo$Beta'
~~~

**Kết quả mong muốn** của hai native run là giống JVM `type=...` và `result=...` ở mục 5. Đây là **kịch bản kiểm chứng đề xuất, chưa thực thi native build trong bài này**. Bản GraalVM hoặc cách đóng gói hiện hữu có thể dùng schema metadata khác: bản cũ thường dùng `reflect-config.json` với cấu trúc một mảng có trường `name`; **không thay lẫn hai định dạng một cách tùy ý**. Luôn xác minh tài liệu đúng phiên bản công cụ và metadata thực sự được đưa vào classpath tại thời điểm build.

## 7. Khi thiếu metadata thì lỗi có thể biểu hiện ra sao?

Nếu không có thông tin cần thiết và Native Image cũng không thể suy ra access từ mã:

- `Class.forName(runtimeName)` có thể thất bại do lớp không được giữ lại hoặc không được đăng ký cho dạng truy cập ấy.
- `getDeclaredConstructor`, `getDeclaredMethod`, `getDeclaredField` hoặc `newInstance` có thể thất bại vì member không được cho phép truy vấn/gọi qua Reflection.
- Tùy phiên bản và chế độ kiểm tra, có thể thấy `ClassNotFoundException`, `NoSuchMethodException`, `MissingReflectionRegistrationError` hoặc chẩn đoán khác. **Không có một loại exception duy nhất được bảo đảm cho mọi thiếu sót metadata.**
- Mã có `catch (Exception)` có thể che mất một phần triệu chứng. Một số lỗi về thiếu đăng ký là `Error` chứ không phải `Exception`; đừng bắt `Throwable` đại trà rồi im lặng tiếp tục.

Cũng cần kiểm tra **nguyên nhân không liên quan metadata**: sai binary name, thiếu dependency khi build native, API private không accessible, lệch version thư viện, hoặc class khởi tạo static không thành công.

Trên các bản GraalVM hỗ trợ, `--exact-reachability-metadata` bật chế độ báo thiếu registration rõ hơn. Khi chạy native binary, `-XX:MissingRegistrationReportingMode=Warn` hoặc `Exit` có thể giúp tìm truy cập chưa được đăng ký. Các flag này **phụ thuộc phiên bản**; kiểm tra tài liệu của runtime thực tế trước khi đưa vào CI.

## 8. Tracing agent: thu thập Reflection thực sự phát sinh

Trong hệ sinh thái GraalVM, tracing agent có thể theo dõi một lần chạy **trên JVM** và ghi metadata cho Reflection, resources, proxy, JNI và các tính năng động liên quan. Một dạng lệnh điển hình (cần GraalVM/JDK có agent tương thích):

~~~bash
java -agentlib:native-image-agent=config-output-dir=generated-config \
  ReflectionModesDemo 'ReflectionModesDemo$Alpha'
~~~

Agent ghi cấu hình cho các thao tác nó **quan sát được**. Nhưng nếu chỉ chạy Alpha, không có gì bảo đảm metadata cho Beta cũng được thu thập. Hãy thiết kế **test matrix bao phủ tất cả đường đi động**, gồm các plugin tùy cấu hình, kiểu dữ liệu, nhánh lỗi và biến thể môi trường.

Quy trình tốt hơn là:

1. Thu thập metadata qua các ca chạy đại diện.
2. Review file đã sinh; loại bỏ đăng ký thừa và bổ sung nhánh chưa chạy.
3. Gộp metadata thành đầu vào build có kiểm soát phiên bản.
4. Build native và chạy test với chính binary đó.
5. Tái chạy mỗi khi cập nhật framework, thư viện hoặc phiên bản GraalVM.

Agent là **công cụ khám phá**, không tự chứng minh đã bao phủ mọi cấu hình runtime mà hệ thống có thể gặp.

## 9. Reflection không phải loại dynamic feature duy nhất

Một hệ thống có thể qua được bước đọc annotation nhưng vẫn lỗi khi làm việc với thứ khác. Đối chiếu nhu cầu:

| Tính năng động | JVM bình thường | Native Image / AOT cần xem xét |
| --- | --- | --- |
| `Class.forName` theo input | Tìm class còn trên classpath và được loader thấy | Class có được giữ lại/đăng ký không? |
| `Method.invoke`/constructor Reflection | Tra cứu và kiểm tra Java access | Member có metadata cho query/invoke không? |
| `getDeclaredAnnotations` và đọc giá trị | Metadata annotation nếu `RetentionPolicy.RUNTIME` | Annotation metadata/kiểu liên quan còn được giữ không? |
| `Proxy.newProxyInstance` | Sinh JDK proxy theo interface runtime | Interface/proxy combination có được hỗ trợ/đăng ký không? |
| Sinh bytecode mới bằng thư viện | JVM có thể nạp class mới khi công cụ và quyền cho phép | Native Image không tương đương classloading JVM mở; cần phương án sinh sẵn hoặc hỗ trợ riêng |
| Tải resource theo tên | Đọc resource có mặt trong classpath | Resource có được đóng gói/đăng ký không? |
| Serialization dựa trên Reflection | Thường truy cập nhiều constructor/field ẩn | Metadata serialization và reflection tương ứng |
| JNI hoặc truy cập native | Nhiều class/member được tra cứu từ native code | Metadata JNI và khả năng liên kết native code |

Với **dynamic proxy**, không chỉ quan tâm class của handler: **danh sách interface và thứ tự** cũng mang ý nghĩa. Với resource, Reflection metadata **không tự thay thế** resource metadata. Với `ServiceLoader`, cần kiểm tra provider được phát hiện và đóng gói đúng trên native target. Mỗi cơ chế có vùng đăng ký riêng; đừng cấu hình `allDeclaredMethods` cho toàn bộ ứng dụng như một cách giải quyết chung.

## 10. Một giới hạn khác: JPMS và deep reflection

Ngay cả khi chạy trên **HotSpot JVM đầy đủ**, named module vẫn có ranh giới đóng gói.

Giả sử:

~~~java
// module-info.java của thư viện
module data.core {
    exports demo.api;
}
~~~

`exports demo.api;` cho phép code ở module đọc được `data.core` sử dụng **API public** ở package đó. Nó **không cấp quyền** cho một module khác gọi private field/method bằng `setAccessible(true)`.

Nếu một framework ở module `data.client` cần deep reflection vào `demo.api`, module sở hữu có thể mở có chọn lọc:

~~~java
module data.core {
    exports demo.api;
    opens demo.api to data.client;
}
~~~

Hoặc khi chạy JVM, nếu có cơ sở bảo mật và vận hành phù hợp:

~~~bash
java --add-opens data.core/demo.api=data.client \
  --module-path mods -m data.client/demo.client.Main
~~~

Trong PowerShell, viết cùng lệnh một dòng:

~~~powershell
java --add-opens data.core/demo.api=data.client --module-path mods -m data.client/demo.client.Main
~~~

Lưu ý:

- `exports` cho dùng public API; `opens` cho phép deep reflection vào package được mở.
- Việc module gọi **đọc được module đích** vẫn quan trọng để làm việc với kiểu/API tương ứng.
- `--add-opens` là flag mở package, không phải cơ chế đăng ký reachability cho Native Image.
- `trySetAccessible()` cho biết việc bật quyền có thành công hay không; `setAccessible(true)` có thể ném `InaccessibleObjectException`.
- `MethodHandles.privateLookupIn(...)` có yêu cầu lookup mode và quyền module riêng, không phải cách bỏ qua hoàn toàn `opens`.

**Chẩn đoán dựa vào lỗi:** `InaccessibleObjectException` thường gợi ý bị giới hạn truy cập JPMS; lỗi thiếu metadata trên native binary thường gợi ý cấu hình reachability; `ClassNotFoundException` còn có thể do đường dẫn lớp/sai tên và cần truy nguyên.

## 11. Spring AOT: cấu hình được quyết định trước thời điểm chạy

Spring có khả năng **Ahead-of-Time processing** để phân tích `ApplicationContext`, tạo mã khởi tạo bean và sinh `RuntimeHints` cho những thao tác động cần thiết. Một số thứ trước đây do classpath scanning hoặc runtime introspection quyết định nay cần **biết đủ thông tin khi build**.

Chẳng hạn, trong Spring AOT:

- classpath và tập bean được phân tích từ build-time environment; thêm JAR mới vào runtime không nhất thiết làm AOT context khám phá ra bean mới;
- các điều kiện như `@Profile` và `@Conditional` có thể được xác định trong AOT processing; đổi cấu hình runtime không luôn thay đổi tập bean như cách chạy JVM thông thường;
- mã AOT có thể dùng lời gọi constructor/factory trực tiếp thay cho Reflection;
- nếu thư viện tùy biến vẫn phải truy cập member bằng Reflection, hãy đóng góp **`RuntimeHints`** cho đúng type/member.

Một ví dụ lớp registrar trong dự án Spring Framework hỗ trợ AOT:

~~~java
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class TaskHints implements RuntimeHintsRegistrar {
    public static class PluginTask {
        public PluginTask() {}
    }

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(
                PluginTask.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }
}
~~~

Đây là **một file Java dùng Spring Framework**, không thuộc ví dụ JDK thuần ở mục 5; cần dependency Spring AOT trên classpath. Để engine biết registrar, có thể gắn `@ImportRuntimeHints(TaskHints.class)` lên một bean/cấu hình phù hợp hoặc đăng ký theo cơ chế `META-INF/spring/aot.factories` được tài liệu Spring hỗ trợ. Mục đích là nói rõ constructor nào sẽ được gọi động.

Spring thường tự sinh nhiều hints cho các thành phần framework mà engine phân tích được; **không đăng ký thủ công trùng lặp mọi bean**. Tập trung vào plugin, conversion, library reflection hoặc loại dữ liệu không được engine suy ra từ bean graph. `RuntimeHintsPredicates` và các công cụ test Runtime Hints của Spring giúp kiểm tra phần đăng ký.

## 12. Kịch bản so sánh hành vi cần chạy trong CI

Hãy giữ **cùng một contract đầu vào/đầu ra** rồi kiểm tra trên từng môi trường; không chỉ đo xem ứng dụng có khởi động thành công.

| Ca kiểm thử | JVM classpath | JVM JPMS hạn chế | Native Image có metadata |
| --- | --- | --- | --- |
| Chọn `Alpha` bằng tên nhị phân | Mong đợi `alpha` | Mong đợi nếu class/module accessible | Mong đợi `alpha` nếu đã đăng ký |
| Chọn `Beta` (nhánh khác) | Mong đợi `beta` | Phụ thuộc module | Phải có metadata cho Beta |
| Tìm public constructor | Thường thành công nếu class tồn tại và accessible | Cần đúng module access | Cần member được giữ/đăng ký |
| Truy cập private field từ module khác | Có thể bị từ chối | Dễ bị `InaccessibleObjectException` khi không `opens` | Vừa cần đăng ký metadata, vừa cần quyền phù hợp |
| Tải class/JAR mới sau khi tiến trình chạy | Có thể qua class loader | Có thêm giới hạn module/loader | Không giả định có thể nạp bytecode hoàn toàn mới |
| Đọc resource động | Nếu resource có mặt | Nếu resource có mặt và cho phép | Phải được đóng gói/đăng ký |
| Dynamic proxy theo cấu hình | JVM có thể tạo lớp proxy | Có thể cần xét quyền module của interface | Cần cấu hình/hỗ trợ proxy phù hợp |

**Bộ kiểm thử tối thiểu:**

1. Chạy trên JVM classpath thông thường với toàn bộ tên lớp có thể chọn.
2. Nếu dự án có JPMS, chạy trên module path và thử quyền `opens`, cả tình huống thành công và bị từ chối.
3. Build Native Image **từ đúng commit, dependency và metadata** sẽ phát hành; chạy binary với ít nhất hai nhánh dynamic input độc lập.
4. Nếu dùng Spring AOT, kiểm tra build-time profile/condition, những bean động và custom Runtime Hints.
5. So sánh loại lỗi, cause, stdout/return value và side effect; lưu log có tên binary, classloader/module và version native-image.

Điểm cốt lõi: **JVM test pass chưa chứng minh Native Image tương thích**. Trái lại, lỗi trên native không mặc nhiên là bug của Java Reflection: có thể do thiếu metadata hoặc giả định sai về những quyết định được đưa lên build time.

## 13. Khi nên thay Reflection bằng thiết kế tĩnh?

Reflection đem lại khả năng mở rộng, nhưng hệ thống AOT càng dễ phân tích khi loại thực thi/metadata được chỉ định rõ ràng.

Các lựa chọn nên cân nhắc:

- **Interface + factory tường minh:** chọn các implementation đã biết, khởi tạo bằng code; compiler/AOT dễ theo dõi call graph.
- **Constructor injection:** cung cấp dependency qua constructor thay vì quét và sửa private field.
- **ServiceLoader với providers xác định trước:** phù hợp khi quy tắc provider của target runtime và build plugin được kiểm chứng; không xem nó là bảo đảm tự động cho JAR nạp sau build.
- **Generated registry tại build time:** annotation processor hoặc AOT step sinh map tên → factory thay cho runtime classpath scan.
- **MethodHandle/VarHandle:** hữu ích để tối ưu đường gọi khi đã có quyền và handle; nhưng **không bảo đảm vượt rào Native Image metadata hoặc JPMS**.
- **Reflection + hints** khi sự linh hoạt thực sự cần: đăng ký đúng lớp/member và kiểm tra native binary trong CI.

Ví dụ thay tìm lớp động bằng factory rõ ràng:

~~~java
import java.util.Map;
import java.util.function.Supplier;

public class StaticTaskFactoryDemo {
    interface Task { String run(); }
    static class Alpha implements Task {
        public String run() { return "alpha"; }
    }
    static class Beta implements Task {
        public String run() { return "beta"; }
    }

    public static void main(String[] args) {
        Map<String, Supplier<Task>> factories = Map.of(
                "alpha", Alpha::new,
                "beta", Beta::new);
        String key = args.length == 0 ? "alpha" : args[0];
        Supplier<Task> factory = factories.get(key);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown task: " + key);
        }
        System.out.println(factory.get().run());
    }
}
~~~

Chạy:

~~~bash
javac --release 17 StaticTaskFactoryDemo.java
java StaticTaskFactoryDemo alpha
java StaticTaskFactoryDemo beta
~~~

Kết quả mỗi lần gọi lần lượt là:

~~~text
alpha
~~~

~~~text
beta
~~~

Ở đây compiler thấy trực tiếp `Alpha::new`, `Beta::new` và không cần `Class.forName` hay reflective constructor invocation. Đánh đổi là không thể tự thêm implementation hoàn toàn mới chỉ bằng thay đổi chuỗi cấu hình; muốn hỗ trợ loại mới, cần sinh lại registry hoặc build lại binary.

## 14. Lỗi chẩn đoán thường gặp

| Dấu hiệu | Điều cần phân biệt | Cách xử lý thực tế |
| --- | --- | --- |
| `ClassNotFoundException` | Sai tên, thiếu classpath/module, hoặc class không có trong Native Image | Xác minh tên binary, classpath khi build, metadata |
| `NoSuchMethodException` | Sai signature, member bị lược bỏ trong native, hoặc API đổi version | Kiểm tra descriptor, method/constructor registration |
| `InaccessibleObjectException` | JPMS đóng package với deep reflection | API public hoặc `opens` đúng package/module |
| `IllegalAccessException` | Access check không qua | Kiểm tra visibility/module/lookup context |
| `MissingReflectionRegistrationError` | Native Image thiếu phần tử được yêu cầu | Thêm hints/metadata phù hợp và chạy native test |
| Proxy phát sinh lỗi | Interface combination hoặc AOT proxy metadata chưa được suy ra | Đối chiếu interface list/order và đăng ký proxy |
| Resource có trên JVM, mất trong native | Resource chưa vào image | Resource metadata hoặc build plugin phù hợp |
| Bean có trên JVM, vắng trên Spring AOT | Build-time graph/conditions khác runtime assumption | Kiểm tra AOT logs, profile/conditions, explicit bean definition |
| Biên dịch native thành công nhưng runtime lỗi | Build pass không bao phủ mọi đường đi động | Test đủ input/configuration và exact registration diagnostics |

Bắt ngoại lệ đúng phạm vi, log nguyên nhân gốc. Không xử lý bằng cách mở toàn bộ JDK package, giữ mọi class/method, hay nuốt mọi exception; các cách đó có thể che lỗi và làm tăng kích thước/tấn công bề mặt của ứng dụng.

## 15. Lưu ý về hiệu năng và an toàn

Native Image thường hướng tới **thời gian khởi động nhanh và footprint nhỏ hơn**, đổi lại có giới hạn nhất định về tính linh hoạt của những thành phần được phép xuất hiện lúc chạy. Tuy nhiên, tốc độ gọi `Method.invoke` không tự động nhanh hơn chỉ vì đã build native; muốn so sánh phải đo cả warmup, throughput, peak RSS, startup và workload thực tế.

- Đăng ký Reflection càng rộng (ví dụ `allDeclaredMethods` cho số lượng class lớn) càng có thể tăng kích thước binary và giữ metadata thừa.
- Đừng coi metadata như một permission check. Nếu một đoạn code được phép truy cập private bằng Java access và metadata đã cho phép, nó có thể trở thành kênh thao tác nhạy cảm.
- Khi dùng tên class nhận từ input không tin cậy, giới hạn danh sách implementation được phép. Cả JVM lẫn Native Image không làm `Class.forName(userInput)` tự trở thành an toàn.
- Một ứng dụng cấu hình plugin động cần đánh giá **khả năng thêm logic mới sau build**. Native binary có *closed-world assumption* khác JVM đầy đủ.
- Nếu quyền truy cập không cần dynamic dispatch, ưu tiên code tường minh giúp dễ audit và dễ tối ưu.

## 16. Câu hỏi tự kiểm tra

1. `javac` biên dịch `.java` trước khi chạy có khiến `Class.forName` không hoạt động? **Không.** JVM còn classpath/module path và cơ chế nạp lớp khi chạy.
2. Vì sao Native Image thường cần reachability metadata? Vì phân tích build-time không thể biết đầy đủ những class/member sẽ được chọn từ input runtime tùy ý.
3. `Class.forName("java.lang.String")` luôn đòi JSON metadata riêng? **Không.** Nhiều lời gọi với input hằng có thể được Native Image suy ra.
4. Có `--add-opens` thì không cần khai báo reflection metadata nữa? **Sai.** `opens` xử lý quyền module, metadata xử lý tính hiện diện/khả năng reflection trong image.
5. Nếu tracing agent chỉ chạy Alpha, nó có tự chứng minh Beta đã được bao phủ? **Không.** Cần chạy mọi nhánh hoặc khai báo bổ sung.
6. `new Alpha()` khác `Class.forName(name).getDeclaredConstructor().newInstance()` ở điểm nào với AOT? Lựa chọn class/constructor ở biểu thức đầu là **tĩnh**; biểu thức thứ hai có thể phụ thuộc dữ liệu runtime.
7. Khi Spring AOT không suy ra việc truy cập Reflection từ custom code, dùng gì? **`RuntimeHintsRegistrar` và `RuntimeHints`** (hoặc tích hợp hints/AOT processor phù hợp).
8. Nếu JVM test pass nhưng native binary báo thiếu member, bước tiếp theo? Xác nhận class/member/đường đi thực tế, metadata đúng phiên bản, rồi build và thử lại chính binary.
9. Named module có `exports` public API nhưng không `opens`, dùng `setAccessible(true)` với private field từ module khác được không? **Không được bảo đảm**; thông thường phải mở package cho module caller.
10. Cách nào phù hợp hơn nếu chỉ có hai task Alpha/Beta và không có yêu cầu thêm plugin tùy ý? **Interface + static factory/registry** có thể đơn giản, an toàn và dễ AOT hơn.

## 17. Tài liệu chính thức để đối chiếu phiên bản

- [GraalVM Native Image — Reachability Metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) (cấu trúc `reachability-metadata.json`, reflection và tùy chọn chẩn đoán).
- [GraalVM Native Image — Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/) (thu thập metadata theo ca chạy; đối chiếu đường dẫn tài liệu với bản phát hành thực tế).
- [Spring Framework — Ahead-of-Time Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html) (hạn chế build-time graph, `RuntimeHints`, `@ImportRuntimeHints`, test).
- [Java SE 17 — Class.forName và Reflection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html).
- [Java SE 17 — AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html).
- [OpenJDK JEP 403 — Strongly Encapsulate JDK Internals](https://openjdk.org/jeps/403).

**Ghi nhớ:** Để so sánh Reflection trên các môi trường, kiểm tra riêng **class/member có trong runtime hay không**, **quyền truy cập có đủ hay không**, và **hành vi có bị cố định ở build time hay không**. Trên Native Image, bổ sung metadata chính xác và test chính binary; trên JPMS, làm rõ `reads`/`exports`/`opens`; còn khi có thể, dùng registry/factory tĩnh để tránh những nhu cầu reflection không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)
