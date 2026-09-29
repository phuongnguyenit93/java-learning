# Dynamic Proxy

Các chương trước dùng Reflection để tìm member rồi thao tác trực tiếp với descriptor. Dynamic Proxy đưa ý tưởng đó lên một abstraction cao hơn: thay vì ứng dụng tự gọi `Method.invoke()` ở mọi nơi, JDK có thể tạo một object runtime **implements một hoặc nhiều interface** và chuyển mọi lời gọi interface qua một `InvocationHandler`.

Đây là nền tảng cho nhiều pattern interception như logging, authorization, ranh giới transaction, metrics hay remote client. Bên gọi vẫn thấy một interface có type rõ ràng; phần động nằm phía sau proxy.

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

Trước khi nhìn API, hãy thấy bài toán mà proxy giải quyết. Không có proxy, ta có thể tự viết một wrapper cho logging:

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

Cách này hoàn toàn hợp lệ khi chỉ có một vài method. Nhưng nếu logging/authorization/metrics phải lặp qua hàng chục interface và method, ta bắt đầu tạo rất nhiều wrapper gần như giống nhau. JDK Dynamic Proxy thay phần boilerplate đó bằng một ý tưởng:

```text
typed interface call
        ↓
runtime-generated implementation
        ↓
one InvocationHandler receives Method + args
        ↓
shared interception/delegation policy
```

Đây là **WHY** của JDK Proxy: giữ contract interface cho bên gọi nhưng gom logic cross-cutting động vào một handler chung.

`Proxy.newProxyInstance(...)` cần ba thành phần chính:

1. `ClassLoader` dùng để define/resolve proxy class;
2. danh sách interface mà proxy sẽ implement;
3. một `InvocationHandler` nhận các lời gọi.

Ở đây dùng `PaymentGateway.class.getClassLoader()` vì loader tạo proxy phải nhìn thấy các interface mà proxy sẽ implement. Chapter này chỉ cần biết ràng buộc visibility đó; class identity, delegation và các loader khác nhau thuộc module ClassLoader kế tiếp.

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

Object `proxy` không phải instance của `PaymentGatewayImpl`. Nó là instance của một class được JDK tạo ở runtime và class đó implements `PaymentGateway`. Vì vậy bên gọi có thể sử dụng interface type-safe:

```java
PaymentGateway gateway = proxy;
gateway.pay(request);
```

Trong khi luồng gọi thực tế là:

```text
caller
  ↓ PaymentGateway.pay(...)
generated proxy object
  ↓ encode call as Method + args
InvocationHandler.invoke(...)
  ↓ optional logic
target / delegate
```

Dynamic Proxy vì thế không làm mất contract của interface. Nó chèn một ranh giới interception giữa bên gọi và phần triển khai.

## <a id="invocation-handler">InvocationHandler và luồng ủy quyền</a>

`InvocationHandler` có một method trung tâm:

```java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
```

`method` mô tả thao tác mà bên gọi vừa gọi; `args` chứa arguments; `proxy` là chính proxy instance. Handler có thể log, kiểm tra quyền, đo thời gian, định tuyến sang remote endpoint hoặc ủy quyền tới object thật.

Một handler tối giản cho ví dụ payment:

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

Việc unwrap `InvocationTargetException` khi ủy quyền bằng `Method.invoke()` rất quan trọng. Nếu target `pay()` ném business exception, handler thường muốn truyền tiếp cause thật theo contract của interface thay vì để wrapper của Reflection trở thành lỗi chính.

Exception từ handler cũng phải phù hợp với contract của method mà proxy đang implement. Runtime exception và error có thể đi qua trực tiếp; checked exception nên nằm trong `throws` của interface method tương ứng. Nếu handler ném một checked exception không được contract của method khai báo, bên gọi proxy có thể nhận `UndeclaredThrowableException` bọc exception đó.

Handler cũng không bắt buộc phải ủy quyền tới một object cục bộ. Một HTTP/RPC client proxy có thể đọc `Method` + annotation rồi encode request ra network. Điều cốt lõi là proxy biến một **lời gọi interface có type rõ ràng** thành một **sự kiện interception ở runtime**.

## <a id="proxy-interfaces">Interface là contract của JDK Proxy</a>

JDK Dynamic Proxy tạo phần triển khai cho interface; nó không tạo subclass của target class cụ thể. Do đó:

```java
proxy instanceof PaymentGateway     // true
proxy instanceof PaymentGatewayImpl // false
```

Bên gọi nên giữ reference bằng interface type. Nếu API bên ngoài yêu cầu class triển khai cụ thể, JDK Proxy không thể giả làm class đó.

Một proxy có thể implement nhiều interface:

```java
Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[] { PaymentGateway.class, Auditable.class },
        handler
);
```

Các interface phải nhìn thấy được từ class loader dùng để tạo proxy và phải thỏa các ràng buộc của `Proxy` API. Trên Java 21, interface đưa vào JDK Proxy **không được là hidden interface hoặc sealed interface**. Non-public interface còn bị giới hạn chặt hơn về package/module; đây là một lý do framework thường proxy public business interface.

Nếu nhiều interface có method cùng signature, handler vẫn nhận `Method` theo quy tắc lựa chọn của JDK Proxy. Không nên thiết kế handler dựa vào giả định “declaring interface luôn chính xác là interface mà bên gọi đã cast tới” khi signature bị trùng giữa nhiều proxy interface.

Default method của interface cũng đi qua handler. Nếu handler muốn gọi đúng phần triển khai mặc định thay vì ủy quyền theo cách khác, Java cung cấp `InvocationHandler.invokeDefault(...)` cho trường hợp đó.

## <a id="object-methods-proxy">equals, hashCode và toString trên proxy</a>

Ba method public, non-final của `Object` — `equals`, `hashCode`, `toString` — có hành vi đặc biệt đáng nhớ: khi gọi chúng trên proxy, JDK cũng dispatch chúng vào `InvocationHandler.invoke(...)`. `Method.getDeclaringClass()` trong trường hợp này là `Object.class`.

Vì vậy một handler kiểu này nguy hiểm:

```java
return method.invoke(proxy, args);
```

Nó invoke lại method trên chính proxy, proxy lại gọi handler, rồi lặp vô hạn.

Ủy quyền thẳng sang target cũng có thể tạo ngữ nghĩa bất ngờ. Ví dụ `proxy.equals(proxy)` mà handler gọi `target.equals(proxy)` có thể trả `false`, phá kỳ vọng mà thiết kế muốn giữ.

Handler nên quyết định contract cho ba method này một cách rõ ràng. Ví dụ handler phía trên dùng identity semantics cho `equals/hashCode` và `toString` tùy chỉnh. Một framework khác có thể chọn semantics theo target, nhưng `equals` và `hashCode` phải vẫn giữ contract tương ứng.

Các method final khác của `Object` như `getClass()`, `wait()` hay `notify()` không trở thành điểm interception của interface theo cách ba method trên làm.

## <a id="proxy-limitations">Giới hạn của proxy và ranh giới với Spring AOP</a>

Giới hạn cốt lõi của JDK Dynamic Proxy đến từ mô hình dựa trên interface:

- không có interface → JDK Proxy không thể cung cấp API của class cụ thể;
- chỉ các lời gọi instance đi qua proxy mới đi qua `InvocationHandler`;
- static method và constructor không phải lời gọi interface instance nên không bị JDK proxy intercept;
- gọi trực tiếp target object bỏ qua proxy hoàn toàn;
- method/trạng thái private không nằm trong interface không tự nhiên trở thành điểm interception.
- sealed interface và hidden interface không hợp lệ làm proxy interface với `Proxy.newProxyInstance(...)` trên Java 21.

Một class cụ thể `final` vẫn có thể được **bọc** phía sau JDK proxy nếu object đó implements interface, vì JDK proxy không subclass target. `final` trở thành vấn đề khi dùng cơ chế **class-based proxy** cần sinh subclass; lúc đó final class không thể được extend và final method không thể được override/intercept theo cách subclass proxy hoạt động.

Spring AOP giúp ứng dụng làm việc ở abstraction cao hơn. Spring có thể dùng JDK Dynamic Proxy cho interface-based proxy hoặc dùng class-based proxy (CGLIB trong Spring) khi cần proxy class cụ thể/tùy cấu hình. Vì class-based proxy dựa trên subclassing, nó có các ranh giới như final class/final method/private method không thể được override để advice theo cơ chế đó.

Dynamic Proxy cũng không giải quyết mọi dạng interception. Constructor interception, static call interception, bytecode weaving hay hành vi self-invocation thuộc các cơ chế/thiết kế khác. Trong module Reflection, điều cần nắm là JDK Proxy cung cấp một **phần triển khai interface được tạo ở runtime** với một handler chung cho lời gọi.

Toàn bộ lộ trình Reflection giờ nối thành một chuỗi:

```text
Class metadata
→ Field / Method / Constructor descriptors
→ access boundary
→ generic signature metadata
→ dynamic invocation
→ safety/performance/maintainability trade-offs
→ interface interception bằng Dynamic Proxy
```

Reflection mạnh nhất khi cấu trúc động là yêu cầu thật và được giữ trong một ranh giới rõ ràng. Khi type đã biết ở compile time, contract Java trực tiếp vẫn thường là lựa chọn đơn giản và dễ bảo trì hơn.
