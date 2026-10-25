<a id="back-to-top"></a>

# Kiến trúc weaving

## Menu
- [Cách bộ dệt tìm điểm phù hợp với pointcut](#weaving-architecture-001)
- [Chèn hành vi vào mã nguồn, mã trung gian hoặc đường gọi](#weaving-architecture-002)
- [Phân biệt dệt tĩnh với can thiệp động lúc chạy](#weaving-architecture-003)
- [Đầu vào, đầu ra và thời điểm áp dụng aspect](#weaving-architecture-004)
- [Phạm vi dệt trong một mô-đun và giữa các mô-đun](#weaving-architecture-005)
- [Quan hệ giữa thứ tự xây dựng và kết quả dệt](#weaving-architecture-006)
- [Cấu hình phạm vi được dệt và phạm vi bị loại trừ](#weaving-architecture-007)
- [Mã đã dệt, thông tin gỡ lỗi và khả năng truy vết](#weaving-architecture-008)
- [Phân biệt lỗi biên dịch, lỗi dệt và lỗi thực thi](#weaving-architecture-009)
- [Dệt lặp và phát hiện hành vi bị chèn nhiều lần](#weaving-architecture-010)

## <a id="weaving-architecture-001">Cách bộ dệt tìm điểm phù hợp với pointcut</a>

<details>
<summary>Xem chi tiết</summary>

# Cách bộ dệt tìm điểm phù hợp với pointcut trong kiến trúc AOP

## 1. Đặt vấn đề: từ một quy tắc pointcut đến những chỗ advice thực sự chạy

Trong **Aspect-Oriented Programming (AOP)**, **pointcut** là quy tắc chọn những **join point** cần được áp dụng **advice**. Join point là điểm trong mô hình thực thi được cơ chế AOP hỗ trợ, chẳng hạn một lần gọi phương thức, thực thi phương thức hoặc truy cập trường dữ liệu, tùy công cụ.

**Weaver (bộ dệt)** là thành phần phối hợp mã ứng dụng với hành vi aspect. Một câu hỏi kiến trúc quan trọng là: **bằng cách nào bộ dệt biết một biểu thức pointcut áp dụng ở những nơi nào trong chương trình?**

Ví dụ một yêu cầu bằng ngôn ngữ tự nhiên:

> Đo thời gian của các thao tác thanh toán, chỉ khi số tiền vượt một ngưỡng, nhưng không đo thao tác chẩn đoán.

Yêu cầu này gồm hai loại thông tin:

- **Thông tin cấu trúc**: tên/chữ ký phương thức, loại join point, kiểu khai báo, vùng mã hoặc metadata. Nhiều phần có thể phân tích trước lúc chạy.
- **Thông tin từng lần chạy**: số tiền thực tế, đối tượng đích hoặc ngữ cảnh điều khiển. Phải đánh giá ở thời điểm thích hợp khi join point thực sự xảy ra.

Bộ dệt thường không “quét toàn bộ chương trình rồi chạy advice cho mọi thứ” một cách mù quáng. Nó phải xác định những **điểm có khả năng khớp**, biểu diễn hoặc chèn cách kiểm tra điều kiện cần thiết, và kết hợp advice vào các ranh giới mà công cụ **thực sự có thể can thiệp**.

**Phạm vi bài:** kiến trúc weaving và cơ chế chọn điểm trong AOP nói chung, từ nền tảng đến các tình huống nâng cao. Không có một thuật toán hay API duy nhất bắt buộc cho tất cả công cụ AOP. Những pipeline bên dưới là **mô hình khái niệm**, còn cách hiện thực phụ thuộc ngôn ngữ, compiler, bytecode, class loader, runtime và loại interception.

## 2. Các khái niệm cần phân biệt trước khi tìm điểm

| Khái niệm | Vai trò | Ví dụ |
| --- | --- | --- |
| **Aspect** | Mô-đun chứa logic của một mối quan tâm xuyên suốt | Kiểm toán thao tác |
| **Advice** | Đoạn hành vi được kết hợp tại điểm được chọn | Ghi trước/sau, bao quanh lời gọi |
| **Join point** | Điểm/sự kiện có thể can thiệp thuộc mô hình thực thi | Một lần thực thi `pay()` |
| **Join-point shadow** | Vị trí cấu trúc có thể phát sinh join point khi chạy trong những mô hình dùng khái niệm này | Phần thực thi `pay` hoặc một call site |
| **Pointcut** | Điều kiện chọn các join point | Chọn execution của phương thức thuộc nhóm thanh toán |
| **Static match** | Kết quả kiểm tra từ dữ liệu cấu trúc | Chữ ký có tên `pay`, số tham số phù hợp |
| **Dynamic guard/residue** | Điều kiện còn phải quyết định khi chạy | `amount > 1000` |
| **Weaving** | Kết hợp lời gọi/logic advice với chương trình | Biến đổi bytecode, biên dịch, hoặc cấu trúc chặn lời gọi |
| **Target/subject** | Mã, đối tượng hoặc thao tác được can thiệp | Dịch vụ xử lý thanh toán |

Từ **shadow** hữu ích vì một vị trí tĩnh có thể được đi qua nhiều lần:

```java
for (int i = 1; i <= 3; i++) {
    gateway.pay(i * 100L);
}
```

Đây là mảnh mã minh họa, giả định `gateway` đã được khai báo. Trong một mô hình hỗ trợ method-call join point, **một vị trí gọi** có thể phát sinh **ba lần xuất hiện động**. Nếu pointcut phụ thuộc giá trị số tiền, ba lần xuất hiện có thể nhận ba kết quả kiểm tra khác nhau.

Đừng đồng nhất “một vị trí mã khớp” với “advice chỉ chạy đúng một lần”.

## 3. Sơ đồ pipeline khái niệm của bộ dệt

Có thể hình dung quá trình bằng một chuỗi bước:

```text
Nguồn mã / bytecode / metadata      Khai báo aspect + pointcut
               |                               |
               v                               v
       Phân tích chương trình           Phân tích biểu thức
               |                               |
               +--------------+----------------+
                              |
                              v
                  Xây mô hình các điểm
                  có thể can thiệp (shadows)
                              |
                              v
                Khớp điều kiện tĩnh
                    /       |       \
                  NO       MAYBE     YES
                   |         |        |
                bỏ qua       |        |
                             v        v
                     Chuẩn bị guard /
                       binding runtime
                              |
                              v
                  Kết hợp advice / tạo
                   đường interception
                              |
                              v
                  Chương trình thực thi
                              |
                              v
               Guard động (nếu cần)
                  /             \
               không khớp       khớp
                  |               |
            tiếp tục thường   áp dụng advice
```

**YES / NO / MAYBE chỉ là mô hình suy luận minh họa**, không phải tên trạng thái hoặc giao diện bắt buộc trong mọi bộ dệt. Một engine có thể có nhiều tầng phân tích, dùng tối ưu compiler, dùng cấu trúc nội bộ khác hoặc trì hoãn một phần công việc đến runtime.

Mỗi bước giải quyết một câu hỏi:

1. **Pointcut muốn chọn gì?**
2. **Mã nào có những điểm can thiệp tương ứng?**
3. **Đặc điểm tĩnh nào đủ để loại bỏ hoặc xác nhận điểm?**
4. **Còn điều kiện nào phải đánh giá theo invocation?**
5. **Advice được kết hợp vào đường thực thi bằng cách nào?**
6. **Khi chạy, điểm đó có thực sự xuất hiện và thỏa điều kiện không?**

Nếu hiểu sáu câu hỏi này, người học có thể phân tích cả cơ chế weaving tĩnh và cơ chế chặn lời gọi bằng đối tượng trung gian mà không nhầm phạm vi hỗ trợ.

## 4. Bước 1 — Phân tích cú pháp và biểu diễn pointcut

Một pointcut được viết ở dạng văn bản, annotation hoặc cấu trúc cấu hình nào đó. Trước khi so khớp, bộ máy cần hiểu **ý nghĩa** của biểu thức đó.

Ví dụ dưới đây là **mã giả trung lập công cụ**, không phải cú pháp DSL bắt buộc:

```text
pointcut auditedOperations =
    executionOf(PaymentService.pay)
    OR executionOf(RefundService.refund)

pointcut largeAuditedOperations =
    auditedOperations
    AND runtimeAmountGreaterThan(1000)
```

Một biểu thức như vậy có thể được diễn giải thành **cây biểu thức (abstract syntax tree — AST)**:

```text
AND
├── OR
│   ├── EXECUTION(signature: PaymentService.pay)
│   └── EXECUTION(signature: RefundService.refund)
└── RUNTIME_GUARD(amount > 1000)
```

Để sử dụng AST, một bộ dệt có thể phải:

- Phân tích token và cấu trúc ngoặc/toán tử.
- Kiểm tra biểu thức có hợp cú pháp không.
- Giải quyết tên kiểu và thành viên về các thực thể của chương trình.
- Kiểm tra tính tương thích giữa designator và loại join point.
- Xác định biến được bind từ ngữ cảnh join point, nếu DSL có binding.
- Biểu diễn biểu thức dưới dạng thuận lợi cho tối ưu và so khớp.

Ví dụ, một điều kiện đòi **đối tượng đích** có thể không có ý nghĩa tại loại join point không mang đối tượng tương ứng. Một trình kiểm tra tốt nên phát hiện các tổ hợp không hợp lệ hoặc xử lý chúng theo quy tắc đã định.

### 4.1. Tên phương thức không phải định danh đầy đủ

Trong Java, `pay(String)` và `pay(String,long)` là overload khác nhau. Mẫu chứa tên `pay` và wildcard tham số có thể chọn cả hai, nhưng một mẫu ràng buộc `(String,long)` có phạm vi hẹp hơn.

Tương tự, kiểu khai báo, phạm vi package, modifier, kiểu trả về và quan hệ kế thừa có thể là một phần của dữ liệu so khớp. Bộ dệt không nên coi mọi method có cùng tên là cùng một điểm.

### 4.2. Cú pháp công cụ không đồng nhất

Một số DSL AOP có toán tử logic, mẫu tên/kiểu, điều kiện ngữ cảnh và pointcut được đặt tên. Một số API interception chỉ cho phép danh sách phương thức hoặc annotation. **Khả năng biểu diễn pointcut phụ thuộc công cụ**: không thể đưa một biểu thức từ DSL này sang DSL khác và cho rằng cú pháp lẫn ý nghĩa giữ nguyên.

Điều quan trọng ở mức kiến trúc là bước **biến khai báo chọn điểm thành một biểu diễn có thể kiểm tra**.

## 5. Bước 2 — Xây danh mục điểm có khả năng can thiệp

Sau khi hiểu pointcut, bộ dệt phải biết **các điểm join point mà chương trình và công cụ có thể tạo ra**.

Tùy thời điểm và dạng weaving, việc này có thể dựa trên:

- **AST nguồn:** compiler nhìn thấy định nghĩa hàm, biểu thức gọi, trường, constructor và vị trí handler.
- **Bytecode/trung gian:** thao tác đọc instruction, method descriptor, thông tin class và các metadata còn giữ sau biên dịch.
- **Reflection hoặc metadata runtime:** bộ chặn xét những method mà đối tượng trung gian biết cách chuyển tiếp.
- **Cấu trúc runtime riêng:** trình thông dịch hoặc runtime AOP có thể đưa join point vào mô hình thực thi của nó.

Không phải tất cả nguồn này có cùng khả năng quan sát.

### 5.1. Ví dụ hai loại điểm khác nhau

```java
class Checkout {
    void run(Gateway gateway) {
        gateway.charge(2000L);
    }
}

class Gateway {
    void charge(long amount) {
        System.out.println(amount);
    }
}
```

Đây là đoạn code minh họa, chưa có `main`. Có ít nhất hai góc nhìn nếu công cụ hỗ trợ:

1. **Method-call shadow:** vị trí biểu thức `gateway.charge(2000L)` trong `Checkout.run`.
2. **Method-execution shadow:** phần thực thi của `Gateway.charge(long)`.

Một bộ dệt chọn `call` có thể phải xem **những vị trí lời gọi**, còn một bộ dệt chọn `execution` quan sát **ranh giới thực thi thân phương thức**.

Nếu chỉ dùng proxy giao diện, công cụ thường chỉ chặn được **những lời gọi đi qua proxy**. Nó không tự tìm và sửa mọi bytecode method-call site như một bộ dệt bytecode đầy đủ.

### 5.2. “Ứng viên” không có nghĩa đã khớp

Nếu chương trình có 10.000 method executions và pointcut chỉ chọn `PaymentService.pay`, 9.999 method khác có thể bị loại ở các bước đầu. Trong thực tế, compiler và index có thể dùng tên kiểu/phương thức để giảm số điểm cần xem xét, thay vì kiểm tra mọi chi tiết của mọi instruction theo một thứ tự cố định.

Tập ứng viên do mô hình join point quyết định trước khi pointcut kiểm tra sâu. Một công cụ không hỗ trợ field-read join point sẽ không thể tự tạo interception cho mọi lần đọc field chỉ nhờ biểu thức pointcut có từ “field”.

## 6. Bước 3 — So khớp tĩnh: loại nhanh điểm không phù hợp

Một phần lớn pointcut có thể kiểm tra bằng metadata không phụ thuộc dữ liệu từng invocation:

| Điều kiện tĩnh | Dữ liệu có thể dùng để so khớp |
| --- | --- |
| Loại join point | Method call, method execution, constructor execution… |
| Tên/chữ ký | Tên, số lượng và kiểu tham số, kiểu trả về |
| Kiểu khai báo | Class/interface, quan hệ kế thừa phù hợp |
| Modifier | `public`, `private`, `static`… nếu loại mẫu hỗ trợ |
| Package/vùng mã | Namespace, declaring type, vị trí từ vựng |
| Annotation/metadata | Dấu đánh dấu đã có ở cấu trúc mã phù hợp |
| Vị trí mã nguồn | Tên file/dòng, nếu công cụ giữ và hỗ trợ |
| Ngữ cảnh mã bao quanh | Phương thức hay kiểu chứa call site |

Ví dụ pointcut theo ý nghĩa:

```text
kind == METHOD_EXECUTION
AND declaringType belongsTo package "example.payment"
AND methodName startsWith "charge"
AND parameterTypes == [String, long]
```

Đây là mã giả; với một method có chữ ký `charge(String,long)` trong đúng vùng kiểu, các điều kiện tĩnh có thể xác nhận khả năng khớp mà không cần biết `String id` và `long amount` đang chứa giá trị gì.

Một method `healthCheck()` có thể bị loại ngay theo tên. Một method `charge(String)` có thể bị loại theo số và kiểu tham số.

### 6.1. Ý nghĩa của so khớp tĩnh

Để giảm chi phí runtime, bộ dệt cố gắng:

- **Loại (definitely no):** vị trí không thể thỏa.
- **Xác nhận đủ bằng metadata (definitely yes):** không còn điều kiện động tại điểm đó.
- **Giữ làm ứng viên động (maybe):** vị trí đúng loại/chữ ký nhưng kết quả còn phụ thuộc dữ liệu runtime.

Một số công cụ có thể dùng phân tích khác, không phơi bày trạng thái “maybe”. Nhưng cách suy luận “biết chắc / không thể / cần kiểm tra khi chạy” là rất hữu ích.

### 6.2. So khớp static không phải thực thi advice

Nếu method thuộc tập khớp tĩnh nhưng chương trình không bao giờ gọi nó, sẽ không có lần chạy advice tương ứng. Nếu điểm chạy nhiều lần, advice có thể chạy nhiều lần. Nếu còn điều kiện động, một số lần có thể bị loại.

## 7. Bước 4 — Giữ lại điều kiện động cho runtime

Ví dụ chọn tất cả method execution `transfer(String,long)` với số tiền lớn hơn 1000.

Chữ ký là phần tĩnh. Nhưng biểu thức `amount > 1000` chỉ quyết định được ở từng lần gọi. Một bộ dệt có thể biểu diễn phần này thành **runtime guard** hay **dynamic residue**:

```text
Tĩnh:
    method == TransferService.transfer(String,long)

Động tại mỗi invocation:
    amount > 1000

Khi guard true:
    chạy advice

Khi guard false:
    bỏ qua advice này; thao tác được tiếp tục theo cơ chế thực thi
```

Điểm cần nhấn mạnh:

- Runtime guard có thể đọc tham số, kiểu đối tượng, ngữ cảnh luồng điều khiển hoặc trạng thái liên quan.
- Guard không nhất thiết là một lời gọi Java tách biệt; nó có thể được biên dịch thành nhánh điều kiện hoặc được interceptor đánh giá.
- Một biểu thức có thể được rút gọn nếu bộ dệt chứng minh được điều kiện luôn đúng hoặc luôn sai ở một shadow cụ thể.
- Nếu guard chứa công việc quá đắt, chi phí có thể xuất hiện ở **mọi lần kiểm tra**, ngay cả khi advice hiếm khi chạy.

### 7.1. Đừng nhầm kiểu đối số với giá trị đối số

Điều kiện “tham số thứ hai có kiểu `long`” có thể kiểm tra từ chữ ký. Điều kiện “giá trị tham số thứ hai lớn hơn 1000” phải quan sát số tiền thực tế. Không thể dùng wildcard kiểu tham số để biểu diễn ngữ nghĩa so sánh giá trị.

### 7.2. Điều kiện theo kiểu runtime

Trong chương trình đa hình:

```java
PaymentGateway gateway = obtainGateway();
gateway.charge(1500L);
```

Đây là đoạn minh họa với biến và hàm giả định. Kiểu khai báo tại call site là `PaymentGateway`, còn đối tượng thực có thể thuộc một class triển khai. Nếu pointcut phụ thuộc **kiểu thực tế của đối tượng**, bộ dệt có thể phải để lại kiểm tra runtime.

Cần biết cụ thể designator trong công cụ xét **kiểu tĩnh**, **declaring type** hay **runtime target type**. Các điều kiện có tên giống nhau giữa công cụ khác nhau chưa chắc mang nghĩa giống nhau.

## 8. Bước 5 — Kết hợp advice vào đường thực thi

Sau khi xác định các shadow được chọn hoặc có khả năng khớp, bộ dệt phải tạo một đường thực thi mà advice có thể chạy.

Tùy kiến trúc, nó có thể:

1. **Chèn trực tiếp các lệnh bổ sung** trước/sau một điểm vào mã hoặc bytecode.
2. **Tạo hàm/đoạn mã trung gian** để chuyển tiếp qua advice.
3. **Tạo proxy/interceptor** nhận lời gọi trước khi chuyển cho target.
4. **Kết hợp metadata và handler** để runtime quyết định gọi advice tại thời điểm xảy ra sự kiện.

Với advice có điều kiện động, phần chèn có thể mang dạng tư duy:

```text
operation(args):
    if dynamicGuard(args):
        runAdviceAround(operation, args)
    else:
        runOriginalOperation(args)
```

Đây là **mã giả khái niệm**, không có nghĩa mọi bộ dệt đều nhân đôi thân method hay sửa trực tiếp lời gọi ở một dạng duy nhất. Trong thực tế cần xử lý return value, ngoại lệ, stack, local variables và ngữ nghĩa ngôn ngữ.

### 8.1. Around advice và continuation

Một quanh-advice có thể dùng `proceed` để tiếp tục, nhận kết quả hoặc ngoại lệ. Bộ dệt phải bảo đảm đường continuation được nối đúng với điểm đã chọn. Ví dụ mã giả:

```text
around timing(operation):
    started = monotonicClock()
    try:
        return proceed(operation)
    finally:
        recordDuration(monotonicClock() - started)
```

Nếu có nhiều advice cùng khớp, “phần được tiếp tục” có thể gồm **advice khác** và phần gốc. Việc weaving phải giữ thứ tự hiệu lực theo cơ chế ưu tiên đã định. Không thể coi `proceed` luôn là lời gọi trực tiếp tới target.

### 8.2. Điều gì xảy ra nếu nhiều pointcut cùng khớp?

Một shadow có thể được chọn bởi pointcut ghi audit và pointcut đo thời gian. Bộ dệt phải kết hợp các advice có thể được áp dụng theo **thứ tự** phù hợp. Ví dụ ý niệm:

```text
AuditAround(
    TimingAround(
        BusinessOperation
    )
)
```

Đổi thứ tự có thể làm khác hành vi khi một advice từ chối hoặc ném lỗi. **Thứ tự không nên suy ra từ thứ tự tên file**; cần xác minh ngữ nghĩa precedence của công cụ.

## 9. Ba nơi có thể diễn ra việc tìm điểm và dệt advice

### 9.1. Weaving khi biên dịch

Khi compiler hoặc bộ dệt nhận mã nguồn/biểu diễn trung gian, nó có thể phân tích cấu trúc chương trình, tìm shadow, so khớp pointcut và tạo mã đã kết hợp advice.

Ưu điểm có thể có:

- Thông tin kiểu, cấu trúc và một số metadata thuận lợi cho matching.
- Nhiều loại điểm can thiệp hơn một proxy chỉ chặn lời gọi ngoài.
- Có cơ hội tối ưu các điều kiện tĩnh.

Đánh đổi:

- Quy trình build cần bước weaving rõ ràng.
- Phạm vi mã được build/weave phải được kiểm soát.
- Chẩn đoán cần công cụ xem mã hoặc báo cáo weaving.

### 9.2. Weaving khi nạp mã

Một bộ biến đổi có thể can thiệp khi lớp/thành phần được nạp vào runtime. Nó phân tích bytecode/metadata lúc đó rồi chèn cơ chế advice tương ứng.

Ưu điểm có thể có: không cần thay đổi nguồn cho mọi thành phần có thể weaving. Đổi lại phải xét thời điểm lớp được nạp, class loader, khả năng biến đổi, module boundary và các giới hạn của môi trường.

**Không được tự giả định** một lớp đã nạp trước khi bộ dệt được kích hoạt sẽ nhận cùng kết quả với lớp được nạp sau. Điều đó phụ thuộc khả năng tái biến đổi và cấu hình của công cụ.

### 9.3. Interception tại runtime

Một proxy hoặc dispatcher có thể chọn method mà nó quản lý, rồi quyết định chạy advice dựa trên metadata và ngữ cảnh động.

Ưu điểm: dễ minh họa, không nhất thiết sửa bytecode phần gốc. Giới hạn thường gặp:

- Chỉ nhìn thấy lời gọi đi **qua trung gian**.
- Một lời gọi trực tiếp tới target có thể không bị chặn.
- **Self-invocation** bên trong target có thể bỏ qua proxy.
- Không tự biến mọi field access, constructor hay call site thành join point.

**Đây là khác biệt về khả năng triển khai, không phải luật rằng tất cả AOP đều bỏ qua lời gọi nội bộ.**

## 10. Ví dụ Java 17: mô phỏng tìm ứng viên tĩnh và guard động

Để quan sát được hai bước khác nhau, ta xây một chương trình **Java thuần**:

1. Quét các phương thức của một giao diện bằng reflection.
2. Chọn method theo tên, kiểu trả về và kiểu tham số (**bộ lọc tĩnh**).
3. Dùng `Proxy` chuyển tiếp cuộc gọi qua interceptor.
4. Chạy guard `amount >= 1000` (**điều kiện động**) trước khi ghi advice.
5. Gọi target và xử lý kết quả/ngoại lệ.

**Lưu ý giới hạn:** đây là bộ mô phỏng quy trình matching ở mức phương thức giao diện. Nó **không phải bộ dệt AOP đầy đủ**, không tạo join point shadow cho mọi call site, không biến đổi bytecode, và việc chọn method theo reflection không chứng minh hỗ trợ mọi loại join point.

Tạo file `WeaverMatchDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class WeaverMatchDemo {

    interface Operations {
        String checkout(String id, long amount);
        String refund(String id, long amount);
        String ping();
    }

    static final class DefaultOperations implements Operations {
        @Override
        public String checkout(String id, long amount) {
            System.out.println("business:checkout:" + id + ":" + amount);
            return "checkout:" + id;
        }

        @Override
        public String refund(String id, long amount) {
            System.out.println("business:refund:" + id + ":" + amount);
            if ("ERR".equals(id)) {
                throw new IllegalStateException("refund rejected");
            }
            return "refund:" + id;
        }

        @Override
        public String ping() {
            System.out.println("business:ping");
            return "pong";
        }
    }

    static boolean staticMatches(Method method) {
        if (method.getReturnType() != String.class) {
            return false;
        }

        Class<?>[] types = method.getParameterTypes();
        if (types.length != 2
                || types[0] != String.class
                || types[1] != long.class) {
            return false;
        }

        return method.getName().equals("checkout")
                || method.getName().equals("refund");
    }

    public static void main(String[] args) {
        Method[] methods = Operations.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));

        Set<Method> selected = new HashSet<>();
        for (Method method : methods) {
            if (staticMatches(method)) {
                selected.add(method);
                System.out.println("scan:match:" + method.getName());
            } else {
                System.out.println("scan:skip:" + method.getName());
            }
        }

        Operations target = new DefaultOperations();
        InvocationHandler handler = (proxy, method, values) -> {
            boolean staticSelected = selected.contains(method);
            boolean dynamicAllowed = staticSelected
                    && values != null
                    && (Long) values[1] >= 1000L;

            if (dynamicAllowed) {
                System.out.println("advice:before:" + method.getName());
            }

            try {
                Object result = method.invoke(target, values);
                if (dynamicAllowed) {
                    System.out.println("advice:after:" + method.getName());
                }
                return result;
            } catch (InvocationTargetException wrapped) {
                Throwable original = wrapped.getCause();
                if (dynamicAllowed) {
                    System.out.println("advice:error:" + method.getName()
                            + ":" + original.getClass().getSimpleName());
                }
                throw original;
            }
        };

        Operations proxy = (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] { Operations.class },
                handler
        );

        System.out.println("caller:" + proxy.checkout("A", 1500));
        System.out.println("caller:" + proxy.checkout("B", 50));
        System.out.println("caller:" + proxy.refund("C", 2000));

        try {
            proxy.refund("ERR", 3000);
        } catch (IllegalStateException error) {
            System.out.println("caller:error:" + error.getMessage());
        }

        System.out.println("caller:" + proxy.ping());
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac WeaverMatchDemo.java
java WeaverMatchDemo
```

Kết quả dự kiến:

```text
scan:match:checkout
scan:skip:ping
scan:match:refund
advice:before:checkout
business:checkout:A:1500
advice:after:checkout
caller:checkout:A
business:checkout:B:50
caller:checkout:B
advice:before:refund
business:refund:C:2000
advice:after:refund
caller:refund:C
advice:before:refund
business:refund:ERR:3000
advice:error:refund:IllegalStateException
caller:error:refund rejected
business:ping
caller:pong
```

### 10.1. Hai tầng matching được thể hiện thế nào?

**Tầng tĩnh:**

- `checkout` và `refund` có đúng tên thuộc danh sách, return type `String` và hai tham số `String,long` nên được thêm vào `selected`.
- `ping` không có hai tham số, nên bị loại.
- Danh sách được sort để đầu ra `scan` có thứ tự xác định.

**Tầng động:**

- `checkout("A",1500)` và `refund("C",2000)` thỏa `amount >= 1000` nên có advice trước/sau.
- `checkout("B",50)` khớp tĩnh nhưng guard động sai, nên phần nghiệp vụ vẫn thực thi mà không có log advice.
- `refund("ERR",3000)` thỏa cả hai tầng nhưng target ném lỗi; interceptor gỡ `InvocationTargetException` của reflection để ghi/lan truyền `IllegalStateException` gốc.
- `ping()` không khớp tĩnh, nên handler không truy cập `values[1]` và tránh lỗi chỉ số. Đây là hệ quả trực tiếp của việc dùng `&&` ngắn mạch trong **mã Java mô phỏng**, không phải cam kết về thứ tự đánh giá của mọi DSL pointcut.

### 10.2. Mô phỏng khác weaver thật ở đâu?

- Reflection trên giao diện không quét các **vị trí gọi** trong mã ứng dụng.
- `HashSet<Method>` chỉ là bộ nhớ kết quả lọc, không phải cấu trúc shadow của một compiler AOP.
- Proxy chỉ thấy những lời gọi **đi qua proxy**; nó không can thiệp vào mọi execution/call trong runtime.
- Quy tắc `staticMatches` là logic Java thủ công, không phải parser ngôn ngữ pointcut.
- Một bộ dệt bytecode có thể đưa guard và advice vào những chỗ khác hoặc tối ưu chúng khác.

Điều rút ra là **hai giai đoạn tư duy**: loại những điểm chắc chắn không phù hợp bằng metadata, và chỉ kiểm tra điều kiện runtime trên các đường còn có khả năng khớp.

## 11. Các tối ưu quan trọng của quá trình tìm điểm

Một bộ dệt có thể giảm chi phí bằng nhiều kỹ thuật:

### 11.1. Index theo kiểu, tên hoặc signature

Nếu pointcut chọn những method trong `example.payment`, không nhất thiết phải xem chi tiết mọi method ở các package không liên quan. Compiler hoặc bộ dệt có thể dùng bảng ký hiệu, index lớp hoặc metadata để rút gọn tập ứng viên.

### 11.2. Constant folding và loại shadow không thể khớp

Nếu một phần pointcut mâu thuẫn với signature của shadow, bộ dệt có thể bỏ shadow ngay. Ví dụ yêu cầu tham số đầu `String` sẽ không khớp một method có tham số đầu `int` theo điều kiện chữ ký tĩnh tương ứng.

### 11.3. Trì hoãn điều kiện chỉ biết ở runtime

Điều kiện theo giá trị tham số không thể quyết định từ signature. Giữ lại **guard nhỏ nhất cần thiết** ở runtime có thể giảm chi phí hơn so với chạy toàn bộ biểu thức hoặc advice cho mọi invocation.

### 11.4. Tái sử dụng dữ liệu tĩnh

Thông tin signature và kiểu có thể được giữ hoặc tái sử dụng theo điểm, thay vì tạo chuỗi mô tả và đối tượng phản xạ mới ở từng lời gọi, nếu thiết kế và API cho phép.

### 11.5. Tránh điều kiện động có side effect

Nếu biểu thức động gọi mạng hoặc sửa dữ liệu nghiệp vụ chỉ để quyết định khớp, việc tối ưu hoặc đánh giá lại có thể làm hệ thống khó dự đoán. Nên giữ điều kiện chọn điểm **thuần, đơn giản và có hợp đồng rõ**, rồi đưa hành vi phụ trợ cần thiết vào advice.

**Quan trọng:** không có bảo đảm mọi công cụ sử dụng những tối ưu trên. Cách tối ưu phải giữ ngữ nghĩa được công cụ cam kết; không nên viết pointcut phụ thuộc vào một thứ tự kiểm tra mà ngôn ngữ không bảo đảm.

## 12. Những trường hợp khiến matching khó hơn tên phương thức

### 12.1. Overload

```java
void send(String message);
void send(String message, int priority);
```

Hai method có cùng tên nhưng khác signature. Mẫu chọn `send(..)` (trong một DSL hỗ trợ ký hiệu tương ứng) có thể chọn cả hai; mẫu yêu cầu đúng `(String,int)` hẹp hơn.

### 12.2. Override và dispatch động

Một biến kiểu giao diện có thể trỏ đến nhiều triển khai. Thông tin tại **method-call site** và thông tin về **phần thân thực thi thực tế** không luôn giống nhau.

Một bộ dệt phải tôn trọng cách ngôn ngữ giải quyết lời gọi, kế thừa, overriding và dynamic dispatch. Không thể chỉ xem tên lớp khai báo tại chỗ gọi rồi chắc chắn biết mọi method body sẽ chạy.

### 12.3. Bridge và synthetic methods

Compiler Java có thể tạo phương thức bridge/synthetic để duy trì ngữ nghĩa generics và đa hình. Ở mức bytecode, tập các method mà bộ dệt gặp có thể nhiều hơn những khai báo người lập trình trực tiếp nhìn thấy.

Cần biết công cụ chọn/loại những method này ra sao để tránh áp advice hai lần hoặc bỏ sót đường thực thi. **Không có quy tắc chung** rằng mọi công cụ đều xử lý chúng y hệt nhau.

### 12.4. Annotation ở interface và implementation

Một method được đánh dấu ở giao diện, lớp cha hoặc implementation có thể hiện ra khác nhau khi phản xạ hoặc khi matching bytecode. Không nên mặc định dấu được kế thừa đến mọi override, đặc biệt với annotation phương thức của Java.

Muốn chọn theo metadata, cần xác định rõ **đọc annotation ở thành phần nào** và kiểm thử khi có override/interface.

### 12.5. Constructor, field và exception handler

Nếu công cụ hỗ trợ những loại join point này, nó phải tìm các vị trí/bytecode tương ứng và áp dụng ngữ nghĩa matching khác method execution. Một số trường hợp như việc **ném ngoại lệ** và việc **đi vào exception handler** là hai sự kiện khác nhau. Không thể suy rằng hỗ trợ handler join point nghĩa là chặn được mọi lệnh `throw`.

## 13. Phạm vi control flow và điều kiện theo ngữ cảnh

Một số mô hình AOP có pointcut phụ thuộc **luồng điều khiển động**: chỉ áp advice khi đang thực thi trong một thao tác cha.

```text
runBatch()
  |
  +-- stepA()
  |     |
  |     +-- save()
  |
  +-- stepB()
        |
        +-- save()

interactiveRequest() -> save()
```

Nếu mục tiêu là chỉ can thiệp `save` khi đang trong luồng `runBatch`, không thể suy từ **chữ ký của save** để biết điểm xuất hiện thuộc batch hay tương tác độc lập.

Bộ dệt có thể chuẩn bị cơ chế theo dõi entry/exit của phạm vi điều khiển và để lại điều kiện runtime cho các join point bên trong, nếu công cụ có tính năng tương ứng.

Các giới hạn:

- Điều kiện theo call stack đồng bộ không tự động lan sang worker thread bất đồng bộ.
- Gọi lồng nhau và đệ quy cần quản lý đúng độ sâu/vòng đời context.
- Ngoại lệ phải được xử lý sao cho trạng thái theo dõi không bị kẹt “đang trong batch”.
- Giữ ngữ cảnh toàn cục không phân biệt invocation có thể gây can thiệp sai giữa các request.

Chi phí của một guard theo control flow có thể khác phép so sánh tham số đơn giản; cần đo dưới tải phù hợp.

## 14. Đúng tên nhưng không thấy advice: phân tích theo từng tầng

Khi một method tưởng như khớp pointcut nhưng advice không chạy, có nhiều nguyên nhân độc lập:

| Tầng | Vấn đề có thể xảy ra |
| --- | --- |
| Phân tích cú pháp | Biểu thức sai ngữ pháp hoặc độ ưu tiên toán tử |
| Giải quyết kiểu | Tên kiểu hoặc method signature không đúng |
| Mô hình join point | Công cụ không hỗ trợ loại điểm được yêu cầu |
| Phạm vi quét | Lớp hoặc thư viện không nằm trong vùng weaving |
| Matching tĩnh | Modifier, tham số, package hoặc metadata không khớp |
| Matching động | Args, runtime type, control flow hoặc trạng thái không thỏa |
| Đường interception | Lời gọi bỏ qua proxy, xảy ra trên target trực tiếp |
| Chuỗi advice | Advice ngoài ngăn tiếp tục tới advice trong hoặc body |
| Thời điểm nạp mã | Lớp đã nạp hoặc đã được xử lý theo cơ chế khác |
| Quan sát | Logging của advice lỗi hoặc trace không bao gồm đường đó |

Cách điều tra hiệu quả là đi **từ trên xuống**, không nới wildcard thật rộng ngay khi không thấy log. Nới quá rộng có thể tạo tác động ngoài ý muốn mà vẫn không giải quyết vấn đề phạm vi weaving.

### 14.1. Tập matching tĩnh khác số lượt advice chạy

Một build có thể báo “12 join-point shadows matched”, nhưng trong một ca kiểm thử chỉ có ba trong số đó được thực thi. Nếu còn guard động, số lượt advice chạy có thể ít hơn số lượt invocation tại các shadow đó.

Nên báo riêng ba số liệu:

1. **Shadows tĩnh được chọn hoặc có khả năng khớp**.
2. **Số lần đi qua các điểm ứng viên**.
3. **Số lần guard cho phép advice thực sự chạy**.

Đây là ba đại lượng khác nhau.

## 15. Tác động đến correctness: weaving không được phá hợp đồng gốc

Bộ dệt cần giữ đúng ngữ nghĩa chương trình trừ những thay đổi mà advice chủ động cho phép. Những điểm cần bảo toàn thường bao gồm:

- **Thứ tự đánh giá** các biểu thức ban đầu.
- **Tham số** và đối tượng đích.
- **Giá trị trả về**, kể cả primitive, reference và `void`.
- **Ngoại lệ**, cause và hành vi unwinding.
- **Ranh giới đồng bộ**, monitor/lock và finally.
- **Đệ quy** và các lời gọi lồng nhau.
- **Tính nhìn thấy/kiểu hợp lệ** theo quy tắc compiler/runtime.
- **Trạng thái xác minh bytecode** đối với những môi trường yêu cầu kiểm tra bytecode.

Một lỗi weaving có thể là lỗi kỹ thuật trước khi advice chạy; một lỗi trong advice cũng có thể gây thay đổi kết quả/ngoại lệ của caller. Cần phân biệt hai nguồn này khi chẩn đoán.

### 15.1. Không hiểu “weaving” là sao chép nguyên văn lệnh advice ở mọi nơi

Công cụ có thể sinh cầu nối, gọi handler, kết hợp closure/continuation hoặc chèn câu lệnh có điều kiện. Hình thức mã sinh ra khác nhau nhưng phải tương thích mô hình semantics đã công bố.

### 15.2. Khi advice được cho phép thay đổi luồng

Một around advice có thể không gọi `proceed`, trả fallback hoặc gọi tiếp nhiều lần nếu ngôn ngữ cho phép. Đó là thay đổi **có chủ đích của advice**, không được nhầm với lỗi weaver. Với thao tác có side effect, quyền này phải được quản lý và kiểm thử kỹ.

## 16. Hiệu năng của quá trình matching và đường đã weave

Chi phí có thể nằm ở hai giai đoạn:

**Chi phí trước khi chạy hoặc lúc nạp:**

- Parse biểu thức.
- Giải quyết kiểu, kiểm tra cú pháp/ngữ nghĩa.
- Duyệt mã và xây shadow.
- So khớp static, tối ưu và sinh mã.
- Biến đổi hoặc kiểm tra bytecode.

**Chi phí mỗi invocation:**

- Đi qua interceptor hoặc nhánh đã weave.
- Lấy ngữ cảnh runtime.
- Đánh giá guard động.
- Chạy advice.
- Tạo dữ liệu log/trace và side effect khác.

Mô hình gần đúng:

```text
Tổng chi phí vận hành =
    chi phí phân tích/weaving phân bổ theo vòng đời
  + số invocation qua ứng viên * chi phí kiểm tra/interception
  + số invocation khớp động * chi phí advice
  + ảnh hưởng cấp phát, I/O, đồng bộ, GC, cache
```

Đây là sơ đồ tư duy, không phải công thức benchmark chính xác. Một công cụ có thể tối ưu các bước thành các instruction đơn giản; một lời gọi mạng trong advice có thể lấn át toàn bộ overhead matching.

### 16.1. Điều kiện tĩnh nên loại nhiều điểm càng sớm càng tốt?

Về nguyên tắc, loại sớm điểm chắc chắn không khớp giúp giảm chi phí runtime. Nhưng không nên thay đổi **ngữ nghĩa** pointcut chỉ để cải thiện số đo. Nếu yêu cầu cần giá trị runtime, không thể “cache kết quả matching” chỉ theo method name cho mọi invocation.

### 16.2. Cách đo phù hợp

Tách biến thể baseline, có instrumentation nhưng guard false, guard true với advice nhẹ và advice thực. Dùng workload đại diện, warm-up hợp lý và đo throughput/latency/allocation; tránh kết luận từ vài lần chạy `System.nanoTime()` hay từ ví dụ có `System.out.println`.

## 17. Chiến lược kiểm thử để chứng minh bộ dệt đã tìm đúng điểm

Một bộ kiểm thử tốt cần cả ca **dương tính** và **âm tính**.

### 17.1. Kiểm thử matching tĩnh

Dựng các method gần giống nhau:

```java
void charge(String id);
void charge(String id, long amount);
void charge(int number, long amount);
void chargePreview(String id, long amount);
```

Đây là danh sách chữ ký minh họa, chưa phải class Java độc lập.

Nếu pointcut chỉ chọn `charge(String,long)`, cần xác minh:

- Phương thức đúng chữ ký được chọn.
- Overload một tham số không được chọn.
- Overload có tham số đầu `int` không được chọn.
- `chargePreview` không được chọn nếu mẫu yêu cầu tên chính xác.

### 17.2. Kiểm thử điều kiện động

Trên cùng một method, gọi với `amount=999`, `1000` và `1001` nếu guard quy định `amount > 1000`. Kiểm tra chính xác ranh giới lớn hơn hay lớn hơn/bằng, kèm dữ liệu null, kiểu runtime và ngữ cảnh control flow nếu áp dụng.

### 17.3. Kiểm thử loại join point

Một method được gọi từ hai nơi: nếu pointcut là **call**, kiểm tra hai call sites; nếu là **execution**, kiểm tra thân được thực thi. Không dùng kết quả ở một loại điểm để kết luận engine hỗ trợ loại còn lại.

### 17.4. Kiểm thử đường trực tiếp và lời gọi nội bộ

Nếu hệ thống dùng proxy, kiểm tra lời gọi qua proxy, trực tiếp tới target và gọi từ chính target. Nếu dùng bytecode weaving, xác minh tập lớp đã được biến đổi và đường code tương ứng.

### 17.5. Kiểm thử ngoại lệ, đệ quy và đồng thời

Đảm bảo advice chạy đúng số lần trong trường hợp method ném lỗi, gọi lồng nhau, chạy đa luồng hoặc có điều kiện control flow. Không dùng một biến mutable chung để lưu ngữ cảnh matching của mọi invocation.

### 17.6. Kiểm thử nhiều aspect

Khi hai aspect cùng chọn một shadow, kiểm tra thứ tự có hiệu lực, điều kiện guard của từng aspect và trường hợp advice ngoài dừng trước khi đến advice bên trong.

## 18. Lập ma trận so khớp để đối chiếu kỳ vọng

Giả sử quy tắc tổng quát:

> Chọn method execution `transfer(String,long)` với giá trị `amount > 1000` trong những lớp được dệt.

| Trường hợp | Khớp tĩnh? | Guard động? | Advice mong đợi? |
| --- | --- | --- | --- |
| `transfer("A",2000)` đúng loại điểm | Có | Đúng | Có |
| `transfer("A",500)` đúng loại điểm | Có | Sai | Không |
| `transfer("A",1000)` đúng loại điểm | Có | Sai | Không |
| `transfer("A")` | Không | Không cần đánh giá | Không |
| `transfer(99,2000)` | Không | Không cần đánh giá | Không |
| `ping()` | Không | Không cần đánh giá | Không |
| Chỉ tồn tại method, chưa được gọi | Có thể có shadow | Không xuất hiện invocation | Không có lần advice chạy |
| Method đúng chữ ký nhưng nằm ngoài weaving | Không có interception tại đường đó | Không được đánh giá ở đó | Không |
| Lời gọi đi trực tiếp qua target, bỏ qua proxy | Tùy mô hình tĩnh | Proxy không thấy | Không qua proxy |
| Thực thi trong vùng control flow cần theo dõi | Có | Tùy trạng thái luồng | Theo guard và công cụ |

Cần tách **điểm có khả năng khớp theo cấu trúc** khỏi **đường thực sự được can thiệp**. Bảng là ma trận kỳ vọng để xây kiểm thử, không phải lời hứa mọi công cụ hoạt động giống nhau.

## 19. Các lỗi hiểu sai thường gặp

**“Weaver chỉ tìm tên phương thức.”** Không. Mô hình join point, signature, declaring type, modifier, metadata và ngữ cảnh đều có thể ảnh hưởng matching.

**“Pointcut hợp cú pháp là chắc chắn có advice chạy.”** Không. Có thể không có shadow khớp, không có invocation hoặc không có đường weaving.

**“Một shadow khớp đồng nghĩa một lần advice.”** Không. Shadow là cấu trúc; invocation có thể xảy ra nhiều lần.

**“Tất cả điều kiện đều kiểm tra khi biên dịch.”** Không. Giá trị args, đối tượng runtime và control flow có thể chỉ biết lúc chạy.

**“Mọi điều kiện runtime đều phải đánh giá cho mọi method.”** Không nhất thiết. Matching tĩnh có thể loại phần lớn ứng viên.

**“Method call và method execution chỉ là hai tên giống nhau.”** Không. Chúng mô tả hai loại điểm, có phạm vi quan sát khác nhau.

**“Proxy là bộ dệt có thể chặn mọi bytecode.”** Không. Proxy thường chỉ can thiệp lời gọi đi qua nó.

**“Chọn kiểu interface nghĩa là đã chọn mọi implementation trong mọi công cụ.”** Chưa chắc. Phải biết ngữ nghĩa subtype và điểm được weaving.

**“Thêm annotation thì bộ dệt tự nhìn thấy ở mọi override.”** Không thể mặc định; cần kiểm tra cách đọc metadata.

**“Điều kiện khớp tĩnh có thể cache kết quả động theo chữ ký.”** Sai khi kết quả phụ thuộc dữ liệu từng lần gọi.

**“Không thấy log chứng minh pointcut sai.”** Có thể lỗi ở weaving, guard, đường gọi hoặc chính log advice.

## 20. Bài tập có đáp án

**1.** Một call site trong vòng lặp chạy 100 lần. Bộ dệt báo một static shadow khớp. Advice chạy mấy lần?

Không kết luận chỉ từ một shadow. Có thể có 100 invocation, nhưng còn phụ thuộc điều kiện động, đường có thực sự được weave và việc điểm có xảy ra hay không.

**2.** Một method có tên `transfer`, nhưng chữ ký là `transfer(String)`; pointcut đòi `transfer(String,long)`. Có cần kiểm tra amount ở runtime không?

Không đối với shadow đó, vì kiểu/số tham số đã không khớp tĩnh.

**3.** `transfer("A",500)` và `transfer("B",5000)` có cùng chữ ký. Có thể cache kết quả điều kiện `amount>1000` theo chữ ký không?

Không. Một lần không khớp, một lần khớp; kết quả phụ thuộc dữ liệu động.

**4.** Mã ở `checkout()` gọi `gateway.pay()`. Điểm method call nằm ở đâu, điểm method execution nằm ở đâu?

Call nằm tại vị trí gọi ở `checkout`; execution nằm ở phần thực thi method `pay`. Công cụ có thể hỗ trợ các tập điểm khác nhau.

**5.** Bộ dệt chỉ chặn qua proxy. Vì sao một lời gọi nội bộ không thấy advice?

Vì lời gọi có thể đi trực tiếp trên đối tượng target, không đi qua proxy. Không suy rằng mọi cơ chế AOP đều có cùng giới hạn.

**6.** Nếu một pointcut có phần tĩnh đúng nhưng điều kiện control flow sai, advice có chạy không?

Không chạy theo điều kiện pointcut ấy tại invocation đó, dù shadow có khả năng khớp.

**7.** Một advice ngoài nhận lỗi từ `proceed`. Có chắc nghiệp vụ ném không?

Không. Advice bên trong có thể phát sinh lỗi hậu xử lý; cần xác định ranh giới và nguồn.

**8.** Làm sao kiểm chứng danh sách 20 shadows đã được weave đồng nghĩa đầy đủ chính sách?

Cần tập mong muốn độc lập, kiểm thử cả ca phải khớp/không được khớp, và xác minh runtime trên các đường gọi thật. Con số 20 tự nó không chứng minh correctness.

## 21. Checklist áp dụng khi phân tích một weaver

- [ ] Đã xác định mô hình và **loại join point** mà công cụ hỗ trợ.
- [ ] Đã hiểu cú pháp pointcut, logic nhóm và cơ chế giải quyết kiểu.
- [ ] Đã phân biệt vị trí tĩnh (shadow) với invocation thực tế.
- [ ] Đã biết công cụ duyệt source, bytecode hay metadata runtime.
- [ ] Có danh sách những điều kiện được giải quyết tĩnh.
- [ ] Có danh sách các guard còn phải kiểm tra runtime.
- [ ] Đã biết weaving xảy ra lúc compile, load hay runtime.
- [ ] Đã xác nhận phạm vi các lớp/module được can thiệp.
- [ ] Có kiểm thử overload, override, interface và metadata nếu liên quan.
- [ ] Có ca kiểm thử call vs execution và các đường bypass proxy.
- [ ] Có ca âm tính chứng minh không chọn thừa.
- [ ] Đã xác nhận số invocation/advice thay vì chỉ đếm shadow.
- [ ] Đã kiểm tra thứ tự nhiều advice, kết quả, ngoại lệ và side effect.
- [ ] Có đo overhead hợp lý và tránh giả định mọi guard đều miễn phí.
- [ ] Không đưa dữ liệu nhạy cảm vào logging của advice.
- [ ] Có cách truy vết cấu hình weaving và thông báo matching của công cụ.

## 22. Tổng kết

**Bộ dệt tìm điểm phù hợp với pointcut bằng cách hiểu biểu thức chọn điểm, xác định các join-point shadow thuộc mô hình được hỗ trợ, so khớp những điều kiện có thể biết từ cấu trúc và chuẩn bị kiểm tra những điều kiện còn phụ thuộc runtime.** Sau đó nó kết hợp advice vào các đường thực thi phù hợp theo cơ chế của mình — có thể lúc biên dịch, nạp mã hoặc chặn lời gọi khi chạy.

Để hiểu đúng một kết quả AOP, phải phân biệt bốn câu hỏi: **pointcut mô tả gì**, **những điểm nào được weaver thực sự xử lý**, **lần thực thi nào thỏa guard**, và **advice nào cuối cùng chạy**. Tư duy theo các tầng đó giúp giải thích điểm được chọn hay bỏ sót, cải thiện hiệu năng, kiểm thử tính đúng đắn và tránh việc một chính sách xuyên suốt vô tình làm thay đổi nghiệp vụ ngoài ý định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-002">Chèn hành vi vào mã nguồn, mã trung gian hoặc đường gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Chèn hành vi vào mã nguồn, mã trung gian hoặc đường gọi trong kiến trúc weaving AOP

## 1. Vấn đề cần giải quyết

Trong **Aspect-Oriented Programming (AOP)**, các mối quan tâm xuyên cắt như tracing, kiểm toán, đo thời gian, chính sách truy cập hoặc ghi nhận lỗi có thể cần được áp dụng cho nhiều vị trí của chương trình. Thay vì sao chép mã xử lý cùng một concern vào hàng trăm phương thức, ta mô tả **aspect**, **pointcut** và **advice**, rồi dùng một cơ chế kết hợp hành vi (**weaving hoặc interception**) để thực hiện chúng tại những **join point** được hỗ trợ.

Câu hỏi kiến trúc quan trọng không chỉ là “advice thực hiện gì?”, mà còn là **“hành vi bổ sung được chèn vào lớp biểu diễn nào của chương trình?”**. Có ba nhóm hình thức rất hữu ích khi phân tích:

1. **Chèn ở mức mã nguồn (source-level transformation/weaving):** biến đổi hoặc sinh mã ở mức ngôn ngữ nguồn trước khi biên dịch.
2. **Chèn ở mức biểu diễn trung gian/mã đã biên dịch (IR/bytecode weaving):** biến đổi cấu trúc biên dịch như abstract syntax representation, intermediate representation, Java bytecode hay định dạng trung gian khác; vị trí chính xác phụ thuộc pipeline.
3. **Chèn vào đường gọi (runtime interception/call routing):** sắp xếp để lời gọi đi qua proxy, decorator, dispatcher, interceptor hoặc chain bao quanh target, thường không cần thay thân bytecode của target.

Ba cách này có thể **tạo trace bề ngoài giống nhau** nhưng khác đáng kể về **phạm vi join point, thời điểm áp dụng, artifact sinh ra, chi phí, cấu hình, khả năng debug và rủi ro bỏ qua advice**.

Mục tiêu của bài:

- Nhận diện rõ **điểm chèn hành vi** trong từng kiến trúc.
- Phân biệt **lớp biểu diễn bị tác động** với **thời điểm weaving**.
- Hiểu khái niệm **join point/pointcut/advice** khi ánh xạ vào nguồn, bytecode hoặc đường gọi.
- Dự đoán khác biệt giữa **method call** và **method execution**, kể cả override, self-invocation, constructor, private/final/static và field access.
- Đánh giá **mã sau biến đổi** và **hành vi runtime** bằng một quy trình kiểm thử có thể lặp.
- Biết khi nào cần source transformation, binary weaving, load-time weaving hay một proxy đơn giản.

Đây là bài về **AOP nói chung**. Không có công cụ AOP nào được xem là quy tắc phổ quát; đặc biệt việc một công cụ hỗ trợ một loại join point không có nghĩa mọi công cụ khác cũng hỗ trợ.

## 2. Ba lớp thể hiện chương trình

Trước hết cần hiểu ranh giới của từng lớp.

### 2.1. Mã nguồn

Đây là những tệp hoặc cấu trúc tương ứng với ngôn ngữ lập trình mà người phát triển có thể đọc và sửa, ví dụ một phương thức Java:

~~~java
class PriceService {
    int quote(int base) {
        return base + 20;
    }
}
~~~

Đoạn này là **minh họa**, không phải chương trình độc lập có phương thức main.

Nếu chèn logging tại nguồn, phần mã mà trình biên dịch nhìn thấy có thể trở thành:

~~~java
class PriceService {
    int quote(int base) {
        System.out.println("trace: begin");
        try {
            return base + 20;
        } finally {
            System.out.println("trace: end");
        }
    }
}
~~~

Phần thứ hai thể hiện **cấu trúc source sau biến đổi ở dạng minh họa**. Một source weaver thật có thể tạo code khác, helper method khác hoặc file nguồn được sinh tự động; không cần tạo chính xác đoạn code trên.

### 2.2. Mã trung gian hoặc bytecode

Chương trình Java thường được biên dịch thành **class file/bytecode** trước khi JVM thực thi. Một công cụ có thể phân tích và biến đổi **representation** này, ví dụ:

~~~text
Bytecode chưa dệt
  → phân tích class / method / control flow
  → xác định join point được hỗ trợ
  → tạo instruction / wrapper / helper phù hợp
  → bytecode sau dệt
~~~

Ở lớp này, mã Java source có thể **không hề thay đổi**, nhưng artifact class hoặc định nghĩa lớp nạp vào JVM đã mang hành vi bổ sung.

**Cẩn thận về thuật ngữ:** “intermediate representation (IR)” là một nhóm khái niệm rộng, bao gồm biểu diễn bên trong compiler, mô hình cây cú pháp, IR dạng control-flow graph, bytecode VM, v.v. Java class-file bytecode là một **dạng biểu diễn biên dịch**, không phải mọi IR đều có định dạng hay ngữ nghĩa như Java bytecode. Công cụ có thể transform một IR compiler rồi sinh bytecode, hoặc sửa chính bytecode đã biên dịch.

### 2.3. Đường gọi khi chạy

Một đối tượng gốc có thể được giữ nguyên, còn caller nhận **đối tượng đại diện** hoặc dùng một dispatcher:

~~~text
Caller → Proxy/Decorator/Interceptor → Target
             │
             ├── before
             ├── delegate / invoke
             └── after/finally
~~~

Hành vi ở đường được định tuyến qua interceptor đã thay đổi. Tuy nhiên nếu có một caller khác **gọi trực tiếp target**, interceptor đó có thể **không được kích hoạt**.

Có thể xem cơ chế này như **kết hợp hành vi tại runtime**. Một số tài liệu gọi rộng là “runtime weaving”, nhưng về mặt kỹ thuật cần nói rõ rằng **routing/interception không đồng nghĩa đã chỉnh sửa bytecode target**.

## 3. Không nhầm lớp chèn với thời điểm chèn

Một lỗi thường gặp là coi **compile-time weaving**, **load-time weaving** và **runtime proxy** đều là ba “nơi chèn” ngang hàng. Thực tế có hai trục phân loại khác nhau:

- **Trục A – tác động vào cái gì?** Source, IR/bytecode, hay đường gọi.
- **Trục B – tác động lúc nào?** Khi sinh source, lúc compile, sau compile, khi load class hoặc khi runtime dựng/điều phối đối tượng.

| Cơ chế thường gặp | Lớp bị tác động | Thời điểm | Tệp nguồn gốc có thể giữ nguyên? |
|---|---|---|---|
| Source transformation | AST/source/generated source | Trước hoặc trong biên dịch | Có, nếu tạo file sinh riêng; không, nếu ghi đè trực tiếp |
| Compiler-integrated IR weaving | IR nội bộ của compiler | Trong compile | Thường có |
| Post-compile/binary weaving | Class/JAR/bytecode đã biên dịch | Sau compile, trước chạy | Có |
| Load-time weaving | Bytecode khi lớp được định nghĩa/nạp | Khi class loading | Có; file class trên đĩa có thể chưa đổi |
| Runtime proxy/decorator | Object graph và đường gọi | Khi tạo proxy/decorator hoặc khi có invocation | Có |

Điểm quan trọng: **load-time weaving vẫn có thể là bytecode weaving**, trong khi **runtime interception có thể không biến đổi bytecode**. Định nghĩa và công cụ cụ thể mới quyết định điều gì xảy ra.

Một ứng dụng cũng có thể dùng **nhiều cơ chế đồng thời**: code generation tạo adapter, binary weaving thêm trace, và một gateway runtime bao quanh các lời gọi nghiệp vụ. Khi đó phải phân biệt từng concern và tầng mà nó được áp dụng.

## 4. Từ pointcut đến hành vi: pipeline khái niệm

Một kiến trúc AOP có thể được mô tả qua bốn pha logic, dù nhiều hệ thống gộp các pha hoặc thực hiện ở thời điểm khác nhau.

~~~text
Đầu vào 1: chương trình gốc
    source / AST / IR / bytecode / objects
                   │
                   ├──────────────────────┐
                   │                      │
Đầu vào 2: aspect                         │
    pointcut + advice + precedence         │
                   │                      │
                   └──────────┬───────────┘
                              ▼
                      Join point matching
                              │
                       Weaving strategy
                              │
                ┌─────────────┼─────────────┐
                ▼             ▼             ▼
           Source edit     IR/bytecode    Call routing
                │             │             │
                └─────────────┼─────────────┘
                              ▼
                       Woven behavior
                              │
                              ▼
                  Test kết quả và phạm vi
~~~

Trong đó:

1. **Join point model:** công cụ định nghĩa những điểm nào có thể can thiệp, ví dụ method call/execution, field get/set, constructor execution hoặc static initialization.
2. **Pointcut matching:** xác định các điểm khớp theo chữ ký, kiểu, vị trí lexical và điều kiện runtime được hỗ trợ.
3. **Advice composition:** quyết định thứ tự của before/after/around, điều kiện proceed, binding argument/target/exception và tương tác giữa nhiều aspect.
4. **Emission/routing:** tạo nguồn mới, biến đổi mã biên dịch hoặc dựng chuỗi lời gọi.

Một công cụ chỉ intercept các method qua interface proxy có thể không có join point kiểu field read, constructor hoặc static initializer. Do đó **không được lấy danh sách join point của một bytecode weaver làm hợp đồng cho mọi proxy runtime**.

## 5. Chèn hành vi vào mã nguồn

### 5.1. Source weaving thực hiện gì?

Một source-level transformer có thể đọc code, parse thành **abstract syntax tree (AST)**, tìm cấu trúc khớp quy tắc, chèn các node tương ứng với advice, rồi sinh lại source hoặc chuyển AST đã biến đổi vào compiler.

Ví dụ muốn bảo đảm ghi dấu kết thúc cả khi method ném lỗi, transformer có thể tạo cấu trúc tương tự:

~~~java
int calculate(int input) {
    traceStart();
    try {
        return compute(input);
    } finally {
        traceEnd();
    }
}
~~~

Đây là đoạn minh họa không chứa khai báo traceStart/compute/traceEnd. Cấu trúc **try/finally** được chọn nhằm tránh bỏ lỡ traceEnd khi lời gọi bên trong ném ngoại lệ. Nhưng nếu **traceStart tự throw**, phần target chưa chạy; nếu **traceEnd throw**, lỗi từ finally có thể thay thế lỗi ban đầu. Đây vẫn là quyết định về exception policy, không được AOP tự giải quyết.

### 5.2. Pipeline khi dùng source transformation

~~~text
Original Java sources
       │
       ▼
Parser / AST construction
       │
       ▼
Pointcut-like matching ở source/AST
       │
       ▼
AST rewrite hoặc source generation
       │
       ▼
Generated/modified Java sources
       │
       ▼
javac → .class → JVM
~~~

Có ít nhất hai hình thức:

- **Ghi đè tệp nguồn gốc:** trực tiếp thay những file mà người phát triển sửa.
- **Sinh artifact trung gian:** giữ nguồn tác giả nguyên vẹn, tạo tệp sinh trong build output rồi compile chúng.

Thông thường nên tách source tác giả và source generated để dễ review, tránh conflict với công cụ format, tránh sửa nhầm file được người dùng bảo trì. Tuy nhiên việc có nên đưa generated source vào version control tùy quy trình dự án.

### 5.3. Ưu điểm

- Mã sau biến đổi có thể được xem hoặc debug gần với ngôn ngữ nguồn.
- Có thể tận dụng compiler và công cụ phân tích tĩnh của ngôn ngữ.
- Có thể cung cấp diagnostic kèm file/line nếu transformer giữ source mapping tốt.
- Có thể sinh các wrapper hoặc adapter có API rõ mà không cần agent runtime.

### 5.4. Đánh đổi

- Phải parse và sinh code đúng với phiên bản cú pháp ngôn ngữ.
- AST rewrite có thể ảnh hưởng import, generic, annotation, formatting, comments và source location.
- Code sinh có thể gặp collision tên biến/helper và vấn đề phạm vi truy cập.
- Nếu chạy lại một transformer không idempotent, advice có thể bị chèn trùng.
- Build incremental có thể tạo artifact cũ khi đổi pointcut mà không thực hiện lại transform đúng cách.
- Một pointcut có điều kiện runtime vẫn cần sinh mã kiểm tra động; source analysis không thể biết trước mọi giá trị của invocation.

### 5.5. Vấn đề “đổi source là đã weave toàn bộ chưa?”

Không. Nếu cùng một lớp đã biên dịch từ bản nguồn cũ tồn tại trong artifact triển khai, thay source nhưng không build lại sẽ không đổi runtime. Nếu một thư viện bên thứ ba không có source trong pipeline này, transformer source có thể không tác động đến thư viện đó.

Như vậy, coverage phụ thuộc **tập nguồn được xử lý và phiên bản artifact thực sự được chạy**, chứ không chỉ sự tồn tại của một plugin tạo code.

## 6. Chèn hành vi tại biểu diễn trung gian/bytecode

### 6.1. Tại sao chọn lớp biểu diễn này?

Một công cụ có thể cần tác động tới chương trình **đã biên dịch**, chẳng hạn một class được tạo bởi compiler khác hoặc thư viện không được build từ source trong dự án hiện tại. Nếu IR/bytecode chứa thông tin đủ để xác định join point, weaver có thể thực hiện transform ở lớp đó.

Với JVM, class file chứa thông tin như method descriptors, instruction, constant pool, exception tables, stack frames/verification metadata và có thể có annotation/line information. Thao tác đổi bytecode phải giữ các ràng buộc của JVM; không thể chỉ thêm một instruction văn bản mà bỏ qua luồng điều khiển và stack.

### 6.2. Hai vị trí chèn thường gặp

**A. Tại thân method (execution weaving)**

~~~text
Trước:
Method Service.run:
    body instructions
    return

Sau:
Method Service.run:
    advice-before instructions
    body instructions
    advice-after instructions / finally path
    return
~~~

Đây là **mô hình đơn giản hóa**, không phải disassembly thật của một weaver. Khi exception xảy ra, phần after/finally có thể cần các nhánh/handler bổ sung; khi có nhiều return, instrument phải bảo toàn ý nghĩa rời method.

**B. Tại vị trí invocation (call-site weaving)**

~~~text
Trước ở Caller:
    invoke Service.run

Sau ở Caller:
    before-call
    invoke Service.run
    after-call
~~~

Một pointcut chọn **call join point** có thể khiến công cụ instrument **mã caller**, không cần sửa thân Service.run. Ngược lại, execution weaving có thể tác động **bên callee**. Hai chiến lược này không đồng nghĩa về phạm vi.

### 6.3. Compiler IR so với binary bytecode

**IR rewriting trong compiler** có thể làm việc với cấu trúc đã biết về type, control-flow và biểu thức trước khi sinh bytecode. **Post-compile bytecode weaving** làm việc với artifact đã sinh, đôi khi mất một số chi tiết nguồn hoặc có thêm cấu trúc synthetic/bridge do compiler tạo.

Do đó cùng một quy tắc AOP có thể cần kỹ thuật matching khác nhau:

| Vấn đề | IR/compiler-level | Post-compile bytecode |
|---|---|---|
| Type info | Có thể dựa vào hệ thống kiểu của compiler | Dựa descriptor, class metadata, classpath/module context |
| Generic | Có thể thấy generic source-level | Gặp erasure và generic signatures nếu được lưu |
| Lambda | Có thể còn cấu trúc nguồn | Có thể đã biểu diễn qua invokedynamic/synthetic methods |
| Source location | Có thể chính xác hơn | Phụ thuộc debug metadata |
| Code emission | Compiler sinh artifact | Weaver sửa class/JAR hoặc tạo class mới |
| Binary library | Chỉ nếu được đưa vào pipeline thích hợp | Có thể tác động vào artifact binary khi công cụ hỗ trợ |

Không phải mọi công cụ giữ hoặc xử lý cùng một mức semantic information; đây là câu hỏi cần tra cứu theo công nghệ cụ thể.

## 7. Bytecode weaving đòi hỏi điều kiện đúng đắn nào?

### 7.1. Stack và verifier

JVM bytecode dùng operand stack và các quy tắc kiểm tra kiểu. Nếu weaver thêm instruction gọi advice nhưng không giữ tính đúng của stack, local variables hoặc control flow, class có thể bị từ chối ở giai đoạn verify/load.

Những thao tác dễ sai:

- Chèn code vào giữa chuỗi dùng operand stack mà không bảo toàn stack state.
- Đổi control-flow branch mà không cập nhật offset/targets.
- Chèn exception handler không đúng vùng protected.
- Không cập nhật stack map frames, max stack/max locals hoặc metadata cần thiết theo class-file version.
- Gọi helper có descriptor không tương thích với giá trị đang nằm trên stack.

Một bộ thư viện bytecode tốt có thể hỗ trợ tính toán và xác minh các cấu trúc này, nhưng người triển khai vẫn phải hiểu giới hạn của công cụ và mã bị biến đổi.

### 7.2. Exception safety và đường return

Nếu muốn after-finally, phải xử lý cả:

- Return bình thường từ một hoặc nhiều nhánh.
- Exception được ném trong method.
- Exception do advice phát sinh.
- Nhánh có thể bypass một đoạn nếu pointcut chỉ chọn một số instruction.
- Trường hợp target bị skip hoặc retry do around advice.

Một phép thay bytecode chỉ chèn lời gọi after ngay trước một return đầu tiên có thể bỏ sót các nhánh return khác hoặc exceptional exit.

### 7.3. Đặc điểm class đặc biệt

Class có thể chứa bridge/synthetic method, generated code, lambda, constructor chain, static initializer, default interface methods, synchronized methods và các cấu trúc mới theo phiên bản JVM. Bộ dệt phải chọn đúng loại điểm, tránh đếm trùng và tuân theo khả năng hỗ trợ của chính nó.

### 7.4. Class-file version và module boundaries

Weaver phải hiểu class-file version được compiler sinh. Khi JVM hoặc ngôn ngữ phát triển, instrumenter lỗi thời có thể không đọc được format mới.

Module boundaries, class loader, access rules và bytecode verification vẫn tồn tại sau weaving. Không nên cho rằng sửa bytecode tự cấp mọi quyền truy cập Java private hoặc mở các module bị đóng.

### 7.5. Chẩn đoán artifact sau dệt

Các công cụ như **javap** có thể giúp xem disassembly:

~~~bash
javac --release 17 Example.java
javap -c -p Example
~~~

Lệnh trên chỉ là hướng dẫn tổng quát; để chạy phải có file Example.java hợp lệ trong môi trường. **javap không phải bộ dệt**, nó chỉ giúp xem class được compile/đã biến đổi. Nếu muốn so sánh trước và sau, phải lưu hai artifact riêng biệt và dùng đúng classpath phiên bản tương ứng.

Không nên kết luận weaving đúng chỉ vì bytecode có thêm một lời gọi. Còn phải xác minh pointcut match, thứ tự advice, tính đúng kiểu, exception behavior và kết quả runtime.

## 8. Post-compile weaving và load-time weaving

Hai cơ chế này đều có thể tác động vào **bytecode**, nhưng khác thời điểm.

### 8.1. Post-compile hoặc binary weaving

~~~text
Source
  │
  ▼
javac
  │
  ▼
Unwoven .class/.jar
  │
  ▼
Binary weaver + aspects
  │
  ▼
Woven .class/.jar
  │
  ▼
JVM
~~~

Thích hợp khi muốn kiểm soát artifact đã dệt trước deploy. Cần đảm bảo pipeline đóng gói **bản woven** chứ không nhầm bản unwoven. Hash, manifest, artifact provenance hoặc bước kiểm tra build có thể giúp xác nhận đúng hiện vật được đưa vào runtime.

### 8.2. Load-time weaving

~~~text
Source → compiler → original .class/.jar
                               │
                               ▼
                         Class loader
                               │
                     Bytecode transformer
                               │
                               ▼
                     Class definition đã dệt
                               │
                               ▼
                              JVM
~~~

Ở đây file class trên đĩa có thể giữ nguyên, nhưng bytecode dùng để **define class** đã được biến đổi. Vì thế không thể chỉ so file trong JAR rồi kết luận aspect không hoạt động.

Các rủi ro đặc thù:

- Transformer được kích hoạt quá muộn, sau khi một lớp cần weave đã được định nghĩa.
- Class loader khác nạp class nằm ngoài phạm vi cấu hình.
- Aspect/helper class cần được tải đúng nhưng việc tải chúng lại gây reentrancy.
- Transformer của nhiều thư viện cùng tác động, thứ tự transform ảnh hưởng kết quả.
- Instrumentation phiên bản mới không tương thích với bytecode của một thư viện.

Một số runtime còn hỗ trợ khả năng retransform/redefine class theo những giới hạn của nền tảng; không nên giả định có thể bổ sung, xóa hoặc đổi bất kỳ cấu trúc class nào vào bất kỳ lúc nào. Giới hạn phải được xem xét riêng.

## 9. Chèn vào đường gọi bằng proxy, decorator và interceptor

### 9.1. Cấu trúc tổng quát

~~~text
Caller
  │
  ▼
Intercepted reference
  │
  ├─ Before behavior
  │
  ├─ Invocation target
  │
  └─ After/finally behavior
  │
  ▼
Caller nhận result/exception
~~~

Một **proxy** có thể implement một interface, giữ reference target và dùng một invocation handler để xử lý những method được gọi qua proxy. Một **decorator** là object bao ngoài target qua một interface chung và thêm hành vi có chủ đích. Một **interceptor chain** cho phép nhiều thành phần bao quanh cùng invocation.

Đây là **đường gọi đã được lắp ghép**, không phải nhất thiết đã sửa implementation của target.

### 9.2. JDK dynamic proxy

JDK dynamic proxy tạo một object triển khai các interface được khai báo và chuyển invocation qua **InvocationHandler**. Handler thường nhận:

- Object proxy.
- Method đại diện cho method được gọi qua interface.
- Object[] arguments (hoặc null nếu không có đối số).

Nó có thể chạy logic before, gọi method.invoke(target,args), xử lý ngoại lệ và sửa result nếu hợp đồng kiểu cho phép.

Giới hạn thiết yếu:

- Chỉ những invocation **đi qua reference proxy** mới bị handler đó chặn.
- Một caller có reference đến target gốc có thể gọi thẳng, bỏ qua proxy.
- Một lời gọi nội bộ từ target đến method khác trên chính target thường không quay lại proxy.
- Proxy interface không tự biến thành cơ chế chặn constructor, field access hoặc static method.
- Reflection invocation có quy tắc exception wrapper, access và return type riêng.

### 9.3. Decorator

Decorator được viết với interface chung và ủy quyền tới target:

~~~java
interface Action {
    void act();
}

final class AuditAction implements Action {
    private final Action target;

    AuditAction(Action target) {
        this.target = target;
    }

    @Override
    public void act() {
        System.out.println("before");
        try {
            target.act();
        } finally {
            System.out.println("after");
        }
    }
}
~~~

Đây là đoạn Java minh họa, chưa có lớp main. Mã rất rõ ràng, dễ hiểu và không cần một bộ dệt. Tuy nhiên người sử dụng phải **nhận đúng reference decorator**; nếu code khác giữ reference target thì đường đó không bị bao.

### 9.4. Interceptor chain

Có thể tổ chức nhiều hành vi:

~~~text
Caller
  │
  ▼
AuthorizationInterceptor
  │
  ▼
TracingInterceptor
  │
  ▼
MetricInterceptor
  │
  ▼
Target
~~~

**Thứ tự rất quan trọng**. Ví dụ nếu Authorization từ chối không gọi tiếp, tracing nằm bên trong có thể không được kích hoạt. Nếu Tracing nằm ngoài Retry thì metric thời gian khác so với khi Tracing nằm trong mỗi lần retry.

Đừng suy thứ tự theo tên class hay thứ tự file nếu cơ chế interceptor không định nghĩa điều đó. Nếu policy phụ thuộc precedence, cấu hình và test phải thể hiện rõ.

## 10. Java 17 runnable: cùng mối quan tâm, ba đường đưa hành vi vào

Ví dụ sau có một chức năng lõi chuẩn hóa chuỗi thành chữ hoa và bốn cách gọi:

1. **Direct:** gọi target Core trực tiếp, không có hành vi xuyên cắt.
2. **SourceEdited:** mã bao ngoài mô phỏng phần được chèn tại lớp mã nguồn, nhưng được viết thủ công để chạy chỉ với JDK.
3. **Decorator:** caller đi qua object bao ngoài target.
4. **Dynamic proxy:** caller đi qua đối tượng đại diện do JDK tạo.

**Điểm trung thực kỹ thuật:** SourceEdited chỉ là **mô phỏng hình dạng source sau khi chèn**, không phải kết quả do một source weaver tự động sinh ra. Decorator và proxy là đường gọi runtime, **không có phần nào trong demo tự biến đổi Java bytecode**.

Tạo file **WeavingArchitectureDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Locale;

public class WeavingArchitectureDemo {
    interface Action {
        String apply(String input);
    }

    static final class Core implements Action {
        @Override
        public String apply(String input) {
            System.out.println("core: " + input.trim());
            return input.trim().toUpperCase(Locale.ROOT);
        }
    }

    static final class SourceEdited implements Action {
        private final Action target;

        SourceEdited(Action target) {
            this.target = target;
        }

        @Override
        public String apply(String input) {
            System.out.println("source: before");
            try {
                return target.apply(input);
            } finally {
                System.out.println("source: after");
            }
        }
    }

    static final class Decorator implements Action {
        private final Action target;

        Decorator(Action target) {
            this.target = target;
        }

        @Override
        public String apply(String input) {
            System.out.println("route: before");
            try {
                return target.apply(input);
            } finally {
                System.out.println("route: after");
            }
        }
    }

    static Action proxy(Action target) {
        return (Action) Proxy.newProxyInstance(
                Action.class.getClassLoader(),
                new Class<?>[]{Action.class},
                (proxy, method, args) -> {
                    System.out.println("proxy: before");
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    } finally {
                        System.out.println("proxy: after");
                    }
                });
    }

    public static void main(String[] args) {
        Action raw = new Core();

        System.out.println("direct:");
        System.out.println("result=" + raw.apply(" alpha "));

        System.out.println("source simulation:");
        System.out.println("result=" +
                new SourceEdited(raw).apply(" beta "));

        System.out.println("decorator:");
        System.out.println("result=" +
                new Decorator(raw).apply(" gamma "));

        System.out.println("proxy:");
        Action intercepted = proxy(raw);
        System.out.println("result=" +
                intercepted.apply(" delta "));

        System.out.println("bypass:");
        System.out.println("result=" +
                raw.apply(" epsilon "));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 WeavingArchitectureDemo.java
java WeavingArchitectureDemo
~~~

Stdout chính xác:

~~~text
direct:
core: alpha
result=ALPHA
source simulation:
source: before
core: beta
source: after
result=BETA
decorator:
route: before
core: gamma
route: after
result=GAMMA
proxy:
proxy: before
core: delta
proxy: after
result=DELTA
bypass:
core: epsilon
result=EPSILON
~~~

### 10.1. Giải thích thứ tự

- Ở **direct**, chỉ có Core.apply, nó in thông báo lõi và trả chuỗi đã chuẩn hóa.
- **SourceEdited** có before/finally được viết sẵn ở lớp bao: đó là cách **mô phỏng ý tưởng source chèn thêm behavior**. Nếu một transformer sinh ra lớp tương tự, artifact source chứa logic đó trước lúc javac xử lý.
- **Decorator** gọi Core qua lớp bao ngoài; kết quả ALPHA/BETA/GAMMA theo đầu vào tương ứng không bị biến đổi ngoài logging.
- **Proxy** chặn invocation đi qua interface Action, chạy before, ủy quyền bằng Method.invoke, in after trong finally, rồi trả result.
- **Bypass** dùng trực tiếp raw nên không có thông báo proxy: before/proxy: after. Điều này làm rõ bản chất **phụ thuộc đường gọi** của cơ chế proxy.

### 10.2. Vì sao cần catch InvocationTargetException?

Reflection API Method.invoke có thể bao exception do method target ném trong **InvocationTargetException**. Handler bắt exception wrapper rồi ném **getCause()** để caller nhận lỗi gốc phù hợp hơn. Ngoài ra JDK proxy có thể bọc một checked exception mà method interface không cho phép truyền ra thành **UndeclaredThrowableException**.

Điểm này cho thấy một interceptor có thể thay **exception behavior** nếu không xử lý đúng, dù chỉ định làm trace. Bytecode weaving cũng phải bảo toàn exception semantics, nhưng cách cài đặt và các wrapper cụ thể có thể khác.

### 10.3. Không kết luận bytecode weaving từ stdout

Các đường trong demo có kết quả tương tự về “log trước/log sau”, nhưng demo **không hề chạy một bytecode weaver**. Để chứng minh bytecode weaving thật, phải chạy compiler/weaver phù hợp trên một artifact đã chọn rồi kiểm tra class/runtime đã dệt, không được chỉ thay tên SourceEdited thành WovenCode.

Ngược lại, một ứng dụng có load-time bytecode weaving có thể tạo stdout tương tự **mà không sinh bất kỳ source file mới nào**.

## 11. So sánh ba vị trí chèn hành vi

| Tiêu chí | Source-level | IR/bytecode-level | Runtime call routing |
|---|---|---|---|
| Điểm tác động chính | Code/AST trước compile | Representation biên dịch hoặc class definition | Đối tượng đại diện, dispatcher, interceptor chain |
| Có cần source target? | Thường cần hoặc cần biểu diễn nguồn | Không nhất thiết, nếu có binary phù hợp | Thường không |
| Có cần rebuild/reweave? | Thường có khi thay rule | Binary weaving có; load-time có thể áp dụng khi định nghĩa lớp | Có thể thay cấu hình/khởi tạo proxy khi runtime |
| Thay class file target? | Có thể tạo class khác sau compile | Có thể có, hoặc chỉ biến đổi lúc load | Không bắt buộc |
| Khả năng chặn direct target call | Có nếu source/đường được biến đổi phù hợp | Có thể nếu execution được weave | Thường không khi bypass proxy |
| Self-invocation | Có thể nếu method execution/đường liên quan đã được chèn | Có thể nếu execution được weave | Thường bỏ qua đường proxy nội bộ |
| Constructor/field/static join points | Phụ thuộc transformer và cú pháp | Phụ thuộc weaver | Proxy method-call không tự hỗ trợ |
| Debug artifact | Generated source/source map | Disassembly/bytecode diagnostics | Proxy chain và runtime reference |
| Rủi ro chính | Source drift, generated code lỗi, build cache | Verify/class loader/compatibility/transformer | Routing bypass, proxy limitations, ordering |
| Thứ tự advice | Do công cụ/composition quyết định | Do công cụ/composition quyết định | Do chain/order quyết định |
| Phù hợp | Build pipeline kiểm soát nguồn | Cần chèn ở lớp đã biên dịch hoặc execution sâu | Chủ động kiểm soát API và đường đi đối tượng |

Bảng là **hướng dẫn tư duy kiến trúc**, không phải ma trận khẳng định tính năng của một sản phẩm cụ thể. Một số công cụ kết hợp nhiều lớp và có khả năng khác bảng điển hình.

## 12. Call join point so với execution join point quyết định nơi chèn

Đây là vấn đề quan trọng nhất khi lựa chọn kiến trúc.

### 12.1. Chọn nơi phát lời gọi

~~~java
class Client {
    void handle(Service service) {
        service.execute();
    }
}
~~~

Đây là đoạn minh họa, thiếu định nghĩa Service. Nếu pointcut chọn **call** ở biểu thức service.execute(), một source/bytecode transformer có thể instrument **Caller.handle**, thậm chí khi Service.execute nằm trong thư viện khác.

Điều đó khác với interceptor proxy: proxy nhận lời gọi sau khi caller đã quyết định reference nào nó đang dùng; nó không nhất thiết biết hay chèn code tại **mọi dòng gọi cụ thể trong source**.

### 12.2. Chọn nơi thực thi

Nếu pointcut chọn **execution** của Service.execute, bộ dệt có thể instrument **thân của method được thực thi**. Bất kể có bao nhiêu caller, lượt thực thi đi vào thân đã dệt đều có thể chạy advice, kể cả lời gọi nội bộ khi compiler/bytecode weaver hỗ trợ.

### 12.3. Call site đa hình

~~~java
Service service = new FastService();
service.execute();
~~~

Kiểu reference tại caller là Service, nhưng runtime dispatch có thể chọn FastService.execute. Một rule chọn call signature có thể nhìn giao diện Service ở caller, còn một rule chọn execution có thể nhìn thân FastService thật được gọi.

Do đó **cùng câu lệnh nguồn** có thể tương ứng một call join point và một execution join point **thuộc kiểu khai báo khác nhau**. Kiểu static, runtime receiver và declaring method đều ảnh hưởng kết quả matching theo ngữ nghĩa của công cụ.

### 12.4. Self-invocation

~~~java
class Service {
    void outer() {
        inner();
    }

    void inner() {
        // Công việc cần quan sát
    }
}
~~~

Một proxy chặn outer khi caller gọi qua proxy nhưng không nhất thiết chặn inner bên trong target. Một execution weaver chèn trực tiếp vào inner có thể quan sát được lời gọi đó. **Giới hạn của proxy không phải định luật của AOP**.

## 13. Có thể chèn tại constructor, field hoặc khởi tạo lớp không?

Cần xét **join point model**, không chỉ thời điểm weave.

- **Constructor call:** biên nơi caller yêu cầu tạo object.
- **Constructor execution:** thân constructor được thực hiện trong chuỗi khởi tạo.
- **Field get/set:** vị trí đọc/ghi trường nếu engine hỗ trợ.
- **Static initialization:** sự kiện lớp được khởi tạo theo JVM.
- **Method execution/call:** hai nhóm join point thường dùng nhất.

Với source hoặc bytecode weaving có hỗ trợ, các điểm trên có thể được biểu diễn thành vị trí chèn cụ thể. Với một **interface proxy** chỉ intercept method, không tự nhiên có field get hay static initialization join point.

Đặc biệt, constructor và static initializer rất nhạy cảm:

- Object có thể chưa có các invariant hoàn chỉnh.
- Lời gọi superclass/subclass có thứ tự quy định.
- Một advice có thể vô tình làm object **escape** trước khi khởi tạo xong.
- Một advice đọc field chưa khởi tạo có thể nhìn thấy giá trị mặc định.
- Class initialization có quy tắc synchronization và failure riêng.

Không nên chèn logic AOP nặng, gọi I/O hoặc tạo dependency vòng ở các giai đoạn này mà không hiểu kỹ hợp đồng khởi tạo.

## 14. Bảo toàn nghĩa khi chèn hành vi

Weaver có thể tạo artifact “biên dịch được” nhưng **đổi hành vi ngoài ý định**. Khi đánh giá kiến trúc, cần tách:

1. **Bảo toàn cấu trúc:** class hợp lệ, method signature phù hợp, control flow hợp lệ.
2. **Bảo toàn chức năng:** giá trị trả về, exception, side effect nghiệp vụ và thứ tự gọi vẫn đúng theo hợp đồng.
3. **Thay đổi có chủ đích:** trace, audit, retry, giới hạn quyền hoặc policy khác thực sự được thực hiện đúng.
4. **Phạm vi áp dụng:** không bỏ sót điểm phải chạy và không áp dụng nhầm điểm ngoài phạm vi.

### 14.1. Before advice

Nếu before chỉ ghi log rồi hoàn tất bình thường, target thường tiếp tục. Nhưng nếu before tự throw, target có thể chưa chạy. Nếu before mutate argument hoặc shared state, kết quả target cũng có thể đổi.

### 14.2. After/finally advice

Nếu muốn log “end” cả khi target throw, cần gắn advice tại các đường rời thích hợp. Ở source dùng try/finally; ở bytecode cần cấu trúc điều khiển/exception tương đương; ở decorator/proxy có thể dùng finally quanh delegate.

Tuy nhiên **finally của advice tự throw** có thể che lỗi gốc. Cần policy về việc ghi nhận secondary failure, không vô ý đổi contract exception.

### 14.3. Around advice

Around có thể thay argument/result, skip target hoặc retry tùy công cụ. Cơ chế chèn phải tuân theo quyền hạn proceed/continuation của engine. Một trình bao runtime có thể gọi target.invoke nhiều lần; một bộ dệt IR có thể tạo control flow tương ứng.

Nếu có retry, phải xét **idempotency**. Việc lặp một thao tác có side effect như gửi tiền, phát thông điệp hoặc ghi đơn hàng có thể gây duplicate behavior.

## 15. Thứ tự của nhiều aspect và nhiều cơ chế

Giả sử có ba concerns: **Authorization**, **Tracing** và **Retry**. Thứ tự lồng nhau có thể làm khác ý nghĩa:

~~~text
Tracing(Retry(Core))
    Trace thấy một thao tác gồm nhiều lượt thử.

Retry(Tracing(Core))
    Trace có thể thấy từng lượt thử.
~~~

Nếu Authorization nằm ngoài Retry, quyền được kiểm tra trước toàn bộ chuỗi retry; nếu nằm trong, quyền có thể được kiểm tra ở mỗi attempt. Cách nào đúng phụ thuộc chính sách.

Khi hệ thống dùng cả bytecode weaving và proxy:

~~~text
Caller → Proxy interceptor → Target class có bytecode đã dệt
                               │
                               └─ Advice trong woven execution
~~~

Một invocation qua proxy có thể kích hoạt **hai lớp advice**. Nếu cả hai cùng ghi metric “một request” mà không có deduplication, số đếm có thể gấp đôi.

Cần có **bản đồ trách nhiệm concern**: concern nào do source-generated code xử lý, concern nào do bytecode weaver, concern nào do interceptor runtime. Đừng dựa vào tên logger giống nhau để suy ra chúng thuộc cùng một điểm.

## 16. Khi nào nên dùng kiến trúc nào?

### 16.1. Nghiêng về source transformation nếu

- Bạn kiểm soát build pipeline và nguồn cần chuyển đổi.
- Cần generated code dễ review, kiểm tra hoặc dùng công cụ compiler tĩnh.
- Chấp nhận build lại để thay aspect.
- Có chiến lược tránh source drift và duplicate injection.
- Phạm vi là phần source nằm trong hệ thống kiểm soát.

### 16.2. Nghiêng về IR/bytecode weaving nếu

- Cần tác động tại **execution** hoặc call site mà caller không nhất thiết đi qua một proxy.
- Cần hỗ trợ một loại join point nằm sâu trong class, ví dụ field access/constructor, và công cụ thật có capability đó.
- Có artifact binary hoặc class không thuộc nguồn bạn muốn sửa.
- Có khả năng kiểm soát bytecode version, verification, class loader và artifact deployment.
- Chấp nhận đầu tư cho test tích hợp, chẩn đoán và toolchain compatibility.

### 16.3. Nghiêng về runtime interceptor nếu

- Có thể **kiểm soát đường tạo và phân phối object reference**.
- Phần lớn concern nằm tại public API/interface hoặc boundary rõ.
- Muốn composition động, dễ bật/tắt hoặc sắp xếp interceptor chain.
- Không cần quan sát constructor, field access hoặc mọi self-invocation trong target.
- Muốn giảm độ phức tạp của binary instrumentation.

### 16.4. Khi không cần AOP

Nếu logic chỉ áp dụng cho một method và là phần rõ ràng của hợp đồng nghiệp vụ, một helper, decorator hoặc đối tượng cộng tác có thể dễ đọc hơn. Không nên đưa mọi biến đổi vào aspect chỉ để “không phải viết thêm một dòng code”.

**Nguyên tắc:** lựa chọn kiến trúc theo **phạm vi join point cần bao phủ** và **mức minh bạch/khả năng vận hành**, không chọn theo việc demo có in dòng before/after giống nhau hay không.

## 17. Chẩn đoán “advice không chạy” theo lớp chèn

### 17.1. Source-level

Kiểm tra:

1. Tệp nguồn nào được transformer chọn?
2. Generated source có được tạo từ phiên bản aspect mới nhất?
3. Mã được chèn nằm đúng method và đủ mọi nhánh?
4. Build có compile từ generated source thay vì original source?
5. Incremental build có bỏ qua bước transform không?
6. Có chèn trùng khi chạy transformer nhiều lần không?

### 17.2. Binary weaving

Kiểm tra:

1. Class/JAR input nào thực sự được dệt?
2. Pointcut chọn call site hay execution?
3. Artifact đóng gói có phải bản woven?
4. Weaver có hỗ trợ class-file version được dùng?
5. Các class cần thiết thuộc class loader/module nào?
6. Bytecode verifier hoặc runtime có báo lỗi không?
7. Advice precedence có khiến phần liên quan không chạy không?

### 17.3. Load-time weaving

Kiểm tra thêm:

1. Transformer đã được đăng ký trước khi class cần weave được định nghĩa chưa?
2. Class nào đã load trước khi thiết lập cấu hình?
3. Có class loader khác dùng cùng tên class nhưng định nghĩa khác không?
4. Có nhiều transformer cùng sửa một class không?
5. Định nghĩa runtime có thực sự chứa instrumented behavior không?

### 17.4. Runtime proxy

Kiểm tra:

1. Caller đang giữ **proxy** hay **target gốc**?
2. Method có thuộc tập được proxy expose/chặn không?
3. Caller có gọi **self-invocation** bên trong target không?
4. Target có được tạo trực tiếp ngoài factory/interceptor chain không?
5. Có class/proxy/interface khác với assumption của pointcut không?
6. Around interceptor bên ngoài có short-circuit trước khi đến advice bên trong không?

## 18. Kiểm chứng kiến trúc theo hai hướng: artifact và hành vi

**Kiểm tra artifact** trả lời: “Điều gì đã được biến đổi ở source/bytecode/đường object?”. **Kiểm tra hành vi** trả lời: “Advice có thực sự chạy ở những lần sử dụng mà chính sách yêu cầu không?”. Cần cả hai khi bảo đảm quan trọng.

### 18.1. Baseline trước weaving

Tạo test trên lớp gốc không có advice:

- Kết quả nghiệp vụ đúng.
- Exception được ném đúng.
- Side effect và số lần gọi đúng.
- Đường đồng thời/bất đồng bộ đã được đại diện nếu liên quan.

### 18.2. Kiểm chứng pointcut match

Tạo danh sách **join point phải khớp** và **không được khớp**. Kiểm tra cả:

- Method của superclass và override ở subclass.
- Method được kế thừa nhưng không override.
- Overload và generic bridge nếu quan trọng.
- Caller khác nhau ở call join point.
- Gọi nội bộ và gọi trực tiếp target.
- Method không thuộc vùng weave/không đi qua proxy.

### 18.3. Kiểm tra thứ tự và số lần

Ghi correlation ID và join point kind trong trace thử nghiệm. Với một request, phân biệt:

~~~text
call join point
execution join point
nested helper
retry attempt
after / exception path
~~~

Một metric “advice chạy 4 lần” không tự nghĩa target chạy 4 lần; có thể bốn join point khác nhau được chọn. Ngược lại target có thể chạy nhiều lần qua retry trong khi một interceptor ngoài chỉ log một lần.

### 18.4. Kiểm chứng lỗi

Thử ba ca độc lập:

1. **Advice trước ném lỗi**: target có được gọi không?
2. **Target ném lỗi**: advice finally/after throwing có hoạt động đúng không?
3. **Advice sau ném lỗi**: caller nhận lỗi nào? Có vô ý che lỗi target không?

Nếu có load-time weaving, các lỗi xảy ra lúc khởi tạo lớp cũng cần test riêng vì failure có thể làm class không khởi tạo thành công.

## 19. Ma trận test đề xuất

| Tình huống | Source transform | IR/bytecode | Proxy/route |
|---|---|---|---|
| Method được chọn từ caller thông thường | Đúng nếu source đã transform | Đúng nếu điểm được weave | Đúng nếu caller dùng proxy |
| Method không thuộc phạm vi pointcut | Không chèn | Không chèn | Không kích hoạt interceptor |
| Self-invocation | Phụ thuộc vị trí source được chèn | Có thể quan sát execution được weave | Thường không quay lại proxy |
| Caller giữ target gốc | Source target đã đổi thì vẫn có behavior | Execution target đã weave thì vẫn có behavior | Có thể bypass |
| Override ở subclass | Phải transform đúng thân cần chọn | Phải weave đúng implementation | Phải kiểm tra đường proxy và method exposed |
| Constructor/field access | Phụ thuộc transformer | Phụ thuộc join point model của weaver | Method proxy không tự bao |
| Target throw | Finally/advice exception đúng | Bytecode handler đúng | Handler/decorator xử lý đúng |
| Class loader khác | Generated source/class có áp dụng không? | Weaver/transformer có bao phủ loader không? | Reference proxy có được phân phối không? |
| Hai aspect cùng khớp | Thứ tự composition đúng | Precedence/bytecode output đúng | Interceptor chain order đúng |
| Phiên bản công cụ mới | Generated source còn biên dịch? | Class-file format còn tương thích? | API/interceptor contract còn tương thích? |
| Build/deploy | Compile generated artifact đúng | Đóng gói đúng binary woven | Wiring object graph đúng |

Bảng mô tả **câu hỏi cần kiểm chứng**, không hứa hẹn mọi source/IR weaver hỗ trợ tất cả loại điểm. Một bộ dệt cụ thể có thể không có khả năng xử lý một số trường hợp.

## 20. Rủi ro vận hành và bảo mật

### 20.1. Side effect và leak dữ liệu

Advice có thể nhận argument và target, rồi ghi log toàn bộ object. Đây là nguy cơ lộ thông tin nếu object chứa credential/token hoặc dữ liệu nhạy cảm. Vị trí chèn không tự quyết định dữ liệu nào được phép ghi.

### 20.2. Khả năng quan sát và debug

Generated source dễ nhìn nhưng có thể làm stack trace lệch nguồn tác giả. Bytecode instrumentation có thể thay method size, thêm synthetic frames và ảnh hưởng thông tin nguồn. Proxy tạo thêm lớp call stack và exception wrappers. Các khác biệt này cần được hỗ trợ bằng log có cấu trúc, source mapping và quy trình debug được ghi nhận.

### 20.3. Hiệu năng

Chi phí phụ thuộc số join point khớp, động tác advice, allocation, serialization, logging I/O, reflection, dynamic dispatch và caching. Source/bytecode weaving có thể tránh một số lớp delegation nhưng vẫn có overhead; proxy có thể được JVM tối ưu ở các mức độ khác nhau.

Không kết luận “bytecode luôn nhanh hơn proxy” chỉ từ hình dạng kiến trúc. Phải đo theo workload, tần suất gọi, latency và chi phí của chính concern.

### 20.4. Class loading và vòng phụ thuộc

Một load-time transformer dùng logging library mà logging library lại nằm trong tập lớp bị transform có thể gây recursion/class initialization problems. Cần giới hạn phạm vi, kiểm tra thứ tự nạp và tránh advice có dependency vòng.

### 20.5. Vùng quyền hạn

Một source transformer hoặc bytecode weaver có khả năng chèn mã rộng nên có thể tạo ảnh hưởng lớn nếu quy tắc pointcut sai. Cần code review, test tự động, kiểm soát artifact và nguyên tắc giới hạn quyền của helper. Một proxy policy có thể bị bỏ qua nếu object graph không được kiểm soát. Cả hai đều cần thiết kế boundary rõ ràng.

## 21. Những hiểu lầm phổ biến

**Hiểu lầm 1 — “Weaving luôn sửa file nguồn Java.”** Binary/load-time weaving có thể thay bytecode hoặc định nghĩa class mà source vẫn nguyên.

**Hiểu lầm 2 — “Load-time weaving và proxy là một.”** Load-time weaving có thể sửa bytecode lúc define class; proxy chủ yếu thay đường gọi qua object đại diện.

**Hiểu lầm 3 — “Có log before/after chứng minh bytecode đã bị dệt.”** Decorator hoặc proxy cũng có thể tạo cùng log. Cần kiểm tra artifact/cơ chế.

**Hiểu lầm 4 — “Pointcut chọn một tên method thì mọi lời gọi đều được chặn.”** Phải xét call/execution, subtype, visibility, vùng weave và routing.

**Hiểu lầm 5 — “Proxy bắt được mọi self-invocation.”** Thường không khi target tự gọi method trên chính nó mà không qua proxy.

**Hiểu lầm 6 — “Sửa bytecode là chỉ thêm vài instruction.”** JVM verifier, control-flow, exception tables, stack frames và class-file format là những ràng buộc thực.

**Hiểu lầm 7 — “Source transformation luôn cần chỉnh sửa file do người dùng viết.”** Có thể sinh file trung gian và giữ nguyên bản tác giả.

**Hiểu lầm 8 — “Class trên đĩa không thay đổi nghĩa là load-time advice không chạy.”** Bytecode có thể được biến đổi chỉ ở lúc class definition.

**Hiểu lầm 9 — “Aspect thứ tự nào cũng như nhau.”** Around/precondition/retry/metric có thể thay semantics khi thứ tự lồng khác nhau.

**Hiểu lầm 10 — “Instrument quá rộng chỉ ảnh hưởng hiệu năng.”** Nó còn có thể làm đổi exception, làm rò dữ liệu, tạo recursion hoặc tác động class initialization.

**Hiểu lầm 11 — “Chỉ kiểm tra source hoặc bytecode đã đủ.”** Artifact có thể đúng nhưng caller vẫn dùng đường bypass; cần kiểm chứng hành vi thật.

**Hiểu lầm 12 — “Công nghệ đã dệt rồi thì mọi rule nghiệp vụ có thể đặt trong advice.”** Một invariant miền quan trọng cần hợp đồng và test rõ ràng, tránh phụ thuộc ẩn vào cấu hình weave.

## 22. Quy trình lựa chọn cơ chế chèn cho một dự án

1. **Xác định concern.** Là trace/metrics, audit bắt buộc, cache/retry hay chính sách có khả năng từ chối thao tác?
2. **Xác định join point thật sự cần.** Public method, execution nội bộ, method call tại caller, constructor hay field?
3. **Xác định phạm vi mã kiểm soát.** Có source? Có binary? Có khả năng kiểm soát object reference?
4. **Đặt yêu cầu bao phủ.** Mọi caller, chỉ một API, cả self-invocation, thư viện ngoài hay class loader đặc biệt?
5. **Chọn lớp chèn.** Source nếu quản lý build/source, bytecode nếu cần điểm sâu hoặc binary, runtime route nếu có boundary object rõ.
6. **Chọn thời điểm.** Generate source, compile, post-compile, load-time hoặc tạo interceptor runtime.
7. **Thiết kế precedence và lỗi.** Advice có được throw/skip/retry không? Tầng nào chịu trách nhiệm final cleanup?
8. **Làm demo tối thiểu.** Có một target, một join point, một advice và baseline kiểm chứng được.
9. **Thử ca biên.** Ngoại lệ, overload, override, self-call, target bypass, nhiều aspects và concurrent calls.
10. **Đưa vào pipeline vận hành.** Với binary weaving lưu provenance artifact, với source transform kiểm tra generated sources, với proxy kiểm soát object graph.
11. **Đo hiệu năng và mức độ quan sát.** Thử workload thật, không chỉ xem stdout.
12. **Viết regression tests.** Tập điểm match và số advice invocation phải còn đúng sau refactor hoặc đổi toolchain.

## 23. Câu hỏi tự kiểm tra

**Câu 1.** Source gốc không có System.out.println nhưng lúc chạy có log trước method. Có thể khẳng định source transformer đã chạy không?

**Đáp:** Không. Log có thể đến từ bytecode weaving, load-time transformation, proxy, decorator hoặc một đường khác.

**Câu 2.** Class file trên đĩa nguyên vẹn nhưng runtime đã có advice được chèn vào method execution. Có thể xảy ra không?

**Đáp:** Có. Load-time weaving có thể biến đổi bytecode khi lớp được định nghĩa mà không ghi lại file class trên đĩa.

**Câu 3.** Một caller sử dụng reference target gốc thay vì proxy. Handler của proxy có chạy không?

**Đáp:** Không trong mô hình chỉ intercept qua reference proxy ấy. Dynamic dispatch Java vẫn diễn ra nhưng không đi qua handler.

**Câu 4.** Muốn chặn mọi method execution của một thư viện đã biên dịch mà không điều khiển caller, tại sao binary weaving có thể thích hợp hơn proxy?

**Đáp:** Nó có thể tác động trực tiếp đến thân đã biên dịch nếu công cụ hỗ trợ và lớp thuộc phạm vi weave, trong khi proxy phụ thuộc caller được định tuyến qua đối tượng đại diện.

**Câu 5.** Tại sao bytecode weaver cần xem xét stack map frames và exception handlers?

**Đáp:** Việc chèn instruction phải tạo class hợp lệ với JVM verifier và giữ đúng các đường điều khiển, kể cả exceptional exit.

**Câu 6.** Nếu source transformer chèn finally một lần nhưng chạy lại transformer và chèn thêm lớp finally giống hệt, trace có thể ra sao?

**Đáp:** Có thể xuất hiện advice trùng hoặc lồng nhau. Transformer cần cơ chế tránh tái chèn ngoài ý định hoặc pipeline dùng source gốc bất biến.

**Câu 7.** Hai lần gọi method thông qua hai caller khác nhau nhưng cùng target. Execution weaving và call-site weaving có nhất thiết thấy cùng một tập điểm không?

**Đáp:** Không. Execution nhìn thân được thực thi, call-site nhìn những vị trí caller được weave; phạm vi và chữ ký có thể khác nhau.

**Câu 8.** Hai interceptor được xếp theo thứ tự Tracing(Retry(Core)) và Retry(Tracing(Core). Kết quả số event trace có thể khác nhau không?

**Đáp:** Có. Tracing ngoài retry có thể thấy một chu kỳ tổng, Tracing bên trong có thể thấy từng attempt.

**Câu 9.** Khi nào một decorator minh bạch có thể phù hợp hơn một bytecode weaver?

**Đáp:** Khi boundary nhỏ, reference có thể kiểm soát được và cần logic bao rõ ràng, dễ debug mà không yêu cầu instrument method nội bộ/constructor/field.

**Câu 10.** Để xác nhận AOP hoạt động đúng, kiểm tra mỗi việc “có thêm lời gọi advice trong bytecode” đã đủ chưa?

**Đáp:** Chưa. Cần kiểm tra matching chính xác, runtime invocation, exception/return contract, thứ tự, số lượt và các đường không được khớp.

## 24. Sơ đồ ghi nhớ

~~~text
           ASPECT = POINTCUT + ADVICE
                      │
                      ▼
                Chọn JOIN POINT
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
     SOURCE/AST    IR/BYTECODE   CALL ROUTE
        │             │           │
     Rewrite      Transform     Proxy / decorator
     trước      trong/sau      / interceptor chain
     compile     compile/load        │
        │             │           │
        └─────────────┼───────────┘
                      ▼
               WOVEN BEHAVIOR
                      │
                      ▼
          KIỂM CHỨNG BẰNG ARTIFACT
          + ĐƯỜNG THỰC THI THẬT
~~~

**Điểm ghi nhớ cuối:** “chèn hành vi” có thể diễn ra tại **source**, **mã trung gian/bytecode** hoặc **đường gọi runtime**. Đừng suy cơ chế từ log bên ngoài. Hãy xác định **lớp biểu diễn được tác động**, **thời điểm áp dụng**, **join point được hỗ trợ**, **đường gọi có được bao phủ** và **kết quả đối chiếu với hợp đồng nghiệp vụ**. Đó là nền tảng để thiết kế weaving đáng tin, kiểm thử được và có khả năng vận hành.

## Tài liệu đối chiếu

- [Java Virtual Machine Specification, Java SE 17 – Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): cấu trúc class file, bytecode và metadata cần được giữ hợp lệ.
- [Java Virtual Machine Specification, Java SE 17 – Loading, Linking, Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): vòng đời định nghĩa/nạp class và những ràng buộc liên quan đến load-time instrumentation.
- [Java SE 17 – Instrumentation API](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html): API hỗ trợ transformer, khả năng retransform/redefine và giới hạn của nền tảng.
- [Java SE 17 – InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): một cơ chế runtime interception dùng trong ví dụ Java.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): tài liệu tham khảo về mô hình join point, pointcut, advice, static/dynamic weaving của một ngôn ngữ AOP cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-003">Phân biệt dệt tĩnh với can thiệp động lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt dệt tĩnh với can thiệp động lúc chạy

## 1. Mục tiêu: hiểu hành vi AOP được gắn vào chương trình lúc nào

Trong **Aspect-Oriented Programming (AOP)**, các concern cắt ngang như audit, metrics, tracing và kiểm soát truy cập có thể được tách thành **aspect**. Một aspect thường phối hợp **pointcut** (chọn các join point) với **advice** (hành vi chạy tại những điểm được chọn). Nhưng chỉ viết pointcut/advice là chưa đủ: cần một **cơ chế kết hợp** để lời gọi thực tế đi qua hành vi bổ sung.

**Weaving (dệt aspect)** là quá trình kết hợp định nghĩa aspect với chương trình mục tiêu để tạo ra hệ thống có hành vi mong muốn. Có công nghệ làm việc này khi biên dịch source, có công nghệ biến đổi bytecode đã biên dịch, có công nghệ dệt khi class được nạp; cũng có cách **can thiệp động qua proxy/interceptor trong lúc chương trình chạy** mà không phải dệt trực tiếp vào bytecode target.

Câu hỏi trọng tâm:

> **Hành vi cắt ngang đã được đưa vào mã/chương trình trước khi chạy, được biến đổi khi class được nạp, hay được định tuyến qua một lớp trung gian ở runtime?**

Từ đó suy ra nhiều khác biệt rất thực tế: các join point có thể nhìn thấy, tác động tới private/self-invocation, chi phí mỗi lần gọi, lúc phát hiện lỗi cấu hình, phạm vi cập nhật, khả năng bật/tắt, kiểm thử và vận hành.

Cần đặc biệt lưu ý hai điều:

1. **“Dệt tĩnh” không có nghĩa advice chỉ chạy khi biên dịch.** Chỉ **việc kết hợp** diễn ra trước runtime; advice vẫn chạy khi các join point tương ứng xuất hiện trong chương trình đang chạy.
2. **“Can thiệp động lúc chạy” không tự đồng nghĩa với load-time weaving.** Class transformation khi nạp và lời gọi đi qua proxy runtime là những cơ chế khác nhau, dù đều xảy ra muộn hơn giai đoạn build.

## 2. Những khái niệm nền tảng

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| **Join point** | Một vị trí/sự kiện thực thi được mô hình AOP công nhận, ví dụ method call hoặc method execution |
| **Pointcut** | Quy tắc chọn tập join point |
| **Advice** | Hành vi thực hiện trước/sau/bao quanh điểm được chọn |
| **Aspect** | Đơn vị tổ chức pointcut, advice và chính sách liên quan |
| **Target** | Chương trình, class hoặc đối tượng có hành vi được can thiệp |
| **Weaver** | Thành phần kết hợp aspect vào chương trình, có thể bằng thay đổi mã nguồn/bytecode |
| **Woven artifact** | Mã hoặc class artifact sau quá trình weaving |
| **Proxy / interceptor** | Lớp trung gian nhận lời gọi và quyết định chuyển tiếp/chạy hành vi bổ sung |
| **Instrumentation** | Cơ chế quan sát hoặc biến đổi mã ở cấp JVM/bytecode, thường có phạm vi và quyền cụ thể |

Điểm thường nhầm là **weaver** với **advice**. Weaver có trách nhiệm *đưa hành vi vào đúng điểm*; advice chỉ là *hành vi muốn chạy*. Một advice `before` có thể được kích hoạt ở runtime cho dù nó đã được dệt từ lúc build.

Một pointcut mô tả nhóm phương thức `public int price(int)` có thể có ý nghĩa về mặt ngữ nghĩa, nhưng một proxy chỉ bắt lời gọi đi qua nó có thể không thấy lời gọi trực tiếp vào target. Ngược lại, một weaver ở mức bytecode có khả năng nhìn các điểm sâu hơn **nếu** mô hình join point và vùng mã được dệt hỗ trợ.

## 3. “Dệt tĩnh” gồm các thời điểm nào?

### 3.1. Compile-time weaving: kết hợp trong quá trình biên dịch

**Compile-time weaving (CTW)**: định nghĩa aspect được kết hợp với chương trình khi build/biên dịch. Trình biên dịch hoặc pipeline dệt tạo artifact có hành vi AOP đã tích hợp.

Sơ đồ:

~~~text
Source nghiệp vụ + định nghĩa aspect
                |
                v
      Build / Compiler / Weaver
                |
                v
  Artifact đã dệt (class / executable)
                |
                v
         Chạy trên runtime
                |
                v
      Join point -> advice chạy
~~~

Ví dụ ý niệm: người dùng gọi `price(2)` qua một class đã được biên dịch và dệt. Khi method chạy, mã advice được chèn/ghép sẽ thực thi theo thứ tự do công cụ xác định. Thực thi advice **vẫn diễn ra ở runtime**, nhưng không cần chờ mỗi lời gọi mới tìm cách gắn aspect vào target.

**Ưu điểm có thể có:**

- Phát hiện một số vấn đề khớp điểm, cú pháp hoặc tương thích khi build thay vì để chạy thực tế mới phát hiện.
- Tạo ra artifact có phạm vi instrumentation rõ ràng, dễ kiểm thử và phát hành như một đơn vị.
- Với weaving thực sự ở class/bytecode, có thể quan sát những join point không phụ thuộc ranh giới proxy, tùy khả năng công cụ.
- Không cần định tuyến **mọi** lời gọi qua một object proxy bổ sung chỉ để có interception.

**Đổi lại:**

- Thay đổi định nghĩa dệt thường đòi hỏi build lại artifact liên quan rồi phát hành lại.
- Phải kiểm soát thứ tự các bước biên dịch, loại bytecode, tương thích thư viện và reproducible build.
- Debugging đôi khi thấy mã đã biến đổi khác source ban đầu; cần symbol/source mapping thích hợp.
- Một artifact được dệt không tự bảo đảm can thiệp mọi class: chỉ những class và join point thực sự được đưa vào phạm vi dệt.

### 3.2. Post-compile / binary weaving: dệt lên output đã biên dịch

Cũng thuộc nhóm chuẩn bị **trước khi chạy**, nhưng không nhất thiết xảy ra trong chính bước biên dịch source:

~~~text
Source -> javac -> các file .class / JAR
                           |
Aspect definitions --------+
                           |
                           v
                Binary / post-compile weaver
                           |
                           v
                  Class / JAR đã dệt
                           |
                           v
                      Run
~~~

Mục đích là áp dụng transformation lên bytecode đã có, có thể hữu ích khi một phần code được tạo bởi công cụ khác hoặc khi build pipeline tách compilation và weaving. Tuy nhiên, việc dệt thư viện bên thứ ba có thành công hay được phép hay không phụ thuộc công nghệ, phạm vi, license, module và tương thích bytecode.

**Compile-time weaving và binary weaving đều là những cách chuẩn bị artifact sớm**, nhưng pipeline, nguồn vào, khả năng debug và phạm vi thao tác có thể khác nhau. Không nên đánh đồng “dệt tĩnh” với “chỉ có một loại compiler đặc biệt”.

## 4. Load-time weaving: dệt khi class được nạp

**Load-time weaving (LTW)** thường chỉ việc áp dụng transformation khi một class đang được JVM nạp/định nghĩa. Nó nằm trên trục **thời điểm dệt** giữa build và các lời gọi về sau.

~~~text
Artifact chưa dệt
        |
        v
Classloader / quá trình định nghĩa class
        |
        +--> ClassFileTransformer / weaver (nếu cấu hình)
        |
        v
Class đã được định nghĩa với bytecode được dệt
        |
        v
Các method của class được thực thi
~~~

LTW **không nhất thiết là proxy**:

- Class bytecode có thể được biến đổi **trước khi JVM định nghĩa class đó**.
- Sau khi class được định nghĩa, các lời gọi vào method có thể thực thi hành vi đã được đưa vào bytecode mà không cần đi qua một wrapper object do ứng dụng tạo.
- Một proxy runtime thường **không sửa bytecode của target**; nó chỉ nhận lời gọi qua một tham chiếu wrapper và gọi tiếp target.
- Những class đã được định nghĩa trước khi transformer được thiết lập có thể không được xử lý bởi LTW thông thường; muốn can thiệp có thể cần khả năng **retransformation/redefinition** riêng của cơ chế instrumentation, với hạn chế cụ thể.

Vì thế không nên gọi mọi AOP engine “dynamic” là LTW, cũng không gọi mọi runtime interceptor là công cụ bytecode weaving.

**Khi kiểm tra LTW**, phải xác minh classloader, thứ tự nạp lớp, điều kiện lọc class, JDK modules, tương thích JVM/bytecode, khả năng mở module, và việc agent/weaver được khởi tạo đủ sớm. Một cấu hình pointcut đúng nhưng transformer đến sau lúc class được nạp có thể không tạo join point như mong muốn.

## 5. Can thiệp động qua proxy/interceptor trong runtime

Mô hình rất phổ biến là **định tuyến lời gọi qua proxy**. Caller giữ một tham chiếu tới đối tượng trung gian. Proxy quyết định thực hiện before/after/around rồi chuyển lời gọi tới target.

~~~text
Caller
  |
  v
Proxy / Interceptor
  |
  +--> pointcut/condition match?
  |      |
  |      +-- yes: advice trước/bao quanh
  |      +-- no:  chuyển tiếp trực tiếp
  |
  v
Target method
  |
  v
Proxy nhận kết quả hoặc lỗi
  |
  +--> advice sau / xử lý theo hợp đồng
  |
  v
Caller
~~~

**Điểm mấu chốt:** interception là **thuộc tính của đường gọi**. Nếu caller giữ target thật và gọi thẳng `target.price()` thay vì `proxy.price()`, proxy không thấy sự kiện đó. Tương tự, trong một mô hình proxy đơn giản, `this.otherMethod()` từ bên trong target cũng không đi qua tham chiếu proxy.

Khả năng cụ thể tùy kỹ thuật proxy:

- **Interface-based proxy:** có thể intercept những phương thức được truy cập qua interface proxy; cần target/contract tương thích.
- **Subclass-based proxy:** có thể override các phương thức đủ điều kiện, nhưng bị ràng buộc bởi `final`, `private` và quy tắc truy cập/override Java.
- **Interceptor chain:** có thể bao nhiều advice quanh một lời gọi và điều khiển thứ tự, nhưng không tự tạo được join point ở mọi constructor, field access hoặc catch handler.

### 5.1. “Động” có thể chỉ là thay đổi chính sách, không phải weaving lại

Một proxy có thể được tạo một lần khi ứng dụng khởi động, nhưng điều kiện bên trong được bật/tắt bằng cấu hình runtime. Ví dụ biến `enabled` quyết định lời gọi `price()` có đi qua code audit hay không.

Điều đó chứng minh **interception behavior linh hoạt theo runtime**, không chứng minh chương trình đã **viết lại bytecode** trong lúc chạy. Cơ chế điều khiển chính sách và cơ chế đưa join point vào đường gọi là hai vấn đề khác nhau.

Không được hứa rằng mọi loại proxy có thể bật/tắt không cần khởi động lại: nếu cấu hình được đọc một lần lúc khởi tạo, thay đổi file cấu hình ngoài cũng có thể chưa được áp dụng. Khả năng hot update phụ thuộc architecture/configuration thật.

### 5.2. JDK proxy và Reflection

Với Java, `java.lang.reflect.Proxy` tạo một implementation proxy cho interface; `InvocationHandler` nhận lời gọi, thông tin `Method` và các đối số. Handler có thể gọi target bằng Reflection, nhưng nếu target ném lỗi thì `Method.invoke()` bọc nguyên nhân trong `InvocationTargetException`. Adapter cần bóc `getCause()` phù hợp để giữ loại ngoại lệ mà caller nên nhận.

Bản thân `Proxy` không định nghĩa một pointcut language tổng quát. Phần điều kiện khớp (tên method, annotation, signature, v.v.) là logic của interceptor hoặc thư viện áp dụng lên proxy.

## 6. Bảng so sánh kiến trúc

| Tiêu chí | Static CTW / binary weaving | Load-time weaving | Runtime proxy / interceptor |
| --- | --- | --- | --- |
| **Khi kết hợp vào chương trình** | Trước lúc chạy, trong hoặc sau build | Khi class được nạp/định nghĩa | Khi caller đi qua wrapper trong runtime |
| **Artifact phát hành** | Thường chứa class đã dệt | Có thể là class gốc + weaver/agent/config | Có thể giữ target class gốc, tạo proxy trong app |
| **Chỉnh sửa bytecode target** | Có thể có, theo cơ chế weaving | Thường có transformation lúc nạp | Không cần sửa bytecode target |
| **Phạm vi** | Các class/join point đã dệt | Các class/join point thực sự qua weaver khi load | Các lời gọi đi qua proxy và method proxy hỗ trợ |
| **Self-invocation trực tiếp** | Có thể quan sát nếu join point được dệt phù hợp | Có thể quan sát nếu class đã được dệt | Thường bypass proxy khi dùng `this.method()` |
| **Private/final method** | Tùy loại join point/weaver, không bị đồng nhất với quy tắc override của proxy | Tùy bytecode/engine/module | Bị giới hạn mạnh với cơ chế cần interface hoặc override |
| **Chi phí runtime** | Có mã advice và overhead do weaving tạo | Có thể phát sinh transform khi class load; advice vẫn có runtime overhead | Dispatch proxy/handler, pointcut runtime, advice và Reflection tùy triển khai |
| **Đổi aspect** | Thường phải build/deploy lại phần liên quan | Cần cấu hình lại và nạp/biến đổi class phù hợp | Có thể đổi handler/cấu hình nếu kiến trúc thiết kế cho phép |
| **Phát hiện lỗi sớm** | Có thể tại build | Nhiều lỗi xuất hiện khi load/deploy | Nhiều lỗi đường gọi/khớp có thể xuất hiện trong runtime |
| **Debugging** | Cần hiểu artifact đã dệt và symbol | Cần quan sát quá trình class loading + transformation | Cần hiểu chuỗi wrapper, thứ tự advice và các đường bypass |
| **Khả năng hot change** | Không mặc định có | Không mặc định có; reload/retransform có giới hạn | Chính sách có thể đổi runtime, nhưng không tự mở rộng đường gọi |
| **Đánh đổi vận hành** | Kiểm soát build artifact | Cần kiểm soát lifecycle classloader/agent | Cần quản lý proxy ownership, object identity và registration |

**Không có kết luận “cách A luôn nhanh hơn cách B”.** Số lượng join point, mức độ tối ưu của engine, Reflection, số tầng advice, class load behavior, memory và tần suất gọi có thể làm kết quả rất khác. So sánh công bằng cần benchmark trên workload đại diện.

## 7. Ví dụ Java 17 chạy độc lập: code chuẩn bị sẵn và proxy có thể bật/tắt

Ví dụ dưới đây cho thấy **hai cách tổ chức luồng gọi dễ quan sát**:

1. `PreparedDecorator` là wrapper có hành vi đo/ghi được xác định từ source và có thể biên dịch thành class trước khi chạy. **Đây là phép so sánh sư phạm cho một artifact đã chuẩn bị, KHÔNG PHẢI một bộ compiler/bytecode weaver.**
2. `runtimeProxy(...)` sử dụng JDK dynamic proxy, chọn việc chạy advice theo `AtomicBoolean enabled` và thông tin `Method` của lời gọi. Nó minh họa **runtime interception thật**, nhưng chỉ trên những lời gọi đi qua proxy.

Cả hai triển khai cùng interface `Pricing`. Trong code, ta quan sát `price` thành công, `price` ném lỗi, `status` bỏ qua pointcut, `batch` gọi nội bộ qua `this.price()`, và việc bật/tắt advice của proxy sau khi proxy đã tạo.

Tạo file `WeavingModesDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WeavingModesDemo {
    interface Pricing {
        int price(int units);
        int batch(int units);
        String status();
    }

    static final class Core implements Pricing {
        @Override
        public int price(int units) {
            if (units <= 0) {
                throw new IllegalArgumentException("units must be positive");
            }
            System.out.println("BUSINESS price " + units);
            return units * 100;
        }

        @Override
        public int batch(int units) {
            System.out.println("BUSINESS batch");
            return this.price(units); // direct internal call; does not use proxy
        }

        @Override
        public String status() {
            return "READY";
        }
    }

    // A build-fixed wrapper, only an analogy for a prepared code artifact;
    // this is not a bytecode weaver.
    static final class PreparedDecorator implements Pricing {
        private final Pricing target;

        PreparedDecorator(Pricing target) {
            this.target = target;
        }

        @Override
        public int price(int units) {
            System.out.println("PREPARED before price");
            try {
                int result = target.price(units);
                System.out.println("PREPARED after price");
                return result;
            } catch (RuntimeException error) {
                System.out.println("PREPARED throwing "
                        + error.getClass().getSimpleName());
                throw error;
            }
        }

        @Override
        public int batch(int units) {
            return target.batch(units);
        }

        @Override
        public String status() {
            return target.status();
        }
    }

    static Object invokeTarget(Pricing target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Pricing runtimeProxy(Pricing target, AtomicBoolean enabled) {
        return (Pricing) Proxy.newProxyInstance(
                Pricing.class.getClassLoader(),
                new Class<?>[]{Pricing.class},
                (proxy, method, args) -> {
                    if (!enabled.get() || !method.getName().equals("price")) {
                        return invokeTarget(target, method, args);
                    }
                    System.out.println("RUNTIME before price");
                    try {
                        Object result = invokeTarget(target, method, args);
                        System.out.println("RUNTIME after price");
                        return result;
                    } catch (Throwable error) {
                        System.out.println("RUNTIME throwing "
                                + error.getClass().getSimpleName());
                        throw error;
                    }
                });
    }

    public static void main(String[] args) {
        Pricing prepared = new PreparedDecorator(new Core());
        System.out.println("PREPARED result=" + prepared.price(2));

        AtomicBoolean enabled = new AtomicBoolean(false);
        Pricing dynamic = runtimeProxy(new Core(), enabled);
        System.out.println("DYNAMIC OFF result=" + dynamic.price(3));

        enabled.set(true);
        System.out.println("DYNAMIC ON result=" + dynamic.price(3));
        try {
            dynamic.price(0);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }

        System.out.println("DYNAMIC batch=" + dynamic.batch(1));
        System.out.println("DYNAMIC status=" + dynamic.status());
        enabled.set(false);
        System.out.println("DYNAMIC OFF AGAIN result=" + dynamic.price(1));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 WeavingModesDemo.java
java WeavingModesDemo
~~~

Kết quả đã xác minh bằng biên dịch và chạy thực:

~~~text
PREPARED before price
BUSINESS price 2
PREPARED after price
PREPARED result=200
BUSINESS price 3
DYNAMIC OFF result=300
RUNTIME before price
BUSINESS price 3
RUNTIME after price
DYNAMIC ON result=300
RUNTIME before price
RUNTIME throwing IllegalArgumentException
CALLER units must be positive
BUSINESS batch
BUSINESS price 1
DYNAMIC batch=100
DYNAMIC status=READY
BUSINESS price 1
DYNAMIC OFF AGAIN result=100
~~~

### 7.1. Đọc kết quả của wrapper chuẩn bị từ trước

Lời gọi `prepared.price(2)` qua `PreparedDecorator` luôn in `PREPARED before`, gọi target rồi in `PREPARED after`. Wrapper được viết rõ trong source và biên dịch trước khi chạy; không có một switch runtime trong ví dụ để thay đổi bộ chọn của wrapper này.

**Đây là một ví dụ về *prepared composition*, không phải CTW thực sự**: class `Core` không bị sửa và nếu caller giữ `Core` rồi gọi trực tiếp thì decorator không can thiệp. Một công cụ weaving đúng nghĩa có thể thay đổi bytecode hoặc artifact để can thiệp sâu hơn. Việc nêu khác biệt này giúp tránh kết luận sai rằng mọi lớp decorator đều tương đương với static weaving.

### 7.2. Runtime proxy bật/tắt hành vi

Lần `dynamic.price(3)` đầu tiên có `enabled=false`: proxy chuyển tiếp thẳng và chỉ thấy `BUSINESS price 3`.

Khi `enabled.set(true)`, **cùng đối tượng proxy ấy** bắt đầu ghi `RUNTIME before/after` cho `price`. `AtomicBoolean` cho phép các thread quan sát trạng thái bật/tắt với ngữ nghĩa đồng thời phù hợp, nhưng **không dệt thêm bytecode vào `Core`**. Nó chỉ thay đổi chính sách khi lời gọi đã đi qua proxy.

Sau khi `enabled=false` lần nữa, hành vi bổ sung dừng ở đường này. Tính linh hoạt do code proxy đã thiết kế sẵn, không phải một khả năng hot-reload mọi aspect trên mọi công nghệ.

### 7.3. Lời gọi nội bộ minh họa ranh giới của proxy

`dynamic.batch(1)` vào proxy, nhưng handler chỉ chọn tên `price`. `Core.batch()` thực hiện `this.price(units)` **trực tiếp trên target**, nên không đi qua proxy `dynamic`. Ta thấy `BUSINESS batch` và `BUSINESS price 1` nhưng không có `RUNTIME before price`.

Nếu một weaver có thể chèn advice tại **execution join point** của `Core.price`, lời gọi nội bộ có thể vẫn được quan sát khi method chạy — nhưng chỉ nếu class và loại join point ấy thực sự được weaving. Không tự suy ra điều đó từ việc một công cụ gắn nhãn “AOP”.

### 7.4. Giữ nguyên ngoại lệ target

`dynamic.price(0)` ném `IllegalArgumentException`. Handler gọi target qua Reflection, nhận wrapper `InvocationTargetException` rồi bóc nguyên nhân bằng `getCause()`. Do đó caller nhìn thấy đúng `IllegalArgumentException: units must be positive`, thay vì một ngoại lệ wrapper tùy ý.

Việc giữ nguyên lỗi là ví dụ về **hợp đồng advice chỉ quan sát**. Nếu advice có chủ đích dịch ngoại lệ hoặc fallback, đó là một chính sách khác cần tài liệu và test.

## 8. Runtime instrumentation không chỉ có proxy

Bên cạnh proxy, nền tảng JVM còn có **instrumentation** có thể tương tác với class loading và biến đổi lớp. Một số cơ chế dùng Java agent để đăng ký `ClassFileTransformer`; với khả năng và cấu hình thích hợp, agent có thể tham gia vào quá trình định nghĩa class hoặc thực hiện **retransformation/redefinition** của class đã tải.

Cần phân biệt ba câu hỏi:

1. **Có hook nhận bytecode không?** Transformer có thể được gọi khi load hoặc khi yêu cầu retransform, tùy lifecycle và quyền được cấp.
2. **Có thực sự biến đổi bytecode không?** Chỉ log việc nạp lớp bằng transformer chưa đồng nghĩa với weaving advice vào method.
3. **Có được phép đổi cấu trúc/mã theo cách muốn không?** Các API redefinition/retransformation có những ràng buộc về thay đổi class, JVM implementation, capability, module và thời điểm.

Một **hot instrumentation agent** có thể là một hình thức can thiệp động khác proxy, nhưng không thể khẳng định nó luôn thay mọi method hoặc có thể tháo gỡ advice để khôi phục trạng thái hoàn toàn mà không tác động gì. Khi thiết kế cần kiểm chứng phiên bản JDK, tùy chọn agent, lớp đã tải, bytecode version, classloader, quyền và các hạn chế của công cụ.

### 8.1. Vì sao không nên lẫn LTW với retransformation?

- **Load-time weaving:** thường tác động **trước hoặc trong lúc class được định nghĩa**, rồi các method của class đã biến đổi chạy về sau.
- **Retransformation/redefinition:** cố gắng biến đổi **class đã được định nghĩa**, theo khả năng mà runtime và agent cho phép.
- **Proxy:** không buộc phải biến đổi class target; caller phải đi qua wrapper.

Cả ba có thể xảy ra khi JVM đang chạy, nhưng điểm tác động và giới hạn không giống nhau. Với một bài toán cần tracing production, việc lựa chọn giữa chúng liên quan đến khả năng vận hành và an toàn của runtime chứ không đơn thuần là “muốn động thì dùng load-time”.

## 9. Join point nào có thể được can thiệp?

Khả năng intercept phụ thuộc **mô hình join point** và **kỹ thuật dệt/chuyển tiếp**, không chỉ thời điểm.

| Loại điểm | Proxy method-call điển hình | Weaving ở class/bytecode khi được hỗ trợ |
| --- | --- | --- |
| Lời gọi method qua interface proxy | Có thể | Có thể nếu chọn và dệt điểm phù hợp |
| `this.method()` nội bộ target | Thường bypass proxy | Có thể quan sát method execution đã dệt |
| Private method | Không là phương thức interface public để proxy kiểu đó intercept | Có thể nếu engine hỗ trợ, class nằm trong phạm vi |
| Final method | Có thể được gọi qua interface proxy, nhưng subclass proxy gặp giới hạn override | Có thể theo cơ chế bytecode và join point model |
| Static method | Không là method instance trên interface proxy Java thông thường | Có thể nếu engine hỗ trợ loại execution/call ấy |
| Constructor | Không tương đương một method instance call qua proxy đã tạo | Tùy mô hình join point và vùng dệt |
| Field get/set | Không tự thấy qua method proxy trừ khi access được đưa qua API method | Có thể nếu engine định nghĩa field join point |
| Exception handler/throw | Proxy thấy exceptional exit của method, không tự thấy mỗi `catch` bên trong | Tùy engine hỗ trợ các join point tương ứng |
| Method trả Future | Proxy có thể thấy Future được trả ngay | Weaving method cũng chưa tự đo được Future completion |

Một nhận định tuyệt đối kiểu “proxy không bao giờ intercept final method” cũng có thể sai nếu proxy nằm ở **interface bên ngoài** và chuyển tiếp tới implementation final; hạn chế **override** khác với khả năng đứng trước lời gọi qua interface. Luôn xác định proxy là interface hay subclass-based.

## 10. Khi nào nên chọn dệt tĩnh?

Static weaving phù hợp hơn khi cần:

- Kiểm soát rõ artifact được dệt cùng version của code và aspect.
- Phát hiện một số vấn đề khi build, chạy test trên **chính artifact sẽ phát hành**.
- Can thiệp sâu vào các join point mà proxy đường gọi không quan sát được, khi công nghệ hỗ trợ.
- Tránh việc ứng dụng phải giữ và truyền tham chiếu proxy cho mọi đường gọi cần quan sát.
- Có CI/CD ổn định, khả năng rebuild và release nhất quán mỗi khi cấu hình aspect thay đổi.

Nhưng static weaving có thể kém phù hợp nếu:

- Không kiểm soát được pipeline build hoặc class cần can thiệp.
- Phải thay đổi phạm vi theo môi trường sau khi artifact đã phát hành mà không muốn tạo build khác.
- Việc bytecode weaving gây khó khăn với mã generated, module, bộ công cụ debug hoặc quy tắc packaging.
- Không có cách đảm bảo các class được dệt chính xác và không bị dệt nhiều lần.

**Gợi ý:** ghi nhận weaving configuration và version như một phần của build artifact; có integration test kiểm tra behavior thực trên artifact sau dệt, thay vì chỉ test source trước dệt.

## 11. Khi nào nên chọn interception động qua proxy?

Proxy/interceptor thích hợp khi:

- Chính sách áp dụng ở **ranh giới API hoặc hợp đồng interface rõ ràng**, ví dụ service call.
- Có thể bảo đảm caller sử dụng **đối tượng proxy**, không giữ target gốc ở nơi cần advice.
- Muốn thay điều kiện bật/tắt một số hành vi runtime qua cấu hình rõ và có khả năng quan sát.
- Cần thay interceptor chain, sử dụng dependency injection/factory hoặc lắp middleware có kiểm soát.
- Không muốn chạy một bytecode weaver hoặc agent can thiệp class.

Nhưng phải đề phòng:

- **Bypass:** caller gọi trực tiếp target hoặc `this.method()` nội bộ.
- **Identity/lifecycle:** `proxy != target`; `equals`/`hashCode`, serialization, type checks và method reflection có thể cho behavior cần xem xét.
- **Kiểu API:** interface proxy không phải subtype của class implementation; cast sang implementation có thể thất bại.
- **Overload:** pointcut chỉ theo tên method dễ chọn nhầm chữ ký.
- **Hiệu năng:** số tầng interceptor, Reflection, logging và điều kiện runtime tăng overhead; phải đo thực tế.
- **Security:** nếu advice là lớp bảo vệ duy nhất, đường đi không qua proxy có thể là lỗ hổng kiến trúc.

Đừng chọn proxy chỉ vì “nhanh hơn” hoặc weaving vì “mạnh hơn” mà không đối chiếu tập join point bắt buộc và đường gọi trong hệ thống.

## 12. Load-time weaving trong vận hành thực tế

Nếu chọn load-time weaving, hãy lập checklist kỹ thuật:

1. **Class nào được phép dệt?** Xác định tập package/class rõ, loại trừ lớp hạ tầng của chính agent nếu cần.
2. **Transformer được đăng ký trước khi class mục tiêu được định nghĩa chưa?** Kiểm tra trình tự khởi tạo.
3. **Ai nạp class?** Một ứng dụng có thể có nhiều classloader; cùng tên class ở các loader khác nhau không nhất thiết là cùng runtime class.
4. **Bytecode có hợp lệ?** Transformation phải giữ tính hợp lệ của stack map frames, method descriptors và verifier rules của JVM.
5. **Module boundary được xử lý hợp lệ?** Một lớp có thể không được phép reflective access hoặc instrument theo cách kỳ vọng nếu modules đóng.
6. **Khi class load lỗi thì sao?** Xác định fail-open/fail-closed và thông tin chẩn đoán.
7. **Có class nào đã load sẵn không?** Nếu có, LTW thông thường có thể bỏ lỡ, phải đánh giá khả năng retransformation riêng.
8. **Có dùng thư viện bên thứ ba không?** Kiểm tra compatibility, quyền đóng gói và tương thích bytecode.
9. **Debug được không?** Thiết lập logging transformation có kiểm soát, stack trace và nguồn symbol phù hợp.
10. **Kiểm soát cấu hình theo release:** thay đổi agent/weaver phải có test trên môi trường tương đương production.

Không nên coi “agent đã khởi động” là bằng chứng mọi pointcut đã khớp. Phải kiểm chứng bằng một bài test **gọi thật** qua đúng join point và so khớp kết quả với behavior mong muốn.

## 13. Sai lầm thường gặp trong phân loại

### 13.1. “Weaving tĩnh nghĩa là hành vi không thể quyết định động”

Không đúng. Một artifact đã được dệt có thể chứa advice kiểm tra giá trị đối số, trạng thái runtime hoặc chính sách bật/tắt. **Thời điểm cài điểm can thiệp** khác **thời điểm tính điều kiện trong advice**. Điều kiện có thể vẫn là runtime.

### 13.2. “Load-time weaving cũng là dynamic proxy”

Không đúng. LTW thường xử lý bytecode trước khi class được định nghĩa; proxy có thể đứng trước lời gọi mà không chỉnh bytecode target.

### 13.3. “Runtime proxy chắc chắn chặn được tất cả lời gọi”

Không đúng. Đường bypass, self-invocation, private method và lời gọi trực tiếp vào target thường nằm ngoài vùng quan sát của một proxy thông thường.

### 13.4. “Không sửa bytecode thì không phải AOP”

Không đúng. Một hệ thống áp dụng pointcut và advice thông qua proxy/interceptor vẫn có thể hiện thực các mục tiêu AOP, miễn ranh giới áp dụng đã được hiểu đúng. Nhưng gọi nó là **bytecode weaving** là thiếu chính xác.

### 13.5. “Động luôn thay đổi được mà không restart”

Không đúng. Cấu hình có thể được chốt tại startup; agent có thể không hỗ trợ thay đổi class đang hoạt động; proxy có thể không có cơ chế reload. **Hot update** là một năng lực phải được thiết kế và kiểm thử riêng.

### 13.6. “Weaving lúc build chắc chắn không overhead ở runtime”

Không đúng. Advice, logging, tracing và logic bổ sung vẫn tiêu tốn CPU/bộ nhớ/I/O khi chạy. Static weaving chỉ thay đổi **cách đưa hành vi vào chương trình**, không làm hành vi miễn phí.

### 13.7. “Weaving lúc nạp lớp giống instrumentation đã chạy từ trước”

Không đúng. Nếu class đã được load trước khi transformer đăng ký, không thể mặc định nó sẽ được dệt bởi cơ chế chỉ can thiệp load-time.

### 13.8. “Chọn đúng pointcut là lời gọi chắc chắn được can thiệp”

Không đúng. Cần xét riêng **matching**, **phạm vi weaving/proxy**, **đường gọi thực** và **thứ tự advice**. Test pointcut trong môi trường mô phỏng không thay thế test integration.

## 14. Kiểm thử theo từng kiến trúc

Khi triển khai AOP, hãy xây bộ test thể hiện rõ ba nhóm: matching, interception và preservation.

| Ca thử | Dệt tĩnh | Dệt khi load | Proxy runtime |
| --- | --- | --- | --- |
| Gọi method public ở ranh giới cần advice | Test artifact sau build | Test class được định nghĩa qua weaver | Gọi qua proxy |
| Gọi trực tiếp vào target | Test có advice tại method execution đã dệt hay không | Test class có được dệt và điểm hỗ trợ không | Thường bypass |
| Gọi `this.method()` | Test đúng join point đã dệt | Tương tự, phụ thuộc scope | Thường bypass |
| Private/final/static | Xác minh mô hình pointcut | Xác minh engine/module | Xác minh kiểu proxy và giới hạn |
| Normal return | Giữ nguyên return value khi chỉ quan sát | Tương tự | Tương tự |
| Throw exception | Giữ nguyên lỗi gốc khi chỉ quan sát | Tương tự | Nếu Reflection, bóc wrapper phù hợp |
| Hai advice cùng khớp | Kiểm precedence | Kiểm precedence và load order | Kiểm thứ tự interceptor |
| Thay đổi aspect | Rebuild/redeploy theo quy trình | Reconfigure/reload theo capability | Thay handler/policy nếu thiết kế cho phép |
| Class/library từ bên thứ ba | Kiểm được dệt không | Kiểm loader, agent, module | Chỉ các API có đường đi qua proxy |
| Bật/tắt runtime | Kiểm behavior đã dệt có guard không | Kiểm configuration thực | Kiểm trạng thái handler và concurrent calls |

Một integration test tối thiểu nên chứng minh: **đúng điểm được chọn**, **sai điểm không được chọn**, **return/exception giữ nguyên**, **số lần target chạy không đổi ngoài chủ đích**, và **bypass được nhận diện đúng**.

### 14.1. Kiểm tra metadata không đủ

Phát hiện một `Method` thông qua Reflection hoặc quét annotation không chứng minh advice có chạy. Với proxy, caller phải dùng proxy; với weaving, class thực sự phải được xử lý, và pointcut phải chọn được join point xuất hiện.

### 14.2. Kiểm thử với nhiều classloader/module

Một ứng dụng plugin hoặc container có thể load nhiều class cùng tên từ classloader khác nhau. Weaving configuration theo tên class thôi chưa chắc phù hợp mọi loader. Khi có module boundary, test bằng cấu hình runtime giống production để phát hiện vấn đề access hoặc class definition sớm.

### 14.3. Kiểm thử concurrent và thay cấu hình động

Nếu có cờ bật/tắt runtime, test lúc nhiều lời gọi đang hoạt động: một lời gọi đã bắt đầu dưới policy cũ và lời gọi mới dưới policy mới có thể có trình tự khác nhau. **Không giả định bật/tắt sẽ hủy ngay một advice đã chạy một phần**, trừ khi implementation bảo đảm điều đó. `AtomicBoolean` trong ví dụ bảo đảm visibility cho việc đọc cờ nhưng không điều khiển lại quá khứ của một invocation.

## 15. Hiệu năng: đừng suy luận thay cho benchmark

Một phép đo công bằng cần phân biệt:

- **Startup/load cost:** transformer nạp lớp, verify bytecode, tạo proxy và đăng ký interceptor.
- **Steady-state per-call cost:** indirection, handler dispatch, dynamic pointcut check, logic advice và I/O của sink.
- **Memory pressure:** proxy objects, metadata, transformed bytecode, classloader state, allocations mỗi lời gọi.
- **Optimization effects:** JIT inlining, deoptimization, class redefinition và megamorphic call sites có thể ảnh hưởng khác nhau.
- **Operational cost:** thời gian build, debugging, compatibility và downtime do reconfiguration.

Trong cả hai mô hình, **logic advice** như ghi log network có thể là phần chi phí lớn nhất, che lấp khác biệt giữa proxy dispatch và woven call. Benchmark cần thực hiện với cùng workload, cùng advice, warm-up phù hợp và hệ thống thu metric không làm nhiễu kết quả.

Không dùng một vòng lặp đơn giản có `System.nanoTime()` để tuyên bố kiến trúc nào luôn nhanh hơn: kết quả có thể bị JIT tối ưu, khác phần cứng, khác số tầng wrapper hoặc GC.

## 16. Hướng dẫn lựa chọn theo tình huống

| Tình huống | Phương án đáng cân nhắc | Lý do |
| --- | --- | --- |
| Audit mọi lần method execution kể cả self-invocation, công cụ có hỗ trợ | Bytecode weaving trong phạm vi được kiểm soát | Proxy ngoài không chắc nhìn lời gọi nội bộ |
| Tracing các API qua interface rõ ràng | Proxy/interceptor | Ranh giới đơn giản, dễ chọn và kiểm thử |
| Muốn lỗi weaving lộ ra trong CI | Compile-time hoặc binary weaving | Kiểm tra được artifact đã dệt từ build |
| Không muốn thay artifact gốc nhưng có quyền cấu hình agent từ đầu | Load-time weaving | Kết hợp khi class được nạp |
| Cần hot instrumentation trên class đã load | Runtime retransformation nếu nền tảng hỗ trợ | Khác với load-time thông thường, có giới hạn riêng |
| Muốn bật/tắt metrics trên proxy đã tạo | Handler có cờ/policy cập nhật runtime | Không cần thay bytecode nếu mọi lời gọi qua proxy |
| Code gọi target trực tiếp, không có ranh giới wrapper đáng tin | Weaving hoặc refactor đường gọi | Proxy đơn lẻ dễ bị bypass |
| Yêu cầu bảo mật bắt buộc trên mọi đường | Thiết kế enforcement tại ranh giới tin cậy, không chỉ “gắn advice” | Cần chứng minh mọi đường được bao phủ |

**Không nhất thiết chỉ được dùng một mô hình cho toàn ứng dụng.** Có thể dùng instrumentation ở lớp hạ tầng để tracing, proxy tại service boundary cho chính sách kỹ thuật và code nghiệp vụ rõ ràng cho bất biến miền. Nhưng khi kết hợp nhiều mô hình, nguy cơ **double-instrumentation** hoặc precedence khó hiểu tăng lên. Cần bản đồ các điểm đã instrument và test số lần advice chạy.

## 17. Checklist thiết kế trước khi chọn công nghệ

1. **Điểm cần can thiệp là gì?** Method call, execution, field access, constructor, exception handler hay task completion?
2. **Ai sở hữu code?** Source mình biên dịch hay thư viện đã đóng gói?
3. **Có đường bypass proxy không?** Gọi trực tiếp target, `this.method()`, callback, reflection?
4. **Cần thay ở thời điểm nào?** Build time, lúc class load, hay trong runtime đã nạp lớp?
5. **Có cần hot update thật không?** Nếu có, engine hỗ trợ đổi chính sách hay retransform ở mức nào?
6. **Có hạn chế môi trường không?** Java version, module, classloader, packaging, agent permissions?
7. **Phạm vi pointcut có ổn định?** Dựa trên API contract/annotation hay tên package dễ thay đổi?
8. **Mức bảo đảm là gì?** Quan sát “best effort” hay enforcement/audit bắt buộc?
9. **Ai được xử lý lỗi advice?** Fail-open/fail-closed và bảo toàn ngoại lệ gốc?
10. **Hiệu năng cần tối ưu gì?** Startup, throughput, latency, memory hay khả năng chẩn đoán?
11. **Kiểm thử artifact nào?** Source trước dệt chưa đủ; phải test thành phẩm hoặc đường proxy thật.
12. **Quan sát đủ các nhánh chưa?** Normal, throwing, recursion, async, self-invocation và concurrent reconfiguration.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Compile-time weaving hoàn tất trước khi ứng dụng chạy. Advice có chạy ở runtime không?  
**Đáp án:** Có. Việc **gắn/kết hợp** xảy ra khi build, còn hành vi advice vẫn thực thi khi join point xuất hiện ở runtime.

**Câu 2.** Proxy runtime với `AtomicBoolean` bật/tắt log có đang thay bytecode của target không?  
**Đáp án:** Không. Nó chỉ thay đổi điều kiện thực thi trong handler trên các lời gọi **đi qua proxy**.

**Câu 3.** Load-time weaving khác proxy ở đâu?  
**Đáp án:** LTW thường biến đổi class bytecode khi class được định nghĩa; proxy thường đứng trước lời gọi mà không sửa class target.

**Câu 4.** Nếu target gọi `this.price()` bên trong `batch()`, proxy ngoài có chặn `price()` không?  
**Đáp án:** Trong mô hình JDK proxy của ví dụ: không, vì đó là lời gọi trực tiếp bên trong target. Weaving tại execution có thể quan sát nếu công cụ hỗ trợ và class đã được dệt.

**Câu 5.** Static weaving có luôn nhanh hơn dynamic proxy không?  
**Đáp án:** Không có kết luận chung. Phải đo chi phí advice, mức tối ưu JIT, dispatch, pointcut, I/O, load và workload cụ thể.

**Câu 6.** Transformer được khởi tạo sau khi class đã load thì class ấy chắc chắn được dệt theo LTW không?  
**Đáp án:** Không. Có thể cần capability retransformation/redefinition riêng và vẫn bị các giới hạn JVM/công cụ.

**Câu 7.** Có thể sử dụng một static decorator viết tay để chứng minh thật sự đã bytecode weaving không?  
**Đáp án:** Không. Decorator chỉ minh họa hành vi wrapper đã chuẩn bị; bytecode weaving phải được kiểm tra ở artifact hoặc cơ chế transform thực.

**Câu 8.** Vì sao chỉ nhìn một pointcut match đúng không đủ xác nhận AOP hoạt động?  
**Đáp án:** Phải có join point thật xuất hiện ở đường gọi được weaver/proxy quan sát, advice được gắn theo phạm vi, và thứ tự/hợp đồng kết quả đúng.

## 19. Tổng kết

**Dệt tĩnh** kết hợp aspect vào artifact trước khi chương trình chạy — trong khi biên dịch hoặc hậu biên dịch — còn **load-time weaving** biến đổi class khi chúng được nạp/định nghĩa. **Can thiệp động qua proxy/interceptor** định tuyến các lời gọi qua lớp trung gian ở runtime và có thể đánh giá điều kiện áp dụng ở mỗi lần gọi mà **không cần biến đổi bytecode target**. Runtime instrumentation/retransformation trên class đã nạp lại là một nhánh khác, có capability và hạn chế riêng.

Lựa chọn đúng không chỉ là so “tĩnh hay động”: hãy xác định **loại join point cần quan sát, phạm vi class/đường gọi thực sự được kiểm soát, khả năng thay đổi, chi phí, tương thích runtime và mức độ bảo đảm**. Dùng integration test để chứng minh cả điểm được chọn lẫn điểm bị loại, bảo toàn return/exception và phát hiện đường bypass. Khi hiểu đúng **thời điểm kết hợp** tách biệt với **thời điểm advice chạy**, ta có thể thiết kế AOP rõ ràng, dự đoán được và dễ vận hành hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-004">Đầu vào, đầu ra và thời điểm áp dụng aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Đầu vào, đầu ra và thời điểm áp dụng aspect trong kiến trúc weaving (AOP)

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), **weaving** là quá trình kết hợp chương trình cơ sở với các **aspect** để hành vi bổ sung (*advice*) có hiệu lực tại các **điểm can thiệp** (*join point*) phù hợp với **pointcut**. Hiểu kiến trúc weaving không chỉ là nhớ thuật ngữ: người học cần biết **đầu vào nào được xử lý, kết quả kết hợp tồn tại dưới dạng nào và thời điểm nào việc kết hợp xảy ra**.

Có hai thời điểm rất dễ nhầm:

- **Thời điểm kết hợp aspect (weaving/composition):** một công cụ hoặc cơ chế chuẩn bị đường thực thi có chứa hành vi bổ sung, chẳng hạn trong build, khi nạp lớp hoặc khi tạo proxy.
- **Thời điểm advice thực thi:** chương trình đang chạy thực sự đi đến một join point được chọn, điều kiện động được thỏa và advice được kích hoạt.

Aspect có thể được weave lúc build nhưng advice chạy hàng nghìn lần sau khi triển khai. Một proxy có thể được tạo lúc khởi động nhưng advice không chạy lần nào nếu không ai gọi qua proxy. Vì vậy, **“aspect đã được kết hợp” không đồng nghĩa “advice đã chạy”**.

Bài học giải thích kiến trúc AOP tổng quát. Những công cụ khác nhau có các mô hình join point và cơ chế kết hợp khác nhau; không nên coi khả năng của một mô hình là cam kết cho mọi hệ AOP.

## 1. Mục tiêu

Sau bài, bạn có thể:

1. Xác định chương trình cơ sở, định nghĩa aspect, pointcut, advice, cấu hình, công cụ weaving và môi trường thực thi.
2. Mô tả **đầu vào** của compile-time, binary/post-compile, load-time và runtime composition.
3. Nhận diện **đầu ra** là bytecode/artifact đã kết hợp, định nghĩa lớp lúc nạp hay proxy/interceptor, tùy cơ chế.
4. Phân biệt **đánh giá điểm khớp tĩnh** với **kiểm tra điều kiện động** ở runtime.
5. Giải thích vì sao một lớp có aspect hoặc một pointcut hợp lệ chưa bảo đảm đường gọi thực tế được can thiệp.
6. So sánh các mốc áp dụng aspect theo độ phủ join point, ảnh hưởng build, nạp lớp, triển khai và bảo trì.
7. Kiểm thử các trường hợp trả về bình thường, ném lỗi, nhiều advice lồng nhau, lời gọi bỏ qua proxy và lớp chưa được weave.
8. Đọc và chạy ví dụ Java 17 mô phỏng runtime composition mà không nhầm nó với bytecode weaving.

## 2. Bản đồ kiến trúc weaving

Sơ đồ tư duy tổng quát:

```text
CHƯƠNG TRÌNH CƠ SỞ                   ĐỊNH NGHĨA ASPECT
  Mã nguồn / bytecode                 Pointcut + Advice
           |                                 |
           +-----------+   +-----------------+
                       |   |
                       v   v
                   [WEAVER / COMPOSER] <--- Cấu hình + thông tin kiểu
                           |
                           v
             KẾT QUẢ KẾT HỢP PHÙ HỢP
         Woven bytecode / class definition / proxy
                           |
                           v
                    CHƯƠNG TRÌNH CHẠY
                           |
                           v
              Join point thực sự xuất hiện?
                      /          \
                     Không        Có
                      |           |
                 Không advice    Kiểm tra điều kiện
                                      |
                                  Advice chạy
```

Một số mô hình không có bộ weaver biến đổi class file mà dùng chuỗi interceptor. Sơ đồ chỉ mô tả trách nhiệm logic. Đầu vào/đầu ra vật lý tùy cơ chế.

### 2.1. Thành phần nào làm việc gì?

| Thành phần | Chức năng |
| --- | --- |
| **Base program** | Chứa logic nghiệp vụ đang có, chẳng hạn `OrderService.submit()` |
| **Join point model** | Quy định loại điểm có thể can thiệp: method execution, call, field get/set, constructor… nếu được hỗ trợ |
| **Pointcut** | Chọn tập join point bằng kiểu, tên, chữ ký, ngữ cảnh hoặc điều kiện tương ứng |
| **Advice** | Quy định hành vi trước, sau thành công, sau lỗi hoặc bao quanh |
| **Aspect** | Đóng gói pointcut và chính sách advice |
| **Weaver/composer** | Kết hợp aspect với đường thực thi của chương trình |
| **Configuration** | Chọn lớp/đường được xử lý, loại trừ, thứ tự aspect, môi trường áp dụng |
| **Runtime** | Thực hiện chương trình đã kết hợp và kích hoạt advice tại join point |

Điểm can thiệp do **mô hình AOP** định nghĩa, không chỉ do cú pháp Java. Ví dụ, một JDK dynamic proxy chặn lời gọi qua phương thức giao diện, nhưng không tự nhìn thấy mọi lệnh truy cập field hoặc constructor.

## 3. Đầu vào: cần những gì để weave?

### 3.1. Chương trình nền

Tùy cơ chế, đầu vào có thể là:

- **Mã nguồn** ở giai đoạn biên dịch;
- **Bytecode** sau khi biên dịch, như các tệp `.class`;
- **Artifact** chứa bytecode và metadata, ví dụ JAR;
- **Bytecode ngay trước lúc JVM định nghĩa lớp**;
- **Đối tượng và hợp đồng phương thức/giao diện** dùng để lắp proxy ở runtime.

Mỗi dạng đầu vào dẫn tới phạm vi và cách kiểm chứng khác nhau. Bộ xử lý bytecode cần biết mã chứa join point; proxy runtime cần bảo đảm người gọi sử dụng đối tượng đại diện.

### 3.2. Định nghĩa aspect

Aspect mô tả ít nhất hai ý:

1. **Chọn điểm nào?** Pointcut có thể lọc loại join point, kiểu khai báo, package, chữ ký, annotation hoặc bối cảnh.
2. **Làm gì ở điểm đó?** Advice có thể ghi log, đo thời gian, kiểm toán hoặc thực thi một chính sách có quyền thay đổi hành vi.

Một số hệ AOP còn cần khai báo thứ tự ưu tiên khi nhiều aspect cùng khớp. Around advice có thể bao quanh chuỗi bên trong, nên `Timing(Retry(Operation))` khác `Retry(Timing(Operation))`.

### 3.3. Thông tin kiểu và phụ thuộc

Để tìm đúng phương thức, công cụ có thể cần thông tin:

- Lớp khai báo, kiểu cha và giao diện;
- Danh sách kiểu/thứ tự tham số, kiểu trả về, modifier;
- Annotation hoặc metadata;
- Các lớp được advice tham chiếu;
- Classpath/module path và class loader;
- Phiên bản JDK, compiler, công cụ weaving tương ứng.

Nếu thông tin kiểu hoặc dependency không khớp giữa lúc build và chạy, có thể thiếu điểm can thiệp hoặc xuất hiện lỗi khi nạp lớp. Tên phương thức trùng nhau không đủ; overload và phương thức kế thừa có thể làm tập điểm khớp thay đổi.

### 3.4. Cấu hình phạm vi

Cấu hình có thể gồm:

- Những lớp, package, module hoặc artifact nào được phép xử lý;
- Những lớp bị loại trừ, kể cả mã hạ tầng của chính advice;
- Thứ tự aspect;
- Thời điểm kích hoạt biến đổi;
- Cách tạo và phân phát proxy;
- Chính sách khi pointcut không khớp hoặc công cụ không hỗ trợ join point.

Pointcut quá rộng có thể tạo log trùng, overhead và can thiệp ngoài ý muốn. Pointcut đúng nhưng lớp nằm ngoài phạm vi weaving cũng không tạo advice ở đường mong muốn.

## 4. Đầu ra: chương trình được kết hợp có dạng gì?

Một khái niệm logic là:

```text
wovenProgram = weave(baseProgram, aspects, configuration)
```

Đây là **ký hiệu mô hình**, không phải API Java. Đầu ra vật lý có nhiều hình thức:

| Cơ chế | Đầu ra điển hình | Điều cần kiểm chứng |
| --- | --- | --- |
| Compile-time | Class file/artifact đã tích hợp logic aspect lúc biên dịch | Có dùng đúng output của compiler/weaver khi chạy không? |
| Binary/post-compile | Bytecode/artifact được biến đổi sau biên dịch | Bước đóng gói có dùng bản đã biến đổi không? |
| Load-time | Class definition chứa bytecode đã được biến đổi trước khi JVM định nghĩa lớp | Transformer có xử lý đúng lớp tại đúng lúc không? |
| Runtime proxy/interceptor | Đối tượng đại diện hoặc chuỗi gọi đã được lắp | Người gọi có đi qua đại diện không? |
| Runtime redefinition (nếu hỗ trợ) | Một số định nghĩa lớp đang sống được cập nhật có điều kiện | JVM và công cụ có cho phép thay đổi loại đó không? |

**Đầu ra không nhất thiết là file nguồn đã sửa.** Khi load-time weaving, file JAR trên đĩa có thể giữ nguyên. Với proxy, lớp nghiệp vụ có thể hoàn toàn không bị thay đổi bytecode.

Không đồng nhất bốn con số: **số lớp được xem xét**, **số join point đã weave**, **số proxy được tạo** và **số lần advice thực thi**.

## 5. Compile-time weaving

**Compile-time weaving** kết hợp aspect như một phần bước biên dịch.

```text
Source code + Aspect definition + Config + Dependencies
                         |
                         v
                    Compiler/Weaver
                         |
                         v
                 Woven bytecode
                         |
                         v
                       Runtime
                         |
                  Join point xảy ra
                         |
                    Advice chạy
```

### 5.1. Điểm mạnh

- Đường thực thi đã được chuẩn bị trước khi ứng dụng chạy.
- Một số lỗi kiểu, pointcut hoặc binding có thể được phát hiện ở build.
- Có thể kiểm thử trực tiếp artifact sẽ triển khai.
- Việc tạo proxy cho từng đối tượng không nhất thiết cần thiết nếu bytecode đã được kết hợp.

### 5.2. Điểm cần kiểm tra

- Công cụ build có thật sự chạy bước weaving không?
- Tập lớp nào nằm trong phạm vi được biên dịch và weave?
- Build cache/incremental build có tạo artifact không nhất quán không?
- Artifact deploy có đúng là **bản đã weave** không?
- Mã do công cụ khác sinh hoặc dependency thứ ba có nằm trong phạm vi không?
- Nếu aspect đổi, những phần nào phải được build lại?

**Kết hợp lúc compile** không có nghĩa advice chạy trong compiler. Advice vẫn thường chạy khi join point xảy ra ở chương trình đã triển khai.

## 6. Binary hoặc post-compile weaving

Ở mô hình này, Java compiler hoặc một công cụ khác tạo bytecode gốc **trước**. Bộ weaver nhận bytecode đó cùng aspect, rồi tạo đầu ra mới.

```text
Java source -> javac -> ORIGINAL .class
                              |
                         Aspect/config
                              |
                           Weaver
                              |
                              v
                          WOVEN .class -> Package -> Runtime
```

### 6.1. Vì sao chọn cách này?

Có thể cần tách bước biên dịch chức năng và bước bổ sung chính sách cắt ngang. Nó hữu ích khi phần mã nền đã được biên dịch riêng hoặc build gồm nhiều bước, miễn công cụ được phép xử lý bytecode đó.

### 6.2. Sai lầm quan trọng

Nếu pipeline vô tình đóng gói `ORIGINAL .class` thay vì `WOVEN .class`, chương trình vẫn có thể chạy nhưng **không có advice**. Tương tự, weave hai lần ngoài ý định có thể gây áp trùng nếu công cụ không thiết kế việc lặp đó.

Cần xác nhận bản class cuối, đường đầu vào/đầu ra của weaver, bytecode hợp lệ và tương thích với JVM. Không mặc định công cụ có thể sửa mọi thư viện hoặc lớp nền tảng.

## 7. Load-time weaving

**Load-time weaving** thực hiện biến đổi trước hoặc trong quá trình JVM **định nghĩa lớp**, khi bytecode đi qua class loader/transformer được cấu hình.

```text
Bytecode gốc -> Class loader / Transformer
                             |
                   Aspect + Config
                             |
                             v
                 Transformed class definition
                             |
                             v
                    JVM executes class
                             |
                        Advice chạy
                      ở join point
```

### 7.1. Đầu vào và đầu ra

Đầu vào thường là bytecode lớp, aspect, cấu hình chọn lớp, thông tin môi trường nạp lớp và những dependency cần thiết. Đầu ra là **định nghĩa lớp đã biến đổi trong JVM**; không bắt buộc có file `.class` mới trên đĩa.

### 7.2. Tại sao thời điểm nạp lớp quan trọng?

Nếu một lớp đã được JVM định nghĩa **trước khi** cơ chế LTW được kích hoạt, không thể mặc định nó sẽ tự được weave. Java Instrumentation có các khả năng redefinition/retransformation trong những giới hạn nhất định, nhưng đó không phải tính năng mặc nhiên của mọi hệ LTW.

Các yếu tố cần kiểm tra là class loader, thời điểm định nghĩa lớp, bộ lọc class, giới hạn bytecode/JVM và cấu hình module. Hai lớp cùng tên nhưng do loader khác nhau định nghĩa có thể là hai kiểu runtime khác nhau, với phạm vi can thiệp khác nhau.

### 7.3. Một giới hạn thường bị hiểu nhầm

Cấu hình module của Java có `requires`, `exports`, `opens` với ý nghĩa riêng. Các khai báo ấy **không tự làm cho aspect được weave**. Quyền truy cập, reflection, class loader và khả năng biến đổi bytecode phải được xem theo công cụ và JVM cụ thể.

## 8. Runtime composition bằng proxy/interceptor

Ở một phương án khác, runtime tạo **proxy** hoặc **chuỗi interceptor** để can thiệp khi lời gọi đi qua đường đó.

```text
Target + Interface + Advice/Interceptor
                      |
                      v
                  Create proxy
                      |
                      v
              Caller holds proxy
                      |
                      v
                 Method call
                      |
               Before -> Target
                      |
               After/Throw/Finally
```

Đầu vào là đối tượng đích, hợp đồng giao diện hoặc lớp phù hợp, chính sách chặn và điều kiện chọn lời gọi. Đầu ra là **đại diện**. Lớp đích có thể không bị thay đổi.

### 8.1. Ưu điểm

- Có thể lắp chính sách tại runtime theo đối tượng hoặc cấu hình.
- Đường chặn có thể được biểu diễn rõ bằng chuỗi interceptor.
- Không nhất thiết sửa bytecode của lớp nghiệp vụ.
- Phù hợp để quan sát các lời gọi đi qua API hoặc cổng giao diện, trong phạm vi cơ chế hỗ trợ.

### 8.2. Những giới hạn phải nhớ

Với **JDK dynamic proxy**, handler nhận các lời gọi qua **giao diện được đại diện**. Nó không tự chặn mọi constructor, phương thức private, lời gọi `this.otherMethod()` bên trong đối tượng đích hoặc lệnh truy cập field trực tiếp.

Ngay cả khi proxy đã được dựng, lời gọi vào **đối tượng gốc** có thể bypass advice. Một proxy theo giao diện cũng không tự trở thành một weaver hỗ trợ method-call join point ở mọi vị trí mã nguồn.

## 9. So sánh thời điểm kết hợp và thời điểm advice chạy

| Trường hợp | Khi nào cấu trúc được kết hợp? | Khi nào advice chạy? |
| --- | --- | --- |
| Compile-time | Khi build/biên dịch | Khi join point đã weave được thực thi |
| Binary/post-compile | Khi xử lý bytecode sau compiler | Khi chạy artifact đã biến đổi và chạm join point |
| Load-time | Khi lớp được biến đổi trước lúc JVM định nghĩa | Khi mã của lớp ấy chạm join point |
| Runtime proxy | Khi lắp đại diện/chuỗi interceptor | Khi lời gọi đi qua proxy, khớp điều kiện |
| Runtime retransform (nếu hỗ trợ) | Khi công cụ được phép biến đổi lại lớp | Khi điểm phù hợp của định nghĩa đã biến đổi được thực thi |

Cách nói **“aspect áp dụng ở runtime”** đôi khi mơ hồ: có thể đang nói đến **lắp proxy lúc runtime**, **biến đổi lớp đang chạy** hoặc đơn giản là **advice được kích hoạt trong runtime sau khi đã weave tại build**. Khi trao đổi kỹ thuật nên ghi rõ ý nào.

### 9.1. Điều kiện tĩnh và điều kiện động

Một phần pointcut có thể được đánh giá theo **tên kiểu, package, chữ ký hoặc annotation** ở giai đoạn sớm. Các điều kiện như **giá trị đối số**, đối tượng runtime hoặc trạng thái luồng điều khiển có thể chỉ biết khi lời gọi xảy ra.

Một weaver có thể chèn đoạn kiểm tra điều kiện động trước; về sau điều kiện ấy mới quyết định advice có chạy ở **lần gọi cụ thể** hay không. Vì vậy “join point nằm trong mã đã weave” không bảo đảm advice chạy cho **mọi** lần đi qua vị trí đó.

## 10. Ví dụ Java 17: lắp cấu trúc trước, gọi advice sau

Ví dụ sử dụng **JDK dynamic proxy** để cho thấy đầu vào/đầu ra của runtime composition và thứ tự hai lớp advice. Nó **không** giả vờ là ví dụ compile-time hoặc load-time weaving. Toàn bộ mã chạy độc lập với JDK 17.

Lưu vào tệp `WeavingLifecycleDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class WeavingLifecycleDemo {
    interface WorkPort {
        String execute(String id);
        String lookup(String id);
    }

    static final class WorkService implements WorkPort {
        @Override public String execute(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id blank");
            }
            return "OK:" + id;
        }

        @Override public String lookup(String id) {
            return "FOUND:" + id;
        }
    }

    static Object invokeTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static WorkPort withAdvice(String label, WorkPort target) {
        return (WorkPort) Proxy.newProxyInstance(
                WorkPort.class.getClassLoader(),
                new Class<?>[] { WorkPort.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("execute")) {
                        return invokeTarget(method, target, args);
                    }

                    String id = (String) args[0];
                    String value = id == null ? "<null>"
                            : id.isBlank() ? "<blank>" : id;

                    System.out.println("[" + label + "] before " + value);
                    try {
                        Object result = invokeTarget(method, target, args);
                        System.out.println("[" + label + "] after " + result);
                        return result;
                    } catch (Throwable error) {
                        System.out.println("[" + label + "] throw "
                                + error.getClass().getSimpleName());
                        throw error;
                    } finally {
                        System.out.println("[" + label + "] finally");
                    }
                });
    }

    public static void main(String[] args) {
        WorkPort original = new WorkService();
        WorkPort inner = withAdvice("INNER", original);
        WorkPort outer = withAdvice("OUTER", inner);

        System.out.println("ASSEMBLED");
        System.out.println("CALLER " + outer.execute("R-1"));

        try {
            outer.execute(" ");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER_ERROR " + error.getMessage());
        }

        System.out.println("LOOKUP " + outer.lookup("R-2"));
        System.out.println("DIRECT " + original.execute("R-3"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 WeavingLifecycleDemo.java
java WeavingLifecycleDemo
```

Kết quả:

```text
ASSEMBLED
[OUTER] before R-1
[INNER] before R-1
[INNER] after OK:R-1
[INNER] finally
[OUTER] after OK:R-1
[OUTER] finally
CALLER OK:R-1
[OUTER] before <blank>
[INNER] before <blank>
[INNER] throw IllegalArgumentException
[INNER] finally
[OUTER] throw IllegalArgumentException
[OUTER] finally
CALLER_ERROR id blank
LOOKUP FOUND:R-2
DIRECT OK:R-3
```

### 10.1. Đâu là đầu vào, đâu là đầu ra?

`withAdvice(label, target)` nhận tên lớp quan sát, một đối tượng triển khai `WorkPort`, thông tin giao diện và logic can thiệp. Nó trả về một **proxy `WorkPort`**. `inner` bao đối tượng gốc; `outer` bao `inner`.

Sau hai lời gọi `withAdvice`, chương trình in `ASSEMBLED`: cấu trúc đã được lắp. Chưa có `execute` nào được gọi, nên **chưa có dòng advice**.

### 10.2. Khi nào advice thực thi?

`outer.execute("R-1")` đi qua proxy ngoài rồi proxy trong. OUTER ghi `before`, INNER ghi `before`, đích chạy, sau đó INNER hoàn tất và OUTER hoàn tất. Thứ tự là kết quả cấu trúc lồng `OUTER(INNER(Target))`.

Lần gọi với `" "` ném `IllegalArgumentException`. Cả hai lớp proxy ghi nhánh lỗi và `finally`; hàm `invokeTarget` bóc `InvocationTargetException` để giữ lỗi đích ban đầu.

### 10.3. Lời gọi nào không được quan sát?

`outer.lookup("R-2")` đi qua proxy nhưng không khớp điều kiện `execute`. `original.execute("R-3")` **khớp về tên** nhưng đi thẳng vào đối tượng gốc, không qua proxy. Cả hai không in dòng advice.

Đây là bằng chứng: **đã lắp cơ chế** khác với **đã thực thi advice trên mọi đường gọi**.

### 10.4. Phạm vi minh họa

Mã dùng proxy JDK chứ không biến đổi bytecode. Điều kiện `method.getName().equals("execute")` là bộ lọc Java đơn giản; khi có overload thực tế phải xác định thêm chữ ký. Nó cũng không mô phỏng field get/set, constructor hay mọi loại join point khác. Dữ liệu ghi log chỉ nhằm học thuật; phải tránh ghi thông tin nhạy cảm trong hệ thống thật.

## 11. Hai đầu vào tưởng giống nhau nhưng tạo phạm vi khác nhau

Giả sử `OrderService` gọi `PaymentGateway.charge()`:

- **Method call join point** có thể gắn với **vị trí viết lời gọi** trong mã `OrderService`.
- **Method execution join point** có thể gắn với **phương thức thực thi** của `PaymentGateway`.
- Một proxy quanh `PaymentGateway` chỉ có thể quan sát đường gọi đi qua đại diện được cấp.

Nếu yêu cầu chọn “mọi lời gọi phát ra từ package order”, đó có thể là tập điểm khác với “mọi phương thức khai báo trong package order”. Một công cụ chỉ hỗ trợ method interception không tự cho phép quan sát mọi method-call join point trong bytecode.

Tương tự, muốn chọn **đọc/ghi field trực tiếp**, một proxy chỉ chặn getter/setter **không đủ**. Cần công nghệ có join point field access và phạm vi weaving phù hợp, hoặc tái thiết kế ranh giới truy cập tường minh.

## 12. Thứ tự aspect là một phần của đầu vào

Hai aspect có thể cùng khớp một join point. Kết quả lắp theo:

```text
Timer(Retry(Target))
```

có thể đo toàn bộ chuỗi thử lại, còn:

```text
Retry(Timer(Target))
```

có thể khiến bộ đo chạy từng attempt. Tương tự, thứ tự giữa kiểm tra quyền, cache, transaction và tracing có thể thay đổi ý nghĩa chính sách.

Không suy ra thứ tự từ tên file hoặc thứ tự khai báo khi công nghệ không cam kết điều đó. Cần có quy tắc precedence hoặc cấu hình chuỗi chặn được kiểm chứng.

## 13. Vấn đề nâng cao

### 13.1. Lớp đã nạp rồi

Thêm một transformer vào JVM **sau khi** lớp đã được định nghĩa không mặc định làm thay đổi lớp ấy. Nếu công nghệ cho phép retransform/redefine thì phải tuân theo điều kiện và giới hạn; không coi nó là đặc tính sẵn có của mọi cơ chế weaving.

### 13.2. Đối tượng đã được cấp ra ngoài

Tạo proxy mới không thay mọi tham chiếu trước đó tới đối tượng gốc. Nếu người gọi còn giữ bản gốc, đường bypass vẫn tồn tại. Phải lựa chọn rõ lúc tạo proxy và nơi quản lý/cấp đối tượng cho chương trình.

### 13.3. Nhiều class loader và module

Hai lớp cùng tên được nạp bởi loader khác nhau có thể là hai kiểu runtime khác nhau. Một transformer hoặc phạm vi weaving của một loader không nhất thiết bao phủ loader kia. JPMS và những quy tắc truy cập của Java cũng cần được xét theo JVM/công nghệ, không suy luận rằng tên package đã đủ.

### 13.4. Asynchronous completion

Phương thức có thể trả về `CompletableFuture` ngay nhưng tác vụ bên trong còn chạy. Advice after-returning quanh lời gọi tạo future quan sát **việc phương thức trả future**, không nhất thiết là **tác vụ hoàn tất thành công**. Cần cơ chế theo dõi completion phù hợp nếu mục tiêu là kết quả bất đồng bộ.

### 13.5. Advice tự gây lỗi

Một advice logging ném lỗi có thể biến lời gọi vốn thành công thành lỗi. Một around advice gọi tiếp hai lần có thể lặp tác dụng phụ. Một cleanup trong `finally` có thể che ngoại lệ gốc nếu ném lỗi mới. Kiến trúc weaving cần kiểm soát cả **thứ tự** và **hợp đồng bảo toàn hành vi**, không chỉ vị trí áp dụng.

## 14. Kiểm tra thành công theo bốn mức

| Mức | Câu hỏi | Bằng chứng |
| --- | --- | --- |
| **Defined** | Aspect/pointcut đã tồn tại? | Build hoặc cấu hình nhận diện được |
| **Selected** | Pointcut chọn đúng những điểm cần thiết? | Test chữ ký, loại điểm, phạm vi, điều kiện |
| **Woven/Composed** | Đầu ra đã được tạo và sử dụng? | Class đã biến đổi hoặc proxy được cấp đúng |
| **Executed** | Advice thật sự chạy ở đường hợp lệ? | Test tích hợp trên lời gọi và kết quả thực |

Một hệ thống có thể PASS mức 1–3 nhưng FAIL mức 4 nếu không ai gọi qua proxy, điều kiện động sai hoặc chạy nhầm artifact. Ngược lại, thấy một dòng log chạy không chứng minh **mọi** đường nghiệp vụ quan trọng được áp chính sách.

### 14.1. Ma trận kiểm thử gợi ý

| Ca kiểm thử | Điều cần chứng minh |
| --- | --- |
| Join point đúng tên/chữ ký | Advice chạy đúng số lần |
| Overload khác | Không khớp ngoài ý muốn |
| Đường trả về bình thường | Kết quả giữ nguyên nếu chỉ theo dõi |
| Đường ném ngoại lệ | Giữ ngoại lệ gốc và dọn bối cảnh phù hợp |
| Đường gọi trực tiếp vs proxy | Hiểu rõ giới hạn bypass |
| Self-invocation | Biết cơ chế có đi qua advice không |
| Lớp chưa weave / loader khác | Phát hiện điểm bỏ sót |
| Compile-time output | Artifact triển khai là bản đã kết hợp |
| Binary output | Không đóng gói nhầm class gốc |
| Load-time | Transformer hoạt động trước lúc lớp được định nghĩa |
| Hai aspect cùng khớp | Thứ tự và hiệu ứng đúng |
| Tác vụ bất đồng bộ | Phân biệt return với completion |
| Nhiều lần retry | Không gây tác dụng phụ lặp ngoài hợp đồng |

## 15. Những lỗi thường gặp

| Sai lầm | Hậu quả | Cách tránh |
| --- | --- | --- |
| Nghĩ “có file aspect là tự chạy” | Không có advice | Xác nhận cơ chế weaving/composition |
| Nghĩ weave lúc build thì advice cũng chạy lúc build | Hiểu sai vòng đời | Tách thời điểm kết hợp và kích hoạt |
| Quên dùng artifact sau binary weave | Thiếu advice dù build báo thành công | Kiểm tra output cuối cùng |
| Bật load-time transformer quá muộn | Lớp mong đợi đã nạp | Kiểm tra timeline class loading |
| Tạo proxy nhưng vẫn cấp đối tượng gốc | Đường gọi bỏ qua advice | Kiểm soát nơi cấp tham chiếu |
| Chọn pointcut quá rộng | Log lặp, ảnh hưởng hiệu năng | Giới hạn chữ ký, loại điểm và phạm vi |
| Cho rằng proxy chặn field access | Bỏ sót truy cập trực tiếp | Dùng cơ chế hỗ trợ loại điểm phù hợp |
| Không kiểm tra thứ tự nhiều aspect | Sai phạm vi retry, logging, transaction | Xác minh precedence |
| Bỏ qua kết quả/lỗi | Advice có thể đổi hành vi nghiệp vụ | Kiểm tra cả return và throw |
| Không theo dõi class loader | Một số lớp không được xử lý | Kiểm tra nguồn lớp và loader |
| Tin số proxy tạo là số advice đã chạy | Số liệu sai | Đếm invocation riêng |
| Không kiểm thử đường bypass | Chính sách quan trọng bị bỏ sót | Test cả đường không có advice |

## 16. Bài tập vận dụng có đáp án

### Bài 1: Thời điểm và đầu ra

Một bộ compile-time weaver tạo class đã kết hợp aspect lúc 10:00. Lần đầu chương trình gọi phương thức khớp lúc 10:30. Weaving và advice diễn ra lúc nào?

**Đáp án:** Weaving diễn ra trong build 10:00. Advice chạy tại join point 10:30, khi chương trình thực thi và điều kiện áp dụng đúng. Hai mốc khác nhau.

### Bài 2: Artifact nhầm

Sau khi chạy binary weaver, pipeline triển khai lại lấy bytecode trước biến đổi. Điều gì có thể xảy ra?

**Đáp án:** Artifact deploy thiếu logic advice mặc dù từng bước build/weaving có thể đã báo thành công. Phải đóng gói và kiểm thử **đúng đầu ra đã weave**.

### Bài 3: Transformer đăng ký muộn

Một lớp `Service` đã được định nghĩa trước khi load-time weaver hoạt động. Có thể mặc định lớp đang chạy được thay đổi ngay không?

**Đáp án:** Không. LTW thường xử lý trong giai đoạn nạp/định nghĩa lớp. Thay lớp đã nạp cần cơ chế riêng, điều kiện và giới hạn tương ứng, không phải mặc nhiên.

### Bài 4: Proxy

Proxy cho `OrderPort` đã tạo, nhưng thành phần A gọi `original.execute()` còn thành phần B gọi `proxy.execute()`. Vì sao A không có advice?

**Đáp án:** A dùng đối tượng gốc và bypass proxy. Tạo đại diện không tự thay tất cả tham chiếu. Đây là vấn đề **đường gọi được kết hợp**, không phải nhất thiết do pointcut sai.

### Bài 5: Một lớp, nhiều lần gọi

Một class được weave một lần ở build và phương thức khớp được gọi 100 lần. Có bắt buộc số lần advice bằng một không?

**Đáp án:** Không. Advice có thể chạy ở từng lần join point xảy ra, tùy điều kiện động và cấu trúc chuỗi aspect. Số lần weave và số invocation khác nhau.

### Bài 6: Loại join point

Yêu cầu can thiệp tại các lần đọc field trực tiếp. JDK dynamic proxy bọc getter có bảo đảm làm được không?

**Đáp án:** Không. Truy cập field trực tiếp không nhất thiết gọi getter qua proxy. Cần mô hình AOP hỗ trợ field-read join point và cơ chế kết hợp phù hợp, hoặc ranh giới API khác.

### Bài 7: Runtime composition

Trong ví dụ Java 17, vì sao `ASSEMBLED` in trước các dòng `[OUTER]` và `[INNER]`?

**Đáp án:** Proxy đã được **tạo và lắp** trước khi `execute` được gọi. Handler/advice chỉ chạy khi có cuộc gọi đi qua proxy và khớp điều kiện.

### Bài 8: Hai aspect

Nếu thứ tự là `Timer(Retry(Target))` thì Timer thường đo gì? Nếu là `Retry(Timer(Target))` thì sao?

**Đáp án:** Timer bên ngoài bao toàn bộ chuỗi retry; Timer bên trong có thể đo từng attempt. Cần xác minh thứ tự thực tế được công nghệ thiết lập.

### Bài 9: Completion bất đồng bộ

Một phương thức trả ngay một future chưa hoàn tất. Advice sau return ghi `SUCCESS`. Có chắc tác vụ async đã thành công không?

**Đáp án:** Không. Return của phương thức tạo future khác với completion của future. Cần theo dõi completion thực và hợp đồng kết quả.

## 17. Ghi nhớ

- **Đầu vào:** base code/bytecode hoặc đối tượng, định nghĩa aspect/pointcut/advice, thông tin kiểu, cấu hình và cơ chế kết hợp.
- **Đầu ra:** artifact/class đã biến đổi, định nghĩa lớp khi nạp hoặc proxy/interceptor, tùy kiến trúc; không phải lúc nào cũng sửa mã nguồn.
- **Các thời điểm áp dụng:** biên dịch, sau biên dịch, nạp lớp hoặc lúc dựng chuỗi runtime; mỗi cách có phạm vi và giới hạn riêng.
- **Weaving xảy ra trước về mặt cấu trúc; advice được thực thi khi join point phù hợp xuất hiện.** Cấu hình một aspect không chứng minh nó đã chạy.
- **Chọn điểm** (pointcut), **kết hợp** (weaving) và **thực thi** (advice invocation) cần được kiểm tra độc lập.
- Với nhiều aspect, **thứ tự lồng nhau** ảnh hưởng kết quả, lỗi, đo thời gian và tác dụng phụ.
- Khi sử dụng AOP cho chính sách quan trọng, hãy xác minh **artifact, class loading, proxy references, đường bypass và tính bảo toàn hợp đồng**, thay vì chỉ nhìn source aspect.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-005">Phạm vi dệt trong một mô-đun và giữa các mô-đun</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi dệt trong một mô-đun và giữa các mô-đun trong AOP

**Weaving (dệt aspect)** là quá trình kết hợp mã của các **aspect/advice** vào những **join point** được **pointcut** chọn. Trong ứng dụng nhỏ, ta dễ nghĩ “khai báo aspect là nó áp dụng cho mọi nơi”. Nhưng khi ứng dụng được chia thành nhiều **mô-đun (module)**, câu hỏi quan trọng là:

**Mã nào được đưa vào phạm vi dệt, ở phía gọi hay phía thực thi, và những ranh giới mô-đun nào có thể hoặc không thể được cơ chế AOP vượt qua?**

Một aspect định nghĩa trong mô-đun A không tự động can thiệp mọi lời gọi ở mô-đun B. Ngay cả khi A gọi được API của B, cơ chế weaving vẫn phải **nhìn thấy đúng join point**, được phép instrument phần mã cần thiết, kết hợp advice vào artifact/bytecode hoặc đi qua interceptor thích hợp, rồi đưa kết quả đã dệt vào runtime thật.

Có hai phạm vi cần phân biệt:

- **Dệt trong một mô-đun (intra-module weaving):** điểm can thiệp và mã thực thi liên quan nằm trong phạm vi cùng một đơn vị mã/build/deployment do ta kiểm soát. Có thể bao gồm cả các lời gọi nội bộ nếu công cụ hỗ trợ kiểu join point và phương thức dệt đó.
- **Dệt giữa các mô-đun (inter-module weaving):** pointcut/advice cần áp dụng tại ranh giới gọi từ module này tới module khác, hoặc tại execution của target ở module phụ thuộc. Thành công phụ thuộc cấu trúc module, cách phân phối artifact, visibility, class loading và nơi weaving thực sự diễn ra.

> **Phạm vi kiến thức:** Bài này nói về AOP nói chung, từ nền tảng tới tổ chức weaving nâng cao. Java 17+ được dùng để minh họa module graph và giới hạn interceptor. Không có một mô hình module hoặc một engine AOP chung bắt buộc; các biểu thức chọn điểm dạng `module=A` hay `execution(B.Service.method)` dưới đây là **ký pháp mô tả ý tưởng**, không phải cú pháp pointcut có thể dùng nguyên xi trong mọi công cụ.

## 1. Ôn lại: aspect, pointcut, join point và weaving

Trong mô hình AOP:

1. **Aspect** đóng gói một mối quan tâm cắt ngang, ví dụ audit, tracing hoặc đo thời gian.
2. **Pointcut** là tiêu chí chọn join point.
3. **Join point** là vị trí/sự kiện có thể can thiệp, ví dụ một lời gọi hoặc một lần thực thi phương thức, tùy engine.
4. **Advice** là hành vi được áp dụng tại điểm khớp.
5. **Weaving** là hoạt động kết hợp advice với mã/chương trình để hành vi đó thực sự xuất hiện khi chạy.

```text
Pointcut + Advice + Chương trình cần can thiệp
                    |
               Weaving process
                    |
                    v
           Chương trình sau weaving
                    |
                    v
        Join point khớp -> advice thực thi
```

**Pointcut được viết đúng không có nghĩa advice đã được weave.** Một class có thể tồn tại, annotation có thể hiện diện, hoặc interceptor có thể được tạo, nhưng nếu đường thực thi không đi qua nó thì hành vi cắt ngang không xảy ra.

Weaving có thể diễn ra khi biên dịch, sau biên dịch, khi class được nạp, hoặc được mô phỏng ở ranh giới runtime bằng proxy/interceptor. Các cơ chế này có **phạm vi mã và join point khác nhau**; chúng không phải những tên gọi tương đương của cùng một thao tác.

## 2. “Mô-đun” có thể chỉ những ranh giới khác nhau

Từ *module* không có một nghĩa duy nhất. Trước khi phân tích intra-/inter-module weaving, cần xác định đang nói về **đơn vị nào**.

| Loại ranh giới | Ví dụ | Hệ quả đối với AOP |
| --- | --- | --- |
| **Package** | `com.example.billing` | Là nhóm tên và ranh giới truy cập Java, không nhất thiết là đơn vị build |
| **Module build** | Một project con sinh ra JAR | Có artifact và quy trình compile riêng; thường là đơn vị được đưa vào hoặc bỏ ra khỏi weaving |
| **JAR/library** | `billing-api.jar`, `billing-impl.jar` | Mã có thể đóng gói tách biệt; weaving cần xử lý JAR chứa join point tương ứng |
| **Java Platform Module System (JPMS)** | `module com.example.billing { ... }` | Có module descriptor, readability, exports, opens, module path |
| **Class loader domain** | Module/plugin được nạp bởi class loader riêng | Quyền truy cập và class identity có thể ảnh hưởng khả năng dệt |
| **Runtime process/service** | Hai dịch vụ chạy trên JVM khác nhau | Không có call stack và object graph chung; AOP local không tự dệt xuyên tiến trình |

**Một package không tự là một module JPMS.** Một JAR cũng không nhất thiết là **named JPMS module**; nó có thể chạy trên classpath (unnamed module), là automatic module, hoặc chứa `module-info.class`. Một module build có thể chứa nhiều package và có thể tạo một hoặc nhiều artifact.

Vì thế, câu “weave trong module Billing” cần giải thích thêm: **weave class nào, artifact nào, ở build step nào, class loader nào, và loại join point nào**?

### Ranh giới tiến trình không phải ranh giới weaving bytecode thông thường

Nếu module A gọi module B **trong cùng JVM**, một cơ chế instrument bytecode có thể có khả năng can thiệp ở các phía phù hợp. Nhưng nếu “B” là một dịch vụ ở **process khác**, trong JVM A chỉ có lời gọi client/protocol. Dệt `B.Service.process()` đòi can thiệp vào artifact/runtime của B, chứ không tự xuất hiện chỉ vì A đã được weave.

Có thể dùng tracing trên hai dịch vụ rồi nối bằng trace context, nhưng **đó là correlation qua giao thức**, không phải một method-call join point Java kéo dài xuyên mạng.

## 3. Phạm vi dệt là một tập mã cụ thể, không phải khẩu hiệu toàn cục

Hãy ký hiệu:

- `M`: tập module của ứng dụng.
- `C(m)`: các class/bytecode thuộc module `m`.
- `W`: tập class hoặc call site thực sự được công cụ đưa vào phạm vi weaving.
- `J(W)`: các join point mà công cụ có thể nhận diện trong tập đó.
- `P`: điều kiện pointcut.

Những điểm được áp dụng advice về nguyên tắc nằm trong **giao**:

```text
EligibleJoinPoints = J(W) ∩ Matches(P) ∩ SupportedByEngine
```

Đây là **mô hình tập hợp khái niệm**, không phải công thức runtime bắt buộc. Ngoài ba điều kiện này, còn có thể có ràng buộc thời điểm load class, visibility, dynamic dispatch, deployment và cơ chế interception.

### Hai loại “phạm vi” hay bị nhập nhằng

**Phạm vi khớp (matching scope):** pointcut mô tả bao nhiêu join point. Ví dụ chọn mọi execution `save(String)` trong nhóm type nhất định.

**Phạm vi dệt/bao phủ (weaving coverage):** những class, call site và đường thực thi mà engine thực sự có thể áp dụng advice.

Một pointcut rất rộng vẫn có thể cho coverage thấp nếu chỉ một JAR được weave. Một công cụ có thể instrument cả ứng dụng nhưng pointcut rất hẹp nên chỉ chọn một method. **Không thể suy luận coverage chỉ từ độ rộng của biểu thức pointcut.**

## 4. Dệt trong cùng mô-đun: các trường hợp cơ bản

Giả sử module `catalog` chứa:

```java
// Mã minh họa, không phải tệp Java độc lập.
class CatalogService {
    void publish(String id) {
        validate(id);
        save(id);
    }

    private void validate(String id) { }

    void save(String id) { }
}
```

Trong cùng một module, có ít nhất ba sự kiện cần phân biệt:

- **Execution** của `publish`.
- **Call site** bên trong `publish` phát lời gọi `validate`, `save`.
- **Execution** của `validate` và `save`.

Nếu một engine hỗ trợ instrument **bytecode của class này** và hỗ trợ đúng loại join point, nó có thể nhận diện nhiều sự kiện kể trên. Nhưng nếu cơ chế chỉ là **proxy đứng ngoài service**, một lời gọi `serviceProxy.publish(...)` có thể được intercept, còn `this.validate(...)` và `this.save(...)` bên trong target có thể đi thẳng tới thân method mà không quay ra proxy.

**Cùng module không bảo đảm self-invocation được advice.** Đây là khác biệt giữa **vị trí mã** và **đường interception**.

### Intra-module: thuận lợi, nhưng không tự động toàn diện

- Có thể kiểm soát cùng build pipeline và xử lý nhiều class trong một lần weave.
- Có thể truy cập metadata, bytecode hoặc type hierarchy dễ hơn khi chúng cùng nằm trong đầu vào build.
- Có thể quản lý cấu hình pointcut chung trong phạm vi module.
- Nhưng vẫn có giới hạn ở private/static/final, method synthetic, reflection, native method và các join point mà engine không hỗ trợ.
- Những class **không được đưa vào `W`** vẫn không được weave dù chúng thuộc cùng project build.

## 5. Dệt giữa các mô-đun: caller và callee ở hai phía

Giả sử module `checkout` gọi module `billing`:

```text
+----------------------------+       +----------------------------+
| Module checkout            |       | Module billing             |
| CheckoutFlow.submit()      |       | InvoiceService.create()    |
|       |                    |       |      ^                     |
|       +--- CALL ---------- +------>+------ EXECUTION ----------|
|                            |       |                            |
+----------------------------+       +----------------------------+
```

Mối quan tâm cắt ngang có thể đặt ở:

1. **Phía caller:** “Mọi lời gọi phát từ `checkout` sang API billing phải được thống kê.” Nếu chọn **method call join point**, vị trí cần instrument có thể nằm **trong bytecode của checkout**.
2. **Phía callee:** “Mọi lần `InvoiceService.create` thực thi đều phải được audit, bất kể caller nào.” Nếu chọn **method execution join point**, vị trí cần instrument có thể nằm **trong bytecode của billing**.
3. **Một lớp trung gian:** một proxy/API gateway nội bộ trong JVM nhận lời gọi trước khi tới target. Coverage phụ thuộc **mọi lời gọi cần bảo vệ có thực sự đi qua lớp này không**.

Đây là ba chiến lược khác nhau. Việc khai báo aspect trong module `observability` không tự khiến cả caller và callee được instrument.

### Bảng nơi cần dệt theo mục tiêu

| Yêu cầu | Vị trí quan sát tự nhiên | Mã thường cần được weave/intercept |
| --- | --- | --- |
| Ghi tất cả lời gọi **từ Checkout** tới Billing | Method call ở checkout | Caller `checkout` |
| Audit mọi lần `Billing.create` thật sự chạy | Method execution ở billing | Callee `billing` |
| Chỉ theo dõi API đi qua một cổng chung | Interceptor tại cổng | Đường gọi qua cổng |
| Theo dõi cả direct call và self-invocation | Execution instrumentation phù hợp | Các method target được instrument |
| Đo tổng thời gian end-to-end qua mạng | Client/server instrumentation + context | Hai phía riêng, nối bằng metadata giao thức |

Bảng là định hướng kiến trúc, **không khẳng định engine nào cũng hỗ trợ call/execution và mọi kiểu method**.

## 6. Một module định nghĩa aspect, module khác chứa target

Giả sử:

```text
observability-module
    └── TraceAspect

business-module
    ├── OrderService
    └── InvoiceService
```

Có ít nhất ba vấn đề độc lập:

**(1) Advice có sẵn ở thời điểm weaving không?** Engine cần tìm được định nghĩa aspect/advice và type cần thiết. Nếu aspect nằm trong một artifact không được đưa vào build/load path thích hợp, việc dệt có thể không thực hiện được hoặc artifact được tạo không chạy được.

**(2) Business bytecode có được weave không?** Nếu engine chỉ xử lý `observability-module` mà không xử lý `business-module`, có thể **không có execution join point nào của business được biến đổi**. Aspect ở module riêng không tự biến đổi class ở module còn lại.

**(3) Runtime có nạp đúng kết quả đã weave không?** Một build có thể tạo artifact woven, nhưng runtime lại load JAR gốc, cache cũ hoặc class từ đường khác. Điều này tạo lỗi “build báo weave thành công nhưng advice không bao giờ chạy”.

Tách module chứa aspect là lựa chọn tổ chức mã tốt, nhưng **weaving boundary** phải được cấu hình cho các module có join point cần bao phủ.

### Module chứa interface khác module chứa implementation

Một API có thể nằm trong `billing-api` còn implementation trong `billing-impl`. Nếu pointcut chọn **call tới interface**, mã phát lời gọi có thể nằm ở module client. Nếu chọn **execution của implementation**, class được instrument nằm ở module implementation.

Dệt riêng `billing-api` thường **không đủ** để dệt thân phương thức implementation nằm trong artifact khác. Interface có thể chỉ định hợp đồng, còn thân logic nằm nơi khác. Đây là vấn đề đặc biệt quan trọng khi dùng layered architecture.

## 7. Những cơ chế weaving và phạm vi module tương ứng

| Cơ chế | Thời điểm | Phạm vi cần kiểm soát | Giới hạn cần xác minh |
| --- | --- | --- | --- |
| **Compile-time weaving** | Khi biên dịch mã | Source/module được đưa vào compiler/weaver | Source/binary của dependency có thể không nằm trong đầu vào |
| **Post-compile weaving** | Sau biên dịch, trước chạy | Class/JAR cụ thể bị biến đổi | Phải deploy **artifact đã dệt**, không phải bản gốc |
| **Load-time weaving** | Khi JVM nạp class | Class loader, agent, cấu hình chọn class | Class đã nạp trước khi agent áp dụng có thể cần cơ chế khác; ranh giới loader quan trọng |
| **Runtime proxy/interceptor** | Khi caller đi qua đối tượng trung gian/hook | Lời gọi thật đi qua interceptor | Direct call/self-invocation dễ bỏ qua |
| **Build-time generated wrapper** | Khi build hoặc generate source | API và call site sử dụng wrapper | Wrapper không tự thay những lời gọi không sử dụng nó |

**Proxy không nhất thiết là bytecode weaving.** Proxy giúp minh họa interception và phạm vi đường gọi, nhưng không được dùng làm bằng chứng rằng method body của target đã được biến đổi.

### Compile-time: cần đưa đúng module vào pipeline

Nếu có modules `A` và `B`, và pointcut chọn call site trong A, compiler/weaver cần xử lý mã call site của A. Chỉ compile B với aspect mà không instrument caller không bảo đảm call advice trong A sẽ tồn tại.

Nếu pointcut chọn execution ở B, cần instrument method execution trong B. Một số toolchain có thể đọc aspect từ artifact khác, một số có thêm điều kiện về compiler, plugin hoặc classpath/module path. Nguyên lý ổn định là: **đưa đúng mã cần thay đổi vào quá trình weaving**.

### Post-compile: kiểm soát artifact đầu ra

Pipeline thường có dạng:

```text
Compile module A -> A.jar
Compile module B -> B.jar
                 |
          Post-compile weave
                 |
          A-woven.jar / B-woven.jar
                 |
          Runtime loads chosen artifacts
```

Không nhất thiết cần dệt cả A và B. Phải chọn theo **loại join point cần có**. Việc dệt nhầm artifact hoặc đưa bản gốc vào runtime có thể khiến test nhận kết quả bất ngờ. Các bước đóng gói, shading, ký JAR hoặc biến đổi bytecode khác phải được sắp xếp nhất quán trong pipeline.

### Load-time: phạm vi phụ thuộc class loading

Load-time weaving có thể can thiệp khi class được JVM định nghĩa, nếu agent/transformer được cài đặt đúng thời điểm và class nằm trong phạm vi xử lý. Nhưng các điểm cần kiểm chứng gồm:

- Transformer có được gọi cho class ở module mục tiêu không?
- Class đã được định nghĩa trước khi transformer sẵn sàng chưa?
- Có class loader khác/child loader không?
- Bytecode có bị một transformer khác biến đổi hay không?
- Có cần quyền truy cập module hoặc cấu hình mở package phù hợp?
- Một số loại class/JDK internals có nằm ngoài phạm vi được phép không?

Không hứa rằng một “công tắc LTW” sẽ dệt mọi class trong cả JVM.

## 8. Java JPMS không đồng nghĩa với cơ chế weaving

**Java Platform Module System (JPMS)**, có từ Java 9 và phù hợp với Java 17+, cho phép tổ chức **named modules** bằng `module-info.java`. JPMS quản lý **module readability** và **khả năng truy cập package**; nó **không tự triển khai pointcut hoặc advice**.

Ví dụ:

```java
module com.example.core {
    exports com.example.core.api;
}
```

Một module khác:

```java
module com.example.app {
    requires com.example.core;
}
```

Từ góc nhìn JPMS:

- `requires com.example.core` tạo sự phụ thuộc/khả năng đọc module khi điều kiện resolution phù hợp.
- `exports com.example.core.api` cho phép truy cập các public type/member phù hợp từ module đọc được.
- Package implementation **không export** vẫn có thể dùng **nội bộ** trong core; app không tự import các public class trong package không export đó như API public.
- `opens` dùng cho trường hợp reflective access sâu theo quy tắc module. Nó **khác** với `exports`.
- `open module` hoặc `opens package` không tự động có nghĩa một engine AOP sẽ nhận diện và weave tất cả class trong package.
- Việc điều chỉnh module graph bằng runtime flags có thể thay khả năng truy cập, nhưng không thay thế cấu hình điểm weave và chính sách bảo mật.

**JPMS là ranh giới của ngôn ngữ/runtime; weaving là cơ chế kết hợp advice.** Hai vấn đề tương tác, nhưng không nên đánh đồng.

### `exports` và `opens` khác nhau thế nào?

| Khai báo | Ý nghĩa chính | Không có nghĩa |
| --- | --- | --- |
| `exports p;` | Công bố public API của package `p` cho module đọc được | Cho phép Reflection truy cập private members tùy ý |
| `exports p to q;` | Công bố public API có điều kiện cho module `q` | Cho mọi module khác truy cập public API |
| `opens p;` | Cho phép deep reflection trên package `p` ở runtime theo quy tắc JPMS | Biến package thành API compile-time được export |
| `opens p to q;` | Deep reflection giới hạn tới module `q` | Tự động instrument code trong `p` |
| `requires q;` | Module cần đọc module `q` | Mọi package của `q` đều được export |

Một engine thao tác bytecode có thể cần quyền riêng khác so với một proxy chỉ gọi public interface method. **Không nên tự thêm `opens` cho toàn bộ module** chỉ để “cho AOP chạy” nếu chưa có lỗi quyền truy cập cụ thể và phương án đúng phạm vi.

## 9. Ví dụ Java 17+: hai module thật, proxy cho thấy ranh giới interception

Ví dụ sau tạo **hai JPMS named modules**:

- `com.example.core`: có API và implementation. Package `com.example.core.api` được export; package `com.example.core.internal` không được export.
- `com.example.app`: phụ thuộc module core, tạo một proxy trước `PriceService` và quan sát lời gọi đi qua proxy.

Ta kiểm chứng ba đường:

1. `app → proxy → core.quote`: advice được quan sát ở ranh giới module.
2. `core.quote → core.basePrice`: lời gọi nội bộ không đi qua proxy app.
3. `app → raw core.quote`: caller gọi target trực tiếp nên không đi qua proxy.

**Phân biệt rõ:** Đây là **minh họa runtime interception**, không phải ví dụ đã thực hiện compile-time weaving vào bytecode của core. Nó chỉ ra vì sao một ranh giới module/proxy **không bao phủ tự động mọi method execution nội bộ**. Một engine bytecode weaving có thể hỗ trợ phạm vi khác.

### 9.1. Cấu trúc thư mục

```text
project/
  src/
    com.example.core/
      module-info.java
      com/example/core/api/PriceService.java
      com/example/core/api/Services.java
      com/example/core/internal/DefaultPriceService.java
    com.example.app/
      module-info.java
      com/example/app/Tracing.java
      com/example/app/Main.java
```

Tạo đúng các file bên dưới. Những tên file chứa `module-info.java` là **module descriptors**, không phải class thông thường.

### 9.2. `src/com.example.core/module-info.java`

```java
module com.example.core {
    exports com.example.core.api;
}
```

Package API được export. Package implementation `com.example.core.internal` không export, nhưng code trong core vẫn có quyền sử dụng các class nội bộ của chính module.

### 9.3. `src/com.example.core/com/example/core/api/PriceService.java`

```java
package com.example.core.api;

public interface PriceService {
    int quote(String sku);
    int basePrice(String sku);
}
```

Đây là contract public. Việc cả `quote` và `basePrice` đều nằm trên interface không có nghĩa một lời gọi từ bên trong implementation bắt buộc đi qua proxy; **proxy chỉ được dùng nếu receiver thật là proxy**.

### 9.4. `src/com.example.core/com/example/core/api/Services.java`

```java
package com.example.core.api;

import com.example.core.internal.DefaultPriceService;

public final class Services {
    private Services() { }

    public static PriceService create() {
        return new DefaultPriceService();
    }
}
```

Factory tạo implementation ở package nội bộ và trả nó qua interface public, cho phép app sử dụng API mà không import class implementation không export.

### 9.5. `src/com.example.core/com/example/core/internal/DefaultPriceService.java`

```java
package com.example.core.internal;

import com.example.core.api.PriceService;

public final class DefaultPriceService implements PriceService {
    @Override
    public int quote(String sku) {
        System.out.println("core.quote " + sku);
        return basePrice(sku) + 5;
    }

    @Override
    public int basePrice(String sku) {
        System.out.println("core.basePrice " + sku);
        return 10;
    }
}
```

`quote` **tự gọi `basePrice`** trong cùng object. Ở ví dụ này, việc gọi không đi vòng qua proxy app.

### 9.6. `src/com.example.app/module-info.java`

```java
module com.example.app {
    requires com.example.core;
}
```

App đọc module core và truy cập API được export. Không cần mở package nội bộ chỉ để tạo proxy trên interface public.

### 9.7. `src/com.example.app/com/example/app/Tracing.java`

```java
package com.example.app;

import com.example.core.api.PriceService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class Tracing {
    private Tracing() { }

    public static PriceService wrap(PriceService target) {
        return (PriceService) Proxy.newProxyInstance(
                PriceService.class.getClassLoader(),
                new Class<?>[] { PriceService.class },
                (proxy, method, args) -> around(method, args, target));
    }

    private static Object around(
            Method method, Object[] args, PriceService target)
            throws Throwable {
        String name = method.getName();
        System.out.println("app.before " + name);

        try {
            Object result = method.invoke(target, args);
            System.out.println("app.after " + name);
            return result;
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
```

Đây là một interceptor bao quanh lời gọi **đi qua proxy**. Hàm `around` dùng Reflection để chuyển tiếp tới target, giữ kết quả trả về và mở lỗi target từ `InvocationTargetException` nếu có.

Không gọi nó là “weaving toàn bộ core”: `Tracing` không biến đổi bytecode `DefaultPriceService` và không tự nhận diện các lời gọi nội bộ.

### 9.8. `src/com.example.app/com/example/app/Main.java`

```java
package com.example.app;

import com.example.core.api.PriceService;
import com.example.core.api.Services;

public class Main {
    public static void main(String[] args) {
        PriceService raw = Services.create();
        PriceService wrapped = Tracing.wrap(raw);

        System.out.println("result " + wrapped.quote("A"));
        System.out.println("result " + wrapped.basePrice("A"));
        System.out.println("result " + raw.quote("A"));
    }
}
```

### 9.9. Biên dịch và chạy

Đứng tại thư mục `project` chứa thư mục `src`. Lệnh sau dùng tính năng **module source path** của `javac`, không cần thư viện ngoài:

```bash
javac --module-source-path src -d out -m com.example.core,com.example.app
java --module-path out -m com.example.app/com.example.app.Main
```

**Standard output chính xác:**

```text
app.before quote
core.quote A
core.basePrice A
app.after quote
result 15
app.before basePrice
core.basePrice A
app.after basePrice
result 10
core.quote A
core.basePrice A
result 15
```

Các tệp cùng lệnh đã được kiểm chứng với một JDK hiện đại; mã sử dụng những tính năng có trong **Java 17**. Nếu chạy từ một thư mục khác, đường dẫn tương đối `src`/`out` cần được điều chỉnh tương ứng.

### 9.10. Phân tích ba đường gọi

**Đường 1 — `wrapped.quote("A")`:** caller ở module app gọi proxy, nên handler in `app.before quote`. Sau đó core thực thi `quote`, bên trong nó gọi `basePrice` và in hai dòng `core.*`. Handler nhận kết quả rồi in `app.after quote`. **Không có `app.before basePrice` bên trong `quote`** vì đó là lời gọi nội bộ trực tiếp trên target.

**Đường 2 — `wrapped.basePrice("A")`:** caller app phát lời gọi riêng qua proxy, nên handler in `before basePrice`/`after basePrice`. Đó là một lượt interception khác với lời gọi `basePrice` phát sinh từ bên trong `quote`.

**Đường 3 — `raw.quote("A")`:** app vẫn có thể gọi interface trực tiếp trên target lấy từ factory. Lời gọi vượt ranh giới module nhưng **bỏ qua proxy**, nên không có dòng `app.before`/`app.after`. Ranh giới module không buộc caller phải dùng interceptor.

### 9.11. Điều gì thay đổi nếu thực sự dệt bytecode?

Nếu một engine AOP có khả năng weave **execution entry** của `DefaultPriceService.basePrice`, advice có thể quan sát cả lời gọi nội bộ từ `quote` và lời gọi trực tiếp khác, **với điều kiện đúng class đã được weave và execution join point được hỗ trợ**.

Nếu engine chỉ weave **call site trong app**, nó có thể thấy các biểu thức `wrapped.quote` hoặc `raw.quote` theo tiêu chí, nhưng **không tự thấy call site `basePrice` trong core** nếu core không được weave. Nếu engine weave **call site trong core**, nó có thể quan sát lời gọi nội bộ `quote → basePrice` nếu loại join point phù hợp.

Sự khác biệt nằm ở **mã sở hữu join point**, không phải nơi file aspect được viết.

## 10. Sơ đồ phạm vi module và khả năng bao phủ

```text
                     MODULE APP
             +-------------------------+
             | Main / external callers |
             |     |             |     |
             |     v             |     |
             |  Proxy            |     |
             |     |             |     |
             +-----|-------------|-----+
                   |             |
         inter-module call       | direct call
                   |             |
             +-----v-------------v-----+
             | MODULE CORE             |
             | DefaultPriceService     |
             |     quote()             |
             |        |                |
             |        +--> basePrice() |
             |          intra-module   |
             +-------------------------+
```

Nếu chỉ proxy ở app, hai đường qua proxy được bao phủ, còn direct/internal call có thể không. Nếu dệt execution của target trong core, đường vào execution có thể được bao phủ bất kể caller, miễn instrument đầy đủ. Nếu dệt call join point của app, tác động chủ yếu lên vị trí gọi do app sở hữu.

**Đừng đánh đồng vị trí triển khai concern với phạm vi các điểm được instrument.** Có thể lưu aspect ở một module độc lập nhưng dệt điểm trong nhiều module khác, hoặc aspect nằm cùng module target nhưng chỉ một số class được dệt.

## 11. Chọn call hay execution khi target ở module khác?

Giả sử ba client `A`, `B`, `C` cùng gọi `SharedService.handle` trong module `Shared`.

Một pointcut **call join point** gắn với những vị trí gọi do client sở hữu:

- Dệt caller A: có thể chỉ quan sát lời gọi phát từ A.
- Dệt caller B: quan sát tương ứng từ B.
- Không dệt caller C: có thể không quan sát call site của C dù nó gọi cùng target.
- Nếu target tự gọi method khác, call site ấy nằm trong Shared chứ không thuộc A/B.

Một pointcut **execution join point** gắn với method được định nghĩa ở Shared:

- Dệt method execution của `SharedService.handle`: có thể quan sát mỗi lần method thực thi do A/B/C gọi, cả những đường trực tiếp được hỗ trợ.
- Nhưng nếu còn nhiều implementation hoặc subclass khác không được weave, vẫn có thể thiếu coverage.
- Advice execution không tự biết một caller tĩnh duy nhất; ràng buộc “chỉ caller A” thường cần cơ chế/context bổ sung.

### Bảng lựa chọn

| Câu hỏi cần trả lời | Hướng điểm can thiệp | Lý do |
| --- | --- | --- |
| “Module A gọi API B bao nhiêu lần?” | Call ở A, hoặc boundary client được bảo đảm | Quan tâm nguồn phát lời gọi |
| “B xử lý thao tác bao nhiêu lần?” | Execution ở B | Quan tâm thao tác đích thực sự chạy |
| “Mọi lời gọi nội bộ trong B đã được log chưa?” | Call sites/executions thuộc B | Caller A không chứa các call site nội bộ của B |
| “Tất cả client đều tuân thủ giới hạn chung chưa?” | Ranh giới thực thi đáng tin cậy hoặc adapter không thể bypass | Chỉ weave một client là không đủ |
| “Một lời gọi ở A có thành công end-to-end không?” | Client-side outcome cộng quan sát ở B khi cần | Return/throw ở ranh giới client khác outcome bên trong B |

**Một call event không nhất thiết tương đương một execution event:** target có thể không được vào do lỗi ở caller, around advice có thể không proceed, hoặc retry có thể gây nhiều lần execution. Vì vậy số liệu phía caller và phía callee có thể khác nhau một cách hợp lệ.

## 12. Module phụ thuộc có nằm trong quyền dệt của ứng dụng không?

Một ứng dụng có thể dùng thư viện ngoài hoặc module chỉ có binary artifact. Khả năng weave nó phụ thuộc giấy phép, quyền sửa artifact, công cụ build/runtime và cơ chế class loading.

Các trường hợp phổ biến:

**Ứng dụng sở hữu source và build của B:** dễ đưa B vào pipeline compile/post-compile weaving, nhưng vẫn cần xác định module path, artifact kết quả và bài test.

**Ứng dụng chỉ dùng binary JAR B:** một số weaver có thể dệt bytecode JAR; số khác không. Phải kiểm chứng tương thích class file, chữ ký JAR, licensing và khả năng kiểm thử bản đã biến đổi. Không tự kết luận “không có source thì không thể weave”, cũng không hứa rằng mọi JAR đều biến đổi an toàn.

**Ứng dụng chỉ gọi public API B qua proxy:** có thể instrument ở phía client mà không đổi B, nhưng coverage giới hạn ở các lời gọi đi qua proxy/adapter. Điều này không tương đương dệt execution nội bộ của B.

**B là runtime module của JDK:** can thiệp vào JDK internals có giới hạn nghiêm ngặt hơn về module, security, instrumentation, tương thích phiên bản và hỗ trợ. Không dùng wildcard weave toàn bộ JDK làm mặc định.

### Độc lập compile và nguy cơ version mismatch

Module A có thể được biên dịch với API B phiên bản cũ, còn runtime nạp B mới. Một rule dựa trên tên/chữ ký/hierarchy có thể bỏ lọt hoặc khớp nhầm khi API thay đổi. Với weaving nhúng bytecode, còn có rủi ro liên quan descriptor, method bridge và thứ tự các bước biến đổi.

Hãy coi **phiên bản artifact + phiên bản aspect/weaver + cấu hình weaving + classloader/module graph** là một cấu hình cần được test chung.

## 13. Module path, classpath và class loader

Trong Java, cùng một ứng dụng có thể phối hợp named modules trên **module path** và classpath trong **unnamed module**. Việc hỗ trợ kết hợp này phụ thuộc cấu trúc thực tế và ràng buộc JPMS.

Các điểm cần kiểm tra khi advice không chạy:

- Module đích có được resolve và load đúng từ đường dự kiến không?
- Code có chạy với `--module-path` hay vô tình đặt JAR lên `-classpath`?
- Class cần weave được nạp bởi **class loader nào**?
- Có **hai bản cùng tên class** ở những loader khác nhau không?
- Runtime có load bản đã weave hay bản chưa weave?
- Module descriptor có `requires`/`exports` đúng cho mã của ứng dụng không?
- Có lỗi truy cập Reflective API vì package chưa được mở hợp lệ không?

**Class identity trong JVM phụ thuộc tên class và class loader định nghĩa nó**, không chỉ tên đầy đủ. Một aspect được cấu hình cho class mà loader A nạp không nên mặc định tương đương class cùng tên do loader B nạp. Điều này có thể xảy ra trong kiến trúc plugin, container hoặc hot reload.

### Một module có nhiều package

Khi một JPMS module chứa cả `api` và `internal`, export `api` không làm mọi method trong `internal` tự được instrument. Ranh giới export điều khiển API access, còn weaver chọn những class/bytecode của nó theo cấu hình và khả năng runtime.

### Split package và tổ chức aspect

JPMS đặt ra các quy tắc tránh xung đột package giữa named modules trong một module graph/layer thích hợp. Không nên đặt class aspect và class target trong **cùng package name nhưng khác named module** rồi mặc định Java sẽ xem chúng như một package có thể chia sẻ quyền package-private. Một cách phổ biến là đặt aspect trong package riêng và sử dụng API/weaving phù hợp, thay vì dựa vào package-private cross-module access.

## 14. Visibility không chỉ là `public` hay `private`

Có ba nhóm quyền khác nhau:

1. **Quyền truy cập Java:** private, package-private, protected, public; trong JPMS còn có readability/exports.
2. **Quyền reflective access:** phụ thuộc API Reflection, module opens và điều kiện runtime.
3. **Khả năng instrument:** phụ thuộc compiler/weaver, class loader, transformer và join point hỗ trợ.

Một `private` method trong B không thể được app A gọi trực tiếp như public API, nhưng **nó vẫn có thể thực thi nội bộ**. Một engine bytecode weaving nhất định có thể có khả năng đặt advice lên execution của nó nếu mã được instrument và công cụ hỗ trợ. Proxy interface bên ngoài thường không bắt được private method như một lời gọi public qua proxy.

Vì vậy:

> “Không gọi trực tiếp được từ module A” không đồng nghĩa “không có join point”; còn “gọi public API được” không đồng nghĩa “đã weave mọi execution liên quan”.

### Khi nào mới cần `opens`?

Nếu một resolver/advice cần deep reflection vào một package trong named module, Java có thể áp đặt hạn chế truy cập. Một module descriptor có thể mở **đúng package cho đúng module đọc reflection**, nếu phù hợp với thiết kế và công cụ.

Nhưng không nên mở toàn bộ internals để chữa một lỗi do **weaver không instrument đúng class**. Trước tiên xác định: thất bại là **không có advice được chèn**, **lời gọi bypass interceptor**, hay **lỗi truy cập Reflection**? Ba vấn đề có cách giải quyết khác nhau.

## 15. Method visibility, overriding và dynamic dispatch xuyên module

Client module có thể gọi qua interface hoặc class cha:

```java
// Khai báo minh họa, không phải chương trình độc lập.
interface Processor {
    void process(String data);
}

class FileProcessor implements Processor {
    @Override
    public void process(String data) { }
}
```

Nếu A tham chiếu `Processor` còn B chứa `FileProcessor`, call site trong A biết chữ ký trên interface; runtime execution đi vào method implementation. Điều này ảnh hưởng matching:

- Pointcut theo **call** có thể khớp chữ ký interface ở caller.
- Pointcut theo **execution** có thể khớp class triển khai ở callee.
- Một implementation khác, hoặc một override mới, có thể thay tập execution được khớp.
- Generic type erasure/bridge methods có thể tạo method descriptor bổ sung mà weaver cần xử lý.
- Một class base được instrument nhưng subclass override không được instrument có thể tạo lỗ coverage, tùy loại join point và cách dệt.

Khi cần độ chính xác cao, hãy viết **ma trận type tĩnh của caller × runtime implementation × module chứa method × loại join point**, thay vì chỉ dựa vào tên method.

## 16. Aspects của nhiều mô-đun và thứ tự kết hợp

Ứng dụng có thể có:

```text
module observability -> TraceAspect
module security      -> AccessAspect
module business      -> OrderService
```

Nếu cả hai aspect cùng khớp `OrderService.place`, phải xác định **precedence**:

```text
Trace.before
  Access.before
    OrderService.place()
  Access.after
Trace.after
```

Đó là một thứ tự giả định. Một thứ tự khác có thể khiến Trace chỉ nhìn thấy lời gọi sau Access, hoặc Access ném lỗi trước khi Trace được vào. Nếu quanh một method có around advice, nó có thể không proceed khiến advice nằm bên trong và target không chạy.

**Mô-đun nào chứa aspect không tự quyết định thứ tự advice.** Ordering thường là một chính sách AOP riêng, không thể suy ra từ tên module, thứ tự JAR hay thứ tự khai báo source nếu công cụ không cam kết điều đó.

### Weaving nhiều lượt và advice bị dệt trùng

Trong pipeline nhiều module, có thể vô tình **dệt một artifact hai lần**, hoặc dệt thêm aspect đã được tích hợp từ bước trước. Hệ quả có thể là log trùng, metric nhân đôi hoặc behavior biến đổi nhiều lần. Một số engine có cơ chế tránh trùng, nhưng không phải bảo đảm phổ quát.

Hãy kiểm tra đầu vào/đầu ra từng bước, số advice thực tế mỗi join point và khả năng lặp lại của build. **Idempotent build pipeline** là mục tiêu khác với **idempotent business operation**; cả hai đều cần chú ý.

## 17. Module giao tiếp qua HTTP hoặc message queue

Hai “module” chạy ở hai process riêng không chia sẻ một JVM stack. Ví dụ:

```text
JVM A                           Network                 JVM B
OrderClient.send()
    |
    +-- HTTP request ----------> server.handle()
                                      |
                                      +--> BusinessService.execute()
```

AOP ở phía A có thể quan sát lời gọi client, encode trace context và đo round-trip. AOP phía B có thể quan sát server handler hoặc business execution. Nhưng **method execution ở B không phải join point trong bytecode A**.

Muốn liên kết hai hoạt động:

- Tạo hoặc chuyển **correlation/trace ID** qua header/message metadata.
- Ở phía nhận, xác thực/trích xuất dữ liệu đúng ranh giới tin cậy.
- Thiết lập context trong scope của request/task B, rồi cleanup.
- Có instrumentation phù hợp ở **cả hai tiến trình**.
- Phân biệt sự kiện client đã gửi, server đã nhận, server đã xử lý và phản hồi đã tới client.

Điều này đặc biệt quan trọng với async queue: một message có thể retry và được xử lý nhiều lần. Một advice ở phía gửi không tự bảo đảm advice ở phía nhận được thực thi chính xác một lần.

## 18. Async trong cùng JVM cũng thay đổi phạm vi quan sát

Ngay cả khi cả hai module chạy trong cùng JVM, module A có thể gọi:

```java
// Minh họa, giả sử executor và task đã được định nghĩa.
executor.submit(() -> businessService.process());
```

Có hai thời điểm khác nhau:

- **Call `submit`** xảy ra ở thread caller trong A.
- **Execution `process`** xảy ra trong worker thread, có thể ở module B.

Method call join point quanh `submit` không đồng nghĩa với execution của `process`. Nếu advice chọn dựa trên **control flow của caller** hoặc `ThreadLocal`, worker không tự nhận context ấy. Muốn quan sát đủ và nối quan hệ nhân quả, cần xác định propagation qua task boundary.

### Đo hiệu năng đúng ranh giới

- Thời gian quanh `submit` thường phản ánh **thời gian lên lịch/gửi tác vụ**, không nhất thiết bao gồm thực thi.
- Advice execution ở `process` có thể đo thời gian chạy target nếu điểm đó được weave.
- End-to-end latency cần kết hợp mốc gửi, thời gian chờ và completion.
- Result `CompletableFuture<T>` là một object trả về trước khi computation có thể hoàn tất; after-returning của method trả future không tự quan sát outcome sau này.

Không dùng một tên pointcut “business operations” để khẳng định mọi hoạt động async đều được đo đúng.

## 19. Annotation giữa module: metadata nằm ở đâu?

Một module API có thể chứa interface được đánh dấu annotation:

```java
// Minh họa, annotation và import chưa được khai báo trong snippet.
interface PaymentApi {
    @Audited
    void charge(String orderId);
}
```

Module implementation có thể override method mà không đặt lại `@Audited`. Annotation trên **interface method** không tự biến thành annotation trên override implementation theo quy tắc kế thừa method annotation của Java.

Nếu pointcut hoặc advice resolver đọc **method implementation** nhưng annotation chỉ có ở **API interface**, nó có thể không tìm thấy metadata. Có thể cần một quy tắc tìm **interface method → implementation method → class-level default**, hoặc thứ tự khác được thiết kế rõ.

Khi chạy với JPMS, khả năng truy cập các annotation type/reflective methods còn phụ thuộc module dependencies/visibility. Nhưng mở package không tự làm annotation bị thiếu xuất hiện trên một phần tử khác.

### Type retention và giai đoạn weave

Annotation có retention `SOURCE`, `CLASS` hoặc `RUNTIME`. Reflection runtime thông thường cần retention `RUNTIME`, còn công cụ phân tích source/bytecode có thể đọc metadata ở giai đoạn khác nếu được hỗ trợ.

Nếu pointcut dựa vào annotation mà một module được compile với retention không phù hợp cơ chế, advice có thể không chọn được join point dự kiến. Đây là vấn đề **metadata availability**, không phải “module B ở quá xa”.

## 20. Rủi ro kiến trúc: chính sách toàn cục chỉ được dệt một phần

Một mối quan tâm cắt ngang như audit có thể được yêu cầu áp dụng **trong toàn hệ thống**. Nhưng nếu chỉ dệt module web/client, các đường sau có thể bỏ lọt:

- Worker batch gọi trực tiếp business module.
- Một module khác dùng API business không qua proxy chung.
- Self-invocation trong business implementation.
- Reflection hoặc callback gọi method trong target.
- Một module/plugin được load muộn không nằm trong tập weaving.
- Một process khác có cùng business API nhưng chạy artifact chưa weave.

**Không sử dụng partial weaving làm lớp bảo vệ duy nhất cho bất biến nghiệp vụ hoặc authorization bắt buộc.** Nếu chính sách là ràng buộc không thể bỏ qua, phải xác định một boundary mà **mọi đường thực thi liên quan đi qua**, hoặc dệt/instrument đầy đủ và có test chứng minh coverage trong phạm vi được hỗ trợ.

### Quan sát và bảo vệ là hai mức kỳ vọng khác nhau

Mất một log có thể là lỗi observability. Mất một kiểm tra quyền là lỗi bảo mật. Với concern quan trọng, cần trả lời rõ:

1. Có chấp nhận partial coverage không?
2. Có test nào phát hiện đường bypass mới khi thêm module không?
3. Mục tiêu là **đảm bảo** hay **ghi nhận theo khả năng tốt nhất**?
4. Khi weaver/agent không hoạt động, ứng dụng có được tiếp tục khởi chạy không?
5. Nếu thiếu aspect trong một module deployment, điều gì sẽ cảnh báo người vận hành?

Phạm vi và chính sách khi lỗi phải là lựa chọn thiết kế được ghi nhận, không bị mặc định.

## 21. Hiệu năng, kích thước build và chi phí mở rộng phạm vi

Càng nhiều class được dệt, càng cần quan tâm:

- Thời gian build, kích thước artifact và chi phí transformation ở runtime.
- Các method tần suất cao bị instrument ngoài ý muốn.
- Việc thu thập context/argument/result có tạo object hoặc log quá nhiều không?
- Chi phí nhận diện join point động và đọc annotation/config.
- Tác động của advice lên inlining/JIT, stack trace, debugging và profiling.
- Số lượng aspect cùng khớp làm độ sâu chuỗi advice tăng.
- Độ phức tạp khi nâng cấp compiler, JDK, class file version và dependency.

Tối ưu không có nghĩa luôn thu hẹp weaving xuống một JAR. Nếu mục tiêu yêu cầu execution coverage trên nhiều module, thu hẹp không đúng sẽ làm mất dữ liệu. Cách hợp lý là **xác định rõ phạm vi cần thiết, kiểm tra điểm khớp thực, sau đó đo overhead**.

### Wildcard xuyên module có thể nguy hiểm

Một pattern “chọn mọi method của mọi module” dễ tạo side effect bất ngờ:

- Log sâu vào thư viện hoặc hạ tầng nội bộ.
- Advice tự gọi method cũng bị pointcut chọn, gây reentrancy.
- Serialization/logging lộ dữ liệu nhạy cảm.
- Giới hạn vòng đời trên thread hoặc asynchronous task không được xử lý.
- Mỗi lần upgrade dependency xuất hiện thêm method bị can thiệp.

Nên có **điều kiện type/package/module/artifact rõ ràng** và danh sách loại trừ cho infrastructure của chính aspect nếu phù hợp công cụ.

## 22. Lập sơ đồ weaving scope trước khi triển khai

Một tài liệu thiết kế ngắn có thể có bốn phần:

**(1) Module graph:**

```text
app (caller)
  -> api (contracts)
      -> implementation (targets)
observability (aspects)
```

**(2) Join point cần chọn:**

```text
call: app.CheckoutFlow -> api.PaymentApi.pay(String)
execution: implementation.PaymentService.pay(String)
```

**(3) Mã/artifact cần instrument:**

```text
call join point       -> weave app call sites
execution join point  -> weave implementation method bodies
```

**(4) Những đường cần kiểm thử:**

```text
app -> proxy -> target
app -> direct target
implementation -> this.otherMethod()
batch -> target
reflection -> target
worker task -> target
```

Khi bốn phần rõ ràng, mới chọn compile-time, post-compile, load-time weaving hay proxy phù hợp. Tránh quyết định theo lời quảng cáo “cross-cutting = toàn cục”; **khả năng kết hợp concern không tự tạo coverage toàn cục**.

## 23. Quy trình dệt qua nhiều module theo từng bước

1. **Định danh module thật:** package, build module, JAR, named JPMS module, class loader hoặc process?
2. **Chọn concern và join point:** audit execution ở target, hay đo lời gọi từ caller?
3. **Vẽ graph phụ thuộc:** module nào chứa interface, implementation, aspect và entry points?
4. **Xác định quyền sở hữu bytecode:** ai build/có thể biến đổi module target hoặc caller?
5. **Chọn cơ chế:** compile-time, post-compile, load-time hoặc proxy/interceptor. Không coi chúng tương đương.
6. **Chọn phạm vi class/artifact:** module nào được weave, tập package/type nào được đưa vào?
7. **Kiểm tra JPMS/access:** `requires`, `exports`, `opens` chỉ khi thực sự cần; tránh mở rộng quyền quá mức.
8. **Xử lý artifact build:** bảo đảm bản đã weave được đóng gói và nạp thực.
9. **Xác định precedence:** nếu aspect từ nhiều module cùng khớp, thứ tự có rõ ràng không?
10. **Kiểm thử đường gọi:** external/internal/direct/override/async/reflection.
11. **Đánh giá policy khi lỗi:** thiếu weaving, thiếu annotation, class không load hoặc agent lỗi có được tiếp tục không?
12. **Giám sát và nâng cấp:** thêm module hoặc dependency mới phải kích hoạt regression coverage.

Đây là quy trình **thiết kế và xác minh**; cú pháp build flags và plugin của từng AOP engine phải lấy từ công cụ được chọn, không thể suy ra từ JPMS.

## 24. Ma trận kiểm thử intra-/inter-module

| Tình huống | Điều cần chứng minh |
| --- | --- |
| Caller và target ở cùng module | Join point tương ứng có được engine hỗ trợ và weave không? |
| Caller ở A, target ở B | Advice nằm ở call site A hay execution B? |
| Module C dùng cùng target B | Coverage có tự bao gồm C hay cần weave C? |
| B tự gọi method của chính nó | Proxy có bị bypass; bytecode execution có bao phủ? |
| Client đi qua proxy | Advice chạy đúng một lượt theo contract? |
| Client gọi raw target | Có đường bypass ngoài ý muốn không? |
| Implementation trong package không export | App có dùng API public factory/interface hợp lệ? |
| Reflection vào package không open | Có lỗi quyền truy cập; đó có thật là lỗi weaving? |
| Một method bị override ở subtype | Pointcut có áp dụng trên execution implementation mới? |
| Interface chứa annotation, implementation không | Resolver tìm đúng metadata? |
| Generic bridge method | Có advice bị trùng hoặc bỏ lọt không? |
| Một JAR được weave nhưng runtime dùng JAR gốc | Test có phát hiện artifact sai? |
| Module plugin dùng class loader riêng | Transformer/resolver có bao phủ loader đó? |
| Task chạy trên worker thread | Context, advice và outcome có đúng đường execution? |
| Gọi sang process khác | Mỗi phía có instrumentation riêng và trace propagation? |
| Hai aspect nằm ở hai module | Precedence và số lần advice chạy có ổn định không? |
| Điểm can thiệp có lỗi | Caller nhận đúng result/exception theo contract? |
| Module/phiên bản mới được thêm | Coverage regression có chạy trước phát hành? |

### Đừng chỉ assert số dòng log

Một test xác nhận “có log audit” chưa cho biết log có đúng **mỗi lần execution** và không bỏ sót đường bypass. Với các concern quan trọng, nên có:

- Counter/log riêng ở target để biết số lần execution thật.
- Counter/log riêng ở call/interception boundary.
- Test cho cả ca **phải có advice** và **không được có advice**.
- Test thay đổi một chiều **chỉ weave A**, **chỉ weave B**, **weave cả hai** nếu công cụ hỗ trợ để xác minh hiểu đúng địa điểm join point.
- Đối chiếu artifact hashes/bytecode metadata trong pipeline khi cần xác minh bản woven được deploy.

## 25. Chẩn đoán “module này không thấy advice”

Khi một method ở module B không có advice dù pointcut có vẻ đúng, hãy kiểm tra theo thứ tự:

**Bước 1 — Xác định đường gọi thật.** Caller có đang gọi object proxy, hay trực tiếp target? Có self-invocation không? Nếu target không được vào do exception ở caller, execution advice có điểm để chạy không?

**Bước 2 — Xác định loại join point.** Bạn muốn call ở caller A hay execution ở target B? Engine có hỗ trợ loại đó không?

**Bước 3 — Kiểm tra phạm vi weave.** Class/JAR chứa điểm can thiệp có nằm trong đầu vào build/transform không? Có bị exclude? Class có được load trước transformer không?

**Bước 4 — Xác định module path và loader.** Runtime có load đúng class từ đúng artifact/module/loader không? Có bản gốc lẫn bản woven không?

**Bước 5 — Kiểm tra pointcut matching.** Signature, package, type hierarchy, visibility, annotation/interface/implementation có đúng không?

**Bước 6 — Kiểm tra access.** Có lỗi JPMS exports/opens hoặc lỗi Reflection quyền truy cập cụ thể không?

**Bước 7 — Kiểm tra precedence và around.** Aspect bên ngoài có bỏ qua `proceed`, nuốt exception hoặc thay kết quả khiến advice bên trong không được gọi không?

**Bước 8 — Kiểm tra runtime task.** Lời gọi có xảy ra ở worker/process khác? Có nhầm ngữ cảnh/ThreadLocal không?

**Bước 9 — Kiểm tra output và test.** Có thể advice đã chạy nhưng logging/metric bị tắt, bị sampling hoặc sink lỗi. Tách test “advice entered” khỏi test “đã xuất log”.

Quy trình này giúp phân biệt **không dệt**, **không khớp**, **bypass**, **không đủ quyền** và **advice chạy nhưng không quan sát được kết quả**.

## 26. Các lỗi tư duy thường gặp

1. **“Aspect nằm trong module A nên chỉ tác động tới A.”** Không nhất thiết; phạm vi được quyết định bởi điểm weave và cấu hình, không chỉ nơi đặt code advice.
2. **“Aspect nằm trong module A thì tự tác động mọi module.”** Cũng không; target/caller cần được đưa vào cơ chế can thiệp.
3. **“Hai method cùng module chắc chắn có self-invocation interception.”** Không đúng với nhiều mô hình proxy.
4. **“Module A có `requires B` nghĩa là AOP sẽ weave B.”** JPMS readability không tự tạo weaving.
5. **“Package được `exports` thì Reflection đọc được mọi private member.”** Không; deep reflection có quy tắc khác.
6. **“Thêm `opens` là đủ để advice chạy.”** Không, nếu bytecode chưa được weave hoặc đường gọi không qua interceptor.
7. **“Dệt interface API là đã dệt toàn bộ implementation.”** Không tự động, vì thân implementation có thể nằm ở module khác.
8. **“Call join point từ A tương đương execution join point ở B.”** Khác phía và tập đường đi được quan sát.
9. **“Một JAR đã weave là mọi runtime dùng bản woven.”** Phải xác minh deployment/module path/class loader.
10. **“Không có source của dependency thì mọi kiểu interception đều bất khả thi.”** Có thể có binary weaving hoặc proxy, tùy công cụ/quyền.
11. **“Một interceptor ở app nhận mọi lời gọi nội bộ core.”** Internal call có thể bypass proxy ở app.
12. **“Lời gọi qua HTTP cũng là method execution join point trong JVM caller.”** Không; process bên kia có stack/runtime riêng.
13. **“Annotation trên interface luôn được reflection ở implementation tìm thấy.”** Java không tự kế thừa method annotation như vậy.
14. **“Weave tất cả package giúp đảm bảo an toàn.”** Wildcard rộng có thể gây recursion, log nhạy cảm và chi phí lớn; coverage cũng còn phụ thuộc đường runtime.
15. **“Pointcut đúng cú pháp thì không cần test thực.”** Matching, weaving và runtime deployment là ba lớp khác nhau.
16. **“Thứ tự module/JAR quyết định precedence.”** Chỉ đúng khi engine cam kết semantics tương ứng; không suy đoán.
17. **“Đo submit là đo task execution.”** Không, nếu tác vụ chạy bất đồng bộ.
18. **“Một aspect audit chỉ cần tồn tại là chính sách audit được bảo đảm.”** Cần boundary đáng tin cậy và test coverage toàn bộ đường đi liên quan.

## 27. Bài tập thực hành

1. **Chạy hai JPMS module:** tạo đúng bảy file của `com.example.core` và `com.example.app`; biên dịch/chạy theo lệnh đã nêu, đối chiếu toàn bộ stdout.
2. **So sánh proxy và raw target:** bỏ lời gọi `wrapped.basePrice("A")`, giữ `wrapped.quote("A")`. Có bao nhiêu dòng `app.before basePrice` và tại sao?
3. **Đổi gọi nội bộ:** sửa `quote` để gọi `basePrice` hai lần. Dự đoán số dòng `core.basePrice` và số dòng advice.
4. **Đổi ranh giới API:** thử import trực tiếp `com.example.core.internal.DefaultPriceService` trong app. Quan sát lỗi compile do package không export, rồi giải thích vì sao factory public tránh vấn đề.
5. **Lọc theo type:** trong `Tracing.around` chỉ in advice nếu tên method là `quote`. Method `basePrice` của proxy còn chạy nghiệp vụ không?
6. **Thêm module client C:** một named module mới `com.example.batch` yêu cầu core và gọi `Services.create().quote(...)` không qua app proxy. Đường gọi đó có log `app.before` không?
7. **Thiết kế call pointcut:** một chính sách chỉ đo lời gọi từ module app tới core. Nêu nơi cần weave và vì sao dệt execution core sẽ quan sát cả batch client.
8. **Thiết kế execution pointcut:** một chính sách phải audit mọi lần `DefaultPriceService.basePrice` thực sự chạy. Nêu phạm vi target cần instrument và đường test phải bao gồm.
9. **Phân tích build:** tạo sơ đồ compile A/B → post-compile weave B → runtime. Nếu deploy B.jar gốc thì kết quả test nào phải thất bại?
10. **Module permissions:** phân biệt khi nào `exports` cần thiết và khi nào một reflection tool có thể cần `opens`. Vì sao không nên `open module` tùy tiện?
11. **Overriding:** thêm `PriceService` implementation thứ hai ở module core. Một pointcut execution chỉ chọn implementation thứ nhất có thể bỏ lọt những lượt gọi nào?
12. **Async:** đưa `wrapped.quote("A")` vào một task trên executor. Advice proxy còn chạy không, và trace ID từ ThreadLocal caller có tự đi theo worker không?
13. **Multiple aspects:** thêm một proxy audit bao ngoài proxy tracing, rồi so sánh thứ tự log khi đảo cách lồng hai proxy.
14. **Boundary mạng:** vẽ hai process A/B trao đổi HTTP. Liệt kê join point local ở mỗi phía, nơi truyền trace headers và khác biệt giữa send/receive/execute.
15. **Coverage test:** tạo ma trận có ít nhất năm đường phải có advice và năm đường không thuộc chính sách; nêu một kiểm chứng tại runtime cho từng đường.

**Tự kiểm tra:** Khi chọn execution trong B, module nào chứa bytecode cần weave? Khi chọn call phát từ A, đâu là nơi chứa call site? `requires`/`exports` có tự chạy advice không? Proxy ở app có tự intercept self-invocation trong core không? Một pointcut rộng có bảo đảm module ngoài tập instrument được bao phủ không? Cùng một target nằm trong module khác có thể được quan sát từ cả call và execution theo những điều kiện nào?

## Kết luận

**Phạm vi dệt trong một mô-đun và giữa các mô-đun không được quyết định chỉ bằng cách tổ chức package hoặc vị trí đặt aspect.** Nó phụ thuộc **loại join point**, **mã sở hữu call site hay execution entry**, **tập class/artifact được weave**, **cơ chế compile-time/post-compile/load-time/proxy**, và **module graph/class loader/deployment thực tế**. Với Java JPMS, `requires`, `exports` và `opens` xác định ranh giới truy cập, chứ không tự đưa advice vào chương trình. Một kiến trúc AOP nhiều module đáng tin cậy phải thiết kế coverage tường minh, test cả lời gọi từ ngoài lẫn lời gọi nội bộ/bypass, xác định precedence và kiểm chứng artifact đã dệt được chạy thật. Khi đó aspect mới thực hiện được mối quan tâm cắt ngang **đúng phạm vi, đủ đường đi và có thể kiểm chứng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-006">Quan hệ giữa thứ tự xây dựng và kết quả dệt</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa thứ tự xây dựng và kết quả dệt trong kiến trúc AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), **weaving (dệt)** là quá trình kết hợp hành vi cắt ngang (**advice**) với những vị trí thực thi (**join point**) mà **pointcut** lựa chọn. Một chương trình có thể trải qua nhiều công đoạn: phân tích nguồn, biên dịch, tạo mã, biến đổi bytecode, liên kết thành phần, nạp lớp hoặc tạo lớp bọc lúc chạy. **Thứ tự thực hiện các công đoạn và các phép biến đổi có thể ảnh hưởng trực tiếp tới mã cuối cùng, tập điểm được chọn và hành vi thực thi.**

Tuy nhiên, **“thứ tự xây dựng” không tự động bằng “thứ tự advice chạy”**. Một công cụ có thể xây dựng nhiều aspect theo thứ tự bất kỳ nhưng sử dụng quy tắc precedence rõ ràng để tạo cùng một chuỗi advice. Ngược lại, một hệ thống ghép các lớp wrapper tuần tự có thể tạo **thứ tự bao ngoài–bên trong ngược với thứ tự thêm wrapper**. Cần phân biệt các tầng này trước khi kết luận một thay đổi thứ tự build làm chương trình chạy khác.

Bài này trình bày mối quan hệ giữa **pipeline xây dựng**, **weaving**, **ưu tiên advice**, **tính ổn định của kết quả dệt**, tác động khi có nhiều phép biến đổi và cách kiểm thử. Những mô hình và giả mã được dùng để giải thích kiến trúc AOP nói chung; thời điểm, cú pháp và ngữ nghĩa cụ thể tùy cơ chế triển khai.

## 1. Bốn loại “thứ tự” phải tách riêng

Cụm từ “thứ tự” trong AOP có thể chỉ các khái niệm khác nhau:

| Thứ tự | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Thứ tự xây dựng (build order)** | Module, nguồn, lớp hay bước build nào được xử lý trước? | Tạo mã → biên dịch → xử lý bytecode → đóng gói |
| **Thứ tự áp dụng phép biến đổi (transformation/weaving order)** | Phép biến đổi nào đọc/sửa biểu diễn chương trình trước? | `W1` xử lý class trước `W2` |
| **Thứ tự kết hợp advice (precedence/composition)** | Advice nào bao ngoài/được ưu tiên ở một join point? | `Audit(Timing(Business))` |
| **Thứ tự chạy (runtime execution order)** | Khi thực thi, before/after/around được quan sát theo thứ tự gì? | `Audit.before` → `Timing.before` → business |

Bốn thứ tự này **có thể tác động lẫn nhau**, nhưng không được dùng thay thế cho nhau.

Ví dụ, một hệ thống build biên dịch hai tệp chứa aspect theo thứ tự A rồi B. Nếu bộ weaver dùng một khai báo ưu tiên **B bao ngoài A**, thứ tự thực thi vẫn là `B.before → A.before → nghiệp vụ → A.after → B.after`. Thứ tự tên tệp hoặc thời điểm biên dịch **không phải** bằng chứng rằng A có precedence cao hơn B.

Ngược lại, nếu một chương trình xây dựng chuỗi xử lý bằng cách lần lượt bọc A rồi bọc B:

~~~text
initial = Business
after A = A(Business)
after B = B(A(Business))
~~~

thì B là **lớp ngoài** và thường chạy phần trước trước A. Đây là kết quả của **toán tử bọc được chọn**, không phải định luật chung về tất cả weaver AOP.

## 2. Weaving xảy ra ở những giai đoạn nào?

Một kiến trúc AOP có thể lựa chọn những thời điểm kết hợp hành vi khác nhau.

### 2.1 Dệt trước hoặc trong quá trình biên dịch

Với cách xử lý mã nguồn hoặc một compiler có khả năng nhận biết aspect, công cụ có thể phân tích khai báo điểm cắt, kiểu và cấu trúc chương trình rồi tạo mã kết hợp. Một số thông tin nguồn có thể hỗ trợ so khớp và kiểm tra lỗi trước khi chạy.

Sơ đồ khái niệm:

~~~text
Mã nghiệp vụ + khai báo aspect
              |
              v
     Phân tích/chọn join point
              |
              v
       Kết hợp hành vi
              |
              v
      Mã/bytecode kết quả
~~~

Không nên mặc định mọi công cụ dệt từ mã nguồn có thể nhìn thấy các phần tử được sinh ở **bước chạy sau**, chẳng hạn lớp proxy tạo tại runtime. Tập mã và metadata có sẵn **ở thời điểm dệt** quyết định những gì công cụ có thể phân tích.

### 2.2 Dệt sau biên dịch, trên bytecode

Một weaver có thể đọc các tệp lớp đã được compiler tạo và biến đổi chúng:

~~~text
Java sources
     |
     v
Compiled classes
     |
     +---> Bytecode weaver + aspect definitions
                    |
                    v
              Woven classes
                    |
                    v
              Packaging
~~~

Ở tầng này, các chữ ký đã qua **type erasure**, bridge/synthetic methods và mã sinh có thể đã tồn tại. Một pointcut theo chữ ký ở bytecode có thể cần hiểu cấu trúc khác với cú pháp generic ở mã nguồn; một bộ AOP cũng có thể **chủ động loại bỏ** một số phương thức do compiler sinh khỏi tập join point được hỗ trợ.

Nếu công cụ biến đổi khác cũng chèn mã hoặc sửa descriptor trong cùng pipeline, **thứ tự các phép biến đổi có thể làm chúng nhìn thấy những biểu diễn khác nhau**.

### 2.3 Dệt khi nạp lớp

Với mô hình load-time weaving, một bộ biến đổi có thể thay đổi biểu diễn lớp khi runtime chuẩn bị nạp nó. Khi đó **thứ tự tác nhân biến đổi**, điều kiện lớp đã được nạp hay chưa, class loader nào chịu trách nhiệm và cấu hình runtime trở nên quan trọng.

Lớp đã được nạp trước khi cấu hình can thiệp có hiệu lực có thể không đi qua cùng đường biến đổi. Không nên suy rằng chỉ đổi file build là đủ để ảnh hưởng mọi lớp đã nạp trong một JVM đang chạy.

### 2.4 Lớp bọc hoặc proxy tại runtime

Một cơ chế AOP có thể tạo một wrapper/proxy chặn lời gọi qua một giao diện hoặc biên đối tượng:

~~~text
Caller → Outer proxy → Inner proxy → Target object
~~~

Ở đây, thứ tự tạo lớp bọc và quy tắc ghép của container/builder có thể xác định proxy nào ở ngoài. Nhưng proxy thường chỉ quan sát lời gọi **đi qua nó**; gọi trực tiếp vào target hoặc một lời gọi nội bộ bỏ qua proxy có thể không được advice tương ứng can thiệp.

**Tóm lại:** thời điểm weaving ảnh hưởng đến **tập join point có thể quan sát**, thông tin cấu trúc sẵn có và tính chất của artifact tạo ra. Thời điểm đó không tự cung cấp quy tắc ưu tiên chung giữa nhiều advice.

## 3. “Kết quả dệt” là gì?

Kết quả weaving không chỉ là “trong bytecode có thêm vài dòng ghi log”. Nó gồm ít nhất ba lớp quan sát:

1. **Artifact được tạo:** mã nguồn trung gian, bytecode, class đã nạp, proxy, wrapper hoặc metadata do weaver sinh.
2. **Cấu trúc can thiệp:** những join point được chọn, thứ tự kết hợp advice, đường gọi tiếp tục hoặc short-circuit.
3. **Hành vi runtime:** giá trị trả về, ngoại lệ, tác dụng phụ, thứ tự ghi log, thời gian đo, kiểm soát quyền và trạng thái tài nguyên.

Hai lần build có thể cho **bytecode khác nhau** vì timestamp, đường dẫn nguồn, tên synthetic hoặc metadata nhưng **hành vi tương đương** đối với các yêu cầu cần kiểm tra. Ngược lại, hai artifact cùng được biên dịch thành công vẫn có thể khác thứ tự advice và gây sai nghĩa vụ bảo mật hoặc giao dịch.

Cần định nghĩa rõ tiêu chuẩn so sánh:

- **Byte-for-byte reproducibility:** kết quả artifact trùng từng byte theo mô hình build có kiểm soát.
- **Structural equivalence:** tập join point, chuỗi advice và loại biến đổi tương đương.
- **Behavioral equivalence:** các quan sát mà hợp đồng nghiệp vụ cho phép vẫn giống nhau.

Không nên khẳng định mọi build đúng bắt buộc có bytecode giống hệt nhau; nhưng **thay đổi cấu trúc hoặc kết quả chạy không mong muốn** phải được nhận diện.

## 4. Vì sao các phép biến đổi không nhất thiết giao hoán?

Gọi `P` là biểu diễn chương trình, `W_A` và `W_B` là hai phép biến đổi. Theo toán học, hai thứ tự:

~~~text
Result1 = W_B(W_A(P))
Result2 = W_A(W_B(P))
~~~

**không nhất thiết tạo cùng kết quả**:

~~~text
W_B(W_A(P)) ≠ W_A(W_B(P))
~~~

Dấu khác nhau ở đây là một **khả năng**, không phải kết luận mọi cặp weaver đều khác nhau. Nếu hai phép biến đổi độc lập và công cụ định nghĩa cơ chế kết hợp ổn định, chúng có thể cho kết quả tương đương.

### 4.1 Tương tác chọn điểm

Giả sử `W_A` tạo thêm một phương thức `helper()` và `W_B` chọn mọi method execution trong một phạm vi. Nếu `W_B` chạy sau `W_A`, nó **có thể** thấy helper mới; nếu chạy trước, helper chưa tồn tại. Khả năng có thật join point cho helper, và việc mã sinh có được chọn hay không, vẫn tùy bộ weaver.

Trường hợp ngược lại, nếu `W_A` xóa, đổi tên, inlining hoặc biến đổi descriptor mà `W_B` dựa vào để match, `W_B` có thể chọn tập điểm khác.

### 4.2 Tương tác hành vi

Ví dụ `W_A` thêm validation, `W_B` thêm cache:

~~~text
Validation(Cache(Business))
Cache(Validation(Business))
~~~

Ở cấu trúc đầu, kiểm tra validation ở ngoài sẽ chạy trước nhánh cache. Ở cấu trúc sau, một cache hit có thể trả sớm và **bỏ qua validation bên trong**. Nếu validation là kiểm tra quyền bắt buộc thì thứ tự thứ hai có thể không chấp nhận được.

Ví dụ này minh họa **thứ tự kết hợp advice có thể ảnh hưởng hợp đồng**, không hàm ý thứ tự mà một công cụ AOP cụ thể tự động chọn.

### 4.3 Tương tác đo lường

~~~text
Timing(Transaction(Business))
Transaction(Timing(Business))
~~~

Nếu timing nằm ngoài transaction, nó có thể bao gồm thời gian tạo và kết thúc giao dịch. Nếu timing bên trong transaction, độ trễ đo được hẹp hơn. Cả hai số đo có thể hợp lệ **nếu tên metric nói đúng phạm vi**; không nên gọi cả hai là “thời gian nghiệp vụ thuần túy”.

### 4.4 Tương tác ngoại lệ

Một advice A bắt lỗi rồi trả fallback, trong khi advice B dựa vào ngoại lệ để rollback. Nếu A nằm **trong B**, B có thể thấy lần gọi trả về bình thường; nếu A nằm **ngoài B**, B có thể nhận lỗi và rollback trước khi A trả fallback. Hai kết quả rất khác nhau dù advice và pointcut không đổi.

Vì vậy, quyết định precedence cần bắt nguồn từ **hợp đồng nghiệp vụ**, không chỉ từ độ đẹp của thứ tự log.

## 5. Thứ tự “áp dụng” wrapper có thể đảo ngược “thứ tự chạy”

Đây là một nguồn lỗi đặc biệt thường gặp với builder tự tạo chuỗi xử lý.

Nếu thuật toán là:

~~~text
chain = Business
for each advice in constructionList:
    chain = Wrap(advice, chain)
~~~

mỗi lần `Wrap` bao **ngoài** chuỗi hiện có. Do đó:

~~~text
constructionList = [AUDIT, TIMING]

step 0: Business
step 1: AUDIT(Business)
step 2: TIMING(AUDIT(Business))
~~~

**Thứ tự thêm:** `AUDIT` rồi `TIMING`.

**Thứ tự chạy before:** `TIMING` rồi `AUDIT`, sau đó mới tới `Business`.

**Thứ tự chạy after:** `AUDIT` rồi `TIMING` nếu cả hai lớp cho phép đi tiếp và trả về bình thường.

Đây là hệ quả của việc dùng phép bọc **bên ngoài** qua vòng lặp. Một builder khác có thể dùng `foldRight`, đảo danh sách trước khi áp dụng, hoặc đặt vị trí thêm mới **bên trong** chuỗi. Vì thế không có công thức chung rằng “phần tử đầu tiên trong danh sách luôn là outermost”.

## 6. Ví dụ Java 17 chạy độc lập: thứ tự xây dựng và đầu ra

Ví dụ sau dùng **wrapper Java thuần túy** để diễn giải cấu trúc kết hợp advice. Nó không khai báo ngôn ngữ pointcut hoặc tự dệt bytecode, mà cố ý đặt tên `assemble` và `wrap` để cho thấy chính xác **quy tắc xây dựng nào tạo thứ tự chạy nào**.

Chương trình có hai lớp minh họa `AUDIT` và `TIMING`, một nghiệp vụ `Business`, nhánh thành công và nhánh ném ngoại lệ. Mỗi wrapper thêm tên của mình vào kết quả, giúp thấy rằng **thứ tự lồng nhau có thể đổi cả giá trị trả về**, không chỉ thứ tự các dòng log.

Lưu file `WeavingBuildOrderDemo.java`:

~~~java
import java.util.List;
import java.util.Locale;

public class WeavingBuildOrderDemo {
    @FunctionalInterface
    interface Step {
        String run(String input);
    }

    static Step business() {
        return input -> {
            System.out.println("BUSINESS " + input);
            if ("fail".equals(input)) {
                throw new IllegalStateException("refused");
            }
            return input.toUpperCase(Locale.ROOT);
        };
    }

    static Step wrap(String name, Step next) {
        return input -> {
            System.out.println(name + " BEFORE");
            try {
                String innerResult = next.run(input);
                String result = name + "(" + innerResult + ")";
                System.out.println(name + " AFTER " + result);
                return result;
            } catch (RuntimeException error) {
                System.out.println(name + " ERROR "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println(name + " FINALLY");
            }
        };
    }

    // Chú ý: mỗi lần lặp tạo wrapper bên NGOÀI chuỗi đang có.
    static Step assemble(List<String> constructionOrder) {
        Step chain = business();
        for (String name : constructionOrder) {
            chain = wrap(name, chain);
        }
        return chain;
    }

    static void demonstrate(
            String caseName, List<String> order, String input) {
        System.out.println("CASE " + caseName);
        Step chain = assemble(order);
        try {
            System.out.println("RESULT " + chain.run(input));
        } catch (RuntimeException error) {
            System.out.println("CALLER " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        demonstrate(
                "AUDIT_THEN_TIMING",
                List.of("AUDIT", "TIMING"),
                "ok");
        demonstrate(
                "TIMING_THEN_AUDIT",
                List.of("TIMING", "AUDIT"),
                "ok");
        demonstrate(
                "FAIL",
                List.of("AUDIT", "TIMING"),
                "fail");
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac WeavingBuildOrderDemo.java
java WeavingBuildOrderDemo
~~~

Stdout chính xác của chương trình:

~~~text
CASE AUDIT_THEN_TIMING
TIMING BEFORE
AUDIT BEFORE
BUSINESS ok
AUDIT AFTER AUDIT(OK)
AUDIT FINALLY
TIMING AFTER TIMING(AUDIT(OK))
TIMING FINALLY
RESULT TIMING(AUDIT(OK))
CASE TIMING_THEN_AUDIT
AUDIT BEFORE
TIMING BEFORE
BUSINESS ok
TIMING AFTER TIMING(OK)
TIMING FINALLY
AUDIT AFTER AUDIT(TIMING(OK))
AUDIT FINALLY
RESULT AUDIT(TIMING(OK))
CASE FAIL
TIMING BEFORE
AUDIT BEFORE
BUSINESS fail
AUDIT ERROR IllegalStateException
AUDIT FINALLY
TIMING ERROR IllegalStateException
TIMING FINALLY
CALLER refused
~~~

### 6.1 Phân tích hai thứ tự xây dựng

Trường hợp đầu:

~~~text
assembly: AUDIT → TIMING
result:   TIMING(AUDIT(Business))
~~~

Thế nên `TIMING BEFORE` xuất hiện trước `AUDIT BEFORE`; lớp `AUDIT` hoàn tất trước và `TIMING` hoàn tất sau.

Trường hợp thứ hai:

~~~text
assembly: TIMING → AUDIT
result:   AUDIT(TIMING(Business))
~~~

Cùng hai wrapper và cùng input `"ok"` nhưng kết quả lần lượt là:

~~~text
TIMING(AUDIT(OK))
AUDIT(TIMING(OK))
~~~

Tức các phép kết hợp **không giao hoán** đối với ví dụ này. Điều đó cho thấy chỉ cần thay thứ tự ghép có thể đổi kết quả quan sát được.

### 6.2 Phân tích nhánh lỗi

Ở trường hợp `FAIL`, thân nghiệp vụ ném `IllegalStateException`. Wrapper trong `AUDIT` quan sát lỗi trước, chạy `finally`, rồi lớp ngoài `TIMING` quan sát lỗi và chạy `finally`. Không có dòng `AFTER` thành công bởi phương thức bên trong không trả về bình thường.

Ví dụ giữ nguyên ngoại lệ gốc. Trong AOP thực tế, nếu một around advice bắt rồi **nuốt hoặc biến đổi ngoại lệ**, lớp ngoài có thể nhìn thấy một kết quả hoặc kiểu lỗi khác. Vì thế cần kiểm thử đường lỗi theo **thứ tự lồng nhau thật**, không chỉ đoán từ tên advice.

### 6.3 Giới hạn của ví dụ

- `AUDIT` và `TIMING` chỉ là tên wrapper. Chương trình không tạo audit store và không đo thời gian thật.
- Việc thêm tên vào giá trị trả về có chủ đích để minh họa sự **khác nhau của kết quả**; một advice đo lường production thường phải **bảo toàn kết quả gốc**.
- Đây là **mô hình ghép lúc chạy**. Không thể suy rằng một weaver compile-time cũng bọc các lớp theo đúng vòng lặp này.
- Chương trình không đăng ký pointcut và chỉ bao lời gọi qua chuỗi `Step` do `assemble` tạo. Không áp dụng kết quả này cho mọi method-call/execution join point.

## 7. Build order, weaving order và advice precedence phải được cấu hình độc lập

Một hệ thống AOP có thể có cơ chế **thứ tự ưu tiên (precedence)** ở cấp aspect hoặc advice. Nếu có, cơ chế đó thường dùng để giải quyết nhiều advice cùng khớp một join point. Nhưng cần xác minh:

1. Precedence áp dụng cho **các aspect khác nhau** hay cả advice trong **cùng một aspect**?
2. Một thứ tự ưu tiên cao hơn nghĩa là **bao ngoài**, chạy **before sớm hơn**, hay quy tắc khác?
3. Khi một advice `before` và một advice `after` cùng khớp, quan hệ precedence được định nghĩa thế nào?
4. Có hỗ trợ **around** với khả năng `proceed` không? Nếu có, advice ngoài có thể chặn advice trong.
5. Trường hợp ưu tiên đồng hạng hay chưa khai báo có **thứ tự bảo đảm** không?
6. Nền tảng có coi một danh sách cấu hình là order of **construction** hay order of **execution** không?

Không mặc định:
- Tệp aspect xuất hiện trước trong thư mục sẽ chạy trước.
- Compiler đọc class A trước class B thì A có precedence cao hơn.
- Thứ tự classpath, tên alphabet hoặc thứ tự Reflection là hợp đồng ưu tiên.
- Giá trị priority số nhỏ luôn chạy trước; quy ước số phải đọc theo nền tảng.
- Một `after` luôn dùng cùng thứ tự như `before`.

### 7.1 Trong mô hình lồng nhau, đường vào và đường ra trái chiều

Nếu đã xác lập rõ cấu trúc:

~~~text
A(B(C(Business)))
~~~

thì đường vào trước khi gọi tiếp thường là:

~~~text
A.before → B.before → C.before → Business
~~~

khi kết thúc bình thường:

~~~text
Business → C.after → B.after → A.after
~~~

Đây là hệ quả ngăn xếp của **around wrappers**, không phải tuyên bố mọi loại advice `after` độc lập sẽ luôn có đúng thứ tự đó trong mọi cơ chế.

### 7.2 Thứ tự có thể chỉ là quan hệ bộ phận

Những bất biến quan trọng như `Security` phải chạy trước tác dụng phụ, hoặc `Transaction` cần bao một đoạn nhất định, có thể được diễn tả bằng **quan hệ ưu tiên bắt buộc**. Không phải lúc nào cũng cần sắp xếp tuyến tính mọi aspect.

Ví dụ:

~~~text
Security outside BusinessEffects
Timing may be outside or inside Audit (depending on desired metric)
~~~

Nếu nền tảng không cho xác định một quan hệ cần thiết, không nên dựa vào thứ tự ngẫu nhiên; thay vào đó có thể hợp nhất các bước phụ thuộc chặt vào một advice hoặc ranh giới nghiệp vụ đáng tin.

## 8. Thứ tự và tập join point quan sát được

Một câu hỏi nâng cao là: **weaver thứ hai có nhìn thấy mã mà weaver thứ nhất đã chèn không?**

Ví dụ:

~~~text
P0 = classes gốc

W1(P0) = classes gốc + helper được tạo
W2(W1(P0)) = W2 có thể nhìn thấy helper

W2(P0) = chỉ nhìn thấy classes gốc
W1(W2(P0)) = helper được tạo sau khi W2 hoàn tất
~~~

Trong cơ chế mà `W2` chọn tất cả execution và không loại helper, hai thứ tự **có thể** tạo tập join point khác nhau. Nhưng một số AOP weaver có các quy tắc loại trừ mã sinh, xử lý cặp lời gọi/khai báo theo ngữ nghĩa nguồn hoặc không cho nhiều pass arbitrary như sơ đồ. Không nên suy việc dệt hai lần sẽ luôn thêm advice hai lần.

### 8.1 Call join point và execution join point

- **Call join point** nằm tại vị trí phát sinh lời gọi, với ngữ cảnh caller và chữ ký tại điểm gọi theo mô hình.
- **Execution join point** nằm ở phần chạy thân phương thức được hỗ trợ.

Một biến đổi tạo thêm lời gọi helper có thể thay đổi tập **call sites** trước khi weaver tiếp theo quét; một biến đổi tạo thêm phương thức có thể thay đổi tập **execution candidates**. Cả hai tác động chỉ xảy ra khi mô hình có hỗ trợ và không loại những điểm do mã sinh tạo ra.

### 8.2 Generics, bridge và bytecode sau biên dịch

Trong Java, generic type erasure có thể đổi descriptor so với chữ ký nguồn; compiler đôi khi sinh **bridge methods** để giữ dispatch của override. Nếu một công cụ xử lý bytecode **sau biên dịch**, nó có thể thấy các thành phần này. Nhưng Reflection nhìn thấy bridge không chứng minh AOP sẽ coi bridge là một execution join point riêng.

Cần phân biệt:
- **Kết quả compiler sinh:** sự tồn tại của bridge/synthetic members trong class file.
- **Bộ chọn của weaver:** thành phần nào được công nhận là join point và pointcut nào match.
- **Thứ tự biến đổi:** thành phần nào được tạo/đổi trước khi bộ chọn chạy.

Không được chỉ nhìn số phương thức trong bytecode rồi kết luận số lần advice thực thi.

## 9. Xây dựng từng phần và kết quả dệt không đồng nhất

**Incremental build** chỉ biên dịch hoặc dệt lại những phần được đánh dấu là thay đổi. Điều này tăng tốc nhưng đặt ra trách nhiệm quản lý phụ thuộc:

- Nếu một **aspect thay đổi**, những lớp nào đang chịu pointcut của nó cần được weave lại?
- Nếu một **class thêm phương thức** mới thuộc phạm vi pointcut, build có nhận ra để cập nhật kết quả dệt không?
- Nếu **một annotation hoặc signature** đổi, các match cũ còn hợp lệ không?
- Nếu đổi cấu hình **precedence**, có cần tái tạo artifact chứa advice của nhiều aspect không?
- Nếu tool đã biến đổi một class, lần sau nó đọc **class gốc** hay **class đã dệt**?
- Nếu có cache build, key cache có bao gồm **phiên bản weaver, cấu hình và toàn bộ khai báo aspect liên quan** không?

Một incremental pipeline quản lý phụ thuộc không đầy đủ có thể tạo **artifact cũ**: một lớp còn mang thứ tự advice của bản build trước, trong khi lớp khác được weave theo cấu hình mới. Chương trình có thể vẫn chạy, khiến lỗi chỉ xuất hiện tại một số đường gọi.

**Không suy rằng “chạy lại build một lần” luôn làm sạch trạng thái.** Cần cơ chế quản lý artifact trung gian, dependency graph và invalidation phù hợp; khi nghi ngờ, đối chiếu với một **clean build** từ cùng nguồn và cấu hình.

### 9.1 Dệt lặp và vấn đề idempotency

Nếu một phép biến đổi `W` thêm instrumentation, không được mặc định:

~~~text
W(W(P)) == W(P)
~~~

Một số cách triển khai có thể thêm code lặp khi chạy hai lần, dẫn đến log hoặc metric bị đếm đôi; một số weaver có cơ chế đánh dấu, từ chối hoặc bảo đảm dệt lặp an toàn theo phạm vi hỗ trợ.

Quy trình phải xác định rõ:
- Nguồn đầu vào của mỗi pass có phải artifact **chưa dệt** hay không.
- Công cụ có hỗ trợ reweaving không, và với giới hạn nào.
- Có dấu vết/metadata để nhận biết artifact đã được weave không.
- Làm sao kiểm thử để phát hiện advice lặp, thay đổi thứ tự hoặc bỏ sót.

Không nên thử “dệt lại để chắc chắn” lên artifact đang dùng mà không biết chính sách hỗ trợ của weaver.

### 9.2 Lớp được tạo sau bước dệt

Nếu bytecode generator hoặc trình sinh mã tạo thêm class **sau** khi một weaver compile-time đã chạy, class mới có thể không được weave trong pass đó. Ngược lại, một lớp được tạo **trước** có thể nằm trong tập ứng viên nếu cơ chế quét thấy nó.

Với mô hình load-time, các lớp được tạo và nạp sau có thể chịu biến đổi theo cấu hình đang hoạt động. Nhưng điều này không tự bảo đảm các lớp do nhiều class loader tạo đều được xử lý giống nhau.

## 10. Tái lập kết quả dệt (reproducibility)

Để so sánh hai bản build, phải kiểm soát **đầu vào đầy đủ**, không chỉ repository commit:

| Yếu tố | Vì sao ảnh hưởng |
| --- | --- |
| Phiên bản compiler/JDK | Bytecode, synthetic/bridge methods và một số metadata có thể khác |
| Phiên bản weaver | Bộ chọn điểm, mã phát sinh và lỗi đã sửa có thể khác |
| Khai báo aspect/pointcut | Quyết định tập join point và advice |
| Quy tắc precedence | Quyết định hành vi khi nhiều advice cùng khớp |
| Classpath/module path | Kiểu, lớp, thư viện và phụ thuộc hiện diện khi build |
| Thứ tự pipeline | Biến đổi nào xem biểu diễn trước/sau |
| Generated sources/classes | Những thành phần nào tồn tại trước pass weaving |
| Build cache/incremental state | Artifact có được tái tạo đúng sau thay đổi không |
| Runtime agent/class loader | Với load-time weaving, cấu hình tải lớp có ảnh hưởng |
| Tham số build/profile | Điều kiện bật/tắt instrumentation và môi trường áp dụng |

Một quá trình kiểm chứng có thể:
1. Ghi lại phiên bản công cụ và cấu hình precedence.
2. Clean build từ nguồn xác định.
3. Xuất **báo cáo join point được chọn** hoặc log weaving nếu công cụ hỗ trợ.
4. Chạy kiểm thử thứ tự advice, kết quả và ngoại lệ.
5. So sánh với incremental build từ cùng nguồn.
6. Kiểm tra cả artifact lớp cụ thể và đường gọi thực tế, không chỉ trạng thái “BUILD SUCCESS”.

### 10.1 Mã băm artifact có ý nghĩa gì?

Hash artifact giúp xác định hai tệp **có giống byte** hay không. Nếu hash khác, cần phân tích xem vì timestamp/metadata hay khác code. Nếu hash giống nhau, hai artifact đó đồng nhất byte; nhưng kết quả runtime còn có thể phụ thuộc **agent, classpath, cấu hình và môi trường nạp lớp**.

Hash **không thay thế** kiểm thử hành vi, nhất là với weaving khi nạp lớp hoặc proxy tạo runtime.

## 11. Xung đột giữa nhiều aspect và nhiều công cụ instrument

Không chỉ nhiều advice, một ứng dụng có thể có **nhiều trình biến đổi mã**: weaver AOP, bộ theo dõi coverage, profiler, công cụ theo dõi tracing hoặc bytecode enhancement.

Các công cụ có thể:
- Chèn phương thức hoặc field trợ giúp.
- Sửa lời gọi hoặc bytecode điều khiển ngoại lệ.
- Tạo try/finally và xử lý stack/metadata.
- Sinh proxy hoặc điều chỉnh đường dispatch.
- Yêu cầu đầu vào đúng một dạng class file nhất định.

Nếu chạy hai công cụ theo thứ tự khác, một công cụ có thể quét **mã đã instrument** thay vì mã nguồn ban đầu. Có thể xuất hiện log lặp, chi phí tăng, lỗi verification, điểm không còn khớp hoặc mất metadata.

**Cách phòng tránh:**
- Đọc hợp đồng tương thích và thứ tự áp dụng mà **chính các công cụ** công bố.
- Chỉ dùng pipeline có thứ tự rõ ràng, được kiểm thử trên phiên bản cụ thể.
- Tránh dùng cấu hình ngầm hoặc thứ tự tác nhân runtime không được bảo đảm.
- Theo dõi danh tính artifact đầu vào/đầu ra mỗi pass.
- Thử nhánh lỗi, dọn tài nguyên và concurrency, không chỉ “phương thức vẫn trả đúng chuỗi”.

Không phải cặp biến đổi nào cũng gây xung đột; mục tiêu là **nhận diện những tương tác có thể làm thay đổi tập điểm hoặc ngữ nghĩa**.

## 12. Những hệ quả về bảo mật và transaction

### 12.1 Bảo mật cần ranh giới không bị bỏ qua

Giả sử cấu trúc:

~~~text
Cache(Authorize(Business))
~~~

Nếu cache nằm ngoài và có quyền trả sớm khi hit, `Authorize` bên trong **không chạy**. Nếu yêu cầu nào cũng phải kiểm tra quyền, đây là lỗi kiến trúc dù từng advice được viết đúng.

Cấu trúc:

~~~text
Authorize(Cache(Business))
~~~

đặt kiểm tra quyền trước nhánh cache; nhưng vẫn phải đảm bảo mọi lối vào cần bảo vệ đi qua lớp ngoài và chính sách được đánh giá trên thông tin đáng tin.

Khi build/weaving thay thứ tự bọc, bất biến “kiểm tra quyền trước khi tiết lộ dữ liệu” có thể bị phá mà không có lỗi biên dịch.

### 12.2 Transaction và retry

Hai thứ tự:

~~~text
Retry(Transaction(Business))
Transaction(Retry(Business))
~~~

có thể tạo ngữ nghĩa khác nhau:

- Retry ở ngoài có thể tạo **giao dịch riêng cho từng lần thử**, nếu cách xây dựng transaction thực sự mở/đóng giao dịch quanh mỗi `proceed`.
- Transaction ở ngoài có thể giữ **một ranh giới giao dịch** cho nhiều lần thử, làm các lỗi trước ảnh hưởng trạng thái những lần sau.

Không được kết luận chỉ từ tên advice rằng một lần retry sẽ luôn tạo giao dịch mới; điều đó còn phụ thuộc API transaction và cách advice tổ chức tài nguyên thật. Với thao tác có tác dụng phụ, cần tính đến idempotency và chính sách rollback.

### 12.3 Audit và kết quả dệt

Một audit advice nằm trong lớp có thể short-circuit có thể không thấy các yêu cầu bị trả sớm. Một audit nằm ở ngoài có thể ghi được mọi yêu cầu tới lớp ngoài, nhưng vẫn phải giới hạn dữ liệu nhạy cảm được thu thập.

“Có audit aspect” không đủ chứng minh tất cả sự kiện phải kiểm toán đều được ghi. Cần biết **nơi dệt và vị trí advice trong chuỗi**.

## 13. Kiểm thử kiến trúc thay vì chỉ kiểm thử build thành công

Một ma trận tối thiểu:

| Kịch bản | Điều cần chứng minh |
| --- | --- |
| Hai advice cùng khớp một execution join point | Thứ tự vào/ra theo precedence đã công bố |
| Đổi thứ tự liệt kê wrapper | Thay đổi kết quả đúng theo thuật toán ghép, hoặc không đổi nếu có quy tắc thứ tự rõ |
| Thêm class/overload mới | Pointcut match đúng tập điểm, không bỏ sót/thừa |
| Aspect đổi pointcut | Incremental build invalidates artifact chịu tác động |
| Clean vs incremental build | Cấu trúc advice và hành vi tương đương |
| Chạy lại weaver trên artifact đã dệt | Không sinh đếm trùng ngoài hợp đồng hỗ trợ |
| Lỗi trong business | Advice error/finally và ngoại lệ gốc đúng thứ tự |
| Around short-circuit | Advice bên trong không chạy; invariants vẫn được bảo vệ |
| Lời gọi qua proxy và lời gọi trực tiếp | Biết đường nào được instrument |
| Class được nạp trước/sau tác nhân weaving | Phạm vi load-time khớp cấu hình thực tế |
| Nhiều công cụ transform cùng class | Không mất metadata, sai stack hoặc đổi hành vi ngoài ý muốn |
| Bảo mật/giao dịch/audit | Những ràng buộc bắt buộc vẫn đúng với thứ tự hiệu lực |

### 13.1 Dùng trace để kiểm tra thứ tự

Có thể thu thập một danh sách sự kiện tuần tự **trong cùng một phép thử đồng bộ**:

~~~text
[
    "Security.before",
    "Transaction.before",
    "Business",
    "Transaction.after",
    "Security.after"
]
~~~

Đó là một **expected trace của cấu trúc lồng nhau đã định nghĩa**, không phải thứ tự mặc định của mọi hệ thống AOP. Khi có nhiều thread hoặc hệ thống log bất đồng bộ, thứ tự dòng log xuất hiện ở nơi thu thập có thể không phản ánh toàn bộ quan hệ thực thi; nên dùng trace/correlation và các assertion đúng phạm vi.

### 13.2 So sánh artifact và hành vi

Nếu được, nên có hai nhóm kiểm chứng:

- **Kiểm chứng cấu trúc:** danh sách join point/advice được dệt, loại mã phát sinh và vị trí áp dụng; có thể dùng báo cáo weaver, inspection bytecode hoặc metadata do công cụ công bố.
- **Kiểm chứng hành vi:** test các đường vào, đường ra, ngoại lệ, short-circuit, retry và tác dụng phụ.

Một ảnh chụp mã cuối hoặc một bài test log đơn lẻ không đủ chứng minh toàn bộ chính sách an ninh vẫn hoạt động.

## 14. Các lỗi hiểu sai phổ biến

1. **“Xây dựng A trước B nên A chạy before trước B.”** Không nhất thiết; precedence và thuật toán kết hợp quyết định thứ tự có hiệu lực.
2. **“Thêm wrapper trước nghĩa là wrapper ở ngoài.”** Sai với vòng lặp `chain = Wrap(next, chain)` như ví dụ; phần thêm sau ở ngoài.
3. **“Dệt ở compile time luôn tốt hơn dệt lúc chạy.”** Mỗi thời điểm có ưu/nhược về tập join point, tính linh hoạt, hiệu năng và vận hành.
4. **“Hai phép biến đổi có thể đổi chỗ mà không ảnh hưởng.”** Chưa chắc: chúng có thể không giao hoán và có thể thay đổi tập join point.
5. **“Có bridge method thì chắc chắn thêm execution advice.”** Sai nếu mô hình loại bridge hoặc định nghĩa join point khác.
6. **“Clean build và incremental build thành công là tương đương.”** Chỉ khi invalidation và đầu vào đã được quản lý đúng; cần đối chiếu.
7. **“Dệt một lần nữa chắc không thay đổi gì.”** Không có bảo đảm idempotency chung.
8. **“Đổi thứ tự build làm đổi ưu tiên advice trong mọi công cụ.”** Sai; nhiều công cụ xác định precedence độc lập với thứ tự build.
9. **“Cùng một tệp class nghĩa là runtime weaving/proxy chắc chắn giống nhau.”** Runtime agent, class loader và cấu hình có thể thay đổi lớp can thiệp.
10. **“Kết quả dệt chỉ cần so hash.”** Hash khác chưa chắc sai hành vi; hash giống chưa chắc bao phủ điều kiện runtime khác.
11. **“Thứ tự after giống before.”** Trong wrapper lồng nhau, phần thoát thường ngược chiều phần vào.
12. **“Mọi advice đều chạy vì đều match pointcut.”** Advice bao ngoài có thể chặn không gọi tiếp, hoặc đường gọi không được weave.
13. **“Thêm aspect chỉ ảnh hưởng nó.”** Aspect mới có thể thay thứ tự, ranh giới transaction hoặc trạng thái ngoại lệ các lớp hiện có.
14. **“Dùng tên class được sinh làm quy tắc build vĩnh viễn.”** Generated class, synthetic name và thứ tự compiler có thể thay giữa công cụ/phiên bản.

## 15. Checklist thiết kế pipeline weaving

Khi đưa AOP vào build hoặc thay đổi kiến trúc dệt, nên ghi lại:

- [ ] **Loại join point** thật sự cần: call, execution hay loại được công cụ hỗ trợ.
- [ ] **Thời điểm weaving:** nguồn, compile-time, post-compile, load-time, runtime proxy hay nhiều bước.
- [ ] **Input của mỗi bước**: mã nguồn, class chưa dệt hay class đã instrument?
- [ ] **Thứ tự các phép biến đổi:** công cụ nào chạy trước, do đâu bảo đảm?
- [ ] **Quy tắc precedence:** aspect/advice nào bao ngoài, quy tắc tie-break và trường hợp đồng mức.
- [ ] **Mã sinh và generic:** bridge/synthetic/generated methods có được xem xét không?
- [ ] **Build dependencies:** đổi aspect/pointcut/precedence có làm những lớp liên quan được weave lại không?
- [ ] **Tái dệt:** cơ chế hỗ trợ reweaving hay có nguy cơ chèn lặp?
- [ ] **Tái lập:** phiên bản JDK, weaver, classpath, config và cache key có được cố định không?
- [ ] **Bất biến nghiệp vụ:** auth, transaction, audit, retry, cleanup có còn đúng khi thứ tự đổi?
- [ ] **QA:** có test thứ tự trước/sau, success/error, short-circuit và direct/proxy paths không?
- [ ] **Quan sát:** có bằng chứng từ artifact hoặc report weaving cùng trace runtime không?

## 16. Bài tập suy luận

### Bài 1: Builder bọc lần lượt

Cho:

~~~text
pipeline = Business
pipeline = Wrap(Log, pipeline)
pipeline = Wrap(Security, pipeline)
~~~

**Hỏi:** lớp nào chạy before trước?

**Đáp:** `Security` ở ngoài `Log`, nên trong mô hình wrapper thông thường: `Security.before → Log.before → Business`. Khi thành công, hậu xử lý theo đường đi ra thường là `Log.after → Security.after`.

### Bài 2: Dệt hai pass

`W1` sinh thêm method `helper()`; `W2` chọn method execution theo tên/phạm vi. Đổi từ `W2(W1(P))` sang `W1(W2(P))` có chắc giữ cùng tập điểm?

**Đáp:** không. `W2` có thể thấy `helper` ở thứ tự đầu và không thấy ở thứ tự sau. Nhưng kết quả cuối còn phụ thuộc quy tắc hỗ trợ join point/mã sinh của `W2`; không tự khẳng định mọi helper sẽ được weave.

### Bài 3: Precedence và thứ tự tệp

Một chương trình có aspect Audit và Timer. Tên tệp Audit đứng trước Timer trong thư mục. Có đủ để xác định Audit chạy trước Timer không?

**Đáp:** không. Thứ tự tệp/thư mục không phải hợp đồng precedence trừ khi công cụ đã quy định tường minh, điều thường không nên dựa vào để thiết kế.

### Bài 4: Cùng code nhưng build khác

Clean build chạy đúng, incremental build sau khi đổi pointcut lại thiếu advice tại một lớp. Khả năng nào cần kiểm tra?

**Đáp:** invalidation của class chịu pointcut, cache key, artifact đã dệt cũ, thứ tự build, danh sách generated classes và cấu hình weaver có được đưa vào quyết định build lại không.

### Bài 5: Thay đổi thứ tự thành lỗi bảo mật

Một cache được đưa từ trong ra ngoài lớp authorization. Cache hit không gọi tiếp. Có thể có vấn đề gì?

**Đáp:** authorization nằm trong cache có thể không chạy khi hit, khiến người không đủ quyền nhận dữ liệu nếu không có rào chắn tương đương. Phải xác định lại precedence và bảo đảm kiểm tra quyền trước trả dữ liệu.

### Bài 6: Lời gọi tiếp nhiều lần

Một advice Retry bao ngoài Transaction và gọi continuation ba lần. Có thể nói luôn rằng ba transaction được tạo không?

**Đáp:** chỉ khi cách kết hợp thật sự cho phép mỗi lần tiếp tục đi qua vùng tạo transaction mới và runtime cung cấp ngữ nghĩa tương ứng. Không suy từ tên advice; cần test phạm vi transaction.

## 17. Kết luận

**Thứ tự xây dựng** xác định các bước tạo và biến đổi chương trình; **thứ tự dệt** xác định phép biến đổi nào nhìn thấy biểu diễn nào; **precedence** xác định các advice được kết hợp ra sao; còn **thứ tự chạy** phản ánh chuỗi có hiệu lực lúc chương trình thực thi.

Bốn thứ tự có liên quan nhưng không đồng nhất. Nếu phép biến đổi **không giao hoán**, đổi thứ tự có thể làm khác mã cuối, tập join point hoặc kết quả trả về. Nếu cơ chế AOP định nghĩa precedence và pipeline ổn định, việc đảo thứ tự xử lý tệp **có thể không ảnh hưởng** tới hành vi. Điều cần kiểm chứng là **artifact, tập điểm được dệt và các bất biến runtime**, nhất là khi có nhiều aspect, nhiều weaver, build tăng dần hoặc proxy/runtime weaving.

**Ghi nhớ:** Đừng đoán thứ tự advice từ tên file, thứ tự compile hoặc thứ tự khai báo. Hãy đọc hợp đồng của cơ chế weaving, mô tả rõ thuật toán kết hợp, kiểm thử đường thành công/lỗi và so sánh clean/incremental build để đảm bảo kết quả dệt đúng ý định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-007">Cấu hình phạm vi được dệt và phạm vi bị loại trừ</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình phạm vi được dệt và phạm vi bị loại trừ

## 1. Vì sao cấu hình weaving scope là quyết định kiến trúc?

Trong **Aspect-Oriented Programming (AOP)**, **weaving** là quá trình kết hợp hành vi của aspect/advice với mã chương trình tại những điểm mà một cơ chế hỗ trợ. Có nhiều cách hiện thực: biến đổi mã nguồn hoặc biểu diễn trung gian khi build, biến đổi bytecode tại lúc biên dịch hoặc nạp lớp, runtime instrumentation, hoặc một hình thức interception khác. Không phải mọi hệ thống AOP đều biến đổi bytecode; những kỹ thuật proxy/wrapper cũng có phạm vi quan sát riêng.

Trong một ứng dụng có hàng nghìn class, thông thường ta **không muốn tất cả đều là đối tượng bị weaving**. Những module chứa business operation có thể cần logging, tracing hoặc policy kỹ thuật; trong khi thư viện framework, infrastructure, mã instrumentation, generated code, class loader, security internals hay thư viện bên thứ ba có thể cần tránh biến đổi hoặc ít nhất cần được xem xét rất thận trọng.

Câu hỏi cốt lõi:

> **Mã nào được phép trở thành ứng viên cho weaving, mã nào nhất định bị loại, và làm sao chứng minh artifact đang chạy thực sự tuân thủ chính sách đó?**

Cấu hình không chỉ là tối ưu hóa. Nó ảnh hưởng đến:

- **Tính đúng đắn:** advice có xuất hiện ở đường xử lý cần thiết không?
- **Tính an toàn:** mã quan trọng có nguy cơ bị bỏ qua hoặc bị biến đổi ngoài chủ đích không?
- **Hiệu năng:** công cụ cần quét/biến đổi bao nhiêu class, và advice chạy bao nhiêu lần?
- **Độ ổn định:** có xung đột với mã đã biến đổi, generated code, runtime hoặc classloader khác không?
- **Khả năng bảo trì:** thêm module/class mới có tự động rơi vào scope hay không?
- **Khả năng kiểm chứng:** có log/list/report về các class được weave, không được weave và các join point được chọn không?

### Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **weaving scope**, **pointcut scope**, **advice applicability** và **runtime call path**.
2. Mô tả các tiêu chí **include**, **exclude**, ranh giới module/package/class/loader/artifact và chính sách ưu tiên loại trừ.
3. Lập chính sách phạm vi theo **allowlist rõ ràng, denylist có chủ đích, default behavior** và quy tắc xung đột được kiểm tra.
4. Hiểu ảnh hưởng của build-time, load-time và runtime instrumentation đối với tập class thật sự được biến đổi.
5. Tránh recursion khi aspect vô tình can thiệp chính logger, metric sink hoặc đoạn mã hỗ trợ weaving.
6. Phân tích giới hạn khi có interface, subclass, annotation, generated classes, nested classes, classloader, dependency, module boundaries hoặc mã đã được load.
7. Xây dựng thử nghiệm positive/negative, kiểm tra artifact thực, đọc diagnostic và theo dõi thay đổi scope sau refactor.
8. Chạy ví dụ Java 17 minh họa quy tắc **include/exclude** bằng một proxy nhỏ, đồng thời hiểu vì sao minh họa đó **không phải trình dệt bytecode**.

Nội dung nói về **AOP nói chung**. Không đưa cú pháp riêng của một engine thành chuẩn áp dụng cho mọi công nghệ.

## 2. Bốn tầng quyết định một advice có xuất hiện hay không

Một lời khuyên “pointcut đã đúng thì advice sẽ chạy” là chưa đủ. Ta phải phân biệt bốn tầng.

### 2.1. Tầng 1 — Phạm vi được xét để weaving (*weaving eligibility*)

Công cụ có thể chỉ được cấu hình xét các class trong một module, package, archive, classloader hoặc những artifact được đưa qua bước biến đổi. Một class nằm ngoài scope có thể **không được công cụ biến đổi**, bất kể tên method hay annotation của nó.

Ví dụ giả mã:

```text
include classes under application.orders
exclude classes under application.instrumentation
exclude classes under application.generated
```

Nếu class `vendor.PaymentClient` không thuộc include, các method của nó **không trở thành đối tượng weaving theo cấu hình đó**, ngay cả khi tên `pay` khớp một pointcut rộng.

### 2.2. Tầng 2 — Tập join point mà engine hỗ trợ

Ngay cả khi class được xét, một engine có thể chỉ hỗ trợ **method execution**; một engine khác hỗ trợ cả method call, constructor, field get/set hoặc handler join point. Ví dụ, annotation trên field không khiến interface proxy tự bắt thao tác ghi field.

Scope là điều kiện **có đủ tư cách để được xét**, không phải sự bảo đảm rằng mọi thao tác trong class đều có hook.

### 2.3. Tầng 3 — Pointcut và điều kiện của advice

Trong class nằm trong scope, pointcut có thể chỉ khớp một số method có marker, chữ ký, modifier hoặc runtime context phù hợp. Nhiều class hợp lệ có thể không có join point nào được chọn.

Ví dụ:

```text
eligible class: app.orders.OrderService
pointcut: execution of methods with marker "Observed"

methods:
  createOrder()  [Observed] -> pointcut có thể khớp
  toString()     [không có]  -> không khớp
```

### 2.4. Tầng 4 — Đường chạy thực tế (*runtime occurrence*)

Một class đã weave và pointcut đã chọn vẫn không đồng nghĩa advice phải chạy ngay. Nhánh code có thể không được thực hiện, điều kiện runtime có thể sai, hoặc ở một mô hình proxy lời gọi có thể **đi thẳng vào target**.

Mô hình tư duy:

```text
Tất cả mã / artifact đang có
         |
         v
Lọc include/exclude ở tầng weaving
         |
         v
Những đơn vị đủ điều kiện để biến đổi
         |
         v
Join points thuộc loại công cụ hỗ trợ
         |
         v
Các điểm pointcut/advice thực sự khớp
         |
         v
Mỗi occurrence thực sự xảy ra ở runtime
         |
         v
Advice được kích hoạt theo hợp đồng engine
```

**Không thể dùng log advisor hoặc danh sách class đã weave làm bằng chứng duy nhất rằng từng runtime path đã được bao phủ.** Ngược lại, không thể dùng một test không chạy đến method để kết luận weaving không hoạt động.

## 3. Phân biệt ba loại “scope” dễ bị dùng lẫn

| Loại scope | Ví dụ | Ai quyết định? | Nếu sai có thể xảy ra |
| --- | --- | --- | --- |
| **Weaving scope** | Chỉ biến đổi class trong `app.billing`, bỏ `app.generated`. | Build tool, transformer, agent, classloader/engine config. | Class mong muốn không weave; class không mong muốn bị weave. |
| **Pointcut scope** | Chọn execution của method được marker `@Audited`. | Biểu thức/API matcher của pointcut. | Advice quá rộng hoặc quá hẹp trong vùng eligible. |
| **Runtime/interception scope** | Chỉ lời gọi thông qua object proxy. | Cách lắp object, call path, runtime weaving. | Caller bypass proxy hoặc điểm không bao giờ xảy ra. |

Khi viết tài liệu kỹ thuật, không chỉ nói “scope của aspect”. Nên viết rõ: **“scope được đưa qua weaving”**, **“scope pointcut”** hay **“đường runtime đi qua hook”**.

### 3.1. Phạm vi của chính aspect và phạm vi target không giống nhau

Một cấu hình có thể lựa chọn:

- Những **target classes** có thể bị can thiệp.
- Những **aspect classes** được đăng ký và có advice hoạt động.
- Những **dependency artifacts** được đưa qua bước biến đổi.
- Những **join point kinds** được engine hỗ trợ hoặc bật.

Một aspect được tải/đăng ký **không** có nghĩa tất cả target của nó đã weave. Ngược lại, một class target đủ điều kiện được weave nhưng không có aspect/pointcut nào khớp cũng có thể không phát sinh advice.

### 3.2. Phần “bị loại trừ” có thể nằm ở tầng khác

Một exclude ở **transformer** có thể chặn công cụ biến đổi class. Một `NOT package(internal)` trong **pointcut** chỉ lọc join point của advice đã khai báo. Kết quả giống nhau trong một ca test cụ thể không có nghĩa hai chính sách tương đương.

Nếu class không được weave thì mọi aspect phụ thuộc vào weaving của engine trên class đó có thể không tác động được. Nếu chỉ loại nó khỏi **một pointcut**, những pointcut khác vẫn có thể chọn class đó.

## 4. Include: tạo danh sách được phép (*allowlist*)

Trong các ứng dụng lớn, chiến lược an toàn thường bắt đầu từ một **positive scope** rõ ràng.

Ví dụ giả mã:

```text
weaving:
  include:
    - app.orders.*
    - app.billing.*
    - shared.applicationapi.*
```

Các dấu `*` chỉ mang tính mô tả. Một công cụ có thể dùng tên package, biểu thức kiểu, pattern của tên file/class, resource path, regular expression hoặc cấu hình khác. Cần xác nhận **kỹ thuật matching và nghĩa của wildcard** trong công cụ thực.

### 4.1. Chọn theo package/namespace

Ưu điểm:

- Ranh giới tương đối dễ đọc và review.
- Mở rộng theo các lớp mới trong cùng miền.
- Dễ tổ chức trách nhiệm giữa team/module.

Rủi ro:

- Refactor đổi package làm class rời scope.
- Một package có thể chứa cả business code và helper nên cần loại trừ thêm.
- Tên package trong source không phải lúc nào trùng với đường resource/descriptor mà transformer nhận.

### 4.2. Chọn theo module hoặc artifact

Trong hệ thống chia module, đôi khi chính sách phù hợp là **chỉ weave module application** mà không weave external dependencies. Cấu hình ở tầng build thường có thể biết artifact nào được xử lý; runtime classloader cũng có thể đóng vai trò ranh giới.

Cần xác định những dependency nào là **nguồn cần can thiệp thực sự**. Nếu một policy phải quan sát lời gọi nằm trong dependency cụ thể, exclude toàn bộ thư viện bên thứ ba có thể làm thiếu độ phủ. Nếu không cần, việc mặc định weave tất cả dependencies tăng nguy cơ xung đột và chi phí kiểm thử.

### 4.3. Chọn theo class hoặc annotation

Một số công cụ cho phép match class theo kiểu, marker hoặc metadata. Chẳng hạn:

```text
include types with annotation "Weavable"
```

Nhưng annotation chỉ là metadata. Nó phải được đọc ở thời điểm thích hợp, ở declaration thích hợp, và công cụ phải hỗ trợ tiêu chí ấy ở **weaving scope** chứ không chỉ ở pointcut.

Nếu một annotation chỉ có `SOURCE` retention, runtime reflection không thể đọc nó như marker `RUNTIME`. Một công cụ phân tích source trước compile có thể dùng nó; công cụ chỉ đọc runtime class có thể không.

## 5. Exclude: loại trừ có chủ đích (*denylist*)

Một cấu hình include có thể bao trùm nhiều class hợp lý nhưng cần loại trừ những vùng có rủi ro.

Ví dụ:

```text
weaving:
  include:
    - app.*
  exclude:
    - app.instrumentation.*
    - app.generated.*
    - app.bootstrap.*
```

Một chính sách thực tế phải nói rõ:

- **Exclude được áp trước hay sau include?**
- Nếu một class khớp cả hai thì **rule nào thắng**?
- Nếu không khớp include nào thì mặc định gì?
- Có khác nhau giữa exclude ở **build tool**, **classloader**, **transformer** và **pointcut** không?
- Có hỗ trợ nested classes hoặc generated methods không?
- Một rule trong module cha có thể bị module con override không?

Không có mặc định **“deny luôn thắng”** áp dụng cho mọi công cụ. Tuy nhiên, bạn có thể chọn **deny-overrides** làm **chính sách thiết kế nội bộ**, rồi bảo đảm engine/config hiện thực đúng theo tài liệu và test.

### 5.1. Những nhóm thường cân nhắc loại trừ

| Nhóm | Vì sao cần xem xét | Có bắt buộc luôn exclude? |
| --- | --- | --- |
| Logger, metrics exporter, tracing internals | Tránh advice gọi lại chính công cụ quan sát, gây recursion hoặc overhead không kiểm soát. | Không tuyệt đối; có thể đặt hook khác, nhưng cần tách lớp rõ. |
| Code sinh tự động / synthetic / bridge | Code có thể đổi theo compiler, nhiều method không hiện trên source và có khả năng tạo trùng tín hiệu. | Không; đôi khi generated code là điểm cần đo. |
| Bootstrap / khởi tạo hạ tầng | Biến đổi quá sớm có thể tạo phụ thuộc vòng hoặc lỗi classloading. | Tùy khả năng engine và mục tiêu. |
| Dependencies của bên thứ ba | Dễ gặp xung đột phiên bản/bytecode, scope quá rộng. | Không; có ca cần instrument dependency. |
| Proxy/helper do công cụ sinh | Tránh weave chồng hoặc recursion ngoài chủ đích. | Phụ thuộc engine và cách tạo helper. |
| Test fixtures | Có thể làm test phức tạp và kém ổn định. | Tùy việc muốn test weaving thật hay logic thuần. |
| Thành phần bí mật/nhạy cảm | Advice log args/results có thể làm lộ dữ liệu. | Không nên dựa vào exclude thay cho data minimization/security policy. |
| Package cực kỳ nóng | Advice có thể tăng overhead nhiều lần. | Cần benchmark; có thể giảm pointcut thay vì exclude toàn bộ. |

**Không tự động loại trừ mọi thành phần “internal” nếu những thao tác đó là yêu cầu audit hoặc bảo mật bắt buộc.** Exclude là quyết định phạm vi, cần có lý do và người chịu trách nhiệm.

### 5.2. Exclude để ngăn vòng lặp recursion

Giả sử advice thực hiện:

```text
businessMethod()
  -> advice before
      -> metrics.record()
          -> advice before
              -> metrics.record()
                  -> ...
```

Nếu `metrics.record()` cũng thuộc target weave và được cùng pointcut chọn, hệ thống có thể rơi vào recursion/reentrancy. Một giải pháp là tách module `instrumentation` và loại nó ở tầng weaving hoặc pointcut phù hợp.

Nhưng phải hiểu: **loại helper khỏi weaving** có thể làm giảm các hook khác trên helper. Nếu chỉ muốn ngăn một advice nhất định, exclude trong pointcut của advice đó có thể chính xác hơn. Hãy chọn tầng có **phạm vi tác động nhỏ nhất đủ đạt mục tiêu**.

### 5.3. Exclude không phải “tắt bảo mật”

Nếu một aspect được dùng cho kiểm tra quyền truy cập, thêm exclude cho một package mới có thể vô tình tạo đường bypass. Chính sách authorization quan trọng phải có biên tin cậy và invariant đáng tin, không chỉ dựa vào một transformer được cấu hình đúng. Mỗi thay đổi exclude cần qua đánh giá ảnh hưởng đến bảo mật và những điểm cần audit.

## 6. Thứ tự và xung đột giữa include/exclude

Xét các rule:

```text
include app.*
exclude app.internal.*
include app.internal.reports.*
```

Một class `app.internal.reports.MonthlyReport` khớp **cả ba**. Kết quả là gì? Không thể trả lời nếu thiếu hợp đồng về rule precedence của công cụ.

Có ít nhất ba mô hình có thể được định nghĩa:

1. **Deny-overrides:** bất kỳ exclude nào khớp thì loại, bất kể include khác.
2. **Last-match wins:** rule cuối cùng khớp quyết định.
3. **Specificity/order-based:** rule chi tiết hơn hoặc độ ưu tiên được khai báo quyết định.

Những mô hình này tạo kết quả khác nhau. Không đoán chỉ từ thứ tự dòng YAML/JSON hoặc tên pattern. Phải đọc hợp đồng hoặc chạy test cho chính công cụ đã chọn.

### 6.1. Một chính sách dễ kiểm chứng

Nếu có quyền thiết kế lớp cấu hình, có thể định nghĩa:

```text
eligible(type) =
    matchesAtLeastOneInclude(type)
    AND NOT matchesAnyExclude(type)
```

Tức **allowlist + deny-overrides**. Với một type không khớp include nào, `eligible=false`. Đây là **chính sách trong bài học**, không khẳng định mọi engine dùng cùng công thức.

| Khớp include? | Khớp exclude? | Eligible theo policy trên |
| --- | --- | --- |
| Không | Không | Không |
| Không | Có | Không |
| Có | Không | **Có** |
| Có | Có | **Không** |

Bảng này nên trở thành một unit test cho lớp chọn scope. Nếu một công cụ dùng mô hình khác, điều chỉnh cấu hình hoặc wrapper để đạt chính sách mong muốn và ghi lại khác biệt.

### 6.2. “Tập eligible” chưa bằng “tập advice chạy”

Giả sử class `app.billing.InvoiceService` eligible. Nếu pointcut chỉ chọn method có `@Tracked` mà `invoicePreview()` không có marker, method ấy không được advice xử lý. Ngược lại, `app.internal.SyncService` có marker nhưng exclude ở tầng weaving thì advice dùng cơ chế đó không được cài trên class ấy.

Tách hai bài test:

- **Scope test:** class có được phép đi qua weaving không?
- **Pointcut/runtime test:** join point trên class đủ điều kiện có thực sự được chọn/kích hoạt khi chạy không?

## 7. Cấu hình mẫu ở mức kiến trúc (không gắn với engine)

Giả sử một hệ thống có module:

```text
app/
  orders/
  billing/
  internal/
    monitoring/
    bootstrap/
  generated/
  cli/
external/
  paymentclient/
  serializer/
```

Mục tiêu: weave application business code ở `orders` và `billing`; không weave nội bộ monitoring, generated classes hoặc dependencies bên ngoài. Mẫu **giả mã cấu hình**:

```yaml
weaving:
  eligible:
    include:
      - "app.orders.**"
      - "app.billing.**"
    exclude:
      - "app.internal.**"
      - "app.generated.**"
  policy:
    default: deny
    conflict: deny-overrides
```

Đây **không phải file cấu hình dùng trực tiếp** của một công cụ; khóa `eligible`, `policy`, `conflict` và wildcard chỉ giúp mô tả yêu cầu. Engine có thể yêu cầu property, XML, Java API, build-plugin DSL, regex, package descriptor hoặc trình biến đổi riêng.

### 7.1. Cần tách cấu hình người viết và cấu hình engine hiểu

Khi dịch policy sang config cụ thể, kiểm tra:

- Engine dùng **tên class đầy đủ** hay đường resource?
- Wildcard `*` có khớp dấu chấm/package delimiter không? `**` được hiểu thế nào?
- Rule xét interface, implementation hay cả hai?
- Có include/exclude ở tầng **aspect registration** riêng không?
- Có thể cấu hình từng **archive/module** ngoài từng class không?
- Rule từ file cấu hình và rule truyền qua command line/agent có độ ưu tiên ra sao?
- Cấu hình bị khóa lúc build hay còn có thể đổi ở runtime?
- Thông báo “matched” đang nói scope class hay pointcut advice?

Một config nhìn hợp lý nhưng regex/wildcard khác kỳ vọng có thể bỏ qua hàng loạt class. Hãy xây **bảng các tên class mẫu và kết quả dự kiến** trước khi tin rằng wildcard đã đúng.

## 8. Build-time, load-time và runtime: scope được áp ở đâu?

### 8.1. Weaving lúc build hoặc compile

Một công cụ có thể biến đổi source/bytecode rồi đóng gói artifact. Phạm vi weaving lúc đó phụ thuộc:

- Danh sách file/class được đưa qua bước biến đổi.
- Source set, module, dependency artifacts trong build graph.
- Phiên bản compiler/transformer và thứ tự plugin.
- Incremental build, cache, generated classes hoặc mã từ dependency.
- Việc artifact đang chạy **có đúng là artifact đã được biến đổi**.

**Rủi ro:** thay đổi cấu hình mà không rebuild/redeploy artifact đúng có thể khiến người kiểm thử tưởng exclude đã hoạt động trong khi binary cũ vẫn được dùng.

### 8.2. Weaving lúc nạp lớp (load-time)

Một class transformer/classloader có thể xem xét từng class khi chúng được nạp. Scope lúc đó còn phụ thuộc:

- Loader nào áp transformer và loader nào không.
- Class có được load **trước khi** transformer kích hoạt hay không.
- Module system, khả năng truy cập class và những giới hạn runtime.
- Thứ tự giữa nhiều transformer/agent.
- Class được tạo động hoặc nạp trong loader riêng.

Một pattern include theo tên class có thể đúng nhưng transformer không hề nhìn thấy class của loader khác. Không nên báo “scope khớp” chỉ dựa vào tên package nếu hệ thống dùng nhiều classloader.

### 8.3. Runtime instrumentation/retransformation

Một số cơ chế cho phép instrument/retransform class khi runtime đang hoạt động, nhưng khả năng này **có giới hạn**, phụ thuộc API, định dạng class, kiểu thay đổi và quyền runtime. Không thể mặc định mọi class đã nạp đều có thể biến đổi mọi mặt hoặc chuyển về nguyên trạng chỉ bằng đổi cấu hình.

Nếu update exclude trong lúc đang chạy, câu hỏi quan trọng là:

- **Những class trước đây đã weave có tự được hoàn nguyên không?**
- **Class mới load có dùng policy mới không?**
- **Những invocation đang chạy bị ảnh hưởng thế nào?**

Câu trả lời không có giá trị chung cho mọi engine. Với cấu hình production, nên xem thay đổi weaving scope như **thay đổi artifact/runtime behavior cần rollout và rollback plan**, không phải thay một flag vô hại.

### 8.4. Proxy/wrapper: scope nằm ở nơi đối tượng được bọc

Một interface proxy có thể quyết định tạo wrapper cho một target hay trả target nguyên gốc. Điều này minh họa allowlist/exclude ở một tầng, nhưng **không phải bytecode weaving**. Một method của class được bọc có thể vẫn được gọi trực tiếp qua reference target ban đầu.

Trong thiết kế proxy, cần kiểm tra:

- Ai tạo target và ai đưa proxy cho caller?
- Caller có giữ tham chiếu gốc không?
- Self-invocation có vòng qua proxy không?
- Class/method visibility và interface boundaries có phù hợp không?
- Có hai proxy lồng nhau khiến advice chạy hai lần không?

Không áp nguyên những giới hạn trên cho mọi kiểu weaving source/bytecode.

## 9. Java 17 runnable: mô phỏng bộ lọc scope bằng proxy

Ví dụ dùng **Java chuẩn**, một allowlist/denylist đơn giản và **JDK dynamic proxy**. Nó **không thực hiện source/bytecode weaving**; chỉ minh họa cách cấu hình **eligible scope** quyết định có tạo proxy/interception cho một đối tượng hay không.

Chính sách trong demo:

```text
include: app.*  OR shared.api
exclude: app.internal.* OR app.generated.*
default: deny
conflict: exclude overrides include
```

Tạo tệp **`WeavingScopeDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class WeavingScopeDemo {
    interface Service {
        String run(String id);
    }

    static final class BillingService implements Service {
        @Override
        public String run(String id) {
            System.out.println("TARGET billing " + id);
            return "BILL:" + id;
        }
    }

    static final class HealthService implements Service {
        @Override
        public String run(String id) {
            System.out.println("TARGET health " + id);
            return "HEALTH:" + id;
        }
    }

    static final class VendorService implements Service {
        @Override
        public String run(String id) {
            System.out.println("TARGET vendor " + id);
            return "VENDOR:" + id;
        }
    }

    static final class GeneratedService implements Service {
        @Override
        public String run(String id) {
            System.out.println("TARGET generated " + id);
            return "GEN:" + id;
        }
    }

    static boolean eligible(String module) {
        boolean included =
                module.startsWith("app.")
                || module.equals("shared.api");
        boolean excluded =
                module.startsWith("app.internal.")
                || module.startsWith("app.generated.");
        return included && !excluded;
    }

    static Service instrument(String module, Service target) {
        if (!eligible(module)) {
            System.out.println("CONFIG excluded " + module);
            return target;
        }
        System.out.println("CONFIG included " + module);

        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                (proxy, method, args) -> {
                    System.out.println("ADVICE before " + method.getName());
                    try {
                        Object result = method.invoke(target, args);
                        System.out.println("ADVICE returning " + method.getName());
                        return result;
                    } catch (InvocationTargetException ex) {
                        System.out.println("ADVICE throwing " + method.getName());
                        throw ex.getCause();
                    }
                }
        );
    }

    public static void main(String[] args) {
        BillingService originalBilling = new BillingService();

        Service billing = instrument("app.billing", originalBilling);
        Service health = instrument("app.internal.health",
                new HealthService());
        Service vendor = instrument("vendor.adapter",
                new VendorService());
        Service generated = instrument("app.generated.mapper",
                new GeneratedService());

        System.out.println("RESULT " + billing.run("B1"));
        System.out.println("RESULT " + health.run("H1"));
        System.out.println("RESULT " + vendor.run("V1"));
        System.out.println("RESULT " + generated.run("G1"));

        System.out.println("RESULT " + originalBilling.run("DIRECT"));
        System.out.println("CHECK shared.api=" + eligible("shared.api"));
        System.out.println("CHECK app.internal.x="
                + eligible("app.internal.x"));
    }
}
```

Biên dịch và chạy:

```bash
javac WeavingScopeDemo.java
java WeavingScopeDemo
```

Đầu ra dự kiến:

```text
CONFIG included app.billing
CONFIG excluded app.internal.health
CONFIG excluded vendor.adapter
CONFIG excluded app.generated.mapper
ADVICE before run
TARGET billing B1
ADVICE returning run
RESULT BILL:B1
TARGET health H1
RESULT HEALTH:H1
TARGET vendor V1
RESULT VENDOR:V1
TARGET generated G1
RESULT GEN:G1
TARGET billing DIRECT
RESULT BILL:DIRECT
CHECK shared.api=true
CHECK app.internal.x=false
```

### 9.1. Điều gì được minh họa chính xác?

**`app.billing`:** khớp include, không khớp exclude → proxy được tạo. Gọi `billing.run("B1")` đi qua handler nên có advice.

**`app.internal.health`:** tên cũng bắt đầu `app.` nhưng bị exclude cụ thể. Theo policy **exclude thắng**, `instrument` trả target gốc; lời gọi `health.run` không có advice.

**`vendor.adapter`:** không khớp include, nên bị loại theo chính sách **default deny**, dù không có rule exclude cụ thể.

**`app.generated.mapper`:** thuộc include `app.` nhưng exclude theo `app.generated.`, nên không được bọc.

**`shared.api`:** bộ kiểm tra rule trả `true`; demo không tạo instance cho nhánh này. Nó chỉ chứng minh predicate eligible, không chứng minh có target nào thật sự được proxy.

**`originalBilling.run("DIRECT")`:** dù target từng được bọc trong `billing`, caller vẫn giữ tham chiếu tới target gốc nên gọi trực tiếp bypass proxy. Đây là ranh giới **call path** của JDK proxy, không liên quan đến logic eligible đúng/sai.

### 9.2. Điều mà ví dụ không chứng minh

- Không chứng minh bytecode `BillingService` được weave.
- Không chứng minh constructor, field access hoặc internal method call bị can thiệp.
- Không có pointcut DSL hoặc cấu hình transformer/classloader thực.
- Không chứng minh mọi lời gọi tới một class eligible đều có advice.
- Không tự kiểm tra scope của những class generated bởi compiler.
- Không cung cấp module/classloader isolation hoặc hot reload.
- Không thể dùng stdout này để kết luận một công cụ AOP production đã weave đúng artifact.

**Giá trị giáo dục** của demo nằm ở việc chính sách allowlist/denylist được viết và test độc lập với mechanism, sau đó ta kiểm tra **đường interception** riêng.

### 9.3. Bài thực hành sửa rule

1. Đổi `module.equals("shared.api")` thành `module.startsWith("shared.")` và xem những module nào mới đủ điều kiện.
2. Bỏ điều kiện `app.generated.` khỏi exclude, dự đoán dòng `CONFIG` và `ADVICE` cho GeneratedService.
3. Đổi include thành chỉ `app.billing`, kiểm tra kết quả `app.orders` và `app.internal.x`.
4. Giữ nguyên rule nhưng gọi target `BillingService` trực tiếp, xác nhận chỉ scope đúng **chưa đủ** để advice chạy qua proxy.
5. Thêm một Service ném `IllegalArgumentException`, gọi qua proxy và chứng minh handler không vô tình nuốt exception hoặc bỏ qua phân loại lỗi.

## 10. Include/exclude ở mức type khác với include/exclude join point

Giả sử engine weave class `app.billing.InvoiceService` nhưng chỉ muốn quan sát method `issue`, không phải `preview`.

Có hai khả năng cấu hình kiến trúc:

**A. Weaving include cả type; pointcut lọc method.**

```text
eligible(type) = type under app.billing
pointcut(method) = execution of issue(...)
```

**B. Exclude type hoàn toàn rồi tìm cách include một method.**

Một số hệ thống **không cho phép** pointcut “cứu lại” một method trong class đã bị loại khỏi transformer; class không được biến đổi nghĩa là advice không có nơi để cài. Cách B có thể bất khả thi tùy engine.

Nguyên tắc: **chọn scope class đủ rộng để chứa những điểm cần can thiệp, rồi dùng pointcut hẹp để kiểm soát advice**. Nhưng đừng make scope class rộng đến mức transformer chạm vào quá nhiều dependencies không cần thiết.

### 10.1. Nếu muốn loại đúng một method

Nên ưu tiên **pointcut exclusion** nếu engine có khả năng chọn method chính xác, thay vì exclude cả class khiến những advice khác mất hook.

Ví dụ giả mã:

```text
pointcut:
  execution methods under app.billing
  AND NOT methodName("internalFlush")
```

Đây không phải cú pháp chung. Nếu `internalFlush` vẫn được một aspect khác chọn, nó có thể vẫn có advice của aspect khác, tùy cơ chế.

### 10.2. Nếu muốn loại một dependency hoàn toàn

Nếu mục tiêu là **không biến đổi bytecode của một thư viện** vì ổn định/tương thích, exclude ở weaving scope có thể phù hợp hơn việc yêu cầu từng pointcut phủ định package thư viện. Nhưng cần xác minh không có chức năng quan trọng phụ thuộc hook ở dependency đó.

## 11. Interface, implementation, inheritance và generated code

### 11.1. Nơi tên class được kiểm tra

Khi một class implement interface, một engine có thể match theo **implementation class**, theo interface, theo kiểu tĩnh ở call site hoặc theo runtime receiver, tùy loại join point và matcher. Rule include `app.api.*` không tự bảo đảm weave body của một implementation nằm ở `vendor.impl.*`.

Đối với JDK proxy, proxy triển khai interface và handler được gọi qua proxy; việc gắn rule vào interface không tương đương **biến đổi class implementation**. Đối với bytecode weaving, cần biết class nào thực sự được transformer xử lý.

### 11.2. Kế thừa và method override

Class con có thể kế thừa method từ superclass nằm ở module khác. Nếu transformer chỉ weave class con, method body thật có thể thuộc superclass; kết quả phụ thuộc engine và loại join point. Đừng dựa vào tên class nhận lời gọi mà bỏ qua **nơi code thực sự được định nghĩa**.

Nếu điểm cần theo dõi là execution của method kế thừa, hãy tạo test với superclass/interface ở package khác và xem nơi hook được cài.

### 11.3. Class lồng, anonymous, lambda, bridge và synthetic methods

Compiler có thể tạo class/member phụ không nhìn rõ như source. Trong Java:

- **Nested class** có tên binary khác với tên source thuận mắt.
- **Anonymous/local class** có các tên do compiler sinh.
- **Lambda** có thể được hiện thực bằng cơ chế runtime/compiler đặc thù.
- **Bridge method** có thể xuất hiện do generics/type erasure.
- Một số compiler/code generator tạo các method/synthetic artifacts.

Một wildcard chỉ khớp các class source do người viết tưởng tượng có thể bỏ qua hoặc bao gồm những class đã sinh không mong muốn. Khi mở rộng scope, test phải gồm **tên class thực hoặc bytecode thực trong artifact**, không chỉ tên package ở source tree.

### 11.4. Generated code là một quyết định có mục tiêu

Loại generated code thường giúp tránh noise, nhưng đôi khi thao tác quan trọng (serializer, RPC client, mapper) thực sự chạy trong code sinh. Nếu metric cần đo ở đó, phải xác định một boundary ổn định, có thể là API wrapper hoặc interface, thay vì vô tình exclude rồi cho rằng policy đã bao phủ.

## 12. Classloader, modules và dependencies

### 12.1. Cùng tên class, khác loader

Hai classloader khác nhau có thể định nghĩa các class cùng tên binary nhưng là các **kiểu runtime khác nhau**. Một policy chỉ dùng tên có thể có ý nghĩa khác với policy ràng buộc loader/module. Một transformer gắn với loader A không nhất thiết nhìn thấy class nạp qua loader B.

Khi debug “tên class khớp nhưng không weave”, nên kiểm tra **loader, module, artifact và thời điểm load**, không chỉ pointcut expression.

### 12.2. Runtime module boundaries

Module system có thể giới hạn truy cập kiểu, reflection hoặc instrumentation. Một engine có thể yêu cầu cấu hình mở module hoặc quyền đặc biệt; không được coi việc include module bằng pattern là đủ để vượt qua các ràng buộc runtime. Phải làm theo hợp đồng chính thức của công cụ, tránh tự tắt các cơ chế bảo vệ rộng hơn cần thiết.

### 12.3. Bên thứ ba và thư viện dùng chung

Một thư viện nằm trong nhiều ứng dụng có thể chạy với phiên bản/classloader khác. Nếu weave toàn thư viện, cần kiểm thử tương thích binary, hiệu năng, thứ tự transformer và ảnh hưởng tới mọi consumer. Có thể chọn can thiệp tại **application boundary** thay vì sửa bytecode của dependency nếu mục tiêu chỉ là đo lời gọi từ ứng dụng.

### 12.4. Điều gì xảy ra khi class đã được nạp?

Một số hook chỉ áp lúc class load. Nếu class đã load trước khi transformer được gắn, nó có thể không được weave, trừ khi cơ chế hỗ trợ retransformation và quy trình đó được chạy phù hợp. Không suy rằng đổi config sau startup sẽ tự sửa toàn bộ class đã load.

## 13. Weaving scope và multi-aspect interaction

Một application có nhiều aspect:

- Tracing.
- Metrics.
- Audit.
- Retry.
- Validation kỹ thuật.
- Resource cleanup.

Nếu exclude cả `app.internal.*` ở tầng weaving, **tất cả** aspect dựa trên cùng transformer có thể mất khả năng chọn join point ở vùng đó. Nhưng nếu chỉ thêm điều kiện `NOT internal` vào pointcut của **Metrics**, Tracing hoặc Audit vẫn có thể được áp dụng ở đó.

Vì vậy cần mô tả cấu hình dưới dạng ma trận:

| Thành phần | Eligible để weave? | Tracing? | Metrics? | Audit? |
| --- | --- | --- | --- | --- |
| `app.billing.InvoiceService` | Có | Có thể | Có thể | Có thể |
| `app.internal.metrics.Meter` | Theo chính sách | Có thể bị loại | Nên tránh tự instrument gây recursion | Tùy yêu cầu |
| `app.generated.Mapper` | Theo chính sách | Tùy chọn | Tùy chọn | Tùy yêu cầu |
| `vendor.PaymentClient` | Theo chính sách | Tùy hệ thống | Có thể quan sát từ wrapper bên ngoài | Tùy yêu cầu |

“Có thể” trong bảng không khẳng định advice đang chạy; nó nhắc rằng phải xét **eligible → pointcut → runtime occurrence** cho từng concern.

### 13.1. Nhiều công cụ weaving cùng một class

Trong một runtime, có thể có nhiều transformer hoặc công cụ code instrumentation. Thứ tự chúng chạy có thể ảnh hưởng bytecode đầu vào và khả năng tương thích. Nếu một class bị biến đổi nhiều lần, không thể mặc định mọi biến đổi đều độc lập hoặc commute.

Cần kiểm tra:
- Ownership của từng transformer.
- Thứ tự và tương thích phiên bản.
- Khả năng idempotency khi reweave.
- Cách phát hiện duplicate advice/hook.
- Quy trình rollback/inspect artifact.

Không tự điều chỉnh thứ tự agent trong production nếu chưa có test với artifact và phiên bản thực tế.

## 14. Khác biệt giữa “không weave” và “weave nhưng không chạy advice”

Đây là một bẫy chẩn đoán phổ biến.

**Tình huống A: class không được weave.**

```text
class: app.internal.Helper
scope exclude: match
transformer: không đưa class qua weaving
pointcut: có vẻ khớp method
runtime: không có hook tương ứng từ transformer đó
```

**Tình huống B: class được weave nhưng pointcut không khớp.**

```text
class: app.billing.InvoiceService
scope include: match
transformer: xử lý class
pointcut: chỉ method @Observed
method: preview() không có marker
runtime: không chạy advice trên preview()
```

**Tình huống C: có hook nhưng không có occurrence.**

```text
class eligible, pointcut matched, hook installed
nhánh code cần gọi method không được thực thi
=> không có advice invocation
```

**Tình huống D: proxy eligible nhưng bị bypass.**

```text
proxy được tạo
caller vẫn gọi target gốc
=> handler không chạy
```

Bốn nguyên nhân tạo **cùng triệu chứng “không thấy log advice”** nhưng cách khắc phục khác nhau. Không nên chỉ tăng độ rộng pointcut khi gốc lỗi là classloader hoặc artifact chưa weave.

## 15. Chiến lược xác minh weaving scope

### 15.1. Trước khi chạy: bảng mong đợi

Lập danh sách những type và kết quả mong đợi:

| Type / module | Include? | Exclude? | Kỳ vọng eligible |
| --- | --- | --- | --- |
| `app.orders.OrderService` | Có | Không | Có |
| `app.billing.InvoiceService` | Có | Không | Có |
| `app.internal.metrics.Meter` | Có nếu include `app.*` | Có | Không nếu deny-overrides |
| `app.generated.JsonMapper` | Có nếu include `app.*` | Có | Không nếu deny-overrides |
| `vendor.db.Client` | Không | Không | Không nếu default deny |
| `shared.api.Gateway` | Theo rule | Theo rule | Đọc từ rule cụ thể |

Đây là những **test input**, không phải tên class lấy từ dự án đang thực thi. Dùng tên class thật của ứng dụng khi viết kiểm thử production.

### 15.2. Trong build/load: diagnostic chứng minh transformation

Nhiều công cụ có tùy chọn chẩn đoán, dump/inspect mã đã weave, danh sách class đã xử lý hoặc thông báo các join point được nhận diện. Nếu công cụ thực sự hỗ trợ, có thể kiểm tra:

- File/class nào **đã được transformer nhìn thấy**?
- File/class nào **thực sự bị biến đổi**?
- Bao nhiêu shadow/join point đã được match?
- Những rule include/exclude nào tác động lên kết quả?
- Có lỗi parse, incompatible bytecode hoặc access error không?
- Artifact cuối cùng có chứa kết quả weaving không?

Phải phân biệt **“class được xét”** với **“class có thay đổi bytecode”**. Một class có thể được xem xét nhưng không có join point khớp nên output không thay đổi.

### 15.3. Trong runtime: positive và negative tests

Một bộ test cần cả hai vế:

1. **Positive:** type eligible, method pointcut khớp, caller đi qua cơ chế → advice xuất hiện đúng.
2. **Negative by scope:** type bị exclude → không có hook từ cơ chế đó.
3. **Negative by pointcut:** type eligible nhưng method không khớp → không có advice cụ thể.
4. **Negative by call path:** proxy eligible nhưng gọi target trực tiếp → không có advice của proxy.
5. **Conflict rule:** class match cả include/exclude → hành vi theo precedence đã chọn.
6. **Generated/inherited:** nested class, subclass, method bridge hoặc generated code → kết quả đúng hợp đồng.
7. **Multiple aspect:** một pointcut bị loại không làm mất những advice khác ngoài ý định.
8. **Rebuild/redeploy:** artifact dùng khi test trùng với artifact thực tế deploy.

Kiểm tra “advice có log trong happy path” không chứng minh exclude đúng. Ngược lại, kiểm tra “không có log” cũng không chứng minh class đã bị exclude, vì có thể method chưa hề chạy.

### 15.4. Kiểm thử thay đổi cấu hình

Khi sửa scope, so sánh **trước/sau**:

- Số class đủ điều kiện.
- Danh sách class mới được weave hoặc mới bị loại.
- Số join points được chọn theo từng aspect.
- Tần suất advice ở workload tương đương.
- Thời gian build/startup và runtime overhead.
- Lỗi classload/verification và xung đột với transformer khác.

Nếu phạm vi được quản lý như code, nên review thay đổi wildcard và các trường hợp `include + exclude` như thay đổi API/permission quan trọng.

## 16. Hiệu năng và kích thước tập weaving

Scope quá rộng có thể gây chi phí ở nhiều giai đoạn:

- **Scan/analysis:** duyệt nhiều class hoặc bytecode.
- **Transformation:** xử lý class thực sự cần biến đổi.
- **Build/rebuild:** cache/incremental compilation chịu ảnh hưởng cấu hình.
- **Class load/startup:** transformer có thể tăng thời gian khởi động.
- **Runtime:** những hook được cài có thể tạo overhead khi join point xảy ra.
- **Diagnostics:** nhiều điểm được instrument có thể tạo lượng log/metric lớn.

Nhưng đừng tự khẳng định “cứ exclude nhiều class thì runtime luôn nhanh hơn”. Một số engine tối ưu pointcut tĩnh tốt, có thể không chèn hook khi không khớp; nhiều chi phí nằm ở số **runtime occurrences**, không chỉ số class eligible. Phải đo bằng profile và benchmark trên workload thực.

### 16.1. Tập nhỏ không tự bảo đảm an toàn

Một method hot path chạy hàng triệu lần có thể tốn hơn rất nhiều so với hàng trăm class ít được gọi. Scope filter giúp giảm bề rộng biến đổi; **pointcut hẹp, sampling, log aggregation và hiệu năng advice** giúp giảm chi phí runtime.

### 16.2. Dữ liệu nhạy cảm

Weave một class chứa thao tác bảo mật/credentials rồi advice in toàn bộ arguments là rủi ro riêng tư. Exclude type có thể là một biện pháp phòng vệ nhưng không thay thế redaction và thiết kế telemetry tối thiểu, nhất là khi nhiều type chia sẻ cùng dữ liệu.

## 17. Những lỗi cấu hình thường gặp

**“Pointcut match thì class chắc chắn đã weave.”** Sai. Class có thể nằm ngoài eligible scope hoặc không được transformer nhìn thấy.

**“Include `app.*` nghĩa là include mọi class ở mọi package con.”** Không thể kết luận nếu chưa biết quy tắc wildcard của engine.

**“Exclude một package khỏi một pointcut nghĩa là engine không hề biến đổi class.”** Sai. Đó có thể chỉ là lọc advice, không phải lọc weaving.

**“Một class match cả include/exclude thì exclude luôn thắng.”** Chỉ đúng nếu chính sách engine đã được xác định hoặc lớp cấu hình của bạn hiện thực như vậy.

**“Loại trừ logger chắc chắn loại mọi recursion.”** Không luôn. Có thể còn những helper/callback khác bị pointcut chọn hoặc self-invocation theo cơ chế riêng.

**“Chỉ cấu hình include module ứng dụng nên dependencies sẽ không bị ảnh hưởng.”** Cần xem build classpath, transformer, classloader và cách engine xác định artifact; tên module trong source chưa đủ.

**“Cấu hình mới được deploy là class cũ đã được unweave.”** Không có bảo đảm chung. Có thể cần rebuild/restart/retransform theo khả năng công cụ.

**“Không có log advice chứng minh class bị exclude đúng.”** Sai. Method có thể chưa xảy ra hoặc pointcut không khớp.

**“Thấy aspect được load nghĩa là target đã weave.”** Sai. Đó là hai trạng thái khác nhau.

**“Generated code nên luôn bị loại.”** Không phải nguyên tắc tuyệt đối; đôi khi điểm tích hợp đúng nằm trong generated wrapper hoặc serializer.

**“Weaving scope thay được kiểm tra quyền ở domain boundary.”** Sai khi có đường bypass hoặc chính sách không bao trùm tất cả truy cập quan trọng.

**“Có thể dùng config thật của một công cụ với bất cứ AOP engine nào.”** Không. Những ví dụ include/exclude trong bài là kiến trúc và giả mã, cú pháp cụ thể phải theo công cụ.

## 18. Bài tập và đáp án

### Bài 1 — Rule precedence

Có:

```text
include app.*
exclude app.internal.*
include app.internal.audit.*
```

Nếu **chính sách được công bố** là `included AND NOT excluded`, class `app.internal.audit.Writer` có eligible không?

**Đáp án:** Không. Nó khớp include nhưng cũng khớp exclude; exclude thắng theo policy được định nghĩa. Nếu engine dùng last-match wins thì câu trả lời có thể khác, nên phải xác minh contract.

### Bài 2 — Class eligible nhưng advice không có log

`app.billing.InvoiceService` nằm trong include. Pointcut chỉ chọn method `@Observed`; method `preview` không có marker và được gọi 10 lần. Có thể kết luận weaving sai không?

**Đáp án:** Không. Type eligible không có nghĩa tất cả method được advice chọn. Cần inspect pointcut/matcher và kiểm thử một method có marker.

### Bài 3 — Hai loại exclude

Bạn muốn metrics không chạy trên `MetricsWriter` nhưng audit vẫn chạy. Nên exclude cả class ở weaving scope hay loại riêng pointcut metrics?

**Đáp án:** Thường nên xem xét **loại khỏi pointcut metrics** để không vô tình mất audit; tất nhiên phải xét recursion, khả năng engine và chính sách tách instrumentation thực tế.

### Bài 4 — Direct target call

Một interface proxy được tạo thành công, nhưng caller vẫn giữ tham chiếu gốc. Rule eligible=true. Advice có nhất thiết chạy?

**Đáp án:** Không. Handler proxy chỉ quan sát những lời gọi qua proxy. Eligibility và đường runtime là hai tầng khác nhau.

### Bài 5 — Load-time scope

Class đã được loader B nạp trước khi transformer gắn với loader A. Tên class khớp include. Có thể khẳng định bytecode đã weave không?

**Đáp án:** Không. Phải kiểm tra loader/transformer, thời điểm load và cơ chế retransformation. Include theo tên không đủ.

### Bài 6 — Generated code

Compiler sinh nested class với binary name khác dự đoán từ source. Pattern exclude chỉ dựa vào tên class source. Rủi ro là gì?

**Đáp án:** Có thể chưa loại được class thực tế, hoặc loại nhầm phần khác; cần kiểm thử tên binary/resource và artifact output.

### Bài 7 — Hai aspect

Tracing và audit cùng cần `app.orders.OrderService`. Một cấu hình exclude ở weaving scope vô tình loại class này. Điều gì xảy ra?

**Đáp án:** Nếu cả hai cần cùng hook weaving trên class đó, chúng có thể mất tác động. Phải phân biệt loại một advice với loại cả target khỏi eligible scope.

### Bài 8 — Performance

Include 20 class nhưng một method được gọi một triệu lần/giây. Include 500 class khác rất ít chạy. Từ số class alone có thể khẳng định cấu hình nào nhanh hơn?

**Đáp án:** Không. Cần xem chi phí scan/build/startup và tần suất runtime/advice overhead thực tế.

### Bài 9 — Thay đổi scope khi runtime đang hoạt động

Chỉnh exclude pattern rồi hot-reload config. Những class đã weave có tự quay về code cũ không?

**Đáp án:** Không thể mặc định. Phải tra hợp đồng của cơ chế transform/retransform, classloader và rollout; có thể cần rebuild hoặc restart.

## 19. Checklist triển khai một weaving policy đáng tin

1. **Nêu rõ mục tiêu:** Đang chọn type để weaving hay join point cho một advice cụ thể?
2. **Xác định join-point model:** Method execution/call, field/constructor hay interception theo proxy?
3. **Khai báo include:** Các namespace/module/artifact cần được xem xét; ưu tiên allowlist có chủ đích.
4. **Khai báo exclude:** Instrumentation internals, generated classes hoặc dependencies với lý do rõ ràng.
5. **Quy định default và precedence:** Match cả hai rule thì kết quả gì? Không match include thì sao?
6. **Kiểm tra wildcard:** Regex, package delimiter, nested/binary class name, case sensitivity.
7. **Xác định thời điểm:** Build, load hay runtime, và ảnh hưởng của cache/incremental/redeploy.
8. **Xác định loader/module:** Transformer thật sự thấy những class nào?
9. **Phân biệt aspects và targets:** Có loại nhầm một class cần cho aspect khác không?
10. **Chứng minh bằng artifact:** Có inspection/diagnostics phù hợp chứng minh class nào đã biến đổi?
11. **Chứng minh bằng runtime:** Có positive/negative test cho pointcut và call path?
12. **Bảo vệ correctness:** Thay đổi exclude không tạo bypass cho policy quan trọng.
13. **Kiểm soát hiệu năng:** Giới hạn hot path, dữ liệu log/metric và class processing.
14. **Quản lý thay đổi:** Review diff của eligible set và test khi refactor module/package.
15. **Rollback:** Có cơ chế khôi phục artifact hoặc cấu hình trước nếu xảy ra xung đột weaving?

## Kết luận

**Weaving scope** định nghĩa **những đơn vị mã có khả năng được công cụ xem xét và biến đổi**; **pointcut** định nghĩa **những join point nào được chọn**; còn **runtime call path** quyết định **lượt thực thi nào thật sự đi qua hook**. Ba tầng này liên quan nhưng không thay thế nhau.

Để cấu hình phạm vi được dệt và loại trừ an toàn, hãy chọn include có chủ đích, quy định exclude và precedence rõ, chú ý classloader/module/build artifact, tách loại trừ ở tầng weaving khỏi loại trừ của từng advice, rồi chứng minh bằng test **cả điểm được chọn lẫn điểm bị loại**. Đừng chỉ dựa vào một biểu thức pointcut nhìn hợp lý hoặc một dòng log thành công: chất lượng của kiến trúc weaving nằm ở **độ phủ có thể kiểm chứng, biên giới rõ ràng, sự tương thích của artifact và khả năng thay đổi mà không tạo tác động ngoài dự kiến**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-008">Mã đã dệt, thông tin gỡ lỗi và khả năng truy vết</a>

<details>
<summary>Xem chi tiết</summary>

# Mã đã dệt, thông tin gỡ lỗi và khả năng truy vết trong kiến trúc AOP

## 1. Mục tiêu: nhìn thấy điều gì sau khi weaving?

Trong **Aspect-Oriented Programming (AOP)**, ứng dụng được tổ chức thành mã xử lý nghiệp vụ và những hành vi xuyên suốt (logging, kiểm toán, đo thời gian, chính sách truy cập...). Một **aspect** mô-đun hóa hành vi xuyên suốt; **pointcut** chọn các **join point**; **advice** là mã được áp dụng tại những điểm đó. **Weaving** là quá trình kết hợp các hành vi thành một đường thực thi có hiệu lực.

Khi gỡ lỗi, ta cần phân biệt ít nhất ba hình ảnh của cùng một thao tác:

1. **Mã nguồn người lập trình viết**: phương thức nghiệp vụ và các khai báo aspect/advice.
2. **Mã hoặc đường thực thi đã được kết hợp**: chỉ thị, phương thức cầu nối, interceptor hoặc proxy được công cụ tạo/sử dụng để gọi advice.
3. **Những gì debugger và hệ thống quan sát nhìn thấy khi chạy**: stack frame, vị trí nguồn, sự kiện, ngoại lệ, trace ID, thời lượng và đường gọi thực tế.

Ba hình ảnh đó có thể liên quan nhưng **không phải lúc nào cũng trùng một-một**. Một breakpoint trong mã nghiệp vụ có thể dừng sau khi advice trước đã chạy; stack trace có thể chứa frame trung gian; một phương thức mới sinh có thể không có dòng Java tương ứng; hoặc một bộ dệt có thể thay bytecode ngay khi nạp mà không tạo tệp “mã đã dệt” trên ổ đĩa.

Bài này giúp trả lời:

- “Mã đã dệt” chính xác là gì trong các kiến trúc AOP khác nhau?
- Vì sao breakpoint, số dòng và stack trace đôi khi khó đọc hơn trước weaving?
- Những metadata gỡ lỗi nào hỗ trợ ánh xạ từ runtime về mã nguồn?
- Làm thế nào truy vết được **điểm nào được chọn**, **aspect/advice nào chạy**, **vì sao chúng chạy**, và **kết quả cuối cùng thế nào**?
- Cần kiểm thử và thu thập chứng cứ gì để xác định weaving đúng, không chọn thừa hoặc bỏ sót?

Phạm vi là **kiến thức AOP tổng quát, cơ bản và nâng cao**. Những ví dụ về bytecode, proxy, Java debugger, Java 17 dùng để minh họa cơ chế cụ thể; không được suy rằng mọi công cụ AOP áp dụng cùng một cách weaving hoặc có cùng khả năng ánh xạ nguồn.

## 2. Các thực thể trong câu chuyện gỡ lỗi

| Thành phần | Điều cần biết khi gỡ lỗi | Dữ liệu hữu ích |
| --- | --- | --- |
| **Join point** | Đang can thiệp loại sự kiện nào? | Call, execution, field access... nếu hỗ trợ |
| **Pointcut** | Vì sao điểm đó thuộc tập được chọn? | Biểu thức, quy tắc tĩnh/động |
| **Join-point shadow** | Vị trí mã nào có thể tạo ra join point? | Chữ ký, vị trí, loại điểm |
| **Advice** | Hành vi nào được chèn/thực hiện? | Before, after, around, thứ tự |
| **Weaver** | Việc kết hợp xảy ra ở đâu và khi nào? | Compiler, loader, runtime interception |
| **Woven representation** | Mã/đường chạy hiệu lực khác mã gốc thế nào? | Bytecode, adapter, proxy, handler |
| **Debug information** | Có ánh xạ được instruction về source không? | Source file, line, local variables, mapping |
| **Runtime trace** | Lần gọi cụ thể đã đi qua những bước nào? | Trace ID, join-point ID, event và outcome |
| **Build provenance** | Đang chạy đúng bản mã nào? | Phiên bản, hash, cấu hình weaving |

Cần phân biệt **provenance của mã** (bản build nào, cấu hình nào, lớp nào được biến đổi) với **trace của một lần thực thi** (lời gọi nào xảy ra, trên dữ liệu nào, advice nào chạy). Chỉ có stack trace mà không biết build đang chạy đôi khi không đủ để tìm lỗi.

## 3. “Mã đã dệt” không nhất thiết là một tệp source mới

Thuật ngữ **woven code** thường chỉ **biểu diễn thực thi đã kết hợp hành vi aspect**. Tùy cách triển khai, nó có thể là:

- Một tệp mã nguồn được sinh hoặc biến đổi trước khi biên dịch.
- Một class file/bytecode đã thay đổi so với phần nguồn nghiệp vụ ban đầu.
- Một lớp được biến đổi trong lúc class loader nạp vào runtime.
- Một hoặc nhiều adapter, proxy, dispatcher, wrapper hoặc interceptor tham gia đường gọi.
- Một cấu trúc trong runtime hoặc JIT dùng để thực hiện advice.

Không có quy tắc chung rằng weaving luôn sinh ra `WovenOrderService.java` mà người lập trình có thể mở trong IDE. Ngay cả khi tạo class file, việc lưu bản bytecode trước và sau weaving ra đĩa cũng là một khả năng công cụ hoặc bước build tùy chọn.

### 3.1. Từ mã nguồn đến hành vi có hiệu lực

Ví dụ mã nghiệp vụ:

```java
class PaymentService {
    void charge(long amount) {
        System.out.println("charge:" + amount);
    }
}
```

Đây là đoạn minh họa; không có `main` độc lập.

Một chính sách AOP có thể muốn ghi sự kiện trước và sau method execution của `charge`. Bộ dệt có thể tạo đường chạy khái niệm:

```text
beforeAdvice()
try:
    PaymentService.charge(amount)
finally:
    afterAdvice()
```

**Đây là mã giả về hành vi**, không có nghĩa bytecode được dệt luôn có đúng một khối `try/finally` như trên. Advice sau có thể mang ngữ nghĩa chỉ khi thành công, chỉ khi ném ngoại lệ hoặc khi kết thúc; cách biến đổi phải tương ứng đúng loại advice.

### 3.2. Woven representation và advice logic không đồng nhất

Một bytecode biến đổi có thể chỉ chứa lời gọi tới một hàm dispatch/advice được đặt ở nơi khác. Một proxy có thể thực thi logic trong handler mà không sửa phần thân target. Ngược lại, một bộ dệt trực tiếp có thể chèn lệnh ngay tại ranh giới method.

Vì vậy muốn trả lời “lỗi phát sinh trong mã đã dệt hay trong advice?”, cần hiểu **vị trí instruction** và **nguồn logic** chứ không chỉ nhìn tên lớp trong stack trace.

## 4. Ba thời điểm weaving và hệ quả gỡ lỗi

### 4.1. Compile-time weaving

```text
Source nghiệp vụ + khai báo aspect
                 |
              Compiler/
               Weaver
                 |
        Class/bytecode đã dệt
                 |
              Runtime
```

Bộ dệt có thể phân tích AST hoặc biểu diễn trung gian, tìm điểm phù hợp, tạo phương thức/đoạn mã trung gian và biên dịch thành mã thực thi.

Khả năng hỗ trợ gỡ lỗi có thể gồm:

- Báo cáo shadow nào được chọn và advice nào áp dụng.
- Giữ hoặc biến đổi metadata ánh xạ source.
- Sinh thông tin để debugger bước giữa mã gốc và mã advice.
- Lưu artifact đã dệt để so sánh với artifact trước khi dệt.

Điểm cần cảnh giác: line number sau biến đổi có thể không còn tương ứng một-một với một dòng nguồn ban đầu. Cần biết công cụ duy trì thông tin debug ra sao.

### 4.2. Load-time weaving

```text
Bytecode chưa dệt
       |
   Class loading
       |
 Transformer / Weaver
       |
 Bytecode trong runtime
       |
     Execute
```

Ở cách này, bytecode đã biến đổi có thể tồn tại **chỉ trong quá trình nạp và bộ nhớ runtime**. Tệp trên ổ đĩa có thể vẫn là phiên bản chưa dệt. Nếu dùng công cụ để kiểm tra tệp class ban đầu và thấy không có lời gọi advice, **chưa đủ** để kết luận advice không được chèn lúc nạp.

Những yếu tố cần xác minh:

- Class loader nào nạp lớp?
- Transformer được đăng ký trước hay sau khi lớp nạp?
- Cấu hình weaving áp dụng cho lớp/module nào?
- Có hỗ trợ retransform/redefine không, và giới hạn là gì?
- Runtime có cung cấp cơ chế dump bytecode sau biến đổi không?

Không suy rằng tất cả transformer đều cho phép biến đổi lại lớp đã nạp; điều đó phụ thuộc công cụ và cơ chế môi trường.

### 4.3. Runtime interception

```text
Caller -> Proxy/Dispatcher -> Advice chain -> Target
```

Với cơ chế này, phần thân target **có thể hoàn toàn không bị sửa**. Thay vào đó, caller đi qua trung gian; handler chọn advice và chuyển tiếp lời gọi.

Tình huống gỡ lỗi phổ biến:

- Breakpoint ở proxy/handler chạy, rồi mới tới target.
- Stack trace có thể xuất hiện frame adapter/reflection.
- Lời gọi trực tiếp trên target bỏ qua proxy có thể không nhận advice.
- Self-invocation trên target có thể không đi qua đối tượng trung gian.
- Một bộ lọc theo method signature có thể đúng nhưng đường chạy không vào proxy.

Giới hạn này thuộc **một kiểu interception**, không phải định nghĩa chung của mọi AOP.

## 5. Metadata debug trong Java class file

Với Java, thông tin gỡ lỗi trong bytecode giúp debugger liên hệ instruction đang chạy với mã nguồn. Một số thành phần đáng biết:

| Metadata hoặc cơ chế | Công dụng | Giới hạn cần nhớ |
| --- | --- | --- |
| `SourceFile` attribute | Thường ghi tên tệp nguồn liên quan đến class | Chưa phải ánh xạ đầy đủ từng instruction |
| `LineNumberTable` | Liên kết vị trí bytecode với số dòng nguồn | Một dòng có thể tương ứng nhiều instruction; có thể thiếu hoặc thay đổi |
| `LocalVariableTable` | Tên và phạm vi biến local để debugger hiển thị | Thường phụ thuộc cách biên dịch và thông tin debug có được giữ |
| `LocalVariableTypeTable` | Thông tin kiểu chữ ký generic của local khi có | Không bảo đảm tồn tại cho mọi class |
| `MethodParameters` | Tên/thuộc tính tham số khi được lưu | Khác cơ chế local variable debug; tùy tùy chọn biên dịch |
| `SourceDebugExtension` / SMAP | Có thể biểu diễn ánh xạ nguồn mở rộng trong công cụ hỗ trợ | Không phải mọi weaver/debugger đều cung cấp hoặc tiêu thụ |
| Stack trace elements | Tên lớp, method, tệp và dòng khi có | Không phải bản đồ đầy đủ về chuỗi advice đã áp dụng |
| Debugger protocol / VM | Quan sát frame, breakpoint, local variables | Khả năng bước qua code sinh và hiển thị nội dung phụ thuộc công cụ |

**Không phải mọi class file đều có đầy đủ các metadata trên.** Thông tin có thể bị bỏ khi biên dịch, bị biến đổi, bị obfuscate hoặc thiếu tương ứng với source. Debugger có thể hiển thị dòng không xác định hoặc một dòng mang tính xấp xỉ/được chọn cho instruction.

### 5.1. Compile với thông tin debug

Ví dụ từ một chương trình Java có thể biên dịch bằng:

```bash
javac -g WovenTraceDemo.java
javap -c -l -p WovenTraceDemo
```

`javac -g` yêu cầu phát sinh thông tin debug được hỗ trợ. `javap -c -l -p` hiển thị lệnh bytecode, thông tin dòng/local và thành viên private trong class được xem. Đây là **lệnh để khảo sát**, không phải bằng chứng rằng lớp đã được AOP weaving.

So sánh có thể được thực hiện trên bản build khác với `javac -g:none` để quan sát ảnh hưởng của việc bỏ debug info. Khi làm vậy nên giữ toàn bộ điều kiện biên dịch khác giống nhau, và nhớ rằng **compiler/JDK phiên bản khác nhau có thể tạo bytecode khác nhau**.

### 5.2. Source mapping sau biến đổi

Bộ dệt chèn thêm instruction, tạo bridge/helper hoặc dời logic có thể cần cập nhật metadata dòng. Nếu công cụ giữ ánh xạ phù hợp, debugger vẫn có thể nhảy về source nghiệp vụ hoặc source advice.

Nếu không giữ ánh xạ, tình huống có thể gồm:

- Breakpoint tại một dòng trông hợp lý nhưng không khớp instruction dự kiến.
- Stepping nhảy qua các frame sinh tự động.
- Stack trace chỉ hiển thị tệp/dòng của lớp trung gian hoặc `Unknown Source`.
- Dòng code hiển thị không giúp nhận diện advice nào đã gây tác động.

**Không có bảo đảm ánh xạ đa nguồn hoàn hảo.** Một instruction đã dệt có thể biểu diễn logic có nguồn gốc từ cả join point và advice. Một số công cụ dùng mapping mở rộng; công cụ khác chỉ dùng line numbers thông thường.

## 6. Tại sao bước debug từng dòng khó hơn sau weaving?

Giả sử mã nguồn chỉ có:

```java
payment.charge(1000L);
```

Nhưng đường chạy hiệu lực có thêm:

```text
before advice
pointcut runtime guard
security advice
business charge()
after returning advice
audit advice
```

Khi chọn **Step Into** ở dòng gọi, debugger có thể đi qua proxy, handler, bridge method hoặc chính code advice trước khi tới phương thức `charge`. Nếu dùng **Step Over**, debugger có thể vẫn chạy toàn bộ advice rồi mới trở lại caller.

Không nên diễn giải việc debugger “nhảy” từ dòng này sang dòng khác là lỗi lập trình ngay lập tức. Cần biết đang xem **nguồn gốc nghiệp vụ**, **advice** hay **code sinh**.

### 6.1. Breakpoint ở đâu để trả lời từng câu hỏi?

| Muốn biết | Breakpoint/log phù hợp |
| --- | --- |
| Caller đã gọi đường nào? | Tại call site hoặc adapter nhập |
| Pointcut có được áp dụng? | Báo cáo weaving hoặc điểm vào interceptor |
| Guard động nhận dữ liệu gì? | Trước/đang đánh giá guard, nếu công cụ cho quan sát |
| Advice nào chạy trước? | Điểm vào before/around advice |
| Target có thực sự được gọi? | Đầu thân phương thức nghiệp vụ |
| Kết quả bị thay đổi ở đâu? | Trước/sau mỗi lớp advice quanh |
| Ngoại lệ gốc từ đâu? | Điểm ném trong thân và các catch/translator |
| Cleanup có che lỗi không? | Đường finally và xử lý lỗi phụ |
| Có bypass proxy? | So sánh đường qua proxy với gọi trực tiếp target |

Một breakpoint ở phương thức nghiệp vụ chỉ chứng minh **phần thân đã được chạy**. Nó không tự trả lời vì sao advice nào được chọn hoặc không được chọn.

### 6.2. Conditional breakpoints và side effect

Debugger có thể cho đặt breakpoint có điều kiện. Nhưng biểu thức điều kiện có thể phải **đánh giá trong runtime đang bị dừng**. Nếu điều kiện gọi phương thức có side effect hoặc I/O, trạng thái ứng dụng có thể thay đổi hoặc việc debug trở nên chậm, gây hiện tượng khác với chạy bình thường.

Khi có thể, ưu tiên điều kiện đơn giản dựa trên dữ liệu đã có hoặc event trace có cấu trúc thay vì biến debugger thành một phần của workflow nghiệp vụ.

## 7. Stack trace sau weaving: nên đọc như thế nào?

Một stack trace Java cho biết một chuỗi frame của ngăn xếp tại thời điểm ngoại lệ hoặc lúc thu thập trace. Nó giúp xác định nơi ngoại lệ được tạo/lan truyền, nhưng không phải nhật ký đầy đủ mọi advice đã chạy và kết thúc.

Ví dụ mô hình:

```text
IllegalArgumentException: invalid amount
    at payment.RealPayment.pay(RealPayment.java:42)
    at ...reflectiveInvocation(...)
    at ...AdviceDispatcher.invoke(...)
    at ...GeneratedProxy.pay(...)
    at checkout.Caller.checkout(Caller.java:18)
```

**Đây là stack trace minh họa, không phải đầu ra cam kết của JDK hoặc một công cụ AOP cụ thể.** Tên frame, lớp sinh, số dòng và thứ tự chính xác sẽ khác theo cơ chế interceptor, JDK, compiler và vị trí ném lỗi.

Cách đọc:

1. **Lỗi được tạo ở đâu?** Phân biệt thân target với advice hoặc adapter.
2. **Các frame trung gian có nghĩa gì?** Chúng có thể do reflection/proxy/weaver tạo để nối đường đi.
3. **Có bọc lỗi không?** `cause` có thể chứa lỗi gốc bên dưới một exception chuyển đổi.
4. **Có lỗi thứ cấp không?** `suppressed` có thể ghi nhận cleanup hoặc việc báo lỗi bị hỏng.
5. **Có mất thông tin nguồn không?** `Unknown Source` hoặc dòng không chính xác cần kiểm tra debug metadata.
6. **Có trace ID không?** Stack trace của một thời điểm không liên kết được tất cả giai đoạn của một yêu cầu phân tán.

### 7.1. Stack trace không chứng minh mọi advice đã chạy

Một before advice có thể đã ghi log rồi kết thúc, nên frame của nó không còn nằm trên stack lúc target ném lỗi. Một advice ở sau khi trả về cũng chưa hề chạy tại thời điểm target đang ném lỗi.

Muốn biết **trình tự thực tế**, cần trace sự kiện hoặc instrumentation có điểm ghi bắt đầu/kết thúc, không chỉ một ảnh chụp stack.

### 7.2. Reflection và ngoại lệ có lớp bọc

Khi interceptor gọi đích bằng `Method.invoke`, Java reflection có thể bọc ngoại lệ do target ném thành `InvocationTargetException`. Handler thường cần phân biệt lớp bọc kỹ thuật với `getCause()` là lỗi từ target.

Công cụ weaving không dùng reflection có thể không có lớp bọc này. Vì vậy không nên tự giả định mọi lỗi trong AOP đều xuất hiện dưới dạng `InvocationTargetException`.

### 7.3. JIT và bytecode tối ưu hóa

Các môi trường runtime hiện đại có tối ưu như JIT/inlining. Debugger và stack-walking API thường phối hợp với VM để tái hiện frame có ý nghĩa, nhưng độ chi tiết, hiệu năng và trải nghiệm stepping có thể phụ thuộc phiên bản/runtime. Đừng suy từ một phép đo đơn lẻ rằng một frame trong stack chính là một lần gọi vật lý chưa từng được tối ưu.

## 8. Truy vết “vì sao advice đã chạy” cần hơn stack trace

Một trace phục vụ chẩn đoán pointcut nên giúp trả lời ít nhất:

- **Join point nào?** Loại, chữ ký và một định danh ổn định thích hợp.
- **Vị trí hay shadow nào?** Source location/định danh call site khi có.
- **Pointcut nào?** Tên quy tắc hoặc một ID tương ứng với bản cấu hình.
- **Advice nào?** ID/loại advice, thứ tự hiệu lực.
- **Khi nào?** Thời điểm vào/ra, thời lượng theo đồng hồ phù hợp.
- **Ngữ cảnh nào?** Trace ID, request ID, loại đối tượng hoặc thuộc tính đã được phép ghi.
- **Điều kiện động đã khớp không?** Phân biệt bị loại bởi guard hay không thuộc phạm vi tĩnh.
- **Kết quả là gì?** Return, throw, bị chặn trước proceed, fallback hoặc retry.
- **Build nào?** Phiên bản artifact đã dệt, cấu hình và chính sách áp dụng.

Không phải mọi công cụ cung cấp sẵn tất cả trường trên. Có trường là **weaving report**, có trường cần do advice thêm vào **runtime log** và có trường do hệ thống build cung cấp.

### 8.1. Static join-point ID và dynamic invocation ID

Giả sử cùng một phương thức `Payment.pay(String,long)` được gọi 1.000 lần. Dùng chỉ **chữ ký** không thể phân biệt 1.000 lần chạy.

Nên suy nghĩ theo hai tầng:

```text
Static identity:
  point = Payment.pay(String,long)
  kind = method execution
  source = phiên bản build đang chạy

Dynamic identity:
  traceId = req-0042
  invocationId = span-009
  outcome = success / failure
```

Đây là mẫu cấu trúc sự kiện, không phải API bắt buộc. Một method call join point ở hai vị trí khác nhau có thể cần thêm **call-site identity**, dù chữ ký phương thức đích giống nhau.

### 8.2. ID phải được gắn đúng vòng đời

- **Request ID** có thể dùng xuyên nhiều thao tác.
- **Invocation/span ID** phân biệt các lần gọi lồng nhau.
- **Build ID** định danh mã và cấu hình weaving.
- **Pointcut/advice ID** mô tả chính sách được áp dụng.

Đừng dùng một biến mutable toàn cục `currentRequestId` cho mọi luồng. Nó có thể khiến log của yêu cầu này mang nhãn của yêu cầu khác. Cần cơ chế context per invocation/tác vụ, cùng chính sách truyền và dọn ngữ cảnh.

## 9. Thiết kế bản ghi truy vết có cấu trúc

Một sự kiện minh họa:

```json
{
  "buildId": "build-2026-10",
  "traceId": "req-0042",
  "invocationId": "call-03",
  "joinPoint": {
    "kind": "method-execution",
    "signature": "Payment.pay(String,long)"
  },
  "advice": {
    "id": "audit-payments",
    "phase": "before"
  },
  "event": "entered"
}
```

Các giá trị trong JSON chỉ mang tính minh họa; đây **không phải schema chuẩn do mọi AOP engine phát ra**.

Một sự kiện kết thúc có thể dùng cùng trace/invocation và mô tả `outcome=returned`, `outcome=thrown` hoặc `outcome=short-circuited`. Với around advice, cần xác định **có gọi proceed hay không**, vì sự kiện “đã vào advice” không đồng nghĩa “target đã chạy”.

### 9.1. Tránh log quá nhiều dữ liệu

Join-point context có thể chứa mật khẩu, token, thông tin thanh toán và dữ liệu cá nhân. Không tự động ghi `args.toString()` hoặc giá trị trả về toàn bộ chỉ để debug.

Ưu tiên:

- Ghi tên/loại điểm, chính sách và kết quả tổng quát.
- Ghi mã tương quan đã được thiết kế, không dùng đối tượng hoặc địa chỉ bộ nhớ làm ID nghiệp vụ.
- Che, loại bỏ hoặc băm có kiểm soát các giá trị nhạy cảm nếu thực sự cần.
- Giới hạn kích thước payload và vòng đời lưu giữ.
- Không để lỗi ghi log tùy chọn che kết quả nghiệp vụ, trừ khi hợp đồng audit bắt buộc yêu cầu khác.

### 9.2. Độ chính xác của thời gian

Đo duration thường cần **đồng hồ đơn điệu** tương ứng (ví dụ `System.nanoTime()` trong Java), còn timestamp để đối chiếu giữa máy cần đồng hồ thời gian thực và phương án đồng bộ/ước lượng sai số.

Không tính duration chỉ bằng phép trừ hai timestamp wall-clock nếu môi trường có thể điều chỉnh đồng hồ. Và không kết luận một dòng `after returning` cho phương thức trả future đồng nghĩa tác vụ bất đồng bộ đã hoàn thành.

## 10. Ví dụ Java 17 chạy được: dấu vết caller → interceptor → business

Java thuần không cung cấp một cú pháp pointcut/weaver AOP thống nhất. Để quan sát **đường gọi đã được kết hợp qua interceptor**, ta có thể dùng `java.lang.reflect.Proxy` của JDK.

Ví dụ này là **mô phỏng một cơ chế runtime interception**: phương thức giao diện được gọi qua proxy, handler ghi sự kiện advice, gọi target và chuyển tiếp kết quả/ngoại lệ. Nó không tạo bytecode weaving cho mọi join point, không tự chặn field access và không thay thế engine AOP tổng quát.

Tạo tệp `WovenTraceDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class WovenTraceDemo {

    interface Payment {
        String pay(String orderId, long cents);
    }

    static final class RealPayment implements Payment {
        @Override
        public String pay(String orderId, long cents) {
            System.out.println("business:pay:" + orderId + ":" + cents);
            if (cents < 0) {
                throw new IllegalArgumentException("negative cents");
            }
            return "RECEIPT:" + orderId;
        }
    }

    public static void main(String[] args) {
        Payment target = new RealPayment();
        AtomicInteger sequence = new AtomicInteger();

        Payment traced = (Payment) Proxy.newProxyInstance(
                Payment.class.getClassLoader(),
                new Class<?>[] { Payment.class },
                (Object proxy, Method method, Object[] values) -> {
                    String traceId = "req-" + sequence.incrementAndGet();
                    String point = method.getDeclaringClass()
                            .getSimpleName() + "." + method.getName();

                    System.out.println("trace:enter:" + traceId + ":" + point);
                    System.out.println("advice:before:" + traceId);

                    try {
                        Object result = method.invoke(target, values);
                        System.out.println("advice:afterReturning:" + traceId);
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable original = wrapped.getCause();
                        System.out.println("advice:afterThrowing:" + traceId
                                + ":" + original.getClass().getSimpleName());
                        throw original;
                    } finally {
                        System.out.println("trace:exit:" + traceId);
                    }
                }
        );

        System.out.println("caller:start");
        System.out.println("caller:result:" + traced.pay("O-1", 1200));

        try {
            traced.pay("O-2", -1);
        } catch (IllegalArgumentException error) {
            System.out.println("caller:error:" + error.getMessage());
        }

        System.out.println("caller:end");
    }
}
```

Lệnh biên dịch và chạy (JDK 17+):

```bash
javac -g WovenTraceDemo.java
java WovenTraceDemo
```

Đầu ra:

```text
caller:start
trace:enter:req-1:Payment.pay
advice:before:req-1
business:pay:O-1:1200
advice:afterReturning:req-1
trace:exit:req-1
caller:result:RECEIPT:O-1
trace:enter:req-2:Payment.pay
advice:before:req-2
business:pay:O-2:-1
advice:afterThrowing:req-2:IllegalArgumentException
trace:exit:req-2
caller:error:negative cents
caller:end
```

### 10.1. Đọc output theo hai trục

**Trục cấu trúc (tĩnh):**

- Handler nhận `Method` tương ứng với phương thức giao diện `Payment.pay`.
- Tên `Payment.pay` mô tả phương thức nhìn qua giao diện proxy trong ví dụ.
- `RealPayment` là lớp target thực tế, không nhất thiết đồng nhất với declaring type của `Method` mà handler nhận.
- Một công cụ weaving vào thân method có thể cung cấp metadata khác, đặc biệt về call site hoặc execution.

**Trục diễn tiến (động):**

- `req-1` và `req-2` phân biệt hai invocation.
- Cả hai đi qua advice trước.
- `req-1` trả về bình thường, có `afterReturning` và caller nhận receipt.
- `req-2` ném `IllegalArgumentException` từ target; reflection bọc tạm thời trong `InvocationTargetException`, handler lấy `getCause()` rồi truyền lỗi gốc để caller bắt.
- `trace:exit` chạy từ `finally` của handler ngay cả trên đường lỗi.

### 10.2. Vì sao ví dụ không in stack trace đầy đủ?

Dấu vết stack có thể chứa tên lớp proxy được sinh, frame reflection và thông tin dòng thay đổi theo JDK/cách thực thi. Ví dụ dùng **sự kiện có cấu trúc xác định** để stdout ổn định, không phỏng đoán một stack trace cụ thể.

Nếu muốn khảo sát stack thật trên máy đang chạy, có thể thêm `new Exception("diagnostic").printStackTrace()` tại điểm được chọn rồi đọc kết quả thực tế. Không nên hard-code một bản stack trace giả làm “stdout chính xác” cho mọi môi trường.

### 10.3. Giới hạn và giá trị của ví dụ

- Gọi `target.pay(...)` trực tiếp **không** đi qua handler proxy này.
- Phương thức `RealPayment` tự gọi phương thức khác của cùng đối tượng có thể bypass proxy.
- Đây là **interception minh họa**, không phải weaving ở mọi method call/execution.
- `AtomicInteger` chỉ làm bộ đếm cho ví dụ; trace ID production cần bền vững/độc lập theo yêu cầu, không tự coi `req-1` là định danh phân tán an toàn.
- Runtime trace cho thấy **chuỗi đã thực sự chạy**, nhưng không chứng minh toàn bộ **tập static shadows** mà một weaver khác có thể chọn.

## 11. Soi bytecode và debug metadata bằng công cụ JDK

Có thể tiếp tục từ tệp `WovenTraceDemo.java`:

```bash
javap -c -l -p WovenTraceDemo
```

Ý nghĩa các tùy chọn:

- `-c`: hiển thị bytecode instructions đã biên dịch.
- `-l`: hiển thị line number và local variable tables nếu có.
- `-p`: bao gồm thành viên private trong phần liệt kê.

Nếu muốn quan sát lớp target lồng bên trong, trên một số shell có thể cần quote/escape ký tự `$` trong tên binary class:

```text
WovenTraceDemo$RealPayment
```

Lệnh xem lớp lồng phải phù hợp với cú pháp shell đang dùng. Không nên giả định dấu `$` trong tên class là cú pháp thuộc Java source hoặc cú pháp wildcard của pointcut.

### 11.1. So sánh “trước” và “sau” weaving đúng cách

Với một hệ thống thực sự có compiler/bytecode weaver, cách điều tra có thể là:

1. Giữ bản bytecode **chưa dệt** từ cùng nguồn/build.
2. Lấy bản bytecode **đã dệt** từ đầu ra build hoặc chức năng dump được hỗ trợ.
3. Xác nhận cùng phiên bản JDK, dependency, cấu hình và tên class.
4. Dùng công cụ xem bytecode để so sánh các phương thức/nhánh/handler.
5. Kiểm tra `LineNumberTable` và metadata debug có được giữ/cập nhật không.
6. Đối chiếu báo cáo weaving: pointcut nào khớp shadow nào.
7. Chạy kiểm thử để xác nhận đường thực thi thực tế đúng.

**Không nên chỉ so sánh hai tệp class ở hai bản build khác nhau** rồi kết luận mọi khác biệt đều do weaving: compiler option, thay đổi nguồn, tối ưu và dependency cũng có thể tạo bytecode khác.

### 11.2. Trong load-time weaving, tệp class trên đĩa có thể chưa dệt

Một nguyên tắc chẩn đoán: nếu weaving xảy ra lúc nạp, công cụ `javap` trên tệp lớp có sẵn **chỉ cho thấy tệp ấy**, không nhất thiết là bytecode sau biến đổi đã được class loader định nghĩa trong VM. Muốn kiểm tra phiên bản runtime phải dùng cơ chế quan sát/dump do môi trường cho phép.

## 12. Weaving report: truy vết tĩnh trước khi chạy

Một công cụ AOP có thể phát báo cáo về các join-point shadows được so khớp. Nội dung thường cần đến:

- Aspect và pointcut/advice nào được áp dụng.
- Loại join point (call/execution/...).
- Chữ ký và vị trí nguồn/mã của điểm nếu có.
- Class hoặc artifact được weaving.
- Điểm bị loại, nếu có chế độ chẩn đoán tương ứng.
- Thông báo lỗi/cảnh báo khi không thể can thiệp.

Một dòng báo cáo **minh họa**, không phải cú pháp cố định:

```text
WEAVE matched:
  advice=auditPayment
  kind=method-execution
  point=PaymentService.pay(String,long)
  class=PaymentService
  source=PaymentService.java
```

Báo cáo này trả lời **điểm tĩnh nào được chọn**. Nó không tự chứng minh một lời gọi đã xảy ra hoặc guard động đã khớp. Một method có thể được dệt nhưng chưa bao giờ chạy trong ca kiểm thử.

### 12.1. Không nhầm “khớp” với “đã áp dụng thành công”

Một weaver có thể tìm thấy điểm có khả năng khớp nhưng thất bại khi biến đổi class hoặc không được phép sửa một thành phần. Cần phân biệt:

1. Điểm được biểu thức chọn.
2. Điểm nằm trong phạm vi có thể weaving.
3. Weaving hoàn tất thành công.
4. Runtime thực sự đi qua điểm.
5. Điều kiện động thỏa và advice chạy.

Nếu thiếu bước (3), báo cáo matcher tích cực không đủ. Nếu thiếu (4), sẽ không có sự kiện runtime dù weaving đúng. Nếu thiếu (5), guard có thể đã lọc invocation.

### 12.2. Cách dùng report trong CI

Với pointcut quan trọng, nhóm phát triển có thể lưu một bản kiểm kê được xem xét về tập join point mong muốn. Trong CI, so sánh report mới với kỳ vọng:

- Điểm nào mới được chọn do thêm method/class?
- Điểm nào biến mất sau refactor?
- Có điểm nào bị chọn ngoài vùng cho phép?
- Có thay đổi loại join point hoặc class loader?
- Có cảnh báo không thể weaving một lớp cần thiết?

Cần tránh dùng **chính report hiện tại** làm “đáp án chuẩn” mà không có yêu cầu độc lập. Kiểm thử chỉ so report với bản thân không phát hiện sai phạm vi.

## 13. Ánh xạ source và bytecode khi xuất hiện mã sinh

Mã được compiler hoặc weaver tạo có thể có:

- Bridge methods.
- Synthetic methods.
- Helper methods giữ đoạn logic được tách.
- Wrapper hoặc dispatcher cho advice.
- Anonymous/lambda classes.
- Proxy classes được sinh runtime.

Những phần này không nhất thiết tồn tại như một method có tên tương đương trong source. Debugger có thể hiển thị frame sinh tự động hoặc ẩn một số chi tiết tùy khả năng.

### 13.1. Source map không thể luôn chỉ đến một dòng duy nhất

Một instruction “gọi advice quanh method” có thể phản ánh cả:

- Vị trí join point trong source nghiệp vụ.
- Dòng khai báo advice trong source aspect.
- Logic sinh tự động của weaver.

Không phải mọi VM/debugger đều hỗ trợ ánh xạ đa nguồn hoàn chỉnh ở cùng một instruction. Nếu stack trace đặt lỗi ở lớp sinh thay vì dòng advice, có thể cần đối chiếu report weaving và mã/bytecode sinh.

### 13.2. Obfuscation và tối ưu build

Nếu tên lớp/method được đổi hoặc debug metadata bị loại, stack trace production có thể khó đọc. Cần giữ artifact/mapping tương ứng với **đúng bản build** để giải mã. Không áp dụng mapping từ phiên bản khác, vì cùng một tên rút gọn có thể chỉ đến mã khác.

Nếu hệ thống ký hoặc checksum artifact, việc weaving sau thời điểm ký cũng ảnh hưởng việc kiểm chứng phiên bản thực thi. Kiến trúc build/release phải tính đến thời điểm weaving.

## 14. Ngoại lệ, cause và suppressed trong đường đã dệt

Một advice có thể ném ngoại lệ **trước** khi phần gốc chạy hoặc **sau** khi phần gốc đã thành công. Vì vậy:

```text
Caller nhận exception
          |
          +-- Có thể body nghiệp vụ chưa được gọi
          |
          +-- Có thể body đã ném exception gốc
          |
          +-- Có thể body đã thành công nhưng after advice lỗi
          |
          +-- Có thể lỗi của advice trong đã bị advice ngoài chuyển đổi
```

Khi gỡ lỗi:

1. Xác định lỗi đầu tiên phát sinh ở đâu.
2. Xác định phần gốc đã được gọi bao nhiêu lần.
3. Xác định có advice nào đổi loại lỗi hoặc trả fallback không.
4. Đọc `Throwable.getCause()` và `getSuppressed()` nếu áp dụng.
5. Kiểm tra có retry nào đã thực thi lại nghiệp vụ hoặc gây side effect không.

Một `finally` tự ném lỗi có thể **che ngoại lệ gốc**; Java `try-with-resources` có cơ chế suppressed cho lỗi khi đóng tài nguyên, nhưng không được suy rằng mọi after/finally advice đều tự động bảo toàn primary error như vậy.

### 14.1. Mã đã dệt và thay đổi hợp đồng quan sát

Nếu phần nghiệp vụ trả về nhưng after advice ném lỗi, caller có thể nhận ngoại lệ. Đây không nhất thiết là “lỗi của weaver”: có thể chính advice gây ra theo logic đã được cấu hình. Cần phân biệt **lỗi biến đổi mã** với **hành vi của advice**.

## 15. Đa luồng, đệ quy và bất đồng bộ làm trace phức tạp hơn

### 15.1. Hai invocation có thể xen kẽ

```text
T1: before(A) --- business(A) ------- after(A)
T2:     before(B) --- business(B) --- after(B)
```

Nếu chỉ nhìn thứ tự timestamp trong một file log, dòng của A/B có thể xen kẽ. Điều đó không chứng minh advice chạy sai thứ tự **bên trong từng invocation**.

Cần gắn event theo trace ID và invocation/span ID. Các ID này không nên lấy từ một biến mutable dùng chung không đồng bộ.

### 15.2. Đệ quy tạo nhiều frame cho cùng join point

Một method đệ quy có thể phát sinh nhiều invocation lồng nhau của cùng shadow. Nếu advice đo thời gian bằng một biến `startTime` dùng chung, lần gọi sâu hơn có thể ghi đè mốc của lần ngoài.

Một thiết kế an toàn thường lưu thông tin theo invocation/frame tương ứng.

### 15.3. Method return khác task completion

Một method trả `CompletableFuture<Result>` có thể trả handle rất nhanh trong khi công việc tiếp tục ở một thread khác.

- Trace around **method execution** có thể kết thúc lúc trả Future.
- Trace **tác vụ bất đồng bộ** cần theo dõi tới completion phù hợp.
- Ngoại lệ xảy ra sau này có thể nằm trong completion state, không xuất hiện trong `catch` đồng bộ bao lời gọi tạo Future.
- Context cần được truyền sang task nếu thiết kế trace xuyên ranh giới.

Một trace sai ranh giới dễ báo “success” quá sớm. Hãy đặt tên event phân biệt `method-returned` và `task-completed` nếu thực sự cần cả hai.

### 15.4. Không coi thread ID là trace ID

Một thread pool xử lý nhiều request nối tiếp hoặc xen kẽ. Thread ID có thể hữu ích để chẩn đoán, nhưng **không đại diện duy nhất cho một request**. Với bất đồng bộ, cùng một request có thể chạy trên nhiều thread.

## 16. Khi nào log tracing tự làm thay đổi hệ thống?

Instrumentation không “miễn phí”. Nó có thể:

- Tăng CPU và cấp phát.
- Gây contention do logger đồng bộ.
- Thực hiện I/O trong đường nóng.
- Làm lộ dữ liệu nhạy cảm.
- Ném ngoại lệ từ formatter hoặc sink.
- Tạo vòng lặp nếu hàm logging cũng khớp pointcut.
- Thay đổi lịch chạy của thread, khiến race condition khó tái hiện.

Do đó một trace phục vụ debug nên **tối thiểu đủ dùng**, có giới hạn và chính sách lỗi rõ ràng.

### 16.1. Dùng sampling có chấp nhận được không?

Với telemetry chẩn đoán, sampling có thể giảm chi phí, miễn không làm mất những event cần thiết cho mục đích đo. Nhưng với audit bắt buộc, lấy mẫu có thể không chấp nhận được. Không tự dùng sampling cho dữ liệu tuân thủ chỉ vì tracing quá nặng.

### 16.2. Tránh tự kích hoạt aspect

Nếu advice ghi log qua một method cũng thuộc pointcut, có thể tạo chuỗi:

```text
advice -> logger.log() -> cùng pointcut -> advice -> ...
```

Cần thiết kế ranh giới lọc rõ ràng hoặc cơ chế chống reentrancy phù hợp. Một cờ global không phân biệt invocation có thể khiến yêu cầu khác mất advice.

## 17. Khi nào nên lấy dump mã đã dệt?

Dump bytecode hoặc bản representation có thể hữu ích để:

- Xác nhận có thực sự chèn advice/guard không.
- So sánh vị trí lệnh trước và sau.
- Giải thích frame sinh hoặc line-number bất thường.
- Xác minh một method không được weaving vì nằm ngoài phạm vi.
- Chẩn đoán lỗi verifier/loader hoặc giới hạn biến đổi.

Nhưng dump cần thận trọng:

- Có thể chứa mã độc quyền, hằng số nhạy cảm, tên module và đường dẫn.
- Có thể rất lớn, ảnh hưởng I/O và bộ nhớ.
- Có thể không thể tái tạo chính xác ở runtime khác.
- Phải gắn đúng build ID/cấu hình và thời điểm lấy.
- Không phát hành public hoặc gửi dump sản phẩm thực nếu chưa có quyền và quy trình bảo mật.

Trong nhiều trường hợp, **weaving report + trace có cấu trúc + stack trace lỗi + artifact build đúng phiên bản** đã đủ để xác định vấn đề mà không cần dump toàn bộ VM.

## 18. Quy trình điều tra một advice “biến mất”

Tình huống: phương thức `Payment.pay` chạy, nhưng không thấy log audit.

Tiến trình có hệ thống:

1. **Xác nhận mã nguồn và bản build**: lớp đang chạy đúng phiên bản nào? Cấu hình weaving có đúng không?
2. **Xác nhận pointcut**: loại join point là call hay execution? Chữ ký, modifier, package, annotation có khớp không?
3. **Kiểm tra phạm vi weaving**: module/class nào được áp dụng? Nếu load-time weaving, transformer đã hoạt động trước khi lớp nạp chưa?
4. **Kiểm tra weaving report**: shadow tương ứng có được chọn và dệt thành công không?
5. **Kiểm tra điều kiện runtime**: có guard dựa trên args, target hoặc control flow đang sai không?
6. **Kiểm tra đường gọi**: có đi qua proxy/interceptor không? Có self-invocation hoặc gọi trực tiếp target không?
7. **Kiểm tra advice ngoài**: có advice nào không gọi proceed, từ chối hoặc đổi đường thực thi không?
8. **Kiểm tra logger**: việc ghi log có lỗi hoặc bộ lọc log có bỏ event không?
9. **Tạo ca tối thiểu**: gọi một lần đúng và một lần sai điều kiện, gắn invocation ID để quan sát.
10. **Đối chiếu report + trace**: tách khớp tĩnh khỏi advice runtime và loại trừ từng nguyên nhân.

Đừng sửa wildcard thành “chọn mọi method” để chữa triệu chứng chưa xác định. Nó có thể mở rộng advice tới những vùng nguy hiểm mà vẫn không giải quyết lỗi đường weaving.

## 19. Quy trình điều tra một stack trace “lạ”

Tình huống: ứng dụng ném ngoại lệ ở một frame tên khó hiểu, debugger nhảy sang method sinh.

Nên làm:

1. Lưu **stack trace đầy đủ**, bao gồm `cause` và `suppressed`.
2. Ghi lại bản build, phiên bản JDK, loại weaving và cấu hình.
3. Xác định frame nào thuộc target, advice, proxy/reflection hoặc code sinh.
4. Kiểm tra `SourceFile`/`LineNumberTable` của đúng class đang chạy hoặc artifact tương ứng.
5. Nếu phù hợp, xem weaving report và bytecode đã dệt.
6. Tái hiện với breakpoint/log tại entry/exit của advice và target.
7. So sánh đầu ra khi **có** và **không có** advice trong một môi trường kiểm thử có kiểm soát, nếu công cụ cho phép mà không thay đổi nghĩa nghiệp vụ.
8. Kiểm tra lỗi do advice sau thành công, advice ngoài đổi lỗi, hoặc cleanup che lỗi gốc.
9. Ghi kết luận bằng **đường thực thi có chứng cứ**, không chỉ từ tên phương thức gần đỉnh stack.

Không nên “xóa frame proxy” khi phân tích, vì đôi khi chính adapter/handler mới là nơi lỗi xảy ra. Có thể rút gọn cách trình bày cho người đọc, nhưng phải giữ bản gốc để chẩn đoán.

## 20. Kiểm thử weaving và traceability

Một bộ kiểm thử đáng tin cậy phải chứng minh cả **đúng phạm vi** lẫn **đúng thông tin quan sát**:

| Ca kiểm thử | Điều cần xác minh |
| --- | --- |
| Phương thức phải được advice | Weaving/matching đúng và advice chạy đúng lần |
| Phương thức không thuộc chính sách | Advice không chạy, kể cả sau refactor |
| Overload/override | Không chọn sai chữ ký hoặc kiểu |
| Call vs execution | Quan sát đúng ranh giới dự kiến |
| Guard động đúng/sai | Có/không có advice tương ứng |
| Lời gọi qua proxy và trực tiếp | Hiểu rõ phạm vi interception |
| Self-invocation | Hành vi đúng giới hạn công cụ |
| Advice trả kết quả | Caller nhận đúng kết quả theo hợp đồng |
| Target ném ngoại lệ | Cause và nhánh after-throwing đúng |
| Advice ném ngoại lệ | Không gán nhãn sai nguồn hoặc che lỗi gốc ngoài ý định |
| Hai advice cùng khớp | Thứ tự hiệu lực đúng |
| Đệ quy/đa luồng | Trace ID, invocation ID và state độc lập |
| Method trả Future | Không nhầm return với task completion |
| Debug info có/không | Hiểu độ tin cậy của file/line trong stack |
| Thay bản build | Report, mapping và trace vẫn khớp phiên bản |

### 20.1. Kiểm thử trace bằng sự kiện xác định

Trong kiểm thử, nên dùng bộ ghi sự kiện trong bộ nhớ thay cho logger bất đồng bộ nếu mục tiêu là so thứ tự chính xác. Ví dụ kỳ vọng:

```text
before#req-1
business#req-1
afterReturning#req-1
exit#req-1
```

Với ngoại lệ:

```text
before#req-2
business#req-2
afterThrowing#req-2
exit#req-2
```

Sau đó kiểm tra **chuỗi sự kiện theo từng invocation**, tránh giả định rằng các event của hai thread phải đứng liền nhau trong log chung.

### 20.2. Kiểm thử metadata gỡ lỗi

Nếu dự án yêu cầu debugger/source mapping chính xác, hãy có một quy trình kiểm thử build artifact với thông tin debug cần thiết, xem class thực tế và xác nhận line mapping ở những vị trí trọng yếu. Không chỉ dựa vào mã nguồn trong IDE, vì mã chạy có thể là phiên bản đã biến đổi.

## 21. Những hiểu nhầm thường gặp

**“Weaving luôn tạo một file Java đã sửa.”** Không. Có thể tạo bytecode, adapter hoặc chỉ biến đổi lớp trong khi nạp.

**“Mã trong repository luôn đúng y hệt bytecode đang chạy.”** Không bảo đảm khi có weaving, code generation, proxy, compiler optimization hoặc khác phiên bản build.

**“Breakpoint ở target không dừng nghĩa là pointcut không khớp.”** Có thể target chưa được gọi vì advice chặn trước, đường gọi khác hoặc breakpoint ánh xạ sai.

**“Stack trace là toàn bộ lịch sử advice.”** Không. Nó là ảnh chụp ngăn xếp tại một thời điểm, không ghi đầy đủ advice đã hoàn tất.

**“Một dòng stack trace luôn ánh xạ một dòng Java.”** Không bảo đảm khi có code sinh, biến đổi hoặc thiếu metadata.

**“Có `LineNumberTable` thì gỡ lỗi woven code luôn hoàn hảo.”** Không. Bảng dòng không nhất thiết diễn tả nguồn gốc đa chiều của advice và join point.

**“Có weaving report nghĩa advice chắc chắn đã chạy.”** Chưa đủ. Cần invocation thật và điều kiện runtime khớp.

**“Không có log nghĩa advice không chạy.”** Có thể logger lỗi, lọc log, sampling hoặc advice ghi sự kiện không đúng chỗ.

**“Proxy có thể chặn mọi lời gọi tới target.”** Không. Đường gọi trực tiếp và lời gọi nội bộ có thể bypass proxy.

**“Trace ID chính là thread ID.”** Không. Một request có thể chuyển thread, còn một thread phục vụ nhiều request.

**“Exception do caller nhận chắc chắn do business.”** Không. Advice trước/sau/bao quanh có thể tạo hoặc chuyển đổi ngoại lệ.

**“Dump đã dệt ở một build đủ chứng minh production cùng bytecode.”** Không nếu cấu hình, class loader, JDK hoặc artifact khác nhau.

## 22. Câu hỏi tự luyện

**1.** Một lớp được load-time weaving nhưng tệp `.class` trên ổ đĩa không có lời gọi advice. Có phải weaving không hoạt động?

Chưa chắc. Tệp đĩa có thể là bytecode trước biến đổi. Phải kiểm tra transformer, class loader, log/report và bytecode thực được nạp nếu môi trường cho phép.

**2.** Weaving report nói có năm shadows khớp. Có thể kết luận advice chạy đúng năm lần không?

Không. Mỗi shadow có thể chạy nhiều lần, không lần nào hoặc trượt guard động.

**3.** Stack trace có frame proxy trước target. Có phải source gốc bị sửa trực tiếp không?

Không. Frame proxy có thể chỉ ra lời gọi qua adapter, trong khi thân target chưa bị biến đổi.

**4.** Debugger dừng ở một dòng mã nguồn nhưng instruction đang chạy thuộc logic advice chèn thêm. Làm sao kiểm chứng?

Đối chiếu loại weaving, mapping/source line, bytecode của đúng artifact, report weaving và điểm vào advice; không dựa duy nhất vào một con số dòng.

**5.** Một phương thức trả `CompletableFuture`, advice ghi “success” ngay khi method trả Future. Có phải tác vụ đã thành công?

Không. Method return và task completion là hai ranh giới khác nhau.

**6.** Hai request cùng gọi một join point. Chỉ dùng chữ ký để log có đủ phân biệt không?

Không. Cần invocation/span ID hoặc correlation ID, và context phải đi theo từng invocation/tác vụ.

**7.** Một after advice ném ngoại lệ sau khi nghiệp vụ đã ghi dữ liệu. Caller nhận lỗi. Có được kết luận dữ liệu chưa ghi không?

Không. Lỗi hậu xử lý có thể xuất hiện sau side effect. Cần xác minh trạng thái và chính sách retry/idempotency.

**8.** Một call site ở hai nơi khác nhau gọi cùng method. Nếu muốn biết vị trí gọi nào đã được can thiệp, metadata tối thiểu là gì?

Ngoài chữ ký, cần định danh **loại join point** và **vị trí call site/shadow** (source location hoặc ID khác do công cụ cung cấp); runtime thêm invocation ID nếu muốn phân biệt các lần gọi.

## 23. Checklist thiết kế và vận hành

- [ ] Biết tool đang dùng compile-time, load-time hay runtime interception.
- [ ] Biết loại join point thực sự được hỗ trợ.
- [ ] Có nguồn xác thực các điểm khớp tĩnh (report hoặc kiểm thử).
- [ ] Có kiểm thử đường chạy thực tế và guard động.
- [ ] Đã xác định cách lấy bản bytecode hoặc representation đã dệt khi cần.
- [ ] Giữ artifact, build ID, cấu hình weaving và mapping đúng phiên bản.
- [ ] Có chính sách giữ debug metadata đủ cho môi trường đích.
- [ ] Debugger có thể phân biệt code nghiệp vụ, advice và frame sinh.
- [ ] Có trace ID/invocation ID cho event trước/sau/ngoại lệ.
- [ ] Không hiểu stack trace là nhật ký toàn bộ advice.
- [ ] Có cách đọc `cause`/`suppressed` để truy nguyên lỗi.
- [ ] Không nhầm success của method trả Future với completion.
- [ ] Không dựa vào thread ID duy nhất để ghép các event.
- [ ] Không để instrumentation log dữ liệu nhạy cảm.
- [ ] Đã cân nhắc chi phí và nguy cơ tái nhập của logging.
- [ ] Có kiểm thử dương/âm khi thay pointcut hoặc refactor.
- [ ] Có phương án kiểm chứng khi production khác môi trường local.

## 24. Kết luận

**Mã đã dệt là biểu diễn thực thi có hiệu lực sau khi hành vi AOP được kết hợp**, không nhất thiết là một tệp source mới. Thông tin gỡ lỗi như dòng nguồn, biến local, source mapping và stack frame giúp kết nối runtime với mã người lập trình viết, nhưng weaving có thể thêm frame, chèn instruction hoặc tạo thành phần trung gian khiến phép ánh xạ không còn đơn giản.

Để truy vết đáng tin cậy, cần kết hợp **chứng cứ cấu trúc** (pointcut, shadow, weaving report, artifact/build ID), **chứng cứ runtime** (invocation, guard, advice order, return/exception) và **chứng cứ debug** (bytecode/source mapping/stack trace). Chỉ khi ba lớp chứng cứ nhất quán, ta mới trả lời được chính xác *điểm nào được dệt, vì sao advice chạy, phần gốc đã thực thi ra sao và caller đã quan sát kết quả nào*.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-009">Phân biệt lỗi biên dịch, lỗi dệt và lỗi thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi biên dịch, lỗi dệt và lỗi thực thi trong kiến trúc AOP

> **Mục tiêu học tập:** Nhận diện ba nhóm sự cố quan trọng khi xây dựng chương trình có lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP): **lỗi biên dịch** (*compilation errors*), **lỗi dệt** (*weaving errors*) và **lỗi thực thi** (*runtime errors*). Hiểu rằng dệt aspect có thể diễn ra ở nhiều thời điểm khác nhau; biết truy nguyên lỗi từ thông báo, giai đoạn pipeline, pointcut, advice, bytecode, class loader và đường thực thi; áp dụng quy trình chẩn đoán có bằng chứng thay vì suy đoán từ việc advice không chạy.

## 1. Tại sao chương trình AOP có thêm một lớp lỗi?

Trong chương trình Java thông thường, người học dễ phân biệt:

- **Biên dịch không thành công:** mã nguồn có lỗi cú pháp, kiểu dữ liệu, khai báo hoặc ràng buộc ngôn ngữ.
- **Chạy chương trình mới phát sinh lỗi:** chẳng hạn một lời gọi ném ngoại lệ hoặc thao tác với dữ liệu không hợp lệ.

Khi áp dụng AOP, còn có hoạt động **weaving**: kết hợp hành vi của aspect với chương trình ở những vị trí được mô hình join point và pointcut cho phép. Từ đây nảy sinh nhóm câu hỏi bổ sung:

- Pointcut có hợp lệ và đúng ngữ nghĩa không?
- Advice có thể được gắn vào loại join point yêu cầu không?
- Bộ dệt có đọc, phân tích và biến đổi được mã liên quan không?
- Những class nào thực sự nằm trong phạm vi weaving?
- Mã sau weaving còn thỏa hợp đồng bytecode/JVM không?
- Advice có thực sự chạy trên đường gọi đang quan sát không?

Một lỗi có thể xuất hiện **ngay khi build**, **khi xử lý bytecode**, **khi nạp class**, hoặc **sau khi chương trình đã chạy**. Điều quan trọng là không đồng nhất **giai đoạn phát hiện** với **bản chất nguyên nhân**.

Ví dụ: một cấu hình weaving sai có thể chỉ bị phát hiện ở thời điểm nạp class. Log xuất hiện trong lúc startup, nhưng nguyên nhân vẫn nằm ở **quy tắc dệt**, không nhất thiết ở logic của phương thức nghiệp vụ.

## 2. Ba nhóm lỗi dưới góc nhìn kiến trúc

| Nhóm | Bản chất chính | Triệu chứng thường gặp | Hướng điều tra đầu tiên |
|---|---|---|---|
| **Lỗi biên dịch** | Mã nguồn không được trình biên dịch chấp nhận theo ngôn ngữ và hệ kiểu | Báo cú pháp sai, không tìm thấy symbol, không tương thích kiểu | Tệp/dòng nguồn, compiler, classpath, phụ thuộc |
| **Lỗi dệt** | Không thể áp dụng hoặc xác nhận được phép kết hợp aspect với mã đích theo cơ chế weaving | Điểm dệt không hợp lệ, binding advice sai, biến đổi class thất bại, hạn chế model/weaver | Pointcut, advice binding, weaving scope, cấu hình và bytecode |
| **Lỗi thực thi** | Chương trình đã vào runtime nhưng luồng điều khiển/dữ liệu dẫn tới lỗi | Ngoại lệ nghiệp vụ, lỗi do advice, recursion, lỗi trạng thái, lỗi liên kết class | Stack trace, dữ liệu runtime, thứ tự advice, đường gọi |

Bảng giúp **phân loại nguyên nhân**, không phải cam kết luôn có ba công cụ hoặc ba pha hoàn toàn tách biệt.

### 2.1. Thời điểm phát hiện có thể trùng nhau

Một bộ dệt hoạt động **ngay trong quá trình biên dịch** có thể báo lỗi của chính hoạt động weaving trong cùng một lệnh build. Một bộ dệt lúc nạp class có thể báo lỗi khi ứng dụng đã khởi động. Do đó:

> **Lỗi dệt không có nghĩa lỗi chỉ xảy ra ở một thời điểm nằm giữa biên dịch và thực thi.**

Ngược lại, một lỗi `ClassNotFoundException` khi nạp class có thể do classpath/deployment sai, không phải do weaving. Cần căn cứ vào thông báo, stack trace, class đang được xử lý và giai đoạn thật sự phát sinh.

## 3. Pipeline khái niệm của một chương trình có AOP

Một cách mô hình hóa phổ biến:

~~~text
Mã nguồn ứng dụng + mã aspect
              |
              v
       Phân tích / biên dịch
              |
              v
        Mã trung gian / bytecode
              |
              v
       Tìm điểm dệt và áp dụng
              |
              v
          Mã đã được dệt
              |
              v
      Nạp class / xác minh / liên kết
              |
              v
        Chạy chương trình
              |
              v
   Advice, phần gốc, caller, I/O...
~~~

Sơ đồ chỉ mang tính **khái niệm**. Trong thực tế, thứ tự và ranh giới có thể thay đổi:

- Một số hệ AOP **dệt trong giai đoạn biên dịch**.
- Một số hệ AOP **dệt mã đã được biên dịch** sau đó.
- Một số hệ AOP **dệt lúc nạp class**.
- Một số cơ chế **chặn lời gọi bằng proxy/interceptor** tại runtime mà không biến đổi bytecode của phương thức gốc theo cùng cách.

Vì thế, việc phân loại lỗi cần gắn với **kiến trúc đang sử dụng**. Không nên buộc một mô hình proxy phải có cùng loại lỗi biến đổi bytecode như một bộ dệt mã.

## 4. Lỗi biên dịch: mã nguồn không hợp lệ

Lỗi biên dịch là lỗi xuất hiện khi trình biên dịch kiểm tra mã theo cú pháp, kiểu và các ràng buộc cần thiết.

Ví dụ mã Java **cố ý sai**, chỉ để minh họa lỗi kiểu:

~~~java
class CompileErrorExample {
    static int calculate() {
        return "not an integer";
    }
}
~~~

Đoạn trên **không biên dịch được** vì phương thức khai báo trả `int` nhưng biểu thức `return` là `String`. Khi chạy `javac CompileErrorExample.java`, trình biên dịch phải từ chối mã. Văn bản và vị trí của thông báo lỗi có thể khác tùy phiên bản và môi trường, nên không gán một stdout/stderr duy nhất như hợp đồng cố định.

**Đây là lỗi Java**, ngay cả khi lớp nằm trong ứng dụng có dùng AOP. Không nên vội điều tra pointcut khi một dòng `return` sai kiểu ngay trong source đã giải thích thất bại.

### 4.1. Những lỗi biên dịch quen thuộc

- Cú pháp không đúng, thiếu dấu hoặc khai báo không hợp lệ.
- Không tìm thấy một lớp, interface, method hoặc biến được sử dụng.
- Kiểu tham số và kiểu trả về không tương thích.
- Mâu thuẫn trong overload, generic hoặc modifier theo quy tắc Java.
- Không tuân thủ quyền truy cập mà compiler kiểm tra.
- Thư viện cung cấp API aspect không nằm đúng trên classpath/module path.
- Mã advice viết bằng Java nhưng khai báo tham số hoặc return không thỏa hợp đồng biên dịch.

### 4.2. Không phải mọi lỗi cú pháp pointcut đều chỉ là lỗi Java

Nếu một mô hình AOP có **ngôn ngữ biểu thức pointcut riêng**, biểu thức sai có thể bị báo bởi bộ phân tích pointcut, trình biên dịch mở rộng hoặc bộ dệt. Nguồn thất bại thuộc **khâu xử lý quy tắc AOP**, dù thông báo được in trong một lệnh có tên “compile”.

Cần xem lớp thành phần nào phát hiện lỗi:

~~~text
Java parser / type checker?
Pointcut parser / validator?
Advice binding checker?
Weaving engine?
JVM verifier / linker?
Application code?
~~~

Đây là cách phân loại hữu ích hơn việc chỉ nhìn tên tiến trình đã dừng.

## 5. Lỗi dệt: không thể kết hợp aspect với vị trí cần can thiệp

**Weaving error** chỉ vấn đề khi phân tích, lựa chọn, gắn hoặc biến đổi mã nhằm thực thi advice ở một join point được hỗ trợ.

Những nguyên nhân có thể xảy ra:

1. **Biểu thức pointcut không hợp lệ** theo cú pháp/đặc tả công cụ.
2. **Ràng buộc tham số advice không khớp** với dữ liệu mà join point có thể cung cấp.
3. **Loại join point không được hỗ trợ** bởi mô hình hoặc một dạng around advice cụ thể.
4. **Không thể đọc, biến đổi hoặc ghi class** thuộc phạm vi dệt.
5. **Phiên bản bytecode hoặc môi trường JVM không tương thích** với công cụ weaving.
6. **Lớp đã được nạp**, và cơ chế hiện tại không cho áp dụng thay đổi theo cách yêu cầu.
7. **Quy tắc weaving xung đột** hoặc vi phạm một ràng buộc do bộ dệt áp đặt.
8. **Class loader/module/access boundary** khiến quá trình kết hợp mã thất bại.
9. **Bytecode được tạo ra không hợp lệ** và bị bộ kiểm tra JVM từ chối, nếu lỗi thực sự bắt nguồn từ mã đã được biến đổi.

Không phải công cụ nào cũng có mọi nguyên nhân trên. Một số lỗi được báo là **warning**, một số là **error**, và có vấn đề chỉ xuất hiện ở runtime.

### 5.1. Pointcut hợp lệ nhưng không khớp không nhất thiết là lỗi dệt

Nếu pointcut chọn `submit*` nhưng chương trình chỉ có `cancelOrder()`, tập khớp có thể rỗng và bộ dệt vẫn hoàn tất thành công.

~~~text
Pointcut hợp lệ + không có join point khớp
    ≠
Pointcut lỗi cú pháp
    ≠
Weaving engine thất bại
~~~

Một pointcut không khớp thường là **vấn đề độ bao phủ hoặc cấu hình**, không tự là ngoại lệ bắt buộc của bộ dệt. Công cụ có thể phát cảnh báo hoặc coi là lỗi tùy chính sách cấu hình, nhưng không được tự gán kết luận.

## 6. Sai cú pháp pointcut so với sai ngữ nghĩa lựa chọn

Xét hai tình huống khái niệm:

**Tình huống A — sai cú pháp:**

~~~text
select method-execution(  // thiếu cấu trúc bắt buộc
~~~

Một parser phù hợp phải từ chối biểu thức theo ngữ pháp của chính công cụ.

**Tình huống B — cú pháp đúng, chọn sai phạm vi:**

~~~text
select executions named "save*"
~~~

Nếu ý định là chọn `submit*`, nhưng quy tắc lại chọn `save*`, biểu thức có thể hợp lệ hoàn toàn. Bộ dệt vẫn chạy; advice chỉ xuất hiện **không đúng chỗ** hoặc **không xuất hiện**.

Bài học:

- **Syntax error:** câu viết không thuộc ngôn ngữ biểu thức hợp lệ.
- **Semantic/configuration mismatch:** câu viết được chấp nhận nhưng diễn đạt sai ý định.
- **Coverage failure:** cơ chế không can thiệp vào đường chạy mong muốn.

Ba vấn đề này có thể có biểu hiện tương tự “không thấy advice”, nhưng cách khắc phục khác.

## 7. Binding của advice có thể không tương thích join point

Một advice có thể cần nhận:

- Đối số của lời gọi.
- Đối tượng đích hoặc receiver.
- Chữ ký của điểm can thiệp.
- Kết quả trả về.
- Ngoại lệ đi ra khỏi một ranh giới.

Nhưng dữ liệu nào có thể bind tùy loại join point và loại advice. Ví dụ:

- Advice chạy **trước** không thể giả định đã có return value của lần thực thi chưa xảy ra.
- Một constructor không có kiểu trả về thông thường như method Java.
- Một field get/set join point (nếu có) không có cùng danh sách tham số như method execution.
- Binding dữ liệu theo tên tham số có thể chịu giới hạn metadata và ngôn ngữ pointcut.

Nếu công cụ kiểm tra được binding lúc dệt, nó có thể từ chối một advice đòi dữ liệu không tồn tại hoặc kiểu không hợp lệ.

**Không được kết luận mọi mismatch sẽ được phát hiện trước runtime.** Một số lỗi chỉ được kiểm tra khi một pointcut động khớp hoặc khi adapter nhận dữ liệu thực tế.

## 8. Không hỗ trợ loại join point khác với không khớp điều kiện

Đây là một phân biệt kiến trúc quan trọng.

**Trường hợp 1 — không hỗ trợ:** Cơ chế chỉ quan sát lời gọi method qua proxy, nhưng yêu cầu là chặn **gán trường trực tiếp**.

**Trường hợp 2 — không khớp:** Mô hình có hỗ trợ method execution, nhưng pointcut chỉ chọn tên `save`, còn phương thức thực tế là `send`.

**Trường hợp 3 — không được dệt:** Công cụ có khả năng xử lý method execution và pointcut đúng, nhưng lớp đích bị loại khỏi weaving scope.

| Trường hợp | Lỗi cốt lõi | Điều cần làm |
|---|---|---|
| Loại join point chưa được hỗ trợ | Giới hạn mô hình | Đổi ranh giới can thiệp hoặc cơ chế phù hợp |
| Điểm được hỗ trợ nhưng không khớp | Điều kiện pointcut | Sửa biểu thức lựa chọn, kiểm tra ca dương/âm |
| Điểm khớp nhưng mã chưa được dệt | Weaving scope/cấu hình | Xác minh danh sách class, class loader, thứ tự xử lý |
| Điểm được dệt nhưng chưa xảy ra | Luồng thực thi | Kích hoạt đúng ca chạy, kiểm tra điều kiện động |
| Advice đã chạy và ném lỗi | Hành vi runtime | Đọc stack trace, xử lý return/throw và thứ tự advice |

Đừng lấy việc “không có log” làm bằng chứng bộ dệt thất bại. Đầu tiên phải chứng minh có một **sự kiện phù hợp** xảy ra trên **đường đã được can thiệp**.

## 9. Compile-time weaving: lỗi dệt xuất hiện ngay lúc build

Với **compile-time weaving**, quá trình xử lý aspect có thể diễn ra cùng giai đoạn biên dịch source. Một lệnh build có thể vừa:

1. Phân tích cú pháp và kiểm tra kiểu của source.
2. Phân tích pointcut và advice.
3. Xác định những vị trí thích hợp để kết hợp mã.
4. Sinh bytecode đã được dệt.

Nếu bước 1 lỗi, có thể là lỗi biên dịch Java. Nếu bước 2/3/4 lỗi, nguyên nhân có thể là **weaving**, nhưng vẫn nằm trong **cùng phiên build**.

### 9.1. Hậu quả với cách đọc log

Khi một lệnh build báo “failed”, hãy kiểm tra:

- Thông báo chỉ ra lỗi ở **source Java** hay biểu thức pointcut?
- Stack trace bắt đầu ở **compiler** hay **weaving component**?
- Vấn đề liên quan một class cụ thể đang được biến đổi?
- Có báo “không thể áp dụng advice”, “binding mismatch”, “không hỗ trợ version” hay không?
- Có nhiều lỗi: lỗi đầu tiên khiến các lỗi tiếp theo chỉ là hậu quả dây chuyền?

Chỉ sau khi xác định giai đoạn gây lỗi mới chọn đúng hướng sửa.

## 10. Post-compile / binary weaving: mã đã biên dịch nhưng chưa dệt được

Một số kiến trúc dệt **bytecode đầu vào đã được biên dịch**. Khi đó:

~~~text
Source Java hợp lệ
        |
        v
    .class ban đầu
        |
        v
   Binary weaving
        |
        v
   .class được dệt
~~~

Một dự án có thể `javac` thành công nhưng bước dệt binary thất bại vì:

- Class file có cấu trúc/phiên bản không tương thích với bộ dệt.
- Một thư viện phụ thuộc hoặc class metadata cần để phân tích chưa có.
- Quá trình ghi output bị hạn chế quyền hoặc cấu hình sai.
- Một quy tắc aspect không áp dụng hợp lệ lên bytecode được chọn.
- Bộ dệt gặp trường hợp mã sinh hoặc tổ hợp class chưa được hỗ trợ.

**Không nên sửa source Java chỉ vì bước sau biên dịch báo lỗi** nếu log đã chỉ ra lỗi nằm ở compatibility hoặc cấu hình dệt. Cần cô lập input/output của bộ dệt và xác minh chính xác class bị ảnh hưởng.

### 10.1. Hỏi bộ dệt đã xử lý mã nào?

Có ba trạng thái khác nhau:

~~~text
Không tìm thấy class cần xử lý
Tìm thấy class nhưng không có join point match
Tìm thấy class và match, nhưng dệt thất bại
~~~

Chúng không thể được xử lý bằng cùng một cách. Một tình huống không match có thể hoàn toàn hợp lệ, còn tình huống thứ ba là thất bại kỹ thuật thực sự.

## 11. Load-time weaving: lỗi dệt có thể xuất hiện khi chương trình khởi động

Với **load-time weaving**, bộ dệt xử lý class khi nó đang được nạp hoặc khi một cơ chế instrumentation phù hợp can thiệp vào quá trình tải class.

Có thể mô tả:

~~~text
Application starts
      |
      v
Class loader requests Class X
      |
      v
Weaving / transform hook
      |
      v
Class verifier and linker
      |
      v
Class usable by runtime
~~~

Vì vậy, stack trace ở startup có thể chứa lỗi từ bộ dệt. Không có lý do để gọi mọi lỗi trong startup là “lỗi logic runtime của business”.

### 11.1. Cấu hình nạp lớp

Các vấn đề thường cần kiểm tra:

- Cơ chế weaving đã được kích hoạt đúng cách chưa?
- Class có được nạp bởi class loader nằm trong phạm vi dự kiến không?
- Class có bị nạp **trước** khi cơ chế weaving có hiệu lực không?
- Những class/library có thể bị loại khỏi vùng dệt không?
- Cơ chế có hỗ trợ class file version mà compiler đang phát sinh không?
- Có nhiều phiên bản của cùng loại runtime/API AOP trên classpath không?
- JVM có cho phép biến đổi class ở giai đoạn được yêu cầu không?

Một ứng dụng có thể hoạt động với lời gọi thông thường nhưng **không có advice** nếu những class quan trọng không đi qua cơ chế dệt. Đó có thể là **mismatch về phạm vi** thay vì lỗi có exception rõ ràng.

### 11.2. Khi nào cần quan tâm việc class đã nạp?

Một số kỹ thuật có thể biến đổi class ở thời điểm nạp nhưng không tự hỗ trợ mọi trường hợp **retransform** hoặc thay đổi cấu trúc sau khi class đã được định nghĩa. Cần kiểm tra điều kiện thực tế của công cụ, không giả định bật cấu hình vào bất kỳ lúc nào cũng dệt lại mã đã nạp.

## 12. JVM verification, linkage và lỗi “nằm ở ranh giới”

Sau khi mã được dệt, JVM còn có các giai đoạn xác minh và liên kết. Những lỗi như **VerifyError**, **ClassFormatError**, **NoClassDefFoundError**, **NoSuchMethodError** hoặc **IllegalAccessError** có thể xuất hiện ở những tình huống khác nhau.

**Không phải cứ gặp các tên lỗi đó là lỗi dệt.** Cần xem nguyên nhân:

| Hiện tượng | Những khả năng phải phân biệt |
|---|---|
| `VerifyError` | Bytecode không thỏa quy tắc xác minh; có thể do biến đổi không hợp lệ, cũng có thể do input/công cụ khác |
| `ClassFormatError` | Cấu trúc class file không hợp lệ; xác định class gốc hay class sau biến đổi |
| `NoClassDefFoundError` | Class cần thiết không có ở thời điểm liên kết/thực thi hoặc khởi tạo class trước đó thất bại |
| `NoSuchMethodError` | Mã biên dịch tham chiếu một method không có trong phiên bản class thực tế; thường liên quan mismatch nhị phân |
| `IllegalAccessError` | Vi phạm điều kiện truy cập trong quá trình liên kết/thực thi |
| `ExceptionInInitializerError` | Khởi tạo class bằng static initializer thất bại; có thể là lỗi logic hoặc tương tác với mã dệt |

Nếu bytecode nguyên gốc chạy hợp lệ nhưng cùng input sau weaving gây `VerifyError`, chứng cứ hướng tới **vấn đề biến đổi / tương thích bytecode**. Tuy nhiên muốn kết luận cần kiểm tra thật sự hai artifact tương ứng và tránh nhầm classpath.

## 13. Lỗi thực thi: khi chương trình đã vào luồng xử lý

Sau khi phần cần thiết được biên dịch, dệt và nạp thành công, chương trình vẫn có thể gặp lỗi:

- `NullPointerException`, `IllegalArgumentException` hoặc lỗi nghiệp vụ.
- Advice đọc đối số theo vị trí sai hoặc ép kiểu sai.
- Advice thiếu kiểm tra `null` đối với target hoặc argument.
- Around advice **quên gọi continuation/proceed**, khiến nghiệp vụ bị bỏ qua.
- Around advice **gọi proceed nhiều lần**, tạo tác dụng phụ lặp khi cơ chế cho phép.
- Advice ghi log hoặc serialize object rồi tự ném lỗi.
- Advice thay return value không tương thích hoặc che mất ngoại lệ gốc.
- Các advice lồng nhau tương tác sai về thứ tự, ngữ cảnh hoặc trạng thái.
- Một lời gọi bị tái chặn gây đệ quy và cạn stack.
- Cạnh tranh dữ liệu hoặc rò trạng thái khi chạy đồng thời.

Đây là các ví dụ về **hành vi runtime**, nhưng nguồn gây lỗi có thể nằm trong advice hoặc trong nghiệp vụ gốc. Vì vậy cần phân biệt **“ngoại lệ của ứng dụng có AOP”** với **“ngoại lệ do chính aspect gây ra”**.

### 13.1. Runtime exception không đồng nghĩa lỗi dệt

Nếu aspect đã được dệt chính xác và advice dùng `args[2]` trong khi chỉ có hai đối số, `ArrayIndexOutOfBoundsException` phát sinh là lỗi của **logic advice lúc chạy**.

Bộ dệt có thể hoàn toàn đúng khi đưa advice vào join point. Không nên “sửa cấu hình weaving” trước khi kiểm tra phép truy cập mảng.

## 14. Thứ tự thời điểm: lỗi ở phần gốc và lỗi ở advice

Xét chuỗi:

~~~text
Caller
  |
  v
Around advice A
  |
  v
Before advice B
  |
  v
Original method
  |
  v
After/finally advice C
  |
  v
Caller
~~~

Lỗi có thể xảy ra ở nhiều nơi:

1. **A ném trước proceed:** B và phần gốc có thể chưa chạy.
2. **B ném trước phần gốc:** phương thức nghiệp vụ có thể chưa được gọi.
3. **Phần gốc ném lỗi:** lỗi có thể được C/A quan sát nếu semantics và cấu trúc bao cho phép.
4. **C ném trong cleanup:** lỗi mới có thể che mất lỗi từ phần gốc.
5. **A bắt rồi chuyển lỗi:** caller có thể thấy lỗi đã được chuyển dạng, không phải exception ban đầu.

Không có một thứ tự advice chung áp dụng cho mọi cơ chế; ví dụ trên minh họa một **chuỗi giả định đã được sắp xếp**.

### 14.1. Dấu hiệu trong stack trace

Nếu stack trace có lớp trung gian/proxy hoặc mã đã dệt, không có nghĩa thành phần đó là nguyên nhân. Nó có thể chỉ là **khung bao** mà ngoại lệ nghiệp vụ đi xuyên qua.

Cần tìm **điểm tạo lỗi đầu tiên có ý nghĩa**, thông điệp và chuỗi `cause` hoặc `suppressed`; không chỉ chọn khung đầu tiên có tên “interceptor”.

## 15. Ví dụ Java 17 hoàn chỉnh: mô phỏng lỗi cấu hình can thiệp và lỗi runtime

Ví dụ dưới đây tạo một mô hình **đơn giản, tự chứa** để quan sát khác biệt:

- **Kiểm tra kế hoạch can thiệp trước khi tạo proxy**: một mô phỏng lỗi ở bước chuẩn bị/dệt khi yêu cầu một method không có.
- **Tạo proxy hợp lệ**: phương thức mục tiêu tồn tại và adapter hoạt động.
- **Nghiệp vụ ném lỗi tại runtime**: một lời gọi có `amount` âm gây lỗi.
- **Đường gọi trực tiếp không qua proxy**: nghiệp vụ vẫn chạy, nhưng không in dòng intercept.

**Chú ý:** Java Dynamic Proxy trong ví dụ **không thực hiện bytecode weaving**. Hàm `validatePlan` chỉ mô phỏng việc kiểm tra một kế hoạch dệt để dạy cách phân loại lỗi. Không gọi kết quả này là lỗi của một bộ dệt AOP cụ thể.

Lưu file **`WeavingDiagnosticsDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class WeavingDiagnosticsDemo {
    public interface Billing {
        String charge(int amount);
    }

    static final class BillingImpl implements Billing {
        @Override
        public String charge(int amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("negative amount");
            }
            return "OK:" + amount;
        }
    }

    record ObservationPlan(String methodName, int parameterCount) {}

    static void validatePlan(Class<?> contract, ObservationPlan plan) {
        if (!contract.isInterface()) {
            throw new IllegalArgumentException("contract must be interface");
        }
        boolean found = false;
        for (Method method : contract.getMethods()) {
            if (method.getName().equals(plan.methodName())
                    && method.getParameterCount() == plan.parameterCount()) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException(
                    "no matching method: " + plan.methodName()
                    + "/" + plan.parameterCount());
        }
    }

    static Billing wrap(Billing target, ObservationPlan plan) {
        validatePlan(Billing.class, plan);
        return (Billing) Proxy.newProxyInstance(
                Billing.class.getClassLoader(),
                new Class<?>[] {Billing.class},
                (proxy, method, args) -> {
                    System.out.println("[intercept] " + method.getName());
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                }
        );
    }

    public static void main(String[] args) {
        System.out.println("phase: configuration");

        try {
            validatePlan(
                    Billing.class, new ObservationPlan("refund", 1));
        } catch (IllegalArgumentException error) {
            System.out.println("[plan-error] " + error.getMessage());
        }

        Billing target = new BillingImpl();
        Billing observed = wrap(
                target, new ObservationPlan("charge", 1));

        System.out.println("[setup] ok");
        System.out.println("[success] " + observed.charge(12));

        try {
            observed.charge(-1);
        } catch (IllegalArgumentException error) {
            System.out.println("[runtime-error] " + error.getMessage());
        }

        System.out.println("[direct] " + target.charge(4));
    }
}
~~~

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac WeavingDiagnosticsDemo.java
java WeavingDiagnosticsDemo
~~~

**Stdout chính xác:**

~~~text
phase: configuration
[plan-error] no matching method: refund/1
[setup] ok
[intercept] charge
[success] OK:12
[intercept] charge
[runtime-error] negative amount
[direct] OK:4
~~~

### 15.1. Tại sao đây không phải minh họa một lỗi biên dịch thật?

Tệp Java hợp lệ nên `javac` có thể tạo class thành công. Kế hoạch sai `refund/1` chỉ được chương trình kiểm tra **khi chạy `main`**. Nó mô phỏng một phép kiểm tra ở **giai đoạn chuẩn bị cơ chế can thiệp**, không phải một compiler error và cũng không phải lỗi dệt bytecode thật.

Nếu một công cụ AOP có phân tích binding/pointcut ở compile-time thì chính sách tương ứng có thể bị bác bỏ sớm hơn. Cách thực hiện cụ thể tùy công cụ.

### 15.2. Phân biệt plan-error và runtime-error

`[plan-error] no matching method: refund/1` nghĩa là **yêu cầu chuẩn bị điểm can thiệp** không tìm thấy phương thức tương ứng theo quy tắc đơn giản trong demo.

`[runtime-error] negative amount` nghĩa là **nghiệp vụ `charge` đã được gọi** với dữ liệu không hợp lệ và tự ném `IllegalArgumentException`.

Hai dòng có thể cùng xuất hiện trong một lần chạy nhưng nằm ở **hai loại nguyên nhân khác nhau**. Không nên sửa pointcut khi lỗi thực tế là `amount < 0`.

### 15.3. Vì sao cần xử lý `InvocationTargetException`?

`Method.invoke` dùng reflection có thể bọc ngoại lệ của method đích trong `InvocationTargetException`. Handler giải lớp bọc và ném lại `wrapped.getCause()`.

Điều này giúp caller nhận `IllegalArgumentException("negative amount")` trong ví dụ. Nếu không giải bọc đúng, hiện tượng runtime có thể bị biến dạng thành lỗi reflection/proxy. Đây là đặc điểm của **adapter dùng reflection**, không phải mọi weaving engine đều xử lý ngoại lệ giống cách này.

### 15.4. Trường hợp direct cho thấy điều gì?

`target.charge(4)` trả `OK:4` mà không có dòng `[intercept]`. Nguyên nhân là đường gọi **không đi qua proxy**.

Đây **không phải lỗi biên dịch**, **không phải lỗi của bộ dệt bytecode**, và **không phải bằng chứng method không được chạy**. Nó là sự khác biệt giữa **đường đi qua cơ chế chặn** và **đường gọi trực tiếp**.

Nếu một chương trình yêu cầu mọi lời gọi đều được ghi, phải kiểm tra kiến trúc điểm can thiệp, không chỉ tìm exception.

## 16. Lỗi không xuất hiện dưới dạng exception: “advice biến mất”

Một trong những trường hợp khó nhất là chương trình **vẫn chạy và trả kết quả đúng** nhưng advice không xuất hiện như dự kiến.

Có ít nhất sáu khả năng:

1. Pointcut hợp lệ nhưng không khớp với tên/chữ ký.
2. Điều kiện động không đúng ở lần gọi.
3. Loại join point cần quan sát không nằm trong mô hình công cụ.
4. Class nằm ngoài vùng weaving hoặc được nạp qua đường khác.
5. Caller đi thẳng tới target thay vì proxy/interceptor.
6. Nhánh mã có join point chưa từng được thực thi trong ca thử.

Ngoài ra, còn có thể có vấn đề ở **kênh log**: advice đã chạy nhưng mức log bị lọc, sink không ghi, dữ liệu bị drop hoặc sự kiện ghi không đúng nhãn.

Vì vậy, “không thấy log” chỉ là **triệu chứng**. Hãy thiết kế một advice/điểm kiểm thử có thể quan sát bằng bộ đếm hoặc sự kiện độc lập, đồng thời kiểm tra trực tiếp phần gốc có được gọi hay không.

## 17. Quy trình điều tra khi advice không chạy

Thực hiện từ đơn giản đến phức tạp:

1. **Xác nhận code path:** dòng nghiệp vụ dự kiến có thật sự được chạy?
2. **Xác nhận loại join point:** điểm đang cần chặn là method call, execution, constructor, field access hay sự kiện khác?
3. **Kiểm tra support:** cơ chế hiện tại có hỗ trợ loại điểm ấy không?
4. **Kiểm tra pointcut:** tên, chữ ký, metadata, điều kiện AND/OR/NOT có đúng không?
5. **Kiểm tra điều kiện động:** giá trị đối số/receiver/cflow có khớp lần chạy này không?
6. **Kiểm tra weaving scope:** class/package/module có nằm trong vùng xử lý?
7. **Kiểm tra class loading:** class đã được nạp trước lúc dệt chưa; runtime có đúng cấu hình?
8. **Kiểm tra runtime path:** lời gọi có đi qua proxy/adapter nếu cơ chế yêu cầu?
9. **Kiểm tra advice:** có thực thi nhưng log bị lọc hoặc bị lỗi trước khi in không?
10. **Kiểm tra sự phối hợp:** advice bao ngoài có short-circuit khiến advice phía trong không chạy không?

Không nên thay đồng thời pointcut, class loader, thứ tự advice và logic nghiệp vụ; như vậy sẽ đánh mất khả năng xác định **thay đổi nào giải quyết vấn đề**.

## 18. Quy trình điều tra lỗi dệt có thông báo rõ ràng

Khi công cụ báo không thể weave, hãy giữ nguyên văn những thông tin sau:

- **Văn bản lỗi và mã lỗi**, nếu có.
- **Giai đoạn**: compile-time, binary weaving hay load-time.
- **Class hoặc vị trí mã** đang được xử lý.
- **Pointcut/advice** được cho là liên quan.
- **Compiler, JVM, weaving runtime** và phiên bản phù hợp.
- **Classpath/module path/class loader** có liên quan.
- **Nguyên nhân gốc** trong chuỗi cause, không chỉ thông báo cấp cao.
- **Artifact đầu vào và đầu ra** nếu có thể giữ an toàn để đối chiếu.

Sau đó phân nhóm:

~~~text
Ngôn ngữ / cú pháp?
Binding / loại join point?
Khả năng hỗ trợ / phiên bản bytecode?
Phạm vi weaving / loading?
Tạo bytecode / verification / linkage?
Lỗi riêng của mã nghiệp vụ sau khi advice chạy?
~~~

Nếu có lỗi verification của class đã được xử lý, cần kiểm tra cả **artifact trước** và **sau weaving**, cùng JVM/tool versions. Không tự sửa logic nghiệp vụ khi chưa chứng minh code nghiệp vụ là nguồn lỗi.

## 19. Chẩn đoán với công cụ Java phổ biến

Các công cụ Java có thể giúp phân tách nguồn lỗi dù không phụ thuộc một framework AOP.

### 19.1. Kiểm tra compile Java

~~~bash
javac -Xlint:all WeavingDiagnosticsDemo.java
~~~

Lệnh trên yêu cầu `javac` hiển thị cảnh báo bổ sung mà compiler hỗ trợ, không biến compiler Java chuẩn thành một engine weaving AOP. Một file compile được không chứng minh pointcut, aspect hay cấu hình dệt đều đúng.

### 19.2. Xem bytecode

~~~bash
javap -c -p WeavingDiagnosticsDemo
~~~

`javap` giúp xem bytecode của class có thể truy cập được. Nếu có artifact **trước và sau weaving** dưới dạng riêng biệt, việc so sánh bytecode có thể hỗ trợ xác định mã dệt đã được áp dụng. Không có thay đổi trong một class cụ thể chưa tự chứng minh toàn bộ hệ thống không được weaving; có thể điểm can thiệp nằm ở class/call site khác.

### 19.3. Theo dõi class loading trên JDK hiện đại

~~~bash
java -Xlog:class+load=info WeavingDiagnosticsDemo
~~~

Lệnh minh họa yêu cầu JVM ghi thêm thông tin class loading. Output có thể rất dài và phụ thuộc môi trường; vì vậy **không đưa stdout cố định** cho lệnh này. Log tải class giúp tìm tên/nguồn của class được nạp, nhưng không tự cho biết pointcut có match hay advice có chạy hay không.

### 19.4. Kiểm tra JVM verification

Một số tình huống có thể thử:

~~~bash
java -Xverify:all WeavingDiagnosticsDemo
~~~

Lệnh yêu cầu JVM xác minh bytecode theo chế độ được hỗ trợ. Nó có thể giúp phát hiện một số lỗi class file; không bảo đảm chứng minh toàn bộ ngữ nghĩa AOP đúng.

**Nguyên tắc:** dùng công cụ để **thu thập bằng chứng theo giả thuyết**, không chạy hàng loạt tùy chọn rồi gắn nhãn lỗi theo dòng log xuất hiện đầu tiên.

## 20. Cẩn thận với lỗi classpath và binary compatibility

Một hệ thống có thể:

1. Biên dịch với phiên bản thư viện A.
2. Dệt với metadata/phiên bản B.
3. Chạy với phiên bản C.

Nếu các phiên bản không tương thích, lỗi có thể xuất hiện dưới dạng **linkage error** khi nạp hoặc gọi method.

Một `NoSuchMethodError` có thể bắt nguồn từ việc class thực tế ở runtime **không còn method** mà bytecode đang tham chiếu. Có thể do thư viện bị thay, khác nhánh build, class loader lấy sai bản hoặc quá trình tạo mã. **Không có lý do để tự kết luận lỗi pointcut** chỉ vì dự án có dùng AOP.

Một quy trình xác minh hữu ích:

- Cùng một bộ artifact đã build có được dùng khi weave và run không?
- Có jar/class trùng tên trên classpath hoặc module path không?
- Compiler và runtime dùng JDK tương thích không?
- Nếu có mã sinh/dệt, output cuối có được đóng gói và triển khai đúng bản?
- Lỗi bắt đầu trước hay sau lúc class được tải/định nghĩa?

## 21. Khi nào runtime error thật sự do advice?

Một số dấu hiệu thường gặp:

### 21.1. Kiểu đối số không như giả định

Advice đọc đối số thứ hai rồi ép sang `String`, nhưng thực tế là `Integer`: xảy ra `ClassCastException` nếu code không kiểm tra kiểu.

**Khắc phục:** binding theo descriptor rõ, kiểm tra kiểu, không suy từ tên method.

### 21.2. `null` hoặc object lifecycle

Advice đọc `this` hoặc target ngay tại giai đoạn khởi tạo mà đối tượng chưa có trạng thái mong đợi: có thể xuất hiện NPE hoặc phá bất biến.

**Khắc phục:** xác định loại join point và thời điểm quan sát chính xác.

### 21.3. Advice gọi lại chính điểm đang bị chặn

Một advice gọi trực tiếp một method qua cùng ranh giới interceptor, dẫn tới vòng lặp advice → method → advice → method.

**Khắc phục:** dùng continuation/`proceed` đúng hợp đồng; phân biệt lời gọi mới với tiếp tục join point hiện tại.

### 21.4. Advice tự ném lỗi khi logging

Serialization hoặc `toString()` trên đối số ném lỗi, khiến caller nhận lỗi quan sát thay vì kết quả gốc.

**Khắc phục:** log tối thiểu, masking, chính sách lỗi rõ, kiểm thử exception path.

### 21.5. Quên proceed hoặc gọi nhiều lần

Một around advice trả giá trị thay thế mà không tiếp tục, hoặc retry không kiểm soát, làm mất/thêm tác dụng phụ.

**Khắc phục:** kiểm tra số lần phần gốc chạy và tính idempotent, không chỉ dựa vào số lần advice chạy.

### 21.6. Lỗi thứ tự khi nhiều advice kết hợp

Một advice thiết lập context trong before, advice khác đọc context khi context chưa được thiết lập hoặc đã bị cleanup.

**Khắc phục:** mô tả lớp ngoài/lớp trong, kiểm chứng thứ tự có thực, dùng cấu trúc lưu/phục hồi an toàn.

## 22. Một chương trình compile thành công nhưng không có advice: có phải lỗi runtime?

Không nhất thiết. Hãy phân biệt:

~~~text
Trạng thái A: chương trình compile được, pointcut không match.
Trạng thái B: pointcut match, nhưng vùng class không được weave.
Trạng thái C: đã weave, nhưng code path chưa chạy.
Trạng thái D: điểm đã chạy, advice đã chạy, nhưng sink không ghi log.
Trạng thái E: advice chạy và ném lỗi.
~~~

Chỉ trạng thái E chắc chắn có **lỗi runtime ở advice**. Trạng thái A/B thường là vấn đề cấu hình/lựa chọn/bao phủ. Trạng thái C có thể là hành vi bình thường. Trạng thái D là vấn đề observability.

Vì vậy nhãn chẩn đoán không chỉ là “compile/weave/runtime” mà còn cần nêu **đã xảy ra điều gì** và **điểm can thiệp nào**.

## 23. Lưu ý về cảnh báo (warning) và lỗi nghiêm trọng (error)

Một số bộ phân tích pointcut có thể báo:

- Không tìm thấy điểm khớp cho mẫu quá hẹp.
- Một điều kiện cấu trúc không thể khớp với loại phần tử nào đó.
- Một giới hạn công cụ hoặc một khả năng không được hỗ trợ.
- Một lời cảnh báo về kiểu, metadata hoặc hành vi có khả năng không an toàn.

Không phải warning nào cũng làm build thất bại. Một hệ CI có thể cấu hình **warnings as errors**, biến cảnh báo thành nguyên nhân dừng pipeline.

Khi gặp một dòng cảnh báo, nên xác minh:

1. Nó biểu thị điều kiện **chắc chắn sai**, khả năng sai hay chỉ thông tin?
2. Cấu hình hiện tại có yêu cầu build phải dừng không?
3. Điểm bị cảnh báo có thật sự nằm trong yêu cầu độ bao phủ hay không?
4. Một warning không match được xem là lỗi bởi **chính sách dự án**, hay bởi đặc tả công cụ?

Không tùy tiện tắt cảnh báo để pipeline xanh nếu nó báo rằng chính sách kiểm soát quan trọng không được gắn.

## 24. Không phải mọi công cụ AOP đều biến đổi bytecode

Một cơ chế dựa trên **proxy/interceptor** có thể thiết lập đối tượng trung gian rồi chặn các lời gọi đi qua nó mà không nhất thiết biến đổi class đích như một bộ dệt bytecode truyền thống.

Do đó, trong một kiến trúc proxy:

- Lỗi tạo proxy hoặc cấu hình interceptor tương ứng với **lỗi thiết lập cơ chế can thiệp**.
- Lời gọi không đi qua proxy là **giới hạn đường gọi**, không tự là lỗi weaving bytecode.
- Lỗi trong handler tại runtime là **lỗi logic/interceptor**.
- Class không có interface hoặc phương thức không được đường proxy hỗ trợ có thể là **giới hạn thiết kế**, không phải một lỗi cú pháp pointcut.

Nên sử dụng khái niệm **lỗi dệt** cho hệ có weaving theo nghĩa rõ ràng. Khi giải thích rộng cho nhiều mô hình, có thể nói **lỗi cấu hình/thiết lập can thiệp** để tránh gán nhãn bytecode weaving sai cho proxy.

## 25. Kiểm thử theo ba tầng để xác định sớm lỗi

### 25.1. Tầng biên dịch và tính hợp lệ cấu trúc

- Mã Java biên dịch bằng JDK mục tiêu.
- Source có kiểu, khai báo, phụ thuộc và module path đúng.
- Biểu thức AOP được kiểm tra bởi compiler/parser tương ứng khi được hỗ trợ.
- Advice binding và loại join point có hợp đồng rõ.

**Mục tiêu:** loại lỗi cú pháp, kiểu và mô tả quy tắc can thiệp sớm nhất có thể.

### 25.2. Tầng weaving / cấu hình can thiệp

- Bộ dệt chạy với đúng phiên bản/cấu hình.
- Những class/call site cần thiết nằm trong phạm vi.
- Output sau weaving có thể được nạp và xác minh.
- Có ca kiểm tra dương: một điểm chắc chắn phải được can thiệp.
- Có ca kiểm tra âm: một điểm chắc chắn không được can thiệp.
- Cảnh báo về thiếu match được đánh giá theo yêu cầu nghiệp vụ.

**Mục tiêu:** chứng minh cơ chế thực sự kết hợp advice vào những nơi dự định.

### 25.3. Tầng runtime/integration

- Advice thực thi tại đúng join point đã chọn.
- Return value và exception của phần gốc được bảo toàn khi aspect chỉ quan sát.
- Around advice gọi `proceed` đúng số lần theo chính sách.
- Dữ liệu động/overload/annotation/`null` không làm advice lỗi.
- Thread lồng nhau/đồng thời và tác vụ bất đồng bộ không rò ngữ cảnh.
- Không có vòng lặp chặn lại ngoài ý muốn.
- Logging không làm lộ dữ liệu và không phá nghiệp vụ.

**Mục tiêu:** kiểm tra hành vi cuối cùng, không chỉ kiểm tra build hoặc việc có mã dệt.

## 26. Ma trận tình huống chẩn đoán

| Hiện tượng | Nhóm nghi ngờ đầu tiên | Bằng chứng cần lấy |
|---|---|---|
| `javac` từ chối kiểu trả về | Biên dịch Java | Vị trí source và kiểu khai báo |
| Pointcut parser báo ký pháp không hợp lệ | Xử lý biểu thức AOP | Dòng quy tắc và đặc tả cú pháp |
| Advice binding yêu cầu dữ liệu không có | Phân tích/binding weaving | Loại join point và tham số advice |
| Binary weaver không đọc được class version | Weaving/compatibility | Class version, JDK và phiên bản công cụ |
| Startup thất bại khi transform class | Load-time weaving | Stack trace transformer, class loader, class đích |
| `VerifyError` sau biến đổi | Bytecode verification, có thể do weaving | Artifact trước/sau và nguyên nhân verifier |
| `NoSuchMethodError` khi gọi | Binary linkage | Classpath, artifact runtime, signature |
| Ứng dụng chạy nhưng không thấy advice | Pointcut/weaving/path/logging | Một test dương/âm và đường đi thật |
| Lời gọi qua proxy có log; gọi target trực tiếp không | Runtime path của proxy | Tham chiếu object ở caller |
| Advice ném `NullPointerException` | Runtime logic | Stack trace, dữ liệu join point tại lần gọi |
| Nguyên nghiệp vụ ném nhưng caller thấy lỗi logger | Advice error handling | Cause/suppressed, khối finally |
| Around advice gọi phần gốc hai lần | Runtime continuation | Bộ đếm trong phần gốc, thứ tự advice |

Ma trận chỉ đưa ra **hướng điều tra ban đầu**. Phân loại cuối cùng phải dựa trên bằng chứng của hệ thống cụ thể.

## 27. Một quy trình chẩn đoán end-to-end

Khi nhận báo cáo “Aspect không hoạt động”, có thể dùng quy trình sau:

1. **Ghi triệu chứng chính xác:** build fail, startup fail, một lời gọi fail hay chỉ thiếu log?
2. **Xác định kiến trúc:** weaving lúc biên dịch, binary weaving, load-time weaving hay proxy/interceptor?
3. **Tái hiện tối thiểu:** một class, một pointcut, một advice, một lời gọi rõ ràng.
4. **Xác minh Java compilation:** mã nguồn/khả năng resolve kiểu và thư viện đã hợp lệ chưa?
5. **Xác minh phân tích AOP:** cú pháp pointcut, join point model, binding và warning.
6. **Xác minh weaving scope:** loại class, thời điểm nạp, class loader, phiên bản bytecode.
7. **Xác minh kết quả:** có artifact đã dệt hoặc bằng chứng adapter/proxy được thiết lập không?
8. **Xác minh runtime path:** caller có thật sự đi qua ranh giới được quan sát?
9. **Xác minh thứ tự thực thi:** ai bao ai, proceed có chạy hay short-circuit?
10. **Xác minh return/throw:** lỗi đến từ phần gốc, advice, logger hay liên kết class?
11. **Đưa vào kiểm thử hồi quy:** ca dương, ca âm, lỗi runtime và giới hạn độ bao phủ.
12. **Ghi lại nguyên nhân gốc:** phân biệt nguồn lỗi với triệu chứng và pha phát hiện.

**Không thay nhiều biến một lúc.** Ví dụ, nếu sửa pointcut, nâng JVM và thay class loader đồng thời rồi vấn đề biến mất, kết quả khó chứng minh nguyên nhân.

## 28. Lỗi dệt có thể bị phát hiện muộn — hãy nêu nhãn hai chiều

Một lỗi có thể có **bản chất weaving** nhưng **được phát hiện khi chạy**. Hữu ích khi báo cáo theo hai chiều:

~~~text
Nguồn lỗi:    cấu hình/pointcut/weaving/bytecode/runtime logic
Pha phát hiện: compile / binary transform / class loading / invocation
~~~

Ví dụ:

~~~text
Nguồn lỗi:    bytecode sau biến đổi không hợp lệ
Pha phát hiện: JVM class verification ở lúc class loading
~~~

Mô tả đó rõ hơn câu “lỗi runtime” vốn quá rộng.

Một ví dụ khác:

~~~text
Nguồn lỗi:    advice đọc nhầm chỉ số đối số
Pha phát hiện: invocation runtime
~~~

Đây thực sự là lỗi logic advice; không nên dồn nó vào “weaving failed” chỉ vì aspect có liên quan.

## 29. Bài tập tự kiểm tra

### Bài 1: Compile-time weaver báo lỗi

Một lệnh build dùng compiler có tích hợp weaving báo rằng binding của advice không phù hợp join point. Có nên nói đây là lỗi Java syntax thông thường không?

**Đáp án:** không. Lỗi xuất hiện trong build nhưng nguyên nhân thuộc **ràng buộc AOP/weaving**. Cần xem thông báo binding và đặc tả join point.

### Bài 2: Không có điểm khớp

Pointcut hợp lệ nhưng không khớp phương thức nào. Bộ dệt không báo lỗi nghiêm trọng. Đây có chắc là bug của weaver không?

**Đáp án:** không. Tập match rỗng có thể là kết quả hợp lệ; cần kiểm tra ý định pointcut và mức độ bắt buộc của độ bao phủ.

### Bài 3: Startup không nạp được class

Một class chỉ bị lỗi khi load-time transformer xử lý. Bạn cần kiểm tra gì?

**Đáp án:** stack trace transformer, class đang tải, class loader, phiên bản class/JVM, phạm vi weaving và artifact trước/sau nếu có. Không tự kết luận nghiệp vụ method đã chạy.

### Bài 4: Advice không in log qua target

`proxy.charge(10)` có dòng intercept, `target.charge(10)` không. Có phải Java compiler làm sai không?

**Đáp án:** không. Đây là giới hạn **đường gọi qua proxy**; gọi target trực tiếp không đi qua handler.

### Bài 5: Lỗi từ nghiệp vụ

Advice chạy, rồi `charge(-1)` ném `IllegalArgumentException` vì số tiền âm. Nhóm lỗi nào?

**Đáp án:** lỗi runtime do **hợp đồng/dữ liệu của nghiệp vụ gốc**; không chứng minh lỗi weaving.

### Bài 6: VerifyError sau dệt

Class ban đầu được JVM chấp nhận; bản sau biến đổi gây `VerifyError`. Kết luận ban đầu nên là gì?

**Đáp án:** nghi vấn có vấn đề ở quá trình biến đổi bytecode hoặc khả năng tương thích; phải so sánh artifact và xác minh môi trường trước khi kết luận cuối cùng.

### Bài 7: Lỗi cùng lúc

Nghiệp vụ ném lỗi A; advice cleanup ném lỗi B khiến caller chỉ thấy B. Có thể tìm nguyên nhân bằng cách chỉ sửa pointcut không?

**Đáp án:** không. Cần kiểm tra **exception masking** và chính sách lỗi của advice; bảo toàn/báo lỗi gốc theo hợp đồng.

### Bài 8: Runtime không có log nhưng nghiệp vụ thành công

Nêu tối thiểu bốn khả năng khác nhau.

**Đáp án gợi ý:** pointcut không match; lớp nằm ngoài weaving scope; loại join point không được hỗ trợ; caller đi ngoài proxy; nhánh chưa xảy ra; logger bị lọc hoặc sink lỗi.

### Bài 9: Binary compatibility

Biên dịch thành công, chạy gặp `NoSuchMethodError`. Có phải weaving chắc chắn là thủ phạm?

**Đáp án:** không. Có thể do classpath/runtime artifact khác với phiên bản đã dùng khi biên dịch, hoặc lỗi liên kết khác. Cần xác minh mã đích ở runtime.

### Bài 10: Kế hoạch retry

Around advice xử lý lỗi bằng cách gọi `proceed` thêm lần nữa. Hệ thống thanh toán ghi trùng một giao dịch. Đây có phải lỗi dệt?

**Đáp án:** thường là lỗi **thiết kế/hành vi runtime của advice và nghiệp vụ**, trừ khi có bằng chứng bộ dệt tạo mã sai. Phải đánh giá idempotency, giao dịch và chính sách retry.

## 30. Checklist trước khi báo “lỗi AOP”

- Tôi đã phân biệt **triệu chứng** với **nguyên nhân** chưa?
- Tôi biết cơ chế đang dùng là compile-time, binary weaving, load-time hay proxy không?
- Tôi đã xác định lỗi xảy ra khi **compile**, **transform/load** hay **invoke** chưa?
- Có phải lỗi cú pháp/kiểu Java thuần không?
- Pointcut có hợp lệ, đúng cú pháp và hỗ trợ đúng loại join point không?
- Binding tham số advice có phù hợp dữ liệu join point không?
- Có xác nhận class/call site nằm trong weaving scope không?
- Với proxy, lời gọi có thật sự qua proxy không?
- Có thể tái hiện bằng một test dương và một test âm không?
- Nếu có exception, tôi đã đọc **cause** và các lỗi phát sinh từ advice chưa?
- Nếu có `VerifyError` hoặc linkage error, tôi đã kiểm tra artifact/phiên bản/class loader chưa?
- Nếu advice không chạy, tôi có bằng chứng phần gốc vẫn chạy và kênh log còn hoạt động không?
- Với around advice, `proceed` có được gọi đúng số lần và có bảo toàn return/throw không?
- Tôi đã đưa ca tái hiện vào bộ kiểm thử hồi quy để tránh lỗi lặp lại chưa?

## 31. Kết luận

**Lỗi biên dịch** liên quan việc source không thỏa ngôn ngữ/hệ kiểu và khả năng phân giải. **Lỗi dệt** liên quan khả năng phân tích, chọn và kết hợp aspect với mã tại các join point được hỗ trợ. **Lỗi thực thi** liên quan những gì xảy ra khi chương trình và advice thực sự chạy: dữ liệu, trạng thái, ngoại lệ, continuation, đồng thời hoặc kết quả nghiệp vụ.

Ba nhãn không phải ba khoảng thời gian hoàn toàn độc lập. Một bộ dệt có thể chạy ngay khi biên dịch hoặc khi nạp class; một lỗi cấu hình weaving có thể chỉ lộ ở runtime, và một lỗi runtime có thể bắt nguồn từ advice chứ không phải phương thức gốc.

Để chẩn đoán chính xác, luôn hỏi hai câu: **“Vấn đề thuộc tầng nào?”** và **“Nó được phát hiện ở giai đoạn nào?”**. Sau đó kiểm chứng pointcut, join point model, weaving scope, runtime path và luồng return/throw bằng một ca thử nhỏ có thể tái hiện, thay vì quy mọi biểu hiện cho “AOP không hoạt động”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-architecture-010">Dệt lặp và phát hiện hành vi bị chèn nhiều lần</a>

<details>
<summary>Xem chi tiết</summary>

# Dệt lặp và phát hiện hành vi bị chèn nhiều lần

## 1. Bài toán và phạm vi

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), **pointcut** chọn những *join point* có thể can thiệp, còn **advice** thực hiện hành vi bổ sung tại các điểm ấy. **Weaving** là quá trình kết hợp aspect với chương trình mục tiêu để advice có thể chạy tại join point được chọn. Ngoài weaving trong hoặc sau build, hoặc lúc nạp class, ứng dụng còn có thể dùng **proxy/interceptor** định tuyến lời gọi qua một lớp trung gian lúc runtime.

**Dệt lặp** là tình huống một quá trình weaving được áp dụng trở lại lên mã/artifact nằm trong phạm vi đã được xử lý trước đó. Nếu quy trình không được kiểm soát, cùng một hành vi cắt ngang có thể xuất hiện **hai lần hoặc nhiều lần trên một đường thực thi**. Tuy nhiên, không có quy tắc chung rằng chạy weaver hai lần nhất định sẽ chèn gấp đôi advice: một số công cụ nhận diện, từ chối, bỏ qua hoặc có quy tắc riêng với class đã được dệt.

Tương tự, **duplicate advice execution** (hành vi advice chạy trùng) **không tự chứng minh có double bytecode weaving**. Hai proxy cùng ghi trace, hai pointcut giao nhau, một method call cộng một method execution, hoặc event được gửi hai lần qua sink có thể cùng tạo triệu chứng “hai dòng log”.

Mục tiêu của bài là:

1. Phân biệt **dệt lặp thực sự**, **bọc trùng bằng proxy**, **nhiều advice hợp lệ**, **retry/recursion**, và **phân phối sự kiện trùng**.
2. Nhận biết tại tầng nào sinh ra nhiều hành vi hơn kỳ vọng.
3. Giữ riêng **số lần advice chạy** và **số lần nghiệp vụ thật sự chạy**.
4. Thiết kế pipeline weaving, registration và pointcut có thể kiểm soát.
5. Xây phép kiểm thử ngăn lỗi tái phát mà không xóa nhầm advice hợp lệ.

Một ví dụ nguy hiểm: audit được thiết kế ghi **một sự kiện cho mỗi lần đặt hàng**, nhưng vì cùng bộ audit được áp hai lần, một lần mua tạo hai dòng sự kiện. Với logic **retry**, vấn đề còn nghiêm trọng hơn: nếu tầng retry cũng bị cài trùng, nghiệp vụ có thể chạy nhiều lần. Đây là hai loại lỗi cần phân tích riêng.

## 2. Thuật ngữ quan trọng

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Join point** | Một vị trí/sự kiện mà mô hình AOP có thể can thiệp, ví dụ call hoặc execution |
| **Pointcut** | Quy tắc chọn tập join point |
| **Advice** | Hành vi được thực hiện tại điểm được chọn |
| **Weaver** | Thành phần kết hợp aspect vào chương trình, thường qua biến đổi mã/bytecode |
| **Woven artifact** | Class/JAR/binary đã qua quá trình dệt |
| **Repeated weaving** | Dệt lại artifact hoặc phạm vi đã được xử lý |
| **Duplicate advice** | Cùng hành vi cắt ngang chạy nhiều lần ngoài hợp đồng |
| **Proxy/interceptor** | Đối tượng/trình xử lý đứng trên đường gọi và chuyển tiếp tới target |
| **Instrumentation** | Cơ chế chèn hoặc kích hoạt khả năng quan sát/can thiệp |
| **Idempotence** | Áp dụng nhiều lần cho hiệu quả tương đương áp dụng một lần theo tiêu chí đã định |
| **Deduplication** | Loại trừ phần tử bị coi là trùng theo định danh và hợp đồng cụ thể |

Phải luôn ghi rõ **“trùng theo đơn vị nào?”** Một lời gọi API có thể chứa nhiều lần gọi method, nhiều join point, nhiều attempts và nhiều events. Nếu chưa xác định đơn vị quan sát, kết luận “chạy trùng” rất dễ sai.

## 3. Dệt lặp trong build pipeline

Một pipeline dễ gặp lỗi:

~~~text
Source + aspect definitions
          |
          v
     Weaver pass #1
          |
          v
     Woven classes
          |
          v
    Package / plugin
          |
          v
     Weaver pass #2
          |
          v
      Deploy / run
~~~

Nếu pass #2 được cung cấp **artifact đã dệt** như thể đó là artifact gốc, hậu quả tùy năng lực và cấu hình weaver. Có thể công cụ phát hiện marker và bỏ qua; có thể nó báo lỗi; cũng có thể pipeline vô ý tạo hành vi được cài nhiều lần.

Những dấu hiệu nên kiểm tra:

- Build đã dệt lúc compile, bước packaging lại áp binary weaving lên cùng output.
- Module con xuất bản class đã dệt, module tổng hợp lại xử lý chính class đó.
- Build cache lưu **woven output** ở đường dẫn mà job sau tưởng là **raw output**.
- Class được dệt trước khi đóng JAR, sau đó deployment pipeline cũng dệt.
- Có hai plugin/agent độc lập cùng cài một chính sách trên cùng ranh giới.

**Phòng ngừa:** phân định rõ thư mục/artifact nguồn chưa dệt và output đã dệt; chỉ định một chủ sở hữu bước weaving; dùng build tái lập; ghi version công cụ, input và output; kiểm thử artifact phát hành chứ không chỉ source.

**Lưu ý về hash:** checksum thay đổi chưa chứng minh double weaving, vì thay đổi metadata, timestamps hoặc compiler cũng có thể thay file. Hãy kết hợp provenance của pipeline, phân tích class và phép đếm runtime.

## 4. Compile-time, binary, load-time và runtime không cùng một loại rủi ro

| Kiểu áp dụng | Thời điểm | Nguồn chồng lấn tiêu biểu | Cách kiểm |
| --- | --- | --- | --- |
| **Compile-time weaving** | Trong build/compile | Dệt tiếp khi package | Đối chiếu output sau build với input |
| **Binary/post-compile weaving** | Sau biên dịch, trước chạy | Dệt lại JAR đã dệt | Kiểm soát nguồn class và các bước pipeline |
| **Load-time weaving** | Khi JVM định nghĩa class | Artifact đã woven lại qua transformer | Kiểm tra cấu hình load/classloader/transformer |
| **Retransformation/redefinition** | Class đã nạp, khi công cụ/JVM hỗ trợ | Nhiều transformer/cơ chế instrument tương tự | Theo dõi class definitions, transformer chain |
| **Runtime proxy/interceptor** | Khi lời gọi đi qua wrapper | Proxy lồng proxy hoặc handler đăng ký hai lần | Kiểm tra object graph và chuỗi interceptor |

**Load-time weaving không đồng nghĩa runtime proxy.** LTW thường biến đổi bytecode khi class được định nghĩa, còn proxy có thể can thiệp mà không sửa bytecode target. Hai cơ chế ấy có thể cùng tồn tại và tạo hai lớp hành vi trên cùng operation.

Một class đã được woven ở build có thể được load bình thường. Nếu còn một agent instrument class ở load-time, cần xác định rõ agent đó có định nghĩa điểm can thiệp chồng lấn với mã đã dệt không.

Không được khẳng định **mọi lần retransformation tự động xếp thêm advice lên bytecode lần trước**. Quy tắc transformer, bytecode được JVM cung cấp khi retransformation, và các thay đổi được phép phụ thuộc hợp đồng Java Instrumentation cùng triển khai công cụ. Muốn biết có chèn trùng hay không phải **quan sát bytecode hoặc số handler thực tế**, không đoán theo số lần yêu cầu retransform.

## 5. Double instrumentation dù không có double weaving

Một tình huống khác:

~~~text
Caller
  |
  v
Proxy AUDIT
  |
  v
Woven target AUDIT
  |
  v
Business execution
~~~

Nếu cả hai tầng đều ghi **cùng một logical audit event**, một lời gọi caller có thể dẫn tới hai events. Nhưng có thể hệ thống cố ý có:

- Audit tại **API call boundary**.
- Audit tại **method execution** bên trong.
- Metrics ở mỗi attempt do retry.
- Trace span ở cả request cấp cao và hoạt động con.

Chúng chỉ là lỗi khi **vi phạm hợp đồng số lần và ngữ nghĩa**. Đừng tắt một tầng chỉ vì thấy hai thông báo trông giống nhau; trước hết phải nhận diện **aspect ID, advice ID, join point kind, operation ID, phase** và **business execution count**.

## 6. Proxy bị bọc hai lần: ca dễ kiểm chứng

Khi một factory bọc cùng hành vi qua hai lần:

~~~text
raw = Business()
p1  = trace(raw)
p2  = trace(p1)

caller -> p2 -> p1 -> raw
~~~

Nếu mỗi proxy chuyển tiếp một lần:

~~~text
TRACE before (outer)
TRACE before (inner)
BUSINESS execute
TRACE after  (inner)
TRACE after  (outer)
~~~

Một **business execution** nhưng **hai lần advice entry**. Điều này có thể làm audit và metrics sai mà không làm tăng số lượng transaction.

Cơ chế phòng ngừa hợp lý là **idempotent registration**: biết một đối tượng đã chứa **chính xác cùng chính sách instrumentation** hay chưa. Tuy nhiên, không được cấm mọi trường hợp proxy lồng nhau — các tầng security, tracing và caching khác trách nhiệm là hoàn toàn hợp lệ.

Một lỗi hay gặp là xây một lớp `proxy factory` nhiều lần ở hai tầng hệ thống mà mỗi lần factory không biết ai đã áp chính sách trước đó. Tốt hơn nên có **một nơi chịu trách nhiệm lắp interceptor chain**, có contract và phép kiểm đếm.

## 7. Pointcut giao nhau và nhiều advice cùng khớp

Ví dụ mô tả:

~~~text
P1 = publicApiOperations
P2 = annotatedAuditedOperations

operationX vừa public vừa annotated
=> P1 match và P2 match
~~~

Nếu cả hai advice đều ghi một event `ORDER_SUCCESS` theo cùng policy, có thể xảy ra duplicate logical event, dù mỗi advice được dệt **đúng một lần**.

Nếu mục tiêu muốn hai tập không trùng, có thể mô tả (pseudocode, không phải cú pháp chung của mọi engine):

~~~text
regularAudit = P1 AND NOT P2
criticalAudit = P2
~~~

Cách này chỉ phù hợp nếu bộ lọc thực sự có nghĩa và engine hỗ trợ các phép ghép. Một giải pháp khác là **một named pointcut chung và một handler audit duy nhất**, trong khi những advice khác như metrics, tracing vẫn có thể dùng tập điểm tương tự nhưng thực hiện mục tiêu khác.

Một điều cần nhớ: một named pointcut được nhiều advice tham chiếu **không tự khiến các advice bị gộp**. Nếu hai advice khác nhau cùng khớp, chúng có thể đều chạy như thiết kế.

## 8. Vì sao hai dòng log chưa chứng minh dệt lặp?

| Hiện tượng | Vì sao thấy nhiều sự kiện? | Dấu hiệu phân biệt |
| --- | --- | --- |
| Before và after | Một lời gọi có hai phase | Phase khác nhau |
| Method call và execution | Hai join point hợp lệ | Kind khác nhau |
| Nested call | Parent gọi child đều khớp | Parent/child invocation |
| Recursion | Phương thức chạy ở nhiều cấp | Depth/call ID khác |
| Retry | Business chạy nhiều attempt | Attempt number tăng |
| Hai aspect khác mục đích | Audit, metrics, trace cùng ghi log | Advice identity khác |
| Bridge/synthetic method | JVM có nhiều method bodies trong chuỗi gọi | Signature/bridge flag |
| Hai classloader | Hai runtime classes cùng binary name | Loader identity khác |
| Event gửi lại | Một event qua queue/exporter nhiều lần | Event ID trùng, advice entry không tăng |
| Log appender trùng | Một log record được xuất hai sink | Một log source, hai output |
| Advice bọc proxy lặp | Một target, nhiều tầng cùng handler | Advice count tăng, target count không tăng |

**Nguyên tắc chẩn đoán:** cần nhiều số đếm độc lập, không chỉ lượng dữ liệu xuất hiện trong dashboard.

## 9. Bốn tầng cần đếm và liên kết

Để phân biệt đúng, định nghĩa tối thiểu:

1. **Logical operation:** một yêu cầu nghiệp vụ end-to-end.
2. **Invocation/attempt:** một lần thực hiện một lời gọi cụ thể hoặc một lần retry.
3. **Advice entry:** một lần một advice thực sự được kích hoạt.
4. **Event delivery:** một sự kiện được tạo, gửi, lưu hoặc xuất ra dashboard.

Cùng một logical order có thể có một invocation, hai advice entries và một business execution. Hoặc có hai invocations vì retry, hai business executions, và số advice entries tùy vị trí của advice trong chuỗi wrapper.

Một bộ log chẩn đoán có kiểm soát có thể chứa:

| Trường | Mục đích |
| --- | --- |
| `logicalOperationId` | Xác định yêu cầu nghiệp vụ |
| `invocationId` / `attempt` | Xác định lời gọi hoặc lượt thử |
| `aspectId` / `adviceId` | Xác định hành vi được cài |
| `joinPointKind` | Call, execution, constructor, v.v. |
| `methodSignature` | Phân biệt overload |
| `phase` | BEFORE, RETURNING, THROWING, FINALLY |
| `parentSpanId` | Biết một event nằm trong lời gọi cha nào |
| `instrumentationLayer` | Proxy, woven artifact, agent |
| `classloaderIdentity` | Biết class bản nào được thực thi |
| `artifactVersion` | Đối chiếu đúng bản build |
| `eventId` | Phân biệt event nhân bản từ transport |

Không phải mọi engine cung cấp đủ metadata. Các ID phục vụ quan sát nên là **ID kỹ thuật không nhạy cảm**; không đưa mật khẩu, token, thông tin cá nhân hoặc toàn bộ payload vào nhãn metric.

## 10. Ví dụ Java 17: chứng minh advice chạy hai lần nhưng nghiệp vụ một lần

Ví dụ độc lập dùng **JDK dynamic proxy** và `AtomicInteger`. Đây là cách mô phỏng **double interception**, **không phải bộ bytecode weaver**.

- `wrap(wrap(raw, stats), stats)` bọc target hai lần bằng cùng `TraceHandler`.
- `wrapOnce` tránh lặp khi **proxy ngoài cùng** đã mang `TraceHandler` của ví dụ.
- So sánh `adviceEntries` và `Core.calls` cho biết chính xác hai hiện tượng.
- Ngoại lệ từ target được bóc khỏi `InvocationTargetException` rồi ném lại.

Tạo file `DuplicateAdviceDemo.java`:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public final class DuplicateAdviceDemo {
    interface Work {
        String execute(String id);
    }

    static final class Core implements Work {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String execute(String id) {
            calls.incrementAndGet();
            System.out.println("BUSINESS execute " + id);
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("invalid id");
            }
            return "ok:" + id;
        }
    }

    static final class Stats {
        final AtomicInteger adviceEntries = new AtomicInteger();
    }

    static final class TraceHandler implements InvocationHandler {
        final Work target;
        final Stats stats;

        TraceHandler(Work target, Stats stats) {
            this.target = target;
            this.stats = stats;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            stats.adviceEntries.incrementAndGet();
            System.out.println("TRACE before " + method.getName());
            try {
                Object result = method.invoke(target, args);
                System.out.println("TRACE returning " + method.getName());
                return result;
            } catch (InvocationTargetException wrapped) {
                Throwable original = wrapped.getCause();
                System.out.println("TRACE throwing "
                        + original.getClass().getSimpleName());
                throw original;
            }
        }
    }

    static Work wrap(Work target, Stats stats) {
        return (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[]{Work.class},
                new TraceHandler(target, stats));
    }

    static Work wrapOnce(Work target, Stats stats) {
        if (Proxy.isProxyClass(target.getClass())
                && Proxy.getInvocationHandler(target) instanceof TraceHandler) {
            return target;
        }
        return wrap(target, stats);
    }

    public static void main(String[] args) {
        Core raw = new Core();
        Stats duplicateStats = new Stats();
        Work duplicated = wrap(wrap(raw, duplicateStats), duplicateStats);

        System.out.println("DOUBLE_RESULT " + duplicated.execute("A"));
        System.out.println("DOUBLE_COUNTS advice="
                + duplicateStats.adviceEntries.get()
                + " business=" + raw.calls.get());

        Core protectedCore = new Core();
        Stats protectedStats = new Stats();
        Work first = wrapOnce(protectedCore, protectedStats);
        Work second = wrapOnce(first, protectedStats);
        System.out.println("SAME_PROXY " + (first == second));
        System.out.println("ONCE_RESULT " + second.execute("B"));
        try {
            second.execute("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
        System.out.println("ONCE_COUNTS advice="
                + protectedStats.adviceEntries.get()
                + " business=" + protectedCore.calls.get());
    }
}
~~~

Biên dịch và chạy (JDK 17 trở lên):

~~~bash
javac --release 17 -encoding UTF-8 DuplicateAdviceDemo.java
java DuplicateAdviceDemo
~~~

Stdout đã xác minh bằng chạy thực:

~~~text
TRACE before execute
TRACE before execute
BUSINESS execute A
TRACE returning execute
TRACE returning execute
DOUBLE_RESULT ok:A
DOUBLE_COUNTS advice=2 business=1
SAME_PROXY true
TRACE before execute
BUSINESS execute B
TRACE returning execute
ONCE_RESULT ok:B
TRACE before execute
BUSINESS execute bad
TRACE throwing IllegalArgumentException
CALLER invalid id
ONCE_COUNTS advice=2 business=2
~~~

### 10.1. Vì sao `DOUBLE_COUNTS advice=2 business=1`?

Lần gọi `duplicated.execute("A")` đi qua proxy ngoài, proxy trong, rồi mới đến Core. Hai `TraceHandler` đều tăng counter, còn `Core.execute()` chỉ chạy một lần vì mỗi handler gọi target đúng một lần.

Trong bài toán audit, hai `TRACE before` có thể là sai vì cùng một logical event phát hai lần; nhưng **business call count** không nhân đôi. Nếu một `around` khác cố ý retry, ta sẽ phải kiểm thêm số lần `proceed`, vì target có thể chạy lại.

### 10.2. Ý nghĩa của `SAME_PROXY true`

`wrapOnce` nhận dạng proxy **ngoài cùng** do `TraceHandler` của ví dụ tạo nên không tạo thêm wrapper. Hai lượt gọi khác nhau — `execute("B")` và `execute("bad")` — có **hai advice entries và hai business invocations**, đúng mỗi lần một entry.

Nhưng `wrapOnce` **không phải giải pháp phát hiện weaving phổ quát**:

- Nó không phát hiện bytecode đã được dệt.
- Nó không nhìn xuyên một proxy handler loại khác để biết có `TraceHandler` bên trong.
- Hai handler cùng class có thể có **cấu hình nghiệp vụ khác nhau**, nên không nhất thiết trùng.
- Một thư viện proxy khác có thể không sử dụng JDK `Proxy`.
- Class đã được instrument ở load-time cũng không được nhận diện từ kiểu proxy ngoài cùng.

Bài học là **kiểm soát tầng registration và policy identity**, không chỉ dùng `instanceof` để quyết định mọi trường hợp.

### 10.3. Lỗi và hợp đồng caller

`execute("bad")` ném `IllegalArgumentException`. Reflection `Method.invoke()` bọc nguyên nhân trong `InvocationTargetException`; handler bóc `getCause()` và ném lại ngoại lệ gốc. Việc chống instrumentation lặp **không được tự đổi loại lỗi, giá trị trả về hoặc số lần nghiệp vụ**.

## 11. Cơ chế phòng ngừa repeated weaving

Các chiến lược cần chọn theo kiến trúc:

| Chiến lược | Áp dụng ở đâu | Lưu ý |
| --- | --- | --- |
| **Một điểm ownership** | Build/registration | Chỉ một bước có trách nhiệm cho mỗi phạm vi |
| **Separate raw và woven artifacts** | Build pipeline | Không dùng output đã dệt làm input raw |
| **Clean, reproducible build** | CI | Kiểm soát cache và artifact provenance |
| **Weaver marker nếu được hỗ trợ** | Bytecode/build | Marker phải có ngữ nghĩa version/policy rõ |
| **Idempotent registration** | Proxy factory | Không thêm cùng handler/policy hai lần |
| **Mutually exclusive pointcuts** | Aspect definition | Tránh hai policy cùng tạo một event |
| **Behavior contract test** | Integration test | Đếm advice và target độc lập |
| **Event-level idempotency key** | Event sink | Bảo vệ transport at-least-once, không sửa bytecode |
| **Instrumentation inventory** | Production observability | Biết class/agent/proxy nào đang hoạt động |

### Marker có thể sai ở đâu?

Một weaver có thể đặt metadata “đã xử lý”. Nhưng marker chỉ hữu ích khi công cụ xác định rõ:

- Nó đại diện cho **weaver version** nào?
- Nó bao phủ toàn class hay chỉ một tập join point?
- Khi pointcut thay đổi, class có được dệt bổ sung không?
- Nếu pass đầu tiên thất bại giữa chừng, marker có phản ánh trạng thái không?
- Nếu agent khác chèn instrumentation, marker có phát hiện được không?
- Khi class được load từ classloader khác, identity có còn phù hợp không?

**Idempotent weaving là hợp đồng cần kiểm thử**, không phải thuộc tính tự nhiên của mọi class file. Không thêm marker bytecode thủ công nếu chưa biết engine và verifier chấp nhận.

## 12. Load-time weaving, retransformation và đăng ký transformer

Một hệ thống dùng Java agent có thể đăng ký một hoặc nhiều `ClassFileTransformer`. Trong quá trình class loading hoặc một số hoạt động retransformation/redefinition, các transformer được áp dụng theo quy tắc của Java Instrumentation.

Rủi ro cần kiểm tra:

1. Cùng transformer được đăng ký nhiều lần ngoài dự định.
2. Hai agent khác nhau instrument cùng bytecode với **chính sách trùng**.
3. Class đã qua build-time weaving nhưng vẫn bị runtime transformer instrument.
4. Pipeline transformer có nhiều bước cùng chèn một đoạn đo/audit.
5. Cache trạng thái theo **class name** nhưng không phân biệt classloader.
6. Cơ chế reload/retransform không cập nhật policy theo điều nhóm vận hành tin tưởng.

Cần đặc biệt tránh phát biểu: “gọi retransform N lần nghĩa là advice bị xếp N lớp.” Điều này **không mặc định đúng** vì bytecode nguồn và thứ tự áp dụng transformer trong retransformation có contract riêng. Muốn kết luận phải kiểm tra transformer count, class bytes tương ứng và số lần advice entry thật trong môi trường có kiểm soát.

## 13. Bridge, synthetic method và gọi lồng

Java compiler có thể sinh **bridge method** để hỗ trợ generic erasure và covariant return. Ví dụ:

~~~java
class Parent<T> {
    T value() { return null; }
}

class Child extends Parent<String> {
    @Override
    String value() { return "x"; }
}
~~~

Đây là đoạn minh họa Java, không phải chương trình độc lập có `main`. Một compiler có thể sinh method bridge bên cạnh implementation. Nếu cả bridge và implementation đều là execution join points được pointcut chọn, cùng một đường gọi có thể kích hoạt advice ở **hai method khác nhau** mà **không có class nào được dệt hai lần**.

Khi nghi ngờ, kiểm tra:

- `Method.isBridge()`, `Method.isSynthetic()`.
- Method descriptor và declaring type, không chỉ method name.
- Mô hình join point: method call, execution hay cả hai?
- Đường gọi bridge tới implementation.
- Quy tắc công cụ để bỏ qua/gom các method synthetic.
- Số event kỳ vọng theo **JVM method execution** hay **logical Java API call**.

Tương tự, đệ quy và self-invocation có thể tạo nhiều executions hợp lệ. Nếu dùng proxy ngoài, self-invocation có thể bypass proxy; nếu class đã bytecode woven tại execution join point, các lần gọi nội bộ có thể được quan sát. Đây là **khác biệt độ phủ**, không tự phải lỗi dệt lặp.

## 14. Retry, proceed và số lần target thực thi

Trong AOP, một **around advice** có thể điều khiển `proceed` tùy chính sách. Với retry hợp lệ:

~~~text
Caller
  |
RetryAdvice
  +--> proceed attempt #1 -> error
  +--> proceed attempt #2 -> success
  |
result
~~~

Nếu timer/audit nằm **trong** retry, chúng có thể chạy ở mỗi attempt; nếu nằm **ngoài**, chúng có thể chỉ chạy một lần cho toàn request. Đây là hệ quả vị trí advice, không nhất thiết do dệt trùng.

Một lỗi khác nghiêm trọng là một around advice **vô tình gọi `proceed` hai lần** dù không có retry policy. Lúc đó business execution count cũng có thể nhân đôi. Chẩn đoán cần phân biệt:

- **Double advice wrapping:** advice=2, target=1.
- **Proceed gọi lặp:** target có thể =2.
- **Retry theo hợp đồng:** target=2 hoặc hơn, attempt ID khác.
- **Redelivery ở queue:** logical operation/event id có thể lặp ngoài ranh giới method.

Đừng khắc phục bằng cách chỉ lọc log trùng ở cuối hệ thống nếu side effect nghiệp vụ đã xảy ra nhiều lần.

## 15. Nhiều classloader và nhiều version

Trong JVM, class identity không chỉ là tên đầy đủ mà còn liên quan đến **classloader định nghĩa**. Hai class có cùng binary name nhưng được nạp bởi hai loaders có thể là hai runtime classes riêng.

Một cache instrumentation dùng duy nhất `"com.example.Service"` làm key có thể:

- Bỏ qua classloader thứ hai vì class cùng tên ở loader thứ nhất đã được đánh dấu.
- Báo sai “đã dệt trùng” dù thực tế là hai class khác nhau.
- Đọc nhầm phiên bản bytecode hoặc agent policy.
- Giữ tham chiếu mạnh tới loader cũ, gây rò rỉ bộ nhớ sau reload.

Một công cụ chẩn đoán nên quan sát **classloader identity, module, code source và artifact version** khi cần. Tuy nhiên, đừng đưa mọi thông tin này thành nhãn metric có cardinality vô hạn; lưu ở trace/debug có kiểm soát.

## 16. Phân biệt duplicate advice với duplicate delivery

Giả sử chỉ có một advice entry:

~~~text
Advice -> Event E-42 -> Queue -> Consumer #1
                             -> redelivery -> Consumer #1 again
~~~

Dashboard hoặc DB có thể chứa hai record `E-42` dù advice chỉ chạy một lần. Lỗi ở **delivery/consumer** chứ không phải weaving. Một hệ thống at-least-once có thể cần **idempotency key** tại consumer/sink, nhưng cách xử lý ấy **không giải quyết** trường hợp advice bị bọc hai lần trước khi event được tạo.

Ngược lại, nếu hai advice entries tạo hai event IDs khác nhau mà mang cùng một logical audit meaning, key event ID đơn thuần sẽ không phát hiện semantic duplication. Có thể cần một khóa chính sách như `(logicalOperationId, eventType, phase)` theo hợp đồng nghiệp vụ.

Tuy nhiên, không được dedup một cách mù quáng theo `methodName` hoặc timestamp: hai lời gọi đồng thời hợp lệ có thể có cùng tên hoặc xảy ra trong cùng milli giây.

## 17. Quy trình chẩn đoán thực tế

**Bước 1 — Đặt hợp đồng rõ:** ví dụ “một `AuditOrderCompleted` cho mỗi logical order thành công”, “một timer cho mỗi attempt” hoặc “một trace span cho mỗi method execution”.

**Bước 2 — Tạo ca tối thiểu:** một method, input xác định, không retry/async nếu đang tìm trùng instrumentation, kết quả và exception rõ.

**Bước 3 — Đếm độc lập:** mỗi aspect/advice entry, mỗi target execution, event được tạo, event đến sink. Không suy diễn từ log.

**Bước 4 — Xác minh join point:** call hay execution? Có bridge/synthetic? Overload? Các method lồng nhau? Pointcut chồng lấn?

**Bước 5 — Liệt kê mọi tầng instrumentation:** compile-time/binary weaving, LTW agent, retransformation, proxy factories và interceptors.

**Bước 6 — Kiểm tra artifact và class đã load:** cùng class file có đi qua hai bước build không, có marker của weaver không, classloader nào đang dùng, version nào được deploy?

**Bước 7 — Cô lập trong môi trường kiểm thử:** chỉ proxy, chỉ bytecode weaving, chỉ agent, rồi kết hợp để xác định tầng tạo duplicate. Không vô hiệu hóa bảo vệ hoặc audit bắt buộc trên production chỉ để thử.

**Bước 8 — Sửa nơi phát sinh:** build lỗi thì sửa pipeline; proxy bọc trùng thì sửa registration; pointcut giao nhau thì sửa ownership; delivery trùng thì sửa consumer/idempotency.

**Bước 9 — Regression test:** sau sửa, xác nhận đúng một policy application theo contract và return/exception/target call count vẫn đúng.

## 18. Bảng test tối thiểu

| Trường hợp | Kỳ vọng |
| --- | --- |
| Build sạch và weaving một lần | Một policy application trên join point dự kiến |
| Thử weaving lại cùng artifact | Công cụ xử lý rõ, không âm thầm tạo duplicate behavior |
| Proxy chỉ bọc một lần | Một advice entry cho một lời gọi |
| Proxy vô tình bọc hai lần | Test phát hiện `advice=2, business=1` |
| Idempotent registration trong ví dụ | Trả cùng proxy và không bọc thêm |
| Hai pointcut giao nhau | Test đúng hai policies nếu có chủ đích |
| Call + execution join point | Phân biệt kind và scope |
| Bridge/synthetic method | Kiểm tra ngữ nghĩa khi method chuyển tiếp |
| Retry | Đếm theo attempt, không nhầm với double weaving |
| Target ném ngoại lệ | Lỗi gốc được giữ nguyên |
| Target gọi trực tiếp bỏ qua proxy | Không kỳ vọng proxy advice nếu không có tầng khác |
| Load-time agent + artifact woven | Kiểm tra chồng lấn thực tế |
| Hai classloader | Không nhầm cùng tên thành cùng class |
| Queue redelivery | Nhận diện bằng event ID/sink, không đổ lỗi cho advice |
| Concurrent calls | Counts thread-safe và IDs phân biệt invocation |

Ví dụ assertion độc lập:

~~~text
given: one selected invocation, no retry
when:  instrumented.execute("A")
then:
    auditEntries == 1
    businessExecutions == 1
    callerResult == "ok:A"
~~~

Đây là pseudocode để mô tả hợp đồng. Nếu hai advice hợp lệ cùng khớp, hãy giữ hai counter theo **policy identity**, không gộp chúng vào một con số rồi mặc định một trong hai là lỗi.

## 19. Những cách sửa sai dễ gây hậu quả

- **Cờ `static boolean alreadyRan`:** sau lần gọi đầu, mọi lời gọi sau có thể bị bỏ qua; còn có race giữa threads.
- **`ThreadLocal` guard không cleanup:** trạng thái rò sang request sau trong thread pool. Nếu có guard, phải khôi phục trong `finally` và phân biệt nested calls.
- **Loại mọi proxy thứ hai:** có thể bỏ mất security/caching/tracing hợp lệ.
- **Dedup theo method name:** overload có chữ ký khác nhau, đồng thời các lần gọi độc lập cũng có cùng tên.
- **Dedup theo timestamp:** trùng thời gian không chứng minh trùng invocation.
- **Chỉ dedup ở log sink:** side effect của advice vẫn chạy hai lần trước khi vào sink.
- **Giữ marker không có version:** thay đổi pointcut có thể bị bỏ qua sai hoặc class chỉ được instrument một phần.
- **Cho rằng agent đã khởi động nghĩa là mọi class đều đã weaving:** classloader, thứ tự nạp lớp, phạm vi và transformer capability có thể giới hạn.
- **Tắt một advice mà chưa xem precedence:** có thể làm mất cả audit của denial hoặc metrics ở nhánh exception.
- **Sửa retry bằng cách bỏ mọi lời gọi lặp:** có thể phá chính sách khôi phục vốn được thiết kế và kiểm thử.
- **Log toàn bộ args/return để tìm trùng:** rò dữ liệu, overhead và nhãn có độ phân biệt quá lớn.

## 20. Nâng cao: tính idempotent đúng tầng

Cần phân biệt ba hợp đồng idempotence:

1. **Build idempotence:** chạy pipeline build/weaving lại với cùng đầu vào phù hợp tạo artifact có behavior tương đương, không chèn trùng ngoài ý định.
2. **Instrumentation registration idempotence:** đăng ký cùng handler/policy nhiều lần không tạo thêm lớp tương đương.
3. **Event processing idempotence:** xử lý cùng event nhiều lần không tạo thêm tác dụng phụ bên lưu trữ ngoài hợp đồng.

Ba tầng này **không thay thế nhau**. Một sink dedup hoàn hảo không loại bỏ overhead của hai advice ở đầu nguồn. Một factory `wrapOnce` không phát hiện class woven trùng ở bytecode. Một weaver có marker không ngăn event bị queue redeliver.

Khi định danh chính sách, có thể thiết kế một khóa theo `(instrumentationId, policyVersion, joinPointIdentity)` cho phạm vi static, cộng `invocationId` hoặc `logicalOperationId` khi đo runtime. Khóa cụ thể tùy hợp đồng: event `before` và `after` khác phase không được xóa nhầm; hai attempts retry có thể cần được giữ riêng.

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Một method in hai dòng TRACE có chứng minh nó bị dệt bytecode hai lần không?  
**Trả lời:** Không. Có thể là hai proxy, hai advice, nhiều join points hoặc trùng ở sink. Phải đo riêng advice entries và target invocations, đồng thời kiểm tra cơ chế instrumentation.

**Câu 2.** Hai proxy cùng ghi audit bao quanh target, mỗi proxy chuyển tiếp đúng một lần. Target có chạy hai lần không?  
**Trả lời:** Không nhất thiết; ví dụ Java cho thấy advice chạy hai lần, target chạy một lần.

**Câu 3.** Tại sao không thể khẳng định retransformation hai lần luôn chèn advice hai lần?  
**Trả lời:** Vì bytecode được cung cấp và cách transformer hoạt động tuân theo hợp đồng JVM và công cụ; cần kiểm nghiệm thực tế.

**Câu 4.** Vì sao bridge method có thể gây cảm giác advice chạy trùng?  
**Trả lời:** Cùng đường gọi có thể đi qua bridge và implementation là hai execution join points, dù mỗi method chỉ được instrument một lần.

**Câu 5.** Khi advice chạy hai lần nhưng target một lần, nơi ưu tiên sửa là gì?  
**Trả lời:** Kiểm tra proxy nesting, policy registration, weaving scope hoặc overlapping pointcuts, không tự sửa nghiệp vụ để tránh chạy lặp.

**Câu 6.** Nếu một event ID xuất hiện hai lần ở DB nhưng advice count chỉ một, suy luận gì?  
**Trả lời:** Có thể lỗi ở event delivery, consumer hoặc exporter; chưa có bằng chứng double weaving.

**Câu 7.** Một cờ global `alreadyExecuted` có sửa an toàn double advice không?  
**Trả lời:** Không. Nó bỏ qua các lời gọi độc lập, không an toàn đồng thời và không phản ánh policy/invocation identity.

**Câu 8.** Có nên coi hai advice cùng khớp cùng một join point là lỗi không?  
**Trả lời:** Chỉ khi vi phạm hợp đồng của chúng. Audit và metrics cùng chạy có thể đúng; hai lần tạo cùng một audit event ngoài ý định mới là lỗi.

## 22. Tổng kết

**Dệt lặp** có thể tạo ra hành vi cắt ngang bị chèn nhiều lần, nhưng **duplicate advice** còn có thể đến từ proxy lồng nhau, pointcut chồng lấn, nhiều tầng instrumentation, bridge methods hoặc một chuỗi join points hợp lệ. **Hai log giống nhau không đủ chứng minh nguồn lỗi.** Phải xác định ý nghĩa của một operation/invocation/attempt, đếm advice entry và business execution độc lập, và lần theo toàn bộ pipeline build, class loading, proxy registration và event delivery.

Thiết kế vững chắc gồm **ownership rõ cho mỗi chính sách**, tách raw/woven artifacts, kiểm soát weaver/transformer, idempotent registration đúng phạm vi, pointcut có ý nghĩa, metadata chẩn đoán không nhạy cảm và integration tests trên đường gọi thực. Mục tiêu không phải cấm mọi lời gọi có nhiều advice, mà là bảo đảm **mỗi hành vi cắt ngang được áp dụng đúng số lần theo hợp đồng**, giữ nguyên kết quả và ngoại lệ nghiệp vụ, đồng thời duy trì số liệu audit/metrics trung thực.

</details>

- [Quay lại đầu trang](#back-to-top)
