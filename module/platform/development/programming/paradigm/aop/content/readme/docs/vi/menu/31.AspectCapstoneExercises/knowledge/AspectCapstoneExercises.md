<a id="back-to-top"></a>

# Bài tập tổng hợp

## Menu
- [Nhận diện scattering và tangling trong chương trình mẫu](#aspect-capstone-exercises-001)
- [Thiết kế aspect ghi nhật ký với phạm vi chọn và loại trừ rõ ràng](#aspect-capstone-exercises-002)
- [Đo thời gian và bảo toàn kết quả, ngoại lệ của lời gọi](#aspect-capstone-exercises-003)
- [Thực nghiệm khác biệt giữa điểm gọi và điểm thực thi](#aspect-capstone-exercises-004)
- [So sánh lời gọi qua proxy với cơ chế biến đổi mã](#aspect-capstone-exercises-005)
- [Thiết kế hai aspect có thứ tự và kiểm chứng chuỗi thực thi](#aspect-capstone-exercises-006)
- [Xây dựng aspect có ngữ cảnh riêng cho lời gọi đồng thời](#aspect-capstone-exercises-007)
- [Minh họa pointcut theo luồng điều khiển hoặc mở rộng cấu trúc kiểu](#aspect-capstone-exercises-008)
- [Thử thay đổi cấu trúc mã để phát hiện pointcut mong manh](#aspect-capstone-exercises-009)
- [Đo chi phí, kiểm thử hồi quy và giải thích khi nên bỏ AOP](#aspect-capstone-exercises-010)

## <a id="aspect-capstone-exercises-001">Nhận diện scattering và tangling trong chương trình mẫu</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện scattering và tangling trong chương trình mẫu

Một trong những lý do quan trọng để tìm hiểu **lập trình hướng khía cạnh (aspect-oriented programming, AOP)** là nhận ra khi nào các chức năng xuyên suốt bị phân tán ở nhiều vị trí và bị trộn với nghiệp vụ. Hai hiện tượng thường được nhắc đến là **scattering** và **tangling**. Chúng khác nhau nhưng thường cùng tồn tại: một yêu cầu như ghi audit có thể xuất hiện ở hàng chục phương thức (scattering), trong khi ở từng phương thức, audit lại xen giữa kiểm tra đầu vào, tính toán và trả kết quả (tangling).

Bài học này là một **bài tập tổng hợp**. Bạn sẽ đọc chương trình Java 17+ chạy được, xác định các concern, đánh dấu bằng chứng scattering/tangling, phân tích rủi ro bảo trì, đề xuất điểm chặn (*join point*) và điều kiện chọn (*pointcut*), rồi so sánh hành vi trước/sau khi tách xử lý xuyên suốt bằng một proxy minh họa. Mục tiêu không phải biến mọi đoạn code trùng lặp thành aspect: chỉ chọn tách khi đó thật sự là một *cross-cutting concern* và cấu trúc mới bảo toàn các yêu cầu chức năng.

## 1. Phân biệt chính xác hai hiện tượng

**Concern** là một trách nhiệm hoặc yêu cầu mà chương trình phải đáp ứng, ví dụ tính giá hóa đơn, kiểm tra tồn kho, ghi sự kiện kiểm toán, theo dõi thời gian xử lý hay xác thực quyền truy cập.

**Scattering (phân tán)** xuất hiện khi **cùng một concern** phải được triển khai hoặc sửa ở **nhiều vị trí khác nhau** của codebase. Ví dụ mọi thao tác thay đổi dữ liệu đều tự ghi hai dòng audit; khi đổi cấu trúc sự kiện audit phải sửa trên hàng chục lớp.

**Tangling (đan xen)** xuất hiện khi **nhiều concern riêng biệt bị trộn vào cùng một đơn vị mã**, thường cùng phương thức hoặc lớp, khiến luồng nghiệp vụ bị ngắt bởi logging, audit, xử lý quyền hoặc cơ chế hạ tầng.

Để ghi nhớ:

| Thuộc tính | Scattering | Tangling |
|---|---|---|
| Góc quan sát | Một concern trải rộng ra nhiều chỗ | Nhiều concern đan trong một chỗ |
| Câu hỏi nhận diện | “Muốn đổi concern này phải sửa bao nhiêu vị trí?” | “Phương thức này đang xử lý bao nhiêu trách nhiệm?” |
| Ví dụ | `TRACE begin/end` lặp trong 20 service | `TRACE`, kiểm tra input, tính tiền, `AUDIT` nằm trong cùng phương thức |
| Rủi ro thường thấy | Sửa thiếu, không đồng nhất hành vi, khó thay chính sách | Khó hiểu luồng chính, khó kiểm thử riêng, dễ sai thứ tự xử lý |
| Hướng cải thiện | Gom chính sách xuyên suốt về nơi thống nhất | Tách xử lý xuyên suốt khỏi logic nghiệp vụ |
| Điều cần bảo vệ | Không bỏ sót điểm nghiệp vụ phải chặn | Không đổi thứ tự tác dụng phụ, outcome hoặc ngoại lệ |

**Không phải cứ xuất hiện hai dòng giống nhau là scattering đáng tách bằng AOP.** Công thức nghiệp vụ hoặc điều kiện đầu vào trùng nhau có thể thuộc một hàm domain chung, value object hoặc policy domain, không phải aspect. **Không phải phương thức dài nào cũng tangling:** độ dài là tín hiệu đọc mã, còn tangling liên quan ranh giới trách nhiệm.

## 2. Chương trình mẫu: hai nghiệp vụ cùng lặp tracing và audit

Tình huống: một hệ thống có dịch vụ lập hóa đơn và dịch vụ giữ hàng. Mỗi phương thức cần làm ba nhóm việc:

1. **Nghiệp vụ:** kiểm tra số lượng và tính tổng tiền hoặc số hàng còn lại.
2. **Tracing:** in dấu thời điểm đi vào/đi ra để quan sát đường gọi.
3. **Audit thành công:** ghi sự kiện khi thao tác nghiệp vụ hoàn tất bình thường.

Trong phiên bản đầu, mỗi dịch vụ tự viết các thao tác tracing và audit. Mã cố ý không sử dụng bất kỳ công cụ AOP nào để người học nhận diện từ cấu trúc Java thông thường.

Tạo tệp `ScatteringTanglingBefore.java`:

```java
public class ScatteringTanglingBefore {
    static final class InvoiceService {
        int issue(int count) {
            System.out.println("TRACE invoice.enter");  // I1
            try {
                if (count <= 0) {                        // I2
                    throw new IllegalArgumentException(
                        "count must be positive"
                    );
                }

                int total = count * 10;                 // I3
                System.out.println("AUDIT invoice.issued"); // I4
                return total;                           // I5
            } finally {
                System.out.println("TRACE invoice.exit"); // I6
            }
        }
    }

    static final class StockService {
        int reserve(int units) {
            System.out.println("TRACE stock.enter");    // S1
            try {
                if (units <= 0 || units > 20) {          // S2
                    throw new IllegalArgumentException(
                        "units out of range"
                    );
                }

                int remaining = 20 - units;             // S3
                System.out.println("AUDIT stock.reserved"); // S4
                return remaining;                       // S5
            } finally {
                System.out.println("TRACE stock.exit"); // S6
            }
        }
    }

    public static void main(String[] args) {
        var invoice = new InvoiceService();
        var stock = new StockService();

        System.out.println("Invoice result: " + invoice.issue(3));
        System.out.println("Stock result: " + stock.reserve(4));

        try {
            stock.reserve(25);
        } catch (IllegalArgumentException exception) {
            System.out.println("Error: " + exception.getMessage());
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ScatteringTanglingBefore.java
java ScatteringTanglingBefore
```

Stdout chính xác:

```text
TRACE invoice.enter
AUDIT invoice.issued
TRACE invoice.exit
Invoice result: 30
TRACE stock.enter
AUDIT stock.reserved
TRACE stock.exit
Stock result: 16
TRACE stock.enter
TRACE stock.exit
Error: units out of range
```

**Đọc luồng thực thi trước khi trả lời.** Khi `issue(3)` thành công, audit được in sau phép tính, còn `finally` in dấu thoát trước khi caller nhận kết quả. Khi `reserve(25)` ném ngoại lệ, audit thành công **không được in**, nhưng dấu thoát vẫn được in vì `finally` chạy cả trên đường lỗi. Các chi tiết này là hợp đồng cần bảo toàn nếu tái cấu trúc.

## 3. Bài tập A: đánh dấu concern trên mã gốc

Không đọc đáp án ở phần tiếp theo trước khi tự trả lời:

1. Trong mỗi phương thức, xác định concern nghiệp vụ và các concern hỗ trợ. Chúng có độc lập về ý định thay đổi không?
2. Hai nhóm dòng nào minh họa **scattering** giữa `InvoiceService` và `StockService`?
3. Dòng nào trong `issue()` cho thấy **tangling**? Nếu xóa riêng tracing thì còn những concern nào cùng nằm trong phương thức?
4. Tại sao `I2` và `S2` chưa đủ để kết luận “validation là scattering nên phải chuyển hết sang advice”?
5. Nếu đổi định dạng audit thành JSON, phải xem xét những vị trí nào của phiên bản hiện tại? Nếu thêm dịch vụ thứ ba, rủi ro sao chép là gì?
6. Đường lỗi `reserve(25)` có audit thành công hay không? Có tracing thoát không? Vì sao câu trả lời quan trọng?
7. Hãy chỉ ra một thay đổi tưởng là tái cấu trúc nhưng **làm đổi kết quả quan sát được** của chương trình.
8. Liệu AOP có thể gom tracing và audit mà không xóa kiểm tra input của domain không? Đề xuất phạm vi pointcut thích hợp.

Hãy trình bày đáp án dưới dạng bảng gồm **concern — bằng chứng — nhiều nơi hay trộn cùng nơi — yêu cầu hành vi**. Cách làm này buộc nhận diện bằng chứng trên mã nguồn thay vì chỉ gọi tên khái niệm.

## 4. Đáp án phân tích chương trình mẫu

### 4.1. Phân loại các dòng

| Dòng | Concern | Vai trò | Bằng chứng |
|---|---|---|---|
| `I1`, `I6` | Tracing | Xử lý xuyên suốt | Ghi dấu vào/ra quanh nghiệp vụ hóa đơn |
| `S1`, `S6` | Tracing | Xử lý xuyên suốt | Cùng chính sách dấu vào/ra cho tồn kho |
| `I4` | Audit thành công | Xử lý xuyên suốt nhưng cần nội dung nghiệp vụ | Ghi sự kiện `invoice.issued` sau khi tính thành công |
| `S4` | Audit thành công | Xử lý xuyên suốt nhưng cần nội dung nghiệp vụ | Ghi sự kiện `stock.reserved` sau khi tính thành công |
| `I2` | Kiểm tra điều kiện nghiệp vụ | Bảo vệ quy tắc của hóa đơn | Số lượng phải dương |
| `S2` | Kiểm tra điều kiện nghiệp vụ | Bảo vệ quy tắc giữ hàng | Số lượng phải trong phạm vi được hỗ trợ |
| `I3`, `I5` | Tính tổng và trả kết quả | Nghiệp vụ | `count * 10` |
| `S3`, `S5` | Tính tồn kho và trả kết quả | Nghiệp vụ | `20 - units` |

**Scattering của tracing:** cặp `I1/I6` và `S1/S6` thể hiện cùng yêu cầu “ghi entry/exit” được phân bố ở hai dịch vụ. Muốn đổi chiến lược tracing hoặc gắn correlation ID, nhiều vị trí phải được sửa hoặc dễ bị quên.

**Scattering của audit:** `I4` và `S4` đều thực hiện chính sách ghi nhận sau thành công. Tuy mỗi sự kiện có nhãn và payload nghiệp vụ khác nhau, **cơ chế xuất audit** có thể là mối quan tâm xuyên suốt, còn **ý nghĩa sự kiện** là dữ liệu nghiệp vụ phải được chỉ rõ.

**Tangling tại `issue()`:** phương thức vừa ghi trace, kiểm tra input, tính tiền, xuất audit, xử lý dọn cuối đường gọi. Trách nhiệm observability/audit được trộn trong đường tính hóa đơn. `reserve()` có tình trạng tương tự.

**Validation không mặc nhiên là scattering AOP:** `count > 0` và `0 < units <= 20` thuộc hai bất biến domain khác nhau. Ngay cả khi lặp một điều kiện giống nhau, cách sửa tốt có thể là giá trị kiểu mạnh, helper thuần hoặc domain policy dùng chung. Nếu một kiểm tra quyền truy cập có quy tắc xuyên suốt tất cả thao tác nhạy cảm, nó có thể là ứng viên aspect; nhưng không được chuyển kiểm tra bảo vệ ra khỏi nơi đáng tin mà chưa chứng minh mọi đường gọi bắt buộc đều được chặn.

### 4.2. “Scattering + tangling” không phải hai nhãn thay thế nhau

Hai nhãn mô tả **hai chiều độc lập**. Nhìn từ tracing, các lệnh xuất hiện ở nhiều lớp → scattering. Nhìn từ `InvoiceService.issue()`, tracing được ghép vào tính toán nghiệp vụ → tangling. Một concern có thể vừa rải rác trên nhiều mô-đun, vừa đan vào mỗi phương thức đó.

Một chương trình nhỏ có thể chỉ có tangling mà chưa cho thấy scattering (ví dụ một hàm rất dài pha nhiều concern). Một concern có thể scattering nhưng không tangling quá nặng nếu mỗi lớp gọi một helper rõ ràng. Do vậy không suy luận hai hiện tượng luôn xuất hiện cùng mức độ.

### 4.3. Phân biệt tín hiệu và nguyên nhân

Các dấu hiệu như “nhiều `println`”, “nhiều `try/finally`” hoặc “hai lớp giống nhau” mới là **tín hiệu điều tra**. Cần đặt câu hỏi trách nhiệm: có cùng lý do thay đổi không, có trải qua nhiều ranh giới nghiệp vụ không, có cần chính sách tập trung không, có điểm chặn ổn định không?

Ví dụ hai phương thức cùng gọi `validatePositive()` có thể chỉ đang tái sử dụng kiểm tra dữ liệu bình thường, không cần aspect. Ngược lại, logging và audit có thể không chứa dòng mã y hệt nhưng vẫn có cùng lý do thay đổi và là mối quan tâm xuyên suốt.

## 5. Bài tập B: dự đoán hệ quả khi sửa phiên bản ban đầu

Hãy xét các yêu cầu mới và xác định nhóm mã chịu tác động:

| Thay đổi yêu cầu | Nơi phải xem xét ở bản gốc | Rủi ro scattering/tangling |
|---|---|---|
| Thêm correlation ID vào mọi dấu tracing | `I1/I6`, `S1/S6` và các dịch vụ tương tự | Sửa thiếu hoặc quên dọn context trên đường lỗi |
| Chuyển audit từ console sang kênh lưu trữ | `I4`, `S4` và các vị trí audit khác | Trộn lỗi I/O của hạ tầng vào nghiệp vụ |
| Audit cả thao tác thất bại | Các nhánh `catch/finally` tương ứng, định nghĩa event lỗi | Sai ngữ nghĩa nếu gắn “success” cho lời gọi lỗi |
| Đổi đơn giá hóa đơn | `I3` | Thay đổi nghiệp vụ, không cần thay tracing |
| Đổi giới hạn tồn kho từ 20 thành cấu hình | `S2/S3` | Thay đổi luật tồn kho, không nên ép vào aspect logging |
| Đo thời gian mọi thao tác quan trọng | Phần bao quanh từng điểm được chọn | Nếu copy thủ công dễ rải logic đo ở nhiều nơi |

Khi refactor, không chỉ đếm số dòng giảm được. Cần chứng minh sự thay đổi đã **giảm số vị trí phải sửa cho cùng một chính sách** mà vẫn giữ hành vi đích: kết quả thành công, thứ tự side effect, loại ngoại lệ, điều kiện ghi audit và cleanup.

## 6. Thiết kế AOP cho ví dụ: chọn join point, pointcut và advice

Một thiết kế hướng AOP có thể tách phần cơ chế tracing/audit khỏi thân nghiệp vụ:

- **Join point:** thời điểm thực thi các thao tác quan trọng như lập hóa đơn hoặc giữ hàng.
- **Pointcut:** chọn đúng các thao tác đã được định nghĩa là cần observability/audit; không mặc định chặn mọi getter hay helper.
- **Around advice:** ghi trace khi vào; gọi `proceed()` để chạy đích; chỉ ghi audit thành công nếu đích hoàn tất bình thường; ghi trace thoát trong `finally`.
- **Context:** định danh thao tác (ví dụ `invoice`/`stock`), sự kiện audit và các trường thật sự cần; không tự động sao chép toàn bộ object graph.
- **Bộ máy can thiệp:** proxy hoặc weaving tùy công cụ, ranh giới target, khả năng chặn self-invocation và loại join point cần hỗ trợ.

Logic cơ chế có thể mô tả bằng pseudocode:

```text
around(operation, auditEvent):
    trace(operation, "enter")
    try:
        result = proceed()               // chạy nghiệp vụ
        audit(auditEvent, "success")       // chỉ sau thành công
        return result
    finally:
        trace(operation, "exit")          // luôn chạy cả khi có lỗi
```

Đây là **pseudocode mô tả advice**, không phải cú pháp của một framework hay một chương trình Java độc lập. Ý định chính là chỉ có một nơi định nghĩa quy tắc tracing; audit dùng cấu hình sự kiện riêng; phương thức nghiệp vụ không cần biết cách xuất trace.

Tuy nhiên, muốn có ngữ nghĩa “audit phản ánh tác vụ đã commit thật” thì ghi audit sau return của phương thức **chưa chắc đủ** nếu hệ thống còn có giao dịch hoặc thao tác bất đồng bộ. Chính sách phải định nghĩa “thành công” ở biên nào: hoàn tất lời gọi, cam kết dữ liệu hay sự kiện nghiệp vụ đã bền vững. Refactor không được âm thầm thay đổi mốc này.

## 7. Phiên bản sau: dùng proxy để minh họa tách concern

Chương trình sau dùng **JDK dynamic proxy** và một hàm `observed()` chứa phần tracing/audit dùng chung. Đây là **một cách triển khai AOP**, không khẳng định mọi mô hình aspect đều yêu cầu proxy. Hai lớp domain chỉ giữ logic kiểm tra input và tính toán.

Tạo tệp `ScatteringTanglingAfter.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ScatteringTanglingAfter {
    interface Operation {
        int execute(int value);
    }

    static final class InvoiceCore implements Operation {
        @Override
        public int execute(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException(
                    "count must be positive"
                );
            }
            return count * 10;
        }
    }

    static final class StockCore implements Operation {
        @Override
        public int execute(int units) {
            if (units <= 0 || units > 20) {
                throw new IllegalArgumentException(
                    "units out of range"
                );
            }
            return 20 - units;
        }
    }

    static Operation observed(
            Operation target, String name, String auditEvent) {
        return (Operation) Proxy.newProxyInstance(
            Operation.class.getClassLoader(),
            new Class<?>[] { Operation.class },
            (proxy, method, args) -> {
                // Demo chỉ gọi Operation.execute().
                System.out.println("TRACE " + name + ".enter");
                try {
                    Object result = method.invoke(target, args);
                    System.out.println("AUDIT " + auditEvent);
                    return result;
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                } finally {
                    System.out.println("TRACE " + name + ".exit");
                }
            }
        );
    }

    public static void main(String[] args) {
        Operation invoice = observed(
            new InvoiceCore(), "invoice", "invoice.issued"
        );
        Operation stock = observed(
            new StockCore(), "stock", "stock.reserved"
        );

        System.out.println("Invoice result: " + invoice.execute(3));
        System.out.println("Stock result: " + stock.execute(4));

        try {
            stock.execute(25);
        } catch (IllegalArgumentException exception) {
            System.out.println("Error: " + exception.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ScatteringTanglingAfter.java
java ScatteringTanglingAfter
```

Stdout chính xác:

```text
TRACE invoice.enter
AUDIT invoice.issued
TRACE invoice.exit
Invoice result: 30
TRACE stock.enter
AUDIT stock.reserved
TRACE stock.exit
Stock result: 16
TRACE stock.enter
TRACE stock.exit
Error: units out of range
```

Đầu ra trên **giống bản trước từng dòng** đối với ba ca thử đã chọn. Điều đó là bằng chứng cần thiết cho các đường này, nhưng chưa chứng minh toàn bộ ngữ nghĩa production. Ví dụ thực tế còn phải kiểm tra overload, cách xử lý `equals/hashCode/toString` của proxy, ngoại lệ checked, gọi nội bộ, concurrency và những phương thức không thuộc pointcut.

### Tại sao phiên bản sau giảm scattering và tangling?

- Tracing `enter/exit` chỉ được mô tả một lần trong `observed()`, thay vì lặp trong `issue()` và `reserve()`.
- Quy tắc audit khi thành công chỉ được triển khai một lần; từng hành động vẫn truyền tên sự kiện `invoice.issued` hoặc `stock.reserved`.
- `InvoiceCore.execute()` tập trung vào “số lượng hợp lệ → tính tổng”; `StockCore.execute()` tập trung vào “số lượng hợp lệ → tính tồn kho”. Đây là giảm tangling trong mã nghiệp vụ.
- Chính sách có thể được sửa tập trung trong interceptor minh họa, trong khi các quy tắc domain vẫn ở lớp domain.

**Giới hạn quan trọng:** Bản demo không loại bỏ mọi khả năng tangling. Hàm `observed()` đang thực hiện cả tracing và audit; nếu hai chính sách phát triển độc lập, có thể tách thành hai interceptor/advice riêng trong một chuỗi với thứ tự có chủ đích. Việc gộp hai concern vào một wrapper là **bước chuyển để minh họa cơ chế**, chưa phải thiết kế sản phẩm cuối cùng tối ưu cho mọi hoàn cảnh.

## 8. Phân tích thay đổi ngữ nghĩa khi refactor

| Thuộc tính | Bản đầu | Bản sau | Điều cần giữ |
|---|---|---|---|
| Kiểm tra số lượng hóa đơn | Trong `issue()` | Trong `InvoiceCore.execute()` | `count <= 0` vẫn báo lỗi |
| Kiểm tra giới hạn tồn kho | Trong `reserve()` | Trong `StockCore.execute()` | `units <= 0 || units > 20` vẫn báo lỗi |
| Tracing entry | Đầu mỗi phương thức | Đầu handler `observed()` | Một lần trước nghiệp vụ |
| Tracing exit | `finally` riêng của từng phương thức | `finally` chung | Chạy trên thành công và lỗi |
| Audit success | Sau phép tính thành công | Sau `Method.invoke` hoàn tất | Không ghi success khi target lỗi |
| Ngoại lệ của target | Ném trực tiếp | Reflection bọc rồi handler mở cause | Caller nhận đúng `IllegalArgumentException` |
| Kết quả | `30` và `16` | `30` và `16` | Cùng đầu ra |
| Đường gọi ngoài | Gọi trực tiếp đối tượng domain | Đi qua proxy | Phải có cùng chính sách can thiệp |

Điểm cần cảnh giác: với một proxy bọc quanh đối tượng, nếu caller giữ tham chiếu tới `new InvoiceCore()` và gọi trực tiếp thì lời gọi **không đi qua interceptor**. Vì thế một chính sách bắt buộc như kiểm soát truy cập không nên chỉ phụ thuộc vào một proxy có thể bị bỏ qua; cần thiết kế ranh giới tin cậy và kiểm thử mọi đường gọi hợp lệ. Với cơ chế weaving, phạm vi join point và khả năng chặn lời gọi nội bộ có thể khác.

Bản `observed()` minh họa chỉ một phương thức interface `execute()`. Nếu caller gọi các phương thức `Object` trên proxy, handler có thể cũng nhận các lời gọi đó; sản phẩm thực tế cần thiết kế riêng cho identity, equality, logging proxy object và những method không thuộc pointcut.

## 9. Bài tập C: thiết kế hai advice độc lập

Hãy giả sử tracing và audit có vòng đời phát triển khác nhau:

- Nhóm vận hành muốn tracing thêm duration hoặc correlation ID, với chính sách lấy mẫu.
- Nhóm nghiệp vụ muốn audit thành công hoặc thất bại theo định dạng kiểm toán bắt buộc.
- Tracing có thể tắt trong một số phép đo hiệu năng; audit bắt buộc không được bỏ qua.

Hãy tách thiết kế thành:

```text
Caller
  → TracingAdvice (before / finally)
     → AuditAdvice (after returning / after throwing nếu chính sách yêu cầu)
        → TargetOperation
```

Trả lời:

1. Thứ tự hai advice thay đổi điều gì? Nếu tracing ở ngoài audit, khoảng đo có bao gồm chi phí audit không?
2. Audit có được phép chạy khi nghiệp vụ ném lỗi hay phải có sự kiện lỗi riêng?
3. Nếu audit ném lỗi ngay sau khi target đã thành công, caller nên nhận thành công hay thất bại? Chính sách nghiệp vụ phải quyết định trước khi code.
4. Nếu tracing chỉ lấy mẫu 1%, audit bắt buộc vẫn chạy 100% hay không?
5. Nếu một advice around quên gọi `proceed()`, điều gì xảy ra?
6. Nếu retry gọi `proceed()` nhiều lần, mỗi lần target và audit sẽ được ghi như thế nào?

**Đáp án hướng dẫn:** mỗi concern nên có vòng đời, chính sách lỗi và phạm vi riêng. Độ đo tracing của wrapper ngoài có thể gồm cả thời gian audit nếu audit nằm bên trong. `finally` giúp cleanup dù có lỗi, còn “after returning” chỉ chạy khi hoàn tất bình thường. Sampling tracing không được tự tác động tới audit bắt buộc. Không được gộp các quyết định này thành một interceptor chỉ để giảm số dòng nếu nghĩa vụ của chúng khác nhau.

## 10. Bài tập D: tự nhận diện từ một chương trình khác

Hãy đọc pseudocode sau:

```text
function createPayment(request):
    ensureAuthenticated(request.user)
    log("start createPayment")
    validatePayment(request)
    payment = calculateAndPersistPayment(request)
    audit("payment-created", request.user, payment.id)
    log("end createPayment")
    return payment

function cancelPayment(request):
    ensureAuthenticated(request.user)
    log("start cancelPayment")
    validateCancelRequest(request)
    payment = cancelStoredPayment(request.id)
    audit("payment-cancelled", request.user, payment.id)
    log("end cancelPayment")
    return payment
```

Hãy xác định:

- Concern nào có cùng lý do thay đổi ở hai phương thức?
- Những đoạn nào thuộc nghiệp vụ thanh toán và hủy thanh toán?
- Điểm nào cho thấy tangling trong từng phương thức?
- Điều gì xảy ra nếu `validatePayment` hoặc `cancelStoredPayment` ném lỗi? Dấu “end” có được ghi không?
- Nếu dời `ensureAuthenticated` vào advice, phải kiểm chứng ranh giới nào để không tạo đường gọi thiếu kiểm soát?
- Muốn có audit chỉ khi dữ liệu **đã cam kết bền vững**, nên đặt policy ở đâu so với biên commit?
- Hai thao tác có cần cùng audit payload không, hay chỉ nên chia sẻ cơ chế phát sự kiện?

**Gợi ý đáp án:** xác thực, ghi dấu và cơ chế audit là ứng viên cross-cutting; hai thao tác có logic domain và quy tắc kiểm tra đầu vào khác nhau. Trong pseudocode, “end” ở cuối chỉ chạy trên đường thành công; muốn bảo toàn cleanup trên đường lỗi thì phải định nghĩa lại yêu cầu và có cấu trúc tương đương `finally`. Xác thực là bảo vệ bắt buộc, do đó refactor an toàn phải chứng minh **mọi đường truy cập dữ liệu cần bảo vệ** đều đi qua biên kiểm soát. Audit về commit không thể được bảo đảm chỉ bằng “đã return thành công” nếu cơ chế lưu trữ còn có giao dịch chưa hoàn tất.

## 11. Kiểm thử bằng chứng về scattering và tangling

Một kế hoạch kiểm thử nên chứa:

**A. Contract trước/sau**

- `issue(3)` hoặc thao tác tương đương cho tổng 30.
- `reserve(4)` hoặc thao tác tương đương trả 16.
- `reserve(25)` ném `IllegalArgumentException` với thông điệp mong đợi.
- Trên hai lần thành công có hai sự kiện audit success; trên lần lỗi không có audit success.
- Mỗi lần đi qua điểm được chọn phải có dấu enter và exit, kể cả khi lỗi.
- Nếu thay thứ tự advice, so lại thứ tự dấu/log và side effects.

**B. Kiểm chứng đúng phạm vi**

- Phương thức thuộc pointcut có advice.
- Phương thức không thuộc pointcut không bị audit sai.
- Gọi trực tiếp target, lời gọi qua proxy, self-invocation và đường async được kiểm tra theo cơ chế AOP chọn.
- Không đánh đồng số lần advice gọi với số lần nghiệp vụ hoàn thành khi có retry hoặc short-circuit.

**C. Độ bao phủ và hồi quy**

- Unit test của core để bảo vệ quy tắc domain.
- Test integration trên đường được instrument để khẳng định advice.
- Negative test cho input lỗi và pointcut không khớp.
- So sánh kết quả, ngoại lệ, số sự kiện và thứ tự khi điều kiện thay đổi.
- Dùng công cụ coverage dòng/nhánh cho code, kết hợp ma trận join point/advice vì 100% line coverage không tự bảo đảm pointcut đúng.

**D. Hiệu năng sau khi đúng chức năng**

- Đo số lần chạy advice trước/sau: hai dòng code được gom thành một vị trí định nghĩa không đồng nghĩa runtime chạy ít hơn.
- Đo overhead của proxy/weaving và context trên những hot path thực tế.
- Tránh đặt I/O đồng bộ hoặc dựng stack trace ở mọi invocation để “xác nhận AOP hoạt động”.
- Nếu cần, dùng JMH cho microbenchmark và đo tải thực tế cho throughput, allocation, p95/p99.

Trong ví dụ đã cho, bản trước và bản sau đều tạo **ba dấu enter, ba dấu exit và hai dòng audit thành công** khi chạy ba lượt. Mục tiêu của refactor ở đây là **tập trung hóa trách nhiệm và giảm tangling**, không phải cắt bớt các sự kiện bắt buộc. Mức overhead của bản sau có thể khác vì proxy/reflection; phải đo trước khi tuyên bố phiên bản sau nhanh hơn.

## 12. Những trường hợp không nên đưa ngay vào aspect

**Quy tắc tính toán nghiệp vụ:** việc tính `count * 10` và `20 - units` khác nhau theo miền domain. Ép chúng vào một advice chỉ vì đều có phép toán là làm mờ trách nhiệm, không giải quyết scattering thực chất.

**Validation gắn với bất biến object:** nhiều kiểm tra dữ liệu thuộc domain model hoặc API contract. Nếu tách khỏi nơi dữ liệu có thể được tạo/sửa mà không có cơ chế bao phủ đáng tin, invariant dễ bị bỏ qua.

**Quy trình nhiều bước có trạng thái:** không phải mọi thao tác khởi tạo, kiểm tra, lưu và xác nhận đều là cross-cutting concern; đôi khi đó là orchestration nghiệp vụ. Tách một khâu cốt lõi vào advice có thể khiến người đọc không thấy quy trình quan trọng.

**Concern không thực sự đồng nhất:** audit giữa các nghiệp vụ có thể chia sẻ cơ chế phát sự kiện nhưng có payload, mức độ bắt buộc, thời điểm commit và cách xử lý lỗi khác nhau. Nếu cố gộp tất cả thành một advice với nhiều điều kiện phân nhánh, chính advice sẽ trở thành một khối tangling mới.

**Điểm chặn không bảo đảm:** nếu cơ chế proxy không thấy self-invocation hoặc đường gọi trực tiếp, không chuyển một kiểm tra bảo mật quan trọng vào interception mà không giải quyết ranh giới này. Có trường hợp explicit wrapper/decorator hoặc policy domain rõ ràng dễ kiểm chứng hơn.

AOP là một cách tổ chức *cross-cutting concerns*, không phải câu trả lời tự động cho mọi duplicate code.

## 13. Quy trình nhận diện trong codebase lớn

Có thể áp dụng bảy bước:

1. **Lập danh sách concern.** Gọi tên chính xác: audit thành công, log lỗi, trace duration, xác thực quyền, transaction boundary, cache invalidation, chứ không gom mọi thứ vào nhãn “infrastructure”.
2. **Đánh dấu vị trí.** Tìm các mẫu lời gọi/annotation/hook có cùng mục đích trên nhiều lớp. Dùng tìm kiếm mã để lập một bảng vị trí, nhưng đọc ngữ cảnh trước khi kết luận.
3. **Vẽ ma trận concern × module.** Trục ngang là các lớp/phương thức; trục dọc là concern. Một hàng xuất hiện ở nhiều cột cho thấy nguy cơ scattering; nhiều concern trong một cột cho thấy nguy cơ tangling.
4. **Phân biệt policy chung và dữ liệu đặc thù.** Quy tắc “log enter/exit trong `finally`” có thể dùng chung; tên sự kiện audit hoặc cấu trúc payload có thể vẫn cần khai báo ở nghiệp vụ.
5. **Xác định join point và tập pointcut mong đợi.** Viết cả positive và negative case, đặc biệt với overload, gọi nội bộ, ngoại lệ và concurrency.
6. **Thiết kế lifecycle và kiểm thử.** Xác định advice chạy trước/sau, có được short-circuit/retry không, tương tác với transaction và cleanup ra sao. Lưu baseline trước khi đổi.
7. **Đánh giá tác động.** So sánh số vị trí phải sửa khi thay policy, mức độ dễ đọc của core, test bảo vệ semantics và chi phí runtime. Nếu cấu trúc mới khó debug hoặc overhead lớn mà lợi ích nhỏ, cân nhắc giải pháp đơn giản hơn.

### Ví dụ ma trận concern × vị trí

| Concern \ Vị trí | `Invoice.issue` | `Stock.reserve` | `Invoice.calculateTax` | Interceptor chung |
|---|---|---|---|---|
| Tính tiền/tồn kho | Tính tổng | Giảm tồn kho | Tính thuế | Không |
| Tracing | Có (bản trước) | Có (bản trước) | Không cần nếu policy không yêu cầu | Có (bản sau) |
| Audit thành công | Có (bản trước) | Có (bản trước) | Không cần | Có (bản sau) |
| Kiểm tra invariant domain | Số lượng dương | Trong giới hạn | Quy tắc thuế | Không |

Ma trận giúp nhìn ra **hàng tracing/audit rải nhiều cột** là scattering và **hai cột đầu kết hợp nhiều hàng concern** là tangling. Bản sau vẫn cần cấu hình chính xác các sự kiện, không được dùng một pointcut quá rộng đến mức can thiệp mọi hàm tính toán.

## 14. Những lỗi nhận diện và refactor thường gặp

1. **“Scattering nghĩa là code được copy-paste y hệt.”** Không đủ. Cùng một concern có thể dùng nhiều câu lệnh khác nhau ở nhiều lớp.
2. **“Tangling nghĩa là method có quá nhiều dòng.”** Sai trọng tâm. Một method ngắn trộn bảo mật, nghiệp vụ và logging vẫn có thể tangling.
3. **“Mọi validation đều là aspect.”** Không. Domain validation gắn với bất biến riêng nên thường phải ở ranh giới domain phù hợp.
4. **“Tách mã trùng là đã giải quyết scattering.”** Chỉ tạo helper chung có thể vẫn phải chèn lệnh gọi helper ở hàng trăm phương thức; chính sách có thể còn bị phân tán theo điểm kích hoạt.
5. **“Tách vào AOP thì mọi lời gọi đều tự được chặn.”** Sai. Khả năng chặn phụ thuộc proxy, weaving, class loader, self-invocation và phạm vi join point.
6. **“Audit có thể chạy trong `finally` mà không đổi nghĩa.”** Audit success ở `finally` sẽ chạy cả khi nghiệp vụ lỗi; cần tách success/failure theo hợp đồng.
7. **“Trước và sau đều in đúng output một lần thì luôn tương đương.”** Chưa đủ. Cần thêm exception, concurrency, retry, side effect và đường gọi khác.
8. **“Một advice thật lớn giải quyết mọi concern.”** Có thể chỉ chuyển tangling từ domain sang advice; nên xác định chính sách và thứ tự độc lập.
9. **“AOP luôn tăng hiệu năng.”** AOP giúp tổ chức code; proxy, reflection, matching, allocation và I/O có thể thêm overhead. Tối ưu runtime cần đo.
10. **“Giảm số lời gọi advice là ưu tiên hơn yêu cầu chức năng.”** Nếu audit hoặc kiểm tra quyền bắt buộc, không được bỏ chỉ vì tần suất cao.
11. **“Scattering chỉ gây phiền khi thêm tính năng.”** Nó cũng gây rủi ro sửa thiếu, lỗi chính sách khác nhau giữa các module và khó chứng minh độ bao phủ.
12. **“Không cần negative test khi pointcut rõ ràng.”** Pointcut rộng có thể chặn nhầm getter/helper và tạo audit thừa, latency hoặc side effect ngoài mong muốn.

## 15. Bài tập mở rộng và tiêu chí đánh giá

**Bài tập mở rộng 1 — thay đổi yêu cầu audit:** sửa chính sách từ “chỉ ghi thành công” thành “ghi thành công hoặc lỗi, với loại sự kiện khác nhau”. Đề xuất vị trí xử lý, payload, exception semantics và test tương ứng. Không viết nhãn success cho trường hợp thất bại.

**Bài tập mở rộng 2 — tách hai interceptor:** thiết kế `TracingInterceptor` và `AuditInterceptor` hoạt động theo chuỗi, xác định rõ thứ tự. Thử một interceptor ném lỗi trong pha after, giải thích caller nhìn thấy gì và cleanup còn chạy không.

**Bài tập mở rộng 3 — pointcut hẹp:** thêm phương thức thuần `preview` không cần audit; kiểm chứng tracing/audit không chạy khi gọi `preview` nếu policy chỉ quan tâm hành động thay đổi trạng thái. Nếu vẫn qua proxy handler, phân biệt chi phí dispatch với việc advice thực sự chạy.

**Bài tập mở rộng 4 — đường gọi nội bộ:** cho `InvoiceCore.execute()` gọi một helper cùng đối tượng. So sánh khả năng chặn helper qua proxy với trường hợp có weaving tương thích. Không khẳng định mọi cơ chế có kết quả giống nhau.

**Bài tập mở rộng 5 — đo tác động bảo trì:** giả sử có 30 dịch vụ đều cần đổi định dạng trace. Đếm số điểm định nghĩa chính sách cần sửa ở bản cũ so với bản có tracing được tập trung hóa. Ngoài số dòng, đánh giá độ bao phủ, tính rõ ràng khi gỡ lỗi và chi phí của lớp trừu tượng mới.

### Rubric tự chấm

| Mức đạt | Dấu hiệu |
|---|---|
| Chưa đạt | Gọi mọi đoạn lặp là aspect; nhầm scattering với tangling hoặc không chỉ ra dòng cụ thể |
| Đạt cơ bản | Xác định đúng tracing/audit rải ở hai dịch vụ; chỉ ra nhiều concern trong một method |
| Khá | Tách đúng nghiệp vụ và xử lý xuyên suốt, giải thích đường thành công/lỗi và pointcut |
| Tốt | Đề xuất refactor kiểm chứng được; giữ kết quả, exception, audit count, tracing `finally`; có test phủ định |
| Rất tốt | Phân biệt ý nghĩa nghiệp vụ của audit với cơ chế chung, xét self-invocation, proxy/weaving, concurrency, retry và trade-off hiệu năng/bảo trì |

## 16. Câu hỏi tự kiểm tra

**1. Cùng một concern xuất hiện trong 15 lớp là gì?** Đó là dấu hiệu scattering nếu các vị trí thực sự có cùng lý do thay đổi. Cần chứng minh concern và phạm vi, không dựa vào trùng chuỗi đơn thuần.

**2. Một phương thức gồm nghiệp vụ, tracing và audit là gì?** Có nguy cơ tangling vì nhiều trách nhiệm đan xen; mức độ đáng tách phụ thuộc tính độc lập và yêu cầu hệ thống.

**3. Trong mã mẫu, đâu là bằng chứng scattering rõ nhất?** Hai cặp trace `I1/I6` và `S1/S6` cùng chính sách, và hai lệnh audit `I4/S4` nằm ở các dịch vụ khác nhau.

**4. Có nên chuyển `count <= 0` và `units > 20` vào cùng một advice?** Không chỉ vì cả hai là `if`. Chúng bảo vệ những bất biến domain khác nhau; cấu trúc tốt phải giữ chúng được kiểm tra ở biên đáng tin.

**5. Vì sao audit success không nằm trong `finally`?** Vì `finally` chạy cả khi ném ngoại lệ. Audit success đặt sai chỗ sẽ ghi một giao dịch lỗi là thành công.

**6. Refactor đã loại bỏ hoàn toàn scattering chưa?** Nó tập trung hóa cơ chế tracing/audit của ví dụ, nhưng dữ liệu sự kiện và các cấu hình áp dụng vẫn hiện diện ở biên thích hợp. Tính đúng đắn còn phụ thuộc mọi đường gọi cần can thiệp đều đi qua cơ chế.

**7. Sau refactor, logic nghiệp vụ có thay đổi không?** Với các ca được chạy, kết quả/exception/stdout là như nhau. Để khẳng định hoàn toàn tương đương còn cần thêm kiểm thử cho đường gọi và trạng thái khác.

**8. Vì sao “ít code hơn” chưa đủ là bằng chứng thiết kế tốt?** Có thể làm mất chính sách bắt buộc, che giấu hành vi, làm khó debug hoặc thêm overhead. Chất lượng đánh giá theo sự phân tách trách nhiệm và các bất biến được giữ.

**9. Một helper tĩnh dùng chung có giải quyết được mọi vấn đề không?** Không. Nó gom implementation nhưng điểm gọi helper vẫn phân tán; AOP có thể gom cả quy tắc kích hoạt nếu join point và pointcut phù hợp. Trong vài bài toán, helper lại dễ hiểu và đủ tốt.

**10. Kết luận chính từ chương trình mẫu?** Phân tích scattering theo trục **một concern — nhiều vị trí**, tangling theo trục **một vị trí — nhiều concern**. Tách tracing/audit chỉ có giá trị nếu giữ đúng điều kiện, thứ tự, outcome và phạm vi chặn.

**Ghi nhớ:** Bước đầu của thiết kế AOP không phải viết advice, mà là *phân loại các lý do thay đổi trong mã hiện tại*. Xác định bằng chứng scattering/tangling, giữ nghiệp vụ ở nơi thuộc về nó, chọn join point rõ ràng và dùng kiểm thử đầu ra, ngoại lệ cùng side effect để xác nhận sự tách biệt thực sự an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-002">Thiết kế aspect ghi nhật ký với phạm vi chọn và loại trừ rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Bài tập tổng hợp: Thiết kế aspect ghi nhật ký với phạm vi chọn và loại trừ rõ ràng

## 1. Mục tiêu bài tập

Bài tập này yêu cầu xây dựng một **logging aspect** trong lập trình hướng khía cạnh (*Aspect-Oriented Programming – AOP*) với chính sách chọn join point có chủ đích. Kết quả không chỉ phải ghi log cho những phương thức được phép, mà còn phải **chứng minh những phương thức bị loại trừ không sinh log**.

Sau khi hoàn thành, người học cần:

1. Phân biệt **join point**, **pointcut**, **advice**, **weaving** và lời gọi bình thường của chương trình.
2. Thiết kế pointcut có **phạm vi dương (inclusion)** và **phạm vi âm (exclusion)**, với lý do cho từng điều kiện.
3. Tránh cách viết wildcard quá rộng như `execution(* *(..))` và tránh lệ thuộc hoàn toàn vào tên phương thức thay đổi theo refactor.
4. Dùng metadata có chủ đích để biểu thị phương thức được phép ghi log; vẫn có cơ chế **từ chối ghi log rõ ràng** ngay cả khi đã được đánh dấu.
5. Giữ đúng giá trị trả về, ngoại lệ và luồng nghiệp vụ; không để advice tự kích hoạt vô hạn.
6. Viết kiểm thử có cả **must-match** và **must-not-match**, xem weave report và chạy đúng artifact đã dệt.
7. Giải thích được chi phí hiệu năng, quy tắc không ghi dữ liệu nhạy cảm và giới hạn của cơ chế AOP đã chọn.

Ví dụ thực hành dùng **Java 17+ với AspectJ compiler/weaver độc lập** làm một hiện thực của AOP ở mức bytecode. Điều kiện pointcut và tập join point hỗ trợ có thể khác ở các cơ chế AOP khác; bài không giả định mọi bộ proxy/interceptor đều có khả năng chọn method execution như bytecode weaving.

## 2. Đề bài thực tế

Giả sử một ứng dụng quản lý đơn hàng có ba loại thao tác:

- **Thao tác nghiệp vụ được kiểm toán:** xác nhận đơn, tác vụ có thể thất bại. Cần log lúc bắt đầu, khi thành công và khi có ngoại lệ.
- **Thao tác bình thường hoặc được miễn trừ:** xem trước đơn, xử lý bảo trì không muốn ghi log. Chúng không được tạo dòng audit dù nằm cùng lớp nghiệp vụ.
- **Thao tác kỹ thuật nội bộ:** phương thức ở package nội bộ có thể mang marker ghi log, nhưng toàn bộ package đó thuộc vùng loại trừ.

Thiết kế aspect mang tên `AuditAspect`. Thay vì ghi log toàn bộ package, bài tập dùng **hai marker annotation** và một điều kiện package exclusion.

**Quy tắc chọn:** một method execution chỉ được ghi log nếu **đồng thời**:

1. Là phương thức `public` của kiểu thuộc `demo.shop.service` hoặc package con.
2. Mang annotation `@Logged`.
3. **Không** mang annotation `@NoLog`.
4. **Không** thuộc package `demo.shop.service.internal` hoặc các package con.

Những điểm này là một phép giao: nếu một điều kiện không thỏa, advice không được chạy. Để tránh quan niệm sai, marker `@Logged` là ý định ghi log do lập trình viên công bố, **không phải** cơ chế weaving tự động; chỉ pointcut + trình weave mới nối marker với advice.

**Quy tắc ghi log:** tại mỗi execution join point được chọn, ghi một dòng `AUDIT START: <method>` trước khi chạy. Nếu method hoàn tất bình thường, ghi `AUDIT OK: <method>`; nếu thoát bằng ngoại lệ, ghi `AUDIT ERROR: <method> - <exception-class>`. Không ghi giá trị tham số, kết quả, stack trace hoặc dữ liệu nhạy cảm trong ví dụ.

**Quy tắc nghiệp vụ:** mã nghiệp vụ vẫn thực thi đúng một lần. Result không bị thay đổi; ngoại lệ không bị nuốt hoặc chuyển thành trạng thái thành công. Advice chỉ thực hiện ghi log minh họa vào stdout.

### 2.1. Ma trận chấp nhận

| Join point | @Logged | @NoLog | Vị trí | Mong đợi |
|---|---:|---:|---|---|
| `OrderService.submit(String)` | Có | Không | `service` | **Có log START + OK** |
| `OrderService.preview(String)` | Không | Không | `service` | **Không log** |
| `OrderService.maintenance(String)` | Có | Có | `service` | **Không log** vì marker loại trừ |
| `InternalJobs.ping()` | Có | Không | `service.internal` | **Không log** vì package loại trừ |
| `OrderService.fail()` | Có | Không | `service` | **Có log START + ERROR**, giữ ngoại lệ |
| Mọi method khác không thỏa đủ điều kiện | Bất kỳ | Bất kỳ | Bất kỳ | **Không log** |

Đây là tiêu chí nghiệm thu. Một lời giải chỉ in được log ở `submit` nhưng vẫn in thêm ở `preview` thì **chưa đạt**.

## 3. Phân tích trước khi viết mã

### 3.1. Vì sao phải tách positive scope và exclusions?

Một biểu thức kiểu `execution(public * demo.shop.service..*.*(..))` có thể chọn quá nhiều phương thức chỉ vì chúng được đặt trong cùng package. Một biểu thức kiểu `execution(* *.submit(..))` có thể ngừng hoạt động sau khi đổi tên `submit` thành `confirm`. Kết hợp **phạm vi loại/type** với **annotation mang ý nghĩa audit** làm điều kiện chính sẽ rõ ràng hơn.

Ngược lại, chỉ dùng `@Logged` cũng chưa đủ: một marker có thể bị copy vào helper hoặc một lớp nội bộ mới. Điều kiện `!within(demo.shop.service.internal..*)` bảo vệ rõ ràng ranh giới kỹ thuật. `@NoLog` cung cấp một ngoại lệ có thể review tại chính nơi khai báo phương thức.

Với bài này, precedence về ý nghĩa là **quy tắc kết hợp bằng AND và phủ định**: `@NoLog` luôn khiến method không match, kể cả khi có `@Logged`.

### 3.2. Vì sao dùng method execution?

Mục tiêu là ghi log mỗi lần **thân phương thức nghiệp vụ thực sự được thực thi**. Pointcut `execution(...)` trong AspectJ phù hợp với mục tiêu đó: weave advice tại method execution join point. `call(...)` chọn điểm gọi và có đặc tính khác khi mã gọi nằm ngoài phạm vi weaving. Một interception qua proxy còn phụ thuộc việc lời gọi có đi qua proxy hay không. Khi chuyển cơ chế AOP, phải xác minh lại semantics thay vì sao chép pointcut theo tên.

### 3.3. Logging aspect phải làm việc gì?

- Lấy thông tin tên phương thức qua join point metadata, **không** serialize toàn bộ `args`.
- Ghi rõ START/OK/ERROR và giữ đúng thứ tự bao quanh nghiệp vụ.
- Đảm bảo code logging tự nó không nằm trong tập method được chọn. Trong bài này, aspect ở package `demo.shop.aspect`, nằm ngoài positive scope `demo.shop.service..*`.
- Không gọi lại phương thức target từ advice. Nếu cần `around advice`, phải hiểu rõ số lần `proceed()`; với yêu cầu hiện tại, advice before/after là đủ.

## 4. Bộ mã nguồn Java 17+ có thể chạy

Làm bài độc lập trong thư mục `aop-logging-lab`. Sử dụng cấu trúc:

```text
aop-logging-lab/
├── src/
│   └── demo/
│       └── shop/
│           ├── Main.java
│           ├── audit/
│           │   ├── Logged.java
│           │   └── NoLog.java
│           ├── aspect/
│           │   └── AuditAspect.aj
│           └── service/
│               ├── OrderService.java
│               └── internal/
│                   └── InternalJobs.java
└── tools/
    ├── aspectjtools-1.9.24.jar
    └── aspectjrt-1.9.24.jar
```

Các đoạn `.java` là mã Java hợp lệ; `AuditAspect.aj` là cú pháp aspect của AspectJ và phải được biên dịch bằng **`ajc`**, không bằng `javac` thuần. Các bản JAR ví dụ dùng cùng phiên bản **AspectJ 1.9.24**; xác nhận toolchain phù hợp với môi trường Java trước khi dùng vào một dự án thật.

### 4.1. Marker cho phương thức phải được ghi log

Tạo `src/demo/shop/audit/Logged.java`:

```java
package demo.shop.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Logged {
}
```

Marker này nói rằng một method *có thể* được audit, tùy thêm các giới hạn pointcut. `RetentionPolicy.RUNTIME` giữ annotation trong metadata runtime; một số hình thức weaving có thể xử lý annotation ở thời điểm compile và có yêu cầu riêng, nhưng dùng retention rõ ràng tránh nhầm rằng mọi annotation đều tồn tại lúc chạy.

### 4.2. Marker loại trừ có chủ đích

Tạo `src/demo/shop/audit/NoLog.java`:

```java
package demo.shop.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoLog {
}
```

Method có đồng thời `@Logged` và `@NoLog` phải **bị loại** khỏi logging. Không suy ra mọi annotation trên method tự động được kế thừa khi override; code production phải kiểm tra vị trí annotation và ngữ nghĩa matcher trên implementation cụ thể.

### 4.3. Lớp nghiệp vụ và bốn hành vi đối chứng

Tạo `src/demo/shop/service/OrderService.java`:

```java
package demo.shop.service;

import demo.shop.audit.Logged;
import demo.shop.audit.NoLog;

public final class OrderService {
    @Logged
    public String submit(String id) {
        return "SUBMITTED:" + id;
    }

    public String preview(String id) {
        return "PREVIEW:" + id;
    }

    @Logged
    @NoLog
    public String maintenance(String id) {
        return "MAINTENANCE:" + id;
    }

    @Logged
    public void fail() {
        throw new IllegalStateException("rejected");
    }
}
```

Quan sát:

- `submit` và `fail` thỏa điều kiện chọn.
- `preview` không có marker nên không chọn.
- `maintenance` cố tình mang **cả hai marker** để buộc kiểm tra exclusion thực tế. Trong sản phẩm thật, hãy cân nhắc vì sao exception được phép thay vì vô tình sao chép annotation.

### 4.4. Package nội bộ có marker nhưng không được log

Tạo `src/demo/shop/service/internal/InternalJobs.java`:

```java
package demo.shop.service.internal;

import demo.shop.audit.Logged;

public final class InternalJobs {
    @Logged
    public String ping() {
        return "PONG";
    }
}
```

`ping` mang `@Logged` và nằm bên dưới `demo.shop.service`. Nếu chỉ dựa vào positive scope và annotation, method sẽ match. Điều kiện `!within(demo.shop.service.internal..*)` phải loại chính method này.

### 4.5. Aspect chứa điều kiện chọn và loại trừ

Tạo `src/demo/shop/aspect/AuditAspect.aj`:

```java
package demo.shop.aspect;

public aspect AuditAspect {
    pointcut selectedServiceMethod():
        execution(public * demo.shop.service..*.*(..))
        && @annotation(demo.shop.audit.Logged)
        && !@annotation(demo.shop.audit.NoLog)
        && !within(demo.shop.service.internal..*);

    before(): selectedServiceMethod() {
        System.out.println(
            "AUDIT START: " + thisJoinPointStaticPart.getSignature().getName());
    }

    after() returning: selectedServiceMethod() {
        System.out.println(
            "AUDIT OK: " + thisJoinPointStaticPart.getSignature().getName());
    }

    after() throwing(Throwable error): selectedServiceMethod() {
        System.out.println(
            "AUDIT ERROR: " + thisJoinPointStaticPart.getSignature().getName()
            + " - " + error.getClass().getSimpleName());
    }
}
```

**Giải nghĩa từng nhánh:**

| Điều kiện | Ý nghĩa | Nếu bỏ điều kiện |
|---|---|---|
| `execution(public * demo.shop.service..*.*(..))` | Chỉ public method execution của các kiểu trong namespace nghiệp vụ | Có nguy cơ chặn method ngoài ranh giới này |
| `@annotation(demo.shop.audit.Logged)` | Method phải được đánh dấu có chủ đích | `preview` và các method public khác trong namespace cũng có thể bị ghi log |
| `!@annotation(demo.shop.audit.NoLog)` | Marker từ chối có ưu tiên loại trừ | `maintenance` có thể bị ghi log |
| `!within(demo.shop.service.internal..*)` | Loại toàn bộ mã nằm trong package nội bộ và cấp con | `InternalJobs.ping` sẽ bị ghi log |

`&&` là phép giao và `!` là phủ định pointcut. Trong cú pháp AspectJ này, `..` ở type pattern cho phép xét các package con; cần kiểm tra điểm khớp thực bằng weave report. Cách dùng `within` ở đây chỉ có ý nghĩa với method execution thuộc lexical type phù hợp, không có nghĩa nó tự động áp dụng chính sách cho mọi class loader hoặc công cụ AOP khác.

**Vì sao ba advice?** `before` chạy trước thân method; `after() returning` chỉ chạy khi thoát bình thường; `after() throwing` chạy khi method thoát bởi ngoại lệ phù hợp. Như vậy không cần `around` chỉ để ghi log, đồng thời giữ nguyên luồng trả về/ngoại lệ mà không phải tự gọi `proceed`.

Với ví dụ đơn luồng và in stdout trực tiếp, thứ tự log là xác định. Trong ứng dụng nhiều luồng, log giữa các request có thể xen nhau; test không nên suy luận tính đúng chỉ dựa vào thứ tự dòng log toàn hệ thống.

### 4.6. Entry point để quan sát kết quả

Tạo `src/demo/shop/Main.java`:

```java
package demo.shop;

import demo.shop.service.OrderService;
import demo.shop.service.internal.InternalJobs;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        OrderService orders = new OrderService();
        InternalJobs internal = new InternalJobs();

        System.out.println(orders.submit("A-17"));
        System.out.println(orders.preview("A-17"));
        System.out.println(orders.maintenance("M-1"));
        System.out.println(internal.ping());

        try {
            orders.fail();
        } catch (IllegalStateException exception) {
            System.out.println("CAUGHT:" + exception.getMessage());
        }
    }
}
```

Code này không gọi `AuditAspect` bằng tay. Khi class đã được weave đúng, các method `submit` và `fail` tự kích hoạt advice tại execution join point.

## 5. Biên dịch và weave từ mã nguồn

Cần có JDK 17+ và hai JAR AspectJ cùng phiên bản. Ví dụ lệnh bên dưới dùng **POSIX shell** (Linux/macOS/Git Bash phù hợp). Lấy compiler và runtime từ Maven Central:

```bash
mkdir -p tools build/woven

curl -fLsS \
  https://repo.maven.apache.org/maven2/org/aspectj/aspectjtools/1.9.24/aspectjtools-1.9.24.jar \
  -o tools/aspectjtools-1.9.24.jar

curl -fLsS \
  https://repo.maven.apache.org/maven2/org/aspectj/aspectjrt/1.9.24/aspectjrt-1.9.24.jar \
  -o tools/aspectjrt-1.9.24.jar
```

Tiếp theo chạy AspectJ compiler với target Java 17. Bắt đầu từ output **sạch**, ví dụ xóa riêng `build/woven` của lab trước lần chạy mới khi chắc chắn không có dữ liệu cần giữ. Lệnh sau giả định `build/woven` chưa chứa class từ lần build cũ:

```bash
java -cp tools/aspectjtools-1.9.24.jar \
  org.aspectj.tools.ajc.Main \
  -17 -encoding UTF-8 \
  -classpath tools/aspectjrt-1.9.24.jar \
  -showWeaveInfo \
  -d build/woven \
  src/demo/shop/audit/Logged.java \
  src/demo/shop/audit/NoLog.java \
  src/demo/shop/service/OrderService.java \
  src/demo/shop/service/internal/InternalJobs.java \
  src/demo/shop/Main.java \
  src/demo/shop/aspect/AuditAspect.aj
```

Các điểm phải kiểm chứng:

1. Exit code của compiler bằng `0`; không có lỗi syntax/classpath.
2. `-showWeaveInfo` báo advice được dệt vào join point của `submit` và `fail`.
3. Không có advice khớp `preview`, `maintenance`, `InternalJobs.ping`.
4. Các classfile đầu ra thực sự nằm trong `build/woven`; quá trình chạy phải lấy từ đây.
5. Compiler version và target JDK thống nhất trên CI.

Không nên phụ thuộc chính xác từng câu chữ trong weave report bởi format log có thể khác giữa các bản AspectJ; hãy xác minh **tập join point** và kiểm thử hành vi.

Chạy:

```bash
java -cp "build/woven:tools/aspectjrt-1.9.24.jar" demo.shop.Main
```

**Stdout mong đợi của ứng dụng**:

```text
AUDIT START: submit
AUDIT OK: submit
SUBMITTED:A-17
PREVIEW:A-17
MAINTENANCE:M-1
PONG
AUDIT START: fail
AUDIT ERROR: fail - IllegalStateException
CAUGHT:rejected
```

Compiler có thể in thông báo weaving riêng **trong bước build**, nhưng không thuộc stdout trên khi chạy chương trình.

### 5.1. Giải thích stdout

- Hai dòng `AUDIT START: submit` và `AUDIT OK: submit` xuất hiện **trước** khi `Main` in result từ lời gọi `submit`.
- `preview` vẫn trả về đúng giá trị nghiệp vụ, nhưng **không có** dòng `AUDIT` dành cho `preview`.
- `maintenance` nằm trong namespace được chọn và có marker ghi log, nhưng **bị loại trừ tường minh** bằng `@NoLog`.
- `ping` cũng mang marker nhưng bị loại vì namespace `internal`. Vì thế chỉ thấy `PONG`.
- `fail` ném ngoại lệ: có `START` và `ERROR`, **không** có `OK`; code gọi vẫn bắt được đúng `IllegalStateException` với message `rejected`.
- Có đúng **hai method được audit** trong ví dụ, mỗi lần gọi tạo hai dòng log, tổng cộng bốn dòng `AUDIT`.

Chỉ cần một dòng audit xuất hiện cho method bị loại hoặc một dòng audit bắt buộc bị thiếu thì testcase nghiệm thu phải fail.

## 6. Biến kiểm thử tự động: match và no-match

### 6.1. So sánh toàn bộ kết quả

Sau khi compile/weave thành công, trên POSIX shell có thể dùng:

```bash
actual=$(java -cp \
  "build/woven:tools/aspectjrt-1.9.24.jar" \
  demo.shop.Main)

expected=$(printf '%s\n' \
  'AUDIT START: submit' \
  'AUDIT OK: submit' \
  'SUBMITTED:A-17' \
  'PREVIEW:A-17' \
  'MAINTENANCE:M-1' \
  'PONG' \
  'AUDIT START: fail' \
  'AUDIT ERROR: fail - IllegalStateException' \
  'CAUGHT:rejected')

test "$actual" = "$expected"
```

Mã thoát của `test` bằng `0` khi output khớp. So sánh toàn bộ chuỗi có ích cho **ví dụ đơn luồng, output xác định**: nó bắt được cả trường hợp thiếu log và trường hợp ghi log thừa. Với ứng dụng thật, nên đưa log vào sink có thể quan sát/assert từng event bằng metadata được chuẩn hóa, tránh phụ thuộc thứ tự xen kẽ của nhiều thread hay format log bên ngoài.

Để kiểm thử riêng các tiêu chí:

- **Must match:** `submit` và `fail` tạo đúng cặp event yêu cầu.
- **Must not match theo thiếu annotation:** `preview` không tạo event.
- **Must not match theo marker phủ định:** `maintenance` không tạo event.
- **Must not match theo package phủ định:** `InternalJobs.ping` không tạo event.
- **Không thay đổi semantics:** giá trị trả về và ngoại lệ vẫn như khi không áp dụng advice.

### 6.2. Test sau khi đóng gói artifact

Gói class đã dệt:

```bash
jar --create \
  --file build/aop-logging-lab.jar \
  -C build/woven .

jar --list --file build/aop-logging-lab.jar
```

Phải thấy các entry quan trọng như:

```text
demo/shop/Main.class
demo/shop/service/OrderService.class
demo/shop/service/internal/InternalJobs.class
demo/shop/aspect/AuditAspect.class
demo/shop/audit/Logged.class
demo/shop/audit/NoLog.class
```

JAR có thể có manifest và metadata khác; danh sách không bắt buộc phải có đúng thứ tự trên. Chạy **chính JAR đã đóng gói**:

```bash
java -cp "build/aop-logging-lab.jar:tools/aspectjrt-1.9.24.jar" \
  demo.shop.Main
```

Expected stdout giống mục 5.1. Trên Windows, classpath separator là `;` thay vì `:`. Việc đóng gói JAR **không tự ghép** runtime `aspectjrt` vào trong, vì vậy phải phân phối nó hoặc chuẩn bị launcher/package mang đủ phụ thuộc.

Kiểm tra bytecode có thể dùng:

```bash
javap -classpath build/aop-logging-lab.jar \
  -c -p demo.shop.service.OrderService
```

Ở các method được weave, compiler có thể chèn call tới aspect/helper. Tên method phụ trợ và layout instruction có thể thay đổi theo phiên bản; **đừng chỉ grep một tên helper cố định**. Kết hợp weave report, kiểm tra JAR cuối và chạy ứng dụng là bằng chứng mạnh hơn kiểm tra bytecode đơn lẻ.

## 7. Thử nghiệm thay đổi từng điều kiện pointcut

Đây là phần quan trọng của bài tập tổng hợp: quan sát việc mở rộng/thu hẹp tập join point khi thay đổi **một điều kiện mỗi lần**. Mỗi biến thể cần biên dịch lại output sạch và chạy lại bảng nghiệm thu.

### 7.1. Bỏ `@annotation(Logged)`

Nếu pointcut chỉ còn chọn public method trong `service`, ngoại trừ `@NoLog` và `internal`, `preview` sẽ bị log dù không thuộc hợp đồng. Đây là lỗi **overmatching**. Với logic audit, nguy cơ còn là leak tham số nếu advice thực tế ghi cả args.

### 7.2. Bỏ `!@annotation(NoLog)`

`maintenance` sẽ được log vì nó mang `@Logged` và thuộc package positive. Thử nghiệm chứng minh một exclusion có tác dụng thật, không chỉ là điều kiện trang trí.

### 7.3. Bỏ `!within(demo.shop.service.internal..*)`

`InternalJobs.ping` sẽ được log vì package con vẫn thuộc `service..*` và method có `@Logged`. Đây là chứng cứ cần thiết để giữ ranh giới nội bộ độc lập với marker.

### 7.4. Giới hạn bằng tên phương thức thay annotation

Thử thay `@annotation(Logged)` bằng một mẫu tên như `execution(* *..*.submit*(..))` (điều chỉnh cú pháp cho weaver). Tên có thể đổi thành `confirm` trong một lần refactor nhưng ý nghĩa audit vẫn còn. Nếu không có test bảo vệ, advice sẽ biến mất âm thầm. Marker diễn đạt ngữ nghĩa tốt hơn trong trường hợp này, dù vẫn phải được kiểm soát khi đổi code.

### 7.5. Đổi `execution` thành `call`

Đây không phải một phép thay thế tương đương. `execution` bắt ở thân method được weave; `call` bắt tại call site phù hợp được weave. Một lời gọi phát sinh từ thư viện/class không được weave có thể không bị advice `call` chặn, dù target execution vẫn chạy. Chỉ thử khi hiểu phạm vi weaving và pointcut designator được công cụ hỗ trợ.

### 7.6. Thêm phương thức mới

Thêm một public method không có marker vào `OrderService`; xác nhận không log. Sau đó thêm `@Logged` cho method mới; xác nhận log. Cuối cùng thêm `@NoLog`; xác nhận lại không log. Chuỗi thử nghiệm cho thấy chính **hợp đồng annotation và exclusion**, không phải tên method ngẫu nhiên, quyết định chính sách.

**Lưu ý:** với các biến thể cố tình làm sai, bộ test chuẩn phải **fail**. Đó là tín hiệu mong muốn chứng minh test phát hiện lỗi, không phải lý do sửa “expected output” để mọi biến thể đều pass.

## 8. Bài nâng cao: tăng độ tin cậy của logging aspect

### 8.1. Không để dữ liệu nhạy cảm lọt vào log

Ví dụ in **chỉ tên method**. Một hệ thống thực tế có thể thêm correlation ID, thời gian, trạng thái hoặc mã lỗi, nhưng không nên tự động in tất cả `args`, result, token hay exception message. Khi thêm field, cần có chính sách:

- Field nào được phép ghi?
- Dữ liệu có phải che/mask không?
- Log có bị đọc bởi nhiều người hoặc chuyển sang dịch vụ bên ngoài không?
- Giá trị có tạo cardinality không giới hạn hay chứa payload quá lớn không?

Với failure, ví dụ chỉ in **tên lớp exception** để tránh lộ message. Với ứng dụng thật, chọn log level và sink phù hợp, lưu nguyên nhân lỗi ở nơi được kiểm soát khi cần chẩn đoán.

### 8.2. Advice có thể ném ngoại lệ

Ví dụ `System.out.println` đơn giản, nhưng logger thực tế có thể fail do I/O, định dạng, serialize hoặc backpressure. Nếu yêu cầu là “logging hỏng không được phá nghiệp vụ”, cần thiết kế rõ fail-open và cách cô lập lỗi của sink. Nếu audit là bắt buộc về mặt pháp lý hoặc transaction, đôi khi fail-closed lại được lựa chọn. **Đây là chính sách ứng dụng**, không có một quy tắc chung cho mọi aspect.

Tránh viết `catch (Exception ignored)` bao quanh toàn bộ advice mà không hiểu hậu quả: nó có thể che mất sự cố giám sát quan trọng. Test trường hợp logger hỏng để xác nhận chính sách mong muốn.

### 8.3. Logger không nên trở thành một join point của chính nó

Một broad pointcut như `execution(* *(..))` có thể bắt cả method helper bên trong aspect, tạo recursion hoặc log storm, tùy phạm vi dệt. Trong ví dụ, positive scope `demo.shop.service..*` đã loại package `demo.shop.aspect`. Với hệ thống lớn, quy tắc an toàn thường bao gồm giới hạn scope và exclusion cho logging/metrics/tracing infrastructure nếu các lớp đó có nguy cơ nằm trong phạm vi weaving.

Không giải quyết mọi vấn đề chỉ bằng cách thêm dần hàng chục exclusion. Nếu danh sách phủ định quá dài, xem lại positive scope và ranh giới kiến trúc.

### 8.4. Aspect state, concurrency và thứ tự event

Ví dụ không lưu trạng thái dùng chung. Nếu thêm bộ đếm event hoặc context, tránh biến mutable không thread-safe trong singleton aspect. Một chương trình nhiều luồng có thể tạo event xen kẽ, nên cần correlation ID hoặc per-request context phù hợp để ghép START với OK/ERROR. Không lấy thứ tự các dòng log global làm bằng chứng chắc chắn cho một request.

`ThreadLocal` có thể hữu ích trong mô hình thread-per-request, nhưng cần xử lý cleanup và các trường hợp chuyển ngữ cảnh; mô hình task bất đồng bộ hoặc virtual thread có chính sách riêng. Không chuyển tiếp context bằng cách giữ tham chiếu lâu dài vô ý.

### 8.5. Around advice và `proceed`

Yêu cầu hiện tại không cần quanh toàn bộ lời gọi. Nếu chuyển sang `around` để đo duration, code phải gọi `proceed()` theo semantics đã chọn, trả về đúng kết quả, giữ ngoại lệ và dọn context trong `finally`. Dùng `around` nhưng quên `proceed` sẽ làm business method không chạy; gọi `proceed` hai lần có thể tạo giao dịch/side effect lặp. Đây là lỗi lớn hơn việc thiếu một dòng log.

### 8.6. Chi phí ghi log và pointcut động

Weaving có thể chèn mã vào join point, nhưng advice vẫn tiêu thụ CPU/bộ nhớ/I/O. Nếu instrument một getter được gọi hàng triệu lần, tác động có thể lớn dù chỉ in một dòng mỗi lần. Thu hẹp pointcut là tối ưu căn bản.

Các điều kiện động như `args` hoặc `if()` (nếu được hỗ trợ) có thể thêm công việc kiểm tra runtime. Khi có thể, hãy dùng thông tin kiểu/annotation/phạm vi để lọc tĩnh; dùng điều kiện runtime cho các quy tắc thực sự phụ thuộc dữ liệu và kiểm tra chi phí dưới tải đại diện.

## 9. Kiểm tra rủi ro khi thay đổi mã và build

### 9.1. Refactor và marker

Đổi tên `submit` thành `confirm` nhưng giữ marker `@Logged` trên method mới, chạy lại weaving và test. Lúc này tên in trong log thay đổi thành `confirm` vì metadata phản ánh tên mới, nhưng **quyết định có audit** vẫn do marker và phạm vi quyết định. Bộ test cần cập nhật expected name theo thay đổi có chủ đích, đồng thời xác nhận method vẫn được chọn.

Chuyển `submit` ra ngoài `demo.shop.service..*` sẽ làm nó không còn match mặc dù vẫn mang `@Logged`. Nếu thao tác vẫn thuộc nghiệp vụ phải audit, cần xem xét lại **ranh giới scope**, không âm thầm nới toàn bộ pointcut thành `execution(* *(..))`.

Nếu chuyển vào `demo.shop.service.internal`, exclusion có quyền ưu tiên và advice không chạy. Đó là hành vi phù hợp với quy tắc đã phát biểu, nhưng nếu không còn phù hợp nghiệp vụ thì phải cập nhật hợp đồng và test công khai.

### 9.2. Override và annotation

Method annotation trên interface hoặc class cha **không tự động** xuất hiện ở method override như một quy luật Java tổng quát. Cách bộ weaving xác định annotation tại execution join point có thể khác cách một proxy dùng reflection trên interface. Khi đưa marker vào nhiều cấp kế thừa, phải test trên method **thực sự chạy**, tránh chỉ nhìn metadata ở interface.

### 9.3. Build artifact

Một dự án có thể chứa nhiều thư mục lớp: plain classes, woven classes, classes từ test và JAR phát hành. Nếu compiler báo đã weave nhưng đóng gói nhầm plain classes, log sẽ biến mất khi triển khai. Luôn chạy smoke test từ **artifact cuối**. Nếu có load-time weaving, phải kiểm tra cấu hình agent và thời điểm class loader định nghĩa class; không dùng kết quả CTW để suy luận tự động về LTW.

### 9.4. Theo dõi tập match

Giữ một “join point inventory” ở mức đủ cần thiết, ví dụ:

```text
MUST MATCH:
  demo.shop.service.OrderService.submit(String)
  demo.shop.service.OrderService.fail()

MUST NOT MATCH:
  demo.shop.service.OrderService.preview(String)
  demo.shop.service.OrderService.maintenance(String)
  demo.shop.service.internal.InternalJobs.ping()
```

Khi PR làm thay đổi tập match, reviewer cần thấy lý do: nghiệp vụ mới, marker mới hay thay đổi phạm vi? Nếu chỉ đếm số join point, một điểm mất và một điểm thừa có thể triệt tiêu về tổng số nhưng vẫn là lỗi.

## 10. Đề kiểm thử đánh giá cuối bài

Học viên cần tự nộp:

1. Các file mã nguồn, trong đó aspect có named pointcut và đủ các điều kiện chọn/loại trừ.
2. Lệnh biên dịch/weave, phiên bản JDK và weaver, cùng weave report chứng minh điểm được chọn.
3. Artifact đã weave hoặc quy trình build tái tạo được artifact, kèm lệnh chạy runtime.
4. Kết quả kiểm thử must-match và must-not-match trên bảng mục 2.1.
5. Một thử nghiệm cố ý bỏ `@NoLog` hoặc package exclusion, cho thấy test fail vì match thừa.
6. Giải thích ảnh hưởng khi đổi tên method, chuyển package hoặc dùng cơ chế proxy thay vì method-execution weaving.
7. Đề xuất logging production không làm lộ dữ liệu nhạy cảm và không thay đổi semantics của nghiệp vụ.

### 10.1. Tiêu chí chấm

| Tiêu chí | Điểm | Điều kiện đạt |
|---|---:|---|
| Phân tích phạm vi | 20 | Giải thích rõ inclusion/exclusion và đủ trường hợp |
| Pointcut đúng | 20 | Match đúng tập method, không phụ thuộc wildcard toàn cục |
| Advice đúng luồng | 20 | START/OK/ERROR chính xác, exception và return giữ nguyên |
| Kiểm thử dương/âm | 20 | Có test phát hiện cả thiếu log lẫn thừa log |
| Build và đóng gói | 10 | Weave thành công; chạy từ artifact đã weave với runtime đầy đủ |
| Giải thích rủi ro | 10 | Đề cập refactor, bảo mật log, hiệu năng, recursion và giới hạn AOP |

**Đạt yêu cầu** khi test chạy trên artifact đã weave đúng output ở mục 5 và giải thích được từng điều kiện loại trừ. Các điểm chấm giúp tự đánh giá, không thay thế xác minh kỹ thuật.

## 11. Các lỗi thường gặp trong lời giải

**Lỗi: chỉ viết `execution(* demo.shop..*.*(..))`.** Pointcut sẽ chọn cả thao tác chỉ đọc và mã kỹ thuật. Cách sửa là đặt thêm ranh giới service, marker được audit và exclusion có chủ đích.

**Lỗi: dùng `@Logged` nhưng bỏ `@NoLog`.** Một method được đánh dấu song song vẫn bị log; điều kiện loại trừ phải có mặt thực sự trong pointcut và được test.

**Lỗi: viết `!within(demo.shop.service.internal.*)` rồi nghĩ đã loại mọi package con.** Mẫu type/package trong AspectJ cần hiểu chính xác; dùng `..*` cho namespace phân cấp phù hợp và xác nhận qua test class nằm sâu hơn.

**Lỗi: dùng method name làm hợp đồng nghiệp vụ duy nhất.** `submit*(..)` có thể match nhầm các method mới hoặc bỏ sót sau refactor. Marker/ranh giới ổn định cùng test làm ý định rõ hơn.

**Lỗi: chỉ quan sát dòng START.** Nếu logic after-return/after-throw sai, hoặc exception bị nuốt, test vẫn có thể báo pass. Cần assert cả OK/ERROR và nghiệp vụ.

**Lỗi: tự gọi lại target trong advice.** Logging before/after không đòi hỏi gọi method target lần nữa; lặp lời gọi có thể tạo side effect và event kép.

**Lỗi: coi `javac` biên dịch được `.aj`.** `.aj` là cú pháp AspectJ; dùng compiler tương ứng hoặc công cụ hỗ trợ rõ ràng, và giữ runtime dependency khi chạy.

**Lỗi: chỉ test class directory rồi giao JAR khác.** Cần đóng gói từ woven output và smoke test chính artifact phát hành.

**Lỗi: so sánh dòng log từ nhiều thread bằng thứ tự global tuyệt đối.** Sự xen kẽ giữa thread là bình thường; chọn correlation/context và kiểm tra tập event trên từng đơn vị nghiệp vụ.

**Lỗi: ghi tất cả args/result để tiện debug.** Thông tin có thể nhạy cảm, khổng lồ hoặc khó serialize. Chỉ ghi dữ liệu thực sự được cho phép và có cấu trúc.

## 12. Checklist trước khi hoàn tất

- [ ] Đã phát biểu được bằng lời nghiệp vụ nào cần ghi log và nghiệp vụ nào không.
- [ ] Pointcut có tên; positive scope dễ đọc; `@Logged` được dùng nhất quán.
- [ ] `@NoLog` và `service.internal..*` thực sự bị loại, không chỉ mô tả bằng comment.
- [ ] Advice không chặn code logging của chính nó và không gọi lại target.
- [ ] Kết quả của `submit`, `preview`, `maintenance`, `ping` đúng.
- [ ] `fail` tạo ERROR và ngoại lệ vẫn đến caller; không có OK giả.
- [ ] Có kiểm thử chứng minh **4 trường hợp rõ ràng: chọn, thiếu marker, marker phủ định, package phủ định**; đồng thời có đường ngoại lệ.
- [ ] Weave report đúng; code chạy từ class/JAR đã weave.
- [ ] Kịch bản cố tình nới pointcut khiến test phát hiện match thừa.
- [ ] Có kế hoạch xử lý thay đổi tên/package, annotation và môi trường build.
- [ ] Không đưa dữ liệu nhạy cảm vào log; đã cân nhắc runtime overhead và concurrency.

**Thông điệp cuối bài:** một logging aspect đúng không được đánh giá bằng việc “có tạo log”, mà bằng **tập join point được chọn chính xác, tập bị loại trừ chính xác và nghiệp vụ không đổi**. Hãy phát biểu chính sách chọn trước, biểu diễn nó bằng pointcut dễ review, rồi kiểm chứng bằng cả test dương lẫn test âm trên artifact thực sự chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-003">Đo thời gian và bảo toàn kết quả, ngoại lệ của lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Bài tập tổng hợp AOP: Đo thời gian và bảo toàn kết quả, ngoại lệ của lời gọi

## 1. Yêu cầu bài toán và tiêu chí hoàn thành

Xây dựng một **advice đo thời gian** chạy quanh phương thức nghiệp vụ. Advice ghi nhận thời lượng dù lời gọi **thành công hay ném ngoại lệ**, đồng thời bảo toàn hợp đồng của phương thức: kết quả trả về, đối tượng ngoại lệ, số lần thực thi và thứ tự xử lý.

Đây là một bài tổng hợp quan trọng vì `around advice` có quyền quyết định có thực hiện lời gọi gốc (`proceed`), thực hiện bao nhiêu lần, sửa giá trị trả về hoặc chuyển đổi ngoại lệ. Một lỗi nhỏ trong phần quan sát có thể biến một lời gọi đơn giản thành hành vi khác với logic ban đầu.

**Yêu cầu bắt buộc:**

1. Đo thời gian thực thi của lời gọi bằng một nguồn thời gian thích hợp để đo khoảng thời gian.
2. Gọi mục tiêu **đúng một lần** đối với mỗi lời gọi được chặn, trừ khi đặc tả nghiệp vụ cho phép retry rõ ràng.
3. Nếu mục tiêu trả về bình thường, advice trả về **đúng giá trị** mà mục tiêu tạo ra, kể cả `null`, giá trị primitive và tham chiếu đối tượng.
4. Nếu mục tiêu ném ngoại lệ, advice để chính ngoại lệ đó tiếp tục lan ra caller: không nuốt, không tự ý thay loại, message, cause hoặc identity.
5. Ghi số đo trên cả đường thành công và đường lỗi; phân biệt outcome để không báo một lời gọi lỗi là thành công.
6. Tránh để logging/metrics làm thay đổi kết quả nghiệp vụ; xác định rõ chính sách khi bộ ghi nhận số đo gặp sự cố.
7. Kiểm tra một trường hợp bình thường, một trường hợp trả `null`, một ngoại lệ runtime và một ngoại lệ checked (nếu API hỗ trợ).
8. Phân biệt thời gian của **method execution** với thời gian toàn bộ request, network, retry hoặc việc chờ tác vụ bất đồng bộ.

**Kết quả mong đợi:** caller quan sát hành vi giống khi không có aspect, còn hệ thống đo lường có thêm bản ghi về method, thời lượng và outcome.

## 2. Kiến thức AOP cần sử dụng

Trong lập trình hướng khía cạnh:

| Khái niệm | Áp dụng vào bài |
| --- | --- |
| Aspect | Đóng gói logic đo thời gian, tách khỏi nghiệp vụ |
| Join point | Sự kiện thực thi được quan sát, ví dụ `execution` của một method |
| Pointcut | Chọn đúng tập lời gọi cần đo, tránh bắt cả helper ghi metric |
| Around advice | Bao quanh phần nghiệp vụ và dùng `proceed` để tiếp tục thực thi |
| Advice precedence | Quyết định khoảng đo có bao gồm những advice bên trong hay không |
| Weaving hoặc interception | Cơ chế để lời gọi đi qua advice, không phải cú pháp pointcut đơn thuần |

Với một công cụ AOP có hỗ trợ weaving tại method execution, mô hình khái niệm là:

```text
caller
  |
  v
[around advice: start = nanoTime()]
  |
  +--> proceed() --> [business method]
  |                      |
  |                      +--> return value
  |                      +--> throw exception
  |
[finally: elapsed = nanoTime() - start, record sample]
  |
  +--> return đúng value ban đầu
  +--> throw đúng exception ban đầu
```

Đối với AOP dựa trên proxy, sự kiện được chặn là lời gọi đi qua **proxy boundary**. Nếu caller dùng tham chiếu target trực tiếp, lời gọi có thể bỏ qua advice. Với weaving ở method execution, phạm vi quan sát có thể bao gồm cả lời gọi nội bộ, tùy bytecode và cấu hình đã weave. Khi đánh giá bài làm, phải ghi rõ **cơ chế AOP nào đang được mô phỏng**.

### 2.1. Công thức đo

```text
startNs = monotonicClock()
try:
    return proceedExactlyOnce()
finally:
    elapsedNs = monotonicClock() - startNs
    record(elapsedNs, outcome)
```

`System.nanoTime()` phù hợp với việc đo **khoảng thời gian** trong một JVM. Giá trị trả về của nó có mốc tùy ý; không dùng để đổi trực tiếp thành ngày giờ. Trong khi đó, `System.currentTimeMillis()` là đồng hồ thời gian thực, có thể bị điều chỉnh, nên không thích hợp bằng cho đo duration ngắn. Tính khoảng đo bằng **hiệu hai lần `nanoTime()`**, không so sánh một timestamp tuyệt đối với timestamp từ JVM hay máy khác.

Trong ứng dụng rất dài hạn, việc trừ hai giá trị `long` có thể bị ảnh hưởng khi khoảng đo kéo dài quá khoảng xấp xỉ 292 năm; với một method invocation thông thường, cách trừ này là phù hợp. Đừng chuyển sớm sang milliseconds nếu bạn cần giữ độ phân giải nanos. **Độ phân giải đơn vị nano không đồng nghĩa độ chính xác tuyệt đối tới từng nano giây**.

### 2.2. Bảo toàn semantics quan trọng hơn log

Một `around advice` quan sát nên tương đương logic gốc trên những điều caller thấy:

- **Giá trị:** đối với reference, bảo toàn chính **tham chiếu** nếu contract yêu cầu; đối với primitive, bảo toàn giá trị theo đúng kiểu.
- **Ngoại lệ:** giữ nguyên đối tượng `Throwable` khi có thể, đặc biệt exception do mục tiêu phát sinh; không vô tình biến checked exception thành một wrapper khác.
- **Số lần:** không gọi `proceed()` hai lần chỉ vì muốn “so sánh trước/sau”; việc thực thi lại có thể nhân đôi giao dịch hoặc ghi dữ liệu.
- **Thứ tự:** không trả trước khi logic bắt buộc hoàn tất; không nuốt exception hay ném exception mới từ `finally` nếu mục tiêu đang lỗi.
- **Luồng:** đo đúng boundaries; một lời gọi method đồng bộ có thể trả về `Future`/`CompletionStage` trước khi tác vụ bất đồng bộ hoàn tất.

Không có nghĩa mọi advice phải thuần quan sát; authorization, retry hay transaction advice có thể có contract chủ động thay đổi hành vi. Bài này chỉ triển khai **timing aspect không đổi semantics**.

## 3. Vì sao phải đo trong `finally`?

Cách dễ sai:

```java
long started = System.nanoTime();
Object result = proceed();
record(System.nanoTime() - started);
return result;
```

Đây là **mảnh minh họa**: `proceed()` đại diện API của around advice chứ không phải hàm JDK có sẵn. Khi mục tiêu ném ngoại lệ, dòng `record(...)` không chạy. Kết quả là bảng metrics chỉ thống kê các lời gọi thành công và khiến latency/error data bị lệch.

Cách đúng ở mức nguyên tắc:

```java
long started = System.nanoTime();
try {
    return proceed();  // Thực thi đúng một lần.
} finally {
    long elapsedNs = System.nanoTime() - started;
    recordSafely(elapsedNs);
}
```

Trong code thực, hàm `recordSafely` còn cần có outcome. Nếu `finally` ném exception mới, exception của mục tiêu có thể bị thay thế. Vì vậy recorder nên được thiết kế **không tạo ảnh hưởng lên luồng nghiệp vụ**, hoặc có biện pháp cô lập lỗi theo yêu cầu vận hành.

Đừng ghi `return` trong `finally`: Java sẽ ưu tiên return đó, có thể bỏ qua giá trị và ngoại lệ phát sinh từ `try`. Tương tự, `throw` từ `finally` có thể che lấp lỗi ban đầu.

## 4. Ví dụ Java 17 chạy độc lập: mô phỏng around advice bằng JDK dynamic proxy

Ví dụ sau dùng `java.lang.reflect.Proxy` để tạo một interceptor tương đương around advice. Đây là **bài thực hành AOP ở mức nguyên lý**, không yêu cầu một công cụ weaving bên ngoài. Điểm quan trọng là cơ chế **chuyển tiếp đúng return và exception**.

Lưu mã thành file **`TimingAspectExercise.java`**:

```java
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class TimingAspectExercise {
    interface Service {
        String value();
        String absent();
        int divide(int left, int right);
        void read() throws IOException;
    }

    static final class RealService implements Service {
        final String expectedValue = new String("READY");
        final IOException expectedFailure = new IOException("read failed");

        @Override
        public String value() {
            return expectedValue;
        }

        @Override
        public String absent() {
            return null;
        }

        @Override
        public int divide(int left, int right) {
            return left / right;
        }

        @Override
        public void read() throws IOException {
            throw expectedFailure;
        }
    }

    record Sample(String method, boolean success, long durationNanos) {}

    static final class TimingHandler implements InvocationHandler {
        private final Service target;
        private final List<Sample> samples;

        TimingHandler(Service target, List<Sample> samples) {
            this.target = target;
            this.samples = samples;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            long started = System.nanoTime();
            boolean success = false;

            try {
                // Reflection trả về Object; giá trị primitive được boxing.
                Object result = method.invoke(target, args);
                success = true;
                return result;
            } catch (InvocationTargetException wrapper) {
                // Lấy đúng ngoại lệ do target ném, tránh trả wrapper.
                throw wrapper.getCause();
            } finally {
                long elapsed = System.nanoTime() - started;
                samples.add(new Sample(method.getName(), success, elapsed));
            }
        }
    }

    public static void main(String[] args) {
        RealService real = new RealService();
        List<Sample> samples = new ArrayList<>();
        Service service = (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] {Service.class},
                new TimingHandler(real, samples)
        );

        String returned = service.value();
        System.out.println("same reference: "
                + (returned == real.expectedValue));

        System.out.println("null kept: " + (service.absent() == null));
        System.out.println("divide result: " + service.divide(8, 2));

        try {
            service.divide(1, 0);
        } catch (ArithmeticException error) {
            System.out.println("runtime type: "
                    + error.getClass().getSimpleName());
        }

        try {
            service.read();
        } catch (IOException error) {
            System.out.println("same checked exception: "
                    + (error == real.expectedFailure));
        }

        for (Sample sample : samples) {
            System.out.println("sample " + sample.method()
                    + " success=" + sample.success()
                    + " durationNonnegative="
                    + (sample.durationNanos() >= 0));
        }
    }
}
```

Chạy với **JDK 17 trở lên**:

```bash
javac TimingAspectExercise.java
java TimingAspectExercise
```

**Standard output** cho chương trình (thời lượng thực không được in ra vì thay đổi theo máy):

```text
same reference: true
null kept: true
divide result: 4
runtime type: ArithmeticException
same checked exception: true
sample value success=true durationNonnegative=true
sample absent success=true durationNonnegative=true
sample divide success=true durationNonnegative=true
sample divide success=false durationNonnegative=true
sample read success=false durationNonnegative=true
```

Giải thích cách bài làm giữ semantics:

1. `value()` trả lại **cùng một instance `String`** do target tạo; phép so sánh tham chiếu dùng `==` cho kết quả `true`.
2. `absent()` trả `null` nguyên trạng; advice không tự thay bằng chuỗi rỗng hay đối tượng khác.
3. `divide(8, 2)` trả `int 4`, dù `Method.invoke` boxing giá trị primitive thành `Integer` ở biên reflection. Proxy tự unbox khi trả về qua chữ ký interface.
4. `divide(1, 0)` vẫn ném `ArithmeticException`. Advice ghi `success=false` nhờ `finally`, nhưng không bắt rồi chuyển ngoại lệ thành giá trị mặc định.
5. `read()` ném checked `IOException`; Reflection bọc exception đích thành `InvocationTargetException`, do đó handler phải dùng `wrapper.getCause()` để chuyển tiếp **đúng đối tượng exception ban đầu**. Interface `Service.read() throws IOException` cho phép proxy chuyển tiếp checked exception này.
6. Mỗi method call tạo đúng **một `Sample`**. Biến `success` chỉ được gán `true` khi `method.invoke` trả bình thường; exception khiến sample ghi thất bại.
7. Số đo `durationNanos` thực nằm trong từng record `Sample`. Output cố tình chỉ kiểm tra số đo không âm để trình bày được một stdout ổn định; duration thực tùy JVM/CPU/GC và workload.

**Giới hạn ví dụ:** `List<Sample>` là `ArrayList` trong chương trình chạy tuần tự. Với concurrent requests, phải dùng collector an toàn đa luồng hoặc một hệ thống metrics phù hợp, tránh `ArrayList` chia sẻ không đồng bộ. Đo lường ở đây bao quanh **`Method.invoke`**, không nhất thiết phản ánh tổng thời gian caller gọi qua proxy hay một network request.

### 4.1. Vì sao không dùng `catch (Exception) { return null; }`?

Một lỗi phổ biến trong interceptor:

```java
try {
    return method.invoke(target, args);
} catch (Exception ignored) {
    return null; // SAI: nuốt lỗi và có thể gây NullPointerException khi unbox.
}
```

Đây là mã **cố ý sai để phân tích**, không dùng trong bài giải cuối. Với method trả primitive như `int`, `null` thậm chí có thể gây `NullPointerException` ở đoạn unboxing của proxy và che khuất `ArithmeticException` gốc. Với phương thức trả object, caller có thể tưởng `null` là kết quả nghiệp vụ hợp lệ. Vì vậy việc “đo được latency” không chứng minh advice đúng.

### 4.2. Tại sao chỉ unwrap `InvocationTargetException`?

`Method.invoke` ném `InvocationTargetException` khi **phương thức đích** ném exception. Những lỗi reflection khác như `IllegalAccessException` là lỗi sử dụng reflection/thiết lập interceptor, không phải business exception tương đương từ method đích. Bài tập yêu cầu giữ nguyên exception do target ném; không che khuất lỗi cấu hình bằng cách biến mọi exception thành nguyên nhân tùy tiện.

JDK dynamic proxy có ràng buộc với checked exception: nếu handler ném một checked exception không được khai báo bởi phương thức interface, proxy có thể bọc nó trong `UndeclaredThrowableException`. Muốn bảo toàn contract checked exception, **interface khai báo đúng `throws`** và interceptor chuyển tiếp loại phù hợp.

### 4.3. Ngoại lệ xảy ra trong metric recorder

Dòng `samples.add(...)` ở `finally` giả định collector hoạt động. Nếu chính collector ném exception, nó có thể **thay thế** giá trị trả về hoặc ngoại lệ mục tiêu. Trong hệ thống đo lường mà tính quan sát không được cản trở nghiệp vụ, dùng phương án cô lập lỗi:

```java
// Minh họa ý tưởng, giả định recorder là một đối tượng có sẵn.
try {
    recorder.record(methodName, success, elapsedNs);
} catch (RuntimeException measurementFailure) {
    // Tăng bộ đếm lỗi đo lường độc lập hoặc bỏ mẫu theo chính sách.
    // Không ném tiếp để tránh thay đổi kết quả/ngoại lệ nghiệp vụ.
}
```

Đoạn trên chỉ là **mảnh minh họa**, không phải chương trình Java biên dịch độc lập: `recorder`, `methodName`, `success` và `elapsedNs` cần được khai báo trong code thực. Không nên tùy tiện bắt `Throwable` để nuốt cả lỗi nghiêm trọng ở JVM; chính sách cần phân biệt lỗi đo lường có thể phục hồi và lỗi hệ thống. Nếu việc ghi audit là yêu cầu bắt buộc, chính sách xử lý lỗi có thể khác timing metric tùy hợp đồng nghiệp vụ.

## 5. Áp dụng vào aspect sử dụng weaving

Khi công cụ AOP hỗ trợ around advice với `proceed`, thuật toán tương đương: lấy thời gian ngay trước `proceed` và thu sample trong `finally`. Ví dụ cú pháp **AspectJ code-style** sau minh họa cho **một method trả `int` cụ thể**, cần toolchain weaving và class `demo.Calculator` phù hợp nên **không chạy độc lập bằng `javac`**:

```java
package demo;

public aspect TimingAspect {
    int around(): execution(int demo.Calculator.divide(int, int)) {
        long started = System.nanoTime();
        try {
            return proceed();
        } finally {
            long elapsed = System.nanoTime() - started;
            System.out.println("durationNanos=" + elapsed);
        }
    }
}
```

Aspect chọn join point **thực thi** `Calculator.divide(int, int)`. `proceed()` giữ nguyên giá trị `int` hoặc cho phép exception đi ra; `finally` vẫn chạy khi target ném lỗi. Dòng log in ra duration không cố định. Trong ứng dụng thực, nếu logging có thể làm thay đổi hành vi, thay bằng recorder được cô lập lỗi, không log dữ liệu nhạy cảm và không để helper cũng khớp pointcut gây đệ quy.

Lưu ý: nếu chọn pointcut có cả method trả `void` lẫn reference và primitive, cần thiết kế đúng signature của `around`/`proceed` theo quy tắc của **AOP engine cụ thể**. Không suy luận mọi công cụ tự chuyển kiểu hay chấp nhận một `Object around` cho tất cả method. Với đoạn ví dụ này, pointcut cố ý hẹp để giữ kiểu `int` rõ ràng.

### 5.1. Ý nghĩa của advice precedence

Giả sử một method có `TimingAspect` và `ValidationAspect` cùng match. Nếu timing là lớp `around` bên ngoài, khoảng đo gồm cả thời gian validation, những advice nằm trong và target. Nếu timing nằm bên trong, khoảng đo bắt đầu sau validation và không tính overhead của advice bên ngoài.

```text
Timing.before (start)
  Validation.before
    Target
  Validation.after
Timing.after (stop)
```

Đây là **mô tả nesting**, không tự suy ra thứ tự precedence của từng engine. Muốn so sánh latency giữa hai lần triển khai, thứ tự advice và vùng đo phải được định nghĩa ổn định. Đồng thời lưu ý `after` trên các advice bao ngoài sẽ chạy theo thứ tự stack unwind.

### 5.2. Chọn đúng join point

- `execution` đo sự thực thi của thân phương thức đã được weave.
- `call` đo sự kiện tại call site đã được weave, có thể bao gồm overhead khác.
- Proxy interception đo đoạn chuyển tiếp qua proxy, có thể bỏ qua self-invocation tùy loại proxy.
- Nếu method được gọi lặp qua retry, recursion hoặc callback, mỗi invocation có một duration; không gộp tùy ý thành một request duration.
- Advice `around` có thể bỏ `proceed` hoặc gọi nhiều lần, nhưng bài này yêu cầu **một lần và bảo toàn nghiệp vụ**.

Một phép test nên xác định rõ số lượng execution join point và advice invocation. Hai dòng log giống nhau không đủ chứng minh target chạy hai lần: có thể là hai advice, hai mức gọi lồng, hoặc log vào/ra.

## 6. Bài tập mở rộng để người học tự thực hành

### Bài A — bảo toàn giá trị và ngoại lệ

Từ ví dụ `TimingAspectExercise`, bổ sung method:

```java
// Mảnh code bổ sung vào interface và implementation tương ứng.
String echo(String input);
```

Cho `echo("x")` trả một reference được cung cấp bởi implementation và `echo(null)` trả `null`. Viết assert với `==` cho reference nếu contract yêu cầu identity; so nội dung bằng `equals` trong trường hợp chỉ cần equality. Sau đó thêm method ném checked exception có subtype, xác nhận type và identity đi đến caller không đổi.

**Tiêu chí đạt:** 5 ca hiện có vẫn qua, thêm các trường hợp `echo` đúng, số sample bằng số invocation, không có exception bị wrap không mong muốn.

### Bài B — exception và cleanup

Cố tình chuyển `samples.add` ra phía sau `method.invoke` thay vì `finally`. Chạy lại ca `divide(1,0)` và `read()`: số sample giảm, cho thấy phương pháp chỉ đo success đã bỏ qua request lỗi. Khôi phục `finally`, rồi mô phỏng recorder ném `IllegalStateException` để thấy nó có thể che business exception nếu không được cách ly.

**Tiêu chí đạt:** ghi được cả success và failure mà vẫn giữ nguyên ngoại lệ của target. Không giải quyết bằng `return null` hay `return 0` khi gặp lỗi.

### Bài C — proxy, pointcut và weaving

Thu hẹp interceptor để chỉ đo `divide`; giữ `value`/`absent`/`read` hoạt động bình thường qua proxy. Tiếp theo gọi trực tiếp `real.divide(8, 2)` để thấy đường raw target không được đo. Nếu triển khai bằng weaving tại method execution, hãy so sánh và giải thích vì sao đường raw target **có thể** được quan sát nếu class tương ứng đã được weave.

**Tiêu chí đạt:** giải thích bằng **điểm đi qua interception**, tránh đánh đồng pointcut không match với lời gọi không qua proxy.

### Bài D — thống kê theo outcome

Tính count, số mẫu success/failure, min/max và trung bình duration từ `List<Sample>`. Chỉ dùng dữ liệu sample để đo method; không gắn patient/user/payment ID vào nhãn metric. Với histogram production, tránh lưu mọi sample vô hạn trong RAM.

Có thể dùng `LongSummaryStatistics` cho tập duration đơn giản, nhưng phải hiểu trung bình không thay thế percentile và không trực tiếp thể hiện tail latency. Mọi so sánh p95/p99 cần đủ mẫu và cùng phân phối workload.

### Bài E — concurrency và async

Cho nhiều thread gọi proxy; thay `ArrayList` bằng collector an toàn đa luồng và kiểm tra số sample không bị mất. Tiếp theo thử một method trả `CompletableFuture`: advice `around` đồng bộ chỉ đo tới thời điểm **trả về future**, chưa chắc đo tới khi future hoàn thành. Nếu yêu cầu đo toàn bộ tác vụ async, cần gắn completion callback riêng, đo từ lúc bắt đầu đến khi completion và chuyển tiếp nguyên trạng success/exception/cancellation theo contract.

**Tiêu chí đạt:** không race condition, không rò trạng thái luồng, không tự thay exception completion hoặc thay đổi cách cancellation lan truyền.

## 7. Kiểm thử đầy đủ về tính tương đương

Bảng kiểm thử sau giúp chứng minh bộ đo không thay đổi lời gọi:

| Trường hợp | Hành vi khi gọi target trực tiếp | Hành vi sau khi áp advice | Sample |
| --- | --- | --- | --- |
| Trả đối tượng | Cùng reference được trả về | Giữ identity, không clone | success |
| Trả `null` | `null` | Vẫn `null` | success |
| Trả primitive | Giá trị chính xác | Vẫn đúng giá trị | success |
| `void` bình thường | Không ném exception | Không ném exception | success |
| Runtime exception | Exception đúng type/message/cause | Cùng exception gốc nếu cơ chế cho phép | failure |
| Checked exception | Exception khai báo bởi method | Không bị đổi thành wrapper khác | failure |
| Lời gọi lặp hoặc đồng thời | Một execution cho mỗi lần nghiệp vụ gọi | Advice và sample có số lần tương ứng | một sample/invocation |
| Ném lỗi trong metric recorder | Nghiệp vụ không phụ thuộc metrics | Theo chính sách cô lập lỗi đo lường, không che kết quả | có thể bỏ sample |
| Async return | Trả về task/future | Không làm đổi future; thời gian đến completion cần đo riêng | xác định scope |

### 7.1. Điều gì có thể xác nhận bằng unit test?

- Dùng target giả có counter để xác nhận **mỗi intercepted invocation gọi target đúng một lần**.
- Dùng tham chiếu object riêng để xác nhận return **identity**.
- Dùng một exception instance riêng để assert `caught == expected`; kiểm cả checked và unchecked.
- Xác nhận sample xuất hiện dù target ném exception; sample có đúng method name và outcome.
- Dùng recorder cố ý ném lỗi để test chính sách cách ly telemetry.
- Với thời lượng, kiểm ≥ 0 cho lời gọi ngắn và tránh assert một số nano/milli giây chính xác vì hệ điều hành, JIT và GC gây nhiễu.

### 7.2. Điều gì nên xác nhận bằng integration test?

- Aspect/weaver/proxy thực sự được cấu hình và pointcut match đúng nơi.
- Gọi từ caller thực có đi qua interception boundary hay không.
- Advice precedence đúng như ý định; thời gian được đo bao gồm/loại trừ các advice khác theo cấu hình.
- Method có annotation, overload, generic bridge hoặc bytecode sinh bởi compiler có bị khớp ngoài phạm vi không.
- Kết quả của class/JAR **sau weaving** đúng với artifact chạy thật.
- Khi concurrency, sampling, export và logging hoạt động, không xuất hiện deadlock, recursion hay overhead vượt ngân sách.

## 8. Những bẫy nâng cao

### 8.1. Đừng dùng `System.currentTimeMillis()` để so duration rất ngắn

`currentTimeMillis()` là wall clock, thích hợp cho timestamp để đối chiếu lịch, còn `nanoTime()` thích hợp cho elapsed time. Nếu cần cả hai, ghi timestamp riêng và duration riêng. Không tính latency bằng timestamp gửi từ hai máy khác nhau khi đồng hồ chưa được đồng bộ hoặc độ chính xác clock không đủ.

### 8.2. Một lời gọi nhìn từ ngoài có thể tạo nhiều sample

Một request có thể đi qua nhiều method và join point; around advice khác cũng có thể ghi thời gian. Recursion, retries và nested calls không nhất thiết là lỗi. Để tổng hợp, dùng `traceId`/`spanId` hoặc invocation ID, và phân biệt **exclusive time** (chỉ phần tự chạy) với **inclusive time** (bao gồm lời gọi con). Bài tập cơ bản đo inclusive elapsed tại boundary đã chọn.

### 8.3. Logging có thể gây lời gọi đệ quy

Nếu pointcut bắt cả method ghi log, serializer, metric exporter hoặc `toString()` của đối tượng, advice đo thời gian có thể kích hoạt chính nó. Thu hẹp pointcut, loại trừ helper hoặc dùng reentrancy guard có scope rõ. Guard theo `ThreadLocal` phải giải phóng trong `finally`; nó không tự truyền qua async task.

### 8.4. Đo thời gian không có nghĩa hệ thống ít overhead

`System.nanoTime()` có chi phí, còn việc dựng chuỗi, lấy stack trace, ghi sync log hoặc export metrics có thể tốn hơn nhiều. Bản đo cũng có thể ảnh hưởng tốc độ ứng dụng. Cần đánh giá overhead với workload thật, sampling phù hợp, collector giới hạn dung lượng và nhãn cardinality thấp.

### 8.5. Ngoại lệ từ advice khác cũng nằm trong phạm vi đo tùy nesting

Nếu một advice ở phía trong ném lỗi **trước khi target chạy**, timing advice ở ngoài vẫn ghi failure cho lời gọi vào chain, mặc dù thân nghiệp vụ chưa được thực hiện. Vì thế metric “execution latency” phải mô tả rõ đo **target body** hay **interception chain**. Kết quả còn phụ thuộc việc pointcut chọn `call`, `execution` hoặc lời gọi qua proxy.

### 8.6. Cẩn thận với `InterruptedException`, cancellation và thời hạn

Không tự nuốt `InterruptedException`. Nếu advice cần bắt ngoại lệ để ghi outcome, hãy ném lại theo contract; nếu can thiệp vào interrupt status phải hiểu API đang dùng. Khi gặp `CancellationException` hoặc future bị cancel, không tự chuyển thành success. Bài đo thời gian không được vô tình làm thay đổi chính sách timeout/cancellation.

### 8.7. Phân biệt giám sát với benchmarking

Một lần chạy đo được 120 ns không có nghĩa method luôn mất 120 ns. JIT warmup, tiered compilation, CPU scheduling, GC, cache, dead-code elimination và contention khiến microbenchmark rất dễ sai. Dùng công cụ benchmark chuyên dụng khi cần số liệu hiệu năng chính xác, và dùng observability trong hệ thống thực để theo dõi latency theo workload.

## 9. Quy trình giải bài mẫu

1. **Viết contract của target** gồm return type, các ngoại lệ có thể ném, số lần gọi dự kiến và hành vi khi `null`.
2. **Chọn join point rõ ràng**; không chọn wildcard toàn hệ thống từ đầu.
3. **Đưa mốc bắt đầu vào trước khi chuyển tiếp**; không làm logging hoặc network I/O tốn kém ngay trước mốc nếu muốn đo phạm vi hợp lý.
4. **Gọi `proceed` hoặc target đúng một lần**; chuyển tiếp toàn bộ tham số và giữ nguyên kết quả.
5. **Đánh dấu outcome:** bình thường thì success, ném lỗi thì failure; không gán success trước khi target kết thúc.
6. **Dùng `finally`** lấy thời lượng và ghi sample, nhưng tách lỗi metric recorder khỏi luồng nghiệp vụ khi được yêu cầu bảo toàn semantics.
7. **Test cả success và failure**; đặc biệt assert identity của reference và exception.
8. **Kiểm thử đúng AOP engine** trong build/runtime; không coi ví dụ proxy JDK là mô hình duy nhất.
9. **Đọc số liệu đúng ngữ cảnh**: đồng bộ hay async, inclusive hay exclusive, có advice khác nằm trong phạm vi đo hay không.

## 10. Checklist tự đánh giá

- [ ] Đã mô tả đúng vấn đề: đo duration **và** không thay đổi return/exception.
- [ ] Dùng `System.nanoTime()` như một nguồn elapsed time, lấy hiệu đúng hai mốc.
- [ ] Advice/handler gọi target đúng một lần trong trường hợp thông thường.
- [ ] Có `finally` để ghi sample trên cả return và throw.
- [ ] Dùng outcome thành công/thất bại chính xác; không báo success khi target ném lỗi.
- [ ] Không nuốt, đổi loại, sửa message, mất cause hoặc thay identity ngoại lệ đích.
- [ ] Không tự biến `null` thành giá trị khác, không hỏng boxing/unboxing primitive.
- [ ] Nếu dùng JDK reflection, đã unwrap `InvocationTargetException` đúng chỗ.
- [ ] Recorder được xử lý theo chính sách không phá contract nghiệp vụ.
- [ ] Phân biệt proxy interception với weaving tại `execution` và biết nơi lời gọi có thể bypass.
- [ ] Đã chạy code Java 17, kiểm tra stdout và test các nhánh trả về, runtime/checked exception.
- [ ] Đã định nghĩa phạm vi thời gian đo trong advice chain và khi trả asynchronous result.
- [ ] Không đưa helper đo/log vào pointcut gây recursion; không tạo metrics có cardinality quá cao.

**Tiêu chuẩn của một timing aspect đúng:** với mọi input và mọi nhánh thực thi được hỗ trợ, nếu bỏ phần ghi nhận sample đi thì caller vẫn nhận **cùng giá trị hoặc cùng ngoại lệ**, và mục tiêu không chạy thêm lần nào do việc đo thời gian.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-004">Thực nghiệm khác biệt giữa điểm gọi và điểm thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Thực nghiệm khác biệt giữa điểm gọi và điểm thực thi

## 1. Mục tiêu bài thực nghiệm

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming, AOP**), một phương thức có thể được nhìn từ hai vị trí khác nhau:

- **Điểm gọi (method-call join point)**: vị trí nơi chương trình thực hiện lời gọi đến phương thức, tức phía *caller*.
- **Điểm thực thi (method-execution join point)**: sự kiện một phương thức bắt đầu được thực thi ở phía *callee/target*.

Hai khái niệm liên quan với nhau nhưng không đồng nhất. Cùng một phương thức có thể có nhiều vị trí gọi; một vị trí gọi có thể dẫn tới nhiều implementation nhờ đa hình; và lời gọi gián tiếp qua reflection không nhất thiết tạo ra điểm gọi tĩnh tương ứng với phương thức đích trong bộ chọn pointcut. Ngoài ra, không phải mọi hệ AOP đều cung cấp cả hai loại join point.

Bài học này là **một thực nghiệm có giả thuyết, mã chạy, kết quả và phân tích**, nhằm giúp bạn:

1. Vẽ đúng luồng từ caller đến target, phân biệt thời điểm quan sát ở đầu gọi và đầu thực thi.
2. Giải thích sự khác biệt khi nhiều caller gọi cùng một phương thức, khi đối tượng gọi chính phương thức của mình và khi dùng reflection.
3. Hiểu ảnh hưởng của pointcut, weaving/proxy, dynamic dispatch và advice around đối với kết quả thực nghiệm.
4. Có chương trình Java 17+ để đo sự kiện một cách xác định mà không cần thư viện bên ngoài.
5. Biết cách chuyển từ mô hình tự ghi sự kiện sang một thử nghiệm AOP thật với pointcut tương ứng, tránh kết luận quá phạm vi chứng cứ.

**Phạm vi:** AOP nói chung, không mặc định một framework hay một engine cụ thể. Khi nhắc đến cú pháp pointcut kiểu AspectJ, đó là **ví dụ cho một hệ có hỗ trợ call và execution**, không phải tuyên bố cú pháp ấy tồn tại ở mọi AOP engine.

## 2. Hai vị trí trên cùng một đường đi

Xét lời gọi:

~~~java
// Đoạn minh họa nằm trong một phương thức caller, chưa phải file độc lập.
Calculator calculator = new Calculator();
int answer = calculator.add(2, 3);
~~~

Bên trong phương thức đích:

~~~java
// Đoạn minh họa là thân một class Calculator.
public int add(int a, int b) {
    return a + b;
}
~~~

Sơ đồ:

~~~text
Caller.main()                                 Calculator.add()
    |                                               |
    |  [CALL: vị trí calculator.add(2,3)]            |
    +---------------------------------------------->|
                                                    | [EXECUTION: bắt đầu chạy add]
                                                    | return 5
    <-----------------------------------------------+
    | caller nhận 5
~~~

Điểm gọi gắn với **biểu thức/lệnh gọi ở caller**; điểm thực thi gắn với **việc chạy phương thức ở callee**. Nhiều công cụ dùng hai kiểu pointcut tương ứng theo ý tưởng:

~~~text
call(<mẫu chữ ký phương thức>)
execution(<mẫu chữ ký phương thức>)
~~~

Đây là **ký hiệu khái niệm**. Mỗi công cụ hỗ trợ tập join point và cú pháp riêng; một cơ chế proxy chỉ chặn đầu vào phương thức thông qua proxy thường không cung cấp đầy đủ các join point call ở mọi vị trí mã nguồn.

### 2.1. Cách đếm cho đúng

Đừng nhầm “một phương thức có một execution join point trong định nghĩa” với “phương thức chỉ thực thi một lần”. Nếu caller gọi Calculator.add hai lần, khi không bị short-circuit và mọi lời gọi đi tới target sẽ có hai lần thực thi. Ngược lại, nếu hai caller khác nhau đều gọi add, có **hai call site** (hai vị trí mã) nhưng mỗi site có thể được đi qua bao nhiêu lần tùy runtime.

Tách ba khái niệm sau:

| Khái niệm | Đơn vị quan sát | Ví dụ |
| --- | --- | --- |
| **Vị trí call site** | Vị trí câu lệnh/bytecode có invocation | Dòng gọi add trong callerA |
| **Số lần sự kiện call** | Bao nhiêu lần chương trình thực thi qua site đó | Vòng lặp gọi add 10 lần: 10 sự kiện ở một site |
| **Số lần sự kiện execution** | Bao nhiêu lần target method thật bắt đầu | 10 lời gọi đi đến thân add: 10 lần execution |

Vì thế “call nhiều hơn execution” không phải mệnh đề luôn đúng. Advice ở call site có thể trả sớm nên không có execution tương ứng; advice ở execution có thể chặn trước thân target; reflection, mã native, mã sinh hoặc lớp không được weave có thể tạo khác biệt giữa các sự kiện mà engine thực sự quan sát.

## 3. Giả thuyết và các trường hợp cần kiểm tra

Hãy dùng một lớp Engine có phương thức run(String). Chuẩn bị bốn đường đi:

- **T1 — Caller A:** callerA gọi engine.run("A").
- **T2 — Caller B:** callerB gọi engine.run("B").
- **T3 — Tự gọi:** Engine.selfInvoke gọi run("self") bên trong cùng đối tượng.
- **T4 — Reflection:** reflective gọi java.lang.reflect.Method.invoke để chạy Engine.run("reflect").

Giả thuyết trước khi chạy:

1. T1 và T2 có hai vị trí gọi khác nhau, nhưng cùng đi vào một định nghĩa Engine.run.
2. T3 là lời gọi nội bộ. Với bytecode có một call site được weave, có thể quan sát lời gọi này; với cơ chế proxy chỉ can thiệp lời gọi **qua đối tượng proxy**, lời gọi trực tiếp trên đối tượng gốc có thể không được chặn.
3. T4 có lời gọi tĩnh vào **Method.invoke** ở phía caller, nhưng không có một biểu thức trực tiếp engine.run("reflect") tại vị trí ấy. Nếu bản thân Engine.run được weave, execution của nó vẫn có thể quan sát được. Loại call join point mà một weaver nhận diện phải xem theo engine cụ thể.
4. Nếu gọi run lặp nhiều lần, số lần execution tăng theo **số lần thân method bắt đầu**, không phụ thuộc số vị trí call site khác nhau.

**Điểm quyết định:** phần dưới mô phỏng log ở từng ranh giới để học cách đọc dữ liệu. Nó **không tự cài đặt AOP weaving**. Để chứng minh đúng semantics của call/execution pointcut, phải chạy thêm thử nghiệm bằng công cụ AOP thật ở mục 7.

## 4. Thực nghiệm Java 17+ chạy độc lập: ghi sự kiện rõ ràng

Lưu nguyên chương trình sau vào **CallExecutionLab.java**:

~~~java
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CallExecutionLab {
    private static final List<String> EVENTS = new ArrayList<>();

    static void event(String message) {
        EVENTS.add(message);
    }

    static final class Engine {
        void run(String value) {
            event("EXEC Engine.run(" + value + ")");
        }

        void selfInvoke() {
            event("EXEC Engine.selfInvoke");
            event("CALL Engine.selfInvoke -> run");
            run("self");
        }
    }

    static void callerA(Engine engine) {
        event("CALL callerA -> Engine.run");
        engine.run("A");
    }

    static void callerB(Engine engine) {
        event("CALL callerB -> Engine.run");
        engine.run("B");
    }

    static void reflective(Engine engine) throws Exception {
        Method method = Engine.class.getDeclaredMethod("run", String.class);
        event("CALL reflective -> Method.invoke");
        method.invoke(engine, "reflect");
    }

    public static void main(String[] args) throws Exception {
        Engine engine = new Engine();
        callerA(engine);
        callerB(engine);
        engine.selfInvoke();
        reflective(engine);
        EVENTS.forEach(System.out::println);
    }
}
~~~

Chạy từ thư mục chứa tệp bằng **JDK 17 trở lên**:

~~~bash
javac CallExecutionLab.java
java CallExecutionLab
~~~

Nếu compiler mới hơn mà cần kiểm tra cú pháp/source API Java 17, có thể dùng:

~~~bash
javac --release 17 CallExecutionLab.java
java CallExecutionLab
~~~

**Stdout đã đối chiếu thực tế** khi biên dịch bằng javac 24.0.2 với --release 17 và chạy trên JDK 24.0.2:

~~~text
CALL callerA -> Engine.run
EXEC Engine.run(A)
CALL callerB -> Engine.run
EXEC Engine.run(B)
EXEC Engine.selfInvoke
CALL Engine.selfInvoke -> run
EXEC Engine.run(self)
CALL reflective -> Method.invoke
EXEC Engine.run(reflect)
~~~

### 4.1. Giải thích thứ tự từng sự kiện

**T1 — Caller A**

~~~text
CALL callerA -> Engine.run
EXEC Engine.run(A)
~~~

Tại callerA, chương trình ghi một sự kiện CALL **ngay trước** lệnh engine.run. Sau khi điều khiển đi vào Engine.run, method tự ghi sự kiện EXEC. Vị trí ghi dấu là hai chỗ khác nhau trong source và nằm ở hai đầu ranh giới lời gọi.

**T2 — Caller B**

~~~text
CALL callerB -> Engine.run
EXEC Engine.run(B)
~~~

Một call site khác vẫn đi tới cùng thân run. Nếu chỉ quan sát ở đầu thực thi mà không mang theo thông tin caller, hai lượt đều là Engine.run với tham số khác nhau. Nếu chỉ quan sát ở call site, có thể biết mỗi lần xuất phát từ vị trí nào.

**T3 — Gọi từ cùng đối tượng**

~~~text
EXEC Engine.selfInvoke
CALL Engine.selfInvoke -> run
EXEC Engine.run(self)
~~~

Engine.selfInvoke được thực thi, sau đó gọi run trên chính đối tượng hiện tại. Vì ví dụ ghi log trực tiếp ở thân phương thức và ở câu lệnh gọi nội bộ, cả hai loại marker đều xuất hiện. **Không được dùng kết quả này để khẳng định một proxy AOP bất kỳ chặn được self-invocation**: marker ở đây do chính mã nguồn viết ra.

**T4 — Lời gọi thông qua reflection**

~~~text
CALL reflective -> Method.invoke
EXEC Engine.run(reflect)
~~~

Phía caller **gọi Method.invoke**, còn phương thức mục tiêu được kích hoạt gián tiếp. Trong ví dụ, marker EXEC tại run luôn in khi run thực thi, bất kể ai gọi. Nhưng marker CALL ghi rõ vị trí thực tế mà mã Java trực tiếp gọi: Method.invoke. Điều này giúp hình dung tại sao một pointcut **call Engine.run** dựa vào call site tĩnh có thể không bắt được đường gián tiếp tương tự.

### 4.2. Giới hạn và sức mạnh của thí nghiệm

Chương trình chứng minh đường điều khiển và thứ tự **các marker do lập trình viên đặt thủ công**. Nó không chứng minh một engine AOP hỗ trợ loại join point nào, chọn đúng pointcut hay có nhìn thấy bytecode invocation bên trong reflection/runtime hay không. Để kiểm tra điều đó, sử dụng công cụ có tài liệu rõ về call/execution (xem mục 7) và lặp lại cùng bốn ca.

Việc marker CALL được đặt **trước** lệnh gọi còn có một chi tiết: nếu đánh giá receiver/tham số có thể ném lỗi trước khi invocation xảy ra, một log viết không đúng vị trí có thể ghi CALL dù một engine pointcut chưa thực sự vào call join point. Vì thế marker trong ví dụ là mô hình giảng giải, không phải phép đo bytecode tuyệt đối.

## 5. Bảng dự đoán và kết quả kiểm chứng

Từ dữ liệu mục 4, lập bảng:

| Đường đi | Marker phía caller | Marker phía target | Điều được rút ra |
| --- | --- | --- | --- |
| callerA → run | CALL callerA → Engine.run | EXEC Engine.run(A) | Caller A có điểm phát lệnh riêng |
| callerB → run | CALL callerB → Engine.run | EXEC Engine.run(B) | Một định nghĩa phương thức có nhiều call site |
| selfInvoke → run | CALL Engine.selfInvoke → run | EXEC Engine.run(self) | Gọi nội bộ vẫn thực thi target; proxy có thể bỏ qua interception |
| Method.invoke → run | CALL reflective → Method.invoke | EXEC Engine.run(reflect) | Lời gọi gián tiếp và thực thi target nằm ở hai ngữ cảnh khác |

Trong lần chạy này có **bốn lần vào Engine.run**, nhưng chỉ **ba marker CALL trực tiếp đến run** do mã nguồn đặt (A, B, selfInvoke); dòng thứ tư là CALL đến Method.invoke. Không nên biến phép đếm này thành quy tắc “AOP call pointcut chỉ chạy ba lần” khi chưa thử trên engine có weaving thật.

**Phép kiểm tra có thể thực hiện:** đánh dấu trên bản in từng dòng là **đầu gọi** hay **đầu thực thi**, rồi nối các cặp theo ca T1–T4. Mỗi cặp caller-target có thể được tách để so sánh trách nhiệm bắt lỗi, chọn pointcut và đo thời gian.

## 6. Call và execution khác nhau ở điều kiện chọn pointcut

### 6.1. Call pointcut thiên về ngữ cảnh nơi gọi

Trong một hệ có hỗ trợ method-call join point, bộ chọn có thể can thiệp ngay ở nơi gọi. Điều này hữu ích khi bạn quan tâm:

- **Ai đã gọi API?** Ví dụ chỉ áp dụng chính sách cho các call site thuộc một package cụ thể.
- **Lời gọi được phát ra từ khu vực nào?** Có thể phân biệt code thư viện nội bộ với caller bên ngoài.
- **Có cần chặn trước khi đi vào target?** Một around advice ở call site có thể bỏ qua lời gọi nếu hợp đồng cho phép.
- **Điểm gắn vào mã đã build:** muốn thấy call join point ở các caller, engine thường phải quan sát/weave những class chứa call site tương ứng.

Không nên nhầm “call pointcut” với “toàn bộ call stack của caller”; một join point là **sự kiện xác định**, không phải mọi frame dọc đường đi.

### 6.2. Execution pointcut thiên về phương thức được thực thi

Trong hệ có hỗ trợ method-execution join point, bộ chọn hoạt động ở phương thức đích khi nó được vào thực thi, có thể hữu ích để:

- Quan sát **mọi đường gọi** đến cùng implementation mà engine đã weave, kể cả nhiều caller.
- Đặt chính sách gắn với bản thân hoạt động của phương thức thay vì gắn từng vị trí gọi.
- Thu thập thông tin target, lời gọi, thời gian thân method, return/throw theo semantics của engine.
- Giảm nhu cầu weave mọi caller nếu engine hỗ trợ weave ở class đích theo phạm vi cấu hình.

Cần phân biệt **entry của execution join point** với **việc thân phương thức đã chạy xong**. An around advice ở execution có thể thay đổi hoặc bỏ qua continuation của method body; khi đó sự kiện execution advice có thể được quan sát dù body thật không chạy. Muốn khẳng định body đã chạy, cần log hoặc counter **ngay trong target**.

### 6.3. Bảng khác biệt có điều kiện

| Trục so sánh | Call join point | Execution join point |
| --- | --- | --- |
| Vị trí ý niệm | Nơi mã gọi phương thức | Nơi phương thức bắt đầu chạy |
| Ngữ cảnh nổi bật | Caller và call site | Callee/target và method body |
| Nhiều caller cùng target | Có thể có nhiều call site | Cùng một định nghĩa target |
| Reflection | Không giả định có call site tĩnh đến target | Có thể thấy execution target nếu target được weave |
| Tự gọi trong cùng đối tượng | Có thể có call join point ở call site nội bộ nếu đã weave | Execution có thể được can thiệp nếu method được weave |
| Proxy chỉ bọc đối tượng | Không đại diện đầy đủ cho mọi call site | Gần với chặn lời gọi qua proxy, nhưng vẫn không phải execution weaving tổng quát |
| Phạm vi cần instrument | Code chứa call site phù hợp | Code chứa target method phù hợp |

Đây là **so sánh về mô hình**, không phải ma trận cam kết cho mọi runtime. Hệ AOP chỉ hỗ trợ interception dựa trên proxy có thể không phơi bày loại call join point để chọn độc lập.

## 7. Mở rộng sang thử nghiệm AOP thật với pointcut call/execution

Để kiểm chứng join point thật, cần một **engine có hỗ trợ cả method-call và method-execution pointcut**. Ví dụ sau viết theo cú pháp pointcut của **AspectJ** như một lựa chọn thực nghiệm, không yêu cầu người học dùng engine này cho mọi dự án.

Cần phân biệt hai mức bằng chứng:

1. **Thử nghiệm Java ở mục 4:** chương trình độc lập và stdout đã được kiểm chứng; các marker đặt thủ công.
2. **Thử nghiệm engine ở mục 7:** cần cài và dùng phiên bản compiler/weaver cùng runtime tương thích với JDK đang dùng; stdout và frame chính xác phụ thuộc cách engine weave/cấu hình. Mã bên dưới là **bộ ba tệp mô tả đầu vào cho engine**, không thể biên dịch tệp .aj bằng javac thông thường.

### 7.1. WorkUnit.java

~~~java
public class WorkUnit {
    public void execute(String label) {
        System.out.println("BODY " + label);
    }

    public void callSelf() {
        execute("self");
    }
}
~~~

### 7.2. Client.java

~~~java
public class Client {
    public static void main(String[] args) {
        WorkUnit unit = new WorkUnit();
        unit.execute("external");
        unit.callSelf();
    }
}
~~~

### 7.3. ObserveJoinPoints.aj

~~~java
public aspect ObserveJoinPoints {
    before(): call(void WorkUnit.execute(String)) {
        System.out.println("CALL " + thisJoinPointStaticPart.getSourceLocation());
    }

    before(): execution(void WorkUnit.execute(String)) {
        System.out.println("EXEC " + thisJoinPoint.getArgs()[0]);
    }
}
~~~

Đây là mã theo cú pháp **AspectJ-style .aj**; yêu cầu compiler/weaver hỗ trợ ngôn ngữ aspect, và cần cấu hình classpath/runtime phù hợp với phiên bản thực tế. Phần SOURCE LOCATION có thể khác giữa các môi trường do đường dẫn và thông tin debug; đừng đóng băng chuỗi nguyên văn này làm assertion.

**Điều cần quan sát khi tất cả class liên quan được weave đúng:**

- Với câu lệnh unit.execute("external") ở Client: có **CALL tại Client** và **EXEC của WorkUnit.execute**, ngoài thông báo BODY.
- Với lời gọi execute("self") ở WorkUnit.callSelf: có **CALL trong WorkUnit.callSelf** và **EXEC của WorkUnit.execute**, ngoài thông báo BODY.
- Nếu chỉ weave WorkUnit mà không weave Client theo quy tắc engine, **các call site ở Client có thể thiếu advice call**, trong khi advice execution ở WorkUnit vẫn có thể hoạt động.
- Nếu only call pointcut được chọn, đừng mặc định nó quan sát mọi đường gián tiếp vào WorkUnit. Nếu execution pointcut được chọn, hãy xác nhận class target thực sự đã được weave.

Một cách thực nghiệm phù hợp là sử dụng bộ công cụ AspectJ đã cài theo tài liệu chính thức phiên bản đang sử dụng, biên dịch ba tệp bằng compiler hỗ trợ .aj, xác nhận **log weaving cho cả Client và WorkUnit**, rồi chạy **artifact đã được weave** với runtime tương thích. Vì phiên bản compiler, classpath và cơ chế weave phụ thuộc cài đặt, phần này không đưa ra dòng lệnh giả định chắc chắn có thể chạy trên mọi máy.

### 7.4. Lưu ý về thứ tự advice

Nếu cả hai advice cùng áp dụng cho một lời gọi, CALL và EXEC là **hai ranh giới khác nhau**. Trong đường đi bình thường, advice call ở phía caller có thể chạy trước khi vào phương thức, sau đó advice execution chạy khi tới target. Tuy nhiên, một advice around có thể bỏ qua proceed, trả sớm hoặc ném lỗi; vì vậy trong một ca thực tế không nên khẳng định luôn có đủ cặp CALL/EXEC.

Khi có nhiều aspect, chính sách precedence và cách sắp xếp advice trước/sau/quanh cũng ảnh hưởng thứ tự in. Muốn kết luận cần đọc **event trace có nhãn join point và advice**, không chỉ đếm hai từ CALL và EXEC ở cuối chương trình.

## 8. Bài kiểm tra nâng cao: đa hình và kiểu tĩnh/động

Xét đoạn minh họa:

~~~java
interface Processor {
    void process();
}

final class FastProcessor implements Processor {
    @Override
    public void process() {
        System.out.println("FAST");
    }
}

// Trong một phương thức gọi:
Processor service = new FastProcessor();
service.process();
~~~

Tại call site, kiểu biến được khai báo là Processor, còn object thật là FastProcessor. Phần thực thi diễn ra ở FastProcessor.process() sau dynamic dispatch. Một pointcut gắn với **chữ ký tĩnh ở call site** và một pointcut gắn với **method execution thực tế** có thể match khác nhau, tùy ngôn ngữ pointcut, rule matching và engine.

Cần kiểm tra:
- Chữ ký dùng để match call là chữ ký nào được compiler/weaver ghi nhận?
- Execution advice áp dụng ở interface, class triển khai hay cả hai theo pointcut?
- Target object runtime có phải class con/implementation khác không?
- Method bridge, default method hoặc override có làm sinh thêm phương thức trung gian không?

Đừng phán đoán “call chỉ thấy interface, execution luôn thấy implementation” như một quy tắc tuyệt đối cho mọi AOP framework. Hãy dùng **đúng engine** để xuất thông tin join point và so sánh signature, declaring type, runtime target.

## 9. Bài kiểm tra nâng cao: self-invocation

Có một khác biệt nổi bật giữa cách instrument bằng weaving và chặn thông qua proxy.

### 9.1. Weaving ở call site hoặc method body

Nếu code bên trong WorkUnit.callSelf() được weave, một call join point tại lệnh execute("self") có thể có advice tương ứng. Nếu WorkUnit.execute() được weave theo execution pointcut, mỗi lần vào method đó có thể kích hoạt advice execution, kể cả lời gọi nội bộ.

Tập join point được weave vẫn phụ thuộc luật match, phạm vi class và cấu hình. Không có bằng chứng rằng “mọi self-call đều bị bắt” chỉ vì đã bật một weaver.

### 9.2. Chặn bằng proxy

Một proxy thường hoạt động khi caller đi **qua đối tượng proxy**. Khi target gọi this.execute() hoặc gọi trực tiếp phương thức khác trên chính instance target, đường đi đó có thể không qua proxy. Vì vậy advice chỉ gắn với proxy có thể xuất hiện với lời gọi ngoài nhưng biến mất khi tự gọi.

Tình huống cần kiểm tra:

~~~text
externalCaller --> proxy --> target.callSelf()
                            |
                            +--> this.execute() --> target.execute()
                                 (có thể không đi qua proxy)
~~~

Nếu bạn thử nghiệm bằng proxy nhưng gọi trực tiếp target từ main, bạn có thể bỏ qua chính bộ chặn. Thực nghiệm phải ghi rõ caller giữ reference nào: proxy hay target gốc.

## 10. Bài kiểm tra nâng cao: reflection, method handles và code được sinh

Trong Java, Method.invoke giúp gọi phương thức qua reflection:

~~~java
// Đoạn minh họa, nằm trong phương thức có WorkUnit unit.
java.lang.reflect.Method method =
        WorkUnit.class.getMethod("execute", String.class);
method.invoke(unit, "via-reflection");
~~~

Ở mã nguồn caller, lệnh invocation trực tiếp là **method.invoke(...)**, không phải **unit.execute(...)**. Nếu engine dựa trên weave call site tĩnh, không được tự giả định nó biến lời gọi reflection thành call join point của WorkUnit.execute. Tuy nhiên, nếu thân WorkUnit.execute đã được weave theo execution, việc chạy qua reflection vẫn có thể kích hoạt advice khi method đích thật bắt đầu.

Tương tự, mã được sinh động, method handle, JNI, bytecode generator và classloader khác nhau có thể tạo đường gọi đặc biệt. Hãy tách:

- **Phần mã thực tế chạy tại caller**: nhìn thấy những instruction/call site nào?
- **Phương thức cuối cùng được thực thi**: target nào nhận quyền điều khiển?
- **Phạm vi instrumentation/weaving**: phần bytecode nào đã được engine theo dõi?
- **Log và exception**: dấu vết đang đến từ caller, target, proxy hay code sinh?

Nếu một loại join point không quan sát được, có thể do khả năng engine, cấu hình instrumentation hoặc đường điều khiển khác mô hình, thay vì do AOP “bị hỏng”.

## 11. Advice có thể phá quan hệ một call – một execution

Không phải mọi lời gọi đều tạo đúng một lần thực thi thân target. Dưới đây là các ca quan trọng:

| Trường hợp | Tại đầu gọi | Tại đầu thực thi | Vì sao cần kiểm tra |
| --- | --- | --- | --- |
| Lời gọi bình thường | Một call event cho một lần đi qua call site được instrument | Một execution nếu target được vào bình thường | Baseline |
| Around tại call trả sớm | Có thể có call advice | Không đi vào target | Cache hoặc authorization có chủ đích |
| Around tại call gọi proceed hai lần | Có thể có một lần vào call advice | Có thể thực thi target hai lần | Retry/side effect |
| Around tại execution trả sớm | Call có thể vẫn được quan sát | Advice execution chạy, thân target có thể không chạy | Phân biệt advice entry và body entry |
| Target ném exception | Call đã phát sinh | Target có thể đã chạy rồi throw | Kết quả không thành công vẫn là execution |
| Nhiều caller | Các site khác nhau | Cùng một phương thức được vào nhiều lần | Không đếm call site như invocation |
| Reflection | Caller trực tiếp gọi reflection API | Target có thể được thực thi | Cơ chế chọn join point khác nhau |

**Ví dụ suy luận:** nếu advice around ở phía call dùng cache và trả dữ liệu mà không proceed, bạn có thể thấy một **sự kiện call** và **không có lần execution của target**. Đây có thể là hành vi mong đợi. Nhưng nếu advice logging lại ghi một “method executed” ngay ở call entry, metric sẽ sai.

Trong các phép đo:
- **Số lần gọi logic** không luôn bằng **số attempt**.
- **Số lần advice được kích hoạt** không luôn bằng **số lần thân target thực sự chạy**.
- **Số vị trí call site** là một đại lượng cấu trúc, không phải bộ đếm số lần chạy.

Khi cần kiểm chứng, thêm một counter ngay đầu **thân phương thức đích** và một counter cho mỗi advice/event. Hai bộ đếm này cho biết chính xác điểm nào có khác biệt.

## 12. Pointcut theo vị trí code và theo target

Một lợi ích của việc phân biệt call và execution là thiết kế pointcut có chủ đích.

### 12.1. Muốn quan sát caller

Ví dụ: chỉ theo dõi lời gọi từ module bên ngoài vào một API. Nếu engine hỗ trợ call join point và ràng buộc phạm vi lexical/within/withincode tương ứng, có thể chọn call site thuộc một package hoặc class caller.

Vấn đề là nếu module caller không nằm trong phạm vi weaving, call advice ở đó có thể không xuất hiện. Do vậy **việc phụ thuộc vào call pointcut có thể yêu cầu instrument các consumer**; không chỉ đặt aspect vào thư viện target rồi mặc định mọi caller đã được weave.

### 12.2. Muốn quan sát mọi lần vào target

Ví dụ: đo số lần một implementation xử lý công việc, bất kể được gọi từ module nào. Nếu engine hỗ trợ execution join point, instrumentation trong method đích thường là lựa chọn tự nhiên hơn. Nhưng vẫn phải định nghĩa đúng tập target class được weave và lưu ý advice execution có thể bỏ qua thân method.

### 12.3. Muốn quan sát chính sách ở đầu proxy

Nếu công cụ AOP chỉ hỗ trợ proxy interception, hãy nói rõ **đường gọi qua proxy** là đơn vị quan sát. Đừng đặt tên metric là “mọi method call trong JVM” hoặc kết luận tương đương với call pointcut kiểu weaving. Một interceptor tại proxy có thể truy cập Method/args/target nhưng bản thân điều đó không biến nó thành call-site weaving.

### 12.4. Tránh pointcut quá rộng

Một pointcut call chọn cả lời gọi của logic ghi log vào chính nó có thể gây reentrancy hoặc recursion. Một execution pointcut chọn quá rộng có thể làm chậm cả helper method hoặc biến đổi những nơi không mong muốn.

Thiết kế **positive case và negative case**: một phương thức phải match và một phương thức tuyệt đối không được match. Ghi rõ lý do giới hạn và kiểm tra bằng trace thực tế.

## 13. Cách đọc stack trace khi thực nghiệm

Stack trace cũng giúp truy ra đường đi, nhưng không nên xem nó là danh sách mọi join point AOP:

~~~text
Caller.callerA(...)
  --> Engine.run(...)
      --> lỗi được ném
~~~

Khi exception được tạo ở Engine.run, stack trace thường hiển thị method đích gần phía trên và caller ở bên dưới. Nếu có proxy/reflection/weaver, có thể xuất hiện thêm frame trung gian hoặc mã sinh. Ngược lại, advice được weave trực tiếp vào bytecode có thể **không có frame riêng**.

Phân biệt:

- **Call stack hiện tại** tại thời điểm chụp.
- **Stack trace đã lưu trong Throwable** tại thời điểm lỗi được ghi, có thể chứa frame method đã unwind khỏi stack hiện tại.
- **Các event call/execution** do advice hoặc instrumentation ghi: đây là dòng thời gian sự kiện, không nhất thiết bằng một stack snapshot.

Một trường hợp exception được ném ở target rồi bị advice catch và trả fallback có thể có execution event nhưng không có exception thoát ra caller. Nếu advice ở call site ném lỗi trước khi proceed, có thể không có execution event target. Vì vậy phải kết hợp trace, event log, số attempt và kết quả cuối.

## 14. Thiết kế bài thực nghiệm có phép kiểm soát

Để kết luận vững, thực hiện theo ma trận thay vì chỉ chạy một lần:

| Lượt | Đổi điều kiện | Giữ cố định | Bằng chứng cần thu |
| --- | --- | --- | --- |
| A | Gọi trực tiếp từ callerA | Cùng target, cùng đầu vào | Call marker và execution marker |
| B | Gọi từ callerB | Cùng target implementation | Phân biệt call site |
| C | Gọi từ chính target | Cùng method run | Tính hiện diện của self-call |
| D | Gọi qua reflection | Cùng method run | Caller gọi Method.invoke và target vẫn vào method |
| E | Chỉ bật advice call ở engine thật | Cùng mã và weaving scope đã ghi | Match ở site nào? |
| F | Chỉ bật advice execution | Cùng mã và weaving scope | Target nào thật sự được instrument? |
| G | Bật cả hai loại advice | Cùng mã, cùng input | Thứ tự event, số lần, exception |
| H | Around call chặn hoặc retry (nếu được phép) | Cùng target | Số lần call advice so với target-body |
| I | Đổi phạm vi weaving của caller | Không đổi target | Call site biến mất/xuất hiện ra sao? |

**Trong mỗi lượt, chỉ thay một yếu tố** và lưu:
- JDK/phiên bản engine AOP, compiler/weaver, build flags.
- Class nào đã được weave; class nào do proxy tạo; caller có đi qua proxy không.
- Source file, method, thread, logical request id, attempt id.
- Kết quả và exception thực tế, counter ở thân target, log từ call/execution advice.
- Lệnh chạy và output mong đợi/quan sát được.

Nếu thử nghiệm nhận kết quả không trùng dự đoán, trước hết kiểm tra việc method thực sự được gọi và việc engine có instrument đúng bytecode. Thiếu event không mặc nhiên chứng minh lỗi logic.

## 15. Lỗi thường gặp khi phân tích hai loại join point

1. **Gọi call site là mọi lần execution:** một chỗ gọi có thể thực thi 100 lần, một phương thức có thể có 100 caller khác nhau. Luôn đếm riêng vị trí và sự kiện.
2. **Tưởng proxy interception là call pointcut đầy đủ:** proxy thường chỉ nhận lời gọi qua chính proxy, không weave mọi caller.
3. **Bỏ qua self-invocation:** test ngoài chạy nhưng lời gọi nội bộ bypass proxy; với weaving, kết quả còn tùy phạm vi instrument.
4. **Dùng reflection rồi mong có lời gọi tĩnh đến target:** tại caller, mã gọi reflection API. Phân biệt đường đi đến target với bytecode call site.
5. **Cho rằng không thấy EXEC là target không làm gì:** có thể log hoặc advice bị thiếu do cấu hình; cần counter ở thân target và trace thật.
6. **Cho rằng nhìn thấy execution advice là body đã chạy:** around advice có thể bỏ qua proceed. Dùng body-entry marker để chứng minh.
7. **Bỏ qua dynamic dispatch:** call signature và execution implementation có thể match khác nhau khi có interface, kế thừa, override.
8. **Không ghi phạm vi weave:** class caller có thể chưa được weave dù class target đã được weave; call advice ở caller khi đó không xuất hiện.
9. **Trộn sự kiện với stack snapshot:** một stack trace không kể đủ toàn bộ lịch sử các call/execution advice.
10. **Đo số lần chạy từ logger thay vì target:** retry, cache, fallback hoặc lỗi logger làm log và thực thi khác nhau.
11. **Dựa vào thứ tự file để suy ra advice order:** precedence cần cấu hình và kiểm chứng theo engine thực tế.
12. **Bật/tắt nhiều điều kiện cùng lúc:** không còn cơ sở kết luận thay đổi nào làm phát sinh hoặc loại bỏ khác biệt.

## 16. Bài tập tự luyện với tiêu chí chấm rõ ràng

### Bài A — Lặp một call site

Trong main của CallExecutionLab, thêm vòng lặp gọi callerA(engine) ba lần. Trước khi chạy, dự đoán có mấy dòng CALL callerA và EXEC Engine.run(A) mới. Sau khi chạy, giải thích sự khác biệt giữa **số dòng log tăng** và **số vị trí câu lệnh gọi trong source**.

**Tiêu chí:** ba lần gọi callerA tạo thêm ba cặp sự kiện; call site trong callerA vẫn là một vị trí mã.

### Bài B — Tạo hai implementation

Thay Engine bằng interface Worker có hai implementation; cho một caller giữ reference Worker và lần lượt gán mỗi implementation. Gắn marker trong cả hai method body. Dự đoán caller có thay đổi vị trí lệnh call hay không, và execution đi vào implementation nào.

**Tiêu chí:** phân biệt kiểu khai báo của reference, dispatch runtime và nơi execution thật.

### Bài C — Tạo lời gọi gián tiếp

Viết thêm một helper dùng Method.invoke hoặc MethodHandle để gọi Engine.run. Ghi marker ở lệnh gọi helper, ở invocation API và ở thân target. So sánh với callerA trực tiếp.

**Tiêu chí:** không tự đặt nhãn CALL Engine.run cho một bytecode site chỉ gọi Method.invoke; chỉ phát biểu về AOP call pointcut sau khi có engine test.

### Bài D — So sánh weaving và proxy

Trên một engine AOP có hỗ trợ weaving call/execution, chạy cùng target với hai advice và ghi log join point signature, source location, thread. Nếu có thể, chạy thêm proxy interception để so sánh self-invocation.

**Tiêu chí:** nêu rõ engine/phiên bản, class được weave, đường đi qua proxy, các sự kiện quan sát thực tế và giới hạn kết luận.

### Bài E — Around chặn trước target

Tạo một advice around ở call join point cho phép trả kết quả cache mà không proceed. Đặt counter trong target. Gọi hai lần với dữ liệu đã cache/không cache và so sánh call advice, execution advice và body count.

**Tiêu chí:** phân biệt hành vi được thiết kế với “thiếu execution do cấu hình”, đồng thời không chấp nhận dữ liệu cache sai hợp đồng.

## 17. Checklist trước khi kết luận

- [ ] Tôi biết đang so sánh **vị trí call site**, **số lần sự kiện call** hay **số lần method execution**.
- [ ] Tôi phân biệt được caller và callee, cùng điểm ghi sự kiện cho từng bên.
- [ ] Tôi đã thử tối thiểu hai caller, một self-call và một lời gọi gián tiếp.
- [ ] Tôi biết ví dụ Java tự ghi marker hay engine AOP thật sự tạo join point; không đánh đồng hai loại bằng chứng.
- [ ] Tôi đã xác minh cơ chế proxy/weaving, engine version, tập class được instrument và pointcut.
- [ ] Tôi hiểu rằng advice around có thể bỏ qua hoặc gọi proceed nhiều lần.
- [ ] Tôi đã xem xét đa hình, reflection, exception, lời gọi nội bộ và mã sinh nếu chúng liên quan.
- [ ] Tôi có stdout/event trace hoặc assertion chính xác kèm số lần target thực chạy.
- [ ] Tôi có cả test phải match lẫn test không được match, và thay một điều kiện mỗi lượt.
- [ ] Mỗi kết luận chỉ nói về cơ chế, engine và tình huống đã kiểm chứng.

**Kết luận thực nghiệm:** điểm gọi trả lời **“lời gọi được thực hiện ở đâu, bởi ai?”**, còn điểm thực thi trả lời **“phương thức nào đang được vào thực thi?”**. Chúng liên quan nhưng không thay thế nhau. Bằng cách tách call-site event, target-body event và interception event, ta có thể giải thích đúng trường hợp nhiều caller, tự gọi, reflection, đa hình, retry hoặc short-circuit, đồng thời tránh quy kết sai cho AOP chỉ vì một marker không xuất hiện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-005">So sánh lời gọi qua proxy với cơ chế biến đổi mã</a>

<details>
<summary>Xem chi tiết</summary>

# Bài tập tổng hợp: So sánh lời gọi qua proxy với cơ chế biến đổi mã trong AOP

## 1. Mục tiêu của bài

AOP (Aspect-Oriented Programming, lập trình hướng khía cạnh) giúp tách hành vi xuyên suốt như logging, kiểm tra truy cập, đo thời gian hoặc theo dõi lỗi khỏi mã nghiệp vụ. Tuy nhiên, **“có aspect” không có nghĩa mọi phương thức đều bị chặn theo cùng một cách**. Hai cơ chế thường được so sánh là:

1. **Can thiệp lời gọi qua proxy:** caller gọi vào một đối tượng trung gian. Proxy nhận lời gọi, chạy logic bổ sung rồi chuyển tiếp đến đối tượng đích hoặc một tầng proxy khác.
2. **Can thiệp bằng weaving/biến đổi mã:** trình dệt chọn các join point và chèn hoặc biến đổi bytecode tại những vị trí tương ứng. Khi mã đã dệt thực thi, advice chạy ngay ở vị trí được tích hợp, không cần mọi caller đều giữ tham chiếu tới một wrapper proxy.

Bài tập yêu cầu **dự đoán kết quả, chạy hai cấu hình trên cùng một mã nghiệp vụ, giải thích sự khác biệt và chọn giải pháp theo yêu cầu**. Dùng Java 17 và JDK dynamic proxy cho nhánh proxy; dùng AspectJ code-style cho nhánh bytecode weaving. Đây là ví dụ cụ thể để học cơ chế **AOP tổng quát**, không phải ví dụ về một hệ thống dependency injection hay framework ứng dụng riêng.

Sau bài này, người học cần:

- Vẽ được đường đi **caller → proxy → handler → target** và đường đi **caller → mã đã dệt → advice/thân gốc**.
- Phân biệt interface proxy của JDK với proxy tạo subclass; phân biệt proxy ở runtime với compile-time/binary/load-time weaving.
- Giải thích đúng vì sao **gọi trực tiếp target** không đi qua JDK proxy, và vì sao **self-invocation (`this.inner()`)** trên target có thể không vào proxy ủy quyền.
- Hiểu khác biệt giữa join point `call` và `execution` trong AspectJ; biết dệt vào **caller** hay **callee** dẫn đến kết quả khác.
- Quan sát advice, exception, `around`/`proceed` và phạm vi can thiệp; không suy diễn từ tên công nghệ.
- Đo overhead công bằng, cân nhắc giới hạn, khả năng bảo trì và an toàn vận hành.

## 2. Khái niệm: advice được thực thi ở đâu?

### 2.1. Proxy-based interception

Proxy là đối tượng thay mặt đối tượng đích tiếp nhận lời gọi. Với **`java.lang.reflect.Proxy`**, JVM tạo một đối tượng triển khai các interface được chỉ định. Mỗi lời gọi phương thức qua interface trên đối tượng proxy được chuyển vào `InvocationHandler.invoke(proxy, method, args)`; handler tự quyết định có gọi target hay không. Handler có thể chạy logic trước/sau, xử lý ngoại lệ, thay tham số hoặc trả giá trị khác.

```text
Client
  |
  | Work.outer() qua tham chiếu proxy
  v
JDK Proxy implements Work
  |
  v
InvocationHandler.invoke(...)
  |
  | Method.invoke(target, args) nếu muốn chuyển tiếp
  v
Target.outer()
  |
  v
Target.inner()  // self-invocation trên target
```

Điểm quan trọng là **hành vi bổ sung nằm trên tuyến lời gọi đi qua proxy**. Nếu client có một tham chiếu trực tiếp đến target rồi gọi target, handler không biết lời gọi ấy. Nếu phương thức target gọi phương thức khác qua `this`, lời gọi nội bộ vẫn chạy trên target và không tự quay lại wrapper proxy ủy quyền.

JDK dynamic proxy cần **interface** phù hợp; nó không tự tạo subclass của một lớp Java bất kỳ. Một thư viện AOP cũng có thể tạo **class-based proxy** bằng subclass/interception. Với class-based proxy, khả năng chặn self-invocation **phụ thuộc thiết kế**: nếu lời gọi nội bộ chạy trên chính đối tượng subclass và dùng virtual dispatch đến override, lời gọi có thể bị chặn; nếu proxy ủy quyền sang một target tách biệt, self-invocation của target có thể bỏ qua proxy. Vì vậy, không được khẳng định “mọi proxy đều bỏ qua mọi self-invocation”.

### 2.2. Bytecode weaving

Với kiểu AOP dệt mã, công cụ phân tích aspect và các **join point shadow** trong bytecode/source được cung cấp rồi tích hợp advice. Trong AspectJ, pointcut `execution(...)` chọn nơi **thân phương thức được thực thi**, còn `call(...)` chọn **vị trí gọi phương thức**. Ngoài phương thức, mô hình AspectJ còn có các join point phù hợp khác như constructor, field access/set và exception handler theo các giới hạn riêng.

```text
Client ----> Target.outer() [mã đã dệt]
                           |
                      advice outer
                           |
                      thân outer
                           |
                      Target.inner() [mã đã dệt]
                           |
                      advice inner
                           |
                      thân inner
```

Khi `Target.outer` và `Target.inner` đều đã được dệt với `execution` advice, lời gọi bên trong target vẫn đi vào thân `inner` đã được biến đổi. Không cần chuyển lời gọi từ `this` ra một wrapper khác.

**Không phải “mọi bytecode trên classpath đều tự được dệt”.** Kết quả phụ thuộc những class được đưa vào compilation/binary weaving, phạm vi include/exclude, cấu hình load-time weaver, class loader, khả năng hỗ trợ join point và thứ tự nạp lớp. Một class chưa được dệt không có lời đảm bảo được advice áp dụng chỉ vì tồn tại aspect trong project.

### 2.3. Thời điểm can thiệp

| Cơ chế | Khi nào tạo hiệu ứng can thiệp? | Lời gọi phải qua đối tượng trung gian? |
| --- | --- | --- |
| JDK dynamic proxy | Proxy class được tạo/cached lúc runtime; handler chạy tại mỗi lời gọi qua proxy | Có, đối với interface dispatch qua proxy |
| Class-based proxy | Proxy subclass/wrapper được tạo hoặc cấu hình lúc runtime tùy thư viện | Lời gọi phải đến đường dispatch có thể chặn |
| Compile-time weaving (CTW) | Chèn advice trong quá trình biên dịch nguồn | Không, nếu method đã được dệt ở execution join point |
| Post-compile/binary weaving | Biến đổi `.class`/JAR đã biên dịch | Không, với execution join point trong class đã dệt |
| Load-time weaving (LTW) | Biến đổi bytecode lúc class loader nạp class | Không, sau khi class được dệt đúng cách |

Một implementation thực tế có thể kết hợp nhiều tầng; dùng proxy không loại trừ dùng weaving. Phân tích **điểm chặn cụ thể** quan trọng hơn việc chỉ biết nhãn công nghệ.

## 3. Thí nghiệm A: JDK dynamic proxy thuần Java 17

Tạo thư mục `src/demo`. Ví dụ có một interface `Work`, target `Target` và một proxy dùng `InvocationHandler`. `outer` gọi `inner` bằng phương thức instance thường trên cùng target. Cuối cùng, chương trình gọi `outer` một lần **qua proxy** và một lần **trực tiếp qua target**.

**Tệp `src/demo/Main.java`:**

```java
package demo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class Main {
    public static void main(String[] args) {
        Work target = new Target();

        Work proxied = (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[] { Work.class },
                (proxy, method, methodArgs) -> {
                    System.out.println("proxy enter " + method.getName());
                    try {
                        return method.invoke(target, methodArgs);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    } finally {
                        System.out.println("proxy exit " + method.getName());
                    }
                });

        System.out.println("-- via proxy --");
        proxied.outer();

        System.out.println("-- direct target --");
        target.outer();
    }
}

interface Work {
    void outer();
    void inner();
}

class Target implements Work {
    @Override
    public void outer() {
        System.out.println("target outer");
        inner();
    }

    @Override
    public void inner() {
        System.out.println("target inner");
    }
}
```

**Biên dịch và chạy với JDK 17+:**

```bash
javac -d out-java src/demo/Main.java
java -cp out-java demo.Main
```

**Stdout chính xác:**

```text
-- via proxy --
proxy enter outer
target outer
target inner
proxy exit outer
-- direct target --
target outer
target inner
```

### 3.1. Phân tích từng sự kiện

1. `proxied.outer()` là một lời gọi qua interface của proxy. JVM dispatch vào `InvocationHandler.invoke` với `method.getName()` là `outer`.
2. Handler in `proxy enter outer` rồi gọi `method.invoke(target, methodArgs)`. Target chạy `outer`, in `target outer` và gọi `inner()` trên chính target.
3. Lời gọi `inner()` **không** đi vào handler, vì proxy chỉ đang chuyển tiếp một lần đến target và không được target sử dụng khi tự gọi.
4. Khi target `outer` trả về, handler chạy `finally` và in `proxy exit outer`.
5. `target.outer()` là gọi trực tiếp target; lần này ngay cả `outer` cũng không qua proxy, nên hoàn toàn không có hai dòng `proxy enter/exit`.

**Chú ý exception:** `Method.invoke` bọc ngoại lệ do method đích ném trong `InvocationTargetException`. Handler trên dùng `throw e.getCause()` để chuyển lỗi nghiệp vụ thật ra ngoài. Với checked exception, hợp đồng throws của interface và quy tắc `UndeclaredThrowableException` của dynamic proxy vẫn cần tuân thủ. `finally` chạy cả khi target ném lỗi.

### 3.2. Biến thể kiểm thử

- Thay `proxied.outer()` bằng `proxied.inner()`: handler sẽ in `proxy enter inner` và `proxy exit inner` vì lời gọi đi **trực tiếp qua proxy**.
- Thay `target.outer()` bằng `target.inner()`: không in dòng nào của handler.
- Thêm log/đo thời gian trong handler: nó đo lời gọi qua wrapper, không tự động đo lời gọi nội bộ trên target.
- Thêm `proxied.equals(...)` hoặc `proxied.toString()`: `equals`, `hashCode` và `toString` còn được dispatch qua handler theo quy tắc JDK proxy; handler production cần thiết kế ngữ nghĩa cho chúng, không giả định mọi method đều là nghiệp vụ.
- Đừng gọi lại `method.invoke(proxy, ...)` ngay trong handler để “chuyển tiếp”: điều đó có thể tái vào chính handler và tạo đệ quy vô hạn. Trong ví dụ, lời gọi chuyển tới **target** là cố ý.

## 4. Thí nghiệm B: dệt advice vào bytecode của chính Target

Giữ **nguyên `src/demo/Main.java`**. Thêm một aspect theo cú pháp AspectJ:

**Tệp `src/demo/TraceAspect.aj`:**

```aspectj
package demo;

public aspect TraceAspect {
    before(): execution(void demo.Target.outer()) {
        System.out.println("weave execution outer");
    }

    before(): execution(void demo.Target.inner()) {
        System.out.println("weave execution inner");
    }
}
```

Hai pointcut chọn **execution join point của đúng phương thức trong `demo.Target`**. Chúng không chọn `InvocationHandler.invoke`, không chọn method interface `Work`, cũng không tự động thêm một handler mới vào proxy. Target class được dệt mới là nơi chứa hiệu ứng.

Cần bộ **AspectJ compiler `ajc` hỗ trợ Java 17** và runtime `aspectjrt.jar`. Giả sử `ajc` có trên PATH, và `aspectjrt.jar` ở thư mục project. **Không dùng thư mục output Java chưa dệt làm input ngầm cho lần build này**; build nguồn sạch vào thư mục `out-woven` riêng:

**Windows PowerShell:**

```powershell
ajc -17 -showWeaveInfo -d out-woven -classpath "aspectjrt.jar" src/demo/Main.java src/demo/TraceAspect.aj
java -cp "out-woven;aspectjrt.jar" demo.Main
```

**Linux/macOS:**

```bash
ajc -17 -showWeaveInfo -d out-woven -classpath aspectjrt.jar src/demo/Main.java src/demo/TraceAspect.aj
java -cp "out-woven:aspectjrt.jar" demo.Main
```

**Stdout của `demo.Main` khi chạy bản đã dệt** (bỏ qua các thông báo `-showWeaveInfo` của compiler):

```text
-- via proxy --
proxy enter outer
weave execution outer
target outer
weave execution inner
target inner
proxy exit outer
-- direct target --
weave execution outer
target outer
weave execution inner
target inner
```

### 4.1. Vì sao lại có những dòng này?

- Đường **qua proxy** vẫn in `proxy enter/exit outer`, vì `proxied.outer()` đi qua `InvocationHandler` của JDK dynamic proxy.
- Khi handler chuyển tiếp `method.invoke(target, ...)`, việc **thực thi `Target.outer`** đã dệt kích hoạt advice `before` và in `weave execution outer`.
- Trong `Target.outer`, lời gọi `inner()` thực thi **`Target.inner` cũng đã dệt**, nên in `weave execution inner` dù không đi qua handler.
- Đường **gọi trực tiếp `target.outer()`** vẫn kích hoạt hai advice execution của bytecode weaving, nhưng không có dòng `proxy enter/exit` vì không có tuyến gọi qua proxy.

Đây là chứng cứ khác nhau về **đường can thiệp**, không phải khẳng định weaving luôn tốt hơn proxy. Ở ví dụ, cả hai cơ chế **cùng đang hoạt động** khi gọi qua `proxied`; do đó có thể nhìn rõ từng tầng chặn.

### 4.2. Có thể dệt vào caller thay vì callee không?

Có. AspectJ có pointcut `call(...)`: nó nhận diện **vị trí lời gọi trong mã đã dệt**. Nếu muốn can thiệp một lời gọi `inner` bằng `call`, phải bảo đảm class **chứa lời gọi** có trong phạm vi dệt. `execution` chọn vị trí thân method của callee; nếu callee đã dệt thì caller trực tiếp hay caller đi qua proxy vẫn có thể kích hoạt advice.

Không đồng nhất `call` với `execution`:

| Khía cạnh | `call` | `execution` |
| --- | --- | --- |
| Join point shadow | Nằm ở **vị trí gọi** | Nằm tại **thân phương thức được thực thi** |
| Điều kiện để dệt | Bytecode ở caller có trong phạm vi dệt | Bytecode của callee có trong phạm vi dệt |
| Trường hợp caller không được dệt | Không có chặn tại call site đó | Vẫn có thể chặn nếu callee đã dệt |
| Lời gọi nội bộ | Có thể chọn nơi `outer` gọi `inner` nếu caller code được dệt và pointcut khớp | Chọn việc `inner` được thực thi nếu body đã dệt |
| Khác biệt ngữ nghĩa | Gắn với site gọi, kiểu tĩnh của tham chiếu và luật method call | Gắn với method body, class/override được thực thi |

Tùy pointcut, weaving mode, bytecode đầu vào và giới hạn của engine, một số call site (đặc biệt các lệnh gọi `super` đặc thù) không hoạt động giống ordinary virtual calls. Vì thế, không đổi `call` thành `execution` mà không kiểm thử semantics.

## 5. Bảng so sánh hệ thống

| Tiêu chí | Proxy chặn lời gọi | Weaving/biến đổi bytecode |
| --- | --- | --- |
| Điểm mở rộng | Đối tượng trung gian và handler/interceptor | Join point shadow trong class/call site được dệt |
| Yêu cầu caller | Phải gọi qua proxy phù hợp để interceptor chạy | Phải thực thi mã đã dệt hoặc gọi vào join point đã được biến đổi |
| JDK dynamic proxy | Chủ yếu interface methods được chọn | Không dựa trên interface wrapper của JDK |
| Class-based proxy | Có thể subclass/chặn virtual methods tùy thư viện | Có thể chọn method execution/call hoặc join point khác tùy weaver |
| Direct reference đến target | Bỏ qua proxy ủy quyền | Vẫn áp dụng tại method body đã dệt |
| Self-invocation | JDK proxy ủy quyền sang target thường không bắt lời gọi nội bộ của target | Execution advice trên inner body đã dệt vẫn chạy |
| Phương thức không public | JDK interface proxy không chặn arbitrary private method; class proxy có giới hạn override | Có thể can thiệp method execution phù hợp, kể cả không public nếu engine có quyền dệt class |
| `final` / `private` / `static` | Không thể override chúng theo subclass interception thông thường; interface proxy không bao trùm arbitrary methods | Dệt bytecode có thể chọn execution của nhiều phương thức dạng này nếu join point hợp lệ và class được dệt |
| Constructor/field access | JDK dynamic proxy không tự bọc constructor hay field read/write | Weaver hỗ trợ pointcut constructor/field tùy mô hình, trừ trường hợp bị giới hạn/inlined |
| Kích hoạt runtime | Handler chạy tại mỗi lời gọi qua proxy | Mã đã dệt được thực thi ở các vị trí được chọn |
| Kiểm soát phạm vi | Qua đối tượng được trao cho caller, interface và proxy factory | Qua phạm vi weaving, pointcut, loại join point và loader |
| Debug | Có thêm proxy/handler frame | Có thể có helper/source mapping của bytecode đã dệt |
| Deployment | Tạo và phân phối proxy đúng nơi; không nhất thiết sửa class đích | Cần build/weaver/agent và quản lý artifact/classloader |
| Rủi ro cấu hình | Lộ target gốc, thiếu proxy, self-invocation, chain interceptor | Thiếu weave input, agent chạy muộn, dệt sai/mở rộng pointcut, chồng lời khuyên |
| Chi phí | Dispatch proxy, handler, reflection/delegation nếu sử dụng | Chi phí lúc dệt và phí guard/advice khi thực thi mã đã dệt |
| Khả năng quan sát | Có thể tập trung trong handler/proxy | Có thể dệt tại các join point mà wrapper proxy không chặn được |

**Ghi chú:** `final` và `private` có ý nghĩa khác với mọi loại proxy, và weaving không bảo đảm can thiệp được *mọi* kiểu bytecode. Ví dụ Java cho phép inline tham chiếu hằng `static final` compile-time constant, nên pointcut đọc field theo `get` không quan sát một lần đọc vốn không còn instruction truy cập field trong bytecode.

## 6. Bài tập áp dụng: dự đoán trước khi chạy

Với `Main.java` ở mục 3 và `TraceAspect.aj` ở mục 4, hãy viết bảng dự đoán trước khi chạy:

| Hành động | Có vào JDK handler? | Có advice `execution outer`? | Có advice `execution inner`? |
| --- | --- | --- | --- |
| `proxied.outer()` khi chỉ `javac` | Có, cho `outer` | Không | Không |
| `target.outer()` khi chỉ `javac` | Không | Không | Không |
| `proxied.inner()` khi chỉ `javac` | Có, cho `inner` | Không | Không |
| `proxied.outer()` khi chạy class đã dệt | Có, cho `outer` | Có | Có |
| `target.outer()` khi chạy class đã dệt | Không | Có | Có |
| `proxied.inner()` khi chạy class đã dệt | Có, cho `inner` | Không | Có |

**Lý giải:** ở hàng `proxied.inner()`, `outer` không được gọi nên advice cho execution của `outer` không chạy. Lời gọi đi qua handler với method `inner`, và thân `Target.inner` đã dệt kích hoạt advice tương ứng.

Thực hành mở rộng:

1. **Thêm `target.inner()` sau lời gọi trực tiếp:** dự đoán không có log proxy; trong bản woven có log `weave execution inner`.
2. **Di chuyển lời gọi `inner()` sang caller:** ví dụ `proxied.outer(); proxied.inner();`. Khi đó proxy intercept hai lời gọi interface riêng biệt; so với lời gọi nội bộ của target trong `outer`.
3. **Thêm around cho `Target.inner`:** chạy `proceed()` một lần, sau đó thử short-circuit. Quan sát target `inner` có chạy không khi around không gọi tiếp.
4. **Biến đổi argument/return của advice:** xác định tầng thay đổi dữ liệu, kết quả và thứ tự khi phối hợp proxy với weaving.
5. **Thêm method không có trong `Work`:** JDK dynamic proxy kiểu này không công bố method đó qua interface `Work`; thử gọi trực tiếp target, rồi dệt execution pointcut phù hợp.
6. **Tạo subclass-based proxy bằng thư viện thích hợp** (thực nghiệm riêng): kiểm tra đúng behavior của self-invocation theo cơ chế subclass hoặc delegation. Không mặc định nó giống JDK dynamic proxy.

## 7. `around` / `proceed` so với handler chuyển tiếp

Trong proxy, `InvocationHandler` có thể **chuyển tiếp** bằng `method.invoke(target, args)`, tự trả một kết quả, hoặc ném lỗi; việc chuyển tiếp chỉ nằm trong logic do handler thiết kế. Trong bytecode weaving kiểu AspectJ, một `around advice` có thể gọi `proceed(...)` để tiếp tục phần tính toán của join point, bao gồm advice khác nếu có. Hai khái niệm có vẻ giống về mục tiêu nhưng **không phải cùng API**.

Ví dụ minh họa AspectJ (đoạn thay cho advice tương ứng; không thêm đồng thời nếu sẽ chọn cùng join point và muốn output ở mục 4):

```aspectj
void around(): execution(void demo.Target.inner()) {
    System.out.println("around before inner");
    try {
        proceed();
    } finally {
        System.out.println("around after inner");
    }
}
```

Với `inner` thực thi bình thường, các dòng theo đường quanh sẽ là `around before inner`, `target inner`, `around after inner`. Nếu `around` không gọi `proceed`, nội dung `Target.inner` được bao quanh có thể không chạy. Nếu gọi `proceed` nhiều lần, phần tính toán phía trong có thể thực thi nhiều lần — cần cẩn trọng với I/O, giao dịch, thao tác không idempotent.

Tại thời điểm weaving, cách mã được tạo ra còn phụ thuộc độ ưu tiên advice; nếu nhiều around cùng match, `proceed` có thể vào tầng advice khác trước khi đến method body. Proxy chain cũng có thứ tự, nhưng thứ tự đó có thể được quản lý bằng wrapper composition/interceptor ordering khác với precedence của bytecode weaver. Hãy kiểm tra dựa trên **đường gọi thực tế**, không chỉ tên lớp.

### 7.1. Ngoại lệ qua hai cơ chế

- JDK dynamic proxy: target throw → `Method.invoke` có thể bọc trong `InvocationTargetException` → handler có thể unwrap → caller thấy exception theo hợp đồng interface. Một checked exception không được phương thức interface khai báo có thể trở thành `UndeclaredThrowableException`.
- Weaving: advice `before` có thể throw trước thân gốc; `around` có thể catch/wrap/throw; `after throwing` và `after` phản ánh đường lỗi theo semantics của join point. Trên code đã dệt, exception không đi qua `InvocationTargetException` trừ khi có đường reflection thật sự.
- Khi **cả proxy và weaving** cùng hoạt động, lỗi có thể đi qua **cả hai**: code đã dệt throw trước, reflection của handler bắt và bọc, handler unwrap trước khi trả lên caller. Thứ tự và loại lỗi quan sát được phải kiểm tra thực nghiệm.

## 8. Tình huống chọn cơ chế AOP

### 8.1. Chỉ cần các lời gọi đi qua API công khai

Một hệ thống dùng interface cho những dịch vụ công khai và mọi caller đều được cấp tham chiếu tới proxy. Nếu mục tiêu chỉ là logging/metrics/policy tại ranh giới API này, proxy có thể đủ và dễ bật/tắt theo cấu hình. Nhưng phải kiểm soát không để caller giữ target gốc ngoài dự kiến và ghi rõ handler nào áp dụng cho method nào.

### 8.2. Cần can thiệp internal method hoặc join point ngoài API

Nếu yêu cầu phải áp dụng cho nội bộ class, private execution được hỗ trợ, constructor hoặc field access, interface proxy thông thường không bao quát đủ. Weaving giúp đưa advice vào những join point tương ứng **nếu chúng được weaver hỗ trợ và mã nằm trong phạm vi dệt**. Chi phí là cần quản lý toolchain, build/reweaving, bytecode compatibility và cấu hình class loader.

### 8.3. Cần cập nhật logic mà không rebuild target class

Một proxy có thể thay hành vi bằng cấu hình/factory và object graph mới mà không sửa file `.class` của target. Tuy vậy, để thay đổi những caller đang giữ target cũ, thường vẫn phải thay tham chiếu hoặc quản lý điểm tạo đối tượng. LTW cũng có thể biến đổi class lúc nạp, nhưng **không được giả định có thể tự động áp dụng cho những class đã nạp trước đó**; agent/instrumentation và support retransform quyết định khả năng này.

### 8.4. Yêu cầu liên quan bảo mật

Không coi proxy hoặc weaving là **ranh giới bảo mật duy nhất** nếu có thể gọi code theo đường khác: bỏ qua proxy, chạy bytecode chưa dệt, thay class loader hoặc không nạp weaver có thể thay đổi nơi advice chạy. Chính sách bảo mật quan trọng phải được thiết kế với ranh giới kiểm soát tin cậy; test cần chứng minh mọi đường truy cập thực tế đều được kiểm tra, không chỉ đường demo thành công.

## 9. Hiệu năng: so sánh đúng bản chất

Không nên kết luận **proxy luôn chậm hơn weaving** hoặc ngược lại. Chi phí phụ thuộc implementation, pointcut, guard động, advice, reflection, JIT, inline, boxing, allocations, số tầng interceptor và tần suất gọi.

### 9.1. Mô hình chi phí đơn giản

```text
T_proxy_call ≈ T_dispatch_proxy
             + T_handler
             + T_delegation_or_reflection
             + T_target
             + T_advice_work

T_woven_call ≈ T_woven_dispatch_or_guard
             + T_target
             + T_advice_work

T_total_weaving ≈ T_build_or_load_weaving
               + sum(T_runtime_woven_calls)
```

Đây là mô hình định tính. Bộ tối ưu JIT có thể inline một phần proxy handler/delegation hoặc advice woven; dynamic matching và thông tin join point có thể tốn thêm runtime. Build-time weaving chuyển một phần chi phí vào build; LTW có thể tác động thời gian class loading/startup. **Phí thật của advice** (I/O, logging, lock) thường lớn hơn phần dispatch.

### 9.2. Thiết kế benchmark công bằng

1. Dùng cùng tác vụ nghiệp vụ, cùng JDK/JVM flags, cùng máy, cùng dữ liệu.
2. Đo ít nhất ba bản: **target trực tiếp**, **target qua proxy**, **target với advice woven**. Nếu cần đánh giá phối hợp, thêm bản **proxy + woven**.
3. Giữ nội dung advice tương đương. Nếu bản proxy in console mà bản weaving không in, phép đo không so overhead can thiệp.
4. Không ghi console trong vòng đo; dùng JMH hoặc benchmark được thiết kế chuẩn có warm-up, nhiều iterations/forks và chống dead-code elimination.
5. Đo riêng creation/class load/build overhead và steady-state call latency/throughput; theo dõi allocation, GC và tail latency khi thích hợp.
6. Thử cả lời gọi qua proxy, direct target, self-invocation, dynamic condition true/false, số lượng method và concurrency thực tế.
7. Đọc profiler và bytecode khi có khác biệt; đừng suy từ một con số microbenchmark rằng lựa chọn nào tối ưu cho mọi hệ thống.

Ví dụ các dòng `System.out.println` trong hai thí nghiệm chỉ là **trace để hiểu semantics**, không phải mã benchmark. Một trace in nhiều dòng hơn không chứng minh cơ chế đó chậm hơn về chi phí dispatch thuần túy.

## 10. Triển khai và gỡ lỗi

### 10.1. Dấu hiệu proxy không được gọi

- Đối tượng được truyền vào caller là **target**, không phải proxy.
- Một method được gọi từ trong target qua `this`, trong kiến trúc proxy ủy quyền sang target tách biệt.
- Method không được công bố qua interface của JDK dynamic proxy hoặc interceptor bị giới hạn method.
- Proxy subclass không thể override method cần chặn (ví dụ `final`/`private`), hoặc lời gọi không đi qua override.
- Handler đã intercept nhưng branch/cấu hình quyết định **không gọi** advice.

Kiểm tra kiểu thực của object tại caller, `Proxy.isProxyClass(...)` nếu là JDK proxy, `Proxy.getInvocationHandler(...)` khi phù hợp, và breakpoint trong `InvocationHandler.invoke`. Với class-based proxy, dùng cơ chế inspect của thư viện proxy đang sử dụng.

### 10.2. Dấu hiệu weaving không áp dụng

- Class đích không nằm trong input/`-inpath`/phạm vi include của weaver.
- Pointcut khớp sai signature, package, overload hoặc loại `call`/`execution`.
- Chạy class từ output **chưa dệt** hoặc JAR cũ trên classpath.
- LTW agent không khởi động đúng lúc hoặc class đã nạp qua loader ngoài cấu hình.
- Advice có điều kiện động false ở lần chạy hiện tại.
- Phiên bản JDK, bytecode và weaver không tương thích; compilation/weaving có warnings/errors.

Với AspectJ, có thể bật `-showWeaveInfo` và kiểm tra bytecode:

```bash
javap -classpath out-java -c -p demo.Target
javap -classpath out-woven -c -p demo.Target
```

Dùng debugger đặt breakpoint ở handler, trong advice `TraceAspect.aj` và trong `Target.outer/inner`. Nếu debugger chuyển vào helper tổng hợp, đó có thể là cấu trúc sau weaving; breakpoint nguồn và log trace giúp tái dựng đường thực thi.

## 11. Các sai lầm thường gặp

**“AOP nào cũng chặn self-invocation như nhau.”** Sai. JDK proxy ủy quyền sang target không tự nhận lời gọi nội bộ trên target; subclass proxy hoặc bytecode weaving có cơ chế khác và phải phân tích theo đường dispatch.

**“Dùng proxy thì method gốc đã được biến đổi bytecode.”** Không nhất thiết. JDK dynamic proxy tạo lớp proxy tại runtime và gọi handler, trong khi class đích ở ví dụ không bị sửa.

**“Weaving chỉ là tạo một proxy ẩn.”** Không đúng theo nghĩa kỹ thuật: CTW/Binary/LTW có thể thay đổi bytecode tại join point shadow, kể cả execution body hoặc field access mà interface wrapper không cung cấp.

**“Direct target luôn được advice chặn.”** Chỉ khi target thật sự đã được dệt ở join point phù hợp. Một aspect có trên classpath chưa đủ.

**“`call` và `execution` tương đương.”** Không. `call` nằm ở caller; `execution` nằm ở callee; phạm vi dệt và điều kiện chọn khác nhau.

**“Mọi subclass proxy đều bỏ qua self-invocation.”** Không thể khẳng định chung; cần xem instance chạy code, cơ chế override và việc proxy có delegate sang target tách biệt không.

**“Phương thức final/private đều không thể áp dụng AOP.”** Không đúng đối với toàn bộ AOP: subclass override và bytecode weaving có khả năng/giới hạn khác nhau.

**“Nếu có hai interceptor thì chắc chắn chỉ chạy một lần.”** Khi proxy và weaving cùng chặn một hành vi, cả hai đều có thể chạy; nhiều advice còn tạo thứ tự và side effect bổ sung.

**“Lỗi hiệu năng nhìn thấy từ stdout.”** Console output chỉ giúp phân tích lời gọi, không đo overhead; cần benchmark chuẩn.

**“Thay target bằng proxy ở một điểm tạo đối tượng sẽ bao phủ mọi lời gọi.”** Chưa chắc: có thể còn các tham chiếu target khác, callback, reflection, lời gọi nội bộ hoặc điểm tạo đối tượng không đi qua factory.

## 12. Tiêu chí hoàn thành bài tập tổng hợp

Hãy tự kiểm tra rằng bạn có thể giải thích và chứng minh:

- **Sơ đồ:** proxy handler nằm ở caller boundary, weaving execution nằm ở method body; giải thích call site vs execution site.
- **Thực nghiệm Java 17:** chạy được `Main.java` với `javac` và thu stdout ở mục 3, đúng hiện tượng self-invocation.
- **Thực nghiệm weaving:** dùng `ajc` dệt thêm `TraceAspect.aj` và thu stdout ở mục 4, kể cả lời gọi trực tiếp target.
- **Bảng dự đoán:** giải thích đủ sáu tình huống ở mục 6, không chỉ thuộc lòng log.
- **Ngoại lệ:** biết handler reflection unwrap `InvocationTargetException`, còn `around` có `proceed` điều khiển đường return/throw.
- **Giới hạn:** phân biệt JDK interface proxy, subclass proxy, CTW/binary/LTW và class loader.
- **Vận hành:** biết kiểm tra artifact class đã dệt, weave info, breakpoint và tránh double interception không chủ ý.
- **Lựa chọn:** chọn cơ chế theo join point cần bao phủ, mức kiểm soát caller, toolchain, semantics, test và overhead đo được.

**Kết luận:** proxy can thiệp những lời gọi **đi qua đối tượng trung gian**; weaving đưa advice vào **các vị trí mã đã được chọn và biến đổi**. Hai đường đi khác nhau giải thích sự khác biệt về direct call, self-invocation, loại join point và phạm vi áp dụng. Cách hiểu bền vững nhất là chạy cùng một chương trình dưới hai cấu hình và kiểm chứng đường thực thi bằng stdout, debugger và bytecode — thay vì xem proxy và weaving là hai tên gọi tương đương.

## Tài liệu tham khảo

- Java SE 17: `java.lang.reflect.Proxy` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html
- Java SE 17: `java.lang.reflect.InvocationHandler` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html
- Java SE 17: `java.lang.reflect.Method.invoke` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html
- AspectJ Programming Guide — join point, `call` và `execution`: https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html
- AspectJ Development Guide — `ajc` và các chế độ weaving: https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html
- AspectJ Load-Time Weaving — https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-006">Thiết kế hai aspect có thứ tự và kiểm chứng chuỗi thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế hai aspect có thứ tự và kiểm chứng chuỗi thực thi

## Mục tiêu bài tập tổng hợp

Khi một join point khớp nhiều aspect trong lập trình hướng khía cạnh (**Aspect-Oriented Programming, AOP**), **thứ tự chạy advice quyết định thứ tự quan sát hành vi**. Hai aspect đều đúng khi chạy riêng vẫn có thể tạo log, metric hoặc ngữ nghĩa khác nhau khi kết hợp. Đặc biệt, với **around advice**, việc đi vào và đi ra tạo nên **cấu trúc lồng nhau**: advice bên ngoài chạy phần “trước” đầu tiên nhưng chạy phần “sau” cuối cùng.

Trong bài này, bạn sẽ thiết kế **hai aspect có trách nhiệm riêng**, chỉ định thứ tự bọc, tính tay chuỗi sự kiện mong đợi, chạy chương trình Java 17+ để kiểm chứng cả đường thành công lẫn lỗi, rồi đảo thứ tự để quan sát sự khác biệt. Đây là một bài tập có lời giải và các tiêu chí tự kiểm tra, không chỉ là ví dụ ghi log hai lần.

Sau khi hoàn thành, bạn cần:

1. Phân biệt **aspect**, **join point**, **pointcut**, **before/after/around advice** và **weaving**.
2. Dự đoán được chuỗi `before → proceed → afterReturning/afterThrowing → finally` khi hai advice bao quanh nhau.
3. Thiết kế hai aspect có nhiệm vụ tách biệt, không ghi trùng và không thay đổi giá trị trả về/ngoại lệ gốc.
4. Nêu được thứ tự áp dụng bằng một chính sách rõ ràng; không phụ thuộc thứ tự tình cờ của classpath, reflection hay lần khởi tạo.
5. Viết phép kiểm tra xác nhận thứ tự sự kiện, bao gồm cả trường hợp đổi thứ tự và nghiệp vụ ném lỗi.
6. Biết mở rộng kiểm thử cho điểm cắt chồng nhau, lời gọi lồng nhau, bất đồng bộ, thread khác và lỗi ngay trong advice.

**Phạm vi:** kiến thức AOP cơ bản và nâng cao theo nghĩa chung. Không có một cú pháp khai báo thứ tự chung cho mọi công cụ AOP; chương trình bên dưới dùng JDK dynamic proxy để **mô phỏng chính xác việc lồng hai around advice theo thứ tự được chỉ định**, từ đó minh họa nguyên tắc thứ tự mà không lệ thuộc vào thư viện AOP cụ thể.

## 1. Ôn lại đúng các khái niệm cần dùng

| Khái niệm | Vai trò trong bài tập |
| --- | --- |
| **Aspect** | Thành phần gom một trách nhiệm xuyên suốt, ví dụ tracing hoặc audit |
| **Join point** | Vị trí có thể được chặn, ở đây là lời gọi tới `Service.execute(...)` qua proxy |
| **Pointcut** | Điều kiện quyết định những join point nào được áp dụng advice |
| **Advice** | Hành vi bổ sung tại join point, có thể chạy trước/sau/bao quanh |
| **Around advice** | Bao lấy thao tác gốc; có thể gọi tiếp (`proceed`), quan sát kết quả và lỗi |
| **Weaving/interception** | Cơ chế gắn advice vào thực thi: có thể là biến đổi bytecode hoặc chặn qua proxy, tùy công nghệ |
| **Precedence/order** | Quy tắc xác định aspect nào là tầng ngoài và tầng trong ở một join point có nhiều aspect |

Lưu ý: **thứ tự đăng ký aspect, thứ tự chạy advice, thứ tự các join point và thứ tự xuất hiện bản ghi ở sink bất đồng bộ là các vấn đề khác nhau**. Ví dụ này kiểm chứng **thứ tự thực thi trong một lời gọi đồng bộ** bằng cách thêm sự kiện vào danh sách trong cùng thread. Nó không tuyên bố rằng các log đi qua hàng đợi bất đồng bộ sẽ đến hệ thống lưu trữ theo đúng thứ tự đó.

Trong các cơ chế AOP khác nhau, việc ưu tiên advice có thể được cấu hình theo annotation, khai báo precedence, trình tự wrapper hoặc quy tắc weaving. Không tự giả định “aspect nào viết trước trong tệp thì chạy trước”. Bạn phải biết cơ chế cụ thể rồi kiểm chứng trên chương trình thật.

## 2. Đề bài: hai aspect, một phương thức nghiệp vụ

Giả sử có interface nghiệp vụ:

```java
// Giao diện bài toán.
interface Service {
    String execute(String input);
}
```

Nghiệp vụ:

- Khi input khác `"fail"`: trả `"ok:" + input`.
- Khi input bằng `"fail"`: ném `IllegalStateException("rejected")`.
- Bản thân nghiệp vụ chỉ ghi một dấu vết “đang thực thi” và thêm một dấu vết trả kết quả hoặc ném lỗi.

Bạn cần thiết kế:

**Aspect 1 — TraceAspect**

- Trước khi đi vào tầng tiếp theo: thêm `Trace.before`.
- Khi tầng tiếp theo hoàn tất bình thường: thêm `Trace.afterReturning`.
- Khi tầng tiếp theo ném ngoại lệ: thêm `Trace.afterThrowing:<ExceptionType>`.
- Ở mọi đường thoát: thêm `Trace.finally`.
- Không thay đổi kết quả nghiệp vụ và không nuốt ngoại lệ.

**Aspect 2 — AuditAspect**

- Trước khi đi vào tầng tiếp theo: thêm `Audit.before`.
- Khi tầng tiếp theo hoàn tất bình thường: thêm `Audit.afterReturning`.
- Khi tầng tiếp theo ném ngoại lệ: thêm `Audit.afterThrowing:<ExceptionType>`.
- Ở mọi đường thoát: thêm `Audit.finally`.
- Không tự ý thay đổi kết quả/ngoại lệ, không ghi tham số nhạy cảm.

Trong bài thực tế, trace thường gắn với correlation/span và audit thường là sự kiện có cấu trúc về hành động/kết quả. Để tập trung vào **thứ tự thực thi**, bản demo ghi các chuỗi sự kiện ngắn, dễ đối chiếu. Hai aspect có thể có code khung giống nhau vì cùng áp dụng dạng around advice; **mục đích, chính sách dữ liệu và mức độ tin cậy của chúng vẫn khác nhau** khi triển khai thật.

### Yêu cầu kiểm chứng

Thực hiện ít nhất bốn kịch bản:

1. **Trace bên ngoài, Audit bên trong — thành công.**
2. **Trace bên ngoài, Audit bên trong — nghiệp vụ ném lỗi.**
3. **Audit bên ngoài, Trace bên trong — thành công.**
4. **Audit bên ngoài, Trace bên trong — nghiệp vụ ném lỗi.**

Với mỗi kịch bản, lưu chuỗi sự kiện thành một danh sách có thứ tự, so sánh với chuỗi mong đợi và chỉ in `CHECK=PASS` nếu khớp **đúng từng phần tử, đúng thứ tự, đúng số lần**. Nếu sai, chương trình phải báo lỗi kiểm chứng.

## 3. Phân tích trước khi viết code: quy tắc lồng nhau

Gọi hai aspect là A và B. Nếu **A là tầng ngoài**, **B là tầng trong**, còn J là join point nghiệp vụ, luồng đồng bộ có dạng:

```text
Caller
  |
  v
A.before
  |
  v
B.before
  |
  v
J: business execution
  |
  v
B.afterReturning / B.afterThrowing
  |
  v
B.finally
  |
  v
A.afterReturning / A.afterThrowing
  |
  v
A.finally
  |
  v
Caller nhận result / exception
```

Những sự kiện `afterReturning` và `afterThrowing` loại trừ nhau đối với **mỗi advice trong một lần thực thi** theo contract của demo: thành công thì nhánh return, lỗi thì nhánh throw. `finally` chạy trong cả hai trường hợp. Điều này không có nghĩa mọi framework AOP đều định nghĩa advice riêng tên tương tự với cách ưu tiên y hệt nhau; đây là ngữ nghĩa của hai khối `try/catch/finally` trong bài.

Cách hiểu chính xác hơn cho một around advice:

```java
// Mã giả: proceed() đại diện cho lời gọi tầng tiếp theo.
Object around() throws Throwable {
    events.add("A.before");
    try {
        Object result = proceed();
        events.add("A.afterReturning");
        return result;
    } catch (Throwable error) {
        events.add("A.afterThrowing");
        throw error;
    } finally {
        events.add("A.finally");
    }
}
```

Chú ý: `A.afterReturning` diễn ra **sau khi toàn bộ tầng bên trong đã trở về bình thường**. Nếu B dùng `finally`, thì `B.finally` phải chạy trước khi A thấy tầng trong đã hoàn tất. Ngược lại, nếu J ném lỗi, B xử lý hoặc truyền lỗi lên trước; A chỉ thấy lỗi nếu B tiếp tục ném lỗi.

### 3.1. Một phép thử suy luận

Giả sử A bao ngoài B và J trả kết quả bình thường:

```text
A.before
B.before
J
B.afterReturning
B.finally
A.afterReturning
A.finally
```

Nếu đảo A và B:

```text
B.before
A.before
J
A.afterReturning
A.finally
B.afterReturning
B.finally
```

Thứ tự các sự kiện trước và sau **đảo theo cấu trúc bọc**, chứ không phải tất cả `before` chạy theo một thứ tự rồi tất cả `after` cũng chạy cùng chiều. Đây là ý tưởng trọng tâm phải nắm trước khi viết chương trình.

### 3.2. Lỗi có thể làm thay đổi chuỗi ra sao?

Nếu J ném `IllegalStateException`, với A ngoài B:

```text
A.before
B.before
J.execute
J.throw
B.afterThrowing:IllegalStateException
B.finally
A.afterThrowing:IllegalStateException
A.finally
```

Nhưng nếu B **bắt lỗi rồi trả một kết quả dự phòng**, A có thể chạy nhánh `afterReturning` vì **từ góc nhìn A, tầng B đã trả bình thường**. Đây là lý do không thể suy luận chuỗi chỉ từ việc “nghiệp vụ có ném lỗi” mà bỏ qua hành vi của từng advice.

Trong lời giải chính, cả hai aspect đều truyền tiếp lỗi nguyên trạng, nên chuỗi dễ kiểm chứng hơn.

## 4. Thiết kế trước khi triển khai

Mỗi aspect trong demo là một **wrapper** quanh đối tượng cùng interface. Wrapper gọi tầng tiếp theo và sinh event theo kết quả; tầng sâu nhất là đối tượng nghiệp vụ.

Khi cấu hình **Trace ngoài, Audit trong**:

```text
Client
  |
  +--> TraceProxy
         |
         +--> AuditProxy
                |
                +--> BusinessService
```

Phần tạo cấu trúc:

```java
// Mã minh họa, các hàm định nghĩa ở chương trình hoàn chỉnh.
Service traceOutsideAudit = trace(audit(business, events), events);
```

Phải đọc từ trong ra ngoài: `audit(business, events)` tạo proxy Audit bọc business; sau đó `trace(...)` tạo proxy Trace bọc proxy Audit. **Lớp wrapper cuối cùng được gắn ở ngoài cùng**.

Cấu hình đảo:

```java
Service auditOutsideTrace = audit(trace(business, events), events);
```

Trong một hệ thống AOP sử dụng thứ tự qua cấu hình, thao tác tương đương là định nghĩa **rõ precedence** cho hai aspect; không nhất thiết viết hai lệnh bao bọc thủ công. Việc bọc proxy trong ví dụ giúp thứ tự nhìn thấy được, phù hợp cho kiểm chứng.

### Các bất biến cần bảo toàn

- Một lời gọi đến nghiệp vụ gốc chỉ diễn ra **một lần**, trừ khi yêu cầu retry được thiết kế tường minh (bài này không có retry).
- `before` của mỗi aspect chỉ chạy một lần cho một lượt chặn.
- Nếu tầng tiếp theo hoàn thành bình thường: chỉ chạy `afterReturning`.
- Nếu tầng tiếp theo ném lỗi: chỉ chạy `afterThrowing`, truyền lại lỗi gốc.
- `finally` luôn chạy sau nhánh return hoặc throw tương ứng.
- Không tự ghi event quá chi tiết đến mức làm thay đổi thời gian hoặc phụ thuộc trạng thái async.
- Chuỗi sự kiện kết thúc ở caller có kết quả/ngoại lệ nhất quán với nghiệp vụ.

## 5. Lời giải Java 17+: hai aspect có thứ tự rõ ràng

Dùng JDK dynamic proxy để minh họa hai around advice. Chương trình có thể lưu trong một tệp, không cần thư viện ngoài.

Tạo tệp **`AspectOrderExercise.java`** với toàn bộ nội dung dưới đây:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AspectOrderExercise {

    public interface Service {
        String execute(String input);
    }

    static final class BusinessService implements Service {
        private final List<String> events;

        BusinessService(List<String> events) {
            this.events = events;
        }

        @Override
        public String execute(String input) {
            events.add("Business.execute");

            if ("fail".equals(input)) {
                events.add("Business.throw");
                throw new IllegalStateException("rejected");
            }

            events.add("Business.return");
            return "ok:" + input;
        }
    }

    // Gọi tầng tiếp theo, rồi gỡ wrapper của reflection nếu có lỗi.
    private static Object callNext(
            Service next, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(next, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static final class TraceAspect implements InvocationHandler {
        private final Service next;
        private final List<String> events;

        TraceAspect(Service next, List<String> events) {
            this.next = next;
            this.events = events;
        }

        @Override
        public Object invoke(
                Object proxy, Method method, Object[] args) throws Throwable {
            events.add("Trace.before");

            try {
                Object result = callNext(next, method, args);
                events.add("Trace.afterReturning");
                return result;
            } catch (Throwable error) {
                events.add("Trace.afterThrowing:"
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                events.add("Trace.finally");
            }
        }
    }

    static final class AuditAspect implements InvocationHandler {
        private final Service next;
        private final List<String> events;

        AuditAspect(Service next, List<String> events) {
            this.next = next;
            this.events = events;
        }

        @Override
        public Object invoke(
                Object proxy, Method method, Object[] args) throws Throwable {
            events.add("Audit.before");

            try {
                Object result = callNext(next, method, args);
                events.add("Audit.afterReturning");
                return result;
            } catch (Throwable error) {
                events.add("Audit.afterThrowing:"
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                events.add("Audit.finally");
            }
        }
    }

    private static Service wrap(
            InvocationHandler advice) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] {Service.class},
                advice
        );
    }

    private static Service trace(
            Service next, List<String> events) {
        return wrap(new TraceAspect(next, events));
    }

    private static Service audit(
            Service next, List<String> events) {
        return wrap(new AuditAspect(next, events));
    }

    private static void verify(
            String label,
            Service service,
            String input,
            List<String> events,
            List<String> expected) {

        events.clear();
        System.out.println("== " + label + " ==");

        String outcome;
        try {
            outcome = "result=" + service.execute(input);
        } catch (IllegalStateException error) {
            outcome = "error="
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage();
        }

        for (String event : events) {
            System.out.println(event);
        }
        System.out.println(outcome);

        if (!events.equals(expected)) {
            throw new AssertionError(
                    "Expected: " + expected + ", actual: " + events);
        }
        System.out.println("CHECK=PASS");
    }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        Service business = new BusinessService(events);

        // Trace là tầng ngoài, Audit là tầng trong.
        Service traceOutsideAudit =
                trace(audit(business, events), events);

        verify(
                "trace > audit, success",
                traceOutsideAudit, "good", events,
                List.of(
                        "Trace.before",
                        "Audit.before",
                        "Business.execute",
                        "Business.return",
                        "Audit.afterReturning",
                        "Audit.finally",
                        "Trace.afterReturning",
                        "Trace.finally"
                )
        );

        verify(
                "trace > audit, failure",
                traceOutsideAudit, "fail", events,
                List.of(
                        "Trace.before",
                        "Audit.before",
                        "Business.execute",
                        "Business.throw",
                        "Audit.afterThrowing:IllegalStateException",
                        "Audit.finally",
                        "Trace.afterThrowing:IllegalStateException",
                        "Trace.finally"
                )
        );

        // Đảo thứ tự: Audit là tầng ngoài, Trace là tầng trong.
        Service auditOutsideTrace =
                audit(trace(business, events), events);

        verify(
                "audit > trace, success",
                auditOutsideTrace, "good", events,
                List.of(
                        "Audit.before",
                        "Trace.before",
                        "Business.execute",
                        "Business.return",
                        "Trace.afterReturning",
                        "Trace.finally",
                        "Audit.afterReturning",
                        "Audit.finally"
                )
        );

        verify(
                "audit > trace, failure",
                auditOutsideTrace, "fail", events,
                List.of(
                        "Audit.before",
                        "Trace.before",
                        "Business.execute",
                        "Business.throw",
                        "Trace.afterThrowing:IllegalStateException",
                        "Trace.finally",
                        "Audit.afterThrowing:IllegalStateException",
                        "Audit.finally"
                )
        );
    }
}
```

### 5.1. Biên dịch và chạy

Bằng JDK 17 trở lên, ở thư mục chứa tệp:

```bash
javac AspectOrderExercise.java
java AspectOrderExercise
```

Đầu ra mong đợi:

```text
== trace > audit, success ==
Trace.before
Audit.before
Business.execute
Business.return
Audit.afterReturning
Audit.finally
Trace.afterReturning
Trace.finally
result=ok:good
CHECK=PASS
== trace > audit, failure ==
Trace.before
Audit.before
Business.execute
Business.throw
Audit.afterThrowing:IllegalStateException
Audit.finally
Trace.afterThrowing:IllegalStateException
Trace.finally
error=IllegalStateException:rejected
CHECK=PASS
== audit > trace, success ==
Audit.before
Trace.before
Business.execute
Business.return
Trace.afterReturning
Trace.finally
Audit.afterReturning
Audit.finally
result=ok:good
CHECK=PASS
== audit > trace, failure ==
Audit.before
Trace.before
Business.execute
Business.throw
Trace.afterThrowing:IllegalStateException
Trace.finally
Audit.afterThrowing:IllegalStateException
Audit.finally
error=IllegalStateException:rejected
CHECK=PASS
```

Bản demo dùng danh sách `ArrayList` thông thường vì mỗi kịch bản chạy **tuần tự trên một thread**, không chia sẻ danh sách để nhiều thread ghi đồng thời. Khi áp dụng cách thu thập event trong một chương trình concurrent, hãy gắn event vào từng invocation hoặc dùng collector có hợp đồng đồng bộ rõ ràng; không tự thay `ArrayList` thành danh sách dùng chung rồi kỳ vọng sự kiện từ các request khác nhau vẫn có thứ tự tuyến tính hữu ích.

### 5.2. Vì sao dùng reflection và unwrap ngoại lệ?

`Proxy.newProxyInstance(...)` tạo một đối tượng triển khai interface `Service`; khi gọi method qua proxy, `InvocationHandler.invoke(...)` nhận `Method` và mảng đối số rồi chủ động chuyển tiếp xuống tầng tiếp theo. Với `method.invoke(next, args)`, ngoại lệ do method đích ném được reflection bọc trong `InvocationTargetException`.

Hàm `callNext(...)` lấy lại `wrapper.getCause()` để hai aspect thấy **ngoại lệ thực từ nghiệp vụ**, thay vì nhìn thấy một wrapper không thuộc ngữ nghĩa nghiệp vụ. Trong demo, `Service.execute` có thể ném `IllegalStateException` (unchecked), nên kết quả rõ ràng.

Với phương thức khai báo checked exceptions hoặc API AOP có quy tắc xử lý lỗi khác, việc truyền ngoại lệ cần theo đúng hợp đồng. Dynamic proxy có thể bọc checked throwable không được khai báo vào `UndeclaredThrowableException`; không nên tổng quát hóa behavior của demo cho mọi chữ ký phương thức.

### 5.3. Vì sao thứ tự được quyết định bằng phép bọc?

Cấu trúc:

```java
Service traceOutsideAudit =
        trace(audit(business, events), events);
```

được tạo từ trong ra ngoài:

1. `audit(business, events)` trả về proxy Audit gọi tiếp `business`.
2. `trace(innerProxy, events)` trả về proxy Trace gọi tiếp proxy Audit.
3. Caller gọi `traceOutsideAudit.execute("good")` nên Trace được đi vào trước.
4. Trace gọi tiếp Audit; Audit gọi tiếp nghiệp vụ.
5. Khi nghiệp vụ trả bình thường, Audit ghi `afterReturning` rồi `finally`, trả về Trace; Trace ghi `afterReturning` rồi `finally`.
6. Caller cuối cùng nhận `"ok:good"`.

Nếu đảo thứ tự tạo proxy, thứ tự vào và đi ra đảo tương ứng. Nếu chỉ đổi tên biến `traceOutsideAudit` thành `auditOutsideTrace` thì **không đổi được thứ tự**; phải thay đổi cấu trúc bọc hoặc cấu hình ưu tiên trong hệ thống AOP thật.

### 5.4. Giới hạn của mô hình proxy

Đây là bài thực hành **around-style interception** bằng proxy của Java, không phải một thư viện aspect tổng quát. Những khác biệt khi đưa kiến thức vào cơ chế weaving thật:

- Proxy JDK hoạt động qua interface; không tự động chặn mọi constructor, field access hoặc method call trong bytecode.
- Lời gọi không đi qua proxy có thể không được chặn; ví dụ lời gọi nội bộ trên chính target có đặc tính khác tùy công nghệ AOP.
- Một framework AOP có thể điều khiển precedence bằng metadata hoặc declaration thay vì gọi `trace(audit(...))`.
- Với nhiều loại advice khác nhau, thứ tự `before/after/around` chịu quy tắc ưu tiên và composition của framework; cần đọc hợp đồng cụ thể.
- Kết quả của bài chứng minh ngữ nghĩa của **hai wrapper đã tạo**, không chứng minh tất cả hệ thống AOP đều áp dụng cùng một cơ chế proxy.

## 6. Đọc kết quả theo từng lớp: chuỗi thành công

Trong kịch bản `trace > audit, success`:

| Bước | Component | Event | Điều gì xảy ra? |
| --- | --- | --- | --- |
| 1 | Trace | `Trace.before` | Caller đi vào wrapper ngoài |
| 2 | Audit | `Audit.before` | Trace gọi tiếp wrapper trong |
| 3 | Business | `Business.execute` | Nghiệp vụ bắt đầu |
| 4 | Business | `Business.return` | Nghiệp vụ chuẩn bị trả kết quả |
| 5 | Audit | `Audit.afterReturning` | Lời gọi xuống business trả bình thường |
| 6 | Audit | `Audit.finally` | Audit rời scope |
| 7 | Trace | `Trace.afterReturning` | Toàn bộ Audit đã hoàn tất |
| 8 | Trace | `Trace.finally` | Trace rời scope |

**Quy tắc tổng quát:** Với \(n\) around advice lồng nhau theo thứ tự ngoài vào trong \(A_1, A_2, \ldots, A_n\), mỗi advice có đúng một `proceed()`, không có advice nào short-circuit hoặc ném thêm ngoại lệ, thì chuỗi vào là:

\[
A_1.before \to A_2.before \to \cdots \to A_n.before \to J
\]

Sau khi J hoàn tất bình thường, các tầng rút lui theo thứ tự **ngược**:

\[
A_n.after \to A_{n-1}.after \to \cdots \to A_1.after
\]

Nếu mỗi tầng còn có `finally`, ta phải chèn `A_i.finally` ngay **sau nhánh after tương ứng và trước khi trả về tầng ngoài**. Vì vậy không đúng nếu chỉ liệt kê tất cả `afterReturning` rồi mới liệt kê tất cả `finally`.

Có thể tự kiểm tra bằng cách xem mỗi tầng như một cặp dấu ngoặc:

```text
Trace.before (
    Audit.before (
        Business
    ) Audit.afterReturning, Audit.finally
) Trace.afterReturning, Trace.finally
```

Nếu một chuỗi có `Trace.afterReturning` xuất hiện trước `Audit.finally` trong chính mẫu code này, đó là dấu hiệu mô hình thứ tự hoặc việc ghi sự kiện đã sai.

## 7. Đọc kết quả theo từng lớp: chuỗi ngoại lệ

Trong kịch bản `trace > audit, failure`:

1. Trace ghi `Trace.before`.
2. Audit ghi `Audit.before`.
3. Business ghi `Business.execute` và `Business.throw` rồi ném `IllegalStateException("rejected")`.
4. Reflection ở Audit unwrap ngoại lệ về `IllegalStateException`.
5. Audit bắt `Throwable`, ghi `Audit.afterThrowing:IllegalStateException` rồi ném tiếp lỗi gốc.
6. Khối `finally` của Audit chạy, ghi `Audit.finally`.
7. Trace nhận cùng exception, ghi `Trace.afterThrowing:IllegalStateException` rồi ném tiếp.
8. Khối `finally` của Trace ghi `Trace.finally`.
9. Caller bắt `IllegalStateException` và in `error=IllegalStateException:rejected`.

Không có event `afterReturning` trong đường lỗi này vì mỗi lời gọi `callNext` đều kết thúc bằng ngoại lệ. Nhánh `finally` vẫn chạy.

Một câu hỏi để kiểm tra suy luận: **Nếu Audit nuốt `IllegalStateException` và trả `"fallback"` thì Trace chạy nhánh nào?** Đáp án: nếu Audit thật sự trả giá trị bình thường mà không phát sinh lỗi tiếp theo, Trace nhìn thấy một return, nên ghi `Trace.afterReturning`. Bởi vậy trạng thái “nghiệp vụ từng ném lỗi” khác với “điều mà một advice bên ngoài quan sát được”.

Một câu hỏi khác: **Nếu Audit ghi sự kiện trong `finally` nhưng đoạn ghi đó ném exception thì chuyện gì xảy ra?** Trong Java, ngoại lệ ném từ `finally` có thể **thay thế** return value hoặc exception đang được truyền ra. Lời giải sử dụng thao tác thêm event đơn giản để cô lập bài toán thứ tự; trong hệ thống thật phải có chính sách rõ ràng cho lỗi ghi log/tracing/audit, đặc biệt nếu chúng là chức năng bắt buộc.

## 8. Vai trò của thứ tự khi hai aspect có nhiệm vụ khác nhau

Không phải cứ “aspect nào ngoài cũng được” miễn là chuỗi event đẹp. Precedence có thể ảnh hưởng đến những gì được đo hoặc quan sát.

Giả sử aspect Trace đo thời gian còn Audit thực hiện một thao tác ghi audit:

- **Trace bên ngoài Audit:** nếu đồng hồ bắt đầu trước `proceed()` và kết thúc sau khi tầng Audit trả, thời lượng đo có thể **bao gồm phần chi phí do Audit thực hiện bên trong**.
- **Audit bên ngoài Trace:** phép đo của Trace có thể **không bao gồm** phần logic trước/sau thuộc Audit bên ngoài, vì Trace chỉ sống trong khoảng gọi tầng được nó bọc.
- Nếu Audit có bước kiểm tra điều kiện và quyết định không gọi tiếp nghiệp vụ, việc Trace có được kích hoạt hay không phụ thuộc Trace nằm trong hay ngoài Audit.
- Nếu một aspect sửa tham số hoặc bọc exception, aspect ngoài có thể thấy giá trị đã sửa thay vì giá trị gốc.

Ví dụ minh họa khái niệm:

```text
Trace( Audit( Business ) )

Trace.start
  Audit.before + audit overhead
    Business
  Audit.after + audit overhead
Trace.stop

=> Duration của Trace có thể bao gồm overhead bên trong Audit.
```

Ngược lại:

```text
Audit( Trace( Business ) )

Audit.before + audit overhead
  Trace.start
    Business
  Trace.stop
Audit.after + audit overhead

=> Duration của Trace không bao gồm hai phần Audit bao ngoài nó.
```

Trong hệ thống thật, startup/JIT, lập lịch, GC, buffering và logging bất đồng bộ có thể ảnh hưởng kết quả đo; sơ đồ chỉ mô tả **ranh giới thời gian** theo vị trí của logic đồng bộ, không dự đoán con số cụ thể.

### 8.1. Quyết định thứ tự dựa trên mục tiêu

| Mục tiêu | Quy tắc thiết kế cần cân nhắc |
| --- | --- |
| Đo toàn bộ latency của call stack có các advice bên trong | Đặt timing/tracing ở tầng ngoài tương ứng |
| Đo riêng nghiệp vụ sau khi các thao tác phụ đã hoàn tất bên ngoài | Đặt timing ở gần nghiệp vụ hơn |
| Ghi audit cho mọi lần **attempt**, kể cả bị một aspect trong chặn | Audit có thể cần bao ngoài aspect có khả năng short-circuit |
| Chỉ ghi audit khi thao tác thực sự được thực thi | Đặt audit tại boundary có thể xác minh hành vi gốc đã chạy |
| Giữ context cho tất cả advice bên trong | Aspect thiết lập context cần bao ngoài những advice tiêu thụ context |
| Kiểm soát lỗi telemetry | Quy định rõ aspect nào được bắt/chuyển tiếp và có thể làm biến đổi exception |

Đây là **các tiêu chí lựa chọn**, không phải một precedence bắt buộc dùng cho mọi ứng dụng. Nếu dùng nhiều loại audit với ý nghĩa pháp lý/nghiệp vụ, không thể tùy tiện đổi thứ tự chỉ để tăng hiệu năng mà chưa xem lại các sự kiện bắt buộc phải ghi.

## 9. Kiểm chứng chuỗi thực thi: cách làm chắc chắn

Chương trình sử dụng một danh sách sự kiện và `List.of(...)` cho chuỗi kỳ vọng. Phép kiểm tra:

```java
if (!events.equals(expected)) {
    throw new AssertionError(...);
}
```

Kiểm tra **cả thứ tự và nội dung**. Nếu một sự kiện thiếu, thừa, trùng hoặc đảo vị trí, `List.equals(...)` trả false. Đây là bằng chứng mạnh hơn việc chỉ grep thấy `Trace.before` hoặc chỉ đếm tổng số log.

Khi viết kiểm thử thực tế:

1. Chọn một danh sách event theo từng invocation; dùng ID nếu có nhiều lời gọi.
2. Xác định expected trace **trước khi chạy**, từ hợp đồng advice và thứ tự cấu hình.
3. Thực hiện lời gọi thành công và lời gọi ném lỗi đã biết.
4. So sánh expected và actual, báo chỗ sai đầu tiên khi có mismatch.
5. Kiểm tra return value/exception identity, không chỉ event sequence.
6. Lặp lại khi đảo thứ tự, thay đổi pointcut hoặc thêm aspect thứ ba.
7. Kiểm thử cả lời gọi không được pointcut chọn: event từ hai aspect **không được xuất hiện**.
8. Nếu môi trường concurrent, không so sánh một chuỗi global trộn nhiều request với chuỗi của một request; phải lọc theo correlation ID hoặc tách bộ thu riêng.

### 9.1. Bảng expected trace dạng tóm tắt

| Thứ tự bọc | Thành công | Ngoại lệ |
| --- | --- | --- |
| Trace ngoài → Audit trong | Trace.before → Audit.before → Business → Audit.afterReturning/finally → Trace.afterReturning/finally | Trace.before → Audit.before → Business.throw → Audit.afterThrowing/finally → Trace.afterThrowing/finally |
| Audit ngoài → Trace trong | Audit.before → Trace.before → Business → Trace.afterReturning/finally → Audit.afterReturning/finally | Audit.before → Trace.before → Business.throw → Trace.afterThrowing/finally → Audit.afterThrowing/finally |

Bảng viết gọn bằng “/finally” để dễ nhìn; phép kiểm tra Java ở trên vẫn yêu cầu **từng event riêng lẻ** đúng thứ tự, không gộp hai sự kiện thành một.

### 9.2. Ba mức bằng chứng khác nhau

- **Bằng chứng cấu hình:** precedence của hai aspect đã được khai báo rõ và được build/deploy đúng.
- **Bằng chứng hành vi:** event thực tế ở success/failure khớp chuỗi dự đoán trên đúng join point.
- **Bằng chứng nghiệp vụ:** kết quả, exception và hiệu ứng nghiệp vụ không bị thay đổi ngoài hợp đồng.

Chỉ có output `CHECK=PASS` từ ví dụ chưa chứng minh mức thứ nhất cho một công nghệ AOP khác. Phải kiểm chứng phiên bản, rule weaving, pointcut và lời gọi thực tế trong ứng dụng sử dụng công nghệ đó.

## 10. Những biến thể nâng cao để tự thực hành

Sau khi chạy thành công lời giải, hãy thực hiện từng biến thể trên **một bản sao chương trình**, tính expected trace trước rồi mới sửa phép kiểm tra.

### Bài 1 — Audit quyết định không gọi tiếp

Yêu cầu: nếu `input` bằng `"blocked"`, Audit trả `"denied"` **mà không gọi `callNext(...)`**. Hãy đặt Trace ngoài Audit và so sánh với Audit ngoài Trace.

Gợi ý lập luận:

- Khi Trace ngoài Audit, Trace được vào trước; Audit có thể short-circuit, nên Trace vẫn nhìn thấy một return từ Audit.
- Khi Audit ngoài Trace, nếu Audit short-circuit trước khi đi vào tầng Trace thì **Trace không được chạy**.
- Nếu Audit đóng vai trò kiểm tra quyền bắt buộc, cần thiết kế chuỗi hành vi chính xác và tránh để request nhạy cảm đi qua xử lý không được phép.

**Không dùng hành vi short-circuit này làm ví dụ cho mọi aspect**. Một số aspect chỉ nên quan sát và luôn phải tiếp tục; một số aspect có chủ đích sửa luồng điều khiển như retry, cache hoặc enforcement.

### Bài 2 — Một aspect thay đổi exception

Yêu cầu: cho Audit chuyển `IllegalStateException` thành `IllegalArgumentException` tại nhánh `catch` và ném lại. Kiểm tra Trace bên ngoài thấy **loại lỗi mới** hay loại gốc.

Đáp án suy luận: Trace bên ngoài sẽ bắt lỗi từ Audit sau khi Audit ném lại, nên thấy loại lỗi mà Audit phát ra. Nếu thứ tự đảo, Trace nằm trong có thể thấy lỗi gốc trước khi Audit bao ngoài chuyển đổi. Đây là minh chứng rằng precedence không chỉ ảnh hưởng độ đẹp của log mà còn ảnh hưởng **error contract**.

Trong ứng dụng thật, việc chuyển ngoại lệ phải có lý do, bảo toàn cause khi phù hợp và không che mất dữ liệu chẩn đoán cần thiết.

### Bài 3 — Trace đo thời lượng bằng biến cục bộ

Yêu cầu: thêm `long start = System.nanoTime();` vào `TraceAspect.invoke`, tính `elapsedNanos` ở `finally` và ghi vào metric collector. So sánh ranh giới phép đo khi Trace nằm ngoài/ở trong Audit.

Không in con số nanosecond rồi assert giá trị chính xác vì phụ thuộc môi trường. Thay vào đó, kiểm tra rằng **đúng một mẫu thời gian** được ghi cho mỗi invocation, thời điểm ghi là khi thoát advice, và không dùng một field `startTime` chung giữa các yêu cầu.

### Bài 4 — Pointcut chỉ chọn một phương thức

Bổ sung `Service.health()` và quy định cả hai aspect chỉ áp dụng `execute(...)`. Kiểm tra:

- `execute(...)` đi qua cả hai aspect.
- `health()` trả bình thường mà không có sự kiện advice.
- Những tên method, overload và interface method khác nhau không vô tình khớp do chỉ kiểm tra prefix chuỗi.

Với dynamic proxy demo, có thể thêm điều kiện trên `method.getName()` và chữ ký; với AOP thật, ưu tiên matcher theo capability/cấu hình pointcut được hỗ trợ. Đừng vội kết luận có thể chặn mọi lời gọi nội bộ của đối tượng chỉ vì wrapper nhìn thấy một phương thức trên interface.

### Bài 5 — Advice tự gây lỗi

Yêu cầu: buộc `Audit.afterReturning` ném một ngoại lệ mô phỏng lỗi sink. Dự đoán nhánh nào của Trace chạy. Câu trả lời là Trace có thể thấy **thất bại từ Audit**, dù nghiệp vụ đã trả kết quả tốt. Đây là thay đổi ngữ nghĩa cần chủ động xử lý.

Điều tra thêm trường hợp `Audit.finally` ném lỗi, hoặc lỗi telemetry xuất hiện đồng thời với lỗi nghiệp vụ. Cần có chính sách bảo toàn lỗi gốc, ghi lỗi phụ an toàn và không tạo vòng lặp logger → advice → logger.

### Bài 6 — Một lời gọi có nhiều thread

Chạy hai request đồng thời. Mỗi request cần có **trace riêng** và context riêng. Không dùng field `currentRequestId` trong một aspect instance dùng chung; không dùng một `ArrayList` global không đồng bộ cho sự kiện từ nhiều thread. Có thể dùng collector theo invocation hoặc sự kiện bất biến chứa correlation ID, rồi nhóm theo ID khi kiểm chứng.

Nếu công việc trả về `CompletionStage` hay một đối tượng tương lai khác, một around advice đồng bộ có thể thoát **trước khi tác vụ hoàn thành**. Lúc đó cần tách chuỗi “thời điểm method trả về stage” và “thời điểm stage hoàn tất”; callback xử lý completion không nhất thiết chạy trên thread ban đầu. Thứ tự log giữa các tác vụ phải được diễn giải theo **happens-before** thực tế, không ghép chung mọi event thành một chuỗi tuyến tính tưởng tượng.

## 11. Những lỗi điển hình khi giải bài

### Lỗi 1 — Nhầm ngoài và trong

`trace(audit(business))` nghĩa là **Trace ngoài, Audit trong**. Đảo tên biến không đổi cấu trúc. Phải theo dõi wrapper mà caller thực sự giữ.

### Lỗi 2 — Nghĩ after chạy cùng chiều với before

Trong around advice, hoạt động đi ra chạy theo chiều ngược của hoạt động đi vào vì cấu trúc lồng nhau. Nếu Audit nằm trong Trace, Audit phải hoàn tất toàn bộ khối xử lý của nó, gồm `finally`, rồi Trace mới hoàn tất `callNext`.

### Lỗi 3 — Quên xử lý lỗi

Chỉ kiểm tra success không đủ. Một aspect logging tưởng “đúng thứ tự” có thể vô tình nuốt exception, biến lỗi thành return, hoặc để một lỗi logger che ngoại lệ từ nghiệp vụ.

### Lỗi 4 — Gọi `proceed()` không chính xác số lần

Around advice có quyền kiểm soát việc gọi tiếp. Nếu không gọi, nghiệp vụ có thể bị bỏ qua; nếu gọi hai lần, nghiệp vụ có thể có tác dụng phụ hai lần. Với demo, mục tiêu là **một lần gọi tầng tiếp theo** ở mỗi advice trên cả success và failure. Retry phải là một chính sách riêng, không phải hệ quả vô ý của cấu trúc catch.

### Lỗi 5 — Dựa vào thứ tự đăng ký không được cam kết

Một số hệ thống có thứ tự mặc định nhưng không bảo đảm ổn định; có nơi precedence được khai báo tường minh. Muốn đúng trong build khác hoặc sau nâng cấp, cần xác định hợp đồng thứ tự của công nghệ đang dùng và có test chứng minh.

### Lỗi 6 — Dùng dữ liệu thu thập bất đồng bộ làm bằng chứng thứ tự đồng bộ

Một event có thể được enqueue theo thứ tự A → B nhưng xuất hiện trên hệ thống tập trung theo thứ tự khác do worker, retry hoặc batching. Nếu cần kiểm tra **thứ tự gọi advice**, ghi vào collector đồng bộ gắn với invocation trong môi trường test; còn nếu cần kiểm tra **thứ tự phát tán từ hệ thống quan sát**, đó là một bài toán khác.

### Lỗi 7 — Dùng state dùng chung cho một invocation

Một field `startTime` hoặc `currentRequestId` trong aspect instance được dùng nhiều lần có thể bị ghi đè bởi request song song hoặc gọi lồng nhau. Đặt state theo invocation vào biến local hoặc scope riêng; nếu truyền bất đồng bộ thì quản lý context theo logical task.

### Lỗi 8 — Quá chú trọng chuỗi event mà quên tác động nghiệp vụ

Bài test nên kiểm tra cả return value/exception, số lần gọi nghiệp vụ và mức độ an toàn của logging. Một chương trình có thứ tự event “đẹp” nhưng trả sai kết quả hoặc chạy nghiệp vụ hai lần vẫn sai.

## 12. Checklist kiểm thử khi chuyển sang công cụ AOP thật

Trước khi coi bài làm hoàn tất trong một hệ thống sử dụng pointcut/weaving thực tế, hãy đối chiếu:

- [ ] Tìm đúng join point nghiệp vụ mà **cả hai aspect thực sự khớp**.
- [ ] Định nghĩa precedence của hai aspect rõ ràng theo tài liệu công nghệ.
- [ ] Kiểm tra success: outer.before → inner.before → business → inner.after/finally → outer.after/finally.
- [ ] Kiểm tra exception: mỗi tầng bắt và truyền đúng lỗi, `finally` vẫn chạy.
- [ ] Đảo precedence: expected trace đảo tương ứng mà kết quả nghiệp vụ giữ đúng.
- [ ] Không có advice khác vô tình tham gia cùng join point làm thay đổi trace.
- [ ] Pointcut không chặn logger, metric recorder, proxy helper hoặc chính công cụ quan sát gây đệ quy.
- [ ] Các ràng buộc về lời gọi nội bộ, proxy, classloader, bridge method hoặc weaving time đã được kiểm tra đúng công nghệ.
- [ ] Có test âm tính cho method **không** được pointcut chọn.
- [ ] Nếu runtime có concurrency, events/context được cô lập theo invocation.
- [ ] Nếu workflow bất đồng bộ, thứ tự completion không bị đánh đồng với thứ tự return đồng bộ.
- [ ] Advice không làm lộ dữ liệu nhạy cảm, không che lỗi gốc và không thêm overhead quá giới hạn.
- [ ] Kết quả kiểm chứng đi kèm artifact, phiên bản Java/công cụ và cấu hình precedence để tái lập.

## 13. Tiêu chí tự chấm bài tập

| Tiêu chí | Điểm gợi ý | Bằng chứng |
| --- | ---: | --- |
| Mô tả đúng pointcut, join point và hai trách nhiệm | 15 | Thiết kế ngắn, phân biệt Trace/Audit |
| Xác định rõ aspect ngoài/trong | 15 | Sơ đồ lồng hoặc cấu hình precedence |
| Chuỗi success chính xác | 15 | So khớp expected event đầy đủ |
| Chuỗi failure chính xác và ngoại lệ gốc được giữ | 15 | Test exception và `finally` |
| Đảo thứ tự, kiểm chứng khác biệt | 15 | Cặp kịch bản Trace/Audit và Audit/Trace |
| Code Java 17+ chạy được và assert rõ ràng | 15 | Lệnh biên dịch/chạy, `CHECK=PASS` |
| Giải thích giới hạn, lỗi thường gặp và bối cảnh thực tế | 10 | Phần phân tích về công cụ, concurrency, logging |

**Điểm tối đa: 100.** Phần chấm điểm là gợi ý để tự đánh giá; quan trọng hơn cả là giải thích được vì sao sự kiện đi ra xuất hiện theo thứ tự ngược, và vì sao việc bắt/biến đổi lỗi ở tầng trong ảnh hưởng tới những gì tầng ngoài nhìn thấy.

## 14. Tổng kết kiến thức

Với hai around advice đồng bộ được bọc theo thứ tự **A ngoài, B trong**, đường thành công có cấu trúc:

```text
A.before
  B.before
    Business
  B.afterReturning
  B.finally
A.afterReturning
A.finally
```

Đường lỗi (khi cả hai advice truyền tiếp lỗi) có cấu trúc tương tự, nhưng thay `afterReturning` bằng `afterThrowing` và giữ nguyên `finally`.

Khi đảo thứ tự bọc, thứ tự vào/ra đổi theo. Khi một advice short-circuit, retry, biến đổi ngoại lệ, phát lỗi mới hoặc xử lý bất đồng bộ, chuỗi cần suy luận lại từ **hợp đồng thực tế**. Một bài giải đúng phải kiểm chứng **thứ tự, số lần thực thi, kết quả và ngoại lệ**, đồng thời không giả định có một cú pháp precedence hoặc một loại join point dùng chung cho mọi công nghệ AOP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-007">Xây dựng aspect có ngữ cảnh riêng cho lời gọi đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Bài tập tổng hợp: Xây dựng aspect có ngữ cảnh riêng cho lời gọi đồng thời

## 1. Bối cảnh và mục tiêu

Một hệ thống Java nhận nhiều yêu cầu cùng lúc. Mỗi yêu cầu đi qua cùng một aspect để ghi nhận mã lời gọi, quan hệ cha–con và tình trạng thành công hoặc lỗi. Nếu aspect đặt thông tin của yêu cầu hiện tại vào một biến instance dùng chung, các luồng có thể ghi đè dữ liệu của nhau: yêu cầu A bất ngờ mang mã B, log liên kết sai, thông tin nội bộ rò sang lời gọi khác. Nếu lưu ngữ cảnh bằng ThreadLocal mà không xóa khi hoàn thành, một thread trong pool có thể sử dụng lại ngữ cảnh của yêu cầu trước.

**Nhiệm vụ:** xây dựng một cơ chế chặn lời gọi kiểu AOP sao cho mỗi lần vào join point có **ngữ cảnh riêng**, nhiều lời gọi đồng thời không ghi đè lên nhau, lời gọi lồng nhau có quan hệ cha–con rõ ràng, ngữ cảnh được dọn đúng cả khi phát sinh ngoại lệ. Sau đó chủ động chạy hai lời gọi đồng thời và kiểm tra các bất biến.

Bài tập dùng **AOP theo nghĩa chung**: điểm áp dụng (join point), phép chọn điểm (pointcut), advice bao quanh (around advice), context và chính sách vòng đời. Để có chương trình Java 17 tự chạy chỉ với JDK, ví dụ hiện thực hành vi chặn lời gọi bằng JDK dynamic proxy. Đây là **một cách hiện thực interception**, không phải bộ dệt bytecode tổng quát; không thể mặc định nó chặn constructor, field access, self-invocation vào target hay mọi loại join point của những công cụ AOP khác.

Sau bài học, bạn cần làm được:

1. Giải thích sự khác nhau giữa **một ngữ cảnh cho mỗi lần gọi**, **một ngữ cảnh đang hoạt động trên mỗi thread** và **một ngữ cảnh được chuyển tường minh sang tác vụ bất đồng bộ**.
2. Định nghĩa hợp đồng ngữ cảnh với định danh duy nhất, metadata cần thiết và quan hệ lời gọi cha–con.
3. Tạo around advice theo mô hình **enter → gọi target → exit trong finally**, giữ đúng kết quả và ngoại lệ.
4. Cô lập lời gọi song song; hỗ trợ lời gọi lồng nhau theo ngăn xếp, không chỉ ghi đè một giá trị ThreadLocal.
5. Chứng minh cleanup với trường hợp thành công và lỗi, gồm thread pool tái sử dụng.
6. Nhận biết giới hạn khi công việc chuyển sang một thread khác và thiết kế context propagation chủ động.
7. Tạo các test có đối chứng, kiểm tra số lần advice chạy, phạm vi pointcut, race condition và chi phí.

## 2. Kiến thức nền cần dùng

Trong một triển khai AOP:

- **Join point:** điểm thực thi có thể được mở rộng bằng advice. Tập join point được hỗ trợ tùy cơ chế AOP.
- **Pointcut:** quy tắc chọn những join point mà concern cần tác động. Không nên bắt rộng ngoài phạm vi bài.
- **Around advice:** chạy trước và sau lời gọi, có thể quản lý context, trả kết quả và chuyển ngoại lệ. Việc tiếp tục lời gọi gốc được thể hiện bằng cơ chế riêng của từng công cụ.
- **Weaving/interception:** tạo điều kiện cho advice thực sự xuất hiện trong luồng chạy; có thể thực hiện tại build/load time hoặc qua proxy ở runtime.
- **Invocation context:** dữ liệu gắn với **một lần đi vào join point**. Nếu method được gọi lại, dù cùng thread và cùng input, vẫn có một invocation riêng.
- **Execution context:** ngữ cảnh cần biết trong lúc thực hiện: correlation ID, trace/span ID, deadline, thông tin quan sát đã được giảm thiểu dữ liệu. Những giá trị này có thể cần được truyền qua các thành phần và qua ranh giới bất đồng bộ.

Điểm cốt lõi: **ThreadLocal cô lập giá trị theo thread, không tự cô lập “mỗi request” trong mọi hoàn cảnh**. Thread pool tái sử dụng thread; một request có thể chuyển qua nhiều thread; một thread cũng có thể xử lý lời gọi lồng nhau. Vì vậy, ThreadLocal chỉ là **cơ chế lưu trạng thái đang hoạt động trên thread tại thời điểm nhất định**. Hợp đồng vòng đời và truyền context mới bảo đảm ngữ nghĩa.

### 2.1. Ba phạm vi dễ nhầm

| Phạm vi | Có nghĩa gì | Ví dụ | Lưu ý |
| --- | --- | --- | --- |
| Per invocation | Mỗi lần gọi nhận một ID/scope riêng | Gọi A hai lần tạo hai invocation ID | Đây là mục tiêu chính của bài |
| Per thread | Mỗi thread có vị trí lưu trạng thái hiện hành riêng | ThreadLocal giữ đỉnh ngăn xếp context | Một thread có thể lần lượt chạy nhiều invocation |
| Per logical operation | Một công việc có thể gồm nhiều invocation và chuyển thread | Một request phát sinh hai tác vụ async | Cần propagation và quan hệ nhân quả rõ ràng |

Một lời gọi có thể có ID riêng nhưng cùng correlation ID với tác vụ cha. **Không dùng chung một đối tượng mutable** cho hai phạm vi này. Nếu cần chia sẻ metadata, dùng bản chụp (snapshot) bất biến và tạo scope mới ở từng lần tiếp tục công việc.

### 2.2. Vì sao một field dùng chung là sai?

Thiết kế nguy hiểm thường trông như sau:

```java
// Mã minh họa lỗi thiết kế; KHÔNG dùng làm aspect thật.
final class UnsafeInvocationState {
    String currentRequestId; // Một field có thể bị nhiều thread ghi cùng lúc.

    void before(String requestId) {
        currentRequestId = requestId;
    }

    String readCurrent() {
        return currentRequestId;
    }
}
```

Khi thread T1 gán A rồi nhường CPU, T2 gán B, T1 đọc lại có thể nhận B. Thêm `volatile` chỉ giúp một số thuộc tính hiển thị bộ nhớ, **không biến field thành trạng thái riêng cho từng lời gọi**. Dùng `synchronized` bao trùm toàn bộ tác vụ có thể vô tình tuần tự hóa các yêu cầu, thay đổi thông lượng và vẫn không giải quyết semantics của công việc bất đồng bộ. Sửa đúng phải bắt đầu từ **mô hình ownership của context**.

## 3. Đặc tả bài tập và tiêu chí đạt

Giả sử có một interface nghiệp vụ `Action.execute(String input)`. Cơ chế AOP cần đáp ứng hợp đồng:

| Mã | Yêu cầu | Bằng chứng đạt |
| --- | --- | --- |
| R1 | Mỗi invocation có ID duy nhất | Hai gọi đồng thời thu được hai ID khác nhau |
| R2 | Metadata của lời gọi A không trở thành metadata B | Trong target, `context.input` khớp tham số đã gọi |
| R3 | Scope được gắn trước khi vào target | Target đọc được context không-null |
| R4 | Target trả về cùng kết quả như khi không bọc, trừ metadata quan sát được quy định | Kết quả nghiệp vụ không bị sửa |
| R5 | Ngoại lệ gốc được truyền tới caller | `IllegalArgumentException` không biến thành exception phản chiếu |
| R6 | Cleanup xảy ra dù thành công hay lỗi | Sau khi thoát lời gọi, trên thread đó `current() == null` |
| R7 | Invocation lồng nhau tạo context con và khi thoát khôi phục context cha | `INNER.parentId` bằng `OUTER.id`; OUTER còn hiệu lực sau INNER |
| R8 | Có thể tái hiện và kiểm tra concurrency | Hai worker bị buộc gặp nhau trong cùng một giai đoạn bằng barrier |
| R9 | Chỉ những điểm được chọn có advice | Test một đường đi qua proxy và một đường gọi trực tiếp |
| R10 | Không để state của thread được giữ lại sau khi xong | Thread pool tái sử dụng mà không mang context cũ |

Các điều kiện R1–R8 được minh họa trực tiếp bằng chương trình hoàn chỉnh ở dưới. R9–R10 cần được mở rộng thêm bằng thử nghiệm hồi quy và môi trường triển khai cụ thể.

**Giới hạn:** không nhét toàn bộ dữ liệu yêu cầu, thông tin xác thực hoặc dữ liệu nhạy cảm vào context chỉ vì có thể lấy được từ mọi advice. Ngữ cảnh nên chứa ít thông tin nhất phục vụ concern, có thời gian sống ngắn và quy tắc truy cập rõ.

## 4. Thiết kế cấu trúc context và vòng đời

Ta chọn một đối tượng bất biến:

```java
record CallContext(long id, Long parentId, String input) {}
```

- `id`: số định danh của invocation mới, lấy từ `AtomicLong` để việc sinh ID giữa các thread không đụng nhau trong **một lần chạy tiến trình**. Đây là ID minh họa, chưa phải trace ID phân tán toàn hệ thống.
- `parentId`: null nếu là lời gọi gốc; ngược lại trỏ đến ID lời gọi đang hoạt động khi một invocation mới đi vào.
- `input`: giá trị bài tập dùng để chứng minh context không bị ghi đè; trong sản phẩm thực tế nên cân nhắc chỉ lưu dữ liệu không nhạy cảm hoặc metadata đã giảm thiểu.

Vì lời gọi có thể lồng nhau trên cùng thread, lưu ngăn xếp:

```text
Thread T1:
    [ OUTER(id=3, parent=null) ]
                 |
       INNER bắt đầu trên T1
                 v
    [ INNER(id=4, parent=3), OUTER(id=3, parent=null) ]
                 |
       INNER kết thúc, pop
                 v
    [ OUTER(id=3, parent=null) ]
                 |
       OUTER kết thúc, pop và ThreadLocal.remove()
                 v
    [ không có context ]

Thread T2:
    [ A(id=1, parent=null) ]  // độc lập với T1

Thread T3:
    [ B(id=2, parent=null) ]  // độc lập với T1 và T2
```

Đây là sơ đồ minh họa; số ID phụ thuộc thứ tự scheduler nên không được coi đây là thứ tự thực tế cố định.

Khi vào advice:

1. Đọc context hiện tại làm parent (nếu có).
2. Lấy stack riêng của thread hoặc tạo một stack mới nếu chưa có.
3. Tạo context **mới, bất biến**, push vào stack.
4. Tiếp tục lời gọi gốc **một lần**, trừ khi bài toán công khai yêu cầu semantics khác.
5. Trong `finally`, pop scope của lần gọi hiện tại; nếu stack rỗng thì `ThreadLocal.remove()`.

Khi lời gọi con kết thúc, stack vẫn chứa lời gọi cha; vì vậy chỉ xóa hẳn ThreadLocal **khi stack rỗng**. Không gọi `remove()` vô điều kiện khi trả về từ INNER vì sẽ xóa nhầm context OUTER còn sống.

Điều cần bảo toàn về thứ tự là **LIFO** (last in, first out): mỗi scope được mở sau phải đóng trước scope bên ngoài. Một thao tác cleanup không được thay đổi kết quả hoặc nuốt ngoại lệ nghiệp vụ.

## 5. Chương trình Java 17 hoàn chỉnh

Để tập trung vào tính đồng thời thay vì phụ thuộc thư viện AOP ngoài JDK, ví dụ dùng `java.lang.reflect.Proxy` chặn lời gọi qua interface `Action`. Mỗi lần chặn là một invocation scope. `ThreadLocal<Deque<CallContext>>` lưu **ngăn xếp đang hoạt động trên mỗi thread**, `AtomicLong` cấp ID duy nhất và `CyclicBarrier` làm hai lời gọi A/B cùng có mặt tại đoạn nghiệp vụ trước khi trả về.

Tạo file **ConcurrentAspectExercise.java** với toàn bộ nội dung sau:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentAspectExercise {
    @FunctionalInterface
    interface Action { String execute(String input); }

    record CallContext(long id, Long parentId, String input) {}
    record WorkerResult(String value, boolean cleaned) {}

    private static final ThreadLocal<Deque<CallContext>> STACK = new ThreadLocal<>();
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong ADVICE_RUNS = new AtomicLong();

    static CallContext current() {
        Deque<CallContext> stack = STACK.get();
        return stack == null ? null : stack.peek();
    }

    static Action withContext(Action target) {
        return (Action) Proxy.newProxyInstance(
                Action.class.getClassLoader(),
                new Class<?>[]{Action.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("execute")) {
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    }
                    CallContext parent = current();
                    Deque<CallContext> stack = STACK.get();
                    if (stack == null) {
                        stack = new ArrayDeque<>();
                        STACK.set(stack);
                    }
                    CallContext ctx = new CallContext(
                            IDS.incrementAndGet(),
                            parent == null ? null : parent.id(),
                            (String) args[0]);
                    stack.push(ctx);
                    ADVICE_RUNS.incrementAndGet();
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    } finally {
                        stack.pop();
                        if (stack.isEmpty()) STACK.remove();
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        CyclicBarrier sameTime = new CyclicBarrier(2);
        Set<Long> concurrentIds = ConcurrentHashMap.newKeySet();
        AtomicReference<Action> self = new AtomicReference<>();
        AtomicBoolean nestedParentFound = new AtomicBoolean();

        Action target = input -> {
            CallContext ctx = current();
            if (ctx == null || !input.equals(ctx.input())) {
                throw new AssertionError("Context missing or wrong");
            }
            if (input.equals("FAIL")) {
                throw new IllegalArgumentException("rejected");
            }
            if (input.equals("OUTER")) {
                return "outer>" + self.get().execute("INNER");
            }
            if (input.equals("INNER")) {
                nestedParentFound.set(ctx.parentId() != null);
                return "inner";
            }
            concurrentIds.add(ctx.id());
            try {
                sameTime.await(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("Workers did not overlap", ex);
            }
            return input + "#" + ctx.id();
        };

        Action observed = withContext(target);
        self.set(observed);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        boolean isolated;
        try {
            Future<WorkerResult> a = pool.submit(() -> {
                String value = observed.execute("A");
                return new WorkerResult(value, current() == null);
            });
            Future<WorkerResult> b = pool.submit(() -> {
                String value = observed.execute("B");
                return new WorkerResult(value, current() == null);
            });
            WorkerResult ra = a.get(15, TimeUnit.SECONDS);
            WorkerResult rb = b.get(15, TimeUnit.SECONDS);
            isolated = ra.value().startsWith("A#")
                    && rb.value().startsWith("B#")
                    && !ra.value().equals(rb.value())
                    && concurrentIds.size() == 2
                    && ra.cleaned() && rb.cleaned();
        } finally {
            pool.shutdownNow();
        }

        String nested = observed.execute("OUTER");
        boolean nestingOk = nested.equals("outer>inner")
                && nestedParentFound.get() && current() == null;

        boolean thrown = false;
        try {
            observed.execute("FAIL");
        } catch (IllegalArgumentException ex) {
            thrown = ex.getMessage().equals("rejected");
        }

        System.out.println("Concurrent calls isolated: " + isolated);
        System.out.println("Nested context restored: " + nestingOk);
        System.out.println("Failure cleanup: " + (thrown && current() == null));
        System.out.println("Advice invocations: " + ADVICE_RUNS.get());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ConcurrentAspectExercise.java
java ConcurrentAspectExercise
```

**Kết quả chuẩn khi hai worker hoàn thành trong thời hạn chờ:**

```text
Concurrent calls isolated: true
Nested context restored: true
Failure cleanup: true
Advice invocations: 5
```

Mã được viết theo Java 17 và chỉ sử dụng API chuẩn của JDK. Việc dùng `javac --release 17` bảo đảm cú pháp, API nền tảng và class-file target phù hợp Java 17. Nếu môi trường quá tải khiến hai worker không gặp nhau trước timeout, chương trình sẽ báo lỗi đồng bộ thay vì in các khẳng định thành công; đó là vấn đề điều kiện chạy thử cần được đánh giá riêng.

### 5.1. Giải thích từng phần

**Một stack cho từng thread.** `STACK` là một biến `static`, nhưng giá trị liên kết với nó do `ThreadLocal` lưu riêng cho từng thread. Hai thread sẽ không cùng thao tác trên một `ArrayDeque` trong chương trình này. `ArrayDeque` không thread-safe, nhưng vẫn hợp lệ ở đây vì mỗi deque do một thread sở hữu và không được gửi sang thread khác. Nếu vô tình chia sẻ tham chiếu deque qua thread, tính an toàn đó không còn đúng.

**Một context cho từng invocation.** `IDS.incrementAndGet()` tạo ID mới bất kể thread nào đang gọi. Cùng một thread gọi A hai lần cũng phải có hai ID khác nhau. `CallContext` là `record`, giúp metadata của một invocation bất biến sau khi tạo. Không đặt `currentId` thành một field mutable trong singleton aspect.

**Quan hệ cha–con.** `parent = current()` được lấy **trước khi push** context mới. Khi OUTER gọi INNER qua chính proxy `self.get()`, INNER sẽ có parent ID của OUTER. Sau khi INNER trả về, `stack.pop()` chỉ loại phần tử INNER; OUTER vẫn ở đỉnh và cuối cùng được dọn đúng lúc OUTER kết thúc.

**Nhận diện lỗi và giữ ngoại lệ.** `Method.invoke` bọc ngoại lệ do target ném trong `InvocationTargetException`. Advice lấy `getCause()` và ném lại để caller nhận đúng `IllegalArgumentException` của target. `finally` vẫn chạy khi có ngoại lệ nên context không bị bỏ lại. Đoạn code không áp dụng retry, không nuốt lỗi và không tự thay kết quả nghiệp vụ.

**Kiểm chứng đồng thời.** Hai callable A và B chạy trên pool có hai thread. `CyclicBarrier(2)` buộc cả hai đi vào một pha trước khi chạy tiếp. Target ghi ID vào concurrent set, kết quả chứa input và ID; test kiểm tra đúng input, hai ID khác nhau và context sạch sau khi lời gọi trên thread worker đã trả về. Không in ID cụ thể, vì thứ tự cấp ID phụ thuộc scheduler và không được giả định cố định.

**Đếm advice.** Có hai invocation song song A, B; hai invocation lồng nhau OUTER, INNER; và một invocation lỗi FAIL. Tổng cộng đúng năm lần advice được thực thi. Bộ đếm `AtomicLong` dùng chung nhưng chỉ chứa **số liệu tổng hợp**, không giữ context của từng yêu cầu. Đây là khác biệt quan trọng giữa state chia sẻ có đồng bộ để quan sát và state riêng để mô tả lời gọi.

### 5.2. Vì sao không dùng `ThreadLocal.withInitial(ArrayDeque::new)`?

Cách dùng `withInitial` cũng hợp lệ nếu mọi đường đi được thiết kế và dọn đúng. Trong bài, `new ThreadLocal<>()` cho phép `current()` trả về null khi thread không có context mà không tạo một deque trống mới. Nó làm phép kiểm tra cleanup trở nên rõ ràng. Quy tắc quan trọng là **không để context còn sống sau khi scope cuối kết thúc**; không phải phải dùng chính xác một kiểu khởi tạo.

### 5.3. Điều gì xảy ra nếu bỏ `finally`?

Nếu chỉ pop ở đường thành công, lần gọi FAIL sẽ để lại một context trên thread hiện tại. Khi thread đó được tái sử dụng trong pool, lời gọi mới có thể nhận nhầm context cũ làm parent; nhật ký và phân tích nguyên nhân lỗi trở nên sai. Ngay cả khi method được gọi liên tục trên cùng thread, lỗi dọn context vẫn có thể ảnh hưởng lời gọi kế tiếp.

Nếu chỉ gọi `STACK.remove()` sau mỗi lần gọi, scope INNER sẽ xóa toàn bộ context, khiến OUTER mất trạng thái đang có. Vì vậy phải **pop một scope và chỉ remove khi deque rỗng**.

## 6. Tự chạy bộ kiểm thử bổ sung

Ví dụ đã kiểm tra các tình huống trọng tâm. Để thật sự hiểu thiết kế, bổ sung các test sau mà không đổi bản chất bài:

### Test A — nhiều lời gọi tuần tự trên một thread trong pool

Tạo `Executors.newSingleThreadExecutor()`, lần lượt submit hai tác vụ đi qua proxy. Vì cùng worker thread có thể được tái sử dụng, sau mỗi lời gọi hãy assert `current() == null` ở bên trong worker, kiểm tra ID hai invocation khác nhau và parent ID null. Đây là test trực tiếp của điều kiện không rò ngữ cảnh khi thread được tái sử dụng.

**Lưu ý:** target demo có barrier 2 parties dành riêng cho A/B. Khi thêm test một thread, tạo target khác không chờ barrier hoặc thêm một nhánh input riêng để tránh deadlock do thiếu party.

### Test B — nested invocation sâu hơn hai tầng

Cho `OUTER → MIDDLE → INNER`. Trong mỗi lời gọi, ghi lại cặp (id, parentId). Assert:

- OUTER có parentId null.
- MIDDLE có parentId là id của OUTER.
- INNER có parentId là id của MIDDLE.
- Sau INNER, đỉnh stack lại là MIDDLE; sau MIDDLE, đỉnh lại là OUTER.
- Sau OUTER, `current() == null`.

Một biến ThreadLocal chỉ giữ **một** context sẽ không tự khôi phục đúng ba tầng. Cấu trúc stack giải quyết trường hợp này.

### Test C — ngoại lệ từ lời gọi bên trong

Cho INNER ném lỗi. Nếu MIDDLE không bắt lỗi, ngoại lệ sẽ đi ngược qua MIDDLE và OUTER. Mỗi scope vẫn phải pop trong `finally`. Nếu MIDDLE bắt lỗi và tiếp tục, ngay sau khi INNER kết thúc, context của MIDDLE phải được khôi phục và vẫn hoạt động hợp lệ. Cần kiểm tra **cả trường hợp có và không có handler ở scope cha**.

### Test D — kiểm chứng đường đi qua proxy

Gọi `observed.execute(...)` rồi so với `target.execute(...)` khi target không phụ thuộc context, để xác định lời gọi trực tiếp không kích hoạt advice. Trong demo chính, target cố ý yêu cầu context nên muốn thử trực tiếp phải thay bằng một target đơn giản có thể chạy cả hai đường. Test này chỉ ra **giới hạn điểm áp dụng** của proxy: lời gọi không đi qua wrapper không được tự động instrument. Với một công cụ weaving khác, tập join point có thể khác và phải kiểm tra đúng cơ chế đó.

### Test E — stress test và race detection

Chạy hàng nghìn invocation ngắn, nhiều thread, ghi tuple (input, invocationId, threadId, parentId, outcome). Assert mỗi invocation ID là duy nhất trong lần chạy, giá trị input tại target khớp đối tượng đang thực thi, không có context còn lại khi scope kết thúc và số lần target khớp số lần được phép. Sau đó thêm lỗi ngẫu nhiên có seed cố định và kiểm tra cleanup. Bài stress test không phải bằng chứng toán học loại trừ mọi race, nhưng giúp phát hiện việc dùng nhầm state chung và hành vi không ổn định.

## 7. Mở rộng nâng cao: context qua tác vụ bất đồng bộ

`ThreadLocal` **không tự truyền context** qua `ExecutorService.submit()`. Nếu lời gọi gốc đang có context nhưng một `Callable` được chạy trên thread pool khác, thread mới có thể có context rỗng hoặc một context riêng. Điều này là đúng theo phạm vi lưu trữ thread, nhưng có thể **không đúng theo yêu cầu quan hệ nhân quả** của hệ thống.

Nếu cần liên hệ công việc async với invocation cha, nên:

1. **Chụp snapshot bất biến** của metadata được phép truyền khi còn ở trong scope gốc (ví dụ correlationId, traceId, deadline).
2. **Truyền tường minh** snapshot vào tác vụ mới hoặc thông qua thư viện propagation được thiết kế rõ ràng.
3. Khi worker bắt đầu, **mở scope mới** gắn với snapshot; tạo invocation ID mới thay vì tái sử dụng ID của lần gọi cha làm ID của lần gọi con.
4. Thực thi tác vụ và đóng scope trong `finally`, khôi phục context trước đó của worker nếu có.
5. Xác định quan hệ parent/span theo semantics: một tác vụ async có thể bắt đầu sau khi scope cha đã đóng; không được kỳ vọng còn giữ nguyên stack live của thread cha.

Ví dụ khái niệm (pseudocode, **không phải đoạn Java độc lập**):

```text
// Trong scope hiện tại:
snapshot = immutableCopyOfAllowedContextFields(current())
future = executor.submit(() -> {
    scope = openChildScopeFrom(snapshot)  // context mới trên worker
    try {
        return performAsyncWork()
    } finally {
        scope.close()                    // khôi phục context cũ của worker
    }
})
```

**Không làm**:

- Đưa trực tiếp `ArrayDeque` của ThreadLocal sang worker: nó là state có thể thay đổi và không được thiết kế chia sẻ giữa thread.
- Lấy toàn bộ object request để đưa vào thread pool vô thời hạn, vì vừa kéo dài vòng đời dữ liệu vừa tạo nguy cơ rò và race.
- Dựa vào `InheritableThreadLocal` như cơ chế propagation mặc định cho thread pool; thread trong pool thường được tạo ở thời điểm khác và tái sử dụng, nên giá trị kế thừa lúc tạo thread không phản ánh ngữ cảnh của từng task submit.
- Mặc định tác vụ đã hoàn thành chỉ vì method đã **trả về** một `Future`; phải làm rõ đo ở mốc trả Future hay mốc công việc async kết thúc.

Java 17 có thể sử dụng executor và ThreadLocal theo mô hình trên. Những cơ chế context tiên tiến khác ở các phiên bản JDK mới cần kiểm tra tài liệu runtime và chiến lược propagation tương ứng; không nên đưa đặc tính của phiên bản mới vào yêu cầu chạy Java 17.

### 7.1. Scope token và vòng đời an toàn hơn

Ở hệ thống lớn, có thể đóng gói vào một abstraction kiểu `Scope` triển khai `AutoCloseable`, cho phép:

```java
// Mã phác thảo API, chưa phải chương trình biên dịch độc lập.
try (InvocationScope scope = contextManager.open(metadata)) {
    return action.execute();
}
```

Thiết kế này có lợi khi có nhiều đường thoát. Tuy nhiên, `close()` phải bảo đảm LIFO và khôi phục context cũ, tránh đóng hai lần, tránh dùng scope từ thread khác khi cơ chế không hỗ trợ, và xử lý exception từ cleanup. Dù dùng `AutoCloseable`, việc giữ đúng vòng đời vẫn là trách nhiệm thiết kế, không tự động có được chỉ nhờ cú pháp try-with-resources.

### 7.2. Per-invocation, per-object và per-target khác nhau

Một số mô hình AOP hỗ trợ phạm vi aspect instance liên quan đến đối tượng đích, đối tượng gọi, luồng control flow hoặc những điều kiện khác. Phạm vi **aspect instance** không đương nhiên đồng nhất với **invocation context**. Một aspect instance phục vụ nhiều method call theo thời gian vẫn cần context riêng cho từng call. Trong bài, proxy `observed` là đối tượng duy nhất nhưng mỗi lần gọi có `CallContext` mới.

Trường hợp sử dụng một aspect instance riêng cho một đối tượng nghiệp vụ vẫn có thể xuất hiện hai thread cùng gọi vào đối tượng đó; do vậy không thể coi field instance là per-invocation chỉ vì target object khác nhau.

## 8. Tính đúng đắn trước các tình huống thất bại

Một implementation context được coi là đáng tin khi giữ invariant ở **mọi đường thoát**:

| Tình huống | Kỳ vọng | Sai lầm thường gặp |
| --- | --- | --- |
| Target trả về bình thường | Caller nhận đúng kết quả; stack được pop | Quên dọn, giữ request cũ |
| Target ném ngoại lệ | Caller nhận ngoại lệ gốc; stack được pop | Catch lỗi rồi trả null, hoặc bỏ qua finally |
| Advice đo thời gian/logging thất bại | Áp dụng chính sách failure đã công bố và bảo toàn ngoại lệ ưu tiên | Lỗi ghi metric che lỗi nghiệp vụ |
| Lời gọi lồng nhau | Khôi phục context cha ngay khi con kết thúc | Remove ThreadLocal ngay khi con trả về |
| Hai thread gọi đồng thời | Context không chia sẻ mutable state | Một field currentContext trên aspect instance |
| Task chuyển executor | Context được snapshot và truyền rõ ràng nếu cần | Giả định ThreadLocal “theo request” xuyên thread |
| Task bị hủy/timeout | Dọn scope ở worker khi nó thực sự kết thúc | Coi timeout ở caller đồng nghĩa worker đã dừng |
| Pointcut bị mở rộng | Join point ngoài hợp đồng không bị chặn | Wildcard quá rộng, không có negative test |

**Đặc biệt quan trọng:** trong Java, việc caller hủy một `Future` không tự bảo đảm tác vụ đã dừng; tùy khả năng hợp tác xử lý interruption và I/O. Scope trên worker phải được dọn khi đoạn thực thi trên worker thực sự thoát. Nếu một async task sống lâu, metadata giữ cùng nó cũng có thể sống lâu; cần có giới hạn thời gian và tài nguyên tương ứng.

## 9. Kiểm soát pointcut và thứ tự advice

Mẫu dynamic proxy ở ví dụ chỉ có một nhánh chặn `execute`. Trong một hệ AOP thực sự, tập join point có thể được chọn qua biểu thức điểm cắt, cấu hình, annotation hoặc cơ chế khác. Không nên bắt tất cả method của toàn ứng dụng để tiện có context, vì các lời gọi hạ tầng logging và chính cơ chế context có thể bị chặn, gây đệ quy hoặc tạo context con không mong đợi.

Trước khi triển khai, định nghĩa tập điểm áp dụng:

- Entry point nào cần tạo context **gốc**?
- Các method con nào phải dùng context cha hay mở **invocation con**?
- Đường gọi nào cố ý không có context?
- Có chặn cả code hạ tầng của advice không?
- Có lời gọi direct/self-invocation không qua proxy nhưng người dùng kỳ vọng nó được chặn không?
- Nếu nhiều advice cùng chặn, điều gì xảy ra khi context scope được đặt trước hoặc sau advice retry, đo thời gian hay transaction?

**Thứ tự có ý nghĩa.** Nếu một advice retry bao ngoài advice tạo context, mỗi attempt có thể nhận invocation ID mới; nếu advice tạo context bao ngoài retry, nhiều attempt có thể nằm trong cùng một scope. Cả hai đều có thể có lý tùy bạn định nghĩa một invocation là “yêu cầu logic” hay “một lần thực thi target”. Cần lựa chọn rõ ràng, đặc biệt khi ghi log, metric, tracing, kiểm toán và idempotency key.

Đối với bài này, một invocation được định nghĩa là **mỗi lần đi qua proxy.execute**. Lời gọi OUTER và lời gọi INNER vì đi qua proxy hai lần nên là hai invocation có quan hệ cha–con. Khi thêm retry, phải cập nhật định nghĩa hoặc thứ tự advice và bài test đếm số lần thực thi.

## 10. Thiết kế logger và telemetry không làm hỏng context

Một aspect có thể ghi sự kiện enter/exit để giải thích hệ thống; song logger cũng có giới hạn:

- **Nhận dạng:** ghi invocation ID, correlation ID phù hợp, tên thao tác đã được cho phép và trạng thái. Không mặc định log toàn bộ input, token hoặc thông tin cá nhân.
- **Phân biệt pha:** enter, success, failure, cleanup; có thể ghi thời lượng nếu có thiết kế đo đúng.
- **Tính đồng thời:** dùng cấu trúc thread-safe và exporter phù hợp, tránh danh sách dùng chung không đồng bộ.
- **Số lần sự kiện:** nested calls tạo nhiều event; xác định metric cần đếm mỗi invocation hay mỗi request. Không cộng nhầm.
- **Failure policy:** logger/external exporter hỏng có làm thất bại tác vụ không? Tùy concern và yêu cầu; cần chọn rõ trước khi viết advice.
- **Backpressure:** ghi đồng bộ mọi join point có thể tăng độ trễ và làm queue đầy; dùng sampling/batching nếu phù hợp, đồng thời theo dõi số event bị drop.
- **Cleanup:** đừng để một lỗi logger ở finally ngăn stack pop. Có thể tách việc dọn state bắt buộc khỏi thao tác xuất dữ liệu có thể thất bại.

Nếu cần log hành vi của nhiều luồng để debug, không nên dựa vào thứ tự tổng của các sự kiện để suy ra quan hệ nhân quả. Nên dùng ID, parent ID, monotonic timestamps và event boundaries, rồi dựng cây/lịch sử thực thi. Hai thread có thể xen kẽ log hợp lệ theo rất nhiều thứ tự khác nhau.

## 11. Đo hiệu năng và tài nguyên của cơ chế context

Context per invocation cũng có chi phí: cấp phát record, push/pop, phép truy cập ThreadLocal, sinh ID atomic và xuất dữ liệu quan sát. Không thể kết luận “ThreadLocal luôn rẻ” hoặc “AtomicLong chắc chắn là nút thắt” nếu chưa đo dưới tải đại diện.

Thiết kế benchmark có đối chứng:

| Biến thể | Cấu hình | Điều cần đo |
| --- | --- | --- |
| B0 | Gọi target trực tiếp, không advice | Baseline logic nghiệp vụ |
| B1 | Qua proxy nhưng advice không tạo context | Chi phí interception |
| B2 | Qua proxy, tạo/đóng context, không ghi log ngoài | Chi phí scope, allocation, atomic ID |
| B3 | Context + logging/tracing như production | Chi phí toàn bộ concern, I/O và queue |

Đo latency p50/p95/p99, throughput, CPU, allocation rate, GC và số context/advice mỗi yêu cầu. Thử cả ít thread và nhiều thread; tỷ lệ nested call cao; workload ngắn và dài; trạng thái bình thường và lỗi. Dùng cùng dữ liệu và cùng offered load, warm-up và lặp nhiều lượt. Vi mô có thể dùng công cụ benchmark chuyên dụng thay vì kết luận từ một vòng lặp `System.nanoTime()` tự viết. Đo end-to-end cũng cần máy phát tải và cấu hình runtime ổn định.

Sự khác nhau giữa cơ chế AOP được dùng cũng có thể thay đổi chi phí. Kết quả đo proxy mẫu không đại diện tuyệt đối cho weaving bytecode; chỉ suy rộng trong phạm vi đã chứng minh.

## 12. Lỗi phổ biến và cách sửa

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Một field `currentContext` chia sẻ | Lời gọi đồng thời ghi đè nhau | Lưu state theo scope, per-thread stack khi phù hợp |
| Chỉ dùng ThreadLocal một giá trị | Nested invocation mất context cha | Dùng stack hoặc scope có cơ chế lưu/khôi phục |
| Quên cleanup khi throw | Thread pool mang context của yêu cầu trước | `finally` + pop + remove khi rỗng |
| Gọi remove trong INNER | OUTER không còn context | Chỉ xóa khi scope cuối cùng kết thúc |
| Truyền trực tiếp mutable deque sang thread khác | Race, corrupt stack, leakage | Truyền snapshot bất biến, tạo scope mới tại worker |
| Cho rằng ThreadLocal đi theo Future | Task async thiếu context | Propagation có chủ đích và kiểm thử |
| Chia sẻ một ID cho mọi nested invocation | Không phân biệt mỗi call với nhóm request | Tách invocation ID và correlation/parent ID |
| Proxy không áp dụng cho self-invocation | Điểm kỳ vọng không có advice | Test đúng đường gọi; thay cơ chế/wiring nếu cần |
| Pointcut quá rộng | Advice bắt cả logger, dễ recursion | Hợp đồng pointcut và negative tests |
| Retry bên trong/ngoài scope thiếu định nghĩa | Sai quan hệ attempt/request, đếm log lệch | Chốt order và đơn vị quan sát |
| Chỉ thử lần chạy tuần tự | Không lộ ghi đè context | Barrier, nhiều thread và lặp stress |
| Chỉ assert ID không trùng | Có thể vẫn lộ input hoặc lỗi cleanup | Kiểm tra data isolation, nesting, exception, cleanup |

## 13. Nâng cấp bài tập thành một thành phần thực tế

Sau khi chương trình mẫu đạt yêu cầu, có thể refactor thành ba thành phần độc lập:

1. **`ContextManager`:** tạo ID, lưu stack, trả `current()`, mở/đóng scope và quản lý snapshot. Phần này không phụ thuộc công cụ AOP.
2. **`ContextAdvice`:** xác định enter/exit tại join point, dùng ContextManager, không nhúng logic nghiệp vụ. Bản adapter cho cơ chế AOP cụ thể có trách nhiệm gọi target và bảo toàn exceptions.
3. **`ContextPropagation`:** tạo wrapper cho executor/callback và chuyển snapshot sang task mới theo chính sách, không chia sẻ live mutable stack.

Tách như vậy giúp test context manager mà không cần weaving, test advice để xác minh mỗi join point mở đúng một scope, và test propagation ở ranh giới async. Một interface scope có thể hỗ trợ tài liệu hóa trách nhiệm cleanup và xây dựng invariant rõ ràng.

Khi đưa vào code thật, bổ sung giới hạn độ dài chuỗi metadata, chính sách bảo mật dữ liệu, định nghĩa thời điểm bắt đầu/kết thúc span, chiến lược ID đủ phạm vi hệ thống, cơ chế cancellation, hỗ trợ nested lỗi phức tạp và khả năng quan sát việc mất context.

**Không biến lời gọi async thành synchronous chỉ để ThreadLocal tiện sử dụng.** Nếu một công việc logic đi qua nhiều thread, giải pháp là mô hình propagation tường minh hoặc công cụ hỗ trợ context được kiểm chứng, không phải buộc mọi tác vụ vào một thread.

## 14. Checklist đánh giá bài hoàn thành

- [ ] Cấu trúc dữ liệu nói rõ scope: invocation ID, parent ID và metadata an toàn.
- [ ] Hai lời gọi A/B đồng thời có context riêng; không ghi đè input/ID.
- [ ] Advice thiết lập context **trước khi** target chạy.
- [ ] Advice bảo toàn return/exception của target theo hợp đồng.
- [ ] Cleanup đặt trong `finally`; không rò sau success hoặc failure.
- [ ] Lời gọi lồng nhau khôi phục context cha bằng LIFO.
- [ ] Worker thread tái sử dụng không nhận context cũ.
- [ ] Số lần target và advice đúng theo định nghĩa invocation.
- [ ] Có negative test cho đường không nằm trong pointcut.
- [ ] Có kiểm thử concurrency thực sự, không dựa vào một biến chia sẻ hoặc `sleep` như bằng chứng duy nhất.
- [ ] Hạn chế đặc trưng của proxy/weaving được giải thích chính xác.
- [ ] Với async, metadata truyền bằng snapshot, mỗi worker mở/đóng scope rõ ràng.
- [ ] Không có logger/telemetry hoặc advice khác làm phá cleanup.
- [ ] Có kế hoạch đo tác động tài nguyên nếu concern chạy ở tần suất cao.

### Câu hỏi tự kiểm tra

**1. Vì sao AtomicLong sinh ID duy nhất nhưng không thay thế ThreadLocal?** AtomicLong chỉ quản lý một bộ đếm dùng chung an toàn; nó không giữ được context riêng để target đang chạy tìm lại metadata của chính invocation đó.

**2. Vì sao một ThreadLocal chứa đúng một context vẫn sai trong lời gọi lồng nhau?** INNER sẽ ghi đè context OUTER và khi kết thúc có thể không khôi phục được OUTER. Stack hoặc scope lưu giá trị trước đó mới bảo đảm nesting.

**3. Khi nào `finally` chạy?** Khi thoát khối `try` theo đường bình thường hoặc do exception, kể cả khi exception được chuyển tiếp. Không được suy rộng thành “finally luôn chạy trong mọi hoàn cảnh” khi JVM/process bị chấm dứt cưỡng bức.

**4. Vì sao không dựa vào thứ tự ID A=1, B=2?** Các thread được scheduler chạy không theo một trật tự toàn cục có thể dự đoán. Test nên xác minh invariant: tính duy nhất, đúng input và cleanup, không cố định phân phối ID cho thread.

**5. Vì sao propagation khác với chia sẻ ngăn xếp ThreadLocal?** Propagation chuyển một **snapshot của ý nghĩa công việc** để worker tạo scope của chính nó; chia sẻ stack mutable tạo race và phá quy tắc LIFO.

**6. Nếu một advice retry gọi target ba lần, có bao nhiêu invocation?** Phụ thuộc điểm đặt scope và định nghĩa invocation. Nếu advice context mở **một lần ngoài retry**, có một scope cho yêu cầu và ba attempt. Nếu mỗi attempt đi qua join point context, có ba scope. Phải quy định và kiểm thử rõ ràng.

**7. Một lời gọi bất đồng bộ trả Future có nghĩa tác vụ gốc đã hoàn tất chưa?** Chưa chắc. Method có thể đã trả đối tượng Future trong khi tác vụ tiếp tục chạy. Cần chọn rõ scope theo lần gọi đồng bộ hay theo vòng đời công việc async.

## 15. Kết quả mong đợi

Hoàn thành bài tập nghĩa là bạn có thể giải thích và chứng minh rằng **mỗi lần gọi có context riêng**, không bị trộn giữa các luồng, hỗ trợ quan hệ cha–con cho lời gọi lồng nhau, cleanup chính xác trong mọi đường thoát và biết giới hạn của context khi công việc chuyển thread. Một thiết kế AOP tốt không chỉ làm các concern dùng chung dễ tái sử dụng; nó còn làm vòng đời và ownership của dữ liệu ngữ cảnh **có thể kiểm chứng** dưới tải đồng thời.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-008">Minh họa pointcut theo luồng điều khiển hoặc mở rộng cấu trúc kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Bài tập tổng hợp: Minh họa pointcut theo luồng điều khiển hoặc mở rộng cấu trúc kiểu

## 1. Mục tiêu của bài

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), không phải mọi quy tắc xuyên suốt đều chọn được chỉ bằng tên lớp hoặc chữ ký phương thức. Có tình huống yêu cầu **cùng một phương thức nhưng chỉ được áp dụng advice khi nó chạy trong một luồng điều khiển nhất định**. Cũng có yêu cầu mở rộng hợp đồng của một kiểu đang tồn tại mà không trực tiếp sửa tệp nguồn của kiểu ấy.

Bài tập này khám phá hai khả năng khác nhau:

1. **Pointcut theo luồng điều khiển** (*control-flow pointcut*): minh họa `cflow` và `cflowbelow`, kết hợp điều kiện theo call stack động với pointcut tĩnh `execution`.
2. **Mở rộng cấu trúc kiểu** (*inter-type declaration, ITD*): dùng một aspect khai báo thêm method và quan hệ implements interface cho lớp khác, sau đó chứng minh classfile sau weaving có thay đổi tương ứng.

Cần phân biệt ngay: **`cflow` là một pointcut động**, còn **ITD là khai báo thay đổi cấu trúc kiểu tại thời điểm weaving**, không phải một loại pointcut khác. Hai kỹ thuật này có thể cùng xuất hiện trong một hệ thống AOP nhưng giải quyết hai yêu cầu riêng.

Ví dụ sử dụng **Java 17+ và trình biên dịch/weaver AspectJ độc lập** để minh họa khả năng AOP cơ bản và nâng cao. Một cơ chế AOP khác có thể không hỗ trợ `cflow`, việc thêm member hay `declare parents`. Vì vậy hãy phân biệt giữa **ý tưởng thiết kế AOP** và **cú pháp/capability cụ thể của weaver**.

Sau bài học, bạn phải giải thích được tại sao advice được thực thi ở lời gọi nội bộ nhưng không được thực thi ở lời gọi độc lập; tại sao `Invoice` có thể cung cấp method và interface mới trong artifact đã weave; và cách viết test phát hiện các lỗi match quá rộng, thiếu weaving hoặc chạy nhầm bytecode.

## 2. Tình huống nghiệp vụ và tiêu chí nghiệm thu

Ứng dụng đơn giản có hai yêu cầu:

**Yêu cầu A – quan sát tồn kho trong quá trình checkout.** Method `Inventory.read(String)` có thể chạy ở nhiều nơi. Chỉ khi nó được gọi trong đường thực thi đồng bộ đang nằm bên trong `OrderService.checkout()`, advice mới in một dòng `FLOW: inventory read inside checkout`. Khi `Inventory.read` được gọi trực tiếp bên ngoài checkout, **không** in dòng đó. Không thay đổi kết quả và thứ tự nghiệp vụ.

**Yêu cầu B – tăng khả năng cho lớp hóa đơn.** Lớp `Invoice` ban đầu chỉ có dữ liệu và phương thức `amount()`; không khai báo `label()` và không implements `Billable` trong mã nguồn. Một aspect khác phải:

- Khai báo `Invoice implements Billable`.
- Bổ sung method `String label()` trả về chuỗi `INVOICE:<amount>`.
- Cho phép mã ứng dụng gọi `invoice.label()` và kiểm tra `invoice instanceof Billable` sau khi compile/weave thành công.

### Ma trận kết quả dự kiến

| Thao tác | Điểm được chọn / thay đổi | Kết quả bắt buộc |
|---|---|---|
| `OrderService.checkout()` gọi `Inventory.read("inside")` | `execution(Inventory.read)` nằm trong `cflow(checkout)` | Một dòng `FLOW: ...` trước `INVENTORY:inside` |
| `new Inventory().read("outside")` | Cùng method nhưng **không** nằm trong checkout | Chỉ `INVENTORY:outside`, không có `FLOW` |
| Tạo `new Invoice(42)` | Class được weave thêm interface và method | Khởi tạo và đọc amount bình thường |
| `invoice.label()` | Method được khai báo qua ITD | In `INVOICE:42` |
| `invoice instanceof Billable` | Quan hệ type được thêm qua `declare parents` | `true` |
| Chạy Java class chưa weave | Thiếu thay đổi ở bytecode/kiểu | Không thỏa toàn bộ hợp đồng bài tập |

Điểm quan trọng nhất của bài là **kiểm thử trường hợp không được chọn**, chứ không chỉ xem có thêm được một dòng advice hay không.

## 3. Kiến thức nền: static pointcut, dynamic pointcut và ITD

### 3.1. Phần nào xác định được khi weave?

`execution(void demo.flow.Inventory.read(String))` biểu thị method execution với chữ ký cụ thể. Weaver có thể tìm các method execution join point tương ứng từ mã đầu vào. Đây là phần **lọc theo cấu trúc tĩnh**.

Ngược lại, cùng method `Inventory.read` có thể được gọi trong hoặc ngoài checkout. Điều kiện đó phụ thuộc **đường thực thi hiện thời**. `cflow(execution(void demo.flow.OrderService.checkout()))` chỉ đúng khi chương trình đang ở trong luồng điều khiển động bắt nguồn từ execution join point được mô tả. Weaver có thể chuẩn bị mã theo dõi phạm vi, nhưng việc một lời gọi thuộc phạm vi hay không phải xét tại runtime.

Ví dụ:

```text
main
 ├─ OrderService.checkout()          <-- bắt đầu phạm vi cflow
 │   ├─ validate()
 │   └─ Inventory.read("inside")    <-- đúng điều kiện cflow
 │       └─ return
 │   └─ return                      <-- kết thúc phạm vi
 └─ Inventory.read("outside")        <-- không thuộc cflow
```

**Điều kiện đồng thời:** để advice chạy, method phải vừa khớp `execution(Inventory.read)` **vừa** nằm trong `cflow(checkout)`. Chỉ dùng `cflow` mà không giới hạn execution có thể khiến advice chạy trên rất nhiều join point thuộc toàn bộ nhánh điều khiển.

### 3.2. Khác biệt `cflow` và `cflowbelow`

Trong ngữ nghĩa AspectJ:

- `cflow(P)` chọn các join point trong luồng điều khiển động bắt nguồn từ join point khớp `P`, **bao gồm chính join point bắt đầu**.
- `cflowbelow(P)` chọn các join point **nằm dưới** join point bắt đầu đó, **không bao gồm join point bắt đầu**.

Nếu `P` là `execution(void OrderService.checkout())`, thì advice gắn lên chính execution `checkout()` sẽ thấy khác biệt giữa `cflow(P)` và `cflowbelow(P)`. Tuy nhiên, khi advice đã giới hạn ở execution `Inventory.read(String)` được gọi **bên trong** checkout, cả hai cách đều có thể chọn cùng lời gọi nội bộ đó trong ví dụ thông thường.

Nói “`cflowbelow` loại toàn bộ lời gọi trực tiếp bên trong method gốc” là sai: nó chỉ loại **join point bắt đầu `P`**, không loại các join point thực thi bên dưới.

### 3.3. ITD là gì?

Inter-type declaration (còn được gọi là *introduction* trong một số mô hình AOP) cho phép một aspect tuyên bố member hoặc quan hệ kiểu cho một lớp khác. Với AspectJ, các ví dụ điển hình là:

- Khai báo thêm method hoặc field cho type khác.
- Dùng `declare parents` để khai báo type thực hiện một interface hay tham gia quan hệ kế thừa được hỗ trợ.

Sau compile-time weaving, bytecode của lớp bị tác động có thể chứa các member/interface được bổ sung. Điều này khác về bản chất với proxy: **ITD thay đổi cấu trúc classfile mà JVM nhìn thấy**, còn interceptor tại runtime có thể chỉ điều hướng một lời gọi mà không bổ sung member thật vào class gốc.

Không phải thay đổi cấu trúc nào cũng hợp lệ. Các quy tắc access, xung đột method đã tồn tại, class/interface hierarchy, hạn chế từ weaver và tính tương thích nhị phân đều phải được xem xét.

## 4. Thiết kế dự án Java 17+

Một dự án minh họa cả hai kỹ thuật, giữ riêng mỗi aspect để dễ quan sát:

```text
aop-control-type-lab/
├── src/
│   └── demo/
│       ├── Main.java
│       ├── flow/
│       │   ├── Inventory.java
│       │   └── OrderService.java
│       ├── structure/
│       │   ├── Billable.java
│       │   └── Invoice.java
│       └── aspect/
│           ├── FlowTrace.aj
│           └── InvoiceExtension.aj
└── tools/
    ├── aspectjtools-1.9.24.jar
    └── aspectjrt-1.9.24.jar
```

Các tệp Java được viết bằng cú pháp Java 17; tệp `.aj` chứa cú pháp AspectJ và được biên dịch bằng `ajc`, **không phải bởi `javac` thuần**. Khi `Main.java` gọi member chỉ được khai báo bằng ITD, cần đưa cả `Main.java`, `Invoice.java` và aspect liên quan vào quy trình compile/weave có nhận thức về ITD.

## 5. Phần A: chọn join point bằng luồng điều khiển

### 5.1. Mã nghiệp vụ tồn kho

Tạo `src/demo/flow/Inventory.java`:

```java
package demo.flow;

public final class Inventory {
    public void read(String sku) {
        System.out.println("INVENTORY:" + sku);
    }
}
```

Lớp này **không biết** điều kiện checkout. Dù được gọi từ đâu, nó luôn in kết quả nghiệp vụ giống nhau. Mục tiêu AOP là bổ sung dòng tracing đúng ngữ cảnh.

### 5.2. Mã nghiệp vụ checkout

Tạo `src/demo/flow/OrderService.java`:

```java
package demo.flow;

public final class OrderService {
    private final Inventory inventory = new Inventory();

    public void checkout() {
        System.out.println("CHECKOUT START");
        inventory.read("inside");
        System.out.println("CHECKOUT END");
    }
}
```

Một lần `checkout()` thực thi, gọi `Inventory.read` trong thời gian checkout chưa trả về. Đây là join point mà trace cần nhận diện.

### 5.3. Aspect theo `cflow`

Tạo `src/demo/aspect/FlowTrace.aj`:

```java
package demo.aspect;

public aspect FlowTrace {
    pointcut duringCheckout():
        cflow(execution(void demo.flow.OrderService.checkout()));

    before():
        execution(void demo.flow.Inventory.read(java.lang.String))
        && duringCheckout() {
        System.out.println("FLOW: inventory read inside checkout");
    }
}
```

Phân tích:

1. `execution(void demo.flow.OrderService.checkout())` xác định **gốc luồng điều khiển**.
2. `cflow(...)` chọn ngữ cảnh runtime bắt nguồn từ execution đó, bao gồm các method được gọi lồng bên dưới trên đường thực thi hiện hành.
3. `execution(void demo.flow.Inventory.read(java.lang.String))` giới hạn đối tượng được advice tới method tồn kho.
4. `&&` yêu cầu **cả hai** điều kiện cùng đúng.
5. `before()` ghi dòng trace trước khi thân `Inventory.read` chạy.

Kết quả: phương thức tồn kho vẫn được gọi bình thường ở cả hai nơi, nhưng **chỉ lời gọi trong checkout** được thêm trace.

Không viết pointcut như `execution(* *(..)) && cflow(...)` trong bài này: biểu thức đó có thể match cả helper, getter hoặc các method không thuộc ý định theo dõi.

### 5.4. Nên thay `cflow` bằng `cflowbelow` khi nào?

Khi cần chọn **mọi join point ở dưới** một execution gốc mà **loại chính execution gốc**, có thể viết:

```java
// Một biểu thức pointcut minh họa trong aspect:
pointcut nestedCheckoutFlow():
    cflowbelow(execution(void demo.flow.OrderService.checkout()));
```

Đây là **mảnh pointcut**, không phải chương trình Java chạy độc lập. Nếu kết hợp với `execution(Inventory.read(String))` của ví dụ, kết quả gọi bên trong checkout vẫn tương tự. Nếu advice nhắm chính `checkout`, hai lựa chọn có thể cho tập join point khác. Hãy kiểm tra ngữ nghĩa trên các đường gọi lồng nhau và lời gọi đệ quy trước khi chọn.

## 6. Phần B: bổ sung hợp đồng cấu trúc kiểu bằng ITD

### 6.1. Interface cần mở rộng

Tạo `src/demo/structure/Billable.java`:

```java
package demo.structure;

public interface Billable {
}
```

Interface ở đây được giữ là **marker interface** để tập trung vào việc thay đổi type relationship. Trong ứng dụng thật, interface có thể định nghĩa các method khác; khi đó lớp sau weaving phải thỏa mọi method bắt buộc hoặc dựa trên các cơ chế hiện thực hợp lệ.

### 6.2. Lớp gốc không biết aspect

Tạo `src/demo/structure/Invoice.java`:

```java
package demo.structure;

public final class Invoice {
    private final int amount;

    public Invoice(int amount) {
        this.amount = amount;
    }

    public int amount() {
        return amount;
    }
}
```

Quan sát trực tiếp source: `Invoice` **không có method `label()`** và **không implements `Billable`**. Điều này là có chủ đích để kiểm chứng phép mở rộng cấu trúc kiểu do aspect thực hiện.

### 6.3. Aspect khai báo thêm interface và method

Tạo `src/demo/aspect/InvoiceExtension.aj`:

```java
package demo.aspect;

public aspect InvoiceExtension {
    declare parents:
        demo.structure.Invoice implements demo.structure.Billable;

    public String demo.structure.Invoice.label() {
        return "INVOICE:" + amount();
    }
}
```

Giải thích:

- `declare parents` tuyên bố quan hệ `Invoice implements Billable` ở thời điểm weaving.
- Cú pháp `public String demo.structure.Invoice.label()` là **inter-type method declaration**: method mới có tên `label()` được khai báo cho kiểu `Invoice`, dù source `Invoice.java` không chứa method này.
- Thân ITD gọi method `amount()` vốn đã là `public`; không cần truy cập trực tiếp vào field `private amount`.
- Hai khai báo làm thay đổi **hợp đồng kiểu** khi compile/weave; chúng không phải advice `before` hoặc `after` và không dựa vào việc có một lời gọi đang nằm trong `cflow`.

Khi chọn cấu trúc kiểu để mở rộng, cần quan tâm API compatibility, xung đột tên member và quan hệ kế thừa. Ví dụ, nếu sau này tác giả `Invoice` tự thêm `label()` với chữ ký trùng nhau, cần giải quyết theo quy tắc của compiler/weaver; không giả định hai method sẽ được giữ độc lập.

### 6.4. Hệ quả cho người dùng class

Với artifact **đã weave**, code có thể gọi `invoice.label()` và dùng `instanceof Billable`. Nhưng nếu một module khác chỉ biên dịch dựa trên bản `Invoice.class` **chưa weave**, compiler Java thuần không thể suy ra ITD từ mã nguồn `Invoice.java`. Do đó, nếu ITD trở thành API công khai, cần tổ chức build để các module sử dụng được **woven classes** hay các signature metadata thích hợp, đồng thời đảm bảo JVM runtime load chính artifact tương ứng.

Đây là khác biệt lớn với advice thuần hành vi: một ITD public có thể ảnh hưởng trực tiếp tới **tính tương thích biên dịch và nhị phân** của consumer.

## 7. Chương trình chính và stdout kỳ vọng

Tạo `src/demo/Main.java`:

```java
package demo;

import demo.flow.Inventory;
import demo.flow.OrderService;
import demo.structure.Billable;
import demo.structure.Invoice;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        OrderService service = new OrderService();
        service.checkout();

        new Inventory().read("outside");

        Invoice invoice = new Invoice(42);
        System.out.println(invoice.label());
        System.out.println("BILLABLE:" + (invoice instanceof Billable));
    }
}
```

Khi compile/weave cùng hai aspect thành công, đầu ra **stdout của ứng dụng** theo thứ tự:

```text
CHECKOUT START
FLOW: inventory read inside checkout
INVENTORY:inside
CHECKOUT END
INVENTORY:outside
INVOICE:42
BILLABLE:true
```

**Giải thích từng phần:**

1. `CHECKOUT START` xuất hiện trước khi checkout gọi Inventory.
2. `FLOW: ...` xuất hiện **một lần** vì `Inventory.read("inside")` đang ở trong dynamic control flow của checkout.
3. `INVENTORY:inside` là nội dung nghiệp vụ gốc; `CHECKOUT END` cho thấy checkout không bị ngắt.
4. `INVENTORY:outside` xuất hiện mà **không có** dòng `FLOW: ...` thứ hai, dù thực thi **cùng method `Inventory.read`**.
5. `INVOICE:42` chứng minh method `label()` do ITD thêm đã được gọi.
6. `BILLABLE:true` chứng minh quan hệ interface được đưa vào type khi weaving.

Hai dòng cuối **không có nghĩa** `Invoice` được proxy bao quanh hoặc call stack đang thuộc checkout; đó là kết quả của biến đổi cấu trúc lớp.

## 8. Biên dịch/weave và chạy với Java 17+

Ví dụ lệnh dùng terminal **POSIX/bash** trong thư mục `aop-control-type-lab`. Cần JDK 17+ và AspectJ compiler hỗ trợ đích Java 17. Ví dụ này chọn **AspectJ 1.9.24** và dùng cùng phiên bản cho compiler và runtime.

### 8.1. Chuẩn bị công cụ

```bash
mkdir -p tools build/woven

curl -fLsS \
  https://repo.maven.apache.org/maven2/org/aspectj/aspectjtools/1.9.24/aspectjtools-1.9.24.jar \
  -o tools/aspectjtools-1.9.24.jar

curl -fLsS \
  https://repo.maven.apache.org/maven2/org/aspectj/aspectjrt/1.9.24/aspectjrt-1.9.24.jar \
  -o tools/aspectjrt-1.9.24.jar
```

**Lưu ý build sạch:** các lệnh bên dưới giả định `build/woven` chưa chứa bytecode từ lần build cũ. Trong pipeline, cần dọn output **của chính lab** trước lần compile độc lập để tránh class cũ đi vào kết quả; không cần đụng đến dự án khác.

### 8.2. Compile-time weaving một lượt

```bash
java -cp tools/aspectjtools-1.9.24.jar \
  org.aspectj.tools.ajc.Main \
  -17 -encoding UTF-8 \
  -classpath tools/aspectjrt-1.9.24.jar \
  -showWeaveInfo \
  -d build/woven \
  src/demo/flow/Inventory.java \
  src/demo/flow/OrderService.java \
  src/demo/structure/Billable.java \
  src/demo/structure/Invoice.java \
  src/demo/aspect/FlowTrace.aj \
  src/demo/aspect/InvoiceExtension.aj \
  src/demo/Main.java
```

Lệnh `java -cp ... org.aspectj.tools.ajc.Main` chạy AspectJ compiler. `-17` chọn target/source Java 17, `-d` chỉ định output chứa **woven class**, `-showWeaveInfo` yêu cầu thông tin các join point và khai báo áp dụng. Định dạng và số dòng weave report có thể khác theo compiler/version, vì vậy không dựa vào một bản ghi log viết cứng để kết luận.

Compiler cần nhìn thấy cả khai báo `InvoiceExtension.aj` khi biên dịch `Main.java` sử dụng `Invoice.label()`. Chạy riêng `javac src/demo/Main.java` trên source chưa weave là một phép thử **không tương đương** và dự kiến không thể phân giải method mới.

### 8.3. Chạy chương trình

```bash
java -cp "build/woven:tools/aspectjrt-1.9.24.jar" demo.Main
```

Stdout phải khớp mục 7. Trên Windows, thay dấu `:` phân cách classpath bằng dấu `;` theo quy tắc của JVM trên nền tảng đó.

### 8.4. Đóng gói và kiểm tra chính artifact đã dệt

```bash
jar --create \
  --file build/control-type-woven.jar \
  -C build/woven .

jar --list --file build/control-type-woven.jar

javap -classpath build/control-type-woven.jar \
  -p demo.structure.Invoice
```

Các kiểm tra cần thực hiện:

- JAR có `demo/flow/Inventory.class`, `demo/flow/OrderService.class`, `demo/structure/Invoice.class`, `demo/structure/Billable.class`, `demo/Main.class` và class của aspect cần thiết.
- `javap -p` trên `Invoice` sau weaving cho thấy method `label()` và quan hệ implements `Billable` phù hợp với kết quả biên dịch (cách in signature có thể khác).
- Kiểm tra bytecode `Inventory.read` cũng có thể xác định thêm logic advice đã được weave; với pointcut động, compiler có thể sinh helper/test runtime. Tên helper là chi tiết thực thi, không nên dùng làm hợp đồng dài hạn.
- Cần chạy lại **chính JAR cuối** để phát hiện trường hợp package nhầm class chưa weave:

```bash
java -cp "build/control-type-woven.jar:tools/aspectjrt-1.9.24.jar" \
  demo.Main
```

Output phải giống mục 7. JAR ví dụ **không nhúng** thư viện `aspectjrt`: khi triển khai phải phân phối dependency phù hợp hoặc xây package tự chứa đúng cách.

## 9. Kiểm thử dương và âm cho `cflow`

### 9.1. Cặp kiểm thử quan trọng nhất

- **Positive case:** `Inventory.read("inside")` chạy trong checkout → có một dòng `FLOW`.
- **Negative case:** `Inventory.read("outside")` chạy ngoài checkout → không có dòng `FLOW`.

Nếu chỉ kiểm tra “chương trình có in FLOW” thì pointcut `execution(void Inventory.read(String))` thiếu `cflow` cũng có thể pass. Phải kiểm tra **lượt nào** bị chặn, không chỉ tổng số ít nhất một.

Đoạn shell kiểm thử output của bản chạy độc lập:

```bash
actual=$(java -cp \
  "build/control-type-woven.jar:tools/aspectjrt-1.9.24.jar" \
  demo.Main)

expected=$(printf '%s\n' \
  'CHECKOUT START' \
  'FLOW: inventory read inside checkout' \
  'INVENTORY:inside' \
  'CHECKOUT END' \
  'INVENTORY:outside' \
  'INVOICE:42' \
  'BILLABLE:true')

test "$actual" = "$expected"
```

Lệnh `test` trả mã khác `0` nếu mất advice, log thêm ở lời gọi ngoài checkout, hoặc phần ITD không tạo đúng output. Đây là test cho **chương trình đơn luồng có stdout xác định**. Trong production, log của nhiều thread có thể xen kẽ: nên assert dựa trên event/correlation ID theo request thay vì giả định một thứ tự log global.

### 9.2. Các biến thể mở rộng phạm vi

**Biến thể 1 – checkout gọi qua helper.** Thêm helper đồng bộ được gọi từ checkout, rồi helper gọi `Inventory.read`. Method read vẫn thuộc dynamic control flow của checkout và cần có trace. Điều này chứng minh `cflow` không chỉ match caller trực tiếp.

**Biến thể 2 – chuyển sang thread khác.** Nếu checkout khởi tạo công việc bất đồng bộ chạy sau/ở một thread khác, không tự mặc định dynamic control flow của checkout sẽ được truyền qua thread đó. Trong AspectJ thông thường, `cflow` dựa trên luồng thực thi động của join point hiện tại và không có chức năng tự mang business context qua mọi ranh giới bất đồng bộ. Muốn trace xuyên thread/task, cần cơ chế truyền context rõ ràng được thiết kế và test riêng.

**Biến thể 3 – gọi lồng/đệ quy.** Khi checkout được gọi lồng hoặc đệ quy, phạm vi `cflow` có thể bao gồm nhiều mức hoạt động; giải thích xem một lần read có thuộc các phạm vi đang hoạt động hay không. Đừng giả định một lời gọi read luôn chỉ có một gốc checkout.

**Biến thể 4 – thêm điều kiện động.** Nếu chỉ trace một SKU cụ thể, kết hợp context/argument binding theo capability công cụ. Cần đánh giá thêm chi phí kiểm tra runtime và chú ý `null`, kiểu runtime của đối số.

### 9.3. Chi phí runtime của `cflow`

Một điều kiện chữ ký/type thuần thường có thể lọc nhiều join point khi weave. `cflow` phải duy trì/kiểm tra trạng thái phạm vi luồng điều khiển khi chương trình chạy; mã phụ trợ và chi phí cụ thể phụ thuộc weaver. Trong đường gọi cực nóng, trace mọi lần read có thể gây allocation, log I/O và latency đáng kể. Đo với workload đại diện và giới hạn pointcut tĩnh càng rõ càng tốt; không đưa `cflow` lên tất cả execution trong toàn ứng dụng nếu không cần.

## 10. Kiểm thử cấu trúc kiểu sau ITD

### 10.1. Tại sao test `instanceof` là cần thiết?

Nếu chỉ kiểm tra chuỗi `INVOICE:42`, về mặt thiết kế có thể đạt kết quả đó bằng một utility method hoặc formatter ngoài lớp, chứ chưa chứng minh `Invoice` thực sự được mở rộng. Bài này cần test **hai dấu hiệu độc lập**:

1. Một instance `Invoice` gọi được method `label()` trực tiếp sau compile/weave.
2. Instance đó thỏa quan hệ `instanceof Billable` vì `declare parents` đã được áp dụng.

`javap` bổ sung chứng cứ rằng thay đổi hiện diện trong classfile, nhưng hành vi runtime vẫn cần kiểm chứng.

### 10.2. Các kiểm tra phản chứng

Thử nghiệm mang tính học tập, thực hiện trên các build riêng:

- **Bỏ ITD method `label()`:** source `Main.java` đang gọi method này phải không còn build được nếu không có thay đổi tương đương khác; điều này xác nhận consumer phụ thuộc vào cấu trúc do weaving bổ sung.
- **Bỏ `declare parents`:** method `label()` có thể vẫn tồn tại nhưng quan hệ implements `Billable` không còn đáp ứng hợp đồng; compile của đoạn `instanceof` có thể thay đổi tùy type checker và cách tổ chức build, vì vậy kiểm tra trên class đã weave thay vì dự đoán máy móc một lỗi compiler cụ thể.
- **Compile consumer trên class chưa weave:** Java compiler sẽ không tự nhận biết các member chỉ xuất hiện trong `.aj`. Cần đúng output và thứ tự build.
- **Thêm method trùng chữ ký vào source `Invoice`:** yêu cầu xử lý xung đột/đặc tả weaver; không được tự động “merge” hai cách hiện thực.

### 10.3. Khả năng bảo trì

ITD có thể giúp bổ sung một concern về hợp đồng kiểu mà không phải sửa nhiều lớp nguồn. Tuy nhiên, nó làm API được nhìn thấy ở runtime **khác** với API thuần đọc từ source type ban đầu. Vì vậy:

- Ghi tài liệu cho public member được đưa vào qua aspect.
- Đảm bảo IDE, compiler, test và consumer dùng đúng build metadata/classpath.
- Không mở rộng cấu trúc lớp của bên thứ ba một cách thiếu kiểm soát.
- Kiểm tra binary compatibility và xung đột tên qua các lần nâng phiên bản dependency.
- Tránh dùng ITD để che giấu quan hệ kiểu quá quan trọng đến mức người đọc code không tìm ra nguyên nhân.

## 11. Phân biệt `cflow` với các khái niệm dễ nhầm

| Khái niệm | Điều kiện chính | Sai lầm thường gặp |
|---|---|---|
| `execution(P)` | Method/constructor execution hoặc loại join point tương ứng khớp signature/type | Tưởng rằng nó tự biết caller hoặc business request hiện tại |
| `call(P)` | Call site phù hợp | Nhầm với execution trên target khi caller không được weave |
| `within(TypePattern)` | Lexical type chứa join point | Nghĩ rằng package/type đồng nghĩa với call stack nghiệp vụ |
| `withincode(Signature)` | Lexical code chứa join point | Nhầm với tất cả lời gọi lồng nhau lúc runtime |
| `cflow(P)` | Dynamic control flow bắt nguồn từ P, gồm join point gốc | Cho rằng chỉ xét caller trực tiếp |
| `cflowbelow(P)` | Dynamic control flow **dưới** P, trừ gốc | Cho rằng loại mọi join point bên trong method gốc |
| `args(...)` | Điều kiện/binding theo đối số runtime khi được hỗ trợ | Đồng nhất với signature tham số khai báo |
| **ITD** | Thêm member/hợp đồng kiểu ở weaving | Gọi nó là một dynamic pointcut |

Đặc biệt, `within(OrderService)` có thể chọn các join point nằm **trong mã của OrderService**. Nó không tự chọn một `Inventory.read` được thực thi trong lớp `Inventory` chỉ vì method đó được gọi từ `OrderService.checkout`. Chính `cflow` diễn đạt ngữ cảnh call stack mà ví dụ yêu cầu.

## 12. Những lỗi thường gặp trong bài tập

**Lỗi 1 – bỏ điều kiện execution của Inventory.** Viết advice trên `cflow(checkout)` đơn thuần có thể áp dụng cho hàng loạt join point trong đường checkout. **Sửa:** kết hợp `execution(Inventory.read) && cflow(checkout)`.

**Lỗi 2 – dùng `within(OrderService)` thay `cflow(checkout)`.** `within` là điều kiện lexical chứ không mô tả toàn bộ call stack. **Sửa:** xác định mục tiêu theo ngữ cảnh runtime và dùng designator phù hợp.

**Lỗi 3 – tin rằng `cflowbelow` không match lời gọi read ngay trong checkout.** `cflowbelow` chỉ loại join point gốc, không loại các execution nằm dưới. **Sửa:** vẽ cây lời gọi và xác định gốc pointcut.

**Lỗi 4 – dùng lời gọi bất đồng bộ rồi đợi `cflow` tự theo context.** Một task/thread khác không nhất thiết nằm trong dynamic control flow của thread gốc. **Sửa:** thiết kế context propagation riêng và test thời điểm task thực thi.

**Lỗi 5 – cho rằng ITD chỉ là phương thức tiện ích.** Utility bên ngoài không thêm thật method/interface vào `Invoice`. **Sửa:** kiểm tra `Invoice.label()` và `instanceof Billable` trên woven class.

**Lỗi 6 – biên dịch `Main.java` bằng `javac` với class gốc và mong thấy `label()`.** Compiler Java thuần chỉ thấy API gốc. **Sửa:** dùng AspectJ-aware compilation hoặc đưa woven artifact phù hợp lên compile classpath.

**Lỗi 7 – đóng gói nhầm class chưa weave.** Build log có thể đúng nhưng runtime không có advice/ITD vì chạy JAR plain. **Sửa:** dùng thư mục đầu ra weaving cho packaging và test chính JAR đó.

**Lỗi 8 – bỏ dependency runtime.** Woven bytecode có thể cần lớp trong `aspectjrt` hoặc helper tương ứng. **Sửa:** phân phối và kiểm tra dependency bằng lệnh chạy thực.

**Lỗi 9 – không kiểm tra trường hợp bên ngoài checkout.** Advice có thể match quá rộng nhưng test vẫn pass vì chỉ thấy một dòng FLOW. **Sửa:** thêm negative case phải chắc chắn không có trace.

**Lỗi 10 – kỳ vọng weaving tái định nghĩa mọi quan hệ kiểu trong JVM đang chạy.** Structural introduction chịu giới hạn compiler/weaver và thời điểm class được định nghĩa. **Sửa:** chọn chiến lược weaving đáp ứng khả năng cấu trúc cần thiết và kiểm tra bytecode/runtime.

## 13. Bài tập mở rộng

### Bài A – dùng `cflowbelow` và giải thích khác biệt

Viết pointcut `insideCheckoutBelow()` dùng `cflowbelow(execution(void OrderService.checkout()))`. Thực hiện hai phép quan sát:

- Advice cho **chính execution `checkout`**: điều gì khác giữa `cflow` và `cflowbelow`?
- Advice cho **execution `Inventory.read` bên dưới checkout**: điều gì giữ nguyên?

Lời giải đạt yêu cầu khi học viên giải thích đúng phạm vi join point gốc và lớp con, không chỉ nêu một giá trị boolean.

### Bài B – thêm một helper lồng nhau

Đưa `Inventory.read("inside")` vào `OrderHelper.load` do `checkout` gọi. `FlowTrace` không cần biết tên helper nhưng vẫn phải chọn read khi thuộc checkout. Thêm negative test gọi `OrderHelper.load` từ bên ngoài checkout để xác nhận không bị trace. Tình huống này chỉ ra ưu thế của **ngữ cảnh động** so với match theo tên caller cụ thể.

### Bài C – thêm interface có phương thức

Đổi `Billable` từ marker thành hợp đồng:

```java
package demo.structure;

public interface Billable {
    String label();
}
```

Giữ ITD method `Invoice.label()` và `declare parents`. Biên dịch lại và kiểm tra một biến `Billable billable = invoice;` có thể gọi `billable.label()` sau weaving. Nếu hợp đồng interface có thêm method chưa được cung cấp, compiler/weaver có thể báo lỗi; học viên phải chứng minh lớp thỏa đầy đủ interface.

### Bài D – kiểm soát thời gian và logging

Thêm advice đo thời lượng trên một join point xác định, dùng phép đo đúng cho benchmark (warmup, nhiều vòng đo, workload đại diện) và không tạo log khối lượng lớn trong vòng lặp nóng. Đo cả phần overhead của cflow và I/O logging; không lấy một phép `System.nanoTime()` duy nhất làm bằng chứng tốc độ.

### Bài E – tổ chức build nhiều module

Module A tạo woven `Invoice.class`; module B gọi `label()`. Thử compile module B lần lượt với **plain JAR** và **woven JAR**. Giải thích sự khác biệt compile-time visibility và runtime classpath. Sau đó xem xét trường hợp tồn tại hai bản `Invoice.class` trên classpath; xác định loader đang dùng bản nào.

## 14. Tiêu chí chấm và checklist

| Hạng mục | Trọng số | Bằng chứng |
|---|---:|---|
| Hiểu `cflow` / `cflowbelow` | 20% | Giải thích call stack và join point gốc chính xác |
| Pointcut chọn đúng | 20% | Trace đúng `read("inside")`, không trace `read("outside")` |
| ITD đúng cấu trúc | 20% | `Invoice.label()` và `instanceof Billable` hoạt động |
| Build/weaving | 15% | JDK 17+, ajc, weave info và artifact cuối có thể chạy |
| Kiểm thử dương/âm | 15% | Có assertion cho cả trường hợp đúng và bị loại |
| Giải thích rủi ro | 10% | Đề cập threading, runtime overhead, classpath, xung đột ITD |

Trước khi hoàn thành, kiểm tra:

- [ ] Phân biệt được **dynamic control flow pointcut** với **static type introduction**.
- [ ] Code `Main` chạy bằng compiler/weaver phù hợp và dùng đúng woven class.
- [ ] `Inventory.read` có một lời gọi trong checkout và một lời gọi ngoài checkout.
- [ ] Dòng `FLOW` chỉ xuất hiện một lần, ở đúng vị trí trước `INVENTORY:inside`.
- [ ] Không in dòng FLOW bổ sung trước `INVENTORY:outside`.
- [ ] Method `label()` không tồn tại trong mã nguồn `Invoice` nhưng tồn tại ở woven artifact.
- [ ] Quan hệ implements `Billable` có thật và kiểm tra runtime cho kết quả `true`.
- [ ] Cả stdout, weave report, disassembly và artifact cuối được xem xét.
- [ ] Không giả định điều kiện cflow tự truyền xuyên mọi thread/task.
- [ ] Đã xem xét ảnh hưởng API compatibility và phụ thuộc khi dùng ITD.

**Kết quả cần ghi nhớ:** `cflow` cho phép mô tả **“một hành vi đang xảy ra trong ngữ cảnh thực thi nào”**; ITD cho phép mô tả **“một kiểu có thêm hợp đồng/member nào sau weaving”**. Chúng kết hợp được với AOP nhưng cần kiểm thử ở những lớp khác nhau: **đường gọi runtime** cho pointcut động và **cấu trúc classfile/API** cho inter-type declaration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-009">Thử thay đổi cấu trúc mã để phát hiện pointcut mong manh</a>

<details>
<summary>Xem chi tiết</summary>

# Bài tập tổng hợp AOP: Thử thay đổi cấu trúc mã để phát hiện pointcut mong manh

## 1. Mục tiêu học tập

Một pointcut có thể hoạt động đúng hôm nay nhưng **âm thầm đổi tập join point được chọn** khi mã nghiệp vụ được refactor. Đổi tên một phương thức, chuyển lớp sang package khác, bổ sung overload, tách helper hoặc đổi từ lời gọi trực tiếp sang callback có thể làm advice biến mất hoặc xuất hiện tại nơi không mong muốn. Điều nguy hiểm nhất là nhiều thay đổi vẫn biên dịch, test nghiệp vụ vẫn qua, nhưng quy tắc AOP không còn đúng.

Bài tập này yêu cầu **thay đổi cấu trúc mã có chủ đích**, quan sát tập join point trước/sau rồi xác định một pointcut có **mong manh (fragile)** hay không. Mục tiêu không phải chứng minh một kiểu pointcut luôn tốt hơn; mục tiêu là học cách viết **hợp đồng chọn join point**, kiểm thử các trường hợp dương/âm và biết khi nào việc refactor cần thay đổi aspect.

**Hoàn thành bài khi có thể:**

1. Giải thích vì sao cùng một pointcut chọn khác đi khi tên, package, method signature, annotation, hierarchy hoặc call graph thay đổi.
2. Đặt được kỳ vọng **match / không match** trước khi chạy test và so với các join point thực tế.
3. Tái hiện được ít nhất một lỗi **false negative** (bỏ sót nơi phải chặn) và một lỗi **false positive** (chặn thêm nơi không nên chặn).
4. Đề xuất pointcut ổn định hơn dựa vào **ngữ nghĩa/hợp đồng**, nếu bài toán cho phép.
5. Phân biệt lỗi do matcher với lỗi **không có interception/weaving** tại điểm gọi.
6. Chạy được ví dụ Java 17 mô phỏng AOP dựa trên proxy và hiểu giới hạn so với AOP dệt bytecode.
7. Lập ma trận regression test và đánh giá tác động nghiệp vụ khi pointcut lệch.

## 2. Pointcut mong manh là gì?

Trong Aspect-Oriented Programming, **join point** là một vị trí/sự kiện có thể bị tác động (ví dụ thực thi method, lời gọi method, khởi tạo hoặc truy cập trường tùy engine); **pointcut** mô tả tập sự kiện được chọn; **advice** là hành vi được thực thi khi chọn được join point đó. **Weaving hoặc interception** xác định các nơi có thể tham gia AOP.

Ví dụ giả định một aspect đo thời gian đang chọn:

```java
// Pointcut kiểu AspectJ, chỉ minh họa cú pháp:
execution(* demo.billing.InvoiceService.submit(..))
```

Nó phụ thuộc vào **tên type**, **package** và **method**. Nếu phương thức được đổi thành `placeOrder` nhưng hành vi nghiệp vụ vẫn là “gửi đơn”, biểu thức trên có thể không khớp nữa. Compiler Java thông thường không nhất thiết báo lỗi vì pointcut chỉ là một mẫu chọn được kiểm tra trong quá trình AOP.

Ngược lại, một pointcut quá rộng:

```java
execution(* demo.billing..*.*(..))
```

có thể bắt cả class mới chứa helper logging hoặc client network vừa được chuyển vào `demo.billing`. Khi advice cũng gọi helper đó, hệ thống có nguy cơ log lặp, tăng overhead hoặc recursion.

**Định nghĩa thực dụng:** pointcut mong manh khi một thay đổi mã **không làm đổi yêu cầu cắt ngang** nhưng lại khiến tập join point phù hợp thay đổi ngoài ý muốn. Không phải thay đổi match nào cũng sai: nếu requirement cố tình gắn với tên hoặc package, khi đổi tên/package thì yêu cầu cũng có thể cần cập nhật.

## 3. Thiết lập hợp đồng chọn trước khi refactor

Trước khi sửa mã, trả lời bằng văn bản:

- **Ý nghĩa nghiệp vụ:** cần chặn thao tác nào? Ví dụ “mọi lệnh đặt hàng” hay “mọi method trong package adapters”?
- **Loại sự kiện:** `call` ở caller, `execution` ở callee, method interception tại proxy, hay loại join point khác?
- **Đơn vị chọn:** khai báo trên method, annotation, interface, hierarchy, package, signature, hay runtime args?
- **Độ sâu:** có bắt lời gọi con, self-invocation, recursion, callback, async completion không?
- **Điều kiện loại trừ:** những helper, serializer, logger, health check nào bắt buộc phải không match?
- **Rủi ro khi lệch:** mất metric, bỏ kiểm tra quyền, trùng audit, nhân đôi side effect, thay exception hoặc latency tăng?

Đặt một **oracle** cho mỗi test: method/sự kiện nào **phải** có advice và method/sự kiện nào **không được** có. Không xây oracle bằng cách sao chép y hệt biểu thức pointcut vào test, vì khi sai cùng một giả định, test có thể cũng sai.

### 3.1. Hai loại sai lệch

| Loại | Định nghĩa | Ví dụ | Tác động |
| --- | --- | --- | --- |
| False negative | Join point thuộc yêu cầu nhưng không được chọn | Đổi `submit()` thành `send()`, mẫu vẫn bắt `submit` | Bỏ log/audit/guard cần thiết |
| False positive | Join point ngoài yêu cầu nhưng bị chọn | Thêm `submitDebug()` vào package và dùng wildcard `submit*` | Chạy advice thừa, overhead, side effect hoặc recursion |
| Mất interception | Matcher có thể đúng nhưng đường gọi không qua proxy/weaving | Target tự gọi `this.send()` trong delegation proxy | Advice không có cơ hội chạy |
| Sai event kind | Matcher gắn vào loại join point khác dự kiến | Chỉ weave `call` ở một số caller, còn code chuyển sang caller khác | Một số sự kiện không được quan sát |
| Match đúng, advice sai | Pointcut chọn đúng nhưng advice không giữ contract | `around` gọi `proceed` hai lần sau refactor retry | Side effect bị nhân đôi |

Bài tập tập trung vào hai hàng đầu, nhưng khi điều tra phải loại trừ ba hàng còn lại để không sửa nhầm nguyên nhân.

## 4. Ma trận thay đổi cấu trúc mã

Đây là tập thí nghiệm nên thực hiện **mỗi lần một thay đổi**, sau đó đối chiếu tập match với bản gốc.

| Thay đổi mã | Pointcut dựa trên tên/package/signature có thể bị gì? | Điều cần quan sát |
| --- | --- | --- |
| Đổi `submit` thành `place` | Bỏ sót method cũ hoặc match vào method khác cùng tên | Có còn advice tại lệnh đặt hàng không? |
| Chuyển `InvoiceService` sang package mới | Mẫu package cũ không khớp | Weaving có xử lý class mới không? |
| Thêm overload `submit(String,int)` | Wildcard `submit(..)` bắt thêm overload | Overload mới thuộc hay không thuộc contract? |
| Thay chữ ký tham số/kiểu trả về | Mẫu signature quá chặt bỏ sót | Compile warning, số join point khớp, giá trị trả về |
| Tách `submit` thành helper hoặc delegate | `execution` bắt thêm tầng, `call` đổi call site | Advice chạy một lần hay nhiều lần? |
| Đổi class sang interface/implementation khác | Mẫu type hoặc annotation lookup đổi | Method nào thực sự được chọn? |
| Đổi tên annotation hoặc chuyển nơi khai báo | `@annotation` và metadata lookup có thể bỏ sót | Annotation có nằm trên join point được engine kiểm tra không? |
| Thêm class/helper vào package đã match rộng | False positive, kể cả logging helper | Có phát sinh advice đệ quy hay metric nhiễu không? |
| Chuyển caller sang callback/lambda | `call` site có thể đổi hoặc proxy boundary bị bỏ qua | Caller mới đã được weave/chặn chưa? |
| Đổi method modifier/visibility | Khả năng interception của proxy có thể khác | Method còn đi qua điểm chặn được hỗ trợ không? |

Lưu ý: sự tác động thực tế tùy **cơ chế AOP**. Với weaving tại `execution`, một lời gọi nội bộ vẫn có thể gặp advice nếu method đích đã được weave. Với **proxy ủy quyền**, lời gọi `this.method()` trong target thường không quay lại proxy. Không dùng một bảng refactor để thay thế việc kiểm tra engine đang chạy.

## 5. Thí nghiệm với pointcut dựa trên tên

### 5.1. Bản gốc

Giả sử mã nghiệp vụ trước khi refactor:

```java
package demo.orders;

public class OrderOperations {
    public String submit(String code) {
        return "accepted:" + code;
    }
}
```

Một pointcut minh họa theo AspectJ:

```java
execution(String demo.orders.OrderOperations.submit(String))
```

Kỳ vọng: chỉ `OrderOperations.submit(String)` được advice. Dấu vết nên cho thấy một match khi gọi đúng phương thức này, không match với `health`, `validate` hay method khác.

### 5.2. Refactor tên nhưng không đổi nghiệp vụ

Đổi `submit` thành `confirmOrder`; cập nhật tất cả caller để chương trình vẫn chạy. Nếu **quên sửa pointcut**, advice sẽ không bám vào method mới dù nghiệp vụ vẫn xử lý đúng.

```java
package demo.orders;

public class OrderOperations {
    public String confirmOrder(String code) {
        return "accepted:" + code;
    }
}
```

Dấu hiệu kiểm chứng:

```text
Before: business call=submit        expectedMatch=true  actualMatch=true
After:  business call=confirmOrder  expectedMatch=true  actualMatch=false
```

Đây là **dấu vết kỳ vọng minh họa**, không phải stdout của mã độc lập. Thay đổi đúng nên là cập nhật pointcut theo hợp đồng mới, hoặc chọn một cách đánh dấu ngữ nghĩa đáng tin hơn.

### 5.3. Nới wildcard quá rộng

Một phản ứng sai thường gặp là đổi thành:

```java
execution(* demo.orders.OrderOperations.*(..))
```

Nó “sửa” trường hợp thiếu match nhưng có thể bắt cả `health()`, `validate()` và những helper tương lai. Vì thế sau mỗi lần mở rộng, phải chạy **negative tests**. Nới wildcard chỉ hợp lý khi requirement thực sự áp dụng cho mọi method trong phạm vi đó.

## 6. Thí nghiệm Java 17 chạy độc lập: tên phương thức so với hợp đồng đánh dấu

Chương trình sau sử dụng **JDK dynamic proxy** để mô phỏng một AOP interceptor. Nó so sánh:

- Một điều kiện **mong manh** dựa vào tên method: `method.getName().equals("submit")`.
- Một điều kiện **ổn định theo hợp đồng**: interface method có annotation `@Observed`.

Trong thí nghiệm, `@Observed` được đặt **trên interface method** và `InvocationHandler` của JDK proxy nhận chính đối tượng `Method` đại diện cho interface method đó. Ví dụ này **không khẳng định** mọi công cụ weaving đều nhìn annotation trên interface giống JDK proxy; khi dùng weaving tại implementation execution phải khai báo và kiểm tra annotation tại method mà engine thực sự quan sát.

Lưu mã vào **`FragilePointcutDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class FragilePointcutDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {}

    interface LegacyOrders {
        @Observed String submit(String orderId);
        String submit(String orderId, int attempts);
    }

    interface RefactoredOrders {
        @Observed String confirmOrder(String orderId);
    }

    static final class LegacyService implements LegacyOrders {
        @Override
        public String submit(String orderId) {
            return "accepted:" + orderId;
        }

        @Override
        public String submit(String orderId, int attempts) {
            return "accepted:" + orderId + ":attempts=" + attempts;
        }
    }

    static final class RefactoredService implements RefactoredOrders {
        @Override
        public String confirmOrder(String orderId) {
            return "accepted:" + orderId;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T trace(Class<T> contract, T target) {
        return (T) Proxy.newProxyInstance(
            contract.getClassLoader(),
            new Class<?>[] {contract},
            (proxy, method, args) -> {
                boolean byName = method.getName().equals("submit");
                boolean byContract = method.isAnnotationPresent(Observed.class);
                System.out.println("pointcut method=" + method.getName()
                        + " name=" + byName
                        + " marker=" + byContract);
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
            }
        );
    }

    public static void main(String[] args) {
        LegacyOrders original = trace(LegacyOrders.class, new LegacyService());
        RefactoredOrders renamed =
                trace(RefactoredOrders.class, new RefactoredService());

        System.out.println("result=" + original.submit("A1"));
        System.out.println("result=" + original.submit("A2", 2));
        System.out.println("result=" + renamed.confirmOrder("A3"));
    }
}
```

Lệnh chạy với JDK 17 trở lên:

```bash
javac FragilePointcutDemo.java
java FragilePointcutDemo
```

**Standard output chính xác:**

```text
pointcut method=submit name=true marker=true
result=accepted:A1
pointcut method=submit name=true marker=false
result=accepted:A2:attempts=2
pointcut method=confirmOrder name=false marker=true
result=accepted:A3
```

Phân tích:

1. **Bản gốc `submit(String)`:** cả điều kiện tên và annotation đều khớp; đây là positive control.
2. **Overload `submit(String,int)`:** điều kiện tên trả `true` dù overload không được đánh dấu `@Observed`. Nếu requirement chỉ áp dụng cho method được đánh dấu, đây là **false positive** do biểu thức name-based thiếu chính xác.
3. **Refactor `confirmOrder(String)`:** điều kiện tên trả `false` dù method được đánh dấu và vẫn thực hiện nghiệp vụ đặt hàng. Đây là **false negative** do đổi tên.
4. Matcher theo `@Observed` vẫn chọn đúng hai method được khai báo là cần theo dõi trong **mô hình JDK proxy này**; annotation thể hiện rõ ý định nghiệp vụ hơn tên method tình cờ.
5. Mã này chỉ **in kết quả matcher**, không tự thực thi một advice timing hay audit. Trong một engine AOP hoàn chỉnh, kết quả match sẽ được dùng để quyết định advice nào được chạy; thí nghiệm cố tình tách phép chọn khỏi side effect để dễ chẩn đoán.

**Giới hạn bắt buộc nhớ:** JDK dynamic proxy chỉ chặn lời gọi đi qua proxy của các interface tương ứng. Nó không cung cấp mọi loại join point của một bytecode weaver. Nếu annotation tồn tại **chỉ trên implementation method**, `method.isAnnotationPresent` tại `Method` interface trong ví dụ sẽ cho kết quả khác. Do đó việc chuyển từ tên sang annotation không tự động làm mọi pointcut an toàn; **vị trí annotation và semantics lookup** phải được kiểm thử.

## 7. Pointcut dựa vào annotation: bền hơn theo điều kiện nào?

Khi chính sách AOP gắn với một **khả năng/nghiệp vụ có chủ đích**, marker annotation hoặc một interface hợp đồng có thể ít phụ thuộc hơn vào việc đổi tên/package. Ví dụ ký pháp AspectJ sau chọn execution của **method thực sự mang annotation** `@Measured` trên vị trí mà weaver xét:

```java
// Mảnh cú pháp pointcut cho engine hỗ trợ @annotation:
execution(* demo..*.*(..)) && @annotation(demo.Measured)
```

Đoạn trên chỉ là **mẫu pointcut**, chưa phải một file Java chạy độc lập. Trong triển khai hoàn chỉnh cần khai báo `demo.Measured` với retention/target thích hợp theo engine, biên dịch aspect và bảo đảm các class mục tiêu thuộc vùng được weave.

Điều cần kiểm chứng khi dùng annotation:

- Annotation ở **method declaration của implementation**, interface, inherited method hay class? `@annotation` phụ thuộc sự kiện và metadata mà engine thực sự thấy.
- Annotation có được giữ ở bytecode/runtime theo nhu cầu matcher không? Java reflection runtime đòi hỏi `RetentionPolicy.RUNTIME`; bytecode weaver có thể có yêu cầu khác.
- Refactor sang method mới có mang lại annotation hoặc contract tương ứng không? Nếu developer quên sao chép annotation, matcher vẫn bỏ sót.
- Overload cần chính sách riêng hay tất cả overload đều được chọn? Đánh dấu cụ thể từng method giúp tránh wildcard quá rộng nhưng cần bảo đảm không quên method mới.
- Có cấm annotation lạm dụng ở helper hoặc method không thuộc nghiệp vụ không? Negative tests vẫn cần thiết.
- Annotation có được áp dụng đúng module/package và classloader mà weaver/agent xử lý không? Pointcut đúng nhưng không weave cũng không có advice.

Marker tốt nhất thường là **một phần của hợp đồng nghiệp vụ đã được review**, không phải nhãn trang trí. Với một yêu cầu thật sự định nghĩa theo vị trí kiến trúc (ví dụ mọi method của tầng persistence), pointcut theo type/package có thể phù hợp hơn annotation; chọn cách biểu đạt sát ý nghĩa yêu cầu.

## 8. Đổi package, type và hierarchy

### 8.1. Đổi package

Ví dụ pointcut:

```java
execution(* demo.orders..*.*(..))
```

Sau khi chuyển `demo.orders.OrderService` sang `demo.checkout.OrderService`, pointcut có thể bỏ sót. Mặt khác, chuyển một helper log vào `demo.orders` có thể khiến nó bị chọn ngoài dự kiến.

**Thực nghiệm:** tạo một bảng danh sách fully-qualified method signature trước và sau. Kiểm tra cả đường **include** và đường **exclude**: “method quan trọng còn được bắt không?” và “helper mới có bị bắt sai không?”. Nếu phạm vi package là hợp đồng kiến trúc có chủ ý, việc chuyển package cũng phải bao gồm thay đổi policy và test tương ứng.

### 8.2. Tách interface/implementation

Khi chuyển một method từ class cụ thể sang interface hoặc đổi implementation, biểu thức `execution(* demo.orders.OrderService.submit(..))` có thể không còn đại diện đúng cho method thực thi. Tùy ký pháp matching/subtype pattern và engine, việc chọn theo interface hợp đồng có thể bền hơn. Tuy nhiên **không mặc định** annotation khai báo trên interface được nhìn thấy tại implementation execution; đặc biệt annotation trên method không tự kế thừa theo cùng quy tắc `@Inherited` dành cho loại annotation trên class.

**Thực nghiệm:** đặt marker lần lượt trên interface method, implementation method và cả hai; chạy matcher tại join point thực. Ghi kết quả của từng trường hợp, không phán đoán từ Java reflection ở vị trí khác.

### 8.3. Overload, generic và bridge method

Giả sử thêm `submit(String,int)`, `submit(Order)` hoặc dùng generic interface với erased signature. Mẫu `submit(..)` có thể bắt thêm overload mới; mẫu `submit(String)` có thể không bắt overload mới; compiler có thể sinh bridge method ở một số cấu trúc kế thừa generics.

**Thực nghiệm:** liệt kê đầy đủ declaring class, method name, parameter descriptor, return type, modifiers và event kind của mỗi match. Một biểu thức match theo tên đơn thuần không đủ xác minh. Nếu engine có tùy chọn bỏ qua synthetic/bridge method, cần kiểm tra tài liệu và phép thử riêng; không áp dụng mặc định từ một engine sang engine khác.

## 9. Refactor call graph và khác biệt `call` / `execution`

### 9.1. Tách phương thức

Trước refactor:

```text
Caller -> OrderService.submit()
```

Sau refactor:

```text
Caller -> OrderFacade.submit()
            -> OrderCore.process()
                 -> Repository.save()
```

Nếu pointcut mở rộng bằng wildcard theo tên, việc thêm facade/helper có thể khiến advice chạy ở **hai tầng**. Nếu pointcut cố định vào `OrderService.submit`, lời gọi nghiệp vụ mới có thể không còn match. Cần xác định ý nghĩa mong muốn: đo **đầu vào use case**, một thao tác persistence hay mỗi lần thực thi nội bộ?

### 9.2. Phân biệt call-site và execution-site

- `call` quan sát lệnh gọi tại caller (nếu site được weave). Khi refactor chuyển lời gọi sang một class/caller không được weave, join point có thể biến mất khỏi phạm vi giám sát.
- `execution` quan sát thân method (nếu thân đã weave). Khi caller chuyển nơi gọi nhưng vẫn đi vào cùng thân method, execution join point vẫn có thể được quan sát.
- Proxy interception thường phụ thuộc **đường gọi đi qua proxy**, không tương đương việc dệt method execution. Ví dụ target gọi `this.process()` trong delegation proxy thường bypass proxy, nhưng woven execution có thể vẫn khớp.

**Bài tập:** viết rõ lời gọi đang được đo nằm ở caller hay callee trước khi đổi cấu trúc code; sau đó vẽ trace từng phương án. Đừng kết luận “pointcut sai” khi không có bằng chứng interception/weaver đã chạm join point thực.

### 9.3. Callback, lambda và bất đồng bộ

Khi chuyển từ lời gọi đồng bộ trực tiếp sang callback, executor hoặc một chuỗi future, thời điểm gọi, thread và receiver có thể thay đổi. Một pointcut `execution` trên method nghiệp vụ có thể vẫn match ở thread mới; một `call` pointcut ở caller cũ có thể không match call site mới; proxy reference có thể bị bỏ qua trong callback.

Nếu advice chỉ đo thời gian đến khi method trả một `CompletionStage`, nó chưa đo thời gian hoàn tất tác vụ. Trong bài tập “thay đổi cấu trúc”, phải đo chính xác **join point coverage**, sau đó mới bàn đến semantics latency/outcome.

## 10. Quy trình thử thay đổi có kiểm soát

### Pha A — chốt trạng thái ban đầu

1. Chọn một pointcut hiện hữu và ghi rõ vì sao nó phải chọn những method nào.
2. Tạo danh sách **positive cases** (phải match) và **negative cases** (không match).
3. Bật diagnostics của AOP engine hoặc một tracer tạm thời để ghi chính xác method/event được chọn.
4. Ghi kết quả baseline: số match, tên signature, advice invocation count và các outcome quan trọng.
5. Giữ artifact/version/config weave cụ thể để tái hiện kết quả.

### Pha B — mỗi lần một refactor

1. Đổi **một chiều duy nhất**: tên, package, overload, annotation, hierarchy hoặc call graph.
2. Kiểm tra chương trình nghiệp vụ vẫn đúng trên input tương đương.
3. Chạy lại positive/negative tests. So sánh tập match theo **hợp đồng nghiệp vụ**, không chỉ theo số lượng.
4. Xác định thiếu match hay thừa match; chỉ ra thay đổi cấu trúc nào gây ra.
5. Quyết định sửa pointcut, sửa annotation/hợp đồng, sửa phạm vi weaving hoặc đổi vị trí chặn.
6. Lặp lại với một refactor khác sau khi đã lưu kết quả và kết luận.

### Pha C — cố tình tạo hai lỗi

**False negative:** đổi `submit` thành `confirmOrder` nhưng giữ matcher `name == "submit"`; kết quả `false` dù use case thuộc tập phải chặn.

**False positive:** thêm overload `submit(String,int)` không thuộc policy; matcher `name == "submit"` cho `true` và chọn sai method.

Chương trình `FragilePointcutDemo` ở trên tạo sẵn hai trường hợp này. Có thể mở rộng để so sánh matcher theo annotation ở interface, implementation hoặc signature cụ thể.

### Pha D — sửa và chứng minh hồi quy

Sau khi sửa, chạy lại toàn bộ ma trận; điểm sửa được coi là đạt khi:

- Mọi positive case khớp, kể cả sau các refactor nằm trong phạm vi cần hỗ trợ.
- Mọi negative case không khớp; helper/health method không bị bắt ngoài ý muốn.
- Advice chạy đúng một lần theo mỗi invocation hợp lệ, hoặc số lần cụ thể đã được thiết kế.
- Kết quả và exception của nghiệp vụ không đổi nếu advice là quan sát thuần.
- Test chạy trên **artifact đã weave/proxy setup đúng** với môi trường triển khai.

## 11. Cách kiểm tra thực tế trong build và runtime

Một bộ test có hai tầng:

| Tầng | Điều chứng minh được | Giới hạn |
| --- | --- | --- |
| Kiểm thử predicate/matcher | Với một signature + annotation + args, matcher chọn đúng theo hợp đồng | Không chứng minh class/call site thực được instrument/weave |
| Kiểm thử tích hợp AOP | Advice thực sự chạy ở join point trong bản build/launcher đúng | Có thể khó cô lập lỗi matcher khỏi cấu hình weaving nếu thiếu diagnostics |

Đối với AspectJ/bytecode weaving, bật thông tin weave khi build/launch, đối chiếu class nằm trong source roots, `-inpath`, `-aspectpath` hoặc phạm vi load-time weaving tương ứng. Đối với proxy, kiểm tra receiver thực tại call site và vị trí interceptor. Sau refactor, class mới có thể không còn trong phạm vi build task/agent/classloader, khiến một pointcut đúng vẫn không chạy.

Một dạng trace hữu ích:

```text
revision=before event=execution method=demo.orders.OrderService.submit(String) expected=true actual=true
revision=after event=execution method=demo.checkout.OrderService.confirmOrder(String) expected=true actual=false
revision=after event=execution method=demo.checkout.DebugHelper.flush() expected=false actual=true
```

Đây là **định dạng trace minh họa**. Trước khi báo pointcut mong manh, phải xác nhận `event` và `method` là đúng join point hiện hữu; không so sánh hai tên method khác nhau bằng chuỗi raw đơn giản nếu cả hai đại diện cùng hành vi.

Có thể lưu một “coverage manifest” từ build hoặc integration test chứa join point ID + policy ID, rồi so sánh với baseline. Khi một refactor thay đổi chữ ký, cần ánh xạ **ngữ nghĩa** từ method cũ sang mới trước khi báo thiếu/thừa match.

## 12. Tiêu chí đánh giá sự ổn định của pointcut

Một pointcut tốt là pointcut **đúng phạm vi thay đổi mong đợi**, không phải pointcut match được nhiều nhất.

### 12.1. Dấu hiệu tương đối bền

- Quy tắc dựa trên ý nghĩa nghiệp vụ được biểu đạt tường minh trong annotation/hợp đồng có người sở hữu.
- Pointcut có ranh giới đủ hẹp và **negative tests** cho helper, logging, lifecycle method.
- Các điều kiện type/annotation/args tương ứng với join point kind mà engine thực sự quan sát.
- Build/CI kiểm tra cả AOP integration sau refactor, không chỉ compile/test business logic.
- Việc đổi method/package có checklist cập nhật policy, marker và weave scope.
- Phạm vi include/exclude được kiểm soát bằng review thay vì wildcard tùy tiện.

### 12.2. Dấu hiệu mong manh

- Bám vào package/tên ngẫu nhiên không phản ánh hợp đồng.
- Dùng wildcard để “tránh sửa aspect mỗi khi refactor”, nhưng không có test chặn false positive.
- Matcher dựa vào reflection ở interface còn marker được đặt ở implementation mà không có chính sách lookup rõ ràng.
- Chỉ test “advice có chạy ở một ca” mà không xác minh nhiều ca và số lần.
- Thay đổi cấu trúc source nhưng không chạy lại weaving task hoặc không kiểm tra artifact cuối.
- Trông cậy vào thứ tự/loại method sinh bởi compiler mà không khóa toolchain và không có test.
- Không phân biệt nested calls, retries, overload và invocation riêng biệt.

**Lưu ý về sự cân bằng:** biểu thức theo annotation cũng có thể mong manh nếu annotation bị quên khi tách method; biểu thức theo package có thể bền nếu package **chính là ranh giới kiến trúc được quản lý**. Tính bền vững phụ thuộc việc rule bám vào yêu cầu thật sự hay tình cờ bám vào hình dạng code hiện tại.

## 13. Bài tập bổ sung có tiêu chí chấm rõ ràng

### Bài A — đổi tên nhưng giữ nghĩa

Từ `FragilePointcutDemo`, đổi `confirmOrder` thành `placeOrder` và giữ `@Observed`. Dự đoán từng kết quả trước khi chạy. Chứng minh name-based matcher vẫn false còn marker matcher vẫn true. Sau đó gỡ marker và quan sát lỗi mới.

**Đạt:** giải thích được vì sao annotation chỉ bền khi được duy trì như hợp đồng.

### Bài B — thêm overload

Thêm vào `LegacyOrders` một method `submit(long orderId)` không có marker và triển khai tương ứng. Kiểm tra matcher tên có thể bắt tất cả overload, trong khi chính sách chỉ chọn method đánh dấu. Nâng predicate name-based lên so đủ parameter types để sửa trường hợp cụ thể, sau đó tự đánh giá predicate còn mong manh trước refactor signature hay không.

**Đạt:** có một false positive và một trường hợp được sửa; hiểu sự đánh đổi giữa precision và độ bền.

### Bài C — đổi package

Tạo hai class có cùng role nhưng thuộc `demo.orders` và `demo.checkout`. Định nghĩa một biểu thức chọn theo package, chạy test trước/sau khi chuyển. Tiếp theo thêm logger helper vào package được chọn để kiểm tra negative case.

**Đạt:** có bảng expected/actual và chứng minh không vô tình bắt logger helper.

### Bài D — thay đổi call graph

Tách phương thức nghiệp vụ thành `Facade.accept()` và `Processor.execute()`. Cho pointcut bắt theo tên `execute`, so advice count với pointcut bắt ở facade. Dùng hai cơ chế quan sát khác nhau nếu công cụ hỗ trợ `call` và `execution`, rồi phân tích chính xác vùng đang đo.

**Đạt:** chỉ ra được trường hợp match ở hai tầng và không làm tăng side effect nghiệp vụ.

### Bài E — chuyển annotation

Đặt marker trên interface method, implementation method, cả hai và không bên nào. Chạy thử với proxy reflection và weaving execution (nếu có toolchain). Ghi chính xác event kind và vị trí method metadata được đọc.

**Đạt:** không đưa kết luận “annotation interface luôn được kế thừa sang implementation”.

### Bài F — CI guard chống regression

Tạo bảng dữ liệu test nhỏ:

```text
case                         expectedMatch
LegacyOrders.submit(String)  true
LegacyOrders.submit(String,int) false
RefactoredOrders.confirmOrder(String) true
DebugHelper.flush()          false
HealthService.ping()         false
```

Gắn assertion ở matcher level và integration level. Với refactor mới, test phải fail khi một method bắt buộc biến mất khỏi tập match hoặc khi method ngoài policy bị chọn. Dữ liệu test cần thể hiện **ý nghĩa nghiệp vụ**, không tự sinh expected từ pointcut đang kiểm tra.

## 14. Những lỗi suy luận phổ biến

1. **“Build thành công thì pointcut đúng.”** Sai: biểu thức có thể không match gì mà quá trình biên dịch vẫn thành công.
2. **“Advice không chạy tức pointcut sai.”** Có thể caller không đi qua proxy, class/call site chưa weave hoặc advice chain bị tắt.
3. **“Wildcard càng rộng thì càng tránh bug refactor.”** Nó có thể giảm false negative nhưng tăng false positive nghiêm trọng.
4. **“Đổi method chỉ là rename nên AOP không ảnh hưởng.”** Pointcut theo signature/tên phụ thuộc trực tiếp vào rename.
5. **“Có annotation trên interface là weaving ở implementation luôn nhìn thấy.”** Không có quy tắc chung như vậy cho mọi engine/event.
6. **“Cùng tên method là cùng join point.”** Overload, bridge methods, event kind và declaring class có thể khác.
7. **“Tăng advice count là chắc chắn có lỗi.”** Tập join point dự kiến có thể lớn hơn do yêu cầu mới; so với hợp đồng, không chỉ với một con số cũ.
8. **“Chỉ có một positive test là đủ.”** Không bắt được lỗi wildcard chọn nhầm helper hoặc overload.
9. **“Self-invocation luôn làm advice mất.”** Điều đó phụ thuộc mô hình proxy/weaving; không khái quát.
10. **“Refactor source và rebuild không làm thay đổi weaving.”** Build graph, classloader, source roots, aspectpath/inpath và output artifact có thể thay đổi.
11. **“Thêm logger để debug vô hại.”** Một helper mới nằm trong vùng pointcut có thể tạo recursion hoặc metric bùng nổ.
12. **“Dữ liệu test có thể suy từ pointcut.”** Như vậy sẽ không có oracle độc lập để phát hiện matcher sai.

## 15. Checklist nộp bài

- [ ] Mô tả chính xác chức năng nghiệp vụ nào phải chịu AOP và sự kiện nào là join point.
- [ ] Chụp baseline các positive và negative match trước khi refactor.
- [ ] Đã refactor ít nhất **tên method**, **overload**, và một trong **package/hierarchy/call graph**.
- [ ] Có ít nhất một false negative và một false positive được giải thích từ pointcut.
- [ ] Đã chạy chương trình Java 17 và hiểu vì sao marker annotation trên interface được thấy qua JDK proxy.
- [ ] Không suy luận mọi bytecode weaver có cùng semantics annotation/interface.
- [ ] Đã kiểm tra đúng đường proxy/weaving, signature, event kind và artifact thực chạy.
- [ ] Đã sửa biểu thức/marker theo hợp đồng rõ ràng, không dùng wildcard tùy tiện.
- [ ] Positive và negative tests đều PASS sau refactor.
- [ ] Không có advice đệ quy, chạy lặp ngoài thiết kế, đổi return/exception hoặc nhân đôi side effect.
- [ ] Báo cáo có bảng `before/after` với `expected/actual` và giải thích nguyên nhân.

**Kết luận thực hành:** Khi refactor, đừng chỉ hỏi “mã Java vẫn chạy không?”. Hãy hỏi thêm **“tập join point được chọn có còn phản ánh đúng hợp đồng AOP hay không?”**. Một pointcut tốt phải được chứng minh bằng **cả ca được chọn và ca bị loại**, trên đúng cơ chế weaving/interception đang triển khai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-capstone-exercises-010">Đo chi phí, kiểm thử hồi quy và giải thích khi nên bỏ AOP</a>

<details>
<summary>Xem chi tiết</summary>

# Đo chi phí, kiểm thử hồi quy và giải thích khi nên bỏ AOP

Áp dụng *aspect-oriented programming* (AOP) giúp tập trung những mối quan tâm xuyên suốt (*cross-cutting concerns*) như tracing, audit, kiểm soát quyền hoặc đo hiệu năng. Nhưng AOP cũng thêm một tầng vận hành: xác định *join point*, chọn *pointcut*, chuyển tiếp qua proxy/interceptor hoặc chạy bytecode đã *weave*, đóng gói ngữ cảnh và thực hiện *advice*. Khi một hệ thống chậm, không đủ căn cứ để kết luận “AOP gây chậm” chỉ vì thấy khung proxy trong stack trace. Cũng không đủ căn cứ để bỏ AOP chỉ vì thử nghiệm nhỏ cho thấy lời gọi trực tiếp nhanh hơn.

**Mục tiêu của bài tập tổng hợp** là xây dựng được một lập luận có bằng chứng: đo đúng phần chi phí cần quan tâm, kiểm chứng không thay đổi kết quả và side effect, sau đó quyết định **giữ, thu hẹp, thay cách triển khai hay bỏ AOP** đối với một concern cụ thể. Quyết định phải xét cả hiệu năng, độ bao phủ chính sách, tính dễ hiểu, khả năng bảo trì và các đường chạy khác nhau.

Bài học tập trung vào AOP tổng quát, không gắn vào một bộ thư viện AOP cụ thể. Ví dụ chạy được với Java 17+ dùng JDK dynamic proxy để minh họa một cơ chế. Khi ứng dụng thực dùng weaving hoặc interceptor bytecode, cần thực hiện lại phép đo và kiểm thử trên cơ chế đó.

## 1. Hiểu “chi phí AOP” trước khi đo

Một lời gọi đi qua AOP thường có các nhóm công việc:

1. **Dispatch:** lời gọi đi qua proxy, handler hoặc điểm bytecode đã chèn.
2. **Matching:** kiểm tra pointcut tĩnh hoặc động; có hệ thống giải quyết một phần khi khởi tạo.
3. **Context:** chuẩn bị metadata, đối số, trạng thái chuỗi advice, thông tin correlation.
4. **Interception:** chạy lần lượt các advice, `proceed()` và các đoạn `finally`/xử lý ngoại lệ.
5. **Nội dung advice:** ghi log, lưu audit, tra cứu quyền, đo thời gian, gửi telemetry hoặc truy vấn dữ liệu.
6. **Chi phí gián tiếp:** allocation, GC, cache locality, tranh chấp lock, blocking I/O và khả năng JIT inline đường gọi.

Nhóm 1–4 thường được gọi là *overhead cơ chế*. Nhóm 5 là công việc chức năng thực sự của concern. Hai nhóm phải được tách khi diễn giải kết quả, bởi một lời gọi chậm sau khi bật audit có thể đang thực hiện I/O cần thiết, chứ không phải proxy chậm.

| Câu hỏi | Phép so sánh hợp lệ | Điều không thể kết luận |
|---|---|---|
| Proxy/interceptor thêm bao nhiêu chi phí? | Direct + công việc tương đương so với đường AOP + cùng công việc | Không so direct không audit với AOP có audit rồi gán chênh lệch cho proxy |
| Chức năng audit tốn bao nhiêu tổng chi phí? | Cùng nghiệp vụ, tắt/bật audit có chủ đích và đo cả side effect | Không gọi đây là “overhead thuần của AOP” |
| Pointcut quá rộng gây bao nhiêu lượt thừa? | Đếm join point ứng viên, số matcher chạy và số advice thực sự chạy | Không suy từ phần trăm được chọn thành phần trăm tiết kiệm CPU |
| Đổi cơ chế proxy sang weaving có lợi không? | Giữ tập join point, kết quả và các advice giống nhau | Không so hai đường chặn khác chức năng hoặc khác số lần chạy target |
| Bỏ AOP có làm giảm rủi ro không? | Test bao phủ chức năng + vận hành + hiệu năng của phương án thay thế | Không chỉ xét ns/op, bỏ qua audit/quyền/tracing bị mất |

### Chi phí tuyệt đối, tương đối và chi phí toàn hệ thống

Giả sử một benchmark đáng tin cho cùng lượng công việc cho ra:

```text
direct  = 80 ns/op
via AOP = 112 ns/op

chênh lệch tuyệt đối = 32 ns/op
chênh lệch tương đối = (112 - 80) / 80 × 100% = 40%
```

**Đây chỉ là số minh họa phép tính, không phải thông số hiệu năng của Java hay một công cụ AOP.** 40% có thể rất đáng chú ý với một hot path gọi hàng triệu lần/giây; nhưng sẽ không có ý nghĩa tương tự với nghiệp vụ dành nhiều mili giây cho I/O. Ngược lại, một advice ghi sự kiện đồng bộ có thể chi phối p99 latency dù số lần proxy dispatch rất nhỏ.

Khi lập kế hoạch, xem **tần suất gọi × chi phí mỗi lượt**, rồi kiểm tra thông lượng và tail latency dưới tải. Nếu advice chạy 500.000 lần/giây và tạo thêm 200 byte/lượt, riêng dữ liệu tạm giả định đã tương đương khoảng 100 MB cấp phát/giây (100.000.000 byte/s, xấp xỉ 95,37 MiB/s). Đây là tốc độ cấp phát, không phải kết luận heap tăng liên tục; phần lớn object tạm có thể được GC thu hồi.

## 2. Bài tập tình huống: giữ audit, tracing và quy tắc nghiệp vụ

Một hệ thống có chức năng tính tiền mua hàng. Khi mua thành công, phải ghi dấu nghiệp vụ; khi lỗi, không được tăng số lượt mua thành công. Một chính sách tracing/audit được bổ sung tại ranh giới `buy()`. Phương thức `quote()` chỉ xem giá, không cần audit giao dịch mua hàng.

Các bất biến dùng làm tiêu chí kiểm chứng:

- `quote(3)` trả về 75.
- `buy(2)` trả về 50; tăng bộ đếm giao dịch thành công đúng một lần.
- `buy(-1)` ném `IllegalArgumentException` với thông điệp `units must be positive`; không tăng bộ đếm thành công.
- Khi có advice, `buy(2)` có tracing vào/ra và audit success đúng một lần.
- Với `buy(-1)`, tracing vào/ra vẫn xuất hiện, ghi audit failure theo chính sách minh họa.
- `quote()` không phát tracing/audit giao dịch mua.
- Biến thể có AOP không được làm đổi kết quả nghiệp vụ so với biến thể trực tiếp.

Đối với hệ thống thật, yêu cầu “audit success” có thể đòi hỏi sự kiện chỉ được xác nhận sau commit bền vững. Bài tập này đơn giản hóa thành **thành công khi phương thức đích trả về bình thường**; không suy rộng điều đó sang giao dịch phân tán hoặc I/O bất đồng bộ.

### 2.1. Chương trình Java 17+ kiểm thử hồi quy trực tiếp và qua proxy

Tạo tệp `AopRegressionDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AopRegressionDemo {
    public interface Pricing {
        int quote(int units);
        int buy(int units);
    }

    static final class PricingCore implements Pricing {
        private final List<String> events;
        private int successfulBuys;

        PricingCore(List<String> events) {
            this.events = events;
        }

        @Override
        public int quote(int units) {
            if (units <= 0) {
                throw new IllegalArgumentException(
                    "units must be positive"
                );
            }
            return units * 25;
        }

        @Override
        public int buy(int units) {
            if (units <= 0) {
                throw new IllegalArgumentException(
                    "units must be positive"
                );
            }
            int amount = units * 25;
            successfulBuys++;
            events.add("BUSINESS buy=" + amount);
            return amount;
        }

        int successfulBuys() {
            return successfulBuys;
        }
    }

    record Outcome(
        int quote,
        int paid,
        String failure,
        int successfulBuys
    ) {}

    static Object invokeTarget(
            Pricing target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    static Pricing instrument(
            Pricing target, List<String> events) {
        return (Pricing) Proxy.newProxyInstance(
            Pricing.class.getClassLoader(),
            new Class<?>[] { Pricing.class },
            (proxy, method, args) -> {
                // Pointcut minh họa: chỉ method buy().
                if (!method.getName().equals("buy")) {
                    return invokeTarget(target, method, args);
                }

                events.add("TRACE enter");
                try {
                    Object result = invokeTarget(
                        target, method, args
                    );
                    events.add("AUDIT success");
                    return result;
                } catch (Throwable failure) {
                    events.add("AUDIT failure");
                    throw failure;
                } finally {
                    events.add("TRACE exit");
                }
            }
        );
    }

    static Outcome scenario(Pricing service, PricingCore core) {
        int quote = service.quote(3);
        int paid = service.buy(2);
        String error = "(none)";

        try {
            service.buy(-1);
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }

        return new Outcome(
            quote, paid, error, core.successfulBuys()
        );
    }

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        List<String> directEvents = new ArrayList<>();
        PricingCore directCore = new PricingCore(directEvents);
        Outcome direct = scenario(directCore, directCore);

        List<String> aopEvents = new ArrayList<>();
        PricingCore aopCore = new PricingCore(aopEvents);
        Pricing withAop = instrument(aopCore, aopEvents);
        Outcome intercepted = scenario(withAop, aopCore);

        check(direct.equals(intercepted),
              "business result changed");
        check(directEvents.equals(
                  List.of("BUSINESS buy=50")),
              "unexpected direct events");
        check(aopEvents.equals(List.of(
                  "TRACE enter",
                  "BUSINESS buy=50",
                  "AUDIT success",
                  "TRACE exit",
                  "TRACE enter",
                  "AUDIT failure",
                  "TRACE exit"
              )), "incorrect advice events");

        System.out.println(
            "Same business outcome: " + direct.equals(intercepted)
        );
        System.out.println("Quote: " + intercepted.quote());
        System.out.println("Buy: " + intercepted.paid());
        System.out.println("Failure: " + intercepted.failure());
        System.out.println(
            "Successful buys: " + intercepted.successfulBuys()
        );
        System.out.println(
            "Direct events: " + directEvents.size()
        );
        System.out.println("AOP events: " + aopEvents.size());
        System.out.println("Regression: PASS");
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AopRegressionDemo.java
java AopRegressionDemo
```

Stdout khi chương trình vượt qua mọi assertion:

```text
Same business outcome: true
Quote: 75
Buy: 50
Failure: units must be positive
Successful buys: 1
Direct events: 1
AOP events: 7
Regression: PASS
```

**Giải thích:** Hai biến thể có cùng `quote`, `paid`, thông điệp lỗi và số lượt mua thành công. Số sự kiện khác nhau **có chủ đích**: direct core chỉ ghi một sự kiện nghiệp vụ; proxy ghi thêm tracing và audit trên hai lần gọi `buy`. Assertion kiểm tra chính xác thứ tự sự kiện, bao gồm cả đường thất bại.

Bài này dùng reflection trong handler. `Method.invoke` bọc ngoại lệ từ phương thức đích trong `InvocationTargetException`; hàm `invokeTarget` mở wrapper để caller nhận nguyên nhân thực. Handler bắt `Throwable` chỉ để ghi nhận failure trước khi ném lại; đây là mẫu minh họa về luồng advice, không có nghĩa mọi hệ thống audit đều nên coi mọi throwable là một “giao dịch thất bại” cùng ngữ nghĩa.

**Giới hạn của demo:**

- Handler chọn method bằng tên `buy`; đây là pointcut đủ đơn giản cho interface chỉ có hai method. Trong hệ thống lớn, cần xét chữ ký, overload và kiểu khai báo.
- `Object` methods như `equals`, `hashCode` và `toString` chưa được định nghĩa chính sách proxy production.
- `PricingCore` và `ArrayList` dùng một luồng; ví dụ không chứng minh thread safety.
- Unit test bảo vệ những ca cụ thể; chưa chứng minh các giá trị biên hoặc đường concurrency và transaction production.
- **Số lượng sự kiện không phải benchmark hiệu năng.** Không đọc “7 lớn hơn 1” thành hệ số chi phí CPU hoặc kết luận phải bỏ AOP.

## 3. Bài tập A: lập ma trận kiểm thử hồi quy

Hãy tự thiết kế ma trận trước khi đọc bảng gợi ý. Với mỗi trường hợp, ghi ít nhất: *đầu vào, kết quả/ngoại lệ, side effect nghiệp vụ, advice mong đợi, điểm không được advice can thiệp*.

| Kịch bản | Kết quả nghiệp vụ | Side effect domain | Advice mong đợi |
|---|---|---|---|
| `quote(3)` | 75 | Không tăng mua hàng | Không audit mua, không tracing mua |
| `buy(2)` | 50 | Thành công +1 | Enter, success, exit |
| `buy(-1)` | `IllegalArgumentException` | Không tăng mua hàng | Enter, failure, exit |
| `quote(-1)` | `IllegalArgumentException` | Không tăng mua hàng | Không tạo audit giao dịch mua |
| Gọi `PricingCore.buy(2)` trực tiếp | 50 | Thành công +1 | Proxy advice không chạy |
| Gọi `Pricing` qua proxy nhiều lần | Kết quả đúng từng lượt | Số thành công chính xác | Advice đúng mỗi invocation |
| Ném lỗi do chính advice | Phụ thuộc hợp đồng lỗi | Không được giả định rollback tự động | Test riêng precedence và cleanup |
| Lời gọi lồng nhau/retry | Tùy điểm chặn và số lần chạy target | Không đếm nhầm business operation | Xác minh advice hit và target calls riêng |

### Những assertion cần thiết

1. **Giữ kết quả:** cùng input, cùng kết quả hoặc loại lỗi/thông điệp theo contract.
2. **Giữ side effect:** cùng số lần thay đổi domain và dữ liệu cuối.
3. **Chứng minh pointcut:** advice chạy ở `buy` nhưng không ở `quote`.
4. **Chứng minh thứ tự:** `TRACE enter` trước target; success/failure sau outcome; `TRACE exit` luôn chạy.
5. **Chứng minh failure path:** khi target ném lỗi, audit success không được phát.
6. **Chứng minh đường thực:** test qua chính đối tượng đã instrument; gọi thẳng target không đại diện cho đường qua proxy.
7. **Kiểm tra concurrency:** nếu đổi bộ đếm thành shared state, cần thiết kế đồng bộ hoặc thu sự kiện cách ly.
8. **Kiểm tra self-invocation và class loading:** khả năng can thiệp khác nhau giữa proxy và weaving; không suy diễn từ một demo.

**Tại sao cần negative test?** Nếu pointcut rộng ra và audit nhầm `quote`, mọi test chỉ gọi `buy` vẫn qua. Negative test khẳng định tính chọn lọc, giảm cả lỗi ngữ nghĩa lẫn overhead do chặn thừa.

## 4. Kiểm thử hồi quy khi thay AOP bằng thiết kế khác

Một lỗi thường gặp: so sánh **bản có AOP và có auditing** với **bản gọi trực tiếp nhưng bỏ auditing**, rồi tuyên bố “bỏ AOP không gây hồi quy vì kết quả số học như nhau”. Thực tế yêu cầu audit đã mất.

Phải tách hai dạng so sánh:

### 4.1. So sánh chức năng tương đương

| Biến thể | Bảo toàn policy audit/tracing? | Dùng để so sánh |
|---|---|---|
| A — target trực tiếp, không advice | Không nếu policy bắt buộc | Baseline logic domain, không phải phương án production tương đương |
| B — qua AOP, có audit/tracing | Có, nếu pointcut bao phủ | Hành vi hiện tại |
| C — explicit decorator/wrapper có cùng audit/tracing | Có nếu mọi caller qua wrapper | So sánh kiến trúc và chi phí cơ chế |
| D — viết thủ công audit/tracing ở các call site | Có nếu không bỏ sót | Chi phí bảo trì và rủi ro scattering/tangling |
| E — AOP với pointcut thu hẹp | Có nếu không bỏ sót điểm bắt buộc | Đo lợi ích giảm can thiệp thừa |

Nếu audit là bắt buộc, **B so với C** là so sánh công bằng hơn để quyết định cơ chế triển khai: cùng logic nghiệp vụ và cùng chức năng quan sát được. So B với A cho thấy chi phí tổng để có chính sách, nhưng không thể quy toàn bộ chênh lệch cho proxy.

### 4.2. Những thứ cần khớp để gọi là tương đương

- Tập đối tượng và phương thức chịu chính sách.
- Thứ tự kiểm tra quyền, audit và thực thi target.
- Ngữ nghĩa thành công/thất bại, ngoại lệ và cleanup.
- Tần suất sự kiện: mỗi attempt, mỗi invocation hay mỗi business transaction.
- Nội dung sự kiện audit, dữ liệu nhạy cảm và nơi xuất dữ liệu.
- Hành vi khi fail: audit không ghi được thì nghiệp vụ có được phép thành công?
- Vòng đời đồng bộ/bất đồng bộ, timeout và propagation context.
- Khả năng chặn self-invocation, callback, retry và các đường gọi không qua proxy.

Nếu một phương án ghi audit sau mỗi lần `Method.invoke` còn phương án khác đợi commit bền vững, đó là **hai hợp đồng khác nhau**, không nên đặt cạnh nhau như một benchmark cùng điều kiện.

## 5. Đo overhead: kế hoạch benchmark đúng

Một chương trình tự lấy `System.nanoTime()` quanh vài nghìn lời gọi thường chưa phải microbenchmark đáng tin. JVM cần warm-up, JIT có thể inline hoặc loại công việc không được dùng; thời gian đo còn chịu ảnh hưởng GC, scheduler, CPU frequency và load từ hệ thống.

Trong Java, sử dụng **JMH (Java Microbenchmark Harness)** cho thí nghiệm vi mô và kết hợp **kiểm thử tải ứng dụng** cho câu hỏi về trải nghiệm thực tế. JMH hỗ trợ warm-up, measurement iterations, forks, tiêu thụ kết quả và plugin profiler. Không lấy output minh họa của bài tập làm số benchmark.

### 5.1. Những đường cần đo

| Biến thể | Mục đích đo | Điều phải giữ giống |
|---|---|---|
| Direct core | Baseline đường nghiệp vụ | Input, thuật toán, output |
| Proxy với handler chuyển tiếp không có advice | Ước lượng dispatch overhead | Cùng phương thức đích |
| Proxy với pointcut không khớp | Đo chi phí matching/skip | Cùng input và đường target |
| Proxy với một advice no-op | Ước lượng chi phí chain | Không thêm side effect chức năng |
| Proxy với nhiều advice no-op | Quan sát ảnh hưởng chiều sâu | Cùng số lần gọi target |
| AOP + audit/tracing thật | Tổng chi phí phương án có chức năng | Cùng định dạng và sink khi so với phương án thay thế |
| Explicit decorator + cùng audit/tracing | So sánh cơ chế tổ chức | Same policy và side effect |
| AOP + pointcut hẹp | Đo khoản tiết kiệm từ phạm vi chọn | Giữ mọi join point bắt buộc |
| Weaving tương đương, nếu triển khai thực hỗ trợ | So sánh lựa chọn instrument | Cùng phạm vi join point và advice |

Không cần đo mọi biến thể nếu quyết định đơn giản. Nhưng để tuyên bố “proxy là nút thắt”, ít nhất cần tách **proxy rỗng** khỏi **advice có công việc thật**.

### 5.2. Đơn vị đo và điều kiện đo

Theo dõi đồng thời:

- **Latency/op:** thời gian một lời gọi; phân biệt mean với percentile trên workload rộng.
- **Throughput:** số thao tác hoàn tất mỗi đơn vị thời gian ở độ đồng thời cụ thể.
- **CPU:** thời gian CPU cho xử lý ứng dụng và overhead.
- **Allocated bytes/op:** lượng cấp phát mỗi lượt, nếu profiler hỗ trợ.
- **Allocation rate và GC:** áp lực tạo object và thời gian collection.
- **p95/p99 dưới tải:** độ trễ đuôi, nơi log đồng bộ, lock hoặc queue thường bộc lộ.
- **Advice invocations và matcher evaluations:** số lần xử lý hoặc đánh giá điều kiện thật.
- **Correctness:** exception, events và outcome; số liệu “nhanh” không có giá trị nếu chức năng bị mất.

Quy trình đo:

1. Cố định JDK, runtime flags, commit, máy test và tập input; ghi rõ cấu hình.
2. Tạo baseline với dữ liệu tránh dead-code elimination; trong JMH, trả về kết quả hoặc tiêu thụ bằng `Blackhole`.
3. Chạy warm-up đủ, measurement iterations và nhiều fork phù hợp với độ nhiễu.
4. Tránh in console, I/O filesystem và logging thay đổi giữa các biến thể thuần cơ chế.
5. Khi đo chức năng audit/tracing, dùng **cùng sink** hoặc mô hình sink tương đương. Nếu I/O là trọng tâm, đo riêng dưới tải có backpressure.
6. Sử dụng JMH GC profiler (chẳng hạn `-prof gc` khi công cụ/phiên bản hỗ trợ) để quan sát cấp phát; dùng JFR, profiler CPU/allocation hoặc heap dump khi cần tìm nút thắt.
7. Chạy load test thực tế ở ít nhất vài mức concurrency đại diện; ghi p50/p95/p99, CPU, throughput và GC.
8. Kiểm tra các assertion hồi quy sau mọi thay đổi instrument, pointcut hay tối ưu context.
9. Lặp phép đo khi thay compiler/JDK, cấu hình JVM, cơ chế weaving hoặc phân phối tải.

### 5.3. Phác thảo JMH — phụ thuộc thư viện, không phải chương trình độc lập

Một benchmark có thể đặt state của mỗi luồng chứa đối tượng đã chuẩn bị và đo hai đường có **cùng tính toán**:

```java
// MINH HỌA cấu trúc JMH, cần JMH dependency và annotation processor.
// Các tên State/Benchmark/Scope/Setup/Level là annotation/API của JMH.
@State(Scope.Thread)
public class PricingBenchmark {
    Pricing direct;
    Pricing proxiedNoOp;

    @Setup(Level.Trial)
    public void setup() {
        direct = createDirectCalculator();
        proxiedNoOp = createProxyWithoutBusinessAdvice(direct);
    }

    @Benchmark
    public int directCall() {
        return direct.quote(3);
    }

    @Benchmark
    public int proxyCall() {
        return proxiedNoOp.quote(3);
    }
}
```

Đoạn trên là **pseudocode gần Java để thiết kế benchmark**: hai hàm `create...` chưa được định nghĩa, imports/dependencies không kèm theo, nên không thể chạy độc lập. Đừng gán một con số ns/op giả định cho nó. Benchmark thực tế phải bảo đảm `direct` và `proxiedNoOp` chạy cùng logic, không lẫn side effect hoặc class layout khác ngoài yếu tố đang đo.

Cũng không được mặc định benchmark đo một call site monomorphic liên tục sẽ giống production khi proxy có nhiều kiểu, nhiều target và concurrency khác nhau.

### 5.4. Ví dụ cách đọc dữ liệu giả định

| Biến thể giả định | Mean | Allocated bytes/op | Ý nghĩa sơ bộ |
|---|---:|---:|---|
| Direct | 60 ns | 0 B | Baseline nghiệp vụ nhỏ |
| Proxy no-op | 95 ns | 32 B | Có thể có chi phí dispatch/context |
| Proxy + 3 interceptor no-op | 125 ns | 48 B | Chuỗi dài hơn có chi phí khác |
| Proxy + audit dùng I/O | 25 µs | 400 B | I/O chi phối, không nên quy hết cho dispatch |
| Decorator + cùng I/O audit | 24,8 µs | 370 B | Chênh lệch nhỏ hơn rất nhiều so với tổng I/O |

**Toàn bộ số trong bảng đều do bài học đặt ra để minh họa, không phải kết quả đo từ JVM hay thư viện thật.** Từ bảng giả định không thể suy ra decorator luôn nhanh hơn hay AOP luôn chậm. Hãy xem độ phân tán và sai số của phép đo: chênh lệch 0,2 µs trong workload có I/O có thể nằm trong nhiễu hoặc biến thiên do sink.

## 6. Bài tập B: tính chi phí và chọn nơi tối ưu

Cho dữ liệu **giả định có chủ đích** của hai loại phương thức:

```text
read() hot path:
  2.000.000 calls/second
  20 ns overhead cơ chế mỗi call, không có advice hữu ích

checkout() critical path:
  1.000 calls/second
  100 µs audit + 20 ns overhead cơ chế mỗi call
```

**Câu hỏi:**

1. Chi phí CPU cộng dồn được mô hình hóa cho overhead cơ chế ở `read()` là bao nhiêu mỗi giây?
2. Nếu thu hẹp phạm vi để `read()` không đi qua interception nữa, tối đa khoản tiết kiệm theo mô hình là bao nhiêu?
3. Trong `checkout()`, tỷ lệ 20 ns so với 100 µs khoảng bao nhiêu?
4. Nếu audit là bắt buộc, có được xóa audit để tăng tốc không?
5. Nếu audit chậm vì sink đồng bộ, nên điều tra chi phí nào trước khi đổi loại proxy?

**Lời giải:**

- `2.000.000 × 20 ns = 40.000.000 ns`, tương đương **40 ms thời gian cộng dồn mỗi giây**, tức khoảng **0,04 CPU-second/second** trong mô hình giản lược. Kết quả này **không bằng** giảm 40 ms latency cho từng lời gọi hay một bảo đảm CPU thực tế.
- Nếu có thể đưa `read()` ra khỏi interception hoàn toàn và chi phí bỏ đi thực sự là 20 ns/lượt, đó là trần tiết kiệm theo mô hình. Nếu chỉ lọc advice nhưng vẫn đi qua proxy, chi phí dispatch có thể còn.
- `100 µs = 100.000 ns`; `20/100.000 = 0,0002`, tức **0,02%** so với riêng khoản audit 100 µs. Đây là so sánh các hạng mục giả định, không phải tỷ lệ tổng request.
- Không được bỏ audit bắt buộc. Có thể sửa policy hoặc cơ chế xuất audit nếu vẫn bảo toàn đầy đủ semantics, độ bền và chính sách lỗi.
- Trước hết đo thời gian chờ I/O, serialization, queue, contention, retry và độ trễ sink. Tối ưu vài nanosecond của proxy gần như không giải quyết nguồn chậm 100 µs trong ví dụ.

### Tại sao cùng một tối ưu có giá trị khác nhau?

Ở phương thức hot path rất nhỏ, chi phí dispatch/matching dù thấp tuyệt đối vẫn có thể đáng xét do tần suất cực lớn. Với một nghiệp vụ nặng, I/O hoặc giao dịch đắt có thể chi phối. Vì vậy **tối ưu phạm vi trước** khi micro-optimize là một lựa chọn có lý: loại những join point không cần can thiệp có thể giảm nhiều invocation hơn việc thay vài lệnh trong advice.

Tuy nhiên, không thu hẹp một pointcut đến mức bỏ qua quyền truy cập hoặc audit bắt buộc; performance improvement chỉ hợp lệ khi kiểm thử hồi quy chức năng vẫn đạt.

## 7. Khi nào nên giữ AOP?

AOP thường phù hợp hơn khi:

1. **Concern thực sự xuyên suốt.** Cùng chính sách phải chạy ở nhiều biên nghiệp vụ; viết tay tạo scattering/tangling đáng kể.
2. **Tập join point định nghĩa rõ và có thể kiểm chứng.** Có ma trận nơi phải chặn và không được chặn; đội dự án có thể duy trì nó.
3. **Thứ tự advice và xử lý lỗi có hợp đồng ổn định.** Cách xếp before/around/after/finally minh bạch và test được.
4. **Cơ chế instrument đáng tin trong triển khai.** Proxy/weaving được kích hoạt đúng trên mọi đường gọi cần thiết; không có điểm bypass không kiểm soát.
5. **Chi phí đo được phù hợp mục tiêu.** CPU, latency, memory, GC và throughput nằm trong ngân sách hiệu năng.
6. **Giảm chi phí bảo trì đáng kể.** Thay policy ở một nơi thay vì sửa hàng chục vị trí; người bảo trì vẫn hiểu đường chạy.
7. **Khả năng quan sát đủ tốt.** Có log/trace/breakpoint để theo dõi advice khi có lỗi và người phát triển biết cách debug.
8. **Không có yêu cầu đặc biệt xung đột.** Ví dụ một số native image/AOT, security boundary hoặc class loader động có thể đặt ràng buộc; cần kiểm tra implementation thực.

Không cần mọi điều kiện có mức độ như nhau. Một hệ thống nhỏ có thể chỉ cần vài concern xuyên suốt và AOP vẫn rõ ràng; một hệ thống lớn cũng có thể không phù hợp nếu tập join point khó chứng minh hoặc việc instrument phá vỡ môi trường triển khai.

## 8. Khi nào nên thu hẹp hoặc thay đổi AOP trước khi bỏ?

**A. Pointcut rộng bất hợp lý**

Nếu log/tracing được chèn vào mọi getter, helper và phép tính nhỏ, trước hết xác định những điểm có giá trị. Chọn chỉ các thao tác quan trọng hoặc sampling telemetry khi được phép, dùng điều kiện tĩnh sớm và hạn chế matcher động đắt.

**B. Chuỗi advice quá sâu**

Đo từng interceptor, kiểm tra logic trùng lặp, việc clone `Object[]`, tạo metadata, map, stack trace và stringify. Có thể đơn giản hóa chain hoặc dùng một context hợp lý, nhưng phải giữ đúng thứ tự policy, retry và cleanup.

**C. Chi phí chính nằm trong nội dung advice**

Nếu profiler thấy phần lớn thời gian ở I/O, serialization, lock hoặc xuất metric, sửa đường đó có khả năng tác động hơn đổi từ reflection sang method handle. Không vội thay cơ chế intercept khi bottleneck nằm ở sink.

**D. Chỉ một số loại đối tượng cần instrument**

Một vài cơ chế proxy bọc toàn bộ interface; phương thức không khớp pointcut vẫn đi vào handler. Nếu công cụ cho phép, giảm tập đối tượng được bọc hoặc chuyển pointcut thành danh sách được xác định trước. Khi cân nhắc weaving, phải kiểm tra lại toàn bộ tập join point và hoạt động class loader.

**E. Chi phí startup hoặc footprint là vấn đề**

Với công cụ chạy ngắn, tạo proxy class, phân tích metadata hoặc biến đổi bytecode lúc nạp class có thể quan trọng hơn throughput ổn định. Cân nhắc caching hợp lý, giảm tập lớp cần instrument hoặc đường wrapper đơn giản cho một biên cụ thể.

**F. Khó gỡ lỗi do lớp trung gian**

Nếu vấn đề chính là khó quan sát, đầu tiên có thể cải thiện tài liệu sơ đồ luồng advice, naming, trace IDs, stack trace và test hợp đồng. Chỉ thay kiến trúc khi chi phí nhận thức/vận hành vẫn vượt lợi ích.

## 9. Khi nào nên bỏ AOP ở một phạm vi cụ thể?

“Bỏ AOP” ở đây nghĩa là **không còn sử dụng cơ chế can thiệp AOP cho một concern hoặc một tập join point**, rồi chuyển trách nhiệm còn cần thiết sang cách thực hiện rõ ràng khác. Đó không đồng nghĩa xóa luôn audit hoặc kiểm soát quyền.

### 9.1. Các dấu hiệu có cơ sở để bỏ

| Dấu hiệu | Vì sao AOP có thể không hợp | Phương án thay thế có thể xem xét |
|---|---|---|
| Chỉ một hoặc hai điểm gọi rõ ràng | Chi phí thiết lập/đọc cấu hình cao hơn giá trị tập trung hóa | Gọi helper tường minh hoặc decorator nhỏ |
| Concern chỉ là một bước nghiệp vụ cụ thể | Giấu luồng chính trong advice gây khó hiểu | Giữ trong service/domain workflow |
| Pointcut rất khó định nghĩa ổn định | Dễ bỏ sót hoặc chặn nhầm khi refactor tên lớp/phương thức | API tường minh và contract rõ |
| Cơ chế proxy có đường bypass quan trọng | Chính sách bắt buộc không được bảo đảm | Đặt policy ở trusted boundary hoặc giá trị/domain invariant |
| Nhiều advice thay đổi kết quả khó dự đoán | Thứ tự và propagation khó review | Composition/decorator rõ thứ tự hoặc orchestration tường minh |
| Đường hot path có overhead vượt ngân sách | Đo thực xác nhận dispatch/context là nút thắt | Wrapper chuyên biệt, direct call, weaving phù hợp hoặc bỏ intercept nơi không cần |
| Build/runtime không hỗ trợ ổn định | Instrumentation, class loader, agent hoặc môi trường đích xung đột | Mã tường minh/khả năng instrument tương thích |
| Không cần dynamic interception | Một concern cố định có thể đơn giản hơn khi truyền dependency rõ | Explicit interface/decorator/helper |

**Không được xem các dấu hiệu trên là quy tắc máy móc.** Nếu AOP đang bảo vệ một chính sách bắt buộc ở nhiều biên quan trọng, quyết định bỏ có thể tạo rủi ro lớn hơn chênh lệch benchmark.

### 9.2. Các phương án không dùng AOP

**Helper tường minh:**

```java
// Minh họa, không phải chương trình độc lập.
auditSink.record(event);
int result = checkoutService.checkout(order);
```

Cách này đơn giản nhưng phải đặt audit ở đúng thời điểm và đảm bảo mọi caller hợp lệ không bỏ qua. Nếu audit yêu cầu thành công sau commit, thứ tự mẫu trên còn chưa đáp ứng; dùng nó như ví dụ cú pháp về explicit call, không như policy hoàn chỉnh.

**Decorator quanh interface:**

```java
// Minh họa, không phải chương trình độc lập.
final class AuditedOperation implements Operation {
    private final Operation delegate;
    private final AuditSink audit;

    AuditedOperation(Operation delegate, AuditSink audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public Result run(Command input) {
        Result result = delegate.run(input);
        audit.success(input, result);
        return result;
    }
}
```

Đây là **đoạn minh họa** vì `Operation`, `AuditSink`, `Result` và `Command` chưa được định nghĩa. Decorator cũng là một lớp trung gian và vẫn có overhead; điểm khác là quan hệ phụ thuộc và đường gọi dễ nhìn hơn. Chưa có `finally`/failure policy trong snippet, nên không tự dùng nó để thay advice có yêu cầu audit failure/cleanup.

**Hàm domain hoặc policy object:** phù hợp với bất biến nghiệp vụ, kiểm tra trạng thái và luật tính toán. Chúng không nhất thiết là aspect chỉ vì cùng được nhiều service dùng.

**Explicit instrumentation tại biên:** phù hợp với một số ít request handler, job entry hoặc public API boundary, nơi mọi caller bắt buộc đi qua điểm đó và việc giữ biểu thức xử lý hiển thị trong code giúp review dễ hơn.

Điều quan trọng là mỗi lựa chọn phải được xem xét cả **khả năng bị bỏ qua**, **tính dễ hiểu** và **bằng chứng hồi quy**.

## 10. Bài tập C: quyết định có bỏ AOP hay không

Đánh giá bốn tình huống:

**Tình huống 1 — tracing trên hàng triệu hàm thuần**

Điều kiện: pointcut chọn tất cả getter và phép tính nhỏ; tracing chủ yếu để chẩn đoán, không phải nghĩa vụ kiểm toán. Kết quả profiling xác nhận tỷ lệ lớn sự kiện không hữu ích và tạo nhiều allocation. Giải pháp hợp lý có thể là **thu hẹp phạm vi hoặc tắt tracing ở hot path**, không nhất thiết bỏ toàn bộ AOP khỏi ứng dụng. Kiểm tra tập điểm chẩn đoán còn cần giữ.

**Tình huống 2 — audit bắt buộc cho nhiều thao tác thay đổi trạng thái**

Điều kiện: audit được triển khai tập trung, test bao phủ đủ join point, overhead trong ngân sách. Bỏ AOP khiến phải sao chép policy ở nhiều nơi và tăng rủi ro bỏ sót. Có lý do **giữ AOP**, nhưng vẫn cần kiểm thử đường bypass, đường lỗi và tính bền vững của audit.

**Tình huống 3 — một thao tác đơn giản cần log thủ công**

Điều kiện: chỉ một biên được instrument, nhiều lớp proxy khó debug, có helper rõ và đội dự án không cần mở rộng policy. Nếu kiểm thử tương đương và đo cho thấy giải pháp tường minh ít phức tạp hơn, **bỏ AOP trong phạm vi này** có thể hợp lý.

**Tình huống 4 — proxy can thiệp sai khi self-invocation**

Điều kiện: chính sách quyền bắt buộc phải áp trên một lời gọi nội bộ nhưng cơ chế proxy hiện dùng chỉ nhìn thấy lời gọi qua proxy bên ngoài. Không thể “tối ưu” bằng cách bỏ hẳn kiểm tra. Phải **đặt policy tại biên đáng tin**, thay tổ chức đường gọi hoặc chọn cơ chế instrument đáp ứng join point bắt buộc, rồi kiểm thử không thể bypass.

### Đáp án tóm tắt theo tiêu chí

| Tình huống | Hướng quyết định | Bằng chứng buộc phải có |
|---|---|---|
| Tracing quá rộng | Thu hẹp, sampling có điều kiện hoặc tắt tại điểm thừa | Tần suất, hiệu năng, không mất telemetry bắt buộc |
| Audit xuyên suốt, đúng và rẻ | Giữ | Coverage join point, audit behavior, SLO |
| Một điểm gọi, nhiều phức tạp | Cân nhắc decorator/helper tường minh | Cùng chức năng, chi phí và độ rõ ràng |
| Proxy bỏ sót chính sách quyền | Sửa ranh giới/cơ chế; không bỏ policy | Test đường bypass và xử lý lỗi |

## 11. Ma trận quyết định đủ bằng chứng

Khi cần đưa ra quyết định kỹ thuật, dùng bảng so sánh trên cùng các tiêu chí:

| Tiêu chí | Giữ AOP | Thu hẹp/đổi cơ chế AOP | Bỏ AOP tại phạm vi đang xét |
|---|---|---|---|
| Chức năng bắt buộc | Giữ nguyên | Phải giữ nguyên | Chuyển sang nơi thực thi khác và test |
| Độ bao phủ chính sách | Phụ thuộc pointcut hiện tại | Phải có ma trận mới | Phải chứng minh mọi caller qua biên thay thế |
| Hiệu năng | Theo baseline thực tế | Lợi ích đo được sau thay đổi | So với phương án cùng semantics |
| Khả năng debug | Có thể khó do chain/proxy | Có thể đơn giản hơn | Thường đường gọi rõ hơn, tùy thiết kế |
| Bảo trì | Tốt khi concern xuyên suốt | Tốt khi giảm interception thừa | Tốt khi phạm vi nhỏ, kém nếu code lại phân tán |
| Tính di động triển khai | Phụ thuộc runtime instrument | Thay cơ chế có rủi ro mới | Có thể giảm lệ thuộc vào agent/weaving |
| Rủi ro hồi quy | Có baseline hiện tại | Pointcut/ordering thay đổi | Khả năng bỏ sót side effect và thứ tự |
| Kiểm chứng | Test nghiệp vụ + advice coverage | Test lại tập join point | Test policy tại mọi đường gọi thay thế |

Không bắt buộc dùng thang điểm số giả tạo. Viết rõ tiêu chí nào là **ràng buộc không được vi phạm** (ví dụ bảo mật, audit hoặc độ tin cậy giao dịch), tiêu chí nào là **mục tiêu tối ưu** (latency, bộ nhớ, startup) và tiêu chí nào là **chi phí bảo trì**.

## 12. Chiến lược chuyển đổi an toàn khi quyết định bỏ AOP

1. **Định nghĩa phạm vi:** concern nào và những join point nào sẽ thay thế, còn những aspect nào giữ lại.
2. **Chụp baseline chức năng:** tập input quan trọng, kết quả, lỗi, side effects, số lượt advice và thứ tự sự kiện.
3. **Tạo phương án tường minh:** decorator, helper, policy object hoặc workflow được chọn; code review thấy rõ nơi thực thi policy.
4. **Kiểm tra mọi caller:** lời gọi bên ngoài, nội bộ, callback, async, retry, chạy song song và đường lỗi theo đúng hợp đồng.
5. **Xác minh không còn hai cơ chế cùng xử lý:** nếu AOP vẫn bật trên wrapper mới, có thể audit/tracing lặp hai lần. Cần kế hoạch gỡ bỏ theo đúng phạm vi.
6. **So sánh chức năng tương đương:** kết quả, loại ngoại lệ, audit payload, thời điểm, transactional outcome, cleanup và bảo vệ dữ liệu.
7. **Đo lại:** CPU, throughput, allocation, p95/p99, GC, startup và số sự kiện. Không kết luận từ một mẫu chạy đơn lẻ.
8. **Có tiêu chí dừng và hoàn tác:** nếu mất policy bắt buộc, lỗi concurrency hoặc SLO xấu đi, chưa được coi thay đổi thành công.

Một số nơi có thể chuyển đổi dần theo từng loại operation để giảm phạm vi thử nghiệm, nhưng phải đặc biệt chú ý **đường chạy đồng thời của cơ chế cũ và mới**. Audit trùng hai lần cũng có thể là lỗi nghiệp vụ nghiêm trọng giống như thiếu một lần.

## 13. Những lỗi thường gặp khi đo và quyết định

1. **So direct không làm audit với AOP có audit:** đang so khác chức năng, không cô lập proxy overhead.
2. **Đếm số log rồi suy ra CPU:** số sự kiện không quy đổi trực tiếp sang ns/op hoặc allocation.
3. **Chỉ đo trung bình:** tail latency, GC pauses và contention có thể quyết định chất lượng hệ thống.
4. **Chạy benchmark không warm-up:** đo startup/JIT compilation lẫn với steady-state nếu mục tiêu là throughput nóng.
5. **Dead-code elimination hoặc constant folding:** benchmark có thể đo đường tối ưu khác thực tế nếu kết quả bị bỏ.
6. **Tắt logger trong một biến thể:** so khác công việc; cần so cùng cơ chế đầu ra khi muốn cô lập overhead.
7. **Không báo sai số:** chênh lệch nhỏ trong nhiễu đo không đủ căn cứ thay kiến trúc.
8. **Dùng benchmark trên một call site để kết luận mọi đường:** JVM JIT xử lý call site đa hình khác nhau.
9. **Tin rằng mọi AOP dùng reflection:** proxy gọi trực tiếp hoặc weaving có đặc điểm khác, phải đo đúng implementation.
10. **Coi pointcut match là đảm bảo mọi đường cần chặn:** lời gọi có thể đi vòng proxy hoặc không thuộc join point được hỗ trợ.
11. **Xóa audit để có kết quả nhanh:** gây mất chức năng nếu audit bắt buộc; cần phương án tương đương.
12. **Bỏ advice mà không tính thứ tự:** after/finally, error precedence và transaction semantics có thể đổi.
13. **Thay bằng helper copy ở nhiều nơi:** có thể tái tạo scattering/tangling và nguy cơ bỏ sót khi thêm method.
14. **Chỉ kiểm tra happy path:** không phát hiện audit success nhầm khi lỗi, thiếu cleanup hoặc retry nhiều lần.
15. **Bỏ qua chi phí di trú và bảo trì:** một tiết kiệm nhỏ có thể không bù rủi ro triển khai và khó review.
16. **Tối ưu khi chưa xác định nút thắt:** nếu bottleneck nằm ở I/O hay lock trong advice, đổi dispatch hiếm khi là giải pháp chính.
17. **Giữ AOP bất kể mức độ phức tạp:** khi chỉ còn một policy nhỏ ở một ranh giới rõ, code tường minh có thể tốt hơn.
18. **Giả định decorator luôn không có overhead:** decorator cũng thêm dispatch và logic; lợi ích phải dựa trên độ rõ ràng, semantics và số đo.

## 14. Bài tập cuối: báo cáo quyết định kiến trúc

Hãy viết một báo cáo ngắn về một concern đang dùng AOP theo mẫu sau, điền **số liệu đo thật của hệ thống**, không thay số giả định của bài học bằng “kết quả đo”:

```text
Concern:
Mục tiêu nghiệp vụ và chính sách bắt buộc:
Join point phải chặn:
Join point phải loại trừ:
Cơ chế AOP hiện dùng:
Test hồi quy đã chạy:
Trạng thái outcome / side effects / exception:
Benchmark (JDK, flags, workload, iterations, forks):
Baseline direct + same behavior:
AOP + same behavior:
Phần chênh lệch CPU/latency/allocated bytes:
p95/p99 dưới tải:
Bottleneck được profiler xác nhận:
Giải pháp thay thế được so sánh:
Rủi ro bỏ sót policy:
Quyết định: giữ / thu hẹp / đổi cơ chế / bỏ ở phạm vi này
Lý do và giới hạn của bằng chứng:
Kế hoạch triển khai, theo dõi và rollback:
```

**Tiêu chí đạt bài:**

- Có phép đo đúng đơn vị và có cách tái hiện.
- Tách chi phí interception khỏi chi phí công việc của advice.
- Có cả test tích cực, test phủ định và failure path.
- Có bằng chứng giữ hoặc chuyển giao đủ các chức năng bắt buộc.
- So sánh phương án thay thế bằng cùng tập chức năng.
- Quyết định được giải thích dựa trên workload và ràng buộc thật, không dựa vào cảm tính “AOP là tốt/xấu”.
- Không tự đưa kết quả số học minh họa thành kết luận về Java/JIT ở mọi môi trường.

## 15. Câu hỏi tự kiểm tra

**1. Khi AOP có audit còn direct không có audit, chênh lệch thời gian có phải là chi phí proxy không?** Không. Nó bao gồm cả chi phí chức năng audit. Muốn cô lập cơ chế phải so với phương án không AOP nhưng vẫn thực hiện audit tương đương.

**2. Giảm 95% số lượt advice có đồng nghĩa giảm 95% latency không?** Không. Có thể vẫn phải kiểm tra pointcut và đi qua proxy; logic nghiệp vụ và I/O còn chi phối tổng latency.

**3. Khi nào nên giữ AOP dù có overhead?** Khi concern thực sự xuyên suốt, độ bao phủ đúng, chi phí trong ngân sách và việc tập trung hóa policy mang lại giá trị bảo trì/độ tin cậy.

**4. Khi nào nên thay bằng decorator hoặc helper?** Khi phạm vi nhỏ, đường gọi rõ, cách tường minh dễ bảo trì hơn và test chứng minh cùng chức năng mà chi phí phù hợp.

**5. Tại sao phép thử hồi quy phải kiểm tra exception và số event?** Vì advice có thể vô tình nuốt lỗi, ghi audit success sai, nhân đôi event hoặc bỏ cleanup dù giá trị trả về ở happy path vẫn đúng.

**6. AOP trên hot path gây tăng allocation thì chắc chắn phải bỏ?** Không. Đầu tiên đo bằng profiler, thu hẹp pointcut, giảm cấp phát không cần thiết hoặc chọn cơ chế phù hợp; quyết định cuối còn phụ thuộc correctness và lợi ích bảo trì.

**7. Nếu handler không áp advice cho `quote()`, proxy có còn overhead không?** Có thể còn vì lời gọi vẫn đi qua handler/matcher. Muốn biết khoản thực phải đo đúng triển khai.

**8. Vì sao test qua chính đối tượng đã instrument quan trọng?** Test chỉ gọi trực tiếp target có thể không đi qua advice; khi đó code nghiệp vụ được phủ nhưng chính sách cross-cutting chưa được kiểm chứng.

**9. Có nên bỏ audit bắt buộc nếu nó gây tăng p99?** Không được bỏ chỉ để giảm latency. Cần tối ưu sink, đường ghi, cấu hình chính sách hoặc thiết kế khác vẫn bảo đảm yêu cầu audit và failure handling.

**10. Báo cáo quyết định nên dẫn chứng gì?** Tập join point mong đợi, kết quả test regression, benchmark có điều kiện tái hiện, profile CPU/allocation, p95/p99, so sánh phương án cùng semantics và các rủi ro triển khai.

**Kết luận thực hành:** AOP là một công cụ tổ chức chính sách xuyên suốt. Đánh giá nó bằng **độ đúng đắn, độ bao phủ và số liệu đo thật**. Nếu AOP giải quyết scattering/tangling mà chi phí chấp nhận được, nên giữ; nếu pointcut quá rộng, ưu tiên thu hẹp; nếu một concern đơn giản, không cần can thiệp động và phương án tường minh dễ kiểm chứng hơn, có thể bỏ AOP **tại phạm vi đó**. Mọi quyết định đều phải bảo toàn những hành vi mà người dùng và hệ thống phụ thuộc vào.

</details>

- [Quay lại đầu trang](#back-to-top)
