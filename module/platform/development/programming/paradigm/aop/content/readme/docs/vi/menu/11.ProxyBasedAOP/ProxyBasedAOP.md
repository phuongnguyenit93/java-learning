<a id="back-to-top"></a>

# AOP qua proxy

## Menu
- [Mô hình đối tượng trung gian tiếp nhận và chuyển tiếp lời gọi](#proxy-based-aop-001)
- [Ủy quyền qua giao diện so với tạo lớp con trung gian](#proxy-based-aop-002)
- [Đối tượng trung gian và đối tượng đích có danh tính khác nhau thế nào](#proxy-based-aop-003)
- [Giới hạn của lời gọi nội bộ không đi qua proxy](#proxy-based-aop-004)
- [Ảnh hưởng của phương thức riêng, phương thức tĩnh và thành phần không thể ghi đè](#proxy-based-aop-005)
- [Đối tượng được tạo ngoài cơ chế quản lý proxy](#proxy-based-aop-006)
- [Chuỗi interceptor và cơ chế gọi tiếp](#proxy-based-aop-007)
- [Khác biệt về phạm vi can thiệp giữa proxy và biến đổi mã](#proxy-based-aop-008)
- [Chẩn đoán tình huống lời gọi không kích hoạt advice](#proxy-based-aop-009)

## <a id="proxy-based-aop-001">Mô hình đối tượng trung gian tiếp nhận và chuyển tiếp lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Mô hình đối tượng trung gian tiếp nhận và chuyển tiếp lời gọi trong AOP qua proxy

**Proxy** là một đối tượng trung gian đứng giữa **bên gọi (caller/client)** và **đối tượng đích (target/real subject)**. Bên gọi thực hiện lời gọi qua proxy; proxy có cơ hội quan sát, kiểm tra, đo đạc, thay đổi cách điều phối hoặc quyết định có **chuyển tiếp lời gọi** tới target hay không. Đây là một cách tổ chức hành vi cắt ngang trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)** mà không buộc phương thức nghiệp vụ phải chứa trực tiếp mã ghi log, kiểm tra quyền hoặc đo thời gian.

Ý tưởng cốt lõi:

~~~text
Caller
  |
  | gọi qua một giao diện/hợp đồng
  v
Proxy (đối tượng trung gian)
  |-- before: ghi nhận/kiểm tra/đo thời điểm bắt đầu
  |-- chọn tiếp tục hay short-circuit
  |
  +---- nếu tiếp tục ----> Target (đối tượng nghiệp vụ)
  |                             |
  |<--- kết quả hoặc ngoại lệ ---+
  |
  |-- after-success / after-error / finally
  v
Caller nhận kết quả hoặc ngoại lệ
~~~

**Proxy không nhất thiết giống target về danh tính đối tượng.** Hai bên có thể cùng thực hiện một giao diện nhưng có class/runtime identity khác nhau. Mặt khác, **việc phương thức được gọi qua proxy** không tự bảo đảm tất cả join point trong thân target đều bị can thiệp: phạm vi chính xác phụ thuộc vào mô hình proxy, những phương thức được chặn và đường gọi thật.

Bài viết tập trung vào AOP nói chung: mô hình nhận lời gọi–chuyển tiếp, quan hệ giữa proxy và target, lifecycle, advice, pointcut, những tình huống không chuyển tiếp và nguyên tắc bảo toàn hợp đồng. Ví dụ Java 17 dùng **JDK dynamic proxy** để thực thi độc lập, đồng thời làm rõ đó chỉ là **một hiện thực minh họa**, không phải cú pháp hay khả năng mặc định của mọi hệ thống AOP.

## 1. Các vai trò trong mô hình proxy

Một kiến trúc proxy phổ biến có bốn vai trò:

| Vai trò | Nhiệm vụ | Điều cần chú ý |
| --- | --- | --- |
| **Caller/client** | Giữ tham chiếu tới một đối tượng có thể gọi được | Có thể không biết tham chiếu đó đang chỉ proxy |
| **Contract/interface** | Quy định phương thức, kiểu tham số/kết quả và nghĩa vụ | Proxy phải đáp ứng hợp đồng quan sát được |
| **Proxy** | Tiếp nhận lời gọi, thực thi logic can thiệp, chuyển tiếp hoặc xử lý thay thế | Có thể có state, chính sách, cache, thứ tự các interceptor |
| **Target/real subject** | Thực hiện logic nghiệp vụ thực tế | Có thể được tạo trước, tạo lười, lấy từ pool hoặc thay theo cấu hình |

Một proxy thường **thay thế được** target từ góc nhìn những phương thức thuộc hợp đồng giao tiếp đã công bố. Tuy nhiên, điều đó không có nghĩa mọi hành vi như `getClass()`, `==`, identity hash code, serialization hoặc phương thức không thuộc giao diện cũng giống hệt nhau.

**Chuyển tiếp (delegation/forwarding)** là hành động proxy gọi target hoặc một handler/interceptor kế tiếp. Một proxy có thể có trước/sau logic, nhưng cũng có thể trả dữ liệu cache, từ chối bằng ngoại lệ hoặc điều phối tới target khác nếu hợp đồng cho phép.

### 1.1 Proxy nằm ở ranh giới lời gọi, không phải được chọn bằng tên gọi

Nếu bên gọi có biến kiểu giao diện:

~~~java
Catalog catalog = obtainCatalog();
int price = catalog.price(10);
~~~

Đây là **mảnh code minh họa**; `obtainCatalog` giả định tồn tại. `catalog` có thể tham chiếu trực tiếp tới `CatalogImpl` hoặc tới một proxy cũng triển khai `Catalog`. Trong cả hai trường hợp, code phía caller dùng cùng lời gọi `price(10)`, nhưng trường hợp proxy còn đi qua lớp can thiệp.

Ngược lại, nếu caller vẫn giữ tham chiếu tới target gốc và gọi trực tiếp target, lời gọi ấy có thể **bỏ qua proxy**. Kiểm soát đường tham chiếu và nơi các đối tượng được cung cấp cho client vì vậy quan trọng không kém logic của handler.

## 2. Luồng một lời gọi từ đầu đến cuối

Giả sử gọi `service.execute("A")` qua proxy và advice chỉ ghi nhận trạng thái.

**Bước 1 — caller phát sinh lời gọi.** JVM dispatch lời gọi tới object proxy theo giao diện hoặc kiểu proxy.

**Bước 2 — proxy nhận thông tin.** Tùy cơ chế, proxy có thể biết `Method`, mảng `args`, chính object proxy, target, metadata và context khác. Đây chưa chắc là toàn bộ thông tin mà một weaver ở method-execution join point có thể nhận.

**Bước 3 — chọn logic can thiệp.** Proxy có thể đối chiếu method, cấu hình policy hoặc một chuỗi interceptor/advice. Nếu cơ chế có pointcut, chỉ những điểm/loại lời gọi phù hợp mới chịu advice tương ứng.

**Bước 4 — xử lý trước.** Ví dụ ghi metric bắt đầu, xác minh đầu vào hoặc kiểm tra quyền trước tác dụng phụ.

**Bước 5 — chuyển tiếp hoặc không chuyển tiếp.** Nếu chuyển tiếp, proxy gọi target hoặc gọi interceptor kế tiếp. Nếu short-circuit, target không chạy qua đường này.

**Bước 6 — nhận kết quả/lỗi.** Chuyển tiếp trả kết quả hoặc ném ngoại lệ; proxy có thể quan sát, chuyển đổi theo hợp đồng hoặc giữ nguyên.

**Bước 7 — kết thúc và trả về caller.** Proxy cần bảo toàn kiểu trả về, hợp đồng ngoại lệ, kết quả, side effects và cleanup phù hợp.

~~~text
Invocation
   |
   v
Proxy handler
   |
   +--> Method/args/context
   |
   +--> Before advice
   |
   +--> Decision?
            |
        +---+---+
        |       |
      proceed  skip
        |       |
        v       v
      Target    cached result / policy exception
        |       |
        +---+---+
            |
            v
      After/error/finally
            |
            v
      Return or throw
~~~

Đó là **sơ đồ ngữ nghĩa**; một cơ chế proxy có thể chuyển tiếp bằng reflection, gọi trực tiếp, method handle hoặc mã được tạo, không bắt buộc dùng cùng các hàm cụ thể.

### 2.1 Điểm phải bảo toàn

Việc “nhận lời gọi và chuyển tiếp” không chỉ là gọi một phương thức có cùng tên. Proxy phải xem xét:

- **Đúng phương thức và overload**: tên, kiểu tham số được khai báo, biến thể gọi và đường dispatch.
- **Đúng thứ tự/giá trị tham số**: `Object[]`, null, boxing/unboxing, varargs.
- **Đúng đối tượng target**: instance phù hợp với thao tác và trạng thái hiện hành.
- **Đúng kết quả**: kiểu trả về, nullability, generic contract, ý nghĩa nghiệp vụ.
- **Đúng ngoại lệ**: lỗi gốc được truyền tiếp hoặc được chuyển đổi theo chính sách, không nuốt ngầm.
- **Tác dụng phụ**: không bỏ qua ghi dữ liệu, gửi sự kiện hoặc phân quyền bắt buộc.
- **Tài nguyên**: cleanup và trạng thái giao dịch không bị rò.
- **Khả năng truy vết**: ghi số request/attempt, cache hit, lỗi đúng đơn vị.

## 3. Proxy liên hệ với AOP như thế nào?

Trong AOP, **join point** là một điểm chương trình có thể can thiệp; **pointcut** là tập điều kiện lựa chọn điểm; **advice** là hành vi cắt ngang. Proxy là **một kỹ thuật áp dụng một phần các ý tưởng đó** qua một **ranh giới lời gọi**.

Ví dụ ý định:

~~~text
Pointcut concept:
  calls through proxy to Catalog.price(int)

Before:
  emit bounded event "price.query"

Proceed:
  forward to underlying Catalog

After-success:
  record completion

After-error:
  record error category; preserve original exception
~~~

Đây là **giả mã**, không phải cú pháp chung. Một dynamic proxy Java không tự hiểu ngôn ngữ pointcut; người lập trình cần viết logic chọn method hoặc tích hợp một bộ chọn của công cụ AOP riêng.

### 3.1 Call join point và execution join point khác nhau

Ở một số hệ thống AOP, **method call** là điểm phát sinh lời gọi ở phía caller, còn **method execution** là lúc phần thân của phương thức đang chạy. Một proxy nằm ở một ranh giới lời gọi **không tự biến thành bộ weaver method-execution toàn cục**.

Nếu target được gọi trực tiếp, method body vẫn thực thi, nhưng proxy handler có thể **không hề biết lần gọi đó**. Một hệ thống biến đổi bytecode trực tiếp vào execution point có thể quan sát đường khác với một hệ thống chỉ dùng proxy.

Đừng nói “AOP qua proxy dệt vào mọi method execution”. Trong proxy-based AOP, phải xác định **đường nào đi qua proxy**, những method nào có thể chặn và các ngoại lệ như self-invocation.

### 3.2 Static structural matching và runtime checks

Nếu cần pointcut chọn phương thức `price(int)`, proxy có thể kiểm tra `Method.getName()` cùng `getParameterTypes()`; chỉ tên là chưa đủ khi có overload. Sau khi match cấu trúc, có thể dùng `args` hoặc target runtime làm điều kiện động.

~~~text
selected(invocation) =
   matchingSignature
   AND matchingProxyBoundary
   AND optionalRuntimeCondition
~~~

Một tham số được **bind** cho advice không có nghĩa đã dùng tham số đó để **lọc** pointcut. Việc đọc `args[0]` và quyết định `args[0] > 0` là hai thao tác riêng.

## 4. Các kiểu proxy và chiến lược chuyển tiếp

### 4.1 Proxy dựa trên interface

Caller và proxy cùng làm việc qua giao diện. Trong Java, **JDK dynamic proxy** tạo class runtime triển khai những giao diện chỉ định và chuyển lời gọi qua một `InvocationHandler`.

Ưu điểm:
- Dùng API chuẩn JDK.
- Không cần sửa class file của target.
- Dễ gắn interceptor vào các phương thức có mặt trong interface.
- Có thể chọn target runtime hoặc thay handler theo thiết kế.

Giới hạn:
- Chỉ tạo proxy cho **giao diện** được cung cấp, không tạo subclass của một concrete class bất kỳ.
- Phương thức không nằm trong giao diện proxy không tự trở thành phương thức gọi được qua proxy.
- Identity/class của proxy khác target.
- Cần xử lý `equals`, `hashCode`, `toString`, checked exceptions và tham số/kết quả cẩn thận.

### 4.2 Proxy dựa trên subclass

Một số cơ chế tạo **subclass hoặc lớp dẫn xuất** của type đích để ghi đè các phương thức thích hợp và chuyển tiếp qua interceptor. Cách này có thể hỗ trợ lời gọi không được mô tả bằng interface, nhưng bị giới hạn bởi quy tắc ngôn ngữ/runtime: **final class/method, private/static methods, constructor, visibility, module access** và khả năng sinh mã.

Không thể mặc định proxy subclass sẽ chặn mọi lời gọi nội bộ hoặc mọi phương thức: cần biết cách dispatch và cách proxy giữ/delegate tới target, đặc biệt nếu có **hai object riêng** hoặc chỉ dùng một instance được dẫn xuất.

### 4.3 Proxy tĩnh và proxy động

**Proxy tĩnh:** code wrapper được viết hoặc sinh tại build, biết giao diện/các thao tác cụ thể, chuyển tiếp bằng những lời gọi đã được biên dịch.

**Proxy động:** runtime sinh class hoặc handler dựa trên giao diện/kiểu và chính sách cấu hình. Việc tạo lúc chạy không tự có nghĩa handler có thể đổi mọi pointcut tức thời; handler và chuỗi interceptors có thể được chốt khi proxy tạo.

### 4.4 Lazy, remote và virtual proxy

Mô hình trung gian còn có thể dùng để:
- **Lazy initialization:** trì hoãn tạo target tới lần đầu thực sự cần.
- **Remote proxy:** biểu diễn đối tượng ở tiến trình/máy khác; việc chuyển tiếp có thể là network request thay vì local method call.
- **Protection proxy:** kiểm tra quyền trước khi gọi target.
- **Caching proxy:** trả kết quả từ cache khi hợp đồng cho phép.
- **Observability proxy:** metrics, tracing, audit có giới hạn dữ liệu.
- **Retry/fallback proxy:** điều phối nhiều lần thử, có khả năng không gọi hoặc gọi nhiều lần; phải kiểm soát side effect.

Những kiểu proxy này có thể kết hợp với AOP, nhưng **không phải mọi Proxy Pattern đều là một framework AOP**. AOP nhấn mạnh việc áp dụng concern vào các tập join point theo quy tắc; proxy là một cơ chế triển khai có thể dùng cho mục đích ấy.

## 5. Ví dụ Java 17 hoàn chỉnh: nhận, chuyển tiếp và bảo toàn lỗi

Ví dụ này dùng Java `Proxy` và `InvocationHandler`. Nó có:
- Giao diện `Service` gồm `process` và `status`.
- Target `ServiceImpl` thực thi nghiệp vụ.
- Proxy in `ADVICE before`, chuyển lời gọi, in `ADVICE after` trên thành công hoặc `ADVICE error` khi target ném lỗi.
- Một lời gọi **trực tiếp** target để so với lời gọi qua proxy.
- Cách xử lý ba phương thức `Object` để tránh nhận dạng proxy nhầm với target hoặc gây đệ quy khi ghi log.

Lưu toàn bộ dưới tên `ProxyForwardingDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ProxyForwardingDemo {
    interface Service {
        String process(String id, int quantity);
        String status();
    }

    static final class ServiceImpl implements Service {
        @Override
        public String process(String id, int quantity) {
            System.out.println("TARGET process id=" + id
                    + " qty=" + quantity);
            if (quantity < 0) {
                throw new IllegalArgumentException("negative quantity");
            }
            return "ok:" + id + ":" + quantity;
        }

        @Override
        public String status() {
            System.out.println("TARGET status");
            return "READY";
        }
    }

    static Object invokeTarget(
            Service target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Service wrap(Service target) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                (proxy, method, args) -> {
                    Object[] values =
                            args == null ? new Object[0] : args;

                    // Chính sách identity minh họa riêng cho proxy.
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ServiceProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == values[0];
                            default -> throw new UnsupportedOperationException();
                        };
                    }

                    String operation = method.getName();
                    System.out.println("ADVICE before " + operation);

                    try {
                        Object result = invokeTarget(target, method, values);
                        System.out.println("ADVICE after " + operation
                                + " result=" + result);
                        return result;
                    } catch (Throwable error) {
                        System.out.println("ADVICE error " + operation
                                + " " + error.getClass().getSimpleName());
                        throw error;
                    }
                });
    }

    public static void main(String[] args) {
        ServiceImpl original = new ServiceImpl();
        Service proxy = wrap(original);

        System.out.println("META proxyClass="
                + Proxy.isProxyClass(proxy.getClass()));
        System.out.println("META sameTarget=" + (proxy == original));
        System.out.println("META toString=" + proxy);
        System.out.println("META equalsSelf=" + proxy.equals(proxy));
        System.out.println("META equalsTarget=" + proxy.equals(original));

        System.out.println("RESULT " + proxy.process("A", 2));

        try {
            proxy.process("B", -1);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }

        System.out.println("DIRECT");
        System.out.println("RESULT " + original.process("C", 1));
        System.out.println("STATUS " + proxy.status());
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac ProxyForwardingDemo.java
java ProxyForwardingDemo
~~~

Stdout xác định:

~~~text
META proxyClass=true
META sameTarget=false
META toString=ServiceProxy
META equalsSelf=true
META equalsTarget=false
ADVICE before process
TARGET process id=A qty=2
ADVICE after process result=ok:A:2
RESULT ok:A:2
ADVICE before process
TARGET process id=B qty=-1
ADVICE error process IllegalArgumentException
CALLER negative quantity
DIRECT
TARGET process id=C qty=1
RESULT ok:C:1
ADVICE before status
TARGET status
ADVICE after status result=READY
STATUS READY
~~~

### 5.1 Đọc đúng output

**`Proxy.isProxyClass(proxy.getClass())` trả true:** đối tượng trung gian là class dynamic proxy được JVM sinh cho giao diện đã chọn; nó **không phải** cùng object với `ServiceImpl`.

**`proxy == original` là false:** caller và target có thể nhìn khác runtime identity. Proxy cần có chính sách nhất quán về `equals`, `hashCode`, `toString` nếu các method ấy được dùng trong cấu trúc dữ liệu, caching hoặc log. Ví dụ chủ động dùng **identity của proxy** và một tên `toString` cố định. Một proxy production có thể chọn chính sách khác, nhưng phải giữ các bất biến của `equals`/`hashCode`.

**`ADVICE before process` xuất hiện trước `TARGET...`:** lời gọi đi vào handler, chạy logic trước, sau đó `invokeTarget` mới gọi target.

**Khi `quantity=-1`:** target đã được gọi, in một dòng, rồi ném `IllegalArgumentException`. `Method.invoke` bọc ngoại lệ nghiệp vụ trong `InvocationTargetException`; code tháo lớp bọc để truyền đúng lỗi gốc. Handler in `ADVICE error` và không in `ADVICE after` thành công.

**Nhánh `DIRECT`:** gọi `original.process("C",1)` trực tiếp không đi qua proxy; chỉ có log target, không có `ADVICE`.

**`status()`:** cũng nằm trên interface proxy, nên cũng được chặn bởi handler. Demo không đặt pointcut lọc tên method, vì thế mọi method interface phù hợp đều vào logic advice.

### 5.2 Giới hạn của chương trình

- Đây là **mô phỏng advice qua dynamic proxy**, không tự dệt bytecode vào target, không cài một ngôn ngữ pointcut phổ quát.
- Handler **được gọi do lời gọi đi qua proxy**; không tự quan sát mọi execution join point trong JVM.
- Ví dụ không kiểm soát danh tính người dùng; không nên biến các dòng `ADVICE` thành cơ chế phân quyền thật.
- Mã ghi result ra console để minh họa; trong thực tế **không nên ghi raw args/result** nếu có thông tin nhạy cảm.
- Handler trả exception gốc qua `throw error`. Với các **checked exceptions không được giao diện khai báo**, dynamic proxy Java có thể bọc thành `UndeclaredThrowableException`; cần thiết kế hợp đồng ngoại lệ phù hợp.
- Nếu đối số hoặc kết quả kiểu primitive được biểu diễn qua `Object` trong proxy, phải bảo đảm boxing/unboxing hợp lệ; `null` cho kiểu primitive có thể gây `NullPointerException` lúc unbox.
- Mã không xử lý caching, retry hay transaction; các mối quan tâm đó cần hợp đồng và kiểm thử riêng.

## 6. Vì sao Object methods cần chính sách rõ?

Dynamic proxy của Java có thể chuyển những lời gọi `equals(Object)`, `hashCode()`, `toString()` đến invocation handler, dù giao diện nghiệp vụ không khai báo chúng. Vì vậy:

~~~text
proxy.toString()  -> handler
proxy.hashCode()  -> handler
proxy.equals(x)   -> handler
~~~

Nếu handler dùng `System.out.println(proxy)` một cách ngây thơ trong mọi lời gọi, việc in có thể lại gọi `proxy.toString()`, khiến handler vào lại và có nguy cơ đệ quy vô hạn.

Các quyết định cần rõ:

- **Identity-based equality:** proxy chỉ bằng chính nó.
- **Target-based equality:** nếu ủy quyền equals cho target, phải xem liệu `target.equals(proxy)` có cùng kết quả với `proxy.equals(target)` không; đối xứng và `hashCode` có thể bị phá.
- **Stable hash code:** nếu dùng proxy làm khóa map, hash code và equality không được thay tùy tiện sau khi thêm/bớt interceptor.
- **Safe toString:** tránh in target hoặc args nếu có bí mật; chuỗi cố định hoặc thông tin đã kiểm duyệt thường an toàn hơn.

Java `==` luôn so sánh identity tham chiếu cho object, nên không thể khiến `proxy == target` true bằng cách viết lại `equals`.

## 7. Chuyển tiếp lời gọi phải tôn trọng dispatch và kiểu

### 7.1 Method và overload

Chỉ lấy `method.getName()` không đủ để phân biệt:

~~~java
void send(String message);
void send(String message, int priority);
~~~

Đây là **mảnh khai báo**, không phải chương trình riêng. Nếu policy muốn chọn overload thứ hai, phải xét `method.getParameterTypes()` hoặc một dạng signature phù hợp, thay vì match mọi method tên `send`.

Proxy dynamic nhận `Method` liên quan interface, không nhất thiết là `Method` của lớp triển khai cuối cùng. Khi cần đọc annotation, phải xác định metadata được khai báo **ở interface hay target implementation**; annotation phương thức/tham số không tự suy theo kiểu như cách kế thừa lớp thông thường.

### 7.2 Varargs, null và generics

- Một phương thức `send(String... values)` biểu diễn varargs bằng một tham số mảng trong chữ ký; `args` không mặc định chứa mỗi phần tử vararg thành một mục riêng.
- Phương thức không tham số có thể cung cấp `args == null` ở JDK dynamic proxy; ví dụ đã chuẩn hóa thành mảng rỗng.
- Phần tử args có thể là `null`, không được luôn gọi `getClass()` trên nó.
- Primitive được boxing vào `Object[]` khi đi qua InvocationHandler.
- Generics Java trải qua type erasure; một số runtime generic checks không hiện ra như `Class<List<String>>` riêng.
- Compiler có thể sinh bridge method để bảo toàn override; đừng suy số method Reflection thấy bằng số join point hoặc số lần advice.

### 7.3 Ngoại lệ

Reflection `Method.invoke` bọc exception từ method đích bằng `InvocationTargetException`. Chuyển tiếp raw wrapper ấy không nhất thiết đáp ứng hợp đồng mà caller kỳ vọng.

Những lựa chọn:
1. Tháo lớp bọc và ném lại **nguyên nhân gốc** nếu hợp đồng cho phép.
2. Ánh xạ có chủ đích sang loại lỗi API/miền nghiệp vụ phù hợp, giữ `cause`.
3. Ghi nhận lỗi bằng mã/nhóm an toàn; không vô tình nuốt lỗi rồi trả thành công giả.

Đối với checked exceptions, còn phải xem khai báo `throws` trên giao diện. Handler có thể ném `Throwable`, nhưng proxy không buộc caller chấp nhận một checked exception mới ngoài hợp đồng.

## 8. Giới hạn đường gọi: proxy không quan sát được mọi lần thực thi

Proxy chỉ nhận invocation thực sự đi qua ranh giới mà nó kiểm soát. Ba trường hợp cần phân biệt:

| Đường gọi | Handler của wrapper proxy có nhận? | Lý do |
| --- | --- | --- |
| `caller → proxy.process()` | Có, nếu method được expose và handler phù hợp | Proxy là receiver của lời gọi ngoài. |
| `caller → target.process()` | Không | Caller đang giữ tham chiếu gốc. |
| `caller → proxy.outer() → target.outer() → this.inner()` | Chỉ nhận `outer` theo mẫu delegating proxy | Lời gọi `inner` phát sinh trên target, không quay về wrapper. |

Trong mẫu subclass proxy **chạy thân method trên chính receiver subclass**, một `this.inner()` có virtual dispatch đến override tương ứng; kết quả có thể khác. Bytecode weaving vào method execution cũng có thể chặn một đường gọi nội bộ nếu class thực sự đã được dệt. Bởi vậy không biến giới hạn của wrapper proxy thành kết luận cho mọi AOP.

Constructor, static, private và final methods còn chịu giới hạn khác nhau theo cách intercept. Nếu nghiệp vụ yêu cầu một policy không thể bỏ qua, phải xác định **toàn bộ đường gọi được phép**, không chỉ thấy một lời gọi mẫu qua proxy thành công.

## 9. Nhiều lớp proxy và thứ tự advice

Có thể ghép nhiều interceptor:

~~~text
Caller
  |
  v
AuthorizationProxy
  |
  v
TransactionProxy
  |
  v
MetricsProxy
  |
  v
Target
~~~

Trong mô hình lớp bọc lồng nhau, đường **vào** thường là:

~~~text
Authorization.before
Transaction.before
Metrics.before
Business
~~~

Đường **ra** khi thành công thường đảo:

~~~text
Metrics.after
Transaction.after
Authorization.after
~~~

Nhưng nếu một advice **không chuyển tiếp** (cache hit hoặc từ chối), mọi lớp **nằm trong điểm short-circuit** có thể chưa chạy, còn lớp ngoài đã vào vẫn có thể thực thi phần after/finally theo thiết kế.

### 9.1 Phép bọc và thứ tự xây dựng

Nếu code tạo chuỗi theo:

~~~text
chain = Target
chain = Metrics(chain)
chain = Transaction(chain)
chain = Authorization(chain)
~~~

thì `Authorization` ở **ngoài cùng**, dù được thêm **sau cùng**. Thứ tự thêm wrapper không nhất thiết bằng thứ tự BEFORE runtime. Một hệ thống AOP có thể định nghĩa precedence riêng; không lấy thứ tự file/aspect hoặc thứ tự khởi tạo làm bảo đảm nếu không có hợp đồng.

### 9.2 Vấn đề khi cache nằm ngoài authorization

~~~text
Cache(Authorization(Target))
~~~

Một cache hit có thể trả sớm mà không gọi `Authorization`. Nếu dữ liệu phải luôn được bảo vệ, thiết kế ấy có thể gây rò rỉ.

~~~text
Authorization(Cache(Target))
~~~

Ở cấu trúc này, quyền được kiểm tra trước khi cache trả kết quả trong đường gọi thông thường. Vẫn cần chứng minh mọi đường vào được bảo vệ và identity/resource được xác thực từ nguồn đáng tin. **Proxy không tự biến một điều kiện runtime không đáng tin thành chính sách phân quyền.**

### 9.3 Retry, transaction và tác dụng phụ

~~~text
Retry(Transaction(Target))
Transaction(Retry(Target))
~~~

Thứ tự có thể thay đổi ranh giới transaction và số lần target chạy. Một retry proxy không được tự lặp lại thao tác có tác dụng phụ nếu chưa có chính sách idempotency. Một transaction proxy phải kết thúc/rollback đúng khi target hoặc advice bên trong ném ngoại lệ, kể cả khi lớp ngoài bắt và trả fallback.

## 10. Proxy không bắt buộc luôn chuyển tiếp

Một proxy có thể thực hiện logic:

~~~text
proxy.handle(invocation):
    if forbidden:
        throw policyError
    if validCacheHit:
        return cachedValue
    return target.handle(invocation)
~~~

Đây là **giả mã**, không phải cú pháp AOP phổ quát. Hai nhánh đầu là **short-circuit**: không gọi target. Điều này có thể đúng đối với yêu cầu bị từ chối hoặc một **truy vấn chỉ đọc có cache hợp lệ**.

Nhưng nếu phương thức gốc cần **ghi tiền, phát sự kiện, lưu đơn hàng hoặc kiểm toán bắt buộc**, proxy không được tùy tiện trả giá trị thành công giả khi chưa thực hiện hậu điều kiện. Kết quả trả về đúng kiểu là chưa đủ.

Một câu hỏi hữu ích trước mọi short-circuit: **những điều gì mà người gọi có quyền kỳ vọng vẫn phải đúng ngay cả khi target không chạy?**

### 10.1 Cache và tính nhất quán

Nếu cache phụ thuộc người dùng, tenant, phiên bản, quyền hoặc thời gian, khóa cache phải phản ánh các yếu tố liên quan. Trả dữ liệu cache của người khác hoặc đã quá hạn có thể vi phạm hợp đồng và bảo mật.

### 10.2 Lazy target

Một proxy có thể chỉ khởi tạo target ở lần gọi đầu, nhưng phải bảo đảm thread safety, publish object an toàn, xử lý thất bại khi khởi tạo và vòng đời tài nguyên. Nếu hai thread gọi cùng lúc, lazy initialization thiếu đồng bộ có thể tạo nhiều target ngoài ý định.

### 10.3 Remote forwarding

Với remote proxy, target ở tiến trình khác. Khi đó lỗi mạng, timeout, retry, serialization, version protocol và idempotency trở thành phần hợp đồng. Không thể coi lời gọi qua mạng tương đương hoàn toàn với local method call, đặc biệt về latency và lỗi.

## 11. Context: proxy, target, this, args không đồng nghĩa

Một điểm dễ nhầm trong AOP là từ **target** và **this**.

- **Proxy object:** đối tượng nhận lời gọi tại ranh giới proxy.
- **Underlying target:** đối tượng nghiệp vụ handler chuyển tiếp tới.
- **`this` trong thân target instance method:** đối tượng đang chạy phần thân, thông thường là target đó.
- **Caller-side `this`:** đối tượng thực thi phương thức phát sinh lời gọi tại một method-call join point, nếu mô hình AOP có expose.
- **Arguments:** giá trị thực tế truyền cho lần gọi.

Trong JDK `InvocationHandler.invoke(proxy, method, args)`, tham số `proxy` là **chính proxy**, không phải đối tượng caller-side `this`. Target chỉ khả dụng khi handler giữ hoặc giải quyết nó; nó không tự được API đưa vào như tham số độc lập.

Tại **execution của phần thân** khi dùng một loại weaving khác, this/target có thể được mô hình hóa khác với invocation handler. Vì vậy không dùng tên `proxy` để suy ý nghĩa `this` hay `target` của mọi ngôn ngữ pointcut.

## 12. Hiệu năng, khả năng mở rộng và vòng đời

Mỗi lớp proxy có thể gây overhead:
- Dispatch bổ sung và đôi khi boxing/unboxing qua `Object[]`.
- Kiểm tra pointcut hoặc điều kiện runtime.
- Thêm hoạt động logging, tracing, metrics.
- Đồng bộ trạng thái, cache hoặc truy vấn cấu hình.
- Reflection hoặc metadata lookup khi chưa có cache hợp lý.
- Lồng nhiều wrapper và cấp phát đối tượng bổ sung.

Overhead thực tế phụ thuộc JVM/JIT, loại proxy, mức độ nóng của phương thức và logic advice; không thể khẳng định “proxy luôn chậm nhiều” hoặc “không có overhead” mà chưa đo.

### 12.1 Đo đúng ranh giới

Nếu `MetricsProxy` ở **ngoài** lớp Authorization và Cache, nó có thể đo cả nhánh bị từ chối và cache hit. Nếu nằm **trong Cache**, cache hit không đi qua nó. Cần định nghĩa **latency toàn lời gọi** và **latency target** khác nhau.

### 12.2 Trạng thái và thread safety

Một proxy dùng chung giữa nhiều caller có thể chạy song song trên nhiều thread. Nếu handler giữ counters, cấu hình, connection, session hoặc cache mutable, cần quy tắc đồng bộ hoặc cấu trúc dữ liệu thread-safe.

Nếu proxy đọc một policy có thể đổi động, nên dùng **snapshot bất biến trên mỗi invocation** để tránh nửa đầu dùng policy cũ, nửa sau dùng policy mới. Nhưng đổi cấu hình không tự thêm một join point mà proxy không bao phủ.

### 12.3 Rò rỉ dữ liệu khi log args/result

Proxy có thể thấy mảng `args`, kết quả và ngoại lệ; đó là dữ liệu rất nhạy cảm. Không mặc định `Arrays.toString(args)`, `target.toString()` hoặc `error.getMessage()` an toàn. Ưu tiên event schema allowlist và chỉ ghi trường được phê duyệt, tránh secret/token/PII, hạn chế retention và quyền truy cập.

### 12.4 Đệ quy do proxy tự gọi lại

Nếu handler dùng `proxy.process(...)` để “chuyển tiếp” thay vì gọi target, nó có thể quay lại chính handler và lặp vô hạn. Một handler còn có thể đệ quy nếu gọi `proxy.toString()` trong bước logging. Phải xác định **đối tượng tiếp nhận continuation** và cơ chế chống reentrancy của mỗi advice.

## 13. Kiểm thử đúng mô hình trung gian

Một bộ kiểm thử nên chứng minh cả **có chặn đúng** và **không thay đổi hợp đồng ngoài ý định**.

| Trường hợp | Điều cần xác nhận |
| --- | --- |
| Caller gọi qua proxy | Before chạy, target nhận đúng args, after chạy khi thành công |
| Target ném exception | Nhánh error chạy và caller nhận lỗi phù hợp; không in after-success |
| Caller gọi trực tiếp target | Không có advice proxy ngoài dự đoán |
| Proxy `equals`/`hashCode`/`toString` | Không đệ quy, nhất quán với chính sách identity |
| Overload/varargs/null | Handler nhận đúng method, args và xử lý giá trị null |
| Interface/proxy runtime type | Không nhầm declaring type, proxy class với target class |
| Self-invocation | Chỉ khẳng định advice chạy nếu đường nội bộ thực sự đi qua proxy |
| Nhiều proxy | Thứ tự vào/ra và nhánh short-circuit đúng |
| Cache hit | Không gọi target nếu hợp đồng cho phép; vẫn bảo vệ dữ liệu |
| Authorization reject | Target không có tác dụng phụ; audit bắt buộc không bị bỏ qua |
| Checked exception | Giữ loại lỗi hợp đồng, không bọc sai hoặc nuốt lỗi |
| Concurrent callers | Không có race/lost update trong handler/cache |
| Async/remote | Phân biệt lúc trả completion handle với lúc hoàn thành thật |
| Direct/execution weaving comparison | Không suy phạm vi proxy bằng phạm vi bytecode weaving |

### 13.1 Kiểm tra thứ tự bằng trace

Thay vì chỉ nhìn hai dòng BEFORE/AFTER, có thể trong test gom event:

~~~text
expected:
  proxy.before
  target.enter
  target.return
  proxy.after
  caller.receive
~~~

Với nhánh lỗi:

~~~text
expected:
  proxy.before
  target.enter
  target.throw
  proxy.error
  caller.catch
~~~

Đó là **trình tự giả định của wrapper đã định nghĩa**. Một cơ chế AOP khác có thể có nhiều advice/loại join point hơn; trace dùng cho kiểm thử phải dựa vào hợp đồng thực tế.

### 13.2 Hợp đồng bảo mật quan trọng hơn chỉ số advice

Nếu proxy làm kiểm soát quyền, một test chỉ thấy `ADVICE before` là chưa đủ. Phải kiểm chứng mọi đường vào bảo vệ đều đi qua kiểm tra, danh tính được xác thực từ nguồn tin cậy, quyền đúng theo tài nguyên và không có đường short-circuit ở lớp ngoài làm bỏ qua policy.

## 14. Những ngộ nhận thường gặp

1. **“Proxy và target luôn là một object.”** Sai: dynamic proxy thường là object riêng.
2. **“Proxy chặn mọi lời gọi trong ứng dụng.”** Sai: chỉ những đường đi qua phạm vi proxy.
3. **“Proxy handler chính là method-execution join point ở target.”** Không tự suy được từ JDK dynamic proxy.
4. **“Chỉ cần method cùng tên là forward đúng overload.”** Phải xét signature và dispatch.
5. **“Object methods không liên quan AOP.”** `equals`, `hashCode`, `toString` có thể đi qua handler và cần chính sách.
6. **“Reflection có `throws Throwable` nên checked exception nào cũng hợp lệ.”** Java proxy có quy tắc ngoại lệ hợp đồng.
7. **“Gọi target và gọi proxy có cùng `getClass()`.”** Không bắt buộc; class proxy có thể khác.
8. **“Tự gọi `this.other()` luôn quay qua proxy.”** Không đối với mọi cơ chế, đặc biệt delegation qua object gốc.
9. **“Cache proxy được trả thành công cho mọi ghi dữ liệu.”** Vi phạm hậu điều kiện nếu side effect bị bỏ qua.
10. **“Đặt Authorization ở trong Cache vẫn bảo vệ mọi cache hit.”** Cache ở ngoài có thể không gọi tiếp.
11. **“Thêm interceptor vào danh sách luôn làm nó chạy trước.”** Phụ thuộc thuật toán lồng wrapper/precedence.
12. **“Proxy phải dùng Reflection.”** Không; có thể dùng lời gọi trực tiếp, mã sinh, method handle.
13. **“Proxy luôn tốt hơn bytecode weaving.”** Hai kỹ thuật có khả năng và giới hạn khác nhau.
14. **“Chỉ log arguments là vô hại.”** Args có thể chứa mật khẩu, token, dữ liệu cá nhân.
15. **“Chạy thành công qua proxy chứng minh không cần test concurrency.”** State handler dùng chung vẫn có rủi ro race.

## 15. Bài tập tự luyện

**Bài 1.** Cho `Client → Proxy → Target`. Advice before in một dòng, gọi target, after in một dòng. Nếu target trả giá trị bình thường, thứ tự nào được kỳ vọng?

**Đáp:** before → target → after, rồi caller nhận kết quả. Đó là thứ tự của **chuỗi handler này**, không là quy tắc chung của mọi advice độc lập trong mọi bộ AOP.

**Bài 2.** Caller giữ `Target` gốc và gọi nó trực tiếp. Proxy có quan sát chắc chắn được lần gọi đó không?

**Đáp:** không. Caller bypass proxy. Nếu chính sách bắt buộc áp dụng cho tất cả đường vào, phải kiểm soát cách phát hành tham chiếu hoặc bổ sung ranh giới đáng tin khác.

**Bài 3.** Một `CacheProxy` bao ngoài `AuthorizationProxy`. Cache hit không chuyển tiếp. Kiểm tra quyền bên trong có chạy không?

**Đáp:** không qua nhánh đó. Đây có thể là lỗ hổng nếu mọi lần đọc cần xác thực/phân quyền trước khi trả dữ liệu.

**Bài 4.** Vì sao gọi `proxy.toString()` từ log trong InvocationHandler có thể nguy hiểm?

**Đáp:** việc gọi `toString()` có thể quay lại chính handler, tạo đệ quy. Cần xử lý Object methods tường minh hoặc ghi metadata an toàn không gọi lại proxy.

**Bài 5.** Handler dùng `Method.invoke(target, args)`; target ném `IllegalArgumentException`. Điều gì cần xử lý?

**Đáp:** `Method.invoke` bọc ngoại lệ target trong `InvocationTargetException`. Handler thường cần tháo bọc và truyền tiếp lỗi gốc theo hợp đồng, không vô tình báo lỗi wrapper như lỗi nghiệp vụ.

**Bài 6.** Một proxy dựa trên interface có chặn được một private method chỉ có trong implementation không?

**Đáp:** không thể mặc định. JDK dynamic proxy chặn lời gọi thông qua các interface đã cung cấp; private method của target không là lời gọi public interface tới proxy.

**Bài 7.** Một remote proxy retry gửi tiền lần nữa sau timeout. Vì sao nguy hiểm?

**Đáp:** timeout không chứng minh thao tác đầu chưa tạo tác dụng phụ; retry có thể gửi tiền hai lần nếu thiếu idempotency và giao thức xác nhận kết quả đáng tin.

## 16. Ghi nhớ

Mô hình proxy trong AOP có thể tóm tắt thành bốn câu hỏi:

1. **Ai nhận lời gọi trước?** Proxy hay target gốc?
2. **Proxy có những thông tin nào?** Method, args, chính proxy, target và ngữ cảnh phù hợp.
3. **Proxy làm gì trước khi chuyển tiếp?** Advice, kiểm tra, metric, cache, short-circuit?
4. **Proxy có thực sự chuyển tiếp không và giữ đúng hợp đồng không?** Nếu không, hệ quả nào xảy ra với target, các advice bên trong và caller?

**Kết luận:** proxy là **đối tượng trung gian tiếp nhận và điều phối lời gọi**. Nó có thể cho AOP tách mối quan tâm cắt ngang khỏi mã nghiệp vụ, nhưng chỉ tác động tới **đường gọi đi qua ranh giới của nó**. Thiết kế tốt phải phân biệt proxy/target, giữ đúng tham số–kết quả–ngoại lệ, cấu hình thứ tự advice có chủ đích và kiểm chứng các đường bypass cùng nhánh short-circuit. Không nên coi proxy là công cụ dệt mọi join point của chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-002">Ủy quyền qua giao diện so với tạo lớp con trung gian</a>

<details>
<summary>Xem chi tiết</summary>

# Ủy quyền qua giao diện so với tạo lớp con trung gian trong AOP qua proxy

## 1. Vấn đề: cùng là proxy, nhưng dựa vào hai cơ chế khác nhau

Trong **Aspect-Oriented Programming (AOP)**, một **aspect** gom các mối quan tâm xuyên suốt như đo thời gian, kiểm toán, ghi log, kiểm soát quyền hoặc chuyển đổi ngoại lệ. Một **pointcut** xác định những **join point** cần áp dụng **advice**. Khi chọn triển khai AOP qua **proxy**, chương trình không nhất thiết sửa bytecode của đối tượng nghiệp vụ; thay vào đó, caller làm việc với một đối tượng trung gian có thể chạy advice và chuyển tiếp thao tác.

Hai hướng xây dựng proxy thường gặp:

1. **Ủy quyền qua giao diện (interface-based delegation proxy)**: proxy triển khai một hay nhiều interface, nhận lời gọi qua các interface ấy rồi ủy quyền tới một **target riêng biệt**.
2. **Tạo lớp con trung gian (subclass-based proxy)**: proxy là một **subclass của kiểu target**, ghi đè những phương thức có thể override để chạy logic xen trước/sau và gọi phần triển khai của lớp cha hoặc ủy quyền theo chiến lược thiết kế.

Hai hướng này khác nhau sâu sắc về **quan hệ kiểu**, **đối tượng thực thi**, **lời gọi nội bộ**, **constructor**, **final/private method**, **trạng thái**, **quyền truy cập**, **kiểm thử** và **giới hạn phạm vi can thiệp**.

Bài tập trung vào **AOP tổng quát**. Java 17 được dùng làm nền tảng minh họa; JDK dynamic proxy là một API Java cụ thể, còn lớp con ghi đè thủ công chỉ minh họa cơ chế subclass proxy chứ không phải một engine sinh lớp con tự động.

## 2. Bộ khái niệm tối thiểu để so sánh hai kiểu proxy

**Caller** giữ reference dùng để gọi. **Proxy** là đối tượng trung gian; **target** thực thi nghiệp vụ. **Interface** quy định API được proxy giao diện triển khai; **subclass** là một kiểu kế thừa class và có thể ghi đè method hợp lệ. Một **interceptor** xử lý lời gọi trước/sau khi chuyển tiếp, còn **advice chain** thể hiện các lớp hành vi lồng nhau.

Trong AOP, **join point** là điểm can thiệp được hỗ trợ, **pointcut** chọn điểm đó, **advice** bổ sung hành vi và **weaving/interception** kết hợp hành vi vào luồng chạy. Đối với chương này, câu hỏi chính không phải thuộc tên thuật ngữ mà là **caller đang gọi object nào, lời gọi được chuyển tiếp bằng delegation hay override, và method ấy có thể được intercept tại điểm đó không**.

## 3. Hình dung hai cấu trúc

### 3.1. Ủy quyền qua giao diện

```text
              implements Interface
 Caller -----------------------> InterfaceProxy
                                      |
                                      | interceptor/advice
                                      v
                                Target implements Interface
                                      |
                                      v
                               Business operation
```

Tại đây **proxy và target là hai đối tượng riêng**. Proxy thường giữ reference tới target và chuyển tiếp lời gọi:

```text
proxy.operation(args):
    beforeAdvice()
    result = target.operation(args)
    afterAdvice()
    return result
```

Đây là **mã giả**; xử lý lỗi và finally sẽ phụ thuộc advice cụ thể.

### 3.2. Tạo lớp con trung gian

```text
                  extends BaseService
 Caller ------------------------------> GeneratedSubclass
                                           |
                                     override operation()
                                           |
                                     beforeAdvice()
                                           |
                                     super.operation()
                                           |
                                     afterAdvice()
```

Ở mô hình subclass **gọi super trên chính receiver proxy**, phần thân lớp cha chạy với `this` là đối tượng subclass. Khi phần thân ấy gọi một phương thức khác có thể override, Java có thể dispatch tới override ở subclass.

Một số giải pháp tạo lớp con nhưng **ủy quyền tới target riêng** thay vì gọi `super` trên receiver. Khi đó hành vi self-invocation có thể khác. Không được suy rằng “subclass proxy” tự động bảo đảm mọi lời gọi nội bộ đều đi qua advice.

## 4. Định nghĩa đúng “ủy quyền” và “kế thừa”

Trong thiết kế hướng đối tượng, **ủy quyền (delegation)** là việc một đối tượng trao trách nhiệm thực hiện công việc cho đối tượng khác. **Kế thừa (inheritance)** là quan hệ subtype giữa class con và class cha.

Một **interface proxy** thường thực hiện cả hai việc:

- Có kiểu tương thích với interface.
- Giữ target và **ủy quyền** thao tác tới target.

Một **subclass proxy** có thể:

- Có kiểu tương thích với class target, vì nó kế thừa class.
- Ghi đè phương thức và dùng `super.method(...)`.
- Hoặc kết hợp kế thừa với một target khác để **ủy quyền**, tùy engine.

**Điểm cần phân biệt:** “interface-based” và “subclass-based” chủ yếu nói về **quan hệ kiểu và cách can thiệp**, trong khi “delegation” nói về **cách chuyển thực thi**. Hai chiều này không luôn loại trừ hoàn toàn nhau.

## 5. So sánh nhanh

| Tiêu chí | Proxy dựa trên interface | Proxy dựa trên subclass |
| --- | --- | --- |
| Kiểu nhìn từ caller | Tương thích những interface proxy triển khai | Tương thích với class cha/kiểu target được kế thừa |
| Có cần interface? | Cần interface mà proxy sẽ hiện thực; có thể do ứng dụng định nghĩa | Không nhất thiết cần interface |
| Có cần subclass target? | Không | Có |
| Target có thể là `final class`? | Có thể, nếu có interface thích hợp và lời gọi qua proxy | Không thể kế thừa trực tiếp class final |
| Bắt method `final` của target? | Có thể can thiệp tại lời gọi interface nếu method đó là cách target hiện thực hợp đồng | Không thể override final method trong subclass |
| Bắt method `private`? | Không tự chặn private call trong target | Không thể override private method |
| Constructor | Proxy có constructor riêng, không chặn constructor target bằng interface dispatch | Tạo subclass đi qua constructor cha; không override constructor |
| Lời gọi nội bộ trên target | Thường bypass proxy nếu target tự gọi `this.method()` | Có thể dispatch vào override khi receiver thực sự là subclass proxy và method override được |
| Identity của proxy và target | Hai object riêng biệt trong mẫu ủy quyền | Có thể cùng một object proxy/subclass trong mẫu `super` |
| Method ngoài interface | Không gọi qua type interface trừ khi proxy có interface khác/adapter tương ứng | Có thể thấy method public/protected của class, nhưng intercept còn tùy override |
| Reflection/JVM | JDK `Proxy` cung cấp proxy interface | Cần lớp con thủ công hoặc công cụ tạo class/bytecode |
| Tương thích class loader/module | Interface và handler phải khả kiến/được phép | Class phải có thể kế thừa/khởi tạo/định nghĩa trong bối cảnh tương ứng |
| Debug | Có thể thấy handler, reflection, generated interface proxy | Có thể thấy subclass sinh, bridge/callback, super call |

Bảng mô tả **những nguyên lý điển hình**, không cam kết mọi AOP runtime đều giống nhau. Muốn đưa ra lựa chọn thực tế phải biết engine dùng interception nào và đường gọi có đi qua nó không.

## 6. JDK dynamic proxy theo interface hoạt động thế nào?

Java SE 17 có `java.lang.reflect.Proxy` và `InvocationHandler`. Một proxy instance được tạo bằng:

```java
Service proxied = (Service) java.lang.reflect.Proxy.newProxyInstance(
        Service.class.getClassLoader(),
        new Class<?>[] { Service.class },
        handler
);
```

Đoạn này chỉ là mảnh code minh họa; `Service` và `handler` phải được khai báo.

Bản thân generated proxy:

- **Implements** những interface được chỉ định.
- Sử dụng `InvocationHandler.invoke(proxy, method, args)` để xử lý lời gọi của các interface ấy.
- Là một class được tạo động, **không phải subclass của target class**.
- Có thể bọc một đối tượng target mà class target là `final`, miễn tương tác đi qua interface thích hợp.
- Được dùng thông qua kiểu interface, không phải ép kiểu thành một class target không liên quan.

Từ góc nhìn gọi hàm:

```text
caller.method(args)
   |
   v
generated interface proxy
   |
   v
InvocationHandler.invoke(proxy, Method, args)
   |
   +-- chọn advice
   +-- method.invoke(target,args)
   +-- xử lý kết quả / ngoại lệ
```

`Method.invoke` chỉ là một cách handler chuyển tiếp; một handler có thể dùng một adapter khác thay vì reflection. Việc proxy JDK sử dụng handler không có nghĩa mọi handler bắt buộc dùng reflection để gọi target.

### 6.1. Interface nào được đưa vào proxy?

Danh sách interface được đưa vào khi tạo proxy phải hợp lệ theo quy tắc API và class loader. Ví dụ nếu một class hiện thực thêm interface không có trong danh sách proxy, caller không tự nhiên có thể ép proxy về interface đó.

Trong Java 17, proxy JDK cần **interface hợp lệ**, không phải class. Sealed interface không được dùng như một proxy interface theo giới hạn `Proxy.newProxyInstance` của Java 17; kiểu interface phải khả kiến với loader và tương thích với module/access rules.

### 6.2. Xử lý `equals`, `hashCode` và `toString`

JDK dynamic proxy cũng chuyển những lời gọi của ba phương thức `Object` này qua `InvocationHandler` theo quy tắc API. Nếu một handler chỉ xử lý method nghiệp vụ và phản xạ mù quáng sang target, semantics identity của proxy có thể không khớp mong đợi.

Cần quyết định rõ:

- `proxy.equals(target)` hay `proxy.equals(anotherProxy)` có ý nghĩa gì?
- `hashCode` có nhất quán với `equals` không?
- `toString` có tiết lộ dữ liệu không nên công bố hoặc kích hoạt vòng gọi advice không?
- Có cần so sánh theo identity proxy, identity target hay business key?

Không có một quy tắc AOP chung bắt buộc mọi proxy phải dùng cùng chính sách equality.

### 6.3. Default method của interface

Một interface Java có thể có default method. Với dynamic proxy, lời gọi default method vẫn đi qua invocation handler; API Java 17 có cơ chế như `InvocationHandler.invokeDefault` để gọi implementation default trong trường hợp phù hợp. Không nên mặc định handler `method.invoke(target, args)` có cùng semantics với gọi default method trên proxy trong mọi thiết kế.

## 7. Subclass proxy dựa trên override hoạt động thế nào?

Java dùng dispatch động đối với những lời gọi instance method có thể override:

```java
class Worker {
    public String execute(String id) {
        return "done:" + id;
    }
}

class InterceptingWorker extends Worker {
    @Override
    public String execute(String id) {
        System.out.println("before");
        String result = super.execute(id);
        System.out.println("after");
        return result;
    }
}
```

Đây là đoạn class Java minh họa. `InterceptingWorker` được caller sử dụng như một `Worker`, vì nó **extends Worker**.

Một subclass proxy tự động có thể sinh override tương tự nhưng dùng dispatcher, method interceptor hoặc callback động. Tùy công cụ, override có thể gọi `super` hoặc ủy quyền sang target.

### 7.1. Tại sao `super.execute` lại quan trọng?

Trong một override, gọi `this.execute(id)` sẽ tiếp tục dispatch tới override hiện tại và có thể gây đệ quy vô hạn. Gọi `super.execute(id)` yêu cầu chạy phần triển khai của lớp cha theo cú pháp Java, tránh vòng lặp trực tiếp ở chính override đó.

Nhưng nếu phần thân `Worker.execute` lại gọi một **phương thức virtual khác**, lời gọi ấy có thể tiếp tục dispatch tới override trong subclass. Đây là nguồn tạo ra khác biệt khi gọi nội bộ và cũng là nguy cơ advice chạy nhiều lần.

### 7.2. Subclass proxy có phải target thật không?

Trong mẫu **lấy subclass instance làm receiver**, đối tượng proxy chính là một instance thuộc subtype của target class; trạng thái kế thừa nằm trên đối tượng ấy.

Trong mẫu **subclass giữ delegate target riêng**, có hai đối tượng: subclass proxy và target. Khi gọi vào delegate, những lời gọi nội bộ trong delegate vẫn dùng receiver của delegate, không tự dispatch sang subclass proxy.

Do đó phải kiểm tra thực tế engine tạo proxy của bạn đang dùng mẫu nào trước khi suy luận về self-invocation hoặc state.

## 8. Java 17 runnable: cùng nghiệp vụ, hai loại proxy, kết quả khác nhau

Ví dụ dưới đây có hai cách triển khai can thiệp:

1. **JDK interface proxy**: handler giữ `Worker target` riêng và gọi `Method.invoke` trên target.
2. **Subclass proxy**: `WorkerSubclassProxy extends Worker`, ghi đè `execute` và gọi `super.execute`.

Đoạn này cố ý để `Worker.batch` gọi `this.execute(id)`. Khi qua **interface proxy**, handler chỉ nhận lời gọi `batch` bên ngoài; target tự gọi `execute` trên target thật. Khi qua **subclass proxy với receiver là subclass**, `Worker.batch` kế thừa gọi `this.execute` và đi vào override.

Đây là **chương trình Java 17 độc lập**, minh họa cơ chế hướng đối tượng và proxy. Nó không phải bộ dệt tự động từ pointcut DSL.

Tạo file `ProxyMechanismsDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ProxyMechanismsDemo {

    interface Port {
        String execute(String id);
        String batch(String id);
    }

    static class Worker implements Port {

        @Override
        public String execute(String id) {
            System.out.println("business:execute:" + id);
            if ("ERR".equals(id)) {
                throw new IllegalStateException("business rejected");
            }
            return "done:" + id;
        }

        @Override
        public String batch(String id) {
            System.out.println("business:batch:" + id);
            return this.execute(id);
        }

        public final String version() {
            System.out.println("business:version");
            return "v1";
        }
    }

    static class WorkerSubclassProxy extends Worker {

        @Override
        public String execute(String id) {
            System.out.println("subclass:before:execute");

            try {
                String result = super.execute(id);
                System.out.println("subclass:after:execute");
                return result;
            } catch (RuntimeException error) {
                System.out.println("subclass:error:"
                        + error.getClass().getSimpleName());
                throw error;
            }
        }

        // batch() kế thừa từ Worker, không override.
        // version() là final nên không thể override.
    }

    public static void main(String[] args) {
        Worker target = new Worker();

        Port interfaceProxy = (Port) Proxy.newProxyInstance(
                Port.class.getClassLoader(),
                new Class<?>[] { Port.class },
                (proxy, method, values) -> {
                    String methodName = method.getName();
                    System.out.println("interface:before:" + methodName);

                    try {
                        Object result = method.invoke(target, values);
                        System.out.println("interface:after:" + methodName);
                        return result;
                    } catch (InvocationTargetException wrapper) {
                        Throwable original = wrapper.getCause();
                        System.out.println("interface:error:"
                                + original.getClass().getSimpleName());
                        throw original;
                    }
                }
        );

        Worker subclassProxy = new WorkerSubclassProxy();

        System.out.println("case:interface-single");
        System.out.println("caller:" + interfaceProxy.execute("A"));

        System.out.println("case:interface-batch");
        System.out.println("caller:" + interfaceProxy.batch("B"));

        System.out.println("case:subclass-single");
        System.out.println("caller:" + subclassProxy.execute("C"));

        System.out.println("case:subclass-batch");
        System.out.println("caller:" + subclassProxy.batch("D"));

        System.out.println("case:subclass-final");
        System.out.println("caller:" + subclassProxy.version());

        System.out.println("case:direct");
        System.out.println("caller:" + target.batch("E"));

        System.out.println("case:interface-error");
        try {
            interfaceProxy.execute("ERR");
        } catch (IllegalStateException error) {
            System.out.println("caller:error:" + error.getMessage());
        }

        System.out.println("case:subclass-error");
        try {
            subclassProxy.execute("ERR");
        } catch (IllegalStateException error) {
            System.out.println("caller:error:" + error.getMessage());
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ProxyMechanismsDemo.java
java ProxyMechanismsDemo
```

**Stdout dự kiến:**

```text
case:interface-single
interface:before:execute
business:execute:A
interface:after:execute
caller:done:A
case:interface-batch
interface:before:batch
business:batch:B
business:execute:B
interface:after:batch
caller:done:B
case:subclass-single
subclass:before:execute
business:execute:C
subclass:after:execute
caller:done:C
case:subclass-batch
business:batch:D
subclass:before:execute
business:execute:D
subclass:after:execute
caller:done:D
case:subclass-final
business:version
caller:v1
case:direct
business:batch:E
business:execute:E
caller:done:E
case:interface-error
interface:before:execute
business:execute:ERR
interface:error:IllegalStateException
caller:error:business rejected
case:subclass-error
subclass:before:execute
business:execute:ERR
subclass:error:IllegalStateException
caller:error:business rejected
```

### 8.1. Trường hợp interface proxy gọi `execute`

Caller giữ `Port interfaceProxy`, nên lời gọi `execute("A")` được dispatcher chuyển đến handler. Handler in `interface:before`, gọi target thực qua reflection, sau đó in `interface:after` rồi trả chuỗi `done:A`.

**Proxy không phải instance của class `Worker`** ở cách này. Nó chỉ được tạo để triển khai `Port`.

### 8.2. Trường hợp interface proxy gọi `batch`

Handler chỉ nhận `batch("B")` bên ngoài:

```text
interfaceProxy.batch(B)
  -> handler (method=batch)
  -> target.batch(B)
     -> target.this.execute(B)   // ở trong target
  -> handler returns
```

Vì `execute` bên trong được gọi trên `target`, nó không quay ngược qua handler proxy. Ta **không thấy** `interface:before:execute` cho lời gọi nội bộ ấy.

Đây là biểu hiện điển hình của **self-invocation bypass** với cấu trúc ủy quyền qua một target riêng.

### 8.3. Trường hợp subclass proxy gọi `batch`

Caller gọi `subclassProxy.batch("D")`, mà `batch` là phương thức **kế thừa từ `Worker`**. Vì receiver thực tế là `WorkerSubclassProxy`, bên trong `Worker.batch`:

```java
return this.execute(id);
```

là một lời gọi virtual đến override trong subclass. Vì vậy có `subclass:before:execute` và `subclass:after:execute` cho lời gọi nội bộ.

**Giới hạn kết luận:** hiện tượng này đúng với **mẫu subclass receiver + super call** trong ví dụ và Java dispatch. Nó không phải bảo đảm rằng mọi công cụ quảng bá “subclass proxy” đều chặn self-invocation; nếu công cụ gọi vào một delegate riêng, hành vi có thể khác.

### 8.4. Trường hợp `version()` là final

`version` thuộc `Worker` và được khai báo `final`. `WorkerSubclassProxy` kế thừa nó nhưng **không thể ghi đè** để chèn advice theo kỹ thuật override. Caller gọi `subclassProxy.version()` sẽ chỉ thấy `business:version`.

Điều này không nói rằng `final` làm mọi kỹ thuật AOP bất khả thi. Một cơ chế **weaving bytecode trực tiếp** hoặc một **wrapper interface phù hợp** có thể can thiệp tại ranh giới khác mà không cần override final method.

### 8.5. Ngoại lệ qua hai proxy

Trường hợp `ERR` cho thấy cả hai cách có thể chuyển đúng `IllegalStateException` về caller:

- Interface handler gọi target qua `Method.invoke`. Khi target ném lỗi, reflection bọc tạm thời trong `InvocationTargetException`; handler lấy `getCause()` và ném lại.
- Subclass override gọi `super.execute` trực tiếp; nó bắt `RuntimeException` và ném lại, không có lớp bọc reflection trong nhánh này.

Nếu handler ném một **checked exception không được interface khai báo**, proxy JDK có thể bọc bằng `UndeclaredThrowableException` theo hợp đồng của `Proxy`. Không được mặc định mọi ngoại lệ đều đi qua nguyên dạng.

## 9. Kiểu của proxy ảnh hưởng API của caller

Với interface proxy:

```java
Port service = interfaceProxy;
```

Câu trên hợp lệ trong ví dụ. Nhưng:

```java
Worker worker = (Worker) interfaceProxy;
```

sẽ thất bại khi chạy vì JDK dynamic proxy không kế thừa `Worker`; nó chỉ triển khai `Port`.

Ngược lại:

```java
Worker worker = new WorkerSubclassProxy();
```

hợp lệ vì subclass proxy **là một subtype của `Worker`**.

Đây là khác biệt đáng cân nhắc:

- Nếu API hiện hữu nhận/trả **interface**, interface proxy thuận lợi.
- Nếu API yêu cầu **concrete class**, interface proxy có thể không thay thế được mà không sửa ranh giới API.
- Subclass proxy có thể dùng ở vị trí cần base class, nhưng chỉ khi class có thể được kế thừa và khởi tạo hợp lệ.
- Một ứng dụng chỉ giữ target concrete reference và tự gọi trực tiếp sẽ không tự động bắt đầu dùng proxy.

### 9.1. Interface nhỏ giúp giảm coupling

Khi thiết kế từ đầu, việc định nghĩa một **port/interface** cho thao tác quan trọng có thể hỗ trợ decorator, fake trong kiểm thử và proxy. Tuy nhiên, không nên tạo interface chỉ để che một rào cản tạm thời mà không có hợp đồng rõ.

Nếu một thư viện không cung cấp interface, ứng dụng có thể tạo interface do mình sở hữu và viết **adapter** để ủy quyền tới thư viện. Khi ấy proxy chỉ bao những lời gọi đi qua adapter, chứ không trở thành subclass hoặc can thiệp mọi method vendor.

## 10. Hạn chế `final`, `private`, `static` và constructor

### 10.1. `final class`

Java không cho tạo subclass trực tiếp từ final class. Một subclass-based proxy cần kế thừa class target sẽ không áp dụng được qua cơ chế kế thừa thông thường.

JDK interface proxy **có thể** bao quanh lời gọi interface vào đối tượng target final, vì proxy không cần kế thừa target.

### 10.2. `final method`

Subclass không thể override final method, nên không thể chèn advice **bằng override ở chính method đó**.

Một interface proxy có thể can thiệp trước khi gọi final implementation nếu lời gọi đi qua method tương ứng trên interface. Interface proxy chặn **ranh giới gọi qua proxy**, không sửa final method bên trong target.

### 10.3. `private method`

Private method không thể được override. Subclass proxy không biến lời gọi private trong lớp cha thành virtual dispatch qua override.

Nếu cần theo dõi private method, phải cân nhắc điểm public boundary hoặc một công cụ weaving có mô hình pointcut/khả năng biến đổi sâu thích hợp.

### 10.4. `static method`

Static method thuộc class, không được override theo nghĩa đa hình instance. JDK interface proxy không thể chặn một lời gọi tĩnh trên class target bằng dispatch interface thông thường.

Một lời gọi static có thể được quan sát bằng **call-site weaving** nếu công cụ hỗ trợ; đó là cơ chế khác với interface/subclass proxy.

### 10.5. Constructor

Constructor không được kế thừa hay override như instance method. Subclass constructor gọi constructor của superclass theo quy tắc Java.

Một subclass proxy phải có đường khởi tạo hợp lệ. Nếu base class chỉ có constructor không thể truy cập hoặc yêu cầu tham số, một bộ sinh lớp con phải đáp ứng các ràng buộc ấy; có công cụ dùng chiến lược khởi tạo riêng nhưng không thể giả định an toàn cho mọi class.

**Tránh gọi phương thức overridable trong constructor của base class.** Khi base constructor gọi một virtual method, override của subclass có thể chạy trước khi state riêng của subclass được khởi tạo đầy đủ. Với subclass proxy, đây là lỗi thiết kế rất khó chẩn đoán.

## 11. Self-invocation: điều gì thực sự quyết định có advice?

Câu “self-invocation luôn bỏ qua AOP” là **quá rộng**. Cần hỏi:

1. Receiver của lời gọi nội bộ là **target** hay **proxy**?
2. Method được gọi có thể **override/virtual dispatch** không?
3. Interception được cài ở **proxy entry**, **subclass override**, hay **method body weaving**?
4. Có advice ngoài nào chặn `proceed` không?
5. Một method được dệt có sử dụng proxy chain bên trong hay không?

Mô hình:

| Tình huống | Lời gọi nội bộ đi đâu? | Khả năng advice |
| --- | --- | --- |
| Delegate target gọi `this.execute()` | Target → target | Bypass interface proxy ngoài |
| Base method chạy trên subclass proxy, gọi virtual `this.execute()` | Dispatch tới subclass override | Có thể intercept override |
| Base method gọi `super.execute()` ở subclass | Gọi phần triển khai superclass | Không đi qua override cùng điểm đó |
| Base method gọi private/final method | Không thể override bằng subclass | Subclass-override interception không có |
| Method body đã được bytecode weaving | Đường nội bộ đi qua body đã dệt | Có thể có advice theo pointcut/weaving |
| Caller vẫn giữ target gốc, gọi trực tiếp | Không đi qua proxy | Không có proxy-entry advice |

Do đó, **phạm vi can thiệp là thuộc tính của đường gọi thực tế**, không chỉ của từ “proxy”.

## 12. Subclass proxy và trạng thái đối tượng

Khi subclass proxy **tự là đối tượng nghiệp vụ**, state trong field kế thừa thuộc proxy:

```text
WorkerSubclassProxy instance
  +-- fields kế thừa từ Worker
  +-- fields riêng của subclass
  +-- overrides
```

Khi interface proxy giữ **một target riêng**, state nghiệp vụ thuộc target:

```text
InterfaceProxy instance            Worker target instance
  +-- handler                       +-- business fields
  +-- reference to target --------> +-- methods
```

Nếu ứng dụng đã tạo một `Worker` có state từ trước rồi tạo một subclass proxy **mới**, không thể mặc định đối tượng mới mang toàn bộ state, identity hoặc lock của target cũ.

Các lựa chọn của engine (delegate riêng, dùng target thực, tạo đối tượng mới, chuyển state...) có thể tác động:

- Identity (`==`) và equality.
- Lifecycle/constructor side effect.
- Field có tính chất bất biến, `final`, hoặc resource handle.
- Synchronization và monitor lock.
- Cache dựa trên identity/object reference.
- Trạng thái đã được khởi tạo bên ngoài proxy.
- Serialization và framework phản xạ phụ thuộc exact class.

**Đừng tự “copy field”** để biến một đối tượng đang hoạt động thành subclass proxy nếu không hiểu tính nhất quán và lifecycle. Nhiều resource và invariant không thể sao chép an toàn.

## 13. Identity và `equals`/`hashCode`

Trong giao diện ủy quyền, proxy và target là hai object khác nhau:

```text
proxy != target
```

Điều này vẫn đúng nếu handler chuyển hầu hết lời gọi tới target.

Các tình huống cần thiết kế:

- Một map/set chứa target hay proxy làm key?
- Equality giữa hai proxy bao cùng target phải ra sao?
- `hashCode` có nhất quán khi target thay đổi trạng thái không?
- Handler nên xử lý `toString` như thế nào để không để lộ dữ liệu hoặc tự gọi lại proxy?

Subclass proxy thường là một subtype của class target, nhưng **không có bảo đảm equality giống hệt target cũ** nếu có object mới hoặc `equals`/`hashCode` dựa vào exact class hay field.

### 13.1. `getClass()` và class-based logic

Khi dùng proxy, `object.getClass()` có thể trả **class proxy sinh**, không phải concrete target class. Điều đó ảnh hưởng mã dùng so sánh exact class:

```java
if (value.getClass() == Worker.class) {
    // ...
}
```

Đây là mảnh minh họa. Nó có thể không chạy đúng kỳ vọng với subclass proxy hoặc JDK proxy. Nên ưu tiên hợp đồng interface, `instanceof` phù hợp và metadata API được thiết kế rõ thay vì coi exact runtime class luôn là business type.

## 14. Interception và ngoại lệ: giữ đúng hợp đồng

Một proxy có thể quan sát/đổi ngoại lệ ở **ranh giới ngoài**. Cần phân biệt:

- Lỗi do target ném.
- Lỗi từ handler/interceptor trước `proceed`.
- Lỗi từ after advice khi target đã hoàn thành.
- Lỗi của proxy framework hoặc reflection.
- Lỗi do một advice khác trong chuỗi ném.

### 14.1. Interface handler và `InvocationTargetException`

Nếu handler dùng `Method.invoke`, checked wrapper `InvocationTargetException` bao lỗi do target ném. Handler có thể lấy `getCause()` để truyền tiếp đúng loại lỗi.

Đừng ném nguyên `InvocationTargetException` không khai báo qua proxy interface rồi kỳ vọng caller thấy lỗi nghiệp vụ giống như gọi target. Chữ ký interface và quy tắc checked exceptions của JDK Proxy có thể dẫn tới `UndeclaredThrowableException`.

### 14.2. Subclass override và `super`

Một override gọi `super.method()` trực tiếp không đi qua `Method.invoke`, nên lỗi target/super có thể được quan sát bằng Java `try/catch` thường. Nhưng một engine subclass dùng dispatcher và reflection vẫn có thể có wrapper kỹ thuật của riêng nó; **đừng đồng nhất mọi subclass proxy với một override thủ công đơn giản**.

### 14.3. Advice sau khi return có thể đổi “thành công” thành “lỗi”

Nếu method gốc hoàn thành và interceptor sau ném lỗi, caller có thể nhận ngoại lệ dù side effect đã xảy ra. Với retry, phải xem thao tác có idempotent không. Đây là rủi ro của **advice semantics**, không phải điểm riêng của interface hay subclass.

## 15. Lớp sinh, visibility, module và class loader

Một proxy được sinh khi chạy phải thỏa quy tắc của môi trường Java:

- Interface phải khả kiến đối với loader dùng để tạo JDK proxy.
- Những interface non-public có các ràng buộc về package/module.
- Một số tổ hợp interface có chữ ký trùng nhưng kiểu trả về không tương thích có thể không tạo proxy được.
- Named module và `exports`/`opens` ảnh hưởng khả năng reflection sâu và việc định nghĩa/truy cập class.
- Subclass proxy phải được định nghĩa trong bối cảnh cho phép kế thừa target và truy cập constructor/method phù hợp.
- Package-private member và protected access phụ thuộc package/runtime package, quan hệ kế thừa và module.
- Một class có thể nhìn thấy qua loader này nhưng helper/interceptor không khả kiến qua loader khác.

### 15.1. Đừng nhầm public API với toàn quyền reflection

Dùng interface public qua JDK proxy có thể tránh nhu cầu đọc các private field của target. Ngược lại, một subclass proxy tạo động có thể cần mở rộng khả năng tạo/định nghĩa class.

`setAccessible(true)` không phải chiếc chìa khóa vượt qua mọi module boundary. Cần tuân thủ quy tắc Java module system và cấu hình được phép.

### 15.2. Sealed class/interface

Java 17 hỗ trợ sealed types hạn chế những subtype có thể xuất hiện. Một subclass sinh động không nằm trong tập subtype được phép có thể không kế thừa sealed class hợp lệ.

JDK dynamic proxy của Java 17 cũng có giới hạn về việc sử dụng sealed interface làm proxy interface. Vì vậy không thể suy rằng “có interface” là đủ; phải kiểm tra interface **có được API proxy hỗ trợ không**.

### 15.3. Class loader identity

Trong JVM, định danh class phụ thuộc tên nhị phân và defining loader. Một proxy sinh ở loader khác hoặc không thấy interface/helper class có thể thất bại khi định nghĩa, ép kiểu hoặc gọi method.

Trong hệ thống plugin/container, hãy kiểm thử đúng bố trí loader/runtime production, không chỉ trong một project đơn module đơn giản.

## 16. Default methods và method resolution

Interface Java có thể chứa default method. Với JDK dynamic proxy, lời gọi trên interface có thể chuyển vào handler và cần một chiến lược rõ nếu muốn gọi default implementation.

Với subclass, method dispatch dựa trên các quy tắc kế thừa, override, super call và interface default method resolution của Java.

Một số trường hợp đặc biệt:

- Một interface proxy hiện thực nhiều interface có **chữ ký method trùng nhau**. Handler có thể nhận `Method` liên quan tới interface xuất hiện trước theo quy tắc `Proxy`, không nhất thiết đúng với kiểu reference ở call site.
- Default method có thể cần `InvocationHandler.invokeDefault` nếu muốn gọi implementation mặc định trên proxy.
- Covariant return types và bridge methods do compiler tạo có thể ảnh hưởng reflection/method interception.
- Method `equals`/`hashCode`/`toString` cần chiến lược rõ.
- Một method trong interface không có nghĩa target tự động triển khai hành vi đúng hợp đồng sau khi handler ủy quyền sai.

Không nên suy từ một ví dụ `execute(String)` đơn giản rằng mọi tổ hợp interface nhiều kế thừa luôn có một đường chuyển tiếp duy nhất.

## 17. Constructor, lifecycle và dependency

Một proxy thường được sử dụng như một component có lifecycle:

```text
Construct target
  -> configure target
  -> create proxy/wrapper
  -> give proxy reference to callers
  -> invoke methods through proxy
  -> close/cleanup according to ownership
```

Với giao diện ủy quyền, cần quyết định **ai sở hữu vòng đời của target**. Ví dụ: proxy đóng mà target vẫn được nơi khác sử dụng thì chuyện gì xảy ra?

Với subclass proxy, việc khởi tạo class con đi qua constructor chain và có thể gây side effect. Nếu class yêu cầu factory riêng, bất biến nội bộ, native resources hoặc kiểm tra identity, subclass proxy có thể không thích hợp.

### 17.1. Nhận proxy phải xảy ra trước khi rò rỉ target

Nếu code tạo target rồi truyền reference target thật ra ngoài trước khi gắn proxy, các caller giữ target có thể tiếp tục bypass advice. Điều này đặc biệt nguy hiểm với chính sách kiểm tra quyền nếu chỉ dựa vào proxy.

Nên đưa một **ranh giới tạo đối tượng được quản lý** vào thiết kế khi yêu cầu mọi lời gọi đều qua interception.

### 17.2. Proxy không tự quản lý tất cả lifecycle methods

Constructor, finalizer, static initializer và các hành vi không qua interface/subclass override không tự được can thiệp. Nếu một chính sách phải bao cả vòng đời đối tượng, cần dùng API lifecycle/factory thích hợp hoặc cơ chế weaving có khả năng quan sát ranh giới đó.

## 18. Hiệu năng: không nên chọn theo định kiến “reflection luôn chậm”

Interface proxy dùng handler; subclass proxy có thể dùng override và dispatcher. Tuy nhiên không thể kết luận một cơ chế luôn nhanh hơn trong mọi môi trường:

- Chi phí tạo proxy thường khác chi phí mỗi invocation.
- Handler có thể dùng `Method.invoke`, method handle hoặc adapter được tối ưu tùy triển khai.
- Subclass proxy có thể thực hiện dispatch qua callback phức tạp, không nhất thiết là một lời gọi `super` đơn giản.
- JVM JIT có thể tối ưu nhiều đường gọi phổ biến tùy profile.
- Tỷ lệ pointcut khớp, số advice và nội dung advice quyết định overhead tổng.
- Logging I/O hoặc chính sách kiểm toán có thể tốn hơn chính interceptor nhiều bậc.
- Class generation/verification và startup/memory footprint có thể đáng kể với hệ thống tạo nhiều loại proxy.

Muốn quyết định theo hiệu năng, hãy đo với workload đại diện: baseline không proxy, interface proxy, subclass proxy, cùng advice và cùng số invocation. Kiểm tra throughput, latency p50/p95/p99, allocation, GC và warm-up. Tránh benchmark có `System.out.println` trong hot loop rồi suy ra chi phí dispatch.

## 19. Khi có nhiều proxy/advice lồng nhau

Một target có thể được bao bởi nhiều tầng:

```text
Caller
   |
   v
Tracing Proxy
   |
   v
Security Proxy
   |
   v
Business Target
```

Khi đó:

- Thứ tự advice ảnh hưởng ngoại lệ, return value và việc target có được gọi hay không.
- Một outer advice có thể từ chối trước khi inner nhận lời gọi.
- Một lớp có thể đổi exception khiến lớp ngoài nhìn thấy lỗi đã biến đổi.
- Nếu mỗi tầng proxy giữ target khác, identity và loại runtime có thể khác kỳ vọng.
- Có thể phát sinh double interception khi vô tình bọc cùng một component nhiều lần.
- Một proxy JDK có thể giữ một subclass proxy làm target nếu các ràng buộc kiểu phù hợp; cơ chế có thể kết hợp, không loại trừ nhau.

Ví dụ:

```text
AuditAround(MetricsAround(BusinessOperation))
```

Lỗi `MetricsAround` ném sau khi business thành công có thể bị `AuditAround` hiểu nhầm là lỗi nghiệp vụ. Kiểm thử nhiều advice phải xét **nguồn lỗi và thứ tự thực tế**, không chỉ log cuối.

## 20. Thiết kế pointcut khi chỉ có proxy entry

Một pointcut rộng trên tên method có thể chọn nhiều thao tác không thuộc yêu cầu. Nhưng pointcut đúng về chữ ký vẫn không đảm bảo advice chạy nếu đường gọi **bypass proxy**.

Hai chiều cần kiểm thử riêng:

1. **Matching:** method được chọn đúng hay không?
2. **Reachability:** invocation có đi qua object proxy và interceptor không?

Ví dụ chính sách “ghi audit mọi thao tác thay đổi dữ liệu”:

- Mẫu tên phương thức có thể bỏ sót một method mới không theo convention.
- Một internal call trên delegate có thể không đi qua interface proxy.
- Một final method có thể không được subclass override.
- Một caller giữ target thật có thể bỏ qua toàn bộ chain.
- Một advice ngoài có thể không tiếp tục đến target.

**Bài học:** một cơ chế interception được chọn để triển khai chính sách quan trọng phải được xem xét cùng kiến trúc luồng gọi, không chỉ cú pháp pointcut.

## 21. Khi nào chọn interface proxy?

Interface proxy thường đáng xem xét khi:

- Chương trình đã dùng **interface rõ ràng** để mô tả port hoặc service contract.
- Muốn bọc một đối tượng có class final nhưng triển khai interface có thể proxy.
- Không cần can thiệp private/helper/constructor bên trong target.
- Muốn decorator dễ kiểm thử, dễ thay implementation.
- Có khả năng kiểm soát cách caller nhận reference, để mọi đường quan trọng dùng proxy.
- Muốn giảm phụ thuộc vào cấu trúc kế thừa và constructor của vendor class.

Cần đánh giá rủi ro:

- Caller yêu cầu concrete class chứ không phải interface.
- Một số method không nằm trong interface.
- Lời gọi trực tiếp/self-invocation trên target bypass handler.
- Handler xử lý default methods, equals/hashCode và checked exception chưa đúng.
- Interface/module/class loader không thỏa quy tắc tạo proxy.

## 22. Khi nào chọn subclass proxy?

Subclass proxy có thể phù hợp khi:

- Caller cần object tương thích với **concrete base class**.
- Class target **không final** và có constructor/visibility phù hợp.
- Những điểm muốn intercept là các method có thể **override**.
- Thiết kế có thể chấp nhận class con được tạo động và lifecycle liên quan.
- Engine xử lý đúng state, constructor, module access và return/exception semantics.
- Hành vi inherited virtual dispatch là yếu tố cần thiết và đã được kiểm chứng.

Cần đánh giá rủi ro:

- Class final, sealed hoặc không cho phép subclass hợp lệ.
- Method final/private/static không thể override.
- Constructor khó khởi tạo hoặc gọi virtual method quá sớm.
- Mã nghiệp vụ dùng exact class checks, equality hoặc serialization nhạy với subtype.
- Subclass proxy ủy quyền vào một target khác, khiến self-invocation không giống tưởng tượng.
- Generated class và helper không thể liên kết qua loader/module.

## 23. Nếu cả hai lựa chọn đều không đủ

Không phải mọi bài toán AOP đều nên ép vào một kiểu proxy. Một số phương án khác:

- **Application-owned facade/adapter:** tập trung call site, giúp kiểm soát contract mà không sửa vendor class.
- **Hook/callback chính thức:** nếu thư viện có extension API phù hợp.
- **Compile-time hoặc load-time weaving:** nếu cần can thiệp vào method execution/internal calls mà proxy entry không nhìn thấy, và có năng lực/quyền cần thiết.
- **Bytecode instrumentation có kiểm soát:** có thể phù hợp với tracing sâu hoặc thư viện bên thứ ba, nhưng đòi hỏi kiểm thử tương thích và phạm vi chặt.
- **Thiết kế lại ranh giới nghiệp vụ:** thay vì intercept mọi private helper, đặt policy ở một operation boundary có ý nghĩa.
- **Client/network interceptor:** khi đối tượng cần quan sát là service ngoài tiến trình.

Không nên hạ yêu cầu bảo mật hoặc kiểm toán bắt buộc xuống “proxy có vẻ chạy trong một demo” nếu vẫn có đường bypass chưa được xử lý.

## 24. Ma trận kiểm thử bắt buộc

| Ca | Interface proxy delegation | Subclass proxy override |
| --- | --- | --- |
| Gọi method qua proxy | Handler phải nhận | Override phải nhận nếu method được intercept |
| Gọi target trực tiếp | Không qua handler | Không qua instance subclass nếu là target khác |
| Target tự gọi `this.virtualMethod()` | Thường bypass handler ngoài | Có thể vào override nếu receiver subclass |
| Method final | Có thể bắt ở interface boundary | Không override được |
| Method private | Không nhìn thấy private call bên trong | Không override được |
| Class final | Có thể nếu có interface phù hợp | Không subclass được |
| Interface không có method cần gọi | Không bắt bằng JDK proxy của interface đó | Có thể intercept concrete method nếu override được |
| Method ném ngoại lệ | Handler phải giữ cause/contract | Override giữ exception semantics |
| Checked exception | Đúng interface `throws`, tránh bọc bất ngờ | Đúng quy tắc override và `throws` của Java |
| Advice ném sau return | Caller có thể nhận lỗi sau side effect | Tương tự |
| `equals/hashCode/toString` | Handler kiểm soát theo API proxy | Có thể kế thừa/override tùy method |
| Constructor có side effect | Target và proxy có lifecycle riêng | Có constructor chain của subclass |
| Nhiều proxy/advice | Đúng thứ tự, không double-wrap | Đúng thứ tự, không double-intercept |
| Cấu hình loader/module | Interface khả kiến và proxy tạo được | Class con/helper định nghĩa được |
| Nhiều thread/đệ quy | Không lưu invocation state chung sai cách | Không lưu invocation state chung sai cách |
| Phương thức trả Future | Phân biệt method return và task completion | Tương tự |
| Hiệu năng hot path | Đo trên tải phù hợp | Đo trên tải phù hợp |

### 24.1. Tạo bộ test xác nhận self-invocation

Để kiểm tra một engine proxy cụ thể, tạo class có:

- Method `outer()` gọi `this.inner()`.
- Cả `outer` và `inner` đủ điều kiện trong pointcut.
- Một counter advice tăng theo tên method.
- Test gọi `outer` qua proxy và kiểm tra counter của `inner`.
- Test gọi `inner` trực tiếp qua proxy.
- Test gọi target không qua proxy nếu code cho phép.

**Không suy kết quả trước khi chạy trên engine thật.** Mẫu Java trong phần 8 chỉ là hai cấu trúc đã xác định rõ, không đại diện cho mọi engine.

## 25. Các lỗi hiểu sai phổ biến

**“Có interface thì mọi lời gọi tới target đều đi qua handler.”** Sai. Caller phải dùng proxy, và lời gọi nội bộ trên target riêng thường bypass.

**“Subclass proxy chắc chắn intercept self-invocation.”** Chưa đủ. Cần biết receiver là subclass proxy và method có thể override; một subclass proxy delegating sang target riêng có thể bypass.

**“JDK dynamic proxy kế thừa target class.”** Sai. Nó hiện thực các interface được chỉ định và dùng invocation handler.

**“Subclass proxy luôn tương thích với mọi final method.”** Nó có thể kế thừa final method, nhưng không thể override method ấy để intercept theo cơ chế kế thừa.

**“Final class nghĩa không thể dùng AOP.”** Sai. Interface proxy hoặc bytecode weaving có thể dùng ở ranh giới phù hợp.

**“Private method có thể bị subclass override bằng reflection.”** Sai. Private method không được override theo Java inheritance; reflection và weaving là các cơ chế khác.

**“Chỉ cần đổi class thành subclass sẽ giữ nguyên state của target đang tồn tại.”** Không. Hai object có identity/lifecycle khác nhau; không nên giả định copy state an toàn.

**“Proxy chậm vì Java reflection luôn chậm.”** Không thể kết luận chung. Chi phí tùy handler, JIT, advice, điểm nóng và workload.

**“Giá trị trả về đúng là đủ chứng minh proxy đúng.”** Chưa đủ. Phải kiểm tra ngoại lệ, advice order, side effects, self-invocation, identity và đường bypass.

**“Một kiểu proxy tốt nhất cho mọi chương trình.”** Không. Lựa chọn phụ thuộc hợp đồng kiểu, quyền kiểm soát đường gọi và các điểm cần can thiệp.

## 26. Bài tập luyện tư duy

**Bài 1 — Interface proxy và final class.** Class `FinalGateway` là final nhưng implements `GatewayPort`. Ứng dụng chỉ gọi qua `GatewayPort`. Dùng JDK dynamic proxy được không?

**Gợi ý:** có thể nếu `GatewayPort` là interface hợp lệ theo `Proxy` và loader/module đáp ứng; proxy hiện thực interface, không cần subclass `FinalGateway`.

**Bài 2 — Concrete API.** Một thư viện yêu cầu tham số đúng kiểu `Worker`, không nhận `Port`. Có thể truyền JDK dynamic proxy của `Port` không?

**Gợi ý:** không thể ép interface proxy thành `Worker` nếu proxy không kế thừa `Worker`. Cần thay API, adapter ở ranh giới khác, subclass proxy hợp lệ hoặc giải pháp tương đương.

**Bài 3 — Self-invocation.** `Worker.batch` gọi `this.execute`; đối tượng đang chạy là `WorkerSubclassProxy` và `execute` có override. Lời gọi nào chạy?

**Gợi ý:** virtual dispatch có thể gọi override trong subclass. Khác với interface handler ủy quyền vào `Worker target` riêng.

**Bài 4 — Final method.** Subclass kế thừa `public final version()`. Vì sao không in advice bằng cách override?

**Gợi ý:** Java cấm override final method. Muốn quan sát phiên gọi phải đặt interception ở boundary khác được hỗ trợ.

**Bài 5 — Constructor.** Base constructor gọi method có thể override; subclass proxy chưa khởi tạo state riêng. Rủi ro gì?

**Gợi ý:** override có thể chạy với state chưa sẵn sàng; tránh gọi overridable methods trong constructor và xem ràng buộc proxy generation.

**Bài 6 — Exception.** Handler gọi `Method.invoke` và target ném `IllegalArgumentException`. Handler nhận gì?

**Gợi ý:** reflection thường đưa `InvocationTargetException`; handler có thể lấy cause để trả lại lỗi gốc. Checked exceptions không khai báo cần xét `UndeclaredThrowableException`.

**Bài 7 — Đa luồng.** Handler giữ biến `currentRequest` dùng chung để log. Hai request chạy cùng lúc. Điều gì có thể sai?

**Gợi ý:** event có thể bị gán nhầm request; nên lưu state theo invocation và truyền context đúng ranh giới.

**Bài 8 — Kết quả trả Future.** Advice after-returning in “đã thành công” khi method trả `CompletableFuture`. Có chắc task hoàn tất chưa?

**Gợi ý:** không. Method return và future completion khác ranh giới; cần quan sát completion nếu chính sách yêu cầu.

## 27. Checklist chọn kiểu proxy cho hệ thống thực

- [ ] Xác định rõ điểm can thiệp là **lời gọi qua proxy**, không chỉ chữ ký method.
- [ ] Liệt kê contract mà caller cần: interface hay concrete class.
- [ ] Biết target có final/sealed hay method final/private/static cần quan sát không.
- [ ] Biết subclass cần constructor nào và có thể định nghĩa hợp lệ không.
- [ ] Kiểm tra class loader/module và các interface được phép proxy.
- [ ] Xác định subclass là **receiver thực** hay delegating tới target khác.
- [ ] Có test self-invocation và gọi trực tiếp target.
- [ ] Kiểm tra `equals`, `hashCode`, `toString` và identity.
- [ ] Không để state đã tạo ở target bị mất khi chuyển sang proxy khác.
- [ ] Không lưu context theo invocation vào shared mutable field không bảo vệ.
- [ ] Có test return value, checked/unchecked, cause và side effect.
- [ ] Đã xác định thứ tự nhiều advice/proxy.
- [ ] Có test negative chứng minh không intercept method ngoài chính sách.
- [ ] Phân biệt method return với completion bất đồng bộ.
- [ ] Đo startup, class generation, throughput, tail latency, allocation.
- [ ] Không ghi token, mật khẩu hoặc dữ liệu nhạy cảm trong handler logging.
- [ ] Có kế hoạch nâng cấp, debug và phương án thay thế nếu proxy không phù hợp.
- [ ] Nếu chính sách bắt buộc không được bypass, kiến trúc phải chặn được mọi đường cần bảo vệ.

## 28. Tài liệu đối chiếu Java 17

Các tài liệu sau giúp xác minh những giới hạn **API Java cụ thể** được dùng làm ví dụ; chúng không định nghĩa toàn bộ AOP:

- [Java SE 17 — Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): tạo proxy implement interface và giới hạn interface/class loader/module.
- [Java SE 17 — InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): quy tắc dispatch và xử lý default method.
- [Java SE 17 — Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): reflective invocation và `InvocationTargetException`.
- [Java Language Specification — Overriding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8): override, final methods và quy tắc kế thừa.

Khi đưa AOP qua proxy vào môi trường thực, cần thêm tài liệu chính thức của **công cụ đang tạo proxy**: cách chọn join point, cơ chế delegate hay super invocation, precedence, lifecycle và giới hạn hoạt động.

## 29. Kết luận

**Interface proxy** là lựa chọn phù hợp khi ứng dụng giao tiếp qua một hợp đồng interface và có thể điều khiển đường gọi để lời gọi đi qua một handler **ủy quyền tới target riêng**. **Subclass proxy** phù hợp khi cần quan hệ subtype với class cụ thể và class/method được phép kế thừa, override. Nhưng subclass proxy không tự giải quyết mọi vấn đề: final/private/static/constructor, state, lifecycle và cả kiểu ủy quyền nội bộ đều ảnh hưởng kết quả.

Điểm mấu chốt là **đừng chọn proxy chỉ vì tên kỹ thuật**. Hãy xác định ai là receiver, lời gọi đi qua những đối tượng nào, method có virtual dispatch hay không, target có được dùng trực tiếp ở đâu không, và hợp đồng kết quả/ngoại lệ phải được giữ thế nào.

Một thiết kế AOP qua proxy đáng tin cậy là thiết kế có **ranh giới rõ, coverage kiểm thử được, không bypass những chính sách bắt buộc và không làm sai hành vi nghiệp vụ**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-003">Đối tượng trung gian và đối tượng đích có danh tính khác nhau thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng trung gian và đối tượng đích có danh tính khác nhau thế nào trong AOP qua proxy

## 1. Proxy và target là ai?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một kỹ thuật triển khai mối quan tâm xuyên cắt là tạo một **proxy** làm trung gian giữa bên gọi (**caller**) và object thực hiện nghiệp vụ (**target**). Caller giữ reference đến proxy, gửi invocation qua nó; interceptor có thể chạy advice trước/sau/bao quanh hành vi gốc, rồi ủy quyền tới target nếu chính sách cho phép.

~~~text
Caller ---> Proxy P ---> Interceptor/advice ---> Target T
               |                                |
         object trung gian               object nghiệp vụ
~~~

**Với proxy dạng wrapper có target instance riêng, P và T là hai object khác nhau trong Java.** Chúng có thể cùng implement một interface, cùng đại diện một service hoặc dùng cùng dữ liệu nghiệp vụ, nhưng **reference identity của chúng khác nhau**.

Cần phân biệt:

1. **Reference identity:** hai reference có trỏ đúng cùng object Java không?
2. **Logical identity:** hai object có đại diện cùng entity/service/tài nguyên không?
3. **Equality:** P.equals(T) và T.equals(P) được định nghĩa thế nào?
4. **Runtime type:** class thực tế của P có giống class T không?
5. **Call path:** lời gọi có qua proxy để kích hoạt advice không?
6. **State và lifecycle:** state, lock, cache key, handler và target tồn tại theo phạm vi nào?
7. **AOP this/target:** vai trò ở join point có phải chính P/T không?

**Không được kết luận mọi cơ chế AOP đều có hai instance P và T.** Một subclass-based proxy có thể sử dụng chính generated subclass instance để xử lý logic; một binary weaver có thể chèn code trực tiếp vào method body mà không cần proxy tách biệt. Phải xác định chiến lược runtime cụ thể.

## 2. Cùng interface không đồng nghĩa cùng reference

Xét khai báo minh họa:

~~~java
interface Service {
    int compute(int value);
}
~~~

Một factory có thể tạo target Core và wrapper P đều implement Service:

~~~text
reference core: Service  --------> Core T
reference observed: Service -----> Proxy P -----> Core T
~~~

Khi caller thực hiện observed.compute(3), lời gọi đi qua handler. Nếu thực hiện core.compute(3), lời gọi đi trực tiếp tới target. Hai lần có thể trả cùng kết quả nhưng chỉ một lần kích hoạt interceptor của P.

Type khai báo **Service** chỉ nói object tương thích API, không chứng minh object là Core, proxy hoặc bất cứ implementation cụ thể nào. Đây là nguyên nhân nên thiết kế API theo interface ổn định, còn test coverage AOP phải kiểm tra reference thực tế được cấp cho caller.

## 3. Reference identity: toán tử ==

Với object reference trong Java, **==** trả true khi hai reference chỉ cùng object (hoặc cả hai null), chứ không so dữ liệu nghiệp vụ.

Trong wrapper model:

~~~text
P == T    --> false
P == P    --> true

Tạo P1 và P2 là hai wrapper mới cùng delegate T:
P1 == P2  --> false
P1 == T   --> false
P2 == T   --> false
~~~

Hai proxy cùng target có thể quan sát cùng business state và return value. Chúng vẫn là các object riêng nếu được tạo bằng hai lần cấp phát instance.

**Logical identity** là câu hỏi khác. Một service có thể đại diện một chức năng; một entity có thể có ID cố định. Hai object biểu diễn cùng entity có thể cần được coi là tương đương theo domain contract, nhưng điều đó **không thay đổi kết quả ==**.

Một proxy được tạo lại sau đổi cấu hình sẽ có object identity mới dù target cũ vẫn tồn tại. Điều này ảnh hưởng cache keyed by reference, monitor synchronization, metrics gắn instance và lifecycle.

## 4. Equals không giống ==

**equals** của Object mặc định có identity semantics khi class không override, nhưng class nghiệp vụ có thể so sánh ID hoặc value fields. Proxy có thể chuyển equals vào InvocationHandler, rồi handler chủ động chọn policy.

| Câu hỏi | Cơ chế | Nhận xét |
|---|---|---|
| P và T có cùng object? | P == T | False nếu wrapper/target riêng |
| P coi T bằng nhau? | P.equals(T) | Tùy implementation |
| T coi P bằng nhau? | T.equals(P) | Có thể khác nếu viết sai |
| P và T có cùng class? | P.getClass() == T.getClass() | Thường false với JDK interface proxy |
| P dùng interface Service? | P instanceof Service | Có thể true |
| P là Core object? | P instanceof Core | False trong ví dụ JDK proxy |

### 4.1. Hợp đồng equality

Java yêu cầu equals có các tính chất:

- **Reflexive:** x.equals(x) là true.
- **Symmetric:** x.equals(y) và y.equals(x) thống nhất.
- **Transitive:** nếu x bằng y và y bằng z thì x bằng z.
- **Consistent:** không đổi tùy tiện nếu dữ liệu so sánh không đổi.
- **Non-null:** x.equals(null) false.
- Nếu x.equals(y) thì x.hashCode() phải bằng y.hashCode().

### 4.2. Nguy hiểm khi forward equals xuống target

Một handler có thể viết theo ý tưởng:

~~~text
P.equals(X) := T.equals(X)
~~~

Với T dùng identity equality mặc định:

~~~text
P.equals(T) = T.equals(T) = true
T.equals(P) = false
~~~

Điều này **phá tính đối xứng**. Vì vậy không được forward tất cả method, kể cả Object.equals, một cách máy móc.

Các chiến lược hợp lý:

1. **Identity theo wrapper:** proxy chỉ bằng chính nó và hashCode identity-based.
2. **Logical equality theo domain key:** cần bảo đảm mọi class tham gia cùng một contract đối xứng/bắc cầu.
3. **Tách domain key:** dùng một ID/value object độc lập cho cache và nghiệp vụ, giữ identity của wrapper riêng.

Với nhiều service không có entity key, cách 1 thường dễ kiểm soát; khi cần logical identity, cách 3 thường rõ nghĩa hơn. Không có giải pháp phổ quát cho mọi domain.

## 5. HashCode và IdentityHashMap

**P.hashCode()** có thể do InvocationHandler xử lý; nó không nhất thiết bằng **System.identityHashCode(P)**. Identity hash là một giá trị số từ quy tắc identity hashing, **không phải định danh duy nhất tuyệt đối** vì có thể trùng số.

**HashMap** và **HashSet** dựa trên equals/hashCode. **IdentityHashMap** phân biệt key bằng reference equality. Trong wrapper model, P và T là hai key khác nhau của IdentityHashMap, ngay cả khi một equality policy coi chúng bằng nhau.

Ví dụ khái niệm:

~~~text
map.put(P, "tracked")
map.get(P) --> "tracked"
map.get(T) --> có thể không thấy nếu equality khác nhau
~~~

Nếu cache nên gộp nhiều proxy theo cùng entity/service logic, dùng một **key nghiệp vụ bất biến** và tài liệu hóa ý nghĩa, không dựa vào tên class hoặc identityHashCode của proxy.

### 5.1. ToString không đại diện identity

Handler có thể trả **TrackedProxy** cho toString; hai proxy khác nhau có thể cùng trả tên này. Không dùng toString làm ID duy nhất, khóa cơ sở dữ liệu, correlation key hoặc bằng chứng P == T.

## 6. Runtime class và type compatibility

**JDK dynamic proxy** implement interface mà factory yêu cầu. Nó không trở thành instance của concrete target class chỉ vì nó delegate đến target.

Một tình huống điển hình:

~~~text
                 Service interface
                  /            \
          Core target T        JDK proxy P
          implements           implements
~~~

Hệ quả trong chương trình mẫu:

- P instanceof Service là true.
- T instanceof Service là true.
- P instanceof Core là false.
- P.getClass() == T.getClass() là false.
- P == T là false.

Không nên cố cast JDK interface proxy thành Core để gọi method không nằm trong interface. Tương tự, không nên dùng tên generated proxy class làm nguồn sự thật về implementation; tên đó có thể thay đổi theo nền tảng và version.

### 6.1. Subclass proxy khác

Một class-based proxy có thể là subclass của concrete implementation, khiến **P instanceof Core** có thể true nếu engine/type cho phép. Nhưng instanceof vẫn không chứng minh **P == T**. Một số engine dùng proxy subclass làm receiver chính; một số giữ delegate target riêng.

### 6.2. Bytecode weaving khác proxy

Một bộ dệt bytecode có thể instrument trực tiếp **execution join point** của Core.method. Khi đó không cần P riêng để có advice. Nếu code tự gọi method nội bộ trong class đã weave, execution advice có thể vẫn chạy tùy engine/pointcut. Không áp giới hạn JDK proxy cho mọi AOP.

## 7. Object methods và InvocationHandler

Một **InvocationHandler** của JDK dynamic proxy có thể nhận lời gọi của **equals, hashCode, toString**. Nó còn nhận method được gọi qua interface Service, bao gồm Object[] args runtime.

Handler cần quyết định rõ:

~~~text
if method == equals:
    return proxy == other
if method == hashCode:
    return identityHashCode(proxy)
if method == toString:
    return "TrackedProxy"
otherwise:
    run advice
    invoke target method
~~~

Đây là giả mã, không phải Java độc lập.

Một lời gọi proxy.toString() không nhất thiết đi qua code Object.toString mặc định; handler có thể trả một chuỗi tùy chỉnh. Một lời gọi proxy.equals(target) không được giả định delegate chính xác tới target.equals(target).

Các method của Object như getClass, wait và notify có ngữ nghĩa nền tảng riêng; không đồng nhất toàn bộ Object methods với các method được JDK dynamic proxy chuyển tới handler.

## 8. Ví dụ Java 17 chạy được: P và T có identity khác nhau

Chương trình sau sử dụng **JDK dynamic proxy** để quan sát trực tiếp:

- P và T khác reference/class.
- P implement Service nhưng không phải Core.
- P xử lý equals/hashCode/toString theo identity policy.
- Target trả **this**, có thể lộ reference T.
- Self-invocation của T không quay lại handler P.
- Số lượt interception khác số lần body target chạy.

Tạo file **ProxyIdentityDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyIdentityDemo {
    interface Service {
        int run(int value);
        void internal();
        Service self();
    }

    static final class Core implements Service {
        private int runs;
        private int internals;

        @Override
        public int run(int value) {
            runs++;
            System.out.println("core: run " + value);
            internal();
            return value * 2;
        }

        @Override
        public void internal() {
            internals++;
            System.out.println("core: internal");
        }

        @Override
        public Service self() {
            return this;
        }
    }

    static Service observe(Service target, AtomicInteger observedCalls) {
        return (Service) Proxy.newProxyInstance(
            Service.class.getClassLoader(),
            new Class<?>[] {Service.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "TrackedProxy";
                        default -> throw new UnsupportedOperationException();
                    };
                }

                observedCalls.incrementAndGet();
                System.out.println("advice: " + method.getName());
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    public static void main(String[] args) {
        Core core = new Core();
        AtomicInteger calls = new AtomicInteger();
        Service observed = observe(core, calls);

        System.out.println("proxy == target: " + (observed == core));
        System.out.println("proxy instanceof Service: " + (observed instanceof Service));
        System.out.println("proxy instanceof Core: " + (observed instanceof Core));
        System.out.println("target instanceof Service: " + (core instanceof Service));
        System.out.println("same runtime class: " + (observed.getClass() == core.getClass()));
        System.out.println("proxy.equals(proxy): " + observed.equals(observed));
        System.out.println("proxy.equals(target): " + observed.equals(core));
        System.out.println("target.equals(proxy): " + core.equals(observed));
        System.out.println("hashCode uses proxy identity: "
            + (observed.hashCode() == System.identityHashCode(observed)));
        System.out.println("proxy.toString(): " + observed);

        System.out.println("proxy.self() == target: " + (observed.self() == core));
        System.out.println("proxy.self() == proxy: " + (observed.self() == observed));

        System.out.println("through proxy:");
        System.out.println("result=" + observed.run(3));

        System.out.println("through returned target:");
        System.out.println("result=" + observed.self().run(4));

        System.out.println("direct target:");
        System.out.println("result=" + core.run(5));

        System.out.println("direct proxy internal:");
        observed.internal();

        System.out.println("core runs=" + core.runs);
        System.out.println("core internals=" + core.internals);
        System.out.println("intercepted calls=" + calls.get());
    }
}
~~~

Biên dịch và chạy trên Java 17:

~~~bash
javac --release 17 ProxyIdentityDemo.java
java ProxyIdentityDemo
~~~

**Stdout chính xác:**

~~~text
proxy == target: false
proxy instanceof Service: true
proxy instanceof Core: false
target instanceof Service: true
same runtime class: false
proxy.equals(proxy): true
proxy.equals(target): false
target.equals(proxy): false
hashCode uses proxy identity: true
proxy.toString(): TrackedProxy
advice: self
proxy.self() == target: true
advice: self
proxy.self() == proxy: false
through proxy:
advice: run
core: run 3
core: internal
result=6
through returned target:
advice: self
core: run 4
core: internal
result=8
direct target:
core: run 5
core: internal
result=10
direct proxy internal:
advice: internal
core: internal
core runs=3
core internals=4
intercepted calls=5
~~~

### 8.1. P == T false nhưng cùng Service

**observed** là một object dynamic proxy; **core** là instance Core. Chúng cùng implement Service nên caller có thể gọi run/internal/self qua interface. Nhưng JVM nhận diện chúng là hai object và hai runtime classes khác nhau.

### 8.2. Equality của proxy được quy định trong handler

Handler chủ động định nghĩa P.equals(P) là true, P.equals(T) là false và P.hashCode() dùng identityHashCode(P). Core không override equals nên T.equals(P) là false. Đây là một policy **của demo**, không phải đáp án bất biến cho mọi proxy.

### 8.3. Return-this làm lộ target

Core.self trả reference **this của Core**, không phải P. Handler chuyển kết quả nguyên trạng. Vì vậy:

~~~text
observed.self() -> handler -> core.self() -> Core T
~~~

Sau đó observed.self().run(4) gọi trực tiếp trên T nên có **advice: self**, nhưng không có **advice: run** ở lần gọi ấy.

Một số engine có thể chọn trả proxy khi target method trả this, tùy contract và khả năng của engine; điều đó **không được mặc định**.

### 8.4. Self-invocation bỏ qua JDK proxy

Core.run tự gọi internal() trên chính Core. Handler không nhận được lời gọi internal từ nội bộ ấy. Nhưng observed.internal() do caller phát qua proxy được handler quan sát.

Do đó:

- **core runs=3**: body run chạy 3 lần.
- **core internals=4**: ba self-calls từ run và một call qua proxy.
- **intercepted calls=5**: ba lần self, một run, một internal đi qua proxy.

**Advice invocation count không bằng method execution count** trong mô hình này.

### 8.5. Exception propagation

Method.invoke có thể bao exception target ném vào InvocationTargetException. Handler unwrap cause để lan truyền lỗi nghiệp vụ phù hợp, tránh làm biến đổi exception contract chỉ vì có interceptor.

## 9. Target reference có thể thoát ra khỏi proxy bằng đường nào?

Không chỉ có self():

1. Factory cấp cả target T và proxy P cho hai thành phần khác nhau.
2. Target method trả this, handler trả nguyên object T.
3. Target đăng ký this vào registry, listener hoặc event bus.
4. Callback/lambda/method reference giữ receiver T từ trước khi tạo P.
5. Một component tự tạo Core bằng new, không qua factory cấp proxy.
6. Một API công bố target object qua reflection/introspection khi code có quyền.
7. Một caller giữ reference T từ lúc lắp ghép hệ thống, dù caller khác nhận P.

Mỗi trường hợp có thể làm bypass những concern chỉ được gắn vào P. Nếu AOP thực hiện chính sách bảo mật hay kiểm toán bắt buộc, cần kiểm soát tất cả đường có thể gọi T, hoặc sử dụng cơ chế có độ bao phủ phù hợp và đã được kiểm chứng.

### 9.1. Fluent API

Một method builder thường trả this:

~~~java
Service configure(int level) {
    this.level = level;
    return this;
}
~~~

Đây là đoạn minh họa chưa đầy đủ. Nếu P delegate configure đến T và trả nguyên reference T, chuỗi:

~~~text
P.configure(3).execute()
~~~

có thể chỉ intercept configure, còn execute chạy trực tiếp trên T. Một số proxy engine có chính sách xử lý return-this riêng; cần test rõ thay vì suy đoán.

### 9.2. Callback và method reference

Nếu callback đóng trên T, invocation xảy ra trong tương lai vẫn có thể đi thẳng T, không qua P. Muốn advice chạy phải tổ chức callback giữ proxy thích hợp hoặc instrument execution tại điểm mà engine hỗ trợ.

## 10. This/target trong pointcut không mặc định là P/T

Một ngôn ngữ AOP có thể có pointcut designators **this** và **target**. Nhưng ý nghĩa của chúng gắn với **join point kind**, không phải tên biến trong InvocationHandler.

Trong một mô hình như AspectJ:

- Ở method-call join point, **this** thường là object đang thực thi mã phía caller; **target** là receiver được gọi nếu có.
- Ở method-execution join point của instance method, **this** và **target** thường là object đang thực thi method.
- Trong static context, một số điểm không có instance this/target.

~~~text
Caller C gọi object T.work()

Call join point:
    this ≈ C, target ≈ T

Execution join point:
    this ≈ T, target ≈ T
~~~

Sơ đồ này là mô tả khái niệm trong mô hình có call/execution join points, không phải ngữ nghĩa chung của mọi công cụ AOP.

Với JDK dynamic proxy, biến **proxy** của InvocationHandler là object P mà JDK chuyển cho handler, còn biến **target** thường là reference T mà handler giữ. **Đừng ánh xạ hai biến ấy sang designators this/target của mọi pointcut language**.

### 10.1. Per-this và per-target

Một số mô hình AOP có cách tạo aspect instance theo this hoặc target tại join point. Ví dụ ở call join point, **per-this** có thể phân biệt các caller C1/C2, còn **per-target** có thể gắn state với T.

Nếu có nhiều wrapper P1/P2 cùng delegate T, cần quyết định rõ state/metrics thuộc về wrapper, target, caller hay logical entity; không suy ra một mapping từ tên per-this/per-target.

## 11. Proxy identity và synchronized

Java sử dụng **monitor của object receiver** với synchronized:

~~~java
synchronized (proxy) {
    // lock P
}

synchronized (target) {
    // lock T
}
~~~

Đoạn minh họa cần biến proxy/target được định nghĩa. Nếu P và T là hai object, **hai monitor khác nhau**, kể cả khi P.equals(T) được tùy chỉnh để true.

Một caller giữ monitor P không tự bảo vệ biến mutable trong T khỏi code khác synchronized(T). Chọn một lock ổn định được mọi đường dùng chung, hoặc đặt synchronization tại nơi thực sự sở hữu invariant.

Các thao tác Object.wait/notify cũng gắn với monitor của object, không theo equals. Không thể đợi trên P và thông báo trên T mà coi là cùng monitor.

### 11.1. Concurrency và interceptor state

P có thể giữ counters, cache hoặc một handler mutable. Nếu nhiều thread gọi qua P, handler phải được thiết kế thread-safe theo contract. Một target T được nhiều proxy P1/P2 delegate vẫn có thể có race trên business fields nếu T không thread-safe.

Proxy không tự đồng bộ mọi method chỉ nhờ tồn tại.

## 12. State, lifetime và garbage collection

Một wrapper thường tạo object graph:

~~~text
Caller -> Proxy P -> Handler H -> Target T
~~~

Nếu Caller giữ P và H giữ T bằng strong reference, T có thể còn reachable. Nhưng T cũng có thể sống nhờ một reference khác sau khi P không còn được sử dụng.

Các câu hỏi cần trả lời:

- Proxy được tạo mỗi request hay tái sử dụng?
- Target có state theo request, session hay application?
- Nhiều proxy có cùng target không?
- Handler có giữ target mạnh, hay lấy từ provider khác?
- Một callback giữ reference P cũ hay T cũ?
- Cache dùng P identity làm key hay dùng stable business key?
- Có classloader/cache nào giữ generated proxy class lâu hơn dự kiến không?

Một WeakHashMap keyed by P không tự có cùng lifecycle với T; value có thể gián tiếp giữ key hoặc handler có reference mạnh khác. Hãy phân tích reachability/object graph thay vì đoán qua tên proxy.

## 13. Nhiều lớp proxy có thể làm identity và số lượt advice phức tạp hơn

~~~text
Caller -> P2 (tracing) -> P1 (authorization) -> T (business)
~~~

Ở wrapper model, P2, P1 và T đều có thể là ba object reference riêng. Caller dùng P2 sẽ qua tracing và authorization; caller có P1 thì thiếu tracing của P2; caller giữ T sẽ bỏ qua cả hai interceptor.

Nếu P2 short-circuit, P1/T có thể không chạy. Nếu P2 retry, P1/T có thể chạy nhiều lần cho một caller invocation. Vì vậy phải phân biệt:

- Số lượt vào proxy ngoài.
- Số lượt vào proxy trong.
- Số lần target thực thi.
- Số self-calls không đi qua proxy.
- Số result/exception đã quan sát.

Không được cộng mọi counter rồi gọi là số request.

### 13.1. Equality qua nhiều proxy

Nếu P2 forward equals về P1, còn P1 forward về T, có thể phát sinh sự khác biệt giữa P2.equals(T), T.equals(P2) và hashCode. Nên giữ một policy nhất quán và dùng domain key riêng cho các yêu cầu so sánh business identity.

## 14. Cache, reflection và serialization

### 14.1. Cache keyed by object

Một HashMap keyed by proxy có thể miss khi tra bằng target; một IdentityHashMap chắc chắn phân biệt P/T ở mô hình wrapper riêng. Nếu ý nghĩa là cùng một entity, dùng immutable entity ID.

### 14.2. Reflection

Một annotation resolver đọc P.getClass() có thể nhận generated proxy class thay vì Core. JDK proxy InvocationHandler có thể nhận Method từ interface, còn annotation lại đặt trên implementation method. Nguồn metadata cần được xác định đúng: interface contract, implementation hoặc join point description.

### 14.3. Serialization

Serialize/deserialize một proxy không có nghĩa giữ nguyên reference identity. Khả năng serialize còn phụ thuộc handler/target và contract công nghệ; kết quả thường là object mới nếu serialization được hỗ trợ. Test equality, state, cache và wiring sau deserialize thay vì mong P == P cũ.

### 14.4. Classloader

Java class identity liên quan binary name và defining ClassLoader. Hai interface/class cùng tên nhưng từ loader khác có thể không tương thích kiểu. Proxy factory phải dùng loader có thể thấy những interface được yêu cầu, còn binding/matching phải chú ý classloader context.

## 15. Ứng dụng vào các concern AOP

| Concern | Câu hỏi identity/coverage phải hỏi |
|---|---|
| Tracing | Lời gọi nào không qua proxy làm mất event? |
| Metrics | Counter đo proxy invocation hay target execution? |
| Authorization | Target reference thoát ra làm bypass policy không? |
| Cache | Cache theo wrapper identity hay theo business key? |
| Retry | Một caller call làm target chạy mấy lần? |
| Logging | toString/getClass của P có làm hiểu sai implementation không? |
| Audit | Có đường gọi nội bộ/direct thiếu sự kiện bắt buộc không? |
| Concurrency | Lock trên P hay T, hai đường có chung monitor không? |
| Lifecycle | P được tạo mới có giữ T cũ hoặc handler cũ không? |

Đối với một concern bắt buộc, xác nhận coverage trên những đường thực tế quan trọng hơn việc code advice chạy trong một test happy path.

## 16. Quy trình kiểm chứng một proxy AOP

1. **Xác định strategy**: JDK interface proxy, decorator, subclass proxy hay bytecode weaving.
2. **Vẽ object graph**: ai giữ P, ai giữ T, handler delegate đến đâu.
3. **Kiểm tra reference identity**: P == T ở mô hình cụ thể.
4. **Kiểm tra runtime type**: instanceof interface/class, getClass.
5. **Kiểm tra equality/hashCode**: reflexive/symmetric/transitive, hash consistency.
6. **Kiểm tra direct vs proxied calls**: đúng số lượt interceptor và target.
7. **Kiểm tra self-invocation và return this**: reference nào được trả và chain có bypass không.
8. **Kiểm tra callbacks và multiple proxies**: caller có được cấp đúng boundary không.
9. **Kiểm tra concurrency**: lock nào bảo vệ state nào.
10. **Kiểm tra exception**: reflection wrapper và advice failures.
11. **Kiểm tra lifecycle/cache**: thay proxy, thay target, serializing nếu liên quan.
12. **Tạo integration tests** cho positive và negative join point coverage.

Để tránh hỏng equality, có thể viết riêng test dùng HashMap, IdentityHashMap, hai proxy bao cùng target và trường hợp proxy/target ở cả hai vế của equals.

## 17. Ma trận test gợi ý

| Kịch bản | Kỳ vọng cần xác minh |
|---|---|
| P và T là wrapper riêng | P == T false |
| Hai proxy cùng target | P1 == P2 false nếu tạo hai object |
| JDK proxy | P instanceof interface true |
| JDK interface proxy vs Core | P instanceof Core false |
| Equals/hashCode | Tuân theo hợp đồng Java và policy đã chọn |
| ToString | Không coi text là identity |
| Return this | Có trả T thô hay được map về P? |
| Fluent chain | Bước tiếp theo còn đi qua handler không? |
| Target self-call | Handler có bị bypass không? |
| Direct target call | Có làm thiếu policy bắt buộc không? |
| Multiple proxy layers | Số và thứ tự advice đúng |
| Exception target | Handler có unwrap/lan truyền phù hợp? |
| synchronized(P)/synchronized(T) | Không nhầm hai monitor |
| Cache/Map keyed by P | Có tìm bằng T được theo policy không? |
| Recreate proxy | Reference identity và cache key thay đổi thế nào? |
| Reflection annotation | Đang đọc interface hay implementation? |
| Classloader khác | Generated proxy type có tương thích không? |

## 18. Những lỗi phổ biến

**Lỗi 1:** Coi proxy và target là một object vì cùng interface.

**Lỗi 2:** Coi proxy là bản sao đầy đủ của target, trong khi nó có thể chỉ delegate.

**Lỗi 3:** Coi equals luôn tương đương toán tử == hoặc ngược lại.

**Lỗi 4:** Forward equals một chiều làm sai symmetry.

**Lỗi 5:** Coi identityHashCode là ID duy nhất.

**Lỗi 6:** Cast JDK interface proxy thành implementation class.

**Lỗi 7:** Dùng proxy.getClass để tra annotation chỉ có trên target class.

**Lỗi 8:** Target trả this gây target leak, làm các call sau bỏ qua advice.

**Lỗi 9:** Coi target self-invocation là proxy invocation.

**Lỗi 10:** synchronized trên P rồi giả định bảo vệ T.

**Lỗi 11:** Coi perthis/pertarget là tên khác của proxy/target bất kể join point.

**Lỗi 12:** Dùng proxy reference làm domain ID cho cache.

**Lỗi 13:** Nhiều proxy cùng delegate T nhưng counters được coi là một.

**Lỗi 14:** Tưởng mọi class-based proxy có hai object riêng.

**Lỗi 15:** Chỉ test return value mà không test số advice invocations.

**Lỗi 16:** Cho target thô thoát ra ngoài khi cần interceptor bắt buộc.

## 19. Câu hỏi ôn tập và đáp án

**Câu 1.** Hai biến đều có compile-time type Service. Chúng chắc chắn == nhau?

**Đáp:** Không; chúng có thể giữ P và T khác nhau.

**Câu 2.** P != T nhưng cả hai trả cùng kết quả getBalance. Mâu thuẫn không?

**Đáp:** Không; P có thể delegate trực tiếp tới T.

**Câu 3.** P.equals(T) true, T.equals(P) false có lỗi gì?

**Đáp:** Vi phạm tính đối xứng của equals.

**Câu 4.** IdentityHashMap có gộp P và T khi equals trả true không?

**Đáp:** Không; nó dùng reference identity.

**Câu 5.** Vì sao observed.self().run(4) trong demo không có advice: run?

**Đáp:** Self trả target T nên run được gọi trực tiếp trên T.

**Câu 6.** Vì sao Core.run tự gọi internal mà handler không nhận thêm event?

**Đáp:** Self-invocation trên T không đi qua reference proxy P.

**Câu 7.** P instanceof Service true thì P instanceof Core chắc true không?

**Đáp:** Không; JDK dynamic proxy không là Core instance.

**Câu 8.** synchronized(P) và synchronized(T) có cùng monitor nếu P.equals(T) true không?

**Đáp:** Không; monitor dựa vào reference identity.

**Câu 9.** Pointcut this() ở method-call join point luôn chỉ P?

**Đáp:** Không; có thể chỉ caller object theo semantics của ngôn ngữ AOP.

**Câu 10.** Một subclass proxy instanceof Core true chứng minh nó cùng reference với T không?

**Đáp:** Không; quan hệ type khác object identity.

**Câu 11.** Nhiều proxy cùng T muốn cache chung theo entity ID nên dùng gì?

**Đáp:** Domain key ổn định, không mặc định dùng proxy reference.

**Câu 12.** Có thể dùng proxy kiểm tra quyền bắt buộc mà vẫn cho caller giữ T gốc không?

**Đáp:** Rất rủi ro; đường gọi T có thể bypass kiểm tra. Cần thiết kế boundary và coverage phù hợp.

## 20. Sơ đồ ghi nhớ

~~~text
                    Caller
                       |
                Service reference
                       |
                       v
                   Proxy P
               identity của P
               class của P
               equals/hashCode của P
                       |
                       v
                 Interceptor
                       |
                       v
                   Target T
               identity của T
               class của T
               business state của T
                       |
                       v
                  Method body
                       |
                       +-- return value
                       +-- throw exception
                       +-- return this (có thể trả T thô)
                                   |
                                   v
                           Caller có thể bypass P
~~~

**Kết luận:** với proxy wrapper và target riêng, **P != T theo reference identity**, dù cả hai có thể cùng Service interface, logic nghiệp vụ và dữ liệu. Equality không tự được hợp nhất; runtime class/monitor/lifecycle có thể khác nhau; return-this, direct reference và self-invocation có thể bypass proxy. Khi dùng AOP, hãy xác nhận **đúng object đang nhận lời gọi, đúng loại join point, đúng equality contract và đúng giới hạn của cơ chế interception**.

## Tài liệu đối chiếu

- [Java SE 17 — Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): JDK dynamic proxy và Object methods.
- [Java SE 17 — InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): tham số proxy/Method/args và exception contract.
- [Java SE 17 — Object](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): equals, hashCode, getClass và monitor.
- [Java SE 17 — IdentityHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html): equality theo reference.
- [Java Language Specification, Java SE 17 — Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html): reference comparison và dynamic method dispatch.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): this/target join point semantics và mô hình aspect instantiation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-004">Giới hạn của lời gọi nội bộ không đi qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của lời gọi nội bộ không đi qua proxy

## 1. Bản chất của vấn đề

Trong **Aspect-Oriented Programming (AOP)**, proxy là một cách triển khai hành vi xuyên suốt: caller gọi phương thức trên **proxy**, proxy lựa chọn advice phù hợp rồi chuyển tiếp lời gọi tới **target**. Khi target đang chạy, nó có thể gọi một phương thức khác của chính mình bằng `this.inner()` hoặc bằng lời gọi `inner()` không ghi rõ receiver. Với mô hình **delegating proxy tách biệt khỏi target**, lời gọi nội bộ ấy **không tự đi qua proxy**.

Kết quả cần phân biệt:

- **Method nội bộ vẫn thực sự chạy.**
- **Proxy handler không nhận một invocation riêng cho method nội bộ.**
- **Advice được gắn với invocation qua proxy của method nội bộ có thể không chạy.**
- Advice của **method outer** vẫn có thể chạy nếu caller đã gọi outer qua proxy.

Đây là giới hạn thường được gọi là **self-invocation bypass**. Không nên diễn giải thành quy tắc tuyệt đối của mọi công cụ AOP: một cơ chế weaving trực tiếp vào bytecode của method execution có thể quan sát self-call; một số dạng class proxy cũng có dispatch khác nhau. Cần xác định rõ **loại proxy**, **receiver thực tế** và **join-point model**.

### Mục tiêu học tập

Sau bài này bạn sẽ:

1. Phân biệt target, proxy, caller, join point, pointcut, advice và handler.
2. Vẽ đường gọi qua proxy và đường gọi qua `this`.
3. Giải thích tại sao metadata/annotation hay pointcut rộng hơn không tự sửa proxy bypass.
4. Phân tích hậu quả đối với tracing, metrics, retry, cache, ranh giới giao dịch, bảo mật và exception.
5. So sánh JDK interface proxy, class-based proxy, decorator và weaving trực tiếp.
6. Chọn các phương án tổ chức lại: coordinator, collaborator được bọc, explicit boundary hoặc weaving phù hợp.
7. Chạy một chương trình Java 17 có output chính xác để chứng minh self-call, direct target call và external call khác nhau.
8. Xây dựng positive/negative tests chứng minh **target thực thi** nhưng **advice inner không thực thi** trong đường bypass.

Bài chỉ xét AOP cơ bản và nâng cao **nói chung**, không dùng cú pháp hoặc hành vi mặc định của một framework cụ thể.

## 2. Cơ chế proxy theo từng tầng

Xét mô hình đơn giản:

~~~text
Caller
  |
  v
Proxy.outer(arguments)
  |
  v
Handler / interceptor chain
  |
  +-- kiểm tra pointcut
  +-- before advice
  +-- chuyển tiếp vào target
  |
  v
Target.outer(arguments)
  |
  v
Handler nhận return / throwable
  |
  +-- after-returning / after-throwing
  |
  v
Caller nhận result / throwable
~~~

Ba quyết định độc lập cùng ảnh hưởng đến kết quả:

| Tầng | Câu hỏi | Hậu quả nếu không thỏa |
| --- | --- | --- |
| **Interception path** | Invocation có đi qua object proxy không? | Handler không nhận lời gọi. |
| **Pointcut matching** | Invocation mà handler nhận có khớp điều kiện không? | Advice cụ thể có thể không chạy, dù target vẫn chạy. |
| **Runtime occurrence** | Đường code đã đi tới lời gọi ấy chưa? | Không có sự kiện nào để quan sát. |

Một annotation `@Observed` trên method chỉ là **metadata**. Nếu handler không nhìn thấy invocation của method ấy, annotation không có cơ hội được kiểm tra bởi handler tại đường gọi đó.

### 2.1. Một target và một proxy có thể cùng tồn tại

Trong JDK dynamic proxy, caller có thể giữ object proxy triển khai interface, còn handler giữ reference tới target:

~~~text
proxyRef  -> handler -> targetRef
                         |
                         +-- target method body
~~~

Target là một object có identity riêng. Khi body của target chạy, `this` là receiver của body đó: thông thường chính target được handler ủy quyền. Do đó `this.inner()` không tự biến thành `proxyRef.inner()`.

Điều này cũng giải thích một bypass khác: nếu một thành phần giữ **targetRef** và gọi thẳng vào nó, lời gọi đó không đi qua handler. Đó là direct-target bypass, không phải self-invocation, nhưng có cùng hậu quả với advice qua proxy.

### 2.2. Method outer có advice không đồng nghĩa method inner có advice

Giả sử pointcut của proxy khớp cả `outer` và `inner`.

~~~text
Caller -> Proxy.outer()
              |
          ADVICE outer trước
              |
          Target.outer()
              |
          this.inner()  --> Target.inner()
              |             (không có Proxy.inner invocation)
              |
          ADVICE outer sau
              |
          Caller
~~~

Method inner vẫn có thể thực hiện I/O, thay đổi trạng thái hoặc ném exception. Nhưng **không có before/after riêng cho inner từ handler của proxy** trong đường gọi ấy. Đếm số dòng log của handler không phải là phép đếm trung thực mọi method execution.

## 3. Khi nào self-invocation bypass xảy ra?

Điều kiện cần ở mô hình delegating proxy:

1. Caller đi vào proxy cho method outer (hoặc target body được gọi bằng cách khác).
2. Execution thực sự chuyển tới **body của target**, nơi `this` trỏ về target.
3. Body gọi một method khác trên chính receiver đó.
4. Lời gọi ấy không được định tuyến qua proxy/interceptor chain.
5. Advice dành cho invocation inner nằm **trên proxy boundary**, không nằm trực tiếp trong body inner đã weave.

Nếu một trong các điều kiện đổi, cần xem lại kết quả. Ví dụ engine có thể dệt hook tại entry của `inner` thay vì chỉ tại proxy invocation.

### 3.1. Lời gọi không ghi `this` cũng có thể là self-call

~~~java
class JobService {
    void outer() {
        inner();
    }

    void inner() {
        // xử lý
    }
}
~~~

Đây là **đoạn mã minh họa**, không phải chương trình độc lập. Trong tình huống method instance thông thường, `inner()` là lời gọi trên receiver hiện tại. Không phải vì thiếu chữ `this` mà lời gọi phải qua proxy.

### 3.2. Virtual dispatch không đồng nghĩa proxy dispatch

Java có thể dispatch một lời gọi tới method được override trong subclass. Điều đó là **đa hình tại runtime**. Nếu receiver vẫn là target thật, việc chọn override không có nghĩa handler của một object proxy riêng biệt đã chạy.

### 3.3. Method reference và lambda cũng có receiver

Một callback được đăng ký bằng `this::inner` trong target thường giữ reference tới **target**. Sau đó, khi callback chạy, lời gọi vẫn đi theo receiver đã capture. Một caller khác đăng ký `proxy::inner` sẽ có call path khác. Đường gọi phải được kiểm tra tại thời điểm callback được thực thi, không chỉ tại chỗ định nghĩa method.

### 3.4. Self-call có thể xảy ra nhiều lần

Một `outer` gọi `this.inner` trong vòng lặp 100 lần có thể tạo **100 execution của inner** nhưng chỉ **1 invocation qua proxy của outer**. Nếu instrumentation chỉ trên proxy inner, số liệu proxy inner có thể bằng 0 trong kịch bản đó. Vấn đề không liên quan đến số lượng method được viết trong source.

## 4. Pointcut đúng vẫn có thể không có advice

Pointcut mô tả **điều kiện lựa chọn**. Proxy mô tả **nơi interception xảy ra**. Chúng là hai tầng khác nhau.

Giả sử predicate:

~~~text
methodName == "inner"
AND methodHasAnnotation("Tracked")
~~~

Điều kiện đó có thể đúng cho method inner được khai báo trên interface, nhưng khi target tự gọi `this.inner()`, handler không nhận event để đánh giá predicate.

Các thay đổi sau **không tự khắc phục**:

- Mở rộng predicate thành mọi method.
- Thêm annotation vào implementation.
- Đổi `private` thành `public`.
- Đổi tên method để khớp wildcard.
- Đổi thứ tự các advice vốn chỉ chạy **sau khi** proxy nhận event.

Đặc biệt, annotation trên interface method và implementation method có thể được reflection tra ở hai declaration khác nhau. Đây là một vấn đề **metadata lookup** riêng biệt; dù khắc phục đúng metadata mà call path vẫn bypass thì handler inner vẫn không chạy.

### 4.1. Dấu hiệu chẩn đoán

Nếu log xuất hiện:

~~~text
ADVICE before outer
TARGET outer
TARGET inner
ADVICE returning outer
~~~

thì kết luận đúng **chỉ khi có bằng chứng từ code/test** là inner được target gọi trực tiếp: inner đã chạy nhưng advice inner không được handler nhìn thấy. Đừng suy “không có log = method chưa chạy”, vì có thể logger bị tắt hoặc pointcut không khớp.

## 5. Ví dụ Java 17 runnable: self-call, proxy-call, external-call

Chương trình sau dùng **JDK dynamic proxy** triển khai interface `Operations`, và một target `Service` chứa hai method `outer` và `inner`.

- `outer` gọi `this.inner` bên trong target.
- Handler ghi advice cho mọi method nó nhận.
- `Coordinator` là thành phần ngoài target, giữ proxy để gọi `inner`.
- `inner("BAD")` ném exception; handler ghi lỗi theo **method đã đi qua proxy**.
- Caller gọi `target.inner("DIRECT")` để chứng minh direct-target bypass.

Tạo file **`ProxySelfCallDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ProxySelfCallDemo {
    interface Operations {
        String outer(String id);
        String inner(String id);
    }

    static final class Service implements Operations {
        @Override
        public String outer(String id) {
            System.out.println("TARGET outer " + id);
            String value = this.inner(id);
            System.out.println("TARGET outer completed " + value);
            return "OUT:" + value;
        }

        @Override
        public String inner(String id) {
            System.out.println("TARGET inner " + id);
            if ("BAD".equals(id)) {
                throw new IllegalStateException("inner failed");
            }
            return "OK:" + id;
        }
    }

    static Operations instrument(Operations target) {
        return (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] { Operations.class },
                (proxy, method, args) -> {
                    System.out.println("ADVICE before " + method.getName());
                    try {
                        Object result = method.invoke(target, args);
                        System.out.println("ADVICE returning "
                                + method.getName());
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable original = wrapped.getCause();
                        System.out.println("ADVICE throwing "
                                + method.getName() + " "
                                + original.getClass().getSimpleName());
                        throw original;
                    }
                }
        );
    }

    static final class Coordinator {
        private final Operations operations;

        Coordinator(Operations operations) {
            this.operations = operations;
        }

        String executeInner(String id) {
            System.out.println("COORDINATOR invoke " + id);
            return operations.inner(id);
        }
    }

    public static void main(String[] args) {
        Service target = new Service();
        Operations proxy = instrument(target);

        System.out.println("CALLER " + proxy.outer("A"));
        System.out.println("CALLER " + proxy.inner("B"));

        Coordinator coordinator = new Coordinator(proxy);
        System.out.println("CALLER " + coordinator.executeInner("C"));

        try {
            proxy.outer("BAD");
        } catch (IllegalStateException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }

        try {
            proxy.inner("BAD");
        } catch (IllegalStateException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }

        System.out.println("CALLER " + target.inner("DIRECT"));
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac ProxySelfCallDemo.java
java ProxySelfCallDemo
~~~

**Stdout đúng thứ tự:**

~~~text
ADVICE before outer
TARGET outer A
TARGET inner A
TARGET outer completed OK:A
ADVICE returning outer
CALLER OUT:OK:A
ADVICE before inner
TARGET inner B
ADVICE returning inner
CALLER OK:B
COORDINATOR invoke C
ADVICE before inner
TARGET inner C
ADVICE returning inner
CALLER OK:C
ADVICE before outer
TARGET outer BAD
TARGET inner BAD
ADVICE throwing outer IllegalStateException
CALLER caught inner failed
ADVICE before inner
TARGET inner BAD
ADVICE throwing inner IllegalStateException
CALLER caught inner failed
TARGET inner DIRECT
CALLER OK:DIRECT
~~~

### 5.1. Tại sao outer có advice nhưng self-call inner không có?

Lời gọi `proxy.outer("A")` vào handler nên in `ADVICE before outer`. Handler thực hiện `method.invoke(target, args)`, làm body `Service.outer` chạy trên receiver target. Trong body, `this.inner("A")` là một lời gọi khác nhưng **không tới handler**. Target in `TARGET inner A`, hoàn tất outer, sau đó handler in `ADVICE returning outer`.

Nếu method inner không chạy, sẽ không có dòng `TARGET inner A` và kết quả outer không thể được tạo theo code đã viết. Bài học là phân biệt **execution** với **interception**.

### 5.2. Tại sao proxy.inner khác target.inner?

`proxy.inner("B")` gọi handler → target.inner, có `ADVICE before inner` và `ADVICE returning inner`. `target.inner("DIRECT")` gọi body trực tiếp, không qua handler. **Một implementation method**, nhưng hai đường gọi với interception khác nhau.

### 5.3. Coordinator minh họa biên ngoài rõ ràng

`Coordinator` nhận một `Operations` có object thực là proxy. Khi nó gọi `operations.inner("C")`, invocation thực đi qua handler. Không cần target biết mình được proxy hóa.

Nếu coordinator được cấp nhầm object `target` thay vì `proxy`, advice vẫn bị bypass. Do đó giải pháp cấu trúc phải đi kèm **wiring/ownership** chính xác.

### 5.4. Exception từ inner qua outer được gắn nhãn outer

`proxy.outer("BAD")` gọi target.outer; inner ném `IllegalStateException` và exception thoát qua outer. `Method.invoke` trên outer bọc exception trong `InvocationTargetException`. Handler gỡ `getCause()`, ghi **`ADVICE throwing outer IllegalStateException`** rồi ném lại cause. Handler không có event inner riêng nên không có `ADVICE throwing inner` ở đường này.

`proxy.inner("BAD")` tạo invocation proxy cho inner nên handler ghi **`ADVICE throwing inner IllegalStateException`**.

### 5.5. Ví dụ này KHÔNG chứng minh điều gì?

- Không phải mọi class-based proxy đều có cùng cách dispatch.
- Không chứng minh bytecode method inner đã được weave.
- Không chặn private method, constructor, static method hoặc field access.
- Không chứng minh mọi caller bắt buộc giữ proxy.
- Không định nghĩa các pointcut DSL cho nhiều engine.
- Không triển khai transaction/caching/retry thực; chỉ mô phỏng một interceptor quan sát.

## 6. Ảnh hưởng đối với logging và tracing

Với tracing theo biên proxy, một lời gọi outer qua proxy tạo span/metric cho outer. Internal calls tới inner không nhất thiết tạo span riêng.

Ví dụ một workflow gửi 20 phần tử:

~~~text
proxy.processBatch()
  -> target.processBatch()
       -> this.processOne(item1)
       -> this.processOne(item2)
       ...
       -> this.processOne(item20)
~~~

Advice trên `processBatch` có thể ghi **một operation**, còn advice riêng của `processOne` qua proxy không được gọi cho 20 internal calls. Nếu mục tiêu là đo tổng thời gian của batch, đây có thể là **độ hạt phù hợp**. Nếu cần thời gian từng item, phải có hook tại mỗi item boundary được bảo đảm, ví dụ collaborator riêng hoặc instrumentation tường minh.

**Không nên tăng số span chỉ để “có log”.** Một hệ thống thu thập trace có thể phải giới hạn sampling/cardinality và tránh ghi nguyên arguments, kết quả hoặc dữ liệu nhạy cảm.

## 7. Ảnh hưởng đối với caching

Nếu proxy advice tại method inner tạo cache:

~~~text
Proxy.inner(key)
  -> nếu cache hit: trả cached value, không gọi target
  -> nếu miss: gọi target.inner(key), có thể lưu kết quả
~~~

thì self-call `this.inner(key)` trong target sẽ **bỏ qua lookup và store** của proxy ấy. Target có thể thực hiện I/O lại, tạo khác biệt hiệu năng và kết quả tùy consistency.

Trước khi chuyển method thành collaborator có proxy, xác định:

- Inner có phải thao tác đọc thuần hay có side effect?
- Cache key có chứa mọi yếu tố ảnh hưởng kết quả không?
- Có nhiều tenant/user, phiên bản, locale hoặc quyền dữ liệu?
- Internal call có **cần** áp dụng cache hay cố ý muốn đường uncached?
- Thay đổi kiến trúc có dẫn tới recursion do cache loader gọi lại proxy không?

Nếu caching quan trọng cho tính đúng đắn hoặc hiệu năng bắt buộc, nên làm biên cache tường minh/được bảo đảm, không chỉ giả định annotation có hiệu lực mọi đường.

## 8. Ảnh hưởng đối với retry

Advice retry quanh `inner` được gọi qua proxy có thể thử lại khi method ném một lỗi phù hợp. Nhưng khi outer tự gọi `this.inner()`, advice retry riêng đó có thể không chạy.

Một “cách sửa” thiếu suy nghĩ là đặt retry lên outer. Điều này có thể thay đổi đơn vị retry từ **một thao tác inner** thành **toàn bộ outer workflow**.

Ví dụ:
- Outer đã ghi một phần dữ liệu rồi inner thứ ba lỗi.
- Retry outer chạy lại hai bước đã thành công.
- Nếu tác vụ không idempotent, dữ liệu hoặc side effect có thể bị lặp.

Giải pháp phải xác định **attempt boundary, idempotency, timeout tổng, backoff và phân loại lỗi**. Self-invocation bypass là tín hiệu cần xem lại thiết kế retry, không phải lý do tự động chuyển annotation lên outer.

## 9. Ảnh hưởng đối với transaction

Đây là trường hợp cần diễn đạt cẩn thận. Nếu outer đã chạy trong một transaction context, self-call inner **có thể vẫn tham gia transaction đang tồn tại** dù advice mở boundary riêng cho inner không được thực hiện.

Không kết luận:

> “Self-invocation luôn khiến inner chạy ngoài transaction”.

Kết luận chính xác hơn:

> “Nếu chính sách transaction riêng của inner được lắp qua proxy invocation và inner bị gọi trực tiếp trên target, **interceptor dành riêng cho inner không được kích hoạt ở đường đó**.”

Điều này ảnh hưởng khi inner được kỳ vọng:
- Mở một unit-of-work mới.
- Có timeout/isolation hoặc propagation riêng.
- Commit/rollback ở một boundary khác outer.
- Ghi metric transaction riêng.
- Dọn resource theo scope inner.

Dù vậy, transaction thật còn phụ thuộc cơ chế lưu trữ, ngữ cảnh hiện hành và quy tắc propagation. Khi refactor, kiểm thử **commit/rollback semantics**, không chỉ đếm các advice được gọi.

## 10. Ảnh hưởng đối với lỗi và cleanup

Giả sử inner ném lỗi:

~~~text
target.outer()
    -> this.inner()
        -> throw X
    -> X thoát ra khỏi outer
~~~

Nếu outer đang chạy trong proxy invocation, after-throwing/around advice của outer có thể nhận X như **exception của lời gọi outer**. Điều đó không có nghĩa advice dành cho inner đã chạy.

Nếu outer tự bắt X và trả fallback:

~~~text
target.outer():
    try:
        return this.inner()
    catch X:
        return fallback
~~~

outer trả bình thường. Advice after-returning của outer có thể chạy; after-throwing của outer không tự thấy X đã bị bắt. Nếu cleanup/metrics phải ghi từng failure inner, nên đặt ở scope đáng tin, không phụ thuộc một proxy inner bị bypass.

Với các resource như lock, stream hoặc transaction handle, đừng dùng proxy advice làm **cơ chế giải phóng duy nhất** khi có đường gọi trực tiếp. Owner của resource cần lifecycle và `try/finally` đúng hợp đồng.

## 11. Bảo mật và các bất biến bắt buộc

Một interceptor kiểm tra quyền truy cập trước khi gọi method có thể có ích tại API boundary. Nhưng nếu target được gọi qua đường bypass, việc kiểm tra có thể không xảy ra.

Vì vậy không xem **proxy advice là bằng chứng duy nhất** rằng mọi đường truy cập dữ liệu đều đã được bảo vệ. Hãy:

1. Chọn **trusted boundary** nơi mọi request phải đi qua.
2. Bảo vệ invariant ở domain/service/data boundary cần thiết.
3. Xác định những object target có thể bị truy cập trực tiếp hay không.
4. Kiểm thử cả đường gọi ngoài và internal call.
5. Đặt chính sách logging/audit tránh lộ secrets từ arguments.
6. Nếu policy cần enforcement tuyệt đối, thiết kế sao cho không có đường bypass có khả năng làm sai trạng thái.

Giải quyết self-invocation bằng cách cố lấy proxy tham chiếu từ bên trong target có thể giúp chạy một advice cụ thể, nhưng **không thay thế threat model** và không chứng minh mọi entry point được bảo vệ.

## 12. Các lựa chọn cấu trúc để tránh bypass

### 12.1. Giữ policy ở outer nếu chỉ cần ranh giới tổng

Nếu mục tiêu chỉ là ghi một trace của toàn workflow, advice trên `outer` có thể đủ. Không cần ép helper inner thành một service độc lập chỉ vì mong có nhiều log hơn.

Ưu điểm: ít layer, ít dispatch, hợp đồng nghiệp vụ dễ đọc. Đánh đổi: không có metric/advice riêng của inner trong các self-calls.

### 12.2. Tách inner sang một collaborator được bọc

~~~text
Caller -> WorkflowService
             |
             +--> ProxiedItemWriter -> ItemWriterTarget.writeOne()
~~~

Bây giờ workflow gọi method qua một **dependency độc lập** đã được inject dưới dạng proxy. Ưu điểm:

- Trách nhiệm orchestration và thao tác được phân tách.
- Caller có một boundary rõ cho policy.
- Mock/spy và integration tests dễ xác nhận.
- Không buộc target biết đến proxy của chính mình.

Rủi ro: nếu nơi khác vẫn giữ `ItemWriterTarget` gốc, bypass vẫn xảy ra. Tách class phải đi cùng quy tắc construction/wiring.

### 12.3. Dùng coordinator bên ngoài

Như ví dụ Java: coordinator nhận `Operations proxy` và gọi `inner`, do đó advice inner được kích hoạt. Cách này phù hợp khi workflow là orchestration độc lập với thao tác.

Không cần áp dụng coordinator cho mọi helper private; chỉ tách khi biên logic và policy có ý nghĩa.

### 12.4. Dùng explicit decorator hoặc interceptor tại biên

Một decorator triển khai cùng interface và chuyển tiếp sang target. Nó giúp nhìn rõ rằng caller phải dùng wrapper. Tuy nhiên decorator **cũng có thể bị bypass** nếu caller giữ target gốc, và self-call trong target không tự quay lại decorator.

Cách tổ chức object graph và quyền sở hữu tham chiếu quan trọng hơn tên gọi “proxy” hay “decorator”.

### 12.5. Dùng weaving trực tiếp method execution khi thật sự cần

Nếu yêu cầu là “quan sát mọi execution của inner bất kể caller”, một cơ chế biến đổi source/bytecode/load time có khả năng dệt hook vào method body có thể phù hợp hơn.

Nhưng phải xác minh:
- Engine hỗ trợ method execution join point nào?
- Class thực sự nằm trong scope được weave?
- Classloader/artifact có chứa mã đã biến đổi?
- Điểm có bị loại bởi pointcut hoặc visibility/bytecode constraints?
- Có xung đột nhiều transformer hoặc recursion trong logger không?
- Có test xác nhận direct call và self-call đúng như mong muốn không?

Không khẳng định weaving tự động bắt mọi điểm chỉ vì nó mạnh hơn JDK interface proxy.

### 12.6. Proxy tham chiếu ngược trong chính target: giải pháp có giá

Target có thể được thiết kế để nhận một proxy reference của chính nó, rồi gọi `proxy.inner()` thay `this.inner()`. Nhưng cách này có thể dẫn đến:
- Vòng phụ thuộc khi khởi tạo.
- Proxy chưa sẵn sàng hoặc bị gắn sai target.
- Coupling nghiệp vụ với infrastructure AOP.
- Vấn đề nhiều instance/proxy scopes khác nhau.
- Reentrancy, recursion và lifecycle khó theo dõi.
- Test phải dựng đúng object graph phức tạp hơn.

Vì thế nên xem đây là **phương án cần cân nhắc kỹ**, không phải mẹo mặc định. Orchestrator/collaborator rõ ràng thường dễ giải thích và bảo trì hơn.

## 13. So sánh các kiểu proxy và weaving

| Cơ chế | Receiver/đường interception | Internal call có tự có advice riêng? |
| --- | --- | --- |
| **Delegating JDK interface proxy** | Proxy nhận interface invocation, handler gọi target riêng. | **Không** khi target gọi trực tiếp `this.inner()`. |
| **Explicit decorator** | Wrapper chuyển tiếp tới target. | **Không** nếu target tự gọi chính nó ngoài wrapper. |
| **Class proxy/subclass proxy** | Có thể override/dispatch theo implementation. | **Phụ thuộc engine, loại method và cách receiver được sử dụng**; phải test. |
| **Source/bytecode weaving execution hook** | Hook có thể được dệt tại method execution. | **Có thể**, nếu method đã được weave và pointcut khớp. |
| **Call-site weaving** | Hook tại vị trí gọi method đã được dệt. | **Có thể**, nếu call site nội bộ thuộc phạm vi được xử lý. |
| **Runtime instrumentation** | Phụ thuộc transformer và những điểm nó tạo ra. | Không thể kết luận chung; xem hook thực tế. |

Bảng không xếp hạng công cụ. Nếu nhu cầu chỉ là một boundary dịch vụ public, proxy có thể đủ và đơn giản. Nếu yêu cầu áp vào **mọi method execution**, cần một mô hình phù hợp hơn và chấp nhận chi phí cấu hình/QA.

### 13.1. Private, final, static và constructor là những trường hợp khác nhau

**Private:** JDK interface proxy không chặn trực tiếp private method của target vì method đó không thuộc public interface invocation qua proxy. Weaving bytecode có thể có khả năng khác.

**Final:** Proxy dựa trên subclass/override có thể bị giới hạn bởi final class/final method; delegating interface proxy có thể vẫn chặn lời gọi interface rồi ủy quyền vào một implementation final. Không đưa ra quy tắc “final luôn cấm AOP”.

**Static:** Static method không được dispatch qua instance của interface proxy như method instance. Muốn quan sát nó phải có hook khác.

**Constructor:** Target constructor thường chạy trước khi proxy tách biệt được tạo để bọc target. JDK interface proxy không tự chặn constructor execution của target.

**Default interface method:** Khi gọi qua proxy, handler có thể nhận invocation của default method; việc nó gọi tiếp default body thế nào và những lời gọi từ đó được dispatch ra sao cần test theo Java API/công cụ cụ thể, không áp nguyên sơ đồ target tách biệt của mọi trường hợp.

## 14. Self-invocation trong async và callback

Một method ngoài có thể lên lịch:

~~~java
Runnable task = this::innerTask;
executor.execute(task);
~~~

Đây là **mã minh họa không độc lập**: giả định `innerTask` và `executor` đã tồn tại. Method reference capture **target `this`**, do đó khi task được gọi ở worker, nó vẫn không tự chuyển thành proxy invocation.

Nếu callback ở nơi khác capture `proxy::innerTask`, đường gọi có thể qua proxy. Nhưng phải lưu ý:

- Tác vụ có thể chạy **sau khi outer đã trả về**.
- Around advice của outer đo tới method return chưa chắc bao toàn bộ task.
- `ThreadLocal` của caller không tự truyền qua worker.
- Callback có thể giữ request/target quá lâu nếu capture object lớn.
- Exception của task có thể được thể hiện trong `CompletionStage` thay vì ném đồng bộ.
- Cancellation/timeout của future không tự chứng minh remote operation đã dừng.

Đây là **hai chiều độc lập**: biên interception (proxy hay target) và biên thời gian (method return hay task completion). Cần giải quyết cả hai nếu muốn monitoring end-to-end.

## 15. Thiết kế kiểm thử chứng minh chính xác bypass

### 15.1. Ma trận đường gọi

| Ca | Đường gọi | Target.inner chạy? | Proxy advice inner chạy? |
| --- | --- | --- | --- |
| A | Caller → proxy.inner | Có nếu handler tiếp tục. | Có nếu pointcut khớp. |
| B | Caller → proxy.outer → target.this.inner | Có nếu outer gọi đến. | Không trong delegating proxy demo. |
| C | Caller → target.inner | Có. | Không. |
| D | Coordinator(proxy) → proxy.inner | Có. | Có nếu pointcut khớp. |
| E | proxy.outer nhưng outer không gọi inner | Không. | Không. |
| F | proxy.outer nhưng before advice ném lỗi | Có thể chưa chạy. | Không nếu inner không được gọi. |
| G | proxy.inner nhưng pointcut không khớp | Có thể chạy. | Không có advice cụ thể. |
| H | proxy.inner được cache advice trả hit | Target có thể không chạy. | Advice cache có thể đã chạy. |

Bảng nói về cơ chế proxy tách target như demo. Với engine khác phải viết ma trận lại dựa trên receiver/call path thật.

### 15.2. Bộ đếm trong target và handler

Đừng chỉ assert số dòng log. Trong test, dùng ba counters:

~~~text
outerAdviceInvocations
innerAdviceInvocations
innerTargetExecutions
~~~

Sau một lượt `proxy.outer()` gọi inner đúng một lần, kỳ vọng của demo:
- `outerAdviceInvocations = 1`
- `innerAdviceInvocations = 0`
- `innerTargetExecutions = 1`

Sau đó gọi `proxy.inner()` một lần và pointcut khớp, kỳ vọng:
- `innerAdviceInvocations = 1`
- `innerTargetExecutions = 2`

Counter target giúp phân biệt **“không chạy advice”** với **“không chạy body”**. Nếu retry, recursion hoặc vòng lặp tồn tại, cần điều chỉnh expected counts.

### 15.3. Ca lỗi và fallback

Cần test hai nhánh:
1. Inner ném lỗi thoát qua outer: advice throwing của **outer** có thể chạy, inner advice vẫn không.
2. Outer tự catch rồi trả fallback: advice returning của **outer** có thể chạy, inner advice vẫn không.

Nếu test chỉ có đường thành công, có thể không phát hiện sai lệch metric lỗi hoặc transaction.

### 15.4. Ca annotation

Khi pointcut chọn theo annotation, kiểm tra annotation ở **interface method** hay **implementation method**, retention là gì, matcher tra metadata ở đâu. Đây là kiểm thử riêng với đường gọi proxy/self-call.

### 15.5. Ca weaving khác proxy

Nếu production dùng class-based proxy hoặc bytecode weaving, phải tạo integration test với **đúng engine, loader, artifact và configuration**; không dùng kết quả demo JDK proxy để khẳng định sản phẩm khác giống nó.

### 15.6. Ca concurrency

Nếu advice giữ `lastMethod`, `lastArgs` hoặc `startTime` ở một field dùng chung, nhiều caller có thể ghi đè và tạo lỗi quan sát độc lập với self-invocation. Dùng scope cục bộ theo invocation, không giữ toàn bộ args/target lâu hơn nhu cầu và dọn context đúng trên error path.

## 16. Quy trình chẩn đoán khi production thiếu advice

1. **Xác nhận symptom:** thiếu log/metric nào? Target action có xảy ra không?
2. **Xác nhận object:** caller đang giữ proxy hay target? Đừng chỉ nhìn tên interface.
3. **Vẽ receiver:** body đang chạy với `this` nào? Có `this.inner`, callback `this::inner` hoặc target reference trực tiếp không?
4. **Xác nhận pointcut:** method thực tế có khớp matcher không, kể cả annotation/overload/override?
5. **Phân biệt class scope:** proxy được tạo cho object đó chưa; trong weaving, class đã được biến đổi chưa?
6. **Kiểm tra caller path:** direct target, self-call, collaborator được inject và async callback có đi qua wrapper không?
7. **Kiểm tra exception:** inner ném ra ngoài hay outer đã catch thành fallback? Advice nào đáng lẽ nhìn thấy lỗi?
8. **Chọn thay đổi nhỏ nhất:** chỉnh wiring, tách collaborator, thay policy boundary hoặc chọn weaving thực sự cần thiết.
9. **Xác nhận backward compatibility:** retry count, transaction context, log volume, dữ liệu riêng tư và thời gian xử lý có thay đổi ngoài ý định không?
10. **Ghi nhận evidence:** positive/negative tests, bộ đếm target/advice và sơ đồ call path trước/sau.

### 16.1. Không có advice không đồng nghĩa chỉ có một nguyên nhân

Thiếu advice còn có thể do:
- Method chưa được gọi.
- Pointcut không khớp.
- Handler bị disable.
- Caller nhận nhầm target.
- Interface/implementation metadata khác chỗ.
- Method không nằm trong interface của JDK proxy.
- Class/loader ngoài scope của weaving.
- Logging sink bị lỗi hoặc sampling loại sự kiện.
- Exception từ advice chặn target trước khi chạy.

Cần phân biệt bằng bằng chứng, không chỉ thay mọi cấu hình rồi hy vọng log xuất hiện.

## 17. Ngộ nhận thường gặp

**“Có `@Observed` nghĩa là mọi execution đều chạy advice.”** Không: metadata không tự tạo interception.

**“Public method chắc chắn được proxy khi gọi từ chính class.”** Không với delegating proxy tách target.

**“Mở rộng pointcut thành mọi method sẽ xử lý self-call bypass.”** Không nếu event không đi qua handler.

**“Không có `ADVICE inner` tức `inner` chưa chạy.”** Sai; body có thể đã chạy trên target.

**“Nếu outer có advice thì inner có advice do cùng class.”** Không; mỗi invocation phải qua biên tương ứng.

**“Inner không có interceptor giao dịch tức luôn không có transaction.”** Không; nó có thể chạy trong transaction đã được outer mở.

**“Class proxy và JDK dynamic proxy luôn giống hệt ở self-invocation.”** Không; dispatch và object model có thể khác.

**“Weaving luôn chặn được mọi method.”** Không; còn join-point model, pointcut, scope, classloader và binary.

**“Chỉ cần gắn proxy vào field của chính target.”** Có thể tạo circular dependency và coupling lớn; chưa chắc là thiết kế tốt.

**“Decorator không bị bypass vì được viết tường minh.”** Target vẫn có thể tự gọi nội bộ; caller có thể giữ target gốc.

**“Đảo order của advice sẽ sửa việc handler không được gọi.”** Không; order chỉ có hiệu lực với advice trong chain đã nhận invocation.

**“Đặt retry ở outer thay inner là tương đương.”** Không; phạm vi retry thay đổi, có nguy cơ lặp side effect.

**“Proxy advice đảm bảo tất cả security checks.”** Chỉ với những entry paths được bảo đảm; domain invariant cần trusted enforcement.

## 18. Câu hỏi vận dụng

**Câu 1.** Caller gọi `proxy.outer()`, target.outer gọi `this.inner()`; pointcut khớp cả hai. Handler nhận những method nào trong đường đó?

**Đáp án:** Handler của delegating proxy nhận invocation outer; không nhận inner riêng.

**Câu 2.** Vì sao in `TARGET inner` mà không in `ADVICE before inner` không mâu thuẫn?

**Đáp án:** Target body chạy; handler proxy không nhận internal call.

**Câu 3.** Chuyển `inner` thành public và thêm annotation có sửa được bypass không?

**Đáp án:** Không tự sửa receiver/call path. Cần bảo đảm lời gọi tới proxy hoặc dùng cơ chế hook khác.

**Câu 4.** Outer bắt `IOException` của inner và trả `"fallback"`. After-throwing của outer có chạy cho IOException đó không?

**Đáp án:** Không nếu outer kết thúc bình thường bằng fallback; exception không thoát khỏi outer invocation.

**Câu 5.** Inner cần chính sách retry độc lập. Vì sao chuyển retry lên outer có thể nguy hiểm?

**Đáp án:** Retry cả outer có thể lặp những side effect đã thành công trước inner. Cần xác định attempt/idempotency boundary.

**Câu 6.** Một `Coordinator` ngoài target nhận `Operations proxy` và gọi `inner()`. Handler có nhận không?

**Đáp án:** Có nếu caller dùng đúng proxy và method thuộc đường interceptor; sau đó pointcut quyết định advice cụ thể.

**Câu 7.** Nếu target đã nằm trong transaction context của outer, self-call inner bypass một interceptor riêng, kết luận nào đúng?

**Đáp án:** Không thể khẳng định inner nằm ngoài transaction; chỉ có thể nói interceptor riêng của inner không chạy qua đường đó.

**Câu 8.** Vì sao một engine weave trực tiếp execution inner có thể vẫn quan sát self-call?

**Đáp án:** Vì execution của inner vẫn xảy ra, và hook có thể được gắn tại entry/body inner thay vì yêu cầu caller qua proxy.

**Câu 9.** Target đăng ký callback bằng `this::inner`, gọi sau khi outer return. Có hai ranh giới nào cần xét?

**Đáp án:** Receiver/call path có bypass proxy không; và task completion xảy ra sau method return hay không.

**Câu 10.** Muốn khẳng định lỗi self-invocation bypass trong test, cần chứng minh hai điều gì?

**Đáp án:** Target.inner đã thực sự chạy và handler/advice inner không nhận invocation tương ứng, đồng thời đã loại trừ lỗi pointcut/logging.

## Kết luận

**Lời gọi nội bộ không đi qua proxy** là vấn đề về **receiver và đường interception**, không phải đơn thuần là điều kiện pointcut. Trong mô hình delegating proxy, caller gọi `proxy.outer()` thì advice outer có thể chạy; nhưng target.outer gọi `this.inner()` trên target nên inner thực thi mà không tạo invocation qua proxy cho inner.

Hướng xử lý phụ thuộc yêu cầu: đôi khi advice tổng ở outer đã đủ; đôi khi nên đưa bước inner thành collaborator có proxy hoặc một coordinator bên ngoài gọi qua proxy; đôi khi cần weaving trực tiếp method execution. Với transaction, retry, caching và bảo mật, cần phân biệt chính sách ở **outer boundary** với chính sách riêng của **inner boundary** để tránh thay đổi semantics ngoài ý định. Hãy chứng minh bằng **call path, counters và negative tests**, không chỉ dựa vào annotation hoặc sự vắng mặt của log.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-005">Ảnh hưởng của phương thức riêng, phương thức tĩnh và thành phần không thể ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của phương thức riêng, phương thức tĩnh và thành phần không thể ghi đè trong AOP qua proxy

> **Mục tiêu học tập:** Hiểu vì sao khả năng can thiệp bằng proxy trong Aspect-Oriented Programming (AOP) phụ thuộc cơ chế dispatch và ranh giới lời gọi; phân biệt **proxy dựa trên giao diện**, **proxy dựa trên lớp con** và **weaving trực tiếp mã**; xác định ảnh hưởng của phương thức `private`, `static`, `final`, lớp `final`, constructor, lời gọi nội bộ và những thành phần không thể override. Từ đó thiết kế pointcut, advice và phép kiểm thử phù hợp, tránh các khẳng định sai kiểu “AOP luôn chặn được” hoặc “AOP không bao giờ chặn được”.

## 1. Tình huống mở đầu: cùng một phương thức nhưng cách gọi khác nhau

Giả sử có một đối tượng thực thi thao tác ghi dữ liệu. Ta muốn thêm advice đo thời gian bằng **proxy**.

~~~text
Client ──> Proxy ──> Target.save(...)
            |
            +── before / after / around
~~~

Nếu client gọi `proxy.save(...)`, lời gọi đi qua ranh giới can thiệp. Nhưng nếu ở một nơi khác client giữ `target` và gọi thẳng `target.save(...)`, cùng phương thức nghiệp vụ có thể chạy **không qua advice của proxy**.

Tình huống phức tạp hơn khi phương thức đích là `final`, có một helper `private`, hoặc gọi một phương thức `static`. Ba đặc điểm này liên quan đến **quy tắc ghi đè/dispatch của Java**, nhưng ảnh hưởng của chúng lên AOP **khác nhau theo loại proxy**.

Ta sẽ phân biệt:

- **Interface proxy:** trung gian triển khai một giao diện; handler nhận lời gọi interface từ client.
- **Subclass proxy:** trung gian là lớp con, can thiệp chủ yếu bằng cách override các phương thức đủ điều kiện.
- **Bytecode weaving:** thay đổi các vị trí mã đã được mô hình join point cho phép; không nhất thiết phụ thuộc vào khả năng override của phương thức.

Điểm quan trọng: một phương thức `final` **không thể bị lớp con override**, nhưng **có thể được gọi qua interface proxy**. Một lời gọi `private` **không phải phương thức có thể override**, nhưng một bộ dệt method execution phù hợp **có thể có khả năng** quan sát nó. Vì thế phải nói rõ **cơ chế**, **điểm can thiệp** và **đường thực thi**.

## 2. Ba câu hỏi trước khi xét modifier

Trong AOP, **join point** là vị trí có thể can thiệp, **pointcut** chọn vị trí và **advice** là hành vi bổ sung. Một **proxy/interceptor** chặn lời gọi tại ranh giới đối tượng; một **weaver** có thể chèn hành vi tại các điểm của mã nếu loại join point được hỗ trợ. Khả năng đọc metadata bằng Reflection không đồng nghĩa khả năng chặn thực thi.

Trước khi nói method `private`, `static` hoặc `final` “không được AOP hỗ trợ”, cần kiểm tra:

1. **Chọn điểm gì?** Lời gọi interface, method execution, constructor hay field access?
2. **Can thiệp bằng cách nào?** Interface delegation, subclass override hay bytecode weaving?
3. **Đường thực thi có tới đó không?** Caller dùng proxy hay target thô, receiver nào thực thi, class có thuộc phạm vi instrumentation không?

Câu trả lời theo ba tầng sẽ phân biệt được **không thể override**, **không được proxy expose** và **không được weaving chọn** — ba nguyên nhân khác nhau.

## 3. Interface proxy hoạt động theo hợp đồng giao diện

Một interface proxy được tạo để triển khai một hoặc nhiều **giao diện** và cung cấp một đối tượng trung gian. Đối với Java Dynamic Proxy, các lời gọi instance method của những giao diện được proxy triển khai có thể đi qua `InvocationHandler`.

Mô hình:

~~~text
interface Worker {
    void execute();
}

class WorkerImpl implements Worker {
    public void execute() { ... }
}

caller ───> (Worker) proxy ──> InvocationHandler ──> WorkerImpl.execute()
~~~

*Đây là cấu trúc mô tả, không phải một tệp Java hoàn chỉnh.*

Điều kiện quan trọng:

- Caller phải **gọi qua tham chiếu proxy** được cung cấp.
- Phương thức cần chặn nằm trong **hợp đồng instance method của giao diện** mà proxy triển khai, hoặc thuộc những method được cơ chế proxy xử lý đặc biệt.
- Handler có thể tiếp tục tới target, short-circuit hoặc xử lý lỗi theo hợp đồng.
- Việc target dùng một lớp `final` **không ngăn proxy giao diện đứng phía trước nó**, vì proxy không cần kế thừa lớp target.

### 3.1. Proxy giao diện không đồng nghĩa proxy của mọi method trên target

Target có thể có method không khai báo trong giao diện:

~~~java
final class WorkerImpl implements Worker {
    public void execute() { /* ... */ }
    public void maintenanceOnly() { /* ... */ }
}
~~~

Đây là mã minh họa. Caller với biến kiểu `Worker` không tự có quyền gọi `maintenanceOnly()`; một `java.lang.reflect.Proxy` triển khai `Worker` cũng **không tự mang method đó**.

Một lớp đích có thể chứa rất nhiều method, nhưng proxy giao diện chỉ đứng trên **bề mặt được giao diện và cơ chế proxy cung cấp**.

## 4. Subclass proxy dựa trên khả năng ghi đè

Một dạng proxy khác tạo hoặc sử dụng **lớp con** của lớp đích. Lớp con override một phương thức có thể ghi đè để thêm hành vi rồi chuyển tiếp về phần cài đặt của lớp cha.

~~~text
TargetClass
   ^
   |
TargetProxySubclass
   |
   +── override execute() {
           before();
           result = super.execute();
           after();
           return result;
       }
~~~

Điều này dựa trên **quy tắc override của Java**. Vì thế:

- **`private` method** không thể được override bởi lớp con.
- **`static` method** không được override đa hình; khai báo trùng tên có thể là *method hiding*, không phải interception theo virtual dispatch.
- **`final` method** không cho phép override.
- **`final` class** không cho phép kế thừa, nên không tạo subclass của chính lớp ấy bằng cách thông thường.
- Constructor không phải là method được kế thừa/override.

**Cần phân biệt** một phương thức của lớp cha có modifier `final` với một phương thức của interface được triển khai bằng method `final` ở target. Interface proxy chặn ở lớp trung gian **trước khi gọi target**; subclass proxy phải override chính phương thức target nếu muốn chặn trực tiếp đường dispatch tương ứng.

### 4.1. Vì sao không thể suy từ “final” sang “không thể AOP”?

`final` hạn chế **override**; nó không tự cấm một đoạn code khác gọi một phương thức, không cấm tạo một đối tượng proxy **triển khai giao diện**, và không tự loại mọi khả năng bytecode weaving.

Một công cụ dệt bytecode vào thân phương thức hoặc call site phù hợp có thể hoạt động theo cơ chế **không phải subclass override**, nếu join point model và giới hạn runtime cho phép.

Nhận định đúng phải có phạm vi:

> “Subclass proxy không thể override một instance method `final`; interface proxy vẫn có thể chặn lời gọi interface tới target có method implementation `final`.”

## 5. Phương thức `private`: không được override và không là API của proxy giao diện

Trong Java, thành viên `private` thuộc phạm vi truy cập riêng của lớp khai báo. Lớp con không override `private` method của lớp cha. Nếu một lớp con tự khai báo method cùng tên/chữ ký, đó là một thành viên độc lập theo ngôn ngữ, không phải override method private của lớp cha.

Ví dụ:

~~~java
class Base {
    private void check() {
        System.out.println("Base private");
    }

    public void run() {
        check();
    }
}

class Child extends Base {
    public void check() {
        System.out.println("Child public");
    }
}
~~~

*Đoạn mã minh họa quy tắc Java, có thể đặt vào tệp đầy đủ khi chạy.*

Lời gọi `check()` trong thân `Base.run()` vẫn tham chiếu phần private của `Base`, không tự dispatch đến `Child.check()`. Vì vậy không thể “override check” để chặn phần helper này bằng một subclass proxy.

### 5.1. Điều gì vẫn có thể quan sát?

Nếu caller gọi `proxy.run()`, advice ở ranh giới `run()` có thể chạy **trước và sau toàn bộ hoạt động**, bao gồm phần private helper đã được thực thi bên trong. Nhưng điều đó khác với một **join point độc lập** tại `check()`.

Để có advice riêng quanh `check()`, cần một mô hình và cơ chế có khả năng quan sát vị trí **method execution/call** thích hợp, không chỉ một proxy giao diện.

### 5.2. Private method và reflection

Java Reflection có thể tìm metadata của `private` method qua `getDeclaredMethod`. Tuy nhiên:

- Tìm thấy `Method` **không đồng nghĩa** proxy có một đường can thiệp tới mọi lời gọi.
- Việc thực sự invoke một private member còn chịu kiểm soát access và module.
- Tự dùng reflection để gọi helper là **một lời gọi mới**, không phải cách thay thế `proceed` ở join point hiện tại.

Không được coi reflection là giải pháp tự động phá mọi giới hạn proxy.

## 6. Phương thức `static`: method hiding khác với override

Một `static` method gắn với kiểu khai báo và được gọi theo cơ chế không giống virtual dispatch của instance method. Method `static` không được **override đa hình**.

Ví dụ minh họa:

~~~java
class Parent {
    static String label() {
        return "Parent";
    }
}

class Child extends Parent {
    static String label() {
        return "Child";
    }
}
~~~

Đây là *method hiding*. Những lời gọi sử dụng tên class tương ứng có thể chọn method khác nhau theo quy tắc giải quyết của Java; không có lời gọi override đa hình đến subclass handler chỉ vì object runtime thuộc `Child`.

### 6.1. Static method không đi qua Java Dynamic Proxy giao diện

Một static method khai báo trên interface **không phải instance method được implement/override** bởi proxy. Ví dụ `Tool.version()` được gọi trên tên interface, không qua tham chiếu `proxy`.

Một static method trên class target cũng không đi qua handler chỉ vì class ấy có một proxy cho các instance method khác.

### 6.2. Nhưng weaving bytecode có thể khác

Nếu AOP có join point model hỗ trợ **method call hoặc method execution cho static method** và weaver xử lý mã tương ứng, có thể có cơ chế quan sát. Sự bất khả của **subclass override** không phải sự bất khả của **mọi cách weaving**.

Cần nêu rành mạch: “không thể can thiệp static theo *cơ chế override subclass*” khác “mô hình AOP này không hỗ trợ static method join point”.

## 7. `final` method: giới hạn riêng của kế thừa

Một `final` instance method không cho lớp con override. Vì thế, một **subclass proxy dựa trên override** không thể chèn advice bằng cách tạo bản override cho chính method đó.

~~~java
class BaseService {
    public final String calculate() {
        return "done";
    }
}
~~~

Nếu một subclass khai báo lại `calculate()` với cùng chữ ký nhằm override, Java từ chối khi biên dịch. Một bộ sinh subclass hợp lệ cũng phải tôn trọng quy tắc này.

Tuy nhiên:

- Một interface proxy chặn **lời gọi đi vào qua giao diện** trước khi gọi `BaseService.calculate()`, nếu method là hiện thực của hợp đồng interface.
- Một weaver mã có thể có loại join point khác không dựa trên override.
- Một method `final` vẫn có thể **được gọi bình thường**; hạn chế nằm ở cách đặt interception.

**Không được đồng nhất `final method` với `method không bao giờ được quan sát`.**

### 7.1. `final` không nói gì về việc policy đã chạy

Ngay cả với method không-final, proxy chỉ chạy khi caller đi đúng đường. Tính override được là **điều kiện khả năng** của subclass proxy, không phải bằng chứng rằng mọi invocation đều qua proxy.

## 8. `final` class và những thành phần không thể tạo subclass

Khai báo `final class` chặn kế thừa class đó. Một dạng proxy tạo subclass của chính target không thể dùng cách ấy để bao một class `final` bằng subclass.

Những cấu trúc Java khác cũng cần chú ý:

- **Record class** là final theo ngữ nghĩa của Java; không thể tạo lớp con của record như một target subclass tùy ý.
- **Enum class** có mô hình kế thừa đặc biệt và không phải lớp target thông thường để subclass proxy.
- **Sealed class** hạn chế những lớp con được phép; một lớp con sinh động không nằm trong danh sách được phép có thể không được chấp nhận.
- **Sealed interface** cũng không phải một giao diện tự do cho mọi lớp proxy sinh động triển khai; Java Dynamic Proxy tiêu chuẩn không chấp nhận tạo proxy trực tiếp cho sealed interface.
- **Constructor** không được kế thừa và override.
- **Field** không là method có thể override; field hiding không phải method dispatch.

### 8.1. Lớp final vẫn có thể đứng sau interface proxy

Nếu `final class PaymentImpl implements Payment`, một proxy triển khai interface `Payment` vẫn có thể chuyển lời gọi đến `PaymentImpl`.

Quan hệ:

~~~text
Payment interface
      ^
      | implemented by
   Proxy             final PaymentImpl
      |                      ^
      +---- delegates ------+
~~~

Proxy **không kế thừa `PaymentImpl`**. Vì thế cấm kế thừa target không tác động trực tiếp lên khả năng tạo đối tượng proxy giao diện.

Điều kiện là API cần chặn thực sự có thể đi qua **giao diện không bị hạn chế tương ứng** và caller sử dụng proxy thay cho target.

## 9. Bảng phân biệt theo cơ chế

| Thành phần / tình huống | Interface proxy | Subclass proxy dựa trên override | Bytecode weaving với mô hình phù hợp |
|---|---|---|---|
| Public instance method khai báo trong interface | Có thể chặn khi caller đi qua proxy | Có thể nếu override hợp lệ | Có thể nếu điểm/mã thuộc phạm vi dệt |
| Target method `final` triển khai interface | **Có thể chặn ở proxy giao diện** | Không thể override method `final` | Có thể nếu model/weaver hỗ trợ |
| Helper `private` bên trong target | Không có interception riêng qua interface proxy | Không thể override | Có thể nếu pointcut/weaving hỗ trợ private method join point |
| `static` method | Không đi qua instance interface proxy | Không override; có thể method hiding | Có thể nếu model/weaver hỗ trợ static join point |
| Target `final class` có interface phù hợp | **Có thể** tạo proxy giao diện | Không thể subclass target | Có thể tùy phạm vi và hạn chế mã |
| Target class không có interface phù hợp | Java Dynamic Proxy giao diện không áp dụng trực tiếp | Có thể nếu target cho kế thừa/override | Có thể theo loại điểm được hỗ trợ |
| Constructor call/execution | Không tự chặn bằng method proxy | Constructor không override | Có thể ở một số join point models |
| Field get/set trực tiếp | Không tự chặn | Field không override | Có thể nếu có field access join points |
| Lời gọi nội bộ `this.other()` | Không tự quay qua proxy | Tùy virtual dispatch của method đủ điều kiện override | Có thể nếu execution/call site đã dệt |
| Gọi trực tiếp target từ bên ngoài | Không đi qua handler | Có thể bypass nếu dùng object target gốc | Tùy vị trí mã thực sự đã dệt |

**Cột weaving là khả năng có điều kiện, không phải cam kết mọi hệ AOP đều hỗ trợ tất cả hàng.** Một công cụ có thể chỉ hỗ trợ một phần điểm, dù dùng bytecode instrumentation.

## 10. Chương trình Java 17 số 1: interface proxy chặn được target `final`

Ví dụ này cố ý dùng một **lớp target final** chứa **method implementation final**, bên cạnh helper `private` và `static`. Chương trình chứng minh interface proxy **vẫn chặn được lời gọi giao diện đến method final**, trong khi helper private/static không tự trở thành lời gọi qua handler.

**Đây là Java Dynamic Proxy chuẩn, minh họa interception qua giao diện; không phải một engine weaving AOP tổng quát.**

Tạo tệp `InterfaceProxyLimitsDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class InterfaceProxyLimitsDemo {
    interface Work {
        String execute(String id);
        String state();
    }

    static final class FinalWorker implements Work {
        private int count = 0;

        @Override
        public final String execute(String id) {
            System.out.println("[target] execute " + id);
            privateStep(id);
            count++;
            return "done-" + id;
        }

        @Override
        public String state() {
            return "count=" + count;
        }

        private void privateStep(String id) {
            System.out.println("[target] private step " + id);
        }

        static String staticStep() {
            System.out.println("[target] static step");
            return "STATIC";
        }
    }

    public static void main(String[] args) {
        FinalWorker target = new FinalWorker();

        Work proxy = (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[] {Work.class},
                (proxyObject, method, arguments) -> {
                    System.out.println("[proxy] " + method.getName());
                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                }
        );

        System.out.println("-- via interface proxy --");
        System.out.println("[caller] " + proxy.execute("A"));
        System.out.println("[caller] " + proxy.state());

        System.out.println("-- direct target --");
        System.out.println("[caller] " + target.execute("B"));

        System.out.println("-- static method --");
        System.out.println("[caller] " + FinalWorker.staticStep());
    }
}
~~~

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac InterfaceProxyLimitsDemo.java
java InterfaceProxyLimitsDemo
~~~

**Stdout:**

~~~text
-- via interface proxy --
[proxy] execute
[target] execute A
[target] private step A
[caller] done-A
[proxy] state
[caller] count=1
-- direct target --
[target] execute B
[target] private step B
[caller] done-B
-- static method --
[target] static step
[caller] STATIC
~~~

### 10.1. Điều gì đã được chứng minh?

- `FinalWorker` là **final class** nên không thể được subclass.
- `execute` là **final instance method** nên cũng không thể override.
- Nhưng `Work` là một interface bình thường; `proxy.execute("A")` vẫn đi qua `InvocationHandler` trước khi handler gọi method trên target.
- `privateStep` chạy bên trong target nhưng **không có một dòng `[proxy] privateStep`**; nó không là method của giao diện.
- `FinalWorker.staticStep()` được gọi bằng class, **không đi qua đối tượng proxy**.
- Lời gọi `target.execute("B")` từ caller đi thẳng target, không in `[proxy] execute`.

Như vậy `final` có ảnh hưởng rất khác nhau đối với **subclass proxy** và **interface proxy**. Nếu một lời gọi bị bỏ sót, phải kiểm tra **tham chiếu mà caller dùng**, không chỉ modifier của method.

### 10.2. Giới hạn của ví dụ

Handler dùng `Method.invoke` để chuyển tiếp. Nếu method đích ném lỗi, reflection có thể bọc nó trong `InvocationTargetException`; đoạn handler giải bọc để giữ nguyên nhân gốc. Các chính sách khác như đổi đối số, retry hoặc đo thời gian không được triển khai ở đây.

Java Dynamic Proxy tiêu chuẩn chỉ cung cấp một cách chặn cụ thể. Nó không sửa thân `FinalWorker.execute`, không chặn constructor hay biến một private method thành public join point.

## 11. Chương trình Java 17 số 2: lớp con không thể override `private`/`static`/`final`

Ví dụ dưới đây mô phỏng ý tưởng **subclass proxy** bằng một lớp con viết thủ công. Nó override **chỉ** method `open` được phép override.

Lưu file `SubclassOverrideLimitsDemo.java`:

~~~java
public class SubclassOverrideLimitsDemo {
    static class Base {
        public String open(String name) {
            System.out.println("[base] open " + name);
            privateStep(name);
            return "open-" + name;
        }

        public final String fixed(String name) {
            System.out.println("[base] final " + name);
            return "fixed-" + name;
        }

        private void privateStep(String name) {
            System.out.println("[base] private " + name);
        }

        public static String utility() {
            System.out.println("[base] static");
            return "static-result";
        }
    }

    static final class InterceptingSubclass extends Base {
        @Override
        public String open(String name) {
            System.out.println("[subclass advice] before open");
            String result = super.open(name);
            System.out.println("[subclass advice] after open");
            return result;
        }
    }

    public static void main(String[] args) {
        Base proxyLike = new InterceptingSubclass();

        System.out.println("[caller] " + proxyLike.open("X"));
        System.out.println("[caller] " + proxyLike.fixed("Y"));
        System.out.println("[caller] " + Base.utility());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SubclassOverrideLimitsDemo.java
java SubclassOverrideLimitsDemo
~~~

**Stdout:**

~~~text
[subclass advice] before open
[base] open X
[base] private X
[subclass advice] after open
[caller] open-X
[base] final Y
[caller] fixed-Y
[base] static
[caller] static-result
~~~

### 11.1. Vì sao chỉ `open` có dòng advice?

`InterceptingSubclass` override được `Base.open` và đặt các lệnh “before/after” quanh `super.open`. Đây là điểm có thể dùng cơ chế **virtual override**.

`Base.fixed` là `final`; lớp con kế thừa nhưng **không thể override** để thêm hành vi vào chính method ấy. Vì vậy lời gọi `proxyLike.fixed("Y")` chạy vào implementation đã thừa kế, không có dòng `[subclass advice]`.

`privateStep` của `Base` không bị override; khi `Base.open` gọi helper đó, nó chạy body của `Base`. `Base.utility` là `static`, không có virtual dispatch vào `InterceptingSubclass`.

### 11.2. Đừng nhầm lớp con viết tay với weaver tự động

Chương trình này chỉ **minh họa quy tắc override** bằng Java thuần, không định nghĩa pointcut, không tự tạo proxy và không có weaving engine. Trong một hệ AOP thực, cơ chế tạo subclass có thể phát sinh mã bao quanh hoặc dùng các chiến lược khác tùy khả năng runtime.

Nếu muốn chặn `fixed`, cần một **ranh giới khác**: ví dụ interface proxy đứng ngoài lời gọi nếu target triển khai giao diện phù hợp, hoặc một mô hình weaving có hỗ trợ join point thích hợp. Không thể khắc phục giới hạn Java override bằng cách đổi tên method và vẫn gọi đó là override.

## 12. Những trường hợp Java cố ý không cho biên dịch

Các ví dụ sau chỉ nhằm hiểu quy tắc ngôn ngữ; **không ghép chúng vào hai tệp Java chạy được ở trên**.

### 12.1. Kế thừa một final class

~~~java
final class LockedType {}

class BadSubclass extends LockedType {}
~~~

Compiler từ chối vì `LockedType` không được kế thừa.

### 12.2. Override một final method

~~~java
class LockedMethod {
    public final void execute() {}
}

class BadOverride extends LockedMethod {
    @Override
    public void execute() {}
}
~~~

Compiler từ chối phương thức override `final`.

### 12.3. Dùng `@Override` cho static method

~~~java
class StaticBase {
    public static void run() {}
}

class BadStatic extends StaticBase {
    @Override
    public static void run() {}
}
~~~

Phương thức `static` không được override; `@Override` ở đây không hợp lệ. Nếu bỏ annotation và đáp ứng các điều kiện ngôn ngữ, khai báo trùng tên có thể là *method hiding* chứ không là đa hình instance.

### 12.4. Cố override private method

~~~java
class PrivateBase {
    private void act() {}
}

class MisleadingChild extends PrivateBase {
    @Override
    public void act() {}
}
~~~

Trong ví dụ, private method không được kế thừa như method có thể override, nên `@Override` không hợp lệ. Nếu bỏ annotation, method `act` trong lớp con không tự trở thành interceptor cho body private của lớp cha.

**Các lỗi trên là lỗi compile Java**, không phải bằng chứng có một pointcut AOP sai.

## 13. Self-invocation: cùng đối tượng gọi method của nó

Xét:

~~~java
interface Billing {
    void checkout();
    void charge();
}

class BillingImpl implements Billing {
    public void checkout() {
        charge();
    }

    public void charge() {
        // business logic
    }
}
~~~

Đây là mảnh Java minh họa.

Với **interface proxy** đứng trước `BillingImpl`, caller gọi `proxy.checkout()`. Handler có thể can thiệp `checkout` rồi chuyển tới `target.checkout()`. Bên trong `target.checkout()`, lời gọi `charge()` đi trực tiếp trên target, không tự đi qua proxy.

Kết quả có thể là advice cho `checkout` chạy, còn advice riêng dự kiến cho `charge` **không chạy ở self-invocation này**.

### 13.1. Subclass proxy có thể khác ở lời gọi virtual

Với subclass proxy, nếu method đang chạy thực sự nằm trên một **instance của lớp proxy** và gọi một virtual method có thể override, virtual dispatch có thể quay tới override của lớp proxy. Vì thế không thể khẳng định “self-invocation luôn bỏ qua mọi proxy”.

Nhưng nếu lời gọi là `private`, `static`, `final`, một lời gọi `super.method()` cụ thể, hoặc một đường gọi vào target khác nằm ngoài instance proxy, kết quả khác.

**Phân tích theo bytecode/dispatch và đối tượng runtime**, không theo tên gọi self-invocation đơn thuần.

### 13.2. Khả năng chặn ở method execution weaving

Nếu một công cụ weaving sửa chính phần thân của `charge` để có hook ở method execution, lời gọi bên trong lớp đích có thể được quan sát **nếu method body được dệt và thực sự chạy**. Không có điều kiện bắt buộc lời gọi phải quay qua proxy giao diện.

Điều này thể hiện sự khác nhau giữa can thiệp **ở ranh giới đối tượng** và can thiệp **ở vị trí mã thực thi**.

## 14. Method call và method execution: vị trí dệt quyết định độ bao phủ

Trong một số mô hình AOP có hai loại điểm:

- **Method call join point:** hoạt động lời gọi tại **vị trí caller**.
- **Method execution join point:** hoạt động khi **thân method đích** được thực thi.

Một proxy giao diện thường hoạt động như **interception ở ranh giới lời gọi qua proxy**, không thể tự suy rằng nó cung cấp đúng toàn bộ semantics của hai loại join point trên.

Ví dụ:

~~~text
Caller A  ─┐
           ├──> Worker.run() body
Caller B  ─┘
~~~

Nếu chỉ một caller đi qua proxy, handler có thể không thấy lời gọi kia. Nếu method body được weaving với method-execution advice phù hợp, cả hai đường có thể được quan sát khi thực sự đi vào thân đã dệt.

Ngược lại, một call-site weaving trong **Caller A** không tự chặn call site ở **Caller B** nếu Caller B chưa được dệt.

Điểm can thiệp “method `run`” cần được mô tả thêm bằng **vị trí**: ở caller, ở proxy hoặc ở thân method.

## 15. Constructor không phải phương thức để override

Constructor của Java không được kế thừa như method và không được override. Khi `new Target()` chạy, một instance của target được khởi tạo. Một proxy được dựng **sau đó** để đứng trước target không thể quay ngược thời gian mà chặn mọi câu lệnh bên trong constructor đã hoàn tất.

Với subclass proxy, việc tạo đối tượng lớp con cũng liên quan quy tắc gọi constructor của lớp cha; một phương thức override được gọi quá sớm từ constructor có thể quan sát trạng thái lớp con **chưa khởi tạo đầy đủ**.

Những lưu ý:

- Interface proxy không tự cung cấp constructor join point.
- Subclass proxy không thể “override constructor”.
- Một số mô hình weaving có thể có constructor call/execution hoặc initialization join points, nhưng các điểm đó có semantics riêng.
- Advice trong giai đoạn khởi tạo phải thận trọng với `this` escaping, virtual dispatch và bất biến đối tượng.

Không đặt kỳ vọng “mọi new đều được AOP quan sát” nếu cơ chế thực tế chỉ có method interceptor.

## 16. Field và accessor: truy cập trực tiếp không phải gọi phương thức

Ví dụ:

~~~java
class Account {
    private int balance;

    public int getBalance() {
        return balance;
    }
}
~~~

Đây là đoạn khai báo minh họa.

Một interface/subclass proxy có thể chặn lời gọi `getBalance()` nếu nằm trong phạm vi và method đủ điều kiện. Nhưng thao tác truy cập trường `balance` **bên trong body** không tự trở thành một lời gọi interface method.

Nếu code khác đọc/ghi field trực tiếp theo quyền truy cập Java, thao tác ấy cũng không đi qua một proxy method thông thường.

Một số mô hình weaving cung cấp join point **field get/set**, nhưng không phải mọi cơ chế proxy có được khả năng đó. Ngoài ra, “field read/write” ở mức bytecode không mặc nhiên bao phủ mọi sự thay đổi trạng thái do reflection, native code hoặc hệ thống ngoài gây ra.

## 17. Default method của interface và private method trong interface

Từ Java 8, interface có thể khai báo **default method**; từ Java 9, interface có thể có **private method** phục vụ tái sử dụng logic bên trong interface.

Với Java Dynamic Proxy:

- Một **default method** là instance method của interface, nên lời gọi tương ứng **có thể tới `InvocationHandler`**.
- Handler phải quyết định cách thực thi tiếp theo: gọi implementation trên target, hoặc dùng cơ chế invoke default phù hợp do Java cung cấp.
- Một **private interface method** không phải API mà caller bên ngoài có thể gọi qua proxy như một public method giao diện.
- Một **static interface method** cũng không là lời gọi instance method qua proxy.

Đừng suy rằng “default method thì luôn chặn được mọi lời gọi nội bộ do nó thực hiện”. Các lời gọi tới helper private hoặc tới method khác bên trong mặc định có ranh giới dispatch riêng.

### 17.1. Khi target có implementation của default method

Nếu target override default method hoặc kế thừa implementation mặc định, việc handler dùng reflection gọi `method.invoke(target, args)` có thể dẫn tới implementation phù hợp trên **target**, không giống việc tự động gọi bản default của **proxy**.

Một số API Java hỗ trợ gọi cụ thể default method qua `InvocationHandler.invokeDefault` trong những điều kiện phù hợp. Đây là một API của JDK, không phải quy tắc chung của mọi engine AOP; cần kiểm tra hành vi cần đạt khi thiết kế handler.

## 18. Object methods và các phương thức đặc biệt

Với Java Dynamic Proxy, các method như `equals(Object)`, `hashCode()` và `toString()` có cơ chế dispatch tới handler theo hợp đồng proxy. Điều đó cần được xử lý có chủ đích.

Ví dụ một handler dùng `proxy.toString()` ngay trong `invoke` để ghi log có thể kích hoạt `invoke` lần nữa, gây **recursion** nếu không cẩn thận.

Ngược lại, những method `final` của `Object` như `getClass()` không được proxy override theo cách instance interface method thông thường.

### 18.1. Không đánh đồng identity proxy với identity target

`proxy == target` thường sai vì đó là hai đối tượng khác nhau. Việc `equals`, `hashCode` và `toString` có thể bị handler xử lý có nghĩa định danh, map key và log output có thể khác target.

Nếu code bên ngoài ép kiểu sang class implementation `FinalWorker`, một Java Dynamic Proxy chỉ triển khai `Work` **không tự là instance của FinalWorker**. Đây là ràng buộc type của API: không thể lấy interface proxy làm một đối tượng subtype của target final class.

**Thiết kế nên dựa trên interface phù hợp** nếu muốn ranh giới interface proxy thay thế được target trong những đường gọi chủ đích.

## 19. Modifier `final` và Java record/sealed types: đánh giá từng trường hợp

Nhu cầu dùng record, sealed type hoặc đối tượng bất biến không có nghĩa phải bỏ AOP, nhưng ảnh hưởng lên loại proxy.

### 19.1. Record

Record là class final. Không thể tạo subclass của record, nhưng record vẫn có thể **triển khai một interface bình thường**. Nếu có lời gọi instance method qua một interface proxy phù hợp, cơ chế ấy có thể đứng trước record.

### 19.2. Sealed class

Một sealed class liệt kê/giới hạn các subclass được phép. Một subclass sinh động không được phép theo quy tắc sealed không thể tự nhiên trở thành subclass proxy của sealed class đó. Đây là một ràng buộc **của cơ chế kế thừa**, không phải cấm weaving thân method trong mọi hoàn cảnh.

### 19.3. Sealed interface

Java Dynamic Proxy tiêu chuẩn có ràng buộc: không tạo proxy trực tiếp implement **sealed interface**. Nếu thiết kế API yêu cầu sealed interface cho một miền kiểu đóng, cần cân nhắc một ranh giới can thiệp khác thay vì mặc định interface proxy như với giao diện không sealed.

### 19.4. Kiểu bất biến

Một lớp immutable có thể là final; nếu vẫn cần quan sát giao diện ngoài của nó, interface proxy có thể phù hợp. Nhưng một policy chỉ quan sát thường không nên sửa đối tượng bất biến hoặc tìm cách chặn mọi truy cập field để thay đổi semantics của class.

## 20. Access modifier và tính hiển thị của API

Không chỉ có `private` và `public`. Java còn có **protected** và **package-private**; khả năng override/hợp lệ phụ thuộc phạm vi truy cập, package, class loader và cấu trúc kế thừa.

Một subclass proxy sinh động thường phải tuân thủ:

- Có được tạo subclass hợp lệ của target?
- Có constructor hợp lệ để khởi tạo subclass?
- Method muốn override có hiển thị cho subclass trong ngữ cảnh đó?
- Có dùng đúng package/module access không?
- Method có modifier cấm override không?
- Phần code có chạy trong đúng class loader/module?

Một method `protected` **có thể override** trong một số điều kiện hợp lệ; method `private` thì không. Package-private cũng có điều kiện package và runtime package riêng.

**Đừng suy rằng mọi method không-public đều như nhau.** Những quy tắc Java về kế thừa và truy cập khác nhau theo modifier và môi trường tạo subclass.

## 21. Module, class loader và class identity

Trên JVM hiện đại, **Java Platform Module System (JPMS)** giới hạn các kiểu truy cập và khả năng tạo/đọc metadata trong những module khác nhau.

Một proxy/interceptor có thể gặp những vấn đề như:

- Interface không có khả năng truy cập hợp lệ trong loader/module mong muốn.
- Một module không mở package cho một phép reflection cần thiết.
- Class target và class proxy ở những module/loader khác nhau.
- Một kiểu cùng tên được nạp qua nhiều class loader nhưng **không cùng runtime identity**.
- Mã sinh subclass không thể thỏa quy tắc package/private/protected access như dự kiến.

Không nên nhầm:

- **Compiler chấp nhận source**.
- **Proxy generator tạo được class trung gian**.
- **Handler có thể gọi target hợp lệ bằng reflection**.
- **Caller có thể cast và gọi object trung gian**.

Đó là các tầng riêng, có thể lỗi ở những thời điểm khác nhau.

## 22. Bytecode weaving không dựa hoàn toàn vào override

Một số hình thức weaving trực tiếp **method body**, **call site** hoặc **điểm truy cập field** theo cơ chế bytecode. Khi đó việc method là `final`, `private` hoặc `static` **không tự loại khả năng can thiệp**: ràng buộc chính nằm ở **join point model, scope, bytecode hợp lệ và giới hạn công cụ**.

Nhưng cũng không được kết luận ngược rằng bytecode weaving luôn can thiệp được:

- Mọi constructor, nhất là giai đoạn object chưa khởi tạo đủ.
- Mọi native method body.
- Mọi JVM-generated method/lambda theo cùng cách.
- Mọi field access do reflection hoặc native code.
- Mọi class đã được load từ trước.
- Mọi method khi VM giới hạn retransformation/redefinition.
- Mọi điểm trên nền tảng AOT/native không có JVM instrumentation tương ứng.

Một lựa chọn kỹ thuật cần căn cứ **bộ join point được công cụ công bố**, cách weaving, phiên bản JVM và kiểm thử thực tế.

### 22.1. Tại sao không chỉ đổi proxy sang weaver để “giải quyết hết”?

Weaving có thể tăng độ bao phủ nhưng mang thêm rủi ro: tương thích bytecode, class loader, JPMS, quyền instrumentation, startup/class loading, hiệu năng và khả năng phối hợp nhiều aspect. Độ sâu can thiệp cao hơn không mặc nhiên tốt hơn cho mọi yêu cầu.

Khi một chính sách chỉ cần quan sát API công khai, một interface proxy đơn giản có thể dễ bảo trì hơn. Khi cần field/constructor/internal execution, có thể phải dùng mô hình khác hoặc thiết kế một ranh giới quan sát rõ trong nghiệp vụ.

## 23. Điểm dễ nhầm: final method trên interface proxy

Câu sau **sai nếu phát biểu không kèm phạm vi**:

> “Method được khai báo final nên AOP không thể chặn nó.”

Một instance method `final` có thể hiện thực phương thức của một giao diện. Caller dùng **interface proxy**, handler nhận lời gọi trước khi chuyển xuống method `final` của target; vậy vẫn có interception ở phía ngoài.

Điều đúng:

> “Nếu cơ chế proxy cần **override chính phương thức final** trong một subclass, nó không làm được điều đó.”

Từ đó có một nguyên tắc thiết kế: khi đánh giá modifier, **hỏi modifier ảnh hưởng tới thao tác nào** — override, dispatch, đọc metadata, weaving body hay chỉ đường vào của caller.

## 24. Khi pointcut hợp lệ nhưng advice không chạy

Những trường hợp thường xuất hiện trong AOP qua proxy:

1. Caller gọi trực tiếp target, không dùng proxy.
2. Pointcut áp dụng cho method **không có trên giao diện proxy**.
3. Subclass proxy không override được method `final`.
4. Target `final class` không thể được subclass.
5. Method `private` được gọi bên trong target, không qua proxy.
6. Method `static` được gọi trực tiếp bằng class.
7. Constructor/field operation không là loại điểm proxy hỗ trợ.
8. Một lời gọi nội bộ không đi qua ranh giới proxy đã thiết kế.
9. Một advice bao ngoài short-circuit và không gọi tiếp khiến advice phía trong không chạy.
10. Điều kiện pointcut động không khớp trong lần gọi đó.
11. Method có overload khác chữ ký, hoặc interface được ánh xạ khác dự kiến.
12. Handler đã chạy nhưng chính logger/sink bỏ qua sự kiện.

Không phải tất cả là **lỗi runtime** hoặc **lỗi của AOP engine**. Một số là kết quả đúng của mô hình và đường gọi. Điều quan trọng là đối chiếu **hợp đồng độ bao phủ mà ứng dụng đã hứa**.

## 25. Advice bao quanh và `proceed` khi gặp giới hạn proxy

Trong một around advice, `proceed` hoặc continuation tương đương thường chuyển quyền điều khiển vào phần còn lại của chuỗi. Nhưng:

- Không gọi proceed có thể **bỏ qua phần gốc** theo semantics của cơ chế.
- Gọi nhiều lần có thể **thực thi lại phần trong** nếu được phép.
- Gọi một lần ở lớp ngoài không đảm bảo phần gốc chạy đúng một lần nếu advice phía trong short-circuit hoặc retry.
- Advice `finally` có thể chạy ở đường return và throw nếu có unwinding phù hợp.
- Một exception ném từ chính advice có thể che lỗi gốc.

Những điều này **độc lập một phần** với vấn đề method `private/static/final`. Nếu lời gọi vốn **không đi qua proxy**, ngay cả một advice viết đúng `proceed` cũng không được kích hoạt.

### 25.1. Không tự gọi lại method để thay proceed

Một advice gọi lại method bị can thiệp thông qua cùng proxy có thể tạo vòng lặp:

~~~text
handler -> proxy.execute() -> handler -> proxy.execute() -> ...
~~~

Hoặc nếu gọi thẳng target, nó có thể **bỏ qua các advice khác** trong chain. Cần dùng continuation đúng hợp đồng của cơ chế thay vì suy rằng một lệnh reflection tương đương `proceed`.

## 26. Bảng kiểm thử cho các thành phần không override được

| Ca | Câu hỏi mong đợi |
|---|---|
| Interface proxy → method target final | Handler có chạy không? Trả về đúng không? |
| Interface proxy → target final class | Proxy có triển khai interface và chạy được không? |
| Gọi trực tiếp target final method | Handler có bị bỏ qua đúng như mô hình không? |
| Method private gọi trong target | Có một interception riêng hay chỉ trong phạm vi outer method? |
| Static method trên target | Có bị gọi trực tiếp ngoài instance proxy không? |
| Subclass proxy → public overridable method | Override có thể chèn before/after không? |
| Subclass proxy → final method | Có bị cấm override hay không có advice ở chính method? |
| Subclass proxy → final class | Tạo subclass có bị ngăn bởi quy tắc Java không? |
| Protected/package-private method | Có điều kiện package/module để override hợp lệ không? |
| Method call nội bộ trên target | Dispatch có quay qua proxy/subclass override hay bỏ qua? |
| Default method trên interface | Handler/target xử lý default method theo hợp đồng nào? |
| Object `equals/hashCode/toString` | Handler có semantics identity và recursion an toàn không? |
| Constructor và field access | Công cụ có hỗ trợ loại join point tương ứng không? |
| Method execution dệt bytecode | Có quan sát đúng cả lời gọi nội bộ/direct call không? |

Cần kết hợp test dương/âm. Chỉ có một log `[proxy] execute` không chứng minh **mọi lời gọi** `execute` đi qua proxy.

### 26.1. Đếm phần gốc thay vì chỉ đếm advice

Một ca thử có thể có handler chạy hai lần nhưng nghiệp vụ chỉ một lần, hoặc ngược lại. Hãy dùng một **bộ đếm trong phần gốc** và một **bộ đếm tại interceptor** để xác nhận số lần thực thi phù hợp thiết kế.

Trong ví dụ `InterfaceProxyLimitsDemo`, hai lần `execute` chạy nghiệp vụ nhưng chỉ một lần đi qua proxy.

## 27. Hiệu năng, bảo mật và độ đúng của lời gọi

Thêm interception có chi phí: dispatch qua proxy, reflection hoặc mã wrapper, kiểm tra pointcut, tạo context, logging, serialization và có thể đồng bộ trạng thái.

Với `private/static/final`, điều nguy hiểm không chỉ là thiếu log mà còn là **giả định độ bao phủ sai**. Ví dụ một chính sách kiểm soát bắt buộc chỉ nằm trong interface proxy, nhưng hệ thống có đường gọi trực tiếp target, có thể tồn tại một đường **bỏ qua kiểm tra**.

Vì thế:

- Không dùng proxy đơn thuần làm hàng rào an toàn nếu còn những đường gọi tin cậy khác bỏ qua nó.
- Đặt các bất biến nghiệp vụ bắt buộc ở ranh giới thực sự kiểm soát được hoặc trong thành phần nghiệp vụ sở hữu chúng.
- Chứng minh độ bao phủ qua integration test, không chỉ unit test của handler.
- Không tự log mọi đối số vì có thể lộ token, thông tin tài khoản hoặc dữ liệu nhạy cảm.
- Thiết kế lỗi logger/interceptor để không vô ý đổi kết quả/ngoại lệ nghiệp vụ nếu chính sách chỉ nhằm quan sát.
- Đo overhead trên những method tần suất cao; không mở rộng pointcut chỉ vì công cụ có khả năng.

### 27.1. Quan sát không đồng nghĩa thực thi quyền truy cập

Một advice ghi `ACCESS CHECK PASSED` trên một đường proxy không đảm bảo mọi lời gọi ở hệ thống đều đã qua kiểm tra. Khi cần chính sách bắt buộc, phải định nghĩa **toàn bộ ranh giới truy cập**, không chỉ một đường gọi được trang bị proxy.

## 28. Quy trình lựa chọn chiến lược khi gặp modifier đặc biệt

Một cách quyết định có cấu trúc:

1. **Đề bài là gì?** Ghi log API công khai, đo method nội bộ, theo dõi field hay kiểm soát constructor?
2. **Công cụ can thiệp nào đang dùng?** Interface proxy, subclass proxy, bytecode weaving, instrumentation?
3. **Loại join point có được hỗ trợ không?** Không suy từ tên “AOP”.
4. **Method/class có modifier nào?** Private, static, final, sealed, record, protected, package-private?
5. **Có interface phù hợp không?** Caller có thể dùng interface proxy thay vì class implementation không?
6. **Lời gọi có đi qua proxy không?** Bao gồm các đường self-invocation và direct reference.
7. **Nếu dùng subclass, có thể kế thừa và override hợp lệ không?** Xem constructor, module, package và method visibility.
8. **Nếu cần sâu hơn, weaving có hỗ trợ không?** Kiểm tra phạm vi, bytecode/version và join point semantics.
9. **Advice có bảo toàn hành vi gốc không?** Return, exception và số lần gọi?
10. **Có kiểm thử cả đường không can thiệp không?** Tránh hứa bao phủ ngoài thực tế.
11. **Có vấn đề bảo mật/độ tin cậy nếu bỏ sót không?** Nếu có, đặt policy ở ranh giới được bảo đảm.
12. **Có phương án đơn giản hơn?** Một API wrapper/decorator rõ ràng đôi khi dễ kiểm soát hơn việc dệt sâu.

Mục tiêu là **thiết kế đúng yêu cầu và giới hạn**, không phải dùng công cụ phức tạp nhất.

## 29. Câu hỏi tự kiểm tra

### Câu 1. `final` có làm mọi AOP bất lực?

Một `final class` triển khai `Service`; method `execute` ở implementation là `final`. Caller gọi `Service` qua Java Dynamic Proxy. Handler có thể chạy không?

**Đáp án:** có, nếu proxy được tạo đúng và caller dùng proxy. Handler chặn lời gọi interface; nó không override lớp final/method final của target.

### Câu 2. Vì sao subclass proxy không override `final`?

**Đáp án:** Java cấm lớp con override một final instance method. Muốn quan sát bằng lớp con phải chọn method đủ điều kiện override hoặc một ranh giới can thiệp khác.

### Câu 3. Private helper

Một public method được proxy chặn rồi gọi private helper. Có chắc handler nhận thêm một lần invocation cho private helper không?

**Đáp án:** không. Proxy interface không tự chặn private helper nội bộ. Dòng code private chạy trong phần gốc nhưng không là lời gọi giao diện qua proxy.

### Câu 4. Static method

Lớp con khai báo static method cùng tên với lớp cha. Đây có phải override đa hình và làm handler chạy không?

**Đáp án:** không. Đây có thể là *method hiding*, không phải override. Một instance interface proxy không tự chặn static method.

### Câu 5. Self-invocation

Client gọi qua interface proxy `checkout()`; bên trong target `checkout` gọi `charge()` bằng `this`. Nếu handler không ghi thêm `charge`, phải kết luận `charge` không chạy?

**Đáp án:** không. `charge` vẫn có thể chạy trực tiếp trên target. Thiếu handler log không chứng minh thiếu nghiệp vụ.

### Câu 6. Lớp final và record

Một record triển khai interface `Readable`. Có thể tạo subclass proxy của record không? Có thể cân nhắc interface proxy không?

**Đáp án:** record là final nên không thể subclass. Một interface proxy có thể áp dụng nếu interface phù hợp với ràng buộc của cơ chế và caller dùng proxy.

### Câu 7. Sealed interface

Một công cụ tạo Java Dynamic Proxy tiêu chuẩn muốn implement trực tiếp một sealed interface. Có được giả định thành công như giao diện bình thường không?

**Đáp án:** không. Sealed interface hạn chế class triển khai; Java Dynamic Proxy tiêu chuẩn không tạo proxy implement sealed interface trực tiếp.

### Câu 8. Bytecode weaving

Một method `private` có bị cấm tuyệt đối đối với mọi dạng AOP không?

**Đáp án:** không. Nó không override được và không được chặn riêng bằng interface proxy thông thường; một mô hình weaving khác có thể quan sát method execution phù hợp, nếu công cụ/VM hỗ trợ.

### Câu 9. Static method và field

Một proxy chặn lời gọi `getBalance()`. Có chứng minh mọi lần đọc field `balance` được chặn không?

**Đáp án:** không. Lời gọi getter là method invocation, còn field get có thể là một loại join point khác hoặc nằm ngoài mô hình proxy.

### Câu 10. Subclass self-invocation

Subclass proxy override được `open()`. Một method khác trên **cùng instance proxy** gọi virtual `open()`. Có khả năng đi qua override không?

**Đáp án:** có, theo virtual dispatch nếu lời gọi không bị buộc vào implementation khác và method override hợp lệ. Vì thế không khẳng định mọi self-invocation đều bỏ qua tất cả loại proxy.

### Câu 11. Object methods

Handler log bằng `proxy.toString()` trong chính handler có thể có rủi ro gì?

**Đáp án:** `toString()` của Java Dynamic Proxy có thể đi qua handler; handler tự gọi lại proxy có thể tạo recursion không mong muốn. Cần định nghĩa xử lý Object methods rõ ràng.

### Câu 12. Chính sách bắt buộc

Một policy phân quyền chỉ chạy khi caller dùng interface proxy. Có thể tuyên bố mọi lời gọi target đều đã được kiểm tra?

**Đáp án:** không, nếu tồn tại đường gọi trực tiếp target hoặc các điểm khác ngoài ranh giới proxy. Policy bắt buộc cần được thực thi ở nơi có độ bao phủ được bảo đảm.

## 30. Ghi nhớ

- **`private`** không thể override; private helper trên target không tự có interception riêng khi dùng interface proxy.
- **`static`** không được override đa hình; method hiding khác với interception qua virtual method.
- **`final method`** chặn subclass override, **không chặn mặc định interface proxy** đứng trước lời gọi đến một implementation final.
- **`final class`** không thể subclass nhưng vẫn có thể triển khai giao diện và đứng sau một interface proxy.
- **Record/sealed type** đưa thêm ràng buộc vào chiến lược subclass/proxy; phải kiểm tra đúng loại.
- **Constructor và field** không thể được xử lý như một virtual instance method bình thường chỉ bằng proxy.
- **Self-invocation** phụ thuộc đường gọi: interface proxy trên target thường bị bỏ qua, subclass override có thể được dispatch tới trong những trường hợp hợp lệ.
- **Bytecode weaving** có thể khác proxy về khả năng quan sát private/static/final, nhưng còn tùy join point model, phạm vi và giới hạn JVM.
- **Pointcut đúng chưa đủ**: phải có loại điểm hỗ trợ, cơ chế can thiệp thực sự hoạt động và đường runtime đi qua đúng vị trí.
- Chính sách quan sát/kiểm soát cần **kiểm thử dương/âm**, bảo toàn giá trị/ngoại lệ và tránh tuyên bố độ bao phủ vượt khả năng.

**Kết luận quan trọng:** Khi đánh giá một phương thức có `private`, `static` hoặc `final`, hãy hỏi **“công cụ cần override phương thức đó, chặn lời gọi qua một giao diện, hay dệt trực tiếp vào mã?”**. Chỉ sau khi biết câu trả lời mới xác định được modifier ấy thật sự giới hạn loại AOP đang áp dụng như thế nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-006">Đối tượng được tạo ngoài cơ chế quản lý proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng được tạo ngoài cơ chế quản lý proxy trong AOP

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một cách triển khai phổ biến là đặt **proxy (đối tượng trung gian)** trước đối tượng nghiệp vụ. Proxy nhận lời gọi, áp dụng advice phù hợp rồi chuyển tiếp đến target. Cách này có thể bổ sung logging, audit, tracing, đo thời gian hay các hành vi xuyên suốt khác **mà không phải sửa thân phương thức nghiệp vụ**.

Tuy nhiên, một giới hạn rất quan trọng xuất hiện khi chương trình **tự tạo đối tượng đích ngoài cơ chế tạo, đăng ký hoặc cung cấp proxy**, ví dụ:

```java
DocumentService service = new DocumentService();
service.save("DOC-17");
```

Nếu `service` ở đây là **đối tượng gốc (raw target)** và không có interception khác, lời gọi đi **thẳng vào target**, không qua proxy. Một pointcut dù mô tả đúng `save(String)` cũng **không làm advice tự xuất hiện trên đường gọi ấy**.

**Ý chính:** Proxy AOP kiểm soát **đường đi của lời gọi qua proxy**, không tự kiểm soát toàn bộ các instance của một class. Điều quyết định là **reference mà caller thật sự dùng**, **ai tạo và phân phối reference đó**, và **lời gọi có đi qua interceptor không** — chứ không đơn thuần là tên class hay annotation trên method.

> Bài này nói về **AOP nói chung**, từ cơ bản đến nâng cao. Các thuật ngữ “managed”, “unmanaged”, “factory”, “registry”, “proxy provider” được dùng ở nghĩa kiến trúc tổng quát, **không gắn với một framework cụ thể**. Giới hạn ở đây là của **proxy-based interception**; một hệ thống AOP dệt bytecode tại compile time hoặc load time có thể có phạm vi khác nếu những join point tương ứng thực sự đã được weave.

## 1. Bản chất của cơ chế AOP qua proxy

Một proxy có thể triển khai cùng giao diện với target và ủy quyền lời gọi sau khi chạy advice:

```text
                  Đường có interception

Caller --> Proxy --> Before advice --> Target.method(...)
              ^                            |
              |                            v
              +---- After / around <--- Result / Exception
              |
         Caller nhận outcome
```

Nếu caller giữ reference trực tiếp:

```text
                  Đường không đi qua proxy

Caller -----------------------> Target.method(...)
                                        |
                                        v
                                Result / Exception
```

Điều này **không đồng nghĩa method thiếu annotation** hay **pointcut không hợp lệ**. Thông thường, điểm khác nằm ở **object được gọi**: caller gọi `proxy.method(...)` hay `rawTarget.method(...)`.

### Tách ba điều kiện độc lập

Để advice của mô hình proxy chạy, cần đồng thời:

1. **Có interception boundary:** proxy/interceptor thật sự tồn tại, có cơ chế chuyển tiếp hợp lệ.
2. **Caller đi qua boundary:** reference nhận lời gọi là proxy hoặc một adapter/hook tương đương.
3. **Advice được chọn:** quy tắc type, method, signature, annotation, ngữ cảnh hoặc predicate động khớp theo semantics của interceptor.

Một method khớp pointcut nhưng bị gọi trực tiếp có thể bỏ qua advice. Ngược lại, caller đi qua proxy nhưng method không khớp pointcut thì proxy vẫn có thể chuyển tiếp mà **không áp dụng concern đó**. Phải chẩn đoán hai tình huống khác nhau.

## 2. “Đối tượng được quản lý” nghĩa là gì?

Trong phạm vi kiến thức này, một **cơ chế quản lý proxy** có thể là:

- Factory chuyên tạo **target rồi wrap thành proxy**.
- Registry/service locator chỉ công bố **reference proxy** tới client.
- Một thành phần dựng object graph rồi đưa **proxy** vào những điểm phụ thuộc.
- Một pipeline interceptor cho phép đăng ký target/handler.
- Một API wrapper chính thức để caller yêu cầu object đã được can thiệp.

Không nhất thiết có một container hay framework. Ví dụ rất đơn giản:

```java
// Đoạn minh họa ý tưởng, không phải chương trình độc lập.
Operations service = ProxyFactory.create();
service.process("A");
```

Nếu `create()` trả proxy, caller gọi qua proxy. Nhưng:

```java
Operations service = new OperationsImpl();
service.process("A");
```

thì caller trực tiếp giữ target, **trừ khi bytecode của target/call site còn được instrument bằng cơ chế khác**.

**Không nên định nghĩa “managed” chỉ là “object được lưu trong một map”.** Một registry giữ raw target vẫn cung cấp đường bypass. Giá trị của cơ chế quản lý là nó **bảo đảm reference mà consumer cần dùng đi qua interception boundary** theo một contract rõ ràng.

### Chỉ tạo proxy thôi chưa đủ

```text
factory:
    target = new Service()
    proxy = wrap(target)
    return proxy
```

Nếu code khác vẫn được trả `target`, hoặc biết cách tự gọi `new Service()` rồi sử dụng, phạm vi proxy chưa toàn diện. Kiến trúc cần quản lý cả **quyền sở hữu target** và **cách phân phối reference**.

## 3. Các đường tạo đối tượng dễ đi ngoài cơ chế proxy

| Đường tạo/lấy object | Ví dụ | Nguy cơ với proxy AOP |
| --- | --- | --- |
| Tạo trực tiếp | `new Service()` | Caller nhận raw instance |
| Static factory không wrap | `Service.createDefault()` | Factory trả raw instance |
| Factory do client tự viết | `() -> new Service()` | Không đi qua factory cấp proxy |
| Reflection | `constructor.newInstance(...)` | Tạo raw instance nếu không wrap |
| Deserialization | Object được tái tạo từ dữ liệu | Instance có thể không đi qua proxy provider |
| Clone/copy | `clone()`, copy constructor | Instance khác raw hoặc metadata/interception bị mất |
| Callback/plugin tự tạo | Plugin tạo instance rồi đăng ký | Provider không kiểm soát reference ban đầu |
| Test tạo fixture | `new Service()` trong unit test | Test có thể bỏ qua advice khi integration cần kiểm |
| Một thư viện tự tạo dependency | Nội bộ thư viện dùng constructor/factory riêng | Interception boundary bên ngoài có thể không thấy |
| Object do native/runtime code sinh | Đường khởi tạo đặc thù | Phải xác nhận integration thực tế |

Bảng là **danh sách rủi ro**, không phải kết luận mọi đường này luôn bypass **mọi** mô hình AOP. Nếu bytecode execution của class đã được weave, một instance mới vẫn có thể đi vào advice tại execution tương ứng. **Giới hạn đang phân tích là cơ chế AOP dựa trên proxy reference**.

### `new` không phải “chỉ báo lỗi” tự thân

Việc gọi `new` hoàn toàn hợp lệ trong Java. Nó chỉ gây thiếu advice **nếu object đó đáng ra phải được truy cập qua proxy nhưng lại được dùng trực tiếp như target**. Một object thuần dữ liệu không cần advice vẫn có thể được tạo bằng `new` mà không có vấn đề gì.

## 4. Tách object identity khỏi loại nghiệp vụ

Giả sử có hai reference:

```java
Operations raw = new OperationsImpl();
Operations wrapped = ProxyFactory.wrap(raw);
```

Chúng có thể cùng biểu diễn **một dịch vụ nghiệp vụ** ở tầng API, nhưng **không nhất thiết là cùng object Java**:

- `raw` trỏ tới implementation object.
- `wrapped` trỏ tới proxy object khác, vốn giữ hoặc truy cập `raw`.
- Cả hai có thể cùng implement `Operations`.
- `raw == wrapped` thường là `false` với dynamic proxy dạng wrapper.
- Gọi qua `raw` và gọi qua `wrapped` có thể có advice khác nhau dù target method cuối cùng giống nhau.

**Cùng target nghiệp vụ không đồng nghĩa cùng interception path.** Chính sự tồn tại đồng thời của hai reference này tạo nguy cơ gọi vòng qua proxy.

### Sơ đồ rò reference

```text
               Factory tạo dịch vụ
                      |
                 +----+----+
                 |         |
                raw       proxy ----> raw
                 |         |
             Caller B   Caller A
                 |         |
             no advice  advice
```

Một factory tốt có thể trả về **chỉ proxy qua public API**, giữ raw reference nội bộ để tránh caller sử dụng nhầm. Tuy nhiên, raw vẫn có thể thoát qua một API phụ, callback hoặc getter nếu thiết kế không cẩn thận.

## 5. Ví dụ Java 17+ chạy được: cùng target nhưng hai đường gọi

Ví dụ sau dùng **Java Dynamic Proxy** từ Java SE, không phụ thuộc thư viện AOP. Proxy áp dụng before/after/error advice trên những lời gọi đi qua nó. Chương trình còn chủ động tạo object ngoài factory bằng `new` và reflection để cho thấy các object đó không tự mang interceptor.

Một trường hợp đặc biệt được minh họa: `proxy.outer(...)` **đã đi qua proxy** nhưng bên trong target, `this.process(...)` vẫn **không** quay lại proxy. Đây là giới hạn self-invocation gắn với proxy đứng ngoài target.

### Tệp `UnmanagedProxyDemo.java`

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class UnmanagedProxyDemo {

    interface Operations {
        String process(String value);
        String outer(String value);
    }

    static final class Service implements Operations {
        private final String id;

        Service(String id) {
            this.id = id;
            System.out.println("create " + id);
        }

        @Override
        public String process(String value) {
            System.out.println("target[" + id + "].process " + value);
            if ("fail".equals(value)) {
                throw new IllegalArgumentException("invalid");
            }
            return "OK:" + value;
        }

        @Override
        public String outer(String value) {
            System.out.println("target[" + id + "].outer " + value);
            // Lời gọi nội bộ đi tới this, không quay về proxy.
            return this.process(value + "-inner");
        }
    }

    static final class ProxyFactory {
        private ProxyFactory() { }

        static Operations wrap(Operations target) {
            return (Operations) Proxy.newProxyInstance(
                    Operations.class.getClassLoader(),
                    new Class<?>[] { Operations.class },
                    (proxy, method, arguments) -> {
                        System.out.println(
                                "advice.before " + method.getName());
                        try {
                            Object result = invoke(method, target, arguments);
                            System.out.println(
                                    "advice.after " + method.getName());
                            return result;
                        } catch (Throwable error) {
                            System.out.println(
                                    "advice.error "
                                    + method.getName() + " "
                                    + error.getClass().getSimpleName());
                            throw error;
                        }
                    });
        }

        static Operations create(String id) {
            return wrap(new Service(id));
        }
    }

    static Object invoke(
            Method method, Operations target, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    public static void main(String[] args) throws Exception {

        // Đường được quản lý: factory trả proxy.
        Operations managed = ProxyFactory.create("managed");
        System.out.println("isProxy "
                + Proxy.isProxyClass(managed.getClass()));
        System.out.println("caller "
                + managed.process("A"));

        // Đường không quản lý: new trả raw object.
        Operations raw = new Service("raw");
        System.out.println("caller "
                + raw.process("B"));

        // Lời gọi ngoài đi qua proxy, nhưng lời gọi nội bộ thì không.
        System.out.println("caller "
                + managed.outer("C"));

        // Một instance được tạo ngoài factory bằng reflection.
        Operations reflective = Service.class
                .getDeclaredConstructor(String.class)
                .newInstance("reflective");
        System.out.println("caller "
                + reflective.process("D"));

        // Raw target cũng có thể được wrap sau khi tạo.
        Operations wrappedRaw = ProxyFactory.wrap(raw);
        System.out.println("caller "
                + wrappedRaw.process("E"));

        // Reference gốc vẫn là đường bypass ngay cả sau khi wrap.
        System.out.println("caller "
                + raw.process("F"));

        // So sánh đường báo lỗi qua proxy và đường trực tiếp.
        try {
            managed.process("fail");
        } catch (IllegalArgumentException error) {
            System.out.println("caller managed error "
                    + error.getMessage());
        }

        try {
            raw.process("fail");
        } catch (IllegalArgumentException error) {
            System.out.println("caller raw error "
                    + error.getMessage());
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 UnmanagedProxyDemo.java
java UnmanagedProxyDemo
```

**Standard output:**

```text
create managed
isProxy true
advice.before process
target[managed].process A
advice.after process
caller OK:A
create raw
target[raw].process B
caller OK:B
advice.before outer
target[managed].outer C
target[managed].process C-inner
advice.after outer
caller OK:C-inner
create reflective
target[reflective].process D
caller OK:D
advice.before process
target[raw].process E
advice.after process
caller OK:E
target[raw].process F
caller OK:F
advice.before process
target[managed].process fail
advice.error process IllegalArgumentException
caller managed error invalid
target[raw].process fail
caller raw error invalid
```

### 5.1. Tại sao `managed.process("A")` có advice?

`ProxyFactory.create("managed")` tạo `Service` rồi **trả proxy**, không trả reference `Service` gốc. Caller dùng proxy nên handler in `advice.before`, chuyển tiếp bằng reflection đến target và sau khi thành công in `advice.after`.

Phương thức `process` ở target **không cần tự biết advice**. Điều kiện đủ của ví dụ là lời gọi được gửi tới proxy và handler được thiết kế chạy logic cho method tương ứng.

### 5.2. Vì sao `raw.process("B")` không có advice?

`new Service("raw")` trả đối tượng `Service` thực. Caller dùng trực tiếp reference đó; chưa có cơ chế interception nào đi giữa caller và method. Vì vậy chỉ có `target[raw].process B` rồi kết quả caller.

Đây là **thiếu proxy path**, không phải method `process` không tồn tại hay interface thiếu khả năng được proxy.

### 5.3. Vì sao `managed.outer("C")` chỉ có một cặp advice?

Caller gọi `outer` qua proxy nên có `advice.before outer` và `advice.after outer`. Nhưng trong thân `Service.outer`, `this.process("C-inner")` dùng **`this` là target object**, không phải proxy. Vì vậy không có cặp `advice.before process` / `advice.after process` cho lời gọi nội bộ.

Việc target tự gọi method trong cùng class **khác** với client tự tạo một target mới, nhưng cả hai đều minh họa giới hạn quan sát của proxy: **đường gọi bỏ qua boundary**.

### 5.4. Vì sao reflection cũng có thể tạo instance raw?

`Service.class.getDeclaredConstructor(String.class).newInstance("reflective")` gọi constructor để tạo `Service`. Kết quả là raw object, **không tự được wrap** chỉ vì nó được tạo qua reflection.

Reflection còn có những quy tắc về visibility/module access và exception riêng. Demo giữ constructor truy cập được trong cùng compilation unit để tập trung vào semantics của proxy.

### 5.5. Wrap sau khi object đã được tạo có giải quyết không?

`ProxyFactory.wrap(raw)` tạo proxy **trỏ tới cùng raw target đã tồn tại**, vì vậy `wrappedRaw.process("E")` có advice. Nhưng `raw` không biến thành proxy và vẫn có thể được dùng tiếp. `raw.process("F")` không có advice.

**Wrap thành công chỉ tạo thêm một đường có interception**, không tự xóa mọi đường gọi trực tiếp vào instance gốc.

### 5.6. Còn trường hợp target ném ngoại lệ?

Khi caller gọi `managed.process("fail")` qua proxy, handler thấy exception, in `advice.error` rồi truyền lỗi gốc từ `InvocationTargetException.getCause()`. Còn `raw.process("fail")` ném trực tiếp mà không có dòng advice.

Cả hai caller đều bắt được `IllegalArgumentException("invalid")`: **outcome nghiệp vụ giống nhau không chứng minh advice đều đã chạy**. Cần test trực tiếp số lần advice hoặc sự kiện audit/metric, không chỉ return/exception.

### 5.7. Giới hạn của demo

- Dynamic proxy chỉ intercept **method của interface được proxy công bố**.
- Nó không dệt bytecode `Service`.
- Tên/kiểu method trong handler được dùng để minh họa, chưa có pointcut DSL tổng quát.
- Không có cơ chế tự phát hiện/đăng ký mọi instance `Service`.
- Không thể suy rộng hành vi này sang mọi subclass proxy hay bytecode weaving engine.
- `advice.after` trong demo chỉ chạy trên nhánh thành công; nhánh lỗi dùng `advice.error`, không phải after/finally.
- Proxy giữ reference tới target nhưng **không kiểm soát các reference raw khác**.
- Trong ứng dụng thực, không nên thay audit bền vững bằng `System.out.println`.

## 6. Tạo trong một factory có nhất thiết là “managed” không?

Không. Một **factory method** chỉ là hàm tạo/cung cấp object. Nếu factory trả raw target, caller vẫn không qua interception. Ví dụ:

```java
// Minh họa:
static Operations createService() {
    return new Service("raw");
}
```

Ngược lại, một factory trả `ProxyFactory.wrap(new Service(...))` tạo đường đã được can thiệp.

**Không phân loại chỉ bằng cú pháp `new` xuất hiện ở đâu.** Một factory có thể dùng `new` **bên trong**, nhưng trả proxy ra bên ngoài. Vấn đề là **reference được cung cấp cho consumer**, không phải hoàn toàn tránh từ khóa `new`.

### Hai kiểu factory khác nhau

```text
Factory không bảo đảm proxy:
  create() -> new Target()
                  |
               raw target

Factory bảo đảm proxy:
  create() -> new Target() -> wrap(Target)
                                    |
                                   proxy
```

API nên thể hiện contract: `createProxiedService`, `openInterceptedSession` hoặc một cách đặt tên/tài liệu rõ ràng phù hợp dự án. Quan trọng hơn tên là **test rằng mọi đường dùng trong phạm vi chính sách đều nhận reference qua interception boundary**.

## 7. Reference escape: đối tượng raw bị lộ ra ngoài

Một trong những nguyên nhân phổ biến làm mất advice là **raw target escape**. Chẳng hạn:

```java
// Mã giả về thiết kế, không phải chương trình Java hoàn chỉnh.
class ServiceProvider {
    private final Operations raw = new OperationsImpl();
    private final Operations proxy = wrap(raw);

    Operations getService() {
        return proxy;
    }

    Operations getRawForDiagnostics() {
        return raw;  // Mở đường bypass cho bất kỳ caller nào lấy được.
    }
}
```

Nếu client được cấp cả proxy lẫn raw, nó có thể vô tình hoặc cố ý chọn đường không có advice.

**Thiết kế tốt:** hạn chế quyền truy cập vào raw target, chỉ công bố contract đã được can thiệp cho những thao tác nằm trong phạm vi AOP. Tuy nhiên, không nên làm raw target “không thể sử dụng” bằng cách phá thiết kế type hay nhúng check lạ trong mỗi method; cần giải quyết ở **object ownership và wiring**.

### Tham chiếu raw đã được lưu trước khi wrap

```text
t0: target = new Service()
t1: Client A nhận target
t2: proxy = wrap(target)
t3: Client B nhận proxy

A vẫn có raw target từ t1.
B có proxy từ t3.
```

Wrap ở `t2` không tự cập nhật reference trong `Client A`. Java reference thông thường không phải một handle “tự nâng cấp thành proxy” khi có ai đó tạo wrapper mới.

Nếu muốn không có đường bypass, phải giải quyết **từ thời điểm phân phối dependency**, hoặc thay những reference đã phát tán trong phạm vi kiểm soát được.

## 8. Tại sao annotation trên class không tự tạo proxy?

Giả sử method được đánh dấu:

```java
// Khai báo minh họa:
@Audited
void transfer(String accountId);
```

Một annotation có thể cung cấp metadata cho pointcut hoặc advice. Nhưng annotation **không phải một cơ chế khởi tạo object, không tạo proxy, không weave bytecode** chỉ vì nó có mặt trên source.

Để advice chạy qua mô hình proxy, cần một thành phần thực sự:

1. Đọc/nhận diện metadata tương ứng khi phù hợp.
2. Tạo hoặc cung cấp proxy có interceptor.
3. Bảo đảm caller dùng proxy.
4. Chọn và áp dụng advice khi method được gọi.

Khi một đoạn mã vẫn gọi `new TransferService().transfer(...)` trực tiếp, chỉ có annotation trong class không đủ để “can thiệp từ xa”.

### Metadata có thể ở interface hay implementation

Nếu interface method có annotation nhưng implementation method không có, một resolver chỉ đọc annotation trực tiếp trên implementation có thể không thấy nó. Điều này là **vấn đề tìm metadata**, khác với **đường gọi bypass proxy**.

Khi debug, xác minh từng lớp: **đúng reference? đúng method metadata? đúng pointcut? đúng advice?** Không quy mọi triệu chứng thiếu log về một nguyên nhân.

## 9. Cách tổ chức dependency để caller luôn dùng proxy

Một kiến trúc đơn giản:

```text
                   Object composition root
                            |
                   Tạo raw target
                            |
                       Wrap thành proxy
                            |
             +--------------+--------------+
             |                             |
         Consumer A                    Consumer B
             |                             |
             +---------- Proxy ------------+
                            |
                          Target
```

**Composition root** là nơi chương trình tập trung việc tạo và nối các object quan trọng. Khái niệm này không yêu cầu framework. Có thể là `main`, factory của module, bootstrap code hoặc một thành phần riêng.

Các nguyên tắc:

- Consumer khai báo phụ thuộc qua **interface/contract** nếu thuận tiện cho proxy.
- Tập trung nơi tạo/wrap object.
- Không để consumer tự ý `new` lại service mà policy yêu cầu đi qua proxy.
- Không cấp public getter trả raw target trừ khi đó là quyết định kiến trúc có kiểm soát.
- Nếu cần nhiều scope/lifetime, factory phải tạo đúng proxy theo từng scope.
- Test công khai cần gọi **API cung cấp reference đã can thiệp**, không tự tạo raw rồi kết luận advice sai.

### Dependency injection tự viết cũng có thể đúng

```java
// Minh họa, không phải chương trình độc lập.
final class Consumer {
    private final Operations service;

    Consumer(Operations service) {
        this.service = service;
    }

    void run() {
        service.process("job");
    }
}
```

Nếu composition root truyền **proxy**, `Consumer` gọi qua proxy. Nếu truyền raw target, cùng class `Consumer` không có advice. Vì vậy dependency injection là **cách cung cấp reference**, không tự bảo đảm interception trừ khi object graph được cấu hình và kiểm chứng đúng.

## 10. Một instance có thể được wrap nhiều lần không?

Có thể:

```text
target
  ^
  |
proxy A (audit)
  ^
  |
proxy B (tracing)
  ^
  |
caller
```

Ở đây caller gọi qua proxy B, proxy B chuyển sang proxy A, proxy A chuyển sang target. Hai proxy có thể tạo hai lớp interceptor có thứ tự khác nhau.

Nhưng cũng có nguy cơ:

- **Double wrapping** cùng concern gây log/audit trùng.
- Hai proxy cùng giữ state nhưng không thống nhất lifetime.
- Method `equals`, `hashCode`, `toString` được chuyển tiếp khác nhau, làm identity semantics khó hiểu.
- Các phép cast sang concrete implementation không còn hợp lệ với interface proxy.
- Client vô tình trộn reference từ hai phiên bản proxy khác nhau.

**“Object đã có proxy” không tự nghĩa “object được bảo vệ đúng một lần”.** Nên có chính sách ownership rõ về ai được wrap, thứ tự concern và cách ngăn duplicate interception ngoài ý muốn.

## 11. Object identity, `equals` và `getClass`

Một Java dynamic proxy là một object do runtime tạo; nó có thể implement interface giống target, nhưng class thực tế khác. Vì thế:

```java
// Mã ý tưởng:
raw.getClass() == proxy.getClass()  // thường false
raw == proxy                       // false với wrapper proxy khác instance
```

Đừng coi hai reference là cùng object chỉ vì chúng dẫn đến cùng hành vi nghiệp vụ.

Các hệ quả:

- Một client yêu cầu cast sang concrete class có thể không cast được interface proxy.
- Logic dựa vào `getClass()` hoặc reflection có thể thấy proxy class thay vì implementation.
- `equals`/`hashCode` cần semantics rõ nếu proxy và raw target làm khóa map/set.
- Identity-based cache có thể xem raw và proxy là hai khóa khác nhau.
- Annotation trực tiếp trên proxy class không nhất thiết chứa metadata được khai báo trên target.

**Đừng “sửa” bằng cách coi proxy luôn bằng raw**: nếu equality không được thiết kế nhất quán, hash-based collections và so sánh hai phía có thể sai. Với AOP proxy, thường nên cho client phụ thuộc vào **contract nghiệp vụ**, tránh dùng class identity làm phần cốt lõi của logic.

## 12. Chọn kiểu proxy không tự sửa được đường tạo object

**Interface proxy** triển khai contract giao diện và thường giữ target riêng; caller có thể bỏ qua wrapper nếu còn tham chiếu raw. **Subclass proxy** có quan hệ subtype với implementation, nhưng chỉ intercept các method theo khả năng kế thừa, override và cơ chế dispatch cụ thể. Class/method `final`, method `private`, `static`, constructor và module access có giới hạn khác nhau.

Trong bài toán **đối tượng tạo ngoài cơ chế quản lý**, dù chọn kiểu nào vẫn phải hỏi: ai tạo object, ai giữ object gốc, caller nhận reference nào và một instance mới có đi qua cơ chế cung cấp proxy không? Đổi từ interface proxy sang subclass proxy **không tự cập nhật các reference đã phát tán**.

Nếu consumer phụ thuộc trực tiếp vào concrete implementation class, có thể không truyền được một JDK interface proxy thay cho object ấy. Thiết kế theo interface hoặc facade thích hợp giúp quản lý boundary, nhưng ranh giới phải xuất phát từ hợp đồng của ứng dụng, không chỉ để tiện tạo proxy.

## 13. Self-invocation khác unmanaged instance, nhưng cùng dẫn tới bypass

Hai tình huống cần phân biệt:

**A. Unmanaged instance:**

```text
caller -> new Service() -> raw.method()
```

Không có proxy trên đường gọi ngay từ đầu.

**B. Self-invocation qua target:**

```text
caller -> proxy.outer() -> target.outer()
                             |
                             +--> this.inner()
```

Lời gọi **ngoài** đã qua proxy; lời gọi **trong** sử dụng `this` của target. Một proxy dạng **wrapper** thường chỉ thấy `outer`, không thấy `inner`.

Chúng có triệu chứng giống nhau (“advice cho `inner` không chạy”) nhưng cách xử lý khác:

- Unmanaged instance: sửa **đường khởi tạo, wiring và phân phối reference**.
- Self-invocation: cân nhắc **tách component, đi qua boundary có chủ đích, hoặc dùng execution weaving nếu công cụ hỗ trợ**.
- Không tự chèn “tìm proxy toàn cục của chính mình” vào mọi target method nếu điều đó tạo dependency vòng tròn và làm logic khó hiểu.

## 14. Reflection, factory bên thứ ba và plugin

Một chương trình có thể tạo object qua reflection:

```java
// Minh họa:
Class<?> type = Class.forName("com.example.Service");
Object instance = type.getDeclaredConstructor().newInstance();
```

Đây là một đường khởi tạo object thông thường. Nếu không có thành phần wrap kết quả, `instance` vẫn là target object. Tên class được resolve đúng không đồng nghĩa runtime tự đăng ký nó với proxy provider.

Đối với plugin hoặc thư viện:

- Plugin có thể tự tạo object rồi gửi về application host.
- Host có thể wrap ở ranh giới plugin API nếu contract cho phép.
- Plugin có thể giữ raw reference riêng và gọi nội bộ.
- Host không nên kết luận đã intercept toàn bộ plugin chỉ vì những lời gọi từ host đi qua một adapter.
- Nếu plugin reload, object/proxy cũ có thể còn sống và trỏ tới class loader thế hệ trước.

**Host có thể kiểm soát reference mà nó sử dụng, nhưng không mặc nhiên kiểm soát mọi lời gọi do plugin phát sinh nội bộ.** Nếu yêu cầu coverage rộng hơn, cần cơ chế weaving/instrumentation hoặc hook được thiết kế ở đúng boundary.

### Reflection vào proxy và Reflection vào raw

Nếu `Method.invoke` được gọi với receiver là **proxy** cho một interface method phù hợp, invocation có thể đi qua handler của proxy. Nếu receiver là **raw target**, Reflection gọi trực tiếp target method.

Reflection **không phải ngoại lệ toàn cục** khiến AOP luôn bị bỏ qua hay luôn được áp dụng. Quan trọng là object receiver, method được gọi và cách engine định nghĩa điểm can thiệp.

## 15. Serialization, deserialization, copy và clone

Một object được tái tạo từ dữ liệu hoặc copy có thể không đi qua factory ban đầu. Vì vậy:

- Không mặc định deserialization của một target class sẽ trả object đã được wrap.
- Không mặc định serialization của một proxy sẽ bảo toàn handler, target reference, context và các điều kiện tương thích phiên bản.
- Không mặc định clone/copy constructor sẽ giữ cấu trúc proxy.
- Các cơ chế Java serialization, custom serializer, record constructor hay thư viện mapping có **quy tắc khởi tạo khác nhau**; không khẳng định mọi cơ chế đều gọi constructor theo cùng cách.

**Chiến lược an toàn hơn:** tách **dữ liệu** cần lưu/truyền ra khỏi **service object có aspect**, rồi cho service được tạo/cung cấp qua provider chuẩn. Ví dụ lưu `OrderData`, không tìm cách serialize toàn bộ graph gồm proxy, runtime handler và resource.

Nếu domain thật sự cần clone một service object, phải xác định rõ bản sao có cần được intercept không, ai wrap nó và liệu raw references còn tồn tại hay không.

## 16. Executor, callback và tác vụ bất đồng bộ

Đối tượng unmanaged không chỉ xuất hiện trong luồng chính. Một callback hoặc task có thể tự tạo service:

```java
// Mã minh họa, không phải chương trình độc lập.
executor.submit(() -> {
    Operations local = new OperationsImpl();
    local.process("async");
});
```

Ở đây task tạo raw target mới. Nếu hệ thống dùng proxy interception ngoài object này, method có thể bypass advice **dù tác vụ chạy trong cùng JVM**.

Một hướng khác:

```java
// Ý tưởng: sharedService được cung cấp từ proxy provider.
executor.submit(() -> sharedService.process("async"));
```

Nếu `sharedService` trỏ tới proxy, lời gọi vẫn có thể đi qua proxy trong worker. Nhưng **ngữ cảnh gắn với ThreadLocal của caller không tự truyền sang worker**. Cần tách hai vấn đề:

1. **Proxy coverage:** task gọi qua proxy hay raw instance?
2. **Context propagation:** advice trên worker có thấy trace ID, authorization context hoặc deadline cần thiết không?

Có proxy mà thiếu context vẫn có thể làm advice áp dụng chính sách sai; có context đúng nhưng task gọi raw target thì advice proxy vẫn có thể không chạy.

## 17. Scope và lifetime của target/proxy

Một proxy provider có thể quản lý object theo những vòng đời khác nhau:

- **Một instance dùng chung** cho nhiều caller.
- **Một instance mỗi phiên yêu cầu**.
- **Một instance mỗi tác vụ/transaction**.
- **Một instance mỗi lần factory được gọi**.
- **Một instance riêng cho plugin/module layer**.

Với mỗi scope, phải xác định quan hệ:

```text
scope -> proxy instance -> target instance -> owned resources
```

Nếu target được tạo ngoài scope rồi cắm vào proxy, nó có thể giữ state từ một request khác. Nếu proxy được chia sẻ nhưng target mutable không thread-safe, interception không tự làm target thread-safe. Nếu target được dispose nhưng proxy vẫn giữ reference, những lời gọi sau có thể chạm resource đã đóng.

**Quản lý proxy và quản lý lifetime là hai trách nhiệm liên quan nhưng khác nhau.** Một registry cung cấp proxy cần có chính sách tạo/dọn target hợp lý, không chỉ wrap một lần rồi coi mọi object tồn tại mãi mãi.

### Proxy và constructor side effect

Việc wrap một raw target **không chạy ngược constructor** và không hoàn tác những tác dụng phụ đã xảy ra trong constructor. Nếu logic quan trọng chỉ nằm trong advice ở method execution, nó không tự bao phủ hành vi constructor trước thời điểm wrap.

Nếu ứng dụng cần can thiệp vào object construction, phải dùng loại join point/hook tương ứng mà engine hỗ trợ, hoặc thiết kế factory có bước khởi tạo kiểm soát được.

## 18. Interception của method không có nghĩa interception của toàn bộ object

Ngay cả một managed proxy cũng thường chỉ can thiệp vào **một tập method/join point**. Ví dụ:

- Interface proxy nhìn thấy các method trong những interface được proxy implement.
- Các method riêng của implementation không nằm trong interface có thể không được gọi qua interface proxy.
- Static call không được gửi tới một instance proxy.
- Private method không được gọi từ bên ngoài như một public proxy method.
- Direct field access không tự trở thành method invocation qua proxy.
- Constructor/initialization có thể diễn ra **trước** khi proxy được tạo.
- Method final có giới hạn nếu dùng subclass override interception.

Do vậy **“object được quản lý” không có nghĩa “mọi hoạt động bên trong object đều được advice”**. Thiết kế policy phải ghi rõ tập join point được yêu cầu, và cơ chế proxy có thực sự quan sát được chúng không.

### Call join point và execution join point

Một **method call** là vị trí phát lời gọi; một **method execution** là ranh giới thân method bắt đầu chạy. Proxy đứng ngoài target thường là **một ranh giới invocation cụ thể**, không nhất thiết đồng nghĩa công cụ có thể observe mọi call site hoặc execution body trong JVM.

Nếu một engine dệt **execution** vào bytecode target, việc caller tạo target mới ngoài factory chưa chắc làm advice biến mất: method body mới tạo vẫn có thể chứa code đã được weave nếu class tương ứng đã được instrument. Đây là lý do phải nói **giới hạn này thuộc proxy-based AOP**, không khái quát sai cho mọi AOP.

## 19. Proxy bypass và security: phân biệt yêu cầu bắt buộc với quan sát

Nếu concern chỉ là log thông tin không bắt buộc, đường bypass có thể làm mất dữ liệu quan sát. Nếu concern là **kiểm soát truy cập** hoặc **bất biến giao dịch**, đường bypass có thể nguy hiểm hơn.

Ví dụ một hệ thống dựa hoàn toàn vào proxy để kiểm tra quyền trước `transfer(...)`. Nếu caller được phép tạo raw `TransferService` rồi gọi thẳng, lớp kiểm tra có thể bị bỏ qua.

**Chính sách bảo vệ bắt buộc phải có boundary đáng tin cậy không bị bypass bởi các đường thao tác được hỗ trợ.** Có thể dùng AOP để thực thi chính sách khi coverage được bảo đảm và kiểm thử đầy đủ, nhưng không được coi “đã tạo proxy đâu đó” là bằng chứng tất cả các entry point an toàn.

Những biện pháp kiến trúc thường hữu ích:

- Không công bố raw implementation như một API được consumer tự do khởi tạo nếu policy yêu cầu interception.
- Đặt invariant quan trọng ở tầng/thao tác không thể bỏ qua trong phạm vi tin cậy.
- Thiết kế module/API làm giảm các lối gọi trực tiếp ngoài dự kiến.
- Kiểm thử các entry point từ batch, CLI, plugin, test, worker và callback.
- Định nghĩa ứng xử khi proxy provider/instrumentation không khả dụng.
- Không log toàn bộ argument nhạy cảm chỉ vì muốn kiểm tra advice có chạy.

**Đừng sử dụng giới hạn này như một biện pháp “tự cho phép bypass security” trong test/production.** Khi cần bypass để đo hoặc debug, phải hiểu rõ tác động và có quyền kiểm soát.

## 20. Thế nào là “bắt buộc dùng proxy” trong kiến trúc?

Không có câu lệnh Java chung biến mọi instance của một class thành proxy. Ta có thể đưa ra **hợp đồng ở API và cách wiring**:

1. Consumer chỉ phụ thuộc interface hoặc loại trừ khả năng truy cập constructor khi hợp lý.
2. Tất cả object chính thức được cấp qua một provider kiểm soát.
3. Provider trả reference đã được can thiệp, không tiết lộ raw target tùy tiện.
4. Các dependency khác truyền chính reference proxy cho consumer tiếp theo.
5. Factory phụ trợ không tự tạo bản raw “giống hệt” rồi sử dụng cho nghiệp vụ yêu cầu aspect.
6. Integration test phát hiện khi một đường gọi không đi qua interceptor.

Những giải pháp này **giảm rủi ro vô tình bypass**. Tuy vậy, không nhất thiết ngăn được một thành phần có đủ quyền tạo hoặc gọi implementation trực tiếp. Nếu yêu cầu là ranh giới bảo mật chống tác nhân không tin cậy, **kiến trúc type/proxy nội bộ không được thay cho kiểm soát quyền ở boundary bảo mật thật sự**.

### Hạn chế constructor visibility

Làm constructor package-private hoặc private có thể buộc caller sử dụng factory từ những vị trí Java thông thường. Nhưng:

- Không phải mọi API đều cho phép hạn chế constructor như vậy.
- Reflection và module access có quy tắc riêng; không dùng “private” như bảo đảm tuyệt đối chống mọi tác nhân có đặc quyền.
- Một số serializer hoặc runtime infrastructure cần cơ chế xây dựng object khác.
- Hạn chế construction không tự tạo advice; factory vẫn phải trả đúng reference proxy.

Nên dùng visibility để hỗ trợ hợp đồng API, không coi đó là tất cả bảo đảm coverage.

## 21. Kiểm tra một reference có phải proxy hay không

Đối với Java Dynamic Proxy, có thể kiểm tra:

```java
boolean dynamicProxy =
        java.lang.reflect.Proxy.isProxyClass(value.getClass());
```

Nhưng đây chỉ là kiểm tra **đối tượng có class được tạo bằng cơ chế Java Dynamic Proxy hay không**. Nó **không chứng minh**:

- Handler có áp dụng advice mong muốn.
- Pointcut có chọn method cụ thể.
- Proxy có wrap đúng target/version.
- Mọi lời gọi nghiệp vụ đều dùng object đó.
- Cơ chế subclass proxy hoặc bytecode weaving có được bao phủ hay không.
- Proxy có đáp ứng policy bảo mật hoặc audit hay không.

**Không kiểm tra AOP bằng `Proxy.isProxyClass` duy nhất.** Cách chắc hơn là **test hành vi advice trên đường gọi thật**, so sánh số lần target thực thi và số lần concern được ghi nhận.

### Unwrap để debug có thể tạo bypass mới

Một số provider cho phép lấy raw target để chẩn đoán. Chức năng đó có ích nhưng cần kiểm soát. Nếu caller lấy target rồi cất nó vào field, những lời gọi sau có thể bypass proxy ngay cả khi provider được cấu hình chính xác.

Khi debug, nên ưu tiên công cụ quan sát metadata/handler hay diagnostics mà **không công bố reference raw rộng rãi**.

## 22. Xử lý ngoại lệ và return value qua hai đường

Đối với cùng một target method, proxy có thể:

- Ghi log trước khi chuyển tiếp.
- Đo thời gian.
- Biến đổi argument ở ranh giới proceed nếu API hỗ trợ.
- Quan sát kết quả và exception.
- Trả fallback hoặc retry nếu around advice cho phép.

Raw target gọi trực tiếp sẽ **không có những lớp biến đổi đó**.

Do đó, bypass không chỉ là thiếu log. Nó có thể khiến **giá trị caller nhận khác**, **lỗi được truyền khác** hoặc **target chạy số lần khác**.

Ví dụ, nếu proxy áp dụng retry 2 lần còn raw method không retry, cùng một hành vi thất bại tạm thời có thể tạo outcome khác. Nhưng phải nhớ retry có side effect và yêu cầu idempotency. Vì thế:

**Đừng “sửa” tình trạng thiếu proxy bằng cách thêm call advice thủ công vào target một cách không nhất quán.** Hoặc thiết kế một abstraction có contract rõ, hoặc chuyển sang cơ chế weaving/instrumentation khác khi thực sự cần coverage execution rộng hơn.

## 23. Transaction, resource scope và đối tượng unmanaged

Một số concern AOP dùng scope quanh một invocation, ví dụ mở một đơn vị công việc, tạo context, hoặc dọn tài nguyên. Nếu target unmanaged được gọi trực tiếp:

- Before advice không chạy để tạo scope.
- Around advice không bao quanh method.
- After/finally advice không chạy để cleanup.
- Caller có thể nhận behavior khác, kể cả resource thiếu được chuẩn bị.

Tuy vậy, **transaction không tự tồn tại chỉ nhờ proxy**. Các API/resource thật phải có contract và quản lý độc lập. Một phương thức tự quản lý tài nguyên đúng vẫn có thể chạy hợp lệ không có proxy nếu nó được thiết kế như vậy.

Quan trọng là **tránh kỳ vọng ẩn** rằng mọi execution sẽ đi qua một proxy, trong khi hệ thống cho phép tạo raw instance ngoài cơ chế quản lý.

## 24. Test thường che khuất lỗi unmanaged instance thế nào?

Một unit test có thể viết:

```java
// Đoạn test ý tưởng.
Operations service = new Service("test");
assertEquals("OK:A", service.process("A"));
```

Test này xác nhận **business logic của raw target**, nhưng **không xác nhận proxy advice**. Nếu concern cần audit, một integration test riêng phải tạo service qua provider chính thức rồi assert sự kiện audit.

Ngược lại, nếu test chỉ dùng mock/proxy mà không kiểm tra target behavior, có thể bỏ qua bug trong implementation.

### Ba lớp kiểm thử

**Unit test raw target:** kiểm logic method độc lập, chủ động không xem đây là bằng chứng advice hoạt động.

**Integration test proxy:** tạo service qua đúng provider/entry point, assert số lần interceptor chạy, target chạy, kết quả/ngoại lệ đúng.

**Architecture/coverage test:** xác minh những consumer quan trọng **không tạo raw target ngoài composition root**, factory không rò target, plugin/batch/async có đường đi phù hợp. Có thể kết hợp code review, rule kiểm tra cấu trúc và test runtime.

### Ma trận đường gọi

| Đường gọi | Proxy wrapper có thể thấy? | Điều cần test |
| --- | --- | --- |
| Consumer gọi reference provider trả về | Có, nếu provider trả đúng proxy | Advice và target chạy theo contract |
| Consumer tự `new` target | Không, nếu không có hook khác | Nhận diện bypass |
| Consumer gọi raw lấy từ getter | Không | Kiểm tra leak reference |
| Consumer gọi proxy cho một raw đã tồn tại | Có trên **đường proxy** | Raw reference cũ vẫn bypass |
| Target tự gọi `this.inner()` | Thường bypass với wrapper proxy | Method inner có cần coverage không? |
| Callback giữ proxy | Có trên lời gọi callback dùng proxy | Context propagation và lifecycle |
| Callback tự tạo raw | Không | Wiring ở task factory |
| Reflection gọi method trên proxy | Có nếu method/cách invoke phù hợp | Tách receiver proxy và target |
| Reflection gọi method trên raw | Không | Không nhầm reflection với AOP |
| Plugin tạo riêng target | Có thể không | Boundary giữa host và plugin |
| Bytecode execution đã weave | Có thể, kể cả raw instance | Phân biệt với cơ chế proxy |

Bảng cố ý dùng “có thể” ở những trường hợp phụ thuộc engine. Muốn có kết luận sản xuất, phải chạy test với implementation thực tế.

## 25. Chẩn đoán khi advice không chạy

Hãy kiểm tra theo thứ tự để tránh sửa nhầm:

### Bước 1 — Có đúng instance không?

Caller đang giữ reference nào? `System.identityHashCode` có thể hỗ trợ theo dõi identity trong debug, nhưng không biến nó thành ID ổn định hay bằng chứng bảo mật. Có thể in tên runtime class hoặc kiểm tra wrapper ở đường dẫn cụ thể khi cần.

### Bước 2 — Reference ấy có qua proxy không?

Kiểm tra cách object được tạo: `new`, static factory, registry, reflection, deserialization hay callback? Có code nào lấy raw target từ một nơi khác không?

### Bước 3 — Provider có thật sự wrap không?

Một config có thể chỉ tạo raw khi thiếu điều kiện. Đừng suy đoán factory tên `createManaged` luôn trả proxy; xem contract và runtime test.

### Bước 4 — Method có nằm trong tập interceptor hỗ trợ không?

Interface proxy chỉ nhìn thấy method của interface được proxied. Với subclass proxy, final/private/static có ràng buộc khác. Không tự áp dụng một kết luận cho mọi cơ chế.

### Bước 5 — Pointcut có match không?

Kiểu, tên method, chữ ký, overload, annotation ở interface/implementation, context và dynamic argument đều có thể ảnh hưởng.

### Bước 6 — Có self-invocation hay direct-call nào không?

Từ client có đi qua proxy, nhưng bên trong target có `this.otherMethod()` hoặc lấy một raw reference khác không?

### Bước 7 — Advice có chạy nhưng không tạo log không?

Một guard trong advice, cờ cấu hình, sampling hoặc sink lỗi có thể khiến không thấy stdout. Phân biệt **advice entered**, **predicate true** và **event exported**.

### Bước 8 — Có nhiều proxy hoặc nhiều target không?

Tham chiếu giữa cache, singleton/per-request instance, class loader/plugin generation có thể trộn object cũ và mới. Kiểm tra ownership/lifecycle, không chỉ method name.

### Bước 9 — Có async hoặc module boundary không?

Worker có dùng đúng proxy không? Trace context có được truyền không? Caller/target ở các module/process khác nhau có chung interception boundary không?

### Bước 10 — Có weaving bytecode song song không?

Nếu execution đã được weave, lời gọi raw có thể vẫn chạy advice ở method body. Chẩn đoán dựa trên **cơ chế thực tế** thay vì gán mọi thiếu log cho proxy.

## 26. Chọn giải pháp phù hợp với mục tiêu coverage

**Giải pháp 1 — Tập trung tạo và phân phối proxy.** Phù hợp khi mọi consumer thuộc phạm vi tin cậy và có thể kiểm soát wiring. Thường đơn giản, rõ ràng, ít phụ thuộc vào bytecode manipulation.

**Giải pháp 2 — Thiết kế boundary API không bị consumer bỏ qua.** Cung cấp facade/service interface qua module public, giữ implementation internals không phải API public. Hữu ích để quản lý object lifecycle và giảm leak reference.

**Giải pháp 3 — Dùng hook/interceptor ngay tại boundary bắt buộc.** Ví dụ một pipeline xử lý lệnh mà mọi tác vụ đều phải đi qua. Phù hợp khi có một điểm kiểm soát thực sự.

**Giải pháp 4 — Dệt execution bytecode khi phạm vi yêu cầu rộng hơn proxy.** Có thể phù hợp nếu cần quan sát các instance do nhiều bên tạo, self-invocation hoặc private execution (khi công cụ hỗ trợ). Đòi hỏi kiểm chứng weaving scope, class loading, module access và artifact deployment.

Không có phương án nào đúng cho mọi trường hợp. **Chọn theo join point cần bao phủ**, giới hạn ngôn ngữ/runtime và yêu cầu audit/bảo mật thực tế.

## 27. So sánh proxy với bytecode weaving khi object unmanaged

| Câu hỏi | Proxy bên ngoài target | Dệt bytecode vào execution của target |
| --- | --- | --- |
| Caller tự `new Target()` rồi gọi method | Thường bypass | Có thể vẫn chạy advice nếu class được weave và pointcut khớp |
| Caller dùng provider trả proxy | Có thể chạy advice | Có thể chạy advice ở execution, ngoài interceptor nếu cả hai cùng có |
| `this.inner()` trong target | Wrapper proxy thường không quan sát | Có thể áp dụng ở inner execution nếu hỗ trợ |
| Target class có cần biến đổi bytecode không? | Không nhất thiết | Có |
| Coverage có dựa vào reference caller dùng không? | Rất nhiều | Có thể ít hơn đối với execution đã weave |
| Có hỗ trợ mọi private/static/final method không? | Không | Phụ thuộc loại join point và weaver |
| Có cần quản lý class loading/artifact không? | Chủ yếu là quản lý proxy/lifetime | Có, tùy compile/load/post-compile weaving |
| Có tự mang ThreadLocal qua async không? | Không | Cũng không |
| Có tự bảo đảm mọi đường chính sách bắt buộc không? | Không | Cũng không nếu weaving scope thiếu |

**Không đồng nhất “weave trong class” với “mọi thứ đều được chặn”.** Bytecode weaving vẫn cần pointcut đúng, class thuộc scope, helper nạp được và runtime dùng bản đã weave.

## 28. Các lỗi phổ biến

1. **“Method được gắn annotation thì mọi `new` đều có advice.”** Annotation không tự tạo proxy.
2. **“Đã tạo một proxy thì raw target cũng biến thành proxy.”** Proxy là reference/object khác.
3. **“Factory luôn trả proxy.”** Chỉ khi factory thực sự wrap.
4. **“Đăng ký object vào một map là nó được quản lý AOP.”** Registry có thể giữ raw instance.
5. **“Thay mọi `new` bằng static factory là đủ.”** Factory không wrap vẫn bypass.
6. **“Caller có interface là tự có proxy.”** Interface chỉ là contract; object runtime có thể là raw.
7. **“`Proxy.isProxyClass` chứng minh advice chạy.”** Nó không xác nhận pointcut/handler behavior.
8. **“Một lời gọi ngoài đã qua proxy thì mọi lời gọi nội bộ cũng qua proxy.”** Wrapper proxy không tự nhận self-invocation.
9. **“Không có log nghĩa là instance unmanaged.”** Có thể pointcut không khớp hoặc sink bị tắt.
10. **“Reflection luôn bypass AOP.”** Method được invoke trên receiver proxy có thể qua handler.
11. **“Serialization luôn giữ proxy.”** Phụ thuộc cơ chế và contract; không suy luận.
12. **“Proxy quanh instance làm các raw reference cũ không còn hợp lệ.”** Chúng vẫn có thể gọi trực tiếp.
13. **“Một proxy chung làm target mutable thread-safe.”** Không tự có bảo đảm đồng bộ.
14. **“Proxy bao mọi phương thức của object.”** Tập method phụ thuộc interface/subclass/hook.
15. **“Subclass proxy có cùng self-invocation semantics với interface wrapper proxy.”** Còn phụ thuộc implementation.
16. **“Dệt bytecode cũng phải quản lý từng instance proxy.”** Execution weaving có thể áp dụng theo class bytes.
17. **“Test business logic raw đủ chứng minh audit.”** Cần integration/coverage test.
18. **“Có advice là security invariant tự an toàn.”** Phải bảo đảm mọi đường bắt buộc được kiểm soát.
19. **“Raw instance trong task tự nhận context của caller.”** Cả proxy reference lẫn context cần được xem riêng.
20. **“Một module chứa aspect là mọi module đều nhận advice.”** Đường gọi và weaving scope mới quyết định.

## 29. Bài tập thực hành

1. Chạy `UnmanagedProxyDemo.java`. Đếm số lần in `advice.before` và `target[...].process`; giải thích vì sao không bằng nhau.
2. Xóa dòng `ProxyFactory.wrap(raw)` nhưng giữ các lời gọi đến `raw`; đầu ra nào mất và vì sao?
3. Đổi `managed.outer("C")` thành `raw.outer("C")`. So sánh advice trên `outer` và `process` bên trong.
4. Tạo một factory `createRaw` trả `new Service(...)`, sau đó gọi qua factory đó. Vì sao gọi “factory” không bảo đảm proxy?
5. Đổi `ProxyFactory.create(...)` để trả raw reference thay vì proxy. Viết test phát hiện thay đổi contract.
6. Tạo một `Consumer` nhận `Operations` qua constructor. Truyền proxy trong một test và raw trong test khác; so sánh log.
7. Tạo hai proxy nối tiếp trên cùng raw target. So sánh thứ tự before/after của hai handler và số lần target được gọi.
8. Thử ép interface proxy về concrete `Service`. Vì sao `ClassCastException` có thể xảy ra dù service implements `Operations`?
9. Bổ sung một method chỉ có trên implementation, không có trên interface. Java dynamic proxy có cho gọi method ấy qua `Operations` không?
10. Thêm một `ExecutorService`, gửi hai task: task thứ nhất giữ proxy, task thứ hai tự `new` raw. So sánh số lần advice và giải thích vấn đề ThreadLocal riêng biệt.
11. Dùng Reflection gọi method interface trên `proxy` rồi trên `raw`. Receiver khác nhau có ảnh hưởng interception ra sao?
12. Thêm method `outer` gọi `this.process("fail")`, rồi so sánh dòng `advice.error` cho `outer` và `process`.
13. Thiết kế một composition root chỉ trả proxy ra ngoài. Nêu mọi API có thể vô tình làm rò raw target.
14. Tạo một class test `AuditRecorder` đếm advice. Kiểm các đường new, factory, reflection, wrapping, self-invocation, callback.
15. So sánh bằng sơ đồ: proxy interface vs execution bytecode weaving cho trường hợp khách hàng tự tạo instance target.
16. Đề xuất cách bảo vệ một invariant bắt buộc khi ứng dụng có entry point qua CLI, HTTP handler, background job và plugin.
17. Phân loại ba tình huống “advice không chạy” thành **unmanaged instance**, **pointcut mismatch**, **advice đã chạy nhưng logging bị tắt**; đề xuất bằng chứng runtime cho từng loại.

**Tự kiểm tra:** Vì sao `raw == proxy` thường false? Nếu factory wrap một target đã được tạo trước đó, các reference raw cũ có tự đi qua proxy không? Lời gọi `this.inner()` có quay ra wrapper proxy không? Annotation trên method có tự đăng ký instance vào provider không? Với bytecode execution weaving, tạo đối tượng bằng `new` có luôn khiến advice biến mất không? Một unit test trực tiếp target có chứng minh concern audit được áp dụng không?

## Kết luận

Trong **AOP qua proxy**, đối tượng được tạo ngoài cơ chế quản lý proxy có thể **không nhận advice**, không phải vì điểm can thiệp sai, mà vì **caller đang giữ và gọi một raw target reference**. Việc tạo proxy sau đó không tự chuyển đổi những reference đã phát tán; self-invocation trong target cũng có thể bypass wrapper. Để áp dụng AOP nhất quán, cần quản lý **composition root, factory, registry, object lifetime và phân phối reference**, kiểm tra cả đường `new`, reflection, plugin, callback, serialization và lời gọi nội bộ, rồi test trên cơ chế interception thật. Khi yêu cầu vượt ngoài phạm vi proxy, có thể cân nhắc bytecode weaving hoặc boundary khác được hỗ trợ, nhưng vẫn phải xác minh **join point, phạm vi dệt và đường thực thi** thay vì mặc định mọi object trong hệ thống đã được bảo vệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-007">Chuỗi interceptor và cơ chế gọi tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi interceptor và cơ chế gọi tiếp trong AOP qua proxy

Khi dùng **proxy** để triển khai một phần hành vi lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), lời gọi của người dùng không đi thẳng đến đối tượng nghiệp vụ mà trước tiên đi qua một **đối tượng đại diện**. Đại diện có thể thực thi nhiều **interceptor** theo thứ tự để ghi nhận thời gian, kiểm tra quyền, giới hạn tần suất, chuyển đổi kết quả hoặc quản lý một số bối cảnh. Nhiều interceptor xếp nối nhau tạo thành **chuỗi interceptor (interceptor chain)**.

Cốt lõi của chuỗi không chỉ là gọi lần lượt các hàm. **Mỗi interceptor quyết định có chuyển tiếp lời gọi tới phần còn lại của chuỗi không, khi nào chuyển tiếp, chuyển tiếp mấy lần và làm gì với kết quả hoặc ngoại lệ.** Thao tác này thường được gọi là **`proceed()`**, **`next()`**, hoặc “invoke next” tùy công cụ. Khi điểm cuối của chuỗi đã tới, cơ chế sẽ chuyển lời gọi tới **phương thức đích**.

Để hiểu và áp dụng đúng, phải phân biệt:

- **Proxy:** nơi nhận cuộc gọi ở phía ngoài, điều phối tới interceptor hoặc target.
- **Interceptor:** một lớp chức năng có thể can thiệp quanh một lượt gọi.
- **Invocation context:** thông tin của lượt gọi, gồm phương thức, đối số, đích và trạng thái của chuỗi nếu cơ chế cung cấp.
- **Proceed/next:** bước tiếp tục tới interceptor kế tiếp hoặc đích, **không nhất thiết** là gọi thẳng phương thức nghiệp vụ.
- **Join point/pointcut:** mô hình và điều kiện chọn điểm cần can thiệp; proxy chỉ hỗ trợ một tập điểm nhất định.
- **Target:** đối tượng nghiệp vụ thực sự được gọi ở điểm cuối khi chuỗi cho phép tiếp tục.

Bài trình bày kiến thức AOP cơ bản và nâng cao **nói chung**. Không có chuẩn thống nhất cho mọi chữ ký interceptor, cách truyền đối số hoặc cho phép gọi `proceed` nhiều lần. Ví dụ dùng Java 17 và JDK dynamic proxy để tự xây một mô hình nhỏ, giúp quan sát chính xác nguyên lý mà không giả định đó là thuật toán của một framework cụ thể.

## 1. Kết quả học tập

Sau bài, bạn có thể:

1. Giải thích vì sao chuỗi interceptor là một **cấu trúc lồng nhau** chứ không chỉ là danh sách callback được chạy từ trên xuống.
2. Đọc đường đi **proxy → interceptor A → interceptor B → target**, rồi đường quay về **target → B → A → caller**.
3. Mô tả vai trò của **invocation context**: method, arguments, target, cursor và thao tác chuyển tiếp.
4. Phân biệt `proceed` một lần, không lần nào hoặc nhiều lần và tác động tới hành vi chính.
5. Phân tích việc interceptor bên ngoài/bên trong nhìn thấy kết quả và ngoại lệ ở **ranh giới riêng**.
6. Đánh giá thứ tự đặt authorization, caching, retry, transaction, timing và logging.
7. Nhận diện vấn đề **self-invocation**, gọi trực tiếp target, phương thức ngoài proxy, recursion, proxy lồng nhau và phương thức có overload.
8. Viết và chạy chương trình Java 17 có interceptor chain hoàn chỉnh, gồm nhánh thành công, retry, lỗi và bypass.
9. Kiểm thử sự bảo toàn của kết quả, ngoại lệ, số lần gọi và phạm vi can thiệp.

## 2. Chuỗi interceptor là gì?

Một phép gọi có thể được mô hình hóa:

```text
Client
  |
  v
Proxy
  |
  +--> Interceptor A: before
         |
         +--> proceed()
                |
                +--> Interceptor B: before
                       |
                       +--> proceed()
                              |
                              +--> Target.method()
                              |
                       <------+-- result / error
                       |
                  Interceptor B: after / catch / finally
                |
         <------+-- result / error
         |
     Interceptor A: after / catch / finally
  |
  v
Client receives result / error
```

Thứ tự đi xuống là **A rồi B rồi target**. Khi phần bên trong trả về, thứ tự đi lên thường là **B rồi A**. Nếu đích ném ngoại lệ, các interceptor có thể quan sát nó trên đường đi ngược và thực hiện `catch`/`finally` theo cấu trúc của từng lớp.

Đây là lý do thường có thể nói:

```text
A(B(Target))
```

thay vì nghĩ `A; B; Target; A; B` như các hàm hoàn toàn tách rời. Một interceptor có thể không chuyển tiếp, hoặc có thể chuyển tiếp nhiều lần; khi đó cấu trúc dòng điều khiển thay đổi.

### 2.1. Interceptor khác advice ở đâu?

**Advice** là khái niệm AOP chỉ hành vi bổ sung ở join point. **Interceptor** thường là một cấu trúc lập trình/runtime để triển khai hành vi đó, nhất là với method interception. Chúng có thể tương ứng gần nhau nhưng **không phải hai từ đồng nghĩa tuyệt đối** trong mọi hệ:

- Một advice có thể được weave trực tiếp vào bytecode, không dùng proxy.
- Một interceptor có thể nằm trong chain xử lý yêu cầu hay sự kiện mà không mang đầy đủ mô hình pointcut AOP.
- Một interceptor kiểu around có thể tổ chức before, after-returning, after-throwing và finally trong cùng một cấu trúc.
- Một hệ AOP có thể có nhiều loại join point hơn method call qua proxy, tùy công nghệ.

Trong phạm vi bài này, interceptor chain được xem là **một cách hiện thực AOP qua proxy**.

### 2.2. Không phải mọi lời gọi đều đi qua chuỗi

Một đối tượng `Service` có thể được giữ đồng thời dưới hai tham chiếu:

```text
original  -> Service
proxied   -> Proxy -> Interceptors -> Service
```

Nếu client gọi `original.execute()`, chuỗi proxy không được kích hoạt. Nếu gọi `proxied.execute()`, lời gọi đi qua chuỗi tương ứng, với điều kiện phương thức được cơ chế hỗ trợ và bộ lọc cho phép.

Một **pointcut có vẻ đúng** chưa đủ bảo đảm advice chạy. Cần kiểm tra nơi cung cấp tham chiếu đối tượng, loại phương thức và đường thực thi cụ thể.

## 3. Thành phần của một invocation context

Một đối tượng ngữ cảnh lời gọi có thể mang các trường sau, tùy thiết kế:

| Dữ liệu | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| **Method/signature** | Phương thức đang được chặn, danh sách kiểu tham số | Khác kiểu runtime của đối số |
| **Arguments** | Giá trị truyền vào lần gọi | Có thể chứa null hoặc wrapper do boxing |
| **Target** | Đối tượng nghiệp vụ nhận thao tác | Không nhất thiết trùng với proxy |
| **Proxy/this context** | Đại diện hoặc ngữ cảnh bên ngoài | Ngữ nghĩa `this`/`target` phụ thuộc mô hình |
| **Interceptor list** | Những bộ chặn được áp theo thứ tự | Thứ tự quyết định khả năng quan sát |
| **Current position** | Vị trí tiếp tục trong chuỗi | Tránh quay về interceptor đang chạy ngoài ý định |
| **Proceed/next** | Tiếp tục tới phần còn lại của chuỗi | Có thể gọi target khi không còn interceptor |
| **Invocation metadata** | Tên thao tác, kiểu đích, dữ liệu phụ trợ | Không tự chứng minh dữ liệu tin cậy |
| **Context attributes** | Trạng thái theo một lượt gọi nếu có hỗ trợ | Cần an toàn khi lồng nhau/đồng thời |

Không nhất thiết phải có tất cả các trường ở một API. Một số cơ chế ẩn cursor trong closure, một số cung cấp đối tượng `MethodInvocation`, số khác dùng hàm `next` nhận đối số.

### 3.1. Chữ ký phương thức và đối số runtime khác nhau

Ví dụ một interface có:

```java
void send(Object value);
void send(String value);
```

Lời gọi `send((Object) "A")` chọn overload khai báo `send(Object)` dù đối số thực tế là `String`. Một interceptor lọc theo `args[0] instanceof String` có thể nhận kết quả khác với bộ lọc chọn chữ ký `send(String)`.

Khi interception nhằm chính sách khác nhau cho từng overload, phải kiểm tra **kiểu tham số khai báo, số lượng và thứ tự**; không suy từ giá trị runtime.

### 3.2. Primitive, boxing và null

Trong JDK dynamic proxy, một phương thức nhận `int count` vẫn có chữ ký `int.class`, nhưng khi truyền qua `Object[] args`, giá trị primitive được biểu diễn bằng `Integer`. Không nên cast một `Long` sang `Integer` chỉ vì đều là số.

Nếu phương thức không tham số, `args` có thể là `null` theo hợp đồng của `InvocationHandler`. Nếu có một tham số tham chiếu mang null, `args` có thể là mảng một phần tử `[null]`. Đây là hai trường hợp khác nhau.

Một interceptor chỉ quan sát không nên unbox `Integer null` thành `int`, vì điều đó gây `NullPointerException` do interceptor tạo ra.

## 4. Cơ chế `proceed()`: đi tiếp một tầng

Xét chuỗi có ba interceptor A, B, C:

```text
A -> B -> C -> Target
```

Tại A, khi gọi `proceed()`, hành động đúng về mặt khái niệm là **gọi phần còn lại từ B**. Tại B, gọi tiếp từ C. Tại C, gọi tiếp vào Target.

Giả mã mô hình:

```text
invoke(position, invocation):
    if position == interceptorCount:
        return targetCall(invocation.arguments)
    interceptor = interceptors[position]
    return interceptor.around(
        invocation.withNext(() -> invoke(position + 1, invocation))
    )
```

Đây là **giả mã**, không phải API Java/AOP thống nhất. Điểm cốt lõi là callback “next” **đã biết vị trí kế tiếp** để không tự đi vòng về chính interceptor hiện tại.

### 4.1. Trả về phải đi ngược toàn chuỗi

Nếu target trả `"OK"`, B có thể ghi log rồi trả lại `"OK"`; A nhận kết quả của **B(Target)**, không phải nhất thiết đọc trực tiếp giá trị từ target.

Nếu B sửa kết quả thành `"CACHED"`, A sẽ nhận `"CACHED"`. Vì vậy interceptor ở ngoài nhìn thấy **kết quả của chuỗi ở bên trong**, có thể khác kết quả gốc.

### 4.2. Ngoại lệ cũng truyền theo chiều ngược

Nếu target ném `IllegalArgumentException`, C, B và A có thể bắt hoặc truyền nó. Nhưng nếu B bắt lỗi rồi trả fallback, A có thể quan sát **đường thành công**. Nếu B tự ném lỗi từ logging, A có thể thấy lỗi ấy thay vì lỗi của target.

Không thể kết luận mọi interceptor “sẽ thấy ngoại lệ từ target” khi có interceptor bên trong chuyển đổi đường điều khiển.

### 4.3. Thứ tự `finally`

Một interceptor dạng:

```java
Object around(Invocation invocation) throws Throwable {
    acquireContext();
    try {
        return invocation.proceed();
    } finally {
        releaseContext();
    }
}
```

Đây là **đoạn minh họa**, trong đó `Invocation`, `acquireContext`, `releaseContext` thuộc mô hình giả định. Cleanup chạy khi phạm vi `try` kết thúc bình thường hoặc do ngoại lệ. Nếu `acquireContext()` ném lỗi **trước khi vào `try`**, khối finally đó chưa bảo vệ được đường lỗi ấy. Cần xác định tài nguyên nào đã chiếm giữ và phải trả.

## 5. Không gọi tiếp, gọi một lần hoặc nhiều lần

Một interceptor kiểu around có thể lựa chọn:

| Số lần chuyển tiếp | Hành vi thường gặp | Rủi ro hoặc yêu cầu |
| --- | --- | --- |
| **0** | Từ chối quyền, cache hit, trả fallback | Target không chạy; phải được phép theo hợp đồng |
| **1** | Logging, timing, tracing thông thường | Chuyển tiếp kết quả/ngoại lệ đúng |
| **Nhiều lần** | Retry có điều kiện, thử nhiều chiến lược | Có thể nhân đôi tác dụng phụ và advice bên trong |

Nếu mục tiêu chỉ là ghi log kỹ thuật, **một lần `proceed`** thường là lựa chọn an toàn. Gọi tiếp nhiều lần không đơn thuần “thực thi log nhiều lần”: nó có thể **gọi lại toàn bộ nghiệp vụ**.

### 5.1. Điểm khó: cursor thay đổi hay continuation có thể tái sử dụng?

Có hai kiểu tổ chức chuỗi đáng phân biệt:

- **Cursor có trạng thái tăng dần:** cùng một đối tượng context giữ chỉ số đang tăng. Gọi `proceed` lần hai trên context đó có thể đi tới vị trí khác, bỏ qua interceptor hoặc bị từ chối; điều này phụ thuộc triển khai.
- **Continuation cố định tới phần đuôi:** mỗi interceptor nhận một context biểu diễn đúng “phần chuỗi còn lại”. Gọi `proceed` nhiều lần có thể chạy lại phần đuôi từ cùng vị trí, nếu API cho phép.

Không được coi một ví dụ tự cài `proceed` nhiều lần là chứng minh mọi công cụ AOP có cùng tính chất. Khi làm retry thật, phải kiểm tra **hợp đồng của API tiến hành** và những side effect liên quan.

### 5.2. Retry có thể lặp interceptor bên trong

```text
Audit(Retry(Metrics(Target)))
```

Nếu `Retry` thử hai lần và mỗi lần `proceed` đi lại qua phần đuôi:

- **Audit** bao ngoài thường được kích hoạt một lần cho logical request.
- **Metrics** nằm bên trong có thể chạy hai lần, mỗi lần đo một attempt.
- **Target** có thể được gọi hai lần.

Nếu đổi thành `Retry(Audit(Metrics(Target)))`, audit cũng có thể chạy theo từng attempt. Đây là ý nghĩa của **thứ tự lồng nhau**; không có thứ tự mặc định chung cho mọi công nghệ.

## 6. Ví dụ Java 17: xây interceptor chain hoàn chỉnh

Ví dụ dưới đây cài một **mô hình chuỗi interceptor trên JDK dynamic proxy**, gồm:

- Một proxy giao diện `WorkPort` và target `WorkService`;
- Một invocation context giữ method, arguments, danh sách interceptor, vị trí kế tiếp và terminal call;
- Hai bộ `Trace` ở ngoài và trong;
- Một bộ `Retry` ở giữa, cho phép thử lại khi nhận lỗi tạm thời được định nghĩa;
- Đường thành công, một trường hợp lỗi lần đầu rồi thành công, một lỗi không retry và lời gọi bypass.

**Đây là chương trình Java 17 độc lập, không dùng thư viện AOP ngoài JDK.** Nó dạy cấu trúc chuỗi và `proceed`, không phải bộ weaving có thể chặn mọi điểm thực thi.

Lưu toàn bộ vào `InterceptorChainDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InterceptorChainDemo {
    interface WorkPort {
        String execute(String id);
        String inspect(String id);
    }

    static final class WorkService implements WorkPort {
        private final Map<String, Integer> attempts = new HashMap<>();

        @Override
        public String execute(String id) {
            int n = attempts.merge(id, 1, Integer::sum);

            if (id.equals("again") && n == 1) {
                throw new IllegalStateException("temporary");
            }
            if (id.equals("bad")) {
                throw new IllegalArgumentException("invalid");
            }
            return "OK:" + id + "#" + n;
        }

        @Override
        public String inspect(String id) {
            return "LOOKUP:" + id;
        }
    }

    @FunctionalInterface
    interface Terminal {
        Object call() throws Throwable;
    }

    @FunctionalInterface
    interface Interceptor {
        Object intercept(Invocation invocation) throws Throwable;
    }

    static final class Invocation {
        final Method method;
        final Object[] arguments;
        final List<Interceptor> chain;
        final int nextIndex;
        final Terminal terminal;

        Invocation(Method method, Object[] arguments,
                   List<Interceptor> chain, int nextIndex,
                   Terminal terminal) {
            this.method = method;
            this.arguments = arguments;
            this.chain = chain;
            this.nextIndex = nextIndex;
            this.terminal = terminal;
        }

        Object proceed() throws Throwable {
            if (nextIndex == chain.size()) {
                return terminal.call();
            }
            Interceptor next = chain.get(nextIndex);
            Invocation remainder = new Invocation(
                    method, arguments, chain, nextIndex + 1, terminal);
            return next.intercept(remainder);
        }
    }

    static final class Trace implements Interceptor {
        private final String label;

        Trace(String label) {
            this.label = label;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            System.out.println("[" + label + "] before execute");
            try {
                Object result = invocation.proceed();
                System.out.println("[" + label + "] return " + result);
                return result;
            } catch (Throwable error) {
                System.out.println("[" + label + "] throw "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println("[" + label + "] finally");
            }
        }
    }

    static final class Retry implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            for (int attempt = 1; attempt <= 2; attempt++) {
                System.out.println("[RETRY] attempt " + attempt);
                try {
                    return invocation.proceed();
                } catch (IllegalStateException error) {
                    if (attempt == 2) {
                        throw error;
                    }
                    System.out.println("[RETRY] retry");
                }
            }
            throw new AssertionError("unreachable");
        }
    }

    static Object callTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static WorkPort wrap(WorkPort target, List<Interceptor> chain) {
        return (WorkPort) Proxy.newProxyInstance(
                WorkPort.class.getClassLoader(),
                new Class<?>[] { WorkPort.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("execute")) {
                        return callTarget(method, target, args);
                    }
                    Object[] values = args == null
                            ? new Object[0] : args.clone();
                    Terminal terminal = () ->
                            callTarget(method, target, values);
                    Invocation first = new Invocation(
                            method, values, chain, 0, terminal);
                    return first.proceed();
                });
    }

    public static void main(String[] args) {
        WorkService service = new WorkService();
        List<Interceptor> chain = List.of(
                new Trace("OUTER"), new Retry(), new Trace("INNER"));
        WorkPort proxy = wrap(service, chain);

        System.out.println("READY");
        System.out.println("RESULT " + proxy.execute("ok"));
        System.out.println("RESULT " + proxy.execute("again"));

        try {
            proxy.execute("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("ERROR " + error.getMessage());
        }

        System.out.println("LOOKUP " + proxy.inspect("x"));
        System.out.println("DIRECT " + service.execute("direct"));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac -encoding UTF-8 InterceptorChainDemo.java
java InterceptorChainDemo
```

Stdout dự kiến chính xác:

```text
READY
[OUTER] before execute
[RETRY] attempt 1
[INNER] before execute
[INNER] return OK:ok#1
[INNER] finally
[OUTER] return OK:ok#1
[OUTER] finally
RESULT OK:ok#1
[OUTER] before execute
[RETRY] attempt 1
[INNER] before execute
[INNER] throw IllegalStateException
[INNER] finally
[RETRY] retry
[RETRY] attempt 2
[INNER] before execute
[INNER] return OK:again#2
[INNER] finally
[OUTER] return OK:again#2
[OUTER] finally
RESULT OK:again#2
[OUTER] before execute
[RETRY] attempt 1
[INNER] before execute
[INNER] throw IllegalArgumentException
[INNER] finally
[OUTER] throw IllegalArgumentException
[OUTER] finally
ERROR invalid
LOOKUP LOOKUP:x
DIRECT OK:direct#1
```

### 6.1. Lắp chuỗi và bắt đầu lời gọi là hai mốc khác nhau

`List.of(new Trace(...), new Retry(), ...)` tạo danh sách interceptor; `wrap(service, chain)` tạo proxy. Tại thời điểm đó **không có advice nào được kích hoạt**. `READY` xuất hiện trước những dòng `[OUTER]` và `[INNER]`.

Khi `proxy.execute("ok")` xảy ra, handler của proxy mới tạo `Invocation` và gọi `first.proceed()`. Đây là bước bắt đầu duyệt chuỗi.

### 6.2. Vì sao OUTER và INNER chạy theo thứ tự đảo chiều khi trả về?

Vì danh sách được lắp là `OUTER -> RETRY -> INNER -> target`. OUTER gọi tiếp sang RETRY; RETRY gọi sang INNER; INNER gọi target. Sau khi target trả kết quả, INNER xử lý `return/finally` trước rồi OUTER nhận kết quả.

Dòng `[OUTER] return OK:ok#1` không đến ngay sau `[OUTER] before`, vì OUTER phải đợi **toàn bộ phần chuỗi bên trong**.

### 6.3. Vì sao INNER chạy hai lần nhưng OUTER chỉ một lần ở `again`?

`Retry` nhận một context với `nextIndex` trỏ tới INNER. Context là đối tượng giữ **vị trí kế tiếp cố định** trong chương trình mẫu. Mỗi lần Retry gọi `invocation.proceed()`, một `Invocation remainder` mới được tạo bắt đầu tại INNER. Do đó hai attempt đi qua INNER và target hai lần.

OUTER đã chạy `proceed()` **một lần** để vào Retry; nó chỉ nhận kết quả sau khi Retry hoàn thành. Vì vậy OUTER ghi một `before` và một `return/finally` cho logical request `again`.

Mô hình này cố ý cho phép gọi tiếp phần đuôi nhiều lần. Một thư viện AOP khác có thể quản lý cursor/continuation theo luật khác; không được suy ra từ Java mẫu rằng API cụ thể chắc chắn hỗ trợ retry bằng `proceed` tương tự.

### 6.4. Nhánh lỗi `bad` khác `again` ra sao?

Target ném `IllegalArgumentException("invalid")` với id `bad`. Retry chỉ bắt `IllegalStateException`, nên **không retry** lỗi này. INNER ghi `throw` và `finally`; ngoại lệ đi tiếp qua Retry và OUTER; OUTER cũng ghi `throw/finally`.

Người gọi nhận đúng thông điệp `invalid`. Ví dụ chỉ minh họa chọn loại lỗi; trong hệ thật, **không được suy rằng mọi `IllegalStateException` đều là lỗi tạm thời có thể retry**. Phải xét hợp đồng và khả năng có tác dụng phụ.

### 6.5. Hai đường không kích hoạt chuỗi

`proxy.inspect("x")` vẫn tới handler proxy nhưng được chuyển tiếp thẳng vì điều kiện trong mẫu chỉ chọn `execute`. Nó cho kết quả `LOOKUP:x` và **không có log interceptor**.

`service.execute("direct")` gọi thẳng target gốc, bỏ qua proxy và toàn bộ chuỗi. Nó trả `OK:direct#1` mà không có log `[OUTER]`. Đây là giới hạn riêng của **AOP qua proxy**, không phải kết luận mọi bytecode weaver sẽ có cùng hành vi.

### 6.6. Chú ý về trạng thái trong ví dụ

`HashMap` lưu số attempt theo id chỉ để mô phỏng một thao tác tạm lỗi lần đầu. Chương trình chạy tuần tự. Nó **không** bảo đảm an toàn đa luồng hoặc chống trùng nghiệp vụ. Trong phần mềm thật, cần trạng thái phù hợp với concurrency và nơi sở hữu hiệu ứng nghiệp vụ.

`args.clone()` tạo bản sao nông của mảng đối số, không biến các đối tượng tham chiếu bên trong thành bất biến. Terminal trong mẫu gọi target bằng mảng đã chọn một lần; ví dụ **không hỗ trợ sửa đối số bằng `proceed(newArgs)`**. Nếu muốn thêm tính năng thay đối số, phải có hợp đồng xác thực số/kiểu tham số rõ ràng.

## 7. Vì sao invocation context cần biết “phần còn lại”?

Một cách triển khai dễ sai là giữ một **cursor mutable chung**:

```text
class Invocation:
    index = 0

    proceed():
        current = chain[index]
        index++
        return current.invoke(this)
```

Đây là **giả mã nguy cơ**, không phải API Java hoàn chỉnh. Nếu một interceptor gọi `proceed` lần đầu, cursor được tăng qua phần đuôi. Khi nó gọi `proceed` lần hai, cursor có thể đã tới terminal hoặc vượt quá cuối chuỗi; lúc đó phần interceptor bên trong **không được chạy lại như lần đầu**.

Tùy mục tiêu, công cụ có thể:

- Không cho phép proceed nhiều lần;
- Coi cursor là trạng thái dùng một lần;
- Sao chép context hoặc tạo continuation mới cho mỗi lần;
- Cung cấp API riêng để thực thi lại phần đuôi.

**Bài học:** không xây retry bằng cách đoán cursor sẽ “tự quay về”. Phải hiểu cơ chế triển khai và kiểm thử số lần chạy của **từng interceptor** lẫn target.

### 7.1. Vì sao ví dụ dùng `nextIndex` bất biến?

Trong ví dụ, context được đưa cho Retry giữ chỉ số bắt đầu từ INNER. Khi `proceed()`, nó **tạo context mới** cho INNER với chỉ số kế tiếp đã tăng. Bản context gốc của Retry không thay `nextIndex`. Nhờ vậy, nếu Retry gọi tiếp lần hai, cùng phần đuôi được duyệt lại.

Đây là lựa chọn để trình bày ngữ nghĩa retry một cách rõ ràng, chứ không phải yêu cầu bắt buộc của mọi hệ AOP.

### 7.2. Context theo lần gọi không phải biến toàn cục

Mỗi lời gọi tới proxy trong ví dụ tạo một `Invocation` mới. Trạng thái `nextIndex` và mảng `values` thuộc lần gọi ấy. Nếu thay bằng field dùng chung trên interceptor singleton để lưu “phương thức hiện tại” hoặc “đối số hiện tại”, hai luồng có thể ghi đè dữ liệu của nhau.

Ngay cả khi danh sách interceptor là bất biến, **nội dung đối tượng interceptor** vẫn có thể chứa trạng thái mutable. Thread safety phải được bảo đảm theo thiết kế, không thể suy từ việc dùng `List.of` là mọi thành phần đều an toàn đồng thời.

## 8. Thứ tự interceptor: thay đổi hành vi, không chỉ thay log

Xét ba chính sách:

- **Authorization:** từ chối người gọi không đủ quyền.
- **Cache:** trả dữ liệu đã có mà không gọi target.
- **Audit:** ghi sự kiện phù hợp với yêu cầu.

Nếu sắp:

```text
Cache(Authorization(Target))
```

một cache hit có thể **trả ngay**, không gọi `proceed`, do đó logic Authorization bên trong **không chạy**. Nếu chính sách yêu cầu xác thực **mọi** lượt truy cập, thứ tự này có thể sai.

Ngược lại:

```text
Authorization(Cache(Target))
```

kiểm tra quyền có cơ hội chạy trước cache. Dù vậy, vẫn cần xét dữ liệu cache có được phân vùng theo người dùng/quyền không.

### 8.1. Retry và transaction

```text
Retry(Transaction(Target))
Transaction(Retry(Target))
```

Trong cấu trúc đầu, mỗi attempt có thể mở một phạm vi transaction mới nếu interceptor Transaction được chạy lại. Trong cấu trúc sau, nhiều attempt có thể nằm trong cùng một phạm vi transaction.

Không có phương án đúng chung: trạng thái transaction sau lỗi, điều kiện retry và các tác dụng phụ bên ngoài đều ảnh hưởng. Một số lỗi khiến transaction hiện tại không thể tiếp tục; retry trong phạm vi cũ có thể không an toàn.

### 8.2. Timing và metrics

```text
Timer(Retry(Target)) -> thường đo toàn bộ yêu cầu gồm retry
Retry(Timer(Target)) -> Timer có thể đo từng attempt
```

Khi báo cáo, nên phân biệt **logical requests**, **attempts** và **effects đã cam kết**. Hai lần gọi tiếp có thể tạo hai tác dụng phụ; một log thành công ở interceptor ngoài không chứng minh chỉ một hiệu ứng.

### 8.3. Tracing và context

Một interceptor có thể tạo trace span trước `proceed`, đóng span trong `finally`. Nếu nằm ngoài retry, nó quan sát cả yêu cầu; nếu nằm trong retry, nó có thể tạo span theo từng attempt. Cần quản lý bối cảnh theo đúng vòng đời và tránh lưu context từng lượt gọi vào field global có thể bị các luồng khác ghi đè.

Khi tác vụ bất đồng bộ chuyển sang thread khác, ngữ cảnh không tự truyền chỉ vì `proceed` đã chạy trong luồng ban đầu.

## 9. Giá trị trả về, ngoại lệ và nghĩa vụ bảo toàn

Một interceptor dùng để **quan sát** không nên tự sửa hợp đồng. Cấu trúc khái niệm:

```text
before(context)
try:
    result = proceed()
    afterReturning(result)
    return result
catch error:
    afterThrowing(error)
    throw error
finally:
    cleanup()
```

Đây là **giả mã AOP**, không phải chương trình độc lập. Nếu `afterReturning` hay `cleanup` tự ném lỗi, kết quả/lỗi nhìn từ interceptor ngoài có thể đổi; vì thế cần chính sách xử lý lỗi hệ quan sát.

### 9.1. Return bình thường khác thành công nghiệp vụ

Một target có thể trả `Result.failure("DECLINED")` mà không ném ngoại lệ. Với interceptor, đó là **đường return bình thường**. Nó không tự xác nhận thanh toán thành công. Advice kiểm toán phải dựa trên hợp đồng kết quả hoặc trạng thái nghiệp vụ, không chỉ nhánh return/throw.

### 9.2. Ngoại lệ bị bọc khi dùng reflection

Trong mã ví dụ, `Method.invoke` có thể bọc lỗi của phương thức đích trong `InvocationTargetException`. Hàm `callTarget` lấy `wrapper.getCause()` để chuyển tiếp lỗi gốc. Vì vậy interceptor nhìn thấy `IllegalArgumentException` chứ không chỉ thấy wrapper reflection.

Tuy nhiên lỗi phát sinh **ngay trong interceptor** (ép kiểu sai, dereference null hoặc logging) không nhất thiết đến từ target. Hệ chẩn đoán nên phân biệt tầng phát sinh lỗi.

### 9.3. `void` và primitive return

Khi JDK dynamic proxy chặn phương thức trả `int`, handler vẫn trả `Object`; runtime proxy cần unbox từ wrapper phù hợp. Handler trả `null` cho `int` có thể gây `NullPointerException`; trả sai kiểu có thể gây `ClassCastException`. Phương thức `void` có quy ước return riêng ở API handler.

Một interceptor quanh không được tự trả `null` như giá trị mặc định cho mọi phương thức mà không xem chữ ký.

### 9.4. `finally` cũng có giới hạn

Cleanup trong `finally` chạy khi điều khiển rời `try` theo return/throw thông thường, nhưng không chứng minh tài nguyên đã được cấp thành công trước đó, và không bảo đảm chạy khi tiến trình bị dừng cưỡng bức. Nếu cleanup ném lỗi mới, nó có thể che lỗi nghiệp vụ. Với tài nguyên `AutoCloseable`, try-with-resources có thể hỗ trợ quản lý lỗi đóng và suppressed exception tốt hơn khi áp dụng đúng.

## 10. Cache, short circuit và chặn `proceed`

Một interceptor có thể không gọi tiếp khi đã có kết quả được phép dùng:

```text
intercept(invocation):
    if hasAuthorizedCachedResult(invocation):
        return cachedResult
    return proceed()
```

Đây là giả mã. Điều cần kiểm tra:

1. Cache hit có hợp lệ với **quyền, tenant và trạng thái** của yêu cầu không?
2. Có nghĩa vụ nghiệp vụ nào bắt buộc phải chạy dù cache hit không?
3. Interceptor ở bên trong bị bỏ qua có chứa kiểm toán/bảo mật bắt buộc không?
4. Giá trị trả về cache có tương thích với chữ ký và hợp đồng không?
5. Nếu target có tác dụng phụ, việc không gọi target có làm sai nghiệp vụ không?

**Không gọi `proceed` là một quyết định thay đổi luồng thực thi**, không phải tối ưu “miễn phí”. Với phương thức ghi dữ liệu, short circuit có thể rất nguy hiểm nếu không được định nghĩa là một cách xử lý hợp lệ.

## 11. Nhiều lần `proceed` và nguy cơ lặp tác dụng phụ

Một interceptor retry có thể gọi phần đuôi nhiều lần khi thấy lỗi. Nhưng timeout không chứng minh lần thử trước chưa tạo hiệu ứng.

Ví dụ:

```text
Attempt 1:
    Server creates payment record
    Network response is lost
    Client observes timeout

Attempt 2:
    Same logical request sent again
    Server may create second payment record
```

Một phép retry an toàn có thể đòi **idempotency key** bền vững tại nơi sở hữu hiệu ứng, chính sách phát hiện trùng và đối soát kết quả không chắc chắn.

Không nên bắt mọi `RuntimeException` rồi retry; lỗi dữ liệu không hợp lệ, lỗi bảo mật hoặc lỗi đã commit có ý nghĩa khác nhau. Cần định nghĩa:

- Lỗi nào được phép retry;
- Tổng số attempts và deadline;
- Backoff/jitter nếu phù hợp;
- Key gắn với logical request, không đổi theo từng attempt;
- Cách xử lý “in progress”, xung đột payload và kết quả không rõ;
- Ranh giới transaction và vòng đời resource theo từng attempt;
- Dấu vết tách biệt request, attempt và effect thực sự.

Ví dụ Java ở trên dùng `IllegalStateException` chỉ làm **tín hiệu tạm lỗi giả lập**. Nó không triển khai cơ chế retry an toàn cho thanh toán hay hệ phân tán.

## 12. Proxy bypass, self-invocation và recursion

### 12.1. Lời gọi trực tiếp target

Nếu mã giữ `target` bên cạnh `proxy`, lời gọi `target.execute()` bỏ qua bộ chặn trên proxy. Đây là lý do phải đảm bảo những thành phần cần chính sách được cấp **tham chiếu proxy phù hợp**, không phải đối tượng gốc.

### 12.2. Self-invocation

Trong nhiều mô hình proxy bao quanh đối tượng đích, khi một phương thức của target gọi `this.otherMethod()`, lời gọi nội bộ đi trực tiếp trên `this` và **không quay lại proxy**. Dù `otherMethod` có tên/chữ ký khớp điều kiện, chuỗi interceptor của proxy có thể không chạy.

Khả năng này khác giữa các kiểu proxy và bytecode weaving. Không nên tự chữa bằng cách nới pointcut; cần hiểu **đường gọi** đang bypass.

### 12.3. Gọi chính proxy từ interceptor

Một lỗi thiết kế nguy hiểm là interceptor gọi lại `proxy.execute()` thay vì `invocation.proceed()`:

```text
proxy.execute()
  -> interceptor
       -> proxy.execute()
            -> interceptor
                 -> ...
```

Lời gọi có thể bắt đầu lại **từ đầu chuỗi**, gây đệ quy vô hạn hoặc nhân số lần side effect. `proceed` đúng nghĩa là **đi tới phần còn lại**, không phải tự gọi lại điểm vào bên ngoài.

### 12.4. Proxy lồng nhau

Hai proxy độc lập bọc cùng target có thể tạo nhiều tầng intercept hơn dự kiến. Nếu mỗi tầng đều có retry hoặc logging, số lần thực thi có thể tăng. Cần phân biệt **hai interceptor trong một chain** với **hai chain do hai proxy khác nhau**; thứ tự và phạm vi có thể khác.

## 13. Những khác biệt giữa công nghệ AOP cần kiểm chứng

Các cơ chế dựa trên proxy khác nhau có thể:

- Hỗ trợ proxy **giao diện** hay **lớp**;
- Chặn được những loại phương thức nào;
- Cung cấp named pointcut, annotation matching hoặc lọc chữ ký ở đâu;
- Cho phép thay đối số trong `proceed` hay không;
- Cho phép gọi tiếp nhiều lần hay từ chối;
- Quản lý **cursor** bằng mutable state hay continuation mới;
- Có quy tắc riêng khi phương thức generic tạo bridge methods;
- Quy định thứ tự interceptor từ cấu hình hoặc registration;
- Bọc và truyền ngoại lệ theo hợp đồng API riêng;
- Xử lý phương thức của `Object`, default method và class loader theo cách riêng.

Không sử dụng các thuộc tính của **JDK dynamic proxy** trong ví dụ để suy ra mọi hệ AOP đều giống nhau. Với chính sách quan trọng, hãy dùng tài liệu và test của công nghệ sẽ triển khai.

## 14. Thiết kế interceptor thực tế

### 14.1. Rõ ràng về trách nhiệm

Một interceptor nên có mục tiêu rõ: kiểm tra quyền, ghi timing, trace, cache, retry hoặc chuyển đổi. Nếu một interceptor vừa cấp phát tài nguyên, retry, đổi lỗi và ghi kiểm toán, việc xác định thứ tự/đường lỗi trở nên khó.

Tách chính sách không có nghĩa chúng hoàn toàn độc lập: chúng cần **hợp đồng kết hợp** về thứ tự, kết quả và lifecycle.

### 14.2. Không lưu dữ liệu invocation vào field dùng chung

Tránh mô hình:

```java
class BadInterceptor {
    String currentId;
    // before: currentId = requestId
    // after:  log(currentId)
}
```

Đây là **mã minh họa không độc lập**. Nếu nhiều luồng dùng cùng interceptor instance, lần gọi A có thể đọc `currentId` của lần B. Tham số cục bộ/context theo lần gọi an toàn về mặt phân tách hơn; vẫn cần xem các đối tượng mutable được tham chiếu bên trong.

### 14.3. Không thay dữ liệu chỉ để ghi log

Nếu policy chỉ đo thời gian, interceptor không nên thay đối số, gọi target nhiều lần hoặc đổi kết quả. Nếu policy thực sự muốn biến đổi đầu vào, phải kiểm tra kiểu, quyền, chuẩn hóa và hợp đồng nghiệp vụ của thao tác.

Việc `args.clone()` chỉ nhân bản **mảng** chứ không tự làm các đối tượng trong mảng bất biến. Một interceptor có thể làm thay đổi đối tượng mutable của caller nếu viết vào trạng thái của chúng.

### 14.4. Chọn đúng phương thức và overload

Điều kiện `method.getName().equals("execute")` đủ cho ví dụ chỉ có một overload tên đó. Trong hệ thống có `execute(String)`, `execute(Object)` và `execute(String,int)`, bộ lọc tên đơn thuần quá rộng. Nên lọc thêm kiểu khai báo và số lượng/thứ tự tham số khi chính sách nhắm một overload.

## 15. Kiểm thử đúng chuỗi, không chỉ kết quả cuối

Bảng test gợi ý:

| Kịch bản | Điều cần xác minh |
| --- | --- |
| Một lần thành công | OUTER before → INNER before → target → INNER return/finally → OUTER return/finally |
| Target ném lỗi không retry | Cả INNER/OUTER xử lý lỗi; lỗi gốc được giữ |
| Retry một lần | INNER/target chạy hai lần theo ngữ nghĩa continuation; OUTER một lần |
| Interceptor không gọi tiếp | Target và interceptor bên trong không chạy |
| Interceptor gọi tiếp nhiều lần | Số lần chạy từng thành phần đúng; không phát sinh hiệu ứng ngoài ý định |
| Phương thức ngoài pointcut | Không vào chuỗi không liên quan |
| Gọi trực tiếp target | Không nhận advice của proxy |
| Self-invocation | Xác nhận đường gọi có bypass không |
| Có nhiều overload | Pointcut không khớp sai |
| Trả primitive/`null`/`void` | Kết quả qua proxy đúng kiểu |
| `InvocationTargetException` | Ngoại lệ gốc được chuyển tiếp |
| `finally` hoặc logging tự lỗi | Không che lỗi gốc trái hợp đồng |
| Nhiều thread | Không lẫn context/cursor giữa các lượt gọi |
| Cache trả sớm | Những chính sách bắt buộc không bị bypass |
| Retry ngoài/ trong transaction | Scope tài nguyên và số attempt đúng |

### 15.1. Tại sao chỉ so kết quả không đủ?

Nếu người gọi nhận `OK` sau hai lần thử, một test chỉ xác minh `equals("OK")` vẫn có thể đạt khi nghiệp vụ đã ghi dữ liệu hai lần. Cần đo **số lần gọi target, số attempt và số hiệu ứng đã xác nhận**.

Ngoài ra nên thử ca âm: một phương thức không thuộc pointcut **không** được nhận log/advice. Điều này giúp phát hiện bộ lọc quá rộng và thứ tự cấu hình sai.

### 15.2. Cách chẩn đoán khi log thiếu

1. Kiểm tra người gọi giữ **proxy hay target gốc**.
2. Xác minh phương thức có nằm trong tập proxy/interceptor hỗ trợ không.
3. Xác minh bộ lọc chữ ký/đối số đã khớp.
4. Kiểm tra interceptor bên ngoài có trả sớm mà không gọi `proceed` không.
5. Xác định số tầng proxy và thứ tự chuỗi thực tế.
6. Kiểm tra self-invocation hoặc lời gọi qua trường `this`.
7. Kiểm tra nhánh lỗi có chặn trước khi advice bên trong được thiết lập không.
8. Kiểm tra lỗi ở chính logging/binding có làm đứt luồng trước target không.
9. Dùng test tích hợp để phân biệt **khớp pointcut**, **đi qua proxy** và **advice thực sự chạy**.

## 16. Những sai lầm phổ biến

| Sai lầm | Hệ quả | Hướng sửa |
| --- | --- | --- |
| Nghĩ interceptor chạy tuyến tính một chiều | Không hiểu thứ tự after/catch/finally | Xem cấu trúc lồng `A(B(Target))` |
| Nghĩ `proceed` luôn gọi ngay target | Bỏ qua interceptor bên trong | Hiểu `proceed` gọi **phần còn lại của chuỗi** |
| Chia sẻ cursor mutable không kiểm soát | Retry bỏ qua lớp trong hoặc vào sai vị trí | Tuân thủ API, dùng continuation đúng |
| Gọi proxy từ trong interceptor | Dễ đệ quy vô hạn | Gọi `proceed`/next chuẩn |
| Dùng `proceed` nhiều lần cho logging | Có thể nhân đôi tác dụng phụ | Mặc định một lần với advice quan sát |
| Retry mọi lỗi | Thử lại lỗi vĩnh viễn, tạo tải | Lọc lỗi và có giới hạn/đối soát |
| Đặt Cache trước Authorization không xét quyền | Có thể trả dữ liệu mà bỏ kiểm tra | Xác định precedence bảo mật |
| Không xác minh proxy reference | Lời gọi bypass advice | Quản lý cấp phát đối tượng rõ ràng |
| Nhầm overload theo kiểu runtime | Áp chính sách sai phương thức | Kiểm tra chữ ký khai báo |
| Nuốt exception rồi trả null | Đổi hợp đồng, có thể lỗi primitive | Bảo toàn lỗi hoặc quy định fallback rõ |
| Lưu request ID trong field singleton | Lẫn dữ liệu nhiều luồng | Dùng context theo lần gọi |
| Cho rằng log success nghĩa là chỉ một effect | Bỏ sót double commit | Kiểm tra trạng thái nghiệp vụ |
| Coi proxy như bytecode weaving | Tưởng chặn mọi field/constructor | Xác minh loại join point hỗ trợ |

## 17. Bài tập vận dụng có đáp án

### Bài 1 — Thứ tự đi và về

Chuỗi `A(B(C(Target)))` gồm ba interceptor đều ghi trước và sau, mỗi lớp gọi tiếp một lần. Nếu Target trả bình thường, thứ tự là gì?

**Đáp án:** A-before, B-before, C-before, Target, C-after, B-after, A-after. Đây là cấu trúc lồng, không phải A-before, B-before, C-before, A-after, B-after, C-after.

### Bài 2 — Short circuit

Một interceptor cache trả kết quả mà không gọi `proceed`. Những thành phần nào chạy?

**Đáp án:** Các interceptor **bên ngoài cache** có thể chạy và nhận giá trị cache; các interceptor **bên trong** và target không chạy cho lượt đó. Nếu kiểm tra quyền nằm bên trong cache thì có nguy cơ bị bỏ qua.

### Bài 3 — Retry và phạm vi

Với `Audit(Retry(Metrics(Target)))`, Retry gọi tiếp hai lần và continuation cho phép chạy lại phần đuôi. Audit và Metrics chạy mấy lần?

**Đáp án:** Audit thường có một lượt kích hoạt ở ngoài; Metrics có thể chạy hai lần theo từng attempt; Target cũng có thể chạy hai lần. Phải xác minh API proceed của công nghệ đang dùng.

### Bài 4 — Lỗi sau commit

Target tạo giao dịch rồi timeout. Interceptor retry gọi `proceed` lại. Vì sao không thể kết luận an toàn?

**Đáp án:** Timeout có thể xuất hiện sau khi giao dịch đã tạo effect. Lần thử tiếp có thể tạo effect thứ hai. Cần idempotency ở nơi sở hữu effect và đối soát kết quả không chắc chắn.

### Bài 5 — Gọi proxy từ chính advice

Interceptor tự gọi `proxy.execute()` thay vì `invocation.proceed()`. Điều gì dễ xảy ra?

**Đáp án:** Lời gọi bắt đầu lại từ điểm vào ngoài, kích hoạt chính interceptor và có nguy cơ đệ quy hoặc lặp xử lý. `proceed` cần chỉ tới phần chuỗi kế tiếp.

### Bài 6 — Context đồng thời

Một interceptor singleton giữ `currentMethod` và `currentArgs` trong field để dùng sau lời gọi. Vì sao không an toàn?

**Đáp án:** Hai luồng có thể ghi đè dữ liệu cùng field. Nên giữ trạng thái theo từng invocation hoặc dùng cơ chế truyền context có vòng đời, đồng thời phải xét các đối tượng mutable bên trong.

### Bài 7 — Ngoại lệ được khôi phục

Một interceptor bên trong bắt lỗi đích rồi trả fallback. Interceptor ngoài sẽ nhất thiết thấy after-throwing không?

**Đáp án:** Không. Với ranh giới interceptor ngoài, phần bên trong đã **trả về bình thường** nhờ fallback, nên interceptor ngoài có thể thấy nhánh return. Phải xét ranh giới thực thi từng interceptor.

### Bài 8 — Proxy bypass

Dịch vụ có phương thức `outer()` gọi `this.inner()`. Một proxy bao đối tượng đích có chắc chặn lời gọi inner không?

**Đáp án:** Không. Trong nhiều mô hình proxy, self-invocation đi trực tiếp trên target và không đi lại qua proxy. Cần kiểm tra công nghệ hoặc thay đổi ranh giới gọi phù hợp.

### Bài 9 — Phân biệt context

JDK proxy nhận đối số `int` qua mảng `Object[]`. Loại giá trị thường thấy ở phần tử mảng và loại chữ ký khai báo là gì?

**Đáp án:** Phần tử mảng dùng wrapper `Integer`, còn `Method.getParameterTypes()` vẫn thể hiện `int.class`. Không đồng nhất boxing với overload.

## 18. Ghi nhớ

- **Proxy là điểm vào**, interceptor là các lớp can thiệp, `proceed` là bước sang **phần còn lại của chuỗi**.
- Chuỗi có cấu trúc **lồng nhau**, nên thứ tự before và after thường ngược nhau.
- **Invocation context** thuộc một lượt gọi và mang method/arguments/đích/vị trí tiếp tục theo mô hình hỗ trợ.
- Interceptor có thể trả sớm, gọi tiếp một lần hoặc nhiều lần **nếu API cho phép**; mỗi lựa chọn thay đổi hành vi quan sát.
- **Retry có thể chạy lại các interceptor bên trong và target**, dẫn tới tác dụng phụ lặp; phải xét idempotency và ranh giới nghiệp vụ.
- **Thứ tự interceptor là một phần hợp đồng**: cache, quyền truy cập, retry, transaction, timing và kiểm toán có thể đổi nghĩa khi hoán vị.
- **Proxy có đường bypass** khi gọi target trực tiếp hoặc self-invocation không qua đại diện; không mặc định nó chặn constructor/field access.
- Advice quan sát nên giữ nguyên **đối số, kết quả, ngoại lệ và số lần thực thi**; kiểm thử dương và âm trên đường gọi thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-008">Khác biệt về phạm vi can thiệp giữa proxy và biến đổi mã</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt về phạm vi can thiệp giữa proxy và biến đổi mã

## 1. Bài toán: một advice được khai báo nhưng không chạy ở mọi nơi

Trong **Aspect-Oriented Programming (AOP)**, **pointcut** chọn những *join point* mà hệ thống có thể can thiệp; **advice** là hành vi được thực thi tại những điểm được chọn. Tuy nhiên, từ “pointcut khớp một phương thức” tới “lần thực thi đó thật sự có advice” còn một điều kiện quan trọng: **cơ chế AOP phải nhìn thấy và can thiệp được đường thực thi ấy**.

Hai nhóm triển khai thường gặp có phạm vi khác nhau:

- **Proxy/interceptor:** caller gọi vào **một đối tượng trung gian**. Chỉ những lời gọi đi qua trung gian, thuộc những phương thức nó hỗ trợ, mới được xử lý.
- **Biến đổi mã (code/bytecode weaving/instrumentation):** công cụ bổ sung logic vào những điểm được chọn trong mã hoặc bytecode. Nếu một method execution thực sự đã được biến đổi, lời gọi vào method đó có thể kích hoạt advice **dù caller không đi qua một proxy ngoài**.

Vì vậy, cùng một ý định “ghi audit mỗi lần thực thi `process()`” có thể đem lại kết quả khác:

~~~text
Proxy ở ranh giới:
  caller -> proxy.process() -> target.process() -> audit chạy
  caller -> target.process()                 -> proxy audit KHÔNG chạy
  target -> this.process()                   -> proxy audit KHÔNG chạy

Giả sử đã woven đúng method execution process:
  caller -> process()                        -> advice trong execution chạy
  target -> this.process()                   -> advice trong execution chạy
~~~

Các dòng “đã woven” chỉ đúng **nếu loại join point ấy được engine hỗ trợ và class/method thực sự nằm trong phạm vi biến đổi**. Không được suy ra mọi công cụ weaving sẽ thấy mọi thao tác Java.

**Mục tiêu của bài:** nhận diện độ phủ theo đường gọi, phân biệt `call` và `execution`, hiểu self-invocation, phương thức `private/final/static`, constructor/field, giới hạn classloader/agent, và thiết kế kiểm thử để không nhầm **“có khai báo pointcut”** với **“được instrument thật”**.

## 2. Phân biệt rõ proxy, weaving và instrumentation

| Thành phần | Trách nhiệm | Điều kiện để can thiệp |
| --- | --- | --- |
| **JDK interface proxy** | Triển khai một interface, chuyển mỗi lời gọi trên proxy qua `InvocationHandler` | Caller dùng proxy; method thuộc interface mà proxy expose |
| **Subclass proxy** | Dùng subclass ghi đè những method được phép override | Lời gọi đi qua subclass instance và cơ chế dispatch phù hợp |
| **Compile-time weaving** | Kết hợp advice khi biên dịch | Source/class và join point được dệt vào artifact build |
| **Post-compile/binary weaving** | Sửa class/JAR đã biên dịch | Đúng artifact và bytecode được weaver xử lý |
| **Load-time weaving** | Biến đổi class khi JVM nạp/định nghĩa nó | Transformer/weaver có mặt đúng lúc và class được chọn |
| **Runtime retransformation** | Biến đổi lại class đã định nghĩa, nếu JVM/agent hỗ trợ | Phải thỏa capability và giới hạn thay đổi class |
| **Interceptor chain** | Tập handler bao quanh một lời gọi đi qua một ranh giới | Đường gọi nằm trong chuỗi interceptors được thiết lập |

**Proxy có thể triển khai hành vi AOP mà không sửa bytecode của target.** Vì thế “AOP qua proxy” không nên bị gọi bừa là “đã dệt bytecode vào class”. Ngược lại, một class có bytecode được biến đổi vẫn có thể được gọi trực tiếp mà advice ở method execution chạy đúng, không cần proxy như điểm vào bắt buộc.

**Thời điểm kết hợp** và **phạm vi pointcut** là hai trục độc lập: weaving trong build không tự cho phép can thiệp mọi join point; dùng proxy lúc runtime cũng không tự giới hạn advice chỉ ở kiểu `before` — proxy có thể bao quanh kết quả và ngoại lệ của những lời gọi nó nhìn thấy.

## 3. Đơn vị cốt lõi: lời gọi qua proxy hay method execution thật?

Giả sử:

~~~java
interface Service {
    void save();
}

class ServiceCore implements Service {
    public void save() { /* business logic */ }
}
~~~

Đây chỉ là đoạn khai báo minh họa, không phải chương trình Java độc lập.

Nếu `Service proxy = makeProxy(new ServiceCore())` thì:

- `proxy.save()` đi qua lớp proxy và có thể kích hoạt handler.
- `new ServiceCore().save()` đi trực tiếp vào target khác; proxy không nhìn thấy.
- `core.save()` với một tham chiếu giữ chính target bên trong proxy cũng bỏ qua handler.
- Nếu `save()` từ chính `ServiceCore` gọi `this.save()` ở một phương thức khác, đường gọi nội bộ thường không đi qua proxy bên ngoài.

Ngược lại, nếu một **execution join point của `ServiceCore.save()` đã được woven vào bytecode**, thì lần nào JVM đi vào thân `save()` cũng có thể kích hoạt đoạn đã chèn, kể cả khi caller dùng tham chiếu trực tiếp hay gọi `this.save()`.

**Không đánh đồng:** “proxy chặn lời gọi vào một interface method” với “ngôn ngữ AOP chọn mọi `method call join point`”. JDK `InvocationHandler` đứng ở **ranh giới lời gọi đi qua proxy**, còn một bytecode weaver có thể biểu đạt `call` tại từng vị trí gọi hoặc `execution` trong thân callee, tùy mô hình của công cụ.

## 4. Bảng độ phủ so sánh

| Tình huống | Interface proxy điển hình | Subclass proxy điển hình | Bytecode weaving khi hỗ trợ điểm đó |
| --- | --- | --- | --- |
| Gọi public method qua proxy | Có thể intercept | Có thể intercept nếu method override hợp lệ | Có thể, nếu callee/call site đã woven |
| Gọi trực tiếp target bỏ qua proxy | Không qua handler proxy | Không qua instance subclass proxy | Có thể tại execution đã woven |
| `this.otherMethod()` bên trong target | Thường bypass proxy ngoài | Có thể khác tùy thiết kế subclass và dispatch; không khái quát mọi trường hợp | Có thể tại execution/call đã woven |
| Private method | Không là public interface method để JDK proxy chặn trực tiếp | Không override được private method | Có thể nếu pointcut/weaver hỗ trợ |
| Final instance method | **Có thể intercept** lời gọi qua interface proxy | Không override final method | Có thể instrument body/call site nếu engine hỗ trợ |
| Static method | Không có dispatch instance qua interface proxy | Không override method static | Có thể với engine hỗ trợ static execution/call |
| Constructor execution | Không phải lời gọi method qua proxy đã tồn tại | Không tương đương một method override | Có thể với engine hỗ trợ constructor join point |
| Field get/set trực tiếp | Không tự chặn field access | Không tự intercept field opcode | Có thể khi field join point được hỗ trợ |
| Một lệnh `throw` hoặc `catch` trong thân method | Handler có thể quan sát exception thoát khỏi method, không tự thấy từng `catch` | Tương tự ở ranh giới override | Phụ thuộc engine có loại join point tương ứng |
| Method trả `CompletableFuture` | Có thể thấy đối tượng Future được trả từ method qua proxy | Có thể ở method được override | Có thể thấy method return; không tự có task completion |
| Mã thuộc JDK/native hoặc thư viện ngoài | Chỉ API/đường gọi proxy expose | Phụ thuộc subclassing/access | Tùy quyền biến đổi, phạm vi và giới hạn JVM/tool |

Bảng thể hiện **khả năng**, không phải lời hứa mọi AOP engine sẽ cung cấp đủ. Với bytecode weaving, cần kiểm tra **mô hình join point, phạm vi class, classloader, module, quyền công cụ và thời điểm biến đổi**. Với proxy, phải biết caller thực tế có sử dụng proxy hay không.

### 4.1. “Final method proxy không chặn được” có luôn đúng?

Không. Một **subclass proxy** cần override target method, nên `final` cản việc override. Nhưng một **JDK interface proxy** đứng trước lời gọi trên interface: handler vẫn có thể chạy rồi gọi vào implementation mà method là `final`. Hai kỹ thuật đều được gọi là proxy nhưng hạn chế khác nhau.

### 4.2. “Private method không thể có advice” có luôn đúng?

Không. JDK interface proxy không intercept trực tiếp private method vì nó không thuộc interface public mà proxy expose. Song một **weaver có hỗ trợ bytecode execution join point** có thể chèn logic vào private method. Quyết định có thực sự chèn được còn phụ thuộc class và công cụ.

### 4.3. Constructor có kết quả trả về không?

Constructor là một loại điểm riêng với lifecycle khởi tạo đối tượng, không đơn thuần là một phương thức instance trả về object qua interface proxy. Một weaving model có thể định nghĩa constructor call/execution hoặc initialization; **không suy ra** từ việc proxy chặn `run()` rằng nó cũng chặn mọi `new Target()`.

## 5. Self-invocation: nguyên nhân phổ biến nhất của “advice biến mất”

Ví dụ minh họa:

~~~java
class InvoiceService {
    void submit() {
        validate();
        persist();
    }

    void validate() { /* logic */ }
    void persist() { /* logic */ }
}
~~~

Đây là mã minh họa không độc lập. Nếu caller gọi `proxy.submit()`, proxy ngoài có thể intercept `submit`. Nhưng `submit()` gọi `validate()` và `persist()` trên **`this` của target**. Trong mô hình wrapper bên ngoài, hai lời gọi đó không đi qua proxy nên những interceptor chỉ gắn trên method qua proxy có thể không chạy.

Ngược lại, nếu bytecode của `validate()` và `persist()` thực sự được woven ở **execution join point**, các lần gọi nội bộ vẫn có thể đi qua mã chèn.

### Giải pháp với proxy

- Đặt chính sách tại **ranh giới public API** thực sự đi qua proxy, nếu chính sách cho phép.
- Tổ chức lại các thao tác cần interception thành hợp đồng service/đối tượng khác và gọi qua **proxy được quản lý**, không qua tham chiếu target thô.
- Tránh dùng self-reference/proxy injection không minh bạch chỉ để “ép” advice chạy; dễ tạo vòng phụ thuộc và khó test.
- Nếu một chính sách bảo mật hoặc tính nhất quán bắt buộc không được phép bypass, cân nhắc thực thi bất biến ấy ngay trong domain boundary hoặc dùng cơ chế can thiệp có độ phủ được xác minh.

Điều không nên làm là giả định **mọi lời gọi nội bộ luôn bị bypass trong mọi kỹ thuật proxy**: subclass dispatch và những cơ chế proxy đặc biệt có thể khác. Hãy mô tả **chính xác wrapper, target instance và đường dispatch** được dùng.

## 6. Method call và method execution khác nhau ra sao?

Trong những mô hình AOP phân biệt hai loại join point:

- **Call join point:** một điểm **gọi** phương thức, gắn với call site của caller.
- **Execution join point:** một lần **thực thi thân** phương thức, gắn với callee.

Một method `Service.process()` có thể được gọi từ hai caller khác nhau:

~~~text
ControllerA ---- call site A ----+
                                  |
                                  v
                         execute Service.process()
                                  `
                                  |
ControllerB ---- call site B ----+
~~~

Nếu pointcut chọn **call sites**, việc dệt có thể cần áp dụng trên code caller tương ứng; nếu chọn **execution**, nó cần đặt tại thân implementation thực sự.

Hệ quả:

1. Bạn có thể kiểm soát code callee nhưng không kiểm soát mọi caller. Execution weaving tại callee có thể có độ phủ thích hợp hơn khi engine hỗ trợ.
2. Bạn có thể chỉ muốn can thiệp lời gọi từ một nhóm caller; một mô hình call join point có thể biểu đạt ngữ cảnh khác với việc bao mọi execution.
3. Nếu một đường đi gồm một call join point và một execution join point, hai advice khác nhau có thể cùng chạy **một cách hợp lệ**. Không kết luận double weaving chỉ vì có hai event.
4. Interface method declaration, method implementation và overload có thể làm bộ chọn ký hiệu/hàm khác nhau; kiểm tra chữ ký thực thay vì chỉ tên.

**JDK dynamic proxy trong ví dụ sau không phải một implementation của toàn bộ ngôn ngữ pointcut call/execution.** Nó chỉ chặn các lời gọi đến object proxy theo interface, nên đừng dùng nó để suy luận mọi tính năng của bytecode-based AOP.

## 7. Ví dụ Java 17 chạy được: thấy ranh giới proxy

Chương trình dùng Java Reflection và `java.lang.reflect.Proxy`:

- Interface `Service` có `work()` và `internally()`.
- `Target.work()` được khai báo **`final`** nhưng vẫn có thể được JDK proxy quan sát khi caller gọi qua interface proxy.
- `Target.internally()` dùng `this.work()`, bỏ qua handler của proxy ở **lời gọi `work` nội bộ**.
- `Target.helper()` là `private` và `Target.utility()` là `static`, không có method tương ứng được expose qua interface proxy.
- Constructor in `BODY constructor`; nó xảy ra trước khi proxy được tạo.
- Lần gọi trực tiếp `target.work()` không đi qua proxy.
- Nhánh target ném ngoại lệ được bóc khỏi `InvocationTargetException` để caller nhìn thấy đúng lỗi gốc.

**Ranh giới mô phỏng:** các dòng `BODY ...` là những câu lệnh `println` được **viết tay trong mã target** để chỉ ra chỗ thực thi. Chúng **không phải kết quả bytecode weaving**. Nếu một bytecode weaver thực sự chèn advice vào body/call site được hỗ trợ, nó có thể thực hiện những quan sát tương tự ở các điểm đã dệt.

Tạo file `AopCoverageDemo.java`:

~~~java
import java.lang.reflect.*;

public class AopCoverageDemo {
    interface Service { String work(String s); String internally(String s); }

    static final class Target implements Service {
        Target() {
            System.out.println("BODY constructor");
        }

        public final String work(String s) {
            System.out.println("BODY work");
            if (s.equals("bad")) {
                throw new IllegalArgumentException("invalid code");
            }
            return helper(s);
        }

        private String helper(String s) {
            System.out.println("BODY private");
            return "ok:" + s;
        }

        public String internally(String s) {
            System.out.println("BODY internally");
            return this.work(s);
        }

        static void utility() {
            System.out.println("BODY static");
        }
    }

    static Object dispatch(Service target, Method m, Object[] args)
            throws Throwable {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Service wrap(Service target) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[]{Service.class},
                (p, m, args) -> {
                    if (!m.getName().equals("work")) {
                        return dispatch(target, m, args);
                    }

                    System.out.println("PROXY before");
                    try {
                        Object result = dispatch(target, m, args);
                        System.out.println("PROXY after");
                        return result;
                    } catch (Throwable error) {
                        System.out.println("PROXY throwing "
                                + error.getClass().getSimpleName());
                        throw error;
                    }
                });
    }

    public static void main(String[] args) {
        Target target = new Target();
        Service proxy = wrap(target);

        System.out.println("CALLER " + proxy.work("A"));
        System.out.println("CALLER " + proxy.internally("B"));
        System.out.println("CALLER " + target.work("C"));
        Target.utility();

        try {
            proxy.work("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
    }
}
~~~

Biên dịch với target Java 17 và chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AopCoverageDemo.java
java AopCoverageDemo
~~~

Đầu ra đã được kiểm chứng bằng trình biên dịch Java với `--release 17` và chạy thực:

~~~text
BODY constructor
PROXY before
BODY work
BODY private
PROXY after
CALLER ok:A
BODY internally
BODY work
BODY private
CALLER ok:B
BODY work
BODY private
CALLER ok:C
BODY static
PROXY before
BODY work
PROXY throwing IllegalArgumentException
CALLER invalid code
~~~

### 7.1. Lời gọi `proxy.work("A")`

Lời gọi đi vào `InvocationHandler`. Handler in `PROXY before`, chuyển tiếp tới `Target.work`, target in `BODY work` và `BODY private`, handler in `PROXY after`, cuối cùng caller nhận `ok:A`.

Dù implementation `work` là **`final`**, JDK interface proxy vẫn intercept vì proxy nhận **lời gọi qua interface**, không phải subclass override method `final` của `Target`.

### 7.2. Lời gọi `proxy.internally("B")`

Handler vẫn nhận lời gọi `internally`, nhưng bộ lọc của ví dụ chỉ ghi `PROXY` cho tên `work`. Sau khi chuyển tiếp tới `Target.internally`, target gọi `this.work("B")` **trực tiếp trên chính target**. Vì vậy có `BODY internally`, `BODY work` và `BODY private`, nhưng không có `PROXY before/after` của `work`.

Đây là **self-invocation bypass** ở proxy ngoài. Nếu `Target.work` đã được weaving tại execution, body instrumentation có thể chạy cho lời gọi này (tùy mô hình công cụ).

### 7.3. Lời gọi `target.work("C")`

Caller giữ tham chiếu `target` thật. Dù proxy `proxy` đã tồn tại, **việc tạo một proxy không tự sửa target**. `target.work("C")` đi thẳng vào `Target.work` nên có dòng `BODY` nhưng không có dòng `PROXY`.

### 7.4. `Target.utility()` và constructor

Constructor của `Target` chạy khi gọi `new Target()`, trước lúc `wrap(target)` trả proxy. Method `static utility()` được gọi qua class, không qua interface proxy; chương trình chỉ in marker `BODY static` viết tay.

Một weaver có hỗ trợ constructor/static join points **có thể** thêm behavior ở những nơi đó. Nhưng ví dụ không chứng minh mọi weaver đều làm được, và không thể lấy một handler method trên interface để thay thế sự hỗ trợ constructor/field join point.

### 7.5. `proxy.work("bad")` và ngoại lệ

Handler in `PROXY before`, target in `BODY work` rồi ném `IllegalArgumentException`. Reflection bọc ngoại lệ trong `InvocationTargetException`; `dispatch` bóc `getCause()` và ném lại. Handler in `PROXY throwing IllegalArgumentException`, caller bắt ngoại lệ nghiệp vụ gốc.

Quan sát chỉ khi **lời gọi đi qua proxy**. Một advice sau trả về bình thường không nên chạy ở nhánh này; một around wrapper có thể quan sát exceptional exit. Không được tự thay lỗi gốc chỉ vì bọc target qua Reflection.

## 8. Weaving thật có thể làm được gì hơn proxy?

Một bytecode weaver có thể biến đổi **class định nghĩa method** hoặc **call site** theo mô hình join point:

~~~text
Source / class bytes
       |
       v
Weaver matches selected join points
       |
       v
Woven class bytes:
  before original operation -> advice logic
  original operation
  on normal/exceptional exit -> advice logic
       |
       v
JVM executes woven code
~~~

Đây là **sơ đồ ý niệm**, không phải trình tự bytecode bắt buộc. Một engine có thể dùng helper method, jump, injected dispatch, synthetic members hoặc kỹ thuật khác để hiện thực advice.

Nếu `Target.work()` đã được dệt ở execution join point, mọi lần vào body của bản class **đã dệt** có thể tạo advice: qua proxy, qua lời gọi trực tiếp hoặc qua `this.work()`. Do đó độ phủ không phụ thuộc riêng object proxy.

Nhưng khả năng đó bị giới hạn bởi:

1. **Engine có định nghĩa loại join point không?** Một engine chỉ hỗ trợ method execution không tự có field access hoặc constructor call.
2. **Class có nằm trong phạm vi dệt không?** Thư viện ngoài, class sinh động hoặc các module riêng có thể không được xử lý.
3. **Công cụ có thể thay đổi bytecode hợp lệ không?** Bytecode verifier, classfile version, stack maps và giới hạn JVM rất quan trọng.
4. **Dệt được chạy đúng thời điểm không?** Với load-time, class đã nạp trước transformer có thể không nằm trong lần dệt.
5. **Có ràng buộc module/classloader không?** Class từ loader khác có thể bỏ qua agent policy.
6. **Native method hoặc runtime internals có giới hạn không?** Không suy luận sửa được thân phương thức không có Java bytecode.
7. **Có nhiều tầng instrumentation không?** Weaving cộng proxy có thể ghi sự kiện trùng nếu thiếu policy ownership.

Từ “weaving mạnh hơn proxy” nên được thay bằng nhận định cụ thể: **weaving có thể nhìn những loại join point/đường gọi mà proxy ngoài không thấy, nếu công cụ và môi trường thật sự hỗ trợ**. Đổi lại là chi phí build/deployment/compatibility và yêu cầu kiểm thử khác.

## 9. Dùng các modifier để xác định *vị trí* can thiệp

Một method `final` cấm **subclass override**, nhưng một interface proxy vẫn có thể intercept lời gọi **trước khi** chuyển tiếp đến implementation final; bytecode weaving có thể xử lý execution/call tương ứng nếu engine hỗ trợ.

Method `private` không được subclass override và không phải API của JDK interface proxy, nhưng một execution hook được dệt trong method private vẫn có thể chạy. Method `static` không dùng virtual instance dispatch để đi qua proxy, song call-site hoặc execution weaving có thể có khả năng riêng.

Đây không phải bảng xếp hạng công cụ. Khi thiếu advice, hãy ghi rõ **modifier → loại join point → ranh giới proxy hoặc class bytes đã weave → đường gọi thật**; việc biết một modifier đơn lẻ không chứng minh advice sẽ chạy hay không.

## 10. Constructor, field, exception handler: khác hẳn method wrapper

Ví dụ:

~~~java
class Counter {
    int value;

    Counter() { value = 0; }

    void increment() { value++; }
}
~~~

Đây là mã minh họa chưa có `main`. Có ba loại thao tác khác nhau:

- `new Counter()` và constructor initialization.
- `Counter.increment()` là method execution.
- `value++` gồm field read/write ở bytecode, nếu không bị tối ưu/biến đổi đặc biệt.

JDK interface proxy của một `CounterService` có thể quan sát method `increment()` **nếu caller đi qua proxy**, nhưng không tự nhìn từng opcode đọc/ghi `value`. Một weaver hỗ trợ field get/set join point có thể làm việc sâu hơn nếu class/call site trong phạm vi.

Tương tự, proxy handler có thể quan sát exception được **ném ra khỏi lời gọi mà nó bao**, nhưng không biết mọi lần có `throw` hoặc `catch` nằm sâu trong thân target. Một exception được ném rồi bắt **bên trong target** có thể không làm method kết thúc ngoại lệ từ góc nhìn proxy. Một weaving model có các điểm exception handler có thể cho quan sát khác, nếu có hỗ trợ.

Đừng dùng phương pháp intercept method để tuyên bố đã thực thi policy tại **mọi field mutation**. Nếu đó là yêu cầu nghiệp vụ bắt buộc, cần xây điểm enforcement tin cậy hoặc kiểm tra khả năng instrument đầy đủ.

## 11. Proxy identity, object graph và phạm vi thật

Không chỉ method modifier ảnh hưởng độ phủ, mà còn **tham chiếu nào được đưa tới caller**:

~~~text
Factory tạo:
  Target raw = new Target()
  Service proxy = wrap(raw)

Caller A giữ proxy -> có interception
Caller B giữ raw   -> không có interception qua proxy ấy
~~~

Một vài tình huống phải kiểm tra:

- **Dependency wiring:** lớp cần advice nhận proxy hay target?
- **Object creation ngoài factory:** người khác gọi `new Target()` và giữ raw reference?
- **Callback và lambda:** callback có giữ `this` của target hay giữ proxy?
- **Serialization/deserialization:** object được phục hồi có còn wrapper không?
- **Equals/hashCode/toString:** proxy có forwarding contract riêng hay không?
- **Dynamic dispatch:** class cast sang concrete implementation có hợp lệ với interface proxy không? Thường **không** vì proxy chỉ implements interfaces.
- **Subclass proxy:** có giới hạn constructor/final class/final method không?
- **Multiple proxies:** cùng policy có vô tình bọc hai lần, khiến advice chạy hai lần không?

Đối với một **advice chỉ quan sát**, tiêu chí tốt là giá trị trả về, loại ngoại lệ và số lần target chạy không đổi ngoài các sự kiện quan sát được. Nhưng một proxy dùng cho authorization/retry/cache có thể có chính sách thay đổi luồng; phải công bố rõ để kiểm thử.

## 12. Weaving và thời điểm class loading

**Compile-time hoặc binary weaving** xử lý class trước runtime, có thể kiểm tra artifact cuối trong pipeline. **Load-time weaving** biến đổi class khi nó đang được JVM định nghĩa và yêu cầu agent/weaver/config có mặt đúng lúc.

Với LTW:

~~~text
raw class bytes
       |
ClassLoader begins defining class
       |
Transformer / Weaver chooses class + join points
       |
transformed bytes (if applicable)
       |
JVM defines class
       |
future method executions may carry advice
~~~

Các lỗi phổ biến:

- Agent khởi động quá muộn: class đã được định nghĩa trước khi transformer xuất hiện.
- Sai classloader: cùng binary name nhưng object runtime thuộc loader khác.
- Pointcut đúng về cú pháp nhưng class ngoài exclude/include policy.
- Bytecode version, module access hoặc JVM verifier không chấp nhận transformation.
- Chỉ một số phương thức được biến đổi, nhưng test giả định toàn class đã được instrument.
- Có transformer khác chèn logic tương đương gây double instrumentation.

**Retransformation/redefinition** là khả năng riêng của JVM instrumentation. Không suy luận rằng gọi retransform nhiều lần sẽ luôn thêm một lớp advice, hoặc tháo agent sẽ hoàn nguyên class ngay lập tức. Phải tuân theo hợp đồng engine/JVM và kiểm thử bytes/behavior thực.

## 13. Method call vs execution: thêm góc nhìn theo artifact

Điểm `call` nằm ở **call site**; điểm `execution` nằm ở **callee**. Điều này ảnh hưởng khả năng áp dụng weaving khi code trải trên nhiều artifacts:

- Nếu chỉ được phép dệt **module callee**, một execution join point có thể dễ kiểm soát hơn những call sites ở các module caller không sửa được.
- Nếu mục tiêu chỉ là lời gọi từ một nhóm caller nhất định, một engine hỗ trợ call join points có thể biểu đạt chính sách chọn theo caller.
- Với proxy, caller không cần được bytecode-weave nhưng phải gọi qua **đúng proxy reference**.
- Một thư viện được load hai phiên bản từ hai classloaders có thể khiến chỉ một bản callee được weaving.
- Method overload, bridge/synthetic methods có thể làm pointcut chọn khác với điều người đọc chỉ nhìn source nghĩ tới.

**Lựa chọn đúng là lựa chọn dựa trên join point cần bao phủ**, không phải chọn phương pháp vì tên “weaving” nghe rộng hơn.

## 14. Generic bridge và synthetic methods

Java compiler có thể tạo **bridge method** do type erasure và covariant return. Một đường gọi có thể đi qua bridge rồi chuyển tới implementation.

Ví dụ:

~~~java
class Parent<T> {
    T value() { return null; }
}

class Child extends Parent<String> {
    @Override
    String value() { return "ok"; }
}
~~~

Đây là đoạn minh họa, không phải chương trình độc lập. Compiler có thể tạo bridge method trong `Child` để bảo toàn đa hình ở bytecode level. Nếu một weaver chọn **method executions** trên cả bridge và implementation, một logical API call có thể kích hoạt advice ở hai join points khác nhau, dù không hề có double weaving.

Với Reflection có thể kiểm tra `Method.isBridge()` và `Method.isSynthetic()` khi phù hợp. Nhưng không loại mọi synthetic method mù quáng: cần hiểu cách engine ánh xạ source pointcut sang bytecode và các đường caller thực sự đi qua.

Proxy handler nhận `Method` đại diện cho lời gọi qua interface; metadata này chưa chắc trùng với method implementation bên dưới ở mọi tình huống override/bridge. Cần test contract thay vì chỉ so tên.

## 15. Ngoại lệ, kết quả trả về và loại advice

Proxy có thể gắn hành vi `before`, `after returning`, `after throwing` hoặc `around` **ở ranh giới lời gọi mà nó thấy**. Bytecode weaving cũng có thể hỗ trợ những loại advice ấy trên tập join point lớn hơn.

Nhưng độ phủ không được đánh giá chỉ bằng việc có log `before`:

- **Normal return:** target trả `null`, `false` hoặc `Result.failure(...)` vẫn có thể là normal completion; after-returning có thể chạy nếu pointcut khớp.
- **Exceptional exit:** target ném ngoại lệ ra khỏi ranh giới, advice ở nhánh throwing/around có thể quan sát.
- **Target tự catch lỗi:** ở ranh giới proxy có thể chỉ thấy normal return, không có thông tin về exception nội bộ.
- **Advice tự ném lỗi:** caller có thể nhìn lỗi của advice thay vì lỗi target; cần chính sách fail-open/fail-closed rõ.
- **Around retry:** có thể gọi `proceed` nhiều lần khiến target thực thi nhiều lần; phải tách số advice entries và business invocations.
- **Async return:** một method trả `CompletableFuture` normal chưa chứng minh task bên trong đã hoàn tất; cả proxy lẫn method-execution weaving đều có thể chỉ thấy handle.

Nếu cần quan sát completion của tác vụ bất đồng bộ, phải gắn vào lifecycle của task/Future hoặc một điểm thích hợp khác, không giả định một after-returning của method có kết quả cuối.

## 16. Bảo mật: độ phủ không phải tính năng phụ

Hãy giả sử advice có nhiệm vụ kiểm tra quyền truy cập. Nếu chính sách chỉ chạy qua proxy, mọi đường dùng **target raw** có thể bypass chính sách đó. Dù code người gọi bình thường đều dùng proxy, một factory khác, bài test, extension hoặc callback có thể vô tình giữ raw reference.

Cần cân nhắc:

1. **Ranh giới tin cậy:** caller không tin cậy có thể tự tạo/giữ target không?
2. **Policy bắt buộc hay chỉ hỗ trợ:** metrics thiếu một số lời gọi có thể là hạn chế quan sát; kiểm quyền bị bypass có thể là lỗi bảo mật.
3. **Đường gọi nội bộ:** self-invocation có thể bỏ qua proxy; audit/enforcement có cần thấy nó không?
4. **Các method không expose qua proxy:** tác động dữ liệu có thể xảy ra ở `private`, `static`, constructor, field access?
5. **Fail-open/fail-closed:** khi engine hoặc policy không áp được, hệ thống xử lý thế nào?
6. **Version/config drift:** production có thực sự instrument class và đường gọi dự kiến không?

Không nên biến AOP thành cơ chế bảo vệ duy nhất cho những bất biến nghiệp vụ quan trọng nếu không thể chứng minh phạm vi áp dụng đầy đủ. Có thể kết hợp kiểm tra ngay trong domain/service boundary, database constraints và cơ chế authorization tại API đáng tin, tùy bài toán.

Weaving sâu hơn có thể giúp độ phủ nhưng cũng cần chẩn đoán **những lớp/điểm không dệt được**; không có “bảo đảm phủ 100%” mặc định từ một công cụ.

## 17. Hiệu năng và quan sát

So sánh hiệu năng không chỉ dựa vào “proxy hay bytecode”:

| Thành phần chi phí | Proxy | Bytecode weaving |
| --- | --- | --- |
| Khởi tạo | Tạo proxy, handler, metadata | Build-time transform hoặc load-time transform |
| Mỗi lời gọi | Dispatch qua wrapper, kiểm pointcut, Reflection tùy cách triển khai | Mã đã chèn, dispatch/helper và advice tương ứng |
| Bộ nhớ | Proxy instances, handler chain, cached metadata | Class bytes biến đổi, metadata, helper/instrumentation state |
| JIT | Inlining phụ thuộc shape và số tầng dispatch | JIT có thể tối ưu nhưng code instrumentation cũng có chi phí |
| I/O | Ghi log/metrics/network có thể là phần chi phí lớn nhất | Tương tự |
| Độ phủ | Giới hạn ở đường gọi proxy | Giới hạn ở class/join point thực sự được woven |

Nếu pointcut quá rộng, cả hai cách đều có thể khiến hot path chậm. Nên đo overhead trên workload thật, có warm-up, đo số lần advice thực thi và tình huống đồng thời. Không tuyên bố một mô hình “luôn nhanh hơn” mô hình khác nếu chưa có benchmark tương ứng.

**Trùng instrumentation** là rủi ro khi kết hợp proxy và weaver: một lượt operation có thể chạy hai audit/metrics. Cần thống nhất policy ownership và đo số lần target thực thi độc lập với số lần advice.

## 18. Quy trình kiểm thử độ phủ đáng tin

Một phép kiểm thử chỉ gọi `adviceMethod()` trực tiếp có thể xác minh logic của advice, nhưng **không chứng minh hệ thống AOP thật sự áp dụng nó**. Hãy có integration test đi qua đúng ranh giới.

### 18.1. Ma trận kiểm thử

| Ca thử | Kỳ vọng với interface proxy minh họa | Điều cần xác minh với weaving |
| --- | --- | --- |
| `proxy.work("A")` | Handler `work` chạy một lần | Cùng invocation có thể còn woven advice nếu class được dệt |
| `proxy.internally("B")` | Không có handler `work` trên `this.work()` | Execution `work` đã woven có thể chạy |
| `target.work("C")` | Không có handler proxy | Execution đã woven có thể chạy |
| Method `final` qua interface proxy | Handler vẫn thấy interface invocation | Bytecode method execution tùy engine |
| Private helper | Proxy không intercept trực tiếp | Weaving private execution nếu hỗ trợ |
| Static method | Proxy instance không intercept trực tiếp | Weaving static join point nếu hỗ trợ |
| Constructor | Proxy không thấy construction của target trước đó | Weaving constructor join point nếu hỗ trợ |
| Field access | Proxy không thấy trực tiếp | Weaving field get/set nếu hỗ trợ |
| Target ném exception | Handler thấy exceptional exit của lời gọi đã chọn | Advice throwing tại đúng join point có thể chạy |
| Internal exception được catch | Handler ngoài có thể chỉ thấy normal return | Inner exception points nếu engine hỗ trợ |
| Proxy bị bypass | Không có advice của proxy | Weaving coverage không phụ thuộc proxy nếu callee dệt |
| Two pointcuts match | Tùy handler chain được lắp | Có thể có nhiều advice hợp lệ, cần đếm |
| Future trả pending | Chỉ thấy handle được return | Method weaving cũng không tự chứng minh task completion |

### 18.2. Các assertion cần thiết

- **Matching:** join point được chọn và không được chọn hoạt động đúng.
- **Interception:** method thật có đi qua proxy hoặc bytecode đã woven không?
- **Behavior preservation:** kết quả trả về, ngoại lệ gốc và side effect nghiệp vụ giữ nguyên nếu advice chỉ quan sát.
- **Call count:** advice entry và target execution count được đo **riêng**.
- **Ordering:** hai advice cùng chọn một điểm có thứ tự như hợp đồng engine.
- **Class identity:** target runtime class có đúng loader, version, artifact được instrument không?
- **Lifecycle:** với LTW, classload/transformer thực sự xảy ra vào thời điểm mong đợi.
- **Negative coverage:** lời gọi không qua proxy phải không bị ghi nhầm là đã intercept; những method ngoài weaving scope không được coi là đã bảo vệ.

### 18.3. Thử nghiệm đối chứng

Có thể chạy một test harness theo các trạng thái:

~~~text
Case A: target raw, không bytecode weaving
Case B: proxy(raw target)
Case C: woven target, gọi trực tiếp
Case D: proxy(woven target)

Đếm riêng:
- số lần business chạy
- số proxy handler entries
- số woven advice entries
- kết quả caller
- exception type
~~~

Đây là thiết kế kiểm thử giả định, **không phải bốn chế độ có sẵn** của ví dụ Java. Ở `Case D`, cùng chính sách được cài cả proxy và woven code có thể sinh event trùng; nếu chủ đích khác nhau, cần phân biệt event semantics.

## 19. Checklist lựa chọn kiến trúc theo độ phủ

Trước khi chọn cơ chế AOP, tự hỏi:

1. **Cần quan sát những loại join point nào?** Public API calls hay cả private/static/constructor/field/exception handler?
2. **Đường gọi đi qua đâu?** Mọi caller có thể được buộc dùng một proxy đáng tin không?
3. **Có self-invocation không?** Method nội bộ cần advice không?
4. **Cần can thiệp class không sở hữu source không?** Có quyền/capability biến đổi bytecode không?
5. **Cần chọn call site hay method execution?** Muốn hạn chế theo caller hay bao mọi lần callee chạy?
6. **Modifier có quan trọng không?** Final/private/static/constructor nằm trong scope chứ?
7. **Có nhiều classloader/module không?** Cùng class name có thể được nạp nhiều bản?
8. **Khi nào instrumentation chạy?** Build, classload hoặc runtime proxy?
9. **Có yêu cầu hot update không?** Policy runtime khác với retransform class đã load.
10. **Các advice có thay đổi luồng không?** Retry/caching/authorization yêu cầu contract và test khác logging.
11. **Có yêu cầu bảo mật/audit bắt buộc không?** Đã loại mọi đường bypass chưa?
12. **Làm sao chứng minh coverage?** Integration test + negative test + production observability.
13. **Nếu weaving thất bại thì sao?** Fail-open/fail-closed theo chính sách.
14. **Có trùng tầng không?** Proxy và woven code có đang áp cùng một logical event hai lần?
15. **Chi phí vận hành nào chấp nhận được?** Build complexity, agent compatibility, debugging hay per-call overhead?

Chỉ chọn proxy khi **ranh giới API được kiểm soát** đủ thỏa yêu cầu; chọn biến đổi mã khi cần những điểm sâu hơn và có khả năng kiểm chứng đúng scope. Cũng có thể kết hợp hai mô hình, nhưng cần bản đồ instrumentation và quy tắc tránh duplicate policy.

## 20. Những lỗi hiểu sai phổ biến

1. **“Đã khai báo pointcut khớp tên method là chắc chắn có advice.”** Sai: còn phải có đường proxy hoặc class/join point thực sự woven.
2. **“Có proxy thì mọi method của target được chặn.”** Sai: interface/override/scope và đường gọi giới hạn độ phủ.
3. **“Self-invocation luôn đi qua proxy.”** Sai trong wrapper bên ngoài như ví dụ.
4. **“Final method không bao giờ intercept được.”** Sai: interface proxy vẫn có thể nhận call đến implementation `final`.
5. **“Bytecode weaving không thể áp dụng cho private method.”** Không có quy tắc chung; phụ thuộc engine/pointcut.
6. **“Proxy tự nhìn constructor/field access.”** Không: method proxy thông thường không chặn các opcode ấy.
7. **“Weaving chắc chắn instrument mọi constructor và field.”** Sai: phải xem mô hình join point và phạm vi thực.
8. **“Method call và execution là cùng một điểm.”** Sai: vị trí caller và callee khác nhau.
9. **“Chỉ cần có `-javaagent` là mọi class được dệt.”** Sai: timing, filters, classloader và capability ảnh hưởng.
10. **“Tắt agent sẽ tự tháo advice khỏi class đã load.”** Không mặc định; phụ thuộc redefinition/restart.
11. **“Proxy biến đổi bytecode target mỗi lần gọi.”** Không phải hoạt động bắt buộc của proxy; JDK `Proxy` có thể chỉ là wrapper.
12. **“Nhìn hai log chứng minh double weaving.”** Không: có thể hai join points, hai proxies, retry hoặc redelivery ở sink.
13. **“Mọi exception nội bộ đều được after-throwing của proxy nhìn thấy.”** Sai nếu target đã catch trước khi thoát ra.
14. **“Future return đồng nghĩa task đã hoàn tất.”** Sai: cần quan sát completion riêng.
15. **“Unit test của advice chứng minh coverage.”** Không: phải test đường can thiệp thật.
16. **“Weaving luôn nhanh hơn proxy.”** Không có kết luận chung khi chưa benchmark.
17. **“Bảo mật có thể đặt hoàn toàn ở một proxy dễ bypass.”** Nguy hiểm nếu người gọi có thể giữ target raw.
18. **“Tên method là đủ xác định join point.”** Không: overload, bridge/synthetic, declaring type và kind cũng có ý nghĩa.

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Một JDK proxy bọc `Service`; caller gọi thẳng `target.work()`. Handler có chạy không?  
**Đáp án:** Không qua handler của proxy đó. Caller đã bỏ qua đối tượng trung gian.

**Câu 2.** `Target.work()` khai báo `final`, nhưng implements method của `Service`. Proxy interface có quan sát call `proxy.work()` được không?  
**Đáp án:** Có thể. Nó nhận interface invocation trước khi gọi target; không cần subclass override final method.

**Câu 3.** `proxy.internally()` chuyển tới target, target gọi `this.work()`. Vì sao không có proxy event của `work`?  
**Đáp án:** Đây là self-invocation trên target, không đi qua tham chiếu proxy ở lời gọi nội bộ.

**Câu 4.** Một class đã woven ở `execution(work)`. `this.work()` có thể kích hoạt advice không?  
**Đáp án:** Có, nếu bytecode thực thi của `work` đã được biến đổi đúng và engine hỗ trợ điểm ấy.

**Câu 5.** Bytecode weaving có luôn thấy constructor, private method, field write không?  
**Đáp án:** Không. Phải kiểm tra join point model, class scope, transformer và giới hạn JVM/công cụ.

**Câu 6.** Tại sao một proxy không thấy exception đã bị target tự catch?  
**Đáp án:** Vì lời gọi ngoài có thể hoàn tất bình thường; exception không thoát khỏi ranh giới mà handler quan sát.

**Câu 7.** Khi `call` và `execution` advice đều chạy, có chắc dệt lặp không?  
**Đáp án:** Không. Chúng là hai loại join point; có thể cùng được chọn hợp lệ.

**Câu 8.** Chạy Java agent sau khi target class đã load thì LTW thông thường có chắc áp dụng không?  
**Đáp án:** Không; có thể cần khả năng retransformation riêng và vẫn bị giới hạn của JVM/tool.

**Câu 9.** Hai lớp audit cùng chạy khi proxy bọc một target đã woven. Nên làm gì?  
**Đáp án:** Xác định policy ownership, tầng và đơn vị event; loại trừ trùng ngoài ý định nhưng giữ những advice khác mục đích.

**Câu 10.** Điều nào chứng minh an toàn cho một policy quan trọng: test hàm advice hay integration test đường gọi?  
**Đáp án:** Cần integration test qua đường instrumentation thật, cả đường bypass/không khớp và các loại join point quan trọng; unit test advice là cần nhưng không đủ.

## 22. Tổng kết

**Khác biệt căn bản giữa AOP qua proxy và biến đổi mã là vị trí đặt điểm can thiệp.** Proxy nhìn thấy **lời gọi qua đối tượng trung gian**; weaving/instrumentation có thể đặt logic tại **call site, method execution hoặc các join point sâu hơn** nếu công cụ hỗ trợ và class thực sự được biến đổi. Vì vậy, self-invocation, direct target calls, `private`, `final`, `static`, constructor và field access có thể có độ phủ khác nhau.

Không có mô hình nào tự bảo đảm “bao mọi thao tác”. Để chọn đúng, hãy xác định join point cần kiểm soát, đường đi thực tế, modifier, classloader/lifecycle, policy lỗi và yêu cầu bảo mật. **Kiểm thử integration với cả đường được chọn và đường bypass** là bằng chứng quan trọng nhất; đánh giá thêm performance, double instrumentation và tính tương thích runtime để advice vận hành đúng ý định mà không thay đổi kết quả, ngoại lệ hoặc số lần thực thi nghiệp vụ ngoài hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-based-aop-009">Chẩn đoán tình huống lời gọi không kích hoạt advice</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán tình huống lời gọi không kích hoạt advice trong AOP qua proxy

Trong **AOP qua proxy**, advice chỉ có cơ hội chạy khi **lời gọi thực sự đi qua một ranh giới proxy phù hợp**, cơ chế chặn nhận được phương thức, các điều kiện lựa chọn khớp, và đường điều khiển đi đến advice ấy. Khi một chương trình chạy đúng nghiệp vụ nhưng không thấy dòng log `before` hoặc `after`, ta **không thể lập tức kết luận “AOP bị hỏng”**. Có thể caller đang giữ target gốc, một lời gọi nội bộ không qua proxy, pointcut chọn sai chữ ký, interceptor bị tắt, around advice bên ngoài trả sớm, hoặc advice chỉ chạy ở nhánh thành công trong khi nghiệp vụ ném lỗi.

Bài này xây dựng **phương pháp chẩn đoán từ ngoài vào trong**, phân biệt các nguyên nhân của sự kiện “advice không kích hoạt” và cách kiểm chứng bằng Java 17. Các ví dụ tập trung vào **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP) nói chung**; Java dynamic proxy là phương tiện minh họa có thể chạy độc lập. Khả năng chặn method call, method execution, private/final/static methods, trình tự precedence và cách triển khai proxy có thể khác nhau theo công cụ.

## 1. Trước khi sửa: xác định điều gì thực sự “không chạy”

Cụm từ “advice không chạy” dễ gom lẫn các hiện tượng rất khác nhau:

1. **Không qua proxy:** caller đi thẳng vào target, nên handler/interceptor không có cơ hội nhận lời gọi.
2. **Qua proxy nhưng không được chọn:** handler nhận method, song pointcut hoặc bộ lọc method trả false.
3. **Qua proxy và khớp chữ ký nhưng sai ngữ cảnh:** tham số, target, đối tượng đang gọi hoặc trạng thái runtime không thỏa điều kiện động.
4. **Advice có tồn tại nhưng không đi tới:** một lớp bên ngoài đã `return`, ném lỗi hoặc short-circuit trước khi gọi continuation.
5. **Chỉ nhầm thời điểm hoặc loại advice:** `after returning` không chạy khi có ngoại lệ; một `after throwing` không chạy trên nhánh thành công.
6. **Advice chạy nhưng người quan sát không thấy:** log level tắt, bộ ghi bất đồng bộ bị trì hoãn, trace bị lấy mẫu, sai cửa sổ tìm kiếm hoặc logger bị lỗi.
7. **Sai phiên bản artifact/cấu hình:** JVM đang dùng proxy/handler hoặc class loader cũ, không phải cấu hình mới vừa build.
8. **Sai kỳ vọng về phạm vi proxy:** JDK interface proxy không tự biến mọi điểm thực thi bên trong object đích thành một join point chặn được.

Thay vì thay pointcut ngẫu nhiên, nên xác nhận từng tầng bằng một **dấu hiệu kiểm chứng tạm thời, an toàn về dữ liệu**, từ lời gọi bên ngoài tới advice và target.

## 2. Cây quyết định chẩn đoán

~~~text
Bắt đầu: business method có được gọi?
          |
          v
Caller đang gọi object nào?
          |
          +-- target gốc ---------> không qua proxy -> sửa đường tham chiếu
          |
          +-- proxy
                 |
                 v
      Handler có nhận invocation?
                 |
                 +-- không -------> sai proxy/interface/classloader/
                 |                  kiểu method hoặc đường dispatch
                 |
                 +-- có
                        |
                        v
             Pointcut / filter khớp?
                        |
                        +-- không --> kiểm tra tên, overload, declaring
                        |            type, annotation, call/execution
                        |
                        +-- có
                               |
                               v
                   Runtime condition đúng?
                               |
                               +-- không --> args/target/context/flag
                               |
                               +-- có
                                      |
                                      v
                          Có được gọi tới advice?
                                      |
                                      +-- không --> precedence, short-circuit,
                                      |            nhánh lỗi, interceptor order
                                      |
                                      +-- có
                                             |
                                             v
                          Advice có phát tín hiệu?
                                             |
                                             +-- không --> logger, sampling,
                                             |            async exporter
                                             |
                                             +-- có --> kiểm tra sink/traces
~~~

Đây là cây khái niệm. Một số cơ chế kết hợp pointcut và handler tại nhiều pha khác nhau; vì vậy cần ánh xạ các bước vào pipeline thật, không buộc mọi công cụ phải có đúng các hàm như trong sơ đồ.

**Một dấu hiệu quan trọng:** nếu target in `BUSINESS` nhưng handler không in `PROXY received`, lỗi không nằm ở câu lệnh bên trong advice. Trước tiên cần kiểm tra **đối tượng được gọi** và phạm vi interception.

## 3. Phân biệt target, proxy và tham chiếu thực tế của caller

Trong mô hình phổ biến:

~~~text
Client -> Proxy -> Interceptor/handler -> Target
~~~

Proxy là **đối tượng trung gian**. Target là **đối tượng nghiệp vụ**. Hai đối tượng có thể cùng triển khai một interface, nhưng **khác identity**. Caller có thể giữ tham chiếu proxy hoặc target gốc; chỉ trường hợp đầu mới chắc chắn đi qua ranh giới proxy này.

Ví dụ giả mã:

~~~java
Task original = new TaskImpl();
Task wrapped = createProxy(original);

wrapped.execute("A");  // Qua proxy: có cơ hội kích hoạt advice.
original.execute("B"); // Đi thẳng target: bỏ qua advice proxy.
~~~

Đây là mảnh minh họa, không phải một chương trình độc lập; `Task`, `TaskImpl`, `createProxy` cần được khai báo tương ứng.

### 3.1 Đừng chỉ dựa vào kiểu biến được khai báo

Hai biến `Task` có thể trỏ tới object runtime khác nhau. Cần kiểm tra **tham chiếu mà code dùng tại điểm gọi**. Với dynamic proxy Java, `Proxy.isProxyClass(object.getClass())` có thể giúp xác nhận một object thuộc dạng JDK dynamic proxy; kết quả false **không chứng minh** object không phải proxy của cơ chế khác (chẳng hạn subclass proxy).

Nếu đang debug production, không in bừa `object.toString()`: toString của proxy có thể lại bị intercept và tạo recursion; toString của target có thể làm lộ secret. Một thông tin kỹ thuật giới hạn như phân loại "proxy/target" và tên thao tác đã duyệt thường đủ.

### 3.2 Có thể proxy đã được tạo nhưng chưa được dùng

Một lỗi lắp ghép phổ biến:

~~~text
create target
create proxy wrapping target
store target in client
discard or never use proxy
~~~

Build/config vẫn có "proxy enabled" nhưng caller gọi object gốc. Để sửa, cần kiểm tra nơi đối tượng được tạo, nơi nó được truyền hoặc gán vào các thành phần khác, và **đối tượng nào thật sự được trả về** sau bước khởi tạo. Nếu cần một chính sách bắt buộc, không nên phát tán tham chiếu target gốc cho những caller có thể bypass.

## 4. Self-invocation: gọi từ target vào chính nó

Một trường hợp đặc biệt quan trọng:

~~~java
class Handler {
    void outer() {
        inner();
    }

    void inner() {
        // Business logic
    }
}
~~~

Đây là mảnh minh họa. Giả sử proxy dùng **delegation sang một instance target riêng**:

1. Client gọi `proxy.outer()`.
2. Proxy intercept `outer` rồi chuyển tiếp tới `target.outer()`.
3. Trong `target.outer()`, lời gọi `inner()` thường là `this.inner()` trên **target**, không qua `proxy`.
4. Nếu advice chỉ được lắp trên proxy đối với `inner`, lời gọi nội bộ có thể **không kích hoạt advice**.

Sơ đồ:

~~~text
Client --outer()--> Proxy --outer()--> Target.outer()
                                       |
                                       +--this.inner()--> Target.inner()
                                                           (không quay proxy)
~~~

Lỗi này thường bị hiểu nhầm thành "pointcut `inner` không khớp". Thực ra có thể **không có join point proxy invocation cho `inner`** trong đường thực thi này.

**Không tuyệt đối hóa self-invocation cho mọi proxy.** Một proxy tạo bằng subclass và thực thi phương thức trên chính instance dẫn xuất có thể có virtual dispatch tới override, khác với mô hình delegation sang target riêng. Ngoài ra, weaver biến đổi bytecode tại method execution có thể quan sát các điểm mà proxy không quan sát. Luôn xác minh mô hình proxy và ranh giới "this" của chính công cụ đang sử dụng.

### 4.1 Cách khắc phục theo kiến trúc

- Đưa trách nhiệm cần advice vào một **thành phần được gọi qua proxy riêng**, nếu việc tách tầng phù hợp nghiệp vụ.
- Đảm bảo caller đi qua **tham chiếu proxy được quản lý** thay vì gọi trực tiếp target.
- Nếu mô hình cho phép, tổ chức lời gọi nội bộ thông qua một tham chiếu trung gian rõ ràng, nhưng tránh vòng lặp phụ thuộc hoặc lộ object gốc.
- Với các điểm bắt buộc không thể đi qua proxy, cân nhắc một **cơ chế weaving thích hợp** hoặc đặt bất biến nghiệp vụ ở nơi không phụ thuộc vào advice proxy.
- Không ép self-invocation quay lại proxy bằng kỹ thuật ẩn làm người đọc khó hiểu, nhất là với chính sách bảo mật; ưu tiên ranh giới thiết kế rõ ràng.

## 5. Handler nhận lời gọi nhưng pointcut không khớp

Nếu có bằng chứng `InvocationHandler` đã nhận invocation, bước tiếp theo là **đánh giá chính xác phương thức và điều kiện match**.

Các lỗi thường gặp:

| Nguyên nhân | Ví dụ | Cách kiểm tra |
| --- | --- | --- |
| Sai tên method | Chọn `save` nhưng lời gọi thực tế là `store` | So sánh tên method thật, không đoán theo endpoint |
| Overload khác | `save(String)` so với `save(Object)` | Xem parameter types và descriptor |
| Sai declaring type | Handler nhận `Method` của interface, bộ lọc đòi implementation class | Kiểm tra `method.getDeclaringClass()` và target runtime class riêng |
| Sai loại join point | Pointcut mô tả execution nhưng cơ chế hiện tại chỉ chặn proxy invocation | Xác minh mô hình được hỗ trợ |
| Annotation không ở chỗ đọc | Annotation khai báo trên implementation, code kiểm tra Method của interface | Đọc metadata đúng method; không mặc định annotation method được kế thừa |
| Chọn package/type quá hẹp | Lớp target không nằm trong nhóm khai báo | Kiểm tra phạm vi cấu trúc |
| Regex/biểu thức khác kỳ vọng | Mẫu tên bỏ sót hậu tố, overload hoặc lớp được sinh | Viết kiểm thử tập điểm match/không match |
| Kiểu động khác kiểu khai báo | Biến interface nhận implementation khác | Phân biệt `Method` tĩnh với `target.getClass()` |
| Generic erasure/bridge | Descriptor sau compile không giống tham số generic nhìn từ source | Xác minh quy tắc match và cách xử lý synthetic/bridge |
| Loại trừ chủ ý | Bộ lọc loại generated methods/namespace hạ tầng | Kiểm tra danh sách exclude được công bố |

### 5.1 Interface Method và implementation Method

Trong **JDK dynamic proxy**, handler thường nhận `Method` tương ứng khai báo ở **giao diện proxy**; nó không tự là `Method` khai báo bởi implementation class. Nếu điều kiện viết như sau:

~~~java
if (method.getDeclaringClass() == RealService.class) {
    // Có thể không khớp khi method đến từ interface.
}
~~~

Đây là mảnh minh họa, giả sử `RealService` đã tồn tại. Với lời gọi `interfaceService.run()` qua JDK proxy, `method.getDeclaringClass()` thường trả interface khai báo `run`. Điều kiện so sánh trực tiếp với `RealService.class` có thể false dù object target là RealService.

Một chiến lược chẩn đoán đúng hơn là **đọc cả hai** thông tin:
- `method.getDeclaringClass()` nói về nguồn khai báo của `Method`.
- `target.getClass()` nói về lớp runtime của đối tượng target mà handler giữ.
- Nếu cần method implementation thực sự, tra cứu theo tên/parameter types trên lớp thích hợp, lưu ý override, visibility, bridge và metadata.

### 5.2 Annotation không phải lúc nào cũng xuất hiện như kỳ vọng

Annotation gắn trên **phương thức interface** không tự mặc nhiên xuất hiện khi đọc một `Method` của implementation, và ngược lại. Annotation `@Inherited` của Java chỉ có ngữ nghĩa kế thừa dành cho **class declarations**, không khiến annotation ở method/parameter tự được kế thừa như class annotation.

Nếu pointcut phụ thuộc annotation:
1. Xác định metadata được đặt ở interface, base class, implementation hay parameter.
2. Xác định proxy handler/weaver đọc `Method` nào.
3. Kiểm tra retention và runtime visibility.
4. Kiểm tra cơ chế xử lý override/bridge nếu có.
5. Thêm test cho cả một trường hợp phải match và một trường hợp không match.

Đừng mở rộng pointcut "bắt tất cả" để che một sai lầm annotation; điều đó có thể làm advice chạy ở những vị trí không mong muốn.

## 6. Giới hạn theo loại proxy: interface, subclass và phương thức đặc biệt

**JDK dynamic proxy** là proxy dựa trên một hoặc nhiều interface. Nó nhận lời gọi tới những phương thức giao diện thích hợp qua `InvocationHandler`; nó **không tự tạo subclass của một concrete class bất kỳ**.

Các giới hạn chẩn đoán:

- **Phương thức chỉ tồn tại trên implementation**, không được công bố qua interface proxy: caller không thể gọi nó trực tiếp qua reference giao diện như một method interface.
- **Private method:** không phải một public instance interface method để JDK dynamic proxy chặn theo cách thông thường.
- **Static method:** gọi trên lớp, không dispatch thông qua object proxy instance theo nghĩa trên.
- **Constructor:** gọi `new Target()` không tự kích hoạt JDK proxy handler.
- **Final class:** vẫn có thể là underlying target của JDK interface proxy nếu nó triển khai interface phù hợp; tính `final` cản **subclassing**, không mặc định cản **interface delegation**.
- **Final method:** hạn chế override trong subclass proxy; nhưng một interface proxy độc lập vẫn có thể **chuyển tiếp** một lời gọi giao diện tới final method implementation nếu tương thích.
- **Default method của interface:** cách dispatch và xử lý method có thể phụ thuộc cách handler tiến hành chuyển tiếp; cần kiểm chứng riêng.
- **`equals`/`hashCode`/`toString`:** những method này có thể được dispatch tới JDK InvocationHandler, nhưng bộ lọc advice có thể chủ động loại chúng.

Một proxy dựa trên **subclass** có ràng buộc khác đối với `final`, `private`, visibility, module boundaries và dispatch nội bộ. Do đó, câu chẩn đoán phải bắt đầu bằng **“đây là proxy loại nào?”**, chứ không bằng quy tắc chung rằng mọi final method đều không được AOP.

## 7. Điều kiện runtime sai dù chữ ký đúng

Một điểm có thể vượt qua bộ chọn cấu trúc nhưng vẫn không thỏa ngữ cảnh:

- `args` thực tế khác giá trị mong muốn.
- Target runtime type không thuộc nhóm mà condition chọn.
- Bộ lọc chỉ bật với một tenant/môi trường/cờ tính năng và hiện đang tắt.
- Dữ liệu context gắn với thread không có trong thread thực thi hiện tại.
- Pointcut theo control flow không khớp đường gọi thật.
- Một validator thu hẹp phạm vi sau khi handler đã tiếp nhận.
- Điều kiện phụ thuộc một snapshot config cũ hoặc thay đổi không được công bố đúng cách giữa nhiều thread.

Ví dụ:

~~~text
matchesStructure = method == "submit(String)"
matchesContext   = enabled AND level == CRITICAL

selected = matchesStructure AND matchesContext
~~~

Đây là **giả mã**. Dù method submit được gọi qua proxy, advice không chạy khi `enabled=false` hoặc `level != CRITICAL` là hành vi **theo đúng thiết kế**, không phải lỗi weaving.

### 7.1 Kiểm tra giá trị mà không làm rò dữ liệu

Không mặc định in toàn bộ `args` để debug; tham số có thể chứa token, password, địa chỉ hoặc dữ liệu riêng tư. Thay vào đó, dùng event nhỏ:

~~~text
operation=submit
via_proxy=true
structural_match=true
runtime_match=false
reason=DISABLED
~~~

Không đưa raw args, response hoặc stack-trace message vào log khi chưa phân loại. Một advice ghi log nhạy cảm có thể tạo sự cố bảo mật nghiêm trọng hơn vấn đề kích hoạt ban đầu.

### 7.2 Kiểm tra cờ bật/tắt đúng thời điểm

Nếu proxy đọc cờ một lần **lúc khởi tạo** thay vì ở mỗi invocation, cập nhật config sau đó không nhất thiết có hiệu lực. Ngược lại, một handler đọc `AtomicBoolean` hoặc snapshot bất biến theo lời gọi có thể thay hành vi advice ngay cả khi proxy không tạo lại.

Cần phân biệt:
- **Configuration value mới đã tồn tại ở kho config?**
- **Proxy hiện tại đã đọc được value mới chưa?**
- **Điều kiện match hiện tại có dùng snapshot đó chưa?**

Không kết luận "reload thành công" chỉ vì tệp cấu hình đã cập nhật.

## 8. Advice bên ngoài short-circuit hoặc một nhánh khác được chọn

Nhiều proxy/interceptor có thể lồng nhau:

~~~text
Caller
  |
  v
CacheInterceptor
  |
  v
AuthorizationInterceptor
  |
  v
MetricsInterceptor
  |
  v
Target
~~~

Nếu CacheInterceptor trả kết quả từ cache **mà không gọi tiếp**, Authorization và Metrics bên trong có thể **không chạy**. Khi đó không phải pointcut của chúng sai; **control flow không đi tới chúng**.

Những nguyên nhân khác:
- Before advice bên ngoài ném lỗi.
- Around advice ngoài không gọi `proceed`.
- Retry interceptor chỉ tiếp tục trong một vài điều kiện.
- Circuit breaker từ chối ngay khi chưa đi tới target.
- Fallback trả sớm sau lỗi ở một lớp trước.
- Handler chỉ tạo một adapter khác và chuyển sang pipeline không mang advice dự kiến.

### 8.1 After-returning không phải finally

Giả sử code minh họa:

~~~text
before()
try:
    result = proceed()
    afterReturning(result)
    return result
catch error:
    afterThrowing(error)
    throw error
finally:
    afterFinally()
~~~

Đây là **giả mã cách tổ chức một wrapper**, không phải định nghĩa thứ tự advice phổ quát của mọi công cụ.

Nếu target ném ngoại lệ:
- Before có thể đã chạy.
- `afterReturning` **không chạy**, vì không có lần trả về bình thường.
- `afterThrowing` có thể chạy theo hợp đồng.
- `finally` của phạm vi đã được vào vẫn chạy.

Vì thế, câu “after advice không chạy” phải được xác định rõ là **after-returning, after-throwing hay finally**; một đoạn log chỉ đặt ở nhánh thành công sẽ không xuất hiện khi target lỗi.

### 8.2 Precedence làm advice nằm ở đâu?

Thứ tự thêm wrapper có thể khác thứ tự vào. Ví dụ:

~~~text
chain = Target
chain = Metrics(chain)
chain = Cache(chain)

effective = Cache(Metrics(Target))
~~~

Cache được thêm **sau** nhưng nằm **ngoài**. Khi cache hit, Metrics bị bỏ qua. Cần quan sát cấu trúc chain có hiệu lực, không suy từ tên file, thứ tự định nghĩa hoặc thứ tự khởi tạo nếu công cụ không bảo đảm.

## 9. Java 17 hoàn chỉnh: một chương trình, năm nguyên nhân

Chương trình sau dùng **JDK dynamic proxy** với:
- `WorkImpl` là target;
- `Work` là interface;
- proxy `InvocationHandler` in dấu **PROXY received** cho mọi lời gọi interface;
- pointcut minh họa chỉ chọn `submit(String)`;
- `AtomicBoolean` là điều kiện runtime bật/tắt advice;
- target có `outer()` gọi `submit()` **trên chính nó**;
- lời gọi trực tiếp tới target; nhánh success và exception.

Tạo một file duy nhất tên `ProxyAdviceDiagnosticsDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyAdviceDiagnosticsDemo {
    interface Work {
        String submit(String id);
        String outer(String id);
        String status();
    }

    static final class WorkImpl implements Work {
        @Override
        public String submit(String id) {
            System.out.println("BODY submit " + id);
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("invalid id");
            }
            return "saved:" + id;
        }

        @Override
        public String outer(String id) {
            System.out.println("BODY outer " + id);
            // this là target WorkImpl, không phải proxy.
            return "outer>" + this.submit(id);
        }

        @Override
        public String status() {
            System.out.println("BODY status");
            return "OK";
        }
    }

    static Object callTarget(
            Work target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Work wrap(Work target, AtomicBoolean enabled) {
        return (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[] { Work.class },
                (proxy, method, args) -> {
                    Object[] values =
                            args == null ? new Object[0] : args;

                    // Object methods có hợp đồng riêng.
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "WorkProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == values[0];
                            default -> throw new UnsupportedOperationException();
                        };
                    }

                    System.out.println(
                            "PROXY received " + method.getName());

                    boolean matches =
                            method.getName().equals("submit")
                            && Arrays.equals(method.getParameterTypes(),
                                    new Class<?>[] { String.class });

                    if (!matches) {
                        System.out.println("POINTCUT no match");
                        return callTarget(target, method, values);
                    }

                    if (!enabled.get()) {
                        System.out.println("ADVICE disabled");
                        return callTarget(target, method, values);
                    }

                    System.out.println("ADVICE before submit");
                    try {
                        Object result =
                                callTarget(target, method, values);
                        System.out.println("ADVICE after submit");
                        return result;
                    } catch (Throwable error) {
                        System.out.println("ADVICE error "
                                + error.getClass().getSimpleName());
                        throw error;
                    }
                });
    }

    public static void main(String[] args) {
        WorkImpl original = new WorkImpl();
        AtomicBoolean enabled = new AtomicBoolean(true);
        Work proxy = wrap(original, enabled);

        System.out.println("PROXY_CLASS "
                + Proxy.isProxyClass(proxy.getClass()));

        System.out.println("CASE through-proxy");
        System.out.println("RESULT " + proxy.submit("A"));

        System.out.println("CASE direct-target");
        System.out.println("RESULT " + original.submit("B"));

        System.out.println("CASE self-invocation");
        System.out.println("RESULT " + proxy.outer("C"));

        System.out.println("CASE nonmatching-method");
        System.out.println("RESULT " + proxy.status());

        enabled.set(false);
        System.out.println("CASE dynamic-disabled");
        System.out.println("RESULT " + proxy.submit("D"));

        enabled.set(true);
        System.out.println("CASE business-failure");
        try {
            proxy.submit("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac ProxyAdviceDiagnosticsDemo.java
java ProxyAdviceDiagnosticsDemo
~~~

Stdout theo logic chương trình:

~~~text
PROXY_CLASS true
CASE through-proxy
PROXY received submit
ADVICE before submit
BODY submit A
ADVICE after submit
RESULT saved:A
CASE direct-target
BODY submit B
RESULT saved:B
CASE self-invocation
PROXY received outer
POINTCUT no match
BODY outer C
BODY submit C
RESULT outer>saved:C
CASE nonmatching-method
PROXY received status
POINTCUT no match
BODY status
RESULT OK
CASE dynamic-disabled
PROXY received submit
ADVICE disabled
BODY submit D
RESULT saved:D
CASE business-failure
PROXY received submit
ADVICE before submit
BODY submit bad
ADVICE error IllegalArgumentException
CALLER invalid id
~~~

### 9.1 Đọc output như một bằng chứng chẩn đoán

| Ca | Có `PROXY received`? | Điều kiện chọn | Có advice? | Kết luận |
| --- | --- | --- | --- | --- |
| `through-proxy` | Có, cho submit | Match và enabled | Có, before/after | Đường bình thường |
| `direct-target` | Không | Không được đánh giá tại proxy | Không | Bypass proxy |
| `self-invocation` | Có cho outer; **không cho submit bên trong** | Outer không match | Không cho submit nội bộ | Self-invocation trên target |
| `nonmatching-method` | Có, cho status | Sai tên/chữ ký | Không | Pointcut/filter loại method |
| `dynamic-disabled` | Có, cho submit | Match nhưng cờ false | Không; chỉ dấu disabled | Điều kiện runtime loại advice |
| `business-failure` | Có, cho submit | Match và enabled | Before và error; không after-success | Nhánh exception |

**Tách ba loại chứng cứ:** `PROXY received` chứng minh handler đã nhận lời gọi; `POINTCUT no match` chứng minh loại trừ cấu trúc; `ADVICE disabled` chứng minh điều kiện động loại trừ. Không có `PROXY received` tại direct-target là dấu hiệu **đường gọi bypass proxy**, chứ không phải chứng minh pointcut không khớp.

### 9.2 Chương trình mô phỏng điều gì và không mô phỏng điều gì?

- **Có:** một proxy interface hoạt động thật trên JDK 17, phương thức target thật, reflection, so khớp chữ ký, cờ runtime, chuyển tiếp kết quả và ngoại lệ.
- **Không:** một ngôn ngữ pointcut chuẩn dùng cho mọi nền tảng hoặc weaving vào class bytecode.
- **Không:** lời gọi method-execution join point theo mọi công cụ AOP; đây là lời gọi đi qua JDK dynamic proxy.
- **Không:** xử lý quyền hoặc giao dịch production. Dòng `ADVICE` là tín hiệu kiểm thử, không phải hàng rào an ninh.
- **Không:** bảo đảm debug log có thể in mọi tham số. Mã chỉ in ID giả phục vụ bài học; với dữ liệu nhạy cảm thực, không dùng raw args hoặc raw error messages.
- **Không:** phân biệt mọi kịch bản subclass proxy, bởi final `WorkImpl` vẫn có thể được bọc bằng interface proxy ở đây.

### 9.3 Giải thích reflection exception

Trong `callTarget`, `Method.invoke` bọc lỗi ném từ thân method bằng `InvocationTargetException`. Nếu không tháo bọc, caller và nhánh error của handler có thể nhìn thấy **loại ngoại lệ khác** với hợp đồng nghiệp vụ. Ví dụ dùng `throw ex.getCause()` để truyền `IllegalArgumentException` gốc. Khi dùng JDK dynamic proxy, checked exceptions không được khai báo trong giao diện còn có thể bị bọc thành `UndeclaredThrowableException`; cần tôn trọng hợp đồng phương thức.

## 10. Chẩn đoán “handler có nhận nhưng không có log advice”

Trong một số hệ thống, handler đi qua nhiều lớp:

~~~text
Proxy
  |
  v
Invocation dispatcher
  |
  v
Pointcut matcher
  |
  v
Runtime condition
  |
  v
Advice chain
  |
  v
Target
~~~

Nếu chỉ thấy event từ dispatcher, cần đánh giá theo trình tự:
1. Matcher có nhận **đúng Method** không?
2. Method có cùng tên nhưng sai overload không?
3. Đây là method của interface hay implementation? Có annotation ở chỗ tương ứng không?
4. Điều kiện target/this/args/control flow có false không?
5. Interceptor có bị tắt bằng cấu hình/sampling không?
6. Có một wrapper khác short-circuit không?
7. Advice loại **before**, **after-returning**, **after-throwing** hay **finally**?
8. Trình ghi log có hoạt động/được xem đúng nơi không?

Để xác định rõ, dùng sự kiện với mã nguyên nhân hữu hạn:

~~~text
invocation-received
selection=STRUCTURE_FALSE
selection=CONTEXT_FALSE
selection=ENABLED
proceed-called
advice-enter
advice-success
advice-error
advice-finally
~~~

Các nhãn trên là **một schema debug đề xuất**, không phải API chung. Chỉ phát những nhãn thực tế phù hợp và có ràng buộc về chi phí, bảo mật.

### 10.1 Đừng chỉ nhìn log để kết luận advice không chạy

Một advice có thể chạy nhưng logger đang ở mức không xuất bản, trace bị sampling hoặc các event được gửi qua một exporter bất đồng bộ. Nếu đây là môi trường thử nghiệm đồng bộ, có thể tạm dùng **counter** hoặc một danh sách sự kiện trong bộ nhớ rồi assert số lần và thứ tự để phân biệt "không chạy" với "đã chạy nhưng không thấy log".

Không đưa dữ liệu riêng tư vào diagnostic trace. Khi debug xong cần gỡ hoặc hạ mức instrumentation để tránh overhead và rò dữ liệu.

## 11. Sau khi sửa code/cấu hình mà lỗi vẫn còn

**Thay source advice không tự bảo đảm proxy đang chạy dùng source mới.** Cần xác định:
- Proxy/handler có được xây dựng **trước khi** đổi cấu hình không?
- Handler dùng một snapshot cấu hình tại thời điểm khởi tạo hay đọc động theo invocation?
- Có nhiều instance hoặc nhiều process đang chạy phiên bản khác nhau không?
- Caller dùng object proxy phiên bản mới hay vẫn giữ tham chiếu cũ?
- Runtime classpath/class loader có tải nhầm phiên bản interface hoặc target không?
- Hệ thống có cache matcher/advice chain đã được làm mới khi cấu hình thay đổi chưa?
- Một bản build cập nhật có được triển khai vào process thực sự phục vụ lời gọi không?

Nếu một thành phần chỉ sử dụng cấu hình cố định tại lúc khởi tạo, có thể cần **tạo lại proxy hoặc redeploy**. Nếu JVM đang dùng class definition cũ, thay JAR trên đĩa không tự thay định nghĩa class đã nạp. Nhưng nếu advice đã được lắp và chỉ đọc một cờ động thì có thể đổi hành vi mà không tạo proxy mới; ví dụ `AtomicBoolean` trong chương trình minh họa trường hợp đó.

## 12. Một số bẫy nâng cao

### 12.1 Generic erasure và bridge

Java compiler có thể sinh bridge/synthetic methods để giữ dispatch generic. Một bộ quét `Method[]` có thể thấy nhiều method, nhưng không thể kết luận một bộ AOP sẽ tạo execution join point hoặc chạy advice thêm một lần cho mỗi bridge. Nếu matcher dựa vào kiểu khai báo/source, cần đọc quy tắc xử lý descriptor đã erase và bridge của cơ chế đang dùng.

### 12.2 Default method, Object methods và dispatch

JDK interface proxy nhận `equals`, `hashCode`, `toString` theo quy tắc của InvocationHandler. Nếu pointcut chỉ chọn method nghiệp vụ, việc không thấy advice khi gọi `toString()` có thể là **thiết kế loại trừ có chủ đích**. Trong chương trình mẫu, Object methods được handler xử lý trước logic match.

Đừng log `proxy.toString()` bên trong `toString` handler: có thể gọi lại handler và gây vòng lặp. Phương thức mặc định của interface cần được chuyển tiếp đúng ngữ nghĩa nếu muốn xử lý nó; không mọi handler đều tự có quy tắc giống nhau.

### 12.3 Phạm vi nhiều class loader và đối tượng nhìn giống nhau

Một loader có thể định nghĩa bản riêng của cùng tên class/interface; khi đó có thể gặp lỗi cast, module access hoặc proxy không nhìn đúng interface. Runtime object được tạo từ một class loader khác có thể không phải instance của Class object mà matcher đang dùng dù tên in ra giống nhau. Cần đối chiếu **class identity**, không chỉ class name.

### 12.4 Vòng lặp advice và đệ quy

Nếu trong handler có:

~~~text
on proxy.method():
    log(proxy.toString())
    return proxy.method() // tiếp tục qua chính proxy
~~~

thì rất có thể gọi lại chính handler. Chuyển tiếp đúng thường là target hoặc interceptor **kế tiếp**, không phải proxy hiện tại trừ khi cơ chế cố ý hỗ trợ tái nhập. Thêm breakpoint/event đánh dấu **depth** giúp phát hiện recursion nhưng phải giới hạn log để không làm đầy bộ nhớ.

### 12.5 Lỗi không biểu hiện thành exception ở đúng chỗ

Một phương thức có thể trả `Future`, `CompletionStage` hoặc handle tác vụ khác. Advice `after-returning` trên method tạo handle có thể chạy **khi handle được trả**, trước khi công việc bất đồng bộ hoàn tất. Nếu task lỗi sau đó, không thể mong cùng `after-throwing` đồng bộ tự ghi lỗi của tác vụ, trừ khi advice được gắn đúng vào cơ chế hoàn thành async.

### 12.6 Advice bị che do đặc thù logging

Một logger dùng mẫu không ổn định có thể lọc/mask mất một dòng, hoặc exporter rơi event vì backpressure. Để kiểm chứng không chạy, tốt nhất dùng test với **đếm invocation đã được chặn** thay vì suy từ một dòng log production.

## 13. Ma trận sửa lỗi nhanh

| Triệu chứng | Nghi vấn đầu tiên | Cách xác minh | Hướng xử lý |
| --- | --- | --- | --- |
| Target chạy nhưng handler im lặng | Bypass proxy | Kiểm tra object reference tại call site | Gọi qua proxy được quản lý |
| Proxy tồn tại nhưng caller vẫn có original | Lộ target | Theo dõi nơi cấp phát/gán tham chiếu | Đảm bảo đường vào chuẩn |
| Handler nhận outer, không nhận inner | Self-invocation trên target | In dấu tại outer/inner và handler | Tách ranh giới hoặc chọn weaving phù hợp |
| Handler nhận method nhưng advice không chạy | Sai pointcut/filter | So sánh signature, declaring type, annotation | Sửa selector chính xác |
| Interface method không thấy annotation | Metadata đặt trên implementation | Kiểm tra hai Method | Định nghĩa quy tắc annotation resolution |
| Advice chạy với overload A, không với B | Match không đủ chữ ký | Xem parameter types và overload | Thêm test cho từng overload |
| Chạy lúc enabled=true nhưng im khi false | Điều kiện runtime | Đo trạng thái cấu hình tại invocation | Thiết kế/ghi rõ flag semantics |
| Before chạy, after-success không chạy | Target ném lỗi | Quan sát error/finally branch | Không chờ after-returning trên error |
| Advice trong không chạy khi cache hit | Short-circuit lớp ngoài | Đối chiếu effective interceptor chain | Đặt advice bắt buộc ở ngoài cache hoặc bảo vệ tương đương |
| Method private/static/constructor không bị chặn | Ngoài khả năng interface proxy | Phân loại loại method và proxy | Chọn kỹ thuật weaving phù hợp |
| Final method không override được | Dùng subclass proxy | Kiểm tra final modifier | Thay interface proxy hoặc kiến trúc nếu thích hợp |
| Đổi pointcut mà runtime vẫn cũ | Handler/chain/cache phiên bản cũ | Kiểm tra artifact/config/loader | Rebuild/recreate/redeploy đúng mô hình |
| Log không thấy nhưng counter tăng | Sink/level/sampling | Assert sự kiện trong test | Kiểm tra telemetry pipeline |
| Advice chạy nhiều lần | Proxy lồng nhau/retry/nhiều join point | Đếm layer và invocation | Phân biệt request/attempt, xử lý precedence |
| Chỉ sai ở async | Sai kỳ vọng thời điểm hoàn tất | Phân biệt returned handle/completion | Gắn xử lý vào completion thích hợp |

## 14. Checklist chẩn đoán thực tế

Thực hiện theo trình tự để tránh sửa quá rộng:

- [ ] Ghi **method cần quan sát**, loại join point và kết quả kỳ vọng.
- [ ] Xác định **caller đang giữ proxy hay target** tại chính call site.
- [ ] Xác minh **loại proxy** (interface, subclass, custom wrapper) và phạm vi method nó có thể chặn.
- [ ] Thêm dấu vết an toàn, xác nhận **handler đã nhận invocation**.
- [ ] Nếu handler không nhận, kiểm tra direct call, self-invocation, factory/injection, loader và phiên bản runtime.
- [ ] Nếu handler nhận, so sánh chữ ký thật: method name, overload, declaring class, parameter types và annotation.
- [ ] Xác minh điều kiện runtime trên args/target/context và cờ enable/sampling.
- [ ] Kiểm tra thứ tự interceptor và khả năng short-circuit/exception trước advice.
- [ ] Phân biệt before, after-success, after-error và finally.
- [ ] Kiểm tra log level, exporter, sampling, async queue trước khi tuyên bố advice không chạy.
- [ ] Kiểm thử cả gọi qua proxy, gọi trực tiếp, lời gọi nội bộ, overload, null, exception và async.
- [ ] Kiểm tra bảo mật: không phát raw token/PII khi thêm diagnostic event.
- [ ] Sau khi sửa, tạo test regression có assertion rõ **thứ tự**, **số lần** và **kết quả/ngoại lệ**.
- [ ] Xác minh policy bắt buộc (authorization/audit) không dựa duy nhất vào một đường proxy dễ bypass.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Method `submit` chạy nghiệp vụ nhưng không có log handler. Có thể kết luận pointcut sai không?

**Đáp:** chưa thể. Trước tiên phải xác minh lời gọi đi qua proxy. Nếu gọi target trực tiếp, matcher chưa từng được đánh giá.

**Câu 2.** Client gọi `proxy.outer()`, bên trong `target.outer()` gọi `this.inner()`. Với proxy delegation riêng, vì sao advice của `inner` có thể không chạy?

**Đáp:** lời gọi nội bộ xảy ra trên target, không quay qua proxy; đây là vấn đề đường gọi. Một subtype-based proxy có thể có dispatch khác nên không nên tổng quát máy móc.

**Câu 3.** Handler nhận method của interface nhưng pointcut so `method.getDeclaringClass() == Impl.class`. Kết quả?

**Đáp:** có thể false vì Method trong JDK dynamic proxy thường được khai báo ở interface. Cần tách declared method và target runtime class.

**Câu 4.** Handler nhận `submit`, pointcut match, nhưng advice im lặng khi flag false. Có phải weaving lỗi?

**Đáp:** không nhất thiết; điều kiện động loại bỏ advice đúng theo cấu hình.

**Câu 5.** Before chạy nhưng after-returning không chạy khi target ném `IllegalArgumentException`. Đúng hay sai?

**Đáp:** đúng đối với ngữ nghĩa after-returning: không có một lần trả về bình thường. Cần quan sát error/finally branch.

**Câu 6.** Một `Cache` ở ngoài `Authorization` trả hit mà không proceed. Điều gì cần lo?

**Đáp:** Authorization ở trong có thể không chạy. Nếu mọi lần đọc phải kiểm tra quyền thì đây là rủi ro; cần đặt hàng rào đúng ranh giới.

**Câu 7.** Với JDK dynamic proxy, lớp target `final` có tự làm proxy bất khả thi không?

**Đáp:** không khi proxy dựa trên interface được target triển khai; `final` chủ yếu hạn chế subclass proxy.

**Câu 8.** Không thấy một dòng log advice ở production. Làm sao xác minh một cách ít phụ thuộc logger?

**Đáp:** dùng test với counter hoặc danh sách sự kiện tuần tự tại handler/advice; kiểm tra riêng telemetry level/sampling/exporter.

## 16. Tài liệu tham khảo

- [Java SE 17 – Dynamic Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): proxy class, giao diện, Object methods và runtime dispatch.
- [Java SE 17 – InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): hợp đồng handler, `Method`, `args`, ngoại lệ và Object methods.
- [Java SE 17 – Method.invoke](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...)): reflection invocation, `InvocationTargetException`.
- [Java SE 17 – AtomicBoolean](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicBoolean.html): cờ thay đổi động có tính hiển thị giữa thread.
- [Java Language Specification – Annotations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.3): ngữ nghĩa `@Inherited` và giới hạn kế thừa annotation.

**Ghi nhớ:** muốn chẩn đoán **“lời gọi không kích hoạt advice”**, hãy đi từ **đường tham chiếu caller → proxy → handler → pointcut → runtime condition → advice chain → log sink**. Nếu một tầng không được đi qua, những tầng bên trong không thể tự có cơ hội chạy. Việc sửa đúng cần dựa vào **bằng chứng về đường thực thi và phạm vi chặn**, không chỉ thêm `println` vào một advice chưa chắc được gọi.


</details>

- [Quay lại đầu trang](#back-to-top)
