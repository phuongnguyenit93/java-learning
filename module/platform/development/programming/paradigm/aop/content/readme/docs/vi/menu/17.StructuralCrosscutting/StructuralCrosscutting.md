<a id="back-to-top"></a>

# Thay đổi cấu trúc

## Menu
- [Phân biệt cắt ngang hành vi với cắt ngang cấu trúc](#structural-crosscutting-001)
- [Bổ sung phương thức, thuộc tính hoặc giao diện khi công cụ hỗ trợ](#structural-crosscutting-002)
- [Khai báo liên kiểu (inter-type declaration) trong mô hình AOP phù hợp](#structural-crosscutting-003)
- [Giới hạn truy cập và xung đột tên khi bổ sung thành phần](#structural-crosscutting-004)
- [Thay đổi quan hệ kiểu và ảnh hưởng tới mã sử dụng](#structural-crosscutting-005)
- [Quan hệ giữa mở rộng cấu trúc, mixin và kế thừa](#structural-crosscutting-006)
- [Cảnh báo hoặc lỗi ở bước xây dựng để kiểm tra quy tắc kiến trúc](#structural-crosscutting-007)
- [Tương thích mã nguồn, tương thích nhị phân và chi phí bảo trì](#structural-crosscutting-008)
- [Khi nào nên sửa thiết kế kiểu trực tiếp thay vì mở rộng bằng aspect](#structural-crosscutting-009)

## <a id="structural-crosscutting-001">Phân biệt cắt ngang hành vi với cắt ngang cấu trúc</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt cắt ngang hành vi với cắt ngang cấu trúc

## 1. Vấn đề: aspect có thể tác động lên **cách code chạy** hoặc **hình dạng kiểu**

**Aspect-Oriented Programming (AOP)** tổ chức các mối quan tâm cắt ngang (cross-cutting concerns) tách khỏi mã nghiệp vụ chính. Khi học AOP, nhiều người chỉ hình dung một aspect ghi log trước và sau method. Đó là **cắt ngang hành vi (behavioral crosscutting)**: bổ sung việc xảy ra **trong quá trình thực thi**. Tuy nhiên, một số mô hình AOP còn cho phép **cắt ngang cấu trúc (structural crosscutting)**: biểu đạt hoặc bổ sung quan hệ kiểu, interface, thành viên, ràng buộc khai báo hoặc metadata của chương trình.

Hai khái niệm khác nhau ở **đối tượng chịu thay đổi**:

- **Cắt ngang hành vi:** can thiệp *khi một join point xảy ra* — thực hiện before/after/around, quan sát exception, quyết định gọi tiếp, biến đổi result hoặc áp đặt chính sách cho lời gọi.
- **Cắt ngang cấu trúc:** can thiệp vào *những gì một type/module biểu hiện* — chẳng hạn một type có thêm interface, một hợp đồng thành viên được giới thiệu, hoặc một quan hệ khai báo được áp dụng cho nhiều class mà không phải chỉnh sửa thủ công từng class.

Ví dụ khái niệm:

~~~text
                      AOP crosscutting
                       /             \
                      /               \
          BEHAVIORAL                     STRUCTURAL
         "Khi run() chạy?"              "Type này có thể có gì?"
           |                                  |
   before / after / around           interface / method / field
   logging, timing, policy           type relationship / declaration
           |                                  |
   Thay đổi luồng thực thi           Thay đổi cấu trúc hoặc contract
   của join point được chọn          mà công cụ cho phép bổ sung
~~~

**Không có bảo đảm rằng mọi AOP engine hỗ trợ cả hai dạng.** Một implementation chỉ dùng dynamic proxy có thể can thiệp vào các lời gọi đi qua proxy và tạo wrapper hiện thực thêm interface, nhưng **không biến đổi cấu trúc của class target gốc**. Ngược lại, một hệ thống weaving có hỗ trợ *inter-type declarations* có thể thêm hành vi thành viên hoặc quan hệ kiểu ở mức class đã dệt; chi tiết phụ thuộc thời điểm weaving, khả năng công cụ và ràng buộc JVM.

Bài này học **Aspect/AOP tổng quát**: cách phân biệt, tình huống sử dụng, giới hạn công nghệ, ví dụ Java 17, kiểm thử, rủi ro tương thích và thiết kế bảo trì.

## 2. Nền tảng: join point, pointcut và advice là phía hành vi

**Join point** là một điểm/sự kiện chương trình được mô hình AOP hỗ trợ, ví dụ method call hoặc method execution. **Pointcut** là quy tắc chọn các join point. **Advice** là hành vi được thực hiện tại những điểm được chọn.

Ví dụ về ý định hành vi:

~~~text
Khi execution của AccountService.transfer(...) xảy ra:
  BEFORE: bắt đầu trace
  AROUND: đo duration, gọi hoạt động gốc
  AFTER RETURNING: ghi outcome thành công
  AFTER THROWING: ghi thông tin lỗi thích hợp
  FINALLY: giải phóng context
~~~

Những hành vi này có thể thay đổi **runtime semantics**: số lần target thực thi, thứ tự side effects, kết quả và exception. Một cache around advice có thể không gọi target; một retry around advice có thể gọi target nhiều lần. Vì vậy “cắt ngang hành vi” không chỉ là “thêm log” — nó có thể điều khiển cả **luồng thực thi**.

Tuy nhiên, ngay cả khi advice biến đổi result `"ok"` thành `"[ok]"`, sự thay đổi đó **không tự chứng minh class của target có thêm public method hay interface**. Đó vẫn là một can thiệp vào behavior của invocation.

### 2.1. Tại sao khái niệm "join point" không đủ mô tả cấu trúc?

Một method có thể **không bao giờ được gọi** trong một lần chạy mà class vẫn có một interface hoặc field khai báo. Đặc tính cấu trúc được xem xét ở mức type definition, compilation, class loading hoặc reflection. Nó không chỉ tồn tại trong khoảng thời gian một invocation đang active.

Do đó, không mô tả introduction bằng câu “advice before chạy lúc object được tạo”, trừ khi một cơ chế cụ thể có lifecycle như vậy. **Mở rộng hợp đồng kiểu** và **khởi chạy code khi constructor thực thi** là hai vấn đề khác nhau.

## 3. So sánh trực tiếp hai dạng cắt ngang

| Tiêu chí | Cắt ngang hành vi | Cắt ngang cấu trúc |
| --- | --- | --- |
| Câu hỏi trung tâm | Khi join point xảy ra, ta làm gì? | Type/module có những member hoặc quan hệ nào? |
| Đối tượng thay đổi | Execution, call, event, outcome | Type shape, interface membership, declarations |
| Khái niệm điển hình | Pointcut, before/after/around advice | Introduction, inter-type declaration, type relationship |
| Thời điểm có hiệu lực | Khi lời gọi/event được can thiệp | Tùy công cụ: build/weave/load/proxy creation |
| Có cần target thực thi để quan sát? | Thường cần một join point thực sự xảy ra | Không nhất thiết: reflection/type checking có thể thấy khi class/proxy tồn tại |
| Ví dụ | Đo duration cho mọi `pay()` | Làm một nhóm types đáp ứng interface `Auditable` |
| Có thêm method vào class gốc? | Không phải mục tiêu định nghĩa | Có thể nếu engine thực sự hỗ trợ structural weaving |
| Có thể đổi giá trị trả về? | Có, nhất là around advice nếu API cho phép | Một introduction method cũng có body riêng, nhưng ý chính là thêm contract |
| Compiler thấy API mới? | Thường không có API mới để thấy | Tùy thời điểm weaving, compiler/plugin và kiểu tĩnh tham chiếu |
| Reflection trên target gốc đổi không? | Thường không do interceptor đơn thuần | Phụ thuộc structural weaving; proxy wrapper không làm target gốc đổi |
| Rủi ro chính | Thứ tự advice, side effects, exceptions, performance | Collisions, binary compatibility, type identity, initialization, tooling |

**Một giải pháp có thể kết hợp cả hai.** Ví dụ, một introduction cho type `Auditable` bổ sung phương thức `auditId()`, rồi một behavioral advice ghi audit mỗi khi method `save()` được gọi. Introduction định nghĩa **khả năng mới của type**; advice định nghĩa **khi nào khả năng đó được sử dụng**.

## 4. Behavioral crosscutting: điểm mạnh và phạm vi

Các concern thường phù hợp với behavioral advice:

- Ghi log, đo latency, tracing, audit theo invocation.
- Kiểm tra quyền hoặc quota tại method boundary.
- Retry có kiểm soát hoặc fallback với các operation idempotent.
- Caching khi kết quả phụ thuộc input và policy.
- Validation đầu vào, chuẩn hóa result theo hợp đồng.
- Quan sát exception hoặc đóng scope.
- Mở hoặc đóng tài nguyên theo invocation, nếu ownership rõ.

Ví dụ pseudo-API tổng quát:

~~~java
// Pseudocode: Proceed và Metrics do hệ thống cung cấp.
Object around(Proceed next, Metrics metrics) throws Throwable {
    long started = System.nanoTime();
    try {
        return next.proceed();
    } finally {
        metrics.observe(System.nanoTime() - started);
    }
}
~~~

Đây là **minh họa không độc lập**, không phải API phổ quát của mọi engine. Nó cho thấy “cắt ngang hành vi” xoay quanh một lần `proceed()` tại join point.

### 4.1. Hành vi không đồng nghĩa không có trạng thái

Behavioral aspect có thể dùng fields, counters, context objects và cả external resources. Nó có thể **làm thay đổi state runtime** hoặc side effects nghiệp vụ. Điều phân biệt với structural crosscutting không phải “có state hay không”, mà là **có thêm thành viên/hợp đồng vào type hay chỉ xử lý invocation theo contract đã có**.

Một field nội bộ của handler dùng để đếm số lời gọi **không tự biến thành field mới của target class**. Nếu reflection trên target gốc vẫn không có field đó, đừng gọi nó là “structural introduction vào target”.

### 4.2. Hành vi vẫn có thể làm sai hợp đồng

Advice có thể ném exception sau khi target đã commit, retry gây side effects trùng, hoặc thay result. Cần test:

- Giá trị caller nhìn thấy.
- Ngoại lệ gốc và causal chain.
- Số lần target chạy.
- Thứ tự và số lượng side effects.
- Những join points không được chọn.
- Concurrent requests/async completion.

**Không vì “chỉ thêm advice” mà coi tính đúng đắn của chương trình là bất biến.**

## 5. Structural crosscutting: introduction và inter-type declaration

Trong những mô hình AOP có hỗ trợ **structural crosscutting**, một aspect có thể mô tả rằng các type đã có thêm khả năng hay khai báo ở mức cấu trúc.

Những loại tính năng thường được gọi là **introductions** hoặc **inter-type declarations**:

1. **Giới thiệu interface/type relationship:** một tập classes được khai báo đáp ứng một contract mới.
2. **Giới thiệu method:** thêm member method với signature/implementation được xác định.
3. **Giới thiệu field/property:** thêm trạng thái có lifecycle và visibility phù hợp.
4. **Giới thiệu constructor hoặc member khác:** tùy đúng khả năng của công cụ và ngôn ngữ.
5. **Thay đổi kiểu cha hoặc quan hệ khai báo:** có thể có công cụ hỗ trợ, nhưng thường chịu giới hạn nghiêm ngặt về type hierarchy.
6. **Áp quy tắc khai báo/checking:** ví dụ quy tắc lỗi/cảnh báo cho những quan hệ thiết kế không hợp lệ; đây là đặc tính declarative có thể đi cùng mô hình AOP.

**Đây là danh sách khả năng theo mô hình, không phải lời khẳng định mọi tool đều thêm được từng loại thành viên.** Trong Java, type hierarchy, final classes, interface compatibility, access rules, visibility, classloader và verifier đều ảnh hưởng tính khả thi.

### 5.1. Ví dụ khái niệm một introduction

Một codebase có nhiều đối tượng:

~~~text
Order
Invoice
Shipment
~~~

Ứng dụng muốn chúng có thể cung cấp cùng contract:

~~~java
interface Trackable {
    String trackingId();
}
~~~

Đây chỉ là **declaration Java** để minh họa hợp đồng.

Một structural weaving tool có thể cho phép diễn đạt ở cấp aspect:

~~~text
INTRODUCE: Order, Invoice, Shipment IMPLEMENT Trackable
INTRODUCE METHOD trackingId(): return a type-specific identifier
~~~

Hai dòng trên là **pseudocode AOP**, không phải cú pháp Java hay cú pháp bảo đảm của bất kỳ engine cụ thể nào. Nếu công cụ hỗ trợ và weaving đúng, các types đã được dệt có thể đáp ứng contract mới.

**Một behavioral advice đo `trackingId()` khi nó được gọi là chuyện khác.** Nó không tự làm ba classes có thêm method.

### 5.2. Introduction có thể có body nhưng vẫn là cấu trúc

Khi introduction thêm một method `trackingId()` có implementation, method ấy tất nhiên có hành vi khi được gọi. Nhưng tính năng **giới thiệu method vào contract của type** được xếp vào structural crosscutting, trong khi việc advice chạy trước/sau execution của method đó thuộc behavioral crosscutting.

Hai trục có thể giao nhau mà không đồng nhất.

## 6. Một ví dụ đối chiếu đơn giản

Giả sử trước can thiệp:

~~~java
interface Operation {
    String process(String input);
}
~~~

Có hai yêu cầu:

**Yêu cầu A — hành vi:** mọi invocation `process()` qua một đường can thiệp phải ghi `before` và `after`, nhưng caller vẫn dùng `Operation`.

**Yêu cầu B — cấu trúc:** một object quan sát được cần có thêm contract `Tagged` với method `tag()`.

~~~java
interface Tagged {
    String tag();
}
~~~

Các kết quả có thể:

~~~text
BEHAVIORAL WRAPPER:
  Operation proxy -> intercept process(input)
  -> CoreOperation vẫn chỉ implements Operation

STRUCTURAL-LIKE WRAPPER INTRODUCTION:
  Proxy object implements Operation + Tagged
  -> caller có thể cast proxy sang Tagged
  -> CoreOperation gốc vẫn KHÔNG implements Tagged

TRUE TARGET-CLASS INTRODUCTION (chỉ nếu engine hỗ trợ):
  Woven CoreOperation implements Operation + Tagged
  -> cấu trúc class đã dệt có thể thay đổi
  -> cần điều kiện weaving, type-checking và binary compatibility
~~~

**Bẫy quan trọng:** proxy implementing additional interface **không chứng minh target class có thêm interface**. Nó đổi **type của wrapper**, không đổi type của object đang được wrapper bao.

## 7. Java 17 chạy được: so sánh advice hành vi và interface trên wrapper

Chương trình sau dùng **JDK dynamic proxy**. Mục đích của nó là kiểm thử:

- Một proxy `Operation` ghi trước/sau `process` nhưng **không bổ sung contract `Tagged`**.
- Một proxy khác triển khai **hai interfaces `Operation` và `Tagged`**, cho phép gọi `tag()` trên proxy.
- Type `CoreOperation` gốc vẫn **không implements `Tagged`** trước và sau khi tạo cả hai proxies.
- Hai lượt gọi `process` đi đến cùng core và tăng `calls` đúng hai lần.
- Reflection/runtime type checks phân biệt thành viên trên wrapper với thành viên trên target.

**Điểm giới hạn:** ví dụ không dùng một structural weaver và không giới thiệu method vào bytecode của `CoreOperation`. Đây là mô phỏng bằng **adapter/proxy**, đủ để học sự khác biệt giữa **behavior at invocation boundary** và **new interface contract exposed by another object**. Một engine inter-type declaration thật có semantics riêng phải kiểm bằng integration test trên woven class.

Tạo file `AspectStructureDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class AspectStructureDemo {
    interface Operation { String process(String input); }
    interface Tagged { String tag(); }

    static final class CoreOperation implements Operation {
        int calls;
        public String process(String input) {
            calls++;
            System.out.println("CORE process " + input);
            return "ok:" + input;
        }
    }

    static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }

    static Operation addBehavior(Operation target) {
        return (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(), new Class<?>[]{Operation.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                        return invoke(target, method, args);
                    System.out.println("BEHAVIOR before " + args[0]);
                    Object value = invoke(target, method, args);
                    System.out.println("BEHAVIOR after " + value);
                    return value;
                });
    }

    static Object addProxyInterface(Operation target, String assignedTag) {
        return Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[]{Operation.class, Tagged.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Tagged.class)
                        return assignedTag;
                    if (method.getDeclaringClass() == Object.class)
                        return invoke(target, method, args);
                    System.out.println("INTRO_PROXY before " + args[0]);
                    Object value = invoke(target, method, args);
                    System.out.println("INTRO_PROXY after " + value);
                    return value;
                });
    }

    static void check(boolean condition) {
        if (!condition) throw new AssertionError("assertion failed");
    }

    public static void main(String[] args) {
        CoreOperation core = new CoreOperation();
        Operation behavior = addBehavior(core);
        check(core instanceof Operation);
        check(!Tagged.class.isInstance(core));
        check(!(behavior instanceof Tagged));
        System.out.println("CORE Operation=true Tagged=false");
        System.out.println("BEHAVIOR proxy Tagged=false");
        System.out.println("RESULT behavior=" + behavior.process("A"));

        Object introduced = addProxyInterface(core, "region:eu");
        check(introduced instanceof Operation);
        check(introduced instanceof Tagged);
        check(!Tagged.class.isAssignableFrom(CoreOperation.class));
        System.out.println("INTRO_PROXY Tagged=true");
        System.out.println("INTRO tag=" + ((Tagged) introduced).tag());
        System.out.println("RESULT introduced="
                + ((Operation) introduced).process("B"));

        check(!Tagged.class.isInstance(core));
        check(core.calls == 2);
        System.out.println("CORE after Tagged=false");
        System.out.println("CORE calls=" + core.calls);
        System.out.println("ALL ASSERTIONS PASSED");
    }
}
~~~

Biên dịch và chạy (JDK 17 trở lên):

~~~bash
javac --release 17 -encoding UTF-8 AspectStructureDemo.java
java AspectStructureDemo
~~~

Đầu ra **đã được xác minh bằng biên dịch/chạy thực**:

~~~text
CORE Operation=true Tagged=false
BEHAVIOR proxy Tagged=false
BEHAVIOR before A
CORE process A
BEHAVIOR after ok:A
RESULT behavior=ok:A
INTRO_PROXY Tagged=true
INTRO tag=region:eu
INTRO_PROXY before B
CORE process B
INTRO_PROXY after ok:B
RESULT introduced=ok:B
CORE after Tagged=false
CORE calls=2
ALL ASSERTIONS PASSED
~~~

### 7.1. Tại sao `behavior instanceof Tagged` là false?

`addBehavior()` tạo proxy chỉ với `new Class<?>[]{Operation.class}`. Invocation handler biết thêm before/after cho `process`, nhưng không có interface mới được đăng ký. Caller có thể dùng nó như `Operation`; không thể dùng như `Tagged`.

**Logic trước/sau là behavioral crosscutting ở ranh giới proxy**, không phải structural modification của target.

### 7.2. Tại sao `introduced instanceof Tagged` là true?

`addProxyInterface()` tạo proxy với **hai interfaces**: `Operation` và `Tagged`. JVM tạo một proxy object hiện thực cả hai, handler cung cấp kết quả khi method `tag` được gọi.

Do đó cast `(Tagged)introduced` hợp lệ. Đây là **mở rộng contract của proxy object**. Tuy nhiên `CoreOperation` ban đầu không hề implements `Tagged`, vì vậy `Tagged.class.isAssignableFrom(CoreOperation.class)` vẫn false.

### 7.3. Tại sao phải kiểm riêng proxy và target?

Hai objects có thể triển khai hai contracts khác nhau:

~~~text
introduced: proxy class implements Operation, Tagged
core:       CoreOperation implements Operation only
~~~

Một test chỉ kiểm `introduced instanceof Tagged` có thể tạo **ảo giác class gốc được weave**. Ta cần kiểm `CoreOperation.class` riêng để biết chính xác đâu là ranh giới cấu trúc.

### 7.4. Hai lượt `process` có làm thay đổi class không?

Không. `CoreOperation.calls` bằng 2 chỉ chứng minh hai lần target thực thi; việc invocation handler ghi before/after không khiến `CoreOperation` nhận thêm public method hoặc interface. Đó là thay đổi **hành vi trên đường gọi proxy**.

### 7.5. Vì sao dùng `Tagged.class.isInstance(core)` thay vì `core instanceof Tagged`?

`CoreOperation` trong ví dụ là một class **`final`** đã biết chắc không implement `Tagged`. Với compile-time type `CoreOperation` cụ thể ấy, biểu thức `core instanceof Tagged` bị Java compiler từ chối vì không có khả năng chuyển đổi kiểu. `Tagged.class.isInstance(core)` là runtime reflective check trả false, tiện để demo điều kiện này mà chương trình vẫn biên dịch.

Điểm này cũng cho thấy **type compatibility là một hợp đồng compile-time**, không chỉ một chuỗi log runtime.

### 7.6. Giới hạn kỹ thuật khác của JDK dynamic proxy

- Nó được tạo để hiện thực **interfaces**, không sửa classfile `CoreOperation`.
- Lời gọi qua **raw target** sẽ bỏ qua behavior của proxy.
- Nó không tự can thiệp vào field access, private methods, constructors hay toàn bộ self-invocation.
- Caller giữ biến tĩnh kiểu `Operation` không thể gọi `tag()` trực tiếp nếu không cast sang `Tagged` hoặc dùng API trả đúng type.
- Proxy method dispatch, Object methods, checked exceptions và method name collisions cần được xử lý theo hợp đồng khi triển khai đầy đủ.
- Handler ở demo chỉ phục vụ `process`, `tag` và những `Object` methods được chuyển tiếp; nó không phải một interception framework tổng quát.

## 8. Introduction thực sự ở mức class khác adapter wrapper như thế nào?

### 8.1. Adapter/composition (wrapper)

~~~text
Caller -> Wrapper implements NewInterface
             |
             +-> delegates to OriginalTarget
OriginalTarget class unchanged
~~~

Caller chỉ thấy new contract **khi có wrapper instance đúng loại**. Nếu một thành phần giữ original target reference, nó không thấy interface bổ sung. Hai references có thể chỉ vào hai objects khác nhau, làm ảnh hưởng identity, `equals`, serialization và lifecycle.

### 8.2. Structural weaving vào class

~~~text
Source definition: OriginalTarget
          |
          +-- Weaving / transformation (nếu engine hỗ trợ)
          |
Woven class: OriginalTarget + introduced relationship/member
          |
New instances / type reflection on woven artifact
~~~

Nếu weaving thêm interface/member vào bytecode của **chính class được load**, những instance của **woven class** có thể hiện thực contract mới, không cần một wrapper riêng. Nhưng khả năng đó phụ thuộc bytecode verifier, classloader, thứ tự load và thời điểm transformation.

Không khẳng định mọi structural introduction đều xuất hiện như một method Java public thường trong mọi kiểu inspection. Có mô hình xây proxy contract, có mô hình thay đổi class, có mô hình chỉ thêm declaration trong compiler. Phải chỉ ra **công nghệ nào** và **điểm quan sát nào** đang nói đến.

### 8.3. Hành vi có thể được thêm qua code weaving mà không phải structural introduction

Một weaver có thể chèn instructions before/after vào thân method đã có, **không thêm một public interface mới**. Đây vẫn là thay đổi runtime behavior của method, dù class bytes bị biến đổi về mặt vật lý.

Do vậy:

> **“Bytecode bị thay đổi” không tự đồng nghĩa “structural crosscutting”.**

Tiêu chí là **contract/type shape và loại biến đổi có thể quan sát**, không chỉ việc bytes được patch.

## 9. Compile-time type checking và khả năng gọi member mới

Nếu một introduction làm một class có thêm method `tag()`, liệu source code khác có gọi `obj.tag()` được không? Câu trả lời không chỉ phụ thuộc JVM runtime mà còn **compiler đã thấy type bổ sung ở thời điểm compile hay chưa**.

Ba trường hợp:

1. **Source compile với structural information:** compiler/extension có thể biết introduction và cho phép compile lời gọi theo contract mới.
2. **Source compile trước introduction:** compiler Java thông thường chỉ thấy type declaration trước khi weave, có thể không chấp nhận `obj.tag()`.
3. **Proxy object với interface mới:** nếu API trả kiểu `Tagged` hoặc caller cast runtime thành `Tagged`, source compile theo contract của interface đó; `CoreOperation` gốc vẫn không có method.

Một quy trình build dùng weaving có thể yêu cầu **compiler/IDE hỗ trợ phù hợp**, generated sources/stubs, hoặc dependency ordering. Đừng viết code rồi mong javac tự hiểu một method dự kiến được thêm bởi một bước xử lý xảy ra sau javac.

### 9.1. Một ví dụ compile-time không hợp lệ cần nhận diện

~~~java
final class Core {}

// Không thể tự mặc định:
Core core = new Core();
// core.tag(); // javac không biết tag() nếu class không khai báo nó.
~~~

Đây là **đoạn minh họa**, không phải standalone demo hoàn chỉnh. Với code Java thông thường, `Core` không có member `tag()`. Nếu một công cụ external transformation về sau thêm method, caller được biên dịch trước đó vẫn cần có một **hợp đồng compile-time rõ ràng** hoặc gọi qua reflection/dynamic interface phù hợp.

### 9.2. Binary compatibility

Thay đổi type hierarchy hoặc thêm interface có thể ảnh hưởng:

- Method resolution và overload selection.
- Serialization metadata và reflective frameworks.
- Casting, `instanceof`, generic bounds.
- Conflicting default methods của interfaces.
- Return type/throws contract của method được giới thiệu.
- Những thư viện đã compile dựa trên class shape cũ.
- Access control giữa package/module/classloader.
- Incremental builds hoặc cached woven artifacts.

Không thể coi introduction chỉ là “thêm một method nhỏ”: nó có thể trở thành **thay đổi công khai về API** của nhiều types.

## 10. Thời điểm weaving và trạng thái class đang được nạp

Structural transformation phải phù hợp với lifecycle của class trong JVM:

~~~text
Source files / binaries
       |
       +-> Compile-time / post-compile weaving
       |
       +-> Load-time transformation (trước class definition)
       |
       v
JVM defines class + verifier checks
       |
       v
Runtime type identity and instances
~~~

**Weaving trước khi JVM định nghĩa class** thường có không gian biến đổi cấu trúc rộng hơn hot replacement của class đã được định nghĩa. Trong Java thông thường, **class redefinition/retransformation của JVM instrumentation API bị giới hạn với những thay đổi schema**, không thể mặc định thêm/bớt fields/methods, đổi signature hay inheritance sau khi class đã load. Khả năng công cụ mở rộng hoặc cơ chế JVM đặc biệt nếu có phải được xem xét riêng, không suy ra từ ngôn ngữ AOP tổng quát.

### 10.1. Classloading và class initialization khác nhau

- **Class definition/loading:** JVM tiếp nhận và định nghĩa type.
- **Class initialization:** chạy initialization logic theo quy tắc JVM vào những thời điểm sử dụng thích hợp.
- **Aspect instantiation:** tạo object chứa advice/state nếu mô hình cần.
- **Advice invocation:** thực thi hành vi tại join point.

Cắt ngang cấu trúc vào lúc load **không nhất thiết** gọi constructor của aspect, cũng không có nghĩa advice đã được kích hoạt. Đừng trộn bốn mốc này.

### 10.2. Nhiều classloaders

Class identity trong JVM được phân biệt bằng binary name và defining classloader. Một công cụ weaving chỉ áp dụng trong một loader có thể làm hai phiên bản “cùng tên” có class shapes khác nhau giữa các loader.

Với plugin/module runtime, kiểm đúng loader là điều thiết yếu. Một `ClassCastException` giữa hai types cùng tên có thể đến từ classloader mismatch, không nhất thiết do introduction viết sai.

## 11. Khả năng structural introduction và những giới hạn Java

Một engine có thể hỗ trợ một số phép thay đổi nhưng không phải tất cả. Cần cân nhắc:

### 11.1. `final` class hoặc `final` method

Một Java class `final` không thể có subclass Java thông thường. Điều đó ảnh hưởng subclass-proxy và một số cơ chế mixin. Nhưng không tự kết luận “một class final không thể được bytecode-transform bởi mọi cơ chế”: khả năng thực còn phụ thuộc loại thay đổi, verifier và công cụ.

Một `final` method không override được theo quy tắc Java, nhưng một weaver có thể thực hiện insertion bên trong method body đã tồn tại nếu engine và runtime cho phép. **Override** khác **bytecode weaving**.

### 11.2. Private members

Một JDK interface proxy không thêm private method vào target. Một structural weaver có thể có các quy tắc riêng cho introduced private members, nhưng không được ngụ ý mọi member mới có cùng access như method public. Module/access checks vẫn có hiệu lực.

### 11.3. Interface method conflicts

Giả sử class đã có method `tag()` với chữ ký/return không tương thích hoặc hai interfaces đưa ra default methods xung đột. Introduction không thể lờ đi contract của Java language và JVM verification. Cần xác định quy tắc precedence, conflict resolution hoặc từ chối transformation.

### 11.4. Tên field và state

Giới thiệu field vào nhiều objects tạo câu hỏi:

- Field có scope **per-instance** hay static?
- Giá trị mặc định, initialization, visibility là gì?
- Thứ tự initialization tương quan constructor và weaving ra sao?
- Thread safety nếu advice truy cập state mới từ nhiều threads?
- Serialization/copy/deserialization xử lý field mới thế nào?
- Reflection hay một ORM có hiểu field đó không?

Một field trên **wrapper** không phải field trên **target**, dù hai objects có thể chia sẻ reference.

### 11.5. Visibility và module boundary

Với Java modules, package access, exported/open packages, reflection và classloader có thể hạn chế khả năng thao tác. Không cố “cứ thêm interface” mà bỏ qua yêu cầu access và legal type relationships.

## 12. Phân biệt structural AOP với các kỹ thuật OOP quen thuộc

Không phải mọi hành động “thêm hành vi chung” đều là structural AOP.

| Kỹ thuật | Cách hoạt động | Tính chất cần phân biệt |
| --- | --- | --- |
| **Interface + implements trong source** | Tác giả sửa type trực tiếp | Cấu trúc đổi nhưng không nhất thiết dùng aspect |
| **Inheritance** | Subclass nhận/override members | Thay class hierarchy theo OOP thông thường |
| **Decorator / adapter** | Wrapper delegate sang object khác | Wrapper có contract khác target |
| **Dynamic proxy** | Object proxy hiện thực interfaces và dispatch invocation | Can thiệp lời gọi qua proxy; không sửa target class |
| **Mixin/trait composition** | Tổ hợp behaviors/contracts theo ngôn ngữ/công cụ | Có thể là static composition, không tự đồng nghĩa AOP |
| **Bytecode advice weaving** | Thêm hành vi vào một số join points | Có thể không thêm API mới |
| **Structural introduction/weaving** | Bổ sung declarations/type relationship theo capability engine | Thay contract type của artifact được dệt |
| **Source generator** | Sinh code/class/interface trước compile | Có thể tạo cấu trúc mới mà không dùng AOP |
| **Reflection/dynamic dispatch** | Gọi members tồn tại hoặc dispatch theo runtime | Không tự tạo member mới trong original class |

**Structural crosscutting là về mục đích modular hóa những thay đổi cấu trúc theo concern**, không đơn thuần là có code được sinh hoặc một adapter được tạo.

### 12.1. Khi nào adapter đủ thay cho introduction?

Nếu chỉ một subsystem cần `Tagged` trên một service instance, wrapper/adapter có thể đơn giản, tường minh và dễ test hơn thay đổi class thật. Nếu rất nhiều types trong cả codebase cần một contract chung ở cả compile time và reflection, structural introduction có thể giảm lặp lại — **nếu toolchain chịu được việc contract bị dệt**.

### 12.2. Không nhầm “mixin” với subclassing tự do

Một mixin có thể được hiện thực bằng delegation, generated subclass hoặc structural weaving. Mỗi phương án có type identity và lifecycle riêng. Muốn giải thích chính xác, phải nói **đối tượng nào** thêm interface và **đối tượng nào** giữ state.

## 13. Kết hợp behavioral và structural crosscutting đúng cách

Một kịch bản phối hợp:

~~~text
STRUCTURAL CONCERN:
  Nhóm domain objects đáp ứng Auditable
  Có contract auditId(): String

BEHAVIORAL CONCERN:
  Mỗi lần operation save() thực thi thành công
  ghi event dùng auditId() của object
~~~

Ưu điểm:

- Contract `Auditable` nói lên khả năng **một type có thể cung cấp gì**.
- Advice nói rõ **lúc nào audit phải xảy ra**.
- Không cần nhét logic audit vào tất cả method nghiệp vụ.

Nhưng vẫn cần kiểm:

1. `Auditable` được giới thiệu vào **đúng type** chưa?
2. Advice có can thiệp vào đúng `save` join point chưa?
3. Nếu object không có contract ở runtime, việc advice cast có lỗi không?
4. Khi `save()` thất bại sau partial commit, event phải mang trạng thái gì?
5. Nếu cache/short-circuit bỏ qua target, audit theo request hay target execution?
6. Nếu có hai aspects cùng introduce cùng tên method, precedence/collision xử lý ra sao?
7. State của introduced member có thread-safe và lifecycle đúng không?

**Không dùng việc “đã thêm Auditable” làm bằng chứng mọi thao tác save đều được audit.** Contract và runtime interception là hai bài test độc lập.

## 14. Pointcut matching không phải một structural declaration

Một pointcut theo method signature, annotation hoặc enclosing type có thể nhận diện nhiều join points, nhưng **việc chọn join point không bổ sung member vào class**.

Ví dụ ý niệm:

~~~text
SELECT Q = execution of all service methods matching a signature
RUN advice before Q
~~~

Đây là behavioral selection. Dù Q match hàng nghìn methods và module hóa concern rất tốt, type shapes không phải đã được biến đổi bởi chính rule Q.

Ngược lại:

~~~text
DECLARE all matching classes implement Trackable
INTRODUCE trackingId() implementation
~~~

Đây là structural intent. Nó không yêu cầu một method execution đã xảy ra để mô tả contract.

### 14.1. “Pointcut theo cấu trúc” là cụm dễ gây nhầm

Một pointcut có thể match dựa trên **cấu trúc tĩnh** (method/annotation/type) nhưng tác động của nó vẫn là **behavioral advice**. “Static matching criterion” không đồng nghĩa “structural crosscutting”.

Hãy hỏi rõ:

> Rule đang **chọn điểm thực thi để chạy advice**, hay **thay đổi khai báo/type relationship**?

Đây là phép phân biệt hữu ích nhất.

## 15. Crosscutting structure có ảnh hưởng runtime behavior không?

Có. Một introduced interface có thể:

- Thay điều kiện `instanceof` và đường dispatch.
- Khiến reflection nhìn thấy một method/member mới.
- Mở một overload/adapter path khác.
- Tạo state mới cần initialization và concurrent protection.
- Được một library khác phát hiện rồi kích hoạt workflow mới.

Vì vậy “structural” không có nghĩa “không ảnh hưởng hành vi”. Nó nghĩa **điểm tác động chủ đích là contract/hình dạng type**, sau đó runtime behavior có thể thay đổi như hệ quả.

Ngược lại, behavioral advice có thể được dệt bằng cách chèn instructions vào bytecode — bytes vật lý của class thay đổi — nhưng không nhất thiết thêm public API. **Phân loại theo semantics, không theo thao tác công cụ trên file bytes.**

## 16. Interface introduction: thiết kế API cẩn thận

Nếu thêm một interface vào nhiều types, tránh đặt contract quá lớn. Ví dụ `Auditable` có 15 methods vừa về security, logging, lifecycle và database sẽ buộc nhiều objects phải đáp ứng những trách nhiệm không đồng nhất.

Hướng tốt:

- Chỉ giới thiệu một contract có **ý nghĩa miền rõ ràng**.
- Ưu tiên interface nhỏ, không buộc target expose internals.
- Không giới thiệu mutators nếu concern chỉ cần read-only identity.
- Định nghĩa type-safe return values và failure semantics.
- Xác định có state per-instance không và ai sở hữu.
- Tránh đặt tên member dễ collision với target hiện có.
- Bảo toàn contract của interface trên tất cả types được chọn.
- Không làm lộ dữ liệu nhạy cảm qua method mới hoặc reflective access.
- Viết tài liệu type visibility: thấy từ compiler, reflection, wrapper hay woven class?

Nếu introduction có ý nghĩa công khai lâu dài, cần versioning và compatibility strategy như một API bình thường.

## 17. Rủi ro khi áp một structural aspect trên diện rộng

Một advice match nhiều methods có thể tạo overhead runtime; một structural introduction match nhiều classes có thể tác động **compile/runtime compatibility trên diện rộng**, kể cả những type không trực tiếp gọi method mới.

Một số hệ quả:

- **Name collision:** nhiều types có method trùng signature nhưng semantic khác.
- **Conflicting implementations:** hai introductions cung cấp cùng method.
- **Type ambiguity:** một overload mới được chọn khác sau compile.
- **Serialization change:** object graph khác khi thêm state.
- **Reflective behavior:** tool khám phá methods/annotations có thể chọn thêm thành viên.
- **ORM mapping:** một ORM có thể đọc field/property mới theo convention, gây mapping ngoài ý định.
- **Classloader identity:** woven và raw versions cùng tên nhưng từ loaders khác nhau.
- **Incremental build drift:** module này thấy intro, module khác dùng raw dependency.
- **Security surface:** interface mới mở capabilities trên một object vốn chỉ lộ API hẹp.
- **Rollback difficulty:** khôi phục code cũ có thể không giải được persisted data/schema đã tạo bởi contract mới.

**Đừng dùng structural introduction chỉ để tránh sửa một dòng khai báo interface**, nếu chi phí tooling và compatibility lớn hơn lợi ích modular hóa.

## 18. Runtime reflection: phải quan sát đúng đối tượng

Để kiểm tra có một contract mới, cần phân biệt:

1. **Type của target gốc** (`CoreOperation.class`).
2. **Class của proxy/wrapper** (`proxy.getClass()`).
3. **Type của class đã được weave** sau transformation.
4. **Type tĩnh mà code caller được compile dựa vào**.

Kết quả các kiểm tra có thể khác nhau.

Bảng đọc ví dụ Java:

| Đối tượng/kiểu | `Operation`? | `Tagged`? | Ý nghĩa |
| --- | --- | --- | --- |
| `CoreOperation` raw | Có | Không | Source class gốc |
| `behavior` proxy | Có | Không | Behavioral invocation interceptor |
| `introduced` proxy | Có | Có | Wrapper hiện thực thêm interface |
| `CoreOperation.class` sau tạo proxy | Có | Không | Tạo wrapper không sửa target class |

Một `instanceof` trên proxy không đủ để kết luận class gốc đã biến đổi. Một `getDeclaredMethods()` trên class gốc cũng không đủ để nói wrapper không có capability, vì đó là **hai objects/types khác nhau**.

### 18.1. Kiểm phương thức mới qua `getMethods()` hay `getDeclaredMethods()`?

- `getMethods()` thường trả public methods có thể truy cập theo quy tắc reflection, gồm inherited public methods.
- `getDeclaredMethods()` chỉ trả methods khai báo trực tiếp bởi class đó, bất kể visibility, không gồm inherited ones.

Nếu một method đến từ interface hoặc được giới thiệu theo kỹ thuật đặc biệt, vị trí quan sát có thể khác. Trước khi assert, cần biết contract reflection cụ thể của sản phẩm weaving/proxy.

## 19. Tests nên tách thành hai nhóm và một nhóm phối hợp

### 19.1. Behavior tests

| Test | Kỳ vọng |
| --- | --- |
| Join point match | Advice chạy tại đúng call/execution |
| Join point không match | Không có advice event |
| Normal return | Result bảo toàn hoặc transform đúng hợp đồng |
| Target throws | Lỗi gốc được ném lại hoặc chuyển đổi có chủ đích |
| Around short-circuit | Target không chạy nếu policy cho phép |
| Retry | Số lần target chạy đúng |
| Concurrency | Per-invocation state không bị ghi đè |
| Proxy bypass | Test đường gọi raw target và self-invocation |
| Async completion | Không nhầm method return với task completion |
| Performance | Latency/overhead phù hợp workload |

### 19.2. Structural tests

| Test | Kỳ vọng |
| --- | --- |
| Target được giới thiệu đúng type | Type thực sự có interface/member cần thiết |
| Target không được chọn | Không thêm interface/member ngoài ý định |
| Member signature | Name, parameters, return type, access đúng |
| Conflict detection | Collision bị từ chối hoặc giải quyết theo policy rõ |
| State initialization | Introduced fields/behavior có lifecycle đúng |
| Reflection | Reflection nhìn thấy contract theo khả năng engine |
| Compile-time use | Downstream sources compile theo API đã được expose |
| Binary compatibility | Client binaries tương thích theo contract |
| Serialization | Schema/serialVersion và backward compatibility được xét |
| Multiple classloaders | Đúng defining classloader và woven artifact |
| Incremental build | Không chạy nhầm raw/woven outputs |
| Unweave/redeploy | Không rò state/metadata/cached class assumptions |

### 19.3. Composition/integration tests

Khi structural `Auditable` và behavioral audit advice phối hợp:

1. Khởi tạo object qua **artifact thật** (proxy/weaver đúng).
2. Khẳng định object cung cấp `auditId()` qua contract cần dùng.
3. Gọi method nghiệp vụ được chọn và assert advice chạy đúng số lần.
4. Xác minh advice đọc đúng `auditId()` và ghi đúng event.
5. Gọi một method không được chọn và assert không bị audit nhầm.
6. Ép target throw để kiểm exception/audit policy.
7. Kiểm short-circuit, retry, async nếu có.
8. Kiểm raw target/direct calls khi dùng proxy.
9. Kiểm không có type được thêm không mong muốn.
10. Test trên chính classloader/build artifact sẽ release.

Một **unit test trực tiếp gọi hàm handler** không đủ chứng minh weaving hay introduction thực tế có hiệu lực.

## 20. Khi nào lựa chọn behavioral, structural hay mô hình khác?

### Chọn behavioral advice khi

- Mục tiêu là theo dõi/thay đổi **thời điểm thực thi**.
- Không cần thêm type contract mới.
- Caller API nên giữ nguyên.
- Concern liên quan logging, metrics, authorization tại boundary rõ.
- Có thể giải quyết bằng interceptor/proxy hoặc code weaving với phạm vi kiểm soát được.

### Cân nhắc structural introduction khi

- Nhiều types độc lập cần cùng một **interface/member contract** có ý nghĩa rõ.
- Đã có engine hỗ trợ đúng loại introduction, build/compiler tích hợp tốt.
- Cần contract xuất hiện ở **type level**, không chỉ wrapper riêng.
- Có kế hoạch xử lý collisions, versioning, reflection, serialization và classloaders.
- Mục tiêu modular hóa cấu trúc mang lợi ích đủ lớn so với chi phí tooling.

### Cân nhắc adapter, decorator hoặc sửa source trực tiếp khi

- Chỉ vài types cần mở rộng.
- Contract mới chỉ cần tại một API boundary.
- Tooling structural weaving làm type checking khó dự đoán.
- Cần type behavior tường minh, dễ IDE navigation và debugging.
- Module có public API ổn định không nên bị thay đổi ngầm.
- Cần wrapper per-instance mà không muốn rewrite class gốc.

**Không có quy tắc “structural AOP luôn hiện đại hơn”**. Giá trị của nó tùy nhu cầu và khả năng vận hành an toàn.

## 21. Những nhầm lẫn phổ biến

1. **“Aspect nào cũng chỉ có advice.”** Không nhất thiết; một số mô hình hỗ trợ introductions.
2. **“Thêm logging là structural vì bytecode thay đổi.”** Phân loại theo contract: logging là behavioral.
3. **“Behavioral advice không có state.”** Có thể có state/counters; không vì thế thành structural introduction.
4. **“Proxy mới implements Tagged thì CoreOperation cũng implements Tagged.”** Sai: hai objects có types khác nhau.
5. **“`instanceof` trên wrapper chứng minh target đã được weave.”** Không.
6. **“Giới thiệu method nghĩa chỉ chèn before advice.”** Không: thêm declaration/member là khác với interception.
7. **“Điều kiện pointcut dựa trên class là structural crosscutting.”** Không: đó có thể là static matching cho behavioral advice.
8. **“Structural introduction tự chạy mỗi khi method bị gọi.”** Type contract không phải một advice invocation.
9. **“Runtime classloader thấy method mới thì compiler mọi module tự biết.”** Không nếu compilation không có thông tin intro.
10. **“Cứ hot reload là thêm field/interface mới được.”** Java redefinition thường có giới hạn schema.
11. **“`final` method không bao giờ bị woven behavior.”** Override và bytecode instrumentation là hai cơ chế khác nhau.
12. **“Một intro interface không có breaking changes.”** Nó có thể ảnh hưởng overloads, reflection, serialization.
13. **“Structural weaving và adapter luôn tương đương.”** Không: object identity/type contract và coverage khác.
14. **“Aspect instantiation xảy ra ở lúc weave.”** Không có quy tắc chung.
15. **“Một class name xác định duy nhất type trong JVM.”** Còn defining classloader.
16. **“Method được thêm ở wrapper cũng thuộc original class reflection.”** Không.
17. **“Giới thiệu field không ảnh hưởng concurrency.”** Introduced state vẫn cần thread safety nếu dùng chung.
18. **“Mọi bytecode weaving tool hỗ trợ inter-type declarations.”** Không.
19. **“Behavioral và structural không thể kết hợp.”** Chúng có thể bổ sung cho nhau.
20. **“Có Auditable contract nghĩa mọi save đã được audit.”** Cần behavioral interception riêng.
21. **“Test mỗi advice đủ chứng minh structural member tồn tại.”** Không; cần type/reflective/build tests.
22. **“Chỉ cần test proxy, không cần test raw target.”** Khi phân biệt introduction, cả hai cực kỳ quan trọng.
23. **“Mọi structural declaration đều visible như Java source member.”** Phụ thuộc engine/build/classloader.
24. **“Cứ thêm interface là auto an toàn.”** Contract có thể mở capability trái policy.
25. **“Mọi class loader đều thấy cùng woven class.”** Không nếu instrumentation scope khác.
26. **“Structural introduction thay cả database schema.”** Không tự động; đây là type/program structure, không phải migration dữ liệu.
27. **“Một method `tag()` được gọi tức là aspect đang hoạt động.”** Có thể chỉ là method thường của proxy/target; phải phân biệt.
28. **“Thay đổi type contract không cần versioning.”** Đặc biệt nguy hiểm với public APIs.

## 22. Câu hỏi tự kiểm tra

**Câu 1.** Advice ghi `before/after` cho `process()` có phải structural crosscutting không?  
**Đáp án:** Không; đó là behavioral crosscutting tại join point.

**Câu 2.** Một introduction khiến nhiều classes implements `Trackable` thuộc dạng nào?  
**Đáp án:** Structural crosscutting, nếu nó thật sự thay type contract của classes được chọn.

**Câu 3.** `Operation` proxy cũng implements `Tagged` thì `CoreOperation` gốc có tự implements `Tagged` không?  
**Đáp án:** Không. Proxy và core có runtime classes riêng.

**Câu 4.** Bytecode được instrument before/after làm class file khác đi; có tự là structural AOP không?  
**Đáp án:** Không. Việc sửa instructions để can thiệp hành vi không nhất thiết thêm member/type relationship.

**Câu 5.** Có thể dùng chỉ tên method signature để quyết định advice chạy; đó là structural pointcut hay behavioral?  
**Đáp án:** Matching có thể dựa trên đặc tính tĩnh, nhưng nếu kết quả chỉ chọn join point để chạy advice thì tác động là behavioral.

**Câu 6.** Vì sao compiler có thể từ chối `core.tag()` dù tool dự kiến weave `tag()` về sau?  
**Đáp án:** Compiler không có contract method ở thời điểm compile; cần tooling/type information hoặc gọi qua interface/API đã biết.

**Câu 7.** `Tagged.class.isInstance(core)` trả false trong ví dụ vì sao?  
**Đáp án:** Class `CoreOperation` gốc không implements `Tagged`; proxy introduction không sửa target.

**Câu 8.** Một structural introduction tạo field mutable per-object, có phải nghĩ đến thread safety không?  
**Đáp án:** Có; nếu nhiều threads truy cập cùng instance/state, vẫn có race như field thông thường.

**Câu 9.** `final` class có thể được subclass proxy bình thường không?  
**Đáp án:** Không; nhưng giới hạn đó không tự mô tả mọi kiểu bytecode weaving.

**Câu 10.** Structural introduction có thể ảnh hưởng overload resolution không?  
**Đáp án:** Có, nếu compile-time type/member set thay đổi; cần kiểm compilation compatibility.

**Câu 11.** Chỉ test `getMethods()` trên proxy có chứng minh woven target type đã đổi không?  
**Đáp án:** Không. Cần test woven target class hoặc loại artifact đang được giới thiệu.

**Câu 12.** Khi nào một wrapper/adapter có thể phù hợp hơn introduction?  
**Đáp án:** Khi chỉ cần interface mới trên một object boundary và muốn tránh thay type gốc/toolchain phức tạp.

**Câu 13.** Một aspect giới thiệu `Auditable` và advice thực hiện audit khi `save()` thành công. Cần mấy nhóm test?  
**Đáp án:** Test structural contract, behavioral join points, và integration test sự phối hợp.

**Câu 14.** Load-time transformation có nghĩa mọi aspect instance đã được tạo ở class load không?  
**Đáp án:** Không. Class transformation, class initialization, aspect instantiation và advice invocation là các mốc khác nhau.

## 23. Checklist thiết kế và QA

### Trước khi chọn cơ chế

- [ ] Mục tiêu là can thiệp **invocation** hay thêm **type contract**?
- [ ] Thêm interface cho **wrapper** có đủ hay phải bổ sung vào **woven target class**?
- [ ] Source/compiler/caller cần nhìn thấy API mới tại thời điểm nào?
- [ ] Engine thực tế hỗ trợ đúng loại introduction nào?
- [ ] Điểm matching có đủ hẹp, không mở rộng thay đổi ngoài ý định?
- [ ] Cần thay đổi type thật hay decorator/composition rõ ràng đã đủ?

### Khi triển khai

- [ ] Xác định runtime type và classloader thực tế của target và proxy.
- [ ] Tránh name/signature collisions và conflicting interface defaults.
- [ ] Xử lý member visibility, module access và method dispatch.
- [ ] Định nghĩa state initialization, lifecycle và thread safety của introduced members.
- [ ] Định nghĩa pointcut/advice precedence riêng cho behavioral logic.
- [ ] Kiểm thử raw target path và self-invocation nếu dùng proxy.
- [ ] Tài liệu hóa public API/versioning của contract mới.
- [ ] Không giả định class đã định nghĩa có thể hot-redefine schema tự do.

### Khi kiểm thử/phát hành

- [ ] Unit test behavior before/after/around và exception path.
- [ ] Test interface membership đúng **đối tượng/kiểu**.
- [ ] Test member signature và reflective visibility.
- [ ] Test classes không được chọn không bị thêm member.
- [ ] Test build/compile consumers trước và sau introduction theo contract.
- [ ] Test correct woven artifact hoặc proxy factory ở môi trường phát hành.
- [ ] Kiểm type identity ở nhiều classloaders khi ứng dụng có plugins.
- [ ] Kiểm serialization/ORM discovery nếu dựa trên reflection.
- [ ] Test quan hệ structural + behavioral bằng workflow end-to-end.
- [ ] Quan sát hiệu năng runtime và tác động tương thích của structural changes.

## 24. Tổng kết

**Cắt ngang hành vi** mô tả **điều gì xảy ra khi join point thực thi**: before, after, around, tracing, policy, retry, caching, result và exception handling. **Cắt ngang cấu trúc** mô tả **type/module có thể mang thêm những khai báo hoặc quan hệ nào**: interface, member hoặc loại introduction khác mà engine thực sự hỗ trợ.

Sự phân biệt không nằm ở việc có sửa bytecode hay không, vì một behavioral weaver cũng có thể sửa classfile để chèn instructions. Nó nằm ở **tác động chủ đích lên execution flow hay type contract**. Một proxy có thể cung cấp interface bổ sung cho **wrapper**, nhưng điều đó không làm class target gốc tự biến thành một woven type mới. Structural weaving thật cần kiểm timing, compiler visibility, classloader, API compatibility, access rules và reflective shape.

Trong thực hành, hãy **xác định đúng đối tượng được bổ sung khả năng, ranh giới can thiệp và công nghệ đang dùng**, rồi viết test riêng cho behavior, riêng cho structure và test tích hợp khi hai kiểu crosscutting phối hợp. Đây là cơ sở để dùng AOP như một công cụ modular hóa có chủ đích thay vì lẫn lộn giữa “gọi thêm code” và “thay đổi hình dạng chương trình”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-002">Bổ sung phương thức, thuộc tính hoặc giao diện khi công cụ hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Bổ sung phương thức, thuộc tính hoặc giao diện khi công cụ hỗ trợ

## 1. Từ can thiệp hành vi đến thay đổi cấu trúc

Trong **Aspect-Oriented Programming (AOP)**, người học thường bắt đầu với pointcut và advice: chọn các join point, sau đó thực hiện logic trước, sau hoặc bao quanh một hoạt động vốn đã tồn tại. Đó là **can thiệp vào hành vi thực thi**. Tuy nhiên, một số mô hình/công cụ AOP còn hỗ trợ **bổ sung cấu trúc** cho chương trình: giới thiệu phương thức mới, thuộc tính/trạng thái mới hoặc giao diện/supertypes mới cho những kiểu được chọn.

Nhóm khả năng này thường được gọi là **introduction**, **inter-type declaration (ITD)**, **mixin**, **type/member introduction** hoặc **structural crosscutting** tùy ngữ cảnh công cụ. Các thuật ngữ liên hệ với nhau nhưng **không đồng nghĩa tuyệt đối**: một interface proxy có thể “introduce” một giao diện chỉ trên object proxy; một code generator có thể tạo subclass với methods/fields bổ sung; một source/bytecode weaver có thể thực sự thay đổi định nghĩa class nếu engine và giai đoạn xử lý hỗ trợ. Không được gộp các mô hình này thành cùng một cơ chế.

### Câu hỏi trung tâm

> Một aspect có thể khiến đối tượng “có thêm khả năng” theo nghĩa nào: **caller nhìn thấy một giao diện mới trên wrapper**, **runtime tạo một lớp con mới**, hay **kiểu đích thật sự có thêm khai báo trong chương trình đã được weave**?

Đây là câu hỏi về **structural capability**, không phải chỉ đổi thứ tự advice. Việc “công cụ hỗ trợ” là điều kiện then chốt: nhiều cơ chế AOP chỉ intercept method có sẵn và **không cung cấp** cách bổ sung field, method hay interface vào class gốc.

### Mục tiêu bài học

Sau bài học, bạn có thể:

1. Phân biệt **behavioral crosscutting** với **structural crosscutting**.
2. Giải thích interface introduction, mixin, inter-type method, inter-type field và thêm quan hệ implements/extends theo khả năng engine.
3. Phân biệt thay đổi **type gốc**, **subclass**, **proxy**, **decorator**, **metadata registry** và **mã generated**.
4. Hiểu tác động lên Java type system, compile-time visibility, runtime casts, reflection, generics, serialization và binary compatibility.
5. Đánh giá các giới hạn của final classes/methods, records, sealed hierarchies, constructors, visibility và JVM class loading.
6. Thiết kế state/lifecycle cho những thuộc tính được bổ sung, nhất là shared/per-object state và thread safety.
7. Giải quyết conflict khi nhiều aspects cùng khai báo method hoặc field, trùng signature/erasure và vấn đề thứ tự.
8. Chọn lúc nào nên introduction, lúc nào nên composition/decorator hoặc mở rộng API theo cách thông thường.
9. Chạy một ví dụ **Java 17 hoàn chỉnh** chứng minh JDK dynamic proxy triển khai thêm interface và methods trên proxy, **không sửa bytecode hay field của target**.
10. Xây dựng ma trận test xác nhận cả **hình dạng kiểu** và **hợp đồng hành vi** sau áp dụng aspect.

**Phạm vi:** Đây là kiến thức Aspect/AOP cơ bản và nâng cao **nói chung**. Không suy hành vi hoặc cú pháp của một AOP engine thành quy tắc chung cho mọi công cụ.

## 2. Structural crosscutting là gì?

Nếu một advice ghi log tại mọi lần gọi `save()`, nó bổ sung **hành vi quan sát** nhưng không nhất thiết làm public API của class thay đổi. Nếu một công cụ làm cho những object thuộc loại `Document` có thêm `getVersion()`, `setTag(...)` hoặc implements `Auditable`, ta đang nói tới một **thay đổi về cấu trúc** ở cấp thích hợp.

Ví dụ mong muốn:

~~~text
Trước:
    Document
       + title()
       + save()

Sau khi có một phép introduction phù hợp:
    Document hoặc một kiểu thay thế được công cụ sinh
       + title()
       + save()
       + tag()          <-- thành viên mới
       + setTag(value)  <-- thành viên mới
       + state tag      <-- trạng thái được cung cấp bởi intro
       implements Tagged
~~~

Sơ đồ là **mục tiêu khái niệm**, không phải lời khẳng định class `Document` trong mọi cách triển khai sẽ thực sự nhận field hay thay đổi `Class` object. Muốn kết luận cần kiểm tra **đối tượng mà caller sử dụng**, **binary thực sự được nạp**, và **metadata mà reflection nhìn thấy**.

### 2.1. Các phép thay đổi cấu trúc thường gặp

| Khả năng | Nghĩa | Điều kiện quan trọng |
| --- | --- | --- |
| **Introduce method** | Thêm callable operation cho một kiểu/đối tượng được chọn. | Visibility, name/signature, implementation, bytecode/API compatibility. |
| **Introduce field/property** | Thêm state hoặc accessor được liên kết với đối tượng. | Owner, khởi tạo, thread safety, lifecycle, reflection. |
| **Introduce interface** | Làm đối tượng/kiểu thỏa một protocol bổ sung. | Phải có implementations cho những abstract methods cần thiết. |
| **Introduce superclass/parent relation** | Thay đổi quan hệ kiểu theo khả năng tool. | Java inheritance restrictions, constructors, verifier, conflicts. |
| **Introduce implementation/mixin** | Cung cấp hành vi và đôi khi state từ một component khác. | Delegation vs weaving, per-instance vs shared state. |
| **Introduce metadata/annotation** | Thay đổi metadata mà tool có thể đọc. | Retention, runtime visibility, compile/load time, lookup site. |

Không phải mọi công cụ hỗ trợ mọi dòng. Có công cụ hỗ trợ chỉ intercept existing methods; có công cụ hỗ trợ chỉ interface introductions; có công cụ hỗ trợ nhiều dạng inter-type declarations.

### 2.2. Thêm một interface khác thêm các phương thức vào target gốc

Một proxy có thể implement `Tagged` và cung cấp `tag()` ngay trong handler dù `Target.class` không có method đó. Caller cast **proxy** sang `Tagged` thành công; cast **target gốc** sang `Tagged` vẫn thất bại.

Ngược lại, nếu source/bytecode weaving thực sự đưa interface vào class target và thêm method implementations, caller sử dụng class đã weave có thể thấy những thành viên đó tùy compile/runtime visibility. Đây là hai semantics khác nhau.

### 2.3. Field thật khác “property” bên ngoài

Một `Map<Target,State>` hoặc một field trong proxy handler có thể tạo cảm giác “target có thuộc tính tag”. Nhưng reflection trên `Target.class.getDeclaredFields()` sẽ **không** hiện một field mới chỉ nhờ map/handler tồn tại. Đừng gọi proxy-side association là **field được chèn vào class target**.

## 3. Cơ chế 1 — Interface proxy và delegation

Với JDK dynamic proxy, Java có thể tạo một proxy class runtime implements **một hoặc nhiều interfaces** được cung cấp cho `Proxy.newProxyInstance`. `InvocationHandler` quyết định cách xử lý invocation của từng method.

~~~text
Caller có Reader + Tagged
            |
            v
Proxy implements Reader, Tagged
            |
            +--> read(key)   -> delegate tới Reader target
            |
            +--> tag()       -> đọc state của handler
            +--> setTag(...) -> ghi state của handler
            |
            v
Original Reader target
   + read(key)
   (không tự có Tagged, tag/setTag)
~~~

Khả năng này thường được dùng để minh họa **interface introduction qua proxy**. Tuy nhiên phải phát biểu chính xác:

- Object proxy có thêm **interface và callable methods của interface**.
- Object target gốc không bị thay class và không được chèn field.
- Implementation cho method mới nằm ở handler/delegate của proxy.
- Source code của client muốn gọi `tag()` phải biết kiểu `Tagged` hoặc kiểm tra/cast động.
- Invocation đi thẳng vào target, không qua proxy, không thấy introduced behavior của wrapper.
- Interface method selection và equality semantics cần được test.

### 3.1. Điều JDK dynamic proxy không làm

JDK interface proxy **không** tạo một subclass tùy ý của concrete target class. Nó không thay class definition của target đã tồn tại, không thêm một public method trực tiếp vào `Target.class`, và không khiến phép `(ConcreteTarget)proxy` luôn hợp lệ.

Nó cũng không tự expose **một method mới không thuộc interfaces được khai báo** như một callable typed Java method cho client. Muốn giới thiệu operation cho proxy theo cách này, cần interface mang method signature tương ứng.

### 3.2. Handler phải thực thi được cả hai nhóm methods

Một handler delegate mọi method tới target bằng `method.invoke(target,args)` sẽ lỗi khi method thuộc interface mới nhưng target **không implement** interface đó. Phải phân nhánh:
- Methods thuộc interface gốc → gọi target.
- Methods thuộc interface mới → gọi implementation/mixin state thích hợp.
- `equals/hashCode/toString` → quyết định semantics rõ.
- Nếu trùng chữ ký giữa interfaces → hiểu cách proxy lựa chọn `Method` và dispatch; không dựa mù vào `getDeclaringClass()` khi signature overlap.

Nếu không xử lý, giới thiệu interface trên giấy nhưng methods không chạy đúng là một **lỗi contract**, không phải introduction thành công.

## 4. Cơ chế 2 — Decorator hoặc adapter hiện thực tường minh

Một class thông thường có thể implements nhiều interfaces rồi chuyển tiếp operations:

~~~java
interface Reader {
    String read(String key);
}

interface Tagged {
    String tag();
}

final class TaggedReader implements Reader, Tagged {
    private final Reader target;
    private final String tag;

    TaggedReader(Reader target, String tag) {
        this.target = target;
        this.tag = tag;
    }

    public String read(String key) {
        return target.read(key);
    }

    public String tag() {
        return tag;
    }
}
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. Nó **không đòi engine AOP có introduction primitive**. Điểm mạnh:
- Type relationship thấy rõ lúc compile.
- Implementation dễ kiểm thử và debug.
- State ownership theo instance rõ.
- Có thể dùng IDE/refactoring/lint bình thường.
- Không có runtime weave/dependency đặc biệt.

Điểm đánh đổi:
- Phải tự viết/wire wrapper và forwarding logic.
- Caller phải nhận đúng decorator thay vì target.
- Target class gốc vẫn không có interface mới.
- Constructor/decorator chain và resource ownership cần thiết kế rõ.
- Nếu số loại wrapper lớn có thể tăng boilerplate.

**Khi nào ưu tiên?** Khi chỉ có vài loại, yêu cầu API rất rõ, không cần crosscutting scope lớn và lợi ích kiểm soát compile time cao hơn tự động introduction.

## 5. Cơ chế 3 — Subclass hoặc generated subclass

Một code generator/runtime library có thể tạo subclass:

~~~text
BaseTarget
    ^
    |
GeneratedTargetProxy
   + methods override/intercept
   + methods mới nếu generator hỗ trợ
   + fields bổ sung trong subclass
~~~

Nếu subclass hợp lệ, proxy có thể assignable tới base class. Nhưng phải phân biệt:
- Một **field mới trong subclass** không phải field được đưa vào định nghĩa **base class**.
- Một method mới trong subclass có thể cần caller biết generated subtype hoặc một interface.
- Method `private`, `final` và constructor không có override semantics giống virtual methods.
- Một class `final` không thể được subclass.
- Object state đã tồn tại và quá trình constructor/delegation có thể khác nhau giữa các generator.

### 5.1. `final`, records và sealed hierarchy

- **Final class:** không thể tạo subclass Java hợp lệ của final class. Nhưng vẫn có thể bọc qua interface proxy/decorator nếu abstraction cho phép.
- **Final method:** subclass không thể override final method. Bổ sung một method **khác tên/chữ ký** là câu chuyện riêng nhưng vẫn bị các ràng buộc class.
- **Record:** record class ngầm định final; không thể tự nhiên tạo subclass của record như một base class bình thường.
- **Sealed class/interface:** quan hệ subtype/implementation bị kiểm soát bởi permitted subtypes. Một proxy không thể giả định được phép implement mọi sealed interface hoặc subclass sealed type ngoài danh sách/quy tắc hợp lệ.
- **Private members:** reflection có thể tìm thấy declarations theo quyền truy cập nhưng điều đó không biến private method thành một điểm override bình thường.

Với source/bytecode weaving, các giới hạn, cách công cụ xử lý và giai đoạn thay đổi có thể khác; không tự suy “final luôn cấm mọi dạng AOP”.

### 5.2. Constructor và object initialization

Một generated subtype phải có chiến lược khởi tạo superclass state hợp lệ. Không thể giả định constructor của target luôn được chạy đúng một lần trên cùng object theo mọi loại proxy. Những invariant được thiết lập trong constructor, side effects khi tạo object và `this` escaping trong constructor cần được test ở engine thật.

Không nên dùng introduction chỉ để tránh thiết kế constructor/resource lifecycle có hợp đồng rõ.

## 6. Cơ chế 4 — Source hoặc bytecode weaving cấu trúc

Một số engine AOP hỗ trợ **structural introductions thật sự**: tại quá trình source transformation, compile-time weaving, post-compile processing hoặc load-time weaving, nó có thể thay đổi class artifact để có:
- Method implementations.
- Fields mới.
- Interface declarations.
- Một số metadata/quan hệ type khác theo khả năng công cụ.

Trường hợp này khác JDK interface proxy: thay đổi có thể được phản ánh trong **binary class definition được nạp**, không chỉ wrapper object.

### 6.1. Bài toán compile-time visibility

Có ít nhất ba góc nhìn cần kiểm:

1. **Source gốc:** `Target.java` có khai báo member đó không?
2. **Compiler của client:** compiler có biết member/interface được introduced nhờ toolchain không?
3. **Runtime:** class được nạp có binary chứa introduced member không?

Một class artifact đã weave có thể chứa method mà source ban đầu không có, nhưng một file client compile bằng javac theo class path/compile pipeline khác có thể không nhìn thấy member như mong muốn. Ngược lại, client compile với một artifact đã weave nhưng runtime lại nạp **bản chưa weave** có thể gặp lỗi linkage hoặc cast.

Không nói “runtime đã introduce nên mọi source đang compile tự động biết method mới”. Hãy kiểm build inputs, compile order, classpath/module path và artifact deployment.

### 6.2. Class loading và class identity

Một `Class` object phản ánh **class đã được JVM định nghĩa** bởi loader. Nếu biến đổi xảy ra trước khi class được define, structure mới có thể trở thành một phần của định nghĩa ấy. Nếu class đã được load, những khả năng thay đổi cấu trúc qua instrumentation/redefinition bị giới hạn bởi JVM và công cụ; không được tự suy có thể thêm trường/phương thức/interface vào một class đã load tùy ý.

Cùng tên class nhưng được nạp bởi hai classloaders khác nhau vẫn là **hai runtime types khác nhau**. Weaving/configuration theo classloader có thể dẫn đến một module nhìn thấy introduced members còn module khác thì không.

### 6.3. Build consistency

Đảm bảo:
- Đúng công cụ biến đổi được chạy trong build.
- Sources và generated artifacts khớp phiên bản.
- Compiler của client dùng đúng binary/API view.
- Production nạp cùng artifact (hoặc được biến đổi tương đương ở load time).
- Cache incremental build được invalidated đúng khi introduction thay đổi.
- Có integration tests chạy với artifact cuối, không chỉ class trong IDE.

### 6.4. Vì sao weaving không tự làm đúng mọi thứ?

Thêm member ở binary level chỉ là **type/structure capability**. Nó chưa bảo đảm:
- Giá trị trả về đúng domain contract.
- Field có owner/scope phù hợp.
- Thread safety.
- Initialization xảy ra đúng thời điểm.
- Serialization/binary compatibility.
- Không có conflict với các aspect khác.
- Security/lifecycle không bị bypass.

## 7. Giới thiệu giao diện: chỉ “implements” là chưa đủ

Giả sử muốn `Invoice` có thêm khả năng:

~~~java
interface Auditable {
    String auditId();
}
~~~

Một công cụ đưa `implements Auditable` vào kiểu mà không có implementation cho `auditId()` sẽ phải giải quyết nghĩa của một abstract method còn thiếu. Tùy công cụ, phải introduce implementation, trỏ sang một delegate/mixin hoặc có một default implementation hợp lệ.

### 7.1. Interface method mặc định

Java interfaces có thể có `default` methods. Nhưng chúng không tự giải quyết mọi conflict:
- Hai interfaces có default methods cùng signature có thể cần phân giải tường minh.
- Một class method hợp lệ có thể ưu tiên hơn default implementation theo luật Java.
- Với JDK dynamic proxy, invocation của default method vẫn có thể đi qua handler; việc gọi default implementation là một thao tác có API/semantics riêng, không tự xảy ra nếu handler chỉ trả null.
- Nếu method introduced có logic dựa vào field target mà target không có, phải tạo state/bridge phù hợp.

### 7.2. Client phải có typing phù hợp

Nếu chỉ một runtime proxy implements `Auditable`, biến:

~~~java
Reader reader = ...;
~~~

không tự có method `auditId()` trong compile-time type `Reader`. Client cần:
- Nhận reference typed as `Auditable`.
- Kiểm tra `instanceof Auditable` rồi dùng pattern variable/cast.
- Hoặc có API/factory trả type thể hiện cả hai capabilities.

Một số weaving toolchains có thể tích hợp type model để compiler thấy introduced declarations, nhưng phải dùng đúng toolchain và artifacts.

### 7.3. Một subtype mới có ý nghĩa nghiệp vụ?

“Đối tượng hỗ trợ `Auditable`” là một lời hứa hành vi. Nếu method `auditId()` luôn trả null hay một ID không ổn định trong khi contract hứa ID bền vững, chỉ thêm interface chưa tạo substitutability. Introduction phải có **behavioral contract tests** tương tự implementation bình thường.

## 8. Giới thiệu phương thức: tên, chữ ký và overload

Một inter-type method có thể thêm operation như `tag()`, `setTag()`, `validate()` hoặc `snapshot()` theo loại target được chọn. Những điểm phải xử lý:

1. **Tên method** có thể đã tồn tại trong target.
2. **Parameter signature** có thể trùng method hiện hữu hoặc method từ aspect khác.
3. **Return type** có thể không tương thích khi override/implement.
4. **Generic erasure** có thể khiến hai declarations tưởng khác nhau ở source thành **name clash** ở JVM.
5. **Visibility** có thể làm một member không thể expose theo mong muốn.
6. **Exception declaration** có thể không phù hợp hợp đồng của interface.
7. **Bridge methods** và covariant returns có thể ảnh hưởng reflection/dispatch.
8. **Default methods** có thể xung đột trong multiple-interface composition.
9. **Varargs và overloaded methods** cần matching/call-site resolution chính xác.
10. **Method name mang nghĩa domain:** adding `validate()` không nên vô tình đổi cách một existing API gọi validation.

### 8.1. Trùng chữ ký phải có chính sách xung đột

Ví dụ target đã có:

~~~java
String tag();
~~~

Một aspect lại muốn introduce `String tag()`. Không thể chỉ nói “introduction thắng method cũ” cho mọi engine. Cần xem công cụ:
- Từ chối do duplicate/conflict.
- Cho phép implementation override theo precedence nếu có hợp đồng cụ thể.
- Chọn implementation khác theo binding rule.
- Buộc đổi tên/đổi interface hoặc tái cấu trúc.

Nếu hai aspects cùng introduce method, cần quyết định ai sở hữu, ai override, hoặc fail fast. Đừng dựa vào classpath scan/weaving order không được công bố.

### 8.2. Không dựa vào return type để phân biệt overload

Trong Java source, overload không thể chỉ khác return type. Một introduction có `String status()` và existing `int status()` không phải hai methods có thể cùng tồn tại như hai lựa chọn overload source bình thường. Có thể xuất hiện những vấn đề JVM descriptors/bridge phức tạp, nhưng công cụ phải giải quyết chúng theo luật verifier/compiler và API contract, không tự “thêm cả hai” một cách vô điều kiện.

### 8.3. Generics và erasure

Hai declarations:

~~~java
void process(java.util.List<String> items);
void process(java.util.List<Integer> items);
~~~

Đây là **mã minh họa xung đột**, không phải mã Java hợp lệ trong cùng class: sau type erasure, cả hai có cùng erasure `process(List)`. Introduction phải được kiểm tra sau erasure chứ không chỉ so text của generic arguments.

### 8.4. Phương thức introduced cần được test như API thực

Gọi `tag()` không chỉ kiểm “có callable”. Cần test:
- Giá trị khởi tạo hợp lệ.
- Input null/invalid.
- Hợp đồng exception.
- State isolation giữa objects.
- Thread safety khi set/get đồng thời.
- Visibility với clients biên dịch theo artifact cuối.
- Behavior sau serialization/deserialization khi hỗ trợ.

## 9. Giới thiệu thuộc tính hoặc field: state thuộc về ai?

Thêm một field được dệt vào target có semantics khác việc tạo một field trong handler hoặc registry.

### 9.1. Trạng thái có thể gắn theo ba nơi

~~~text
(A) Woven target field
      Target+introducedField

(B) Proxy/decorator field
      Proxy(tag) -> Target(no field)

(C) External association
      Registry[Target identity] -> tagState
~~~

Cả ba có thể hỗ trợ operations `tag()`/`setTag()` ở một API phù hợp nhưng có đặc tính khác:

| Thuộc tính | Field dệt trong target | State của proxy | State trong registry |
| --- | --- | --- | --- |
| Owner trực tiếp | Woven target instance. | Proxy/handler instance. | Registry/association. |
| Hai proxies cùng target | Có thể cùng state khi đọc field trên target. | Có thể tách state theo proxy. | Tùy key theo target hay proxy. |
| Reflection trên target class | Có thể thấy field nếu thực sự dệt. | Không thấy field của proxy. | Không thấy registry state như field. |
| Cleanup | Theo target + resource lifecycle. | Theo proxy lifetime. | Cần remove/eviction. |
| Risk | Initialization, binary/schema changes. | Nhiều wrappers, ownership. | Retention, keys, concurrency. |

Không có lựa chọn luôn tốt nhất. Điều quan trọng là **đơn vị identity/lifetime của dữ liệu được introduction**.

### 9.2. Per-instance hay shared?

Một introduced field **instance** thường tách dữ liệu giữa các target instances khi đúng model; một field **static** là state cấp class/runtime type, có thể dùng chung nhiều objects. Nhưng classloader và weaving scope có thể khiến cùng tên class tạo nhiều bản riêng.

Nếu muốn mỗi target có ID độc lập, một static mutable field `currentAuditId` là sai. Nếu muốn shared immutable policy, lặp policy vào mỗi target có thể không cần thiết.

### 9.3. Field mới cần initialization

Một field kiểu `String tag` có default JVM là null nếu không được khởi tạo tường minh; với primitive `int`, default 0. Nhưng **default JVM không nhất thiết là business default đúng**. Nếu interface hứa `tag()` luôn non-null, phải thiết kế initializer/constructor hook/lazy-safe initialization hoặc accessor logic có contract phù hợp.

### 9.4. Thread safety

Nếu target singleton được nhiều threads dùng, introduced instance field vẫn shared bởi nhiều calls. Một `int updates++` không atomic; một `StringBuilder` được chia sẻ không an toàn concurrent; `volatile` hỗ trợ visibility cho một field nhưng không tự biến compound operation thành atomic.

Dùng `AtomicInteger`, synchronization, immutable snapshots hoặc state riêng mỗi invocation theo yêu cầu. Per-target không đồng nghĩa per-thread.

### 9.5. Lifecycle/resource

Nếu introduced field giữ `InputStream`, listener, thread, cache hoặc lock, cần owner/close/unregister rõ. Khi field thật gắn với target, nó có thể giữ resources đến khi target không còn reachable; GC không phải một lifecycle hook xác định để đóng tài nguyên ngoài heap.

Nếu registry giữ strong keys từ target tới state, registry có thể giữ target sống vô hạn. Nếu proxy có trường giữ target, proxy lifetime có thể kéo dài target lifetime. Đừng coi introduction là miễn phí về memory.

## 10. Ví dụ Java 17 runnable: một proxy giới thiệu interface Tagged

Ví dụ sau cố ý **không dùng weaver đặc biệt**, chỉ sử dụng API Java 17. Nó minh họa **giới thiệu giao diện trên proxy**:
- Target `BasicReader` chỉ implements `Reader`.
- Proxy được tạo implements cả `Reader` và `Tagged`.
- `read` đi tới target; `tag`/`setTag` được xử lý trong invocation handler.
- Hai proxies bọc **cùng target** nhưng mỗi handler có state `tag` riêng.
- Object methods `equals`, `hashCode`, `toString` được xử lý với semantics **identity của proxy**, không cố làm proxy và target có cùng identity.
- Lỗi từ target được gỡ khỏi `InvocationTargetException`, giữ exception gốc cho caller.
- Reflection trên `BasicReader` không tự xuất hiện method `tag`; introduction chỉ có trên proxy.

Tạo file **`InterfaceIntroductionDemo.java`**:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class InterfaceIntroductionDemo {
    interface Reader {
        String read(String key);
    }

    interface Tagged {
        String tag();
        void setTag(String value);
    }

    static final class BasicReader implements Reader {
        @Override
        public String read(String key) {
            System.out.println("TARGET read " + key);
            if ("BAD".equals(key)) {
                throw new IllegalArgumentException("bad key");
            }
            return "DATA:" + key;
        }
    }

    static final class IntroductionHandler implements InvocationHandler {
        private final Reader target;
        private String tag = "unset";

        IntroductionHandler(Reader target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "TaggedReaderProxy";
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                };
            }

            if (method.getDeclaringClass() == Tagged.class) {
                return switch (method.getName()) {
                    case "tag" -> tag;
                    case "setTag" -> {
                        tag = (String) args[0];
                        System.out.println("INTRO setTag " + tag);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                };
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapped) {
                throw wrapped.getCause();
            }
        }
    }

    static Reader attachTag(Reader target) {
        return (Reader) Proxy.newProxyInstance(
                Reader.class.getClassLoader(),
                new Class<?>[] { Reader.class, Tagged.class },
                new IntroductionHandler(target)
        );
    }

    public static void main(String[] args) {
        Reader target = new BasicReader();
        Reader first = attachTag(target);
        Reader second = attachTag(target);

        System.out.println("FIRST Reader=" + (first instanceof Reader));
        System.out.println("FIRST Tagged=" + (first instanceof Tagged));
        System.out.println("TARGET Tagged=" + (target instanceof Tagged));
        System.out.println("SAME class="
                + (first.getClass() == target.getClass()));
        System.out.println("SAME object=" + (first == target));

        Tagged a = (Tagged) first;
        Tagged b = (Tagged) second;
        a.setTag("alpha");
        b.setTag("beta");
        System.out.println("TAGS first=" + a.tag()
                + " second=" + b.tag());

        System.out.println("CLIENT " + first.read("A"));
        System.out.println("CLIENT " + target.read("B"));

        System.out.println("PROXY self-equal=" + first.equals(first));
        System.out.println("PROXY target-equal=" + first.equals(target));
        System.out.println("PROXY text=" + first);

        boolean targetHasTagMethod = Arrays.stream(
                target.getClass().getDeclaredMethods()
        ).anyMatch(method -> method.getName().equals("tag"));
        System.out.println("TARGET method tag=" + targetHasTagMethod);

        try {
            first.read("BAD");
        } catch (IllegalArgumentException error) {
            System.out.println("CLIENT error=" + error.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac InterfaceIntroductionDemo.java
java InterfaceIntroductionDemo
~~~

**Stdout kỳ vọng, đúng thứ tự:**

~~~text
FIRST Reader=true
FIRST Tagged=true
TARGET Tagged=false
SAME class=false
SAME object=false
INTRO setTag alpha
INTRO setTag beta
TAGS first=alpha second=beta
TARGET read A
CLIENT DATA:A
TARGET read B
CLIENT DATA:B
PROXY self-equal=true
PROXY target-equal=false
PROXY text=TaggedReaderProxy
TARGET method tag=false
TARGET read BAD
CLIENT error=bad key
~~~

### 10.1. Vì sao `FIRST Tagged=true` nhưng `TARGET Tagged=false`?

`Proxy.newProxyInstance` tạo object proxy implements hai interfaces được cung cấp. `BasicReader` gốc chỉ implements `Reader` và không bị sửa cấu trúc, nên khi `target` là reference kiểu `Reader`, `target instanceof Tagged` trả false.

Đây là bằng chứng trực tiếp về **interface introduction trên wrapper**, không phải interface được dệt vào target class.

### 10.2. Vì sao hai proxies có tags khác nhau?

Mỗi lời gọi `attachTag(target)` tạo **một `IntroductionHandler` mới**, có field `tag` riêng. Cho dù hai handlers giữ cùng target, `a.tag()` và `b.tag()` vẫn là `alpha` và `beta`.

Đây là **per-proxy state**, không phải per-target state. Nếu yêu cầu “mỗi Reader target chỉ có một tag, mọi wrappers cùng thấy”, cần owner/map/field khác và test identity association.

### 10.3. Vì sao `read()` còn hành vi target gốc?

Handler chuyển tiếp `Reader` methods qua `method.invoke(target,args)`. `read("A")` in `TARGET read A` rồi trả `DATA:A`. Lỗi khi `read("BAD")` được gỡ khỏi `InvocationTargetException`, caller tiếp tục nhận `IllegalArgumentException("bad key")`.

Giới thiệu interface mới không bắt buộc làm thay đổi semantics của những operations cũ — nhưng điều đó phải được **hiện thực và kiểm thử**, không tự động đúng với mọi handler.

### 10.4. Vì sao không có `tag` trong target reflection?

`Arrays.stream(target.getClass().getDeclaredMethods())` phản ánh methods được khai báo trong `BasicReader` class đã load. Chúng không có `tag` vì handler chỉ cài method cho proxy. Dữ liệu tag cũng là field của `IntroductionHandler`, không phải field mới của `BasicReader`.

Nếu một engine weaving thật sự thêm `tag` vào class artifact, kết quả reflection có thể khác; phải test artifact đó thay vì suy từ demo.

### 10.5. Identity và equals là hợp đồng phải chọn

`first != target` vì là hai objects. Handler định nghĩa `equals` là so identity của proxy, vì vậy `first.equals(first)` true và `first.equals(target)` false. `hashCode` tương thích với identity semantics ấy. `toString` có giá trị riêng của proxy.

Nếu API trước kia hứa object identity của target, giao diện mới không khiến proxy trở nên “trong suốt hoàn toàn”. Đây là khác biệt cần giải quyết tại abstraction boundary.

### 10.6. Demo cố ý không đảm bảo điều gì?

- Không chèn một field mới vào target class.
- Không thêm một Java method declaration mới vào source/bytecode của `BasicReader`.
- Không dùng syntax của một ITD/weaving engine chuyên biệt.
- Không tạo subclass của `BasicReader` (lớp này còn được khai báo `final`).
- Không làm `Reader` interface gốc tự kế thừa `Tagged`.
- Không xử lý nhiều interfaces có methods trùng signature; `getDeclaringClass` phân nhánh đúng nhờ các methods **không trùng** ở demo.
- Handler lưu mutable String `tag` và chạy tuần tự; nếu nhiều threads set/read cùng proxy, cần memory-visibility/atomicity contract phù hợp.
- Không xử lý serialization, module access phức tạp, async stream hoặc production-grade resource lifecycle.

### 10.7. Mở rộng bài thực hành

Bạn có thể thêm method `clearTag()` vào `Tagged`, rồi sửa handler tương ứng. Để giữ contract, hãy kiểm:
- `clearTag()` trả về trạng thái được định nghĩa.
- Hai proxy có state độc lập.
- Target reflection vẫn không thấy added method.
- Nếu dùng chung một handler cho hai proxies, state **có thể** trở thành shared và cần được công bố.
- Nếu muốn thread-safe semantics, chỉ rõ linearization/visibility của get/set/clear.

Đây là bài tập thiết kế interface extension **qua proxy**, vẫn không phải field introduction bằng weaving.

## 11. Khi phương thức introduced gọi lại target

Một introduced method có thể triển khai bằng:
- Đọc state của handler.
- Gọi một method có sẵn trên target.
- Delegate sang helper/mixin object.
- Thực thi behavior mới hoàn toàn.
- Kết hợp nhiều operations của target.

Ví dụ một `snapshot()` introduced có thể gọi `read()` nhiều lần. Nhưng nên quyết định **gọi qua proxy hay target**:
- Gọi `target.read()` trực tiếp có thể bypass advice được đặt trên proxy `read()`.
- Gọi `proxy.read()` có thể đi qua advice nhưng dễ tạo recursion nếu chính `read()` lại gọi `snapshot()`.
- Nếu introduced method chạy trong class đã weave, self-invocation semantics phụ thuộc join-point model.

Đây là vấn đề **interception path**, độc lập với việc field/method mới có tồn tại. Thêm cấu trúc không tự sửa lỗi self-invocation hoặc recursive advice.

### 11.1. Không lặp side effect ngoài ý định

Một introduced method `saveAndTag()` gọi `save()` rồi cập nhật tag. Nếu aspect retry bên ngoài và `save()` không idempotent, lặp toàn bộ operation có thể tạo duplicate changes. Cần define atomicity, failure/rollback và retry boundary.

### 11.2. Chú ý transaction/resource scope

Nếu introduced method mở một stream/lock hoặc gắn state bên cạnh thao tác nghiệp vụ, ai đóng chúng? Một method trả stream không thể bị wrapper `finally` đóng resource ngay khi return nếu caller còn đọc. Với async, `CompletionStage` completion có thể xảy ra sau method return.

### 11.3. Thực thi field access vs accessor method

Một tool AOP có thể hỗ trợ field-get/set join points; tool khác chỉ thấy method calls. Nếu policy dựa vào việc can thiệp mọi lần đọc/ghi field introduced, phải kiểm nó có thực sự observe field access không. Một direct field access không tự trở thành method invocation trên proxy.

## 12. Các xung đột khi nhiều aspects cùng thay đổi cấu trúc

Một chương trình lớn có thể có:
- Aspect A introduce `getTag()`.
- Aspect B cũng introduce `getTag()` nhưng khác nghĩa.
- Một base class đã có `getTag()`.
- Một mixin yêu cầu property `tag` còn một mixin khác dùng cùng tên.
- Một interface mới có default method cùng signature.
- Một generated bridge method trùng descriptor.

### 12.1. Những chiến lược giải quyết

| Tình huống | Chiến lược cần cân nhắc |
| --- | --- |
| Method đã tồn tại | Từ chối, đổi tên/API hoặc precedence nếu tool có semantics rõ. |
| Hai introductions trùng method | Fail fast hoặc khai override rule tường minh. |
| Field trùng tên | Đặt owner/namespace rõ hoặc đổi mô hình state. |
| Interface methods không được implement | Cung cấp delegate/default implementation hợp lệ. |
| Generic erasure name clash | Đổi signature/API; không kỳ vọng runtime phân biệt generics đã erasure. |
| Hai advice dùng state khác owner | Tách per-target/per-proxy/per-flow semantics. |
| Initialization đan xen | Chọn lifecycle owner và xác nhận thứ tự tạo state. |
| Build artifact lệch version | Rebuild/weave đồng bộ và test binary. |

Không nên coi source order, alphabetical class name hay registry insertion order là **hợp đồng ưu tiên cấu trúc** nếu công cụ không công bố.

### 12.2. Precedence của advice khác conflict resolution của members

Advice order quyết định wrapper nesting cho **một execution join point**. Member introduction conflict liên quan **hình dạng type** và lựa chọn implementation; nó có thể được giải quyết trong compiler/weaver trước runtime. Đảo order advice không tự loại duplicate methods.

### 12.3. Giao diện được introduce có thể thay đổi method overload resolution

Nếu compile-time view của một type có thêm overload, compiler của client có thể chọn method khác ở những lời gọi ambiguous hoặc overload-dependent. Đây là hệ quả về **source compatibility**, không chỉ runtime dispatch. Cần kiểm trên consumer source code và artifact build cuối.

## 13. Source compatibility, binary compatibility và behavioral compatibility

Ba trục này không thay thế nhau:

### 13.1. Source compatibility

Client source đang compile có còn compile sau khi introduce method/interface không?
- Một method mới có thể tạo overload ambiguity.
- Một interface mới có thể mang abstract methods mà implementation cần đáp ứng.
- Một type đã được giới thiệu có thể ảnh hưởng generic bounds.
- Client javac thông thường có thể chưa biết introduced member nếu build pipeline không đưa declaration vào compile-time view.

### 13.2. Binary compatibility

Class files đã compile trước đó có tiếp tục link/run được với transformed artifacts không?
- Class/method/field descriptors có khớp không?
- Có nạp nhầm artifact chưa weave không?
- Runtime có gặp `NoSuchMethodError`, `NoSuchFieldError`, `AbstractMethodError` hoặc linkage errors khác không?
- Classloader/module boundaries có đúng không?

Không khẳng định mỗi introduction đều gây các lỗi ấy; đó là **những failure modes cần test** khi build/runtime artifacts không đồng bộ.

### 13.3. Behavioral compatibility

Dù code compile và link:
- Return values có đúng contract?
- Exception types/cause và nullability có giữ?
- Đối tượng được gắn state mới có đúng identity/lifetime?
- Existing methods có bị advice hoặc mixin thay behavior ngoài ý định?
- Concurrency/serialization/lazy streams/async outcomes có đúng?
- Các clients dựa vào reflection/equality có còn hoạt động?

Một introduction thành công ở structural level vẫn có thể thất bại ở behavioral level.

### 13.4. Compatibility nên được phát biểu theo client boundary

Một JDK interface proxy implements `Reader,Tagged` có thể thay target tốt ở `Reader` contract nhưng không thay được concrete target type về `getClass`, serialization hoặc casts. Không hứa hoàn toàn trong suốt cho mọi Java observation.

## 14. Reflection, annotations và tooling sau introduction

### 14.1. Reflection hỏi object nào?

- `target.getClass()`: class của target gốc.
- `proxy.getClass()`: generated proxy class.
- `wovenTarget.getClass()`: class definition thực sự được nạp sau transformation.

Nếu introduction chỉ nằm trên proxy, reflection trên target không thấy field của handler. Nếu bytecode đã weave vào target, reflection có thể thấy thành viên theo visibility và loại truy vấn `getMethods`/`getDeclaredMethods` tương ứng.

### 14.2. Annotation lookup

Một annotation có thể nằm ở:
- Interface mới.
- Method introduced.
- Target implementation.
- Mixin/delegate implementation.
- Proxy-generated method.
- Bytecode artifact đã weave.

Java reflection không tự hợp nhất annotations từ mọi chỗ thành một cái nhìn duy nhất. Một advice matching metadata phải xác định nơi lookup, retention và inheritance semantics. Nếu matcher chỉ đọc method của interface gốc, annotation trên mixin có thể không được nhìn thấy.

### 14.3. IDE, debugger, stack trace

Generated methods hoặc code dệt có thể khiến:
- IDE navigation không mở tới source như một method bình thường.
- Stack trace thêm frames từ proxy/interceptor/bridge.
- Source-level breakpoint không khớp vị trí transformed bytecode.
- Incremental compilation cache sai khi cấu trúc đã thay nhưng artifact chưa cập nhật.

Những giới hạn này là vấn đề **toolchain observability**; không chứng minh introduction không đúng, nhưng cần hướng dẫn debug/build rõ.

## 15. Serialization, state migration và phiên bản

Nếu class đã weave có thêm instance field, serialized form hoặc reflection-based mapper có thể thay đổi — **tùy serialization mechanism**. Với Java native serialization, default field serialization và serialVersionUID cần cân nhắc; với JSON mapper, các getters/fields mới có thể xuất hiện trong schema khi được tool discover.

Một proxy giữ field ở handler lại có object graph/serializability khác target. Không tự giả định target `Serializable` nghĩa là mọi handler/delegate và proxy setup đều serialize/deserialize như mong đợi.

### 15.1. Tương thích dữ liệu cũ

Khi cấu trúc mới được triển khai:
- Dữ liệu đã lưu trước introduction có field mới mặc định gì?
- Khi deserialize object cũ, introduced member có invariant nào phải thỏa?
- Có cần migration/backfill?
- Một target chưa weave nhưng đọc dữ liệu có field mới sẽ xử lý thế nào?
- Phiên bản client cũ khi gọi API mới có fail hay bỏ qua fields hợp lệ?

### 15.2. Object identity sau deserialization

Nếu introduced state nằm trong proxy handler, deserialize hoặc tạo wrapper mới có thể tạo state mới. Nếu state nằm trong woven target field, nó có thể theo serialized field tùy cơ chế. Cần xác định **source of truth** và persistence contract, không đồng nhất hai mô hình.

### 15.3. Không đưa toàn bộ target vào registry để “giữ thuộc tính”

Nếu dùng external association, một global strong-key map có thể giữ tất cả targets không còn dùng. Nếu value chứa back-reference tới key, weak-key mapping cũng cần được xem xét retained paths. Có thể cần explicit removal, per-request/per-target owner hoặc ID-based store bounded theo nghiệp vụ.

## 16. Security và quyền truy cập khi bổ sung API

Introduction có thể tăng **attack surface**. Một method `resetAudit()` vốn không tồn tại trên API cũ nay trở thành public method ở wrapper hoặc transformed class có thể bị caller dùng nếu giữ reference phù hợp.

Cần hỏi:
1. Method mới có nên public không?
2. Có authorization và validation phù hợp không?
3. Nếu mixin truy cập state target, đó có phải quyền truy cập được phép?
4. Interface mới có vô tình làm object được các serializers/routers/discoverers nhận thành capability nhạy cảm không?
5. Có đường gọi trực tiếp target bỏ qua proxy enforcement không?
6. Các pointcuts security có khớp methods mới không?
7. Một introduced method có thể bypass invariant của domain API cũ không?
8. Có cần negative tests cho untrusted callers?

### 16.1. “Implements interface” không phải bằng chứng caller có quyền

Một marker interface hoặc `Auditable` chỉ mô tả capability/contract ở type level. Nó không xác thực identity người dùng. Nếu có method nhạy cảm, policy phải được bảo vệ ở trusted boundary và trong implementation khi cần.

### 16.2. Reflection không phải ranh giới bảo mật duy nhất

Một method public introduced có thể được gọi bằng typed API hoặc reflection. Dựa vào việc “người dùng không biết tên method generated” không phải security policy. Cần thiết kế access modifiers và enforcement thật.

### 16.3. Không log toàn bộ state introduced

Một field như `tenantContext` có thể chứa dữ liệu nhạy cảm. Advice tự động ghi mọi field bằng reflection để debug có thể lộ secrets hoặc gây recursion. Chỉ log những fields được allowlist, sanitize và giới hạn dung lượng.

## 17. Khởi tạo state, lifecycle và chạy song song

### 17.1. Một introduction có thể phục vụ nhiều objects

Nếu cùng aspect giới thiệu interface cho nhiều targets:
- Một instance aspect có thể phục vụ nhiều targets.
- State có thể nằm trong từng proxy/handler.
- State có thể nằm trong từng target đã weave.
- State có thể nằm trong registry shared.
- Một mixin object có thể là singleton hoặc tạo per target tùy cấu hình.

Phải nêu association key và owner. Không mặc định một aspect declaration sinh đúng một object cho mỗi target.

### 17.2. Trường hợp tạo hai proxies cho cùng target

Trong demo Java, tag được gắn với **handler**, nên hai wrappers có hai tag riêng. Nếu nghiệp vụ yêu cầu một tag duy nhất trên target, cách đó không đủ. Hãy chọn:
- Một proxy duy nhất có owner ổn định.
- State gắn vào target qua weaving phù hợp.
- Per-target identity registry có cleanup.
- Một adapter quản lý canonical identity.

Mỗi lựa chọn có rủi ro khác nhau với equality, references và memory.

### 17.3. Đồng thời

Nếu `setTag()` được gọi trên cùng proxy từ nhiều threads, field `String tag` mutable trong demo không có explicit synchronization/volatile. Reference assignment có atomicity theo Java, nhưng thread đọc có thể gặp visibility/ordering không đúng mong muốn. Dùng `volatile`, `AtomicReference` hoặc lock khi hợp đồng yêu cầu; nếu thao tác gồm nhiều fields, cần synchronization cho toàn invariant.

### 17.4. Lazy initialization

Một introduced member có thể được tạo state khi lần đầu gọi. Với nhiều threads, nếu logic kiểu `if (state==null) state = new State()`, có thể race, tạo nhiều instances hoặc unsafe publication. Chọn một chiến lược safe publication/lazy initialization được kiểm chứng, không chỉ hy vọng first call sẽ tuần tự.

### 17.5. Cleanup

Một introduced field giữ connection, stream, listener hoặc executor cần explicit close/unregister. Một proxy wrapper không tự đóng target đúng lúc chỉ vì wrapper được GC. Với hot reload/classloader unload, registries phải xóa references tới class/method/proxy của module cũ.

## 18. Khi nào nên dùng introduction, khi nào không?

### 18.1. Những trường hợp có giá trị

- Một capability nhỏ, độc lập, áp dụng nhất quán cho nhiều types không cùng hierarchy.
- Tích hợp interface protocol `Tagged`, `Auditable`, `Identifiable` cho nhiều objects khi tool hỗ trợ và contract rõ.
- Code generation hoặc weaving làm giảm boilerplate cấu trúc lặp lại có ý nghĩa crosscutting.
- Một mô hình domain/legacy có nhiều targets mà extension thông qua composition hiện hành khó làm gọn.
- Test/debug tooling cần một capability phụ, nhưng không làm thay đổi nghiệp vụ cốt lõi.

### 18.2. Những trường hợp nên cân nhắc composition trước

- Chỉ mở rộng một hoặc hai classes.
- Có thể sửa trực tiếp source và API một cách rõ ràng.
- Field introduced giữ state phức tạp hoặc tài nguyên có vòng đời quan trọng.
- Method mới có logic nghiệp vụ lớn cần riêng dependency/service.
- Security policy mới đòi trusted boundary rõ.
- Build/weaving toolchain phức tạp hơn lợi ích tránh boilerplate.
- Runtime có nhiều classloaders/AOT/native constraints khiến generated structure khó ổn định.
- Consumers cần compile-time discoverability/refactoring thông thường.

### 18.3. Một quyết định thực dụng

Nếu mục tiêu là “mọi đối tượng của các loại này có thêm method để trả tag”:
1. Có thể định nghĩa interface `Tagged` và decorator không?
2. Có cần **target class thật** implements `Tagged`, hay caller dùng wrapper là đủ?
3. Caller đang dùng concrete classes hay interfaces?
4. State nằm ở target/proxy/registry và bao lâu?
5. Có cần thay đổi hàng nghìn class artifacts hay chỉ vài object wrappers?
6. Runtime/build tool thật sự hỗ trợ method/field/interface introduction mức nào?
7. Các tests về reflection, serialization, concurrency, security có đủ không?

Chỉ khi câu trả lời cho thấy lợi ích rõ, hãy chọn structural AOP introduction thay vì API/composition bình thường.

## 19. Ma trận kiểm thử sau khi introduce cấu trúc

| Ca kiểm thử | Điều phải xác nhận |
| --- | --- |
| **Interface visibility** | `instanceof IntroducedInterface` đúng trên object kỳ vọng. |
| **Original target** | Target gốc có hoặc không thay type theo cơ chế thực tế, không đoán. |
| **Callable methods** | Tất cả introduced abstract methods có implementation hợp lệ. |
| **Member reflection** | `getMethods/getDeclaredMethods/getDeclaredFields` khớp binary artifact. |
| **Compile-time client** | Consumer code compile với đúng toolchain và type view. |
| **Runtime binary** | Đúng transformed/generated class được nạp; không linkage failure. |
| **Target methods** | Existing return/exception/side effects giữ contract. |
| **Introduced state** | Defaults, nullability, persistence và mutations đúng. |
| **Two proxies one target** | Per-proxy vs per-target state rõ và test được. |
| **Multiple targets** | State không rò giữa instances không liên hệ. |
| **Method collision** | Duplicate declarations được xử lý rõ hoặc fail fast. |
| **Erasure** | Không có generic name clash, bridge/override đúng. |
| **Final/sealed/record** | Không giả định unsupported subtype generation. |
| **Object methods** | Equals/hashCode/toString có semantics hợp lệ. |
| **Self-invocation** | Introduced behavior và interception coverage không bị suy sai. |
| **Concurrency** | Safe initialization, reads/writes, per-target state. |
| **Error** | Exceptions được bảo toàn, cleanup thực hiện đúng. |
| **Serialization** | Round-trip/schema/state migration theo hợp đồng. |
| **Security** | API mới không bypass authorization hoặc lộ data. |
| **Hot reload** | Registry/classloader references được cleanup. |
| **Version upgrade** | Source/binary/behavioral contracts tiếp tục được test. |

### 19.1. So sánh reflection ở đúng nơi

Đừng test chỉ trên proxy rồi kết luận target class được weave. Với JDK proxy, test:
- `proxy instanceof Tagged` → true.
- `target instanceof Tagged` → false.
- `target.getClass().getDeclaredMethods()` không có `tag`.
- `proxy.getClass()` khác `target.getClass()`.

Với structural weaving engine, phải **test binary target thực sau weaving** theo cơ chế đó.

### 19.2. Negative test quan trọng

Cố gắng gọi method introduced khi:
- Không dùng đúng wrapper.
- Mixin thiếu initialization.
- Input null/invalid.
- Có hai aspects cùng trùng signature.
- Runtime nạp artifact sai version.
- Caller cast concrete subtype không tương thích.
- Concurrent threads cùng set state.
- Một advice khác short-circuit hoặc retry method introduced.

Đây là các đường dễ lỗi hơn happy path “getter trả đúng”.

### 19.3. Test theo hợp đồng, không theo mẹo runtime

Nếu tool hứa interface introduction, assert capability mà caller được công bố nhìn thấy. Không cố assert class name generated phải là một chuỗi cụ thể (proxy class names không phải API bền vững). Nếu binary weaving hứa field introduced, kiểm field/behavior trên artifact cuối thay vì chỉ xem source cũ.

## 20. Những hiểu lầm thường gặp

**“AOP nào cũng thêm field/method/interface được.”** Không. Nhiều engines chỉ có interception; structural introduction tùy tool.

**“Một JDK proxy implement interface mới nghĩa là class target đã đổi implements.”** Không. Chỉ proxy object có giao diện mới.

**“Có `tag()` qua proxy nghĩa là `Target.class` có declared method `tag`.”** Không nếu method được handler cung cấp.

**“Field trong handler là field introduced vào target.”** Không. Nó thuộc proxy handler.

**“Tạo subclass mới nghĩa là sửa field của base class.”** Không. Field mới thuộc subclass, không thay declaration base class.

**“Final class không thể tham gia bất kỳ AOP nào.”** Sai. Nó có thể được bọc qua interface hoặc được biến đổi theo khả năng công cụ thích hợp; chỉ không subclass bằng Java thông thường.

**“Records và sealed hierarchy không có ràng buộc khi introduce interfaces.”** Sai. Java type rules vẫn phải được tôn trọng.

**“Chỉ cần thêm implements thì mọi abstract method tự có logic.”** Không. Cần implementation/delegate/default method hợp lệ.

**“Hai generic method khác `List<String>` và `List<Integer>` chắc chắn không trùng.”** Sai do erasure.

**“Đặt aspect A trước B trong source là conflict resolver.”** Không có quy tắc chung ấy.

**“Một source client luôn nhìn thấy introduced method nếu runtime class đã weave.”** Không. Compile-time view/toolchain phải tương thích.

**“`equals` sẽ tự khiến proxy và target có identity giống nhau.”** Không. Toán tử `==` chỉ so references; equality policy phải được thiết kế.

**“State per-target mặc nhiên thread-safe.”** Không. Cùng target có thể nhận nhiều concurrent calls.

**“Introduction không ảnh hưởng serialization.”** Không được giả định. New fields/getters và proxy graph có thể đổi schema hoặc serialization behavior.

**“Không có read/write mới của field nên privacy không đổi.”** Sai. Public introduced API có thể làm lộ capabilities hoặc dữ liệu trước đó không truy cập được.

**“Thêm cấu trúc giúp mọi self-call đi qua advice.”** Không. Interception path vẫn tùy proxy/weaving semantics.

**“Tool biến đổi class đã load tùy ý.”** Không. Class redefinition/structural changes chịu giới hạn JVM và tool.

**“Một test proxy demo chứng minh mọi weaving engine giống vậy.”** Không. Chỉ kết luận trong mô hình được test.

## 21. Bài tập vận dụng và đáp án

### Câu 1 — Proxy hay target được thay?

`Reader target` không implements `Tagged`, `Reader proxy` được tạo với cả `Reader` và `Tagged`. Kết quả `proxy instanceof Tagged` true cho biết gì?

**Đáp án:** Proxy object có capability `Tagged`. Không thể kết luận target class đã có interface đó.

### Câu 2 — Hai proxies cùng target

P1 và P2 cùng delegate vào T, mỗi handler có field `tag` riêng. P1 set `"A"`, P2 set `"B"`. Tag nào thuộc T?

**Đáp án:** Theo mô hình ấy không có field tag trên T; P1/P2 giữ state riêng. Muốn per-target tag phải đổi owner/association.

### Câu 3 — Final target

`final class ReadOnlyStore implements Store`. Có thể tạo JDK interface proxy implements `Store` và `Tagged` không?

**Đáp án:** Có thể nếu các interfaces bình thường hợp lệ cho proxy và loader/visibility phù hợp. Final ngăn subclass của `ReadOnlyStore`, không ngăn object wrapper implement interfaces độc lập.

### Câu 4 — Method conflict

Target đã có `status()`; một aspect introduce method cùng signature nhưng nghĩa khác. Có được tự chọn implementation theo alphabet không?

**Đáp án:** Không nên. Cần conflict rule được công cụ hỗ trợ, ưu tiên fail fast/đổi API khi không thể xác định ngữ nghĩa.

### Câu 5 — Field location

`target.getClass().getDeclaredFields()` không thấy `tag` nhưng `((Tagged)proxy).tag()` trả được. Có mâu thuẫn không?

**Đáp án:** Không. Handler/registry có thể giữ tag mà target class không có field mới.

### Câu 6 — Compile-time vs runtime

Client `new Target().tag()` không compile trong javac thông thường, nhưng bản binary được weave ở runtime có `tag`. Vì sao?

**Đáp án:** Compiler không có declaration trong compile-time type view. Cần toolchain/artifact compile tương thích nếu muốn gọi method bằng static typing.

### Câu 7 — Generic name clash

Một class có hai methods `process(List<String>)` và `process(List<Integer>)`. Vì sao introduction đó không hợp lệ trong cùng Java class?

**Đáp án:** Hai signatures có cùng erasure `process(List)`, tạo name clash.

### Câu 8 — State concurrency

Một introduced field đếm updates bằng `count++`, cùng target được 100 threads gọi. Có đảm bảo count tăng 100 không?

**Đáp án:** Không; cần atomic/synchronization phù hợp. Introduction không tự đem lại thread safety.

### Câu 9 — Security

Một aspect introduce public method `resetState()` cho mọi domain object. Rủi ro?

**Đáp án:** Tăng attack surface; caller có capability có thể phá invariant nếu method không có authorization/validation. Phải xác định trusted boundary.

### Câu 10 — Binary mismatch

Client compile với class artifact đã weave có method `tag`, production lại load bản chưa weave. Điều gì có thể xảy ra?

**Đáp án:** Tùy call site, có thể lỗi linkage như `NoSuchMethodError` hoặc lỗi khác. Cần đồng bộ compile/runtime artifacts.

### Câu 11 — Serialization

Sau khi introduce field `lastAuditId` vào binary target, dữ liệu serialized cũ có thể cần xử lý gì?

**Đáp án:** Cần kiểm default/migration/schema/compatibility theo serialization mechanism, không giả định mọi round-trip giữ contract.

### Câu 12 — Mixin với async

Một introduced method `fetchAsync()` dùng future, advice quanh nó đóng resource ngay khi method return handle. Có đúng không?

**Đáp án:** Chưa chắc. Task có thể còn pending; resource lifecycle phải theo hợp đồng completion/ownership, không chỉ method return.

### Câu 13 — API hay introduction

Chỉ có một class cần thêm method `tag()` đơn giản, source có thể sửa. Nên nhất thiết dùng structural AOP?

**Đáp án:** Không. Mở rộng trực tiếp class hoặc dùng decorator có thể đơn giản, rõ type hơn và ít ràng buộc toolchain.

### Câu 14 — Reflection metadata

Một proxy implements interface `Tagged` có annotation trên method `tag`, nhưng matcher chỉ đọc annotations từ target implementation class. Có chắc matcher thấy không?

**Đáp án:** Không. Metadata lookup site và annotation retention/merging policy phải được xác định rõ.

## 22. Checklist trước khi bật một structural introduction

1. **Mục tiêu:** Thêm behavior tại join point hay thực sự cần thay đổi type shape?
2. **Nơi áp dụng:** Proxy, subclass generated, decorator, source hay bytecode weaving?
3. **Capability của tool:** Có hỗ trợ thêm method, field, interface hoặc parent relation đúng yêu cầu?
4. **Target identity:** Object nào có capability, wrapper hay target gốc?
5. **Compile view:** Compiler client có thấy introduced members không?
6. **Runtime artifact:** Production nạp đúng transformed class không?
7. **Type rules:** Final, record, sealed, visibility, constructors, generics/erasure?
8. **Conflict:** Duplicate methods/fields, default methods, bridge/overload resolution?
9. **Implementation:** Tất cả abstract interface methods được implement/delegate đúng?
10. **State owner:** Field/state nằm ở target, proxy, mixin hay registry?
11. **Initialization:** Giá trị mặc định/invariant đúng từ trước invocation đầu?
12. **Thread safety:** Per-target/per-proxy state có concurrent reads/writes không?
13. **Lifecycle:** Ai close/unregister/remove, khi nào target/proxy/module shutdown?
14. **Security:** New methods có làm lộ operation/dữ liệu không?
15. **Existing contract:** Original return/error/side effects có giữ?
16. **New contract:** Introduced API có tài liệu, nullability, failure semantics?
17. **Equality:** Identity, equals/hashCode/toString có chính sách?
18. **Reflection:** Annotations, declared methods/fields và classloader đúng?
19. **Serialization:** Schema, version, round-trip/migration?
20. **AOP coverage:** New methods có được những advice khác quan sát nếu cần?
21. **Ordering:** Structural conflicts và advice precedence có chính sách riêng?
22. **Async:** Introduced API trả futures/streams có giữ lifecycle?
23. **Testing:** Có build+runtime integration tests, positive và negative paths?
24. **Rollback:** Có thể tắt introduction mà không làm client binary phụ thuộc member mới lỗi?
25. **Maintainability:** Lợi ích crosscutting có lớn hơn complexity toolchain/debug không?

## Kết luận

**Bổ sung phương thức, thuộc tính hoặc giao diện** là một hình thức **thay đổi cấu trúc** trong những mô hình AOP có hỗ trợ introduction/inter-type declarations. Nhưng “có thêm khả năng” có nhiều mức: **proxy implement interface mới**, **subclass/generated wrapper có members mới**, hoặc **class artifact thật sự được biến đổi bằng weaving**. Các mức ấy không tương đương về `getClass()`, reflection, casts, binary compatibility, initialization và ownership.

Một thiết kế tốt xác định rõ **công cụ hỗ trợ loại thay đổi nào**, đối tượng nào sở hữu members/state mới, compiler và runtime có nhìn cùng type definition không, cách xử lý conflict/erasure/inheritance ra sao, và liệu các methods mới có bảo toàn hợp đồng, bảo mật, thread safety và lifecycle không. Ví dụ Java 17 cho thấy interface introduction bằng JDK proxy rất hữu ích khi chỉ cần mở rộng capability của wrapper, nhưng **không được gọi nhầm thành field/method được chèn vào target gốc**. Khi nhu cầu chỉ là một extension nhỏ, class/interface bình thường hoặc composition có thể đơn giản hơn; khi nhiều types thật sự cần một structural concern chung, introduction là công cụ mạnh nếu **được hỗ trợ, khai báo rõ và kiểm thử trên artifact cuối cùng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-003">Khai báo liên kiểu (inter-type declaration) trong mô hình AOP phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo liên kiểu (inter-type declaration) trong mô hình AOP phù hợp

## 1. Khai báo liên kiểu là gì?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một *aspect* mô-đun hóa những mối quan tâm cắt ngang nhiều thành phần. Ta thường biết AOP qua **pointcut** và **advice**: một pointcut lựa chọn các **join point**; advice thực hiện hành vi trước, sau hoặc bao quanh những điểm đó.

**Khai báo liên kiểu (inter-type declaration, ITD)** là một cơ chế khác: aspect có thể **mô tả phần mở rộng cấu trúc của những kiểu khác**. Khi mô hình/công cụ AOP hỗ trợ, một khai báo nằm trong aspect có thể trở thành thành viên hoặc quan hệ kiểu của một class/interface khác sau khi được xử lý bởi compiler hoặc weaver phù hợp.

Những thay đổi cấu trúc có thể gồm:

- Thêm **field** vào kiểu đích.
- Thêm **method** vào kiểu đích.
- Thêm **constructor** vào class đích, theo các ràng buộc công cụ.
- Khai báo kiểu đích **implements** thêm interface.
- Khai báo kiểu đích **extends** một superclass hợp lệ, nếu mô hình ngôn ngữ hỗ trợ và không vi phạm hệ thống kiểu.

**Không phải mọi hệ thống AOP đều có ITD.** Một engine chỉ hỗ trợ proxy/advice khi gọi method có thể **không thay đổi được cấu trúc Java class thực tế**. Vì vậy cụm “trong mô hình AOP phù hợp” có ý nghĩa quan trọng: phải xác minh công cụ có hỗ trợ **structural/static crosscutting**, hình thức weaving và giới hạn Java/runtime tương ứng.

Ví dụ trực giác:

```text
Trước khi có ITD:

Order
  + id()
  + submit()

Sau khi một công cụ hỗ trợ ITD xử lý thành công:

Order
  + id()
  + submit()
  + auditCount()          [introduced method]
  + auditMarks            [introduced field]
  implements Auditable    [introduced parent interface]
```

Đây là mô tả cấu trúc, **không có nghĩa mã `Order.java` gốc phải được sửa trực tiếp**, nhưng artifact dùng để biên dịch/nạp/chạy có thể phải được **woven** thành công.

## 2. Phân biệt ITD với advice: hai chiều cắt ngang

| Câu hỏi | Advice (dynamic crosscutting) | Inter-type declaration (structural/static crosscutting) |
| --- | --- | --- |
| Can thiệp vào cái gì? | Đường thực thi ở join point | Cấu trúc của kiểu đích |
| Thêm method mới vào API? | Không tự động | Có thể, nếu được hỗ trợ |
| Thêm field riêng cho mỗi instance? | Không tự động | Có thể với introduced instance field |
| Thêm quan hệ implements interface? | Không tự động | Có thể với declaration hỗ trợ |
| Thay đổi hành vi method hiện hữu? | Có thể qua before/after/around | Có thể gián tiếp do method/hierarchy mới, nhưng không cùng cơ chế |
| Phụ thuộc call path có qua proxy không? | Với proxy-based advice: thường có | Với ITD đã weave vào class: việc tồn tại thành viên không đòi caller đi qua proxy |
| Kiểm tra lúc nào? | Matching/dispatch ở compile/load/runtime tùy mô hình | Thường ràng buộc kiểu và cấu trúc ở compile/weave/load |
| Rủi ro chính | Lỗi thứ tự, bypass, exception, side effects | Xung đột chữ ký, hierarchy, binary compatibility, initialization, access |

**Advice không mặc nhiên là ITD.** Một around advice có thể trả giá trị khác hoặc thực hiện thêm logic nhưng **không biến đối tượng thành subtype của một interface mới** chỉ vì đã bọc method.

**ITD không mặc nhiên là advice.** Thêm method `auditCount()` không có nghĩa mọi lần gọi `submit()` sẽ tự tăng số đếm. Nếu cần tăng đếm khi một join point xảy ra, có thể kết hợp ITD với advice hoặc để logic cập nhật ở method phù hợp.

## 3. Khi nào nên cân nhắc ITD?

Một số use case hợp lý:

1. **Gắn vai trò (role) vào các type đã có:** nhiều class không có quan hệ cha-con nghiệp vụ nhưng đều có thể đóng vai trò `Auditable`, `Identifiable` hoặc `Observable`.
2. **Thêm hành vi chung có trạng thái per-instance:** ví dụ thêm số đếm audit cho một nhóm class khi hệ thống sở hữu được phạm vi weaving.
3. **Mở rộng một API legacy do mình quản lý** mà không muốn chèn cùng đoạn mã vào nhiều class.
4. **Xây một mixin/role có tính kiểu tĩnh:** consumer có thể thao tác qua interface được giới thiệu sau khi biên dịch phù hợp.
5. **Tách chức năng cắt ngang về một module:** ví dụ trạng thái quan sát, một interface hỗ trợ chẩn đoán, hoặc năng lực debug cho đối tượng được chọn.

Cần thận trọng với những ý tưởng:

- Thêm dữ liệu bí mật vào object ngoài mục đích ban đầu.
- Thay đổi parent class hàng loạt.
- Gắn `Serializable` nhưng chưa thiết kế invariant của object graph.
- Thêm overload giống API vendor có thể thay đổi overload resolution.
- Introduce method chỉ để tránh refactor một interface đáng lẽ do ứng dụng tự thiết kế.
- Dùng ITD để mở khóa class bị hệ thống kiểu Java cố tình hạn chế.

**Chọn ITD khi lợi ích về mô-đun hóa cấu trúc đủ lớn và có thể quản lý được build, weaving, compatibility và ownership.** Không dùng chỉ vì cú pháp ngắn.

## 4. Điều kiện tiên quyết: không phải engine nào cũng hỗ trợ

Trước khi sử dụng, hỏi:

- Công cụ AOP có cho **khai báo member ở kiểu khác** hay chỉ hỗ trợ interception?
- Có hỗ trợ thêm **field/method/constructor** hay chỉ thêm interface?
- Có hỗ trợ `declare parents ... implements` / `extends` không?
- Việc mở rộng được áp dụng ở **source compile-time**, **binary weaving**, **load-time** hay cách khác?
- Những consumer muốn gọi member mới cần được compile với artifact đã weave hay cần compile riêng theo hướng dẫn của engine?
- Compiler/IDE có hiểu cú pháp và symbol ITD không?
- Runtime class loader/module có cho định nghĩa và nạp class sau biến đổi không?
- Có những kiểu không hỗ trợ như `final`/`sealed`/record, JDK system classes, native types hay class của module bị khóa không?
- Có cách kiểm tra class thật sau weaving và rollback khi lỗi không?

Tài liệu và ví dụ dưới đây dùng **AspectJ** làm **một ngôn ngữ hỗ trợ ITD** để nhìn thấy cú pháp cụ thể. Các hệ thống AOP khác có thể dùng annotation, mixin metadata, compiler transform hoặc không có khả năng tương đương.

## 5. Khái niệm “kiểu đích” và “nơi khai báo”

Trong một khai báo như:

```java
public int Order.auditCount() {
    return 0;
}
```

đoạn này là **cú pháp inter-type method đặc thù AspectJ**, không phải khai báo method hợp lệ bên trong class Java bình thường.

Hai vai trò:

- **Declaring aspect:** module chứa khai báo, ví dụ `AuditFeature`.
- **Target type:** type nhận thành viên khi được weave, ví dụ `Order`.

Khi triển khai thành công, người dùng có thể nhìn thành viên ấy như năng lực của `Order` theo quy tắc access và compile visibility của công cụ. Tuy nhiên quyền truy cập của ITD không nhất thiết giống việc chèn thủ công một dòng `private` vào class đích; phải xem ngữ nghĩa visibility của ngôn ngữ ITD.

### 5.1. Receiver `this` bên trong introduced method

Theo ngữ nghĩa AspectJ, trong body của một **inter-type instance method**, `this` chỉ **đối tượng target type đang chạy method**, không phải instance của declaring aspect.

Ví dụ:

```java
public String Order.describe() {
    return "Order:" + this.id();
}
```

Đây là fragment ITD đặc thù AspectJ; `id()` được giả định là method phù hợp của `Order`. Nếu cần truy cập state của chính aspect, phải dùng cơ chế tham chiếu aspect phù hợp; không suy rằng `this` ở đây là aspect instance.

## 6. Giới thiệu một method mới

Inter-type method hữu ích khi muốn một role công khai có hành vi mới:

```java
public int Order.auditCount() {
    return 0;
}
```

Phát biểu: kiểu `Order` được bổ sung một method public trả `int`.

Nhưng production cần hiểu:

- Method có va chạm với method cùng chữ ký đã tồn tại không?
- Có ghi đè hoặc xung đột với method kế thừa không?
- Có thay đổi overload resolution khi method cùng tên tồn tại?
- Nếu method mới truy cập field introduced, field đã được khởi tạo đúng chưa?
- Contract của method mới là gì khi trạng thái target thay đổi?
- Method có được consumer nhìn thấy ở compile time không?

### 6.1. Khác với wrapper method

Một wrapper `OrderView.auditCount()` là method thuộc **wrapper**, không tự biến `Order` thành đối tượng có method đó. Nếu API yêu cầu object thật có thêm member, wrapper không thể được quảng bá là ITD.

### 6.2. Generic methods và overload

Một số ngôn ngữ ITD hỗ trợ generic introduced methods. Ví dụ tài liệu AspectJ mô tả cách thêm method generic vào một type theo cú pháp có tham số kiểu trước return type.

Khi đưa generic vào, cần kiểm tra:

- Type parameter bounds.
- Java erasure và bridge methods.
- Overload với method generic/covariant return có sẵn.
- Checked exceptions được khai báo.
- Source và binary compatibility.
- IDE/compiler nhận biết type signature đã weave.

Không suy rằng một method có tên khác là chắc chắn tránh xung đột nếu **erased signature** trùng.

## 7. Giới thiệu field: trạng thái thật của target hay state ở aspect?

Ví dụ cú pháp AspectJ:

```java
private int Order.auditMarks = 0;
```

Đây là **inter-type instance field** trong mô hình AspectJ: mỗi instance đích hỗ trợ phần state đã introduced. Nó **không phải** một biến singleton trên aspect.

So sánh:

```text
Aspect-wide counter:
  one count shared by aspect instance, tùy lifecycle của aspect

Introduced instance field:
  each Order instance has its own auditMarks state
```

Trường hợp nhiều object:

```text
Order A: auditMarks=1
Order B: auditMarks=3
Order C: auditMarks=0
```

Đây là ví dụ giá trị sau các lượt cập nhật độc lập, không phải output thực thi.

### 7.1. Access modifier của introduced field

Theo ngữ nghĩa AspectJ, `private` trên inter-type member có nghĩa **riêng với declaring aspect**, khác với việc trực tiếp chèn `private field` vào source của target.

- Code bên trong aspect có thể truy cập ITD private phù hợp.
- Code thông thường của target không đương nhiên có quyền nhìn member private-to-aspect.
- Introduced member public có thể trở thành một phần API được thấy rộng rãi.
- Visibility package/default gắn với package của aspect theo quy tắc công cụ, không mặc định là package của target.
- Theo tài liệu ngôn ngữ AspectJ liên quan, **không có protected inter-type member** cùng ngữ nghĩa với introduced public/private/default.

Cần đọc chính xác đặc tả access của compiler đang dùng; không đánh đồng với Java source member.

### 7.2. Khởi tạo field và side effects

Một inter-type field initializer có thể được chạy trong lifecycle tạo object. Theo ngữ nghĩa AspectJ đã công bố, initializer của introduced field chạy trước class-local initializers trong target.

Điều này có hậu quả:

- Field introduced có thể chưa nhìn thấy các giá trị target dự kiến.
- Initialization order phụ thuộc hierarchy và compilation/weaving.
- Không nên thực hiện I/O hoặc gọi method overridable một cách bừa bãi trong initializer.
- Không coi đưa thêm field vào object là một thay đổi không ảnh hưởng lifecycle.

Đặc biệt với **serialization**, object layout và state mới có thể thay đổi cách lưu/khôi phục; phải xem contract serialization cụ thể.

## 8. Giới thiệu constructor

Trong AspectJ có dạng khai báo ITD cho constructor:

```java
public Point.new(int x, int y) {
    this.x = x;
    this.y = y;
}
```

Đây là **mảnh minh họa tool-specific**, giả định `Point` cho phép các truy cập tương ứng và compiler chấp nhận các ràng buộc constructor; không phải code Java chạy trực tiếp bằng `javac`.

Tài liệu AspectJ mô tả introduced constructor làm type đích hỗ trợ thêm constructor, với một số giới hạn quan trọng:

- Không introduce constructor lên interface.
- Có thể xung đột với constructor được compiler tạo ngầm.
- **Inter-type constructor không thể gán giá trị cho `final` variable đã khai báo trong target type**, theo hạn chế của AspectJ.
- Vẫn phải tuân thủ constructor chain và các quy tắc khởi tạo được áp dụng.
- Constructor side effects/lifecycle không được bỏ qua.

### 8.1. Bẫy constructor mặc định

Một class Java không khai báo constructor có **implicit no-arg constructor** trong nhiều trường hợp. Thêm một ITD no-arg constructor không chắc là “thêm mới vì source không viết constructor nào”; nó có thể xung đột với constructor ngầm.

### 8.2. Không “vá” bất biến của class bằng constructor bất kỳ

Nếu target có final fields, validation bắt buộc hoặc yêu cầu factory riêng, một introduced constructor không tự bảo đảm các invariant ấy. Việc thêm constructor là **thay đổi public construction contract** và cần code review tương đương viết constructor trong class.

## 9. Giới thiệu quan hệ implements interface

Một trong những ứng dụng rõ nhất là **role introduction**.

Ví dụ AspectJ:

```java
declare parents: Order implements Auditable;
```

Ý nghĩa: loại `Order` được khai báo triển khai `Auditable` trong mô hình đã được weave. Tất nhiên, hợp đồng interface phải được thực hiện: method có sẵn trên target hoặc được đưa vào bằng ITD (theo quy tắc compiler).

Đây là thay đổi **quan hệ kiểu**, không chỉ thêm một object wrapper đang giữ `Order`.

### 9.1. Khác với Java dynamic proxy

JDK dynamic proxy có thể tạo một instance triển khai interface `Auditable` và giữ `Order` làm target, nhưng **class `Order` gốc không vì vậy implements Auditable**.

```text
ITD role:
  Order class (woven) implements Auditable
  order instanceof Auditable -> có thể true

Interface proxy:
  proxy implements Auditable
  order gốc instanceof Auditable -> không đổi
```

Không được kết luận kết quả của `instanceof` chỉ từ “đã cài AOP”; phải biết class nào đã weave, object nào đang dùng và loader nào định nghĩa type.

### 9.2. Vai trò của interface trong hợp đồng

Nên ưu tiên một interface **có nghĩa nghiệp vụ** và method rõ:

```java
public interface Auditable {
    void markAudited();
    int auditCount();
}
```

Đây là source Java hợp lệ khi lưu trong `Auditable.java`. ITD có thể làm type đích thỏa hợp đồng đó. Đừng thêm một marker interface rỗng chỉ để dễ cast mà không có ngữ nghĩa thực tiễn.

## 10. Giới thiệu quan hệ extends class

Một số ngôn ngữ ITD, như AspectJ, có dạng:

```java
declare parents: SomeType extends NewBaseType;
```

Đây là **cú pháp đặc thù ngôn ngữ AOP**, không phải một câu lệnh Java hợp lệ trong thân class thông thường.

Thay đổi superclass thường rủi ro hơn thêm interface vì Java class đã có chuỗi kế thừa và constructor/behavior phụ thuộc superclass.

Cần xác minh:

- Target và base class có tương thích hệ thống kiểu không?
- Target có là `final`/record/sealed hierarchy bị giới hạn không?
- Có làm mất/chồng chéo contract của superclass hiện hữu không?
- Constructor chain còn hợp lệ không?
- Có xung đột field/method inherited, overload hoặc override không?
- Serialization, equals/hashCode và lifecycle có phụ thuộc superclass cũ không?
- Classloader, module boundary và binary references có chấp nhận hierarchy sau weaving không?

**Không nên coi `declare parents ... extends` là thao tác thay superclass tùy ý, không điều kiện.** Một compiler/weaver nghiêm túc phải kiểm tra nhiều ràng buộc.

## 11. Ví dụ ITD hoàn chỉnh bằng AspectJ (không phải Java thuần)

Phần này cho thấy một cách **đúng bản chất structural crosscutting**: class `Order` không tự khai báo `Auditable`, nhưng aspect bổ sung field, methods và quan hệ implements.

**Tệp `Order.java`:**

```java
public class Order {
    private final String id;

    public Order(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
```

**Tệp `Auditable.java`:**

```java
public interface Auditable {
    void markAudited();
    int auditCount();
}
```

**Tệp `AuditIntroduction.aj` – cú pháp AspectJ:**

```java
public aspect AuditIntroduction {
    declare parents: Order implements Auditable;

    private int Order.auditMarks = 0;

    public void Order.markAudited() {
        auditMarks++;
    }

    public int Order.auditCount() {
        return auditMarks;
    }
}
```

**Tệp `Demo.java`:**

```java
public class Demo {
    public static void main(String[] args) {
        Order order = new Order("A-1");
        Auditable audit = (Auditable) order;

        System.out.println("id:" + order.id());
        System.out.println("start:" + audit.auditCount());

        audit.markAudited();
        audit.markAudited();

        System.out.println("after:" + audit.auditCount());
        System.out.println("same:" + (order == audit));
    }
}
```

### 11.1. Điều cần thấy

- `Order` trong source không viết `implements Auditable`.
- `AuditIntroduction` khai báo quan hệ implements.
- Aspect đưa `auditMarks` thành state được gắn với mỗi `Order` instance.
- Aspect đưa hai method đúng contract của `Auditable` vào target.
- `Demo` ép chính instance `order` sang `Auditable`; không tạo một wrapper khác.
- `same:true` minh họa reference qua interface và reference qua class đều chỉ tới cùng object.

### 11.2. Công cụ biên dịch bắt buộc

Không thể biên dịch `AuditIntroduction.aj` bằng `javac` thuần. Cần một bản **AspectJ compiler/weaver hỗ trợ source/target Java 17** cùng runtime phù hợp và phải đưa các class liên quan vào phạm vi compile/weave.

Ví dụ **lệnh tham khảo**, phụ thuộc phiên bản công cụ:

```bash
ajc -17 -d out Order.java Auditable.java AuditIntroduction.aj Demo.java
java -cp "out:<duong-dan-aspectjrt.jar>" Demo
```

Lệnh thứ hai minh họa classpath trên hệ điều hành dùng dấu `:`. Với Windows, dùng dấu `;` giữa các entry. Đường dẫn đến `aspectjrt.jar` phải thay bằng đường dẫn thật của phiên bản công cụ đang cài. Nếu dùng build tool, cấu hình nguồn Java và weave class theo tài liệu chính thức của toolchain.

**Không coi câu lệnh trên là lệnh độc lập có thể chạy trên máy chỉ cài JDK**. Phải có `ajc` và AspectJ runtime thích hợp. Tên flag/source level cần được xác minh với phiên bản compiler thực tế; các bản compiler cũ có thể không hỗ trợ Java 17.

Khi toolchain và weaving đúng, **đầu ra logic mong đợi**:

```text
id:A-1
start:0
after:2
same:true
```

Đây là kết quả theo ngữ nghĩa của chương trình minh họa, không phải chứng cứ rằng mọi phiên bản AspectJ/build configuration đã được chạy và kiểm thử ở đây.

### 11.3. Tại sao đây là ITD thay vì advice?

Ví dụ không có `before`, `after` hay `around`. Nó thực hiện **thay đổi cấu trúc**:

1. Một quan hệ implements mới.
2. Một instance field mới.
3. Hai instance methods mới.

Không có quy tắc tăng counter tự động khi `Order.id()` được gọi. Chỉ `markAudited()` chủ động tăng counter. Muốn tự tăng tại một join point, cần logic advice phù hợp hoặc sửa method body được chọn.

## 12. Java 17 standalone: phân biệt adapter/proxy với thay đổi type thật

Vì một bài học Java cần thấy chương trình chạy ngay cả khi không có compiler AOP, ví dụ sau dùng **Java 17 thuần** để so sánh:

- **Adapter** triển khai interface và **giữ target khác**.
- **Subclass được viết rõ trong Java source** có quan hệ subtype với target.
- ITD nếu có sẽ bổ sung khả năng vào type gốc theo mô hình weaving riêng; chương trình Java dưới đây **không thực hiện ITD**.

Tạo file `StructureVsWrapperDemo.java`:

```java
public class StructureVsWrapperDemo {
    interface Auditable {
        void markAudited();
        int auditCount();
    }

    static class Order {
        private final String id;

        Order(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    static final class OrderAdapter implements Auditable {
        private final Order target;
        private int marks;

        OrderAdapter(Order target) {
            this.target = target;
        }

        @Override
        public void markAudited() {
            marks++;
        }

        @Override
        public int auditCount() {
            return marks;
        }

        Order target() {
            return target;
        }
    }

    static final class ExtendedOrder extends Order
            implements Auditable {
        private int marks;

        ExtendedOrder(String id) {
            super(id);
        }

        @Override
        public void markAudited() {
            marks++;
        }

        @Override
        public int auditCount() {
            return marks;
        }
    }

    public static void main(String[] args) {
        Order raw = new Order("A-1");
        OrderAdapter adapter = new OrderAdapter(raw);

        System.out.println("raw:type:" + raw.getClass().getSimpleName());
        System.out.println("raw:auditable:" + (raw instanceof Auditable));
        System.out.println("adapter:type:"
                + adapter.getClass().getSimpleName());
        System.out.println("adapter:same-target:"
                + (adapter.target() == raw));

        adapter.markAudited();
        System.out.println("adapter:count:" + adapter.auditCount());
        System.out.println("raw:still:"
                + (raw instanceof Auditable));

        Order extended = new ExtendedOrder("B-2");
        System.out.println("extended:type:"
                + extended.getClass().getSimpleName());
        System.out.println("extended:auditable:"
                + (extended instanceof Auditable));

        Auditable role = (Auditable) extended;
        role.markAudited();
        role.markAudited();
        System.out.println("extended:count:" + role.auditCount());
        System.out.println("extended:same-reference:"
                + (extended == role));
    }
}
```

Biên dịch/chạy chỉ với JDK 17:

```bash
javac StructureVsWrapperDemo.java
java StructureVsWrapperDemo
```

**Stdout xác định:**

```text
raw:type:Order
raw:auditable:false
adapter:type:OrderAdapter
adapter:same-target:true
adapter:count:1
raw:still:false
extended:type:ExtendedOrder
extended:auditable:true
extended:count:2
extended:same-reference:true
```

### 12.1. Ý nghĩa của `raw:still:false`

Tạo `new OrderAdapter(raw)` **không sửa type `Order`** và không làm biến `raw` triển khai interface. Chỉ adapter triển khai interface và giữ tham chiếu tới `raw`.

Đây là khác biệt quan trọng giữa **wrapper/delegation** và **structural introduction**.

### 12.2. Ý nghĩa của `extended:auditable:true`

`ExtendedOrder` được Java source khai báo `implements Auditable`, nên nó là subtype. Tuy nhiên `Order` gốc vẫn không trở thành Auditable.

Một ITD thành công có thể khiến chính `Order` thỏa interface sau weaving, không cần đổi source `Order.java`.

### 12.3. Không nên dùng ví dụ Java thuần để “chứng minh” hành vi của một ITD engine

Java thuần không có cú pháp `public void Order.markAudited()`. Chương trình so sánh **cơ chế thay thế tương tự** về sử dụng object/type, nhưng không thực hiện biến đổi class file. Để kiểm ITD thật, phải xây, weave và chạy ví dụ ở phần trước bằng công cụ tương ứng.

## 13. Vai trò chung trên nhiều class qua interface

Giả sử ba kiểu `Point`, `Line` và `Shape` cần có năng lực `HasLabel` nhưng không chia sẻ superclass thích hợp.

Một số ngôn ngữ ITD cho phép:

1. Khai báo một interface dùng làm role.
2. Khai báo nhiều target type implements interface.
3. Đưa field/method dùng chung vào interface role theo cơ chế ITD của công cụ.

Trong AspectJ, tài liệu minh họa kiểu marker interface được introduced vào nhiều target, sau đó introduce field/method trên interface ấy:

```java
aspect LabelFeature {
    private interface HasLabel {}

    declare parents: (Point || Line || Shape)
        implements HasLabel;

    private String HasLabel.label;

    public String HasLabel.getLabel() {
        return label;
    }
}
```

Đây là **mảnh AspectJ minh họa**, giả định các target types tồn tại; không phải một ứng dụng hoàn chỉnh chạy với `javac`.

Ý nghĩa: mô-đun một role thành **một phần mở rộng dùng chung**, thay vì lặp khai báo field và method cho từng type.

### 13.1. Đừng nhầm với interface field của Java thuần

Trong Java thuần, field khai báo trong interface có quy tắc cố định của ngôn ngữ (`public static final`). Mô hình inter-type member của AspectJ có thể cho phép khai báo **instance field liên quan đến interface role** như một phần khả năng structural weaving.

Đây là tính năng **ngôn ngữ AOP riêng**, không phải bằng chứng rằng Java source interface thuần có thể chứa mutable instance fields.

### 13.2. Tránh pattern target quá rộng

Một type pattern chọn hàng trăm class có thể:

- Gắn thêm role cho những type không thực sự phù hợp.
- Tạo xung đột method/field ở class con.
- Khiến một bản nâng cấp thư viện phát sinh lỗi weave.
- Tác động serialization hoặc reflection trên diện rộng.
- Thay đổi biểu hiện API mà đội sở hữu class không được thông báo.

Ưu tiên target type pattern nhỏ, có review và test compile/weave regression.

## 14. ITD và mô hình kế thừa Java

Java có những ràng buộc cơ bản:

- Một class chỉ có **một superclass** trực tiếp theo hệ thống kế thừa class Java.
- Một class có thể implement nhiều interface hợp lệ.
- `final class` không thể bị subclass trực tiếp bằng cơ chế Java thường.
- Sealed classes/interfaces giới hạn những subtype được phép.
- Records là class với ràng buộc cấu trúc riêng, ngầm final và không cho tự thêm instance field thông thường.
- Một số loại kiểu/binary có giới hạn đặc biệt theo VM, module, compiler hoặc Java language version.

ITD không phải “đặc quyền bỏ qua mọi ràng buộc Java”. Một engine phù hợp phải tôn trọng type safety và những quy tắc VM, hoặc công bố chính xác giới hạn biến đổi hỗ trợ.

### 14.1. Sealed types và permits

Nếu một sealed interface chỉ permit một số implementation, việc introduce nó làm parent cho một class ngoài tập permitted có thể không hợp lệ. Cần kiểm tra **trạng thái hierarchy sau weave** và sự tương thích với ngữ nghĩa của Java version đang dùng.

Không giả định ITD có thể tự thêm type vào sealed hierarchy mà không cần compiler và module coherence.

### 14.2. Record và giá trị bất biến

Một record Java có component fields và các quy tắc construction, equality, serialization riêng. Thêm arbitrary mutable instance field bằng ITD có thể vi phạm giới hạn class-file/ngôn ngữ hoặc không được tool hỗ trợ. Khi cần giữ metadata ngoài record, một **external association map**, event log hoặc wrapper có thể phù hợp hơn, nhưng lại có ownership/GC semantics riêng.

### 14.3. Enum, native và JDK types

Các kiểu đặc biệt hoặc class do hệ thống nạp có thể không thuộc phạm vi weaving. Không suy rằng bất kỳ binary nào cũng biến đổi được chỉ vì có bytecode trên disk. Class loader bootstrap, package/module integrity và ràng buộc JVM có thể ngăn hoặc giới hạn thao tác.

## 15. Khả năng truy cập và quyền sở hữu module

Một introduced method công khai có thể thay đổi API consumer nhìn thấy. Nhưng cần phân biệt:

- Visibility theo ngôn ngữ ITD.
- Java visibility/access flags sau weaving.
- Package chứa aspect và package chứa target.
- Module export/open, access qua reflection.
- Classloader defining type.
- Quyền truy cập field/method private/protected của target.

Trong AspectJ, một aspect **privileged** có khả năng truy cập member không public của type khác theo cơ chế công cụ. Đây là lựa chọn có rủi ro bảo trì và quyền truy cập lớn, không phải yêu cầu bắt buộc cho mọi ITD.

### 15.1. Tránh yêu cầu privileged khi có API công khai đủ dùng

Nếu introduced method chỉ cần gọi `target.id()` public, đừng phụ thuộc private field nội bộ của target khi không cần. Library upgrade có thể đổi field layout nhưng vẫn giữ public API; phụ thuộc vào private details khiến weaving dễ vỡ.

### 15.2. Không tự nhận định JPMS luôn cho phép

Trong Java Platform Module System, `exports` và `opens` kiểm soát khả năng truy cập/reflective access theo module. Việc weaving hoặc định nghĩa class có thể có yêu cầu riêng. Cần thử với **module graph, class loader và packaging giống production**.

Một ví dụ chạy trên classpath đơn giản có thể không đủ chứng minh nó chạy trong named module, plugin loader hay môi trường có signed JAR.

## 16. Va chạm chữ ký và xung đột member

Các tình huống dễ xảy ra:

1. Target đã có một method đúng tên/parameter types mà aspect muốn introduce.
2. Hai aspect cùng introduce method/field công khai có thể nhìn thấy vào cùng type.
3. Một superclass hoặc interface đã có method khiến introduced method gây ambiguous inheritance.
4. Method mới ảnh hưởng overload resolution hoặc generic type erasure.
5. Một field công khai cùng tên tạo mơ hồ khi code truy cập.
6. Target thay đổi trong phiên bản mới, vô tình thêm method/field trùng ITD cũ.

### 16.1. Không coi compile conflict là lỗi nhỏ để bỏ qua

Trong Java, một call site có thể được overload resolution quyết định ở compile time. Thêm overload có thể làm mã nguồn cũ **không còn biên dịch** hoặc chọn method khác sau khi biên dịch lại, ngay cả khi binary cũ vẫn còn chạy.

Vì vậy introduced public members là một **thay đổi API thật** và cần quản lý tương thích.

### 16.2. Tương tác giữa nhiều ITD

Theo đặc tả AspectJ, một số xung đột giữa inter-type members có thể được giải quyết theo quy tắc inheritance hoặc precedence của declaring aspects. Tuy nhiên không nên dựa vào precedence mơ hồ để hợp thức hóa hai declaration cùng chủ đích khác nhau.

Cần:

- Chỉ định một owner cho capability.
- Đánh giá thứ tự/precedence khi thật sự có ý nghĩa.
- Kiểm tra build error/warning.
- Thử full compile/weave với tất cả aspect bật.
- Có test cho lựa chọn overload và dispatch kỳ vọng.

**Precedence của advice** và **xử lý xung đột ITD** là hai mảng ngữ nghĩa liên quan nhưng không thể chỉ dựa trên một câu “aspect A chạy trước aspect B” để hiểu.

## 17. Constructor, initialization và invariant

Một introduced field có default value, nhưng method target có thể quan sát object trong constructor khi state chưa hoàn tất. ITD đặt thêm logic vào initialization chain nên cần kiểm tra:

- Thứ tự field initializer.
- Superclass constructor có gọi method overridable không.
- Target có công bố `this` trước khi hoàn tất init không.
- Introduced method được gọi ở giai đoạn nào.
- Field introduced có thread-safe nếu object được chia sẻ không.
- Có cần idempotent setup hay teardown không.

Một invariant ví dụ:

```text
auditCount >= 0
owner != null before public business operation
```

Nếu introduced field được cập nhật bằng `++` đồng thời trên nhiều thread, **không tự thread-safe**. Java type checking không chứng minh atomicity của phép cập nhật.

### 17.1. Đếm số lần bằng `int++` không phù hợp concurrency nghiêm ngặt

`auditMarks++` trong ví dụ đơn luồng là chủ ý đơn giản. Với counter concurrent cần xem atomic operations/synchronization hoặc chọn mô hình thread confinement.

Không mặc định inter-type instance field “mỗi object có state riêng” đồng nghĩa “state luôn nhất quán trong mọi luồng”.

## 18. Bảo toàn API khi mở rộng cấu trúc

Từ góc nhìn consumer, một thay đổi ITD có thể được xem như:

- Thêm method vào concrete type.
- Thêm interface được triển khai.
- Thêm constructor công khai.
- Thêm field quan sát được qua reflection.
- Thay đổi superclass hoặc inherited methods.

Những thay đổi ấy có thể ảnh hưởng:

- Source compatibility khi biên dịch lại.
- Binary compatibility nếu artifact woven và consumer compile với cấu trúc không đồng bộ.
- Reflection và các thư viện dựa runtime class metadata.
- Serialization/deserialization.
- Dependency injection/lookup theo interface.
- Logging/JSON mapping dựa trên getter.
- Method handles, caches theo class hoặc constructor discovery.

### 18.1. “Thêm method” có thể là breaking change

Dù Java thường chấp nhận thêm method, trong hệ thống có nhiều overload, interface inheritance hoặc reflective mapper, một introduced method mới có thể làm call resolution/serialization thay đổi. Đánh giá bằng compiler và integration tests, không chỉ nhìn diff mã gốc.

### 18.2. Có bản weave nhưng consumer dùng bản chưa weave

Hai module có thể cùng tham chiếu tên `Order` nhưng khác artifact:

```text
Module A compiled against woven Order
Module B loads original unwoven Order
```

Có nguy cơ linkage/typing/runtime error hoặc thiếu method theo ngữ cảnh. Phải bảo đảm **artifact/loader version consistency** và test packaging cuối cùng, đặc biệt khi dùng incremental build, multi-module, shaded JAR hoặc plugin architecture.

## 19. ITD khi biên dịch, weaving và thời điểm nạp class

Các mô hình triển khai có thể khác nhau:

### 19.1. Compile-time weaving

Compiler thấy source target, aspect và khai báo ITD; nó có thể đưa cấu trúc được khai báo vào binary cuối.

Ưu điểm:

- Dễ báo lỗi type conflict khi build.
- Consumer source có thể kiểm kiểu với model đã kết hợp khi build đúng.
- Có thể inspect artifact trước deployment.

Rủi ro:

- Build config phải đưa đủ nguồn và compiler phù hợp.
- Multi-module cần quy định dependency và thứ tự compile/weave.
- IDE không được tích hợp có thể hiểu sai method introduced.

### 19.2. Binary weaving/post-compile weaving

Class files đã được compile trước có thể được một weaver biến đổi thêm.

Cần xác minh:

- Có được chỉnh sửa artifact đó không?
- Class file version có hỗ trợ không?
- Consumer compile dựa trên API nào?
- Digital signature/JAR integrity có bị ảnh hưởng không?
- Metadata, debug symbols hoặc tool instrumentation khác có xung đột không?

### 19.3. Load-time weaving

Class được xử lý lúc nạp theo cấu hình weaver/loader.

Các vấn đề:

- Class đã nạp từ trước có được biến đổi kịp không?
- Parent-first/child-first loader chọn bản nào?
- Module access và bytecode verification có hợp lệ không?
- Instrumentation khác có đua nhau biến đổi class không?
- Nếu consumer đã compile với ITD nhưng runtime không weave class, có lỗi linkage không?

**Không tự cho rằng mọi biến đổi cấu trúc đều có thể áp dụng bằng retransformation sau khi class đã nạp.** Java instrumentation/redefinition APIs có những giới hạn riêng về thay đổi schema (method/field/hierarchy); công cụ phải thực hiện đúng phạm vi được VM hỗ trợ.

## 20. So sánh với mixin, decorator, composition và extension method

| Kỹ thuật | Method xuất hiện trên target class thật? | Thay đổi instanceof của target? | Cần chú ý |
| --- | --- | --- | --- |
| ITD được weave thành công | Có thể có | Có thể khi introduce interface | Compiler/weaver và compatibility |
| Java interface default method | Chỉ khi type triển khai interface theo Java type rules | Chỉ khi type implements interface | Không tự thêm interface vào một type cũ |
| Java subclass viết thủ công | Trên subclass; target superclass gốc không đổi | Instance của subclass có các subtype hợp lệ | Constructor/final/hierarchy |
| Adapter/decorator | Trên adapter/decorator | Target gốc không đổi | Reference và identity khác |
| JDK dynamic proxy | Trên proxy implements interface | Target gốc không đổi | Lời gọi phải qua proxy |
| Mixin library/runtime | Tùy công cụ | Tùy công cụ | Không mặc định là bytecode ITD |
| Static utility method | Không | Không | Caller gọi qua utility/facade |
| Bytecode advice weaving | Không tự thêm member chỉ vì advice chạy | Không tự thay interface | Dynamic vs structural crosscutting |

**Đừng gọi mọi kiểu “thêm hành vi” là ITD.** Nếu dữ liệu được giữ trong `WeakHashMap<Object,State>` bên ngoài target, đó là **association bên ngoài**, không nhất thiết là introduced instance field.

### 20.1. Khi adapter tốt hơn ITD

- Ứng dụng không sở hữu JAR vendor.
- Không thể kiểm soát full compile/weave pipeline.
- Không muốn thay đổi public type structure.
- Chỉ cần thêm role ở một ranh giới API do mình sở hữu.
- Cần dễ gỡ tính năng khi nâng cấp thư viện.
- Muốn hỗ trợ những class bị hạn chế hierarchy.

### 20.2. Khi ITD có giá trị khác biệt

- Các consumer thực sự cần **static type relationship** được introduced.
- Nhiều target types chia sẻ cùng role nhưng không tiện sửa source.
- Cần state per-instance được liên kết trực tiếp với target trong phạm vi công cụ cho phép.
- Build/test/package có thể kiểm soát và xác minh weaving xuyên các module liên quan.

## 21. ITD và aspect lifetime là hai chuyện khác nhau

Một aspect có thể có lifecycle theo singleton, per-object, per-control-flow hoặc mô hình khác tùy engine. Nhưng introduced instance field có ý nghĩa **trạng thái trên target**, không phải mặc định là field trên aspect instance.

Ví dụ:

```text
One aspect instance
  |
  +-- Order A has introduced auditMarks=1
  +-- Order B has introduced auditMarks=3
```

Đừng suy rằng singleton aspect làm tất cả Order cùng một counter. Ngược lại, nếu aspect giữ `static counter` hoặc mutable field của chính aspect thì có thể **chia sẻ** theo lifecycle ấy.

Kiểm thử phải tách:

- State riêng từng target.
- State riêng mỗi aspect instance.
- State static/shared.
- State theo thread/invocation.
- State được lưu ở external registry.

Các mô hình state khác nhau làm ra kết quả concurrency và serialization khác nhau.

## 22. ITD và việc gọi method từ mã đã biên dịch

Một method được introduced có thể được gọi:

- Từ source được compile cùng toolchain hỗ trợ ITD.
- Qua interface introduced sau weaving.
- Qua reflection sau khi class được load đã có member.
- Từ code đã compile trước với một API phù hợp khi runtime giữ đúng class contract.

Không chắc source được compile hoàn toàn bằng `javac` thuần **trước khi** có ITD sẽ biết member mới trong symbol table.

Một tình huống điển hình:

```text
Compile consumer against original Order:
    order.auditCount()  // compiler Java thuần không thấy method

Compile/weave consumer and Order under ITD-aware toolchain:
    method có thể trở thành symbol hợp lệ
```

**Source visibility và runtime classfile visibility là hai pha khác nhau**; phải chỉ rõ compiler nào và artifact nào được consumer dùng. Không chỉ nói “woven class có method nên mọi source Java ở mọi module sẽ tự compile”.

## 23. Xung đột với private method và encapsulation

ITD cần tôn trọng mô hình access. AspectJ còn cung cấp cơ chế privileged aspect, nhưng đó là khả năng quyền cao và có thể tạo coupling với nội bộ target.

Giới hạn thiết kế:

- Không truy cập field private vendor chỉ để tiết kiệm một getter nếu không bắt buộc.
- Không introduce public method tiết lộ state nhạy cảm.
- Không tạo method cùng chữ ký nhưng contract khác với API target.
- Không thay invariants mà đội sở hữu target không biết.
- Không tạo một role làm tăng quyền thao tác vượt security model.
- Không coi `private` của ITD có cùng phạm vi với Java private trong target.

Đây không chỉ là bài toán cú pháp: ITD có thể thay **bề mặt API của class** và do đó cả trust boundary.

## 24. Serialization và persistence

Một introduced field có thể khiến trạng thái bổ sung tồn tại trên object. Nhưng từ đó không thể tự suy rằng:

- Java serialization sẽ lưu đúng field theo phiên bản cũ.
- Thêm interface `Serializable` là đủ để object hợp lệ cho serialization.
- Khôi phục object bỏ qua constructor luôn khởi tạo introduced field như ý.
- ORM hoặc JSON mapper sẽ nhìn field/method theo cách bạn mong đợi.
- Schema persistence tự động cập nhật.
- Version cũ/phiên bản mới của woven binary trao đổi dữ liệu an toàn.

Cần quy định:

1. State introduced có cần bền vững không?
2. Nếu không, có thể coi nó là transient/derived không?
3. Migration khi version thêm/bớt member sẽ như thế nào?
4. Giữ invariant ra sao khi deserialization?
5. Contract của equals/hashCode/toString có phụ thuộc state mới không?
6. Có phản ánh field qua reflection/introspection ngoài chủ đích không?

**ITD thay đổi cấu trúc, không tự quản lý migration.**

## 25. Nhiều aspect cùng mở rộng một type

Một codebase có thể có:

```text
TraceFeature -> Order.traceId
AuditFeature -> Order.auditMarks
CacheFeature -> Order.cacheStatus
```

Nếu mỗi tính năng độc lập và tên/contract rõ, modularity có thể cải thiện. Nhưng nếu:

- Hai aspect introduce cùng public method.
- Một method của feature A dựa vào field private của feature B.
- Parent interface tạo phương thức inherited xung đột.
- Một update thêm method mới vào target và đụng ITD.
- Một feature bị disable nhưng consumer vẫn compile với API introduced.

thì hệ thống có thể lỗi compile/weave hoặc chạy không tương thích.

### 25.1. Thiết kế trách nhiệm sở hữu

Nên có tài liệu:

- Aspect owner.
- Target type pattern cụ thể.
- Introduced member signature và visibility.
- Hợp đồng method/field, initialization.
- Các feature/aspect phụ thuộc.
- Tương thích khi tắt tính năng.
- Điều kiện build/weave cần bật đồng thời.

Không đặt toàn bộ tính đúng đắn vào **thứ tự aspect ngẫu nhiên**.

### 25.2. Thay đổi API qua một feature toggle

Tắt một before advice có thể chỉ đổi hành vi runtime, nhưng tắt một ITD có thể khiến **member hoặc interface biến mất khỏi class**. Đây là thay đổi type schema lớn hơn.

Vì vậy ITD không thích hợp cho một feature flag biến động per-request nếu consumer phụ thuộc static type introduced. Hãy xem nó như một phần cấu hình artifact/deployment nhất quán.

## 26. Kiểm thử ITD ở ba lớp

### 26.1. Compile/weave tests

Kiểm thử rằng:

- Aspect và target compile/weave thành công.
- Interface role được introduced.
- Method/field/constructor được tạo với signature mong đợi.
- Không có xung đột inheritance/erasure/visibility.
- Công cụ báo đúng lỗi với một declaration cố ý không hợp lệ.
- Build thực tế dùng đúng compiler/weaver version.

### 26.2. Runtime behavior tests

Kiểm thử:

- `instanceof`/cast theo role introduced.
- Method introduced chạy đúng và giữ invariant.
- Hai target instances có state introduced độc lập.
- Exception propagation và side effect đúng contract.
- Concurrency phù hợp; không mất update khi dùng chung object.
- Class loader cuối cùng thực sự nạp **woven binary**.
- Reflection chỉ thấy những member intended.
- Constructor mới khởi tạo state đúng nếu có dùng.

### 26.3. Regression/integration tests

Kiểm thử:

- Khởi động ứng dụng/module production-like.
- Consumer compile với exposed APIs.
- Class/module version giữa producer và consumer nhất quán.
- Serialized data cũ còn tương thích nếu được yêu cầu.
- Không xung đột với các aspect khác.
- Không có đổi behavior ngoài dự kiến ở overload/equals/hashCode.
- Tắt/bỏ ITD dẫn tới failure rõ thay vì lỗi ngầm muộn.
- Thư viện vendor nâng cấp không làm type pattern chọn nhầm đối tượng.

## 27. Ma trận kiểm thử nên có

| Ca | Điều muốn chứng minh |
| --- | --- |
| Chưa áp dụng weaving | Source target nguyên bản không có member mới |
| Đã weave thành công | Type đúng hỗ trợ member/interface mới |
| Cast vào interface introduced | Thành công đúng phạm vi |
| Cast ở type không được chọn | Không tự trở thành role |
| Gọi method introduced | Kết quả/side effect đúng |
| Hai instances | Field introduced không lẫn state |
| Constructor introduced | Chuỗi khởi tạo/invariant phù hợp |
| Existing method conflict | Compiler/weaver báo lỗi hoặc xử lý đúng đặc tả |
| Generic erasure clash | Không phát sinh signature trùng ngoài chủ đích |
| Existing superclass/interface | Không phá luật type hierarchy |
| Final/sealed/record | Giới hạn được công cụ kiểm đúng |
| Module/classloader | Artifact nạp đúng ở runtime |
| Consumer biên dịch bằng compiler khác | Không giả định thấy symbol ITD nếu không có metadata đúng |
| Serialization | Versioning và state persistence theo hợp đồng |
| Concurrent updates | Không mất state do race |
| Multi-aspect ITDs | Không xung đột public member/precedence |
| Bỏ một ITD | Consumer được báo breaking change rõ |
| Build sạch vs incremental | Cùng cấu trúc class cuối |
| Binary weaving trên thư viện ngoài | Có quyền và kiểm tra integrity |
| Reflection mapping | Không lộ/thêm property ngoài ý muốn |

### 27.1. Kiểm tra giới hạn bằng negative tests

Một bộ test chỉ chứng minh ITD hoạt động trên class dễ nhất không đủ. Nên có các ca **không được introduce** vào type ngoài scope, không được bỏ qua conflict, và phải thất bại rõ nếu loader không nạp woven binary.

### 27.2. Source vs binary verification

Một số tool có thể hiển thị cấu trúc introduced khác nhau trong decompiler/reflection nhưng ngữ nghĩa vẫn được công cụ bảo đảm. Khi cần xác minh ở mức bytecode, kiểm tra class artifact được đóng gói và chạy thật. Không dựa vào mã Java nguồn chưa weave như bằng chứng cuối.

## 28. Khi nào không nên dùng ITD?

Không nên mặc định dùng ITD nếu:

- Chỉ cần ghi log trước/sau method: **advice** có thể phù hợp hơn.
- Chỉ cần gắn thêm hành vi ở biên API do mình sở hữu: **adapter/decorator** có thể đơn giản hơn.
- Không kiểm soát compiler/weaver hoặc deployment artifacts.
- Target là class không thể cấu trúc lại an toàn theo Java type rules.
- Một SDK vendor thay đổi liên tục và không có hợp đồng binary ổn định.
- Cần bật/tắt capability từng request nhưng client đã compile phụ thuộc interface introduced.
- Có rủi ro serialization, ORM mapping hoặc module access lớn hơn lợi ích.
- ITD chỉ che giấu một vấn đề thiết kế trách nhiệm/ownership đáng lẽ cần refactor.
- Giải pháp phải dễ hiểu bởi các đội chỉ dùng Java compiler và reflection thông thường.

**Giải pháp ít xâm lấn hơn thường đáng ưu tiên**, trừ khi vai trò và type structure bổ sung là một nhu cầu thực sự.

## 29. Các hiểu nhầm thường gặp

**“AOP chỉ có before/after/around.”** Không phải mọi mô hình. Một số ngôn ngữ AOP còn hỗ trợ structural crosscutting, gồm ITD.

**“Một around advice có thể làm target implements interface mới.”** Không tự động. Advice không đồng nghĩa thay type schema.

**“JDK proxy triển khai Auditable nghĩa Order gốc cũng implements Auditable.”** Sai. Proxy và target là những đối tượng/kiểu khác nhau.

**“ITD là sửa source code trực tiếp.”** Không nhất thiết. Công cụ có thể weave class từ source/binary mà không sửa file nguồn.

**“ITD dùng được với mọi engine AOP.”** Sai. Phải có ngôn ngữ/compiler/weaver hỗ trợ.

**“Introduced field là field singleton của aspect.”** Không khi nó được khai báo là instance field trên target.

**“`private` trong inter-type member có nghĩa đúng như private field viết trong target source.”** Không thể suy như vậy; theo AspectJ nó private đối với declaring aspect.

**“Introduced constructor có thể tùy tiện gán final field của target.”** Không với giới hạn được AspectJ công bố.

**“Không viết constructor trong source nghĩa không có constructor để xung đột.”** Sai. Java có quy tắc implicit constructor.

**“Declare parents extends bỏ qua mọi luật kế thừa Java.”** Không. Type hierarchy vẫn phải hợp lệ.

**“Introduce interface là cách bỏ qua sealed permits.”** Không được giả định; cần thỏa quy tắc type/JVM và tool support.

**“ITD chỉ thay đổi behavior, không thay compatibility.”** Sai. Public method/interface/constructor mới là thay đổi cấu trúc API.

**“Dùng compiler AOP một lần là mọi module tự nhìn thấy ITD.”** Không. Các consumer, classloaders và runtime artifacts phải nhất quán.

**“Thêm field là an toàn cho serialization/ORM.”** Không. Có thể đổi layout, reflection, schema và migration.

**“Có role mới nghĩa mọi instance đang chạy đều được biến đổi ngay.”** Không thể mặc định, nhất là class đã load và giới hạn redefinition.

**“Mọi introduced member có thể được xác minh chỉ qua test return value.”** Không. Cần type, reflection, binary, initialization và integration tests.

## 30. Bài tập thực hành

### Bài 1 — Advice hay ITD?

Yêu cầu: trước mỗi lần gọi `Order.submit()`, ghi thời gian bắt đầu. Có nhất thiết cần ITD không?

**Đáp án:** Không. Nếu chỉ quan sát thời điểm chạy, before/around advice thường trực tiếp hơn. Chỉ cân nhắc ITD nếu cần cấu trúc/state mới có ý nghĩa trên type.

### Bài 2 — Role cho class legacy

`Order` không implements `Auditable`; consumer muốn nhận chính instance Order dưới interface Auditable trong artifact woven. Cơ chế nào phù hợp?

**Đáp án:** ITD `declare parents ... implements` cùng method implementations có thể phù hợp nếu engine hỗ trợ và class/weaving hợp lệ. Adapter chỉ làm wrapper implements interface, không đổi type Order.

### Bài 3 — Trạng thái nhiều instance

Introduced field `private int Order.marks`, tạo hai Order và gọi `mark()` hai lần trên object thứ nhất. Object thứ hai có mặc định count bằng 2 không?

**Đáp án:** Không; introduced **instance field** gắn với từng object theo ngữ nghĩa target state. Các lần cập nhật object thứ nhất không mặc định cập nhật object thứ hai.

### Bài 4 — Xung đột method

Sau khi triển khai ITD `Order.auditCount()`, tác giả Order thêm method cùng chữ ký vào version kế tiếp. Điều gì cần làm?

**Đáp án:** Kiểm tra compiler/weaver conflict, quyết định ownership/cấu trúc mới và cập nhật contract; không tùy tiện mặc định hai method cùng tồn tại hay một method luôn thắng.

### Bài 5 — Class final

`FinalOrder` là final nhưng có public API cho phép viết adapter. Có nhất thiết biến nó thành subclass để thêm role?

**Đáp án:** Không thể subclass final bằng Java thông thường. Adapter là lựa chọn tách biệt; ITD thích hợp đến đâu phụ thuộc loại introduction và giới hạn công cụ, không được đoán rằng final có thể bị bỏ qua.

### Bài 6 — Constructor ngầm

Target source không khai báo constructor. Một ITD muốn introduce no-arg constructor. Có chắc không conflict?

**Đáp án:** Không. Java có implicit default constructor khi điều kiện phù hợp; cần kiểm tra chữ ký và rules của compiler AOP.

### Bài 7 — Java 17 thuần

Vì sao lệnh `javac Order.java AuditIntroduction.aj` không hợp lệ trong ví dụ ITD?

**Đáp án:** `.aj` chứa cú pháp AspectJ, không phải Java source thuần. Cần compiler/weaver hỗ trợ và đúng build configuration.

### Bài 8 — Runtime không weave

Consumer compile thành công với introduced method nhưng runtime nạp `Order.class` nguyên gốc. Hậu quả có thể là gì?

**Đáp án:** Runtime type/member thiếu so với contract lúc compile; có thể có linkage hoặc cast/method resolution error tùy call path. Phải kiểm artifact và loader đúng.

### Bài 9 — Bảo mật

Một introduced public method trả nội dung token private của target. “Chỉ thêm method” có vô hại không?

**Đáp án:** Không. Nó mở rộng API và có thể làm lộ dữ liệu. Phải đánh giá security/ownership/trust boundary.

### Bài 10 — Concurrent counter

Introduced field `int marks` tăng bằng `marks++` trên cùng Order từ nhiều thread. Có chắc số cuối bằng tổng lượt gọi?

**Đáp án:** Không. `++` không bảo đảm atomic update giữa nhiều thread; phải dùng cơ chế đồng bộ/atomic phù hợp nếu contract đòi đếm chính xác.

## 31. Checklist đánh giá trước khi áp dụng ITD

- [ ] Mục tiêu thực sự cần **thay đổi cấu trúc type**, không chỉ can thiệp method invocation.
- [ ] Công cụ đang dùng hỗ trợ đúng kiểu ITD yêu cầu.
- [ ] Đã phân biệt cú pháp tool-specific với Java 17 thuần.
- [ ] Biết declaring aspect và target type, type patterns đủ hẹp.
- [ ] Đã chỉ rõ fields/methods/constructors/parents cần giới thiệu.
- [ ] Đã kiểm type/signature, erasure, overload và inheritance conflicts.
- [ ] Đã kiểm visibility của inter-type member theo ngữ nghĩa công cụ.
- [ ] Không tiết lộ private state qua introduced public API.
- [ ] Đã đánh giá initializer order, constructor chain và invariant.
- [ ] Không tự tin rằng ITD constructor có thể gán final fields target.
- [ ] Đã kiểm final/sealed/record/enum và hierarchy constraints.
- [ ] Đã kiểm source/binary/load-time weaving support.
- [ ] Consumer modules compile với model type đã introduced.
- [ ] Runtime load đúng woven binary; loader/module cấu hình khớp.
- [ ] Không giả định structural redefinition áp được lên class đã load.
- [ ] Có kế hoạch ownership/compatibility khi library upgrade.
- [ ] Có chính sách với nhiều aspects cùng introduce members.
- [ ] Giới hạn reflection, serialization/ORM schema và persistence migration đã được xem xét.
- [ ] Đã kiểm thread safety của introduced mutable state.
- [ ] Có positive/negative compile-time tests và runtime tests.
- [ ] Đã thử với deployment/package/profiles gần production.
- [ ] Đã ghi rõ contract và breaking changes khi thêm/bỏ role.
- [ ] Có phương án rollback/alternative adapter nếu weaving thất bại.
- [ ] Không tự coi proxy/decorator là ITD thật.
- [ ] Đã tham khảo tài liệu chính thức đúng phiên bản compiler đang dùng.

## 32. Tài liệu đối chiếu

Các nguồn sau mô tả **ngữ nghĩa của AspectJ, một công cụ AOP có ITD**. Chúng không làm ITD trở thành tính năng phổ quát của mọi cơ chế AOP:

- [Eclipse AspectJ – Inter-type declarations](https://eclipse.dev/aspectj/doc/released/progguide/language-interType.html): các loại member, `this`, visibility và role introduction.
- [Eclipse AspectJ – Static crosscutting](https://eclipse.dev/aspectj/doc/released/progguide/semantics-declare.html): grammar và quy tắc method/field/constructor/parent declarations, conflicts, initialization, access modifiers.
- [Eclipse AspectJ – Quick reference](https://eclipse.dev/aspectj/doc/latest/progguide/quickreference.html): cú pháp ITD và `declare parents`.
- [Eclipse AspectJ – Roles and views](https://eclipse.dev/aspectj/doc/released/progguide/examples-basic.html): ví dụ role introduction qua các class có sẵn.
- [Java SE 17 – Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): để đối chiếu dynamic proxy **không thay type của target**.
- [Java Language Specification 17](https://docs.oracle.com/javase/specs/jls/se17/html/index.html): quy tắc kiểu, inheritance, constructor, final và sealed types.

Muốn dùng một compiler/version cụ thể, kiểm tra tài liệu phát hành, hỗ trợ bytecode Java tương ứng và thử compile/weave trên đúng classpath/module path thực tế.

## 33. Kết luận

**Inter-type declaration là cách mô-đun hóa một thay đổi về cấu trúc của kiểu, không chỉ chèn thêm lệnh trước/sau lời gọi.** Khi được compiler/weaver hỗ trợ, aspect có thể giới thiệu fields, methods, constructors và quan hệ implements/extends theo quy tắc ngôn ngữ, giúp những type hiện hữu nhận thêm vai trò hoặc năng lực rõ ràng.

Giá trị lớn nhất là **structural crosscutting có chủ đích**. Nhưng hệ quả đi kèm là trách nhiệm lớn hơn với **type safety, constructor/invariant, hierarchy, visibility, source/binary compatibility, serialization và deployment**.

Hãy xác minh ba điều trước khi áp dụng: **có thực sự cần mở rộng type hay chỉ cần advice/adapter; công cụ AOP có hỗ trợ ITD theo đúng ngữ nghĩa mong muốn không; và toàn bộ consumer–compiler–woven artifact–runtime có nhất quán không**. Một ITD tốt phải vừa tiện mô-đun hóa vừa có thể kiểm chứng được, thay vì đưa một cấu trúc mới vào hệ thống mà không có contract hoặc đường nâng cấp an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-004">Giới hạn truy cập và xung đột tên khi bổ sung thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn truy cập và xung đột tên khi bổ sung thành phần trong Aspect/AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), một số cơ chế ngoài advice trước/sau/quanh còn hỗ trợ **thay đổi cấu trúc kiểu** (*structural crosscutting*): bổ sung phương thức, trường dữ liệu, constructor, giao diện triển khai hoặc quan hệ kế thừa cho những lớp đã tồn tại. Trong các mô hình như AspectJ, khả năng này thường được mô tả bằng **inter-type declarations (ITD)** hoặc *introductions*. Một hướng khác là **mixin qua adapter/proxy**, chỉ đưa thêm khả năng khi đi qua đối tượng thích nghi, không nhất thiết sửa cấu trúc lớp đích.

Khả năng “bổ sung thành phần” rất mạnh, nhưng có những câu hỏi quan trọng:

1. Thành phần mới có **quyền truy cập** tới dữ liệu `private`, `protected`, package-private và public của lớp đích hay không?
2. Nếu lớp đã có phương thức/trường **cùng tên** hoặc nhiều aspect cùng bổ sung một thành phần, ai sở hữu tên đó?
3. Thêm một interface có method trùng chữ ký hoặc nhiều `default` methods thì compiler/weaver giải quyết thế nào?
4. Nếu class dùng generics, overload, inheritance, static members hoặc final modifiers, có lỗi **signature clash**, erasure hay thay nghĩa dispatch không?
5. Nếu lớp ở một module/package/class loader khác, cơ chế AOP có thật sự được phép **weave hoặc tiếp cận** thành phần cần dùng không?
6. Nếu không thể bổ sung an toàn, nên đổi tên, dùng interface hợp đồng, adapter, composition hay bỏ việc can thiệp cấu trúc?

**Nguyên tắc cốt lõi:** AOP không tự cấp “quyền truy cập mọi thứ” và cũng không bảo đảm hai khai báo trùng nhau được kết hợp theo thứ tự ưu tiên một cách an toàn. **Luật của Java, của bytecode, của ngôn ngữ AOP và của cơ chế weaver/proxy phải được xem riêng.**

Bài này nói về **Aspect/AOP nói chung, từ cơ bản tới nâng cao**, không gắn với một framework ứng dụng cụ thể. Những đoạn có cú pháp AspectJ được đánh dấu là **minh họa cho ngôn ngữ hỗ trợ ITD**, không phải cú pháp Java thông thường. Chương trình Java 17 chạy được ở phần thực hành minh họa **interface mixin qua proxy và xung đột phương thức**, đồng thời nêu rõ những điều mà proxy *không* thực hiện được.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

- Phân biệt thay đổi cấu trúc lớp bằng **inter-type declaration** với **advice** và với mixin qua **adapter/proxy**.
- Hiểu các mức visibility của Java: `public`, `protected`, **package-private**, `private`; tránh diễn giải quyền của ITD theo trực giác đơn giản.
- Nhận diện xung đột **cùng tên, cùng chữ ký, cùng field, overload, inheritance, default interface methods và generic erasure**.
- Nêu được vì sao **field hiding** không giống method overriding, và vì sao một member mới không tự thay thế member cũ.
- Kiểm tra `final`, `static`, bridge methods, constructor và những ràng buộc từ class/interface contract.
- Hiểu giới hạn của **JPMS module**, **reflection**, **class-loader identity**, phạm vi weaving và chế độ proxy.
- Thiết kế bổ sung thành phần có **tên ổn định, ownership rõ ràng và khả năng tương thích** với phiên bản thư viện thay đổi.
- Chạy ví dụ Java 17 có `default` method conflict, interface adapter qua JDK proxy và hai giao diện trùng chữ ký.
- Lập ma trận test để phát hiện xung đột tại **compile time, weaving time, class loading time và runtime**.

## 2. Nhắc lại: thay đổi hành vi khác thay đổi cấu trúc

Trong AOP, hai nhóm chức năng dễ bị trộn lẫn:

| Nhóm | Tác động điển hình | Ví dụ |
| --- | --- | --- |
| **Behavioral crosscutting** | Chọn join point rồi chạy advice | Trước khi gọi `pay()` thì kiểm tra quyền |
| **Structural crosscutting** | Bổ sung thành viên hoặc quan hệ kiểu nếu ngôn ngữ/công cụ hỗ trợ | Thêm `traceId()` cho `Order` |
| **Adapter/mixin proxy** | Đối tượng bọc biểu lộ thêm một interface hoặc hành vi | Proxy vừa là `OrderView` vừa là `Auditable` |
| **Composition thông thường** | Một object dùng object khác qua field/interface | `OrderAuditAdapter` chứa `Order` |
| **Inheritance thông thường** | Thay đổi hoặc mở rộng lớp con theo Java | `AuditedOrder extends Order` nếu được phép |

**Advice** không đồng nghĩa class thực sự có thêm method. Một around advice có thể thay hành vi khi gọi `save()`, nhưng không khiến compiler Java cho phép gọi `order.traceId()` nếu type của `order` không khai báo phương thức đó và chưa có cơ chế introduction hợp lệ.

**Proxy adapter** khác ITD thật: đối tượng proxy có thể triển khai interface mới, nhưng **target gốc** không vì thế được bổ sung field/method vào class file. Nếu một người giữ tham chiếu gốc, họ vẫn thấy API cũ. Nếu weaver sửa cấu trúc kiểu theo hợp đồng của ngôn ngữ AOP, API của artifact được weave có thể khác API trước weaving.

### 2.1. Ba cấp tác động cần kiểm chứng

- **Source-level visibility:** compiler đang biên dịch trên những khai báo nào, classpath/modulepath nào?
- **Bytecode/runtime structure:** class file nạp thực tế có field/method/interface mới không?
- **Call-site behavior:** caller có gọi qua proxy, adapter hay woven class mà mong đợi không?

Một chương trình thành công khi dùng IDE plugin đặc biệt không bảo đảm chương trình **biên dịch bằng javac đơn thuần**, hay bản triển khai cuối cùng đã chứa những introduction.

## 3. Inter-type declarations và mixin thực chất làm gì?

Một ngôn ngữ AOP hỗ trợ ITD có thể cho phép khai báo bên ngoài class đích rằng class ấy có thêm thành viên hoặc quan hệ kiểu. Mô hình ý tưởng:

```text
Original class:
  Order { id(), total() }

Structural aspect / introduction:
  Order implements Auditable
  Order.auditTag()

Woven result (conceptually):
  Order { id(), total(), auditTag() }
  Order is assignable to Auditable
```

Đây là sơ đồ khái niệm. Cách tạo bytecode, quyền truy cập, tên bridge/synthetic method và thời điểm weave phụ thuộc công cụ.

Nếu thay bằng một proxy:

```text
Order target { id(), total() }
       |
       v
Adapter/Proxy implements OrderView, Auditable
       |
       +--> calls target.id(), target.total()
       +--> implements auditTag() itself
```

Target `Order` không nhất thiết implements `Auditable`. **Chỉ proxy** có thể mang interface mới; `order instanceof Auditable` vẫn có thể false trong khi `proxy instanceof Auditable` là true.

### 3.1. Không phải mọi bộ AOP cho phép structural introduction

Một số công cụ chỉ hỗ trợ **method execution interception** hoặc một tập join point giới hạn; không thể thêm field, constructor hay sửa class hierarchy. Có công cụ cho phép **declare parents**, có công cụ chỉ cho phép **thêm interface qua proxy**, và cũng có công cụ không hỗ trợ gì ngoài advice.

Trước khi thiết kế, phải tra cứu khả năng thực tế: **ngôn ngữ AOP**, thời điểm weaving, loại class đích, bytecode format, phiên bản JDK, giới hạn loader/module và những thành phần được weave.

## 4. Giới hạn truy cập trong Java

Bốn mức kiểm soát truy cập cho members thường gặp:

| Modifier | Cùng class khai báo | Cùng package | Lớp con khác package | Code khác package không kế thừa |
| --- | --- | --- | --- | --- |
| `public` | Có | Có | Có | Có, nếu class/package/module cũng cho truy cập |
| `protected` | Có | Có | Có theo các quy tắc subclass và qualifier của Java | Không |
| Không ghi modifier (**package-private**) | Có | Có theo runtime/compiler package phù hợp | Không chỉ vì là subclass khác package | Không |
| `private` | Có trong phạm vi quyền private/nestmate theo Java | Không chỉ vì cùng package | Không chỉ vì kế thừa | Không |

Đây là **tóm lược**: member public trong một class không thể truy cập từ chỗ mà **class khai báo không accessible**, và module mạnh có thể giới hạn việc liên kết class/package qua biên module. Với `protected` ở khác package, Java còn quy định **kiểu của expression dùng làm receiver** đối với instance members; không thể tóm thành “subclass ở đâu cũng gọi trên mọi instance”.

### 4.1. `private` không biến thành public vì có aspect

Nếu một lớp có:

```java
public class Account {
    private long balance;
    public long balance() { return balance; }
}
```

Một phần code ngoài lớp **không tự được phép** đọc `balance` chỉ vì nó nằm trong một aspect. Một ngôn ngữ AOP có **cơ chế được cấp quyền đặc biệt**, hoặc weaver có thể chèn những phương thức truy cập, có thể cung cấp khả năng riêng — nhưng đó là **hợp đồng mở rộng của ngôn ngữ/công cụ**, không phải luật của Java thông thường.

Ở mức thiết kế, ưu tiên **public hoặc package-facing API ổn định** phù hợp quyền hạn. Không đưa ITD vào chỉ để phá encapsulation của một lớp không sở hữu, nhất là khi chứa thông tin nhạy cảm.

### 4.2. `protected` có quy tắc receiver

Giả sử subclass ở package khác thừa hưởng method protected. Quyền truy cập có thể hợp lệ với `this` hoặc một receiver có kiểu subclass phù hợp, nhưng không mặc định hợp lệ với một biểu thức có kiểu base tùy ý. Code aspect/ITD có thể được biên dịch trong ngữ cảnh khác ngữ cảnh của subclass, nên không được suy quá mức từ tên modifier.

### 4.3. Package-private phụ thuộc ranh giới package/module và loader

Hai lớp có tên package chữ giống nhau không chắc có quyền package-private với nhau trong runtime nếu được nạp dưới **những class loader khác nhau**. Trên JVM, runtime package identity liên quan tới **tên package và defining class loader**, còn JPMS bổ sung những quy tắc về named modules và đóng gói.

Vì vậy một introduction/proxy vào lớp ở loader khác có thể thất bại do **inaccessible type**, kiểm tra liên kết hoặc quy tắc module, dù người viết thấy package name giống nhau.

### 4.4. Visibility của thành phần *được bổ sung*

Một ITD tạo method `public` có ý nghĩa API công khai của artifact đã weave, còn method `private`/package-private có phạm vi sử dụng hẹp theo semantics công cụ. Không mặc định một member private được “thêm vào target” sẽ dùng đúng những luật lexical của mã khai báo trực tiếp trong target; các compiler AOP có thể áp những quy tắc riêng về **ai sở hữu, ai thấy và body được biên dịch trong ngữ cảnh nào**.

## 5. `public` của interface là một hợp đồng không thể hạ cấp

Method abstract của interface trong Java thường là **public**. Nếu lớp hoặc ITD được dùng để đáp ứng interface:

```java
interface Identifiable {
    String id();
}
```

thì implementation tương ứng phải accessible công khai theo hợp đồng interface. Không thể triển khai bằng `private String id()` hay package-private method rồi kỳ vọng compiler coi đó là một implementation hợp lệ.

Một lỗi hay gặp là code của class đã có `String id()` package-private, sau đó aspect “giới thiệu” `implements Identifiable`. Có một method **trùng tên và tham số** nhưng thiếu visibility không làm interface tự được thực hiện hợp lệ. Phải có một implementation đúng signature/access, theo khả năng mà công nghệ cho phép.

## 6. Xung đột tên: phân loại chính xác

Tên giống nhau chưa đủ kết luận lỗi. Cần xem:

1. Thành phần là **field, method, constructor, nested type hay interface**?
2. Cùng tên trong **cùng lớp** hay được thừa hưởng từ superclass/interface?
3. Với method: cùng **parameter types** sau erasure không, return types có tương thích không?
4. Thành phần **instance** hay **static**?
5. Accessibility và override/hide có hợp lệ không?
6. Member đã có trong class gốc hay do **nhiều aspect khác nhau** giới thiệu?
7. Việc biên dịch/weaving có tạo **synthetic hoặc bridge methods** không?
8. Có nhiều **default methods** với cùng chữ ký không?
9. Có artifact/thư viện phiên bản mới thêm member trùng với introduction cũ không?

| Tình huống | Rủi ro | Hướng xử lý |
| --- | --- | --- |
| Hai ITD cùng bổ sung `auditTag()` vào một target | Tranh chấp cùng signature | Chọn một owner, đổi API, bỏ một introduction |
| Class gốc đã có `auditTag()` | Thay đổi/đè/hide không như dự kiến hoặc bị từ chối | Tôn trọng thành phần gốc và kiểm tra luật precedence/collision |
| ITD thêm `id(String)`, lớp có `id()` | Có thể chỉ là overload hợp lệ | Đánh giá ambiguity và binary compatibility |
| Hai method khác generic type argument nhưng cùng erasure | Name clash | Đổi tên/thiết kế type-safe API khác |
| Two interfaces có `default label()` | Không có default duy nhất | Class phải resolve rõ theo luật Java |
| Field cùng tên ở lớp cha/con | Field hiding, không dynamic dispatch | Dùng accessor có hợp đồng thay vì dựa field name |
| Một interface cần method public, class chỉ có method package-private | Không đáp ứng contract | Bổ sung public implementation hợp lệ |
| Static và instance method cùng signature | Không thể xem static hiding như instance overriding | Đổi thiết kế/tên |
| `final` method bị cố override | Không hợp lệ | Chuyển sang composition hoặc API khác |
| Proxy thêm interface duplicate signature | Handler có thể không phân biệt view bằng Method | Thiết lập một behavior thống nhất |

**Cấu hình thứ tự aspect không phải cách “giải quyết” mọi trùng member.** Thứ tự advice/precedence về hành vi **khác** quy tắc xung đột declarations ở type system.

## 7. Method signature, overload và xung đột do erasure

Trong Java, hai method có thể overload hợp lệ khi **danh sách kiểu tham số** khác nhau. Nhưng chỉ khác **return type** thì không tạo overload hợp lệ:

```java
// CỐ Ý KHÔNG BIÊN DỊCH: không được overload chỉ theo return type.
int size() { return 1; }
String size() { return "one"; }
```

Đây là hai phương thức giả định đặt trong cùng lớp. Một ITD thêm method cùng tên/tham số nhưng return type khác vào class đã có member cũng có thể không hợp lệ hoặc tạo collision theo quy tắc công cụ; không được mặc định “compiler chọn theo ngữ cảnh trả về”.

### 7.1. Generic erasure gây name clash

Đoạn dưới đây **cố ý không biên dịch**:

```java
import java.util.List;

class ErasureClash {
    void inspect(List<String> values) {}
    void inspect(List<Integer> values) {}
}
```

Sau *type erasure*, cả hai có parameter type `List`, nên không thể tạo hai overload chỉ dựa vào type argument `String` và `Integer`. Nếu một aspect thêm method `inspect(List<Integer>)` vào một class đã có `inspect(List<String>)`, cần tính cả **erasure và các phương thức bridge/synthetic có thể được sinh**, không chỉ nhìn source generic signature.

### 7.2. Covariant return và override

Một override có thể thu hẹp **reference return type** theo luật covariant return:

```java
class Base {
    Number value() { return 1; }
}
class Child extends Base {
    @Override Integer value() { return 2; }
}
```

Đây là snippet Java minh họa. Không được thay đổi return type tùy ý: ví dụ `String` không phải subtype của `Number`. Khi compiler tạo **bridge method**, bytecode có thể có member bổ sung để giữ compatibility. Một weaver/ITD phải xử lý xung đột ở cấp bytecode và type system, không chỉ ở hình thức chữ ký nguồn.

### 7.3. Varargs và arrays

Trong Java, `String...` là cú pháp khai báo tham số varargs dựa trên `String[]`. Nếu method đã có `log(String[])`, không thể giới thiệu một method `log(String...)` vào cùng class rồi coi đó là một overload khác. Nhiều biểu diễn nhìn khác nhau ở source vẫn có **cùng descriptor**.

## 8. Field hiding không phải method overriding

Field resolution trong Java khác virtual method dispatch. Ví dụ:

```java
class Parent {
    public String label = "parent";
    public String label() { return "parent-method"; }
}
class Child extends Parent {
    public String label = "child";
    @Override public String label() { return "child-method"; }
}
```

Đây là snippet minh họa. Nếu `Parent p = new Child()`, biểu thức `p.label` tham chiếu field của `Parent` theo **kiểu compile-time của expression**, còn `p.label()` gọi method override của `Child` bằng dispatch động.

Do đó, khi ITD thêm **field cùng tên với field được thừa hưởng**, không nên coi nó như “override state”. Nó có thể **hide** một field và tạo hai vùng dữ liệu khác nhau trên một object. Điều này dễ làm advice đọc một giá trị trong khi code nghiệp vụ đọc giá trị khác.

### 8.1. Tên field và method có thể trùng

Java cho phép field và method có cùng tên trong một class ở những dạng hợp lệ, vì ngữ cảnh cú pháp truy cập khác nhau (`obj.value` và `obj.value()`). Nhưng điều đó **không có nghĩa thiết kế API dễ hiểu**; một ITD thêm method trùng tên field có thể làm người đọc khó xác định mục đích.

Tên bổ sung nên mô tả **hợp đồng** và tránh những thành phần có sẵn, không tận dụng mọi khe hở pháp lý của cú pháp.

### 8.2. Static hiding và giới hạn của `final`

Một static method không được override theo nghĩa dispatch động của instance method; nó có cơ chế **hiding** riêng. Một method `final` không được override trong Java thông thường. Một class `final` không được subclass, dù một adapter hoặc proxy giao diện có thể được xây bên cạnh nó.

Với ITD/weaving, khả năng thay đổi cấu trúc hoặc thêm method mới cho class final **phụ thuộc công cụ**; không từ đó suy ra được phép override method final hoặc vi phạm ràng buộc bytecode. Khi gặp final/binary library, luôn xác minh khả năng hỗ trợ thay vì phỏng đoán.

## 9. Nhiều interface và `default` method collision

Java cho phép interface khai báo `default` methods. Nếu một class triển khai **hai interface độc lập có cùng default signature**, Java không thể chọn ngầm một nhánh. Class cần cung cấp override giải quyết.

Ví dụ:

```java
interface Left {
    default String label() { return "left"; }
}
interface Right {
    default String label() { return "right"; }
}
final class Combined implements Left, Right {
    @Override public String label() {
        return Left.super.label() + "+" + Right.super.label();
    }
}
```

Đoạn này có thể biên dịch khi đặt trong đúng file/class hoặc các top-level package-private classes hợp lệ. Phương thức override biểu thị quyết định **tường minh của chủ class**. Một introduction làm class implements thêm interface có default method có thể khiến **xung đột mới** xuất hiện, dù class không đổi source.

### 9.1. Quy tắc cần nhớ

- **Class method hợp lệ** thường có ưu tiên hơn interface default tương ứng theo luật Java.
- Hai default từ những interface không có quan hệ ưu tiên có thể bắt buộc class resolve bằng override.
- Một abstract method của interface khác có thể ảnh hưởng quy tắc kế thừa và lỗi compile nếu class chưa đáp ứng hợp đồng.
- Khi introduction thêm nhiều interface, phải kiểm tra **toàn bộ tập members được kế thừa**, không chỉ những method của interface mới được thêm.
- Nếu interface thay đổi từ phiên bản thư viện A sang B và bổ sung default method mới, source trước đây hợp lệ có thể gặp conflict hoặc thay đổi dispatch khi rebuild.

Không giải quyết default collision chỉ bằng “aspect nào chạy trước”, vì đây là vấn đề **type/interface contract**, không phải thứ tự chạy advice.

## 10. Cú pháp structural introduction đặc thù của AspectJ

Ví dụ chỉ minh họa **cú pháp AspectJ**, không phải Java tiêu chuẩn; cần compiler/weaver hiểu ngôn ngữ này và lớp/interface đích tương ứng:

```java
// File: TagIntroduction.aj (minh họa ngôn ngữ AspectJ)
// Giả sử demo.Order đã tồn tại, và demo.TraceTag là interface
// có phương thức: String traceTag();

public aspect TagIntroduction {
    declare parents: demo.Order implements demo.TraceTag;

    public String demo.Order.traceTag() {
        return "tagged";
    }
}
```

Ý tưởng: phần `declare parents` bổ sung quan hệ implements interface cho target, còn **inter-type method** cung cấp implementation của `traceTag()` ở class được áp dụng theo khả năng của công cụ.

Đây là mã **không chạy độc lập** với `javac`; muốn thực thi cần các lớp đích, interface tương ứng và đúng toolchain AspectJ. Không nên lấy ví dụ này làm bằng chứng mọi weaver hay proxy đều có quyền thay đổi quan hệ implements của class.

### 10.1. Ví dụ về xung đột *không dùng để biên dịch*

Giả sử hai aspect cùng muốn giới thiệu một method có cùng signature:

```java
// Hai declarations MÔ TẢ XUNG ĐỘT (không ghép để biên dịch).
public String demo.Order.traceTag() { return "A"; }
public String demo.Order.traceTag() { return "B"; }
```

Không thể thiết kế API chỉ dựa vào kỳ vọng “weaver sẽ chọn B vì chạy sau”. Công nghệ có thể báo conflict, có quy tắc precedence hạn chế hoặc hạn chế introduction dựa vào class hierarchy. Phải dựa trên tài liệu và kiểm thử **toolchain/version** cụ thể, rồi sửa thiết kế để **một chủ sở hữu** chịu trách nhiệm mỗi member.

### 10.2. Private inter-type state

Một ITD có thể giới thiệu state riêng trong những mô hình hỗ trợ. Nhưng field đó được **khai báo ở đâu**, ai có thể đọc, compiler có phát sinh helper method/field synthetic nào và object lifetime thế nào đều cần xác minh theo luật AspectJ/weaver thực tế.

Một field `private` được giới thiệu không có nghĩa mọi đoạn source Java của target được phép truy cập nó như một field viết trực tiếp trong target. Ngược lại, một aspect đặc quyền trong công nghệ hỗ trợ có thể có quyền đặc biệt, nhưng không nên biến cơ chế ấy thành **phụ thuộc ngầm vào internals** của thư viện không thuộc quyền sở hữu.

## 11. JPMS, reflection và class-loader boundaries

Từ Java 9, **Java Platform Module System (JPMS)** bổ sung các ranh giới đóng gói. Một module có thể **exports** package để code module khác truy cập public types, trong khi **opens** cho một số dạng reflection truy cập member không public. Hai khái niệm có mục đích và tác dụng khác nhau.

Một chương trình dùng reflection có thể thử:

```java
field.setAccessible(true);
```

Nhưng trong môi trường module mạnh, thao tác đó có thể thất bại, chẳng hạn với `InaccessibleObjectException`, nếu package/module không cho phép deep reflection phù hợp. Cũng không có luật rằng thêm một tùy chọn mở package sẽ tự giải quyết mọi xung đột ITD hoặc cấp quyền cho mọi hình thức bytecode rewriting.

### 11.1. Kiểm tra phân biệt `exports` và `opens`

| Cần làm | Yêu cầu cần xem |
| --- | --- |
| Code module khác gọi public API | Exports, khả năng đọc module, accessibility |
| Reflection đến nonpublic member | Opens/deep-reflection permission nếu áp dụng |
| Bổ sung member bằng weaving | Công cụ hỗ trợ module/class loading và cấu trúc bytecode |
| Proxy triển khai interface nonpublic | Giới hạn về package/module và interface list của JDK Proxy |
| Nạp class cùng tên từ hai loader | Runtime type identity và cast/linkage |
| Hot reload thư viện | Cache/proxy/weaver có giữ class loader cũ hay không |

**Hai `Class` object có binary name giống nhau nhưng thuộc defining class loader khác nhau có thể là hai runtime types khác nhau.** Một method signature trên object của loader A không tự đáp ứng interface có tên giống hệt từ loader B.

### 11.2. Không dùng `setAccessible` như một thiết kế mặc định

Reflection mở quyền riêng tư có thể gây lỗi module, khó bảo trì và làm yếu encapsulation. Nếu muốn thêm một hành vi đọc trạng thái, ưu tiên **API công khai tối thiểu** hoặc phối hợp giữa các module qua interface ổn định. Chỉ dùng quyền truy cập đặc biệt khi có lý do và hợp đồng hỗ trợ rõ.

## 12. Java 17 runnable: mixin proxy và va chạm phương thức

Chương trình sau không dùng AOP compiler. Nó minh họa ba tình huống:

1. **Hai default methods trùng chữ ký** được giải bằng override tường minh.
2. **JDK dynamic proxy** bổ sung một interface quan sát cho **đối tượng proxy**, nhưng không sửa class target gốc.
3. **Hai interface khai báo cùng method signature** trên một proxy: handler không thể biết lời gọi được thực hiện qua kiểu tham chiếu nào chỉ từ `Method.getDeclaringClass()`; JDK Proxy dùng một `Method` tương ứng interface ưu tiên theo danh sách trong tình huống trùng method.

Đặt toàn bộ trong file `StructuralMixinDemo.java`:

```java
import java.lang.reflect.Proxy;

public class StructuralMixinDemo {
    public interface Named {
        String name();
    }

    public interface AuditTag {
        String auditTag();
    }

    public static final class Order implements Named {
        private final String id;

        public Order(String id) {
            this.id = id;
        }

        @Override
        public String name() {
            return "order-" + id;
        }
    }

    public interface Left {
        default String label() {
            return "left";
        }
    }

    public interface Right {
        default String label() {
            return "right";
        }
    }

    public static final class Combined implements Left, Right {
        @Override
        public String label() {
            return Left.super.label() + "+" + Right.super.label();
        }
    }

    public interface FirstCode {
        String code();
    }

    public interface SecondCode {
        String code();
    }

    static Object adapt(Order target) {
        return Proxy.newProxyInstance(
                StructuralMixinDemo.class.getClassLoader(),
                new Class<?>[] { Named.class, AuditTag.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("name")
                            && method.getParameterCount() == 0) {
                        return target.name();
                    }
                    if (method.getName().equals("auditTag")
                            && method.getParameterCount() == 0) {
                        return "audit:" + target.name();
                    }
                    if (method.getName().equals("toString")
                            && method.getParameterCount() == 0) {
                        return "AugmentedOrder";
                    }
                    throw new UnsupportedOperationException(
                            method.getName());
                });
    }

    static Object duplicateMethods() {
        return Proxy.newProxyInstance(
                StructuralMixinDemo.class.getClassLoader(),
                new Class<?>[] { FirstCode.class, SecondCode.class },
                (proxy, method, args) ->
                        method.getDeclaringClass().getSimpleName());
    }

    public static void main(String[] args) {
        Order target = new Order("7");
        Object adapted = adapt(target);

        System.out.println("DOMAIN " + target.name());
        System.out.println("MIXIN_NAME " + ((Named) adapted).name());
        System.out.println("MIXIN_TAG " + ((AuditTag) adapted).auditTag());
        System.out.println("TARGET_TAG "
                + AuditTag.class.isInstance(target));
        System.out.println("PROXY_TAG " + (adapted instanceof AuditTag));
        System.out.println("DEFAULT " + new Combined().label());

        Object duplicate = duplicateMethods();
        System.out.println("DUP_FIRST "
                + ((FirstCode) duplicate).code());
        System.out.println("DUP_SECOND "
                + ((SecondCode) duplicate).code());
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 StructuralMixinDemo.java
java StructuralMixinDemo
```

Stdout dự kiến chính xác:

```text
DOMAIN order-7
MIXIN_NAME order-7
MIXIN_TAG audit:order-7
TARGET_TAG false
PROXY_TAG true
DEFAULT left+right
DUP_FIRST FirstCode
DUP_SECOND FirstCode
```

### 12.1. Vì sao target vẫn không có `AuditTag`?

`adapt(target)` tạo ra một **đối tượng proxy khác** triển khai `Named` và `AuditTag`. Invocation handler chuyển `name()` về target và tự cung cấp `auditTag()`. Vì class `Order` gốc **không implements AuditTag**, dòng `TARGET_TAG false` đúng; riêng object proxy mới in `PROXY_TAG true`.

**Không được kết luận AOP qua proxy đã “thêm method vào class Order”.** Nó tạo một view/adapter khác. Người giữ target gốc vẫn không gọi được `target.auditTag()` ở Java compile time.

### 12.2. Vì sao `Combined` cần override?

`Left` và `Right` đều cung cấp `default String label()`. Class `Combined` không thể im lặng dùng một trong hai. Nó override và lựa chọn kết hợp `Left.super.label()` với `Right.super.label()`, tạo `left+right`.

Một introduction thêm interface có default method vào class đang có interface khác phải tính các xung đột tương tự.

### 12.3. Vì sao hai kiểu tham chiếu đều nhận `FirstCode`?

Hai interface `FirstCode` và `SecondCode` đều khai báo `String code()`. Proxy thực thi một implementation cho signature trùng nhau; khi handler nhận `Method`, nó không có thông tin “caller đã cast sang SecondCode rồi gọi”. Trong JDK Proxy, `Method` cho signature duplicate được lấy theo quy tắc về **interface xuất hiện trước** trong danh sách interface của proxy.

Bởi `FirstCode.class` đứng trước `SecondCode.class`, handler trả `"FirstCode"` cho cả hai lượt. Đây là lý do **không dùng `getDeclaringClass()` để quyết định quyền/nghĩa khác nhau cho hai view có cùng signature trên một proxy**.

### 12.4. Hạn chế và an toàn của ví dụ

- Proxy ở đây chỉ hỗ trợ hai methods `name()` và `auditTag()` cùng `toString()` ở handler; mọi method khác (chẳng hạn `equals`, `hashCode`) cố ý chưa có chính sách xử lý. **Không dùng làm adapter sản xuất chưa hoàn thiện**.
- Vì mọi interface được chọn là public, ví dụ không cần xử lý đặc thù về interface package-private từ nhiều package/module.
- Ví dụ không có ITD, không thay bytecode và không mở quyền truy cập `private` của target; `id` chỉ được sử dụng nội bộ qua `name()`.
- Không có field được bổ sung vào `Order`; nếu cần trạng thái per-proxy, handler/coordinator phải quản lý vòng đời, đồng thời và cleanup của state ấy.
- Nếu hai interface có cùng tên/parameters nhưng return type không tương thích, JDK Proxy có ràng buộc tạo proxy và có thể báo lỗi; chương trình minh họa dùng cùng `String` để có kết quả xác định.
- Caller phải thật sự dùng **proxy instance**, không dùng target gốc, thì phần bổ sung mới có mặt.

## 13. Kiểm soát tên và khả năng tương thích khi nâng cấp

Giả sử thư viện `Order` ở phiên bản 1 chưa có `auditTag()`. Một aspect giới thiệu method đó thành công. Ở phiên bản 2, tác giả thư viện **tự thêm** `auditTag()` với cùng signature hoặc nghĩa khác. Giờ xuất hiện nguy cơ xung đột source, bytecode hoặc ngữ nghĩa.

Để tránh:

1. Ưu tiên bổ sung những **interface có owner riêng** và tên method đặc trưng, chẳng hạn `AuditTraceView.traceTag()`, thay vì thêm những tên phổ biến như `getId`, `status`, `save`.
2. Công bố **API contract**: ai sở hữu, có thể sửa tên/kiểu không, bảo đảm về return/throw và thread-safety.
3. Kiểm tra **collision với cả class hierarchy**, kể cả members được kế thừa.
4. Kiểm tra **generics erasure, overload, bridge methods** và static/instance.
5. Có **binary compatibility test** với các artifact được weave, không chỉ unit test trên source.
6. Đánh giá kết quả khi thư viện nâng cấp: member mới ở target có trùng với introduction không?
7. Không phát tán một “public synthetic feature” vô tình trở thành API mà các module khác phụ thuộc nếu chưa cam kết duy trì.
8. Khi cần không làm thay đổi API target, ưu tiên **composition hoặc adapter**.

### 13.1. Binary compatibility không bằng source compatibility

Một class đã weave có thêm method và interface có thể thay đổi kết quả reflection, dispatch và kiểm tra `instanceof`. Caller đã biên dịch với API được giới thiệu có thể cần artifact **được weave phù hợp** lúc chạy. Nếu runtime nạp bản chưa weave, có thể gặp `NoSuchMethodError`, `ClassCastException` hoặc lỗi linking khác, tùy hình thức dùng.

Không đánh giá bằng một lệnh `javac` chạy trên lớp chưa weave rồi suy rằng introduction vô dụng. Ngược lại, không dùng một bản woven artifact làm bằng chứng mọi môi trường classpath/modulepath đều nhìn thấy cùng API.

## 14. Kế thừa, constructor và hạn chế của cấu trúc đích

Một introduction có thể nhắm class hoặc interface theo mô hình công cụ, nhưng những trường hợp sau cần thận trọng:

- Target là **final class**: không thể subclass thông thường, nhưng một weaver có thể có khả năng thêm một số members mà không subclass; phải xác nhận cụ thể.
- Target có **final method**: không được override theo Java như method thông thường; nếu cần đổi hành vi, advice khác override.
- Target có constructor bắt buộc bất biến: thêm field mới không đồng nghĩa field được gán đúng cho mọi đường khởi tạo/deserialization.
- Target dùng reflection/serialization: field bổ sung có thể ảnh hưởng quan sát cấu trúc, chiến lược serialize hoặc layout.
- Target là record/sealed class: JVM và Java có ràng buộc đặc biệt về state, inheritance và permitted subclasses; không suy một ITD tùy ý được phép phá các ràng buộc ấy.
- Target interface có private/default/static members: mỗi loại có luật thừa kế/truy cập khác nhau.
- Target được nạp trước khi cơ chế weaving tương ứng áp dụng: khả năng thêm thành phần có thể bị giới hạn bởi **thời điểm load/redefine/retransform**.

### 14.1. Trạng thái bổ sung phải có vòng đời

Nếu aspect giới thiệu một field mới cho mỗi target, phải hỏi:

- Field khởi tạo lúc nào và mặc định có ý nghĩa không?
- Object target có thể được tạo bằng reflection, deserialization, factory hoặc bytecode instrumentation không?
- Nhiều thread có thể ghi field được giới thiệu không?
- Field giữ request/context sống quá lâu không?
- Nếu target được clone hoặc serialize, giá trị field mới phải được xử lý ra sao?
- Có phương thức cleanup/close phù hợp không?

ITD không tự làm field mới **thread-safe** và cũng không tự giải phóng tài nguyên ngoài heap.

## 15. Xung đột khi nhiều aspect cùng mở rộng một kiểu

Một codebase có thể có hai module AOP khác nhau:

```text
AuditAspect  -> introduces Order.trace()
MetricAspect -> introduces Order.trace()
```

Nếu hai chức năng muốn nghĩa khác nhau nhưng dùng cùng signature, việc chúng nằm ở hai file không làm xung đột biến mất. Một bản build có thể thất bại, một cơ chế có thể yêu cầu precedence, hoặc một số tổ hợp không được phép; **không có quy tắc chung “aspect mới nhất thắng”**.

Cách giải hợp lý:

1. Tìm **hợp đồng thực tế** cả hai thành phần muốn cung cấp.
2. Nếu cùng một khả năng, hợp nhất **một interface/owner**, không tạo hai thực thể tranh chấp.
3. Nếu là hai khả năng khác nhau, **đặt tên khác** phù hợp ngữ cảnh (ví dụ `auditTraceId` và `metricSeriesId`).
4. Nếu một khả năng cần tái sử dụng kết quả của khả năng kia, chuyển sang **API phối hợp tường minh**.
5. Nếu không sở hữu target hoặc thư viện thay đổi nhiều, dùng **adapter/composition** để tránh chiếm namespace của target.
6. Kiểm thử kết quả trên artifact thực sau weaving và khi cả hai aspect cùng bật.

Việc đặt tên có prefix/suffix ngẫu nhiên để tránh lỗi compiler không giải quyết **semantic collision** nếu cùng một method name thật sự cần hai loại hành vi trái nhau.

## 16. Checklist trước khi bổ sung member bằng AOP

| Câu hỏi | Cần xác nhận |
| --- | --- |
| Công cụ có hỗ trợ **structural introduction** không? | ITD, declare parents, interface introduction hay chỉ proxy adapter |
| Target class có nằm trong phạm vi được xử lý? | Build, weaver, loader, module, package, artifact |
| Có member trùng ở target hoặc superclass không? | Field/method signatures, static, return types |
| Có nhiều aspect cùng mở rộng target không? | Conflict/owner/precedence nếu có |
| Có interface default trùng method không? | Java multiple-inheritance resolution |
| Member có thỏa visibility và interface contract không? | Public/protected/package/private, accessible types |
| Có generic erasure/bridge clash không? | Erased signatures và binary descriptors |
| Có final, sealed, record, constructor constraint không? | Ràng buộc class/bytecode được hỗ trợ |
| Có module/classloader boundary không? | Exports/opens, defining loader, runtime package |
| Caller dùng đúng object/artifact không? | Proxy vs original target; woven vs unwoven class |
| Có đường bypass hoặc khác compiler không? | Direct target call, self-invocation, build cache |
| Member mới có giữ state/resource không? | Thread-safety, lifetime, cleanup, serialization |
| Nâng cấp thư viện thì sao? | API collisions và binary compatibility |
| Có phương án ít can thiệp hơn không? | Public API, adapter, decorator, composition |

## 17. Kiểm thử ở những giai đoạn khác nhau

| Thời điểm | Ví dụ lỗi cần bắt |
| --- | --- |
| **Compile source** | Method duplicate, generic erasure, thiếu public implementation |
| **Aspect compilation** | ITD conflict hoặc target name/signature sai |
| **Weaving** | Không thể sửa class structure, incompatibility, thiếu class |
| **Class loading/linking** | Loader mismatch, module access, VerifyError/LinkageError |
| **Runtime calling** | `ClassCastException`, method không tồn tại, proxy view không đúng |
| **Reflection** | `getDeclaredMethods` khác mong đợi giữa woven/unwoven |
| **Concurrency** | Field bổ sung bị ghi đồng thời, race và retention |
| **Serialization** | State được bổ sung không khớp policy phiên bản |
| **Library upgrade** | API gốc mới chiếm tên introduction |
| **Regression** | Hai aspect cùng khai báo member, thứ tự/coverage thay đổi |

### 17.1. Test thuận và test nghịch

Không chỉ kiểm tra “gọi method mới được”. Cần kiểm tra:

- Không có **hai definitions** không tương thích trong artifact.
- Caller qua interface mới nhìn đúng implementation/return/throw.
- Caller giữ target gốc trong mô hình proxy **không giả nhận** API mới.
- Một method `private` trong target vẫn giữ đúng ranh giới truy cập đã thiết kế.
- Xung đột `default` được resolve tường minh.
- Phiên bản thư viện mới có hoặc không có member trùng được phát hiện sớm.
- Module khác/class loader khác không được cấp quyền “tình cờ”.
- Một lời gọi tại nhánh exception không giữ state/resource ngoài vòng đời.
- Nếu thay đổi cấu trúc là tính năng bắt buộc, bản triển khai **chưa weave** bị phát hiện thay vì âm thầm chạy thiếu tính năng.

### 17.2. Verify artifact cuối

Một bản build có thể biên dịch thành công nhưng chương trình thực tế nạp artifact khác (ví dụ class gốc chưa weave). Khi đó các test chạy trong IDE có thể pass nhưng deployment lỗi ở `instanceof` hoặc gọi method được giới thiệu.

Hãy kiểm tra đúng **artifact runtime**, classpath/modulepath, phiên bản JVM và thứ tự class loader. Nếu cần, kiểm tra public API qua reflection **một cách có kiểm soát** và chạy end-to-end tests trên chính artifact dự kiến triển khai.

## 18. Sai lầm phổ biến

| Sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Coi ITD là tính năng mặc định của mọi AOP | Viết mã không được công cụ hỗ trợ | Xác minh khả năng toolchain |
| Nghĩ aspect đọc mọi private field | Vi phạm encapsulation hoặc compile/access fail | Dùng API/hợp đồng hoặc quyền đặc biệt khi được hỗ trợ |
| Nhầm `protected` với “mọi subclass gọi được trên mọi object” | Illegal access | Áp đúng luật subclass/receiver |
| Cùng package name là đủ package-private | Classloader/runtime package mismatch | Kiểm tra loader và module |
| ITD thêm method trùng method gốc | Collision/đổi hành vi | Một owner, rename, adapter |
| Chỉ khác return type mà gọi là overload | Compile conflict | Đổi params hoặc tên theo contract |
| `List<String>` vs `List<Integer>` coi là overload | Generic erasure clash | Thiết kế interface/type-safe API khác |
| Coi field hiding như override | Đọc hai state khác nhau | Dùng accessor rõ |
| Trộn static method với instance override | Dispatch sai | Tách chiến lược, đổi tên |
| Hai default methods cùng chữ ký không resolve | Compile conflict | Override tường minh |
| Đoán Proxy handler biết interface của caller | Sai với duplicate method | Quy định một behavior thống nhất |
| Nghĩ proxy đã sửa target class | `instanceof`/method call gốc thất bại | Dùng đúng proxy hoặc woven artifact |
| Dựa vào `setAccessible(true)` trong mọi module | Có thể InaccessibleObjectException | Exports/opens theo hợp đồng hoặc API công khai |
| Không test khi nâng cấp thư viện | New member collision/binary break | Compatibility matrix |
| Không kiểm tra bytecode đã weave | Runtime không có member | Artifact-level validation |
| Thêm field giữ request lâu dài | Memory retention/race | Scoped state, owner/cleanup |

## 19. Bài tập vận dụng có đáp án

### Bài 1 — Hai aspect trùng tên

AuditAspect và TraceAspect đều giới thiệu `Order.traceId()` với những ý nghĩa khác nhau. Có thể để cả hai rồi dùng advice precedence chọn phiên bản không?

**Đáp án:** Không được mặc định. Đây là **structural member collision**, không chỉ vấn đề thứ tự advice. Nên xác định owner hoặc tách thành những hợp đồng/names rõ ràng.

### Bài 2 — Package-private và module

Một ITD được biên dịch ở package chữ giống target, nhưng code nạp từ class loader khác. Có thể tự cho rằng field package-private truy cập được không?

**Đáp án:** Không. Runtime package và module/class-loader identity phải được xem cụ thể; tên package dạng chuỗi giống nhau không đủ bảo đảm quyền.

### Bài 3 — Generic erasure

Target đã có `inspect(List<String>)`. Aspect muốn thêm `inspect(List<Integer>)`. Vì sao rủi ro?

**Đáp án:** Cùng erasure `inspect(List)`, không phải hai overload hợp lệ dựa trên generic type arguments. Đổi tên hoặc xây một API khác.

### Bài 4 — Return type

Một class đã có `int count()`; aspect bổ sung `String count()`. Compiler có chọn method theo nơi gán kết quả không?

**Đáp án:** Không. Java không overload chỉ theo return type. Bổ sung cần tên/chữ ký hợp lệ theo type system và toolchain.

### Bài 5 — Default methods

Class implements cả `Left` và `Right`, mỗi interface có `default label()`. Làm sao giải quyết?

**Đáp án:** Cung cấp override `label()` rõ, có thể gọi `Left.super.label()` và `Right.super.label()` theo điều kiện Java cho phép.

### Bài 6 — Proxy và target

Từ chương trình runnable, tại sao `AuditTag.class.isInstance(target)` là false nhưng proxy trả true?

**Đáp án:** Proxy là **một object khác** triển khai giao diện bổ sung; nó không mutate class `Order` gốc.

### Bài 7 — Duplicate proxy methods

Vì sao `((SecondCode) duplicate).code()` vẫn in `FirstCode`?

**Đáp án:** Hai interfaces có signature giống nhau và proxy implementation không biết caller cast view nào. `Method` handler nhận được gắn với interface đầu tiên theo quy tắc JDK Proxy đối với duplicate methods.

### Bài 8 — Public interface contract

Một interface yêu cầu `public String id()`; class có method cùng chữ ký nhưng package-private. Chỉ declare implements interface đã đủ chưa?

**Đáp án:** Chưa. Implementation phải đáp ứng visibility và contract; cần một phương thức public hợp lệ theo khả năng công cụ.

### Bài 9 — Field vs method

Khi subclass có field cùng tên superclass, tại sao `Parent p = new Child(); p.label` và `p.label()` có thể khác?

**Đáp án:** Field access liên kết theo kiểu compile-time của expression, còn instance method override dùng dynamic dispatch.

### Bài 10 — Artifact

Code dùng method được ITD thêm chạy khi build bằng AOP compiler, nhưng gặp lỗi khi deployment. Nguyên nhân cần kiểm tra trước?

**Đáp án:** Bản triển khai có nạp **woven class đúng** không, classpath/modulepath/class loader có trộn woven và unwoven hay không, và runtime API thật có method/implements interface như dự kiến không.

### Bài 11 — Private field

Một aspect muốn đọc dữ liệu nhạy cảm từ `private` field của target. Có nên coi đó là quyền mặc định của AOP?

**Đáp án:** Không. Phải xem cơ chế access cụ thể và trách nhiệm bảo mật; thường nên cung cấp API tối thiểu được chủ lớp cho phép thay vì phá encapsulation.

## 20. Ghi nhớ

- **Structural introduction khác advice**: thêm method/field/interface không giống chạy trước/sau một join point.
- **ITD/weaving khác proxy mixin**: proxy thường chỉ làm object bọc mang thêm interface, không sửa class target gốc.
- Quyền truy cập `public/protected/package-private/private` còn chịu luật **class accessibility, modules, loaders và compiler AOP**; aspect không tự là ngoại lệ cho mọi giới hạn.
- **Xung đột tên** phải xét member kind, method signature, static/instance, inheritance, generic erasure và default interface methods; không thể giải tất cả bằng advice precedence.
- **Field hiding không phải overriding**, **return type khác không tạo overload**, và **default method conflict** cần giải quyết tường minh.
- Interface introduction phải thỏa **public method contract**; private state được giới thiệu không tự trở thành public API.
- **JPMS exports khác opens**; reflection hoặc weaving chỉ hoạt động khi quyền/toolchain hỗ trợ, không dựa vào tên package giống nhau.
- Tên introduction phải có owner rõ và chịu được **library upgrade**; ưu tiên adapter/composition khi không cần đổi bytecode của target.
- Kiểm thử trên **artifact đã weave hoặc proxy đúng** với các đường lỗi, modules, loader, collision và runtime behavior, không chỉ happy path.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-005">Thay đổi quan hệ kiểu và ảnh hưởng tới mã sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi quan hệ kiểu và ảnh hưởng tới mã sử dụng trong AOP

## 1. Từ advice hành vi đến structural crosscutting

**Aspect-Oriented Programming (AOP)** không chỉ có thể bổ sung hành vi trước/sau/bao quanh một **join point** bằng **advice**, mà trong một số ngôn ngữ còn có **structural crosscutting**: thay đổi cấu trúc của kiểu dữ liệu hoặc ràng buộc mối quan hệ giữa các kiểu.

Những khả năng có thể được hỗ trợ, tùy ngôn ngữ và weaver:

- **Inter-type method introduction:** thêm method vào một type mà không sửa source khai báo gốc.
- **Inter-type field introduction:** thêm field/state vào type.
- **Interface introduction:** làm một class thực hiện một interface/capability bổ sung.
- **Parent declaration:** thay đổi quan hệ superclass/interface trong phạm vi ngôn ngữ cho phép.
- **Structural rules:** khai báo ràng buộc hoặc báo lỗi khi cấu trúc vi phạm quy tắc.
- **Mixins/roles:** ghép một tập behavior vào đối tượng hoặc kiểu theo hợp đồng rõ ràng.

Ví dụ ý tưởng:

~~~text
Trước structural weaving:
  Invoice implements DomainEntity

Sau structural weaving:
  Invoice implements DomainEntity, Auditable
~~~

Nếu một weaver thực sự tạo class binary với interface bổ sung trước khi JVM định nghĩa class, những **Invoice instances** của class ấy có thể tương thích với Auditable trong runtime type hierarchy. Điều này có thể ảnh hưởng trực tiếp tới **instanceof, casts, assignments, method dispatch, reflection, generics, serialization và pointcuts dựa vào type hierarchy**.

Điểm quan trọng nhất của chủ đề: **không phải mọi “introduction” đều thay đổi class gốc**. Một số cơ chế tạo proxy hoặc adapter implement interface bổ sung; target bên trong vẫn giữ class ban đầu. Hai cơ chế có hệ quả rất khác với mã sử dụng.

Bài học trình bày AOP cơ bản và nâng cao ở mức tổng quát; không giả định mọi engine hỗ trợ cùng dạng structural weaving.

## 2. Những khái niệm cần phân biệt

| Thuật ngữ | Ý nghĩa |
|---|---|
| **Advice** | Code bổ sung được kích hoạt tại join point phù hợp |
| **Pointcut** | Quy tắc lựa chọn join point |
| **Interception** | Chặn hoặc bao một operation khi thực thi |
| **Structural crosscutting** | Thay đổi hoặc ràng buộc cấu trúc type/member |
| **Inter-type declaration (ITD)** | Khai báo một member hoặc quan hệ kiểu từ aspect áp sang target type |
| **Declare parents** | Một kiểu khai báo quan hệ parent/interface trong một số AOP languages |
| **Introduction** | Bổ sung capability/member/interface |
| **Mixin** | Kết hợp role/behavior vào đối tượng hoặc kiểu |
| **Weaver** | Thành phần ghép các aspects vào chương trình |
| **Static type** | Kiểu compiler gán cho expression |
| **Runtime class** | Class thực tế của object |
| **Subtype** | Quan hệ có thể thay thế một type bằng type khác theo hệ thống kiểu |

**Interception không tự tạo subtype.** Chạy logging advice trước Invoice.save không tự khiến Invoice implement Auditable. Muốn object có quan hệ type mới phải có một **mechanism thay type metadata hoặc tạo object khác hỗ trợ type đó**.

### 2.1. Interface khác superclass

Java hỗ trợ một class extends một superclass trực tiếp và có thể implements nhiều interfaces. Không được đọc “thay đổi quan hệ kiểu” thành “cho phép multiple class inheritance”.

Một số structural changes có thể không thực hiện được vì constraints của class hierarchy, final/sealed declarations, conflicts, module accessibility hoặc bytecode verification.

## 3. Ba mô hình cần vẽ riêng

### 3.1. Binary structural weaving

~~~text
Core class definition
        |
        v
Weaver adds interface/member declarations
        |
        v
Woven class binary
        |
        v
JVM loads class with new type structure
~~~

Nếu thành công, cùng object Core có thể thực hiện interface mới. Reflection trên woven Core class có thể thấy introduced members/interfaces theo dạng binary được tạo.

Cần kiểm tra rằng **consumer compiler** có đủ thông tin type khi compile source gọi introduced method, và runtime dùng binary đã weave tương thích. Không phải class đã load xong cũng được thay arbitrary interfaces trong JVM.

### 3.2. Proxy/adapter tạo object khác

~~~text
Caller --> Adapter P implements Worker, Tagged
                       |
                       v
              Target T implements Worker
~~~

Trong mô hình này:

- P instanceof Tagged: true.
- T instanceof Tagged: false.
- P == T: false nếu là hai allocations riêng.
- P.work có thể delegate tới T.work để cho cùng kết quả.
- Reflection trên class P khác class T.
- Nếu caller cầm raw T, cast Tagged có thể throw.

Adapter có thể bổ sung capability **ở một API boundary**, không phải biến class gốc thành subtype mới.

### 3.3. Subclass-based proxy

Một generated subclass có thể extends target concrete type và implements thêm interface. Proxy P có thể instanceof Core, nhưng nếu target T riêng được giữ để delegate, **P và T vẫn không phải cùng reference**.

Final class không được subclass theo Java rules. Một công cụ sửa binary trực tiếp trước load có các constraints khác; không dùng quy tắc của subclassing để kết luận về mọi loại weaving.

### 3.4. Câu hỏi kiểm chứng mechanism

- Một class definition thực sự đổi hierarchy hay một wrapper mới được tạo?
- Object nào được trao cho caller, P hay T?
- Runtime Class nào implement interface?
- Có type weaving ở compile time/load time không?
- Một loaded class có được instrument theo phạm vi này không?
- Cần recompile consumers để thấy introduced APIs không?
- Direct target calls có đi qua interception/role mới không?

## 4. Interface introduction thay đổi nghĩa vụ hợp đồng

Ví dụ:

~~~java
interface Auditable {
    String auditId();
}
~~~

Một target class không implements Auditable:

~~~java
final class Invoice {
    private final String id;
    Invoice(String id) { this.id = id; }
}
~~~

Đây là hai snippets minh họa, chưa phải chương trình độc lập.

Nếu một AOP language bổ sung Auditable thật sự vào Invoice, **phải có một implementation thích hợp cho auditId**, hoặc compiler/weaver phải áp quy tắc hợp lệ của nó với abstract/default methods.

Hợp đồng interface không chỉ là chữ ký:

- auditId có được phép null không?
- Có ổn định sau khi lưu entity không?
- Có phải duy nhất trong một domain không?
- Có được phép throw ở trạng thái chưa được persistence không?
- Có thread-safe không?
- Có lộ dữ liệu nhạy cảm không?

Một introduced method trả string hằng để lấp đủ chữ ký vẫn có thể phá **behavioral subtyping** nếu interface hứa unique IDs. Type compatibility phải đi kèm semantic compatibility.

### 4.1. Marker interfaces cũng làm thay behavior

Ngay cả interface không có methods có thể ảnh hưởng instanceof, type-based dispatch, reflection, object classification và serialization. Do đó “chỉ thêm marker” không có nghĩa không có tác động lên mã sử dụng.

## 5. Inter-type declaration và declare parents

Một ví dụ **minh họa cú pháp kiểu AspectJ**, không biên dịch bằng javac thuần:

~~~aspectj
public aspect AuditingRole {
    declare parents: demo.Invoice implements demo.Auditable;

    public String demo.Invoice.auditId() {
        return "invoice-audit";
    }
}
~~~

Ý tưởng của đoạn mã:

1. Một aspect xác định target type Invoice.
2. Khai báo Invoice sẽ implement Auditable.
3. Giới thiệu member auditId để đáp ứng contract.
4. Weaver kiểm tra các constraints và tạo type representation tương ứng.
5. Consumers sử dụng woven type theo quy tắc compilation và classloading của môi trường.

Đây là **cú pháp định hướng ý tưởng**, cần xác minh package declarations, syntax và constraints của compiler AOP cụ thể khi ứng dụng. Java compiler thông thường không hiểu aspect declaration.

### 5.1. Giới thiệu field

Một aspect có thể muốn bổ sung state:

~~~text
Invoice:
  introduced auditVersion
  introduced getAuditVersion()
  introduced updateAuditVersion()
~~~

Nhưng field ở đâu? Có thể là per-object binary field, field trong adapter, hoặc external map keyed by identity, tùy implementation. Mỗi phương án có serialization, concurrency, lifecycle và equality khác nhau.

### 5.2. Scope của introduction pattern

Nếu type pattern match cả một package, tất cả classes phù hợp có thể bị thêm interface. Điều này tạo ảnh hưởng dây chuyền:

- Registry coi nhiều objects là Auditable hơn trước.
- Aspect khác chọn các join points theo interface đó.
- Compiler recompile source có overload ambiguities.
- Serializers có thể chọn logic khác cho những type mới.
- Unit tests chỉ một Invoice không phát hiện tác động lên class khác.

Chọn type pattern hẹp và test cả **negative targets** không nên bị introduction.

## 6. Static type, runtime type, instanceof và cast

Trong Java:

- **Static type** xác định methods compiler cho phép gọi và overload signatures có thể chọn.
- **Runtime type** xác định object thực tế có thuộc một interface/class hay không.
- **instanceof** kiểm tra object thực sự compatible với runtime type test.
- **cast** cho phép truy cập contract đã được xác minh nhưng có thể throw ClassCastException nếu sai.
- **Class.isAssignableFrom** và reflection cho biết quan hệ ở class metadata.

Một caller có:

~~~java
Worker worker = obtainWorker();
worker.work();
if (worker instanceof Tagged tagged) {
    System.out.println(tagged.tag());
}
~~~

Đây là snippet chưa độc lập vì cần Worker, Tagged và obtainWorker. Nó cho thấy worker có static type Worker; các methods riêng của Tagged chỉ được gọi khi compiler biết Tagged contract và runtime object phù hợp.

### 6.1. Adapter khác original target

Nếu obtainWorker() trả TaggedAdapter thì instanceof Tagged true. Nếu trả raw CoreWorker chưa implement Tagged thì false. Một API phải nêu rõ liệu mọi Worker nó cấp đều có capability đó hay không.

### 6.2. Binary đã weave nhưng source compiler chưa thấy

Ngay cả khi runtime class thực sự implement interface, một **biến được khai báo Worker** vẫn không tự có method Tagged trong Worker interface. Phải dùng static type/cast/type narrowing phù hợp. Muốn gọi trực tiếp qua concrete type thì compilation phải dùng type information nhất quán với weaving.

## 7. Ví dụ Java 17 runnable: thêm interface bằng adapter, không sửa target class

Ví dụ chạy bằng **JDK 17 thuần**, có chủ đích **không dùng AOP weaver**. Nó làm rõ sự khác nhau giữa object có capability bổ sung và class gốc không đổi, cũng như ảnh hưởng tới overload, generics và reflection.

Tạo **TypeRelationshipDemo.java**:

~~~java
import java.util.List;

public class TypeRelationshipDemo {
    interface Worker {
        String work();
    }

    interface Tagged {
        String tag();
    }

    static final class CoreWorker implements Worker {
        @Override
        public String work() {
            return "core-work";
        }
    }

    static final class TaggedAdapter implements Worker, Tagged {
        private final Worker delegate;
        private final String label;

        TaggedAdapter(Worker delegate, String label) {
            this.delegate = delegate;
            this.label = label;
        }

        @Override
        public String work() {
            return delegate.work();
        }

        @Override
        public String tag() {
            return label;
        }
    }

    static String route(Worker worker) {
        return "Worker";
    }

    static String route(Tagged tagged) {
        return "Tagged";
    }

    static String route(Object value) {
        return "Object";
    }

    static void printTypes(Worker worker, String name) {
        System.out.println(name + " instanceof Worker=" + (worker instanceof Worker));
        System.out.println(name + " instanceof Tagged=" + (worker instanceof Tagged));
    }

    public static void main(String[] args) {
        Worker core = new CoreWorker();
        Worker adapted = new TaggedAdapter(core, "audit");

        printTypes(core, "core");
        printTypes(adapted, "adapted");
        System.out.println("adapted==core=" + (adapted == core));
        System.out.println("core.work=" + core.work());
        System.out.println("adapted.work=" + adapted.work());

        if (adapted instanceof Tagged taggable) {
            System.out.println("adapted.tag=" + taggable.tag());
            System.out.println("route((Tagged)adapted)=" + route(taggable));
        }

        System.out.println("route(adapted)=" + route(adapted));
        System.out.println("route((Object)adapted)=" + route((Object) adapted));

        List<Worker> workers = List.of(adapted);
        System.out.println("workers.element.tag=" + ((Tagged) workers.get(0)).tag());
        System.out.println("Tagged.assignable.from.CoreWorker="
                + Tagged.class.isAssignableFrom(CoreWorker.class));
        System.out.println("Tagged.assignable.from.TaggedAdapter="
                + Tagged.class.isAssignableFrom(TaggedAdapter.class));

        try {
            Tagged invalid = (Tagged) core;
            System.out.println(invalid.tag());
        } catch (ClassCastException error) {
            System.out.println("core.cast=ClassCastException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 TypeRelationshipDemo.java
java TypeRelationshipDemo
~~~

**Stdout thực tế đã được xác minh:**

~~~text
core instanceof Worker=true
core instanceof Tagged=false
adapted instanceof Worker=true
adapted instanceof Tagged=true
adapted==core=false
core.work=core-work
adapted.work=core-work
adapted.tag=audit
route((Tagged)adapted)=Tagged
route(adapted)=Worker
route((Object)adapted)=Object
workers.element.tag=audit
Tagged.assignable.from.CoreWorker=false
Tagged.assignable.from.TaggedAdapter=true
core.cast=ClassCastException
~~~

### 7.1. Hai kết quả work giống nhau nhưng objects khác nhau

TaggedAdapter.work chuyển tiếp tới CoreWorker.work, nên cùng trả core-work. Nhưng adapted==core false: wrapper P và target T khác reference identity.

### 7.2. Chỉ adapter có Tagged

TaggedAdapter implement Worker và Tagged; CoreWorker implement Worker. Vì vậy cast Tagged trên adapted hợp lệ, cast trên raw core throw. **Đây không phải bytecode-level structural weaving của CoreWorker.**

### 7.3. Overload resolution phụ thuộc static expression type

Biến adapted có static type Worker, vì vậy route(adapted) chọn route(Worker) dù runtime object cũng implement Tagged.

Biến taggable có static type Tagged nên route(taggable) chọn route(Tagged). Cast sang Object chọn route(Object).

**Overload resolution là compile-time**, khác virtual method dispatch chọn implementation override trên runtime receiver.

### 7.4. List<Worker> không phải List<Tagged>

Dù element runtime là TaggedAdapter, workers.get(0) có static type Worker. Code phải cast hoặc type-test trước khi gọi Tagged API. Việc bổ sung interface không xóa các quy tắc generics của Java.

### 7.5. Reflection kiểm tra class-level capability

Tagged.class.isAssignableFrom(CoreWorker.class) false nhưng với TaggedAdapter.class là true. Một binary thật đã structural-weave CoreWorker để implement Tagged có thể cho kết quả khác; phải test đúng deployed artifact.

## 8. Thay đổi overload resolution ở mã nguồn

Giả sử chương trình có hai overload:

~~~java
void inspect(Worker worker) { /* ... */ }
void inspect(Tagged tagged) { /* ... */ }
~~~

Đây là snippets minh họa, chưa là chương trình độc lập. Với một expression static type Worker, compiler chọn overload Worker; static type Tagged chọn Tagged.

Nếu một concrete class được introduction để implements đồng thời Worker và Tagged, khi recompile:

~~~java
inspect(concreteValue);
~~~

có thể trở thành **ambiguous** nếu hai overload nhận unrelated interfaces và không cái nào more specific. Phải cast rõ, hoặc thiết kế API không ambiguous.

### 8.1. Runtime object khác compile-time choice

Một thay đổi type hierarchy ở runtime **không tự viết lại method descriptor** của call đã được compile từ trước. Bytecode cũ vẫn nhắm một overload đã chọn, trong khi source recompile với type relation mới có thể chọn khác hoặc báo ambiguous.

Đây là ví dụ vì sao **source compatibility và binary compatibility phải được xét riêng**.

### 8.2. Override khác overload

Override là triển khai một method theo inheritance/dispatch rules; overload là nhiều chữ ký cùng tên. Interface introduction có thể ảnh hưởng cả method set và subtype relations, nhưng theo hai cơ chế khác nhau.

Không dùng tên gọi “match method” để trộn lẫn chọn overload lúc compile với chọn implementation lúc runtime.

## 9. Default methods, xung đột và hierarchy restrictions

Interface có thể có default method:

~~~java
interface A {
    default String label() { return "A"; }
}

interface B {
    default String label() { return "B"; }
}
~~~

Một class implements cả A và B mà chưa có một implementation giải quyết conflict có thể gặp lỗi. Structural introduction không được bỏ qua Java method resolution rules.

### 9.1. Class method và interface defaults

Một concrete/inherited class method có thể có precedence đối với interface default trong những tình huống hợp lệ. Nhưng nếu target method có return type, access hoặc final/static modifier không tương thích với contract, introduction có thể không hợp lệ.

### 9.2. Java không có multiple concrete superclass inheritance

Một declare parents yêu cầu một class extends hai concrete parents là không phù hợp Java type system. Ngay cả thay một superclass cũng có constructor/inherited member constraints.

### 9.3. Final, record và sealed types

Một final class không thể bị subclass. Record classes là final. Sealed types giới hạn permitted subclasses/implementers.

Điều này ảnh hưởng thiết kế generated subclass proxies. Với **direct binary weaving**, một công cụ có thể có khả năng khác, nhưng vẫn phải đáp ứng type rules, class verifier và constraints phù hợp. Không khẳng định một final class cấm mọi dạng interface introduction; cũng không khẳng định weaver luôn bỏ qua được sealed hierarchy.

## 10. Generics, erasure và contracts

Một interface generic:

~~~java
interface Container<T> {
    T get();
}
~~~

Nếu một Invoice type thực sự implement Container<Invoice>, compiler có thể dùng generic contract ấy. Nhưng Java generics sử dụng **type erasure** ở runtime; instanceof không xác minh tùy ý tất cả generic type arguments.

### 10.1. Invariance

Ngay cả khi Invoice là subtype của Auditable, List<Invoice> không tự là subtype của List<Auditable>. Nếu API chỉ đọc, wildcard có thể phù hợp:

~~~java
List<? extends Auditable> view = invoices;
~~~

Dòng này chỉ hợp lệ nếu invoices có compile-time type phù hợp và Invoice là Auditable theo type system compiler dùng.

### 10.2. Bridge methods và name clashes

Một generic interface method có thể cần bridge method hoặc gây name-clash sau erasure. Nếu method đã tồn tại trên target có signature không tương thích với introduced generic contract, compiler/weaver phải xử lý hợp lệ hoặc từ chối.

Không suy introduction đúng chỉ từ hình thức khai báo source của các generic parameters.

### 10.3. Raw interface không chứng minh type argument đúng

Một object runtime có thể cast được sang raw Container, nhưng nếu implementation trả dữ liệu khác với Container<Invoice> contract, ClassCastException có thể xảy ra tại use site. Generic contract là phần cần kiểm thử, không phải annotation trang trí.

## 11. Reflection, annotations và metadata

Structural type changes có thể ảnh hưởng:

- Class.getInterfaces()/getGenericInterfaces().
- Class.isAssignableFrom().
- Class.getMethods()/getDeclaredMethods().
- Method.getDeclaringClass().
- Method/Type annotations và generic metadata.
- Object.getClass().
- Framework-neutral scanners/registries dựa trên marker interface.

Nếu class definition thực sự đã weave, reflection trên class ấy có thể thấy type/method additions. Nếu introduction chỉ thuộc proxy, reflection trên proxy có thể thấy interface mà reflection trên target không thấy.

### 11.1. GetMethods khác GetDeclaredMethods

getMethods xét các public methods có thể truy cập từ type, bao gồm inherited methods theo Java reflection contract; getDeclaredMethods tập trung methods declared tại class. Chúng không đồng nhất.

Một introduced method có thể được biểu diễn ở các lớp khác nhau tùy mechanism. Hãy kiểm tra type thật đang được quan sát.

### 11.2. Annotation trên implementation không tự có ở interface/proxy

Annotation placement và inheritance rules khác nhau theo annotation target, method, class, interface. Đừng cho rằng annotation của một method implementation xuất hiện khi chỉ reflect interface Method.

### 11.3. Những tác động gián tiếp

Một serializer, validator, object mapper hoặc policy registry có thể chọn nhánh theo interface. Thêm interface cho một class có thể làm chính các components ấy đổi behavior dù source của chúng không thay đổi.

## 12. Class loader, weaving phase và loaded classes

Java class identity không chỉ là binary name; **defining ClassLoader** là thành phần của runtime type identity.

Hai interfaces cùng tên do hai defining loaders khác nhau load có thể không compatible. Dẫn tới:

- ClassCastException.
- LinkageError hoặc visibility issue.
- isAssignableFrom false dù class names trông giống.
- Plugin/service module không thấy introduced API.
- Runtime deploy type khác với build/test.

### 12.1. Weaving tại thời điểm thích hợp

Nếu type relations phải thay trên binary, weaving thường cần diễn ra ở một phase mà runtime type còn có thể được định nghĩa với structure mới. Không mặc định JVM đã load class rồi cho phép thay superclass/interface bằng arbitrary redefinition.

### 12.2. Phạm vi weaving

Nếu build chỉ weave package A còn production load một class cùng tên từ module/loader khác, behavior có thể khác. Cần bảo đảm mapping target types, weaving configuration và generated artifacts nhất quán.

### 12.3. Consumer compile against type introduction

Consumer source dùng Invoice như một Auditable có thể phải compile trong một môi trường hiểu type introduction. Nếu chạy với raw Invoice không woven, những casts, method references hoặc links có thể fail tùy tình huống.

Kết luận: **compile-time type view và runtime bytecode phải thống nhất**.

## 13. Source, binary và behavioral compatibility

Ba góc nhìn:

| Loại | Câu hỏi |
|---|---|
| **Source compatibility** | Source cũ recompile với type mới được không? |
| **Binary compatibility** | Bytecode đã compile có link và chạy được không? |
| **Behavioral compatibility** | Kết quả, dispatch, side effects, type-based policies có còn đúng? |

Ví dụ một class thêm interface có thể vẫn giữ các method cũ nên binary consumer cũ dùng method đó vẫn chạy, nhưng source recompile có thể gặp **overload ambiguity**. Một library dùng instanceof để phân nhánh có thể đổi behavior ngay cả khi không có lỗi compilation.

### 13.1. API contract là public dependency

Nếu Auditable interface là một public API và được introduction vào nhiều classes, version mới của Auditable thêm abstract method có thể yêu cầu cập nhật những implementations đó. Không thể để structural introduction là hidden build detail không được versioning.

### 13.2. Compile/test/production parity

Cần chạy tests trên **artifact sau weave** và deployed runtime, không chỉ source class ban đầu. Nếu runtime một số nodes dùng type introduction còn nodes khác không, behavior của casts/discovery có thể không nhất quán.

### 13.3. Compatibility phụ thuộc consumers

Một consumer chỉ dùng Worker.work có thể không bị ảnh hưởng bởi Tagged introduction. Consumer dùng Tagged casts hoặc serializers dựa interface có thể bị ảnh hưởng mạnh. Không có câu trả lời tương thích tuyệt đối nếu chưa biết use sites.

## 14. Serialization và persistence

Một introduced field hay interface có thể ảnh hưởng serialization form hoặc nhánh serializer chọn.

Những câu hỏi:

- Introduced field có nằm trong serialized state không?
- SerialVersionUID hoặc binary compatibility có thay đổi không?
- Adapter/proxy handler/delegate có serializable contract không?
- Deserialization trên runtime không weave có thiếu capability không?
- Một marker interface mới có đổi serialization strategy không?
- Data versions cũ được đọc bởi class mới thế nào?
- External persistence mapper dùng reflection có nhìn thấy members mới không?

**Serializable marker không tự bảo đảm object graph và trạng thái handler/delegate có thể serialize đúng.** Nếu fields introduction chứa mutable/global state, lifecycle còn phức tạp hơn.

## 15. Reference identity, equality và cache

Một adapter P bọc target T thường có **P != T** theo reference identity. Dù cả hai cùng đại diện một service logical, hai objects có monitor, lifecycle và hash/identity semantics khác.

Nếu cache dùng P làm key, raw T có thể không tìm thấy entry. Một IdentityHashMap chắc chắn phân biệt hai references khi chúng là hai objects.

Không forward equals từ adapter xuống target một chiều:

~~~text
P.equals(T) = T.equals(T) = true
T.equals(P) = false
~~~

Điều này có thể vi phạm symmetry khi T giữ identity equality mặc định.

Muốn cache theo thực thể nghiệp vụ, dùng một **business ID bất biến** với contract rõ, thay vì ép proxy và target có cùng object identity.

## 16. Introduced mutable state và concurrency

Giả sử introduction thêm mutable counter vào mỗi Invoice:

~~~text
Invoice:
   introduced int auditCount
   introduced incrementAuditCount()
~~~

Nếu cùng Invoice instance được nhiều threads thao tác, increment bằng ++ vẫn có thể **lost update**. Structural introduction không tự thêm synchronization.

Nếu trạng thái được lưu trong một shared aspect field hay external map thay vì per-object field, nhiều Invoices có thể cùng chia sẻ state và yêu cầu concurrency control khác.

Cân nhắc:

- Immutable introduced metadata.
- AtomicInteger/AtomicLong nếu cần atomic counter.
- Lock bảo vệ multi-field invariant.
- ConcurrentMap với key/lifecycle policy đúng.
- ThreadLocal cho state theo thread, có cleanup.
- Data isolation theo instance khi semantics thực sự per-object.

### 16.1. Initialization và lifecycle

Introduced fields cần initialization đáng tin cậy trước khi methods mới được gọi. Constructors, serialization, cloning và reflection creation có thể đi qua các đường khác nhau. Cần test state đã sẵn sàng ở mọi object creation path.

### 16.2. External state map retention

Nếu aspect giữ Map<Target, State> với strong references, Map có thể giữ target sống lâu. Weak key/caching strategy cũng cần test equality, GC và concurrency. Không dùng identityHashCode như unique ID tuyệt đối.

## 17. Structural introduction có thể đổi pointcut matches

Một aspect A bổ sung Auditable vào Invoice; aspect B có type-based pointcut chọn methods trên những đối tượng Auditable.

Nếu B áp dụng dựa trên woven hierarchy, introduction của A có thể làm **tập join points của B thay đổi**. Đây là semantic dependency dù hai aspects không gọi nhau.

Nhưng exact matching còn phụ thuộc:

- Call hay execution join point.
- Static declaring type hay runtime target type.
- Type pattern semantics của AOP language.
- Weaver precedence và instrumentation phase.
- Proxy interface introduction hay binary structural changes.
- Class loader và package scope.

Không suy rằng mọi pointcut sẽ luôn match theo cùng quy tắc khi type hierarchy đổi. Cần positive và negative matching tests.

### 17.1. Multiple introductions

Hai aspects có thể cố thêm cùng method với implementations khác nhau, hoặc giới thiệu interfaces có default conflicts. Cần chính sách conflict/precedence rõ; không tin một aspect tự động “thắng” aspect khác.

### 17.2. Advice khác vẫn có thể bypass

Nếu capability chỉ nằm ở proxy P, những lời gọi trực tiếp trên T không tự đi qua P. Nếu behavior introduced phụ thuộc một advice ở P, cần kiểm soát reference distribution và self-invocation theo mechanism thực.

## 18. Khi nào nên dùng structural introduction?

Có thể hợp lý nếu:

1. Nhiều target types thực sự cần public capability/interface chung.
2. Contract đó có ý nghĩa rõ ràng và có implementation đủ đúng.
3. Build/weaving pipeline được quản lý chặt.
4. Patterns giới hạn tập target và test đúng đối tượng bị/không bị áp dụng.
5. Các consumers thực sự cần type-level contract.
6. Source/binary/behavioral compatibility được đánh giá.
7. Class loaders và deployment có cùng woven type information.
8. Cách tổ chức bằng aspect tăng tính rõ ràng hơn chi phí ẩn của API change.

Không cần structural type change nếu mục đích chỉ là:

- Ghi log invocation.
- Đo thời gian method.
- Đếm operation.
- Kiểm tra quyền ở một method boundary.
- Cache kết quả mà không cần exposing một interface mới.

Các mục tiêu ấy thường có thể dùng advice, decorator hoặc composition rõ ràng.

## 19. Phương án đơn giản hơn: explicit interface

Nếu source class nằm trong quyền kiểm soát và capability thật sự là một domain contract:

~~~java
final class Invoice implements Auditable {
    @Override
    public String auditId() {
        return "...";
    }
}
~~~

Đây là snippet minh họa, chưa độc lập. Lợi ích:

- Type relation hiện rõ trong source.
- IDE, Java compiler và reviewers dễ nhận biết.
- Không có hidden weaving requirement để type-check.
- Tests không cần đoán object đã introduction hay chưa.

Nhược điểm: phải sửa nhiều classes, có thể tạo repetition hoặc trộn concern vào domain nếu introduction phục vụ crosscutting role thuần.

## 20. Phương án khác: adapter, decorator và composition

Adapter có thể expose một interface mới chỉ tại boundary cần dùng:

~~~text
Raw Invoice T
      |
      v
AuditableInvoiceAdapter P
      |
      v
Consumer sees Auditable
~~~

Ưu điểm:

- Không cần chỉnh sửa class binary gốc.
- Áp dụng được cho third-party types.
- Ranh giới capability rõ khi API trả adapter.
- Dễ unit test riêng.

Nhược điểm:

- Hai object identities.
- Caller có T thô không tự thấy Auditable.
- Cast/reflection behavior trên T khác P.
- Equality/hashCode/serialization cần contract riêng.
- Delegate có thể bỏ sót methods hoặc làm sai concurrency boundaries.

Nếu mục tiêu chỉ là gắn metadata cho một class mà không cần caller cast, một metadata registry cũng có thể đủ, nhưng cần kiểm soát class loader/lifecycle.

## 21. Thứ tự đánh giá và quy trình migration

Một kế hoạch migration nên gồm:

1. **Liệt kê use sites:** casts, instanceof, reflection, overloads, registry lookups.
2. **Vẽ type graph trước/sau:** interfaces, superclass, inherited/default methods.
3. **Xác định mechanism:** woven binary hay proxy/adapter.
4. **Kiểm tra method contracts:** return/exception/thread safety/semantics.
5. **Kiểm tra collisions:** signature, generics erasure, default methods.
6. **Kiểm tra final/sealed constraints** và class loading.
7. **Tạo compiler tests:** source trước/sau và overload ambiguities.
8. **Tạo runtime tests:** cast, instanceof và isAssignableFrom trên object thật.
9. **Tạo reflection/serializer tests:** trên binary deployed, không chỉ type mocks.
10. **Kiểm tra điểm cấp object:** factory có trả P hay T?
11. **Kiểm tra identity:** equality, cache keys, lock monitors.
12. **Kiểm tra concurrency:** introduced fields/state thread-safe nếu cần.
13. **Kiểm tra pointcut interactions:** advice khác match rộng hơn không?
14. **Kiểm tra old/new artifacts:** link và deploy consistency.
15. **Tài liệu hóa API/versioning:** giới hạn type capability và fallback khi không weave.

## 22. Ma trận kiểm thử

| Kịch bản | Cần xác minh |
|---|---|
| Target raw không implement capability | instanceof false, cast thất bại khi dùng sai |
| Adapter implements capability | instanceof/cast thành công trên adapter |
| Structural-woven target | Runtime Class có interface/member mới |
| Proxy và target cùng workflow | Reference identity khác, dù kết quả delegate giống |
| Static type Worker | Chỉ Worker methods có thể gọi trực tiếp |
| Overloads Worker/Tagged | Compiler chọn theo static type |
| Concrete class implements hai interfaces | Ambiguous overload được xử lý |
| Generic List<Worker> | Không tự coi là List<Tagged> |
| Reflection class relation | getInterfaces/isAssignableFrom đúng deployed type |
| Introduced method | Return/exception semantics đúng contract |
| Default method conflicts | Compiler/weaver báo hoặc override hợp lệ |
| Final/sealed types | Không tạo hierarchy bất hợp lệ |
| Class loader isolation | Type identity và cast không nhầm |
| Serialization | Old/new data được xử lý đúng |
| Factory/distribution | Caller nhận đúng object có capability |
| HashMap/IdentityHashMap | Equality/caching không nhầm P với T |
| Concurrent mutable field | Không lost update |
| Woven type consumer vs raw runtime | Mismatch bị phát hiện |
| Other aspects by interface pattern | Pointcut matches có kiểm soát |
| Negative target types | Không bị giới thiệu interface nhầm |
| API version bump | Giữ source/binary/behavioral contract theo yêu cầu |

## 23. Những sai lầm phổ biến

**1.** Cho rằng before/around advice tự thay Java inheritance.

**2.** Cho rằng proxy implement Tagged thì raw target cũng implement Tagged.

**3.** Đồng nhất overload resolution với runtime method dispatch.

**4.** Bỏ qua compiler static type khi gọi introduced methods.

**5.** Cast raw target không có introduced interface rồi ngạc nhiên vì ClassCastException.

**6.** Cho rằng marker interface không có tác động hành vi.

**7.** Thêm interface nhưng không cung cấp method implementation đúng contract.

**8.** Không kiểm tra default method conflict.

**9.** Nghĩ generics invariant tự biến thành covariance sau introduction.

**10.** Cho rằng final class có thể được subclass như class thường.

**11.** Nghĩ loaded class có thể thay superclass/interface tùy ý trong JVM.

**12.** Bật weaving khi test nhưng không bật khi deploy.

**13.** Không nhận ra recompile có overload ambiguity dù binary cũ vẫn chạy.

**14.** Dùng adapter equals bất đối xứng với target.

**15.** Quên giới hạn ClassLoader type identity.

**16.** Cho rằng source annotation ở target luôn hiện trên proxy methods.

**17.** Dùng introduced mutable field không đồng bộ ở shared object.

**18.** Không kiểm thử pointcut của aspect khác sau thay đổi hierarchy.

**19.** Dùng interface quá rộng rồi cho nhiều methods throw UnsupportedOperationException.

**20.** Chọn structural weaving khi một explicit interface/adapter nhỏ đã đáp ứng yêu cầu.

## 24. Câu hỏi ôn tập và đáp án

**Câu 1.** Trước method của Invoice có logging advice. Invoice đã implement Auditable chưa?

**Đáp:** Chưa; advice hành vi không tự làm type introduction.

**Câu 2.** TaggedAdapter implement Tagged và delegate CoreWorker. Raw CoreWorker có thể cast Tagged không?

**Đáp:** Không, trừ khi class CoreWorker thực sự có quan hệ type đó qua cơ chế khác.

**Câu 3.** Vì sao route(adapted) chọn Worker trong demo?

**Đáp:** Static type của biến adapted là Worker, overload được giải quyết lúc compile.

**Câu 4.** Vì sao route(taggable) chọn Tagged?

**Đáp:** Biến taggable có static type Tagged.

**Câu 5.** Woven CoreWorker implements Tagged thật có thể thay đổi reflection không?

**Đáp:** Có nếu deployed runtime Class chứa interface mới.

**Câu 6.** List<Invoice> tự là List<Auditable> khi Invoice extends/implements Auditable không?

**Đáp:** Không, generics invariant.

**Câu 7.** Tại sao interface marker vẫn có thể đổi behavior của một serializer?

**Đáp:** Serializer có thể chọn strategy dựa vào instanceof hoặc interface metadata.

**Câu 8.** Khi hai unrelated interfaces có cùng default method, introduction có thể gặp lỗi gì?

**Đáp:** Method conflict, cần implementation giải quyết hợp lệ.

**Câu 9.** Một class final có thể được subclass proxy không?

**Đáp:** Không theo Java subclassing rules.

**Câu 10.** Structural weaving có luôn giống subclass proxy không?

**Đáp:** Không; direct binary weaving và subclassing là hai mechanisms khác nhau.

**Câu 11.** Source recompile có thể fail dù bytecode cũ vẫn link không?

**Đáp:** Có, ví dụ overload trở thành ambiguous.

**Câu 12.** Vì sao Class.isAssignableFrom có thể false dù hai interface names giống nhau?

**Đáp:** Chúng có thể do những ClassLoaders khác nhau định nghĩa.

**Câu 13.** Introduced mutable field count++ có thread-safe mặc định không?

**Đáp:** Không, concurrency vẫn cần synchronization/atomic state.

**Câu 14.** Một aspect khác chọn Auditable join points có bị introduction ảnh hưởng không?

**Đáp:** Có thể, vì type hierarchy mới thay tập matching.

**Câu 15.** Có nên dùng structural introduction chỉ để log một operation không?

**Đáp:** Thường không cần; một method-level advice hoặc decorator có thể đủ.

**Câu 16.** Khi runtime cấp raw T nhưng documentation hứa capability trên P, lỗi nằm ở đâu?

**Đáp:** Hợp đồng factory/distribution không nhất quán về object được cung cấp cho caller.

## 25. Sơ đồ tóm tắt

~~~text
             CẦN THÊM CAPABILITY?
                       |
                       v
            Cần Java subtype thật?
              /              \
            KHÔNG             CÓ
              |                |
              v                v
          Advice hoặc    Có thể sửa source?
          metadata          /       \
                         CÓ         KHÔNG
                          |            |
                          v            v
                   implements       Structural
                   trực tiếp        introduction?
                          |            |
                          +------+-----+
                                 |
                                 v
                 Xác định runtime mechanism
                woven class / adapter / proxy
                                 |
                 +---------------+----------------+
                 |               |                |
                 v               v                v
             instanceof        overload         reflection
             cast/identity     static types     class metadata
                 |               |                |
                 +---------------+----------------+
                                 |
                                 v
                      Contract + QA migration
                  compile/runtime consistency,
                  generics, compatibility, state,
                  class loaders, pointcut effects
~~~

**Kết luận:** Type relationship introduction là một dạng **structural crosscutting** có hệ quả trực tiếp với mã sử dụng. Cần phân biệt **type của class được weave** với **type mở rộng chỉ thuộc một proxy/adapter**; kiểm chứng instanceof/cast, static overload selection, generics, reflection và object identity trên đúng object. Mọi introduction còn phải giữ Java type rules, hợp đồng interface, tương thích nguồn/binary/behavioral, versioning, concurrency và tác động tới các pointcut khác. Chỉ dùng thay đổi type khi có một capability-level contract thực sự; nếu chỉ cần bổ sung behavior, một advice hoặc explicit adapter thường dễ lý giải hơn.

## Tài liệu tham khảo

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): structural crosscutting và inter-type declarations trong một ngôn ngữ AOP.
- [JLS 17 — Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html): Java type system.
- [JLS 17 — Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html): class hierarchy và method rules.
- [JLS 17 — Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html): interfaces/default methods.
- [JLS 17 — Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html): cast, instanceof, overload resolution.
- [JLS 17 — Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html): binary compatibility.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): reflection, assignability.
- [Java SE 17 — Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): interface-based proxy capabilities.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-006">Quan hệ giữa mở rộng cấu trúc, mixin và kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa mở rộng cấu trúc, mixin và kế thừa trong AOP

> **Phạm vi:** Kiến thức lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP) từ cơ bản đến nâng cao. Bài làm rõ ba khái niệm dễ bị gọi thay thế nhau: **mở rộng cấu trúc (structural extension / introduction)**, **mixin** và **kế thừa (inheritance)**; đồng thời phân biệt với composition, delegation, interface default methods và proxy. Khả năng thêm interface, method, field hoặc quan hệ cha vào lớp đã có phụ thuộc mô hình ngôn ngữ/công cụ AOP. Các ví dụ Java 17 minh họa nguyên lý và giới hạn, không phải cú pháp một bộ dệt AOP cụ thể.

## 1. Tại sao cần phân biệt ba khái niệm?

Một lớp nghiệp vụ ban đầu chỉ cung cấp các thao tác cốt lõi:

~~~text
Order
  - code
  + code()
  + calculateTotal()
~~~

Sau đó hệ thống muốn các Order có thể được gắn nhãn, ghi thông tin truy vết hoặc tham gia một hợp đồng mới. Có ít nhất ba hướng thiết kế:

1. **Kế thừa:** định nghĩa một kiểu con mở rộng kiểu cha đã có.
2. **Mixin:** tái sử dụng một nhóm hành vi giữa các kiểu khác nhau, thường không muốn ép chúng vào một nhánh phân cấp chung.
3. **Mở rộng cấu trúc bằng AOP:** dùng cơ chế introduction/inter-type declaration tương ứng để khai báo thêm thành viên hoặc quan hệ kiểu cho những lớp đích từ bên ngoài phần khai báo ban đầu.

Ba hướng có thể cho cảm giác giống nhau ở điểm cuối cùng: object có thêm một API hoặc hành vi. Nhưng **chúng khác nhau về nguồn khai báo, cơ chế áp dụng, danh tính kiểu, thứ tự phân giải method và vòng đời trạng thái**.

Câu hỏi thiết kế không phải “cách nào thêm được method?” mà là: *Ai sở hữu hợp đồng mới? Object gốc có thật sự trở thành thành viên của interface đó không? Hành vi được cài ở đâu? Điều gì xảy ra khi kế thừa và các extension xung đột?*

## 2. Ba định nghĩa cốt lõi

### 2.1. Kế thừa

**Inheritance** là quan hệ giữa kiểu con và kiểu cha được ngôn ngữ/hệ kiểu định nghĩa. Trong Java, một class có thể extends một class cha trực tiếp và có thể implements nhiều interface theo luật ngôn ngữ.

Kế thừa có thể đem lại:

- Hành vi được thừa hưởng từ superclass.
- Override method theo quy tắc dynamic dispatch.
- Khả năng dùng subtype ở nơi yêu cầu supertype khi hợp đồng phù hợp.
- Trạng thái instance từ superclass và quan hệ khởi tạo tương ứng.

Kế thừa không đồng nghĩa “mọi object sẽ tự nhận code mới khi ta khai báo một class con”. Một instance vốn thuộc class gốc không tự chuyển thành instance của class con mới.

### 2.2. Mixin

**Mixin** là ý tưởng tái sử dụng một phần chức năng giữa nhiều kiểu, chẳng hạn nhóm method gắn nhãn, kiểm toán hoặc định dạng, mà không nhất thiết mô tả một quan hệ “là một” của domain.

Mixin có nhiều cách hiện thực tùy ngôn ngữ:

- Trait hoặc module có method dùng lại.
- Interface cùng default methods khi khả năng ngôn ngữ cho phép.
- Delegate object cung cấp hành vi.
- Một đối tượng triển khai interface và chia sẻ logic bằng composition.
- Structural introduction kết hợp interface với một implementation/delegate trong hệ AOP có hỗ trợ.

**Mixin là mẫu tổ chức/tái sử dụng hành vi**, không mặc định là một cú pháp, cũng không mặc định tạo superclass mới.

### 2.3. Mở rộng cấu trúc trong AOP

**Structural extension** là việc thay đổi **cấu trúc nhìn thấy được của một kiểu hoặc đối tượng qua hệ thống can thiệp**, thay vì chỉ chạy advice trước/sau/bao quanh lời gọi.

Một cơ chế introduction có thể, tùy công cụ:

- Làm một lớp đích thực hiện thêm interface.
- Cung cấp một hoặc nhiều method được giới thiệu.
- Cung cấp trạng thái bổ sung hoặc delegate cho hành vi mới.
- Thêm quan hệ kiểu cha/con trong giới hạn được cơ chế hỗ trợ.
- Thay cách các công cụ compile/weave nhìn thấy các thành viên được bổ sung.

Không được mặc định rằng mọi engine AOP có đủ tất cả khả năng này. Một engine chỉ có runtime interception cũng có thể **không hỗ trợ structural changes**.

## 3. AOP hành vi và AOP cấu trúc khác nhau

**Behavioral advice** bổ sung hành vi tại join point:

~~~text
Caller -> Before/around/after advice -> Original method
~~~

Cấu trúc method và interface mà caller thấy có thể vẫn giữ nguyên. Ví dụ logging trước pay không có nghĩa đối tượng PaymentService tự implements Loggable.

**Structural introduction** hướng đến một thay đổi dạng:

~~~text
Before:
  class Order { code(); }

After introduction (conceptually):
  Order also supports interface Taggable
  + addTag(String)
  + tags()
~~~

Một advice có thể dùng trạng thái mới sau introduction, nhưng **advice và introduction không cùng một thao tác**. Advice tác động vào các điểm thực thi; introduction tác động vào mô hình cấu trúc/kiểu hoặc mặt ngoài đối tượng theo cơ chế.

## 4. Bảng so sánh ở mức kiến trúc

| Tiêu chí | Kế thừa | Mixin / trait | AOP structural extension |
|---|---|---|---|
| Mục đích chính | Quan hệ subtype và mở rộng kiểu | Tái sử dụng hành vi | Bổ sung cấu trúc ngoài phần khai báo target |
| Nơi khai báo | Class/interface ở mã nguồn | Trait/interface/delegate | Aspect hoặc quy tắc introduction |
| Target có cần biết tính năng? | Thường có trong khai báo subtype | Phụ thuộc cách áp dụng | Có thể giảm thay đổi trực tiếp target |
| Thay đổi hệ kiểu? | Có, theo ngôn ngữ | Có hoặc không | Có thể có, tùy weaving/proxy model |
| Bổ sung instance state? | Qua class/subclass | Tùy implementation | Phụ thuộc loại introduction |
| Method resolution | Luật ngôn ngữ | Luật trait/default/delegate | Luật công cụ kết hợp ngôn ngữ |
| Tranh chấp tên | Override/overload | Conflict resolution | Có thể fail weaving hoặc cần precedence/giải quyết |
| Vòng đời | Theo object class | Theo owner/delegate | Per-target/per-instance/per-proxy tùy mô hình |
| Công cụ thực thi | Compiler/runtime Java | Compiler/runtime của cách áp dụng | Weaver/interceptor/adapter tương ứng |

Không đánh đồng ô “thay đổi hệ kiểu” giữa một **proxy implementing interface** và **class gốc thực sự được dệt để implements interface**.

## 5. Ví dụ trực giác: một tính năng gắn nhãn

Giả sử nhiều đối tượng domain cần thao tác:

~~~text
Taggable
  + addTag(tag)
  + tags()
~~~

Các đối tượng gồm Order, Invoice và Shipment; chúng không nhất thiết có một superclass domain chung ngoài Object.

Một giải pháp inheritance có thể tạo một lớp nền TaggedEntity, nhưng điều đó:

- Đưa tính năng gắn nhãn vào phân cấp class.
- Có thể bất tiện với một superclass đã được chọn.
- Tạo nguy cơ base class tích lũy quá nhiều mối quan tâm.
- Có thể buộc domain class phụ thuộc hạ tầng tagging.

Một giải pháp mixin/delegate có thể cho các class sử dụng chung TagState/TagService mà không cần cùng superclass.

Một giải pháp introduction trong AOP có thể khai báo ở ngoài rằng một nhóm lớp đích nay hỗ trợ Taggable, nếu cơ chế chọn lớp và bổ sung interface/member cho phép.

**Cùng API đầu ra không có nghĩa cùng quan hệ kiểu hoặc cùng vị trí lưu trạng thái.**

## 6. Hệ kiểu Java và single class inheritance

Java class chỉ có **một superclass trực tiếp**. Một class có thể implements nhiều interface, và các interface có thể mở rộng nhiều interface khác.

Điều này ảnh hưởng thiết kế mixin:

- Không thể cho một class Java extends đồng thời hai concrete base classes.
- Một interface default method có thể cung cấp hành vi chung nhưng không có instance field theo cách một class bình thường có.
- Nếu hành vi mixin cần state, phải xác định nơi state được lưu: trong class, delegate, external map hoặc cấu trúc do weaver tạo.
- Một công cụ structural AOP không nên được hiểu là “bỏ qua mọi luật hợp lệ của hệ kiểu Java”.

Nếu introduction muốn thêm quan hệ superclass, phải tuân theo **ràng buộc của class hierarchy và bytecode**. Không thể tùy ý ghép nhiều superclass class như một phép mixin vô điều kiện.

## 7. Interface và mixin không hoàn toàn đồng nghĩa

Một interface thể hiện một **hợp đồng kiểu**. Java interface có thể có abstract methods, default methods, static methods và private helper methods trong phạm vi luật ngôn ngữ.

Mixin là ý tưởng **chia sẻ implementation**. Một interface chỉ chứa abstract method tạo hợp đồng nhưng không cung cấp code dùng chung; một interface có default methods có thể đóng vai trò mixin-like.

Ví dụ khái niệm:

~~~java
interface Describable {
    String code();

    default String description() {
        return "code=" + code();
    }
}
~~~

Đây là mảnh Java hợp lệ nhưng không phải file runnable độc lập.

Một class implements Describable cần cung cấp code(); khi ấy có thể dùng default description(). Điều này là **mixin-like behavior bằng ngôn ngữ Java**, không cần structural weaver và cũng không tự giới thiệu interface cho các class vốn chưa implements nó.

## 8. Composition/delegation cũng có thể giống mixin

Một class có thể giữ delegate:

~~~text
Order
  has-a TagState
  addTag delegates to TagState.add
~~~

Ưu điểm:

- State owner rõ.
- Kiểm thử delegate độc lập.
- Không cần hierarchy phức tạp.
- Dễ chọn thread-safety/synchronization.
- Không phụ thuộc bytecode weaving để biết method tồn tại.

Nhược điểm có thể là phải viết forwarding methods, sửa target hoặc bọc target trong adapter.

Trong một số hệ AOP, một introduction có thể được hiện thực bằng **delegate**. Nhưng điều này phụ thuộc cơ chế; không được tuyên bố mọi introduction đều tương đương composition thủ công.

## 9. Kế thừa là subtype relation chứ không phải dán thêm method

Nếu class PriorityOrder extends Order, quan hệ:

~~~text
PriorityOrder is-an Order
~~~

có ý nghĩa với overload resolution, subtype checks và dynamic dispatch.

Một Taggable mixin được áp dụng cho Order có thể chỉ biểu thị:

~~~text
Order supports Taggable behavior
~~~

Không nhất thiết tạo ra quan hệ domain “TaggedOrder là một nhánh con của Order”. Một structural introduction có thể đưa ra interface membership, còn việc có superclass relation mới hay không phải dựa vào tính năng của công cụ.

**Mối quan hệ kiểu nên phản ánh ý nghĩa hợp đồng**, không chỉ tiện viết code.

## 10. Ví dụ Java 17 số 1: interface default, xung đột và kế thừa

Chương trình này trình bày ba quy tắc Java cụ thể:

1. Một class implements hai interface cung cấp default method cùng chữ ký phải giải quyết xung đột phù hợp.
2. Một subclass có thể override method đã được class cha giải quyết.
3. Method concrete được thừa hưởng từ superclass có ưu tiên trước default method interface tương ứng trong trường hợp minh họa.

Lưu file **InheritanceMixinDemo.java**:

~~~java
public class InheritanceMixinDemo {
    interface LeftLabel {
        default String label() {
            return "LEFT";
        }
    }

    interface RightLabel {
        default String label() {
            return "RIGHT";
        }
    }

    static class Combined implements LeftLabel, RightLabel {
        @Override
        public String label() {
            return LeftLabel.super.label()
                    + "+"
                    + RightLabel.super.label();
        }
    }

    static class Specialized extends Combined {
        @Override
        public String label() {
            return "SPECIAL(" + super.label() + ")";
        }
    }

    static class BaseRecord {
        public String label() {
            return "BASE";
        }
    }

    static class DerivedRecord extends BaseRecord
            implements LeftLabel {
        // BaseRecord.label() đã cung cấp implementation.
    }

    public static void main(String[] args) {
        Combined combined = new Combined();
        Specialized specialized = new Specialized();
        DerivedRecord derived = new DerivedRecord();

        System.out.println("[combined] " + combined.label());
        System.out.println("[specialized] " + specialized.label());
        System.out.println("[class-wins] " + derived.label());
        System.out.println("[as-interface] "
                + ((LeftLabel) combined).label());
    }
}
~~~

Biên dịch/chạy bằng JDK 17:

~~~bash
javac InheritanceMixinDemo.java
java InheritanceMixinDemo
~~~

**Stdout chính xác:**

~~~text
[combined] LEFT+RIGHT
[specialized] SPECIAL(LEFT+RIGHT)
[class-wins] BASE
[as-interface] LEFT+RIGHT
~~~

### 10.1. Combined phải giải quyết xung đột

LeftLabel và RightLabel đều cung cấp label(). Combined override rõ ràng và gọi hai default implementations qua InterfaceName.super.

Nếu Combined không có một quy tắc override phù hợp, compiler Java sẽ báo xung đột default methods. Không có chuyện JVM tùy ý chọn interface xuất hiện đầu tiên trong implements list.

### 10.2. Specialized minh họa kế thừa và dynamic dispatch

Specialized extends Combined và override label. Khi gọi trên đối tượng Specialized, implementation override được thực thi.

Đây là luật Java thông thường, không phải weaving hoặc introduction. Việc có mixin-like behavior từ interface không xóa quy tắc subclass dispatch.

### 10.3. BaseRecord minh họa ưu tiên class implementation

DerivedRecord extends BaseRecord và implements LeftLabel. BaseRecord đã cung cấp label concrete; do đó trong tình huống này method của class được dùng thay vì default label của interface.

Không suy rằng mọi engine introduction có thể dễ dàng áp dụng một default method để ghi đè method sẵn có của class. Muốn chặn/thay hành vi method đang tồn tại là một chủ đề **behavioral advice/override/dispatch** khác với bổ sung cấu trúc, và có các quy tắc xung đột riêng.

### 10.4. Gọi qua interface không thay implementation động

Khi cast combined thành LeftLabel và gọi label(), method override ở Combined vẫn được dùng. Hợp đồng interface quyết định cách sử dụng về mặt kiểu; dispatch tới implementation hợp lệ tại runtime.

## 11. Đây chưa phải structural AOP

InheritanceMixinDemo chỉ dùng những tính năng sẵn có trong source Java. Các class **tự khai báo** implements/extends; không có aspect nào bổ sung interface cho một class đã định nghĩa.

Điều quan trọng là dùng ví dụ để hiểu **luật kiểu và giải quyết xung đột** mà một implementation AOP có thể phải tương thích, không coi Java default methods là một ngôn ngữ introduction chung.

Muốn quan sát hiệu ứng thêm interface ở ngoài target source, cần phân biệt **adapter/proxy** với **weaving thực sự thay đổi định nghĩa class**.

## 12. Introduction có thể có nhiều cấp độ

Một số kiểu mở rộng cấu trúc về mặt khái niệm:

### 12.1. Interface introduction

Công cụ khiến lớp đích được nhìn nhận là thực hiện một interface mới. Khi có hỗ trợ đầy đủ, code được biên dịch trong môi trường biết introduction có thể gọi các method của interface theo hợp đồng.

### 12.2. Method introduction

Một method mới được bổ sung vào mô hình cấu trúc của lớp đích. Việc compiler, class loader và reflection có nhìn thấy nó ra sao phụ thuộc cách weaving.

### 12.3. Field/state introduction

Một trạng thái mới được tổ chức để phục vụ hành vi bổ sung. Nó có thể gắn từng instance, một aspect instance, một delegate hoặc một vùng lưu bên ngoài.

### 12.4. Parent-type introduction

Một số hệ AOP có khả năng thay hoặc khai báo thêm quan hệ kiểu cha trong giới hạn nhất định. Phải xem tính hợp lệ trong hierarchy; không coi đây là một cách tùy ý vượt luật single class inheritance.

### 12.5. Delegate-backed introduction

Một mặt ngoài mới được ghép từ target và delegate. Đôi khi caller chỉ tương tác qua một wrapper/proxy; khi đó target gốc có thể **không bị thay cấu trúc thật**.

Bốn/five cấp độ này không mặc nhiên cùng được một engine hỗ trợ.

## 13. Mixin trong AOP thường là hợp đồng cộng implementation

Một thiết kế điển hình ở mức khái niệm:

~~~text
Target: Order
Interface: Taggable
Implementation source: TagMixinDelegate

Introduction policy:
  target types matching criterion
  expose Taggable
  delegate Taggable methods to a suitable state holder
~~~

Mục tiêu là tái sử dụng implementation Taggable cho nhiều target types mà không chép method vào từng class source.

Điểm cần chốt:

- Ai tạo delegate?
- Mỗi target một delegate hay nhiều target dùng chung?
- Ai hủy hoặc thu hồi state?
- Một target có được nhiều proxy đại diện không?
- Phương thức introduced có dispatch tới target hay delegate?
- Quan hệ interface có nhìn thấy từ object gốc, proxy hay class đã weave?
- Khi nhiều introductions trùng method signature, lỗi/precedence xử lý ra sao?

Nếu không làm rõ, một “mixin” có thể vô tình trộn trạng thái giữa các đối tượng hoặc gây memory leak.

## 14. Proxy không tự biến đổi class đích

Trong Java, Dynamic Proxy có thể tạo một object mới triển khai một tập interface xác định và chuyển lời gọi tới InvocationHandler.

Ví dụ:

~~~text
Original object: Order
Proxy object:    OrderView + Taggable
                     |
                     +-- OrderView calls delegate to Order
                     +-- Taggable calls use extra state
~~~

Ở đây **proxy** có thể implements Taggable, nhưng **Order gốc không tự implements Taggable**.

Điều đó khác với một loại class-level structural weaving thực sự khiến target class được nhận diện là implement interface mới.

Một runtime interceptor có thể dùng proxy làm “mặt ngoài đã mở rộng”, nhưng phải mô tả đúng giới hạn. Casting original Order thành Taggable có thể không hợp lệ; casting proxy thì có thể hợp lệ.

## 15. Ví dụ Java 17 số 2: proxy đưa thêm interface, nhưng không weave object gốc

Ví dụ này dùng Java Dynamic Proxy để **mô phỏng một mặt ngoài bổ sung hành vi Taggable**. Đây không phải một implementation structural AOP tự động.

Mỗi proxy có một LinkedHashSet riêng lưu tags. Hai proxy cùng ủy quyền tới một Order gốc vẫn có state tags khác nhau. Điều này minh họa vì sao **per-proxy state** không nên bị lẫn với **per-target state**.

Lưu file **ProxyMixinDemo.java**:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;

public class ProxyMixinDemo {
    interface OrderView {
        String code();
    }

    interface Taggable {
        void addTag(String tag);
        String tags();
    }

    static final class Order implements OrderView {
        private final String code;

        Order(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }

    static Object introduce(OrderView target) {
        Set<String> tags = new LinkedHashSet<>();

        InvocationHandler handler = (proxy, method, args) -> {
            Class<?> owner = method.getDeclaringClass();

            if (owner == Taggable.class) {
                if (method.getName().equals("addTag")) {
                    tags.add((String) args[0]);
                    return null;
                }
                if (method.getName().equals("tags")) {
                    return String.join(",", tags);
                }
            }

            if (owner == OrderView.class
                    && method.getName().equals("code")) {
                return target.code();
            }

            if (owner == Object.class) {
                return switch (method.getName()) {
                    case "toString" ->
                            "TaggedView(" + target.code() + ")";
                    case "hashCode" ->
                            System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default ->
                            throw new UnsupportedOperationException(
                                    method.toString());
                };
            }

            throw new UnsupportedOperationException(method.toString());
        };

        return Proxy.newProxyInstance(
                OrderView.class.getClassLoader(),
                new Class<?>[] {OrderView.class, Taggable.class},
                handler);
    }

    public static void main(String[] args) {
        Order original = new Order("ORD-7");
        Object proxyOne = introduce(original);
        Object proxyTwo = introduce(original);

        System.out.println("[original] taggable="
                + (((Object) original) instanceof Taggable));
        System.out.println("[proxy] taggable="
                + (proxyOne instanceof Taggable));
        System.out.println("[proxy] sameObject="
                + (proxyOne == proxyTwo));

        Taggable one = (Taggable) proxyOne;
        Taggable two = (Taggable) proxyTwo;
        one.addTag("hot");
        one.addTag("hot");
        one.addTag("review");

        System.out.println("[proxy-one] code="
                + ((OrderView) proxyOne).code());
        System.out.println("[proxy-one] tags=" + one.tags());
        System.out.println("[proxy-two] empty="
                + two.tags().isEmpty());
        System.out.println("[original] code=" + original.code());
    }
}
~~~

Biên dịch và chạy bằng JDK 17:

~~~bash
javac ProxyMixinDemo.java
java ProxyMixinDemo
~~~

**Stdout chính xác:**

~~~text
[original] taggable=false
[proxy] taggable=true
[proxy] sameObject=false
[proxy-one] code=ORD-7
[proxy-one] tags=hot,review
[proxy-two] empty=true
[original] code=ORD-7
~~~

### 15.1. Tại sao original không Taggable?

Order chỉ khai báo implements OrderView. Nó là một class Java được biên dịch bình thường và không hề được một weaver thay đổi. Interface Taggable xuất hiện **trên proxy object** do Proxy.newProxyInstance tạo.

### 15.2. Tại sao proxy Taggable?

newProxyInstance nhận hai interface: OrderView và Taggable. Proxy class tại runtime implements cả hai nên proxyOne instanceof Taggable đúng.

Điều này chỉ cho phép cast **proxy object**, không cung cấp một phép cast mới cho **original object**.

### 15.3. Tại sao proxyOne và proxyTwo có tags khác nhau?

Mỗi lần gọi introduce tạo một LinkedHashSet mới bên trong InvocationHandler closure. Hai proxies cùng trỏ tới target code ORD-7, nhưng state của Taggable nằm **per-proxy**, không nằm trong Order.

Nếu yêu cầu thực tế là tags gắn lâu dài với **target**, thiết kế per-proxy ở đây không đáp ứng: truy cập qua proxy khác không thấy tags đã lưu. Cần xác định key ownership và state lifetime ở cấp target.

### 15.4. Tại sao hot chỉ xuất hiện một lần?

LinkedHashSet giữ phần tử không trùng và theo thứ tự chèn. Sau khi addTag("hot") hai lần và "review" một lần, chuỗi nối là hot,review.

Đây là thuộc tính cấu trúc dữ liệu được dùng trong demo, không phải quy tắc tự có của một mixin.

### 15.5. Hạn chế cố ý của ví dụ

- Không có code weaving, inter-type compiler hay pointcut selection.
- Không thay đổi class Order gốc.
- Dynamic Proxy chỉ dựng mặt ngoài dựa trên interface.
- Object methods được xử lý bằng identity semantics của proxy trong demo, không phải quy tắc equality cho mọi application.
- LinkedHashSet trong handler chưa đồng bộ hóa, không an toàn nếu cùng proxy bị nhiều thread sửa đồng thời.
- Handler triển khai method name rõ cho ví dụ, không phải adapter phổ dụng cho mọi overloaded method.
- Không mô tả toàn bộ cơ chế checked exceptions của reflection/proxy; nếu handler dùng Method.invoke phải xử lý InvocationTargetException và hợp đồng throws thích hợp.

**Bài học:** proxy có thể cho trải nghiệm “object hỗ trợ thêm interface”, nhưng phải biết **object nào được mở rộng** và **state gắn với gì**.

## 16. Structural weaving thực sự khác proxy bọc

Giả sử một công cụ AOP có inter-type declarations thực sự được weave vào một class:

~~~text
Source declares Order only.
Weaver transforms type model / bytecode.
Resulting Order class can expose added interface/members
within the supported configuration.
~~~

Khi ấy có thể có sự khác biệt so với proxy:

- Code tương thích có thể xem **instance của Order class** là interface mới.
- Method introduction có thể được nhìn thấy trong class metadata hoặc reflection theo cách weaving.
- Không cần caller luôn cầm một proxy wrapper riêng.
- Semantics về lifecycle và field storage phụ thuộc những gì weaver đã bổ sung.

Nhưng không được nói mọi kỹ thuật weaving đều hoạt động như trên ở mọi phase. **Compile-time, post-compile và load-time weaving** có điểm thời gian, khả năng ảnh hưởng tới caller đã biên dịch, instrumentation scope và class loading khác nhau.

## 17. Thời điểm weaving ảnh hưởng khả năng nhìn thấy API

Có thể phân biệt ba nhóm khái niệm:

1. **Compile-time weaving:** mã nguồn và quy tắc aspect được kết hợp trong quá trình build thích hợp.
2. **Post-compile/binary weaving:** class artifacts đã biên dịch được biến đổi trước khi được sử dụng theo cơ chế.
3. **Load-time weaving:** class được biến đổi khi nạp, trước khi JVM định nghĩa class tương ứng nếu cơ chế hỗ trợ.

Một thay đổi structural có thể ảnh hưởng **kiểu nhìn thấy lúc compile** của caller. Nếu caller đã biên dịch mà không biết method introduced, nó không tự có source-level type checking và lời gọi trực tiếp tới method đó.

Ngược lại, bytecode được weave đúng thời điểm có thể cung cấp runtime interface membership, nhưng bản thân điều ấy chưa làm **source code cũ tự xuất hiện lời gọi method mới**.

Cần phân biệt **API visibility khi biên dịch**, **cấu trúc runtime đã nạp**, và **cơ chế truy cập thông qua proxy/adapter**.

## 18. Binary compatibility và class loading

Structural change có thể tương tác với:

- Các class đã được JVM nạp.
- Multiple class loaders có các bản định nghĩa class khác nhau.
- Module boundaries và access rules.
- Method descriptors/erasure.
- Những caller đã biên dịch với phiên bản cũ.
- Serialization contracts hoặc reflection metadata.
- Interface method dispatch và default-method rules.

Không tự cho rằng sau khi một class đã được JVM định nghĩa, hệ thống có thể tùy ý thêm method/interface cho class ấy ở mọi thời điểm. Các API redefine/retransform class trong JVM có **những giới hạn cụ thể**; cấu trúc có thể cần được quyết định trước khi class được định nghĩa.

Vì vậy structural AOP thường nhạy hơn advice thuần quan sát đối với build/deployment/class-loading environment.

## 19. Khả năng casting phải gắn với danh tính object

Ba tình huống:

~~~text
1) Subclass object:
   new PriorityOrder() instanceof Order  -> true

2) Proxy implements Taggable:
   proxy instanceof Taggable             -> true
   underlying original may not be Taggable

3) Woven target type implements Taggable:
   actual woven instance may be Taggable
   depending on weaving model and load scope
~~~

Không thể chỉ từ một log “introduced Taggable” mà kết luận **mọi reference tới object gốc** đều cast được thành Taggable. Phải biết nó chỉ là proxy mặt ngoài hay thay đổi class runtime thật.

Điều này quan trọng khi một phần code dùng proxy còn code khác giữ reference trực tiếp tới target.

## 20. Identity và equality khi dùng mixin/proxy

Với inheritance, một object instance có danh tính của chính nó. Với wrapper/proxy, có ít nhất **hai object**: original và proxy.

Nếu hai proxies cùng đại diện một target:

~~~text
proxyOne != proxyTwo (identity)
proxyOne delegates to target X
proxyTwo delegates to target X
~~~

Định nghĩa equals/hashCode của proxy có thể:

- Dựa trên identity của proxy.
- Dựa trên identity của target.
- Dựa trên business key của target.
- Dựa trên một contract riêng.

Mỗi lựa chọn có tác động tới Set/Map caching, context storage và mixin state. Không tự dùng equals của target làm khóa lưu state nếu hai target **equals bằng nhau về nghiệp vụ nhưng là hai object khác** mà state cần tách.

Nếu state nên gắn mỗi target instance, **object identity** và lifetime/GC cần được thiết kế rõ.

## 21. State ownership: per-instance, per-target, per-proxy, per-type

Một mixin có thể cần trạng thái:

| Mô hình state | Ý nghĩa | Rủi ro |
|---|---|---|
| **Per-target instance** | Mỗi target object một state | Cần lifecycle gắn target/GC |
| **Per-proxy** | Mỗi proxy view một state | Hai proxies của một target khác state |
| **Per-aspect singleton** | Shared state toàn scope | Nhiều target/request trộn dữ liệu |
| **Per-type** | Shared state theo class/type | Không chứa dữ liệu riêng instance |
| **Per-invocation** | State tạm cho một lời gọi | Không giữ lâu hơn lời gọi |
| **External keyed store** | State nằm trong map/delegate ngoài | Key/equality, retention, concurrency |

Không được đặt requestId hiện tại vào mixin instance sống lâu qua nhiều request chỉ vì nó gắn với một target. **Per-target không phải per-request**.

Một introduction tạo field trên target có thể có lifetime theo target; một delegate per-proxy có lifetime theo proxy. Chúng không hoán đổi được khi thiết kế dữ liệu.

## 22. Giữ state bên ngoài có thể gây memory leak

Nếu dùng một static map:

~~~text
Map<Target, MixinState>
~~~

và giữ strong references, target objects có thể không được thu hồi dù ứng dụng không còn dùng.

Một weak-key map có thể giảm retention trong một số thiết kế, nhưng cần hiểu semantics của equals/hashCode và việc **value không được giữ đường tham chiếu ngược tới key** nếu mục tiêu là GC. Ngoài ra weak references có thể bị thu hồi ở thời điểm không dự đoán trước.

Một identity map cho phép phân biệt object identity nhưng nếu nó giữ strong key vẫn có thể tạo leak. Không có một loại map nào tự giải quyết mọi yêu cầu identity, lifecycle và concurrency.

Nếu dùng field thực sự trong woven object, state thường có lifetime theo object. Nếu dùng proxy, state có thể theo proxy. **Hãy xác định nơi state thật sự nằm trước khi chọn cleanup.**

## 23. Concurrency của mixin state

Một mixin có thể được áp dụng lên shared target nhiều thread cùng gọi. Nếu introduced field hoặc delegate chứa HashMap, ArrayList hoặc LinkedHashSet mutable, không thể mặc định thread safe.

Ví dụ ProxyMixinDemo sử dụng LinkedHashSet không được synchronized. Dùng an toàn trong một demo tuần tự, không chứng minh rằng addTag đồng thời là an toàn.

Các lựa chọn gồm:

- Immutable value replacement với cơ chế publish phù hợp.
- Thread-safe collection nếu semantics đúng.
- Synchronization/lock có phạm vi hẹp.
- Atomic state khi thao tác đủ đơn giản.
- Per-invocation state nếu không cần chia sẻ.
- Ownership theo thread hoặc actor model phù hợp.

Không dùng synchronized mù quáng nếu cross-cutting code có thể re-enter target hoặc gọi advice khác, vì dễ tạo lock ordering/deadlock problem.

## 24. Method lookup, overriding và conflict resolution

Nếu một method được bổ sung trùng tên với method có sẵn, không đủ để biết “ai thắng” chỉ bằng tên.

Phải kiểm tra:

1. Chữ ký đầy đủ (name và parameter types, method descriptor khi xét bytecode).
2. Visibility/access của cả hai khai báo.
3. Static hay instance method.
4. Return-type covariance hợp lệ hay không.
5. Method abstract/default/concrete.
6. Superclass/interface precedence.
7. Phạm vi generic và type erasure.
8. Bridge methods do compiler tạo.
9. Rules về conflict của công cụ structural AOP.
10. Có method final hoặc ràng buộc khác không thể thay thế.

Ở Java, overriding một method đã có khác với **thêm method mới**. Một introduction không thể tự được coi là quyền override mọi method final hoặc ghi đè default resolution tùy ý.

## 25. Hai mixin đưa ra cùng một member

Giả sử có hai mixin:

~~~text
TagMixin:
    label()

AuditMixin:
    label()
~~~

Nếu cả hai được áp dụng cho cùng target và cùng signature, hệ thống có thể gặp xung đột.

Các cách xử lý **phụ thuộc ngôn ngữ/công cụ**:

- Báo lỗi compile/weave.
- Yêu cầu khai báo override/alias rõ.
- Định nghĩa precedence tường minh.
- Đổi API thành hai tên/namespace không trùng.
- Dùng delegate composition và forwarding có chủ đích.

Không giả định “aspect khai báo sau luôn thắng” hoặc “mixin gần target hơn luôn thắng”. Nếu thứ tự ảnh hưởng correctness, phải có bảo đảm thực tế và kiểm thử.

Một cách thiết kế tốt là **tránh làm mờ ownership**: hai trách nhiệm khác nhau nên có hai interface/method names rõ, thay vì tranh một method chung không mang ngữ nghĩa cụ thể.

## 26. Overload khác override

Hai method cùng tên nhưng khác tham số là **overload**. Một method trong subtype có cùng chữ ký phù hợp và ràng buộc return/access là **override**.

Mixin giới thiệu:

~~~text
label()
label(Locale)
~~~

có thể tạo overload. Nhưng thêm label() cùng signature với method có sẵn có thể là conflict hoặc một cơ chế override tùy luật.

AOP advice chọn method call bằng pointcut cũng có thể bị nhầm giữa các overload nếu biểu thức chọn chỉ dùng tên. Khi làm structural introduction, cần kiểm tra **hợp đồng chữ ký và type resolution**, không chỉ tên hiển thị trên log.

## 27. Generics và type erasure

Java generics có **type erasure**; nhiều thông tin tham số kiểu không tồn tại như các method descriptors runtime khác nhau.

Nếu introduction/mixin cố tạo các method chỉ khác nhau về generic type arguments nhưng bị erase thành cùng descriptor, có thể gặp name clash.

Ví dụ khái niệm:

~~~text
process(List<String>)
process(List<Integer>)
~~~

Hai chữ ký này không thể là hai overload Java hợp lệ chỉ dựa trên generic argument vì cùng erasure List.

Cần chú ý compiler-generated bridge methods trong các quan hệ override tổng quát. Một công cụ weaving xử lý bytecode phải nhất quán với những quy tắc này; không được hứa thêm mọi generic method tùy ý mà không xét collision.

## 28. Kế thừa và substitution

Một subtype hợp lệ về kiểu chưa chắc giữ đúng mọi giả định về **hành vi**. Thiết kế nên tôn trọng khả năng thay thế: nơi mong đợi supertype không nên bị phá invariants bởi subtype hoặc introduced behavior.

Ví dụ một introduction thêm Taggable cho Order chỉ nên thể hiện **một hợp đồng mà Order thật sự đáp ứng**. Nếu addTag được cho phép nhưng tags luôn trả dữ liệu từ proxy khác không đồng nhất, có thể vi phạm kỳ vọng của client.

Hợp đồng hành vi gồm:

- Điều kiện trước/sau.
- Bất biến của object.
- Ngữ nghĩa exceptions.
- State consistency.
- Thread safety.
- Equality/identity.
- Persistence/serialization nếu có.
- Khả năng dùng qua các reference khác nhau.

Structural typing nhìn có vẻ đúng chưa bảo đảm behavior đúng.

## 29. Mixin không phải đa kế thừa class

Một ngôn ngữ có thể hỗ trợ traits, modules hoặc multiple inheritance với các quy tắc linearization và conflict resolution riêng. Java class inheritance không có đa kế thừa class.

Interface default methods đem lại một dạng tái sử dụng code, nhưng:

- Không kế thừa multiple concrete class state.
- Có quy tắc xung đột default methods rõ ràng.
- Không tự giới thiệu interface vào target chưa khai báo.
- Không tự giải quyết state ownership.
- Không thay thế composition cho mọi nhu cầu.

Vì vậy gọi mixin là “đa kế thừa” chỉ chính xác khi nói về một mô hình ngôn ngữ cụ thể có semantics phù hợp; không phải một định nghĩa phổ quát.

## 30. AOP structural introduction và open classes

**Open class** là ý tưởng cho phép bổ sung phần khai báo hoặc hành vi của class từ ngoài module class ban đầu theo một cách có tổ chức.

Structural introduction trong AOP có thể được xem như một dạng hỗ trợ open classes trong những mô hình cho phép thêm members hoặc parent types.

Nhưng “open classes” là một khái niệm thiết kế rộng. Không phải một runtime monkey-patching system bất kỳ cũng có type checking hoặc weaving semantics giống structural AOP.

Điểm cần xét:

- Extension được giải quyết ở compile time hay runtime?
- Có thể nhìn thấy từ những module khác không?
- Có một định nghĩa class thống nhất hay nhiều wrapper views?
- Konflikt method được phát hiện lúc nào?
- Metadata reflection có phản ánh không?
- Binary compatibility được đảm bảo ở mức nào?

## 31. Thiết kế introduction tránh phụ thuộc quá nhiều vào target

Một structural aspect có thể phải chọn nhiều classes:

~~~text
Apply Taggable to:
  Order
  Invoice
  Shipment
~~~

Mục tiêu tái sử dụng là rõ, nhưng nếu selection quá rộng:

- Một class vốn đã implements Taggable có thể bị conflict.
- Một class mới vô tình nhận API không phù hợp domain.
- Một method introduced vô tình trùng tên với method mới của target sau refactor.
- Một version library mới thay hierarchy làm weaving không còn hợp lệ.
- Một target từ module khác không nằm trong phạm vi weave.

Nên chọn **tiêu chí áp dụng có ý nghĩa** và có test âm: class nào chắc chắn **không được nhận extension**.

## 32. Khi nào chọn kế thừa?

Kế thừa phù hợp khi:

- Có quan hệ subtype thực sự, rõ hợp đồng.
- Chúng ta kiểm soát source hierarchy.
- Một shared base class không cản trở nhu cầu mở rộng khác.
- Các invariants của superclass và subclass tương thích.
- Việc thiết kế mở rộng không làm lớp nền trở thành “god object”.

Không nên dùng kế thừa chỉ để gom logging, metrics, tagging và audit vào một base class nếu chúng không cùng bản chất domain.

Nếu một tính năng là optional/cross-cutting, composition hoặc mixin có thể biểu đạt tốt hơn. Nếu cần bổ sung cấu trúc vào code không muốn hoặc không thể sửa, structural AOP có thể phù hợp **khi công cụ và deployment có hỗ trợ chắc chắn**.

## 33. Khi nào chọn mixin/delegation?

Mixin/delegation phù hợp khi:

- Hành vi được tái sử dụng ở nhiều domain types.
- Không muốn đưa các types vào một concrete superclass chung.
- Có thể định nghĩa một interface rõ.
- State được lưu ở delegate hoặc owner hợp lý.
- Conflict resolution và thread safety dễ kiểm soát.
- Không cần thay bytecode hay runtime type model của class đích.

Một adapter/proxy có thể là giải pháp khi caller được phép nhận một wrapper object mới. Cần tài liệu hóa rằng **caller phải dùng wrapper**, và direct target reference có thể không có interface mở rộng.

## 34. Khi nào chọn structural extension bằng AOP?

Nên cân nhắc khi:

- Nhiều target classes cần cùng một hợp đồng ngoài concern cốt lõi.
- Source target không nên bị sửa hoặc nằm ở một module có khả năng mở rộng được kiểm soát.
- Mô hình AOP có **structural introductions được định nghĩa rõ**.
- Build/deployment có thể đảm bảo weaving đúng classes.
- Caller cần kiểu mới theo ngữ nghĩa thực, không chỉ intercept behavior.
- Các conflicts, constructor/lifetime/state và class loading đã được đánh giá.
- Có integration tests về cast, reflection, dispatch và concurrency.

Không nên dùng structural AOP chỉ vì muốn log một method: **behavioral advice** thường đúng mục tiêu và đơn giản hơn.

## 35. Thử nghiệm phải kiểm tra lớp nào thật sự được thay

Các ca test có giá trị:

| Ca | Kỳ vọng |
|---|---|
| Class đích được chọn | Nhận đúng extension theo mô hình |
| Class không được chọn | Không vô tình nhận interface/member |
| Proxy được gắn interface | Proxy cast được |
| Original target sau proxy wrapping | Không mặc định cast được |
| Woven class thật | Kiểm tra runtime interface membership khi được hỗ trợ |
| Target đã có method cùng chữ ký | Conflict/precedence đúng đặc tả |
| Hai mixin trùng member | Không có thứ tự ngầm nguy hiểm |
| Subclass override | Dispatch theo hợp đồng |
| Superclass method và interface default | Luật ngôn ngữ được giữ |
| Nhiều proxy cho cùng target | State ownership đúng |
| Hai targets equals nhưng khác identity | Không trộn state |
| Nhiều threads dùng shared target | Không race mixin state |
| Generic erasure collision | Không tạo class không hợp lệ |
| Reflection lookup | Chỉ khẳng định những method runtime thực có |
| Caller đã compile trước weaving | API visibility được xác nhận |
| Module/class loader boundary | Không nhầm hai lớp cùng tên khác loader |
| GC/release | Không giữ target qua static map ngoài ý định |
| Exception từ delegate | Không che lỗi gốc hoặc phá API |

Một unit test của delegate chưa đủ chứng minh structural weaving đã áp dụng lên đúng class ở môi trường chạy thật.

## 36. Lỗi phổ biến và cách sửa

**Lỗi 1 — Đồng nhất mixin với kế thừa.** Mixin là tổ chức tái sử dụng hành vi; kế thừa có subtype semantics cụ thể. Chọn đúng theo hợp đồng.

**Lỗi 2 — Cho rằng proxy đã sửa class gốc.** Proxy là object khác; phải phân biệt reference gốc và proxy.

**Lỗi 3 — Coi interface default method là state-bearing mixin.** Interface không có instance fields tùy ý; phải thiết kế state owner.

**Lỗi 4 — Đặt state của mọi target vào singleton aspect field.** Nhiều object có thể trộn trạng thái.

**Lỗi 5 — Sử dụng map strong-key lưu per-target state mãi.** Có thể giữ target sống lâu gây memory leak.

**Lỗi 6 — Mỗi proxy tự có state nhưng ứng dụng kỳ vọng per-target.** Hai wrapper nhìn thấy dữ liệu không nhất quán. Quy định scope rõ.

**Lỗi 7 — Bỏ qua thread safety.** Introduced field/delegate vẫn chịu concurrency như field thông thường.

**Lỗi 8 — Hai introductions trùng tên và dựa vào order ngẫu nhiên.** Dùng naming/override/precedence được bảo đảm hoặc báo conflict.

**Lỗi 9 — Cho rằng mọi AOP engine hỗ trợ thêm method/field.** Phải kiểm tra đúng join point và introduction capabilities.

**Lỗi 10 — Nhầm advice với introduction.** Chặn lời gọi không tạo thành viên kiểu mới.

**Lỗi 11 — Coi một cast thành công ở proxy chứng minh target cast được.** Test cả hai reference.

**Lỗi 12 — Không tính generic erasure và bridge methods.** Dễ gây collision/dispatch khác mong đợi.

**Lỗi 13 — Đổi hierarchy nhưng không xét binary compatibility.** Caller và class loader có thể dùng version khác.

**Lỗi 14 — Tạo mixin làm sai subtype invariants.** Interface membership về kiểu không đảm bảo behavior của contract.

**Lỗi 15 — Không kiểm thử negative selection.** Lớp ngoài phạm vi vẫn có thể bị mở rộng không mong muốn.

## 37. Quy trình phân tích một mở rộng cấu trúc

1. Xác định **hợp đồng mới** cần cung cấp.
2. Xác định yêu cầu là **behavioral advice** hay **structural API change**.
3. Xem kế thừa có phản ánh quan hệ domain thật không.
4. Xem mixin/delegate có thể tái sử dụng hành vi mà không sửa target không.
5. Nếu dùng AOP introduction, xác minh công cụ hỗ trợ interface/method/field/parent ở mức nào.
6. Xác định **object nhận interface**: target thật, woven instance hay proxy view.
7. Xác định **state owner**: per-instance, per-target, per-proxy hay per-type.
8. Xác định thứ tự method lookup, override và các conflicts.
9. Kiểm tra Java inheritance, generic erasure và access constraints.
10. Kiểm tra compile-time API visibility so với runtime structure.
11. Kiểm tra class loading, module boundaries và build/deployment.
12. Định nghĩa thread safety và release/GC policy.
13. Kiểm thử cast, reflection, delegate behavior và negative target selection.
14. Đo overhead và ảnh hưởng debug/stack traces.
15. Ghi tài liệu về điều kiện extension hoạt động và nơi không hoạt động.

Không bắt đầu bằng việc tìm một cú pháp “thêm interface nhanh nhất” khi chưa biết **kiểu và state có ý nghĩa gì ở runtime**.

## 38. Bài tập tự kiểm tra

### Câu 1. Mixin và superclass

Order và Shipment cần gắn nhãn nhưng thuộc hai hierarchy khác nhau. Có bắt buộc đưa chúng vào cùng một superclass TaggableBase không?

**Đáp án:** không. Có thể dùng interface + delegation, default methods khi đủ, hoặc structural introduction nếu cơ chế hỗ trợ.

### Câu 2. Proxy và cast

Một proxy implements Taggable, target gốc không. Có thể cast target gốc thành Taggable chỉ vì proxy của nó cast được không?

**Đáp án:** không. Proxy và target là hai object với type membership có thể khác nhau.

### Câu 3. Hai default methods

Hai interfaces cung cấp cùng default label(). Một class implements cả hai có thể dựa vào thứ tự implements để chọn method không?

**Đáp án:** không. Cần tuân thủ luật giải quyết conflict, ví dụ override rõ như chương trình đầu.

### Câu 4. Superclass và interface

BaseRecord có label concrete, DerivedRecord extends BaseRecord implements LeftLabel có label default. Method nào được dùng trong ví dụ?

**Đáp án:** method từ BaseRecord.

### Câu 5. Hai proxies, cùng target

ProxyOne và ProxyTwo được tạo từ một Order nhưng mỗi handler có state riêng. Tags thêm vào ProxyOne có tự xuất hiện ở ProxyTwo không?

**Đáp án:** không trong chương trình minh họa. State được cấp phát per-proxy.

### Câu 6. Per-target và per-request

Một target service sống lâu phục vụ nhiều requests. Có nên lưu currentRequestId vào introduced field per-target và xem đó là request scope không?

**Đáp án:** không. Per-target lifetime có thể dài hơn request, gây nhầm dữ liệu và concurrency issues.

### Câu 7. Advice và introduction

Một advice log trước calculateTotal. Order có tự trở thành Loggable interface không?

**Đáp án:** không. Behavioral advice không mặc định thay đổi cấu trúc kiểu.

### Câu 8. Generic overload

Có thể thêm hai overload process(List<String>) và process(List<Integer>) chỉ dựa vào generic arguments trong Java không?

**Đáp án:** không, vì type erasure khiến chữ ký xung đột.

### Câu 9. Field introduction và GC

Một static map có strong keys là target objects và chứa state mixin. Rủi ro là gì?

**Đáp án:** map có thể giữ targets sống lâu hơn dự kiến và gây memory retention.

### Câu 10. Class loading

Một app đã load class trước khi bộ biến đổi có cơ hội thêm interface. Có thể tự giả định mọi object đã tạo sẽ nhận interface mới không?

**Đáp án:** không. Cần xem weaving phase và giới hạn JVM/class redefinition.

### Câu 11. Thêm method có sẵn

Một introduction khai báo method cùng chữ ký với method của target. Có chắc introduction sẽ override theo ý tác giả không?

**Đáp án:** không. Có thể conflict hoặc áp dụng một precedence/dispatch riêng tùy công cụ; phải kiểm chứng đặc tả.

### Câu 12. Chọn kỹ thuật

Cần gắn logging vào mọi lời gọi pay nhưng không thay public API. Có nhất thiết dùng structural introduction không?

**Đáp án:** không. Behavioral advice thường phù hợp hơn.

### Câu 13. Trait và đa kế thừa

Có thể gọi mọi mixin trong Java là multiple concrete class inheritance không?

**Đáp án:** không. Java không hỗ trợ một class extends hai concrete superclasses; mixin-like behavior qua interfaces/delegates có semantics khác.

### Câu 14. Runtime type identity

Hai class cùng tên được nạp bởi hai class loaders khác nhau có mặc định là cùng Java runtime type không?

**Đáp án:** không. Class loader identity là một phần của runtime type identity; weaving/casting cần đúng loader scope.

### Câu 15. Interface contract

Một target được introduce Taggable nhưng implementation addTag và tags không nhất quán. Thành công về instanceof có đủ xác nhận thiết kế đúng không?

**Đáp án:** không. Hợp đồng hành vi, state consistency và khả năng thay thế vẫn phải được kiểm thử.

## 39. Checklist cuối cùng

- [ ] Phân biệt rõ inheritance, mixin, introduction và delegation.
- [ ] Biết object nào thật sự mang interface mới.
- [ ] Không nhầm proxy surface với class-level weaving.
- [ ] Có hợp đồng kiểu và hành vi xác định.
- [ ] Kiểm tra giới hạn một superclass trực tiếp của Java class.
- [ ] Có quy tắc giải quyết trùng method/field.
- [ ] Xác định state lifetime per-target/per-proxy/per-instance.
- [ ] Không dùng singleton state cho dữ liệu riêng target hoặc request.
- [ ] Đã xem xét concurrency và GC retention.
- [ ] Đã xét binary compatibility và load-time scope.
- [ ] Không giả định mọi công cụ hỗ trợ mọi introduction.
- [ ] Có test dương/âm về target selection.
- [ ] Có test casting trên target và proxy khi thích hợp.
- [ ] Có test default-method conflicts, overriding và generics erasure.
- [ ] Có integration test với build/runtime thật.
- [ ] Tài liệu hóa điều kiện và giới hạn để người dùng API không hiểu nhầm.

## 40. Tổng kết

**Kế thừa** diễn đạt quan hệ subtype và tái sử dụng theo hệ phân cấp. **Mixin** diễn đạt tái sử dụng một nhóm hành vi, có thể hiện thực bằng default methods, delegation, traits hoặc các cơ chế ngôn ngữ khác. **Mở rộng cấu trúc trong AOP** dùng một cơ chế introduction tương ứng để đưa interface, member hoặc quan hệ kiểu mới vào mô hình của những lớp đích, nếu công cụ hỗ trợ.

Những phân biệt phải nhớ:

- **Thêm advice không đồng nghĩa thêm API cấu trúc.**
- **Proxy implementing interface không làm class gốc tự implements interface.**
- **Mixin không mặc định là multiple concrete class inheritance.**
- **Java default methods có quy tắc conflict/dispatch rõ, không do thứ tự ghi tùy ý.**
- **State ownership quyết định dữ liệu được chia sẻ per-target hay per-proxy.**
- **Class loading/weaving phase quyết định khả năng hiện diện của structural members.**
- **Casting, reflection, method resolution, generics và binary compatibility phải được kiểm chứng theo runtime thật.**
- **Một cấu trúc hợp lệ về kiểu vẫn phải bảo toàn hợp đồng hành vi và thread safety.**

**Câu hỏi thiết kế quyết định:** *Chúng ta muốn một quan hệ subtype thật, một nhóm hành vi có thể tái sử dụng hay một API mới được đưa vào những lớp sẵn có từ bên ngoài — và caller đang tương tác với original object, proxy view hay một class đã được weave?*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-007">Cảnh báo hoặc lỗi ở bước xây dựng để kiểm tra quy tắc kiến trúc</a>

<details>
<summary>Xem chi tiết</summary>

# Cảnh báo hoặc lỗi ở bước xây dựng để kiểm tra quy tắc kiến trúc trong AOP

Trong **Aspect-Oriented Programming (AOP – lập trình hướng khía cạnh)**, aspect không chỉ dùng advice để xử lý lời gọi ở runtime. Một số mô hình aspect kết hợp vào chương trình ở **compile-time hoặc post-compile weaving**, có khả năng kiểm tra những đặc điểm **cấu trúc mã và điểm nối tĩnh (static join point shadows)** ngay trong quá trình xây dựng. Khi một quy tắc kiến trúc bị vi phạm, công cụ có thể phát **warning** để người viết xem xét, hoặc **error** để từ chối tạo artifact.

Ví dụ, tổ chức phần mềm quy định **mã domain không được phụ thuộc trực tiếp vào implementation của tầng infrastructure**; hoặc một API legacy chỉ được sử dụng trong giai đoạn chuyển đổi. Thay vì chờ request thật chạy rồi quan sát log, một bộ kiểm tra ở bước build có thể phát hiện các **dependency type, chữ ký API, một số call site hoặc thay đổi cấu trúc type** theo khả năng phân tích của nó.

Bài thuộc nhóm **Thay đổi cấu trúc**: khi aspect có khả năng **introduction/inter-type declaration**, nó có thể bổ sung interface, member hoặc quan hệ type. Quy tắc kiến trúc cần mô tả **cấu trúc mong muốn trước/sau thay đổi**, không chỉ tập phương thức được advice chặn. Trọng tâm bài là thiết kế rule, chọn severity, định vị lỗi, phối hợp với weaving, xây bộ kiểm thử và đưa kết quả vào CI.

**Phạm vi:** kiến thức AOP cơ bản và nâng cao **nói chung**. Không tồn tại một cú pháp warning/error hay loại static join point duy nhất cho mọi công cụ AOP. Ví dụ Java 17 ở dưới dùng **Java Compiler API và annotation processor** để minh họa rõ cách tạo diagnostic thật từ source; processor này **không phải một bytecode weaver**, nhưng là cơ chế bổ trợ hữu ích khi kiến trúc cần kiểm tra type-level structure vượt ngoài khả năng của một pointcut.

## 1. Nền tảng: cấu trúc chương trình và thời điểm kiểm tra

- **Aspect:** tập hợp concern cắt ngang, pointcut/advice, đôi khi có khai báo thay đổi cấu trúc.
- **Join point:** điểm chương trình có thể can thiệp; ví dụ method call, method execution nếu mô hình hỗ trợ.
- **Pointcut:** điều kiện chọn tập join point; có phần xác định tĩnh và có phần phụ thuộc runtime.
- **Advice:** hành vi before/after/around hoặc dạng khác chạy tại join point được chọn.
- **Weaving:** kết hợp aspect vào source, compiled code, lúc load class hoặc qua proxy.
- **Introduction/inter-type declaration:** đưa member, interface hoặc một quan hệ cấu trúc vào type qua một cơ chế AOP có hỗ trợ.
- **Architecture invariant:** tính chất mà cấu trúc phần mềm phải bảo toàn, ví dụ chiều phụ thuộc giữa layer hoặc loại API được phép xuất hiện.
- **Build diagnostic:** warning/error được phát bởi compiler, weaver, processor hoặc analyzer lúc xây dựng.

Với nhiều aspect, cần phân biệt:
1. **Chọn điểm:** pointcut có thể chọn call site hoặc execution point nào.
2. **Thay đổi cấu trúc:** introduction có thể tạo một member/type relation mới.
3. **Kiểm chứng:** rule đọc source, type model, bytecode hoặc effective post-weave structure.
4. **Thi hành:** advice runtime vẫn cần khi điều kiện chỉ xác định được lúc chạy.

### 1.1 Static checking khác runtime advice

| Tiêu chí | Cảnh báo/lỗi khi build | Advice runtime |
| --- | --- | --- |
| Khi nào | Phân tích source, type hoặc bytecode | Khi invocation thật xảy ra |
| Có cần request? | Không | Có |
| Bằng chứng | Type dependency, chữ ký, static call, artifact structure | Args, identity, target, context, control flow |
| Kết quả | Diagnostic hoặc build failure | Log, đo đạc, trả kết quả hoặc exception |
| Giới hạn | Không biết đủ giá trị động, reflection, implementation nạp muộn | Có thể bị bypass nếu lời gọi không qua interception boundary |
| Chủ thể xử lý | Developer, build và CI | Caller, service, người vận hành |

Một lỗi kiến trúc tĩnh có thể được bắt trước khi chương trình chạy. Nhưng **PASS ở build không chứng minh mọi hành vi runtime được bảo vệ**. Ví dụ annotation “yêu cầu kiểm tra quyền” xuất hiện trong source không tự chứng minh người dùng thực sự có quyền khi một request chạy.

### 1.2 Các pha có thể xuất hiện quy tắc

~~~text
Source + architecture rules
          |
          v
Parse / symbol and type resolution
          |
          v
Static architecture checks
          |             |
       WARNING         ERROR
          |             |
          v             +--> build FAIL
Weaving / introductions (if supported)
          |
          v
Post-weave structural checks
          |
          v
Tests -> Package / release
~~~

Đây là **một pipeline khái niệm**. Không mặc định mọi công cụ thực hiện đúng thứ tự ấy: có hệ thống tích hợp weaving vào compiler, có hệ thống biến đổi binary sau compile hoặc chỉ runtime proxy. Một invariant nhắm tới **artifact sau weaving** cần được kiểm trên effective structure đúng pha; kiểm source trước introductions chưa đủ.

## 2. Warning và error cần phản ánh chính sách

### 2.1 Warning – cần review nhưng không tự cấm artifact

Warning hợp lý khi:
- API legacy còn được phép nhưng đang trong lộ trình chuyển đổi.
- Một type có dấu hiệu coupling hoặc naming convention đáng nghi.
- Một method có metadata chưa đầy đủ, cần đánh giá thủ công.
- Một call site có nguy cơ đi qua API cũ nhưng chưa chắc vi phạm.
- Một structural introduction có thể tạo điểm cần review trong bản tiếp theo.

Warning tốt có **mã rule, location, chứng cứ và gợi ý sửa**; warning mơ hồ dễ bị lờ đi khi số lượng lớn.

### 2.2 Error – cấm build khi bất biến bị phá

Error phù hợp khi policy đã có đủ bằng chứng:
- Domain tuyên bố field phụ thuộc concrete infrastructure type bị cấm.
- Public API để lộ một internal type bị cấm.
- Một introduction tạo type/member không hợp lệ theo quy tắc của compiler/weaver.
- Module dependency graph chứa cạnh hoặc cycle bị policy cấm.
- Một static method-call join point từ vùng domain đến API bị cấm, nếu analyzer đủ khả năng xác định.

Cần phân biệt **lỗi cấu trúc thật** với một nghi vấn runtime không chứng minh được từ source. Nếu rule có false positive lớn, không tự động nâng nó thành error mà chưa định nghĩa chính sách ngoại lệ.

### 2.3 Đưa warning thành error theo lộ trình

~~~text
Discover baseline -> Warn -> Fix new violations -> Reduce debt -> Enforce error
~~~

Lộ trình thực tế:
1. Thu thập kết quả rule trên clean build để có baseline.
2. Gỡ cảnh báo không chính xác, hoàn thiện matcher và tài liệu.
3. Chặn hoặc yêu cầu review đối với vi phạm **mới**.
4. Thu hẹp các ngoại lệ có owner và thời hạn.
5. Nâng thành error khi một invariant đã đủ rõ và áp dụng đồng nhất.

**Warning-as-error** là chính sách riêng. Trình biên dịch Java có tùy chọn **-Werror** trong hoàn cảnh phù hợp để warning làm build fail; các analyzer/weaver khác có cơ chế riêng. Không giả định mọi warning tự động dừng build.

## 3. Viết một architecture rule có thể kiểm chứng

Một rule nên có:

| Thành phần | Ví dụ |
| --- | --- |
| ID | ARC001 |
| Mục tiêu | Domain không phụ thuộc concrete infrastructure |
| Scope | Những type thuộc domain package |
| Điều kiện | Field khai báo kiểu thuộc infrastructure package |
| Severity | ERROR |
| Vị trí | Field gây phụ thuộc |
| Thông báo | domain depends on infrastructure |
| Hướng sửa | Đảo dependency qua abstraction thích hợp |
| Ngoại lệ | Có giới hạn, lý do và owner |
| Test | Một fixture PASS, một fixture FAIL |

Ví dụ **pseudo-rule**, không phải một ngôn ngữ cấu hình chung:

~~~text
ARCH-RULE ARC001:
    scope = types in demo.domain
    if declared field type belongs to demo.infra:
        ERROR "depend on an appropriate port instead"

ARCH-RULE ARC002:
    scope = types in demo.domain
    if declared field type belongs to demo.legacy:
        WARNING "review and migrate legacy dependency"
~~~

**Điều kiện phải hẹp và chính xác.** Hai rule trên chỉ kiểm **field type**. Chúng chưa chặn method call, constructor parameter, method return, generic type argument hoặc reflection. Không viết tài liệu “cấm mọi cách sử dụng infrastructure” khi implementation chỉ kiểm một dạng tham chiếu.

### 3.1 Rule call site phải quan sát đúng điểm gọi

Giả sử source có:

~~~text
domain.OrderService.submit()
         |
         +---- call infra.SqlRepository.persist()
                              ^
                              prohibited call edge
~~~

Nếu invariant cấm **dependency từ caller domain sang callee infra**, một rule về **method call** có thể hữu ích. Chỉ chọn **method execution** của persist không đủ kết luận **caller thuộc layer nào**.

Một số ngôn ngữ AOP cho phép khai báo warning/error khi một **static pointcut** match call site có thể xác định tại build, ví dụ ý tưởng:

~~~text
aspect ArchitectureGuard:
    when domain-layer call site targets an infrastructure API:
        declare ERROR "[ARC001] Direct call prohibited"

    when a visible call uses legacy API:
        declare WARNING "[ARC002] Legacy call requires review"
~~~

**Đây là giả mã**, không phải cú pháp tương thích mọi trình biên dịch AOP. Static call matching phụ thuộc call-join-point semantics, owner type, symbol resolution, classpath và các quy tắc generated/bridge methods.

### 3.2 Rule type-level không nhất thiết là pointcut

Muốn kiểm các yêu cầu như:
- Một class phải implement đúng interface.
- Public API không được expose implementation type.
- Class thuộc package A không được khai báo field thuộc package B.
- Một type không được có member bị trùng do introduction.

Ta có thể dùng **type checker, annotation processor, AST analyzer hoặc bytecode scanner** thay vì ép mọi thứ thành một pointcut. Architecture governance có thể là sự phối hợp nhiều loại công cụ, miễn chứng cứ và thời điểm chạy rõ ràng.

## 4. Ví dụ Java 17 có diagnostic thật khi biên dịch

Chương trình **BuildArchitectureRulesDemo.java** chạy một compilation task trong bộ nhớ bằng **Java Compiler API**. Một **annotation processor** duyệt field của type demo.domain.OrderService và:
- Cho phép field kiểu **demo.port.Store**.
- Phát **ERROR ARC001** nếu field kiểu **demo.infra.***.
- Phát **WARNING ARC002** nếu field kiểu **demo.legacy.***.

Ví dụ này **không là một Aspect compiler/weaver**; nó dạy cơ chế build-time diagnostics mà một hệ thống kiểm soát quy tắc kiến trúc có thể áp dụng bên cạnh AOP. Với rule kiểm **static call join point**, cần analyzer thích hợp hơn cho method body hoặc bytecode.

Lưu nguyên file sau với tên **BuildArchitectureRulesDemo.java**:

~~~java
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class BuildArchitectureRulesDemo {
    static final class Source extends SimpleJavaFileObject {
        final String source;

        Source(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/')
                    + JavaFileObject.Kind.SOURCE.extension),
                  JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreErrors) {
            return source;
        }
    }

    @SupportedAnnotationTypes("*")
    @SupportedSourceVersion(SourceVersion.RELEASE_17)
    public static final class ArchitectureProcessor
            extends AbstractProcessor {
        @Override
        public boolean process(
                Set<? extends TypeElement> annotations,
                RoundEnvironment round) {
            if (round.processingOver()) return false;

            for (Element type : round.getRootElements()) {
                if (!(type instanceof TypeElement t)
                        || !t.getQualifiedName().contentEquals(
                                "demo.domain.OrderService")) {
                    continue;
                }

                for (Element field : t.getEnclosedElements()) {
                    if (field.getKind() != ElementKind.FIELD) continue;
                    String usedType = field.asType().toString();

                    if (usedType.startsWith("demo.infra.")) {
                        processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "ARC001 domain depends on infrastructure: "
                                    + usedType,
                            field);
                    } else if (usedType.startsWith("demo.legacy.")) {
                        processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.WARNING,
                            "ARC002 review legacy dependency: "
                                    + usedType,
                            field);
                    }
                }
            }
            return false;
        }
    }

    static void check(String scenario, boolean bad) {
        List<JavaFileObject> files = new ArrayList<>();
        files.add(new Source("demo.port.Store",
                "package demo.port; public interface Store {}"));
        files.add(new Source("demo.infra.SqlStore",
                "package demo.infra; public class SqlStore {}"));
        files.add(new Source("demo.legacy.LegacyClient",
                "package demo.legacy; public class LegacyClient {}"));

        String fields = bad
                ? "demo.infra.SqlStore sql; "
                  + "demo.legacy.LegacyClient legacy;"
                : "demo.port.Store store;";
        files.add(new Source("demo.domain.OrderService",
                "package demo.domain; public class OrderService { "
                + fields + " }"));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK required");
        }

        DiagnosticCollector<JavaFileObject> collector =
                new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, null, collector,
                List.of("-proc:only", "--release", "17"),
                null, files);
        task.setProcessors(List.of(new ArchitectureProcessor()));

        boolean successful = Boolean.TRUE.equals(task.call());
        System.out.println("CASE " + scenario);

        collector.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR
                        || d.getKind() == Diagnostic.Kind.WARNING)
                .map(d -> d.getKind().name() + " "
                        + d.getMessage(Locale.ROOT))
                .sorted(Comparator.naturalOrder())
                .forEach(System.out::println);

        System.out.println(
                "RESULT " + (successful ? "PASS" : "FAIL"));
    }

    public static void main(String[] args) {
        check("allowed", false);
        check("violation", true);
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac --release 17 BuildArchitectureRulesDemo.java
java BuildArchitectureRulesDemo
~~~

Output đã đối chiếu bằng việc biên dịch và chạy mã Java:

~~~text
CASE allowed
RESULT PASS
CASE violation
ERROR ARC001 domain depends on infrastructure: demo.infra.SqlStore
WARNING ARC002 review legacy dependency: demo.legacy.LegacyClient
RESULT FAIL
~~~

### 4.1 Vì sao allowed PASS?

Type **demo.domain.OrderService** chỉ có field **demo.port.Store**. Đây là một abstraction được cho phép theo policy của ví dụ. Processor không phát diagnostic, nên compilation task thành công.

**Giới hạn:** việc phụ thuộc một interface không tự bảo đảm architecture hoàn hảo; nó chỉ **không vi phạm** các rule đã được định nghĩa.

### 4.2 Vì sao violation có cả error và warning?

Hai field trong phiên bản vi phạm được kiểm bởi hai điều kiện:
- Concrete dependency **demo.infra.SqlStore** → ERROR ARC001.
- Legacy dependency **demo.legacy.LegacyClient** → WARNING ARC002.

Một error khiến compilation task trả thất bại. Warning vẫn được thu nhận và hiển thị, không tự làm build fail khi không có error hoặc chính sách warning-as-error.

Diagnostic được sắp xếp khi hiển thị nhằm có **stdout xác định**. Bản thân builder/analyzer production nên định nghĩa một cách ghi kết quả có thể tái lập.

### 4.3 Vì sao dùng -proc:only?

Tùy chọn **-proc:only** chạy annotation processing mà không sinh artifact class output của một compilation thông thường. Trong demo, điều này phù hợp để phân biệt **bước kiểm tra** với **bước đóng gói**. Build production có thể đặt checker trong compiler thật hoặc một build step riêng; không bắt buộc dùng tùy chọn này cho mọi dự án.

### 4.4 Phạm vi giới hạn của processor

Processor này cố ý chỉ xét:
- **Một type duy nhất**, đúng tên demo.domain.OrderService.
- **Những field** được khai báo trên type.
- **Chuỗi tên kiểu field** bắt đầu bằng demo.infra. hoặc demo.legacy.

Nó **không** kiểm:
- Constructor/method parameter hay return types.
- Method calls trong thân hàm.
- Generics với loại bị cấm nằm trong type arguments.
- Import không được dùng, reflection, dynamic class loading.
- Toàn bộ class thuộc domain package khác.
- Type hierarchy sau một AOP structural introduction.
- Runtime authorization, cache bypass hoặc async behavior.

Bởi vậy **PASS của demo không phải chứng chỉ kiến trúc toàn ứng dụng**. Một analyzer production phải duyệt đầy đủ các kinds đã cam kết và kiểm đúng representation.

## 5. Cảnh báo/lỗi khi structural introductions thay đổi type

Một ngôn ngữ AOP có thể **thêm interface hoặc method** vào type thông qua introduction. Ví dụ khái niệm:

~~~text
Before weaving:
  class Ledger { ... }

Structural introduction:
  add interface Auditable to Ledger
  add auditKey() member to Ledger

After weaving:
  Ledger implements Auditable
  Ledger has auditKey()
~~~

Cần xác định quy tắc:
1. Có cho phép một aspect thay public API của Ledger không?
2. Nếu Ledger đã có method auditKey với chữ ký không tương thích, compiler báo gì?
3. Type được giới thiệu có thuộc đúng architectural layer không?
4. Member mới có tạo direct dependency từ domain sang infra không?
5. Rule kiểm **source trước weave** hay **effective structure sau weave**?
6. Pointcut khác có chọn member mới không, và có tạo recursion/advice overlap không?

**Không có quy tắc cố định mọi introduction đều được phép hay bị cấm.** Lỗi/cảnh báo phải theo invariant được chủ sở hữu kiến trúc định nghĩa.

### 5.1 Pre-weave vs post-weave là hai câu hỏi khác

| Pha | Ví dụ invariant | Hạn chế |
| --- | --- | --- |
| Trước weave | Source domain không khai báo direct infra field | Chưa nhìn thấy member được đưa thêm |
| Sau weave | Effective Ledger thật sự implement Auditable | Phụ thuộc pipeline và metadata sau weaving |
| Sau compile/classfile | Classfile không có dependency cấm | Có thể mất thông tin source-level hữu ích |
| Runtime | Audit thật sự phát event ở invocation phù hợp | Không thể suy từ chỉ một type declaration |

Nếu checker chạy trước introduction nhưng invariant nói về **artifact sau weaving**, kết quả PASS chỉ là **kiểm một pha**, không chứng minh trạng thái cuối.

### 5.2 Thứ tự nhiều transformation

Một weaver A có thể đưa thêm method mà weaver B dùng để lựa chọn join point; đổi thứ tự xử lý có thể thay tập điểm B nhìn thấy. Khái niệm:

~~~text
W2(W1(Program)) may differ from W1(W2(Program))
~~~

Do đó cần quản lý build order, version, classpath, mã sinh và quy tắc reweaving theo công cụ thật. Đừng mặc định các phép biến đổi giao hoán hoặc cùng artifact được weave lại nhiều lần mà không thay hành vi.

## 6. Chất lượng rule: loại false positive và false negative

**False positive:** rule báo vi phạm khi cấu trúc hợp lệ theo policy.

**False negative:** một cấu trúc bị cấm không được báo.

Nguyên nhân thường gặp:
- So khớp tên class bằng substring không resolve symbol.
- Nhầm package/tên trùng trong các module khác nhau.
- Bỏ sót constructor parameters hoặc API return types.
- Bỏ sót nested generic types và arrays.
- Không phân biệt method call và method execution.
- Bỏ sót code generation ở pha sau.
- Không nhìn thấy reflection/dynamic loading.
- Scanner trên source chưa biết hierarchy được introduction thay đổi.
- Cache của incremental build giữ metadata cũ.
- Rule bị disable/suppress ngoài ý định.

**Kiểm tra bằng symbol/type model** thường đáng tin hơn tìm chuỗi trong source comment. Nhưng ngay cả type model cũng cần quyết định rõ **kiểu khai báo** khác gì **implementation runtime** khi kiểm dependency.

### 6.1 Mô tả đúng độ phủ

Một architecture report nên cho biết:
- Tập source/module được kiểm.
- Số type/call sites được quan sát.
- Rule IDs nào enabled.
- Những loại dependency có kiểm và không kiểm.
- Số warning/error/suppression theo ID.
- Analyzer chạy pre-weave hay post-weave.
- Classpath, generated sources và build variant.
- Có clean/incremental validation hay không.

Nếu checker không chạy, **không thấy lỗi không phải PASS**. CI nên fail nếu checker bắt buộc bị bỏ qua do plugin bị tắt hoặc task không được thực thi.

## 7. Fixture kiểm thử: đúng phải pass, sai phải fail

Một rule tốt phải có test theo cả hai hướng.

| Test | Expected |
| --- | --- |
| Domain field tham chiếu port được phép | PASS |
| Domain field tham chiếu concrete infra | ERROR ARC001, build FAIL |
| Domain field tham chiếu legacy API | WARNING ARC002 |
| Một type hợp lệ cùng simple name nhưng khác package | Không báo nhầm |
| Domain constructor nhận infra khi rule đã bao phủ constructor | ERROR của rule mở rộng |
| Domain public API trả infra type nếu cấm | ERROR |
| Static call từ domain tới method infra nếu rule call hỗ trợ | ERROR |
| Reflection string chỉ chứa chữ SqlStore | Không báo nhầm như type reference |
| Introduction hợp lệ | PASS post-weave |
| Introduction tạo member conflict | ERROR nếu compiler/weaver quy định |
| Source sau khi sửa dependency bị cấm | PASS ở clean và incremental |
| Processor không chạy dù bắt buộc | CI FAIL về cấu hình kiểm tra |

### 7.1 Kiểm tra boundary có giá trị cao

- **Overload:** các method cùng tên nhưng parameter types khác nhau.
- **Generic erasure:** bridge methods có thể xuất hiện trong bytecode.
- **Inner/nested types:** package, visibility và target name resolution.
- **Interface call:** một call qua interface có thể dẫn tới concrete runtime implementation khác.
- **Default method:** rules static phải rõ điểm call/execution được kiểm.
- **Generated source:** thứ tự tạo mã và phân tích có thể khác.
- **Reflection:** một type có thể được load không có direct dependency được scanner phát hiện.
- **Multi-module:** analyzer có thực sự biết dependency graph đầy đủ không?

Không viết một rule “forbid ALL runtime calls to module X” rồi chỉ scan source text cho tên module X. Hãy giới hạn câu chữ theo bằng chứng có thể nhận diện và bổ sung runtime controls khi cần.

## 8. Tổ chức CI và xử lý kết quả

Một pipeline chặt chẽ có thể có:

~~~text
compile inputs
  |
  +--> source/type architecture checks
  |
  +--> aspect weaving / structural checks
  |
  +--> bytecode consistency checks
  |
  +--> unit/integration/architecture regression tests
  |
  +--> package artifact only if required checks pass
~~~

### 8.1 Chính sách thực thi

- **ERROR:** bất biến bắt buộc bị phá → không phát hành.
- **WARNING:** hiển thị, triage, có owner; fail CI chỉ khi policy yêu cầu.
- **CHECKER FAILED:** lỗi tool/config khác rule violation; không im lặng coi PASS.
- **CHECKER SKIPPED:** không được coi như đã kiểm.
- **SUPPRESSION:** phải có lý do, scope cụ thể, thời hạn; không tắt toàn bộ rule không kiểm soát.

### 8.2 Diagnostic hữu ích hơn một thông báo chung

Một thông báo đề xuất:

~~~text
ERROR ARC001
At: demo.domain.OrderService.sql
Observed: demo.infra.SqlStore
Invariant: domain must not directly reference infrastructure types
Suggestion: introduce a domain-owned port and wire adapter elsewhere
~~~

Đây **không phải stdout của demo**, mà là mẫu thông báo phát triển. Các rule cần ghi **vị trí thật** khi compiler có metadata vị trí nguồn; sử dụng phần tử field ở processor giúp compiler gắn diagnostic đến thành phần liên quan.

### 8.3 Warning baseline và suppression

Nếu hệ thống đã có nhiều vi phạm legacy:
- Chỉ cho baseline những trường hợp được phê duyệt.
- Ngăn baseline tăng tự do.
- Yêu cầu lý do và hạn chót của suppression.
- Không suppress lỗi bắt buộc nếu policy không cho phép.
- Có báo cáo theo thời gian để giảm nợ kiến trúc.
- Khi rule đổi semantics, chạy lại fixture và baseline thay vì tin danh sách suppress cũ.

## 9. Clean build so với incremental build

Một source change có thể làm thay đổi type hierarchy hoặc call edges ở nhiều module. Nếu build incremental không invalidates các kết quả liên quan, checker có thể **không báo lỗi mới**, trong khi clean build lại FAIL.

Kiểm thử pipeline:
1. Clean build với code hợp lệ → PASS.
2. Thêm dependency bị cấm → ARC001 ERROR, FAIL.
3. Gỡ dependency → PASS.
4. Thay interface hoặc structural introduction dùng chung → các consumer liên quan phải được recheck.
5. Chạy cùng chuỗi trên incremental build → kết quả rule nhất quán.
6. Xác minh artifact cuối đã dùng đúng code và thứ tự weaver.

Nếu một rule kiểm **post-weave member** nhưng CI chỉ chạy pre-weave task, đó là lỗi cấu hình kiểm tra, không phải một bằng chứng kiến trúc hợp lệ.

### 9.1 Dependency và precedence cycles

Một graph architecture có thể có cạnh module A→B, B→C, C→A. Nếu policy yêu cầu DAG, analyzer cần phát hiện cycle và báo đường cụ thể.

Nhưng cần phân biệt:
- **Module dependency cycle:** vòng ở type/package/module graph.
- **Advice precedence cycle:** ràng buộc A ngoài B, B ngoài C, C ngoài A; không có thứ tự hợp lệ.
- **Runtime recursion:** advice gọi helper bị chính pointcut chọn lại.

Ba loại này không thể được xử lý chỉ bằng một phép sort đơn giản trên danh sách aspect. Một rule build-time phải nêu rõ **loại cạnh** nó thực sự phân tích.

## 10. Phối hợp với kiểm soát runtime

Một kiến trúc sạch vẫn có thể xuất hiện lỗi:
- Direct target call bypass proxy advice.
- Around advice ngoài short-circuit và bỏ qua kiểm tra quyền bên trong.
- Async completion xảy ra sau method return nên after-returning chạy sớm.
- Cache trả dữ liệu của tenant khác do cache key thiếu thành phần.
- Reflection hoặc dynamic classloading kích hoạt implementation chưa được kiểm.
- Retry lặp lại side effect không idempotent.

Các trường hợp này cần **runtime authorization, validation, tracing hoặc kiểm thử** đúng cách; static warning/error không thể thay thế. Ngược lại, runtime advice cũng không tự bảo đảm mọi structural dependency sạch.

Chính sách kiến trúc nên chia:
- **Static rule:** những điều compiler/weaver/analyzer có thể chứng minh.
- **Runtime invariant:** những điều phụ thuộc identity, args, flow, side effects hoặc môi trường.
- **Test coverage:** chứng minh mọi đường vào cần thiết thực sự được kiểm soát.

## 11. Các ngộ nhận thường gặp

1. **“AOP chỉ có runtime advice.”** Một số cơ chế hỗ trợ khai báo cấu trúc và diagnostics ở build.
2. **“Mọi AOP proxy tự báo lỗi kiến trúc khi compile.”** Không, khả năng phụ thuộc mô hình.
3. **“Warning luôn phải fail.”** Chỉ khi policy warning-as-error được áp dụng.
4. **“Error luôn là lỗi cú pháp.”** Có thể là lỗi custom architectural rule.
5. **“Không thấy ARC001 là không còn dependency.”** Chỉ đúng theo scope checker đã chạy.
6. **“Processor field-only bắt được method calls.”** Không.
7. **“Một method execution pointcut cho biết caller nào vi phạm.”** Chưa đủ nếu invariant về call direction.
8. **“Pre-weave PASS bảo đảm mọi member post-weave đúng.”** Không.
9. **“Một interface introduction luôn giải dependency sai.”** Không nếu quan hệ thực vẫn bị cấm.
10. **“Static analysis luôn nhận ra reflection và mọi implementation runtime.”** Không.
11. **“Chỉ cần 1 test vi phạm.”** Phải có positive và boundary fixtures.
12. **“Cấu hình checker không chạy thì mặc định PASS.”** Phải coi chưa được kiểm chứng.
13. **“Incremental PASS chắc giống clean PASS.”** Cần kiểm cache và invalidation.
14. **“Tất cả warning nên được suppress cho sạch output.”** Làm mất giá trị triage.
15. **“Làm warning thành error là thay được security runtime.”** Không.
16. **“Một rule có tên chung sẽ tự bao phủ mọi loại dependency.”** Cần ghi đúng data model và phạm vi.
17. **“Generated code không đáng quan tâm.”** Có thể là artifact cuối bị policy ràng buộc.
18. **“Chỉ thay thứ tự weaving là giải mọi conflict.”** Một invariant mâu thuẫn vẫn không có lời giải.
19. **“Một diagnostic không có ID/vị trí vẫn đủ.”** Thiếu khả năng tra cứu và sửa nhanh.
20. **“PASS ở build nghĩa hệ thống không thể vi phạm lúc chạy.”** Static và dynamic proof khác nhau.

## 12. Checklist cho một bài thực hành và triển khai thực tế

- [ ] Viết invariant kiến trúc bằng câu ngắn có thể bác bỏ.
- [ ] Chỉ rõ source, typed AST, compiled bytecode hay effective post-weave type được kiểm.
- [ ] Xác định scope: type, field, method signature, call site hoặc execution join point.
- [ ] Chọn severity WARNING/ERROR nhất quán với policy.
- [ ] Gán mã rule, vị trí, observed type/call và hướng sửa.
- [ ] Chọn AOP declaration nếu model hỗ trợ; dùng processor/analyzer bổ trợ khi cần.
- [ ] Kiểm tra quá trình structural introduction có thay representation hay không.
- [ ] Viết test hợp lệ PASS và vi phạm FAIL.
- [ ] Viết test riêng cho warning không fail trong chính sách mặc định.
- [ ] Viết boundary cases: overload, generics, reflection, generated code, module graph.
- [ ] Xác minh checker luôn chạy trong CI và không bị skip.
- [ ] Chạy clean + incremental builds để phát hiện cache stale.
- [ ] Quản lý suppressions/baseline bằng owner và ngày hết hạn.
- [ ] Không log dữ liệu nhạy cảm trong diagnostics hoặc test.
- [ ] Bảo toàn runtime authentication/authorization ngoài quy tắc tĩnh.
- [ ] Không tạo artifact release nếu một invariant bắt buộc FAIL.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Domain khai báo field concrete infra, policy cấm. Warning hay error?

**Đáp:** ERROR nếu ràng buộc bắt buộc có chứng cứ rõ, như ARC001.

**Câu 2.** Một legacy API còn được dùng trong migration, muốn đánh dấu để review?

**Đáp:** WARNING phù hợp; có thể nâng thành ERROR khi policy cấm sử dụng.

**Câu 3.** Processor demo có kiểm method body calls không?

**Đáp:** không. Nó chỉ duyệt field trong một type. Cần AST/call analysis hoặc bytecode scanner để kiểm lời gọi.

**Câu 4.** Tại sao một rule cấm call từ domain tới infra cần phân biệt call và execution?

**Đáp:** invariant nói về **hướng dependency** từ caller. Execution của callee không tự xác định một call site bị cấm.

**Câu 5.** Type được AOP thêm interface sau khi analyzer đã chạy. PASS trước weave chứng minh type cuối đã implement interface chưa?

**Đáp:** chưa; cần kiểm effective post-weave structure.

**Câu 6.** Tại sao warning trong output demo không phải lý do duy nhất của RESULT FAIL?

**Đáp:** có ARC001 ERROR. Warning không mặc định fail nếu không có warning-as-error.

**Câu 7.** Cùng source mà clean build báo ARC001, incremental build không báo. Có đủ an toàn để release?

**Đáp:** chưa. Phải kiểm cache/invalidation và bảo đảm task checker quan sát đúng representation.

**Câu 8.** Nếu người dùng runtime không được phân quyền, error về package dependency có bảo vệ được không?

**Đáp:** không. Static architecture rule không tự kiểm tra identity và permission runtime.

**Câu 9.** Nếu checker đã bị tắt nhưng output không có lỗi, đó có phải PASS?

**Đáp:** không. Đó là **chưa được kiểm tra**, phải có chính sách fail cho bước bắt buộc.

**Câu 10.** Vì sao một architecture rule cần cả fixture hợp lệ?

**Đáp:** để phát hiện false positive, đảm bảo rule không chặn các trường hợp nằm trong chính sách cho phép.

## 14. Kết luận

**Cảnh báo hoặc lỗi ở bước xây dựng để kiểm tra quy tắc kiến trúc** giúp biến kiến trúc từ văn bản hướng dẫn thành **hệ ràng buộc có thể kiểm tra tự động**. Trong AOP có thay đổi cấu trúc, điều này đặc biệt quan trọng vì các introduction hoặc bước weaving có thể khiến type và tập join point sau build khác source ban đầu.

Để thiết kế đúng:
1. **Xác định invariant và pha phân tích trước khi viết rule.**
2. **Warning phục vụ review; error chặn vi phạm không được phép.**
3. **Rule phải có chứng cứ, ID, vị trí và bộ test hai chiều.**
4. **Không hứa quá khả năng phân tích tĩnh hoặc dùng nó thay security runtime.**
5. **Đưa kiểm tra vào CI, quản lý baseline và đối chiếu clean/incremental builds.**

Ghi nhớ: **Một build PASS chỉ có ý nghĩa khi biết checker thực sự đã chạy trên đúng cấu trúc, đúng phạm vi và đúng phiên bản artifact.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-008">Tương thích mã nguồn, tương thích nhị phân và chi phí bảo trì</a>

<details>
<summary>Xem chi tiết</summary>

# Tương thích mã nguồn, tương thích nhị phân và chi phí bảo trì

## 1. Vì sao thay đổi cấu trúc bằng aspect là vấn đề tương thích?

Trong **Aspect-Oriented Programming (AOP)**, pointcut chọn *join point* còn advice bổ sung hành vi khi điểm ấy xảy ra. Một số mô hình AOP còn hỗ trợ **cắt ngang cấu trúc (structural crosscutting)** bằng *introduction/inter-type declaration*: một nhóm type có thể nhận thêm interface, method, field hoặc quan hệ khai báo. Điều này giúp tránh sửa lặp lại nhiều class, nhưng đồng thời làm thay đổi **hợp đồng mà mã nguồn, bytecode, trình liên kết, reflection và các module phụ thuộc nhìn thấy**.

Ví dụ, một introduction áp dụng cho `Order`, `Invoice` và `Shipment`:

~~~text
TRƯỚC:
  Order                 Invoice               Shipment
  (không có Trackable)   (không có Trackable)   (không có Trackable)

STRUCTURAL ASPECT, nếu công cụ hỗ trợ:
  introduce Trackable
  introduce trackingId(): String

SAU KHI WEAVE ĐÚNG:
  Order implements Trackable
  Invoice implements Trackable
  Shipment implements Trackable
~~~

Các dòng mô tả trên là **pseudocode khái niệm**, không phải một cú pháp được mọi công cụ AOP hỗ trợ. Nếu cơ chế chỉ tạo **wrapper/proxy triển khai interface mới** thì class gốc không tự thay đổi. Đó là một đối tượng proxy có contract rộng hơn, khác với introduction dệt trực tiếp vào class.

Khi phát hành thay đổi, phải trả lời bốn câu hỏi độc lập:

1. **Tương thích mã nguồn (source compatibility):** client source cũ có biên dịch lại được với API mới mà không sửa không?
2. **Tương thích nhị phân (binary compatibility):** client `.class` cũ có tiếp tục liên kết và gọi được API mới không?
3. **Tương thích hành vi (semantic compatibility):** result, exceptions, side effects, method selection và pointcut/advice order có giữ hợp đồng không?
4. **Tương thích công cụ và vận hành:** đúng woven artifact, classloader, compiler, reflection, serializer và lifecycle triển khai không?

Một bản cập nhật có thể **source-compatible, binary-compatible nhưng vẫn behavior-breaking**. Một thay đổi khác có thể **source-breaking và binary-breaking**. Vì vậy “build xanh” hoặc “không sửa source” chưa đủ kết luận an toàn.

## 2. Behavioral crosscutting khác structural crosscutting

**Behavioral crosscutting** bổ sung hành vi tại một join point:

~~~text
BEFORE execution(pay): ghi trace
AROUND execution(pay): authorize -> proceed -> metrics
AFTER THROWING: quan sát ngoại lệ
~~~

Đó là mã mô tả ý niệm. Cơ chế thực có thể dùng proxy hoặc bytecode weaving. Một behavioral weaver sửa instructions bên trong classfile nhưng **không nhất thiết bổ sung public API/member**.

**Structural crosscutting** biểu đạt sự thay đổi về kiểu hoặc khai báo:

~~~text
INTRODUCE interface Trackable vào những type được chọn
INTRODUCE method trackingId() với signature và implementation
~~~

Điểm phân biệt không phải “file `.class` có bị sửa hay không”. Cả hai kiểu can thiệp có thể sửa bytecode. Điểm phân biệt là **hợp đồng thực thi tại join point** hay **hình dạng/quan hệ của type**.

Một advice có field đếm `invocations` nằm trên object advice không có nghĩa target class nhận một introduced field. Một dynamic proxy triển khai `Tagged` cũng không biến raw target thành `Tagged`; cần kiểm **đúng object**.

## 3. Bảng khái niệm compatibility

| Cấp độ | Điều phải kiểm | Dạng lỗi hoặc thay đổi |
| --- | --- | --- |
| Source | Client source cũ compile lại với library/aspect mới | `cannot find symbol`, ambiguous overload |
| Binary | Client `.class` cũ resolve owner/method/field descriptors | `NoSuchMethodError`, `NoSuchFieldError` |
| Type/linkage | Runtime kind, hierarchy, access, loaders phù hợp | `IncompatibleClassChangeError`, `IllegalAccessError`, `ClassCastException` |
| Semantic | Caller nhận đúng value/exception, target đúng số lần | Overload khác, advice retry, result transformation |
| Reflection | Methods, fields, interface set, annotations đúng | Serializer/ORM khám phá property mới |
| Serialization | Dữ liệu cũ/mới tương tác theo contract | Schema drift, `serialVersionUID` |
| Build | Compiler/weaver/IDE nhất quán | Compile với woven, chạy với raw |
| Deployment | Runtime nạp đúng version và defining classloader | Bản JAR khác, agent cài muộn |

Tính tương thích luôn được phát biểu **so với client nào và trong ngữ cảnh nào**. “Version mới tương thích” là phát biểu thiếu thông tin nếu không nói đang giữ mã nguồn, binary, dữ liệu hay hành vi.

## 4. Source compatibility: cùng nguồn có còn biên dịch được?

Xét một thư viện v1:

~~~java
public final class PublicApi {
    public String id() { return "id-v1"; }
    public String render(Object input) { return "Object-v1"; }
}
~~~

Client:

~~~java
PublicApi api = new PublicApi();
api.id();
api.render(null);
~~~

Ở v1, `render(null)` chọn `render(Object)` vì chỉ có overload đó. V2 thêm:

~~~java
public String render(String input) {
    return "String-v2";
}
~~~

Khi biên dịch **cùng source client** với v2, compiler biết `String` cụ thể hơn `Object` và có thể chọn `render(String)`. Client không phải sửa source, nhưng đường thực thi và kết quả thay đổi.

**Source compatibility không hứa giữ nguyên overload binding và semantics sau recompilation.** Nếu hai overload không có một lựa chọn cụ thể hơn rõ ràng — chẳng hạn `render(String)` và `render(Integer)` với một lời gọi `null` phù hợp cả hai — có thể xuất hiện ambiguity.

Introduction thêm method hàng loạt vào nhiều classes có thể gây cùng hiện tượng này mà developer chưa hề sửa những call sites.

### 4.1. Thêm interface không luôn vô hại

Nếu một structural aspect giới thiệu interface có abstract methods cho nhiều classes, một compiler xử lý contract đó phải đảm bảo implementation phù hợp. Nếu thêm default methods vào các interfaces khác nhau mà chúng xung đột, một type có thể cần cách resolution rõ ràng. Giới thiệu một method trùng erasure của generic method cũng có thể tạo name clash.

Không áp quy tắc máy móc “cứ thêm là source-compatible”. Luôn test **client source** và **implementors** thực tế.

## 5. Binary compatibility: cùng `.class` có liên kết được không?

Khi biên dịch Java, client tạo symbolic references trong bytecode. Method không chỉ được nhận biết bằng tên; JVM còn dùng **owner, name và descriptor**.

Các descriptors mẫu:

~~~text
id():String        -> ()Ljava/lang/String;
render(Object)     -> (Ljava/lang/Object;)Ljava/lang/String;
render(String)     -> (Ljava/lang/String;)Ljava/lang/String;
~~~

**JVM method descriptor bao gồm return type.** Vì vậy đổi return type hoặc parameter types có thể làm mất descriptor mà binary cũ cần, ngay cả khi tên method giữ nguyên. Java source overload resolution diễn ra lúc compile; binary cũ không tự chọn overload mới khi library update.

Các lỗi thường gặp:

| Runtime lỗi | Nguyên nhân gợi ý |
| --- | --- |
| `NoSuchMethodError` | Binary tham chiếu method/descriptor không còn được resolve |
| `NoSuchFieldError` | Field symbolic reference không còn |
| `IncompatibleClassChangeError` | Thay đổi class/interface hoặc instance/static expectation không tương thích |
| `IllegalAccessError` | Access sau cập nhật không còn hợp lệ cho binary |
| `AbstractMethodError` | Invocation cần concrete implementation nhưng runtime thiếu |
| `NoClassDefFoundError` | Required class không thể tìm/định nghĩa ở runtime |
| `ClassCastException` | Runtime object không đáp ứng type mà caller cast, có thể do wrapper/loader |

Đó là **ví dụ các failure modes**, không phải bảng ánh xạ một lỗi sang đúng một thay đổi duy nhất. Cần kiểm descriptor, class hierarchy, classpath và classloader trước khi quy kết.

**Một binary có thể load thành công nhưng chỉ lỗi khi chạm vào method cũ**. Vì vậy test chỉ khởi động chương trình mà không gọi các public methods quan trọng là chưa đủ.

## 6. Thực nghiệm Java 17: cùng một client, ba phiên bản thư viện

Thí nghiệm thực hiện đúng hai chiều:

- Giữ **Client.class đã compile với v1** và chạy trên libraries v1/v2/v3.
- Giữ **Client.java không sửa** nhưng compile lại với v2 và thử compile với v3.

Kết quả cho thấy **source, binary và behavior không tương đương**. Đây là thử nghiệm Java thuần về ABI; nó không giả lập rằng `javac` có một structural weaver. Tuy nhiên, introductions trong một AOP engine phải tôn trọng các nguyên lý liên kết này nếu client đã được compile chống hợp đồng được weave.

### 6.1. Library v1 — `PublicApi.java`

~~~java
public final class PublicApi {
    public String id() { return "id-v1"; }
    public String render(Object input) { return "Object-v1"; }
}
~~~

### 6.2. Library v2 — cùng binary name, thêm overload

~~~java
public final class PublicApi {
    public String id() { return "id-v2"; }
    public String render(Object input) { return "Object-v2"; }
    public String render(String input) { return "String-v2"; }
}
~~~

### 6.3. Library v3 — bỏ method `id()`

~~~java
public final class PublicApi {
    public String render(Object input) { return "Object-v3"; }
    public String render(String input) { return "String-v3"; }
}
~~~

### 6.4. Client dùng ở cả ba giai đoạn

~~~java
public final class Client {
    public static void main(String[] args) {
        PublicApi api = new PublicApi();
        try {
            System.out.println("ID " + api.id());
        } catch (NoSuchMethodError error) {
            System.out.println("LINKAGE NoSuchMethodError id()");
        }
        System.out.println("RENDER " + api.render(null));
    }
}
~~~

Đây là **mã chạy thật** sau khi compile trong thư mục phù hợp. Catch `NoSuchMethodError` chỉ nhằm làm rõ thí nghiệm, **không phải mẫu xử lý production**. Nếu production gặp linkage error, cần sửa dependency/artifact/ABI thay vì im lặng bỏ lỗi.

### 6.5. Script PowerShell tái hiện toàn bộ và tự dọn dữ liệu tạm

Yêu cầu Windows PowerShell 5.1+ và JDK 17+ có `javac`/`java` trên PATH. Script viết 4 source files vào thư mục tạm, xây 3 library versions và 2 client versions, chạy và kiểm trường hợp compilation thất bại. Các lệnh đều dùng đường dẫn tạm; không chỉnh sửa project.

~~~powershell
$ErrorActionPreference='Stop'
$root=Join-Path $env:TEMP ('aop-compat-'+[guid]::NewGuid().ToString('N'))
$enc=New-Object System.Text.UTF8Encoding($false)
try {
  foreach($dir in @('src-v1','src-v2','src-v3','src-client','v1','v2','v3','client-v1','client-v2','client-v3')) {
    [void](New-Item -ItemType Directory -Force -Path (Join-Path $root $dir))
  }
  function Put([string]$relative,[string]$value) {
    [IO.File]::WriteAllText((Join-Path $root $relative),$value,$enc)
  }
  Put 'src-v1/PublicApi.java' @'
public final class PublicApi {
    public String id() { return "id-v1"; }
    public String render(Object input) { return "Object-v1"; }
}
'@
  Put 'src-v2/PublicApi.java' @'
public final class PublicApi {
    public String id() { return "id-v2"; }
    public String render(Object input) { return "Object-v2"; }
    public String render(String input) { return "String-v2"; }
}
'@
  Put 'src-v3/PublicApi.java' @'
public final class PublicApi {
    public String render(Object input) { return "Object-v3"; }
    public String render(String input) { return "String-v3"; }
}
'@
  Put 'src-client/Client.java' @'
public final class Client {
    public static void main(String[] args) {
        PublicApi api = new PublicApi();
        try {
            System.out.println("ID " + api.id());
        } catch (NoSuchMethodError error) {
            System.out.println("LINKAGE NoSuchMethodError id()");
        }
        System.out.println("RENDER " + api.render(null));
    }
}
'@
  foreach($i in 1..3) {
    & javac --release 17 -encoding UTF-8 -d (Join-Path $root "v$i") (Join-Path $root "src-v$i/PublicApi.java")
    if($LASTEXITCODE -ne 0){throw "compile v$i failed"}
  }
  & javac --release 17 -encoding UTF-8 -cp (Join-Path $root 'v1') -d (Join-Path $root 'client-v1') (Join-Path $root 'src-client/Client.java')
  if($LASTEXITCODE -ne 0){throw 'compile client-v1 failed'}
  Write-Output 'RUN existing client + v1'
  & java -cp ((Join-Path $root 'client-v1')+';'+(Join-Path $root 'v1')) Client
  if($LASTEXITCODE -ne 0){throw 'run client+v1 failed'}
  Write-Output 'RUN existing client + v2'
  & java -cp ((Join-Path $root 'client-v1')+';'+(Join-Path $root 'v2')) Client
  if($LASTEXITCODE -ne 0){throw 'run client+v2 failed'}
  & javac --release 17 -encoding UTF-8 -cp (Join-Path $root 'v2') -d (Join-Path $root 'client-v2') (Join-Path $root 'src-client/Client.java')
  if($LASTEXITCODE -ne 0){throw 'compile client-v2 failed'}
  Write-Output 'RUN rebuilt client + v2'
  & java -cp ((Join-Path $root 'client-v2')+';'+(Join-Path $root 'v2')) Client
  if($LASTEXITCODE -ne 0){throw 'run new client+v2 failed'}
  Write-Output 'RUN existing client + v3'
  & java -cp ((Join-Path $root 'client-v1')+';'+(Join-Path $root 'v3')) Client
  if($LASTEXITCODE -ne 0){throw 'run client+v3 failed'}
  $ErrorActionPreference='Continue'
  $diagnostic=& javac --release 17 -encoding UTF-8 -cp (Join-Path $root 'v3') -d (Join-Path $root 'client-v3') (Join-Path $root 'src-client/Client.java') 2>&1
  $status=$LASTEXITCODE
  $ErrorActionPreference='Stop'
  if($status -eq 0){throw 'recompile unexpectedly succeeded'}
  Write-Output 'RECOMPILE original source + v3 FAILED (expected)'
  Write-Output 'ALL COMPATIBILITY CHECKS PASSED'
}
finally {
  if(Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
}
~~~

Đầu ra **đã kiểm chứng bằng biên dịch và chạy thực với JDK 17**:

~~~text
RUN existing client + v1
ID id-v1
RENDER Object-v1
RUN existing client + v2
ID id-v2
RENDER Object-v2
RUN rebuilt client + v2
ID id-v2
RENDER String-v2
RUN existing client + v3
LINKAGE NoSuchMethodError id()
RENDER Object-v3
RECOMPILE original source + v3 FAILED (expected)
ALL COMPATIBILITY CHECKS PASSED
~~~

### 6.6. Đọc bốn trường hợp

1. **V1 binary + V1 library:** `id()` tồn tại; `render(null)` đã compile thành `render(Object)`, kết quả `Object-v1`.
2. **V1 binary + V2 library:** symbolic reference của lời `render(null)` trong binary cũ vẫn là `render(Object)`, kết quả `Object-v2`; overload String mới không thay bytecode cũ.
3. **Client source rebuild + V2 library:** javac thấy thêm `render(String)` và chọn overload cụ thể hơn, kết quả `String-v2`. Source vẫn compile, **behavior thay đổi**.
4. **V1 binary + V3 library:** binary gọi `id()` đã bị xóa nên phát sinh `NoSuchMethodError`; phần catch của bài học in tên lỗi. Compile lại Client source với v3 thất bại do thiếu `id()`.

Thử nghiệm làm rõ một vấn đề cốt lõi khi version một structural aspect: **một introduction bổ sung overloaded member có thể không phá binary cũ nhưng thay đổi behavior của client khi rebuild**. Ngược lại, xóa một member đã được client compile phụ thuộc có thể phá cả hai.

## 7. Một introduction interface/method ảnh hưởng compile-time và runtime ra sao?

Giả sử `Order` ban đầu không implements `Trackable`.

~~~text
RAW:
  Order implements BusinessEntity

WRAPPER:
  OrderProxy implements BusinessEntity, Trackable
  Order raw class giữ nguyên

WOVEN (nếu công cụ hỗ trợ):
  Order implements BusinessEntity, Trackable
  method trackingId() nằm trong woven class
~~~

**Đừng đánh đồng wrapper với woven target class**. Nếu caller giữ raw `Order` thì cast `Trackable` không tự thành công chỉ vì một proxy khác có interface ấy. Nếu source compiler không được cung cấp introduced type contract, nó cũng không tự cho phép `new Order().trackingId()`.

### 7.1. Mã nguồn downstream phải nhìn thấy hợp đồng

Cần xác định:

- Introduction được áp dụng ở compile-time, binary weaving hay load-time?
- Compiler của client nhìn **raw source**, **woven classfile**, **generated declarations** hay một interface API rõ?
- IDE, incremental build, language server có hiểu thông tin mới không?
- Module chạy dùng cùng artifact đã compile không?
- Một module khác có thể dùng raw JAR và tạo type identity khác không?

**Client compile với woven API nhưng chạy raw JAR** là một kiểu mismatch dễ gây lỗi linkage/runtime; không thể khắc phục chỉ bằng unit test advice logic.

### 7.2. Khi binary cũ chạy trên woven class mới

Nếu member mà binary cũ cần vẫn giữ nguyên, lời gọi có thể chạy. Nhưng introduced interface có thể đổi behavior ngoài ý định: `instanceof Trackable` bắt đầu true; reflective serializer thấy getter mới; behavioral pointcut có thể match những methods mới.

### 7.3. Khi binary mới chạy trên raw class cũ

Một client mới compile gọi `Order.trackingId()` có thể gặp lỗi nếu runtime nạp `Order` raw chưa có method. Đây là **lỗi artifact compatibility**, không nhất thiết do business code sai. Phải test cả **new-client + expected woven artifact** và **old-client + new woven artifact**.

## 8. Ma trận thay đổi cấu trúc và mức rủi ro

Bảng chỉ là **định hướng kiểm thử**; quy tắc chi tiết còn phụ thuộc JLS, JVM và mô hình AOP.

| Thay đổi | Nguy cơ source | Nguy cơ binary | Vấn đề bổ sung |
| --- | --- | --- | --- |
| Thêm method tên mới | Thường ít nếu không conflict | Thường giữ old references | API discovery/reflection đổi |
| Thêm overload | Có thể source vẫn compile nhưng binding khác hoặc ambiguous | Old descriptors thường còn | Semantic drift khi recompile |
| Xóa/đổi tên member cũ | Client cũ có thể không compile | Có thể `NoSuchMethodError`/`NoSuchFieldError` | Migration |
| Đổi return/parameter types | Source call hoặc assignments có thể fail | Descriptors thay | Bridge/generics |
| Đổi instance↔static | Cách invocation thay | Có thể `IncompatibleClassChangeError` | Inheritance semantics |
| Thu hẹp visibility | Compilation access lỗi | Có thể `IllegalAccessError` | Module boundaries |
| Thêm interface | Type checking/method conflicts | Tùy resolution/hierarchy | `instanceof`, overload, reflection |
| Thêm abstract method vào interface | Implementors mới có thể phải bổ sung | Có thể lỗi khi invocation trên old implementor | Default/abstract semantics |
| Thêm default method | Một số cases compile lại không đúng | Có thể đổi method resolution | Diamond conflicts |
| Thêm field | Thường ít ảnh hưởng source references cũ | Old field descriptors thường còn | Serialization, ORM |
| Thêm interface trên proxy | Raw target source không đổi | Caller phải có wrapper đúng | Object identity, alias |
| Dệt before/after giữ method descriptors | Source API thường không đổi | Linkage có thể giữ | Exceptions/performance/side effects |
| Thay class hierarchy | Có thể đổi casts/overloads | Có thể lỗi method/class resolution | Dispatch và binary identity |

Không suy từ “đều là additive change” rằng một migration an toàn với **mọi** clients. Cần biết overloads, inheritance graph, raw/woven artifacts và điểm quan sát.

## 9. Cơ chế weaving và giới hạn của JVM class schema

Một structural declaration có thể được áp dụng trước khi JVM định nghĩa class:

~~~text
Source or existing binary
         |
         +-> compile-time / binary weaving
         +-> load-time transform (trước defineClass)
         |
         v
      JVM class definition + verification
         |
         v
      Class objects / instances
~~~

Một Java agent có thể biến đổi bytecode, nhưng **redefinition/retransformation một class đã được định nghĩa** qua Instrumentation API thông thường có giới hạn về **schema changes**: không tự do thêm/bớt methods/fields, đổi signatures hay inheritance. Không suy từ khả năng đổi method body lúc chạy rằng có thể hot-add interface vào bất kỳ loaded class nào.

### 9.1. Build-time và load-time có rủi ro khác nhau

- **Build-time weaving:** dễ xác định woven artifact trước khi publish, nhưng phải quản lý compiler, cache và artifact provenance.
- **Load-time weaving:** linh hoạt hơn khi nạp class, nhưng phụ thuộc transformer/agent startup, classloader, thứ tự định nghĩa class và chính sách runtime.
- **Runtime proxy:** không đổi target class schema, nhưng chỉ các lời gọi qua proxy và contract của proxy được can thiệp.
- **Existing loaded class:** structural schema đổi trực tiếp có thể không hỗ trợ, nên restart/redeploy có thể cần.

**Class definition**, **class initialization**, **aspect instantiation** và **advice invocation** là bốn mốc riêng; không đồng nhất chúng.

### 9.2. Defining classloader là một phần của type identity

Cùng binary class name nhưng do hai defining classloaders khác nhau tạo ra có thể là hai runtime types khác nhau. Trong plugin systems, module A có woven `Order` còn module B có raw `Order`; casting hoặc reflective matching có thể không hoạt động như mong muốn.

Khi debug, ghi **binary name + defining classloader + artifact hash/version**, không chỉ tên class.

## 10. Reflection và API discovery có thể đổi hành vi ngầm

Một structural introduction thêm `getAuditId()` hoặc field `auditId` có thể được các công cụ reflection khám phá:

- JSON serializer nhận property mới theo convention.
- ORM mapping scan field/getter mới.
- Plugin registry dò interface vừa được thêm.
- Schema generator tìm methods/annotations mới.
- RPC endpoint scanner có thể xem interface mới.
- Observability tool có thể đếm methods/annotations khác đi.

Không phải mọi framework sẽ tự phản ứng như nhau; điều đó tùy cấu hình. Nhưng **reflective surface** là một dạng hợp đồng quan trọng ngoài source/binary compatibility.

### 10.1. Kiểm đúng lớp

- `Core.class` raw: các members class raw thực sự khai báo.
- `proxy.getClass()`: các interfaces/methods của proxy.
- Woven `Core.class`: type đã được engine thay đổi nếu có.
- Static type client được compile dựa vào: có thể không biết một introduced method.

`getMethods()` liệt kê public methods theo reflection rules, gồm inherited public methods; `getDeclaredMethods()` liệt kê methods **được khai báo trực tiếp** trong class. Không đếm đơn giản methods và cho đó là bằng chứng weaving đúng; còn synthetic/bridge methods do compiler sinh.

### 10.2. Bridge và synthetic methods

Với generic overrides, compiler có thể tạo bridge methods để duy trì method dispatch sau erasure. Một weaver cũng có thể bổ sung helpers synthetic. Khi so public ABI, kiểm method descriptor và flags, không suy “hai methods cùng tên tức introduction bị chạy trùng”.

## 11. Serialization compatibility và data schema

Một introduced field/property có thể làm dữ liệu được serialize thay đổi dù binary caller vẫn chạy. Đối với Java native serialization, nếu class dùng `serialVersionUID` tính mặc định thì thay đổi class shape có thể ảnh hưởng version UID; ngay cả `serialVersionUID` cố định cũng **không biến mọi data schema change thành an toàn**.

Những tình huống cần test:

- Serialized data từ bản cũ đọc bằng bản mới.
- Dữ liệu mới đọc bởi bản cũ nếu rollback/older clients là yêu cầu.
- Property vừa được introduce có default/backfill thích hợp không.
- Getter mới có lộ secrets hoặc tạo cycles trong object graph không.
- ORM có map field mới ngoài dự kiến không.
- Binary class name/type hierarchy đã thay có ảnh hưởng serializer không.
- JSON schema/protocol compatibility của hệ thống bên ngoài có chính sách riêng không.

**Compatibility của persisted data khác compatibility của method invocation.** Chỉ test `javac` và `java` chưa chứng minh schema migration đúng.

## 12. Introduced member và xung đột với cấu trúc đang có

### 12.1. Name collision

Nếu một target đã khai báo `trackingId()` với ý nghĩa riêng, còn aspect giới thiệu `trackingId()` khác, không thể để hai implementations tranh cùng contract mà không có rule cụ thể. Một số công cụ báo conflict, một số có precedence hoặc visibility riêng.

**Thiết kế tốt:** giới hạn type selectors, đặt tên member có ý nghĩa domain, xây positive/negative tests và fail build khi xung đột chưa được quyết định.

### 12.2. Generic erasure

`List<String>` và `List<Integer>` có cùng erased raw type `List`. Không mặc định hai introduced methods chỉ khác generic type arguments đều tạo overload hợp lệ. Kiểm cả generic signature metadata lẫn JVM descriptors.

### 12.3. Interface default methods

Thêm interface mới có thể làm object đáp ứng một capability mà các thư viện khác tự khám phá. Nếu hai defaults cùng signature cạnh tranh theo hierarchy, compiler/runtime phải có resolution phù hợp. Từng engine có thể giới hạn introductions; không tuyên bố khả năng thêm interface là không điều kiện.

### 12.4. Visibility, modules và `final`

Một subclass proxy không thể subclass Java `final` class bình thường, nhưng bytecode weaving vào một method body và thay type relationship là những cơ chế khác. Java modules, package accessibility, private members, verifier và classloader constraints đều cần xét.

Không suy từ một hạn chế của proxy rằng mọi kiểu weaving đều bị giới hạn y hệt.

## 13. Binary surface khi structural weaving đổi hoặc rút đi

Khi một introduced API đã được client compile sử dụng, nó trở thành **API thật** đối với client ấy, dù source `Order.java` không khai báo rõ.

Nếu version mới:
- Xóa introduction `trackingId()`;
- Chỉ để method ở một proxy thay vì woven target;
- Đổi return type hoặc field type;
- Thay target selector nên một số classes không còn được introduce;

thì **client binaries cũ có thể tham chiếu API biến mất**. Lỗi có thể chỉ xuất hiện ở một đường gọi hiếm, khi production thật gọi method đó.

Nguyên tắc: **các public introductions phải được quản lý version như public source methods**. Chúng không “riêng tư” chỉ vì nằm trong file aspect.

## 14. Bảo trì: giảm boilerplate không đồng nghĩa giảm tổng chi phí

Một structural aspect có thể thay hàng trăm lần chỉnh source, nhưng mang thêm chi phí:

1. **Discoverability:** mở `Order.java` không thấy method đang hiện diện trong woven class.
2. **Tooling:** IDE/compiler/static analysis phải hiểu introduction.
3. **Build:** bước weaving, source generation, incremental cache và dependency graph phải chính xác.
4. **Testing:** thêm các tests source/binary/reflective và woven/raw artifact.
5. **Debugging:** stacktrace có proxy, synthetic methods hoặc classloader mismatch.
6. **Deployment:** agent/load ordering và artifact version cần kiểm.
7. **Versioning:** clients có thể compile dựa trên methods được introduced mà thư viện không nhìn thấy trực tiếp.
8. **Onboarding:** developer mới cần hiểu crosscutting type contract và join point behavior.
9. **Migration:** thay engine AOP hoặc rút introduction phải có compatibility shim.
10. **Security/data contracts:** interface mới có thể mở capabilities và serializer properties.

Ngược lại, lợi ích có thật khi nhiều types cần **một contract nhất quán**, quy tắc nằm ở một nơi, compiler/weaver đáng tin và test suite có kiểm soát.

### 14.1. Khung đánh giá chi phí định tính

~~~text
Total maintenance cost ≈
  initial implementation
+ weaving/build/IDE maintenance
+ compatibility verification
+ debugging and observability
+ deployment/rollback costs
+ future migration costs
− duplicated declarations removed
− consistency errors prevented
~~~

Đây chỉ là **mô hình suy nghĩ**, không phải công thức định lượng chung. Điểm cần xem là **blast radius** của một introduction: chọn tất cả types trong package rộng có thể tự mở rộng khi class mới xuất hiện.

### 14.2. Cùng một concern có thể dùng adapter thay vì introduction

Nếu chỉ một API boundary cần `Tagged`, wrapper implementation của `Tagged` có thể dễ bảo trì. Nếu toàn bộ codebase thực sự yêu cầu `Order` ở type-level phải đáp ứng `Trackable` và compiler/tooling hỗ trợ tốt, structural introduction có thể có lợi.

Không nên chọn weaving khi sửa source trực tiếp hoặc composition đơn giản hơn, nhưng cũng không nên phủ nhận lợi ích của một contract cắt ngang được kiểm soát.

## 15. Thiết kế migration cho API được introduce

Nếu muốn đổi `trackingId()` sang `traceId()`, **xóa ngay** method cũ là rủi ro đối với client source và binaries.

Một chiến lược chuyển tiếp có thể là giữ method cũ và forward:

~~~java
interface Trackable {
    String traceId();

    @Deprecated
    default String trackingId() {
        return traceId();
    }
}
~~~

Đây là fragment Java hợp lệ minh họa **API deprecation**, không phải đảm bảo mọi client đã compile với introduced `Order.trackingId()` sẽ link. Cần kiểm **binary owner/descriptor cũ**: một default method trên interface có thể hoặc không thỏa call site cũ, tùy method resolution và nơi method từng được giới thiệu. Đôi khi phải giữ một delegating method đúng contract cũ ngay trên woven class.

Các bước chuyển tiếp:

1. Công bố contract mới.
2. Giữ compatibility shim của member cũ nếu feasible.
3. Build old clients và chạy trên new artifact.
4. Cập nhật clients theo từng release.
5. Giám sát usage của API deprecated.
6. Chỉ xóa khi consumers đã di trú hoặc version policy cho phép breaking change.
7. Ghi hướng rollback cho cả code và serialized data.
8. Nếu bỏ weaving, chuyển sang explicit interface/adapter hoặc source declaration có khả năng giữ ABI.

## 16. Ma trận test cần có

| Nhóm | Test cụ thể |
| --- | --- |
| Source | Compile lại client source cũ với woven API mới |
| Binary | Chạy client `.class` cũ với library mới |
| Overload | `null`, generic/primitive overload selection và ambiguity |
| Introduction | `instanceof`, method/field signatures, visibility |
| Negative coverage | Classes không được chọn không nhận interface/member |
| Reflection | `getMethods`, `getDeclaredMethods`, `isAssignableFrom` |
| Serialization | Old data → new version, new data → old version nếu yêu cầu |
| Behavioral | Advice order, exceptions, retry, cached results, side effects |
| Proxy | Client đi qua proxy vs raw target; self-invocation |
| Weaving | Đúng classfile đã weave, agent/classloader đúng thời điểm |
| Deployment | Không nhầm raw/woven JAR, version mismatch |
| Rollback | Woven→raw, API removed, data schema compatibility |
| Concurrency | Introduced state có thread-safe và lifecycle đúng |
| Build | IDE/CLI cùng nhìn API, incremental build không dùng stale output |

**Bắt buộc kiểm cả positive lẫn negative coverage.** Chỉ assert `Order` có `Trackable` chưa đủ; có thể `SecretToken` hoặc type không liên quan cũng vô tình được introduce bởi pattern quá rộng.

### 16.1. Không mock mất điều đang cần test

Unit test một behavioral advice khi gọi trực tiếp method handler không chứng minh structural weaver đã chạy. Tương tự, test một proxy implements interface mới không chứng minh raw target class thực sự bị thay cấu trúc.

Integration test phải dùng **artifact/runtime mechanism thực** trong phạm vi khả thi.

## 17. Dùng `javap` và ABI snapshots để thấy khác biệt

JDK có công cụ `javap` cho phép kiểm method signatures/descriptors:

~~~bash
javap -p -s PublicApi
~~~

Để so ba thư viện của ví dụ, đặt classpath tới thư mục library tương ứng; kiểm `id()Ljava/lang/String;` và hai overload `render(...)Ljava/lang/String;`.

Một ABI baseline có thể ghi:

~~~text
PublicApi:
  id()Ljava/lang/String;
  render(Ljava/lang/Object;)Ljava/lang/String;
~~~

Phiên bản mới thêm:

~~~text
  render(Ljava/lang/String;)Ljava/lang/String;
~~~

Xóa `id()` ở phiên bản sau là một ABI diff có nguy cơ breaking. Nếu dùng structural weaving, **chụp ABI của woven classfiles**, không chỉ raw source. Có thể thêm automated compatibility checks trong CI nhưng vẫn phải test hành vi.

### 17.1. Build provenance

Khi `NoSuchMethodError` xuất hiện, cần xác định:

- Method owner/name/descriptor được client yêu cầu.
- Client compile với library version nào.
- Runtime đã nạp classfile nào.
- Woven hay raw artifact?
- Defining classloader nào?
- Weaver/agent có chạy trước defineClass không?
- Module/classpath có cả hai versions không?
- Aspect type selectors có loại class khỏi introduction không?

Không vội sửa bằng cách “thêm try/catch” hoặc tăng timeout; đây là lỗi compatibility hoặc deployment cần giải quyết ở nguồn.

## 18. Behavioral compatibility cũng chịu tác động của structural change

Khi giới thiệu method/interface mới, các behavioral aspects có thể:

- Match vào method mới do pointcut quá rộng.
- Ghi event audit mới cho một lời gọi mà trước đây không tồn tại.
- Làm metrics count tăng do thêm synthetic/bridge methods.
- Thay advice precedence nếu introduce method kéo thêm một layer.
- Khiến serializer/reflection gọi introduced getters và kích hoạt code có side effects.
- Làm một branch `instanceof` bắt đầu chạy.
- Cho phép caller mới dùng một capability có rủi ro bảo mật.

Do đó kiểm structural ABI xong **chưa đủ**. Test behavioral side effects, exception propagation, timing và những đối tượng không được chọn.

### 18.1. Binary compatibility có thể vẫn giữ mà hành vi đổi

Trong thử nghiệm v1/v2, `Client.class` cũ vẫn chạy với v2; method `render(Object)` tồn tại, nên ABI call không bị mất. Nhưng implementation của nó đã đổi từ `Object-v1` sang `Object-v2` dù signature không đổi. Một advice chèn thêm retry thậm chí có thể làm target execute hai lần và tạo side effect trùng.

**Một thư viện tương thích nhị phân không phải thư viện giữ nguyên nghĩa vụ nghiệp vụ.**

## 19. Những hiểu lầm thường gặp

1. **“Compile source xanh tức binary cũ chắc chạy.”** Không; phải chạy old binaries.
2. **“Binary cũ chạy tức source rebuild chọn cùng overload.”** Không; overload resolution diễn ra khi compile.
3. **“Thêm method luôn an toàn.”** Có thể có overload ambiguity và semantic drift.
4. **“Giảm số dòng code luôn giảm maintenance cost.”** Weaving/build/compatibility có chi phí riêng.
5. **“Một introduction chỉ là advice before.”** Không; nó thay đổi contract của type nếu engine thực hỗ trợ.
6. **“Bytecode weaving nào cũng sửa type hierarchy.”** Không; behavioral weaving có thể chỉ sửa instructions.
7. **“Proxy implements Tagged nghĩa target implements Tagged.”** Không.
8. **“Client compiler tự thấy method sẽ weave sau.”** Không nếu không có type information/tooling.
9. **“NoSuchMethodError có thể catch như lỗi validation.”** Đây là lỗi linkage, không phải policy xử lý nghiệp vụ bình thường.
10. **“Chỉ so tên method là đủ.”** JVM dùng owner/descriptors/access/kind.
11. **“Return type không có trong JVM descriptor.”** Sai.
12. **“Một class name luôn chỉ một runtime type.”** Còn defining loader.
13. **“Hot reload có thể thêm mọi field/interface ở loaded class.”** Instrumentation schema có giới hạn.
14. **“Raw artifact test đủ cho woven production.”** Không.
15. **“Giới thiệu getter không ảnh hưởng serializer.”** Có thể làm thay property discovery.
16. **“Một default method mới không thể gây conflict.”** Sai trong các hierarchy phù hợp.
17. **“Giữ serialVersionUID nghĩa mọi schema change an toàn.”** Không.
18. **“ABI pass nghĩa advice behavior pass.”** Không.
19. **“Thay method trong aspect không phải public API change.”** Nếu clients gọi introduced member, nó là API thực.
20. **“Đổi loader không ảnh hưởng casting.”** Runtime identity phụ thuộc loader.
21. **“Module compile woven, module khác compile raw chắc tương đương.”** Không.
22. **“Một structural rule match rộng sẽ không ảnh hưởng classes mới.”** Nó có thể tự match classes được thêm.
23. **“Bỏ structural weaver chỉ xóa dependency.”** Client APIs, artifact và reflection còn phụ thuộc nó.
24. **“Add interface không đổi behavior.”** `instanceof`, dispatch và reflection có thể đổi.
25. **“Chỉ test client mới với library mới là đủ.”** Old-client/new-library bắt lỗi binary compatibility.
26. **“Xóa method cũ ngay vì đã deprecated.”** Old binaries có thể vẫn cần nó.
27. **“Wrapper và woven target có cùng identity.”** Chúng thường là hai objects hoặc types khác nhau.
28. **“Source compatibility bảo đảm serializer schema.”** Không.
29. **“Chạy không lỗi lúc startup là ABI an toàn.”** Một missing method có thể chỉ bị resolve khi gọi.
30. **“Một test `instanceof` duy nhất chứng minh structural introduction đúng.”** Cần cả chính xác types được/không được chọn, descriptors và toolchain.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Source compatibility khác binary compatibility thế nào?  
**Đáp án:** Source hỏi có thể compile lại source cũ không; binary hỏi compiled client cũ có resolve/run với library mới không.

**Câu 2.** V2 thêm `render(String)`, binary client compile với v1 có tự gọi overload mới không?  
**Đáp án:** Không, descriptor của call cũ vẫn là `render(Object)`.

**Câu 3.** Vì sao rebuild client source cũ với v2 lại in `String-v2`?  
**Đáp án:** `javac` chọn overload `String` cụ thể hơn khi truyền `null`.

**Câu 4.** V3 bỏ `id()`, binary client cũ có thể gặp lỗi gì?  
**Đáp án:** `NoSuchMethodError` khi lời gọi cần method cũ.

**Câu 5.** Thêm interface cho proxy có thêm interface vào raw target class không?  
**Đáp án:** Không, chỉ proxy object mang contract đó.

**Câu 6.** Tại sao source compiler chưa thấy introduction thì không thể gọi method mới trực tiếp?  
**Đáp án:** Compiler cần type information ở thời điểm compile, không tự biết method sẽ được weave về sau.

**Câu 7.** Có cần kiểm reflection khi structural introduction thêm field/getter không?  
**Đáp án:** Có, vì serializer/ORM/schema scanner có thể quan sát sự thay đổi.

**Câu 8.** `serialVersionUID` cố định có bảo đảm dữ liệu cũ và mới luôn tương thích?  
**Đáp án:** Không; còn type/field/schema và custom serialization contracts.

**Câu 9.** Một class đã load có chắc hot-add field qua Java Instrumentation API thông thường không?  
**Đáp án:** Không; schema changes của redefinition/retransformation có giới hạn.

**Câu 10.** Vì sao cùng tên class có thể không cast được giữa hai plugins?  
**Đáp án:** Defining classloader khác tạo runtime type identity khác.

**Câu 11.** Có thể giữ ABI nhưng vẫn phá behavioral contract không?  
**Đáp án:** Có, ví dụ đổi implementation, thêm advice retry hoặc overload được chọn khác sau recompilation.

**Câu 12.** Trước khi xóa introduced `trackingId()` cần làm gì?  
**Đáp án:** Kiểm consumers, giữ compatibility shim nếu khả thi, test binaries cũ và lập migration/rollback.

## 21. Checklist trước khi phát hành một structural aspect

- [ ] Xác định type được chọn và type **không** được chọn.
- [ ] Ghi rõ member/interface/field mới cùng descriptor, visibility và lifecycle.
- [ ] Kiểm xung đột name, generics erasure, defaults, inheritance và access.
- [ ] Xác định compiler/source processing thấy introduction ở giai đoạn nào.
- [ ] Build consumer source cũ chống woven artifact mới.
- [ ] Chạy compiled client cũ chống woven artifact mới.
- [ ] Test overload selection sau khi biên dịch lại.
- [ ] So public ABI của woven artifacts, không chỉ raw source.
- [ ] Test `instanceof`/reflection trên đúng proxy hoặc target class.
- [ ] Test serializer/ORM và dữ liệu phát hành cũ.
- [ ] Kiểm aspect mới có làm behavioral pointcut match thêm methods không.
- [ ] Kiểm exception/result/side effect của những lời gọi đã tồn tại.
- [ ] Đảm bảo CI artifact giống artifact phát hành.
- [ ] Không trộn raw/woven JAR sai version trong classpath/module path.
- [ ] Kiểm load-time transformer/agent trước class definition nếu cần.
- [ ] Kiểm classloader/module boundaries của ứng dụng.
- [ ] Xem xét concurrency nếu introduced field có trạng thái mutable.
- [ ] Ghi deprecation/migration path cho API public bị đổi.
- [ ] Có rollback plan cho cả code và data schema.
- [ ] Theo dõi linkage errors/serializer changes sau triển khai.

## 22. Tổng kết

**Tương thích mã nguồn, tương thích nhị phân, tương thích hành vi và chi phí bảo trì** là các trục đánh giá riêng khi AOP thay đổi cấu trúc. Một introduction có thể bổ sung contract cho nhiều types, nhưng cũng tạo rủi ro xung đột method, lệch raw/woven artifact, lỗi JVM linkage, thay overload selection và làm reflection/serialization công bố API mới. Một proxy mở rộng interface chỉ cho **proxy** không đồng nghĩa class target đã được biến đổi.

Thử nghiệm Java 17 cho thấy: client binary cũ **giữ nguyên descriptor** khi library thêm overload; cùng source compile lại có thể chọn overload khác; xóa method cũ dẫn đến `NoSuchMethodError` và source compilation failure. Vì vậy khi quản lý structural AOP như một public API, cần **test client cũ ở cả dạng source và binary trên artifact mới**, đồng thời kiểm behavioral regressions, classloaders, reflective surface và khả năng rollback.

Giá trị của aspect nằm ở khả năng mô-đun hóa concern một cách có chủ đích, không phải biến đổi chương trình mà không cần chịu trách nhiệm về compatibility. **Thiết kế tốt làm rõ ai thấy type contract mới, khi nào nó tồn tại và chi phí vận hành của nó trong suốt vòng đời phiên bản.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-crosscutting-009">Khi nào nên sửa thiết kế kiểu trực tiếp thay vì mở rộng bằng aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào nên sửa thiết kế kiểu trực tiếp thay vì mở rộng bằng aspect

## 1. Câu hỏi kiến trúc đằng sau một phép mở rộng kiểu

Trong **Aspect-Oriented Programming (AOP)**, advice có thể can thiệp vào những join point đã có. Một số mô hình AOP nâng cao còn hỗ trợ **structural crosscutting**: đưa vào method, field, interface hoặc một capability bổ sung bằng introduction, mixin, proxy, code generation hoặc source/bytecode weaving, tùy công cụ.

Khả năng ấy hữu ích khi một mối quan tâm xuyên suốt phải xuất hiện tại nhiều kiểu. Nhưng “công cụ có thể bổ sung” **không tự có nghĩa đó là lựa chọn thiết kế tốt**. Nếu bản thân một kiểu nghiệp vụ thiếu method hoặc invariant thiết yếu, việc dùng aspect để “vá” API có thể che một thiết kế kiểu chưa đầy đủ, gây lỗi compile/runtime khó phát hiện và làm tăng phụ thuộc vào toolchain.

Ví dụ: `Invoice` luôn phải có `invoiceId`, `status` và quy tắc chuyển trạng thái hợp lệ. Nếu API được công bố không cho biết những dữ liệu ấy, dùng một aspect giới thiệu `getStatus()` hoặc external map `Invoice → status` có thể tạo hai nơi giữ sự thật. Khi status là **bất biến nghiệp vụ cốt lõi**, thường nên sửa **mô hình/kiểu** một cách tường minh.

Ngược lại, nếu cần gắn `traceId` cho nhiều operations khác nhau để theo dõi request mà không thay đổi contract của từng domain type, một aspect quan sát hoặc một scoped context có thể hợp lý hơn.

### Nguyên tắc quyết định

> **Nếu một khả năng là bản chất của đối tượng, thuộc hợp đồng mà mọi client hợp lệ phải biết, hoặc bảo vệ invariant của domain, ưu tiên sửa trực tiếp type/API/implementation. Nếu khả năng chủ yếu là mối quan tâm xuyên suốt, không đổi semantic contract của type, có phạm vi áp dụng và lifecycle rõ, AOP có thể là một giải pháp thích hợp.**

Đây là **heuristic kiến trúc**, không phải luật tuyệt đối. Với code của bên thứ ba, một legacy binary không thể sửa, hoặc hàng trăm kiểu có chung một capability do công cụ weaving hỗ trợ tốt, introduction có thể là sự đánh đổi đúng. Cần đánh giá theo **hợp đồng**, **chi phí**, **mức độ kiểm soát mã nguồn**, **toolchain**, **khả năng kiểm thử** và **vòng đời dữ liệu**.

### Sau bài học, bạn sẽ

1. Phân biệt **sửa trực tiếp class/interface**, **composition/decorator**, **AOP advice** và **structural introduction**.
2. Nhận diện **domain invariant, identity, lifecycle** và operations thuộc bản chất một type.
3. Biết khi nào nên thêm field/method/interface trực tiếp thay vì cất state vào proxy/aspect.
4. Hiểu sự khác nhau giữa kiểu được thấy ở **source/compiler**, **proxy runtime** và **class được weave**.
5. Đánh giá trade-off về LSP, API discoverability, binary compatibility, source compatibility, serialization và migration.
6. Phân tích Java constraints: `final`, `record`, sealed hierarchies, constructors, visibility và generic erasure.
7. Thiết kế phần mở rộng khi không thể sửa source: interface adapter, wrapper, external associations hoặc tool-specific introductions.
8. Tránh việc dùng aspect để **che lỗi thiết kế**, **vượt quyền**, **nhân side effects** hoặc lưu state sai scope.
9. Chạy một ví dụ Java 17 đối chiếu **class trực tiếp implements capability** với **proxy thêm interface**, và giải thích stdout.
10. Áp dụng ma trận quyết định, bài tập và checklist review cho một hệ thống thực.

**Phạm vi:** Kiến thức Aspect/AOP cơ bản và nâng cao **nói chung**. Không đồng nhất cú pháp, tính năng hoặc precedence của một engine với tất cả công cụ AOP.

## 2. Bốn cách giải một yêu cầu “thêm khả năng cho đối tượng”

Giả sử hệ thống có `Document` với `read()` và `save()`. Yêu cầu mới: mỗi document có `revisionId()`, đồng thời mọi lần `save()` cần được đo latency.

Có bốn hướng thiết kế chính:

| Cách | Điều thay đổi | Điểm mạnh | Rủi ro/giới hạn |
| --- | --- | --- | --- |
| **Sửa class/interface trực tiếp** | API và implementation của loại đối tượng. | Type contract rõ với compiler/IDE; dễ bảo vệ invariant. | Cần sở hữu source, versioning/migration. |
| **Composition/decorator** | Tạo wrapper implement abstraction tương ứng. | Tường minh, dễ test, không cần weaving engine. | Caller phải dùng wrapper; khác identity/type gốc. |
| **Advice tại join point** | Hành vi quanh các operations đã có. | Hợp cho logging, tracing, metrics, policy crosscutting. | Không mặc định thêm member vào type; call path có thể bypass. |
| **Structural introduction/mixin** | Bổ sung interface/method/state ở mức engine hỗ trợ. | Có thể áp dụng capability thống nhất cho nhiều types. | Cú pháp/toolchain/type view, conflicts và lifecycle phức tạp. |

Không nhất thiết chọn **chỉ một**. Một thiết kế thường hiệu quả là:
- `revisionId` là **dữ liệu cốt lõi** → sửa domain type hoặc một interface typed.
- `save()` latency metric là **cross-cutting observation** → dùng advice hoặc instrumentation thích hợp.
- Một compatibility adapter cho đối tác không sửa được → wrapper hoặc introduction khi cần.

### 2.1. “Mở rộng bằng aspect” có nhiều nghĩa

Một cơ chế chỉ có before/after/around advice **không đồng nghĩa có structural introductions**. Một JDK dynamic proxy có thể tạo object implements interface bổ sung, nhưng **không sửa class target**. Một generated subclass có members mới chỉ thay subtype, không thay base declaration. Một weaver có thể thay class artifact khi hỗ trợ, nhưng vẫn chịu Java/JVM/build constraints.

Trước khi ra quyết định, phải trả lời: **công cụ đang thêm gì, vào object/class nào, ở compile time hay runtime?**

### 2.2. “Sửa thiết kế kiểu trực tiếp” không chỉ là thêm một getter

Có thể cần:
- Định nghĩa một interface nghiệp vụ chính xác hơn.
- Tách một class có nhiều trách nhiệm.
- Đưa state/invariant về đúng aggregate owner.
- Dùng value object thay chuỗi/số rời rạc.
- Tạo constructor/factory bảo đảm trạng thái hợp lệ.
- Chỉnh generic bounds, error model hoặc lifecycle API.
- Tái cấu trúc inheritance thành composition.
- Công bố sealed subtypes/variant states rõ ràng.

Nếu vấn đề là domain model thiếu ranh giới, thêm method bằng weaving vẫn chưa sửa **mô hình khái niệm**.

## 3. Dấu hiệu mạnh cho việc sửa type trực tiếp

### 3.1. Thuộc tính là một phần của danh tính nghiệp vụ

Một `Customer` cần `customerId`, một `Payment` cần `paymentId`, một `Order` cần `orderId`. Nếu hệ thống chỉ hợp lệ khi mỗi instance đều có định danh đó, nó thường nên là **field hoặc value object trong domain contract**, không phải dữ liệu được gắn tạm khi một advice chạy.

Vì sao?
- Constructor/factory có thể kiểm tra ID ngay.
- Compiler và tests nhìn thấy ID requirement.
- Serialization/database mapping có thể được thiết kế thống nhất.
- Mọi call path (bao gồm direct target) đều thấy cùng nguồn sự thật.
- Không phụ thuộc proxy instance và registry riêng để tra ID.

Một correlation ID cho telemetry thì khác: nó mô tả **lượt xử lý**, không nhất thiết thuộc identity bền vững của từng domain entity.

### 3.2. Method mới diễn đạt operation nghiệp vụ chính

Nếu `Account.transferTo(...)` có quy tắc số dư, idempotency và atomicity, việc “giới thiệu” method bằng aspect có thể che logic quan trọng và chuyển trách nhiệm ra khỏi aggregate/service có quyền kiểm soát invariant.

Nếu method là operation chính mà mọi caller cần hiểu, nên đưa nó vào **public API** phù hợp rồi có implementation/test rõ. Advice có thể bổ trợ tracing, metrics hay policy xung quanh operation ấy, không thay thế domain method.

### 3.3. Invariant cần đúng ở mọi đường truy cập

Ví dụ một `InventoryItem` phải luôn thỏa `reserved <= available`. Nếu chỉ interceptor trên proxy mới bảo vệ điều kiện này, một direct target call hoặc self-invocation không đi qua proxy có thể phá invariant.

Khi invariant là **bắt buộc** cho mọi thay đổi state, đặt validation/update vào đúng owner/trusted boundary. Một aspect có thể quan sát hoặc thực thi thêm policy, nhưng không nên là nơi duy nhất duy trì sự thật nội tại của đối tượng nếu interception coverage chưa được chứng minh.

### 3.4. Compiler phải bảo đảm khả năng sử dụng

Một API mới cần mọi consumer thấy `Auditable.auditId()` và được compiler kiểm tra. Nếu source của type có thể sửa, việc implements interface rõ ràng thường giảm rủi ro hơn phụ thuộc một quá trình weave/generation mà IDE/test và production có thể dùng artifact khác nhau.

Nếu chỉ proxy runtime implements `Auditable`, biến gốc `Document d` không tự có `auditId()`; caller phải cast hoặc được trả typed adapter. **Compile-time visibility** là một yêu cầu kiến trúc thực, không chỉ sự tiện lợi của IDE.

### 3.5. State có lifecycle/phụ thuộc phức tạp

Một introduced field giữ database resource, listener, mutable buffer hoặc task controller cần khởi tạo và dọn đúng thời điểm. Constructor, factory, `close()`, owner và invariants có thể được thể hiện rõ hơn khi state thuộc class/component được định nghĩa tường minh.

Nếu aspect state lives ở registry global `Map<Target,State>`, registry có thể giữ target lâu hơn dự kiến. Nếu state ở handler, hai wrappers cùng target có thể có hai states, dẫn tới phân tán nguồn sự thật.

## 4. Dấu hiệu cho phép ưu tiên aspect

Một aspect phù hợp khi các điều kiện sau phần lớn đúng:
- Concern áp cho **nhiều kiểu hoặc nhiều join points** theo một quy tắc rõ ràng.
- Concern chủ yếu là **cross-cutting observation** hoặc một policy được công bố.
- Nó không biến đổi invariant cốt lõi hay đòi thêm thuộc tính bắt buộc trong domain schema.
- Interception mechanism bao phủ đúng các paths cần xử lý.
- Behavior có thể kiểm thử độc lập và kiểm tra integration với target.
- State chủ yếu immutable/shared an toàn hoặc local mỗi invocation.
- Không phụ thuộc vào một interface được introduced nhưng compiler/consumer không biết.
- Có chính sách rõ khi advice throw, skip, retry, short-circuit hoặc async.
- Build/deploy pipeline cho introduction/weaving được kiểm soát nếu sử dụng.
- Lợi ích giảm duplication lớn hơn coupling với framework/toolchain.

Các ví dụ điển hình:
- Đo thời gian của nhiều operations.
- Thêm correlation ID vào logs trong scope đã xác thực.
- Thu thập per-attempt và per-logical metrics.
- Audit invocation theo policy được công bố.
- Truyền một concern orthogonal qua nhiều components, khi API domain không nên mang các tham số chỉ phục vụ instrumentation.
- Giới thiệu một **capability hẹp** cho nhiều đối tượng legacy khi không thể sửa source và runtime/toolchain được kiểm soát.

### 4.1. Aspect không nhất thiết phải “vô hình”

Authorization, retry hay fallback có thể **đổi hành vi** một cách hợp lệ nếu API/architecture đã công bố policies tương ứng. Nhưng nếu policy bắt buộc, nó cần được bảo vệ ở boundary đáng tin và test các đường bypass, không chỉ giả định mọi lời gọi qua proxy.

### 4.2. Có thể có sự phân công hợp lý

~~~text
Domain type:
  business identity
  invariants
  state transitions
  primary APIs

Cross-cutting aspect:
  tracing
  metrics
  optional structured audit
  consistent policy at verified boundaries

Infrastructure adapter:
  protocol conversion
  serialization
  external dependency mapping
~~~

Sơ đồ là gợi ý phân chia trách nhiệm, không phải tuyên bố một kiến trúc duy nhất cho mọi ứng dụng.

## 5. “Bản chất của đối tượng” và “bản chất của một lần sử dụng”

Hai tình huống dễ nhầm:

**Tình huống A — Mỗi hóa đơn có số định danh phát hành:** Đây là dữ liệu của **invoice entity**. Nếu ID thay đổi khi tạo proxy khác, có thể là lỗi. Nó nên nằm trong hợp đồng type hoặc một owner bền vững.

**Tình huống B — Mỗi request thao tác hóa đơn có trace ID:** Đây là dữ liệu của **invocation/logical operation**. Một invoice có thể bị đọc bởi nhiều requests song song với trace IDs khác nhau. Lưu `currentTraceId` trong invoice field hay aspect per-target field là sai scope.

| Câu hỏi | Thuộc về type/domain | Thuộc về invocation/context |
| --- | --- | --- |
| Hai requests trên cùng object cần cùng giá trị? | Thường có, tùy property. | Thường không. |
| Giá trị cần serialize/persist lâu dài? | Có thể có. | Thường không. |
| Constructor/factory phải validate? | Thường có. | Root context entry thường validate. |
| Có liên quan nghiệp vụ khi không có AOP? | Có. | Có thể chỉ phục vụ observability. |
| Có cần đọc trên direct-target calls? | Thường có. | Phụ thuộc instrumentation coverage. |
| Có thể do nhiều threads cập nhật đồng thời? | Cần domain synchronization. | Cần per-call/task isolation. |

Đôi lúc **một audit ID bền vững** thuộc entity, còn **trace ID của lần audit** thuộc flow. Đặt tên rõ để tránh đưa một concern xuyên suốt thành field nghiệp vụ hoặc ngược lại.

## 6. Interface introduction không tự sửa một API thiếu hợp đồng

Giả sử type chỉ có:

~~~java
interface Reader {
    String read(String key);
}
~~~

Đến một ngày mọi caller **bắt buộc** cần owner ID của dữ liệu để thực thi quyền. Nếu chỉ dùng một proxy thêm `Owned.ownerId()`, thì:
- Target gốc vẫn không có contract `Owned`.
- Caller giữ target có thể bỏ qua proxy.
- Hai proxies có thể trả owner IDs khác nhau nếu handler sở hữu state.
- Compiler không bảo đảm mọi `Reader` đều có owner ID.
- Một code path dùng plain `Reader` không nhận được type guarantee.
- Owner ID từ untrusted input trong handler có thể bị spoof.

Nếu quyền sở hữu là **đặc tính bắt buộc của mọi Reader trong domain**, hãy cân nhắc định nghĩa:
- `OwnedReader extends Reader`, có `ownerId()`.
- Một `record OwnerId(...)`/value object validated.
- Factory chỉ trả loại đảm bảo owner.
- An explicit authorized operation boundary.
- Domain tests không phụ thuộc interception.

Một aspect có thể cung cấp tracing/audit cho `OwnedReader` nhưng không nên là nguồn duy nhất của thuộc tính domain bắt buộc.

### 6.1. Khi nào introduction interface lại hợp lý?

Một integration tới thư viện bên thứ ba có rất nhiều objects, và app chỉ cần gắn một **capability phụ** để instrumentation, không thể sửa source. Nếu proxy hoặc weaver có hợp đồng rõ và mọi consumer được cung cấp đúng typed objects, introduction có thể giảm boilerplate.

Ví dụ một `TraceTagged` marker dùng để nhận diện wrapper tại instrumentation boundary có thể phù hợp; nhưng đừng dùng marker đó thay authorization hoặc dữ liệu nghiệp vụ cần tồn tại trên target thật.

### 6.2. Không gọi proxy interface introduction là “target đã implements”

Với JDK dynamic proxy, chỉ object proxy implements các interfaces cấu hình. Target gốc không tự thay định nghĩa class và không xuất hiện field mới chỉ nhờ handler. Nếu một weaver thực sự thay class artifact, behavior/reflective visibility có thể khác; phải xác minh engine và build pipeline ấy.

## 7. Trường hợp bổ sung field: đừng tạo hai nguồn sự thật

Một tình huống nguy hiểm:

~~~text
Order.class:
    status = SHIPPED

Aspect Registry:
    Map<Order,Status> shadowStatus
    shadowStatus[order] = PAID
~~~

Ai sở hữu status? Một caller đọc `order.status` thấy SHIPPED, một introduced getter trả PAID. Không có cách diễn giải nhất quán nếu không định nghĩa rõ một hệ là authoritative và cơ chế đồng bộ được bảo đảm.

### 7.1. Field nên ở nơi invariant được thực thi

Nếu status điều khiển `ship()`, `cancel()`, `refund()`, logic transition thường nên nằm trong domain owner, không bị chia giữa field gốc và registry external chỉ để aspect có thể “thêm thuộc tính”.

### 7.2. Khi external association có thể phù hợp

Một external association `Target → MetricsBucket` có thể hợp lý khi:
- Metrics chỉ là metadata phụ.
- Không ảnh hưởng business methods.
- Mapping đúng identity và bounded lifecycle.
- Owner remove entries khi target hết hạn.
- Không giữ strong keys/values khiến target bị giữ vô hạn.
- Concurrent updates được xử lý.

Đây là **observability state**, không phải một field domain mới bắt buộc.

### 7.3. Per-proxy state khác per-target state

Hai wrappers bọc cùng một target có thể có hai fields riêng. Nếu yêu cầu “mọi references tới target có một thuộc tính thống nhất”, state nằm trong handler **không tự đáp ứng**. Sửa type trực tiếp có thể giúp thống nhất owner nếu source được kiểm soát; nếu không, cần canonical adapter hoặc registry identity có cleanup và documented semantics.

## 8. Thay method trực tiếp khi logic cần được đọc và bảo trì như domain code

Một method bổ sung mang tính nghiệp vụ đáng kể có:
- Input validation.
- Transaction boundaries.
- State transition.
- Error types.
- Idempotency guarantees.
- Side effects trên hệ ngoài.
- Compensating action hoặc undo behavior.
- Persistence/schema contract.

Việc triển khai nó bằng introduction/weaving có thể hữu ích về kỹ thuật, nhưng tăng độ khó:
- Người đọc source `Account` không thấy `transfer()`.
- Refactoring IDE chưa chắc hiểu ITD.
- Unit test trên target chưa weave khác integration runtime.
- Build phải theo toolchain tương ứng.
- Method name/signature có thể conflict khi domain thêm member sau này.
- Lỗi linkage xuất hiện nếu client/runtime artifacts lệch.

Khi domain source nằm trong quyền kiểm soát và khả năng cần lâu dài, thường nên đưa method vào class/service đúng trách nhiệm, có API docs và tests. Aspect ở ngoài đo performance/audit theo hợp đồng.

### 8.1. Một method chỉ là tiện ích thuần?

Nếu method mới là helper pure để format ID, hãy cân nhắc:
- Utility function.
- Value object method.
- Interface default method.
- Extension qua composition/adapter.

Structural AOP có thể là quá nhiều hạ tầng cho một helper đơn giản.

### 8.2. Một method cross-cutting nhưng yêu cầu data source là domain?

Ví dụ `validatedCopy()` áp cho nhiều value objects. Nếu invariant của từng type khác nhau, một aspect cố “giới thiệu” generic validation có thể thiếu thông tin. Có thể tốt hơn là một `Validatable` typed contract với implementation rõ trên mỗi type, hoặc một service validator được đăng ký tường minh. AOP chỉ hợp lý nếu crosscutting logic thật sự uniform.

## 9. LSP và khả năng thay thế: việc thêm member vẫn cần đúng hành vi

**Liskov Substitution Principle (LSP)** nhắc rằng thay implementation/đối tượng tại một abstraction boundary không được làm client tuân thủ hợp đồng trở nên sai.

Nếu proxy implements `PaymentPort` và một interface phụ:
- `PaymentPort.charge()` vẫn phải giữ result, exception và side-effect semantics.
- Không được tự retry non-idempotent operations ngoài contract.
- Không được biến failure thành `false`/0 giả thành công.
- `equals`, `hashCode`, identity và `getClass()` có thể khác target; nếu caller được quyền phụ thuộc, proxy không là thay thế hoàn toàn.
- `synchronized(proxy)` và `synchronized(target)` là hai monitors khác khi đó là hai objects.
- Async result/stream resource lifecycle phải giữ đúng lời hứa.

### 9.1. Compatibility theo abstraction khác “hoàn toàn giống”

Một proxy có thể thay target hợp lệ khi caller chỉ dùng interface, nhưng thất bại khi caller cast sang concrete target class. Một weaver giữ target identity có thể thay lỗi/return nếu advice có mutation. Không đồng nhất **type assignability** với **behavioral substitutability**.

### 9.2. Xác định semantic ownership

Nếu một property quyết định postcondition, ai chịu trách nhiệm bảo đảm đúng?
- Target/domain owner.
- Adapter có contract rõ.
- Một external service giữ authoritative state.
- AOP introduction được kiểm chứng.

Đừng để hai thành phần cùng tuyên bố authoritative mà không có consistency contract.

## 10. Ràng buộc của Java type system và JVM

### 10.1. Final classes/methods

`final class` không thể bị subclass theo Java bình thường, còn `final method` không thể bị override ở subclass. Nhưng một final class có thể được **bọc bằng interface proxy hoặc decorator** nếu có abstraction thích hợp. Không suy rằng final cấm mọi hình thức AOP.

### 10.2. Records

Java record là một class có cấu trúc data carrier được khai báo và implicitly final. Nó rất phù hợp cho **value objects bất biến** mang state cốt lõi với invariant ở compact constructor. Một proxy subclass record không phải hướng hợp lệ theo luật Java thông thường.

Nếu muốn record có thêm business property bền vững, thường nên sửa **record components/schema** và migration cần thiết, thay vì giữ một shadow field của aspect. Cũng phải cân nhắc compatibility: thay record components ảnh hưởng constructors, accessors, equality, serialization.

### 10.3. Sealed hierarchies

Một sealed interface/class kiểm soát các permitted subtypes. Không thể giả định runtime-generated proxy được phép implement/seal-hierarchy type mới bất kỳ. Nếu cần mở rộng domain variants, sửa permits/subtype design trực tiếp là lựa chọn rõ khi nắm source.

### 10.4. Constructors và initialization

Bổ sung field cần một thời điểm initialization. Với type trực tiếp, constructor/factory/initializer có thể bảo đảm invariant. Với weaving/introduction, cần hiểu cơ chế khi instance được tạo, lazy vs eager, và việc constructor target có thực sự được can thiệp hay không. Một interface proxy được dựng **sau khi target tồn tại** không đưa field của handler vào constructor target.

### 10.5. Generics/erasure

Hai methods `process(List<String>)` và `process(List<Integer>)` trong cùng Java class có cùng erasure và không thể coexist như overload thông thường. Một introduction có thể gây name clash dù source aspect và target nhìn khác nhau. Nên để compiler/type system kiểm soát sớm bằng API rõ, hoặc test toolchain biến đổi chính xác.

### 10.6. Classloading và binary artifacts

Java runtime type identity gồm class name và defining loader. Class source chưa weave và binary đã weave là những API views khác nhau; client compile với artifact đã weave nhưng production nạp class chưa weave có thể gặp linkage failure.

Không mặc định runtime agent có thể thêm method/field/interface tùy ý vào **class đã load**: structural class redefinition bị giới hạn. Cần kiểm tra giai đoạn compile/load và JVM tool support. Nếu source có thể sửa trực tiếp, build consistency thường dễ quản lý hơn.

## 11. Một ví dụ Java 17: một capability bắt buộc nên nằm trong API chính thức

Tưởng tượng ví điện tử có:
- `Wallet.credit(amount)`: tăng số dư đúng amount khi amount hợp lệ.
- `Wallet.balance()`: đọc số dư.
- `Auditable.auditId()`: cho biết định danh audit bền vững.

Nếu audit ID thật sự là **một phần hợp đồng của ví**, ta muốn compiler biết implementation phải cung cấp nó. Có thể định nghĩa `AuditedWallet extends Wallet, Auditable` và yêu cầu các factories/API trả loại ấy.

Một cách khác là bọc một `Wallet` bằng proxy implements thêm `Auditable`. Proxy đó có audit ID, nhưng target gốc không có capability ấy. Điều này hợp lý cho metadata của wrapper; không tương đương với bổ sung identity bền vững cho mọi object ví.

### 11.1. Thiết kế trực tiếp: type diễn đạt lời hứa

~~~text
AuditedWallet extends Wallet, Auditable
          |
          +-- DirectWallet implements AuditedWallet

Caller nhận AuditedWallet
   -> compiler biết balance(), credit(), auditId()
   -> constructor bảo đảm auditId hợp lệ
   -> mọi phương thức trong DirectWallet nhìn cùng state
~~~

### 11.2. Thiết kế proxy: capability chỉ tồn tại trên wrapper

~~~text
PlainWallet implements Wallet
           ^
           | delegates
Proxy implements Wallet, Auditable
           + auditId từ handler
           + credit/balance chuyển tiếp vào PlainWallet

Caller giữ PlainWallet gốc: không có Auditable.
Caller giữ proxy: có Auditable.
~~~

Cả hai mô hình có thể hữu ích, nhưng cần mô tả đúng ai sở hữu contract.

## 12. Java 17 runnable: sửa kiểu trực tiếp so với thêm interface bằng proxy

Chương trình dưới đây mô phỏng hai cách. Nó không dùng một weaver và **không sửa class target bằng AOP**; JDK dynamic proxy chỉ giới thiệu giao diện phụ **trên proxy object**.

Tạo file **`DirectTypeVsAspectDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class DirectTypeVsAspectDemo {
    interface Wallet {
        int credit(int amount);
        int balance();
    }

    interface Auditable {
        String auditId();
    }

    interface AuditedWallet extends Wallet, Auditable { }

    static final class DirectWallet implements AuditedWallet {
        private final String auditId;
        private int balance = 10;

        DirectWallet(String auditId) {
            if (auditId == null || auditId.isBlank()) {
                throw new IllegalArgumentException("audit ID required");
            }
            this.auditId = auditId;
        }

        @Override
        public int credit(int amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("negative amount");
            }
            balance += amount;
            return balance;
        }

        @Override
        public int balance() {
            return balance;
        }

        @Override
        public String auditId() {
            return auditId;
        }
    }

    static final class PlainWallet implements Wallet {
        private int balance = 10;

        @Override
        public int credit(int amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("negative amount");
            }
            balance += amount;
            return balance;
        }

        @Override
        public int balance() {
            return balance;
        }
    }

    static Wallet addAuditInterface(Wallet target, String id) {
        return (Wallet) Proxy.newProxyInstance(
                Wallet.class.getClassLoader(),
                new Class<?>[] { Wallet.class, Auditable.class },
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "AuditedWalletProxy";
                            default -> throw new UnsupportedOperationException(
                                    method.getName());
                        };
                    }
                    if (method.getDeclaringClass() == Auditable.class) {
                        return id;
                    }
                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                }
        );
    }

    static void client(Wallet wallet) {
        System.out.println("CLIENT credit=" + wallet.credit(5));
        System.out.println("CLIENT balance=" + wallet.balance());
    }

    public static void main(String[] args) {
        AuditedWallet direct = new DirectWallet("D-7");
        PlainWallet target = new PlainWallet();
        Wallet proxy = addAuditInterface(target, "P-8");

        System.out.println("DIRECT auditable=" + (direct instanceof Auditable));
        System.out.println("PROXY auditable=" + (proxy instanceof Auditable));
        System.out.println("TARGET auditable=" + (target instanceof Auditable));
        System.out.println("PROXY sameTarget=" + (proxy == target));
        System.out.println("PROXY concrete=" + (proxy instanceof PlainWallet));

        System.out.println("DIRECT id=" + direct.auditId());
        System.out.println("PROXY id=" + ((Auditable) proxy).auditId());
        client(direct);
        client(proxy);

        System.out.println("DIRECT finalBalance=" + direct.balance());
        System.out.println("TARGET finalBalance=" + target.balance());

        try {
            direct.credit(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("DIRECT error=" + ex.getMessage());
        }
        try {
            proxy.credit(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("PROXY error=" + ex.getMessage());
        }

        try {
            new DirectWallet("");
        } catch (IllegalArgumentException ex) {
            System.out.println("DIRECT invariant=" + ex.getMessage());
        }

        System.out.println("DIRECT classImplements="
                + Auditable.class.isAssignableFrom(DirectWallet.class));
        System.out.println("TARGET classImplements="
                + Auditable.class.isAssignableFrom(PlainWallet.class));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DirectTypeVsAspectDemo.java
java DirectTypeVsAspectDemo
~~~

**Stdout kỳ vọng:**

~~~text
DIRECT auditable=true
PROXY auditable=true
TARGET auditable=false
PROXY sameTarget=false
PROXY concrete=false
DIRECT id=D-7
PROXY id=P-8
CLIENT credit=15
CLIENT balance=15
CLIENT credit=15
CLIENT balance=15
DIRECT finalBalance=15
TARGET finalBalance=15
DIRECT error=negative amount
PROXY error=negative amount
DIRECT invariant=audit ID required
DIRECT classImplements=true
TARGET classImplements=false
~~~

### 12.1. Điều quan trọng nhất của thiết kế trực tiếp

`DirectWallet` **implements `AuditedWallet`**. Compiler đòi implementation cho `auditId()`, `credit()`, `balance()`; constructor reject ID trống. Một caller chỉ nhận `AuditedWallet` có thể dựa vào capability ấy theo type contract.

Không có hidden registry nào giữ audit ID riêng. Mọi lời gọi trên object trực tiếp đều có thể đọc property `auditId` do class định nghĩa, không phụ thuộc caller đã bọc proxy hay chưa.

### 12.2. Điều quan trọng nhất của proxy

`proxy instanceof Auditable` đúng, nhưng `target instanceof Auditable` sai. `proxy` và `target` khác references; runtime proxy cũng không phải `PlainWallet`. Audit ID `P-8` nằm trong closure của handler, không nằm trong field của `PlainWallet`.

Nếu một module khác giữ `target` gốc, nó không thể ép target sang `Auditable` như một hợp đồng. Đây là lý do interface introduction qua wrapper không thể **tự thay thế** việc sửa type khi domain yêu cầu mọi target có ID.

### 12.3. Tại sao cùng kết quả credit chưa chứng minh cùng design?

Cả `client(direct)` và `client(proxy)` in `credit=15, balance=15`; interface `Wallet` được thực hiện đúng ở các lời gọi đã test. Nhưng design khác ở:
- Compile-time type guarantee.
- Constructor invariant của audit ID.
- Reflection trên target class.
- Object identity/concrete assignability.
- Quyền sở hữu audit state.
- Khả năng caller đi vòng qua proxy.

**Behavioral substitutability trong `Wallet` không đồng nghĩa structural equivalence của `AuditedWallet`.**

### 12.4. Vì sao lỗi `credit(-1)` vẫn đúng?

Target/direct implementations ném `IllegalArgumentException`. Handler proxy gọi `Method.invoke`, catch `InvocationTargetException` và rethrow cause; caller nhìn thấy đúng loại lỗi và message `negative amount`.

Một proxy muốn được coi là compatible phải giữ **return type + semantics + error contract** của operations gốc, không chỉ thêm interface phụ.

### 12.5. Vì sao reflection cuối chương trình quan trọng?

`Auditable.class.isAssignableFrom(DirectWallet.class)` là true vì direct class implements interface. `Auditable.class.isAssignableFrom(PlainWallet.class)` là false vì class gốc không thay đổi.

Một proxy cũng là runtime class có khả năng implements `Auditable`, nhưng đó là class generated **khác**. Nếu tool weaving thực sự thay target artifact, reflection trên transformed target có thể khác; demo này không kiểm điều đó.

### 12.6. Giới hạn có chủ đích

- Demo tuần tự; `balance` mutable không thread-safe khi concurrent calls.
- Handler được viết cho two non-overlapping interfaces; signature clashes cần handling khác.
- Audit ID của proxy chỉ là immutable closure, không phải policy xác thực.
- Proxy không chặn internal method calls của `PlainWallet` nếu target tự gọi chính nó.
- Không có persistence/serialization, transaction hoặc class bytecode weaving.
- Không khẳng định một interface proxy có thể implement arbitrary sealed interface; ở đây interfaces thông thường.
- Không dùng `AuditedWallet` làm kiểu của proxy vì proxy được tạo implements `Wallet,Auditable` chứ không implements `AuditedWallet` trực tiếp. Hai interfaces có đủ methods về mặt cấu trúc **không** tự có nghĩa object implement subinterface `AuditedWallet` theo nominal typing của Java. Nếu muốn proxy `instanceof AuditedWallet`, phải khai interface ấy trong danh sách proxy và xử lý methods tương ứng.

**Bài học từ code:** Nếu invariant “mọi ví được cấp phát đều có audit ID” là quan trọng, thiết kế **factory trả `AuditedWallet`** là một ràng buộc chặt hơn việc “có thể bọc bất kỳ `Wallet` nào và cast khi cần”.

## 13. Chỗ nào là vấn đề của type, chỗ nào là của aspect?

Một cách phân chia các yêu cầu cụ thể:

| Yêu cầu | Lựa chọn ban đầu | Lý do |
| --- | --- | --- |
| Mỗi `Invoice` phải có `invoiceId` không null. | Sửa type/constructor/schema. | Invariant nội tại. |
| Mỗi `Order` có `cancel()` với state transitions. | Sửa domain method/service. | Business operation cần ownership. |
| Mọi method public đều có duration metric. | Aspect/instrumentation. | Concern quan sát xuyên nhiều types. |
| Ghi trace ID theo từng HTTP request. | Scoped context + instrumentation. | Per-operation, không phải per-entity state. |
| Bổ sung `Auditable` cho 100 third-party objects. | Adapter/proxy hoặc ITD nếu tool phù hợp. | Source không thể sửa; capability phụ. |
| Khóa cache theo tenant và data type. | Explicit cache policy/adapter, aspect khi verified. | Cần trusted scope và semantics dữ liệu. |
| Một field `balance` phải nhất quán với ledger. | Domain aggregate/state owner. | Không tạo shadow state trong aspect. |
| Chặn truy cập khi chưa xác thực. | Trusted security boundary, có thể hỗ trợ bằng advice. | Policy bắt buộc, không được bypass. |
| Enforce unique order numbers trên storage. | DB/domain consistency mechanism. | Aspect interception không tự đảm bảo transaction/concurrency. |
| Ghi audit sau commit thành công. | Transaction event/outbox/audit boundary rõ. | After method return không luôn bằng after commit. |
| Mọi entity cần value equality. | Sửa value type/equality design. | Identity semantics cốt lõi. |
| Tự động thêm helper `debugLabel()`. | Utility/default method/decorator, cân nhắc aspect nếu rất rộng. | Đừng over-engineer. |

**Lưu ý:** Những lựa chọn này là điểm bắt đầu; điều kiện domain, legacy constraints và tool support có thể thay đổi kết luận. Quan trọng là diễn đạt **vì sao**.

## 14. Tính phát hiện của API và trải nghiệm phát triển

Một method xuất hiện trực tiếp trong class/interface được Java compiler, IDE, static analyzer, documentation generator và test doubles nhìn thấy theo các conventions thông thường.

Một method introduced bằng AOP có thể vẫn được tooling hỗ trợ tốt nếu engine có compiler integration, nhưng sẽ đòi:
- Plugin/processor/weaver được chạy đúng phiên bản.
- Generated source hoặc class artifact cùng build.
- Source-to-binary mapping.
- Annotation metadata và scopes nhất quán.
- Dependency locking và upgrade policy.
- Hướng dẫn debug stack traces/transformed code.

### 14.1. Điều gì xảy ra với người mới đọc code?

Nếu người đọc thấy `invoice.approve()` trong source, tìm class `Invoice` nhưng không thấy method và không biết weaving config, chi phí hiểu code tăng. Nếu operation quan trọng, đưa nó vào API trực tiếp có thể tăng khả năng bảo trì đáng kể.

### 14.2. Hidden coupling

Một aspect introduce `getTenant()` cho nhiều classes có thể tạo một mối phụ thuộc ngầm: những callers tưởng type có capability tự nhiên nhưng chỉ đúng khi module AOP đã được kích hoạt. Khi chạy unit test target **không weave**, code có thể không compile hoặc throw.

Nếu capability là thật sự bắt buộc, tài liệu hóa hợp đồng dependency và cân nhắc sửa type/adapter thay vì tạo hidden type requirement.

### 14.3. “Giảm boilerplate” không phải mục tiêu cao nhất

Loại bỏ 20 getters được copy có ích, nhưng nếu khiến build khó reproduce và tests chậm, lợi ích có thể không đủ. Ngược lại, một code-generation/toolchain được kiểm soát với structural introductions cho hàng nghìn types có thể đem lại lợi ích lớn. Đánh giá chi phí vòng đời, không chỉ dòng mã giảm.

## 15. Khi không sửa được source: thứ tự ưu tiên các phương án

Không có quyền sửa class không đồng nghĩa AOP luôn là giải pháp duy nhất.

### 15.1. Adapter typed, composition trước

Nếu third-party `ExternalRecord` không có `auditId()`, tạo:

~~~java
interface AuditedRecord {
    String auditId();
    ExternalRecord source();
}
~~~

Rồi có implementation adapter giữ reference tới target và ID được cấp hợp lệ. Caller nhận đúng `AuditedRecord`, không cần tưởng target class đã bị thay. Đây là API typed, reflection rõ và đơn vị ownership được công bố.

### 15.2. Mixin/introduction khi coverage lớn

Nếu hàng trăm types phải có cùng capability và công cụ hỗ trợ introduction chuẩn, có thể cân nhắc mixin:
- Tool có hỗ trợ generated implementation cho method mới?
- State instance hay shared?
- Những concrete types nào được chọn?
- Runtime/classloaders nào chịu phép biến đổi?
- Reflection/serialization/compatibility có cần nhìn introduced member?
- Conflict khi một target version mới tự thêm method trùng?
- Consumer compile-time view có nhận được type?
- Có thể rollback mà không phá client binary?

Nếu các câu trả lời có được chứng cứ tốt, AOP introduction có thể là lựa chọn đúng.

### 15.3. External registry khi metadata không phải domain state

Một map ID → metrics / weak association có thể hỗ trợ tracing/correlation khi không nên thay domain model. Nhưng tránh giữ target objects qua strong references trong registry sống dài. Bảo đảm eviction/cleanup, thread safety, cardinality budget và tenant isolation.

### 15.4. Bytecode weaving khi không có lựa chọn khác

Weaving có thể phù hợp cho một structural concern quan trọng mà source không thể sửa, nhưng cần:
- Kiểm soát compile/load stages và classloaders.
- Tests với artifact đã weave thật.
- Chiến lược versioning khi target library nâng cấp.
- Failure mode khi transformation không được áp dụng.
- Đánh giá security, legal/supportability và toolchain ownership.

Không sử dụng weaving chỉ để tránh một wrapper tường minh nếu wrapper đã đáp ứng API.

## 16. Source, binary và behavioral compatibility khi sửa trực tiếp

“Sửa type trực tiếp” không phải lựa chọn luôn không rủi ro. Một thay đổi class/interface phải được thiết kế versioning.

### 16.1. Source compatibility

Thêm abstract method vào interface có thể buộc các implementers đang có phải viết method mới khi compile lại. Thay generic bounds hoặc overloads có thể tạo ambiguity. Thêm constructor required parameter làm code cũ không compile.

Những điều này không luôn xấu: compiler báo nơi code phải thay có thể giúp **phát hiện sớm** những consumer chưa tuân hợp đồng mới, tốt hơn lỗi xuất hiện muộn do một introduction ẩn.

### 16.2. Binary compatibility

Không phải mọi source-compatible change đều binary-compatible và ngược lại. Ví dụ thay method/field descriptor, return type, interface hierarchy, constructor hoặc class artifact có thể ảnh hưởng linkage. Phải áp dụng luật Java binary compatibility cụ thể và test binaries từ version cũ.

Với weaving, cần test đặc biệt trường hợp client compile với class có introduced method nhưng runtime nạp class **chưa weave**: có thể lỗi như `NoSuchMethodError`. Không giả định compile success bảo đảm deployment đúng.

### 16.3. Behavioral compatibility

Ngay cả khi compile/link:
- Result có đúng domain meaning?
- `null` và `Optional.empty()` có phân biệt?
- Exceptions có đúng type/cause?
- Side effects có xảy ra đúng số lần?
- Identity, equality, hashCode và resource ownership có đúng?
- Async completion, cancellation và streams có giữ contract?
- New fields có initialized đúng?

Một introduced member và một method sửa trực tiếp **đều** cần behavioral tests.

### 16.4. Migration plan

Khi thay schema/model:
1. Thêm API hoặc value object mới.
2. Thiết kế backfill/default cho dữ liệu cũ.
3. Update persistence/schema và serializers.
4. Sửa callers theo staged rollout.
5. Thêm contract tests cho version cũ/mới.
6. Deprecate API cũ khi đã có migration path.
7. Rollback được nếu deployment không đồng bộ.

Nếu introduction từng che một thuộc tính thiếu, migration còn cần **lấy state từ proxy/registry** đưa về domain authoritative store, xác định conflicts và dữ liệu có thể bị mất khi wrapper hết vòng đời.

## 17. Serialization, reflection và đồng nhất dữ liệu

### 17.1. Khi field được thêm vào model

Các cơ chế serialization khác nhau có thể đọc fields/getters/annotations. Thêm field trực tiếp có thể đổi JSON/schema/Java serialization; không được tự suy mọi serializers sẽ tự tương thích. Nhưng việc thay đổi được **thể hiện trong source và migration** giúp consumer nhận biết sớm hơn.

### 17.2. Khi field chỉ nằm trong proxy handler

`target.getClass().getDeclaredFields()` không thấy field của handler. ORM/serializer đọc target có thể không lưu property mới. Nếu property cần persistence, một accessor trên wrapper chưa đủ nếu persistence layer tiếp tục lưu target gốc.

### 17.3. Reflection trên generated vs transformed type

Một interface proxy và target có runtime classes khác nhau. Source/bytecode weaving có thể tạo artifact chứa members mới, nên reflection trên target đã weave có thể thấy. Cần kiểm **đối tượng nào** và **artifact nào**, không chỉ tên interface.

### 17.4. Equality và identity

Nếu domain entity là value object có equality dựa trên ID, một proxy object riêng có thể có equality/hashCode không tương thích. Nếu map key thay target bằng proxy, behavior có thể đổi. Khi identity thuộc domain contract, hãy thiết kế trực tiếp hoặc giữ canonical adapter rõ, không cố giả lập reference equality bằng `equals()`: toán tử `==` không thể thay đổi cho hai objects riêng.

## 18. Các trường hợp nên tránh “dùng aspect làm sửa thiết kế”

### 18.1. Dùng advice để vá một null invariant

Nếu `User.email` được hứa non-null, đừng chờ advice tại `sendMail` rồi thay null bằng `"unknown"` để tiếp tục. Hãy validate ở constructor/factory/domain boundary và sửa dữ liệu sai. Advice chỉ là điểm instrumentation hoặc policy phụ.

### 18.2. Tự phát sinh field status bên ngoài aggregate

Đặt `WorkflowState` trong singleton aspect map không biến state thành đúng source of truth của domain. Hai object wrappers hoặc một direct target caller có thể quan sát khác nhau.

### 18.3. Dùng pointcut để bù việc thiếu interface chính thức

Nếu mọi consumer cần `Closeable` semantics, hãy đưa `close()`/ownership contract vào abstraction hoặc adapter. Một advice “đóng resource cuối method” có thể đóng sai nếu method trả stream/future cần resource sau khi return.

### 18.4. Dùng around advice để “thêm transaction” khi boundary bị phân mảnh

Có thể áp transaction-like policy ở crosscutting boundary, nhưng transaction semantics phụ thuộc resource manager, propagation, commit/rollback và async lifecycle. Nếu core invariant về atomic state không tồn tại ở layer đáng tin, chỉ bổ sung around advice quanh một method không chứng minh transaction đúng.

### 18.5. Tự “sửa lỗi” bằng fallback giá trị mặc định

Method trả `int`, aspect bắt mọi exception rồi `return 0`; code vẫn đúng primitive type nhưng có thể biến storage error thành “đếm thành công bằng 0”. Sửa error model/typed result hoặc chính sách fallback công khai thay vì che lỗi.

### 18.6. Cho aspect retry một command không idempotent

Một command gửi tiền có thể commit trước timeout; retry có thể nhân side effects dù caller cuối nhận `OK`. Phải sửa operation contract/idempotency hoặc tạo trusted retry boundary, không coi retry chỉ là option của aspect.

### 18.7. Gắn property per-request vào per-target aspect state

Một target singleton nhận nhiều requests song song; `aspect.currentTraceId` mutable field có thể bị overwrite. Nếu concern là request, sử dụng per-invocation/task context đúng scope; không sửa domain type để chứa trace ID hiện hành.

### 18.8. Chỉ kiểm thử proxy, bỏ qua direct calls và self-invocation

Một interceptor ở proxy có thể không thấy `this.internalMethod()`. Nếu validation/security invariant buộc chạy ở mọi internal path, sửa domain/trusted boundary thay vì tin mọi join point qua handler.

## 19. Quy trình ra quyết định có thể áp dụng ngay

### Bước 1 — Xác định capability có phải domain contract không

Viết một câu:

> “Mọi instance của type X phải ___, kể cả khi không có instrumentation hay proxy.”

Nếu điền được một invariant không tùy chọn, nghiêng về **sửa trực tiếp type, API hoặc domain owner**.

### Bước 2 — Xác định khách hàng sử dụng capability

Ai cần dùng?
- Compiler của các modules khác?
- Serializer/ORM?
- External APIs?
- Những clients chỉ giữ interface?
- Reflection/annotations?
- Một logger hoặc profiler?
- Một security boundary?

Nếu nhiều consumers chính thức cần contract, explicit interface thường hữu ích hơn hidden introduction.

### Bước 3 — Hỏi ai sở hữu dữ liệu và lifecycle

Capability có state nào?
- Persistent domain field?
- Per-target transient metadata?
- Per-proxy handler state?
- Per-invocation timestamp?
- Shared metrics counter?
- External resource?

Sở hữu sai state có thể nguy hiểm hơn việc viết thêm vài dòng code trong domain type.

### Bước 4 — Kiểm tra quyền sửa source và ràng buộc kỹ thuật

- Có thể sửa source class/interface không?
- Type thuộc vendor/legacy binary?
- Class là `final`/`record`/sealed?
- Tool hỗ trợ structural introduction đến mức nào?
- Compiler/runtime cùng thấy introduced API?
- Có nhiều classloaders, AOT/native hoặc hot reload?

Không đưa ra quyết định AOP khi chưa biết những điều kiện này.

### Bước 5 — Xác định mức độ thay đổi hành vi

- Chỉ quan sát?
- Có thêm method gọi từ caller?
- Có thay result/exception?
- Có short-circuit/caching?
- Có retry làm target chạy nhiều lần?
- Có effects lên concurrency, resource lifetime hoặc transaction?

Nếu thay đổi semantics domain, yêu cầu contract explicit và tests ở owner phù hợp.

### Bước 6 — So sánh chi phí vòng đời

Ước lượng/định tính:
- Dòng code boilerplate giảm.
- Toolchain/deployment complexity tăng.
- Debuggability/IDE support.
- Performance overhead.
- Test matrix.
- Risk khi nâng version library.
- Số types/join points chịu tác động.
- Rollback compatibility.

### Bước 7 — Chọn phương án đơn giản nhất giữ hợp đồng

Một thứ tự cân nhắc hữu ích, **không phải luật bắt buộc**:

1. **Sửa trực tiếp type/domain boundary** nếu đây là contract cốt lõi và có source.
2. **Composition/adapter** nếu không sửa source hoặc muốn cô lập integration.
3. **Advice** cho concern crosscutting trên join points đã tồn tại.
4. **Structural introduction/weaving** nếu thực sự cần capability áp đồng loạt vào nhiều types và toolchain hỗ trợ nhất quán.

Chọn theo nguyên nhân, không theo “công cụ hiện đang tiện”.

## 20. Scorecard để review một quyết định kiến trúc

Mỗi tiêu chí trả lời Có/Không hoặc Mức độ và kèm bằng chứng:

| Tiêu chí | Nghiêng sửa trực tiếp | Nghiêng aspect/introduction |
| --- | --- | --- |
| Thuộc tính là bất biến domain? | Có. | Không/metadata phụ. |
| Compiler phải bảo đảm mọi instance có capability? | Có. | Không, chỉ scoped wrapper. |
| Cần persistence/serialization chính thức? | Có. | Chỉ telemetry tạm. |
| Nhiều consumers cần gọi member trực tiếp? | Có. | Chỉ instrumentation module. |
| Thực hiện invariant tại mọi call path? | Có. | Verified interception đủ. |
| Source target do nhóm sở hữu? | Có. | Không, third-party. |
| Concern áp hàng trăm unrelated types? | Có thể, nhưng cần cân nhắc. | Có thể rất phù hợp. |
| Có state nhiều bước, transaction, lifecycle phức tạp? | Ưu tiên owner tường minh. | Chỉ nếu tool/owner có contract rõ. |
| Phụ thuộc toolchain weaving làm tăng rủi ro? | Ưu tiên source/API. | Chỉ dùng khi lợi ích đủ lớn. |
| Behavior chỉ quan sát và ít state? | Không bắt buộc sửa type. | Rất phù hợp. |
| Capability dễ biểu diễn bằng typed adapter? | Adapter có thể tốt hơn. | Introduction không bắt buộc. |
| Nhiều proxy cùng target phải dùng cùng state? | Domain/shared authoritative owner. | Per-target association nếu kiểm soát tốt. |

### 20.1. Không cộng điểm máy móc

Một invariant security “không được bypass” có thể quan trọng hơn mười mục hiệu năng hay boilerplate. Scorecard là công cụ thảo luận, không phải hàm số tự động chọn framework.

### 20.2. Ví dụ quyết định

“Thêm `Currency currency()` cho mọi money value” — đây có thể là tính chất cốt lõi của **Money** và liên quan phép tính. Nên sửa value object/API thay vì đem currency vào aspect state.

“Gắn trace baggage đã sanitize cho những method calls trong workflow” — metadata per-flow, áp rộng, thường không nên sửa mọi domain type; dùng context/instrumentation phù hợp.

“Gắn `Tagged` cho objects của library không thể sửa trong debug environment” — typed decorator/proxy hoặc introduction là lựa chọn thực tế, miễn tài liệu nói rõ wrapper mới sở hữu tag.

## 21. Ma trận kiểm thử trước khi quyết định và sau khi triển khai

### 21.1. Nếu sửa type trực tiếp

- Constructor/factory reject invalid state.
- Every implementation của interface đáp ứng contract.
- State transition invariant đúng trên success/error.
- Compile-time tests cho consumer API.
- Legacy data migration/serialization compatibility.
- Multithreaded use đúng theo thread-safety contract.
- Value equality/hashCode/identity semantics rõ.
- APIs async/lazy result vẫn giữ ownership.
- Rollback/deprecation plan và versioning.

### 21.2. Nếu dùng adapter/proxy/introduction

- `proxy instanceof IntroducedInterface` đúng khi được công bố.
- `target instanceof IntroducedInterface` đúng hoặc sai **theo cơ chế thực**, không suy đoán.
- Original operations giữ result/error/side effects.
- Introduced operations có implementation và initialization.
- Two proxies one target: state per-proxy/per-target đúng requirement.
- Direct target call có chính sách hợp lệ nếu bypass proxy.
- Self-invocation có được interceptor bao phủ không?
- Reflection `getClass()/getDeclaredMethods()` đúng artifact.
- Source compiler thấy các introduced methods khi cần.
- Runtime classloader nạp đúng transformed binary.
- Conflicts, overloads, generics erasure và sealed/final restrictions được test.
- Serialization schema và resource cleanup hợp lệ.
- Concurrent state không race; shutdown/async completion đúng.
- Security policy không phụ thuộc interface marker chưa xác thực.

### 21.3. Nếu dùng advice cho concern crosscutting

- Pointcut thực sự chọn đúng occurrences.
- Advice order được khai báo và xác minh.
- Short-circuit có bỏ qua policy bắt buộc không?
- Return/exception transparency hoặc transformation được công bố.
- Logging/metrics failure không che target failure nếu best-effort.
- `ThreadLocal` restore/remove ở success/error.
- Retry budget/idempotency/timeout đúng.
- Async stage completion được quan sát tại đúng thời điểm.
- Context/registry không giữ args/results/targets vô hạn.
- Performance overhead và p99 được đo dưới tải.

### 21.4. Golden-path test không đủ

Một proxy có thể pass `credit(5)` mà fail `credit(-1)`; một adapter có thể đúng với một instance nhưng sai khi hai wrappers bọc cùng target; một Weaver có thể hoạt động trong IDE và thiếu ở production. Các negative paths chính là bằng chứng của architectural correctness.

## 22. Những hiểu lầm phổ biến

**“Công cụ hỗ trợ introduction thì luôn nên dùng thay sửa class.”** Không. Khả năng kỹ thuật không thay thế quyết định về domain contract.

**“Sửa class trực tiếp là trái tinh thần AOP.”** Không. AOP bổ sung khả năng tổ chức cross-cutting concerns, không có mục tiêu thay mọi domain/API design.

**“Một interface proxy thêm capability nghĩa là target gốc đã implements.”** Không với JDK delegating proxy.

**“Per-proxy field là per-target field.”** Không nếu hai wrappers cùng target giữ hai states độc lập.

**“Sửa source lúc nào cũng dễ, không cần migration.”** Không. API/schema/versioning có thể gây breaking changes.

**“Method introduced không xuất hiện trong class source nhưng tất cả compilers đều nhìn thấy.”** Không. Tùy compiler/weaver/build artifact.

**“Một method mới có thể overload chỉ bằng return type khác.”** Không trong Java source thông thường.

**“Final class cấm mọi proxy và mọi AOP.”** Không. Final cấm subclass theo Java, không cấm bọc qua interface hay các cách instrumentation hợp lệ.

**“Aspect là nơi thích hợp để giữ trạng thái domain vì nó dùng chung nhiều lớp.”** Không nếu nó tạo nguồn sự thật thứ hai hoặc sai lifecycle.

**“Có `instanceof Auditable` là caller được phép audit/reset.”** Không. Type capability không thay thế authorization.

**“Log đầy đủ nghĩa là đã thực thi audit nghiệp vụ bền vững.”** Không. Logging/transaction commit/audit persistence là những events khác nhau.

**“Chỉ cần priority advice là mọi direct call cũng được kiểm soát.”** Không. Ordering không sửa proxy bypass.

**“Một proxy có cùng API sẽ có cùng identity/equality/class.”** Không mặc định.

**“Return type hợp lệ chứng minh postcondition.”** Không; result và side effects phải đúng.

**“Retry chỉ làm code chịu lỗi tốt hơn.”** Có thể lặp side effects; cần domain idempotency.

**“Chi phí introduction chỉ là CPU runtime.”** Còn build, security, migration, classloader, debugging, testing và vận hành.

**“Đưa traceId vào mọi domain entity là rõ ràng hơn scoped context.”** Không nếu traceId thuộc invocation, không phải entity.

**“Tách mọi thứ sang aspect làm type nhỏ hơn nên tốt hơn.”** Type nhỏ vì thiếu invariant có thể là **thiết kế kém rõ**, không phải clean design.

## 23. Bài tập vận dụng kèm lời giải

### Bài 1 — Order status

Mọi `Order` phải giữ trạng thái đã commit và kiểm quy tắc `NEW → PAID → SHIPPED`. Nên dùng field trong aspect hay domain type?

**Đáp án:** Nghiêng sửa domain state machine/type trực tiếp hoặc owner authoritative, vì status là invariant nghiệp vụ. Advice có thể quan sát transitions.

### Bài 2 — Trace ID

Một request đọc hàng chục domain objects; cần gắn trace ID vào logs của mỗi call. Nên thêm `traceId` vào mọi entity?

**Đáp án:** Không mặc định. Trace ID thuộc logical operation; dùng scoped context và instrumentation được propagate/cleanup đúng.

### Bài 3 — Library không sửa được

Một `Reader` từ third-party cần capability `Tagged` chỉ cho diagnostic tools. Có thể dùng cách nào?

**Đáp án:** Typed adapter/JDK interface proxy implements `Tagged`, hoặc introduction nếu tool phù hợp. Công bố tag thuộc wrapper hay target association.

### Bài 4 — Hai wrappers một target

P1 và P2 cùng bọc target T; mỗi handler có field `auditId`. P1 trả A, P2 trả B. Nếu auditId phải là định danh bền vững của T thì sai ở đâu?

**Đáp án:** State owner sai scope. Cần một nguồn authoritative theo T hoặc đưa vào domain API, không per-proxy.

### Bài 5 — Compiler guarantee

Một method `getCurrency()` được add chỉ bằng runtime proxy nhưng mọi consumer có biến type `Money` cần gọi nó. Điều gì thiếu?

**Đáp án:** Compile-time type contract; cần sửa Money interface/value object hoặc trả một typed adapter được công bố.

### Bài 6 — Retry charge

Aspect tự retry `charge()` sau timeout, attempt đầu có thể đã commit. Caller cuối nhận `OK`. Có an toàn chỉ vì result đúng type?

**Đáp án:** Không. Cần idempotency/transaction policy, vì side effects có thể nhân đôi.

### Bài 7 — Record

Muốn mọi `Money` record có `currency` bắt buộc. Có nên giữ currency trong external map của aspect?

**Đáp án:** Nếu currency thuộc giá trị tiền, nghiêng sửa record components/schema và migration, giữ invariant tại constructor/factory.

### Bài 8 — Sealed interface

Một sealed hierarchy chỉ cho phép hai implementations. Có thể tự tạo runtime proxy implement interface ấy mà không cần xét permitted subtypes?

**Đáp án:** Không. Java sealed type rules áp dụng; cần xem thiết kế hierarchy và runtime/tool capability.

### Bài 9 — Reflection

Proxy có `auditId()`, nhưng `target.getClass().getDeclaredMethods()` không thấy method đó. Là lỗi introduction?

**Đáp án:** Không nếu mục tiêu là interface introduction trên proxy. Nó sẽ là lỗi expectation nếu tài liệu từng hứa field/method được weave vào target class.

### Bài 10 — Một helper nhỏ

Chỉ một class cần method `displayName()` thuần túy. Có nên cài structural weaving pipeline?

**Đáp án:** Thường không. Method trực tiếp, utility hoặc decorator có thể đơn giản và dễ bảo trì hơn.

### Bài 11 — Auth policy

Một proxy mới có `resetState()` public và hiện mọi caller có proxy có thể gọi. Cần gì?

**Đáp án:** Authorization/validation ở trusted boundary, negative tests; một interface marker không phải permission.

### Bài 12 — Compiler thấy method nhưng runtime không có

Client compile với artifact đã weave, deployment lại nạp binary chưa weave. Có thể xuất hiện vấn đề gì?

**Đáp án:** Lỗi linkage/cast/behavior tùy call site, chẳng hạn `NoSuchMethodError`. Đồng bộ artifact/toolchain và test production bundle.

### Bài 13 — Async stream

Một introduced method trả `Stream<Row>`. Advice đóng resource ở `finally` khi method return. Có thể sai vì sao?

**Đáp án:** Stream có thể lazy, caller chưa tiêu thụ; resource ownership cần sống tới khi stream/caller đóng theo contract.

### Bài 14 — State đồng thời

Một introduced field `int accesses` tăng bằng `++` trên target singleton được 100 threads gọi. Per-target có bảo đảm đúng không?

**Đáp án:** Không; cùng target vẫn shared giữa threads. Dùng atomic/synchronization phù hợp.

### Bài 15 — Source sửa được nhưng nhiều lớp

Có 200 domain classes đều cần `toAuditLabel()`. Nên mặc định dùng introduction?

**Đáp án:** Chưa đủ dữ liệu. Nếu label chỉ phụ trợ và uniform, mixin/introduction/utility có thể phù hợp; nếu mỗi type có semantics khác và công bố API, interface typed với implementations hoặc adapter có thể rõ hơn. Đánh giá toolchain và test cost.

## 24. Checklist cuối trước khi quyết định

1. **Hợp đồng:** Capability mới có phải bản chất của type/domain không?
2. **Invariant:** Có bắt buộc đúng với mọi target, kể cả direct calls?
3. **Identity:** Property thuộc target, wrapper hay invocation?
4. **State owner:** Có một nguồn sự thật duy nhất không?
5. **Compiler:** Consumer có cần nhìn API mới lúc compile không?
6. **Source control:** Có quyền sửa class/interface target không?
7. **Scope:** Concern áp cho vài types hay hàng trăm join points?
8. **Tool support:** Engine có method/field/interface introduction thực hay chỉ advice?
9. **Mechanism:** Proxy, decorator, generated subclass hay woven target artifact?
10. **Class constraints:** `final`, record, sealed, constructors, modules?
11. **Conflict:** Existing methods/fields, generics erasure, default methods?
12. **API:** Có phương án interface/adapter/value object đơn giản hơn?
13. **Contracts:** Return/error/side effects có giữ đúng?
14. **Security:** Trusted boundary và authorization có bị bypass?
15. **Concurrency:** Shared/per-target state có thread-safe, visibility đúng?
16. **Async:** Task completion/streams/resource lifecycle rõ?
17. **Persistence:** State mới có cần lưu, backfill và schema migration?
18. **Reflection:** Consumer/tool có nhìn đúng class và annotations không?
19. **Binary:** Client runtime nạp đúng transformed artifact không?
20. **Build:** Toolchain ổn định, version locked, debug được?
21. **Tests:** Có contract tests cho direct target, wrapper, woven và negative paths?
22. **Observability:** Metrics/trace IDs đặt đúng scope chưa?
23. **Performance:** Contention, allocation, memory retention, p99?
24. **Maintenance:** Người đọc source có tìm thấy behavior quan trọng không?
25. **Rollback:** Có thể rollback mà không phá client phụ thuộc introduced member?

## Kết luận

**Khi một khả năng là hợp đồng cốt lõi của domain, cần được compiler xác nhận, bảo vệ invariant ở mọi đường gọi, hoặc có state/persistence/lifecycle quan trọng, ưu tiên sửa trực tiếp type, interface, value object hoặc owner nghiệp vụ.** Cách này biến yêu cầu thành điều kiện tường minh mà caller, IDE và tests đều nhìn thấy, thay vì một dependency ẩn vào cấu hình aspect.

**Khi yêu cầu là cross-cutting concern, áp rộng cho nhiều loại, không làm biến dạng semantics cốt lõi và interception coverage đã được xác minh, AOP có thể giảm duplication và tách instrumentation rất tốt.** Với thư viện không thể sửa, adapter/proxy/introduction là lựa chọn thực dụng, miễn nói rõ capability nằm trên wrapper hay class đã weave, state thuộc ai, clients thấy API ở giai đoạn nào và có cơ chế cleanup/compatibility phù hợp.

Đừng đánh đồng **“có thể thêm bằng aspect”** với **“nên thêm bằng aspect”**. Một kiến trúc tốt đặt **hợp đồng nghiệp vụ tại nơi dễ nhìn và kiểm chứng nhất**, rồi dùng Aspect/AOP để bổ sung những mối quan tâm thật sự xuyên suốt — thay vì che đi một mô hình kiểu cần được sửa.


</details>

- [Quay lại đầu trang](#back-to-top)
