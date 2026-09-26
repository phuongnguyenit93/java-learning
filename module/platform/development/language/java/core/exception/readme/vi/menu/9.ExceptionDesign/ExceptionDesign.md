# Thiết kế Exception

Biết cú pháp `try/catch` chưa đủ để thiết kế failure flow tốt. Câu hỏi quan trọng hơn là:

```text
Tầng nào có đủ context
và đủ trách nhiệm
để làm điều có ý nghĩa với failure?
```

Một exception design tốt giữ được ba thứ cùng lúc:

```text
control flow đúng
+ abstraction đúng
+ diagnostics đủ
```

## <a id="exception-boundaries">Exception Translation tại Abstraction Boundary</a>

Tầng thấp thường dùng vocabulary của implementation:

```text
SQLException
IOException
SocketTimeoutException
```

Tầng cao hơn có thể cần vocabulary của application/domain:

```text
OrderRepositoryException
DocumentLoadException
PaymentUnavailableException
```

Ví dụ:

```java
Order loadOrder(long orderId) {
    try {
        return repository.load(orderId);
    } catch (SQLException ex) {
        throw new OrderRepositoryException(
                "Cannot load order " + orderId,
                ex
        );
    }
}
```

### VÌ SAO TRANSLATE?

Nếu service public contract expose thẳng `SQLException`, caller bị coupling với storage technology.

Sau này repository đổi từ JDBC sang file hoặc remote API, failure vocabulary của caller cũng bị kéo theo.

Translation tạo boundary:

```text
implementation detail
→ kết thúc tại boundary

application meaning
→ tiếp tục ra caller
```

Nhưng translation phải giữ `cause`; nếu không, abstraction đẹp hơn nhưng diagnostics tệ hơn.

### Đừng wrap ở mọi tầng

Không cần:

```text
SQLException
→ RepositoryException
→ ServiceException
→ ControllerException
```

nếu mỗi wrapper chỉ đổi tên.

Wrap khi tầng mới thực sự thêm một trong các giá trị:

- abstraction meaning;
- handling category;
- useful context;
- stable public contract.

## <a id="do-not-swallow">Không nuốt lỗi</a>

Anti-pattern:

```java
try {
    run();
} catch (Exception ex) {
    // ignored
}
```

Code bên ngoài có thể tiếp tục như thể operation thành công trong khi state thực tế chưa đạt outcome mong muốn.

Hậu quả:

```text
failure xảy ra
→ signal bị xóa
→ caller thấy success giả
→ bug xuất hiện xa nguyên nhân gốc
```

### “Ignore có chủ ý” khác “swallow vô thức”

Có trường hợp một failure cụ thể được phép bỏ qua:

```java
try {
    deleteTemporaryFile();
} catch (NoSuchFileException ex) {
    // file đã không tồn tại; outcome mong muốn vẫn đạt
}
```

Điểm khác là:

- type cụ thể;
- lý do rõ;
- outcome vẫn hợp lệ;
- phạm vi ignore hẹp.

Không nên dùng `catch (Exception)` trống để đạt hiệu ứng tương tự.

## <a id="logging-boundary">Ranh giới Logging</a>

Một anti-pattern phổ biến:

```text
repository catch → log → rethrow
service catch    → log → rethrow
controller catch → log → response
```

Một failure tạo ra ba log stack trace gần giống nhau.

### Heuristic hữu ích

```text
tầng chỉ rethrow/translate
→ giữ context/cause
→ thường không log lại

tầng kết thúc request/job/message
→ có request/job/business context đầy đủ
→ thường log một lần
```

Đây không phải luật tuyệt đối. Một tầng có thể cần metric/audit riêng mà không dump lại full stack trace.

Câu hỏi tốt hơn “có log không?” là:

```text
Log này thêm thông tin mới gì
và ai chịu trách nhiệm cuối cho failure?
```

### Đừng log rồi làm mất cause

Sai:

```java
catch (SQLException ex) {
    log.error("database failed");
    throw new OrderRepositoryException("load failed");
}
```

Tốt hơn nếu cần wrap:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed", ex);
}
```

Boundary cuối log wrapper cùng causal chain một lần.

## <a id="exception-as-control-flow">Exception và Control Flow</a>

Exception phù hợp với **abnormal completion**, không nên thay thế nhánh bình thường có thể diễn đạt trực tiếp.

Khó đọc:

```java
try {
    return values.get(index);
} catch (IndexOutOfBoundsException ex) {
    return null;
}
```

nếu “index không tồn tại” thực ra là một state bình thường mà API có thể check rõ ràng.

Rõ hơn trong nhiều context:

```java
if (index < 0 || index >= values.size()) {
    return null;
}
return values.get(index);
```

### Nhưng exception ở parsing boundary có thể hợp lý

```java
try {
    return Integer.parseInt(text);
} catch (NumberFormatException ex) {
    return defaultValue;
}
```

Ở đây API parsing vốn dùng exception để báo input không parse được; catch tại boundary nhỏ có policy rõ.

Vấn đề không phải “exception chậm nên không bao giờ dùng”, mà là semantics:

```text
normal branch
→ diễn đạt bằng control structure bình thường

abnormal completion
→ exception
```

Hiệu năng có thể là lý do bổ sung trong hot path, nhưng readability/contract mới là lý do thiết kế đầu tiên.

## <a id="cleanup-and-recovery">Cleanup, Recovery, Retry và Propagation</a>

Các hành động này khác nhau.

### Cleanup

```text
mục tiêu
→ giải phóng resource / hoàn tất cleanup bắt buộc

công cụ
→ try-with-resources
→ finally khi phù hợp
```

Cleanup không có nghĩa operation đã phục hồi.

### Recovery

Recovery nghĩa là có một chiến lược thực sự làm operation tiếp tục hợp lệ.

Ví dụ:

```text
file cấu hình chính không tồn tại
→ dùng default configuration được contract cho phép
```

Catch rồi trả một giá trị tùy ý không phải recovery nếu caller không thể phân biệt dữ liệu thật với dữ liệu giả.

### Retry

Retry chỉ có ý nghĩa khi failure có khả năng **tạm thời** và operation an toàn để thử lại.

Trước khi retry, hỏi:

```text
Failure có transient không?
Operation có idempotent hoặc có cơ chế chống duplicate side effect không?
Retry có bounded không?
Có backoff/cancellation/timeout policy không?
```

Không nên:

```java
while (true) {
    try {
        sendPayment();
        break;
    } catch (Exception ex) {
        // retry forever
    }
}
```

Retry vô hạn có thể biến failure thành overload và nhân side effect.

Trong Java Core module này, điểm cần nhớ là **retry là handling policy ở boundary có context**, không phải phản xạ mặc định cho mọi exception.

### Propagation

Nếu tầng hiện tại không thể:

- recover;
- translate hữu ích;
- bổ sung context cần thiết;
- kết thúc operation;
- cleanup thứ nó sở hữu;

thì để exception propagate có thể là lựa chọn đúng.

### Decision model

```text
Exception tới tầng hiện tại
        ↓
Tôi có sở hữu resource cần cleanup không?
        → có: cleanup an toàn

Tôi có thể phục hồi outcome hợp lệ không?
        → có: recover

Failure có transient và retry an toàn không?
        → có: retry theo policy bounded

Abstraction có đổi ở đây không?
        → có: translate + preserve cause

Đây có phải final request/job boundary không?
        → có: map outcome + log/observe phù hợp

Không có trách nhiệm nào ở trên?
        → propagate
```

### End-to-end mental model của module

```text
failure xảy ra
        ↓
throw
        ↓
propagate + stack unwinding
        ↓
cleanup vẫn phải được thực hiện
        ↓
tầng có trách nhiệm:
catch / recover / retry / translate / terminate
        ↓
nếu wrap: giữ cause
nếu cleanup cũng fail: giữ suppressed failure
        ↓
log/observe tại boundary phù hợp
```

Mục tiêu của exception design không phải là “catch càng nhiều càng an toàn”. Mục tiêu là **đặt failure policy đúng tầng, bảo toàn thông tin và không làm sai semantics của success/failure**.