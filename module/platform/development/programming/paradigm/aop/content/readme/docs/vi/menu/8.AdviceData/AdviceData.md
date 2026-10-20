<a id="back-to-top"></a>

# Dữ liệu trong advice

## Menu
- [Truy cập chữ ký và mô tả điểm can thiệp](#advice-data-001)
- [Nhận tham số, đối tượng đích và đối tượng thực thi](#advice-data-002)
- [Phân biệt dữ liệu ràng buộc trực tiếp với tra cứu bằng phản chiếu](#advice-data-003)
- [Giá trị null, kiểu nguyên thủy và chuyển đổi kiểu](#advice-data-004)
- [Làm việc với giá trị trả về và phương thức không trả dữ liệu](#advice-data-005)
- [Đọc annotation và dữ liệu cấu hình liên quan](#advice-data-006)
- [Không giữ tham chiếu đối tượng lâu hơn nhu cầu xử lý](#advice-data-007)
- [Giới hạn thu thập tham số chứa dữ liệu nhạy cảm](#advice-data-008)
- [Tách trích xuất ngữ cảnh khỏi xử lý nghiệp vụ của aspect](#advice-data-009)

## <a id="advice-data-001">Truy cập chữ ký và mô tả điểm can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Truy cập chữ ký và mô tả điểm can thiệp trong advice AOP

> **Mục tiêu học tập:** Hiểu dữ liệu mô tả **join point** mà một advice có thể quan sát: loại điểm can thiệp, **chữ ký khai báo** (signature), kiểu khai báo, tên, các kiểu tham số, kiểu kết quả và metadata tùy mô hình; phân biệt chúng với **đối số, đối tượng và ngữ cảnh thực thi thực tế**. Biết khai thác thông tin để ghi vết, đo lường và chẩn đoán mà không nhầm call site với execution, phương thức giao diện với phương thức triển khai, hoặc dữ liệu mô tả tĩnh với dữ liệu thay đổi theo từng lần gọi.

## 1. Vì sao advice cần biết nó đang đứng ở đâu?

Một hệ thống có nhiều thao tác như `submitOrder`, `refund` và `cancelOrder`. Nếu một advice chỉ in thông báo “đã vào phương thức” thì rất khó xác định hoạt động nào đang được quan sát, đặc biệt khi có **overload** (nhiều phương thức cùng tên nhưng khác chữ ký) hoặc những nhóm lớp dùng tên phương thức giống nhau.

Hành vi ghi nhận hữu ích thường cần thông tin có cấu trúc:

~~~text
Loại join point:  thực thi phương thức
Phương thức:       OrderService.submit(String, int)
Đối tượng đích:   OrderServiceImpl
Đối số lần này:   ["A-17", 2]
Kết quả:           "receipt-A-17"
~~~

Trong đó:

- **Loại join point, tên và chữ ký** mô tả **phần tử hoặc ranh giới chương trình** mà công cụ đang quan sát.
- **Đối tượng đích và đối số** nói về **một lần thực thi cụ thể**.
- **Kết quả hoặc ngoại lệ** chỉ biết được sau khi phần thực thi liên quan hoàn tất theo ngữ nghĩa của advice.
- Một số thông tin khác như **vị trí mã nguồn, annotation, caller, luồng điều khiển** chỉ có khi mô hình AOP, môi trường chạy và cơ chế weaving thực sự cung cấp.

Bài học tập trung vào câu hỏi: **“Từ bên trong một advice, ta biết điểm can thiệp này là gì, và mô tả nó chính xác đến mức nào?”** Đây là kiến thức nền tảng để làm logging, tracing, kiểm toán kỹ thuật, đo thời lượng và chẩn đoán pointcut.

## 2. Vị trí của dữ liệu join point trong AOP

Các thành phần thường gặp:

| Thành phần | Trách nhiệm | Thông tin liên quan |
|---|---|---|
| **Join point** | Điểm/sự kiện trong luồng chương trình mà mô hình AOP cho phép can thiệp | Loại điểm, phần tử thực thi, ngữ cảnh |
| **Pointcut** | Điều kiện lựa chọn tập join point | Có thể dùng một phần chữ ký và ngữ cảnh để so khớp |
| **Advice** | Hành vi bổ sung chạy tại join point được chọn | Có thể được cung cấp dữ liệu mô tả điểm và dữ liệu runtime |
| **Aspect** | Tổ chức các pointcut và advice của một mối quan tâm xuyên suốt | Nhóm các chính sách quan sát hoặc kiểm soát |
| **Weaving** | Kết hợp advice với chương trình | Quyết định khả năng truy cập dữ liệu tại điểm được hỗ trợ |

**Chữ ký không phải là advice.** Chữ ký mô tả phần tử của chương trình. **Điểm can thiệp không phải pointcut.** Pointcut là điều kiện lựa chọn các điểm đó. Một advice có thể đọc dữ liệu của **join point đã được lựa chọn**, nhưng không được suy rằng nó tự thấy mọi hoạt động tồn tại trong hệ thống.

Một công cụ chỉ hỗ trợ chặn **lời gọi phương thức qua proxy** không tự cung cấp join point của phép gán trường hoặc lời gọi constructor. Không thể yêu cầu “đọc chữ ký của field-set join point” khi cơ chế thực tế không có loại join point ấy.

## 3. Signature là gì?

**Signature** (chữ ký) là thông tin nhận diện cấu trúc của một phần tử chương trình tại một loại join point phù hợp. Với phương thức Java, một mô tả chữ ký có thể chứa:

- Tên phương thức: `submitOrder`.
- Kiểu khai báo: `OrderService`.
- Các kiểu tham số: `String`, `int`.
- Kiểu trả về: `Receipt` hoặc `void`.
- Một số thuộc tính khai báo như modifier, metadata hoặc thông tin kiểu, tùy API.
- Trong một số mô hình, một đối tượng signature có kiểu chuyên biệt: method signature, constructor signature, field signature…

Ví dụ mô tả dành cho người đọc:

~~~text
OrderService.submitOrder(String, int):Receipt
~~~

**Đây là ký pháp trình bày**, không phải biểu thức Java chuẩn hay cú pháp pointcut bắt buộc. Các công cụ AOP có thể định dạng chữ ký khác nhau; một đối tượng signature có thể trả nhiều trường thông tin thay vì chỉ một chuỗi.

### 3.1. Tên và chữ ký không giống nhau

Hai phương thức dưới đây có **cùng tên** nhưng khác danh sách kiểu tham số:

~~~java
void submitOrder(String id);
void submitOrder(String id, int quantity);
~~~

*Đây là hai khai báo minh họa, không phải chương trình Java độc lập.*

Chỉ ghi `submitOrder` vào log sẽ không cho biết overload nào được thực thi. Khi cần phân biệt, nên hiển thị **kiểu khai báo + tên + danh sách kiểu tham số**, và thêm kiểu trả về nếu hữu ích cho việc đọc.

### 3.2. “Chữ ký Java” và “chuỗi mô tả đầy đủ” không hoàn toàn tương đương

Trong quy tắc khai báo/overload của Java, **không thể overload chỉ bằng cách đổi kiểu trả về**. Một chuỗi mô tả để log có thể chứa kiểu trả về, modifier và annotation, dù những trường đó **không nhất thiết thuộc định nghĩa chữ ký dùng cho overload** của ngôn ngữ Java.

Ví dụ:

~~~text
public String find(String id)
~~~

là một mô tả giàu thông tin hơn chỉ `find(String)`. Khi trình bày dữ liệu AOP, hãy phân biệt:

1. **Danh tính/đặc điểm của phần tử khai báo** theo mô hình ngôn ngữ.
2. **Đối tượng metadata** do API/công cụ cung cấp.
3. **Chuỗi diễn giải** mà advice tự tạo để con người dễ đọc.

## 4. Signature khác dữ liệu runtime của một lời gọi

Xét phương thức:

~~~java
String submitOrder(String orderId, int quantity);
~~~

Hai lần thực thi có thể là:

~~~text
Lần 1: submitOrder("A-17", 2)
Lần 2: submitOrder("B-28", 5)
~~~

Ở cùng một phương thức khai báo, **tên và kiểu tham số không đổi**. Nhưng giá trị `orderId`, `quantity` khác nhau.

| Câu hỏi | Dữ liệu cần dùng |
|---|---|
| Đây là phương thức có tên gì? | Signature/metadata |
| Phương thức khai báo ở kiểu nào? | Declaring type của signature |
| Tham số thứ hai được khai báo kiểu nào? | Kiểu tham số trong signature |
| Lần gọi này truyền số lượng mấy? | **Đối số runtime** |
| Đối tượng triển khai thật sự thuộc lớp nào? | **Runtime target/receiver**, nếu công cụ cung cấp |
| Caller đã nhận kết quả nào? | Return value ở advice có ngữ nghĩa phù hợp |
| Ngoại lệ nào vừa được ném ra ngoài điểm này? | Dữ liệu exception ở advice tương ứng |

Một lời gọi có thể dùng cùng chữ ký nhưng **đối số khác**, **đối tượng đích khác**, **trạng thái khác**, hoặc được kích hoạt từ **caller khác**. Không được ghi một chữ ký rồi coi đó là bản sao đầy đủ của sự kiện runtime.

### 4.1. Static metadata và dynamic context

Thông tin chữ ký khai báo thường là **cấu trúc**, có thể được biết trước khi một lần gọi xảy ra. Dữ liệu runtime như đối số thực tế chỉ tồn tại tại lần xảy ra đó.

Tuy nhiên, “cấu trúc” không có nghĩa một cơ chế bắt buộc phân tích ở compile time. Metadata có thể được đọc qua reflection ở runtime hoặc được đưa sẵn vào advice trong quá trình weaving.

## 5. Các loại join point có chữ ký tương ứng khác nhau

Không phải mọi join point đều là thực thi phương thức.

| Loại join point nếu mô hình hỗ trợ | Thông tin mô tả thường hữu ích | Ví dụ |
|---|---|---|
| **Method execution** | Phương thức được thực thi, tên, kiểu khai báo, kiểu tham số/return | `OrderService.submit(String)` |
| **Method call** | Lời gọi được tạo tại một call site, phần tử được gọi | `caller -> service.submit(...)` |
| **Constructor call** | Constructor được yêu cầu ở nơi tạo đối tượng | `new Order(String)` |
| **Constructor execution** | Constructor cụ thể đang thực thi | `Order(String)` |
| **Field get** | Trường được đọc, kiểu khai báo, kiểu dữ liệu | `Order.status:String` |
| **Field set** | Trường được ghi, kiểu khai báo, kiểu dữ liệu | `Order.status:String` |
| **Exception handler** | Loại điểm xử lý ngoại lệ, dữ liệu có thể khác kiểu chữ ký method | Ranh giới xử lý `catch` nếu mô hình có hỗ trợ |

Một mô hình AOP có thể cung cấp **signature của field**, **signature của constructor** hoặc các loại descriptor khác. Không nên ép tất cả vào API method signature.

**Giới hạn quan trọng:** Đó là các **khả năng theo mô hình**, không phải lời hứa mọi hệ AOP đều hỗ trợ toàn bộ các loại join point trên. Khi viết advice, trước tiên phải xem công cụ đang chọn được loại điểm nào, và loại đối tượng mô tả nào được cung cấp ở chính điểm đó.

## 6. Mô tả join point thường gồm những nhóm dữ liệu nào?

Có thể chia theo vai trò:

### 6.1. Kind — loại điểm can thiệp

Ví dụ: method execution, method call, constructor execution, field get/set. Loại điểm quyết định các thuộc tính nào có ý nghĩa.

### 6.2. Signature — phần tử được quan sát

Một method signature có thể chứa tên, declaring type, parameter types, return type và modifier. Một constructor signature thường không có kiểu trả về như một phương thức thông thường. Field signature có tên trường và kiểu của trường.

### 6.3. Source location — vị trí mã nguồn nếu được cung cấp

Một số cơ chế có thể trả file và dòng nguồn liên quan đến join point shadow hoặc call site. Nhưng vị trí này **không phải luôn có**, và số dòng có thể thiếu hoặc thay đổi khi build, generate code, tối ưu hoặc không giữ debug metadata.

### 6.4. Target/this/receiver — đối tượng liên quan

Tùy mô hình, join point có thể cung cấp đối tượng đích, đối tượng đang thực thi hoặc một đối tượng đại diện. Không được giả định các khái niệm đó luôn cùng tham chiếu, nhất là khi dùng proxy hay các loại join point khác nhau.

### 6.5. Arguments — đối số tại lần xảy ra

Danh sách đối số hoặc các binding tương ứng có thể cho biết dữ liệu mà lời gọi mang theo. Giá trị này cần được phân biệt với **kiểu tham số khai báo**.

### 6.6. Return value và exception — dữ liệu sau thực thi

Không phải advice nào cũng có quyền quan sát kết quả. Advice chạy **trước** phần gốc chưa có return value của lần đó. Advice sau hoàn tất bình thường hoặc sau ngoại lệ có thể có dữ liệu khác nhau. Around advice có thể nhận giá trị hoặc lỗi khi continuation/proceed kết thúc.

**Đừng tự giả định một API join point duy nhất luôn có tất cả các trường trên.** Mỗi điểm và mỗi loại advice có thể có hợp đồng dữ liệu riêng.

## 7. Phân biệt method call với method execution khi đọc chữ ký

Một dòng chương trình có thể tạo một lời gọi:

~~~java
service.submitOrder("A-17");
~~~

Trong mô hình phân biệt các loại điểm:

- **Method call join point:** diễn ra ở **vị trí caller yêu cầu gọi**.
- **Method execution join point:** diễn ra khi **phương thức đích được thực thi**.

Cả hai có thể mô tả `submitOrder`, nhưng **ranh giới** khác nhau. Nếu một công cụ hỗ trợ cả hai, advice ở call site và advice ở execution có thể chạy khác thời điểm, khác phạm vi và có thông tin vị trí mã nguồn khác nhau.

Ví dụ phương thức được gọi từ hai nơi:

~~~text
CheckoutScreen.checkout()  ──┐
                             ├──> OrderService.submitOrder(...)
BatchImport.run()        ────┘
~~~

Có hai **vị trí gọi** khác nhau nhưng cùng có thể đi tới một phương thức thực thi ở phía đích. Chữ ký method giống nhau không đủ để nói caller nào đã gọi; cần **call site hoặc ngữ cảnh** tương ứng, nếu mô hình có hỗ trợ.

Vì vậy, advice cần phân biệt:

- **Chữ ký phần tử được gọi**.
- **Vị trí của join point đang được chọn**.
- **Ngữ cảnh thực tế của lần xuất hiện**.

### 7.1. Gọi qua interface và thực thi lớp triển khai

Biến `service` có thể được khai báo kiểu `OrderService`, còn đối tượng thực tế thuộc `OrderServiceImpl`.

Một cơ chế quan sát lời gọi trên **giao diện** có thể cung cấp metadata của method trên interface, không tự động là `Method` của lớp triển khai. Một cơ chế quan sát execution có thể quy định cách lấy method khác.

Đó không phải lỗi của metadata: **hai nguồn mô tả những ranh giới khác nhau**.

## 8. Ví dụ Java 17 hoàn chỉnh: quan sát chữ ký và ngữ cảnh qua proxy

Ví dụ này dùng Java Dynamic Proxy để chứng minh:

1. Lấy `Method` mô tả phương thức được gọi qua giao diện.
2. Đọc tên, declaring type, các kiểu tham số và kiểu trả về.
3. Đọc annotation trên phương thức giao diện.
4. Đọc đối số **của từng lần gọi**.
5. Phân biệt kiểu khai báo của method và lớp thực tế của đối tượng đích.
6. Truyền lời gọi tới phần nghiệp vụ và bảo toàn kết quả.

**Đây là chương trình Java 17 chạy độc lập, dùng proxy để mô phỏng ranh giới can thiệp; không phải một hệ weaving AOP tổng quát.** Java Dynamic Proxy không tự can thiệp vào constructor hay phép gán trường, và không quan sát các lời gọi không đi qua proxy.

Lưu tệp **`JoinPointSignatureDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Collectors;

public class JoinPointSignatureDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tracked {}

    public interface Operations {
        @Tracked
        String submit(String orderId, int quantity);
        String preview(String orderId);
    }

    public static final class OperationsImpl implements Operations {
        @Override
        public String submit(String orderId, int quantity) {
            System.out.println("[business] submit " + orderId
                    + " x" + quantity);
            return "receipt:" + orderId + ":" + quantity;
        }

        @Override
        public String preview(String orderId) {
            System.out.println("[business] preview " + orderId);
            return "preview:" + orderId;
        }
    }

    static String signature(Method method) {
        String parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return method.getDeclaringClass().getSimpleName()
                + "." + method.getName()
                + "(" + parameterTypes + "):"
                + method.getReturnType().getSimpleName();
    }

    static Object callTarget(Method method, Object target, Object[] values)
            throws Throwable {
        try {
            return method.invoke(target, values);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    public static void main(String[] args) {
        Operations target = new OperationsImpl();

        Operations observed = (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] {Operations.class},
                (proxy, method, values) -> {
                    System.out.println("[point] " + signature(method));
                    System.out.println("[metadata] declaring="
                            + method.getDeclaringClass().getSimpleName()
                            + ", target=" + target.getClass().getSimpleName()
                            + ", tagged="
                            + method.isAnnotationPresent(Tracked.class));
                    System.out.println("[runtime] args="
                            + Arrays.toString(values));

                    Object result = callTarget(method, target, values);
                    System.out.println("[return] " + result);
                    return result;
                }
        );

        System.out.println("[caller] " + observed.submit("A-7", 2));
        System.out.println("[caller] " + observed.preview("B-8"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac JoinPointSignatureDemo.java
java JoinPointSignatureDemo
~~~

**Stdout chính xác:**

~~~text
[point] Operations.submit(String,int):String
[metadata] declaring=Operations, target=OperationsImpl, tagged=true
[runtime] args=[A-7, 2]
[business] submit A-7 x2
[return] receipt:A-7:2
[caller] receipt:A-7:2
[point] Operations.preview(String):String
[metadata] declaring=Operations, target=OperationsImpl, tagged=false
[runtime] args=[B-8]
[business] preview B-8
[return] preview:B-8
[caller] preview:B-8
~~~

### 8.1. Vì sao chữ ký có `Operations`, không phải `OperationsImpl`?

Đối tượng `Method` trong lời gọi proxy giao diện mô tả **phương thức được khai báo trên giao diện**. Do đó:

~~~text
method.getDeclaringClass().getSimpleName() → Operations
target.getClass().getSimpleName()          → OperationsImpl
~~~

Cả hai kết quả đều đúng nhưng trả lời **hai câu hỏi khác nhau**. Đó là lý do không nên mặc định dùng `method.getDeclaringClass()` để kết luận runtime target class.

### 8.2. Vì sao giá trị tagged khác nhau?

`submit` được đánh dấu `@Tracked` trên giao diện, còn `preview` không có dấu hiệu đó. `@Retention(RUNTIME)` cho phép reflection quan sát annotation vào lúc chạy. Nếu đặt annotation **chỉ trên phương thức triển khai** mà handler vẫn nhận `Method` của giao diện, `method.isAnnotationPresent(...)` có thể không trả cùng kết quả.

Một cơ chế AOP khác có thể định nghĩa quy tắc lookup annotation riêng, vì thế phải xem **phần tử nào** đang được dùng làm metadata source.

### 8.3. `Arrays.toString(values)` có ý nghĩa gì?

`values` chứa đối số thực tế tại **lần gọi hiện tại**; đối số `int` đi qua cơ chế phản chiếu dưới dạng đối tượng boxed phù hợp, nên in ra `2`. Đó là dữ liệu runtime, không phải kiểu tham số `int` trong signature.

Trong chương trình thật, **không nên tự in mọi đối số**: có thể chứa mật khẩu, token, dữ liệu cá nhân, nội dung lớn hoặc đối tượng mà `toString()` tốn chi phí/gây tác dụng phụ. Ví dụ in đối số chỉ nhằm minh họa cấu trúc.

### 8.4. Kết quả trả về được xử lý ra sao?

Handler gọi phương thức đích rồi nhận giá trị. `callTarget` giải gói `InvocationTargetException` để trả lại nguyên nhân lỗi do method đích ném, nếu có. Ở hai lời gọi minh họa đều không có lỗi, nên handler in `[return]` và trả kết quả.

Nếu method đích **ném ngoại lệ**, dòng `[return]` ở phiên bản này sẽ không xuất hiện; muốn ghi nhận đường lỗi cần `catch` hoặc `finally` thích hợp. **Khả năng đọc chữ ký không phụ thuộc việc phương thức thành công**, nhưng dữ liệu kết quả chỉ có nếu ranh giới thực thi thực sự hoàn tất bình thường.

### 8.5. Giới hạn phạm vi của ví dụ

Java Dynamic Proxy chặn những lời gọi **đi qua đối tượng `observed`**, không tự chặn mọi lời gọi lên `target` hay những lời gọi nội bộ trên target. Ví dụ vì thế minh họa API metadata và dữ liệu runtime, **không chứng minh một công cụ AOP nào có thể truy cập mọi loại join point**.

## 9. Ví dụ thứ hai: phương thức, constructor và field có dạng mô tả khác nhau

Khi mô hình AOP hỗ trợ các loại join point khác ngoài phương thức, khái niệm signature cũng thay đổi tương ứng. Java Reflection có các phần tử `Method`, `Constructor` và `Field`; chúng là **nguồn metadata Java**, không tự động là đối tượng join point của một engine AOP.

Lưu tệp **`SignatureKindsDemo.java`**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SignatureKindsDemo {
    static final class Ledger {
        private final String code;

        Ledger(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    static String parameters(Class<?>[] types) {
        return Arrays.stream(types)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
    }

    public static void main(String[] args) throws Exception {
        Constructor<Ledger> constructor =
                Ledger.class.getDeclaredConstructor(String.class);
        Method method = Ledger.class.getDeclaredMethod("code");
        Field field = Ledger.class.getDeclaredField("code");

        System.out.println("constructor="
                + constructor.getDeclaringClass().getSimpleName()
                + "(" + parameters(constructor.getParameterTypes()) + ")");

        System.out.println("method="
                + method.getDeclaringClass().getSimpleName()
                + "." + method.getName()
                + "(" + parameters(method.getParameterTypes()) + "):"
                + method.getReturnType().getSimpleName());

        System.out.println("field="
                + field.getDeclaringClass().getSimpleName()
                + "." + field.getName()
                + ":" + field.getType().getSimpleName());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SignatureKindsDemo.java
java SignatureKindsDemo
~~~

**Stdout chính xác:**

~~~text
constructor=Ledger(String)
method=Ledger.code():String
field=Ledger.code:String
~~~

Ở đây constructor được mô tả bằng tên lớp và tham số, method bằng tên/tham số/kiểu trả về, field bằng tên/kiểu trường. Việc Java Reflection có thể đọc metadata **không đồng nghĩa** một công cụ AOP có thể gắn advice vào mọi constructor hoặc field access.

Muốn từ metadata tới một sự kiện AOP, cần có **mô hình join point hỗ trợ loại điểm**, pointcut lựa chọn và cơ chế weaving xử lý đúng nơi.

## 10. Các mức chi tiết khi in chữ ký

Một advice có thể muốn log:

~~~text
submit
submit(String,int)
Operations.submit(String,int)
Operations.submit(String,int):String
public abstract Operations.submit(String,int):String
~~~

Mỗi dòng có độ phân biệt và độ dài khác nhau. Chọn mức chi tiết theo mục đích:

- **Log ngắn:** chỉ tên hoặc tên + kiểu khai báo nếu không có nguy cơ trùng.
- **Chẩn đoán overload:** cần danh sách **kiểu tham số**.
- **Phân tích API/kiểu:** thêm kiểu trả về, modifier hoặc metadata đặc biệt.
- **Định danh ổn định hơn:** có thể cần tên kiểu đầy đủ (fully qualified name), cùng thông tin module/class loader khi thực sự có xung đột.
- **Đo hiệu năng:** tránh dựng chuỗi dài, đọc annotation lặp hoặc serialize mọi đối số ở đường thực thi tần suất cao.

Một chuỗi `toString()` của đối tượng join point có thể dễ đọc nhưng **không nhất thiết là định dạng ổn định để parse**. Nếu cần hệ thống theo dõi tự động, nên lấy **các trường cấu trúc riêng** mà API cung cấp, rồi tự định nghĩa schema thống nhất.

## 11. Tên kiểu đơn giản và tên kiểu đầy đủ

Trong Java:

- `Class.getSimpleName()` trả tên dạng ngắn, ví dụ `String`.
- `Class.getName()` hoặc `getTypeName()` thường chứa mô tả đầy đủ hơn theo API, ví dụ `java.lang.String`.
- Tên của **array**, **kiểu lồng**, **kiểu ẩn danh**, **kiểu sinh tự động**, **generic** và **kiểu bị erasure** có những khác biệt cần cân nhắc.

Ví dụ `String` và `java.lang.String` có thể là hai cách hiển thị cùng một kiểu. Nhưng hai lớp thuộc các package khác nhau đều có thể có simple name `Service`, nên định dạng chỉ dùng simple name dễ gây nhầm.

**Một identifier dễ đọc không đương nhiên là identifier toàn cục.** Trong hệ thống lớn có nhiều module, class loader hoặc mã sinh, nên thiết kế khóa định danh join point một cách rõ ràng thay vì dùng một chuỗi ngắn làm khóa duy nhất.

## 12. Tham số khai báo, tên tham số và giá trị đối số

Ba loại dữ liệu khác nhau:

| Dữ liệu | Ví dụ | Nguồn |
|---|---|---|
| **Kiểu tham số** | `String, int` | Chữ ký khai báo |
| **Tên tham số** | `orderId, quantity` | Metadata khai báo nếu được giữ và truy cập được |
| **Giá trị đối số thực tế** | `"A-7", 2` | Ngữ cảnh runtime của lần gọi |

Trong Java Reflection, `Method.getParameterTypes()` cung cấp kiểu tham số ở dạng `Class<?>[]`, còn `Method.getParameters()` cho thông tin `Parameter`. **Tên tham số gốc** không luôn được lưu ở dạng có thể đọc: với mã biên dịch bằng `javac -parameters`, reflection có thể thấy tên khai báo; nếu thiếu metadata đó, API có thể hiển thị tên tổng hợp như `arg0`.

Vì vậy không được xây chính sách quan trọng dựa trên việc tên tham số tự động luôn là `orderId`. Khi cần bind dữ liệu theo tham số, phải xem ngôn ngữ/công cụ AOP dùng **vị trí, tên, kiểu hoặc cơ chế binding** nào và biên dịch yêu cầu metadata gì.

### 12.1. Varargs và mảng

Một phương thức khai báo `log(String... tags)` thường được biểu diễn ở cấp bytecode/reflection bằng tham số mảng `String[]`. Khi tạo mô tả cho người đọc, cần quyết định hiển thị `String...` hay `String[]`, dựa trên cờ varargs và API có sẵn.

Không nên coi hai chuỗi hiển thị khác nhau đó là bằng chứng hai phương thức khai báo độc lập.

## 13. Generic, type erasure và bridge method

Một chữ ký ở mã nguồn có thể chứa `List<String>`, `T` hoặc generic bound; còn kiểu tham số trong bytecode thường chịu **type erasure**.

Ví dụ khai báo:

~~~java
void send(java.util.List<String> items);
~~~

Đây là khai báo minh họa. Nếu chỉ gọi `Method.getParameterTypes()`, kiểu tham số có thể được nhìn thấy là `java.util.List`. Muốn đọc metadata generic khai báo, reflection có các API như `getGenericParameterTypes()`, nhưng không phải mọi thông tin generic của đối tượng runtime đều còn tồn tại.

**Trong Java không thể khai báo hai overload chỉ khác `List<String>` so với `List<Integer>`** trên cùng kiểu theo cách khiến hai method có cùng erasure. Vì vậy, một định danh AOP dựa trên chữ ký runtime không được tự suy rằng parameterized type arguments luôn giúp phân biệt overload.

Khi Java compiler tạo **bridge method** để phục vụ tính tương thích đa hình với generic/covariant return, reflection có thể thấy các phương thức có cờ `isBridge()` hoặc `isSynthetic()`. Nếu một công cụ AOP quan sát các method-level join point ở bytecode, cần tìm hiểu nó xử lý bridge/synthetic method ra sao để tránh đếm trùng hoặc hiểu sai điểm gốc.

Không nên tự dùng tên phương thức đơn giản làm điều kiện duy nhất trong hệ có generic và các lớp triển khai phức tạp.

## 14. Annotation là dữ liệu mô tả nhưng nơi khai báo rất quan trọng

Một annotation có thể xuất hiện ở:

- Kiểu/lớp.
- Phương thức của giao diện.
- Phương thức triển khai.
- Constructor.
- Field hoặc tham số.
- Một phần tử khác theo `@Target`.

Từ góc độ advice, câu hỏi không chỉ là “có annotation này không?” mà còn là:

> **Annotation đang được tra cứu trên chính phần tử nào?**

Ví dụ một method trên interface có `@Tracked`, nhưng method triển khai không được ghi annotation đó. Java Reflection trên `Method` của lớp triển khai không tự có nghĩa kết quả giống method trên interface. Một cơ chế AOP có thể có quy tắc kết hợp hoặc tra cứu metadata riêng; cần nêu rõ.

**Retention** cũng ảnh hưởng khả năng đọc metadata. Annotation phục vụ compile time không nhất thiết tồn tại cho `isAnnotationPresent` khi chạy. Một bộ phân tích mã/bytecode có thể có nguồn dữ liệu khác reflection runtime.

### 14.1. Không gộp annotation của class với annotation của method

Một annotation trên class không tự đồng nghĩa mọi method đều trực tiếp mang annotation ấy. Nếu chính sách pointcut muốn chọn mọi method thuộc một class được đánh dấu, cần biểu thức phù hợp với **metadata của class**, không chỉ kiểm tra method annotation.

## 15. Modifier và khả năng truy cập

Metadata có thể mô tả `public`, `private`, `static`, `final`, `abstract` hoặc các thuộc tính khác, nhưng không phải mọi modifier đều có cùng ý nghĩa với mọi loại phần tử.

Ví dụ:

- Một constructor không được override như instance method.
- Một static method không được dispatch đa hình như instance method.
- Một field có modifier final không nói được điểm field-read có được weaving hay không.
- Một phương thức private có thể không chặn được bằng một cơ chế subclass override, nhưng một cơ chế weaving trực tiếp mã có thể có khả năng khác.

**Đọc được chữ ký không đồng nghĩa chặn được hành vi.** Java Reflection có thể trả `Method` cho một private method, nhưng một công cụ AOP dùng proxy giao diện không tự nhiên can thiệp vào mọi lần private method thực thi.

Tương tự, khả năng dùng reflection để **truy cập/invoke** một phần tử và khả năng chỉ **đọc metadata** là những quyền khác nhau, có thể chịu ràng buộc module/access của Java.

## 16. Source location: hữu ích nhưng không phải luôn đáng tin làm khóa định danh

Một số mô hình join point cho phép lấy file nguồn, dòng hoặc vị trí gần điểm can thiệp. Điều này hữu ích khi chẩn đoán:

~~~text
point: method call
called member: OrderService.submit(String)
source: CheckoutScreen.java, line 87
~~~

Đây chỉ là **ví dụ định dạng**, không phải một kết quả có thật được đo từ chương trình.

Cần lưu ý:

1. Không phải mọi điểm đều có thông tin file/line.
2. Mã bytecode có thể được tạo từ nhiều nguồn; debug metadata có thể bị bỏ.
3. Refactor hoặc chèn dòng có thể thay đổi số dòng mà ngữ nghĩa nghiệp vụ không đổi.
4. Cùng một phương thức có thể được gọi tại nhiều call site, nhưng execution join point không nhất thiết cung cấp caller source location.
5. Class loader, mã generate và optimization có thể ảnh hưởng dữ liệu mà công cụ báo.

Không nên dùng **số dòng mã nguồn** làm khóa duy nhất cho sự kiện kiểm toán lâu dài. Nó thích hợp để điều tra, không phải danh tính nghiệp vụ bền vững.

## 17. Static part và dynamic context của join point

Trong một số mô hình AOP, đối tượng join point có thể có phần mô tả **tĩnh** (*static part*) và ngữ cảnh động của **lần thực thi**.

Mô hình tư duy:

~~~text
STATIC PART
    kind
    signature
    source location (nếu có)
    thông tin cấu trúc khác
          |
          | được dùng tại nhiều lần xuất hiện
          v
DYNAMIC JOIN POINT OCCURRENCE
    đối tượng hiện tại / đối tượng đích
    đối số cụ thể
    ngữ cảnh lời gọi
    kết quả / ngoại lệ nếu phù hợp với loại advice
~~~

Ví dụ một method execution có cùng chữ ký xuất hiện 10.000 lần. Bộ máy có thể tái sử dụng phần metadata tĩnh và đưa dữ liệu động cho từng lần xảy ra. Nhưng **không được tự suy rằng mọi API AOP đều công khai một kiểu `StaticPart` hoặc có cùng chiến lược cache**.

### 17.1. Điều kiện tĩnh không có nghĩa runtime không cần xử lý

Một bộ máy có thể phân tích chữ ký trước rồi tạo code can thiệp. Một proxy đơn giản có thể kiểm tra method qua reflection khi được gọi. Hai cách đều có thể dùng **thông tin cấu trúc**.

Đừng nhầm **loại thông tin** với **thời điểm công cụ tính ra thông tin đó**.

## 18. Tên join point không đồng nghĩa ngữ nghĩa nghiệp vụ

Một method tên `save()` có thể:

- Lưu bản nháp.
- Lưu giao dịch đã được xác nhận.
- Lưu một bản ghi chẩn đoán.
- Gửi một yêu cầu tới hệ thống ngoài mà chưa bảo đảm kết quả cuối.

Nếu advice quyết định chính sách chỉ theo tên `save`, nó có thể bao phủ quá rộng. **Signature mô tả cấu trúc**, không bảo đảm ý nghĩa nghiệp vụ hoặc trạng thái kết quả.

Khi cần phân biệt “đã lưu thành công”, advice thường phải căn cứ vào hợp đồng trả về, ngoại lệ, điểm commit hoặc sự kiện có ngữ nghĩa phù hợp. Dữ liệu runtime có thể mang những thông tin đó, nhưng vẫn phải chọn đúng ranh giới quan sát.

**Ví dụ:** `processAsync()` trả về một `Future` bình thường. Chữ ký có kiểu trả về `Future`, còn công việc thực sự có thể thất bại vài giây sau. Advice đọc chữ ký và thấy phương thức trả về không đồng nghĩa “công việc đã hoàn tất”.

## 19. Rủi ro khi ghi đối số và kết quả thô vào log

Lấy được dữ liệu join point chỉ là khả năng kỹ thuật, **không có nghĩa luôn nên ghi ra**.

Những rủi ro:

- Dữ liệu nhạy cảm: thông tin khách hàng, token, bí mật, tài khoản, dữ liệu sức khỏe.
- Đối tượng lớn hoặc đệ quy: in toàn bộ object graph gây tốn tài nguyên hoặc lỗi stack.
- `toString()` có tác dụng phụ hoặc chi phí lớn.
- Dữ liệu đồng thời thay đổi: chuỗi log có thể không mô tả một snapshot nhất quán.
- Số lượng log quá cao tại hot path.
- Tên loại hoặc chữ ký nội bộ có thể không nên lộ ra bên ngoài ranh giới tin cậy.

Cách làm phù hợp:

1. Ưu tiên **metadata cấu trúc** cần thiết như operation name và loại join point.
2. Định nghĩa danh sách giá trị được phép ghi, masking/redaction và giới hạn kích thước.
3. Chỉ ghi dữ liệu runtime đúng mục đích, tránh serialize tùy tiện.
4. Tách log kỹ thuật khỏi quyết định nghiệp vụ/kiểm soát quyền.
5. Bảo đảm lỗi của logger không vô tình thay đổi giá trị trả về hoặc che ngoại lệ gốc nếu quan sát được kỳ vọng là best-effort.

## 20. Metadata, performance và cache đúng cách

Chữ ký của một `Method` không đổi vì đối số `"A-7"` hay `"B-8"`. Vì vậy có thể cân nhắc **cache phép định dạng chữ ký** theo một khóa metadata phù hợp, trong phạm vi vòng đời và class loader đúng.

Nhưng **đối số, receiver và kết quả** thay đổi theo từng lần gọi, không nên dùng một cache chỉ dựa trên tên method để lưu chúng.

Ví dụ chiến lược:

~~~text
Ở giai đoạn chuẩn bị / khi gặp method đầu tiên:
    xây chuỗi mô tả signature
    cache theo định danh Method thích hợp

Ở mỗi lần join point xuất hiện:
    lấy signature có sẵn
    đọc đối số thực tế lần này
    thực hiện advice theo chính sách
~~~

Đây là pseudocode, không phải thuật toán bắt buộc. Trong chương trình có **nhiều class loader hoặc hot reload**, cache có thể cần chính sách vòng đời để tránh giữ tham chiếu class mãi mãi.

**Tránh tối ưu sớm:** việc dùng cache phải được đo đạc, không nên thêm độ phức tạp nếu chi phí chính đến từ I/O logging, serialization hoặc truy vấn trạng thái.

## 21. Phân biệt thông tin ở before, after và around advice

| Loại advice | Có thể mong đợi thông tin nào? | Không nên giả định |
|---|---|---|
| **Before** | Metadata của join point và ngữ cảnh đầu vào nếu có | Đã biết kết quả trả về của hoạt động chưa chạy |
| **After-returning** | Metadata và kết quả hoàn tất bình thường nếu cơ chế cung cấp | Mọi lần trả bình thường đều thành công nghiệp vụ |
| **After-throwing** | Metadata và ngoại lệ đi ra khỏi ranh giới nếu cơ chế cung cấp | Mọi ngoại lệ được bắt trong phương thức đều đi ra |
| **After/finally** | Metadata và thông tin kết thúc theo semantics của công cụ | Luôn có cả return value lẫn exception trong một lần thoát |
| **Around** | Metadata, ngữ cảnh khi vào, kết quả/ngoại lệ từ continuation nếu gọi tiếp | `proceed` luôn gọi thẳng phần gốc hoặc luôn chỉ được gọi một lần |

Một số cơ chế cấp quyền truy cập đối tượng join point trong advice qua tham số ràng buộc; một số có một context object hay API khác. Không thể suy ra cú pháp chỉ từ khái niệm AOP.

### 21.1. Proceed và dữ liệu join point

Trong around advice, `proceed` hoặc continuation tương đương biểu diễn **phần thực thi tiếp theo trong chuỗi**. Nó có thể đi qua advice khác trước khi tới phần gốc. Nếu around advice không gọi tiếp, dữ liệu return/exception mà caller thấy có thể phản ánh **kết quả thay thế**, không phải kết quả nghiệp vụ gốc.

Nếu cơ chế cho phép thay đối số khi tiếp tục, cần phân biệt **đối số tại điểm vào**, **đối số được truyền xuống** và **đối số advice phía trong nhìn thấy**. Không có hợp đồng chung cho tất cả công cụ.

## 22. Điểm cần cẩn thận với nhiều aspect

Nếu hai aspect cùng quan sát một join point, chúng có thể cùng đọc signature và đối số, nhưng:

- Thứ tự chạy có thể khác nhau.
- Around advice phía ngoài có thể short-circuit, khiến phần gốc hoặc advice phía trong không chạy.
- Around advice có thể thay return value hoặc exception theo chính sách.
- Điều kiện động có thể thay đổi tập điểm được chọn từng lần.
- Mã nghiệp vụ có thể hoàn tất bình thường nhưng trạng thái nghiệp vụ thất bại.

**Một metadata signature giống nhau không bảo đảm hai advice cùng thấy một sự kiện có cùng kết quả cuối.** Phải nêu rõ ranh giới và thứ tự advice khi diễn giải log.

## 23. Những lỗi phổ biến khi truy cập signature/join point

### 23.1. Chỉ in tên mà bỏ kiểu khai báo và tham số

Rủi ro: log `submit` không phân biệt hai overload hoặc hai lớp khác nhau.

**Khắc phục:** định nghĩa định dạng gồm đủ các trường cần để phân biệt.

### 23.2. Đồng nhất declaring type với runtime target

Rủi ro: metadata lấy từ interface `Operations` nhưng lại ghi “đối tượng thực thi là Operations”.

**Khắc phục:** đọc đúng trường **declaring type** và trường **đối tượng runtime** riêng biệt, nếu công cụ có cung cấp.

### 23.3. Coi giá trị đối số là một phần cố định của signature

Rủi ro: dùng cùng chữ ký `pay(String,int)` để suy mọi lời gọi đều là VIP hoặc đều có số tiền giống nhau.

**Khắc phục:** phân biệt metadata tĩnh với ngữ cảnh từng lần thực thi.

### 23.4. Coi string từ `toString()` là hợp đồng API

Rủi ro: định dạng thay đổi theo phiên bản hoặc mô hình; parser log bị lỗi.

**Khắc phục:** truy cập trường có cấu trúc, tạo schema/log format riêng.

### 23.5. Tưởng Method của interface có mọi annotation ở implementation

Rủi ro: đọc annotation không đúng nguồn và loại nhầm join point.

**Khắc phục:** xác định rõ phần tử metadata; kiểm tra quy tắc lookup của cơ chế đang dùng.

### 23.6. Cho rằng reflection đọc được nghĩa là AOP chặn được

Rủi ro: đọc được `Field` hoặc constructor qua Reflection rồi kết luận tool proxy hiện tại hỗ trợ field/constructor join point.

**Khắc phục:** kiểm tra join point model, pointcut, weaving scope và runtime path.

### 23.7. Đọc source location như một định danh ổn định

Rủi ro: sửa file, biên dịch lại hoặc bỏ debug info khiến file/line thay đổi.

**Khắc phục:** dùng source location cho chẩn đoán, không làm khóa nghiệp vụ duy nhất.

### 23.8. Ghi toàn bộ arguments/return value

Rủi ro: rò rỉ dữ liệu nhạy cảm, tạo log khổng lồ, `toString` có tác dụng phụ.

**Khắc phục:** redaction, whitelist, giới hạn độ dài và kiểm tra tác động hiệu năng.

### 23.9. Lấy thời điểm method return làm thời điểm hoàn tất toàn bộ async task

Rủi ro: advice ghi `SUCCESS` khi thực chất mới nhận một handle tới công việc đang chạy.

**Khắc phục:** quan sát đúng sự kiện hoàn tất của tác vụ nếu đó mới là mục tiêu.

### 23.10. Dùng dữ liệu metadata để thay kiểm tra quyền

Rủi ro: suy rằng phương thức có tên `authorized*` nghĩa là người dùng đã được phân quyền.

**Khắc phục:** chính sách an toàn phải dựa trên ranh giới đáng tin cậy và dữ liệu quyền thực tế; signature chỉ hỗ trợ nhận diện thao tác.

## 24. Quy trình thực hành khi thiết kế advice đọc chữ ký

1. **Xác định mục đích:** logging, audit, metrics hay chẩn đoán pointcut?
2. **Chọn loại join point:** method call, execution, constructor, field…; kiểm tra mô hình có hỗ trợ không.
3. **Liệt kê metadata cần thiết:** kind, declaring type, member name, parameter types, return type, annotation, location nếu có.
4. **Liệt kê ngữ cảnh động cần thiết:** receiver/target, đối số, kết quả/exception, correlation ID.
5. **Xác định thời điểm có dữ liệu:** before, returning, throwing, finally hay around?
6. **Xác định nguồn metadata:** interface, implementation, call site hay đối tượng signature chuyên biệt?
7. **Thiết kế định dạng:** ưu tiên trường có cấu trúc, ổn định và tránh phụ thuộc chuỗi mặc định.
8. **Thiết kế xử lý dữ liệu nhạy cảm:** không in thô mọi đối số; giới hạn độ dài và masking.
9. **Kiểm thử overload/generic/proxy:** các điểm giống tên nhưng khác kiểu có được phân biệt?
10. **Kiểm thử nhánh thành công/lỗi:** có bảo toàn return/exception và không làm thay đổi nghiệp vụ?
11. **Kiểm thử phạm vi can thiệp:** method gọi qua proxy hay gọi trực tiếp, điểm có thuộc phạm vi weaving không?
12. **Đo chi phí:** cache metadata cấu trúc nếu cần; không cache giá trị runtime như dữ liệu cố định.
13. **Tài liệu hóa giới hạn:** dữ liệu nào được hỗ trợ, dữ liệu nào không thể lấy ở mô hình hiện tại.

Một advice tốt không chỉ “in được tên phương thức”; nó **biết tên đó phản ánh ranh giới nào, có thể tin cậy đến mức nào và những dữ liệu runtime nào không được phép suy ra từ chữ ký**.

## 25. Bài tập tự kiểm tra

### Bài 1. Tên và chữ ký

Hai phương thức `send(String)` và `send(String,int)` có cùng tên. Nếu log chỉ in `send`, thiếu dữ liệu gì để phân biệt overload?

**Đáp án:** cần danh sách **kiểu tham số khai báo** (và thường thêm kiểu khai báo để phân biệt các lớp). Kiểu trả về có thể hữu ích khi mô tả nhưng không phải căn cứ overload chỉ dựa vào return type.

### Bài 2. Giao diện và lớp đích

Advice dùng proxy giao diện lấy `method.getDeclaringClass().getSimpleName()` bằng `Operations`; đối tượng đích là `OperationsImpl`. Hai giá trị có mâu thuẫn không?

**Đáp án:** không. Một giá trị mô tả **nơi phương thức phản chiếu được khai báo**, giá trị kia là **lớp của đối tượng runtime**.

### Bài 3. Static versus dynamic

Cùng method `pay(String,int)` được gọi `pay("VIP",120)` rồi `pay("NORMAL",120)`. Dữ liệu nào có thể dùng lại giữa hai lần?

**Đáp án:** tên, declaring type, kiểu tham số và return type có thể giống nhau; **giá trị tier** khác, phải đọc tại mỗi lần gọi nếu chính sách phụ thuộc nó.

### Bài 4. Metadata của annotation

Annotation nằm trên method implementation, nhưng advice kiểm tra `Method` của interface. Có thể kết luận annotation chắc chắn hiện diện không?

**Đáp án:** không. Phải xác định phần tử được tra cứu và quy tắc metadata lookup; reflection trên method interface không tự kế thừa mọi annotation của method triển khai.

### Bài 5. Generic signature

`getParameterTypes()` đối với một tham số khai báo `List<String>` có nhất thiết trả một kiểu runtime độc lập tên `List<String>` không?

**Đáp án:** không. Type erasure và API reflection ảnh hưởng cách biểu diễn; `getGenericParameterTypes()` có thể đọc metadata generic khai báo khi còn lưu, nhưng không biến type argument thành kiểu runtime phân biệt overload.

### Bài 6. Constructor và field

Java Reflection tìm được `Constructor` và `Field`. Có được kết luận một công cụ AOP hiện tại chặn mọi constructor execution và field set không?

**Đáp án:** không. Khả năng đọc metadata Java khác với hỗ trợ **loại join point** và **weaving scope** của công cụ AOP.

### Bài 7. Source location

Một điểm can thiệp hôm nay được ghi ở dòng 87, mai refactor thành dòng 95. Điều đó có chắc là hai hoạt động nghiệp vụ khác nhau?

**Đáp án:** không. Dòng nguồn hữu ích cho chẩn đoán, nhưng không phải khóa định danh ngữ nghĩa ổn định.

### Bài 8. Ngoại lệ

Advice chạy trước method `submit`. Nó có thể biết trước ngoại lệ thực tế mà method sẽ ném ra sau đó chỉ bằng cách đọc chữ ký `throws` không?

**Đáp án:** không. Danh sách kiểu ngoại lệ **được khai báo** (nếu có) là metadata; ngoại lệ **thực tế xảy ra** của một lần thực thi chỉ biết khi đường lỗi phát sinh và được quan sát.

### Bài 9. Kết quả async

Một method có signature trả `CompletionStage<Result>`. Advice đọc chữ ký và thấy method return bình thường. Có thể ghi “công việc cuối cùng thành công” không?

**Đáp án:** không. Return của phương thức và việc tác vụ bất đồng bộ hoàn tất là hai mốc khác nhau; cần quan sát kết quả hoàn tất đúng ranh giới.

### Bài 10. Dữ liệu log

Tại sao in `Arrays.toString(args)` cho mọi advice không phải chính sách production an toàn?

**Đáp án:** đối số có thể chứa dữ liệu nhạy cảm, lớn hoặc object có `toString()` gây tác dụng phụ. Cần chọn trường được phép ghi, masking, giới hạn kích thước và đo chi phí.

## 26. Tổng kết

- **Signature** mô tả **phần tử khai báo hoặc ranh giới cấu trúc** liên quan đến join point: tên, declaring type, các kiểu tham số, kiểu trả về và metadata khác khi mô hình cung cấp.
- **Mô tả join point** có thể rộng hơn signature, gồm loại điểm, vị trí mã, target/receiver, đối số và ngữ cảnh liên quan.
- **Đối số, đối tượng, kết quả và ngoại lệ thực tế** là thông tin của **một lần thực thi**, không phải đặc điểm bất biến của chữ ký.
- **Method call và method execution** có thể mang cùng member name nhưng là **hai ranh giới khác nhau**; thông tin call site không tự được suy ra từ execution signature.
- Java Reflection cho phép đọc **Method/Constructor/Field**, nhưng đọc được metadata **không đồng nghĩa một công cụ AOP chặn được tất cả loại join point**.
- Cần chú ý interface so với implementation, overload, annotation retention, generic erasure, bridge method, varargs, modifier và source location.
- Khi đưa metadata vào advice, phải thiết kế định dạng log ổn định, kiểm soát dữ liệu nhạy cảm và chi phí; không suy trạng thái nghiệp vụ từ tên method.
- **Pointcut, loại join point, weaving và runtime path** vẫn quyết định dữ liệu nào có thể được quan sát ở một điểm thực tế.

**Câu hỏi kiểm tra quan trọng nhất:** *“Thông tin này mô tả phần tử chương trình, vị trí join point, hay giá trị của lần thực thi hiện tại — và cơ chế AOP có thực sự cung cấp nó tại ranh giới mà tôi đã chọn không?”*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-002">Nhận tham số, đối tượng đích và đối tượng thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận tham số, đối tượng đích và đối tượng thực thi trong advice AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), advice thường cần biết **lần thực thi nào đang diễn ra** và **những dữ liệu nào liên quan tới lần thực thi đó**. Ghi log, kiểm tra quyền, đo thời gian, theo dõi truy vết hay kiểm toán đều có thể cần đọc tham số của lời gọi, đối tượng nhận lời gọi và đối tượng đang thực thi mã ở vị trí được can thiệp.

Ba nguồn ngữ cảnh dễ nhầm nhất là:

1. **Arguments / args – tham số thực tế:** những giá trị được truyền vào một lời gọi, theo vị trí và kiểu tương ứng.
2. **Target – đối tượng đích:** đối tượng mà thao tác hướng tới hoặc được gọi, nếu loại join point ấy có target.
3. **This / executing object – đối tượng đang thực thi:** đối tượng của ngữ cảnh `this` tại vị trí chương trình đang chạy; ở một **method call join point** có thể là đối tượng phía bên gọi, không phải target.

**Đây không phải ba tên cho cùng một đối tượng.** Đặc biệt, ở điểm gọi `a.call(b)`, đối tượng `a` có thể là *receiver/target* của phương thức `call`, trong khi `b` chỉ là **tham số**; còn `this` trong mã phát sinh lời gọi có thể là một đối tượng thứ ba. Ở điểm **method execution** trong phương thức instance của đích, `this` thông thường gắn với chính đối tượng nhận việc thực thi.

Bài này trình bày cách nhận và ràng buộc ba loại dữ liệu trong advice, giải thích sự khác biệt theo **loại join point**, cách sử dụng khi chọn pointcut và những giới hạn của cơ chế AOP. Tên biểu thức cụ thể như `this`, `target`, `args` được dùng theo nghĩa khái niệm thường thấy trong các mô hình AOP có khả năng expose ngữ cảnh; **không phải mọi công cụ đều hỗ trợ cùng cú pháp hoặc cùng tập join point**.

## 1. Ngữ cảnh của join point: tại sao advice cần dữ liệu?

**Join point (điểm nối)** là một điểm xác định trong luồng thực thi mà mô hình AOP có thể can thiệp. **Pointcut** xác định điểm nào được chọn. **Advice** là hành vi thực thi tại điểm được chọn. **Context binding/exposure** cung cấp dữ liệu của một lần xuất hiện join point cho advice.

Ví dụ nghiệp vụ:

~~~java
receiptService.create("ORDER-7", 3);
~~~

Đây là **một mảnh lời gọi**, giả định đã có đối tượng `receiptService` và phương thức `create`. Nếu advice ghi lại lần tạo biên nhận, nó có thể cần:

- **Chữ ký/mô tả:** tên phương thức `create`, kiểu tham số được khai báo.
- **Giá trị args:** `"ORDER-7"` và `3` ở chính lần gọi này.
- **Target:** đối tượng thực sự nhận thao tác tạo biên nhận.
- **Executing object / this:** đối tượng mà mã đang được thực thi tại điểm nối thuộc về nó, **nếu điểm đó có ngữ cảnh this**.
- **Dữ liệu sau thực thi:** giá trị trả về hoặc ngoại lệ, chỉ khả dụng ở những thời điểm/loại advice phù hợp.
- **Dữ liệu bổ sung:** correlation ID, identity đã xác thực hoặc transaction context nếu chương trình cung cấp qua một nguồn hợp lệ.

Advice không nên tự coi một đối số tên `userId` là danh tính đã xác thực. **Binding truyền dữ liệu, không tự xác minh độ tin cậy của dữ liệu.**

## 2. Bảng phân biệt: args, target và this

| Thành phần | Câu hỏi cần trả lời | Ví dụ ở một lời gọi method call | Lưu ý |
| --- | --- | --- | --- |
| **Arguments** | Lần này truyền những giá trị gì? | `"ORDER-7"`, `3` | Có thể rỗng, null, primitive được biểu diễn boxing |
| **Target** | Đối tượng nào là đích nhận lời gọi? | `receiptService` | Có thể không tồn tại ở điểm tĩnh, constructor hoặc loại join point khác |
| **This / executing object** | Đối tượng nào đang thực thi mã tại điểm nối? | `this` của phương thức bên gọi phát sinh `receiptService.create(...)` | Có thể khác target; ở static context không có instance this |

Nếu code là:

~~~java
class OrderController {
    private ReceiptService receiptService;

    void confirm() {
        receiptService.create("ORDER-7", 3);
    }
}
~~~

Đây là **đoạn minh họa trong ngữ cảnh lớp**. Tại **method call join point** của `receiptService.create` phát sinh trong `confirm()`:

- **This** của mã bên gọi có thể là instance `OrderController`.
- **Target** là instance nhận lời gọi `ReceiptService.create`.
- **Args** là mã đơn `"ORDER-7"` và số lượng `3`.

Nhưng tại **method execution join point** ở phần thân `ReceiptService.create`, nếu đó là phương thức instance thông thường, **this** đang chạy thân phương thức thường là đối tượng service đó; tức có thể trùng với đối tượng đích của execution. Sự phân biệt phải gắn với **loại join point**, không chỉ tên phương thức.

### 2.1 Đây là dữ liệu runtime chứ không chỉ chữ ký tĩnh

Kiểu khai báo trong phương thức có thể là:

~~~java
void create(CharSequence orderId, int copies);
~~~

Hai lần gọi `create("A", 1)` và `create("B", 5)` có **cùng chữ ký tĩnh** nhưng giá trị args khác nhau. Nếu `ReceiptService` là interface, một biến khai báo kiểu interface có thể trỏ tới nhiều triển khai runtime khác nhau.

Vì vậy cần phân biệt:

- **Declared parameter types:** `CharSequence`, `int`.
- **Actual argument values:** `"A"`, `1` hoặc `"B"`, `5`.
- **Actual argument runtime classes:** ví dụ đối số chuỗi có thể là `String`; đối số null không có runtime class.
- **Declared target type:** kiểu được dùng ở chữ ký hoặc tại call site.
- **Target runtime type:** lớp đối tượng thật tại lần thực thi, nếu có target.

Pointcut chọn theo chữ ký không đồng nghĩa pointcut chọn theo giá trị hay kiểu runtime của đối tượng.

## 3. Nhận tham số trong advice

### 3.1 Các cách tiếp cận khái niệm

Một hệ thống AOP có thể cho advice nhận:

1. **Toàn bộ danh sách đối số** dưới dạng mảng/sequence.
2. **Một số đối số được bind theo vị trí, tên hoặc kiểu**, tùy cơ chế.
3. **Thông tin chữ ký** như tên phương thức và kiểu được khai báo.
4. **Ngữ cảnh kết quả/ngoại lệ** ở loại advice sau thực thi phù hợp.

Giả mã không gắn với một ngôn ngữ AOP cụ thể:

~~~text
pointcut: execution of ReceiptService.create(...)
bind:     orderId = args[0]
          copies  = args[1]

before advice:
    record("create", orderId, copies)
~~~

Bộ chọn ở đây mô tả nơi chạy advice. Việc ràng buộc `args[0]` và `args[1]` giúp advice biết **giá trị của lần gọi đang xử lý**, không phải những giá trị cố định lúc khai báo pointcut.

Nếu một mô hình AOP hỗ trợ mẫu kiểu động trên tham số, pointcut còn có thể dùng **kiểu runtime của args** để quyết định có khớp. Điều đó khác với binding dữ liệu cho advice: **đọc một đối số không tự động có nghĩa đã lọc theo đối số đó**.

### 3.2 Không giả định args luôn có độ dài và thứ tự mong muốn

Nếu pointcut rộng chọn các overload khác nhau:

~~~java
void process(String id);
void process(String id, int priority);
~~~

Đây là mảnh khai báo. Với overload đầu, `args[1]` không tồn tại. Advice đọc chỉ số 1 mà không kiểm tra chữ ký/độ dài có thể ném lỗi. Phải chọn đúng signature hoặc kiểm tra đối số an toàn.

Ngoài ra:

- Phương thức không tham số có danh sách args rỗng.
- Tham số kiểu tham chiếu có thể là `null`.
- Tham số primitive có thể hiện dưới dạng wrapper (`int` thành `Integer`) ở một API reflection/proxy.
- Với varargs Java, phần `String...` thường là **một tham số mảng** ở chữ ký bytecode; đừng mặc định một phần tử vararg tương đương một mục độc lập trong mảng args của advice.
- Generic bị **type erasure** có thể làm kiểu trong descriptor khác kiểu tổng quát nhìn từ nguồn.
- Một số cơ chế cung cấp bản sao mảng đối số; thay đổi mảng ấy có được truyền sang lời gọi thật hay không **phụ thuộc hợp đồng của continuation/binding**.

### 3.3 Đọc khác với sửa tham số

Một advice chỉ quan sát thường nên đọc args mà không chỉnh sửa. Để thay tham số trước khi tiếp tục, cần khả năng **around advice + proceed với đối số thay thế** nếu mô hình hỗ trợ, và phải bảo toàn hợp đồng về kiểu, thứ tự, tính hợp lệ và tác dụng phụ.

Giả mã:

~~~text
around(context, proceed):
    original = context.args
    if allowedToNormalize(original):
        transformed = normalize(original)
        return proceed(transformed)
    return proceed(original)
~~~

**Không mặc định** việc sửa một `Object[]` trong handler hoặc context khiến lời gọi thật nhận đối số đã sửa. Cũng không mặc định mọi AOP runtime cho phép thay đối số hoặc gọi `proceed` nhiều lần. Đây là các tính năng cần xác minh.

### 3.4 Tránh làm rò rỉ dữ liệu

Truyền args cho advice logging là một ranh giới dữ liệu mới. Không tự in tất cả tham số hoặc `toString()` của đối tượng:

- Password, token, secret, thông tin thanh toán và dữ liệu cá nhân có thể bị lộ.
- `toString()` có thể có tác dụng phụ, ném lỗi hoặc vô tình truy cập cấu trúc lớn/vòng lặp.
- Các đối tượng mutable có thể thay đổi giữa thời điểm advice ghi nhận và thời điểm xử lý bất đồng bộ.
- Nếu lưu tham chiếu tới args lâu hơn vòng đời lời gọi, cần cân nhắc quyền sở hữu, snapshot và dọn dẹp để tránh rò dữ liệu.

Một cách an toàn hơn là **chọn đúng các trường cần quan sát, che dữ liệu nhạy cảm, ghi mã tương quan và trạng thái**, thay vì ghi toàn bộ payload.

## 4. Target: đối tượng đích là ai?

**Target** của một method-call join point instance thường là đối tượng mà lời gọi hướng tới. Nhưng một chương trình có thể chứa nhiều đối tượng:

~~~java
controller.submit(service, order);
~~~

Ở lời gọi `submit`:

- Target của **`submit`** là `controller`.
- `service` và `order` là **arguments** của lời gọi `submit`, không phải target của `submit`.
- Nếu bên trong `submit` có `service.save(order)`, thì ở lời gọi **`save`** target lại là `service`, args gồm `order`.

Việc một object xuất hiện trong danh sách tham số **không** biến nó thành target của cùng join point.

### 4.1 Target type tĩnh và type thực tế

~~~java
Service service = new SpecialService();
service.execute("A");
~~~

Nếu `Service` là interface và `SpecialService` là một triển khai, **kiểu khai báo** của biến service là `Service`, nhưng **runtime class** của target là `SpecialService`.

Một pointcut dựa trên kiểu khai báo của chữ ký có thể so khớp khác một pointcut kiểm tra target runtime. Nếu muốn chọn tất cả triển khai của interface, mẫu quan hệ subtype/instance (khi được hỗ trợ) có ý nghĩa khác điều kiện **đúng bằng một lớp**.

### 4.2 Proxy và decorator: “đối tượng nào là target?”

Trong hệ thống có **proxy**, bên gọi có thể nắm một proxy, proxy chuyển tiếp tới object nghiệp vụ gốc. Lúc này cần phân biệt:

- **Proxy/receiver tại biên:** object bên gọi thực sự gọi.
- **Underlying target:** object nghiệp vụ mà lớp bọc chuyển tiếp tới.
- **Method signature được handler nhận:** có thể được khai báo bởi interface.
- **Đối tượng đang chạy thân phương thức nghiệp vụ:** instance cụ thể của lớp triển khai.

Ở một **proxy invocation handler**, tham số `proxy` không nhất thiết là `target` gốc. Nếu advice muốn kiểm tra annotation hoặc kiểu của lớp nghiệp vụ, đọc `proxy.getClass()` có thể cho kết quả khác `target.getClass()`.

Quan trọng hơn, **không có một quy tắc phổ quát** bắt mọi công cụ AOP gán từ “target” cho cùng đối tượng khi có nhiều lớp bọc. Phải đọc định nghĩa ngữ cảnh của loại join point và kỹ thuật weaving đang sử dụng.

### 4.3 Target không phải lúc nào cũng tồn tại

Có thể có join point không gắn với một đối tượng instance cụ thể:

- Phương thức `static`: không có receiver instance theo nghĩa lời gọi instance.
- Một số điểm khởi tạo đối tượng: đối tượng có thể chưa tồn tại ở giai đoạn trước khi được tạo.
- Một số sự kiện xử lý ngoại lệ hoặc truy cập tĩnh: ngữ cảnh target phụ thuộc mô hình.
- Một số kỹ thuật weaving chỉ expose chữ ký và tham số, không có object target khả dụng.

Không được gọi `target.getClass()` khi target có thể là `null`. Việc `null` được diễn giải ra sao trong biểu thức pointcut còn phụ thuộc ngôn ngữ AOP cụ thể.

## 5. This: đối tượng đang thực thi và sự khác nhau giữa call/execution

Trong Java, từ khóa `this` trong thân **phương thức instance** tham chiếu đối tượng đang thực thi phương thức ấy. Nhưng khi dùng ký hiệu `this` trong pointcut AOP, cần hỏi **điểm nối là call hay execution**.

### 5.1 Tại method-call join point

Ví dụ:

~~~java
class Controller {
    private final Service service;

    Controller(Service service) {
        this.service = service;
    }

    void submit() {
        service.execute("A");
    }
}
~~~

Đây là mã minh họa, giả sử `Service` được khai báo ở nơi khác.

Tại vị trí gọi `service.execute("A")` **nằm trong** `Controller.submit()`:

- **This của mã đang thực thi tại chỗ gọi** là instance `Controller`.
- **Target của lời gọi `execute`** là instance mà `service` tham chiếu.
- **Args** chứa `"A"`.

Nếu một mô hình AOP expose cả this và target tại method-call join point, hai giá trị này thường là **hai đối tượng khác nhau**. Đây là lý do tên “đối tượng thực thi” không được tự động đồng nhất với đối tượng “bị gọi”.

### 5.2 Tại method-execution join point

Khi phần thân `ServiceImpl.execute` chạy:

~~~java
class ServiceImpl implements Service {
    @Override
    public void execute(String id) {
        System.out.println(this.getClass().getSimpleName());
    }
}
~~~

Đây là mã minh họa, giả sử giao diện `Service` tồn tại.

Ở **execution của phương thức instance**, `this` của thân phương thức là đối tượng `ServiceImpl` đang thực thi. Target của execution, nếu mô hình hỗ trợ khái niệm ấy, thông thường cũng là đối tượng đang chạy thân phương thức. Trong trường hợp gọi qua proxy hoặc công cụ tạo mã, cách mô hình hóa `this` và `target` phải xem hợp đồng cụ thể; không suy từ một biến `proxy` trong handler ra mọi execution join point.

### 5.3 Tại static method và static context

~~~java
static void refreshAll() {
    // Không có this của một instance ở thân phương thức static.
}
~~~

Vì không có `this` instance, điều kiện pointcut đòi một object executing-this có thể không khớp hoặc không hợp lệ cho điểm này, tùy mô hình. Đừng thay một `Class` object vào vị trí instance target mà không có định nghĩa rõ.

### 5.4 Bảng đối chiếu

| Loại điểm | This / executing object | Target | Args |
| --- | --- | --- | --- |
| Gọi instance method từ thân instance method khác | Instance bên gọi tại vị trí phát sinh call | Instance nhận lời gọi | Đối số truyền cho lời gọi |
| Thực thi thân instance method | Instance đang chạy thân phương thức | Thông thường chính instance đó, nếu được expose | Tham số của phần thực thi |
| Gọi instance method từ static context | Không có caller-instance `this` | Instance nhận lời gọi | Đối số |
| Gọi static method | Không có receiver instance theo nghĩa instance target | Tùy mô hình, thường không có instance target | Đối số của static method |
| Proxy invocation handler | Không suy máy móc từ `proxy` | Object nghiệp vụ do handler giữ có thể khác proxy | Các đối số handler nhận |

Bảng mô tả **ngữ nghĩa khái niệm**. Không phải mọi nền tảng đều định nghĩa mỗi hàng thành một loại join point có thể weave advice.

## 6. Context binding vào advice và quan hệ với pointcut

Có hai trách nhiệm độc lập:

1. **Pointcut selection:** quyết định **điểm/lần gọi nào** khớp.
2. **Context binding:** quyết định **những giá trị nào** được truyền vào advice sau khi khớp.

Một số mô hình pointcut có thể vừa lọc theo kiểu runtime vừa bind dữ liệu. Giả mã:

~~~text
structuralCondition = method execution of process(...)
runtimeCondition    = target is instance of SpecialService
argumentCondition   = amount > 0

pointcut = structuralCondition
           AND runtimeCondition
           AND argumentCondition

advice receives (target, amount, invocationInfo)
~~~

Đây không phải cú pháp một thư viện; phần chọn xác định tập điểm chịu tác động, phần binding cung cấp thông tin cụ thể của mỗi lần thực thi.

Ví dụ một advice đo thời gian **không cần lọc theo giá trị** nhưng vẫn có thể bind tên phương thức để đặt tên metric. Ngược lại, một pointcut chỉ khớp tham số có kiểu runtime đặc biệt cần đánh giá điều kiện động nhưng không bắt buộc advice phải ghi nội dung của tham số đó.

### 6.1 Khi nào nên chọn theo target, this hay args?

- Muốn theo dõi **mọi thao tác trên một nhóm lớp đích**: nghĩ đến target và phạm vi cấu trúc tương ứng.
- Muốn theo dõi **các điểm phát sinh từ một nhóm caller**: cân nhắc this ở loại **call** phù hợp; phải xác minh những call join point thực sự được hỗ trợ.
- Muốn đo hoặc kiểm toán **theo dữ liệu yêu cầu**: dùng args hoặc context được bind, nhưng lọc/chuyển đổi an toàn.
- Muốn áp dụng hành vi tại **phương thức được khai báo** ở interface hoặc lớp cụ thể: ưu tiên khái niệm chữ ký/declaring type; đừng thay bằng runtime type khi yêu cầu là tĩnh.
- Muốn **kiểm soát quyền**: chọn tất cả điểm cần bảo vệ rồi kiểm tra danh tính đã được xác thực và tài nguyên trong policy; không chỉ chọn các lần đã có quyền khiến những lần không đủ quyền bỏ qua kiểm tra.

## 7. Ví dụ Java 17 chạy độc lập: proxy, target, caller và args

Để thấy rõ ranh giới, ví dụ dưới sử dụng **Java dynamic proxy**. Nó thể hiện cách một lớp chặn lời gọi **qua interface** nhận `Method`, target và args. Đây **không phải hệ thống AOP chung**: lớp proxy không tự expose `this` của caller, và các dòng quan sát caller được in trực tiếp trong phương thức bên gọi để làm rõ đối tượng đang thực thi ở call site.

Lưu file `AdviceContextDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AdviceContextDemo {
    interface Task {
        String run(String id, int attempts);
    }

    static final class TaskImpl implements Task {
        @Override
        public String run(String id, int attempts) {
            System.out.println("BODY this="
                    + getClass().getSimpleName()
                    + " id=" + id + " attempts=" + attempts);
            return id + ":" + attempts;
        }
    }

    static final class Caller {
        private final Task task;

        Caller(Task task) {
            this.task = task;
        }

        String invoke(String id, int attempts) {
            System.out.println("CALLER this="
                    + getClass().getSimpleName());
            return task.run(id, attempts);
        }
    }

    static Object invokeTarget(Task target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Task weave(Task target) {
        return (Task) Proxy.newProxyInstance(
                Task.class.getClassLoader(),
                new Class<?>[] { Task.class },
                (proxy, method, args) -> {
                    Object[] actualArgs =
                            args == null ? new Object[0] : args.clone();

                    System.out.println("ADVICE methodDeclared="
                            + method.getDeclaringClass().getSimpleName());
                    System.out.println("ADVICE target="
                            + target.getClass().getSimpleName()
                            + " proxyIsTarget=" + (proxy == target));
                    System.out.println("ADVICE args="
                            + actualArgs[0] + "," + actualArgs[1]);

                    return invokeTarget(target, method, actualArgs);
                });
    }

    public static void main(String[] args) {
        TaskImpl original = new TaskImpl();
        Task task = weave(original);
        Caller caller = new Caller(task);

        System.out.println("RESULT " + caller.invoke("alpha", 2));
        System.out.println("DIRECT");
        System.out.println("RESULT " + original.run("beta", 1));
    }
}
~~~

Chạy trên JDK 17+:

~~~bash
javac AdviceContextDemo.java
java AdviceContextDemo
~~~

Stdout xác định của chương trình:

~~~text
CALLER this=Caller
ADVICE methodDeclared=Task
ADVICE target=TaskImpl proxyIsTarget=false
ADVICE args=alpha,2
BODY this=TaskImpl id=alpha attempts=2
RESULT alpha:2
DIRECT
BODY this=TaskImpl id=beta attempts=1
RESULT beta:1
~~~

### 7.1 Đọc từng dòng

**`CALLER this=Caller`**: trong thân `Caller.invoke`, `this` là instance của `Caller`. Đây là cách chương trình Java **tự in this tại call site**; dynamic proxy không tự nhận được object Caller qua một tham số `this` của handler.

**`ADVICE methodDeclared=Task`**: handler nhận `Method` được khai báo tại interface `Task`. `Method.getDeclaringClass()` chỉ nói về khai báo của `Method`, không phải lớp runtime của target.

**`ADVICE target=TaskImpl proxyIsTarget=false`**: handler đang giữ object nghiệp vụ `TaskImpl`; biến `proxy` là object đại diện khác. Trong ví dụ này, `target` được xác định **tường minh bởi closure của handler**, không phải suy ra từ tên lớp proxy.

**`ADVICE args=alpha,2`**: tham số đúng của lần gọi qua proxy gồm chuỗi `alpha` và số nguyên `2`. Vì Reflection/Proxy biểu diễn primitive qua wrapper trong `Object[]`, giá trị 2 tương ứng `Integer` tại đây.

**`BODY this=TaskImpl...`**: khi `TaskImpl.run` thực thi, `this` của thân phương thức chính là object `TaskImpl`.

**Nhánh `DIRECT`**: lời gọi trực tiếp `original.run` không đi qua proxy, nên không có dòng `ADVICE`; điều đó minh họa **độ bao phủ của cơ chế can thiệp**, không nói rằng lời gọi trực tiếp không có arguments hoặc target.

Ví dụ này không xác định syntax cho các pointcut `this`/`target`/`args` của mọi mô hình AOP. Nó chỉ minh họa ba dạng thông tin bằng chương trình Java chạy được.

### 7.2 Điều gì thay đổi khi có nhiều proxy?

Nếu một proxy bao ngoài proxy khác, “object nhận lời gọi” ở mỗi biên có thể là một proxy khác nhau. Một advice bên ngoài có thể biết loại runtime của proxy kế tiếp; một advice ở gần nghiệp vụ hơn có thể biết target gốc. **Không có một object target phổ quát độc lập với vị trí can thiệp**.

Vì thế khi test, cần ghi rõ:

- Điểm gọi nào đã được instrument?
- `this` thuộc đoạn mã nào?
- `target` trong hợp đồng công cụ chỉ đối tượng nhận ở biên nào?
- `args` là tham số trước hay sau khi một advice bên ngoài đã biến đổi chúng?
- Khi chuyển tiếp, giá trị trả về và ngoại lệ có được giữ đúng hợp đồng hay không?

## 8. Nhận ngữ cảnh ở before, after và around advice

Không phải mọi dữ liệu có thể đọc tại mọi thời điểm.

| Dạng advice | Dữ liệu có thể cần | Dữ liệu/chức năng cần lưu ý |
| --- | --- | --- |
| Before | Method signature, target/this, args trước thực thi | Chưa có kết quả của lần thực thi |
| After returning | Dữ liệu join point và kết quả thành công nếu cơ chế expose | Không chạy trên nhánh lỗi theo nghĩa after-success |
| After throwing | Dữ liệu join point và ngoại lệ nếu được expose | Không có kết quả thành công; phải truyền tiếp lỗi khi cần |
| After / finally | Dữ liệu chung và trạng thái hoàn thành phù hợp | Có thể chạy khi thành công hoặc lỗi; cleanup phải an toàn |
| Around | Context, args, tiếp tục `proceed`, kết quả hoặc ngoại lệ | Có thể chặn, biến đổi, gọi tiếp nhiều lần nếu được hỗ trợ |

**Around advice** thường có quyền tác động mạnh hơn: nó có thể chọn **không gọi tiếp** rồi trả giá trị cache hoặc ném lỗi. Khi đó một advice nằm bên trong có thể không được thực thi, và kết quả trả về có thể không xuất phát từ thân nghiệp vụ. Ngược lại, lời gọi `proceed()` có thể kích hoạt thêm các advice bên trong trước khi tới phương thức đích thật.

Khi phân tích ngữ cảnh, hãy nhớ **object target được cung cấp bởi một API không chứng minh thân phương thức gốc đã chạy**; quanh advice khác có thể đã short-circuit.

## 9. Các trường hợp nâng cao dễ nhầm

### 9.1 `this` trong advice không tự đồng nhất với `this` ở join point

Bên trong **mã của aspect/advice**, `this` của ngôn ngữ Java (nếu advice là một instance method trong một object aspect) có thể trỏ tới **instance của aspect**. Nó không tự nhiên trở thành `this` của lớp nghiệp vụ.

Nếu ngôn ngữ pointcut có biểu thức expose **this của join point**, đó là **giá trị ngữ cảnh được bind** theo hợp đồng của AOP, khác với từ khóa `this` theo cú pháp bên trong thân Java của advice.

### 9.2 Static method và không có receiver

Với `Math.abs(-3)` hoặc một phương thức static tự viết, không có receiver instance làm target giống lời gọi instance. Ngay cả khi có một `Class` object mô tả lớp chứa phương thức, nó **không tự là một target object của lời gọi static** theo cùng ý nghĩa.

### 9.3 Constructor và object initialization

Với constructor, câu hỏi “đối tượng đích tồn tại lúc nào” phụ thuộc giai đoạn: **trước khi tạo, đang khởi tạo, sau khởi tạo**. Một số mô hình AOP có join point constructor call/execution/initialization riêng. Không lấy quy tắc của method-execution instance thông thường áp dụng máy móc cho mọi giai đoạn khởi tạo.

### 9.4 Generic, overload, bridge và phương thức sinh

Một pointcut dựa trên chữ ký `process(String)` có thể khác pointcut chọn `process(Object)`. Với Java generics, compiler có thể tạo **bridge method** sau type erasure. Reflection có thể thấy cả bridge và phương thức chuyên biệt, nhưng **không thể suy** một bộ AOP sẽ expose hai execution join point hoặc luôn chạy advice hai lần.

Args runtime của `String` vẫn là giá trị chuỗi thực tế; thông tin kiểu generic như `List<String>` thường không tạo một runtime class riêng để match. Cần phân biệt **kiểu trong chữ ký**, **runtime class của đối số** và **danh mục join point của nền tảng**.

### 9.5 Self-invocation và đường gọi bỏ qua proxy

Nếu một phương thức nghiệp vụ gọi một phương thức khác trên `this` bên trong object gốc, lời gọi đó **có thể không đi qua proxy ngoài**. Advice được cài ở proxy vì thế có thể bỏ lỡ lần gọi nội bộ. Nhưng với kỹ thuật weaving ở bytecode, điểm execution tương ứng có thể vẫn được quan sát.

Điều này nhấn mạnh rằng **ngữ cảnh đúng không thay thế phạm vi can thiệp đúng**. Có thể biết một target phù hợp mà vẫn không có advice nào chạy vì đường gọi không đi qua vị trí đã được instrument.

### 9.6 Control flow, thread và tác vụ bất đồng bộ

Một advice ở điểm đồng bộ có thể đọc target và args khi lời gọi còn sống. Nếu công việc tiếp tục trên thread hoặc task khác, không mặc định rằng các giá trị context gắn với luồng sẽ tự truyền sang đó. Cần quy định cách truyền correlation ID, danh tính và những dữ liệu được phép dùng qua ranh giới bất đồng bộ.

Giữ lại một tham chiếu tới target hoặc args trong hàng đợi log có thể kéo dài vòng đời của object và làm lộ dữ liệu. Đối với hệ thống truy vết, nên chuyển dữ liệu sang một cấu trúc sự kiện tối thiểu, bất biến và được bảo vệ đúng phạm vi.

## 10. Bảo mật và tính đúng đắn khi dùng ngữ cảnh

### 10.1 Target đúng không tự chứng minh quyền

Kiểm tra `target instanceof SensitiveService` chỉ xác định loại đối tượng. Nó **không xác thực người dùng**, không xác định người ấy có quyền truy cập tài nguyên cụ thể và không ngăn đường gọi không nằm trong phạm vi advice.

Đối với chính sách bắt buộc, cần chọn đủ điểm cần bảo vệ, kiểm tra chủ thể đã được xác thực và tài nguyên thực sự, chặn trước tác dụng phụ, đồng thời kiểm chứng mọi đường vào. Không nên đưa điều kiện “người dùng đã có quyền” vào pointcut rồi vô tình để lời gọi không có quyền **bỏ qua advice kiểm tra**.

### 10.2 Arguments có thể bị sửa trong khi đang xử lý

Nếu đối tượng tham số mutable được truyền chung qua các lớp, một advice có thể đọc trạng thái trước khi nghiệp vụ thay đổi hoặc sau khi advice khác đã thay đổi. Muốn log trạng thái như lúc yêu cầu đến, phải xác định thời điểm chụp snapshot và chiến lược sao chép phù hợp.

`args.clone()` chỉ tạo **bản sao nông của mảng**. Nó không clone các object bên trong, không tự bảo vệ khỏi dữ liệu thay đổi và không giải quyết quyền truy cập.

### 10.3 Lựa chọn trường dữ liệu an toàn

Bản ghi log nên ưu tiên:

- Tên thao tác hoặc chữ ký cần thiết.
- Correlation ID/trace ID không nhạy cảm và được quản lý đúng.
- Mã phân loại trạng thái thành công/lỗi.
- Những tham số đã được whitelist, chuẩn hóa hoặc che giá trị nhạy cảm.
- Chỉ danh tính đã xác thực từ nguồn tin cậy, với phạm vi retention phù hợp.

Đừng để advice tự gọi `toString()` trên mọi target/args mà không kiểm soát. Đôi khi hành vi này đắt, đệ quy hoặc có tác dụng phụ.

## 11. Kiểm thử binding và phạm vi pointcut

Một ma trận kiểm thử tốt phải xác minh đồng thời **điểm được chọn** và **giá trị được bind**.

| Trường hợp | Args | Target | This/executing object | Điều cần kiểm chứng |
| --- | --- | --- | --- | --- |
| Gọi instance method từ một instance khác | Đúng thứ tự và giá trị | Object nhận lời gọi | Object phía caller ở điểm call | Không đánh đồng caller với receiver |
| Execution của instance method | Tham số của lần chạy | Object đang thực thi theo định nghĩa mô hình | Thường cùng object ở thân instance method | Phân biệt call/execution |
| Gọi từ static context | Vẫn có args | Đích instance nếu là instance method | Không có caller `this` | Không giả định this khác null |
| Static method | Args theo chữ ký | Không có instance target thông thường | Không có executing instance | Không gọi `getClass()` trên null |
| Lời gọi qua proxy | Args do handler nhận | Proxy hay target gốc tùy ranh giới | Caller không tự xuất hiện trong handler | Phân biệt `proxy` và `target` |
| Lời gọi trực tiếp object gốc | Có args và target | Object thật | Phụ thuộc điểm gọi | Proxy advice có thể không chạy |
| Args chứa null | Một phần tử null | Target không đổi | Không liên quan | Không cố lấy runtime class của null |
| Overload/generic | Chữ ký có thể khác | Phụ thuộc lời gọi | Phụ thuộc điểm | Bind đúng mảng args và kiểu |
| Nhiều advice | Có thể đã bị biến đổi trước khi vào lớp trong | Mỗi lớp nhìn theo ranh giới | Phụ thuộc call/execution | Không mặc định tất cả thấy cùng context |
| Chuyển sang task khác | Có thể phải truyền tường minh | Có thể chỉ truyền ID thay object | Thread context có thể đổi | Không rò dữ liệu hoặc giữ tham chiếu quá lâu |

Khi dùng framework/công cụ AOP thực tế, nên kiểm tra riêng:

1. Nó hỗ trợ **call join point**, **execution join point** hay chỉ một loại?
2. Nó định nghĩa `this`, `target`, `args` như thế nào ở mỗi loại?
3. Chữ ký được bind là từ interface, lớp khai báo hay method thực thi?
4. Có bind được **giá trị đối số** hay chỉ đọc metadata chữ ký?
5. Có khả năng **thay đổi args** khi `proceed` không, và nếu có thì cách ánh xạ thế nào?
6. Có điểm nào không có target hoặc this không?
7. Proxy, self-invocation, bridge methods và async có làm thay phạm vi không?
8. Advice có giữ nguyên kết quả, ngoại lệ và các bất biến nghiệp vụ không?

## 12. Những lỗi hiểu sai phổ biến

**Lỗi 1: Coi args là kiểu tham số được khai báo.** Args là giá trị thực tế của lần thực thi; chữ ký phương thức mô tả loại tham số tĩnh. Hai lời gọi cùng phương thức có args khác nhau.

**Lỗi 2: Coi target là một tham số bất kỳ.** Một object có thể được truyền vào như tham số `service` nhưng target của lời gọi hiện tại lại là `controller`. Target chỉ xác định theo **join point đang xét**.

**Lỗi 3: Coi this và target luôn giống nhau.** Ở method call từ một instance khác, this có thể là caller và target là receiver. Ở method execution thông thường, chúng có thể trùng. Cần biết loại điểm.

**Lỗi 4: Coi `this` trong thân advice là `this` của nghiệp vụ.** Từ khóa Java trong mã advice có thể trỏ tới aspect instance. Ngữ cảnh `this` của join point phải được expose/bind riêng.

**Lỗi 5: Coi proxy chính là object nghiệp vụ.** Runtime class của proxy có thể khác target. Lớp `Method` do proxy cung cấp cũng có thể là interface.

**Lỗi 6: Cho rằng đủ args sẽ tự khớp pointcut.** Binding không đồng nghĩa selection. Pointcut cần điều kiện chọn rõ ràng, và cơ chế weaving phải thực sự quan sát được điểm.

**Lỗi 7: Cho rằng static method có target object như instance method.** Lời gọi static không có receiver instance theo nghĩa thông thường.

**Lỗi 8: Cứ sửa mảng args là sửa được lời gọi thật.** Khả năng này phải được nền tảng định nghĩa; thay đổi bản sao mảng không tự thay đổi dữ liệu ở target.

**Lỗi 9: In hết target và args để debug.** Có thể gây rò thông tin, log quá lớn, tác dụng phụ `toString()` và rò vòng đời object.

**Lỗi 10: Thấy advice chạy qua proxy thì mọi đường gọi khác cũng chịu advice.** Direct call và self-invocation có thể đi ngoài proxy.

## 13. Tình huống tự luyện

Giả sử có:

~~~java
class Controller {
    private final Service service;

    Controller(Service service) {
        this.service = service;
    }

    void submit(String id) {
        service.save(id);
    }
}
~~~

Đây là **mảnh minh họa**, giả định `Service` có phương thức instance `save(String)`.

**Câu 1.** Tại điểm **call** của `service.save(id)`, this là gì?

**Đáp:** nếu đây là instance method call join point mà mô hình có expose this, nó là object `Controller` đang chạy `submit`.

**Câu 2.** Tại điểm **call**, target là gì?

**Đáp:** object nhận lời gọi `save`, tức đối tượng mà `service` đang tham chiếu (có thể là proxy ở ranh giới bên gọi).

**Câu 3.** Args gồm gì?

**Đáp:** giá trị hiện tại của biến `id` được truyền cho `save`, theo chữ ký của lời gọi.

**Câu 4.** Tại **execution** của thân `ServiceImpl.save`, this là gì?

**Đáp:** object `ServiceImpl` đang thực thi thân instance method, nếu cơ chế expose điểm đó.

**Câu 5.** Nếu `id == null`, có lấy `id.getClass()` để match runtime type không?

**Đáp:** không; null không có lớp runtime. Có thể vẫn có thông tin declared type `String` trong chữ ký.

**Câu 6.** Nếu `service` trỏ tới proxy và handler giữ `ServiceImpl`, `proxy.getClass()` có chắc là `ServiceImpl.class` không?

**Đáp:** không. Đó là hai object khác nhau trong ví dụ proxy thông thường.

**Câu 7.** Nếu `ServiceImpl.save` gọi `this.audit(id)` ở thân phương thức, advice chỉ có ở proxy có chắc thấy lời gọi `audit` này không?

**Đáp:** không. Lời gọi nội bộ có thể không đi qua proxy. Cần kiểm tra cơ chế can thiệp thực tế.

## 14. Tổng kết

Để nhận đúng dữ liệu trong advice, hãy luôn trả lời **bốn câu hỏi**:

1. **Đang ở loại join point nào?** `call`, `execution` hay một loại khác mà mô hình hỗ trợ?
2. **Args thuộc lời gọi nào?** Giá trị thực tế, thứ tự, nullability và loại dữ liệu của chính lần thực thi?
3. **Target là object nào?** Receiver tại ranh giới nào, proxy hay target nghiệp vụ gốc?
4. **This là object nào?** Đối tượng đang thực thi mã tại điểm nối, có thể là caller ở call join point hoặc object nghiệp vụ ở execution join point?

**Kết luận:** `args` trả lời **“truyền những gì?”**; `target` trả lời **“thao tác hướng tới object nào?”**; `this` trả lời **“mã của object nào đang được thực thi ở điểm này?”**. Khi advice phân biệt được ba nguồn ngữ cảnh, việc ghi log, truy vết, chọn pointcut và kiểm soát hành vi sẽ chính xác hơn, không bị nhầm lẫn giữa giao diện, proxy, đối tượng đích và bên gọi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-003">Phân biệt dữ liệu ràng buộc trực tiếp với tra cứu bằng phản chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt dữ liệu ràng buộc trực tiếp với tra cứu bằng phản chiếu trong AOP

## 1. Vấn đề cốt lõi: Advice lấy dữ liệu từ đâu?

Khi lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một **advice** cần dữ liệu về sự kiện đang được can thiệp để quyết định ghi log, kiểm tra, đo đạc, đổi tham số hoặc xử lý kết quả. Hai cách lấy thông tin rất dễ bị đánh đồng:

1. **Ràng buộc trực tiếp (direct binding):** thông qua **pointcut** và hệ thống tham số của advice, công cụ AOP truyền thẳng các giá trị ngữ cảnh được lựa chọn vào các biến có kiểu được khai báo. Ví dụ ràng buộc đối số String, đối tượng target hoặc exception được ném.
2. **Tra cứu qua metadata hoặc phản chiếu (reflection/introspection):** advice hoặc interceptor có một đối tượng mô tả join point, chữ ký hay Method/Class/Annotation, sau đó chủ động xem thông tin của phần tử chương trình, ví dụ tên method, kiểu tham số, annotation, nơi khai báo hoặc cấu trúc class.

Hai cách **không đối lập tuyệt đối**. Một advice có thể bind trực tiếp giá trị đối số và đồng thời đọc metadata của phương thức bằng reflection. Một cơ chế AOP cũng có thể **dùng reflection nội bộ để hiện thực việc binding**; điều quan trọng là **hợp đồng của code advice với dữ liệu** chứ không phải phỏng đoán công cụ đã sinh những instruction nào.

Cần phân biệt ba lớp dữ liệu:

- **Giá trị runtime:** object nào đang nhận lời gọi, String hiện tại là gì, exception nào vừa phát sinh, return value nào vừa được trả.
- **Join point metadata:** điểm đó là call hay execution, chữ ký được hệ thống AOP mô tả, vị trí mã hoặc ngữ cảnh thực thi được cung cấp.
- **Java reflection metadata:** Method, Constructor, Field, Class, parameter types, annotation có retention phù hợp, access modifiers...

Một object **Method** mô tả khai báo phương thức; nó **không chứa** giá trị đối số của một lần gọi cụ thể. Ngược lại, một String được bind từ **args** là giá trị tham số của **lần gọi hiện tại**, không tự nói method đó có annotation nào.

## 2. Nền tảng: Join point, pointcut, advice, binding

| Thành phần | Vai trò |
|---|---|
| **Join point** | Điểm thực thi mà mô hình AOP hỗ trợ nhận diện, ví dụ method call, method execution, constructor, field access. |
| **Pointcut** | Quy tắc chọn join point theo chữ ký, kiểu, vị trí, annotation, runtime context hoặc kết hợp của chúng. |
| **Advice** | Hành vi được áp dụng tại các join point khớp, có thể chạy trước/sau/bao quanh. |
| **Binding** | Truyền những giá trị liên quan đến join point vào tham số được khai báo trong advice. |
| **Join point descriptor** | Đối tượng/API mô tả join point, có thể cung cấp signature, args, this, target hay source location. |
| **Reflection** | API để tra cứu hoặc tương tác với khai báo class/method/field/constructor và metadata runtime. |

Trong mô hình AOP có ràng buộc dữ liệu, pointcut vừa **chọn điểm**, vừa có thể **cung cấp reference** hoặc **giá trị** cho advice. Ví dụ một selector chọn method execution có argument **String orderId**, rồi bind đúng **orderId của lượt thực thi hiện tại**.

Mô hình:

~~~text
Caller -> join point khớp pointcut
                  |
                  +-- Runtime value: orderId = "A42"
                  |         |
                  |         v
                  |   Direct binding:
                  |   advice(String orderId)
                  |
                  +-- Metadata: chữ ký và nơi khai báo
                            |
                            v
                      Join point descriptor
                            |
                            v
                       Class / Method
                       reflection lookup
~~~

Đừng đặt đồng nhất “có đối tượng join point” với “đang dùng Java reflection”. Ví dụ API kiểu **getArgs()** do một join point descriptor cung cấp là cách lấy **giá trị runtime**; còn **Method.getParameterTypes()** là cách lấy **kiểu được khai báo**. Chúng trả lời hai câu hỏi khác nhau.

## 3. Binding trực tiếp hoạt động như thế nào?

### 3.1. Ràng buộc giá trị đối số

Một mô hình AOP có pointcut designator dạng **args(...)** có thể cho advice nhận trực tiếp đối số theo **tên biến formal** và **kiểu khai báo**.

Ví dụ cú pháp minh họa theo ngôn ngữ AspectJ, không phải file Java độc lập cho javac:

~~~java
before(String orderId, int quantity):
        execution(* example.OrderService.submit(String, int))
        && args(orderId, quantity) {
    System.out.println("orderId=" + orderId + ", quantity=" + quantity);
}
~~~

Trong mô hình này:

- Pointcut chọn **execution** của phương thức phù hợp chữ ký.
- **args(orderId, quantity)** bind hai đối số tại join point vào formal parameters.
- Thân advice sử dụng các biến có tên và kiểu rõ ràng: **orderId** là String, **quantity** là int.
- Không cần gọi **getMethod(...)**, duyệt parameter array, cast thủ công hoặc suy luận đối số thứ hai thuộc kiểu nào.

Chính xác cú pháp binding, primitive boxing, phạm vi join point và khả năng kiểu tương thích phải theo quy tắc của ngôn ngữ AOP cụ thể. Ví dụ này chỉ diễn giải khả năng binding trong một ngôn ngữ có nó.

### 3.2. Ràng buộc đối tượng đang thực thi và target

Trong những mô hình có designator **this(Type)** và **target(Type)**, có thể bind reference đến đối tượng tương ứng:

~~~text
pointcut selected(current, receiver):
    method-call of Service.execute(...)
    AND this(current)
    AND target(receiver)
~~~

Đây là giả mã, không phải cú pháp chung của tất cả compiler.

- Ở **method call**, đối tượng đang thực thi mã tại điểm gọi và đối tượng target nhận lời gọi có thể khác nhau.
- Ở **method execution** của instance method, hai vai trò đó thường cùng là receiver theo mô hình AspectJ.
- Ở static context, không được giả định có một instance this/target như khi gọi method instance.

Nếu advice nhận object đã bind, nó có thể dùng API công khai của object đó mà không cần reflection. Nhưng việc bind một reference **không biến object chưa hoàn tất initialization thành an toàn để sử dụng**.

### 3.3. Ràng buộc return value và exception

Một số loại advice hỗ trợ bind giá trị từ giai đoạn hoàn tất:

~~~text
after-returning(result):
    examine return value of the selected join point

after-throwing(error):
    examine exception raised by the selected join point
~~~

Đây là giả mã trung lập công cụ. Return value chỉ có sau khi join point hoàn tất bình thường; Throwable tương ứng chỉ có ở đường ngoại lệ. **Before advice** không thể bind trước giá trị trả về tương lai như thể target đã chạy.

Đây là khác biệt quan trọng so với Method reflection: **Method.getReturnType()** có thể cho biết kiểu trả về được khai báo bất cứ lúc nào metadata truy cập được; nó **không biết giá trị trả về của một lần invocation**. Để lấy giá trị cụ thể, cần join point context ở thời điểm thích hợp.

### 3.4. Ràng buộc annotation

Một số pointcut cho bind **annotation instance** gắn với phần tử chương trình đang được chọn, chẳng hạn annotation tại method execution. Advice có thể đọc thuộc tính annotation trực tiếp qua reference được bind.

Tuy nhiên, cần phân biệt:

- Annotation trên **method khai báo ở interface**.
- Annotation trên **method implementation**.
- Annotation trên **method override**.
- Annotation trên **class** hoặc đối tượng target.
- Annotation nhìn thấy được theo **RetentionPolicy.RUNTIME** khi dùng Java reflection ở runtime.

Method annotation trong Java **không tự động được kế thừa qua overriding**. Một engine có thể chủ động tra cứu khai báo ở superclass/interface theo chính sách riêng, nhưng không được suy đó là hành vi của Java reflection trên chính Method override.

## 4. Reflection: tra cứu thông tin về khai báo

**Java reflection** cung cấp các lớp như:

- **Class<?>:** kiểu runtime, superclass, interfaces, fields, methods, annotations và các thành viên khác.
- **java.lang.reflect.Method:** tên, declaring class, parameter types, return type, modifiers, annotations; có thể gọi method bằng **invoke**.
- **Constructor<?>:** thông tin về constructor và khả năng tạo object bằng reflection.
- **Field:** thông tin về trường dữ liệu và khả năng đọc/ghi theo quyền truy cập.
- **java.lang.reflect.Parameter:** metadata của formal parameter, bao gồm tên khi metadata phù hợp được lưu.

Ví dụ:

~~~java
Method method = SomeService.class.getMethod("process", String.class, int.class);
System.out.println(method.getName());
System.out.println(method.getReturnType());
~~~

Đây là đoạn minh họa Java, cần import **java.lang.reflect.Method** và định nghĩa SomeService cùng process để chạy. Nó tra cứu **khai báo method** theo tên và **chính xác các parameter Class**, không tra cứu giá trị String/int của invocation nào.

**Reflection không tự giúp biết caller đã truyền nội dung gì trong một lần gọi thật.** Muốn lấy giá trị invocation cần code đang có argument values, API interceptor cung cấp chúng hoặc join point context tương ứng. Method object không giữ lịch sử những lần invoke.

### 4.1. getMethod và getDeclaredMethod khác nhau

- **getMethod(name, parameterTypes...)** tìm **public** method theo quy tắc API, có thể bao gồm method public được kế thừa.
- **getDeclaredMethod(name, parameterTypes...)** tìm method được **khai báo trực tiếp trong Class** được gọi, không tìm method kế thừa và không yêu cầu nó public.
- Với overload, phải truyền đúng **parameter types**, không chỉ tên method.
- Với bridge/synthetic methods do compiler sinh, một số kết quả phản chiếu có thể khiến việc lựa chọn method cần xử lý cẩn thận.

Nếu subclass không override một method public của Base, **Child.class.getMethod(...)** có thể trả một Method mà **getDeclaringClass()** là Base. Nếu Child override method đó, Method khai báo tại Child có thể khác metadata so với Method ở Base.

### 4.2. Reflection tìm được method nhưng không đồng nghĩa được phép gọi

Java có các quy tắc kiểm soát truy cập; mô-đun trong hệ thống Java hiện đại cũng có ràng buộc **module boundaries**. Có thể lấy metadata của một member nhưng vẫn không có quyền tùy ý truy cập nó. Việc gọi **setAccessible(true)** không bảo đảm sẽ thành công qua mọi module; có thể gặp **InaccessibleObjectException** hoặc lỗi truy cập.

Một advice chỉ cần metadata của public API thường không nên cố phá encapsulation bằng deep reflection. Nếu tính năng cần truy cập member không public, hãy xem xét API chính thức, module openness và giới hạn được quản lý rõ ràng.

### 4.3. Reflection cần metadata tồn tại tại runtime

Annotation muốn lấy bằng **Method.getAnnotation(...)** thường cần được giữ ở runtime bằng **RetentionPolicy.RUNTIME**. Không phải annotation nào xuất hiện ở mã nguồn cũng được Java reflection đọc thấy.

Tên tham số source của Method không luôn được giữ mặc định trong class file theo cách reflection đọc như tên gốc. Khi cần tên tham số, một trong những yếu tố liên quan là cách biên dịch, chẳng hạn flag **-parameters**. Trong AOP, đôi khi có hệ thống binding dựa tên formal parameters hoặc cần metadata riêng; phải phân biệt điều đó với kiểu được biết nhờ chữ ký bytecode.

## 5. Bảng so sánh: binding trực tiếp và reflection

| Tiêu chí | Binding trực tiếp vào advice | Tra cứu qua reflection/metadata |
|---|---|---|
| **Đối tượng dữ liệu chính** | Giá trị hoặc object của một join point đang chạy | Thông tin về method/class/field/annotation; có thể kết hợp với args runtime |
| **Hình thức truy cập** | Formal parameter có tên và type | Method, Class, Annotation, Signature, Object[] và API tra cứu |
| **Phân biệt lượt gọi** | Giá trị được bind theo mỗi invocation | Metadata Method thường dùng lại giữa nhiều invocations; args runtime phải lấy riêng |
| **Độ an toàn kiểu** | Có thể được kiểm tra ở mức ngôn ngữ AOP/compile, tùy binder | Thường cần cast, kiểm tra kiểu và xử lý lỗi runtime |
| **Phù hợp với** | Đã biết chính xác giá trị cần lấy | Logging tổng quát, kiểm tra metadata, dispatch động nhiều signature |
| **Chi phí** | Có thể được tối ưu bằng weaving | Lookup/annotation traversal/invoke có overhead riêng; cần đo thực tế |
| **Phạm vi áp dụng** | Phụ thuộc pointcut và kiểu formal tương thích | Phụ thuộc đối tượng metadata sẵn có, quyền truy cập và engine |
| **Độ linh hoạt** | Tốt cho nhu cầu cố định, tường minh | Tốt khi cần khám phá cấu trúc nhiều method khác nhau |
| **Ảnh hưởng refactor** | Đổi type/signature có thể gây lỗi biên dịch hoặc làm pointcut không khớp | String tên method, lookup và cast có thể lỗi runtime hoặc trả metadata khác |
| **Kết quả của method** | Có thể bind trong after-returning/around phù hợp | Method object chỉ biết return type; giá trị thực phải lấy từ invocation |
| **Ngoại lệ target** | Có thể bind tại after-throwing phù hợp | Method.invoke có thể bọc exception trong InvocationTargetException |
| **Bảo mật** | Không tự có quyền truy cập mọi field của object | Reflection bị giới hạn bởi access control và module boundaries |

**Không kết luận binding luôn nhanh hơn reflection một số lần cố định.** Những chương trình khác nhau có weaving strategy, chi phí dispatch, caching, hot path và workload khác nhau. Sự khác biệt an toàn nhất khi thiết kế là **mô hình dữ liệu và độ tường minh của hợp đồng**, rồi mới benchmark hiệu năng.

## 6. Ví dụ Java 17 chạy được: dữ liệu có kiểu và metadata qua Method

Ví dụ sau mô phỏng hai cách xử lý tại một biên lời gọi:

1. **TypedBoundary:** method có signature được biết trước; lấy trực tiếp **String text**, **int count** và **target** bằng các tham số Java có kiểu.
2. **ReflectiveBoundary:** dùng **JDK dynamic proxy**; handler nhận **Method** và **Object[] args**, tra method implementation và annotation bằng reflection, rồi gọi target.

Đây là **chương trình Java độc lập để hiểu sự khác biệt dữ liệu**, không phải một aspect được dệt bằng compiler AOP. Đường TypedBoundary giống về **giao diện dữ liệu tường minh** với direct binding nhưng là decorator Java viết tay; đường proxy biểu diễn introspection runtime.

Tạo file **AdviceDataDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AdviceDataDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Tracked {
        String value();
    }

    interface Formatter {
        String format(String text, int count);
    }

    static final class FormatterImpl implements Formatter {
        @Override
        @Tracked("format-impl")
        public String format(String text, int count) {
            System.out.println("core: format");
            return text.repeat(count);
        }
    }

    static final class TypedBoundary implements Formatter {
        private final Formatter target;

        TypedBoundary(Formatter target) {
            this.target = target;
        }

        @Override
        public String format(String text, int count) {
            System.out.println("bound: text=" + text + ", count=" + count
                    + ", target=" + target.getClass().getSimpleName());
            return target.format(text, count);
        }
    }

    static Formatter reflectiveBoundary(Formatter target) {
        return (Formatter) Proxy.newProxyInstance(
                Formatter.class.getClassLoader(),
                new Class<?>[]{Formatter.class},
                (proxy, method, args) -> {
                    System.out.println("reflect: declaring="
                            + method.getDeclaringClass().getSimpleName()
                            + ", args=" + args.length);

                    Method implementationMethod = target.getClass()
                            .getMethod(method.getName(), method.getParameterTypes());
                    Tracked marker = implementationMethod.getAnnotation(Tracked.class);
                    System.out.println("reflect: marker="
                            + (marker == null ? "none" : marker.value()));

                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        Formatter core = new FormatterImpl();
        Formatter typed = new TypedBoundary(core);
        Formatter reflected = reflectiveBoundary(core);

        System.out.println("typed result=" + typed.format("ha", 2));
        System.out.println("reflect result=" + reflected.format("go", 3));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AdviceDataDemo.java
java AdviceDataDemo
~~~

**Stdout chính xác:**

~~~text
bound: text=ha, count=2, target=FormatterImpl
core: format
typed result=haha
reflect: declaring=Formatter, args=2
reflect: marker=format-impl
core: format
reflect result=gogogo
~~~

### 6.1. Nhánh typed đang nhận dữ liệu gì?

- Tham số **text** là String ngay trong signature của method **format**.
- Tham số **count** là int, không cần cast từ **Object[]**.
- **target** là reference kiểu Formatter được truyền vào constructor TypedBoundary.
- Sau khi ghi nhận, code gọi **target.format(text, count)** qua interface có kiểu.

Trong một advice AOP thực, code có thể đạt mức rõ ràng tương tự bằng pointcut **args(text,count)** và **target(...)** được bind. Nhưng ví dụ dùng **decorator viết tay** để chạy chỉ với JDK, chứ không khẳng định đó là direct binding do một weaver tạo tự động.

### 6.2. Nhánh reflection đang thấy những đối tượng nào?

InvocationHandler nhận **Method method** là phương thức giao diện Formatter mà proxy đang thực hiện. Vì vậy:

~~~text
reflect: declaring=Formatter
~~~

Nó không tự nói method được khai báo trực tiếp tại implementation, dù target runtime là FormatterImpl. Để đọc annotation đã được đặt lên method triển khai, handler cố ý tìm:

~~~java
Method implementationMethod = target.getClass()
        .getMethod(method.getName(), method.getParameterTypes());
~~~

Sau đó lấy annotation **Tracked** có runtime retention. Khi phương thức triển khai có **@Tracked("format-impl")**, log là:

~~~text
reflect: marker=format-impl
~~~

Nếu chỉ tìm annotation trên Method đại diện cho interface, có thể không thấy annotation được đặt **chỉ ở implementation**. Đây là ví dụ về sự khác nhau giữa **Method ở biên proxy** và **Method chứa metadata nghiệp vụ**.

### 6.3. Nhánh reflective gọi target như thế nào?

Handler dùng **method.invoke(target,args)** và truyền những đối số runtime mà proxy nhận được. Nếu phương thức gốc ném exception, Java reflection có thể bọc nó trong **InvocationTargetException**; code bắt và ném lại **getCause()** để tránh chuyển một exception bọc thành lỗi mới ngoài ý định.

Ví dụ hiện tại thành công, nên cả nhánh typed và reflective đều trả về String từ Core. Chúng không chứng minh công cụ AOP bất kỳ luôn dùng reflection invocation; bytecode weaving có thể tạo mã thực thi khác mà vẫn cung cấp metadata tương đương cho advice.

### 6.4. Hai đường không tương đương về bao phủ

TypedBoundary và ReflectiveBoundary được đưa vào biến **typed** và **reflected**. Nếu caller giữ reference **core** rồi gọi trực tiếp, cả hai lớp bao ngoài đều bị bỏ qua. Một bytecode weaver gắn advice trực tiếp vào execution của implementation có thể quan sát những đường khác, tùy phạm vi weave và loại join point.

Đừng dùng việc example handler nhận Method để kết luận AOP là “Java reflection tự động”. AOP mô tả **pointcut và lời khuyên tại join point**; reflection chỉ là một kỹ thuật lấy metadata hoặc dispatch trong số các kỹ thuật có thể dùng.

## 7. Khi nào đọc thông tin từ join point descriptor?

Một số mô hình AOP cung cấp đối tượng mô tả join point. Ví dụ các API phổ biến về mặt khái niệm:

~~~text
joinPoint.getKind()          -> loại join point
joinPoint.getSignature()     -> mô tả chữ ký
joinPoint.getArgs()          -> Object[] đối số runtime nếu được hỗ trợ
joinPoint.getThis()          -> đối tượng đang thực thi nếu có
joinPoint.getTarget()        -> đối tượng đích nếu có
joinPoint.getSourceLocation()-> vị trí source nếu metadata sẵn có
~~~

Đây là **minh họa dạng API**, không phải một interface Java độc lập được khai báo trong bài. Tên method trên một công cụ cụ thể có thể khác và metadata có thể thiếu ở một số loại join point.

Với một API như AspectJ **thisJoinPoint**, thông tin được cung cấp không đồng nhất với Java reflection:

- **getArgs()** trả **giá trị đối số runtime** của join point được mô tả. Đây là thông tin invocation.
- **getSignature()** trả **mô tả chữ ký join point**, không phải lúc nào cũng là Method reflection object.
- Một cơ chế lấy **Method** từ chữ ký có thể chỉ dùng được cho đúng loại join point và đúng kiểu signature được hỗ trợ.
- **getThis()** và **getTarget()** biểu thị vai trò runtime ở join point; không phải truy vấn tất cả instance của một class.

Chỉ cần tên method và argument count cho logging tổng quát? Descriptor có thể đủ, chưa cần thực hiện Java reflection để tìm lại Method. Cần đọc annotation trên một implementation method? Có thể phải kết hợp metadata do engine cung cấp với Java reflection hoặc API annotation binding tương ứng.

### 7.1. Static information và dynamic information

| Dữ liệu | Thường có thể tách khỏi từng invocation? |
|---|---|
| Tên method, parameter types được khai báo | Có thể ổn định trên nhiều lượt của cùng điểm |
| Declaring class/chữ ký join point | Thường ổn định theo loại điểm đã được weave |
| Giá trị String/number trong argument | Không; thay đổi theo lượt gọi |
| Target runtime subtype | Có thể thay đổi theo lượt gọi |
| Return value | Chỉ có tại đường hoàn tất thích hợp |
| Exception object | Chỉ có khi join point thất bại |
| Annotation trên Method được chọn | Metadata tương đối ổn định theo khai báo nhưng phương thức được chọn phải chính xác |

Nhiều API AOP cho khả năng lấy một **static part** của join point để tái sử dụng thông tin không đổi giữa invocations. Điều này hữu ích cho ghi trace nhẹ hoặc caching metadata. Nhưng không được cache **args runtime** như thể chúng là static metadata.

## 8. Chọn binding hay reflection theo nhu cầu

### 8.1. Chọn binding khi giá trị cần lấy đã biết

Ví dụ advice chỉ cần **orderId** và **quantity** của đúng một method hoặc họ method có hợp đồng tương ứng.

Ưu điểm:

- Biến có tên, type và mục đích rõ.
- Ít Object[] access, cast và lookup động trong thân advice.
- Dễ gắn vào test vì dữ liệu cần dùng là input tường minh.
- Compiler hoặc AOP compiler có thể phát hiện một số mismatch về signature và binding.

Bất lợi:

- Advice gắn tương đối chặt với chữ ký hoặc pointcut cụ thể.
- Refactor thay danh sách/thứ tự tham số có thể cần cập nhật pointcut.
- Một advice phải hỗ trợ rất nhiều signature khác nhau có thể trở nên khó khai báo formal parameters chung.

### 8.2. Chọn metadata/reflection khi cần khám phá tổng quát

Ví dụ một aspect logging chung cho hàng trăm method có chữ ký khác nhau muốn ghi:

- Tên method và declaring type.
- Những annotation được gắn tại method implementation.
- Kiểu tham số được khai báo.
- Một dấu nhận diện cho nhóm operation dựa trên metadata.
- Thông tin về một số argument theo policy mà không cần biết trước signature.

Ưu điểm:

- Một cơ chế chung có thể áp dụng nhiều method với signature khác nhau.
- Phù hợp hạ tầng logging, tracing, metric, kiểm tra cấu hình hoặc kiểm toán metadata.
- Cho phép xử lý annotation-driven behavior khi không thể bind một formal type chung cho mọi method.

Bất lợi:

- Có thể phải cast và xử lý type errors ở runtime.
- Lookup string-based dễ nhầm overload, declaring class, bridge/synthetic method.
- Reflection có vấn đề quyền truy cập và module boundaries.
- Nếu dùng Method.invoke, phải xử lý exception wrapper, return type, primitive boxing.
- Quá rộng dễ thu thập dữ liệu nhạy cảm ngoài phạm vi dự kiến.

### 8.3. Kết hợp khi có hai nhu cầu

Một thiết kế cân bằng:

~~~text
Pointcut chọn đúng các method có @Tracked
     |
     +--> Bind target và argument quan trọng có type
     |
     +--> Đọc annotation/metadata chỉ khi cần
     |
     +--> Advice thực hiện logging đã giới hạn dữ liệu
~~~

Có thể tận dụng annotation binding nếu công cụ hỗ trợ để tránh tự lookup Method ở mỗi lần. Ngược lại, với một handler thực sự tổng quát không có formal signature ổn định, nhận Method và Object[] là lựa chọn hợp lý nếu code kiểm tra kiểu chặt và thực sự cần.

## 9. Rủi ro: Method ở interface khác Method ở implementation

Giả sử có:

~~~java
interface Operation {
    void execute();
}

class OperationImpl implements Operation {
    @Override
    @Audited
    public void execute() {
        // Nghiệp vụ
    }
}
~~~

Đây là **mã minh họa**, annotation Audited cần được định nghĩa để biên dịch. Một proxy chặn qua interface có thể nhận Method của **Operation**, còn annotation chỉ gắn với **OperationImpl.execute()**. Nếu gọi **method.getAnnotation(Audited.class)** trên Method của interface, kết quả có thể là null.

Cách xử lý tùy chính sách:

1. Nếu annotation trên interface chính là **hợp đồng**, tìm tại interface.
2. Nếu annotation trên implementation chính là **hợp đồng**, tìm method implementation tương ứng.
3. Nếu cần tìm theo cả interface và superclass, phải đặc tả **thứ tự ưu tiên khi metadata xung đột**.
4. Nếu subclass override method, method annotation không tự gắn vào override; policy có muốn nhận annotation từ superclass không?
5. Nếu method được tạo bridge bởi compiler, cần xét cách engine/lookup liên kết bridge với implementation.

Không có quy tắc chung “annotation sẽ tự được thừa kế” khi chỉ gọi Java Method.getAnnotation. Những giải pháp duyệt hierarchy thủ công hoặc metadata resolver riêng là **cơ chế bổ sung**, không phải mặc định của Java reflection.

## 10. Bridge methods, overload và generic type erasure

### 10.1. Overload có cùng tên

~~~java
void save(String value);
void save(String value, int version);
~~~

Đây là khai báo minh họa. **getMethod("save", String.class)** chọn overload thứ nhất, còn **getMethod("save", String.class, int.class)** chọn overload thứ hai. Chỉ log method name rồi chọn theo tên không đủ phân biệt.

Khi bind argument cho advice, pointcut cũng phải biết đang chọn overload nào nếu chúng có semantics khác. Một rule ghi log mọi method tên save có thể khớp cả hai nhưng không chắc mọi method có chung danh sách formal parameters để bind.

### 10.2. Generics và bridge

Java generics sử dụng **type erasure** ở bytecode. Khi triển khai hoặc override generic method, compiler có thể sinh **bridge method** (synthetic) để duy trì dispatch và tương thích. Reflection có API như **isBridge()** và **isSynthetic()** để nhận diện một số method như vậy.

Một công cụ AOP cấp cao có thể tránh những duplicate events theo cách riêng; một công cụ instrument bytecode ở mức thấp có thể nhìn nhiều method mà Java source chỉ hiển thị một. Khi pointcut và reflection cùng tham gia, cần test:

- Advice có chạy đúng một lần cho một thao tác nghiệp vụ không?
- Method object nào được dùng để tìm annotation?
- Metadata đang lấy từ bridge hay method implementation?
- Generic signature được diễn giải theo source type hay erased type?

Nếu reflection lookup trả Method không đúng khai báo mong muốn, bạn có thể đọc sai annotation hoặc kiểm tra kiểu không đúng. Đừng dựa vào tên method và số parameter làm khóa duy nhất khi có overload/bridge.

## 11. Null, primitive và mảng đối số

Binding trực tiếp với **int count** khác với đọc phần tử **args[1]** là Object rồi cast thành int:

~~~java
int count = (int) args[1];
~~~

Đoạn Java minh họa trên chỉ đúng nếu phần tử là wrapper tương thích để unbox, ví dụ Integer, và không phải null. Nếu null, có thể gặp NullPointerException; sai kiểu có thể gây ClassCastException.

Một số điểm cần kiểm tra:

- **Object[]** trong InvocationHandler có thể **null** nếu method không có đối số; không được luôn giả định **args.length** hợp lệ.
- Với method có đối số primitive, reflection/proxy dùng boxed values theo quy tắc API.
- Với varargs, tham số cuối có thể là array theo representation runtime, không tự là nhiều vị trí riêng.
- Giá trị object trong mảng có thể là subtype so với type được khai báo.
- Nếu mảng chỉ để quan sát, sửa phần tử của mảng không tự đồng nghĩa công cụ đã thay đối số được truyền tiếp cho target; khả năng thay argument phụ thuộc API proceed/invoke.

Binding có kiểu giảm nhu cầu cast thủ công nhưng **không tự thay thế kiểm tra null và domain invariants**. Một String được bind vẫn có thể null nếu method contract cho phép.

## 12. Hiệu năng: tránh giả định tuyệt đối

Một lời khuyên thường thấy là “binding luôn nhanh, reflection luôn chậm”. Điều này đơn giản hóa quá mức:

- **Direct binding** có thể được dệt thành đường truy cập ngắn đến argument/object.
- **Join point introspection** có thể tạo Object[] hoặc đối tượng mô tả, nhưng một số dữ liệu có thể được tối ưu hoặc tái sử dụng.
- **Reflection lookup** có thể tốn chi phí khi liên tục tìm Method/annotation, nhưng lookup được cache có thể giảm overhead.
- **Method.invoke** có chi phí và semantics khác gọi trực tiếp; JVM và phiên bản runtime có thể tối ưu ở mức khác nhau.
- **Advice làm network I/O hoặc ghi log đồng bộ** có thể chiếm áp đảo mọi chênh lệch binding/reflection.

Định hướng thiết kế:

1. Khi chỉ cần một vài argument đã biết, ưu tiên **binding có kiểu** nếu engine hỗ trợ.
2. Khi chỉ cần method name, dùng **metadata join point** sẵn có thay vì tìm lại Method theo tên nếu thông tin đã đủ.
3. Chỉ dùng Java reflection khi cần API thông tin hoặc invocation không được binding/descriptor cung cấp.
4. Nếu metadata là bất biến theo method, cân nhắc cache có vòng đời phù hợp. Không cache một giá trị argument của lượt gọi trước rồi tái sử dụng ở lượt sau.
5. Benchmark trên workload và cấu hình weaving/proxy thực tế; kiểm tra cả allocation, lock contention, GC, I/O và p95/p99 latency.

Không cache Method/metadata ở một static map không giới hạn trong hệ thống có class loader được tạo/xóa động mà chưa đánh giá rủi ro giữ reference khiến loader không giải phóng được.

## 13. Bảo mật và quyền truy cập metadata

Reflection có thể làm code nhìn thấy rất nhiều chi tiết về class. Binding cũng có thể làm advice nắm reference đến object và argument nhạy cảm. Hai cơ chế không cấp giấy phép thu thập toàn bộ dữ liệu.

Các rủi ro:

- Log **toString()** của một object có thể lộ token, mật khẩu, khóa hoặc thông tin cá nhân.
- Dùng reflection đọc tất cả field có thể vượt quá mục đích kiểm toán.
- Khi cố truy cập member không public, quy tắc encapsulation của Java và module system có thể chặn hành động.
- Annotation chứa cấu hình có thể cũng là thông tin cần bảo vệ.
- Advice lưu target trong collection sống lâu có thể tạo memory leak và giữ dữ liệu nhạy cảm ngoài vòng đời dự kiến.

Thực hành phù hợp là **data minimization**: chỉ bind hoặc tra cứu đúng giá trị cần cho chính sách, redaction các trường nhạy cảm, nêu rõ nơi và thời gian giữ dữ liệu, và kiểm thử trên đường thành công/lỗi.

## 14. Điều kiện kiểu và thời điểm binding

### 14.1. Type pattern không phải kiểm tra object runtime

Một biểu thức chọn **execution** theo chữ ký có thể match dựa type khai báo; còn điều kiện dạng **target(Type)** ở mô hình hỗ trợ có thể phụ thuộc **runtime receiver**. Hai điều kiện không luôn tương đương khi có kế thừa và override.

Ví dụ:

~~~java
Formatter service = new FormatterImpl();
service.format("x", 2);
~~~

Kiểu tĩnh của biến là Formatter; runtime target là FormatterImpl. Một advice bind object target có thể dùng interface Formatter hoặc kiểu rộng hơn; một đoạn reflection tìm implementation method qua target.getClass() có thể trả metadata thuộc FormatterImpl. Nhưng ở một call join point, signature được engine mô tả có thể phản ánh góc nhìn của caller/interface.

Đừng đoán declaring class dựa duy nhất vào runtime getClass() hoặc ngược lại. Hãy xác định **loại join point** và **đối tượng metadata** được engine cung cấp.

### 14.2. Binding diễn ra theo invocation

Giá trị được bind từ args/this/target tương ứng với **lượt thực thi cụ thể**. Nếu cùng một method gọi hai lần với argument khác nhau, binding sẽ nhận giá trị khác nhau.

Ngược lại, Method reflection object có thể được dùng cho nhiều lần gọi cùng method với các argument khác nhau. Nó không chứa “argument trước đó” hay “result của lần cuối”. Cố dùng Method metadata để suy ra giá trị runtime là sai mô hình.

### 14.3. Không có target ở mọi loại join point

Static method execution, static initialization và các điểm đặc thù có thể không có receiver instance. Một advice bind target cho những join point đó có thể không khớp hoặc không hợp lệ tùy ngôn ngữ.

Tương tự, constructor hoặc initialization join point có thể có đối tượng đang được xây dựng nhưng trạng thái chưa hoàn thiện; reflection để đọc tất cả field lúc đó đặc biệt nguy hiểm. Luôn đánh giá khả năng tồn tại của this/target và invariant trước khi dereference.

## 15. Exception: binding lỗi gốc so với InvocationTargetException

Giả sử target ném **IllegalArgumentException**. Nếu dùng một **after throwing advice** và engine hỗ trợ **throwing(exception)** binding, advice có thể nhận Throwable do join point báo cáo theo semantics của engine.

Nếu dùng Java Method.invoke:

~~~java
try {
    return method.invoke(target, args);
} catch (java.lang.reflect.InvocationTargetException exception) {
    throw exception.getCause();
}
~~~

Đoạn minh họa thuộc thân phương thức có return type tương thích, không phải một chương trình độc lập. Reflection bọc lỗi do target ném trong InvocationTargetException; nếu không tách cause, exception mà interceptor/caller thấy có thể khác với lỗi nghiệp vụ ban đầu.

Hai cơ chế có **ranh giới exception** khác nhau. Không nên so sánh chỉ dựa tên Throwable mà không xác định exception đến từ advice, target, reflection invocation hay proxy wrapping.

Với JDK dynamic proxy, nếu handler ném checked exception không phù hợp với những loại exception method interface khai báo, caller có thể nhận một wrapper như **UndeclaredThrowableException**. Test exception contract quan trọng không kém test return value.

## 16. Annotation binding và reflection: chọn đúng nguồn sự thật

Giả sử có chính sách quan sát bằng annotation:

~~~text
@Tracked(level = "INFO")
public void execute()
~~~

Điều này chỉ là ví dụ metadata, chưa định nghĩa đầy đủ một chương trình Java.

Bạn có hai cách nhìn:

**Cách A — Bind trực tiếp annotation từ pointcut:** nếu engine có designator và binding phù hợp, dùng annotation gắn với **join point được chọn**. Ưu tiên cách này khi semantics annotation của engine trùng chính xác policy muốn áp dụng.

**Cách B — Tìm annotation bằng Java reflection:** lấy Method phù hợp, gọi getAnnotation hoặc các API liên quan. Cần tự trả lời Method đó thuộc interface, implementation, superclass hay bridge. Nếu cần tìm nhiều cấp, phải chủ động thiết kế resolver cùng precedence.

Không nên trộn hai cách mà không kiểm tra sự tương đồng. Một engine có thể định nghĩa annotation pointcut theo execution declaration, trong khi code reflection lại đọc interface Method; kết quả khác nhau là có thể dự đoán, không mặc định do một trong hai bị lỗi.

Khi policy muốn đọc annotation ở implementation, test ít nhất các ca:

| Trường hợp | Điều cần kiểm tra |
|---|---|
| Annotation chỉ ở interface | Reflection trên implementation Method có nhìn thấy không? |
| Annotation chỉ ở implementation | Method tại proxy handler thuộc loại nào? |
| Subclass override không lặp annotation | Policy có muốn áp dụng metadata ở base không? |
| Subclass kế thừa method không override | getDeclaringClass() là ai? |
| Bridge method từ generics | Method nào thực sự mang annotation? |
| Annotation có SOURCE/CLASS retention | Java runtime reflection có đọc thấy không? |

## 17. Tương tác với around advice

Around advice có thể cần cả **argument values** lẫn **return value**. Với engine có hỗ trợ thay argument khi proceed:

~~~text
around join point:
    boundInput = arguments at this invocation
    revisedInput = normalize(boundInput)
    raw = proceed(revisedInput)
    return transformResult(raw)
~~~

Đây là giả mã. Nếu API proceed yêu cầu truyền argument mới, việc chỉ sửa một biến local của advice không tự đổi input của target. Nếu code mutate object truyền theo reference, target có thể nhìn thấy side effect nhưng đó vẫn khác “thay reference argument”.

Reflection-based interceptor có thể thực hiện một việc tương tự bằng Method.invoke(target, newArgs), như ví dụ Java ở phần 6 dùng method.invoke(target,args). Nhưng nghĩa của **proceed** trong một woven join point không phải luôn bằng **Method.invoke**, đặc biệt khi quanh nó còn nhiều advice và thứ tự precedence.

Với before advice đơn thuần, chưa có return value của target để bind; với after returning có giá trị return nhưng không tự có quyền replay target; với around, việc gọi tiếp và thay output phụ thuộc contract cụ thể.

## 18. Những lỗi thường gặp

**Lỗi 1 — Nghĩ Method object chứa giá trị của invocation.** Method mô tả khai báo; đối số runtime phải lấy ở join point/interceptor đang xử lý lượt gọi.

**Lỗi 2 — Tưởng mọi introspection đều là Java reflection.** Join point descriptor có thể cung cấp signature và args; không cần Method reflection cho mọi câu hỏi.

**Lỗi 3 — Dùng Object[] quá nhiều dù formal binding đủ.** Cast, index và lookup thủ công làm advice khó đọc và dễ hỏng khi signature đổi.

**Lỗi 4 — Cho rằng typed binding tự động thay đổi tham số target.** Binding cung cấp giá trị cho advice; muốn thay argument được chuyển tiếp phải có API và hợp đồng hỗ trợ.

**Lỗi 5 — Dùng Method của interface để tìm annotation chỉ gắn ở implementation.** Có thể nhận null dù implementation đã được đánh dấu.

**Lỗi 6 — Cho rằng method annotation truyền tự động sang override.** Java không áp dụng @Inherited cho method overriding theo cách đó; phải xác định policy tra cứu hierarchy nếu cần.

**Lỗi 7 — Tra Method chỉ theo tên.** Overload, primitive types, arrays, bridges và generics có thể làm tra cứu sai.

**Lỗi 8 — Không unwrap InvocationTargetException.** Caller nhận wrapper thay vì lỗi gốc, khiến xử lý exception sai.

**Lỗi 9 — Dùng deep reflection bừa bãi.** JPMS/access control có thể chặn, đồng thời vượt quá yêu cầu bảo mật.

**Lỗi 10 — Cache cả Object[] args của invocation.** Giá trị runtime thay đổi theo lượt; cache nhầm có thể làm rò dữ liệu request hoặc báo sai.

**Lỗi 11 — Cho rằng getArgs() cung cấp tên tham số source.** Nó cung cấp giá trị; tên source phụ thuộc loại metadata/compiler/engine.

**Lỗi 12 — Benchmark mà bỏ qua I/O.** So sánh chỉ chi phí lookup nhưng advice thực tế làm network/log có thể dẫn đến kết luận hiệu năng vô nghĩa.

**Lỗi 13 — Tưởng dynamic proxy bao phủ mọi method.** Caller đi thẳng target, self-invocation hoặc static/private/constructor có thể không đi qua interceptor; khả năng khác nếu dùng bytecode weaving.

## 19. Quy trình lựa chọn và kiểm chứng

### Bước 1 — Phân loại dữ liệu cần lấy

Hãy viết ra các trường thật sự cần:

- **Giá trị của một argument cụ thể:** ưu tiên bind trực tiếp nếu chữ ký đã biết.
- **Đối tượng target hoặc this:** dùng binding hoặc context do engine hỗ trợ.
- **Tên method/chữ ký:** thử metadata từ join point descriptor.
- **Annotation trên implementation:** kiểm tra engine có binding phù hợp không; nếu không, dùng reflection với resolver chính xác.
- **Return value/Throwable:** dùng advice loại thích hợp, không tra Method để tìm giá trị invocation.

### Bước 2 — Xác định join point kind

Call và execution có thể cung cấp chữ ký từ những góc nhìn khác nhau. Trong đa hình, call site giữ kiểu tĩnh còn execution chọn triển khai thật; reflection ở proxy có thể là Method của interface. Đây là tiền đề chọn đúng declaring class.

### Bước 3 — Kiểm tra kiểu và null

Với binding trực tiếp, xác minh formal type tương thích; với Object[] và reflection, kiểm tra array null/length, index, primitive wrapper, varargs, nullable reference.

### Bước 4 — Kiểm tra nguồn metadata

Khai báo ở interface hay implementation? Annotation retention? Superclass hoặc override? Bridge method? Có đủ quyền truy cập trong module không?

### Bước 5 — Kiểm tra cơ chế áp dụng

Code gốc có nằm trong phạm vi weaving không? Caller có đi qua proxy không? Một pointcut chọn execution có được engine hỗ trợ không? Binding chỉ tồn tại nếu join point thật được chọn.

### Bước 6 — Thử đường thành công và thất bại

Test dữ liệu đầu vào đúng kiểu, sai kiểu (nếu API cho phép), null, method không có args, overload, target throw, annotation thiếu, không tìm được Method và lỗi access.

### Bước 7 — Kiểm tra nonfunctional

Đo allocation, lookup cost, số lần advice được kích hoạt, memory retention, khả năng lộ dữ liệu và tác động concurrency. Tối ưu sau khi biết thực tế workload.

## 20. Ma trận kiểm thử ngắn

| Trường hợp | Kỳ vọng cần xác minh |
|---|---|
| Advice bind String và int | Lấy đúng giá trị từng invocation, không lệch index |
| Method có 0 tham số trong proxy | Handler không dereference Object[] null |
| Method overload cùng tên | Lookup chọn đúng parameter Class[] |
| Annotation chỉ ở implementation | Resolver đọc đúng Method chứa annotation |
| Override method | Không giả định method annotation được kế thừa |
| Method kế thừa | Declaring class có thể là superclass |
| Generics tạo bridge | Không gây event trùng hoặc đọc annotation sai |
| Target throw | Lỗi gốc được quan sát/lan truyền đúng |
| Primitive argument/result | Boxing/unboxing hợp lệ |
| Lời gọi không qua proxy | Không kỳ vọng proxy handler chạy |
| Static call hoặc constructor | Không bind this/target khi mô hình không hỗ trợ |
| Concurrent invocations | Không cache nhầm args/result giữa các thread |
| Hot path có reflection | Đo bằng benchmark và workload thích hợp |
| Dữ liệu nhạy cảm | Chỉ log những trường được phép |

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Advice đã bind biến String orderId từ args. Có cần dùng Method.getParameterTypes để biết nội dung String orderId không?

**Trả lời:** Không. Giá trị được bind trực tiếp; parameter types chỉ mô tả kiểu khai báo.

**Câu 2.** Method.getReturnType trả int. Có biết lần gọi trước đã trả 42 không?

**Trả lời:** Không. Return type là metadata tĩnh; giá trị của lần gọi là dữ liệu động cần được thu ở return boundary.

**Câu 3.** JDK proxy handler nhận Method của Formatter interface nhưng annotation chỉ ở FormatterImpl.format. Tại sao handler có thể không thấy annotation nếu đọc Method trực tiếp?

**Trả lời:** Vì đó là hai khai báo method khác nhau; annotation ở implementation không tự xuất hiện trên interface Method.

**Câu 4.** Chỉ cần method name và kind cho trace, có nhất thiết phải dùng Java reflection?

**Trả lời:** Không. Join point descriptor của một số mô hình AOP đã có thể cung cấp thông tin cần.

**Câu 5.** Có thể dùng Object[] args từ lượt A làm thông tin cho lượt B trên cùng Method object không?

**Trả lời:** Không. Args thuộc từng invocation, có thể khác nhau ở mỗi lần gọi và cần được xử lý/cất giữ an toàn.

**Câu 6.** Direct binding có bảo đảm target luôn nhận tham số mới nếu advice gán lại biến không?

**Trả lời:** Không. Binding cho advice thấy giá trị; việc thay argument khi tiếp tục đòi hỏi cơ chế tương ứng và có thể không được hỗ trợ.

**Câu 7.** Một method reflection có thể được tìm thấy nhưng invoke không thành công không?

**Trả lời:** Có. Access control, module boundary, target/arguments không tương thích hoặc lỗi trong target đều có thể làm invocation thất bại.

**Câu 8.** Advice chỉ cần annotation của join point được chọn. Có thể ưu tiên binding annotation không?

**Trả lời:** Có nếu engine hỗ trợ và semantics của annotation pointcut khớp chính sách. Nếu phải tìm theo interface, implementation hoặc override hierarchy khác với engine, cần resolver được xác định rõ.

## 22. Tóm tắt mô hình dữ liệu

~~~text
                  JOIN POINT THỰC THI
                          │
            ┌─────────────┴──────────────┐
            │                            │
            ▼                            ▼
    DỮ LIỆU RUNTIME               METADATA VỀ ĐIỂM
    args / this / target           signature / kind / class
    return / exception            annotation / declaring method
            │                            │
            ▼                            ▼
     DIRECT BINDING                INTROSPECTION API
     formal parameters             join point descriptor
     được khai báo kiểu             hoặc Java reflection
            │                            │
            └─────────────┬──────────────┘
                          │
                          ▼
                       ADVICE
                          │
                          ▼
            HÀNH VI XUYÊN CẮT CÓ KIỂM SOÁT
~~~

**Ghi nhớ:**

1. Binding trực tiếp dùng **giá trị thật của lượt join point hiện tại** qua formal parameters mà pointcut/advice hỗ trợ; ưu tiên khi biết rõ dữ liệu nào cần dùng.
2. Reflection khám phá **cấu trúc và metadata của chương trình**, và có thể invoke method, nhưng không tự biết giá trị argument/return của một lượt thực thi nếu không có ngữ cảnh invocation.
3. API mô tả join point nằm **giữa hai khái niệm**: nó có thể cung cấp cả metadata lẫn dữ liệu invocation, không nhất thiết đồng nghĩa với Java reflection.
4. Chọn đúng declaring Method, loại join point, thứ tự exception, quyền truy cập và phạm vi weaving/proxy quan trọng hơn việc quyết định công cụ nào “tốt hơn” một cách tuyệt đối.

## Tài liệu đối chiếu

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): pointcut, advice binding, this/target/args, annotation pointcuts và join point context trong một ngôn ngữ AOP cụ thể.
- [Java SE 17 — java.lang.reflect.Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): introspection method, getParameterTypes, getAnnotation, invoke và exception wrapper.
- [Java SE 17 — java.lang.reflect.InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): object Method và Object[] args được chuyển vào JDK dynamic proxy handler.
- [Java Language Specification, Java SE 17 — Annotations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html): metadata annotation, retention và các giới hạn kế thừa theo ngữ nghĩa Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-004">Giá trị null, kiểu nguyên thủy và chuyển đổi kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị null, kiểu nguyên thủy và chuyển đổi kiểu trong dữ liệu của advice (AOP)

Khi một **advice** nhận dữ liệu tại điểm can thiệp (*join point*) trong lập trình hướng khía cạnh (**Aspect-Oriented Programming, AOP**), dữ liệu có thể xuất hiện dưới nhiều hình thức: đối số tham chiếu là `null`, tham số khai báo `int` nhưng được biểu diễn thành `Integer` trong một mảng `Object[]`, một phương thức trả về `void`, một giá trị có kiểu runtime khác kiểu khai báo hoặc một ngoại lệ được bọc qua reflection. Nếu advice tự ép kiểu mà không phân biệt các trường hợp này, nó có thể thất bại **trước khi** phương thức nghiệp vụ được gọi hoặc **sau khi** phương thức đã có tác dụng phụ.

Vì vậy, khi dùng dữ liệu trong advice, cần trả lời ba câu hỏi riêng biệt:

1. **Pointcut đã chọn join point nào?** Chữ ký khai báo, overload, loại điểm nối và phạm vi áp dụng là gì?
2. **Dữ liệu được bind vào advice bằng cách nào?** Ngữ nghĩa của kiểu tĩnh, kiểu runtime, tham số tên, `null` và kiểu nguyên thủy của mô hình AOP ra sao?
3. **Advice có bảo toàn hợp đồng không?** Nếu chỉ quan sát, nó có chuyển tiếp đúng một lần, giữ kết quả và ngoại lệ, không đưa vào giá trị `null` hoặc kiểu không phù hợp không?

Các cơ chế AOP không hỗ trợ cùng một tập join point hoặc cùng quy tắc *binding*. Bài này giải thích nền tảng Java và các nguyên tắc AOP tổng quát, dùng JDK dynamic proxy để minh họa một hình thức can thiệp **qua phương thức giao diện**. Đây không phải khẳng định proxy tương đương mọi cơ chế weaving hay một cú pháp pointcut được dùng chung.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

- Phân biệt **giá trị `null`**, **kiểu tham chiếu**, **kiểu nguyên thủy**, **wrapper class** và tình huống không có giá trị trả về.
- Giải thích boxing/unboxing khi dữ liệu primitive đi qua các API động như `Object[]`, đồng thời hiểu vì sao kiểu khai báo của phương thức không đổi.
- Chọn overload dựa vào kiểu tham số được khai báo thay vì loại đối tượng runtime truyền vào.
- Dùng ép kiểu tham chiếu, kiểm tra `instanceof`, chuyển đổi số, widening/narrowing đúng điều kiện mà không ngụy trang lỗi.
- Phân tích cách `null` ảnh hưởng tới pointcut động, ràng buộc đối số, `this`/`target`, binding kết quả và binding ngoại lệ.
- Nhận diện lỗi `NullPointerException` khi unboxing, `ClassCastException` khi cast sai, sai lệch số do narrowing hoặc khi trả kết quả không tương thích.
- Áp dụng các phép kiểm chứng đối với thành công, lỗi, kết quả `null`, giá trị primitive và đường gọi bỏ qua cơ chế AOP.
- Giữ advice theo dõi độc lập khỏi quy tắc nghiệp vụ và tránh làm lộ dữ liệu nhạy cảm.

## 2. Bốn tầng của một lần can thiệp

Đừng coi “dữ liệu của advice” là một biến duy nhất. Một lần gọi có thể có:

| Tầng | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Chữ ký tĩnh** | `compute(int, Object)` | Kiểu và thứ tự tham số phương thức đã được Java chọn |
| **Đối số runtime** | `[7, "A"]` trong cơ chế trả `Object[]` | Giá trị của đúng lần thực thi; `int` có thể được boxing thành `Integer` |
| **Điều kiện pointcut/binding** | “chọn `compute(int,Object)`; bind đối số thứ hai” | Xác định nơi áp advice và biến nào nhận dữ liệu |
| **Hợp đồng advice** | đọc đối số, chuyển tiếp, ghi kết quả/lỗi | Quyết định có giữ nguyên cách thực thi đã định hay thay đổi nó |

Ví dụ một phương thức khai báo tham số `Object` có thể nhận đối tượng `String`. Điều này **không biến phương thức ấy thành overload có tham số `String`**. Cũng vậy, một biến `Object` chứa `Integer` không làm chữ ký của phương thức `int` bị đổi thành `Integer`.

Một bộ weaving có thể tạo mã với kiểu mạnh ở điểm can thiệp; một proxy động có thể chuyển dữ liệu qua `Object[]`. Tư duy về **kiểu tĩnh và biểu diễn động** vẫn áp dụng, nhưng chi tiết binding phải xem theo công cụ.

## 3. `null` là gì và không phải là gì?

`null` là giá trị đặc biệt có thể gán cho **biến kiểu tham chiếu** trong Java:

```java
String name = null;
Integer count = null;
Object payload = null;
```

Trong khi đó, biến kiểu nguyên thủy không thể mang `null`:

```java
int amount = 3;
// int other = null; // Lỗi biên dịch.
```

**`null` không phải một đối tượng.** Nó không có phương thức instance để gọi hoặc một lớp runtime có thể truy vấn bằng `getClass()`. Việc `null` truyền được vào tham số tham chiếu không nói rằng ứng dụng chấp nhận `null` về mặt nghiệp vụ.

### 3.1. `null` trong binding đối số

Giả sử:

```java
String normalize(String text)
```

Nếu người gọi truyền `null`, advice có thể nhận một giá trị `String` bằng `null` **nếu cơ chế binding cho phép trường hợp đó**. Advice này không nên lập tức thực hiện `text.length()`. Phải xử lý hoặc từ chối theo hợp đồng:

```java
if (text == null) {
    // Ghi nhận trạng thái thiếu dữ liệu, không dereference.
} else {
    int length = text.length();
}
```

Đoạn code chỉ mô tả một nhánh kiểm tra; nó **không** yêu cầu advice tự thay đổi `null` thành chuỗi rỗng. Trong một số miền nghiệp vụ, hai giá trị này mang nghĩa khác nhau.

### 3.2. `instanceof` và `null`

Biểu thức `value instanceof String` trả `false` nếu `value == null`. Vì vậy:

```java
Object value = null;
System.out.println(value instanceof String); // false
```

Hệ quả trong AOP: một điều kiện pointcut **kiểm tra kiểu đối tượng runtime** có thể xử lý `null` khác với điều kiện chọn **chữ ký khai báo có tham số `String`**. Không được suy rằng mọi biểu thức kiểu trong mọi ngôn ngữ pointcut có quy tắc giống `instanceof` Java; nhiều ngôn ngữ AOP có quy tắc binding riêng khi đối số null. Cần đọc mô tả điều kiện đó và kiểm thử trực tiếp.

### 3.3. `null` trong overload

```java
void accept(String value);
void accept(Object value);
```

Ở Java, `accept(null)` chọn phiên bản `String`, vì `String` cụ thể hơn `Object` trong tập ứng viên này. Nhưng nếu có `accept(String)` và `accept(Integer)`, lời gọi `accept(null)` không có một overload duy nhất cụ thể hơn và gây lỗi biên dịch; `accept((String) null)` hoặc `accept((Integer) null)` chọn rõ phương thức.

Quan trọng: **lựa chọn overload xảy ra ở phía mã gọi**. Pointcut theo chữ ký `accept(String)` xét phương thức đã được chọn, không phải đoán kiểu của `null` ở thời gian chạy.

### 3.4. `null` không giống `void`

- Phương thức `String find()` trả `null`: lời gọi **trả về bình thường**, có kết quả tham chiếu là `null`.
- Phương thức `void update()` hoàn tất: **không có một giá trị kết quả** như `String` hay `Integer` để người gọi nhận.
- Phương thức `String find()` ném lỗi: **không có kết quả trả về bình thường** cho lần gọi đó.

Một số API reflection biểu diễn `Method.invoke` đối với phương thức `void` bằng `null`. Điều đó là **quy ước biểu diễn của API**, không biến `void` thành một phương thức trả về đối tượng null. Không đưa mọi `result == null` vào cùng nhóm nghiệp vụ “không tìm thấy”.

## 4. Kiểu nguyên thủy và các wrapper class

Java có tám kiểu nguyên thủy:

| Kiểu nguyên thủy | Wrapper | Ghi chú |
| --- | --- | --- |
| `boolean` | `Boolean` | Không phải kiểu số |
| `byte` | `Byte` | Số nguyên 8 bit có dấu |
| `short` | `Short` | Số nguyên 16 bit có dấu |
| `char` | `Character` | Đơn vị mã UTF-16, không phải ký tự Unicode đầy đủ theo mọi cách hiểu |
| `int` | `Integer` | Số nguyên 32 bit có dấu |
| `long` | `Long` | Số nguyên 64 bit có dấu |
| `float` | `Float` | Dấu phẩy động độ chính xác đơn |
| `double` | `Double` | Dấu phẩy động độ chính xác kép |

Một giá trị primitive **không phải đối tượng tham chiếu**. Nhưng nhiều API động của Java dùng `Object` hoặc `Object[]`, nên giá trị primitive có thể được **boxing** thành wrapper tương ứng.

```java
int raw = 12;
Integer boxed = raw;     // Autoboxing.
int again = boxed;       // Unboxing.
Object context = boxed;  // Giữ wrapper Integer.
```

### 4.1. Boxing trong advice qua proxy/reflection

Một giao diện:

```java
interface CounterPort {
    int increment(int delta);
}
```

Khi JDK dynamic proxy nhận lời gọi `increment(5)`, `InvocationHandler` thường nhìn thấy `Method` với kiểu tham số khai báo `int.class`, trong khi phần tử trong `Object[] args` chứa `Integer.valueOf(5)`.

Các mệnh đề đúng đồng thời:

1. **Chữ ký vẫn là `increment(int)`.**
2. **Giá trị được truyền qua `Object[]` bằng wrapper `Integer`.**
3. **Có thể nhận kiểu nguyên thủy trở lại bằng unboxing** khi giá trị không null và wrapper phù hợp.
4. **Số lượng đối số** trong mảng và thứ tự phải được kiểm chứng trước khi cast nếu pointcut có thể chọn nhiều overload.

Một ví dụ minh họa:

```java
int delta = (Integer) args[0];
```

Đoạn này giả định `args != null`, có ít nhất một phần tử, `args[0]` là `Integer` không null và đúng chữ ký. Nếu một điểm can thiệp khác lọt vào bộ lọc, giả định có thể sai. Không suy rằng mọi `Object` số đều cast được thành `Integer`.

### 4.2. Khi nào `args` là `null`?

Theo hợp đồng `InvocationHandler` của JDK dynamic proxy, **mảng đối số có thể là `null` khi phương thức không có tham số**. Đây là đặc điểm của cơ chế proxy, không phải “đối số đầu tiên có giá trị null”.

Phân biệt:

```text
args == null        -> có thể biểu diễn phương thức không tham số
args.length == 1    -> có một phần tử (phần tử ấy có thể là null)
args[0] == null     -> có đối số ở vị trí 0 và giá trị là null
```

Nếu phương thức không có tham số, đọc `args[0]` trực tiếp có thể gây lỗi. Với mô hình AOP có tham số advice được bind theo chữ ký rõ ràng, công cụ có thể làm phần đối chiếu hộ người lập trình; với interceptor tự viết, trách nhiệm kiểm tra thuộc về bạn.

## 5. Unboxing từ `null`: lỗi trước khi nghiệp vụ chạy

Đây là một trong những lỗi dễ làm hỏng advice.

```java
Integer wrapped = null;
// int value = wrapped; // Ném NullPointerException khi chạy.
```

Vì `int` không thể chứa null, unboxing `Integer null` thành `int` gây `NullPointerException`. Lỗi cũng có thể xuất hiện ở biểu thức số học hoặc phép so sánh tưởng chừng vô hại:

```java
Integer count = null;
// if (count > 0) { ... } // Unboxing ngầm -> NullPointerException.
```

Nếu advice thực thi một biểu thức như vậy **trước khi `proceed`**, phương thức đích có thể **không bao giờ được gọi**. Khi đó lỗi quan sát được là do advice, không nhất thiết do nghiệp vụ.

### 5.1. Cách kiểm tra phù hợp

Nếu `null` hợp lệ theo hợp đồng tham số, giữ giá trị là wrapper và xử lý nhánh riêng:

```java
Integer count = (Integer) args[0];
if (count == null) {
    System.out.println("count is absent");
} else {
    System.out.println("count=" + count.intValue());
}
```

Nếu `null` là dữ liệu không hợp lệ, lớp nghiệp vụ hoặc ranh giới xác thực dữ liệu nên **có trách nhiệm xác định cách từ chối**. Advice quan sát không nên tự đổi null thành 0 vì điều đó thay đổi ý nghĩa kết quả.

### 5.2. `Optional` cũng không tự giải mọi vấn đề

`Optional<T>` có thể biểu đạt một giá trị có hoặc không trong API thích hợp, nhưng không làm mọi phương thức Java tự động null-safe. `Optional` cũng không thay thế việc xử lý các trường hợp kiểu nguyên thủy, overload, kết quả `void` hoặc lỗi do proxy/weaver cung cấp dữ liệu không hợp lệ.

Nếu làm việc với số nguyên, có `OptionalInt`, `OptionalLong`, `OptionalDouble` cho một số thiết kế API, nhưng không nên chuyển đổi giao diện nghiệp vụ chỉ để tránh kiểm tra null trong advice.

## 6. Ép kiểu tham chiếu: cast và `instanceof`

Giả sử một bộ chặn nhận `Object value`. Các biểu thức sau có nghĩa khác nhau:

```java
String text = (String) value;         // Cast: lỗi nếu value không phải String phù hợp.
boolean isText = value instanceof String; // Kiểm tra loại runtime.
```

Nếu `value == null`, ép `(String) value` **hợp lệ** và kết quả vẫn là null; `instanceof String` là false. Nhưng nếu `value` là `Integer` thì cast sang `String` gây `ClassCastException`.

Một cú pháp an toàn khi thật sự cần đọc thông tin từ `String`:

```java
if (value instanceof String text) {
    System.out.println("Length=" + text.length());
} else if (value == null) {
    System.out.println("Value is null");
} else {
    System.out.println("Other type");
}
```

Đoạn code Java 17 này **minh họa** kiểm tra kiểu; không phải toàn bộ chương trình. Điều kiện `instanceof` tránh dereference trên null và tránh cast sai khi nhánh không được chọn.

### 6.1. Cast không làm chuyển kiểu đối tượng

Cast `(String) value` yêu cầu giá trị runtime tương thích với `String`; nó **không chuyển `Integer 42` thành `String "42"`**. Nếu thật sự cần một chuỗi mô tả, `String.valueOf(value)` trả `"null"` khi tham số tham chiếu null, hoặc biểu diễn chuỗi phù hợp của đối tượng.

Khác biệt này đặc biệt quan trọng trong advice ghi log: việc chuyển thành chuỗi để quan sát **khác** quyền tự sửa đối số truyền vào phương thức đích.

### 6.2. Kiểm tra kiểu không phải xác thực dữ liệu

Một đối tượng là `String` chỉ chứng minh loại đối tượng, không chứng minh chuỗi ấy là mã đơn hợp lệ, không nhạy cảm hoặc được phép ghi vào log. Tương tự, một `Integer` không có nghĩa số nằm trong miền chấp nhận của nghiệp vụ.

Advice không nên tự xác thực/ủy quyền dựa trên cast và `instanceof` đơn giản nếu ranh giới bảo mật đòi hỏi điều kiện mạnh hơn.

## 7. Chuyển đổi kiểu số: widening, narrowing và wrapper

Java hỗ trợ một số chuyển đổi kiểu nguyên thủy, nhưng không phải mọi kiểu chuyển đổi đều an toàn về độ lớn hay độ chính xác.

### 7.1. Widening primitive

```java
int small = 12;
long wide = small;      // Widening int -> long.
double approx = wide;   // Widening long -> double, có thể mất độ chính xác.
```

Việc gọi chuyển đổi là **widening** không đồng nghĩa mọi giá trị nguyên lớn đều giữ chính xác khi chuyển sang dấu phẩy động. Ví dụ, `long` chứa được những số nguyên mà `double` không biểu diễn chính xác toàn bộ.

### 7.2. Narrowing primitive

```java
long large = 3_000_000_000L;
int narrow = (int) large;  // Mất thông tin; kết quả không còn là 3_000_000_000.
```

Nếu advice “chuẩn hóa” một số tiền, mã định danh hoặc kích thước bằng narrowing mà không kiểm tra phạm vi, dữ liệu có thể bị biến đổi thầm lặng. Với phép chuyển `long` sang `int` đòi hỏi phát hiện tràn, `Math.toIntExact(long)` sẽ ném `ArithmeticException` nếu giá trị không biểu diễn được trong `int`.

### 7.3. Wrapper không có quan hệ kế thừa số tương đương

`Integer` và `Long` đều kế thừa `Number`, nhưng **không** có nghĩa ép `(Long) (Object) Integer.valueOf(5)` hợp lệ. Phép cast ấy gây `ClassCastException`.

Khi thật sự cần chuyển dữ liệu số:

```java
Object data = Integer.valueOf(5);
long converted = ((Number) data).longValue();
```

Đây là phép **chuyển giá trị** dựa trên `Number`, không phải cast wrapper sang wrapper. Nó cần kiểm tra `data != null`, `data instanceof Number` và yêu cầu độ chính xác/phạm vi. `Number.longValue()` không bảo đảm không mất thông tin với mọi lớp con `Number`.

### 7.4. Kiểu chữ ký không tự thay đổi khi có widening ở điểm gọi

Ví dụ có hai overload `calculate(int)` và `calculate(long)`. Lời gọi `calculate(4)` thường chọn `int` khi cả hai khả dụng. Nếu chỉ có `calculate(long)`, Java có thể chuyển `int` thành `long` ở **điểm gọi**. Pointcut theo chữ ký phải chọn **phương thức thật đã được compiler lựa chọn**, không suy ra overload bằng loại đối tượng trong `Object[]`.

Khi proxy nhận phương thức khai báo `long`, đối số primitive của phương thức đó được biểu diễn bằng `Long` tương ứng trong mảng của handler; không vì mã nguồn ban đầu có literal `4` mà handler được bảo đảm nhận `Integer`.

## 8. Binding kiểu tĩnh và kiểu động trong pointcut

Một cơ chế AOP có thể cho phép kết hợp:

- Điều kiện theo **chữ ký phương thức/field**;
- Điều kiện theo **kiểu runtime của đối số**;
- Điều kiện theo **kiểu runtime của đối tượng đích**, khi join point có target;
- Ràng buộc dữ kiện phù hợp vào **biến advice**;
- Điều kiện theo annotation, bối cảnh điều khiển hoặc trạng thái khác được hỗ trợ.

Tuy nhiên, từ “binding một `String`” có thể chỉ hai ý:

1. **Bind đối số ở vị trí xác định của phương thức khai báo `String`**, kể cả khi giá trị null được hợp đồng cho phép.
2. **Chỉ khớp khi đối số thực tế có kiểu runtime `String`**, tức một điều kiện động có thể loại trừ null tùy ngữ nghĩa công cụ.

Không được đánh đồng hai ý. Một số ngôn ngữ pointcut còn có những quy tắc riêng cho kiểu primitive, boxing, binding reference và `null`. Cú pháp như `args(name)` nếu được một công cụ hỗ trợ **không nhất thiết** hoạt động giống `name instanceof T` viết tay trong Java.

### 8.1. Không suy luận “có dữ liệu” từ tên biến

Giả sử một advice khai báo tham số `String orderId`. Tên biến `orderId` chỉ có ý nghĩa nếu **quy tắc binding** đã liên kết nó với đúng dữ kiện ở join point. Công cụ không tự tìm một biến cục bộ cùng tên trong mã nguồn đích.

Nếu hai tham số đều là `String`, việc đảo vị trí binding có thể không gây lỗi kiểu nhưng tạo dữ liệu sai. Đây là lỗi khác với `NullPointerException` hoặc `ClassCastException`, và cần kiểm thử riêng.

### 8.2. Kiểu rộng có thể khiến advice bỏ lỡ thông tin

Một advice khai báo đối số kiểu `Object` có thể nhận nhiều loại giá trị, bao gồm null. Nếu nó cần tính chiều dài chuỗi mà tự cast mọi giá trị sang `String`, chính sự rộng của bộ chọn tạo rủi ro runtime. Có hai chiến lược hợp lệ tùy mục đích:

- **Làm pointcut hẹp** để chọn đúng chữ ký hoặc kiểu đối số mà advice được phép xử lý.
- **Giữ pointcut rộng nhưng xử lý kiểu theo nhánh** an toàn, không giả định mọi đối số thuộc một lớp.

Dù chọn hướng nào, đừng nới pointcut vì “muốn dễ quan sát” rồi vô tình gắn advice thay đổi nghiệp vụ lên những loại join point không phù hợp.

## 9. Giá trị trả về trong advice: null, primitive và void

Khi around advice hoặc một bộ chặn định trả kết quả ra người gọi, **hợp đồng trả về** quan trọng không kém đầu vào.

### 9.1. Phương thức trả tham chiếu

Nếu phương thức khai báo `String find()`, kết quả `null` có thể hợp lệ về kiểu (dù nghiệp vụ có thể cấm). Advice chỉ quan sát thường nên **trả nguyên kết quả** nhận được từ thao tác bên trong.

Khi ghi kết quả, phải xử lý null trước nếu gọi `result.toString()` hoặc dereference. `String.valueOf(result)` có thể an toàn hơn cho log kỹ thuật, nhưng vẫn cần bảo vệ dữ liệu nhạy cảm và chi phí tạo chuỗi.

### 9.2. Phương thức trả primitive

Nếu phương thức khai báo `int count()`, người gọi cần kết quả primitive. Trong JDK dynamic proxy, handler trả `Object`; proxy phải **unbox** giá trị wrapper phù hợp trước khi trả tới người gọi. Nếu handler trả **null** cho phương thức trả primitive, quá trình unboxing của proxy có thể ném `NullPointerException`. Nếu trả một đối tượng kiểu không tương thích, proxy có thể ném `ClassCastException`.

Điều này có nghĩa một advice quanh không được tự coi `null` là “không muốn can thiệp” cho mọi kiểu kết quả. Nếu không có kết quả thay thế hợp lệ, cần **chuyển tiếp đúng kết quả** của đích hoặc tuân thủ hợp đồng rõ ràng về đường lỗi.

### 9.3. Phương thức `void`

Với `void save()`, không có giá trị nghiệp vụ trả về. Một handler proxy thông thường có thể trả `null` cho kiểu `void` theo hợp đồng `InvocationHandler` mà không gây vấn đề tương tự kiểu primitive. Nhưng điều này không biến `save()` thành `Object save()`; mã gọi vẫn không thể lấy kết quả từ phương thức void.

Phân biệt ba trường hợp:

| Chữ ký | Kết quả hợp lệ | Lỗi thường gặp trong advice |
| --- | --- | --- |
| `String name()` | Một `String` hoặc null về mặt kiểu Java | Gọi `toString` trên null |
| `int count()` | Giá trị int (trong proxy biểu diễn qua `Integer`) | Handler trả null hoặc sai wrapper |
| `void update()` | Không có giá trị trả về | Cố coi null của API reflection là một “kết quả nghiệp vụ” |

### 9.4. After-returning và after-throwing không hoán đổi được

Một lời gọi trả null bình thường là nhánh **after-returning**, không phải nhánh **after-throwing**. Nếu advice sau thành công yêu cầu ràng buộc một kiểu cụ thể, điều kiện binding có thể giới hạn những kết quả được advice nhận; phải xem quy tắc công nghệ.

Ngược lại, nếu đích ném lỗi, không có kết quả trả về bình thường để bind vào tham số result. Advice after-throwing có thể nhận đối tượng lỗi theo hợp đồng loại ngoại lệ được hỗ trợ.

## 10. Ngoại lệ khi ép kiểu và ngoại lệ của nghiệp vụ

Một `ClassCastException` xảy ra **trong thân advice** có thể là lỗi kỹ thuật của khía cạnh; nó không chứng minh đích đã xử lý sai dữ liệu. Một `IllegalArgumentException` xảy ra khi `Method.invoke` nhận đối số không tương thích cũng có thể xuất phát từ lớp chuyển tiếp, không phải từ thân phương thức đích.

Phân biệt các nguồn:

| Vị trí phát sinh | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Binding/cast trong advice** | `(String) Integer.valueOf(2)` | Sai giả định kiểu của advice |
| **Unboxing trong advice** | `int n = (Integer) null` | Không thể chuyển null thành primitive |
| **Gọi reflection** | Mảng đối số sai số lượng hoặc kiểu | Lỗi truyền tham số trước khi target chạy |
| **Target** | `throw new IllegalArgumentException(...)` trong nghiệp vụ | Lỗi gốc của logic được gọi |
| **Unboxing kết quả proxy** | Handler trả null cho `int` | Lỗi chuyển kết quả ở ranh giới proxy |
| **Cleanup/logging advice** | Mã ghi log tự ném lỗi | Có thể thay hoặc che lỗi gốc |

### 10.1. `InvocationTargetException` không phải lỗi nghiệp vụ gốc

Khi dùng `Method.invoke`, ngoại lệ do thân phương thức đích ném thường được bọc bởi `InvocationTargetException`. Khi muốn giữ ngoại lệ gốc qua proxy, handler có thể làm:

```java
try {
    return method.invoke(target, args);
} catch (java.lang.reflect.InvocationTargetException ex) {
    throw ex.getCause();
}
```

Đây là **đoạn mã dùng bên trong một phương thức khai báo `throws Throwable`**, không phải chương trình độc lập. Nếu chỉ ghi wrapper, advice có thể chẩn đoán nhầm loại lỗi. Tuy vậy đừng bắt mọi `Throwable` rồi mặc định nó đến từ target: các lỗi ngay trong advice hoặc trong cơ chế chuyển tiếp cũng cần được phân biệt.

### 10.2. Advice không được nuốt lỗi ép kiểu rồi trả kết quả bất kỳ

Một mẫu nguy hiểm là:

```java
try {
    return proceed();
} catch (ClassCastException ex) {
    return null;
}
```

Đây là giả mã theo phong cách Java: `proceed()` phải được định nghĩa bởi cơ chế AOP. Trả null để che lỗi không an toàn nếu phương thức có kết quả primitive; nó cũng có thể biến lỗi lập trình thành kết quả nghiệp vụ giả.

Cách sửa là **làm rõ tập điểm và kiểu binding** thay vì dùng fallback tùy ý khi phát hiện một cast sai.

## 11. Ví dụ Java 17: null, primitive boxing và kết quả advice

Ví dụ dưới đây dùng **JDK dynamic proxy** để minh họa cơ chế nhận dữ liệu ở handler, chọn phương thức theo chữ ký, quan sát `null`, unboxing có kiểm soát và chuyển tiếp kết quả/ngoại lệ. Tất cả chỉ dùng JDK 17, không cần thư viện bên ngoài.

Lưu toàn bộ thành `AdviceDataDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class AdviceDataDemo {
    interface Calculation {
        int add(int left, int right);
        Integer doubleOrNull(Integer number);
        String describe(Object payload);
    }

    static final class Calculator implements Calculation {
        @Override
        public int add(int left, int right) {
            if (left < 0) {
                throw new IllegalArgumentException("left phải không âm");
            }
            return left + right;
        }

        @Override
        public Integer doubleOrNull(Integer number) {
            return number == null ? null : number * 2;
        }

        @Override
        public String describe(Object payload) {
            return payload == null ? "<null>" : payload.toString();
        }
    }

    private static Object invokeTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    static Calculation withAdvice(Calculation target) {
        return (Calculation) Proxy.newProxyInstance(
            Calculation.class.getClassLoader(),
            new Class<?>[] { Calculation.class },
            (proxy, method, args) -> {
                System.out.println("[ENTER] " + method.getName()
                        + " args=" + Arrays.toString(args));

                if (method.getName().equals("add")) {
                    if (!Arrays.equals(method.getParameterTypes(),
                            new Class<?>[] { int.class, int.class })) {
                        throw new IllegalStateException("Wrong signature");
                    }

                    // int của chữ ký được boxing thành Integer trong Object[].
                    int left = (Integer) args[0];
                    int right = (Integer) args[1];
                    System.out.println("[BIND] add=" + left + "+" + right);
                } else if (method.getName().equals("doubleOrNull")) {
                    Integer number = (Integer) args[0];
                    System.out.println("[BIND] number=" + number);
                } else if (method.getName().equals("describe")) {
                    Object payload = args[0];
                    String type = payload == null
                            ? "null" : payload.getClass().getSimpleName();
                    System.out.println("[BIND] runtimeType=" + type);
                }

                try {
                    Object result = invokeTarget(method, target, args);
                    System.out.println("[RETURN] " + String.valueOf(result));
                    return result;
                } catch (Throwable error) {
                    System.out.println("[THROW] "
                            + error.getClass().getSimpleName());
                    throw error;
                }
            }
        );
    }

    public static void main(String[] args) {
        Calculation monitored = withAdvice(new Calculator());

        System.out.println("CALLER add=" + monitored.add(2, 3));
        System.out.println("CALLER missing=" + monitored.doubleOrNull(null));
        System.out.println("CALLER double=" + monitored.doubleOrNull(4));
        System.out.println("CALLER describe=" + monitored.describe("A"));
        System.out.println("CALLER null=" + monitored.describe(null));

        try {
            monitored.add(-1, 1);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER error=" + error.getMessage());
        }

        System.out.println("DIRECT add=" + new Calculator().add(1, 1));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 AdviceDataDemo.java
java AdviceDataDemo
```

Đầu ra dự kiến:

```text
[ENTER] add args=[2, 3]
[BIND] add=2+3
[RETURN] 5
CALLER add=5
[ENTER] doubleOrNull args=[null]
[BIND] number=null
[RETURN] null
CALLER missing=null
[ENTER] doubleOrNull args=[4]
[BIND] number=4
[RETURN] 8
CALLER double=8
[ENTER] describe args=[A]
[BIND] runtimeType=String
[RETURN] A
CALLER describe=A
[ENTER] describe args=[null]
[BIND] runtimeType=null
[RETURN] <null>
CALLER null=<null>
[ENTER] add args=[-1, 1]
[BIND] add=-1+1
[THROW] IllegalArgumentException
CALLER error=left phải không âm
DIRECT add=2
```

### 11.1. Những điểm quan trọng của ví dụ

1. `Calculation.add(int,int)` khai báo kiểu primitive. `Method.getParameterTypes()` vẫn có `int.class`, nhưng handler nhận `Integer` trong `Object[]`. Ép `(Integer)` rồi unboxing là phù hợp khi đã xác nhận chữ ký.
2. `doubleOrNull(Integer)` là kiểu tham chiếu: có thể nhận null và trả null. Cả hai trường hợp đều đi qua nhánh trả về bình thường, không phải ngoại lệ.
3. `describe(Object)` nhận chuỗi `"A"` nhưng chữ ký vẫn là `Object`; `runtimeType=String` là thông tin của **giá trị**.
4. `describe(null)` không được gọi `payload.getClass()` trực tiếp. Advice phân nhánh để không ném lỗi khi giá trị null.
5. `add(-1,1)` cho thấy lỗi do **đích** ném, được tháo khỏi `InvocationTargetException` rồi chuyển tiếp tới người gọi. Advice chỉ ghi loại lỗi và giữ nguyên ngoại lệ.
6. `new Calculator().add(1,1)` gọi trực tiếp đối tượng đích, **không qua proxy**, nên không có `[ENTER]` hoặc `[RETURN]`. Điều này minh họa phạm vi của proxy, không phải mọi công nghệ AOP.
7. `Arrays.toString(args)` được dùng cho giao diện có tham số ở mọi phương thức đang demo. Khi mở rộng handler cho phương thức **không tham số**, phải xử lý `args == null` nếu định truy cập phần tử.
8. Ví dụ in dữ liệu người gọi chỉ để học cách binding; với ứng dụng thật cần bảo vệ mã định danh, token, nội dung riêng tư và dữ liệu có thể nhạy cảm.

### 11.2. Vì sao ví dụ không gọi advice là pointcut hoàn chỉnh?

Bộ lọc theo `method.getName()` trong handler chỉ là **logic Java do người lập trình viết**. Ngôn ngữ pointcut đầy đủ có thể hỗ trợ nhiều loại join point, điều kiện khai báo, kiểu runtime, annotation và luật kết hợp. Dynamic proxy này chỉ chặn **lời gọi phương thức qua giao diện đại diện**.

Vì vậy kết quả của ví dụ chỉ chứng minh: **nếu lời gọi đi qua proxy, dữ liệu nào có thể được quan sát và chuyển tiếp như thế nào**. Nó không chứng minh mọi truy cập field, constructor, phương thức tĩnh/private hoặc lời gọi nội bộ đều được advice nhận.

## 12. Ví dụ Java 17: lỗi khi handler trả null cho primitive

Một lỗi khác xuất hiện ở ranh giới trả kết quả. Để nhìn thấy rõ, lưu mã hoàn chỉnh thành `PrimitiveReturnDemo.java`:

```java
import java.lang.reflect.Proxy;

public class PrimitiveReturnDemo {
    interface NumberPort {
        int value();
    }

    public static void main(String[] args) {
        NumberPort invalid = (NumberPort) Proxy.newProxyInstance(
                NumberPort.class.getClassLoader(),
                new Class<?>[] { NumberPort.class },
                (proxy, method, input) -> null
        );

        try {
            System.out.println(invalid.value());
        } catch (NullPointerException error) {
            System.out.println("NULL_FOR_INT NullPointerException");
        }

        NumberPort valid = (NumberPort) Proxy.newProxyInstance(
                NumberPort.class.getClassLoader(),
                new Class<?>[] { NumberPort.class },
                (proxy, method, input) -> Integer.valueOf(7)
        );

        System.out.println("VALID " + valid.value());
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 PrimitiveReturnDemo.java
java PrimitiveReturnDemo
```

Đầu ra:

```text
NULL_FOR_INT NullPointerException
VALID 7
```

**Tại sao?** `NumberPort.value()` khai báo `int`. Handler có kiểu trả về `Object`, và lớp proxy phải chuyển giá trị đó sang `int`. Nó không thể unbox `null`; `Integer.valueOf(7)` thì phù hợp và cho kết quả 7.

Đây là hành vi của JDK dynamic proxy. Một bộ AOP có binding kết quả và kiểm tra kiểu ở giai đoạn weaving có thể phát hiện lỗi ở thời điểm khác; không nên suy rằng mọi công nghệ đều ném cùng loại lỗi tại cùng điểm thực thi. Điều cần nhớ là **đường return của advice phải phù hợp hợp đồng kiểu kết quả của điểm can thiệp**.

## 13. Nếu advice chủ động sửa đối số

Một số around advice có thể gọi tiếp với đối số thay thế, nhưng **năng lực và quy tắc đó phụ thuộc mô hình**. Nếu được hỗ trợ, trước khi thay dữ liệu cần xét:

- Đối số được thay có cùng kiểu/hợp đồng với tham số của phương thức đã chọn không?
- Nếu thay null cho tham số primitive, cơ chế từ chối ở giai đoạn nào?
- Nếu chuyển số `long` sang `int`, có kiểm tra tràn không?
- Nếu thay wrapper bằng kiểu runtime khác, cơ chế có thể thực hiện conversion được không, hay ném lỗi?
- Thay đối số có làm mất định danh giao dịch, khóa chống trùng hoặc dữ liệu audit không?
- Nếu gọi tiếp nhiều lần với các giá trị khác nhau, tác dụng phụ có bị lặp không?

Tuyệt đối không dùng “cast thành kiểu cần thiết” như công cụ sửa dữ liệu tùy tiện. `((Number) value).intValue()` có thể cắt phần thập phân hoặc tràn tùy lớp số; nó không tương đương một phép xác minh miền giá trị.

### 13.1. Chữ ký overload không tự được giải lại

Trong Java, `handle(Object)` không tự đổi thành `handle(String)` chỉ vì around advice thay giá trị đối số thành một đối tượng `String`. Thông thường, điểm thực thi đã được lựa chọn theo chữ ký/cấu trúc của join point. Nếu cần gọi overload khác, đó là **một thao tác điều phối khác có chủ đích**, không phải phép đổi kiểu thông thường của binding.

## 14. Generics, type erasure và bridge methods

Java xóa phần lớn thông tin tham số kiểu generic khi biên dịch (*type erasure*). Xét:

```java
interface Handler<T> {
    T handle(T value);
}

class TextHandler implements Handler<String> {
    @Override
    public String handle(String value) {
        return value;
    }
}
```

Đây là **mã minh họa**, không phải tệp thực thi độc lập. Compiler có thể tạo phương thức cầu nối (*bridge method*) để giữ hành vi đa hình sau erasure. Một công cụ phân tích bytecode/reflection có thể nhìn thấy thêm phương thức tổng hợp, trong khi người đọc mã nguồn chỉ thấy phiên bản `handle(String)`.

Các vấn đề đối với advice:

1. `Method.getParameterTypes()` trả kiểu đã xóa/khai báo ở cấp lớp đang phản chiếu; không tự khôi phục mọi tham số generic `T` ở nguồn.
2. Một điểm `handle(Object)` do bridge có thể chuyển tiếp tới `handle(String)`; việc đếm join point/advice phụ thuộc công nghệ và loại điểm được weaving.
3. Phép cast generic có thể bị compiler chèn ở đường thực thi, gây `ClassCastException` ở vị trí khác điều bạn tưởng.
4. Nếu advice yêu cầu kiểu `String`, phải phân biệt pointcut theo **chữ ký gốc của interface**, **phương thức hiện thực** và **giá trị runtime**.

`Method.isBridge()` và `Method.isSynthetic()` giúp kiểm tra một số phương thức do compiler sinh trong Java Reflection. Nhưng không nên loại bỏ chúng vô điều kiện khỏi mọi chính sách; tập điểm mong muốn phải được định nghĩa theo hợp đồng nghiệp vụ và khả năng AOP thực tế.

## 15. `this`, `target` và đối tượng vắng mặt

Ngoài args/result, một advice có thể cần đối tượng đang thực thi hoặc đối tượng đích. Một số mô hình phân biệt `this` và `target` theo loại join point:

- Ở điểm gọi phương thức, đối tượng chứa mã phát sinh lời gọi và đối tượng nhận lời gọi có thể là hai đối tượng khác nhau.
- Ở một điểm thực thi instance, chúng có thể trùng nhau theo ngữ nghĩa công nghệ.
- Với thao tác tĩnh hoặc điểm nối không có receiver, **không được mặc định luôn có một target instance**.
- Với JDK proxy, handler được cung cấp một tham chiếu proxy trong tham số `proxy`, nhưng không vì thế mà tham số này luôn đại diện cho **target thực tế**; target có thể là đối tượng mà handler tự giữ để chuyển tiếp.

Vì vậy dùng `target.getClass()` trong advice đòi hỏi có target thực sự và khác null. Nếu metadata của join point đã đủ để ghi log, không cần tự dereference một đối tượng vốn có thể vắng mặt.

## 16. Số, độ chính xác và dữ liệu nghiệp vụ

Việc ép kiểu trong advice có thể làm thay đổi các miền nhạy cảm:

- **Giá tiền:** `float`/`double` có biểu diễn nhị phân giới hạn; quy tắc làm tròn và đơn vị cần được định nghĩa rõ. Không dùng `Double` thay tùy tiện cho `BigDecimal` trong một chính sách chuyển tiền.
- **Mã định danh:** chuyển `long` sang `int` có thể mất dữ liệu; ID không phải lúc nào là đại lượng để làm toán.
- **Thời lượng:** `Duration`, nanoseconds và milliseconds có thể khác đơn vị; narrowing có thể tràn.
- **Số lượng:** `Integer null` có thể biểu thị “không cung cấp”, trong khi `0` biểu thị đã cung cấp giá trị 0.
- **Tỷ lệ:** chuyển `double` sang `int` làm mất phần thập phân, không tự thực hiện nghiệp vụ làm tròn mong muốn.
- **Kiểu boolean:** `Boolean null` có thể có ý nghĩa ba trạng thái; `boolean` chỉ có hai trạng thái.

Một advice đo đạc có thể chuyển dữ liệu sang loại biểu diễn phù hợp cho hệ quan sát, nhưng phải **không biến dữ liệu đầu vào của nghiệp vụ** khi không có quyền.

### 16.1. Boxing cũng có chi phí

Trên các đường gọi nóng, tạo wrapper, mảng đối số và dùng reflection/proxy có thể tăng chi phí. JVM có thể tối ưu một phần, nhưng không nên tuyên bố “boxing luôn tạo allocation” hoặc “không có chi phí vì cache wrapper”.

Khi hiệu năng quan trọng, hãy benchmark đúng đường thực thi và công cụ AOP trong môi trường gần sản xuất; đừng suy chi phí từ một lần in log hoặc một ví dụ nhỏ.

## 17. Kiểm thử từng trường hợp

Một ma trận test nên kiểm tra **pointcut có đúng**, **binding có đúng** và **hợp đồng kết quả có được giữ**:

| Ca kiểm thử | Mong đợi |
| --- | --- |
| Đối số `String` hợp lệ | Advice nhận đúng chuỗi, đích chạy đúng một lần |
| Đối số tham chiếu null | Advice không dereference lỗi; xử lý theo hợp đồng |
| Đối số primitive `int` | Binding có kiểu khai báo `int` và biểu diễn runtime phù hợp |
| Wrapper `Integer` là null | Không unbox bất cẩn |
| Hai overload `f(int)`, `f(Integer)` | Pointcut chọn đúng chữ ký mong muốn |
| Đối số `Object` giữ `String` runtime | Không nhầm chữ ký `Object` thành `String` |
| Giá trị trả `String null` | Nhánh return bình thường, không báo throw |
| Phương thức `void` | Không ép một giá trị kết quả vốn không tồn tại |
| Phương thức trả primitive và handler trả null | Phát hiện lỗi tại ranh giới kiểu trả về |
| Cast sai lớp wrapper | Lỗi được nhận diện, không bị nuốt hoặc đổi kết quả tùy tiện |
| Chuyển `long` sang `int` quá phạm vi | Có kiểm tra tràn nếu hợp đồng đòi tính chính xác |
| Đích ném ngoại lệ | Advice ghi nhận và chuyển tiếp lỗi gốc |
| Advice ném lỗi trước khi chuyển tiếp | Xác định đích không chạy, phân biệt lỗi advice |
| Lời gọi bỏ qua proxy / mã chưa weaving | Không giả định advice được áp |
| Hai lời gọi đồng thời | Dữ liệu mỗi lần advice không lẫn nhau |
| Dữ liệu nhạy cảm | Không in hoặc lưu giá trị ngoài chính sách |

### 17.1. Quy trình chẩn đoán khi xuất hiện `NullPointerException` trong advice

1. Xác định `null` thuộc **đối số**, **target**, **mảng `args`**, **kết quả** hay **ngữ cảnh kỹ thuật**.
2. Kiểm tra chữ ký phương thức thật: tham số đó khai báo primitive hay reference?
3. Xác định có phép dereference hoặc **unboxing ngầm** không.
4. Nếu handler lấy `args[0]`, kiểm tra có đúng số phần tử và vị trí không.
5. Nếu null ở kết quả, xem phương thức trả reference, primitive hay void.
6. Xác định lỗi phát sinh **trước `proceed`**, **trong target** hay **sau khi nhận kết quả**.
7. Sửa điều kiện/binding hoặc hợp đồng xử lý null; không ngầm chuyển null thành giá trị mặc định khi miền nghiệp vụ không cho phép.
8. Kiểm thử lại cả các đường mà pointcut không được áp dụng.

### 17.2. Quy trình chẩn đoán `ClassCastException`

1. Xem lớp runtime thật của giá trị (khi khác null) và kiểu đích của cast.
2. Kiểm tra `Method.getParameterTypes()` cùng số/thứ tự tham số; có overload không?
3. Xem giá trị primitive đã được boxing thành wrapper nào.
4. Phân biệt **cast wrapper** với **chuyển giá trị primitive/số**.
5. Kiểm tra generic erasure/bridge nếu phương thức generic đang tham gia.
6. Xác minh dữ liệu có được thay bởi một around advice khác trong chuỗi không.
7. Thu hẹp pointcut hoặc thêm nhánh xử lý kiểu phù hợp; không chỉ bắt lỗi rồi trả null.
8. Kiểm tra lại kết quả/ngoại lệ và tác dụng phụ của lời gọi gốc.

## 18. Những sai lầm phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Coi `null` là một đối tượng có lớp runtime | Dereference hoặc giả định kiểu sai | Xử lý null tách biệt |
| Dùng `instanceof T` để khẳng định chữ ký khai báo là `T` | Khớp nhầm overload nhận `Object` | So chữ ký và tiêu chí runtime riêng |
| Ép `(Integer) args[0]` khi pointcut chọn mọi overload | Cast sai hoặc không có phần tử | Lọc chữ ký/kiểm tra args trước |
| Tưởng mảng `Object[]` chứa primitive trực tiếp | Lẫn cơ chế boxing | Hiểu wrapper tương ứng |
| Unbox một `Integer null` | `NullPointerException` | Kiểm tra null, giữ dữ liệu reference khi cần |
| Coi `int` và `Integer` là cùng chữ ký | Chọn sai pointcut | Phân biệt primitive và wrapper |
| Dùng `(Long) objectInteger` | `ClassCastException` | Chuyển số có kiểm tra, không cast wrapper ngang |
| Ép `long` xuống `int` tùy ý | Tràn/mất dữ liệu | Kiểm tra phạm vi hoặc dùng `Math.toIntExact` |
| Trả null từ handler cho phương thức trả `int` | Lỗi unboxing ở proxy | Trả đúng wrapper/giữ giá trị gốc |
| Xem `void` là một kết quả null nghiệp vụ | Ghi sai trạng thái | Phân biệt chữ ký void với reference trả null |
| Catch mọi lỗi và trả null | Che lỗi gốc, phá hợp đồng | Chuyển tiếp ngoại lệ phù hợp |
| Đổi tham số khi chỉ được yêu cầu ghi log | Thay hành vi nghiệp vụ | Giữ input/output nguyên dạng |
| Tin rằng binding đúng là đã weaving đúng | Bỏ sót đường gọi trực tiếp | Kiểm chứng cơ chế và phạm vi |
| Log toàn bộ `args` hoặc `result` | Rò rỉ thông tin | Ghi tối thiểu, che dữ liệu nhạy cảm |
| Lưu dữ liệu lời gọi trong field aspect dùng chung | Lẫn ngữ cảnh khi đồng thời | Dùng biến theo lần thực thi |

## 19. Bài tập vận dụng và đáp án

### Bài 1 — Null và primitive

Cho:

```java
Integer x = null;
// int y = x;
```

Dòng thứ hai (nếu bỏ chú thích) có hợp lệ khi biên dịch và chuyện gì xảy ra khi chạy?

**Đáp án:** Dòng mã hợp lệ về cú pháp/kiểu nhờ unboxing từ `Integer` sang `int`; khi thực thi nó ném `NullPointerException` vì `x` là null. Advice không nên thực hiện unboxing vô điều kiện nếu dữ liệu đầu vào có thể null.

### Bài 2 — Null có phải một lỗi không?

Phương thức `String find()` trả null bình thường. Advice sau thành công có nhất thiết là nhánh after-throwing không?

**Đáp án:** Không. Đây là lời gọi trả về bình thường. Việc bind null vào một tham số advice kiểu cụ thể phụ thuộc công nghệ, nhưng không nên gắn nhãn “exception” chỉ vì giá trị kết quả là null.

### Bài 3 — Boxing trong proxy

Một phương thức khai báo `long process(long amount)`, người gọi truyền literal `3`. Trong handler JDK proxy, kiểu tham số khai báo và biểu diễn đối số tương ứng thường là gì?

**Đáp án:** Chữ ký đích có tham số `long` (reflection trả `long.class`); sau khi Java chuyển literal nguyên thủy theo lời gọi tới tham số `long`, phần tử `Object[]` được biểu diễn bằng `Long`. Không giả định có `Integer` chỉ vì literal ban đầu là một giá trị `int`.

### Bài 4 — Cast ngang giữa wrapper

`Object o = Integer.valueOf(9)`. Biểu thức `(Long) o` có chuyển giá trị 9 thành một `Long` không?

**Đáp án:** Không. Nó gây `ClassCastException` khi chạy. Nếu cần giá trị long, có thể dùng `((Number)o).longValue()` sau khi xác minh kiểu/phạm vi, hoặc dùng unboxing rồi widening theo thiết kế rõ.

### Bài 5 — `args == null` khác `args[0] == null`

Trong JDK proxy, một phương thức không tham số được gọi; handler dự kiến đọc `args[0]`. Vì sao điều này nguy hiểm?

**Đáp án:** Với phương thức không có tham số, mảng `args` có thể là null. Không có phần tử 0 để đọc. Phải chọn/kiểm tra chữ ký và dạng mảng trước khi truy cập.

### Bài 6 — Primitive return

Một handler proxy cho giao diện có phương thức `int total()` trả `null` để biểu thị “không có dữ liệu”. Điều gì xảy ra?

**Đáp án:** Proxy cần unbox kết quả thành `int`; null không thể unbox và có thể gây `NullPointerException`. Phải tuân thủ hợp đồng kiểu trả về; nếu API cần thể hiện thiếu dữ liệu, kiểu tham chiếu hoặc thiết kế result phù hợp phải được xác định ở hợp đồng, không dùng null tùy ý trong handler.

### Bài 7 — Kiểu runtime khác chữ ký

Một lớp có `send(Object)` và `send(String)`, mã gọi `send((Object)"A")`. Pointcut chọn chữ ký `send(String)` có được áp chỉ vì đối tượng truyền là `String` không?

**Đáp án:** Không. Mã gọi chọn `send(Object)`; kiểu runtime của giá trị và kiểu khai báo của overload là hai tiêu chí khác nhau. Một điều kiện dynamic riêng có thể xem xét giá trị runtime nếu công nghệ hỗ trợ.

### Bài 8 — Độ chính xác

Một advice nhận `long` rồi ép `(int)` trước khi chuyển tiếp để phù hợp một API khác. Những rủi ro nào cần kiểm tra?

**Đáp án:** Giá trị ngoài phạm vi `int` có thể bị cắt mất phần bit cao. Nếu việc chuyển này được phép thì phải kiểm tra phạm vi (`Math.toIntExact` khi phù hợp), đơn vị, ý nghĩa nghiệp vụ và hợp đồng đầu vào; không nên làm việc đó trong advice thuần quan sát.

### Bài 9 — Lỗi advice hay lỗi target?

Một advice ép `args[0]` sang `String` nhưng đối số thật là `Integer`. Phương thức đích có chắc đã chạy không?

**Đáp án:** Không. Cast có thể ném `ClassCastException` ngay trong advice trước khi `proceed`. Cần phân biệt nguồn lỗi dựa trên vị trí phát sinh và đường thực thi, không quy mọi ngoại lệ cho phương thức đích.

### Bài 10 — Chọn cách kiểm thử

Bạn có một pointcut theo tên `process` và advice đọc phần tử 0 thành `Integer`. Hãy nêu ít nhất ba ca cần thử.

**Đáp án:** Nên thử overload `process(int)` (primitive được boxing), `process(Integer)` với null (không được unbox mù quáng), `process(String)` (không khớp hoặc phải được xử lý khác), và `process()` (mảng args có thể null). Đồng thời thử kết quả/ngoại lệ và các đường bỏ qua cơ chế AOP.

## 20. Ghi nhớ

- **`null` chỉ dùng như một giá trị tham chiếu**, không phải primitive hay một đối tượng có kiểu runtime.
- **Kiểu phương thức khai báo** và **đối tượng biểu diễn giá trị trong handler/advice** là hai lớp thông tin khác nhau; primitive có thể được boxing trong `Object[]`.
- `int` và `Integer` là hai kiểu khác nhau. **Unboxing null** gây lỗi, cast ngang giữa wrapper không phải chuyển số, narrowing có thể mất thông tin.
- **Pointcut theo chữ ký** khác điều kiện theo kiểu đối số runtime; ngữ nghĩa binding với null, primitive và kết quả phụ thuộc cơ chế AOP.
- Kết quả trả null, phương thức void và đường ném ngoại lệ **không đồng nghĩa**. Phương thức trả primitive đòi một giá trị tương thích ở ranh giới trả kết quả.
- **Advice chỉ để quan sát nên giữ nguyên đối số, kết quả, ngoại lệ và số lần thực thi**; nếu cần chuyển kiểu hoặc thay dữ liệu, phải có hợp đồng riêng.
- Kiểm thử cả trường hợp khớp lẫn không khớp, null, boxed primitive, overload, lỗi chuyển kiểu, giá trị trả về và phạm vi weaving/proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-005">Làm việc với giá trị trả về và phương thức không trả dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Làm việc với giá trị trả về và phương thức không trả dữ liệu

## 1. Giá trị trả về là dữ liệu gì trong advice?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), một **pointcut** chọn các *join point* có thể can thiệp; **advice** thực hiện hành vi bổ sung tại các điểm ấy. Khi join point là lời gọi hoặc lần thực thi phương thức, advice có thể cần biết **giá trị mà phương thức trả cho caller**, ví dụ để ghi thống kê, audit trạng thái, đo kết quả, kiểm tra hợp đồng hoặc áp dụng biến đổi có chủ đích.

Trọng tâm của bài là phân biệt rõ:

1. **Phương thức trả một giá trị:** `String`, `int`, một DTO, `Optional<T>`, `CompletableFuture<T>`, v.v.
2. **Phương thức khai báo `void`:** hoàn tất mà **không có giá trị trả về**.
3. **Phương thức trả tham chiếu `null`:** *có* hợp đồng kiểu trả về tham chiếu, nhưng giá trị cụ thể bằng `null`.
4. **Phương thức ném ngoại lệ:** kết thúc bằng đường exceptional completion; không có một kết quả trả về bình thường ở ranh giới đó.
5. **Phương thức trả một handle bất đồng bộ:** phương thức đã trả về bình thường nhưng dữ liệu cuối có thể **chưa sẵn sàng**.

Những tình huống này có ý nghĩa khác nhau. Nếu gộp `void`, `null`, kết quả rỗng và ném ngoại lệ thành “không có dữ liệu”, advice có thể ghi nhầm metric, ép kiểu lỗi, ném `NullPointerException` hoặc thay đổi giao diện nghiệp vụ.

**Tư duy đúng:** chọn **thời điểm advice** và **hợp đồng dữ liệu của join point** trước; sau đó mới quyết định cách đọc, kiểm tra, ghi hoặc biến đổi kết quả.

## 2. Mối quan hệ giữa return type, return value và completion

Một phương thức Java có thể có **kiểu trả về khai báo** và **giá trị trả về runtime**:

~~~java
String name() {
    return "Alice";
}

String missing() {
    return null;
}

void clear() {
    // hoàn tất bình thường nhưng không return một giá trị
}

int size() {
    return 3;
}
~~~

Các khai báo trên chỉ là minh họa; chưa phải chương trình độc lập có `main`.

| Phương thức | Kiểu khai báo | Kết quả bình thường | Ý nghĩa trong advice |
| --- | --- | --- | --- |
| `name()` | `String` | `"Alice"` | Có một tham chiếu `String` để quan sát |
| `missing()` | `String` | `null` | Vẫn là **normal return** |
| `clear()` | `void` | Không có giá trị | Có thể có sự kiện hoàn tất bình thường nhưng không có result nghiệp vụ |
| `size()` | `int` | `3` | Kết quả primitive; adapter dùng `Object` có thể boxing |
| `load()` ném `IOException` | Tùy chữ ký | Không có normal return cho lần này | Nhánh exceptional completion |

Một phương thức `boolean save()` trả `false` vẫn **kết thúc bình thường**; việc `false` biểu thị thất bại nghiệp vụ không biến nó thành exception. Tương tự, `Result.failure(...)` được trả bình thường vẫn có thể kích hoạt advice hậu trả về.

**Normal completion khác business success.** Khi thống kê, nên dùng tên rõ như `normal_return_count` thay vì mặc định đặt `successful_order_count` cho mọi lần advice sau khi trả về chạy.

## 3. Advice nào có thể quan sát kết quả?

Không phải mọi advice đều có cùng dữ liệu vào cùng thời điểm.

| Loại advice | Có biết kết quả trả về của lời gọi đang diễn ra? | Lưu ý chính |
| --- | --- | --- |
| **Before** | Chưa | Chỉ có thể dựa vào chữ ký, target, đối số/ngữ cảnh được cung cấp trước khi thực thi |
| **After returning** | Có thể, nếu engine hỗ trợ binding | Chỉ khi join point hoàn tất bình thường; cần xử lý `void`, `null` và binding theo engine |
| **After throwing** | Không có normal return của lần ấy | Quan sát ngoại lệ thoát khỏi join point; không phải giá trị trả về |
| **After/finally** | Tùy API; không bảo đảm có kết quả | Chạy khi rời phạm vi ở cả đường bình thường và ngoại lệ theo mô hình tương ứng |
| **Around** | Có thể nhận kết quả từ `proceed` | Có thể chuyển tiếp, giữ nguyên, thay thế, bỏ qua target hoặc xử lý lỗi tùy hợp đồng |

Ví dụ sơ đồ khái niệm:

~~~text
Caller
  |
  v
[Before]
  |
[Target/Join point]
  |
  +---- normal return: result --------> [After returning] ---> caller
  |
  +---- exceptional exit: error ------> [After throwing] ----> caller
                        \
                         [After/finally] trên đường rời phạm vi
~~~

Đây là sơ đồ về **điều kiện kích hoạt**, không phải đặc tả thứ tự chung cho mọi công cụ AOP. Một cơ chế `around` hoặc thứ tự nhiều advice có thể tạo ra nhiều tầng quan sát; result mà mỗi tầng thấy phụ thuộc **ranh giới được bao quanh**.

Cần xác minh bộ máy AOP có hỗ trợ binding giá trị trả về ở loại advice và join point đang dùng hay không. Đừng sao chép cú pháp tham số result từ công cụ này sang công cụ khác, hoặc giả định advice `before` đã biết kết quả tương lai.

## 4. Trường hợp `void`: không trả dữ liệu không có nghĩa là không hoàn tất

`void` trong Java diễn đạt rằng phương thức **không sản sinh giá trị trả về**:

~~~java
void publish(String message) {
    System.out.println(message);
    return;
}
~~~

Đây là đoạn minh họa, không phải chương trình đầy đủ. `return;` trong method `void` chỉ kết thúc phương thức; không trả object hay `null` theo hợp đồng source.

Nhưng `publish(...)` có thể:

- **Hoàn tất bình thường**, nên một advice theo dõi normal completion có thể chạy nếu engine hỗ trợ join point tương ứng.
- **Ném ngoại lệ**, khiến advice quan sát exceptional exit có thể chạy thay vào đó.
- **Bị bỏ qua bởi pointcut/proxy/weaving**, nên không có advice dù phương thức chạy.
- **Thực hiện side effect** (ghi DB, in log, gửi message), dù không có result object.

Vì thế `void` không đồng nghĩa “không có kết quả nghiệp vụ đáng quan sát”. Ta vẫn có thể ghi tên thao tác, thời gian, trạng thái hoàn tất, số lần gọi và ngoại lệ nếu có.

### 4.1. Khi công nghệ yêu cầu binding return value

Một số cơ chế chỉ cho bind giá trị trả về vào tham số advice nếu kiểu hoặc join point phù hợp. Với method `void`, có thể không có gì để bind, dù event normal completion vẫn tồn tại. **Đừng giả định** engine sẽ tự cung cấp đối tượng `Void` hay `null` cho mọi binding return.

Nếu chính sách chỉ cần biết method `void` hoàn tất bình thường, hãy chọn loại advice/cấu hình **không đòi một result object không tồn tại**. Nếu mã adapter dựa trên Reflection cung cấp `null` cho method `void`, đó là quy ước adapter, không phải giá trị return nghiệp vụ.

### 4.2. `void` và `Void` khác nhau

- `void` là từ khóa kiểu trả về **không có giá trị**.
- `Void` là một **kiểu tham chiếu** (`java.lang.Void`). Một phương thức khai báo `Void` có một contract trả về tham chiếu, thực tế trong mã Java thông thường thường là `null`.

Ví dụ:

~~~java
Void task() {
    doWork();
    return null;
}
~~~

Đây là đoạn minh họa thiếu `doWork()`. Không nên dùng `Void` chỉ để ép mọi phương thức `void` vào một giao diện kết quả; mỗi lựa chọn có ngữ nghĩa API riêng. Chẳng hạn `Callable<Void>` có thể biểu diễn một tác vụ kiểu generic với kết quả `null`, khác một phương thức khai báo `void` trực tiếp.

### 4.3. `void` khác `Optional.empty()`

`Optional.empty()` là một **object Optional hợp lệ** biểu thị vắng giá trị bên trong, không phải `null` và không phải `void`. Advice có thể quan sát một kết quả `Optional<T>` tồn tại rồi kiểm tra `isPresent()` theo nhu cầu. Không ép `Optional.empty()` thành exceptional completion.

## 5. Giá trị `null`: có thể là return bình thường

Một lỗi rất phổ biến trong advice sau khi trả về là:

~~~java
void observe(Object result) {
    System.out.println(result.toString());
}
~~~

Đây là đoạn minh họa. Nếu result bằng `null` thì chính advice ném `NullPointerException`, dù target có thể đã hoàn tất bình thường. Điều này gây ra hai sai lệch:

1. Caller có thể thấy lỗi mới không phát sinh từ logic nghiệp vụ.
2. Người vận hành có thể đếm nhầm lời gọi target thành công thành thất bại do advice.

Cách an toàn hơn cho quan sát đơn giản:

~~~java
String label = result == null ? "<null>" : result.getClass().getSimpleName();
~~~

Cần nhớ: **`String.valueOf(result)` gọi `toString()` khi result khác `null`**, nên vẫn có rủi ro tốn thời gian, xuất dữ liệu nhạy cảm hoặc lỗi từ code `toString()`. Nếu không cần toàn bộ nội dung kết quả, chỉ ghi **loại**, **trạng thái**, **kích thước có kiểm soát** hoặc trường đã cho phép.

Nếu hợp đồng API không cho phép `null` nhưng target vẫn trả `null`, đó là một vấn đề hợp đồng đáng kiểm thử. Advice audit không nên âm thầm “sửa” nó thành giá trị khác, trừ khi chính sách chuyển đổi được định nghĩa rõ.

## 6. Primitive, boxing/unboxing và kiểu chính xác

Phương thức trả primitive:

~~~java
int count() { return 7; }
boolean enabled() { return false; }
long bytes() { return 1024L; }
~~~

Khi một adapter Java Reflection trả kết quả qua `Object`, primitive thường được **boxing**:

- `int` → `Integer`
- `boolean` → `Boolean`
- `long` → `Long`

Trong khi đó, **kiểu trả về khai báo vẫn là primitive**, có thể nhận bằng `Method.getReturnType()`: `int.class`, `boolean.class`, `long.class`. Boxing ở tầng adapter **không thay đổi** chữ ký method.

Không được giả định rằng một result `Integer` luôn đến từ method khai báo `Integer`; nó có thể đến từ method khai báo `int`. Ngược lại, nếu một `around` adapter trả `null` cho phương thức primitive, proxy hoặc cơ chế hoàn trả có thể ném lỗi unboxing thay vì trả kết quả hợp lệ.

Ví dụ sai về mặt hợp đồng:

~~~text
target: int balance()
around: return null
caller: cần một int => nguy cơ NullPointerException/unboxing failure
~~~

Bởi vậy, một advice biến đổi result phải xác minh **return type của join point**, khả năng tương thích kiểu và giá trị. Với method `void`, không nên trả một object bất kỳ rồi tin rằng caller có thể sử dụng nó làm return value.

## 7. Phân biệt quan sát và biến đổi giá trị trả về

Đây là ranh giới thiết kế then chốt.

**After-returning** thường dùng để **quan sát** kết quả normal completion. Trong nhiều mô hình AOP, việc hàm advice tự `return` một object không có nghĩa nó được thay vào kết quả của target. Muốn đổi kết quả mà caller nhìn thấy, thường cần **around advice** hoặc một cơ chế return transformation đặc biệt được engine hỗ trợ.

Hai hợp đồng khác nhau:

~~~text
observe(result):
    metrics.record(resultMetadata)
    // Caller nhận result ban đầu

transform(proceed):
    original = proceed()
    changed = normalize(original)
    return changed
    // Caller có thể nhận changed
~~~

Đây là **pseudocode**, không phải ngôn ngữ pointcut dùng chung cho mọi engine AOP.

Ví dụ chính sách rõ ràng:

- **Quan sát**: khi `fetch("none")` trả `null`, ghi `result_is_null=true` rồi caller vẫn nhận `null`.
- **Biến đổi**: `around` chủ động đổi `null` thành `"fallback"` trước khi caller nhận, vì API quy định chính sách mặc định.

Chỉ lựa chọn thứ hai được phép thay result. Và dù biến đổi hợp lệ, advice vẫn cần bảo đảm:

1. Kiểu của result mới tương thích kiểu khai báo/hợp đồng Java.
2. Giá trị mới không làm caller hiểu nhầm trạng thái nghiệp vụ.
3. Không chạy target thêm lần ngoài ý định.
4. Không “nuốt” ngoại lệ và biến thành giá trị thành công trừ khi chính sách phục hồi được công bố.
5. Không làm mất metadata, correlation hoặc cấu trúc domain có ý nghĩa.
6. Các tầng advice bên trong/bên ngoài có thể nhìn **những result khác nhau**.

**Sửa một object mutable là một dạng thay đổi:** advice lấy `List` hoặc DTO rồi sửa field sẽ tác động tới caller dùng cùng tham chiếu, dù chính method advice không trả reference mới. Advice được ghi là “chỉ quan sát” nên tránh các thay đổi như vậy.

## 8. Ví dụ Java 17+ chạy độc lập: giá trị, void, null và transformation

Chương trình này dùng **JDK dynamic proxy** và Java Reflection để mô phỏng dữ liệu mà advice nhận ở ranh giới lời gọi:

- `observe(...)` thực hiện hành vi tương đương **after returning**, chỉ chạy khi target được chuyển tiếp **trả về bình thường**.
- Nó phân biệt `void` dựa vào **kiểu khai báo**, `null` dựa vào **giá trị runtime**, `int` qua boxing của Reflection và `CompletableFuture` còn pending.
- `withFallback(...)` là một **around wrapper khác** có chủ đích biến kết quả `null` của riêng `fetch` thành `"fallback"`. Điểm khác biệt giữa **đọc** và **thay** result sẽ nhìn thấy ở đầu ra.
- `crash()` ném ngoại lệ: observer **không in `AFTER crash`** vì không có normal return ở ranh giới đó.
- Mỗi lời gọi qua proxy được chuyển tiếp một lần; `InvocationTargetException` của reflection được bóc để không thay loại lỗi nghiệp vụ.

Tạo file `AdviceReturnDataDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

public final class AdviceReturnDataDemo {
    interface Operations {
        String fetch(String key);
        int sum(int left, int right);
        void reset();
        CompletableFuture<String> defer();
        String crash();
    }

    static final class Core implements Operations {
        public String fetch(String key) {
            System.out.println("BUSINESS fetch " + key);
            return "none".equals(key) ? null : "item:" + key;
        }

        public int sum(int left, int right) {
            return left + right;
        }

        public void reset() {
            System.out.println("BUSINESS reset");
        }

        public CompletableFuture<String> defer() {
            System.out.println("BUSINESS defer");
            return new CompletableFuture<>();
        }

        public String crash() {
            System.out.println("BUSINESS crash");
            throw new IllegalStateException("unavailable");
        }
    }

    static Object invoke(Operations target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Operations observe(Operations target) {
        return (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[]{Operations.class},
                (proxy, method, args) -> {
                    Object value = invoke(target, method, args);
                    String display;
                    if (method.getReturnType() == void.class) {
                        display = "<void>";
                    } else if (value == null) {
                        display = "<null>";
                    } else if (value instanceof CompletableFuture<?> future) {
                        display = "Future(done=" + future.isDone() + ")";
                    } else {
                        display = String.valueOf(value);
                    }
                    System.out.println("AFTER " + method.getName()
                            + " declared=" + method.getReturnType().getSimpleName()
                            + " value=" + display);
                    return value;
                });
    }

    static Operations withFallback(Operations target) {
        return (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[]{Operations.class},
                (proxy, method, args) -> {
                    Object result = invoke(target, method, args);
                    if (method.getName().equals("fetch") && result == null) {
                        System.out.println("AROUND fallback applied");
                        return "fallback";
                    }
                    return result;
                });
    }

    public static void main(String[] args) {
        Operations raw = observe(new Core());
        Operations transformed = withFallback(raw);

        System.out.println("CALLER " + raw.fetch("A"));
        System.out.println("CALLER " + raw.fetch("none"));
        System.out.println("CALLER " + transformed.fetch("none"));
        System.out.println("CALLER " + raw.sum(2, 3));
        raw.reset();
        System.out.println("CALLER reset done");

        CompletableFuture<String> pending = raw.defer();
        System.out.println("CALLER future.done=" + pending.isDone());
        pending.complete("async-done");
        System.out.println("CALLER future.value=" + pending.join());

        try {
            raw.crash();
        } catch (IllegalStateException error) {
            System.out.println("CALLER caught "
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AdviceReturnDataDemo.java
java AdviceReturnDataDemo
~~~

Đầu ra chính xác đã đối chiếu bằng chạy thực:

~~~text
BUSINESS fetch A
AFTER fetch declared=String value=item:A
CALLER item:A
BUSINESS fetch none
AFTER fetch declared=String value=<null>
CALLER null
BUSINESS fetch none
AFTER fetch declared=String value=<null>
AROUND fallback applied
CALLER fallback
AFTER sum declared=int value=5
CALLER 5
BUSINESS reset
AFTER reset declared=void value=<void>
CALLER reset done
BUSINESS defer
AFTER defer declared=CompletableFuture value=Future(done=false)
CALLER future.done=false
CALLER future.value=async-done
BUSINESS crash
CALLER caught IllegalStateException:unavailable
~~~

### 8.1. Vì sao `fetch("none")` có hai kết quả caller khác nhau?

Lần qua `raw`: target trả `null`, observer in `<null>`, rồi caller cũng nhận `null`. **Đó là advice chỉ quan sát**.

Lần qua `transformed`: wrapper `withFallback` gọi vào `raw` trước; `raw` vẫn quan sát `null` và in `AFTER ... <null>`. Sau đó wrapper ngoài thấy result `null`, thay bằng `"fallback"` rồi trả cho caller. Vì thế **observer bên trong và caller bên ngoài nhìn thấy hai result khác nhau**. Đây là hành vi có chủ đích của `around`, không phải một after-returning observer tự sửa kết quả.

Chính thứ tự wrapper quyết định tầng nào thấy giá trị gốc hay biến đổi. Đảo vị trí wrapper có thể làm thay đổi dữ liệu nhìn thấy ở mỗi advice.

### 8.2. Vì sao `reset()` ghi `<void>` thay vì `<null>`?

`Method.invoke()` với method `void` trả `null` trong adapter Reflection. Nếu chỉ xét `value == null`, ví dụ sẽ gộp `reset()` với `fetch("none")`; do đó observer kiểm tra `method.getReturnType() == void.class` **trước**. Đây là phân biệt theo **kiểu khai báo**, không phải một giá trị `Void` đặc biệt được return từ target.

### 8.3. Vì sao `sum(2,3)` in `int` nhưng `value` là `Object`?

Interface khai báo `int sum(int,int)`. Java Reflection cung cấp kết quả qua `Object` (primitive được boxing), nên observer có thể in `5`. Nhưng `Method.getReturnType()` vẫn là `int.class`, vì vậy hiển thị `declared=int`.

### 8.4. Vì sao không có `AFTER crash`?

`crash()` ném `IllegalStateException`. Hàm `invoke(...)` bóc `InvocationTargetException` rồi ném lại nguyên nhân, khiến lời gọi observer không đi tới đoạn in `AFTER`. Caller bắt đúng lỗi gốc. Nếu target bắt lỗi bên trong và **trả một kết quả dự phòng bình thường**, observer có thể chạy như mọi normal return khác.

### 8.5. Phạm vi và giới hạn của mô hình

Đây là **mô hình JDK proxy dùng để học dữ liệu trong advice**, không phải bộ weaving tổng quát. Nó chỉ intercept lời gọi **qua `raw` hoặc `transformed`**; gọi trực tiếp `new Core().fetch(...)` không chạy observer. Chương trình cố ý dùng interface method để lấy kiểu trả về và phản chiếu đúng lời gọi. Một engine AOP có thể khác về loại join point, phương thức đại diện, binding, thứ tự advice và khả năng can thiệp private/final/self-invocation.

Trong code `withFallback`, điều kiện chỉ theo tên `fetch` vì interface minh họa không có overload. Trong code thật, nên chọn rõ chữ ký hoặc marker, tránh một overload khác cũng bị thay result ngoài dự định.

## 9. Làm việc với phương thức không có result object

Nếu method `void` được chọn, advice vẫn có thể thực hiện nhiều nhiệm vụ **không cần giá trị trả về**:

| Mục tiêu | Dữ liệu phù hợp | Có cần return value không? |
| --- | --- | --- |
| Đếm số lần kết thúc bình thường của `void send()` | Tên thao tác, trạng thái normal completion | Không |
| Đo thời gian của `void update()` | Mốc bắt đầu và kết thúc | Không |
| Audit `void delete()` | Hợp đồng thao tác, ID đã được phép log, outcome | Không |
| Theo dõi lỗi của `void publish()` | Ngoại lệ thoát ra, loại lỗi | Không |
| Kiểm tra quyền trước `void reset()` | Principal/role/đối số theo hợp đồng | Không |
| Áp dụng cleanup sau `void flush()` | Tài nguyên/ngữ cảnh của lời gọi | Không |

**Không cần tạo kết quả giả** chỉ để logging. Việc thêm `return null;` vào code `void` là không hợp lệ theo cú pháp Java. Nếu một adapter generic dùng `Object` đại diện result cho mọi phương thức, cách biểu diễn `null` ở tầng adapter phải được quản lý có ý thức, không được xem là result object nghiệp vụ.

Một ví dụ pseudocode:

~~~text
afterNormalCompletion(joinPoint):
    if declaredReturnType == void:
        metrics.count("completed_void_operation")
    else:
        metrics.observeType(returnedValue)
~~~

Đây là **cách tư duy**, không phải API có sẵn của mọi công cụ AOP.

## 10. Dữ liệu null, rỗng và kết quả biểu diễn thất bại nghiệp vụ

Xét những trường hợp:

| Giá trị trả về | Trạng thái Java method | Có đồng nghĩa business success? |
| --- | --- | --- |
| `null` | Normal return nếu không có ngoại lệ | Không đủ thông tin |
| `Optional.empty()` | Normal return với container rỗng | Tùy hợp đồng |
| Danh sách rỗng `List.of()` | Normal return có object | Tùy hợp đồng |
| `false` | Normal return primitive/boxed | Tùy hợp đồng |
| `Result.failure("denied")` | Normal return một Result object | Có thể biểu thị từ chối nghiệp vụ |
| `void` hoàn tất | Normal completion, không có value | Tùy ý nghĩa thao tác |
| Ném `IllegalArgumentException` | Exceptional completion tại ranh giới | Không có normal result |
| Future chưa hoàn tất | Normal return một handle | Chưa biết task cuối thành công |

Không nên gán nhãn `SUCCESS` ở mọi event after-returning. Tên như `NORMAL_RETURN` hoặc `METHOD_COMPLETED` chính xác hơn nếu advice chỉ biết đường hoàn tất.

Nếu chính sách yêu cầu “nghiệp vụ thành công”, hãy diễn đạt cách tính theo từng hợp đồng: cờ `success` trong Result, mã HTTP, trạng thái persistence, completion của Future hoặc event miền. Một advice dùng chung cho nhiều API không tự biết ý nghĩa của từng kiểu giá trị.

## 11. Ngoại lệ: không có giá trị trả về bình thường

Khi một phương thức ném exception ra khỏi join point, không thể đọc một normal result của lần gọi ấy trong after-returning. Ví dụ:

~~~java
String load() {
    throw new IllegalStateException("unavailable");
}
~~~

Đây là đoạn minh họa, không có `main`. Một advice `after throwing` hoặc một `around` bắt và ném lại lỗi có thể quan sát exceptional exit nếu engine hỗ trợ. Nếu cần giữ hợp đồng, không nên biến lỗi thành `null` chỉ để tiếp tục luồng bình thường.

Các tình huống cần phân biệt:

- **Target ném lỗi nhưng tự catch và trả fallback:** join point quan sát ở ranh giới method ngoài có thể **normal return**.
- **Target ném lỗi ra khỏi method:** không có normal result; after-returning ở ranh giới ấy **không chạy**.
- **Advice after-returning tự ném lỗi:** target đã trả bình thường, nhưng caller có thể nhận ngoại lệ do advice. Nguồn lỗi khác nhau.
- **Around cố ý nuốt/biến đổi lỗi:** đây là chính sách điều khiển luồng, không còn là observer thuần túy.
- **`finally` trong target ném lỗi sau khi tính return:** kết quả cuối có thể là exceptional completion, không phải normal return đã dự kiến.

Khi dùng Reflection, `Method.invoke` có thể bọc lỗi do target ném trong `InvocationTargetException`. Nếu không bóc `getCause()` phù hợp, caller qua proxy có thể nhận loại lỗi không mong muốn. Tuy nhiên, không nên catch toàn bộ exception từ Reflection rồi mặc định coi chúng đều là lỗi nghiệp vụ: lỗi truy cập, ký kết API hoặc chính advice cũng có ý nghĩa riêng.

## 12. Thứ tự nhiều advice quyết định kết quả được nhìn thấy

Giả sử chuỗi:

~~~text
Caller
  |
[Outer around normalizer]
  |
[Inner after-returning observer]
  |
[Target] -> null
  |
Inner observer sees null
  |
Outer normalizer turns null -> fallback
  |
Caller sees fallback
~~~

Nếu đổi thứ tự hoặc chọn join point khác, observer có thể thấy `fallback` thay vì `null`. Do đó “giá trị trả về mà advice nhận” phải gắn với **ranh giới và thời điểm**.

Trường hợp phức tạp:

- Một `around` có thể trả cached result mà không gọi target; after-returning của wrapper bên ngoài có thể nhìn một normal return dù target không chạy.
- Một `around` khác có thể gọi `proceed` hai lần vì retry, khiến nhiều inner observations tương ứng với các attempt; outer observer có thể chỉ thấy result cuối.
- Một advice ném exception sau khi target hoàn tất bình thường, khiến outer layer quan sát lỗi dù inner layer đã quan sát normal return.
- Advice biến đổi kết quả có thể làm một phương thức khai báo trả primitive gặp lỗi khi chuyển đổi nếu output không tương thích.

Hãy mô tả precedence/ordering rõ theo engine AOP đang dùng và kiểm thử **giá trị thực sự caller nhận**, không chỉ kiểm tra log trong một advice đơn lẻ.

## 13. Bất đồng bộ: trả Future chưa phải trả dữ liệu cuối

Trong Java, phương thức:

~~~java
CompletableFuture<String> fetchAsync() {
    return new CompletableFuture<>();
}
~~~

đã **trả một đối tượng `CompletableFuture` bình thường**. After-returning ở ranh giới method có thể nhận handle đó ngay cả khi `future.isDone() == false`.

Nhưng “trả về Future” khác “tác vụ hoàn tất với `String`”:

1. **Return stage:** caller nhận `CompletableFuture<String>`; advice theo method normal return chạy tại đây.
2. **Completion stage:** Future có thể hoàn tất với value, hoàn tất ngoại lệ hoặc bị hủy vào một thời điểm sau.
3. **Consumption stage:** caller gọi `join()`, đăng ký callback hoặc sử dụng stage khác để đọc kết quả; exception tại đây không nhất thiết thoát trong lần gọi method tạo Future trước đó.

Nếu yêu cầu là log **giá trị cuối của tác vụ**, cần quan sát completion của Future bằng cơ chế như `whenComplete` hoặc một lớp instrumentation tương ứng:

~~~java
CompletableFuture<String> pending = startAsync();
pending.whenComplete((value, error) -> {
    if (error == null) {
        recordCompletionValue(value);
    } else {
        recordCompletionError(error);
    }
});
~~~

Đoạn trên là **minh họa không độc lập** vì `startAsync` và hai hàm `record...` là dịch vụ ứng dụng. `whenComplete` trả một stage mới; lỗi trong callback có thể ảnh hưởng stage phụ thuộc. Không tự giả định callback chạy trên thread của caller; cần xem ngữ cảnh, cancellation, propagation và chính sách lỗi của observer.

Không gọi `join()` ngay trong một after-returning chỉ để có dữ liệu cuối nếu nó không nằm trong hợp đồng: hành vi này có thể **chặn thread**, thay đổi latency và thứ tự lỗi.

## 14. Return types nâng cao: generics, erasure, covariant return và bridge

### 14.1. Generic return type

Với `List<String> names()`, chữ ký Java ở source có tham số kiểu `String`, nhưng thông tin runtime của generic chịu quy tắc **type erasure**. `Method.getReturnType()` thường trả kiểu thô `List.class`, còn `Method.getGenericReturnType()` có thể cung cấp metadata parameterized type nếu được lưu.

Advice chung không nên ép mọi phần tử thành `String` chỉ vì một khai báo generic trong tài liệu; cần tuân thủ hợp đồng type/metadata và kiểm tra ở ranh giới thích hợp.

### 14.2. Covariant return

Java có thể cho subclass override một phương thức với kiểu trả về hẹp hơn:

~~~java
class Base {
    Number value() { return 1; }
}
class Specialized extends Base {
    @Override
    Integer value() { return 2; }
}
~~~

Hai chữ ký nhìn ở base và subclass có thể có **return type khai báo khác nhau**. Một engine chọn call join point trên interface/base khác với execution join point trong implementation có thể phân giải metadata kiểu trả về khác nhau. Đừng tự áp đặt kết quả `Method.getReturnType()` của một `Method` lên mọi điểm gọi của cùng thao tác.

### 14.3. Bridge/synthetic methods

Trình biên dịch có thể tạo bridge methods để duy trì đa hình với generic/covariant return. Một bộ máy AOP ở mức bytecode có thể nhìn thấy phương thức không được viết trực tiếp trong source. Khi logging result hoặc đo số lần hoàn tất, hãy kiểm tra chính sách lọc bridge/synthetic của công cụ để tránh quan sát trùng hoặc nhầm signature.

### 14.4. Return type và kiểu của advice khác nhau

Return type của **target** không phải return type của **hàm advice**. Một hàm advice được engine yêu cầu trả `Object` hoặc `void` theo API riêng không có nghĩa nó thay đổi hợp đồng `int`, `String` hoặc `void` của phương thức được quan sát. Cần phân biệt ranh giới API của AOP engine với ranh giới public API của ứng dụng.

## 15. Lưu ý hiệu năng, bảo mật và tác dụng phụ

Kết quả trả về có thể là object rất lớn, collection chưa được materialize, stream lazy, object có `toString()` thực hiện I/O hoặc dữ liệu nhạy cảm. Lấy toàn bộ object graph để log có thể tốn hơn cả lời gọi nghiệp vụ.

Các hướng dẫn thực hành:

- Ghi **metadata có giá trị** (tên operation, loại outcome, số phần tử được phép đếm, trạng thái) thay vì toàn bộ payload.
- Không dùng `toString()` trên object tùy ý nếu mục tiêu chỉ là biết có result hay không.
- Không đọc hết `Stream` hay iterator chỉ để log kích thước: thao tác đó có thể tiêu thụ dữ liệu mà caller cần.
- Không sửa `List`, `Map` hoặc DTO mutable khi advice được mô tả là observer.
- Phân biệt lỗi của sink log/metrics với ngoại lệ của target; có chính sách fail-open/fail-closed rõ, không nuốt lỗi tùy tiện.
- Tránh log password, access token, dữ liệu khách hàng hoặc nội dung ngoại lệ có thể chứa dữ liệu riêng tư.
- Với result không đồng bộ, không block trong after-returning để “chờ dữ liệu thật”.
- Đảm bảo observer thread-safe nếu được dùng chung giữa nhiều request; tránh lưu result riêng một lần gọi vào field mutable singleton.
- Giới hạn cardinality nếu đưa loại return/result state vào nhãn metrics; không dùng mọi giá trị business làm label.
- Nếu result là object lazy hoặc resource-bound, chỉ quan sát những đặc điểm có thể đọc an toàn mà không kích hoạt việc tiêu thụ hoặc đóng resource.

## 16. Giới hạn theo cách can thiệp của công cụ AOP

Một biểu thức pointcut chọn được `void reset()` trên lý thuyết không đồng nghĩa advice chắc chắn chạy: điều đó còn phụ thuộc engine, loại join point và ranh giới thực thi.

| Bối cảnh | Điều cần xác minh |
| --- | --- |
| Proxy dựa trên interface | Lời gọi có đi qua proxy không? Interface có khai báo method đó không? |
| Proxy cần override method | Method `private`/`final` hoặc những modifier khác có bị giới hạn không? |
| Bytecode weaving | Lớp và loại join point có thực sự nằm trong vùng weaving được cấu hình không? |
| Method call vs method execution | Return type/metadata được hiểu ở caller hay implementation? |
| Self-invocation | `this.method()` có bỏ qua proxy không? |
| Method `void` | Engine có cho after-returning không cần binding return object không? |
| Generics/bridge | Tool có xem bridge/synthetic methods là join point riêng không? |
| Method trả Future | Tool quan sát method return hay completion của task? |
| Lỗi trong advice | Caller nhìn exception nào, có policy bỏ qua lỗi observer không? |

Không phải mọi công nghệ đều có cùng cú pháp, cùng phạm vi binding result hoặc cùng loại advice. Muốn biết chắc phải xem mô hình join point của engine đang dùng và viết integration test trên **đường gọi thực**, không chỉ gọi unit test của hàm advice.

## 17. Quy trình thiết kế advice xử lý kết quả

1. **Viết mục đích rõ:** chỉ đo số normal return, log trạng thái, hay chủ động biến đổi kết quả?
2. **Chọn join point/pointcut:** call/execution, method cụ thể, overload/annotation và đường can thiệp thực tế.
3. **Chọn loại advice ít quyền lực nhất:** after-returning để quan sát normal result; around nếu thật sự cần thay/điều khiển result.
4. **Phân loại return type:** `void`, primitive, tham chiếu có thể `null`, container, generic, Future.
5. **Xác minh binding:** engine có cung cấp result cho loại join point đó không? Khi `void` thì làm gì?
6. **Quy định null policy:** ghi nhận `null` hợp lệ, cảnh báo vi phạm hợp đồng hay normalize theo một chính sách chuyển đổi rõ.
7. **Bảo vệ hợp đồng caller:** giá trị, kiểu, exception, số lần target chạy và tài nguyên phải được giữ nguyên nếu chỉ quan sát.
8. **Hạn chế dữ liệu truy cập:** chỉ lấy fields/metadata đã cho phép; tránh serialize toàn bộ object.
9. **Tính đến async/lazy:** không coi Future return là task success; không tiêu thụ Stream cho log.
10. **Kiểm thử tích hợp:** cùng method qua proxy/weaving, đường trực tiếp, nhánh lỗi và nhiều wrapper khác thứ tự.

Một matrix kiểm thử cho code `AdviceReturnDataDemo`:

| Case | Loại hoàn tất | Kỳ vọng observer | Kỳ vọng caller |
| --- | --- | --- | --- |
| `fetch("A")` | Normal, `String` | `item:A` | `item:A` |
| `fetch("none")` qua `raw` | Normal, `null` | `<null>` | `null` |
| `fetch("none")` qua `transformed` | Normal bên trong + biến đổi bên ngoài | Observer trong thấy `<null>` | Caller thấy `fallback` |
| `sum(2,3)` | Normal, primitive `int` | `declared=int, value=5` | `5` |
| `reset()` | Normal, `void` | `declared=void, value=<void>` | Không có return value |
| `defer()` | Normal return Future pending | `Future(done=false)` | Nhận Future, hoàn tất sau |
| `crash()` | Exceptional exit | Không có `AFTER crash` | Nhận `IllegalStateException` gốc |
| Target gọi trực tiếp không qua proxy | Tùy target | Không có observer proxy | Theo hợp đồng target |

## 18. Các lỗi thường gặp

1. **Đồng nhất `void` với `null`:** Reflection adapter có thể biểu diễn cả hai qua `null`, nhưng ý nghĩa contract khác nhau.
2. **Giả định before advice đã đọc được return value:** kết quả chưa tồn tại.
3. **Cho rằng after-returning là mọi lần nghiệp vụ thành công:** `false` hoặc `Result.failure` vẫn có thể trả bình thường.
4. **Dùng `result.toString()` vô điều kiện:** lỗi khi `null` và có thể lộ dữ liệu.
5. **Return `null` từ around cho primitive:** phá hợp đồng trả `int`/`boolean`.
6. **Coi `Void` bằng `void`:** kiểu tham chiếu khác hoàn toàn từ khóa không trả giá trị.
7. **Đổi result mà không công bố:** caller nhận kết quả bất ngờ dù tên advice chỉ là logging.
8. **Sửa object mutable rồi nghĩ đó chỉ là quan sát:** caller có thể thấy đối tượng bị thay đổi.
9. **Coi `Optional.empty()` là không có normal return:** nó là một object/container hợp lệ.
10. **Coi Future chưa hoàn tất là task thành công:** mới chỉ có handle được trả bình thường.
11. **Làm `join()` trong after-returning:** có thể block và thay đổi ngữ nghĩa async.
12. **Không bóc `InvocationTargetException` khi dùng reflection:** caller nhìn sai nguồn lỗi.
13. **Không kiểm tra overload, bridge và loại join point:** chọn nhầm phương thức hoặc quan sát trùng.
14. **Giả định proxy nhìn được self-invocation hoặc private method:** phải kiểm thử ranh giới thật.
15. **Ghi mọi result vào log:** chi phí, rò dữ liệu, tiêu thụ lazy Stream và side effect ngoài ý định.
16. **Để lỗi metrics tự che target exception:** làm caller nhận lỗi sai và sai số liệu.
17. **Bỏ qua thứ tự các around wrapper:** observer có thể thấy dữ liệu gốc khác caller.
18. **Chỉ unit test observer mà không integration test AOP:** chưa chứng minh advice chạy đúng điểm.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Phương thức khai báo `String fetch()` trả `null`. Có thể có after-returning advice không?  
**Trả lời:** Có, vì target hoàn tất bình thường; advice phải xử lý `null` an toàn.

**Câu 2.** Phương thức `void reset()` không trả object. Có thể đo thời gian hoặc đếm normal completion không?  
**Trả lời:** Có, nếu engine hỗ trợ join point và advice tương ứng; không cần bind một result object không tồn tại.

**Câu 3.** `Void task()` và `void task()` giống nhau hoàn toàn không?  
**Trả lời:** Không. `Void` là reference type, có thể trả `null`; `void` không trả giá trị.

**Câu 4.** Vì sao một observer dùng `Method.invoke()` nên kiểm tra `getReturnType()` trước khi diễn giải `null`?  
**Trả lời:** Vì `Method.invoke` trả `null` cả khi method `void` và khi method tham chiếu thực sự trả `null`.

**Câu 5.** Nếu `around` đổi `null` thành `fallback`, after-returning ở bên trong và caller bên ngoài có thấy cùng result không?  
**Trả lời:** Không bắt buộc; observer trong có thể thấy `null`, caller ngoài thấy `fallback`.

**Câu 6.** Khi method khai báo `int` và adapter nhận một `Integer`, kiểu trả về method có đổi thành `Integer` không?  
**Trả lời:** Không; đây là boxing trong adapter; kiểu khai báo vẫn `int`.

**Câu 7.** Method trả `CompletableFuture` pending. Sau khi after-returning chạy, task đã hoàn tất chưa?  
**Trả lời:** Chưa chắc. Method đã trả handle, còn task completion là sự kiện khác.

**Câu 8.** Vì sao không dùng kết quả after-returning để tự kết luận mọi thao tác là business success?  
**Trả lời:** Nó chỉ xác nhận normal completion của ranh giới; `null`, `false`, `Optional.empty()` và `Result.failure` có ý nghĩa miền khác nhau.

**Câu 9.** Advice chỉ quan sát có nên ghi toàn bộ result vào log không?  
**Trả lời:** Không mặc định; chỉ ghi metadata/dữ liệu được phép, tránh chi phí, rò thông tin, thay đổi object hoặc tiêu thụ dữ liệu lazy.

## 20. Tổng kết

**Dữ liệu trả về trong advice phải được hiểu theo cả kiểu khai báo, giá trị runtime và cách join point hoàn tất.** Method `void` không có result object nhưng vẫn có thể hoàn tất bình thường; method kiểu tham chiếu có thể trả `null`; method primitive có thể được boxing trong adapter; Future trả bình thường không đồng nghĩa tác vụ hoàn tất.

Hãy dùng **after-returning** khi muốn quan sát normal result theo khả năng binding của engine, **after-throwing** khi cần quan sát ngoại lệ, và **around** chỉ khi có hợp đồng thực sự yêu cầu điều khiển hoặc thay đổi kết quả. Với advice thuần quan sát, bảo toàn return value, exception, dữ liệu đầu vào và số lần thực thi target. Cuối cùng, kiểm thử đủ `void`/`null`/primitive/reference/async/exception và ranh giới proxy/weaving thực để bảo đảm dữ liệu advice nhìn thấy là đúng, an toàn và không gây tác dụng phụ ngoài ý định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-006">Đọc annotation và dữ liệu cấu hình liên quan</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc annotation và dữ liệu cấu hình liên quan trong advice AOP

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, *advice* là hành vi bổ sung chạy tại các **join point** được *pointcut* lựa chọn. Ngoài tên phương thức, đối số, đối tượng đích và kết quả thực thi, advice có thể cần đọc **annotation** hoặc **dữ liệu cấu hình** để biết *tại sao điểm đó được áp dụng chính sách* và *phải áp dụng chính sách như thế nào*.

Ví dụ, nhiều phương thức nghiệp vụ đều cần audit, nhưng mỗi phương thức cần tên sự kiện hoặc mức ghi nhận khác nhau. Thay vì viết riêng logic audit ở từng phương thức, có thể dùng annotation để khai báo metadata:

```java
// Minh họa ý tưởng, chưa phải tệp Java độc lập.
@AuditEvent(name = "document.published", includeResult = false)
String publish(String documentId);
```

Một advice được kích hoạt tại phương thức phù hợp có thể đọc metadata này để xác định **tên sự kiện** và **có cho phép ghi thông tin kết quả hay không**. Đồng thời, một cấu hình riêng có thể bật/tắt audit ở một số thao tác, đặt giới hạn kích thước bản ghi hoặc chọn nơi xuất sự kiện.

**Mấu chốt:** Annotation là *metadata được khai báo trong mã*, cấu hình là *nguồn chính sách/giá trị bổ sung*, còn advice là *nơi sử dụng dữ liệu ấy để thực hiện hành vi*. Ba thứ liên quan nhưng không đồng nghĩa. Việc một annotation xuất hiện trên mã **không tự động** khiến advice chạy; cơ chế AOP vẫn phải có pointcut phù hợp, truy cập được metadata và thực sự áp dụng advice vào đường thực thi.

> Bài này nói về **AOP nói chung**, gồm kiến thức nền tảng và các giới hạn nâng cao trong Java. Java Reflection được dùng để giải thích cách đọc annotation, còn các quy tắc phối hợp annotation với cấu hình được xây dựng tường minh. Không có một ngôn ngữ pointcut, thứ tự ưu tiên cấu hình hay cơ chế tự đọc annotation nào là bắt buộc cho mọi công cụ AOP.

## 1. Annotation và cấu hình đóng vai trò gì trong advice?

Một aspect audit có thể cần bốn nhóm dữ liệu:

| Nhóm dữ liệu | Ví dụ | Vai trò |
| --- | --- | --- |
| **Metadata tĩnh của join point** | Phương thức `publish(String)`, kiểu khai báo | Xác định điểm được can thiệp |
| **Annotation trên code** | `@AuditEvent(name="document.published")` | Mô tả ý định/chính sách của API |
| **Cấu hình liên quan** | `audit.enabled=true`, danh sách thao tác loại trừ | Điều chỉnh chính sách mà không phải sửa logic audit |
| **Ngữ cảnh lượt gọi** | `documentId`, kết quả, exception, trace ID | Cung cấp dữ liệu động để advice xử lý |

Các nguồn có vòng đời khác nhau:

- Annotation thông thường gắn với class/method/parameter được biên dịch. Thay đổi metadata này thường cần thay code và build/reload thành phần tương ứng.
- Cấu hình có thể được nạp từ file, biến môi trường, kho cấu hình hoặc giá trị tạo lúc khởi động; khả năng cập nhật động là đặc tính triển khai, không tự có.
- Đối số/kết quả/ngoại lệ là dữ liệu **mỗi lần gọi**.
- Một số dữ liệu ngữ cảnh gắn với thread/task và không tự động lan qua các ranh giới bất đồng bộ.

**Đọc được annotation** không bảo đảm aspect đã **được kích hoạt**. **Đọc được cấu hình** không bảo đảm kết quả **an toàn nghiệp vụ**. Cả hai còn cần quy tắc chọn, thực thi và kiểm thử.

## 2. Phân biệt pointcut dùng annotation và advice đọc annotation

Có hai việc rất dễ lẫn:

**A. Chọn join point bằng annotation.** Một pointcut có thể biểu diễn ý tưởng “chỉ chọn những method được đánh dấu bởi annotation X”, nếu cơ chế AOP có khả năng matching annotation ở loại join point đó.

**B. Đọc giá trị annotation trong advice.** Sau khi lời gọi được chọn, advice đọc thuộc tính như `name`, `includeResult` hoặc `level` để biết cách xử lý.

```text
Source code
  @AuditEvent(name = "document.published")
  publish(String id)
       |
       +--> Pointcut: có chọn method này không?
                    |
                    +--> Advice: đọc name, includeResult ...
                                  |
                                  +--> Audit sink / metric / tracing
```

Một pointcut có thể chọn theo **tên/chữ ký phương thức** rồi advice tự đọc annotation như dữ liệu tùy chọn. Ngược lại, một pointcut dựa trên annotation có thể thực hiện việc chọn trước khi advice chạy. Cơ chế nào hiệu quả và chính xác phụ thuộc engine.

Nếu pointcut quá rộng mà advice tự kiểm tra annotation rồi bỏ qua thao tác, advice **vẫn có thể được gọi** trên mọi join point được chọn rộng đó, gây overhead và rủi ro. Nếu pointcut chọn bằng annotation nhưng advice lại tìm annotation ở một vị trí metadata khác, có thể xảy ra tình huống pointcut khớp còn advice đọc `null`.

### Ví dụ tình huống không đồng bộ về nơi tìm metadata

- Điểm gọi được viết qua một **interface method có annotation**.
- Implementation override method đó nhưng **không lặp lại annotation**.
- Engine cung cấp cho advice `Method` của implementation.
- Advice chỉ gọi `implementationMethod.getDeclaredAnnotation(...)`.

Khi đó advice có thể nhận `null` dù annotation có mặt trên interface method. Đây là vấn đề **quy tắc resolve metadata**, không phải bằng chứng annotation bị mất khỏi code nguồn.

## 3. Annotation Java thực sự là gì?

Annotation là metadata gắn vào phần tử của chương trình. Ta định nghĩa một annotation type bằng `@interface`:

```java
// Khai báo minh họa, được đặt ở phạm vi type thích hợp.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface AuditEvent {
    String name();
    boolean includeResult() default false;
}
```

Đây là đoạn khai báo minh họa, **chưa có các import cho annotation API và chưa có main()**.

Các chi tiết quan trọng:

- `@Retention` quyết định annotation được giữ tới giai đoạn nào. `RUNTIME` cho phép Reflection đọc khi chương trình chạy.
- `@Target` quy định annotation có thể gắn ở đâu, chẳng hạn class, method, parameter, field hoặc type-use tùy khai báo.
- `name()` là phần tử bắt buộc: nơi dùng annotation phải cung cấp giá trị.
- `includeResult() default false` có giá trị mặc định nếu không khai báo tường minh.
- Annotation là **metadata**, không phải lời gọi hàm, interceptor hay việc đăng ký aspect.

Annotation element có những kiểu được Java cho phép như primitive, `String`, `Class`, enum, annotation và mảng của các kiểu hợp lệ; không thể khai báo phần tử annotation là một object nghiệp vụ tùy ý. Giá trị annotation được xác định theo cơ chế khai báo/biên dịch Java, không phải một biểu thức tùy ý được chạy theo từng invocation.

### `SOURCE`, `CLASS` và `RUNTIME`

| Retention | Đặc điểm chính | Reflection runtime thông thường đọc được? |
| --- | --- | --- |
| `SOURCE` | Metadata dành cho xử lý ở source | Không |
| `CLASS` | Ghi vào class file nhưng không yêu cầu JVM giữ để Reflection runtime truy xuất | Không theo Reflection runtime thông thường |
| `RUNTIME` | Được lưu để có thể truy xuất qua Reflection khi chạy | Có, tại phần tử được hỗ trợ |

**Lưu ý:** Một công cụ AOP áp dụng weaving lúc biên dịch hoặc phân tích bytecode có thể sử dụng metadata `CLASS` theo cách khác Reflection runtime. Vì vậy, câu hỏi “annotation có đọc được không?” phải đi kèm câu hỏi **“đọc bằng cơ chế nào, ở giai đoạn nào?”**.

## 4. Đọc annotation từ Method bằng Reflection

Java cung cấp `java.lang.reflect.Method` và các API annotation tương ứng:

```java
// Đoạn minh họa đặt trong nơi có biến Method method.
AuditEvent direct = method.getDeclaredAnnotation(AuditEvent.class);
AuditEvent visible = method.getAnnotation(AuditEvent.class);
Annotation[] allDirect = method.getDeclaredAnnotations();
```

Các API phục vụ mục tiêu khác nhau:

- `getDeclaredAnnotation(AuditEvent.class)`: lấy annotation được khai báo trực tiếp tại phần tử này, hoặc `null`.
- `getAnnotation(AuditEvent.class)`: tìm annotation theo semantics của API `AnnotatedElement`, chú ý quy tắc inherited annotation chỉ áp dụng ở những trường hợp Java quy định.
- `getDeclaredAnnotations()`: liệt kê annotation được khai báo trực tiếp.
- `getAnnotationsByType(...)`: hữu ích khi annotation được khai báo dạng **repeatable**, có semantics container/inheritance thích hợp.
- `getParameterAnnotations()`: cho biết annotation gắn với từng tham số hình thức của method.
- `getAnnotatedReturnType()`: API dành cho metadata annotation ở **type-use** của kiểu trả về, khác annotation đặt trực tiếp trên method.

Khi đã có instance annotation:

```java
if (direct != null) {
    String eventName = direct.name();
    boolean includeResult = direct.includeResult();
}
```

Đó là cách đọc **metadata**, chưa nói gì về việc advice chạy ở join point nào. Code sản xuất phải xác định xử lý thế nào nếu annotation vắng mặt hoặc trả giá trị không phù hợp chính sách.

### Method annotation khác parameter annotation

```java
// Ví dụ khái niệm, không phải tệp Java độc lập.
@AuditEvent(name = "document.saved")
void save(@Sensitive String secret, String publicId);
```

Annotation `@AuditEvent` nằm trên **method**; `@Sensitive` nằm trên **parameter**. Đọc bằng `method.getAnnotation(AuditEvent.class)` sẽ không tự đọc `@Sensitive`. Muốn hiểu tham số nào cần ẩn, phải đọc `method.getParameterAnnotations()` hoặc metadata khác theo đúng `@Target` và `@Retention`.

Tên biến tham số, metadata của annotation tham số và **giá trị đối số runtime** cũng là ba dữ liệu riêng. Việc tham số được đánh dấu nhạy cảm không có nghĩa advice được phép in giá trị của nó ra log.

## 5. Annotation trên interface và implementation

Trong AOP có proxy, lời gọi thường đi qua interface. Với Java Dynamic Proxy, `InvocationHandler` nhận một `Method` của interface proxy. Nhưng phần logic thực thi nằm trong **implementation object** có thể có annotation khác.

```java
// Mã minh họa, thiếu phần khai báo annotation.
interface DocumentApi {
    @AuditEvent(name = "api.publish")
    String publish(String id);
}

class DocumentService implements DocumentApi {
    @Override
    @AuditEvent(name = "service.publish")
    public String publish(String id) {
        return "done:" + id;
    }
}
```

Hai vị trí annotation có thể mang ý nghĩa khác nhau:

1. **Interface method:** metadata của contract API.
2. **Implementation method:** metadata của một implementation cụ thể.

Một advice nên lựa chọn quy tắc rõ ràng, ví dụ:

- Ưu tiên annotation trực tiếp trên **implementation method** nếu có.
- Nếu không có, sử dụng annotation trên **interface method**.
- Nếu cả hai không có, có thể xét annotation ở **class** hoặc cấu hình mặc định nếu đó là contract thiết kế.

Đây là **quy tắc do ứng dụng quyết định**, không phải “quy tắc ưu tiên annotation” được Java hoặc mọi engine AOP cung cấp sẵn. Các hệ thống có thể chọn interface làm nguồn chính để bảo vệ contract thay vì cho implementation override.

### Lấy implementation Method sao cho đúng

Nếu có `Method interfaceMethod` và `Object target`, một cách đơn giản đối với public method:

```java
Method implementationMethod = target.getClass().getMethod(
        interfaceMethod.getName(),
        interfaceMethod.getParameterTypes());
```

`getMethod(...)` tra cứu public method có tên và danh sách kiểu tham số phù hợp, bao gồm method public được kế thừa. Điều này thuận tiện cho interface proxy trong ví dụ đơn giản. Nhưng code tổng quát cần tính đến method không public, class ẩn, bridge, generic erasure, covariant return, module access và việc cùng signature xuất hiện ở nhiều cấp hierarchy.

**Không phải mọi implementation method đều có thể lấy bằng `getDeclaredMethod(...)` trên concrete class.** Một method có thể được kế thừa từ superclass; `getDeclaredMethod` chỉ xét method khai báo trực tiếp trên lớp được hỏi.

## 6. Annotation method có tự động được “kế thừa” khi override không?

**Không nên giả định annotation đặt trên method ở superclass/interface sẽ tự xuất hiện trên method override.**

Trong Java, `@Inherited` chỉ tạo cơ chế tìm kiếm kế thừa cho **annotation trên class**, theo quy tắc áp dụng với superclass, **không làm annotation trên method hay interface tự được kế thừa cho method override**.

Ví dụ:

```java
// Ví dụ minh họa:
class Parent {
    @AuditEvent(name = "parent.work")
    public void work() { }
}

class Child extends Parent {
    @Override
    public void work() { }
}
```

Dù `Parent.work` được đánh dấu, việc hỏi annotation trực tiếp trên `Child.work` có thể trả `null`. Một thư viện AOP có thể tự xây quy tắc tìm ngược method ở superclass hoặc interface; **đó là hành vi của resolver/tool**, không phải Java tự sao chép annotation vào override.

### Quy tắc với class-level annotation

Nếu annotation type được đánh dấu thêm `@Inherited` và được gắn vào một class cha, `Class.getAnnotation(...)` trên class con có thể nhìn thấy annotation đó khi class con không khai báo annotation tương ứng, theo quy tắc Java. Nhưng:

- Không áp dụng như vậy cho annotation ở method.
- Không tự lan từ annotation gắn trên interface sang implementing class qua cơ chế `@Inherited`.
- `getDeclaredAnnotation(...)` trên class con vẫn chỉ xét annotation **được khai báo trực tiếp**.
- Resolver tùy chỉnh có thể quyết định xử lý khác và phải có test cho lựa chọn đó.

**Kết luận:** Luôn xác định nơi metadata được gắn và nơi advice đọc nó. “Annotation tồn tại đâu đó trong hierarchy” không đồng nghĩa “Reflection tại method hiện tại tự trả về annotation”.

## 7. Annotation ở class, method, parameter và return type

Trong một hệ thống lớn, cùng một concern có thể có nhiều cấp:

| Nơi khai báo | Ý nghĩa ví dụ | Lưu ý |
| --- | --- | --- |
| **Class/type** | Mặc định audit cho mọi thao tác của service | Dễ quá rộng; nên có cơ chế loại trừ |
| **Method** | Tên event, cấp log, có ghi kết quả không | Chi tiết hơn nhưng có thể khác ở override |
| **Parameter** | Dữ liệu nào nhạy cảm, vai trò từng đối số | Metadata khác giá trị runtime |
| **Return type-use** | Ràng buộc dữ liệu đầu ra | Phải dùng đúng reflection API type-use |
| **Annotation lặp lại** | Nhiều policy trên cùng phần tử | Cần `@Repeatable` và API đọc thích hợp |

Ví dụ một class có mặc định và method override metadata:

```java
// Mã ý tưởng: giả định annotation type cho phép @Target(TYPE, METHOD).
@AuditEvent(name = "default.operation")
class ReportService {
    @AuditEvent(name = "report.export", includeResult = false)
    public String export(String id) {
        return "exported:" + id;
    }

    public String status() {
        return "READY";
    }
}
```

Một resolver có thể chọn **method-level override class-level**. Nhưng nếu yêu cầu là “các operation không có annotation không được audit”, thì **không nên tự động dùng class default**. Phải diễn tả policy mong muốn rõ ràng, thay vì suy ra từ vị trí annotation.

### Annotation vắng mặt là trường hợp bình thường

`getAnnotation(...)` có thể trả `null`. Advice cần quyết định:

- **Không áp dụng hành vi** nếu annotation thiếu.
- **Dùng default ở class**.
- **Dùng default từ cấu hình**.
- **Báo lỗi cấu hình** nếu metadata bắt buộc nhưng không có.

Không ép gọi `tag.name()` khi `tag == null`. Một lỗi `NullPointerException` từ advice có thể khiến nghiệp vụ thất bại dù target vốn không có lỗi.

## 8. Đọc dữ liệu cấu hình liên quan: nên nằm ở đâu?

Annotation thường tiện để thể hiện **ý định ổn định của API**, còn cấu hình phù hợp với những giá trị có thể thay đổi theo môi trường hoặc deployment, chẳng hạn:

- Bật/tắt một chính sách observability.
- Chọn sink hoặc phạm vi xuất log/audit.
- Ngưỡng kích thước event cho phép ghi.
- Danh sách operation được loại trừ.
- Quy tắc sampling metric/tracing.
- Mapping tên event sang một category do đơn vị vận hành quản lý.

Nhưng “cấu hình ngoài” không bắt buộc là đọc file trong mỗi advice. Có thể **nạp một snapshot cấu hình vào bộ nhớ**, cung cấp qua một service cấu hình, hoặc đọc từ môi trường tại một thời điểm được xác định.

### Đường dữ liệu mẫu

```text
Annotation trên method/class       Cấu hình ngoài
    |                                   |
    +------ metadata resolver           |
                    |                    |
                    +--- policy merger --+
                               |
                         ResolvedPolicy
                               |
                       Advice tại join point
                               |
                        Audit/metric/tracing
```

**Annotation reader** nên chịu trách nhiệm tìm metadata. **Configuration provider** chịu trách nhiệm cung cấp các giá trị được xác thực. **Policy resolver** chịu trách nhiệm ghép chúng và áp quy tắc ưu tiên. **Advice** sử dụng policy cuối để làm một hành vi có contract rõ ràng. Chia trách nhiệm như vậy giúp code kiểm thử được và tránh một interceptor phải tự biết mọi kiểu nguồn cấu hình.

## 9. Xác định thứ tự ưu tiên metadata và cấu hình

Không có thứ tự ưu tiên phổ quát. Một chính sách thiết kế có thể là:

1. **Cấu hình an toàn cấp toàn cục:** vô hiệu hóa một concern, giới hạn dữ liệu được phép ghi hoặc danh sách operation bị cấm ghi.
2. **Annotation trên implementation method:** điều chỉnh behavior cụ thể của implementation.
3. **Annotation trên interface method:** thể hiện API contract.
4. **Annotation trên class:** mặc định của một nhóm method.
5. **Default nội bộ:** hành vi rõ ràng khi không có nguồn nào đưa giá trị.

Trong một hệ thống khác, API contract có thể **không cho implementation override** một phần cấu hình bảo mật. Vì vậy thứ tự không chỉ là kỹ thuật; nó thể hiện quyền sở hữu chính sách.

### Ví dụ về conflict

```text
Interface method: @AuditEvent(name = "api.operation")
Implementation:   @AuditEvent(name = "impl.operation")
Class:            @AuditEvent(name = "class.default")
Config:           audit.enabled = false
```

Nếu cấu hình bật/tắt có quyền ưu tiên, advice **không ghi audit** bất kể annotation nêu tên event gì. Nếu enabled=true và method implementation có annotation, resolver có thể chọn `impl.operation`. Nhưng chính sách này là **lựa chọn thiết kế đã công bố**, không thể suy ra từ annotation Java.

### Không ghép bằng sự tình cờ

Tránh kiểu:

```java
// Pseudocode thể hiện một lựa chọn dễ mơ hồ.
String name = methodTag != null ? methodTag.name()
            : classTag != null ? classTag.name()
            : externalConfig.get("name");
```

Cần trả lời: `name=""` có nghĩa là “dùng giá trị mặc định” hay “tên event rỗng hợp lệ”? `null` biểu thị thiếu, lỗi đọc hay intentional disable? Một Boolean cấu hình `false` là false thật, không phải “không có cấu hình”. Thứ tự phải được diễn đạt thành một rule có test, không dựa vào truthiness hoặc chuỗi rỗng không được định nghĩa.

## 10. Ví dụ Java 17+ chạy được: advice đọc annotation và cấu hình

Ví dụ dưới đây dùng **Java Dynamic Proxy** như một cơ chế interception nhỏ. Nó kết hợp:

- Annotation `@AuditTag` trên **interface method**, **implementation method** và **implementation class**.
- Một cấu hình `Map<String,Boolean>` điều khiển enable/disable ở ranh giới method.
- Resolver có thứ tự: **implementation method → interface method → implementation class**.
- Advice đọc `value()` và `includeResult()` để quyết định thông tin audit.
- Nhánh thành công và lỗi, có truyền tiếp exception gốc.

Đây là **bài tập Java SE độc lập**, không phải triển khai một ngôn ngữ pointcut AOP hoàn chỉnh. Handler được gọi bởi proxy cho các lời gọi qua interface; việc chọn policy được thực hiện bên trong handler. Cơ chế AOP khác có thể lọc annotation ngay tại pointcut trước khi advice chạy.

Lưu toàn bộ thành tệp **`AnnotationAdviceDemo.java`** bằng UTF-8:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class AnnotationAdviceDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface AuditTag {
        String value();
        boolean includeResult() default false;
    }

    interface Operations {
        @AuditTag("api-publish")
        String publish(String id);

        @AuditTag("api-execute")
        String execute(String id);

        String status();

        String ping();
    }

    @AuditTag("class-default")
    static final class OperationsImpl implements Operations {
        @Override
        @AuditTag(value = "impl-publish", includeResult = true)
        public String publish(String id) {
            System.out.println("target publish " + id);
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("bad id");
            }
            return "PUB:" + id;
        }

        @Override
        public String execute(String id) {
            System.out.println("target execute " + id);
            return "EXEC:" + id;
        }

        @Override
        public String status() {
            System.out.println("target status");
            return "READY";
        }

        @Override
        public String ping() {
            System.out.println("target ping");
            return "PONG";
        }
    }

    static AuditTag resolveAnnotation(
            Method interfaceMethod, Object target) throws Exception {
        Method implementationMethod = target.getClass().getMethod(
                interfaceMethod.getName(),
                interfaceMethod.getParameterTypes()
        );

        AuditTag tag =
                implementationMethod.getDeclaredAnnotation(AuditTag.class);

        if (tag == null) {
            tag = interfaceMethod.getDeclaredAnnotation(AuditTag.class);
        }

        if (tag == null) {
            tag = target.getClass().getDeclaredAnnotation(AuditTag.class);
        }

        return tag;
    }

    static Object invoke(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Operations withAdvice(
            Operations target, Map<String, Boolean> enabledByMethod) {
        return (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] { Operations.class },
                (proxy, method, args) -> {
                    AuditTag tag = resolveAnnotation(method, target);

                    boolean enabled = enabledByMethod.getOrDefault(
                            method.getName(), true
                    );

                    if (!enabled || tag == null) {
                        return invoke(method, target, args);
                    }

                    System.out.println("[audit:before] " + tag.value());

                    try {
                        Object result = invoke(method, target, args);

                        String line = "[audit:after] " + tag.value();
                        if (tag.includeResult()) {
                            line += " result=" + result;
                        }

                        System.out.println(line);
                        return result;
                    } catch (Throwable error) {
                        System.out.println(
                                "[audit:error] " + tag.value() + " "
                                + error.getClass().getSimpleName()
                                + ":" + error.getMessage()
                        );
                        throw error;
                    }
                }
        );
    }

    public static void main(String[] args) {
        Operations target = new OperationsImpl();

        // Cấu hình tắt audit cho status, vẫn giữ nghiệp vụ hoạt động.
        Map<String, Boolean> config = Map.of("status", false);
        Operations service = withAdvice(target, config);

        System.out.println("caller " + service.publish("P-1"));
        System.out.println("caller " + service.execute("E-2"));
        System.out.println("caller " + service.status());
        System.out.println("caller " + service.ping());

        try {
            service.publish("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("caller error " + error.getMessage());
        }
    }
}
```

Biên dịch và chạy với **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 AnnotationAdviceDemo.java
java AnnotationAdviceDemo
```

**Standard output chính xác:**

```text
[audit:before] impl-publish
target publish P-1
[audit:after] impl-publish result=PUB:P-1
caller PUB:P-1
[audit:before] api-execute
target execute E-2
[audit:after] api-execute
caller EXEC:E-2
target status
caller READY
[audit:before] class-default
target ping
[audit:after] class-default
caller PONG
[audit:before] impl-publish
target publish bad
[audit:error] impl-publish IllegalArgumentException:bad id
caller error bad id
```

### 10.1. Tại sao `publish` chọn metadata ở implementation?

`publish(String)` được đánh dấu ở cả interface với `"api-publish"` và implementation với `"impl-publish"`. Resolver đọc annotation **trực tiếp trên implementation method trước** nên chọn `"impl-publish"`.

Giá trị `includeResult=true` của implementation khiến advice in `result=PUB:P-1` ở nhánh thành công. Khi `publish("bad")` ném ngoại lệ, advice in `[audit:error]` và chuyển tiếp lỗi gốc; không có dòng success giả.

### 10.2. Tại sao `execute` chọn annotation interface?

`OperationsImpl.execute` không có annotation method trực tiếp. Resolver chuyển sang `Operations.execute`, tìm `@AuditTag("api-execute")`. Vì `includeResult` không được khai báo ở annotation này, nó nhận **giá trị mặc định `false`** và advice không in kết quả.

Điểm này minh họa một vấn đề quan trọng: annotation trên **interface method** không tự được “kế thừa” vào implementation method; chính resolver trong ví dụ **chủ động tìm cả hai vị trí**.

### 10.3. Tại sao `status` không audit dù class có annotation?

`OperationsImpl` có annotation class-level `@AuditTag("class-default")`. Nhưng cấu hình `Map.of("status", false)` làm `enabled=false` nên handler **gọi target mà không xuất audit**.

Một annotation mô tả default không được phép “ghi đè” cấu hình tắt trong chính sách ví dụ này. `status()` vẫn chạy và trả `READY`.

### 10.4. Tại sao `ping` dùng class-level default?

`ping` không có annotation trên method implementation hoặc interface, nên resolver chọn annotation trên class. Cấu hình không có khóa `"ping"`, và `getOrDefault("ping", true)` cho phép audit. Advice in event `"class-default"`.

**Chú ý:** Việc class annotation mặc định áp dụng cho method không được đánh dấu là **quy tắc của resolver ví dụ**, không phải Java tự tạo annotation trên mọi method.

### 10.5. Vì sao phải gỡ `InvocationTargetException`?

`Method.invoke` có thể bọc ngoại lệ do target ném bằng `InvocationTargetException`. Hàm `invoke` của ví dụ lấy `getCause()` để advice và caller nhận đúng `IllegalArgumentException("bad id")` thay vì xử lý nhầm lỗi wrapper như lỗi nghiệp vụ mới.

### 10.6. Giới hạn cố ý của demo

- Cấu hình chỉ theo **tên phương thức**: nếu có overload cùng tên, chúng sẽ chia sẻ một cờ enable. Ứng dụng lớn cần key ổn định bao gồm type/signature hoặc policy identifier.
- Resolver dùng `getMethod(...)` cho **public method**; không giải quyết đầy đủ interface hierarchy, method bridge, visibility khác hoặc generic specialization.
- Cấu hình là `Map` bất biến dùng để minh họa, không có cơ chế reload động.
- Không có cache annotation; resolver dùng Reflection ở mỗi invocation.
- Không có annotation lặp, meta-annotation hoặc tham số được đánh dấu nhạy cảm.
- Dòng `includeResult=true` chỉ nên được dùng cho dữ liệu được phép ghi; ví dụ cố ý dùng chuỗi `PUB:P-1` không chứa bí mật.
- Handler chỉ nhận lời gọi **qua proxy**, không can thiệp lời gọi trực tiếp target hoặc self-invocation bên trong target.
- Sử dụng tên “audit” trong demo không biến stdout thành một hệ thống audit bất biến, bền vững hoặc chịu lỗi.

## 11. Giá trị mặc định và cấu hình bên ngoài là hai cấp default khác nhau

Có ít nhất ba kiểu default:

1. **Default của annotation element**, ví dụ `includeResult() default false`. Java dùng giá trị này khi annotation đã tồn tại nhưng phần tử đó bị lược bỏ.
2. **Default khi không có annotation**, do resolver của ứng dụng xác định.
3. **Default khi không có khóa cấu hình**, ví dụ `enabledByMethod.getOrDefault(name, true)`.

Chúng rất khác nhau. Nếu method **không có annotation**, không thể gọi `tag.includeResult()` để nhận `false`; trước hết resolver phải quyết định có tạo một policy mặc định hay không. Nếu cấu hình có khóa `false`, nó khác trường hợp khóa không tồn tại.

### Thiết kế “unset” rõ ràng

Một mô hình chính sách có thể phân biệt:

```text
ENABLED
DISABLED
UNSPECIFIED
```

`UNSPECIFIED` nghĩa là “chuyển sang cấp mặc định kế tiếp”, không đồng nghĩa `false`. Với các cờ có ảnh hưởng bảo mật, cách hợp nhất nên có **fail-closed semantics** nơi cần thiết, thay vì vô tình coi dữ liệu đọc lỗi như cờ cho phép.

Nếu cấu hình yêu cầu một số nguyên `maxRecordBytes`, phải kiểm tra `> 0`, giới hạn hợp lý và cách xử lý overflow. Annotation cho phép một số giá trị tại compile time nhưng không tự chứng minh giá trị đó hợp lệ với chính sách nghiệp vụ.

## 12. Annotation lặp lại và annotation gián tiếp

Một số bài toán cho phép nhiều policy trên cùng method. Java hỗ trợ **repeatable annotation** thông qua `@Repeatable(Container.class)`. Khi đó API `getAnnotationsByType(...)` thường là đường phù hợp để nhận các instance cùng type, thay vì tự giả định một `getAnnotation(...)` luôn thấy từng phần tử bên trong container.

Ví dụ ở mức ý tưởng:

```java
// Ký pháp minh họa ý định, giả định định nghĩa @Tag
// đã hỗ trợ @Repeatable và target phù hợp.
@Tag("metrics")
@Tag("audit")
void process();
```

Nếu advice cần cả hai tag, resolver phải xử lý chúng theo rule rõ: **AND hay OR**, thứ tự ưu tiên, có chấp nhận trùng tên không, xung đột xử lý thế nào. Không ngầm chọn phần tử đầu tiên chỉ vì thứ tự API trả về thuận tiện.

### Meta-annotation

Một annotation khác có thể được đánh dấu bởi một annotation mang ý nghĩa chính sách. Nhưng Java Reflection **không mặc nhiên diễn giải đệ quy mọi meta-annotation thành policy của method**. Nếu công cụ AOP cung cấp matching meta-annotation, phải biết chính xác ngữ nghĩa của nó; nếu tự xây resolver, cần tự duyệt annotation type và xác định giới hạn độ sâu, chu kỳ và ngoại lệ.

Đừng tự nhận mọi annotation “tương đương nhau” chỉ vì tên giống nhau hoặc có cùng một meta-annotation trong hierarchy.

## 13. Annotation ở constructor, field và parameter không giống method annotation

AOP có thể hỗ trợ nhiều loại join point, tùy engine:

- Method call/execution.
- Constructor call/execution hoặc initialization.
- Field read/write.
- Những điểm khác do công cụ hỗ trợ.

Nếu một annotation gắn với **field**, việc tìm `method.getAnnotation(...)` không truy ra field annotation. Nếu gắn với **constructor**, phải dùng reflection `Constructor`. Nếu gắn với **parameter**, phải đọc annotation của tham số theo đúng vị trí.

```java
// Minh họa annotation trên parameter:
void publish(@Sensitive String token, String publicId);
```

Advice muốn che token cần biết **parameter index**, kiểu và giá trị thực tế; chỉ biết tên method không đủ. Việc `@Sensitive` có mặt cũng không có nghĩa nên đọc hoặc ghi token vào audit; ngược lại, nó có thể là lý do phải **bỏ qua/ẩn dữ liệu**.

### Parameter name khác parameter annotation

Java Reflection có thể đọc tên tham số với điều kiện class file chứa metadata phù hợp, chẳng hạn khi biên dịch với `-parameters`. Tên tham số không có mặt hoặc bị biến đổi không làm parameter annotation tự mất đi; chúng là các loại metadata khác nhau. Một policy nghiêm ngặt nên ưu tiên index/type/annotation tường minh hơn suy luận ý nghĩa bảo mật chỉ từ tên `password` hay `token`.

## 14. Generic methods, bridge methods và overload: đọc đúng Method nào?

Một trong những khó khăn nâng cao của AOP annotation là **metadata method tại runtime không luôn trùng điều người đọc thấy trong source**.

### 14.1. Overload

```java
void save(String id);
void save(int id);
```

Hai method có cùng tên nhưng chữ ký khác nhau. Nếu dùng cấu hình chỉ theo key `"save"`, không phân biệt được chúng. Một cấu hình theo method nên cân nhắc **declaring type + name + ordered parameter types**, hoặc một identifier ổn định khai báo riêng.

### 14.2. Generics và type erasure

```java
interface Consumer<T> {
    void accept(T value);
}

class StringConsumer implements Consumer<String> {
    public void accept(String value) { }
}
```

Trong bytecode, compiler có thể sinh bridge method để bảo đảm overriding sau erasure. `getDeclaredMethods()` có thể nhìn thấy những method compiler sinh ra. API `Method.isBridge()` và `Method.isSynthetic()` hỗ trợ phân tích.

Một engine AOP có thể ánh xạ bridge method tới method người dùng hoặc áp dụng advice ở nhiều nơi; không có một kết luận phổ quát. Resolver tự viết cần tránh việc vô tình dùng annotation từ bridge method thay vì method nghiệp vụ tương ứng khi điều đó trái contract.

### 14.3. Covariant return

Override có thể trả subtype của kiểu trả về superclass. Java Reflection có thể cho thấy nhiều descriptor/bridge trong một số trường hợp. Nếu muốn resolve annotation trên implementation, cần hiểu method thực chạy và hierarchy thay vì chỉ chọn method đầu tiên có tên trùng.

### 14.4. ClassLoader và module boundary

Class và annotation cùng tên nhưng được nạp bởi class loader khác nhau không nhất thiết là cùng `Class<?>`. Reflection, proxy và truy cập method không public còn chịu giới hạn module/access. Nếu resolver không thể truy cập phần tử cần thiết, **không nên tự coi exception như “không có annotation”** mà im lặng bỏ qua một policy bắt buộc.

## 15. Cấu hình động, cache và việc làm mới policy

Reflection để đọc annotation thường có thể được tối ưu bằng cache theo `Method`, target class hoặc một khóa nhận diện chính xác. Nhưng dữ liệu trong cache phải phản ánh **semantics thực tế**.

### 15.1. Cái gì có thể cache?

- **Annotation metadata tĩnh:** thường phù hợp để cache theo cặp `(targetClass, methodSignature)` trong vòng đời class tương ứng.
- **Policy đã merge với cấu hình bất biến:** có thể cache cùng phiên bản cấu hình.
- **Giá trị của request hiện tại:** **không nên** cache chỉ theo method nếu giá trị thay đổi mỗi lượt gọi.
- **Trace ID/identity theo thread:** không nên cache vào một object interceptor dùng chung cho nhiều request.

### 15.2. Invalidating cache khi cấu hình thay đổi

Nếu cấu hình cho phép reload, `ResolvedPolicy` cũ có thể không còn chính xác. Một thiết kế có thể:

1. Nạp và validate **snapshot bất biến** của cấu hình.
2. Gắn số phiên bản/generation cho snapshot.
3. Tính policy theo annotation và **snapshot hiện tại**.
4. Cache kết quả theo **method + target type + config version**, hoặc bỏ cache merge và đọc cấu hình đã được chuẩn bị.
5. Khi thay config, thay snapshot một cách nguyên tử/nhất quán, không sửa nhiều giá trị rời rạc giữa những invocation.

Một lời gọi có thể bắt đầu khi phiên bản cấu hình A còn hiệu lực và kết thúc khi B đã được công bố. Hệ thống phải định nghĩa **snapshot tại đầu invocation** hay tra lại từng bước. Nếu audit cần tính nhất quán, dùng một policy snapshot cho toàn bộ lượt gọi thường dễ hiểu hơn.

### 15.3. Thread safety của cache

Một aspect/interceptor có thể được dùng đồng thời bởi nhiều thread. Map mutable không đồng bộ chứa kết quả resolve có thể gây race nếu được cập nhật không an toàn. Sử dụng cache thread-safe hoặc khởi tạo snapshot bất biến; tránh giữ `currentMethod`, `currentAnnotation` hoặc `currentRequestId` trong trường instance dùng chung để “giảm chi phí tra cứu”.

### 15.4. Chi phí lớn không phải chỉ từ Reflection

Ngay cả khi metadata được cache, log, serialize result, đọc cấu hình từ mạng hoặc phân tích object graph trong mỗi lời gọi vẫn có thể rất tốn kém. Chỉ đưa thông tin cần thiết vào audit; đo overhead thật trong đường chạy tần suất cao, không suy đoán mọi reflection đều là nút thắt hiệu năng.

## 16. Bảo mật: annotation không biến policy thành quyền truy cập

Annotation có thể gắn nhãn một phương thức là “được audit” hoặc “chứa dữ liệu nhạy cảm”. Nhưng nếu cơ chế AOP chỉ áp dụng advice ở đường gọi qua proxy, **đường gọi trực tiếp target hoặc self-invocation có thể bỏ qua** logic ấy.

Điều này đặc biệt nguy hiểm nếu một annotation được dùng như **bằng chứng ủy quyền duy nhất**. Nguyên tắc:

- **Chính sách bắt buộc** phải được thực thi tại ranh giới không thể bỏ qua đối với các đường thao tác liên quan.
- Annotation là metadata để **mô tả**, không tự kiểm tra danh tính hoặc quyền.
- External config là dữ liệu đầu vào cần được **xác thực về tính toàn vẹn và nguồn đáng tin cậy**.
- Không tin giá trị config/annotation chỉ vì nó có dạng đúng. Một cờ “allowAll” sai nơi là lỗi bảo mật dù kiểu Boolean hợp lệ.
- Không dùng metadata nằm trong nguồn bên ngoài chưa xác thực như thể nó là annotation đáng tin của code đã được kiểm soát.

### Lưu kết quả có thể tiết lộ dữ liệu

Annotation như `includeResult=true` chỉ thể hiện ý định. Trước khi in hoặc lưu kết quả, advice phải xét:

- Return value có chứa mật khẩu, token, PII hoặc dữ liệu kinh doanh nhạy cảm không?
- Có cần ẩn, rút gọn hoặc chỉ lấy mã trạng thái không?
- Kết quả là object mutable có `toString()` chứa dữ liệu không mong muốn không?
- Việc gọi `toString()` có side effect, chậm hoặc ném lỗi không?
- Audit sink có yêu cầu độ bền, idempotency hay quy tắc redaction đặc biệt không?

**Một annotation cho phép log không chứng minh dữ liệu có thể công bố**. Nên ưu tiên dữ liệu được phép ghi rõ ràng và hạn chế log toàn bộ object.

## 17. Nếu annotation hoặc cấu hình đọc không được, advice nên làm gì?

Câu trả lời phụ thuộc vào mức độ quan trọng của concern.

**Với logging/metric phụ trợ**, có thể áp dụng fallback được xác định từ trước, bỏ qua một trường không bắt buộc hoặc ghi lỗi quan sát một cách kiểm soát để nghiệp vụ tiếp tục. Cũng cần tránh vòng lặp khi chính hệ thống logging bị lỗi.

**Với policy bắt buộc**, việc “không đọc được metadata” có thể là lý do **từ chối thao tác** theo nguyên tắc fail-closed, hoặc từ chối khởi động ứng dụng. Không nên lén dùng default cho phép tất cả nếu config bị thiếu, sai định dạng hoặc lỗi truy cập.

Các trạng thái phải phân biệt:

| Tình huống | Ý nghĩa | Hướng xử lý |
| --- | --- | --- |
| Annotation không có | Có thể là bình thường | Theo policy default |
| Annotation có nhưng phần tử dùng default | Metadata hợp lệ | Dùng default đã khai báo |
| Cấu hình không có khóa | Không được chỉ định tại cấp này | Dùng precedence/fallback định nghĩa |
| Cấu hình có `false` | Một giá trị tường minh | Không được coi là missing |
| Cấu hình sai kiểu/sai miền | Lỗi cấu hình | Validate, báo lỗi rõ |
| Reflection không có quyền truy cập | Hạn chế runtime/module | Không đồng nhất với thiếu annotation |
| Nguồn cấu hình tạm thời không khả dụng | Lỗi hạ tầng | Áp dụng chính sách retry/fallback/fail-closed được công bố |
| Metadata mâu thuẫn | Không rõ quyền ưu tiên | Giải quyết bằng rule hoặc báo lỗi |

### Lỗi từ advice có thể che lỗi target

Nếu advice đang xử lý exception target mà bản thân việc đọc cấu hình/serialize error lại ném exception khác, caller có thể thấy lỗi advice thay cho lỗi nghiệp vụ. Hãy thiết kế ranh giới xử lý lỗi phụ đúng mức độ quan trọng. Với audit bắt buộc, lỗi audit có thể cần làm thao tác thất bại; với metric phụ trợ, có thể cần cô lập lỗi. **Quyết định phải tường minh**, không dựa vào một `catch (Exception)` nuốt mọi lỗi.

## 18. Annotation và dữ liệu cấu hình có thể được đọc ở thời điểm nào?

Trong một hệ thống AOP, việc đọc metadata/cấu hình có thể xảy ra:

- **Khi khởi tạo aspect:** đọc một snapshot chung.
- **Khi resolver được yêu cầu lần đầu:** resolve method, cache kết quả.
- **Mỗi lần invocation:** đọc metadata và ngữ cảnh mới.
- **Khi cấu hình reload:** tính lại policy.
- **Khi class được load/weave:** phân tích annotation trong bytecode.
- **Khi build:** chọn những điểm có annotation rồi đưa advice vào artifact.

Mỗi thời điểm có trade-off. **Chọn tĩnh tại build/load** có thể giảm chi phí lookup runtime, nhưng không tự hỗ trợ đổi annotation không rebuild. **Chọn động tại invocation** linh hoạt nhưng cần quản lý hiệu năng và tính nhất quán của cấu hình.

Một annotation `RUNTIME` cho phép Reflection đọc khi chương trình chạy; nó **không bắt buộc engine phải đọc mỗi invocation**. Tương tự, external config có thể là một snapshot khởi động thay vì giá trị thay đổi tức thì.

## 19. Context propagation và async: annotation không mất, ngữ cảnh có thể mất

Annotation trên method là metadata của mã; đổi thread không tự xóa annotation. Nhưng advice còn có thể kết hợp nó với dữ liệu **theo request** như trace ID hoặc user context, vốn có thể gắn vào thread.

Nếu method `publish` được chạy qua executor:

- Annotation của `publish` có thể được resolve như trước.
- `ThreadLocal` của caller **không tự truyền** sang worker.
- Config snapshot có thể là chung hoặc thay đổi giữa lúc submit và execute.
- Nếu advice đọc config/trace tại **execution** thay vì lúc **submit**, nó có thể thấy dữ liệu khác.
- Một callback xử lý future có thể chạy trên thread khác và cần propagation riêng.

Điều này cho thấy **metadata tĩnh**, **runtime config snapshot** và **context mỗi invocation** cần được quản lý như ba lớp khác nhau. Không nên cache cả chúng thành một “policy” theo method rồi dùng lại vô hạn.

## 20. Multi-aspect composition và precedence

Giả sử có `AuditAspect`, `TimingAspect` và `AccessAspect`. Mỗi aspect có thể đọc annotation khác nhau hoặc chia sẻ một metadata resolver. Khi cùng khớp một join point, thứ tự advice có thể quyết định:

- Audit nhìn thấy input ban đầu hay input đã được around advice chuẩn hóa?
- Timing có bao gồm thời gian đọc cấu hình và ghi audit không?
- AccessAspect chặn trước khi AuditAspect bắt đầu hay AuditAspect ghi cả yêu cầu bị từ chối?
- Một around bên ngoài recover lỗi thì AuditAspect ở bên trong quan sát outcome gì?

**Pointcut** quyết định những điểm được chọn; **precedence** quyết định thứ tự các advice. Annotation chứa `priority=...` có thể là một nguồn cấu hình cho ordering nếu công cụ/ứng dụng định nghĩa như vậy, nhưng nó **không tự tạo một thứ tự chạy** trong mọi engine AOP.

### Tránh resolver phụ thuộc vòng tròn

Nếu đọc config của AuditAspect lại kích hoạt một method đang bị AuditAspect chọn, có thể xuất hiện vòng gọi đệ quy. Hãy tách hạ tầng metadata/config khỏi tập join point của chính aspect khi cần, hoặc áp dụng guard có semantics đúng. Đừng dựa vào một flag toàn cục mutable thiếu thread safety để “tắt đệ quy”.

## 21. Thiết kế một metadata resolver có thể kiểm thử

Một resolver tốt thường có hợp đồng rõ:

```text
Input:
    - join point kind / method metadata
    - target type
    - current config snapshot

Output:
    - resolved policy (hoặc no-policy / validation error)
```

Điều cần ghi trong tài liệu thiết kế:

1. Loại join point nào được hỗ trợ: call, execution, proxy invocation?
2. Có lấy annotation từ interface, implementation, superclass, class default không?
3. Thứ tự ưu tiên các nguồn là gì?
4. Có xử lý override, inherited methods, default methods và bridge methods không?
5. Khi annotation missing, dùng fallback hay bỏ qua?
6. Cấu hình có thể disable annotation không? Có thể tăng quyền/chính sách được không?
7. Config key có phân biệt overload không?
8. Khi reload cấu hình, cache được invalidated thế nào?
9. Dữ liệu request/thread có bao giờ bị cache nhầm cùng metadata tĩnh không?
10. Nếu lỗi đọc metadata hoặc config, fail-open hay fail-closed theo concern nào?
11. Cấu hình và annotation có chứa thông tin nhạy cảm không?
12. Làm sao quan sát được policy cuối mà **không log dữ liệu bí mật**?

Có thể biểu diễn kết quả resolve bằng một **record bất biến** trong Java để tránh cập nhật nửa chừng:

```java
// Java 17+, minh họa dạng dữ liệu, chưa là tệp chạy độc lập.
record ResolvedAuditPolicy(
        boolean enabled,
        String eventName,
        boolean includeResult
) { }
```

Thay vì để advice tự đọc ba nguồn ở nhiều thời điểm khác nhau, resolver tạo một policy nhất quán cho invocation. Điều này cũng làm unit test dễ hơn: input xác định thì output xác định.

## 22. Ma trận kiểm thử cần có

| Trường hợp | Kết quả/thuộc tính cần xác minh |
| --- | --- |
| Annotation nằm trên implementation method | Resolver chọn đúng giá trị và precedence |
| Annotation chỉ nằm trên interface method | Resolver không bỏ lọt metadata hợp lệ |
| Annotation chỉ nằm trên class | Default được áp dụng hoặc bỏ qua đúng contract |
| Method có annotation khác class | Có chọn đúng cấp ưu tiên không? |
| Method không có annotation ở đâu | Không ném NPE; fallback đúng |
| Annotation thiếu một phần tử có default | Đọc đúng giá trị mặc định |
| Cấu hình có `false` | Thực sự disable, không coi như missing |
| Cấu hình không có khóa | Dùng default được tài liệu hóa |
| Config sai kiểu/ngoài miền | Báo lỗi hoặc áp fallback đúng chính sách |
| Hai overload cùng tên | Config key có đủ thông tin phân biệt không? |
| Inherited/overridden method | Đọc đúng method/interface/superclass |
| Repeatable annotation | Đọc đủ các giá trị bằng API thích hợp |
| Generic bridge method | Không đọc nhầm hoặc áp advice trùng ngoài ý muốn |
| Target throw | Advice ghi đúng nhánh error và truyền lỗi theo contract |
| Call trực tiếp target | Kiểm chứng giới hạn proxy/weaving |
| Cấu hình reload | Có cập nhật cache và policy nhất quán không? |
| Nhiều thread | Không lẫn ngữ cảnh hoặc policy snapshot |
| Async callback | Metadata đúng; request context không bị giả định tự truyền |
| Annotation đánh dấu dữ liệu nhạy cảm | Không ghi lộ nội dung bị cấm |
| Lỗi Audit sink | Có tuân thủ contract thành công/thất bại không? |

### Ba mức kiểm thử

- **Unit test resolver:** Với metadata và cấu hình giả lập, xác minh trường hợp ưu tiên, vắng mặt, lỗi và default.
- **Integration test AOP:** Gọi qua cơ chế interception/weaving thực tế, xác nhận advice có chạy đúng đường và đúng dữ liệu.
- **Contract test nghiệp vụ:** Kết quả/exception của target phải được bảo toàn hoặc biến đổi đúng chính sách, không có side effect phát sinh do đọc metadata.

Chỉ unit test `getAnnotation(...) != null` là **chưa đủ** để chứng minh pointcut chọn đúng hoặc advice có quyền can thiệp trên mọi đường thực thi.

## 23. Các lỗi thường gặp

1. **Tưởng có annotation là advice tự chạy.** Vẫn cần pointcut và cơ chế AOP áp dụng đúng join point.
2. **Đọc annotation ở implementation trong khi annotation chỉ nằm trên interface.** Có thể nhận `null` dù metadata tồn tại.
3. **Tin `@Inherited` tự áp dụng cho method/interface.** Quy tắc kế thừa Java không hoạt động như vậy.
4. **Không dùng `@Retention(RUNTIME)` nhưng lại đòi Reflection runtime đọc.** Kết quả có thể không có annotation.
5. **Đọc method annotation thay vì parameter annotation.** Metadata có thể nằm ở phần tử khác.
6. **Coi annotation element default là default khi không có annotation.** Đây là hai trường hợp khác.
7. **Không phân biệt config `false` và key không tồn tại.** Dễ bật chính sách ngoài ý muốn.
8. **Ưu tiên annotation và config không nhất quán.** Hai implementation có thể xử lý xung đột khác nhau.
9. **Dùng config key chỉ là tên method.** Overload có thể bị gộp ngoài ý định.
10. **Bỏ qua bridge/generic/inherited method.** Reflection có thể phản ánh metadata khác code nguồn.
11. **Cache policy gộp cả request context.** Dữ liệu request cũ có thể rò sang request mới.
12. **Đọc file/mạng trong mọi advice trên đường nóng.** Gây overhead, timeout hoặc làm lỗi nghiệp vụ.
13. **Coi không đọc được config như cho phép tất cả.** Rất rủi ro với policy bắt buộc.
14. **Log toàn bộ result vì `includeResult=true`.** Có thể lộ thông tin nhạy cảm.
15. **Cho rằng proxy bao phủ direct call/self-invocation.** Chúng có thể bypass advice.
16. **Tưởng config thay đổi sẽ tức thì cập nhật cache.** Phải thiết kế invalidation/snapshot.
17. **Nhầm metadata từ caller với target runtime.** Interface/call/implementation có thể dẫn đến các Method khác nhau.
18. **Gán annotation là tính năng riêng của một công cụ.** Đây là Java metadata; cách AOP dùng nó phụ thuộc engine.

## 24. Bài tập thực hành

1. Chạy `AnnotationAdviceDemo.java` và giải thích lý do `publish` chọn `"impl-publish"`, `execute` chọn `"api-execute"`, còn `ping` chọn `"class-default"`.
2. Xóa annotation khỏi `OperationsImpl.publish`. Dự đoán tên audit nào được chọn và điều kiện `includeResult` thay đổi ra sao.
3. Đổi cấu hình thành `Map.of("status", true, "ping", false)`. Dự đoán chính xác những dòng audit xuất hiện/biến mất.
4. Bỏ annotation khỏi cả implementation và interface của `execute`. Resolver có tìm class default không? Nếu yêu cầu chỉ audit method được đánh dấu, cần sửa rule thế nào?
5. Thêm hai overload `publish(String)` và `publish(int)`, rồi giải thích vì sao cấu hình chỉ key theo tên method không phân biệt được chúng.
6. Chuyển annotation class-level từ implementation sang interface type. Resolver dùng `target.getClass().getDeclaredAnnotation(...)` còn thấy annotation không? Cần thêm nhánh nào nếu muốn hỗ trợ interface type annotation?
7. Thêm annotation trên superclass method và override method ở subclass không khai báo annotation. Dùng Reflection quan sát `getDeclaredAnnotation` của hai method.
8. Dùng `@Inherited` trên một annotation type có `@Target(TYPE)`; kiểm tra behavior của `Class.getAnnotation` trên subclass so với một implementation của interface.
9. Thêm phần tử `String[] tags() default {};` vào annotation và đọc nó trong advice. Xác định cách xử lý khi mảng trống.
10. Thiết kế một `ResolvedAuditPolicy` bất biến và unit test precedence giữa config disable, implementation, interface, class default.
11. Xây cache theo `Method` và target type, rồi thiết kế cách làm mới khi config version tăng. Vì sao không cache một trace ID theo method?
12. Thêm annotation parameter đánh dấu dữ liệu nhạy cảm; kiểm tra vị trí tham số bằng `getParameterAnnotations()` và tạo log đã che dữ liệu.
13. Mô phỏng cấu hình sai kiểu, thiếu khóa và `false` tường minh; phân biệt ba kết quả policy.
14. Cho target ném ngoại lệ, xác nhận advice không ghi success giả và không mất Throwable gốc.
15. Gọi trực tiếp `target.publish(...)` ngoài proxy và mô tả vì sao handler không có log.
16. Lập bảng test ít nhất năm ca *metadata phải được dùng* và năm ca *không nên dùng*, gồm interface, override, inherited, generic/bridge và disable config.

**Tự kiểm tra:** Vì sao annotation có `RUNTIME` vẫn chưa đủ để advice chạy? Annotation trên interface method có mặc nhiên xuất hiện ở implementation method không? `@Inherited` có giúp kế thừa annotation method không? `getDeclaredAnnotation` khác `getAnnotation` ở trường hợp nào? Vì sao cấu hình `false` không phải “không có cấu hình”? Khi nào cần cache policy, và cache key nào an toàn khi cùng method xử lý nhiều request?

## Kết luận

**Advice có thể dùng annotation và cấu hình để ra quyết định chính sách nhất quán, nhưng phải đọc đúng nguồn metadata và đúng thời điểm.** Annotation trên interface, implementation, class, parameter hay type-use là những dữ liệu khác nhau; Java retention, inheritance, Reflection và generic/bridge method quyết định cách truy xuất. Cấu hình bổ sung quyền điều chỉnh nhưng phải có **thứ tự ưu tiên, default, validation và lifecycle** rõ ràng. Một thiết kế vững thường tách **metadata resolver**, **configuration provider**, **policy merging** và **advice**, kiểm thử cả metadata lẫn đường interception thật, quản lý cache/reload, đồng thời bảo vệ dữ liệu nhạy cảm và các chính sách bắt buộc ở ranh giới đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-007">Không giữ tham chiếu đối tượng lâu hơn nhu cầu xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Không giữ tham chiếu đối tượng lâu hơn nhu cầu xử lý

## Mục tiêu bài học

Trong **Aspect-Oriented Programming (AOP)**, advice có thể nhận hoặc truy cập nhiều dữ liệu thuộc một **join point**: đối tượng đang được gọi, target, đối số, kết quả, exception, metadata, thông tin thực thi và đối tượng biểu diễn continuation. Những dữ liệu này rất hữu ích để đo lường, kiểm tra kỹ thuật, ghi trace hoặc kết hợp hành vi. Tuy nhiên, **đọc dữ liệu trong advice không đồng nghĩa được phép lưu giữ toàn bộ đối tượng vô thời hạn**.

Một tham chiếu được giữ lâu hơn nhu cầu có thể khiến những object graph lớn không thể được thu gom rác, làm bộ nhớ tăng dần, giữ thông tin nhạy cảm lâu hơn chính sách cho phép, dẫn đến rò context giữa request hoặc khiến callback dùng dữ liệu đã hết hiệu lực.

**Quy tắc trọng tâm: chỉ giữ tham chiếu trong phạm vi và thời gian thực sự cần để hoàn thành hợp đồng của advice; nếu phải giữ lâu hơn một invocation, hãy chỉ rõ ai sở hữu, điều kiện giải phóng, giới hạn dung lượng và điều gì xảy ra khi công việc lỗi, bị hủy hoặc không bao giờ kết thúc.**

Sau bài học, bạn sẽ:

1. Hiểu mối quan hệ giữa **reachability**, vòng đời object và **memory retention** trong một chương trình có AOP.
2. Xác định những tham chiếu dễ bị giữ ngoài ý muốn: `this`, `target`, `args`, `result`, `Throwable`, join-point context, annotation, `proceed`/closure và dữ liệu gắn với thread.
3. Phân biệt **biến cục bộ của một lần gọi** với **trạng thái chia sẻ của một aspect**, collection toàn cục, cache, `ThreadLocal` và callback bất đồng bộ.
4. Thiết kế **snapshot dữ liệu tối thiểu** thay cho lưu cả object graph, giữ context đúng vòng đời đồng bộ/bất đồng bộ và cleanup bằng `finally`.
5. Hiểu khi nào `WeakReference`, weak-key map, TTL, bounded queue hoặc unregister listener có ích — và vì sao chúng không tự sửa một thiết kế giữ dữ liệu sai.
6. Chạy ví dụ Java 17 với dynamic proxy minh họa cách giữ dữ liệu tạm thời, xóa `ThreadLocal`, quan sát async completion mà không giữ lại payload và kiểm tra đường bypass proxy.
7. Lập ma trận kiểm thử để phát hiện giữ tham chiếu quá lâu, rò context, tiêu thụ bộ nhớ không giới hạn và lỗi do concurrency.

Phạm vi là **AOP nói chung**, không mặc định mọi công cụ hỗ trợ cùng loại join point hoặc cùng dạng join-point context. Ví dụ Java chỉ minh họa một kỹ thuật interception cụ thể.

## 1. Ngữ cảnh của advice chứa những gì?

Một join point có thể là một **lần method execution**, **method call**, constructor execution, field access hoặc sự kiện khác, tùy **join-point model** của công cụ. Pointcut chọn các điểm phù hợp; advice được chạy ở những điểm đó; weaving hoặc proxy quyết định cách hành vi được chèn vào chương trình.

Một advice có thể cần một phần của các dữ liệu sau:

| Dữ liệu | Ví dụ | Rủi ro khi giữ quá lâu |
| --- | --- | --- |
| **Target** | Đối tượng dịch vụ được gọi. | Target có thể tham chiếu tới cache, dependency, tài nguyên, cấu trúc domain lớn. |
| **Đối tượng `this` hoặc caller context** | Receiver hay đối tượng liên quan đến điểm gọi. | Có thể giữ cả graph do receiver/caller nắm giữ. |
| **Arguments** | Mảng đối số chứa request/payload. | Một argument có thể giữ buffer lớn, stream, thông tin người dùng hoặc document graph. |
| **Result** | Entity, DTO, stream, future hoặc iterator. | Có thể giữ bản ghi lớn, buffer, tài nguyên chưa đóng hoặc một tác vụ còn pending. |
| **Throwable** | Exception từ target/advice. | Exception có stack trace, `cause`, `suppressed`, custom fields và dữ liệu nhạy cảm. |
| **Method/signature/annotation** | Metadata dùng chọn pointcut. | Có thể liên quan class/classloader; cache không kiểm soát dễ giữ metadata lớp cũ sau reload. |
| **Join-point / proceeding context** | Object cho phép lấy args hoặc gọi `proceed`. | Thường gián tiếp nắm target, args hoặc continuation có phạm vi hẹp. |
| **Trace/request context** | Correlation ID, principal, tenant. | Giữ nguyên security/request object có thể kéo dài thời gian sống của dữ liệu riêng tư. |

**Chỉ vì một API trả về một object không có nghĩa cần lưu nó.** Trong nhiều advice chỉ ghi số liệu, dữ liệu thật sự cần giữ có thể là một `operationName`, một `requestId`, một loại lỗi và một giá trị `durationNanos` — không phải toàn bộ `args`, `target` và `Throwable`.

### 1.1. `this` và `target` không luôn đồng nghĩa

Một số mô hình AOP phân biệt đối tượng hiện tại tại nơi gọi (`this`) với đối tượng nhận thao tác (`target`), nhất là khi có proxy hoặc join point ở phía caller. Không nên suy ra chúng là cùng một instance trong mọi công cụ. Cả hai đều có thể tạo **strong reference** đến một object graph lớn.

Nếu chỉ cần nhãn kiểu hoặc danh tính ổn định, hãy chọn dữ liệu đại diện thích hợp thay vì lưu nguyên receiver. Cũng không nên tự động dùng `System.identityHashCode` như một ID toàn cục duy nhất hoặc ổn định xuyên quá trình; nó không mang hợp đồng đó.

### 1.2. Đối số không phải lúc nào cũng là dữ liệu nhẹ

Một `Object[] args` nhìn có vẻ chỉ là một mảng nhỏ. Nhưng các phần tử có thể là request body, file upload, byte array lớn, entity liên kết với session hoặc một đồ thị object. Nếu giữ **tham chiếu tới mảng**, các phần tử của nó có thể tiếp tục được giữ.

Tạo một bản sao mảng bằng `args.clone()` chỉ sao chép **container**, không deep-copy những object bên trong; bản sao vẫn giữ strong reference đến các phần tử. Muốn giảm retention, phải chọn những **giá trị nhỏ, độc lập và thật sự cần thiết**.

### 1.3. Metadata có vòng đời riêng

Trong Java, các instance `Class`, `Method` hoặc annotation được dùng trong matcher có quan hệ tới kiểu và classloader. Với ứng dụng có cơ chế nạp/reload module, một cache toàn cục giữ `Class`/`Method` của lớp cũ có thể góp phần ngăn classloader được thu hồi. Công cụ AOP có thể có cache metadata được quản lý vòng đời riêng, nhưng không vì thế mà một cache custom trong advice có quyền sống vô hạn.

Không phải mọi `Method` giữ lại đều là memory leak: ví dụ một registry dùng suốt vòng đời ứng dụng có thể có lý do giữ metadata. Vấn đề là **vòng đời của cache có phù hợp vòng đời của class/module và có cơ chế giải phóng không**.

## 2. Vì sao strong reference có thể kéo dài sự tồn tại của đối tượng?

Trong Java và nhiều runtime có garbage collector, object chỉ có thể được xem xét thu gom khi **không còn reachable** từ các gốc được GC xét qua những đường tham chiếu liên quan. Một biến `static`, một thread còn sống, field của object lâu sống, queue, callback hoặc collection có thể giữ một **đường tham chiếu mạnh** dẫn tới object.

Sơ đồ minh họa:

```text
GC root
   |
   v
long-lived singleton aspect
   |
   v
List<Object[]> storedArguments
   |
   v
Object[] args
   |
   v
RequestPayload
   |
   +--> byte[large]
   +--> customer / document graph
   +--> nested collections
```

Mặc dù phương thức nghiệp vụ đã kết thúc, object `RequestPayload` vẫn có thể reachable thông qua `storedArguments`. GC **không thể quyết định tự xóa dữ liệu còn được chương trình giữ có chủ đích**, kể cả khi lập trình viên đã quên collection đó tồn tại.

### 2.1. Memory leak không nhất thiết là object “không bao giờ được giải phóng”

Trong môi trường managed runtime, memory leak thường biểu hiện dưới dạng **unintended retention**: object không còn cần về mặt chức năng nhưng vẫn reachable. Nếu cache được giải phóng lúc tắt ứng dụng, ta vẫn có thể coi việc tăng bộ nhớ suốt thời gian chạy dài là một vấn đề nghiêm trọng.

Một số biểu hiện:

- Heap usage sau các đợt GC vẫn tăng theo số request.
- Số object thuộc nhóm request/context/exception tồn tại lâu bất thường.
- Latency GC, pressure allocation hoặc thời gian pause tăng.
- Worker pool tái sử dụng thread nhưng context cũ vẫn hiện trong invocation mới.
- Shutdown/redeploy không thu hồi được classloader hoặc tài nguyên đi kèm.

**Không kết luận memory leak chỉ từ một snapshot heap lớn.** Cần xem retained paths, chu kỳ sống, workload và xu hướng sau các lượt xử lý ổn định. Cache được cố ý giữ có thể dùng bộ nhớ lớn nhưng vẫn bounded và đúng hợp đồng.

### 2.2. Kích thước reference nhỏ không nói được lượng bộ nhớ bị giữ

Một field chỉ chứa một tham chiếu tới một request có thể kéo theo graph lớn: request → session → danh sách document → byte arrays. Nếu lời gọi advice lưu `Throwable`, throwable có thể giữ cause, suppressed exceptions, custom fields hoặc stack trace; việc giữ chỉ một exception lâu dài vẫn có thể làm rò dữ liệu và giữ tài nguyên gián tiếp.

Vì vậy **retained size** đáng quan tâm hơn kích thước nông (*shallow size*) của chính reference.

## 3. Vòng đời đúng của dữ liệu trong advice

Hãy phân loại dữ liệu thành ba nhóm.

### 3.1. Dữ liệu chỉ cần trong invocation đồng bộ

Ví dụ:

- Thời điểm bắt đầu.
- Reference `target` và `args` để gọi tiếp.
- Tạm đọc annotation hoặc chữ ký method.
- Throwable để ghi loại lỗi và ném lại.
- Context tạm dùng khi method chưa return.

Giải pháp ưu tiên: **biến cục bộ trong method advice hoặc handler**, hạn chế đưa vào field dùng chung. Nếu cần đặt trong `ThreadLocal`, bảo đảm cleanup theo phạm vi thread và có quy tắc hỗ trợ nested invocation.

Không cần “chủ động set null mọi local variable” khi chương trình đã thoát scope hợp lý. Hạn chế reachability bằng thiết kế scope thường rõ hơn micro-optimization.

### 3.2. Dữ liệu cần cho công việc bất đồng bộ

Ví dụ:

- Trace ID, operation name, thời điểm bắt đầu để tính latency end-to-end.
- Một token tương quan hoặc deadline.
- Một snapshot metadata đã lọc dùng trong completion callback.

Không nên vì muốn đo lúc hoàn tất mà capture **toàn bộ join-point context**, `args`, request, target hoặc một `ProceedingJoinPoint`-like object trong lambda. Callback có thể sống đến khi future hoàn tất; nếu future không hoàn tất, nó có thể giữ dữ liệu rất lâu.

Thay vào đó, lấy những giá trị **độc lập, nhỏ và cần thiết**, ví dụ:

```text
String requestId
String operation
long startedNanos
```

Không phải mọi `String` đều luôn nhỏ hoặc an toàn: request ID cần chiều dài giới hạn, còn exception message hoặc body có thể chứa dữ liệu lớn/nhạy cảm. “Snapshot tối thiểu” còn bao gồm **giới hạn cardinality và chính sách riêng tư**.

### 3.3. Dữ liệu được chủ động giữ nhiều invocation

Ví dụ:

- Bộ đếm metric dạng số.
- Cấu hình pointcut.
- Cache metadata có giới hạn.
- Listener được đăng ký theo vòng đời module.
- Registry phục vụ debug có TTL và dung lượng tối đa.

Mỗi cấu trúc giữ dài hạn cần câu trả lời cụ thể:

1. Ai sở hữu?
2. Tối đa bao nhiêu phần tử hoặc bytes?
3. Khi nào loại bỏ?
4. Khi module/ứng dụng dừng, ai cleanup?
5. Nếu việc ghi hoặc hủy bị lỗi, trạng thái còn lại là gì?
6. Có chứa reference tới request/user/target không?
7. Concurrency và reentrancy có làm retention tăng bất ngờ không?

Nếu không trả lời được, đừng lưu object vào state sống dài của aspect.

## 4. Các mẫu giữ tham chiếu quá lâu thường gặp

### 4.1. Lưu mọi bộ `args` vào field/static list

Ví dụ **phản ví dụ**, không phải chương trình độc lập:

```java
static final java.util.List<Object[]> HISTORY =
        new java.util.ArrayList<>();

void recordInvocation(Object[] args) {
    HISTORY.add(args); // giữ mạnh mọi phần tử trong mảng
}
```

Với N lần gọi, collection có thể tăng cùng N. Mảng chứa request payload hoặc object có graph lớn sẽ tiếp tục được giữ. Dùng synchronized list chỉ giải quyết một phần vấn đề đồng thời, **không giải quyết vấn đề vòng đời**.

Hướng khắc phục: chỉ lưu **tổng số lần gọi**, các trường tóm tắt an toàn hoặc một mẫu lịch sử có **giới hạn + TTL**. Nếu thực sự cần bản ghi để điều tra, nên dùng hệ thống lưu log/audit có retention và kiểm soát truy cập rõ, không nhét toàn bộ object runtime vào singleton aspect.

### 4.2. Giữ `target` trong một cache theo từng request

```text
Map<requestId, targetObject>
```

Nếu các key không bao giờ bị xóa, target được giữ; target có thể giữ kết nối, buffer hoặc các dependency khác. Nếu reference tới target phải được duy trì suốt vòng đời proxy, đó có thể là **quan hệ sở hữu hợp lý**. Rủi ro xuất hiện khi một map riêng giữ bản sao reference theo mọi request **sau khi request không còn cần**.

### 4.3. Giữ `Throwable` để “debug về sau”

```text
List<Throwable> recentErrors
```

Cấu trúc này giữ exception object, stack trace, cause chain và custom fields. Ngay cả khi chỉ giữ “100 lỗi gần nhất”, mỗi Throwable có thể tham chiếu graph lớn; cần xem **dung lượng thực** và độ nhạy cảm. Một snapshot như `errorType`, `errorCode`, một ID/timestamp thường an toàn hơn.

Một số trường hợp điều tra cần retention tạm thời; hãy giới hạn thời gian, số lượng, redaction và cleanup khi hoàn tất phiên chẩn đoán.

### 4.4. Lưu cả result sau khi caller đã nhận

Advice chỉ cần tính `result.size()` hoặc gắn outcome label thì không nên giữ cả result DTO, stream hoặc collection trong một list dài hạn. Với dữ liệu lazy (iterator/stream/publisher), việc giữ wrapper còn có thể giữ tài nguyên chưa đóng hoặc kéo dài subscription.

### 4.5. Lambda/callback capture dữ liệu lớn

Ví dụ:

```java
Payload payload = ...; // đoạn minh họa
stage.whenComplete((value, error) -> {
    log(payload);       // closure giữ strong reference tới payload
});
```

Trong khi stage pending, callback có thể phải tồn tại và giữ `payload`. Nếu `payload` chứa byte array lớn, thời gian giữ ít nhất có thể kéo dài tới completion, trừ khi implementation có cơ chế giải phóng khác.

Cách thiết kế tốt hơn khi chỉ cần ID:

```java
String requestId = payload.id();
stage.whenComplete((value, error) -> {
    recordResult(requestId, error);
});
```

Đây là **mã minh họa**, không độc lập: các loại/hàm cần được định nghĩa ở nơi khác. Không capture `payload` trong callback đã đổi; bảo đảm `requestId` là dữ liệu ngắn, được phép dùng và không gián tiếp giữ payload. Nếu cần nguyên payload để thực hiện **nghiệp vụ**, không được xóa reference quá sớm làm logic sai; cần chốt vòng đời theo hợp đồng của task.

### 4.6. Registration mà không unregister

Một aspect có thể đăng ký listener vào event bus, scheduler hoặc registry. Nếu nguồn phát sự kiện sống dài, nó giữ callback, callback có thể giữ aspect, target hoặc state request. Sau khi module/dịch vụ hết hiệu lực, chưa unregister listener là nguồn retention phổ biến.

Cần xác định **subscription handle** hoặc cơ chế tháo đăng ký và cleanup khi scope kết thúc, kể cả sau exception/cancellation. “Dùng weak listener” không luôn đủ nếu nơi khác vẫn còn strong reference hoặc listener phải hoạt động tới khi unsubscribe rõ.

## 5. `ThreadLocal`: tiện cho advice đồng bộ nhưng cần cleanup

`ThreadLocal` cho phép một thread lưu trạng thái gắn với thread ấy. Một số advice dùng nó để mang trace context, thời điểm bắt đầu, dữ liệu đệ quy hoặc một giá trị tạm.

Rủi ro: thread trong pool thường sống **lâu hơn nhiều request**. Nếu `ThreadLocal` vẫn giữ `CallContext` sau khi lời gọi xong, context cũ có thể tiếp tục reachable theo thread và gây rò thông tin hoặc memory retention.

### 5.1. Mẫu đơn giản: đặt, dùng, xóa trong `finally`

```java
CallContext ctx = new CallContext(requestId);
CURRENT.set(ctx);
try {
    return proceed();
} finally {
    CURRENT.remove();
}
```

Đây là **đoạn khái niệm**, giả định `CURRENT`, `CallContext` và `proceed` đã có. Với một scope không lồng, `remove()` trong `finally` bảo đảm cleanup cả khi return và throw. Việc `remove()` phải xảy ra **trên đúng thread đã đặt giá trị**; gọi `remove()` từ một worker khác không xóa giá trị thread-local của thread ban đầu.

### 5.2. Nested advice/đệ quy: không được xóa bừa context của caller ngoài

Nếu cùng một `ThreadLocal` được sử dụng ở các scope lồng nhau:

```text
outer advice: set context A
    inner advice: set context B
    inner finally: remove()
outer resumes: context A đã biến mất
```

Vì vậy **mẫu `set` rồi `remove` đơn giản chỉ đủ cho scope không cần restore giá trị trước đó**.

Mẫu phục hồi hợp đồng lồng nhau:

```java
CallContext previous = CURRENT.get();
CURRENT.set(current);
try {
    return proceed();
} finally {
    if (previous == null) {
        CURRENT.remove();
    } else {
        CURRENT.set(previous);
    }
}
```

Tuy nhiên, ngay cả việc restore cũng chỉ đúng nếu scope được điều khiển nhất quán và không có luồng nào dùng chung state vượt quá hợp đồng. Với nhiều loại context, có thể dùng scope/stack có lifecycle rõ thay vì tự viết logic tùy tiện trong mọi advice.

### 5.3. Thread-local và async là hai phạm vi khác nhau

Một method trả `CompletionStage` có thể kết thúc ngay; advice `finally` trên caller thread nên cleanup scope của invocation tại thời điểm return. Nếu completion callback chạy trên thread khác, nó **không tự được kế thừa** giá trị `ThreadLocal` hiện có của caller.

Sai lầm phổ biến:

- Giữ context trong `ThreadLocal` của caller cho tới lúc stage hoàn tất — có thể rò giữa request vì thread được tái sử dụng.
- Gọi `CURRENT.remove()` trong completion callback ở thread worker và nghĩ rằng đã dọn `CURRENT` của caller — đó có thể là hai thread khác nhau.
- Capture cả `CallContext` chứa `args`/target chỉ để lấy correlation ID khi completion — kéo dài reference không cần thiết.

Nếu cần context xuyên async boundary, nên **chuyển snapshot tối thiểu theo task** và khôi phục bằng scope riêng ở thread chạy callback, rồi dọn/restore trên **chính thread đó**.

### 5.4. ThreadLocal key có thể yếu nhưng value vẫn được giữ

Trong triển khai Java thông dụng, entry của `ThreadLocalMap` dùng weak reference cho key, nhưng value vẫn là strong reference trong entry cho đến khi entry được loại bỏ/dọn thích hợp. Do đó **không thể xem weak key là lý do để bỏ qua `remove()`**. Với thread sống lâu, một value có thể bị giữ quá thời gian bạn cần.

Đừng xây chính sách bộ nhớ dựa vào “sớm muộn GC sẽ xử lý” khi chương trình tự biết chính xác lúc scope kết thúc.

## 6. Singleton aspect và trạng thái dùng chung

Một số công cụ có thể tạo instance aspect chia sẻ giữa nhiều join point hoặc nhiều thread; các cơ chế khác có lifecycle theo target, đối tượng hoặc một context cụ thể. **Không mặc định mọi aspect đều singleton**, nhưng phải phân tích lifecycle thực tế.

Nếu một aspect lâu sống có field:

```java
Object lastArgument;
Throwable lastError;
Object lastResult;
```

thì mỗi field có thể giữ object của lời gọi gần nhất **cho tới khi bị ghi đè hoặc aspect bị thu hồi**. Dù chỉ một reference, nếu object đó là payload lớn, vấn đề vẫn đáng kể. Với nhiều thread, field mutable còn có nguy cơ race và trộn dữ liệu giữa các request.

### 6.1. Trạng thái theo invocation là mặc định an toàn hơn

Thay vì:

```text
aspect.startTime = now()
...
duration = now() - aspect.startTime
```

nên dùng:

```text
startTime = now()        // local trong một invocation
...
duration = now() - startTime
```

Đây vừa là bài toán **thread safety** vừa là **scope/retention**. Một biến local không tự đảm bảo object được GC ngay, nhưng nó không bị ghim vào state chia sẻ của aspect sau khi scope kết thúc.

### 6.2. Không nhầm giữ lâu có chủ đích với rò rỉ

Một proxy thường cần giữ reference đến target trong thời gian proxy được dùng. Một registry cần giữ cấu hình matcher trong vòng đời module. Đó có thể là **ownership đúng**, không phải rò bộ nhớ.

Cần đánh giá: reference còn có **mục đích hoạt động** không? Có mất cơ hội release khi parent hết vòng đời không? Có bị nhân theo từng request mà không giới hạn không?

Mục tiêu bài này là **loại bỏ retention không có chủ đích**, không phải cực đoan xóa mọi reference trong advice.

## 7. Async callback: giữ ID nhỏ, không giữ toàn bộ join-point context

Với method đồng bộ:

```text
before -> proceed -> after/finally -> hết invocation
```

Với method bất đồng bộ:

```text
before -> proceed -> trả CompletionStage -> finally của invocation
                                      |
                                      +--> completion ở thời điểm sau
```

Nếu muốn đo task completion, callback có thể cần sống tới khi stage hoàn tất. Vì thế **một mức retention tạm thời là cần thiết**, nhưng phải được thiết kế:

- Giữ `startedNanos`, `operationName`, `traceId` dạng giá trị nhỏ thay vì cả `args`.
- Không capture `Proceed`, `target` hoặc `joinPoint` nếu callback chỉ cần số đo cuối.
- Không lưu `Throwable` vào collection vô hạn sau completion; chuyển thành error classification an toàn.
- Khi task bị cancel/timeout hoặc không complete, cần deadline, chính sách dọn dẹp và kiểm soát số pending callbacks.
- Nếu handler đăng ký callback trả về một dependent stage, phân biệt stage **gốc** và stage **phụ thuộc**; lỗi callback có thể tạo lỗi ở stage phụ thuộc, cần kênh xử lý riêng.
- Không coi completion callback luôn chạy cùng thread đã vào advice.
- Nếu result là một stream/publisher lazy, phải xét vòng đời subscription/onNext/onError/onComplete/cancel, không chỉ return của method tạo pipeline.

### 7.1. Không dùng callback chỉ để trì hoãn giải phóng của request

Ví dụ anti-pattern:

```text
onCompletion(() -> {
    logFullRequest(request);
    releaseRequestLater();
})
```

Nếu stage có nguy cơ pending lâu vô hạn, callback giữ request rất lâu. Trường hợp cần log hậu kỳ nên tạo **snapshot đã kiểm soát thông tin và kích thước ngay lúc invocation**, rồi callback giữ snapshot thay vì request object.

### 7.2. Một lambda có thể capture `this` ngoài ý muốn

Khi lambda gọi một instance method hoặc dùng field của aspect, nó có thể giữ `this`; `this` có thể liên kết tới target, cache, registry hoặc dữ liệu khác. Java compiler có cách capture biến mà lambda thực sự dùng, nhưng kết quả vẫn nên được kiểm tra bằng thiết kế: **đừng đưa nguyên `this` vào callback tồn tại lâu nếu có thể thay bằng các scalar hoặc service được sở hữu hợp lý**.

Không nhất thiết mọi capture của `this` là sai: nếu callback phải dùng một service hoạt động suốt ứng dụng, đó có thể là dependency hợp lệ. Phân biệt “callback giữ service dùng chung” và “callback giữ request-scoped object đã kết thúc”.

## 8. Ví dụ Java 17 hoàn chỉnh: cleanup dữ liệu lời gọi và quan sát completion

Chương trình sau dùng **JDK dynamic proxy theo interface**. Mục đích là minh họa việc advice tạm giữ `Payload` trong `ThreadLocal` **chỉ trong thời gian invocation**, sau đó xóa bằng `finally`, đồng thời đăng ký callback chỉ giữ `id` nhỏ để ghi kết quả async.

**Đây là ví dụ một cơ chế triển khai theo tư duy AOP**, không phải ngôn ngữ pointcut tổng quát. Nó không chặn mọi lần method execution hay mọi đường gọi trực tiếp vào target.

Tạo file **`AdviceReferenceLifetimeDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class AdviceReferenceLifetimeDemo {
    static final class Payload {
        final String id;
        final byte[] bytes;

        Payload(String id, byte[] bytes) {
            this.id = id;
            this.bytes = bytes;
        }
    }

    static final class CallContext {
        final String id;
        final Payload payload;

        CallContext(String id, Payload payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    static final ThreadLocal<CallContext> ACTIVE = new ThreadLocal<>();

    interface Worker {
        CompletionStage<String> work(Payload payload);
    }

    static final class TaskWorker implements Worker {
        private CompletableFuture<String> pending;

        @Override
        public CompletionStage<String> work(Payload payload) {
            if ("BAD".equals(payload.id)) {
                throw new IllegalArgumentException("bad id");
            }
            System.out.println("TARGET scheduled " + payload.id);
            pending = new CompletableFuture<>();
            return pending;
        }

        void complete(String value) {
            CompletableFuture<String> ready = pending;
            pending = null;
            ready.complete(value);
        }
    }

    static Worker instrument(Worker target) {
        return (Worker) Proxy.newProxyInstance(
                Worker.class.getClassLoader(),
                new Class<?>[] { Worker.class },
                (proxy, method, arguments) -> {
                    Payload payload = (Payload) arguments[0];
                    String id = payload.id;
                    ACTIVE.set(new CallContext(id, payload));

                    try {
                        System.out.println("ADVICE before " + id);
                        Object outcome;
                        try {
                            outcome = method.invoke(target, arguments);
                        } catch (InvocationTargetException wrapped) {
                            throw wrapped.getCause();
                        }

                        if (outcome instanceof CompletionStage<?> stage) {
                            // Callback chỉ cần id; không capture payload/context.
                            stage.whenComplete((value, failure) -> {
                                if (failure == null) {
                                    System.out.println("ASYNC complete "
                                            + id + "=" + value);
                                } else {
                                    System.out.println("ASYNC failure "
                                            + id + " "
                                            + failure.getClass().getSimpleName());
                                }
                            });
                        }

                        System.out.println("ADVICE return-handle " + id);
                        return outcome;
                    } catch (Throwable original) {
                        System.out.println("ADVICE synchronous-error "
                                + id + " "
                                + original.getClass().getSimpleName());
                        throw original;
                    } finally {
                        ACTIVE.remove();
                        System.out.println("ADVICE cleared " + id);
                    }
                }
        );
    }

    public static void main(String[] args) {
        TaskWorker target = new TaskWorker();
        Worker proxy = instrument(target);

        CompletionStage<String> task =
                proxy.work(new Payload("A", new byte[1024]));
        System.out.println("CALLER context-clear " + (ACTIVE.get() == null));
        target.complete("OK");
        System.out.println("CALLER result "
                + task.toCompletableFuture().join());

        try {
            proxy.work(new Payload("BAD", new byte[2048]));
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }
        System.out.println("CALLER context-clear " + (ACTIVE.get() == null));

        CompletionStage<String> direct =
                target.work(new Payload("DIRECT", new byte[512]));
        target.complete("DIRECT-OK");
        System.out.println("CALLER direct "
                + direct.toCompletableFuture().join());
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac AdviceReferenceLifetimeDemo.java
java AdviceReferenceLifetimeDemo
```

**Stdout kỳ vọng:**

```text
ADVICE before A
TARGET scheduled A
ADVICE return-handle A
ADVICE cleared A
CALLER context-clear true
ASYNC complete A=OK
CALLER result OK
ADVICE before BAD
ADVICE synchronous-error BAD IllegalArgumentException
ADVICE cleared BAD
CALLER caught bad id
CALLER context-clear true
TARGET scheduled DIRECT
CALLER direct DIRECT-OK
```

### 8.1. Đường A: dùng payload trong invocation, chỉ giữ ID trong callback

1. Caller gọi `proxy.work` với `Payload("A", byte[1024])`.
2. Handler tạo `CallContext` chứa một reference đến payload, lưu trong `ACTIVE` để phục vụ **invocation hiện tại**.
3. Target in `TARGET scheduled A`, tạo future pending và trả handle.
4. Handler đăng ký `whenComplete` chỉ dùng local `String id`, không truy cập `payload`, `arguments`, `CallContext` hay `ACTIVE` trong callback.
5. Handler in `ADVICE return-handle A`, sau đó `finally` xóa thread-local và in `ADVICE cleared A`.
6. Caller kiểm tra `ACTIVE.get() == null`, in `true` vì scope của invocation trên thread này đã được dọn.
7. `target.complete("OK")` làm stage hoàn tất. Callback in `ASYNC complete A=OK`; caller `join` đọc kết quả.

**Giới hạn suy luận:** Kiểm tra `ACTIVE.get() == null` chứng minh thread-local của **thread đang chạy demo** được dọn sau invocation; nó **không chứng minh payload đã được GC**. Payload vẫn có thể reachable từ những chỗ khác và GC không chạy theo lịch do code mong muốn. Quan trọng là callback không cần giữ payload để tạo dòng log.

### 8.2. Đường BAD: cleanup phải chạy khi ném lỗi

`proxy.work("BAD")` khiến target ném `IllegalArgumentException` đồng bộ. `Method.invoke` bọc lỗi target thành `InvocationTargetException`, handler gỡ cause và ném lại. Nhánh `catch` in `ADVICE synchronous-error BAD IllegalArgumentException`. `finally` vẫn chạy, xóa `ACTIVE` và in `ADVICE cleared BAD`.

Nếu chỉ đặt `ACTIVE.remove()` sau lời gọi target mà không dùng `finally`, nhánh lỗi có thể làm context còn lại trong thread-local. Với thread pool, điều này đặc biệt nguy hiểm khi thread được dùng lại bởi request khác.

### 8.3. Đường DIRECT: chỉ proxy invocation mới đi qua handler

`target.work("DIRECT")` gọi thẳng target, không có dòng ADVICE. Target vẫn lên lịch future và hoàn tất được. Đây là giới hạn **đường gọi qua proxy**, không phải quy tắc mọi công cụ AOP đều có. Không thể dùng sự vắng mặt của log từ proxy để khẳng định không có tác vụ chạy.

### 8.4. `TaskWorker.pending` cũng là một reference — tại sao hợp lệ trong demo?

Target giữ future trong field `pending` **để có cách hoàn tất chính task đó**. `complete` lấy reference rồi đặt field về `null` trước khi gọi `ready.complete(value)`. Đây là ví dụ retention có **mục đích và điểm giải phóng rõ ràng**.

Nhưng thiết kế đó chỉ hỗ trợ **một job pending tại một thời điểm**; nếu `work` được gọi đồng thời, reference có thể bị ghi đè. Trong ứng dụng thật cần quản lý nhiều job bằng registry/queue có ràng buộc dung lượng, đồng bộ hóa, timeout/cancel và cleanup cho mọi terminal path. Không sao chép nguyên demo vào xử lý concurrent production.

### 8.5. Vì sao callback chỉ giữ ID?

Nếu callback ghi `payload.bytes.length` hoặc `context.payload`, nó sẽ giữ reference tới payload cho tới khi callback không còn cần. Để ghi completion status của task, chỉ cần ID nhỏ và loại outcome. Dữ liệu `byte[1024]` không phục vụ công việc quan sát completion, nên không nên để callback giữ nó.

Lưu ý nếu ID chứa dữ liệu nhạy cảm hoặc quá dài, cần sanitize/giới hạn từ trước. Và vì `whenComplete` trả một **dependent stage**, ví dụ bỏ qua dependent stage này chỉ để tập trung vào retention; trong production cần quan tâm lỗi của chính callback instrumentation.

### 8.6. Không suy từ ví dụ thành “cứ xóa mọi context trong finally”

Demo không có nested invocation trên cùng thread, nên `ACTIVE.remove()` là đúng. Nếu một advice bên trong dùng cùng `ThreadLocal` và ghi đè context cũ, cần **restore giá trị trước đó** hoặc dùng scope stack có lifecycle rõ như mục 5.2. Nếu async worker cần thông tin context, phải truyền snapshot vào worker và quản lý scope ở worker, không giữ thread-local caller mở tới completion.

## 9. Snapshot dữ liệu tối thiểu: thu cái cần dùng, không thu cả object graph

Giả sử mục tiêu của advice là ghi số liệu:

```text
operation="save"
durationMs=12
outcome="failure"
errorType="IOException"
traceId="a1b2"
```

Không có lý do tự động giữ toàn bộ đối tượng `Customer`, `HttpRequest`, byte buffer hay `Throwable` lâu hơn invocation chỉ để tạo những trường đó.

### 9.1. Thiết kế bản ghi nhỏ, ổn định và an toàn

Các thuộc tính tốt:

- **Immutable:** tránh giữ tham chiếu tới object mutable bị thay đổi sau đó.
- **Scalar hoặc dữ liệu đã rút gọn:** operation name, enum status, timestamp, count, bounded identifier.
- **Không chứa bí mật:** không lưu token, mật khẩu, thông tin nhận dạng không được phép, toàn bộ request/response.
- **Giới hạn độ dài/cardinality:** không lấy raw exception message hoặc path người dùng làm nhãn metric không giới hạn.
- **Có vòng đời:** được đẩy tới sink hoặc bị loại khi queue đầy theo chính sách rõ.
- **Không phụ thuộc target còn sống:** record có thể tồn tại mà không cần giữ object nghiệp vụ.

Trong Java, một `record` có các field `String`, `long`, enum có thể là cấu trúc tiện cho snapshot, nhưng phải bảo đảm các `String`/field thật sự nhỏ và không giữ object không cần thiết.

### 9.2. Không deep-copy toàn bộ object chỉ vì sợ strong references

Một bản sao sâu toàn bộ request có thể tiêu tốn **nhiều bộ nhớ hơn**, tạo chi phí CPU và giữ thông tin nhạy cảm lâu hơn. Cách tốt thường là **chiếu dữ liệu (projection)** xuống tập trường cần thiết chứ không copy toàn bộ.

Nếu thực sự cần dữ liệu cho tác vụ async, cần đảm bảo dữ liệu vẫn còn hiệu lực tới khi tác vụ sử dụng xong. “Giảm giữ tham chiếu” không có nghĩa được làm hỏng correctness bằng cách bỏ reference khi target còn đang cần.

## 10. Các cấu trúc giữ lâu có chủ đích: cache, metrics, queue, listener

### 10.1. Cache phải có chính sách

Một cache key/value có thể giữ reference gián tiếp lớn. Chính sách tối thiểu:

- **Dung lượng tối đa** (số phần tử và khi cần ước lượng byte).
- **Eviction** dựa trên nhu cầu (LRU/TinyLFU hoặc chính sách khác).
- **TTL hoặc invalidation** phù hợp tính đúng đắn dữ liệu.
- **Bounded concurrency** khi nhiều request miss cùng lúc.
- **Cleanup** khi module ngừng hoạt động.
- **Định nghĩa ownership**: cache giữ dữ liệu đã snapshot hay object nghiệp vụ sống lâu?

TTL không tự hạn chế số phần tử tăng trong khoảng thời gian trước khi hết hạn; ngược lại eviction không giải quyết dữ liệu đã lỗi thời. Có thể cần cả hai.

### 10.2. Metrics nên giữ số liệu, không giữ invocation object

Một `LongAdder` đếm lỗi có thể phù hợp hơn collection lưu mọi Throwable. Histogram thời gian xử lý có thể phù hợp hơn danh sách mọi `startTime/finishTime` nếu bài toán chỉ cần phân phối.

Cảnh giác **cardinality**: gắn user ID hoặc message tự do làm metric label có thể tạo hàng triệu time series, một dạng retention/áp lực tài nguyên ở hệ thống đo lường dù advice không trực tiếp giữ object Java.

### 10.3. Queue quan sát phải bounded

Nếu advice đưa snapshot vào queue cho worker ghi log mà producer nhanh hơn consumer, queue không giới hạn sẽ giữ record tăng mãi. Cần chính sách backpressure, bounded buffer, drop/sampling hoặc xử lý lỗi phù hợp tầm quan trọng của telemetry.

Nếu chính sách là audit bắt buộc, “drop khi đầy” có thể không hợp lệ. Khi đó phải xây hợp đồng hệ thống rõ về giao dịch, lưu bền và phản hồi thất bại, không che bằng một collection in-memory.

### 10.4. Listener/subscription phải có scope

Một listener gắn vào global event bus có thể giữ object làm callback, bao gồm target hoặc request graph. Cần unregister khi scope kết thúc; nếu task dài hạn, có thể cần handle/cancellation token và deadline.

Với publisher/stream, phải theo dõi cả **subscription completion, error và cancellation**, không chỉ return của method tạo stream.

## 11. Weak references: công cụ bổ sung, không phải câu thần chú chống leak

Java có `WeakReference`, `SoftReference`, `PhantomReference` và reference queue với những hợp đồng khác nhau. Trong bài toán “không giữ một object sống chỉ vì cache quan sát nó”, weak reference có thể hữu ích, nhưng phải hiểu rõ giới hạn.

### 11.1. `WeakReference` không kéo dài lifetime như strong reference

Nếu chỉ còn weak reference tới một object, GC có thể thu hồi object đó. Nhưng:

- Không có bảo đảm GC sẽ chạy **ngay lập tức** tại thời điểm bạn muốn.
- `ref.get()` có thể trả object hoặc `null`, nên code phải chấp nhận mất đối tượng.
- Nếu còn strong reference ở nơi khác, weak reference không giúp object được thu hồi.
- Weak reference không thay thế việc **unregister listener**, đóng stream hoặc giải phóng tài nguyên ngoại vi đúng vòng đời.

Đừng viết test đòi `System.gc()` phải dọn object trong một khoảng mili giây cố định: GC không có hợp đồng như vậy.

### 11.2. Weak-key map vẫn có thể giữ key gián tiếp

`WeakHashMap<K,V>` sử dụng weak keys, nhưng **value được giữ mạnh** khi entry còn tồn tại. Nếu value lại tham chiếu mạnh về key, key có thể tiếp tục reachable qua map → value → key, làm mất lợi ích mong đợi.

Ví dụ ý tưởng nguy hiểm:

```text
WeakHashMap<Key, Value>
Value.key --> Key  // strong reference quay về key
```

Cần kiểm tra object graph thực, không chỉ thấy chữ “Weak” trong tên cấu trúc mà tin rằng hết memory leak.

### 11.3. `SoftReference` không phải cache eviction policy đầy đủ

Soft references phụ thuộc quyết định GC và áp lực bộ nhớ, không cho độ trễ eviction/TTL/budget rõ ràng. Với hệ thống production, một bounded cache có chính sách cụ thể thường dễ kiểm soát hơn cache dựa hoàn toàn vào hy vọng GC.

### 11.4. Reference queue và cleanup

Nếu dùng weak/phantom references cần theo dõi object bị thu hồi, một `ReferenceQueue` có thể hỗ trợ phát hiện các reference được enqueue. Tuy nhiên cleanup tài nguyên ngoại vi quan trọng nên có **owner và close/unregister rõ**, không dựa vào thời điểm GC không xác định để thực hiện hành vi nghiệp vụ đúng hạn.

## 12. Async, timeout và công việc không bao giờ kết thúc

Một callback được đăng ký trên future có thể giữ snapshot hoặc closure đến khi future hoàn tất. Nếu stage **không bao giờ complete**, callback có thể còn gắn với stage lâu bất thường.

Cần thiết kế:

1. **Deadline/timeout** cho logical task nếu nghiệp vụ cho phép.
2. **Cancellation propagation** xuống tác vụ thật khi abstraction hỗ trợ; chỉ cancel handle không luôn dừng I/O nền.
3. **Giới hạn pending tasks** và số callback được đăng ký.
4. **Cleanup ở terminal event**: success, failure hoặc cancellation.
5. **Giám sát orphaned tasks** và backlog để phát hiện tăng retention.
6. **Không capture request graph** nếu chỉ cần tên operation/ID để ghi outcome.
7. **Phân biệt timeout signal với tác vụ thật đã dừng**; tài nguyên có thể cần cleanup bổ sung.

Nếu task cần giữ payload tới lúc thực thi xong, hãy gắn ownership payload vào **task có vòng đời rõ**, không mượn một biến `ThreadLocal` của invocation rồi để nó tồn tại vô hạn.

### 12.1. Phân biệt dữ liệu do advice giữ và dữ liệu do task cần

Một task bất đồng bộ thực sự cần `Payload` để xử lý có thể giữ reference tới payload tới khi kết thúc; đây là retention **phục vụ nghiệp vụ**. Advice đo completion chỉ cần request ID không nên tự giữ **thêm** một reference tới payload nếu điều đó không cần thiết. Sau khi task hoàn tất, cả task owner và advice callback đều cần có cơ chế giải phóng phù hợp.

### 12.2. `CompletionStage` gốc và dependent stage

`whenComplete` trả một stage phụ thuộc. Nếu callback của advice ném exception:

- **Stage phụ thuộc** có thể bị lỗi theo hợp đồng của CompletionStage.
- Nếu advice **trả dependent stage** cho caller, lỗi instrumentation có thể đổi outcome caller quan sát.
- Nếu advice **trả stage gốc** và bỏ dependent stage, outcome caller có thể không đổi nhưng lỗi callback không được báo rõ ở nơi khác.

Để giữ hợp đồng của target, phải chọn stage trả ra và chính sách xử lý lỗi callback có chủ đích; cả hai quyết định đều liên quan đến vòng đời object và sự tồn tại của callback.

## 13. Nhận diện memory retention trong hệ thống có aspect

### 13.1. Quan sát xu hướng theo workload

Một phép thử có ý nghĩa có thể:

1. Khởi động cùng cấu hình và làm nóng ứng dụng.
2. Thực thi N request có payload cỡ xác định.
3. Chờ các task hoàn tất và cleanup theo hợp đồng.
4. Lặp chu kỳ nhiều lần ở tốc độ tương tự.
5. Theo dõi số context còn active, queue depth, cache entries, pending futures và heap/GC theo thời gian.
6. Chụp heap dump hoặc dùng profiler được phép để xem **retained path** nếu dữ liệu tăng bất thường.
7. So sánh khi bật/tắt instrumentation theo một thiết kế kiểm soát được.

Không kết luận chỉ vì một lần đo `Runtime.freeMemory()` giảm: runtime có thể giữ heap đã cấp phát để tái sử dụng và GC hoạt động theo thời điểm không xác định.

### 13.2. Tìm GC roots và retained paths

Nếu profiler chỉ ra:

```text
Thread worker
  -> ThreadLocalMap entry
  -> CallContext
  -> Payload
  -> large byte[]
```

thì việc thiếu cleanup của thread-local là giả thuyết có cơ sở. Nếu đường là:

```text
static aspect
  -> List<Throwable>
  -> CustomError
  -> originalRequest
```

thì lỗi đến từ collection sống dài. Nếu đường là:

```text
pending CompletableFuture
  -> completion callback
  -> captured Payload
```

cần hỏi payload có thực sự cần trong callback và task có deadline không.

**Điều cần sửa là đường sở hữu và vòng đời, không phải chỉ gọi `System.gc()`.**

### 13.3. Đừng xem `WeakReference.get() == null` là phép kiểm tra duy nhất

Dùng weak reference có thể hỗ trợ thí nghiệm trong điều kiện thích hợp, nhưng GC không xác định thời điểm. Một bộ test ổn định hơn có thể kiểm tra **số phần tử registry bằng 0 sau completion**, **ThreadLocal được xóa**, **listener đã unregister**, **queue không vượt cap** và **tham chiếu không được capture trong callback** qua review/profiler. Các kiểm chứng này đánh giá thiết kế deterministic hơn là buộc GC chạy.

## 14. Tính đúng đắn, bảo mật và concurrency

### 14.1. Giảm retention không được phá correctness

Nếu around advice cần retry hoặc tiếp tục sau I/O, có thể phải giữ đối số cho tới khi continuation thực hiện; giải phóng quá sớm có thể làm mất dữ liệu cần cho nghiệp vụ. Mục tiêu không phải “không giữ bất cứ gì” mà là “không giữ **lâu hơn thời gian cần thiết**”.

Ví dụ, một input stream đang được dùng phải sống đến khi đọc xong, nhưng sau khi đóng và hết scope thì không nên bị lưu trong một map debug vô hạn.

### 14.2. Dữ liệu nhạy cảm trong logs và metrics

Giữ reference quá lâu cũng là vấn đề riêng tư. Một Throwable có thể chứa input lỗi; argument có thể là token hoặc file y tế; target có thể giữ thông tin người dùng. Khi telemetry cần ghi một điều gì đó, dùng allowlist field an toàn, redaction và thời hạn lưu.

Không xây correlation ID bằng cách nối toàn bộ email, số điện thoại hoặc request body. Một identifier ngắn và opaque thường phù hợp hơn. Với metrics, tránh nhãn cardinality cao gây tăng lưu trữ.

### 14.3. Concurrency, race và aspect state

Nếu aspect dùng `HashMap` không đồng bộ để lưu mọi context đang chạy, nhiều thread có thể gây race, dữ liệu mất hoặc cấu trúc bị hỏng. Nếu chuyển sang `ConcurrentHashMap`, race cấu trúc có thể giảm nhưng không tự tạo **eviction**, timeout và ownership. Chính sách vòng đời vẫn phải rõ.

### 14.4. Reentrancy và lời gọi lồng nhau

Một advice có thể kích hoạt method cũng khớp pointcut (ví dụ logger gọi một method đã được instrument). Nếu cùng `ThreadLocal` được ghi đè bởi lời gọi trong, cần stack/restore như mục 5.2. Không đơn giản `remove()` rồi coi giá trị của tầng ngoài đã được khôi phục.

### 14.5. Proxy, weaving và đối tượng nào được giữ

JDK dynamic proxy có handler giữ reference đến target vì đó là quan hệ cần thiết để proxy làm việc. Class proxy hoặc code weaving có biểu diễn khác. Một advice có thể nhận join-point object sống ngắn; một engine có thể tự quản lý metadata caching; tất cả phải được xem theo hợp đồng và implementation cụ thể.

**Đừng suy rằng cứ có AOP là có memory leak.** Memory retention xuất hiện khi application/advice/tool giữ reference không phù hợp với vòng đời dự kiến. Cần tách trách nhiệm và kiểm chứng thay vì đổ mọi vấn đề bộ nhớ cho khái niệm AOP.

## 15. Checklist review một advice

| Câu hỏi | Cách đánh giá |
| --- | --- |
| Advice cần dữ liệu gì? | Liệt kê từng field thực sự dùng trong before, after, error và async callback. |
| Dữ liệu thuộc scope nào? | Một invocation, một task, một request, một thread, một module hay toàn ứng dụng? |
| Có lưu `args`, `result`, `target` hay `Throwable` không? | Nếu có, chứng minh thời hạn giữ và chủ sở hữu. |
| `ThreadLocal` có cleanup đúng thread không? | Kiểm tra success/throw/nested invocation và propagation async. |
| Có callback capture cả context không? | Đổi sang immutable snapshot tối thiểu khi có thể. |
| Có cache/list/registry tăng theo request không? | Đặt giới hạn, TTL, eviction, metrics cho số phần tử. |
| Listener/subscription có tháo đăng ký không? | Đảm bảo cleanup ở complete/error/cancel/shutdown. |
| Future có thể pending mãi không? | Có deadline, giám sát pending và giới hạn tài nguyên không? |
| Weak reference có bị value giữ ngược key không? | Xem retained paths, đừng tin chỉ vào tên API. |
| Context có dữ liệu nhạy cảm không? | Dùng allowlist, redaction, giới hạn thời gian lưu. |
| Có nhiều thread/nested call không? | Kiểm tra thread safety, restore context và callback lifecycle. |
| Có chứng minh được retention hợp lý không? | Dùng integration tests, counters, profiler/heap dump khi cần. |

## 16. Ca kiểm thử gợi ý

### Ca 1 — Cleanup sau success

Gọi method qua advice, target return bình thường. Sau khi advice kết thúc, assert context tạm của thread **đã được xóa hoặc restore** đúng như scope được thiết kế.

### Ca 2 — Cleanup sau exception

Target ném lỗi, advice quan sát và ném lại. Assert lỗi gốc tới caller và context tạm không còn ở thread đó. Đây là ca bắt buộc để kiểm chứng `finally`.

### Ca 3 — Nested advice

Outer đặt context A, inner đặt context B rồi kết thúc. Assert outer tiếp tục thấy context A. Với scope stack/restore, inner không được xóa context của outer.

### Ca 4 — Async pending

Target trả future chưa hoàn tất. Ngay sau return, assert thread-local của caller đã cleanup; callback chỉ giữ snapshot cần thiết. Đừng assert payload đã được GC.

### Ca 5 — Async terminal

Hoàn tất future thành công, lỗi hoặc cancel. Assert pending count/registry/subscription được giải phóng theo chính sách, không tăng qua nhiều vòng test.

### Ca 6 — Never-completing task

Giữ một stage pending vượt deadline được thiết kế. Assert cơ chế timeout/cancellation/quota không để giữ vô hạn toàn bộ request graph. Tránh kỳ vọng cancellation tự dừng mọi I/O nếu abstraction không hứa.

### Ca 7 — High-volume workload

Thực thi nhiều request nối tiếp và song song với payload cỡ lớn. Assert queue/cache size trong giới hạn, metric không tạo nhãn vô hạn, và retained paths không cho thấy collection static giữ mọi argument.

### Ca 8 — Proxy bypass

Gọi target trực tiếp, xác nhận handler proxy không ghi trạng thái. Dữ liệu do target giữ theo hợp đồng nghiệp vụ vẫn có thể tồn tại; cần phân biệt retention của target với retention do advice tạo ra.

### Ca 9 — Listener registration

Tạo scope, đăng ký listener có callback, kết thúc scope rồi unregister. Assert registry không còn listener; đừng dùng GC như cơ chế duy nhất kiểm tra unsubscribe.

## 17. Các ngộ nhận thường gặp

**“Garbage collector sẽ tự sửa mọi memory leak.”** Sai. GC không giải phóng object vẫn reachable qua strong references sống dài.

**“Mảng `Object[]` nhỏ thì lưu `args` không sao.”** Sai. Các phần tử có thể dẫn tới graph lớn.

**“Sao chép `args.clone()` giải quyết retention.”** Sai. Đó là shallow copy và vẫn giữ các phần tử.

**“Chỉ dùng một field `lastError` nên không thể leak.”** Sai. Một Throwable có thể giữ nhiều dữ liệu và sống suốt đời aspect.

**“`ThreadLocal` tách theo thread nên không cần `remove()`.”** Sai. Thread pool sống lâu, value có thể tồn tại xuyên request.

**“`remove()` trong callback async sẽ dọn context của thread gọi method.”** Không nếu callback ở thread khác. Thread-local thuộc thread cụ thể.

**“`finally { remove(); }` luôn đúng kể cả nested scope.”** Chưa đủ; có thể cần restore previous context.

**“Callback chỉ cần request ID thì cứ capture nguyên request cũng được.”** Không nên. Capture nguyên request kéo dài tuổi thọ object graph ngoài nhu cầu quan sát.

**“Dùng `WeakHashMap` thì key chắc chắn được giải phóng.”** Không nếu value hoặc một nơi khác còn strong reference về key.

**“Bộ nhớ tăng trong một lần chạy nghĩa là chắc chắn leak.”** Không. Cần xem workload, GC, heap policy và retained paths.

**“Cache không giới hạn là giải pháp tăng hiệu năng.”** Không. Retention vô hạn có thể gây áp lực heap, GC và rủi ro dữ liệu.

**“Không nên giữ bất kỳ reference nào trong advice.”** Quá cực đoan. Reference cần thiết trong invocation, task hoặc lifetime của proxy là hợp lệ; vấn đề là thiếu giới hạn và điểm kết thúc.

## 18. Tự đánh giá

**1.** Một singleton aspect lưu mọi `args` trong `ArrayList<Object[]>`. Vì sao memory có thể tăng dù target kết thúc?

**Đáp án:** List giữ mảng đối số, mảng giữ các phần tử, các phần tử có thể giữ object graph; target kết thúc không cắt đường strong reference từ singleton.

**2.** `args.clone()` có giải quyết khi một argument là `byte[10_000_000]` không?

**Đáp án:** Không. Mảng `Object[]` mới vẫn tham chiếu cùng byte array.

**3.** Advice chỉ cần một ID để ghi completion của future. Nên capture request hay ID?

**Đáp án:** Chỉ snapshot ID đã được giới hạn/sanitize nếu đủ. Tránh capture request object và context lớn không cần thiết.

**4.** Caller thread dùng `ThreadLocal`, callback chạy ở worker thread và gọi `remove()`. Caller có chắc sạch context không?

**Đáp án:** Không. `remove` tác động lên thread đang chạy callback, không phải tự động lên thread caller.

**5.** Với nested advice, inner xóa thread-local trong `finally` có đủ không?

**Đáp án:** Nếu outer cũng có context trong cùng slot, phải restore giá trị trước đó hoặc dùng scope stack hợp lệ; xóa đơn thuần có thể làm mất outer context.

**6.** `WeakHashMap` có key yếu nhưng value giữ reference tới key. Key có chắc được thu hồi không?

**Đáp án:** Không; đường map → value → key có thể duy trì strong reachability của key.

**7.** Vì sao `System.gc()` không phải assertion ổn định cho memory leak test?

**Đáp án:** GC không có lịch chạy và kết quả thu gom xác định theo thời điểm, kể cả khi đã được gợi ý gọi GC.

**8.** Proxy giữ reference tới target suốt thời gian proxy còn dùng. Điều đó có luôn là leak không?

**Đáp án:** Không. Nếu target là thành phần mà proxy cần điều phối, đó là quan hệ sở hữu hợp lệ; phải giải phóng khi proxy/module hết nhu cầu.

**9.** Future pending mãi và callback capture request lớn. Rủi ro là gì?

**Đáp án:** Khi stage/callback còn reachable, request graph có thể bị giữ lâu, ngoài ra số pending stages có thể tăng không giới hạn nếu thiếu timeout/quota/cleanup.

**10.** Một aspect ghi metric bằng cách lưu message của mọi Throwable làm nhãn. Vấn đề nào xuất hiện?

**Đáp án:** Message có thể chứa dữ liệu nhạy cảm, độ dài bất thường và cardinality cao; cần phân loại lỗi ổn định, redaction và giới hạn.

## Kết luận

Trong AOP, advice thường nhận những dữ liệu rất giàu ngữ cảnh nhưng **không sở hữu chúng vô thời hạn**. Dữ liệu của một join point nên được sử dụng trong **scope ngắn nhất phù hợp**; đối với async, chỉ giữ những snapshot nhỏ cần thiết đến completion; đối với state dài hạn, phải có **ownership, giới hạn, chính sách giải phóng và kiểm thử**. Đặc biệt chú ý các đường giữ mạnh qua `args`, target, result, Throwable, singleton aspect, `ThreadLocal`, callback, cache và listener. GC không thể sửa một thiết kế vẫn cố giữ object reachable. Cách áp dụng an toàn là dùng local scope hoặc cleanup/restore trong `finally`, không capture toàn bộ context cho tác vụ hậu kỳ, giữ đúng dữ liệu cho nghiệp vụ và chứng minh vòng đời qua test/profiler.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-008">Giới hạn thu thập tham số chứa dữ liệu nhạy cảm</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thu thập tham số chứa dữ liệu nhạy cảm trong advice AOP

**Advice** trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**) có thể quan sát các lời gọi và lần thực thi phương thức, đồng thời nhận thông tin về chữ ký, đối tượng đích, đối tượng đang thực thi, các tham số, kết quả và ngoại lệ tùy cơ chế hỗ trợ. Khả năng nhận dữ liệu này giúp ghi log, audit, tracing và đo lường; nhưng cũng làm một advice ghi log quá rộng trở thành **đường rò rỉ dữ liệu nhạy cảm**.

Một lời gọi nghiệp vụ có thể mang mật khẩu, access token, khóa API, số tài khoản, thông tin sức khỏe, định danh cá nhân, địa chỉ giao hàng hoặc payload tự do do người dùng nhập. Nếu advice mặc định in toàn bộ `args`, serialise object hoặc gọi `toString()`, dữ liệu ấy có thể bị nhân bản vào hệ thống log, công cụ tracing, bộ thu thập metrics, queue, bản sao lưu hoặc trang xử lý sự cố.

**Nguyên tắc trọng tâm:** giới hạn **ngay tại lúc thu thập**; mặc định **không ghi giá trị tham số**, chỉ lấy những thuộc tính thực sự cần thiết và đã được phân loại là an toàn. Che giấu sau khi dữ liệu thô đã bị gửi đi không phải là biện pháp tương đương.

Bài học này áp dụng cho **AOP nói chung**: phân biệt loại join point, cách expose/binding ngữ cảnh, chính sách tối thiểu hóa dữ liệu, chiến lược biến đổi, giới hạn lưu trữ và cách kiểm thử. Cú pháp pointcut/khả năng đọc tham số phụ thuộc nền tảng AOP; ví dụ Java 17 ở dưới chỉ mô phỏng một lời gọi được chặn qua dynamic proxy để làm rõ các nguyên tắc.

## 1. Đặt đúng ranh giới: pointcut, binding và collection

Trong AOP:

- **Join point:** vị trí/sự kiện chương trình mà cơ chế AOP có thể can thiệp, ví dụ method call hoặc method execution khi được hỗ trợ.
- **Pointcut:** quy tắc chọn join point cần can thiệp, chẳng hạn phương thức thuộc một thành phần hoặc một chữ ký cụ thể.
- **Advice:** xử lý được chạy ở điểm đã chọn, ví dụ trước khi gọi hoặc sau khi kết thúc.
- **Context binding/exposure:** cách đưa thông tin của lần thực thi vào advice; có thể gồm `args`, `target`, `this`, signature, result hay exception tùy loại join point.
- **Weaving:** cách kết hợp advice với code nghiệp vụ bằng khả năng của nền tảng: proxy, biến đổi bytecode hoặc kỹ thuật khác.

Có ba quyết định **khác nhau** cần thực hiện:

1. **Có được chọn join point này không?** Đây là quyết định phạm vi cấu trúc và điều kiện pointcut.
2. **Advice có thể nhận thông tin gì?** Đây là khả năng binding của mô hình AOP.
3. **Thông tin nào được phép thu thập, giữ hoặc phát tán?** Đây là chính sách an toàn dữ liệu.

Cơ chế AOP **có thể** cung cấp toàn bộ mảng `Object[] args`, nhưng điều đó không có nghĩa advice **nên** copy toàn bộ mảng vào một event log. Tính năng kỹ thuật và quyền sử dụng dữ liệu là hai việc tách biệt.

Ví dụ mảnh code minh họa:

~~~java
checkout.placeOrder(accessToken, shippingAddress, channel, totalCents);
~~~

Nếu chỉ cần biết kênh thanh toán và tình trạng thành công, advice có thể phát ra:

~~~text
event=order.place channel=WEB outcome=SUCCESS
~~~

Không có lý do tự động nào để lưu token, địa chỉ, giá trị tổng tiền hoặc toàn bộ `checkout.toString()` chỉ vì những giá trị đó sẵn có tại join point.

## 2. Vì sao thu thập tham số tự động lại nguy hiểm?

Một mảng args có thể mang dữ liệu từ nhiều vùng tin cậy:

| Nhóm dữ liệu | Ví dụ | Rủi ro nếu advice ghi nguyên giá trị |
| --- | --- | --- |
| Bí mật xác thực | Mật khẩu, OTP, API key, refresh token, bearer token | Người có quyền đọc log có thể dùng lại bí mật để truy cập |
| Định danh phiên | Cookie phiên, session identifier, token reset | Chiếm phiên, liên kết hành vi trái phép |
| Dữ liệu định danh | Số giấy tờ, địa chỉ, điện thoại, email, tên khách hàng | Xâm phạm riêng tư hoặc liên kết danh tính |
| Tài chính | Số tài khoản, thông tin thẻ, số dư, nội dung giao dịch | Rò rỉ dữ liệu tài chính, vi phạm chính sách |
| Dữ liệu đặc biệt nhạy cảm | Hồ sơ sức khỏe, nội dung cá nhân, thông tin trẻ em | Hậu quả riêng tư và pháp lý cao |
| Bí mật hệ thống | Chuỗi kết nối có mật khẩu, private key, khóa mã hóa | Mở rộng bề mặt tấn công hạ tầng |
| Payload tự do | Search text, ghi chú, nội dung form, tệp tải lên | Chứa bất kỳ dữ liệu nhạy cảm nào, khó lọc bằng tên |
| Kết quả và lỗi | Response body, exception message, stack trace | Có thể chứa lại đầu vào, query, token hoặc địa chỉ nội bộ |

Một đối tượng có vẻ vô hại như `OrderRequest` có thể chứa những field bí mật lồng sâu. Gọi `String.valueOf(request)` hoặc serialise toàn bộ object là hành vi **mở rộng phạm vi thu thập**, kể cả khi tên phương thức nghe không liên quan dữ liệu nhạy cảm.

Dữ liệu có thể bị nhân bản qua nhiều tầng:

~~~text
Invocation arguments
        |
        v
 Advice giữ/copy giá trị thô
        |
        +--> Log formatter / console
        +--> Trace span attribute / baggage
        +--> Event queue / metrics label
        +--> Error reporter / crash dump
        +--> Tập trung log / backup / kho phân tích
        |
        v
Nhiều nơi lưu, nhiều quyền truy cập, nhiều vòng đời
~~~

Vì vậy, việc định dạng log chỉ là **một điểm bảo vệ**. Cần nghĩ về luồng dữ liệu từ lúc advice đọc args cho đến lúc event bị xóa hoặc được chia sẻ cho hệ thống khác.

## 3. Phân loại dữ liệu trước khi thiết kế advice

Không nên chờ đến lúc viết dòng log mới đoán tham số có nhạy cảm hay không. Mỗi thao tác được chọn nên có một **hợp đồng ghi nhận dữ liệu (observability data contract)**:

- **Mục đích:** cần chẩn đoán lỗi, audit hay đo độ trễ?
- **Loại sự kiện:** thao tác nào, mức độ quan trọng, trạng thái hoàn thành?
- **Trường được phép:** sự kiện, kênh dạng enum, mã lỗi đã chuẩn hóa, thời lượng hoặc ID tương quan phi nhạy cảm.
- **Trường cấm:** thông tin xác thực, payload riêng tư, dữ liệu thô không cần thiết.
- **Biến đổi được phép:** bỏ, phân loại, làm tròn, tổng hợp, mask/tokenize theo chính sách.
- **Đích gửi:** log, trace, metrics, cảnh báo, audit store.
- **Quyền truy cập và vòng đời:** ai được đọc, giữ bao lâu, khi nào xóa?
- **Kiểm thử:** làm sao chứng minh chuỗi canary nhạy cảm không đi vào bất kỳ đầu ra nào?

### 3.1 Không đánh giá bằng kiểu dữ liệu đơn giản

Một `String` có thể là kênh `"WEB"` hoặc bearer token. Một `int` có thể là số lượng hàng hoặc mã PIN. Một enum có thể mô tả trạng thái thông thường, nhưng cũng có thể là phân loại bệnh. **Không có kiểu Java nào tự chứng minh dữ liệu an toàn để log.**

Cách làm thích hợp là phân loại **theo ý nghĩa nghiệp vụ và đích thu thập**, không chỉ dựa vào `String`, `int` hay tên tham số.

### 3.2 Chính sách mặc định loại trừ (default-deny)

Một quy tắc an toàn có thể là:

~~~text
if field is approved, required, and already safe:
    collect minimal representation
else:
    do not collect field value
~~~

Trường chưa được phân loại **không được tự động cho qua**. Khi thêm một tham số mới, hệ thống log vẫn an toàn theo mặc định. Đây là khác biệt cơ bản giữa **allowlist** và **denylist theo tên trường**.

Denylist có thể bỏ sót tên `pwd`, `credential`, `authorization`, `secretValue` hoặc field lồng sâu dưới một tên khác. Allowlist ngược lại buộc chủ sở hữu hệ thống chủ động tuyên bố điều gì được phép đi vào kênh quan sát.

## 4. Tránh những lối tắt dễ làm lộ dữ liệu

### 4.1 In nguyên mảng args

~~~java
System.out.println(java.util.Arrays.toString(args));
~~~

Đây là **mảnh mã cần tránh đối với dữ liệu không được phân loại**. Nó gọi cách biểu diễn chuỗi của các phần tử; có thể để lộ nhiều trường hoặc chuỗi chứa dữ liệu nhạy cảm. Với mảng lồng nhau, các API biểu diễn sâu còn tăng độ rộng thu thập.

### 4.2 Gọi `toString()` của toàn bộ target hoặc request

~~~java
logger("request=" + request);
~~~

Đây là **mảnh minh họa**, không phải chương trình độc lập. Một `toString()` do mã nghiệp vụ viết có thể in tất cả field, truy cập object lồng nhau, chạy logic không mong muốn hoặc ném ngoại lệ. Advice không nên dựa vào `toString()` của đối tượng tùy ý để quyết định những trường nào được lưu.

### 4.3 Dựa hoàn toàn vào regex để che bí mật

Ví dụ thay số thẻ bằng `****` sau khi đã tạo chuỗi log có thể bỏ sót định dạng lạ, dữ liệu phân mảnh hoặc secret xuất hiện ở vị trí không lường trước. Regex phù hợp cho **lớp phòng vệ bổ sung**, không thay việc bỏ trường ngay khi thu thập.

### 4.4 Cho rằng chỉ log DEBUG mới nguy hiểm

Tham số có thể lọt vào event ngay khi tạo dữ liệu, trước khi filter theo mức log, hoặc vào hệ thống tracing và báo lỗi tự động. Log có thể bật ở production khi điều tra sự cố. Cần bảo vệ **bất kể mức log**, không chỉ dựa vào cấu hình tắt DEBUG.

### 4.5 Ghi thông báo ngoại lệ nguyên văn

~~~java
System.out.println(error.getMessage());
~~~

Đây là **mảnh mã rủi ro nếu error message có thể chứa đầu vào**. Ngoại lệ từ HTTP, SQL, validation hoặc ứng dụng có thể nhúng token, địa chỉ, email hoặc payload. Nếu cần trạng thái lỗi, nên chọn **mã lỗi và loại đã chuẩn hóa**, hạn chế ghi chi tiết chưa lọc.

## 5. Phạm vi pointcut càng rộng, bề mặt lộ càng lớn

Một pointcut chọn mọi method execution trong cả ứng dụng có thể bắt cả:

- Phương thức đăng nhập chứa password/OTP.
- Phương thức gửi thanh toán chứa dữ liệu thẻ.
- Phương thức đọc hồ sơ có dữ liệu đặc biệt nhạy cảm.
- Phương thức xử lý token và khóa hệ thống.
- Phương thức chỉ dùng nội bộ hoặc mã sinh mà trước đó người viết không để ý.

Vì vậy, một chính sách quan sát tốt thường giới hạn cấu trúc theo **đúng nhóm nghiệp vụ cần quan sát**, sau đó áp dụng **allowlist dữ liệu** ở cấp tham số/trường.

~~~text
selected = structuralScope AND allowedJoinPointKind

event = {
    operation: approvedOperationName,
    outcome: approvedStatus,
    durationBucket: approvedBucket
}

raw args -> excluded by default
~~~

Các biểu thức trên là **giả mã ngữ nghĩa**, không phải cú pháp pointcut phổ quát. Kể cả khi pointcut đủ hẹp, một phương thức thuộc đúng nhóm vẫn có thể nhận secret; phạm vi hẹp không thay thế phân loại field.

### 5.1 Call và execution có thể expose ngữ cảnh khác nhau

Tại **method call join point**, advice có thể quan sát args từ chỗ phát sinh lời gọi, đối tượng đích và đối tượng phía caller nếu mô hình hỗ trợ. Tại **method execution join point**, advice quan sát lần chạy thân phương thức và các tham số của phần thực thi đó.

Không phải cơ chế AOP nào cũng expose cả hai loại điểm. Với proxy qua interface, advice chỉ thấy lời gọi **đi qua proxy**; lời gọi trực tiếp hay self-invocation trên target có thể bỏ qua. **Giới hạn phạm vi thu thập** và **giới hạn phạm vi weaving** phải được kiểm chứng riêng: một phép đo không đầy đủ không được trình bày như đã bao phủ mọi đường vào.

### 5.2 Target và this cũng có thể chứa secret

Không chỉ args: đối tượng `target` hoặc `this` có thể giữ thông tin cấu hình, trạng thái xác thực, cache dữ liệu người dùng và tham chiếu tới object có vòng đời dài. Ghi `target.toString()`, dump toàn bộ field qua reflection hoặc đưa object trực tiếp vào event queue cũng có nguy cơ rò dữ liệu.

Nếu chỉ cần biết loại thao tác, dùng **tên sự kiện đã cố định** thay vì trích xuất object. Nếu cần loại target, cân nhắc ánh xạ vào một **nhóm danh mục được duyệt** thay vì in tên lớp đầy đủ, vì tên lớp và đường dẫn nội bộ cũng có thể là thông tin hạ tầng không cần công bố.

## 6. Kỹ thuật thu thập an toàn

### 6.1 Thu thập metadata và trạng thái đã chuẩn hóa

Những trường thường dễ kiểm soát hơn nội dung dữ liệu thô:

| Nhu cầu | Trường có thể xem xét | Điều không nên lấy mặc định |
| --- | --- | --- |
| Đếm lời gọi | Mã sự kiện cố định, success/error | Nguyên mảng tham số |
| Đo độ trễ | Tên thao tác được duyệt, duration | Response body hoặc credential |
| Điều tra validation | Mã quy tắc vi phạm, trường phân loại | Giá trị đầu vào bị từ chối |
| Audit truy cập | Loại thao tác, kết quả allow/deny, chủ thể đã xác thực theo chính sách | Token hoặc payload xác thực |
| Theo dõi lỗi | Mã lỗi an toàn, nhóm lỗi, request correlation | Exception message chưa lọc |
| Đo phân bố | Histogram thời gian hoặc lượng dữ liệu được nhóm | Giá trị cá nhân làm label duy nhất |

Khi một event không cần định danh đối tượng, **bỏ hẳn ID** thường an toàn hơn việc tạo ID thay thế. Nếu thực sự cần correlation, dùng định danh ngẫu nhiên không suy ra thông tin cá nhân và có vòng đời/không gian sử dụng phù hợp.

### 6.2 Redaction, masking, hashing và tokenization khác nhau

- **Omission:** bỏ hoàn toàn trường. Đây thường là mặc định tốt nhất cho secret.
- **Redaction:** thay giá trị bằng nhãn cố định như `<REDACTED>`. Cho biết tham số có mặt nhưng không giữ nội dung.
- **Masking:** giữ một phần ký tự. Chỉ hợp lệ khi chính sách cho phép; vài ký tự cuối vẫn có thể đủ để liên kết hoặc suy đoán dữ liệu.
- **Aggregation/bucketing:** thay số cụ thể bằng nhóm có ý nghĩa như `SMALL`, `MEDIUM`, `LARGE`, khi ranh giới được duyệt.
- **Hashing:** tạo dấu vết một chiều; **hash không salt/không key đối với giá trị dễ đoán vẫn có thể bị dò từ điển**. Kể cả HMAC, giá trị đầu ra có thể là định danh liên kết lâu dài, nên vẫn cần giới hạn quyền và vòng đời.
- **Tokenization:** ánh xạ dữ liệu sang mã giả thay thế, có thể cho phép tra cứu ngược qua kho ánh xạ được bảo vệ; kho ấy trở thành thành phần nhạy cảm riêng.
- **Encryption:** bảo vệ nội dung khi lưu/truyền nếu quản lý khóa đúng, nhưng **không xóa bỏ trách nhiệm** tối thiểu hóa thu thập hoặc điều chỉnh quyền đọc giải mã.

Không được nói “đã hash nên không còn dữ liệu nhạy cảm” cho mọi trường hợp; tính liên kết qua nhiều event hoặc nguy cơ dò giá trị vẫn có thể tồn tại.

### 6.3 Không log dữ liệu đầu vào dù có lỗi

Ở nhánh validation fail, log có thể ghi `validation_rule=AMOUNT_NEGATIVE` thay vì `badAmount=-123456789`. Ở nhánh authentication fail, ghi mã lỗi `AUTH_FAILED`, không in credential hay token được cung cấp.

Nếu yêu cầu audit cần giữ nhiều thông tin hơn log chẩn đoán thông thường, hãy thiết kế **kênh audit riêng**, dữ liệu được phê duyệt, phân quyền nghiêm ngặt và vòng đời phù hợp. Không nên cho phép một advice log thông thường thu thập vô hạn chỉ vì cùng hệ thống có nhu cầu kiểm toán.

## 7. Java 17: ví dụ chạy độc lập với allowlist và annotation

Ví dụ sau **không sử dụng ngôn ngữ pointcut của một bộ AOP cụ thể**. Nó dùng dynamic proxy Java để minh họa cách advice có thể nhận `Method` và `args` nhưng **chỉ tạo bản tóm tắt an toàn** cho log.

Hợp đồng:

- `@Sensitive`: tham số chứa thông tin phải che, chỉ log nhãn `<REDACTED>`.
- `@SafeToLog`: gợi ý một tham số **đã được duyệt** cho mục đích minh họa; code **chỉ cho phép** giá trị thuộc enum `Channel`, không tự log mọi object mang annotation.
- Không có annotation: `<OMITTED>` theo mặc định.
- Không log `Method.toString()`, `target.toString()`, toàn bộ `args` hoặc exception message.
- Với nhánh lỗi, log **category** đã chuẩn hóa, vẫn ném tiếp ngoại lệ nghiệp vụ gốc.
- Mã là ví dụ **đơn luồng**, không thực hiện thanh toán thật và không xác thực người dùng.

Tạo file `SafeAdviceLoggingDemo.java`:

~~~java
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.StringJoiner;

public class SafeAdviceLoggingDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Sensitive {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface SafeToLog {}

    enum Channel {
        WEB, MOBILE
    }

    interface Checkout {
        String placeOrder(
                @Sensitive String sessionToken,
                @Sensitive String shippingAddress,
                @SafeToLog Channel channel,
                int totalCents);
    }

    static final class CheckoutImpl implements Checkout {
        @Override
        public String placeOrder(
                String sessionToken,
                String shippingAddress,
                Channel channel,
                int totalCents) {
            if (totalCents < 0) {
                throw new IllegalArgumentException("invalid amount");
            }
            System.out.println("BUSINESS processed");
            return "ACCEPTED";
        }
    }

    static String safeArguments(Method method, Object[] values) {
        Annotation[][] parameterTags =
                method.getParameterAnnotations();
        StringJoiner result = new StringJoiner(",");

        for (int i = 0; i < parameterTags.length; i++) {
            boolean sensitive = false;
            boolean safe = false;

            for (Annotation tag : parameterTags[i]) {
                if (tag.annotationType() == Sensitive.class) {
                    sensitive = true;
                }
                if (tag.annotationType() == SafeToLog.class) {
                    safe = true;
                }
            }

            String rendered = "<OMITTED>";
            if (sensitive) {
                rendered = "<REDACTED>";
            } else if (safe && i < values.length
                    && values[i] instanceof Channel channel) {
                // name() có tập giá trị hữu hạn được duyệt.
                rendered = channel.name();
            }

            result.add("p" + i + "=" + rendered);
        }
        return result.toString();
    }

    static Object invokeTarget(
            Object target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Checkout instrument(Checkout target) {
        return (Checkout) Proxy.newProxyInstance(
                Checkout.class.getClassLoader(),
                new Class<?>[] { Checkout.class },
                (proxy, method, args) -> {
                    Object[] values =
                            args == null ? new Object[0] : args;

                    if (method.getDeclaringClass() == Object.class) {
                        return invokeTarget(target, method, values);
                    }

                    System.out.println("ADVICE event=order.place args="
                            + safeArguments(method, values));

                    try {
                        Object result =
                                invokeTarget(target, method, values);
                        System.out.println("ADVICE status=SUCCESS");
                        return result;
                    } catch (Throwable error) {
                        String category =
                                error instanceof IllegalArgumentException
                                ? "VALIDATION" : "OTHER";
                        System.out.println(
                                "ADVICE status=ERROR category=" + category);
                        throw error;
                    }
                });
    }

    public static void main(String[] args) {
        Checkout checkout = instrument(new CheckoutImpl());

        System.out.println("CALLER " + checkout.placeOrder(
                "demo-token", "demo-address", Channel.WEB, 1200));

        try {
            checkout.placeOrder(
                    "demo-token-2", "demo-address-2",
                    Channel.MOBILE, -1);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER validation error");
        }
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac SafeAdviceLoggingDemo.java
java SafeAdviceLoggingDemo
~~~

Stdout chính xác:

~~~text
ADVICE event=order.place args=p0=<REDACTED>,p1=<REDACTED>,p2=WEB,p3=<OMITTED>
BUSINESS processed
ADVICE status=SUCCESS
CALLER ACCEPTED
ADVICE event=order.place args=p0=<REDACTED>,p1=<REDACTED>,p2=MOBILE,p3=<OMITTED>
ADVICE status=ERROR category=VALIDATION
CALLER validation error
~~~

### 7.1 Vì sao đây là ví dụ thu thập tối thiểu?

**Thứ nhất**, `sessionToken` và `shippingAddress` ở vị trí 0 và 1 không được chuyển thành chuỗi. Advice chỉ xuất ra nhãn cố định `<REDACTED>`; không cắt chuỗi đầu vào, không in độ dài và không tính hash.

**Thứ hai**, `channel` là enum với một tập giá trị đã biết; code chỉ dùng `name()` khi annotation cho phép **và** giá trị thực tế thuộc enum đó. Một tham số `String` được gắn cùng annotation sẽ không tự được in nhờ điều kiện kiểm tra kiểu hạn chế.

**Thứ ba**, `totalCents` không có annotation `@SafeToLog`, nên log chỉ cho biết `p3=<OMITTED>`; nó không suy rằng mọi số nguyên đều an toàn. Tùy hệ thống, giá trị tiền có thể là dữ liệu kinh doanh nhạy cảm.

**Thứ tư**, advice ghi `SUCCESS` hoặc `ERROR category=VALIDATION` thay vì in kết quả, error message và stack trace. Lỗi nghiệp vụ được truyền ra như cũ; việc ẩn thông tin trên log **không có nghĩa thay đổi ngoại lệ nhận bởi bên gọi**.

**Thứ năm**, annotation được đọc từ `Method` của interface `Checkout` mà dynamic proxy cung cấp. Trong ví dụ, annotation được khai báo **trực tiếp ở tham số của interface**. Parameter annotation trên interface không thể mặc định coi là có mặt trên mọi `Method` của lớp triển khai nếu đọc reflection tại một điểm khác. Vì vậy cơ chế AOP thật cần xác định chính xác **chữ ký nào được đọc để phân loại**.

### 7.2 Những giới hạn không được bỏ qua

- Dynamic proxy chỉ can thiệp những lời gọi **thông qua proxy**. Nếu gọi trực tiếp `new CheckoutImpl().placeOrder(...)`, advice demo không chạy. Điều này làm phạm vi đo khác, **không chứng minh các đường khác an toàn để log**.
- Các giá trị `"demo-token"`, `"demo-address"` là **dữ liệu giả** trong mã minh họa, không phải secret thật.
- Nếu một developer bỏ annotation hoặc thêm tham số mới, chính sách **mặc định bỏ giá trị**; đó là hành vi mong muốn.
- Annotation là **tín hiệu phân loại**, không tự kiểm định pháp lý hay bảo đảm một field có thật sự an toàn. Quy trình review và kiểm thử vẫn cần thiết.
- Ví dụ không có logger bất đồng bộ, metric backend, audit store hoặc chính sách retention. Những phần đó cần triển khai theo môi trường thật.
- Hệ thống có thể có thêm đường log từ thư viện HTTP, ORM, exception handler, request filter hoặc các công cụ profiling độc lập với advice. Bảo đảm riêng tại advice **không bảo đảm toàn ứng dụng không bao giờ lưu secret**.

## 8. Nhận dữ liệu theo loại advice mà không làm lộ bí mật

### 8.1 Before advice

Before advice thường thấy args trước khi gọi nghiệp vụ. Nó phù hợp để ghi **tên sự kiện an toàn**, số lượng tham số nếu cần, correlation ID hoặc thời điểm bắt đầu. Nhưng **chưa có kết quả**; không được ghi `success=true` tại điểm này chỉ vì phương thức đã được gọi.

Một điểm rủi ro là in **mọi tham số đầu vào** trước cả khi biết phương thức có được phép chạy hay không. Người dùng độc hại có thể cố đưa dữ liệu nhạy cảm hoặc chuỗi gây log injection vào các đối số.

### 8.2 After returning advice

Loại này có thể nhận **giá trị trả về** ngoài args. Kết quả còn nhạy cảm hơn đầu vào: thông tin hồ sơ, access token mới cấp, dữ liệu tài chính hoặc nội dung tìm kiếm. Nếu mục tiêu chỉ là đếm thành công, lưu `outcome=SUCCESS` thay vì serialize response.

Đừng giả định kết quả trả về là “an toàn vì là output đã được duyệt”. Nó có thể thuộc một phạm vi phân quyền cao hơn log store.

### 8.3 After throwing advice

Ngoại lệ có thể chứa input trong message, suppressed exceptions, cause chain và stack trace. Một điểm ghi lỗi chung nên phân loại theo **mã lỗi đã duyệt**; chỉ truyền message và trace vào nơi có mức bảo vệ tương xứng khi có cơ sở cần thiết và chính sách rõ.

Nếu advice chỉ bắt lỗi để log, nó thường cần **bảo toàn ngoại lệ gốc**; đừng đổi đường thất bại thành thành công chỉ vì muốn giấu lỗi khỏi log.

### 8.4 Around advice

Around advice có thể đọc args, gọi tiếp, đổi tham số, đổi kết quả hoặc short-circuit nếu mô hình hỗ trợ. Nó cần tránh:

- Copy toàn bộ args và giữ trong closure/queue lâu hơn yêu cầu.
- Ghi raw args trước khi gọi tiếp và raw result sau khi trả về.
- Gọi `toString()` hoặc serializer toàn diện trong `finally`.
- Trả về nội dung đã bị mask **cho bên gọi** chỉ vì muốn mask log, làm sai hợp đồng nghiệp vụ.
- Gọi lại `proceed` nhiều lần gây lặp side effect chỉ để thu thập thêm dữ liệu.

**Che dữ liệu khi phát event quan sát** phải tách khỏi **dữ liệu nghiệp vụ được chuyển tiếp**. Không tự sửa token/địa chỉ mà nghiệp vụ cần dùng chỉ nhằm làm log an toàn.

## 9. Dữ liệu cấu trúc: mục tiêu tốt hơn nội dung tự do

Một event có cấu trúc hữu ích có thể gồm:

~~~text
event_name: order.place
component: checkout
outcome: VALIDATION_ERROR
channel: MOBILE
duration_bucket: UNDER_50_MS
trace_id: an approved random correlation identifier
~~~

Các giá trị trên là ví dụ của **event schema được thiết kế trước**, không phải dữ liệu thật của một người dùng. Dữ liệu cấu trúc có ích vì:

- Mỗi field có mô tả, loại, ranh giới và chính sách rõ.
- Có thể giới hạn các trường vào tập enum hoặc khoảng giá trị được duyệt.
- Dễ kiểm thử sự vắng mặt của field nhạy cảm.
- Dễ áp dụng thời hạn giữ và quyền truy cập riêng cho mỗi loại sự kiện.
- Giảm nhu cầu gắn payload tự do vào một chuỗi lớn khó kiểm soát.

Ngay cả trace ID hoặc mã đối tượng nghiệp vụ cũng có thể **giúp liên kết hoạt động của một cá nhân**. Chúng cần được xem xét theo mục đích và phạm vi; “không phải password” không đồng nghĩa “không nhạy cảm”.

### 9.1 Cardinality cao cũng là rủi ro

Đưa email, user ID, số tài khoản, request body hoặc token vào **metric labels** tạo ra rất nhiều chuỗi giá trị khác nhau, gây quá tải lưu trữ và làm lộ dữ liệu trong hệ thống metrics. Để phân tích tỷ lệ lỗi hoặc latency, thường dùng nhãn **bounded** như tên thao tác, trạng thái và nhóm nguyên nhân, không dùng định danh cá nhân làm label.

### 9.2 Chống log injection

Nếu thật sự phải ghi một chuỗi do bên ngoài cung cấp, cần chặn hoặc escape đúng theo format sink: xuống dòng, carriage return, dấu phân cách, control characters và cấu trúc có thể phá định dạng log. Ưu tiên sử dụng API structured logging có escaping đúng, giới hạn kích thước và schema đã duyệt.

Sanitization định dạng **không** thay phân loại độ nhạy. Một token đã escape an toàn khỏi log injection vẫn là một token bị lộ nếu ghi nguyên giá trị.

## 10. Giới hạn về vòng đời và nơi lưu

### 10.1 Ngay trong bộ nhớ advice

Đừng giữ tham chiếu toàn bộ `args`, `target`, `this` hoặc response vào một queue lâu dài khi chỉ cần ba trường an toàn. Nếu log bất đồng bộ, hãy tạo **sự kiện nhỏ, bất biến và đã lọc**, thay vì chuyển toàn bộ object rồi định dạng ở consumer.

Sao chép mảng bằng `args.clone()` là **sao chép nông**: các object bên trong vẫn được chia sẻ. Nếu nội dung mutable thay đổi, event ghi sau có thể phản ánh thời điểm khác và làm lộ dữ liệu không lường trước.

### 10.2 Khi truyền đi

Kênh log/trace/audit cần bảo vệ tính bí mật và toàn vẹn khi truyền, cấu hình endpoint đúng, dùng credentials theo nguyên tắc ít quyền. Đảm bảo các hệ thống thu thập thứ ba được phê duyệt trước khi gửi dữ liệu; một SDK telemetry không phải nơi mặc định được phép nhận mọi payload.

### 10.3 Khi lưu trữ và truy cập

- Phân quyền theo vai trò, hạn chế người có thể truy vấn log.
- Có retention và cơ chế xóa phù hợp chính sách và quy định áp dụng.
- Xem xét dữ liệu trong bản sao lưu, kho thứ cấp, export và môi trường thử nghiệm.
- Bảo vệ quyền xem, chia sẻ, tải xuống, tìm kiếm và đối chiếu sự kiện.
- Kiểm tra nhà cung cấp log/trace có phù hợp với phân loại dữ liệu thực tế.
- Ghi nhận và kiểm toán việc truy cập log nhạy cảm nếu cần.

Việc **mã hóa lúc lưu** là biện pháp quan trọng, nhưng không cho phép thu thập tùy ý. Dữ liệu nhạy cảm không cần thiết vẫn nên bị loại **trước** khi đến kho lưu trữ.

## 11. Kiểm thử để chứng minh không rò qua advice

Một bài kiểm thử tốt phải dùng **giá trị canary giả** có thể tìm kiếm dễ dàng, ví dụ chuỗi như `CANARY_DO_NOT_RECORD_123` đặt vào tham số được phân loại sensitive. Sau đó chạy đủ các đường và kiểm tra không có chuỗi canary trong bất kỳ event/trace/metric được hệ thống thử nghiệm thu thập.

Nên kiểm tra:

1. **Success:** lời gọi thành công không in token, thông tin cá nhân, request body hoặc response nhạy cảm.
2. **Validation failure:** input sai không được đưa nguyên vào exception log.
3. **Backend failure:** lỗi từ thư viện/thân phương thức không làm advice vô tình in thông báo chứa dữ liệu nhạy cảm.
4. **Tham số mới:** nếu thêm field chưa được phân loại, mặc định bị bỏ.
5. **Object lồng nhau:** `CustomerRequest` chứa secret không bị serialize nguyên object.
6. **`null`, array, collection, varargs, generic:** cách duyệt không làm mất an toàn khi loại dữ liệu thay đổi.
7. **Overload:** các chữ ký cùng tên không dùng nhầm metadata annotation hoặc chỉ số tham số.
8. **Interface/override/proxy:** code đọc annotation tại `Method` đúng như thiết kế; phương thức triển khai không tự được giả định kế thừa parameter annotation.
9. **After returning / throwing / finally:** không lộ kết quả và message lỗi khi advice ở nhiều giai đoạn.
10. **Multiple advice:** lớp ngoài và trong không tạo thêm bản sao raw args do một bên lọc còn bên kia log toàn bộ.
11. **Dynamic sampling / debug level:** bật mức chi tiết cao vẫn không được xuất secret.
12. **Async logging:** queue/event cuối cùng đã chứa dữ liệu an toàn, không có object nhạy cảm được giữ chờ format.
13. **Metrics/tracing:** label/attribute và baggage không chứa payload.
14. **Sink:** cả console, file, collector, exporter, error reporter, backup được kiểm tra ở phạm vi có thể quan sát.
15. **Performance:** phân loại và tạo event đủ nhanh; advice không gây reentrancy, deadlock hoặc tăng contention quá mức.

**Kiểm tra bằng điều kiện phủ định:** `assert(!capturedOutput.contains(canary))` cho mọi đích quan sát được trong test. Đồng thời kiểm tra **những event được phép vẫn tồn tại** để tránh tình trạng “an toàn” bằng cách vô hiệu hóa toàn bộ audit bắt buộc.

Không đưa secret thật vào test. Canaries giả có cấu trúc nhận diện riêng giúp phát hiện rò dữ liệu mà không làm phát sinh một vụ rò thật.

## 12. Tình huống thiết kế: cùng một phương thức, ba nhu cầu khác nhau

Giả sử nghiệp vụ `registerAccount(email, password, consent)` và ba nhóm muốn dùng cùng join point.

**Nhóm vận hành** cần biết tỷ lệ thành công và latency. Event phù hợp có thể là `event=account.register`, `outcome=SUCCESS/ERROR`, `duration_ms`. Không cần email hay password.

**Nhóm bảo mật** cần phát hiện các lần đăng ký đáng ngờ. Nó có thể cần trạng thái xác thực, mã nguyên nhân và dấu hiệu gian lận đã chuẩn hóa; nếu cần định danh để điều tra, phải dùng phương pháp được phê duyệt và kho audit có kiểm soát, không tự phát tán email/password vào log ứng dụng.

**Nhóm sản phẩm** cần phân tích lựa chọn consent. Có thể cần các giá trị boolean/enum được duyệt và tổng hợp, nhưng cần xem mục đích thu thập, thông báo, quyền riêng tư và giới hạn lưu giữ. Không được suy rằng “đã có args nên dùng toàn bộ args cho analytics”.

**Kết luận tình huống:** một join point có thể phục vụ nhiều mục tiêu, nhưng **mỗi mục tiêu cần schema thu thập riêng**. Gộp các nhu cầu thành một “log everything” sẽ làm tăng rủi ro mà không nhất thiết cải thiện khả năng phân tích.

## 13. Các lỗi thường gặp và cách sửa

| Sai lầm | Hậu quả | Cách tiếp cận tốt hơn |
| --- | --- | --- |
| Log toàn bộ `args` | Rò token, PII, payload lồng nhau | Mặc định omit; allowlist các field tối thiểu |
| Log mọi `target.toString()` | Rò trạng thái object và thông tin nội bộ | Ghi tên thao tác/nhóm target đã được duyệt |
| Cho rằng String không nhạy cảm nếu tên là `message` | Tên trường không bảo đảm ý nghĩa | Phân loại theo hợp đồng nghiệp vụ |
| Dùng blacklist tên `password` duy nhất | Bỏ sót tên khác và trường nested | Default-deny và review schema |
| Dùng hash đơn giản của email/token | Có thể bị dò và liên kết lâu dài | Omit hoặc dùng cơ chế bảo vệ được phê duyệt |
| Mask bằng regex sau khi đã gửi log | Bản thô có thể đã tới nhiều sink | Biến đổi trước khi phát event |
| Ghi exception message trong catch | Message có thể chứa input nhạy cảm | Mã lỗi an toàn, kiểm tra riêng exception detail |
| In kết quả từ after advice | Response có thể chứa dữ liệu nhạy cảm | Log status hoặc số liệu đã tổng hợp |
| Tin rằng DEBUG tắt nên không có rủi ro | Có exporter/queue/sink khác | Áp dụng giới hạn ở nguồn thu thập |
| Bỏ audit bắt buộc để tránh log | Mất dấu vết an ninh | Schema audit tối thiểu có kiểm soát |
| Sử dụng mọi ID làm metrics label | Cardinality cao và rò định danh | Nhóm trạng thái/tên thao tác hữu hạn |
| Bắt lớp proxy rồi coi đã bao phủ mọi đường gọi | Direct/self-call có thể bỏ qua proxy | Xác minh phạm vi weaving và mọi điểm bắt buộc |
| Lưu args cho async formatter | Giữ tham chiếu nhạy cảm, dữ liệu đổi | Tạo event đã lọc và bất biến ngay tại advice |
| Dùng annotation mà không kiểm thử | Metadata có thể nằm ở chữ ký khác | Xác nhận nguồn Method và chính sách mặc định |

## 14. Checklist trước khi đưa một advice thu thập dữ liệu vào vận hành

- [ ] Xác định **mục đích hợp lệ** của từng trường trong event.
- [ ] Chọn đúng **join point/pointcut**, không dùng phạm vi rộng chỉ vì dễ viết.
- [ ] Biết `args`, `this`, `target`, `result`, `exception` thực chất là dữ liệu nào ở loại điểm đang xét.
- [ ] Có bảng phân loại và **default-deny** cho tham số chưa được duyệt.
- [ ] Không in secret/token/password và payload nhạy cảm, kể cả trong nhánh lỗi.
- [ ] Không gọi `toString()` hoặc serializer trên object tùy ý.
- [ ] Nếu dùng annotation, xác minh metadata đúng interface/method/parameter và inheritance/override.
- [ ] Chỉ phát event **nhỏ, có schema, đã lọc**; không chuyển raw args tới queue.
- [ ] Các field động được giới hạn kích thước, escape an toàn, không tạo metrics label cardinality vô hạn.
- [ ] Correlation ID hoặc định danh thay thế tuân thủ chính sách quyền riêng tư.
- [ ] Sinks và bên thứ ba được phân quyền, mã hóa và áp dụng retention.
- [ ] Kiểm thử canary qua success/failure/debug/async và nhiều advice cùng can thiệp.
- [ ] Chức năng audit bắt buộc vẫn hoạt động với **dữ liệu tối thiểu đã được duyệt**.
- [ ] Advice không làm biến đổi kết quả, ngoại lệ hoặc tác dụng phụ nghiệp vụ chỉ để thay đổi log.
- [ ] Đánh giá định kỳ khi thêm tham số hoặc mở rộng schema.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Một advice có thể truy cập `Object[] args`. Có nên mặc định ghi `Arrays.deepToString(args)` để dễ debug?

**Đáp:** không. Điều đó có thể serialize dữ liệu nhạy cảm hoặc object lồng nhau. Cần allowlist các trường đã phân loại an toàn, thường chỉ giữ tên thao tác và trạng thái.

**Câu 2.** Nếu thay số tài khoản bằng SHA-256 không có key rồi ghi vào log, dữ liệu có chắc đã vô danh?

**Đáp:** không. Dữ liệu có không gian giá trị hạn chế có thể bị dò từ điển; hash còn có thể làm liên kết nhiều sự kiện. Cần ưu tiên omission, hoặc cơ chế pseudonymization/hashing được phê duyệt với quản lý khóa và vòng đời phù hợp.

**Câu 3.** Nếu advice after returning nhận object response, có thể coi nó an toàn vì là dữ liệu do server tạo không?

**Đáp:** không. Response có thể chứa thông tin riêng tư, token hoặc bí mật thương mại. Chỉ ghi metadata/kết quả tổng quát được duyệt.

**Câu 4.** Nếu tên phương thức là `getCustomer`, ta nên log `customerId` để đo latency theo từng khách không?

**Đáp:** thông thường không dùng ID khách hàng làm label metrics; cardinality cao và làm lộ mối liên kết. Dùng tên thao tác, trạng thái và duration histogram; nếu phải có correlation trong kênh riêng, cần thiết kế có kiểm soát.

**Câu 5.** Một param có `@SafeToLog`; vậy có thể gọi `String.valueOf(value)` dù nó là object bất kỳ không?

**Đáp:** không. Annotation không thay thế xác minh loại và ý nghĩa giá trị. Ví dụ Java giới hạn việc xuất dữ liệu vào enum `Channel`; object tùy ý vẫn bị omit.

**Câu 6.** Nếu proxy advice đã không ghi token, có nghĩa không còn rủi ro rò token trong toàn ứng dụng không?

**Đáp:** không. HTTP logging, exception formatter, tracing exporter, ORM, profiler hoặc lời gọi không đi qua proxy vẫn có thể thu thập. Cần kiểm soát theo **toàn luồng dữ liệu**.

**Câu 7.** Một audit bắt buộc cần biết ai thực hiện thao tác. Có nên tắt toàn bộ audit để không lưu PII?

**Đáp:** không. Cần phân biệt nhật ký nghiệp vụ thường và audit được kiểm soát; audit nên dùng chỉ danh tính đã xác thực và các trường tối thiểu theo chính sách, có phân quyền và retention rõ.

## 16. Tài liệu tham khảo

- [OWASP Logging Cheat Sheet – Data to exclude](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html): phân loại những nhóm dữ liệu thường không nên ghi nguyên vào log, bảo vệ đường truyền/kho lưu và tránh log injection.
- [OWASP Secure Logging Benchmark](https://owasp.github.io/www-project-secure-logging-benchmark/): các rủi ro thực tế khi dữ liệu xác thực và dữ liệu cá nhân đi vào log.
- [Java SE 17 – Executable.getParameterAnnotations()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html#getParameterAnnotations()): đọc annotation của từng tham số theo thứ tự khai báo.
- [Java SE 17 – Dynamic Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): lớp proxy và cơ chế chuyển lời gọi qua InvocationHandler.

**Ghi nhớ:** Một advice nhận được tham số không có nghĩa nó được phép **thu thập toàn bộ tham số**. Hãy chọn đúng join point, hiểu ngữ cảnh, mặc định không lưu dữ liệu thô, chỉ tạo event tối thiểu đã phân loại và kiểm thử nhiều đường ghi để tránh rò rỉ. Quan sát tốt là quan sát **đủ để vận hành đúng**, không phải thu thập càng nhiều càng tốt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-data-009">Tách trích xuất ngữ cảnh khỏi xử lý nghiệp vụ của aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Tách trích xuất ngữ cảnh khỏi xử lý nghiệp vụ của aspect trong AOP

> **Mục tiêu:** Thiết kế advice theo hướng tách biệt **cách thu thập, chuẩn hóa và kiểm tra dữ liệu tại join point** với **quy tắc xử lý mà aspect thực hiện trên dữ liệu đó**. Người học cần biết ranh giới của join point context, cách tạo bản chụp dữ liệu an toàn, cách triển khai một tầng trích xuất độc lập có thể kiểm thử, cách advice phối hợp trước/sau/ngoại lệ và những giới hạn về weaving, hiệu năng, riêng tư, trạng thái đồng thời.

## 1. Vấn đề: một advice làm quá nhiều việc

Giả sử có một aspect quan sát hoạt động thanh toán. Tại mỗi lời gọi, advice cần xác định tên phương thức, đọc đối số, che dữ liệu tài khoản, kiểm tra có phải thao tác đáng theo dõi không, tạo mã sự kiện, ghi log và xử lý lỗi. Cách viết “mọi thứ trong một advice” dễ tạo đoạn mã kiểu sau:

~~~text
around selectedPaymentOperation:
    đọc chữ ký
    đọc đối số
    tìm tên tài khoản và số tiền
    chuyển đổi / che các giá trị nhạy cảm
    quyết định loại sự kiện cần ghi
    khởi tạo logger, formatter, metrics
    ghi lời gọi bắt đầu
    try:
        kết quả = proceed()
        đọc kết quả để quyết định trạng thái
        ghi lời gọi hoàn tất
        return kết quả
    catch error:
        đọc ngoại lệ
        ghi lỗi
        throw error
~~~

Đây là **pseudocode độc lập công cụ**, không phải cú pháp của một ngôn ngữ aspect cụ thể.

Đoạn mã trên trộn ít nhất ba trách nhiệm:

1. **Đọc và diễn giải join point:** nơi lấy chữ ký, target, đối số, metadata, kết quả và lỗi.
2. **Áp dụng quy tắc của mối quan tâm xuyên suốt:** sự kiện nào được ghi, ở mức nào, đếm như thế nào, xử lý loại lỗi nào.
3. **Điều phối luồng thực thi:** gọi tiếp hành vi gốc, bảo toàn return value/exception, chạy xử lý trước/sau đúng lúc.

Sự trộn lẫn gây khó kiểm thử, khó thay công cụ AOP và có thể vô tình biến việc đọc ngữ cảnh thành một thao tác nghiệp vụ nguy hiểm. Một thiết kế vững hơn tách những phần này thành các đơn vị với giao diện và trách nhiệm rõ ràng.

**Ý chính:** advice là **điểm phối hợp**; extractor là **bộ chuyển đổi ngữ cảnh**; policy của aspect là **bộ quyết định và xử lý mối quan tâm xuyên suốt**. Không nên để mọi policy phải biết trực tiếp đối tượng join point đặc thù của một cơ chế weaving.

## 2. Hiểu đúng “xử lý nghiệp vụ của aspect”

Trong bài này, cụm **“xử lý nghiệp vụ của aspect”** không có nghĩa copy logic thanh toán, đặt hàng hay tính phí từ ứng dụng vào aspect. Nó chỉ **quy tắc của mối quan tâm mà aspect đang đảm nhận**.

Ví dụ với aspect quan sát:

- Trích xuất ngữ cảnh: xác định `PaymentApi.pay(String,int)`, tên lớp đích, giá trị amount được phép dùng, loại kết thúc.
- Quy tắc aspect: với thao tác thanh toán, ghi một sự kiện `STARTED` và một sự kiện `FINISHED`; chỉ lưu tên ngoại lệ, không lưu message nhạy cảm.
- Nghiệp vụ gốc: quyết định việc thanh toán hợp lệ hay không, tạo kết quả, lưu trạng thái, gọi hệ thống bên ngoài.

Ba nhóm này khác nhau.

Một aspect kiểm toán không nên tự quyết định “thanh toán thành công” chỉ vì method trả về bình thường, nếu phương thức trả về một đối tượng nghiệp vụ biểu thị thất bại. Một aspect đo thời gian không nên mở/đóng tài nguyên nghiệp vụ mà nó không sở hữu.

**Mục đích tách lớp không phải chuyển nghiệp vụ gốc vào aspect, mà là tránh để logic của aspect gắn chặt với cách lấy dữ liệu từ một join point cụ thể.**

## 3. Cấu trúc khuyến nghị: adapter → extractor → context → policy

Một mô hình kiến trúc khái niệm:

~~~text
                      Join point được chọn
                               |
                               v
                    +----------------------+
                    | Advice / Adapter     |
                    | before/around/after  |
                    +----------------------+
                      |               ^
                      v               |
               +--------------+       | kết quả/ngoại lệ
               | Context      |       | của phần tiếp tục
               | Extractor    |       |
               +--------------+       |
                      |               |
                      v               |
               +--------------+       |
               | Context      |       |
               | Snapshot     |       |
               +--------------+       |
                      |               |
                      v               |
               +--------------+       |
               | Aspect       |       |
               | Policy       |       |
               +--------------+       |
                      |               |
                      v               |
               +--------------+       |
               | Event Sink / |       |
               | Observer     |       |
               +--------------+       |
                                      |
                     proceed / next --+
                                      |
                               Phần gốc
~~~

Sơ đồ chỉ minh họa **các trách nhiệm**; thứ tự gọi thực tế phụ thuộc loại advice và thời điểm lấy dữ liệu. Nếu policy cần biết kết quả/ngoại lệ, advice có thể phát sự kiện sau khi `proceed` trả về hoặc ném lỗi.

### 3.1. Advice/adapter làm gì?

- Nhận quyền điều khiển tại join point do pointcut chọn.
- Lấy dữ liệu được mô hình AOP cung cấp và chuyển cho extractor.
- Gọi policy ở mốc phù hợp: trước, sau thành công, sau lỗi hoặc finally.
- Với around advice, quyết định tiếp tục bằng `proceed` hoặc cơ chế tương đương **theo hợp đồng đã định**.
- Bảo toàn kết quả và ngoại lệ gốc nếu aspect chỉ có chức năng quan sát.
- Giữ phần phối hợp ngắn, không thực hiện parse/format phức tạp trong thân advice.

### 3.2. Context extractor làm gì?

- Nhận dữ liệu thô có cấu trúc từ join point.
- Chỉ lấy những trường được phép/được yêu cầu.
- Chuẩn hóa tên hoạt động, chữ ký, kiểu và giá trị cho mục tiêu quan sát.
- Che hoặc loại thông tin nhạy cảm.
- Tạo một **context snapshot** không phụ thuộc trực tiếp vào API của công cụ AOP.
- Có thể trả lỗi/thiếu dữ liệu theo hợp đồng rõ, không tự làm thay đổi nghiệp vụ gốc.

### 3.3. Aspect policy làm gì?

- Nhận context theo một schema ổn định.
- Áp dụng các quy tắc của mối quan tâm xuyên suốt: ghi loại sự kiện nào, tạo metric nào, phân loại thành công/thất bại kỹ thuật ra sao.
- Gửi sự kiện tới sink/logger/collector qua giao diện riêng.
- Không cần dùng `Method`, `Field`, `proceed` hoặc đối tượng join point cụ thể nếu policy không có nhu cầu đó.

### 3.4. Context snapshot là gì?

Một **bản chụp nhỏ** mô tả một lần quan sát: operation, loại điểm, lớp đích, các trường an toàn, correlation ID nếu có, timestamp nếu cần. Nó không nhất thiết là “bản sao toàn bộ trạng thái ứng dụng”.

Nên ưu tiên cấu trúc **bất biến** để dễ truyền giữa các bước và tránh một policy vô tình sửa dữ liệu mà policy khác đang sử dụng.

## 4. Điểm can thiệp có những dữ liệu gì?

Không phải join point nào cũng cung cấp các trường giống nhau.

| Nhóm dữ liệu | Ví dụ | Lưu ý khi tách extractor |
|---|---|---|
| **Loại join point** | Method call, method execution, constructor execution | Phụ thuộc join point model |
| **Signature** | Tên, declaring type, parameter types | Thường là thông tin cấu trúc |
| **Metadata khai báo** | Modifier, annotation nếu truy cập được | Nguồn interface/implementation có thể khác |
| **Đối tượng liên quan** | Target, receiver, `this` | Có thể khác nhau trong proxy và các mô hình AOP |
| **Đối số runtime** | Mã đơn hàng, số lượng, cờ ưu tiên | Thay đổi mỗi lần gọi, có thể nhạy cảm |
| **Kết quả** | Giá trị trả về khi hoàn tất bình thường | Chưa có trước khi phần gốc chạy |
| **Ngoại lệ** | Lỗi đi ra khỏi ranh giới quan sát | Lỗi nội bộ đã được bắt có thể không xuất hiện |
| **Source location** | File, dòng mã nguồn nếu cung cấp | Không chắc có hoặc ổn định |
| **Ngữ cảnh liên kết** | Correlation ID, thông tin theo tác vụ/luồng | Phải xác định nguồn và quy tắc truyền |

**Không được tự giả định extractor có thể đọc mọi trường ở mọi loại advice.** Advice chạy trước không biết trước kết quả. Method execution và method call có ranh giới khác; một công cụ chỉ dùng proxy interface không có mọi join point của constructor hay field access.

Một extractor tốt có **hợp đồng đầu vào rõ ràng**: những dữ liệu nào có, những dữ liệu nào có thể thiếu, và thiếu thì làm gì.

## 5. Phân biệt dữ liệu tĩnh và dữ liệu của mỗi lần gọi

Với phương thức Java:

~~~java
String pay(String accountId, int amount);
~~~

*Đây là khai báo minh họa, không phải chương trình độc lập.*

**Thông tin cấu trúc** thường có thể dùng lại:

~~~text
declaringType = PaymentApi
memberName    = pay
parameterType = [String, int]
returnType    = String
~~~

**Thông tin động** thay đổi giữa các lần:

~~~text
Lần 1: accountId="acct-1", amount=120
Lần 2: accountId="acct-2", amount=0
~~~

Không nên cache **giá trị đối số** theo chữ ký phương thức. Có thể cache **cách trình bày chữ ký**, khi khóa cache và vòng đời lớp phù hợp, nhưng một context snapshot của mỗi invocation nên được tạo theo dữ liệu của chính lần đó.

Một policy quan sát cần thông tin cấp hoạt động (operation name) có thể không cần lưu đối số. Giới hạn thông tin ở ngay tầng extractor làm giảm rủi ro rò rỉ và chi phí xử lý.

## 6. Thiết kế context DTO có hợp đồng rõ

Trong Java 17, **record** phù hợp với nhiều bản chụp dữ liệu nhỏ, chẳng hạn:

~~~java
record InvocationSnapshot(
        String operation,
        String targetType,
        java.util.List<String> safeArguments
) {
    InvocationSnapshot {
        safeArguments = java.util.List.copyOf(safeArguments);
    }
}
~~~

Đây là **mảnh mã Java minh họa**, có thể đặt bên trong một class/file Java phù hợp.

Ưu điểm:

- **Tên trường mang ý nghĩa cụ thể:** operation, targetType, safeArguments.
- **Chỉ lưu thông tin tối thiểu:** không lưu toàn bộ join point/proxy/target.
- **Bất biến theo bề mặt API:** record không cho thay các thành phần sau khi khởi tạo; `List.copyOf` tạo danh sách không cho sửa trực tiếp.
- **Không gắn với công cụ AOP:** policy có thể được unit test bằng cách tự tạo record.

**Cẩn thận về “deep immutability”:** record chỉ giữ reference bất biến. Nếu một trường tham chiếu tới đối tượng mutable, nội dung đối tượng đó vẫn có thể bị thay đổi. Vì vậy ưu tiên primitive, `String`, enum, những record bất biến khác và bản sao an toàn của collection.

Không nên đặt trực tiếp một `Object[] args` mutable vào DTO chỉ vì nó sẵn có. Array có thể bị sửa; các object bên trong còn có thể mang dữ liệu nhạy cảm hoặc trạng thái biến đổi sau khi advice kết thúc.

## 7. Extractor nên nhận gì và trả gì?

Có hai hướng thiết kế thường thấy.

### 7.1. Extractor nhận đối tượng join point thô

~~~text
extract(RawJoinPoint) -> InvocationSnapshot
~~~

Ưu điểm: adapter ít dòng. Nhược điểm: extractor bị gắn với API AOP cụ thể, khó kiểm thử độc lập hoặc chuyển sang cơ chế weaving khác.

### 7.2. Adapter chuyển dữ liệu sang mô hình đầu vào trung lập

~~~text
Advice đọc:
    kind, memberDescriptor, target, arguments

ContextExtractor.extract:
    (kind, memberDescriptor, targetInfo, arguments)
        -> InvocationSnapshot
~~~

Ưu điểm: extractor ít phụ thuộc cơ chế AOP; kiểm thử có thể tự tạo dữ liệu đầu vào. Nhược điểm: adapter phải ánh xạ dữ liệu chính xác, không được làm mất khác biệt quan trọng giữa method call và method execution.

Không có một lựa chọn tốt cho mọi trường hợp. Một cách cân bằng:

- **Adapter** chịu trách nhiệm phụ thuộc vào công cụ và thu thập các trường đã được cung cấp.
- **Extractor** chịu trách nhiệm **biến đổi/kiểm tra/chọn dữ liệu** theo hợp đồng.
- **Policy** chỉ biết mô hình trung lập và quy tắc của aspect.

Nếu extractor cần khả năng đặc thù như metadata annotation, có thể đưa thông tin ấy qua một descriptor trung lập thay vì truyền toàn bộ đối tượng runtime đặc thù vào policy.

## 8. Interface của các trách nhiệm nên được phân định thế nào?

Pseudocode độc lập công cụ:

~~~text
interface ContextExtractor:
    extract(rawMetadata, runtimeArguments) -> ContextSnapshot

interface AspectPolicy:
    before(snapshot)
    completed(snapshot, completion)

interface EventSink:
    emit(event)

around selectedJoinPoint:
    snapshot = extractor.extract(joinPoint.metadata, joinPoint.arguments)
    policy.before(snapshot)
    try:
        result = proceed()
        policy.completed(snapshot, NormalCompletion)
        return result
    catch error:
        policy.completed(snapshot, ExceptionalCompletion(typeOf(error)))
        throw error
~~~

Phần trên minh họa trách nhiệm. **Không nên triển khai nguyên xi** nếu cơ chế AOP có cách xử lý lỗi, outcome, invocation hoặc return/throw khác; cần tuân thủ hợp đồng của công cụ.

Một extractor **không nên tự gọi `proceed`**. Nếu có `proceed` bên trong extractor, tầng chuyển đổi dữ liệu đã nắm quyền điều khiển nghiệp vụ; việc test hay dùng lại trở nên khó và có thể làm phần gốc chạy nhầm số lần.

Một policy quan sát thông thường cũng **không nên gọi trực tiếp phương thức đích bằng reflection**. Những thao tác đó thuộc vòng đời tiếp tục của adapter/around advice, không phải trách nhiệm quyết định nội dung log.

## 9. Ví dụ Java 17 đầy đủ: advice adapter, extractor và policy độc lập

Để học cách tách ba trách nhiệm, ví dụ dưới đây dùng **Java Dynamic Proxy** như một **ranh giới can thiệp minh họa**. Nó không tự weaving mọi join point và không đại diện cho đầy đủ ngữ nghĩa của một công cụ AOP bất kỳ.

Thiết kế:

- `InvocationSnapshot` là DTO bất biến chỉ chứa thông tin an toàn.
- `ContextExtractor` biến `Method`, target và đối số thành snapshot.
- `AuditPolicy` chỉ nhận snapshot và kết quả hoàn tất, không biết reflection/proxy.
- `AdviceBridge` là adapter phối hợp, chuyển lời gọi tới nghiệp vụ và bảo toàn kết quả/ngoại lệ gốc trong các đường chạy minh họa.
- `PaymentService` là nghiệp vụ thực tế; nó không biết aspect.

Lưu tệp **`SeparatedAdviceDemo.java`**:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class SeparatedAdviceDemo {
    interface PaymentApi {
        String pay(String accountId, int amount);
        String status();
    }

    static final class PaymentService implements PaymentApi {
        @Override
        public String pay(String accountId, int amount) {
            System.out.println("[business] pay amount=" + amount);
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return "ok-" + amount;
        }

        @Override
        public String status() {
            System.out.println("[business] status");
            return "READY";
        }
    }

    record InvocationSnapshot(
            String operation,
            String targetType,
            List<String> safeArguments
    ) {
        InvocationSnapshot {
            safeArguments = List.copyOf(safeArguments);
        }
    }

    enum Completion {
        RETURNED,
        THREW
    }

    static final class ContextExtractor {
        InvocationSnapshot extract(
                Method method, Object target, Object[] arguments) {
            List<String> safe = new ArrayList<>();

            // Chỉ công khai trường được phép trong hợp đồng.
            if (method.getName().equals("pay")
                    && method.getParameterCount() == 2
                    && arguments != null && arguments.length == 2) {
                safe.add("account=***");
                safe.add("amount=" + arguments[1]);
            } else if (arguments != null) {
                for (int i = 0; i < arguments.length; i++) {
                    safe.add("arg" + i + "=<hidden>");
                }
            }

            return new InvocationSnapshot(
                    method.getDeclaringClass().getSimpleName()
                            + "." + method.getName(),
                    target.getClass().getSimpleName(),
                    safe
            );
        }
    }

    static final class AuditPolicy {
        void started(InvocationSnapshot context) {
            System.out.println("[aspect] enter "
                    + context.operation()
                    + " target=" + context.targetType()
                    + " args=" + context.safeArguments());
        }

        void finished(
                InvocationSnapshot context,
                Completion completion,
                String detail) {
            System.out.println("[aspect] exit "
                    + context.operation()
                    + " outcome=" + completion
                    + " detail=" + detail);
        }
    }

    static final class AdviceBridge implements InvocationHandler {
        private final Object target;
        private final ContextExtractor extractor;
        private final AuditPolicy policy;

        AdviceBridge(
                Object target,
                ContextExtractor extractor,
                AuditPolicy policy) {
            this.target = target;
            this.extractor = extractor;
            this.policy = policy;
        }

        @Override
        public Object invoke(
                Object proxy, Method method, Object[] arguments)
                throws Throwable {
            InvocationSnapshot context =
                    extractor.extract(method, target, arguments);
            policy.started(context);

            try {
                Object result = method.invoke(target, arguments);
                policy.finished(
                        context, Completion.RETURNED, "returned");
                return result;
            } catch (InvocationTargetException wrapped) {
                Throwable cause = wrapped.getCause();
                policy.finished(
                        context, Completion.THREW,
                        cause.getClass().getSimpleName());
                throw cause;
            }
        }
    }

    public static void main(String[] args) {
        PaymentApi target = new PaymentService();
        ContextExtractor extractor = new ContextExtractor();
        AuditPolicy policy = new AuditPolicy();

        PaymentApi observed = (PaymentApi) Proxy.newProxyInstance(
                PaymentApi.class.getClassLoader(),
                new Class<?>[] {PaymentApi.class},
                new AdviceBridge(target, extractor, policy)
        );

        System.out.println("[caller] " + observed.pay("acct-123", 120));

        try {
            observed.pay("acct-999", 0);
        } catch (IllegalArgumentException error) {
            System.out.println("[caller] caught " + error.getMessage());
        }

        System.out.println("[caller] " + observed.status());
    }
}
~~~

Biên dịch và chạy với **JDK 17 trở lên**:

~~~bash
javac SeparatedAdviceDemo.java
java SeparatedAdviceDemo
~~~

**Stdout chính xác:**

~~~text
[aspect] enter PaymentApi.pay target=PaymentService args=[account=***, amount=120]
[business] pay amount=120
[aspect] exit PaymentApi.pay outcome=RETURNED detail=returned
[caller] ok-120
[aspect] enter PaymentApi.pay target=PaymentService args=[account=***, amount=0]
[business] pay amount=0
[aspect] exit PaymentApi.pay outcome=THREW detail=IllegalArgumentException
[caller] caught amount must be positive
[aspect] enter PaymentApi.status target=PaymentService args=[]
[business] status
[aspect] exit PaymentApi.status outcome=RETURNED detail=returned
[caller] READY
~~~

### 9.1. Nghiệp vụ và aspect không phụ thuộc lẫn nhau

`PaymentService` thực hiện kiểm tra `amount` và trả chuỗi kết quả. Nó **không** gọi extractor, không ghi sự kiện audit và không biết proxy.

`AuditPolicy` nhận `InvocationSnapshot` cùng loại hoàn tất và một chi tiết an toàn. Nó **không** biết `Method.invoke`, `InvocationTargetException`, tên account thật hoặc tham chiếu đối tượng đích.

Đây là tác dụng cốt lõi của việc tách lớp: thay đổi cách thu thập metadata **không buộc viết lại chính sách audit**, và thay đổi định dạng audit **không buộc thay cách gọi nghiệp vụ**.

### 9.2. Vì sao snapshot che tài khoản?

Ở `pay`, extractor cố ý biến mọi account ID thành `"account=***"`. Policy không bao giờ nhận giá trị nguyên bản nên khó vô tình ghi nó ra log. Trường amount được chọn để minh họa dữ liệu không bị che theo **quy ước riêng của ví dụ**.

Trong ứng dụng thật, ngay cả số tiền cũng có thể là thông tin nhạy cảm. Danh sách trường được phép ghi và cách masking phải xuất phát từ hợp đồng dữ liệu và yêu cầu riêng tư cụ thể.

### 9.3. Vì sao không dùng `arguments.toString()`?

`Object[]` không có cách `toString()` trực tiếp cho nội dung theo ý muốn, còn `Arrays.toString(arguments)` có thể in thông tin nhạy cảm hoặc gọi `toString()` của những đối tượng phức tạp. Extractor không nên serialize cả mảng đối số một cách tùy tiện.

Ở đây extractor dùng **allowlist minh họa**: chỉ với phương thức `pay` hai tham số thì lấy amount, mọi tham số khác sẽ được che. Cách nhận diện bằng tên/số tham số phù hợp với demo nhỏ, nhưng trong hệ thật nên xét **chữ ký, metadata hoặc descriptor chính xác** để tránh nhầm những overload trùng tên.

### 9.4. Vì sao result không được lưu trong context đầu vào?

Snapshot được tạo **trước khi gọi phần gốc** nên chưa có return value. Khi phần gốc hoàn tất, `AdviceBridge` tạo dữ liệu hoàn tất `Completion.RETURNED` hoặc `Completion.THREW` để đưa cho policy.

Giữ **bản chụp đầu vào** và **sự kiện hoàn tất** riêng giúp tránh ghi một trường `result` vào context lúc chưa biết giá trị, hoặc làm một DTO mutable đổi trạng thái từ “chưa hoàn tất” sang “đã hoàn tất”.

### 9.5. Vì sao cần gỡ `InvocationTargetException`?

Khi gọi `method.invoke(target, arguments)`, lỗi do phần gốc ném có thể được bọc bởi `InvocationTargetException`. Adapter giải lớp bọc để policy phân loại **loại lỗi gốc** rồi ném lại chính nguyên nhân.

Đây là một đặc điểm của **reflection trong ví dụ**, không phải cú pháp bắt buộc của mọi around advice. Những cơ chế weaving khác có thể cho return/throw đi qua continuation trực tiếp.

### 9.6. Phạm vi ví dụ không bao phủ mọi lỗi của adapter

Ví dụ minh họa xử lý đường **method trả bình thường** và **method đích ném qua `InvocationTargetException`**. Nếu **extractor, policy hoặc chính hạ tầng reflection** ném lỗi khác, mã adapter có thể chưa ghi sự kiện exit và lỗi của chính aspect có thể ảnh hưởng caller.

Điều này **không được xem là thiết kế production hoàn chỉnh**. Phần sau phân tích chiến lược lỗi và cách kiểm thử trường hợp hạ tầng/aspect gặp sự cố. Quan trọng là không che đi giới hạn của một chương trình chỉ nhằm học kiến trúc tách lớp.

## 10. Tiêu chí của một ContextExtractor tốt

Một extractor chuyên trách nên có những phẩm chất sau.

### 10.1. Có đầu vào và đầu ra rõ ràng

Giao diện khai báo rõ đọc dữ liệu nào và trả kiểu gì; không âm thầm truy cập một biến toàn cục hoặc log thẳng.

### 10.2. Ít tác dụng phụ

Lý tưởng, extractor là một phép biến đổi dữ liệu: cùng dữ liệu đầu vào hợp lệ tạo cùng snapshot (bỏ qua metadata động được khai báo là một phần input). Không thực hiện I/O mạng, ghi database hay gọi lại nghiệp vụ.

### 10.3. Không điều khiển proceed

Chỉ advice/adapter phối hợp `proceed` hoặc cơ chế tương đương. Extractor không tự chạy phần nghiệp vụ, không retry, không short-circuit.

### 10.4. Không làm lộ dữ liệu không cần thiết

Áp dụng allowlist và masking **trước khi** đưa dữ liệu đến policy hoặc sink. Hạn chế cả những dữ liệu trung gian bị giữ quá lâu.

### 10.5. Có cách xử lý thiếu dữ liệu

Nếu source location không có, giá trị target là null hoặc model không cung cấp tên tham số, extractor cần trả một dạng `unknown`/optional theo schema hoặc xử lý theo hợp đồng. Không tự đoán những trường chưa được quan sát.

### 10.6. Chi phí có thể dự đoán

Tránh truy cập dữ liệu lớn, gọi `toString()` tùy ý, lập bản sao sâu quá mức hoặc dùng reflection lặp lại đắt đỏ mà không có nhu cầu.

### 10.7. Có thể kiểm thử độc lập

Có thể gọi extractor bằng dữ liệu giả lập mà không cần kích hoạt AOP thực, sau đó so khớp snapshot. Đây là ưu điểm trực tiếp của việc tách trách nhiệm.

## 11. Kiểm thử extractor riêng không cần chạy cả aspect

Một extractor tốt nên được kiểm thử như một hàm chuyển đổi. Ví dụ các ca cho `ContextExtractor` của chương trình Java 17 ở trên:

| Đầu vào | Kỳ vọng |
|---|---|
| `PaymentApi.pay("acct-123",120)` | Operation `PaymentApi.pay`; target `PaymentService`; args chỉ `account=***`, `amount=120` |
| `PaymentApi.pay("acct-999",0)` | Tài khoản vẫn bị che; amount là `0` |
| `PaymentApi.status()` | Operation `PaymentApi.status`; args là danh sách trống |
| Một method khác có đối số | Mỗi đối số bị che bằng placeholder |
| Mảng đối số gốc bị thay đổi sau khi trích xuất | Snapshot không tự biến đổi vì chỉ giữ chuỗi an toàn và `List.copyOf` |
| Tên chữ ký không phù hợp chính sách allowlist | Không tự tiết lộ giá trị chưa được phép |

### 11.1. Một phép kiểm thử Java 17 tối giản

Giả sử các lớp nằm trong cùng chương trình `SeparatedAdviceDemo`, ta có thể bổ sung **một phương thức kiểm thử** dưới đây trong class đó (đây là **mảnh mã để gắn vào ví dụ**, không phải tệp chạy độc lập):

~~~java
static void verifyExtractor() throws Exception {
    var method = PaymentApi.class.getMethod(
            "pay", String.class, int.class);

    var extractor = new ContextExtractor();
    var context = extractor.extract(
            method, new PaymentService(),
            new Object[] {"private-account", 120});

    if (!context.operation().equals("PaymentApi.pay")) {
        throw new AssertionError("wrong operation");
    }
    if (!context.safeArguments().equals(
            List.of("account=***", "amount=120"))) {
        throw new AssertionError("unexpected safe arguments");
    }
    if (context.safeArguments().toString()
            .contains("private-account")) {
        throw new AssertionError("sensitive data leaked");
    }
}
~~~

Muốn chạy phép kiểm thử này trong ví dụ, gọi `verifyExtractor()` từ `main` hoặc từ một bộ kiểm thử Java tương thích. Vì phương thức không in gì nếu thành công, stdout minh họa của `main` **không đổi** khi gắn nó và gọi ở đầu `main`.

Điểm chính là **không cần tạo proxy** mới xác minh được việc che dữ liệu. Bộ kiểm thử AOP/integration vẫn cần ở tầng khác để xác minh extractor thực sự được gọi tại các join point như kỳ vọng.

## 12. Kiểm thử policy riêng bằng context giả

Tương tự, policy không nên bắt buộc có một đối tượng join point thật để chạy.

Pseudocode:

~~~text
snapshot = InvocationSnapshot(
    operation="PaymentApi.pay",
    targetType="PaymentService",
    safeArguments=["account=***", "amount=120"])

sink = FakeEventSink()

policy = AuditPolicy(sink)
policy.started(snapshot)
policy.finished(snapshot, RETURNED, "returned")

assert sink.events == [
    EnterEvent("PaymentApi.pay", ...),
    ExitEvent("PaymentApi.pay", RETURNED, ...)
]
~~~

Đây là mã **minh họa kiến trúc**, không phải cú pháp Java độc lập.

Việc dùng **fake sink** giúp test không ghi vào log thật hoặc gọi dịch vụ ngoài. Kiểm thử policy chỉ quan tâm quyết định phát sự kiện; nó không cần biết `Method`, `Proxy` hay cách engine AOP bind tham số.

Để làm được điều này trong chương trình production, tách `AuditPolicy` khỏi `System.out` bằng giao diện `EventSink` là một bước hữu ích. Demo dùng `System.out` trực tiếp chỉ để stdout dễ quan sát.

## 13. Phân biệt kiểm thử extractor, policy và advice adapter

Ba tầng nên có bộ kiểm thử riêng:

| Tầng | Cần kiểm thử | Không cần giả định |
|---|---|---|
| **Extractor unit test** | Mapping metadata, masking, chuẩn hóa, xử lý null/thiếu trường | Không cần weaving |
| **Policy unit test** | Quy tắc chọn loại sự kiện, phân loại completion, gọi sink | Không cần đối tượng join point của công cụ |
| **Adapter/integration test** | Pointcut khớp, adapter được gọi, proceed đúng một lần, return/throw bảo toàn | Không chỉ dựa vào test `extract()` |
| **System/observability test** | Sự kiện có tới sink đúng trong môi trường chạy, giới hạn riêng tư và số lần ghi | Không suy từ stdout demo |

**Unit test extractor PASS không chứng minh pointcut bao phủ tất cả đường gọi cần thiết.** Ngược lại, integration test nhìn thấy một dòng log cũng không chứng minh extractor không rò rỉ dữ liệu ở các overload và tình huống lỗi khác.

## 14. Sai lầm: để extractor phụ thuộc vào policy

Một thiết kế kém:

~~~text
extract(joinPoint):
    if methodName startsWith "pay":
        log("PAYMENT STARTED")
    updateMetrics()
    return rawArguments
~~~

Bộ phận mang tên extractor ở đây vừa **đọc** ngữ cảnh, vừa **thực thi chính sách**. Hệ quả:

- Không thể kiểm thử mapping mà không làm phát sinh log.
- Đổi quy tắc audit buộc sửa mã đọc join point.
- Dùng lại extractor cho metrics có thể tạo log không mong muốn.
- Một lần trích xuất nhiều lần có thể tạo nhiều sự kiện sai.
- Dữ liệu raw được trả ra policy, tăng nguy cơ rò rỉ.

Cách tách:

~~~text
snapshot = extractor.extract(joinPoint)
policy.recordStart(snapshot)
~~~

Extractor chỉ trả một snapshot an toàn; policy quyết định có phát sự kiện hay không. Nếu cần nhiều policy, tất cả có thể dùng cùng schema nhưng phải có hợp đồng rõ về thứ tự và quyền truy cập.

## 15. Sai lầm ngược: đẩy mọi quyết định ngữ cảnh vào advice

Một thiết kế khác cũng khó bảo trì:

~~~text
around selected:
    if joinPoint.kind == METHOD_EXECUTION:
        if joinPoint.signature.name == "pay":
            if joinPoint.args.length == 2:
                account = joinPoint.args[0]
                amount = joinPoint.args[1]
                ...
    policy.handle(account, amount, declaringClass, ...)
~~~

Lúc này advice trở thành một extractor khổng lồ. Khi thêm overload, method call, constructor hoặc dữ liệu ngữ cảnh mới, phần điều phối `proceed` có nguy cơ bị sửa sai trong lúc cập nhật mapping.

Giải pháp là đưa khối ánh xạ/chuẩn hóa dữ liệu vào **một hàm có tên và hợp đồng riêng**. Advice đọc dễ hơn:

~~~text
snapshot = extractor.extract(joinPointContext)
policy.before(snapshot)
return proceed()
~~~

Nếu policy cần kết quả/ngoại lệ, advice đưa sự kiện hoàn tất vào policy sau `proceed` mà không hòa trộn mã phân tích chữ ký với mã điều khiển luồng.

## 16. Vì sao không truyền trực tiếp RawJoinPoint xuống mọi tầng?

Một đối tượng join point thô có thể có quyền truy cập rộng:

- Danh sách đối số gốc chứa dữ liệu nhạy cảm.
- Tham chiếu target cho phép chạm trạng thái mutable.
- Dữ liệu của công cụ weaving/proxy gắn với vòng đời invocation hiện tại.
- Các API `proceed`, `getThis`, `getTarget`, `getArgs` hoặc tương đương có semantics phụ thuộc công cụ.

Nếu truyền trực tiếp nó qua nhiều tầng, các policy trở nên **phụ thuộc vào công cụ**, dễ đọc dữ liệu thừa hoặc gọi nhầm phần gốc. Nếu giữ join point sau khi invocation kết thúc, có thể vi phạm hợp đồng vòng đời của cơ chế.

Một context snapshot nhỏ là ranh giới **giảm quyền**: chỉ cung cấp những trường cần và không cho policy nắm quyền điều khiển lời gọi nghiệp vụ.

**Ngoại lệ có chủ đích:** một số aspect thực sự cần khả năng đặc thù của join point ở policy (ví dụ chính sách có phép biến đổi đối số). Khi đó phải định nghĩa một giao diện năng lực hẹp, tách riêng khỏi extractor quan sát, thay vì giả định mọi policy đều có quyền điều khiển `proceed`.

## 17. Chữ ký, metadata và đối tượng đích: mapping đúng nguồn

Với Java:

~~~java
interface PaymentApi {
    String pay(String account, int amount);
}

final class PaymentService implements PaymentApi {
    public String pay(String account, int amount) {
        return "ok";
    }
}
~~~

Đây là **mã khai báo minh họa**, không phải chương trình độc lập.

Một proxy của `PaymentApi` có thể cung cấp `Method` được khai báo trên interface. Vì thế:

- `method.getDeclaringClass()` có thể là `PaymentApi`.
- `target.getClass()` là `PaymentService`.

Đây là **hai thông tin đúng nhưng khác mục đích**. Extractor không nên đổi nhầm declaring type thành runtime target type hoặc ngược lại.

Tương tự, annotation có thể được khai báo trên interface hoặc implementation. Một phương thức reflection của interface **không tự mang mọi annotation của phương thức implementation**. Nếu policy dựa trên annotation, extractor/adapter phải xác định rõ nguồn metadata và quy tắc tra cứu của công cụ.

### 17.1. Generic, overload và biến thể chữ ký

Một method `submit(String)` khác `submit(String,int)`. Một extractor chọn chính sách chỉ bằng tên có thể nhầm overload. Với generic Java, các type argument có thể bị **type erasure** trong bytecode; không tự coi `List<String>` và `List<Integer>` là hai chữ ký runtime phân biệt.

Để an toàn, có thể dùng một **operation descriptor** đã được xác định rõ: loại join point, declaring type, method name, parameter types và các metadata cần thiết, thay vì phụ thuộc một tên ngắn.

## 18. Lỗi trích xuất ngữ cảnh phải được xử lý thế nào?

Có hai loại lỗi khác nhau:

1. **Lỗi nghiệp vụ gốc:** phần xử lý được quan sát ném ngoại lệ.
2. **Lỗi của aspect/extractor/policy/sink:** thao tác đọc metadata, masking, format hoặc ghi nhận gặp lỗi.

Nếu aspect **chỉ quan sát**, một yêu cầu thường gặp là không để lỗi telemetry làm thất bại thao tác nghiệp vụ vốn sẽ thành công. Nhưng nếu aspect thực thi **một bảo đảm bắt buộc** (ví dụ kiểm tra chính sách tại ranh giới được thiết kế), chọn “bỏ qua mọi lỗi và tiếp tục” có thể là sai về tính đúng đắn/an toàn.

Cần phân loại chính sách:

| Loại mối quan tâm | Hướng xử lý lỗi thường cần cân nhắc |
|---|---|
| Metrics best-effort | Hạn chế ảnh hưởng nghiệp vụ; ghi dấu mất metrics bằng kênh thích hợp |
| Log chẩn đoán best-effort | Có chiến lược fallback/gộp hoặc bỏ bản ghi an toàn |
| Audit bắt buộc | Xác định rõ hợp đồng fail-closed/fail-open, độ bền lưu trữ và cách thông báo lỗi |
| Kiểm soát an toàn | Không tự bỏ qua lỗi của kiểm tra bắt buộc |
| Dọn ngữ cảnh bắt buộc | Phải cân nhắc giữ lỗi chính và báo lỗi cleanup |

### 18.1. Nếu extractor lỗi trước proceed

Một around advice gọi extractor **trước** `proceed`. Nếu extractor ném lỗi, phần nghiệp vụ có thể **chưa chạy**. Đó có thể là lựa chọn có chủ đích đối với chính sách bắt buộc, nhưng là rủi ro không mong muốn với logging best-effort.

Có thể thiết kế wrapper an toàn quanh extractor để tạo snapshot tối thiểu hoặc báo tình trạng thiếu dữ liệu, **chỉ khi chính sách cho phép**. Không nên tự dùng chuỗi `"unknown"` cho mọi lỗi rồi ngầm giả vờ thông tin quan sát đầy đủ.

### 18.2. Nếu policy lỗi sau proceed

Nghiệp vụ có thể đã hoàn tất, nhưng policy/sink ghi kết quả tự ném lỗi. Caller có nguy cơ nhìn thấy lỗi từ aspect dù nghiệp vụ gốc thành công.

Nếu nghiệp vụ gốc đã ném lỗi và cleanup/policy cũng ném lỗi, lỗi aspect có thể **che nguyên nhân gốc**. Muốn bảo toàn lỗi gốc, cần hợp đồng riêng khi nhiều ngoại lệ đồng thời xuất hiện.

### 18.3. Đừng tự ném return/exception trong finally

Trong Java, `return` hoặc `throw` trong `finally` có thể thay thế kết quả hoặc ngoại lệ đang chờ. Mã dọn dẹp ở advice nên tránh hành vi này trừ khi đang triển khai một chính sách chuyển đổi có chủ đích và đã được kiểm thử.

## 19. Bản chụp khi vào và sự kiện khi thoát

Một mô hình tách lớp tốt thường có **hai thời điểm**:

~~~text
Vào join point:
    entry = extractor.extractEntry(rawContext)
    policy.onEntry(entry)

    result = proceed()

Thoát bình thường:
    completion = extractor.extractReturn(entry, result)
    policy.onCompletion(completion)

Thoát ngoại lệ:
    completion = extractor.extractFailure(entry, error)
    policy.onCompletion(completion)
    throw error
~~~

Đây là pseudocode, không phải cú pháp công cụ AOP.

Vì return value và exception chưa tồn tại lúc bắt đầu, hãy cân nhắc hai kiểu record:

~~~text
EntrySnapshot(operation, safeArguments, correlation)
CompletionSnapshot(entryId, completionKind, safeResultOrErrorType)
~~~

Điều này tránh tạo một object mutable có các trường lúc đầu null, sau đó thay đổi; những policy cần quan sát đồng thời cũng không phải lo có thread khác sửa snapshot sau khi nhận.

### 19.1. “Method return” không đồng nghĩa “nghiệp vụ thành công”

Một method có thể trả về bình thường một kết quả `DECLINED`. Completion kind `RETURNED` nói rằng không có ngoại lệ thoát khỏi ranh giới method. Trạng thái nghiệp vụ cần được diễn giải từ **hợp đồng kết quả** nếu chính sách thật sự cần nó, và chỉ khi được phép đọc.

### 19.2. “Method return” không đồng nghĩa tác vụ bất đồng bộ hoàn tất

Một method có thể trả một `CompletionStage` hoặc handle của công việc đang chạy. Nếu policy cần quan sát thời điểm tác vụ hoàn tất, phải có extractor/adapter của **sự kiện hoàn tất đó** hoặc cơ chế callback tương ứng. Advice quanh method trả về chỉ quan sát ranh giới method.

## 20. Lưu ý về đồng thời, lồng nhau và vòng đời context

### 20.1. Không dùng biến toàn cục làm snapshot của lần gọi

Nếu advice đặt `currentOperation` vào một biến static dùng chung, hai thread hoặc hai lời gọi lồng nhau có thể ghi đè. Thay vào đó, giữ snapshot trong **biến cục bộ của invocation** hoặc trong một cơ chế ngữ cảnh được thiết kế rõ cho từng lần gọi.

### 20.2. Context theo thread cần lưu-phục hồi

Một `ThreadLocal` dùng cho correlation có thể phù hợp với lời gọi đồng bộ, nhưng khi advice lồng nhau, giá trị mới cần **phục hồi giá trị cũ** thay vì xóa sạch. Nếu không, advice bên trong có thể làm mất context của advice bên ngoài.

### 20.3. Không giữ RawJoinPoint để xử lý về sau

Nếu cần gửi sự kiện bất đồng bộ tới queue, nên gửi **snapshot bất biến, tối thiểu**, không gửi đối tượng join point thô cùng tham chiếu target và argument array mutable. Context gốc có thể chỉ có ý nghĩa trong vòng đời invocation.

### 20.4. Bounded data

Một argument có thể chứa tập dữ liệu lớn. Snapshot nên giữ các trường cần thiết, kích thước giới hạn hoặc mã tham chiếu an toàn, thay vì sao chép toàn bộ object graph rồi đẩy qua nhiều queue.

## 21. Mở rộng cho nhiều loại join point

Nếu hệ AOP hỗ trợ **method call**, **method execution**, **constructor execution**, **field get/set**, các loại điểm đó có thông tin mô tả khác nhau. Không nên thiết kế extractor giả định luôn có `Method` và `Object[] args`.

Một cách mở rộng:

~~~text
sealed ContextDescriptor:
    MethodDescriptor(kind, declaringType, name, parameterTypes)
    ConstructorDescriptor(kind, declaringType, parameterTypes)
    FieldDescriptor(kind, declaringType, fieldName, fieldType)

Extractor:
    extract(descriptor, runtimeData) -> ContextSnapshot
~~~

Đây là pseudocode nêu kiến trúc. Một hệ thống Java 17 có thể biểu diễn hierarchy đóng bằng `sealed interface` và các record implement phù hợp.

**Không nhất thiết mọi loại join point đều cần cùng schema.** Ví dụ field set có giá trị gán, field get có giá trị đọc; constructor execution không có kiểu trả về như method. Có thể dùng các context type chuyên biệt để tránh nhiều trường null và những phép ép kiểu thiếu an toàn.

### 21.1. Method call và method execution không thể gộp mù quáng

Call site là nơi caller yêu cầu gọi; execution là nơi phần đích chạy. Nếu policy quan sát “được gọi từ module nào”, thông tin call site cần được thu ở một loại join point có ngữ nghĩa phù hợp. Nếu extractor chỉ có metadata của method execution, không thể tự suy chính xác caller từ method signature.

### 21.2. Khả năng đọc metadata khác khả năng can thiệp

Java Reflection đọc được `Method`, `Constructor`, `Field`. Điều đó **không chứng minh** một công cụ AOP hỗ trợ advice trên mọi method call, constructor hay field access. Cần xác minh join point model, phạm vi weaving và runtime path trước khi triển khai extractor của loại điểm đó.

## 22. Điều kiện pointcut thuộc tầng nào?

Pointcut quyết định **điểm nào** được chọn. Extractor quyết định **dữ liệu nào** được lấy ở một điểm đã vào adapter. Hai khái niệm khác nhau.

Ví dụ:

~~~text
Pointcut:
    chọn method execution thuộc nhóm thao tác thanh toán

Extractor:
    tạo snapshot gồm operation, targetType và amount an toàn

Policy:
    ghi enter / exit cho tất cả điểm phù hợp
~~~

Nếu một phần điều kiện phụ thuộc đối số lúc chạy, ngôn ngữ AOP có thể có **dynamic pointcut**. Không được tùy tiện chuyển toàn bộ logic chọn điểm sang extractor rồi coi như hiệu ứng tương đương: với cách đó, advice có thể đã được kích hoạt và tiêu tốn chi phí trước khi extractor quyết định bỏ qua.

Ngược lại, không nên nhét mọi quy tắc format và masking vào pointcut. Pointcut nên mô tả tập điểm, còn extractor nên cung cấp dữ liệu sạch để policy xử lý.

**Một ranh giới dễ bảo trì:** pointcut chọn *nơi* cần quan sát, extractor chọn *dữ liệu* cần mang theo, policy chọn *phản ứng* tương ứng.

## 23. Khi nào extractor cần biết chính sách?

Không phải mọi hệ thống đều muốn một extractor “không biết gì” về mục tiêu. Để chọn trường an toàn, extractor có thể nhận **cấu hình** như:

~~~text
ExtractionPolicy:
    allowedFields = ["operation", "targetType", "amount"]
    redactFields = ["accountId"]
    maxStringLength = 120
~~~

Điều đó khác với việc extractor tự quyết định có ghi audit hay gửi metrics không.

- **Cấu hình trích xuất** mô tả tập dữ liệu đầu ra hợp lệ và cách bảo vệ dữ liệu.
- **Aspect policy** mô tả hành động trên snapshot đã trích xuất.
- **Sink policy** mô tả nơi, định dạng và độ bền khi phát sự kiện.

Tách biệt trách nhiệm **không nhất thiết** đòi hỏi mỗi class không có cấu hình. Điều quan trọng là không tạo vòng phụ thuộc khiến thay đổi logging buộc thay đổi mã reflection ở mọi advice.

## 24. Hiệu năng: tách lớp không được biến thành sao chép vô hạn

Việc tạo một DTO cho mỗi join point có chi phí. Nhưng trộn mọi thứ trong advice cũng không miễn phí. Hãy đo các chi phí chính:

1. Truy xuất signature/annotation ở các điểm tần suất cao.
2. Sao chép hoặc định dạng đối số.
3. Chuẩn hóa, masking, serialize dữ liệu.
4. Tạo DTO và sự kiện.
5. Gửi sink đồng bộ hoặc bất đồng bộ.
6. Kiểm tra điều kiện động.
7. Logging trong đường nóng.

Các cách giảm chi phí có thể có:

- Pointcut đủ hẹp để không chặn những điểm không cần quan sát.
- Cache **metadata tĩnh** theo định danh phù hợp và vòng đời class loader.
- Không cache **đối số runtime** như dữ liệu tĩnh.
- Chỉ trích xuất trường policy thật sự cần.
- Hạn chế `toString()` trên object không rõ nguồn.
- Dùng schema/snapshot nhẹ, có kích thước giới hạn.
- Nếu dùng queue, thiết kế giới hạn dung lượng, backpressure và chính sách mất sự kiện rõ ràng.

Không được mặc định một implementation AOP sẽ tối ưu hóa các bước theo cùng cách. Việc đo phải thực hiện trong cơ chế, pointcut và workload thực tế.

## 25. Những lỗi thường gặp và cách sửa

### 25.1. “Extractor” vừa ghi log vừa gọi phần gốc

**Lỗi:** bộ phận chuyển dữ liệu có side effect và quyền điều khiển luồng.

**Cách sửa:** extractor trả snapshot; adapter chịu trách nhiệm proceed; policy/sink chịu trách nhiệm ghi sự kiện.

### 25.2. Policy nhận trực tiếp đối tượng join point của công cụ

**Lỗi:** policy phụ thuộc API AOP, khó unit test và có quyền đọc raw arguments không cần thiết.

**Cách sửa:** truyền context DTO trung lập; giữ phụ thuộc công cụ ở adapter.

### 25.3. Context giữ `Object[]` mà không snapshot

**Lỗi:** đối số là mảng mutable và có thể chứa object nhạy cảm; thay đổi sau khi event phát sẽ làm dữ liệu không nhất quán.

**Cách sửa:** sao chép dữ liệu được phép vào kiểu bất biến/đã chuẩn hóa.

### 25.4. Gọi `toString()` trên mọi đối số

**Lỗi:** lộ dữ liệu bí mật, tạo log lớn hoặc kích hoạt tác dụng phụ.

**Cách sửa:** allowlist, masking, giới hạn kích thước; không serialize object tùy tiện.

### 25.5. Phụ thuộc tên method mà bỏ chữ ký

**Lỗi:** overload cùng tên có hợp đồng đối số khác nhau.

**Cách sửa:** dùng descriptor có kiểu khai báo, tên và kiểu tham số; kiểm thử overload.

### 25.6. Nhầm kiểu khai báo với kiểu runtime

**Lỗi:** xem declaring type của method interface là class thực của target.

**Cách sửa:** tách `declaringType` và `targetType`, chỉ lấy cái cần thiết.

### 25.7. Bắt lỗi aspect rồi luôn cho phép thao tác

**Lỗi:** với chính sách bắt buộc, fail-open có thể vi phạm yêu cầu an toàn.

**Cách sửa:** định nghĩa chính sách lỗi theo mức độ bắt buộc của aspect, không theo sự tiện lợi của code.

### 25.8. Lỗi logging che ngoại lệ nghiệp vụ

**Lỗi:** advice ném ngoại lệ mới khi đang xử lý lỗi gốc.

**Cách sửa:** có chính sách lỗi quan sát và bảo toàn nguyên nhân gốc theo hợp đồng; kiểm thử lỗi kép.

### 25.9. Dùng `ThreadLocal` mà không phục hồi

**Lỗi:** context lồng nhau sai hoặc dữ liệu rò sang tác vụ khác trên thread pool.

**Cách sửa:** lưu giá trị cũ, restore trong `finally`, thiết kế truyền context cho async riêng.

### 25.10. Giữ context thô sau khi method kết thúc

**Lỗi:** vòng đời invocation có thể đã kết thúc; tham chiếu tới target hoặc args vẫn mutable.

**Cách sửa:** tạo snapshot tối thiểu trước khi gửi qua queue/callback.

### 25.11. Để policy quyết định thành công nghiệp vụ bằng exception đơn thuần

**Lỗi:** method có thể trả về trạng thái từ chối bình thường hoặc trả một handle tác vụ chưa hoàn tất.

**Cách sửa:** phân biệt completion kỹ thuật và outcome nghiệp vụ; lấy từ đúng hợp đồng và đúng điểm quan sát.

### 25.12. Coi một unit test extractor là đủ cho toàn hệ

**Lỗi:** phần trích xuất đúng nhưng pointcut không khớp hoặc lời gọi đi ngoài weaving.

**Cách sửa:** kết hợp test extractor, policy, adapter và runtime integration với đường gọi dương/âm.

## 26. Kịch bản nâng cao: nhiều policy dùng chung một snapshot

Một hệ thống có thể có nhiều mối quan tâm:

- Metrics chỉ cần operation và completion.
- Audit cần operation, correlation ID và một số trường được cho phép.
- Tracing cần operation, span context và mốc thời gian.
- Chẩn đoán cần thêm metadata khi mức debug được bật.

Một snapshot chung quá rộng dễ mang dữ liệu thừa tới tất cả policy. Có thể chọn:

1. **Một context cơ sở tối thiểu**, sau đó extractor chuyên biệt tạo các phần mở rộng theo quyền.
2. **Các DTO khác nhau theo mục đích**, ví dụ `MetricContext`, `AuditContext`.
3. **Một descriptor cấu trúc dùng chung**, cộng phần runtime trích xuất có điều kiện.

Đánh đổi: quá nhiều DTO gây phức tạp, một DTO quá rộng gây rủi ro. Chọn giải pháp dựa trên mức nhạy cảm và khả năng thay đổi của từng mối quan tâm.

**Không nên cấp toàn bộ raw arguments cho mọi policy chỉ vì nó làm API tiện hơn.**

## 27. Checklist thiết kế một aspect có tầng trích xuất riêng

1. Tôi đã xác định **loại join point** và vùng weaving thật sự được hỗ trợ chưa?
2. Pointcut chọn điểm nào, bao gồm/loại trừ các trường hợp nào?
3. Advice có chỉ làm nhiệm vụ **điều phối** và bảo toàn return/throw không?
4. Extractor có giao diện dữ liệu đầu vào/đầu ra rõ không?
5. Snapshot có phân biệt metadata cấu trúc với dữ liệu runtime không?
6. Snapshot có bất biến, kích thước giới hạn và không lưu tham chiếu thô không cần thiết không?
7. Có allowlist/redaction cho đối số hoặc giá trị kết quả không?
8. Policy có thể chạy unit test mà không cần engine AOP không?
9. Có fake sink để xác minh số lượng/loại sự kiện không?
10. Lỗi của extractor/policy/sink được xử lý theo chính sách bắt buộc hoặc best-effort rõ ràng chưa?
11. Thứ tự advice, lời gọi lồng nhau và đồng thời có được kiểm chứng?
12. Một lần method return có bị nhầm thành task async đã hoàn tất không?
13. Có thử cả ca **success**, **exception**, **pointcut không khớp** và **đường ngoài weaving** không?
14. Có xác minh không rò dữ liệu nhạy cảm trong log, exception message và những trường dự phòng?
15. Phần trích xuất có bị lặp ở nhiều advice không, và có thể chuẩn hóa thành một lớp chung đúng phạm vi không?

## 28. Bài tập tự kiểm tra

### Bài 1: Phân trách nhiệm

Một advice trực tiếp gọi `joinPoint.getArgs()`, format JSON, gửi thông điệp audit, quyết định kết quả `proceed` và tự làm retry. Hãy xác định đâu là ba trách nhiệm đang bị trộn.

**Đáp án:** đọc/chuyển đổi ngữ cảnh; chính sách audit và sink; điều phối luồng phần gốc bằng continuation. Retry lại là một chính sách điều khiển có hợp đồng riêng, không nên vô tình bị gộp vào extractor.

### Bài 2: Dữ liệu nào không nên đưa vào snapshot?

Tầng metrics chỉ cần loại thao tác và thời lượng. Có cần giữ toàn bộ `Object[] args` để “sau này tiện tra cứu” không?

**Đáp án:** không. Chỉ giữ các trường cần; lưu raw arguments làm tăng rủi ro quyền riêng tư, chi phí và tính mutable.

### Bài 3: Policy có cần `Method`?

Một policy chỉ quyết định in `STARTED` hoặc `FINISHED` theo operation. Có cần nhận `java.lang.reflect.Method` không?

**Đáp án:** thường không. Một `InvocationSnapshot` có operation đã chuẩn hóa và completion là đủ. `Method` nên thuộc adapter/extractor khi cần metadata.

### Bài 4: Lỗi của extractor

Một extractor logging best-effort ném lỗi trước khi advice gọi `proceed`. Điều gì có thể xảy ra?

**Đáp án:** phần gốc có thể không chạy, dù chính sách chỉ muốn quan sát. Cần thiết kế lỗi ở ranh giới aspect rõ ràng và kiểm thử tình huống đó.

### Bài 5: Return bình thường nhưng nghiệp vụ thất bại

Một method trả `PaymentResult.declined()` và không ném ngoại lệ. Snapshot completion nên ghi gì nếu chỉ quan sát ranh giới method?

**Đáp án:** `RETURNED` hoặc trạng thái tương đương. Việc thanh toán từ chối là **outcome nghiệp vụ**, không tự đồng nghĩa exceptional completion.

### Bài 6: Context với async

Advice đưa `RawJoinPoint` chứa target và args vào queue xử lý sau. Vấn đề có thể là gì?

**Đáp án:** object mutable, vòng đời join point, rò dữ liệu/giữ tham chiếu lớn; cần snapshot an toàn, nhỏ và đủ thông tin.

### Bài 7: Overload

Extractor che tài khoản cho phương thức tên `pay`, nhưng thêm overload `pay(Account, String)`. Dùng chỉ `method.getName().equals("pay")` có đủ không?

**Đáp án:** không. Phải kiểm tra descriptor/chữ ký thích hợp hoặc metadata hợp đồng để mapping đúng đối số theo từng overload.

### Bài 8: Kiểm thử ranh giới

Extractor và policy đều pass unit test, nhưng lời gọi `target.pay(...)` trực tiếp không có audit khi dùng proxy interface. Do lớp nào gây ra?

**Đáp án:** ranh giới runtime/weaving của adapter proxy. Unit test hai lớp kia không chứng minh mọi đường gọi đã đi qua proxy.

### Bài 9: Nhiều policy

Metrics cần operation, audit cần account đã che, tracing cần correlation. Nên phát cho cả ba raw arguments rồi để mỗi policy tự lọc không?

**Đáp án:** không nên mặc định. Tốt hơn dùng dữ liệu tối thiểu theo quyền, extractor chung hoặc chuyên biệt với schema rõ và masking trước khi truyền.

## 29. Ghi nhớ

- **Advice/adapter** chịu trách nhiệm đi qua ranh giới AOP và phối hợp continuation, return, exception.
- **Context extractor** chịu trách nhiệm chuyển dữ liệu join point thành **snapshot tối thiểu, an toàn, dễ kiểm thử**.
- **Aspect policy** chịu trách nhiệm quy tắc quan sát/kiểm toán/đo lường; không cần hiểu cơ chế phản chiếu hoặc weaving nếu không cần.
- **Event sink** là phụ thuộc riêng cho việc gửi/ghi sự kiện; policy có thể được test bằng fake sink.
- **Metadata cấu trúc** như chữ ký khác với **đối số/kết quả/ngoại lệ** của từng lần xuất hiện động.
- Không truyền dữ liệu thô rộng quyền hoặc mutable qua nhiều tầng khi chỉ cần một vài trường đã chuẩn hóa.
- Tách lớp không xóa bỏ trách nhiệm kiểm chứng **pointcut, join point model, weaving scope và runtime path**.
- Một aspect quan sát tốt phải có hợp đồng rõ về **lỗi của chính nó**, quyền riêng tư, đồng thời và hoàn tất bất đồng bộ.

Cách tự kiểm tra đơn giản: **“Nếu ngày mai thay cơ chế AOP nhưng giữ nguyên chính sách audit, tôi phải sửa phần đọc join point/adapter hay buộc viết lại toàn bộ chính sách?”** Nếu policy chỉ nhận một context rõ nghĩa, bất biến và trung lập, việc thay đổi sẽ được cô lập tốt hơn, đồng thời bộ kiểm thử trở nên đơn giản và đáng tin hơn.


</details>

- [Quay lại đầu trang](#back-to-top)
