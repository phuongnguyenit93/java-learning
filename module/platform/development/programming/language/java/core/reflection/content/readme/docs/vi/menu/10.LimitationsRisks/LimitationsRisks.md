# Khi nào nên dùng Reflection?

Reflection giải quyết một bài toán thật: mã có thể làm việc với cấu trúc chỉ được biết lúc chạy. Nhưng sự linh hoạt đó đến từ việc dời nhiều đảm bảo của trình biên dịch sang lúc chạy. Vì vậy câu hỏi thiết kế không phải “Reflection có làm được không?”, mà là “hành vi động này có đáng để trả chi phí về độ an toàn, sự phụ thuộc và vận hành không?”.

Các rủi ro trong chương này đều xuất phát từ cùng cách hiểu: mã Java trực tiếp tham chiếu kiểu/thành phần bằng ký hiệu mà trình biên dịch hiểu; mã dùng Reflection thường đi qua siêu dữ liệu, chuỗi, `Class`, `Method`, `Field` và `Object` rồi tự kiểm tra lúc chạy.

## <a id="reflection-cost-boundary">Cái giá và ranh giới sử dụng</a>

Tính linh hoạt của Reflection đến từ việc dời một số quyết định từ thời điểm biên dịch sang lúc chạy. Vì vậy cái giá đầu tiên là **mất một phần kiểm tra tĩnh**.

~~~java
// Trình biên dịch kiểm tra được
service.pay(request);

// Sai tên chỉ lộ ra khi chạy
service.getClass().getMethod("proccess", PaymentRequest.class);
~~~

Ngoài lỗi tên, code dùng Reflection còn phải xử lý thành phần không tồn tại, sai đối tượng nhận, sai đối số, quyền truy cập bị từ chối và ngoại lệ do chính mã đích ném ra. Các chương trước đã lần lượt minh họa những nhóm lỗi này; chương cuối gom chúng lại để phục vụ quyết định thiết kế.

Cái giá thứ hai là **sự phụ thuộc ẩn**. IDE dễ tìm tham chiếu của `service.pay(...)`, nhưng khó biết một chuỗi `"pay"` trong cấu hình hay framework có đang phụ thuộc vào tên method đó hay không. Đổi tên/tái cấu trúc vì thế cần hợp đồng và test tốt hơn.

Cái giá thứ ba là tính đóng gói. Reflection vẫn chịu kiểm tra truy cập; một số API có thể yêu cầu thay đổi khả năng truy cập phản chiếu, và JPMS tạo thêm ranh giới đóng gói mạnh. Đây là nội dung của chương **Quyền truy cập, đóng gói và giới hạn mô-đun**. “Có Reflection” không đồng nghĩa “private không còn ý nghĩa”.

Cái giá thứ tư là chi phí lúc chạy. Việc tra cứu siêu dữ liệu và thao tác qua `Method.invoke()`/`Field` thường đắt hơn lời gọi trực tiếp, dù JVM và framework có nhiều kỹ thuật lưu đệm/tối ưu. Điều này hiếm khi là lý do để né Reflection ở bước khởi động vài lần, nhưng đáng chú ý nếu việc tra cứu/gọi nằm trên đường chạy nóng. Các phần bên dưới sẽ phân tích chi tiết hơn.

Một quy tắc thực dụng:

~~~text
Biết kiểu/thành phần tại thời điểm biên dịch
→ ưu tiên lời gọi trực tiếp, interface, đa hình hoặc factory

Cấu trúc thật sự chỉ biết lúc chạy
và việc khám phá nó là yêu cầu
→ Reflection có thể phù hợp
~~~

Khi dùng Reflection, nên thu hẹp vùng động: kiểm tra siêu dữ liệu sớm, lưu đệm đối tượng mô tả nếu dùng lặp lại, chuyển lỗi lúc chạy thành lỗi miền/framework dễ hiểu, và giữ phần mã nghiệp vụ bên ngoài càng an toàn kiểu càng tốt.

Phần tổng quan này đặt các đánh đổi vào cùng một khung. Các phần tiếp theo sẽ tách riêng độ an toàn lúc biên dịch, tính đóng gói, hiệu năng, native image và khả năng bảo trì trước khi đi tới mô hình quyết định cuối cùng.

## <a id="compile-time-safety-loss">Mất độ an toàn tại thời điểm biên dịch</a>

Lời gọi trực tiếp tạo ra một hợp đồng mà trình biên dịch có thể kiểm tra:

```java
String result = service.pay(request);
```

Nếu `pay` bị đổi tên, kiểu tham số hoặc kiểu trả về thay đổi, mã gọi trực tiếp thường lỗi khi biên dịch. Với Reflection:

```java
Method method = service.getClass().getMethod(methodName, PaymentRequest.class);
Object result = method.invoke(service, request);
```

`methodName` có thể đến từ cấu hình hoặc annotation. Trình biên dịch không biết chuỗi đó có còn trỏ đến thành phần hợp lệ hay không. Sai tên, sai chữ ký, sai đối tượng đích hoặc sai đối số chỉ lộ ra khi luồng lúc chạy được thực thi.

Reflection còn làm luồng kiểu yếu hơn. `invoke()` trả `Object`, nên bên gọi thường phải ép kiểu. Siêu dữ liệu generic cũng chỉ mô tả khai báo; nó không tự khôi phục toàn bộ độ an toàn generic tại thời điểm biên dịch.

Đây không phải lý do cấm Reflection. Framework cần khám phá động thì không thể yêu cầu trình biên dịch biết trước mọi phần triển khai. Cách giảm rủi ro là đưa việc phân giải động vào một ranh giới nhỏ, kiểm tra sớm và cung cấp API có kiểu rõ ràng cho phần còn lại của ứng dụng.

```text
config/siêu dữ liệu động
        ↓ kiểm tra một lần
reflection adapter / registry
        ↓ hợp đồng có kiểu rõ ràng
mã ứng dụng
```

## <a id="encapsulation-breakage">Sự phụ thuộc khi phá tính đóng gói</a>

Truy cập phản chiếu sâu cho phép framework làm việc với constructor/field/method private khi ranh giới truy cập cho phép. Nhưng thành phần private vốn là chi tiết phần triển khai mà bên sở hữu được quyền thay đổi mà không cần giữ tương thích cho bên gọi bên ngoài.

Nếu mapper đọc `processedCount` bằng tên field hoặc framework gọi `internalStatus()`, mã đó đã tạo một hợp đồng ngầm:

```text
chi tiết triển khai của PaymentService
          ↓ tên/chữ ký private
bên sử dụng Reflection phụ thuộc vào nó
```

Đổi tên, tái cấu trúc package hoặc thay đổi tính đóng gói có thể làm bên sử dụng hỏng lúc chạy. JPMS còn khiến sự phụ thuộc này rõ hơn: package không `opens` thì truy cập phản chiếu sâu từ mô-đun khác có thể bị chặn hoàn toàn.

Khi thư viện/framework cần quyền truy cập qua Reflection bền vững, tốt hơn là thiết kế một điểm tích hợp rõ ràng: public interface, hợp đồng annotation, quy ước constructor/property được mô tả rõ, hoặc package `opens` có chủ đích. `setAccessible(true)` không nên trở thành cách mặc định để “sửa” một thiết kế API thiếu hợp đồng.

## <a id="reflection-performance">Hiệu năng và lưu đệm siêu dữ liệu</a>

Reflection có thêm chi phí thực thi, nhưng câu “Reflection luôn chậm” quá đơn giản để dùng làm quy tắc thiết kế.

Chi phí có thể nằm ở nhiều nơi:

- tra cứu lặp lại như `getDeclaredMethod()` hoặc quét toàn bộ thành phần;
- phân tích và phân giải siêu dữ liệu annotation/generic;
- kiểm tra quyền truy cập;
- đóng gói đối số, unboxing/widening và boxing giá trị trả về;
- phân phối lời gọi phản chiếu khó tối ưu giống một điểm gọi trực tiếp trong một số đường chạy nóng.

Java hiện đại đã tối ưu Core Reflection đáng kể, nên không nên dựa vào benchmark cũ hoặc các chi tiết phần triển khai lịch sử để kết luận. Cách thực tế là **không lặp việc khám phá siêu dữ liệu vô ích**.

```java
final class PaymentInvoker {
    private final Method pay;

    PaymentInvoker() throws NoSuchMethodException {
        this.pay = PaymentService.class.getMethod("pay", PaymentRequest.class);
    }

    Method method() {
        return pay;
    }
}
```

Framework thường phân giải siêu dữ liệu khi khởi động, đăng ký hoặc lần dùng đầu tiên, sau đó lưu đệm đối tượng mô tả/kế hoạch đã được kiểm tra. Lưu đệm giảm tra cứu/phân tích lặp lại; nó không xóa chi phí chuyển đổi đối số, phân phối lời gọi động hay chi phí bảo trì.

Nếu Reflection nằm trong vòng lặp nóng và hiệu năng thực sự quan trọng, hãy đo bằng benchmark/profiler trên tải công việc thật. Khi đối tượng đích đã được phân giải và được gọi rất nhiều lần, `MethodHandle` hoặc mã sinh tự động có thể đáng cân nhắc, nhưng lựa chọn phải dựa trên mô hình của trường hợp sử dụng chứ không phải khẩu hiệu “Reflection chậm”.

## <a id="native-image-boundary">Ranh giới với native image</a>

Đây là một ranh giới **triển khai/lúc chạy**, không phải kiến thức bắt buộc để hiểu `Method.invoke()` hay `Field.get()`. Trước tiên cần giải nghĩa vài thuật ngữ:

- **JVM/HotSpot**: cách chạy Java thông thường, nơi bytecode được JVM nạp và có thể được JIT biên dịch trong lúc chương trình chạy;
- **AOT (ahead-of-time)**: biên dịch trước khi chương trình bắt đầu chạy thay vì chờ lúc chạy mới biên dịch/phân giải mọi thứ;
- **native executable**: file thực thi native được tạo sẵn cho nền tảng đích;
- **closed-world analysis**: công cụ xây dựng giả định tập mã cần tồn tại phải được xác định từ những gì nó có thể thấy tại thời điểm xây dựng;
- **reachability metadata**: thông tin bổ sung nói cho công cụ xây dựng biết những class/thành phần nào vẫn cần được giữ vì chúng chỉ được tìm động lúc chạy.

Trên JVM thông thường, ứng dụng có thể quyết định lúc chạy rằng nó cần class/thành phần nào rồi dùng Reflection để tìm chúng. Một hệ thống native image theo mô hình AOT + closed-world có bài toán khác: công cụ xây dựng cần biết phần chương trình nào phải tồn tại trong native executable.

GraalVM Native Image là ví dụ điển hình. Phân tích tĩnh có thể nhận ra một số quyền truy cập qua Reflection khi đối tượng đích đủ rõ tại thời điểm xây dựng, nhưng truy cập phụ thuộc chuỗi/cấu hình lúc chạy thường cần **reachability metadata** để khai báo class, method, field, proxy hoặc tính năng động cần giữ lại.

Mô hình tư duy:

```text
thế giới JVM động
đầu vào lúc chạy → chọn thành phần → Reflection tìm thành phần

native image theo mô hình closed world
phân tích lúc xây dựng + reachability metadata
        ↓
quyết định thành phần nào có trong file thực thi
```

Vì vậy framework dùng Reflection nhiều cần nghĩ đến tích hợp native image ngay tại lớp siêu dữ liệu. Một ứng dụng chạy đúng trên HotSpot nhưng thiếu reachability metadata có thể lỗi trong native executable khi thành phần động không được đăng ký/giữ lại.

Cấu hình native image là ranh giới của môi trường triển khai/lúc chạy khác, không thay đổi ngữ nghĩa của Java Reflection trên JVM. Không nên đưa mối quan tâm về native image vào mọi class nghiệp vụ; framework hoặc mô-đun tích hợp nên sở hữu các gợi ý/siêu dữ liệu tương ứng.

## <a id="reflection-maintainability">Khả năng bảo trì và tái cấu trúc</a>

Trình biên dịch và IDE hiểu tham chiếu ký hiệu tốt hơn Reflection dựa trên chuỗi. Nếu mã viết:

```java
service.pay(request);
```

thao tác đổi tên khi tái cấu trúc có thể cập nhật/cảnh báo các điểm gọi. Nếu mã viết:

```java
getMethod("pay", PaymentRequest.class);
```

công cụ có thể không biết chuỗi `"pay"` là một phụ thuộc cần đổi. Cấu hình bên ngoài mã nguồn còn khó theo dấu hơn.

Reflection cũng làm luồng điều khiển ít hiển nhiên: đọc mã nguồn của bên gọi chưa chắc biết method nào sẽ chạy, vì đối tượng đích có thể đến từ việc quét, annotation, tên class hoặc registry plugin. Việc gỡ lỗi và phân tích tĩnh vì thế cần nhiều ngữ cảnh lúc chạy hơn.

Một thiết kế dùng Reflection dễ bảo trì thường có các đặc điểm:

1. Reflection tập trung ở lớp framework/adapter thay vì rải khắp mã nghiệp vụ;
2. dùng `Class<?>`, annotation, enum hoặc đăng ký có kiểu rõ ràng khi có thể thay cho chuỗi thô;
3. phân giải và kiểm tra siêu dữ liệu sớm;
4. lưu đệm một mô hình đã kiểm tra thay vì quét lại nhiều lần;
5. có kiểm thử tích hợp (integration test) chạy các luồng Reflection quan trọng;
6. hợp đồng public vẫn là nguồn sự thật, Reflection vào thành phần private chỉ dùng khi yêu cầu thật sự cần.

## <a id="reflection-decision-model">Mô hình quyết định: khi nào nên và không nên dùng Reflection?</a>

Sau khi đã đi qua `Class<?>`, đối tượng mô tả thành phần, thao tác động, kiểm soát truy cập, siêu dữ liệu generic và Dynamic Proxy, có thể đưa quyết định sử dụng Reflection về một chuỗi câu hỏi thực dụng:

```text
Cấu trúc/kiểu/thành phần đã biết rõ tại thời điểm biên dịch?
    ├─ Có
    │   ↓
    │ ưu tiên lời gọi trực tiếp / interface / đa hình / factory
    │
    └─ Không
        ↓
Việc khám phá lúc chạy có thực sự là yêu cầu?
    ├─ Không
    │   ↓
    │ làm hợp đồng tĩnh rõ hơn thay vì dùng Reflection
    │
    └─ Có
        ↓
Có thể khoanh Reflection vào một ranh giới hạ tầng nhỏ?
    ├─ Không
    │   ↓
    │ mức độ phụ thuộc + lỗi lúc chạy + chi phí bảo trì tăng mạnh
    │
    └─ Có
        ↓
kiểm tra sớm → lưu đệm siêu dữ liệu → cung cấp hợp đồng có kiểu rõ ràng
```

Một lựa chọn Reflection thường hợp lý khi nhiều điều kiện sau cùng đúng:

- kiểu/thành phần cụ thể chỉ được biết lúc chạy;
- framework/công cụ cần một thuật toán chung cho nhiều kiểu do bên ngoài định nghĩa;
- siêu dữ liệu hoặc annotation là một phần tự nhiên của hợp đồng;
- mã dùng Reflection được tập trung ở lớp adapter/container/serializer/proxy;
- lỗi tra cứu/truy cập/sai kiểu được kiểm tra và chuyển thành lỗi miền/framework rõ ràng;
- hiệu năng được đo trên tải công việc thật nếu Reflection nằm trên đường chạy nóng.

Ngược lại, Reflection thường là tín hiệu thiết kế kém nếu mã đã biết chính xác kiểu/thành phần nhưng vẫn dùng tra cứu bằng chuỗi chỉ để tránh viết lời gọi trực tiếp, hoặc nếu `setAccessible(true)` được dùng tràn lan để vượt qua ranh giới API mà không có hợp đồng tích hợp rõ ràng.

Toàn bộ module có thể được giữ lại bằng mô hình tư duy cuối:

```text
LÝ DO
→ cần làm việc với cấu trúc chỉ biết lúc chạy

MÔ HÌNH
→ Class<?> + đối tượng mô tả thành phần + siêu dữ liệu Type

CƠ CHẾ
→ khám phá → kiểm tra → kiểm tra quyền truy cập → thao tác

TÍCH HỢP
→ siêu dữ liệu annotation lúc chạy / Dynamic Proxy / công cụ framework

ĐÁNH ĐỔI
→ an toàn lúc biên dịch / đóng gói / hiệu năng / khả năng tái cấu trúc / native image

QUYẾT ĐỊNH
→ chỉ dùng Reflection khi tính động lúc chạy thực sự là một phần của yêu cầu
```

Reflection mạnh nhất khi **tính động là yêu cầu thật** và phần động được giữ trong một ranh giới nhỏ, được kiểm tra sớm, có hợp đồng rõ ràng. Khi kiểu đã biết tại thời điểm biên dịch, Java trực tiếp vẫn thường đơn giản, an toàn và dễ bảo trì hơn.
