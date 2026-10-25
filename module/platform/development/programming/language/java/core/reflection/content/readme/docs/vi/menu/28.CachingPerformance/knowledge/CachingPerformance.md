<a id="back-to-top"></a>

# Cache và hiệu năng

## Menu
- [Chi phí tra cứu metadata khác chi phí invoke](#caching-performance-001)
- [Cache Field, Method và Constructor theo ngữ cảnh phù hợp](#caching-performance-002)
- [Khóa cache cần xét Class và định danh loader](#caching-performance-003)
- [ClassValue cho dữ liệu liên kết với từng Class](#caching-performance-004)
- [Cache giữ Class có thể giữ cả class loader](#caching-performance-005)
- [Tránh cache toàn JVM không có chính sách vòng đời](#caching-performance-006)
- [An toàn nhiều luồng khi công bố hoặc sử dụng metadata đã cache](#caching-performance-007)
- [Không chia sẻ khả năng truy cập private ngoài phạm vi cần thiết](#caching-performance-008)
- [Benchmark với warmup, lời gọi trực tiếp và workload thực tế](#caching-performance-009)
- [Chi tiết triển khai reflection có thể thay đổi giữa các phiên bản JDK](#caching-performance-010)

## <a id="caching-performance-001">Chi phí tra cứu metadata khác chi phí invoke</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí tra cứu metadata khác chi phí invoke trong Java Reflection

## Mục tiêu học tập

Khi ứng dụng dùng reflection, có ít nhất hai công việc rất khác nhau:

1. **Tìm và diễn giải metadata**: xác định một `Class`, tìm `Method`/`Field`/`Constructor` hoặc đọc chữ ký generic, annotation, modifier.
2. **Thực thi phương thức**: đã có `Method` rồi, gọi `method.invoke(receiver, arguments...)` để chạy logic trên một đối tượng hoặc gọi phương thức static.

Hai công việc có tính chất chi phí khác nhau. Một đoạn code chậm do **tìm lại method trên mỗi request** không có nghĩa bản thân `Method.invoke` phải chịu toàn bộ chi phí đó. Ngược lại, cache `Method` chỉ loại bỏ việc tra cứu lặp; nó **không biến lời gọi reflection thành lời gọi Java trực tiếp**.

Sau bài này, bạn sẽ phân tách được các bước khi đo hiệu năng, hiểu lúc nào cache metadata giúp ích, phân biệt chi phí khởi tạo với chi phí lặp lại và thiết kế benchmark đủ đáng tin.

## 1. Hai công đoạn và phạm vi công việc

Ví dụ có một class:

~~~java
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
~~~

Đây là **đoạn minh họa khai báo class**, không phải chương trình độc lập chứa `main`.

Cùng một phương thức có thể được tìm rồi gọi như sau:

~~~java
Method method = Calculator.class.getMethod(
        "add", int.class, int.class);    // Bước A: tìm metadata
Object result = method.invoke(
        new Calculator(), 2, 5);        // Bước B: thực thi
~~~

Đây cũng là **đoạn trích**, cần `import java.lang.reflect.Method` và khai báo/ xử lý checked exceptions. Giá trị `result` là `Integer` tương ứng số `7`, vì giá trị primitive trả qua `Method.invoke` được boxing.

| Công đoạn | Ví dụ API | Loại công việc | Kết quả |
| --- | --- | --- | --- |
| Lấy handle class | `Calculator.class`, `obj.getClass()` | Có/nhận diện đối tượng `Class` của kiểu | `Class<?>` |
| Tìm thành viên | `getMethod`, `getDeclaredMethod`, `getDeclaredFields` | Tra cứu tên/chữ ký/phạm vi khai báo, có thể tạo wrapper và mảng kết quả | `Method`, `Field`, `Constructor`, mảng |
| Đọc metadata chuyên sâu | `getGenericReturnType`, `getAnnotations` | Có thể phải phân tích `Signature`/annotation và phân giải kiểu | `Type`, annotation, metadata |
| Chuẩn bị sử dụng thành viên | `canAccess`, `trySetAccessible`, tạo `MethodHandle` | Kiểm tra/thiết lập cách truy cập, ràng buộc vào handle | Khả năng truy cập hoặc handle |
| Thực thi | `Method.invoke(target, args)` | Kiểm tra receiver/đối số/quyền phù hợp, chuyển đổi, dispatch và chạy thân method | Kết quả `Object` hoặc ngoại lệ |

**Không đánh đồng** việc lấy `Class` từ `Calculator.class` với việc phải tìm lại `Method` bằng tên. `Class.forName(...)` còn liên quan tải/khởi tạo class tùy cách gọi nên chi phí cold-start của nó cũng không đại diện cho lookup thông thường.

### 1.1. Tra cứu metadata tốn gì?

Với `clazz.getMethod("add", int.class, int.class)`, runtime phải tìm thành viên `public` phù hợp tên và danh sách tham số, có xét các thành viên được thừa hưởng theo quy tắc API. `getDeclaredMethod` chỉ tra phương thức **khai báo trực tiếp** trên class đang xét và có thể tìm cả non-public member. Đổi từ phương thức này sang phương thức kia chỉ để chạy nhanh hơn có thể làm **sai tập kết quả**, nên chọn API trước theo ngữ nghĩa.

Các yếu tố ảnh hưởng đến chi phí lookup:

- Số lượng và hình thái thành viên, overload và cây kế thừa/interface cần xử lý.
- Việc dùng `getMethods()` / `getDeclaredMethods()` để liệt kê mảng toàn bộ rồi lọc, so với tra một phương thức có tên và parameter types rõ ràng.
- Allocation cho wrapper `Method` và các mảng metadata; JVM/JDK có thể cache nội bộ nhưng **không có hợp đồng hiệu năng cố định** để dựa vào.
- Phân tích annotation và generic metadata có thể phát sinh thêm công việc riêng, gồm phân giải type; không nên gộp chi phí này vào một chỉ số tên chung là “reflection lookup”.
- Lookup thực hiện một lần lúc ứng dụng khởi động khác lookup lặp hàng nghìn lần trong vòng xử lý request.

Khi có metadata rồi, các lệnh như `method.getName()` và `method.getReturnType()` đọc dữ liệu từ `Method`; chúng không đồng nghĩa với việc phải gọi lại `Class.getMethod(...)`. Tuy vậy, một số API metadata có thể cấp phát đối tượng/mảng hoặc phân giải thông tin bổ sung; không khẳng định mọi getter đều miễn phí.

### 1.2. Một lần `Method.invoke` tốn gì?

Khi đã có `Method`, `invoke` **không tự chạy lại câu lệnh `getMethod` mà ứng dụng đã dùng**. Nó phải thực hiện ngữ nghĩa của lời gọi reflection, trong đó có:

- Xác nhận receiver hợp lệ cho instance method và kiểm tra điều kiện truy cập theo quy tắc reflection/JPMS (cách tối ưu phụ thuộc JDK và trạng thái member).
- Kiểm tra số lượng, kiểu đối số, boxing/unboxing hoặc widening primitive hợp lệ; varargs không tự tiện được trải phẳng từ mảng bất kỳ.
- Đi tới phương thức thực cần gọi, gồm **dynamic dispatch** với instance method override; static method dùng phương thức được chỉ định.
- Thực thi **thân phương thức đích**. Công việc SQL, I/O, mạng hoặc xử lý dữ liệu trong thân method có thể áp đảo overhead của reflection.
- Boxing giá trị trả về primitive hoặc trả `null` cho `void`; lỗi phát sinh từ thân method được bọc trong `InvocationTargetException`.

Chi phí invoke thay đổi theo method, kiểu tham số, JIT warmup, khả năng tối ưu và phiên bản JVM. Không có quy luật như “invoke luôn chậm hơn lookup” hoặc “reflection luôn chậm X lần so với direct call” dùng được cho mọi Java/JVM.

## 2. Mô hình chi phí: tìm lại mỗi lần hay cache Method?

Gọi:

- `L`: chi phí **một lần** tìm `Method` theo tên/chữ ký.
- `I`: chi phí **một lời gọi reflection**, gồm overhead invoke và công việc của target.
- `n`: số lần cần gọi cùng một phương thức.

Một mô hình đơn giản, chưa tính tối ưu JVM/caching/allocation và thời gian tạo cache:

| Cách viết | Tổng chi phí gần đúng |
| --- | --- |
| Mỗi lần tìm rồi gọi | `n × (L + I)` |
| Tìm một lần rồi tái dùng | `L + n × I` |
| Tiết kiệm phần lookup (mô hình) | `(n - 1) × L` |

Ví dụ: với 10.000 request đều gọi cùng một method, bản **lookup mỗi request** có 10.000 lần tìm; bản cache có một lần tìm và 10.000 lần invoke. Hai bản vẫn thực thi 10.000 lần phương thức nghiệp vụ.

Đây là mô hình để thấy rõ nguồn chi phí, **không phải** khẳng định mỗi `L` hoặc `I` là một hằng số có thể đo độc lập hoàn toàn trên JVM thực. Nếu method chỉ dùng một lần, cache có thể không có ý nghĩa; nếu target làm I/O kéo dài, phần tiết kiệm lookup có thể quá nhỏ so với thời gian end-to-end.

## 3. Ví dụ Java 17+ chạy được: đếm lookup và invoke

Ví dụ này cố ý **không in nanosecond** vì timing cực ngắn từ một lượt chạy dễ gây kết luận sai. Thay vào đó, mã dùng kết quả xác định để chứng minh cache `Method` giảm **số lần tra cứu metadata**, trong khi số lần invoke không đổi.

Lưu tệp **ReflectionCostDemo.java**:

~~~java
import java.lang.reflect.Method;

public class ReflectionCostDemo {
    public static final class Calculator {
        public int add(int a, int b) {
            return a + b;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Calculator target = new Calculator();
        final int calls = 4;

        // Cách 1: mỗi lời gọi đều lookup lại Method.
        int repeatedSum = 0;
        int repeatedLookups = 0;
        for (int i = 1; i <= calls; i++) {
            Method method = Calculator.class.getMethod(
                    "add", int.class, int.class);
            repeatedLookups++;
            repeatedSum += (Integer) method.invoke(target, i, 10);
        }

        // Cách 2: lookup một lần rồi invoke nhiều lần.
        Method cached = Calculator.class.getMethod(
                "add", int.class, int.class);
        int cachedSum = 0;
        for (int i = 1; i <= calls; i++) {
            cachedSum += (Integer) cached.invoke(target, i, 10);
        }

        // Đối chứng: gọi Java trực tiếp, không dùng reflection.
        int directSum = 0;
        for (int i = 1; i <= calls; i++) {
            directSum += target.add(i, 10);
        }

        System.out.println("repeated: lookups=" + repeatedLookups
                + ", invokes=" + calls + ", sum=" + repeatedSum);
        System.out.println("cached: lookups=1, invokes=" + calls
                + ", sum=" + cachedSum);
        System.out.println("direct: lookups=0, invokes=0, sum=" + directSum);
        System.out.println("metadata: name=" + cached.getName()
                + ", parameters=" + cached.getParameterCount()
                + ", return=" + cached.getReturnType().getTypeName());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ReflectionCostDemo.java
java ReflectionCostDemo
~~~

Stdout:

~~~text
repeated: lookups=4, invokes=4, sum=50
cached: lookups=1, invokes=4, sum=50
direct: lookups=0, invokes=0, sum=50
metadata: name=add, parameters=2, return=int
~~~

Giải thích:

1. Ba đường đi đều thực thi phép cộng với `i` từ 1 đến 4 và `10`, cho tổng `11 + 12 + 13 + 14 = 50`.
2. Cách thứ nhất gọi `Class.getMethod(...)` **bốn lần**; cách thứ hai chỉ gọi **một lần**. Cả hai đều dùng `Method.invoke(...)` bốn lần.
3. `cached.getName()`, `getParameterCount()` và `getReturnType()` cho thấy có thể đọc metadata từ `Method` đã lưu mà không thực hiện lại `getMethod(...)`.
4. Đây là **thử nghiệm về cấu trúc công việc**, không phải microbenchmark về thời gian thực. Chỉ số lookup/invoke và stdout trên đây là cố định; latency giữa máy, JDK và từng lần chạy thì không.

## 4. Thiết kế cache đúng phạm vi

### 4.1. Cache ở đâu?

- **Một phương thức cố định trong thành phần dùng lâu dài:** resolve `Method` lúc khởi tạo và giữ trong một field; tái dùng trên nhiều request.
- **Một class có nhiều method:** xây index theo tên + parameter types hoặc annotation một lần khi đăng ký route/serializer/DI bean. Đừng mỗi request quét `getDeclaredMethods()` rồi lọc.
- **Nhiều class động:** dùng cấu trúc cache có key phân biệt đúng `Class<?>` và signature, xử lý đồng thời nếu dùng nhiều thread; cân nhắc `ClassValue<T>` để liên kết dữ liệu với vòng đời class.
- **Plugin/hot reload/classloader có thể thay đổi:** tránh `static Map<Class<?>, Method>` mạnh, tồn tại mãi không giới hạn. Key và value giữ tham chiếu đến class/classloader có thể cản việc unload; dùng vòng đời rõ ràng hoặc cơ chế cache phù hợp.

Một lỗi thường gặp là cache `Method` chỉ bằng tên chuỗi `"save"`. Hai class hoặc hai overload có thể có cùng tên nhưng khác tham số và khai báo. Cache key thường cần bao gồm **class identity (và ngữ cảnh class loader), tên phương thức, kiểu tham số, cách lookup**; đừng chỉ dựa vào tên class dưới dạng String.

`Method` có thể được tái dùng giữa các thread cho mục đích invocation thông thường, miễn chính đối tượng target và dữ liệu nghiệp vụ được quản lý thread-safety đúng cách. Cache không làm `target` trở thành thread-safe. Ngoài ra, việc gọi `trySetAccessible()`/`setAccessible(true)` bị giới hạn bởi quyền và module access; cache kết quả tra cứu không tự vượt qua JPMS.

### 4.2. Cache loại bỏ lookup nào, và không loại bỏ phần nào?

| Quyết định | Có thể giảm | Không tự giảm |
| --- | --- | --- |
| Tái dùng `Method` | Lặp lại `getMethod` / `getDeclaredMethod` | Overhead invoke, thân method |
| Cache kết quả quét annotation/generic hợp lệ | Phân tích/lọc metadata lặp đi lặp lại | Công việc dùng kết quả đó ở mỗi request |
| Chuẩn bị `MethodHandle` một lần | Lặp lại bước lookup/tạo handle | Chi phí thân method; mức lợi từ JIT phải đo |
| Đổi reflection sang gọi Java trực tiếp | Có thể loại bỏ nhiều lớp overhead động | Không thay đổi I/O/CPU nghiệp vụ của method |
| Cache cả **kết quả nghiệp vụ** | Có thể tránh gọi method nếu kết quả được phép tái sử dụng | Tính đúng đắn về dữ liệu mới, invalidation và đồng thời |

Không nhầm **cache metadata** với **cache kết quả nghiệp vụ**. Cache `Method` không trả lại giá trị cũ của lần invoke, còn cache kết quả method phải có quy tắc key/TTL/invalidation riêng.

### 4.3. Khi nào cần MethodHandle hoặc VarHandle?

`java.lang.invoke.MethodHandle` đại diện một đường gọi được chuẩn bị qua `MethodHandles.Lookup` và có thể dùng `invokeExact`/`invoke` theo các quy tắc kiểu riêng. Với đường gọi rất nóng và handle ổn định, JIT có thể tối ưu tốt, nhưng lookup/setup handle cũng có chi phí và không có bảo đảm mọi benchmark đều nhanh hơn reflection.

- Với method hot path, so sánh **cached `Method.invoke`** và **cached `MethodHandle`**, thay vì so handle đã chuẩn bị với `getMethod + invoke` trong mỗi vòng rồi quy chênh lệch hoàn toàn cho invoke.
- `VarHandle` là cơ chế truy cập biến/field có ngữ nghĩa memory-order rõ ràng, khác với phương thức; dùng đúng API theo nhiệm vụ.
- Đừng bỏ qua quyền truy cập, module openness, chữ ký kiểu chính xác và chi phí adaptation của method handle.

## 5. Đo hiệu năng đúng: tách lookup, invoke và tổng đường đi

`System.nanoTime()` là đồng hồ đo khoảng thời gian, nhưng đọc vài lần trong một hàm `main` **không đủ** để kết luận chi phí đơn lẻ của reflection: JIT chưa warm up, khởi tạo class, dead-code elimination, constant folding, GC, CPU scheduling và cache của chính JVM đều có thể ảnh hưởng.

Nên dùng **JMH (Java Microbenchmark Harness)** khi mục tiêu thật sự là so sánh nanosecond/op hoặc throughput trong tình huống nhỏ. Tạo các benchmark tách biệt và chạy trên cùng môi trường:

| Benchmark | Công việc trong mỗi operation | Câu hỏi trả lời |
| --- | --- | --- |
| `lookupOnly` | `clazz.getMethod(name, parameterTypes...)`, consume `Method` | Chi phí tra cứu ở điều kiện đã warm up là bao nhiêu? |
| `invokeCached` | `cachedMethod.invoke(target, ...)`, consume kết quả | Chi phí invoke với `Method` đã có là bao nhiêu? |
| `lookupThenInvoke` | Lookup và invoke trong cùng operation | Tổng chi phí khi code thật tìm lại mỗi lần là bao nhiêu? |
| `directCall` | `target.add(...)`, consume kết quả | Đường gọi Java bình thường cùng logic đích tốn bao nhiêu? |
| `cachedHandleCall` (tùy chọn) | Dùng `MethodHandle` tạo ở setup | Có cơ hội tối ưu khác khi chuẩn bị handle không? |

Một thiết kế thử nghiệm có ý nghĩa phải:

1. Chuẩn bị `target` và `Method` trong `@Setup` cho nhánh cached; **không đưa setup vào thời gian của `invokeCached`**.
2. Có warmup, measurement iterations, nhiều fork/JVM độc lập; ghi phiên bản JDK, máy, JMH, flags và trạng thái compiler.
3. Dùng giá trị đầu vào/kết quả đúng để trình biên dịch không loại bỏ phép tính; trong JMH có thể trả kết quả hoặc dùng `Blackhole`. Chọn target method phản ánh nhiệm vụ thực, không chỉ một phép cộng vô nghĩa nếu muốn suy luận tới ứng dụng.
4. Đo allocation và GC cùng latency/throughput khi cần; phân biệt **cold startup** với **steady state**.
5. Phân biệt `getMethod` tìm một tên với `getMethods` quét tất cả, và phân biệt signature đơn giản với generic/annotation phức tạp.
6. So kết quả tương đối qua nhiều lần thử và khoảng dao động; không gán một con số “reflection chậm gấp N lần” làm thuộc tính cố định của Java.

**Quan trọng:** trong benchmark lookup, JVM có thể tối ưu các giá trị đầu vào không đổi; điều này vẫn phản ánh một trạng thái sử dụng nhất định nhưng chưa đại diện cho lookup theo tên class/method thay đổi liên tục. Hãy đo workload phù hợp: cố định một signature, nhiều overload, hoặc nhiều kiểu được tải động, tùy ứng dụng.

### Phân tích kết quả đo theo nguồn chi phí

Giả sử profiler cho thấy phần lớn thời gian request nằm trong phương thức đích truy vấn cơ sở dữ liệu; tiết kiệm vài lần lookup hầu như không đổi latency end-to-end. Ngược lại, framework ánh xạ hàng triệu object nhỏ, mỗi object lại `getDeclaredFields()` rồi duyệt annotation, có thể tốn allocation và CPU đáng kể; cache schema/metadata một lần thường đáng thử.

Nếu `lookupOnly` chiếm tỷ trọng đáng kể, ưu tiên tái dùng metadata. Nếu `invokeCached` vẫn là nút thắt trên hot path, cân nhắc API tĩnh, method handle ổn định hoặc code generation, nhưng chỉ chọn sau khi đã đo và kiểm tra tính đúng.

## 6. Rủi ro correctness và bẫy tối ưu

### 6.1. Lookup đúng ngữ nghĩa trước khi tối ưu

`getMethod` chỉ lấy method `public` phù hợp (có xét thừa hưởng); `getDeclaredMethod` chỉ lấy member khai báo trên chính class, kể cả non-public. Chúng không thể hoán đổi chỉ vì benchmark thấy khác nhau. Nếu method bị overload, phải dùng đúng `Class<?>` của từng tham số; `Integer.class` không thể thay tùy tiện cho `int.class` khi lookup chữ ký `add(int,int)`.

### 6.2. Reflection invoke có hợp đồng runtime riêng

- `Method.invoke` có thể ném `IllegalAccessException`, `IllegalArgumentException` hoặc `InvocationTargetException`; exception do method đích ném được bọc trong `InvocationTargetException`.
- Với instance method, receiver phải phù hợp; static method không cần instance receiver.
- Tham số primitive được boxing trong mảng đối số, kết quả primitive được boxing trong `Object`; `void` trả `null`.
- Cache sai `Method` theo class/loader hoặc chữ ký dễ dẫn đến lỗi runtime, dù benchmark cache có vẻ nhanh.
- Truy cập non-public trong JPMS có thể bị chặn. Không tự động dùng `setAccessible(true)` như một mẹo hiệu năng; nó thay đổi khả năng truy cập trong phạm vi pháp luật module của JVM.

### 6.3. Đừng tối ưu trước khi biết vòng đời

Một công cụ khởi động, scan annotation một lần rồi lưu router/mapper có thể chấp nhận chi phí startup để giảm chi phí request. Một thư viện plugin sống lâu phải cân nhắc reload và unloading để không giữ class loader cũ. Và một ứng dụng hiếm dùng reflection có thể để code lookup trực tiếp cho đơn giản nếu profiler chưa thấy vấn đề.

## 7. Tổng hợp và câu hỏi tự kiểm tra

Điểm cần nhớ là **thời gian tra cứu metadata** và **thời gian thực thi lời gọi phản xạ** thuộc hai công đoạn. Bản cache `Method` giúp tránh công đoạn tìm `Method` lặp lại; không loại bỏ chi phí `Method.invoke` hoặc công việc của phương thức nghiệp vụ. Metadata chuyên sâu (generic, annotation) cũng có chi phí phân tích riêng cần đo đúng chỗ. Mức lợi luôn phụ thuộc workload/JDK/JIT, nên dùng profiler và JMH thay cho số liệu phỏng đoán.

Tự kiểm tra:

1. Cùng một `Method` gọi 1000 lần, tại sao nên thử cache thay cho 1000 lần `getMethod`? Chi phí nào vẫn tồn tại?
2. `getMethods()` và `getMethod("add", int.class, int.class)` khác nhau về ý nghĩa và lượng công việc như thế nào?
3. Vì sao output `sum=50` của ví dụ chỉ chứng minh tính tương đương chức năng, không chứng minh tốc độ?
4. Khi benchmark `cachedHandleCall` và `lookupThenInvoke`, vì sao kết luận “MethodHandle invoke nhanh hơn” là chưa được kiểm chứng?
5. Khi dùng cache trong plugin có class loader riêng, làm sao tránh nhầm type identity và giữ class loader đã không còn dùng?

### Tài liệu API chính thức

- Java SE 21 — `Class`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
- Java SE 21 — `Method`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html
- Java SE 21 — `ClassValue`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassValue.html
- Java SE 21 — `MethodHandle`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandle.html
- JMH (OpenJDK benchmark harness): https://github.com/openjdk/jmh

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-002">Cache Field, Method và Constructor theo ngữ cảnh phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Cache Field, Method và Constructor theo ngữ cảnh phù hợp

## 1. Vì sao và khi nào nên cache metadata Reflection?

Trong Java Reflection, một chương trình có thể tìm metadata của class bằng những API như `Class.getDeclaredField(...)`, `getDeclaredMethod(...)`, `getDeclaredConstructor(...)`, sau đó dùng đối tượng `Field`, `Method` hoặc `Constructor` để đọc/ghi field, gọi phương thức hoặc tạo object.

Nếu một framework lặp đi lặp lại thao tác **tra cứu đúng cùng một thành viên** khi xử lý hàng nghìn đối tượng, việc tìm metadata nhiều lần có thể tạo chi phí không cần thiết. Cache cho phép tách hai việc:

1. **Khám phá thành viên một lần** trong một ngữ cảnh cụ thể: tên class, tên thành viên, kiểu tham số, quyền truy cập.
2. **Sử dụng metadata đã tìm** cho những receiver hoặc đối số phù hợp trong các lần thao tác tiếp theo.

Ví dụ, một serializer ghi giá trị field `name` cho rất nhiều đối tượng `Person` có thể tra cứu `Field` đó một lần rồi dùng `field.get(person)` trên từng instance. Một dependency injector có thể cache constructor đã lựa chọn theo loại component; một router có thể cache method tương ứng với endpoint cố định.

Tuy nhiên **cache không mặc nhiên làm lời gọi Reflection nhanh như lời gọi Java trực tiếp**. Nó chủ yếu tránh chi phí tra cứu metadata lặp lại. `Method.invoke(...)` vẫn có chi phí gọi gián tiếp, kiểm tra kiểu, boxing/unboxing hoặc xử lý ngoại lệ phù hợp. Cơ chế thực thi Reflection và JIT có thể khác nhau giữa các phiên bản JDK. Hãy tối ưu dựa trên profile/benchmark khi performance là yêu cầu thật.

### Mô hình ba lớp

```text
Đặc tả cần truy cập
  └── Class + loại thành viên + tên + chữ ký + chính sách quyền
        ↓
Tra cứu/kiểm tra một lần
  └── Field / Method / Constructor
        ↓
Cache theo ngữ cảnh phù hợp
  └── Metadata đã được chọn
        ↓
Dùng cho từng object/lần gọi
  └── Field.get/set | Method.invoke | Constructor.newInstance
```

Điều quan trọng nhất không phải “đưa mọi thứ vào static Map”, mà là chọn **đúng đơn vị tái sử dụng, khóa cache và vòng đời cache**.

## 2. Thông tin cần lưu của Field, Method, Constructor

Ba loại metadata đều thuộc Reflection nhưng có quy tắc tra cứu và sử dụng khác nhau:

| Loại | Tra cứu thông thường | Khóa nhận diện cần thiết | Sử dụng sau khi cache |
| --- | --- | --- | --- |
| `Field` | `getDeclaredField("name")` / `getField("name")` | Lớp/ngữ cảnh khai báo, tên field, chính sách tìm declared/public | `field.get(receiver)`, `field.set(receiver, value)` |
| `Method` | `getDeclaredMethod("save", String.class)` / `getMethod(...)` | Lớp/ngữ cảnh tìm, **tên và danh sách kiểu tham số theo đúng thứ tự**, chính sách tìm | `method.invoke(receiver, args...)` |
| `Constructor` | `getDeclaredConstructor(String.class)` / `getConstructor(...)` | Lớp cần tạo và **danh sách kiểu tham số**, chính sách tìm | `constructor.newInstance(args...)` |

- **Instance receiver không phải một phần của khóa metadata** nếu cùng một `Field`/`Method` có thể dùng với nhiều instance cùng loại phù hợp. Đừng cache từng đối tượng nghiệp vụ chỉ để tránh tra cứu cùng field.
- **Field** được tra cứu theo tên. Kiểu field có thể được kiểm tra qua `field.getType()` khi cần, nhưng tên cùng chính sách tra cứu/lớp là cơ sở nhận diện trong các API nói trên.
- **Method** có thể overload. Ví dụ `parse(String)` và `parse(int)` là hai method khác nhau; cache chỉ theo tên `"parse"` là sai. Khi dùng `getDeclaredMethod` hay `getMethod`, danh sách tham số quyết định overload. Các API tra cứu Java Reflection này không nhận kiểu trả về làm tham số; nếu hệ thống yêu cầu kiểm tra return type, so sánh thêm `method.getReturnType()` theo hợp đồng của hệ thống.
- **Constructor** không có tên riêng để truyền vào API tra cứu. Khóa cần lớp và các kiểu tham số; `getDeclaredConstructor()` không đồng nghĩa với “chọn constructor bất kỳ”.
- `getDeclaredXxx` tra cứu thành viên **khai báo trực tiếp** ở lớp được hỏi, kể cả non-public, nhưng không tự duyệt toàn bộ cấp cha cho mọi loại thành viên. `getXxx` công khai tìm public member theo quy tắc kế thừa. Hai chiến lược tra cứu này không được dùng chung cache key khi ý nghĩa khác nhau.

Cũng phải phân biệt **cache đối tượng mô tả** với **cache kết quả đọc dữ liệu**. Cache `Field` có thể hợp lệ; cache `field.get(person)` thì sẽ giữ một giá trị từ một thời điểm, có thể đã lỗi thời nếu field thay đổi.

## 3. Thiết kế khóa cache an toàn

Nếu cache phục vụ một class cố định cùng danh sách thành viên cố định, có thể tạo một bộ metadata gắn với class đó. Nếu cache dùng cho nhiều class, overload và chiến lược lookup, cần khóa mô tả đầy đủ.

Một khóa cho method động có thể được định nghĩa như sau:

```java
record MethodKey(
        Class<?> owner,
        String name,
        java.util.List<Class<?>> parameterTypes,
        boolean declaredOnly) {}
```

Đoạn này chỉ minh họa khai báo khóa, không phải chương trình độc lập. Khi tạo key, dùng một danh sách **bất biến được copy từ các kiểu tham số**, chẳng hạn:

```java
MethodKey key = new MethodKey(
        Person.class,
        "greet",
        java.util.List.of(String.class),
        true
);
```

Những điểm cần xem xét khi triển khai cache thật:

1. **Lớp `Class<?>` xác định theo identity, gồm cả classloader**. Hai lớp có cùng tên đầy đủ nhưng do hai classloader khác nhau nạp là hai `Class` khác nhau; dùng `getName()` làm toàn bộ khóa có thể gây nhầm metadata.
2. **Overload phải có đầy đủ signature**. Đối với danh sách tham số do caller cung cấp, sao chép sang danh sách bất biến trước khi đưa vào khóa; đừng giữ một array có thể bị thay đổi làm nguồn hash/equality.
3. **Phạm vi tìm thành viên** phải được cố định: declared-only, public/inherited hoặc thuật toán tìm thủ công trên cây lớp. Cùng tên và signature nhưng lookup policy khác có thể cho kết quả khác.
4. **Quyền và ngữ cảnh caller** không tự suy ra chỉ từ tên lớp. Nếu framework tạo metadata đã được `setAccessible(true)` hoặc tạo handle bằng lookup có đặc quyền, đừng phát tán đối tượng đó qua một cache toàn cục mà code ít tin cậy cũng đọc được.
5. **Chính sách chọn constructor** phải ổn định: nếu framework chọn theo annotation, visibility hay config, những yếu tố ấy thuộc hợp đồng của cache. Thay đổi quy tắc chọn phải làm mất hiệu lực entry thích hợp.

Ví dụ sai:

```java
// Sai: hai overload save(String) và save(int) bị gộp vào cùng key.
java.util.Map<String, java.lang.reflect.Method> methodsByName;
```

Ví dụ tốt hơn về cấu trúc, khi mỗi class có cache riêng:

```java
record Signature(String name, java.util.List<Class<?>> params) {}

java.util.concurrent.ConcurrentMap<Signature, java.lang.reflect.Method> methods;
```

Hai khai báo trên là các mảnh của một thiết kế, chưa khởi tạo biến hay xử lý ngoại lệ. Class chủ sở hữu có thể là khóa ở lớp ngoài; khi cache theo class, inner key chỉ cần mô tả các method **bên trong** đúng lớp/ngữ cảnh đó.

## 4. Chọn phạm vi cache theo vòng đời hệ thống

Có nhiều phạm vi hợp lệ. Chọn theo mức ổn định của class, quyền truy cập và thời gian tồn tại:

| Phạm vi | Khi phù hợp | Lưu ý |
| --- | --- | --- |
| Biến local trong một đợt xử lý | Chỉ dùng lặp lại trong một request/batch | Dễ kiểm soát, không giữ class/object lâu hơn batch |
| Một service hoặc component | Service cố định một vài kiểu DTO/domain | Cache sống theo vòng đời service, dễ invalidation |
| Theo mỗi `Class<?>` với `ClassValue<T>` | Thư viện xử lý nhiều lớp, muốn dữ liệu gắn với lớp và vòng đời lớp | Phù hợp hơn static map mạnh trong môi trường classloader thay đổi |
| Cache dùng chung theo key đầy đủ | Nhiều entry động, công việc thực sự tái sử dụng metadata | Cần kiểm soát quyền, đồng thời, kích thước và classloader |

### Nguy cơ static Map giữ classloader

Một cache cấp ứng dụng như:

```java
static final java.util.Map<Class<?>, Object> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
```

có **strong reference** tới `Class<?>` và giá trị. Nếu cache sống lâu hơn plugin, deployment hoặc classloader chứa class, nó có thể ngăn các class/classloader đó được thu gom. Đây là vấn đề thực tế với application server có hot deploy, plugin system, IDE, framework dùng generated classes.

Chuyển khóa sang `WeakHashMap<Class<?>, Field>` **không tự động giải quyết mọi leak**: giá trị `Field`/`Method` có thể tham chiếu ngược tới declaring class, tạo đường strong reference từ map đến chính khóa qua value. Dùng `ClassValue` hoặc cache được quản lý theo vòng đời component/classloader thường rõ ràng hơn. Nếu vẫn dùng map lâu sống, cần có lifecycle cleanup, giới hạn số entry phù hợp và kiểm tra tham chiếu giữ lại.

### `ClassValue<T>`

`ClassValue<T>` cung cấp ánh xạ giá trị **theo từng `Class<?>`**, hữu ích khi muốn giữ metadata thuộc về một class mà không xây một static map mạnh theo class. Khi `ClassValue.get(type)` chưa có giá trị cho class ấy, `computeValue(type)` sẽ được gọi để tính.

```java
ClassValue<SomeMetadata> cache = new ClassValue<>() {
    @Override
    protected SomeMetadata computeValue(Class<?> type) {
        // Khám phá và tạo metadata cho chính type này.
        return buildMetadata(type);
    }
};
```

Đây là **pseudocode Java theo hợp đồng**: `SomeMetadata` và `buildMetadata` phải được định nghĩa trong ứng dụng; ví dụ hoàn chỉnh ở mục 6.

`ClassValue` hỗ trợ truy cập đồng thời, nhưng **`computeValue` có thể bị gọi nhiều lần cho cùng một class khi có cạnh tranh**; chỉ một giá trị phù hợp được xuất bản cho các `get` thành công. Vì vậy, hàm compute nên thuần hoặc có tác dụng phụ an toàn, không khởi chạy một lần duy nhất các hành động không thể lặp. `ClassValue.remove(type)` cho phép bỏ giá trị của class, nhưng khi có thread đang tính hoặc đọc đồng thời, cần thiết kế lại quy trình invalidation thật cẩn thận.

## 5. Quyền truy cập: cache metadata không phải giấy phép vượt private/module

Mỗi `Field`, `Method` và `Constructor` là một đối tượng phản chiếu. Tra cứu được một member **không có nghĩa** mọi caller đều được phép thao tác trên member đó.

- Với member public ở class public/package exported hợp lệ, thường có thể dùng các phương thức `getField`, `getMethod`, `getConstructor` mà không cần nới quyền.
- Với member private/package-private, thao tác reflective có thể bị chặn vì access control. `setAccessible(true)` chỉ thành công khi Java language/module rules cho phép mở quyền; trong named modules, `opens` và quan hệ module có thể quyết định kết quả.
- Nếu không có quyền mở truy cập, `setAccessible(true)` có thể ném `InaccessibleObjectException`, một unchecked exception. Tùy nhu cầu có thể sử dụng `trySetAccessible()` để nhận tín hiệu `false` khi không thể bật, rồi phản hồi phù hợp.
- **Cờ accessible của đối tượng `AccessibleObject` là trạng thái mutable**. Nếu một thành phần đã bật quyền rồi chia sẻ cùng `Method`/`Field` cho code không tin cậy, đó có thể là vấn đề bảo mật. Cache metadata nên nằm trong phạm vi dịch vụ tin cậy sử dụng nó.
- Reflection không làm mất đi cơ chế bảo vệ riêng của module, cũng không khiến field `final` bình thường trở thành điểm cập nhật tùy ý theo mọi hoàn cảnh.

**Quy trình khuyến nghị:** cache khi đã xác định chắc chắn member cần thao tác và quyền cần thiết; tập trung logic truy cập vào component có trách nhiệm. Đừng gọi `setAccessible(true)` đại trà lên mọi field của mọi class, vì dễ vi phạm chính sách module và tạo bề mặt truy cập rộng.

## 6. Ví dụ độc lập Java 17+: cache ba loại member bằng ClassValue

Ví dụ dưới đây có một lớp `Person` với constructor, field và method private. Cache chứa cả ba metadata, mỗi lần gọi `CACHE.get(Person.class)` đều nhận bộ member cho đúng `Person`. Việc mở quyền thành công trong ví dụ vì `Person` là nested class cùng **nest** với lớp truy cập, nên Java cho phép ngữ cảnh này truy cập private member; không được mặc định điều đó khi đổi sang class thuộc module khác.

Lưu file **`ReflectionCacheDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionCacheDemo {
    private static class Person {
        private String name;

        private Person(String name) {
            this.name = name;
        }

        private String greet(String greeting) {
            return greeting + " " + name;
        }
    }

    private record Accessors(
            Constructor<?> constructor,
            Field field,
            Method method) {
    }

    private static final ClassValue<Accessors> CACHE = new ClassValue<>() {
        @Override
        protected Accessors computeValue(Class<?> type) {
            if (type != Person.class) {
                throw new IllegalArgumentException("Class not supported");
            }
            try {
                Constructor<?> constructor =
                        type.getDeclaredConstructor(String.class);
                Field field = type.getDeclaredField("name");
                Method method = type.getDeclaredMethod("greet", String.class);

                constructor.setAccessible(true);
                field.setAccessible(true);
                method.setAccessible(true);

                return new Accessors(constructor, field, method);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Cannot resolve members", ex);
            }
        }
    };

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Accessors a = CACHE.get(Person.class);
        Person person = (Person) a.constructor().newInstance("Lan");

        System.out.println(a.field().get(person));
        System.out.println(a.method().invoke(person, "Hello,"));

        a.field().set(person, "Minh");
        System.out.println(a.method().invoke(person, "Hi,"));

        System.out.println(a == CACHE.get(Person.class));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ReflectionCacheDemo.java
java ReflectionCacheDemo
```

Kết quả:

```text
Lan
Hello, Lan
Hi, Minh
true
```

Giải thích:

1. Lần đầu `CACHE.get(Person.class)` cần tạo `Accessors` bằng `computeValue`: tìm constructor `Person(String)`, field `name` và method `greet(String)`.
2. Bộ metadata được dùng để tạo một object `Person("Lan")`, đọc field và gọi method. Không cần tra cứu lại mỗi lần gọi `get`/`invoke`.
3. Sau khi đổi field trên instance thành `"Minh"`, gọi method đã cache sẽ thấy **giá trị hiện tại** của instance; cache metadata không đóng băng dữ liệu nghiệp vụ.
4. Trong lần chạy tuần tự này, `CACHE.get(Person.class)` lần sau trả cùng tham chiếu `Accessors`, nên so sánh bằng `==` in `true`.

Vì `setAccessible(true)` có thể không hợp lệ ở các ngữ cảnh khác, ví dụ này **không** khuyến khích cấp quyền private cho mọi class. Với member public, nên ưu tiên tra cứu/truy cập public thông thường. Trong triển khai thực tế, việc bắt `ReflectiveOperationException` trong `computeValue` chưa xử lý `InaccessibleObjectException` (unchecked) nếu cấu hình module không cho phép; cần báo rõ lỗi cấu hình thay vì che giấu.

## 7. Cache nhiều member của cùng một lớp

Trong serializer hoặc framework, mỗi lớp có thể có hàng chục field và method. Khi đó một `ClassValue` có thể giữ **bảng metadata riêng cho từng class**:

```text
ClassValue<PerClassCache>
   |
   +-- Person.class → PerClassCache
   |       +-- Field "name"
   |       +-- Method ("greet", [String])
   |       +-- Constructor ([String])
   |
   +-- Order.class → PerClassCache
           +-- Field "status"
           +-- Method ("update", [int])
           +-- Constructor ([long])
```

Thiết kế một `PerClassCache` gồm nhiều `ConcurrentHashMap`, mỗi map dùng kiểu khóa phù hợp. Cách này:

- Giảm việc giữ trực tiếp `Class<?>` trong static map toàn cục.
- Tập trung chính sách tìm member và quyền truy cập trong mỗi class.
- Cho phép lazy lookup theo tên/signature thay vì khám phá toàn bộ member khi chưa cần.
- Dễ gắn metrics về số cache hit/miss nếu thật sự cần tối ưu.

### ConcurrentHashMap và checked exception

Lệnh `computeIfAbsent` tiện lợi nhưng hàm mapping không được khai báo ném checked `NoSuchMethodException`. Một ví dụ rút gọn minh họa cách bọc lỗi:

```java
Method result = methods.computeIfAbsent(key, k -> {
    try {
        return owner.getDeclaredMethod(
                k.name(),
                k.parameterTypes().toArray(Class<?>[]::new)
        );
    } catch (NoSuchMethodException ex) {
        throw new IllegalArgumentException("Method not found: " + k, ex);
    }
});
```

Đây là **đoạn minh họa trong một implementation `PerClassCache`**, không phải file Java chạy độc lập. Nó giả định có `methods: ConcurrentHashMap<Signature,Method>`, `key: Signature`, `owner: Class<?>` và `Signature` cung cấp `name()`/`parameterTypes()`. Một hệ thống thật cần quyết định trước cách xử lý lỗi: `computeIfAbsent` không cache kết quả `null` hay exception; truy vấn luôn thất bại có thể tiếp tục tốn chi phí nếu được gọi đi gọi lại.

Khi concurrency cao, mapping function có thể tạo tranh chấp khóa. Không đặt tác vụ I/O, truy vấn mạng, gọi lại vòng vào cùng map hoặc tác dụng phụ nặng trong compute. Xây key bất biến rồi cache thành viên có giá trị hợp lệ; khi metadata hết hiệu lực theo lifecycle, hủy cả cache liên quan.

## 8. Những trường hợp phải cẩn thận

### a) Method overload và method được kế thừa

Với `UserService.save(User)` và `UserService.save(String)`, khóa `(name="save")` không đủ. Nếu dùng `getDeclaredMethod` trên subclass, member kế thừa không nhất thiết hiện diện trong danh sách declared. Hãy cố định thuật toán tra cứu và chữ ký; nếu chuyển sang `getMethod` hoặc duyệt superclass thủ công, cần khóa/cấu hình phân biệt đúng hành vi.

### b) Proxy và nhiều classloader

Framework tạo proxy class có thể sử dụng nhiều `Class<?>` dù các class có cấu trúc tương tự. `Method` tìm cho class A không tự nhiên phù hợp với mọi proxy hoặc class cùng tên do loader khác nạp. Với `Method.invoke`, receiver thực tế phải tương thích với **declaring class** của method đó. Cache theo classloader chỉ bằng string như `"com.example.User"` là thiếu an toàn.

### c) Truy cập bất đồng bộ và đối tượng metadata dùng chung

`Field`, `Method` và `Constructor` có thể được tái sử dụng khi nhiều thread cùng gọi thông qua Reflection; điều đó không có nghĩa **đối tượng nghiệp vụ được truy cập tự động thread-safe**. Ví dụ `field.set(...)` cùng field trên một object không tạo atomicity/happens-before theo ý muốn. Nếu bài toán cần kiểm soát memory ordering hay CAS, xem `VarHandle` hoặc primitive đồng bộ tương ứng, không chỉ cache `Field`.

### d) Cache stale hoặc thay đổi mô hình runtime

Thay một class bằng phiên bản nạp từ classloader mới tạo ra **Class object khác**. Cần tìm metadata từ class mới. Với plugin reload, hot redeploy, instrumentation hoặc schema mapping thay đổi, định nghĩa rõ lúc nào bỏ cache theo loader/component/phiên bản. `ClassValue.remove(type)` chỉ loại entry gắn với `type` đó; không có nghĩa xóa mọi cấu trúc tham chiếu liên quan bên ngoài.

### e) Memory cost và lợi ích nhỏ

Nếu thành viên chỉ được gọi một lần, tạo map, key, synchronization và entry cache có thể tốn nhiều hơn việc tra cứu trực tiếp. Nếu một ứng dụng quét rất nhiều class nhưng chỉ sử dụng một phần nhỏ member, cache tất cả có thể tốn bộ nhớ. Với hot loop, cần đo riêng **chi phí tìm metadata** và **chi phí gọi member**, thay vì chỉ benchmark chung và gán mọi cải thiện cho cache.

### f) Dữ liệu nhạy cảm và phạm vi tin cậy

Một shared cache cho phép truy cập metadata private đã mở quyền có thể vô tình tạo khả năng truy cập từ component khác. Thiết kế ownership rõ ràng: chỉ code có quyền mới lấy hoặc nhận member đặc quyền; không công khai map member được bật accessible thành một service đa tenant mà không có kiểm tra quyền.

## 9. Đo hiệu năng đúng cách

Nếu mục tiêu là giảm độ trễ, hãy thử hai đường đi:

- **Không cache:** mỗi vòng gọi `getDeclaredMethod(...)` rồi `invoke(...)`.
- **Có cache:** tìm một lần hoặc lấy `Method` từ cache rồi `invoke(...)`.

Nhưng cần tách các chi phí: tạo key, cache hit/miss, lookup, invocation, autoboxing, kết quả và độ trễ của chính method được gọi. Nếu method làm I/O vài mili giây, tối ưu metadata có thể không đáng kể. Nếu call site rất nóng, cân nhắc giao diện trực tiếp hoặc `MethodHandle` sau khi đã đo và khi thiết kế cho phép.

Nên dùng JMH hoặc công cụ đo phù hợp có warmup, nhiều lần chạy, fork JVM, kiểm soát Dead Code Elimination và GC. Tránh kết luận từ một vòng `System.nanoTime()` đơn lẻ. Đặc biệt, **thời gian cache hit không bằng 0**: concurrent map lookup, key construction và memory footprint đều có giá.

Metrics hữu ích có thể gồm: số lớp/entry đang cache, số lần build metadata, cache hit/miss, số lỗi access/lookup, thời gian xử lý mỗi loại request, tổng heap giữ bởi cache và số classloader được giữ lại sau redeploy. Chọn số đo gắn với câu hỏi hiệu năng thực tế, không thêm cache chỉ vì mã “trông nhanh hơn”.

## 10. Quy trình quyết định và thực hành

Trước khi cache một thành viên Reflection, lần lượt trả lời:

1. **Có lặp lại đủ nhiều không?** Nếu chỉ tra cứu một lần, chưa cần cache.
2. **Khóa đã xác định đúng member chưa?** Với method/constructor phải có tham số đúng thứ tự; phân biệt declared và public lookup.
3. **Phạm vi và vòng đời là gì?** Request, component, class hay classloader; có plugin/redeploy không?
4. **Quyền của người dùng cache có thống nhất không?** Tránh đưa member đã nới quyền vào cache dùng bởi nhiều vùng tin cậy khác nhau.
5. **Cạnh tranh và invalidation ra sao?** Dùng cấu trúc concurrency phù hợp; xử lý lookup thất bại rõ ràng, không side effect nhạy cảm trong compute.
6. **Đã đo đúng nút thắt chưa?** Nếu chi phí nằm ở invoke hoặc công việc nghiệp vụ, cache lookup có thể chưa đủ.

### Bài tập tự kiểm tra

- Tạo hai method overload `convert(String)` và `convert(int)`. Giải thích vì sao map theo chuỗi `"convert"` là sai và thiết kế khóa phân biệt cả hai.
- Trong ví dụ `ReflectionCacheDemo`, dùng cùng `Accessors` trên hai object `Person` khác nhau. Cache lưu metadata nào, và giá trị `name` của từng object có ảnh hưởng nhau không?
- Chuyển `Person` sang một named module khác không `opens` package cho bên gọi. Giải thích vì sao `setAccessible(true)` có thể thất bại ngay cả khi lookup member thành công.
- Một plugin được nạp và gỡ liên tục. Vì sao static `ConcurrentHashMap<Class<?>,Method>` có thể giữ classloader cũ, và `ClassValue` giải quyết bài toán quản lý metadata theo class ra sao?
- Nếu một ứng dụng xử lý 100 request nhưng chỉ có 2 request dùng một method hiếm, cache eager toàn bộ method của class có chắc hiệu quả không? Bạn sẽ đo điều gì?

**Kết quả cần đạt:** biết khi nào cache `Field`/`Method`/`Constructor` có lợi, xây khóa cache phù hợp với chữ ký và chiến lược lookup, giữ đúng phạm vi quyền truy cập, tránh rò rỉ classloader và kiểm chứng hiệu năng bằng dữ liệu đo thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-003">Khóa cache cần xét Class và định danh loader</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa cache cần xét `Class` và định danh ClassLoader trong Java Reflection

## 1. Vấn đề: “cùng tên lớp” chưa chắc là cùng kiểu

Trong ứng dụng dùng **Reflection**, framework, plugin, ứng dụng chạy nhiều class loader hay cơ chế hot reload, chúng ta thường cache kết quả tốn chi phí tìm kiếm như `Class<?>`, `Method`, `Constructor<?>`, `Field`, annotation metadata hoặc bộ chuyển đổi dữ liệu. Lỗi khó phát hiện xuất hiện khi khóa cache chỉ là tên chuỗi:

~~~java
// Cách dễ sai khi có nhiều class loader
Map<String, Method> methodCache = new HashMap<>();
methodCache.put("com.example.Plugin#execute", aMethod);
~~~

Tên `com.example.Plugin` chỉ là **binary name**. Nó không đủ nhận diện một lớp Java tại runtime: **hai class loader khác nhau có thể định nghĩa hai class khác nhau mang cùng binary name**. Nếu lấy `Method` từ lớp thứ nhất đem gọi trên đối tượng của lớp thứ hai, Java Reflection có thể ném `IllegalArgumentException` vì receiver không phải instance của declaring class phù hợp.

Bài này giải thích cách chọn khóa đúng, minh họa lỗi bằng Java 17+ chạy được, đề xuất `ClassValue` để giữ metadata theo từng `Class<?>`, và phân tích cache gây giữ class loader trong bộ nhớ.

**Kết quả cần đạt:** phân biệt tên lớp, class loader được yêu cầu nạp và class loader thực sự định nghĩa; xây cache có định danh chính xác; xử lý overload/thread safety; tránh giữ class/plugin cũ sau khi unload.

## 2. Định danh lớp tại JVM: binary name **cùng** defining loader

JVM xác định một class/interface runtime dựa trên **tên nhị phân và class loader định nghĩa nó**. Vì vậy, có thể có:

~~~text
Loader-A --define--> demo.Plugin   (Class object A)
Loader-B --define--> demo.Plugin   (Class object B)
~~~

Dù `Class.getName()` trả cùng `"demo.Plugin"`:

- `classA == classB` là `false`; chúng biểu diễn **hai kiểu runtime khác nhau**.
- `classA.getClassLoader() != classB.getClassLoader()` nếu chúng được định nghĩa bởi hai loader khác nhau.
- Đối tượng do `classA` tạo không tự động là instance của `classB`: `classB.isInstance(objectFromA)` trả `false`.
- `Method`/`Constructor` hoặc `Field` tra cứu trên class A không thể tùy ý áp dụng cho class B, kể cả bytecode nhìn giống hệt.
- Các static field và quá trình khởi tạo class cũng thuộc từng class runtime; việc một loader nạp cùng tên không làm hai class chia sẻ trạng thái static.

**Phân biệt defining và initiating loader:** khi gọi `requestedLoader.loadClass("demo.Plugin")`, loader ấy có thể **ủy quyền cho parent**. Kết quả `Class<?>` lúc đó do **parent định nghĩa**, nên `type.getClassLoader()` có thể khác `requestedLoader`. Chọn khóa theo `Class<?>` sau khi đã phân giải là cách đơn giản và chính xác nhất khi cache metadata phụ thuộc vào *kiểu thực tế*.

Trường hợp bootstrap loader như `String.class.getClassLoader()` có thể trả về `null`; `null` ở đây là trường hợp hợp lệ, không phải lỗi.

## 3. Chọn khóa cache theo thứ đang được cache

Không có một kiểu khóa phù hợp mọi bài toán. Trước tiên hãy xác định bạn muốn cache **kết quả phân giải tên** hay **metadata của một class đã xác định**:

| Dữ liệu cache | Khóa gợi ý | Vì sao? |
| --- | --- | --- |
| Metadata/annotation của một class | `Class<?>` hoặc `ClassValue<T>` | Chính `Class` đã mang định danh runtime gồm loader |
| Danh sách `Method` của một class | `Class<?>` rồi khóa phụ cho signature | Chặn nhầm method giữa hai lớp trùng tên |
| Một `Method` cụ thể | `Class<?>` + tên + kiểu tham số + chính sách declared/public | Overload không có cùng metadata |
| Một `Constructor` | `Class<?>` + kiểu tham số | Constructor overload theo tham số |
| Một `Field` | `Class<?>` + tên field + phạm vi tra cứu | Field được gắn với declaring class cụ thể |
| Phân giải tên `"demo.Plugin"` thành `Class<?>` | **Tên + initiating loader / ngữ cảnh phân giải** | Cùng tên có thể phân giải khác nhau theo loader |
| Tra cứu theo JPMS layer/module | Khóa bổ sung `Module`/`ModuleLayer` khi cách phân giải phụ thuộc chúng | Loader đơn thuần không luôn mô tả đầy đủ chính sách tìm module |

Đối với **metadata đã có `Class<?>`**, khóa bằng `Class<?>` là **đủ để phân biệt loader**. Không bắt buộc thêm `type.getClassLoader()` vào khóa, vì hai `Class` khác nhau đã khác nhau rồi. Một khóa `(Class<?>, ClassLoader)` đôi khi dùng được, nhưng thường dư thừa và có thể vô tình giữ thêm tham chiếu mạnh.

Nếu API nhận **tên lớp dạng chuỗi** và lựa chọn loader ở thời điểm tìm, hãy dùng một khóa có cả tên **và chính ngữ cảnh yêu cầu phân giải**. Nên dùng **đối tượng loader**, không chỉ `loader.getClass().getName()`, `toString()` hay `hashCode()`: hai instance loader cùng lớp vẫn có thể định nghĩa những class khác nhau. Nếu hệ thống có layer, phiên bản plugin, biến thể lookup hay rule tùy biến, cần phản ánh những yếu tố làm thay đổi kết quả trong khóa.

**Quan hệ tương đương phải đúng:** hai request chỉ chia sẻ kết quả cache nếu chương trình thực sự bảo đảm chúng phân giải tới cùng đối tượng runtime và cùng chính sách truy cập.

## 4. Ví dụ chạy được: cùng tên nhưng hai class loader khác nhau

Ví dụ dưới đây tự đọc bytecode của lớp nested `Widget` rồi tạo hai `ClassLoader` tùy chỉnh để **định nghĩa lại đúng một tên class**. Loader tùy chỉnh chỉ bỏ qua cơ chế ủy quyền parent đối với `Widget`; những lớp còn lại vẫn ủy quyền bình thường.

Tạo file `ClassCacheKeyDemo.java`:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ClassCacheKeyDemo {
    public static class Widget {
        public Widget() {}
        public String ping() { return "pong"; }
    }

    static class ShadowLoader extends ClassLoader {
        private final byte[] bytes;

        ShadowLoader(byte[] bytes) {
            super(ClassCacheKeyDemo.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals(Widget.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    found = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) resolveClass(found);
                return found;
            }
        }
    }

    static byte[] widgetBytes() throws IOException {
        try (InputStream in = ClassCacheKeyDemo.class.getResourceAsStream(
                "ClassCacheKeyDemo$Widget.class")) {
            if (in == null) throw new IOException("Class bytes not found");
            return in.readAllBytes();
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = widgetBytes();
        Class<?> appClass = Widget.class;
        Class<?> pluginClass =
                new ShadowLoader(bytes).loadClass(Widget.class.getName());
        Class<?> otherPluginClass =
                new ShadowLoader(bytes).loadClass(Widget.class.getName());

        System.out.println("Same name: "
                + appClass.getName().equals(pluginClass.getName()));
        System.out.println("Same Class: " + (appClass == pluginClass));
        System.out.println("Different plugin classes: "
                + (pluginClass != otherPluginClass));
        System.out.println("Different defining loaders: "
                + (pluginClass.getClassLoader()
                        != otherPluginClass.getClassLoader()));

        Map<String, Method> wrongCache = new HashMap<>();
        wrongCache.put(appClass.getName() + "#ping",
                appClass.getMethod("ping"));
        Object pluginObject = pluginClass.getConstructor().newInstance();
        try {
            wrongCache.get(pluginClass.getName() + "#ping")
                    .invoke(pluginObject);
        } catch (IllegalArgumentException expected) {
            System.out.println("Wrong cached Method: IllegalArgumentException");
        }

        Map<Class<?>, Method> correctCache = new HashMap<>();
        correctCache.put(appClass, appClass.getMethod("ping"));
        correctCache.put(pluginClass, pluginClass.getMethod("ping"));
        System.out.println("Correct reflection call: "
                + correctCache.get(pluginClass).invoke(pluginObject));
    }
}
~~~

Biên dịch/chạy tại thư mục chứa file bằng **Java 17+**:

~~~bash
javac --release 17 ClassCacheKeyDemo.java
java ClassCacheKeyDemo
~~~

Kết quả thực tế đã kiểm tra trên JDK 21 với `--release 17`:

~~~text
Same name: true
Same Class: false
Different plugin classes: true
Different defining loaders: true
Wrong cached Method: IllegalArgumentException
Correct reflection call: pong
~~~

**Phân tích:**

1. Cả hai loader tùy chỉnh định nghĩa bytecode của `ClassCacheKeyDemo$Widget`, tạo các `Class<?>` khác với lớp `Widget` do loader ứng dụng định nghĩa.
2. Cache sai dùng `getName() + "#ping"` nên trả `Method` của lớp ứng dụng, dù đối tượng nhận lời gọi thuộc lớp do plugin loader định nghĩa.
3. `Method.invoke(pluginObject)` ném `IllegalArgumentException` vì receiver không thuộc kiểu khai báo method tương ứng.
4. Cache dùng `Map<Class<?>, Method>` phân biệt hai runtime class; gọi method của lớp plugin trên instance plugin trả kết quả chính xác.

Đây là **demo cố ý** dùng child-first cho một class nhằm tái hiện sự khác biệt. Đừng biến toàn bộ loader thành child-first trong sản phẩm nếu không hiểu ranh giới dependency, package, resource và bảo mật.

## 5. Cache method phải xét cả signature và quy tắc Reflection

Ngay cả khi khóa ngoài đã là `Class<?>`, một lớp có thể overload:

~~~java
String substring(int beginIndex)
String substring(int beginIndex, int endIndex)
~~~

Vì vậy không nên cache bằng mỗi tên `"substring"`. Nếu sử dụng `Class.getMethod(name, parameterTypes)` (chỉ tìm method public, có cả kế thừa), khóa phụ nên mang tên và **danh sách `Class<?>` của tham số theo đúng thứ tự**. Với `getDeclaredMethod(...)`, phải rõ chính sách lấy method khai báo trực tiếp; đừng trộn cache của `getMethod` và `getDeclaredMethod`.

Những điều dễ sót:

- `int.class` khác `Integer.class`; `String[].class` khác `String.class`.
- Overload `call(String)` khác `call(Object)`; số lượng và thứ tự tham số đều quan trọng.
- Method có varargs vẫn có kiểu tham số mảng trong Reflection, ví dụ `String...` thành `String[].class`.
- `Method.getDeclaringClass()` có thể là **lớp cha/interface khai báo** chứ không phải owner class dùng để tra cứu. Cache theo class yêu cầu và định danh method/declaring class cho phù hợp mục đích.
- Generic sau **type erasure** không làm `List<String>` và `List<Integer>` trở thành hai kiểu tham số runtime khác nhau.
- Trong bytecode có thể tồn tại bridge/synthetic method. Nếu đang làm việc với metadata thô hoặc descriptor JVM, kiểm tra cả `getReturnType()`, `isBridge()` và `isSynthetic()` khi thích hợp. API `getMethod` thông thường không cho chọn method bằng return type.
- Quyền truy cập thành viên có thể phụ thuộc **caller lookup, module, `exports`/`opens`**. Lấy được `Method` hay cache nó không đồng nghĩa mọi caller đều có quyền gọi/private access.

Với ứng dụng chỉ có một `status()` không tham số và API public, khóa phụ có thể rất đơn giản. Với thư viện Reflection tổng quát, cần biểu diễn đầy đủ signature, loại thành viên, cách lookup và chính sách truy cập.

## 6. `ClassValue<T>`: cache giá trị gắn trực tiếp với từng `Class<?>`

JDK cung cấp `java.lang.ClassValue<T>` (Java 7+) để **gắn dữ liệu tính toán theo từng class runtime**. Vì `Class<?>` đã phân biệt loader, `ClassValue` cũng phân biệt hai class trùng tên được định nghĩa bởi loader khác nhau.

~~~java
private static final ClassValue<Metadata> CACHE = new ClassValue<>() {
    @Override
    protected Metadata computeValue(Class<?> type) {
        return inspect(type);
    }
};
~~~

Đây là **fragment minh họa**; `Metadata` và `inspect` là kiểu/hàm do ứng dụng tự định nghĩa. `CACHE.get(type)` tính giá trị khi chưa có rồi lưu theo `type`; `CACHE.remove(type)` bỏ giá trị của class đó để lần tiếp theo tạo lại.

Lợi ích chính: tránh phải tự quản lý một `Map<Class<?>, ...>` toàn cục chỉ để ánh xạ class sang metadata; `ClassValue` được thiết kế riêng cho mối liên hệ này và hỗ trợ truy cập đồng thời. Tuy nhiên:

- `computeValue` **có thể chạy nhiều lần khi thread cạnh tranh**, dù chỉ một kết quả được cài đặt để trả về. Không đặt thao tác side effect không an toàn hoặc giao dịch bắt buộc chạy đúng một lần trong `computeValue`.
- `remove(type)` chỉ xóa association của **đúng đối tượng `Class<?>`** khỏi **đúng đối tượng `ClassValue`** đó. Nó không “xóa mọi cache” của ứng dụng.
- `ClassValue` không tự ngăn mọi memory leak: nếu code khác vẫn giữ `Class<?>`, loader, instance hoặc handle của plugin cũ thì lớp vẫn có thể còn sống.
- Chỉ thêm `ClassValue` khi metadata thực sự dùng nhiều lần; cache làm tăng độ phức tạp và tiêu thụ bộ nhớ nếu phép tra cứu rất rẻ/ít lặp.

### Ví dụ Java 17 hoàn chỉnh: `ClassValue` và overload

Tạo file `ClassValueDemo.java`:

~~~java
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ClassValueDemo {
    record Signature(String name, List<Class<?>> parameters) {
        Signature(String name, Class<?>... parameters) {
            this(name, List.of(parameters));
        }
    }

    private static final ClassValue<ConcurrentHashMap<Signature, Method>> CACHE =
            new ClassValue<>() {
                @Override
                protected ConcurrentHashMap<Signature, Method> computeValue(
                        Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };

    static Method publicMethod(Class<?> owner, String name,
                               Class<?>... parameters)
            throws NoSuchMethodException {
        Signature key = new Signature(name, parameters);
        var methods = CACHE.get(owner);
        Method cached = methods.get(key);
        if (cached != null) return cached;

        Method discovered = owner.getMethod(name, parameters);
        Method prior = methods.putIfAbsent(key, discovered);
        return prior == null ? discovered : prior;
    }

    public static void main(String[] args) throws Exception {
        Method one = publicMethod(String.class, "substring", int.class);
        Method again = publicMethod(String.class, "substring", int.class);
        Method two = publicMethod(
                String.class, "substring", int.class, int.class);

        System.out.println("Same cached method: " + (one == again));
        System.out.println("Overloads distinct: " + (one != two));
        System.out.println("First call: " + one.invoke("abcd", 2));
        System.out.println("Second call: " + two.invoke("abcd", 1, 3));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ClassValueDemo.java
java ClassValueDemo
~~~

Output đã kiểm tra:

~~~text
Same cached method: true
Overloads distinct: true
First call: cd
Second call: bc
~~~

`Signature` là record nên có so sánh theo giá trị; `List.of(parameters)` tạo danh sách bất biến chứa tham số theo đúng thứ tự thay vì giữ trực tiếp mảng `Class<?>...` có thể bị caller sửa. `ConcurrentHashMap` cho phép các thread truy cập cùng metadata class an toàn theo thao tác map.

Ví dụ cố ý chỉ cache **method public** qua `getMethod`. Nếu method vắng mặt, nó ném `NoSuchMethodException` và không lưu “không tồn tại”. Với hệ thống cần cache cả *negative lookup*, hãy dùng kiểu kết quả riêng (sentinel/`Optional` hoặc object chứa trạng thái) và xác định chính sách invalidation cẩn thận. `ConcurrentHashMap` không cho key hoặc value `null`.

Về concurrency, ví dụ dùng `get` → tra cứu Reflection → `putIfAbsent`. Hai thread có thể cùng tra cứu method một lần trong tình huống cạnh tranh, nhưng map giữ một giá trị cuối; điều này phù hợp vì tra cứu method là thao tác không có side effect nghiệp vụ.

## 7. Khi phải cache bằng tên trước khi có `Class<?>`

Một resolver nhận:

~~~java
Class<?> resolve(String binaryName, ClassLoader requestedLoader)
        throws ClassNotFoundException {
    return Class.forName(binaryName, false, requestedLoader);
}
~~~

là **fragment đầy đủ của hàm** để đặt trong class. `Class.forName(..., false, requestedLoader)` yêu cầu tra cứu mà **không chủ động khởi tạo lớp**. Đối số loader là loader dùng để yêu cầu phân giải, không bảo đảm đó là defining loader của kết quả.

Nếu cache kết quả `resolve`, khóa phải bao gồm tối thiểu **`binaryName` và `requestedLoader` theo identity**, hoặc một namespace/version context có hợp đồng tương đương. Hãy phân biệt:

- `Map<String, Class<?>>` **sai** nếu cùng tên có thể được giải bằng nhiều loader.
- `Map<Key(String, ClassLoader), Class<?>>` có thể đúng về phân biệt request, với điều kiện `ClassLoader` được so sánh theo **định danh thực sự của instance** (identity). `record` dùng `equals` của loader; nếu có loader tùy biến ghi đè `equals`, hãy chọn wrapper identity hoặc cấu trúc phù hợp.
- Nếu đã có `Class<?> resolved`, cache metadata theo **`resolved`**, không cần tính lại key tên + loader.
- Không cache `ClassNotFoundException` vĩnh viễn theo một loader có thể tìm thêm class sau khi thêm JAR/plugin; negative lookup phải có chính sách làm mới tương ứng.

**Tránh dùng** `requestedLoader.getClass().getName()` làm loader id; nó chỉ nói **loại loader**, không phải instance nào. Cũng không dùng mỗi `System.identityHashCode(loader)` để làm định danh duy nhất trong cache lâu dài: mã hash có thể trùng và bản thân hash không giữ được tham chiếu chính xác tới loader.

Với nền tảng có nhiều `ModuleLayer`, resolver có thể phụ thuộc module/layer bên cạnh loader. Định nghĩa khóa theo **đúng các đầu vào của phép resolve đang dùng**, thay vì giả định mỗi `binaryName` có một class toàn cục.

## 8. Vòng đời cache và nguy cơ giữ class loader

Một khóa metadata theo `Class<?>` có thể **đúng về identity** nhưng cache global như `static ConcurrentHashMap<Class<?>, Method>` lại sống lâu hơn plugin. Map giữ mạnh cả key `Class` lẫn `Method` trỏ về declaring class, khiến loader cũ có thể chưa được thu hồi sau khi gỡ plugin. Đổi sang `WeakHashMap<Class<?>, Method>` không tự giải quyết vòng giữ từ value về key.

Với metadata tính riêng cho mỗi class, `ClassValue` thường phù hợp; với plugin có lifecycle rõ, cache nằm trong plugin hoặc có thao tác `evict` khi unload cũng là lựa chọn hợp lý. Những nguồn giữ khác như callback, thread, `ThreadLocal` hay handle phải được dọn theo lifecycle; **GC và class unloading không có hạn chót được bảo đảm**. Bài “Cache giữ Class có thể giữ cả class loader” phân tích reference graph, weak keys, heap dump và kiểm tra GC roots cụ thể.

## 9. Reload, dynamic proxy, hidden class và thay đổi định nghĩa

**Reload plugin:** phiên bản mới cùng binary name thường xuất hiện dưới **loader mới**, tức `Class<?>` mới. Cache theo class tự phân biệt phiên bản cũ/mới; cache theo tên có thể trả metadata cũ.

**Dynamic proxy:** proxy class sinh ra phụ thuộc loader, tập interface và ngữ cảnh module. Dùng tên hiển thị hoặc tên proxy class sinh ra làm định danh bền vững là rủi ro. Nếu cache phản chiếu trên proxy, dùng `proxy.getClass()` hoặc một khóa dựa trên cấu hình proxy đã chuẩn hóa theo đúng loader/interface identity.

**Hidden class:** class có thể được tạo bằng `MethodHandles.Lookup.defineHiddenClass`; nó không nhất thiết được định danh/tra cứu lại bằng `Class.forName(name)` theo cách thông thường. Nếu cần cache metadata, khóa bằng chính `Class<?>` đã được cấp.

**Instrumentation/redefinition:** các API như `Instrumentation.redefineClasses` có thể thay bytecode của **cùng class runtime** trong giới hạn JVM cho phép. Khi đó **identity của `Class<?>` không đổi**, nhưng metadata/behavior có thể thay đổi theo thao tác công cụ. Cache theo `Class<?>` không tự biết phiên bản bytecode đã đổi: cơ chế instrumentation/framework phải có **invalidation hoặc generation/version** phù hợp nếu dữ liệu cache phụ thuộc nội dung có thể thay đổi.

**Class loader delegation:** hai loader khác nhau có thể cùng ủy quyền nạp về parent và nhận **chính một `Class<?>`**. Trong trường hợp đó cache theo kết quả `Class<?>` cho metadata có thể chia sẻ an toàn theo identity. Không suy đoán hai kiểu khác nhau chỉ vì hai lời gọi `loadClass` bắt đầu từ hai loader khác nhau.

## 10. Quy tắc vận hành để cache Reflection an toàn

### 10.1. Tách metadata khỏi quyền truy cập

Một `Method` có thể tồn tại nhưng không truy cập được từ một module/caller cụ thể. `setAccessible(true)` vẫn chịu ràng buộc `opens`; `MethodHandles.Lookup` mang quyền của caller hoặc lookup được cấp. Nếu cache **handle có đặc quyền** hay dữ liệu phụ thuộc caller, khóa và cách chia sẻ phải phản ánh ranh giới quyền, không phát tán handle/lookup cho đối tượng không tin cậy.

### 10.2. Thread safety không đồng nghĩa key correctness

`ConcurrentHashMap` giúp các thao tác map nhất quán giữa thread, **không biến `String className` thành định danh class an toàn**. Phải sửa thiết kế khóa trước khi bàn tối ưu lock hay compute-if-absent.

Với `computeIfAbsent`, hàm tính toán có thể ném checked exception khiến bạn phải quy đổi/wrap rồi xử lý rõ ràng; không cache một ngoại lệ ở dạng thành công. Cân nhắc thất bại tạm thời, thay đổi plugin, quyền module và cơ chế invalidation trước khi dùng negative cache.

### 10.3. Đừng tối ưu khi chưa đo

Reflection lookup lặp lại có thể tốn chi phí nhưng tạo cache cũng có giá thành: lookup khóa, bộ nhớ, giữ loader, đồng bộ và invalidation. Dùng benchmark phù hợp (ví dụ JMH với warm-up) để quyết định cache gì. Nếu đường gọi nóng đã có `MethodHandle`/`VarHandle` ổn định và được cấp đúng quyền, đôi khi lưu handle trong phạm vi lifecycle rõ ràng tốt hơn cache `Method` toàn cục.

### 10.4. Checklist review

- Cache đang lưu **kết quả tra tên** hay metadata của **`Class<?>` cụ thể**?
- Nếu key là `String`, lớp ấy có thể được định nghĩa từ bao nhiêu loader/plugin?
- Nếu key là `Class<?>`, có đang vô tình giữ loader cũ qua global strong map?
- Key của method có phân biệt overload, kiểu tham số theo thứ tự, static/instance và `getMethod` với `getDeclaredMethod`?
- Nếu có quyền truy cập khác nhau theo caller/module, liệu cache có vô tình chia sẻ handle đặc quyền?
- Khi reload/unload hoặc redefine, entry nào phải bị loại bỏ?
- Có test hai loader **cùng nạp một binary name** hay mới chỉ test một loader mặc định?

## 11. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân điển hình | Cách xử lý |
| --- | --- | --- |
| `Method.invoke` báo `IllegalArgumentException` dù “đúng class name” | `Method` thuộc class do loader khác định nghĩa | So sánh `method.getDeclaringClass()` và `receiver.getClass()`, cả loader |
| `ClassCastException` với hai lớp cùng tên | Hai defining loader tạo hai runtime type | Dùng chung API/interface do loader phù hợp chia sẻ, không ép theo tên |
| `NoSuchMethodException` sau deploy phiên bản mới | Cache cũ, signature khác, overload sai | Tra đúng `Class<?>` mới và đúng kiểu tham số |
| `LinkageError` hoặc loader constraint violation | Loading/linking không tương thích giữa các class loader | Rà soát delegation và dependency giữa loader |
| Loader plugin không được unload | Cache tĩnh giữ `Class`, `Method`, handle hoặc instance | Dùng `ClassValue`/cache theo vòng đời và dọn các reference |
| Cache name + loader class vẫn va chạm | Dùng **loại loader**, không phải instance loader | Dùng class đã resolve hoặc loader object identity |
| Cùng loader nạp class qua parent nhưng cache bị nhân đôi | Khóa dùng initiating loader khi chỉ cache class metadata | Cache theo `Class<?>` kết quả để gộp được cùng identity |
| Cache không đổi sau redefine cùng `Class` | Không có invalidation theo phiên bản | Gắn vòng đời cache với generation/redefine event |
| Cache map thread-safe nhưng trả method sai | Đồng bộ đúng, định danh sai | Thiết kế lại key trước khi tối ưu đồng bộ |

## 12. Tự kiểm tra

1. Hai `Class<?>` có `getName()` bằng nhau nhưng defining loader khác nhau. Có được dùng chung `Method` không? **Không**; đó là hai runtime type riêng.
2. Cache `Map<Class<?>, Metadata>` đã phân biệt loader chưa? **Có**; `Class<?>` mang định danh runtime bao gồm defining loader.
3. Nếu cache `Class.forName(name, false, requestedLoader)` trước khi biết kết quả, có thể chỉ dùng `name` không? **Không**, nếu nhiều loader/ngữ cảnh có thể phân giải khác nhau.
4. `requestedLoader` và `resolvedClass.getClassLoader()` có luôn giống nhau không? **Không**, vì parent delegation.
5. Vì sao `WeakHashMap<Class<?>, Method>` chưa chắc cho loader unload? **Method value có thể giữ declaring class**, tạo đường tham chiếu mạnh từ map value đến key.
6. `ClassValue.computeValue` có chắc chỉ chạy đúng một lần trong cạnh tranh không? **Không**; nhiều thread có thể cùng tính, một giá trị được cài đặt.
7. Sau khi redefine bytecode của một class mà giữ nguyên `Class<?>`, key theo `Class<?>` đã tự invalid cache chưa? **Chưa**; cần generation/invalidation nếu metadata ảnh hưởng.
8. Với `getMethod("substring", int.class)` và `getMethod("substring", int.class, int.class)`, cache key nên khác nhau ở đâu? **Danh sách kiểu tham số**.

## 13. Tài liệu chính thức

- [Java Virtual Machine Specification 17 — Creation and Loading, `5.3](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — ClassValue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassValue.html)
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java SE 17 — WeakHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html)

**Ghi nhớ:** Đối với metadata Reflection, **khóa bằng chính `Class<?>`** (hoặc dùng `ClassValue`) để phân biệt cả defining loader. Chỉ khi cache **phân giải tên trước khi có class** mới cần giữ tên cùng initiating loader/ngữ cảnh; sau khi có class, đừng quay lại khóa bằng tên. Cân nhắc vòng đời plugin và strong references để một cache đúng về mặt ngữ nghĩa không trở thành nguyên nhân memory leak.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-004">ClassValue cho dữ liệu liên kết với từng Class</a>

<details>
<summary>Xem chi tiết</summary>

# ClassValue: lưu dữ liệu tính toán riêng cho từng Class trong Java

## 1. Bài toán: cache metadata gắn với runtime class

Với Java Reflection, các thao tác như `getDeclaredMethods()`, `getDeclaredFields()`, đọc annotation hoặc phân tích cấu trúc lớp có thể lặp lại rất nhiều lần. Nếu mỗi request đều dựng lại danh sách metadata và bảng ánh xạ từ tên method sang `Method`, chương trình tốn công tính toán và cấp phát bộ nhớ.

Một lựa chọn thường gặp là tạo `Map<Class<?>, Metadata>`:

```java
// Ví dụ minh họa, chưa phải lời giải đầy đủ:
// private static final Map<Class<?>, Metadata> CACHE = ...
Metadata metadata = CACHE.get(type);
```

Tuy nhiên, với map dùng **strong key** tồn tại suốt vòng đời ứng dụng, khóa `Class<?>` có thể giữ tham chiếu tới class và class loader của plugin/module mà chương trình đã ngừng sử dụng. Việc đồng bộ hóa tính toán, tránh xây dữ liệu nhiều lần và xử lý class unloading cũng trở thành trách nhiệm riêng của người viết cache.

**`java.lang.ClassValue<T>`** được thiết kế cho trường hợp **một giá trị kiểu `T` gắn với từng đối tượng `Class<?>`**. Chúng ta mô tả cách tính giá trị bằng `computeValue(Class<?> type)` rồi lấy nó bằng `get(type)`. JVM/JDK quản lý mối liên kết từ mỗi `Class` tới giá trị tương ứng của từng `ClassValue`.

Ví dụ ứng dụng:

- Cache danh sách method hoặc field của mỗi class phục vụ cơ chế serialize/binding/dispatch.
- Lưu metadata đã phân tích từ annotation của từng kiểu.
- Lưu codec, descriptor hoặc thông tin cấu hình được suy ra **chỉ từ class**, không từ object cụ thể.
- Giảm số lần phân tích Reflection trong thư viện/framework có thể gặp nhiều class và class loader khác nhau.

`ClassValue` không tạo ra field thật trong bytecode của class, không thay đổi `Class<?>`, không tự gọi method nghiệp vụ và không phải cơ chế lưu trạng thái **riêng cho từng object instance**.

## 2. API cốt lõi và phạm vi khóa

Phác thảo chữ ký các thành viên quan trọng của `ClassValue<T>`:

```java
public abstract class ClassValue<T> {
    protected ClassValue();
    protected abstract T computeValue(Class<?> type);
    public T get(Class<?> type);
    public void remove(Class<?> type);
}
```

Đây là phần **chữ ký minh họa**, không phải một class người học tự định nghĩa; sử dụng implementation của JDK qua `java.lang.ClassValue`.

| API | Vai trò | Lưu ý |
|---|---|---|
| `new ClassValue<T>() { ... }` | Tạo một đối tượng cache với hàm tính giá trị cụ thể | Phải override `computeValue` |
| `computeValue(Class<?> type)` | Tính dữ liệu ứng với một class khi cần | Có thể được gọi hơn một lần cho cùng class trong tình huống cạnh tranh |
| `get(Class<?> type)` | Trả giá trị hiện gắn với cặp `(ClassValue instance, Class<?>)`; khi chưa có sẽ yêu cầu tính | Không cần tự quản lý `Map<Class<?>,T>` |
| `remove(Class<?> type)` | Loại giá trị đang gắn cho class cụ thể để lần đọc tiếp theo có thể tính lại | Chỉ ảnh hưởng **một class trong một ClassValue**; không có thao tác xóa toàn bộ cache tương đương `Map.clear()` |

Ví dụ gọi:

```java
ClassValue<String> simpleNames = new ClassValue<>() {
    @Override
    protected String computeValue(Class<?> type) {
        return type.getSimpleName();
    }
};

System.out.println(simpleNames.get(String.class)); // String
System.out.println(simpleNames.get(Integer.class)); // Integer
```

Đây là đoạn chạy được khi đặt vào thân một phương thức Java; không cần tạo instance `String` hoặc `Integer` vì khóa là **runtime class**, không phải object.

### Cặp khóa thực tế gồm hai identity

Hãy hình dung một bảng tra cứu logic:

```text
(ClassValue A, Alpha.class) --> giá trị A/Alpha
(ClassValue A, Beta.class)  --> giá trị A/Beta
(ClassValue B, Alpha.class) --> giá trị B/Alpha
```

Mỗi instance `ClassValue` có một không gian giá trị độc lập. `get(Alpha.class)` ở `ClassValue A` không dùng chung kết quả với cùng lời gọi ở `ClassValue B`. Đồng thời, hai đối tượng của cùng một class `Alpha` đều có chung **class key** nếu lấy metadata qua cùng một `ClassValue`.

## 3. Vòng đời: tính lười, đọc lại, xóa rồi tính lại

Ở luồng đơn, cơ chế cơ bản là:

1. Khởi tạo instance `ClassValue<T>` cùng cài đặt `computeValue`.
2. `get(Alpha.class)` lần đầu chưa có dữ liệu: gọi `computeValue(Alpha.class)` để tạo ứng viên giá trị và gắn giá trị được chọn.
3. `get(Alpha.class)` lần sau, nếu dữ liệu vẫn còn hợp lệ: trả lại giá trị cache; không cần phân tích Reflection lần nữa.
4. `get(Beta.class)` là khóa class khác: tính giá trị riêng cho `Beta`.
5. `remove(Alpha.class)` loại bỏ liên kết hiện có của `Alpha` trong **chính** instance `ClassValue` đó.
6. `get(Alpha.class)` sau khi xóa sẽ có thể chạy `computeValue` và cài đặt giá trị mới; giá trị của `Beta` không bị xóa.

```text
                        ClassValue<List<String>>
                                 |
                 +---------------+---------------+
                 |                               |
             Alpha.class                     Beta.class
                 |                               |
        computeValue(Alpha)              computeValue(Beta)
                 |                               |
          [hello, reset]                        [size]
                 |                               |
       get(Alpha) dùng lại               get(Beta) dùng lại
                 |
          remove(Alpha)
                 |
         get(Alpha) tính lại
```

Điểm khác `Map.computeIfAbsent` là `ClassValue` có cách gắn dữ liệu gắn liền với đối tượng `Class` và được thiết kế cho vòng đời class/class loader. Nó không cung cấp các thao tác map như `entrySet()`, `keySet()`, `put()` hay `clear()`.

## 4. Ví dụ Java 17+ chạy độc lập, kết quả chính xác

Tạo file **`ClassValueDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassValueDemo {
    static class Alpha {
        public void hello() {}
        private void reset() {}
    }

    static class Beta {
        public int size() { return 42; }
    }

    private static final AtomicInteger COMPUTES = new AtomicInteger();

    private static final ClassValue<List<String>> METHOD_NAMES = new ClassValue<>() {
        @Override
        protected List<String> computeValue(Class<?> type) {
            COMPUTES.incrementAndGet();
            return Arrays.stream(type.getDeclaredMethods())
                    .map(Method::getName)
                    .sorted()
                    .toList();
        }
    };

    public static void main(String[] args) {
        List<String> first = METHOD_NAMES.get(Alpha.class);
        System.out.println("Alpha: " + first);

        List<String> again = METHOD_NAMES.get(Alpha.class);
        System.out.println("Alpha reused: " + (first == again));

        System.out.println("Beta: " + METHOD_NAMES.get(Beta.class));
        System.out.println("computeValue calls: " + COMPUTES.get());

        METHOD_NAMES.remove(Alpha.class);
        List<String> refreshed = METHOD_NAMES.get(Alpha.class);
        System.out.println("Alpha refreshed: " + refreshed);
        System.out.println("same list after remove: " + (first == refreshed));
        System.out.println("computeValue calls after remove: " + COMPUTES.get());

        ClassValue<String> CLASS_NAMES = new ClassValue<>() {
            @Override
            protected String computeValue(Class<?> type) {
                return type.getSimpleName();
            }
        };
        System.out.println("another ClassValue: " + CLASS_NAMES.get(Alpha.class));
    }
}
```

Biên dịch bằng Java 17 trở lên (cờ `--release 17` xác nhận mã chỉ cần các API có trong Java 17):

```bash
javac --release 17 ClassValueDemo.java
java ClassValueDemo
```

Stdout xác minh:

```text
Alpha: [hello, reset]
Alpha reused: true
Beta: [size]
computeValue calls: 2
Alpha refreshed: [hello, reset]
same list after remove: false
computeValue calls after remove: 3
another ClassValue: Alpha
```

**Phân tích từng dòng:**

- `computeValue` nhận đúng `Class<?>` được yêu cầu và gọi `getDeclaredMethods()` để lấy các method khai báo trực tiếp (gồm private method). Kết quả được sắp xếp tên bằng `sorted()` trước khi in; không phụ thuộc thứ tự Reflection vốn không được đảm bảo.
- Hai lần `METHOD_NAMES.get(Alpha.class)` liên tiếp trả **cùng tham chiếu `List`** trong ví dụ đơn luồng, nên phép so sánh `first == again` là `true`.
- Khóa `Beta.class` khác khóa `Alpha.class` nên tính thêm một lần. Tổng số lời gọi `computeValue` lúc đó là `2`.
- Sau `remove(Alpha.class)`, `get(Alpha.class)` tính danh sách mới. Nội dung có thể bằng nhau, nhưng đây là **hai instance list khác nhau**, nên kết quả `first == refreshed` là `false`.
- Lần tính lại tăng bộ đếm từ `2` lên `3`; `Beta` vẫn có giá trị của nó.
- `CLASS_NAMES` là instance `ClassValue` khác, quản lý chuỗi tên lớp; nó không chia sẻ nội dung với `METHOD_NAMES` dù cùng khóa `Alpha.class`.

Ví dụ cố tình chạy một luồng. **Không sử dụng tổng số lần `computeValue` của ví dụ này làm cam kết rằng trong chương trình nhiều luồng mỗi class luôn chỉ tính đúng một lần.** Phần tiếp theo giải thích khác biệt.

## 5. Đồng thời: không mặc định computeValue chạy đúng một lần

Khi nhiều thread cùng gọi `get(type)` lúc cache chưa có giá trị, **có thể có nhiều lần chạy `computeValue(type)` đồng thời**. JDK chỉ cài đặt/giữ một giá trị được chọn cho class tại một thời điểm cache ổn định; một số kết quả tính toán cạnh tranh có thể bị bỏ đi. Vì vậy, `ClassValue` loại bớt nhu cầu quản lý khóa cho phép tra cứu cache, nhưng **không biến `computeValue` thành “exactly once initializer”**.

Từ đó có các nguyên tắc:

- Làm `computeValue` **độc lập, xác định được** theo `type` và lý tưởng là không có side effect. Việc scan metadata và dựng một descriptor mới phù hợp hơn việc tạo kết nối, ghi file hoặc cộng tiền.
- Nếu `computeValue` có side effect hoặc tài nguyên ngoại vi, không dựa vào `ClassValue` để đảm bảo chỉ thực hiện một lần. Phải dùng cơ chế quản lý tài nguyên/idempotency riêng.
- Nếu cache trả một đối tượng **có thể thay đổi**, tính an toàn thread của chính đối tượng đó vẫn là trách nhiệm ứng dụng. `ClassValue` không tự biến `HashMap` được cache thành thread-safe.
- Với metadata bất biến, ưu tiên `List.copyOf(...)`, `Map.copyOf(...)` hoặc immutable record/value object. Ví dụ `Stream.toList()` từ Java 16 cho một danh sách không thể sửa bằng `add/remove/set`.
- Nếu có nhu cầu tính toán cực nặng và nhiều thread cùng tranh chấp một class, đo đạc và thiết kế chiến lược đồng bộ ở tầng tính toán riêng; không suy từ thao tác `get` rằng phép tính chỉ chạy một lần.

Khi `computeValue` ném exception, `get` đang thực hiện tính toán có thể ném theo exception đó; đừng coi kết quả lỗi là một descriptor hợp lệ đã được cache. Hãy xác định chính sách retry/diagnostics thay vì che giấu lỗi bằng giá trị placeholder không chính xác.

**Không gọi lại `sameClassValue.get(type)` trên chính khóa đó từ bên trong `computeValue(type)`.** Đây là cách rất dễ tạo đệ quy vô hạn hoặc vòng phụ thuộc, vì kết quả cho khóa đó còn chưa được xác lập.

## 6. Class identity, class loader và giải phóng class

Ở JVM, identity của một class phụ thuộc **binary name và defining class loader**. Có thể có hai class khác nhau cùng tên như `example.Plugin`, được định nghĩa bởi hai class loader khác nhau. Khi đó:

```text
Loader 1 -> example.Plugin (Class object A)
Loader 2 -> example.Plugin (Class object B)
```

`ClassValue.get(Class object A)` và `ClassValue.get(Class object B)` dùng **hai khóa khác nhau**. Điều này cần thiết cho Reflection: methods, fields, annotations và module access trên hai class có thể khác nhau dù `getName()` giống nhau.

**Vì sao có ích cho môi trường plugin/redeploy?**

Một cấu trúc như `static ConcurrentHashMap<Class<?>, Metadata>` có **khóa mạnh**. Khi map sống lâu và chưa xóa entry, nó có thể giữ class và class loader của plugin cũ không được thu gom. `ClassValue` được thiết kế để gắn dữ liệu với lifecycle của `Class` mà không cần đặt mọi đối tượng `Class` làm key mạnh trong một map toàn cục do ứng dụng quản lý.

Điều này **không bảo đảm class unloading sẽ xảy ra**: nếu ứng dụng/framework vẫn giữ tham chiếu mạnh tới class, class loader, object, thread hay metadata, chúng vẫn có thể ngăn giải phóng. Một `ClassValue` có giá trị liên kết với class không thay thế được việc đóng resource, dừng thread hoặc xóa các cache khác vốn giữ class loader.

Không tự tạo key bằng `type.getName()` để thay cho `Class<?>`: cùng tên nhưng loader khác có thể đại diện hai runtime type khác nhau. Khi cache metadata Reflection, việc phân biệt đúng identity là yêu cầu về **tính đúng đắn**, không chỉ về bộ nhớ.

## 7. Gắn dữ liệu Reflection có cấu trúc với từng class

`ClassValue` thường chứa **metadata đã được phân tích**, không chỉ danh sách tên method. Ví dụ cache các `Method` được khai báo trực tiếp của một class:

```java
// Mảnh code minh họa đặt trong class có sử dụng Reflection.
private static final ClassValue<List<java.lang.reflect.Method>> METHODS =
        new ClassValue<>() {
            @Override
            protected List<java.lang.reflect.Method> computeValue(Class<?> type) {
                return java.util.Arrays.stream(type.getDeclaredMethods())
                        .sorted(java.util.Comparator.comparing(
                                java.lang.reflect.Method::toGenericString))
                        .toList();
            }
        };
```

`METHODS.get(SomeType.class)` trả danh sách `Method` gắn với `SomeType.class`. Đoạn này chỉ định nghĩa field/cache; để chạy độc lập cần class bao quanh có `SomeType`. Mọi phép chọn overload phải dựa vào **tên và parameter types** thật sự: không lấy đại phần tử đầu tiên cùng tên, vì class có thể chứa nhiều overload, bridge hoặc synthetic method.

Một logic tra cứu mảnh:

```java
// Giả định biến 'type' là Class<?> và 'methodName' là String.
for (java.lang.reflect.Method method : METHODS.get(type)) {
    if (method.getName().equals(methodName) &&
            java.util.Arrays.equals(
                    method.getParameterTypes(),
                    new Class<?>[]{String.class})) {
        // Tìm được method nhận chính xác một String.
        // Cần kiểm tra access trước khi invoke.
        break;
    }
}
```

**Lưu ý Reflection:** caching một `Method` không đồng nghĩa method đó gọi được. `getDeclaredMethods()` có thể trả method private; `Method.invoke` vẫn phải tuân thủ visibility và ranh giới module. `setAccessible(true)` cũng có thể bị từ chối nếu module/package không mở phù hợp. Cache metadata **không tạo thêm quyền truy cập**.

Nếu kết quả phụ thuộc vào những tham số khác ngoài class, chẳng hạn schema version, locale, quyền người dùng hoặc cấu hình tenant, hãy thiết kế key/value thích hợp. Chỉ dùng `ClassValue` khi dữ liệu được gắn với class; không nhét trạng thái người dùng có vòng đời ngắn vào một giá trị dùng chung giữa các request.

## 8. Làm mới cache, thay đổi cấu hình và remove()

`ClassValue.remove(Class<?> type)` xóa liên kết cache cho **một class** trong instance `ClassValue` cụ thể. Phương thức trả về `void`; nó không trả giá trị cũ và không phải thao tác xóa cả cache.

Ví dụ:

```java
// Nếu metadata được tính bởi CACHED_METADATA:
CACHED_METADATA.remove(type);
// Lần get(type) tiếp theo có thể tính lại:
Metadata refreshed = CACHED_METADATA.get(type);
```

Đây là **mảnh code minh họa**, trong đó `CACHED_METADATA`, `type` và `Metadata` do ứng dụng định nghĩa. Gọi `remove` hợp lý khi chính sách cache nhận biết dữ liệu cần tính lại. Với nhiều luồng, phải xem các guarantee của ứng dụng về việc đồng bộ cập nhật và truy vấn; các lời gọi `get` đang diễn ra có thể cạnh tranh với `remove`. Không coi `remove` là rào chắn giao dịch đảm bảo toàn bộ thread lập tức dùng chung một version dữ liệu mới.

Nếu metadata là hàm của **cấu hình thay đổi trong runtime**, một bản cache được tạo từ cấu hình cũ có thể lỗi thời. Có ba lựa chọn thiết kế cần cân nhắc:

1. Bảo đảm dữ liệu chỉ phụ thuộc vào class và bất biến trong suốt vòng đời liên kết, khi đó `ClassValue` phù hợp trực tiếp.
2. Gọi `remove` cho những class cần làm mới khi biết có cập nhật, đồng thời dùng cơ chế đồng bộ ở tầng ứng dụng để quản lý lần đọc/ghi cạnh tranh.
3. Nếu config/version thay đổi độc lập với class và cần nhiều version cùng lúc, tổ chức **giá trị bên trong** thành map/versioned descriptor hoặc chọn cache với composite key. `ClassValue` đơn lẻ chỉ có một chiều khóa `Class<?>`.

Không có API để duyệt tất cả `Class<?>` hiện được lưu và gọi `remove` hàng loạt trên một `ClassValue`. Nếu cần quản lý từng tập khóa, ứng dụng phải có registry riêng, cùng cân nhắc khả năng giữ class loader sống lâu ngoài ý muốn.

## 9. So sánh lựa chọn cache

| Lựa chọn | Khi thích hợp | Điểm cần cẩn thận |
|---|---|---|
| `ClassValue<T>` | Một giá trị tính lười trên mỗi runtime `Class<?>`, chẳng hạn metadata Reflection | Không có API duyệt key, không bảo đảm `computeValue` chỉ chạy một lần dưới concurrency |
| `ConcurrentHashMap<Class<?>, T>` | Cần duyệt key, eviction tùy ý, cache policy cụ thể; class set có vòng đời được kiểm soát | Khóa mạnh có thể giữ class/class loader; phải dọn dẹp và thiết kế compute policy |
| `WeakHashMap<Class<?>, T>` | Cần key yếu với chính sách synchronization do ứng dụng quản lý | Không thread-safe mặc định; value tham chiếu ngược tới class có thể vô hiệu hóa lợi ích weak key |
| Tính lại bằng Reflection mỗi lần | Gọi hiếm, metadata nhỏ, chưa có bằng chứng bottleneck | Trả chi phí tra cứu/khởi tạo dữ liệu mỗi lần |
| Cache gắn với instance object | Kết quả phụ thuộc trạng thái từng object | Không phải cache **theo Class**; `ClassValue` sẽ chia sẻ theo runtime class |

`ClassValue` thường là lựa chọn gọn cho thư viện phân tích type metadata trong môi trường nhiều class loader. Nhưng không mặc định rằng nó nhanh hơn mọi map trong mọi workload. Hãy đo bằng benchmark tương ứng với số class, số lần đọc, mức độ tranh chấp, chi phí `computeValue` và yêu cầu giải phóng lớp.

## 10. Lỗi thiết kế thường gặp

**1. Nhầm mỗi class với mỗi object.** Nếu hai object `a` và `b` có cùng `a.getClass() == b.getClass()`, cùng `ClassValue` sẽ trả cùng giá trị đã gắn cho class đó. Không đặt data per-request/per-instance vào đây.

**2. Nghĩ computeValue chạy đúng một lần.** Dưới contention, có thể có nhiều computation ứng viên. Side effects cần được kiểm soát độc lập.

**3. Giữ dữ liệu mutable không an toàn.** Cache trả `ArrayList` hoặc `HashMap` bị sửa từ nhiều request sẽ gây race/corruption. Dựng immutable snapshot hoặc dùng cấu trúc concurrent có chủ đích.

**4. Tính dữ liệu theo trạng thái bên ngoài nhưng không invalidation.** Giá trị có thể lỗi thời khi config, feature flags, schema hoặc plugin registration thay đổi. Xác định rõ chính sách cập nhật.

**5. Gọi `get` đệ quy cho cùng khóa từ `computeValue`.** Kết quả chưa được cache; có thể dẫn tới vòng lặp/`StackOverflowError`.

**6. Giả định remove xóa “mọi class”.** `remove(type)` chỉ xóa entry của cặp `(ClassValue instance, type)`, không tác động giá trị của class hoặc `ClassValue` khác.

**7. Cache metadata để “né” quy tắc accessibility.** `ClassValue` không thay đổi `private`, module openness hoặc các exception khi invoke.

**8. Dùng tên lớp string làm identity.** Class loader khác nhau có thể định nghĩa hai kiểu cùng tên; cache từ `getName()` đơn thuần dễ trả sai metadata.

**9. Xem cache là giải pháp cho memory leak từ mọi nguồn.** ClassValue không tự dừng thread/plugin loader còn bị giữ bởi cache khác, thread-local hay static reference.

## 11. Câu hỏi tự kiểm tra

1. Hai lần `cache.get(String.class)` liên tiếp trong một luồng, chưa xóa cache, có phải chạy `computeValue` lần hai không? — Thông thường **không**, vì giá trị đã được gắn và tái sử dụng.
2. Hai instance `ClassValue` khác nhau cùng gọi `get(String.class)` có chia sẻ giá trị không? — **Không**; mỗi `ClassValue` quản lý liên kết riêng.
3. Có thể mặc định `computeValue` chạy chính xác một lần cho mỗi class trong hệ nhiều thread không? — **Không**; nhiều computation cạnh tranh có thể xảy ra.
4. `remove(Alpha.class)` có làm mất entry `Beta.class` không? — **Không**, nếu đó là hai class khác nhau trong cùng `ClassValue`.
5. Hai plugin định nghĩa hai `Class` cùng binary name dưới hai defining loader khác nhau có dùng chung key không? — **Không**; runtime class identity khác nhau.
6. `ClassValue<List<Method>>` có cho phép gọi mọi private method của lớp đó không? — **Không**; vẫn phải tuân thủ Java access control và module boundaries.

## 12. Ghi nhớ

- **`ClassValue<T>`** gắn dữ liệu tính lười với từng `Class<?>` và từng instance `ClassValue`; phù hợp để cache metadata Reflection.
- **`get` tái sử dụng giá trị**, `computeValue` tạo giá trị khi cần, còn `remove(type)` yêu cầu bỏ liên kết cho đúng class đó.
- **Thread-safe ở cơ chế ánh xạ không có nghĩa compute exactly once**, cũng không làm các đối tượng mutable được cache trở nên thread-safe.
- **Class loader là phần của identity**: ClassValue phân biệt đúng những class cùng tên do loader khác nhau định nghĩa và hỗ trợ kiến trúc tương thích class unloading.
- **Cache không thay thế thiết kế vòng đời, invalidation hay bảo mật truy cập**: chỉ cache dữ liệu đúng scope và xử lý thay đổi cấu hình, resource và quyền truy cập tại tầng phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-005">Cache giữ Class có thể giữ cả class loader</a>

<details>
<summary>Xem chi tiết</summary>

# Cache giữ `Class` có thể giữ cả class loader: vòng đời metadata Reflection trong Java

## 1. Vấn đề: cache giúp nhanh hơn nhưng có thể giữ plugin trong bộ nhớ

Một framework dùng Reflection thường phải tra cứu đi tra cứu lại cùng một lớp: danh sách field, method, constructor, annotation, bộ ánh xạ dữ liệu, `MethodHandle` hoặc cấu hình serialization. Cache các kết quả đó giúp giảm chi phí khám phá metadata và xây dựng adapter.

Tuy nhiên, trong ứng dụng **nạp và gỡ plugin**, môi trường triển khai lại module, application server hoặc chương trình tạo nhiều class loader động, **cache sống lâu hơn class loader** có thể làm các lớp cũ không được thu hồi. Ví dụ:

~~~java
// Minh họa nguy cơ: map tĩnh sống theo toàn bộ ứng dụng.
static final Map<Class<?>, Metadata> CACHE = new ConcurrentHashMap<>();
~~~

Nếu `Class<?>` trong map là một lớp được nạp bởi loader của plugin, entry được giữ bằng tham chiếu mạnh (*strong reference*). Trong tình huống thông thường, chuỗi tham chiếu này có thể giữ `Class` và class loader định nghĩa nó còn có thể truy cập được, do đó chặn việc thu hồi class loader và các lớp do loader đó quản lý.

**Bản chất lỗi là lệch vòng đời:** thành phần cache của ứng dụng tồn tại lâu dài, trong khi plugin và các lớp do plugin nạp phải có thể được giải phóng sau khi gỡ. Điều này không có nghĩa mọi cache `Class` đều gây lỗi. Nếu mọi class tồn tại cùng vòng đời ứng dụng, cache hữu hạn có thể hoàn toàn hợp lý. Vấn đề xuất hiện khi cache **sống lâu hơn namespace nạp lớp** mà nó giữ.

Sau bài này, bạn sẽ biết cách nhận diện đường giữ tham chiếu, lựa chọn cache có phạm vi phù hợp, sử dụng `ClassValue` và weak-key cache đúng cách, kiểm chứng bằng công cụ chẩn đoán, và tránh một số sửa chữa không hiệu quả.

## 2. Nhớ lại định danh kiểu và class loader

Trong Java, **tên nhị phân chưa đủ để xác định một kiểu runtime**. Class loader định nghĩa kiểu là thành phần của danh tính kiểu. Hai lớp có cùng `getName()` nhưng được định nghĩa bởi hai loader khác nhau có thể là hai `Class<?>` khác nhau:

~~~text
Ứng dụng chính: AppClassLoader
    └── com.example.PluginApi       → Class A

Plugin được nạp riêng: PluginClassLoader
    └── com.example.PluginApi       → Class B

A.getName().equals(B.getName())     → true
A == B                              → false
~~~

Đây là tình huống minh họa, không phải hai định nghĩa cùng package trong một mã nguồn độc lập. Trong chương trình thật, cần xem loader có **ủy quyền cho cha** hay **tự định nghĩa lớp**. Một loader con có thể trả về `Class` đã được loader cha định nghĩa; điều này không tạo ra một kiểu mới.

Class unloading là khả năng của JVM trong quá trình quản lý bộ nhớ: theo Java Language Specification, một lớp/interface **có thể** được unload khi class loader định nghĩa nó có thể được GC thu hồi. Việc class thực sự được unload và thời điểm thực hiện là quyết định của JVM; ứng dụng không được dựa vào việc class unloading xảy ra ngay lập tức.

Hai điểm cần phân biệt:

- **Đối tượng `Class<?>` là metadata gateway của một kiểu cụ thể.** Nếu mã ngoài plugin giữ mạnh đối tượng này, loader định nghĩa lớp có thể tiếp tục bị giữ.
- **Class loader còn sống** có thể giữ nhiều thứ hơn một lớp: metadata của các lớp được nạp, liên kết giữa chúng, các đối tượng liên quan. Vì thế một entry nhỏ trong cache có thể làm phần bộ nhớ đáng kể không được thu hồi.

## 3. Đường tham chiếu gây giữ class loader

Ví dụ cache nằm trong một class của ứng dụng chính, thường tồn tại đến cuối chương trình:

~~~text
GC Root: Class/static field của ứng dụng chính
      ↓
ConcurrentHashMap (cache tồn tại lâu)
      ↓
entry: key là Class<?> của plugin       [strong]
      ↓
Class metadata / runtime type
      ↓
defining PluginClassLoader
      ↓
các lớp/metadata liên quan của plugin
~~~

Đây là **sơ đồ khái niệm về khả năng truy cập**, không phải mô tả bắt buộc từng cạnh nội bộ của mọi JVM. Khi phân tích heap dump, đường tham chiếu có thể chứa những đối tượng trung gian và cạnh khác với sơ đồ trên.

Một số dạng giữ mạnh dễ gặp:

- `Map<Class<?>, ...>`, `Set<Class<?>>` hoặc danh sách `Class<?>` nằm trong singleton, static registry.
- `Map<String, Method>` hoặc `Map<String, Field>`. **Key là `String` không loại bỏ nguy cơ** nếu value là đối tượng Reflection tham chiếu tới lớp khai báo của plugin.
- Cache chứa `Constructor<?>`, `MethodHandle`, `MethodType`, `CallSite`, factory, lambda, instance của plugin hoặc một object có field tham chiếu các thành phần đó.
- Cấu trúc không trực tiếp giữ `Class` nhưng value chứa class, class loader, đối tượng plugin hoặc dữ liệu `ThreadLocal` còn sống trong thread pool.
- Thread còn chạy có **thread context class loader (TCCL)** trỏ tới plugin loader sau khi plugin đã ngừng sử dụng.

Vì vậy, chẩn đoán phải hỏi **ai còn giữ mạnh cái gì, và giữ lâu đến đâu**, thay vì chỉ tìm các biến có tên `CACHE`.

## 4. Ví dụ Java 17+ chạy độc lập: cùng tên, hai loader, một cache mạnh

Ví dụ tạo hai đối tượng `Class` cho một tên, trong đó class thứ hai được một loader con **tự định nghĩa**. Không phụ thuộc vào việc GC sẽ chạy; chương trình chỉ kiểm tra danh tính kiểu và việc cache mạnh đang chứa entry.

Tạo tệp `ClassCacheLifetimeDemo.java`:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClassCacheLifetimeDemo {
    record Metadata(String binaryName, int methodCount) {}

    static final Map<Class<?>, Metadata> STRONG_CACHE =
            new ConcurrentHashMap<>();

    static final ClassValue<Metadata> CLASS_METADATA = new ClassValue<>() {
        @Override
        protected Metadata computeValue(Class<?> type) {
            return new Metadata(
                    type.getName(), type.getDeclaredMethods().length);
        }
    };

    // Chỉ với tên PluginPayload, loader này tự định nghĩa lớp.
    static final class IsolatedLoader extends ClassLoader {
        private final String isolatedName;

        IsolatedLoader(ClassLoader parent, String isolatedName) {
            super(parent);
            this.isolatedName = isolatedName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals(isolatedName)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (InputStream in = getParent()
                            .getResourceAsStream(resource)) {
                        if (in == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = in.readAllBytes();
                        result = defineClass(
                                name, bytes, 0, bytes.length);
                    } catch (IOException ex) {
                        throw new ClassNotFoundException(name, ex);
                    }
                }
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> hostType = PluginPayload.class;
        ClassLoader pluginLoader = new IsolatedLoader(
                hostType.getClassLoader(), hostType.getName());
        Class<?> pluginType = Class.forName(
                hostType.getName(), false, pluginLoader);

        System.out.println("same name: "
                + hostType.getName().equals(pluginType.getName()));
        System.out.println("same Class: " + (hostType == pluginType));
        System.out.println("defined by child: "
                + (pluginType.getClassLoader() == pluginLoader));

        Metadata data = new Metadata(
                pluginType.getName(),
                pluginType.getDeclaredMethods().length);
        STRONG_CACHE.put(pluginType, data);
        System.out.println("strong entries: " + STRONG_CACHE.size());

        Metadata fromClassValue = CLASS_METADATA.get(pluginType);
        System.out.println("ClassValue result: "
                + fromClassValue.binaryName().equals(pluginType.getName()));

        // Chủ động bỏ tham chiếu do ứng dụng giữ trong map.
        STRONG_CACHE.remove(pluginType);
        System.out.println("strong entries after remove: "
                + STRONG_CACHE.size());
    }
}

// Cùng tệp nguồn: javac tạo thêm PluginPayload.class.
class PluginPayload {
    public int answer() {
        return 42;
    }
}
~~~

Lệnh chạy (đứng trong thư mục ghi tệp):

~~~bash
javac ClassCacheLifetimeDemo.java
java ClassCacheLifetimeDemo
~~~

Kết quả xác định:

~~~text
same name: true
same Class: false
defined by child: true
strong entries: 1
ClassValue result: true
strong entries after remove: 0
~~~

**Giải thích:**

1. `PluginPayload.class` là kiểu do loader thông thường của ứng dụng thấy. `IsolatedLoader` tự đọc bytecode và gọi `defineClass` cho **chính tên `PluginPayload`**, nên `pluginType` khác `hostType` dù tên bằng nhau.
2. Entry `STRONG_CACHE.put(pluginType, data)` giữ mạnh đối tượng `Class` làm khóa khi map còn sống. Nếu mã này là một registry tồn tại lâu dài, entry có thể giữ loader/plugin cũ.
3. `ClassValue.get(pluginType)` tạo metadata gắn **theo đúng `Class<?>`**, phù hợp cho dữ liệu tính một lần theo từng kiểu.
4. `STRONG_CACHE.remove(pluginType)` bỏ tham chiếu **của map**. Các biến local `pluginType`, `pluginLoader`, metadata và các tham chiếu JVM khác có thể vẫn tồn tại trong thời gian chương trình chạy. **Không được suy ra loader đã unload** chỉ từ `size() == 0`.

Chương trình này minh họa cơ chế và quyền sở hữu cache; nó **không dùng `System.gc()`, `finalize()` hoặc thời gian chờ** để tạo một kết quả GC không đáng tin. `IsolatedLoader` là công cụ học tập, không phải loader plugin production đã xử lý đầy đủ resource, security và module boundaries.

## 5. Tại sao key là `Class<?>` lại khác key là tên lớp?

Xem hai cách tổ chức cache:

~~~java
Map<Class<?>, Metadata> byClass = new ConcurrentHashMap<>();
Map<String, Metadata> byName = new ConcurrentHashMap<>();
~~~

*Đây là fragment: cần `Metadata`, import `Map` và `ConcurrentHashMap` như ví dụ trên.*

### Cache theo `Class<?>`

**Ưu điểm:** tự phân biệt các kiểu có cùng tên nhưng do loader khác nhau định nghĩa; lookup theo đúng kiểu runtime; tiện cho Reflection.

**Rủi ro:** map tồn tại lâu với strong key có thể giữ lớp và loader không còn được ứng dụng sử dụng. Nếu cache không có chính sách gỡ từng plugin, dữ liệu sẽ tích tụ sau nhiều lần reload.

### Cache theo `String`

**Ưu điểm:** bản thân `String` tên lớp không nhất thiết giữ class loader. Có thể phù hợp cho nội dung độc lập với loader, chẳng hạn tên hiển thị đã chuẩn hóa.

**Rủi ro:** hai plugin có thể có lớp trùng `binaryName` nhưng khác metadata. Dùng mỗi tên làm khóa **có thể trả nhầm dữ liệu**. Hơn nữa, nếu `Metadata` bên value chứa `Class`, `Method`, `Field` hoặc instance plugin, cache `Map<String, Metadata>` vẫn có thể giữ loader. Chuyển key sang `String` không phải cách sửa tổng quát.

**Chọn key theo định danh nghiệp vụ.** Nếu cache cần một namespace của plugin, có thể tạo khóa logic `pluginId + pluginGeneration + className` (gồm phiên bản/vòng đời reload) và **xóa các entry** khi gỡ plugin. Khi dùng `Class<?>`, áp dụng phạm vi cache phù hợp hoặc giải pháp vòng đời bên dưới.

## 6. Lựa chọn 1: cache có phạm vi và xóa chủ động

Đơn giản nhất là **đưa cache vào đúng vòng đời**:

~~~text
PluginSession #42
  ├── loader
  ├── services
  └── metadata cache chỉ dùng cho session #42

Khi unload session:
  1. Ngừng các lời gọi mới, đợi công việc đang chạy hoàn tất
  2. Hủy đăng ký handler, listener, task, timer, ThreadLocal
  3. Xóa metadata cache và tham chiếu tới services/loader
  4. Đóng tài nguyên loader hỗ trợ close() nếu thích hợp
  5. Cho phép JVM tự quyết định GC và class unloading
~~~

Nếu một cache tĩnh chứa nhiều plugin, có thể tổ chức namespace theo `pluginId` và một phương thức `evictPlugin(id)`. **Phải kiểm soát đồng thời:** không nên gỡ entry trong khi request cũ còn dùng nó rồi tuyên bố plugin đã unload; cần chuyển trạng thái plugin sang dừng nhận việc mới, kết thúc những tác vụ đang chạy và hủy đăng ký các điểm giữ tham chiếu.

Dùng API cache có giới hạn kích thước hoặc thời gian sống khi mục tiêu là kiểm soát bộ nhớ tổng thể. Nhưng chính sách hết hạn **không bảo đảm** loader được gỡ đúng thời điểm nếu entry còn được truy cập, và cũng không thay thế cleanup vòng đời đối với resource khác.

**Phù hợp khi:** plugin có lifecycle rõ, module được triển khai lại, hoặc cache chứa nhiều thành phần (metadata, instance, handler) mà GC yếu không thể giải quyết yêu cầu hủy đăng ký/tài nguyên.

## 7. Lựa chọn 2: `ClassValue<T>` cho metadata theo từng `Class`

`java.lang.ClassValue<T>` là API chuẩn Java để **gắn giá trị tính lười theo từng `Class<?>`**. Đặc biệt phù hợp cho metadata phát sinh từ chính class đang xét: mapping field, thông tin serializer, descriptor dùng trong framework.

~~~java
static final ClassValue<Field[]> DECLARED_FIELDS = new ClassValue<>() {
    @Override
    protected Field[] computeValue(Class<?> type) {
        return type.getDeclaredFields();
    }
};

Field[] fields = DECLARED_FIELDS.get(SomeType.class);
~~~

*Fragment cần `import java.lang.reflect.Field;` và lớp `SomeType` tồn tại.*

Đặc điểm quan trọng:

- Giá trị được liên kết với **đúng `Class<?>`**, nên hai class cùng tên nhưng khác loader có thể nhận metadata riêng.
- `get(type)` tính dữ liệu theo nhu cầu bằng `computeValue(type)`; trong cạnh tranh nhiều thread, `computeValue` **có thể được gọi nhiều lần**, dù một giá trị được chấp nhận cho lần liên kết đó. Không thực hiện side effect không thể lặp hoặc đăng ký resource một cách vô điều kiện trong `computeValue`.
- `remove(type)` xóa liên kết của `ClassValue` cho lớp đó; lần `get` sau có thể tính lại. Đây là **invalidation metadata**, không phải lệnh dỡ lớp và cũng không loại bỏ các tham chiếu mà code bên ngoài đang giữ.
- Khác với một `static Map<Class<?>, V>` nằm ở ứng dụng, `ClassValue` được thiết kế để gắn giá trị với đời sống của `Class` mà không cần dùng một bản đồ global giữ mạnh khóa kiểu theo cách thông thường. Nó giúp tránh **một nguồn** giữ loader; không đảm bảo unloading nếu nơi khác vẫn giữ class, loader hoặc giá trị.

Nếu `Field[]` trong ví dụ là mảng cache, **đừng cho code gọi sửa trực tiếp mảng chung** nếu điều đó ảnh hưởng đến các thành phần khác; trả bản sao hoặc metadata bất biến khi cần. `ClassValue` bảo đảm cơ chế liên kết, **không bảo đảm object giá trị là immutable hoặc thread-safe**.

Cũng cần phân biệt `ClassValue` với `ThreadLocal`: `ClassValue` liên kết theo **class**, không theo thread. Đây thường là lợi thế đối với metadata dùng chung giữa nhiều luồng; việc đồng bộ nội bộ giá trị vẫn là trách nhiệm của thiết kế.

**Phù hợp khi:** dữ liệu được tính từ một class, truy cập lặp lại, nên giữ theo vòng đời class và không cần quét danh sách mọi entry để xuất báo cáo.

## 8. Lựa chọn 3: `WeakHashMap<Class<?>, V>` — cần kiểm tra cả value

`WeakHashMap` lưu **key bằng weak reference**. Khi key không còn được tham chiếu mạnh từ nơi khác, GC có thể thu hồi nó và entry sẽ được loại bỏ. Đây là lựa chọn có thể cân nhắc cho một số cache, nhưng có một bẫy lớn: **value của `WeakHashMap` vẫn được giữ mạnh**.

Ví dụ **không giải quyết được vòng giữ tham chiếu**:

~~~java
Map<Class<?>, Method> methods = new WeakHashMap<>();

Class<?> type = ...;
Method method = type.getDeclaredMethod("run");
methods.put(type, method);
~~~

*Fragment minh họa giả định `type` có phương thức `run` và xử lý ngoại lệ tra cứu.*

Đường tham chiếu có thể là:

~~~text
Global WeakHashMap
    │
    ├── key (weak) ─────→ Class<?> của plugin
    │
    └── value (strong) ─→ Method
                             └── declaring Class<?> của plugin
                                      └── loader plugin
~~~

Giá trị `Method` lại giữ đường tham chiếu mạnh tới **chính class dùng làm key**. Vì vậy key có thể không đủ điều kiện bị GC dọn, và việc dùng weak key không đem lại tác dụng mong đợi.

Tương tự, value là một `MethodHandle`, metadata object chứa `Class<?>`, mảng `Field[]` hoặc lambda đóng trên class/instance của plugin cũng có thể giữ key trực tiếp hoặc gián tiếp. Có thể dùng value chỉ chứa thông tin bất biến độc lập với class, hoặc cần một cấu trúc cache xử lý vòng tham chiếu kiểu *ephemeron* phù hợp; **không mặc định rằng `WeakHashMap` là ephemeron map**.

Các lưu ý:

- `WeakHashMap` mặc định **không thread-safe**. Với truy cập đồng thời, dùng đồng bộ thích hợp hoặc lựa chọn khác; `Collections.synchronizedMap(...)` chỉ đồng bộ các thao tác theo hợp đồng wrapper, không tự giải quyết giá trị giữ key.
- Số lượng entry có thể thay đổi do GC; `size()` không phải một số liệu ổn định để làm logic nghiệp vụ hoặc snapshot test.
- Weak reference **không khiến GC chạy ngay**; đừng xây test yêu cầu entry biến mất sau `System.gc()`.
- Giá trị yếu (`WeakReference<V>`) cũng có đánh đổi: dữ liệu có thể bị thu hồi nhanh, phải tạo lại thường xuyên; vẫn cần bảo đảm tính đúng đắn và concurrency.

**Phù hợp khi:** bạn hiểu rõ đường giữ tham chiếu của cả key/value, metadata không trỏ ngược tới key, và chấp nhận eviction do GC. Với cache theo class thông thường, `ClassValue` thường là lựa chọn nên thử trước.

## 9. Cache `Method`, `Field`, `MethodHandle` và proxy cũng có thể giữ class loader

Mối nguy không chỉ nằm trong generic type của key.

| Cache chứa | Vì sao cần xem đường tham chiếu | Hướng xử lý |
| --- | --- | --- |
| `Class<?>` | Gắn với danh tính runtime của lớp và loader định nghĩa | Giới hạn scope, chủ động evict hoặc dùng `ClassValue` |
| `Method` / `Constructor<?>` | Metadata của thành viên thuộc declaring class | Không giữ trong global registry vượt đời plugin |
| `Field` | Tham chiếu field của declaring class | Cache theo class/scope phù hợp và xóa khi unload |
| `MethodHandle` / `MethodType` | Có thể tham chiếu lớp, member, adapter và các kiểu liên quan | Kiểm tra value graph, không chỉ cache key |
| `Proxy` instance / `InvocationHandler` | Instance, handler và các interface/proxy class có thể neo plugin | Hủy đăng ký listener/handler, ngừng công việc còn chạy |
| Lambda/factory/callback | Có thể capture instance hoặc class/loader plugin | Unregister theo vòng đời, tránh holder static |
| `ThreadLocal` và TCCL | Thread pool của ứng dụng có thể sống lâu hơn plugin | `remove()` ThreadLocal, khôi phục context loader, kết thúc task |
| `ClassValue<V>` | Gắn metadata theo class, có cơ chế quản lý liên kết | Không xuất giá trị ra global holder sống lâu; cân nhắc `remove` khi invalidate |

Một ví dụ phổ biến: một **static map trong lớp được nạp bởi AppClassLoader** giữ `Method` của plugin. Dù map dùng key `String`, value chứa một reflective member nên đường tham chiếu có thể giữ plugin loader. Khi triển khai lại plugin, bản method cũ không phải method của class mới chỉ vì hai class trùng tên.

## 10. Dọn lifecycle: đâu là trách nhiệm của hệ thống plugin?

Một hệ thống nạp/gỡ plugin nên định nghĩa các bước cleanup có chủ sở hữu rõ:

1. **Ngừng sử dụng plugin cũ.** Chặn request mới, kết thúc hoặc hủy các task đang chạy, callback hoặc timer liên quan.
2. **Hủy đăng ký đối tượng sống lâu.** Loại listener, interceptor, registry, event bus subscription, driver/provider hoặc service chứa instance từ plugin.
3. **Xóa cache theo plugin/class loader.** Cả key và value có thể giữ metadata; kiểm tra các singleton và vùng static của ứng dụng chính.
4. **Dọn thread và ngữ cảnh.** Khôi phục TCCL sau từng công việc; loại `ThreadLocal` của plugin khỏi thread do ứng dụng quản lý; tránh thread chạy mã plugin vô thời hạn.
5. **Đóng tài nguyên có thể đóng.** Chẳng hạn `URLClassLoader.close()` dừng việc nạp tài nguyên/JAR mới và giải phóng resource liên quan theo API; **không có nghĩa gọi `close()` sẽ tự unload các class đã nạp**.
6. **Chỉ sau khi không còn tham chiếu mạnh cần thiết** mới kỳ vọng loader *có thể* trở thành đối tượng đủ điều kiện GC. JVM quyết định khi nào thu gom và unload class.

Tránh chạy cleanup trong `finalize()` hoặc coi `System.gc()` là cơ chế vận hành bắt buộc. Dọn lifecycle phải là hành vi chủ động, có thể quan sát và xử lý lỗi.

### `ClassLoader` tạo ra theo từng lần tải lại

Nếu một plugin được reload nhiều lần, mỗi thế hệ loader có thể giữ bản riêng của cùng tên class. Khi đăng ký cache hoặc callback, hãy gắn thông tin thế hệ (*generation*) để tránh trả metadata cũ cho plugin mới.

Ví dụ logic:

~~~text
plugin: payments
generation: 17 → loader A → com.example.Handler (Class A)
generation: 18 → loader B → com.example.Handler (Class B)

Tên lớp giống nhau, Class A != Class B.
Các cache/handler của generation 17 phải được giải phóng theo lifecycle.
~~~

Một lớp mới cùng tên không tự xóa các entry do ứng dụng chính đang giữ cho lớp cũ.

## 11. Chẩn đoán thực tế: heap, class unloading và đường về GC root

Nếu thấy bộ nhớ tăng sau mỗi vòng redeploy, hãy đo và xác định nguồn giữ tham chiếu.

### 11.1. Dấu hiệu cần phân biệt

- **Heap tăng:** có thể do cache value/instance/plugin registry còn sống; heap không phải toàn bộ bộ nhớ JVM.
- **Metaspace tăng:** có thể liên quan số lớp/loader và khả năng class unloading, nhưng cũng phụ thuộc chính sách GC, workload và hành vi nạp lớp bình thường.
- **Số class loader cũ không giảm sau khi đã gỡ plugin:** cần kiểm tra vòng đời, GC thực tế và có còn đường tham chiếu mạnh hay không. Không kết luận từ một lần quan sát đơn lẻ.

Một cache lớn nhưng có giới hạn và hoạt động đúng có thể là chi phí chủ ý. Đối với leak, hãy ưu tiên bằng chứng **những đối tượng không còn cần thiết nhưng vẫn được giữ qua GC roots**.

### 11.2. Dùng công cụ HotSpot/JDK khi phù hợp

Trên JVM HotSpot với công cụ chẩn đoán sẵn có:

~~~bash
jcmd <pid> VM.classloader_stats
~~~

Ví dụ lệnh này có thể giúp xem thống kê class loader; tính khả dụng và tên command phụ thuộc JVM/JDK. `<pid>` là mã tiến trình thực, không nhập nguyên dấu ngoặc nhọn vào shell.

Khi chủ động bật log trong một môi trường thử nghiệm HotSpot:

~~~bash
java -Xlog:class+unload=info YourApplication
~~~

Các dòng class unload cho biết sự kiện unload thực tế; **không xuất hiện dòng log** trong một cửa sổ ngắn không tự chứng minh có leak, vì JVM có thể chưa thực hiện class unloading.

Với Native Memory Tracking (cần cấu hình phù hợp lúc khởi động nếu JVM hỗ trợ), bạn có thể xem bộ nhớ native tổng thể/metaspace bằng công cụ tương ứng. Đừng dùng một số liệu NMT đơn lẻ để quy lỗi cho cache.

### 11.3. Heap dump: tìm đường từ đối tượng bị giữ đến GC root

Trong heap analyzer như Eclipse Memory Analyzer hoặc VisualVM (tùy môi trường), hãy:

1. Tìm các **class loader cũ** của plugin, group theo loại và số lượng.
2. Chọn loader đáng lẽ đã ngừng sử dụng, xem **path to GC roots** và các strong references giữ loader, `Class` hoặc reflective member của plugin.
3. Theo ngược tới thành phần dài hạn: static map, singleton, thread, `ThreadLocal`, TCCL, listener, callback hoặc cache value.
4. Kiểm tra cả value graph: weak key không giúp nếu value giữ key.
5. Xử lý điểm giữ tham chiếu thực sự rồi thử lại chu kỳ load/unload trong cùng điều kiện kiểm thử.

Heap dump cần được quản lý cẩn thận vì có thể chứa dữ liệu nhạy cảm của ứng dụng. Chẩn đoán chỉ trên môi trường được phép và giữ phạm vi dữ liệu cần thiết.

## 12. Chiến lược kiểm thử không phụ thuộc GC có chạy đúng lúc không

Bạn có thể kiểm thử chắc chắn **lifecycle cache**, ngay cả khi không thể yêu cầu JVM thu hồi class loader tức thì:

- Nạp plugin thế hệ mới, xác nhận `Class<?>` của thế hệ mới **khác** lớp của thế hệ cũ nếu loader tự định nghĩa kiểu.
- Cache được gắn đúng scope hoặc key thế hệ, không trả nhầm metadata cũ.
- Khi gọi `unloadPlugin(id)`, registry của ứng dụng không còn entry/callback/task được sở hữu bởi plugin đó; phương thức này có thể được kiểm tra bằng số entry còn lại hoặc danh sách đăng ký.
- Với `ClassValue`, kiểm tra `get`, `remove` và việc tính lại metadata qua một hàm `computeValue` không có side effect không thể lặp. Không giả định `computeValue` chỉ được gọi đúng một lần khi chạy đa luồng.
- Với weak reference, chỉ ghi nhận GC thu hồi được loader trong **kiểm thử tích hợp có kiểm soát** như một tín hiệu hỗ trợ. Không viết assertion kiểu “gọi `System.gc()` rồi nhất định `weakRef.get() == null`”.

Ví dụ tự kiểm tra logic chủ sở hữu cache:

~~~java
Map<Class<?>, Metadata> cache = new HashMap<>();
cache.put(pluginType, metadata);

// Sau bước unregister/evict thuộc lifecycle:
cache.remove(pluginType);
if (cache.containsKey(pluginType)) {
    throw new AssertionError("Plugin cache entry remains");
}
~~~

*Fragment giả định các kiểu/biến đã khai báo; ví dụ này kiểm tra entry chứ không khẳng định JVM đã unload class.*

Đừng nhầm giữa **“code đã bỏ tham chiếu do mình sở hữu”** và **“JVM đã thu hồi mọi dữ liệu của plugin”**. Một thư viện bên thứ ba hoặc thread khác vẫn có thể giữ tham chiếu.

## 13. Checklist lựa chọn cache cho Reflection

| Nhu cầu | Lựa chọn thường phù hợp | Điều phải kiểm tra |
| --- | --- | --- |
| Metadata chỉ dùng trong một request/task | Biến cục bộ hoặc cache theo request | Không lưu ra singleton |
| Metadata theo từng class, dùng nhiều lần | `ClassValue<V>` | `computeValue` thuần, value an toàn luồng, không có global holder giữ lại |
| Plugin có lifecycle rõ và nhiều resource đi kèm | Cache theo plugin, `evictPlugin` | Unregister đầy đủ và chờ task cũ xong |
| Cache chung có giới hạn kích thước/thời gian | Bounded/expiring cache | Key/value giữ loader bao lâu, dữ liệu có bị tính lại quá nhiều không |
| Weak-key cache khi value độc lập key | `WeakHashMap<Class<?>, V>` (cẩn thận) | Value không giữ ngược class; concurrency và GC |
| Cần lưu danh sách mọi class toàn hệ thống lâu dài | Registry được cấp quyền sở hữu cụ thể | Phải có chính sách loại lớp của plugin khi unload |
| Chỉ muốn giữ tên hiển thị độc lập plugin | `String` hoặc DTO bất biến | Không dùng cùng tên làm danh tính runtime giữa nhiều loader |

Không có một loại cache tối ưu cho mọi trường hợp. Hãy bắt đầu từ **ai sở hữu metadata**, **khi nào dữ liệu hết hạn**, **có nhiều loader hay không**, và **value chứa những tham chiếu nào**.

## 14. Hiểu lầm thường gặp

**“`Class<?>` chỉ là một tên nhẹ, giữ nó không ảnh hưởng loader.”** Sai. Nó đại diện kiểu runtime cụ thể, liên quan đến class loader định nghĩa lớp.

**“Chỉ cần đổi `Map<Class<?>, V>` thành `Map<String, V>`.”** Không chắc. Value vẫn có thể giữ class, method hoặc instance; còn key tên đơn lẻ có thể nhập nhằng giữa plugin generations.

**“`WeakHashMap<Class<?>, Method>` đảm bảo không leak.”** Sai nếu `Method` giữ đường tham chiếu về chính declaring class làm key. Value được giữ mạnh.

**“`ClassValue` đảm bảo JVM unload lớp ngay.”** Sai. Nó giúp tổ chức metadata theo class, nhưng loader chỉ đủ điều kiện thu hồi khi không còn các đường tham chiếu mạnh khác; JVM không cam kết thời điểm unload.

**“Gọi `URLClassLoader.close()` là unload tất cả class.”** Sai. `close()` là đóng nguồn tài nguyên/khả năng nạp thêm theo API, không phải lệnh gỡ mọi đối tượng `Class`.

**“Không thấy unload log là chắc chắn memory leak.”** Chưa đủ. Cần xem đã có một chu kỳ GC/class unloading phù hợp chưa và có đường tham chiếu nào còn giữ loader không.

**“Chỉ có static field gây leak.”** Không đúng. Thread pool, TCCL, callback, listener, timer, instance trong container và value của cache đều có thể là nguồn giữ.

## 15. Tự kiểm tra

1. Tại sao `static Map<Class<?>, Metadata>` có thể giữ plugin sau khi gỡ? **Key `Class` được giữ mạnh bởi map tồn tại lâu và có thể giữ loader cùng lớp liên quan.**
2. Hai class có cùng `getName()` nhưng khác defining loader có luôn là một kiểu không? **Không.**
3. `WeakHashMap<Class<?>, Method>` có tự động an toàn với class unloading không? **Không, vì value `Method` có thể giữ lại declaring class/key.**
4. Metadata nên gắn theo `Class` và không cần liệt kê toàn bộ cache: thử API nào? **`ClassValue<T>`.**
5. Gọi `ClassValue.remove(type)` có bắt JVM unload class không? **Không.** Nó chỉ xóa liên kết metadata tương ứng.
6. Khi reload plugin, ngoài cache cần dọn gì? **Callback, listener, task, `ThreadLocal`, TCCL, instance và resource mà hệ thống còn sở hữu.**
7. Vì sao không dùng `System.gc()` rồi assert loader biến mất? **GC/class unloading không có thời điểm đảm bảo; kết quả có thể không ổn định.**
8. Nếu map dùng `String` key mà value là `Method` từ plugin thì còn nguy cơ giữ loader không? **Có.**

## 16. Kết luận

Cache metadata Reflection là một tối ưu hợp lý, nhưng một tham chiếu mạnh đến `Class<?>`, `Method`, `Field`, `MethodHandle` hoặc instance plugin có thể kéo dài vòng đời của **cả class loader**. Điều cần thiết là thiết kế **cache theo đúng vòng đời dữ liệu**, phân biệt danh tính kiểu theo loader, chủ động dọn đăng ký khi gỡ plugin, dùng `ClassValue` khi cần giá trị gắn theo class và tránh nhầm weak key với việc value không còn giữ class.

Khi nghi ngờ leak, kiểm chứng bằng đường tham chiếu từ GC roots và số liệu class loader/metaspace theo thời gian. Việc entry biến mất khỏi cache chỉ chứng minh một nguồn giữ đã được bỏ; việc JVM unload lớp vẫn phụ thuộc vào toàn bộ đồ thị tham chiếu và hoạt động của GC.

### Tài liệu tham khảo

- [Java SE 21 — `ClassValue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassValue.html)
- [Java SE 21 — `WeakHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/WeakHashMap.html)
- [Java SE 21 — `ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 21 — `URLClassLoader.close`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html)
- [JLS `12.7 — Unloading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.7)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-006">Tránh cache toàn JVM không có chính sách vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh cache toàn JVM không có chính sách vòng đời trong Java Reflection

## 1. Câu hỏi quan trọng trước khi tạo cache

Một cache Reflection được xem là hoàn chỉnh khi **đúng dữ liệu, đúng chủ sở hữu và đúng thời hạn**. `ConcurrentHashMap` làm việc với nhiều thread nhưng không tự đặt kích thước tối đa, thời gian hết hạn hoặc điểm hủy. Vì vậy, với cache metadata, phải chỉ rõ: *ai tạo, ai được dùng, lúc nào metadata còn hợp lệ, ai loại bỏ, và cần quan sát điều gì sau khi loại bỏ*.

Tình huống cần quyết định khác nhau: metadata cho một request ngắn hạn có thể giữ ở biến local; schema của class dùng hàng triệu lần có thể nằm trong `ClassValue`; registry plugin thay phiên bản phải hủy cache khi plugin dừng; kết quả phụ thuộc policy/tenant phải gắn version hoặc chỉ nằm trong phạm vi request.

Về class loader, một entry giữ `Class<?>`, `Method`, `Field` hoặc handle trong thành phần sống lâu **có thể** giữ loader của plugin cũ. Nhưng không phải cứ thấy `static` là leak: cần xét owner và đường tham chiếu thực. Nội dung chuyên sâu về GC roots và `WeakHashMap` nằm ở bài “Cache giữ Class có thể giữ cả class loader”; bài này tập trung xây **chính sách vòng đời có thể kiểm tra**.

## 2. Phân loại dữ liệu để xác định thời hạn hợp lệ

| Dữ liệu cache | Identity/scope chính | Lúc cần tính lại hoặc loại bỏ |
| --- | --- | --- |
| `Field`, `Method`, `Constructor` của một class cụ thể | `Class<?>`, member signature và quy tắc lookup | Owner/plugin unload hoặc metadata cần rebuild |
| Descriptor bất biến suy từ class | Theo `Class<?>`; `ClassValue` khi phù hợp | Class/schema policy thực sự thay đổi |
| Schema đã lọc theo naming policy/config | Class **cộng policy generation/version** | Đổi policy hoặc phiên bản schema |
| Quyết định field được lộ cho người dùng/tenant | Request/user/policy; tránh cache class-only | Thay quyền, tenant hoặc request context |
| Giá trị `Field.get(instance)` | Trạng thái **instance**, không phải class metadata | Đổi state; thông thường không thuộc metadata cache |
| Dữ liệu/handler của plugin generation cũ | Instance/generation của plugin | Đóng plugin, hủy đăng ký và kết thúc in-flight work |

**Key đúng chưa đủ.** `Class<?>` phân biệt runtime class theo loader; tuy nhiên cùng một class có thể có schema khác nhau khi policy hoặc cấu hình thay đổi. Một `ClassValue` chỉ phân biệt theo class, nên dữ liệu phụ thuộc version cần thêm tầng quản lý version/invalidation. Ngược lại, cache bằng chuỗi `getName()` có thể ghép nhầm class từ hai loader.

**Value cũng phải được xét.** Nếu shared value chứa capability truy cập private hoặc trạng thái theo tenant, chỉ thêm phần tử vào key không tự tạo ra ranh giới quyền phù hợp. Nên giữ metadata chung ở scope đã kiểm chứng và áp dụng kiểm tra quyền theo request tại nơi sử dụng.

## 3. Ba lựa chọn thực tế và trách nhiệm dọn dẹp

### 3.1. Cache thuộc request, session hoặc plugin

Dùng khi dữ liệu chỉ lặp lại trong thời gian ngắn hoặc gắn với một plugin generation. Một `InspectionSession` có thể chứa `Map<Class<?>, Metadata>`, đóng bằng `AutoCloseable` và xóa entries trong `close()`; ví dụ chạy được ở mục 4.

Một plugin unload cần thứ tự rõ ràng: ngừng nhận việc mới → hoàn tất/hủy việc đang chạy → unregister listener/callback/task → xóa cache và reference do plugin sở hữu → đóng tài nguyên phù hợp. `clear()` chỉ cắt reference của chính map, không chứng minh JVM đã unload lớp. Với session có nhiều thread, `close()` còn phải phối hợp với in-flight operation thay vì cho phép lookup và shutdown tranh chấp.

### 3.2. `ClassValue<T>` cho metadata thật sự thuộc về từng Class

Nếu một descriptor chỉ là hàm của `Class<?>` và tái dùng thường xuyên, `ClassValue` tránh map global giữ key mạnh trực tiếp. `computeValue(type)` có thể chạy nhiều lần dưới contention, nên tạo metadata hoàn chỉnh, lý tưởng không có side effect. `remove(type)` chỉ invalid một class trong một `ClassValue`, không cung cấp TTL, `clearAll` hay việc đóng tài nguyên bên ngoài. Lần đọc đang giữ value cũ có thể tiếp tục dùng nó; nếu policy cần chuyển đổi đồng nhất, phải có synchronization/generation ở tầng sở hữu.

### 3.3. Cache bounded/TTL hoặc generation-aware

Chọn khi hệ thống phải cache theo nhiều khóa động như `(Class, policyVersion)`, cần giới hạn memory và kiểm soát eviction. Đặt rõ **maximum size/weight, idle/TTL, trigger invalidation và owner**; `ConcurrentHashMap` không cung cấp các khả năng này tự động.

Ví dụ, khi cấu hình serialization nâng từ generation `v12` lên `v13`, xây snapshot metadata mới, công bố generation mới rồi hủy entries `v12` sau khi lượt xử lý cũ hoàn thành. TTL là giới hạn thời gian dự phòng, không thay cho sự kiện invalidate do schema/security policy đổi. Theo dõi hit/miss, current entries, evictions và retained old loaders để phát hiện chính sách không hoạt động.

### Checklist thiết kế

Trước khi triển khai hãy xác định **owner**, **key/value dependencies**, **thời hạn hiệu lực**, **điểm invalidate**, **hành vi concurrent close/reload** và **cách xác minh**. Một cache thiếu bất kỳ mục nào trong các mục đó có thể nhanh ở trạng thái ổn định nhưng trả schema cũ hoặc giữ plugin lâu ngoài dự kiến.

## 4. Ví dụ Java 17+ hoàn chỉnh: global map, ClassValue và session

Ví dụ dưới đây dùng metadata đơn giản là tên field của lớp `Invoice`. Chương trình minh họa **so sánh hành vi cache**, không mô phỏng đầy đủ class unloading. Vì chạy tuần tự trên một class bình thường, các con số scan ổn định. Đừng sao chép `GLOBAL` làm giải pháp production không có chính sách giải phóng.

Lưu nội dung vào `ReflectionCacheLifetimeDemo.java`:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReflectionCacheLifetimeDemo {
    static final class Invoice {
        private final String id = "INV-7";
        private final int amount = 500;
        private transient String debug = "temporary";
    }

    private static final AtomicInteger SCANS = new AtomicInteger();

    // Anti-pattern demo: never cleared, strongly retains Class keys.
    private static final ConcurrentMap<Class<?>, List<String>> GLOBAL =
            new ConcurrentHashMap<>();

    private static final ClassValue<List<String>> PER_CLASS = new ClassValue<>() {
        @Override
        protected List<String> computeValue(Class<?> type) {
            return scan(type);
        }
    };

    private static List<String> scan(Class<?> type) {
        SCANS.incrementAndGet();
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .filter(f -> !Modifier.isTransient(f.getModifiers()))
                .map(Field::getName)
                .sorted()
                .toList();
    }

    static final class InspectionSession implements AutoCloseable {
        private final Map<Class<?>, List<String>> cache = new HashMap<>();
        private boolean closed;

        List<String> fieldNames(Class<?> type) {
            if (closed) throw new IllegalStateException("session closed");
            return cache.computeIfAbsent(type, ReflectionCacheLifetimeDemo::scan);
        }

        int entries() {
            return cache.size();
        }

        @Override
        public void close() {
            if (!closed) {
                cache.clear();
                closed = true;
            }
        }
    }

    public static void main(String[] args) {
        Class<?> type = Invoice.class;
        System.out.println("global first: " +
                GLOBAL.computeIfAbsent(type, ReflectionCacheLifetimeDemo::scan) +
                ", scans=" + SCANS.get());
        System.out.println("global again: " +
                GLOBAL.computeIfAbsent(type, ReflectionCacheLifetimeDemo::scan) +
                ", scans=" + SCANS.get());

        System.out.println("ClassValue first: " + PER_CLASS.get(type) +
                ", scans=" + SCANS.get());
        System.out.println("ClassValue again: " + PER_CLASS.get(type) +
                ", scans=" + SCANS.get());
        PER_CLASS.remove(type);
        System.out.println("ClassValue after remove: " + PER_CLASS.get(type) +
                ", scans=" + SCANS.get());

        try (InspectionSession session = new InspectionSession()) {
            System.out.println("session first: " + session.fieldNames(type) +
                    ", scans=" + SCANS.get());
            System.out.println("session again: " + session.fieldNames(type) +
                    ", entries=" + session.entries() +
                    ", scans=" + SCANS.get());
        }
        System.out.println("session closed: references cleared");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 ReflectionCacheLifetimeDemo.java
java ReflectionCacheLifetimeDemo
~~~

Output chính xác (chương trình một luồng):

~~~text
global first: [amount, id], scans=1
global again: [amount, id], scans=1
ClassValue first: [amount, id], scans=2
ClassValue again: [amount, id], scans=2
ClassValue after remove: [amount, id], scans=3
session first: [amount, id], scans=4
session again: [amount, id], entries=1, scans=4
session closed: references cleared
~~~

**Ý nghĩa:**

1. `GLOBAL` chỉ scan một lần, nhưng entry `Class<?>` sẽ tồn tại trong `static` map cho tới lúc xóa hoặc cache được GC. Map này không có `remove/clear`.
2. `PER_CLASS` cũng reuse metadata; gọi `remove(Invoice.class)` khiến lần `get` kế tiếp scan lại. Với nhiều thread, số lần `computeValue` không bắt buộc giống demo.
3. `InspectionSession` tái sử dụng kết quả trong phiên; `close()` xóa map. Dòng `"references cleared"` chỉ xác nhận cleanup do ứng dụng thực hiện, **không chứng minh ClassLoader đã được unload**.
4. `scan` chỉ lấy metadata, không đọc state của instance `Invoice`. Việc bỏ field `transient` là **chính sách riêng của demo**, không phải quy tắc bắt buộc khi dùng Reflection.
5. `getDeclaredFields()` không bảo đảm thứ tự; ví dụ `sorted()` để output ổn định.
6. `InspectionSession` minh họa **cách dùng tuần tự**. `HashMap`/`closed` không thread-safe; session chia sẻ đa luồng cần đồng bộ và quy tắc dừng tác vụ trước khi `close()`.

## 5. Tại sao key đúng vẫn có thể cho value sai?

`Class<?>` là key hợp lý cho metadata **cấu trúc của class**, nhưng nhiều giá trị derived metadata còn phụ thuộc vào policy. Cùng một class có thể có projection khác nhau khi:

- Audit nội bộ cần trường kỹ thuật, API công khai thì loại trường nhạy cảm.
- Tenant A và tenant B được nhìn các field khác nhau.
- Cấu hình serialization, naming strategy, locale hoặc version schema thay đổi.
- Module layer/access context khác nhau, hoặc plugin được hot reload.
- Mapping giữa field với business property thay đổi dù `Class<?>` không đổi.

Không được cache quyết định `allowed=true` cho user A trong `Map<Class<?>, Boolean>` toàn JVM rồi tái dùng cho user B. Nên cache phần **metadata có thể chia sẻ** và tính authorization/redaction theo request; hoặc đưa policy identity/version vào key và đặt cache trong owner có vòng đời cụ thể.

Không nên cache `Field.get(obj)` như thể metadata: đó là **giá trị runtime của một instance**, có thể đổi theo thời gian, có tác dụng bảo mật và không tự tạo snapshot nhất quán trên nhiều field.

## 6. Eviction, unload và concurrency là ba chủ đề riêng

### 6.1. Eviction không đồng nghĩa class unloading ngay

Khi xóa entry, map thôi giữ tham chiếu **của riêng nó**. Đối tượng đang chạy, stack frame, thread-local, executor, listener, callbacks, công cụ monitoring hoặc cache khác vẫn có thể giữ ClassLoader. Ngay cả khi không còn đường tham chiếu mạnh, JVM có thể trì hoãn GC/class unloading.

Không kiểm thử leak bằng cách gọi `System.gc()` một lần rồi kết luận chắc chắn. Hãy phân tích heap dump, GC roots và retained heap sau nhiều chu kỳ chạy/phục hồi phù hợp.

### 6.2. Compute không bảo đảm exactly once

Javadoc `ClassValue.get` cho phép nhiều thread cùng tính `computeValue` và cài đặt một kết quả; do đó compute phải có tính lặp lại an toàn, lý tưởng là thuần hàm trên metadata và policy bất biến. `remove(type)` cạnh tranh với `get(type)` có các quy tắc state riêng.

`ConcurrentHashMap.computeIfAbsent` điều phối atomic mapping của map; không nên ngoại suy ngữ nghĩa exactly-once của map sang `ClassValue`. Khi phân tích benchmark nhiều luồng, đừng assert đúng một lần compute nếu API cho phép cạnh tranh.

### 6.3. Close cần phối hợp với in-flight work

`close()` trên cache scoped không tự đợi các request đang giữ reference hoàn tất. Nếu nhiều worker đang sử dụng plugin, chủ sở hữu nên ngừng nhận công việc mới, đợi/cancel công việc đang xử lý theo policy, rồi giải phóng cache và ClassLoader. Tránh đồng thời dỡ loader trong khi một operation vẫn truy cập metadata của plugin.

## 7. Quy trình thiết kế cache Reflection có thể kiểm chứng

1. **Đo nhu cầu:** đo tỷ lệ scan lặp lại, thời gian Reflection, tổng latency, khối lượng class và heap/metaspace trước khi cache.
2. **Phân loại dữ liệu:** chỉ cache metadata dùng lại hợp lệ; phân biệt derived schema, dữ liệu theo user và giá trị object.
3. **Chọn owner:** request/session, plugin, application component hay JVM. Tránh owner lâu hơn dữ liệu mà không có lý do và cleanup.
4. **Chọn key:** `Class<?>` cho class identity, bổ sung policy/config generation khi cần; không dùng tên class đơn độc xuyên loader.
5. **Chọn implementation:** cache scoped, `ClassValue`, bounded/TTL; xem value có tham chiếu ngược tới class/loader không.
6. **Định nghĩa expiration/invalidation:** clear ở `close`, remove khi đổi policy, unload plugin, hoặc bounded eviction; xác định hành vi khi concurrent load.
7. **Đo/kiểm thử:** hit/miss, entry count (nếu có), retained class loaders, memory usage, thời gian reload và hành vi sau shutdown. Chạy lặp nhiều vòng plugin unload/reload, không chỉ một lần.

Các dấu hiệu cần điều tra gồm: class loader thế hệ cũ không mất khỏi GC roots sau khi plugin dừng, số entry tăng theo mỗi lần reload, retained heap/metaspace tăng liên tục, hoặc cache chứa field/Method của loader đã không còn phục vụ request nào.

## 8. Ví dụ lựa chọn theo tình huống

| Tình huống | Cách phù hợp | Lưu ý |
| --- | --- | --- |
| Quét vài loại DTO trong một request | Cache trong request/session | Dọn ngay sau request hoặc khi close. |
| Metadata bất biến theo `Class` dùng trên nhiều request | `ClassValue` | Không trộn auth/config per-user; `computeValue` có thể chạy nhiều lần. |
| Plugin hot deploy, runtime cần reload | Cache sở hữu bởi plugin hoặc per-class association | Gỡ registry, listener, worker và mọi reference khác tới loader cũ. |
| Schema phụ thuộc phiên bản cấu hình | Bounded cache key chứa version hoặc cache generation scoped | Invalidate khi version thay đổi. |
| Shared cache có nguy cơ gặp hàng trăm nghìn kiểu | Cache bounded có memory budget/TTL hoặc bỏ cache | `ConcurrentHashMap` nguyên bản không có max size. |
| Dữ liệu nhạy cảm theo tenant | Không giữ trong metadata cache toàn JVM | Redaction/permission theo request. |

## 9. Các lỗi vòng đời nên phát hiện trong review

| Dấu hiệu | Phân tích | Hành động |
| --- | --- | --- |
| Cache global tăng entries qua mỗi plugin reload | Có thể giữ metadata thế hệ cũ | Owner cho từng plugin/generation; evict khi unload |
| Policy đã cập nhật nhưng metadata vẫn cũ | Key thiếu policy version hoặc không invalidate | Versioned key/snapshot và quy trình chuyển đổi rõ |
| `close()` chạy trong khi request còn dùng metadata | Reference cũ vẫn còn trong in-flight work | Chặn việc mới, chờ/hủy việc cũ rồi dọn owner |
| Bounded cache hết hạn nhưng dữ liệu bảo mật vẫn sai | TTL không bảo đảm invalidation ngay khi quyền đổi | Quyết định quyền theo request hoặc invalidate ngay |
| Chọn `ClassValue` nhưng cần `clearAll()`/TTL | API không cung cấp thao tác đó | Chọn owner/cache policy khác hoặc quản lý instance/generation |
| `ConcurrentHashMap` được coi là có giới hạn dung lượng | Thread-safe không đồng nghĩa bounded | Đặt eviction/memory budget riêng |
| Chỉ đo GC một lần để kết luận hết leak | GC/class unloading không được đảm bảo đúng lúc | Theo dõi GC roots, retained loaders qua nhiều chu kỳ |

Không kiểm tra an toàn cache chỉ bằng tỷ lệ hit. Cần đồng thời kiểm thử việc đổi schema/tenant policy, plugin reload, lỗi trong compute, đồng thời khi close và các tham chiếu còn sống sau khi dọn.

## 10. Bài tập tự kiểm tra

1. Thêm class `Receipt` trong chương trình và gọi `GLOBAL.computeIfAbsent(Receipt.class, ...)`. Kích thước global map thay đổi ra sao? Nó tự xóa entry khi hết dùng class không?
2. Bỏ dòng `PER_CLASS.remove(type)` và dự đoán `SCANS`. Khi thêm nhiều thread, điều gì khiến kết quả đếm compute khác đi?
3. Gọi `InspectionSession.fieldNames` sau `close()`: tại sao chương trình chủ động từ chối?
4. Thiết kế trường hợp hai loader A/B nạp class cùng binary name. Vì sao key `getName()` sai, còn `Class<?>` đủ để phân biệt?
5. Vẽ reference graph của `WeakHashMap<Class<?>, List<Field>>` và giải thích vì sao key có thể chưa được thu gom.
6. Một công cụ export schema có policy theo tenant và tự động reload plugin: chọn loại cache nào cho metadata thô, loại nào cho view theo user, và các điểm invalidation cụ thể.
7. Đề xuất bài stress test tải/dỡ plugin 100 vòng. Theo dõi loader cũ bằng heap/GC roots và cache entries; nếu số retained loader tăng, cần kiểm tra những owner nào ngoài cache?

**Tự đánh giá:** Khi một cache dùng `Class<?>`/`Method`/`Field` làm key hay value, bạn có xác định được **đơn vị sở hữu, hạn tồn tại, nơi invalidation, key có thể trỏ tới loader nào và liệu dữ liệu bị sai khi policy đổi** không? Nếu chưa, cache toàn JVM không giới hạn là một thiết kế chưa hoàn chỉnh.

## 11. Tài liệu chính thức

- [Java SE 17 — ClassValue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassValue.html) — `get`, `computeValue`, `remove` và cạnh tranh.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html) — `getDeclaredFields`, `getClassLoader` và class identity.
- [Java SE 17 — Field](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html) — declaring class, metadata.
- [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) — atomic map operations, `computeIfAbsent`.
- [Java SE 17 — WeakHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html) — weak keys, strong values và vòng tham chiếu.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) — định nghĩa class qua loader.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-007">An toàn nhiều luồng khi công bố hoặc sử dụng metadata đã cache</a>

<details>
<summary>Xem chi tiết</summary>

# An toàn nhiều luồng khi công bố và sử dụng metadata Reflection đã cache

## 1. Vấn đề: cache đúng nhưng công bố sai vẫn gây lỗi

Reflection cho phép tìm metadata tại runtime, chẳng hạn `Class<?>`, `Method`, `Constructor<?>`, `Field`, annotation và chữ ký generic. Việc tra cứu metadata lặp lại trong đường xử lý nóng có thể không cần thiết; framework thường **cache kết quả tra cứu** rồi dùng lại từ nhiều thread.

Tuy nhiên, cache có hai trách nhiệm độc lập:

1. **Công bố an toàn (*safe publication*):** thread khác phải nhìn thấy đối tượng cache **đã được khởi tạo đầy đủ**, không thấy cấu trúc đang xây dở hoặc giá trị thiếu cập nhật.
2. **Sử dụng an toàn (*safe concurrent use*):** sau khi nhận metadata từ cache, mọi thread phải tuân thủ quyền truy cập, kiểu receiver/đối số và quy tắc đồng bộ của **đối tượng đích**. Bản thân metadata dùng chung không khiến trạng thái nghiệp vụ được bảo vệ.

Ví dụ, một cache `Map<Class<?>, Method>` có thể trả về đúng phương thức cho tất cả thread. Nếu các thread dùng `Method.invoke(...)` trên cùng một đối tượng có field đếm không đồng bộ, kết quả cập nhật vẫn có thể sai vì data race trên **đối tượng đích**.

Điều cốt lõi là tách các tầng:

```text
            [Thread A]              [Thread B]
                |                       |
                +----- đọc cache --------+
                          |
            cache đã publish an toàn
                          |
                   Method / Field /
            MethodHandle / InvocationPlan
                          |
                kiểm tra quyền + kiểu
                          |
                   gọi trên receiver
                          |
                 state của receiver
         (cần bảo vệ riêng nếu có chia sẻ)
```

**Không nên** suy ra “metadata gần như bất biến” đồng nghĩa có thể đặt nó vào bất kỳ `HashMap` dùng chung nào rồi đọc/ghi tự do. Rủi ro có thể nằm ở cấu trúc cache, cách dựng value, bước publish, thao tác thay đổi metadata liên quan tới accessibility hoặc object mà metadata được dùng để gọi.

## 2. Safe publication trong Java Memory Model

Theo Java Memory Model (JMM), nếu một thread xây dữ liệu và thread khác đọc nó mà không có quan hệ **happens-before** thích hợp, việc chỉ gán tham chiếu không bảo đảm thread đọc nhìn thấy mọi write đã xảy ra trong quá trình xây dựng.

Ví dụ **không an toàn**, chỉ để chỉ ra lỗi:

```java
// Minh họa lỗi: KHÔNG dùng như cache nhiều luồng.
static Map<Class<?>, Method> cache = new HashMap<>();

static void register(Class<?> type, Method method) {
    cache.put(type, method); // ghi đồng thời không được đồng bộ
}

static Method lookup(Class<?> type) {
    return cache.get(type); // đọc đồng thời không được đồng bộ
}
```

`HashMap` không an toàn khi nhiều thread đọc/ghi đồng thời không được điều phối. Kể cả có một thread điền cache rồi thread khác bắt đầu, vẫn cần một cơ chế công bố đảm bảo visibility, ví dụ class initialization, đồng bộ lock hoặc primitive concurrent phù hợp. Không dùng `volatile HashMap` như cách “sửa” mọi cập nhật: `volatile` trên **tham chiếu** không biến các thao tác `put/get` của map thành thread-safe.

Những cơ chế công bố hữu ích:

| Cơ chế | Bảo đảm liên quan | Khi nào hợp lý |
| --- | --- | --- |
| Khởi tạo trường `static final` trong class initialization | JVM bảo đảm các thread thấy trạng thái class được khởi tạo đúng | Cache hoặc đối tượng bất biến dựng trong giai đoạn khởi tạo |
| `synchronized` hoặc `Lock` cùng giao thức | Unlock happens-before lock tiếp theo trên cùng monitor/lock | Cache có transaction hoặc nhiều thay đổi liên quan |
| Ghi/đọc một tham chiếu `volatile` | Ghi volatile happens-before đọc volatile tương ứng về sau | Công bố **snapshot bất biến** bằng cách thay toàn bộ tham chiếu |
| `ConcurrentHashMap` | API concurrent và bảo đảm visibility cho dữ liệu được gắn với key đúng cách | Cache tăng trưởng động, truy cập theo key |
| `ClassValue<T>` | Gắn value được tạo lười với từng `Class<?>`, cơ chế truy cập an toàn cho nhiều thread | Cache metadata theo class, đặc biệt môi trường nạp/gỡ class động |
| Immutable object với field `final` | Hỗ trợ final-field semantics khi dựng đúng, không để `this` thoát khỏi constructor | Value của cache không cần sửa sau khi xây |

**`final` không thay thế đồng bộ cho mọi cấu trúc mutable.** Ví dụ `final List<Method> methods` chỉ cố định tham chiếu; nếu danh sách thật là `ArrayList` rồi bị sửa đồng thời, vẫn có race. Ưu tiên tạo đầy đủ dữ liệu trong biến cục bộ, biến thành `List.copyOf(...)`/`Map.copyOf(...)`, đặt vào field `final` và publish qua primitive an toàn. Không để `this` hay collection nội bộ thoát ra trước khi constructor hoàn thành.

## 3. Chọn khóa cache đủ định danh metadata

Một khóa cache thiếu thông tin có thể trả metadata **sai** dù map hoàn toàn thread-safe.

- `Class<?>` phân biệt **kiểu runtime thực sự**, bao gồm danh tính class loader. Hai class loader có thể định nghĩa lớp có cùng tên nhị phân nhưng đó là hai `Class<?>` khác nhau. Dùng mỗi chuỗi `type.getName()` làm khóa toàn ứng dụng có thể trộn metadata của hai lớp không tương thích.
- Nếu cache **nhiều method trong cùng một class**, khóa cần đủ tên, kiểu tham số khai báo, static/instance khi phù hợp và chính sách xử lý overload, bridge, visibility. `getMethod("convert", String.class)` khác `getMethod("convert", Object.class)`.
- Field của superclass và field cùng tên của subclass có thể tồn tại đồng thời; chỉ lưu `"id"` mà bỏ declaring class hoặc chính sách tra cứu dễ gây nhầm.
- Với constructor, khóa gồm declaring class và các kiểu tham số. Constructor không kế thừa.
- Với `MethodHandle`, cần hiểu `MethodType` và quyền `Lookup` tại thời điểm tạo; không dùng handle có quyền rộng như một phần của public API thiếu kiểm soát.

Phạm vi cache nên gắn với **lifecycle và quyền sử dụng**: cache metadata public để đọc có thể dùng chung rộng rãi; handle private được tạo bằng `Lookup` có quyền đặc biệt cần giới hạn phạm vi chia sẻ. Đưa handle vào cache không làm mất các quy tắc JPMS, cũng không cấp quyền gọi cho mọi người dùng cuối của ứng dụng.

## 4. Ưu tiên `ClassValue<T>` khi cache theo class

`java.lang.ClassValue<T>` cung cấp cách gắn dữ liệu tính toán lười với một `Class<?>` mà không cần tự duy trì một `Map<Class<?>, T>` static lâu sống. API này đặc biệt phù hợp cho serializer, framework, proxy, plugin hoặc công cụ Reflection có nhiều class loader.

Mẫu ý tưởng:

```java
private static final ClassValue<Plan> PLANS = new ClassValue<>() {
    @Override
    protected Plan computeValue(Class<?> type) {
        return createPlan(type); // tạo đầy đủ trước khi công bố
    }
};

Plan plan = PLANS.get(SomeType.class);
```

Đoạn này là **mẫu minh họa**, chưa có khai báo `Plan`/`createPlan`; ví dụ hoàn chỉnh ở phần tiếp theo.

Những điểm quan trọng:

- `get(type)` cung cấp value gắn với class được yêu cầu. Không cần tự khóa một `HashMap` trong đường đọc.
- Với truy cập cạnh tranh, **`computeValue(type)` có thể được gọi nhiều lần**, dù cuối cùng một value được dùng làm mapping tương ứng. Vì vậy hàm xây cache phải an toàn khi chạy lặp lại và không được gây side effect nghiệp vụ kiểu “mỗi class đăng ký đúng một sự kiện” dựa đơn thuần vào số lần gọi.
- Không tạo và chia sẻ trước một `Plan` chưa hoàn tất, không publish tham chiếu tới map đang điền.
- `ClassValue.remove(type)` có thể được dùng để bỏ value khi chính sách cho phép tạo lại, nhưng không coi thao tác remove/get cạnh tranh là cơ chế cập nhật nguyên tử cho mọi thread hoặc hủy các reference `Plan` mà thread đã lấy trước đó.
- So với `ConcurrentHashMap<Class<?>, ...>` static, `ClassValue` thường phù hợp hơn với vòng đời class/class loader. Một map static giữ khóa `Class<?>` có thể **giữ class loader không được unload** nếu không có chính sách dọn dẹp.

## 5. Chương trình Java 17+ hoàn chỉnh: một plan bất biến, nhiều thread dùng chung

Chương trình xây `InvocationPlan` bất biến chứa `Method` public `greet(String)` của một kiểu, cache theo class bằng `ClassValue`. Nhiều tác vụ chạy đồng thời lấy plan, nhưng mỗi tác vụ dùng một receiver riêng; main in kết quả theo thứ tự Future được tạo để stdout xác định.

Tạo tệp **`SafeReflectionCacheDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SafeReflectionCacheDemo {
    public static final class Greeter {
        public String greet(String name) {
            return "Xin chào, " + name;
        }
    }

    private static final class InvocationPlan {
        private final Method method;

        private InvocationPlan(Method method) {
            this.method = method;
        }

        String call(Object receiver, String argument)
                throws ReflectiveOperationException {
            return (String) method.invoke(receiver, argument);
        }
    }

    private static final ClassValue<InvocationPlan> PLANS =
            new ClassValue<>() {
                @Override
                protected InvocationPlan computeValue(Class<?> type) {
                    try {
                        Method method =
                                type.getMethod("greet", String.class);
                        return new InvocationPlan(method);
                    } catch (NoSuchMethodException e) {
                        throw new IllegalArgumentException(
                                "Requires public greet(String): "
                                        + type.getName(), e);
                    }
                }
            };

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                final int number = i;
                futures.add(pool.submit(() -> {
                    InvocationPlan plan = PLANS.get(Greeter.class);
                    Greeter receiver = new Greeter();
                    return plan.call(receiver, "bạn " + number);
                }));
            }

            for (Future<String> future : futures) {
                System.out.println(future.get());
            }
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SafeReflectionCacheDemo.java
java SafeReflectionCacheDemo
```

Stdout:

```text
Xin chào, bạn 1
Xin chào, bạn 2
Xin chào, bạn 3
Xin chào, bạn 4
Xin chào, bạn 5
Xin chào, bạn 6
```

Tại sao ví dụ an toàn?

1. Trường `PLANS` được tạo trong quá trình **class initialization**. Mọi thread dùng cùng cơ chế `ClassValue` để lấy plan cho `Greeter.class`.
2. `computeValue` chỉ tìm `Method` public và tạo `InvocationPlan` hoàn chỉnh; không đăng ký side effect và vẫn đúng nếu bị tính lặp do cạnh tranh.
3. `InvocationPlan` có field `final Method`; không có setter hay collection bị sửa sau khi publish. Program không thay đổi `setAccessible` trên `Method` đã chia sẻ.
4. `ClassValue` chịu trách nhiệm phối hợp association; các thread không truy cập `HashMap` dùng chung chưa đồng bộ.
5. Mỗi task tạo `new Greeter()` của riêng mình. Nếu `Greeter.greet` sửa state chia sẻ, riêng cache an toàn vẫn **không** bảo đảm lời gọi nghiệp vụ an toàn.
6. Các task có thể hoàn tất không theo thứ tự, nhưng `futures` được duyệt theo **thứ tự submit**, nên các dòng stdout có thứ tự từ 1 đến 6.
7. `Future.get()` có cơ chế chờ/visibility: main chỉ xử lý kết quả sau khi tác vụ hoàn thành; không phải dùng sleep để “đợi dữ liệu hiện ra”.

Ví dụ dùng `getMethod` cho API public đã biết. Với method private, module khác, overload phức tạp hoặc quyền truy cập đặc biệt, thêm chính sách xử lý phù hợp; không suy rằng cache sẽ tự giải quyết vấn đề access.

## 6. Dùng `ConcurrentHashMap` khi cần cache theo nhiều khóa động

`ConcurrentHashMap` thường hợp lý nếu khóa không chỉ là `Class<?>`, chẳng hạn cặp class + tên method + danh sách kiểu tham số hoặc cấu hình operation riêng. Nó hỗ trợ truy cập concurrent và `computeIfAbsent` để liên kết value với key:

```java
private static final ConcurrentMap<Class<?>, Method> METHODS =
        new ConcurrentHashMap<>();

static Method resolve(Class<?> type) {
    return METHODS.computeIfAbsent(type, key -> {
        try {
            return key.getMethod("greet", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Requires greet(String): " + key.getName(), e);
        }
    });
}
```

Đây là **đoạn minh họa**, cần import `java.lang.reflect.Method`, `java.util.concurrent.ConcurrentMap` và `java.util.concurrent.ConcurrentHashMap`; đặt trong class có quyền dùng những member ấy.

`computeIfAbsent` thực hiện mapping cho key chưa có theo hợp đồng concurrent map; với một mapping thành công đang tồn tại, những thread khác có thể tái sử dụng. Tuy nhiên, **không thiết kế mapping function dựa vào giả định “chạy đúng một lần trong suốt vòng đời ứng dụng”**: khi entry bị xóa, compute ném exception, trả `null` hoặc cache được tái tạo, phép tính có thể thực hiện trở lại.

Mapping function nên:

- **Ngắn, độc lập, không có side effect nghiệp vụ** và trả đối tượng đã khởi tạo đầy đủ.
- Không cập nhật đệ quy/chồng chéo cùng map trong lúc tính; `ConcurrentHashMap` có thể ngăn recursive update và ném `IllegalStateException`.
- Không làm I/O hoặc công việc chặn quá lâu vì các thread khác có thể bị ảnh hưởng trong quá trình cập nhật mapping.
- Trả về value bất biến hoặc value có đồng bộ nội bộ. **Concurrent map chỉ bảo vệ cấu trúc map và cách publish**, không khiến một `ArrayList` bên trong value tự thread-safe.

**Không cache lỗi bằng cách lén trả `null`.** `ConcurrentHashMap` không chấp nhận key/value `null`; mapping function trả `null` thì mapping không được ghi. Nếu muốn negative cache cho lookup không tồn tại, hãy định nghĩa một kiểu kết quả biểu diễn rõ “không tìm thấy” (ví dụ value immutable chứa trạng thái) với chính sách thời hạn và invalidation.

Với map static chứa khóa `Class<?>`, phải quan tâm tới class loader leak. Nếu chủ yếu gắn dữ liệu với mỗi class, `ClassValue` thường là lựa chọn đơn giản và phù hợp vòng đời hơn.

## 7. Bẫy “map thread-safe nhưng value không thread-safe”

Ví dụ sai:

```java
// Minh họa lỗi: map concurrent nhưng value ArrayList vẫn mutable.
ConcurrentMap<Class<?>, List<Method>> cache =
        new ConcurrentHashMap<>();

List<Method> list = cache.computeIfAbsent(
        SomeType.class, type -> new ArrayList<>());
list.add(someMethod); // nhiều thread add cùng List -> race
```

Đoạn trên chỉ mô tả anti-pattern; `SomeType`, `someMethod` và imports chưa được khai báo. `ConcurrentHashMap` bảo vệ việc gán/get danh sách vào map, **không đồng bộ các lời gọi `ArrayList.add`** trên danh sách trả về.

Cách tốt hơn cho metadata chỉ đọc là **dựng xong rồi mới publish**:

```java
List<Method> collected = new ArrayList<>();
// ... thu thập Method vào collected chỉ trong thread khởi tạo ...
List<Method> immutableSnapshot = List.copyOf(collected);
// Publish immutableSnapshot qua ClassValue, ConcurrentHashMap,
// volatile hoặc primitive đồng bộ phù hợp.
```

Đây cũng là snippet, không phải chương trình độc lập. `List.copyOf` tạo danh sách không cho sửa cấu trúc và không để lộ `collected` làm backing list mutable. Nhưng nó chỉ cố định **danh sách các tham chiếu**: nếu phần tử của collection có trạng thái mutable bị sửa sau publish, phải đánh giá riêng.

Nếu có nhiều nhóm metadata liên quan, hãy dựng **một đối tượng snapshot** chứa đủ các map/list và publish đồng thời thông qua **một tham chiếu**. Publish từng map riêng lẻ dễ khiến reader thấy “method map version mới, field map version cũ” trong cùng một lượt xử lý.

## 8. Thay snapshot bằng `volatile` khi có cập nhật cấu hình

Một chương trình có thể xây catalog metadata mới rồi **thay toàn bộ snapshot**. Với mô hình nhiều reader, ít writer, việc đọc một trường `volatile` là lựa chọn đơn giản:

```java
private volatile List<Method> methods = List.of();

void reload(Class<?> type) {
    List<Method> fresh = List.copyOf(
            java.util.Arrays.asList(type.getMethods()));
    methods = fresh; // publish cả snapshot bằng một volatile write
}

List<Method> currentMethods() {
    return methods; // một volatile read
}
```

Đây là đoạn minh họa đặt trong một class; cần import `Method`. Trường `methods` phải được khai báo trên instance mà các thread chia sẻ có chủ đích.

Các điểm quan trọng:

- `volatile` trên **tham chiếu snapshot** giúp thread đọc thấy snapshot được công bố sau write volatile; không cần sửa trực tiếp list đã publish.
- Cả snapshot cũ lẫn snapshot mới đều hợp lệ với thread đã lấy tham chiếu trước lúc swap; việc thay tham chiếu không “thu hồi” dữ liệu trong tay reader.
- Nếu phải cập nhật nhiều field liên quan, hãy gói chúng vào một snapshot immutable rồi swap **một** tham chiếu để tránh phiên bản dữ liệu lẫn lộn.
- Nếu nhiều writer đồng thời tính snapshot bằng cách đọc trạng thái trước và cộng thêm dữ liệu, `volatile` **không** bảo vệ thao tác read-modify-write khỏi **lost update**. Dùng lock, `AtomicReference.compareAndSet` hoặc cơ chế version/generation thích hợp.
- Một thread lấy snapshot rồi dùng lâu có thể tiếp tục thấy dữ liệu cũ. Nếu yêu cầu nhất quán mạnh hơn (ví dụ yêu cầu mọi lần gọi sau reload đều dùng policy mới), cần xác định synchronization boundary, phiên bản và cách xử lý thao tác đang chạy.

Không dùng “double-checked locking” tự viết với tham chiếu mutable không `volatile`. Đối với cache lười theo class, `ClassValue` và `ConcurrentHashMap.computeIfAbsent` thường rõ ràng, ít lỗi hơn.

## 9. Sử dụng metadata chia sẻ: khi nào là an toàn?

### 9.1. `Method`, `Constructor` và `Field`

Metadata đã tìm được có thể được giữ để tái sử dụng. Việc nhiều thread gọi `Method.invoke` thông qua cùng một `Method` đã được cấu hình ổn định là tình huống thông thường. Tuy vậy:

- **Không thay đổi `setAccessible(true/false)` đồng thời** trên đối tượng `AccessibleObject` dùng chung trong khi thread khác đang dùng. Việc đổi trạng thái access là thay đổi có tác động đến quyền/kiểm tra truy cập; nên giải quyết quyền trước khi công bố hoặc dùng `MethodHandles.Lookup` hợp lệ với phạm vi quyền rõ ràng.
- `Field.get/set` truy cập **trạng thái field thực**. Nếu nhiều thread đọc/ghi cùng field của cùng object, phải xét `volatile`, lock hoặc VarHandle access mode; cache `Field` không bảo vệ giá trị được đọc/ghi.
- `Constructor.newInstance` tạo instance; constructor có thể thực hiện side effect hoặc sử dụng tài nguyên dùng chung. An toàn cache `Constructor` không bảo đảm constructor đích thread-safe.
- Metadata cho class được tải từ plugin có thể phụ thuộc class loader/module. Quyền access có thể khác ở caller khác; không công bố handle/metadata đặc quyền ra ngoài boundary truy cập.

### 9.2. `MethodHandle` và `VarHandle`

`MethodHandle` biểu diễn thao tác đã liên kết, có type; `VarHandle` biểu diễn một biến và các mode truy cập. Chúng phù hợp để chia sẻ theo mục đích đã xác định, nhưng:

- Receiver, array hoặc đối tượng mà handle thao tác **vẫn có thể mutable và chia sẻ**.
- `VarHandle.get`/`set` bình thường không đồng nghĩa `getVolatile`/`setVolatile` hay `compareAndSet`. Chọn access mode theo hợp đồng bộ nhớ và loại field.
- Không dùng handle có quyền private làm “đường vòng” bỏ xác thực quyền nghiệp vụ. Quyền tạo handle, quyền module và quyền của người dùng thao tác là các tầng khác nhau.
- Bind receiver vào handle (`bindTo`) có thể làm cache giữ mạnh object receiver và class loader, ảnh hưởng vòng đời, đồng thời có thể khiến nhiều thread vô tình tác động cùng instance. Khi có thể, cache handle không bind receiver và truyền receiver tại lời gọi.

### 9.3. Annotation và tập metadata đã tiền xử lý

Nếu đọc annotation và tạo sơ đồ mapping, lưu một **immutable descriptor** chứa tên, `MethodType`/kiểu field, danh sách tham số, flags và kết quả validation. Nếu chứa `Map`/`List`, dùng bản copy immutable; đừng trả trực tiếp mutable collection nội bộ cho caller.

Các kết quả `Annotation` runtime không nên bị hiểu là cấu hình nghiệp vụ bất biến theo thời gian nếu ứng dụng còn đọc file cấu hình hoặc policy động bên ngoài. Phân biệt metadata của class với **trạng thái cấu hình có thể reload**.

## 10. Cache lifecycle, refresh và class loader

Một cache có thể thread-safe nhưng vẫn sai vì giữ metadata **quá lâu** hoặc đặt khóa không đủ định danh.

- Dùng khóa `Class<?>` (hoặc `ClassValue`) thay vì chỉ tên class; cùng binary name trong hai class loader có thể là hai kiểu khác nhau.
- Cache static `ConcurrentHashMap<Class<?>, ...>` giữ strong reference tới key/value. Plugin class loader có thể không được unload nếu cache không xóa entry và value giữ class.
- Trường hợp chỉ cần giá trị gắn với class, `ClassValue` giảm gánh nặng quản lý vòng đời.
- Nếu metadata phụ thuộc **phiên bản cấu hình**, annotation scan policy, feature flags hoặc quyền tenant, khóa hoặc snapshot cần chứa version/policy tương ứng. `ClassValue` chỉ theo class không tự phân biệt các phiên bản policy.
- Nếu refresh, phải định nghĩa rõ thời điểm snapshot mới có hiệu lực, xử lý thread đang giữ snapshot cũ, và khi nào có thể loại bỏ tài nguyên đã gắn với snapshot.
- Không mặc định gọi `ClassValue.remove(type)` sẽ buộc mọi thread dừng dùng value cũ; remove chỉ tác động mapping mà `ClassValue` quản lý.

Đối với long-running server, quan sát kích thước cache, số class loader, hit/miss, thời gian khởi tạo metadata và tính đúng của invalidation. Đừng chọn cache toàn cục chỉ vì tra cứu reflection “có vẻ chậm”; hãy đo trên đường đi thực tế.

## 11. Những lỗi nhiều luồng thường gặp và cách sửa

| Sai lầm | Vì sao nguy hiểm | Hướng xử lý |
| --- | --- | --- |
| `HashMap` static với `put/get` từ nhiều thread | Race ở cấu trúc map và visibility | `ConcurrentHashMap`, `ClassValue` hoặc đồng bộ nhất quán |
| Xây object một phần rồi đưa vào cache | Reader thấy state chưa hoàn tất | Dựng local đầy đủ, publish value immutable |
| `final Map` nhưng vẫn gọi `put` đồng thời | `final` chỉ khóa tham chiếu, không khóa state | Immutable map hoặc map concurrent |
| `volatile Map` rồi cùng sửa `HashMap` | `volatile` không bảo vệ nội bộ map | Thay snapshot immutable, hoặc dùng concurrent map |
| `computeIfAbsent` vừa compute vừa cập nhật cùng map | Recursive update, blocking và side effect không xác định | Compute thuần, nhanh, tránh đụng map từ mapping function |
| Cho rằng `ClassValue.computeValue` chạy đúng một lần | Có thể tính cạnh tranh nhiều lần | Giữ phép tính idempotent, không side effect |
| Cache theo chuỗi tên class | Hai class loader định nghĩa cùng tên có thể bị trộn | Khóa bằng `Class<?>` hoặc `ClassValue` |
| Cache `Method` rồi đổi `setAccessible` trong khi dùng | Quyền/kiểm tra access bị thay đổi lúc chạy | Cấu hình trước publish; tránh toggle shared metadata |
| Metadata thread-safe ⇒ method đích thread-safe | State của receiver và side effect vẫn race | Đồng bộ object nghiệp vụ hoặc thiết kế bất biến |
| Cache handle đã `bindTo` receiver dài hạn | Giữ receiver và có thể giữ class loader; nhiều thread cùng sửa state | Cache handle độc lập rồi truyền receiver |
| Publish nhiều map ở nhiều thời điểm | Reader có thể thấy nửa phiên bản mới, nửa cũ | Gộp vào một immutable snapshot để atomic swap |
| Không dọn cache cho plugin unload | Strong reference giữ `Class<?>`/loader | `ClassValue` hoặc chính sách lifecycle/eviction |

Đừng lạm dụng khóa global bao trùm cả thao tác nghiệp vụ chỉ để bảo vệ cache. Nên tách **synchronization của việc tìm/bổ sung metadata** khỏi **synchronization của thao tác lên object đích**. Như vậy vừa rõ hợp đồng vừa tránh khóa lớn gây nghẽn.

## 12. Quy trình thiết kế và kiểm chứng

Với một cache metadata cần dùng chung nhiều thread:

1. **Xác định dữ liệu được cache**: chỉ `Class/Method/Field/Constructor`, hay cả handle, accessor, parser, map và policy.
2. **Xác định đơn vị nhận diện**: `Class<?>`, member signature, class loader, phiên bản cấu hình và quyền lookup.
3. **Tạo value hoàn chỉnh**: validate member, chuẩn hóa descriptor và sao chép collection thành immutable snapshot trước khi publish.
4. **Chọn cơ chế publish**: `ClassValue` cho per-class; `ConcurrentHashMap` cho nhiều khóa động; `static final`/class initialization cho value cố định; `volatile` cho swap snapshot nguyên khối.
5. **Tách an toàn của cache khỏi an toàn của receiver**: xác định thread nào có quyền đụng cùng đối tượng, field mutable và external side effect.
6. **Chỉ rõ chính sách thay đổi**: không thay `AccessibleObject` dùng chung tùy tiện; nếu có reload, xác định version và cách chuyển đổi.
7. **Kiểm tra dưới cạnh tranh**: nhiều thread cold-start cùng class, nhiều class loader, có cache miss/exception, thay snapshot trong khi đọc, kiểm tra stale value, và xác nhận không tự động giả định `computeValue` đúng một lần.
8. **Đo hiệu năng và vòng đời**: số lookup trùng, contention, allocation, memory retention và class-loader unload. Không phán đoán hiệu năng chỉ từ lựa chọn API.

### Câu hỏi tự kiểm tra

1. Vì sao `static final HashMap` vẫn không an toàn nếu có thread ghi vào map sau lúc khởi tạo?
2. `volatile` trên một tham chiếu tới `HashMap` có làm `HashMap.put`/`get` an toàn khi dùng đồng thời không?
3. Vì sao `ClassValue.computeValue` nên tránh side effect ngay cả khi đã có cache cho mỗi `Class<?>`?
4. Trong `SafeReflectionCacheDemo`, vì sao stdout ổn định dù các tác vụ chạy trên bốn worker thread?
5. Nếu giữ chung `Method` nhưng nhiều thread gọi trên cùng một counter chưa đồng bộ, cache có bảo vệ counter không?
6. `Class<?>` khác chuỗi `getName()` thế nào khi hai plugin class loader định nghĩa lớp cùng tên?
7. Vì sao `ConcurrentHashMap<Class<?>, ArrayList<Method>>` vẫn có thể lỗi nếu nhiều thread sửa cùng list?
8. Nếu hot reload một policy, việc thay ba `volatile Map` riêng lẻ có đảm bảo một reader luôn thấy cùng một version không?
9. Nếu cache `MethodHandle.bindTo(receiver)`, điều gì xảy ra với vòng đời receiver và an toàn truy cập state của nó?

**Ghi nhớ:** Một cache Reflection đúng đắn cần **value được xây hoàn chỉnh, được công bố với happens-before phù hợp, có khóa và vòng đời chính xác**, cùng chính sách sử dụng an toàn đối với receiver và trạng thái nghiệp vụ. `ClassValue` và `ConcurrentHashMap` xử lý tốt việc phối hợp truy cập metadata; chúng không thay thế cơ chế đồng bộ của đối tượng được gọi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-008">Không chia sẻ khả năng truy cập private ngoài phạm vi cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

# Không chia sẻ khả năng truy cập private ngoài phạm vi cần thiết

## 1. Vấn đề trong reflection và cache

Reflection cho phép chương trình tìm và gọi một thành viên lớp thông qua các đối tượng như `Method`, `Field` hoặc `Constructor`. Theo mặc định, API vẫn tuân thủ các kiểm tra truy cập của Java; các thành viên `private` không đương nhiên được phép gọi từ mọi nơi.

`java.lang.reflect.AccessibleObject` (lớp cha của `Method`, `Field`, `Constructor`) có các thao tác `setAccessible(true)` và `trySetAccessible()` để yêu cầu **bỏ qua một số kiểm tra truy cập ngôn ngữ Java** đối với đối tượng phản chiếu cụ thể, trong giới hạn mà Java Platform Module System (JPMS) cho phép. Khi trạng thái này bật, mã khác **được trao chính đối tượng `Method`/`Field` đó** có thể sử dụng khả năng truy cập mà bên sở hữu đã bật.

Vì thế, trong module **Cache và hiệu năng**, nguyên tắc thực hành là:

> **Chỉ giữ và chia sẻ metadata hoặc khả năng gọi đúng với những nơi thật sự cần. Không đưa đối tượng reflection đã được bật quyền private vào cache dùng chung hoặc API công khai.**

Ví dụ rủi ro: component chẩn đoán được phép truy cập phương thức private của một plugin để kiểm tra nội bộ. Nó gọi `method.trySetAccessible()` rồi lưu `Method` trong `public static Map<String, Method>`. Một component khác lấy được reference này có thể gọi `method.invoke(...)` mà không phải tự thiết lập quyền trên cùng đối tượng. Một cache được đặt tên là “tối ưu hiệu năng” lúc này vô tình trở thành nơi **phân phối quyền truy cập mạnh hơn mức cần thiết**.

Điểm cần phân biệt: quyền kiểm tra truy cập của Java/JPMS không phải cơ chế cô lập chống mọi mã độc chạy trong cùng JVM. Việc kiểm soát API, reference và quyền lookup vẫn quan trọng để bảo toàn **đóng gói**, tránh dùng nhầm và hạn chế năng lực được trao ngoài ý muốn.

## 2. Cơ chế quyền trên `AccessibleObject`

| API hoặc thao tác | Ý nghĩa | Lưu ý bảo toàn phạm vi |
| --- | --- | --- |
| `getDeclaredMethod`, `getDeclaredField` | Tra metadata kể cả thành viên không public được khai báo trực tiếp | Tìm thấy metadata không đồng nghĩa được phép gọi/đọc |
| `Method.invoke`, `Field.get/set` | Thực thi thao tác reflection | Tuân thủ kiểm tra truy cập trừ khi các kiểm tra đó được bỏ qua hợp lệ |
| `canAccess(receiver)` | Kiểm tra có truy cập được qua đối tượng reflection này với receiver thích hợp hay không | Chỉ đọc trạng thái, không mở quyền; `null` cho static member/constructor, instance thật cho instance member |
| `trySetAccessible()` | Cố bật trạng thái bỏ qua kiểm tra truy cập, trả `true` nếu thành công; `false` nếu không thể bật | **Làm thay đổi trạng thái** đối tượng khi thành công |
| `setAccessible(true)` | Yêu cầu bật trạng thái bỏ qua kiểm tra truy cập | Có thể ném `InaccessibleObjectException` nếu JPMS không cho phép |
| `setAccessible(false)` | Tắt trạng thái bỏ qua kiểm tra truy cập trên **đối tượng này** | Không phải cơ chế thu hồi mọi reference/handle đã phát tán |
| `MethodHandles.privateLookupIn` | Tạo `Lookup` có quyền truy cập phù hợp vào lớp đích khi đủ điều kiện | `Lookup` cũng là capability nhạy cảm; không cung cấp cho nơi không cần |

Một `Method` đã bật `setAccessible(true)` vẫn phải đáp ứng những điều kiện khác của phép gọi như receiver đúng lớp, số lượng/kiểu tham số tương thích. Bật accessible **không biến phương thức thành public trong bytecode**, không đổi modifiers và không cho quyền vô hạn với mọi package/module.

Với Java 9+, JPMS giới hạn deep reflection. Nếu package của module đích không được mở phù hợp cho module gọi, `trySetAccessible()` có thể trả `false`, còn `setAccessible(true)` có thể ném `InaccessibleObjectException`. Đừng coi `setAccessible(true)` là cách đi vòng mọi ranh giới module.

### Quyền gắn với đối tượng đã trao

Xét đoạn mã sau (minh họa hành vi cần tránh):

```java
// MÃ MINH HỌA ANTI-PATTERN, KHÔNG CHẠY ĐỘC LẬP:
// Method được tạo ở một thành phần được tin cậy:
Method privateMethod = Target.class.getDeclaredMethod("secret");
privateMethod.setAccessible(true);

// Sai phạm vi: đưa đối tượng đã bật quyền vào registry/cache công khai:
sharedRegistry.put("secretMethod", privateMethod);
```

Sau khi reference `privateMethod` thoát ra ngoài, bên nhận có thể sử dụng khả năng đang gắn trên **reference đó**. Việc “ẩn” field cache bằng `private` không đủ nếu một public getter hoặc callback lại trả thẳng đối tượng `Method`. Tương tự, trả một `MethodHandle` đã được tạo với lookup quyền private cũng trao khả năng gọi mà nó chứa.

## 3. Ví dụ Java 17+: mở quyền cục bộ rồi đóng lại

Ví dụ này cố tình có **hai top-level class** trong cùng một tệp: lớp `Ticket` có phương thức private; tiện ích `ScopedPrivateAccessDemo` thuộc **nest khác**, nên ban đầu không có quyền gọi phương thức private qua reflection. Chạy trên **class path/unnamed module** để việc mở quyền trong ví dụ được chấp nhận.

Lưu vào `ScopedPrivateAccessDemo.java`:

```java
import java.lang.reflect.Method;

public class ScopedPrivateAccessDemo {
    static final class Diagnostics {
        static boolean suffixMatches(Ticket ticket, String expected)
                throws ReflectiveOperationException {
            // Mỗi lần gọi tạo đối tượng Method riêng, không đưa vào cache chung.
            Method method = Ticket.class.getDeclaredMethod("lastFour");

            System.out.println("before=" + method.canAccess(ticket));
            if (!method.trySetAccessible()) {
                throw new IllegalStateException(
                        "Private access is not available");
            }

            try {
                System.out.println("during=" + method.canAccess(ticket));
                String suffix = (String) method.invoke(ticket);

                // Trả một kết quả nghiệp vụ tối thiểu, không trả Method.
                return suffix.equals(expected);
            } finally {
                method.setAccessible(false);
                System.out.println("after=" + method.canAccess(ticket));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Ticket ticket = new Ticket("DOC-4821");
        System.out.println("label=" + ticket.publicLabel());
        boolean matched = Diagnostics.suffixMatches(ticket, "4821");
        System.out.println("matched=" + matched);
    }
}

final class Ticket {
    private final String identifier;

    Ticket(String identifier) {
        this.identifier = identifier;
    }

    private String lastFour() {
        return identifier.substring(identifier.length() - 4);
    }

    String publicLabel() {
        return "ticket";
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -encoding UTF-8 ScopedPrivateAccessDemo.java
java ScopedPrivateAccessDemo
```

Stdout dự kiến:

```text
label=ticket
before=false
during=true
after=false
matched=true
```

Giải thích:

1. `method.canAccess(ticket)` ban đầu là `false` vì phương thức `Ticket.lastFour()` là private và code gọi thuộc một nest khác.
2. `trySetAccessible()` được phép trong ví dụ chạy trên class path; đối tượng `Method` cục bộ được bật khả năng gọi.
3. `invoke(ticket)` thực thi phương thức và trả chuỗi. Tiện ích trả **boolean** cho caller; không trả chuỗi nội bộ hoặc `Method`.
4. `finally` tắt accessible flag trên **chính đối tượng `Method` cục bộ** trước khi phương thức kết thúc, kể cả nếu `invoke` ném ngoại lệ.
5. `matched=true` chứng minh kết quả sau cùng là dữ liệu tối thiểu đã chủ đích cung cấp, không phải quyền phản chiếu.

**Giới hạn quan trọng:** ví dụ làm rõ vòng đời cục bộ; không khẳng định `setAccessible(false)` là một cơ chế thu hồi quyền tuyệt đối. Nếu trước đó đã phát tán đối tượng, một bản sao/reference khác hoặc handle độc lập vẫn có thể tồn tại. Cũng không nên bật/tắt accessible flag **trên cùng `Method` được dùng đồng thời bởi nhiều thread**: một thread tắt flag có thể làm thread khác bất ngờ thất bại khi gọi.

Trong ứng dụng thông thường, tốt hơn cả là **không dùng reflection vào private** nếu một phương thức public/package-private thích hợp có thể biểu đạt điều cần làm. Phương thức `publicLabel()` trong ví dụ thể hiện lối truy cập hẹp hơn cho nhu cầu phổ biến.

## 4. Thiết kế cache đúng phạm vi

Tối ưu reflection thường hướng tới giảm chi phí **tra cứu metadata lặp lại**. Nhưng cache càng chia sẻ rộng, càng nhiều code có thể giữ reference đến đối tượng truy cập nhạy cảm.

So sánh các thiết kế:

| Thiết kế | Đánh giá |
| --- | --- |
| Cache toàn cục ánh xạ tên → `Method` đã bật accessible và cho mọi component lấy ra | **Tránh**: phát tán reference được cấp khả năng private |
| Cache metadata của public method để phục vụ các thao tác public | Phù hợp hơn nếu có nhu cầu thực tế và quản lý class loader |
| Cache `Method` private bên trong một helper được tin cậy; chỉ xuất API nghiệp vụ hẹp | Có thể chấp nhận khi thật sự cần hiệu năng, nhưng phải kiểm soát caller, luồng sử dụng và vòng đời cache |
| Tìm private method cục bộ mỗi lần, trả kết quả đã kiểm tra | Đơn giản, dễ kiểm toán; đổi lại chi phí lookup lặp lại |
| Dùng `MethodHandle` quyền private rồi công khai handle ra ngoài | Cũng là phát tán khả năng gọi; không phải giải pháp an toàn tự động |

Nếu phương thức thực sự cần được cache, phạm vi nên là **component sở hữu công việc**, ví dụ:

- Đặt cache ở class/service nội bộ, không có getter trả `Method` hoặc `Lookup`.
- Chỉ trả về giá trị đã giới hạn qua method nghiệp vụ như `boolean matches(...)` hoặc DTO không chứa nội bộ nhạy cảm.
- Không dùng khóa chuỗi tên phương thức một cách mơ hồ; xem xét declaring `Class<?>`, tên, danh sách tham số, static/instance và module/loader khi cần.
- Lựa chọn chính sách eviction/lifecycle phù hợp vì cache giữ `Method`/`Class` lâu có thể giữ tham chiếu đến **class loader của plugin**, làm cản trở việc dỡ phiên bản plugin.
- Dùng cơ chế cache thread-safe (`ConcurrentHashMap`, `ClassValue` khi phù hợp) để tránh thay đổi cấu trúc cache đồng thời; **thread-safe map không biến trạng thái accessible của phần tử thành thread-safe**.

Không nên quyết định cache private reflection chỉ vì “reflection chậm”. Benchmark đúng workload trước; chi phí lookup, invoke, boxing và tạo adapter phụ thuộc JDK, kiểu gọi và tần suất. Nếu logic có thể dùng lời gọi Java trực tiếp, đó thường là đường đơn giản và dễ bảo trì nhất.

### Không bật/tắt quyền trên một đối tượng chia sẻ giữa nhiều thread

Giả sử `Method cached` đã được chia sẻ, một thread làm:

```java
// Thread A — anti-pattern:
cached.setAccessible(true);
try {
    cached.invoke(target);
} finally {
    cached.setAccessible(false);
}
```

Trong lúc đó thread B cũng sử dụng `cached`. Vì flag là trạng thái trên đối tượng reflection, thread B có thể quan sát nó ở những thời điểm không dự kiến. `finally` chỉ bảo đảm thread A **cố đóng sau thao tác**, không tạo một đoạn truy cập nguyên tử cho mọi thread.

Nếu phải dùng private access trong code đồng thời, hãy tránh chia sẻ đối tượng đang bị toggle, cân nhắc tạo đối tượng cục bộ hoặc thiết kế một component sở hữu quyền ổn định và không để capability thoát ra; kiểm tra an toàn luồng và các invariant nghiệp vụ riêng. Khóa đồng bộ chỉ có ý nghĩa nếu **mọi caller** truy cập cùng trạng thái đều dùng đúng khóa, điều mà một reference đã rò rỉ không bảo đảm.

## 5. `MethodHandles.Lookup` cũng là một capability

`MethodHandles.Lookup` đại diện ngữ cảnh truy cập của code tạo ra nó. Đôi khi framework cần `MethodHandles.privateLookupIn(Target.class, callerLookup)` để tạo lookup có quyền cần thiết cho target, nếu điều kiện module/readability/openness được đáp ứng. Sau đó:

```java
// Đoạn minh họa, cần import và điều kiện quyền/module phù hợp:
MethodHandles.Lookup privateLookup =
        MethodHandles.privateLookupIn(Target.class,
                MethodHandles.lookup());
MethodHandle privateHandle = privateLookup.findVirtual(
        Target.class,
        "hiddenAction",
        MethodType.methodType(void.class));
```

**Không tự biến thành an toàn chỉ vì đã chuyển từ `Method` sang `MethodHandle`**:

- `Lookup` có quyền private nên chỉ đưa vào những đoạn code được tin cậy và cần quyền đó.
- `MethodHandle` trỏ tới private operation cũng trao khả năng gọi; thành phần nhận handle có thể gọi mà không phải tra cứu quyền lại như lúc tạo.
- Không cache private lookup/handle ở registry dùng chung cho những plugin không tin cậy.
- Ưu tiên đóng gói trong helper, chỉ xuất các thao tác đã được kiểm tra và đầu ra được giới hạn.

Java modules vẫn có quyền quyết định package nào mở cho deep reflection. Không dùng `--add-opens` toàn cục chỉ để làm tắt ngoại lệ; thay đổi mở module có thể cấp quyền rộng cho mã khác trong module nhận quyền.

## 6. Thiết kế API thay thế để tránh phải phân phối private access

Một số lựa chọn thường tốt hơn:

**API công khai tối thiểu.** Nếu các bên chỉ cần câu trả lời “mã có hợp lệ không?”, tạo `boolean isValidCode(String input)` thay vì trả `Field` chứa mã.

**Interface dành cho tích hợp.** Plugin cung cấp interface public cho chức năng được phép dùng, thay vì cho host đi tìm member private theo chuỗi tên. Điều này có hợp đồng biên dịch và ít phụ thuộc cấu trúc nội bộ.

**Adapter hoặc service sở hữu quyền.** Nếu framework buộc phải reflection vào private để tương thích legacy, đặt quyền ở một adapter nội bộ; caller truyền yêu cầu đã kiểm tra và chỉ nhận dữ liệu cần thiết.

**Thay thế cho public API và cache metadata.** Đối với phương thức public ổn định, nếu cần, giữ metadata trong cache riêng mà không bật accessible. Vẫn nên xem xét loader lifecycle.

**Module openness ở mức tối thiểu.** Khi plugin là named module, chỉ mở package cần thiết tới module chẩn đoán cụ thể (`opens package.to.inspect to module.diagnostics;`) thay vì `open module` hoặc `--add-opens ...=ALL-UNNAMED` trên toàn bộ ứng dụng. “Mở package” là quyền **ở cấp package/module**, không giới hạn riêng một hàm; vì thế càng cần kiểm soát API component nhận quyền.

## 7. Kiểm tra và xử lý ngoại lệ

Các vấn đề phổ biến khi xây capability nội bộ:

| Tình huống | Nguyên nhân/cách xử lý |
| --- | --- |
| `getDeclaredMethod` ném `NoSuchMethodException` | Kiểm tra đúng tên và danh sách kiểu tham số (primitive khác wrapper), overload và bản class đang nạp |
| `trySetAccessible()` trả `false` | JPMS hoặc quy tắc truy cập không cho phép; từ chối hành động và sửa cấu hình module theo quyền cần thiết |
| `setAccessible(true)` ném `InaccessibleObjectException` | Đích không mở package phù hợp; không retry mù quáng hoặc mở cả module |
| `Method.invoke` ném `IllegalAccessException` | Access check còn hiệu lực hoặc context không đủ quyền; không giả định mở quyền đã thành công |
| `InvocationTargetException` | Mã đích thực sự ném ngoại lệ; xem `getCause()` để chẩn đoán, không đổ lỗi cho accessibility |
| `IllegalArgumentException` khi invoke | Receiver hoặc số/kiểu đối số sai; mở quyền không khắc phục |
| Sau khi unload plugin, class loader vẫn còn tham chiếu | Kiểm tra cache chứa `Class`, `Method`, `Lookup`, handle hay lambda liên quan plugin |
| Test chạy được trên class path nhưng lỗi trên module path | Kiểm tra `opens`, `exports`, module caller/target và class loader |

Không ghi secret, token hay toàn bộ đối số private vào log khi reflection thất bại. Chỉ ghi đủ loại exception, member signature, defining class, module/loader và bước thất bại theo quy tắc bảo vệ dữ liệu của ứng dụng.

## 8. Checklist review dành cho cache reflection

Trước khi cho một cache/reflection helper vào production, kiểm tra:

1. Thành viên private có thật sự cần truy cập không? Có API public/package-private rõ ràng hơn không?
2. Caller nào được gọi helper? Có kiểm tra điều kiện nghiệp vụ trước khi mở quyền?
3. Có đối tượng `Method`/`Field`, `Lookup` hoặc `MethodHandle` nhạy cảm nào thoát ra qua getter, callback, map, event, log hay object được trả về không?
4. Cache nằm đúng nơi sở hữu quyền, hay đang được chia sẻ cho toàn ứng dụng/plugin?
5. Nếu một đối tượng accessible có thể dùng đồng thời, code có dựa vào thao tác bật/tắt flag dễ tranh chấp không?
6. Cache có vòng đời phù hợp với class loader/redeploy/hot reload không?
7. Quyền module có thật sự chỉ mở những package/module cần thiết không?
8. Có test cả trường hợp được phép và từ chối truy cập, sai receiver/đối số, và ngoại lệ từ target không?
9. Đã đo hiệu năng trước khi đánh đổi phạm vi cache lấy tốc độ chưa?

## 9. Câu hỏi tự kiểm tra

**Câu 1.** Nếu một method private được bật `trySetAccessible()`, rồi `Method` đó đưa vào cache công khai, rủi ro chính là gì?

**Đáp án:** Các thành phần lấy được **reference tới chính `Method` đã bật quyền** có thể sử dụng capability truy cập private đó; biện pháp là giữ nó trong component đáng tin cậy và chỉ xuất API nghiệp vụ hẹp.

**Câu 2.** Gọi `setAccessible(false)` trên Method cục bộ có giúp thu hồi mọi handle đã phát tán không?

**Đáp án:** Không. Nó chỉ chỉnh flag trên đối tượng reflection tương ứng, không vô hiệu hóa các capability độc lập khác hoặc xóa mọi reference đã trao.

**Câu 3.** Vì sao dùng `ConcurrentHashMap` không giải quyết toàn bộ rủi ro?

**Đáp án:** Map thread-safe chỉ bảo vệ thao tác map; giá trị `Method` bên trong vẫn có accessible flag riêng và có thể bị thay đổi đồng thời.

**Câu 4.** `MethodHandles.privateLookupIn` có thay được quản lý phạm vi quyền không?

**Đáp án:** Không. `Lookup` và các private handle được tạo ra cũng mang capability; chỉ trao trong phạm vi cần thiết.

**Câu 5.** Có nên bật `--add-opens ...=ALL-UNNAMED` mỗi khi reflection bị từ chối?

**Đáp án:** Không. Trước hết phải xác định ai cần truy cập, package nào cần mở và liệu API public/adapter có giải quyết được không.

**Ghi nhớ:** **Cache metadata để tiết kiệm công tra cứu, nhưng giữ capability truy cập private ở đúng nơi sở hữu và đúng thời hạn.** Với reflection, quyền nằm cả ở **cách tạo đối tượng**, **trạng thái accessible** và **reference bạn trao cho mã khác**; tối ưu hiệu năng không được làm mở rộng phạm vi truy cập ngoài nhu cầu.

Tham khảo: [`AccessibleObject` Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html), [`Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html), [`MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html), [`Module`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-009">Benchmark với warmup, lời gọi trực tiếp và workload thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Benchmark Reflection với warmup, lời gọi trực tiếp và workload thực tế

## 1. Mục tiêu

Khi xây dựng công cụ dùng Java Reflection, một câu hỏi thường gặp là: **gọi phương thức qua `Method.invoke` có thực sự ảnh hưởng đến hiệu năng ứng dụng không?** Muốn trả lời chính xác, cần so sánh với lời gọi trực tiếp trong những điều kiện tương đương, tách chi phí lookup metadata khỏi chi phí invocation, làm nóng JVM và chạy công việc đủ giống production.

Sau bài này, bạn sẽ biết cách:

- Thiết kế baseline **direct invocation** và phép đo **cached reflective invocation** có cùng đầu vào, cùng logic và đầu ra.
- Giải thích vai trò của warmup, JIT, inlining, dead-code elimination, GC và tiered compilation.
- Phân biệt bài toán đo overhead thuần của lời gọi với bài toán đo thời gian của một workload thực tế.
- Viết benchmark minh họa độc lập bằng Java 17+, rồi chuyển sang **JMH** để lấy số đo dùng cho quyết định kỹ thuật.
- Đọc `ns/op`, throughput, độ biến động, fork và kết quả benchmark mà không rút ra kết luận quá mức.
- Quyết định khi nào việc cache `Method` có giá trị; khi nào cần profile và benchmark cả pipeline.

Bài học chỉ tập trung vào benchmark và thiết kế đo hiệu năng. Các API reflection để tìm method và gọi method được sử dụng làm công cụ trong ví dụ.

## 2. Trước tiên phải xác định chính xác đang đo gì

Một request trong công cụ reflection có thể đi theo quy trình:

```text
Nhận input -> parse -> xác định class/method -> chuyển đổi đối số
           -> gọi method -> xử lý kết quả -> trả response
```

Các phép đo khác nhau trả lời **các câu hỏi khác nhau**:

| Kịch bản | Nội dung đo | Khi nào hữu ích |
| --- | --- | --- |
| **Direct** | `Target.operation(input)` | Baseline của cùng thao tác nghiệp vụ |
| **Cached reflection** | `cachedMethod.invoke(target, input)` | Đo đường gọi reflective khi `Method` đã được tìm trước |
| **Lookup + invocation** | `getMethod(...); invoke(...)` trong mỗi lần gọi | Đo tổng chi phí tra cứu và gọi khi không cache |
| **End-to-end** | Parse, validation, dispatch, method invocation, output | Đo tác động đến trải nghiệm thực tế hoặc throughput |

Nếu so sánh direct với `getMethod(...).invoke(...)` trong cùng vòng lặp, số đo phản ánh **cả lookup lẫn invoke**. Không được diễn giải toàn bộ chênh lệch thành chi phí riêng của `Method.invoke`.

`Method.invoke` dùng kiểu `Object` cho đầu vào/đầu ra, có thể kéo theo boxing, unboxing, kiểm tra hoặc chuyển đổi primitive hợp lệ. JIT có thể tối ưu khác nhau giữa các đường gọi. Vì vậy không thể khẳng định “Reflection luôn chậm X lần” nếu chưa chỉ rõ workload, JVM và cấu hình.

Một cách nhìn hữu ích:

```text
Tổng chi phí công cụ ≈ parse + lookup/cache + convert + dispatch + target + response
```

Đây là mô hình phân tích chứ không phải phương trình chính xác trong hệ thống nhiều luồng. Các phần có thể tương tác qua GC, CPU cache, contention và scheduler.

## 3. Warmup: lý do số đo ban đầu dễ sai

JVM có thể bắt đầu thực thi bytecode bằng interpreter hoặc mã biên dịch ít tối ưu, sau đó thu thập profile và dùng JIT để biên dịch những đường code nóng. JIT có thể inline, loại bỏ phép tính thừa, chuyên biệt hóa kiểu tại call site, hoặc deoptimize khi giả định trước đó không còn đúng.

Do vậy, thời gian ở vài lời gọi đầu thường khác so với trạng thái ổn định (*steady state*).

Quy trình minh họa:

```text
Khởi tạo dữ liệu và cache Method
    |
    v
Warmup direct và reflection (cùng phân bố input)
    |
    v
Đo nhiều lượt, cân bằng thứ tự chạy
    |
    v
Kiểm tra checksum / kết quả đúng
    |
    v
Phân tích kết quả, độ biến động và giới hạn phép đo
```

Các nguyên tắc:

1. **Warmup cả hai đường gọi.** Không làm nóng direct 100.000 lượt nhưng đo reflection ngay từ lần đầu.
2. **Dùng input đại diện.** Warmup bằng đối tượng/method khác có thể không làm nóng đúng đường code cần đo.
3. **Không xem số vòng warmup cố định là bảo đảm.** JVM có thể tiếp tục JIT/GC sau warmup; cần xem tính ổn định qua nhiều lần đo.
4. **Tách cold start khỏi steady state.** Ứng dụng CLI ngắn hạn có thể quan tâm thời gian khởi động, lookup lần đầu; server chạy lâu có thể quan tâm throughput đã warm.
5. **Không đo riêng một lượt rồi kết luận.** Sự khác biệt nhỏ có thể chìm trong nhiễu hệ điều hành, lịch CPU hoặc GC.

## 4. Benchmark minh họa Java 17+: direct và cached Method.invoke

Ví dụ độc lập sử dụng hai workload:

- **TINY:** `plusOne(int)` chỉ cộng 1, để thấy chi phí đường gọi có thể nổi bật khi target quá nhỏ.
- **REALISTIC:** `parseOrder(String)` tách bản ghi, trim/uppercase vùng, parse số lượng và giá, rồi tính kết quả. Đây là workload CPU/chuỗi đơn giản mang tính đại diện hơn, dù vẫn chưa thay thế tác vụ production.

Cả hai dùng cùng dữ liệu cho direct/reflection. `getMethod` được gọi **trước** khi đo. Có checksum để xác nhận hai đường cho cùng kết quả. Benchmark chạy warmup mỗi đường, lấy nhiều mẫu và đảo thứ tự chạy giữa các mẫu.

Tạo tệp `ReflectionBenchmarkDemo.java`:

```java
import java.lang.reflect.Method;
import java.util.Locale;

public final class ReflectionBenchmarkDemo {
    private static final String[] ROWS = {
            "VN|2|350", "US|4|150", "JP|1|800", "DE|5|99"
    };
    private static final int WARMUP_CALLS = 20_000;
    private static final int CALLS_PER_SAMPLE = 60_000;
    private static final int SAMPLES = 5;
    private static volatile long sink;

    public static int plusOne(int value) {
        return value + 1;
    }

    public static long parseOrder(String row) {
        String[] fields = row.split("\\|", -1);
        String region = fields[0].trim().toUpperCase(Locale.ROOT);
        int quantity = Integer.parseInt(fields[1]);
        long cents = Long.parseLong(fields[2]);
        return quantity * cents + region.length() * 7L;
    }

    private static long run(Method method, boolean realistic,
                            boolean reflective, int calls) throws Exception {
        long checksum = 0;
        for (int i = 0; i < calls; i++) {
            if (realistic) {
                String row = ROWS[i % ROWS.length];
                long result = reflective
                        ? (Long) method.invoke(null, row)
                        : parseOrder(row);
                checksum += result;
            } else {
                int value = i * 31 + 5;
                int result = reflective
                        ? (Integer) method.invoke(null, value)
                        : plusOne(value);
                checksum += result;
            }
        }
        return checksum;
    }

    private static double timed(Method method, boolean realistic,
                                boolean reflective) throws Exception {
        long start = System.nanoTime();
        long checksum = run(method, realistic, reflective, CALLS_PER_SAMPLE);
        long elapsed = System.nanoTime() - start;
        sink = checksum;
        return (double) elapsed / CALLS_PER_SAMPLE;
    }

    private static void compare(Method method, boolean realistic) throws Exception {
        String label = realistic ? "REALISTIC" : "TINY";
        long direct = run(method, realistic, false, CALLS_PER_SAMPLE);
        long reflection = run(method, realistic, true, CALLS_PER_SAMPLE);
        if (direct != reflection) {
            throw new AssertionError("Different results: " + label);
        }
        sink = direct;
        for (int warm = 0; warm < 8; warm++) {
            sink = run(method, realistic, false, WARMUP_CALLS);
            sink = run(method, realistic, true, WARMUP_CALLS);
        }

        System.out.println(label + ": same checksum = true");
        for (int sample = 0; sample < SAMPLES; sample++) {
            double directNs;
            double reflectionNs;
            if (sample % 2 == 0) {
                directNs = timed(method, realistic, false);
                reflectionNs = timed(method, realistic, true);
            } else {
                reflectionNs = timed(method, realistic, true);
                directNs = timed(method, realistic, false);
            }
            System.out.printf(Locale.ROOT,
                    "sample %d: direct %.1f ns/op, reflection %.1f ns/op%n",
                    sample + 1, directNs, reflectionNs);
        }
    }

    public static void main(String[] args) throws Exception {
        Method tiny = ReflectionBenchmarkDemo.class.getMethod("plusOne", int.class);
        Method realistic = ReflectionBenchmarkDemo.class.getMethod(
                "parseOrder", String.class);
        compare(tiny, false);
        compare(realistic, true);
    }
}
```

Biên dịch và chạy:

```bash
javac ReflectionBenchmarkDemo.java
java ReflectionBenchmarkDemo
```

Chương trình đã được chạy thực tế với JDK. Output có hai dòng xác thực kết quả:

```text
TINY: same checksum = true
REALISTIC: same checksum = true
```

Hai dòng đó **không liền nhau trong stdout**: sau dòng `TINY` có năm dòng `sample ...` rồi mới đến dòng `REALISTIC` và năm dòng `sample ...` tiếp theo. Mỗi dòng mẫu chứa `direct ... ns/op` và `reflection ... ns/op`. Các con số đo **không có stdout cố định**, phụ thuộc CPU, phiên bản JVM, tải máy và tối ưu JIT.

Trong một lần chạy thử, direct TINY có thể được báo thấp hơn 1 ns/op. Đó là tín hiệu phải đánh giá **mức độ tối ưu của JIT đối với benchmark**, không thể khẳng định một lần gọi ứng dụng thực tế nhất định chỉ tốn từng ấy thời gian.

### Giải thích code

- Hai `Method` được lấy trước `compare`, nên đây là **cached invocation**.
- `run` dùng cùng đối số cho mỗi cặp direct/reflection. Phương thức static sử dụng `method.invoke(null, ...)` vì không có receiver instance.
- Reflection trả `Object`; phép ép `(Integer)`/`(Long)` unbox kết quả đúng primitive.
- Kiểm tra checksum trước đo xác nhận hai đường cho cùng kết quả. Đo nhanh hơn nhưng tính sai là vô nghĩa.
- Warmup lặp lại cả hai đường, trước khi vào năm mẫu đo.
- Thứ tự direct/reflection thay đổi giữa mẫu chẵn và lẻ để giảm một phần thiên lệch thứ tự.
- `System.nanoTime()` đo **elapsed time**, rồi chia cho số lần gọi để ước lượng `ns/op`.
- `volatile sink` làm kết quả có thể quan sát được, nhưng vẫn không bảo đảm loại bỏ mọi vấn đề tối ưu.

**Giới hạn:** chương trình dùng một JVM process, vòng lặp riêng và số lượng input nhỏ; không tạo fork độc lập hay có công cụ kiểm soát JIT đầy đủ. Nó là ví dụ có thể chạy để học, **không phải số benchmark đủ tin cậy cho quyết định tối ưu sản phẩm**.

## 5. Những điều dễ làm sai phép đo

### 5.1. Inlining, constant folding và dead-code elimination

Phép cộng đơn giản `plusOne(x)` có thể bị JIT inline, đơn giản hóa hoặc tối ưu toàn vòng lặp. Nếu kết quả không được sử dụng, JVM có thể loại bỏ nhiều công việc. Dù có `volatile sink` sau vòng lặp, trình biên dịch vẫn có quyền thực hiện nhiều biến đổi bảo toàn kết quả quan sát được.

Đặc biệt, so sánh direct với reflection trên target cực nhỏ dễ phóng đại chênh lệch bởi đường direct tối ưu vượt xa mã nguồn tưởng tượng ban đầu. Điều đó không có nghĩa số đo “lỗi” theo nghĩa công cụ đo sai thời gian; nó có nghĩa **mã máy đang được đo không nhất thiết đại diện cho production**.

JMH cho phép trả về giá trị của `@Benchmark` hoặc dùng `Blackhole` để tiêu thụ output; đây là một trong nhiều biện pháp chống dead-code elimination. Tuy nhiên, chính input và cấu trúc workload vẫn phải được thiết kế đúng.

### 5.2. GC, allocation và boxing

`Method.invoke` dùng API nhận/trả `Object` nên trường hợp primitive có thể liên quan boxing/unboxing. Một số phân bổ có thể bị JVM tối ưu, nhưng vẫn cần phân biệt trường hợp tham số primitive với đối tượng.

Workload chuỗi có thể tạo mảng từ `split` và đối tượng parse/normalize; khi target làm nhiều việc như thế, chi phí gọi reflective có thể trở thành tỷ lệ nhỏ. GC có thể làm một mẫu bị chậm đột biến. Nếu đối tượng được cấp phát nhiều, nên theo dõi lượng allocation/GC song song với `ns/op`.

### 5.3. Nhiễu hệ thống và số lượng mẫu

Một phép đo microbenchmark quá ngắn dễ bị ảnh hưởng bởi chi phí gọi đồng hồ và scheduling. Một lần chạy dễ bị lệch do JIT hoặc nhiệt độ CPU. Vì vậy:

- Thu thập **nhiều iteration và nhiều fork**.
- Đo trong môi trường tương đối ổn định, ghi JDK vendor/version, flags, CPU, OS, heap.
- Không đặt logging/format output vào vùng đo trừ khi mục tiêu đo chính chúng.
- Giữ đầu vào tương đương giữa đường direct và reflection.
- Xem phân bố, độ lệch và error thay vì chỉ lấy một con số đẹp.

## 6. Benchmark nghiêm túc bằng JMH

[JMH (Java Microbenchmark Harness)](https://openjdk.org/projects/code-tools/jmh/) của OpenJDK có cơ chế warmup, measurement, JVM fork, benchmark mode và quản lý tiêu thụ kết quả phù hợp hơn phép đo thủ công. JMH không tự làm workload trở nên đại diện; nó là hạ tầng đo.

Một cách bắt đầu là tạo dự án Maven bằng JMH archetype với **phiên bản ví dụ 1.37** (cần Maven và khả năng tải dependencies):

```bash
mvn archetype:generate -DinteractiveMode=false -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype -DarchetypeVersion=1.37 -DgroupId=demo -DartifactId=reflection-bench -Dversion=1.0
```

Lưu class sau thành `reflection-bench/src/main/java/demo/ReflectionJmh.java`. Cấu hình project build với JDK 17 trở lên.

```java
package demo;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ReflectionJmh {
    private final String[] rows = {
            "VN|2|350", "US|4|150", "JP|1|800", "DE|5|99"
    };
    private int index;
    private Method tinyMethod;
    private Method realisticMethod;

    public static int plusOne(int x) {
        return x + 1;
    }

    public static long parseOrder(String row) {
        String[] fields = row.split("\\|", -1);
        String region = fields[0].trim().toUpperCase(Locale.ROOT);
        int quantity = Integer.parseInt(fields[1]);
        long cents = Long.parseLong(fields[2]);
        return quantity * cents + region.length() * 7L;
    }

    @Setup(Level.Trial)
    public void setUp() throws ReflectiveOperationException {
        tinyMethod = ReflectionJmh.class.getMethod("plusOne", int.class);
        realisticMethod = ReflectionJmh.class.getMethod(
                "parseOrder", String.class);
    }

    private String nextRow() {
        return rows[(index++) & 3];
    }

    @Benchmark
    public int directTiny() {
        return plusOne(index++);
    }

    @Benchmark
    public int reflectiveTiny() throws ReflectiveOperationException {
        return (Integer) tinyMethod.invoke(null, index++);
    }

    @Benchmark
    public long directRealistic() {
        return parseOrder(nextRow());
    }

    @Benchmark
    public long reflectiveRealistic() throws ReflectiveOperationException {
        return (Long) realisticMethod.invoke(null, nextRow());
    }
}
```

Sau khi tạo class và có Maven/JMH dependencies từ archetype:

```bash
cd reflection-bench
mvn clean verify
java -jar target/benchmarks.jar ".*ReflectionJmh.*" -rf json -rff results.json
```

Lệnh ghi kết quả vào `results.json`; không có bảng số cố định vì chạy trên JVM/máy khác nhau cho kết quả khác nhau.

**Ý nghĩa thiết kế:**

- `@Setup(Level.Trial)` cache `Method` ngoài phần đo; đây là phép đo **cached reflection**, không tính chi phí lookup.
- `@State(Scope.Thread)` cung cấp state riêng theo thread; `index` thay đổi theo lần gọi để tránh dùng một literal bất biến làm input duy nhất.
- `@Warmup`, `@Measurement` và `@Fork` cấu hình warmup, số lượt đo và số JVM fork. Có thể tăng khi độ biến động lớn.
- JMH sử dụng kết quả trả về của `@Benchmark` để hạn chế những tối ưu làm phép đo biến mất.
- Hai benchmark tiny so sánh lời gọi xử lý primitive; hai benchmark realistic cùng chạy `parseOrder` trên bộ input tương đương.
- `@BenchmarkMode(Mode.AverageTime)` kết hợp `@OutputTimeUnit(TimeUnit.NANOSECONDS)` cho score kiểu `ns/op`.

Ví dụ JMH này minh họa cách chuẩn bị một phép so sánh độc lập. Trước khi dùng cho quyết định thật, cần xác minh phân bố input gần production, không có thay đổi state ngoài dự kiến, và các target không bị JIT đơn giản hóa tới mức vô nghĩa. Có thể thiết kế benchmark thứ ba cho lookup hoặc cache miss, thay vì đưa lookup vào phép đo cached invocation.

## 7. Workload thực tế quan trọng hơn một phép cộng

Một benchmark tiny có thể làm overhead gọi nổi bật. Nhưng phần lớn sản phẩm phải parse request, validate, thao tác dữ liệu, gọi I/O hoặc giao tiếp database. Các tác vụ đó có thể tốn nhiều hơn cơ chế gọi phương thức.

Cần chọn workload đại diện theo ít nhất các chiều:

| Chiều cần mô phỏng | Ví dụ |
| --- | --- |
| Độ phức tạp target | Trả primitive; parse chuỗi; xử lý object; truy vấn dữ liệu |
| Loại tham số | Primitive, wrapper, String, DTO, mảng, nhiều tham số |
| Kết quả | Primitive, đối tượng, void, exception |
| Cache pattern | Một method nóng, nhiều method hiếm gọi, cache hit/miss |
| Tải | Một thread, nhiều thread, request bị contention |
| Input | Độ dài, tỷ lệ lỗi, số loại request, phân bố thực tế |
| Cấu hình runtime | Cold start, warmup lâu, kích thước heap, GC và JDK |

Không đánh đồng benchmark `parseOrder` với workload có I/O; nó vẫn là tác vụ CPU/chuỗi. Với DB/network hoặc service thực tế, nên **profile và load test end-to-end**, lấy p50/p95/p99 latency, throughput, allocation và CPU. Microbenchmark không thay thế kiểm chứng hệ thống dưới tải.

### Tỷ trọng overhead

Giả sử **chỉ để minh họa phép tính**, chi phí tăng thêm do cơ chế gọi là 100 ns:

| Chi phí target | Overhead thêm | Tỷ lệ tăng tương đối |
| ---: | ---: | ---: |
| 100 ns | 100 ns | 100% |
| 1.000.000 ns (1 ms) | 100 ns | 0,01% |

Công thức minh họa: `overhead / target time × 100%`. Đây **không phải số đo thật** về `Method.invoke`. Bài học là cùng một chi phí cố định có thể rất quan trọng ở target cực nhỏ và gần như không đáng kể khi target nặng; phải đo production workload mới kết luận được.

## 8. Cache Method: quyết định dựa trên dữ liệu

Cache `Method` giúp tránh lặp lại tra cứu metadata nếu cùng chữ ký được dùng thường xuyên. Nhưng caching có chi phí bộ nhớ, khởi tạo, lookup key và vòng đời.

Khi quyết định cache, hãy kiểm tra:

1. **Cache hit ratio**: nếu đa số request gọi lại một số ít method, lợi ích lookup có thể đáng kể.
2. **Key đầy đủ**: phải phân biệt class, method name, danh sách kiểu tham số (overload), và ngữ cảnh class loader nếu liên quan. Chỉ key theo tên method là không đủ.
3. **Scope cache**: instance, class loader hay toàn application? Tham chiếu mạnh tới `Class`/`Method` có thể cản trở class unloading khi ứng dụng nạp module động.
4. **Thread safety**: việc đọc/ghi cache có tạo contention không?
5. **Kích thước và eviction**: cache vô hạn cho nhiều class/method có thể gây tăng heap.
6. **Profile thực tế**: có chắc lookup đang chiếm thời gian đáng kể, hay toàn request bị chi phối bởi parse/network/DB?
7. **Đo tách biệt**: benchmark direct, cached invoke, lookup+invoke và full pipeline trước/sau cache.

`ClassValue` có thể hữu ích khi dữ liệu cache gắn với vòng đời của từng `Class`. Với class loader động, tránh xây cache toàn cục giữ mãi strong reference mà không có chính sách vòng đời.

Nếu muốn so sánh thêm `MethodHandle`, hãy chuẩn bị handle và adapter trước phép đo khi thí nghiệm chỉ quan tâm invocation. Không kết luận `MethodHandle` luôn nhanh hơn từ tên API; chữ ký, call-site type, inlining và cách xây adapter đều ảnh hưởng kết quả.

## 9. Cách đọc kết quả và báo cáo

Một báo cáo kỹ thuật nên chứa:

- **Câu hỏi**: cached `Method.invoke` so với direct trong workload nào?
- **Môi trường**: JDK vendor/version, flags, CPU, OS, cấu hình GC/heap.
- **Dữ liệu**: số lượng và phân bố input, phương thức được benchmark, kiểu tham số/return.
- **Thiết lập đo**: warmup, measurement iterations, số fork, số thread, benchmark mode.
- **Kết quả**: score, đơn vị, sai số/độ phân tán, các mẫu bất thường, checksum hoặc kiểm tra chức năng.
- **Giới hạn**: thiếu I/O, dataset không đại diện, case cold start, cache miss hoặc mô hình concurrency chưa được đo.

Các đại lượng không nên nhầm:

| Đại lượng | Cách hiểu |
| --- | --- |
| `ns/op` | Số nanosecond trung bình cho một operation; thấp hơn thường tốt |
| `ops/s` | Throughput; cao hơn thường tốt |
| Error/độ lệch | Độ bất định của kết quả; chênh lệch nhỏ có thể không đáng tin |
| `p50/p95/p99` | Phân vị độ trễ request; quan trọng khi đánh giá tail latency |
| Allocation/GC | Thông tin về cấp phát và dừng GC có thể làm thay đổi độ trễ |

Đừng lấy một mẫu nhanh nhất rồi công bố thành tốc độ “chuẩn”. Nếu confidence/error giữa direct và reflection chồng lấn đáng kể, cần tăng số phép đo hoặc kết luận rằng khác biệt chưa được xác định chắc chắn.

## 10. Lỗi phổ biến

1. **Đo không warmup**, nhưng báo số liệu như steady-state.
2. **Đo lookup + invoke** rồi gán chênh lệch cho riêng `Method.invoke`.
3. **Direct làm ít việc hơn reflection**, ví dụ một bên parse dữ liệu, bên kia đã có tham số kiểu đúng.
4. **Không dùng kết quả**, khiến JIT có thể loại bỏ công việc.
5. **Input quá đơn điệu**, làm compiler đơn giản hóa vòng lặp hơn ứng dụng thật.
6. **Bỏ qua boxing/unboxing hoặc allocation** khi giải thích khác biệt.
7. **Đưa in kết quả hoặc logging vào vòng đo** nhưng không có ý định đo chính logging.
8. **Chạy chỉ một lần, trên một JVM**, rồi kết luận tỉ lệ cố định cho mọi môi trường.
9. **Nhìn mỗi ns/op tiny**, bỏ qua latency/throughput của request có target thật.
10. **Cache mọi metadata vô hạn**, gây tăng memory hoặc giữ class loader không cần thiết.
11. **So sánh JMH modes khác nhau** mà không kiểm tra đơn vị, số thread và kết quả.
12. **Benchmark chỉ đường thành công**, không kiểm tra input bất hợp lệ, exception hoặc tỷ lệ cache miss khi chúng xuất hiện nhiều trong production.

## 11. Tự kiểm tra

**Câu 1.** `service.getClass().getMethod("work").invoke(service)` trong vòng lặp có là phép đo overhead thuần của `Method.invoke` không?

Không. Mỗi lượt bao gồm tra cứu method. Nếu cần cached invocation, hãy tạo `Method` trước vòng đo.

**Câu 2.** Tại sao warmup cả direct và reflection?

Mỗi đường có JIT profile khác nhau; warmup một phía có thể làm sai so sánh trạng thái ổn định.

**Câu 3.** Nếu thấy direct tiny chỉ khoảng 0,2 ns/op, có nên tuyên bố đây là chi phí mọi lời gọi Java trực tiếp?

Không. JIT có thể inline, tối ưu loop hoặc constant folding. Con số mô tả mã benchmark được tối ưu dưới điều kiện cụ thể, không mặc nhiên đại diện mọi request.

**Câu 4.** Vì sao checksum cần khớp?

Nếu hai đường cho kết quả khác nhau, có thể chúng đang thực hiện lượng công việc hoặc logic khác nhau, khiến kết quả tốc độ không có ý nghĩa.

**Câu 5.** Workload mục tiêu chạy 5 ms do I/O, benchmark một method trả `int` có đủ để quyết định thay reflection không?

Không. Phải đo overhead trong pipeline thực tế; network, DB, GC và queueing có thể chi phối độ trễ.

**Câu 6.** JMH có thay thế profiling và load test không?

Không. JMH đo một phép microbenchmark đã định nghĩa tốt; profiling tìm điểm nghẽn, còn load test đánh giá end-to-end latency/throughput và contention.

**Bài tập:** thêm phép đo lookup riêng, thay số lượng method nóng và tỷ lệ cache hit; sau đó chạy JMH trên các JDK mục tiêu. Chỉ đề xuất cache hoặc thay API khi số liệu mới phản ánh workload thực tế.

## 12. Điểm cần nhớ

Một benchmark Reflection đáng tin phải có **baseline trực tiếp**, **cùng input/cùng kết quả**, **warmup**, **cache metadata khi đo cached invocation**, **nhiều mẫu/fork** và **workload đại diện**. Benchmark phép tính nhỏ giúp nhận ra overhead của lời gọi; benchmark nghiệp vụ thật mới giúp quyết định tối ưu có đáng hay không. Với kết luận quan trọng, dùng JMH và ghi rõ toàn bộ ngữ cảnh phép đo.

Tài liệu chính thức:

- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/)
- [Mã nguồn JMH](https://github.com/openjdk/jmh)
- [Method.invoke — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [System.nanoTime — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime())

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-performance-010">Chi tiết triển khai reflection có thể thay đổi giữa các phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Chi tiết triển khai reflection có thể thay đổi giữa các phiên bản JDK

## 1. Phân biệt hợp đồng API với cơ chế nội bộ của JDK

Trong Java, **core reflection** là tập API chuẩn như `java.lang.Class`, `java.lang.reflect.Method`, `Constructor`, `Field`, `AccessibleObject`. Ứng dụng dùng API để khám phá thông tin về kiểu và thao tác lên các thành viên ở runtime. Chẳng hạn:

```java
Method add = Counter.class.getMethod("add", int.class);
Object result = add.invoke(counter, 2);
```

Đây là fragment cần có `Counter`, `counter` và import `java.lang.reflect.Method`. Hợp đồng quan trọng là tìm đúng phương thức, kiểm tra quyền truy cập, kiểm tra đối số, thực thi method, trả kết quả và báo ngoại lệ theo Javadoc. **Cách JDK chuyển lời gọi phản chiếu thành thao tác JVM không nằm trong cam kết ổn định của public API.**

Một JDK có thể dùng native entry, lớp hỗ trợ được sinh động, `MethodHandle`, `VarHandle`, JIT intrinsics hoặc cơ chế khác. Những chi tiết ấy được quyền thay đổi miễn là tiếp tục đáp ứng đặc tả Java SE, trừ khi bản nâng cấp còn có thay đổi API/semantics được công bố riêng. Vì vậy:

- Có thể kỳ vọng `Method.invoke`, `Field.get` và `Constructor.newInstance` thực hiện đúng hành vi theo **Javadoc của phiên bản mục tiêu**.
- Không nên giả định đường gọi nội bộ, tên lớp như `DelegatingMethodAccessorImpl`, tên field private của JDK hoặc ngưỡng tối ưu hóa là một giao diện công khai.
- Không suy ra rằng cùng một lời gọi reflection luôn có cùng thời gian thực thi trên JDK khác nhau, hoặc ngay giữa các giai đoạn warm-up của cùng một JVM.
- Không đồng nhất việc thay đổi implementation với thay đổi ý nghĩa API; một lần nâng JDK vẫn cần test regression để xác nhận behavior và performance thực tế.

Bài học này thuộc chủ đề **cache và hiệu năng**: hiểu lịch sử thay đổi triển khai reflection sẽ giúp thiết kế cache, đánh giá benchmark và lập kế hoạch nâng JDK chính xác hơn.

## 2. Một ví dụ có thật: JEP 416 trong JDK 18

[**JEP 416 — Reimplement Core Reflection with Method Handles**](https://openjdk.org/jeps/416) được đưa vào **JDK 18**. Mục tiêu chính là đơn giản hóa việc bảo trì: triển khai core reflection dùng chung nền tảng với `java.lang.invoke`, giảm các đường mã nội bộ trùng lặp.

| Giai đoạn của OpenJDK | Mô hình triển khai đáng chú ý | Điều cần hiểu |
| --- | --- | --- |
| JDK 17 trở về trước (mô hình truyền thống) | Lời gọi `Method.invoke`/constructor reflective ban đầu có thể đi qua native accessor; sau một ngưỡng, hệ thống *inflation* sinh accessor bytecode hỗ trợ lặp gọi | Đây là **implementation detail**, không phải hợp đồng rằng số lần N luôn kích hoạt một lớp accessor cụ thể trên mọi JVM |
| JDK 18–21 | Core reflection được triển khai lại trên nền `MethodHandle` cho `Method`/`Constructor` và `VarHandle` cho `Field` | Người dùng tiếp tục gọi public API cũ; lớp nội bộ và đặc tính hiệu năng có thể khác |
| JDK 22 trở đi (OpenJDK) | Triển khai core reflection cũ đã được loại bỏ | Đừng dựa vào cờ tạm thời để quay lại legacy implementation; tài liệu JDK 22 xác nhận nó không còn tác dụng |

### Mô hình trước JDK 18: “inflation” là gì?

Trong một số triển khai HotSpot/OpenJDK trước JDK 18, lần gọi reflection đầu có thể đi qua accessor sử dụng mã native. Sau một số lần gọi, JDK có thể chuyển sang accessor sinh động bằng bytecode để tránh lặp đi qua native. Cơ chế này được gọi là *inflation*. Các lớp hỗ trợ nội bộ và cách chúng được JIT xử lý tạo ra chi phí warm-up, code generation và nhiều code path cần bảo trì.

Đó là lý do một số bài benchmark cũ quan sát thấy thời gian gọi reflection trước và sau warm-up khác nhau. **Không đưa kết luận này thành quy luật cố định của Java language**: threshold, tên property nội bộ, hiệu ứng tối ưu hóa và lựa chọn implementation tùy bản JDK/nhà cung cấp.

### Sau JEP 416: “dùng MethodHandle” có ý nghĩa gì?

Trong OpenJDK từ JDK 18, `Method.invoke` được hỗ trợ qua method handles, constructor invocation qua method handles và accessor của field qua var handles. Điều này **không có nghĩa** API `Method.invoke` biến thành `MethodHandle.invokeExact` đối với người dùng.

Các khác biệt ở public API vẫn tồn tại:

- `Method.invoke` tiếp tục nhận `Object...` và trả về `Object`; phương thức đích ném exception thì reflection bọc nguyên nhân trong `InvocationTargetException`.
- `MethodHandle.invokeExact` yêu cầu chữ ký tại call site khớp đúng `MethodType` của handle và ném trực tiếp ngoại lệ từ mục tiêu (trừ những lỗi khi bản thân handle được gọi sai).
- `Field.get/set` vẫn tuân theo kiểu, quyền truy cập và quy tắc chuyển đổi của reflection; `VarHandle` cung cấp thêm các access mode có ngữ nghĩa bộ nhớ/atomic khác nhau.
- `AccessibleObject` và kiểm tra quyền theo JPMS vẫn có hợp đồng riêng, không bị loại bỏ chỉ vì implementation nền dùng `java.lang.invoke`.

Tài liệu phát hành [Oracle JDK 18](https://www.oracle.com/java/technologies/javase/18-relnote-issues.html) mô tả một số rủi ro tương thích đối với code **dựa vào chi tiết không được tài liệu hóa**. Chẳng hạn, code tự kiểm tra các lớp accessor nội bộ hoặc cố sửa trường `modifiers` private trong `Method`/`Field`/`Constructor` có thể bị ảnh hưởng. Đây là ví dụ rõ ràng về lý do tránh phụ thuộc JDK internals.

### Cờ tương thích cũ không phải giải pháp lâu dài

JEP 416 từng cung cấp system property `-Djdk.reflect.useDirectMethodHandle=false` làm phương án tạm để quay lại implementation cũ trong những bản JDK còn chứa nó. **JDK 22 đã loại bỏ implementation cũ và cờ trên trở thành no-op** theo [JDK 22 Release Notes](https://www.oracle.com/java/technologies/javase/22-relnote-issues.html). Không dùng property này như cấu hình hiệu năng hoặc phương án khắc phục lâu dài trên các JDK hiện đại.

Nếu cần thử nghiệm hồi quy trên JDK 18–21, nên coi property đó là công cụ chẩn đoán *gắn với đúng phiên bản được hỗ trợ*, không là public contract. Các JDK khác nhà cung cấp có thể khác; luôn kiểm tra release notes của bản phân phối đang chạy.

## 3. Điều gì ổn định, điều gì có thể đổi?

### Hành vi được xác định qua public API

Các thao tác sau là điều ứng dụng nên dựa vào **theo Javadoc của JDK mục tiêu**:

- `Class#getMethod(name, parameterTypes)` tìm public method theo tên và kiểu tham số, hoặc ném `NoSuchMethodException` nếu không tìm thấy.
- `Method#invoke(receiver, args)` yêu cầu receiver phù hợp cho instance method; static method bỏ qua receiver; số lượng và kiểu argument phải tương thích.
- `Constructor#newInstance(args)` tạo instance theo constructor được chọn, với kiểm tra truy cập và đối số.
- `Field#get(receiver)`/`set(receiver, value)` đọc/ghi field theo quyền và kiểu; field `final` có hạn chế riêng.
- Khi code của method/constructor đích ném ngoại lệ, `Method.invoke`/`Constructor.newInstance` phản ánh ngoại lệ qua `InvocationTargetException`.
- Quyền truy cập phản chiếu được kiểm soát bởi ngôn ngữ Java và Java Platform Module System; `setAccessible(true)` không mặc nhiên vượt qua ranh giới module.

Những điều này đủ để ứng dụng tương tác với reflection mà không quan tâm accessor được gọi qua native, bytecode hay method handle ở bên trong.

### Chi tiết không nên lấy làm hợp đồng

| Giả định nội bộ | Tại sao nguy hiểm |
| --- | --- |
| Cụ thể lớp triển khai `MethodAccessor`/`FieldAccessor` | JEP 416 đã thay đường triển khai; một bản JDK có thể bỏ lớp nội bộ |
| Ngưỡng “inflation” hoặc property không được chuẩn hóa | Có thể bị đổi, bị bỏ hoặc hoàn toàn vô hiệu ở phiên bản sau |
| Thứ tự `getDeclaredMethods()`/`getDeclaredFields()` | Javadoc không cam kết thứ tự theo source; muốn kết quả ổn định phải sort có chủ đích |
| Cách `Method`/`Field` giữ internal cache/accessor | Layout private field và cache strategy có thể thay đổi |
| Một con số “reflection chậm hơn trực tiếp X lần” | Thay đổi theo JDK, workload, kiểu lời gọi, compiler, warm-up và cách giữ metadata |
| Private JDK package như `jdk.internal.reflect` | Không thuộc API Java SE công khai; còn bị giới hạn bởi module encapsulation |
| Stack trace luôn có cùng frame nội bộ | Thay đổi implementation có thể thay đổi helper frames; không parse theo tên class nội bộ |

Ngay cả khi public API ổn định, việc nâng JDK có thể mang theo thay đổi **được công bố** ở API, kiểm soát truy cập, cảnh báo hoặc behavior. Vì vậy kiểm tra compatibility phải đọc release notes và chạy test chứ không chỉ dựa vào câu “implementation detail”.

## 4. Ví dụ Java 17+: API công khai cho kết quả nhất quán

Chương trình sau dùng reflection để tạo object, gọi method, đọc/ghi field; dùng `ClassValue<Method>` làm **ví dụ cache metadata** theo class; và gọi cùng logic qua `MethodHandle` để thấy API ngoài khác nhau nhưng kết quả nghiệp vụ không thay đổi. Không đọc lớp private của JDK, không kiểm tra accessor internals và không dựa vào time measurement.

Lưu thành file **`ReflectionUpgradeDemo.java`**:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUpgradeDemo {
    public static final class Counter {
        public int value;

        public Counter(int start) {
            value = start;
        }

        public int add(int delta) {
            return value += delta;
        }
    }

    // Cache metadata theo Class<?>; không cache giá trị của từng object.
    private static final ClassValue<Method> ADD = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("add", int.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    public static void main(String[] args) throws Throwable {
        Constructor<Counter> constructor =
                Counter.class.getConstructor(int.class);
        Counter counter = constructor.newInstance(5);

        Method method = ADD.get(Counter.class);
        Object result = method.invoke(counter, 2);

        Field field = Counter.class.getField("value");
        System.out.println("reflection invoke: " + result);
        System.out.println("reflection field: " + field.getInt(counter));
        System.out.println("metadata cached: "
                + (method == ADD.get(Counter.class)));

        MethodHandle handle = MethodHandles.lookup().findVirtual(
                Counter.class,
                "add",
                MethodType.methodType(int.class, int.class));
        Counter other = new Counter(5);
        int fromHandle = (int) handle.invokeExact(other, 2);
        System.out.println("method handle: " + fromHandle);

        field.setInt(counter, 11);
        System.out.println("field after set: " + field.getInt(counter));
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac --release 17 ReflectionUpgradeDemo.java
java ReflectionUpgradeDemo
```

Stdout dự kiến:

```text
reflection invoke: 7
reflection field: 7
metadata cached: true
method handle: 7
field after set: 11
```

Ý nghĩa:

1. `Constructor.newInstance(5)` tạo `Counter(5)`; `Method.invoke(counter, 2)` cho kết quả `7`.
2. `ClassValue` lưu `Method` theo `Class<?>`. Hai lần `ADD.get(Counter.class)` trong cùng lần chạy trả cùng đối tượng metadata ở ví dụ này; không có `remove` hay thay đổi cache giữa hai lần đọc.
3. `MethodHandle` gọi `Counter.add` trên instance khác với chữ ký cụ thể `(Counter,int)int` và trả `7`.
4. `Field.setInt` thay giá trị trên instance thứ nhất thành `11` rồi `getInt` đọc lại `11`.
5. **Code không khẳng định hai cách gọi có cùng tốc độ**. Nó là regression test cho behavior công khai, có thể chạy lại trên nhiều JDK và so stdout.

Lệnh sau dùng để ghi nhận JDK đang chạy, nhưng phần output **phụ thuộc máy** nên không gộp vào stdout cố định ở trên:

```bash
java -version
javac -version
```

Nếu máy có nhiều JDK, đảm bảo `java` và `javac` dùng phiên bản mong muốn (qua `JAVA_HOME`/`PATH` hoặc đường dẫn tuyệt đối). Tham số `--release 17` đảm bảo biên dịch với API mục tiêu Java 17, **không tự biến JVM đang chạy thành JDK 17**.

## 5. Hiệu năng reflection phụ thuộc call site và mức độ “constant”

JEP 416 nêu một vấn đề quan trọng: khi đối tượng `Method`/`Constructor`/`Field` được JVM nhận diện là hằng tại vị trí dùng (ví dụ giữ trong `static final` và được compiler tối ưu phù hợp), triển khai mới có thể tối ưu tốt; khi metadata nằm trong dữ liệu động mà JIT không nhìn thấy như constant, đường gọi có thể kém hơn ở một số microbenchmark.

**Không hiểu nhầm `final` tự động làm mọi call site nhanh:** khả năng constant folding và inlining là quyết định của JVM/JIT, không cam kết ngôn ngữ. Không phải mọi cached `Method` đều trở thành hằng theo cách JIT cần; `ClassValue` là giải pháp cache metadata theo class, không tự đảm bảo tối ưu invocation tương tự một field `static final`.

Tách ít nhất bốn loại chi phí khi phân tích:

| Giai đoạn | Ví dụ | Điểm cần đo |
| --- | --- | --- |
| Khám phá member | `clazz.getMethod(...)`, `getDeclaredFields()` | Tần suất gọi, cache misses, classloader |
| Kiểm tra quyền/chuẩn hóa đối số | Receiver, access checks, boxing/unboxing | Loại access, chữ ký, số lượng argument |
| Thực thi lời gọi | `Method.invoke(...)`, `MethodHandle.invokeExact(...)`, direct method | JIT inlining, target polymorphism, loại method, call site |
| Đời sống metadata/cache | `ClassValue`, map theo `Class<?>` | Memory retention, module/classloader unload, cache invalidation |

Ví dụ sai điển hình khi benchmark:

```java
// Đo cả lookup lẫn invoke, không cô lập chi phí gọi:
for (int i = 0; i < count; i++) {
    Method method = type.getMethod("run");
    method.invoke(target);
}
```

Đây là fragment minh họa (cần biến `type`, `target`, `count` và xử lý exception). Kết quả được đo sẽ bao gồm lookup, có thể cộng thêm các ảnh hưởng từ cache của JDK, JIT và exception. Nếu muốn biết **chi phí invocation riêng**, đưa lookup ra ngoài phần đo; nhưng vẫn cần thử workload dynamic lookup nếu đó là hành vi thực tế của ứng dụng.

Dùng `MethodHandle` cũng không mặc nhiên nhanh hơn `Method.invoke`: các trường hợp typed/exact call và constant handle dễ được tối ưu hơn; dynamic handle, chuyển kiểu hoặc `invokeWithArguments` có thể phát sinh overhead. Lựa chọn API trước hết dựa theo semantic và quyền truy cập, sau đó benchmark nếu tốc độ thực sự quan trọng.

## 6. Cache metadata an toàn qua nhiều phiên bản JDK

Cache có giá trị khi cùng class và policy được dùng lặp lại, nhất là framework phải quét nhiều object. Tuy nhiên, **cách cache** phải tránh giữ classloader/plugin đã được unload.

### `ClassValue<T>`

`ClassValue<T>` gắn một giá trị tính theo đối tượng `Class<?>` và để runtime quản lý vòng đời cache gắn với class. Nó phù hợp cho metadata bất biến theo kiểu, ví dụ danh sách property đã chuẩn hóa hoặc `Method` tương ứng. Trong chương trình trên, `ClassValue<Method>` tránh phải tìm lại `getMethod("add", int.class)` mỗi lần.

Lưu ý: `ClassValue.computeValue` có thể được thực thi nhiều lần trong trường hợp cạnh tranh; thiết kế công việc tính toán như một thao tác **không side effect nguy hiểm**. Nếu policy thay đổi, giá trị cache cũng phải được invalidation hoặc key/cấu trúc cache phải gồm phiên bản policy. Không cache trường dữ liệu hoặc kết quả method theo `Class<?>` nếu giá trị thuộc từng instance.

### Map và ràng buộc classloader

`ConcurrentHashMap<Class<?>, AccessPlan>` thường nhanh và thuận tiện, nhưng key/value có thể giữ tham chiếu tới `Class<?>`, `Method`, `Field` hoặc `MethodHandle`, từ đó làm classloader không được GC sau hot reload/plugin unload. Cân nhắc `ClassValue`, bộ cache giới hạn vòng đời hoặc cơ chế weak-key phù hợp; xem cả **value có vòng tham chiếu về class hay không**.

### Không cache trạng thái không có trong hợp đồng

Không truy cập trường private của `Method` để giữ accessor nội bộ hoặc đoán threshold inflation. Sử dụng metadata công khai, và nếu cần gọi với kiểu cố định qua handle, xây/cấu hình handle bằng `MethodHandles.Lookup` với quyền hợp lệ. Lỗi reflection access có thể phụ thuộc module và caller context; không dùng kết quả cache đã thu dưới quyền khác như bằng chứng mọi caller đều được phép.

## 7. Cách benchmark reflection đúng khi nâng JDK

Đừng so một vòng lặp `System.nanoTime()` duy nhất trên JDK A và B rồi khẳng định JDK mới “nhanh/chậm hơn” dựa trên một con số. JIT, warm-up, garbage collection, CPU scaling và dead-code elimination dễ làm benchmark sai.

Một kế hoạch phù hợp:

1. Chốt **JDK vendor, build, kiến trúc CPU, OS, JVM flags** cho mỗi phép thử; ghi `java -version`.
2. Dùng **JMH** (Java Microbenchmark Harness) cho microbenchmark với fork, warm-up và đo lường rõ ràng; không lấy bài demo ở mục 4 làm benchmark.
3. Đo độc lập `lookup`, `invoke` và workload end-to-end. Có các case: member `static final`, metadata/handle nằm ở field instance, lookup theo class động, method 0/1/nhiều tham số, primitive/reference, public/private hợp lệ.
4. Không để JIT loại bỏ công việc: tiêu thụ kết quả bằng `Blackhole` hoặc trả giá trị hợp lệ theo hướng dẫn của JMH.
5. So kết quả throughput/latency kèm biến thiên giữa nhiều fork; quan sát cả allocation, GC, classloader retention và tốc độ cold startup nếu ứng dụng nhạy với chúng.
6. Chạy **functional regression tests** riêng: permission/JPMS, exception, varargs, conversion, field/constructor behavior. Microbenchmark không thay thế regression test.
7. Nếu có chênh lệch, tìm nguyên nhân bằng profiler/JFR và đổi thiết kế có cơ sở (ví dụ cache metadata hoặc chuẩn hóa call site). Không ép JVM bật lại cơ chế cũ không còn được hỗ trợ.

Không có con số “`Method.invoke` chậm X lần” nào đúng cho mọi JDK. JEP 416 trình bày số liệu của workload và bản JVM tại thời điểm JEP; đó là minh họa cho **trade-off** và lý do đo lại, không phải dự báo cố định cho mọi phiên bản sau.

## 8. Chẩn đoán khác biệt khi nâng JDK

Khi từ JDK 17 nâng lên JDK 21, 22 hoặc một phiên bản mới hơn, hãy phân loại thay đổi:

| Dấu hiệu | Có thể liên quan đến | Hướng xử lý |
| --- | --- | --- |
| Test về `Method.invoke` cùng tham số trả kết quả khác | Thay đổi app dependency/behavior, JDK bug hoặc giả định sai về contract | Viết test tối thiểu, so Javadoc của hai JDK và báo bug nếu có chứng cứ |
| `InaccessibleObjectException` hoặc access failure | Module boundaries, `opens` và quyền lookup | Chuyển sang API public hoặc cấp đúng quyền module, không đọc JDK internals |
| Stack trace xuất hiện frame `java.lang.invoke` mới | Implementation core reflection mới | Debug theo API và cause; không dùng tên helper frame làm điều kiện logic |
| Startup hoặc steady-state latency thay đổi | Chi phí initialization, JIT và loại call site | Dùng JFR/JMH, tách cold vs warmed case |
| System property legacy không còn hiệu quả trên JDK 22+ | Triển khai cũ đã bị loại bỏ | Bỏ workaround và kiểm tra đúng release notes |
| Plugin/hot reload không giải phóng classloader | Metadata cache giữ `Class<?>`/`Method` hoặc classloader | Đánh giá lifecycle cache, `ClassValue` hoặc weak-reference design |

Ví dụ: nếu framework dựa vào internal `MagicAccessorImpl`, thành công trên JDK cũ không chứng minh code là portable. Tìm cách biểu diễn cùng nghiệp vụ bằng `Method`, `Constructor`, `Field`, `MethodHandles.Lookup` hoặc public interfaces thay vì “sửa” bằng reflection vào chính private field của reflection implementation.

Nếu ứng dụng bị lỗi sau nâng JDK, ghi **đúng phiên bản của tất cả JVM chạy** (build container, test worker, IDE, production); xác minh dependencies hỗ trợ JDK đó và tạo minimal reproducer. Không thay đổi cờ VM ngẫu nhiên khi chưa cô lập nguyên nhân.

## 9. Lỗi thường gặp và câu hỏi tự kiểm tra

**Lỗi 1 — Đánh đồng JEP 416 với việc loại bỏ reflection:** JEP 416 là thay đổi cách triển khai **bên dưới** `java.lang.reflect`, không yêu cầu thay tất cả `Method.invoke` thành `MethodHandle`.

**Lỗi 2 — Coi `jdk.reflect.useDirectMethodHandle=false` là cấu hình hiệu năng ổn định:** đây là workaround tạm thời trong một số JDK sau JEP 416; JDK 22 đã loại bỏ implementation cũ.

**Lỗi 3 — Kết luận `ClassValue`/`static final` luôn đảm bảo nhanh hơn:** cache giảm chi phí discovery; JIT có thể tối ưu call site tùy điều kiện, không có cam kết performance tự động.

**Lỗi 4 — Dùng `getDeclaredMethods()` rồi tin vào thứ tự mặc định:** API không hứa sắp theo source. Muốn kết quả ổn định để sinh schema/test, tự sắp xếp.

**Lỗi 5 — Dùng private JDK fields, `sun.reflect.*` hoặc `jdk.internal.reflect.*` như public API:** có rủi ro biên dịch, access và behavior khi nâng JDK. Dùng public reflection/invoke APIs.

**Lỗi 6 — Chỉ chạy test hiệu năng mà bỏ test chức năng:** JIT benchmark không kiểm tra module access, exception wrapping, null receiver hay primitive conversion.

Tự trả lời:

1. JDK 18 có đổi cú pháp `Method.invoke` cho người dùng không? **Không; JEP 416 đổi implementation nội bộ.**
2. Nếu JDK 22 bỏ đường cũ thì `-Djdk.reflect.useDirectMethodHandle=false` còn khôi phục nó được không? **Không; cờ thành no-op.**
3. Framework gọi `Class.getDeclaredMethods()` rồi cần output ổn định thì nên làm gì? **Định nghĩa tiêu chí sắp xếp rõ ràng.**
4. Cache `Method` vào map là bảo đảm performance và không có memory leak phải không? **Không; cần kiểm tra workload và classloader lifecycle.**
5. Muốn so hai bản JDK về chi phí phản chiếu thì đo thế nào? **JMH với warm-up/forks, tách lookup/invoke, giữ workload và môi trường tương đương, kết hợp functional tests.**
6. Tại sao vẫn nên đọc Java SE Javadoc sau khi biết reflection dùng method handles? **Vì hợp đồng public API và implementation nền là hai tầng khác nhau.**

Tài liệu tham khảo:

- [JEP 416 — Reimplement Core Reflection with Method Handles](https://openjdk.org/jeps/416)
- [Oracle JDK 18 Release Notes — JEP 416](https://www.oracle.com/java/technologies/javase/18-relnote-issues.html)
- [Oracle JDK 22 Release Notes — Remove the Old Core Reflection Implementation](https://www.oracle.com/java/technologies/javase/22-relnote-issues.html)
- [Javadoc Method (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [Javadoc ClassValue (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassValue.html)
- [JMH — Java Microbenchmark Harness](https://github.com/openjdk/jmh)


</details>

- [Quay lại đầu trang](#back-to-top)
