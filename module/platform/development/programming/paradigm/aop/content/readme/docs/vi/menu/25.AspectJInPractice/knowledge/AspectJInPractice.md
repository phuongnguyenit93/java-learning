<a id="back-to-top"></a>

# Minh họa với AspectJ

## Menu
- [AspectJ như một ví dụ cụ thể của mô hình AOP có weaving](#aspect-j-in-practice-001)
- [Aspect bằng cú pháp chuyên biệt và bằng annotation](#aspect-j-in-practice-002)
- [Biểu thức execution, call và within trong pointcut](#aspect-j-in-practice-003)
- [Biểu thức this, target, args và pointcut theo annotation](#aspect-j-in-practice-004)
- [Pointcut cflow và cflowbelow để minh họa luồng điều khiển](#aspect-j-in-practice-005)
- [Advice before, after, around và lời gọi proceed](#aspect-j-in-practice-006)
- [Khai báo ưu tiên, aspect trừu tượng và mô hình tạo aspect](#aspect-j-in-practice-007)
- [Mô hình singleton, perthis, pertarget và percflow trong vòng đời aspect](#aspect-j-in-practice-008)
- [Khai báo liên kiểu và kiểm tra quy tắc ở bước xây dựng](#aspect-j-in-practice-009)
- [Cấu hình phạm vi dệt và đọc thông báo của bộ dệt](#aspect-j-in-practice-010)
- [Phân biệt đặc tính riêng của AspectJ với khái niệm AOP tổng quát](#aspect-j-in-practice-011)

## <a id="aspect-j-in-practice-001">AspectJ như một ví dụ cụ thể của mô hình AOP có weaving</a>

<details>
<summary>Xem chi tiết</summary>

# AspectJ như một ví dụ cụ thể của mô hình AOP có weaving

## 1. Mục tiêu: nhìn thấy AOP hoạt động qua bytecode

**Aspect-Oriented Programming (AOP)** là một cách tổ chức chương trình nhằm mô-đun hóa những *mối quan tâm cắt ngang* (*cross-cutting concerns*) như quan sát thời gian, truy vết, kiểm toán hoặc các chính sách kỹ thuật được áp dụng nhất quán ở nhiều điểm thực thi. Ý tưởng cốt lõi là mô tả **điểm nào cần can thiệp** và **hành vi gì cần thực thi** mà không phải rải cùng đoạn mã vào từng phương thức nghiệp vụ.

**AspectJ** cung cấp một ví dụ cụ thể cho mô hình này bằng cơ chế **weaving**: kết hợp mã aspect với mã chương trình tại các join point phù hợp. Nhờ đó, một lời gọi nội bộ hay một method execution được lựa chọn có thể được can thiệp ngay trong bytecode, không phụ thuộc việc caller cầm một đối tượng wrapper cụ thể.

Bài này dùng AspectJ để hiểu **AOP từ cơ bản tới nâng cao**. Các khái niệm như *join point*, *pointcut*, *advice*, *aspect*, *composition* là kiến thức AOP chung; cú pháp `aspect`, `execution(...)`, `call(...)`, `declare precedence` và công cụ `ajc` là cách AspectJ hiện thực chúng.

Sau bài học, bạn có thể:

1. Giải thích weaving là gì và phân biệt với việc gọi qua một proxy/decorator.
2. Đọc được pointcut, advice và vòng đời thực thi của một aspect kiểu AspectJ.
3. Biên dịch ví dụ Java 17+ kèm một file `.aj`, kiểm chứng thay đổi đầu ra trước/sau weaving.
4. Phân biệt compile-time, post-compile và load-time weaving; nhận biết khi nào một join point bị/mất matching.
5. Thiết kế pointcut chính xác, chọn thứ tự aspect, bảo toàn exception/return và kiểm thử weaving.
6. Hiểu giới hạn về class loader, đồng thời, callback async, chi phí và quyền hạn khi áp dụng AOP.

## 2. Mô hình khái niệm của AOP

### 2.1. Thuật ngữ cơ bản

| Khái niệm | Vai trò | Ví dụ |
| --- | --- | --- |
| **Concern** | Một mối quan tâm cần thực thi | Đo thời gian thao tác xử lý |
| **Cross-cutting concern** | Concern đi qua nhiều module | Tracing cho nhiều service |
| **Join point** | Điểm có nghĩa trong quá trình thực thi | Method execution, method call, constructor execution |
| **Pointcut** | Biểu thức chọn các join point | `execution(* demo..*(..))` |
| **Advice** | Mã chạy trước/sau/xung quanh join point | Đếm số lần gọi, log lỗi |
| **Aspect** | Mô-đun chứa pointcut, advice và cấu trúc liên quan | `LatencyAspect` |
| **Weaver** | Thành phần phối hợp advice với các join point phù hợp | Bộ biên dịch/bytecode weaver |
| **Target / advised code** | Mã gốc được tác động | `GreetingService.greet()` |
| **Inter-type declaration** | Bổ sung member hoặc quan hệ kiểu theo cơ chế AspectJ | Thêm phương thức vào một class qua aspect |

**Join point là sự kiện/địa điểm có nghĩa trong execution model**, còn **pointcut là điều kiện mô tả tập join point**. Không nên nhầm pointcut với một lời gọi phương thức cụ thể; pointcut có thể khớp rất nhiều điểm ở nhiều class.

**Advice** có thể quan sát hoặc điều chỉnh hành vi: advice kiểu `around` thậm chí có thể không cho logic gốc chạy, thực thi lại hoặc đổi giá trị trả về. Vì vậy AOP yêu cầu thiết kế hợp đồng và kiểm thử tương đương nghiêm túc, đặc biệt với thao tác có side effect.

### 2.2. Mô hình thay đổi khi có weaving

~~~text
Mã nguồn nghiệp vụ    Mã nguồn aspect          Bộ công cụ weaving
       |                    |                           |
       +--------------------+-------------------------->|
                                                       |
                                             Bytecode đã được woven
                                                       |
                                                       v
                      JVM chạy method đã chứa điểm thực thi advice
~~~

Khi method được weave, lời gọi Java thường đến method đó có thể kích hoạt advice mà caller không phải gọi qua một đối tượng `Proxy`. Đây là điểm khác biệt quan trọng so với một số cơ chế interception dựa hoàn toàn trên wrapper.

**Không nên hiểu weaving là hệ thống JVM tùy ý sửa mọi class đã chạy xong.** AspectJ xử lý bytecode tại giai đoạn biên dịch, sau biên dịch hoặc khi class được định nghĩa bởi class loader phù hợp. Load-time weaving diễn ra trước khi class đích được định nghĩa; nó không đồng nghĩa tùy ý thay đổi mọi class đã được load.

## 3. Weaving, proxy và decorator: giống và khác nhau

| Khía cạnh | Proxy/decorator ở ranh giới lời gọi | Weaving kiểu AspectJ |
| --- | --- | --- |
| Cách can thiệp | Caller phải đi qua wrapper/proxy phù hợp | Bytecode tại join point được kết hợp advice |
| Phạm vi | Thường dựa trên interface, đối tượng hoặc ranh giới đăng ký | Có thể chọn nhiều loại join point trong mô hình AspectJ |
| Self-invocation | Lời gọi trực tiếp bên trong target thường không đi qua wrapper | `execution(...)` được weave tại method có thể áp dụng cả self-call |
| Method call và method execution | Thường thiên về lời gọi qua proxy | Có hai loại join point riêng `call(...)` và `execution(...)` |
| Constructor / field | Wrapper thường không bắt được trực tiếp | AspectJ có designator tương ứng, tùy khả năng và phạm vi weaving |
| Tích hợp | Ít đụng build/class loader hơn | Cần compiler/weaver, cấu hình build/load phù hợp |
| Độ minh bạch | Dễ thấy wrapper khi tạo đối tượng | Source nghiệp vụ có thể không thể hiện advice sẽ chạy |

Ví dụ `service.internal()` gọi `this.process()` trên chính target: một proxy bên ngoài có thể không nhận thấy `process()`, nhưng **AspectJ execution join point** đã được weave ngay trong `process()` vẫn có thể chạy advice khi method đó được thực thi.

Với **call pointcut**, code chứa *vị trí gọi* phải được weave; chỉ weave định nghĩa method đích mà không weave class của caller có thể không bắt được vị trí `call` mong muốn. Đây là khác biệt quan trọng khi quyết định phạm vi triển khai aspect.

Sự lựa chọn giữa proxy và weaving không chỉ là “cái nào mạnh hơn”; cần đánh giá phạm vi join point, quyền kiểm soát mã đích, overhead, tính minh bạch, class loader và khả năng kiểm thử.

## 4. Ba thời điểm weaving

### 4.1. Compile-time weaving (CTW)

**Compile-time weaving** kết hợp mã Java và aspect ngay khi biên dịch bằng `ajc`. Mã nguồn nghiệp vụ và aspect (hoặc aspect đã biên dịch) cùng tham gia công đoạn build. Output là các file `.class` đã được weave.

~~~text
.java + .aj --ajc--> woven .class --JVM--> advice + nghiệp vụ
~~~

Đặc điểm:

- Dễ tái lập ở build pipeline; kết quả weaving có thể được kiểm tra ngay khi build.
- Có thể xử lý những cấu trúc aspect cần nhìn thấy khi compile, chẳng hạn một chương trình tham chiếu member được thêm bởi inter-type declaration.
- Cần kiểm soát cấu hình compiler, phiên bản Java, artifact chứa aspect và các class nguồn tham gia.
- Nếu một class không có trong tập đầu vào build phù hợp, nó có thể không được weave như dự kiến.

### 4.2. Post-compile / binary weaving

Với **post-compile weaving**, ứng dụng được biên dịch thành `.class` hoặc JAR trước, rồi một bước `ajc` khác áp aspect lên **bytecode có sẵn**.

~~~text
.java --javac--> .class/JAR --ajc -inpath + aspect--> woven .class/JAR
~~~

Điều này phù hợp khi không muốn thay compiler của toàn bộ mã nguồn, hoặc cần can thiệp một thư viện mà có quyền xử lý artifact. Tuy nhiên phải bảo đảm đủ class path/aspect path, phạm vi `-inpath`, đồng thời tránh nhập lại chính output làm input của lượt weave tiếp theo. Cần chú ý license và khả năng tương thích bytecode đối với artifact bên thứ ba.

**Điểm phân biệt:** CTW compile nguồn rồi weave; post-compile nhận bytecode đã biên dịch và weave. Cả hai tạo bytecode đã được thay đổi trước khi JVM bình thường load class đích.

### 4.3. Load-time weaving (LTW)

Với **load-time weaving**, bộ weaver can thiệp lúc class loader chuẩn bị định nghĩa class trên JVM. Cách phổ biến là dùng Java agent `aspectjweaver.jar` cùng mô tả aspect/phạm vi trong `META-INF/aop.xml`.

~~~text
JVM startup -javaagent:aspectjweaver.jar
               |
               v
     class loader đọc .class gốc
               |
               v
       weaver áp aspect phù hợp
               |
               v
        JVM định nghĩa class đã weave
~~~

Một số giới hạn:

- Weaver phải biết các aspect đủ sớm **trước lúc class đích được định nghĩa**; class đã load trước đó có thể bị bỏ qua.
- Class loader chỉ có thể weave những class nó trực tiếp định nghĩa theo mô hình LTW; delegation tới parent loader có thể làm thay đổi phạm vi.
- Class loader, module boundary, classpath, artifact chứa aspect và `aop.xml` đều ảnh hưởng tính khả dụng.
- LTW làm phát sinh yêu cầu kiểm soát JVM startup/agent và kiểm thử môi trường runtime, không chỉ build.
- Nếu một class cần biết member do aspect bổ sung ngay khi biên dịch code tham chiếu member ấy, chỉ đổi bytecode ở LTW có thể không đủ; cần sắp xếp compilation phù hợp.

AspectJ mô tả ba giai đoạn chính này trong tài liệu phát triển. Đừng dùng “runtime weaving” để ngụ ý AspectJ mặc định sửa những class đã được định nghĩa và chạy sẵn mà không cơ chế reload/instrumentation chuyên biệt.

## 5. Cú pháp cốt lõi: aspect, pointcut, advice

Một aspect mã nguồn kiểu AspectJ dùng từ khóa `aspect`, có thể chứa khai báo pointcut và advice. Ví dụ minh họa cú pháp:

~~~java
// Giả sử OrderService có public Receipt place(Order).
// Đây là đoạn minh họa trong file .aj, KHÔNG độc lập để biên dịch.
public aspect OrderObservation {
    pointcut placing():
        execution(* OrderService.place(..));

    before(): placing() {
        System.out.println("đang bắt đầu đặt hàng");
    }

    after() returning: placing() {
        System.out.println("method trả về bình thường");
    }

    after() throwing(Throwable problem): placing() {
        System.out.println("method ném " + problem.getClass().getSimpleName());
    }
}
~~~

Những thành phần:

- `execution(* OrderService.place(..))` chọn **method execution** khớp mẫu kiểu trả về bất kỳ và bất kỳ danh sách tham số.
- `before()` chạy trước join point.
- `after() returning` chỉ chạy khi join point kết thúc bình thường.
- `after() throwing(...)` chỉ chạy khi join point kết thúc bằng exception phù hợp.
- `after()` không có điều kiện returning/throwing là dạng finally-like advice, chạy khi join point kết thúc theo cả hai đường.

Ví dụ ở trên minh họa cấu trúc. Khi cần đo thời gian hay bảo đảm cleanup, **around advice với `try/finally`** thường giúp gắn rõ mốc bắt đầu/kết thúc trong một policy. Với `after returning`, giá trị trả về có thể được bind để quan sát nếu khai báo pointcut/advice phù hợp.

### 5.1. Các designator thông dụng

| Designator | Chọn theo | Lưu ý |
| --- | --- | --- |
| `execution(SignaturePattern)` | Thực thi method | Weave code của method được chọn |
| `call(SignaturePattern)` | Điểm gọi method | Weave *caller code* chứa lời gọi |
| `within(TypePattern)` | Vị trí mã nằm trong một kiểu | Bổ sung filter lexical |
| `withincode(SignaturePattern)` | Join point nằm trong phần thân method/constructor phù hợp | Lọc lexical theo code chứa join point |
| `this(TypeOrId)` | Đối tượng `this` tại join point | Ở một số join point có thể khác `target` |
| `target(TypeOrId)` | Đối tượng đích được gọi | Cần hiểu instance thực tế |
| `args(TypeOrId, ..)` | Đối số và có thể bind giá trị | Không đồng nghĩa lấy mọi object graph |
| `@annotation(AnnotationTypeOrVar)` | Annotation liên quan join point | Chỉ áp nơi annotation được giữ và match theo quy tắc |
| `cflow(Pointcut)` | Join point trong control flow của một pointcut | Có chi phí/semantics động, phải đo đạc |
| `if(BooleanExpression)` | Điều kiện động | Tránh đưa nghiệp vụ nặng vào kiểm tra pointcut |

Ví dụ pointcut kết hợp:

~~~java
// Giả sử tồn tại package demo.billing.
// Minh họa cú pháp, không phải chương trình độc lập.
pointcut measuredCalls():
    execution(public * demo.billing..*(..))
    && !within(demo.billing.observability..*);
~~~

Biểu thức logic `&&`, `||`, `!` có thể kết hợp những điều kiện selection. Hãy đặt pointcut theo **contract của thao tác**, thay vì quét một tên method quá rộng rồi phải thêm vô số ngoại lệ. Với `@annotation`, ý nghĩa và retention của annotation cũng phải được kiểm chứng; không giả định mọi annotation đều xuất hiện ở runtime theo cách giống nhau.

### 5.2. `execution` khác `call` trong tự gọi

Giả sử:

~~~java
class Processor {
    void first() {
        second();
    }
    void second() {
        // nghiệp vụ
    }
}
~~~

Đoạn Java chỉ minh họa. Nếu `execution(void Processor.second())` đã được weave vào method `second`, advice chạy khi `second` thực thi, kể cả được gọi bằng `this.second()` trong `first`. Nếu dùng `call(void Processor.second())` thì vị trí gọi trong `first` phải được weave. Một điểm gọi trong class không được weave có thể không chịu advice này.

Nhờ hiểu hai join point khác nhau, ta tránh những kết luận sai kiểu “weaving bắt mọi lời gọi ở bất kỳ nơi nào” hoặc “self-call luôn bypass như proxy”.

## 6. Ví dụ hoàn chỉnh Java 17+: thêm advice mà không sửa business code

Ví dụ sử dụng **compile-time weaving** để thấy cùng mã Java cho kết quả khác khi có file aspect. Ba file trong cùng một thư mục và đều thuộc default package để các lệnh biên dịch ngắn, độc lập.

### 6.1. Mã nghiệp vụ: `Greeter.java`

~~~java
public class Greeter {
    public String greet(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("empty name");
        }
        System.out.println("domain: greet");
        return "Hello " + name;
    }
}
~~~

Method chỉ quan tâm đến hợp đồng: từ chối tên rỗng và trả lời chào hợp lệ. Nó **không gọi aspect** và không chứa tracing.

### 6.2. Chương trình gọi: `Demo.java`

~~~java
public class Demo {
    public static void main(String[] args) {
        Greeter greeter = new Greeter();
        System.out.println("main: " + greeter.greet("An"));

        try {
            greeter.greet("   ");
        } catch (IllegalArgumentException e) {
            System.out.println("main: rejected " + e.getMessage());
        }
    }
}
~~~

### 6.3. Aspect: `CallTrace.aj`

~~~java
public aspect CallTrace {
    pointcut greeting(String name):
        execution(String Greeter.greet(String))
        && args(name);

    String around(String name): greeting(name) {
        System.out.println("trace: before");
        try {
            String result = proceed(name);
            System.out.println("trace: after");
            return result;
        } catch (RuntimeException failure) {
            System.out.println("trace: error");
            throw failure;
        }
    }
}
~~~

Giải thích:

- `pointcut greeting(String name)` đặt tên tập join point và **bind** đối số `name` nhờ `args(name)`.
- `execution(String Greeter.greet(String))` chọn method execution của `greet`, với return type `String` và một đối số `String`.
- `around` nhận giá trị đã bind, chạy trước và sau logic gốc; `proceed(name)` tiếp tục tới join point/chuỗi advice bên trong với đối số đó.
- Khi method ném `IllegalArgumentException`, `catch (RuntimeException)` log rồi **ném lại cùng exception**, không chuyển lỗi thành giá trị mặc định.
- Nếu advice thêm một lời gọi `proceed()` thứ hai hoặc tự sửa `name`, nó sẽ thay semantics nghiệp vụ. Đoạn ví dụ cố ý gọi đúng một lần.
- Chỉ có advice được thêm qua weaving; `Greeter.java` và `Demo.java` không phụ thuộc trực tiếp vào tên `CallTrace`.

### 6.4. Chạy Java thường trước khi weave

Đặt file `Greeter.java` và `Demo.java` vào thư mục hiện tại, chạy:

~~~bash
javac --release 17 Greeter.java Demo.java
java Demo
~~~

**Stdout không có aspect:**

~~~text
domain: greet
main: Hello An
main: rejected empty name
~~~

Đây là phép kiểm chứng rằng code nghiệp vụ đứng độc lập, và chạy `java` với class chưa weave không tự kích hoạt file `.aj` chỉ nằm trên đĩa.

### 6.5. Biên dịch/weave với `ajc`

Chuẩn bị hai artifact cùng phiên bản AspectJ:

- `aspectjtools.jar`: chứa compiler/weaver `org.aspectj.tools.ajc.Main`.
- `aspectjrt.jar`: thư viện runtime mà class đã weave có thể cần tại khi chạy.

Dùng một bản phát hành AspectJ hỗ trợ Java 17 và bộ JDK đang sử dụng. Ví dụ dưới đặt hai JAR trong thư mục `lib` với tên đã rút gọn thành `aspectjtools.jar` và `aspectjrt.jar`. Tạo thư mục `woven` và chạy trên **Windows PowerShell**:

~~~powershell
New-Item -ItemType Directory -Force woven | Out-Null
java -cp "lib\aspectjtools.jar" org.aspectj.tools.ajc.Main -17 -classpath "lib\aspectjrt.jar" -d woven Greeter.java Demo.java CallTrace.aj
java -cp "woven;lib\aspectjrt.jar" Demo
~~~

Trên macOS/Linux, classpath lúc chạy dùng dấu `:`:

~~~bash
mkdir -p woven
java -cp "lib/aspectjtools.jar" org.aspectj.tools.ajc.Main -17 -classpath "lib/aspectjrt.jar" -d woven Greeter.java Demo.java CallTrace.aj
java -cp "woven:lib/aspectjrt.jar" Demo
~~~

**Stdout khi đã weave:**

~~~text
trace: before
domain: greet
trace: after
main: Hello An
trace: before
trace: error
main: rejected empty name
~~~

Không có lệnh gọi `CallTrace` trong `Demo`. Sự khác biệt đầu ra đến từ bytecode method `Greeter.greet` đã được `ajc` weave: mỗi lần thực thi method khớp pointcut kích hoạt advice. Lời gọi thành công đi qua `before -> proceed -> after`; lời gọi không hợp lệ đi qua `before -> proceed -> exception -> error` rồi caller bắt exception gốc.

**Cần lưu ý đường chạy:** output `woven` phải được đặt trước các thư mục/JAR chứa phiên bản `Greeter.class` chưa weave trên classpath. Nếu JVM load class gốc từ thư mục hiện tại trước, bạn có thể tưởng weaving “không hoạt động” dù bước compile đã thành công. Các ví dụ sử dụng classpath tường minh chỉ gồm `woven` và runtime.

## 7. Phân tích chính xác điều diễn ra trong ví dụ

### 7.1. Join point không phải là object proxy

`new Greeter()` tạo đối tượng Java thông thường. Aspect advice không được đăng ký bằng `new Wrapper(greeter)` và `Demo` không gọi API reflection/proxy nào. Weaver đưa lời khuyên vào bytecode tương ứng với method execution.

Điều này giải thích vì sao các lời gọi hợp lệ tới method đã weave có thể được quan sát bất kể caller trực tiếp tạo object hay gọi nội bộ. Đừng suy ra mọi bytecode của mọi dependency đều được weave: phạm vi lớp đầu vào cho build hay lớp được load qua weaving loader vẫn quyết định.

### 7.2. Đường thành công và lỗi

- Khi `greet("An")` chạy, advice in `trace: before`.
- `proceed(name)` cho logic gốc chạy: in `domain: greet` và trả `Hello An`.
- Advice in `trace: after` rồi trả lại chính giá trị đó.
- `Demo` in kết quả.
- Với `greet("   ")`, advice in `trace: before`, logic gốc ném `IllegalArgumentException` trước khi in dòng domain.
- Advice in `trace: error` rồi ném lại lỗi; `Demo` bắt và in `main: rejected empty name`.

Đối với advice production, bảo đảm lỗi từ emitter/tracing không vô tình thay đổi kết quả nghiệp vụ nếu telemetry chỉ có trách nhiệm quan sát. Ví dụ cố ý dùng `System.out.println` để minh họa; logging thật cần xử lý overload và data privacy.

### 7.3. Vì sao `proceed` cần hợp đồng?

Advice `around` có thể:

- Không gọi `proceed`, ví dụ do cache hit, từ chối quyền hoặc circuit breaker.
- Gọi một lần (quan sát đơn giản).
- Gọi nhiều lần (retry nếu operation có thể retry và có idempotency).
- Thay đối số hoặc kết quả (chỉ khi hợp đồng cho phép).

Những khả năng này là **quyền năng**, không phải khuyến nghị mặc định. Việc dùng AspectJ không cấp quyền tự ý nuốt exception, chặn validation nghiệp vụ, thay đổi số tiền, đảo ngược side effect hoặc tạo success giả.

## 8. Annotation-style aspect và code-style aspect

AspectJ hỗ trợ **code style** qua file `.aj` (như `CallTrace.aj`) và **annotation style** qua class Java có annotation như `@Aspect`, `@Pointcut`, `@Before` và `@Around`. Hai cách biểu đạt có thể tham gia weaving nếu được cấu hình đúng.

Ví dụ annotation-style ngắn:

~~~java
// Minh họa API AspectJ: cần dependency aspectjrt và cấu hình weaving.
// Đây KHÔNG là file độc lập có thể chạy bằng javac một mình.
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class BasicTrace {
    @Before("execution(* Greeter.greet(..))")
    public void beforeGreeting() {
        System.out.println("trace: entering greet");
    }
}
~~~

**Viết class có `@Aspect` không tự làm weaving xảy ra khi chỉ chạy `javac` rồi `java`.** Vẫn cần compiler/weaver hoặc LTW phù hợp. Đây là khác biệt giữa **mô tả advice** và **kích hoạt interception**.

Code-style `.aj` cung cấp cú pháp ngôn ngữ AspectJ trực tiếp, và có thể biểu đạt thêm nhiều cấu trúc như inter-type declaration. Annotation style hữu ích khi muốn khai báo aspect bằng Java syntax thông thường, nhưng các khả năng cú pháp không đồng nhất hoàn toàn; cần kiểm tra tính năng cụ thể trong tài liệu công cụ.

## 9. Post-compile weaving: khi mã Java đã biên dịch

Nếu muốn giữ bước biên dịch Java bằng `javac`, có thể compile app trước rồi dùng `ajc` weave bytecode có sẵn.

Giả định `Greeter.java`, `Demo.java` và `CallTrace.aj` như ví dụ, đã có `lib/aspectjtools.jar` và `lib/aspectjrt.jar` cùng phiên bản. Lệnh mang tính mẫu trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force plain, woven | Out-Null
javac --release 17 -d plain Greeter.java Demo.java
java -cp "lib\aspectjtools.jar" org.aspectj.tools.ajc.Main -17 -classpath "lib\aspectjrt.jar" -inpath plain -d woven CallTrace.aj
java -cp "woven;lib\aspectjrt.jar" Demo
~~~

`-inpath plain` là nơi `ajc` nhận **bytecode đầu vào** để weave. `CallTrace.aj` vẫn là nguồn AspectJ cần compile trong bước này. Trong dự án thực, phải phân biệt source path, classpath, aspectpath, inpath và output để không vô ý weave trùng hoặc bỏ sót class.

Để chắc chắn kết quả, so sánh hành vi với bản `plain`, bật báo cáo join point khi compile, và chỉ đóng gói artifact `woven` khi muốn chạy mã đã weave. Khi vừa có class nguyên bản vừa có bản woven trên classpath, thứ tự class loading có thể làm test sai.

## 10. Load-time weaving: cấu hình và giới hạn

Khi cần weave lúc class được định nghĩa, có thể dùng `aspectjweaver.jar` làm Java agent. Đây là **một lựa chọn khác với CTW**, không cần kết hợp cả hai lên cùng class chỉ để bài demo hoạt động.

Ví dụ `META-INF/aop.xml` tối giản:

~~~xml
<aspectj>
  <aspects>
    <aspect name="CallTrace"/>
  </aspects>
  <weaver options="-showWeaveInfo">
    <include within="Greeter"/>
  </weaver>
</aspectj>
~~~

Đây là cấu hình minh họa cho bản aspect đã được **biên dịch và đặt trên classpath**. File `META-INF/aop.xml` cũng cần xuất hiện đúng trên classpath để LTW đọc được. `<include within="Greeter"/>` nhằm giới hạn class được weave; trong dự án có package, nên chọn pattern package/class thích hợp thay vì để phạm vi mặc định quá rộng.

Một lệnh khởi động có hình thức:

~~~powershell
java -javaagent:lib\aspectjweaver.jar -cp "classes;lib\aspectjrt.jar;aspects" Demo
~~~

Trong đó `classes` chứa class gốc, `aspects` chứa aspect đã biên dịch và `META-INF/aop.xml`, và `lib` chứa các JAR AspectJ tương thích. Chỉ lệnh trên không tự tạo những thư mục đó: phải build và đóng gói đúng các thành phần trước. **Không dùng output CTW đã weave lại trong ví dụ LTW này** nếu mục tiêu là kiểm chứng thuần LTW.

Các vấn đề LTW cần biết:

1. JVM cần được khởi động với agent khi thích hợp, trước khi những class cần weave được định nghĩa.
2. Phạm vi type được weave chịu ảnh hưởng class loader delegation và bộ lọc include/exclude.
3. Aspect phải hiện diện với loader/weaver ở giai đoạn cần thiết, và version artifact phải tương thích.
4. Một số môi trường ứng dụng dùng nhiều class loader, module, instrumentation hoặc các bytecode transformer khác; thứ tự và tính tương thích cần được kiểm chứng riêng.
5. Bật LTW trên toàn bộ dependency mà không giới hạn có thể làm tăng chi phí startup và chọn cả join point ngoài ý định.
6. “Agent chạy được” chưa chứng minh mọi pointcut đã match; phải kiểm tra weave info và test hành vi thực tế.

Khi triển khai, nên kiểm tra tài liệu phát triển của AspectJ tương ứng với phiên bản sử dụng, nhất là yêu cầu của JDK, class loader và mô hình đóng gói. **Phần demo CTW ở mục 6 là ví dụ độc lập chính**; cấu hình LTW trong mục này mô tả cơ chế và cần điều chỉnh cho hệ thống cụ thể.

## 11. Đi sâu hơn: binding, context và các loại advice

### 11.1. Bind đối số và lấy metadata

Trong ví dụ, `args(name)` liên kết đối số method với tham số advice để sử dụng trong `proceed(name)`. AspectJ cũng cung cấp metadata của join point, chẳng hạn `thisJoinPointStaticPart`, hữu ích để lấy chữ ký tĩnh cho log/metric.

Minh họa:

~~~java
// Đoạn trong advice code-style, không phải chương trình độc lập:
String signature = thisJoinPointStaticPart.getSignature().toShortString();
~~~

Không tự động đưa `name`, `args` hoặc `toString()` của toàn object vào telemetry. Advice có thể nằm ở vị trí xem dữ liệu nhạy cảm, do đó cần allowlist, giới hạn dung lượng và context tin cậy. Đọc chữ ký tĩnh thường đủ để tạo nhãn operation; full stack trace và reflection sâu có thể đắt đỏ.

### 11.2. Advice trước/sau và trường hợp lỗi

| Advice | Thời điểm | Gợi ý |
| --- | --- | --- |
| `before` | Trước join point | Kiểm tra điều kiện, đánh dấu bắt đầu |
| `after returning` | Chỉ khi trả về bình thường | Ghi nhận kết quả thành công theo contract |
| `after throwing` | Khi kết thúc bằng exception phù hợp | Ghi số lỗi, không tự nuốt lỗi |
| `after` | Kết thúc bất kể đường bình thường/lỗi | Cleanup/tổng kết |
| `around` | Bao trùm join point | Đo thời gian, kiểm soát `proceed`/retry/fallback khi được phép |

Nếu method trả một `Future` hoặc `CompletionStage` ngay lập tức, `after returning` ở method execution chỉ quan sát được **handle**, chưa chắc quan sát được kết quả cuối của phép tính. Đo operation bất đồng bộ cần instrumentation ở ranh giới completion/callback đúng, với logic kết thúc một lần và giữ nguyên semantics hủy/lỗi.

### 11.3. Scope/lifecycle của aspect instance

Trong AOP nói chung, có thể có chính sách aspect instance dùng chung, theo đối tượng hoặc theo control flow. AspectJ có các cơ chế như `perthis`, `pertarget`, `percflow`, `pertypewithin` ở những ngữ cảnh phù hợp. Đừng giả định mỗi method invocation có một aspect object mới.

State dạng field dùng chung trong singleton aspect phải thread-safe; principal, start time và request-local context không nên giữ trong field dùng chung nếu có nhiều luồng. `ThreadLocal` cũng không tự truyền sang executor khác và phải được khôi phục/xóa đúng lúc để tránh rò dữ liệu giữa request.

## 12. Inter-type declaration: một khả năng nâng cao của weaving

Ngoài advice, AspectJ có **inter-type declarations (ITD)** cho phép giới thiệu member/quan hệ kiểu ở những class được chọn. Minh họa code-style:

~~~java
// Giả sử class Account tồn tại.
// Đoạn .aj minh họa, cần class Account và ajc để biên dịch.
public aspect AccountTag {
    public String Account.describe() {
        return "account";
    }
}
~~~

Sau khi aspect được kết hợp bằng compiler/weaver theo điều kiện phù hợp, class được giới thiệu method có thể có hành vi mà mã nguồn `Account` ban đầu không khai báo. Đây là ví dụ rất rõ ràng rằng weaving có thể **tác động đến cấu trúc type**, chứ không chỉ chặn method call.

Tuy vậy, ITD làm tăng sự phụ thuộc ngầm: người đọc class gốc có thể không thấy member được thêm, IDE/compile pipeline phải hiểu nguồn aspect, và binary compatibility hoặc xung đột tên member phải được kiểm thử. Nếu code Java cần gọi `account.describe()` tại compile time, compiler phải nhìn thấy giới thiệu member đó ở bước phù hợp; chỉ để LTW chạy quá muộn không khiến `javac` thông thường chấp nhận member chưa tồn tại.

Không dùng ITD chỉ để “tiện thêm helper”; cân nhắc liệu một interface, adapter hay method trong chính domain model có diễn đạt hợp đồng rõ ràng hơn không.

## 13. Thứ tự nhiều aspect: composition và precedence

Một join point có thể match nhiều aspect, ví dụ tracing, metric và kiểm soát quyền. Thứ tự advice ảnh hưởng mạnh đến semantics:

~~~text
Metrics -> Authorization -> Domain
         deny: metrics thấy denial

Authorization -> Metrics -> Domain
         deny: metrics bên trong không chạy
~~~

Nếu cache được đặt **ngoài** aspect kiểm tra quyền và trả về khi cache hit, lời gọi có thể bypass quyền. Nếu retry nằm ngoài transaction, mỗi attempt có thể có transaction riêng; nếu retry nằm bên trong transaction, nhiều attempt có thể dùng chung trạng thái giao dịch không phù hợp.

AspectJ có cách mô tả precedence giữa các aspect. Ví dụ cú pháp:

~~~java
// Ví dụ khai báo trong một aspect .aj.
// Các aspect trong ví dụ phải tồn tại; đoạn này không độc lập.
declare precedence: SecurityAspect, MonitoringAspect, *;
~~~

Khai báo này cho ý tưởng ưu tiên `SecurityAspect` trước `MonitoringAspect` và các aspect còn lại; **thứ tự quanh một join point** phải được kiểm tra theo before/after/around của từng advice. Với aspect có ưu tiên cao hơn, phần advice trên đường vào có thể chạy sớm hơn, còn hành vi trên đường ra có thể diễn ra theo thứ tự đảo. Không suy luận bằng cách chỉ nhìn tên aspect; cần integration test thể hiện chuỗi thực thi.

Nếu nhiều pointcut chồng lấn bất ngờ, một operation có thể bị ghi log hai lần hoặc bị retry nhiều tầng. Cần định nghĩa chính sách order công khai, tránh rely vào thứ tự load tình cờ và thiết kế pointcut đủ hẹp.

## 14. Hiệu năng và độ tin cậy của weaving

Weaving thêm bytecode vào những vị trí được chọn. Do đó overhead phụ thuộc **số join point thực sự bị chặn**, nội dung advice, biến động class loading và phần việc telemetry; không thể khẳng định weaving tự động có overhead bằng 0.

Các bẫy thường gặp:

- **Pointcut quá rộng:** `execution(* *(..))` không giới hạn có thể match các method không liên quan, thậm chí telemetry code tạo recursion. Giới hạn package, type, annotation hoặc method contract.
- **Advice quá nặng:** serialize toàn bộ argument, log stack trace mọi lời gọi, truy vấn network đồng bộ ở hot path.
- **Nhiều aspect chồng lấn:** cùng một lời gọi phát nhiều event, tạo hai transaction hoặc retry không mong muốn.
- **State không an toàn đồng thời:** aspect singleton lưu `startNanos`/principal dưới field mutable chung.
- **Giấu exception:** lời gọi nguyên bản ném lỗi nhưng advice trả fallback giả thành công, làm thay đổi hợp đồng nghiệp vụ.
- **Sai classpath:** chạy bản class chưa weave hoặc đưa hai bản cùng tên lên classpath; kết quả khác build đã kiểm chứng.
- **Sai loader/phạm vi LTW:** class đã load trước, nằm trong parent loader hoặc không match bộ lọc.
- **Weave lại không kiểm soát:** pipeline dùng class đã weave làm input, gây cấu trúc không mong muốn hoặc sinh thông báo xung đột.
- **Không theo dõi lỗi weaving:** deployment vẫn chạy nhưng quan sát/bảo vệ phụ thuộc advice không còn hoạt động.

Một Aspect quan sát nên có cơ chế giảm log (sampling/quota), đo overhead, loại trừ chính package hạ tầng của nó khi cần và không thu thập dữ liệu nhạy cảm bằng reflection mặc định.

## 15. Cách xác nhận aspect đã được weave

Không nên kết luận thành công chỉ vì chạy `ajc` không báo lỗi. Một quy trình kiểm chứng tốt:

1. **Đọc pointcut được chọn:** chữ ký có khớp kiểu trả về, visibility, static/instance, package, số tham số không?
2. **Bật thông tin weaver:** dùng `-showWeaveInfo` khi build hoặc `-showWeaveInfo` trong tùy chọn weaver khi LTW.
3. **Chạy đối chứng trước và sau weaving:** mã nghiệp vụ giống nhau, output có/không có advice đúng chỗ.
4. **Test các đường đi:** success, exception, self-call, overload, method từ subclass, async completion và cache hit nếu policy liên quan.
5. **Kiểm tra runtime classpath:** đúng class đã weave, đúng version runtime và đúng class loader.
6. **Kiểm tra giới hạn:** method đích không được weave, caller chưa weave với `call`, class được load trước agent hoặc API nằm ngoài pattern.
7. **Tập trung observable contract:** giá trị trả về, loại lỗi, số side effect, số lần proceed và trạng thái giao dịch phải đúng.
8. **Theo dõi build/deployment:** thông tin weaving nên đủ để phát hiện khi nâng JDK, đổi package hoặc nâng dependency làm pointcut không còn match.

Một số tùy chọn có thể dùng trong môi trường phù hợp:

~~~bash
# Ví dụ thêm tùy chọn vào lệnh ajc ở mục 6:
# -showWeaveInfo: hiện các join point được weave.
# -Xlint: thêm cảnh báo cấu hình/weaving liên quan.
~~~

Đây là chú giải tùy chọn, không phải lệnh chạy độc lập. Kiểm tra chính xác syntax/version từ help `ajc -help` và tài liệu của bản AspectJ đang dùng.

### 15.1. Bộ kiểm thử định hướng hành vi

| Tình huống | Cần chứng minh |
| --- | --- |
| Method hợp lệ với CTW | Advice chạy đúng một lần, kết quả không đổi |
| Method ném exception | Advice ghi lỗi, caller nhận exception gốc |
| Method không thuộc pointcut | Không bị chặn |
| Method overload | Pattern chọn đúng overload cần quan sát |
| Self-call | `execution` của method được weave vẫn chạy |
| Caller chưa weave + `call` | Không suy luận sai rằng call site đã được chặn |
| Hai advice cùng join point | Thứ tự/precedence đúng hợp đồng |
| LTW thiếu aspect/aop.xml | Test phát hiện khi lớp không được weave |
| Classloader khác | Phạm vi weaving được xác nhận theo topology loader |
| Async trả handle | Không báo “business success” trước completion thật |
| Retry hoặc cache hit | Không phát side effect trùng hoặc bypass policy |
| Tải cao | Overhead, memory, telemetry loss được đo và giới hạn |

## 16. Lỗi tư duy phổ biến

**“Chỉ cần thêm file aspect là runtime tự chạy.”** Sai. Phải có compilation/weaving phù hợp; annotation hay file `.aj` chỉ biểu diễn chính sách, không thay thế bước tích hợp.

**“Weaving là một proxy trong suốt.”** Sai về cơ chế. Weaving tác động bytecode ở join point, trong khi proxy thường chỉ intercept lời gọi đi qua đối tượng đại diện.

**“`call` và `execution` như nhau.”** Sai. Call join point nằm ở vị trí gọi; execution ở thân method. Điều này ảnh hưởng cần weave class nào và phát hiện self-invocation.

**“LTW thay mọi class đang chạy.”** Sai. LTW làm việc khi class được định nghĩa trong phạm vi class loader/weaver; class đã load trước có thể không được can thiệp.

**“Advice là kỹ thuật nên được phép đổi kết quả nghiệp vụ.”** Sai. Around advice có thể thay hành vi nhưng chỉ nên làm vậy nếu contract công khai cho phép. Ngay cả log cũng không được làm lộ thông tin hoặc gây lỗi mới che mất lỗi gốc.

**“Pointcut rộng giúp không bỏ sót gì.”** Không hẳn. Pointcut quá rộng dễ gây recursion, overhead, chặn sai domain semantics và khó bảo trì. Bao phủ đúng là một yêu cầu có test, không phải vấn đề số lượng join point tối đa.

**“Sau khi method trả về là operation đã kết thúc.”** Với API bất đồng bộ, chỉ có handle được trả; hoàn tất vật lý và hoàn tất logic có thể đến về sau.

**“Compiler báo thành công nghĩa là kết quả chắc đã weave đúng.”** Không đủ. Một pattern không khớp hoặc classpath sai có thể làm chương trình chạy thiếu advice; cần báo cáo weaver và kiểm thử đối chứng.

## 17. Checklist kiến trúc AOP có weaving

Trước khi đưa một aspect vào dự án, hãy ghi lại:

1. **Chính sách:** concern nào được mô-đun hóa? Hợp đồng của advice có được phép block, retry, mutate hay fallback không?
2. **Join point:** `execution` hay `call`, constructor/field hay loại khác? Vì sao chọn loại ấy?
3. **Pointcut scope:** chọn package, class, method, annotation và exclusion nào? Có method mới hoặc overload bị match sai không?
4. **Weaving stage:** CTW, binary weaving hay LTW? Đầu vào/đầu ra và loader nào chịu trách nhiệm?
5. **Dependencies:** phiên bản compiler/weaver/runtime có tương thích JDK và nhau không?
6. **Advice contract:** `proceed` chạy bao nhiêu lần; exception, return, side effect và async completion được bảo toàn như thế nào?
7. **State/thread-safety:** aspect instance có chia sẻ không, per-invocation state đặt ở đâu, context qua thread được phục hồi ra sao?
8. **Composition:** nhiều aspect có thứ tự nào, có cache-hit bypass authorization hay double retry không?
9. **Observability và bảo mật:** log/metric nào được phép, mức overhead và dữ liệu nhạy cảm được kiểm soát ra sao?
10. **Validation:** có so sánh trước/sau weaving, showWeaveInfo, test self-call/exception/async và kiểm tra startup/classpath chưa?

### Tài liệu tham khảo

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/index.html) — thuật ngữ AOP, pointcut, advice, join point và các đặc trưng ngôn ngữ.
- [AspectJ Development Guide: ajc](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html) — cách biên dịch, inpath, cảnh báo và tùy chọn weaver.
- [AspectJ Development Guide: Load-Time Weaving](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html) — CTW, binary weaving, LTW, agent, class loader và cấu hình.

**Ý chính cần giữ:** AspectJ minh họa rất rõ AOP có weaving vì khái niệm “chọn join point rồi kết hợp advice” được thể hiện thành bytecode thực thi, không chỉ là cách gói một object. Nắm được **các loại join point, thời điểm weaving, phạm vi class được weave, thứ tự advice và hợp đồng nghiệp vụ** mới là nền tảng để dùng AOP an toàn, hiệu quả và có thể kiểm chứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-002">Aspect bằng cú pháp chuyên biệt và bằng annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Aspect bằng cú pháp chuyên biệt và bằng annotation trong AspectJ

## 1. Tổng quan và mục tiêu

**Aspect-Oriented Programming (AOP)** cho phép mô tả những hành vi cắt ngang nhiều module, chẳng hạn quan sát lời gọi, ghi audit, áp chính sách và đo thời gian, bằng **aspect**, **pointcut** và **advice**. **AspectJ** mở rộng khả năng diễn đạt AOP của Java và cung cấp compiler/weaver để gắn các hành vi này vào các vị trí chương trình phù hợp.

Một kiến thức quan trọng khi học AspectJ là **cùng một aspect có thể được biểu diễn theo hai phong cách**:

1. **Code-style**: cú pháp ngôn ngữ AspectJ chuyên biệt dùng `aspect`, `pointcut`, `before`, `after`, `around` và `declare`. File thường mang đuôi `.aj`, phải được công cụ hiểu cú pháp AspectJ xử lý.
2. **Annotation-style (@AspectJ)**: aspect là một lớp Java gắn `@Aspect`; khai báo điểm chặn bằng `@Pointcut`; advice bằng `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`, `@Around`. Java compiler có thể biên dịch class annotation phù hợp, nhưng advice chỉ có hiệu lực khi AspectJ **weave** bytecode.

Bài này tập trung vào **AOP nói chung với AspectJ**, bao gồm kiến thức cơ bản và cơ chế nâng cao. Sau khi học, bạn có thể:

- Đối chiếu chính xác phần tử của hai cú pháp.
- Hiểu `execution`, `call`, bind `args`, nhận return value và phân biệt các loại advice.
- Chạy hai phiên bản aspect có cùng join point/advice trên Java 17.
- Giải thích vì sao chỉ có `@Aspect` và chạy `javac` chưa đủ để bật advice.
- Chọn đúng thời điểm weaving: compile-time, binary/post-compile và load-time.
- Biết khi nào code-style có khả năng biểu đạt tốt hơn đối với inter-type declaration, `declare` và privileged aspects.
- Tránh các lỗi về signature, binding tên tham số, overloading, thứ tự advice, aspect scope và concurrency.

**Điều cần nhớ từ đầu:** lựa chọn code-style hay annotation-style là lựa chọn **cách khai báo source**. Nó độc lập với việc dùng compile-time hay load-time weaving và không tự xác định tập join point nào sẽ hoạt động.

## 2. Những thành phần AOP được biểu diễn

| Thuật ngữ | Ý nghĩa | Ví dụ |
|---|---|---|
| **Join point** | Một điểm/sự kiện thực thi mà AspectJ có thể quan sát | Execution của `TaskService.perform` |
| **Pointcut** | Quy tắc chọn join point và, nếu cần, bind ngữ cảnh | `execution(String TaskService.perform(String))` |
| **Advice** | Hành vi chạy tại join point đã chọn | In `enter` trước method |
| **Aspect** | Đơn vị đóng gói pointcut, advice, state và khai báo liên quan | `NativeTrace` hoặc `AnnotationTrace` |
| **Weaving** | Tích hợp hành vi advice vào bytecode mục tiêu | `ajc` compile và weave `TaskService` |
| **Target** | Lớp/phương thức thực thi nghiệp vụ | `TaskService.perform` |
| **Static crosscutting** | Tác động tới cấu trúc kiểu hoặc báo lỗi kiến trúc | Inter-type method, `declare warning` |

Một lời gọi sau weaving có thể được xem là:

```text
App.main()
  |
  +--> TaskService.perform("A-7")
         |-- before advice: enter:A-7
         |-- method body: return "done:A-7"
         |-- after returning advice: exit:done:A-7
       App in result: done:A-7
```

Aspect không chỉ là “wrapper gọi hàm”. Với bytecode weaving, AspectJ có thể nhận diện nhiều loại join point, bao gồm execution, call và một số thao tác khác trong phạm vi mà weaver kiểm soát. Không giả định mọi công cụ AOP đều hỗ trợ tất cả loại join point này.

## 3. Cú pháp code-style: ngôn ngữ AspectJ

Một tệp `.aj` có thể chứa khai báo:

```java
// Đây là cú pháp AspectJ, không phải Java thuần cho javac.
public aspect ExampleAspect {
    pointcut selected():
        execution(* demo.Service.execute(..));

    before(): selected() {
        System.out.println("before");
    }

    after() returning: selected() {
        System.out.println("success");
    }
}
```

Ý nghĩa các phần:

- `public aspect ExampleAspect` khai báo một **aspect type**. Theo mặc định, nó theo mô hình instance singleton, trừ khi có per-clause khác.
- `pointcut selected(): ...;` đặt tên một quy tắc. Dấu `:` ngăn formal part với pointcut expression.
- `execution(* demo.Service.execute(..))` match việc thực thi method `execute` của type được chỉ định; `*` là return type bất kỳ và `..` trong danh sách đối số thể hiện số lượng/kiểu tham số bất kỳ.
- `before(): selected() { ... }` chạy trước join point.
- `after() returning: selected() { ... }` chạy khi join point kết thúc bình thường. Nếu có exception, advice kiểu `returning` không phải nhánh ghi lỗi.
- Nếu cần nắm kết quả trả về, có thể khai báo biến binding trong `returning(...)`.

Đây là **phần mở rộng ngôn ngữ Java**, nên `javac` không biên dịch được file chứa `aspect` hoặc cú pháp advice chuyên biệt. Sử dụng `ajc` để biên dịch các nguồn này và weave những lớp đích cần thiết.

## 4. Cú pháp annotation-style: Java class có metadata AspectJ

Một ví dụ tương ứng:

```java
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class ExampleAnnotationAspect {
    @Pointcut("execution(* demo.Service.execute(..))")
    public void selected() {}

    @Before("selected()")
    public void beforeSelected() {
        System.out.println("before");
    }
}
```

Đặc điểm:

- `@Aspect` thuộc namespace `org.aspectj.lang.annotation` và đánh dấu một lớp Java là aspect để AspectJ weaver xử lý.
- `@Pointcut` được khai báo trên Java method, có **thân rỗng**. Đây là một tên pointcut, không phải method nghiệp vụ phải gọi để kích hoạt advice.
- `@Before` nhận một pointcut expression trong chuỗi; `"selected()"` tham chiếu pointcut đã đặt tên.
- Các advice khác được biểu diễn bởi annotation riêng; phần thân advice là Java method.
- Class này có thể compile bằng `javac` nếu đã có AspectJ runtime API trên classpath và code không dùng cú pháp ngôn ngữ mở rộng.
- **Compilation và weaving là hai công việc khác nhau**: biên dịch ra class với annotation chưa tự sửa bytecode của target.

Hai phong cách có thể tạo cùng semantics khi được weaver xử lý đúng. Annotation-style hữu ích nếu muốn dùng các công cụ biên tập Java thông thường và tách giai đoạn compile source khỏi weave các class đã biên dịch.

## 5. Đối chiếu cấu trúc giữa hai phong cách

| Nhiệm vụ | Code-style | Annotation-style |
|---|---|---|
| Aspect | `public aspect X { }` | `@Aspect public class X { }` |
| Pointcut có tên | `pointcut work(): expression;` | `@Pointcut("expression") void work() {}` |
| Trước join point | `before(): work() { }` | `@Before("work()") void before() { }` |
| Sau thành công | `after() returning: work() { }` | `@AfterReturning("work()") void after() { }` |
| Sau khi lỗi | `after() throwing: work() { }` | `@AfterThrowing("work()") void afterError() { }` |
| Sau join point nói chung | `after(): work() { }` | `@After("work()") void after() { }` |
| Around | `T around(...): work(...) { return proceed(...); }` | `@Around("work(...)") Object around(ProceedingJoinPoint p) throws Throwable` |
| Join point hiện tại | `thisJoinPoint` | Tham số `JoinPoint` / `ProceedingJoinPoint` |
| Bind đối số | Formal trong `pointcut`/advice và `args(...)` | Formal Java method trong `@Pointcut`/advice |
| Thêm inter-type method | Cú pháp khai báo trực tiếp được hỗ trợ | Không viết trực tiếp cùng cú pháp bằng Java thuần; có cơ chế annotation hỗ trợ một số dạng |
| Quy tắc kiến trúc | Các `declare` của AspectJ | Một số `@Declare...`, tùy loại và phiên bản |
| Compile source | Cần compiler hiểu cú pháp AspectJ | Có thể compile bằng `javac` với phụ thuộc phù hợp |
| Kích hoạt advice | Weaving | **Vẫn phải weaving** |

Không xem bảng là một bản dịch “thay từ khóa bằng annotation” cho mọi tính năng. Code-style có những khả năng tĩnh mà Java annotation không thể mô tả hoàn toàn, trong khi việc cấu hình metadata và bind formal của annotation-style có những yêu cầu riêng.

## 6. Ví dụ chạy được: cùng thao tác, hai cách viết

Để so sánh công bằng, hãy dùng **cùng hai file nghiệp vụ** và compile **hai aspect vào hai output directory tách biệt**. Như vậy không có chuyện hai aspect đồng thời in log khiến ta tưởng một aspect chạy hai lần.

Tạo các file sau trong cùng thư mục làm việc.

### TaskService.java

```java
public class TaskService {
    public String perform(String id) {
        return "done:" + id;
    }
}
```

`TaskService.perform("A-7")` trả chuỗi `done:A-7` và không tự ghi log. Không cần sửa mã nghiệp vụ để thêm advice.

### App.java

```java
public class App {
    public static void main(String[] args) {
        System.out.println(new TaskService().perform("A-7"));
    }
}
```

Khi chạy `App` với `TaskService` chưa được weave, stdout chỉ gồm:

```text
done:A-7
```

### NativeTrace.aj — Code-style

```java
public aspect NativeTrace {
    pointcut operation(String id):
        execution(String TaskService.perform(String)) && args(id);

    before(String id): operation(id) {
        System.out.println("enter:" + id);
    }

    after(String id) returning(String result): operation(id) {
        System.out.println("exit:" + result);
    }
}
```

Phân tích:

1. `pointcut operation(String id)` có formal `id` kiểu `String`.
2. `execution(String TaskService.perform(String))` match chính xác việc thực thi method nhận một `String` và trả `String`.
3. `args(id)` bind đối số runtime vào formal của pointcut.
4. `before(String id): operation(id)` nhận giá trị đã bind và in `enter:A-7`.
5. `after(String id) returning(String result): operation(id)` chạy sau khi method thành công; bind kết quả vào `result`.
6. Phần mã nghiệp vụ trả lại `done:A-7` cho `App`, rồi `App` in dòng cuối.

Nếu body ném ngoại lệ, `after returning` không thực thi; muốn ghi lỗi thì dùng `after throwing` hoặc around advice theo hợp đồng.

### AnnotationTrace.java — Annotation-style

```java
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AnnotationTrace {
    @Pointcut("execution(String TaskService.perform(String)) && args(id)")
    public void operation(String id) {}

    @Before("operation(id)")
    public void before(String id) {
        System.out.println("enter:" + id);
    }

    @AfterReturning(pointcut = "operation(id)", returning = "result")
    public void after(String id, String result) {
        System.out.println("exit:" + result);
    }
}
```

Cấu trúc tương ứng:

- `@Pointcut("... && args(id)")` khai báo điểm chặn và tên formal được bind.
- `@Before("operation(id)")` chọn cùng tập execution join point.
- `@AfterReturning(pointcut = "operation(id)", returning = "result")` lấy return value vào tham số method tên `result`.
- Method advice là method Java bình thường về cú pháp, nhưng để nó được tự động gọi tại join point thì **weaver** phải nhận ra annotation và xử lý class đích.
- Tên `result` trong annotation cần khớp với tên formal; thiếu metadata tên tham số ở một số quy trình compile riêng rẽ có thể khiến binding thất bại.

Đây là một trường hợp hai phong cách có **ngữ nghĩa quan sát giống nhau**.

## 7. Cài đặt công cụ và cách chạy trên Java 17

Các ví dụ ở trên đã được kiểm tra bằng **AspectJ 1.9.24** với `-17`, và chạy trên JVM có hỗ trợ Java 17 trở lên. Cần có `aspectjtools` (chứa compiler `ajc`) và `aspectjrt` ở giai đoạn thích hợp.

Có thể tải hai dependency bằng Maven:

```bash
mvn -B dependency:get -Dartifact=org.aspectj:aspectjtools:1.9.24 -Dtransitive=false
mvn -B dependency:get -Dartifact=org.aspectj:aspectjrt:1.9.24 -Dtransitive=false
```

Lệnh được viết để sử dụng local Maven repository mặc định. Nếu cấu hình của môi trường dùng repository khác, hãy điều chỉnh đường dẫn jar tương ứng.

### 7.1. Windows PowerShell: compile và chạy code-style

```powershell
$repo = Join-Path $HOME ".m2/repository/org/aspectj"
$tools = Join-Path $repo "aspectjtools/1.9.24/aspectjtools-1.9.24.jar"
$rt = Join-Path $repo "aspectjrt/1.9.24/aspectjrt-1.9.24.jar"

New-Item -ItemType Directory -Force out-native | Out-Null
java -cp $tools org.aspectj.tools.ajc.Main -17 -classpath $rt -d out-native App.java TaskService.java NativeTrace.aj
java -cp "out-native;$rt" App
```

### 7.2. Windows PowerShell: compile và chạy annotation-style

```powershell
New-Item -ItemType Directory -Force out-annotation | Out-Null
java -cp $tools org.aspectj.tools.ajc.Main -17 -classpath $rt -d out-annotation App.java TaskService.java AnnotationTrace.java
java -cp "out-annotation;$rt" App
```

**Kết quả stdout đã kiểm tra của cả hai cách:**

```text
enter:A-7
exit:done:A-7
done:A-7
```

Các tùy chọn:

- `-17`: chọn mức ngôn ngữ Java 17 cho `ajc`.
- `-classpath $rt`: cung cấp AspectJ runtime API khi compile/weave.
- `-d out-native` hoặc `-d out-annotation`: giữ output riêng để **không weave chồng hai aspect tương đương**.
- `java -cp "out-native;$rt" App`: chạy class đã weave và có runtime API trên classpath.

Trên Linux/macOS, classpath dùng dấu `:` thay cho `;`. Ví dụ Bash:

```bash
REPO="$HOME/.m2/repository/org/aspectj"
TOOLS="$REPO/aspectjtools/1.9.24/aspectjtools-1.9.24.jar"
RT="$REPO/aspectjrt/1.9.24/aspectjrt-1.9.24.jar"

mkdir -p out-native out-annotation
java -cp "$TOOLS" org.aspectj.tools.ajc.Main -17 -classpath "$RT" -d out-native App.java TaskService.java NativeTrace.aj
java -cp "out-native:$RT" App

java -cp "$TOOLS" org.aspectj.tools.ajc.Main -17 -classpath "$RT" -d out-annotation App.java TaskService.java AnnotationTrace.java
java -cp "out-annotation:$RT" App
```

Các lệnh cần chạy tại thư mục chứa bốn file source, nhưng **mỗi lần compile chỉ đưa vào ba file cần thiết**.

## 8. Biên dịch annotation bằng javac chưa có nghĩa là đã weave

Để thấy khác biệt này, có thể biên dịch cả ba file Java bằng `javac` trước, rồi chạy ứng dụng:

```powershell
New-Item -ItemType Directory -Force out-javac, out-woven | Out-Null
javac --release 17 -g -parameters -cp $rt -d out-javac App.java TaskService.java AnnotationTrace.java
java -cp "out-javac;$rt" App
```

Stdout là:

```text
done:A-7
```

Không thấy `enter` và `exit` vì các class mới chỉ **được biên dịch Java**, chưa được weave. Sau đó dùng `ajc` để weave bytecode:

```powershell
java -cp $tools org.aspectj.tools.ajc.Main -17 -classpath "out-javac;$rt" -inpath out-javac -aspectpath out-javac -d out-woven
java -cp "out-woven;$rt" App
```

Kết quả sau weaving:

```text
enter:A-7
exit:done:A-7
done:A-7
```

Trong quy trình này:

- `-inpath out-javac` chỉ định các class đầu vào cần weave.
- `-aspectpath out-javac` giúp tìm compiled aspects để áp dụng.
- `-d out-woven` ghi kết quả ra thư mục mới, dễ phân biệt class gốc và class đã weave.
- `-g -parameters` giữ metadata hữu ích cho việc bind tên formal của pointcut/advice. Đã kiểm tra rằng bỏ metadata này trong quy trình demo có thể gây lỗi binding lúc weave.

Đừng nhầm `-aspectpath` với mọi dependency của ứng dụng; vẫn phải quản lý classpath cần thiết. Với build có nhiều module, tập class target và tập aspect có thể nằm ở các output riêng.

## 9. Ba thời điểm weaving và cách chúng liên quan cú pháp

| Cơ chế | Hoạt động | Lưu ý |
|---|---|---|
| **Compile-time weaving (CTW)** | `ajc` đọc source Java/AspectJ, compile và weave thành class | Cần đưa đủ target/aspect source vào build |
| **Binary/post-compile weaving** | `ajc` đọc `.class`/jar đã compile, chèn advice và xuất class mới | Tránh trộn class woven với class chưa woven hoặc weave trùng |
| **Load-time weaving (LTW)** | Weaver can thiệp khi JVM nạp class | Cần agent/config/classloader phù hợp; chỉ có runtime jar là chưa đủ |

`aspectjweaver` là thành phần phổ biến cho LTW; cấu hình triển khai thường dùng `-javaagent` và tệp cấu hình aspect, ví dụ `META-INF/aop.xml`. Đây là cơ chế riêng với `javac`/`ajc` compile ở build time.

Sơ đồ:

```text
Code-style .aj + Java target --ajc--> woven classes --> JVM

Annotation .java + Java target --ajc--> woven classes --> JVM

Annotation .java --javac--> plain classes --ajc(binary weave)--> woven classes --> JVM

Java/Aspect classes --> classloader + AspectJ weaving agent --> loaded woven classes
```

**Cả code-style lẫn annotation-style có thể được xử lý qua weaving phù hợp.** Không kết luận code-style bắt buộc dùng compile-time hay annotation-style bắt buộc dùng load-time.

## 10. Pointcut: cú pháp chung, ngữ nghĩa join point

Cả hai phong cách dùng **cùng ý tưởng về biểu thức pointcut**. Những designator tiêu biểu:

| Designator | Chọn theo |
|---|---|
| `execution(...)` | Execution của method thỏa chữ ký |
| `call(...)` | Vị trí gọi method; cần weave bytecode phía caller |
| `args(x)` | Tham số runtime và bind giá trị |
| `this(x)` | Ngữ cảnh đối tượng `this` theo join point |
| `target(x)` | Đối tượng đích theo join point |
| `within(TypePattern)` | Lexical type scope |
| `withincode(Signature)` | Lexical code body chứa join point |
| `@annotation(a)` | Annotation liên quan join point và có thể bind |
| `cflow(p)` / `cflowbelow(p)` | Ngữ cảnh luồng điều khiển theo ngữ nghĩa AspectJ |

**`execution` khác `call`**: advice ở phần thực thi của method không đồng nghĩa advice tại mọi call site. Một class caller chưa được weave có thể không có `call` advice, trong khi class target đã weave có thể có `execution` advice.

Giữa các kiểu join point, `this` và `target` có thể khác nhau. Không dùng interchangeably khi kiểm tra quyền hay trích principal/target. Với pointcut phụ thuộc annotation, phải biết annotation đặt ở interface, implementation, method override hay class; không được suy rằng Java tự kế thừa annotation method trên mọi nhánh override.

`args(id)` trong ví dụ nhận dữ liệu để advice in `enter:A-7`. Đối số thật có thể là token hoặc nội dung nhạy cảm; chỉ render allowlisted fields và giới hạn kích thước trước khi log.

## 11. Bind đối số, kết quả và lỗi

Ở code-style:

```java
// Snippet AspectJ; phụ thuộc pointcut/workspace phù hợp.
after(String id) returning(String value): operation(id) {
    System.out.println(value);
}
```

Ở annotation-style:

```java
// Snippet bên trong class @Aspect với pointcut operation(String).
@org.aspectj.lang.annotation.AfterReturning(
    pointcut = "operation(id)", returning = "value")
public void onSuccess(String id, String value) {
    System.out.println(value);
}
```

Cần chú ý tên `value` trong phần `returning` và tên tham số advice. Nếu pointcut biểu diễn một phương thức `void` thì không thể coi return value như một `String` không tồn tại. Trong một số môi trường, tên argument trong annotation có thể cần `argNames` hoặc debug/parameter metadata. Test đúng toolchain đã dùng, thay vì trông cậy IDE sẽ đoán chính xác.

Những đường kết thúc:

- **Before:** chạy trước join point.
- **After returning:** chỉ chạy khi kết thúc bình thường, có thể nhận result.
- **After throwing:** chạy khi ném exception, có thể nhận Throwable phù hợp.
- **After:** thực thi sau join point theo ngữ nghĩa cleanup/finally.
- **Around:** bao cả lời gọi, có quyền thực hiện `proceed`, đo thời gian, wrap hoặc thay return nếu hợp đồng cho phép.

Sự khác biệt này là ngữ nghĩa AOP, không thay đổi chỉ vì code được viết bằng `@AfterReturning` hay `after() returning`.

## 12. Around advice trong hai phong cách

Code-style dùng phép `proceed` đặc biệt:

```java
// Snippet .aj; chỉ dùng riêng nếu có TaskService được weave.
public aspect AroundNative {
    pointcut selected(): execution(String TaskService.perform(String));

    String around(): selected() {
        long start = System.nanoTime();
        try {
            return proceed();
        } finally {
            System.out.println("elapsed_ns=" + (System.nanoTime() - start));
        }
    }
}
```

Annotation-style dùng đối tượng `ProceedingJoinPoint`:

```java
// Snippet là method bên trong một class có @Aspect.
@org.aspectj.lang.annotation.Around(
    "execution(String TaskService.perform(String))")
public Object around(org.aspectj.lang.ProceedingJoinPoint joinPoint)
        throws Throwable {
    long start = System.nanoTime();
    try {
        return joinPoint.proceed();
    } finally {
        System.out.println("elapsed_ns=" + (System.nanoTime() - start));
    }
}
```

**Các snippet này là minh họa bổ sung**, không thuộc hai chương trình đã test tại mục 6. Giá trị `elapsed_ns` biến đổi từng lần chạy, nên không có stdout cố định.

Quy tắc:

1. Nếu around advice không gọi `proceed`, phần thân target có thể không chạy; đó có thể là hành vi cố ý (từ chối quyền, cache hit) nhưng phải có hợp đồng rõ.
2. Một số chính sách retry có thể gọi `proceed` nhiều lần khi công cụ hỗ trợ; điều đó đòi hỏi thao tác an toàn khi lặp và input có thể replay.
3. Return type và exception phải phù hợp join point; sai kiểu, nuốt lỗi hoặc wrap không kiểm soát có thể làm sai nghiệp vụ.
4. `finally` quanh một method trả future chỉ bao thời gian **tạo/trả handle**, không tự đo toàn bộ thời gian completion.
5. Việc bind args và truyền args mới vào `proceed(..)` có quy tắc của AspectJ; không đổi dữ liệu nghiệp vụ chỉ để phục vụ telemetry.

## 13. Cấu trúc nâng cao và giới hạn annotation-style

Code-style có thể dùng các cấu trúc **static crosscutting** mà Java không thể viết trực tiếp bằng cú pháp thông thường.

Ví dụ khai báo inter-type method và warning kiến trúc:

```java
// Snippet cú pháp .aj; cần các type bên dưới tồn tại trong build.
public aspect AddCapabilities {
    public String TaskService.auditCategory() {
        return "task";
    }

    declare warning:
        call(* java.lang.System.exit(..)):
        "Do not terminate an application from business code";
}
```

**Inter-type declaration (ITD)** có thể thêm method, field hoặc constructor vào type đích khi được weave. `declare parents` thay đổi quan hệ supertype/interface theo loại khai báo được hỗ trợ. `declare warning` và `declare error` áp các ràng buộc kiến trúc. `declare precedence` xác định thứ tự aspect.

Annotation-style có những công cụ như `@DeclareParents`, `@DeclareMixin`, `@DeclareWarning`, `@DeclareError` và `@DeclarePrecedence`. **Điều này không có nghĩa toàn bộ cú pháp code-style được dịch 1:1 sang annotation**:

- Trong Java class có `@Aspect`, bạn không thể viết trực tiếp cú pháp `public String TaskService.auditCategory()` của code-style.
- Annotation-style không có khai báo `privileged aspect` tương đương đầy đủ.
- Một số cấu trúc như `declare soft`, `declare parents extends` hoặc khai báo bổ sung annotation có hạn chế theo phiên bản/trình biên dịch; cần đối chiếu tài liệu của bản AspectJ đang dùng.
- Mọi khai báo inter-type vẫn cần quyền weave bytecode của target. Không phải cứ viết annotation là có thể thay đổi bất kỳ class nào, nhất là class đã load hoặc class thư viện không nằm trong phạm vi weaver.

Vì vậy, khi cần khả năng **thay đổi cấu trúc type** hoặc diễn đạt quy tắc tĩnh rất đặc biệt, code-style thường trực tiếp hơn. Khi chỉ cần pointcut/advice, annotation-style là một cách biểu đạt rõ ràng và thân thiện với môi trường Java.

## 14. Aspect instance và state

Aspect mặc định theo mô hình singleton, nhưng AspectJ còn hỗ trợ các mô hình instance gắn theo đối tượng hoặc luồng điều khiển, chẳng hạn `perthis`, `pertarget`, `percflow` và `percflowbelow` theo ràng buộc của weaver.

Code-style minh họa:

```java
// Snippet AspectJ cần demo.Service tồn tại.
public aspect PerObjectTrace
    perthis(execution(* demo.Service.perform(..))) {
}
```

Annotation-style có thể mô tả một số per-clause qua thuộc tính `@Aspect`:

```java
// Snippet Java annotation phụ thuộc demo.Service.
@org.aspectj.lang.annotation.Aspect(
    "perthis(execution(* demo.Service.perform(..)))")
public class PerObjectTrace {
}
```

**Không đặt dữ liệu request vào field mutable của singleton aspect** mà không có cơ chế scope/đồng bộ, bởi nhiều thread có thể chạy advice đồng thời. `percflow` liên quan luồng điều khiển; không giả định context tự truyền sang task được đưa vào executor khác. Với asynchronous context, phải có chính sách capture/install/restore riêng.

Một cơ chế per-clause không thể tạo mọi aspect instance nếu target bytecode cần thiết không nằm trong phạm vi weaver được phép xử lý.

## 15. Thứ tự advice và phối hợp nhiều aspect

Khi nhiều aspect cùng match một join point, thứ tự ảnh hưởng kết quả:

```text
Authorization -> Transaction -> Retry -> Business
```

Khác với:

```text
Retry -> Transaction -> Authorization -> Business
```

Cần xác định ai chạy trước, ai đo thời gian cho một logical operation, retry có lặp toàn transaction hay không, và bao nhiêu lần audit được phát ra. AspectJ có cơ chế khai báo precedence thích hợp; code-style có `declare precedence`, annotation-style có `@DeclarePrecedence` trong phạm vi được hỗ trợ.

**Không đoán thứ tự** dựa trên tên file, thứ tự import hay vị trí annotation. Thiết lập thứ tự minh bạch khi thứ tự có ý nghĩa, rồi xác minh bằng integration test. Nếu compile hai ví dụ `NativeTrace` và `AnnotationTrace` vào cùng một output và cả hai được weave, cùng một lời gọi có thể in log thừa; đó không phải bằng chứng một phong cách bị “gọi hai lần” tự nhiên.

## 16. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách khắc phục |
|---|---|---|
| Có `@Aspect` nhưng log không chạy | Chỉ compile Java mà chưa weave | Kiểm tra bước `ajc`/agent và class đã weave |
| `ajc` báo thiếu formal binding | Thiếu tên tham số/debug metadata khi dùng `javac` trước | Biên dịch với `-g -parameters` hoặc cấu hình binding hỗ trợ và test |
| `javac` báo lỗi với `aspect` | Tệp `.aj` dùng cú pháp ngôn ngữ mở rộng | Dùng `ajc` |
| Log bị lặp | Hai aspect cùng match hoặc weave trùng | Tách output và kiểm tra cấu hình aspect |
| Advice không match | Sai `execution`/`call`, type hoặc overload | Kiểm tra join point và class được weave |
| Đổi tên method làm mất advice | Pointcut dùng exact tên/chữ ký cũ | Kiểm tra lại pointcut contract khi refactor |
| Method trên interface không nhận annotation đúng | Khác vị trí annotation ở interface/implementation | Xác minh lookup/weaver semantics |
| Advice không ghi lỗi | Dùng `after returning` cho đường ném exception | Dùng `after throwing` theo hợp đồng |
| Around gây lỗi kiểu trả về | `proceed`/return không phù hợp signature | Bảo toàn return và Throwable |
| LTW không hoạt động | Có runtime jar nhưng thiếu agent/config | Cấu hình weaving đúng deployment |
| Thời gian đo quá ngắn | Method chỉ trả future chưa hoàn tất | Đo đến completion nếu đó là hợp đồng observation |
| Concurrency lỗi | Aspect singleton lưu state của từng request | Tách state theo scope hoặc dùng đồng bộ đúng |

Đặc biệt, **pointcut expression dạng chuỗi** trong annotation-style có thể vẫn chứa tên/signature cũ dù IDE đã refactor method Java. Code-style cũng cần test phạm vi match, nhưng nhiều khai báo chuyên biệt được `ajc` kiểm tra cùng build. Không có phong cách nào tự bảo đảm đúng tập join point sau mọi lần refactor.

## 17. Cách kiểm thử aspect có hiệu lực

Nên dùng ít nhất ba loại kiểm tra:

1. **Compile/check metadata:** biểu thức pointcut hợp lệ, annotation nằm đúng chỗ, output chứa aspect mong muốn, version runtime/weaver tương thích.
2. **Weaving verification:** target code được đưa vào đúng `-inpath`/source compile hoặc được LTW biến đổi ở thời điểm nạp; không chạy nhầm class chưa weave trên classpath.
3. **Behavior test:** gọi thật qua entrypoint của ứng dụng, assert `before`, success/error advice, side effect và result chính xác.

Với demo, kiểm thử dương là cả hai phiên bản đều in:

```text
enter:A-7
exit:done:A-7
done:A-7
```

Kiểm thử âm là Java class chưa weave chỉ in:

```text
done:A-7
```

Các kiểm thử nâng cao nên bao gồm:

- Method ném exception: `after returning` không chạy, `after throwing` xử lý đúng đường lỗi.
- Thay `perform(String)` thành một overload hoặc đổi tên: pointcut có bỏ sót/chặn nhầm không?
- Class caller chưa weave khi pointcut dùng `call`.
- Aspect trên interface vs implementation; override, generic bridge method, method synthetic, varargs.
- Nhiều aspect cùng target: precedence và khả năng ghi nhận trùng.
- Actor/context trên nhiều thread hoặc future trả sớm: thời điểm observation chính xác.
- Build/deploy khác môi trường lập trình: phiên bản Java/AspectJ và agent/classloader tương thích.

**Có class aspect trong jar chưa đủ chứng minh advice đã chạy.** Cần ít nhất một test gọi vào phương thức đích qua đường thực thi thật và kiểm chứng effect.

## 18. Chọn phong cách phù hợp

Chọn **code-style** khi:

- Muốn sử dụng đầy đủ các cú pháp AspectJ đặc thù như inter-type method/field, những `declare` nâng cao.
- Build đã tích hợp `ajc` và nhóm muốn khai báo crosscutting tập trung bằng ngôn ngữ AspectJ.
- Cần trực tiếp mô tả các thay đổi cấu trúc type mà Java annotation không biểu đạt nguyên xi.

Chọn **annotation-style** khi:

- Phần lớn concern là pointcut và advice, cùng các loại `@Declare...` được hỗ trợ.
- Muốn viết Java class/method thông thường, dùng IDE Java, unit test helper và chuyển source qua `javac` trước khi weave nếu cần.
- Toolchain có bước weaving được định nghĩa và kiểm tra rõ.

Dù chọn cách nào:

- Pointcut phải bám theo **ranh giới nghiệp vụ** hoặc contract được review, tránh wildcard quá rộng.
- Không log tham số nhạy cảm, không gọi `proceed` ngoài hợp đồng và không dùng retry trên side effect chưa an toàn.
- Quản lý precedence, concurrency và vòng đời asynchronous observation.
- Pin phiên bản compiler/runtime/weaver và kiểm tra build reproducibility.
- Test **tập join point được chọn và hành vi thực sự xảy ra** thay vì chỉ nhìn syntax.

## 19. Ghi nhớ

**Cú pháp chuyên biệt và cú pháp annotation của AspectJ là hai cách diễn đạt AOP**, không phải hai mô hình độc lập. `aspect`/`pointcut`/`before` trong `.aj` tương ứng với `@Aspect`/`@Pointcut`/`@Before` trong Java ở nhiều trường hợp, và chúng có thể tạo kết quả như nhau khi weave đúng. Cần phân biệt riêng **cú pháp khai báo, thời điểm weaving, khả năng static crosscutting và metadata binding**. Một Java class chứa `@Aspect` mới chỉ là phần mô tả; muốn advice chạy phải chắc rằng target đã được weave.

### Tài liệu kỹ thuật

- [AspectJ — An Annotation Based Development Style](https://eclipse.dev/aspectj/doc/latest/adk15notebook/ataspectj.html): cú pháp annotation, tương ứng với code-style và giới hạn.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): join point, pointcut, advice, aspect instantiation và static crosscutting.
- [AspectJ ajc compiler/weaver](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html): compiler options, binary weaving, `-inpath` và `-aspectpath`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-003">Biểu thức execution, call và within trong pointcut</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu thức `execution`, `call` và `within` trong pointcut AspectJ

## 1. Ba câu hỏi khác nhau về một lời gọi phương thức

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), một **aspect** đóng gói mối quan tâm cắt ngang các thành phần; **advice** là logic được gắn tại những **join point** do **pointcut** lựa chọn. AspectJ là một cách hiện thực AOP trên Java, cung cấp ngôn ngữ pointcut với nhiều loại join point và các biểu thức chọn chúng.

Ba pointcut designator quan trọng:

- **`execution(MethodPattern)`**: chọn **điểm thực thi** của phương thức phù hợp; hỏi “phương thức nào đang bắt đầu chạy?”.
- **`call(MethodPattern)`**: chọn **điểm gọi** phương thức phù hợp; hỏi “ở đâu đang phát sinh lời gọi tới phương thức đó?”.
- **`within(TypePattern)`**: lọc theo **vị trí từ vựng trong mã nguồn/kiểu khai báo** của join point; hỏi “join point nằm bên trong kiểu nào?”.

Điểm khác biệt lớn nhất là **execution tập trung vào callee**, **call tập trung vào call site**, và **within tập trung vào vị trí chứa join point**, không tự định nghĩa đây là lời gọi hay thực thi.

Ví dụ giả định:

~~~java
class Task {
    void run() {
        System.out.println("running");
    }
}

class Client {
    void start(Task task) {
        task.run();
    }
}
~~~

Với một lời gọi `task.run()` từ `Client.start()`:

~~~text
Client.start()  -- call(Task.run()) -->  Task.run() body
        ^                                    ^
        |                                    |
    call join point                     execution join point
    thuộc Client                        thuộc Task

within(Client): true ở call site nằm trong Client
within(Task): true ở execution join point của Task.run
~~~

Một lời gọi thông thường có thể dẫn tới cả `call` và `execution` join point, **nếu cả hai vị trí đều nằm trong phạm vi được weaving**. Điều này không có nghĩa mỗi lời gọi nhất thiết được quan sát đủ cả hai: kiểu join point được hỗ trợ, điểm cắt, phạm vi dệt mã, lời gọi native/reflective và lộ trình dispatch ảnh hưởng tới tập điểm xuất hiện trong chương trình đang chạy.

## 2. Nền tảng cú pháp: signature pattern và type pattern

AspectJ có ngôn ngữ biểu thức riêng. Các ví dụ cú pháp dưới đây sử dụng **code-style AspectJ**, trong đó có thể khai báo `aspect` và pointcut bằng cú pháp `execution(...)`, `call(...)`, `within(...)`.

Mẫu chữ ký phương thức phổ biến:

~~~aspectj
execution(public * demo.services.OrderService.submit(..))
call(* demo.services.OrderService.submit(..))
~~~

Đọc mẫu trên:

| Thành phần | Ý nghĩa |
| --- | --- |
| `public` | Ràng buộc modifier; có thể bỏ để không giới hạn theo modifier đó |
| `*` đầu tiên | Mọi kiểu trả về |
| `demo.services.OrderService` | Kiểu khai báo phương thức được chọn |
| `submit` | Tên phương thức |
| `(..)` | Bất kỳ số lượng và kiểu tham số |
| `(String)` | Đúng một tham số `String` |
| `(String, ..)` | Tham số đầu là `String`, theo sau có thể có thêm tham số |
| `demo.services..*` trong type pattern | Kiểu trong package đó và các package con theo quy tắc mẫu kiểu |
| `+` sau một type pattern | Kiểu đó và các subtype phù hợp |

Một số biểu thức minh họa:

~~~aspectj
// Mọi phương thức công khai của một lớp, mọi tham số.
execution(public * demo.services.OrderService.*(..))

// Mọi phương thức tên save trả void và nhận đúng một String.
execution(void demo.services.OrderService.save(String))

// Các lời gọi tới phương thức có tên log của một lớp.
call(* demo.telemetry.AuditLogger.log(..))

// Các điểm thực thi method có kiểu khai báo thuộc vùng dịch vụ.
execution(* demo.services..*.*(..))

// Các join point nằm từ vựng trong các kiểu thuộc vùng ứng dụng.
within(demo.application..*)
~~~

**Chú ý:** `execution` và `call` yêu cầu **method pattern**; `within` yêu cầu **type pattern**. Không viết `within(* demo.Service.run(..))`, vì đây là mẫu phương thức, không phải mẫu kiểu. Ngược lại, `execution(demo.services..*)` không phải mẫu thực thi phương thức hoàn chỉnh.

Cú pháp `*`, `..` và `+` có ý nghĩa theo vị trí. Ví dụ `..` bên trong danh sách tham số đại diện cho nhiều tham số, còn `..` trong type/package pattern liên quan phân cấp package/type. Không nên tự suy diễn chúng đều có cùng nghĩa trong mọi ngữ cảnh.

## 3. `execution(...)`: điểm thực thi nằm trong phương thức đích

Biểu thức:

~~~aspectj
pointcut selectedExecution():
    execution(void demo.WorkUnit.run(String));
~~~

chọn method-execution join point tương ứng khi `WorkUnit.run(String)` thực thi, với điều kiện thân phương thức thích hợp đã được dệt mã theo cơ chế triển khai.

Dòng thời gian thông thường:

~~~text
caller chuẩn bị tham số
caller gọi WorkUnit.run(...)
    [execution advice before / around entry]
    body WorkUnit.run(...)
    [execution advice after / around exit]
caller tiếp tục
~~~

`execution` phù hợp khi mục tiêu là **bao quanh hành vi của callee**. Nếu phương thức được gọi từ nhiều nơi, một execution advice ở thân phương thức có thể quan sát các lượt thực thi tương ứng mà không cần liệt kê từng caller.

Ứng dụng thường gặp:

- Đo thời lượng thực thi thực tế của một phương thức nghiệp vụ.
- Kiểm tra điều kiện tại ranh giới bước vào thực thi.
- Gắn context cho một lần operation.
- Ghi nhận ngoại lệ mà thân phương thức đẩy ra đồng bộ.
- Đặt chính sách kỹ thuật được mô tả dựa trên vòng đời method execution.

Tuy nhiên, điểm “thực thi phương thức” vẫn là ranh giới **theo lời gọi đồng bộ**: nếu method chỉ trả về một future chưa hoàn tất, `execution` không tự chờ tác vụ bất đồng bộ xong.

### 3.1. Nhiều caller, một execution pointcut

~~~text
CallerA -> WorkUnit.run()
CallerB -> WorkUnit.run()
Worker  -> WorkUnit.run()
                  |
                  v
         execution(WorkUnit.run)
~~~

Một execution pointcut có thể áp dụng cho tất cả lần thực thi trong phạm vi đã weave, trong khi một call pointcut chỉ đặt ở một tập call site sẽ chỉ nhìn thấy những lời gọi thuộc tập đó.

### 3.2. Trả về hay ném ngoại lệ

Advice `before()` tại `execution` chạy ở đầu lượt thực thi. Advice `after() returning` và `after() throwing` có thể phân biệt return bình thường với throw đồng bộ theo ngữ nghĩa advice. `around()` có thể bao quanh proceed và thay đổi hành vi nếu được thiết kế như vậy.

Không được đồng nhất việc `run()` trả về một `CompletionStage` với trạng thái hoàn tất thành công của công việc được đại diện bởi stage đó.

### 3.3. Mẫu lớp và kế thừa

Nếu kiểu thực tế dùng kế thừa hoặc interface, cần xem xét chữ ký phương thức được thực thi, kiểu khai báo và phương thức được override. Chẳng hạn một điểm cắt cho `execution(* demo.Base.work(..))` không nên bị coi là lời hứa bao phủ mọi implementation của `work` ở mọi kiểu chỉ vì các đối tượng có quan hệ subtype.

Khi muốn match subtype, dùng type pattern phù hợp (ví dụ `Base+`) rồi **kiểm chứng trên các lớp thực tế**, đặc biệt với override, bridge method do generic và các phương thức có chữ ký gần giống.

## 4. `call(...)`: điểm gọi nằm ở phía caller

Biểu thức:

~~~aspectj
pointcut selectedCall():
    call(void demo.WorkUnit.run(String));
~~~

chọn join point xuất hiện ở **vị trí mã đang gọi** phương thức có chữ ký phù hợp. Advice của `call` có thể chạy trước khi lời gọi chuyển điều khiển sang callee.

Dòng thời gian:

~~~text
Caller:
    tạo/đánh giá receiver, arguments
    [call advice before]
    gọi target method
        [execution advice before nếu có]
        chạy method body
        [execution advice after nếu có]
    [call advice after]
    caller tiếp tục
~~~

Thời điểm đánh giá receiver và argument cũng là một phần semantics của ngôn ngữ Java. Không được giả định `before() : call(...)` chạy trước mọi biểu thức tạo argument; nó chạy tại call join point sau khi những giá trị cần thiết cho lời gọi đã được chuẩn bị theo trình tự đánh giá Java.

`call` đặc biệt hữu ích khi mục tiêu là **quan sát những người gọi cụ thể** hoặc chính thao tác đi qua một ranh giới phụ thuộc, chẳng hạn “mọi call site trong tầng nhập liệu gọi một API nguy hiểm”.

Ví dụ:

~~~aspectj
before():
    call(* demo.remote.RemoteClient.request(..))
    && within(demo.application..*) {
    System.out.println("outgoing request from application code");
}
~~~

Điểm cắt trên chọn các call join point đến `RemoteClient.request` mà **call site nằm trong kiểu thuộc `demo.application` hoặc các package con tương ứng**. Nó không tự chọn mọi phương thức của RemoteClient và cũng không chọn các call site nằm ở package khác.

### 4.1. Cùng callee, nhiều call site

~~~text
ClientA.run() --- call site A ---+
                                 +--> Worker.perform()
ClientB.run() --- call site B ---+
Internal.tick() - call site C ---+
~~~

`call(Worker.perform)` có thể chọn các vị trí A, B, C đã được weave. Điều kiện `&& within(ClientA)` thu hẹp về vị trí A. Một execution pointcut cho Worker.perform là một **điểm loại khác** ở phần thực thi của Worker.

### 4.2. Phạm vi weave ở caller là then chốt

Nếu chỉ class callee được weave, một `call` advice nằm ở code caller chưa được weave có thể không chạy. Ngược lại, `execution` advice ở callee đã được weave vẫn có thể chạy khi một caller ngoài phạm vi gọi nó.

Điều này quan trọng với thư viện bên thứ ba, classloader riêng, mã nạp động, môi trường có nhiều artifact hoặc khi chỉ weaving một tập lớp đã định. **Không có call join point được instrument tại call site thì call advice không thể xuất hiện ở đó.**

### 4.3. Lời gọi reflection hoặc lối gọi gián tiếp

Nếu mã gọi `java.lang.reflect.Method.invoke(...)`, call join point tại source site có thể là `Method.invoke`, không phải lời gọi viết trực tiếp như `service.work()`. Còn execution join point của `work()` đã được weave có thể vẫn quan sát lượt thực thi của method khi reflection kích hoạt nó.

Đây là khác biệt loại join point, **không phải tuyên bố rằng reflection luôn né AOP**. Cần kiểm tra runtime, kiểu phương thức và cơ chế weaving được dùng.

## 5. `within(...)`: lọc theo vị trí từ vựng, không theo runtime receiver

Biểu thức:

~~~aspectj
within(demo.WorkUnit)
~~~

chọn các join point **nằm trong phần mã được khai báo từ vựng bên trong kiểu `demo.WorkUnit`**, theo type pattern. Nó không có nghĩa “mọi lời gọi bất kỳ đến đối tượng WorkUnit”.

Ví dụ:

~~~java
class WorkUnit {
    void run() {
        // phần thân run nằm trong WorkUnit
    }

    void localCall() {
        run(); // vị trí gọi này cũng nằm trong WorkUnit
    }
}

class Client {
    void invoke(WorkUnit unit) {
        unit.run(); // vị trí gọi này nằm trong Client
    }
}
~~~

Bảng đối chiếu:

| Join point | `within(WorkUnit)` | `within(Client)` | Lý do |
| --- | --- | --- | --- |
| `execution(WorkUnit.run)` | Có | Không | Execution được khai báo trong WorkUnit |
| `call(WorkUnit.run)` từ WorkUnit.localCall | Có | Không | Call site nằm trong WorkUnit |
| `call(WorkUnit.run)` từ Client.invoke | Không | Có | Call site nằm trong Client |
| `execution(Client.invoke)` | Không | Có | Execution được khai báo trong Client |

**Ghi nhớ:** `within` mô tả **kiểu chứa join point**, không phải kiểu runtime của receiver hay đối tượng đang được gọi.

Dấu hiệu rất dễ nhầm:

~~~aspectj
call(void demo.WorkUnit.run(..))
&& within(demo.Client)
~~~

đọc đúng là “các lời gọi tới WorkUnit.run **viết trong mã của Client**”. Nó **không** có nghĩa “method WorkUnit.run được khai báo trong Client”, và cũng không chọn tất cả execution của WorkUnit.run.

Ngược lại:

~~~aspectj
execution(void demo.WorkUnit.run(..))
&& within(demo.WorkUnit)
~~~

thường là điều kiện phù hợp với vị trí khai báo method WorkUnit.run, nhưng `within` ở đây không làm thay đổi nó thành call pointcut. Khi chỉ cần method execution của kiểu cụ thể, chỉ dùng `execution` thường đã rõ hơn.

### 5.1. `within` độc lập không chỉ chọn phương thức

Một biểu thức `within(SomeType)` tự nó giới hạn **vị trí chứa** những loại join point mà AspectJ hỗ trợ. Nó có thể match nhiều loại join point xuất hiện trong kiểu đó, chứ không chỉ `call` hoặc `execution` method. Do vậy, để tránh lời khuyên “áp dụng cho mọi thứ nằm trong lớp”, thường phối hợp:

~~~aspectj
execution(* demo.WorkUnit.*(..))
&& within(demo.WorkUnit)
~~~

hoặc:

~~~aspectj
call(* demo.RemoteApi.*(..))
&& within(demo.application..*)
~~~

Điều kiện đầu xác định **loại và chữ ký điểm cắt**, điều kiện sau giới hạn vị trí mã chứa điểm ấy.

### 5.2. Không nhầm `within` với `withincode`, `this`, `target`

Các từ khóa khác trả lời câu hỏi khác:

| Pointcut designator | Chọn theo |
| --- | --- |
| `within(TypePattern)` | Kiểu từ vựng chứa join point |
| `withincode(MethodPattern)` | Phần mã thuộc thân method/constructor phù hợp |
| `this(TypeOrId)` | Đối tượng `this` ở join point theo ngữ nghĩa từng loại join point |
| `target(TypeOrId)` | Đối tượng đích của join point theo ngữ nghĩa từng loại join point |
| `args(TypeOrId, ..)` | Đối số tại join point |
| `execution(MethodPattern)` | Method execution |
| `call(MethodPattern)` | Method call site |

Ví dụ `within(Client)` không đòi receiver của cuộc gọi phải là Client; caller code có thể gọi một đối tượng WorkUnit hoặc RemoteApi. Nếu muốn ràng buộc receiver theo kiểu runtime, cần xem xét `target(...)` và loại join point, không thay bằng `within`.

## 6. Ví dụ đầy đủ: ba designator tạo kết quả khác nhau

Ví dụ dưới đây có thể biên dịch với **AspectJ compiler (`ajc`)** và chạy trên Java 17 nếu môi trường đã cài một bản AspectJ hỗ trợ JDK đang dùng. Nó dùng **code-style AspectJ**; cú pháp `aspect` nằm trong file `.aj` và không thể biên dịch bằng riêng `javac`.

Tạo cấu trúc:

~~~text
demo/
  Demo.java
  WorkUnit.java
  TraceAspect.aj
~~~

### 6.1. Tệp `demo/WorkUnit.java`

~~~java
package demo;

public class WorkUnit {
    public void run(String message) {
        System.out.println("BODY " + message);
    }

    public void self() {
        run("internal");
    }
}
~~~

### 6.2. Tệp `demo/Demo.java`

~~~java
package demo;

public class Demo {
    public static void main(String[] args) {
        WorkUnit unit = new WorkUnit();
        unit.run("external");
        unit.self();
    }
}
~~~

### 6.3. Tệp `demo/TraceAspect.aj`

~~~aspectj
package demo;

public aspect TraceAspect {
    // Nằm tại call site trong Demo.
    before():
        call(void demo.WorkUnit.run(String))
        && within(demo.Demo) {
        System.out.println("CALL from Demo");
    }

    // Nằm tại call site trong WorkUnit.self().
    before():
        call(void demo.WorkUnit.run(String))
        && within(demo.WorkUnit) {
        System.out.println("CALL from WorkUnit");
    }

    // Nằm tại method execution, dù caller là ai.
    before():
        execution(void demo.WorkUnit.run(String)) {
        System.out.println("EXEC WorkUnit.run");
    }
}
~~~

Ở ví dụ này, mỗi điểm gọi `run` match **đúng một** advice `call` nhờ hai điều kiện `within` loại trừ nhau. Advice `execution` là một loại join point khác. Vì vậy thứ tự in ra không dựa vào việc phỏng đoán precedence giữa nhiều advice tại cùng một join point.

### 6.4. Biên dịch và chạy

Điều kiện môi trường:

- Có JDK 17+ và `java` sẵn trong PATH.
- Có công cụ `ajc` (AspectJ compiler) từ bản AspectJ tương thích với JDK.
- Có `aspectjrt.jar` trên runtime classpath nếu cách cài AspectJ yêu cầu (thường nằm trong thư mục `lib` của bản phân phối AspectJ).

Từ thư mục **cha của `demo`**, dùng lệnh sau trên môi trường Unix-like. Biến `ASPECTJ_HOME` là đường dẫn tới bản cài AspectJ thực tế trên máy, phải cấu hình trước:

~~~bash
# ASPECTJ_HOME đã trỏ tới thư mục cài AspectJ.
mkdir -p out
ajc -17 -d out demo/WorkUnit.java demo/Demo.java demo/TraceAspect.aj
java -cp "out:$ASPECTJ_HOME/lib/aspectjrt.jar" demo.Demo
~~~

Trên Windows, dấu phân cách classpath là `;` thay cho `:`; tùy cách cài, vị trí `aspectjrt.jar` có thể khác. Khi công cụ compiler không hỗ trợ cờ `-17`, cần dùng một bản AspectJ có hỗ trợ language level phù hợp; ví dụ Java trên cũng không dùng tính năng vượt quá Java 17.

Stdout:

~~~text
CALL from Demo
EXEC WorkUnit.run
BODY external
CALL from WorkUnit
EXEC WorkUnit.run
BODY internal
~~~

**Vì sao có đúng các dòng đó?**

1. `Demo.main` gọi `unit.run("external")`. Call site nằm trong kiểu Demo: `call(...) && within(Demo)` match và in `CALL from Demo`.
2. Control vào phương thức `WorkUnit.run`: `execution(...)` match, in `EXEC WorkUnit.run`.
3. Thân method chạy và in `BODY external`.
4. Sau đó `Demo.main` gọi `unit.self()`; biểu thức `call(run)` không match lời gọi `self()` vì tên method khác.
5. Bên trong `WorkUnit.self`, biểu thức `run("internal")` tạo call site nằm từ vựng trong WorkUnit: advice thứ hai in `CALL from WorkUnit`.
6. Thân `WorkUnit.run` thực thi lần nữa và in `EXEC WorkUnit.run`, rồi `BODY internal`.

Nếu bỏ weaving khỏi **`Demo`**, advice `call && within(Demo)` không còn cơ hội chặn call site của Demo. Nếu bỏ weaving khỏi **`WorkUnit`**, cả call site trong `self()` và execution của `run()` có thể không được instrument. Đó là lý do ví dụ biên dịch/dệt **cả ba file cùng nhau**.

### 6.5. Tại sao không dùng `javac` cho `.aj`?

`javac` hiểu Java thông thường nhưng không xử lý khai báo `aspect` và logic weaving của code-style AspectJ. `ajc` biên dịch AspectJ và Java cần thiết, thực hiện weaving theo cấu hình. Trong một dự án dùng load-time weaving, quy trình chuẩn bị và runtime khác; khi đó phải xác định đúng các lớp được load/weave và không tự sử dụng lệnh biên dịch trên như công thức cho mọi môi trường.

## 7. Ma trận chọn điểm cắt: quan sát người gọi hay người được gọi?

| Nhu cầu | Biểu thức minh họa | Lý do |
| --- | --- | --- |
| Theo dõi mỗi lượt thực thi của `WorkUnit.run` | `execution(void demo.WorkUnit.run(String))` | Bám vào thân method đích |
| Theo dõi những call site gọi `WorkUnit.run` | `call(void demo.WorkUnit.run(String))` | Bám vào điểm phát sinh lời gọi |
| Chỉ theo dõi caller nằm trong `demo.ui` | `call(* demo.WorkUnit.run(..)) && within(demo.ui..*)` | Giới hạn nơi viết lời gọi |
| Chỉ instrument code trong `demo.services` | `within(demo.services..*)` cộng điều kiện join point cụ thể | Giới hạn vị trí chứa |
| Chọn execution của toàn bộ service được phê duyệt | `execution(* demo.services..*Service.*(..))` | Giới hạn method execution theo type/name |
| Chọn các call site trong một method cụ thể | `call(* demo.RemoteApi.*(..)) && withincode(* demo.Client.submit(..))` | Hẹp hơn giới hạn theo cả kiểu |
| Chọn các execution khai báo ở một kiểu nhất định | `execution(* demo.WorkUnit.*(..)) && within(demo.WorkUnit)` | Kết hợp chữ ký và vị trí chứa |
| Truy vết lời gọi qua lớp thư viện nhưng chỉ có quyền weave caller | `call(* demo.RemoteApi.*(..))` tại call site được weave | Call-site advice không đòi instrument thân thư viện, nếu call site thuộc phạm vi |
| Bảo vệ thao tác nhạy cảm từ nhiều entry point | Xem xét execution tại ranh giới tin cậy, kèm kiểm tra độ bao phủ | Chặn theo callee thường phù hợp hơn dựa duy nhất vào một tập caller |

Đây là mẫu phân tích, không phải cam kết rằng mọi expression sẽ match trong bất kỳ bản biên dịch nào. Phải kiểm tra các type/method pattern thực tế, phạm vi weaving và control flow.

## 8. Chọn giữa `call` và `execution` khi làm quan sát

### 8.1. Kết quả đo có thể khác nhau

Giả sử method đích `calculate` nhanh nhưng caller dành thời gian xử lý tham số phức tạp trước khi gọi. Advice tại call join point và advice tại execution join point không tự động bao gồm toàn bộ thời gian chuẩn bị tham số ấy.

- **`execution`** quan sát lượt thực thi thân phương thức đích.
- **`call`** quan sát phần hoạt động của lời gọi tại một call site đã được weave, gồm việc tiến vào/ra lời gọi theo semantics AOP.
- Để đo **end-to-end operation** gồm xếp hàng, retry hoặc hoàn tất bất đồng bộ, cần một ranh giới khác rộng hơn, được định nghĩa theo hợp đồng.

Một phép đo “call duration” và “execution duration” có thể khác nhau vì các lớp advice khác, dispatch/bridge, wrapper, lỗi, thread scheduling hoặc phạm vi quan sát. Không nên gán chúng cùng một tên metric.

### 8.2. Người gọi có nhiều call site

Nếu một method được gọi từ 20 chỗ khác nhau:

- `execution` tại callee thường giúp instrument tập trung.
- `call` có thể giúp phân biệt những chỗ gọi, đặc biệt khi kết hợp `within` hoặc `withincode`.
- Truy vết mọi call site làm tăng số điểm instrument và overhead; phải giới hạn theo mục tiêu.

### 8.3. Thư viện chỉ có bytecode

Có lúc người phát triển không muốn hoặc không thể weave code thư viện. Nếu một caller thuộc quyền kiểm soát được weave, `call` ở caller có thể quan sát các lần gọi từ caller đó vào API thư viện mà không cần thay đổi thân method thư viện. Nhưng các caller khác không được weave vẫn nằm ngoài.

Ngược lại, nếu thư viện là nơi chứa method execution và không được weave, `execution` advice không thể tự xuất hiện trong body của thư viện. Quyết định cần dựa trên khả năng can thiệp tại build/load-time trong môi trường thật.

## 9. `within` chỉ nói “mã nằm ở đâu”, không nói “ai đang chạy”

Giả sử:

~~~java
class Processor {
    void delegate(ExternalService service) {
        service.send();
    }
}
~~~

Join point `call(ExternalService.send)` nằm từ vựng trong `Processor.delegate`. Do đó:

~~~aspectj
call(* demo.ExternalService.send(..)) && within(demo.Processor)
~~~

có thể match call site đó. Dù receiver là `ExternalService`, `within(ExternalService)` **không** match riêng call site nằm trong Processor.

Ở method execution `ExternalService.send`, tình thế ngược lại: execution nằm trong kiểu khai báo method tương ứng. Dùng `within(Processor)` không biến execution của ExternalService thành join point trong Processor chỉ vì Processor là caller.

Các câu cần tự hỏi:

1. Mình đang chọn **call** hay **execution**?
2. Join point ấy nằm trong **mã của lớp nào**?
3. Kiểu receiver/target có liên quan đến `within` không? (Thông thường không; đó là vai trò của `target` khi phù hợp.)
4. Có call site nào khác được tạo ở kiểu/package khác không?
5. Nếu mã bị refactor sang helper class, biểu thức `within` có thay đổi tập match không?

Điểm cuối cùng rất thực tế: chuyển một dòng gọi từ `Client` sang `Helper` có thể làm một `call && within(Client)` advice không còn chạy, ngay cả khi hành vi nghiệp vụ bên ngoài giữ nguyên.

## 10. Pointcut composition bằng toán tử logic

Ba designator có thể kết hợp bằng:

- `&&`: cả hai điều kiện cùng đúng ở **cùng một join point**.
- `||`: một trong hai điều kiện đúng.
- `!`: loại trừ điều kiện.
- Dấu `(...)` để nhóm và kiểm soát thứ tự biểu thức.

Ví dụ:

~~~aspectj
pointcut observedCalls():
    call(* demo.RemoteClient.request(..))
    && within(demo.application..*)
    && !within(demo.telemetry..*);
~~~

Một biểu thức khác:

~~~aspectj
pointcut observedMethods():
    execution(public * demo.services..*.*(..))
    && !within(demo.services.internal..*);
~~~

Ở dạng tổng quát, `execution(...) && call(...)` thường **không chọn được một join point method bình thường**, vì method execution và method call là hai **loại join point khác nhau**. Không dùng `&&` để mang nghĩa “đầu tiên call rồi execution” trên cùng một điểm.

Nếu muốn theo dõi cả hai loại, dùng `||` khi thích hợp:

~~~aspectj
pointcut bothKinds():
    call(void demo.WorkUnit.run(String))
    || execution(void demo.WorkUnit.run(String));
~~~

Nhưng advice cho `bothKinds()` có thể kích hoạt **hai lần cho một lượt gọi được weave đầy đủ**: một tại call site, một tại execution. Nếu chỉ muốn một event “operation started”, đây có thể gây đếm trùng. Cần xác định loại điểm và đơn vị metric.

### 10.1. Độ ưu tiên logic và tính dễ đọc

Với biểu thức phức tạp, nên dùng dấu ngoặc:

~~~aspectj
(
    call(* demo.Api.read(..))
    || call(* demo.Api.write(..))
)
&& within(demo.clients..*)
~~~

Dấu ngoặc giúp cho điều kiện `within` rõ ràng áp dụng cho **cả hai nhóm call**. Nếu bỏ ngoặc và hiểu sai precedence của toán tử, pointcut có thể chọn nhiều call site hơn dự kiến.

### 10.2. Named pointcut

Một biểu thức được sử dụng nhiều nơi nên có tên thể hiện ý nghĩa:

~~~aspectj
pointcut businessExecution():
    execution(public * demo.business..*Service.*(..));

pointcut inboundApiCalls():
    call(* demo.api..*.*(..))
    && within(demo.application..*);
~~~

Tên pointcut giúp review, nhưng **không thay thế thử nghiệm** xem join point nào thực sự match. Mỗi tên nên đi kèm phạm vi loại trừ và ý định đo/kiểm soát.

## 11. Mức độ rộng của pattern và rủi ro instrument quá nhiều

Các expression rất rộng như:

~~~aspectj
execution(* *(..))
call(* *(..))
within(demo..*)
~~~

có thể match số lượng lớn join point. Nếu advice gọi logger, formatter hoặc metric exporter mà chính các thành phần ấy cũng match, hệ thống có thể tạo vòng đệ quy, log storm hoặc tăng mạnh overhead.

Một điểm cắt tốt thường có:

1. **Mẫu dương** xác định nhóm hành vi cần instrument.
2. **Điều kiện vị trí** dùng `within` khi cần ràng buộc caller code hoặc vùng module.
3. **Loại trừ hạ tầng** như logger, exporter, policy evaluator và code của chính aspect.
4. **Chính sách kết quả** cho lỗi, timeout, callback và việc thay đổi control flow.
5. **Test xác nhận tập join point được chọn**, cả dương tính lẫn âm tính.

Ví dụ giả mã dạng chính sách (không phải cú pháp đầy đủ):

~~~text
allowed =
    expected method execution
    AND within approved modules
    AND NOT instrumentation internals
    AND NOT sensitive utilities excluded by policy
~~~

Một khi `call` hoặc `execution` được dùng cho authorization, transaction hoặc retry, việc chọn sai tập điểm có thể gây thay đổi chức năng nghiêm trọng hơn một phép đo sai; cần review kiến trúc và lỗi/tác dụng phụ.

## 12. Các tình huống nâng cao cần phân biệt

### 12.1. Lời gọi nội bộ trên `this`

Với code:

~~~java
class A {
    void one() { two(); }
    void two() {}
}
~~~

Lời gọi `two()` nằm trong A.one. Trong **code-style AspectJ được weave phù hợp**, `call(* A.two(..)) && within(A)` có thể match call site đó; `execution(* A.two(..))` có thể match thân A.two. Không lấy quy tắc bỏ qua self-invocation của một số cơ chế proxy để suy ra rằng AspectJ weaving luôn bỏ qua call site nội bộ.

### 12.2. Dynamic dispatch và method pattern

Với `Base b = new Child(); b.work();`, caller biết một chữ ký theo kiểu biên dịch và JVM dispatch tới implementation thực tế. `call` và `execution` có thể khác nhau về chữ ký join point được match trong tình huống kế thừa, override và bridge methods.

Không nên suy ra từ runtime class của receiver rằng mọi `call(Sub.work)` đều match call site viết qua biến kiểu Base. Nếu muốn theo dõi đa hình, cần dùng type pattern đúng, xem xét `+`/binding của `target` khi phù hợp, và kiểm thử cả kiểu khai báo lẫn kiểu runtime.

### 12.3. Constructor không phải method call thông thường

`execution(...)` và `call(...)` trong bài áp dụng cho **method**. AspectJ còn có join point/pointcut riêng liên quan constructor, chẳng hạn `initialization` hoặc `preinitialization`, và mẫu constructor call `call(Type.new(..))` trong những trường hợp được hỗ trợ.

Không dùng mẫu method `execution(void Type.new(..))` để thay cho constructor execution. Nếu cần instrument vòng đời khởi tạo, hãy học và dùng cú pháp constructor cùng các giai đoạn initialization thích hợp; thứ tự và điểm thực thi khác method thường.

### 12.4. Accessor, bridge và phương thức sinh bởi compiler

Compiler có thể tạo bridge method cho generic hoặc những đoạn mã hỗ trợ khác. Một pointcut quá rộng có thể thấy các điểm không phải là operation nghiệp vụ như mong đợi, tùy phiên bản compiler và cách weaving. Nếu metrics cần một đơn vị “một business invocation”, hãy kiểm tra số event thực tế thay vì tự coi một method name tương đương một event.

### 12.5. Kiểm soát quyền nên đặt ở ranh giới thực hiện effect

Giả sử `call(SensitiveApi.delete)` chỉ được instrument ở UI caller, nhưng job, event consumer hoặc reflection cũng có thể kích hoạt hành vi. Tập call site UI không đủ chứng minh mọi đường đều được bảo vệ. `execution` ở phương thức dịch vụ đã weave có thể phù hợp hơn, song vẫn phải xác nhận toàn bộ đường đến effect và tránh phụ thuộc vào advice có thể bị bỏ qua.

Nếu tài nguyên nhạy cảm, giữ kiểm tra quyền ở ranh giới đáng tin cậy là lựa chọn kiến trúc quan trọng, ngay cả khi AOP hỗ trợ áp cùng chính sách ở nhiều điểm.

### 12.6. Các mô hình weaving và phạm vi runtime

AspectJ có thể được tích hợp vào quá trình build để dệt class, hoặc áp dụng load-time weaving theo cấu hình. Dù chọn cách nào, phải xác định **cả call site lẫn callee nào thực sự đã được weave**, tránh kết luận sai từ việc aspect source được biên dịch thành công.

Dấu hiệu phổ biến của lỗi phạm vi: `execution` có event nhưng `call` không có, hoặc chỉ một nhóm caller hiện ra. Hãy kiểm tra phạm vi dệt, classloader, loại code và call site được chọn; không sửa bằng cách tùy tiện nới pointcut trên toàn chương trình khi chưa hiểu nguyên nhân.

## 13. Debug pointcut và xác nhận join point thực tế

Khi không thấy advice chạy, kiểm tra có hệ thống:

**Bước 1 — Xác nhận kiểu join point mong muốn.** Lỗi phổ biến là muốn biết caller ở đâu nhưng viết `execution`, hoặc muốn bảo vệ method đích nhưng chỉ chọn `call` trong một package hẹp.

**Bước 2 — Xác nhận pattern.** Kiểm tra return type, modifier, tên phương thức, danh sách tham số, package và subtype pattern. Một overload khác có thể không match.

**Bước 3 — Xác nhận `within`.** Join point nằm trong kiểu chứa mã nào? Với call, đó là vùng source của caller; với execution, là vùng khai báo/thực thi method được instrument.

**Bước 4 — Xác nhận weaving.** Với call, caller class đã được weave chưa? Với execution, callee đã được weave chưa? Có classloader, build artifact hoặc runtime config khác biệt không?

**Bước 5 — Xác nhận order.** Một advice khác có thể trả về trước khi proceed(), làm advice phía trong không chạy.

**Bước 6 — Xác nhận self-call, reflection và dispatch.** Đừng dùng suy đoán “mọi internal call đều bỏ qua aspect” hoặc “reflection luôn bypass”; hãy xem kiểu can thiệp và receiver thực tế.

**Bước 7 — Thu hẹp phép thử.** Dùng ví dụ hai class và một method tương tự demo; kiểm tra một join point trước, sau đó tăng dần pattern.

**Bước 8 — Kiểm chứng trường hợp bị loại trừ.** Đảm bảo advice **không** match telemetry internals và các method không thuộc chính sách; tránh chỉ test happy path.

Một cách quan sát an toàn trong môi trường thử nghiệm là dùng `thisJoinPointStaticPart` để biết thông tin tĩnh về join point được chọn, nhưng không nên log không giới hạn mọi đối số hoặc toàn bộ dữ liệu nhạy cảm.

## 14. Những sai lầm thường gặp

**Sai lầm 1 — Coi `call` và `execution` là đồng nghĩa.** Call gắn với vị trí gọi; execution gắn với thân phương thức. Một bên có thể được weave, bên kia không.

**Sai lầm 2 — Coi `within(Type)` là “target có kiểu Type”.** Within là vị trí từ vựng chứa join point. Với receiver type dùng công cụ pointcut khác phù hợp như `target`.

**Sai lầm 3 — Viết `call(...) && execution(...)` để “bao cả hai”.** Một join point thường không đồng thời là hai loại này. Khi cần lựa chọn union, dùng `||` và đánh giá rủi ro đếm trùng.

**Sai lầm 4 — Chọn method signature sai.** Phương thức trả kiểu khác, có overload khác hoặc nằm ở subtype không được type pattern bao phủ. Kiểm tra chữ ký được compiler nhìn thấy.

**Sai lầm 5 — Đặt `within(Callee)` trên `call` và kỳ vọng bắt mọi caller.** Khi đó chỉ lấy call site **nằm trong Callee**, không phải mọi lời gọi hướng tới Callee. Nếu muốn mọi call site có thể weave, bỏ hoặc thay điều kiện `within`.

**Sai lầm 6 — Chỉ weave một phía nhưng kỳ vọng đủ hai event.** Nếu caller chưa được weave, call advice thiếu; nếu callee chưa được weave, execution advice thiếu. Cần quản lý phạm vi dệt mã.

**Sai lầm 7 — Dùng pointcut quá rộng cho logger/metric.** Advice có thể tự chặn hạ tầng do nó gọi, gây đệ quy hoặc chi phí cực lớn. Loại trừ và kiểm thử.

**Sai lầm 8 — Đồng nhất method return với hoàn tất tác vụ async.** Return future không phải success của future; thiết kế observer completion riêng nếu cần.

**Sai lầm 9 — Dựa trên một call pointcut để cưỡng chế mọi đường truy cập.** Caller khác, callback, job hoặc reflection có thể nằm ngoài phạm vi. Bảo vệ effect tại ranh giới tin cậy.

**Sai lầm 10 — Sao chép cú pháp từ một biến thể AOP sang mô hình khác mà không kiểm tra.** Phạm vi join point và khả năng weave phụ thuộc công cụ. Ví dụ `.aj` code-style AspectJ cần `ajc`, không thể giả định mọi compiler Java hay mọi hình thức proxy đều hiểu cú pháp đó.

## 15. Bài tập có đáp án

### Câu 1. Cho hai lớp:

~~~java
class A {
    void x(B b) { b.y(); }
}
class B {
    void y() {}
}
~~~

Ở lời gọi `b.y()`, `within(A)` kết hợp với `call(B.y)` có thể match không?

**Đáp án:** Có, nếu call site nằm trong A đã được weave và chữ ký method pattern đúng. Còn `execution(B.y) && within(A)` không match execution của B.y chỉ vì A là caller.

### Câu 2. Một phương thức `B.y()` được gọi từ A và C. Muốn chọn mọi lượt thực thi tại thân `B.y()`, điểm cắt nào tự nhiên nhất?

**Đáp án:** `execution(* B.y(..))` với type pattern và phạm vi weave phù hợp. Một `call` pointcut ở riêng A chỉ thấy call site của A.

### Câu 3. Muốn đo các lời gọi tới `RemoteClient.fetch` chỉ phát sinh từ kiểu thuộc package `app.ui`, chọn biểu thức nào?

**Đáp án:** Ví dụ `call(* RemoteClient.fetch(..)) && within(app.ui..*)` với tên kiểu đầy đủ theo dự án. `within` lọc mã caller, không lọc nơi định nghĩa `fetch`.

### Câu 4. Một class được weave ở method execution, nhưng caller đến từ thư viện không được weave. `execution` và `call` có thể cho kết quả gì?

**Đáp án:** Execution advice của callee có thể chạy; call advice tại call site thư viện chưa weave không chạy. Cần biết phạm vi weaving và các join point thực tế.

### Câu 5. Một `call` advice chọn vị trí trong `WorkUnit.self()` nơi tự gọi `run()`. Có thể kết luận đây là bypass vì “self-call” không?

**Đáp án:** Không. Trong code-style AspectJ được weave ở call site ấy, `call` có thể match. Giới hạn self-invocation của một số cơ chế proxy không được áp đặt cho mọi dạng weaving.

### Câu 6. Biểu thức `call(..) || execution(..)` cùng chọn `run`. Người phát triển ghi một metric ở cả hai. Rủi ro gì?

**Đáp án:** Một lượt gọi bình thường có thể phát hai sự kiện: một cho call site và một cho execution. Metric operations_total có thể đếm trùng nếu không phân biệt loại join point.

### Câu 7. Có nên viết `within(demo..*)` và coi đó chỉ là “mọi phương thức trong demo”?

**Đáp án:** Không. `within` chọn join point theo vùng mã nằm trong kiểu phù hợp, có thể gồm nhiều loại join point. Nếu chỉ muốn method execution, thêm `execution(...)`.

### Câu 8. Di chuyển câu lệnh gọi API từ `Client` sang `Helper`, còn logic nghiệp vụ tương đương. Pointcut `call(Api.send(..)) && within(Client)` có còn match không?

**Đáp án:** Không nhất thiết; vị trí từ vựng của call site đã đổi sang Helper. Cần cập nhật điểm cắt hoặc thiết kế theo một ranh giới ít phụ thuộc vị trí source hơn.

## 16. Checklist review trước khi dùng trong chương trình thật

| Câu hỏi | Bằng chứng cần có |
| --- | --- |
| Muốn quan sát method được gọi hay nơi gọi? | Chọn `execution` hoặc `call` có lý do |
| Cần lọc caller trong một module? | Dùng `call && within(CallerPattern)` nếu tương thích phạm vi weave |
| Cần lọc code trong một kiểu? | `within(TypePattern)` dùng đúng nghĩa từ vựng |
| Signature có đúng? | Kiểm tra return, modifier, overload, parameters, subtype |
| Caller/callee có được weave? | Biên dịch, load-time weave và test runtime xác nhận |
| Có call nội bộ/reflective không? | Kiểm tra đúng receiver và loại join point |
| Có advice chồng nhau không? | Đánh giá precedence, skip/proceed và đếm trùng |
| Có nguy cơ recursion từ telemetry? | Loại trừ hạ tầng aspect và thử failure path |
| Có dùng để kiểm soát truy cập không? | Không có đường bypass tới sensitive effect |
| Có future, callback hoặc Publisher không? | Phân biệt invocation return với logical completion |
| Có retry hoặc giao dịch không? | Effect, transaction scope, idempotency được kiểm tra |
| Có test âm tính không? | Method/call site ngoài vùng phải thực sự không match |

## 17. Tổng kết

`execution`, `call` và `within` trả lời ba câu hỏi khác nhau:

~~~text
execution(...)  -> callee nào đang thực thi?
call(...)       -> lời gọi tới callee ấy nằm ở đâu?
within(...)     -> join point nằm từ vựng trong kiểu nào?
~~~

Dùng `execution` khi mục tiêu là ranh giới thực thi của method; dùng `call` khi mục tiêu là call site hoặc vùng phụ thuộc giữa các thành phần; dùng `within` để thu hẹp theo vị trí mã chứa một join point đã được xác định rõ loại.

Khi kết hợp, viết pointcut thể hiện đúng mục tiêu, kiểm tra chữ ký và phạm vi weaving, đo đúng đơn vị call/execution, đồng thời loại trừ hạ tầng dễ gây recursion. Với chính sách có thể đổi luồng điều khiển hoặc kiểm soát quyền, phải xác nhận mọi đường thực thi và tác dụng phụ nhạy cảm được bảo vệ; tên của biểu thức điểm cắt không thay thế chứng cứ runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-004">Biểu thức this, target, args và pointcut theo annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu thức this, target, args và pointcut theo annotation trong AspectJ

## 1. Mục tiêu: chọn đúng join point và lấy đúng ngữ cảnh

Trong Aspect-Oriented Programming (AOP), **pointcut** xác định những join point nào sẽ được aspect can thiệp. Trong AspectJ, ngoài việc chọn loại join point bằng execution() hoặc call(), ta thường muốn ràng buộc **ai đang thực thi**, **đối tượng nào được gọi**, **tham số nào được truyền** và **annotation nào đánh dấu hành vi**.

Bốn câu hỏi liên quan nhưng không đồng nhất:

- **this(...):** tại join point, đối tượng đang thực thi là đối tượng nào?
- **target(...):** đối tượng nhận thao tác hoặc đối tượng đích của join point là đối tượng nào?
- **args(...):** các đối số được truyền vào join point có giá trị/kiểu gì?
- **@annotation(...), @this(...), @target(...), @args(...):** thông tin annotation nằm ở thành phần nào và có thể được dùng để match/bind ra sao?

Chẳng hạn khi một đối tượng OrderScreen gọi service.submit("A-01"), ở **method call join point**, this có thể là OrderScreen (bên gọi) và target là đối tượng service (bên nhận). Ở **method execution join point** của service.submit(), this và target thường cùng là đối tượng service. Việc nhầm hai ranh giới này dẫn đến pointcut không khớp hoặc ghi log sai đối tượng.

Các biểu thức ở đây là **cú pháp ngôn ngữ pointcut của AspectJ**, được minh họa bằng code-style aspect. Nội dung áp dụng cho AOP nói chung và ngữ nghĩa AspectJ, không phụ thuộc cơ chế interception của bất kỳ framework ứng dụng nào.

## 2. Ôn lại join point, pointcut và advice

- **Join point:** một vị trí trong luồng chương trình mà AspectJ nhận diện, chẳng hạn method call, method execution, truy cập field hoặc xử lý ngoại lệ.
- **Pointcut designator (PCD):** thành phần của biểu thức chọn join point, ví dụ call(...), execution(...), this(...), target(...), args(...).
- **Pointcut:** biểu thức ghép các PCD và phép logic && (AND), || (OR), ! (NOT).
- **Advice:** mã chạy trước, sau hoặc quanh join point mà pointcut chọn.
- **Weaving:** ghép advice vào mã tương ứng; AspectJ có các cách như compile-time, post-compile hoặc load-time weaving.

Ví dụ:

~~~aspectj
pointcut pricedCall():
    call(* PriceService.calculate(..))
    && within(shop.ui..*);
~~~

Đây là **mảnh mã minh họa**, giả định đã có kiểu PriceService và package shop.ui; chưa phải chương trình độc lập. call(...) chọn lời gọi khớp chữ ký, còn within(...) giới hạn **vị trí mã chứa lời gọi**. Việc kết hợp kinded PCD (call/execution), scoping PCD (within/withincode) và contextual PCD (this/target/args/annotation) thường làm pointcut dễ hiểu, dễ kiểm thử và giảm phạm vi cần xem xét khi weaving.

### call(...) khác execution(...)

| Đặc điểm | call(MethodPattern) | execution(MethodPattern) |
| --- | --- | --- |
| Điểm được chọn | Vị trí lệnh gọi phương thức | Khi thân phương thức bắt đầu thực thi |
| Mã liên quan | Bên gọi | Bên nhận/triển khai |
| this() | Đối tượng thực thi mã ở vị trí gọi; không có ở ngữ cảnh static | Đối tượng đang thực thi thân phương thức instance |
| target() | Đối tượng đích của lời gọi instance | Đối tượng đang thực thi phương thức instance |
| args() | Các đối số tại lời gọi | Các đối số tại lúc thực thi |
| @annotation() | Annotation trên **subject** của call join point | Annotation trên phương thức thực thi được chọn |

Tại call join point có thể có this != target. Tại execution join point của instance method, this và target là cùng một object. Với những join point thuộc **ngữ cảnh static** hoặc lời gọi static không có receiver instance, các PCD yêu cầu đối tượng this/target có thể không match; không dùng chúng như một điều kiện bao phủ mọi lời gọi static.

Cần phân biệt this(...) của AspectJ với this trong thân advice: trong code-style aspect, this là thể hiện aspect đang thực thi advice, còn this(...) PCD ràng buộc đối tượng của **join point đang được quan sát**.

## 3. this(Type) và this(variable)

**this(Type)** chọn join point mà đối tượng đang thực thi có runtime type phù hợp với Type. **this(variable)** đồng thời chọn theo kiểu của biến formal và **bind đối tượng hiện tại** cho advice sử dụng.

~~~aspectj
pointcut uiCalling():
    call(* *(..))
    && within(shop.ui..*)
    && this(shop.ui.OrderScreen);
~~~

Đoạn này chọn các lời gọi bên trong mã thuộc shop.ui... khi đối tượng hiện hành là OrderScreen; ví dụ chỉ có tác dụng nếu kiểu và package tương ứng tồn tại.

Binding trong một pointcut có tham số:

~~~aspectj
pointcut fromScreen(shop.ui.OrderScreen screen):
    call(* *(..))
    && this(screen);
~~~

Lúc advice nhận biến screen, đó là đối tượng **bên gọi tại call join point**. Nó không tự đồng nghĩa với receiver của phương thức đang được gọi.

Ví dụ dùng execution:

~~~aspectj
pointcut executingOnService(ServiceImpl current):
    execution(* ServiceImpl.*(..))
    && this(current);
~~~

Đây là đoạn cú pháp minh họa, cần đặt trong aspect và có lớp ServiceImpl. Với instance method của ServiceImpl, current chính là đối tượng đang chạy phương thức. Không phải mọi join point đều có this; nếu đặt this(...) trên call từ static main(), kết quả là **không match**, dù lời gọi có receiver là một object.

## 4. target(Type) và target(variable)

**target(Type)** chọn join point theo kiểu runtime của đối tượng đích; **target(variable)** đưa đối tượng đích vào lời advice.

~~~aspectj
pointcut invokingService(ServiceImpl service):
    call(* Service.send(..))
    && target(service);
~~~

Nếu biến bên gọi được khai báo kiểu Service nhưng object thực là ServiceImpl, target(service) vẫn có thể match và bind đối tượng ServiceImpl. Vì target() là một PCD dựa trên runtime type, đừng nhầm nó với pattern chữ ký trong call(...).

Trong ví dụ sau:

~~~text
class Ui {
    void render() {
        Service service = new ServiceImpl();
        service.send("hello");
    }
}
~~~

Tại **call** của service.send trong Ui.render:
- this là một thể hiện Ui;
- target là thể hiện ServiceImpl;
- args là một đối số String có giá trị "hello".

Tại **execution** của ServiceImpl.send:
- this và target cùng là ServiceImpl;
- args vẫn là đối số của lượt thực thi.

**Lưu ý:** câu trong khối text chỉ là ví dụ mô hình; lớp Service và ServiceImpl sẽ được định nghĩa trong chương trình hoàn chỉnh bên dưới.

### Khi nào this và target không bằng nhau?

Chúng thường khác ở lời gọi instance method phát ra từ **một đối tượng khác**. Đối với lời gọi từ một phương thức trên cùng đối tượng, this và target có thể trùng. Ở execution join point của instance method thì chúng trùng theo ngữ nghĩa join point. Sự khác nhau xuất phát từ **loại join point**, không phải từ “đối tượng proxy” trong một framework cụ thể.

## 5. args(TypePattern...) và args(variable...)

**args(...)** kiểm tra kiểu/giá trị đối số tại join point. Có hai cách dùng chính:

1. **Lọc theo kiểu:** args(String), args(int, ..), args(String, *, long), v.v.
2. **Bind giá trị:** args(message), args(amount, ..) khi message/amount được khai báo với kiểu tương ứng trong pointcut hoặc advice.

~~~aspectj
pointcut oneString(String text):
    execution(* Service.send(String))
    && args(text);
~~~

Ở đây text nhận đúng đối số String khi advice chạy. Bản thân args(text) **không** có nghĩa chuỗi phải bằng chữ "text"; đó là tên tham số formal. Muốn kiểm tra giá trị cụ thể, thực hiện trong advice hoặc dùng điều kiện động được thiết kế phù hợp.

Các dấu đặc biệt:

| Cú pháp | Ý nghĩa |
| --- | --- |
| args(String) | Một đối số có kiểu phù hợp String |
| args(int, ..) | Đối số đầu phù hợp int; có thể có thêm đối số phía sau |
| args(.., String) | Đối số cuối phù hợp String; phía trước có thể có nhiều đối số |
| args(*, String) | Chính xác hai đối số; đối số đầu là bất kỳ, đối số sau phù hợp String |
| args(..) | Không ràng buộc danh sách đối số |

**args(...) không giống tham số khai báo trong execution(...).** execution(void Service.handle(Object)) lọc theo chữ ký khai báo, còn args(String) có thể lọc join point mà đối số thực sự là String ở runtime dù tham số khai báo là Object. Với null, AspectJ có quy tắc matching dựa trên kiểu tĩnh vì không có runtime class cho giá trị null; tránh giả định rằng null luôn match hoặc không match mọi type pattern.

### Binding và vị trí tham số

~~~aspectj
pointcut requestAmount(String orderId, int amount):
    execution(* OrderService.reserve(String, int))
    && args(orderId, amount);
~~~

Đây là đoạn mã minh họa cần các kiểu tương ứng và một aspect bao ngoài. Thứ tự binding phải đúng. Nếu phương thức có nhiều đối số, không nên phụ thuộc vào việc đọc tùy ý Object[] rồi ép kiểu dựa trên chỉ số không được kiểm soát.

Khi một pointcut dùng || để ghép hai nhánh có binding, các biến cần được ràng buộc hợp lệ trong mọi nhánh có thể match; biểu thức bind mơ hồ có thể bị compiler từ chối.

## 6. Các pointcut theo annotation: annotation nằm ở đâu?

Trong AspectJ, họ PCD bắt đầu bằng @ dùng metadata đã khai báo trên method, type hoặc runtime class của đối tượng/đối số.

| PCD | Đối tượng được kiểm tra |
| --- | --- |
| @annotation(Trace) | Annotation trên **subject** của join point được chọn, ví dụ phương thức tương ứng với execution(...) |
| @within(Trace) | Mã chứa join point được định nghĩa bên trong kiểu có annotation Trace |
| @withincode(Trace) | Mã chứa join point nằm trong phương thức/constructor được đánh dấu Trace |
| @this(Trace) | Runtime type của object đang được ràng buộc vào this có annotation Trace |
| @target(Trace) | Runtime type của target object có annotation Trace |
| @args(Trace, ..) | Runtime type của đối số đầu có annotation Trace; có thể có thêm đối số |

Tên Trace chỉ là annotation minh họa; mỗi PCD cần một annotation type đúng ngữ cảnh, ví dụ annotation chỉ dùng trên METHOD sẽ không phù hợp để đánh dấu TYPE.

**@annotation** không có nghĩa “bất cứ phương thức nào được gọi bên trong một phương thức có annotation”. Trường hợp đó liên quan tới @withincode. **@within** cũng không phải một cách thay thế cho @target: một cái xét nơi mã được định nghĩa, một cái xét annotation trên runtime target type. Khi kết hợp call hoặc execution, khác biệt vị trí chủ thể của join point rất quan trọng.

### Cần @Retention nào?

Một annotation Java có thể dùng @Retention(SOURCE), @Retention(CLASS), hoặc @Retention(RUNTIME):

- Nếu muốn **bind object annotation** vào biến trong advice, phải dùng RUNTIME.
- Với @this, @target và @args dựa vào annotation của runtime type, AspectJ yêu cầu retention RUNTIME.
- @annotation, @within, @withincode có thể match annotation với ít nhất CLASS retention trong các trường hợp không bind; khi bind giá trị của annotation cũng cần RUNTIME.
- Annotation không tự kế thừa giữa các phương thức override chỉ vì cùng tên. Với annotation trên type, quy tắc kế thừa Java và cách pointcut xét runtime type phải được hiểu riêng.

Để giải thích và chạy code dễ dàng, bài này dùng @Retention(RetentionPolicy.RUNTIME) cho annotation phương thức.

## 7. @annotation(annotationVariable): vừa lọc vừa lấy metadata

Giả sử annotation có thuộc tính value():

~~~java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface TraceOperation {
    String value();
}
~~~

Một aspect có thể lấy chính instance annotation:

~~~aspectj
pointcut annotatedWork(TraceOperation operation):
    execution(@TraceOperation * *.*(..))
    && @annotation(operation);

before(TraceOperation operation): annotatedWork(operation) {
    System.out.println(operation.value());
}
~~~

Hai đoạn trên là mảnh minh họa; cần đặt đúng vào các tệp/khối tương ứng và weave bằng AspectJ mới hoạt động. Trong execution(...), @TraceOperation trong method pattern lọc phương thức có annotation ấy; @annotation(operation) bind metadata để advice lấy value(). Có thể dùng execution(* *.*(..)) && @annotation(operation) khi muốn dùng đúng một bộ lọc annotation mà không lặp lại annotation ở chữ ký.

Điểm hữu ích: annotation thể hiện ý định ở ranh giới phương thức, còn aspect thực thi hành vi xuyên suốt. Tuy nhiên, annotation chỉ là metadata; nó không tự phát sinh advice nếu không có pointcut/weaving thực sự áp dụng.

### Phân biệt kiểu runtime của đối số với annotation trên tham số khai báo

@args(TrustedRequest) nói rằng **runtime class của object đối số** có annotation @TrustedRequest. Nó không có nghĩa **tham số trong khai báo phương thức** được gắn annotation @TrustedRequest. Đây là lỗi đọc pointcut khá phổ biến.

Ví dụ:

~~~java
@TrustedRequest
class RequestData {}

void process(RequestData data) {}
~~~

Nếu @TrustedRequest được định nghĩa với @Target(TYPE) và @Retention(RUNTIME), @args(TrustedRequest) có thể match khi runtime argument là RequestData. Ngược lại, gắn annotation lên tham số như void process(@TrustedRequest RequestData data) là metadata **trên formal parameter**; không phải nội dung mà @args kiểm tra. Hai đoạn minh họa này không độc lập để biên dịch.

## 8. Ví dụ đầy đủ: this, target, args và @annotation trong một lần chạy

Ví dụ sau sử dụng **code-style AspectJ** (tệp .aj), chọn một **method call** từ một đối tượng Demo đến đối tượng ServiceImpl, rồi chọn **method execution** có annotation @Trace. Cả hai advice chạy trước phương thức gốc, từ đó thấy được this/target khác nhau ở call và cùng nhận đối tượng service ở execution.

### Tệp 1: Demo.java

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Trace {
    String value();
}

interface Service {
    void send(String message);
}

class ServiceImpl implements Service {
    @Trace("dispatch")
    public void send(String message) {
        System.out.println("body:" + message);
    }
}

public class Demo {
    public void run() {
        Service service = new ServiceImpl();
        service.send("hello");
    }

    public static void main(String[] args) {
        new Demo().run();
    }
}
~~~

### Tệp 2: TracingAspect.aj

~~~aspectj
public aspect TracingAspect {
    pointcut sendCall(Demo caller, ServiceImpl receiver, String message):
        call(void Service.send(String))
        && within(Demo)
        && this(caller)
        && target(receiver)
        && args(message);

    before(Demo caller, ServiceImpl receiver, String message):
            sendCall(caller, receiver, message) {
        System.out.println(
            "call:this=" + caller.getClass().getSimpleName()
            + ",target=" + receiver.getClass().getSimpleName()
            + ",arg=" + message
        );
    }

    pointcut tracedExecution(
            ServiceImpl receiver, String message, Trace trace):
        execution(void ServiceImpl.send(String))
        && this(receiver)
        && args(message)
        && @annotation(trace);

    before(ServiceImpl receiver, String message, Trace trace):
            tracedExecution(receiver, message, trace) {
        System.out.println(
            "execution:annotation=" + trace.value()
            + ",this=" + receiver.getClass().getSimpleName()
            + ",arg=" + message
        );
    }
}
~~~

### Biên dịch và chạy

Cần cài **JDK 17+** và bộ công cụ AspectJ có lệnh **ajc** hỗ trợ Java 17, đồng thời có file aspectjrt.jar từ cùng bản phân phối. Đặt Demo.java, TracingAspect.aj và aspectjrt.jar trong cùng thư mục làm việc để dùng các lệnh sau:

~~~bash
mkdir -p out
ajc -17 -classpath aspectjrt.jar -d out Demo.java TracingAspect.aj
java -cp out:aspectjrt.jar Demo
~~~

Các lệnh trên dùng cú pháp Linux/macOS. Trên Windows PowerShell, tạo thư mục bằng mkdir out và dùng dấu chấm phẩy trong classpath:

~~~powershell
mkdir out
ajc -17 -classpath aspectjrt.jar -d out Demo.java TracingAspect.aj
java -cp "out;aspectjrt.jar" Demo
~~~

javac đơn thuần **không** biên dịch cú pháp aspect trong file .aj; ajc cần nhìn thấy cả tệp Java chứa join point được chọn lẫn aspect khi compile-time weaving. Trong runtime cũng cần aspectjrt.jar. Không dùng câu lệnh Java thuần để chạy mà thiếu phần đã weave, vì advice sẽ không thực thi.

**Kết quả mong đợi sau weaving thành công** (chỉ hiển thị stdout chương trình, không tính thông báo biên dịch):

~~~text
call:this=Demo,target=ServiceImpl,arg=hello
execution:annotation=dispatch,this=ServiceImpl,arg=hello
body:hello
~~~

Giải thích:

1. Demo.run() là instance method, nên call join point có **this=Demo**. Dù biến service được khai báo theo interface Service, receiver thật là ServiceImpl nên **target(receiver)** match runtime type.
2. args(message) ràng buộc giá trị "hello" ở vị trí đối số duy nhất.
3. Khi ServiceImpl.send() được thực thi, **this(receiver)** của execution join point là ServiceImpl.
4. @annotation(trace) bind annotation @Trace("dispatch") khai báo trên ServiceImpl.send(); advice đọc được trace.value() = "dispatch".
5. Cả hai advice đều là before: advice tại call chạy trước khi bước vào execution, sau đó mới chạy thân send().

**Giới hạn kiểm chứng:** bản code được trình bày theo cú pháp AspectJ chuẩn và kết quả được suy ra theo trình tự join point; môi trường soạn bài không có lệnh ajc để chạy thử weaving tại chỗ. Khi thực hành, dùng một phiên bản AspectJ phù hợp với JDK, kiểm tra lệnh ajc -version, rồi đối chiếu stdout thực. Trong các môi trường weaving hoặc cách viết pointcut khác, số join point và thứ tự advice có thể thay đổi nếu tập chọn khác.

## 9. Vì sao từng điều kiện trong ví dụ là cần thiết?

Pointcut call:

~~~aspectj
call(void Service.send(String))
&& within(Demo)
&& this(caller)
&& target(receiver)
&& args(message)
~~~

- call(...) chọn lời gọi theo **chữ ký tại vị trí gọi**. Trong Demo.run(), biến service có kiểu interface Service, nên pattern Service.send phù hợp.
- within(Demo) giới hạn call site vào mã định nghĩa trong Demo.
- this(caller) ràng buộc object đang thực thi mã chứa lời gọi.
- target(receiver) ràng buộc object thực sự nhận lời gọi.
- args(message) lấy argument thực tế.

Pointcut execution:

~~~aspectj
execution(void ServiceImpl.send(String))
&& this(receiver)
&& args(message)
&& @annotation(trace)
~~~

- execution(...) chọn thân phương thức ServiceImpl.send.
- this(receiver) lấy object đang chạy thân phương thức.
- args(message) lấy đối số của lượt thực thi.
- @annotation(trace) chỉ match nếu subject mang annotation có kiểu của biến formal Trace và bind instance annotation.

Nếu đổi Demo.run() thành static method mà vẫn giữ this(caller), call pointcut không match vì không có object caller. Nếu đổi annotation sang chỉ SOURCE retention thì @annotation(trace) không thể bind runtime instance. Nếu đổi pattern call sang một chữ ký khác, pointcut có thể không khớp dù receiver runtime vẫn là ServiceImpl.

## 10. Các pointcut annotation thường dùng và tình huống phù hợp

### @this và @target

~~~aspectj
call(* *.*(..)) && @this(ObservedType)
call(* *.*(..)) && @target(ObservedType)
~~~

Các biểu thức minh họa này giả định @ObservedType là annotation có @Target(TYPE), @Retention(RUNTIME). Biểu thức đầu xét **runtime class của bên đang thực thi mã gọi**, biểu thức sau xét **runtime class của đối tượng đích**. Tại một call join point có object bên gọi và bên nhận khác nhau, chúng có thể cho kết quả khác.

### @within và @withincode

~~~aspectj
execution(* *(..)) && @within(ObservedType)
call(* *.*(..)) && @withincode(ObservedMethod)
~~~

@within xét annotation trên kiểu **định nghĩa mã gắn với join point**; @withincode xét annotation trên **phương thức hoặc constructor bao lấy vị trí mã**. Ví dụ dùng @withincode để chỉ đo các lời gọi phát ra từ những phương thức đã được chú thích, thay vì những lời gọi đến các phương thức được chú thích.

### @args

~~~aspectj
execution(* Service.consume(..)) && @args(ObservedPayload, ..)
~~~

Nếu @ObservedPayload có @Target(TYPE), @Retention(RUNTIME), biểu thức trên cần đối số đầu là object có **runtime class** mang annotation ấy. Nó không xét annotation trên chính tham số được khai báo trong phương thức consume. Với đối số null, không có runtime type để đọc annotation, nên không được xem null như một object annotated.

### @annotation

~~~aspectj
execution(* ServiceImpl.*(..)) && @annotation(Trace)
~~~

Lọc việc thực thi phương thức trong ServiceImpl có subject mang @Trace. Dạng @annotation(trace) với biến formal Trace trace dùng khi advice cần đọc thuộc tính. Điểm mạnh là mô tả ý định rõ bằng annotation. Điểm yếu là có thể bỏ sót phương thức chưa gắn annotation và có thể nhầm vị trí annotation ở interface so với implementation; cần kiểm thử trên join point thực tế.

## 11. Phối hợp pointcut bằng phép logic và binding đúng

**AND:** các điều kiện cùng phải đúng.

~~~aspectj
execution(* ServiceImpl.send(..))
&& args(String)
&& @annotation(Trace)
~~~

**OR:** được phép chọn hai nhóm join point, nhưng cần lưu ý binding. Trong biểu thức có biến formal, các nhánh hợp lệ phải ràng buộc đầy đủ và không mơ hồ.

**NOT:** loại bỏ phần không mong muốn, ví dụ tránh gắn aspect vào chính lớp logging/aspect hoặc những thao tác nội bộ.

~~~aspectj
execution(* shop..*(..)) && !within(shop.instrumentation..*)
~~~

Đây là biểu thức minh họa cần package thực. Không nên viết pointcut quá rộng như execution(* *(..)) rồi chỉ dựa vào kiểm tra kiểu động để quyết định ở hàng triệu join point; giới hạn theo loại join point, phạm vi package/type và ngữ cảnh khi có thể. Điều này giúp giảm chi phí weaving và làm tác động của aspect dễ theo dõi.

### Pointcut không phải truy vấn dữ liệu nghiệp vụ

args(orderId) bind ID thực tế nhưng không kiểm chứng quyền truy cập đối với đơn hàng đó. @annotation(RequiresRole) có thể giúp chọn phương thức cần áp dụng chính sách, nhưng annotation hay tên vai trò không tự chứng minh người gọi có quyền với **tài nguyên cụ thể**. Advice phải gọi logic phân quyền đáng tin với dữ liệu thích hợp nếu nhiệm vụ là kiểm soát truy cập.

Tương tự, target(ServiceImpl) không xác nhận đây là “đúng tenant”. Type của receiver chỉ là một yếu tố lựa chọn join point, không thay thế các bất biến của domain.

## 12. Các lỗi thường gặp và cách chẩn đoán

| Lỗi | Biểu hiện | Nguyên nhân và cách khắc phục |
| --- | --- | --- |
| Dùng this(ServiceImpl) tại call từ Demo | Advice không chạy | this ở call là **Demo**, cần target(ServiceImpl) cho receiver |
| Dùng target(Demo) ở call đến ServiceImpl | Không match | target là đối tượng đích |
| Dùng this(...) tại static main | Không match | Không có instance this ở static context |
| Dùng call(ServiceImpl.send) nhưng call site khai báo interface Service | Có thể không match | Chữ ký của call site thường là Service.send; kiểm tra chữ ký thực và dùng target(...) nếu cần loại runtime |
| Nhầm args(String) với execution(...(String)) | Chọn nhầm join point | args xét kiểu đối số runtime/giá trị; execution xét chữ ký khai báo |
| Dùng @args để tìm annotation trên parameter | Không match kỳ vọng | @args kiểm tra runtime type của **đối số** |
| Dùng @annotation để tìm annotation trên class | Không match kỳ vọng | Dùng @within, @this hoặc @target theo ý nghĩa |
| Annotation chỉ SOURCE/CLASS nhưng cố bind annotation runtime | Không biên dịch hoặc không bind như mong muốn | Dùng RUNTIME cho annotation cần binding |
| Điểm match quá rộng | Weaving chậm, advice xuất hiện không mong muốn | Thêm kinded/scoping PCD và giới hạn đúng loại join point |
| Advice không xuất hiện dù code hợp lệ | Chạy chương trình không weave hoặc bỏ sót nguồn | Bảo đảm compile/weave đúng tệp và dùng đúng runtime classpath |
| Biến formal khai báo sai kiểu/vị trí | Lỗi compile hoặc lọc sai | Đối chiếu kiểu/đối số với args(...) và advice signature |
| Log dữ liệu từ args(...) không chọn lọc | Rò token, dữ liệu cá nhân | Chỉ đọc/trích xuất tham số đã kiểm duyệt |

Để chẩn đoán, hãy kiểm tra theo thứ tự: join point là call hay execution; chữ ký tại điểm đó; this/target/args thật; annotation nằm trên member hay type nào, retention gì; pointcut có được woven vào đúng code hay không; và advice có thật sự áp dụng trong luồng gọi đang thử không.

## 13. Bài tập kiểm tra hiểu đúng

1. Trong một instance method của Caller có lệnh api.send("hi"), với api được khai báo kiểu Interface nhưng object thật là Impl: ở call join point, this và target lần lượt là gì? Viết pointcut chọn riêng target Impl.
2. Nếu lệnh gọi trên được chuyển vào static method, this(Caller) còn match không? target(Impl) có thể vẫn match không?
3. Một phương thức process(Object x) nhận runtime object String: execution(* process(Object)) && args(String) khác execution(* process(String)) thế nào?
4. Một phương thức annotated @Trace trên implementation nhưng không trên interface: so sánh call và execution join point; pointcut @annotation có thể bị ảnh hưởng thế nào?
5. Lớp RequestPayload có @Sensitive trên type; tham số method lại không có annotation. Biểu thức @args(Sensitive, ..) đang kiểm tra điều gì?
6. Nếu muốn chỉ đo các lời gọi **phát ra từ** các phương thức được chú thích @Audited, nên cân nhắc @withincode(Audited) hay @annotation(Audited)? Giải thích vì sao.

**Đáp án định hướng:** (1) this=Caller, target=Impl; kết hợp call(...) && target(Impl). (2) this(Caller) không match trong static context; target(Impl) vẫn có thể match một instance call. (3) chữ ký vẫn nhận Object, nhưng args(String) có thể match khi đối số runtime là String; execution pattern nhận String không tương đương. (4) annotation trên member được chọn, nên phải xác nhận subject của call và execution thay vì giả định giống nhau. (5) xét annotation trên runtime class của giá trị truyền vào. (6) @withincode chọn join point nằm trong mã phương thức được chú thích, còn @annotation chọn join point có subject được chú thích.

## 14. Tóm lược nguyên tắc thiết kế

- Bắt đầu bằng việc xác định **call** hay **execution**, rồi mới chọn this/target.
- Dùng **this** cho đối tượng hiện hành, **target** cho đối tượng nhận thao tác, **args** cho đối số thực.
- Dùng **@annotation** cho annotation trên subject, **@within/@withincode** cho ngữ cảnh mã, **@this/@target/@args** cho annotation trên runtime type của object tương ứng.
- Khi cần sử dụng giá trị/metadata trong advice, khai báo **biến formal** đúng kiểu và bind rõ ràng; với annotation binding phải bảo đảm RUNTIME retention.
- Đặt kinded/scoping PCD để giới hạn tập join point, tránh pointcut quá rộng.
- Luôn xác nhận join point có được weaving thực sự và khớp tập tình huống cần đo/bảo vệ.
- Không dùng PCD thay thế nghiệp vụ hoặc phân quyền; thông tin runtime thu được chỉ là ngữ cảnh để advice thực hiện đúng trách nhiệm.

**Kết luận:** this, target và args mô tả **ngữ cảnh động của một join point**; các pointcut theo annotation cho phép chọn và bind metadata theo **vị trí annotation thực sự tồn tại**. Hiểu đúng sự khác biệt call/execution và member/type/argument giúp viết aspect chính xác, dễ kiểm thử và tránh can thiệp nhầm hành vi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-005">Pointcut cflow và cflowbelow để minh họa luồng điều khiển</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut `cflow` và `cflowbelow` trong AspectJ: hiểu luồng điều khiển động của AOP

## 1. Vấn đề: cùng một phương thức, nhưng ngữ cảnh gọi khác nhau

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP), một pointcut thông thường có thể chọn join point bằng tên method, kiểu chứa method, annotation hoặc loại hoạt động. Tuy nhiên, nhiều chính sách xuyên suốt không chỉ phụ thuộc **method nào đang thực thi**, mà còn phụ thuộc **đường điều khiển nào đã đưa chương trình đến method đó**.

Ví dụ, một phương thức `store.write(...)` được gọi trong hai trường hợp:

- Nhánh A: `processOrder() → reserve() → store.write()`.
- Nhánh B: `adminRepair() → store.write()`.

Nếu muốn ghi telemetry chuyên biệt **chỉ khi thao tác ghi diễn ra bên trong việc xử lý đơn hàng**, pointcut chỉ chọn `execution(* Store.write(..))` là quá rộng. Cần bổ sung điều kiện: tại thời điểm `write` chạy, stack điều khiển vẫn đang nằm trong phạm vi động được mở bởi `processOrder`.

AspectJ cung cấp hai pointcut designator liên quan:

- **`cflow(P)`**: chọn các join point thuộc *dynamic control flow* của một join point khớp `P`, **bao gồm cả join point khởi đầu**.
- **`cflowbelow(P)`**: chọn các join point ở **bên dưới** join point khớp `P` trong dynamic control flow, **loại trừ chính join point mở đầu đó**.

Dấu `P` đại diện cho một pointcut biểu thức. Đây là **pointcut xác định theo ngữ cảnh thực thi động**, không chỉ là tìm lời gọi nằm về mặt văn bản trong source code.

Ví dụ trực quan:

~~~text
main()
  |
  +-- processOrder()         <-- P = execution(processOrder)
  |     |
  |     +-- reserve()
  |     |     |
  |     |     +-- store.write()
  |     |
  |     +-- validate()
  |
  +-- adminRepair()
        |
        +-- store.write()
~~~

Trong lần chạy có `processOrder()` đang hoạt động:

- `cflow(P)` đúng tại `processOrder()` **và** các join point nằm trong luồng thực thi phát sinh bên dưới nó.
- `cflowbelow(P)` đúng tại `reserve()`, `store.write()`, `validate()` và các join point khác thuộc phần bên dưới, nhưng **không đúng tại chính `processOrder()` mở đầu**.
- `adminRepair()` và `store.write()` do `adminRepair()` gọi sau khi `processOrder()` kết thúc không thuộc luồng đó.

**Chú ý:** sơ đồ trên chỉ thể hiện các phương thức tiêu biểu. Tập join point thật còn phụ thuộc các kiểu join point mà bộ weaving hỗ trợ và điểm nào được pointcut còn lại chọn.

## 2. Những khái niệm cần nắm trước

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Join point** | Vị trí sự kiện thực thi được AspectJ cho phép lựa chọn: method call, method execution, constructor execution, field access... tùy dạng pointcut |
| **Pointcut** | Biểu thức chọn tập join point để áp advice |
| **Advice** | Mã được gắn vào join point đã chọn, ví dụ before, after, around |
| **`execution(...)`** | Khớp thời điểm thực thi thân method với chữ ký phù hợp |
| **`call(...)`** | Khớp điểm gọi method ở phía bên gọi |
| **`within(...)`** | Điều kiện dựa trên lexical type mà mã join point nằm trong |
| **`withincode(...)`** | Điều kiện dựa trên mã nguồn enclosing method/constructor |
| **`cflow(P)`** | Điều kiện về call stack/dynamic control flow được mở từ `P`, tính cả entry join point |
| **`cflowbelow(P)`** | Điều kiện về dynamic control flow bên dưới `P`, loại trừ entry join point |
| **`thisJoinPoint`** | Thông tin runtime của join point trong advice |
| **`thisJoinPointStaticPart`** | Phần thông tin tĩnh của join point, hữu ích khi cần tránh dựng object chi tiết không cần thiết |

**Điểm phân biệt:** `within` và `withincode` phản ánh **vị trí tĩnh/lexical**. `cflow` và `cflowbelow` phản ánh **đường đi đã thực sự được thực thi**.

Ví dụ, `store.write()` có thể được gọi từ nhiều phương thức khác nhau; cùng một execution join point ở `Store.write` đôi khi thuộc `cflow(processOrder)`, đôi khi không. Điều kiện thay đổi theo lời gọi đang diễn ra, không do tên method thay đổi.

## 3. Cú pháp cơ bản và cách kết hợp

Một số biểu thức mẫu:

~~~aspectj
// Chọn mọi join point trong dynamic control flow của
// execution một method processOrder không tham số:
cflow(execution(void OrderService.processOrder()))

// Chỉ chọn những join point bên dưới execution đó:
cflowbelow(execution(void OrderService.processOrder()))

// Chỉ chọn execution các method của Repository diễn ra
// bên trong control flow processOrder:
execution(* Repository.*(..))
    && cflow(execution(void OrderService.processOrder()))

// Chỉ chọn các execution bên dưới processOrder:
execution(* Repository.*(..))
    && cflowbelow(execution(void OrderService.processOrder()))
~~~

Đây là **các mảnh cú pháp AspectJ minh họa**, chưa phải một file `.aj` hoàn chỉnh. Tên class/method mang tính giả định; chữ ký trong pointcut phải đúng với chương trình thật.

Hai câu cuối là ví dụ sử dụng toán tử **`&&`** để lấy giao của hai điều kiện:

1. Về mặt method: join point hiện tại phải là execution thuộc tập `Repository.*(..)`.
2. Về mặt đường điều khiển: join point hiện tại phải nằm trong `cflow` hoặc `cflowbelow` của `OrderService.processOrder()`.

**Không nên nhầm** `cflow(P)` với “tìm tất cả method mà `P` có thể gọi đến” bằng phân tích tĩnh. AspectJ đánh giá membership trong control flow ở runtime. Đường call chưa chạy, nhánh if không được chọn hoặc method đã return đều không tạo membership tại thời điểm đó.

## 4. Phân biệt chính xác `cflow` và `cflowbelow`

Giả sử entry pointcut `P` khớp execution của `outer()`. Khi gọi:

~~~text
main
 └─ outer                   P matches here
     └─ inner
         └─ leaf
~~~

Ta chỉ quan sát execution của ba method `outer`, `inner`, `leaf`:

| Execution join point | `cflow(P)` | `cflowbelow(P)` | Vì sao |
| --- | --- | --- | --- |
| `outer()` mở đầu | Có | **Không** | `cflow` tính cả entry; `cflowbelow` loại trừ entry |
| `inner()` trong `outer` | Có | Có | Nằm bên dưới entry đang hoạt động |
| `leaf()` trong `inner` | Có | Có | Thuộc nhánh con của cùng entry |
| `other()` sau khi `outer` return | Không | Không | Entry không còn trên dynamic control flow |

Có thể diễn đạt:

~~~text
cflow(P)      = { entry(P) } ∪ { các join point ở bên dưới entry(P) }
cflowbelow(P) =                { các join point ở bên dưới entry(P) }
~~~

Công thức là **mô hình tư duy cho một lần kích hoạt entry đơn lẻ**. Khi có đệ quy hoặc các kích hoạt `P` lồng nhau, cần xét nhiều entry đang hoạt động cùng lúc; một join point có thể là entry cho lần kích hoạt bên trong nhưng vẫn nằm **bên dưới** một entry `P` khác phía ngoài.

### Khác gì với `cflow(P) && !P`?

Dễ nghĩ rằng:

~~~aspectj
cflowbelow(P)
~~~

luôn tương đương:

~~~aspectj
cflow(P) && !P
~~~

Nhưng **không nên thay thế máy móc**, nhất là khi `P` có thể khớp nhiều join point lồng nhau/đệ quy. `!P` loại bỏ **mọi join point hiện tại khớp P**. Ngược lại, một join point khớp `P` bên trong vẫn có thể nằm dưới một entry `P` khác đã bắt đầu trước đó, nên `cflowbelow(P)` có thể đúng tại join point lồng trong ấy. Hãy hiểu “below” theo **quan hệ control flow của một entry đang hoạt động**, không đơn giản là phủ định nhãn method.

## 5. Ví dụ đầy đủ: Java 17 + AspectJ đếm join point

Ví dụ sau dùng **compile-time weaving của AspectJ** để quan sát ba method trong một chương trình Java nhỏ. Cấu trúc gồm một file Java và một file aspect. Chương trình không dùng thư viện ứng dụng; chỉ cần một bản phân phối AspectJ tương thích với JDK đang chạy, trong đó có compiler `ajc` và runtime `aspectjrt.jar`.

Điểm thiết kế: hai advice tăng hai bộ đếm độc lập thay vì cùng in log tại mọi join point. Nhờ vậy, ví dụ **không phụ thuộc vào thứ tự chạy của hai before advice** cùng khớp một join point; kết quả cuối rõ ràng và kiểm chứng được.

### 5.1. Tệp `FlowDemo.java`

~~~java
public class FlowDemo {
    // Package-private để aspect trong cùng package cập nhật.
    static int cflowHits = 0;
    static int belowHits = 0;

    void outer() {
        inner();
    }

    void inner() {
        leaf();
    }

    void leaf() {
        // Không có side effect; chỉ làm một execution join point.
    }

    void outside() {
        // Execution bên ngoài control flow của outer().
    }

    public static void main(String[] args) {
        FlowDemo demo = new FlowDemo();

        demo.outer();
        demo.outside();

        System.out.println("cflow=" + cflowHits);
        System.out.println("cflowbelow=" + belowHits);
    }
}
~~~

Từ `main()`, chương trình đi qua `outer → inner → leaf`. Sau khi `outer` return mới gọi `outside`. `outside` không được tính vào control flow của `outer`.

### 5.2. Tệp `FlowCounterAspect.aj`

~~~aspectj
public aspect FlowCounterAspect {
    // Entry point của phạm vi control flow cần quan sát.
    pointcut root():
        execution(void FlowDemo.outer());

    // Chỉ đếm ba execution join point cụ thể.
    pointcut tracked():
        execution(void FlowDemo.outer())
        || execution(void FlowDemo.inner())
        || execution(void FlowDemo.leaf());

    // Bao gồm chính execution outer() và descendants.
    before(): tracked() && cflow(root()) {
        FlowDemo.cflowHits++;
    }

    // Chỉ bao gồm descendants bên dưới execution outer().
    before(): tracked() && cflowbelow(root()) {
        FlowDemo.belowHits++;
    }
}
~~~

Đây là **mã AspectJ thật**: phần `pointcut` và `before()` thuộc cú pháp ngôn ngữ khía cạnh, không phải cú pháp Java thuần. Cần compiler `ajc` để weave aspect; lệnh `javac FlowCounterAspect.aj` không hợp lệ.

### 5.3. Biên dịch và chạy

Đặt hai file cùng thư mục. Cần cài phiên bản AspectJ có khả năng chạy với JDK đang sử dụng, thêm executable `ajc` vào `PATH`, đồng thời biết vị trí `aspectjrt.jar` của cùng bản phân phối.

~~~bash
ajc -d out FlowDemo.java FlowCounterAspect.aj

# macOS / Linux: đường dẫn runtime ví dụ theo ASPECTJ_HOME
java -cp "out:$ASPECTJ_HOME/lib/aspectjrt.jar" FlowDemo
~~~

Trên Windows PowerShell, thay dấu phân tách classpath bằng dấu chấm phẩy:

~~~powershell
ajc -d out FlowDemo.java FlowCounterAspect.aj
$runtimeJar = Join-Path $env:ASPECTJ_HOME "lib/aspectjrt.jar"
java -cp "out;$runtimeJar" FlowDemo
~~~

**Tiền điều kiện:** biến `ASPECTJ_HOME` phải trỏ tới bản phân phối có file `lib/aspectjrt.jar`; nếu cài AspectJ bằng Maven/Gradle hoặc đặt JAR ở nơi khác, dùng đúng đường dẫn runtime thực tế. Compiler và runtime cần tương thích. Nếu compiler yêu cầu xác định source/target level, cấu hình cấp Java phù hợp cho phiên bản `ajc` đang dùng; ví dụ chỉ dùng cú pháp Java đơn giản để giảm phụ thuộc vào phiên bản ngôn ngữ.

### 5.4. Kết quả mong đợi

~~~text
cflow=3
cflowbelow=2
~~~

Giải thích chính xác:

| Thời điểm | Join point được `tracked()` chọn? | Trong `cflow(root())`? | Trong `cflowbelow(root())`? |
| --- | --- | --- | --- |
| Thực thi `outer()` | Có | Có | Không |
| Thực thi `inner()` | Có | Có | Có |
| Thực thi `leaf()` | Có | Có | Có |
| Thực thi `outside()` | **Không** | Không | Không |
| Thực thi `main()` | **Không** | Không | Không |

Do đó `cflowHits` tăng ba lần, `belowHits` tăng hai lần. Không có statement nào trong thân ba method tự tăng bộ đếm; mọi thay đổi phát sinh từ hai **before advice** được weave vào execution join point tương ứng.

**Điều kiện của kết quả:** cả hai file phải được biên dịch/weave trong cùng cấu hình. Nếu chỉ biên dịch Java bằng `javac` hoặc chạy class không được weave thì hai counters vẫn là 0; đó là dấu hiệu aspect **không tham gia** chứ không phải `cflow` đã khớp sai.

## 6. Tại sao thêm `tracked()` ngoài `cflow(root())`?

Một pointcut đơn lẻ:

~~~aspectj
before(): cflow(root()) {
    // ...
}
~~~

có thể áp trên **rất nhiều join point** nằm trong phạm vi điều khiển, tùy mức weaving và tập join point AspectJ có thể quan sát. Trong ví dụ có mục tiêu đếm đúng ba method execution, ta dùng:

~~~aspectj
tracked() && cflow(root())
~~~

Điều đó giúp:

- **Rõ phép đo**: chỉ đếm execution của `outer`, `inner`, `leaf`, không đếm constructor, method call, field access hoặc hook khác.
- **Giảm phạm vi advice**: không chạy trên mọi join point chỉ vì nằm trong control flow.
- **Giải thích đúng semantics**: `cflow` là điều kiện ngữ cảnh, còn `tracked` là điều kiện loại/vị trí sự kiện.
- **Dễ kiểm thử**: dự đoán được chính xác số lần increment trong một lịch chạy xác định.

Trong chương trình thật, thường kết hợp điều kiện ngữ cảnh với điều kiện tài nguyên:

~~~aspectj
execution(* AuditStore.save(..))
    && cflow(execution(void BatchRunner.runBatch(..)))
~~~

Nó nói: “chỉ áp policy tại execution của `AuditStore.save` **khi đang nằm trong luồng thực thi động do `runBatch` mở ra**”. Nó **không** nói rằng mọi method do `runBatch` gọi đều được phép hoặc chắc chắn `save` sẽ được gọi.

## 7. Vì sao `execution` và `call` cho kết quả khác?

AspectJ phân biệt:

- `call(ReturnType Type.method(..))` khớp **điểm gọi** một method, nằm phía *caller*.
- `execution(ReturnType Type.method(..))` khớp **việc thực thi thân method**, nằm phía *callee*.

Sơ đồ:

~~~text
Caller.someMethod()
   |
   +-- [call join point] ---> Target.method()
                                 |
                                 +-- [execution join point]
                                 |    body...
                                 +-- return
~~~

Nếu dùng `cflow(call(...))`, entry là **call join point**. Nếu dùng `cflow(execution(...))`, entry là **execution join point**. Cả hai có thể bao quanh những phần khác nhau của quá trình gọi do advice, dispatch và vùng được weave; chỉ dựa vào tên method không đảm bảo chúng là cùng một entry.

Trong ví dụ phần 5, cố ý chọn `execution` để làm việc trên một execution entry rõ ràng, thay vì phụ thuộc caller cụ thể. Khi muốn diễn tả *lời gọi được phát ra từ đâu*, cần xem xét `call` hoặc các điều kiện lexical. Khi muốn diễn tả *operation thực sự đang chạy ở callee*, `execution` thường dễ hiểu hơn.

**Điểm giới hạn:** một class gọi tới thư viện chưa được weave có thể tạo những join point khác với khi toàn bộ bytecode được weave. Việc match một `call` phụ thuộc call site đã được instrument; match `execution` phụ thuộc body/implementation có thể được weave. Phải xác minh build/runtime thực tế.

## 8. `cflow` khác `within` và `withincode` ở điểm nào?

Giả sử method `audit.log` được gọi cả từ `OrderService.place` lẫn `MaintenanceTool.repair`.

| Pointcut | Cơ sở quyết định | Điều nó không biểu đạt |
| --- | --- | --- |
| `within(OrderService)` | Join point nằm về mặt lexical trong type đó | Không tự chọn method của class khác chỉ vì được gọi từ OrderService |
| `withincode(* OrderService.place(..))` | Join point nằm trong code lexical của method `place` | Không tự theo toàn bộ chuỗi gọi sâu qua các class khác |
| `cflow(execution(* OrderService.place(..)))` | Join point hiện tại thuộc dynamic control flow mở bởi execution `place` | Không tự yêu cầu join point là loại execution cụ thể |
| `cflowbelow(execution(* OrderService.place(..)))` | Như trên nhưng nằm dưới entry | Không khớp chính entry mở đầu |
| `execution(* AuditStore.log(..)) && cflow(...)` | Execution của `log` diễn ra trong control flow phù hợp | Không match `log` ở một đường gọi ngoài flow |

Ví dụ `OrderService.place` gọi `Inventory.reserve`, sau đó `Inventory.reserve` gọi `AuditStore.log`. `withincode(* OrderService.place(..))` không chọn execution của `AuditStore.log` vì nó **không nằm trong code lexical của `place`**. Nhưng `cflow(execution(* OrderService.place(..)))` có thể đúng tại `AuditStore.log` khi execution của `place` vẫn đang hoạt động.

Có thể dùng cả điều kiện lexical lẫn dynamic, nhưng không dùng thay nhau nếu yêu cầu nghiệp vụ phụ thuộc call stack.

## 9. Đệ quy và nhiều kích hoạt lồng nhau

Xét:

~~~text
outer(level=2)           <- entry A
  |
  +-- outer(level=1)     <- entry B; đồng thời nằm dưới A
        |
        +-- leaf()
~~~

Giả sử `P` khớp cả hai execution `outer`. Khi `outer(level=1)` bắt đầu:

- Nó là **entry join point** của kích hoạt B.
- Nó đồng thời nằm **bên dưới entry A** vẫn đang hoạt động.
- Vì vậy `cflowbelow(P)` có thể đúng tại execution `outer(level=1)`, mặc dù biểu thức `P` cũng đúng ở join point này.

Đây là lý do không nên luôn thay `cflowbelow(P)` bằng `cflow(P) && !P`. Trong đệ quy, hai biểu thức có thể cho những tập match khác nhau.

Tương tự, nếu pointcut `P` chọn nhiều method entry khác nhau có thể lồng nhau, `cflow` phản ánh **các lần kích hoạt thực tế**, không giả định chỉ có một entry duy nhất cho toàn chương trình. Khi đặt state trong advice, phải cân nhắc mỗi lần kích hoạt, recursion depth và ownership, tránh trộn context của các entry lồng nhau.

Nếu một concern chỉ nên chạy ở entry ngoài cùng, có thể cần pointcut/guard riêng để loại recursion hoặc đánh dấu scope; không suy rằng `cflowbelow` tự mang ý nghĩa “một lần duy nhất cho cả workflow”.

## 10. Gắn dữ liệu từ entry pointcut tới join point bên trong

Một khả năng quan trọng của `cflow` là **truyền/bind context** được match tại entry đến advice ở bên trong dynamic flow, với các pointcut parameter được định nghĩa phù hợp.

Mẫu AspectJ minh họa:

~~~aspectj
pointcut batchEntry(String jobId):
    execution(void BatchRunner.run(String))
    && args(jobId);

before(String jobId):
    execution(* AuditStore.record(..))
    && cflow(batchEntry(jobId)) {
    System.out.println("In batch: " + jobId);
}
~~~

Đây là đoạn **minh họa cú pháp**, không phải file hoàn chỉnh vì `BatchRunner` và `AuditStore` chưa được khai báo. Ý tưởng: khi `run(String)` được gọi với `jobId="J-42"`, advice cho `AuditStore.record` bên trong dynamic flow có thể nhận `jobId` thuộc entry phù hợp.

Cần phân biệt:

- `args(jobId)` ở `batchEntry` lấy tham số từ **entry join point**.
- `execution(* AuditStore.record(..))` chọn **join point hiện tại** mà advice sẽ chạy.
- `cflow(batchEntry(jobId))` là điều kiện runtime và **liên kết** dữ liệu của entry động với advice.

Điều này có thể hỗ trợ correlation/audit theo workflow mà không cần thêm tham số `jobId` vào tất cả method trung gian. Tuy nhiên, không dùng context lấy từ một đối số thiếu tin cậy làm căn cứ cấp quyền nếu chưa xác thực/ủy quyền; cflow không biến dữ liệu do caller cung cấp thành identity đáng tin cậy.

## 11. Kết hợp với `before`, `after` và `around`

`cflow`/`cflowbelow` là **pointcut designator**, không phải một loại advice riêng. Có thể kết hợp với những advice mà bộ máy AOP hỗ trợ:

~~~aspectj
before(): execution(* Store.save(..))
          && cflow(execution(void OrderService.place(..))) {
    // Ghi thời điểm bắt đầu thao tác save ở trong flow đặt hàng.
}
~~~

Đây là đoạn mô tả cú pháp; chưa phải chương trình độc lập.

Một **around advice** có thể chặn `proceed()` khi join point nằm trong control flow được chọn, ví dụ áp policy kiểm soát ở tầng storage khi đang xử lý lệnh import. Tuy nhiên:

- Mỗi `proceed()` phải tuân hợp đồng return/exception của join point.
- Bỏ `proceed()` có thể bỏ qua side effect của method đích.
- Gọi `proceed()` nhiều lần có thể lặp tác động, cần hợp đồng retry/idempotency.
- Advice nằm bên trong một around advice short-circuit có thể không chạy.
- Thứ tự các advice cùng match một join point ảnh hưởng context, metrics và bảo mật.

Không dùng `cflow` như một cơ chế bảo vệ đủ mạnh để nói “bất kỳ ai cũng không thể gọi Store.save ngoài luồng đơn hàng”. Nếu các đường ngoài flow vẫn truy cập tài nguyên nhạy cảm, policy kiểm soát truy cập cần có enforcement phù hợp với mọi entry path.

## 12. Luồng đồng bộ, bất đồng bộ và thread boundary

`cflow` diễn tả **dynamic control flow đang hoạt động**. Nó không tự tạo cơ chế truyền scope hoặc principal xuyên mọi asynchronous boundary.

Ví dụ:

~~~text
Thread A:
  runBatch()
    |
    +-- executor.submit(task)
    |
    +-- return

Thread B (sau đó):
  task.run()
    |
    +-- AuditStore.record()
~~~

Khi `runBatch` tạo task và return, execution của task trên Thread B **không tự thuộc dynamic control flow của execution `runBatch` trên Thread A**. Việc chuyển `Runnable` hay `CompletionStage` sang executor không có nghĩa AspectJ `cflow(runBatch)` tiếp tục áp cho method bên worker. Cơ chế cflow truyền thống theo ngữ cảnh luồng thực thi động, không tương đương distributed trace hoặc một context token được lan truyền tự động.

Các hệ quả:

1. Một advice `before(): save() && cflow(batchEntry())` có thể chạy với thao tác save **đồng bộ** trong `batchEntry`, nhưng không chạy với save của worker ở luồng khác.
2. Advice `after returning` ở entry method có thể kích hoạt khi entry trả **handle**, không phải khi tác vụ hoàn tất.
3. `CompletableFuture.thenApply(...)` có thể chạy ở luồng khác hoặc lúc khác; không nên suy membership cflow từ sơ đồ logic nghiệp vụ.
4. Nếu dùng executor, cần một mô hình truyền context được thiết kế tường minh, hoặc đặt join point entry phù hợp **ở chính worker**.
5. Không xem `ThreadLocal` như một giải pháp chuyển context tự động. Context mang security/transaction còn phải có lifetime, quyền và cleanup đúng.

Ví dụ thiết kế lại:

~~~text
submit(jobId, work)
    |
    +-- wrap task with explicit context
           |
           +-- worker.run()
                   |
                   +-- join point entry ở worker
                   +-- method cần quan sát/kiểm soát
~~~

Ở đây có thể đặt pointcut root vào `worker.run` được weave, hoặc dùng scoped explicit context do tầng được tin cậy quản lý. Không cần cố khiến một `cflow` của method đã return “tồn tại mãi” qua thread khác.

## 13. `cflow` và context của transaction, authorization, tracing

### 13.1. Transaction

Transaction là phạm vi **tài nguyên và transaction manager**, không phải chỉ là phạm vi call stack. Một pointcut `cflow(execution(* OrderService.place(..)))` có thể xác định method nào chạy trong luồng lời gọi của use-case, nhưng **không tự tạo transaction**, không chứng minh connection đã tham gia transaction, và không bảo đảm rollback/commit nếu lời gọi bất đồng bộ hoặc qua resource khác.

Nếu transaction advice được gắn ở một entry join point, phải kiểm tra ownership, propagation, rollback policy, commit timing và resource thật. `cflow` chỉ giúp chọn ngữ cảnh để áp advice, không thay thế transaction coordinator.

### 13.2. Authorization

Một storage method có thể cần phân quyền ở mọi entry path. Nếu chỉ bảo vệ nó khi `cflow(execution(* Controller.action(..)))`, các lời gọi từ CLI, batch hoặc direct service path có thể đi vòng ngoài flow và không được kiểm tra. Bảo mật nên **fail closed** và có điểm enforcement không bị bỏ qua. `cflow` phù hợp điều kiện *thêm* theo ngữ cảnh hơn là làm hàng rào duy nhất cho tài nguyên nhạy cảm.

### 13.3. Tracing và correlation

`cflow` hữu ích để gắn telemetry cho các join point chạy đồng bộ trong một phạm vi. Nhưng trace xuyên service, process và thread cần cơ chế propagating trace context; `cflow` không tự truyền trace ID qua mạng hoặc worker. Không đánh đồng “ở trong cflow” với “thuộc cùng business request về mặt phân tán”.

### 13.4. Retry và caching

Nếu một around advice retry một method bên trong entry, join point có thể xuất hiện nhiều lần trong cùng control flow; advice dùng `cflow` được kích hoạt theo **mỗi lần execution thực tế**. Nếu cache trả sớm ở lớp ngoài, method bên trong không chạy và không phát sinh join point cần match. `cflow` không tự bảo đảm method sẽ thực thi.

## 14. Chi phí runtime và cách kiểm soát phạm vi

Các pointcut dựa trên chữ ký/lexical position thường có nhiều phần điều kiện có thể giải quyết khi weave. `cflow`/`cflowbelow` cần xét **trạng thái dynamic control flow** khi runtime đi tới join point; công cụ có thể sinh mã theo dõi entry/exit hoặc dùng chiến lược tối ưu hóa riêng.

Vì vậy:

- Không đặt điều kiện `cflow` cực rộng trên mọi join point nếu chỉ cần một tập operation nhỏ.
- Hãy kết hợp điều kiện tĩnh cụ thể như `execution(* Store.save(..))` để giảm phạm vi quan sát.
- Ưu tiên root `P` có ý nghĩa nghiệp vụ rõ; tránh các pointcut root match quá nhiều event khó kiểm soát.
- Đo overhead thực tế nếu join point chạy cực thường xuyên, như thao tác nhỏ trong vòng lặp nóng.
- Dùng các công cụ compiler/weaver phù hợp để kiểm tra join point nào thực sự được weave.
- Quan sát nested entry và recursion nếu policy cần state; tránh state global không an toàn đồng thời.
- Chú ý vòng đời class loader và build config: aspect không được weave sẽ không có hiệu lực dù cú pháp đúng.

Không có một mức overhead cố định cho mọi bản AspectJ, mọi JDK và mọi pointcut. Chi phí phụ thuộc cách biên dịch, phạm vi điểm match, số advice, cấu hình weave, tối ưu hóa của công cụ và đặc điểm workload.

## 15. Debug: vì sao pointcut không match?

Khi một advice sử dụng `cflow` hoặc `cflowbelow` không chạy như mong đợi, kiểm tra theo thứ tự:

1. **Entry `P` thực sự match chưa?** `execution` và `call` khác nhau; chữ ký method phải chính xác.
2. **Class nào đã được weave?** Compile-time, post-compile hoặc load-time weaving chỉ có tác dụng trên đúng class/module được cấu hình.
3. **Join point đích được chọn chưa?** Có thể điều kiện `execution(* Repository.*(..))` quá hẹp dù `cflow` đúng.
4. **Join point nằm trong cùng dynamic flow không?** Entry đã return, work chạy trên executor hoặc lời gọi từ đường khác đều làm điều kiện sai.
5. **Có biến thể method/overload/bridge không?** Chữ ký pointcut phải xét method thực được chọn.
6. **Advice khác có short-circuit không?** Nếu method không chạy thì execution join point không xuất hiện.
7. **Có recursion/nested root không?** Tập `cflowbelow` có thể khác suy nghĩ trực giác.
8. **Có nhiều thread đồng thời không?** Log từ hai thread có thể xen kẽ; không dùng log order toàn cục làm bằng chứng duy nhất.
9. **Có instrumentation/agent xung đột không?** Thứ tự weave và bytecode transform có thể thay đổi khả năng match.
10. **Runtime `aspectjrt.jar` có đúng với code woven không?** Sai hoặc thiếu runtime có thể gây lỗi khi chạy.

**Cách thử nhanh:** tạo một before advice chọn riêng `root()`, sau đó một before advice chọn `tracked()` không có cflow, cuối cùng thêm `cflow(root())`. Lần lượt so sánh điểm match giúp phân biệt lỗi ở entry, join point đích hay điều kiện dynamic. Đây là hướng debug, không phải lời khuyên ghi log dữ liệu nhạy cảm vô hạn trong môi trường production.

## 16. Các lỗi nhận thức phổ biến

| Nhầm lẫn | Sửa lại |
| --- | --- |
| “`cflow` là danh sách những method `P` có thể gọi” | Nó xét control flow **đang diễn ra**, không suy toàn bộ call graph có thể xảy ra |
| “`cflowbelow` là alias của `cflow(P) && !P`” | Có thể khác khi `P` lồng nhau/đệ quy |
| “`cflowbelow` chọn chính entry method” | Nó loại entry mở đầu, trừ trường hợp join point là entry bên trong nhưng nằm dưới một entry khác |
| “`cflow` truyền scope qua `CompletableFuture`” | Không tự theo worker/thread hoặc distributed flow |
| “Có advice `cflow` tức method gốc luôn chạy” | Cache/around advice khác có thể short-circuit; event chưa xảy ra thì không có gì để match |
| “`call` và `execution` có cùng boundary” | Call nằm bên caller, execution nằm ở callee |
| “`withincode` giống `cflow`” | Một cái lexical; một cái dynamic |
| “Một `cflow` tự bảo đảm transaction” | Cần transaction manager và tài nguyên thực |
| “Một `cflow` làm bảo mật kín mọi đường gọi” | Đường ngoài flow không được chọn; cần enforcement có coverage đầy đủ |
| “AspectJ là cú pháp Java thường” | `aspect`, `pointcut` và advice cần compiler/weaver thích hợp |
| “Nếu compiler thành công thì aspect chắc chắn áp đúng” | Cần kiểm chứng join point/weaving và hành vi runtime |
| “Mỗi lần gọi có một global cflow dùng chung” | Phải xét control-flow context theo execution/thread và lồng nhau |

## 17. Kiểm thử có ý nghĩa học tập

### 17.1. Với ví dụ phần 5

- **Test A — root vào đúng một lần:** `outer → inner → leaf`; kết quả `cflow=3`, `cflowbelow=2`.
- **Test B — outside sau root:** `outside` không làm tăng counters vì không thuộc `tracked()` và không nằm trong active cflow.
- **Test C — gọi `inner()` trực tiếp từ `main`:** nếu thay body main chỉ gọi `inner()`, không có execution `outer` mở đầu nên hai counters vẫn bằng 0.
- **Test D — gọi `outer()` hai lần tuần tự:** mỗi lần thêm 3 và 2, sau hai lần mong đợi 6 và 4.
- **Test E — bỏ file aspect khỏi weave:** counters giữ 0, chứng minh khác biệt giữa logic Java gốc và advice được weave.

### 17.2. Đường cần thử trong ứng dụng thực

| Kiểm thử | Mục tiêu |
| --- | --- |
| Entry `P` bắt đầu rồi method con gọi cùng thread | Pointcut động đúng |
| Method con được gọi từ entry khác | Không false-positive |
| Entry đã return mới gọi method đích | Không còn active flow |
| Recursion hoặc nested entry `P` | Phân biệt `cflowbelow` với phủ định đơn giản |
| Gọi method đồng bộ có exception | Scope được kết thúc đúng khi stack unwinds |
| Async task trên executor | Không giả định cflow tự lan theo task |
| Method overload hoặc thư viện chưa weave | Hiểu rõ coverage |
| Có cache/retry/transaction advice khác | Đúng số lần join point xuất hiện và thứ tự advice |
| Tải nhiều thread | Counters/log correlation không bị trộn ngữ cảnh |
| Thay đổi config weaving | Test báo lỗi nếu aspect không còn active |

Nếu đo bằng counter shared giữa nhiều thread, cần cấu trúc đồng bộ/atomic thích hợp; các trường `static int` trong demo chỉ dành cho trường hợp đơn luồng xác định. Trên production, các chỉ số còn cần tên operation và key correlation để không ghép sai những control flow cùng chạy.

## 18. Khi nào nên dùng `cflow` và khi nào không?

**Nên cân nhắc** khi concern phụ thuộc một ngữ cảnh lời gọi đồng bộ, chẳng hạn:

- Đánh dấu log/metrics của những method con chỉ khi được gọi từ một nghiệp vụ cụ thể.
- Thêm profiling chuyên sâu cho các operation phát sinh trong một workflow.
- Gắn thông tin từ entry method đến advice chạy ở method con (nếu chính sách cho phép).
- Phân biệt cùng một method được gọi từ nhiều use-case bằng dynamic call stack.
- Dùng `cflowbelow` khi muốn áp policy **chỉ cho phần bên trong**, tránh advice áp lên chính entry.

**Không nên dùng làm lời giải duy nhất** nếu mục tiêu là:

- Bảo vệ một resource bắt buộc được kiểm tra với **mọi** entry path.
- Theo dõi xuyên thread, process, HTTP, message broker hoặc các continuation dài sau method return.
- Xây transaction phân tán hoặc bảo đảm rollback/commit xuyên nhiều tài nguyên.
- Thay thế truyền ngữ cảnh được quản lý rõ ràng cho danh tính người dùng, tenant hoặc correlation ID trong tác vụ nền.
- Chọn một tập method cố định chỉ dựa trên tên/type/annotation: pointcut tĩnh có thể đơn giản hơn.

**Nguyên tắc lựa chọn:** nếu câu hỏi là “join point thuộc type/method nào?” thì dùng pointcut chữ ký/lexical. Nếu câu hỏi là “join point này **đang xảy ra trong quá trình chạy do method X khởi phát** hay không?” thì `cflow` hoặc `cflowbelow` mới phù hợp.

## 19. Checklist tự đánh giá

Trước khi dùng `cflow`/`cflowbelow`, hãy trả lời:

1. Entry `P` chọn đúng **call** hay **execution** join point?
2. Advice có cần khớp **chính entry** không? Nếu cần chọn `cflow`; nếu không, cân nhắc `cflowbelow`.
3. Tập join point đích đã giới hạn bằng `execution/call/within` phù hợp chưa?
4. Có recursion hoặc nhiều entry `P` lồng nhau không?
5. Method đích có được gọi ngoài flow đó hay sau khi entry đã return không?
6. Có tác vụ async, thread switch hoặc callback làm thay đổi dynamic scope không?
7. Cơ chế weaving đã instrument đúng các class và join point chưa?
8. Có advice nào trước đó có thể bỏ `proceed()`, retry hoặc đổi outcome không?
9. Nếu dùng để kiểm soát quyền/giao dịch, có enforcement/resource transaction thực ngoài điều kiện cflow không?
10. Đã thử trường hợp âm tính **không nằm trong flow** và trường hợp **nằm dưới entry** chưa?
11. Runtime overhead và log/metric cardinality có được đo đúng trên workload không?
12. Đã phân biệt rõ lexical scope và dynamic control-flow scope trong tài liệu/bài kiểm thử chưa?

## 20. Tổng kết

`cflow(P)` và `cflowbelow(P)` thể hiện năng lực lựa chọn join point theo **luồng điều khiển động** của AspectJ. `cflow` tính cả join point khởi đầu do `P` chọn; `cflowbelow` chọn các join point xảy ra ở **bên dưới** entry đó. Khi kết hợp với `execution(...)` hoặc `call(...)`, chúng giúp áp advice có điều kiện theo cách một operation được đi tới, thay vì chỉ dựa vào tên method.

Ba giới hạn cần nhớ: **(1)** dynamic flow không phải lexical call graph; **(2)** đệ quy/lồng nhau làm `cflowbelow` khác phép phủ định đơn giản của `P`; **(3)** không tự truyền context qua luồng bất đồng bộ hoặc thay thế cơ chế bảo mật/giao dịch thực. Dùng hai pointcut này đúng lúc sẽ minh họa rất rõ điểm mạnh của AOP: tách chính sách xuyên suốt theo **bối cảnh đang thực thi**, với ngữ nghĩa và phạm vi được xác định cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-006">Advice before, after, around và lời gọi proceed</a>

<details>
<summary>Xem chi tiết</summary>

# Advice before, after, around và lời gọi proceed: minh họa AOP bằng AspectJ

## 1. Mục tiêu học tập

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), một mối quan tâm cắt ngang như ghi log, đo thời gian, kiểm soát quyền hoặc thu thập metric thường xuất hiện ở nhiều vị trí khác nhau trong mã nghiệp vụ. **Advice** là hành vi mà aspect đưa vào những **join point** được chọn bởi một **pointcut**.

Bốn kiểu advice thường gặp là:

- **Before:** chạy trước join point được chọn.
- **After:** chạy sau join point; trong AspectJ cần phân biệt after kiểu finally, after returning và after throwing.
- **Around:** bao quanh join point, có thể thực thi trước và sau, quan sát hoặc thay đổi kết quả, thậm chí quyết định có tiếp tục join point hay không.
- **Proceed:** cơ chế riêng trong around advice để tiếp tục phần thực thi nằm phía trong advice theo chuỗi được dệt.

Mục tiêu của bài không chỉ là nhớ cú pháp, mà còn hiểu **điều gì được đảm bảo, điều gì có thể thay đổi, khi nào phải gọi proceed và vì sao việc bỏ qua/nhân đôi proceed có thể phá hợp đồng nghiệp vụ**.

Các ví dụ sử dụng **AspectJ** như một cách minh họa cụ thể cho AOP cơ bản và nâng cao. Những khái niệm advice, join point và pointcut có ý nghĩa chung trong AOP, nhưng cú pháp và tập join point phụ thuộc cơ chế triển khai.

## 2. Ba thành phần cần hiểu trước khi đọc code

**Join point** là một điểm xác định trong quá trình thực thi mà cơ chế AOP nhận biết được: ví dụ thực thi phương thức, lời gọi phương thức, tạo đối tượng, truy cập trường hoặc xử lý ngoại lệ (tùy khả năng của công nghệ AOP).

**Pointcut** là điều kiện chọn những join point cần áp dụng advice. Một pointcut đơn giản của AspectJ:

~~~java
// Ví dụ một biểu thức pointcut, không phải chương trình Java độc lập.
execution(String Work.transform(String))
~~~

Biểu thức trên nhắm tới **lần thực thi** phương thức Work.transform nhận String và trả String. Đây không phải một lệnh Java thông thường; nó là ngôn ngữ pointcut của AspectJ. Cùng một phương thức có thể được gọi từ nhiều nơi, trong khi join point execution thuộc về việc thực thi thân phương thức.

**Advice** là logic được dệt vào những điểm phù hợp. Trong AspectJ kiểu code-style, aspect được khai báo trong tệp nguồn có cú pháp riêng, chẳng hạn:

~~~java
// Đây là đoạn cú pháp AspectJ trong tệp .aj, không biên dịch độc lập bằng javac.
public aspect TraceAspect {
    before(): execution(void Work.open()) {
        System.out.println("BEFORE open");
    }
}
~~~

**Weaving** là quá trình tích hợp aspect vào chương trình để khi join point diễn ra, advice được gọi đúng cách. AspectJ có thể dệt ở thời điểm biên dịch, sau khi đã biên dịch hoặc trong lúc nạp lớp, tùy cấu hình. Ở ví dụ chính, ta sử dụng **compile-time weaving với trình biên dịch ajc**.

### Phân biệt execution với call

Hai pointcut dễ bị nhầm:

~~~java
// Cú pháp pointcut AspectJ, không phải Java độc lập.
execution(* Work.transform(..))
call(* Work.transform(..))
~~~

- execution nhắm vào việc **thực thi thân phương thức**, nên có thể bao quát những người gọi khác nhau khi code đã được dệt phù hợp.
- call nhắm vào **vị trí phát sinh lời gọi**, tức các call site; phạm vi phụ thuộc các call site có được dệt không.
- Một lời gọi từ mã không được dệt có thể không kích hoạt call advice; điều đó không có nghĩa execution advice trên phần thân đã được dệt cũng bị bỏ qua.
- Một lớp chỉ đơn thuần tham chiếu hoặc nhận Method object chưa chắc tạo ra một join point call hay execution tương ứng.

Khi đánh giá advice có bao phủ hành vi cần kiểm soát hay không, phải xác định rõ **loại join point và phạm vi weaving**.

## 3. Before advice: chạy trước, nhưng không tự xử lý kết quả sau đó

Before advice thực thi trước phần join point mà nó bao quanh theo thứ tự advice đang có hiệu lực:

~~~java
// Cú pháp AspectJ trong tệp .aj.
before(): execution(void Work.open()) {
    System.out.println("BEFORE open");
}
~~~

Nếu Work.open bắt đầu bằng việc in “WORK open”, ta nhận được “BEFORE open” trước “WORK open”, giả sử không có advice khác can thiệp thứ tự.

Công dụng thích hợp:

- Ghi một event bắt đầu, lấy timestamp đầu vào.
- Kiểm tra điều kiện truy cập trước khi thao tác nhạy cảm xảy ra.
- Khởi tạo ngữ cảnh hoặc kiểm tra argument theo chính sách.
- Đếm số lần join point được đi vào.

Giới hạn:

- Before advice **không nắm được giá trị trả về** hay ngoại lệ sẽ phát sinh sau khi target chạy.
- Before không tự quyết định chạy tiếp hay không như around với proceed. Tuy nhiên, nếu chính before advice ném ngoại lệ, luồng thực thi có thể bị ngắt **trước target**, nên advice không được coi là không có tác dụng điều khiển.
- Nếu logger trong before ném exception, một lời gọi vốn hợp lệ có thể bị chặn. Với logging best effort, cần cô lập lỗi logging; với kiểm soát truy cập, từ chối có chủ đích là hành vi theo chính sách.
- Nếu có nhiều aspect, thứ tự giữa các before advice cần hiểu theo quy tắc precedence, không dựa vào thứ tự hiển thị trong file bất kỳ.

Một before advice rất phù hợp để xác thực quyền **trước** khi gọi target, nhưng chỉ khi pointcut thật sự bao phủ mọi đường đi cần bảo vệ.

## 4. After advice: ba biến thể khác ngữ nghĩa

Trong AspectJ, từ “after” không chỉ là một loại duy nhất. Cần nắm ba trường hợp.

### 4.1. after() — ngữ nghĩa finally

~~~java
// Cú pháp AspectJ.
after(): execution(void Work.finish()) {
    System.out.println("AFTER finally");
}
~~~

After kiểu này chạy khi join point kết thúc theo đường bình thường **hoặc** ném ngoại lệ, khi control flow đã đi tới advice tương ứng. Nó tương tự vùng finally quanh phần join point.

Ứng dụng: giải phóng scope hoặc ghi nhận rằng lời gọi đã kết thúc, bất kể thành công hay thất bại. Hạn chế: chính advice cũng có thể ném lỗi và che kết quả/ngoại lệ nghiệp vụ; cleanup cần được thiết kế an toàn và phải bảo toàn chính sách lỗi.

Không nên tự động viết “operation succeeded” trong after kiểu finally: target có thể kết thúc bằng ngoại lệ.

### 4.2. after() returning — chỉ khi hoàn tất bình thường

~~~java
// Cú pháp AspectJ.
after() returning(int value): execution(int Work.count()) {
    System.out.println("AFTER returning: " + value);
}
~~~

Biến thể này chỉ chạy khi join point kết thúc **bình thường**. Có thể bind giá trị trả về để ghi nhận, đo đạc hoặc kiểm tra theo yêu cầu.

Một số lưu ý:

- Giá trị trả về của target được quan sát qua biến binding, không nên suy luận advice này có quyền tùy ý thay kết quả target giống around.
- Với phương thức void, after returning vẫn có thể dùng mà không bind giá trị trả về.
- Nếu phương thức trả về CompletableFuture đang pending, đó vẫn là một **lần trả về bình thường của phương thức**, không đồng nghĩa operation bất đồng bộ đã hoàn thành thành công.
- Chính after returning advice có thể ném exception và làm thay đổi những gì caller nhận được. Bảo toàn ngữ nghĩa là trách nhiệm của aspect.

### 4.3. after() throwing — chỉ khi join point ném ngoại lệ

~~~java
// Cú pháp AspectJ.
after() throwing(IllegalArgumentException error):
        execution(void Work.fail()) {
    System.out.println("AFTER throwing: " + error.getMessage());
}
~~~

Biến thể này được chọn khi join point kết thúc bằng ngoại lệ tương thích với kiểu binding, ở đây là IllegalArgumentException. Nếu target ném ngoại lệ loại khác không phù hợp, advice với binding này không chạy. Có thể dùng kiểu Throwable để nhận phạm vi rộng hơn nhưng phải xử lý đúng lỗi nghiệp vụ và lỗi nghiêm trọng.

Ứng dụng: ghi nguyên nhân thất bại đồng bộ, thống kê lỗi theo loại hoặc gắn thêm ngữ cảnh vận hành. Nếu chỉ cần quan sát, tốt nhất **không thay ngoại lệ gốc**. Nếu chính logger trong after throwing ném ngoại lệ khác, caller có thể mất nguyên nhân ban đầu.

**After throwing tại method execution không tự bắt lỗi phát sinh muộn trong callback hoặc future.** Lỗi đó cần hook hoặc pointcut tại ranh giới tương ứng.

### Bảng so sánh ngắn

| Kiểu advice | Điều kiện chạy | Có thể biết gì tại thời điểm đó? | Lỗi thường gặp |
| --- | --- | --- | --- |
| before | Trước phần join point tương ứng | Tham số và ngữ cảnh đầu vào | Logger lỗi làm chặn target |
| after() | Join point kết thúc, dù normal/exceptional | Đã rời khỏi lời gọi đồng bộ | Ghi nhãn success dù vừa thất bại |
| after() returning | Join point trả về bình thường | Có thể bind return value | Nhầm trả future với thành công nghiệp vụ |
| after() throwing | Join point thoát ra bằng ngoại lệ phù hợp | Có thể bind throwable | Logger che ngoại lệ gốc |
| around | Bao quanh phần tiếp tục khi advice chạy | Có thể quan sát trước/sau, return, throw | Không proceed, proceed nhiều lần, thay contract |

## 5. Around advice và proceed: công cụ mạnh nhất, cũng dễ gây sai nhất

Around advice thay một đoạn luồng điều khiển bằng mã do aspect định nghĩa. Nó thường có mẫu:

~~~java
// Fragment cú pháp AspectJ, không phải Java độc lập.
String around(): execution(String Work.transform(String)) {
    System.out.println("AROUND enter");
    String result = proceed();
    System.out.println("AROUND exit");
    return result;
}
~~~

Khi join point Work.transform thực thi:

1. Around advice được vào.
2. Dòng “AROUND enter” được in.
3. **proceed()** tiếp tục phần thực thi bên trong advice: thường dẫn đến target, nhưng có thể đi qua những advice có precedence thấp hơn.
4. Sau khi phần thực thi đó trả về, advice in “AROUND exit”.
5. Advice trả giá trị cho caller.

Phân biệt quan trọng: **proceed không có nghĩa “gọi trực tiếp target và bỏ qua mọi advice khác”**. Nó đi tiếp qua chuỗi thực thi được dệt, theo thứ tự precedence. Nếu một around advice nằm bên trong trả kết quả mà không gọi proceed, target có thể không được chạy.

### 5.1. Bỏ qua proceed

~~~java
// Fragment minh họa; chỉ hợp lệ khi chính sách cho phép short-circuit.
String around(): execution(String Work.transform(String)) {
    return "CACHED"; // Không gọi proceed().
}
~~~

Cơ chế này cho phép caching, từ chối thực thi, fallback hoặc trả response tổng hợp. Nhưng nếu target có side effect, việc bỏ qua nó có thể là thay đổi nghiệp vụ nghiêm trọng.

Các câu hỏi cần trả lời trước khi short-circuit:

- Target có phải chạy ít nhất một lần theo contract không?
- Trạng thái cache có đúng với context/người dùng hiện tại không?
- Các advice bên trong có còn được chạy khi proceed bị bỏ qua không?
- Có cần audit bắt buộc trước khi trả kết quả thay thế không?
- Với security advice, bỏ qua proceed là deny có chủ đích hay vô tình?
- Giá trị trả về có hợp lệ về kiểu, nullability và ngữ nghĩa không?

### 5.2. Gọi proceed đúng một lần

Nếu mục tiêu chỉ là quan sát, một around advice thông thường nên gọi proceed **chính xác một lần**, giữ nguyên return/exception, và không tạo side effect khác ảnh hưởng caller:

~~~java
// Fragment cú pháp AspectJ minh họa; recordStart/recordEnd giả định đã có.
String around(): execution(String Work.transform(String)) {
    recordStart();
    try {
        return proceed();
    } finally {
        recordEnd();
    }
}
~~~

Cần bảo đảm recordStart/recordEnd không ném lỗi ngoài chính sách; nếu recordEnd ném lỗi trong finally, nó có thể che giá trị trả về hoặc exception của target. Đây là lý do “gọi proceed đúng một lần” chỉ là **một phần** của tính minh bạch.

### 5.3. Gọi proceed nhiều lần

~~~java
// Fragment minh họa cơ chế retry; có thể nguy hiểm với side effect.
String around(): execution(String Work.transform(String)) {
    try {
        return proceed();
    } catch (TransientFailure failure) {
        return proceed();
    }
}
~~~

Trong đoạn trên, TransientFailure là một kiểu exception minh họa do ứng dụng định nghĩa. Nếu lần gọi đầu đã ghi dữ liệu rồi ném lỗi, lần thứ hai có thể ghi lại. Retry cần giới hạn attempt, thời gian, điều kiện lỗi, idempotency, và chính sách xử lý interruption/cancellation.

Một aspect dùng cho tracing thuần túy không nên vô tình gọi proceed hai lần. Không nên giả định proceed được chạy một lần mặc định: **chỉ khi code thực sự gọi nó**, dòng thực thi phía trong mới tiếp tục.

### 5.4. Proceed ném ngoại lệ

Nếu phần tiếp tục ném ngoại lệ, các dòng sau proceed trong cùng khối try thông thường không chạy:

~~~java
// Fragment minh họa, operation được định nghĩa trong ứng dụng.
Object around(): execution(* Work.risky(..)) {
    System.out.println("before proceed");
    try {
        return proceed();
    } finally {
        System.out.println("after proceed, even on throw");
    }
}
~~~

Phần finally chạy khi proceed trả về hoặc ném exception thông thường. Tuy nhiên, nếu dùng logging có thể ném trong finally, lỗi mới vẫn có thể che lỗi target. Với exception transformation, làm rõ đây là chính sách nghiệp vụ chứ không phải tác dụng phụ của logging.

## 6. Ví dụ hoàn chỉnh Java 17+ với AspectJ

Ví dụ sau tách mỗi dạng advice sang một method khác nhau, giúp **thứ tự stdout xác định** mà không phải suy đoán precedence của nhiều advice cùng khớp một join point.

Có hai tệp: Main.java là Java thuần, TraceAspect.aj chứa mã AspectJ. Phải dùng **trình biên dịch AspectJ ajc** để dệt aspect; chạy javac riêng trên Main.java sẽ không thực hiện weaving.

### 6.1. Tệp Main.java

~~~java
import java.util.Locale;

class Work {
    void open() {
        System.out.println("WORK open");
    }

    void finish() {
        System.out.println("WORK finish");
    }

    int count() {
        System.out.println("WORK count");
        return 3;
    }

    String transform(String input) {
        System.out.println("WORK transform");
        return input.toUpperCase(Locale.ROOT);
    }

    void fail() {
        System.out.println("WORK fail");
        throw new IllegalArgumentException("denied");
    }
}

public class Main {
    public static void main(String[] args) {
        Work work = new Work();

        work.open();
        work.finish();

        System.out.println("CALLER count=" + work.count());
        System.out.println("CALLER transform=" + work.transform("aspect"));

        try {
            work.fail();
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER caught: " + error.getMessage());
        }
    }
}
~~~

### 6.2. Tệp TraceAspect.aj

~~~java
public aspect TraceAspect {
    before(): execution(void Work.open()) {
        System.out.println("BEFORE open");
    }

    after(): execution(void Work.finish()) {
        System.out.println("AFTER finally");
    }

    after() returning(int value): execution(int Work.count()) {
        System.out.println("AFTER returning: " + value);
    }

    after() throwing(IllegalArgumentException error):
            execution(void Work.fail()) {
        System.out.println("AFTER throwing: " + error.getMessage());
    }

    String around(): execution(String Work.transform(String)) {
        System.out.println("AROUND enter");
        String result = proceed();
        System.out.println("AROUND exit");
        return result;
    }
}
~~~

**Kiểm tra kiểu:** Around advice trả String vì join point Work.transform trả String. Nếu advice chọn một join point void, cú pháp trả giá trị sẽ khác; nếu advice chọn nhiều phương thức với kiểu trả về không đồng nhất, cần một thiết kế binding/return type phù hợp. Không dùng một kiểu return ngẫu nhiên chỉ vì compiler cho phép chuyển đổi.

### 6.3. Biên dịch với ajc và chạy

Cần JDK 17, một bản AspectJ compiler hỗ trợ Java 17, và thư viện runtime aspectjrt.jar tương ứng. Giả sử biến môi trường ASPECTJ_HOME trỏ tới bộ cài có thư mục lib chứa aspectjrt.jar, lệnh trên hệ Unix:

~~~bash
mkdir -p out
ajc -source 17 -target 17 -classpath "$ASPECTJ_HOME/lib/aspectjrt.jar" -d out Main.java TraceAspect.aj
java -cp "out:$ASPECTJ_HOME/lib/aspectjrt.jar" Main
~~~

Với Windows PowerShell, dấu phân cách classpath là dấu chấm phẩy:

~~~powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
ajc -source 17 -target 17 -classpath "$env:ASPECTJ_HOME\lib\aspectjrt.jar" -d out Main.java TraceAspect.aj
java -cp "out;$env:ASPECTJ_HOME\lib\aspectjrt.jar" Main
~~~

Lưu ý: một số cách đóng gói AspectJ đặt compiler/runtime ở đường dẫn khác; điều kiện cần là lệnh ajc hoạt động và classpath tìm được thư viện runtime tương ứng. Dùng cùng thế hệ phiên bản compiler/runtime, tránh mismatch. Đây là lệnh minh họa chạy local khi bộ công cụ đã được cài đặt, không phải lệnh javac độc lập.

### 6.4. Stdout dự kiến

~~~text
BEFORE open
WORK open
WORK finish
AFTER finally
WORK count
AFTER returning: 3
CALLER count=3
AROUND enter
WORK transform
AROUND exit
CALLER transform=ASPECT
WORK fail
AFTER throwing: denied
CALLER caught: denied
~~~

**Giải thích từng cụm:**

1. Work.open: before advice được gọi trước khi method in “WORK open”.
2. Work.finish: thân method in “WORK finish”, sau đó after kiểu finally được chạy.
3. Work.count: method trả số 3; after returning nhận chính giá trị đó và ghi log trước khi caller in “CALLER count=3”.
4. Work.transform: around in dòng vào, gọi proceed để thân method in “WORK transform” và trả “ASPECT”, rồi around in dòng thoát và trả giá trị cho caller.
5. Work.fail: method in “WORK fail” rồi ném IllegalArgumentException; after throwing bind lỗi và in “denied”; caller bắt lại cùng nhánh lỗi theo hợp đồng.

Ví dụ chỉ đặt một advice áp dụng vào mỗi method nên không phát sinh câu hỏi sắp thứ tự nhiều advice cho cùng join point. Trong chương trình thực, nếu có nhiều advice cùng khớp, kết quả sẽ phụ thuộc precedence và loại advice.

### 6.5. Một thử nghiệm để tự quan sát proceed

Ở TraceAspect.aj, thay nội dung của around cho Work.transform bằng:

~~~java
// Đoạn thay thế trong aspect, không biên dịch song song
// với advice around cũ trên cùng join point.
String around(): execution(String Work.transform(String)) {
    System.out.println("AROUND bypass");
    return "OVERRIDE";
}
~~~

Khi biên dịch lại, thân Work.transform không chạy cho join point đó, vì advice không gọi proceed. Caller sẽ nhận “OVERRIDE”. Đây là thay đổi có chủ đích về luồng điều khiển. Không dùng bản này nếu mục tiêu là tracing thuần túy hay method cần tạo side effect.

## 7. Điều gì xảy ra khi nhiều advice cùng khớp?

Trong ví dụ đơn giản, mỗi method có một advice riêng. Trên ứng dụng lớn, một join point có thể được nhiều aspect chọn, ví dụ kiểm tra quyền, transaction policy, retry, metrics và logging.

Hình dung:

~~~text
caller
  -> around A (pre)
      -> before B
          -> around C (pre)
              -> target
          <- around C (post)
      <- after D
  <- around A (post)
~~~

Sơ đồ chỉ mô tả **một thứ tự khả dĩ**, không phải quy tắc precedence cố định cho mọi aspect. AspectJ có cơ chế xác định precedence giữa aspect và quy tắc thứ tự advice bên trong aspect; có thể dùng khai báo precedence tường minh khi thiết kế cần thứ tự chắc chắn.

Những câu hỏi quan trọng:

- Access control có bắt buộc chạy trước operation nhạy cảm không?
- Retry phải bao quanh logging, hay logging bao quanh mỗi attempt?
- Metrics đo **mỗi attempt** hay **toàn bộ operation**?
- Một around advice không proceed có ngăn các advice nằm sâu hơn chạy không?
- Exception của logging trong finally có che lỗi của target không?
- Một advice trả fallback có vô tình biến lỗi nghiệp vụ thành success không?

Nếu aspect A bao ngoài B, thứ tự thường quan sát là **A pre → B pre → target → B post → A post** khi cả hai đều gọi proceed đúng một lần và không ném lỗi. Nhưng nếu advice có nhánh short-circuit hoặc exception, chuỗi đó có thể khác.

Khi bảo toàn contract quan trọng, nên thiết kế test cụ thể với nhiều advice và output/counter thay vì dựa vào suy luận từ một ví dụ một aspect.

## 8. Return value và kiểu dữ liệu trong around

Around advice có khả năng tạo ra giá trị mà caller nhìn thấy. Những ràng buộc cần xét:

- **Kiểu trả về tương thích:** around cho một join point trả String không được tự ý trả Integer. Compiler có thể kiểm tra nhiều vi phạm kiểu trước khi weaving.
- **Giá trị null:** nếu method cho phép null, advice không nên giả định mọi kết quả khác null. Nếu contract cấm null, fallback null có thể gây lỗi ở caller.
- **Giữ exception gốc:** nếu mục tiêu quan sát, ném lại đúng ngoại lệ; không tạo exception mới do logger hỏng.
- **Hợp đồng side effect:** bỏ qua proceed nghĩa là thân join point có thể không chạy; proceed hai lần có thể thực hiện side effect hai lần.
- **Return wrapper:** nếu target trả CompletionStage hoặc một đối tượng có identity quan trọng, thay bằng wrapper/stage khác có thể đổi semantics.
- **Checked exception:** một around advice cần tuân theo quy tắc khai báo ngoại lệ, binding và type checking của AspectJ cho join point nó chọn.

Một example ngắn về biến đổi kết quả có chủ đích:

~~~java
// Cú pháp AspectJ; giả sử Work.transform đã được dệt.
String around(): execution(String Work.transform(String)) {
    String original = proceed();
    return "[" + original + "]";
}
~~~

Nếu input là “aspect”, caller nhận “[ASPECT]”. Đây là một **thay đổi nghiệp vụ** hoặc chính sách trình bày; không nên thực hiện trong logging aspect chỉ để ghi nhận.

## 9. Binding tham số và dùng proceed với dữ liệu đã thay đổi

Around có thể bind đối số từ join point bằng args và chuyển đối số mới cho proceed khi cơ chế ngôn ngữ cho phép. Ví dụ code-style AspectJ:

~~~java
// Fragment cú pháp AspectJ, không phải đoạn Java độc lập.
// Nếu chạy cùng ví dụ đầy đủ, phải bỏ/thay around
// đã chọn cùng Work.transform để tránh trùng chính sách.
String around(String input):
        execution(String Work.transform(String)) && args(input) {
    String normalized = input.trim();
    return proceed(normalized);
}
~~~

Trong trường hợp này, advice chọn Work.transform và bind tham số String vào input. Nó tạo giá trị đã trim rồi truyền làm đối số khi proceed. Bản này minh họa khả năng can thiệp; **không dùng để logging thuần túy**, vì nó thay đổi dữ liệu mà target nhận.

Đừng nhầm proceed trong AspectJ code-style với mọi API proxy có tên tương tự; cú pháp truyền đối số và quy tắc binding có thể khác. Với nhiều binding (this, target, args, annotation), quy tắc truyền giá trị cho proceed phải khớp thứ tự và loại binding theo AspectJ.

Nếu normalizing input là hợp đồng nghiệp vụ, cần kiểm thử:

1. Input có ký tự Unicode hoặc khoảng trắng đặc biệt.
2. Null input: input.trim() có thể ném NullPointerException.
3. Chuẩn hóa có làm thay đổi quyền, chữ ký số hoặc cache key không?
4. Có những advice khác phụ thuộc original input hay không?
5. Target có side effect hoặc kiểm tra dữ liệu gốc trước khi thay đổi không?

Khi chỉ muốn đo lường, tốt hơn là đọc arguments để ghi log theo chính sách, không tự sửa chúng.

## 10. After throwing khác với catch quanh proceed

After throwing giúp ghi nhận một ngoại lệ đã thoát khỏi join point được chọn; nó không mặc định phục hồi bằng cách trả fallback.

Around advice có thể bắt ngoại lệ của phần tiếp tục:

~~~java
// Fragment minh họa; Work.risky được định nghĩa trong ứng dụng.
String around(): execution(String Work.risky()) {
    try {
        return proceed();
    } catch (IllegalStateException failure) {
        recordFailure(failure);
        throw failure;
    }
}
~~~

Ở đây recordFailure là hàm giả định. Mục đích là **giữ nguyên đối tượng ngoại lệ**. Nếu recordFailure ném lỗi, ngoại lệ ghi nhận có thể che lỗi gốc; thiết kế logger an toàn là một phần của chính sách.

Around cũng có thể trả fallback thay vì throw, nhưng đó là quyết định rõ ràng về hợp đồng của operation. Hãy phân biệt:

- Quan sát lỗi, rồi ném lại.
- Chuyển lỗi thành ngoại lệ khác được định nghĩa.
- Cung cấp fallback.
- Retry có giới hạn sau một nhóm lỗi đã phân loại.

Nếu một after throwing advice chỉ bắt RuntimeException còn target ném checked exception, đừng giả định advice đó sẽ thấy mọi failure. Phải chọn kiểu throwable/binding theo yêu cầu kiểm thử.

## 11. After finally và lỗi trong advice

Một giả định thường thấy: vì after kiểu finally luôn chạy sau target, nó an toàn để ghi log trong mọi trường hợp. Đây là suy luận sai.

~~~java
// Fragment minh họa, sink được khai báo ở nơi khác.
after(): execution(* Work.process(..)) {
    sink.write("operation finished"); // Có thể ném lỗi.
}
~~~

Nếu target đã thành công và sink.write ném lỗi, caller có thể nhìn thấy lỗi logger thay cho kết quả thật. Nếu target đã ném ngoại lệ nghiệp vụ và sink.write lại ném lỗi, kết quả quan sát được cũng có thể bị thay đổi.

Hãy xác định chính sách:

- **Best effort diagnostics:** cô lập lỗi logging ở chính helper hoặc advice; không làm sai outcome nghiệp vụ.
- **Audit bắt buộc:** nếu nghiệp vụ chỉ được phép diễn ra khi audit bền vững, phải thiết kế ranh giới transaction/cam kết phù hợp hoặc từ chối trước khi tạo side effect. Không “tự nhiên” phát lỗi audit sau khi side effect đã commit rồi để caller retry.
- **Cleanup bắt buộc:** nếu việc giải phóng tài nguyên bị lỗi, cần quy tắc xử lý/ghi nhận cụ thể; không tự nuốt mọi lỗi với lý do đó là after advice.
- **Error và fatal condition:** bắt RuntimeException khác với nuốt mọi Throwable; không hứa an toàn tuyệt đối khi JVM hết bộ nhớ hoặc mất khả năng hoạt động.

AOP không làm lỗi của instrumentation trở nên vô hại. Mọi advice đều là code thật, có thể ném ngoại lệ và sử dụng tài nguyên.

## 12. Advice và bất đồng bộ: “after returning” của phương thức không phải “after completion”

Một method có thể trả ngay một CompletableFuture đang pending:

~~~java
// Fragment Java trong một class; không độc lập.
CompletableFuture<String> fetchAsync() {
    return pendingTask;
}
~~~

Nếu after returning advice gắn vào execution của fetchAsync, nó chạy khi **phương thức trả về đối tượng future**, chứ không tự chờ task hoàn tất. Tác vụ có thể tiếp tục, thành công, thất bại hoặc bị hủy sau đó.

Nếu cần log kết quả cuối, có thể thêm completion observer:

~~~java
// Fragment Java dùng CompletionStage; recordOutcome phải có
// chính sách không ném lỗi quan sát ngoài ý muốn.
CompletionStage<String> observed = stage.whenComplete((value, error) -> {
    recordOutcome(value, error);
});
~~~

Kết quả của whenComplete là một dependent stage. Nếu observer ném lỗi khi stage nguồn thành công, dependent stage có thể thất bại. Và cancellation của dependent stage không luôn tương đương cancellation upstream. Do đó, một around advice thay stage trả về cần hiểu rõ semantics, không chỉ đơn giản attach callback.

**Lỗi muộn trong callback không tự đi vào after throwing của lời gọi tạo future.** Để quan sát nó, phải chọn join point/hook thật sự liên quan tới callback hoặc completion.

## 13. Trường hợp self-invocation và phương thức được gọi từ nhiều nơi

Trong ví dụ main, Work là một class được ajc dệt tại **method execution**, nên các advice execution được nhắm trực tiếp vào thân method được dệt. Đây khác với cơ chế chỉ can thiệp khi caller đi qua một đối tượng proxy.

Giả sử Work.open được gọi từ một phương thức khác trong chính Work. Với execution weaving phù hợp, advice có thể vẫn áp dụng khi Work.open thực thi. Nếu chuyển sang cơ chế call interception giới hạn ở proxy, internal call có thể không kích hoạt advice.

Một kiểm thử coverage nghiêm túc cần:

- Xác nhận code target thật sự đã được woven.
- Gọi join point từ đường thông thường, từ đường nội bộ và từ đường callback nếu chúng tồn tại.
- Phân biệt execution với call pointcut.
- Kiểm tra overload và các method signature có match pointcut hay không.
- Kiểm tra việc thêm class mới hoặc tải class động có được weaving không.
- Không suy từ stdout của một ví dụ compile-time weaving rằng mọi lời gọi ở mọi runtime đều có coverage giống nhau.

Nếu advice dùng để kiểm soát quyền, phải bảo đảm mọi đường đến thao tác nhạy cảm đi qua một điểm cưỡng chế có thẩm quyền; không dựa vào tên “aspect” để suy ra an toàn.

## 14. Thứ tự thực thi khi có exception

Để hiểu thời điểm callback:

~~~text
Đường bình thường (một around bao ngoài phần tiếp tục):
around pre
  -> proceed
      -> target
      <- normal result
around post
<- caller nhận result

Đường ném lỗi:
around pre
  -> proceed
      -> target throws E
      <- E truyền ra
around catch/finally (nếu có)
<- caller thấy E hoặc kết quả đã được chính sách chuyển đổi
~~~

Nếu around viết:

~~~java
// Fragment minh họa: after-proceed chỉ chạy khi proceed trả bình thường.
Object around(): execution(* Work.risky(..)) {
    System.out.println("enter");
    Object result = proceed();
    System.out.println("normal exit");
    return result;
}
~~~

Khi proceed ném lỗi, dòng “normal exit” bị bỏ qua. Nếu muốn ghi sự kiện kết thúc trong cả hai nhánh, dùng finally (và cô lập lỗi logger theo chính sách). Nếu muốn phân biệt success và failure, dùng catch có chủ đích hoặc after returning/after throwing đúng nghĩa.

Không hứa một thứ tự cố định nếu nhiều around/before/after cùng áp dụng và precedence chưa được định nghĩa; test cùng runtime, cùng aspect set để xác định thứ tự thật.

## 15. Những lỗi thường gặp với proceed và cách tránh

### Lỗi 1: không gọi proceed trong advice chỉ dùng để đo đạc

Nếu around trả giá trị mặc định mà không proceed, business method không chạy. Cần kiểm thử target invocation count bằng 1 khi mục đích chỉ là observability.

### Lỗi 2: proceed nhiều lần vô tình

Một vòng retry vô hạn có thể tạo side effect lặp lại. Chỉ retry khi có ngân sách, điều kiện lỗi đã xác định và hành vi idempotent hoặc reconcile.

### Lỗi 3: tin rằng proceed luôn gọi thẳng target

Proceed tiếp tục chain bên trong; một advice khác có thể short-circuit hoặc ném lỗi. Không nhầm thứ tự logic với call trực tiếp.

### Lỗi 4: after returning là “operation đã thành công”

Nếu method trả future, chỉ có lời gọi **trả về bình thường**. Phải quan sát completion nếu cần outcome cuối.

### Lỗi 5: after() kiểu finally chỉ chạy khi thành công

Sai. Nó áp dụng cả đường ném lỗi trong phạm vi đã dệt; không ghi “success” một cách vô điều kiện.

### Lỗi 6: after throwing luôn thấy mọi loại lỗi

Binding kiểu cụ thể có thể chỉ khớp những exception tương thích. Ngoài ra, các lỗi xảy ra sau khi join point trả về không nằm trong phạm vi lời gọi đồng bộ đó.

### Lỗi 7: logger trong advice không thể làm thay đổi outcome

Sai. Một exception do logger ném có thể che return hoặc exception nghiệp vụ. Giảm side effect và cô lập lỗi quan sát theo chính sách.

### Lỗi 8: lạm dụng around cho mọi trường hợp

Nếu chỉ cần ghi log trước, before đơn giản hơn. Nếu chỉ cần quan sát return, after returning diễn đạt ý định rõ hơn. Around phù hợp khi cần điều khiển phần tiếp tục, biến đổi dữ liệu hoặc kết hợp quan sát hai phía có giữ trạng thái.

### Lỗi 9: dùng javac để biên dịch trực tiếp tệp .aj

Mã code-style AspectJ cần compiler/weaver phù hợp; javac thuần không hiểu khai báo aspect hoặc advice trong tệp .aj.

### Lỗi 10: giả định thứ tự giữa nhiều aspect là thứ tự trong file bất kỳ

Precedence là vấn đề ngữ nghĩa của AOP. Khi cần chính xác, khai báo precedence và kiểm thử, nhất là với authorization, transaction policy, retry và telemetry.

## 16. Checklist kiểm thử advice đúng hợp đồng

Với một join point mẫu, nên có các tình huống sau:

| Test | Điều phải xác minh |
| --- | --- |
| Before trên method bình thường | Advice chạy trước target; target đúng một lần |
| Before ném lỗi có chủ đích | Target không chạy, caller nhận lỗi từ chối đúng contract |
| After finally khi target trả bình thường | Advice được chạy sau thân method |
| After finally khi target ném | Advice vẫn được chạy, không che lỗi gốc nếu policy yêu cầu |
| After returning có giá trị | Advice nhận đúng giá trị trả về của join point |
| After returning khi target throw | Advice không chạy |
| After throwing với exception phù hợp | Advice nhận đúng loại và thông điệp lỗi |
| After throwing với exception không phù hợp | Advice không khớp binding đó |
| Around proceed một lần | Thân target chạy một lần; return và exception được bảo toàn |
| Around không proceed | Target bị bỏ qua có chủ đích; giá trị thay thế hợp lệ |
| Around retry | Số lần target chạy đúng attempt, không sinh side effect trái policy |
| Around trên async return | Advice return đúng lúc; completion được theo dõi riêng |
| Nhiều advice cùng khớp | Precedence và log order đúng cấu hình |
| Logger tự throw | Không thay đổi kết quả nghiệp vụ ngoài policy |
| Direct/internal call | Coverage phù hợp loại pointcut và weaving |
| Refactor signature | Pointcut vẫn match hoặc regression test phát hiện |

Những bài test này giúp phân biệt **đúng cú pháp** với **đúng hành vi thực thi**. Một chương trình compile được vẫn có thể sai logic nếu bỏ proceed hoặc vô tình nuốt ngoại lệ.

## 17. Cách lựa chọn advice theo mục đích

| Mục tiêu | Kiểu advice thích hợp | Lý do |
| --- | --- | --- |
| Kiểm tra quyền trước khi vào target | Before hoặc around | Có thể từ chối trước khi side effect diễn ra |
| Đo thời gian nhập phương thức | Before + hook kết thúc, hoặc around | Cần timestamp bắt đầu và kết thúc |
| Log mỗi lần target trả về bình thường | After returning | Phân biệt rõ với đường lỗi |
| Ghi nhận exception đồng bộ | After throwing | Có thể bind throwable |
| Cleanup sau cả success và failure | After finally hoặc finally trong around | Nhìn cả hai đường rời join point |
| Cache với short-circuit | Around | Có thể trả kết quả mà không proceed |
| Retry có giới hạn | Around | Có quyền gọi proceed nhiều lần theo chính sách |
| Biến đổi argument hoặc return | Around | Có thể can thiệp trước/sau proceed |
| Tracing của kết quả bất đồng bộ | Hook completion kết hợp advice ở invocation | Return future chưa có nghĩa complete |
| Quan sát mà không đổi hợp đồng | Advice đơn giản nhất, thêm error isolation | Giảm bề mặt thay đổi control flow |

Nếu yêu cầu chỉ là ghi nhật ký “bắt đầu”, không cần around. Nếu cần chuyển đổi return value, before/after returning thường không phải lựa chọn để sửa kết quả; dùng around với hợp đồng rõ ràng.

## 18. Bài tập tự đánh giá

**Câu 1.** Một before advice in “start”, target ném ngoại lệ. Before có tự in “failed” sau khi target ném không?

**Đáp án:** Không. Before chạy trước join point và không có nhánh sau target. Dùng after throwing hoặc around catch khi cần ghi lỗi.

**Câu 2.** After() kiểu finally khác after returning như thế nào?

**Đáp án:** After finally chạy khi join point kết thúc theo cả đường bình thường lẫn ngoại lệ (trong phạm vi advice áp dụng). After returning chỉ chạy khi join point trả về bình thường và có thể bind return value.

**Câu 3.** Around không gọi proceed có làm thân method chạy không?

**Đáp án:** Không, đối với phần join point nằm phía trong advice đó. Around có thể short-circuit hợp lệ theo chính sách, nhưng nếu lỡ quên proceed thì mất hành vi nghiệp vụ.

**Câu 4.** Gọi proceed hai lần có vô hại với một phương thức tạo đơn hàng không?

**Đáp án:** Không. Thao tác có thể tạo hai đơn hàng hoặc thực hiện side effect hai lần. Retry phải có điều kiện rõ, giới hạn attempt và idempotency.

**Câu 5.** Một method trả CompletableFuture pending. After returning chạy. Nghiệp vụ đã thành công chưa?

**Đáp án:** Chưa thể kết luận. Chỉ phương thức đã trả future bình thường. Cần completion hook để biết future hoàn tất thành công, thất bại hay bị hủy.

**Câu 6.** Nếu một after throwing advice bind IllegalArgumentException mà target ném IOException, advice đó có nhất thiết chạy không?

**Đáp án:** Không. Binding throwable phải tương thích kiểu exception được ném. Nếu cần quan sát phổ rộng, thiết kế binding thích hợp và xử lý nhiều loại lỗi.

**Câu 7.** Trong around, proceed có luôn gọi trực tiếp thân method không qua advice nào khác?

**Đáp án:** Không. Proceed đi tiếp qua chain bên trong theo precedence; nó có thể gặp advice khác trước khi đến target.

**Câu 8.** Một after finally advice ghi log bị lỗi khi target đã ném lỗi nghiệp vụ. Caller có thể thấy lỗi gì?

**Đáp án:** Nếu logging throw không được cô lập, lỗi logger có thể che lỗi nghiệp vụ. Với policy quan sát best effort, phải giữ nguyên lỗi target.

**Câu 9.** Tại sao không thể chỉ dùng javac để biên dịch TraceAspect.aj?

**Đáp án:** Tệp .aj sử dụng cú pháp riêng của AspectJ, cần ajc hoặc cơ chế weaving tương thích. Java compiler thuần không nhận khai báo code-style aspect.

**Câu 10.** Trong ví dụ, Work.transform được gọi từ nhiều caller. execution advice có nhất thiết giống call advice trên mọi caller không?

**Đáp án:** Không. Execution advice được dệt vào thực thi method; call advice bắt call site thuộc phạm vi weaving. Phạm vi khác nhau nên cần test phù hợp.

## 19. Tự thực hành theo ba thí nghiệm

**Thí nghiệm A — Không proceed:** đổi around trong TraceAspect.aj sang trả một String cố định. Sau khi biên dịch lại, kiểm tra “WORK transform” biến mất và caller nhận chuỗi thay thế. Giải thích vì sao điều này nguy hiểm với method có side effect.

**Thí nghiệm B — Proceed một lần với finally:** thay around bằng mẫu try/finally; sửa Work.transform để ném IllegalArgumentException khi input null. Quan sát phần finally vẫn chạy khi proceed throw, còn dòng sau proceed ngoài finally sẽ bị bỏ qua.

**Thí nghiệm C — Hai advice cùng join point:** tạo một around đo thời gian và một before logging cùng chọn Work.transform. Định nghĩa precedence, dự đoán thứ tự trước khi chạy rồi kiểm chứng stdout. Thử short-circuit trong around và ghi lại advice nào không còn chạy. Chú ý không suy ngược quy luật precedence từ một quan sát tình cờ.

Khi hoàn thành, cần giải thích được không chỉ **dòng nào in trước**, mà cả **target thực thi bao nhiêu lần, caller nhận kết quả gì và những mối quan tâm cắt ngang nào còn được thực hiện**.

## Kết luận

Before phù hợp với xử lý trước join point. After finally, after returning và after throwing đại diện cho **những điều kiện kết thúc khác nhau**. Around là dạng có quyền điều khiển mạnh: nó có thể đi tiếp qua proceed, thay đổi dữ liệu, short-circuit hoặc retry. Sức mạnh đó đòi hỏi kỷ luật về kiểu dữ liệu, return value, ngoại lệ, side effect và thứ tự advice. AspectJ giúp thấy cơ chế này bằng các pointcut execution và mã .aj được weaving, nhưng nguyên tắc vẫn thuộc AOP nói chung: **chọn advice theo mục đích, hiểu rõ phần thực thi được bao quanh, và chỉ thay đổi hành vi nghiệp vụ khi chính sách thật sự yêu cầu**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-007">Khai báo ưu tiên, aspect trừu tượng và mô hình tạo aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo ưu tiên, Aspect trừu tượng và mô hình tạo Aspect trong AspectJ

## 1. Vì sao ba khái niệm này phải được học cùng nhau?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một chương trình có thể áp dụng nhiều **aspect** lên cùng một **join point**. Chẳng hạn, một operation đồng thời phải được kiểm tra quyền, ghi log, đo thời gian và quản lý giao dịch. Khi ấy, ba câu hỏi kiến trúc xuất hiện:

1. **Aspect nào chạy trước, aspect nào chạy sau?** Đây là vấn đề **advice precedence**, có thể điều khiển bằng khai báo ưu tiên.
2. **Làm sao tái sử dụng phần cơ chế chung mà vẫn cho từng module xác định điểm cắt?** Đây là vai trò của **abstract aspect**, kế thừa aspect và abstract pointcut.
3. **Có bao nhiêu đối tượng aspect tồn tại, chúng gắn với đâu và giữ trạng thái bao lâu?** Đây là vấn đề **aspect instantiation model**.

Ba vấn đề có liên quan chặt chẽ: thứ tự quyết định phạm vi quan sát/kiểm soát, kế thừa quyết định cách chia sẻ chính sách, còn mô hình tạo aspect quyết định ai sở hữu state của advice. Nếu thiết kế sai, cùng một chương trình có thể ghi log trùng, bỏ qua kiểm tra quyền, giữ dữ liệu người gọi trong singleton hoặc coi tác vụ bất đồng bộ đã kết thúc ngay sau khi phương thức trả về.

Bài này minh họa bằng **cú pháp AspectJ độc lập** cùng những nguyên tắc AOP áp dụng rộng hơn. AspectJ là ngôn ngữ và công cụ weaving có cú pháp riêng; các đoạn mã dùng từ khóa aspect, pointcut, before, around, declare và per-clause cần được biên dịch bằng **ajc**, không thể biên dịch trực tiếp bằng javac thuần.

## 2. Ôn nhanh: join point, pointcut, advice, aspect

- **Join point:** vị trí có thể can thiệp vào hoạt động chương trình, chẳng hạn method call, method execution hoặc constructor execution; loại join point tùy cơ chế AOP.
- **Pointcut:** biểu thức chọn các join point, ví dụ chọn execution của một method nghiệp vụ.
- **Advice:** hành vi được chạy trước, sau, khi có lỗi hoặc thay thế/bọc join point. Với around advice, proceed() là cách tiếp tục tới advice có ưu tiên thấp hơn hoặc phép tính gốc.
- **Aspect:** đơn vị đóng gói pointcut, advice, field và method liên quan đến concern.
- **Weaving:** quá trình liên kết logic aspect vào chương trình. Cấu hình biên dịch hoặc nạp lớp quyết định các join point thực tế được áp dụng.
- **Concrete aspect:** aspect có thể được hệ thống AspectJ tạo instance và chạy advice.
- **Abstract aspect:** aspect dùng làm nền tảng kế thừa; có thể chứa pointcut trừu tượng và advice dùng chung, nhưng không tự trực tiếp tạo một instance nghiệp vụ theo kiểu concrete aspect.
- **Per-clause:** phần khai báo kiểu gắn các instance của aspect với singleton, object, dynamic control flow hoặc type.

Một lời gọi có nhiều advice không phải mặc nhiên một danh sách callback tuần tự. Advice có thể bao quanh nhau như các lớp lồng nhau; khi một tầng kết thúc, các lệnh sau proceed() hoặc after advice chạy trong quá trình quay ra ngoài.

## 3. Khai báo ưu tiên là gì?

**Precedence** xác định thứ bậc giữa các advice cùng áp dụng tại **một join point**. Trong AspectJ, cú pháp tổng quát là:

~~~aspectj
declare precedence: FirstAspect, SecondAspect, ThirdAspect;
~~~

Danh sách sắp xếp **từ ưu tiên cao sang ưu tiên thấp**. Nếu advice của FirstAspect và SecondAspect cùng khớp một join point, advice ở FirstAspect có ưu tiên cao hơn.

Có thể đặt khai báo trong một aspect chuyên điều phối thứ tự:

~~~aspectj
public aspect AdviceOrdering {
    declare precedence: AuthenticationAspect,
                        AuthorizationAspect,
                        TransactionAspect,
                        ObservationAspect;
}
~~~

Đây là **đoạn cú pháp minh họa**, các aspect trong danh sách chưa được định nghĩa ở đây nên không phải chương trình độc lập. Trong thiết kế thực tế, thứ tự cần dựa trên contract của từng concern, không được suy luận rằng danh sách này đúng cho mọi hệ thống.

Các điểm cần nhớ:

- Precedence chỉ có ý nghĩa khi nhiều advice **cùng khớp join point**. Nó không buộc những join point không liên quan phải chạy theo một thứ tự toàn cục.
- Thứ tự khai báo có thể ảnh hưởng đến việc một advice quan sát được kết quả, exception hoặc side effect do advice khác tạo ra.
- Nếu không có quan hệ precedence và không có quan hệ kế thừa xác định thứ tự giữa hai aspect khác nhau, **không nên dựa vào một thứ tự ngẫu nhiên**.
- Một khai báo không thay thế việc chọn pointcut phù hợp; aspect không khớp vẫn không chạy.
- Khi các yêu cầu thứ tự mâu thuẫn, trình biên dịch có thể báo lỗi về precedence/circularity. Không cố xử lý bằng cách đoán thứ tự của bản build hiện tại.

## 4. Before, after và around dưới tác động của precedence

Giả sử có Aspect A ưu tiên cao hơn Aspect B, cùng bao quanh một method:

~~~text
A quanh B quanh method:
A: trước proceed
    B: trước proceed
        METHOD chạy
    B: sau proceed
A: sau proceed
~~~

Với hai around advice đều gọi proceed() đúng một lần, A là vòng **ngoài** và B là vòng **trong**. Vì vậy:

- Phần trước proceed() của A chạy trước B.
- Phần sau proceed() của B chạy trước phần sau proceed() của A.
- Nếu A không gọi proceed(), B và method gốc có thể không chạy.
- Nếu B ném exception, A có thể thấy exception nếu đường điều khiển đi qua A.
- Với before advice, advice có ưu tiên cao hơn thường chạy trước các before advice thấp hơn cùng join point.
- Với after advice, logic thường chạy khi luồng điều khiển quay ra, vì vậy **không được suy luận đơn giản “ưu tiên cao sẽ in trước trong mọi loại advice”**.

Xét ví dụ:

~~~aspectj
aspect Outer {
    void around(): execution(void Work.run()) {
        System.out.println("OUTER enter");
        proceed();
        System.out.println("OUTER leave");
    }
}

aspect Inner {
    void around(): execution(void Work.run()) {
        System.out.println("INNER enter");
        proceed();
        System.out.println("INNER leave");
    }
}

aspect Ordering {
    declare precedence: Outer, Inner;
}
~~~

Nếu lớp Work có method run() in WORK và chỉ một invocation được thực thi, thứ tự là OUTER enter, INNER enter, WORK, INNER leave, OUTER leave. Đoạn trên minh họa **các aspect**, chưa cung cấp lớp Work nên không phải project độc lập; project chạy đầy đủ nằm ở phần ví dụ sau.

**Với advice bao quanh có thể ném lỗi**, hãy dùng try/finally nếu logic kết thúc/cleanup phải chạy cả khi proceed() ném exception. Chỉ đặt System.out.println sau proceed() sẽ không bảo đảm nó được gọi trong đường lỗi.

## 5. Cú pháp declare precedence và type pattern

AspectJ cho phép danh sách **type pattern**, không chỉ tên aspect đơn lẻ:

~~~aspectj
aspect PolicyOrder {
    declare precedence: *..*Security*, *..*Audit*, *;
}
~~~

Trong ngữ cảnh cú pháp này:

- Pattern đầu chọn các aspect phù hợp mẫu tên có chứa Security theo quy tắc type pattern.
- Pattern kế tiếp chọn nhóm Audit.
- Dấu * cuối biểu diễn các kiểu không được khớp bởi pattern trước trong danh sách, giúp có quy tắc cho phần còn lại.
- Trong danh sách declare precedence, wildcard đại diện phần còn lại này được dùng tối đa một lần.
- Dấu + sau tên kiểu có thể diễn đạt một type pattern gồm kiểu và các kiểu con, hữu ích khi khai báo thứ tự cho **các concrete subaspect** kế thừa abstract aspect.

Ví dụ:

~~~aspectj
aspect FamilyOrdering {
    declare precedence: SecurityPolicy+, AuditTemplate+, *;
}
~~~

Đây là snippet minh họa về type pattern; để biên dịch cần các type tương ứng. Với abstract parent và concrete child, một khai báo chỉ trỏ vào **abstract aspect cha** mà không bao gồm các concrete child có thể không tạo hiệu lực như mong muốn. Đọc chính xác type patterns và kiểm tra concrete aspects được match.

Đừng dùng pattern quá rộng chỉ vì dễ cấu hình. Một khai báo ảnh hưởng tới mọi aspect trong nhiều module có thể làm thay đổi phạm vi transaction, audit và retry ngoài dự kiến.

## 6. Thứ tự khi không khai báo rõ

Các quy tắc cơ bản của AspectJ:

1. **Giữa hai aspect khác nhau:** nếu có declare precedence, dùng thứ tự đã công bố.
2. **Quan hệ kế thừa:** một concrete subaspect có precedence cao hơn superaspect theo quy tắc kế thừa, trừ khi thứ tự được thiết lập khác bằng khai báo phù hợp.
3. **Hai aspect độc lập, không có precedence:** thứ tự giữa các advice của chúng không được xác định đáng tin để xây business logic.
4. **Nhiều advice trong cùng một aspect:** quy tắc thứ tự nội bộ khác với thứ tự giữa các aspect; với trường hợp có after advice, định nghĩa thứ tự có điều kiện theo vị trí khai báo.
5. **Chồng lấn nhiều quy tắc:** phải tránh quan hệ tạo chu trình hoặc mâu thuẫn dẫn đến lỗi biên dịch.

Một hệ thống phụ thuộc vào “lần chạy thử này LoggingAspect luôn thấy exception trước TransactionAspect” nhưng không có contract thứ tự tương ứng là hệ thống khó bảo trì.

## 7. Chọn precedence theo ý nghĩa nghiệp vụ

Xem ba concern:

- **Authorization:** có thể từ chối trước side effect.
- **Transaction:** quản lý điểm bắt đầu, commit/rollback.
- **Observability:** cần đo một operation ngay cả khi bị từ chối.

Hai lựa chọn có thể có:

~~~text
Observation -> Authorization -> Transaction -> Business
~~~

Nếu Observation nằm ngoài Authorization, nó có thể ghi kết quả DENY và duration của cả operation. Nếu chỉ muốn đo riêng phần nghiệp vụ đã được phép, điểm quan sát có thể nằm trong Authorization; khi đó denial có thể không được đo ở cùng timer.

Không có một thứ tự tốt nhất cho mọi ứng dụng. Hãy viết rõ bảng kỳ vọng:

| Tình huống | Thứ tự cần quan tâm | Câu hỏi cần trả lời |
| --- | --- | --- |
| Caller thiếu quyền | Authorization so với transaction | Có phát sinh side effect/transaction không? |
| Một attempt lỗi rồi retry | Retry so với timer | Timer đo toàn operation hay từng attempt? |
| Method ném lỗi | Audit/log so với transaction | Log ghi lỗi trước hay sau rollback? |
| Task trả CompletionStage | Advice so với completion | Method return hay task done là mốc kết thúc? |
| Operation kết thúc bị deny | Observability so với authorization | Denial có thuộc số operation được thống kê? |

Thứ tự advice là một phần của **hợp đồng quan sát và điều khiển**. Không nên thay một dòng precedence mà không kiểm thử lại behavior.

## 8. Abstract Aspect: mẫu chung và điểm cắt tùy biến

Khi nhiều module cần cùng cơ chế logging hoặc auditing nhưng có **tập join point khác nhau**, abstract aspect cho phép định nghĩa một template:

~~~aspectj
public abstract aspect OperationTemplate {
    protected abstract pointcut selectedOperation();

    before(): selectedOperation() {
        System.out.println("COMMON before");
    }
}
~~~

Ở đây:

- **abstract aspect** cho phép chứa **abstract pointcut** chưa có biểu thức.
- Advice trước join point dùng pointcut trừu tượng, nên chính sách log có thể được viết một lần.
- Một concrete subaspect phải triển khai pointcut để quyết định những join point được áp dụng.
- Abstract aspect không tự được dùng như một concrete aspect phát advice độc lập.
- Nên đặt tên pointcut theo **ý nghĩa** như businessOperation, protectedOperation hoặc dependencyCall; tránh tên helper mơ hồ.

Một concrete aspect:

~~~aspectj
public aspect OrderTemplate extends OperationTemplate {
    protected pointcut selectedOperation():
        execution(void OrderService.submit());
}
~~~

Những đoạn trên là **phần của một project có lớp OrderService**, được hoàn thiện ở ví dụ runnable. Ý nghĩa là concrete aspect chọn một operation còn parent sở hữu hành vi chung.

## 9. Hạn chế kế thừa của AspectJ cần nhớ

Khả năng kế thừa aspect giống mô hình hướng đối tượng nhưng có quy tắc riêng:

- Một **aspect có thể extends một class** phù hợp hoặc **implements interface**; điều đó không biến aspect thành đối tượng phải tạo bằng new.
- Một **aspect chỉ được extends aspect trừu tượng**; không định nghĩa một concrete aspect extends một concrete aspect khác.
- **Class thông thường không được extends một aspect.**
- **Abstract pointcut chỉ có thể được khai báo trong abstract aspect**; concrete child có thể định nghĩa pointcut đó.
- Pointcut có quy tắc thành viên và kế thừa; không thể overload hai pointcut chỉ bằng cách đổi danh sách tham số dưới cùng một tên theo cách method Java thông thường.
- Concrete subaspect có thể kế thừa field, method, pointcut và advice của abstract parent.
- Cơ chế tạo instance của concrete aspect **kế thừa từ parent aspect** khi có parent. Cần kiểm tra quan hệ giữa per-clause của parent và class con thay vì xem mỗi child là một cấu hình vòng đời độc lập.
- Advice được áp dụng theo concrete aspect thực sự được tạo; vì vậy khai báo precedence phải suy xét **concrete aspects** chứ không chỉ dựa vào tên abstract template.

Không tạo aspect instance bằng **new SomeAspect()**. Với những model hỗ trợ truy cập instance, AspectJ cung cấp những hình thức **aspectOf(...)** và **hasAspect(...)** tương ứng; cách dùng phụ thuộc loại per-clause.

## 10. Ví dụ hoàn chỉnh: ưu tiên + abstract aspect + advice lồng nhau

Đây là project AspectJ nhỏ gồm một lớp Java và bốn tệp .aj. Mục tiêu là thấy rõ:

1. Abstract aspect định nghĩa lời khuyên chung.
2. Concrete aspect chọn một method thực sự.
3. Trace aspect cùng chặn join point đó.
4. declare precedence đặt Trace ở vòng ngoài.
5. Mã nghiệp vụ không cần biết hai aspect đang tồn tại.

**Tệp Demo.java**

~~~java
public class Demo {
    public static void main(String[] args) {
        new OrderService().submit();
    }
}

class OrderService {
    void submit() {
        System.out.println("BUSINESS submit");
    }
}
~~~

**Tệp OperationTemplate.aj**

~~~aspectj
public abstract aspect OperationTemplate {
    protected abstract pointcut operation();

    before(): operation() {
        System.out.println("AUDIT before");
    }
}
~~~

**Tệp OrderAudit.aj**

~~~aspectj
public aspect OrderAudit extends OperationTemplate {
    protected pointcut operation():
        execution(void OrderService.submit());
}
~~~

**Tệp TraceAspect.aj**

~~~aspectj
public aspect TraceAspect {
    void around(): execution(void OrderService.submit()) {
        System.out.println("TRACE enter");
        try {
            proceed();
        } finally {
            System.out.println("TRACE leave");
        }
    }
}
~~~

**Tệp Ordering.aj**

~~~aspectj
public aspect Ordering {
    declare precedence: TraceAspect, OrderAudit;
}
~~~

### Biên dịch và chạy

Yêu cầu JDK 17, phiên bản AspectJ/ajc hỗ trợ Java 17, trình biên dịch ajc nằm trong PATH và thư viện runtime **aspectjrt.jar** tương thích. Đặt biến môi trường ASPECTJRT trỏ tới tệp aspectjrt.jar thực tế trên máy; đường dẫn đó không được đoán.

Lệnh biên dịch chung:

~~~bash
ajc -17 -classpath "$ASPECTJRT" -d out Demo.java OperationTemplate.aj OrderAudit.aj TraceAspect.aj Ordering.aj
~~~

Dòng trên dùng cách nội suy biến của shell kiểu Unix. Với PowerShell, dùng lệnh tương đương:

~~~powershell
ajc -17 -classpath "$env:ASPECTJRT" -d out Demo.java OperationTemplate.aj OrderAudit.aj TraceAspect.aj Ordering.aj
java -cp "out;$env:ASPECTJRT" Demo
~~~

Với bash hoặc shell có dấu hai chấm phân tách classpath:

~~~bash
java -cp "out:$ASPECTJRT" Demo
~~~

Không chạy javac cho các tệp .aj vì chúng chứa cú pháp AspectJ. ajc phải nhận **cả mã Java lẫn các tệp aspect** để thực hiện biên dịch và weaving cho join point được chọn.

**Stdout của lần chạy này**:

~~~text
TRACE enter
AUDIT before
BUSINESS submit
TRACE leave
~~~

### Giải thích thứ tự chính xác

- Method execution của OrderService.submit khớp cả pointcut operation của OrderAudit lẫn around advice của TraceAspect.
- TraceAspect có precedence cao hơn OrderAudit nên around advice của TraceAspect bắt đầu trước.
- Lời gọi proceed() của TraceAspect cho phép những advice có precedence thấp hơn tiếp tục; advice before kế thừa từ OperationTemplate qua OrderAudit chạy và in AUDIT before.
- Method gốc chạy và in BUSINESS submit.
- Luồng quay lại around advice; finally in TRACE leave dù method gốc ném exception.
- OperationTemplate là abstract template; **không có một lượt AUDIT thứ hai** do parent tự tồn tại như một concrete aspect tách biệt.
- Ordering không có advice nhưng vẫn có ý nghĩa: khai báo thứ tự tại join point.

Khi đổi declare precedence thành OrderAudit, TraceAspect, điều có thể quan sát là **AUDIT before chạy trước TRACE enter**. Nếu chính sách yêu cầu audit mọi lần thử trước khi cho phép trace/body chạy, vị trí này có ý nghĩa. Với những advice after/around khác, cần xem cả đường unwind, không chỉ so dòng log đầu.

**Giới hạn của demo:** không mô phỏng dependency ngoài, transaction hay nhiều thread. Số dòng là cố định vì chỉ một method và hai advice khớp, không có ngoại lệ. Khi mở rộng, cần tránh pointcut bắt chính phương thức trong aspect, gây đo trùng hoặc recursion.

## 11. Aspect instance: điểm khác biệt lớn so với object thông thường

Một **aspect instance** là đối tượng giữ field và thực thi advice. AspectJ có quy tắc tạo nó riêng; lập trình viên không tự gọi new như đối với service thông thường.

Vấn đề thiết kế quan trọng nhất là:

> State trong aspect có phạm vi **cùng với mô hình instantiation**, không tự động có phạm vi theo request, thread hoặc transaction mà ta mong muốn.

Ví dụ một counter trong singleton aspect sẽ được chia sẻ bởi nhiều lời gọi và nhiều thread trong phạm vi classloader tương ứng. Một counter trong pertarget aspect có thể gắn với từng target object. Một counter trong percflow aspect có thể gắn với một dynamic control flow.

Chọn sai phạm vi gây hai nhóm lỗi trái ngược:

- **Quá rộng:** dữ liệu người dùng/request bị chia sẻ nhầm, race condition, rò state.
- **Quá hẹp hoặc sai vòng đời:** mất dữ liệu cần chia sẻ, tạo quá nhiều đối tượng, metric bị phân mảnh hoặc context biến mất khi task chuyển thread.

## 12. Bản đồ mô hình tạo Aspect và phạm vi trạng thái

**Precedence trả lời advice nào bọc advice nào; instantiation trả lời instance nào sở hữu state.** Đây là hai quyết định khác nhau. Sau khi xác định thứ tự và thiết kế một abstract template, cần chọn phạm vi instance dựa trên ai sở hữu dữ liệu:

| Mô hình AspectJ | Instance thuộc về | Rủi ro cần nhớ |
| --- | --- | --- |
| `issingleton()` (mặc định) | Một instance aspect trong phạm vi bản aspect được nạp | Field mutable có thể bị nhiều thread cùng truy cập |
| `perthis(P)` | Mỗi object là `this` tại join point kích hoạt `P` | `this` ở method call không nhất thiết là receiver |
| `pertarget(P)` | Mỗi object là `target` tại join point kích hoạt `P` | Một target có thể phục vụ rất nhiều request |
| `percflow(P)` | Mỗi lần đi vào dynamic control flow từ `P` | Không tự truyền ngữ cảnh sang executor/thread khác |
| `percflowbelow(P)` | Phần flow nằm dưới join point kích hoạt `P` | Không áp dụng chính điểm mở flow theo ngữ nghĩa below |
| `pertypewithin(TypePattern)` | Một instance cho mỗi type phù hợp | Không phải mỗi object hoặc mỗi request |

Các per-clause trên là **cú pháp và mô hình của AspectJ**, không phải năng lực mặc định của mọi hệ AOP. `pertypewithin` nhận type pattern, các mô hình `perthis`/`pertarget`/`percflow` nhận pointcut expression. Điều kiện tạo instance có thể khác với pointcut của advice chạy sau đó.

## 13. Singleton: giữ state chung khi thực sự cần

Với singleton aspect không trạng thái, advice thường đơn giản và dễ dự đoán. Nhưng một field `currentUser`, `lastRequest` hoặc `startTime` trên singleton sẽ bị chia sẻ giữa nhiều invocation, có thể gây race và lộ ngữ cảnh. Singleton ở đây **không đồng nghĩa** một object duy nhất trên toàn bộ nhiều class loader hoặc tiến trình.

Nếu cần trạng thái riêng cho mỗi lời gọi, sử dụng dữ liệu cục bộ của advice hoặc context có lifecycle xác định; không đổi sang `perthis` chỉ vì muốn có per-request isolation.

## 14. `perthis` và `pertarget`: phân biệt đối tượng sở hữu

Ở **call join point**, `this` thường thuộc bên phát lời gọi và `target` là receiver; ở **execution join point** của instance method, hai vai trò thường cùng chỉ đối tượng đang chạy phương thức. Do đó per-object scope phụ thuộc cả **P chọn loại join point nào**.

Ví dụ rút gọn về target được gọi:

~~~java
// Chỉ định nghĩa type minh họa; chương trình chạy đầy đủ
// cho per-object lifecycle được trình bày ở bài chuyên sâu.
class Account {
    void charge() {}
}
~~~

~~~aspectj
// Snippet ý niệm độc lập: chọn một target Account cho mỗi instance.
aspect PerAccountObserver
        pertarget(execution(void Account.charge())) {
    private int hits = 0;
    before(): execution(void Account.charge()) { hits++; }
}
~~~

Hai lần gọi trên **cùng Account** sẽ dùng cùng pertarget instance sau khi pointcut kích hoạt; object Account khác có instance khác. `hits++` không tự trở thành thread-safe nếu nhiều thread dùng cùng Account. Đoạn mã chỉ minh họa mối quan hệ giữa P, target và state, không phải chương trình Java thuần chạy bằng `javac`.

## 15. `percflow` và `percflowbelow`: state theo call stack động

`percflow(P)` gắn một aspect instance với **mỗi lần bắt đầu dynamic flow** từ điểm khớp P. Với `percflowbelow(P)`, tác dụng nhắm phần ở dưới điểm mở flow. Đệ quy hoặc các flow lồng nhau có thể tạo nhiều scope; cần kiểm thử trước khi sử dụng để đếm attempt hoặc giữ trạng thái tác vụ.

Một `submit()` trả về Future **không bảo đảm** worker chạy ở thread khác còn nằm trong flow của caller. Phải truyền context theo ranh giới task nếu chính sách yêu cầu, thay vì giả định `percflow` là một context propagation API.

## 16. `pertypewithin`: scope theo type

Mẫu `pertypewithin(com.example.domain..*)` gắn aspect instance theo **mỗi type phù hợp**, không theo từng object. Nó hữu ích với thống kê/metadata per-class nhưng không thích hợp để lưu `currentUser` hoặc đếm riêng từng instance. Cần xét class loader và kiểu nào thực sự nằm trong vùng weaving trước khi diễn giải số lượng instance.

## 17. Accessor và thời điểm instance được gắn

Các dạng `aspectOf()`/`hasAspect()` phụ thuộc per-clause: singleton hoặc flow thường dùng accessor không tham số; per-object có dạng lấy object; per-type dùng `Class`. Không mặc định gọi `aspectOf` là hành động tạo aspect instance. Với per-object, instance có thể chưa được gắn nếu chưa có join point kích hoạt; với flow, accessor ngoài scope có thể không có instance phù hợp.

Giữ API cụ thể và kiểm thử theo **version AspectJ** đang dùng; không dùng truy cập instance từ ngoài thay thế một hợp đồng lifecycle rõ ràng.

## 18. Chọn mô hình từ quyền sở hữu state, không từ tên gọi

Trả lời tuần tự: **(1)** có thực sự cần state? **(2)** State gắn với caller, target, type hay một flow đồng bộ? **(3)** Có thread khác, recursion hoặc retry không? **(4)** Khi nào reset/cleanup và số instance tăng ra sao? **(5)** Advice chạy ở đâu so với điểm kích hoạt per-clause?

Ví dụ, một policy quan sát không state thường dùng singleton; counter theo target có thể dùng `pertarget`; một scope chẩn đoán chỉ tồn tại trên call stack đồng bộ có thể dùng `percflow`. Một logical request qua queue/thread pool nên dùng task context có propagation/lifetime minh bạch, không coi per-clause của AspectJ là per-request tự động.

**Ranh giới chương:** bảng trên đủ để hiểu vì sao precedence và abstract aspect có thể kết hợp với nhiều kiểu instance; phần vòng đời chuyên sâu cần đo bằng phép thử hai object, nhiều flow lồng nhau, lỗi và concurrent calls, chứ không suy từ tên mô hình.

## 19. Abstract Aspect và per-clause inheritance

Một abstract aspect có thể xác định cả policy mẫu lẫn mô hình instantiation; concrete child tiếp nhận quy tắc tạo instance theo kế thừa aspect.

Hình dung:

~~~aspectj
abstract aspect PerTargetTemplate
        pertarget(execution(void Account.charge())) {
    protected int visits = 0;

    protected abstract pointcut observedOperation();

    before(): observedOperation() {
        visits++;
    }
}

aspect AccountMonitor extends PerTargetTemplate {
    protected pointcut observedOperation():
        execution(void Account.charge());
}
~~~

Đây là snippet riêng minh họa inheritance và per-clause, không yêu cầu ghép cùng ví dụ có PerTargetCounter đã định nghĩa. Parent xác định mô hình pertarget và giữ cơ chế; child định nghĩa join point dùng advice.

Một số điều phải kiểm tra:

- Không mở rộng concrete aspect để “thừa kế” advice: parent aspect cần trừu tượng.
- Kiểu instantiation được định nghĩa/kế thừa qua quan hệ aspect; không coi child có thể tự chọn mô hình mâu thuẫn với parent mà không kiểm tra quy tắc ngôn ngữ.
- Nếu nhiều concrete children có cùng pointcut, chúng có thể tạo nhiều advice application độc lập dù dùng chung template. Tái sử dụng code **không tự deduplicate instrumentation**.
- Khi khai báo precedence giữa các gia đình aspect, chú ý concrete children và type patterns.
- Field visits thuộc instance của **concrete child**, với scope xác định bởi pertarget; không xem nó là bộ đếm toàn cục của abstract template.

## 20. Composition: ba trục độc lập nhưng tương tác

Hãy nhớ ba trục:

~~~text
Trục 1: WHERE  = pointcut / selected join points
Trục 2: ORDER  = advice precedence / nesting
Trục 3: SCOPE  = aspect instantiation / state ownership
~~~

Một aspect có pointcut đúng nhưng precedence sai có thể ghi nhận sai outcome. Một aspect có precedence đúng nhưng scope sai có thể rò state. Một aspect có scope và order đúng nhưng pointcut quá rộng có thể gây overhead hoặc chặn helper ngoài ý muốn.

Ví dụ một metric aspect singleton có thể thread-safe và chạy đúng ở operation boundary. Nếu pointcut chồng lấn với một metric aspect khác, counter vẫn bị đếm đôi. Đổi sang pertarget không giải quyết duplicate semantics; cần xác định một chủ sở hữu signal.

Ngược lại, pertarget giúp state tách theo object nhưng không tách theo request nếu một object được tái sử dụng cho hàng nghìn request. Đặt currentUser trong field pertarget của singleton service vẫn có thể làm rò identity.

## 21. Priority và error semantics

Một before advice có thể ném exception để chặn những advice có precedence thấp hơn và target execution. Một around advice có thể không gọi proceed(). Điều này hợp lý với authorization deny hoặc circuit breaker được thiết kế, nhưng phải ghi rõ contract:

- Aspect nào được phép chặn?
- Audit/log nên ghi cả denied attempts hay chỉ operation thật sự thực thi?
- Transaction có bắt đầu trước khi authorization có thể từ chối không?
- Timeout/retry có xảy ra ở ngoài hay trong transaction?
- Nếu advice cao hơn ném lỗi, advice thấp hơn có còn cơ hội quan sát không?
- Có cleanup trong finally ở advice ngoài khi một advice trong lỗi không?

Các quyết định này phải được kiểm thử với method success, failure, denied, timeout và retry. Không nên xử lý bằng cách đổi thứ tự type pattern cho đến khi stdout “trông đúng” mà thiếu hợp đồng rõ.

## 22. Priority với retry, transaction và đo thời gian

Hai thứ tự điển hình:

~~~text
OperationTimer -> RetryAspect -> AttemptTimer -> Business
~~~

Khi OperationTimer bao ngoài retry, nó đo toàn bộ operation và mọi lần thử (cùng thời gian chờ nếu retry có backoff). AttemptTimer bên trong được kích hoạt mỗi attempt. Đây không phải trùng nếu metric đặt tên **operation.duration** và **attempt.duration** khác nhau.

Nếu thứ tự đổi, duration có thể mất thời gian queue/backoff hoặc tạo nhiều mẫu không còn biểu diễn cùng đơn vị. Precedence là một cấu phần của contract metric, không chỉ chi phối thứ tự dòng log.

Tương tự, transaction quanh toàn bộ retry và transaction per-attempt có mức atomicity khác nhau. Việc “transaction ngoài retry” có thể giữ lock hoặc context lâu hơn; “transaction trong retry” có thể yêu cầu idempotency và rollback đúng mỗi attempt. Không mặc định đảo precedence vô hại.

Một concrete abstract-template subclass có advice kế thừa vẫn tham gia composition như một aspect cụ thể. Nếu dùng annotation hoặc type pattern để chọn các lớp, phải xác minh nhóm concrete aspects thực tế.

## 23. Lỗi thiết kế state hay gặp

### Singleton nhưng chứa currentUser

Sai vì service có thể được gọi đồng thời; principal của người A có thể bị dùng cho lời gọi người B. Hãy dùng context theo invocation/task đã xác thực hoặc truyền tham số.

### pertarget nhưng tưởng là per-request

Một target service object thường sống dài và phục vụ nhiều request. pertarget gắn state với object, không tạo mới theo mỗi yêu cầu. Chọn lại model hoặc dùng request/workflow-scoped context thực sự.

### percflow nhưng dùng qua async

Một method tạo Future rồi trả về; flow ở thread caller kết thúc trước worker. percflow không tự truyền state giữa thread và không thay việc truyền correlation/security context tường minh.

### pertypewithin nhưng tưởng mỗi object

Một class có hàng nghìn instances vẫn có aspect state theo type chứ không theo từng instance. Nếu cần số lượt riêng từng object, xem pertarget hoặc một registry có hợp đồng phù hợp.

### State mutable không đồng bộ

Aspect instance per-object hoặc singleton có thể được gọi từ nhiều thread. Chỉ vì AspectJ tạo instance theo mô hình đã chọn không đồng nghĩa counter++ tự thread-safe.

### Aspect không cleanup tài nguyên

Nếu advice giữ ThreadLocal, lock, permit hoặc transaction, phải khôi phục/đóng đúng mốc và đúng thread. Việc method return trước công việc dài hoàn tất không cho phép tự giữ tài nguyên của lời gọi ngắn qua một task ngoài scope.

## 24. Những sai lầm về abstract aspect

1. **Đặt một abstract pointcut trong concrete aspect.** Điều này vi phạm quy tắc ngôn ngữ; khai báo abstract pointcut thuộc abstract aspect.
2. **Cho concrete aspect extends concrete aspect.** AspectJ chỉ cho kế thừa từ abstract aspect.
3. **Tưởng parent abstract tự tạo advice event độc lập.** Advice của parent có hiệu lực thông qua concrete subtype; không đếm thêm một lần do parent tự active.
4. **Quên override abstract pointcut.** Concrete child phải cung cấp định nghĩa, nếu không không thể hoàn chỉnh theo contract.
5. **Chỉ declare precedence giữa tên abstract parent.** Khi cần áp dụng tới concrete child, phải xác minh type patterns có bao phủ child (ví dụ dấu +).
6. **Mỗi child trùng pointcut và cùng emit business outcome.** Có thể tạo semantic duplicates dù mỗi child kế thừa đúng một template.
7. **Dùng abstract aspect để gom những quy tắc nghiệp vụ không liên quan.** Tái sử dụng AOP phù hợp hơn khi phần chung thực sự là một cross-cutting mechanism ổn định; business policy nên được đặt tên/kiểm thử riêng.

## 25. Kiểm thử ba nhóm hành vi

### Thứ tự advice

Kiểm thử một join point được match bởi nhiều aspect, xác nhận thứ tự before và unwind theo precedence. Cần có cả đường success và exception. Nếu một advice có quyền deny, kiểm tra method gốc có được gọi hay không.

### Abstract aspect

Kiểm tra concrete child áp dụng đúng pointcut, nhận advice kế thừa, và các method không thuộc target không bị áp dụng. Khi có hai concrete children, kiểm tra có trùng join point/metric ngoài ý muốn hay không.

### Model instantiation

| Trường hợp | Điều cần xác minh |
| --- | --- |
| Singleton với 10 lời gọi | State chung theo instance được tạo và thread-safe khi cần |
| Hai object target khác nhau | pertarget state tách biệt |
| this khác target tại call join point | perthis/pertarget gắn đúng object |
| Một flow lồng nhau | percflow tạo scope theo dynamic entry |
| Chỉ các join point bên dưới entry | percflowbelow không bao chính entry |
| Hai class cùng khớp type pattern | pertypewithin có instance riêng cho type |
| Gọi aspectOf khi instance chưa bound | hasAspect/exception handling phù hợp |
| Hai task executor cùng chạy | Không chia sẻ context do hiểu sai scope |
| Method trả CompletionStage | Invocation return không bị coi là task completion |
| Mở rộng concrete child mới | Precedence và pointcut coverage được kiểm tra |

Có thể dùng event recorder trong bộ nhớ thay vì so stdout cho toàn hệ thống; stdout trong ví dụ giúp học thứ tự, nhưng test sản phẩm nên assert semantic events và state.

## 26. Các câu hỏi tự đánh giá

**Câu 1.** A có precedence cao hơn B, cả hai dùng around và đều gọi proceed đúng một lần. Thứ tự enter/leave?

**Đáp án:** A enter → B enter → target → B leave → A leave.

**Câu 2.** Một method chỉ được match bởi B, không được match bởi A. Khai báo A trước B có ép A chạy không?

**Đáp án:** Không. Precedence không làm pointcut tự khớp.

**Câu 3.** Một abstract aspect chứa before advice và một abstract pointcut, concrete child triển khai pointcut. Có hai instance abstract và concrete độc lập cùng ghi log không?

**Đáp án:** Không. Parent abstract cung cấp phần cơ chế cho concrete child; không có một event riêng chỉ vì parent tồn tại.

**Câu 4.** Hai concrete children cùng kế thừa một logging template và pointcut của chúng cùng chọn một operation. Có thể bị log hai lần không?

**Đáp án:** Có. Kế thừa chung code không tự loại trùng join point.

**Câu 5.** Vì sao pertarget không phải per-request?

**Đáp án:** Vì state gắn với target object; target thường được tái sử dụng bởi nhiều request.

**Câu 6.** Khi method thực thi bên trong object A được gọi từ object B, perthis và pertarget có nhất thiết khác nhau không?

**Đáp án:** Không ở execution join point thông thường; this/target liên quan đến object đang thực thi. Tại call join point, this thường là phía caller còn target là callee, nên có thể khác.

**Câu 7.** Có nên dùng percflow để tự chuyển security context sang thread pool?

**Đáp án:** Không. Dynamic control flow không tự tạo propagation qua asynchronous execution.

**Câu 8.** pertypewithin dùng type pattern hay pointcut?

**Đáp án:** Type pattern.

## 27. Quy trình áp dụng vào kiến trúc

1. **Liệt kê các concern** cần AOP và xác định operation boundary rõ.
2. **Viết contract cho từng advice:** sẽ log/deny/retry/commit gì, khi nào và cho những join point nào.
3. **Phân loại order dependencies:** cái gì phải bao ngoài để quan sát/kiểm soát kết quả; cái gì nằm bên trong.
4. **Khai báo precedence tường minh** khi nhiều advice cùng join point và thứ tự có ảnh hưởng.
5. **Tách template trừu tượng** nếu nhiều module cùng cơ chế nhưng khác điểm cắt; bắt buộc concrete child khai báo phạm vi.
6. **Chọn per-clause theo chủ sở hữu state**, không chọn theo tên cho dễ nhớ.
7. **Kiểm tra call vs execution semantics** cho perthis/pertarget và pointcut matching.
8. **Kiểm tra lifecycle async, nested flow và thread safety.**
9. **Viết tests cho ba trục WHERE/ORDER/SCOPE** và những exception paths.
10. **Review khi thêm aspect mới:** tránh chồng pointcut, precedence conflict và double counting.
11. **Giữ logic nghiệp vụ cốt lõi tường minh**, không giấu quyết định giá, hạn mức hoặc chuyển trạng thái trong một template AOP chung.

## 28. Tài liệu tham khảo và lưu ý phiên bản

Các quy tắc cú pháp trong bài đối chiếu theo tài liệu chính thức của AspectJ:

- [AspectJ Programming Guide – Language Semantics](https://eclipse.dev/aspectj/doc/latest/progguide/semantics.html): advice precedence, abstract aspect, inheritance và aspect instantiation.
- [AspectJ Quick Reference](https://eclipse.dev/aspectj/doc/latest/progguide/quickreference.html): cú pháp declare, pointcut, advice và per-clause.
- [AspectJ Developer's Notebook – pertypewithin](https://eclipse.dev/aspectj/doc/released/adk15notebook/pertypewithin.html): một instance mỗi type, accessor và điều kiện ngầm.
- [AspectJ ajc compiler guide](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html): lệnh biên dịch, tùy chọn Java compliance và yêu cầu cung cấp đầy đủ nguồn khi compile/weave.

Với môi trường thực tế, kiểm tra phiên bản ajc, runtime aspectjrt và phiên bản Java tương thích. Nếu dùng incremental build hoặc weaving ở thời điểm khác, cần xác minh rằng các class chứa join point đã được instrument thực sự; việc viết một tệp aspect hợp lệ chưa chứng minh nó sẽ chạy trên mọi bytecode.

## 29. Tổng kết

- **Declare precedence** trả lời advice nào có thứ tự cao hơn ở join point chồng lấn; với around advice, thứ tự này thường tương ứng với các vòng bọc lồng nhau và thứ tự quay ra ngược lại.
- **Abstract aspect** cho phép tái sử dụng cơ chế AOP và để concrete subaspect định nghĩa pointcut cụ thể. Chỉ concrete aspect mới thực thi advice theo instance thực của nó.
- **Aspect instantiation model** xác định phạm vi state: singleton, per-object theo this/target, per-dynamic-control-flow hoặc per-type.
- **WHERE / ORDER / SCOPE** là ba quyết định độc lập cần kiểm thử cùng nhau. Đúng pointcut nhưng sai order hoặc sai scope vẫn có thể làm chương trình quan sát nhầm, đếm trùng hoặc rò state.
- **AOP giúp tách cơ chế chung**, nhưng cần giữ bất biến nghiệp vụ, identity context, async lifecycle và trách nhiệm tài nguyên trong những hợp đồng rõ ràng.

Khi nắm chắc cả thứ tự, kế thừa và vòng đời instance, ta có thể xây dựng những aspect dùng lại được, dự đoán được hành vi và an toàn hơn khi nhiều concern cùng áp dụng trên một chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-008">Mô hình singleton, perthis, pertarget và percflow trong vòng đời aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Mô hình singleton, perthis, pertarget và percflow trong vòng đời aspect (AspectJ)

## 1. Vì sao vòng đời aspect là một quyết định kiến trúc?

Trong Aspect-Oriented Programming (AOP), **pointcut** lựa chọn các *join point* mà advice có thể can thiệp, còn **advice** định nghĩa hành vi cắt ngang như metrics, tracing, audit, logging hoặc chính sách thực thi. Một câu hỏi quan trọng nữa là: **instance aspect nào thực hiện advice và sở hữu các trường dữ liệu bên trong aspect?**

AspectJ hỗ trợ các mô hình khởi tạo/liên kết (*instantiation models*):

- **Singleton (`issingleton()` mặc định):** một instance aspect dùng chung trong phạm vi một bản aspect được nạp.
- **`perthis(P)`:** một instance aspect cho mỗi đối tượng `this` đi qua join point khớp `P`.
- **`pertarget(P)`:** một instance aspect cho mỗi đối tượng `target` đi qua join point khớp `P`.
- **`percflow(P)`:** một instance aspect cho mỗi lần đi vào luồng điều khiển động bắt đầu ở join point khớp `P`.

Nếu aspect giữ một trường `count`, với singleton đó có thể là bộ đếm toàn bộ các join point phù hợp, với `perthis` là bộ đếm theo đối tượng đang gọi, với `pertarget` là bộ đếm theo đối tượng nhận lời gọi, còn với `percflow` là trạng thái cho từng lần thực hiện một flow. **Cùng một advice và cùng một field nhưng mô hình instantiation khác nhau sẽ cho cách chia sẻ trạng thái khác nhau.**

Đây là bài học AOP nói chung, dùng cú pháp code-style của AspectJ để minh họa cụ thể. Cú pháp `aspect` và `per...` trong tệp `.aj` cần công cụ biên dịch/weaving AspectJ; chúng không phải cú pháp của Java thuần.

### Những khái niệm phải tách rời

1. **Join point:** một điểm có thể can thiệp trong quá trình thực thi, ví dụ gọi hoặc thực thi phương thức.
2. **Pointcut P trong per-clause:** nơi dùng để tạo/liên kết instance aspect.
3. **Pointcut Q của advice:** nơi advice muốn chạy sau khi có instance tương ứng.
4. **Aspect instance:** object giữ field và thực thi advice; không phải object nghiệp vụ.
5. **Vòng đời:** trạng thái được chia sẻ đến khi nào, khi nào tạo mới và nơi nào có thể truy cập instance.

Việc chọn `perthis` không tự động biến mọi advice thành per-thread. Chọn `percflow` cũng không biến một yêu cầu bất đồng bộ nhiều thread thành một flow liên tục. Để sử dụng đúng, trước tiên phải hiểu `this`, `target` và loại join point.

## 2. `call`, `execution`, `this` và `target`

Xét đoạn Java sau, chỉ mang tính minh họa:

~~~java
class Client {
    void invoke(Worker receiver) {
        receiver.work();
    }
}

class Worker {
    void work() {}
}
~~~

Tại join point `call(void Worker.work())` nằm trong `Client.invoke`:

- `this`: đối tượng `Client` đang thực hiện lời gọi.
- `target`: đối tượng `Worker` nhận lời gọi.

Tại join point `execution(void Worker.work())`:

- `this`: đối tượng `Worker` đang chạy thân `work()`.
- `target`: cũng là đối tượng `Worker` ấy trong trường hợp instance method thông thường.

Vì vậy cùng một chương trình có thể dùng `perthis(call(...))` để tách state theo caller và `pertarget(call(...))` để tách state theo receiver. Nhưng tại nhiều `execution` join point, hai đối tượng trùng nhau, nên hai mô hình có thể cho kết quả phân vùng tương tự.

Điểm gọi từ ngữ cảnh static có thể không có đối tượng `this` phù hợp; pointcut `this(Client)` chỉ match khi ngữ cảnh thật có `Client`. Không dùng tên method, tên kiểu tham số hoặc package để **suy ra quyền bảo mật**. Các cấu trúc này chỉ giúp chọn join point hoặc xác định đối tượng theo hợp đồng.

### Khái niệm cflow

`cflow(P)` bao gồm các join point trong luồng điều khiển động bắt đầu từ P, kể cả điểm P. `cflowbelow(P)` chọn phần dưới P nhưng không bao gồm chính P.

~~~text
Batch.run()       <- bắt đầu P
  step()
  helper()
    step()
Batch.run() về    <- kết thúc flow P
~~~

Đó là phạm vi một chuỗi thực thi đồng bộ còn hoạt động, **không phải** một object tồn tại vĩnh viễn hoặc một request logic tự động lan sang executor/RPC.

## 3. Bảng tổng quan bốn mô hình

| Mô hình | Cú pháp code-style | Khóa liên kết | Tạo instance khi nào? | State đại diện cho |
| --- | --- | --- | --- | --- |
| Singleton | `aspect A { ... }` hoặc `aspect A issingleton() { ... }` | Một bản aspect đã nạp | Khi aspect được khởi tạo trong classloader | Toàn bản aspect |
| Per-this | `aspect A perthis(P) { ... }` | Đối tượng `this` ở join point P | Lần đầu gặp P cho đối tượng chưa liên kết | Một đối tượng đang thực thi/gọi |
| Per-target | `aspect A pertarget(P) { ... }` | Đối tượng `target` ở join point P | Lần đầu gặp P cho target chưa liên kết | Một đối tượng nhận thao tác |
| Per-control-flow | `aspect A percflow(P) { ... }` | Một lần đi vào P | Mỗi lần bắt đầu flow P | Một luồng điều khiển động |

Từ khóa **singleton** ở đây không có nghĩa là chỉ có một instance xuyên nhiều JVM, process hoặc classloader. Mỗi bản aspect được nạp bởi classloader khác nhau có thể có instance singleton riêng.

`perthis` và `pertarget` là các mô hình **per-object**, không phải một aspect cho mỗi lần gọi. Cùng một object được sử dụng nhiều lần sẽ thường dùng lại aspect instance đã liên kết. `percflow` lại có thể có nhiều instance hoạt động lồng nhau trên cùng một thread.

## 4. Singleton: một instance dùng chung

Một aspect không có per-clause là singleton mặc định. Code-style minh họa:

~~~java
// Đoạn AspectJ .aj, không chạy độc lập
aspect Calls {
    private long total;

    before(): call(void Worker.work()) {
        total++;
    }

    public long total() {
        return total;
    }
}
~~~

AspectJ cung cấp accessor `Calls.aspectOf()` để lấy instance. Field `total` được dùng chung cho các join point mà aspect nhận được.

**Phù hợp cho:** cấu hình bất biến, collector metrics dùng chung, utility advice stateless, bảng chính sách không gắn riêng với request.

**Không phù hợp nếu không có đồng bộ hóa:** biến `currentUser`, `requestId`, `transactionState` hoặc một field mutable lưu giá trị của lần gọi hiện tại. Nhiều thread có thể ghi đè nhau, gây đo sai hoặc rò context.

`total++` trong ví dụ không phải phép cộng nguyên tử: đối với cập nhật song song có thể dùng `AtomicLong`/`LongAdder` hoặc cấu trúc synchronized phù hợp. Singleton **không tự thread-safe**; muốn xem tổng toàn hệ thống nhiều instance còn cần tầng tổng hợp bên ngoài.

## 5. `perthis(P)`: một aspect theo đối tượng phía `this`

Ví dụ:

~~~java
// Đoạn AspectJ .aj, không chạy độc lập
aspect ByCaller perthis(
    call(void Worker.work()) && this(Client)
) {
    private int count;

    before(): call(void Worker.work()) && this(Client) {
        count++;
    }

    public int count() {
        return count;
    }
}
~~~

Với hai đối tượng Client `a` và `b`, khi `a` gọi `Worker.work()` lần đầu, AspectJ liên kết một instance `ByCaller` với a. Nếu a tiếp tục gọi cùng hoặc khác Worker, instance đó vẫn được sử dụng. Khi b gọi, một instance khác được liên kết với b.

Ví dụ:

~~~text
a -> Worker X
a -> Worker Y
b -> Worker X

ByCaller của a: 2
ByCaller của b: 1
~~~

Lấy instance đã liên kết bằng `ByCaller.aspectOf(a)` và kiểm tra tồn tại bằng `ByCaller.hasAspect(a)` trong code-style AspectJ. Nếu a chưa đi qua P, chưa chắc có aspect instance; `aspectOf(a)` có thể ném `NoAspectBoundException`.

**Ứng dụng:** thống kê theo caller object, state của một đối tượng chủ động thực hiện thao tác, instrumentation cho các đối tượng caller có vòng đời thực sự phù hợp.

**Cạm bẫy:** nếu a là đối tượng singleton phục vụ mọi khách hàng, `perthis` vẫn chỉ tạo state của một a, **không** phải state riêng cho từng tài khoản. Một đối tượng được chia sẻ giữa nhiều thread cũng làm instance `ByCaller` của nó được chia sẻ và có thể cần synchronization.

## 6. `pertarget(P)`: một aspect theo đối tượng đích

Ví dụ:

~~~java
// Đoạn AspectJ .aj, không chạy độc lập
aspect ByReceiver pertarget(
    call(void Worker.work()) && target(Worker)
) {
    private int count;

    before(): call(void Worker.work()) && target(Worker) {
        count++;
    }

    public int count() {
        return count;
    }
}
~~~

Hai client a và b cùng gọi Worker X, thì instance `ByReceiver` gắn với X ghi cả hai lượt gọi. Nếu b gọi Worker Y, Y có instance aspect khác.

~~~text
a -> X
a -> X
b -> X
b -> Y

ByReceiver của X: 3
ByReceiver của Y: 1
~~~

Truy cập bằng `ByReceiver.aspectOf(X)` sau lần match, hoặc kiểm tra `ByReceiver.hasAspect(X)`.

**Ứng dụng:** theo dõi/chính sách theo đối tượng tài nguyên đích, bộ đếm mỗi repository object hoặc receiver độc lập.

**Cạm bẫy:** per-target là **theo object Java**, không nhất thiết theo tài nguyên logic ở bên ngoài. Hai Worker object có thể cùng tham chiếu một connection pool hoặc một tài khoản remote; việc tạo quota riêng cho từng Worker sẽ không giới hạn tổng tải tới pool/tài khoản đó. Ngược lại, nhiều thread dùng chung một Worker làm state aspect của Worker bị chia sẻ, không tự có mutual exclusion.

### Khi perthis và pertarget giống nhau

Với `execution(void Worker.work())` của instance method, `this` và `target` thường cùng là Worker. Nếu cả hai per-clause chọn execution ấy, cả hai có thể liên kết theo chính Worker. Để chứng minh **sự khác biệt**, ví dụ chạy đầy đủ ở phần sau dùng `call` trong `Client.invoke`, nơi caller và receiver khác nhau.

## 7. `percflow(P)`: một aspect cho mỗi lần vào dynamic control flow

Ví dụ:

~~~java
// Đoạn AspectJ .aj, không chạy độc lập
aspect PerRun percflow(
    execution(void Batch.run(boolean))
) {
    private int steps;

    before(): execution(void Batch.step()) {
        steps++;
    }

    public int steps() {
        return steps;
    }
}
~~~

Mỗi lần vào `Batch.run(boolean)` tạo một **instance PerRun mới**, độc lập với lần vào khác. Khi `Batch.step()` chạy trong flow ấy, advice cập nhật state của instance hiện hành. Tại một vị trí nằm trong flow có thể gọi `PerRun.aspectOf().steps()`.

Khi flow kết thúc, `PerRun.aspectOf()` không còn có instance hiện hành cho flow đó. Gọi ngoài phạm vi có thể ném `NoAspectBoundException`; dùng `PerRun.hasAspect()` khi cần kiểm tra.

### Gọi lồng và đệ quy

~~~text
run(true)         <- tạo F1
  step()          <- F1 đếm 1
  run(false)      <- tạo F2 lồng F1
    step()        <- F2 đếm 1
    step()        <- F2 đếm 2
  return          <- F2 hết hiệu lực; F1 hiện hành
  step()          <- F1 đếm 2
return            <- F1 hết hiệu lực
~~~

Cả F1 và F2 có thể tồn tại trên cùng thread trong cùng khoảng thời gian, nhưng là **hai instance theo hai lần vào P**. Đây là điểm phân biệt với “một aspect cho mỗi thread”.

AspectJ còn có `percflowbelow(P)`: mô hình flow bắt đầu phía dưới điểm P, tương ứng trường hợp muốn loại trừ chính join point entry. Phải phân biệt per-clause dùng để tạo instance với pointcut advice dùng để chọn vị trí chạy.

### Đồng bộ và bất đồng bộ

`percflow` dựa vào **control flow động của quá trình thực thi**. Khi method trả `CompletionStage` hoặc publisher rồi công việc chạy trong thread khác, flow đồng bộ hiện tại có thể đã kết thúc. Aspect instance theo `percflow` không tự lan qua executor, asynchronous callback, message queue hoặc tiến trình khác.

Nếu mục tiêu là trạng thái theo **logical request end-to-end**, hãy thiết kế propagation context độc lập, cleanup và cancellation rõ ràng. Đừng dùng percflow như bằng chứng quyền truy cập hay như một distributed transaction context.

## 8. Ví dụ hoàn chỉnh chạy với AspectJ code-style

Chương trình có hai client, hai worker và hai lời gọi Batch lồng nhau. Kết quả sẽ cho thấy rõ state của từng mô hình. Ví dụ gồm **hai tệp**: Java thường và AspectJ `.aj`, dùng Java 17 cùng công cụ `ajc` tương thích.

### 8.1 Tệp `AspectLifecycleDemo.java`

~~~java
public class AspectLifecycleDemo {
    public static void main(String[] args) {
        Worker shared = new Worker();
        Worker other = new Worker();
        Client a = new Client();
        Client b = new Client();

        a.invoke(shared);
        a.invoke(shared);
        b.invoke(shared);
        b.invoke(other);

        System.out.println("singleton="
            + SingletonMeter.aspectOf().count());
        System.out.println("perthis.a="
            + PerCallerMeter.aspectOf(a).count());
        System.out.println("perthis.b="
            + PerCallerMeter.aspectOf(b).count());
        System.out.println("pertarget.shared="
            + PerTargetMeter.aspectOf(shared).count());
        System.out.println("pertarget.other="
            + PerTargetMeter.aspectOf(other).count());

        new Batch().run(true);
    }
}

class Client {
    void invoke(Worker receiver) {
        receiver.work();
    }
}

class Worker {
    void work() {}
}

class Batch {
    void run(boolean nested) {
        step();
        if (nested) {
            run(false);
        }
        step();

        System.out.println((nested ? "outer=" : "inner=")
            + PerFlowMeter.aspectOf().steps());
    }

    void step() {}
}
~~~

### 8.2 Tệp `LifecycleAspects.aj`

~~~java
aspect SingletonMeter {
    private int calls;

    before(): call(void Worker.work()) && this(Client) {
        calls++;
    }

    public int count() {
        return calls;
    }
}

aspect PerCallerMeter perthis(
    call(void Worker.work()) && this(Client)
) {
    private int calls;

    before(): call(void Worker.work()) && this(Client) {
        calls++;
    }

    public int count() {
        return calls;
    }
}

aspect PerTargetMeter pertarget(
    call(void Worker.work()) && target(Worker)
) {
    private int calls;

    before(): call(void Worker.work()) && target(Worker) {
        calls++;
    }

    public int count() {
        return calls;
    }
}

aspect PerFlowMeter percflow(
    execution(void Batch.run(boolean))
) {
    private int steps;

    before(): execution(void Batch.step()) {
        steps++;
    }

    public int steps() {
        return steps;
    }
}
~~~

### 8.3 Cách build và chạy

Cần JDK 17 và bản AspectJ compiler hỗ trợ Java 17. Hai tệp nằm trong cùng thư mục; môi trường cung cấp lệnh `ajc` và file `aspectjrt.jar`. Ví dụ với shell Unix-like:

~~~bash
mkdir -p out
ajc -17 -d out AspectLifecycleDemo.java LifecycleAspects.aj
java -cp "out:aspectjrt.jar" AspectLifecycleDemo
~~~

Nếu `ajc` chưa nhận runtime từ cấu hình classpath, bổ sung `-classpath aspectjrt.jar` cho lệnh biên dịch; file `aspectjrt.jar` phải nằm tại đường dẫn tương ứng. Windows sử dụng dấu `;` làm dấu phân tách classpath, ví dụ `out;aspectjrt.jar`, và cách tạo thư mục phụ thuộc shell.

Không thay lệnh `ajc` bằng `javac`: Java compiler không hiểu cú pháp `aspect`, và riêng tệp Java cần được build cùng các aspect để nhận các accessor `aspectOf` do AspectJ tạo ra.

**Stdout dự kiến trong lần chạy bình thường:**

~~~text
singleton=4
perthis.a=2
perthis.b=2
pertarget.shared=3
pertarget.other=1
inner=2
outer=2
~~~

**Giải thích:**

1. `a` gọi shared hai lần; `b` gọi shared một lần và other một lần, tổng cộng bốn lời gọi tới `Worker.work()` trong `Client.invoke`. Singleton nhận cả bốn.
2. `perthis` gắn với hai object a và b, mỗi instance đếm hai lượt.
3. `pertarget` gắn với Worker shared và other, lần lượt ba và một lượt.
4. `Batch.run(true)` tạo flow ngoài, gọi `step()` hai lần trong flow của nó. Nó còn gọi lồng `run(false)`; flow trong tạo instance khác và cũng gọi `step()` hai lần. Do print trong flow trong chạy trước khi trở về flow ngoài, các dòng cuối là `inner=2` và `outer=2`.
5. Advice chỉ tăng counter, không in, nên stdout không phụ thuộc thứ tự tương đối giữa các advice tại cùng Worker call join point.

Ví dụ được thiết kế tuần tự một thread và không có exception/callback bất đồng bộ, vì vậy giá trị đếm có thể suy ra một cách xác định. Trong chương trình thật, nhiều thread hoặc pointcut rộng có thể thay đổi số instance, thứ tự, khả năng tranh chấp và lượng trạng thái tồn tại.

## 9. Truy cập instance: `aspectOf` và `hasAspect`

AspectJ code-style cung cấp các accessor sau:

| Mô hình | Lấy instance | Kiểm tra đã có instance |
| --- | --- | --- |
| Singleton | `A.aspectOf()` | `A.hasAspect()` |
| Per-this | `A.aspectOf(caller)` | `A.hasAspect(caller)` |
| Per-target | `A.aspectOf(receiver)` | `A.hasAspect(receiver)` |
| Per-cflow | `A.aspectOf()` trong flow | `A.hasAspect()` trong flow |

**Điểm cần nhớ:** per-object aspect có thể chưa tồn tại nếu đối tượng chưa bao giờ đi qua `P`. Per-cflow aspect có thể không tồn tại ngoài flow P hiện hành. `aspectOf` không tự động tạo một association hợp lệ cho bất kỳ đối tượng/ngữ cảnh tùy ý nào; dùng sai ngữ cảnh có thể nhận `org.aspectj.lang.NoAspectBoundException`.

Aspect trong code-style được quản lý bởi cơ chế AspectJ, không được khởi tạo bằng `new` để cố “gắn” vào target. Với annotation-style aspect biên dịch bằng Java compiler thường, các accessor tạo ở thời điểm compile và tiện ích truy cập có khác biệt; ví dụ ở đây dùng nhất quán code-style `.aj`.

## 10. Per-clause P khác advice pointcut Q

Ví dụ cấu trúc:

~~~java
// Cú pháp AspectJ minh họa, không chạy độc lập
aspect Example pertarget(P) {
    before(): Q {
        // advice tại Q trong ngữ cảnh instance đã liên kết
    }
}
~~~

`P` trong `pertarget(P)` quyết định **đối tượng nào được liên kết với aspect và khi nào association được tạo**. `Q` của `before()` quyết định những join point mà advice muốn chạy. Không thể mặc định advice chạy ở mọi Q nếu chưa có aspect instance thích hợp; cũng không thể coi `perthis(P)` tự động biến Q thành P.

Ví dụ một aspect tạo instance khi method `initialize()` chạy và advice quan sát `process()`. Nếu đối tượng chưa qua `initialize()` mà đã `process()`, advice có thể không chạy do chưa có instance gắn với nó. Điều này có thể là thiết kế mong muốn hoặc lỗi coverage nguy hiểm. Phải test đường gọi đúng.

Trong ví dụ hoàn chỉnh, P và Q được chọn giống nhau để người học quan sát quy tắc dễ hơn. Trong hệ thống lớn, cần mô tả cả P lẫn Q, phạm vi exclude và cách phát hiện thiếu match khi refactor.

## 11. Thời gian sống, bộ nhớ và thread safety

### 11.1 Trạng thái được chia sẻ đến đâu?

- **Singleton:** field có phạm vi bản aspect/classloader. Mọi join point áp dụng có thể chia sẻ state.
- **Perthis/pertarget:** field gắn với object được liên kết. Trạng thái còn có ý nghĩa khi object/association còn tồn tại; vòng đời thu gom phụ thuộc cơ chế runtime và tham chiếu thực tế, không nên tự hứa một thời điểm GC cụ thể.
- **Percflow:** field có ý nghĩa trong một lần flow động đang hoạt động; nested flow có instance riêng, khi ra khỏi flow thì không còn instance hiện hành.

Một aspect per-object không nhất thiết có memory overhead thấp: nếu chương trình tạo hàng triệu object khác nhau và mỗi object chạm P, có thể tạo hàng triệu instance aspect tương ứng. Một percflow trên method đệ quy có thể tạo nhiều instance lồng nhau, cần chú ý allocation và giới hạn độ sâu.

### 11.2 Per-object không tự an toàn đa luồng

Giả sử hai thread cùng gọi Worker shared. `PerTargetMeter.aspectOf(shared)` thuộc một instance chung; `calls++` có nguy cơ mất cập nhật. Tương tự, một Client object dùng chung trên nhiều thread làm perthis instance của nó trở thành shared mutable state.

Với trường hợp counter cần cập nhật nhiều thread, cân nhắc:

~~~java
// Java minh họa, không phải một aspect chạy độc lập
private final java.util.concurrent.atomic.LongAdder calls =
    new java.util.concurrent.atomic.LongAdder();

void onCall() {
    calls.increment();
}

long total() {
    return calls.sum();
}
~~~

`LongAdder.sum()` có thể chỉ là snapshot gần đúng khi có cập nhật đồng thời, nhưng phù hợp cho metrics throughput cao. Với nghiệp vụ cần accounting chính xác theo giao dịch, dùng nguồn dữ liệu/đồng bộ hóa phù hợp hơn.

### 11.3 Singleton và phân tán

Ngay cả singleton aspect cũng không phải global singleton giữa nhiều JVM/process. Nếu cần rate limit toàn cụm, bộ đếm tập trung, hay tổng hợp metrics, bạn cần thêm cơ chế phối hợp hoặc aggregation ngoài process. Chọn per-clause không tự tạo distributed consistency.

## 12. Recursion, async, lỗi và cancellation

**Recursion:** Nếu P là execution của một method đệ quy, mỗi lần đi vào P tạo một flow mới theo `percflow`. Điều này đúng khi mỗi mức cần state riêng, nhưng nếu bạn muốn một logical operation bao cả đệ quy, hãy đánh dấu entrypoint ngoài cùng hoặc truyền context khác. Ví dụ `Batch.run` minh họa nested flow chứ không giả định có một state duy nhất trên cả stack.

**Async:** `CompletableFuture`/`CompletionStage` có thể hoàn tất sau khi method đã trả về, đôi khi trên thread khác. Percflow gắn với control flow đồng bộ không tự kéo dài đến completion. Với publisher nhiều subscriber, một lần tạo publisher và các subscription là những đơn vị khác nhau. Muốn theo dõi lifecycle cần hook vào completion/subscription/terminal và truyền context rõ ràng.

**Exception:** Nếu flow kết thúc do exception, per-flow association hiện hành phải được gỡ đúng quy tắc runtime. Khi viết advice có state, luôn dùng cơ chế cleanup thích hợp; không tự nuốt exception gốc chỉ để cập nhật metric.

**Cancellation:** Hủy future không luôn có nghĩa công việc nền đã dừng. Đừng đưa kết luận quản lý tài nguyên hoặc quyền hạn dựa riêng trên việc `percflow` của method gọi đã kết thúc.

**Bảo mật:** Model gắn instance theo `this`/`target` không chứng minh principal hiện tại hoặc ownership của tài nguyên. Kiểm tra quyền phải dựa vào danh tính đã xác thực, action, resource và context thực tế, không suy quyền từ kiểu hay object đang được instrument.

## 13. Các trường hợp thường chọn sai mô hình

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Dùng singleton field `requestId` | Trộn context giữa các thread | Dùng context scope phù hợp cho từng request |
| Dùng perthis cho user khi Client là singleton | Gộp nhiều user vào một instance | Chọn khóa quyền/user theo context đã xác thực |
| Dùng pertarget làm quota toàn service khi mỗi lần tạo Worker mới | Quota bị chia nhỏ, mất giới hạn toàn service | Dùng một admission controller với phạm vi đúng |
| Dùng percflow cho callback trên executor | Không tìm thấy instance flow hiện hành | Truyền context bất đồng bộ tường minh |
| Dùng perthis trên execution và kỳ vọng phân vùng theo caller khác target | State có thể gắn với target | Chọn call join point và kiểm tra `this/target` |
| Gọi `aspectOf` trước lần match | `NoAspectBoundException` | Dùng `hasAspect` hoặc thiết kế thứ tự rõ |
| Đưa P quá rộng | Tạo instance ngoài phạm vi và tốn bộ nhớ | Rà soát pointcut P và tập match |
| Q match nhưng P chưa khởi tạo association | Advice không có instance phù hợp | Test P và Q riêng |
| `calls++` trong pertarget được nhiều thread dùng | Mất cập nhật | Atomic/adder/synchronization |
| Giả định singleton xuyên nhiều classloader | Sai mô hình state hệ thống | Ghi rõ phạm vi classloader/JVM |

## 14. Checklist kiểm thử và thiết kế

### Singleton

- [ ] Một bản aspect có một instance singleton cho toàn bộ join point trong scope.
- [ ] Các field chia sẻ có đồng bộ hóa phù hợp.
- [ ] Không giữ request/user context ở field toàn cục.

### Perthis

- [ ] Hai caller object khác nhau có hai aspect instance.
- [ ] Một caller gọi nhiều target vẫn dùng instance theo caller.
- [ ] Chọn đúng call/execution join point để `this` có nghĩa dự kiến.
- [ ] Trường hợp chưa qua P được xử lý đúng khi gọi `aspectOf/hasAspect`.

### Pertarget

- [ ] Hai target khác nhau có state riêng.
- [ ] Nhiều caller cùng một target dùng chung state theo target.
- [ ] Tài nguyên ngoài process không bị nhầm với identity object Java.
- [ ] Nhiều thread cùng target không gây race ngoài ý muốn.

### Percflow

- [ ] Mỗi lần vào P tạo state riêng.
- [ ] Nested/recursive flow có instance lồng đúng cách.
- [ ] Trả về hoặc exception kết thúc scope theo lifecycle.
- [ ] `aspectOf()` ngoài flow không bị sử dụng như instance toàn cục.
- [ ] Async callback không mặc định có context percflow.

### Toàn bộ

- [ ] Dùng `ajc` để compile/weave code-style AspectJ, runtime được cung cấp khi chạy.
- [ ] Có integration test qua join point thật, không chỉ unit test class Java.
- [ ] Pointcut P và Q được tài liệu hóa riêng.
- [ ] Đã đo chi phí allocation, contention và số instance ở workload thực.
- [ ] Mọi thay đổi tên method/package/chữ ký được kiểm tra lại độ bao phủ.

## 15. Tài liệu tham khảo chính thức

- [AspectJ Programming Guide – Aspect Instantiation](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): mô hình singleton, per-object và per-control-flow.
- [AspectJ Quick Reference](https://eclipse.dev/aspectj/doc/latest/progguide/quickreference.html): cú pháp per-clause, API `aspectOf` và các pointcut liên quan.

## 16. Kết luận

Bốn mô hình **singleton, `perthis`, `pertarget` và `percflow`** là bốn cách lựa chọn **phạm vi sở hữu trạng thái của aspect**: bản aspect được nạp, đối tượng `this`, đối tượng `target` hoặc một lần đi vào dynamic control flow. Muốn lựa chọn đúng phải hiểu join point là call hay execution, per-clause P có tạo association hay chưa, advice pointcut Q chọn gì, trạng thái có được nhiều thread chia sẻ không, và luồng thực thi có vượt qua ranh giới async không. Ví dụ AspectJ được weave thực sự và bộ test về scope/nested flow là cách xác nhận tốt hơn suy đoán từ tên mô hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-009">Khai báo liên kiểu và kiểm tra quy tắc ở bước xây dựng</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo liên kiểu và kiểm tra quy tắc ở bước xây dựng trong AspectJ

## 1. Vì sao AOP có thể thay đổi cả cấu trúc chương trình?

AOP thường được giới thiệu qua pointcut và advice: chọn một vị trí thực thi rồi chạy hành vi bổ sung trước, sau hoặc bao quanh vị trí đó. Đó là **crosscutting động** (*dynamic crosscutting*): chương trình có thêm xử lý khi chạy nhưng cấu trúc kiểu dữ liệu không nhất thiết thay đổi.

**AspectJ** còn cung cấp **crosscutting tĩnh** (*static crosscutting*), thực hiện khi biên dịch hoặc weaving. Hai nhóm khả năng nổi bật:

1. **Khai báo liên kiểu (inter-type declarations, ITD):** aspect có thể đưa method, field, constructor hoặc quan hệ kế thừa/triển khai interface vào kiểu khác. Các thành viên đó trở thành một phần của cấu trúc kiểu ở mã đã weave.
2. **Khai báo quy tắc xây dựng (declare error / declare warning):** aspect mô tả những join point mà quá trình xây dựng phải cảnh báo hoặc từ chối, ví dụ tầng giao diện không được gọi trực tiếp API lưu trữ.

Hai kỹ thuật này có mục đích khác nhau nhưng đều cho phép biểu đạt một quyết định kiến trúc tập trung. Advice chỉ chạy khi join point được thực thi; ITD và các kiểm tra declare error/warning tác động lên **kiểu hoặc quá trình build/weave**.

Ví dụ, một lớp Shipment ban đầu không khai báo interface Described. Aspect có thể bổ sung việc implement Described và một method describe(), sau đó code đã weave có thể gọi qua interface. Ngược lại, nếu quy tắc kiến trúc cấm ViewLayer gọi trực tiếp Repository.save(), declare error có thể khiến build thất bại ngay khi mã chứa lời gọi không hợp lệ, không cần chạy nhánh ấy.

**Mục tiêu bài học:** hiểu cú pháp và ngữ nghĩa của inter-type field/method/constructor, declare parents, declare error và declare warning; biết giới hạn truy cập/kiểu; tổ chức quy tắc build dễ hiểu và kiểm thử; phân biệt bảo đảm build với kiểm tra runtime.

## 2. Ôn lại các khái niệm AspectJ liên quan

- **Aspect:** đơn vị chứa pointcut, advice và các khai báo tĩnh xuyên suốt.
- **Join point:** vị trí được mô hình AOP nhận diện, như gọi hoặc thực thi phương thức.
- **Pointcut:** biểu thức chọn tập join point, dùng cho advice hoặc khai báo kiểm tra.
- **Advice:** đoạn mã chạy khi join point được chọn xảy ra.
- **Inter-type declaration:** khai báo trong aspect bổ sung thành viên hoặc cấu trúc kiểu khác.
- **Weaving:** bước hợp nhất aspect với các lớp liên quan, có thể tại compile time, sau compile hoặc load time tùy cơ chế.
- **Build-time architecture rule:** ràng buộc được kiểm tra khi biên dịch/weaving trên những join point mà compiler có thể phân tích.

Chẳng hạn:

~~~aspectj
before(): execution(* Shipment.process(..)) {
    System.out.println("before process");
}
~~~

Đây là **mảnh aspect minh họa**, giả định tồn tại kiểu Shipment có method process. Nó thể hiện dynamic crosscutting: trước mỗi execution join point phù hợp, advice được chạy.

Ngược lại:

~~~aspectj
public String Shipment.describe() {
    return "Shipment";
}
~~~

Đây là **inter-type method**, không phải advice. Nó khai báo thêm method trên Shipment ở chương trình đã weave. Muốn hoàn chỉnh cần đặt trong aspect và có lớp Shipment. Các ví dụ chạy đầy đủ ở phần sau.

## 3. Inter-type declarations thay đổi cấu trúc gì?

### 3.1. Thêm method vào kiểu khác

Cú pháp tổng quát:

~~~aspectj
[modifiers] ReturnType TargetType.methodName(Parameters) {
    // method body
}
~~~

Ví dụ:

~~~aspectj
public String Shipment.describe() {
    return "Shipment:" + id();
}
~~~

Đây là mảnh mã phụ thuộc lớp Shipment có method id(). Sau weaving, Shipment có method describe() dùng được theo quy tắc visibility. Từ khóa **this** trong thân method liên kiểu chỉ instance Shipment nhận lời gọi, **không** phải instance của aspect.

Nếu introduced method trùng chữ ký một thành viên đã có hoặc xung đột với các introduced member khác, compiler/weaver có thể báo lỗi. Không coi ITD như cơ chế tự động ghi đè bất kỳ method nào.

### 3.2. Thêm field

Cú pháp:

~~~aspectj
[modifiers] FieldType TargetType.fieldName = initialValue;
~~~

Ví dụ:

~~~aspectj
private int Shipment.readCounter = 0;
~~~

Một field instance mới được đưa vào mỗi instance Shipment đã weave. Trong AspectJ, thành viên liên kiểu private này có phạm vi truy cập theo **aspect khai báo**, tức code trong aspect được phép dùng nó. Field không trở thành biến chia sẻ toàn cục giữa mọi Shipment chỉ vì khai báo trong aspect.

Tuy nhiên, thêm state có thể ảnh hưởng bộ nhớ, thread-safety, serialization, thứ tự khởi tạo và bất biến của lớp. Với kiểu dữ liệu được chia sẻ qua nhiều luồng, một counter tăng bằng ++ không tự an toàn khi có cạnh tranh. Nên cân nhắc AtomicInteger hoặc thiết kế chuyên trách nếu cần số liệu đồng thời.

### 3.3. Thêm constructor

Cú pháp:

~~~aspectj
[modifiers] TargetType.new(Parameters) {
    // initialization
}
~~~

Ví dụ **chỉ minh họa**:

~~~aspectj
public Tag.new(String text) {
    this.text = text;
}
~~~

Đoạn này giả định Tag là class có field có thể gán text và không có constructor xung đột. AspectJ không cho khai báo constructor liên kiểu cho interface. Constructor introduced không thể dùng để gán field final được khai báo trong chính target type; do đó phải xem lại bất biến khởi tạo thay vì dùng ITD để tùy ý dựng object.

Cũng cần chú ý constructor không đối số: một class không khai báo constructor có thể đã có default no-arg constructor do Java tạo. Thêm constructor liên kiểu không đối số có thể xung đột dù file nguồn không có dòng constructor tường minh.

### 3.4. Thay quan hệ type bằng declare parents

Các dạng quan trọng:

~~~aspectj
declare parents: TargetTypePattern implements InterfaceType;
declare parents: TargetTypePattern extends SuperclassType;
~~~

**implements** dùng để làm một kiểu hoặc nhóm kiểu thực hiện thêm interface. **extends** thay đổi quan hệ superclass theo quy tắc và giới hạn hợp lệ của hệ thống kiểu Java/AspectJ; không thể tùy tiện phá bất biến kế thừa hoặc thêm nhiều superclass cho Java class.

Một ví dụ:

~~~aspectj
declare parents: Shipment implements Described;
public String Shipment.describe() {
    return "shipment:" + id();
}
~~~

Sau khi weave, Shipment thỏa hợp đồng Described. Nếu declare parents thêm interface có method trừu tượng chưa được class hoặc ITD đáp ứng, compiler/weaver có thể báo lỗi. Các giới hạn kiểu, truy cập và tương thích vẫn phải được đáp ứng; declare parents không phải thao tác ép kiểu runtime tùy ý.

## 4. Ví dụ chạy độc lập: thêm interface, field và method

Ví dụ sau dùng **code-style AspectJ**, gồm một file Java thuần và một file .aj. Java class không biết đến interface bổ sung trong mã nguồn riêng, nhưng sau compile-time weaving có thể được sử dụng như một Described.

### Tệp IntroDemo.java

~~~java
class Shipment {
    private final String code;

    Shipment(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}

interface Described {
    String describe();
}

public class IntroDemo {
    public static void main(String[] args) {
        Shipment shipment = new Shipment("S-7");
        Described described = shipment;
        System.out.println(described.describe());
        System.out.println("reads=" + shipment.readCount());
    }
}
~~~

### Tệp ShipmentIntroduction.aj

~~~aspectj
public aspect ShipmentIntroduction {
    declare parents: Shipment implements Described;

    private int Shipment.readCounter = 0;

    public String Shipment.describe() {
        this.readCounter++;
        return "Shipment:" + this.code();
    }

    public int Shipment.readCount() {
        return this.readCounter;
    }
}
~~~

### Biên dịch và chạy

Cần JDK 17+ và bộ công cụ **AspectJ ajc** có hỗ trợ nguồn Java 17. Cần thêm thư viện runtime AspectJ (aspectjrt.jar) của bộ phân phối tương thích. Đặt IntroDemo.java, ShipmentIntroduction.aj, aspectjrt.jar cùng thư mục.

Trên Linux/macOS:

~~~bash
mkdir -p out
ajc -17 -classpath aspectjrt.jar -d out IntroDemo.java ShipmentIntroduction.aj
java -cp out:aspectjrt.jar IntroDemo
~~~

Trên Windows PowerShell:

~~~powershell
mkdir out
ajc -17 -classpath aspectjrt.jar -d out IntroDemo.java ShipmentIntroduction.aj
java -cp "out;aspectjrt.jar" IntroDemo
~~~

**Kết quả chương trình dự kiến khi weaving thành công:**

~~~text
Shipment:S-7
reads=1
~~~

Giải thích:

1. File Java gốc chỉ khai báo Shipment, Described và IntroDemo, không khai báo implements hoặc describe() trên Shipment.
2. Aspect thêm interface Described vào Shipment thông qua declare parents, rồi thêm implementation describe().
3. Field readCounter được introduce riêng trên mỗi Shipment instance; mỗi lần gọi describe() tăng counter một đơn vị. readCount() trả số lượt đã gọi.
4. Vì compile-time weaving xử lý cả .java và .aj, compiler có thể type-check phép gán Described described = shipment và lời gọi shipment.readCount().
5. Nếu chỉ chạy javac trên tệp Java gốc mà không đưa aspect vào quá trình xây dựng, hai tham chiếu này không biên dịch được: Shipment chưa được Java thuần biết là Described và chưa có readCount().

**Giới hạn xác minh:** môi trường soạn nội dung hiện chưa có lệnh ajc, nên ví dụ và stdout được trình bày như kết quả dự kiến theo ngữ nghĩa AspectJ, không tuyên bố đã biên dịch/weave thực tế tại đây. Khi chạy bài thực hành, dùng ajc -version để xác nhận compiler và kiểm tra lỗi ở terminal trước khi đánh giá kết quả.

### Vì sao đây là static crosscutting?

Không có pointcut nào theo dõi thời điểm describe() được gọi để *tạo* method. Method và interface đã hiện diện trong cấu trúc type sau khi weave. Người đọc chỉ mở Shipment.java sẽ không thấy readCount() hoặc implements Described; để hiểu type thực tế phải xem cả ShipmentIntroduction.aj và cấu hình weave.

Đây vừa là lợi ích mở rộng hành vi không sửa lớp gốc, vừa là chi phí cần kiểm soát khi thiết kế module.

## 5. Giới hạn của inter-type declarations

### 5.1. Type phải thuộc tập code được weave

Việc một aspect khai báo bổ sung member cho một type không có nghĩa mọi bản triển khai/lớp đã biên dịch đang tồn tại đều tự được sửa. Compiler hoặc weaver phải thật sự nhận và xử lý bytecode/mã của target. Nếu một module khác tải bản Shipment chưa weave, nó không có hợp đồng Described được thêm theo cùng cách.

Điều này quan trọng khi các module được build riêng, đóng gói thư viện riêng hoặc có class loader khác nhau. Khi inter-type interface liên quan các implementation, interface và các class triển khai cấp đầu phải nhất quán với bản weave để tránh lỗi runtime do thiếu member.

### 5.2. Visibility không như viết source trực tiếp trong class

Member ITD có thể được khai báo public, private hoặc mức mặc định theo các quy tắc AspectJ. Với member private, phạm vi truy cập tương ứng với aspect khai báo; không nên nhầm với private member xuất hiện trực tiếp trong Java source của target. AspectJ không hỗ trợ protected inter-type member theo cùng cú pháp thông thường.

Tùy thiết kế, public introduced method sẽ xuất hiện trong API của type đã weave và có thể khiến client phụ thuộc vào aspect. Khi bỏ aspect, hợp đồng API này biến mất. Vì vậy, những API công khai cốt lõi nên được cân nhắc đặt trực tiếp trong module chủ sở hữu thay vì để một aspect tùy chọn introduce.

### 5.3. Xung đột thành viên, kế thừa và metadata

Nếu hai aspect introduce cùng public member hoặc một member trùng với target class, có thể phát sinh lỗi ambiguity/conflict. Với declare parents, Java chỉ có single inheritance cho class; quan hệ superclass/interface phải hợp lệ. Một interface mới cũng có thể thêm nghĩa vụ triển khai methods.

Thêm field có state tạo trách nhiệm khởi tạo và thread-safety. Dữ liệu serialize từ class sau weave có thể khác so với class chưa weave. Những thay đổi như vậy cần được quản lý như thay đổi kiểu/API, có kiểm thử tương thích.

### 5.4. Không dùng ITD để che giấu thay đổi domain lớn

Một introduced method tiện ích như describe() có thể giúp giảm boilerplate. Nhưng introduce các nghiệp vụ cốt lõi như calculateBalance(), transferOwnership() hoặc commitPayment() từ bên ngoài module domain có thể khiến bất biến bị phân tán. Người bảo trì khó biết logic quan trọng đang ở đâu và ai sở hữu nó.

Cần cân nhắc trước khi dùng ITD:

- Ai sở hữu contract mà member mới thực hiện?
- Việc đổi hoặc bỏ aspect có phá API của module không?
- Client có phải biết về kiểu đã weave để biên dịch không?
- Có thể dùng một adapter/decorator hoặc interface khai báo trực tiếp dễ hiểu hơn không?
- Mức độ truy cập vào state của type có tạo coupling với implementation nội bộ không?

ITD phù hợp khi thay đổi cấu trúc thật sự là một chính sách crosscutting tĩnh, tập target được kiểm soát và quá trình weaving là phần bắt buộc của build.

## 6. Kiểm tra quy tắc kiến trúc trong bước xây dựng

AspectJ cung cấp hai khai báo:

~~~aspectj
declare warning: PointcutExpression : "message";
declare error: PointcutExpression : "message";
~~~

Chúng yêu cầu compiler/weaver đưa ra cảnh báo hoặc lỗi khi phát hiện **join point có khả năng tồn tại** và khớp pointcut. Thay vì chờ đến runtime, build có thể phát hiện một đường phụ thuộc không hợp lệ trong mã.

Ví dụ chính sách: tầng giao diện chỉ được gọi tầng use case; không được gọi trực tiếp Repository.save(). Nếu một vị trí gọi từ ViewLayer tới Repository.save() xuất hiện trong code được weave, declare error sẽ chặn build.

### Điểm quan trọng về thời điểm kiểm tra

- **declare warning**: báo một vi phạm cần xem xét; chính sách build có thể quyết định cảnh báo có đủ để chặn pipeline không.
- **declare error**: báo lỗi compile/weave đối với join point bị chọn; build phải được xem là không hợp lệ.
- Pointcut kiểm tra được ở bước này phải dựa trên những yếu tố có thể quyết định tĩnh như kinded/scoping pointcuts: call(...), execution(...), within(...), withincode(...).
- Không thể yêu cầu declare error/warning ra quyết định dựa vào các yếu tố hoàn toàn runtime như cflow, cflowbelow, this, target, args, if. Những điều kiện này chỉ biết khi chương trình chạy nên không phù hợp để làm căn cứ compile-time check.
- Kiểm tra này chỉ bao phủ các join point **có mặt trong mã mà compiler/weaver nhìn thấy** và chọn được bằng pointcut. Nó không tự kiểm tra mọi module ngoài scope weaving hoặc lời gọi động/reflection tùy ý.

Điều này là **ràng buộc cấu trúc** chứ không phải thay thế kiểm thử nghiệp vụ, kiểm soát truy cập runtime hay phân tích bảo mật toàn diện.

## 7. Ví dụ quy tắc declare error: không đi tắt tầng kiến trúc

Giả định hệ thống có ba kiểu: Repository, UseCase và ViewLayer. ViewLayer chỉ gọi UseCase; UseCase mới gọi Repository.

### Tệp BoundaryDemo.java

~~~java
class Repository {
    void save(String value) {
        System.out.println("stored:" + value);
    }
}

class UseCase {
    private final Repository repository;

    UseCase(Repository repository) {
        this.repository = repository;
    }

    void execute(String value) {
        repository.save(value);
    }
}

class ViewLayer {
    private final UseCase useCase;

    ViewLayer(UseCase useCase) {
        this.useCase = useCase;
    }

    void submit(String value) {
        useCase.execute(value);
    }
}

public class BoundaryDemo {
    public static void main(String[] args) {
        Repository repository = new Repository();
        UseCase useCase = new UseCase(repository);
        ViewLayer view = new ViewLayer(useCase);
        view.submit("OK");
    }
}
~~~

### Tệp ArchitectureRules.aj

~~~aspectj
public aspect ArchitectureRules {
    declare error:
        call(* Repository.save(..)) && within(ViewLayer)
        : "ViewLayer must call UseCase, not Repository.save";

    declare warning:
        call(* Repository.save(..)) && within(BoundaryDemo)
        : "Move persistence calls into UseCase";
}
~~~

Giải thích các biểu thức:

- **call(* Repository.save(..))** chọn các vị trí gọi bất kỳ phương thức save có bất kỳ danh sách tham số phù hợp trong kiểu Repository, với return type bất kỳ.
- **within(ViewLayer)** yêu cầu vị trí gọi được khai báo trong mã của ViewLayer.
- Phép **&&** bắt buộc cả hai điều kiện cùng đúng.
- Vì mã hợp lệ chỉ gọi repository.save() từ UseCase.execute(), quy tắc cấm ở ViewLayer **không match**.
- declare warning thứ hai cho biết cách biểu diễn cảnh báo, nhưng ở bản code hợp lệ BoundaryDemo không gọi trực tiếp Repository.save(), nên nó cũng không match.

### Biên dịch bản hợp lệ

~~~bash
mkdir -p out
ajc -17 -classpath aspectjrt.jar -d out BoundaryDemo.java ArchitectureRules.aj
java -cp out:aspectjrt.jar BoundaryDemo
~~~

**Stdout chương trình dự kiến:**

~~~text
stored:OK
~~~

Để minh họa quy tắc bị vi phạm, thay riêng thân ViewLayer.submit() bằng đoạn sau rồi biên dịch lại:

~~~java
void submit(String value) {
    Repository bypass = new Repository();
    bypass.save(value);
}
~~~

Đây là **đoạn thay thế minh họa**, không phải một tệp đầy đủ. Khi compiler/weaver nhìn thấy lời gọi Repository.save() bên trong ViewLayer, điểm call khớp cả hai phần của declare error. Build phải thất bại với thông điệp được khai báo:

~~~text
ViewLayer must call UseCase, not Repository.save
~~~

**Không khẳng định toàn bộ console output đúng hệt một dòng:** định dạng thông báo lỗi, file/line, warning và mã lỗi phụ thuộc phiên bản/cấu hình ajc. Dòng trên là **phần thông điệp được khai báo**, không phải mô phỏng nguyên văn một phiên biên dịch đã chạy.

Điểm nổi bật: dù submit() chưa được thực thi trong thử nghiệm, một **lời gọi vi phạm có khả năng tồn tại trong mã được weave** đã đủ để declare error phát hiện. Kiểm tra ở đây không phải đánh giá biến value có bằng "OK" hay không; nó đánh giá vị trí phụ thuộc cấu trúc.

## 8. Viết pointcut kiến trúc chính xác và dễ duy trì

### 8.1. Chọn call thay vì execution để kiểm tra bên gọi

Nếu câu hỏi là “module A **có gọi** API của module B không?”, pointcut thường kết hợp **call(...)** với **within(...)** hoặc **withincode(...)** để xác định **vị trí bên gọi**.

~~~aspectj
declare error:
    call(* data.Repository+.save(..))
    && within(ui..*)
    : "UI must not call storage directly";
~~~

Đây là **mảnh cú pháp minh họa** với package/kiểu giả định data.Repository và ui... Dấu **+** sau type pattern bao gồm các subtype tương ứng; cần kiểm tra pattern so với chữ ký thực của call join point. Đừng sử dụng riêng execution(* Repository.save(..)) để suy ra ai đã gọi; execution chọn thân phương thức, không chỉ ra đường phụ thuộc từ UI.

Nếu muốn cấm một method trong chính module B không được thực thi, execution và within có thể phù hợp hơn. Phải xác định trước **loại sự vi phạm muốn phát hiện**.

### 8.2. Giới hạn theo module/package

Một quy tắc chọn toàn bộ project có thể bắt cả test, công cụ quản trị hoặc package được phép có ngoại lệ. Dùng within(module..*) hoặc những kiểu được chỉ định rõ để mô tả đúng phạm vi. Nếu có ngoại lệ hợp lệ, biểu diễn chúng tường minh, ví dụ loại trừ package bootstrap có trách nhiệm ghép mô-đun. Nhưng ngoại lệ quá rộng làm quy tắc mất giá trị.

Thay đổi package, di chuyển lớp hoặc chuyển từ lời gọi trực tiếp sang reflection có thể thay đổi tập match mà không đổi mục đích của code. Vì vậy mỗi rule cần có kiểm thử dương và âm: một mẫu hợp lệ được chấp nhận, một mẫu sai bị phát hiện.

### 8.3. Quy tắc không nên dựa vào trạng thái runtime

Ví dụ muốn cấm “người không có quyền ADMIN gọi save()” là **kiểm tra runtime authorization**; không thể dùng declare error để quyết định từ quyền của người dùng đang đăng nhập. Tương tự, liệu lời gọi xử lý sai dữ liệu, ghi trùng DB hay vượt hạn mức là câu hỏi runtime/domain.

Có thể dùng declare error để cấm **cấu trúc truy cập** trực tiếp đến API lưu dữ liệu, còn policy và dữ liệu nghiệp vụ vẫn phải được kiểm tra ở ranh giới thực thi phù hợp.

## 9. Những điểm thường bị bỏ sót khi dùng ITD và declare rule

### 9.1. Khả năng hiểu hành vi từ mã nguồn

Mở riêng Shipment.java không thấy interface/method mới; mở ViewLayer.java cũng chưa biết build bị chặn bởi quy tắc của ArchitectureRules. Muốn hiểu đầy đủ cần đọc thêm aspect và cấu hình build xác định tập weave.

Nên lưu một **bản đồ khai báo tĩnh** ở nơi dễ tìm:

| Target / vùng mã | Aspect áp dụng | Thay đổi hoặc quy tắc | Bằng chứng cần kiểm thử |
| --- | --- | --- | --- |
| Shipment | ShipmentIntroduction | Implements Described, describe(), readCounter, readCount() | Sau weaving có type/method và hành vi đúng |
| ViewLayer -> Repository | ArchitectureRules | declare error nếu gọi save trực tiếp | Mẫu sai không biên dịch |
| EntryPoint -> Repository | ArchitectureRules hoặc quy tắc tương tự | Có thể cảnh báo đường gọi cần xem xét | Mẫu hợp lệ và mẫu vi phạm đều được kiểm tra |
| Type hierarchy | declare parents | Thêm superinterface/superclass hợp lệ | Không có xung đột kiểu và API |

Bảng chỉ là mô hình minh họa; project thực phải ghi đúng tên type, phạm vi weaving và điều kiện kích hoạt.

### 9.2. Compile-time weaving so với load-time weaving

Các inter-type declarations và declare checks đòi hỏi compiler hoặc weaver thực sự nhìn thấy type/join point liên quan. Với **compile-time weaving**, nhiều vi phạm có thể được phát hiện trong quá trình build, phù hợp với mục tiêu chặn pull request sai kiến trúc. Với **load-time weaving**, một số thao tác phát hiện có thể xảy ra khi class được load; không được tự tuyên bố tất cả vi phạm đều đã được chứng minh ở bước build nếu chỉ weaving lúc runtime.

Nếu yêu cầu “build xanh đồng nghĩa không tồn tại lời gọi cấm”, cần bảo đảm quy tắc và toàn bộ tập class liên quan được đưa vào bước kiểm tra lúc build, không chỉ phân phối aspect jar và chờ runtime.

### 9.3. Các đường ngoài phạm vi weaving

Compiler không thể kết luận mọi thứ về mã không được weave, class được tạo động, native call, reflection hoặc dịch vụ chạy ở nơi khác. Một policy về phụ thuộc xuyên module có thể cần thêm module dependency rules, code review hoặc static analysis phù hợp. Không dùng declare error như lời bảo đảm bảo mật tuyệt đối.

### 9.4. Xung đột với Java type system

ITD tạo thêm API phải hợp lệ với Java: kiểu tham số/return, visibility, interface contract, field/method conflict, kế thừa và initialization. Compiler là công cụ phản hồi sớm, nhưng vẫn cần kiểm thử hành vi và tương thích binary. Nếu dùng JPMS, exports/opens và class loading cũng ảnh hưởng phạm vi tích hợp; không mở bừa package riêng tư chỉ để weaving thuận tiện.

### 9.5. Dùng warning có chủ đích

Warning thích hợp cho quá trình chuyển đổi một kiến trúc cũ, theo dõi API sắp loại bỏ hoặc báo đường gọi không mong muốn nhưng chưa thể sửa tất cả ngay. Error phù hợp với bất biến kiến trúc đã được thống nhất. Cần có chủ sở hữu rule, mô tả khắc phục rõ ràng, kiểm soát ngoại lệ và nâng warning thành error khi đủ điều kiện.

## 10. Thiết kế và kiểm thử các ràng buộc

Một quy trình thực tế:

1. **Xác định contract:** kiểu nào cần nhận member/interface mới? Biên giới module nào không được vượt?
2. **Viết rule theo ngôn ngữ cấu trúc:** quan hệ type cho declare parents; điểm gọi/định nghĩa cụ thể cho declare error/warning.
3. **Lắp đúng build:** compile/weave cả code cần sửa kiểu lẫn phần cần kiểm tra; xem thông báo compiler.
4. **Kiểm thử mẫu hợp lệ:** class đã weave gọi được member ITD, luồng hợp lệ không bị declare error bắt nhầm.
5. **Kiểm thử mẫu sai:** tạo một lời gọi trực tiếp bị cấm và bảo đảm bước build báo lỗi; tránh để mẫu sai nằm trong nguồn ứng dụng chính.
6. **Kiểm tra API:** introduced members có visibility/kiểu ổn định, không xung đột với code đang có.
7. **Kiểm tra runtime:** chức năng thật chạy đúng, aspect không đưa state nguy hiểm hoặc lỗi init.
8. **Tự động hóa:** đưa bước weave/rule-check vào build/CI; nếu có module mới, phải biết nó có được bao phủ không.
9. **Rà soát refactor:** đổi package, tên type hay dependency có làm pattern mất tác dụng hoặc chọn thêm type không mong muốn không?
10. **Tài liệu hóa:** ghi owner, phạm vi, thông điệp lỗi và cách sửa vi phạm.

Đối với mỗi declare error nên giữ một phép thử **negative compilation** riêng: nguồn sai phải bị compiler từ chối. Một bài kiểm thử chỉ chạy khi compile thành công không chứng minh rule sẽ chặn được vi phạm. Ngược lại, nếu compiler báo lỗi vì source Java sai cú pháp chứ không vì declare error, bài kiểm thử cũng chưa có giá trị; phải đối chiếu thông điệp mong đợi.

## 11. Phân biệt kiểm tra build với các chính sách runtime

| Nhu cầu | Công cụ/phạm vi thích hợp | Vì sao |
| --- | --- | --- |
| Bổ sung method/interface cho một type được weave | Inter-type declaration, declare parents | Thay cấu trúc type tĩnh |
| Cấm UI gọi Repository trực tiếp | declare error trên call + within | Vị trí gọi có thể nhận diện khi build |
| Cảnh báo API cũ còn được gọi | declare warning | Nhắc refactor dần |
| Kiểm tra quyền của người dùng đang đăng nhập | Runtime authorization/advice tại biên giới tin cậy | Chủ thể/quyền thay đổi ở runtime |
| Đo thời gian của một lần gọi | Advice + đồng hồ monotonic | Duration chỉ tồn tại khi thực thi |
| Quyết định đơn hàng có hợp lệ hay không | Domain logic | Phụ thuộc dữ liệu/trạng thái nghiệp vụ |
| Kiểm tra mọi module tuân thủ dependency direction | Rule AspectJ nếu các join point được bao phủ; có thể thêm dependency/static checks | Không phải mọi phụ thuộc đều là method-call join point |
| Đảm bảo code không lộ dữ liệu ở mọi nhánh | Kiểm thử bảo mật, policy và các kiểm soát khác | declare error không phân tích toàn bộ trạng thái runtime |

Không dùng ITD để thêm “thành công nghiệp vụ” vào một kiểu mà không xác định owner và contract. Không dùng declare error để suy ra rằng một người dùng cụ thể có quyền cập nhật bản ghi cụ thể. Kỹ thuật tĩnh làm tốt việc mô tả **cấu trúc**, còn yêu cầu runtime cần điểm kiểm tra runtime tương ứng.

## 12. Các lỗi hay gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Nghĩ ITD là advice chạy mỗi lần gọi | Hiểu sai khi nào method/field xuất hiện | Phân biệt thay đổi type tĩnh và chạy advice động |
| Dùng javac thuần cho chương trình có .aj | Compiler không biết cú pháp/kết quả weave | Dùng ajc hoặc bước weaving tương đương |
| Chỉ weave aspect, không weave target | Type triển khai không có member mới | Đưa target vào phạm vi weaving |
| Introduce method trùng chữ ký | Lỗi thành viên hoặc ambiguity | Kiểm tra API và các aspect khác |
| Thêm constructor không đối số trùng default | Xung đột constructor | Xem cả constructor do Java tạo ngầm |
| Gán final field của target từ introduced constructor | Không hợp lệ | Khởi tạo trong owner hoặc dùng thiết kế khác |
| Dùng private ITD như private của target source | Hiểu sai phạm vi truy cập | Đọc visibility theo aspect khai báo |
| Dùng target/args/cflow trong declare error | Không thể quyết định thuần ở build time | Chọn PCD tĩnh: call/execution/within... |
| Dùng execution để phát hiện “UI gọi DB” | Không biết nguồn lời gọi | Dùng call kết hợp within phía UI |
| Để rule chỉ chạy ở load time nhưng khẳng định build chặn | Vi phạm có thể chỉ phát hiện khi chạy | Xác định compile-time weaving/check trong CI |
| Rule match quá rộng | Vi phạm giả, build bị chặn không cần thiết | Giới hạn type/package và kiểm thử ngoại lệ |
| Không có bài thử vi phạm | Rule có thể ngừng match sau refactor | Negative compilation test |
| Introduce state không đồng bộ | Race trên field giữa nhiều luồng | Xem thread-safety của member mới |
| Thay đổi public API qua aspect tùy chọn | Client phụ thuộc vào member có thể biến mất | Xem ITD là phần bắt buộc hoặc khai báo API trực tiếp |

## 13. Bài tập tự đánh giá

**Bài 1.** Một class Report có code cũ, cần implement interface Identifiable và cung cấp method id(). Hãy nêu cách dùng declare parents và inter-type method; nếu Report đã có method id() không tương thích, điều gì có thể xảy ra?

**Bài 2.** Một field counter được introduce dưới dạng private int Report.counter = 0. Field đó thuộc mỗi object Report hay là state chung của aspect? Khi nhiều luồng tăng counter, cần xét điều gì?

**Bài 3.** Tại sao dùng declare error: call(* Repository.save(..)) && within(ViewLayer) có thể chặn vi phạm trước lúc chạy, còn điều kiện “người gọi có role ADMIN” không thể được quyết định tương tự ở build time?

**Bài 4.** Build chỉ sử dụng javac cho IntroDemo.java và không weave ShipmentIntroduction.aj. Dự đoán lỗi kiểu nào sẽ xảy ra, và vì sao?

**Bài 5.** Một thư viện thứ ba không được đưa vào compiler/weaver nhưng có đường gọi vi phạm. Build sạch có đủ chứng minh quy tắc kiến trúc toàn hệ thống đã đạt không? Nêu bước cần bổ sung.

**Định hướng trả lời:** (1) declare parents: Report implements Identifiable và method ITD tương thích hợp đồng; thành viên xung đột gây lỗi. (2) Field instance trên từng Report; ++ không tự an toàn với concurrency. (3) Quy tắc call/within là cấu trúc tĩnh, role của user là dữ liệu runtime. (4) Java thuần không biết các thành viên và quan hệ được introduce, nên các tham chiếu sẽ không compile. (5) Không; phải mở rộng phạm vi weaving/check hoặc dùng thêm công cụ phân tích phụ thuộc phù hợp.

## 14. Ghi nhớ

- **Inter-type declarations** thay đổi cấu trúc type: method, field, constructor và quan hệ kế thừa/interface.
- **declare parents** mô tả việc thêm extends/implements nhưng vẫn chịu ràng buộc của hệ thống kiểu.
- **declare error/warning** mô tả quy tắc join point tĩnh để compiler/weaver cảnh báo hoặc chặn.
- Với ranh giới **bên gọi -> bên nhận**, hãy xét **call(...) && within(...)** trước khi chọn execution(...).
- Điều kiện phụ thuộc runtime như **this, target, args, cflow, if** không phù hợp làm bộ lọc của declare error/warning.
- Tính hiệu lực phụ thuộc vào **tập class thực sự được weave** và thời điểm weaving; build rule chỉ mạnh bằng phạm vi kiểm tra của nó.
- Mọi thay đổi API qua ITD và mọi rule build cần owner, kiểm thử dương/âm và tài liệu hóa.

**Kết luận:** AspectJ có thể tổ chức không chỉ hành vi xuyên suốt lúc chạy mà còn cấu trúc type và quy tắc kiến trúc tại thời điểm xây dựng. Dùng ITD khi cần một thay đổi cấu trúc được quản lý rõ, và dùng declare error/warning để chuyển những ranh giới phụ thuộc có thể xác định tĩnh thành phản hồi compiler. Kết hợp với kiểm thử và phạm vi weaving đầy đủ, chương trình dễ bảo trì hơn và lỗi kiến trúc được phát hiện sớm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-010">Cấu hình phạm vi dệt và đọc thông báo của bộ dệt</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình phạm vi dệt và đọc thông báo của bộ dệt trong AspectJ

## 1. Tổng quan

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP), viết một `aspect` và một `pointcut` chưa bảo đảm advice có hiệu lực ở những phương thức mong muốn. AspectJ còn cần thực hiện **weaving (dệt)**: kết nối advice với các join point mà bộ máy hỗ trợ. Việc dệt có thể xảy ra khi biên dịch nguồn, xử lý bytecode đã biên dịch, hoặc khi JVM nạp class.

Đối với một lỗi như “advice không chạy”, cần phân biệt ít nhất ba điều:

- **Class đủ điều kiện dệt hay chưa?** Source, file `.class`, JAR hoặc class loader có nằm trong vùng mà weaver xem xét không?
- **Aspect và pointcut có khớp không?** Aspect có được đăng ký và pointcut chọn được join point phù hợp không?
- **Đường thực thi có chạy qua join point không?** Code đã được dệt có thể không chạy vào nhánh đó, hoặc điều kiện runtime của pointcut không đúng.

Thông báo của bộ dệt giúp trả lời hai câu đầu; kiểm thử hành vi mới trả lời được câu thứ ba. Bài học đi sâu vào cấu hình và chẩn đoán **AspectJ nói riêng, AOP tổng quát nói chung**, không phụ thuộc framework ứng dụng.

~~~text
Java / AspectJ source hoặc binary classes
                   |
                   v
           Weaver input scope
                   |
                   v
       Eligible classes / class loader
                   |
                   v
            Available aspects
                   |
                   v
         Pointcut and join points
                   |
                   v
          Advice woven into code
                   |
                   v
        Runtime execution occurs?
                   |
                   v
           Advice is executed
~~~

**Nguyên tắc trung tâm:** “có class trên classpath” khác “class được weave”; “đã weave” khác “advice đã thực thi”; “advice thực thi” khác “mọi đường gọi cần bảo vệ đều được kiểm soát”.

## 2. Các khái niệm quan trọng

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Aspect** | Đơn vị đóng gói pointcut, advice và logic xuyên suốt |
| **Join point** | Điểm có thể được chọn để can thiệp, chẳng hạn method call hoặc execution |
| **Pointcut** | Quy tắc chọn những join point cụ thể |
| **Advice** | Logic trước, sau hoặc quanh join point |
| **Weaver** | Công cụ gắn advice vào chương trình |
| **CTW** | Compile-time weaving: dệt trong quá trình biên dịch source |
| **Binary weaving** | Dệt file `.class` hoặc JAR đã biên dịch |
| **LTW** | Load-time weaving: dệt khi class được nạp/định nghĩa |
| **Weaving scope** | Tập class/type mà bộ dệt có thể xử lý |
| **Aspect selection** | Tập aspects có hiệu lực đối với bộ dệt |
| **Weave info** | Thông báo về việc dệt xảy ra tại class/join point |
| **Lint** | Chẩn đoán các tình huống có thể sai trong crosscutting |

Ví dụ pointcut/advice:

~~~aspectj
before(): execution(void example.WorkService.run()) {
    System.out.println("[advice] before");
}
~~~

Đây là đoạn cú pháp AspectJ minh họa, **không phải file `.aj` độc lập**. Nó nói advice cần đặt ở execution của `WorkService.run()` nếu join point đó được weaver hỗ trợ và class được đưa vào đúng phạm vi. Đoạn code tự nó không chỉ định binary input, class loader hoặc thời điểm dệt.

## 3. Compile-time weaving (CTW)

Trong CTW, compiler `ajc` nhận source Java và AspectJ, biên dịch và gắn advice vào bytecode đầu ra.

~~~text
.java + .aj --> ajc --> .class đã được weave --> JVM
~~~

Một ví dụ lệnh:

~~~bash
ajc -d build/ctw -showWeaveInfo -Xlint:warning \
    example/Main.java example/WorkService.java example/TraceAspect.aj
~~~

Ý nghĩa:

- `-d build/ctw`: nơi tạo output riêng.
- Các file `.java` đưa vào build là source cần compile/weave.
- `.aj` chứa aspect cần compile.
- `-showWeaveInfo` hiển thị những join point được bộ dệt tác động.
- `-Xlint:warning` báo tình huống nghi ngờ.

**Lưu ý:** `ajc` cần được cung cấp các source cần thiết; không tự tìm toàn bộ source trong dự án chỉ vì class tồn tại trong một cây thư mục khác. Nếu class nghiệp vụ chỉ được nhận diện như dependency, body của class đó không mặc nhiên được weave.

CTW phù hợp khi muốn xác định phạm vi ở **thời điểm build**, lưu log compiler trong CI và kiểm soát artifact đã dệt. Nhược điểm là phải cấu hình đúng pipeline build, đặc biệt với source chia nhiều module và JAR bên thứ ba.

## 4. Binary/post-compile weaving: ba đường dẫn rất dễ nhầm

Một lớp đã được biên dịch vẫn có thể trở thành input của bộ dệt. Khi dùng `ajc`, ba nhóm đường dẫn có mục tiêu khác nhau.

| Option | Chức năng chính | Không được hiểu là |
| --- | --- | --- |
| `-inpath <path>` | Đường dẫn file `.class`/JAR đưa vào làm **bytecode input cho weaving** | Dependency thông thường chỉ để resolve type |
| `-aspectpath <path>` | Đường dẫn **binary aspects** đã biên dịch được áp lên input | Danh sách class nghiệp vụ cần weave |
| `-classpath <path>` | Đường dẫn thư viện/class để **resolve dependency và kiểu** | Yêu cầu dệt tất cả class trên classpath |
| `-d <dir>` | Output đã biên dịch/dệt | Nguồn đầu vào nên bị ghi đè |
| `-showWeaveInfo` | Log weaving | Log method đã thực sự chạy trong request |

~~~text
compiled app .class/JAR -----> -inpath ------+
                                             |
compiled binary aspect -----> -aspectpath ---+--> ajc --> woven artifact
                                             |
other dependencies ----------> -classpath ---+
~~~

Ví dụ lệnh minh họa:

~~~bash
ajc -inpath build/original \
    -aspectpath build/aspects \
    -classpath lib/dependencies.jar \
    -d build/woven \
    -showWeaveInfo -Xlint:warning
~~~

Phải điều chỉnh đường dẫn theo artifact thực. Tạo output riêng và không vô tình đưa output trở lại `-inpath` ở build sau: dệt trùng có thể gây hành vi lặp, khó xác định phiên bản được JVM nạp.

Khi dùng aspect binary, đảm bảo version compiler/runtime tương thích và mang theo các dependency cần thiết của aspect khi chạy.

### 4.1. `call` và `execution` làm thay đổi class cần weave

~~~text
Caller.method()
    |
    +-- CALL join point --> Target.method()
                                |
                                +-- EXECUTION join point
~~~

Pointcut `call(* Target.method(..))` chọn vị trí **bên caller**; `execution(* Target.method(..))` chọn execution **bên callee**. Nếu chỉ bytecode của Caller được weave, advice dạng `call` có thể được chèn ở caller site mà không cần dệt thân Target. Với advice execution, body của Target phải thuộc phạm vi dệt theo kỹ thuật thực tế.

Vì vậy không đủ để hỏi “class Target có trên classpath không”; cần xác định **join point nằm trong bytecode class nào**.

## 5. Load-time weaving (LTW)

LTW gắn advice khi JVM tải/định nghĩa class. AspectJ có thể dùng `aspectjweaver.jar` làm Java agent và một hoặc nhiều file `META-INF/aop.xml` nhìn thấy trên đường tìm class của loader tương ứng.

~~~text
java -javaagent:aspectjweaver.jar
                |
                v
      Class loader sees aop.xml
                |
                v
       Weaver loads aspects
                |
                v
  Eligible types filtered by config
                |
                v
      Matched join points woven
~~~

Ví dụ cấu hình trong file `META-INF/aop.xml`:

~~~xml
<aspectj>
  <aspects>
    <aspect name="lab.aspects.TraceAspect"/>
  </aspects>
  <weaver options="-verbose -showWeaveInfo -debug">
    <include within="lab.app..*"/>
    <exclude within="lab.internal..*"/>
  </weaver>
</aspectj>
~~~

Đọc từng phần:

1. `<aspect name="..."/>` đăng ký một aspect có tên đầy đủ; **không** phải lệnh tự tìm mọi aspect trên classpath.
2. `<weaver>` thiết lập tùy chọn chẩn đoán và phạm vi các **type/class được phép weave**.
3. `<include within="lab.app..*"/>` chỉ cho class trong `lab.app` và package con được xét.
4. `<exclude within="lab.internal..*"/>` loại vùng class nội bộ. Với include đã hẹp như ví dụ, exclude này có tính minh họa/phòng thủ.
5. `-verbose` cho log lifecycle; `-showWeaveInfo` cho thông tin dệt; `-debug` của LTW giúp xem class được xét, bỏ qua hoặc loại trừ.

**Tệp này phục vụ LTW.** Đặt nó ở build folder không tự làm compiler CTW `ajc` thay đổi phạm vi source cần biên dịch. LTW còn phụ thuộc việc Java agent được kích hoạt đúng lúc và class loader thực sự nhìn thấy XML/aspect.

### 5.1. Hai vị trí `include/exclude` khác nhau

~~~xml
<aspectj>
  <aspects>
    <aspect name="lab.aspects.TraceAspect"/>
    <include within="lab.aspects..*"/>
  </aspects>
  <weaver options="-showWeaveInfo">
    <include within="lab.app..*"/>
    <exclude within="lab.app.generated..*"/>
  </weaver>
</aspectj>
~~~

| Nơi đặt | Đối tượng lọc |
| --- | --- |
| `<aspects><include/exclude>` | **Aspect được khai báo**, quyết định aspect nào được sử dụng |
| `<weaver><include/exclude>` | **Class/type đích**, quyết định lớp nào đủ điều kiện weaving |
| Pointcut trong mã aspect | **Join point**, quyết định vị trí advice được áp |

Một class đủ điều kiện theo `<weaver>` vẫn có thể không nhận advice nếu không có pointcut nào match. Một aspect được khai báo nhưng bị filter `<aspects>` loại cũng không tham gia weaving.

Khi có nhiều `META-INF/aop.xml` trên đường tìm class, weaver có thể tổng hợp nội dung theo quy tắc của AspectJ. Vì thế hãy đọc log `using configuration` để biết **các file XML thật sự đang được loader dùng**, không chỉ xem một file trong source tree.

### 5.2. Class loader và thời điểm

LTW diễn ra ở ranh giới class loading; một class đã được JVM nạp trước thời điểm weaver hoạt động **không mặc nhiên** bị biến đổi trở lại. Một ứng dụng có nhiều loader có thể dùng nhiều cấu hình khác nhau. Prefix class loader trong log là dấu vết quan trọng để tìm tại sao module A được weave còn module B không.

## 6. Tùy chọn để đọc thông báo của bộ dệt

| Tùy chọn | Dùng khi | Cách hiểu đúng |
| --- | --- | --- |
| `-verbose` | Cần thông tin compilation/lifecycle | Cho biết bước xử lý hoặc aspect/config được đăng ký, không nhất thiết join point đã nhận advice |
| `-showWeaveInfo` | Cần xem weaving thực tế | Log vị trí/lớp được tác động, không chứng minh method đã chạy |
| `-debug` (LTW) | Class bị bỏ qua hoặc exclude không rõ | Cho log chi tiết các class được weaver xét; không phải `javac -g` |
| `-Xlint` / `-Xlint:warning` | Kiểm tra dấu hiệu cấu hình sai | Cảnh báo khả nghi, cần phân tích theo loại |
| `-Xlint:error` | Chính sách CI nghiêm ngặt | Có thể nâng một số lint lên mức lỗi |
| `-Xlintfile <file>` | Cần tùy chỉnh nhóm lint | Properties cấu hình mức từng loại |
| `-log <file>` | Muốn lưu compiler messages | Thu bằng chứng build có thể đối chiếu |
| `ajc -version` | Chẩn đoán môi trường | Xác định compiler thực đang chạy |
| `ajc -help` | Xác nhận tùy chọn | Kiểm tra flag phù hợp với bản AspectJ |

Đối với LTW, hai system property hữu ích:

~~~text
-Daj.weaving.verbose=true
-Dorg.aspectj.weaver.showWeaveInfo=true
~~~

Chúng là **JVM options**, cần đặt lên command `java` thực tế; không tự chạy riêng trong terminal.

Một số dòng thông báo **minh họa cấu trúc, không phải output exact của bất kỳ phiên bản nào**:

~~~text
[AppClassLoader@...] info AspectJ Weaver Version ...
[AppClassLoader@...] info using configuration .../META-INF/aop.xml
[AppClassLoader@...] info register aspect lab.aspects.TraceAspect
[AppClassLoader@...] weaveinfo ... WorkService.run() ...
[AppClassLoader@...] debug ... SkipService ... excluded/ignored ...
~~~

Đọc theo thứ tự:

- **Loader**: có đúng class loader của module đang chạy?
- **Configuration**: có đúng `aop.xml` dự kiến?
- **Register aspect**: aspect mong đợi có được nhận diện?
- **Weave information**: advice có được gắn ở method đích?
- **Debug**: class bị exclude, không thể weave hay chỉ chưa đến lúc load?
- **Warning/error**: lỗi pointcut, bytecode, dependency, scope hoặc version?

Đừng viết kiểm thử phụ thuộc nguyên văn số dòng/compiler log minh họa; định dạng log, source line và thứ tự có thể đổi theo phiên bản.

## 7. `Xlint` và thông báo không match

Một cảnh báo tiêu biểu là `adviceDidNotMatch`, gợi ý advice chưa tìm được join point phù hợp trong tập class/compiler biết đến. Nguyên nhân có thể là:

- Sai chữ ký: tên method, package, kiểu return, overload, annotation hoặc `call`/`execution`.
- Source/bytecode đích không thuộc input weaving.
- Aspect được viết cho module khác chưa xuất hiện trong lần compile.
- Với LTW, class liên quan có thể chỉ được load sau này.

**Khác biệt quan trọng:** CTW có tập input được xác định tại build nên có thể cảnh báo một advice không match. LTW không biết trước toàn bộ class sẽ load về sau; xử lý `adviceDidNotMatch` có thể khác, ví dụ không nâng thành warning như khi compile-time. Không có warning **không đồng nghĩa** lời gọi đã được bảo vệ.

Tránh tắt mọi cảnh báo bằng `-Xlint:ignore` chỉ vì build ra output. Hãy xác định cảnh báo thuộc loại gì và nó có thể chỉ ra vùng dệt bị mất hay không. Khi cần CI nghiêm ngặt, cấu hình quy tắc có chủ đích thay vì biến mọi message thành lỗi mà không phân loại.

## 8. Ví dụ Java 17 và AspectJ: so sánh CTW với LTW

Bộ ví dụ độc lập gồm **ba file Java** và **một file aspect**. Ta sẽ chạy cùng một chương trình nhưng thay đổi phạm vi dệt:

- CTW: đưa cả hai service vào source input, nên hai execution có advice.
- LTW: filter chỉ cho `lab.app..*`, nên chỉ `WorkService` có advice.
- Không weaving: chỉ có thông báo từ thân hai method.

Cấu trúc:

~~~text
lab/
  app/
    Main.java
    WorkService.java
  internal/
    SkipService.java
  aspects/
    TraceAspect.aj
~~~

### 8.1. `lab/app/Main.java`

~~~java
package lab.app;

import lab.internal.SkipService;

public class Main {
    public static void main(String[] args) {
        new WorkService().run();
        new SkipService().run();
    }
}
~~~

### 8.2. `lab/app/WorkService.java`

~~~java
package lab.app;

public class WorkService {
    public void run() {
        System.out.println("[target] work");
    }
}
~~~

### 8.3. `lab/internal/SkipService.java`

~~~java
package lab.internal;

public class SkipService {
    public void run() {
        System.out.println("[target] skip");
    }
}
~~~

### 8.4. `lab/aspects/TraceAspect.aj`

~~~aspectj
package lab.aspects;

public aspect TraceAspect {
    before():
        execution(void lab.app.WorkService.run())
        || execution(void lab.internal.SkipService.run()) {

        String name = thisJoinPointStaticPart
                .getSignature()
                .getDeclaringType()
                .getSimpleName();

        System.out.println("[advice] " + name + ".run");
    }
}
~~~

Cần `ajc` để biên dịch `.aj`, không dùng `javac TraceAspect.aj`. `thisJoinPointStaticPart` lấy metadata tĩnh, dùng để xác định method được advice.

### 8.5. Compile-time weaving: kết quả có hai advice

Đặt `ASPECTJ_HOME` tới bản phân phối có JAR tương thích, dùng `ajc` phù hợp với JDK 17+.

~~~bash
mkdir -p build/ctw
ajc -d build/ctw \
    -classpath "$ASPECTJ_HOME/lib/aspectjrt.jar" \
    -showWeaveInfo -Xlint:warning \
    lab/app/Main.java \
    lab/app/WorkService.java \
    lab/internal/SkipService.java \
    lab/aspects/TraceAspect.aj

java -cp "build/ctw:$ASPECTJ_HOME/lib/aspectjrt.jar" lab.app.Main
~~~

**Stdout chương trình** (không tính log `ajc`):

~~~text
[advice] WorkService.run
[target] work
[advice] SkipService.run
[target] skip
~~~

Giải thích: cả `lab.app.WorkService` và `lab.internal.SkipService` nằm trong input `ajc`. Pointcut chọn cả hai execution `run()`, do đó advice in trước body của mỗi method. `-showWeaveInfo` sẽ giúp kiểm tra hai vị trí được dệt; không dự đoán format exact của thông báo compiler.

### 8.6. Load-time weaving: chỉ WorkService có advice

**Bước 1:** Compile Java thường, tạo **class chưa weave**:

~~~bash
mkdir -p build/ltw-classes build/ltw-aspects build/ltw-config/META-INF

javac -d build/ltw-classes \
    lab/app/Main.java \
    lab/app/WorkService.java \
    lab/internal/SkipService.java
~~~

**Bước 2:** Compile aspect riêng thành binary (dùng class Java chỉ để resolve type):

~~~bash
ajc -d build/ltw-aspects \
    -classpath "build/ltw-classes:$ASPECTJ_HOME/lib/aspectjrt.jar" \
    lab/aspects/TraceAspect.aj
~~~

**Bước 3:** Lưu nội dung sau đúng vào `build/ltw-config/META-INF/aop.xml`:

~~~xml
<aspectj>
  <aspects>
    <aspect name="lab.aspects.TraceAspect"/>
  </aspects>
  <weaver options="-verbose -showWeaveInfo -debug">
    <include within="lab.app..*"/>
    <exclude within="lab.internal..*"/>
  </weaver>
</aspectj>
~~~

**Bước 4:** Chạy bằng agent và classpath chỉ trỏ tới class Java gốc, aspect binary và config:

~~~bash
java -javaagent:"$ASPECTJ_HOME/lib/aspectjweaver.jar" \
    -cp "build/ltw-classes:build/ltw-aspects:build/ltw-config:$ASPECTJ_HOME/lib/aspectjrt.jar" \
    lab.app.Main
~~~

Bỏ qua các dòng diagnostics của LTW, **stdout chương trình** có thứ tự:

~~~text
[advice] WorkService.run
[target] work
[target] skip
~~~

Tại sao? `lab.app.WorkService` thuộc include nên có execution `run()` được weave. `lab.internal.SkipService` ở ngoài include và được exclude rõ ràng, do đó method vẫn chạy nhưng advice không xuất hiện.

Nếu thấy bốn dòng như CTW, có thể đã chạy nhầm class vốn được weave từ `build/ctw`, có config khác mở rộng scope hoặc class loader khác. Nếu không có advice nào, kiểm tra `-javaagent`, classpath, `META-INF/aop.xml`, tên aspect và logs.

**Đối chứng không weaving:**

~~~bash
java -cp build/ltw-classes lab.app.Main
~~~

Stdout:

~~~text
[target] work
[target] skip
~~~

Với **Windows**, dùng dấu `;` làm phân tách classpath thay vì dấu `:` và trỏ `-javaagent` tới đúng file `aspectjweaver.jar`; trong PowerShell có thể đặt toàn bộ lệnh `java` trên một dòng để tránh lỗi nối dòng.

### 8.7. Vì sao tách các output?

Output CTW đã chứa advice; output LTW ban đầu không chứa advice. Nếu vô tình đặt `build/ctw` lên classpath ở kịch bản LTW, thí nghiệm không còn chứng minh agent weave class nguyên bản. Một build đáng tin cần lưu rõ **input**, **output**, **version ajc**, **runtime JAR**, **config XML** và **classpath**.

## 9. Đọc log trong thực tế: bảng chẩn đoán

| Triệu chứng | Khả năng cần kiểm tra |
| --- | --- |
| Không có bất kỳ log LTW | Agent chưa bật, options chưa bật, config không thấy được |
| Có `using configuration` nhưng không thấy aspect đăng ký | Tên aspect sai, không có binary, filter `<aspects>` loại |
| Có `register aspect` nhưng không có weaveinfo lớp mong đợi | Class chưa load, exclude, pointcut sai hoặc thiếu bytecode input |
| Thấy class `ignored/excluded` trong debug | Kiểm tra type pattern, class loader và restriction |
| Có weaveinfo nhưng advice không in | Nhánh method không chạy, runtime condition false, lớp chạy bị khác |
| `adviceDidNotMatch` khi CTW | Input build thiếu class hoặc pointcut không khớp |
| Thông báo LTW khác CTW | Lượng class biết trước khác nhau; xem thời điểm load |
| Chỉ một overload có advice | Kiểm tra signature/return/args của pointcut |
| Advice chạy hai lần | Tìm double weave, nhiều aspect, call/execution cùng match |
| Không đọc thấy XML từ packaged JAR | Kiểm tra `META-INF/aop.xml` có nằm đúng trên class loader path |
| JVM chạy nhưng policy quan trọng bị bỏ qua | Test positive/negative và entry path, không chỉ nhìn log |

Đừng chỉ grep `register aspect` rồi báo PASS: đăng ký aspect **không chứng minh** đã weave join point cần thiết. Tương tự, thông báo woven class **không chứng minh** runtime đã thực thi nhánh đó.

## 10. Lọc phạm vi an toàn và hiệu năng

**Phạm vi quá rộng** có thể dệt cả các class không liên quan, tăng thời gian build/class load, tăng kích thước bytecode và overhead runtime. Aspect logging nếu vô tình match chính hạ tầng logging có thể tạo vòng lặp đệ quy. **Phạm vi quá hẹp** có thể bỏ sót những method cần audit, transaction hoặc kiểm soát quyền.

Một cách thiết kế hợp lý:

1. Xác định danh sách operation cần tác động.
2. Chọn đúng `call` hay `execution`.
3. Giới hạn lớp có thể weave bằng input, `-inpath` hoặc LTW include/exclude.
4. Viết pointcut đủ cụ thể; đừng dùng một pattern khớp cả ứng dụng chỉ vì muốn warning biến mất.
5. Bật diagnostics trong build/test; đo overhead bằng workload thực.
6. Kiểm tra **positive** (method phải có advice) và **negative** (method không được có advice).
7. Khi dùng cflow, args hoặc điều kiện động, kiểm tra thêm trạng thái runtime.

Cũng cần biết: một security aspect ở vài join point **không tự bảo vệ mọi đường trực tiếp tới resource**; transaction advice không tự cung cấp transaction manager/rollback cho external side effect. Bằng chứng weaving là một phần của QA kiến trúc, không thay thế kiểm thử an toàn nghiệp vụ.

## 11. Lớp đã được weave có thể chưa chạy advice

Có nhiều nguyên nhân:

- Method chưa được gọi trong test case đó.
- Around advice khác short-circuit và không gọi `proceed()`.
- Pointcut có `if()`, `args()` hoặc `cflow()` chỉ đúng trong một số ngữ cảnh runtime.
- Execution xảy ra ở class/loader khác với artifact vừa kiểm tra.
- Công việc thực tế chạy trên worker bất đồng bộ, method ban đầu chỉ trả `CompletionStage`/future.
- Log của advice bị tắt hoặc gửi sang sink khác.

Một chương trình trả handle bất đồng bộ không đồng nghĩa công việc đã hoàn tất. `-showWeaveInfo` không cho biết completion outcome. Nếu muốn đo hết một asynchronous operation, cần hook vào tín hiệu hoàn tất phù hợp, không chỉ vào method return.

## 12. Checklist khi cấu hình cho hệ thống thật

- [ ] Đã xác định **CTW, binary weaving hay LTW**?
- [ ] Lớp đích thật sự có trong source input/`-inpath` hoặc class loader phù hợp?
- [ ] `-aspectpath` chứa binary aspect, không bị nhầm với target classes?
- [ ] `-classpath` cung cấp đúng dependency?
- [ ] Output woven được tách khỏi input và có build sạch?
- [ ] Với LTW, `-javaagent` được bật và `aop.xml` nằm đúng `META-INF`?
- [ ] Aspect được khai báo đầy đủ và có log đăng ký?
- [ ] `<aspects>` filter đúng tập aspect, `<weaver>` filter đúng tập class?
- [ ] `include/exclude` dùng đúng type pattern cho package con?
- [ ] Đã đối chiếu `call`/`execution` với vị trí bytecode có thể weave?
- [ ] `-showWeaveInfo` cho thấy join point quan trọng nhận advice?
- [ ] `-debug` LTW cho biết lý do class bị ignore/exclude?
- [ ] Warning `Xlint` được phân loại thay vì bỏ qua?
- [ ] Runtime test chứng minh behavior đúng, gồm cả case không match?
- [ ] Không có output class cũ hoặc dependency JAR sai version được nạp?
- [ ] Có kiểm chứng riêng cho async, security, transaction và exception khi liên quan?

## 13. Các lỗi thiết kế phổ biến

| Sai lầm | Cách sửa |
| --- | --- |
| Có class trên classpath tức đã weave | Xác nhận source input hoặc `-inpath`/LTW class eligibility |
| Chỉ có aspect binary là đủ | Cần aspect được nhận diện, pointcut match và target class trong scope |
| Nhầm filter trong `<aspects>` với `<weaver>` | Phân biệt lọc aspect và lọc class |
| Chỉ xem `-verbose` | Dùng weaveinfo/debug và runtime test phù hợp |
| Tưởng `-debug` LTW là debug symbol | Phân biệt `-debug` với `-g` |
| Dùng `aop.xml` để giới hạn CTW mà không cấu hình compiler | CTW cần source/input build và pointcut |
| Đổi XML trên đĩa rồi mong class đã load tự đổi | Khởi động JVM mới để kiểm chứng phạm vi LTW |
| Bật agent nhưng thiếu XML/aspect | Kiểm tra classpath, loader và registered aspects |
| Tắt tất cả lint warning | Phân loại nguyên nhân, giữ cảnh báo có ích |
| Dệt cả class logging để chữa missing match | Giới hạn pointcut/scope để tránh đệ quy |
| Chạy test LTW với output CTW | Tách class nguyên bản và class woven |
| Dùng weaveinfo thay kiểm thử hành vi | Kiểm chứng lời gọi thực tế và hợp đồng nghiệp vụ |

## 14. Kết luận

Cấu hình AspectJ đúng là sự phối hợp giữa **input weaving**, **aspect availability**, **class/type filters**, **pointcut matching** và **runtime invocation**. Các thông báo `-verbose`, `-showWeaveInfo`, `-debug` và `Xlint` giúp chẩn đoán từng lớp, nhưng không thay thế kiểm thử hành vi. Cách làm đáng tin là quan sát cả **method phải nhận advice** và **method phải nằm ngoài scope**, sau đó kiểm tra trên đúng artifact, JVM và class loader mà hệ thống sẽ triển khai.

Nhớ ba phân biệt: **classpath khác input để weave; đăng ký aspect khác join point được advise; weave info khác advice đã thực thi**.

## 15. Nguồn tra cứu chính thức

- [AspectJ Development Environment Guide — ajc](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html)
- [AspectJ Development Environment Guide — Load-Time Weaving](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html)
- [AspectJ Problem Diagnosis Guide — Messages](https://eclipse.dev/aspectj/doc/latest/pdguide/messages.html)

Dùng `ajc -version` và `ajc -help` trên môi trường của mình để kiểm tra cờ thực tế, vì cách ghi thông báo và mức hỗ trợ có thể thay đổi giữa những phiên bản AspectJ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-j-in-practice-011">Phân biệt đặc tính riêng của AspectJ với khái niệm AOP tổng quát</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đặc tính riêng của AspectJ với khái niệm AOP tổng quát

## 1. Hai tầng kiến thức cần tách bạch

**Aspect-Oriented Programming (AOP)** là tư duy mô-đun hóa những **mối quan tâm cắt ngang** (cross-cutting concerns): một quy tắc như logging, kiểm soát truy cập, tracing, metrics hoặc kiểm tra điều kiện có thể áp dụng tại nhiều vị trí trong chương trình mà không phải lặp lại từng dòng trong mọi phương thức nghiệp vụ.

**AspectJ** là một công nghệ cụ thể trong hệ sinh thái Java để hiện thực tư duy ấy. Nó có ngôn ngữ pointcut, mô hình join point, cú pháp advice, công cụ biên dịch/weaving, các khai báo thay đổi cấu trúc kiểu và các quy tắc xác định thứ tự advice.

Hai phát biểu sau rất khác nhau:

- “AOP cho phép mô tả logic cắt ngang bằng aspect rồi áp dụng tại những join point phù hợp” — **khái niệm tổng quát**.
- “Một pointcut viết bằng execution(* Account.transfer(..)) và biên dịch cùng tệp .aj bằng ajc” — **mô tả một hiện thực AspectJ**.

Đừng lấy một cú pháp của AspectJ làm định nghĩa bắt buộc cho mọi hệ AOP. Ngược lại, cũng đừng phủ nhận khả năng có thật của AspectJ chỉ vì một mô hình AOP khác không hỗ trợ khả năng đó.

### Mục tiêu

Sau bài này, bạn sẽ:

1. Phân biệt aspect, join point, pointcut, advice và weaving ở mức trừu tượng.
2. Nhận biết các điểm cụ thể mà AspectJ hỗ trợ như method call, method execution, field get/set và cflow.
3. Hiểu vì sao execution và call có vị trí áp dụng khác nhau.
4. Phân biệt bản chất before/after/around với cú pháp và semantics của proceed trong AspectJ.
5. Hiểu compile-time, binary và load-time weaving không phải ba bước bắt buộc của mọi AOP.
6. Nhận diện inter-type declarations, declare parents, declare error/warning và declare precedence là các năng lực riêng của AspectJ.
7. Biên dịch một chương trình Java 17 và tệp AspectJ .aj, giải thích chính xác stdout.
8. Chuyển kiến thức sang một cơ chế AOP khác mà không áp đặt quy tắc của AspectJ lên nó.

## 2. Từ vựng AOP tổng quát và hiện thực AspectJ

| Thuật ngữ | Tầng khái niệm AOP | Biểu hiện trong AspectJ |
| --- | --- | --- |
| Concern | Mục tiêu/chính sách xuyên qua nhiều module | Logging, metrics, authorization được mô tả bằng aspect |
| Aspect | Đơn vị mô-đun hóa concern | Aspect code-style hoặc annotation-style được công cụ AspectJ hỗ trợ |
| Join point | Điểm chạy mà hệ AOP có thể quan sát/can thiệp | Tập điểm định nghĩa cụ thể: call, execution, get/set và những loại khác |
| Pointcut | Quy tắc lựa chọn join point | Ngôn ngữ execution(...), call(...), args(...), within(...), cflow(...) |
| Advice | Logic thực thi khi điểm phù hợp | before, after, after returning, after throwing, around |
| Continuation | Phần thực thi bên trong có thể được gọi từ around | Từ khóa proceed(...) trong code-style AspectJ |
| Weaving | Cách kết hợp aspect với chương trình | Công cụ ajc, dệt source/bytecode hoặc dệt khi load class |
| Context binding | Cách lấy tham số/kết quả/ngoại lệ/ngữ cảnh | args, this, target, thisJoinPoint, returning và throwing binding |
| Introduction | Bổ sung thành viên/quan hệ kiểu nếu mô hình cho phép | Inter-type declarations và declare parents |
| Precedence | Thứ tự các advice/aspect khi cùng khớp | Quy tắc precedence của AspectJ và declare precedence |
| Coverage | Những nơi concern thật sự chạy | Phụ thuộc pointcut, mã được woven và điều kiện runtime |

Khái niệm AOP không bắt buộc mọi thư viện hỗ trợ tất cả khả năng trong cột phải. Một thư viện chỉ chặn method call vẫn có thể áp dụng ý tưởng tách concern, dù nó không hỗ trợ get/set pointcut hay giới thiệu method mới.

## 3. Mối quan tâm cắt ngang là khái niệm, không phải cú pháp

Ví dụ một hệ nghiệp vụ Java:

~~~java
// Fragment Java minh họa, không phải chương trình độc lập.
Order placeOrder(Request request);
PaymentResult charge(Payment payment);
Inventory reserve(String sku, int quantity);
~~~

Cả ba operation đều có thể cần:

- Xác thực và phân quyền.
- Đo thời gian và ghi metric.
- Ghi trace/correlation ID.
- Logging ở điểm bắt đầu, kết thúc và lỗi.
- Giới hạn số lần retry hoặc quản lý timeout.

Nếu mã concern lặp lại trong từng method, chúng có thể làm khó bảo trì. AOP đề xuất mô tả concern ở một nơi và áp dụng theo quy tắc selection.

~~~text
                   Aspect: logging/tracing
                     |      |      |
                     v      v      v
placeOrder()       charge()     reserve()
       \              |           /
        \-------- business -------/
~~~

Tư duy này tồn tại độc lập với tệp .aj. Một giải pháp dùng decorator, interceptor, weaving hay code generation có thể cùng phục vụ việc tách concern, nhưng không mặc nhiên có tập join point hoặc semantics giống AspectJ.

**Giới hạn của AOP:** không phải logic nào cũng là concern cắt ngang. Tính giá bán, chuyển trạng thái đơn hàng hoặc duy trì bất biến của aggregate thường có vị trí tự nhiên trong domain model. Chuyển những việc đó thành advice chỉ vì công cụ làm được có thể tăng coupling ẩn và khiến luồng điều khiển khó đọc.

## 4. Join point: ý tưởng tổng quát, tập điểm do công nghệ quy định

**Join point** là một vị trí có thể nhận advice trong mô hình đang dùng. AOP tổng quát không quy định cố định “mọi join point đều là method execution” hay “mọi field write đều có thể bị chặn”.

AspectJ có mô hình join point giàu khả năng, bao gồm:

- Method execution: thực thi thân phương thức.
- Method call: điểm phát sinh lời gọi phương thức.
- Constructor call/execution và một số điểm khởi tạo.
- Field get và field set.
- Static initialization và một số join point liên quan khởi tạo đối tượng.
- Exception handler và một số điểm thực thi advice.

Những loại này thuộc **mô hình join point AspectJ**, không phải một danh sách bắt buộc cho tất cả hệ AOP.

### 4.1. Method execution khác method call

Hai pattern AspectJ:

~~~java
// Biểu thức pointcut, không phải mã Java độc lập.
execution(* AccountService.transfer(..))
call(* AccountService.transfer(..))
~~~

Execution nhắm tới **việc thực thi thân method transfer**. Call nhắm tới **vị trí phát lời gọi** đến method đó.

~~~text
CallerA ------ call site A -------+
                                 |
CallerB ------ call site B ------> AccountService.transfer()
                                 ^ method execution join point
~~~

Hệ quả quan trọng:

1. Execution advice cần phần thân method đích được weaving phù hợp.
2. Call advice cần những call site liên quan được weaving phù hợp.
3. Nếu có caller bên ngoài vùng weaving, call advice không nhất thiết quan sát caller đó, dù phần thân method đích đã được woven.
4. Một lời gọi nội bộ cùng object vẫn có thể kích hoạt execution advice được woven vào method, trong khi cơ chế chỉ intercept lời gọi qua một proxy có thể bỏ qua self-invocation.
5. Nếu gắn cả call advice và execution advice để đếm operation, có thể đếm hai lần một đường gọi nếu không định nghĩa đơn vị metric rõ.

Không thể chỉ nhìn tên method và kết luận hai pointcut tương đương. **Vị trí join point, phạm vi weaving, điều kiện động** cùng quyết định hành vi.

### 4.2. Field get/set không phổ quát

AspectJ cho phép lựa chọn field access:

~~~java
// Biểu thức pointcut AspectJ, không biên dịch độc lập bằng javac.
get(int Account.balance)
set(int Account.balance)
~~~

Một cơ chế AOP chỉ chặn method invocation không nhất thiết quan sát được field read/write. Vì vậy câu nói “AOP luôn chặn được thao tác đọc và ghi field” là sai.

Ngay cả khi AspectJ hỗ trợ get/set, một policy bảo mật không nên chỉ dựa vào giả định mọi cách truy cập dữ liệu đều đi qua field pointcut; vẫn phải kiểm tra weaving coverage, reflection và các ranh giới bên ngoài ứng dụng.

## 5. Pointcut: điều kiện chọn chung, biểu thức chọn là đặc thù

Ở tầng trừu tượng, pointcut là **một vị từ**: join point nào thỏa điều kiện sẽ nhận advice. Điều kiện có thể liên quan method signature, đối tượng, tham số, vị trí mã, annotation hoặc dynamic control flow, tùy cơ chế AOP.

AspectJ dùng một **ngôn ngữ pointcut cụ thể**. Chẳng hạn:

~~~java
// Code-style AspectJ trong tệp .aj.
pointcut moneyMovement():
    execution(* AccountService.transfer(..));

before(): moneyMovement() {
    System.out.println("Entering transfer");
}
~~~

Ký hiệu execution, dấu ba chấm, phép ghép &&, || và ! theo quy tắc của AspectJ. Không sao chép cú pháp này sang một bộ interceptor khác rồi tin rằng ý nghĩa giữ nguyên.

### 5.1. Các pointcut designator tiêu biểu

| Designator | Ý nghĩa minh họa | Đặc tính cần lưu ý |
| --- | --- | --- |
| execution(signature) | Method/constructor execution | Quan sát ở phía thực thi |
| call(signature) | Method/constructor call site | Quan sát ở phía caller |
| get(fieldPattern), set(fieldPattern) | Field read/write | Không phải mọi AOP đều hỗ trợ |
| within(typePattern) | Vùng mã được khai báo trong type phù hợp | Ràng buộc vị trí lexical |
| withincode(methodPattern) | Điểm nằm trong mã của method/constructor phù hợp | Khác với dynamic control flow |
| this, target | Đối tượng gắn với join point | Cần hiểu nghĩa riêng theo loại join point |
| args | Điều kiện/binding đối số | Liên quan runtime argument và kiểu |
| cflow, cflowbelow | Dynamic control flow của một pointcut khác | Không phải tính năng bắt buộc của mọi AOP |
| if | Điều kiện động theo ngữ cảnh | Có chi phí và hạn chế về cách sử dụng |
| @annotation, @within, @target | Lựa chọn theo annotation | Phụ thuộc target metadata và ranh giới tương ứng |

Các dòng bảng chỉ giới thiệu **nhóm cấu trúc**, không phải mọi tổ hợp đều hợp lệ trong mọi context. Khi dùng thật, tham khảo cú pháp và version AspectJ đang cài, nhất là cách binding tham số và áp dụng annotation.

### 5.2. Static matching và dynamic matching

Một pointcut có thể được phân tích một phần ở thời điểm weaving: tên kiểu, tên phương thức, chữ ký. Những điều kiện dựa trên đối tượng, đối số hoặc control flow có thể phải kiểm tra khi chạy.

Điều này ảnh hưởng hiệu năng và phạm vi:

- Điểm được chọn theo signature không có nghĩa mọi lần chạy đều thỏa điều kiện runtime.
- Dynamic test có thể làm advice không chạy ở một lần cụ thể dù bytecode đã woven.
- Đo lường overhead nên tách thời gian kiểm tra pointcut với logic trong advice khi phân tích hiệu năng.
- Khi kiểm thử quyền, cần biết có điểm enforcement chắc chắn cho mọi tình huống nhạy cảm; một điều kiện động sai có thể thành đường bypass.

## 6. Aspect là module khái niệm; cú pháp aspect của AspectJ là một lựa chọn

Ở mức AOP tổng quát, **aspect** là mô-đun chứa hành vi cắt ngang và các quy tắc áp dụng.

AspectJ code-style cho phép tệp .aj và khai báo:

~~~java
// Trace.aj: đoạn AspectJ code-style, chưa phải chương trình độc lập.
public aspect Trace {
    before(): execution(void Work.start()) {
        System.out.println("Before start");
    }
}
~~~

Từ khóa aspect và khai báo before với dấu hai chấm là cú pháp cụ thể, **không phải Java thuần** được javac biên dịch riêng như một class thông thường.

AspectJ còn có phong cách dùng annotation trong mã Java. Annotation-style vẫn cần cơ chế xử lý/weaving phù hợp; chỉ thêm một annotation lên class không có nghĩa JVM tự hiểu và chèn advice vào bytecode.

Ở mức khái niệm:

- Concern cần được mô-đun hóa.
- Điểm áp dụng concern phải được mô tả rõ.
- Phần thực thi phải có cơ chế nối vào join point.

Ở mức công nghệ: tệp .aj, ajc, những annotation của AspectJ, compiler/runtime jar và cấu hình weaving là những chi tiết **phải học cho AspectJ**.

## 7. Advice: before, after và around ở hai tầng

Trong AOP tổng quát, advice trả lời câu hỏi **logic cắt ngang chạy khi nào so với join point**. Nhưng mỗi ngôn ngữ AOP có kiểu advice, quy tắc tương tác với exception và cách binding dữ liệu khác nhau.

AspectJ code-style có:

~~~java
// Các mảnh advice AspectJ độc lập, không phải một chương trình đầy đủ.
before(): execution(void Work.open()) {
    System.out.println("Before");
}

after(): execution(void Work.close()) {
    System.out.println("After finally");
}

after() returning(int result): execution(int Work.count()) {
    System.out.println("Returned: " + result);
}

after() throwing(IllegalArgumentException error):
        execution(void Work.fail()) {
    System.out.println("Threw: " + error.getMessage());
}

String around(): execution(String Work.label(String)) {
    return proceed();
}
~~~

**Before** chạy trước phần join point tương ứng. Nếu advice itself ném exception, target có thể không được chạy; không được coi before là quan sát không có khả năng ảnh hưởng kết quả.

**After kiểu finally** chạy khi join point kết thúc theo đường bình thường hoặc do throw, trong phạm vi advice tương ứng. Nó không đồng nghĩa “nghiệp vụ thành công”.

**After returning** chạy khi join point trả về bình thường và có thể bind return value. Nếu target trả CompletableFuture pending, chỉ biết target đã trả đối tượng future bình thường; chưa biết tác vụ bên trong thành công.

**After throwing** chạy khi ngoại lệ thoát khỏi join point phù hợp với kiểu binding. Binding IllegalArgumentException không có nghĩa observer nhận mọi throwable.

**Around** có thể thực thi trước và sau phần bên trong, thay đổi kết quả, không tiếp tục phần bên trong hoặc tiếp tục nhiều lần có chủ đích.

Điểm tổng quát là **mẫu thời điểm thực thi**. Các cú pháp returning(...), throwing(...), kiểu của pointcut và quy tắc kết hợp là đặc thù AspectJ.

### Bảng chọn advice theo mục tiêu

| Mục tiêu | Advice/điểm can thiệp thường thích hợp | Lưu ý |
| --- | --- | --- |
| Ghi trước lúc chạy | Before | Logging lỗi có thể chặn target |
| Ghi khi kết thúc bất kể outcome | After/finally | Không mặc định log success |
| Đọc giá trị trả về đồng bộ | After returning | Return future không phải completion future |
| Ghi exception đồng bộ | After throwing | Throwable binding phải phù hợp |
| Cache và short-circuit | Around | Có thể bỏ qua phần thực thi bên trong |
| Retry có điều kiện | Around | Nguy cơ gọi target nhiều lần |
| Biến đổi argument/return | Around | Là thay đổi hợp đồng nghiệp vụ |
| Ghi trạng thái tác vụ async | Completion callback/hook của mô hình async | Không đủ chỉ dùng after returning của lời gọi tạo future |

## 8. Proceed: tính năng điều khiển mạnh nhưng không phổ quát về cú pháp

Trong AOP, around advice có thể **ủy quyền phần thực thi tiếp theo** cho chuỗi nội bộ. AspectJ code-style sử dụng proceed.

~~~java
// Mảnh code-style AspectJ, Work.decorate đã tồn tại.
String around(String input):
        execution(String Work.decorate(String)) && args(input) {
    System.out.println("around entry");
    String value = proceed(input);
    System.out.println("around exit");
    return value;
}
~~~

Proceed không nhất thiết gọi trực tiếp target và bỏ qua mọi advice khác. Nó đi tiếp **qua phần chuỗi bên trong** theo thứ tự precedence đã được xác lập.

### 8.1. Không gọi proceed

~~~java
// Mảnh AspectJ minh họa short-circuit.
String around(): execution(String Work.decorate(String)) {
    return "CACHE_VALUE";
}
~~~

Nếu advice được chạy mà không gọi proceed, phần phía trong không thực thi theo nhánh đó. Có thể hữu ích với cache hay deny, nhưng không phù hợp cho tracing thuần túy nếu target bắt buộc phải chạy.

### 8.2. Gọi proceed nhiều lần

~~~java
// Mảnh AspectJ minh họa retry;
// TransientFailure là một exception giả định do ứng dụng định nghĩa.
String around(): execution(String Work.decorate(String)) {
    try {
        return proceed();
    } catch (TransientFailure ex) {
        return proceed();
    }
}
~~~

Một lần proceed có thể gây side effect rồi throw. Proceed lần nữa có thể nhân đôi side effect; policy retry cần số attempt, ngân sách thời gian, idempotency và khả năng phân biệt kết quả không chắc chắn.

### 8.3. Truyền tham số mới

~~~java
// Mảnh AspectJ, thay thế around khác cho cùng Work.decorate.
String around(String input):
        execution(String Work.decorate(String)) && args(input) {
    String normalized = input.trim();
    return proceed(normalized);
}
~~~

Đây là khả năng thay đổi đầu vào. Nó không phải logging best effort và có thể ảnh hưởng xác thực, cache key, chữ ký, nullability hoặc hành vi domain. Cú pháp truyền đối số vào proceed thuộc về AspectJ code-style; một mô hình AOP khác có thể có invocation object, argument array hoặc callback khác.

**Phân biệt nguyên lý và cú pháp:** “around có thể tiếp tục” là ý tưởng rộng; proceed(...) với quy tắc binding chính xác là hiện thực cụ thể.

## 9. Weaving: concept chung, giai đoạn và công cụ cụ thể

Weaving là việc kết hợp định nghĩa aspect với các join point trong chương trình. Trong thực tế AOP có nhiều cơ chế chèn hành vi: compile-time transformation, load-time bytecode instrumentation, runtime interception, proxy hay decorator.

AspectJ cung cấp những hình thức phổ biến:

1. **Compile-time weaving:** ajc biên dịch nguồn và aspect rồi tạo bytecode được woven.
2. **Post-compile/binary weaving:** dệt vào class/jar đã biên dịch.
3. **Load-time weaving:** dùng cấu hình/agent và class loader thích hợp để dệt khi class được nạp.

Ba mục trên là **lựa chọn của AspectJ**, không phải ba giai đoạn bắt buộc mà mọi ứng dụng AOP phải trải qua.

~~~text
Source Java + AspectJ source
              |
              +--> ajc compile-time weaving
              |
Class/JAR ----+--> post-compile weaving
              |
Class loading +--> load-time weaving
              |
              v
Bytecode thực thi có advice tại join point phù hợp
~~~

Một tệp aspect tồn tại ở source tree **không chứng minh** advice đã được weave hay sẽ được kích hoạt. Cần xác nhận compiler/weaver, classpath/module path, cấu hình class loading, class/call site được instrument và pointcut match.

### Vì sao tự gọi nội bộ không có một đáp án chung?

Với AspectJ execution weaving vào thân method, một self-call phù hợp vẫn có thể chạy advice khi method đó được thực thi. Với một dạng interception chỉ chặn invocation đi qua object đại diện, self-call có thể không đi qua interceptor.

Do đó phát biểu “mọi AOP đều bỏ qua self-invocation” là sai; phát biểu “mọi AOP đều bắt self-invocation” cũng sai. Câu hỏi đúng là **đang chặn loại join point nào và mã nào được instrument**.

## 10. Inter-type declarations: điều nổi bật nhưng không phải bản chất bắt buộc của AOP

Tách concern bằng advice khác với bổ sung cấu trúc kiểu. AspectJ có **inter-type declaration (ITD)** cho phép đưa một số method/field hoặc quan hệ kiểu vào type được chọn theo những quy tắc của compiler/weaving.

Ví dụ hai fragment:

~~~java
// Customer.java — Java thuần
class Customer {
}
~~~

~~~java
// CustomerExtension.aj — AspectJ code-style
public aspect CustomerExtension {
    public String Customer.displayName() {
        return "anonymous";
    }
}
~~~

Trong build được weave phù hợp, phương thức displayName có thể được thêm vào Customer theo quy tắc của AspectJ. Điều này không có nghĩa một class Java viết riêng như trên đã tự có method nếu chỉ chạy javac.

Phân biệt rõ:

- **Advice:** áp dụng hành vi tại join point trong runtime.
- **ITD/introduction:** bổ sung thành viên hoặc quan hệ kiểu.
- **Decorator:** cung cấp một đối tượng bao quanh với interface được thiết kế, không nhất thiết thay type gốc.
- **Inheritance/composition:** kỹ thuật hướng đối tượng bình thường.

ITD có thể hữu ích khi muốn cung cấp một khả năng ngang nhiều kiểu, nhưng làm tăng coupling ẩn và khiến người đọc source class khó thấy toàn bộ API thực tế. Cần quản lý đụng tên method, binary compatibility, vòng đời phiên bản và khả năng debug.

### Declare parents

AspectJ cũng hỗ trợ những khai báo liên quan đến hierarchy, như declare parents, với điều kiện theo hệ kiểu và compiler. Đây là năng lực đặc thù AspectJ; một hệ AOP không có introductions vẫn có thể thực hiện đúng việc quan sát/intercept concern theo mục tiêu của nó.

## 11. Declare precedence, declare warning và declare error

Một khía cạnh riêng của AspectJ là nhóm **declare constructs** phục vụ quy tắc cấu trúc/kiến trúc và thứ tự aspect.

Ví dụ fragment minh họa:

~~~java
// Cú pháp AspectJ trong .aj, giả sử các aspect đã được định nghĩa.
declare precedence: SecurityPolicy, TracingPolicy, *;
~~~

Đây là cách biểu diễn một thứ tự aspect mong muốn. Vấn đề **thứ tự bảo mật trước telemetry** là quan tâm thiết kế tổng quát; câu lệnh declare precedence là ngôn ngữ AspectJ.

Một ví dụ khác:

~~~java
// Fragment trong AspectJ .aj, không phải Java thuần.
declare warning:
    call(* java.lang.System.exit(..)) :
    "Process termination calls should be reviewed";
~~~

Kiểu khai báo này có thể phát cảnh báo từ compiler/weaver cho những join point phù hợp nhằm kiểm tra quy tắc kiến trúc. Khả năng này **không phải** điều kiện để một thư viện được gọi là AOP.

- Declare warning/error giúp phát hiện loại vi phạm có thể mô tả bằng pointcut.
- Nó không thay thế toàn bộ static analyzer, linter hay kiểm thử bảo mật.
- Kết quả phụ thuộc những class/call site được phân tích và phiên bản công cụ.
- Nếu quy tắc về quyền phải được enforce ở runtime, chỉ tạo cảnh báo khi build không tự thay thế điểm chặn bắt buộc.

## 12. Ví dụ Java 17 + AspectJ chạy được để phân biệt hai tầng

Chương trình gồm **hai tệp**:

- Demo.java: Java 17 thông thường với các phương thức nghiệp vụ.
- SampleAspect.aj: tệp code-style của AspectJ dệt advice lên những method phù hợp.

Để thứ tự stdout minh bạch, chỉ một dạng around chọn method decorate, còn before/after chọn prepare, after returning chọn amount và after throwing chọn broken.

### 12.1. Tệp Demo.java

~~~java
import java.util.Locale;

final class Service {
    void prepare() {
        System.out.println("TARGET prepare");
    }

    int amount() {
        System.out.println("TARGET amount");
        return 12;
    }

    String decorate(String input) {
        System.out.println("TARGET decorate");
        return input.toUpperCase(Locale.ROOT);
    }

    void broken() {
        System.out.println("TARGET broken");
        throw new IllegalArgumentException("invalid");
    }
}

public class Demo {
    public static void main(String[] args) {
        Service service = new Service();
        service.prepare();
        System.out.println("CALLER amount=" + service.amount());
        System.out.println("CALLER decorate=" + service.decorate("aop"));

        try {
            service.broken();
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER error=" + error.getMessage());
        }
    }
}
~~~

Tệp trên **chạy được bằng Java thuần**; nó không có khai báo aspect hay advice trong source.

### 12.2. Tệp SampleAspect.aj

~~~java
public aspect SampleAspect {
    before(): execution(void Service.prepare()) {
        System.out.println("BEFORE prepare");
    }

    after(): execution(void Service.prepare()) {
        System.out.println("AFTER prepare");
    }

    after() returning(int value): execution(int Service.amount()) {
        System.out.println("AFTER returning=" + value);
    }

    after() throwing(IllegalArgumentException error):
            execution(void Service.broken()) {
        System.out.println("AFTER throwing=" + error.getMessage());
    }

    String around(String input):
            execution(String Service.decorate(String)) && args(input) {
        System.out.println("AROUND enter=" + input);
        String result = proceed(input);
        System.out.println("AROUND exit=" + result);
        return result;
    }
}
~~~

Ở đây:

1. aspect và cú pháp .aj là ngôn ngữ code-style AspectJ.
2. execution(...) là pointcut designator của AspectJ.
3. before/after/around cụ thể hóa các mẫu advice chung.
4. returning và throwing binding là quy tắc liên kết dữ liệu của AspectJ.
5. proceed(input) đi tiếp qua phần thực thi bên trong around.
6. Method Service.decorate không chứa dòng nào về logging; output AROUND đến từ mã được weaving.

### 12.3. Biên dịch và chạy

Cần một JDK 17 và trình biên dịch AspectJ ajc phiên bản hỗ trợ Java 17, cùng runtime jar tương ứng. Giả sử ASPECTJ_HOME trỏ vào bộ cài có lib/aspectjrt.jar và lệnh ajc có trong PATH.

Trên Unix:

~~~bash
mkdir -p out
ajc -source 17 -target 17 -classpath "$ASPECTJ_HOME/lib/aspectjrt.jar" -d out Demo.java SampleAspect.aj
java -cp "out:$ASPECTJ_HOME/lib/aspectjrt.jar" Demo
~~~

Trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
ajc -source 17 -target 17 -classpath "$env:ASPECTJ_HOME\lib\aspectjrt.jar" -d out Demo.java SampleAspect.aj
java -cp "out;$env:ASPECTJ_HOME\lib\aspectjrt.jar" Demo
~~~

Bộ cài khác nhau có thể đặt compiler/runtime jar ở vị trí khác hoặc gọi ajc thông qua một launcher khác. Điều kiện thực chất là bộ công cụ hỗ trợ Java 17 và classpath hợp lệ. Mã .aj không được javac biên dịch trực tiếp; ajc thực hiện compile-time weaving ở ví dụ này.

### 12.4. Stdout dự kiến khi weave thành công

~~~text
BEFORE prepare
TARGET prepare
AFTER prepare
TARGET amount
AFTER returning=12
CALLER amount=12
AROUND enter=aop
TARGET decorate
AROUND exit=AOP
CALLER decorate=AOP
TARGET broken
AFTER throwing=invalid
CALLER error=invalid
~~~

Giải thích:

- prepare: before chạy trước thân method; after kiểu finally chạy sau khi thân method trả bình thường.
- amount: method in rồi trả 12; after returning nhận 12 trước khi biểu thức println ở caller sử dụng giá trị trả về.
- decorate: around in khi vào, gọi proceed, method gốc in rồi trả AOP, around in khi thoát và caller nhận AOP.
- broken: thân method in rồi ném IllegalArgumentException; after throwing nhận lỗi tương thích, rồi caller bắt lỗi.

Dù có một before và một after ở prepare, thứ tự trước/thân/sau vẫn dễ xác định; ví dụ không có nhiều around cạnh tranh trên cùng join point.

### 12.5. Chạy Java thuần để đối chiếu

Biên dịch riêng Demo.java vào một thư mục khác, không dùng aspect:

~~~bash
mkdir -p plain
javac -d plain Demo.java
java -cp plain Demo
~~~

Stdout nếu không có cơ chế weaving nào khác:

~~~text
TARGET prepare
TARGET amount
CALLER amount=12
TARGET decorate
CALLER decorate=AOP
TARGET broken
CALLER error=invalid
~~~

So với bản ajc:

- Các dòng TARGET/CALLER do Java code tạo ra.
- Các dòng BEFORE/AFTER/AROUND do aspect tạo ra qua weaving.
- Không có weaving thì cùng code Java chạy không có advice.
- AOP là ý tưởng tổ chức concern; **AspectJ cung cấp cơ chế cụ thể biến ý tưởng thành hành vi**.

## 13. Vì sao ví dụ này không chứng minh mọi tính năng AOP trên thế giới?

Ví dụ minh họa một tập pointcut và advice cụ thể do AspectJ hỗ trợ. Nó **không chứng minh**:

- Mọi cơ chế AOP đều có join point field access.
- Mọi cơ chế đều chặn self-invocation giống nhau.
- Mọi cơ chế dùng compiler ajc hoặc tệp .aj.
- Mọi hệ đều cho phép sửa hệ thống kiểu bằng ITD.
- Mọi advice trong mọi công nghệ sẽ sắp theo cùng một quy tắc precedence.
- Một method trả future là đã hoàn tất tác vụ bên trong.
- Một aspect có mặt trong source tree luôn được kích hoạt.

Muốn đánh giá một công nghệ AOP khác, hãy giữ cách đặt câu hỏi theo concern/join point/pointcut/advice/continuation/ordering và xác minh khả năng thực tế của nó.

## 14. Một thí nghiệm phân biệt call với execution

Khi đã chạy ví dụ chính, hãy thử tạo aspect riêng quan sát một method theo hai điểm:

~~~java
// Hai mảnh pointcut/advice của AspectJ; chưa phải chương trình độc lập.
before(): call(String Service.decorate(String)) {
    System.out.println("AT CALL SITE");
}

before(): execution(String Service.decorate(String)) {
    System.out.println("AT METHOD EXECUTION");
}
~~~

Trong trường hợp caller chứa call site và thân method đều đã được woven, một lời gọi có thể đi qua cả hai dạng advice. Nhưng số lượng event còn tùy phạm vi instrumentation và số caller. Không kết luận “mọi lời gọi đều in hai dòng” khi chưa xác định đầy đủ build và loader.

**Đây là cách kiểm chứng một đặc tính của AspectJ:** call-side với execution-side có thể khác nhau. **Đây không phải yêu cầu của mọi hiện thực AOP.**

### Self-invocation

~~~java
// Fragment Java, chưa phải ví dụ biên dịch độc lập.
class Task {
    void run() {
        sensitive();
    }

    void sensitive() {
        updateData();
    }
}
~~~

Nếu phần thân sensitive được woven với execution pointcut tương ứng, việc gọi từ run trên cùng object vẫn có thể được advice quan sát. Nếu chỉ có proxy-based call interception tại một ranh giới bên ngoài, self-call có thể không đi qua proxy. Không áp một nhận xét từ cơ chế này lên tất cả cơ chế khác.

## 15. Độ ưu tiên giữa nhiều aspect là một vấn đề thiết kế thực

Giả sử authentication, authorization, retry, transaction và metrics cùng áp dụng một operation.

~~~text
Authorization
  -> Retry
      -> Metrics
          -> Business logic
      <- Metrics
  <- Retry
<- Authorization
~~~

Sơ đồ chỉ là thứ tự giả định phục vụ phân tích:

- Metrics bên trong retry đo từng attempt.
- Metrics bên ngoài retry đo toàn operation.
- Authorization nằm ngoài retry có thể được đánh giá một lần; bên trong retry có thể được đánh giá lại theo mỗi attempt.
- Một around không gọi proceed có thể khiến advice phía trong không được thực thi.
- Ghi audit trong after finally có thể phát sinh cả khi target thất bại; thứ tự và failure policy quan trọng.

**Precedence/ordering là câu hỏi tổng quát.** Declare precedence và quy tắc advice của AspectJ là cách một công nghệ cụ thể giải quyết câu hỏi ấy. Khi correctness phụ thuộc thứ tự, phải kiểm tra/thiết kế thứ tự tường minh thay vì tin rằng thứ tự các file được biên dịch quyết định hành vi.

## 16. Lỗi instrumentation và ngoại lệ: nguyên tắc chung cho mọi AOP

Nếu một before advice ghi log mà logger ném exception, target có thể không chạy. Nếu after finally ghi log lỗi, caller có thể nhìn thấy lỗi logging thay cho return hay exception nghiệp vụ. Around có thể vô tình gọi proceed hai lần hoặc bỏ qua proceed.

Ví dụ anti-pattern:

~~~java
// Fragment AspectJ, giả sử logger và Work.process tồn tại.
after(): execution(* Work.process(..)) {
    logger.write("finished");
}
~~~

Nếu logger ném lỗi, hành vi nghiệp vụ có thể bị thay đổi. Cách xử lý tùy **chính sách**:

- Telemetry/logging best effort nên cô lập lỗi có thể xử lý.
- Audit bắt buộc phải được thiết kế ở ranh giới cam kết/bền vững phù hợp, không để một lỗi sau commit biến operation thành thất bại giả.
- Authorization từ chối sớm là thay đổi control flow có chủ đích, cần kiểm thử.
- Retry chỉ hợp lệ khi có số attempt/timeout/idempotency.
- Cancellation của future không bảo đảm task đã dừng; after returning của method không đồng nghĩa success end-to-end.

Tất cả đều là các nguyên tắc lập trình đúng đắn vượt quá cú pháp .aj.

## 17. Bất đồng bộ: join point đồng bộ không tự thấy completion

Giả sử một method Java trả CompletableFuture đang pending:

~~~java
// Fragment Java; pending được cung cấp từ ngữ cảnh xung quanh.
CompletableFuture<String> loadAsync() {
    return pending;
}
~~~

After returning trên execution của loadAsync chạy lúc method trả đối tượng future. Nó không tự chờ kết quả được tính sau đó. After throwing trên method cũng không tự nhận lỗi xảy ra trong worker lúc khác.

Để quan sát completion, có thể dùng API của mô hình bất đồng bộ:

~~~java
// Fragment Java, stage và recordOutcome đã được định nghĩa.
CompletionStage<String> monitored = stage.whenComplete(
        (value, failure) -> recordOutcome(value, failure));
~~~

Lưu ý:

1. whenComplete tạo dependent stage; callback observer ném lỗi có thể làm dependent stage thất bại khi source thành công.
2. Completion callback có thể chạy ngay khi gắn nếu source đã hoàn tất hoặc chạy trên luồng làm source hoàn tất.
3. Context theo ThreadLocal không tự động truyền qua mọi callback/executor.
4. Timeout của future không chứng minh tác vụ đã bị dừng; side effect có thể tới muộn.
5. Muốn logging best effort, callback observer phải cô lập lỗi logging theo policy.

AOP có thể giúp gắn concern vào chỗ phù hợp, nhưng **ngữ nghĩa vòng đời async thuộc mô hình thực thi**, không thể suy ra chỉ từ nhãn advice after.

## 18. Checklist chuyển kiến thức sang một hệ AOP khác

Trước khi áp dụng một điều học từ AspectJ cho một công nghệ khác, hãy kiểm tra:

1. Concern cần tách là gì? Nó thật sự cross-cutting hay là domain invariant?
2. Join point được hỗ trợ: method call, method execution, constructor, field, callback hay tập khác?
3. Pointcut/selection được diễn đạt bằng cú pháp, annotation, interceptor registration hay code generation?
4. Advice có before, after finally, returning, throwing, around không; semantics riêng là gì?
5. Cơ chế continuation là proceed, invocation object hay callback?
6. Có thể short-circuit hoặc gọi phần bên trong nhiều lần không? Hợp đồng return/exception ra sao?
7. Advice được gắn ở compile-time, load-time hay runtime? Mã nào thực sự được instrument?
8. Self-invocation, direct call, reflection, overload và class loader mới ảnh hưởng coverage thế nào?
9. Thứ tự nhiều advice được quyết định ra sao?
10. Có introductions/ITD hoặc thay đổi cấu trúc type không? Có thật sự cần dùng?
11. Lỗi do logging/metrics có thể che lỗi nghiệp vụ không?
12. Method trả future có cần hook completion riêng để quan sát outcome không?
13. Cần những integration test nào để chứng minh pointcut match và target chạy đúng số lần?

Chỉ sau khi trả lời những câu hỏi này mới có thể dịch một ví dụ AspectJ sang công nghệ khác mà không sai ngữ nghĩa.

## 19. Những ngộ nhận thường gặp

**“AOP chính là AspectJ.”** Sai: AspectJ là một hiện thực của tư duy AOP.

**“Có từ khóa execution là có AOP.”** Sai: đây là cú pháp selection cụ thể của AspectJ, không phải chuẩn chung.

**“Mọi AOP chặn được get/set field.”** Sai: tập join point khác nhau.

**“AspectJ chỉ quan sát method call.”** Sai: có method execution và nhiều loại khác.

**“Bất cứ code Java nào cũng tự kích hoạt .aj.”** Sai: cần compile/weaving/runtime phù hợp.

**“Proceed luôn gọi target đúng một lần.”** Sai: around kiểm soát việc có proceed và số lần gọi; có thể đi qua advice khác.

**“AOP bắt buộc compile-time weaving.”** Sai: cả ý tưởng AOP lẫn công nghệ AspectJ đều không bị giới hạn chỉ một thời điểm dệt.

**“Mọi framework AOP đều có inter-type declarations.”** Sai: ITD là khả năng không phổ quát.

**“After returning chứng minh tác vụ async đã hoàn tất.”** Sai: chỉ method trả về bình thường.

**“Nếu advice không chạy thì chắc vì pointcut sai.”** Chưa đủ: còn có khả năng bytecode/call site không được woven hoặc class loader không dùng đúng artifact.

**“Nếu advice chạy thì contract nghiệp vụ không thể bị đổi.”** Sai: around, lỗi logger, exception transformation và retry đều có thể đổi kết quả.

## 20. Bài tập tự đánh giá

**Câu 1.** AOP là tư duy hay một tệp mã nguồn cụ thể?

**Đáp án:** AOP là tư duy/mô hình hóa concern cắt ngang tại join point. Tệp .aj là một cách hiện thực bằng AspectJ.

**Câu 2.** Vì sao call và execution trong AspectJ không nên bị hiểu là cùng một join point?

**Đáp án:** Call quan sát call site; execution quan sát phần thân method được thực thi. Phạm vi weaving và nhiều caller có thể tạo coverage khác nhau.

**Câu 3.** Nếu một thư viện AOP chỉ hỗ trợ interceptor quanh method, nó có phải “không phải AOP” vì thiếu get/set không?

**Đáp án:** Không. Một mô hình có tập join point hẹp hơn vẫn có thể mô-đun hóa concern xuyên suốt.

**Câu 4.** Around advice không gọi proceed sẽ có tác dụng gì?

**Đáp án:** Nó có thể short-circuit phần thực thi bên trong và trả kết quả thay thế. Đó là hành vi cần được chính sách cho phép, nhất là khi target có side effect.

**Câu 5.** Inter-type declaration có phải đặc điểm bắt buộc của mọi AOP không?

**Đáp án:** Không. Đây là một khả năng mở rộng kiểu mà AspectJ hỗ trợ; không phải mọi hệ AOP có.

**Câu 6.** Một aspect tồn tại nhưng không có log. Kiểm tra những gì?

**Đáp án:** Compiler/weaver có chạy, class/call site đã woven chưa, pointcut có match không, điều kiện runtime có thỏa không, class loader và phiên bản runtime có đúng không.

**Câu 7.** Vì sao after returning của một method trả CompletableFuture chưa cho biết operation thành công?

**Đáp án:** Future có thể chưa hoàn tất hoặc hoàn tất exceptional ở thời điểm khác; phải quan sát completion channel.

**Câu 8.** Declare precedence là điều gì?

**Đáp án:** Một cách của AspectJ để mô tả ưu tiên giữa các aspect. Bài toán thứ tự advice là tổng quát, nhưng cú pháp đó không phổ quát.

**Câu 9.** Làm sao minh họa rõ sự khác nhau giữa Java thuần và AspectJ weaving?

**Đáp án:** Biên dịch Demo.java bằng javac để quan sát dòng TARGET/CALLER, rồi dệt thêm SampleAspect.aj bằng ajc để quan sát các dòng BEFORE/AFTER/AROUND tương ứng.

**Câu 10.** Điều gì nên mang theo khi học một công nghệ AOP khác?

**Đáp án:** Concern, join point, selection/pointcut, advice semantics, continuation, weaving/interception, precedence, coverage, error/side-effect policy và phương pháp kiểm thử; cú pháp cụ thể phải học lại theo công nghệ đó.

## Kết luận

**AOP cung cấp bộ khái niệm để tổ chức logic cắt ngang; AspectJ cung cấp một mô hình và bộ công cụ Java cụ thể để triển khai những khái niệm đó.** Method call/execution, get/set, cflow, tệp .aj, compiler ajc, inter-type declaration và declare constructs là những tính năng quan trọng khi học AspectJ nhưng không thể coi là luật chung của mọi AOP. Điều nên giữ lại khi chuyển công nghệ là tư duy xác định concern, điểm can thiệp, thứ tự thực thi, phạm vi bao phủ, kết quả/ngoại lệ và các kiểm thử xác nhận hành vi thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)
