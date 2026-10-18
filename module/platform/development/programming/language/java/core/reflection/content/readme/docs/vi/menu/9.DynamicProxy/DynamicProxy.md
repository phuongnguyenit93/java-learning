# Proxy động và chặn lời gọi

Các chương trước dùng Reflection để tìm thành phần rồi thao tác trực tiếp với đối tượng mô tả. Dynamic Proxy đưa ý tưởng đó lên một mức trừu tượng cao hơn: thay vì ứng dụng tự gọi `Method.invoke()` ở mọi nơi, JDK có thể tạo một đối tượng lúc chạy **triển khai một hoặc nhiều interface** và chuyển mọi lời gọi interface qua một `InvocationHandler`.

Đây là nền tảng cho nhiều mẫu chặn lời gọi như ghi log (logging), phân quyền (authorization), ranh giới giao dịch, đo lường (metrics) hay client từ xa. Bên gọi vẫn thấy một interface có kiểu rõ ràng; phần động nằm phía sau proxy.

Để nối với ví dụ xuyên suốt, ta thêm một interface vì JDK Dynamic Proxy làm việc trên interface:

```java
interface PaymentGateway {
    String pay(PaymentRequest request);
}

final class PaymentGatewayImpl implements PaymentGateway {
    private final PaymentService service;

    PaymentGatewayImpl(PaymentService service) {
        this.service = service;
    }

    @Override
    public String pay(PaymentRequest request) {
        return service.pay(request);
    }
}
```

## <a id="jdk-proxy-model">Mô hình JDK Dynamic Proxy</a>

Trước khi nhìn API, hãy thấy bài toán mà proxy giải quyết. Không có proxy, ta có thể tự viết một lớp bọc để ghi log:

```java
final class LoggingPaymentGateway implements PaymentGateway {
    private final PaymentGateway target;

    LoggingPaymentGateway(PaymentGateway target) {
        this.target = target;
    }

    @Override
    public String pay(PaymentRequest request) {
        System.out.println("before pay");
        try {
            return target.pay(request);
        } finally {
            System.out.println("after pay");
        }
    }
}
```

Cách này hoàn toàn hợp lệ khi chỉ có một vài method. Nhưng nếu logic ghi log/phân quyền/đo lường phải lặp qua hàng chục interface và method, ta bắt đầu tạo rất nhiều lớp bọc gần như giống nhau. JDK Dynamic Proxy thay phần mã lặp đó bằng một ý tưởng:

```text
lời gọi interface có kiểu rõ ràng
        ↓
phần triển khai được tạo lúc chạy
        ↓
một InvocationHandler nhận Method + args
        ↓
chính sách chặn lời gọi/ủy quyền dùng chung
```

Đây là **lý do tồn tại** của JDK Proxy: giữ hợp đồng interface cho bên gọi nhưng gom logic dùng chung giữa nhiều lời gọi vào một handler chung.

`Proxy.newProxyInstance(...)` cần ba thành phần chính:

1. `ClassLoader` dùng để định nghĩa/phân giải proxy class;
2. danh sách interface mà proxy sẽ triển khai;
3. một `InvocationHandler` nhận các lời gọi.

Ở đây dùng `PaymentGateway.class.getClassLoader()` vì loader tạo proxy phải nhìn thấy các interface mà proxy sẽ triển khai. Chương này chỉ cần biết ràng buộc về khả năng nhìn thấy đó; định danh class, cơ chế ủy quyền và quan hệ giữa nhiều loader thuộc module ClassLoader.

```java
PaymentGateway target = new PaymentGatewayImpl(
        new PaymentService("stripe")
);

InvocationHandler handler = new LoggingHandler(target);

PaymentGateway proxy = (PaymentGateway) Proxy.newProxyInstance(
        PaymentGateway.class.getClassLoader(),
        new Class<?>[] { PaymentGateway.class },
        handler
);

String result = proxy.pay(new PaymentRequest("ORD-42", 150_000L));
```

Đối tượng `proxy` không phải instance của `PaymentGatewayImpl`. Nó là instance của một class được JDK tạo lúc chạy và class đó triển khai `PaymentGateway`. Vì vậy bên gọi có thể sử dụng interface với kiểu rõ ràng:

```java
PaymentGateway gateway = proxy;
gateway.pay(request);
```

Trong khi luồng gọi thực tế là:

```text
bên gọi
  ↓ PaymentGateway.pay(...)
đối tượng proxy được sinh tự động
  ↓ biểu diễn lời gọi thành Method + args
InvocationHandler.invoke(...)
  ↓ logic trung gian nếu có
đối tượng đích / đối tượng được ủy quyền
```

Dynamic Proxy vì thế không làm mất hợp đồng của interface. Nó chèn một ranh giới chặn lời gọi giữa bên gọi và phần triển khai.

## <a id="invocation-handler">InvocationHandler và luồng ủy quyền</a>

`InvocationHandler` có một method trung tâm:

```java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
```

`method` mô tả thao tác mà bên gọi vừa gọi; `args` chứa các đối số; `proxy` là chính instance proxy. Với method không có tham số, JDK có thể truyền `args == null`, vì vậy handler tổng quát không nên mặc định `args.length` luôn dùng được. Handler có thể ghi log, kiểm tra quyền, đo thời gian, định tuyến tới điểm cuối từ xa hoặc ủy quyền tới đối tượng thật.

Một handler tối giản cho ví dụ thanh toán:

```java
final class LoggingHandler implements InvocationHandler {
    private final PaymentGateway target;

    LoggingHandler(PaymentGateway target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }

        System.out.println("before " + method.getName());

        try {
            Object result = method.invoke(target, args);
            System.out.println("after " + method.getName());
            return result;
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "PaymentGatewayProxy[" + target + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }
}
```

Việc lấy ngoại lệ gốc từ `InvocationTargetException` khi ủy quyền bằng `Method.invoke()` rất quan trọng. Nếu method đích `pay()` ném ngoại lệ nghiệp vụ, handler thường muốn truyền tiếp nguyên nhân thật theo hợp đồng của interface thay vì để lớp bọc của Reflection trở thành lỗi chính.

Ngoại lệ từ handler cũng phải phù hợp với hợp đồng của method mà proxy đang triển khai. `RuntimeException` và `Error` có thể đi qua trực tiếp; checked exception nên nằm trong `throws` của interface method tương ứng. Nếu handler ném một checked exception không được hợp đồng của method khai báo, bên gọi proxy có thể nhận `UndeclaredThrowableException` bọc ngoại lệ đó.

Handler cũng không bắt buộc phải ủy quyền tới một đối tượng cục bộ. Một HTTP/RPC client proxy có thể đọc `Method` + annotation rồi mã hóa yêu cầu gửi qua mạng. Điều cốt lõi là proxy biến một **lời gọi interface có kiểu rõ ràng** thành một **sự kiện chặn lời gọi ở lúc chạy**.

## <a id="proxy-interfaces">Interface là hợp đồng của JDK Proxy</a>

JDK Dynamic Proxy tạo phần triển khai cho interface; nó không tạo subclass của class đích cụ thể. Do đó:

```java
proxy instanceof PaymentGateway     // true
proxy instanceof PaymentGatewayImpl // false
```

Bên gọi nên giữ tham chiếu bằng kiểu interface. Nếu API bên ngoài yêu cầu class triển khai cụ thể, JDK Proxy không thể giả làm class đó.

Một proxy có thể triển khai nhiều interface:

```java
Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] { PaymentGateway.class, Auditable.class },
        handler
);
```

Các interface phải nhìn thấy được từ class loader dùng để tạo proxy và phải thỏa các ràng buộc của `Proxy` API. Trên Java 21, interface đưa vào JDK Proxy **không được là hidden interface hoặc sealed interface**. Interface không public còn bị giới hạn chặt hơn về package/mô-đun; đây là một lý do framework thường proxy interface nghiệp vụ public.

Nếu nhiều interface có method cùng chữ ký, handler vẫn nhận `Method` theo quy tắc lựa chọn của JDK Proxy. Không nên thiết kế handler dựa vào giả định “interface khai báo luôn chính xác là interface mà bên gọi đã ép kiểu tới” khi chữ ký bị trùng giữa nhiều proxy interface.

Default method của interface cũng đi qua handler. Nếu handler muốn gọi đúng phần triển khai mặc định thay vì ủy quyền theo cách khác, Java cung cấp `InvocationHandler.invokeDefault(...)` cho trường hợp đó.

## <a id="object-methods-proxy">equals, hashCode và toString trên proxy</a>

Ba method public, không `final` của `Object` — `equals`, `hashCode`, `toString` — có hành vi đặc biệt đáng nhớ: khi gọi chúng trên proxy, JDK cũng chuyển lời gọi vào `InvocationHandler.invoke(...)`. `Method.getDeclaringClass()` trong trường hợp này là `Object.class`.

Vì vậy một handler kiểu này nguy hiểm:

```java
return method.invoke(proxy, args);
```

Nó gọi lại method trên chính proxy, proxy lại gọi handler, rồi lặp vô hạn.

Ủy quyền thẳng sang đối tượng đích cũng có thể tạo ngữ nghĩa bất ngờ. Ví dụ `proxy.equals(proxy)` mà handler gọi `target.equals(proxy)` có thể trả `false`, phá kỳ vọng mà thiết kế muốn giữ.

Handler nên quyết định hợp đồng cho ba method này một cách rõ ràng. Ví dụ handler phía trên dùng ngữ nghĩa dựa trên đồng nhất đối tượng (identity) cho `equals/hashCode` và `toString` tùy chỉnh. Một framework khác có thể chọn ngữ nghĩa theo đối tượng đích, nhưng `equals` và `hashCode` phải vẫn giữ hợp đồng tương ứng.

Các method `final` khác của `Object` như `getClass()`, `wait()` hay `notify()` không trở thành điểm chặn lời gọi theo cách ba method trên làm.

## <a id="proxy-limitations">Giới hạn của proxy và ranh giới với Spring AOP</a>

Giới hạn cốt lõi của JDK Dynamic Proxy đến từ mô hình dựa trên interface:

- không có interface → JDK Proxy không thể cung cấp API của class cụ thể;
- chỉ các lời gọi instance đi qua proxy mới đi qua `InvocationHandler`;
- static method và constructor không phải lời gọi interface instance nên không bị JDK proxy chặn;
- gọi trực tiếp đối tượng đích bỏ qua proxy hoàn toàn;
- method/trạng thái private không nằm trong interface không tự nhiên trở thành điểm chặn lời gọi.
- sealed interface và hidden interface không hợp lệ làm proxy interface với `Proxy.newProxyInstance(...)` trên Java 21.

Một class cụ thể `final` vẫn có thể được **bọc** phía sau JDK proxy nếu đối tượng đó triển khai interface, vì JDK proxy không tạo subclass của class đích. `final` trở thành vấn đề khi dùng cơ chế **proxy dựa trên class** cần sinh subclass; lúc đó class final không thể được kế thừa và method final không thể được override/chặn theo cách proxy subclass hoạt động.

Spring AOP giúp ứng dụng làm việc ở mức trừu tượng cao hơn. Spring có thể dùng JDK Dynamic Proxy cho proxy dựa trên interface hoặc dùng proxy dựa trên class (CGLIB trong Spring) khi cần proxy class cụ thể/tùy cấu hình. Vì proxy dựa trên class dùng cơ chế tạo subclass, các class final, method final và method private không thể được override để áp dụng advice theo cơ chế đó.

Dynamic Proxy cũng không giải quyết mọi dạng chặn lời gọi. Chặn constructor, chặn static call, bytecode weaving hay hành vi self-invocation thuộc các cơ chế/thiết kế khác. Trong module Reflection, điều cần nắm là JDK Proxy cung cấp một **phần triển khai interface được tạo lúc chạy** với một handler chung cho lời gọi.

Dynamic Proxy là milestone tích hợp cuối trước phần tổng hợp. Đến đây người học đã thấy Reflection không chỉ là "gọi method bằng chuỗi" mà là một chuỗi từ siêu dữ liệu tới hành vi lúc chạy:

```text
Class<?> và siêu dữ liệu
→ các đối tượng mô tả Field / Method / Constructor
→ thao tác động
→ ranh giới truy cập
→ siêu dữ liệu chữ ký generic
→ chặn lời gọi interface bằng Dynamic Proxy
```

Chương cuối sẽ đặt toàn bộ chuỗi này vào mô hình quyết định: khi nào sự linh hoạt đó đáng để đổi lấy lỗi lúc chạy, liên kết ẩn, chi phí thực thi và chi phí bảo trì.
