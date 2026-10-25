<a id="back-to-top"></a>

# Khởi tạo lớp

## Menu
- [Phương thức &lt;clinit&gt;, static field initializer và static block](#class-initialization-001)
- [Phân biệt &lt;clinit&gt; với constructor &lt;init&gt;](#class-initialization-002)
- [Các thao tác chủ động kích hoạt khởi tạo](#class-initialization-003)
- [Các thao tác không kích hoạt khởi tạo: class literal, mảng và hằng biên dịch](#class-initialization-004)
- [Khởi tạo lớp cha và thứ tự thực hiện static initializer](#class-initialization-005)
- [Khởi tạo interface và ảnh hưởng của default method](#class-initialization-006)
- [Đọc static field kế thừa: kiểu thực sự khai báo field](#class-initialization-007)
- [Đồng bộ khởi tạo lớp giữa nhiều luồng](#class-initialization-008)
- [Khởi tạo đệ quy, phụ thuộc vòng và giá trị chưa hoàn thiện](#class-initialization-009)
- [ExceptionInInitializerError và trạng thái khởi tạo thất bại](#class-initialization-010)

## <a id="class-initialization-001">Phương thức &lt;clinit&gt;, static field initializer và static block</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức `<clinit>`, static field initializer và static block

> **Ngữ cảnh:** Java Core → ClassLoader → Khởi tạo lớp. Bài học tập trung vào mã chạy trong **giai đoạn khởi tạo class/interface** (class initialization), thứ tự thực thi các khai báo `static` và cách trình biên dịch biểu diễn chúng bằng phương thức JVM `<clinit>`. Các ví dụ hoàn chỉnh dưới đây dùng Java 17+, chạy độc lập theo đúng tên tệp.

## 1. Phải phân biệt ba khái niệm trước khi đọc mã

- **Static field initializer** là biểu thức gán ban đầu gắn với khai báo biến lớp, chẳng hạn `static int count = initialize();`. Biểu thức có thể có side effect, ném lỗi hoặc đọc field khác.
- **Static initializer block** là khối `static { ... }` trong thân `class`. Khối chứa các statement cần chạy trong quá trình khởi tạo lớp. Một lớp có thể có nhiều khối `static`, xen giữa các field initializer.
- **`<clinit>` (class/interface initialization method)** là phương thức đặc biệt ở **mức class file/JVM**, thường do compiler tạo để kết hợp các lệnh khởi tạo tĩnh cần thực thi. Nó không phải phương thức mà bạn tự khai báo, gọi trực tiếp hay override trong mã Java.

Sơ đồ tư duy:

~~~text
Mã nguồn Java                  Class file                    JVM runtime
static int a = compute(); ─┐
static { audit(a); } ───────┼──> <clinit>:()V ─────────────> chạy khi class được initialize
static int b = more(); ────┘       theo thứ tự nguồn

static final int SIZE = 64  ────> ConstantValue = 64 ─────> gán trước lúc chạy <clinit>
                                            (nếu compiler phát ra thuộc tính)
~~~

**Chú ý:** `static` thuộc về lớp nhưng **không đồng nghĩa** “chạy ngay lúc chương trình bắt đầu”. Lớp có thể đã được **load / verify / prepare** mà phần khởi tạo tĩnh vẫn chưa chạy. Một class không có công việc khởi tạo tĩnh cần thực thi có thể **không có `<clinit>`** trong bytecode.

## 2. Phân biệt nhanh với constructor `<init>`

`<clinit>:()V` là phương thức JVM dành cho mã khởi tạo static của class hoặc interface; class file có thể không có phương thức này nếu không có công việc static cần thực thi. `<init>(...)V` là constructor, có thể có nhiều overload và chạy cho từng instance. Java source không gọi trực tiếp `<clinit>`, còn JVM tự điều phối nó khi class được initialized. Với `new Demo()` lần đầu, initialization của class hoàn tất trước constructor; lần `new` tiếp theo vẫn gọi constructor nhưng không chạy lại initializer tĩnh.

Bài **Phân biệt `<clinit>` với constructor `<init>`** có ví dụ chạy nhiều lần `new` và giải thích instance field initializer, instance block, `super(...)`. Phần còn lại của bài này tập trung vào bytecode, thứ tự và giới hạn của **static initialization**.

## 3. Trước `<clinit>`, JVM đã làm gì?

Các giai đoạn quy định bởi JVM Specification:

~~~text
Loading                 Linking                                      Initialization
đọc / định nghĩa class  ├─ Verification: kiểm tra bytecode           ├─ gán ConstantValue nếu có
                        ├─ Preparation: tạo static fields, mặc định └─ chạy <clinit> nếu class có
                        └─ Resolution: giải symbolic references
                            (thời điểm có thể sớm hoặc lười)
~~~

Trong **preparation**, các trường static được tạo và có **giá trị mặc định**: `int` là `0`, `boolean` là `false`, reference là `null`. Đừng nói `static int x = 10` đã chạy “trong preparation”: biểu thức gán do mã nguồn tạo thuộc initialization.

Một điểm dễ nhầm: trường constant có `ConstantValue`. Theo JVMS, giá trị từ thuộc tính đó được gán **trong initialization, trước khi gọi `<clinit>`**; không phải JVM chạy một dòng bytecode Java thông thường cho hằng đó ngay tại vị trí khai báo. Vì vậy thuật toán JLS ưu tiên gán các **constant variables** trước những initializer khác.

Xem hai trường hợp:

~~~java
static final int PORT = 8080;                 // constant variable: int + constant expression
static final int RUNTIME_PORT = readPort();   // final, nhưng KHÔNG phải constant variable
~~~

`PORT` có thể được biểu diễn bằng `ConstantValue` và tham chiếu trực tiếp từ class khác có thể được compiler **inline**. `RUNTIME_PORT` đòi chạy biểu thức `readPort()` trong initialization, dù có `final`.

**Không có bảo đảm mọi symbolic reference được resolve cùng một thời điểm.** JVM có thể link/resolve sớm hoặc muộn theo giới hạn đặc tả; điều kiện kích hoạt initialization mới là thứ cần dựa vào, không phải việc log báo class vừa được load.

## 4. Static field initializer và static block được chạy theo thứ tự nào?

Với phần mã khởi tạo tĩnh bình thường, Java thực thi **field initializers và static blocks theo thứ tự chúng xuất hiện trong thân class**, sau các bước bắt buộc như gán compile-time constants và khởi tạo lớp cha cần thiết. Mỗi initializer có thể đọc trạng thái đã gán trước đó; một field khai báo sau nhưng chưa được gán vẫn có giá trị **mặc định**.

Ví dụ chạy được, tệp **`InitOrder.java`**:

~~~java
public class InitOrder {
    static class Demo {
        static int first = mark("field first", 10);

        static {
            System.out.println(
                "block: first=" + first + ", second=" + readSecond());
        }

        static int second = mark("field second", 20);

        static int readSecond() {
            return second;
        }

        static int mark(String name, int value) {
            System.out.println(name);
            return value;
        }
    }

    public static void main(String[] args) {
        System.out.println("before");
        System.out.println("result=" + Demo.second);
        System.out.println("again=" + Demo.first);
    }
}
~~~

~~~bash
javac --release 17 InitOrder.java
java InitOrder
~~~

Output thực tế trên OpenJDK 21 khi biên dịch với `--release 17`:

~~~text
before
field first
block: first=10, second=0
field second
result=20
again=10
~~~

Diễn giải từng bước:

1. JVM bắt đầu initialize `Demo` khi cần đọc `Demo.second`, là trường static **không phải constant variable**.
2. Ban đầu `first=0` và `second=0` do preparation.
3. `first = mark(...)` in `field first`, rồi đặt `first=10`.
4. Khối `static` chạy; `readSecond()` thấy `second=0` vì initializer của `second` nằm **sau** khối này.
5. `second = mark(...)` in `field second`, rồi đặt `second=20`.
6. `main` nhận kết quả `20`; lần đọc `Demo.first` sau đó không chạy lại initializer.

### 4.1. Xem bytecode gộp thành `<clinit>`

Sau khi biên dịch ví dụ:

~~~bash
javap -c -p 'InitOrder$Demo'
~~~

Tìm phần `static {};` ở cuối đầu ra `javap`. Mẫu bytecode quan trọng sẽ mang ý nghĩa:

~~~text
invokestatic mark(...)         // tính first
putstatic first                // first = 10
... invokevirtual println ...  // chạy static block
invokestatic mark(...)         // tính second
putstatic second               // second = 20
return
~~~

Mã đã kiểm tra trên OpenJDK 21 cho thấy phần `static {};` chứa cả hai `putstatic` và lời gọi in của khối giữa, đúng thứ tự. Thứ tự **hành vi** là do ngôn ngữ quy định; các chỉ số constant pool, offset và opcode tối ưu cụ thể có thể khác giữa compiler/JDK.

### 4.2. Forward reference: luật compile time và giá trị runtime

Đoạn dưới **không biên dịch được** vì đọc bằng *simple name* một trường được khai báo phía sau trong initializer:

~~~java
class IllegalForward {
    static int first = later; // illegal forward reference
    static int later = 7;
}
~~~

Nhưng giới hạn forward reference không cấm một phương thức như `readSecond()` đọc field sau khi JVM đã cho phép gọi phương thức ấy. Bởi vậy `InitOrder.Demo` ở trên **biên dịch được** nhưng vẫn thấy `second=0` trong lúc initialization đang dang dở. Có thể dùng qualified field access hoặc đường gọi gián tiếp để vượt trường hợp compile-time bị cấm, song việc ấy **không làm field được gán sớm hơn**.

**Kinh nghiệm:** Nếu các static fields phụ thuộc nhau, viết luồng khởi tạo đơn giản theo đúng thứ tự và tránh callback ở giữa. Đừng thiết kế logic đúng sai dựa trên việc một field khác tình cờ còn giá trị mặc định.

## 5. Hằng biên dịch và mã khởi tạo tĩnh

`static final int RETRIES = 3` và `static final String LABEL = "ready"` là **constant variables**: `final`, kiểu primitive/`String`, giá trị là *constant expression*. Chúng có thể dùng thuộc tính `ConstantValue` và được inline vào mã bên gọi. `static final Integer BOXED = 3` hoặc `static final int SIZE = Integer.parseInt("3")` không phải constant variables và cần initializer ở runtime. `ConstantValue` được gán **trong initialization trước `<clinit>`**, sau preparation đặt giá trị mặc định.

Việc chỉ thấy `static final` chưa đủ để kết luận có mã `<clinit>` hay một phép đọc có kích hoạt initialization. Bài **Các thao tác không kích hoạt khởi tạo** trình bày riêng ví dụ compile-time constant, `Integer`, inlining và binary compatibility.

## 6. Khi nào JVM đi vào `<clinit>`?

Một lớp có thể đã được load/link nhưng mã static chỉ được thực thi khi có yêu cầu **initialization**. Các điểm kích hoạt phổ biến: `new`, `getstatic` đọc nonconstant field, `putstatic` ghi field, `invokestatic` gọi phương thức static của **declaring type**, hoặc API như `Class.forName(name, true, loader)`. Ngược lại, chỉ thực hiện `T.class`, `new T[5]` hay `Class.forName(name, false, loader)` không tự yêu cầu khởi tạo `T`.

Các bài **Các thao tác chủ động kích hoạt khởi tạo** và **Các thao tác không kích hoạt khởi tạo** hướng dẫn chi tiết về trigger, reflection, MethodHandle và constant inlining. Ở đây, điểm chính là phân biệt **thời điểm có class file** với **thời điểm thực thi mã `<clinit>`**.

## 7. Class cha và interface trong quy trình initialization

Trước khi chạy initializer tĩnh thông thường của một class, JVM phải initialize superclass và các superinterface **có khai báo default method** theo thuật toán quy định. Nhưng initialize một interface riêng lẻ không tự động kéo theo initialization mọi superinterface; chỉ quan hệ `implements` cũng không khiến tất cả interface chạy mã static.

Bài **Khởi tạo lớp cha và thứ tự thực hiện static initializer** cùng bài **Khởi tạo interface và ảnh hưởng của default method** cho những ví dụ có output riêng để kiểm chứng thứ tự giữa các kiểu.

## 8. Đồng bộ khởi tạo và đọc giá trị dở dang

JVM điều phối initialization theo **runtime class identity** (binary name và defining loader); một thread đang khởi tạo sẽ khiến thread khác cần cùng class phải chờ. Khi **chính thread đó** yêu cầu khởi tạo lại cùng class theo một vòng gọi, JVM không chạy lại `<clinit>` từ đầu. Field chưa được gán có thể vẫn là `0` hoặc `null`. Đồng bộ thành công công bố trạng thái ban đầu, nhưng không tự làm các phép ghi mutable static về sau trở nên thread-safe.

Hai bài **Đồng bộ khởi tạo lớp giữa nhiều luồng** và **Khởi tạo đệ quy, phụ thuộc vòng và giá trị chưa hoàn thiện** tập trung vào bảo đảm happens-before, vòng A → B → A và deadlock.

## 9. Nếu `<clinit>` thất bại

Static initializer ném exception chưa được xử lý có thể khiến class trở thành **erroneous**. Lần đầu JVM bọc throwable không thuộc `Error` bằng `ExceptionInInitializerError`; nếu throwable là `Error` thì có thể chuyển tiếp chính lỗi đó. Các yêu cầu khởi tạo sau đối với **cùng runtime class** gặp `NoClassDefFoundError` thay vì chạy lại `<clinit>`.

Vì thế hãy tìm nguyên nhân đầu tiên trong stack trace; tránh đặt công việc I/O hoặc thao tác cần retry trong static initializer. Bài **ExceptionInInitializerError và trạng thái khởi tạo thất bại** có quy trình debug chuyên sâu.

## 10. Cú pháp và giới hạn của khối `static`

Khối `static`:

~~~java
class StaticRules {
    private static final java.util.Map<String, Integer> CODES;

    static {
        java.util.Map<String, Integer> values = new java.util.HashMap<>();
        values.put("ok", 200);
        values.put("missing", 404);
        CODES = java.util.Map.copyOf(values);
    }

    static int codeOf(String key) {
        return CODES.getOrDefault(key, -1);
    }
}
~~~

Đây là đoạn **class minh họa** biên dịch được trên Java 17+, không khai báo `main`. Cách này hữu ích khi cần nhiều câu lệnh để tạo trạng thái static cuối cùng; `Map.copyOf` giúp tránh caller sửa trực tiếp trạng thái sau khởi tạo. Để chạy thử từ một chương trình khác, gọi `StaticRules.codeOf("ok")` và sẽ nhận `200`.

Các giới hạn quan trọng:

- Khối `static` không được dùng `return`; không có `this`/`super` của instance và không thể trực tiếp truy cập instance field/method như thể đã có đối tượng.
- Khối static phải **có khả năng complete normally** theo phân tích compile-time; viết một vòng vô hạn hoặc `throw` chắc chắn không có đường thoát sẽ gặp giới hạn JLS. Một phương thức được gọi từ initializer vẫn có thể ném lỗi ở runtime như `FailureCase`.
- Checked exceptions từ static initializer phải được xử lý trong khối hoặc chuyển thành unchecked exception; static initializer không có `throws` để khai báo.
- Interface **không cho khai báo `static { ... }` theo cú pháp Java**, nhưng có thể có các field initializer bằng biểu thức gọi phương thức, và compiler có thể tạo `<clinit>` cho interface ấy.
- Enum, record và các lớp do compiler/framework tạo vẫn có quy tắc class initialization; chi tiết code sinh ra có thể nhiều hơn dòng nguồn. Dùng `javap -c -p` để quan sát đúng artifact đang chạy.
- Dù JVM thường gộp các static initializers trong một `<clinit>`, compiler tối ưu hằng và đoạn mã khác có thể khiến kết quả bytecode không khớp một-một với từng dòng Java. Chú ý **hành vi do đặc tả quy định**, không khẳng định mọi compiler phải sinh bytecode giống nhau.

## 11. Chọn static field initializer hay static block?

| Nhu cầu | Cách viết thường rõ nhất | Điều cần cân nhắc |
| --- | --- | --- |
| Giá trị độc lập, gọn | `static final int LIMIT = 100;` | Compile-time constant có thể bị inline vào client |
| Cần gọi phương thức tính giá trị | `static final Config DEFAULT = createDefault();` | Factory được gọi lúc class initialize; có thể ném lỗi |
| Cần nhiều bước, ràng buộc thứ tự | `static { ... }` | Giữ đoạn ngắn, rõ exception và dependency |
| Cấu hình có thể thất bại hoặc phụ thuộc môi trường | Explicit bootstrap / dependency injection | Tránh làm cả class unusable do lỗi init một lần |
| Chi phí tạo resource lớn nhưng ít khi cần | Lazy holder hoặc lazy initialization có kiểm soát | Xử lý close/lifetime, concurrency, lỗi và cleanup riêng |

### 11.1. Initialization-on-demand holder: dựa trên chính cơ chế `<clinit>`

~~~java
public final class LazyService {
    private LazyService() { }

    private static final class Holder {
        static final LazyService INSTANCE = new LazyService();
    }

    public static LazyService instance() {
        return Holder.INSTANCE;
    }
}
~~~

`LazyService.instance()` là static method nên sẽ initialize `LazyService` nếu cần. Nhưng `Holder` là **class khác**: `Holder.INSTANCE` được khởi tạo khi `Holder` lần đầu active use. JVM đảm bảo phối hợp initialization giữa thread cho `Holder`; ví dụ này minh họa lazy singleton đơn giản mà không cần tự viết double-checked locking.

Giới hạn: instance sẽ tồn tại theo loader/lifetime của lớp, không có cơ chế đóng resource tự động; constructor nếu ném exception sẽ khiến `Holder` erroneous và không có retry trong cùng loader. Tránh áp dụng mẫu này cho resource cần vòng đời dừng/mở lại linh hoạt nếu chưa bổ sung cơ chế quản lý.

## 12. Checklist đọc lỗi hoặc phân tích `<clinit>`

Khi gặp một bug static initialization, làm theo thứ tự:

1. **Chỉ đúng runtime class:** tên đầy đủ, defining loader, module, JDK/runtime và điều kiện launcher. Cùng tên nhưng loader khác có thể có trạng thái riêng.
2. **Tìm trigger đầu tiên:** `new`, `getstatic/putstatic`, `invokestatic`, `Class.forName(..., true, ...)`, framework reflection hay lớp con kéo theo superclass/default-method interface.
3. **Đọc thứ tự mã nguồn:** mọi static field initializer và `static{}` theo thứ tự; đánh dấu dependency tới class khác. Phân biệt constant variables với nonconstant static fields.
4. **Đọc bytecode khi cần:** `javap -c -p ClassName`; xem phần `static {};` tương ứng `<clinit>`, các lệnh `getstatic`/`putstatic`, và `ConstantValue` trong `javap -v`. Đừng suy ra từ tên trường rằng field chắc chắn có `ConstantValue`.
5. **Đọc cause đầu tiên:** `ExceptionInInitializerError.getCause()` hoặc `getException()` (API lịch sử), log sớm nhất; nếu chỉ thấy `NoClassDefFoundError`, tìm failure trước đó cùng loader.
6. **Kiểm tra vòng phụ thuộc và blocking:** class A gọi B trong lúc A init, B đọc lại A; khóa, thread, kết nối bên ngoài, I/O trong `static{}`.
7. **Thiết kế lại nếu cần:** giảm side effect, đưa việc có thể fail vào startup quản lý rõ ràng, tránh trạng thái static mutable chia sẻ không đồng bộ, tái biên dịch consumer khi đổi compile-time constants.

Các lệnh hữu ích khi có JDK tools:

~~~bash
javac --release 17 InitOrder.java
javap -c -p 'InitOrder$Demo'
javap -v 'ConstantCase$Config'
java -Xlog:class+init=info InitOrder
~~~

`-Xlog:class+init=info` là logging của **HotSpot/OpenJDK hỗ trợ unified logging** và có thể in rất nhiều thông tin; tên tag/đầu ra không phải cú pháp chuẩn JLS cho mọi JVM. Nếu tùy môi trường, kiểm tra `java -Xlog:help`. `javap` là công cụ JDK đọc class file; có thể phát hiện `ConstantValue` và `static {};` ngay cả khi chưa chạy chương trình.

## 13. Điểm kiểm tra khi đọc `<clinit>`

Đọc static field initializers và `static {}` **theo thứ tự nguồn**, kiểm tra `ConstantValue`, dùng `javap -c -p` để thấy bytecode thường được in ở mục `static {};`, và phân biệt hoạt động **loading** với lúc mã thực sự chạy. Số khối `static` trong source không phải số phương thức `<clinit>` trong class file.

## 14. Câu hỏi tự kiểm tra

1. Nếu `static int a = 1; static { a++; } static int b = a * 2;` thì sau init có `a=2`, `b=4`. Giải thích theo thứ tự nguồn.
2. Vì sao `static final String LABEL = "ready";` không buộc class chủ sở hữu initialize khi client in hằng, còn `static final Integer BOXED = 42;` có thể buộc? Trả lời bằng điều kiện **constant variable**.
3. Vì sao `new ClassName[5]` và `ClassName.class` có thể chưa kích hoạt `<clinit>`?
4. Sau khi một static block ném `IllegalStateException` trong lần init đầu, vì sao lần sau cùng loader có thể thấy `NoClassDefFoundError`?
5. Một plugin reload tạo defining loader mới cho cùng tên lớp: static initializer có thể chạy lại không? **Có**, vì đó là runtime class identity mới, trong khi lớp cũ nếu còn referenced vẫn là entity khác.
6. Nếu trường nonconstant được khai báo sau block, vì sao block có thể đọc `0` qua phương thức gọi gián tiếp dù sau cùng trường là `20`?
7. Vì sao chạy `javap -c -p` và tìm phần `static {};` giúp giải thích thứ tự field initializer và static block?

## 15. Tài liệu đặc tả và API chuẩn

- [JLS 12.4 — Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4): active use, thứ tự superclass/superinterface, synchronization, erroneous state.
- [JLS 8.3.2, 8.3.3 — Field Initialization và Forward References](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.2): field initializer và giới hạn đọc field trước khai báo.
- [JLS 8.7 — Static Initializers](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.7): quy tắc cú pháp khối `static`.
- [JLS 4.12.4 — Constant Variables](https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.4): điều kiện hằng compile time.
- [JVMS 5.4.2 — Preparation](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.2) và [JVMS `5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.5): giá trị mặc định, `ConstantValue`, kích hoạt initialization.
- [JVMS 2.9.2 — Class and Interface Initialization Methods](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.9.2): tên/mô tả method `<clinit>`.
- [ClassLoader API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html) và [Class.forName API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): phân biệt loading với yêu cầu initialization.

**Ghi nhớ:** Static field initializers và static blocks xác định những **hành động khởi tạo** của lớp trong thứ tự được JLS quy định; compiler thường tổng hợp thành `<clinit>` để JVM chạy **khi lớp được initialize**, dưới cơ chế đồng bộ và xử lý lỗi của JVMS. Hiểu đúng *khi nào*, *theo thứ tự nào* và *với class identity nào* quan trọng hơn việc ghi nhớ tên phương thức đặc biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-002">Phân biệt &lt;clinit&gt; với constructor &lt;init&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phương thức khởi tạo lớp <clinit> với constructor <init> trong Java

## 1. Hai thời điểm khởi tạo khác nhau

Khi đọc source Java, ta quen với từ khóa **static**, phương thức khởi tạo đối tượng (constructor), phép **new** và thứ tự chạy các khối lệnh. Khi đọc bytecode, JVM dùng hai tên phương thức đặc biệt:

- **<clinit>** là phương thức khởi tạo **lớp hoặc interface** ở cấp JVM (class/interface initialization method). Trong bytecode do javac tạo, nó chứa mã khởi tạo các trường static và các khối static initializer phải chạy tại thời điểm khởi tạo lớp. JVM quản lý việc gọi nó trong tiến trình khởi tạo lớp.
- **<init>** là phương thức khởi tạo **instance** ở cấp JVM (instance initialization method); mỗi constructor Java tương ứng với một phương thức <init> có descriptor phù hợp. Nó được thực thi khi tạo đối tượng qua constructor, theo chuỗi constructor của lớp và lớp cha.

Hai phương thức cùng có chữ “init”, nhưng khác **đối tượng được khởi tạo**, **điều kiện kích hoạt**, **số lần chạy**, **thứ tự phụ thuộc** và **cách xuất hiện trong bytecode**. Sự phân biệt này rất quan trọng trong chủ đề class loading: JVM có thể tải (load), liên kết (link), thậm chí lấy Class<?> cho một lớp trước khi thực hiện <clinit>. Ngược lại, constructor <init> thuộc giai đoạn khởi tạo một đối tượng cụ thể.

> Ghi nhớ: **<clinit> chuẩn bị trạng thái static của một Class runtime; <init> chuẩn bị trạng thái của từng instance.** Không phải mọi lớp đều có mã <clinit> và không phải mọi lần dùng một tên lớp đều khiến <clinit> chạy.

## 2. So sánh trực tiếp

| Tiêu chí | <clinit> | <init> |
| --- | --- | --- |
| Ngữ nghĩa | Khởi tạo class/interface | Khởi tạo đối tượng |
| Cú pháp nguồn tương ứng | static field initializer và static initializer block | Constructor Java, kết hợp mã khởi tạo instance |
| Tên trong JVM class file | Tên đặc biệt <clinit> | Tên đặc biệt <init> |
| Chữ ký thường gặp | Không tham số, trả void: **()V** | Tham số tùy constructor, trả void; ví dụ **()V**, **(I)V** |
| Số lượng trong một class file | Tối đa một <clinit>; có thể không có | Có thể có nhiều <init> khi overload; lớp thông thường thường có ít nhất một constructor, kể cả mặc định |
| Ai kích hoạt | Cơ chế class/interface initialization của JVM theo các điều kiện đặc tả | Chuỗi gọi constructor do bytecode thực hiện bằng **invokespecial** |
| Số lần chạy | Tối đa một lần thực thi khởi tạo thành công cho từng Class runtime; thất bại đưa lớp sang trạng thái lỗi | Mỗi lần một constructor được gọi trong quá trình tạo instance; có thể lặp nhiều lần |
| Gắn với đối tượng | Không có **this** của instance | Có receiver **this** trong quá trình khởi tạo đối tượng |
| Quan hệ kế thừa | Không kế thừa <clinit>; JVM điều phối khởi tạo các kiểu phụ thuộc | Constructor không được kế thừa; constructor lớp con gọi chuỗi constructor lớp cha |
| Từ Java source có gọi trực tiếp được không? | Không | Gọi bằng **new**, **this(...)**, **super(...)**, hoặc cơ chế reflection/API tương ứng; không viết tên <init> trực tiếp |

Trong bytecode, dấu ngoặc nhọn và hai dấu < > quanh tên là một phần của **tên đặc biệt do JVM định nghĩa**. Chúng không phải những tên phương thức Java mà ta được phép tự khai báo trong source.

## 3. <clinit> được hình thành như thế nào?

Một lớp Java có thể chứa các khai báo sau:

~~~java
class Settings {
    static int port = 8080;           // Khởi tạo trường static bằng biểu thức
    static String host;

    static {                          // Khối static initializer
        host = "localhost";
        System.out.println("Settings initialized");
    }
}
~~~

javac có thể biên dịch các thao tác static có tác dụng lúc chạy thành một phương thức **<clinit>()V**. Khi JVM khởi tạo Settings, nó thực hiện các chỉ thị đó theo thứ tự thích hợp. **Không có hàm main hoặc constructor nào phải gọi <clinit> bằng tên.**

Các phép gán giá trị cho biến static và khối static initializer được thực hiện theo **thứ tự văn bản** trong source đối với các initializer cần thực thi, sau khi áp dụng các quy tắc khởi tạo lớp cha/interface liên quan. Trước khi mã <clinit> chạy, vùng nhớ static đã nhận **giá trị mặc định** tương ứng với kiểu (0, false, null...), trong giai đoạn chuẩn bị (preparation). Do đó:

- static int count; không cần câu lệnh gán 0 tường minh để có giá trị 0 ban đầu.
- static int count = compute(); tạo mã thực thi khi khởi tạo lớp; giá trị được ghi lúc chạy.
- static final int LIMIT = 100; là ví dụ **constant variable** với biểu thức hằng ở thời điểm biên dịch; JVM có thể gán giá trị theo thuộc tính ConstantValue của class file, và javac không cần tạo <clinit> riêng chỉ vì trường ấy.
- static final String ID = makeId(); **không** phải hằng thời điểm biên dịch chỉ vì có static final; lời gọi makeId() cần diễn ra khi lớp được khởi tạo.

Nếu một lớp không có công việc khởi tạo static tại runtime, compiler **có thể không phát sinh <clinit>**. Điều này không có nghĩa lớp chưa từng được JVM khởi tạo; “class initialization” là trạng thái/quy trình của JVM, còn <clinit> là phương thức thực thi khi có mã cần chạy.

**Interface** cũng có thể có <clinit> (chẳng hạn khi trường static có initializer không phải biểu thức hằng). Interface không có instance constructor <init> vì không thể tạo đối tượng interface trực tiếp.

## 4. Constructor <init> được hình thành như thế nào?

Constructor trong Java source:

~~~java
class Account {
    private final String id;

    Account() {
        this("guest");          // Chuyển tới constructor khác cùng lớp
    }

    Account(String id) {
        this.id = id;
    }
}
~~~

Trong class file có các phương thức tên đặc biệt **<init>**, được phân biệt bởi danh sách tham số: **<init>()V** và **<init>(Ljava/lang/String;)V**. Giá trị trả về ở dạng descriptor là V (void); một constructor không thể khai báo kiểu trả về trong Java source.

Một số thuộc tính quan trọng:

1. Một lớp có thể khai báo nhiều constructor overload, tức có nhiều <init>, trong khi chỉ có tối đa một <clinit>.
2. Nếu không khai báo constructor, javac thường tự sinh constructor mặc định phù hợp với quy tắc ngôn ngữ. Điều này **không** tạo thêm <clinit>.
3. Một constructor có thể ủy quyền qua **this(...)** hoặc gọi constructor superclass qua **super(...)** (tường minh hoặc ngầm định). Ở mức bytecode, lời gọi constructor dùng chỉ thị **invokespecial** tới <init> phù hợp.
4. Constructor không được kế thừa. Nếu subclass không khai báo constructor, compiler tạo constructor của subclass để khởi tạo superclass theo đúng điều kiện hợp lệ.
5. Các instance field initializers và instance initializer blocks được biên dịch thành mã trong quá trình chạy <init>. Nếu có chuỗi this(...) trong cùng lớp, các đoạn initializer của lớp ấy chạy **một lần** tại constructor cuối cùng gọi super(...), không chạy lặp ở từng constructor ủy quyền.
6. Không tồn tại API Java source tên Account.<init>(); cách tạo đối tượng thông thường là **new Account()** hoặc **new Account("x")**, với việc cấp phát bộ nhớ và gọi constructor được JVM đảm nhiệm.

Phân biệt thao tác **cấp phát** với **khởi tạo instance**: bộ nhớ cho đối tượng được chuẩn bị với giá trị trường mặc định trước khi mã khởi tạo instance/constructor làm thay đổi các giá trị đó. Việc dùng các API cấp thấp hoặc cơ chế đặc biệt có thể tránh chuỗi constructor thông thường; không dùng ngoại lệ này làm mô hình mặc định khi học Java.

## 5. Ví dụ hoàn chỉnh: <clinit> một lần, <init> cho từng đối tượng

Tạo file **InitOrderDemo.java** với toàn bộ chương trình Java 17+:

~~~java
public class InitOrderDemo {
    static class Target {
        static int staticNumber = trace("S1 - static field");

        static {
            System.out.println("S2 - static block");
        }

        int number = trace("I1 - instance field");

        {
            System.out.println("I2 - instance block");
        }

        Target() {
            System.out.println("I3 - constructor body");
        }
    }

    static int trace(String label) {
        System.out.println(label);
        return 10;
    }

    public static void main(String[] args) {
        System.out.println("M - before first new");
        new Target();

        System.out.println("M - before second new");
        new Target();
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 InitOrderDemo.java
java InitOrderDemo
~~~

**Stdout chính xác:**

~~~text
M - before first new
S1 - static field
S2 - static block
I1 - instance field
I2 - instance block
I3 - constructor body
M - before second new
I1 - instance field
I2 - instance block
I3 - constructor body
~~~

**Giải thích từng giai đoạn:**

- Trước câu lệnh new Target() đầu tiên, lớp Target có thể đã được load/link nhưng chưa nhất thiết initialized. Khi cần tạo instance đầu tiên, JVM bảo đảm Target được khởi tạo. Mã static của Target chạy theo thứ tự **S1 → S2**.
- Sau khi bảo đảm Target đã initialized, JVM thực hiện công việc tạo đối tượng và constructor. Mã instance field initializer, instance initializer block rồi thân constructor chạy theo thứ tự **I1 → I2 → I3**.
- Với new Target() lần hai, Target đã được khởi tạo thành công nên **S1 và S2 không chạy lại**. Một instance mới vẫn chạy **I1 → I2 → I3**.
- Đối tượng InitOrderDemo bao ngoài không được tạo; việc Target là **static nested class** không buộc nó có một instance của lớp bao ngoài. Quan hệ lồng tên không tự động biến một constructor thành class initializer.

Giá trị 10 do trace() trả về được gán lần lượt cho staticNumber và number; ở đây ta quan sát **thời điểm gọi trace**, không cần in giá trị cuối cùng của trường.

## 6. Trigger của `<clinit>` nhìn từ việc tạo object

Một class có thể đã load/link nhưng chưa initialized. `new Target()` yêu cầu class initialization trước constructor nếu lớp chưa khởi tạo; `Target.class` không tự tạo yêu cầu ấy. Đọc constant variable có thể được compiler inline. Bốn lệnh `new`, `getstatic`, `putstatic`, `invokestatic` cùng reflection/MethodHandle được phân tích đầy đủ trong bài riêng về thao tác chủ động kích hoạt.

## 7. Thử phân biệt metadata và instance

`Class.forName("SomeType", false, loader)` có thể trả đối tượng `Class<?>` mà chưa yêu cầu khởi tạo `SomeType`. Nhưng `type.getDeclaredConstructor().newInstance()` thực sự tạo object và đòi initialization trước khi constructor thực hiện. Điều này phân biệt rõ việc **biết thông tin về kiểu** với **khởi tạo một object của kiểu**.

## 8. Kế thừa tạo hai luồng thứ tự riêng

Với `new Child()` khi `Child extends Parent`, JVM initialize `Parent` trước phần static của `Child` nếu cần; sau đó, constructor `Child.<init>` gọi `Parent.<init>` trước phần thân riêng của con. Đây là **hai chuỗi khác nhau**: class initialization liên quan trạng thái static của các lớp, còn constructor chain liên quan từng object. Bài về thứ tự superclass chứa chương trình quan sát static đầy đủ; ví dụ `InitOrderDemo` mục 5 đã làm rõ quá trình theo từng instance.

## 9. Quan sát <clinit> và <init> bằng javap

Sau khi biên dịch ví dụ InitOrderDemo, javac thông thường tạo hai class file:

~~~text
InitOrderDemo.class
InitOrderDemo$Target.class
~~~

Trên PowerShell hoặc shell hiểu ký tự $, đặt tên file/lớp trong **dấu nháy đơn** để tránh bị thay thế biến:

~~~bash
javap -p -c -v 'InitOrderDemo$Target'
~~~

Ở phần constant pool và biểu diễn bytecode chi tiết, hãy tìm các tên đặc biệt:

- **<clinit>:()V**, gắn với các lệnh gọi trace("S1 - static field"), gán Target.staticNumber và in "S2 - static block". Khối static và initializer static được gom theo thứ tự vào một phương thức class initialization.
- **<init>:()V**, gắn với constructor Target(). Bên trong có lời gọi **invokespecial** tới constructor của superclass, sau đó mã khởi tạo trường number, instance block và thân constructor.
- **invokestatic** có thể xuất hiện khi gọi phương thức static trace; **putstatic** khi gán trường staticNumber; **putfield** khi gán trường instance number. Mỗi chỉ thị cho biết loại thao tác, không có nghĩa cứ thấy invokestatic bất kỳ là một class initializer riêng.

Bạn cũng có thể chạy:

~~~bash
javap -p -c InitOrderDemo
~~~

để xem mã trong main. Thao tác **new InitOrderDemo$Target** ở bytecode kết hợp với lời gọi **invokespecial ...<init>** sau khi cấp phát đối tượng. JVM kiểm tra/tiến hành class initialization khi cần trước khi tạo instance; source không hiện ra một lệnh gọi trực tiếp tới <clinit>.

Các dấu mốc bytecode chính:

| Dấu hiệu | Ý nghĩa |
| --- | --- |
| **<clinit>()V** | Mã class/interface initialization khi compiler cần tạo |
| **<init>(...)V** | Một constructor với tham số đúng descriptor |
| **new** | Cấp phát instance của class; nếu cần sẽ kích hoạt class initialization |
| **invokespecial ...<init>** | Gọi constructor cụ thể |
| **getstatic / putstatic** | Truy cập trường static; có thể là điểm kích hoạt initialization |
| **getfield / putfield** | Truy cập trường instance của object |
| **invokestatic** | Gọi phương thức static; có thể là điểm kích hoạt initialization |

**Lưu ý:** javap có thể in constructor dưới dạng tên hiển thị thân thiện (ví dụ InitOrderDemo$Target()) thay vì nguyên văn <init> ở một số dòng, còn tùy chọn -v và constant pool cho thấy tên JVM rõ hơn. Không phụ thuộc chính xác số thứ tự constant pool, offset hoặc cách trình bày của javap qua các bản JDK.

## 10. Phạm vi “một lần” của `<clinit>`

Một runtime class (binary name và defining loader) hoàn tất initialization thành công một lần, được JVM điều phối giữa các thread; một loader khác tự định nghĩa lớp cùng tên có trạng thái riêng. `<init>` không có quy tắc một lần theo class: constructor chạy mỗi khi một instance được tạo thông thường. Chi tiết concurrency và vòng chờ được học tại bài đồng bộ initialization.

## 11. Khi <clinit> lỗi, hệ quả khác constructor lỗi

Nếu code trong <clinit> phát sinh exception, JVM có quy tắc đặc biệt cho initialization failure:

- Nếu throwable là một **Error**, JVM có thể chuyển tiếp chính Error đó.
- Nếu throwable không phải Error (chẳng hạn RuntimeException thoát khỏi static initializer), JVM thường bọc nguyên nhân trong **ExceptionInInitializerError** cho lần thất bại khởi tạo.
- Các lần sử dụng tiếp theo của cùng lớp ở trạng thái erroneous thường gặp **NoClassDefFoundError** báo rằng không thể initialize lớp. Lỗi này **không đồng nghĩa** với không tìm thấy file .class trên classpath.

Ví dụ đoạn source hợp lệ (minh họa, không phải chương trình độc lập):

~~~java
class Broken {
    static final int VALUE = read();

    static int read() {
        throw new IllegalStateException("bad config");
    }
}
~~~

Khi có một active use yêu cầu khởi tạo Broken, lỗi từ read() làm class initialization thất bại; JVM **không chạy lại read()** cho mỗi lần đọc Broken.VALUE về sau. Để phục hồi, chương trình thường cần sửa nguyên nhân và sử dụng một định nghĩa class runtime mới hoặc khởi động lại phần ứng dụng thích hợp.

Ngược lại, nếu **constructor <init>** của một class đã được khởi tạo bình thường ném exception, lần tạo instance ấy thất bại. Điều đó **không tự động biến cả class thành erroneous**: sau khi xử lý nguyên nhân, những lần gọi constructor sau vẫn có thể thành công. Đây là khác biệt rất lớn khi vận hành ứng dụng.

## 12. Ba bẫy khi phân biệt hai phương thức

- `javap` thường hiển thị `<clinit>` dưới nhãn `static {};`, dù trong đó có nhiều field initializer và nhiều khối static.
- Một class có thể không có `<clinit>` nếu không cần công việc static runtime; Java có thể sinh constructor mặc định khi source không khai báo constructor.
- Outer class và nested class là hai runtime classes riêng. Chỉ quan hệ lồng nhau không bảo đảm outer initializer chạy trước initializer của nested class.

## 13. Tìm lỗi theo cấp class hay instance

| Hiện tượng | Vị trí kiểm tra | Hệ quả |
| --- | --- | --- |
| Static field initializer/block ném exception | `<clinit>`, `ExceptionInInitializerError.getCause()`, superclass | Class có thể erroneous và không tự retry |
| Instance field initializer/constructor ném exception | `<init>`, `super(...)`, body constructor | Instance cụ thể thất bại; class không tự động erroneous |
| `javap` hiện nhiều `<init>` | Constructor overload | Không có nghĩa `<clinit>` chạy nhiều lần |

Bài về `ExceptionInInitializerError` đi sâu vào quy tắc lỗi; ở đây hãy xác định **đang khởi tạo class hay đang khởi tạo instance** trước khi chọn cách khắc phục.

## 14. Tự kiểm tra hiểu bài

1. **Hai lần new Target() nhưng một lần in từ static block. Vì sao?** Class initialization thành công một lần cho Class runtime của Target; mỗi instance vẫn chạy constructor.
2. **static final int LIMIT = 5; có nhất thiết tạo <clinit> không?** Không. Đó là constant variable và compiler có thể dùng ConstantValue/inlining; không cần mã runtime chỉ để gán giá trị này.
3. **static final Object VALUE = new Object(); có giống LIMIT không?** Không. new Object() không phải constant expression, nên việc đọc trường static ấy có thể kích hoạt class initialization.
4. **Có thể có <init>()V và <init>(I)V trong một class không?** Có; đó là hai constructor overload. <clinit> thì tối đa một.
5. **Class.forName(name, false, loader) đã có Class<?> thì static block chắc chắn đã chạy chưa?** Chưa; false không yêu cầu initialization. Trạng thái thực tế vẫn có thể là đã initialized do thao tác khác trước đó.
6. **Lỗi constructor có đồng nghĩa lớp bị erroneous không?** Không; lỗi class initialization và lỗi tạo một instance có ngữ nghĩa khác nhau.
7. **Trong new Child(), static block của Parent hay Child chạy trước khi cả hai chưa initialized?** Parent trước Child. Nhưng instance constructor Parent và Child chạy sau giai đoạn initialization thích hợp.
8. **Nếu cùng binary name được định nghĩa bởi hai class loader, <clinit> có thể chạy hai lần trên toàn JVM không?** Có, mỗi Class runtime riêng có chu trình initialization của nó.

## Tài liệu chuẩn

- **JLS §12.4 – Initialization of Classes and Interfaces:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4
- **JLS §12.5 – Creation of New Class Instances:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.5
- **JLS §8.8 – Constructor Declarations:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8
- **JLS §8.7 – Static Initializers:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.7
- **JVM Specification §2.9 – Special Methods:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.9
- **JVM Specification §5.5 – Initialization:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- **Class.forName Java 17 API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-003">Các thao tác chủ động kích hoạt khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Các thao tác chủ động kích hoạt khởi tạo lớp và interface trong Java

## 1. Khởi tạo là giai đoạn nào?

Khi nói JVM **khởi tạo lớp (*class initialization*)**, ta đang nói đến giai đoạn thực thi mã khởi tạo các biến `static` và các khối `static { … }` của một lớp hoặc interface. Trình biên dịch thông thường gom các bước khởi tạo này vào phương thức khởi tạo lớp đặc biệt `<clinit>` trong bytecode; không phải lớp nào cũng cần một `<clinit>`. Giai đoạn này khác với:

- **Loading:** tìm/đọc biểu diễn lớp và tạo đối tượng `Class` tương ứng.
- **Linking:** bao gồm verification, preparation và resolution (có thể được thực hiện theo thời điểm phù hợp). Preparation cấp bộ nhớ cho biến `static` và thiết lập giá trị mặc định. Giá trị từ thuộc tính `ConstantValue` được gán trong **initialization, trước `<clinit>`**, theo JVMS §5.5.
- **Initialization:** thực thi các trình khởi tạo `static` có tác dụng runtime theo thứ tự khai báo, và bảo đảm các quan hệ khởi tạo cần thiết với kiểu cha.

Một lớp đã được *load* hoặc *link* **chưa chắc đã được initialize**. JVM có thể nạp thông tin kiểu trước, rồi hoãn việc chạy mã `static` cho đến khi chương trình thực sự dùng kiểu theo một trong những cách được đặc tả coi là **chủ động sử dụng (*active use*)**.

Trong một JVM, với một lớp cụ thể được định danh bởi **tên nhị phân cùng defining class loader**, việc khởi tạo thành công được thực hiện một lần. Nhiều luồng cùng kích hoạt lớp đó được JVM phối hợp đồng bộ; lần dùng sau không chạy lại các khối `static`.

## 2. Bốn thao tác bytecode chủ yếu buộc JVM khởi tạo

Ở mức JVM, bốn chỉ thị quan trọng nhất là `new`, `getstatic`, `putstatic` và `invokestatic`. Khi chỉ thị được thực thi và lớp/interface đích chưa được khởi tạo, JVM phải thực hiện khởi tạo trước khi thao tác có thể tiếp tục (sau các bước loading/linking cần thiết).

| Chỉ thị JVM | Cách dùng Java thường gặp | Kiểu thực sự được khởi tạo | Ghi nhớ |
| --- | --- | --- | --- |
| `new` | `new Customer()` | Lớp `Customer` được tạo đối tượng, cùng các kiểu cha bắt buộc | Việc khai báo biến tham chiếu đơn thuần không tương đương tạo đối tượng. |
| `getstatic` | `Customer.total` | Lớp/interface **khai báo** trường `static` được resolve | Đọc trường không phải *constant variable* kích hoạt khởi tạo; hằng compile-time thường được inline nên không phát sinh `getstatic`. |
| `putstatic` | `Customer.total = 10` | Lớp/interface **khai báo** trường `static` được resolve | JVM khởi tạo kiểu trước khi phép ghi `static` bên ngoài chạy. |
| `invokestatic` | `Customer.refresh()` | Lớp/interface **khai báo** phương thức `static` được resolve | Khởi tạo diễn ra trước khi thân phương thức `static` được gọi. |

Ví dụ với:

~~~java
class Counter {
    static int count = initialize();
    static int initialize() {
        System.out.println("Counter initialized");
        return 1;
    }
    static void reset() { count = 0; }
}
~~~

Lần đầu thực hiện `Counter.count`, `Counter.count = 9` hoặc `Counter.reset()` sẽ khởi tạo `Counter` trước, nếu kiểu đó chưa được khởi tạo. Nếu chạy lần lượt cả ba thao tác thì thông điệp khởi tạo chỉ xuất hiện một lần. Đoạn trên là lớp minh họa để ghép vào chương trình; ví dụ có đầy đủ `main` và kết quả xác định nằm ở phần 6.

**Chú ý về đích thực sự:** trong `Child.inheritedField`, trường có thể được khai báo trong `Parent`; khi đó chính `Parent` được khởi tạo do phép truy cập trường, còn `Child` không nhất thiết được khởi tạo. Quy tắc tương tự áp dụng với phương thức `static` kế thừa: xét kiểu **khai báo thành viên đã resolve**, không chỉ cách viết bên trái dấu chấm.

### 2.1. `new`: tạo đối tượng lớp

`new Account()` là active use của lớp `Account`. Trước khi đối tượng được tạo theo cách thông thường, JVM phải bảo đảm `Account` đã khởi tạo; nếu lớp có superclass chưa khởi tạo, superclass được khởi tạo trước. Sau đó mới tới logic instance initializer và constructor.

Phân biệt rõ:

- `Account account;` chỉ khai báo biến.
- `Account account = null;` chỉ gán tham chiếu `null`.
- `Account[] accounts = new Account[3];` tạo **array object**, không tự làm khởi tạo lớp phần tử `Account`.
- `new Account()` mới trực tiếp kích hoạt khởi tạo `Account`.

Không suy ra rằng JVM buộc phải khởi tạo mọi lớp được nhắc tên trong một tệp source.

### 2.2. `getstatic`: đọc trường `static` không phải hằng compile-time

Ví dụ:

~~~java
static final int MAX_SIZE = 64;                   // constant variable
static final String PREFIX = "ID-";             // constant variable
static final int PORT = Integer.parseInt("8080"); // giá trị tính ở runtime
static final Integer BOXED = 64;                  // không phải constant variable
static int counter = 1;
~~~

Trong Java, *constant variable* là biến `final` có kiểu nguyên thủy hoặc `String` và được khởi tạo bằng **constant expression**. Biểu thức đọc `MAX_SIZE` hoặc `PREFIX` từ nơi khác thường được biên dịch thành chính giá trị hằng; việc đọc đó **không kích hoạt khởi tạo** lớp khai báo hằng.

Ngược lại, `PORT`, `BOXED` và `counter` không phải constant variable. Dù `PORT` và `BOXED` cũng có `final`, đọc các trường này có thể phát sinh `getstatic` và khởi tạo lớp khai báo. Câu “truy cập mọi `static final` đều không init” là **sai**.

Vì trình biên dịch có thể inline giá trị hằng vào class file của caller, việc thay đổi hằng trong thư viện mà không biên dịch lại caller còn có thể khiến caller dùng giá trị cũ. Đây là một hệ quả của việc nhúng hằng, không phải lỗi class loader.

### 2.3. `putstatic`: ghi trường `static`

Ví dụ `Settings.enabled = true` cần lớp khai báo `enabled` đã khởi tạo trước phép ghi. Nếu trong `Settings` có `static boolean enabled = false;` thì initializer chạy trong giai đoạn khởi tạo, rồi phép gán bên ngoài mới đặt thành `true`. Không nhầm bước khởi tạo mặc định/khởi tạo nội bộ với một thao tác ghi `static` của caller.

### 2.4. `invokestatic`: gọi phương thức `static`

Ví dụ `Registry.open()` kích hoạt khởi tạo kiểu khai báo phương thức `open` trước khi thân hàm chạy. Nó đúng cả khi phương thức không truy cập trường `static` nào.

Lời gọi phương thức *instance* thông thường qua một object đã tồn tại không phải một nguyên nhân chủ động mới để khởi tạo kiểu: đối tượng của một lớp được tạo theo cách chuẩn vốn đã đòi hỏi lớp đó được khởi tạo.

## 3. Những thao tác qua reflection và MethodHandle

Ngoài cú pháp Java thông thường, một số API gọi động cũng chủ động kích hoạt khởi tạo:

| Thao tác | Kích hoạt khởi tạo? | Điều kiện/giải thích |
| --- | --- | --- |
| `Class.forName("com.example.Plugin")` | **Có** | Overload một đối số yêu cầu khởi tạo lớp được tìm thấy. |
| `Class.forName(name, true, loader)` | **Có** | `initialize=true` yêu cầu khởi tạo khi lớp chưa khởi tạo. |
| `Class.forName(name, false, loader)` | **Không chỉ bởi lời gọi này** | Tìm/nạp lớp mà không yêu cầu chạy các initializer. |
| `SomeType.class` | **Không** | Lấy đối tượng `Class` không tự kích hoạt khởi tạo kiểu tương ứng. |
| `SomeType.class.getDeclaredMethods()` | **Thông thường không** | Chỉ xem metadata của kiểu; khác với gọi phương thức `static` đã tìm thấy. |
| `SomeType.class.getDeclaredConstructor().newInstance()` | **Có** | Reflection thực sự tạo instance của lớp. |
| `Method.invoke(null, …)` cho phương thức `static` | **Có** | Gọi phương thức `static` của kiểu khai báo. |
| `Field.get(null)` / `Field.set(null, value)` cho trường `static` | **Có** | Truy cập hoặc ghi trường `static` qua reflection; khác với lấy metadata `Field`. |
| `ClassLoader.loadClass(name)` theo triển khai chuẩn | **Không tự yêu cầu initialization** | Chủ yếu nạp/trả về `Class`; custom loader vẫn có thể tự chạy thêm logic. |

Ví dụ muốn khám phá plugin mà chưa chạy static initializer thì thường bắt đầu bằng `Class.forName(name, false, loader)`, sau đó cân nhắc thời điểm khởi tạo thật sự. Ngược lại, `Class.forName(name)` có thể thực thi code trong `static { … }` ngay lập tức, nên không phù hợp nếu mục tiêu chỉ là đọc metadata mà chưa kích hoạt lớp.

Ở tầng JVM, **lần gọi đầu tiên** của một `MethodHandle` được tạo từ quá trình resolve tham chiếu có các reference kind `REF_getStatic`, `REF_putStatic`, `REF_invokeStatic` hoặc `REF_newInvokeSpecial` cũng có thể kích hoạt khởi tạo kiểu đích. Phân biệt thao tác chỉ *tìm/tạo handle* với thao tác thật sự *invoke handle*; thời điểm và điều kiện chính xác được định nghĩa tại JVMS §5.5. Khi gọi bootstrap method của `invokedynamic`, việc thực thi bootstrap đó cũng phải tuân thủ quy tắc khởi tạo tương ứng.

Ngoài ra, JVM khởi tạo lớp được chọn làm điểm bắt đầu chương trình trước khi gọi `main`, và một số lời gọi JNI/phần phản chiếu từ native code cũng có thể yêu cầu khởi tạo. Vì vậy danh sách các câu lệnh Java trong một file không phải toàn bộ đường vào của quá trình này.

## 4. Khởi tạo bắc cầu qua quan hệ kế thừa

Khi **một class** được khởi tạo, JVM bảo đảm:

1. Superclass chưa khởi tạo được khởi tạo **trước** subclass.
2. Những superinterface trong quan hệ kế thừa thích hợp **có khai báo default method** cũng được khởi tạo theo thuật toán của JVM; có thể bao gồm default method do compiler tạo.
3. Sau các bước tiên quyết, initializer `static` của lớp đang khởi tạo mới chạy.

Không được suy ra rằng **mọi interface mà một lớp `implements` đều khởi tạo ngay**. Một interface không có default method thường không phải được khởi tạo chỉ vì lớp triển khai được khởi tạo. Mặt khác, việc khởi tạo **một interface** không tự động khởi tạo tất cả superinterface của nó.

Ví dụ ý nghĩa với khai báo:

~~~java
class Parent {
    static int total = 4;
}
class Child extends Parent {
    static { System.out.println("Child initialized"); }
}
~~~

Nếu lần đầu dùng là `Child.total`, chính `Parent` khai báo trường `total` nên `Parent` được khởi tạo, nhưng `Child` **không bị khởi tạo chỉ vì cú pháp `Child.total`**. Nếu sau đó `new Child()`, `Child` mới được khởi tạo; `Parent` không khởi tạo lần hai.

Nguyên tắc *only the declaring type* cũng áp dụng cho việc đọc trường `static` của interface qua tên của subinterface hoặc lớp triển khai. Khi làm bài tập, hãy xác định nơi thành viên được **khai báo thực sự** rồi mới kết luận kiểu nào chạy `<clinit>`.

## 5. Những trường hợp tưởng là active use nhưng không phải

| Hành động | Có khởi tạo `Target` chỉ từ hành động đó không? | Vì sao? |
| --- | --- | --- |
| `Target t = null;` | Không | Chỉ tạo/gán tham chiếu. |
| `Target.class` | Không | Có `Class` object không đồng nghĩa với chạy static initializer. |
| `new Target[5]` | Không | Tạo array của tham chiếu, không tạo `Target` instance. |
| `obj instanceof Target` hoặc `(Target) obj` | Không | Thử/ép kiểu không phải một trong bốn lệnh active initialization chính. |
| `ClassLoader.loadClass("Target")` | Không theo hợp đồng loadClass chuẩn | Loading khác initialization. |
| Đọc `Target.CONST` khi `CONST` là constant variable | Không | Giá trị thường được chèn ngay vào bytecode bên gọi. |
| Lấy `Field`/`Method` metadata qua reflection | Không chỉ vì lấy metadata | Việc đọc/ghi trường hoặc invoke phương thức static sau đó mới là trường hợp kích hoạt. |
| Đọc `Child.parentField` kế thừa từ `Parent` | Không đối với `Child` | Chỉ kiểu thực sự khai báo trường là đích khởi tạo của `getstatic`. |

Kết luận “không” ở bảng này là **đối với chính thao tác được xét**. Các mã chạy xung quanh, API bổ sung, hoặc cơ chế custom class loader vẫn có thể tạo hiệu ứng phụ và tự khởi tạo lớp trong lúc xử lý. Một lớp cũng có thể đã được khởi tạo trước đó từ thao tác khác.

## 6. Thí nghiệm Java 17+ kiểm chứng các trường hợp

Lưu tệp **`ActiveInitDemo.java`** sau ở một thư mục riêng, chạy bằng JDK 17 trở lên. Ví dụ đặt tất cả kiểu trợ giúp vào một lớp bên ngoài để có thể biên dịch và chạy độc lập.

~~~java
public class ActiveInitDemo {
    static class Constants {
        static { System.out.println("init Constants"); }
        static final int COMPILE_TIME = 7;
        static final int RUNTIME = Integer.parseInt("9");
    }

    static class OnWrite {
        static { System.out.println("init OnWrite"); }
        static int count = 1;
    }

    static class OnCall {
        static { System.out.println("init OnCall"); }
        static void ping() { System.out.println("ping"); }
    }

    static class Parent {
        static { System.out.println("init Parent"); }
        static int inherited = 4;
    }

    static class Child extends Parent {
        static { System.out.println("init Child"); }
    }

    interface WithDefault {
        int MARKER = announce();
        static int announce() {
            System.out.println("init WithDefault");
            return 1;
        }
        default void action() {}
    }

    static class ImplementsDefault implements WithDefault {
        static { System.out.println("init ImplementsDefault"); }
    }

    static class Reflective {
        static { System.out.println("init Reflective"); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. passive");
        Class<?> type = Constants.class;
        System.out.println("constant=" + Constants.COMPILE_TIME);
        Class.forName(type.getName(), false, type.getClassLoader());

        System.out.println("2. getstatic");
        System.out.println("runtime=" + Constants.RUNTIME);

        System.out.println("3. putstatic");
        OnWrite.count = 5;
        System.out.println("count=" + OnWrite.count);

        System.out.println("4. invokestatic");
        OnCall.ping();

        System.out.println("5. inherited static");
        System.out.println("inherited=" + Child.inherited);

        System.out.println("6. new Child");
        new Child();

        System.out.println("7. new ImplementsDefault");
        new ImplementsDefault();

        System.out.println("8. reflection");
        Class.forName(Reflective.class.getName());
        System.out.println("done");
    }
}
~~~

Chạy trong thư mục chứa source:

~~~bash
javac ActiveInitDemo.java
java ActiveInitDemo
~~~

Kết quả stdout (đã chạy và đối chiếu):

~~~text
1. passive
constant=7
2. getstatic
init Constants
runtime=9
3. putstatic
init OnWrite
count=5
4. invokestatic
init OnCall
ping
5. inherited static
init Parent
inherited=4
6. new Child
init Child
7. new ImplementsDefault
init WithDefault
init ImplementsDefault
8. reflection
init Reflective
done
~~~

**Đọc kết quả theo trình tự:**

1. Phần `passive` lấy `Constants.class`, đọc hằng `COMPILE_TIME` và dùng `Class.forName(..., false, ...)`. Không dòng nào in `init Constants`.
2. Đọc `Constants.RUNTIME` (một `static final int` *không* phải constant variable) buộc khởi tạo `Constants`; initializer in `init Constants` trước khi mã `main` in giá trị `9`.
3. Ghi `OnWrite.count = 5` buộc khởi tạo `OnWrite` trước; giá trị `count` sau đó là `5`. Lần đọc tiếp không chạy lại initializer.
4. Gọi `OnCall.ping()` buộc khởi tạo `OnCall` rồi mới in `ping`.
5. Truy cập `Child.inherited` khởi tạo `Parent`, vì trường đó khai báo tại `Parent`. Nó **chưa** in `init Child`.
6. `new Child()` mới khởi tạo `Child`; `Parent` đã khởi tạo nên không in lần hai.
7. `ImplementsDefault` có superinterface `WithDefault` khai báo default method `action()`. JVM khởi tạo `WithDefault` trước `ImplementsDefault`, cho thấy đây là quy tắc khởi tạo phụ thuộc khi khởi tạo class.
8. `Class.forName(Reflective.class.getName())` yêu cầu khởi tạo `Reflective`, vì vậy thông điệp xuất hiện trước `done`.

Trong ví dụ trên, các lớp phụ trợ lần đầu được dùng theo đúng thứ tự để việc quan sát độc lập và dễ kiểm chứng. Nếu đổi thứ tự các thao tác, một lớp đã được khởi tạo ở bước trước sẽ không chạy lại initializer, khiến đầu ra khác.

## 7. Quy trình khởi tạo và lỗi dễ gặp

Khi một thao tác đòi hỏi khởi tạo kiểu `T`, JVM dùng trạng thái khởi tạo của chính kiểu đó để bảo đảm hành vi đúng cho nhiều luồng:

~~~text
Thao tác active use đối với T
          |
          v
T đã khởi tạo thành công? ---- Có ----> Tiếp tục thao tác
          |
         Chưa
          |
          v
T đang được luồng khác khởi tạo?
          |                           \
         Có                           Không
          |                             |
          v                             v
Chờ kết quả theo quy tắc JVM    Khởi tạo kiểu cha liên quan
                                        |
                                        v
                                Chạy initializer của T
                                        |
                            +-----------+-----------+
                            |                       |
                         Thành công               Thất bại
                            |                       |
                            v                       v
                       Đánh dấu xong        T ở trạng thái lỗi
~~~

Chi tiết thuật toán đồng bộ và xử lý khởi tạo đệ quy nằm trong JLS §12.4.2 và JVMS §5.5; sơ đồ chỉ nêu những giai đoạn cần hiểu.

- **Khối `static` ném exception:** khi initializer thất bại, JVM đánh dấu kiểu ở trạng thái lỗi. Lần khởi tạo gây lỗi có thể ném `ExceptionInInitializerError` (nếu nguyên nhân là ngoại lệ không phải `Error`); lần dùng sau có thể nhận `NoClassDefFoundError` vì lớp đã không thể khởi tạo. Đó là lỗi *initialization*, khác với lỗi class file không tìm thấy.
- **Dùng biến `static` trong chu trình khởi tạo:** một initializer có thể gọi sang kiểu khác rồi vòng lại kiểu ban đầu. Luồng đang khởi tạo có thể quan sát trường chưa được gán giá trị cuối cùng, ví dụ `0` hoặc `null`; tránh thiết kế các trình khởi tạo phụ thuộc vòng.
- **I/O, network hoặc logic nặng trong `static`:** việc chỉ gọi `Class.forName(name)` hay một method `static` đơn giản có thể phát sinh công việc ngoài ý muốn hoặc failure tại thời điểm runtime; hãy giữ side effect có kiểm soát.
- **Đo thời điểm bằng log loading:** `-verbose:class` hoặc `-Xlog:class+load=info` cho biết class loading, không chứng minh được thời điểm chạy `<clinit>`. Dùng thông điệp trong initializer hoặc công cụ chuyên dụng để quan sát initialization.
- **Hiểu sai về hằng:** chỉ *constant variable* theo JLS mới có quy tắc đọc không khởi tạo như đã mô tả. Có `static final` không đồng nghĩa với compile-time constant.
- **Hiểu sai đích khởi tạo qua kế thừa:** hãy xác định nơi member được khai báo hoặc loại lớp đang thực sự được tạo, không suy đoán từ tên phía trước dấu chấm.

## 8. Checklist suy luận một biểu thức có kích hoạt khởi tạo không

Khi gặp một biểu thức Java hoặc đoạn bytecode, lần lượt xét:

1. JVM có đang thực thi `new`, `getstatic`, `putstatic` hoặc `invokestatic` không? Nếu là truy cập hằng `static final`, cần kiểm tra xem trình biên dịch có dùng literal inline thay vì `getstatic` hay không.
2. Nếu là truy cập thành viên `static`, **lớp/interface nào khai báo trường hoặc phương thức sau khi resolve**?
3. Nếu là reflection, đang chỉ đọc metadata hay thật sự yêu cầu initialization, tạo instance, gọi phương thức hoặc đọc/ghi trường `static`? Overload `Class.forName` có đối số `initialize` bằng gì?
4. Nếu là `MethodHandle`, đang lấy handle hay đã invoke handle thuộc reference kind có thể kích hoạt?
5. Kiểu đích đã khởi tạo chưa? Nếu đã xong, initializer không chạy thêm lần nữa. Nếu chưa, superclass và các superinterface có default method liên quan có thể phải khởi tạo trước.
6. Đừng suy đoán chỉ từ việc một class đã được load, có `Class` object, được nói đến trong signature, hoặc có một mảng của kiểu đó.

**Tự kiểm tra:** Hai câu lệnh `Plugin.class.getDeclaredMethods()` và `Class.forName("com.example.Plugin")` có cùng tác động với static initializer của `Plugin` không? **Không.** Trường hợp đầu chỉ truy vấn metadata; trường hợp sau (với overload một tham số và tên lớp hợp lệ) yêu cầu khởi tạo nếu `Plugin` chưa khởi tạo.

## Tài liệu chuẩn

- [JLS Java SE 21, §12.4.1 – When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.1): liệt kê active use ở mức ngôn ngữ, hằng compile-time, class/interface, default methods và reflection.
- [JLS Java SE 21, §12.4.2 – Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.2): đồng bộ, khởi tạo lặp và xử lý thất bại.
- [JVMS Java SE 21, §5.5 – Initialization](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.5): `new`, `getstatic`, `putstatic`, `invokestatic`, `MethodHandle` và hành vi JVM.
- [JDK 21, Class.forName](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): phân biệt `initialize=true` và `false`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-004">Các thao tác không kích hoạt khởi tạo: class literal, mảng và hằng biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Các thao tác không kích hoạt khởi tạo lớp: class literal, mảng và hằng biên dịch

## 1. Bài học giải quyết vấn đề gì?

Trong Java, **JVM biết về một lớp** không nhất thiết đồng nghĩa với việc **lớp đó đã được khởi tạo**. Ba trường hợp hay gây nhầm lẫn là:

1. Truy cập **class literal** như `Example.class`.
2. Tạo **mảng có kiểu phần tử là lớp** như `new Example[5]`.
3. Đọc **hằng có thể xác định lúc biên dịch** như `Example.MAX_SIZE` nếu đó là *constant variable* đúng nghĩa.

**Cả ba thao tác trên, xét riêng bản thân chúng, đều không kích hoạt initialization của `Example`.** Tuy vậy, JVM vẫn có thể đã load/link `Example`. Và nếu một biểu thức liên quan thực hiện thêm thao tác “active use” (chẳng hạn `new Example()` hoặc đọc một trường `static` không phải hằng), lớp vẫn có thể được khởi tạo ngay lúc đó.

Mục tiêu sau bài: nhận diện đúng ba trường hợp không kích hoạt, phân biệt `static final` với **constant variable**, dự đoán được output của ví dụ Java 17+ và không nhầm **loading** với **initialization**.

## 2. Nền tảng: loading, linking và initialization khác nhau

Khi JVM làm việc với một kiểu:

- **Loading:** tìm/định nghĩa biểu diễn runtime của lớp từ dữ liệu lớp. Một đối tượng `Class<?>` tương ứng có thể được JVM sử dụng.
- **Linking:** xác minh (`verification`), chuẩn bị (`preparation`), giải quyết tham chiếu biểu tượng (`resolution`). Một số việc phân giải có thể xảy ra muộn.
- **Initialization:** thực thi logic khởi tạo biến `static` và các khối `static {}` của lớp theo quy tắc Java/JVM.

Ví dụ mã minh họa:

~~~java
class Sample {
    static {
        System.out.println("Sample initialized");
    }
}
~~~

Nhìn thấy `Sample.class` hoặc thấy JVM đã nạp bytecode chưa đủ để kết luận `Sample initialized` đã được in. Chỉ khi `Sample` được **khởi tạo** thì khối `static` nói trên mới được thực thi; trong một định nghĩa lớp đã khởi tạo bình thường, khối này không chạy lại mỗi lần truy cập.

Theo [JLS 12.4.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.1), một lớp thường bị kích hoạt khởi tạo khi lần đầu có thao tác như:

- `new Sample()`: tạo **instance** `Sample`.
- `Sample.run()`: gọi phương thức `static` **thực sự được khai báo bởi `Sample`**.
- Gán một trường `static` được khai báo bởi `Sample`.
- Đọc một trường `static` được khai báo bởi `Sample` **nếu đó không phải constant variable**.
- Một số cơ chế đặc biệt/reflection, ví dụ overload `Class.forName(String)` yêu cầu initialization.

Khi lớp con được khởi tạo, superclass của nó cũng phải được khởi tạo theo quy tắc. Với trường tĩnh kế thừa, phải quan tâm **lớp khai báo trường**, chứ không chỉ tên lớp ghi bên trái dấu chấm.

**Giới hạn chính xác của bài:** “Không kích hoạt” chỉ có nghĩa **thao tác đang xét không tự yêu cầu khởi tạo lớp đích**. Nó không chứng minh lớp chưa được load/link, chưa từng được khởi tạo trước đó, hoặc không có lớp khác được khởi tạo như hệ quả của việc tính toán biểu thức.

## 3. Class literal: `Sample.class` không khởi tạo `Sample`

### 3.1. Lấy metadata không phải tạo instance

~~~java
Class<Sample> info = Sample.class;
System.out.println(info.getName());
~~~

`Sample.class` là **class literal**: nó cung cấp một đối tượng `Class<Sample>` mô tả kiểu. Thao tác này **không tự kích hoạt** khối `static {}` của `Sample`. Việc in tên qua metadata cũng không buộc phải khởi tạo `Sample`.

So sánh:

~~~java
Sample object = new Sample(); // kích hoạt khởi tạo Sample nếu chưa xảy ra
~~~

`new Sample()` tạo instance; đây là một trong các thao tác active use. Khác biệt nằm ở **lấy metadata** và **tạo đối tượng của lớp**.

### 3.2. Bảng phân biệt các biểu thức gần giống

| Biểu thức | Có tự yêu cầu khởi tạo lớp `Sample`? | Lý do |
|---|---|---|
| `Sample.class` | **Không** | Class literal chỉ tham chiếu metadata về kiểu |
| `Sample[].class` | **Không** | Class literal cho kiểu mảng, không tạo instance `Sample` |
| `Class.forName("Sample", false, loader)` | **Không yêu cầu** | `false` nói rõ không yêu cầu initialization |
| `Class.forName("Sample")` | **Có** | Overload một tham số yêu cầu initialization |
| `Class.forName("Sample", true, loader)` | **Có** | Cờ `true` yêu cầu initialization |
| `new Sample()` | **Có** | Tạo instance |
| `Sample.action()` nếu `action` là static method khai báo ở `Sample` | **Có** | Gọi phương thức static của lớp |

Với lớp có package, `Class.forName` cần **binary name** phù hợp, ví dụ `com.example.Sample`. Với lớp lồng tĩnh, tên nhị phân dùng `$`. Trong khi đó, `Sample.class` được compiler hiểu trực tiếp từ mã nguồn.

### 3.3. Chỗ dễ suy luận sai

- **“`Sample.class` chưa chạy `static`, vậy chưa load.”** Không có cơ sở. Loading và initialization là hai việc khác nhau.
- **“Có một đối tượng `Class<?>` là đã có instance `Sample`.”** Sai. `Class<?>` đại diện metadata của kiểu, không phải instance mà `new Sample()` tạo ra.
- **“`Class.forName` lúc nào cũng chỉ load.”** Sai: overload và cờ `initialize` quyết định yêu cầu initialization. Đừng dùng `Class.forName("Sample")` để minh họa trường hợp *không* khởi tạo.

## 4. Tạo mảng không đồng nghĩa khởi tạo lớp phần tử

### 4.1. Một mảng chỉ cấp chỗ chứa tham chiếu

~~~java
Sample[] samples = new Sample[3];
System.out.println(samples.length); // 3
~~~

Biểu thức `new Sample[3]` tạo **một đối tượng mảng** gồm ba vị trí tham chiếu, tất cả ban đầu đều là `null`:

~~~text
samples: Sample[]
+-----------+-----------+-----------+
| null      | null      | null      |
+-----------+-----------+-----------+
     [0]         [1]         [2]
~~~

Nó **không** gọi `new Sample()` ba lần. Vì vậy, tạo mảng không tự kích hoạt initialization của lớp thành phần `Sample`.

Tương tự, hai biểu thức sau không tự khởi tạo lớp `Sample`:

~~~java
Sample[][] grid = new Sample[2][3];
Class<?> arrayType = Sample[].class;
~~~

JVM có thể tạo hoặc tìm runtime class cho kiểu mảng và thông tin lớp phần tử, nhưng đó không phải khởi tạo `Sample`. Không nên lấy thời điểm array class được tạo để suy ra thời điểm khối `static {}` của `Sample` chạy.

### 4.2. Khi có instance trong biểu thức tạo mảng

~~~java
Sample[] refs = new Sample[2];               // không khởi tạo Sample
Sample[] withObject = {new Sample(), null};  // khởi tạo Sample
~~~

Trong dòng thứ hai, **`new Sample()`** là thao tác kích hoạt, không phải dấu ngoặc của array initializer.

Tương tự:

~~~java
Sample[] data = new Sample[Sample.size()];
~~~

Nếu `size()` là phương thức static khai báo bởi `Sample`, việc gọi `Sample.size()` có thể kích hoạt initialization trước khi mảng được cấp phát. **Đánh giá các biểu thức bên trong câu lệnh** phải được tách khỏi quy tắc khởi tạo do chính `new Sample[...]`.

### 4.3. Cách tự quan sát

Đặt `System.out.println("INIT Sample")` trong `static {}` của `Sample`. Chạy một JVM mới và thực hiện **chỉ** `Sample[] a = new Sample[2];`, in `a.length`; dự đoán không thấy `INIT Sample`. Sau đó thêm `a[0] = new Sample();`: khi chưa có hoạt động khởi tạo nào trước đó, dòng `INIT Sample` xuất hiện lúc tạo instance.

## 5. Đọc hằng biên dịch không khởi tạo lớp khai báo

### 5.1. Định nghĩa đúng của constant variable

Không phải bất kỳ `static final` nào cũng là hằng biên dịch. Theo [JLS 4.12.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4), một **constant variable** phải đồng thời:

1. Là biến có `final`.
2. Có kiểu **primitive** như `int`, `boolean`, `long`, `char` hoặc **`String`**.
3. Được gán giá trị bằng **constant expression** (xem [JLS 15.29](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.29)).

Trong thực tế, ta thường gặp **`static final` constant variable** được công khai trong một lớp hoặc interface. Compiler có thể nhúng trực tiếp giá trị tại vị trí sử dụng; đọc trường hằng này không tự kích hoạt initializer của lớp khai báo.

~~~java
class Limits {
    static {
        System.out.println("INIT Limits");
    }

    static final int MAX = 2 + 3;           // constant variable
    static final String PREFIX = "ja" + "va"; // constant variable
}
~~~

`System.out.println(Limits.MAX)` sẽ in `5`; `System.out.println(Limits.PREFIX)` sẽ in `java`. Chỉ riêng hai lần đọc constant variable **không** khiến `INIT Limits` xuất hiện.

### 5.2. So sánh từng loại static field

| Khai báo trường tại `Limits` | Constant variable? | Đọc trường có tự kích hoạt `Limits`? |
|---|---|---|
| `static final int MAX = 5;` | Có | **Không** |
| `static final long MASK = 1L << 3;` | Có | **Không** |
| `static final String CODE = "A" + "B";` | Có | **Không** |
| `static final boolean READY = true;` | Có | **Không** |
| `static final Integer BOXED = 5;` | **Không** — `Integer` là reference type | **Có** |
| `static final int DYNAMIC = Integer.parseInt("5");` | **Không** — method call không phải constant expression | **Có** |
| `static final String UUID = System.getProperty("id");` | **Không** — method call | **Có** |
| `static final String X = new String("x");` | **Không** — `new` không phải constant expression | **Có** |
| `static final int[] VALUES = {1, 2};` | **Không** — kiểu mảng | **Có** |
| `static int mutable = 5;` | **Không** — không có `final` | **Có** |

Đừng nhầm “được gán một lần”, “giá trị cố định trong ví dụ” hoặc “có modifier `final`” với định nghĩa *constant variable*.

### 5.3. Interface và trường khai báo tại superclass

Một trường khai báo trong interface được ngầm định là `public static final`, nhưng chỉ là *constant variable* nếu giá trị là constant expression:

~~~java
interface Options {
    int PORT = 8080;                           // constant variable
    String MODE = System.getProperty("mode");   // không là constant variable
}
~~~

Đọc `Options.PORT` không tự khởi tạo `Options`; đọc `Options.MODE` là active use của một nonconstant static field và có thể kích hoạt khởi tạo interface.

Với kế thừa:

~~~java
class Parent {
    static int count = 1;
}
class Child extends Parent {}
~~~

Viết `Child.count` thực ra là tham chiếu trường **được khai báo bởi `Parent`**. Nếu chưa được khởi tạo, thao tác đọc nonconstant field này khởi tạo `Parent`; không vì cách viết `Child.count` mà tự động khởi tạo `Child`.

### 5.4. Rủi ro khi thay đổi public compile-time constant

Nếu thư viện xuất bản:

~~~java
public static final int API_VERSION = 1;
~~~

client đã biên dịch có thể lưu trực tiếp literal `1` vào bytecode. Sau này thư viện đổi trường thành `2` nhưng client **chưa được biên dịch lại**: client vẫn có thể in `1` ngay cả khi đang dùng JAR thư viện mới.

Đây là hệ quả của **compile-time inlining** và binary compatibility ([JLS 13.4.9](https://docs.oracle.com/javase/specs/jls/se21/html/jls-13.html#jls-13.4.9)), không phải ClassLoader “giữ nhầm giá trị”. Khi thay hằng công khai có client phụ thuộc, kiểm tra và biên dịch lại client khi cần.

## 6. Thực hành Java 17+ chạy độc lập

Tạo đúng tệp **`NoInitTriggersDemo.java`**. Mọi lớp cần thiết đều nằm trong một tệp:

~~~java
public class NoInitTriggersDemo {
    static class FromLiteral {
        static { System.out.println("INIT FromLiteral"); }
    }

    static class ArrayElement {
        static { System.out.println("INIT ArrayElement"); }
    }

    static class CompileTimeConstants {
        static { System.out.println("INIT CompileTimeConstants"); }
        static final int RETRIES = 2 + 3;
        static final String MODE = "safe";
        static final Integer BOXED = 10;
    }

    static class ComputedValue {
        static { System.out.println("INIT ComputedValue"); }
        static final int COUNT = Integer.parseInt("9");
    }

    public static void main(String[] args) {
        System.out.println("A");
        Class<?> type = FromLiteral.class;
        System.out.println(type.getSimpleName());

        System.out.println("B");
        ArrayElement[] array = new ArrayElement[3];
        System.out.println(array.length);

        System.out.println("C");
        System.out.println(CompileTimeConstants.RETRIES);
        System.out.println(CompileTimeConstants.MODE);

        System.out.println("D");
        System.out.println(ComputedValue.COUNT);

        System.out.println("E");
        System.out.println(CompileTimeConstants.BOXED);

        System.out.println("F");
        new FromLiteral();
        new ArrayElement();
    }
}
~~~

Biên dịch/chạy từ thư mục chứa tệp:

~~~sh
javac NoInitTriggersDemo.java
java NoInitTriggersDemo
~~~

**Stdout chính xác** (đã kiểm tra bằng `javac --release 17`):

~~~text
A
FromLiteral
B
3
C
5
safe
D
INIT ComputedValue
9
E
INIT CompileTimeConstants
10
F
INIT FromLiteral
INIT ArrayElement
~~~

### Giải thích đúng điểm khởi tạo

| Nhóm dòng | Thao tác | Vì sao chưa/đã khởi tạo |
|---|---|---|
| `A → FromLiteral` | `FromLiteral.class` và đọc tên metadata | Chưa có active use khiến `FromLiteral` initialization |
| `B → 3` | `new ArrayElement[3]` | Tạo mảng `null`; không tạo `ArrayElement` |
| `C → 5 / safe` | Đọc `RETRIES` và `MODE` | Hai trường là constant variables nên không tự khởi tạo `CompileTimeConstants` |
| `D → INIT ComputedValue → 9` | Đọc `COUNT` | `Integer.parseInt` khiến `COUNT` không là constant variable; `getstatic` kích hoạt khởi tạo |
| `E → INIT CompileTimeConstants → 10` | Đọc `BOXED` | `Integer` là reference type, do đó `BOXED` không là constant variable |
| `F → INIT FromLiteral / INIT ArrayElement` | Hai biểu thức `new` | Tạo instance của mỗi lớp, kích hoạt initialization nếu lớp chưa init |

Quan sát quan trọng nhất là `INIT CompileTimeConstants` **không xuất hiện khi đọc hai hằng `RETRIES`/`MODE`**, mà chỉ xuất hiện khi đọc `BOXED`. `FromLiteral` cũng chỉ init khi tạo instance ở nhóm `F`, dù `FromLiteral.class` đã được lấy từ nhóm `A`.

### Kiểm chứng ở mức bytecode

~~~sh
javap -c -p NoInitTriggersDemo
~~~

Hãy tìm trong phương thức `main`:

- `ldc` cho class literal và literal chuỗi.
- `anewarray` cho thao tác cấp phát mảng tham chiếu.
- Giá trị `RETRIES` và `MODE` được dùng trực tiếp tại vị trí gọi; không cần `getstatic` của hai constant variables đó.
- `getstatic` đối với `ComputedValue.COUNT` và `CompileTimeConstants.BOXED`.

Các opcode quan sát với `javac` giúp hiểu lý do code hoạt động; **đặc tả JLS** mới là nguồn quy tắc về thời điểm initialization. Không kết luận mọi chi tiết JVM từ một opcode đơn lẻ.

## 7. Thực nghiệm nhỏ để phân biệt nguyên nhân

**Thí nghiệm 1 — Mảng có phần tử khởi tạo ngay:** đổi `new ArrayElement[3]` thành `new ArrayElement[]{new ArrayElement(), null, null}`. Dòng `INIT ArrayElement` chuyển lên nhóm `B`, vì đã có `new ArrayElement()` trong biểu thức phần tử.

**Thí nghiệm 2 — Đổi nonconstant thành constant:** đổi `static final int COUNT = Integer.parseInt("9");` thành `static final int COUNT = 9;` rồi **biên dịch lại**. Lần đọc `COUNT` không còn tự kích hoạt initializer; `INIT ComputedValue` biến mất khỏi chương trình này.

**Thí nghiệm 3 — Chỉ thay `class literal` bằng instance:** giữ `FromLiteral.class`, thêm `new FromLiteral();` ngay bên dưới. Lớp `FromLiteral` được khởi tạo tại dòng tạo instance đó; class literal vẫn không phải tác nhân kích hoạt.

**Thí nghiệm 4 — Reflection:** so `Class.forName("NoInitTriggersDemo$FromLiteral", false, loader)` với cờ `true` (nơi `loader` là defining loader của `NoInitTriggersDemo`). Overload cờ `false` không yêu cầu initialization; cờ `true` yêu cầu. Hãy đặt phép thử trước mọi `new FromLiteral()` để quan sát khác biệt.

**Lưu ý quan sát:** khởi chạy **JVM mới** cho mỗi trường hợp cần trạng thái lớp chưa khởi tạo. Việc lặp lại các phép thử trong cùng JVM sau khi đã init không làm `static {}` in lần nữa, dễ gây kết luận sai.

## 8. Những nhầm lẫn cần tránh

1. **“Không init” nghĩa là “không load”.** Sai: class literal và array allocation có thể buộc JVM làm việc với thông tin lớp mà không khởi tạo lớp đích.
2. **“`new Sample[3]` tạo ba `Sample`.”** Sai: nó chỉ tạo một mảng ba slot `null`.
3. **“Mảng chứa `new Sample()` nên bản thân việc tạo mảng gây init.”** Sai: chính phép tạo **instance** gây init.
4. **“`final` hoặc `static final` nào cũng được inline.”** Sai: primitive/`String` và constant expression là các tiêu chí bắt buộc.
5. **“`Integer` là số, vì vậy `static final Integer` là constant variable.”** Sai: `Integer` là reference type.
6. **“Tên bên trái dấu chấm quyết định chính xác lớp phải khởi tạo.”** Sai với trường kế thừa; cần nhìn lớp thực sự **khai báo** trường.
7. **“Interface field đều không kích hoạt khởi tạo.”** Sai: một interface field không phải constant variable có thể khiến interface được khởi tạo khi đọc.
8. **“`Class.forName` luôn giống `Target.class`.”** Sai: overload một tham số yêu cầu initialization.
9. **“Không thấy log initializer tức lớp chưa từng init.”** Sai nếu lớp đã init trước đó; phải quan sát trên JVM mới và gắn log cho đúng lớp.
10. **“Đổi giá trị constant trong JAR thì mọi client tự nhận giá trị mới.”** Sai nếu client đã inline và chưa được biên dịch lại.

## 9. Câu hỏi ôn tập

1. Nêu sự khác nhau giữa loading, linking và initialization. Vì sao `Sample.class` không chứng minh khối `static {}` đã chạy?
2. Vì sao `new Sample[4]` không tạo bốn đối tượng `Sample`?
3. Trong `Sample[] data = {new Sample()};`, thao tác nào gây initialization?
4. Phân loại `static final int A = 5;`, `static final int B = parse();` và `static final Integer C = 5;` thành constant/nonconstant variables.
5. Khi đọc `Child.X` nhưng `X` là nonconstant static field khai báo ở `Parent`, lớp nào là đối tượng của trigger?
6. Đọc `Options.PORT` và `Options.MODE` ở interface tại mục 5 khác nhau thế nào?
7. Vì sao `INIT CompileTimeConstants` xuất hiện ở nhóm `E` mà không xuất hiện ở nhóm `C` của chương trình?
8. Trong trường hợp nào `Class.forName(name, false, loader)` phù hợp hơn `Class.forName(name)` để chỉ quan sát việc tìm lớp?
9. Một chương trình đã in `INIT Sample`, sau đó chạy lại `new Sample()` nhưng không in tiếp: điều này có bình thường không?
10. Vì sao đổi một public compile-time constant ở thư viện có thể cần biên dịch lại client?

**Hoàn thành bài khi:** dự đoán đúng stdout, giải thích được ba thao tác không kích hoạt initialization và phân biệt được *constant variable* với các trường `static final` không phải hằng biên dịch.

## 10. Tài liệu chuẩn

- [JLS Java SE 21 12.4.1 — When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.1): các trigger của initialization, ví dụ superclass, static fields và interface.
- [JLS Java SE 21 4.12.4 — final Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4): định nghĩa của constant variable.
- [JLS Java SE 21 15.29 — Constant Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.29): biểu thức hằng hợp lệ.
- [JLS Java SE 21 13.4.9 — final Fields and Constants](https://docs.oracle.com/javase/specs/jls/se21/html/jls-13.html#jls-13.4.9): hệ quả biên dịch khi giá trị hằng thay đổi.
- [Java SE 21 Class.forName API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): tham số `initialize`.

Ví dụ dùng API và cú pháp tương thích Java 17 trở lên; quy tắc về thời điểm khởi tạo được trình bày theo Java Language Specification.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-005">Khởi tạo lớp cha và thứ tự thực hiện static initializer</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo lớp cha và thứ tự thực hiện static initializer trong Java

Khi chương trình lần đầu sử dụng một lớp theo cách buộc JVM **khởi tạo lớp** (class initialization), Java phải thiết lập trạng thái tĩnh của lớp đó. Với quan hệ kế thừa, **lớp cha được khởi tạo trước khi chạy các bộ khởi tạo tĩnh thông thường của lớp con**. Bên trong từng lớp, các lệnh gán cho trường `static` và các khối `static { ... }` được thực thi theo thứ tự xuất hiện trong mã nguồn.

Đây là quy tắc quan trọng khi đọc log khởi động ứng dụng, phân tích thứ tự đăng ký thành phần tĩnh hoặc tìm lỗi xảy ra trong `static initializer`. Tuy nhiên, có những ngoại lệ cần hiểu rõ: việc nạp lớp không nhất thiết khởi tạo lớp, đọc hằng số tại thời điểm biên dịch có thể không kích hoạt khởi tạo, truy cập thành viên `static` **kế thừa** thường khởi tạo lớp **khai báo** thành viên, và một số **superinterface có default method** cũng có thể được khởi tạo trước lớp triển khai.

## 1. Khởi tạo lớp khác gì nạp lớp và tạo đối tượng?

Ba khái niệm dễ bị trộn lẫn:

| Hoạt động | Điều xảy ra | Có bắt buộc chạy `static initializer` không? |
| --- | --- | --- |
| **Loading** | Class loader/JVM tạo định nghĩa `Class<?>` từ biểu diễn lớp. | Không nhất thiết. |
| **Linking / preparation** | JVM kiểm tra/liên kết và cấp phát các trường tĩnh với giá trị mặc định; một số hằng có `ConstantValue` được xử lý theo quy định riêng. | Chưa phải việc chạy toàn bộ khối `static`. |
| **Initialization** | JVM thực thi thủ tục khởi tạo lớp, gồm phần khởi tạo trường tĩnh và khối `static` cần thực thi. | Có, nếu lớp có các thao tác đó. |
| **Object construction** | Tạo đối tượng, chạy quá trình khởi tạo instance và constructor trong chuỗi kế thừa. | Chỉ kích hoạt khởi tạo lớp nếu định nghĩa lớp liên quan chưa được khởi tạo. |

Ví dụ, `ClassLoader.loadClass(...)` thông thường **nạp** nhưng không yêu cầu **khởi tạo** lớp; `Class.forName(name)` dạng một tham số thông thường yêu cầu khởi tạo. Với `Class.forName(name, false, loader)`, tham số `false` cho phép lấy `Class<?>` mà chưa yêu cầu chạy `static initializer`.

Đừng nhầm `static initializer` với constructor:

- `static { ... }` và phép gán cho trường `static` thông thường chạy **một lần cho mỗi định nghĩa lớp được khởi tạo thành công**, không chạy lại khi tạo thêm object.
- Khối khởi tạo instance `{ ... }`, phép gán trường instance và constructor phục vụ **mỗi lần tạo đối tượng**.
- `Class<?>.getClassLoader()` cho biết defining loader. Hai loader định nghĩa cùng tên nhị phân có thể tạo ra hai định nghĩa lớp khác nhau, mỗi định nghĩa có trạng thái khởi tạo riêng.

## 2. Quy tắc chính: lớp cha trước, lớp con sau

Giả sử có chuỗi kế thừa:

~~~text
java.lang.Object
       │
       ▼
      Root
       │
       ▼
     Parent
       │
       ▼
      Child
~~~

Khi một hành động buộc `Child` được khởi tạo và các lớp cha chưa khởi tạo, thứ tự khái quát là:

~~~text
Khởi tạo Root (sau Object nếu cần)
      ↓
Khởi tạo Parent
      ↓
Khởi tạo Child
      ↓
Tiếp tục hành động đã yêu cầu Child
~~~

Nói chính xác hơn, JVM thực hiện **thủ tục khởi tạo** đối với lớp cần dùng, rồi bảo đảm các lớp cha cần thiết và những superinterface thuộc trường hợp quy định được khởi tạo trước khi chạy phần khởi tạo thông thường của lớp hiện tại. Mỗi lớp cha đã khởi tạo thì được bỏ qua; JVM không chạy lại khối `static` của nó.

Quy tắc này bảo đảm các thành viên tĩnh và giả định khởi tạo cần thiết ở lớp cha đã sẵn sàng khi lớp con thực thi bộ khởi tạo của mình. **Không có quy tắc “khởi tạo toàn bộ lớp con khi cha được dùng”.** Chỉ dùng thành viên khai báo bởi `Parent` thường không buộc `Child` khởi tạo.

Để tránh diễn giải sai, cần phân biệt một chi tiết ở mức đặc tả: giá trị của **constant variable** có thể được thiết lập từ thuộc tính `ConstantValue` theo bước riêng của thủ tục khởi tạo; quy tắc “cha trước con” ở đây nói về **thứ tự thực thi các phép gán tĩnh thông thường và khối `static` có side effect**, không có nghĩa mọi byte dữ liệu tĩnh của lớp con đều chưa thể mang giá trị trước khi cha hoàn tất.

## 3. Trong từng lớp, trường static và khối static chạy theo thứ tự văn bản

Trong một lớp, các trình khởi tạo của trường tĩnh và khối `static` được chạy **từ trên xuống dưới**, giống như được ghép thành một khối duy nhất:

~~~java
class Settings {
    static int a = calculateA();

    static {
        System.out.println("middle");
    }

    static int b = calculateB();
}
~~~

Đây là **đoạn minh họa** để nhìn thứ tự; hai method `calculateA` và `calculateB` chưa được định nghĩa nên đoạn code này **không phải tệp Java độc lập để biên dịch**. Nếu có định nghĩa các method đó, thứ tự side effect sẽ là `calculateA()` → in `middle` → `calculateB()`.

Với dữ liệu thông thường, JVM trước hết cấp phát trường `static` và đặt **giá trị mặc định** trong giai đoạn preparation (`0`, `false`, `null`, tùy kiểu). Sau đó quá trình initialization thực hiện các phép gán có code và khối `static` theo thứ tự. Vì vậy, một initializer có thể quan sát giá trị mặc định của trường khác trong một số trường hợp phụ thuộc vòng; không được suy rằng giá trị khai báo cuối cùng đã hiện diện ở mọi thời điểm.

Các quy tắc **illegal forward reference** hạn chế một số cách dùng trường tĩnh được khai báo phía sau ngay trong initializer của cùng lớp. Nhưng phụ thuộc gián tiếp qua method hoặc qua lớp khác vẫn có thể tạo vòng khởi tạo hợp lệ về cú pháp mà khó dự đoán ở runtime.

### Khối static khác constructor ở điểm nào?

`static { ... }` được gắn với **lớp**, không phụ thuộc object cụ thể và không trực tiếp truy cập `this` hoặc `super`. Constructor được thực thi khi xây dựng **instance**, sau khi các lớp cần thiết đã khởi tạo. Nếu `new Child()` là hành động đầu tiên sử dụng `Child`, **static initialization diễn ra trước** chuỗi constructor/instance initialization.

## 4. Ví dụ Java 17: quan sát đầy đủ thứ tự Root → Parent → Child

Tạo tệp **`StaticOrderDemo.java`** với toàn bộ nội dung:

~~~java
public class StaticOrderDemo {
    private static int mark(String label, int result) {
        System.out.println(label);
        return result;
    }

    static class Root {
        static int rootValue = mark("Root.field", 1);

        static {
            System.out.println("Root.block");
        }
    }

    static class Parent extends Root {
        static {
            System.out.println("Parent.block-1");
        }

        static int parentValue = mark("Parent.field", 2);

        static {
            System.out.println("Parent.block-2");
        }
    }

    static class Child extends Parent {
        static int childValue = mark("Child.field", 3);

        static {
            System.out.println("Child.block");
        }
    }

    public static void main(String[] args) {
        System.out.println("Before Child");
        System.out.println("Value = " + Child.childValue);
        System.out.println("Again = " + Child.childValue);
    }
}
~~~

Biên dịch/chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 StaticOrderDemo.java
java StaticOrderDemo
~~~

**Stdout chính xác:**

~~~text
Before Child
Root.field
Root.block
Parent.block-1
Parent.field
Parent.block-2
Child.field
Child.block
Value = 3
Again = 3
~~~

**Giải thích từng bước:**

1. JVM khởi tạo `StaticOrderDemo` để chạy `main`; ba lớp lồng `Root`, `Parent`, `Child` **không phải tự động khởi tạo cùng outer class**.
2. `Before Child` xuất hiện trước khi `Child` được dùng theo cách đòi hỏi initialization.
3. Đọc `Child.childValue` buộc khởi tạo `Child`. Vì `Child` kế thừa `Parent`, còn `Parent` kế thừa `Root`, JVM thực thi các bộ khởi tạo của `Root` rồi `Parent` rồi `Child`.
4. Bên trong `Parent`, `Parent.block-1` chạy **trước** initializer `parentValue`, sau đó tới `Parent.block-2`; khối `static` không có ưu tiên tuyệt đối hơn phép gán `static`, chúng phụ thuộc **thứ tự văn bản**.
5. Sau khi các lớp được khởi tạo, biểu thức đọc field trả `3`. Đọc lại `Child.childValue` không tái chạy bất cứ block nào.

Thử thay thứ tự khai báo hai khối `static` và `parentValue` trong `Parent` rồi biên dịch lại để thấy log thay đổi theo văn bản. Đây là sự thay đổi thứ tự thực thi code trong **cùng một lớp**, còn thứ tự **cha → con** vẫn được bảo đảm.

## 5. Tên lớp trước dấu chấm chưa chắc là declaring class

Nếu `Parent` khai báo `static int shared` còn `Child extends Parent`, biểu thức `Child.shared` chỉ khởi tạo **Parent** nếu cần. Nếu `Child` khai báo field riêng cùng tên thì đó là **field hiding**, hai field tách biệt. Với `static final int LIMIT = 99` là constant variable, phép đọc hằng không tự kích hoạt initialization.

Bài **Đọc static field kế thừa: kiểu thực sự khai báo field** dạy riêng member lookup, hiding, `getDeclaringClass()` và bytecode `getstatic`. Ở bài này, điều cần giữ là **lớp thật sự sở hữu field/method** trong chuỗi init.

## 6. Static initialization và constructor theo hai lượt

Nếu `new Child()` là active use đầu, static initializer `Parent` rồi `Child` chạy trước các constructor. Sau đó chuỗi constructor `Parent()` → `Child()` chạy cho instance đó. Một object thứ hai chỉ chạy lại chuỗi constructor vì các lớp đã initialized thành công. Bài phân biệt `<clinit>`/`<init>` cung cấp ví dụ riêng về hai chuỗi.

## 7. Ngoại lệ quan trọng: superinterface có default method

Không thể diễn giải thứ tự đầy đủ chỉ bằng cây `extends`. Theo Java 17, khi một **class** được khởi tạo, JVM còn khởi tạo những **superinterface trực tiếp hoặc gián tiếp có khai báo default method** nếu chúng chưa khởi tạo. Các interface này được xét theo thứ tự đệ quy do đặc tả quy định. Ngược lại:

- Interface **không có default method** không nhất thiết được khởi tạo chỉ vì một class implements nó.
- Khởi tạo một **interface** tự nó **không kéo theo khởi tạo toàn bộ superinterface** mà nó extends.
- Những phương thức static khai báo trong interface **không phải default method**. `default` là implementation của một instance method trong interface.

Quan sát bằng ví dụ **`InterfaceInitDemo.java`**:

~~~java
public class InterfaceInitDemo {
    private static int trace(String label, int value) {
        System.out.println(label);
        return value;
    }

    interface Plain {
        int NUMBER = trace("Plain initialized", 1);
    }

    interface WithDefault {
        int NUMBER = trace("WithDefault initialized", 2);

        default void run() {
            // Không cần gọi để interface được khởi tạo cùng lớp.
        }
    }

    static class Impl implements Plain, WithDefault {
        static {
            System.out.println("Impl initialized");
        }
    }

    public static void main(String[] args) {
        System.out.println("Before object");
        new Impl();
        System.out.println("After object");
        System.out.println("Plain number = " + Plain.NUMBER);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 InterfaceInitDemo.java
java InterfaceInitDemo
~~~

Stdout:

~~~text
Before object
WithDefault initialized
Impl initialized
After object
Plain initialized
Plain number = 1
~~~

`WithDefault` được khởi tạo **trước `Impl`** vì khai báo default method `run`. `Plain` chưa cần khởi tạo tại thời điểm `new Impl()`; nó chỉ khởi tạo khi truy cập `Plain.NUMBER`. Cả hai trường interface là `public static final` theo luật ngôn ngữ, nhưng initializer gọi method `trace(...)` nên **không phải compile-time constant** và quan sát được lúc initialization.

**Chi tiết nâng cao:** Với nhiều superinterface có default method, thứ tự JLS 12.4.2 dùng cách duyệt **đệ quy theo thứ tự khai báo từ trái sang phải**, xét superinterface trước interface đang duyệt, sau superclass của class; không đơn giản là “interface được viết bên trái có priority cao hơn mọi ancestor khác”. Có trường hợp trình biên dịch sinh **synthetic default method**, cũng có thể ảnh hưởng việc khởi tạo interface. Để hiểu chính xác tình huống phức tạp, đối chiếu đồ thị interface và output `javap -v` thay vì chỉ suy từ dòng `implements`.

## 8. Những hoạt động có và không yêu cầu khởi tạo lớp

Các tình huống thường gặp (giả sử lớp chưa khởi tạo, không có yếu tố khác buộc khởi tạo trước):

| Hành động | Kết quả cần nhớ |
| --- | --- |
| `new Child()` | Yêu cầu khởi tạo `Child`; lớp cha cần thiết được khởi tạo trước code static thông thường của con. |
| `Child.ownStaticField` (field khai báo ở Child, không phải constant variable) | Yêu cầu khởi tạo `Child`. |
| `Child.ownStaticMethod()` (method khai báo ở Child) | Yêu cầu khởi tạo `Child`. |
| `Child.inheritedStaticField` (field khai báo ở Parent) | Yêu cầu khởi tạo lớp **thực sự khai báo field**, không tự yêu cầu khởi tạo `Child`. |
| Đọc `static final int` được khai báo bằng constant expression | Thông thường không buộc khởi tạo lớp khai báo để đọc giá trị. |
| `Child.class` | Lấy class literal **không tự buộc** initialization. |
| `Class.forName("pkg.Child")` | Dạng thông thường yêu cầu khởi tạo `Child`. |
| `Class.forName("pkg.Child", false, loader)` | Không yêu cầu khởi tạo chỉ bởi lời gọi này. |
| `loader.loadClass("pkg.Child")` | Theo hợp đồng `loadClass` thường nạp mà không khởi tạo. |
| Tạo một instance thứ hai của `Child` | Không chạy lại `static` của `Child` đã khởi tạo thành công; constructor vẫn chạy. |

Bảng là **mô hình hành vi bắt buộc về thời điểm chạy các initializer**, không phải lịch thực tế JVM đọc tệp bytecode. JVM có thể nạp, kiểm tra hoặc resolve một lớp sớm hơn, miễn giữ đúng ngữ nghĩa quan sát được về initialization.

## 9. Khi chuỗi initialization bị gián đoạn

Nếu initializer của superclass hoặc một superinterface bắt buộc ném lỗi, lớp con không thể hoàn tất initialization bình thường. Vòng phụ thuộc do code tạo ra có thể đọc field còn mặc định; hai thread chờ chéo nhau có thể deadlock. Đó là chủ đề của ba bài riêng về **đồng bộ**, **khởi tạo đệ quy** và **initialization thất bại**. Thứ tự trong các mục trước mô tả đường thành công thông thường.

## 10. Cách suy luận và debug thứ tự initialization

Khi thấy log khác dự kiến, hãy kiểm tra theo thứ tự:

1. **Lớp nào bị “active use” trước?** Tìm `new`, `invokestatic` (gọi static method), `getstatic`/`putstatic` (field không phải constant variable), reflection hoặc điểm entry `main`.
2. **Thành viên static thực sự được khai báo ở đâu?** `Child.shared` có thể thực chất là `Parent.shared`; tránh đoán theo dấu chấm trong mã nguồn.
3. **Lớp cha nào còn chưa khởi tạo?** Lần lượt đi qua superclass chain; lưu ý các superinterface có default method của class.
4. **Trong từng lớp, thứ tự văn bản ra sao?** Đọc xen kẽ field initializer và khối `static`, không gom mọi field trước rồi mọi block sau.
5. **Có hằng compile-time không?** `static final int LIMIT = 10` có ngữ nghĩa khác `static final Integer LIMIT = 10` hoặc `static final int LIMIT = calculate()`.
6. **Có vòng phụ thuộc hoặc lỗi khởi tạo không?** Tìm exception đầu tiên, `ExceptionInInitializerError`, `NoClassDefFoundError`, hay side effect đọc giá trị mặc định.
7. **Có nhiều class loader hoặc nhiều thread không?** Cùng tên lớp nhưng khác defining loader có thể là hai định nghĩa độc lập; nhiều thread có thể chờ initialization.

Để quan sát bytecode trên HotSpot/JDK 17+, chạy:

~~~bash
javap -c -p StaticOrderDemo
javap -c -p 'StaticOrderDemo$Parent'
~~~

Lưu ý lệnh thứ hai dùng dấu nháy đơn để tránh shell diễn giải ký tự `$` trong tên lớp lồng trên các shell kiểu Unix. Trên Windows PowerShell, dấu nháy đơn cũng giữ nguyên chuỗi lớp lồng. `javap` có thể hiển thị method đặc biệt `<clinit>`, là nơi bytecode cho phép gán `static` và block `static` được gom theo thứ tự biên dịch. Dùng `javap -v -p` khi cần xem nhiều metadata hơn.

Nếu chỉ nhìn `javap -c` của từng lớp, hãy nhớ **thứ tự giữa các lớp là do thủ tục JVM initialization**, không phải do bytecode `Child.<clinit>` tự chứa toàn bộ code `Parent.<clinit>`.

## 11. Tự kiểm tra

**Câu 1.** `Child extends Parent`. Nếu `Child` chưa khởi tạo và gọi method static khai báo ở `Child`, khối `static` của ai chạy trước?

**Đáp án:** Nếu `Parent` chưa khởi tạo thì bộ khởi tạo tĩnh thông thường của `Parent` chạy trước phần tương ứng của `Child` (và các superclass của `Parent` trước nữa nếu cần). Nếu `Parent` đã initialized thì không chạy lại.

**Câu 2.** Trong cùng class: `static { A(); }`, tiếp đến `static int x = B();`, sau cùng `static { C(); }`. Thứ tự các lời gọi?

**Đáp án:** `A()` → `B()` → `C()`, theo thứ tự văn bản, giả sử không có lỗi hay side effect kích hoạt đường khởi tạo khác làm thay đổi quan sát.

**Câu 3.** Vì sao `Child.PARENT_FIELD` có thể chỉ in log từ `Parent`?

**Đáp án:** Trường được khai báo ở `Parent`; initialization theo lớp **khai báo trường** đối với truy cập static field, không dựa đơn thuần vào kiểu viết trước dấu chấm.

**Câu 4.** `interface Metrics` không có default method, nhưng khai báo `int X = helper();`. Tạo object của lớp `implements Metrics` có chắc chạy initializer của `Metrics` không?

**Đáp án:** Không. Interface không có default method không bị buộc khởi tạo chỉ vì lớp triển khai được khởi tạo. Truy cập `Metrics.X` sẽ yêu cầu initialization vì `helper()` không tạo constant variable.

**Câu 5.** Tạo object thứ hai từ cùng class có chạy lại khối `static`?

**Đáp án:** Không, nếu class definition đã initialized thành công. Các initializer instance và constructor vẫn chạy cho object mới.

## Ghi nhớ

Để dự đoán đúng log và trạng thái khi JVM khởi tạo lớp, hãy lần lượt xác định **hành động thực sự buộc khởi tạo**, **lớp khai báo thành viên**, **chuỗi superclass và superinterface default-method cần thiết**, rồi **thứ tự field initializer / static block từ trên xuống trong mỗi lớp**. Class đã khởi tạo thành công không khởi tạo lại cho lần sử dụng sau. Những trường hợp cần xét riêng là compile-time constant, vòng phụ thuộc, lỗi static initializer và class loader khác nhau.

**Nguồn chuẩn để tra cứu thêm:** Java Language Specification (Java SE 17), §12.4 *Initialization of Classes and Interfaces*, đặc biệt §12.4.1–§12.4.2: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4 ; Java Virtual Machine Specification (Java SE 17), §5.5: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-006">Khởi tạo interface và ảnh hưởng của default method</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo interface và ảnh hưởng của default method trong Java

Trong Java, **interface cũng có giai đoạn khởi tạo (initialization)**, dù không thể tạo instance trực tiếp bằng `new Interface()`. Điểm dễ nhầm là quy tắc khởi tạo interface khác với quy tắc của class, và **default method** có thể khiến interface được khởi tạo khi JVM khởi tạo một class triển khai nó, **ngay cả khi chương trình chưa gọi default method đó**.

Theo *Java Language Specification (JLS) §12.4.1*:

- Khởi tạo **class** đòi hỏi khởi tạo các superclass chưa được khởi tạo, đồng thời khởi tạo các **superinterface khai báo ít nhất một default method** phù hợp trong cây kế thừa.
- Khởi tạo **interface** riêng nó **không tự động khởi tạo các superinterface**.
- Interface có thể được khởi tạo vì truy cập một trường `static` không phải *constant variable*, gọi `static` method do nó khai báo, hoặc do một thao tác phản chiếu có yêu cầu initialization.

Cần tách bạch ba việc: JVM đã **nạp** định nghĩa interface, đã **liên kết** interface, và đã **khởi tạo** interface. Chỉ giai đoạn khởi tạo mới chạy logic gán giá trị trường `static` có tính toán của interface.

## 1. Interface được khởi tạo nghĩa là gì?

Với class, ta quen thuộc với `static` field initializer và `static { ... }`. **Interface không cho phép khai báo `static` initializer block**, nhưng field được khai báo trong interface mặc định là `public static final`, và biểu thức khởi tạo các field có thể chạy mã khi interface được initialized.

```java
interface Feature {
    int FIXED_LIMIT = 10;
    int RUNTIME_LIMIT = calculate();

    static int calculate() {
        System.out.println("Feature initialized");
        return 20;
    }

    static void printInfo() {
        System.out.println("Feature.printInfo()");
    }
}
```

Đây là **đoạn minh họa**, không phải file có `main`. Hai field đều ngầm mang `public static final`, nhưng chúng khác nhau quan trọng:

- `FIXED_LIMIT = 10` là một **constant variable**: kiểu primitive/String, `final`, được khởi tạo bằng *constant expression*. Trình biên dịch có thể ghi sẵn `10` vào mã của nơi sử dụng, nên chỉ đọc `Feature.FIXED_LIMIT` **không yêu cầu khởi tạo `Feature`**.
- `RUNTIME_LIMIT = calculate()` **không** phải constant variable, dù cũng là `static final`. Đọc trực tiếp `Feature.RUNTIME_LIMIT` khi interface chưa initialized khiến JVM khởi tạo interface, gọi `calculate()` một lần, rồi nhận `20`.
- `Feature.printInfo()` là lời gọi `static` method do `Feature` khai báo; nếu `Feature` chưa initialized, lời gọi này cũng kích hoạt initialization.

Các trường trong interface được khởi tạo **theo thứ tự khai báo trong mã nguồn**, phù hợp quy tắc khởi tạo của Java. Không được coi mọi field `static final` là giá trị đã được tính sẵn ở compile time.

### Bốn trạng thái thường bị lẫn

| Sự kiện | Ý nghĩa đối với interface `Feature` |
|---|---|
| JVM load/create kiểu | Có biểu diễn runtime `Class<Feature>` và loader phù hợp; chưa chắc chạy các field initializer |
| JVM link kiểu | Verify và prepare; resolution có thể làm theo nhu cầu; chưa đồng nghĩa evaluate `calculate()` |
| JVM initialize interface | Chạy quá trình khởi tạo biến interface (như `RUNTIME_LIMIT = calculate()`) nếu có |
| Tạo object triển khai | Tạo instance của một **class** triển khai `Feature`; không tồn tại `new Feature()` trực tiếp |

Một interface được khởi tạo tối đa một lần thành công cho **mỗi định nghĩa kiểu runtime** (theo defining class loader); nhiều đối tượng của class triển khai không làm nó chạy lại field initializer.

## 2. Những trigger khởi tạo interface

Các trường hợp hay gặp:

| Thao tác | Có bắt buộc initialize interface T chỉ vì thao tác đó? | Lý do |
|---|---|---|
| `T.class` | Không | Class literal lấy đại diện kiểu, không phải active use |
| `Class.forName(name, false, loader)` | Không yêu cầu | Tra cứu/nạp và liên kết kiểu với `initialize=false` |
| `Class.forName(name)` | Có, nếu chưa initialized | Overload này yêu cầu khởi tạo kiểu tìm được |
| `T.CONST` với field là constant variable | Không | Giá trị compile-time constant có thể được inline |
| `T.RUNTIME_FIELD` là field `static` không phải constant variable, do T khai báo | Có | Truy cập trường non-constant là active use |
| `T.staticMethod()`, method do T khai báo | Có | Gọi `static` method là active use |
| `class C implements T { ... }` (chỉ khai báo kiểu) | Không | Quan hệ `implements` không tự nó buộc initialization |
| Initialize class `C implements T`, T **không khai báo default method** | Không chỉ vì `implements` | JVM không buộc initialize mọi interface được triển khai |
| Initialize class `C implements T`, T **khai báo default method** | Có, nếu chưa initialized | Quy tắc khởi tạo superinterface có default method |
| Gọi instance default method trên object của `C` | T đã được initialized nếu T thuộc nhóm bắt buộc trước khi C initialized | Khởi tạo C đã xảy ra trước khi tạo object để gọi method |

Bảng mô tả **yêu cầu bắt buộc** theo Java; một interface có thể đã được initialized từ một nguyên nhân khác trước khi thao tác diễn ra. Reflection và method handles còn có những điểm kích hoạt riêng do API/VM quy định.

**Đặc biệt:** không thể khai báo một `static` method ở interface rồi trông chờ interface triển khai được “kế thừa” method ấy như default method. `static` method của interface được gọi qua tên chính interface khai báo method.

## 3. Vì sao default method làm thay đổi quy tắc?

Default method (Java 8+) là **instance method có phần thân mặc định** khai báo trong interface:

```java
interface Greeting {
    int INIT = logInitialization();

    static int logInitialization() {
        System.out.println("Greeting initialized");
        return 1;
    }

    default String greet() {
        return "Hello";
    }
}
```

`Greeting` không cần instance riêng để tồn tại. Một class có thể triển khai `Greeting` và sử dụng hoặc override `greet()`.

Với:

```java
class Greeter implements Greeting {
    static {
        System.out.println("Greeter initialized");
    }
}
```

Khi JVM **initialize `Greeter`**, nó phải bảo đảm `Greeting` cũng initialized vì `Greeting` khai báo một **default method**. Theo JLS, điều này xảy ra **ngay cả khi constructor của `Greeter` không gọi `greet()` và ngay cả khi `Greeter` override `greet()`**.

Điểm quyết định nằm ở **khai báo default method trong một superinterface** chứ không phải ở việc chương trình đã thực thi phần thân default method bao nhiêu lần. Việc gọi default method sau đó chỉ là thực thi phương thức trên một instance hợp lệ.

### Default method khác static method, abstract method và private interface method

| Method trong interface | Ví dụ | Nó làm interface trở thành superinterface cần initialize khi class triển khai initialized? |
|---|---|---|
| **Default instance method** | `default void run() { }` | **Có** |
| Abstract instance method | `void run();` | **Không chỉ vì method này** |
| Static method | `static void help() { }` | **Không chỉ vì nó tồn tại**; gọi trực tiếp method lại là một trigger khác |
| Private helper method | `private void helper() { }` hoặc `private static void helper() { }` | **Không chỉ vì helper này** |

Một interface có cả abstract, static và default methods vẫn thuộc nhóm phải initialize lúc class triển khai initialized vì nó **có khai báo default method**. Nếu không có default method thì không có yêu cầu đó chỉ từ quan hệ `implements`.

**Cảnh báo với bytecode:** Java compiler có thể sinh *synthetic default methods* trong một số trường hợp. Khi phân tích bytecode thực, quy tắc JVM xét phương thức được khai báo trong định nghĩa kiểu runtime, không chỉ những dòng `default` mà người học trực tiếp nhìn thấy trong Java source.

## 4. Ba quan hệ kế thừa phải phân biệt

### 4.1. Class implements interface chỉ có abstract method

```java
interface AbstractOnly {
    int CHECK = init();

    static int init() {
        System.out.println("AbstractOnly initialized");
        return 1;
    }

    void run();
}

class Implementation implements AbstractOnly {
    @Override
    public void run() { }
}
```

Nếu chương trình chỉ tạo `new Implementation()`, JVM **không bắt buộc** initialize `AbstractOnly` chỉ vì quan hệ implements. Một định nghĩa interface có thể đã được nạp/liên kết để kiểm tra kiểu mà vẫn chưa chạy initializer `CHECK`.

Nhưng truy cập `AbstractOnly.CHECK` sẽ buộc initialize interface đó vì `CHECK` không phải compile-time constant.

### 4.2. Class implements interface có default method

```java
interface WithDefault {
    int CHECK = init();

    static int init() {
        System.out.println("WithDefault initialized");
        return 1;
    }

    default void run() { }
}

class DefaultImplementation implements WithDefault {
    static {
        System.out.println("DefaultImplementation initialized");
    }
}
```

Khi `new DefaultImplementation()` là lần sử dụng chủ động đầu tiên và các kiểu chưa initialized, thứ tự bắt buộc sẽ bao gồm:

1. Khởi tạo superclass của `DefaultImplementation` nếu cần.
2. Khởi tạo superinterface **`WithDefault`** vì nó khai báo default method.
3. Chạy initializer riêng của `DefaultImplementation`.
4. Tạo/khởi tạo instance.

Vì vậy `WithDefault initialized` xuất hiện trước `DefaultImplementation initialized`. JVM không phải chờ gọi `run()` mới initialize `WithDefault`.

### 4.3. Interface extends interface có default method

```java
interface Parent {
    int VALUE = trace();

    static int trace() {
        System.out.println("Parent initialized");
        return 1;
    }

    default void ping() { }
}

interface Child extends Parent {
    int CHILD_VALUE = childTrace();

    static int childTrace() {
        System.out.println("Child initialized");
        return 2;
    }
}
```

Với đoạn trên:

- **Đọc `Child.CHILD_VALUE`** buộc initialize `Child` (field được khai báo ở Child). Việc initialize `Child` **không tự động initialize `Parent`**, dù Parent khai báo default method.
- **Đọc `Child.VALUE`** về mặt cú pháp dùng tên Child nhưng field `VALUE` thực sự được **Parent khai báo**. Truy cập này buộc initialize **Parent**, không có lý do tự động initialize Child.
- **Initialize một concrete class `C implements Child`**: JVM xem các superinterface trong cây kế thừa của C. Vì **Parent khai báo default method**, Parent phải initialized (nếu chưa) trước khi C initialized. `Child` *không tự động* initialized do implements nếu nó không khai báo default method riêng và không có trigger khác.

Điểm dễ lẫn: **initialize interface Child** khác với **initialize một class triển khai Child**. Trường hợp thứ hai xét các superinterface có default method; trường hợp thứ nhất không tự lan việc initialization lên toàn bộ interface cha.

## 5. Cây kế thừa sâu: interface nào sẽ chạy initializer?

Giả sử quan hệ:

```text
interface Root              declares default method
    |
    v
interface Mid               only abstract / static methods
    |
    v
interface Leaf              only constants / abstract methods
    ^
    |
class Concrete              implements Leaf
```

Khi `Concrete` được initialized, **`Root`** thuộc nhóm superinterface có khai báo default method. Nếu `Mid` và `Leaf` không khai báo default method và không có trigger khác, chúng **không bắt buộc** initialized chỉ vì `Concrete implements Leaf`.

Như vậy “chỉ interface được implements trực tiếp mới đáng quan tâm” cũng sai: điều kiện được xét trong **cây superinterface**, gồm cả tổ tiên gián tiếp có default method.

### Thứ tự quan sát khi có nhiều nhánh

JVM có thuật toán khởi tạo liên quan superclass và các superinterface chứa default methods; nó bảo đảm những loại cần thiết được initialized trước khi chính class hoàn tất initialization. Quy tắc chi tiết về duyệt cây và trạng thái đã initialized nằm ở **JVM Specification §5.5**.

Vì thế không nên tự ấn định thứ tự log cho **nhiều nhánh độc lập** chỉ từ sơ đồ không phản ánh đầy đủ thứ tự khai báo và thuật toán JVM. Điều chắc chắn của một tình huống đơn giản là:

```text
new Concrete()
    |
    +--> initialize superclass nếu chưa initialized
    |
    +--> initialize các superinterface có default method
    |    theo quy tắc JVM, nếu chưa initialized
    |
    +--> initialize Concrete
    |
    +--> gọi constructor Concrete
```

**Không phải** tất cả interface được nạp đều phải initialized, và cũng không có chuyện initializer của cùng một định nghĩa interface chạy lặp lại mỗi lần tạo object.

## 6. Thí nghiệm Java 17+: quan sát cả ba trường hợp

Tạo file **`InterfaceInitializationDemo.java`** với toàn bộ chương trình sau. Ví dụ dùng nested interfaces và nested static classes để có thể chạy trong một file; phương thức `mark` in thông báo mỗi khi initializer có tính toán được thực thi.

```java
public class InterfaceInitializationDemo {
    static int mark(String name) {
        System.out.println("init " + name);
        return 1;
    }

    interface AbstractOnly {
        int VALUE = mark("AbstractOnly");
        void work();
    }

    interface Defaulted {
        int VALUE = mark("Defaulted");

        default void work() {
            System.out.println("Defaulted.work");
        }
    }

    interface Parent {
        int VALUE = mark("Parent");

        default void work() {
            System.out.println("Parent.work");
        }
    }

    interface Leaf extends Parent {
        int VALUE = mark("Leaf");
    }

    interface Config {
        int CONST = 10;
        int NON_CONST = mark("Config");

        static void ping() {
            System.out.println("Config.ping");
        }
    }

    static class AbstractImpl implements AbstractOnly {
        static {
            System.out.println("init AbstractImpl");
        }

        @Override
        public void work() { }
    }

    static class DefaultImpl implements Defaulted {
        static {
            System.out.println("init DefaultImpl");
        }
    }

    static class LeafImpl implements Leaf {
        static {
            System.out.println("init LeafImpl");
        }
    }

    public static void main(String[] args) {
        System.out.println("1) literal: " + Leaf.class.getSimpleName());
        System.out.println("2) Leaf.VALUE: " + Leaf.VALUE);
        System.out.println("3) constant: " + Config.CONST);

        System.out.println("4) construct AbstractImpl");
        new AbstractImpl();

        System.out.println("5) construct DefaultImpl");
        new DefaultImpl().work();

        System.out.println("6) construct LeafImpl");
        new LeafImpl().work();

        System.out.println("7) Config.NON_CONST: " + Config.NON_CONST);
        System.out.println("8) Config.ping");
        Config.ping();
    }
}
```

Dùng **JDK 17 hoặc mới hơn**:

```bash
javac InterfaceInitializationDemo.java
java InterfaceInitializationDemo
```

**Stdout dự kiến (trong chương trình này):**

```text
1) literal: Leaf
init Leaf
2) Leaf.VALUE: 1
3) constant: 10
4) construct AbstractImpl
init AbstractImpl
5) construct DefaultImpl
init Defaulted
init DefaultImpl
Defaulted.work
6) construct LeafImpl
init Parent
init LeafImpl
Parent.work
init Config
7) Config.NON_CONST: 1
8) Config.ping
Config.ping
```

### Phân tích chính xác từng mốc

**(1) `Leaf.class`:** JVM cung cấp `Class<Leaf>`. Việc lấy literal không tự yêu cầu initialize `Leaf` hoặc `Parent`; vì vậy chưa có dòng `init ...`.

**(2) `Leaf.VALUE`:** field này do **Leaf** khai báo; giá trị đến từ lời gọi `mark("Leaf")` nên nó không phải constant variable. Truy cập khiến **Leaf initialized** và in `init Leaf`. Mặc dù Leaf extends Parent và Parent có default method, **Parent không tự initialized** khi Leaf initialized.

**(3) `Config.CONST`:** giá trị `10` là compile-time constant nên chỉ đọc nó không cần initialize Config. Không có dòng `init Config` ở đây.

**(4) `new AbstractImpl()`:** AbstractImpl triển khai AbstractOnly, nhưng AbstractOnly **không khai báo default method**. Initializer `AbstractImpl` chạy, còn `init AbstractOnly` không xuất hiện. JVM có thể đã nạp/liên kết AbstractOnly; điều này vẫn khác với initialization.

**(5) `new DefaultImpl().work()`:** trước khi khởi tạo DefaultImpl, JVM initialize **Defaulted** vì interface này khai báo default method. Thứ tự là `init Defaulted` rồi `init DefaultImpl`, sau đó mới gọi `Defaulted.work`. Nếu bỏ lời gọi `work()` và chỉ viết `new DefaultImpl()`, hai dòng initializer vẫn xuất hiện.

**(6) `new LeafImpl().work()`:** LeafImpl implements Leaf; Leaf kế thừa default method từ Parent. **Parent** thực sự khai báo default method nên Parent được initialized trước LeafImpl, dù Leaf đã initialized từ bước (2) và bản thân Leaf không khai báo default method. Phần thân `Parent.work` chạy *sau* khi object đã được tạo.

**(7) `Config.NON_CONST`:** lần truy cập field không phải compile-time constant mới kích hoạt `Config`, in `init Config` trước khi giá trị `1` được in.

**(8) `Config.ping()`:** static method của interface là trigger initialization; trong chương trình này Config **đã initialized ở bước (7)** nên chỉ còn lời gọi phương thức, không chạy lại `mark("Config")`.

### Biến thể đáng thử (không nằm trong stdout bên trên)

- Đưa `new DefaultImpl()` lên trước thao tác `Leaf.VALUE`: sẽ thấy `Defaulted` initialized trước khi bạn cần đọc field của Leaf.
- Bỏ toàn bộ thao tác `Leaf.VALUE` nhưng giữ `new LeafImpl()`: Parent vẫn initialized vì default method; **Leaf không buộc initialized** chỉ vì nó nằm giữa Parent và LeafImpl.
- Đổi `Defaulted.work()` từ `default` thành phương thức trừu tượng và bổ sung override `work()` trong DefaultImpl: khi initialize DefaultImpl, không còn yêu cầu initialize Defaulted chỉ từ quan hệ implements, nếu Defaulted không khai báo default method nào khác.
- Thêm override `work()` trong DefaultImpl mà **vẫn giữ default method khai báo ở Defaulted**: JVM vẫn phải initialize Defaulted khi initialize DefaultImpl.
- Thay `Config.CONST` bằng `Config.NON_CONST` ở bước (3): `Config` initialized sớm hơn vì field không còn là compile-time constant.

Các biến thể trên nhằm kiểm chứng **trigger**; không nên so sánh thứ tự tải lớp chính xác chỉ bằng output của initializer.

## 7. Default method có làm interface được initialized ngay khi load không?

**Không.** Một interface có default method vẫn có thể được JVM **load/link** mà chưa initialized:

```java
Class<?> type = Defaulted.class;
Class<?> checked = Class.forName(
        type.getName(), false, type.getClassLoader());
```

Đây là **đoạn minh họa trong cùng phạm vi nhìn thấy `Defaulted`**. `Class.forName(name, false, loader)` tìm/nạp và liên kết kiểu nhưng không yêu cầu initialized. Vì thế **định nghĩa default method không tự tạo trigger ngay tại thời điểm loading**.

Trigger ở đây là **initialization của concrete class triển khai**. Tại mốc đó, JVM xem quan hệ với các superinterface có khai báo default method và initialized chúng theo quy tắc.

Cũng không nên đánh đồng:
- **Được quyền gọi default method**: yêu cầu method resolution/dispatch trên một instance hợp lệ.
- **Khởi tạo interface chứa default method**: hệ quả bắt buộc khi class triển khai được initialized, kể cả chưa gọi method.
- **Khởi tạo một interface trực tiếp bằng static field/method**: trigger riêng, không phụ thuộc có instance hay default method.

## 8. Khi class override default method thì sao?

Xét class:

```java
interface Auditor {
    int READY = Hooks.touch();

    default String audit() {
        return "default";
    }
}

class Record implements Auditor {
    @Override
    public String audit() {
        return "override";
    }
}

class Hooks {
    static int touch() {
        System.out.println("Auditor initialized");
        return 1;
    }
}
```

Đây là **cụm khai báo minh họa**, chưa có `main`. Khi class `Record` được initialized, `Auditor` vẫn là superinterface **khai báo default method**, nên `Auditor` cần initialized ngay cả khi mọi instance của `Record` gọi bản override. Đây là quy tắc khởi tạo theo **khai báo phương thức**, không phụ thuộc thực tế dispatch sẽ chạy thân nào.

Có thể dùng `javap -c -p Auditor` và `javap -c -p Record` để quan sát bytecode, nhưng output `javap` phụ thuộc compiler và phiên bản JDK; **đừng** lấy một listing cụ thể thay cho quy tắc JLS/JVMS.

## 9. Thứ tự khởi tạo, vòng phụ thuộc và lỗi

### 9.1. Thứ tự khởi tạo trong interface

Interface không có `static { ... }`, nhưng những biểu thức khởi tạo trường không phải constant variable có thể gọi method. Các initializer trong interface được xử lý theo **thứ tự mã nguồn** trong giai đoạn initialization.

```java
interface Ordered {
    int A = log("A");
    int B = log("B");

    static int log(String name) {
        System.out.println(name);
        return 1;
    }
}
```

Nếu chương trình đọc `Ordered.A` khi Ordered chưa initialized, thứ tự output khởi tạo là `A` rồi `B`. Đọc tiếp `Ordered.B` không lặp lại output. Trong interface, các field là `final` nhưng vẫn có thể có biểu thức khởi tạo runtime.

### 9.2. Khởi tạo nhiều interface có default method

Nếu một class extends superclass và implements nhiều superinterface có default methods, các kiểu cần initialized được xác định theo quy tắc JLS/JVMS; nhiều interface có thể được initialized trước class, không thể mặc định chỉ interface được liệt kê đầu tiên sẽ chạy.

Đối với cây kế thừa phức tạp và diamond inheritance, cần áp dụng thuật toán khởi tạo của **JVMS `5.5** để dự đoán thứ tự giữa các interface cụ thể. Không tự suy luận rằng default method nào được chọn khi gọi sẽ là **interface duy nhất** cần initialized.

### 9.3. Nếu interface initializer thất bại

Nếu initializer của interface ném exception khi nó đang được initialized, việc khởi tạo có thể thất bại, ảnh hưởng đến class triển khai đang chờ nó. Lần sử dụng đầu có thể thấy `ExceptionInInitializerError` (khi ngoại lệ thường bị bọc), và những lần dùng về sau với **cùng định nghĩa kiểu** có thể thấy `NoClassDefFoundError: Could not initialize class ...`.

**Thực hành:** tránh đặt side effects khó kiểm soát, kết nối mạng, truy vấn database hoặc vòng phụ thuộc phức tạp trong field initializer của interface; việc tạo object triển khai có thể kích hoạt initializer ấy **sớm hơn dự kiến** nếu interface có default method.

### 9.4. Đa luồng

JVM đồng bộ hóa initialization cho mỗi định nghĩa class/interface. Hai luồng cùng lúc tạo instance của class triển khai không làm initializer của interface chạy thành hai lần thành công; một luồng có thể phải chờ quá trình khởi tạo do luồng khác thực hiện. Logic blocking trong initializer hoặc vòng phụ thuộc giữa các kiểu vẫn có thể tạo deadlock hoặc tình huống khó phân tích.

## 10. Các nhầm lẫn cần sửa

1. **“Interface không có constructor nên không bao giờ initialized.”** Sai. Interface initialized khi có trigger như đọc trường không phải constant variable, gọi static method hoặc class triển khai có điều kiện default method.
2. **“Tất cả interface của một class đều initialized khi `new`.”** Sai. Chỉ các superinterface khai báo default methods là nhóm có yêu cầu từ quy tắc class initialization; những interface khác cần trigger riêng.
3. **“Chỉ gọi default method mới initialized interface.”** Sai. Interface chứa default method thường đã initialized trước khi class triển khai hoàn tất initialization.
4. **“Class override default method nên khỏi initialized interface đó.”** Sai. Yếu tố quyết định là interface **khai báo default method**, không phải method nào được dispatch.
5. **“`Child extends Parent` nên initialize Child thì Parent chắc chắn initialized.”** Sai đối với interface: khởi tạo interface không tự kích hoạt initialization của superinterface.
6. **“Class implements Child, chỉ xét default method khai báo trực tiếp ở Child.”** Sai. Cần xét cả superinterface **gián tiếp** như Parent.
7. **“Mọi `static final` field đều không trigger initialization.”** Sai. Biểu thức khởi tạo runtime làm trường đó không phải *constant variable*.
8. **“`T.class` và `Class.forName(name)` tương đương.”** Sai. Class literal không tự initialize T; overload `Class.forName(String)` yêu cầu initialized kiểu được tìm.
9. **“Load interface có default method là lập tức chạy initializer.”** Sai. Loading/linking khác initialization.
10. **“Default method tồn tại là đủ để tạo instance interface.”** Sai. Interface không thể trực tiếp `new`; đối tượng vẫn có kiểu runtime là một class cụ thể.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Interface `I` khai báo `default void save() { }`. Class `C implements I` và override `save()`. Khi `new C()` lần đầu, `I` có cần initialized không?

**Đáp án:** Có, nếu chưa initialized. `I` là superinterface của class đang initialized và **khai báo default method**. Việc override không loại bỏ yêu cầu này.

**Câu 2.** Interface `I` chỉ khai báo `void save();` và `int X = trace();`. Class `C implements I`. `new C()` có tự bắt buộc chạy `trace()` của `I` không?

**Đáp án:** Không chỉ vì implements. `I` không khai báo default method. Nhưng nếu mã đọc `I.X`, nó sẽ initialized và chạy `trace()`.

**Câu 3.** `Parent` có default method, `Child extends Parent` nhưng không khai báo default method riêng. Đọc field runtime `Child.X` có tự initialize Parent không?

**Đáp án:** Không. Khởi tạo Child không tự lan lên superinterface Parent. Trái lại, khi một class triển khai Child được initialized, Parent cần initialized do default method mà Parent khai báo.

**Câu 4.** Tại sao đọc `I.X` với `int X = 123;` khác `int X = calculate();`?

**Đáp án:** `123` là constant expression, nên `X` là compile-time constant. Lời gọi `calculate()` không phải constant expression; vì vậy đọc `I.X` ở trường hợp hai là trigger initialization.

**Câu 5.** Một interface đã initialized và class triển khai nó tạo 100 object. Field initializer của interface chạy bao nhiêu lần?

**Đáp án:** Không chạy lại trong 100 lần tạo mới. Với cùng một định nghĩa kiểu runtime đã initialized thành công, phần khởi tạo interface được thực thi một lần.

## 12. Tư duy kiểm tra nhanh

```text
Quan sát interface I có được initialized hay không
                       |
          +------------+-------------+
          |                          |
          v                          v
Đang dùng chính I?       Đang initialize class C?
          |                          |
   +------+------+                  v
   |             |       I có phải superinterface
static method  non-constant  (trực tiếp/gián tiếp)
của I         static field   và KHAI BÁO default method?
   |             |                 /         \
   +------+------+               có          không
          |                        |             |
   trigger initialize I    initialize I       không chỉ
   (nếu chưa)              (nếu chưa)         vì implements

Đọc compile-time constant hoặc I.class không tự initialize I.
Khởi tạo một interface không tự initialize interface cha.
```

Khi phân tích thực tế, hãy hỏi **(1) đang initialize class nào, (2) interface nào trong cây kế thừa *khai báo* default method, (3) có static field/method riêng nào đã kích hoạt initialization trước đó không?** Ba câu hỏi này giải thích được cả trường hợp interface được initialized trước khi dùng default method, lẫn trường hợp một interface trừu tượng hoàn toàn vẫn chưa chạy initializer sau khi class triển khai đã được tạo.

### Nguồn tham khảo chính thức

- [Java Language Specification, 12.4 và 12.4.1 — Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.1)
- [Java Virtual Machine Specification, 5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5)
- [Java Language Specification, 9.4.3 — Interface Method Body (default methods)](https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-007">Đọc static field kế thừa: kiểu thực sự khai báo field</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc static field kế thừa: kiểu thực sự khai báo field

## 1. Vấn đề: `Child.value` làm lớp nào khởi tạo?

Xét hai lớp:

~~~java
class Parent {
    static int value = 42;
}

class Child extends Parent {
}
~~~

Java cho phép đọc biến qua hai cách **Parent.value** và **Child.value**. Trong cả hai trường hợp, **field thực sự thuộc Parent**: Child chỉ kế thừa khả năng truy cập nó, không nhận một bản sao biến static.

Theo **JLS §12.4.1**, khi một static field được sử dụng mà **không phải constant variable**, chỉ class/interface **thực sự khai báo field** mới bị khởi tạo do phép truy cập ấy. Vì vậy, **Child.value** có thể làm **Parent initialized**, nhưng không tự làm **Child initialized**. Đó là điểm trọng tâm của bài học.

Bài này giúp bạn xác định declaring class khi có inheritance, phân biệt field hiding và trường hằng được compiler in-line, đọc dấu vết initialization, kiểm tra bằng reflection và tránh nhầm lẫn giữa việc gọi tên Child với thực sự sử dụng thành viên của Child.

## 2. Ba tên có thể khác nhau

Để phân tích **Child.value**, cần tách ba câu hỏi:

| Khái niệm | Câu hỏi | Giá trị trong ví dụ |
| --- | --- | --- |
| **Qualifier** | Tên kiểu nào viết trước dấu chấm? | Child |
| **Declaring class** | Class/interface nào *khai báo* field được lookup chọn? | Parent |
| **Initialization target** | Kiểu nào cần initialization bởi việc đọc static field runtime này? | Parent |

Không nên kết luận kiểu cần khởi tạo chỉ bằng cách nhìn chữ bên trái dấu chấm.

Nếu **Child** không khai báo value, member lookup tìm thấy field kế thừa từ **Parent**. Không có cơ chế override field giống instance method. Nếu sau đó Child tự khai báo field cùng tên thì nó **hide** field của Parent: lúc này **Child.value** và **Parent.value** là hai field khác nhau.

### 2.1 Static field là trạng thái của khai báo kiểu

Static field không được sao chép thành một vùng nhớ mới cho mỗi subclass. Nếu Parent khai báo **static int count**, thì:

~~~java
long before = Child.count;
Child.count = 30;
long after = Parent.count;
~~~

Cả phép đọc và gán ở đây đều hướng tới **Parent.count** (giả sử Child không tự khai báo count và kiểu của count phù hợp). Giá trị **after** sẽ là 30. Không tồn tại một **Child.count** độc lập trong trường hợp này.

### 2.2 Trạng thái initialized không đồng nghĩa loaded

Ngay cả khi **Child** chưa thực thi static initializer, JVM vẫn có thể đã **load** hoặc **link** Child. Cơ chế loading tìm/tạo biểu diễn lớp; linking xác minh, chuẩn bị, phân giải tham chiếu; initialization mới thực thi logic static. Bài này đo và giải thích **điều kiện initialization**, không dùng số dòng log để khẳng định chính xác thời điểm loading hoặc linking.

## 3. Quy tắc khởi tạo từ Java Language Specification

Với một kiểu **T**, initialization xảy ra ngay trước các hành động active use đáng chú ý:

1. T là class và instance của T được tạo.
2. Một static method **do T khai báo** được gọi.
3. Một static field **do T khai báo** được gán.
4. Một static field **do T khai báo** được dùng, và field không phải **constant variable**.

Khi class T initialized, JVM khởi tạo những superclass chưa khởi tạo của T trước. Các superinterface khai báo default methods cũng có quy tắc liên quan. Interface initialization không mặc nhiên khởi tạo toàn bộ superinterface.

Đối với **Child.value** mà value chỉ được Parent khai báo, trường hợp số 4 liên quan đến **Parent**, không phải Child.

~~~text
Parent
  ├─ static int value = 42;
  └─ static { ... }    ← code chạy khi Parent initialized
       ▲
       │ extends
       │
Child
  └─ static { ... }    ← không chạy chỉ vì đọc Parent.value

        đọc Child.value
               │
               ▼
    lookup → Parent.value
               │
               ▼
    initialize Parent nếu cần
               │
               ▼
        đọc Parent.value
~~~

Quy tắc ngược chiều cần nhớ: **new Child()** hoặc active use của **field Child tự khai báo** sẽ khiến Child khởi tạo; khi đó Parent phải initialized trước Child. Điều này không có nghĩa đọc một trường kế thừa tự khởi tạo toàn bộ nhánh kế thừa.

## 4. Ví dụ chạy được số 1: đọc trường kế thừa

Tạo tệp **InheritedStaticFieldDemo.java**:

~~~java
public class InheritedStaticFieldDemo {
    static class Parent {
        static int count = initializeCount();

        static int initializeCount() {
            System.out.println("Parent: initialize count");
            return 42;
        }

        static {
            System.out.println("Parent: static block");
        }
    }

    static class Child extends Parent {
        static int own = 7;

        static {
            System.out.println("Child: static block");
        }
    }

    public static void main(String[] args) {
        System.out.println("1. Before inherited field");
        System.out.println("2. Child.count = " + Child.count);
        System.out.println("3. After inherited field");
        System.out.println("4. Parent.count = " + Parent.count);
        System.out.println("5. Child.own = " + Child.own);
        System.out.println("6. Child.count again = " + Child.count);
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac InheritedStaticFieldDemo.java
java InheritedStaticFieldDemo
~~~

Stdout chính xác:

~~~text
1. Before inherited field
Parent: initialize count
Parent: static block
2. Child.count = 42
3. After inherited field
4. Parent.count = 42
Child: static block
5. Child.own = 7
6. Child.count again = 42
~~~

### Giải thích các mốc

- Khi in dòng 1, chưa có phép đọc static field từ Parent hoặc Child.
- Để tính **Child.count** ở dòng 2, chương trình cần field **Parent.count**. JVM initialized Parent: trước tiên thực thi **initializeCount()**, sau đó khối static theo thứ tự xuất hiện trong khai báo Parent. Kết quả được in ra là 42.
- Sau dòng 3, **Child: static block** chưa xuất hiện. Đọc static field kế thừa không gây initialization của Child.
- Dòng 4 đọc lại cùng biến Parent.count nên không khởi tạo Parent lần nữa.
- Dòng 5 đọc **Child.own**; vì **own được Child khai báo** và không phải constant variable, JVM initialized Child. Parent đã initialized nên chỉ có dòng của Child được in.
- Dòng 6 đọc field thuộc Parent vốn đã được khởi tạo, nên không còn thông báo static nào.

**Điểm cần hiểu:** output chứng minh khi nào các **static initializers** được thực thi. Nó không khẳng định trước dòng 5 Child chưa hề được nạp hoặc chưa hề được liên kết.

## 5. Cách Java chọn field: member lookup và declaring class

Giả sử:

~~~java
class Base {
    public static long total = 100;
}

class Derived extends Base {
}
~~~

Cả **Base.total** lẫn **Derived.total** đều truy cập field do Base khai báo:

~~~java
long first = Base.total;
long second = Derived.total;
~~~

Nếu **Derived** chưa khai báo trường cùng tên, không có quyết định runtime dựa vào đối tượng thuộc Derived hay Base. Trường được **ràng buộc tĩnh** theo quy tắc tra thành viên ở Java.

Cách viết tên subclass có thể khiến người đọc nghĩ rằng một static initializer của Derived phải chạy. Trên thực tế, trường được chọn vẫn là **Base.total**.

Không nên khẳng định rằng compiler luôn phải phát sinh đúng cùng một tham chiếu constant pool cho cả hai biểu thức. Bytecode có thể chứa symbolic references và được JVM field resolution xử lý theo quy tắc đặc tả. Điều quan trọng là **field thực sự được phân giải tới khai báo nào**.

### 5.1 Đọc và ghi một trường kế thừa

Với Base.total ở trên:

~~~java
Derived.total = 250;
System.out.println(Base.total); // 250
~~~

Đây vẫn là phép gán và đọc **Base.total**. Nếu Base chưa initialized, phép gán static field của Base phải kích hoạt initialization Base trước khi cập nhật. Không có initialization Derived bắt buộc chỉ vì cú pháp dùng tên Derived.

### 5.2 Viết bằng tên declaring class sẽ rõ ràng hơn

Khi đã biết field là của Base, nên viết **Base.total**. Điều này bộc lộ nơi sở hữu biến, giúp code review dễ nhận ra thời điểm khởi tạo và tránh bị hiểu nhầm khi người khác thêm field trùng tên vào subclass.

## 6. Dùng biến tham chiếu để truy cập static field

Java cho phép dùng một primary expression bên trái dấu chấm khi truy cập field static:

~~~java
Derived reference = null;
long value = reference.total;
~~~

Đây là cú pháp hợp lệ dù IDE có thể cảnh báo. Vì total là static, giá trị của **reference** không được dereference để lấy field. Nếu reference là null, việc đọc **không ném NullPointerException** chỉ vì tham chiếu null.

Theo **JLS §15.11.1**, primary expression vẫn phải được **đánh giá**, nhưng kết quả của nó bị bỏ qua khi field là static. Điều này đặc biệt quan trọng nếu phần qualifier có side effect.

Ví dụ minh họa:

~~~java
static Derived obtainReference() {
    System.out.println("qualifier evaluated");
    return null;
}

// Đặt trong một method:
long result = obtainReference().total;
~~~

Nếu biểu thức này được thực thi, dòng **qualifier evaluated** vẫn xuất hiện. Sau khi tính xong qualifier, phép đọc vẫn truy cập field **Base.total** và chỉ yêu cầu initialization của Base nếu cần.

**Phân biệt:** nếu field không static, truy cập qua tham chiếu null có thể ném NullPointerException; và với **new Derived().total**, chính thao tác **new Derived()** đã khiến Derived initialized từ trước khi đọc static field, nên đó là ví dụ **không phù hợp** để kiểm tra riêng trigger của field access.

**Khuyến nghị:** static field nên được truy cập qua tên kiểu khai báo như **Base.total**, không qua reference hoặc tên subclass nếu muốn tránh hiểu sai.

## 7. Ngoại lệ quan trọng: compile-time constant không kích hoạt initialization

Quy tắc active use đối với **đọc static field** loại trừ **constant variable**.

Theo JLS §4.12.4, constant variable là biến **final**, có kiểu **primitive** hoặc **String**, được khởi tạo bằng **constant expression**. Với field như vậy, javac thường đưa giá trị hằng trực tiếp vào bytecode caller (inlining), thay vì phát sinh đọc field runtime.

| Khai báo static | Có phải constant variable? | Phép đọc tự kích hoạt initialization? |
| --- | --- | --- |
| **static final int LIMIT = 100;** | Có | Không. |
| **static final String NAME = "Java";** | Có | Không. |
| **static final long MASK = 1L << 3;** | Có | Không. |
| **static int count = 100;** | Không | Có, nếu class chưa initialized. |
| **static final int SIZE = Integer.parseInt("100");** | Không | Có. |
| **static final Integer BOXED = 100;** | Không | Có. |
| **static final Object TOKEN = new Object();** | Không | Có. |

Từ khóa **final** không tự tạo constant variable. Một field có giá trị chỉ được tính khi runtime, hoặc field có kiểu Object/Integer, không thuộc ngoại lệ này.

### 7.1 Ví dụ chạy được số 2: hằng kế thừa so với field tính lúc chạy

Tạo **InheritedConstantDemo.java**:

~~~java
public class InheritedConstantDemo {
    static class Parent {
        static {
            System.out.println("Parent initialized");
        }

        static final int LIMIT = 64;
        static final String NAME = "core";
        static final int COMPUTED = compute();

        static int compute() {
            System.out.println("Parent.compute()");
            return 9;
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("Child initialized");
        }
    }

    public static void main(String[] args) {
        System.out.println("limit = " + Child.LIMIT);
        System.out.println("name = " + Child.NAME);
        System.out.println("computed = " + Child.COMPUTED);
    }
}
~~~

~~~bash
javac InheritedConstantDemo.java
java InheritedConstantDemo
~~~

Stdout:

~~~text
limit = 64
name = core
Parent initialized
Parent.compute()
computed = 9
~~~

Giải thích:

1. Parent.LIMIT và Parent.NAME là **constant variables**. Việc đọc chúng thông qua tên **Child** không tự khởi tạo Parent hoặc Child.
2. **COMPUTED** không phải compile-time constant vì initializer gọi method. Lần đọc **Child.COMPUTED** làm JVM initialized **Parent**.
3. Trong Parent, static block xuất hiện trước khai báo COMPUTED; nên in **Parent initialized** rồi **Parent.compute()**.
4. Child không khai báo bất kỳ field nào đang được đọc, nên **Child initialized** không xuất hiện.

### 7.2 Hệ quả của inlining khi nâng cấp thư viện

Giả sử một thư viện định nghĩa **public static final int LIMIT = 64**. Client đã biên dịch có thể mang giá trị 64 trong bytecode. Nếu thư viện đổi LIMIT thành 128 mà không biên dịch lại client, client vẫn có thể thấy 64. Điều này khác với static field không phải constant variable, được đọc từ định nghĩa runtime.

Khi thiết kế API, dùng constant variable cho hằng bất biến về mặt hợp đồng; không coi đó là cơ chế cấu hình có thể thay riêng lúc triển khai.

## 8. Field hiding: khi Child khai báo cùng tên

Nếu Child có khai báo riêng:

~~~java
class Parent {
    static int score = 10;
}

class Child extends Parent {
    static int score = 20;
}
~~~

Lúc này **Parent.score** và **Child.score** là hai field độc lập. Field lookup chọn theo kiểu xuất hiện trong mã nguồn, không phải virtual override.

- **Parent.score** → đọc field Parent.
- **Child.score** → đọc field Child.
- **Parent ref = new Child(); ref.score** → đọc Parent.score dựa trên **kiểu tĩnh Parent**.
- **Child ref = new Child(); ref.score** → đọc Child.score dựa trên **kiểu tĩnh Child**.

Khi đọc field do Child khai báo, JVM initialized Child nếu chưa. Việc initialization Child cũng đòi hỏi superclass Parent initialized trước nếu cần.

### 8.1 Ví dụ chạy được số 3: field hiding có hai vùng nhớ

Tạo **StaticFieldHidingDemo.java**:

~~~java
public class StaticFieldHidingDemo {
    static int initial(String owner, int value) {
        System.out.println(owner + " field initialized");
        return value;
    }

    static class Parent {
        static int score = initial("Parent", 10);
    }

    static class Child extends Parent {
        static int score = initial("Child", 20);
    }

    public static void main(String[] args) {
        Parent parentRef = null;
        Child childRef = null;

        System.out.println("parentRef.score = " + parentRef.score);
        System.out.println("childRef.score = " + childRef.score);
        System.out.println("Parent.score = " + Parent.score);
        System.out.println("Child.score = " + Child.score);
    }
}
~~~

~~~bash
javac StaticFieldHidingDemo.java
java StaticFieldHidingDemo
~~~

Stdout:

~~~text
Parent field initialized
parentRef.score = 10
Child field initialized
childRef.score = 20
Parent.score = 10
Child.score = 20
~~~

Khi **parentRef.score** được đọc lần đầu, Parent initialized. Khi **childRef.score** được đọc, Child initialized vì score được Child khai báo. Parent đã initialized trước đó. Hai tham chiếu đều null, nhưng các phép truy cập static hợp lệ không dereference null.

Đây là ví dụ minh họa field hiding. Code thực tế nên viết **Parent.score** và **Child.score** cho rõ.

### 8.2 Không nhầm field hiding với method overriding

| Cú pháp | Cơ chế chính |
| --- | --- |
| **ref.field** | Field được lựa chọn theo **kiểu tĩnh** của ref và member lookup. |
| **ref.instanceMethod()** | Method có thể được override; **runtime dispatch** thường chọn implementation theo object thực. |
| **Child.field** kế thừa từ Parent | Vẫn là field thuộc Parent. |
| **Child.field** do Child tự khai báo | Field khác với Parent.field; Child là owner. |

Không áp dụng nguyên tắc **polymorphism của instance methods** cho static fields.

## 9. Reflection: kiểm tra field thực sự được khai báo ở đâu

Trong Java Reflection có ba API phù hợp:

- **Class.getField(name)** tìm **public fields**, kể cả field public được kế thừa.
- **Class.getDeclaredField(name)** chỉ tìm field **do Class đang xét tự khai báo** (bất kể public/private), không tìm field thừa hưởng.
- **Field.getDeclaringClass()** trả về Class/interface **thực sự chứa khai báo field**.

Đó là cách kiểm tra owner thay vì đoán dựa trên cú pháp **Child.field**.

### 9.1 Ví dụ chạy được số 4: reflection và initialization

Tạo **InheritedFieldReflectionDemo.java**:

~~~java
import java.lang.reflect.Field;

public class InheritedFieldReflectionDemo {
    static class Parent {
        public static int LEVEL = initialize();

        static int initialize() {
            System.out.println("Parent initialized");
            return 5;
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("Child initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        Field field = Child.class.getField("LEVEL");

        System.out.println("declaring class = "
                + field.getDeclaringClass().getSimpleName());
        System.out.println("declared by Child = "
                + (field.getDeclaringClass() == Child.class));

        try {
            Child.class.getDeclaredField("LEVEL");
            System.out.println("Child declares LEVEL");
        } catch (NoSuchFieldException expected) {
            System.out.println("Child does not declare LEVEL");
        }

        System.out.println("reflected value = " + field.get(null));
    }
}
~~~

~~~bash
javac InheritedFieldReflectionDemo.java
java InheritedFieldReflectionDemo
~~~

Stdout:

~~~text
declaring class = Parent
declared by Child = false
Child does not declare LEVEL
Parent initialized
reflected value = 5
~~~

**Child.class** và tra metadata không làm Child initialized. **getField("LEVEL")** trả field public kế thừa từ Parent; **getDeclaredField("LEVEL")** trên Child không tìm thấy vì Child không tự khai báo LEVEL. Khi **field.get(null)** đọc giá trị của static field, Parent được initialized trước khi trả 5. Không có dòng **Child initialized**.

**Chú ý:** các thao tác reflection khác như đọc static field, gọi static method hoặc khởi tạo instance có thể kích hoạt initialization. Đừng khái quát rằng “reflection luôn không khởi tạo class”; phải xét đúng API và thao tác.

## 10. Static field của interface cũng tuân theo owner

Field khai báo trong interface mặc nhiên là **public static final**. Tuy vậy, nó không nhất thiết là constant variable: initializer có thể là lời gọi method.

Ví dụ:

~~~java
interface ParentFlags {
    int DYNAMIC = compute();

    static int compute() {
        System.out.println("ParentFlags initialized");
        return 8;
    }
}

interface ChildFlags extends ParentFlags {
    String LABEL = announce();

    static String announce() {
        System.out.println("ChildFlags initialized");
        return "child";
    }
}
~~~

Khi chương trình đọc **ChildFlags.DYNAMIC**, field được **ParentFlags** khai báo và không phải constant variable. JVM khởi tạo **ParentFlags**, không tự khởi tạo ChildFlags chỉ vì tên qualifier là ChildFlags.

Nếu sau đó đọc **ChildFlags.LABEL**, đây là field được ChildFlags khai báo và không phải constant variable, nên ChildFlags initialized. Khởi tạo một interface **không mặc nhiên** làm toàn bộ superinterface khởi tạo, khác với class/superclass.

Cũng cần cẩn thận khi class implements nhiều interface cùng khai báo field cùng tên: **member lookup có thể mơ hồ**, gây lỗi biên dịch. Hãy truy cập qua tên declaring interface để làm rõ ý nghĩa.

## 11. Quy trình chẩn đoán truy cập Sub.FIELD

Khi cần dự đoán static block nào chạy, hãy lần lượt hỏi:

1. **FIELD thuộc member nào được Java chọn?** Kiểm tra Child tự khai báo hay kế thừa. Nếu có field hiding, xác định chính xác field qua kiểu tĩnh và member lookup.
2. **FIELD có thực sự là static không?** Instance fields có quy tắc truy cập khác.
3. **Declaring type của field là ai?** Có thể là superclass hoặc interface, dù code viết **Sub.FIELD**.
4. **FIELD có phải constant variable không?** final + primitive/String + constant expression thì phép đọc không tự trigger initialization.
5. **Declaring type đã initialized chưa?** Nếu chưa và field cần truy cập runtime, JVM sẽ initialize kiểu đó.
6. **Có hành động khác kích hoạt Sub trước đó không?** Ví dụ new Sub(), gọi static method do Sub khai báo hoặc đọc field riêng của Sub.
7. **Nếu Sub được khởi tạo thì superclass đã được khởi tạo chưa?** JVM đảm bảo superclass có liên quan được khởi tạo trước Sub.
8. **Có nhiều class loader định nghĩa lớp cùng tên không?** Trạng thái initialized gắn với từng định nghĩa runtime Class, không chỉ string tên lớp.

Một sơ đồ:

~~~text
    Gặp Sub.FIELD
         │
         ▼
  Lookup FIELD
         │
         ├── do Sub khai báo ──────→ owner = Sub
         │
         └── kế thừa từ Parent ───→ owner = Parent
                                    │
                                    ▼
                        static? constant variable?
                            │               │
                         Không             Có
                            │               │
                   quy tắc khác      đọc constant không
                                      tự yêu cầu initialize
                            │
                            ▼
                  static, non-constant
                            │
                            ▼
                    initialize owner
                     nếu chưa init
                            │
                            ▼
                   đọc hoặc gán field
~~~

Ở đây “owner” là class/interface khai báo field, không phải object đang dùng làm qualifier.

## 12. So sánh các thao tác dễ nhầm

Giả sử **Parent** khai báo **static int X**, **static final int LIMIT = 7**; **Child extends Parent** và khai báo **static int OWN**:

| Thao tác đầu tiên | Kiểu cần initialized do thao tác | Vì sao |
| --- | --- | --- |
| **int a = Child.X;** | Parent | X được Parent khai báo và không phải constant. |
| **Child.X = 9;** | Parent | Gán static field do Parent khai báo. |
| **int a = Parent.X;** | Parent | Cùng field Parent.X. |
| **int n = Child.LIMIT;** | Không do đọc hằng | LIMIT là constant variable. |
| **int a = Child.OWN;** | Parent rồi Child nếu cả hai chưa init | OWN thuộc Child; khi init Child phải init superclass. |
| **new Child()** | Parent rồi Child nếu chưa init | Active use do tạo instance Child. |
| **Class<?> c = Child.class;** | Không tự khởi tạo Child | Class literal không là trigger initialization. |
| **Class.forName(binaryNameChild)** | Child và các điều kiện superclass | Dạng một đối số yêu cầu initialization. |
| **Class.forName(binaryNameChild, false, loader)** | Không chủ động init Child | Overload có boolean false. |

Nếu Child là nested class, binary name thường chứa **$**; không thể dùng tên đơn giản **"Child"** bất kể package/nesting trong Class.forName. Mục tiêu của bảng là so sánh **nguyên nhân khởi tạo**, không phải thay thế quy tắc đặt tên nhị phân.

## 13. Quan sát bytecode bằng javap

Sau khi biên dịch các ví dụ:

~~~bash
javap -c -p InheritedStaticFieldDemo
javap -c -p InheritedConstantDemo
~~~

Phép đọc một static field runtime thường dùng bytecode **getstatic**, phép gán thường dùng **putstatic**. Compiler có thể in-line **constant variables** bằng các lệnh tải hằng, nên không có getstatic tương ứng ở caller.

Tuy nhiên, không nên suy từ một tên được thấy trong bytecode rằng lớp đó **chắc chắn khởi tạo**: JVM có field resolution xác định khai báo thực tế. Cũng không nên mặc định mọi compiler phát sinh constant pool references giống nhau dù có cùng ngữ nghĩa Java.

Khi muốn xem loading trên HotSpot/JVM hỗ trợ Unified Logging, có thể thử:

~~~bash
java -Xlog:class+load=info InheritedStaticFieldDemo
java -Xlog:help
~~~

**class+load** có thể hiện lớp đã nạp và nguồn của lớp. Dấu hiệu **static initializer chạy** vẫn cần quan sát riêng từ chương trình. Logging format và mức độ chi tiết phụ thuộc JVM/JDK; tránh coi một dòng class loading là bằng chứng về initialization.

## 14. Những lỗi và hiểu nhầm thường gặp

### “Đọc Child.counter không thấy Child static block.”

Hãy tìm nơi khai báo **counter**. Nếu Parent khai báo, Java đang truy cập **Parent.counter**, và quy tắc initialization chỉ yêu cầu Parent. Nếu Child tự khai báo trường cùng tên, đó là field hiding và kết luận khác.

### “static final chắc chắn đã initialized khi đọc.”

Sai đối với **constant variables**; đọc **static final int X = 5** không tự làm class initialized. Ngược lại, **static final int X = Integer.parseInt("5")** được tính runtime, vẫn có thể kích hoạt initialization. Kiểu Integer/Object cũng không phải compile-time constant variable.

### “Tôi thấy Parent và Child đều initialized sau khi đọc field kế thừa.”

Có thể đã có **active use khác** của Child trước đó, chẳng hạn **new Child()** hoặc **Child.OWN**. Để kiểm tra, tạo chương trình đơn giản mới và chỉ đọc **Child.inheritedField** trước mọi hành động khác. Khi static initialization đã thành công một lần, đọc tiếp không chạy lại các khối static.

### “reference.field là static, reference null thì phải NullPointerException.”

Không đúng. Khi field là static, giá trị qualifier bị bỏ qua sau khi đánh giá biểu thức. Nếu qualifier có side effect hoặc lỗi trong quá trình đánh giá, side effect/lỗi vẫn xảy ra. Với instance field, quy tắc dereference null khác.

### “Lần chạy này class đã load nên chắc chắn static block đã chạy.”

Không. Loading và initialization là những pha khác nhau. Một class có thể đã load/link mà chưa initialized. Đừng gộp ba khái niệm vào một từ “load class”.

### “Một JAR thay đổi constant mà client vẫn thấy giá trị cũ.”

Nếu constant variable đã được compiler in-line, cần biên dịch lại client. Đây là hệ quả tương thích nhị phân và cách mã hóa constant, không phải lỗi khởi tạo subclass.

### “NoSuchFieldError xảy ra khi dùng Sub.FIELD.”

Đó có thể là vấn đề khác phiên bản binary, class loader hoặc field resolution (ví dụ thư viện runtime không còn field caller đã biên dịch tham chiếu). Kiểm tra classpath/module path và phiên bản artifact; không tự quy lỗi cho quy tắc initialization.

## 15. Tự kiểm tra kiến thức

**1.** Parent khai báo **static int cache = 1**, Child extends Parent và có static block. Lần đầu in **Child.cache** làm lớp nào initialized?

**Đáp án:** Parent. cache thực sự thuộc Parent. Child không bị khởi tạo chỉ do phép đọc này.

**2.** Nếu Child tự khai báo **static int cache = 2**, lần đầu đọc Child.cache thì sao?

**Đáp án:** Đây là field mới thuộc Child. Nếu chưa lớp nào initialized, superclass Parent được khởi tạo trước rồi đến Child.

**3.** Parent khai báo **static final String VERSION = "v1"**. Vì sao Child.VERSION không tự init Parent?

**Đáp án:** VERSION là constant variable (final, kiểu String, constant expression). Phép đọc constant không phải active use kích hoạt initialization.

**4.** **Parent p = new Child(); System.out.println(p.score);** chọn score theo kiểu tĩnh hay kiểu runtime?

**Đáp án:** Theo kiểu tĩnh Parent và quy tắc field lookup. Nhưng **new Child()** đã khởi tạo Child và superclass từ trước; cần tách riêng tác động này khi phân tích output.

**5.** Vì sao **Child.class.getField("LEVEL").getDeclaringClass()** trả Parent.class?

**Đáp án:** getField có thể tìm field public được kế thừa, còn getDeclaringClass trả về nơi định nghĩa field thực sự.

**6.** Parent khai báo **static final Object TOKEN = new Object()**. Đọc **Child.TOKEN** có kích hoạt Parent không nếu chưa init?

**Đáp án:** Có. Object không phải primitive hoặc String constant variable; final không đủ để loại trừ initialization.

**7.** Interface A khai báo **int N = compute()**, interface B extends A. Đọc B.N lần đầu thì interface nào khởi tạo?

**Đáp án:** A, do N được A khai báo và không phải constant variable. Không tự init B chỉ vì B xuất hiện trong cú pháp.

**8.** Nếu cả Parent.field và Child.field có cùng tên do mỗi lớp tự khai báo, đổi biến tham chiếu từ kiểu Parent sang Child sẽ thay đổi trường nào được đọc?

**Đáp án:** Có thể thay đổi: field lookup theo kiểu tĩnh, nên hai khai báo khác nhau có thể được chọn. Không phải override động.

## 16. Ghi nhớ

**Quy tắc cốt lõi:** Khi truy cập static field kế thừa, hãy xác định **class/interface thực sự khai báo field**, không dựa vào tên subclass đứng trước dấu chấm.

- Field kế thừa vẫn thuộc declaring type; không có bản sao static tự động ở subclass.
- Đọc hoặc gán static field runtime yêu cầu initializing **declaring type** nếu cần.
- Nếu subclass tự khai báo field cùng tên, đó là **field hiding** và là một trường riêng.
- Compile-time constant variable là ngoại lệ: phép đọc hằng không tự kích hoạt initialization.
- Java field access ràng buộc tĩnh theo kiểu, khác instance method overriding.
- Reflection **getDeclaringClass()** giúp tìm owner; **getField** có thể thấy field inherited, còn **getDeclaredField** chỉ thấy field tự khai báo.
- Dùng tên declaring class khi viết code giúp tránh nhầm lẫn về trạng thái và thứ tự initialization.

### Tài liệu tham khảo

- **JLS §12.4.1, When Initialization Occurs:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.1
- **JLS §4.12.4, final Variables / Constant Variables:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4
- **JLS §15.11.1, Field Access Using a Primary:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.11.1
- **JLS §8.3.1.1, static Fields:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.3.1.1
- **Java SE 21 reflection Field API:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-008">Đồng bộ khởi tạo lớp giữa nhiều luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Đồng bộ khởi tạo lớp giữa nhiều luồng trong Java

## 1. Vấn đề: nhiều luồng đồng thời dùng một lớp chưa khởi tạo

Khi một lớp Java được sử dụng chủ động (*active use*) lần đầu, JVM phải bảo đảm lớp được **khởi tạo** trước khi hoạt động đó tiếp tục. Ví dụ, nhiều luồng cùng đọc `Configuration.PORT`, gọi `Registry.lookup()` hoặc thực hiện `new Service()`. Nếu lớp chưa khởi tạo, cần xác định **luồng nào thực thi mã khởi tạo tĩnh**, những luồng còn lại đợi thế nào và kết quả được công bố ra sao.

Đối với **một định nghĩa lớp runtime**, JVM bảo đảm:

- Chỉ **một luồng** thực hiện khởi tạo tĩnh của lớp trong một lượt initialization; các luồng khác yêu cầu khởi tạo cùng lớp phải đợi khi cần.
- Khi khởi tạo **thành công**, JVM ghi nhận lớp đã khởi tạo; những lần sử dụng sau không chạy lại `<clinit>`.
- Những luồng tiếp tục sau khi lớp được khởi tạo nhìn thấy kết quả khởi tạo theo bảo đảm *happens-before* của Java Memory Model.
- Nếu khởi tạo **thất bại**, lớp chuyển sang trạng thái lỗi (*erroneous*); JVM không tự chạy lại initializer trên cùng định nghĩa lớp.

Điểm quan trọng: một định nghĩa lớp được xác định bởi **(tên nhị phân, defining class loader)**. Hai class loader khác nhau tự định nghĩa cùng tên `com.example.Configuration` tạo thành hai kiểu runtime khác nhau, nên chúng có trạng thái và quá trình khởi tạo riêng.

Cơ chế này thuộc JVM, không cần người lập trình tự `synchronized` mọi static initializer. Tuy nhiên, nó **không** tự bảo đảm thread safety cho mọi thao tác sửa trường tĩnh sau khi initialization hoàn tất.

## 2. Phải phân biệt loading, linking và initialization

Vòng đời của một lớp gồm những hoạt động có ý nghĩa khác nhau:

1. **Loading:** tìm hoặc tạo biểu diễn runtime từ định nghĩa nhị phân của class/interface.
2. **Linking:** verification, preparation và resolution theo các quy tắc JVM.
3. **Initialization:** thực thi các khai báo khởi tạo biến tĩnh và khối `static` theo thứ tự được chỉ định; compiler thường tạo phương thức đặc biệt `<clinit>` cho mã cần thực thi.

Bài này phân tích bước **initialization**. Nạp được `SomeClass.class` không có nghĩa initializer đã chạy.

Ví dụ minh họa (đoạn class, chưa phải chương trình độc lập):

```java
class Settings {
    static final int PORT_CONSTANT = 8080;
    static final int PORT_RUNTIME = readPort();

    static int readPort() {
        System.out.println("Settings initialized");
        return 9090;
    }
}
```

- `Settings.PORT_CONSTANT` là *constant variable* (primitive `static final` được gán biểu thức hằng tại compile time); truy cập giá trị hằng này có thể được compiler inline và **không buộc** khởi tạo `Settings`.
- `Settings.PORT_RUNTIME` được gán bằng lời gọi phương thức, không phải constant variable. Truy cập `getstatic` tới trường này là active use, có thể kích hoạt khởi tạo.
- `Settings.class` hoặc `Class.forName("Settings", false, loader)` không chủ động yêu cầu khởi tạo lớp.
- `Class.forName("Settings", true, loader)` hoặc `Class.forName("Settings")` yêu cầu initialization; nếu lớp đã được khởi tạo thì không chạy lại.

Một luồng có thể nhìn thấy đối tượng `Class<?>` sớm hơn lúc mã `static` chạy. Đọc log *class loading* vì thế khác với đọc các dòng log in từ initializer.

## 3. Đặc tả JLS §12.4.2: trạng thái và khóa khởi tạo riêng của mỗi lớp

Java Language Specification §12.4.2 mô tả quy trình chi tiết với một **khóa khởi tạo duy nhất** `L_C` cho mỗi lớp hoặc interface `C`.

| Trạng thái của C | Ý nghĩa |
| --- | --- |
| Chưa được khởi tạo | Chưa có luồng nào thực hiện initialization |
| Đang được khởi tạo bởi luồng T | T chịu trách nhiệm chạy initialization |
| Đã khởi tạo thành công | Các yêu cầu sau sử dụng trạng thái đã công bố |
| Ở trạng thái lỗi (*erroneous*) | Initialization đã thất bại; không được thử lại trên chính lớp runtime đó |

Khi một luồng muốn khởi tạo `C`, JVM thực hiện thủ tục về mặt khái niệm:

1. Đồng bộ trên khóa `L_C` để kiểm tra trạng thái của `C`.
2. **Nếu đã khởi tạo thành công:** nhả khóa rồi kết thúc thủ tục.
3. **Nếu một luồng khác đang khởi tạo C:** nhả khóa và chờ quá trình đó kết thúc; sau khi tỉnh dậy, kiểm tra lại trạng thái.
4. **Nếu chính luồng hiện tại đang khởi tạo C:** xử lý yêu cầu khởi tạo lặp trong cùng luồng như hoàn tất bình thường, tránh tự đợi chính mình.
5. **Nếu C đang erroneous:** nhả khóa và ném `NoClassDefFoundError` cho yêu cầu khởi tạo về sau.
6. **Nếu chưa khởi tạo:** đánh dấu C đang được khởi tạo bởi luồng hiện tại, **nhả khóa khởi tạo**, thực hiện các bước liên quan và chạy initialization theo đặc tả.
7. Khi hoàn tất hoặc thất bại, đồng bộ để ghi trạng thái kết quả và thông báo các luồng đang chờ.

**Không giữ khóa L_C suốt toàn bộ mã initializer.** Đây là chi tiết dễ hiểu sai: JVM dùng khóa để điều phối trạng thái chờ và kết thúc; nó không cần giữ khóa init trong khi thực thi tất cả câu lệnh bên trong `<clinit>`. JVM còn có quyền tối ưu việc đồng bộ khi vẫn bảo toàn quan hệ *happens-before*.

Đừng dựa vào giả định `synchronized (C.class)` là cách công khai để nắm chính xác khóa `L_C` nội bộ của JVM. Mục đích của `synchronized (C.class)` là khóa monitor của đối tượng `Class` trong code ứng dụng; đặc tả initialization có quy tắc riêng.

### 3.1. Hai luồng nhìn thấy điều gì?

```text
Thời gian   Thread A                          Thread B
---------   --------------------------------  --------------------------------
t0          đọc Config.VALUE                 đọc Config.VALUE
t1          nhận quyền init Config             thấy Config đang init bởi A
t2          chạy code initialization          đợi kết quả initialization
t3          đánh dấu init thành công
t4                                            tỉnh dậy, kiểm tra trạng thái
t5          tiếp tục dùng VALUE               tiếp tục dùng VALUE đã khởi tạo
```

Không có bảo đảm **A sẽ luôn thắng**: scheduler, việc nạp trước đó và thời điểm gọi quyết định luồng nào đến trước. JVM chỉ bảo đảm **không có hai lượt initialization chạy song song trên cùng runtime Class**.

## 4. Ví dụ Java 17+: bốn luồng, một lượt static initialization

Chương trình dùng ba `CountDownLatch` để bốn worker cùng sẵn sàng rồi bắt đầu đọc `Configuration.PORT`. Để stdout xác định, worker chỉ ghi vào một phần tử mảng riêng; main chờ tất cả hoàn thành và in kết quả theo thứ tự đã biết.

Lưu file `ConcurrentClassInitializationDemo.java`:

```java
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class ConcurrentClassInitializationDemo {

    static class Configuration {
        static int initializationCount;
        static final int PORT = initialize();

        private static int initialize() {
            initializationCount++;
            System.out.println("init: begin");
            try {
                Thread.sleep(150); // Mô phỏng bước init chậm.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("init interrupted", e);
            }
            System.out.println("init: end");
            return 8080;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int workers = 4;
        int[] observed = new int[workers];

        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    observed[index] = Configuration.PORT;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    finished.countDown();
                }
            }, "reader-" + i);
            thread.start();
        }

        ready.await();
        start.countDown();
        finished.await();

        System.out.println("initializations=" +
                Configuration.initializationCount);
        System.out.println("values=" + Arrays.toString(observed));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ConcurrentClassInitializationDemo.java
java ConcurrentClassInitializationDemo
```

Stdout trong điều kiện chạy bình thường, không bị ngắt ngoài ý muốn:

```text
init: begin
init: end
initializations=1
values=[8080, 8080, 8080, 8080]
```

Giải thích:

1. `PORT` được gán bằng `initialize()`, nên **không phải** compile-time constant và có thể kích hoạt initialization khi đọc.
2. `ready` bảo đảm bốn worker đều đến điểm chờ trước khi `start` được mở. Nó tạo điều kiện để tranh chấp nhưng không bảo đảm tất cả thật sự chạy cùng một lệnh `getstatic` trong cùng một nano-giây.
3. Luồng nào đến `Configuration.PORT` trước có thể nhận quyền chạy initializer. Các luồng khác yêu cầu khởi tạo trong lúc đó phải chờ, không thực thi `initialize()` thêm lần nữa.
4. `initializationCount` vì vậy bằng 1. Hai dòng `init` chỉ xuất hiện một lượt.
5. Sau khi `finished.await()` hoàn tất, main thấy các phép ghi vào `observed` của worker; mảng được in theo thứ tự index nên kết quả không thay đổi do scheduler.
6. `Thread.sleep(150)` chỉ để làm thời gian khởi tạo dễ quan sát, **không** mang lại thread safety.

**Phân biệt hai bảo đảm đồng bộ:** Class initialization công bố giá trị `Configuration.PORT`; `CountDownLatch` trong ví dụ còn bảo đảm main đợi cả bốn worker xong trước khi đọc mảng `observed`.

## 5. Happens-before: tại sao trạng thái tĩnh ban đầu được công bố đúng?

Việc chỉ cho một luồng chạy initializer chưa đủ: JVM còn phải bảo đảm các luồng đã vượt qua bước kiểm tra initialization có thể quan sát giá trị được thiết lập trong quá trình đó.

Theo Java Memory Model và JLS §12.4.2, các thao tác đồng bộ trong initialization tạo/bảo toàn quan hệ **happens-before** cần thiết. Khi một lớp khởi tạo thành công, các luồng dùng lớp sau đó không cần tự tạo thêm hàng rào bộ nhớ chỉ để nhìn thấy đúng giá trị mà initializer đã gán.

Ví dụ (class minh họa):

```java
final class Catalog {
    static final java.util.Map<String, Integer> PORTS = build();

    private static java.util.Map<String, Integer> build() {
        java.util.Map<String, Integer> data = new java.util.HashMap<>();
        data.put("http", 8080);
        data.put("admin", 9090);
        return java.util.Collections.unmodifiableMap(data);
    }
}
```

Các luồng đọc `Catalog.PORTS` sau khi init thành công sẽ quan sát cấu trúc đã chứa hai entry. Map gốc chỉ là biến cục bộ trong `build()`, không bị lộ ra nơi khác để sửa sau khi khởi tạo.

Tuy nhiên, việc công bố đúng **trạng thái ban đầu** không làm các thao tác ghi về sau tự đồng bộ:

```java
class Counter {
    static int count;

    static void increment() {
        count++; // Không atomic khi nhiều thread cùng cập nhật.
    }
}
```

`Counter.count++` vẫn là thao tác đọc–sửa–ghi có thể race giữa các thread. Cần `AtomicInteger`, `synchronized`, lock hoặc thiết kế bất biến thích hợp. `volatile` riêng lẻ cũng **không** biến `count++` thành thao tác atomic.

## 6. Yêu cầu initialization lặp lại trên chính luồng đang khởi tạo

Điểm khác biệt giữa **luồng khác** và **chính luồng đang khởi tạo** là thiết yếu.

Một initializer có thể gọi static method của chính lớp hoặc gián tiếp quay lại cần khởi tạo cùng lớp. JVM nhận ra trạng thái “đang được khởi tạo bởi thread hiện tại” và không đợi lại. Nhưng vì initializer chưa hoàn tất, những static field được gán **sau** vị trí hiện tại vẫn có thể đang ở giá trị mặc định từ bước preparation.

Lưu ví dụ độc lập `RecursiveInitializationDemo.java`:

```java
public class RecursiveInitializationDemo {

    static class State {
        static int early = readLater();
        static int later = 42;

        static int readLater() {
            return later;
        }
    }

    public static void main(String[] args) {
        System.out.println("early=" + State.early);
        System.out.println("later=" + State.later);
    }
}
```

```bash
javac RecursiveInitializationDemo.java
java RecursiveInitializationDemo
```

Stdout:

```text
early=0
later=42
```

Tại lúc chạy `readLater()` để gán `early`, luồng hiện tại đã ở trong quá trình khởi tạo `State`. `later` đã có giá trị mặc định `0` từ preparation nhưng lệnh `later = 42` chưa đến lượt chạy. Khi initializer hoàn thành, `later` mới bằng `42`.

Đây là lời cảnh báo về **thứ tự khai báo static field** và vòng gọi trong initializer. Khởi tạo lặp **cùng thread** không chờ vô hạn, nhưng có thể khiến logic thấy trạng thái khởi tạo dở dang.

## 7. Failure: nếu initializer thất bại thì các luồng còn lại ra sao?

Nếu static initializer ném một ngoại lệ chưa được xử lý, JVM đánh dấu lớp ở trạng thái **erroneous**, đánh thức các luồng đang đợi, và không thử khởi tạo lại định nghĩa lớp đó.

- Nếu nguyên nhân được ném ra **không phải một `Error`**, JVM thường bọc nó bằng `ExceptionInInitializerError` cho luồng thực hiện initializer (theo quy tắc của đặc tả).
- Nếu nguyên nhân vốn là `Error`, JVM truyền tiếp đối tượng lỗi thay vì bọc giống một exception thông thường.
- Các thread đã đợi và những lần active use sau khi lớp ở trạng thái erroneous thường nhận `NoClassDefFoundError`. Cần xem cả nguyên nhân ban đầu trong log, vì thông báo của lượt sau có thể không chỉ ra toàn bộ lỗi gốc.

Ví dụ độc lập `FailedInitializationDemo.java`:

```java
public class FailedInitializationDemo {

    static class Broken {
        static final int VALUE = parse();

        private static int parse() {
            throw new IllegalArgumentException("invalid configuration");
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                System.out.println(Broken.VALUE);
            } catch (LinkageError error) {
                System.out.println("attempt-" + attempt + ": " +
                        error.getClass().getSimpleName());
            }
        }
    }
}
```

```bash
javac FailedInitializationDemo.java
java FailedInitializationDemo
```

Stdout trong ví dụ:

```text
attempt-1: ExceptionInInitializerError
attempt-2: NoClassDefFoundError
```

Lượt đầu `Broken.VALUE` kích hoạt `parse()`, nó ném `IllegalArgumentException`. JVM bọc trong `ExceptionInInitializerError` và đánh dấu `Broken` erroneous. Lượt hai, JVM không gọi `parse()` nữa.

Việc `catch (LinkageError)` có mặt trong ví dụ chỉ để **quan sát tên lỗi**; trong hệ thống thật, không nên tùy tiện nuốt các `Error` quan trọng rồi tiếp tục chạy như thể trạng thái ứng dụng vẫn hợp lệ.

### Failure từ lớp cha hoặc superinterface

Khi initialization của một lớp phải khởi tạo superclass và những superinterface có khai báo default method theo quy tắc đặc tả, lỗi ở một phụ thuộc cần khởi tạo có thể làm quá trình khởi tạo lớp con không thành công. JVM không bỏ qua lỗi để hoàn thành lớp con.

Phải phân biệt hai dạng lỗi:
- **Thiếu class lúc loading/linking**: chẳng hạn `NoClassDefFoundError` vì thiếu superclass cần thiết.
- **Class đã được định nghĩa nhưng initializer thất bại**: nguyên nhân thường nằm ở mã `static`, cấu hình hoặc tác vụ được gọi trong init.

Cùng tên exception ở log không đủ để xác định loại lỗi; phải xem thời điểm, stack trace và chuỗi `Caused by`.

## 8. Deadlock giữa các lớp khi những luồng khác nhau khởi tạo chéo

JVM tránh trường hợp **một thread tự chờ chính nó khi khởi tạo lại cùng class**, nhưng không bảo đảm tránh mọi kiểu deadlock trong hệ thống nhiều lớp và nhiều thread.

Giả sử:
- Thread T1 đang khởi tạo `A`. Trong static initializer của `A`, nó cần sử dụng `B` và yêu cầu `B` khởi tạo xong.
- Thread T2 đang khởi tạo `B`. Trong static initializer của `B`, nó lại cần `A` hoàn tất initialization.

```text
T1: owns initialization of A -> needs B -> waits for T2
T2: owns initialization of B -> needs A -> waits for T1
```

Đây là **vòng chờ**; chương trình có thể treo. Khác với ví dụ đọc `State.later` ở cùng thread, hai thread không được bỏ qua một lớp do thread khác đang khởi tạo.

Để giảm nguy cơ:

1. Giữ static initializer ngắn; ưu tiên xây dựng dữ liệu bất biến và không có side effect dài.
2. Tránh trong static initializer những thao tác `Future.get()`, `Thread.join()`, callback, remote calls hoặc đợi singleton của lớp có quan hệ phụ thuộc vòng.
3. Tách các bước bootstrapping có khả năng chờ/block thành thủ tục ứng dụng có thứ tự rõ ràng, ngoài `<clinit>` khi phù hợp.
4. Không mở thread trong initializer rồi chờ thread ấy hoàn tất nếu thread mới có thể tham chiếu trở lại lớp đang init.
5. Khi ứng dụng treo lúc start, lấy thread dump (ví dụ `jcmd <pid> Thread.print` khi attach khả dụng) và đối chiếu stack của các thread tham gia class initialization.

Không nên cho rằng mọi công cụ phát hiện deadlock của object monitor đều tự mô tả đầy đủ vòng đợi liên quan initialization; thread dump và đồ thị phụ thuộc vẫn cần phân tích.

## 9. Ranh giới ClassLoader: cùng tên không nhất thiết cùng lượt khởi tạo

Trong hệ thống plugin, container hoặc môi trường reload động:

```text
ApplicationLoader
  |- Config (defined by application loader; one init state)
  |
  |- PluginLoader A
  |    |- PluginA (uses shared Config)
  |
  |- PluginLoader B
       |- PluginB (uses shared Config)
```

Nếu cả hai plugin **ủy quyền** cho loader cha và nhận chính lớp `Config` do cha định nghĩa, chúng chia sẻ trạng thái initialization duy nhất của `Config`.

Ngược lại:

```text
PluginLoader A -> defines com.example.Config -> init state A
PluginLoader B -> defines com.example.Config -> init state B
```

Nếu mỗi plugin **tự định nghĩa** `com.example.Config`, JVM xem đó là hai runtime Class riêng. Mỗi bên có static state, một lần initialization thành công (nếu thành công) và cơ chế điều phối riêng.

| Điều kiện | Có dùng chung initialization không? |
| --- | --- |
| Nhiều thread dùng cùng `Class<?>` | Có |
| Hai loader con cùng delegate tới một định nghĩa từ cha | Có |
| Hai loader khác nhau tự định nghĩa cùng binary name | Không |
| Một `Class<?>` đã khởi tạo thành công từ trước | Không khởi tạo lại |
| Một `Class<?>` đã erroneous | Không tự thử lại; yêu cầu init về sau bị lỗi |

Vì thế, việc thấy log “initialized” hai lần chưa đủ kết luận JVM phá quy tắc; phải so sánh **defining loader** của hai bản lớp. Ngược lại, một lớp bị erroneous trong loader cũ không làm một định nghĩa runtime hoàn toàn mới ở loader khác tự động erroneous.

## 10. Ứng dụng: initialization-on-demand holder cho lazy singleton

Một ví dụ thực dụng của đồng bộ JVM là **initialization-on-demand holder idiom**. Instance chỉ được tạo khi code lần đầu yêu cầu static field của lớp `Holder`.

Lưu file `LazyHolderDemo.java`:

```java
public class LazyHolderDemo {

    static final class Service {
        private Service() {
            System.out.println("Service constructed");
        }

        static Service instance() {
            return Holder.INSTANCE;
        }

        private static final class Holder {
            static final Service INSTANCE = new Service();
        }
    }

    public static void main(String[] args) {
        System.out.println("before");
        Service a = Service.instance();
        Service b = Service.instance();
        System.out.println("same=" + (a == b));
    }
}
```

```bash
javac LazyHolderDemo.java
java LazyHolderDemo
```

Stdout:

```text
before
Service constructed
same=true
```

Lần đầu `Holder.INSTANCE` được đọc, JVM điều phối initialization của `Holder`. Mã tạo `Service` chạy một lần cho cùng `Holder` runtime, và tham chiếu được công bố an toàn cho các thread. Cách này tránh phải đặt `synchronized` trên mọi lời gọi `instance()` chỉ để khởi tạo lazy singleton.

**Giới hạn:** một loader khác tự định nghĩa `Holder` có thể tạo singleton riêng; lỗi trong constructor làm initialization của `Holder` thất bại; trạng thái mutable bên trong `Service` vẫn cần thread safety riêng.

## 11. Ngộ nhận và lỗi thường gặp

| Ngộ nhận | Thực tế |
| --- | --- |
| “`static` chạy một lần duy nhất trong cả JVM.” | Một lần khởi tạo thành công cho **mỗi định nghĩa runtime Class**; nhiều loader có thể có nhiều định nghĩa. |
| “Hai thread có thể chạy cùng `<clinit>` song song.” | JVM điều phối để chỉ một thread thực hiện initialization của cùng lớp. |
| “JVM giữ `synchronized(C.class)` qua toàn bộ `<clinit>`.” | Khóa khởi tạo là cơ chế đặc tả riêng; không nên đồng nhất với monitor ứng dụng. |
| “Cứ đọc `static final` là khởi tạo lớp.” | Trường constant variable có thể được inline nên không bắt buộc init. |
| “Initialization xong thì mọi cập nhật static đều thread-safe.” | Cập nhật mutable sau đó vẫn cần đồng bộ thích hợp. |
| “Nếu `<clinit>` lỗi, thread sau sẽ thử lại.” | Cùng runtime Class sẽ giữ trạng thái erroneous; yêu cầu init tiếp bị lỗi. |
| “Recursive static call trên cùng thread luôn deadlock.” | JVM cho phép khởi tạo đệ quy cùng thread kết thúc bình thường; nhưng có thể thấy giá trị mặc định. |
| “JVM không thể deadlock lúc class init.” | Hai thread chờ hai lớp khởi tạo chéo có thể deadlock. |
| “Log trong `static` cho biết thứ tự loading.” | Log đó phản ánh initialization, không nhất thiết loading. |

## 12. Chẩn đoán một lỗi initialization đa luồng

Khi ứng dụng treo hoặc nhiều luồng nhận `NoClassDefFoundError` ở lần dùng lớp đầu:

1. **Xác định đúng lớp runtime.** In `type.getName()` và `type.getClassLoader()` khi có đối tượng `Class<?>`; `null` ở `getClassLoader()` thường chỉ bootstrap loader.
2. **Xác định active use đã kích hoạt init.** Chẳng hạn `new`, truy cập static field không phải hằng biên dịch, gọi static method hay API reflection yêu cầu init.
3. **Đọc toàn bộ static field initializer và `static {}`.** Chú ý thứ tự khai báo, method call gián tiếp và initializer lớp cha.
4. **Tìm thread đầu tiên khởi tạo.** Lấy thread dump để xác định nơi luồng đang chạy/chờ; đừng chỉ xem exception cuối cùng của các thread bị đánh thức.
5. **Tìm lỗi gốc.** Lần đầu có thể báo `ExceptionInInitializerError` với `Caused by` hữu ích; lần sau thường chỉ `NoClassDefFoundError` do trạng thái erroneous.
6. **Kiểm tra vòng phụ thuộc class A–B hoặc chờ task được khởi động từ `<clinit>`.**
7. **Với plugin, xác định defining loader và bản JAR thực sự.** Hai class cùng tên không đảm bảo chung một initialization.
8. **Nếu initializer đã thành công nhưng dữ liệu vẫn race, kiểm tra các ghi sau đó** và các quan hệ happens-before của ứng dụng thay vì sửa nhầm luồng init.

Trên JVM Java 17+ hỗ trợ unified logging, có thể quan sát quá trình **loading** bằng:

```bash
java -Xlog:class+load=info ConcurrentClassInitializationDemo
```

Đây là log loading; để biết initializer thực thi lúc nào, hãy kết hợp log từ mã `<clinit>` và stack/thread dump. Lệnh `jcmd <pid> Thread.print` cần quyền attach thích hợp và có thể thay đổi khả dụng theo môi trường.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Ba thread cùng chạm vào `Config.VALUE` lần đầu, cùng một defining loader. Mã `static` của `Config` có thể in “init” ba lần không?

**Trả lời:** Không. Chỉ một thread thực hiện initialization cho cùng runtime Class; các thread khác chờ khi cần rồi dùng kết quả.

**Câu 2.** Khởi tạo thành công rồi, hai thread thực hiện `Counter.count++`. Có chắc tăng đúng hai đơn vị không?

**Trả lời:** Không. `count++` không atomic; synchronization lúc initialization không bảo vệ các cập nhật sau đó.

**Câu 3.** Vì sao `State.early` bằng 0 trong ví dụ đệ quy?

**Trả lời:** `State.readLater()` chạy trước câu lệnh gán `later = 42` trong cùng quá trình initialization; lúc đó `later` còn giá trị mặc định từ preparation.

**Câu 4.** Vì sao truy cập `Broken.VALUE` lần đầu ra `ExceptionInInitializerError`, lần hai ra `NoClassDefFoundError`?

**Trả lời:** Lượt đầu initializer ném exception khiến JVM đánh dấu lớp erroneous; những lượt sau không chạy lại initializer.

**Câu 5.** Hai plugin mỗi bên tự định nghĩa `Config` cùng tên, và mỗi bên in “initialized” một lần. Có trái đặc tả không?

**Trả lời:** Không. Mỗi defining loader có một runtime Class riêng, nên mỗi lớp có lượt initialization riêng.

**Câu 6.** Thread A khởi tạo class A rồi cần B; thread B khởi tạo B rồi cần A. Có thể deadlock không?

**Trả lời:** Có. Hai yêu cầu chờ initialization qua hai thread tạo vòng chờ, khác với đệ quy cùng thread.

## 14. Kết luận và tài liệu chuẩn

Đồng bộ khởi tạo lớp là **hợp đồng của JVM theo từng runtime Class**: chỉ một thread thực hiện initialization, thread khác đợi kết quả khi cần, kết quả thành công được công bố đúng theo quan hệ *happens-before*, và trạng thái lỗi được ghi nhận để ngăn việc tự thử lại. Hãy tận dụng đặc tính này khi xây dựng dữ liệu tĩnh bất biến hoặc lazy holder, đồng thời tách rõ các vấn đề mutable state, vòng phụ thuộc giữa lớp và ranh giới class loader.

Tài liệu chính thức:

- JLS 17 §12.4.1, *When Initialization Occurs*: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1
- JLS 17 §12.4.2, *Detailed Initialization Procedure*: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2
- JVMS 17 §5.5, *Initialization*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- JLS 17 §17.4.5, *Happens-before Order*: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-009">Khởi tạo đệ quy, phụ thuộc vòng và giá trị chưa hoàn thiện</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo đệ quy, phụ thuộc vòng và giá trị chưa hoàn thiện trong Java

## 1. Vấn đề: một lớp được dùng khi chính nó còn đang khởi tạo

Một lớp Java có thể đang chạy các biểu thức gán trường `static` hoặc khối `static { ... }` thì lại truy cập đến chính lớp đó — trực tiếp hoặc thông qua một lớp khác. Java cho phép tình huống này trong nhiều trường hợp, nhưng kết quả không phải lúc nào cũng trực giác: **một trường `static` có thể bị đọc khi mới mang giá trị mặc định, trước khi initializer của nó chạy xong**.

Hai hiện tượng cần phân biệt ngay:

1. **Đệ quy khởi tạo trên cùng một thread:** đang khởi tạo lớp A, mã gọi đến A lần nữa (có thể theo vòng A → B → A). JVM nhận ra cùng thread đã khởi tạo A dở dang và **không chạy lại** bộ khởi tạo của A; lần yêu cầu đệ quy được coi là hoàn tất cho luồng đó. Mã có thể đọc những trường A chưa được gán giá trị cuối cùng.
2. **Vòng phụ thuộc khởi tạo giữa nhiều thread:** thread T1 đang khởi tạo A rồi cần B, trong khi T2 đang khởi tạo B rồi cần A. Mỗi thread có thể phải chờ thread kia hoàn tất và tạo thành **deadlock**.

Hai trường hợp đều bắt nguồn từ vòng phụ thuộc nhưng cơ chế và triệu chứng khác nhau. **Một vòng A → B → A trên cùng thread không tự động gây StackOverflowError hoặc deadlock.** Nó thường tạo kết quả “lạ” do thấy trạng thái chưa hoàn thiện. Vòng xuyên thread có thể chặn nhau mãi mà không ném exception.

## 2. Các giai đoạn liên quan đến giá trị của trường `static`

Trước khi phân tích vòng phụ thuộc, cần xác định thời điểm JVM tạo giá trị:

```text
Loading
   ↓
Linking
   ├── Verification
   ├── Preparation: cấp phát/đặt giá trị mặc định cho static fields
   └── Resolution: có thể thực hiện sớm hoặc trì hoãn
   ↓
Initialization
   ├── đảm bảo các lớp cha cần thiết đã được khởi tạo
   ├── chạy các static field initializers theo thứ tự khai báo
   └── chạy các static blocks theo thứ tự tương ứng
   ↓
Initialized successfully
```

**Preparation** chuẩn bị trường `static` bằng giá trị mặc định: số nguyên `0`, `boolean=false`, tham chiếu `null`, v.v. **Initialization** thực hiện các câu lệnh/biểu thức gán trong mã nguồn, theo thứ tự văn bản của trường và static block. Vì vậy, với:

```java
static int count = calculate();
static String label = "ready";
```

trong lúc `calculate()` chưa trả về, `count` và `label` có thể vẫn là `0` và `null` đối với mã đang khởi tạo đệ quy trên **cùng thread**.

Có ngoại lệ về **constant variables** được biểu diễn bằng thuộc tính `ConstantValue`: JLS quy định chúng được gán trong thủ tục khởi tạo trước khi chạy các initializer thông thường, và bên sử dụng có thể đã inline hằng lúc biên dịch. Do đó không suy diễn mọi `static final` đều quan sát được giá trị mặc định; chỉ các trường `static` **không phải compile-time constant** là trường hợp cần đặc biệt lưu ý ở bài này.

Đừng đồng nhất *đã load*, *đã link* với *đã initialize*. Các lần đọc `Class<?>` hoặc `T.class` không tự kích hoạt việc chạy bộ khởi tạo của T; còn việc đọc trường static không phải hằng thời gian biên dịch thường yêu cầu khởi tạo lớp **khai báo** trường đó.

## 3. JVM xử lý yêu cầu khởi tạo đệ quy như thế nào?

Theo quy trình tại **JLS `12.4.2**, mỗi lớp hoặc interface runtime có một trạng thái khởi tạo, có thể mô tả khái quát:

| Trạng thái | Ý nghĩa khi một thread yêu cầu initialize |
|---|---|
| Chưa khởi tạo | Thread bắt đầu thủ tục khởi tạo nếu được chọn để làm việc đó |
| Đang được thread **khác** khởi tạo | Thread yêu cầu phải chờ và kiểm tra lại sau khi trạng thái đổi |
| Đang được **chính thread hiện tại** khởi tạo | Yêu cầu đệ quy hoàn tất bình thường; không chạy lại `<clinit>` |
| Đã khởi tạo thành công | Dùng ngay, không chạy lại `<clinit>` |
| Ở trạng thái lỗi (erroneous) | Tiếp tục sử dụng theo đường kích hoạt init sẽ thất bại, thường với `NoClassDefFoundError` |

JVM có cơ chế đồng bộ hóa khởi tạo riêng cho từng **runtime class/interface**. Không cần và không nên cố tự quản lý “init lock” qua `synchronized (SomeClass.class)` để kiểm soát chu trình khởi tạo. Việc JVM triển khai khóa khởi tạo không nhất thiết tương đương Java monitor mà mã ứng dụng dùng.

Với trường hợp đệ quy **cùng thread**, chuỗi được hình dung như sau:

```text
Thread T:
  initialize A
      A.<clinit> bắt đầu
          đọc B.value
              initialize B
                  B.<clinit> bắt đầu
                      đọc A.value
                          A đang init bởi T
                          → yêu cầu đệ quy được coi đã xử lý
                          → trả về giá trị hiện có của A.value
                  B.<clinit> hoàn tất
          A.<clinit> hoàn tất
  A đã khởi tạo thành công
```

Điều cốt lõi: **“yêu cầu khởi tạo đệ quy hoàn tất” không có nghĩa `A.<clinit>` đã chạy xong**. Nó chỉ giúp cùng thread tiếp tục mà không khởi tạo A vô hạn. Trạng thái trường A vẫn phản ánh những gì đã được gán **tới thời điểm truy cập**.

## 4. Ví dụ đầy đủ: vòng A → B → A và giá trị còn mặc định

Tạo file `CircularInitDemo.java`:

```java
public class CircularInitDemo {
    static class A {
        static int value = compute();

        static int compute() {
            System.out.println("A computes using B");
            return B.value + 1;
        }

        static {
            System.out.println("A finalized value=" + value);
        }
    }

    static class B {
        static int value = compute();

        static int compute() {
            System.out.println("B computes using A");
            return A.value + 10;
        }

        static {
            System.out.println("B finalized value=" + value);
        }
    }

    public static void main(String[] args) {
        boolean bFirst = args.length > 0 && args[0].equals("B");
        System.out.println("Start: " + (bFirst ? "B" : "A"));

        if (bFirst) {
            System.out.println("B.value=" + B.value);
            System.out.println("A.value=" + A.value);
        } else {
            System.out.println("A.value=" + A.value);
            System.out.println("B.value=" + B.value);
        }
    }
}
```

Biên dịch một lần bằng Java 17+:

```bash
javac CircularInitDemo.java
```

### 4.1. Truy cập A trước

```bash
java CircularInitDemo
```

Stdout chính xác:

```text
Start: A
A computes using B
B computes using A
B finalized value=10
A finalized value=11
A.value=11
B.value=10
```

Phân tích từng bước:

1. Đọc `A.value` trong `main` buộc JVM khởi tạo A. Trước khi các phép gán chạy, cả `A.value` và `B.value` đều có giá trị mặc định `0` khi lớp tương ứng đã được chuẩn bị.
2. `A.compute()` in dòng đầu, rồi yêu cầu `B.value`; vì B chưa khởi tạo, cùng thread bắt đầu khởi tạo B.
3. `B.compute()` in dòng thứ hai, rồi yêu cầu `A.value`. A **đang** khởi tạo bởi chính thread đó, nên JVM cho phép yêu cầu đệ quy hoàn tất mà không chạy lại initializer của A. **Phép gán cho `A.value` vẫn chưa xong**, nên B đọc được `0`.
4. B tính `0 + 10 = 10`, gán vào `B.value`, rồi chạy static block in `B finalized value=10`.
5. Trở lại A: `B.value` giờ là `10`; A tính `10 + 1 = 11`, gán vào `A.value` rồi in `A finalized value=11`.
6. `main` nhận `A.value=11`, sau đó đọc `B.value=10`; các lớp đã khởi tạo nên không chạy initializer lần nữa.

**Kết luận của ví dụ:** JVM không “đợi cho đủ dữ liệu” khi chính thread đang chạy initializer lại đọc một trường chưa gán. Nó trả giá trị hiện có, ở đây là `0`.

### 4.2. Truy cập B trước

Chạy chương trình ở **một JVM mới** với tham số `B`:

```bash
java CircularInitDemo B
```

Stdout chính xác:

```text
Start: B
B computes using A
A computes using B
A finalized value=1
B finalized value=11
B.value=11
A.value=1
```

Lần này B khởi tạo trước và bị đọc ngược khi chưa gán, nên `B.value=0` trong lúc tính A. Sau đó `A.value=1` và `B.value=11`. **Cùng source code nhưng điểm truy cập đầu tiên khác nhau dẫn đến trạng thái cuối khác nhau.**

Hai lần chạy phải ở **hai tiến trình JVM riêng biệt**, vì trong cùng JVM đã khởi tạo thành công A/B thì đổi thứ tự đọc sau đó **không khởi tạo lại** lớp. Đây là một lỗi thường gặp khi viết test thực nghiệm về class initialization.

## 5. Đệ quy trong chính một lớp: không nhất thiết có hai lớp

Vòng phụ thuộc cũng xuất hiện khi static initializer của lớp gọi một static method của chính lớp đó, và method đọc một trường chưa được gán.

Tạo `SelfInitDemo.java`:

```java
public class SelfInitDemo {
    static class Settings {
        static int first = computeFirst();
        static int second = 8;

        static int computeFirst() {
            System.out.println("computeFirst sees second=" + second);
            return second + 1;
        }

        static {
            System.out.println(
                    "static block sees first=" + first
                    + ", second=" + second
            );
        }
    }

    public static void main(String[] args) {
        System.out.println("first=" + Settings.first);
        System.out.println("second=" + Settings.second);
    }
}
```

Lệnh chạy:

```bash
javac SelfInitDemo.java
java SelfInitDemo
```

Stdout:

```text
computeFirst sees second=0
static block sees first=1, second=8
first=1
second=8
```

`Settings.second` chưa được phép gán `8` khi `computeFirst()` chạy, nên ở thời điểm đó vẫn là `0`. Java có quy tắc hạn chế một số tham chiếu trực tiếp đến static field khai báo phía sau trong initializer (**illegal forward reference**), nhưng việc đọc qua một method như ví dụ vẫn biên dịch hợp lệ. Quy tắc compiler này không bảo đảm rằng mọi lần quan sát trường static đều đã hoàn thiện.

Hãy tránh dùng kết quả từ field khai báo phía sau làm điều kiện hợp lệ ngay trong field initializer nằm phía trước nếu logic phụ thuộc giá trị sau khởi tạo.

## 6. Vòng phụ thuộc có thể để lại `null` hoặc object chưa sẵn sàng

Với field kiểu tham chiếu, giá trị mặc định là **`null`**. Điều này nguy hiểm hơn phép cộng với `0`: code có thể ném `NullPointerException`, lưu `null` vào cache hoặc truyền một tham chiếu không hợp lệ ra ngoài.

### 6.1. Ví dụ `static final` không bảo vệ được vòng khởi tạo

Tạo `CircularReferenceDemo.java`:

```java
public class CircularReferenceDemo {
    static final class Holder {
        final String name;

        Holder(String name) {
            this.name = name;
        }
    }

    static final class Left {
        static final Holder HOLDER = Right.HOLDER;

        static {
            System.out.println("Left.HOLDER=" + Left.HOLDER);
        }
    }

    static final class Right {
        static final Holder HOLDER = Left.HOLDER;

        static {
            System.out.println("Right.HOLDER=" + Right.HOLDER);
        }
    }

    public static void main(String[] args) {
        System.out.println("read Left");
        System.out.println("result Left=" + Left.HOLDER);
        System.out.println("result Right=" + Right.HOLDER);
    }
}
```

Chạy:

```bash
javac CircularReferenceDemo.java
java CircularReferenceDemo
```

Stdout:

```text
read Left
Right.HOLDER=null
Left.HOLDER=null
result Left=null
result Right=null
```

Đọc `Left.HOLDER` khiến JVM khởi tạo Left, bắt đầu đánh giá `Right.HOLDER`. Bên trong khởi tạo Right, việc đọc `Left.HOLDER` thuộc **cùng thread đang khởi tạo Left**, và phép gán của Left chưa kết thúc, nên giá trị là `null`. Right giữ `null`, sau đó Left cũng giữ `null`.

Cả hai field đều khai báo `static final`, nhưng `Holder` là kiểu tham chiếu không phải `String` compile-time constant; `final` chỉ ràng buộc việc gán field, **không bảo đảm đồ thị khởi tạo phụ thuộc vòng sẽ tự tạo giá trị hợp lệ**. Trong ví dụ, constructor của `Holder` thậm chí chưa được gọi lần nào. `final` cũng không biến một tham chiếu `null` thành object hợp lệ.

### 6.2. Phân biệt “giá trị chưa hoàn thiện” với “object chưa xây dựng xong”

Có ít nhất ba kiểu vấn đề:

- **Static field còn mặc định:** chưa được gán, thread đệ quy thấy `0`/`false`/`null`.
- **Static field đã được gán tạm nhưng sẽ còn thay đổi:** logic giữa các static blocks có thể đọc được giá trị trung gian.
- **Object bị công bố quá sớm:** static initializer/constructor đưa `this` hoặc object đang xây dựng vào registry/callback; một đoạn mã khác có thể sử dụng object trước khi các invariant của nó được thiết lập. Đây còn liên quan an toàn công bố (safe publication), đặc biệt giữa nhiều thread, và không thể chỉ giải thích bằng mặc định của static field.

Không kết luận rằng mọi field sẽ luôn là giá trị mặc định trong vòng phụ thuộc: **thứ tự gán đã thực hiện** mới quyết định giá trị quan sát. Field đã gán từ trước có thể hoàn toàn hợp lệ; field chưa gán thì có thể chưa hợp lệ.

## 7. Hằng biên dịch khi truy vết vòng initialization

`static final int LIMIT = 10` và `static final String LABEL = "ready"` có thể được compiler inline, nên phép đọc không nhất thiết đi vào vòng initialization. `static final Object TOKEN = new Object()` và `static final int X = parse()` phải được gán lúc runtime, vì vậy có thể bị đọc dở dang nếu initializer tạo vòng. Bài về **hằng biên dịch** định nghĩa đầy đủ constant variable; ở đây hãy vẽ cạnh dependency chỉ cho thao tác thực sự cần initialization.

## 8. Vòng giữa hai thread: vì sao có thể deadlock?

Xét hai lớp mà bộ khởi tạo lần lượt cần class kia:

```text
Thread T1                              Thread T2
---------                              ---------
bắt đầu initialize A                  bắt đầu initialize B
A.<clinit> ...                         B.<clinit> ...
muốn đọc B.value                      muốn đọc A.value
B đang initialize bởi T2             A đang initialize bởi T1
→ T1 phải chờ T2                       → T2 phải chờ T1
              \                        /
               Vòng chờ (deadlock)
```

Đây là một **lịch thực thi có thể xảy ra**, không phải kết cục bắt buộc của mọi chương trình chứa vòng A ↔ B. Nếu chỉ một thread chủ động khởi tạo cả A và B trước khi thread kia vào cuộc, nó có thể gặp hiện tượng giá trị mặc định kiểu mục 4 mà **không deadlock**. Scheduler, thời điểm khởi tạo và việc thư viện vô tình chạm tới class trước đó đều ảnh hưởng hiện tượng quan sát.

Một **deadlock khác** có thể xảy ra ngay cả khi không có static field A ↔ B:

```text
Thread T1 khởi tạo ClassA
  └─ trong ClassA.<clinit>:
       tạo Thread T2
       gọi T2.join() để chờ T2
                         Thread T2:
                           gọi ClassA.someStaticMethod()
                           → cần chờ T1 khởi tạo ClassA xong
T1 đợi T2, T2 đợi T1 ⇒ deadlock
```

**Tránh chờ vô thời hạn bên trong static initializer**, chẳng hạn `Thread.join()` không timeout, chờ `Future.get()` trên task phụ thuộc lớp đang init, hoặc chờ một executor/service được khởi động theo chính đường phụ thuộc đó.

### 8.1. Vì sao deadlock không giống vòng khởi tạo đệ quy cùng thread?

Khi cùng thread đang khởi tạo A mà lại yêu cầu A, JVM có quy tắc **đệ quy cùng thread** để kết thúc yêu cầu lồng nhau; trường có thể còn mặc định. Khi **thread khác** yêu cầu A, JVM phải bảo toàn việc A chỉ được khởi tạo một lần và thread thứ hai phải chờ trạng thái của A. Điều đó bảo vệ tính nhất quán sau initialization nhưng khiến đồ thị chờ vòng có thể bị kẹt.

Cơ chế này thuộc initialization của JVM; `ThreadMXBean.findDeadlockedThreads()` chủ yếu phát hiện vòng chờ trên monitor/ownable synchronizer và **không bảo đảm tìm ra mọi class-initialization deadlock**. Hãy xem toàn bộ thread dump và những class đang khởi tạo thay vì chỉ dựa vào một API deadlock detector trả về `null`.

## 9. Điều gì được bảo đảm giữa các thread?

Java cần có ngữ nghĩa đồng bộ cho initialization: sau khi class đã **khởi tạo thành công**, các thread sử dụng nó qua những hành động yêu cầu init có thể tiếp tục với các hiệu ứng của initialization đã được công bố theo quy tắc happens-before tương ứng. JVM có thể tối ưu cơ chế khóa nhưng phải giữ hệ quả đồng bộ mà JLS yêu cầu.

Điều này **không** đồng nghĩa:

- Một thread khác có thể tùy tiện đọc field thông qua `getstatic` trong khi class đang do thread đầu tiên khởi tạo: JVM phải áp dụng quy tắc chờ init khi thích hợp.
- Việc object được tạo và chia sẻ qua một registry/callback từ giữa `<clinit>` sẽ luôn an toàn: nếu object thoát ra trước khi hoàn thành constructor hoặc không được công bố đúng cách, đó là vấn đề về trạng thái object và Java Memory Model.
- Các giá trị mutable `static` được sửa **sau khi khởi tạo** tự động thread-safe: vẫn cần đồng bộ thích hợp, `volatile`, immutable objects hoặc concurrent collections tùy trường hợp.

**Trạng thái chưa hoàn thiện trong bài này** thường xuất hiện rất rõ khi **chính thread khởi tạo** đọc ngược các trường còn mặc định. Không dùng hiện tượng này để kết luận rằng Java cho phép mọi thread khác thấy trạng thái đang khởi tạo của lớp.

## 10. Đồ thị dependency JVM và dependency do ứng dụng

Initialize `Child` đòi hỏi initialize `Parent` trước, tức có cạnh bắt buộc **Child → Parent**. Nếu initializer của `Parent` tự truy cập static member của `Child`, ứng dụng tạo thêm cạnh **Parent → Child** và có thể thành vòng. Khi debug, ghi riêng cạnh từ thuật toán JVM với cạnh do `getstatic`/`invokestatic` trong code.

## 11. Kết quả cần phòng tránh

Phụ thuộc vòng có thể lưu `0`/`null` vào registry/cache, gây `NullPointerException` hoặc tạo kết quả khác nhau theo lớp được active use đầu tiên. Lỗi chưa xử lý còn có thể làm class erroneous, dẫn đến `ExceptionInInitializerError` ở lượt đầu và `NoClassDefFoundError` ở lượt sau. **Không phải mọi vòng đều ném lỗi**: `CircularInitDemo` chứng minh kết quả số khác nhau dù cả hai lượt initialization hoàn tất bình thường.

Bài cuối chương phân tích xử lý erroneous class và class loader; ở đây ưu tiên tìm và phá vòng dependency trước khi field dở dang bị sử dụng.

## 12. Cách thiết kế để tránh vòng phụ thuộc initialization

### 12.1. Xác định đồ thị phụ thuộc và phá vòng

Vẽ cạnh `A → B` khi **initializer của A** phải chạm vào thành viên không hằng của B hoặc gọi hành động khiến B phải khởi tạo:

```text
Thiết kế nguy hiểm:

A.<clinit> ──needs──> B.value
     ▲                    │
     └────needs───────────┘

Thiết kế tốt hơn:

main / Bootstrap
       ├──create──> ServiceA
       ├──create──> ServiceB
       └──wire────> quan hệ phụ thuộc rõ ràng
```

Một dependency DAG (không có chu trình) dễ kiểm soát hơn các static initializer gọi lẫn nhau. Nếu A và B phải tham chiếu hai chiều, có thể tạo object trước, rồi **wire các tham chiếu trong một bước khởi động có kiểm soát** thay vì để field initializer quyết định thứ tự theo lần chạm class đầu.

### 12.2. Chuyển initialization phức tạp sang điểm khởi động rõ ràng

Ví dụ dưới đây loại bỏ việc A và B tự tìm nhau trong static initializer.

Tạo `ExplicitBootstrapDemo.java`:

```java
public class ExplicitBootstrapDemo {
    static final class ServiceA {
        private ServiceB peer;

        void connect(ServiceB peer) {
            this.peer = peer;
        }

        String readPeer() {
            return peer.name();
        }

        String name() {
            return "A";
        }
    }

    static final class ServiceB {
        private ServiceA peer;

        void connect(ServiceA peer) {
            this.peer = peer;
        }

        String readPeer() {
            return peer.name();
        }

        String name() {
            return "B";
        }
    }

    public static void main(String[] args) {
        ServiceA a = new ServiceA();
        ServiceB b = new ServiceB();
        a.connect(b);
        b.connect(a);

        System.out.println("A sees " + a.readPeer());
        System.out.println("B sees " + b.readPeer());
    }
}
```

Chạy:

```bash
javac ExplicitBootstrapDemo.java
java ExplicitBootstrapDemo
```

Stdout:

```text
A sees B
B sees A
```

Ở đây graph tham chiếu đối tượng vẫn hai chiều, nhưng quá trình **xây dựng và kết nối** diễn ra theo thứ tự rõ ràng. Mã chỉ gọi `readPeer()` sau khi đã kết nối cả hai. Nếu chia sẻ cho các thread khác, tiếp tục cần cơ chế công bố/synchronization phù hợp; ví dụ trên chỉ có một thread.

### 12.3. Lazy initialization cần được dùng đúng

Một initialization-on-demand holder thường có dạng:

```java
public final class ServiceRegistry {
    private ServiceRegistry() {}

    private static final class Holder {
        private static final ServiceRegistry INSTANCE =
                new ServiceRegistry();
    }

    public static ServiceRegistry getInstance() {
        return Holder.INSTANCE;
    }
}
```

Đây là mẫu class **minh họa độc lập**, không có `main`; nếu muốn thử, gọi `ServiceRegistry.getInstance()` từ chương trình khác. Holder tách thời điểm khởi tạo singleton khỏi thời điểm load class ngoài và hưởng bảo đảm initialization an toàn của JVM.

**Nhưng lazy holder không tự giải quyết vòng phụ thuộc:** nếu constructor `ServiceRegistry()` gọi sang một holder khác rồi holder đó lại gọi `ServiceRegistry.getInstance()` trước khi `Holder.INSTANCE` được gán, vẫn có thể quan sát `null` hoặc gặp lỗi/deadlock. Cần bảo đảm graph xây dựng thực sự không có vòng yêu cầu instance chưa hoàn tất.

### 12.4. Những nguyên tắc thực hành

- Giữ initializer `static` đơn giản, hữu hạn, độc lập và ít side effect; ưu tiên các giá trị bất biến khởi tạo từ dữ liệu có sẵn.
- Không gọi qua lại giữa các singleton đang xây dựng; tách **allocate**, **configure**, **publish** nếu các object phụ thuộc hai chiều.
- Tránh tạo thread, đợi network, `join` hay chờ Future trong `<clinit>`; các điểm chờ có thể gọi ngược về class đang init.
- Không công bố `this` từ constructor tới global registry/callback trước khi invariant của object hoàn tất.
- Kiểm thử startup trong **JVM mới** với những đường kích hoạt đầu tiên khác nhau khi nghi ngờ phụ thuộc thứ tự.
- Kiểm tra xem trình biên dịch có inline một hằng không; đừng nhầm việc đọc hằng `static final` với active use tạo initialization.
- Với code được chạy qua ClassLoader tùy chỉnh/plugin, xác định chính xác **defining loader** của mỗi class: cùng binary name do các loaders khác nhau định nghĩa sẽ có các trạng thái khởi tạo độc lập.

## 13. Quy trình debug vòng khởi tạo và deadlock

1. **Tìm lần dùng đầu:** truy cập nào kích hoạt `getstatic`, `putstatic`, `invokestatic`, `new` hoặc reflection có yêu cầu initialization? Chú ý đến class *khai báo* field/method.
2. **Đọc static initializers theo thứ tự văn bản**, gồm biểu thức gán field và khối `static`; hàm helper được gọi từ đó là một phần của đồ thị phụ thuộc.
3. **Ghi đồ thị A → B → A**: cạnh nào do static initializer, cạnh nào do superclass initialization hoặc hoạt động gọi ngược?
4. **Phân biệt lịch sử thread:** nếu chu trình trên cùng thread, kiểm tra field chưa được gán; nếu nhiều thread, tìm thread đang init mỗi class và đang đợi nhau.
5. **Dùng trace có chủ đích:** đặt log trước/sau biểu thức quan trọng, kèm thread name; tránh tạo log có side effect khởi tạo class khác. Log lớp được **load** không chứng minh lớp đã **initialized**.
6. **Xem thread dump khi bị treo:** dùng `jcmd <pid> Thread.print -l` hoặc `jstack <pid>` nếu JDK hỗ trợ; đối chiếu stack đang ở `<clinit>`, constructor, `join`, `Future.get` và các wait. Không kết luận không deadlock chỉ vì detector cho monitor không tìm thấy vòng.
7. **Ghi lại exception đầu tiên:** với `ExceptionInInitializerError`, xem `getCause()`; với `NoClassDefFoundError` sau đó, quay lại lỗi initialization trước thay vì chỉ sửa classpath.
8. **Chạy lại trong process mới:** class erroneous không được khởi tạo lại trong cùng runtime class; trạng thái initialized cũng không tự xóa giữa các unit test chạy cùng JVM.
9. **Sửa dependency**, không vá bằng `Thread.sleep()`, thêm `synchronized` ngẫu nhiên hoặc phụ thuộc thời gian chạy; những thao tác đó thường chỉ thay đổi lịch và che lỗi.

Có thể xem bytecode của một nested class, ví dụ từ `CircularInitDemo`:

```bash
javap -c -p 'CircularInitDemo$A'
```

Trên shell Unix hoặc PowerShell, dấu nháy đơn ngăn ký tự `$` bị shell diễn giải. `javap` thường hiển thị phần `static {};` với các lệnh `invokestatic` và `putstatic` tương ứng `A.compute()` và gán `A.value`. Bytecode giúp tìm thứ tự gán, nhưng để chứng minh thread nào đang init lúc lỗi, vẫn cần runtime trace/thread dump.

## 14. Tình huống cần phân biệt

| Tình huống | Có thể xảy ra | Điều không nên kết luận |
|---|---|---|
| A đang init trên T, gọi A lần nữa cũng trên T | Yêu cầu đệ quy hoàn tất, đọc giá trị đang có | `<clinit>` được chạy từ đầu lần hai |
| A → B → A trên một thread | B đọc mặc định của một field A chưa gán | Mọi vòng đều deadlock |
| T1 init A chờ B; T2 init B chờ A | Cross-thread deadlock | JVM nhất thiết ném `StackOverflowError` |
| Một thread khác cần A khi T đang init A | Thread khác chờ init thành công/thất bại theo quy tắc JVM | Luôn được phép thấy field còn mặc định |
| Đọc `static final int X = 5` | Hằng được inline, có thể không kích hoạt init | Mọi `static final` đều hành xử như nhau |
| Static initializer ném unchecked exception | `ExceptionInInitializerError` lần đầu, class erroneous về sau | JVM sẽ tự thử init lại mỗi lần |
| Holder trì hoãn tạo singleton | Có thể giảm eager init | Chắc chắn giải được chu trình constructor/holder |

## 15. Câu hỏi tự kiểm tra

1. **Nếu T đang khởi tạo A và trong `A.<clinit>` mã lại yêu cầu initialize A thì JVM làm gì?** Yêu cầu lồng nhau của cùng T hoàn tất bình thường; `A.<clinit>` không bị chạy lại từ đầu.
2. **Tại sao trong `CircularInitDemo` chạy A trước cho `B.value=10`, chạy B trước lại cho `A.value=1`?** Vì lớp bị đọc ngược trong khi chưa hoàn tất initializer đang có `value=0`, rồi phép tính diễn ra theo thứ tự khác.
3. **Dùng `static final Holder` có ngăn field nhận `null` trong vòng init không?** Không; `Holder` không phải compile-time constant, `final` không loại bỏ việc một field chưa được gán bị đọc đệ quy.
4. **Hai thread cùng yêu cầu init một class chưa init có cùng chạy `<clinit>` không?** Không; JVM điều phối để class init một lần, thread còn lại phải tuân theo trạng thái/đợi.
5. **A → B → A có luôn deadlock không?** Không; cùng một thread thường thấy trạng thái chưa hoàn thiện, còn lịch cross-thread chờ nhau có thể deadlock.
6. **Nếu initializer thất bại, gọi lại static field sẽ tự retry không?** Không; class runtime chuyển sang trạng thái erroneous và lần sử dụng tiếp thất bại.
7. **Holder singleton có chắc tránh vòng không?** Không, nếu constructor/initializer của holder tạo vòng gọi ngược đến instance chưa gán.
8. **Phương án ổn định nhất khi hai service cần tham chiếu hai chiều là gì?** Tách construction/wiring khỏi static initializer; tạo object và kết nối phụ thuộc ở startup rõ ràng, rồi mới publish/sử dụng.

## Tài liệu chuẩn

- [Java Language Specification — 12.4.1, When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.1): tác nhân yêu cầu khởi tạo và trường hợp dùng constant variable.
- [Java Language Specification — 12.4.2, Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.2): trạng thái lớp, thread đang khởi tạo, trường hợp yêu cầu đệ quy và khi khởi tạo thất bại.
- [Java Language Specification — 4.12.4, final Variables](https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.4): constant variables và phân biệt với `final` thông thường.
- [Java Virtual Machine Specification — 5.5, Initialization](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.5): cơ chế initialization ở mức JVM.
- [Java Language Specification — 17, Threads and Locks](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html): happens-before và đồng bộ giữa thread.

**Điểm phải nắm:** Đệ quy khởi tạo cùng thread có thể đọc **giá trị chưa gán**; vòng khởi tạo giữa nhiều thread có thể tạo **vòng chờ**. Cả hai là dấu hiệu cần thiết kế lại dependency startup để trạng thái quan trọng chỉ được sử dụng khi đã hoàn chỉnh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-initialization-010">ExceptionInInitializerError và trạng thái khởi tạo thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# ExceptionInInitializerError và trạng thái khởi tạo thất bại trong Java

Khi Java Virtual Machine (JVM) **khởi tạo một lớp hoặc interface**, nó thực hiện các bộ khởi tạo `static` và khởi tạo các trường `static` theo quy tắc của Java. Nếu quá trình này kết thúc bất thường, lớp có thể chuyển sang trạng thái **khởi tạo thất bại (erroneous)**. Lần sử dụng đầu tiên có thể thấy `ExceptionInInitializerError`; những lần chủ động khởi tạo lại chính lớp đó thường nhận `NoClassDefFoundError`.

Đây là tình huống quan trọng khi dùng **class loader**, framework, thư viện cấu hình, singleton `static` hoặc code sinh lúc chạy. Nó giải thích vì sao sau khi sửa biến cấu hình trong bộ nhớ, lời gọi thứ hai vẫn báo lỗi dù đoạn khởi tạo không chạy lần nữa.

## 1. Khởi tạo lớp thực hiện công việc gì?

Quá trình chuẩn của một lớp thông thường:

```text
Loading               Tìm/đọc class file; tạo biểu diễn runtime của lớp
   ↓
Linking
  ├─ Verification      Xác minh class file/bytecode
  ├─ Preparation       Cấp phát và đặt giá trị mặc định cho trường static
  └─ Resolution        Giải quyết symbolic references (có thể diễn ra muộn)
   ↓
Initialization         Chạy logic khởi tạo lớp, nếu có, qua <clinit>
   ↓
Sử dụng bình thường   new, gọi phương thức, truy cập trường...
```

Phần **initialization** không đồng nghĩa với **loading** hoặc **preparation**. Trong preparation, các trường `static` đã có giá trị mặc định (`0`, `false`, `null`, ...); trong initialization, các giá trị theo biểu thức khởi tạo và các khối `static { ... }` được thực thi theo thứ tự xuất hiện trong nguồn (với các quy tắc biên dịch/JVM tương ứng).

Ví dụ:

```java
class Settings {
    static int retries = loadRetries();

    static {
        System.out.println("Settings initialized");
    }

    static int loadRetries() {
        return 3;
    }
}
```

Lớp `Settings` được tạo và liên kết trước khi code khởi tạo `static` bắt đầu. Khi khởi tạo, nó gọi `loadRetries()` để thiết lập `retries`, rồi in thông báo từ khối `static`. Nếu `loadRetries()` ném một exception không được xử lý, khối `static` phía sau **không được chạy**.

**Điểm học tập:** Việc tham chiếu đến `Settings.class` hay sử dụng `Class.forName("Settings", false, loader)` không tự nó buộc `Settings` thực hiện khối khởi tạo. Ngược lại, `Class.forName("Settings")` thông thường yêu cầu khởi tạo, nên có thể làm lộ lỗi này.

## 2. Khi nào JVM bắt đầu initialization?

Theo ngữ nghĩa Java, các ví dụ sử dụng chủ động điển hình là:

- `new MyClass()`: tạo instance của lớp, nên lớp cần được khởi tạo trước khi tạo instance hoàn tất.
- `MyClass.staticMethod()`: gọi phương thức `static` được khai báo bởi lớp.
- `MyClass.staticField = value`: gán trường `static` không phải hằng số inline ở lớp khai báo.
- `int x = MyClass.nonConstantStaticField`: đọc trường `static` không phải *constant variable*.
- `Class.forName("pkg.MyClass")`: phương thức này mặc định yêu cầu khởi tạo lớp được tìm thấy.

Đọc *compile-time constant* khác với đọc biến `static` thông thường:

```java
class Example {
    static final int LIMIT = 10;              // constant variable
    static final int DYNAMIC = computeLimit(); // không phải constant variable

    static int computeLimit() {
        System.out.println("initializing");
        return 20;
    }
}
```

Mã `System.out.println(Example.LIMIT)` có thể được biên dịch thành sử dụng trực tiếp giá trị `10`, **không kích hoạt khởi tạo** `Example`. Trong khi đó, đọc `Example.DYNAMIC` là một hành vi có thể kích hoạt initialization. Do vậy, “đọc một trường static” chưa đủ để kết luận khối `static` sẽ chạy.

Khi khởi tạo một lớp, JVM còn phải khởi tạo superclass chưa khởi tạo và những superinterface có default methods theo quy tắc hiện hành. Nếu một superclass thất bại, lớp con đang được khởi tạo cũng có thể bị đánh dấu erroneous mà không chạy bộ khởi tạo riêng của nó. Khởi tạo một interface thì không mặc nhiên khởi tạo tất cả superinterface của nó.

## 3. Phân biệt exception gốc, ExceptionInInitializerError và NoClassDefFoundError

Giả sử một lớp có:

```java
static final String CONFIG = loadRequiredConfig();
```

Nếu `loadRequiredConfig()` ném `IllegalStateException` hoặc `NullPointerException` và không có đoạn xử lý phù hợp, JVM đánh dấu lớp khởi tạo lỗi. Điều gì được ném ra cho người gọi phụ thuộc loại `Throwable` làm kết thúc bộ khởi tạo:

| Lần thử và nguyên nhân | Throwable nhìn thấy thường gặp | Ý nghĩa |
|---|---|---|
| Lần đầu: `<clinit>` kết thúc bởi `RuntimeException`, ví dụ `IllegalStateException` | `ExceptionInInitializerError` chứa exception gốc | JVM bọc lỗi không thuộc nhánh `Error` khi khởi tạo |
| Lần đầu: `<clinit>` trực tiếp kết thúc bởi `Error`, ví dụ `AssertionError` | Chính `Error` đó | JVM không tự bọc `Error` thành một `ExceptionInInitializerError` mới |
| Lần kế tiếp: cùng lớp đã ở trạng thái erroneous | `NoClassDefFoundError` | JVM từ chối cố khởi tạo lại lớp đã thất bại |
| Bất kỳ lúc nào: class file không thể tải hoặc dependency thiếu | Có thể là `ClassNotFoundException` hoặc `NoClassDefFoundError`, tùy API/ngữ cảnh | Cần phân biệt với erroneous state; không phải mọi `NoClassDefFoundError` đến từ `<clinit>` |

`ExceptionInInitializerError` kế thừa `LinkageError`, còn `LinkageError` kế thừa `Error`. `NoClassDefFoundError` cũng kế thừa `LinkageError`, nhưng **không** phải subclass của `ExceptionInInitializerError`.

Đừng mặc định rằng lần đầu **luôn** xuất hiện `ExceptionInInitializerError`: nếu code khởi tạo ném `Error`, JVM có thể chuyển tiếp chính lỗi đó. Ngoài ra, khi việc tạo đối tượng `ExceptionInInitializerError` thất bại vì thiếu bộ nhớ, JVM có thể dùng `OutOfMemoryError` theo đặc tả.

### Exception gốc nằm ở đâu?

Trong lần đầu thất bại do exception không phải `Error`, xem chuỗi nguyên nhân:

```text
java.lang.ExceptionInInitializerError
Caused by: java.lang.IllegalStateException: missing configuration
    at ... loadRequiredConfig(...)
    at ... <clinit>(...)
```

Đoạn trên là **mẫu stack trace minh họa**, không phải stdout cố định của chương trình. `Throwable.getCause()` trả về cause khi có. `ExceptionInInitializerError.getException()` là API cũ cũng có thể trả về exception ban đầu. Phân tích nguyên nhân phải bắt đầu từ lỗi trong `<clinit>`, không chỉ từ kiểu lỗi bọc phía ngoài.

Lần kế tiếp có thể thấy:

```text
java.lang.NoClassDefFoundError: Could not initialize class pkg.Settings
```

Thông báo và chuỗi cause có thể khác theo JDK/JVM. Đôi khi `NoClassDefFoundError` về sau có một cause liên quan đến lần thất bại ban đầu; **không dựa vào điều đó như điều kiện bắt buộc**. Luôn tìm lần initialization thất bại đầu tiên trong log.

## 4. Trạng thái khởi tạo của lớp là một state machine

JVMS `5.5` mô tả bốn trạng thái có ý nghĩa logic đối với class/interface đã được verify và prepare:

```text
VERIFIED + PREPARED, NOT INITIALIZED
                    |
                    | yêu cầu khởi tạo đầu tiên
                    v
BEING INITIALIZED BY THREAD T
         |                    |
         | thành công         | kết thúc bất thường
         v                    v
FULLY INITIALIZED         ERRONEOUS
         |                    |
         | dùng tiếp          | yêu cầu khởi tạo tiếp
         v                    v
     sử dụng được         NoClassDefFoundError
```

- **Not initialized:** chưa bắt đầu chạy logic khởi tạo.
- **Being initialized:** một thread đang làm việc đó. Nếu thread khác cùng cần khởi tạo lớp, nó phải chờ kết quả; sau khi được đánh thức, nó kiểm tra lại trạng thái.
- **Fully initialized:** khởi tạo thành công, các lần chủ động sử dụng tiếp không chạy lại `<clinit>`.
- **Erroneous:** quá trình khởi tạo đã thất bại. Yêu cầu khởi tạo tiếp theo với cùng runtime class ném `NoClassDefFoundError`, thay vì tự chạy lại initializer.

JVM có khóa khởi tạo riêng (`L_C`) cho từng class/interface về mặt ngữ nghĩa. Cách JVM biểu diễn khóa và tối ưu hóa việc lock là chi tiết triển khai, nhưng quy tắc đồng bộ và thứ tự quan sát phải được bảo toàn.

Nếu cùng **thread đang khởi tạo** lại yêu cầu khởi tạo chính lớp đó theo cách đệ quy, thuật toán của JVM cho phép yêu cầu lặp hoàn tất bình thường, không chạy một vòng `<clinit>` thứ hai. Tình huống này có thể làm code trong đường đệ quy quan sát một vài giá trị `static` đang ở mức mặc định/chưa hoàn tất; đó là một lý do tránh thiết kế static initialization quá phức tạp.

### Vì sao không tự thử lại?

Khởi tạo có thể đã tạo side effects trước khi gặp lỗi: in log, mở kết nối, chỉnh registry, đặt một số static fields, khởi chạy thread, v.v. JVM không có cơ chế “rollback rồi thử lại” chung cho các tác vụ đó. Đánh dấu erroneous và từ chối những lần khởi tạo lại tránh việc chạy lại một chuỗi tác vụ có trạng thái không chắc chắn. Trong một JVM đang chạy, trạng thái erroneous tồn tại đối với **chính runtime class đó**; không thể reset bằng cách gán lại một biến static thông thường.

## 5. Ví dụ Java 17+ có thể biên dịch và chạy

Ví dụ sau chứng minh ba điểm: `Class.forName(..., false, loader)` chưa chạy `<clinit>`; exception thường trong initializer dẫn tới `ExceptionInInitializerError`; khi class đã erroneous, truy cập sau nhận `NoClassDefFoundError`. Nó còn so sánh với việc initializer ném `AssertionError` trực tiếp.

Tạo tệp **`InitializerFailureDemo.java`**:

```java
public class InitializerFailureDemo {
    static class BadRuntime {
        static final int VALUE = initialize();

        private static int initialize() {
            System.out.println("BadRuntime: initializing");
            throw new IllegalStateException("missing configuration");
        }
    }

    static class BadError {
        static final int VALUE = initialize();

        private static int initialize() {
            System.out.println("BadError: initializing");
            throw new AssertionError("corrupt state");
        }
    }

    static void probe(String label, Runnable access) {
        try {
            access.run();
        } catch (LinkageError error) {
            System.out.println(label + ": "
                    + error.getClass().getSimpleName());

            if (error instanceof ExceptionInInitializerError wrapped) {
                System.out.println("cause: "
                        + wrapped.getCause().getClass().getSimpleName());
            }
        } catch (AssertionError error) {
            System.out.println(label + ": "
                    + error.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws Exception {
        Class.forName(
                "InitializerFailureDemo$BadRuntime",
                false,
                InitializerFailureDemo.class.getClassLoader()
        );
        System.out.println("load without initialization");

        probe("first Runtime", () -> System.out.println(BadRuntime.VALUE));
        probe("second Runtime", () -> System.out.println(BadRuntime.VALUE));

        probe("first Error", () -> System.out.println(BadError.VALUE));
        probe("second Error", () -> System.out.println(BadError.VALUE));
    }
}
```

Biên dịch/chạy từ thư mục chứa tệp:

```bash
javac --release 17 -d out InitializerFailureDemo.java
java -cp out InitializerFailureDemo
```

Đầu ra dự kiến với JDK 17+ tuân theo quy tắc trên:

```text
load without initialization
BadRuntime: initializing
first Runtime: ExceptionInInitializerError
cause: IllegalStateException
second Runtime: NoClassDefFoundError
BadError: initializing
first Error: AssertionError
second Error: NoClassDefFoundError
```

Giải thích theo thứ tự:

1. `Class.forName(..., false, ...)` tìm/chuẩn bị lớp `BadRuntime` nhưng không yêu cầu chạy initializer. Vì vậy câu đầu tiên in ra từ `main`.
2. `BadRuntime.VALUE` không phải *compile-time constant*, vì giá trị được trả từ một method. Lần đọc đầu kích hoạt `BadRuntime` initialization.
3. `initialize()` in một dòng rồi ném `IllegalStateException`. JVM bọc throwable này thành `ExceptionInInitializerError` và đánh dấu `BadRuntime` erroneous.
4. Lần đọc `BadRuntime.VALUE` kế tiếp không gọi `initialize()` lần nữa; JVM ném `NoClassDefFoundError`.
5. `BadError.initialize()` ném `AssertionError` (một subclass của `Error`), vì vậy lần đầu nhận chính `AssertionError`. Sau đó `BadError` cũng erroneous và lần tiếp theo nhận `NoClassDefFoundError`.

Hai khối `catch` trong `probe` **chỉ nhằm quan sát lỗi trong ví dụ**. Trong ứng dụng thật, đừng bắt chung lỗi khởi tạo rồi bỏ qua như thể component đã sẵn sàng. Nếu không có phương án fallback hợp lệ, để lỗi lan lên và dừng startup thường an toàn hơn việc tiếp tục với trạng thái thiếu nhất quán.

**Chi tiết dễ bỏ sót:** `static final int VALUE = initialize()` không phải constant variable. Nếu viết `static final int VALUE = 42` thì compiler có thể inline `42`, khiến ví dụ không kích hoạt initialization và dẫn tới kết luận sai.

## 6. Tại sao đôi khi chỉ thấy NoClassDefFoundError?

Những trường hợp thường gặp trong log:

**Tình huống A: class thật sự thiếu.**

```text
java.lang.NoClassDefFoundError: com/example/OptionalDependency
Caused by: java.lang.ClassNotFoundException: com.example.OptionalDependency
```

Một tham chiếu đến lớp phụ thuộc không được loader tìm thấy. Điều này khác với class có mặt nhưng đã từng thất bại trong `<clinit>`.

**Tình huống B: class có mặt nhưng initialization đã thất bại.**

```text
java.lang.ExceptionInInitializerError
Caused by: java.lang.IllegalArgumentException: bad setup
...
java.lang.NoClassDefFoundError: Could not initialize class com.example.ConfigHolder
```

Đoạn trên ghép các **mẫu log minh họa** từ những lượt gọi khác nhau, không phải một stack trace duy nhất. Nếu hệ thống chỉ lưu lượt gọi sau, người vận hành có thể thấy `NoClassDefFoundError` mà không còn log về exception ban đầu.

**Cách xử lý:** đối chiếu mốc thời gian từ khi JVM/class loader khởi động, tìm **lỗi đầu tiên** liên quan đến class name; kiểm tra exception cause, dòng trong `<clinit>` và trạng thái các dependency. Nếu hệ thống đa luồng, thread thứ hai có thể chờ thread thứ nhất khởi tạo rồi chỉ nhận thông tin erroneous.

Một `NoClassDefFoundError` tự nó **không đủ** để kết luận đường dẫn classpath sai hay initializer thất bại. Cần phân biệt theo thông điệp, cause và log đầy đủ.

## 7. Phạm vi “thất bại một lần” gắn với ClassLoader

JVM nhận diện runtime class bằng cặp:

```text
(binary name, defining class loader)
```

Vì vậy, hai lớp cùng tên nhị phân nhưng được **hai defining class loaders khác nhau** định nghĩa là hai runtime classes khác nhau, có initialization state riêng. Nếu loader A đã định nghĩa `pkg.Settings` và lớp này erroneous, **không có API Java thông thường để gọi lại `<clinit>` trên cùng lớp đó**.

Giải pháp thực tế tùy môi trường:

- **Ứng dụng Java thông thường:** sửa lỗi cấu hình, tài nguyên hoặc bytecode rồi khởi động lại tiến trình JVM để lớp được định nghĩa và khởi tạo trong runtime mới.
- **Plugin/container hỗ trợ reload:** tạo một defining class loader mới, nạp phiên bản lớp và dependency tương thích trong namespace mới, sau đó thay phần hệ thống sử dụng nó. Phải quản lý tham chiếu cũ, thread, tài nguyên và vòng đời loader cẩn thận.
- **Unit test:** tách logic tải cấu hình khỏi initializer `static` nếu cần dễ kiểm thử và thử lại; một test khác trong cùng JVM không mặc nhiên “làm mới” static state của lớp đã erroneous.

Chỉ tạo `new ClassLoader(parent)` **không đủ** nếu loader đó tiếp tục parent-first delegation để trả về chính lớp cũ từ parent. Điều quyết định là **defining loader của lớp được dùng thực tế**; load lại tên qua một child loader mà vẫn delegate lên lớp lỗi không khôi phục được trạng thái.

Cũng không nên dựa vào reflection để viết trường static hoặc cố gọi thủ công `<clinit>` nhằm “reset” một lớp: JVM quản lý trạng thái này theo quy tắc riêng.

## 8. Thực hành thiết kế tránh lỗi khởi tạo

Một initializer ngắn, ít side effects thường dễ duy trì hơn initializer mở kết nối mạng, tải file phức tạp hoặc tra cứu trạng thái phụ thuộc môi trường.

### 8.1. Tránh I/O và cấu hình có thể thất bại trong static initializer

Thiết kế dễ phát sinh lỗi:

```java
final class RemoteConfig {
    static final String TOKEN = readFromRemoteService();
    // Khi readFromRemoteService() thất bại, RemoteConfig có thể erroneous.
}
```

Đây chỉ là **đoạn minh họa**, chưa có định nghĩa `readFromRemoteService()`. Vấn đề không nằm ở `static final` tự thân, mà ở việc tải dữ liệu có thể thất bại đã được đặt vào initialization không có cơ chế retry cho cùng class.

Một cách thiết kế khác cho service cần retry:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigService {
    public String readToken(Path path) throws IOException {
        return Files.readString(path).trim();
    }
}
```

Tạo tệp `ConfigService.java`, biên dịch `javac ConfigService.java`. Đây là class biên dịch được nhưng **không có `main`**, nên chỉ là phần mô phỏng component. Người gọi có thể quyết định retry, fallback hoặc dừng startup khi `readToken` báo `IOException`; thất bại của lời gọi method **không tự làm lớp `ConfigService` erroneous**.

Lưu ý: ví dụ trên chỉ minh họa việc **dời phép đọc có thể lỗi ra khỏi khởi tạo lớp**. Trong hệ thống thật vẫn cần xác thực cấu hình, bảo mật token và xử lý tài nguyên phù hợp.

### 8.2. Nếu buộc cần dữ liệu lúc initialization

Dữ liệu bất biến, tự đủ và tính toán xác định thường thích hợp hơn. Khi bắt buộc kiểm tra cấu hình trong initializer:

- Đưa thông điệp lỗi rõ ràng vào exception gốc (`IllegalStateException("missing X")`) để log dễ tìm nguyên nhân.
- Không bắt `Throwable` rồi âm thầm trả về giá trị giả, vì có thể khiến class nhìn như đã khởi tạo thành công trong trạng thái sai.
- Không chạy tác vụ có thể gây side effects lớn mà không dự tính hành vi thất bại.
- Xác định failure là **fail-fast** hay có thể **retry**; nếu cần retry, nên đặt thao tác vào API/service với vòng đời riêng, thay vì dựa vào việc JVM chạy lại `<clinit>`.

### 8.3. Không lạm dụng catch đối với Error

Có thể xem root cause với `catch (ExceptionInInitializerError error)` tại điểm startup/logging thích hợp, sau đó phát tán lại hoặc chấm dứt thao tác. Tuy nhiên, `ExceptionInInitializerError` thuộc nhóm `Error` và không phải tình huống exception nghiệp vụ bình thường.

```java
try {
    Component.start();
} catch (ExceptionInInitializerError error) {
    log.error("Cannot initialize component", error);
    throw error;
}
```

Đây là **đoạn minh họa** cần có `Component` và `log` do ứng dụng cung cấp. Nó không phải chương trình Java độc lập, và không thay thế chính sách lifecycle/recovery của ứng dụng.

## 9. Đồng thời và tác động sang các lớp khác

**Nhiều thread:** Nếu thread A bắt đầu khởi tạo `Config`, thread B cần khởi tạo `Config` sẽ chờ kết quả. Nếu A thất bại và `Config` bị đánh dấu erroneous, B sau khi tiếp tục sẽ nhận `NoClassDefFoundError`. B không trở thành “thread thứ hai thử chạy lại static block”.

**Phụ thuộc superclass:** Khi lớp con cần khởi tạo và initializer của superclass thất bại, quá trình khởi tạo lớp con cũng thất bại, lớp con có thể erroneous. Lần đầu có thể thấy throwable bắt nguồn từ superclass; các lần sau dùng lớp con có thể nhận `NoClassDefFoundError` cho lớp con. Phân tích stack trace phải đi về lớp khởi tạo gốc.

**Side effects không tự quay lui:** Giả sử initializer A đã ghi một dòng log, thêm item vào registry hoặc thay đổi tệp, rồi bước kế tiếp thất bại. JVM đánh dấu A erroneous nhưng không tự xóa tác dụng bên ngoài của các bước đã chạy. Đây là một lý do ưu tiên initialization nhỏ và thuần túy.

**Vòng phụ thuộc static initialization:** Nếu A đang khởi tạo mà gọi B, B lại đọc A, cùng thread có thể quay lại A khi A chưa hoàn tất. Khi đó các trường của A chưa được gán có thể vẫn là giá trị mặc định, dẫn tới lỗi logic hoặc ngoại lệ khác. Không phải mọi vòng phụ thuộc đều bị verifier cấm; thiết kế thứ tự khởi tạo cần tránh trạng thái trung gian.

## 10. Checklist chẩn đoán ExceptionInInitializerError

1. **Tìm lần ném đầu tiên**, thường là `ExceptionInInitializerError`, `AssertionError` hoặc một `Error` khác phát sinh trong initialization; đừng chỉ nhìn `NoClassDefFoundError` ở những request sau.
2. **Tìm cause gốc** và vị trí `<clinit>` / phương thức được static initializer gọi. Ví dụ `IllegalStateException` do thiếu cấu hình, `NullPointerException` do phụ thuộc chưa sẵn sàng, `IOException` được bọc thành runtime exception.
3. **Xác nhận trigger thực sự**, ví dụ `new`, `invokestatic`, đọc non-constant static field hoặc `Class.forName(name)`; đọc constant có thể không khởi tạo lớp.
4. **Kiểm tra class loader và class identity**, đặc biệt với ứng dụng plugin, app server, test harness, agent hoặc nhiều phiên bản thư viện.
5. **Kiểm tra lớp cha/interface có default method** khi throwable của một lớp xuất hiện trong lúc lớp khác đang khởi tạo.
6. **Kiểm tra đồng thời**, thread nào chạy `<clinit>` trước, thread nào chỉ chờ rồi gặp erroneous state.
7. **Sửa nguyên nhân, khởi động lại hoặc tạo runtime class mới có kiểm soát**. Đừng mong một lần truy cập lại cùng class đã erroneous sẽ sửa trạng thái.
8. **Đưa retry vào tầng ứng dụng** nếu đó là yêu cầu nghiệp vụ. Đừng dùng static initialization làm vòng retry tự động.

## 11. Câu hỏi tự kiểm tra

**1. Tại sao lần đầu lỗi khác lần sau?** Lần đầu JVM đang thực hiện initializer, có thể phải bọc exception gốc vào `ExceptionInInitializerError`. Sau đó runtime class đã erroneous; yêu cầu khởi tạo tiếp theo bị từ chối bằng `NoClassDefFoundError`.

**2. `ExceptionInInitializerError` có phải một `Exception` thông thường?** Không. Nó là `Error` thông qua `LinkageError` và thường biểu thị lỗi nghiêm trọng khi initialization.

**3. Nếu `static` initializer ném `AssertionError`, JVM có luôn bọc nó bằng `ExceptionInInitializerError` không?** Không. `AssertionError` là `Error`, nên lần thất bại đầu có thể trực tiếp ném `AssertionError`.

**4. Gọi lại `new Config()` có chạy lại initializer đã thất bại không?** Không, nếu vẫn là cùng runtime class erroneous. JVM từ chối, thường bằng `NoClassDefFoundError`.

**5. Đổi giá trị biến môi trường trong JVM rồi gọi lại có đủ không?** Không. Phải sửa nguyên nhân và sử dụng vòng đời/runtime class mới, hoặc thiết kế lại để phần có thể thất bại nằm ngoài `<clinit>` và hỗ trợ retry.

**6. Có thể dùng `Class.forName(name, false, loader)` để tránh initialization ngay tại bước tra cứu không?** Có. Nhưng khi thực sự sử dụng lớp theo cách đòi initialization, lỗi vẫn có thể xuất hiện.

**7. Một `NoClassDefFoundError` có chắc nghĩa là class không nằm trong JAR?** Không. Nó cũng có thể xuất hiện vì một lớp đã bị đánh dấu erroneous sau lần initialization trước.

## 12. Tài liệu chuẩn để đối chiếu

- *Java Language Specification*, 12.4, “Initialization of Classes and Interfaces”: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4
- *Java Virtual Machine Specification*, 5.5, “Initialization”: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.5
- Java API, ExceptionInInitializerError: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html
- Java API, NoClassDefFoundError: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html

**Ghi nhớ:** `ExceptionInInitializerError` chỉ là một cách JVM báo rằng initializer thất bại lần đầu; nguyên nhân gốc nằm sâu hơn trong `<clinit>`. Khi class chuyển sang trạng thái **erroneous**, những lần cần khởi tạo lại *cùng runtime class* sẽ bị từ chối bằng `NoClassDefFoundError`. Đọc log theo thứ tự thời gian, hiểu danh tính class loader và đưa logic cần retry ra khỏi `static` initialization.


</details>

- [Quay lại đầu trang](#back-to-top)
