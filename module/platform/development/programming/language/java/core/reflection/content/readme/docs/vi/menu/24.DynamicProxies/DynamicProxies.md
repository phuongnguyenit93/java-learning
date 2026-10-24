<a id="back-to-top"></a>

# Dynamic proxy

## Menu
- [Proxy và InvocationHandler](#dynamic-proxies-001)
- [Tạo proxy bằng newProxyInstance](#dynamic-proxies-002)
- [Chọn class loader nhìn thấy đúng các interface](#dynamic-proxies-003)
- [Điều kiện interface public, non-public, hidden hoặc sealed](#dynamic-proxies-004)
- [Gọi proxy được chuyển thành InvocationHandler.invoke](#dynamic-proxies-005)
- [Method, proxy instance và args trong handler](#dynamic-proxies-006)
- [equals, hashCode, toString và hợp đồng cần định nghĩa](#dynamic-proxies-007)
- [Kiểu trả về, unboxing và giới hạn của giá trị null](#dynamic-proxies-008)
- [Checked exception và UndeclaredThrowableException](#dynamic-proxies-009)
- [Gọi default method bằng InvocationHandler.invokeDefault](#dynamic-proxies-010)
- [Tránh gọi lại chính proxy gây đệ quy vô hạn](#dynamic-proxies-011)
- [Nhiều interface có phương thức trùng chữ ký](#dynamic-proxies-012)
- [Proxy.isProxyClass, getInvocationHandler và giới hạn phụ thuộc tên lớp proxy](#dynamic-proxies-013)
- [Proxy interface khác cơ chế tạo lớp con bằng thư viện sinh bytecode](#dynamic-proxies-014)

## <a id="dynamic-proxies-001">Proxy và InvocationHandler</a>

<details>
<summary>Xem chi tiết</summary>

# Proxy và InvocationHandler trong Java

## 1. Từ lời gọi interface đến một bộ xử lý chung

Giả sử nhiều thao tác cần ghi log trước và sau khi chạy. Nếu viết phần log vào từng method nghiệp vụ, mã lặp lại và phần nghiệp vụ bị trộn với việc theo dõi lời gọi.

**Proxy động của JDK (dynamic proxy)** tạo một đối tượng triển khai các interface được chọn lúc chạy. Khi mã gọi sử dụng đối tượng ấy qua interface, lời gọi được chuyển đến một bộ xử lý chung: `InvocationHandler`.

Bộ xử lý có thể:

- Thêm hành vi trước hoặc sau lời gọi.
- Ủy quyền cho một đối tượng thật.
- Tự tính kết quả mà không có đối tượng thật.
- Từ chối một thao tác hoặc chuyển đổi lỗi theo hợp đồng interface.

Các vai trò cần giữ riêng:

| Vai trò | Trách nhiệm |
| --- | --- |
| Interface | Hợp đồng mà mã gọi nhìn thấy |
| Proxy instance | Đối tượng nhận lời gọi qua interface |
| InvocationHandler | Quyết định cách xử lý lời gọi đã được mô tả |
| Đối tượng đích, nếu có | Thực hiện nghiệp vụ được handler ủy quyền |

Proxy không mặc nhiên đồng nghĩa với một đối tượng đích ẩn bên trong. Đối tượng đích là một lựa chọn của cách viết handler.

## 2. Cơ chế chuyển lời gọi

Một luồng ủy quyền điển hình:

~~~text
Mã gọi
  -> phương thức trên proxy qua interface
  -> InvocationHandler.invoke(proxy, method, args)
  -> xử lý bổ sung
  -> method.invoke(target, args), nếu chọn ủy quyền
  -> kết quả hoặc ngoại lệ quay lại mã gọi
~~~

Hai API chính:

~~~java
Proxy.newProxyInstance(loader, interfaces, handler)
~~~

Đây là biểu thức minh họa, với các biến đã được chuẩn bị. Phương thức trả về `Object`; mã gọi ép về interface mà proxy được tạo để triển khai.

Handler thực hiện phương thức:

~~~java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable
~~~

Dòng trên biểu diễn chữ ký của `InvocationHandler.invoke`, không phải một phương thức hoàn chỉnh có phần thân.

Ý nghĩa các tham số:

- `proxy`: chính đối tượng proxy đang nhận lời gọi, **không phải đối tượng đích**.
- `method`: metadata của method interface được gọi; lớp khai báo có thể là interface cha mà interface được cung cấp kế thừa method.
- `args`: các đối số, với giá trị primitive được đóng hộp; bằng `null` nếu method không có đối số.

Lấy được một `Method` mô tả lời gọi chưa có nghĩa handler phải dùng Reflection để gọi một đối tượng thật. Handler có thể chọn cách xử lý khác.

## 3. Ví dụ hoàn chỉnh: thêm dấu vết và ủy quyền

Lưu thành `ProxyHandlerDemo.java`:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

public class ProxyHandlerDemo {
    public interface GreetingService {
        String greet(String name);
        int calls();
    }

    public static final class RealGreetingService
            implements GreetingService {
        private int count;

        @Override
        public String greet(String name) {
            String cleaned = Objects.requireNonNull(name, "name").strip();
            if (cleaned.isEmpty()) {
                throw new IllegalArgumentException("name is blank");
            }
            count++;
            return "Hello, " + cleaned;
        }

        @Override
        public int calls() {
            return count;
        }
    }

    public static final class TracingHandler implements InvocationHandler {
        private final GreetingService target;

        public TracingHandler(GreetingService target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] arguments) throws Throwable {

            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "GreetingProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                };
            }

            System.out.println("enter " + method.getName());
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            } finally {
                System.out.println("leave " + method.getName());
            }
        }
    }

    public static void main(String[] args) {
        GreetingService target = new RealGreetingService();
        TracingHandler handler = new TracingHandler(target);

        GreetingService service = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(),
                new Class<?>[] {GreetingService.class},
                handler);

        System.out.println("isProxyClass="
                + Proxy.isProxyClass(service.getClass()));
        System.out.println("handlerMatches="
                + (Proxy.getInvocationHandler(service) == handler));

        System.out.println(service.greet("Lan"));
        System.out.println("calls=" + service.calls());
        System.out.println("toString=" + service);
        System.out.println("equalsSelf=" + service.equals(service));
        System.out.println("equalsTarget=" + service.equals(target));

        try {
            service.greet(" ");
        } catch (IllegalArgumentException exception) {
            System.out.println("error="
                    + exception.getClass().getSimpleName()
                    + ":" + exception.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 ProxyHandlerDemo.java
java ProxyHandlerDemo
~~~

Kết quả:

~~~text
isProxyClass=true
handlerMatches=true
enter greet
leave greet
Hello, Lan
enter calls
leave calls
calls=1
toString=GreetingProxy
equalsSelf=true
equalsTarget=false
enter greet
leave greet
error=IllegalArgumentException:name is blank
~~~

Chương trình chạy tuần tự. `RealGreetingService` giữ số lần chào hợp lệ; ví dụ không xây một bộ đếm đồng thời.

Đọc theo từng bước:

1. `newProxyInstance` tạo một đối tượng triển khai `GreetingService`.
2. `service.greet("Lan")` chuyển thành lời gọi `handler.invoke`.
3. Handler in `enter`, gọi method trên `target`, rồi luôn in `leave` trong `finally`.
4. `calls()` không có tham số, nên mảng đối số của handler là null. `Method.invoke` chấp nhận null để biểu diễn không có đối số.
5. Kết quả `int` của `calls()` được Reflection đóng hộp khi trở về handler, rồi proxy trả lại kết quả phù hợp với kiểu `int` của interface.
6. Khi đối tượng đích từ chối tên rỗng, handler lấy nguyên nhân gốc khỏi `InvocationTargetException`, nên phía gọi nhận `IllegalArgumentException`.

Proxy được nhận diện bằng `Proxy.isProxyClass`, không bằng việc tìm một chuỗi tên lớp do JDK sinh. `Proxy.getInvocationHandler` lấy handler gắn với đúng proxy instance.

## 4. Tách vai trò proxy, handler và target

Ví dụ trên có hai đối tượng **khác nhau**: `service` là proxy nhận lời gọi từ caller, còn `target` là `RealGreetingService` thực hiện nghiệp vụ. Handler nối hai đối tượng bằng `method.invoke(target, arguments)` và chỉ log quanh lần gọi được ủy quyền đó. Lời gọi `target.greet("Lan")` trực tiếp **không đi qua handler**; chỉ những lời gọi mà caller gửi đến `service` mới được chặn.

Không có quy tắc yêu cầu handler phải sở hữu target. Một mock hoặc RPC stub có thể tự trả lời dựa trên `method` và `args`. Khi không có target, handler vẫn phải tạo kết quả đúng kiểu hoặc ném ngoại lệ phù hợp với hợp đồng của interface.

**Không ủy quyền ngược về proxy:** `method.invoke(proxy, arguments)` thực hiện lại cùng phương thức trên đối tượng proxy, nên lại kích hoạt handler và có thể gây vòng lặp đến `StackOverflowError`. Khi log, tránh nối chuỗi với chính `proxy` nếu chưa có chính sách xử lý `toString()` rõ ràng.

## 5. Bốn ranh giới mà một handler cần giữ đúng

| Ranh giới | Điều cần biết khi viết handler |
| --- | --- |
| Đối số | `args == null` nếu method không nhận tham số; primitive được boxing. |
| Giá trị trả về | Một `int` cần `Integer` tương ứng; `null` với kiểu primitive gây `NullPointerException`; kiểu object sai có thể gây `ClassCastException`. |
| Ngoại lệ | `Method.invoke(target, args)` bọc lỗi của target bằng `InvocationTargetException`; nếu chuyển tiếp lỗi thật, thường lấy `getCause()`. Checked exception ra khỏi handler phải phù hợp với `throws` của interface, nếu không proxy có thể bọc `UndeclaredThrowableException`. |
| `Object` methods | `equals`, `hashCode` và `toString` cũng tới handler; ví dụ dùng identity của proxy cho equality/hash và chuỗi `GreetingProxy`. |

Với default method của interface, lời gọi vẫn đi qua handler. Nếu thật sự muốn chạy phần thân mặc định, từ Java 16 có `InvocationHandler.invokeDefault`; không sử dụng `Method.invoke(proxy, args)` để thay thế. Các bài riêng về giá trị trả về, checked exception, default method và các phương thức của `Object` sẽ đi sâu vào các điều kiện cụ thể.

## 6. Khi nào sử dụng mô hình này?

Chọn JDK dynamic proxy nếu caller làm việc qua một **interface** và bạn muốn thay đổi cách lời gọi được xử lý tại runtime: kiểm tra, ghi log, đo đạc, mock hay gọi dịch vụ từ xa. `newProxyInstance` tạo lớp proxy implements các interface đó; nó không làm proxy trở thành subclass của lớp `RealGreetingService`.

Bộ nạp lớp phải nhìn thấy đúng từng interface được yêu cầu. Cần xét thêm ranh giới Java modules, interface `sealed`/`hidden`, các method trùng chữ ký và quyền truy cập của interface `non-public` nếu dùng trong môi trường plugin/module. Chỉ một interface public ở classpath thông thường, như ví dụ, giúp tập trung vào luồng lời gọi trước khi chuyển sang các trường hợp khó.

## 7. Tự kiểm tra từ ví dụ

1. `Proxy.newProxyInstance` có tự khởi tạo `RealGreetingService` không? **Không**; target được tạo độc lập và handler nhận target do code ứng dụng cung cấp.
2. Vì sao `service.greet("Lan")` có in `enter/leave`, nhưng `target.greet("Lan")` không? **Đối tượng nhận lời gọi khác nhau**; chỉ proxy chuyển lời gọi qua handler.
3. `calls()` không có tham số; handler có nhận mảng một phần tử không? **Không**; `arguments` là `null`.
4. Vì sao cần tháo `InvocationTargetException` khi chuyển tiếp lỗi gốc? Để giữ nguyên nhân nghiệp vụ và tránh đưa một checked wrapper ngoài `throws` của interface ra phía caller.
5. Có bắt buộc dùng `Method.invoke(target, arguments)` không? **Không**; handler có thể tự thực thi một chiến lược khác và trả giá trị đúng hợp đồng.

**Ghi nhớ:** JDK proxy cung cấp một đối tượng gọi được qua interface, còn `InvocationHandler` định nghĩa hành vi của lời gọi. Target, nếu có, là lựa chọn của handler. Luôn tách **proxy nhận lời gọi**, **handler điều phối** và **target xử lý nghiệp vụ** để tránh vô tình gọi ngược vào proxy.

### Tài liệu chính thức

- [Java SE 17 — Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html)
- [Java SE 17 — InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html)
- [Java SE 17 — Method.invoke](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-002">Tạo proxy bằng newProxyInstance</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo proxy bằng `Proxy.newProxyInstance` trong Java

`Proxy.newProxyInstance` là API của `java.lang.reflect.Proxy` dùng để **tạo một đối tượng proxy tại runtime**, triển khai một hoặc nhiều interface đã chỉ định và chuyển các lời gọi phương thức trên đối tượng đó đến một `InvocationHandler`. Đây là cách tạo *JDK dynamic proxy* có sẵn trong Java, không cần tự viết một lớp wrapper cho từng implementation.

Mục tiêu của bài: hiểu chính xác ba tham số của `newProxyInstance`, luồng lời gọi qua handler, cách tạo proxy chạy được trên Java 17+, cách trả giá trị/lan truyền ngoại lệ và những giới hạn cần kiểm soát khi áp dụng thực tế.

## 1. Khi nào cần tạo proxy?

Giả sử ứng dụng có interface `Greeter` và một implementation thật. Nhiều đối tượng cần gọi `greet`, nhưng bạn muốn ghi log trước/sau lời gọi, đo thời gian, kiểm tra quyền hoặc thống kê số lần gọi. Cách gọi trực tiếp sẽ chạy thẳng vào đối tượng thật:

```text
caller ──greet("An")──> Greeter implementation
```

Nếu caller nhận **proxy có cùng interface**, bạn có thể đặt xử lý chung ở giữa:

```text
caller (biết interface Greeter)
      │ gọi greet("An")
      ▼
dynamic proxy (implements Greeter)
      │ chuyển thông tin lời gọi
      ▼
InvocationHandler.invoke(proxy, method, args)
      │ trước lời gọi: log / kiểm tra / đo đạc
      │ tùy thiết kế: method.invoke(target, args)
      │ sau lời gọi: xử lý kết quả / ngoại lệ
      ▼
kết quả hoặc ngoại lệ quay về caller
```

Caller vẫn viết `service.greet("An")` như với một đối tượng `Greeter` bình thường. Điểm khác nằm ở **đối tượng được truyền cho caller**: đó là proxy, và handler được thực thi khi có lời gọi qua proxy.

Điều quan trọng: proxy **không bắt buộc** phải chuyển tiếp tới một đối tượng thật. Handler cũng có thể trả kết quả trực tiếp, lấy dữ liệu từ nơi khác hoặc từ chối lời gọi. Mô hình *proxy bọc target* chỉ là trường hợp thông dụng, dễ hiểu nhất.

## 2. Chữ ký và ý nghĩa của ba tham số

```java
public static Object newProxyInstance(
        ClassLoader loader,
        Class<?>[] interfaces,
        InvocationHandler h)
```

| Tham số | Vai trò | Chọn như thế nào? |
| --- | --- | --- |
| `loader` | Class loader dùng để định nghĩa lớp proxy được tạo tại runtime | Thường sử dụng `Greeter.class.getClassLoader()`; loader phải nhìn thấy các interface cần proxy |
| `interfaces` | Danh sách các interface mà proxy sẽ triển khai | Ví dụ `new Class<?>[] {Greeter.class}`; có thể nhiều interface nếu đáp ứng các ràng buộc của JDK |
| `h` | Đối tượng `InvocationHandler` xử lý các lời gọi lên proxy | Thường là lambda hoặc class cài đặt phương thức `invoke` |

Phương thức **trả về `Object`**, vì danh sách interface chỉ được xác định từ tham số tại runtime. Người gọi thường ép kiểu về interface đã biết:

```java
Greeter proxy = (Greeter) Proxy.newProxyInstance(
        Greeter.class.getClassLoader(),
        new Class<?>[] {Greeter.class},
        handler);
```

Đoạn trên giả định `Greeter` và `handler` đã được khai báo; chương trình chạy đầy đủ ở mục 4.

### Phân biệt proxy với đối tượng đích

- `proxy instanceof Greeter` là `true` nếu `Greeter.class` nằm trong danh sách interface được triển khai.
- `proxy` **không phải** một instance của lớp implementation `BasicGreeter`; ép `(BasicGreeter) proxy` sẽ thất bại.
- Proxy thực thi lời gọi trên **interface**. `Proxy.newProxyInstance` không tạo subclass của một lớp Java thông thường. Nếu cần proxy lớp cụ thể, phải dùng chiến lược khác có hỗ trợ việc đó.
- `Proxy.isProxyClass(proxy.getClass())` giúp xác định lớp của đối tượng được tạo bằng cơ chế JDK proxy. `Proxy.getInvocationHandler(proxy)` trả lại handler tương ứng, nhưng đòi hỏi đối tượng đầu vào thực sự là proxy.

Đây cũng là lý do API proxy thường được sử dụng cùng **lập trình hướng interface**: caller phụ thuộc contract `Greeter`, không phụ thuộc vào implementation bên dưới.

## 3. `InvocationHandler` nhận gì khi có lời gọi?

Interface `InvocationHandler` có một phương thức trừu tượng:

```java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
```

| Tham số của `invoke` | Ý nghĩa |
| --- | --- |
| `proxy` | Chính đối tượng proxy đang nhận lời gọi; **không** phải target |
| `method` | Metadata `java.lang.reflect.Method` mô tả phương thức được gọi từ interface (hoặc một trong ba phương thức `Object` đặc biệt) |
| `args` | Các đối số của lần gọi, theo thứ tự; có thể là `null` khi phương thức không có tham số |

Handler có thể nhìn `method.getName()`, `method.getParameterTypes()`, annotation và các metadata khác để quyết định thực hiện hành động nào. Không nên dựa **chỉ** vào tên phương thức nếu interface có overload.

Giá trị handler trả về sẽ thành kết quả của lời gọi trên proxy. Nếu kiểu trả về là primitive như `int`, handler phải trả wrapper phù hợp như `Integer` để Java unbox; trả `null` cho kiểu primitive sẽ dẫn đến `NullPointerException`. Với phương thức `void`, handler nên trả `null`; caller không nhận một giá trị nào.

**Tránh vòng lặp vô hạn:** bên trong `invoke`, gọi lại chính phương thức trên `proxy` sẽ lại kích hoạt handler. Nếu muốn chuyển tiếp đến implementation thật, hãy gọi trên `target` (một đối tượng khác với proxy).

## 4. Ví dụ Java 17+ đầy đủ: tạo proxy, log và chuyển tiếp tới target

Lưu chương trình sau thành file **`ProxyDemo.java`**. Ví dụ dùng một interface `Greeter`, implementation `BasicGreeter` và handler vừa log vừa chuyển tiếp lời gọi.

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ProxyDemo {
    public interface Greeter {
        String greet(String name);
        int count();
    }

    public static class BasicGreeter implements Greeter {
        private int calls;

        @Override
        public String greet(String name) {
            calls++;
            return "Xin chào, " + name + "!";
        }

        @Override
        public int count() {
            return calls;
        }
    }

    public static void main(String[] args) {
        Greeter real = new BasicGreeter();

        InvocationHandler handler = (proxy, method, passedArgs) -> {
            // Ba phương thức Object này cũng đi qua handler.
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "GreeterProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == passedArgs[0];
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                };
            }

            System.out.println("Trước: " + method.getName());
            try {
                // Gọi implementation thật, không gọi lại proxy.
                Object result = method.invoke(real, passedArgs);
                System.out.println("Sau: " + method.getName());
                return result;
            } catch (InvocationTargetException exception) {
                // Giữ lại nguyên nhân thật từ phương thức được gọi.
                throw exception.getCause();
            }
        };

        Greeter proxy = (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[] {Greeter.class},
                handler);

        System.out.println(proxy.greet("An"));
        System.out.println("Đếm: " + proxy.count());
        System.out.println(proxy.toString());
        System.out.println("Tự bằng chính mình: " + proxy.equals(proxy));
        System.out.println("Có phải proxy: "
                + Proxy.isProxyClass(proxy.getClass()));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ProxyDemo.java
java ProxyDemo
```

Kết quả chuẩn:

```text
Trước: greet
Sau: greet
Xin chào, An!
Trước: count
Sau: count
Đếm: 1
GreeterProxy
Tự bằng chính mình: true
Có phải proxy: true
```

**Điều xảy ra khi chạy:**

1. `real` là một `BasicGreeter` thực sự và giữ state `calls`.
2. `newProxyInstance` tạo một đối tượng triển khai interface `Greeter`, kết nối đối tượng đó với `handler`.
3. `proxy.greet("An")` được Java chuyển đến `handler.invoke(proxy, method, passedArgs)`. `method.getName()` là `"greet"` và `passedArgs[0]` là `"An"`.
4. Handler in dòng "Trước", gọi `method.invoke(real, passedArgs)`; `BasicGreeter.greet` tăng `calls` và trả chuỗi. Handler in "Sau" rồi trả cùng kết quả cho caller.
5. `proxy.count()` tiếp tục đi qua handler; lần này `passedArgs` là `null` vì `count()` không nhận tham số. Lời gọi `method.invoke(real, passedArgs)` vẫn hợp lệ trong trường hợp này.
6. `toString()`, `equals(Object)` cũng đi vào handler nhưng được tách riêng để có ngữ nghĩa rõ ràng. `hashCode()` được xử lý tương tự bằng identity hash code, nhất quán với phép so sánh identity trong ví dụ.

`InvocationTargetException` là vỏ ngoại lệ mà Reflection dùng khi code ở target ném lỗi. Đoạn `catch` trả lại `getCause()` để proxy lan truyền nguyên nhân thực thay vì vô tình để caller nhận wrapper không được khai báo.

Lưu ý rằng dòng "Sau" trong ví dụ **chỉ in khi target thành công**. Nếu cần đo đạc hoặc log bất kể thành công/thất bại, hãy dùng `try/finally` và phân biệt riêng trạng thái lỗi; không biến lỗi thật thành kết quả thành công.

## 5. Quy trình tạo proxy trong dự án

Khi áp dụng `newProxyInstance` vào code thực tế, có thể làm theo quy trình:

1. **Xác định interface contract** mà caller đang sử dụng, ví dụ `Greeter` hoặc `Repository`. Kiểm tra target thực sự triển khai contract đó nếu bạn định delegate.
2. **Chọn class loader** có thể nhìn thấy interface. Dùng loader của chính interface là lựa chọn tốt cho ví dụ cùng module; khi chạy với plugin hoặc nhiều class loader, phải bảo đảm khả năng nhìn thấy từng interface.
3. **Xây dựng handler** có hành vi rõ: pre-processing, chọn target, delegate, post-processing, xử lý ngoại lệ, quy tắc cho `equals`/`hashCode`/`toString`.
4. **Tạo proxy một lần tại điểm cấu hình** rồi truyền cho caller bằng interface. Đừng tạo proxy mới cho mỗi lời gọi nếu không có lý do cụ thể.
5. **Kiểm thử hành vi thông qua reference proxy**: thành công, ngoại lệ, phương thức không tham số, overload, kiểu trả về, phương thức `Object` và các luồng đồng thời nếu có state chia sẻ.

Một proxy có thể triển khai **nhiều interface** bằng cách truyền thêm các `Class<?>` tương ứng trong mảng, miễn là các interface thỏa các giới hạn của JDK. Tuy nhiên caller phải chọn đúng interface để ép kiểu và bạn vẫn cần thiết kế logic dispatch cho các phương thức có cùng chữ ký.

## 6. Các tình huống thực tế và điều proxy không tự làm

| Mục đích | Handler cần thực hiện | Lưu ý |
| --- | --- | --- |
| Ghi log | Log tên phương thức, đầu vào được phép ghi, kết quả hoặc lỗi | Tránh log mật khẩu, token và dữ liệu nhạy cảm |
| Đo thời gian | Ghi mốc trước khi delegate, đo trong `finally` | Phân biệt thời gian thực thi và thời gian chờ I/O |
| Kiểm tra quyền | Xác minh quyền rồi cho phép hoặc từ chối lời gọi | Phải có contract rõ cho lỗi từ chối |
| Transaction/cross-cutting logic | Bắt đầu, hoàn tất hoặc rollback theo chính sách | Cần quy tắc ngoại lệ, propagation và đồng bộ trạng thái |
| Adapter/client | Dựa trên `Method` và args để gửi tới remote service | Proxy không tự tạo network client hay serializer |

`newProxyInstance` chỉ cung cấp **hạ tầng chuyển lời gọi**. Nó không tự cài logging, authorization, transaction, retry, caching hay serialization. Tất cả hành vi đó đến từ `InvocationHandler` hoặc những thành phần được handler sử dụng.

Cần phân biệt với `java.net.Proxy`: lớp đó mô tả proxy mạng (ví dụ SOCKS/HTTP), **không liên quan tới việc tạo JDK dynamic proxy bằng Reflection**.

## 7. Những giới hạn và lỗi thường gặp

### 7.1. Phải là interface và phải nhìn thấy qua class loader

Mảng `interfaces` phải chứa các interface hợp lệ, không phải class thông thường, kiểu primitive, hidden interface hay sealed interface. Không truyền trùng cùng một `Class<?>`; interface phải nhìn thấy được bằng tên qua loader đã chọn. Những tổ hợp interface có các phương thức xung đột về kiểu trả về, hoặc interface không public thuộc các package/module không tương thích, cũng có thể khiến `newProxyInstance` ném `IllegalArgumentException`.

Với named module (JPMS), quyền truy cập và vị trí của lớp proxy phụ thuộc khả năng truy cập các interface. Không nên giả định rằng tên lớp proxy sinh ra là cố định hay constructor của lớp proxy đó có thể được truy cập trực tiếp. Dùng `newProxyInstance` để tạo instance.

### 7.2. Handler phải xử lý `Object` methods

JDK proxy chuyển cả `equals(Object)`, `hashCode()` và `toString()` đến handler. `method.getDeclaringClass()` của ba phương thức này là `Object.class`.

Nếu delegate mù quáng bằng `method.invoke(real, args)`, `proxy.equals(proxy)` có thể thành `real.equals(proxy)` và trả `false` ngay cả khi bạn so sánh proxy với chính nó. Nếu handler gọi `proxy.toString()` để ghi log cho chính lời gọi `toString()`, nó còn có thể lặp đệ quy.

Chọn chính sách equality rõ ràng: dùng identity của proxy (như ví dụ), hoặc thiết kế value equality phù hợp với abstraction và bảo đảm `equals` nhất quán với `hashCode`.

### 7.3. Ngoại lệ và kiểu trả về

- `Method.invoke(target, args)` bọc lỗi phát sinh từ target trong `InvocationTargetException`. Delegate đúng cách thường cần giải bọc `getCause()`.
- Handler có thể ném unchecked exception. Với **checked exception** không tương thích với `throws` của method thuộc interface, proxy sẽ bọc nó trong `UndeclaredThrowableException`. Vì vậy không thể tùy ý ném checked exception từ handler rồi mong caller nhận nguyên trạng.
- Nếu handler trả sai kiểu so với kiểu trả về đã khai báo, lời gọi qua proxy có thể ném `ClassCastException`; với kiểu primitive mà handler trả `null`, sẽ ném `NullPointerException`.
- Nếu muốn xử lý mọi kết thúc, kể cả khi target ném ngoại lệ, đặt cleanup và ghi metric trong `finally`; không nuốt ngoại lệ trừ khi contract cho phép.

### 7.4. Phương thức `default` và interface trùng chữ ký

Phương thức `default` của interface cũng có thể được chuyển đến handler. Trên Java 17+, khi muốn gọi trực tiếp phần thân default method của một proxy interface từ handler, có thể dùng `InvocationHandler.invokeDefault(proxy, method, args)` sau khi đã kiểm tra `method.isDefault()` và các điều kiện hợp lệ. Đây là API có từ Java 16; không phải mọi lời gọi default method tự bỏ qua handler.

Khi proxy triển khai nhiều interface có cùng tên và danh sách tham số, handler có thể nhận `Method` gắn với interface xuất hiện trước trong mảng interface, bất kể caller gọi qua reference interface nào. Do đó, thứ tự interface và quy tắc xử lý phương thức trùng chữ ký cần được cân nhắc.

### 7.5. Lời gọi trực tiếp và self-invocation không đi qua proxy

Chỉ những lời gọi **thực sự đi qua đối tượng proxy** mới được handler chặn. Nếu code gọi `real.greet("An")`, handler không chạy. Tương tự, nếu một method trong target gọi một method khác trên `this`, lời gọi nội bộ này không tự quay lại proxy. Đây là nguồn gốc của các lỗi khó thấy khi dùng interceptor cho transaction, cache hoặc authorization.

### 7.6. Chi phí hiệu năng, concurrency và input không tin cậy

Dynamic proxy phát sinh chi phí dispatch/Reflection và có thể thêm allocation hoặc logic phụ. Không nên khẳng định luôn chậm một mức cố định; hãy đo trong tình huống thật và tránh tạo proxy hoặc truy tìm metadata lặp đi lặp lại không cần thiết.

Nếu cùng handler/target có state chia sẻ và nhiều thread gọi proxy đồng thời, code handler cần tuân thủ quy tắc thread safety tương ứng. Proxy không tự làm cho counter, cache hay transaction state trở nên an toàn.

Không dùng tên interface/lớp/hành động nhận từ nguồn không tin cậy để tự do khởi tạo hoặc gọi method; hãy giới hạn contract cho phép, kiểm tra dữ liệu đầu vào và quyền thực thi ở điểm thích hợp.

## 8. Khi nào dùng JDK dynamic proxy, khi nào dùng cách khác?

**Ưu tiên `Proxy.newProxyInstance`** khi caller đã làm việc qua interface, và bạn cần hành vi chung xung quanh các lời gọi tại runtime. Điển hình là logging, metrics, remote adapter, invocation routing và nhiều cơ chế interceptor trong thư viện/framework.

**Ưu tiên decorator viết tay** nếu chỉ có một vài interface/lớp ổn định, logic bọc đơn giản và bạn muốn code tường minh, dễ theo dõi bằng IDE, dễ kiểm tra kiểu lúc biên dịch.

**Cân nhắc công cụ proxy bằng subclass hoặc bytecode** nếu contract bắt buộc dựa trên class cụ thể. Giải pháp đó có quy tắc riêng về final method, constructor và access; không thể dùng trực tiếp cùng cơ chế của `java.lang.reflect.Proxy`.

Một lưu ý trong thiết kế: dù proxy có thể dùng để cài cross-cutting concerns, việc dùng dynamic proxy **không tự động biến mọi lời gọi trong hệ thống thành lời gọi được intercept**. Phạm vi luôn phụ thuộc nơi bạn trao đối tượng proxy cho caller.

## 9. Checklist và tự kiểm tra

Trước khi đưa một JDK dynamic proxy vào ứng dụng, hãy xác nhận:

- Interface và class loader phù hợp; caller giữ reference đến **proxy**.
- Handler xác định đúng phương thức và xử lý `args == null`.
- Các giá trị trả về đúng kiểu, đặc biệt với primitive hoặc `void`.
- Ngoại lệ phát sinh từ target được lan truyền theo contract của interface.
- `equals`, `hashCode` và `toString` không có hành vi bất ngờ hay recursion.
- Lời gọi nội bộ trên target, state chia sẻ và performance đã được tính đến.

**Câu hỏi luyện tập:**

1. Vì sao `newProxyInstance` nhận danh sách interface mà không nhận lớp implementation để làm superclass?
2. Nếu thay `proxy.greet("An")` bằng `real.greet("An")` thì output thay đổi ra sao?
3. Tại sao `invoke` cần trả `Integer` khi interface có phương thức trả `int`?
4. Điều gì có thể xảy ra nếu handler gọi `proxy.toString()` khi đang xử lý `toString()`?
5. Tại sao cần `exception.getCause()` sau `Method.invoke` và khi nào proxy tạo `UndeclaredThrowableException`?
6. Vì sao test phải bao gồm cả lỗi, overload, self-invocation và tình huống concurrent?

## Tham khảo API chuẩn

- [`Proxy.newProxyInstance` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Proxy.html)
- [`InvocationHandler.invoke` và `InvocationHandler.invokeDefault` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/InvocationHandler.html)

Bài này tập trung vào **cách tạo và sử dụng proxy qua `newProxyInstance`**: chọn đúng interface/loader/handler, hiểu dispatch và kiểm soát hành vi. Các kỹ thuật proxy lớp cụ thể, AOP framework và bytecode generation là những chủ đề liên quan nhưng có cơ chế khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-003">Chọn class loader nhìn thấy đúng các interface</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn class loader nhìn thấy đúng các interface khi tạo Java dynamic proxy

## 1. Vấn đề thực tế

Trong Java Reflection, `java.lang.reflect.Proxy` tạo đối tượng triển khai một hoặc nhiều **interface tại runtime**, rồi chuyển lời gọi phương thức tới `InvocationHandler`. API chính là:

~~~java
Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] {Service.class, Auditable.class},
        handler);
~~~

*Đây là đoạn mã minh họa, giả định các kiểu và biến đã được khai báo.*

Tham số `loader` rất quan trọng: JVM dùng nó để định nghĩa lớp proxy được sinh ra, và **loader đó phải nhìn thấy đúng từng interface** mà proxy triển khai. Một interface có thể tồn tại trong chương trình nhưng không nhìn thấy từ loader được chọn; hai lớp/interface có tên giống hệt nhau vẫn có thể là **hai kiểu khác nhau** nếu được định nghĩa bởi hai class loader khác nhau.

**Mục tiêu bài học:** biết chọn loader phù hợp trong chương trình thông thường và môi trường plugin/module; hiểu phép kiểm tra danh tính `Class` của `Proxy`; kiểm chứng trước khi tạo proxy; nhận biết lỗi do loader, do quy tắc interface, và do ép kiểu.

## 2. “Nhìn thấy interface” nghĩa chính xác là gì?

Với từng interface `i` và class loader `cl`, một điều kiện bắt buộc của `Proxy.newProxyInstance` là:

~~~java
Class.forName(i.getName(), false, cl) == i
~~~

Ý nghĩa của từng phần:

- `i` là **chính đối tượng `Class<?>`** của interface được truyền trong mảng `interfaces`.
- `i.getName()` là tên nhị phân (*binary name*) của interface.
- `Class.forName(name, false, cl)` thử phân giải kiểu có tên đó bằng loader `cl`, với `initialize=false` để không chủ động khởi tạo lớp.
- `== i` yêu cầu trả về **đúng cùng một đối tượng `Class`**, chứ không đơn thuần là hai kiểu có cùng tên.

Nếu `Class.forName` không tìm được kiểu, nó ném `ClassNotFoundException`. Nếu tìm được **một interface khác có cùng tên**, phép so sánh `==` vẫn sai. `Proxy.newProxyInstance` sẽ từ chối trường hợp vi phạm quy tắc nhìn thấy kiểu, thường bằng `IllegalArgumentException`. Lưu ý: lớp có thể được phân giải qua cơ chế **ủy quyền** (*delegation*) của loader; không đòi hỏi `i.getClassLoader() == cl`.

Đây là phân biệt then chốt:

| Câu hỏi | Phép kiểm tra | Ý nghĩa |
| --- | --- | --- |
| Hai interface có tên giống nhau? | `a.getName().equals(b.getName())` | Chỉ so sánh tên |
| Hai interface là cùng một kiểu runtime? | `a == b` | So sánh định danh `Class` |
| Loader `cl` thấy đúng interface `i`? | `Class.forName(i.getName(), false, cl) == i` | Điều kiện bắt buộc của dynamic proxy |
| Một đối tượng có triển khai `i`? | `i.isInstance(object)` | Kiểm tra tương thích kiểu lúc chạy |

Java xác định kiểu tham chiếu theo **tên nhị phân cùng class loader định nghĩa kiểu**, không chỉ dựa vào tên. Loader được chọn cho proxy vì thế phải thấy **đúng `Class`** của mọi interface đầu vào.

## 3. Hình dung class loader trong môi trường ứng dụng và plugin

Ví dụ có một class loader ứng dụng `AppLoader` và loader plugin `PluginLoader` ủy quyền về `AppLoader`:

~~~text
                  Bootstrap / Platform
                           ↑
                        AppLoader
                       (SharedApi)
                           ↑ ủy quyền về cha
                       PluginLoader
                       (PluginOnlyApi)

AppLoader:   thường thấy SharedApi; không tự động thấy PluginOnlyApi
PluginLoader: có thể thấy cả SharedApi (qua cha) và PluginOnlyApi (do mình nạp)
~~~

Trong cấu hình này, khi cần proxy cho `SharedApi` **và** `PluginOnlyApi`, loader plugin có thể là lựa chọn hợp lý **nếu** kiểm tra `Class.forName(..., false, PluginLoader) == i` đúng với *cả hai* interface. Loader ứng dụng thường không thể nhìn thấy interface chỉ có trong plugin. Ngược lại, trong hệ thống dùng loader riêng không ủy quyền chuẩn, một loader có thể nạp lại `SharedApi` thành **bản sao cùng tên nhưng khác kiểu**; khi đó không dùng nó để triển khai interface `SharedApi` của ứng dụng.

Không có quy tắc “cứ loader con là đúng”. Cấu trúc ủy quyền, chính sách child-first, JPMS và danh tính thực của mỗi `Class<?>` mới quyết định khả năng tương thích.

## 4. Quy trình chọn loader

1. **Thu thập chính các `Class<?>` của interface cần triển khai.** Ví dụ `Service.class` của API dùng chung hoặc `pluginApiClass` nhận từ loader plugin. Tránh tải lại interface chỉ bằng tên nếu đã có `Class<?>` đáng tin cậy.
2. **Chọn ứng viên dựa trên nơi các interface được định nghĩa và cơ chế ủy quyền.** Nếu chỉ có một interface thông thường, `Service.class.getClassLoader()` là điểm xuất phát đơn giản. Với nhiều interface của plugin, xem loader plugin có nhìn thấy toàn bộ interface, kể cả API dùng chung, hay không.
3. **Kiểm tra danh tính của tất cả interface**, bằng phép so sánh ở mục 2. Nếu một interface không đạt, đổi kiến trúc nạp lớp hoặc chọn loader khác có thể thấy đồng thời các kiểu thực sự cần dùng.
4. **Kiểm tra giới hạn khác của dynamic proxy.** Các interface phải hợp lệ, khả năng truy cập qua module/package phải cho phép, các kiểu xuất hiện trong chữ ký phương thức `public` cũng phải nhìn thấy; proxy có thể bị từ chối dù bước 3 đã đạt.
5. **Tạo proxy bằng đúng loader và mảng interface đã kiểm tra**, sau đó dùng proxy thông qua interface được chia sẻ giữa phía gọi và proxy.

Có thể viết phép kiểm tra sơ bộ như sau:

~~~java
static boolean seesAllInterfaces(ClassLoader loader, Class<?>... interfaces) {
    for (Class<?> iface : interfaces) {
        if (iface == null || !iface.isInterface()) {
            return false;
        }
        try {
            if (Class.forName(iface.getName(), false, loader) != iface) {
                return false;
            }
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
    return true;
}
~~~

*Đây là phương thức minh họa, cần đặt trong một lớp Java; ví dụ hoàn chỉnh ở mục 5.*

Hàm trên xác nhận **từng interface đầu vào nhìn thấy được và có cùng định danh**, nhưng **chưa chứng minh proxy chắc chắn tạo được**: quy tắc về kiểu của tham số/giá trị trả về, method trùng chữ ký, interface không công khai, module, interface `sealed` hoặc `hidden` vẫn cần được `Proxy` xác thực. Các lỗi nạp/liên kết lớp khác `ClassNotFoundException` cũng có thể phát sinh và không nên che giấu như một kết quả `false` thông thường.

## 5. Ví dụ Java 17+ chạy độc lập: một loader thấy đủ, một loader chỉ thấy một interface

Trong chương trình dưới đây, `Greeter` và `Tag` đều do loader ứng dụng nhìn thấy. `MaskingLoader` được tạo ra để **cố ý từ chối nạp `Tag`**, mô phỏng tình huống chọn loader không nhìn thấy đầy đủ interface. Đây là loader phục vụ bài thực nghiệm; trong ứng dụng thật, lỗi thường đến từ ranh giới giữa các module/plugin hoặc classpath khác nhau.

Tạo tệp `ProxyLoaderChoiceDemo.java`:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class ProxyLoaderChoiceDemo {
    public interface Greeter {
        String greet(String name);
    }

    public interface Tag {
    }

    static final class MaskingLoader extends ClassLoader {
        MaskingLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.equals(Tag.class.getName())) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    static boolean seesAllInterfaces(ClassLoader loader, Class<?>... types) {
        for (Class<?> type : types) {
            if (type == null || !type.isInterface()) {
                return false;
            }
            try {
                if (Class.forName(type.getName(), false, loader) != type) {
                    return false;
                }
            } catch (ClassNotFoundException ex) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        ClassLoader good = Greeter.class.getClassLoader();
        ClassLoader masked = new MaskingLoader(good);
        Class<?>[] interfaces = {Greeter.class, Tag.class};

        System.out.println("good sees all: "
                + seesAllInterfaces(good, interfaces));
        System.out.println("masked sees Greeter: "
                + seesAllInterfaces(masked, Greeter.class));
        System.out.println("masked sees Tag: "
                + seesAllInterfaces(masked, Tag.class));

        InvocationHandler handler = (proxy, method, values) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "GreeterProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == values[0];
                    default -> throw new UnsupportedOperationException();
                };
            }
            if (method.getName().equals("greet")) {
                return "Xin chào, " + values[0];
            }
            throw new UnsupportedOperationException(method.toString());
        };

        Object created = Proxy.newProxyInstance(
                good, interfaces, handler);
        Greeter greeter = (Greeter) created;

        System.out.println(greeter.greet("Linh"));
        System.out.println("implements Tag: " + (created instanceof Tag));
        System.out.println("proxy loader == good: "
                + (created.getClass().getClassLoader() == good));

        try {
            Proxy.newProxyInstance(masked, interfaces, handler);
            System.out.println("unexpected success");
        } catch (IllegalArgumentException ex) {
            System.out.println("masked rejected: IllegalArgumentException");
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac ProxyLoaderChoiceDemo.java
java ProxyLoaderChoiceDemo
~~~

Kết quả:

~~~text
good sees all: true
masked sees Greeter: true
masked sees Tag: false
Xin chào, Linh
implements Tag: true
proxy loader == good: true
masked rejected: IllegalArgumentException
~~~

Giải thích:

- `good` nhìn thấy cả hai interface nên có thể tạo lớp proxy triển khai `Greeter` và `Tag`.
- `masked` vẫn nhìn thấy `Greeter` nhờ ủy quyền cho `good` nhưng từ chối `Tag`. **Chỉ cần một interface không nhìn thấy** là mảng interface không hợp lệ với loader đó.
- Proxy được tạo bằng `good` có thể ép về `Greeter` và kiểm tra bằng `instanceof Tag`.
- `InvocationHandler` nhận các lời gọi qua proxy; ví dụ cũng xử lý `equals`, `hashCode` và `toString` thừa hưởng từ `Object` để tránh trả sai kiểu dữ liệu. Logic nghiệp vụ của `greet` độc lập với quy tắc chọn loader.

Trong ứng dụng thực, đừng sửa loader để “giấu” interface như `MaskingLoader`. Hãy chọn một loader **thật sự thấy đúng tất cả kiểu** hoặc thiết kế một API dùng chung qua ranh giới plugin.

## 6. Chọn `getClassLoader()`, context class loader hay system loader?

| Ứng viên | Khi nào cân nhắc | Giới hạn |
| --- | --- | --- |
| `MyApi.class.getClassLoader()` | Một hoặc nhiều interface thuộc API dùng chung, loader này thấy đủ tất cả | Không tự động thấy interface chỉ có trong plugin |
| `pluginInterface.getClassLoader()` | Interface do plugin nạp; loader plugin có thể ủy quyền thấy các API chung | Phải xác minh từng interface, kể cả API từ phía ứng dụng |
| `Thread.currentThread().getContextClassLoader()` (TCCL) | Framework/container đặt loader ngữ cảnh để khám phá kiểu theo yêu cầu hiện tại | Có thể là `null`, bị thay đổi, hoặc thấy bản sao không đúng của interface |
| `ClassLoader.getSystemClassLoader()` | Ứng dụng classpath đơn giản và tất cả interface đều có thể tìm bởi system loader | Không phải loader chung cho mọi môi trường plugin/container |
| `null` (bootstrap loader) | Interface có thể phân giải thông qua bootstrap loader và các quy tắc khác cho phép | Không phù hợp với interface ứng dụng thông thường |

**TCCL là một gợi ý về ngữ cảnh nạp lớp, không phải đảm bảo tính đúng.** Ví dụ một plugin có thể cần TCCL để nạp lớp triển khai hoặc tài nguyên, nhưng loader dùng cho `Proxy` vẫn phải vượt qua bài kiểm tra danh tính interface. Nếu framework tạm đổi TCCL, hãy bảo đảm khôi phục đúng loader cũ sau khi hoàn tất công việc.

Đoạn kiểm tra khi cần xem xét TCCL:

~~~java
ClassLoader context = Thread.currentThread().getContextClassLoader();
if (!seesAllInterfaces(context, interfaces)) {
    throw new IllegalArgumentException(
            "Context class loader cannot see the requested interfaces");
}
Object proxy = Proxy.newProxyInstance(context, interfaces, handler);
~~~

*Fragment này giả định hàm `seesAllInterfaces`, biến `interfaces` và `handler` đã được định nghĩa; vẫn cần xử lý các giới hạn proxy khác.*

Khi có sẵn interface `Class<?>`, ưu tiên truyền chúng trực tiếp thay vì ghép tên để tải qua TCCL rồi hy vọng nhận đúng kiểu.

## 7. Cùng tên nhưng khác `ClassLoader`: lỗi khó tìm trong hệ plugin

Giả sử ứng dụng và plugin **đều nạp riêng** bytecode của `com.example.api.Service`:

~~~text
AppLoader    → Class A: com.example.api.Service
PluginLoader → Class B: com.example.api.Service

A.getName().equals(B.getName()) == true
A == B                             == false
~~~

Đây là **minh họa hai kiểu runtime khác nhau**, không phải tên lớp của một chương trình ví dụ độc lập. Nếu `PluginLoader` tìm thấy `B` khi yêu cầu `Service`, nhưng bạn truyền `A` vào `Proxy.newProxyInstance`, điều kiện `Class.forName(A.getName(), false, PluginLoader) == A` thất bại. Ngay cả khi tạo được proxy triển khai `B`, mã ứng dụng đang mong `A` vẫn không thể ép proxy thành `A`: việc trùng tên không tạo quan hệ kiểu.

Cách tổ chức thường dùng:

- Đặt interface hợp đồng chung vào một thư viện/API được nạp **một lần** bởi loader dùng chung; loader plugin ủy quyền tới bản chung thay vì chứa một bản riêng.
- Nếu plugin có interface riêng, truyền `Class<?>` thực tế mà plugin đang dùng, rồi chọn một loader phân giải chính xác cả interface riêng và hợp đồng chung.
- Nếu các interface bắt buộc nằm trong hai vùng loader không thể nhìn thấy lẫn nhau, **không có một tham số `loader` thần kỳ** để tạo proxy cho cả hai. Cần điều chỉnh cấu trúc loader, tách proxy hoặc dùng một lớp chuyển tiếp qua API chung.

Một `ClassLoader` được đưa vào `Proxy.newProxyInstance` **có thể khác** loader trực tiếp định nghĩa một số interface trong danh sách: ủy quyền có thể trả về đúng `Class` của loader cha. Điều kiện là **danh tính được phân giải trùng khớp**, không phải mọi `getClassLoader()` đều bằng nhau.

## 8. Những ràng buộc khác thường bị nhầm thành lỗi class loader

Theo API `Proxy.newProxyInstance` của Java 17+:

- Mỗi phần tử của mảng `interfaces` phải là **interface hợp lệ**, không phải class, primitive, interface `sealed` hoặc `hidden`; không được truyền trùng cùng đối tượng `Class<?>`.
- **Mọi kiểu tham chiếu được dùng trong chữ ký của các phương thức `public`** của các interface (kể cả phương thức kế thừa) cũng phải nhìn thấy theo tên qua loader đã chọn. Không đủ nếu chỉ kiểm tra tên của chính interface.
- Với nhiều interface **không `public`**, tất cả phải thuộc **cùng package và module**, được định nghĩa bởi loader được cung cấp và thỏa các điều kiện truy cập. Một loader nhìn thấy chúng chưa đủ để vượt qua hạn chế này.
- Các phương thức của nhiều interface có cùng tên và danh sách tham số phải có **kiểu trả về tương thích** theo quy tắc proxy. Thứ tự các interface còn có ý nghĩa khi có những chữ ký phương thức trùng nhau.
- Trong ứng dụng dùng **Java Platform Module System (JPMS)**, Java có quy tắc xác định package/module chứa lớp proxy, đôi khi sinh proxy trong *dynamic module*. Tránh mặc định rằng có thể truy cập `public` constructor của lớp proxy bằng Reflection từ module bất kỳ; sử dụng `Proxy.newProxyInstance` như API được thiết kế.

Lỗi phổ biến:

| Biểu hiện | Hướng chẩn đoán |
| --- | --- |
| `IllegalArgumentException` từ `newProxyInstance` | Kiểm tra interface có hợp lệ, có trùng, có thấy đúng danh tính qua loader, kiểu trong chữ ký và giới hạn package/module |
| `ClassNotFoundException` khi thử `Class.forName` | Loader không tìm được kiểu bằng tên đó |
| `ClassCastException` sau khi tạo proxy | Phía gọi có thể đang dùng một `Class` khác, hoặc proxy không triển khai interface mong đợi |
| `IllegalAccessException` khi tìm cách tự khởi tạo lớp proxy | Có thể do quy tắc truy cập proxy trong module; dùng `newProxyInstance` |
| `NoClassDefFoundError` hoặc lỗi liên kết khác | Kiểm tra dependency và khả năng nạp/liên kết các kiểu tham chiếu; đây không nhất thiết là lỗi so sánh interface |

Đừng khắc phục một cách máy móc bằng `setAccessible(true)`, thay đổi thứ tự interface hoặc ép kiểu không kiểm tra. Những thao tác đó không làm hai đối tượng `Class` khác nhau trở thành một.

## 9. Cách chẩn đoán khi proxy không tạo được

Khi gặp lỗi ở ranh giới framework/plugin, ghi nhận các giá trị sau cho **từng interface**:

~~~java
for (Class<?> i : interfaces) {
    System.out.println("interface: " + i.getName());
    System.out.println("defined by: " + i.getClassLoader());
    System.out.println("module: " + i.getModule());
    try {
        Class<?> resolved = Class.forName(i.getName(), false, loader);
        System.out.println("same Class: " + (resolved == i));
        System.out.println("resolved by: " + resolved.getClassLoader());
    } catch (ClassNotFoundException ex) {
        System.out.println("not visible by name");
    }
}
~~~

*Fragment dùng biến `loader` và `interfaces` đã có. Tên do `getName()` trả về không đủ để kết luận kiểu giống nhau; kết quả `same Class` mới trả lời câu hỏi quan trọng.*

Đồng thời kiểm tra loader của các kiểu xuất hiện trong tham số/kiểu trả về của phương thức `public`, mối quan hệ giữa các module, tính `public` của interface và xem trong classpath/plugin có nhiều bản thư viện hợp đồng hay không. Chỉ bật log/diagnostics phù hợp; đừng phụ thuộc vào tên tự sinh của lớp proxy như một API ổn định.

## 10. Tự kiểm tra kiến thức

1. `Proxy.newProxyInstance` yêu cầu điều gì ở mỗi interface `i` với loader `cl`? **`Class.forName(i.getName(), false, cl) == i`.**
2. Nếu `AppLoader` định nghĩa API chung, `PluginLoader` ủy quyền về cha và tự nạp interface plugin, loader nào có khả năng tạo proxy cho cả hai? **`PluginLoader`, khi kiểm tra danh tính và các ràng buộc khác đều đạt.**
3. `interfaceA.getName().equals(interfaceB.getName())` là `true` nhưng `interfaceA == interfaceB` là `false`. Có thể coi chúng là cùng kiểu không? **Không.** Tên giống nhau không bảo đảm định danh cùng loader.
4. TCCL có phải luôn là lựa chọn đúng? **Không.** Phải xác minh nó thấy đúng mọi interface.
5. Một loader thấy đúng tất cả interface, có chắc tạo được proxy? **Chưa chắc.** Còn kiểu trong public method signatures, ràng buộc `non-public` interface/module, method trùng chữ ký, `sealed`/`hidden`, v.v.
6. Vì sao có thể gặp `ClassCastException` sau khi tạo proxy thành công? **Mã ép kiểu có thể tham chiếu interface khác danh tính hoặc interface không được proxy triển khai.**

## 11. Kết luận

Khi tạo dynamic proxy, hãy chọn **một class loader phân giải được đúng mọi interface với cùng định danh `Class`**, rồi xác minh thêm các ràng buộc kiểu, package và module. Trong chương trình đơn giản, loader của API interface thường đủ. Trong framework/plugin, quyết định phải dựa trên cấu trúc nạp lớp thực tế và cơ chế ủy quyền. Điều kiện `Class.forName(i.getName(), false, loader) == i` là phép kiểm tra xuất phát đáng tin cậy để tránh nhầm “cùng tên” với “cùng kiểu”.

Tài liệu tham khảo:
- [Java SE 21 — `Proxy.newProxyInstance` và các hạn chế đối với interface](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Proxy.html)
- [Java SE 21 — `Class.forName` và danh tính lớp](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html)
- [Java SE 21 — `ClassLoader` và cơ chế nạp lớp](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-004">Điều kiện interface public, non-public, hidden hoặc sealed</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện interface `public`, `non-public`, `hidden` và `sealed` khi dùng Java Dynamic Proxy

## 1. Mục tiêu và quy tắc quyết định

`java.lang.reflect.Proxy` tạo một lớp tại runtime để triển khai **một hoặc nhiều interface**, rồi chuyển lời gọi phương thức trên proxy đến `InvocationHandler.invoke`. Việc một giá trị `Class<?>` có `isInterface() == true` **chưa đủ** để dùng được với `Proxy.newProxyInstance`.

Trong Java 17+, tập interface đầu vào phải thỏa đồng thời những ràng buộc về **loại interface**, **class loader**, **khả năng nhìn thấy kiểu**, **package/module** và **chữ ký phương thức**. Hai trường hợp đặc biệt bị từ chối ngay từ thiết kế là **hidden interface** và **sealed interface**.

| Interface đầu vào | Có thể dùng với `Proxy.newProxyInstance`? | Điều kiện chính |
| --- | --- | --- |
| `public`, không `hidden`, không `sealed` | **Có**, nếu hợp lệ | Interface và các kiểu trong chữ ký public phải nhìn thấy qua loader truyền vào; cách đóng gói của module ảnh hưởng vị trí/quyền truy cập lớp proxy. |
| `non-public` (ví dụ package-private), không `hidden`, không `sealed` | **Có điều kiện** | Mọi interface non-public trong cùng lần tạo phải ở **cùng package và cùng module**, được định nghĩa bởi loader được truyền vào; module chứa chúng phải truy cập được các interface cần triển khai. |
| `hidden interface` | **Không** | API `Proxy` yêu cầu interface **không hidden**; vi phạm gây `IllegalArgumentException`. |
| `sealed interface` | **Không** | API `Proxy` yêu cầu interface **không sealed**; vi phạm gây `IllegalArgumentException` dù interface đó là `public`. |

Điểm cần nhớ: **`non-public` không có nghĩa là không thể proxy**. Ngược lại, `public` cũng không tự bảo đảm thành công nếu class loader, module hoặc chữ ký không hợp lệ.

## 2. Ba khái niệm dễ bị trộn lẫn

### 2.1. Quyền truy cập Java của interface

- `public interface Api` có modifier `public`: `Modifier.isPublic(Api.class.getModifiers())` là `true`.
- `interface InternalApi` ở cấp package, hoặc interface thành viên không khai báo `public`, có thể là `non-public`: `Modifier.isPublic(...)` là `false`.
- Khả năng nhìn thấy kiểu từ mã Java của người gọi và khả năng JVM tạo proxy hợp lệ là **hai câu hỏi khác nhau**. Có `Class<?>` của interface không tự xóa các giới hạn package/module.

Một **public interface nằm trong package không được export** của named module vẫn có modifier `public`. Nó **không tự biến thành non-public interface** chỉ vì module không export package đó.

### 2.2. Package, runtime module và class loader

Tên package giống nhau chưa chắc cùng runtime package/module trong JVM. Khi nhiều interface non-public cùng tham gia, JDK yêu cầu chúng cùng package **và cùng module**, và module/loader phải thỏa quy tắc định nghĩa lớp proxy.

Với mọi interface `i`, class loader `loader` được truyền vào `Proxy.newProxyInstance` cần nhìn thấy đúng **cùng đối tượng `Class`** theo kiểm tra:

```java
Class.forName(i.getName(), false, loader) == i
```

Đây là **biểu thức minh họa điều kiện Javadoc**, không phải mã độc lập và không phải khuyến nghị gọi `Class.forName` với hidden interface. Loader thấy một kiểu **cùng tên nhưng khác định danh `Class`** vẫn không đạt yêu cầu. Ngoài các interface, các kiểu xuất hiện trong chữ ký của mọi phương thức `public` (kể cả phương thức kế thừa từ superinterface) cũng phải nhìn thấy bằng tên qua loader này.

### 2.3. Interface bình thường, hidden và sealed

- **Interface bình thường** có tên nhị phân được loader tra cứu; `public` hay `non-public` là một chiều phân loại khác.
- **Hidden interface** được định nghĩa, chẳng hạn qua `MethodHandles.Lookup#defineHiddenClass`. `Class.isHidden()` trả `true`; nó không có binary name có thể được tra cứu theo cách thông thường. `Proxy` từ chối loại này.
- **Sealed interface** giới hạn những kiểu được phép triển khai qua `permits`; `Class.isSealed()` trả `true`. JDK `Proxy` không nhận sealed interface làm đầu vào, kể cả khi một cách thiết kế khác có thể tuân theo `permits`.

`hidden` và `sealed` là **hai thuộc tính khác nhau**. Một interface `non-sealed` trong phân cấp có sealed superinterface cần được đánh giá theo **chính đối tượng `Class<?>` đưa vào mảng interfaces**; đừng kết luận nó bị từ chối chỉ vì tổ tiên sealed. Tuy nhiên các điều kiện loader, module và chữ ký vẫn phải đạt.

## 3. Package/module của lớp proxy được chọn thế nào?

JDK chọn quyền truy cập của **lớp proxy được sinh ra** dựa trên tập interface:

| Tập interface | Quy tắc về lớp proxy |
| --- | --- |
| Tất cả interface đều `public`, và tất cả ở package được export hoặc open | Proxy class là `public` trong một package được export vô điều kiện nhưng **không open**; tên package/module cụ thể không được bảo đảm. |
| Tất cả interface đều `public`, nhưng ít nhất một interface ở package không export **và** không open | Proxy class là `public` trong package không export, không open của **dynamic module**; mã ngoài module không được mặc nhiên truy cập trực tiếp lớp proxy. |
| Có ít nhất một interface `non-public` | Proxy class là **non-public**, ở **cùng package và module** với các non-public interface. Tất cả non-public interface phải cùng package/module; nếu khác nhau thì không thể tạo proxy đó. |

**Tính khả dụng của proxy instance khác với quyền truy cập trực tiếp proxy class.** Người gọi thường chỉ cần:

```java
Object instance = Proxy.newProxyInstance(loader, interfaces, handler);
```

Sau đó ép kiểu về interface mà mã gọi được phép truy cập. Không nên dựa vào tên lớp có dạng `$Proxy...` hoặc tự gọi constructor của proxy class bằng Reflection. Với proxy trong dynamic module đóng, tự gọi constructor qua Reflection có thể bị `IllegalAccessException`. `Proxy.getProxyClass` cũng đã bị đánh dấu deprecated; ưu tiên `newProxyInstance`.

Nếu vừa có interface public vừa có non-public, quyền truy cập proxy class đi theo **interface hạn chế nhất**; thêm một interface public không làm proxy class non-public trở thành public.

## 4. Ví dụ hoàn chỉnh chạy được trên Java 17+

Ví dụ này minh họa **bốn trường hợp** trong một chương trình: public hợp lệ, non-public hợp lệ, sealed bị từ chối, hidden bị từ chối. Chương trình còn kiểm chứng trường hợp trộn public và non-public.

Lưu thành file **`ProxyInterfaceRulesDemo.java`** (không thêm khai báo `package` để chạy trực tiếp từ cùng thư mục):

```java
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

public class ProxyInterfaceRulesDemo {
    public interface PublicApi {
        String message();
    }

    interface InternalApi {
        String internal();
    }

    public sealed interface SealedApi permits Approved {
        String execute();
    }

    public static final class Approved implements SealedApi {
        public String execute() { return "approved"; }
    }

    interface HiddenSeed {
        String hidden();
    }

    public static void main(String[] args) throws Throwable {
        ClassLoader loader = ProxyInterfaceRulesDemo.class.getClassLoader();
        InvocationHandler handler = (proxy, method, parameters) ->
                switch (method.getName()) {
                    case "message" -> "public-ok";
                    case "internal" -> "internal-ok";
                    default -> throw new UnsupportedOperationException(method.getName());
                };

        PublicApi publicProxy = (PublicApi) Proxy.newProxyInstance(
                loader, new Class<?>[]{PublicApi.class}, handler);
        System.out.println("public: " + publicProxy.message()
                + ", proxyPublic=" + Modifier.isPublic(publicProxy.getClass().getModifiers()));

        InternalApi internalProxy = (InternalApi) Proxy.newProxyInstance(
                loader, new Class<?>[]{InternalApi.class}, handler);
        System.out.println("non-public: " + internalProxy.internal()
                + ", proxyPublic=" + Modifier.isPublic(internalProxy.getClass().getModifiers()));

        Object mixed = Proxy.newProxyInstance(
                loader, new Class<?>[]{PublicApi.class, InternalApi.class}, handler);
        System.out.println("mixed: " + ((PublicApi) mixed).message()
                + ", proxyPublic=" + Modifier.isPublic(mixed.getClass().getModifiers()));

        System.out.println("sealed flag: " + SealedApi.class.isSealed());
        try {
            Proxy.newProxyInstance(loader, new Class<?>[]{SealedApi.class}, handler);
            System.out.println("sealed: unexpected success");
        } catch (IllegalArgumentException expected) {
            System.out.println("sealed rejected: " + expected.getClass().getSimpleName());
        }

        String resource = "/" + HiddenSeed.class.getName().replace('.', '/') + ".class";
        try (InputStream stream = HiddenSeed.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Class bytes not found");
            byte[] bytes = stream.readAllBytes();
            Class<?> hidden = MethodHandles.lookup()
                    .defineHiddenClass(bytes, false).lookupClass();
            System.out.println("hidden flags: interface=" + hidden.isInterface()
                    + ", hidden=" + hidden.isHidden());
            try {
                Proxy.newProxyInstance(loader, new Class<?>[]{hidden}, handler);
                System.out.println("hidden: unexpected success");
            } catch (IllegalArgumentException expected) {
                System.out.println("hidden rejected: " + expected.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch và chạy trong thư mục có file nguồn bằng JDK 17 trở lên:

```bash
javac --release 17 ProxyInterfaceRulesDemo.java
java ProxyInterfaceRulesDemo
```

Output:

```text
public: public-ok, proxyPublic=true
non-public: internal-ok, proxyPublic=false
mixed: public-ok, proxyPublic=false
sealed flag: true
sealed rejected: IllegalArgumentException
hidden flags: interface=true, hidden=true
hidden rejected: IllegalArgumentException
```

**Giải thích kết quả:**

1. `PublicApi` là interface thành viên có `public`; proxy class tạo ra có `public`, handler trả `"public-ok"`.
2. `InternalApi` là interface thành viên không khai báo `public` nên non-public. Vì tạo một mình và đáp ứng quy tắc package/module/loader, proxy được tạo thành công nhưng proxy class không `public`.
3. Khi đưa đồng thời `PublicApi` và `InternalApi` vào mảng, proxy vẫn hợp lệ nhưng proxy class non-public theo interface hạn chế hơn. Vẫn có thể gọi `PublicApi.message()` qua kiểu interface public.
4. `SealedApi.class.isSealed()` là `true`; JDK từ chối ngay yêu cầu proxy interface đó bằng `IllegalArgumentException`. Một class `Approved` được liệt kê trong `permits` không làm cho JDK Proxy được phép tự bổ sung subclass.
5. `HiddenSeed` đầu tiên là interface **bình thường** với file `.class` do `javac` tạo ra. `defineHiddenClass` định nghĩa một **hidden interface mới** từ bytecode đó, rồi trả về `Class<?>` có `isInterface() == true` và `isHidden() == true`. Đây chính là loại JDK Proxy không nhận. Không nên đồng nhất `HiddenSeed.class` ban đầu với đối tượng `Class<?> hidden` mới.
6. Tên lớp proxy và tên hidden interface có thể thay đổi giữa các lần chạy hoặc các bản JDK; ví dụ chỉ in những đặc tính được API bảo đảm. `InvocationHandler` trong ví dụ xử lý hai phương thức nghiệp vụ đang gọi; một ứng dụng thực tế cần định nghĩa cách xử lý `equals`, `hashCode`, `toString` và các phương thức khác nếu được gọi.

Ví dụ đã được kiểm chứng bằng `javac --release 17` và thực thi trên JDK 24. Nó chỉ dùng API có sẵn từ Java 17, gồm `Class.isHidden()`, `Class.isSealed()` và `Lookup.defineHiddenClass`. Phần đọc `.class` cần các file được `javac` tạo nằm trên classpath khi chạy; lệnh ở trên đáp ứng điều đó.

## 5. Vì sao hidden và sealed không tạo proxy được?

### 5.1. Hidden interface

`MethodHandles.Lookup#defineHiddenClass(byte[], ...)` tạo hidden class hoặc hidden interface từ bytecode. Kiểu hidden không có binary name để loader thông thường tìm lại bằng tên; không thể giả định rằng `Class.forName(hidden.getName(), false, loader)` sẽ tìm ra chính nó.

Javadoc `Proxy.newProxyInstance` quy định rõ các phần tử trong mảng `interfaces` phải là interface **non-hidden**. Kể cả khi đã giữ tham chiếu `Class<?>` của hidden interface trong tay, việc truyền nó vào API này vẫn bị loại. Đây là **ràng buộc của cơ chế JDK dynamic proxy**, không có nghĩa mọi kỹ thuật sinh bytecode hoặc mọi framework tạo adapter đều không thể tương tác với đối tượng hidden theo cách khác.

### 5.2. Sealed interface

Ví dụ hợp lệ về khai báo:

```java
sealed interface Operation permits ReadOperation {}

final class ReadOperation implements Operation {}
```

Hai khai báo trên là **mảnh minh họa ở phạm vi package**, cần đặt trong file nguồn hợp lệ. `Operation.class.isSealed()` là `true`. Khi gọi `Proxy.newProxyInstance(..., new Class<?>[]{Operation.class}, ...)`, JDK từ chối `Operation` vì chính sách `non-sealed` của API. Không thể sửa bằng cách đưa proxy sinh động vào danh sách `permits` trong source code như với lớp cài đặt đã biết.

**Phân biệt `non-sealed` trong Java với “không bị sealed”:** từ khóa `non-sealed` được dùng cho một interface/class trực tiếp mở lại khả năng triển khai trong phân cấp sealed. `Class.isSealed()` của nó là `false`. Nếu bản thân interface đầu vào không hidden, không sealed và thỏa các điều kiện khác, riêng lịch sử kế thừa từ sealed superinterface không phải là lý do để từ chối nó.

## 6. Những kiểm tra khác vẫn phải làm cho interface public/non-public

Ngay cả khi từng interface có `isInterface() == true`, `isHidden() == false`, `isSealed() == false`, `Proxy.newProxyInstance` vẫn có thể ném `IllegalArgumentException`:

1. **Trùng interface:** cùng một đối tượng `Class<?>` xuất hiện hai lần trong mảng `interfaces`.
2. **Sai class loader:** interface không nhìn thấy được bằng tên qua loader được chỉ định, hoặc loader tìm ra một `Class` khác cùng tên; các kiểu được nhắc trong chữ ký `public` cũng phải nhìn thấy.
3. **Non-public khác package/module:** hai interface non-public của hai package khác nhau không thể cùng được một lớp proxy triển khai. Ngay cả khi tên package giống nhau, khác module vẫn không hợp lệ theo điều kiện của JDK.
4. **Loader không định nghĩa non-public interface:** loader chỉ *nhìn thấy* nó thông qua delegation chưa đủ nếu không đúng yêu cầu loader định nghĩa theo Javadoc.
5. **Module chứa non-public interface thiếu khả năng truy cập:** khi trộn non-public với các interface khác, module chứa proxy phải truy cập được những interface được triển khai.
6. **Trùng chữ ký nhưng kiểu trả về không tương thích:** các phương thức cùng tên và danh sách tham số mà kiểu trả về nguyên thủy/`void` khác nhau sẽ xung đột; với kiểu trả về tham chiếu, cần có một kiểu trả về là subtype phù hợp của các kiểu còn lại.
7. **Giới hạn JVM:** số lượng interface hoặc giới hạn về class file có thể khiến việc sinh proxy không thực hiện được.

Nếu `interfaces` hoặc phần tử của nó là `null`, hoặc `handler` là `null`, API có thể ném `NullPointerException` theo hợp đồng; đừng gom mọi thất bại vào `IllegalArgumentException`.

**Module access khác với điều kiện export/open:** public interface trong một package đóng có thể được đưa vào proxy theo quy tắc **dynamic module** của JDK. Việc tạo proxy thành công không biến package đó thành public API cho mọi mã Java khác. Cần phân biệt tạo proxy, ép kiểu hợp lệ từ vị trí người gọi và truy cập trực tiếp lớp proxy đã được sinh.

## 7. Chẩn đoán nhanh trước khi tạo proxy

Đoạn phương thức dưới đây dùng để **lọc sơ bộ** (đặt trong một class có `import java.lang.reflect.Modifier;`). Nó chỉ kiểm tra **loại interface**; kết quả `"candidate"` không có nghĩa mọi yêu cầu khác đã đạt.

```java
static String precheck(Class<?> type) {
    if (type == null) return "null";
    if (!type.isInterface()) return "not an interface";
    if (type.isHidden()) return "hidden interface: unsupported";
    if (type.isSealed()) return "sealed interface: unsupported";
    return Modifier.isPublic(type.getModifiers())
            ? "candidate: public"
            : "candidate: non-public; check package, module and loader";
}
```

Khi điều tra một thất bại, nên ghi nhận (mà không phụ thuộc vào tên proxy do JDK sinh):

- `type.getName()`, `type.isInterface()`, `type.isHidden()`, `type.isSealed()`, `Modifier.isPublic(type.getModifiers())`.
- `type.getPackageName()`, `type.getModule()`, `type.getClassLoader()` và loader thực sự truyền vào `Proxy.newProxyInstance`.
- Toàn bộ interface trong mảng, thứ tự và các kiểu xuất hiện trong chữ ký `public`, kể cả interface kế thừa.
- Loại exception và nguyên nhân; tránh dựa vào chuỗi thông báo exception chính xác vì thông báo có thể thay đổi theo JDK.

Ví dụ **không hợp lệ** về trùng interface:

```java
Proxy.newProxyInstance(
        PublicApi.class.getClassLoader(),
        new Class<?>[]{PublicApi.class, PublicApi.class},
        handler);
```

Đây là **mảnh minh họa** sử dụng `PublicApi` và `handler` đã khai báo ở chương trình trước, không phải chương trình độc lập. Lỗi này là `IllegalArgumentException` dù `PublicApi` hoàn toàn public, không hidden và không sealed.

## 8. Hiểu đúng các lỗi hay gặp

| Quan niệm hoặc cách sửa sai | Cách xử lý đúng |
| --- | --- |
| “Chỉ có `public interface` mới dùng được JDK Proxy.” | Non-public hợp lệ nếu đáp ứng chặt chẽ package/module/loader. |
| “Non-public interface đồng nghĩa package không export.” | Modifier của interface và mức export/open của package là hai khái niệm khác nhau. |
| “Có `Class<?>` của hidden interface thì vẫn proxy được.” | `isHidden() == true` bị JDK Proxy loại dù đã có đối tượng `Class`. |
| “Sealed interface public thì được.” | `isSealed() == true` bị từ chối độc lập với modifier `public`. |
| “Đổi loader sang `Thread.currentThread().getContextClassLoader()` chắc chắn sửa lỗi.” | Phải chứng minh loader chỉ định thấy đúng identity của mọi interface và mọi kiểu trong chữ ký, cùng quy tắc dành cho non-public. |
| “Đã tạo proxy thành công thì có thể gọi constructor lớp `$Proxy...`.” | Quyền truy cập lớp proxy có thể bị module đóng; dùng `Proxy.newProxyInstance`. |
| “`newProxyInstance` chỉ kiểm tra `isInterface()`.” | Javadoc còn ràng buộc sealed/hidden, trùng lặp, loader, chữ ký, package/module, VM limits. |
| “Mỗi interface có proxy riêng được thì ghép chúng luôn được.” | Tập interface kết hợp có thể xung đột về non-public package/module hoặc kiểu trả về của phương thức. |

## 9. Bài tập và câu hỏi tự kiểm tra

1. Hai interface **package-private** ở cùng package và cùng module, được định nghĩa bởi đúng class loader, có thể được đưa chung vào `newProxyInstance` không? Hãy giải thích các điều kiện còn lại vẫn phải kiểm tra.
2. Một `public interface` nằm trong package **không export, không open** của named module có bị từ chối chỉ vì package đóng không? Lớp proxy sẽ nằm ở đâu theo quy tắc JDK?
3. Trong ví dụ, vì sao `InternalApi` tạo được proxy dù `Modifier.isPublic(InternalApi.class.getModifiers()) == false`?
4. Vì sao `HiddenSeed.class` lúc mới biên dịch khác với `Class<?> hidden` do `defineHiddenClass` trả về?
5. Nếu một interface có `isSealed() == true` và đồng thời là `public`, điều kiện nào quyết định kết quả của `newProxyInstance`?
6. Nếu hai interface đều `public`, không hidden/sealed nhưng khai báo cùng phương thức `value()` với hai kiểu trả về nguyên thủy khác nhau, vì sao yêu cầu proxy có thể thất bại?

**Tự kiểm tra trước khi thiết kế thư viện:** Trước tiên phân loại `isInterface / isHidden / isSealed`; sau đó kiểm tra loader, tập interface, chữ ký, package/module và quyền truy cập từ mã sử dụng proxy. Dùng **`Proxy.newProxyInstance`** làm API tạo instance, sau đó làm việc qua các interface mà mã người gọi thực sự được phép truy cập.

## 10. Tài liệu chính thức

- [Oracle Java 17 — `java.lang.reflect.Proxy`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): Restrictions, Package and Module Membership of Proxy Class, Dynamic Modules và ngoại lệ của `newProxyInstance`.
- [Oracle Java 17 — `Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `isInterface`, `isHidden`, `isSealed`, `getModifiers`, `getModule` và `getClassLoader`.
- [Oracle Java 17 — `MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html): `defineHiddenClass` và cách tạo hidden class/interface.
- [Java Language Specification 17 — Sealed Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.1.1.4): ngữ nghĩa `sealed`, `permits`, `non-sealed`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-005">Gọi proxy được chuyển thành InvocationHandler.invoke</a>

<details>
<summary>Xem chi tiết</summary>

# Lời gọi qua dynamic proxy được chuyển tới `InvocationHandler.invoke`

## 1. Ý nghĩa của cơ chế chuyển tiếp lời gọi

Với **JDK dynamic proxy** (`java.lang.reflect.Proxy`), ứng dụng tạo một đối tượng tại runtime triển khai một hoặc nhiều **interface**. Khi code gọi một phương thức qua interface trên đối tượng proxy, mã của lớp proxy do JDK tạo sẽ chuyển lời gọi đến `InvocationHandler.invoke(proxy, method, args)`. Handler quyết định phải làm gì: trả kết quả trực tiếp, gọi một đối tượng thật (*target*), kiểm tra quyền, ghi log, tính thời gian hoặc ném ngoại lệ.

Điểm cốt lõi của bài này là **lời gọi phương thức trên proxy kích hoạt handler; không mặc định chạy phương thức cùng tên của một đối tượng đích**. Chỉ khi handler chủ động gọi target thì nghiệp vụ của target mới chạy.

Ví dụ về đường đi:

```text
Caller
  |
  | service.greet("Lan")  // service là JDK proxy
  v
Phương thức greet trong lớp proxy do JDK tạo
  |
  | handler.invoke(proxy, Method(greet), Object[]{"Lan"})
  v
InvocationHandler
  |-- xử lý ngay và trả String
  |-- hoặc kiểm tra/log rồi gọi method.invoke(target, args)
  |-- hoặc ném exception
  v
Giá trị trả về / exception được chuyển về caller
```

Bản thân interface chỉ quy định hợp đồng, chẳng hạn `GreetingService.greet(String)`; proxy cung cấp implementation tại runtime thông qua handler. Điều này hữu ích khi muốn chèn hành vi chung quanh nhiều lời gọi mà code sử dụng interface không phải đổi cách gọi.

## 2. Tạo đối tượng proxy và điều kiện áp dụng

Ví dụ tạo proxy ở mức API:

```java
GreetingService service = (GreetingService) Proxy.newProxyInstance(
    GreetingService.class.getClassLoader(),
    new Class<?>[] { GreetingService.class },
    handler
);
```

Đây là **đoạn minh họa**, giả định đã định nghĩa `GreetingService` và `handler`. Ba đối số có vai trò:

1. **Class loader:** dùng để định nghĩa lớp proxy và giải quyết các interface được yêu cầu; loader phải phù hợp với khả năng nhìn thấy các interface.
2. **Mảng interface:** tập interface mà proxy triển khai. Lời gọi qua các interface này được chuyển tới handler. Các interface cần thỏa điều kiện hợp lệ của API (chẳng hạn phải là interface, không trùng lặp).
3. **`InvocationHandler`:** đối tượng xử lý các lời gọi được chuyển tiếp.

`Proxy.newProxyInstance(...)` trả về `Object` nên thường cần ép kiểu sang interface. Class của proxy là một lớp được sinh ở runtime, kế thừa `Proxy` và triển khai các interface đã chỉ định. `Proxy` của JDK **không tự tạo subclass của một concrete class bất kỳ**; nếu cần thay thế hành vi của concrete class, phải chọn cơ chế thích hợp khác.

Từ phía code sử dụng, cú pháp trông bình thường:

```java
String message = service.greet("Lan");
```

Dù caller gọi như một method Java thông thường, với `service` là proxy thì lời gọi ấy đi vào handler.

## 3. Bốn thành phần của `InvocationHandler.invoke`

Interface `InvocationHandler` khai báo:

```java
Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable;
```

| Thành phần | Ý nghĩa |
| --- | --- |
| `proxy` | Chính đối tượng proxy nhận lời gọi, không phải target nghiệp vụ. |
| `method` | Đối tượng `java.lang.reflect.Method` mô tả phương thức được gọi qua proxy: tên, tham số, kiểu trả về, annotation và declaring class. |
| `args` | Giá trị các đối số theo thứ tự khai báo, được đóng hộp khi đối số thuộc kiểu primitive; có thể là `null` nếu phương thức không có tham số. |
| Giá trị trả về / `throws Throwable` | Handler trả giá trị phù hợp với chữ ký phương thức được gọi hoặc ném lỗi; lớp proxy chuyển kết quả hay ngoại lệ về caller theo các quy tắc Java. |

Lưu ý `method` là **metadata mô tả phương thức**, còn `args` là **dữ liệu của lần gọi hiện tại**. Hai lời gọi đến cùng một method có thể đi qua handler với các giá trị `args` khác nhau.

Việc chuyển tiếp không tương đương `method.invoke(proxy, args)` trong handler. Cách gọi đó thường quay ngược vào chính proxy, khiến `invoke` bị gọi lặp lại và có thể dẫn tới `StackOverflowError`. Nếu muốn ủy quyền, hãy gọi method lên một **target riêng có kiểu tương thích** hoặc thực hiện nghiệp vụ trực tiếp.

## 4. Ví dụ Java 17+ hoàn chỉnh: gọi proxy, handler nhận lời gọi

Tạo tệp `ProxyInvokeDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class ProxyInvokeDemo {
    public interface GreetingService {
        String greet(String name);
        void ping();
    }

    public static void main(String[] args) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            System.out.println("invoke: " + method.getName());

            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "GreetingProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                };
            }

            return switch (method.getName()) {
                case "greet" -> "Xin chào, " + arguments[0];
                case "ping" -> null;
                default -> throw new UnsupportedOperationException(
                        method.getName());
            };
        };

        GreetingService service = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(),
                new Class<?>[] { GreetingService.class },
                handler
        );

        System.out.println(service.greet("Lan"));
        service.ping();
        System.out.println(service.toString());
        System.out.println(service.equals(service));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ProxyInvokeDemo.java
java ProxyInvokeDemo
```

Stdout chính xác:

```text
invoke: greet
Xin chào, Lan
invoke: ping
invoke: toString
GreetingProxy
invoke: equals
true
```

Diễn giải từng bước:

1. `Proxy.newProxyInstance(...)` tạo đối tượng triển khai `GreetingService`. Không có class `GreetingServiceImpl` nào cần được tạo.
2. `service.greet("Lan")` đến handler với `method.getName()` là `"greet"` và `arguments[0]` là `"Lan"`. Handler in tên phương thức rồi trả chuỗi. Caller nhận chuỗi đó như kết quả của `greet`.
3. `service.ping()` cũng tới handler. Phương thức `void` không mang giá trị về caller; handler trả `null`.
4. `service.toString()` và `service.equals(service)` đều được JDK proxy chuyển tới handler. Với ba phương thức đặc biệt `equals`, `hashCode` và `toString`, đối tượng `Method` được truyền vào có `Object.class` là declaring class. Ví dụ xử lý chúng một cách có chủ đích.
5. `proxy == arguments[0]` tại nhánh `equals` kiểm tra đồng nhất tham chiếu, tránh gọi lại `proxy.equals(...)` từ handler.

Ví dụ cố ý trả lời trực tiếp trong handler để làm rõ cơ chế. Thực tế handler thường còn ủy quyền đến target.

## 5. Trường hợp handler ủy quyền tới target bằng Reflection

Với proxy dùng để thêm log, đo thời gian hoặc bảo mật, handler thường bao lời gọi gốc theo trình tự: **trước lời gọi → gọi target → sau lời gọi**. Hãy phân biệt:

- `proxy`: đối tượng trung gian mà caller đang sử dụng.
- `target`: implementation nghiệp vụ thật, tồn tại độc lập.
- `method.invoke(target, arguments)`: lời gọi phản chiếu lên target; đây là hành động **do handler lựa chọn** thực hiện.

Ví dụ độc lập `ForwardingProxyDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ForwardingProxyDemo {
    public interface Calculator {
        int divide(int left, int right);
    }

    public static final class DefaultCalculator implements Calculator {
        @Override
        public int divide(int left, int right) {
            return left / right;
        }
    }

    public static void main(String[] args) {
        Calculator target = new DefaultCalculator();

        Calculator proxy = (Calculator) Proxy.newProxyInstance(
                Calculator.class.getClassLoader(),
                new Class<?>[] { Calculator.class },
                (proxyObject, method, actualArgs) -> {
                    System.out.println("before: " + method.getName());
                    try {
                        return method.invoke(target, actualArgs);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    } finally {
                        System.out.println("after: " + method.getName());
                    }
                }
        );

        System.out.println("result: " + proxy.divide(8, 2));

        try {
            proxy.divide(1, 0);
        } catch (ArithmeticException ex) {
            System.out.println("caught: " + ex.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac ForwardingProxyDemo.java
java ForwardingProxyDemo
```

Stdout:

```text
before: divide
after: divide
result: 4
before: divide
after: divide
caught: ArithmeticException
```

Trong lần gọi thứ nhất, `proxy.divide(8, 2)` chuyển tới handler; handler gọi `method.invoke(target, actualArgs)`, target tính `8 / 2` và trả số nguyên `4`. Reflection trả giá trị kiểu `Object` chứa `Integer`; lớp proxy gỡ hộp thành `int` cho caller.

Trong lần gọi thứ hai, target ném `ArithmeticException` vì chia cho 0. `Method.invoke` bọc ngoại lệ từ method đích trong `InvocationTargetException`. Handler lấy `ex.getCause()` và ném nguyên nhân thực sự; `finally` vẫn chạy. Vì vậy caller bắt được `ArithmeticException`. Nếu cứ ném nguyên `InvocationTargetException` ra từ handler, nó là checked exception không được khai báo trong chữ ký `divide` và có thể bị proxy bọc thêm trong `UndeclaredThrowableException`, khiến việc xử lý lỗi khó hơn.

Ở bài toán lớn, hãy kiểm soát việc truy cập target, xử lý ngoại lệ và bảo đảm logic ghi log/transaction không bỏ sót nhánh thất bại.

## 6. Kiểm chứng phạm vi chuyển lời gọi

Hai ví dụ trên cho thấy **cùng một cơ chế dispatch có hai cách xử lý khác nhau**:

- `ProxyInvokeDemo` không có target: `greet()` và `ping()` tới handler, rồi handler tự cung cấp kết quả. Vì thế `invoke: greet` xuất hiện trước chuỗi chào; `ping` trả `null` vì method là `void`.
- `ForwardingProxyDemo` có target: handler nhận `divide`, ghi `before`, rồi chủ động gọi `method.invoke(target, actualArgs)`. `finally` ghi `after` ngay cả khi target ném `ArithmeticException`.

Điểm thực nghiệm cần nhớ: nếu gọi `target.divide(8, 2)` trực tiếp, handler của proxy **không chạy**, dù target triển khai cùng interface. Nếu target tự gọi phương thức của chính nó qua `this`, lời gọi đó cũng không tự chuyển về proxy bên ngoài. Do vậy, quy tắc chặn lời gọi phụ thuộc **receiver thực tế**, không phụ thuộc việc method có tên giống nhau.

## 7. Điều gì diễn ra khi handler trả kết quả hoặc ném lỗi?

Khi handler hoàn tất, lớp proxy phải đáp ứng kiểu và ngoại lệ của method interface:

| Tình huống | Kết quả quan sát ở caller |
| --- | --- |
| Handler trả `"Xin chào, Lan"` cho method trả `String` | Caller nhận chính chuỗi đó |
| Handler trả `Integer(4)` cho method trả `int` | Proxy unbox thành `4` |
| Handler trả `null` cho method trả `void` | Hoàn thành không có giá trị trả về |
| Handler trả `null` cho method trả primitive | `NullPointerException` tại đường chuyển kiểu |
| Handler ném `RuntimeException` hoặc `Error` | Caller nhận trực tiếp theo hợp đồng unchecked |
| Handler ném checked exception ngoài `throws` của interface | Proxy bọc `UndeclaredThrowableException` |

Nếu target ném `ArithmeticException` trong lần `Method.invoke`, Reflection bọc nó vào `InvocationTargetException`; `ForwardingProxyDemo` chủ động ném `ex.getCause()` để caller nhìn thấy lỗi thật. Nếu bỏ bước này, wrapper checked có thể bị proxy bọc thêm lần nữa.

## 8. Ba đường gọi đặc biệt để không hiểu sai dispatch

**Các method của `Object`:** `equals(Object)`, `hashCode()`, `toString()` được proxy chuyển tới handler; `Method.getDeclaringClass()` ở ba lời gọi này là `Object.class`. Method `getClass()` là `final` nên không bị chặn theo cơ chế đó. Nếu in chính proxy trong handler, lời gọi `toString()` có thể quay lại handler gây đệ quy.

**Default method:** phần thân `default` của interface không tự chạy trước handler. Với Java 16+, handler có thể dùng `InvocationHandler.invokeDefault(proxy, method, args)` cho default method hợp lệ nếu quyết định thực thi phần thân. Lệnh `method.invoke(proxy, args)` lại gọi chính proxy và có nguy cơ đệ quy.

**Nhiều interface:** khi hai interface có cùng tên method và danh sách tham số, `Method` đưa vào handler có thể phụ thuộc thứ tự interface khi tạo proxy, và kiểu trả về hiệp biến còn có quy tắc riêng. Handler không nhận được thông tin caller đã ép proxy thành interface nào. Thiết kế dispatch theo contract đã rõ, không chỉ dựa vào tên method.

## 9. Bài tự kiểm tra

1. Trong `ProxyInvokeDemo`, bỏ đối tượng target đi có làm lời gọi `greet` thất bại không? **Không**, handler đang tự trả kết quả.
2. Trong `ForwardingProxyDemo`, vì sao `after: divide` vẫn được in khi chia cho 0? Vì handler dùng `finally` quanh lời gọi target.
3. Nếu thay receiver trong `method.invoke(target, actualArgs)` bằng proxy của cùng lời gọi, điều gì xảy ra? Dispatch quay về handler, dễ lặp vô hạn.
4. Nếu gọi method trả `int` mà handler trả `null`, lỗi có xuất hiện trước khi handler trả về không? Không nhất thiết; lớp proxy phải unbox kết quả và sẽ ném `NullPointerException`.
5. `service.toString()` có tới handler không? **Có**. `service.getClass()` có tới handler không? **Không**, vì `getClass` là `final` method của `Object`.

**Điều cần nhớ:** lời gọi trên **proxy instance** kích hoạt handler. Handler có thể tự xử lý, gọi target hoặc chạy default method được chọn; phía caller chỉ nhìn thấy kết quả/ngoại lệ phù hợp với chữ ký interface. Truy ra receiver của mỗi lời gọi là cách chắc chắn nhất để dự đoán hành vi.

### Tài liệu chính thức

- [Java SE 17 — InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html)
- [Java SE 17 — Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html)
- [Java SE 17 — Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-006">Method, proxy instance và args trong handler</a>

<details>
<summary>Xem chi tiết</summary>

# Method, proxy instance và args trong InvocationHandler (Java Dynamic Proxy)

## Mục tiêu và bức tranh tổng thể

Với JDK dynamic proxy, khi người gọi gọi một phương thức trên đối tượng proxy, lớp proxy do JDK tạo chuyển lời gọi đến `InvocationHandler.invoke(Object proxy, Method method, Object[] args)`. Handler quyết định xử lý lời gọi bằng logic riêng, ủy quyền sang target, hoặc ném ngoại lệ. Muốn viết handler đúng, bạn phải phân biệt **proxy instance**, **metadata của phương thức** và **giá trị các đối số**.

~~~text
Caller: api.add(2, 5)
          |
          v
JDK proxy implements interface Calculator
          |
          v
InvocationHandler.invoke(proxy, method, args)
                         |       |       |
              chính proxy       |       +-- [Integer(2), Integer(5)]
                                +---------- Method: Calculator.add(int,int)
          |
          +--> xử lý trực tiếp / gọi method.invoke(target, args)
          |
          v
return Object (Integer(7)) -> proxy unbox -> caller nhận int 7
~~~

JDK dynamic proxy triển khai **interface**; bạn tạo proxy bằng `Proxy.newProxyInstance(classLoader, interfaces, handler)`. `target` không phải tham số mặc định của `InvocationHandler`: nếu muốn proxy làm việc như một decorator, bạn tự giữ target trong handler và tự gọi tiếp.

## 1. `invoke(proxy, method, args)` trả về dữ liệu gì?

Chữ ký trong `java.lang.reflect.InvocationHandler`:

~~~java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
~~~

Đây là chữ ký API, không phải chương trình chạy độc lập.

| Tham số | Bản chất | Dùng để làm gì? |
| --- | --- | --- |
| `proxy` | Chính **đối tượng JDK proxy** mà caller đang gọi; nó thường khác đối tượng nghiệp vụ `target` | Kiểm tra identity, thực hiện quy tắc `equals/hashCode/toString`, chạy default method trên proxy |
| `method` | `java.lang.reflect.Method` đại diện cho phương thức được proxy xử lý | Đọc tên/chữ ký/annotation/kiểu trả về, chọn chính sách, ủy quyền cho receiver tương thích |
| `args` | `Object[]` chứa **giá trị đối số theo thứ tự**; bằng `null` nếu method không có tham số | Kiểm tra, log có kiểm soát, biến đổi, hoặc chuyển tiếp sang target |

### 1.1. `Method` là metadata của lời gọi, không nhất thiết là method trên target

Các API quan trọng:

- `getName()` trả tên phương thức. Khi có overload, phải so thêm `getParameterTypes()`; tên đơn lẻ chưa đủ phân biệt `find(int)` và `find(String)`.
- `getDeclaringClass()` thường trả interface khai báo phương thức (có thể là **superinterface**); với `equals(Object)`, `hashCode()` và `toString()`, nó trả `Object.class`.
- `getReturnType()` và `getParameterTypes()` mô tả kiểu runtime (sau type erasure); `getGenericReturnType()` và `getGenericParameterTypes()` đọc thông tin generic được lưu trong chữ ký khai báo.
- `getAnnotation(MyAnnotation.class)` đọc annotation runtime trên chính `Method` này. Nó **không tự tìm** annotation chỉ tồn tại trên method được triển khai bởi target.
- `isDefault()` nhận diện interface default method; `isVarArgs()` nhận diện tham số varargs; `getExceptionTypes()` cho biết checked exceptions trên chữ ký.

Nếu proxy triển khai nhiều interface khai báo phương thức **cùng tên và danh sách tham số**, `Method` đưa cho handler được lựa chọn dựa trên thứ tự interface trong lúc tạo proxy; nó không bắt buộc tương ứng với interface mà caller dùng để tham chiếu lời gọi. Với chữ ký trùng nhau, không suy diễn quá nhiều từ `getDeclaringClass()` hay chỉ từ annotation của một trong các interface.

### 1.2. `proxy` là proxy, không phải target

- `Proxy.isProxyClass(proxy.getClass())` xác nhận class do API JDK proxy tạo ra; `Proxy.getInvocationHandler(proxy)` truy xuất handler từ proxy hợp lệ.
- Không dựa vào tên class sinh bởi JDK vì đó là chi tiết triển khai.
- **Không ủy quyền bằng `method.invoke(proxy, args)`** trong chính handler: khi gọi phương thức interface trên proxy một lần nữa, lời gọi đi vào cùng handler, có thể lặp đến `StackOverflowError`. Thông thường dùng `method.invoke(target, args)`, với target triển khai interface tương ứng.
- Ngay cả `System.out.println(proxy)` hoặc nối chuỗi với proxy cũng có thể gọi `proxy.toString()` rồi vào handler lần nữa. Dùng thông tin phương thức hoặc identity để log khi chưa có chính sách `toString` rõ ràng.

### 1.3. `args` chứa giá trị, với ba tình huống đặc biệt

**Không tham số:** `ping()` khiến `args == null`; không được truy cập `args.length` trước khi kiểm tra. Handler có thể chuẩn hóa thành `args == null ? new Object[0] : args` nếu thuật toán cần một mảng.

**Primitive và null:** `add(2,5)` có mảng `Object[]` gồm hai `Integer` (boxing). Ngược lại, `send(null)` có mảng **dài 1**, phần tử `args[0] == null`; khác với `args == null`.

**Varargs:** `join("a", "b")` của `join(String... parts)` nhận `args.length == 1` và `args[0]` là mảng `String[]{"a","b"}`. Varargs là một tham số mảng ở runtime. `join((String[]) null)` lại có mảng `args` một phần tử với `args[0] == null`.

## 2. Chương trình Java 17+ chạy được: quan sát cả ba tham số

Lưu toàn bộ thành **HandlerArgumentsDemo.java**. Chương trình có một target `CalculatorImpl`, method trả primitive, varargs, default method, `Object.toString` và `Object.equals`.

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class HandlerArgumentsDemo {
    public interface Calculator {
        int add(int left, int right);
        String join(String... parts);

        default String origin() {
            return "from default";
        }
    }

    static final class CalculatorImpl implements Calculator {
        @Override
        public int add(int left, int right) {
            return left + right;
        }

        @Override
        public String join(String... parts) {
            return String.join(":", parts);
        }
    }

    public static void main(String[] args) {
        Calculator target = new CalculatorImpl();

        InvocationHandler handler = (proxy, method, callArgs) -> {
            String methodName = method.getDeclaringClass().getSimpleName()
                    + "." + method.getName();

            // Tránh gọi toString() trên proxy trong equals(proxy).
            String shownArgs = method.getDeclaringClass() == Object.class
                    && method.getName().equals("equals")
                    ? "[reference]"
                    : callArgs == null
                            ? "null"
                            : Arrays.deepToString(callArgs);

            System.out.println("invoke " + methodName
                    + " | isProxy=" + Proxy.isProxyClass(proxy.getClass())
                    + " | args=" + shownArgs);

            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "CalculatorProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == callArgs[0];
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                };
            }

            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, callArgs);
            }

            try {
                return method.invoke(target, callArgs);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };

        Calculator calculator = (Calculator) Proxy.newProxyInstance(
                Calculator.class.getClassLoader(),
                new Class<?>[]{Calculator.class},
                handler);

        System.out.println("result=" + calculator.add(2, 5));
        System.out.println("join=" + calculator.join("a", "b"));
        System.out.println("origin=" + calculator.origin());
        System.out.println("toString=" + calculator.toString());
        System.out.println("same=" + calculator.equals(calculator));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac HandlerArgumentsDemo.java
java HandlerArgumentsDemo
~~~

Output:

~~~text
invoke Calculator.add | isProxy=true | args=[2, 5]
result=7
invoke Calculator.join | isProxy=true | args=[[a, b]]
join=a:b
invoke Calculator.origin | isProxy=true | args=null
origin=from default
invoke Object.toString | isProxy=true | args=null
toString=CalculatorProxy
invoke Object.equals | isProxy=true | args=[reference]
same=true
~~~

**Giải thích các điểm quan trọng:**

1. `method` trong lời gọi `add` là `Calculator.add(int,int)`; `callArgs` chứa `Integer(2)` và `Integer(5)`. Handler chuyển lời gọi tới `CalculatorImpl`; giá trị trả về qua reflection là `Integer(7)` và proxy unbox thành `int` cho caller.
2. `join` có `args` một phần tử mảng, nên `Arrays.deepToString(callArgs)` hiển thị `[[a, b]]`.
3. `origin()` không có tham số nên `callArgs == null`. Vì `method.isDefault()` là true, `invokeDefault` thực thi phần thân default method trên proxy.
4. Hai lời gọi `Object.toString` và `Object.equals` vẫn đi qua handler. Ví dụ dùng identity equality; không in chính proxy bên trong `equals` để tránh callback ngoài ý muốn vào `toString`.
5. `target` là object nghiệp vụ có sẵn; `calculator` là proxy do `Proxy.newProxyInstance` trả về. Handler **tự quyết định** gọi target.

## 3. Forward Method sang target và xử lý exception

Khi `target` thực sự triển khai interface khai báo `method`, handler có thể dùng:

~~~java
try {
    return method.invoke(target, args);
} catch (InvocationTargetException e) {
    throw e.getCause();
}
~~~

Đây là **đoạn trích bên trong handler**, không phải class biên dịch độc lập. Reflection yêu cầu receiver tương thích và quyền truy cập hợp lệ; truyền sai receiver/đối số có thể gây `IllegalArgumentException`, truy cập không được phép có thể gây `IllegalAccessException`. Nếu bản thân target ném lỗi, `Method.invoke` bọc lỗi trong `InvocationTargetException`. Handler thường nên ném lại `getCause()` để giữ exception gốc.

Mặc dù `InvocationHandler.invoke` khai báo `throws Throwable`, lời gọi **qua proxy** phải tuân thủ hợp đồng kiểu trả về và `throws` của phương thức interface:

| Handler thực hiện | Kết quả ở phía caller |
| --- | --- |
| Trả `Integer(7)` khi method trả `int` | Proxy unbox thành `int 7` |
| Trả `null` cho phương thức trả primitive | `NullPointerException` |
| Trả một object sai kiểu trả về | `ClassCastException` |
| Ném `RuntimeException` hoặc `Error` | Ngoại lệ/lỗi đi ra bình thường |
| Ném checked exception được chữ ký method cho phép | Truyền ra như khai báo |
| Ném checked exception **không được khai báo** ở method interface | Proxy ném `UndeclaredThrowableException` bọc ngoại lệ đó |

Đây là lý do không nên để `InvocationTargetException` lọt ra trực tiếp khi bạn muốn caller thấy checked exception mà method đã khai báo. Với nhiều interface có chữ ký trùng nhau, checked exception có thể cần tương thích với **tất cả** chữ ký tương ứng, không chỉ một `Method.getExceptionTypes()`.

## 4. Trường hợp đặc biệt của Method và proxy

### 4.1. `equals`, `hashCode`, `toString`

Ba phương thức public không final từ `Object` này được JDK proxy chuyển tới handler. Với chúng, `method.getDeclaringClass() == Object.class`, kể cả khi interface redeclare chữ ký tương ứng. `wait()`/`notify()` không nằm trong nhóm xử lý đặc biệt này.

Bạn phải chọn chính sách nhất quán. Ví dụ dùng `proxy == args[0]` cho `equals`, `System.identityHashCode(proxy)` cho `hashCode` và chuỗi cố định cho `toString`. Nếu định nghĩa equality theo đối tượng đích hay khóa nghiệp vụ, cả `equals` và `hashCode` phải tuân thủ cùng nguyên tắc.

### 4.2. Default method của interface

Lời gọi vào `default method` của interface **vẫn tới handler**. Với Java 16+, `InvocationHandler.invokeDefault(proxy, method, args)` gọi phần thân default method hợp lệ trên **proxy**, như lời gọi `Interface.super.method(...)`. Đối số có thể null khi không có tham số.

Ngược lại, `method.invoke(target, args)` chạy trên target theo dispatch bình thường. Nếu target đã override default method, phần override có thể được chọn. Hai cách này khác receiver và có thể cho kết quả khác nhau. `invokeDefault` chỉ nhận `Method` tương ứng với default method còn hợp lệ trong hệ interface của proxy; không dùng được cho bất kỳ method thường nào.

### 4.3. Method trùng chữ ký, overload và metadata generic

Với overload `work(String)` và `work(int)`, so thêm `method.getParameterTypes()`; overload không được phân biệt bằng nội dung `args` đơn thuần, nhất là khi giá trị là null. Với nhiều interface chứa cùng chữ ký, thứ tự interface lúc tạo proxy ảnh hưởng `Method` đưa tới handler. Với generic, `Method` giữ thông tin khai báo trong phạm vi metadata còn lưu; nó không tự thay biến kiểu qua toàn bộ cây kế thừa thành kiểu cụ thể của target.

Nếu logic cần annotation của method được implement ở target, phải chủ động tra cứu method tương ứng trên class target và xử lý bridge/override khi cần. Annotation trên interface method không tự được “chuyển” vào method triển khai.

## 5. Bảng kiểm nhanh cho args

| Ví dụ gọi method | Giá trị `args` | Lưu ý |
| --- | --- | --- |
| `ping()` | `null` | Không tự giả định mảng rỗng |
| `send(null)` | `[null]` | Mảng dài 1, phần tử null |
| `add(2,5)` | `[Integer(2),Integer(5)]` | Primitive đã boxing |
| `join("a","b")` với `String...` | Mảng dài 1, phần tử là `String[]` | Không trải phẳng mảng varargs |
| `send((String)null)` | `[null]` | Overload phải phân biệt bằng kiểu Method |
| `record(secret)` | Có thể chứa dữ liệu nhạy cảm | Không log toàn bộ token/password |

Khi chuyển `args` cho `method.invoke(target, args)`, phải giữ đúng **số lượng và cấu trúc tham số**. Truyền riêng `args[0]` cho method cần nhiều đối số, hoặc bọc thêm mảng ở sai tầng, sẽ làm thay đổi cách reflection nhận tham số; Java varargs ở đây không cho phép tự ý trải phẳng mảng.

## 6. Các lỗi phổ biến và cách tự kiểm tra

- **Đệ quy không kết thúc:** gọi `method.invoke(proxy, args)` trong handler. Dùng target tương thích hoặc xử lý đặc biệt `invokeDefault` khi muốn thực thi default body.
- **NPE do args:** truy cập `args.length` với method không tham số. Luôn kiểm tra null trước.
- **Nhầm mảng varargs:** xem `join("a","b")` là hai phần tử `args`. Thực tế chỉ có một phần tử kiểu `String[]`.
- **Nhầm metadata:** coi `method.getDeclaringClass()` là class của target hoặc tin annotation interface sẽ tự bao gồm annotation triển khai.
- **Sai kiểu trả về:** trả `null`/object không tương thích cho primitive hoặc reference return type.
- **Sai checked exception:** bỏ qua `InvocationTargetException.getCause()`, hoặc ném checked exception ngoài `throws` của interface.
- **Log gây side effect:** ép `toString()` trên proxy argument, hoặc ghi mật khẩu/token trong `args`.

Tự trả lời: (1) `proxy` khác `target` thế nào? (2) tại sao `ping()` cho `args == null` nhưng `send(null)` không? (3) tại sao `method.invoke(proxy, args)` có nguy cơ đệ quy? (4) vì sao `method.getDeclaringClass()` có thể là `Object.class`? (5) khi nào phải dùng `InvocationHandler.invokeDefault`? (6) phản xạ và proxy lần lượt bọc exception ra sao?

### Tài liệu chính thức

- Java SE 21 `InvocationHandler`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/InvocationHandler.html
- Java SE 21 `Proxy`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Proxy.html
- Java SE 21 `Method`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html
- Java SE 21 `UndeclaredThrowableException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/UndeclaredThrowableException.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-007">equals, hashCode, toString và hợp đồng cần định nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# `equals`, `hashCode`, `toString` trong Java dynamic proxy: hợp đồng cần định nghĩa

## 1. Vì sao ba phương thức này cần được xử lý riêng?

Dynamic proxy của JDK là một đối tượng được tạo lúc chạy bằng `java.lang.reflect.Proxy` và triển khai một hay nhiều **interface**. Khi mã gọi phương thức của interface, proxy chuyển lời gọi đến `InvocationHandler.invoke(proxy, method, args)` để handler quyết định hành vi.

Có một chi tiết dễ bỏ sót: **`equals(Object)`, `hashCode()` và `toString()` cũng được proxy chuyển đến `InvocationHandler`**, dù chúng được khai báo trong `java.lang.Object` và không được liệt kê trong interface nghiệp vụ. Vì thế, handler phải chủ động xác định đối tượng proxy được xem là bằng đối tượng nào, mã băm của nó ra sao và cách biểu diễn chuỗi của nó. Nếu chỉ xử lý phương thức nghiệp vụ rồi mặc định các phương thức còn lại thuộc về đối tượng đích, chương trình có thể lỗi lúc chạy hoặc tạo ra hành vi không nhất quán trong `HashMap`, `HashSet`, log và kiểm thử.

Phân biệt ba đối tượng có thể xuất hiện:

- **Proxy**: đối tượng mà bên gọi nắm giữ và gọi `proxy.equals(...)`, `proxy.hashCode()`, `proxy.toString()`.
- **Handler**: đối tượng triển khai `InvocationHandler`; nhận lời gọi từ proxy. Bản thân handler có `equals`/`hashCode`/`toString` riêng, nhưng đó **không tự động** là hợp đồng của proxy.
- **Target/delegate** (nếu có): đối tượng thật mà handler chuyển tiếp nghiệp vụ tới. Hợp đồng của target không tự động biến thành hợp đồng của proxy.

Không phải mọi dynamic proxy đều có target: một handler có thể tự tạo kết quả, gửi lời gọi RPC, thực hiện logging hoặc truy cập tài nguyên bên ngoài.

## 2. JVM chuyển ba lời gọi Object vào handler như thế nào?

Với một proxy từ `Proxy.newProxyInstance(...)`:

| Lời gọi của bên dùng | `method.getDeclaringClass()` trong handler | `args` | Kiểu trả về cần cung cấp |
| --- | --- | --- | --- |
| `proxy.equals(other)` | `Object.class` | Một phần tử: `other`, có thể `null` | `Boolean` để proxy trả `boolean` |
| `proxy.hashCode()` | `Object.class` | `null` vì không có tham số | `Integer` để proxy trả `int` |
| `proxy.toString()` | `Object.class` | `null` vì không có tham số | `String` |

Điều này đúng cả khi một interface khai báo lại chữ ký phù hợp của ba phương thức nói trên: `Method` được chuyển vào handler vẫn có declaring class là `Object.class`. Vì vậy, kiểm tra `method.getDeclaringClass() == Object.class` rồi phân nhánh theo tên là cách dễ hiểu để phân biệt ba lời gọi này với phương thức nghiệp vụ. Một phương thức overload như `equals(String)` **không** thay thế `equals(Object)` và phải được nhận diện theo chữ ký riêng nếu interface thực sự có khai báo.

Proxy cũng kế thừa một số phương thức `Object` khác. Các phương thức `final` như `getClass()`, `wait()`, `notify()` không được handler chặn theo cách này; không nên khẳng định *mọi* lời gọi phương thức `Object` đều qua `invoke`.

**Kiểu trả về phải đúng.** `InvocationHandler.invoke` có kiểu kết quả `Object`, nhưng proxy sẽ kiểm tra/ép kết quả theo kiểu trả về của phương thức được gọi. Trả `null` cho `equals` hoặc `hashCode` (kiểu nguyên thủy) gây `NullPointerException` khi unbox; trả sai kiểu có thể gây `ClassCastException`. `toString` có thể trả `null` về mặt kiểu, nhưng giá trị chuỗi có ý nghĩa thường hữu ích hơn. Nếu handler ném checked exception không được khai báo trong chữ ký, proxy có thể bọc nó trong `UndeclaredThrowableException`.

## 3. Hợp đồng của từng phương thức

### `equals(Object)` — định nghĩa thế nào là hai proxy bằng nhau

Hợp đồng cơ bản của `Object.equals` là:

1. **Phản xạ:** `x.equals(x)` là `true`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều đúng thì `x.equals(z)` phải đúng.
4. **Nhất quán:** nếu thông tin dùng để so sánh không đổi, gọi lại cho cùng kết quả.
5. **Với `null`:** `x.equals(null)` là `false`.

Hai chiến lược thường dùng:

- **Bằng nhau theo định danh proxy (identity semantics):** chỉ `proxy == other` mới bằng nhau. Hai proxy khác nhau được xem là khác nhau, kể cả nếu chúng cùng chuyển lời gọi tới một target. Đây là chính sách đơn giản và an toàn khi mỗi proxy là một đối tượng wrapper độc lập.
- **Bằng nhau theo định danh logic (logical/value semantics):** hai proxy biểu diễn cùng một thực thể nghiệp vụ/tài nguyên có thể bằng nhau nếu chúng mang cùng khóa logic ổn định. Phải định nghĩa rõ tập đối tượng được so sánh (chẳng hạn cùng kiểu handler và cùng loại dịch vụ), tránh chỉ dựa vào một chuỗi ID mà các kiểu không liên quan cũng dùng.

Không có một chính sách `equals` đúng cho mọi proxy. Phải lựa chọn theo vai trò của proxy và tài liệu hóa rõ, đặc biệt nếu API trả về nhiều proxy bọc cùng đối tượng.

### `hashCode()` — nhất quán với `equals`

Điều kiện bắt buộc: nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()`. Chiều ngược lại không bắt buộc: hai đối tượng khác nhau được phép trùng mã băm.

- Khi dùng **identity semantics**, `System.identityHashCode(proxy)` là lựa chọn phù hợp để tính mã băm trực tiếp từ định danh đối tượng mà không gọi lại `proxy.hashCode()`.
- Khi dùng **logical/value semantics**, mã băm nên tính từ **đúng các khóa ổn định dùng trong `equals`**, ví dụ `Objects.hash(serviceType, resourceId)` hoặc `key.hashCode()` nếu `key` đã bao gồm mọi thành phần nhận dạng.
- Các thuộc tính dùng trong `equals`/`hashCode` không nên thay đổi khi proxy đang là khóa của `HashMap` hay phần tử của `HashSet`; nếu thay đổi, tra cứu có thể thất bại dù phần tử vẫn nằm trong cấu trúc dữ liệu.

`System.identityHashCode(proxy)` **không** phù hợp nếu hai proxy khác nhau được coi là bằng nhau theo khóa logic: chúng có thể nhận các mã băm khác nhau và vi phạm hợp đồng. Không dùng `proxy.hashCode()` ngay trong nhánh xử lý `hashCode` vì sẽ gọi ngược vào cùng handler.

### `toString()` — biểu diễn hữu ích, an toàn

`toString` nên trả về một mô tả ngắn, dễ nhận diện trong log/debug, chẳng hạn `GreeterProxy[label=A]` hoặc tên interface và ID tài nguyên phù hợp. Không có quy tắc bắt buộc rằng hai đối tượng bằng nhau phải có chuỗi giống nhau, nhưng mô tả nhất quán giúp kiểm thử và vận hành.

Không cần liệt kê toàn bộ trạng thái nội bộ, đặc biệt không in token, mật khẩu hoặc dữ liệu nhạy cảm. Tránh viết `return proxy.toString()` hoặc `String.valueOf(proxy)` ngay bên trong nhánh `toString`: chúng sẽ kích hoạt `InvocationHandler.invoke` lần nữa và dẫn tới đệ quy.

## 4. Ví dụ Java 17+: ba hợp đồng theo định danh proxy

Tạo tệp `ProxyObjectMethodsDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

public class ProxyObjectMethodsDemo {
    public interface Greeter {
        String greet(String name);
    }

    static Greeter createGreeter(String label) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "GreeterProxy[label=" + label + "]";
                    default -> throw new UnsupportedOperationException(
                            method.toString());
                };
            }

            if (method.getDeclaringClass() == Greeter.class
                    && method.getName().equals("greet")) {
                return "Xin chào, " + args[0];
            }

            throw new UnsupportedOperationException(method.toString());
        };

        return (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[] { Greeter.class },
                handler);
    }

    public static void main(String[] args) {
        Greeter a = createGreeter("A");
        Greeter b = createGreeter("B");

        System.out.println(a.greet("An"));
        System.out.println(a.equals(a));
        System.out.println(a.equals(b));
        System.out.println(a.equals(null));
        System.out.println(
                a.hashCode() == System.identityHashCode(a));
        System.out.println(a.toString());

        Set<Greeter> set = new HashSet<>();
        set.add(a);
        set.add(b);
        set.add(a);
        System.out.println(set.size());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ProxyObjectMethodsDemo.java
java ProxyObjectMethodsDemo
```

Kết quả chuẩn:

```text
Xin chào, An
true
false
false
true
GreeterProxy[label=A]
2
```

Phân tích:

1. `a.greet("An")` là phương thức nghiệp vụ, được handler trả về chuỗi chào.
2. `a.equals(a)` là `true`, còn `a.equals(b)` là `false` mặc dù `a` và `b` đều triển khai `Greeter`. Hai đối tượng proxy được tạo bằng hai lần gọi factory nên có định danh khác nhau.
3. `a.equals(null)` là `false` vì một proxy không đồng nhất với `null`.
4. `a.hashCode() == System.identityHashCode(a)` là `true` theo chính sách đã cài đặt; giá trị mã băm cụ thể có thể khác nhau ở mỗi lần chạy nên ví dụ chỉ kiểm tra tính nhất quán, không in số tùy ý.
5. `a.toString()` tạo chuỗi có thể đọc trực tiếp từ thông tin `label` mà handler đã giữ, không tự gọi lại phương thức của proxy.
6. `HashSet` chỉ chứa hai proxy phân biệt dù chèn `a` hai lần. `HashSet` phụ thuộc cả `hashCode` lẫn `equals` nên đây là kiểm tra thực tế của hợp đồng.

Trong code trên, `args[0]` chỉ được đọc ở nhánh `equals` hoặc `greet`, là hai method có một tham số. Khi nhận `hashCode` hoặc `toString`, `args` là `null` và code không truy cập vào nó.

## 5. Khi proxy bọc target: không chuyển tiếp mù quáng ba phương thức

Giả sử handler chuyển nghiệp vụ đến một đối tượng `target` bằng reflection:

```java
// Minh họa bên trong InvocationHandler, chưa phải chương trình độc lập:
Object result = method.invoke(target, args);
```

Điều này chỉ thích hợp khi `method` thực sự là phương thức muốn gọi trên `target` và điều kiện truy cập/kiểu đối tượng được đáp ứng. Riêng `equals`, `hashCode`, `toString` phải có quyết định rõ ràng.

Ví dụ handler viết `return target.equals(args[0]);` cho `proxy.equals(other)` có thể khiến `proxy.equals(target)` trả `true` nhưng `target.equals(proxy)` lại trả `false`. Như vậy **vi phạm tính đối xứng**. Ngược lại, gọi `method.invoke(proxy, args)` để “trả về mặc định” sẽ kích hoạt lại cùng proxy và tạo chuỗi lời gọi đệ quy.

Nếu muốn **proxy bằng target** hoặc **hai proxy cùng bọc một target thì bằng nhau**, cần kiểm tra cả hai chiều so sánh, kiểu được chấp nhận và mã băm tương ứng. Trong nhiều hệ thống, chọn identity semantics cho proxy và công khai ID nghiệp vụ qua interface là dễ dự đoán hơn.

Một lựa chọn khác là sử dụng **khóa logic bất biến** khi tạo handler. Chẳng hạn handler chứa `record ServiceKey(String tenant, String serviceId) {}` và so sánh hai proxy hợp lệ theo hai khóa `ServiceKey`. Khi đó:

- `equals` cần kiểm tra đối tượng còn lại thuộc nhóm proxy tương thích và handler của nó có khóa tương ứng; có thể dùng `Proxy.isProxyClass(other.getClass())` rồi `Proxy.getInvocationHandler(other)` sau khi đã kiểm tra `other != null`.
- `hashCode` phải được tính từ cùng khóa; **không** tính theo định danh proxy.
- `toString` có thể hiển thị loại dịch vụ và phần ID an toàn để hỗ trợ chẩn đoán.

Việc `Proxy.getInvocationHandler(other)` lấy được handler chỉ xác nhận đối tượng là JDK dynamic proxy (hoặc có thể phát sinh lỗi theo điều kiện API), không tự chứng minh nó là cùng kiểu nghiệp vụ. Hãy kiểm tra kiểu handler/contract trước khi dùng khóa. Hai proxy được coi là bằng nhau phải chia sẻ quy tắc so sánh và mã băm cho cả hai chiều; nếu target của framework khác cũng có `equals` tùy biến, việc so sánh xuyên giữa proxy và target thường phức tạp hơn nhiều.

## 6. Những lỗi phổ biến và cách tránh

| Lỗi triển khai | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Chỉ xử lý tên method nghiệp vụ, quên `equals`/`hashCode`/`toString` | Log hoặc thêm proxy vào `HashSet` có thể ném lỗi | Tạo nhánh chuyên xử lý `Object.class` |
| Dùng `proxy.equals(other)` trong nhánh xử lý `equals` | Đệ quy qua `invoke` | Dùng `proxy == other` cho identity semantics; hoặc so khóa bất biến |
| Dùng `proxy.hashCode()` trong nhánh xử lý `hashCode` | Đệ quy qua `invoke` | Dùng `System.identityHashCode(proxy)` khi chọn identity semantics |
| Dùng `proxy.toString()` trong nhánh `toString` | Đệ quy qua `invoke` | Dựng chuỗi từ dữ liệu handler an toàn |
| `equals` so khóa logic nhưng `hashCode` dùng identity | Có thể vi phạm hợp đồng, làm sai tra cứu trên `HashMap`/`HashSet` | Dùng cùng bộ khóa cho cả hai |
| Ủy quyền thẳng `target.equals(other)` | Có thể bất đối xứng giữa target và proxy | Chọn chính sách rõ ràng và kiểm tra cả hai chiều |
| Trả `null` cho `equals`/`hashCode` | `NullPointerException` khi unbox | Trả `Boolean`/`Integer` đúng kiểu |
| Thay đổi khóa sau khi thêm proxy vào hash collection | Không còn tìm ra phần tử theo khóa cũ | Dùng khóa bất biến trong suốt vòng đời proxy |
| In target/credentials trong `toString` | Dữ liệu nhạy cảm có thể vào log | Chỉ hiển thị metadata an toàn, cần thiết |

**Đừng chỉ kiểm tra tên phương thức.** Nếu interface có overload `equals(String)` thì đây không phải `Object.equals(Object)`. Hãy kết hợp declaring class/chữ ký; điều kiện `method.getDeclaringClass() == Object.class` giải quyết đúng ba method Object đặc biệt được JDK proxy chuyển vào handler.

## 7. Nên kiểm thử hợp đồng ra sao?

Với mỗi loại proxy, tối thiểu nên kiểm tra:

- **`equals`:** `x.equals(x)`, `x.equals(null)`, hai proxy cùng/khác khóa, đối xứng khi so với proxy khác và với target nếu API cho phép.
- **`hashCode`:** các cặp bằng nhau có hash bằng nhau; giá trị ổn định khi trạng thái nhận dạng không đổi; thêm rồi tìm lại trong `HashSet`/`HashMap`.
- **`toString`:** gọi không lỗi, không đệ quy, không chứa dữ liệu nhạy cảm, chuỗi có ích khi đọc log.
- **Phân luồng handler:** ba method Object được phân biệt với method interface; test thêm interface khai báo lại `toString()` hoặc overload `equals(String)` nếu API có tình huống đó.
- **Đường lỗi:** handler không trả `null` cho method trả primitive, không nuốt ngoại lệ nghiệp vụ thành chuỗi thành công giả.

Nếu framework tạo proxy lồng nhau (proxy bọc proxy), cần xác định `equals` đang so lớp wrapper ngoài, wrapper trong hay ID nghiệp vụ; việc dựa đơn thuần vào class proxy sinh tự động hoặc tên của nó thường làm hợp đồng mong manh khi thay đổi interface hay class loader.

## 8. Tự kiểm tra

**Câu 1.** Hai đối tượng được tạo riêng bằng `Proxy.newProxyInstance` cùng trỏ đến một target có bắt buộc phải `equals` nhau không?

**Trả lời:** Không. Điều đó phụ thuộc chính sách `equals` trong handler. Với identity semantics, chúng khác nhau; với logical semantics có thể bằng nhau nếu hợp đồng được thiết kế nhất quán.

**Câu 2.** Tại sao `return proxy.hashCode()` bên trong nhánh xử lý `hashCode` là sai?

**Trả lời:** Lời gọi quay lại đúng proxy và handler, gây đệ quy. Với identity semantics, dùng `System.identityHashCode(proxy)` để tránh dispatch phản chiếu.

**Câu 3.** Có thể dùng `System.identityHashCode(proxy)` khi hai proxy khác nhau được coi là bằng nhau theo `resourceId` không?

**Trả lời:** Không nên: giá trị identity hash có thể khác nhau giữa hai proxy bằng nhau. Hash phải được tính bằng cùng dữ liệu nhận dạng ổn định đã dùng trong `equals`.

**Câu 4.** `getClass()` của proxy có đi vào `InvocationHandler.invoke` như `toString()` không?

**Trả lời:** Không. `getClass()` là phương thức `final` của `Object`; ba phương thức `equals`, `hashCode`, `toString` được JDK dynamic proxy xử lý đặc biệt.

**Ghi nhớ:** Dynamic proxy của JDK không tự cung cấp chính sách equality có nghĩa cho ứng dụng. Handler cần định nghĩa `equals`, `hashCode` và `toString` đồng bộ với cách proxy đại diện cho đối tượng/tài nguyên, rồi kiểm thử chúng qua các thao tác thường ngày như so sánh, lưu tập hợp và ghi log.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-008">Kiểu trả về, unboxing và giới hạn của giá trị null</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu trả về, unboxing và giới hạn của `null` trong Java Dynamic Proxy

Trong Java Dynamic Proxy, phương thức trên interface có thể khai báo kiểu trả về cụ thể như `int`, `String`, `Integer` hoặc `void`, nhưng `InvocationHandler.invoke(...)` luôn có kiểu trả về **`Object`**. Khi phương thức của proxy được gọi, lớp proxy do JDK tạo ra chịu trách nhiệm chuyển giá trị `Object` đó sang kiểu mà interface đã công bố. **Sai kiểu gây `ClassCastException`; trả về `null` cho primitive gây `NullPointerException`.** Với kiểu tham chiếu, `null` thường hợp lệ; với `void`, giá trị trả về từ handler bị bỏ qua.

Bài này tập trung vào **hợp đồng kiểu trả về tại ranh giới giữa `InvocationHandler` và phương thức proxy**, không nhầm nó với phép gọi `Method.invoke` trên một đối tượng đích bất kỳ.

## 1. Giá trị đi qua proxy như thế nào?

Giả sử interface công bố:

~~~java
interface Counter {
    int getCount();
}
~~~

Phương thức `Counter.getCount()` buộc phía người gọi nhận một `int`, nhưng handler có chữ ký:

~~~java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
~~~

Đây là **hai mức kiểu khác nhau**:

~~~text
Người gọi: Counter.getCount() -> int
                |
                v
Phương thức trong lớp proxy do JDK sinh ra
                |
                v
InvocationHandler.invoke(...) -> Object
                |
                v
Proxy kiểm tra/ép kiểu wrapper rồi unbox thành int
                |
                v
Trả int cho người gọi, hoặc ném lỗi khi chuyển đổi
~~~

Về **ý nghĩa**, đoạn chuyển đổi cho `int` gần giống:

~~~java
Object result = handler.invoke(proxy, method, args);
return ((Integer) result).intValue();
~~~

Đây chỉ là **mã giả minh họa**: không thể chèn hai dòng này trực tiếp vào một phương thức thường mà chưa định nghĩa các biến và xử lý ngoại lệ. Bytecode của lớp proxy thực tế do JDK tạo và có thể khác cách trình bày trên.

- Handler trả về `Integer.valueOf(7)`: ép sang `Integer` thành công, `intValue()` tạo ra `7`.
- Handler trả về `null`: phép ép kiểu tham chiếu sang `Integer` vẫn cho `null`, nhưng gọi `intValue()` trên `null` gây `NullPointerException`.
- Handler trả về `Long.valueOf(7)`: ép từ `Long` sang `Integer` không hợp lệ, gây `ClassCastException`.
- Handler trả về giá trị kiểu `int` bằng `return 7;`: trình biên dịch **autobox** thành `Integer` trước khi handler trả `Object`.

**Thời điểm lỗi:** Trong những tình huống trên, handler có thể đã kết thúc bình thường. NPE/CCE phát sinh khi mã của proxy chuyển kết quả sang kiểu interface yêu cầu; một khối `try/catch` chỉ bao quanh phần xử lý bên trong handler sẽ không tự bắt được lỗi xảy ra sau khi handler `return`.

## 2. Hợp đồng kiểu trả về cần nhớ

| Kiểu trả về ở interface | Giá trị handler trả về | Kết quả của lời gọi trên proxy |
|---|---|---|
| `int` | `Integer.valueOf(7)` hoặc `return 7;` | `7`, unbox thành `int` |
| `int` | `null` | `NullPointerException` |
| `int` | `Long.valueOf(7)` | `ClassCastException` |
| `long` | `Long.valueOf(7)` hoặc `return 7L;` | `7L`, unbox thành `long` |
| `long` | `Integer.valueOf(7)` | `ClassCastException`, **không** tự nới rộng `int` thành `long` |
| `boolean` | `Boolean.TRUE` | `true` |
| `boolean` | `null` hoặc wrapper khác `Boolean` | NPE nếu `null`; CCE nếu sai wrapper |
| `Integer` | `null` hoặc một `Integer` | Hợp lệ vì `Integer` là kiểu tham chiếu |
| `String` | `null` hoặc một `String` | Hợp lệ |
| `String` | `Integer.valueOf(7)` | `ClassCastException` |
| `Object` | Bất kỳ đối tượng nào hoặc `null` | Hợp lệ |
| `void` | `null`, `Integer.valueOf(1)`, hay đối tượng khác | Giá trị bị bỏ qua; người gọi không nhận kết quả |

**Với mọi kiểu nguyên thủy (primitive), wrapper phải tương ứng đúng:**

| Primitive | Wrapper cần trả từ handler |
|---|---|
| `boolean` | `Boolean` |
| `byte` | `Byte` |
| `short` | `Short` |
| `char` | `Character` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |

Một điểm dễ nhầm: trong Java thông thường, `long value = 7;` được phép nhờ chuyển đổi primitive từ `int` sang `long`. Nhưng nếu **phương thức proxy khai báo `long`**, handler trả về `Integer` thì proxy cần `Long` để unbox. Nó không nhận `Integer` rồi tự suy diễn một phép widening. Tương tự, `Float` không thay cho `Double`.

Đối với kiểu tham chiếu `T`, giá trị khác `null` phải là instance có thể gán cho `T` (kể cả subtype/interface triển khai phù hợp). Thí dụ phương thức trả `CharSequence` có thể nhận `String`, nhưng phương thức trả `String` không thể nhận `StringBuilder`.

## 3. Chạy một ví dụ Java 17+ bao phủ các trường hợp

Lưu chương trình hoàn chỉnh sau thành **`ProxyReturnDemo.java`**. Chương trình tạo các proxy thực sự qua `Proxy.newProxyInstance`, rồi thử cả kết quả hợp lệ và lỗi đã kiểm soát. `check(...)` chỉ in **tên lớp ngoại lệ** để stdout ổn định, không phụ thuộc thông điệp lỗi của từng bản JDK.

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class ProxyReturnDemo {
    interface Probe {
        int primitive();
        long wider();
        Integer boxed();
        String text();
        void tick();
    }

    static Probe create(InvocationHandler handler) {
        return (Probe) Proxy.newProxyInstance(
                Probe.class.getClassLoader(),
                new Class<?>[]{Probe.class}, handler);
    }

    static void check(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + " -> OK");
        } catch (RuntimeException e) {
            System.out.println(label + " -> " + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Probe normal = create((proxy, method, values) ->
                switch (method.getName()) {
                    case "primitive" -> 7;
                    case "wider" -> 9L;
                    case "boxed", "text" -> null;
                    case "tick" -> 123;
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        System.out.println("int = " + normal.primitive());
        System.out.println("long = " + normal.wider());
        System.out.println("Integer null = " + (normal.boxed() == null));
        System.out.println("String null = " + (normal.text() == null));
        normal.tick();
        System.out.println("void = OK");

        Probe nullResult = create((proxy, method, values) -> null);
        check("int <- null", () -> nullResult.primitive());

        Probe longForInt = create((proxy, method, values) -> 7L);
        check("int <- Long", () -> longForInt.primitive());

        Probe intForLong = create((proxy, method, values) -> 7);
        check("long <- Integer", () -> intForLong.wider());

        Probe intForString = create((proxy, method, values) -> 7);
        check("String <- Integer", () -> intForString.text());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac --release 17 -encoding UTF-8 ProxyReturnDemo.java
java ProxyReturnDemo
~~~

Đầu ra chuẩn (stdout):

~~~text
int = 7
long = 9
Integer null = true
String null = true
void = OK
int <- null -> NullPointerException
int <- Long -> ClassCastException
long <- Integer -> ClassCastException
String <- Integer -> ClassCastException
~~~

Diễn giải những điểm quan trọng:

1. `case "primitive" -> 7` trả `Integer` do autoboxing; `Probe.primitive()` nhận `int` sau unboxing.
2. `case "wider" -> 9L` trả `Long`, đúng wrapper của `long`.
3. `boxed()` và `text()` đều trả `null` thành công vì interface khai báo kiểu tham chiếu.
4. `tick()` khai báo `void`. Handler cố ý trả `123`, nhưng proxy **bỏ giá trị này**, nên gọi `normal.tick()` vẫn thành công.
5. `nullResult.primitive()` phát sinh NPE dù lời gọi handler đã trả `null` bình thường.
6. `longForInt.primitive()` và `intForLong.wider()` đều CCE: wrapper không khớp chính xác; phép chuyển số tự động như mã Java tĩnh không được thực hiện ở ranh giới này.
7. `intForString.text()` gây CCE do giá trị `Integer` không thể gán cho kiểu trả về `String`.

Ví dụ `Probe` không khai báo tham số; với những lời gọi này, `values` mà handler nhận được là `null`. Nếu một method có tham số primitive, phần tử tương ứng trong `args` mà handler nhận được là wrapper, ví dụ đối số `int` được boxing thành `Integer`. **Boxing tham số đầu vào** và **unboxing giá trị đầu ra** là hai quá trình cần phân biệt.

## 4. `null` có thể xuất hiện ở những nơi khác nhau

`null` là giá trị của kiểu tham chiếu; bản thân biến primitive không chứa `null`. Hãy phân biệt ít nhất ba tình huống:

**Tình huống A — interface trả primitive**

~~~java
int count = proxy.primitive();
~~~

Mã ở vị trí gọi là hợp lệ lúc biên dịch vì khai báo của `Probe.primitive()` là `int`. Nếu handler trả `null`, proxy ném NPE **trong quá trình xử lý lời gọi**; người gọi không nhận được một `int` nào.

**Tình huống B — interface trả wrapper**

~~~java
Integer maybeCount = proxy.boxed();  // có thể là null
~~~

Đây là lời gọi hợp lệ. Tuy nhiên, nếu phía người gọi tiếp tục:

~~~java
int count = proxy.boxed();          // tự unbox null -> NPE
~~~

NPE lần này do **mã người gọi unbox một `Integer` bằng `null`**, không phải do proxy vi phạm hợp đồng của `boxed()`. Hai nơi phát sinh có thể thể hiện khác nhau trên stack trace.

**Tình huống C — interface trả `void`**

~~~java
proxy.tick();
~~~

Bên ngoài không có kết quả để lưu; handler vẫn phải hoàn thành phương thức `Object invoke(...)` bằng một giá trị `Object` như `null`. **Thực hành thông thường** là trả `null` cho method `void`, nhằm làm rõ ý nghĩa thay vì trả giá trị bị bỏ đi.

Một phương thức trả `Void` (chữ `V` hoa, là lớp wrapper tham chiếu) **không giống** phương thức trả primitive `void`: `Void` có thể nhận `null` như các reference type. Đừng đồng nhất `Void.class` với `void.class`.

## 5. Thiết kế hợp đồng khi dữ liệu có thể thiếu

Khi không có giá trị, chọn kiểu trả về theo nhu cầu nghiệp vụ trước khi viết handler:

- **Luôn có một số đếm hợp lệ:** interface trả `int`; handler đảm bảo luôn trả `Integer` không `null`. Nếu dữ liệu thiếu là lỗi, ném ngoại lệ có ý nghĩa thay vì trả `null`.
- **Số đếm được phép vắng mặt:** interface trả `Integer` và mô tả rõ nghĩa của `null`; người gọi kiểm tra trước khi unbox.
- **Muốn thể hiện thiếu dữ liệu một cách tường minh:** cân nhắc `OptionalInt` cho số nguyên không có giá trị hoặc `Optional<T>` cho reference type; handler phải trả chính một instance `OptionalInt`/`Optional` tương thích, chẳng hạn `OptionalInt.empty()`, thay vì trả `null` mơ hồ.
- **Thao tác chỉ tạo tác dụng phụ:** interface trả `void` và handler trả `null` sau khi làm xong; không dùng một giá trị bị bỏ qua để báo kết quả.
- **Cần báo thành công/thất bại:** cân nhắc `boolean` với `Boolean.TRUE`/`Boolean.FALSE`, hoặc một kiểu kết quả có cấu trúc nếu cần thông tin lỗi, thay vì dùng `null` cho `boolean`.

`0`, `false` hoặc chuỗi rỗng **không phải** giá trị mặc định do Dynamic Proxy tự tạo khi handler trả `null`. Nếu nghiệp vụ cho phép giá trị dự phòng, **handler phải chủ động quyết định** giá trị đó.

## 6. Phát hiện lỗi sớm trong `InvocationHandler`

Trong hệ thống có nhiều method, trả `null` như một giá trị mặc định cho mọi method chưa xử lý là nguy hiểm: có thể gây NPE ở method trả primitive, làm mất dữ liệu ở method trả reference, và phá các method của `Object`.

Một hướng thiết kế là dựa trên `method.getReturnType()`:

~~~java
Class<?> returnType = method.getReturnType();

if (returnType == void.class) {
    // Thực hiện tác dụng phụ xong rồi return null.
} else if (returnType == int.class) {
    // Trả Integer không null, hoặc ném lỗi nghiệp vụ rõ ràng.
} else if (returnType == Integer.class) {
    // Có thể trả Integer hoặc null nếu hợp đồng cho phép.
}
~~~

Đây là **đoạn minh họa nằm trong handler**, không phải chương trình Java hoàn chỉnh. Với những giao diện lớn, nên định nghĩa hợp đồng trả về và phân phối method rõ ràng hơn là chỉ suy luận hành vi từ kiểu trả về.

Nếu muốn fail-fast, handler có thể kiểm tra kết quả **trước khi return**:

~~~java
static Object requireIntResult(Method method, Object result) {
    if (method.getReturnType() != int.class) {
        throw new IllegalArgumentException("Method must return int");
    }
    if (!(result instanceof Integer)) {
        throw new IllegalStateException("Expected non-null Integer result");
    }
    return result;
}
~~~

Đây là **một phương thức hỗ trợ minh họa**, cần đặt trong một lớp và `import java.lang.reflect.Method;` trước khi dùng. Handler cho method trả `int` có thể gọi `return requireIntResult(method, calculated);`. Cách này biến một lỗi unboxing khó tìm thành ngoại lệ có thông điệp rõ ngay tại nơi dựng kết quả; nó không thay đổi quy tắc chuyển đổi của proxy. Với primitive khác, kiểm tra wrapper tương ứng; với reference type, dùng `returnType.isInstance(result)` cho giá trị không `null`.

Đặc biệt, khi trả về `String`, `List` hoặc kiểu class khác, **runtime chỉ kiểm tra kiểu có thể gán**. Với `List<String>`, generics bị xóa kiểu ở runtime; proxy không tự kiểm tra từng phần tử có phải `String`. Việc lấy phần tử sai kiểu và ép về `String` sau đó vẫn có thể gây CCE ở nơi sử dụng.

## 7. Những bẫy dễ gặp

| Sai lầm | Vì sao sai | Cách xử lý |
|---|---|---|
| `return null;` cho mọi method chưa biết | `int`, `boolean`, `long`... sẽ NPE; `hashCode()` của `Object` cũng trả `int` | Xử lý từng method, hoặc ném `UnsupportedOperationException` khi chưa có hợp đồng |
| Trả `7` cho method trả `long` | `7` autobox thành `Integer`, trong khi proxy cần `Long` | Trả `7L` hoặc `Long.valueOf(7)` |
| Dùng `return null` và mong nhận `0`/`false` | Dynamic Proxy không tự sinh mặc định primitive | Chọn giá trị dự phòng rõ ràng hoặc ném lỗi |
| Nghĩ mọi `null` đều làm proxy lỗi | Reference và `Void`/`Integer` có thể nhận `null` | Kiểm tra loại trả về và hợp đồng nghiệp vụ |
| Nghĩ `void` buộc handler phải trả `null` | Proxy bỏ qua giá trị; nhưng `null` là lựa chọn dễ đọc | Trả `null` vì rõ ý nghĩa, không trông chờ giá trị khác |
| Chỉ bắt lỗi **bên trong** handler | NPE/CCE chuyển kiểu có thể xuất hiện **sau** handler `return` | Kiểm tra kết quả sớm trong handler hoặc xử lý ngoại lệ tại caller |
| Dùng `== Integer.class` để phát hiện `int` | Primitive `int.class` khác wrapper `Integer.class` | So `getReturnType()` với đúng `int.class`/`Integer.class` |
| Bỏ qua `equals`, `hashCode`, `toString` | Các lời gọi này cũng tới handler và có hợp đồng trả về riêng | Xử lý method của `Object` một cách chủ ý |

Với phương thức trùng tên và danh sách tham số từ nhiều interface, Java Dynamic Proxy còn có quy tắc về **tính tương thích của kiểu trả về** khi tạo proxy. Nếu một chữ ký trùng có kiểu trả về primitive hoặc `void`, các interface đó phải khai báo cùng kiểu trả về; với kiểu reference, cần một kiểu có thể gán cho các kiểu trả về còn lại. Đừng suy luận rằng chỉ vì hai method cùng tên mà handler có thể tùy ý trả một kiểu bất kỳ.

## 8. Phân biệt Dynamic Proxy với phản chiếu gọi method

Hai API dễ bị lẫn:

- `InvocationHandler.invoke(proxy, method, args)` được **lớp Dynamic Proxy** gọi. Handler trả `Object` và lớp proxy chuyển kết quả theo chữ ký của interface.
- `Method.invoke(target, args)` là **Reflection API** gọi trực tiếp method trên target, cũng trả `Object`; kết quả primitive được boxing, method trả `void` cho `null`. Nếu method đích ném ngoại lệ, `Method.invoke` thông thường bọc trong `InvocationTargetException`.

Handler có thể dùng `method.invoke(delegate, args)` để ủy quyền cho implementation thật. Khi đó cần xử lý ngoại lệ phản chiếu cho phù hợp, và vẫn phải đảm bảo **giá trị cuối cùng handler trả cho proxy** khớp kiểu mà interface đòi hỏi. Chuyển kiểu của proxy không được bỏ qua chỉ vì handler gọi Reflection nội bộ.

Nếu handler ném một **checked exception** không được khai báo bởi interface method, lời gọi qua proxy thường chuyển thành `UndeclaredThrowableException`. Đây là quy tắc về **ngoại lệ**, khác hoàn toàn với NPE/CCE do **kiểu kết quả**; không nên trộn chúng thành một nguyên nhân.

## 9. Tự kiểm tra kiến thức

1. Với `boolean active()`, handler trả `Boolean.FALSE`, `Integer.valueOf(0)` và `null` thì từng lời gọi cho kết quả/lỗi gì? Vì sao `0` không tự biến thành `false`?
2. Với `Long total()` (wrapper), handler trả `null` có lỗi tại proxy không? Nếu caller viết `long x = proxy.total();`, lỗi phát sinh tại đâu?
3. Vì sao `double avg()` cần `Double` và `return 2.5f;` không phù hợp? Hãy thử thay bằng `return 2.5d;`.
4. Vì sao handler trả một đối tượng với method `void tick()` không làm người gọi nhìn thấy đối tượng đó?
5. Viết quy tắc cho `String name()` chấp nhận `null` nhưng vẫn từ chối đối tượng `Integer`, và cho `int count()` không chấp nhận `null`.
6. Bổ sung method `int hashCode()` vào kịch bản handler xử lý mọi method; nhận xét tác động nếu handler trả `null` khi client gọi `proxy.hashCode()`.

**Ghi nhớ:** kiểu trả về của `InvocationHandler.invoke` luôn là `Object`, nhưng hợp đồng bên ngoài thuộc về **interface method**. Proxy phải ép kiểu và unbox để trả đúng kết quả. `null` hợp lệ cho reference và bị bỏ qua khi method là `void`; `null` cho primitive gây NPE, còn đối tượng không tương thích gây CCE. Hãy quyết định rõ ý nghĩa “không có dữ liệu” ngay trong hợp đồng API và kiểm tra kết quả trước khi đưa ra khỏi handler nếu cần chẩn đoán sớm.

### Tài liệu tham khảo

- [Javadoc Java 17 — `InvocationHandler.invoke`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html#invoke(java.lang.Object,java.lang.reflect.Method,java.lang.Object%5B%5D))
- [Javadoc Java 17 — `Proxy`, quy tắc proxy class và các method trùng chữ ký](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-009">Checked exception và UndeclaredThrowableException</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception và UndeclaredThrowableException trong Java Dynamic Proxy

## 1. Bối cảnh và mục tiêu

Trong JDK Dynamic Proxy, lời gọi một phương thức của interface được chuyển tới **InvocationHandler.invoke(proxy, method, args)**. Handler có thể gọi một đối tượng đích, trả kết quả hoặc ném ngoại lệ. Vấn đề là **InvocationHandler.invoke** khai báo **throws Throwable**, nhưng phương thức trên interface được proxy triển khai vẫn có hợp đồng checked exception riêng.

Ví dụ, nếu interface khai báo **String read() throws IOException**, caller được chuẩn bị để xử lý **IOException**. Khi handler ném một **SQLException** không nằm trong hợp đồng, lớp proxy do JDK tạo ra không chuyển ngoại lệ checked này nguyên trạng: nó bọc bằng **UndeclaredThrowableException** (UTE), một unchecked exception.

Mục tiêu bài học là:
- Hiểu ranh giới giữa **throws** của phương thức interface và **throws Throwable** của invocation handler.
- Dự đoán khi nào ngoại lệ đi qua trực tiếp, khi nào được bọc bởi **UndeclaredThrowableException**.
- Phân biệt wrapper proxy với **InvocationTargetException** do **Method.invoke** tạo khi target ném lỗi.
- Viết handler ủy quyền có thể chuyển tiếp hoặc chuyển đổi ngoại lệ đúng hợp đồng, đồng thời giữ nguyên nguyên nhân gốc.

**Kiến thức cần trước:** checked/unchecked exception, **throw**, **throws**, interface, **Proxy.newProxyInstance**, **InvocationHandler**, **Method.invoke**.

## 2. Ba nhóm ngoại lệ cần phân biệt

**Checked exception** gồm các lớp con của **Exception** ngoại trừ **RuntimeException** và những lớp con của nó. Ví dụ **IOException**, **SQLException**, **ClassNotFoundException**. Với lời gọi được kiểm tra tĩnh, Java yêu cầu caller bắt hoặc khai báo chúng.

**Unchecked exception** là **RuntimeException** và các lớp con, hoặc **Error** và các lớp con. Ví dụ **IllegalArgumentException**, **IllegalStateException**, **AssertionError**. Những ngoại lệ này không cần xuất hiện trong mệnh đề **throws**.

**Ngoại lệ được phép bởi hợp đồng interface** là checked exception có kiểu tương thích với ít nhất một kiểu được phương thức khai báo trong **throws**. Ví dụ:

~~~java
interface Reader {
    String read() throws java.io.IOException;
}
~~~

Một **FileNotFoundException** là lớp con của **IOException**, nên được phép đi qua lời gọi **Reader.read()**. **SQLException** không tương thích với **IOException** và không được phép đi qua như một checked exception nguyên trạng.

Điều cần nhớ: **InvocationHandler.invoke** nhận trách nhiệm xử lý động ở runtime, còn **Reader.read()** là hợp đồng Java mà caller nhìn thấy. Trình biên dịch không thể biết handler nào sẽ được gắn vào proxy trong lúc chạy, nên JDK tạo lớp proxy bảo đảm quy tắc truyền ngoại lệ của interface.

## 3. Chính xác khi nào JDK Proxy ném UndeclaredThrowableException?

Khi **InvocationHandler.invoke** kết thúc bằng cách ném ngoại lệ, phương thức trên proxy xử lý về mặt ý nghĩa như sau:

| Throwable rời handler | Hợp đồng **read() throws IOException** | Kết quả |
| --- | --- | --- |
| **IOException("disk")** | Tương thích **IOException** | Caller nhận **IOException** trực tiếp |
| **FileNotFoundException("missing")** | Là lớp con của **IOException** | Caller nhận **FileNotFoundException** trực tiếp |
| **SQLException("db")** | Không tương thích **IOException** | Proxy ném **UndeclaredThrowableException** chứa **SQLException** |
| **IllegalStateException("bad")** | Unchecked | Caller nhận **IllegalStateException** trực tiếp |
| **AssertionError("bad")** | **Error**, unchecked | Caller nhận **AssertionError** trực tiếp |
| **InvocationTargetException** chứa **IOException** | Kiểu của wrapper không tương thích **IOException** | Proxy ném **UndeclaredThrowableException** chứa **InvocationTargetException** |

Luồng gọi:

~~~text
Caller -> proxy.read() -> InvocationHandler.invoke(...)
                          |
                          +-- trả giá trị ----------------> Caller
                          +-- RuntimeException / Error ---> Ném trực tiếp
                          +-- checked được khai báo ------> Ném trực tiếp
                          +-- checked ngoài hợp đồng -----> UndeclaredThrowableException
                                                            -> Caller
~~~

**UndeclaredThrowableException** thuộc gói **java.lang.reflect**, kế thừa **RuntimeException**, vì vậy caller không bị compiler buộc phải bắt nó. API **getUndeclaredThrowable()** trả về đúng Throwable mà proxy đã bọc; **getCause()** cung cấp nguyên nhân tương ứng.

Khi phương thức interface **không có throws**, mọi checked exception rời handler đều bị bọc theo quy tắc này. Ngược lại, interface khai báo **throws Exception** có thể cho nhiều checked exception đi qua, nhưng không có nghĩa nên khai báo như vậy: cần thiết kế hợp đồng có ý nghĩa cho caller.

## 4. Ví dụ đầy đủ Java 17+: bốn tình huống khác nhau

Lưu chương trình sau trong tệp **ProxyExceptionDemo.java**. Interface **Reader.read()** khai báo **IOException**. Target thật luôn ném **IOException("disk")**. Handler có bốn nhánh để thấy chính xác ngoại lệ nào rời khỏi proxy.

~~~java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLException;

public class ProxyExceptionDemo {
    public interface Reader {
        String read() throws IOException;
    }

    public static class FailingReader implements Reader {
        @Override
        public String read() throws IOException {
            throw new IOException("disk");
        }
    }

    enum Mode {
        DIRECT_CHECKED, UNDECLARED, BAD_DELEGATION, GOOD_DELEGATION
    }

    static void run(Mode mode) {
        Reader reader = (Reader) Proxy.newProxyInstance(
                ProxyExceptionDemo.class.getClassLoader(),
                new Class<?>[]{Reader.class},
                (proxy, method, args) -> {
                    if (mode == Mode.DIRECT_CHECKED) {
                        throw new IOException("disk");
                    }
                    if (mode == Mode.UNDECLARED) {
                        throw new SQLException("db");
                    }
                    if (mode == Mode.BAD_DELEGATION) {
                        // Method.invoke bọc lỗi target trong
                        // InvocationTargetException: đây là
                        // một checked exception chưa được tháo.
                        return method.invoke(new FailingReader(), args);
                    }

                    // GOOD_DELEGATION: truyền lại lỗi thật của target.
                    try {
                        return method.invoke(new FailingReader(), args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

        try {
            reader.read();
        } catch (IOException e) {
            System.out.println(
                    mode + ": IOException(" + e.getMessage() + ")");
        } catch (UndeclaredThrowableException e) {
            Throwable cause = e.getUndeclaredThrowable();
            if (cause instanceof InvocationTargetException ite) {
                cause = ite.getCause();
            }
            System.out.println(mode + ": UTE("
                    + cause.getClass().getSimpleName()
                    + ":" + cause.getMessage() + ")");
        }
    }

    public static void main(String[] args) {
        for (Mode mode : Mode.values()) {
            run(mode);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 ProxyExceptionDemo.java
java ProxyExceptionDemo
~~~

Đầu ra:

~~~text
DIRECT_CHECKED: IOException(disk)
UNDECLARED: UTE(SQLException:db)
BAD_DELEGATION: UTE(IOException:disk)
GOOD_DELEGATION: IOException(disk)
~~~

**Giải thích chi tiết:**

1. **DIRECT_CHECKED:** handler trực tiếp ném **IOException**. Vì **Reader.read()** có **throws IOException**, proxy chuyển ngoại lệ đúng kiểu ra ngoài; **catch (IOException)** nhận được.
2. **UNDECLARED:** handler ném **SQLException**, không tương thích với hợp đồng **throws IOException**. Proxy tạo **UndeclaredThrowableException**, đối tượng **SQLException** nằm trong **getUndeclaredThrowable()**.
3. **BAD_DELEGATION:** target ném **IOException**, nhưng **Method.invoke** bắt ngoại lệ target và ném **InvocationTargetException**. Handler không tháo wrapper, nên Throwable rời handler chính là **InvocationTargetException**, vốn không thuộc **throws IOException**. Proxy bọc thêm một tầng UTE.
4. **GOOD_DELEGATION:** handler bắt **InvocationTargetException** và ném **getCause()**. Throwable rời handler là **IOException** của target; proxy chuyển nguyên trạng để caller bắt bằng **catch (IOException)**.

Kết quả trên đã được đối chiếu qua chạy thử Java; thứ tự hiển thị chính là thứ tự các giá trị trong enum **Mode**. Ví dụ dùng import tường minh cho **java.lang.reflect.Proxy** và chương trình có thể biên dịch độc lập bằng JDK 17+.

## 5. InvocationTargetException khác gì UndeclaredThrowableException?

| Câu hỏi | **InvocationTargetException** | **UndeclaredThrowableException** |
| --- | --- | --- |
| Ai tạo wrapper? | **Method.invoke** khi code của method được gọi ném Throwable | Mã phương thức proxy do JDK tạo khi handler ném checked exception ngoài hợp đồng |
| Là checked hay unchecked? | Checked, kế thừa **ReflectiveOperationException** | Unchecked, kế thừa **RuntimeException** |
| Wrapper chứa gì? | Ngoại lệ thật do target ném | Throwable mà invocation handler đã ném ra nhưng interface không khai báo hợp lệ |
| Lấy nguyên nhân | **getCause()** hoặc **getTargetException()** | **getUndeclaredThrowable()** hoặc **getCause()** |
| Sửa ở đâu? | Bắt và tháo wrapper ngay tại handler khi ủy quyền bằng Reflection | Sửa hợp đồng hoặc cách handler phát sinh/chuyển đổi checked exception |

Một chuỗi điển hình của nhánh **BAD_DELEGATION**:

~~~text
UndeclaredThrowableException
  -> InvocationTargetException
       -> IOException("disk")
~~~

Không nên chỉ đọc dòng exception ngoài cùng rồi kết luận đối tượng đích ném sai kiểu. Target có thể ném **IOException** hoàn toàn phù hợp; lỗi nằm ở việc handler để **InvocationTargetException** vượt qua ranh giới proxy.

Nếu target ném **IllegalArgumentException** qua **Method.invoke**, Reflection vẫn bọc lỗi trong **InvocationTargetException**. Handler không tháo wrapper có thể khiến caller nhận UTE dù bản thân **IllegalArgumentException** là unchecked và đáng lẽ có thể được ném trực tiếp.

## 6. Mẫu handler chuyển tiếp ngoại lệ đúng cách

Khi handler chỉ ủy quyền từ proxy sang đối tượng thật bằng **Method.invoke**, mẫu cơ bản là:

~~~java
try {
    return method.invoke(target, args);
} catch (InvocationTargetException e) {
    throw e.getCause();
}
~~~

Đây là **đoạn trích dùng bên trong InvocationHandler.invoke**, giả sử biến **target** tồn tại và **method/args** là tham số callback. Nó không phải một chương trình độc lập. **InvocationHandler.invoke** khai báo **throws Throwable** nên có thể ném **e.getCause()**.

Tuy nhiên, **tháo InvocationTargetException chưa bảo đảm mọi checked exception đều hợp lệ**. Nếu target ném một **SQLException** trong khi interface chỉ khai báo **IOException**, proxy vẫn bọc SQLException bằng UTE. Cần xác định chính sách lỗi:

- **Chuyển tiếp nguyên nhân thật** nếu nó tương thích với hợp đồng **throws** của interface. Không cần đổi kiểu.
- **Chuyển đổi có ý nghĩa** nếu ngoại lệ của thư viện/hạ tầng không thuộc hợp đồng API. Ví dụ, một lỗi đọc tài liệu từ cơ sở dữ liệu có thể chuyển thành **IOException** nhưng cần kèm cause.
- **Giữ nguyên nguyên nhân** trong exception mới. Không nuốt exception hay ném wrapper không có cause làm mất dữ kiện chẩn đoán.
- **Sửa thiết kế hợp đồng** nếu caller thật sự phải xử lý một checked exception bổ sung; không mở rộng bừa bãi thành **throws Throwable** để né quy tắc.

Ví dụ minh họa chuyển một lỗi từ tầng dữ liệu thành lỗi đọc tài liệu:

~~~java
try {
    return loadDocumentFromDatabase();
} catch (java.sql.SQLException e) {
    throw new java.io.IOException("Không thể đọc tài liệu", e);
}
~~~

Đoạn này giả định có phương thức **loadDocumentFromDatabase()** có thể ném **SQLException** và code nằm trong handler cho phương thức interface **throws IOException**. Trình biên dịch sẽ cần triển khai đầy đủ phương thức đó nếu đưa ví dụ vào dự án. Không dùng chuyển đổi này tùy tiện khi không có quan hệ ý nghĩa giữa lỗi dữ liệu và lỗi đọc.

## 7. Các trường hợp biên

### 7.1. Không khai báo checked exception

Với interface có phương thức **void execute()** và không khai báo **throws**, handler ném **IOException** sẽ dẫn đến UTE. **throws Throwable** trên handler không thay đổi chữ ký của **execute()**.

### 7.2. Một proxy triển khai nhiều interface trùng chữ ký

Nếu các interface cùng khai báo một phương thức có cùng tên và cùng danh sách tham số nhưng **throws** khác nhau, checked exception đi ra từ handler phải tương thích với hợp đồng của **tất cả** những interface mà lời gọi tương ứng có thể thực hiện. Không đủ để exception chỉ tương thích với một interface.

Ví dụ **A.process() throws IOException** và **B.process() throws SQLException**: khi proxy triển khai cả **A** lẫn **B**, handler ném **IOException** sẽ bị proxy bọc trong UTE vì kiểu đó không tương thích với **throws SQLException** của **B.process()**. Đây là đặc điểm của các phương thức trùng chữ ký, không phụ thuộc cách caller ép kiểu proxy sang **A** hay **B**.

### 7.3. Các phương thức equals, hashCode và toString

Lời gọi **equals**, **hashCode**, **toString** trên proxy cũng được chuyển qua handler. Chúng không khai báo checked exception, nên handler ném checked exception cho các lời gọi này cũng tạo UTE. Cần quyết định rõ cách xử lý ba method đó trong handler nếu ứng dụng dùng chúng.

### 7.4. IllegalAccessException khi gọi Reflection

Nếu **Method.invoke** ném **IllegalAccessException** do quyền truy cập, đây là checked exception ở tầng Reflection, không phải lỗi được target ném và được bọc trong **InvocationTargetException**. Nếu handler chuyển nguyên nó ra ngoài nhưng interface không khai báo loại tương thích, proxy bọc UTE. Cách khắc phục cần xem quyền truy cập và module Java thực tế; tháo wrapper hoặc gọi **setAccessible(true)** tùy tiện không tự giải quyết mọi trường hợp.

## 8. Hướng chẩn đoán khi gặp UTE

1. **Kiểm tra mệnh đề throws trên interface.** Ghi lại phương thức được caller gọi và các interface có phương thức trùng chữ ký.
2. **Lấy throwable nằm trong UTE.** Gọi **getUndeclaredThrowable()** (hoặc **getCause()**). Đừng chỉ dựa vào tên UTE.
3. **Kiểm tra có InvocationTargetException không.** Nếu có, đọc tiếp **getCause()** để biết lỗi thật của target.
4. **Tìm đúng đoạn code trong handler.** Đoạn nào ném Throwable ra khỏi **InvocationHandler.invoke**? Đây là loại checked hợp lệ, loại ngoài hợp đồng, hay wrapper Reflection bị quên?
5. **Sửa đúng ranh giới.** Nếu target ném lỗi đã được khai báo nhưng handler để lọt wrapper, tháo wrapper. Nếu target thực sự ném checked exception ngoài hợp đồng, thiết kế lại cách chuyển đổi hoặc chữ ký interface.
6. **Kiểm thử cả hai đường gọi.** Gọi target trực tiếp để ghi nhận nguyên nhân, rồi gọi qua proxy để xác nhận kiểu exception caller thấy.

Có thể ghi lại chuỗi cause đầy đủ tại điểm giám sát, nhưng không nên luôn bắt UTE ở tất cả caller như biện pháp thay cho việc sửa handler.

## 9. Lỗi phổ biến

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Cho rằng **throws Throwable** của handler cho phép mọi checked exception tới caller | UTE xuất hiện ở runtime | Đối chiếu ngoại lệ với **throws** của interface |
| Ủy quyền bằng **return method.invoke(target, args)** mà không bắt **InvocationTargetException** | UTE có thể chứa **InvocationTargetException** dù lỗi gốc hợp lệ | Bắt wrapper và **throw e.getCause()** |
| Chuyển mọi exception sang **new RuntimeException()** không truyền cause | Mất thông tin gốc, khó xác định lỗi | Giữ cause và chọn exception đúng ngữ nghĩa |
| Ép kiểu mọi cause thành **IOException** | Có thể gây lỗi ép kiểu và che bug | Kiểm tra loại và chuyển đổi có chủ đích |
| Caller chỉ bắt checked exception đã khai báo và cho rằng đã xử lý mọi lỗi | UTE unchecked vẫn thoát ra | Sửa handler và theo dõi UTE ở ranh giới thích hợp |
| Chỉ nhìn thông báo UTE thay vì chuỗi cause | Chẩn đoán nhầm nguồn lỗi | Dùng **getUndeclaredThrowable()** và **getCause()** |

## 10. Thực hành và tự kiểm tra

**Bài tập 1:** Đổi interface thành phương thức **read()** không khai báo **IOException** nhưng giữ handler ném **IOException**. Sau khi chỉnh implementation để chương trình biên dịch, dự đoán kết quả: caller nhận **UndeclaredThrowableException**, bên trong là **IOException**.

**Bài tập 2:** Cho target ném **IllegalArgumentException**. So sánh handler chỉ gọi **Method.invoke** với handler bắt **InvocationTargetException** rồi ném **getCause()**. Trường hợp đầu tạo UTE chứa wrapper; trường hợp sau cho **IllegalArgumentException** thoát ra trực tiếp.

**Bài tập 3:** Cho handler ném **SQLException** khi interface chỉ khai báo **IOException**. Viết cách chuyển đổi sang **IOException** có cause, sau đó xác minh caller bắt **IOException** và đọc được **SQLException** từ **getCause()**.

**Bài tập 4:** Tạo hai interface cùng khai báo **process()** nhưng một bên **throws IOException**, bên kia **throws SQLException**. Tạo proxy triển khai cả hai và giải thích vì sao việc ném **IOException** trực tiếp từ handler dẫn đến UTE.

**Câu hỏi tự kiểm tra:**
- Vì sao **InvocationHandler.invoke throws Throwable** không xóa bỏ giới hạn checked exception của interface?
- Khi UTE chứa **InvocationTargetException** chứa **IOException**, ngoại lệ nào thực sự rời khỏi handler?
- Khi target ném **RuntimeException** qua **Method.invoke**, có cần tháo **InvocationTargetException** không?
- Vì sao sửa lỗi UTE bằng cách luôn thêm **throws Exception** vào interface có thể làm giảm chất lượng hợp đồng API?

**Đáp án cốt lõi:** Proxy bảo toàn hợp đồng của interface; **InvocationTargetException** là wrapper checked do Reflection tạo; nên tháo wrapper để truyền nguyên nhân thật; việc mở rộng **throws** phải phục vụ cách caller xử lý lỗi, không được dùng như mẹo che lỗi thiết kế.

Khi đã trả lời được các câu hỏi trên và dự đoán đúng cả bốn dòng output, bạn đã hiểu được mối liên hệ giữa checked exception, cơ chế ủy quyền Reflection và **UndeclaredThrowableException** của JDK Dynamic Proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-010">Gọi default method bằng InvocationHandler.invokeDefault</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi default method bằng `InvocationHandler.invokeDefault` trong Java

## 1. Vấn đề: default method cũng đi qua InvocationHandler

Dynamic proxy của JDK là một đối tượng được tạo bằng `java.lang.reflect.Proxy` để triển khai một hoặc nhiều interface. Khi gọi phương thức của proxy, lời gọi được chuyển tới `InvocationHandler.invoke(proxy, method, args)`, kể cả khi interface đã có phần thân `default method`. Vì thế, nếu handler muốn **thực thi phần thân mặc định** thay vì tự tính kết quả, nó cần chủ động chuyển tiếp lời gọi.

Từ **Java 16**, API `InvocationHandler.invokeDefault` cung cấp cách trực tiếp để làm việc này. Với Java 17+, đây là lựa chọn rõ ràng khi viết dynamic proxy có hỗ trợ default method.

```java
// Mẫu dùng bên trong InvocationHandler.invoke:
if (method.isDefault()) {
    return InvocationHandler.invokeDefault(proxy, method, args);
}
```

Đây là **đoạn minh họa đặt trong handler**, chưa phải chương trình độc lập. `method.isDefault()` giúp phân biệt default method thực sự với phương thức abstract của interface, phương thức của `Object` và các phương thức khác.

## 2. Cú pháp, tham số và cơ chế

Chữ ký API:

```java
public static Object invokeDefault(
        Object proxy, Method method, Object... args) throws Throwable;
```

| Tham số | Ý nghĩa |
| --- | --- |
| `proxy` | Đối tượng **dynamic proxy** đang xử lý lời gọi. |
| `method` | `Method` đại diện cho **default method hợp lệ** được khai báo trên một proxy interface hoặc được interface đó kế thừa. |
| `args` | Các đối số truyền cho default method, đúng số lượng và kiểu tham số; có thể là `null` khi phương thức không có tham số. |
| Giá trị trả về | `Object` chứa kết quả do phần thân default method trả về (hoặc `null` nếu phương thức trả về `void`). |

`InvocationHandler.invokeDefault(proxy, method, args)` thực thi phần thân mặc định theo ngữ nghĩa gần với lệnh JVM `invokespecial`, tương đương ý tưởng gọi `InterfaceName.super.method(...)` từ lớp proxy. Đây **không phải** lời gọi thông thường thông qua `Method.invoke(proxy, ...)`.

Điều kiện quan trọng: `method` phải là default method có thể được phân giải qua các interface của **chính proxy**. Không thể lấy một default method tùy ý từ interface không liên quan rồi yêu cầu proxy thực thi. Nếu một proxy interface override default method của interface cha, việc cố gọi trực tiếp phiên bản cha bằng một `Method` cũ có thể không còn hợp lệ.

Sơ đồ luồng:

```text
client gọi proxy.defaultMethod(...)
    → JVM chuyển vào handler.invoke(proxy, method, args)
        → method.isDefault() == true
            → InvocationHandler.invokeDefault(proxy, method, args)
                → chạy thân default method trong interface
                    → trả kết quả về handler rồi về client
```

Nếu bên trong thân default method lại gọi một phương thức interface khác qua `this`, lời gọi ảo đó vẫn có thể đi qua handler của proxy. Handler phải xử lý được cả lời gọi tiếp theo.

## 3. Ví dụ Java 17+ chạy độc lập

Lưu tệp `InvokeDefaultDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class InvokeDefaultDemo {
    public interface TextService {
        String code();

        default String greeting(String name) {
            return "Hello, " + name + " from " + code();
        }

        default String plain() {
            return "DEFAULT";
        }
    }

    public static void main(String[] args) {
        InvocationHandler handler = (proxy, method, callArgs) -> {
            if (method.isDefault()) {
                System.out.println("handler default: " + method.getName());
                return InvocationHandler.invokeDefault(
                        proxy, method, callArgs);
            }

            if (method.getName().equals("code")
                    && method.getParameterCount() == 0) {
                return "proxy";
            }

            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "TextServiceProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == callArgs[0];
                    default -> throw new UnsupportedOperationException(
                            method.toString());
                };
            }

            throw new UnsupportedOperationException(method.toString());
        };

        TextService service = (TextService) Proxy.newProxyInstance(
                TextService.class.getClassLoader(),
                new Class<?>[] { TextService.class },
                handler);

        System.out.println(service.greeting("An"));
        System.out.println(service.plain());
        System.out.println(service.code());
        System.out.println(service.toString());
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 InvokeDefaultDemo.java
java InvokeDefaultDemo
```

Stdout chính xác:

```text
handler default: greeting
Hello, An from proxy
handler default: plain
DEFAULT
proxy
TextServiceProxy
```

Diễn giải theo thứ tự:

1. `service.greeting("An")` tới handler. Vì `greeting` là default method, `method.isDefault()` là `true` và `invokeDefault` chạy phần thân đã viết trong `TextService`.
2. Trong thân `greeting` có `code()`; đây là phương thức abstract được gọi trên proxy, nên handler nhận lời gọi `code` và trả `"proxy"`. Kết quả ghép thành `Hello, An from proxy`.
3. `plain()` không nhận tham số: `callArgs` trong handler có thể bằng `null`, và truyền nguyên giá trị đó cho `invokeDefault` là hợp lệ.
4. `code()` không phải default method: handler tự cung cấp kết quả.
5. `toString()` thuộc nhóm phương thức `Object` được proxy chuyển đến handler; nhánh xử lý riêng tránh việc mọi lời gọi không phải default đều bị ném lỗi. Ví dụ cũng xử lý `equals` và `hashCode` theo danh tính proxy.

## 4. Tại sao không dùng Method.invoke(proxy, args)?

Giả sử trong handler bạn viết:

```java
// Sai khi mục tiêu là chạy phần thân default method:
return method.invoke(proxy, args);
```

`Method.invoke` gọi phương thức trên chính đối tượng `proxy`. Đối tượng này lại chuyển lời gọi về handler, dẫn tới chu trình lặp và có nguy cơ `StackOverflowError`. `InvocationHandler.invokeDefault` đi vào **phần thân mặc định được chỉ định**, nên giải quyết đúng bài toán.

Cũng không thể viết `SomeInterface.super.method(...)` trực tiếp trong handler lambda: cú pháp đó chỉ hợp lệ ở ngữ cảnh Java cho phép gọi default method của superinterface, không phải một lời gọi động dựa vào đối tượng `Method`.

Trước Java 16, các cách xử lý thường liên quan `MethodHandles.Lookup` và quyền truy cập module; không nên sao chép những ví dụ cũ sang Java 17+ khi API `invokeDefault` đã đáp ứng nhu cầu.

## 5. Kế thừa, override và nhiều default method trùng chữ ký

**Kế thừa:** nếu `Child extends Parent` và `Child` không override default method `Parent.run()`, proxy chỉ triển khai `Child` vẫn có thể gọi phần thân default kế thừa từ `Parent` thông qua `invokeDefault`. `Method` do handler nhận có thể có `getDeclaringClass() == Parent.class`.

**Override:** nếu sau đó `Child` định nghĩa lại `default run()`, phương thức hiệu lực là phiên bản của `Child`. Một handler lưu sẵn `Parent.class.getMethod("run")` rồi ép `invokeDefault` gọi bản cha có thể nhận `IllegalArgumentException`, vì bản cha không còn là mục tiêu phân giải hợp lệ qua `Child`. Đây là điểm cần kiểm tra khi nâng cấp thư viện interface.

**Hai interface độc lập có cùng default method:** handler có thể chủ động chọn một bản bằng cách lấy đúng `Method` của interface mong muốn. Ví dụ độc lập sau cho kết quả cùng là `second`, bất kể client ép proxy về `First` hay `Second`:

Lưu tệp `ChooseDefaultDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ChooseDefaultDemo {
    public interface First {
        default String origin() { return "first"; }
    }

    public interface Second {
        default String origin() { return "second"; }
    }

    public static void main(String[] args) throws Exception {
        Method chosen = Second.class.getMethod("origin");

        InvocationHandler handler = (proxy, method, callArgs) -> {
            if (method.getName().equals("origin")) {
                return InvocationHandler.invokeDefault(
                        proxy, chosen, callArgs);
            }
            throw new UnsupportedOperationException(method.toString());
        };

        Object proxy = Proxy.newProxyInstance(
                ChooseDefaultDemo.class.getClassLoader(),
                new Class<?>[] { First.class, Second.class },
                handler);

        System.out.println(((First) proxy).origin());
        System.out.println(((Second) proxy).origin());
    }
}
```

```bash
javac --release 17 -encoding UTF-8 ChooseDefaultDemo.java
java ChooseDefaultDemo
```

```text
second
second
```

Ví dụ trên chọn rõ `Second.origin()`. Nó **không** nói rằng Java tự ưu tiên `Second`; thứ tự và `Method` ban đầu chuyển đến handler không nên được sử dụng thay cho quyết định nghiệp vụ. Nếu muốn chọn theo đối tượng gọi ban đầu, hãy thiết kế giao diện/điểm vào rõ ràng: với hai chữ ký trùng nhau, handler không tự nhận được thông tin client đã cast sang interface nào.

## 6. Kiểm tra đầu vào, lỗi và ngoại lệ

| Trường hợp | Hành vi/cách xử lý |
| --- | --- |
| `method.isDefault() == false` | Không chuyển phương thức abstract hoặc `Object` sang `invokeDefault`; tự xử lý/delegate. |
| `proxy` không phải dynamic proxy, hoặc `method` không phải default method hợp lệ của proxy | `IllegalArgumentException`. |
| `method` đã bị proxy interface override khiến mục tiêu cũ không còn hợp lệ | Có thể nhận `IllegalArgumentException`; cập nhật lựa chọn `Method` theo interface hiệu lực. |
| Số đối số sai hoặc kiểu đối số không tương thích, kể cả việc unbox primitive | `IllegalArgumentException`. |
| `proxy == null` hoặc `method == null` | `NullPointerException`. |
| Lớp khai báo default method không thể truy cập từ nơi gọi | Có thể phát sinh `IllegalAccessException`. |
| Thân default method tự ném ngoại lệ | `invokeDefault` có thể ném tiếp `Throwable` đó; phải cân nhắc hợp đồng `throws` của phương thức interface. |

Handler có chữ ký `Object invoke(...) throws Throwable`, nên có thể chuyển tiếp `Throwable` từ `invokeDefault` mà không cần ép thành `RuntimeException`. Nếu ngoại lệ checked không được khai báo bởi phương thức interface thoát khỏi handler, lời gọi trên proxy có thể bao nó trong `UndeclaredThrowableException`. Với phương thức trả về primitive, kết quả handler trả về phải tương thích với wrapper tương ứng; trả `null` sẽ gây lỗi khi proxy cần unbox.

Để dùng an toàn, hãy kiểm tra `method.isDefault()` trước, truyền đúng proxy thực, chuyển nguyên `args` (kể cả `null` khi không có tham số), và xử lý riêng các phương thức còn lại.

## 7. Lỗi thường gặp và cách tránh

- **Tự gọi lại proxy bằng reflection:** `method.invoke(proxy, args)` không phải cách gọi thân default; dùng `invokeDefault`.
- **Cho rằng default method tự chạy:** dynamic proxy chuyển lời gọi vào handler; muốn chạy phần thân mặc định phải chọn làm điều đó.
- **Nhầm `isDefault()` với `!isAbstract()`:** phương thức `Object` cũng không abstract nhưng không phải default method.
- **Tự tạo `Method` theo tên mà không xét proxy interface:** chỉ dùng phương thức default hợp lệ, còn hiệu lực và có thể truy cập.
- **Giả sử `args` luôn khác `null`:** handler nhận `null` khi phương thức không có tham số; nếu tự đọc `args[0]` phải chắc chắn phương thức cần đối số.
- **Bỏ qua đường gọi tiếp từ default method:** phần thân default có thể gọi phương thức interface khác và làm handler chạy thêm lần nữa.
- **Không kiểm tra tương thích JDK:** `InvocationHandler.invokeDefault` chỉ xuất hiện từ Java 16; biên dịch với `--release 11` không thể gọi API này.
- **Quên kế thừa và override:** việc thêm default method mới trong interface con có thể thay đổi phương thức mà `invokeDefault` được phép gọi.

## 8. Câu hỏi tự kiểm tra

1. **Vì sao `method.invoke(proxy, args)` không phù hợp để chạy default method từ handler?** Vì nó gọi lại phương thức trên proxy, có thể đưa lời gọi trở vào chính handler và lặp vô hạn.
2. **Default method gọi một abstract method cùng interface thì sao?** Lời gọi thực hiện trên proxy; handler lại nhận phương thức abstract và phải xử lý nó.
3. **Một proxy triển khai `First` và `Second` đều có `default origin()`. Có thể chọn `Second` không?** Có: lấy `Second.class.getMethod("origin")` rồi truyền phương thức đó vào `InvocationHandler.invokeDefault`, miễn hợp lệ với các interface của proxy.
4. **Có thể truyền `null` cho `args` không?** Có, khi default method có 0 tham số; với phương thức có tham số phải truyền số lượng/kiểu đối số hợp lệ.
5. **API này dùng trên Java 11 được không?** Không; API xuất hiện từ Java 16.

**Ghi nhớ:** `InvocationHandler.invokeDefault` là cầu nối từ `InvocationHandler` tới **phần thân default method hợp lệ của interface trên dynamic proxy**. Hãy dùng `Method.isDefault()` để nhận diện, xử lý riêng phương thức abstract/`Object`, và kiểm tra kỹ kế thừa, override, quyền truy cập cùng đối số.

Tham khảo: [Javadoc Java 17 — InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-011">Tránh gọi lại chính proxy gây đệ quy vô hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh gọi lại chính proxy gây đệ quy vô hạn trong Java Dynamic Proxy

## 1. Mục tiêu và bối cảnh

Khi sử dụng **JDK Dynamic Proxy**, mỗi lời gọi phương thức trên proxy được chuyển đến `InvocationHandler.invoke(...)`. Lỗi thường gặp là trong handler, lập trình viên lại gọi phương thức **trên chính đối tượng proxy** để “thực thi phương thức gốc”. Lệnh này kích hoạt handler thêm lần nữa; vòng lặp có thể tiếp diễn đến khi cạn ngăn xếp và phát sinh `StackOverflowError`.

Sau bài này, bạn có thể:

- Phân biệt `proxy` (đối tượng trung gian) với `target` (đối tượng triển khai thật).
- Nhận diện đệ quy trực tiếp qua `method.invoke(proxy, args)` và đệ quy gián tiếp qua logging, `toString()`, `equals()` hay `hashCode()`.
- Chuyển tiếp lời gọi đúng sang `target`, xử lý ngoại lệ phản chiếu và các phương thức của `Object`.
- Hiểu khi nào một lời gọi lại proxy là **có chủ đích** và khi nào nó tạo chu trình không mong muốn.

Bài này tập trung vào **JDK Dynamic Proxy** (`java.lang.reflect.Proxy`), vốn tạo proxy cho **interface**. Cơ chế proxy dựa trên subclass của các thư viện khác có thể khác về cách chuyển tiếp lời gọi.

## 2. Cơ chế gây đệ quy

Một handler được khai báo theo giao diện `InvocationHandler`:

```java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
```

Trong đó:

- `proxy` là **chính đối tượng proxy** mà bên gọi vừa sử dụng, không phải đối tượng nghiệp vụ thật.
- `method` mô tả phương thức mà bên gọi đang yêu cầu; có thể là phương thức của interface hoặc `equals`, `hashCode`, `toString` từ `Object`.
- `args` chứa tham số của lời gọi; có thể là `null` nếu phương thức không có tham số.

**Ví dụ sai**, chỉ là mảnh code đặt trong ngữ cảnh đã import `InvocationHandler`:

```java
InvocationHandler badHandler = (proxy, method, args) -> {
    System.out.println("Gọi " + method.getName());
    return method.invoke(proxy, args); // SAI: gọi lại chính proxy
};
```

Nếu client gọi `service.greet("An")`, luồng thực hiện sẽ thành:

```text
client gọi proxy.greet("An")
    -> InvocationHandler.invoke(proxy, greet, ["An"])
       -> method.invoke(proxy, ["An"])
          -> InvocationHandler.invoke(proxy, greet, ["An"])
             -> method.invoke(proxy, ["An"])
                -> ... lặp lại ...
```

`Method.invoke(...)` không “bỏ qua” handler khi receiver là proxy. Đây vẫn là lời gọi vào một phương thức của proxy, nên cơ chế chuyển tiếp lại hoạt động. Kết cục thường là `StackOverflowError`; dấu vết có thể xen kẽ giữa phương thức proxy, handler và Java Reflection. **Không nên chạy đoạn sai như một bài thử tải**, vì việc tràn ngăn xếp không mang thêm giá trị học tập.

Quy tắc chính: **`proxy` là điểm tiếp nhận lời gọi; `target` mới là nơi thực hiện công việc nếu đang xây dựng proxy ủy quyền.**

## 3. Cách sửa: chuyển tiếp vào đối tượng thật

Với trường hợp proxy dùng để đo thời gian, ghi log, kiểm tra quyền hoặc bổ sung hành vi quanh một đối tượng có sẵn, cấu trúc đúng là:

```text
caller -> proxy -> InvocationHandler
                       |
                       +-> ghi log / kiểm tra
                       +-> method.invoke(target, args)
                       +-> trả kết quả cho caller
```

Cần bảo đảm:

1. `target` là một đối tượng **khác proxy**, triển khai interface có phương thức được gọi.
2. Handler dùng `method.invoke(target, args)` thay cho `method.invoke(proxy, args)`.
3. Nếu thao tác trên `target` lại gọi ngược vào proxy, phải xem xét luồng gọi thật để loại bỏ **chu trình phụ thuộc**. Chỉ thay receiver bằng `target` không tự động giải quyết mọi vòng lặp gián tiếp.

### Chương trình Java 17+ chạy độc lập

Tạo tệp `SafeProxyDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class SafeProxyDemo {
    public interface GreetingService {
        String greet(String name);
    }

    static final class RealGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Xin chào, " + name + "!";
        }
    }

    public static void main(String[] args) {
        GreetingService target = new RealGreetingService();

        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "GreetingProxy@" +
                            Integer.toHexString(System.identityHashCode(proxy));
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }

            System.out.println("Gọi: " + method.getName());
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };

        GreetingService service = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(),
                new Class<?>[] { GreetingService.class },
                handler);

        System.out.println(service.greet("An"));
        System.out.println(service.toString().startsWith("GreetingProxy@"));
        System.out.println(service.equals(service));
        System.out.println(service.hashCode() == System.identityHashCode(service));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac SafeProxyDemo.java
java SafeProxyDemo
```

Kết quả (chương trình này đã được chạy để xác minh):

```text
Gọi: greet
Xin chào, An!
true
true
true
```

**Giải thích:**

- `target` là `RealGreetingService` và thực sự xử lý `greet`.
- Handler ghi tên phương thức bằng `method.getName()`; thao tác trên metadata này không gọi lại proxy.
- `method.invoke(target, arguments)` thực thi phương thức trên `target`. Kết quả trả về qua handler rồi về client, không tạo vòng lặp.
- Ba phép thử cuối xác minh hành vi của `toString`, `equals` và `hashCode`. `toString()` được gọi thật trên proxy, nhưng nhánh dành cho `Object` trả chuỗi tự tạo **mà không gọi lại** `proxy.toString()`.
- `System.identityHashCode(proxy)` lấy mã băm định danh trực tiếp, không gọi `proxy.hashCode()`.
- `InvocationTargetException` gói ngoại lệ do phương thức đích ném; `throw e.getCause()` truyền nguyên nhân gốc qua `InvocationHandler`. Việc proxy chuyển tiếp checked exception vẫn tuân theo khai báo `throws` của interface.

Ví dụ dùng quy ước equality theo **identity của proxy** để bảo đảm `service.equals(service)` là `true`. Đây là một lựa chọn có chủ đích: nếu ứng dụng yêu cầu equality dựa trên nghiệp vụ, hãy thiết kế cả `equals`/`hashCode` một cách nhất quán thay vì ủy quyền tùy tiện.

## 4. Bẫy gián tiếp: logging cũng có thể gọi lại proxy

Ngay cả khi đã dùng `method.invoke(target, args)`, handler vẫn có thể tự gây đệ quy:

```java
// Mảnh code minh họa lỗi bên trong một handler.
System.out.println("Proxy: " + proxy); // SAI trong handler chung
return method.invoke(target, args);
```

Toán tử nối chuỗi biến `proxy` thành chuỗi, dẫn đến `proxy.toString()`. Với JDK dynamic proxy, `toString()` cũng đi qua `InvocationHandler`. Lời gọi mới lại chạy chính dòng logging đó, rồi tiếp tục gọi `toString()`.

Các tình huống cần phân biệt:

| Biểu thức trong handler | Có thể gọi lại handler? | Cách thay thế hoặc lưu ý |
| --- | --- | --- |
| `method.invoke(proxy, args)` | **Có**, với phương thức được proxy điều phối | Chuyển tiếp vào `target` thật, nếu đang làm delegation |
| `proxy.toString()`, `"proxy=" + proxy`, `System.out.println(proxy)` | **Có**, qua `toString` | Ghi `method.getName()` hoặc `System.identityHashCode(proxy)` |
| `proxy.hashCode()` | **Có**, qua `hashCode` | Dùng `System.identityHashCode(proxy)` khi cần identity |
| `proxy.equals(other)` | **Có**, qua `equals` | Dùng `proxy == other` nếu mục tiêu là so sánh tham chiếu |
| `proxy.getClass()` | **Không** đối với lời gọi `Object.getClass()` thông thường | `getClass` là `final`, không được override để chuyển đến handler |
| `method.getName()`, `method.getDeclaringClass()` | **Không** | Chỉ đọc metadata của `Method` |

Không nên viết `logger.info("proxy={}", proxy)` và mặc nhiên nghĩ rằng cách log có placeholder sẽ tránh gọi `toString()`. Nhiều logger định dạng đối tượng tại thời điểm phát log; việc gọi `toString()` có thể xảy ra sau đó. Khi debug handler, ưu tiên một nhãn cố định, tên phương thức và identity hash.

## 5. Vì sao phải chú ý các phương thức của Object?

Trên JDK dynamic proxy, các lời gọi `equals(Object)`, `hashCode()` và `toString()` được gửi đến `InvocationHandler`. Do đó, một handler chỉ biết gọi `method.invoke(target, args)` có thể gây ra **lỗi ngữ nghĩa** dù không tràn ngăn xếp.

Ví dụ, nếu `target` dùng `Object.equals` mặc định, chuyển tiếp `proxy.equals(proxy)` thành `target.equals(proxy)` có thể trả về `false`. Khi đó tính chất phản xạ của `equals` (`x.equals(x) == true`) bị vi phạm. Tương tự, `proxy.hashCode()` có thể trả mã băm của target trong khi `equals` được định nghĩa bằng identity proxy, khiến hợp đồng `equals/hashCode` thiếu nhất quán nếu không cân nhắc.

Một cách xử lý an toàn cho proxy dùng identity là:

```java
if (method.getDeclaringClass() == Object.class) {
    return switch (method.getName()) {
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "ServiceProxy@" +
                Integer.toHexString(System.identityHashCode(proxy));
        default -> throw new UnsupportedOperationException(method.getName());
    };
}
```

Đây là mảnh code bên trong `invoke`; `args[0]` được sử dụng trong nhánh `equals(Object)` có một tham số. Nếu interface có yêu cầu đặc biệt về identity hoặc equality, hãy thay đổi chính sách này có chủ đích và kiểm thử.

## 6. Phân biệt recursion ngoài ý muốn với lời gọi lồng nhau hợp lệ

Không phải cứ `InvocationHandler.invoke` chạy lại là lỗi. Chẳng hạn, khi triển khai một default method của interface, phương thức này có thể gọi phương thức khác trên `this`, và lời gọi thứ hai hợp lệ nếu cuối cùng được xử lý xong.

Với Java 17, API `InvocationHandler.invokeDefault(proxy, method, args)` cho phép gọi phần triển khai mặc định của một phương thức interface **mà không gọi lại handler để thực thi chính phương thức default đó**:

```java
// Mảnh code trong InvocationHandler.invoke(...), dùng khi phù hợp:
if (method.isDefault()) {
    return InvocationHandler.invokeDefault(proxy, method, args);
}
```

Cần lưu ý rằng **thân default method vẫn có thể gọi các phương thức khác trên proxy**. Nếu các lời gọi đó quay về chính default method mà không có điểm dừng, chu trình vẫn tồn tại. `invokeDefault` không phải cơ chế chống đệ quy tổng quát.

Tương tự, proxy có thể bọc thêm một proxy khác để xây dựng chuỗi interceptor hợp lệ. Điều cần loại bỏ là **chu trình gọi** như `proxyA -> handlerA -> proxyB -> handlerB -> proxyA`, không phải toàn bộ lời gọi lồng nhau.

## 7. Chẩn đoán và khắc phục khi gặp StackOverflowError

Khi thấy `StackOverflowError` trong quá trình gọi dynamic proxy:

1. **Đọc chuỗi frame lặp lại** trong stack trace. Tìm các frame của `InvocationHandler.invoke`, `Method.invoke`, lớp proxy và các hàm logging. Chuỗi này giúp xác định lời gọi nào quay lại điểm xuất phát.
2. **Tìm mọi vị trí dùng `proxy` bên trong handler**, đặc biệt là receiver của lời gọi phương thức, phép chuyển thành chuỗi và callback do target thực hiện. Đừng chỉ tìm đúng dòng `method.invoke(proxy, args)`.
3. **Xác định đối tượng nhận lời gọi thật**: nếu handler được thiết kế để ủy quyền, `target` phải triển khai interface cần gọi và không được trỏ về proxy vừa tạo. Nếu dùng nhiều tầng proxy, vẽ thứ tự các tầng để phát hiện chu trình.
4. **Sửa đường đi của lời gọi**: gọi target thật, cung cấp cách xử lý riêng cho `Object`, hoặc dùng API dành riêng cho default method khi thích hợp.
5. **Kiểm thử hành vi**: gọi một phương thức nghiệp vụ, `toString`, `equals`, `hashCode`, trường hợp không có tham số, đường đi ném ngoại lệ và một target được bọc bởi nhiều interceptor nếu ứng dụng có dùng.

Một lời chặn re-entry như `ThreadLocal<Boolean>` có thể hữu ích trong trường hợp ứng dụng **cố ý cấm** một luồng đi vào handler lần hai; phải xóa trạng thái trong `finally`. Tuy nhiên, đặt chặn chung cho mọi lời gọi lồng nhau có thể từ chối cả tương tác hợp lệ giữa các phương thức. Cách giải quyết chính vẫn là thiết kế lại đường chuyển tiếp để không tạo chu trình.

Đừng cố “chữa” bằng cách tăng kích thước stack (`-Xss`) hoặc bắt và bỏ qua `StackOverflowError`. Những cách đó không sửa luồng gọi sai, đồng thời có thể khiến ứng dụng hỏng theo cách khó theo dõi hơn.

## 8. Lỗi phổ biến và bài tập tự kiểm tra

**Lỗi phổ biến:**

- Dùng nhầm `proxy` thay vì `target` làm receiver của `Method.invoke`.
- Ghi log nguyên proxy bên trong handler và vô tình kích hoạt `toString`.
- Bỏ qua `equals/hashCode/toString` hoặc chuyển tiếp chúng sang target mà không kiểm tra hợp đồng đối tượng.
- Giữ tham chiếu đến proxy làm target khi cấu hình nhiều interceptor.
- Bọc thêm `InvocationTargetException` mà không kiểm tra nguyên nhân gốc; cách này làm khó chẩn đoán ngoại lệ thật của phương thức nghiệp vụ.
- Coi mọi lời gọi lồng nhau là lỗi và dùng chặn re-entry quá rộng.

**Tự kiểm tra:**

1. Vì sao `method.invoke(proxy, args)` có thể gọi `InvocationHandler.invoke` lần nữa? Vì receiver vẫn là proxy, nên lời gọi được điều phối qua handler.
2. Nếu handler in `"Đang xử lý: " + proxy`, điều gì có thể xảy ra? Phép nối chuỗi gọi `toString` của proxy, thường kích hoạt chính handler đó.
3. Làm sao lấy một mã băm định danh để log mà không đi qua `hashCode` của proxy? Dùng `System.identityHashCode(proxy)`.
4. Khi nào `method.invoke(target, args)` vẫn không an toàn? Khi `target` lại là proxy đó, có chu trình qua những proxy khác hoặc target callback quay ngược về proxy vô hạn.
5. `InvocationHandler.invokeDefault` có bảo đảm không còn vòng lặp không? Không; nó chỉ gọi trực tiếp phần thân default method, còn thân này có thể phát sinh lời gọi khác và tạo chu trình.

**Ghi nhớ:** Trong `InvocationHandler`, biến `proxy` đại diện cho đầu vào của lời gọi đã bị chặn. Muốn ủy quyền, hãy gọi **đối tượng đích thật** và kiểm soát các đường gọi gián tiếp, nhất là logging và ba phương thức `equals/hashCode/toString`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-012">Nhiều interface có phương thức trùng chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều interface có phương thức trùng chữ ký trong Java Dynamic Proxy

## 1. Vấn đề cần hiểu

Một dynamic proxy tạo bằng `java.lang.reflect.Proxy` có thể triển khai **nhiều interface** cùng lúc. Điều gì xảy ra nếu các interface ấy khai báo phương thức có **cùng tên và cùng danh sách kiểu tham số**?

Ví dụ, `Readable` và `Auditable` đều có `String status()`:

~~~java
interface Readable {
    String status();
}
interface Auditable {
    String status();
}
~~~

Về phía người gọi, có thể ép cùng một proxy sang `Readable` hoặc `Auditable` rồi gọi `status()`. Tuy nhiên, class proxy được tạo chỉ có cơ chế thực thi lời gọi chung; nó **không nhận được thông tin người gọi đã ép đối tượng sang interface nào**. Vì vậy, `InvocationHandler.invoke(proxy, method, args)` không thể dựa vào kiểu biến tại chỗ gọi để biết “lời gọi đến từ Readable hay Auditable”.

Điểm cần nắm vững:

- **Thứ tự interface khi tạo proxy có ý nghĩa.** Với các phương thức trùng tên và cùng tham số, proxy ưu tiên `Method` từ interface xuất hiện sớm nhất trong mảng interface (kể cả phương thức interface ấy kế thừa), trong trường hợp phương thức được gộp cùng kiểu trả về.
- **Kiểu trả về phải tương thích** thì proxy mới tạo được. Các kiểu trả về tham chiếu có thể hiệp biến; các kiểu primitive hoặc `void` phải trùng khớp.
- **Checked exception phải hợp lệ với tất cả khai báo liên quan**; nếu không, proxy có thể bọc ngoại lệ bằng `UndeclaredThrowableException`.
- **Default method không tự chạy** chỉ vì interface có thân hàm. Handler muốn dùng một default implementation phải lựa chọn và gọi có chủ đích.
- Một số trường hợp kiểu trả về khác nhau cần phân biệt thêm theo chữ ký ở cấp JVM; không suy diễn máy móc rằng mọi lời gọi trùng tên đều chuyển tới cùng một `Method`.

## 2. “Trùng chữ ký” nghĩa là gì?

Ở mức mã nguồn Java, **method signature** gồm tên phương thức và kiểu tham số (kèm tham số kiểu của phương thức nếu có); **kiểu trả về và `throws` không dùng để overload**. Vì vậy:

| Interface A | Interface B | Quan hệ |
| --- | --- | --- |
| `String name()` | `String name()` | Cùng tên, tham số và kiểu trả về |
| `Number id()` | `Integer id()` | Cùng tên/tham số, kiểu trả về hiệp biến |
| `void send(String s)` | `void send(String s) throws IOException` | Cùng tên/tham số, khác `throws` |
| `void send(String s)` | `void send(int n)` | **Khác** chữ ký vì kiểu tham số khác |
| `int count()` | `long count()` | Cùng tên/tham số nhưng kiểu trả về primitive không tương thích |

Một proxy có thể phục vụ các lời gọi từ nhiều interface. Song phép kiểm tra hợp lệ khi tạo proxy và cách phân phối vào handler tuân theo quy tắc riêng của `Proxy`; nó không hoàn toàn giống việc tự viết `class implements A, B` trong mã nguồn.

## 3. Thứ tự interface quyết định `Method` được thấy trong handler

Tạo proxy bằng:

~~~java
Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] { Readable.class, Auditable.class },
        handler);
~~~

Khi cả hai interface có đúng `String status()`, lời gọi qua `((Readable) proxy).status()` và `((Auditable) proxy).status()` đều chuyển tới handler cùng **phương thức đại diện**: `Readable.status()` vì `Readable` đứng trước. Đổi thứ tự mảng thì `Auditable.status()` được chọn. Thứ tự không thay đổi kiểu static của lời gọi tại compiler; nó thay đổi cách proxy sinh ra thực thi và cách chọn `Method`.

Một chi tiết dễ bỏ qua: nếu interface đứng trước **kế thừa** phương thức từ interface cha, `method.getDeclaringClass()` có thể trả về **interface cha thực sự khai báo**, thay vì interface trực tiếp được đưa vào mảng. Do đó, `getDeclaringClass()` không bảo đảm luôn là một phần tử của mảng interface.

### Ví dụ chạy được: đổi thứ tự và ép qua hai interface

Tạo file `DuplicateMethodDemo.java`:

~~~java
import java.lang.reflect.Proxy;

public class DuplicateMethodDemo {
    public interface Readable { String status(); }
    public interface Auditable { String status(); }

    static Object makeProxy(Class<?> first, Class<?> second) {
        return Proxy.newProxyInstance(
                DuplicateMethodDemo.class.getClassLoader(),
                new Class<?>[] { first, second },
                (proxy, method, args) -> {
                    if (method.getName().equals("status")) {
                        System.out.println("Method from: "
                                + method.getDeclaringClass().getSimpleName());
                        return "READY";
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    public static void main(String[] args) {
        Object first = makeProxy(Readable.class, Auditable.class);
        System.out.println(((Readable) first).status());
        System.out.println(((Auditable) first).status());

        Object reversed = makeProxy(Auditable.class, Readable.class);
        System.out.println(((Readable) reversed).status());
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac DuplicateMethodDemo.java
java DuplicateMethodDemo
~~~

Kết quả:

~~~text
Method from: Readable
READY
Method from: Readable
READY
Method from: Auditable
READY
~~~

Hai lời gọi đầu dùng các kiểu tham chiếu khác nhau nhưng handler vẫn thấy `Readable`. Lần thứ ba cho thấy **thứ tự đăng ký interface** làm thay đổi `Method` được chuyển vào handler. **Không phân nhánh nghiệp vụ theo kiểu được ép tại phía caller**, bởi handler không có thông tin đó.

## 4. Quy tắc tương thích của kiểu trả về

Với các phương thức cùng tên và cùng danh sách tham số, `Proxy.newProxyInstance` yêu cầu kiểu trả về đáp ứng điều kiện:

1. Nếu **bất kỳ** kiểu trả về nào là primitive hoặc `void`, **tất cả phải đúng kiểu đó**. `int` và `long`, `void` và `Object` không thể gộp; không có tự boxing hay widening để hợp thức hóa proxy.
2. Nếu tất cả là **kiểu tham chiếu**, phải tồn tại một kiểu trả về có thể gán cho **tất cả** kiểu trả về còn lại. Chẳng hạn `Integer` là subtype của `Number` nên `Number code()` và `Integer code()` tương thích. Hai kiểu cuối `String` và `Integer` không có quan hệ subtype phù hợp nên không tương thích.
3. Khi không thỏa điều kiện, lời gọi **tạo proxy** ném `IllegalArgumentException`; không cần chờ đến lần gọi phương thức.

**Điểm tinh tế với covariant return:** `Number code()` và `Integer code()` cùng tên/tham số theo cách nhìn Java, nhưng có **descriptor JVM khác nhau vì descriptor chứa kiểu trả về**. Class proxy có thể sinh các phương thức/bridge cần thiết; trong Java 17, handler có thể thấy `Method` có kiểu trả về `Number` khi gọi qua interface rộng và `Integer` khi gọi qua interface hẹp. Vì vậy, quy tắc “interface đầu tiên thắng” không có nghĩa `method.getReturnType()` luôn giống nhau khi có covariant return. Kiểm tra hành vi cụ thể và luôn trả về đối tượng tương thích với các contract liên quan.

**Giá trị handler trả về:** proxy sẽ kiểm tra/ép sang kiểu trả về tương ứng. Trả sai kiểu tham chiếu gây `ClassCastException`; trả `null` cho kiểu trả về primitive có thể gây `NullPointerException` lúc unboxing. `void` thường cần handler trả về `null` (giá trị trả về của handler sẽ bị bỏ qua).

## 5. `throws` khác nhau: tập checked exception chung mới an toàn

Hai interface có thể cùng khai báo `read()` nhưng một interface `throws IOException`, interface kia `throws SQLException`. Việc **tạo proxy vẫn hợp lệ**, vì `throws` không phải kiểu trả về và không quyết định chữ ký runtime. Tuy nhiên, handler **không được tự do phát ra mọi checked exception từ interface đứng trước**.

Quy tắc: để checked exception đi qua proxy mà không bị bọc, kiểu ngoại lệ đó phải tương thích với ít nhất một exception được khai báo trong **mỗi** phương thức trùng chữ ký thuộc các interface liên quan. Nếu một interface khai báo `Exception` và interface khác khai báo `IOException`, `IOException` có thể đi qua; `SQLException` không thỏa interface thứ hai. Nếu một khai báo `IOException` và một khai báo `SQLException`, một `IOException` thông thường không thể đi thẳng qua hai contract.

Nếu handler ném checked exception không hợp lệ với toàn bộ hợp đồng, proxy bọc nó trong **`java.lang.reflect.UndeclaredThrowableException`**, là unchecked exception. Cần dùng `getUndeclaredThrowable()` hoặc `getCause()` để xem nguyên nhân.

`RuntimeException` và `Error` không chịu giới hạn khai báo `throws` giống checked exception; chúng có thể truyền ra ngoài theo cơ chế unchecked thông thường. Hãy phân biệt lỗi ném ra từ handler với lỗi tạo proxy (thường `IllegalArgumentException`).

## 6. Nhiều default method trùng tên: chọn triển khai một cách tường minh

Java 8 bổ sung **default method** có phần thân bên trong interface. Khi dynamic proxy triển khai hai interface cùng có `default String label()`, việc gọi `label()` **vẫn đi qua `InvocationHandler.invoke`**; proxy không tự động chọn nội dung của interface đầu hoặc thứ hai để chạy.

Từ Java 16, `InvocationHandler.invokeDefault(proxy, method, args...)` cho phép handler gọi một default method cụ thể. Có thể lấy `Method` từ interface mình muốn, ví dụ `Second.class.getMethod("label")`, rồi gọi `invokeDefault`. Đây là quyết định nghiệp vụ riêng với quy tắc `Method` nhận vào handler. Method để gọi phải là default method hợp lệ, có thể truy cập, được khai báo trong proxy interface hoặc được kế thừa mà vẫn có thể chọn theo quy tắc JVM. Khi một interface override default method trong tương lai, lời gọi `invokeDefault` đến implementation cũ có thể không còn hợp lệ.

Trường hợp interface thứ nhất khai báo một phương thức abstract và interface thứ hai khai báo default method cùng tên/tham số, cũng **không suy ra** proxy sẽ tự chạy default method. Handler cần dispatch rõ ràng.

## 7. Ví dụ đầy đủ: kiểu trả về, checked exception và default method (Java 17+)

Tạo file `ProxyEdgeCases.java`:

~~~java
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLException;

public class ProxyEdgeCases {
    public interface Wide { Number code(); }
    public interface Narrow { Integer code(); }
    public interface BadA { int amount(); }
    public interface BadB { long amount(); }
    public interface Disk { String read() throws IOException; }
    public interface Database { String read() throws SQLException; }
    public interface First {
        default String label() { return "first"; }
    }
    public interface Second {
        default String label() { return "second"; }
    }

    public static void main(String[] args) throws Throwable {
        Object covariant = Proxy.newProxyInstance(
                ProxyEdgeCases.class.getClassLoader(),
                new Class<?>[] { Wide.class, Narrow.class },
                (proxy, method, callArgs) -> {
                    System.out.println("Return token: "
                            + method.getReturnType().getSimpleName());
                    return 42;
                });
        System.out.println(((Wide) covariant).code());
        System.out.println(((Narrow) covariant).code());

        try {
            Proxy.newProxyInstance(
                    ProxyEdgeCases.class.getClassLoader(),
                    new Class<?>[] { BadA.class, BadB.class },
                    (proxy, method, callArgs) -> 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("Primitive conflict: IllegalArgumentException");
        }

        Object checked = Proxy.newProxyInstance(
                ProxyEdgeCases.class.getClassLoader(),
                new Class<?>[] { Disk.class, Database.class },
                (proxy, method, callArgs) -> { throw new IOException("failed"); });
        try {
            ((Disk) checked).read();
        } catch (UndeclaredThrowableException ex) {
            System.out.println("Wrapped checked: "
                    + ex.getUndeclaredThrowable().getClass().getSimpleName());
        }

        Object defaults = Proxy.newProxyInstance(
                ProxyEdgeCases.class.getClassLoader(),
                new Class<?>[] { First.class, Second.class },
                (proxy, method, callArgs) -> {
                    System.out.println("Default Method from: "
                            + method.getDeclaringClass().getSimpleName());
                    Method chosen = Second.class.getMethod("label");
                    return InvocationHandler.invokeDefault(proxy, chosen);
                });
        System.out.println(((First) defaults).label());
        System.out.println(((Second) defaults).label());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ProxyEdgeCases.java
java ProxyEdgeCases
~~~

Kết quả xác minh trên OpenJDK 21 với `javac --release 17`:

~~~text
Return token: Number
42
Return token: Integer
42
Primitive conflict: IllegalArgumentException
Wrapped checked: IOException
Default Method from: First
second
Default Method from: First
second
~~~

Giải thích:

- `Wide.code()` và `Narrow.code()` hợp lệ nhờ kiểu trả về hiệp biến. Hai lần gọi nhận `Method` với kiểu trả về tương ứng `Number` và `Integer`; handler trả `Integer(42)` phù hợp cả hai.
- Hai khai báo `amount()` khác kiểu trả về primitive (`int`/`long`) bị từ chối **ngay khi tạo proxy**.
- Handler ném `IOException` trong khi proxy cũng công khai `Database.read() throws SQLException` nên exception bị bọc dù lời gọi thực hiện qua `Disk`.
- Cả hai lần gọi `label()` handler đều nhận `Method` từ `First` vì `First` được đăng ký trước. Nhưng handler chọn **thực thi default implementation của `Second`**, nên kết quả đều là `second`.

Lưu ý kết quả trên là kết quả của **đúng ví dụ này**; sửa thứ tự interface, kiểu trả về, chữ ký, hoặc logic handler sẽ thay đổi kết quả.

## 8. Ba phương thức của `Object` có ưu tiên riêng

`equals(Object)`, `hashCode()` và `toString()` là ba public, non-final method của `Object` được dynamic proxy chuyển đến `InvocationHandler`. Nếu một proxy interface tình cờ khai báo lại chúng với chữ ký tương ứng, `Method` được truyền cho handler vẫn có `getDeclaringClass() == Object.class`, vì các phương thức này được coi là **ưu tiên trước mọi interface** khi chọn `Method`.

Không áp dụng suy luận này cho mọi method của `Object` (ví dụ `getClass()` là final, không được handler override theo cơ chế trên). Handler nên xử lý ba phương thức cơ bản rõ ràng để tránh kết quả `toString()` bất ngờ hoặc vòng lặp đệ quy khi log:

~~~java
// Fragment bên trong InvocationHandler.invoke(proxy, method, args)
if (method.getDeclaringClass() == Object.class) {
    return switch (method.getName()) {
        case "toString" -> "DynamicServiceProxy";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new UnsupportedOperationException(method.toString());
    };
}
~~~

Đây là **fragment**, không phải chương trình độc lập. Đặc biệt, tránh gọi `proxy.toString()` ngay trong nhánh xử lý `toString` của chính handler vì sẽ lại đi vào handler.

## 9. Generic, method erasure và giới hạn của `Method`

Tại runtime, dynamic proxy làm việc với `Method` và kiểu tham số/kiểu trả về đã được JVM biểu diễn. Ví dụ, tham số kiểu `List<String>` và `List<Integer>` có cùng raw type `List` sau erasure; `Method.getParameterTypes()` thông thường cho biết `List.class`, không kiểm chứng từng phần tử lúc chạy.

`Method.getGenericParameterTypes()` có thể đọc thông tin **khai báo generic** còn lưu trong class file, nhưng thông tin đó **không có nghĩa** lời gọi đến proxy được JVM tự kiểm tra sâu thành phần tử `String` hay `Integer`. Khi hai interface có khai báo generic khác nhau nhưng erasure trùng, không được định tuyến nghiệp vụ chỉ dựa trên kiểu generic mong đợi ở caller.

Một vài interface generic sau chuyên biệt hóa có thể xuất hiện **bridge method** do compiler sinh, với đặc điểm `Method.isBridge()` hoặc `isSynthetic()`. Khi chẩn đoán nên quan sát cả `method.getName()`, `getParameterTypes()`, `getReturnType()` và `getDeclaringClass()`, thay vì giả định rằng tên method là định danh duy nhất.

Đầu vào nhận từ bên ngoài vẫn phải được validate nội dung; dynamic proxy không thay thế cơ chế kiểm tra dữ liệu kiểu generic.

## 10. Thiết kế handler ổn định khi kết hợp nhiều interface

Để tránh nhầm lẫn và hành vi lệ thuộc thứ tự một cách vô tình:

1. **Rà soát interface trước khi tạo proxy:** lấy các method public kế thừa và khai báo, nhóm theo tên + danh sách tham số đã erasure; so sánh kiểu trả về, checked exceptions, default implementation và ý nghĩa nghiệp vụ.
2. **Ghi rõ thứ tự interface** truyền vào `Proxy.newProxyInstance`. Đừng lấy thứ tự tùy ý từ `HashSet` hoặc một collection không bảo đảm thứ tự; đây là đầu vào có ý nghĩa.
3. **Hợp nhất nghiệp vụ của method trùng chữ ký** nếu chúng phải dùng chung một hành vi. Nếu hai interface có hai ý nghĩa khác nhau nhưng cùng chữ ký, hãy đổi thiết kế (đổi tên/phân tách proxy) vì handler không thể biết kiểu tham chiếu của caller để phân biệt.
4. **Phân nhánh theo contract đã định nghĩa**, không mặc định dùng `method.getDeclaringClass()` làm “interface người gọi”. Khi có covariant returns hoặc bridge, kiểm tra thêm descriptor/kiểu trả về.
5. **Chọn default method tường minh** với `InvocationHandler.invokeDefault` nếu thật sự muốn gọi thân hàm mặc định.
6. **Chuẩn hóa xử lý exception và giá trị trả về** theo tất cả interface. Kiểm tra riêng `equals`, `hashCode`, `toString` và tránh gọi lại chính proxy một cách không kiểm soát.
7. **Bảo đảm class loader nhìn thấy tất cả interface**, interface là loại phù hợp để tạo proxy và khai báo không có xung đột return type; thay đổi thứ tự mảng có thể tạo một proxy class khác.

Khi cần hai implementation khác nhau cho cùng tên/tham số dựa trên một ngữ cảnh nghiệp vụ, truyền **ngữ cảnh tường minh** (tham số, strategy, adapter) hoặc tạo hai proxy/entry point riêng. Ép một proxy duy nhất qua interface khác nhau không chuyển được thông tin ấy vào `invoke`.

## 11. Bảng lỗi thường gặp

| Dấu hiệu | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Handler luôn thấy interface A dù gọi qua B | A đứng trước trong mảng interface và method trùng | Đọc lại thứ tự đăng ký; không suy đoán kiểu caller |
| `IllegalArgumentException` lúc tạo proxy | Return type xung đột hoặc vi phạm điều kiện tạo proxy | Kiểm tra phương thức trùng tên/tham số, return type và visibility |
| `UndeclaredThrowableException` khi gọi method | Checked exception từ handler không hợp lệ với tất cả `throws` | Xem `getUndeclaredThrowable()`, thống nhất hợp đồng lỗi |
| Default method không chạy | Mọi lời gọi proxy đi qua handler | Dùng `InvocationHandler.invokeDefault` với `Method` hợp lệ |
| `method.getReturnType()` khác nhau giữa hai lần gọi | Các method hiệp biến có descriptor return khác | Phân biệt trường hợp này với method cùng return type |
| `ClassCastException` hoặc `NullPointerException` sau `invoke` | Handler trả kết quả không tương thích hoặc `null` cho primitive | Trả đúng kiểu và kiểm tra nullability |
| Handler lặp vô hạn khi log | Handler lại gọi method trên chính proxy | Không gọi `proxy.toString()` từ nhánh `toString` |

## 12. Câu hỏi tự kiểm tra

1. Nếu `A` và `B` đều có `String name()`, proxy được tạo với `[A.class, B.class]`, rồi gọi qua biến kiểu `B`, `Method` thuộc đâu? **`A`** (hoặc interface cha thực sự khai báo method mà `A` kế thừa), vì A đứng trước.
2. Đảo thành `[B.class, A.class]` ảnh hưởng gì? **Có thể đổi `Method` đại diện** cho các method cùng descriptor; không thay đổi nội dung method của từng interface.
3. `Number value()` và `Integer value()` có tạo proxy cùng lúc được không? **Có**, vì `Integer` là subtype của `Number`. Nhưng đừng giả định `Method.getReturnType()` giống nhau ở mọi lời gọi.
4. `int value()` và `long value()` có tạo proxy cùng lúc được không? **Không**; primitive return phải đúng cùng kiểu.
5. Vì sao `throws IOException` và `throws SQLException` có thể dẫn tới `UndeclaredThrowableException`? Vì checked exception từ handler phải hợp lệ với **mọi contract** method trùng; `IOException` không nằm trong `throws SQLException`.
6. Để chạy body của `Second.default label()` khi handler nhận `Method` thuộc `First`, cần gì? **Lấy `Second.class.getMethod("label")` và gọi `InvocationHandler.invokeDefault(proxy, chosen)`** khi method có thể được chọn hợp lệ.
7. Có thể nhìn `method.getDeclaringClass()` để biết caller đã ép proxy sang A hay B không? **Không**. Class proxy không truyền thông tin kiểu tham chiếu của caller vào handler.

## Tài liệu tham khảo

- [Oracle JDK 17 — Proxy: Methods Duplicated in Multiple Proxy Interfaces](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html)
- [Oracle JDK 17 — InvocationHandler và invokeDefault](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html)

**Ghi nhớ:** Khi nhiều interface có method trùng tên/tham số, cần xét **thứ tự interface, kiểu trả về, checked exceptions và default methods** như bốn khía cạnh độc lập. `Method` nhận trong handler không phải thông tin về kiểu biến của caller. Thiết kế contract rõ ràng trước khi viết dispatch sẽ tránh phần lớn lỗi khó chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-013">Proxy.isProxyClass, getInvocationHandler và giới hạn phụ thuộc tên lớp proxy</a>

<details>
<summary>Xem chi tiết</summary>

# `Proxy.isProxyClass`, `Proxy.getInvocationHandler` và giới hạn của tên lớp proxy trong Java

## 1. Bài toán: nhận diện một JDK dynamic proxy đúng cách

Trong Java Reflection, `java.lang.reflect.Proxy` tạo **dynamic proxy** tại runtime: JVM sinh một lớp thực thi một hoặc nhiều interface và chuyển các lời gọi phương thức qua một đối tượng `InvocationHandler`. Cơ chế này thường xuất hiện ở adapter, logging, kiểm tra quyền, giao dịch, AOP hoặc client gọi dịch vụ.

Khi đang cầm một biến kiểu `Object`, có ba câu hỏi khác nhau:

1. **Đối tượng có thực sự thuộc lớp JDK dynamic proxy?** Dùng `Proxy.isProxyClass(candidate.getClass())` sau khi kiểm tra `candidate != null`.
2. **Đối tượng proxy đang sử dụng handler nào?** Dùng `Proxy.getInvocationHandler(candidate)`, với điều kiện đây là instance của lớp proxy do API JDK tạo.
3. **Đối tượng có cung cấp nghiệp vụ qua interface nào?** Kiểm tra `instanceof`, `Class.isInstance` hoặc ép về interface đã biết. Đây là kiểm tra hợp đồng nghiệp vụ, không đồng nghĩa với hai câu hỏi trên.

**Điểm mấu chốt:** đừng nhận diện proxy bằng chuỗi do `getClass().getName()` trả về. Tên lớp phát sinh là chi tiết triển khai, không phải giao diện API ổn định.

## 2. Hai API chính và điều kiện sử dụng

Các chữ ký trong `java.lang.reflect.Proxy`:

```java
public static boolean isProxyClass(Class<?> cl);
public static InvocationHandler getInvocationHandler(Object proxy)
        throws IllegalArgumentException;
```

| API | Đầu vào | Ý nghĩa khi thành công | Trường hợp cần lưu ý |
|---|---|---|---|
| `Proxy.isProxyClass(Class<?> cl)` | Đối tượng `Class` cần kiểm tra | `true` đúng khi `cl` là lớp proxy được sinh bởi cơ chế `java.lang.reflect.Proxy` | Không có nghĩa “bất kỳ lớp nào kế thừa `Proxy`”; với lớp thường kết quả là `false` |
| `Proxy.getInvocationHandler(Object proxy)` | **Instance** của JDK dynamic proxy | Trả về chính tham chiếu `InvocationHandler` đã gắn với proxy | Ném `IllegalArgumentException` nếu đối số không phải instance proxy hợp lệ |
| `Proxy.newProxyInstance(loader, interfaces, handler)` | Loader, mảng interface và handler | Tạo instance proxy triển khai những interface yêu cầu | Có thể thất bại nếu tập interface hoặc khả năng nhìn thấy interface không hợp lệ |

Chú ý sự khác biệt về đối số: `isProxyClass` nhận **`Class<?>`** (thường lấy qua `obj.getClass()`), còn `getInvocationHandler` nhận **instance `Object`**. Không truyền `Greeting.class` cho `getInvocationHandler`, và không truyền trực tiếp `greeting` cho `isProxyClass`.

Theo hợp đồng API, `isProxyClass` chỉ nhận diện lớp được tạo bằng cơ chế `Proxy.newProxyInstance` hoặc `Proxy.getProxyClass` (phương thức thứ hai đã bị đánh dấu deprecated từ Java 9). Nó không kiểm tra tên lớp theo mẫu `$Proxy...`, cũng không coi `Proxy.class` hoặc lớp tự viết `extends Proxy` là lớp proxy được sinh động.

`getInvocationHandler` cung cấp **handler đang gắn với proxy**, không tự động trả về đối tượng đích (target). Nếu handler đóng vai trò delegate tới một service thật, muốn biết target cần API do chính handler/framework cung cấp; JDK không định nghĩa một phương thức “unwrap target” chung.

## 3. Đường đi của một lời gọi phương thức

Luồng cơ bản:

```text
Mã gọi qua interface
        |
        v
proxy.greet("Lan")
        |
        v
Lớp proxy do JDK sinh tại runtime
        |
        v
InvocationHandler.invoke(proxy, method, args)
        |
        +-- log/kiểm tra quyền/chuyển tiếp tới target
        |
        v
Giá trị trả về hoặc exception đến phía gọi
```

Để tạo JDK dynamic proxy, trước hết phải có ít nhất một interface phù hợp và một handler. Ví dụ:

```java
Greeting proxy = (Greeting) Proxy.newProxyInstance(
        Greeting.class.getClassLoader(),
        new Class<?>[]{Greeting.class},
        handler
);
```

Đây là **mảnh code minh họa**: `Greeting` và `handler` được định nghĩa trong ví dụ chạy hoàn chỉnh bên dưới.

`Proxy.newProxyInstance` tạo proxy triển khai `Greeting` và gắn `handler`. Khi gọi `proxy.greet(...)`, đối tượng `Method` và đối số được chuyển tới `InvocationHandler.invoke(...)`. Điều này khác với Reflection thông thường: việc lấy `Class` hoặc lấy handler **không tự chạy phương thức nghiệp vụ**.

Ba phương thức `equals(Object)`, `hashCode()` và `toString()` của `Object` cũng được chuyển qua handler khi gọi trên JDK proxy; người viết handler nên định nghĩa hành vi phù hợp. Trái lại, `getClass()` là phương thức `final` của `Object` và không được dispatch qua handler.

## 4. Ví dụ Java 17+ chạy độc lập: kiểm tra lớp và lấy handler

Tạo file `ProxyInspectionDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class ProxyInspectionDemo {
    interface Greeting {
        String greet(String name);
    }

    static class ManualProxy extends Proxy {
        ManualProxy(InvocationHandler handler) {
            super(handler);
        }
    }

    public static void main(String[] args) {
        InvocationHandler handler = (proxy, method, parameters) -> {
            switch (method.getName()) {
                case "greet":
                    return "Xin chào, " + parameters[0];
                case "toString":
                    return "GreetingProxy";
                case "equals":
                    return proxy == parameters[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        };

        Greeting greeting = (Greeting) Proxy.newProxyInstance(
                Greeting.class.getClassLoader(),
                new Class<?>[]{Greeting.class},
                handler
        );

        System.out.println("isProxyClass: " + Proxy.isProxyClass(greeting.getClass()));
        System.out.println("same handler: " + (Proxy.getInvocationHandler(greeting) == handler));
        System.out.println(greeting.greet("Lan"));
        System.out.println("toString: " + greeting);
        System.out.println("String.class isProxyClass: " + Proxy.isProxyClass(String.class));

        ManualProxy manual = new ManualProxy(handler);
        System.out.println("manual instanceof Proxy: " + (manual instanceof Proxy));
        System.out.println("manual isProxyClass: " + Proxy.isProxyClass(manual.getClass()));

        try {
            Proxy.getInvocationHandler(manual);
        } catch (IllegalArgumentException ex) {
            System.out.println("manual handler: IllegalArgumentException");
        }

        try {
            Proxy.getInvocationHandler("ordinary");
        } catch (IllegalArgumentException ex) {
            System.out.println("ordinary handler: IllegalArgumentException");
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa file:

```bash
javac ProxyInspectionDemo.java
java ProxyInspectionDemo
```

Stdout:

```text
isProxyClass: true
same handler: true
Xin chào, Lan
toString: GreetingProxy
String.class isProxyClass: false
manual instanceof Proxy: true
manual isProxyClass: false
manual handler: IllegalArgumentException
ordinary handler: IllegalArgumentException
```

**Giải thích các điểm dễ nhầm:**

- `greeting.getClass()` là lớp proxy sinh bởi JDK, nên `isProxyClass` trả `true`. `Proxy.getInvocationHandler(greeting) == handler` trả `true` vì trả đúng tham chiếu handler đã cung cấp.
- `Greeting` là interface lồng trong lớp ví dụ; chương trình dùng loader có thể nhìn thấy interface đó. Không cần biết tên class mà JVM tạo ra.
- Lời gọi `greeting.greet("Lan")` được xử lý ở nhánh `"greet"` của handler. Kết quả `"Xin chào, Lan"` là giá trị handler trả về.
- `toString()` cũng qua handler. `Proxy.isProxyClass(String.class)` trả `false` vì `String` là lớp thường.
- `ManualProxy` **tự kế thừa** `Proxy` và dùng constructor `protected Proxy(InvocationHandler)`, nhưng không phải lớp sinh bởi Proxy API. Vì vậy `manual instanceof Proxy` là `true`, còn `isProxyClass(manual.getClass())` là `false`.
- `getInvocationHandler(manual)` và `getInvocationHandler("ordinary")` đều ném `IllegalArgumentException`. Việc một object kế thừa `Proxy` hoặc có trường handler không đủ để thỏa điều kiện API.

Đoạn xử lý `equals/hashCode/toString` trong ví dụ nhằm minh họa việc các lời gọi đó cũng qua handler. Với hệ thống thực, phải chọn chính sách `equals` và `hashCode` nhất quán, đặc biệt nếu proxy được dùng làm khóa trong `HashMap`/`HashSet`.

## 5. Vì sao `instanceof Proxy` và tên lớp không thay thế `isProxyClass`?

Xét ba cách kiểm tra:

```java
boolean a = candidate instanceof Proxy;
boolean b = candidate != null
        && Proxy.isProxyClass(candidate.getClass());
boolean c = candidate != null
        && candidate.getClass().getName().contains("$Proxy");
```

Đây là **mảnh code minh họa** với biến `candidate` kiểu `Object` đã có; không phải chương trình chạy độc lập.

- `a` chỉ cho biết đối tượng là instance của `Proxy` hoặc một lớp kế thừa `Proxy`. Ví dụ `ManualProxy` ở trên làm cho `a` đúng nhưng `b` sai.
- `b` là kiểm tra theo hợp đồng của JDK cho một **instance lớp proxy được sinh động**. Đây là lựa chọn phù hợp khi thực sự cần biết một object có phải JDK proxy.
- `c` là heuristic theo tên; có thể sai dương với lớp người dùng tự đặt tên chứa `$Proxy`, hoặc sai âm khi cơ chế đặt tên và package thay đổi. Đừng dùng trong code điều khiển nghiệp vụ hay kiểm tra bảo mật.

Một proxy vẫn có thể triển khai rất nhiều interface; `isProxyClass` **không nói proxy triển khai interface nào**. Nếu cần đảm bảo proxy dùng như `Greeting`, kiểm tra `candidate instanceof Greeting` hoặc `Greeting.class.isInstance(candidate)`. Ngược lại, một implementation bình thường của `Greeting` không tự trở thành dynamic proxy chỉ vì thực thi interface đó.

Nhận diện theo lớp và nhận diện theo năng lực phải phục vụ hai mục đích khác nhau:

| Bạn cần biết | Nên dùng |
|---|---|
| Đối tượng có đúng là JDK dynamic proxy? | `Proxy.isProxyClass(obj.getClass())`, sau khi kiểm tra null |
| Lấy JDK `InvocationHandler` đang gắn với instance | `Proxy.getInvocationHandler(obj)`, sau khi đã xác minh proxy |
| Đối tượng có thể được dùng qua interface `Service`? | `obj instanceof Service` hoặc `Service.class.isInstance(obj)` |
| Handler có chuyển tiếp tới target nào? | API/hợp đồng của handler hoặc framework cụ thể |
| Đây có phải proxy do CGLIB/Byte Buddy/Spring cơ chế khác tạo? | API phát hiện proxy của chính công cụ/framework tương ứng |

## 6. Giới hạn khi dựa vào tên lớp proxy

Ở một số lần chạy, `proxy.getClass().getName()` có thể trông giống `jdk.proxy1.$Proxy0` hoặc một tên khác chứa `$Proxy`. Đây **chỉ là ví dụ về tên có thể quan sát được**, không phải quy tắc đặt tên được JDK hứa bảo toàn. Các điều kiện ảnh hưởng đến lớp được tạo gồm:

1. **Phiên bản JDK, implementation và môi trường module:** cách tổ chức package/module cho proxy có thể thay đổi. Với interface không phải `public`, quy tắc package/module khác với trường hợp toàn bộ interface đều public.
2. **Class loader và tập interface:** proxy được định nghĩa trong không gian lớp phù hợp với loader và các interface. Một tên lớp khi tách khỏi class loader không đủ xác định runtime identity.
3. **Thứ tự và tập interface:** proxy tạo cho các danh sách interface khác nhau có thể là các `Class` khác nhau; thứ tự interface cũng có ý nghĩa đối với bộ nhớ cache và một số quy tắc chọn `Method`.
4. **Caching và thứ tự tạo proxy:** thời điểm/tên được cấp phát là chi tiết triển khai. Đừng giả định mọi lần tạo đều có hậu tố tăng dần hoặc proxy thứ nhất luôn là `$Proxy0`.

Do đó, những đoạn mã dưới đây không đáng tin cậy:

```java
// KHÔNG NÊN: dựa vào package/tên lớp phát sinh.
if (obj.getClass().getName().startsWith("com.sun.proxy.$Proxy")) {
    // ...
}

// KHÔNG NÊN: tự suy ra loại proxy từ hậu tố số.
String generatedName = obj.getClass().getSimpleName();
boolean guessed = generatedName.matches("\\$Proxy\\d+");
```

Đây là các **mảnh mã phản ví dụ**; biến `obj` phải được định nghĩa trong ngữ cảnh bao quanh.

Không đưa tên proxy sinh ra vào file cấu hình, định dạng dữ liệu lưu trữ, cache key hay điều kiện phân quyền. Nếu cần biết hợp đồng, lưu **tên interface hoặc định danh nghiệp vụ ổn định**. Nếu cần lấy handler, giữ API công khai của `Proxy`. Nếu cần log để chẩn đoán, có thể ghi tên lớp hiện tại, nhưng log đó là thông tin quan sát, không phải chuẩn tương thích.

### Class identity vẫn phụ thuộc class loader

Trong JVM, identity của một lớp được xác định bởi **binary name và defining class loader**. Hai class loader độc lập có thể định nghĩa hai lớp cùng binary name nhưng có identity khác nhau. Kết quả là so sánh `Class<?>`, phép ép kiểu và khả năng truy cập interface có thể khác ngay cả khi chuỗi `getName()` bằng nhau.

Không dùng `Class.forName("jdk.proxy1.$Proxy0")` hoặc tên sinh ngẫu nhiên đã thấy ở lần chạy trước để khôi phục proxy. Thay vào đó, giữ đối tượng hoặc interface cần thiết, và tạo proxy mới qua `Proxy.newProxyInstance` khi đúng yêu cầu.

## 7. Mẫu kiểm tra an toàn trước khi lấy handler

Nếu đầu vào có thể là object bình thường, có thể viết một utility nhỏ:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;

public final class ProxyHandlers {
    private ProxyHandlers() {
    }

    public static Optional<InvocationHandler> jdkHandler(Object candidate) {
        if (candidate == null || !Proxy.isProxyClass(candidate.getClass())) {
            return Optional.empty();
        }
        return Optional.of(Proxy.getInvocationHandler(candidate));
    }
}
```

Lưu thành `ProxyHandlers.java` và biên dịch:

```bash
javac ProxyHandlers.java
```

Phương thức `jdkHandler` không gọi vào handler hay tạo instance mới; nó chỉ trả về metadata đối tượng đang có. `Optional.empty()` cho biết đầu vào null hoặc không phải JDK proxy, không có nghĩa mọi object không phải JDK proxy đều không dùng cơ chế AOP/proxy nào khác.

Trường hợp ứng dụng **biết chắc** đầu vào là JDK proxy, có thể gọi trực tiếp:

```java
InvocationHandler handler = Proxy.getInvocationHandler(proxyObject);
```

Đây là **mảnh code minh họa**; `proxyObject` phải là instance JDK proxy hợp lệ trong ngữ cảnh. Với đầu vào từ bên ngoài, nên kiểm tra trước để tránh `IllegalArgumentException`. Không nên bắt mọi exception rồi im lặng coi mọi object là proxy.

Cũng cần phân biệt *lấy handler* với *dùng handler*. `getInvocationHandler` trả một đối tượng có thể chứa state, phụ thuộc framework, trạng thái kết nối hoặc dữ liệu ủy quyền. Không tùy tiện ép handler về kiểu nội bộ hoặc gọi trực tiếp `invoke` bên ngoài hợp đồng của framework.

## 8. Dùng trong framework, logging và kiểm tra lỗi

**Phát hiện một JDK proxy cho mục đích chẩn đoán:**

```java
if (service != null && Proxy.isProxyClass(service.getClass())) {
    InvocationHandler handler = Proxy.getInvocationHandler(service);
    System.out.println("JDK handler type: " + handler.getClass().getName());
}
```

Đây là **mảnh code minh họa**, giả định biến `service` đã có. Việc log *kiểu của handler* thường ổn hơn việc hard-code tên lớp proxy sinh ra. Không tự động log toàn bộ `toString()` của handler vì nó có thể bao gồm thông tin nội bộ.

**Nếu dùng Spring AOP hay thư viện proxy khác:** JDK dynamic proxy chỉ là một lựa chọn. Các proxy dựa trên subclass hoặc cơ chế bytecode khác có thể không được `Proxy.isProxyClass` nhận diện dù vẫn hoạt động như proxy. Khi cần phân biệt proxy framework, dùng API framework hỗ trợ, thay vì xây thuật toán đoán từ tên lớp hoặc từ `Proxy.isProxyClass` đơn lẻ.

**Khi debug sự cố:**

- `isProxyClass == false`: trước hết xác nhận đó thực sự là JDK dynamic proxy hay chỉ là lớp thường/subclass proxy khác. Đừng kết luận “không có proxy”.
- `getInvocationHandler` ném `IllegalArgumentException`: kiểm tra đúng object instance, không truyền target, `Class<?>`, `Proxy.class` hoặc một subclass viết tay.
- `ClassCastException` khi ép về interface: proxy có thể không triển khai interface đó, hoặc interface có identity khác do class loader. Kiểm tra `instanceof` và loader tương ứng.
- `InvocationHandler.invoke` trả sai kiểu hoặc `null` cho method trả primitive: phương thức proxy có thể phát sinh `ClassCastException` hoặc `NullPointerException`. Đó là lỗi hợp đồng giá trị trả về của handler, không phải lỗi nhận diện tên proxy.
- `UndeclaredThrowableException`: có thể xuất hiện khi handler ném checked exception không được khai báo tương thích với phương thức interface; cần xem exception gốc.

## 9. Các lưu ý về thiết kế và bảo trì

**Ưu tiên lập trình qua interface.** Tạo proxy và truyền nó như `Greeting`, `Service` hoặc interface nghiệp vụ. Không ép về lớp có tên `$ProxyN`, không tạo đối tượng bằng constructor của proxy sinh ra và không dựa vào thứ tự class được tạo.

**Không dùng handler như API nghiệp vụ.** Handler là cấu phần hạ tầng hỗ trợ dispatch. Framework có thể thay đổi implementation handler, thêm nhiều lớp delegate hoặc đổi chiến lược proxy. Nếu bạn cần truy cập mục tiêu, định nghĩa một giao diện hoặc sử dụng API chính thức của framework.

**Không đồng nhất nhiều loại proxy.** JDK proxy dựa trên interface; subclass-based proxy và proxy do instrumentation tạo có thể cần cách nhận diện khác. Hai phương thức của `java.lang.reflect.Proxy` chỉ có hợp đồng đối với JDK dynamic proxy.

**Kiểm tra cả logic và tương thích.** Test nên dựa vào kết quả gọi interface, `isProxyClass` và handler identity khi cần, thay vì assert tên `$Proxy0`. Các test phụ thuộc tên lớp thường thất bại khi cập nhật JDK hoặc thay đổi tổ chức class loader/module.

**Không dùng phát hiện proxy làm hàng rào an ninh.** `isProxyClass` cho biết nguồn gốc tạo lớp theo cơ chế JDK, không chứng minh handler đáng tin, target đã được xác thực hay người gọi có quyền. Việc kiểm tra input và phân quyền phải dựa trên chính sách bảo mật ứng dụng.

## 10. Câu hỏi tự kiểm tra

1. Vì sao `obj instanceof Proxy` có thể đúng trong khi `Proxy.isProxyClass(obj.getClass())` sai? Vì lớp tự viết có thể `extends Proxy` nhưng không được JDK Proxy API sinh ra.
2. `Proxy.getInvocationHandler(obj)` có tự động trả target thật không? Không; nó trả handler đang gắn với proxy.
3. Có thể đoán một đối tượng là JDK proxy vì class name chứa `$Proxy` không? Không; đó là heuristic dựa trên chi tiết triển khai.
4. `isProxyClass` có khẳng định đối tượng triển khai `Runnable` hay một interface nghiệp vụ cụ thể không? Không; kiểm tra `instanceof Runnable` hoặc `Runnable.class.isInstance(obj)` riêng.
5. Nếu một thư viện chuyển từ JDK proxy sang subclass-based proxy, logic dựa trên `Proxy.isProxyClass` sẽ ra sao? Nó có thể trả `false`; cần xem hợp đồng của thư viện, thay vì giả định framework nào cũng dùng cùng một cơ chế.

## 11. Ghi nhớ

- `Proxy.isProxyClass(Class<?>)` kiểm tra **chính xác lớp JDK dynamic proxy được sinh qua Proxy API**.
- `Proxy.getInvocationHandler(Object)` lấy **handler đã gắn với instance JDK proxy**, và có thể ném `IllegalArgumentException` khi truyền object khác.
- `instanceof Proxy` rộng hơn điều kiện của `isProxyClass`; tên lớp `$Proxy...` là chi tiết triển khai.
- Interface và class loader mới là những điều cần xét khi sử dụng proxy; tên lớp phát sinh không phải khóa định danh ổn định.
- Dùng API JDK cho JDK dynamic proxy, API framework cho các dạng proxy khác và interface nghiệp vụ cho hành vi ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-proxies-014">Proxy interface khác cơ chế tạo lớp con bằng thư viện sinh bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# Dynamic proxy qua interface và proxy bằng lớp con sinh bytecode trong Java

Trong Java Reflection, hai kỹ thuật thường được gọi chung là **proxy** vì cùng đặt một lớp trung gian trước hành vi thật để ghi log, kiểm tra quyền, đo thời gian, giao dịch hoặc gọi từ xa. Tuy nhiên, **JDK dynamic proxy** và **proxy dựa trên lớp con (subclass proxy)** có cấu trúc kiểu, cách chặn lời gọi và giới hạn hoàn toàn khác nhau.

Điểm dễ nhầm nhất: **JDK dynamic proxy cũng tạo ra bytecode/lớp ở runtime**. Sự khác biệt chính không phải “một bên sinh bytecode, một bên không”, mà là **lớp proxy được sinh ra implements các interface** hay **extends một lớp cụ thể và ghi đè các method có thể override**.

## 1. Hai cơ chế ở cấp độ kiểu và lời gọi

### JDK dynamic proxy: đứng trước interface

Cho interface `Greeting` và implementation `GreetingImpl`:

```text
Client giữ tham chiếu Greeting
          |
          v
Đối tượng proxy (lớp JVM tạo, extends java.lang.reflect.Proxy)
          | implements Greeting
          v
Phương thức giao diện trên proxy
          |
          v
InvocationHandler.invoke(proxy, method, args)
          |
          +--> gọi GreetingImpl thật, hoặc thực hiện hành vi khác
```

Khi gọi `greeting.say("An")`, lời gọi đi vào method do lớp proxy tạo rồi chuyển đến `InvocationHandler`. Handler **có thể** ủy quyền đến một target, trả kết quả giả lập, gọi qua mạng hoặc ném exception. JDK Proxy không bắt buộc có một target instance.

Ví dụ khởi tạo:

```java
Greeting proxy = (Greeting) Proxy.newProxyInstance(
        Greeting.class.getClassLoader(),
        new Class<?>[] {Greeting.class},
        handler);
```

Đây là **đoạn minh họa**: giả sử `Greeting` và `handler` đã được định nghĩa. Đối tượng trả về thỏa `proxy instanceof Greeting`, nhưng thông thường **không phải** instance của `GreetingImpl` dù handler đang chuyển lời gọi đến `GreetingImpl`. Lớp proxy kế thừa `java.lang.reflect.Proxy`, không kế thừa implementation `GreetingImpl`. Một lớp JDK proxy có thể implements nhiều interface hợp lệ.

### Subclass proxy: đứng trước một lớp cụ thể

Với lớp `Service` có method `work()` có thể ghi đè:

```text
Client giữ tham chiếu Service
          |
          v
Đối tượng thuộc lớp con do thư viện sinh ra
          | extends Service
          v
Method work() được override trong lớp con
          |
          +--> interceptor trước lời gọi
          +--> gọi super.work() / chuyển đến target (tùy cơ chế)
          +--> interceptor sau lời gọi
```

Các thư viện như **Byte Buddy** hoặc **CGLIB** có thể tạo bytecode và định nghĩa một lớp con ở runtime, từ đó ghi đè method hợp lệ để cài logic trung gian. Đối tượng này có thể dùng như chính lớp cơ sở: `subclassProxy instanceof Service` là `true`. Không cần `Service` khai báo interface.

**Chú ý:** một thư viện sinh bytecode có thể hỗ trợ nhiều kiểu tạo lớp khác nhau; ở đây “subclass proxy” chỉ cơ chế cụ thể là sinh lớp `extends Service` và override method. Cách gọi target, lưu state và tạo instance tùy framework/cấu hình.

## 2. JDK Proxy chuyển lời gọi qua InvocationHandler như thế nào?

API trọng tâm nằm trong `java.lang.reflect.Proxy` và `java.lang.reflect.InvocationHandler`:

- `Proxy.newProxyInstance(loader, interfaces, handler)`: tạo instance của một lớp proxy hỗ trợ các interface được yêu cầu. Interface phải thỏa ràng buộc visibility, class loader và module của JVM.
- `InvocationHandler.invoke(Object proxy, Method method, Object[] args)`: nhận proxy đang được gọi, metadata `Method` và đối số. Với method không tham số, `args` có thể là `null`.
- `method.invoke(target, args)`: một cách **tùy chọn** để ủy quyền vào implementation; khi method thật ném exception, Reflection có thể bọc trong `InvocationTargetException`, cần xử lý cause.
- `Proxy.isProxyClass(type)` và `Proxy.getInvocationHandler(proxy)`: giúp kiểm tra lớp JDK proxy và truy xuất handler khi thích hợp.

Không phải lời gọi Java nào trên đối tượng proxy cũng vào handler. Các interface instance method sẽ được chuyển qua handler; `equals(Object)`, `hashCode()` và `toString()` khai báo từ `Object` cũng được chuyển đến handler. Ngược lại, `getClass()` là method `final` của `Object` và không bị handler chặn. Static method của interface không được gọi bằng dispatch của instance proxy.

**Default method của interface:** invocation qua proxy vẫn được chuyển tới handler; handler phải quyết định xử lý, chẳng hạn ủy quyền cho target có implementation hoặc dùng `InvocationHandler.invokeDefault(...)` (Java 16+), nếu phù hợp. Đừng mặc định mọi default method sẽ tự chạy mà không qua handler.

**Kiểu của proxy:** caller nên giữ proxy bằng biến interface. Nếu cố cast proxy JDK sang lớp implementation cụ thể, có thể gặp `ClassCastException`. Method chỉ có ở implementation và **không nằm trong interface được proxy implements** cũng không thể gọi qua API interface đó.

## 3. Subclass proxy chặn lời gọi bằng override như thế nào?

Một subclass proxy có thể được hình dung bằng mã nguồn do người viết tự tạo sau; thư viện thật sinh cấu trúc tương đương bằng bytecode thay vì cần viết sẵn lớp này:

```java
class Service {
    String work() {
        return "done";
    }
}

class ServiceProxyLike extends Service {
    @Override
    String work() {
        System.out.println("before");
        String result = super.work();
        System.out.println("after");
        return result;
    }
}
```

Đây là **đoạn minh họa cấu trúc**, không phải bằng chứng rằng thư viện nào cũng gọi `super.work()`. Một số proxy gọi method gốc trên chính instance lớp con, trong khi proxy của framework khác có thể chuyển tiếp đến target instance riêng hoặc dùng interceptor chain. Cấu trúc bytecode và đường đi thực tế phải kiểm tra theo framework đang dùng.

Giới hạn cốt lõi xuất phát từ luật kế thừa/override của Java:

- **`final class`** không thể được subclass. Record mặc nhiên là `final`; sealed class cũng hạn chế danh sách lớp được phép kế thừa.
- **`final instance method`** không thể bị override. Một subclass proxy thuần túy không thể chèn interceptor thông qua override của method ấy.
- **`private method`** không được override; **`static method`** được resolve theo lớp, không tham gia virtual dispatch/override. Constructor cũng không phải method được override.
- Method `public`/`protected` và method package-private có thể override **khi các quy tắc truy cập và package cho phép**. Module encapsulation, class loader và cách định nghĩa lớp có thể đặt thêm giới hạn.
- Việc tạo instance cần một chiến lược hợp lệ: constructor có thể được gọi hoặc framework có thể dùng kỹ thuật instantiation riêng. Vì vậy **không được suy ra** từ chữ “subclass proxy” rằng constructor target chạy đúng một lần theo một cách cố định.

Một method `final` vẫn có thể được **gọi** trên subclass instance nếu được kế thừa và truy cập hợp lệ; điều thiếu là điểm override để chèn logic. Kết quả thực tế còn phụ thuộc cách framework quản lý state và target.

## 4. So sánh trực diện

| Tiêu chí | JDK dynamic proxy | Proxy là lớp con sinh bytecode |
|---|---|---|
| Kiểu proxy | Lớp sinh ra extends `Proxy`, implements interface | Lớp sinh ra extends lớp đích |
| Điều kiện chính | Cần một hoặc nhiều interface phù hợp | Cần lớp đích có thể kế thừa và method có thể override |
| `instanceof` | Đúng với interface được proxy implements | Đúng với lớp cơ sở |
| Điểm chặn | Method trong lớp proxy chuyển tới `InvocationHandler.invoke` | Override method đi qua interceptor/logic trong lớp con |
| Method chỉ có ở concrete class | Không trở thành API của proxy nếu không nằm trên interface | Có thể chặn nếu được kế thừa và override hợp lệ |
| Method `final` của concrete class | Không áp dụng để chọn method qua interface; không proxy concrete class | Không thể chặn bằng override |
| `private`/`static` method của target | Không được gọi qua interface instance dispatch | Không thể override/chặn bằng override |
| Multiple interfaces | Có thể implements nhiều interface | Lớp con chỉ có một superclass nhưng có thể implements interface |
| Target instance thật | Không bắt buộc; handler quyết định | Phụ thuộc framework: có thể gọi `super` hoặc target riêng |
| Cách tạo proxy | API JDK sẵn có | Thường dùng thư viện như Byte Buddy/CGLIB |
| Kiểu sử dụng thích hợp | Contract dựa trên interface | Cần duy trì kiểu concrete class hoặc class không có interface |

Cả hai đều chịu quy tắc về type identity, class loader, module và visibility của JVM. Cả hai cũng có chi phí runtime nhất định; không nên khẳng định cơ chế nào **luôn** nhanh hơn nếu chưa đo với workload và framework thực tế.

## 5. Chương trình Java 17+ minh họa cả hai đường đi

Ví dụ sau chạy chỉ với JDK. Phần JDK Proxy dùng proxy thật; phần lớp con được **viết tay để mô phỏng chính xác ý tưởng override**, tránh yêu cầu tải thêm thư viện Byte Buddy/CGLIB. Ví dụ không giả vờ rằng lớp viết tay là bytecode do thư viện tự sinh.

Lưu thành **`ProxyVsSubclassDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ProxyVsSubclassDemo {
    interface Greeting {
        String say(String name);
    }

    static class GreetingImpl implements Greeting {
        @Override
        public String say(String name) {
            return "Hello, " + name;
        }

        public String extra() {
            return "Only available on concrete class";
        }
    }

    static class Service {
        String work() {
            return "work done";
        }

        final String fixed() {
            return "fixed";
        }
    }

    // Minh họa cấu trúc lớp mà thư viện sinh bytecode có thể tạo.
    static class ServiceSubclassDemo extends Service {
        @Override
        String work() {
            System.out.println("[subclass] before");
            String result = super.work();
            System.out.println("[subclass] after");
            return result;
        }
    }

    public static void main(String[] args) {
        GreetingImpl target = new GreetingImpl();

        Greeting interfaceProxy = (Greeting) Proxy.newProxyInstance(
                Greeting.class.getClassLoader(),
                new Class<?>[] {Greeting.class},
                (proxy, method, arguments) -> {
                    System.out.println("[interface] " + method.getName());
                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

        System.out.println(interfaceProxy.say("An"));
        System.out.println("proxy instanceof Greeting = "
                + (interfaceProxy instanceof Greeting));
        System.out.println("proxy instanceof GreetingImpl = "
                + (interfaceProxy instanceof GreetingImpl));
        System.out.println("proxy superclass is Proxy = "
                + (interfaceProxy.getClass().getSuperclass() == Proxy.class));

        Service subclassProxy = new ServiceSubclassDemo();
        System.out.println(subclassProxy.work());
        System.out.println(subclassProxy.fixed());
        System.out.println("subclass instanceof Service = "
                + (subclassProxy instanceof Service));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -encoding UTF-8 ProxyVsSubclassDemo.java
java ProxyVsSubclassDemo
```

**Stdout:**

```text
[interface] say
Hello, An
proxy instanceof Greeting = true
proxy instanceof GreetingImpl = false
proxy superclass is Proxy = true
[subclass] before
[subclass] after
work done
fixed
subclass instanceof Service = true
```

**Giải thích kết quả:**

1. Khi gọi `interfaceProxy.say("An")`, method của lớp proxy JDK gọi handler. Handler in `[interface] say`, rồi dùng Reflection gọi `GreetingImpl.say`.
2. Proxy implements `Greeting` nhưng không extends `GreetingImpl`; do đó hai dòng `instanceof` lần lượt là `true` và `false`. Superclass trực tiếp là `Proxy`.
3. `ServiceSubclassDemo` là một lớp con của `Service`. Method `work()` được override, in log trước/sau và gọi `super.work()`; đây là **cơ chế override** mà thư viện sinh subclass có thể tạo tự động.
4. `fixed()` là `final` và không được override trong `ServiceSubclassDemo`. Nó vẫn trả `fixed`, không đi qua log `[subclass]`.
5. `subclassProxy` vẫn là `Service` theo `instanceof` và có thể được truyền vào chỗ nhận `Service`, khác với JDK interface proxy khi code cần concrete type `GreetingImpl`.

Lưu ý: trong handler, `method.invoke(target, arguments)` có thể gây `InvocationTargetException`; ví dụ đã bóc cause để giữ exception thật của method. Nếu thay `target` bằng chính `proxy` rồi gọi lại cùng method, handler có thể gọi đệ quy vô hạn.

## 6. Những khác biệt có tác động khi dùng framework

### Không có interface, hoặc caller cần concrete type

Nếu API nhận `Service` như concrete class và cần thay đối tượng bằng proxy vẫn là `Service`, một JDK proxy chỉ implements `ServiceInterface` (nếu có) thường không thể cast thành `Service`. Proxy theo subclass có thể phù hợp **nếu** `Service` cho phép kế thừa và có các method cần chặn có thể override.

Ngược lại, nếu API đã thống nhất dựa trên interface, JDK proxy cung cấp hợp đồng rõ ràng và dùng trực tiếp API chuẩn của Java. Một implementation với method bổ sung ngoài interface sẽ không tự được thêm vào contract proxy.

### Self-invocation không thể kết luận chỉ từ chữ “proxy”

Hãy phân biệt **virtual dispatch của Java** với **cách framework gọi target**:

- Nếu một instance của lớp con thực sự gọi method được override thông qua `this.work()`, Java có thể dispatch vào override của lớp con.
- Nếu một framework đặt proxy bên ngoài một **target instance riêng**, lời gọi nội bộ `this.work()` trên target có thể đi thẳng vào target và **bỏ qua proxy**.
- Vì vậy với Spring AOP hay các framework tương tự, cần xem **thiết kế proxy, chuỗi interceptor và đối tượng nào thực thi lời gọi**, chứ không mặc định subclass proxy sẽ xử lý mọi self-invocation.

### Lỗi và giới hạn thường gặp

| Hiện tượng | Cách giải thích hoặc kiểm tra |
|---|---|
| `ClassCastException` khi cast JDK proxy sang implementation | Proxy chỉ nhận danh tính qua interface được implements, không kế thừa implementation |
| Không chặn được method `final` | Subclass không thể override; cân nhắc interface proxy, đổi API/method hoặc một kỹ thuật instrumentation khác nếu được thiết kế |
| Không tạo được subclass | Kiểm tra class `final`/record/sealed, constructor phù hợp, quyền truy cập, package và module |
| JDK proxy gặp method có checked exception | Nếu handler ném checked exception ngoài danh sách `throws` được method giao diện cho phép, caller có thể nhận `UndeclaredThrowableException` |
| Handler bất ngờ chạy khi log proxy | `toString()` và các method `Object` nói trên có thể đi vào handler; cẩn thận vòng lặp đệ quy |
| Không nhìn thấy method concrete-only qua JDK proxy | Thêm hành vi vào interface nếu đó thật sự là phần contract cần công khai |
| Tên lớp proxy hoặc hành vi khác giữa môi trường | Không phụ thuộc tên lớp sinh ra; kiểm tra version JVM/thư viện, class loader và module configuration |

Không nên dùng tên dạng `$Proxy...` hoặc tên lớp con được generator sinh làm định danh ổn định. Chúng là chi tiết triển khai. Dùng interface/class contract cùng API metadata phù hợp để nhận diện.

## 7. Chọn cơ chế nào?

- **Ưu tiên JDK dynamic proxy** khi lớp đích đã có interface phù hợp, ứng dụng gọi qua interface, và muốn một lớp trung gian cho log, quyền truy cập, đo đạc hay remote invocation bằng thư viện chuẩn.
- **Xem xét subclass proxy** khi caller cần đúng kiểu concrete class hoặc lớp đích không có interface, đồng thời các method cần chặn có thể override, lớp có thể được kế thừa và môi trường cho phép tạo/định nghĩa lớp.
- **Kiểm tra framework thực tế** khi làm việc với Spring AOP, Hibernate hoặc mocking library: một framework có thể lựa chọn chiến lược proxy theo cấu hình và trường hợp sử dụng; việc dùng Byte Buddy/CGLIB không tự chứng minh mọi method được chặn hay mọi constructor chạy theo cùng một đường.

Đặt yêu cầu trước khi lựa chọn: **caller đang dùng interface hay class? Method cần chặn có thể override không? Proxy có target riêng hay chính là instance thực thi?** Ba câu trả lời này thường xác định hướng phù hợp.

## 8. Tự kiểm tra

**Câu 1.** JDK dynamic proxy có sinh bytecode không?

**Có.** Nó tạo lớp proxy ở runtime; khác biệt là lớp ấy extends `Proxy` và implements interface, không extends implementation concrete.

**Câu 2.** Có thể cast JDK proxy của `Greeting` thành `GreetingImpl` chỉ vì handler chuyển tiếp đến `GreetingImpl` không?

**Không.** Việc handler gọi một target không làm thay đổi quan hệ kế thừa của proxy object.

**Câu 3.** Nếu `Service` là `final class`, thư viện có thể dùng cơ chế subclass proxy thông thường không?

**Không.** Java cấm kế thừa final class. Có thể cần thiết kế dựa trên interface hoặc một kỹ thuật khác với subclassing.

**Câu 4.** Một method `final` có thể chạy trên subclass instance không? Có bị chặn qua override không?

**Có thể chạy nếu được kế thừa và truy cập hợp lệ**, nhưng **không thể chặn bằng override** trong lớp con.

**Câu 5.** Vì sao việc gọi `this.someMethod()` đôi khi bypass interceptor dù framework dùng class-based proxy?

Vì framework có thể thực thi method trên **target instance riêng** hoặc có quy tắc interception riêng; phải phân tích nơi diễn ra dispatch, không suy từ hình thức lớp proxy.

## Tài liệu tham khảo chuẩn

- Java SE 17 API: [`java.lang.reflect.Proxy`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html).
- Java SE 17 API: [`java.lang.reflect.InvocationHandler`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html).
- Java Language Specification §8.4.8 (override), §8.1.1.2 (final class), §15.12 (method invocation).
- Tài liệu Byte Buddy và CGLIB của đúng phiên bản đang sử dụng khi cần xác minh chiến lược tạo lớp, interceptor và instantiation.

**Ghi nhớ:** JDK Proxy dùng một lớp implements interface để chuyển lời gọi tới `InvocationHandler`; subclass proxy dùng một lớp extends concrete class, thường chặn hành vi qua override. Cả hai có thể sinh bytecode ở runtime, nhưng **hình dạng kiểu và điểm chặn lời gọi khác nhau**, nên khả năng cast, phạm vi method và giới hạn `final` cũng khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)
