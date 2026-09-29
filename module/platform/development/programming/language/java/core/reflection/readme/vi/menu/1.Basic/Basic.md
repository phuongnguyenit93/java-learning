# Reflection cơ bản

Reflection xuất hiện khi chương trình cần làm việc với **cấu trúc của code như dữ liệu ở runtime**. Nếu một đoạn code đã biết chính xác kiểu nào, field nào và method nào cần dùng, Java cho phép gọi trực tiếp và compiler kiểm tra gần như mọi thứ từ trước. Nhưng một framework, serializer, test runner hoặc công cụ ánh xạ thường chỉ biết đối tượng thật sau khi chương trình đã chạy. Khi đó tên kiểu, field, method hoặc constructor có thể đến từ cấu hình, annotation hay chính object đang được xử lý.

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

Mục tiêu của chương này là hiểu **Reflection giải quyết vấn đề gì và đổi lại ta mất gì** trước khi học các API chi tiết.

## <a id="reflection-model">Reflection là gì?</a>

Trong code thông thường, cấu trúc cần dùng đã nằm ngay trong câu lệnh:

~~~java
PaymentService service = new PaymentService("stripe");
String result = service.pay(new PaymentRequest("ORD-1", 100_000));
~~~

Compiler nhìn thấy PaymentService, constructor và pay(...). Nếu tên method sai, kiểu tham số sai hoặc method không tồn tại, lỗi xuất hiện khi compile.

Trước khi đi tiếp, cần khóa ba từ sẽ xuất hiện xuyên suốt module:

- **metadata** là dữ liệu mô tả cấu trúc của code, ví dụ tên class, modifier, field, method, parameter type hoặc annotation;
- **descriptor phản chiếu** là object runtime đại diện cho một phần cấu trúc đó, ví dụ `Field`, `Method` hoặc `Constructor<?>`;
- **receiver** là object cụ thể mà một instance field/method sẽ thao tác lên. Ví dụ khi gọi `method.invoke(service, ...)`, `service` chính là receiver.

Reflection không thao tác trực tiếp với dòng source code. Nó thao tác với các object metadata/descriptors mà JVM cung cấp cho chương trình đang chạy.

Reflection cho phép chuyển một phần thông tin đó sang runtime. Chương trình có thể lấy mô tả của một kiểu đang được JVM biết tới, tìm member theo metadata, rồi đọc field, gọi method hoặc tạo object thông qua descriptor phản chiếu.

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

Một bài toán điển hình là framework muốn tạo object từ cấu hình:

~~~text
payment.class = com.example.PaymentService
payment.provider = stripe
~~~

Nếu không dùng Reflection, framework phải biết trước PaymentService và viết nhánh riêng cho nó, hoặc yêu cầu ứng dụng đăng ký factory/adapter theo một contract đã định. Cách đó vẫn rất tốt khi ta kiểm soát tập kiểu. Nhưng một framework tổng quát không thể viết sẵn nhánh cho mọi class mà người dùng sẽ tạo trong tương lai. Reflection cho phép framework hỏi runtime:

~~~text
Đây là class nào?
    ↓
Có constructor/member nào phù hợp?
    ↓
Metadata của chúng là gì?
    ↓
Có thể thao tác với chúng theo rule của framework hay không?
~~~

Điểm khác biệt sẽ rõ hơn nếu class name thật sự đến từ cấu hình thay vì xuất hiện dưới dạng `PaymentService.class` trong source:

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

Đoạn framework này không cần compile-time reference tới concrete payment implementation trong biến `type`. Nó nhận **tên → runtime type → member descriptors → thao tác**. Đây mới là bài toán Reflection được sinh ra để giải quyết. Nếu binary name không resolve được, `Class.forName(...)` ném `ClassNotFoundException`; Reflection không tự tìm một class “gần đúng”.

Vì vậy Reflection là một **cơ chế runtime metadata + dynamic access/invocation**. Nó không tự “hiểu” business logic, không tự quét toàn bộ JVM và cũng không biến Java thành ngôn ngữ dynamic. Code gọi Reflection vẫn phải tự quyết định sẽ tìm gì, chấp nhận kiểu nào và xử lý lỗi ra sao.

### Direct access và runtime-discovered access

Hai cách tiếp cận giải cùng một thao tác nhưng có contract rất khác:

| Direct/static access | Reflection |
| --- | --- |
| Kiểu/member được ghi trực tiếp trong source | Kiểu/member có thể được tìm từ metadata lúc chạy |
| Compiler kiểm tra tên, kiểu tham số và khả năng gọi | Nhiều lỗi chuyển sang runtime |
| IDE/refactor tool nhìn thấy quan hệ rõ | Quan hệ có thể ẩn trong string/config/metadata |
| Dễ đọc và tối ưu cho code ứng dụng | Linh hoạt cho framework/tooling tổng quát |

Khi cấu trúc đã biết ở compile time, direct call thường là lựa chọn tốt hơn. Reflection có giá trị khi **việc không biết trước cấu trúc chính là một phần của bài toán**.

### Bản đồ thuật ngữ của module

Các chương sau sẽ dùng những khái niệm này:

| Thuật ngữ | Vai trò |
| --- | --- |
| Class<?> | Mô tả runtime của một kiểu đã được JVM load |
| Field | Descriptor cho một field |
| Method | Descriptor cho một method |
| Constructor<?> | Descriptor cho một constructor |
| Member | Contract metadata chung mà Field/Method/Constructor cùng tham gia |
| Executable | Base abstraction chung của Method và Constructor |
| get / set | Đọc hoặc ghi trạng thái thông qua Field |
| invoke | Gọi method thông qua Method |
| newInstance | Tạo object thông qua Constructor |
| AccessibleObject | Nền tảng chung của Field/Method/Constructor cho kiểm tra reflective access |
| Type và các subtype | Metadata cho generic signature còn giữ lại ở runtime |
| Dynamic Proxy | Cơ chế tạo phần triển khai của interface tại runtime |

Đừng nhìn `Class`, `Field`, `Method`, `Constructor`, `Type` như một danh sách API rời rạc. Object model cốt lõi của Reflection có thể hình dung như sau:

~~~text
                         runtime type
                             │
                         Class<?> ───────────────┐
                         │                       │
                         │ discovers             │ also implements Type
                         ↓                       │
                    member descriptors           │
                         │                       │
                 Member (common contract)        │
                  ┌──────┴───────────┐            │
                  │                  │            │
                Field           Executable        │
                  │             ┌────┴─────┐      │
                  │           Method   Constructor │
                  │             │          │       │
                  └──────┬──────┴──────────┘       │
                         ↓                          │
                 AccessibleObject                  │
               reflective access control           │
                                                    │
generic declaration metadata                       │
        ↓                                           │
       Type ◄───────────────────────────────────────┘
        ├─ Class<?>
        ├─ ParameterizedType
        ├─ TypeVariable
        ├─ WildcardType
        └─ GenericArrayType
~~~

Sơ đồ này không phải cây kế thừa Java tuyệt đối ở mọi nhánh; nó là **bản đồ vai trò** để learner biết object nào dẫn tới object nào. Về hierarchy thực tế, `Field` trực tiếp kế thừa `AccessibleObject`; `Method` và `Constructor` kế thừa `Executable`, còn `Executable` kế thừa `AccessibleObject`. Cả ba loại member đều thực hiện contract `Member`. `Class<?>` vừa là cửa vào để discover member, vừa là một implementation của `Type` khi generic metadata thật sự là một runtime class thông thường.

Từ đó có thể đọc toàn bộ module bằng bốn câu hỏi lớn:

~~~text
Class<?>          → runtime type này là gì và có cấu trúc nào?
Field/Method/
Constructor       → member cụ thể nào tồn tại và ta thao tác với nó ra sao?
AccessibleObject  → ta có quyền thực hiện thao tác phản chiếu đó không?
Type              → generic declaration được biểu diễn thế nào ở runtime metadata?
~~~

Lộ trình của module đi từ câu hỏi lớn tới cơ chế cụ thể:

~~~text
Reflection Basics
    ↓ hiểu vì sao runtime inspection tồn tại
Class Metadata
    ↓ biết runtime mô tả một type thế nào
Fields
    ↓ đọc/ghi trạng thái theo metadata
Methods
    ↓ chọn và gọi hành vi
Constructors
    ↓ tạo object khi constructor được phát hiện động
Access Control
    ↓ hiểu giới hạn encapsulation/JPMS
Generic Type Inspection
    ↓ đọc generic signature
Dynamic Invocation
    ↓ hiểu conversion và dispatch động sâu hơn
Limitations / Risks
    ↓ đánh giá safety, performance, maintainability
Dynamic Proxy
    ↓ nối metadata với framework-style interception
~~~

## <a id="class-object-entrypoints">Class<?>: cửa vào của Reflection</a>

Reflection thường bắt đầu từ một Class<?>. Class không phải source code của class; nó là object runtime đại diện cho một kiểu mà JVM đã biết.

Có ba cửa vào thường gặp:

~~~java
// 1. Biết kiểu ngay trong source
Class<PaymentService> a = PaymentService.class;

// 2. Có sẵn object nhưng không biết concrete type ở compile time
PaymentService service = new PaymentService("stripe");
Class<?> b = service.getClass();

// 3. Chỉ có tên binary của class ở runtime
Class<?> c = Class.forName("com.example.PaymentService");
~~~

Class literal phù hợp khi source đã tham chiếu trực tiếp tới type. Object.getClass() phù hợp với framework nhận một object bất kỳ và muốn inspect concrete runtime class. Class.forName(...) phù hợp với tình huống plugin/configuration nơi tên class chỉ có ở runtime.

`Class<T>` còn mang thông tin type ở compile time. `Class<PaymentService>` nói rằng object `Class` này đại diện cho `PaymentService`. `Class<?>` nghĩa là “đây là một `Class` object hợp lệ nhưng code hiện tại chưa biết type cụ thể là gì”. Framework thường làm việc với `Class<?>` vì chính việc chưa biết concrete type là một phần của bài toán; sau đó nó kiểm tra metadata trước khi cast hoặc thao tác.

Primitive và array cũng có Class object:

~~~java
Class<Integer> primitive = int.class;
Class<String[]> array = String[].class;
~~~

`Class.forName(String)` mặc định **khởi tạo class** sau khi tìm/load nó, nên static initialization có thể chạy. Overload `Class.forName(name, false, loader)` cho phép yêu cầu không initialize ở bước đó. Chi tiết lifecycle và class identity thuộc module ClassLoader; ở đây chỉ cần nhớ rằng lấy `Class` bằng tên có thể có side effect khác với việc đơn giản dùng class literal.

Sau khi có Class<?>, ta mới đi sang các câu hỏi cụ thể:

~~~java
Class<?> type = PaymentService.class;

String name = type.getName();
Field[] fields = type.getDeclaredFields();
Method[] methods = type.getDeclaredMethods();
Constructor<?>[] constructors = type.getDeclaredConstructors();
~~~

Chương Class Metadata sẽ đào sâu phần “type này là gì”; các chương Field/Method/Constructor sẽ xử lý từng loại member.

## <a id="reflection-use-cases">Khi nào Reflection thực sự hữu ích?</a>

Reflection mạnh nhất ở code hạ tầng phải làm việc với **nhiều kiểu do người dùng/framework extension định nghĩa**.

Một dependency injection container có thể tìm constructor và inject dependency. Một serializer có thể inspect field/property để chuyển object thành JSON. ORM có thể đọc metadata của entity và ánh xạ dữ liệu. Test runner có thể phát hiện method có annotation test. Router/framework có thể đọc metadata rồi đăng ký handler. Các công cụ debug, validation hoặc object mapping cũng có cùng mẫu: structure của target trở thành input của thuật toán.

Ví dụ một mapper rất nhỏ có thể nhận object mà không biết concrete class:

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

Điều quan trọng là mapper viết **một thuật toán theo metadata**, thay vì một nhánh riêng cho PaymentService, Customer, Order, ...

Reflection không phải lựa chọn duy nhất cho extensibility. Interface, factory, registry, ServiceLoader, generated code hoặc annotation processing có thể tạo contract rõ và an toàn hơn. Reflection đáng dùng khi runtime discovery thật sự đem lại giá trị, đặc biệt khi framework không thể yêu cầu mọi model viết adapter bằng tay.

## <a id="reflection-cost-boundary">Cái giá và ranh giới sử dụng</a>

Tính linh hoạt của Reflection đến từ việc dời một số quyết định từ compile time sang runtime. Vì vậy cái giá đầu tiên là **mất một phần kiểm tra tĩnh**.

~~~java
// Compiler kiểm tra được
service.pay(request);

// Sai tên chỉ lộ ra khi chạy
service.getClass().getMethod("proccess", PaymentRequest.class);
~~~

Ngoài lỗi tên, reflective code còn phải xử lý member không tồn tại, sai receiver, sai argument, access bị từ chối và exception do chính target ném ra. Các lỗi này sẽ được học cụ thể ở từng chương.

Cái giá thứ hai là hidden coupling. IDE dễ tìm reference của service.pay(...), nhưng khó biết một string "pay" trong config hay framework có đang phụ thuộc vào tên method đó hay không. Rename/refactor vì thế cần contract và test tốt hơn.

Cái giá thứ ba là encapsulation. Reflection vẫn chịu kiểm tra truy cập; một số API có thể yêu cầu thay đổi reflective accessibility, và JPMS tạo thêm ranh giới strong encapsulation. Đây là nội dung của chương Access Control. “Có Reflection” không đồng nghĩa “private không còn ý nghĩa”.

Cái giá thứ tư là runtime overhead. Lookup metadata và Method.invoke/Field access thường đắt hơn direct call, dù JVM và framework có nhiều kỹ thuật cache/optimization. Điều này hiếm khi là lý do để né Reflection ở bước bootstrap vài lần, nhưng đáng chú ý nếu lookup/invoke nằm trên hot path. Chương Limitations / Risks sẽ đi sâu hơn.

Một quy tắc thực dụng:

~~~text
Biết type/member ở compile time
→ ưu tiên direct call, interface, polymorphism hoặc factory

Structure thật sự chỉ biết ở runtime
và việc khám phá nó là requirement
→ Reflection có thể phù hợp
~~~

Khi dùng Reflection, nên thu hẹp vùng dynamic: validate metadata sớm, cache descriptor nếu dùng lặp lại, chuyển lỗi runtime thành lỗi domain/framework dễ hiểu, và giữ phần business code bên ngoài càng type-safe càng tốt.

Sau chương này, câu hỏi tiếp theo là: **một Class<?> thực sự cho ta biết những metadata nào về type?** Đó là vai trò của Class Metadata.
