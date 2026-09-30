# Reflection là gì và vì sao cần nó?

Reflection xuất hiện khi chương trình cần làm việc với **cấu trúc của mã như dữ liệu lúc chạy**. Nếu một đoạn mã đã biết chính xác kiểu nào, field nào và method nào cần dùng, Java cho phép gọi trực tiếp và trình biên dịch kiểm tra gần như mọi thứ từ trước. Nhưng một framework, serializer, test runner hoặc công cụ ánh xạ thường chỉ biết đối tượng thật sau khi chương trình đã chạy. Khi đó tên kiểu, field, method hoặc constructor có thể đến từ cấu hình, annotation hay chính đối tượng đang được xử lý.

Trong các ví dụ của module này, ta dùng một mô hình nhỏ xuyên suốt:

~~~java
public record PaymentRequest(String orderId, long amount) {}

public class PaymentService {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    public String pay(PaymentRequest request) {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
~~~

Mục tiêu của chương này là hiểu **Reflection là gì, vì sao nó tồn tại và khi nào bài toán thực sự cần khả năng khám phá cấu trúc lúc chạy** trước khi học các API chi tiết.

## <a id="reflection-model">Reflection là gì?</a>

Trong code thông thường, cấu trúc cần dùng đã nằm ngay trong câu lệnh:

~~~java
PaymentService service = new PaymentService("stripe");
String result = service.pay(new PaymentRequest("ORD-1", 100_000));
~~~

Trình biên dịch nhìn thấy `PaymentService`, constructor và `pay(...)`. Nếu tên method sai, kiểu tham số sai hoặc method không tồn tại, lỗi xuất hiện ngay khi biên dịch.

Trước khi đi tiếp, cần khóa ba từ sẽ xuất hiện xuyên suốt module:

- **siêu dữ liệu (metadata)** là dữ liệu mô tả cấu trúc của mã, ví dụ tên class, modifier, field, method, kiểu tham số hoặc annotation;
- **đối tượng mô tả phản chiếu** là đối tượng lúc chạy đại diện cho một phần cấu trúc đó, ví dụ `Field`, `Method` hoặc `Constructor<?>`;
- **đối tượng nhận (receiver)** là đối tượng cụ thể mà một instance field/method sẽ thao tác lên. Ví dụ khi gọi `method.invoke(service, ...)`, `service` chính là đối tượng nhận.

Reflection không thao tác trực tiếp với từng dòng mã nguồn. Nó thao tác với các đối tượng siêu dữ liệu/mô tả mà JVM cung cấp cho chương trình đang chạy.

Reflection cho phép dời một phần quyết định đó sang lúc chạy. Chương trình có thể lấy mô tả của một kiểu mà JVM đang biết, tìm thành phần theo siêu dữ liệu, rồi đọc field, gọi method hoặc tạo đối tượng thông qua đối tượng mô tả phản chiếu.

~~~java
Class<?> type = PaymentService.class;

Method method = type.getMethod("pay", PaymentRequest.class);
Object service = type.getConstructor(String.class)
        .newInstance("stripe");

Object result = method.invoke(
        service,
        new PaymentRequest("ORD-1", 100_000)
);
~~~

Điểm cốt lõi không phải là Reflection cung cấp một cách dài hơn để gọi method. Điểm cốt lõi là **cấu trúc cần thao tác có thể được phát hiện trong lúc chạy thay vì phải được ghi cứng trong mã nguồn gọi nó**.

Một bài toán điển hình là framework muốn tạo đối tượng từ cấu hình:

~~~text
payment.class = com.example.PaymentService
payment.provider = stripe
~~~

Nếu không dùng Reflection, framework phải biết trước `PaymentService` và viết nhánh riêng cho nó, hoặc yêu cầu ứng dụng đăng ký factory/adapter theo một hợp đồng đã định. Cách đó vẫn rất tốt khi ta kiểm soát tập kiểu. Nhưng một framework tổng quát không thể viết sẵn nhánh cho mọi class mà người dùng sẽ tạo trong tương lai. Reflection cho phép framework đặt câu hỏi ở lúc chạy:

~~~text
Đây là class nào?
    ↓
Có constructor/thành phần nào phù hợp?
    ↓
Siêu dữ liệu của chúng là gì?
    ↓
Có thể thao tác với chúng theo quy tắc của framework hay không?
~~~

Điểm khác biệt sẽ rõ hơn nếu tên class thật sự đến từ cấu hình thay vì xuất hiện dưới dạng `PaymentService.class` trong mã nguồn:

~~~java
String className = config.get("payment.class");

Class<?> type = Class.forName(className);
Constructor<?> constructor = type.getConstructor(String.class);
Object service = constructor.newInstance("stripe");

Method pay = type.getMethod("pay", PaymentRequest.class);
Object result = pay.invoke(
        service,
        new PaymentRequest("ORD-1", 100_000)
);
~~~

Đoạn framework này không cần tham chiếu tại thời điểm biên dịch tới phần triển khai thanh toán cụ thể trong biến `type`. Nó nhận **tên → kiểu lúc chạy → các đối tượng mô tả thành phần → thao tác**. Đây mới là bài toán Reflection được sinh ra để giải quyết. Nếu tên nhị phân không thể được phân giải, `Class.forName(...)` ném `ClassNotFoundException`; Reflection không tự tìm một class “gần đúng”.

Vì vậy Reflection là một **cơ chế siêu dữ liệu lúc chạy + truy cập/gọi động**. Nó không tự “hiểu” logic nghiệp vụ, không tự quét toàn bộ JVM và cũng không biến Java thành ngôn ngữ kiểu động. Mã gọi Reflection vẫn phải tự quyết định sẽ tìm gì, chấp nhận kiểu nào và xử lý lỗi ra sao.

### Truy cập trực tiếp và truy cập được khám phá lúc chạy

Hai cách tiếp cận giải cùng một thao tác nhưng có hợp đồng rất khác:

| Truy cập trực tiếp/tĩnh | Reflection |
| --- | --- |
| Kiểu/thành phần được ghi trực tiếp trong mã nguồn | Kiểu/thành phần có thể được tìm từ siêu dữ liệu lúc chạy |
| Trình biên dịch kiểm tra tên, kiểu tham số và khả năng gọi | Nhiều lỗi chuyển sang lúc chạy |
| IDE/công cụ tái cấu trúc nhìn thấy quan hệ rõ | Quan hệ có thể ẩn trong chuỗi, cấu hình hoặc siêu dữ liệu |
| Dễ đọc và tối ưu cho mã ứng dụng | Linh hoạt cho framework/công cụ tổng quát |

Khi cấu trúc đã biết tại thời điểm biên dịch, lời gọi trực tiếp thường là lựa chọn tốt hơn. Reflection có giá trị khi **việc không biết trước cấu trúc chính là một phần của bài toán**.

### Bản đồ thuật ngữ của mô-đun

Các chương sau sẽ dùng những khái niệm này:

| Thuật ngữ | Vai trò |
| --- | --- |
| Class<?> | Đối tượng mô tả một kiểu Java mà JVM biết tại lúc chạy |
| Field | Đối tượng mô tả một field |
| Method | Đối tượng mô tả một method |
| Constructor<?> | Đối tượng mô tả một constructor |
| Member | Hợp đồng siêu dữ liệu chung mà Field/Method/Constructor cùng tham gia |
| Executable | Khái niệm trừu tượng nền tảng chung của Method và Constructor |
| AnnotatedElement | Hợp đồng chung để đọc annotation lúc chạy trên các phần tử hỗ trợ annotation |
| get / set | Đọc hoặc ghi trạng thái thông qua Field |
| invoke | Gọi method thông qua Method |
| newInstance | Tạo đối tượng thông qua Constructor |
| AccessibleObject | Nền tảng chung của Field/Method/Constructor cho kiểm tra quyền truy cập phản chiếu |
| Type và các subtype | Siêu dữ liệu cho chữ ký generic còn giữ lại lúc chạy |
| Dynamic Proxy | Cơ chế tạo phần triển khai của interface lúc chạy |

Đừng nhìn `Class`, `Field`, `Method`, `Constructor`, `Type` như một danh sách API rời rạc. Mô hình đối tượng cốt lõi của Reflection có thể hình dung như sau:

~~~text
                         kiểu lúc chạy
                             │
                         Class<?> ───────────────┐
                         │                       │
                         │ khám phá               │ đồng thời triển khai Type
                         ↓                       │
                đối tượng mô tả thành phần       │
                         │                       │
                 Member (hợp đồng chung)         │
                  ┌──────┴───────────┐            │
                  │                  │            │
                Field           Executable        │
                  │             ┌────┴─────┐      │
                  │           Method   Constructor │
                  │             │          │       │
                  └──────┬──────┴──────────┘       │
                         ↓                          │
                 AccessibleObject                  │
             kiểm soát truy cập phản chiếu          │
                                                    │
siêu dữ liệu khai báo generic                       │
        ↓                                           │
       Type ◄───────────────────────────────────────┘
        ├─ Class<?>
        ├─ ParameterizedType
        ├─ TypeVariable
        ├─ WildcardType
        └─ GenericArrayType
~~~

Sơ đồ này không phải cây kế thừa Java tuyệt đối ở mọi nhánh; nó là **bản đồ vai trò** để người học biết đối tượng nào dẫn tới đối tượng nào. Về hệ phân cấp thực tế, `Field` trực tiếp kế thừa `AccessibleObject`; `Method` và `Constructor` kế thừa `Executable`, còn `Executable` kế thừa `AccessibleObject`. Cả ba loại thành phần đều thực hiện hợp đồng `Member`. `AccessibleObject` triển khai `AnnotatedElement`, vì vậy các đối tượng mô tả này cũng tham gia mô hình đọc annotation lúc chạy. `Class<?>` vừa là cửa vào để khám phá thành phần, vừa là một phần triển khai của `Type` khi siêu dữ liệu generic thật sự là một class thông thường ở lúc chạy.

Từ đó có thể đọc toàn bộ module bằng bốn câu hỏi lớn:

~~~text
Class<?>          → kiểu lúc chạy này là gì và có cấu trúc nào?
Field/Method/
Constructor       → thành phần cụ thể nào tồn tại và ta thao tác với nó ra sao?
AccessibleObject  → ta có quyền thực hiện thao tác phản chiếu đó không?
Type              → khai báo generic được biểu diễn thế nào trong siêu dữ liệu lúc chạy?
~~~

Lộ trình của module đi từ câu hỏi lớn tới cơ chế cụ thể:

~~~text
Reflection là gì và vì sao cần nó?
    ↓ hiểu bài toán khám phá cấu trúc lúc chạy
Mô hình kiểu lúc chạy với Class<?>
    ↓ biết JVM mô tả kiểu và siêu dữ liệu thế nào
Khám phá các thành phần của lớp
    ↓ Field / Method / Constructor và các đối tượng mô tả chung
Thao tác động bằng siêu dữ liệu
    ↓ đọc/ghi, gọi method, tạo đối tượng, xử lý đối số và mảng động
Quyền truy cập, đóng gói và giới hạn mô-đun
    ↓ hiểu kiểm tra truy cập và JPMS
Siêu dữ liệu Generics còn lại sau xóa kiểu
    ↓ đọc chữ ký generic còn tồn tại
Proxy động và chặn lời gọi
    ↓ nối siêu dữ liệu với cơ chế chặn lời gọi interface
Khi nào nên dùng Reflection?
    ↓ tổng hợp độ an toàn, hiệu năng, khả năng bảo trì và lựa chọn thiết kế
~~~

## <a id="reflection-use-cases">Khi nào Reflection thực sự hữu ích?</a>

Reflection mạnh nhất ở mã hạ tầng phải làm việc với **nhiều kiểu do người dùng hoặc phần mở rộng framework định nghĩa**.

Một dependency injection container có thể tìm constructor và tiêm dependency. Một serializer có thể kiểm tra field/thuộc tính để chuyển đối tượng thành JSON. ORM có thể đọc siêu dữ liệu của entity và ánh xạ dữ liệu. Test runner có thể phát hiện method có annotation test. Router/framework có thể đọc siêu dữ liệu rồi đăng ký handler. Các công cụ gỡ lỗi, kiểm tra hợp lệ hoặc ánh xạ đối tượng cũng có cùng mẫu: cấu trúc của đối tượng đích trở thành đầu vào của thuật toán.

Ví dụ một mapper rất nhỏ có thể nhận đối tượng mà không biết class cụ thể:

~~~java
static Map<String, Object> snapshot(Object target) throws IllegalAccessException {
    Map<String, Object> values = new LinkedHashMap<>();

    for (Field field : target.getClass().getDeclaredFields()) {
        if (Modifier.isPublic(field.getModifiers())) {
            values.put(field.getName(), field.get(target));
        }
    }
    return values;
}
~~~

Điều quan trọng là mapper viết **một thuật toán theo siêu dữ liệu**, thay vì một nhánh riêng cho `PaymentService`, `Customer`, `Order`, ...

Reflection không phải lựa chọn duy nhất để mở rộng hệ thống. Interface, factory, registry, `ServiceLoader`, mã được sinh tự động hoặc annotation processing có thể tạo hợp đồng rõ và an toàn hơn. Reflection đáng dùng khi việc khám phá lúc chạy thật sự đem lại giá trị, đặc biệt khi framework không thể yêu cầu mọi mô hình dữ liệu viết adapter bằng tay.
