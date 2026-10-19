<a id="back-to-top"></a>

# Sai lầm thường gặp

## Menu
- [Catch rỗng và nuốt mất tín hiệu thất bại](#exception-handling-pitfalls-001)
- [Bắt Exception hoặc Throwable quá rộng ở mọi tầng](#exception-handling-pitfalls-002)
- [Vừa ghi log vừa ném lại ở nhiều tầng](#exception-handling-pitfalls-003)
- [Ném ngoại lệ mới nhưng làm mất cause](#exception-handling-pitfalls-004)
- [Return trong finally hoặc lỗi cleanup che lỗi chính](#exception-handling-pitfalls-005)
- [Đóng tài nguyên thủ công thiếu nhánh thất bại](#exception-handling-pitfalls-006)
- [Dùng ngoại lệ để che việc kiểm tra điều kiện đơn giản](#exception-handling-pitfalls-007)
- [Trả null hoặc giá trị mặc định sau lỗi mà hợp đồng không nói rõ](#exception-handling-pitfalls-008)
- [Thử lại mọi ngoại lệ mà không xét tính tạm thời và tác động lặp](#exception-handling-pitfalls-009)
- [Để exception message trở thành định dạng dữ liệu mà mã khác phải phân tích](#exception-handling-pitfalls-010)
- [Bắt lỗi do sai lập trình rồi tiếp tục với trạng thái không hợp lệ](#exception-handling-pitfalls-011)

## <a id="exception-handling-pitfalls-001">Catch rỗng và nuốt mất tín hiệu thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Catch rỗng và nuốt mất tín hiệu thất bại trong Java

## 1. Vì sao một khối `catch` rỗng nguy hiểm?

Trong Java, ngoại lệ (*exception*) là một **tín hiệu điều khiển** cho biết luồng xử lý bình thường đã bị gián đoạn. Khi code ném một exception, JVM tìm `catch` thích hợp trên call stack. Nếu một `catch` bắt được exception và **không ném lại, không trả trạng thái lỗi, không phục hồi, không báo lỗi theo hợp đồng**, luồng thực thi thường tiếp tục như thể vấn đề đã được xử lý.

Ví dụ:

~~~java
try {
    saveOrder();
} catch (IOException ignored) {
}
sendSuccessResponse();
~~~

Đây là **fragment minh họa**, với `saveOrder()` và `sendSuccessResponse()` giả định đã được khai báo. Nếu `saveOrder()` ném `IOException`, dòng `sendSuccessResponse()` vẫn được gọi, dù đơn hàng có thể chưa được lưu hoặc chỉ mới lưu một phần. `catch` rỗng không tự hoàn tác ghi file, transaction đã commit, lệnh HTTP hay thay đổi đối tượng trong bộ nhớ.

Đây là **nuốt lỗi** (*exception swallowing*). Nó có thể tạo ra ba loại hậu quả:

1. **Sai kết quả nghiệp vụ:** API trả `true`, `200 OK` hoặc “đã lưu” khi thật ra chưa biết công việc có hoàn tất hay không.
2. **Mất chứng cứ chẩn đoán:** exception class, message, stack trace và `cause` bị loại bỏ khỏi kênh người có trách nhiệm theo dõi.
3. **Sai điều phối:** caller không retry/rollback/compensate/alert vì không nhận tín hiệu thất bại; hệ thống giám sát có thể đếm request đó là thành công.

Không phải mọi `catch` đều phải ném lại. Một `catch` có thể **xử lý thành công** khi nó thật sự khôi phục được điều kiện cần, ví dụ fallback cache hợp lệ hoặc bỏ qua một file cấu hình tùy chọn theo hợp đồng. Vấn đề không phải riêng hai dấu `{}`, mà là **đánh mất thông tin thất bại mà caller/hệ thống phải biết**.

**Nguyên tắc xuyên suốt:** Bắt exception chỉ là bước tiếp nhận lỗi; muốn xem lỗi đã được xử lý, phải chứng minh được hậu điều kiện, cơ chế phục hồi và đường thông tin đi tới nơi chịu trách nhiệm.

## 2. Cơ chế của `try-catch`: sau catch, chương trình đi đâu?

Cấu trúc cơ bản:

~~~java
try {
    performOperation();
} catch (IOException e) {
    handle(e);
}
continueProcessing();
~~~

Đây là fragment với các hàm giả định. Nếu `performOperation()` ném `IOException` và `handle(e)` **kết thúc bình thường**, execution tiếp tục tại `continueProcessing()`. JVM không tự quyết định “lỗi này chưa giải quyết, hãy truyền tiếp”; từ quan điểm điều khiển chương trình, `catch` đã xử lý luồng ném exception.

Nếu `handle(e)` ném exception mới, hoặc `catch` thực hiện `throw e`, exception lại lan ra ngoài. Nếu `catch` `return`, phương thức hiện tại kết thúc theo nhánh trả về; nếu có `finally` liên quan, nó được thực thi theo quy tắc Java. Nếu `catch` gọi một hàm fallback và fallback thất bại, lỗi mới cũng phải được xem xét.

### 2.1. So sánh bốn cách bắt lỗi

| Hành động trong `catch` | Caller quan sát gì? | Điều kiện nên dùng |
| --- | --- | --- |
| `catch (IOException ignored) {}` | Không nhận exception đó; mã sau catch tiếp tục | Gần như chỉ khi việc bỏ qua được **chứng minh** là an toàn và có chủ đích |
| `throw e;` | Nhận chính exception đó nếu chữ ký/hệ thống type cho phép | Tầng hiện tại không thể xử lý, muốn giữ loại/cause/trace |
| `throw new ServiceException("...", e);` | Nhận exception mới với cause gốc | Chuyển lỗi sang hợp đồng của tầng khác, bổ sung ngữ cảnh |
| `return Result.failure(code, e);` | Nhận một trạng thái thất bại có cấu trúc | API chọn result type để thể hiện thất bại dự kiến/được phân loại |

Tùy API, việc log tại `catch` rồi **trả trạng thái lỗi** cũng hợp lý; log không phải yếu tố thay thế result. Một số lỗi có thể được phục hồi bằng fallback và trả giá trị thành công, nhưng fallback phải thỏa invariant và không nói sai rằng thao tác gốc đã hoàn tất.

### 2.2. Không thấy stack trace không đồng nghĩa không có lỗi

Exception có thể được tạo, ném, bị catch rồi bỏ qua mà không có bất cứ dòng log nào. Java **không có cơ chế mặc định in mọi exception đã bị bắt**. Chỉ các exception không được bắt và đi tới uncaught handler, hoặc các đường code chủ động log/đọc Throwable, mới có thể tạo biểu hiện dễ thấy. Vì vậy hệ thống có thể “không thấy lỗi trong log” trong khi một số bước đã thất bại.

## 3. Một ví dụ ngắn: kết quả báo thành công nhưng state đã thay đổi

Giả sử một thao tác gồm:
1. Ghi một bản ghi “đơn được tạo”.
2. Ghi audit bắt buộc để xác nhận quy trình hoàn tất.

Nếu audit thất bại **sau bước 1**, việc nuốt exception làm caller tưởng cả hai bước hoàn tất, trong khi trạng thái chỉ có phần đầu.

~~~text
Ghi đơn thành công
        |
        v
Ghi audit thất bại -> IOException
        |
        v
catch rỗng
        |
        v
trả "SUCCESS" dù audit chưa có
~~~

Quan trọng: ngay cả khi ta sửa `catch` để truyền exception, **phần ghi đơn trước đó không tự hoàn tác**. Có hai vấn đề độc lập:
- **Phát tín hiệu lỗi đúng:** caller biết quy trình chưa hoàn tất.
- **Bảo toàn/khôi phục trạng thái:** transaction, staging, outbox hoặc bù trừ được thiết kế theo phạm vi tài nguyên.

Nếu chỉ thêm `throw` mà bỏ qua tính nhất quán dữ liệu, bạn đã khôi phục được khả năng quan sát lỗi nhưng chưa bảo đảm toàn bộ nghiệp vụ nguyên tử.

## 4. Ví dụ Java 17 đầy đủ: nuốt lỗi, truyền cause và trả kết quả có nghĩa

Chương trình dưới đây dùng một `EventStore` trong bộ nhớ để quan sát trạng thái. Nó cố tình tạo lỗi I/O trong bước audit, rồi so sánh:
- Phương thức **`broken`** bắt rỗng và in thông báo thành công.
- Phương thức **`honest`** thêm ngữ cảnh, giữ cause và truyền exception lên caller.
- Hàm tìm kiếm trả `Optional.empty()` **chỉ khi không có dữ liệu**.
- Một phương thức thể hiện fallback có cấu trúc ở mức tối thiểu: trả boolean phản ánh bước audit thành công/thất bại, không tự tuyên bố thành công.

Lưu chính xác thành **`EmptyCatchLessonDemo.java`**:

~~~java
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmptyCatchLessonDemo {
    static final class EventStore {
        private final List<String> events = new ArrayList<>();
        void append(String value) { events.add(value); }
        List<String> snapshot() { return List.copyOf(events); }
    }

    static void writeAudit() throws IOException {
        throw new IOException("audit unavailable");
    }

    static void broken(EventStore store) {
        try {
            store.append("order-created");
            writeAudit();
        } catch (IOException ignored) {
        }
        System.out.println("broken: reported success");
    }

    static void honest(EventStore store) throws IOException {
        store.append("order-created");
        try {
            writeAudit();
        } catch (IOException cause) {
            throw new IOException("order may be partially processed", cause);
        }
    }

    static Optional<String> find(String id) {
        return "A".equals(id) ? Optional.of("Alice") : Optional.empty();
    }

    static boolean attemptWithFallback() {
        try {
            writeAudit();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        EventStore first = new EventStore();
        broken(first);
        System.out.println("broken state: " + first.snapshot());

        EventStore second = new EventStore();
        try {
            honest(second);
        } catch (IOException e) {
            System.out.println("honest: " + e.getMessage());
            System.out.println("cause: " + e.getCause().getMessage());
        }
        System.out.println("honest state: " + second.snapshot());
        System.out.println("lookup: " + find("B").isEmpty());
        System.out.println("fallback: " + attemptWithFallback());
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 trở lên** trong thư mục chứa file:

~~~bash
javac --release 17 EmptyCatchLessonDemo.java
java EmptyCatchLessonDemo
~~~

Stdout đã kiểm chứng:

~~~text
broken: reported success
broken state: [order-created]
honest: order may be partially processed
cause: audit unavailable
honest state: [order-created]
lookup: true
fallback: false
~~~

### 4.1. Những điều output chứng minh

- **`broken: reported success`:** phương thức `broken` tiếp tục sau `catch` trống và in thành công dù `writeAudit` luôn ném `IOException`.
- **`broken state: [order-created]`:** phần ghi dữ liệu trong bộ nhớ đã xảy ra. `catch` rỗng không rollback trạng thái.
- **`honest: order may be partially processed`:** lỗi không bị mất; code bọc lại với ngữ cảnh rằng công việc có thể đã xử lý một phần.
- **`cause: audit unavailable`:** constructor `new IOException(message, cause)` giữ nguyên exception nền; caller có thể xem `getCause()` và stack trace tương ứng.
- **`honest state: [order-created]`:** truyền lại exception không đảo ngược tác động trước lỗi. Nếu nghiệp vụ đòi hỏi atomicity, phải thiết kế thêm cơ chế phù hợp.
- **`lookup: true`:** `Optional.empty()` thể hiện một kết quả tìm kiếm không thấy `"B"` trong trường hợp hợp lệ; không dùng nó để thay thế lỗi đọc storage.
- **`fallback: false`:** ví dụ boolean phản ánh rằng audit không thành công. Đây là một lựa chọn API tối giản, không phải lời khuyên dùng boolean cho mọi loại lỗi; hệ thống lớn nên cân nhắc result có mã nguyên nhân.

Với ứng dụng thực tế, cần chọn tên trả kết quả rõ hơn (ví dụ `auditSucceeded`), giữ lỗi để ghi nhận/đối soát khi cần, và bảo đảm dữ liệu không lộ thông tin nhạy cảm. Mẫu `attemptWithFallback` chỉ có thể xem là “đã xử lý” nếu hợp đồng công khai rằng `false` là **kết quả thất bại có ý nghĩa**, không phải `true` ở mọi trường hợp hoặc một biến bị bỏ qua.

## 5. Phân biệt “bắt rồi xử lý” với “bắt rồi nuốt”

Một `catch` là xử lý hợp lệ khi thực hiện ít nhất một hành động phù hợp với hợp đồng API:
- **Khôi phục thật:** retry có kiểm soát hoặc dùng nguồn dự phòng thỏa điều kiện, sau đó xác nhận kết quả đúng.
- **Truyền tiếp:** `throw` cùng exception, hoặc wrap với cause và ngữ cảnh.
- **Trả kết quả thất bại:** typed result, mã lỗi, trạng thái pending/unknown hoặc một checked outcome phù hợp.
- **Đánh dấu transaction rollback-only:** khi transaction manager thực sự hỗ trợ và chương trình còn trong phạm vi transaction.
- **Chấp nhận bỏ qua một tình huống không quan trọng:** có chính sách rõ, không ảnh hưởng invariant và có thể giải thích vì sao mất sự kiện đó an toàn.

Ví dụ bỏ qua hợp lý: cố xóa một **file tạm đã được xác định là không còn cần thiết**, gặp lỗi “file không còn tồn tại” trong một phương thức cleanup vốn được thiết kế **idempotent**. Tuy vậy, nên dùng API mang ý nghĩa trực tiếp như `Files.deleteIfExists(path)` và xử lý những lỗi khác (ví dụ permission denied) theo mức độ quan trọng; không catch cả `IOException` rồi giả định tất cả đều là “không tồn tại”.

### 5.1. Cách xác minh một fallback có thật sự phục hồi?

Hỏi:
1. Kết quả sau fallback có thỏa contract giống nhánh thành công hay được khai báo khác biệt?
2. Dữ liệu fallback có thể cũ hoặc thiếu? Caller có cần biết nguồn không?
3. Có side effect nào của bước gốc đã xảy ra trước khi lỗi ném?
4. Retry có tạo duplicate hay vi phạm idempotency không?
5. Nếu fallback cũng thất bại, tín hiệu nào được truyền tiếp?

Ví dụ đọc cache khi database mất kết nối có thể hợp lý đối với giao diện chỉ đọc chấp nhận dữ liệu cũ, nếu freshness được mô tả rõ. Nhưng cache fallback không hợp lý cho thao tác thanh toán cần kết quả commit chính xác.

## 6. Không dùng `Optional.empty()` hay `null` để che mọi lỗi

Cách sai:

~~~java
Optional<Customer> findCustomer(String id) {
    try {
        return repository.findById(id);
    } catch (Exception ignored) {
        return Optional.empty();
    }
}
~~~

Đây là **fragment minh họa không độc lập**, `Customer` và `repository` chưa được định nghĩa. Nó làm “không có khách hàng” không thể phân biệt với “database hỏng”, “không được phép truy cập” hay một bug `NullPointerException`. Caller có thể tự tạo khách hàng trùng, ghi nhầm trạng thái hoặc trả HTTP 404 thay vì 503.

Một contract rõ hơn:
- `Optional<Customer>` chỉ thể hiện **có hoặc không có khách hàng** khi truy vấn được thực hiện hợp lệ.
- Lỗi storage chưa được phục hồi thì truyền exception hoặc trả `Result.Error(code, cause)` riêng.
- Nếu cần biết sự khác biệt giữa “không tồn tại” và “chưa kiểm tra được”, một sealed result type như `Found`, `NotFound`, `Unavailable` có thể biểu đạt đầy đủ.

Dùng `null` cho cả vắng mặt và lỗi còn nguy hiểm hơn vì dễ gây `NullPointerException` ở tầng trên, làm mất thông tin về ngoại lệ gốc. Không lấy những giá trị mặc định `0`, `false`, chuỗi rỗng hoặc danh sách rỗng thay cho “lỗi đã xảy ra” nếu chúng đồng thời là kết quả hợp lệ.

## 7. Checked exception, unchecked exception và catch rộng

Các `IOException`, `SQLException` là **checked exceptions**, thường cần bắt hoặc khai báo `throws`; `NullPointerException`, `IllegalArgumentException`, `IllegalStateException` là **unchecked exceptions**. `Error` cũng thuộc `Throwable` nhưng là nhánh khác, thường báo lỗi nghiêm trọng không nên che.

~~~java
try {
    executeBusinessOperation();
} catch (Exception ignored) {
    // Anti-pattern: nuốt cả lỗi hạ tầng và bug RuntimeException.
}
~~~

Đây là mảnh minh họa, cần phương thức `executeBusinessOperation()`. Một `catch (Exception)` ban đầu nhằm bỏ qua lỗi I/O sau refactor có thể nuốt thêm NPE hoặc lỗi invariant. `catch (Throwable)` còn bắt cả `Error`.

**Trọng tâm ở đây:** `catch` rỗng làm mất tín hiệu thất bại. Phạm vi bắt và phân loại lỗi là vấn đề liên quan: chỉ gom nhiều ngoại lệ bằng multi-catch nếu chúng có cùng chính sách. Một supervisor/framework đặc biệt có thể bắt rộng để báo lỗi và kết thúc đơn vị công việc, nhưng **không được báo thành công** khi chưa phục hồi trạng thái.

## 8. Giữ nguyên nguyên nhân: rethrow, wrap và không tạo lỗi giả

Nếu `catch` không thể phục hồi, **đừng kết thúc bình thường**. Giữ cùng exception bằng `throw e;` khi chữ ký cho phép, hoặc dịch sang abstraction mới và giữ `cause`:

~~~java
try {
    persist();
} catch (IOException e) {
    throw new IllegalStateException("Could not persist order " + orderId, e);
}
~~~

Đây là mảnh minh họa có `persist()` và `orderId` từ ứng dụng; message cần tránh lộ dữ liệu nhạy cảm. Constructor `(String, Throwable)` giúp giữ lỗi gốc trong `getCause()`. Ngược lại, `new IllegalStateException("failed")` bỏ cause.

Nếu chỉ muốn để lỗi lan truyền và không có ngữ cảnh để thêm, **bỏ cả khối `catch`** thường tốt hơn. Bài chuyên về *ném ngoại lệ mới nhưng làm mất cause* sẽ giải thích kỹ cause chain và suppressed; điều cần nhớ ở đây là không **nuốt** lỗi rồi tạo ra thành công giả.

## 9. `InterruptedException` là tín hiệu hủy; nuốt nó đặc biệt nguy hiểm

Một lỗi thường gặp trong vòng lặp worker:

~~~java
while (running) {
    try {
        Thread.sleep(1_000);
        poll();
    } catch (InterruptedException ignored) {
    }
}
~~~

Đây là fragment minh họa, `running` và `poll()` chưa định nghĩa. Khi thread bị yêu cầu interrupt để hủy hoặc shutdown, `Thread.sleep` thường ném `InterruptedException` và xóa interrupt status. `catch` rỗng khiến vòng lặp tiếp tục; hệ thống có thể không dừng task dù executor đã yêu cầu hủy.

Mẫu hợp tác:

~~~java
Runnable worker = () -> {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            Thread.sleep(1_000);
            pollOnce();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }
};
~~~

Đây là fragment, `pollOnce()` chưa được khai báo. Việc khôi phục interrupt flag rồi thoát giúp tầng trên hoặc code teardown biết rằng thread đã nhận yêu cầu ngắt. Nếu phương thức hiện tại được phép khai báo `throws InterruptedException`, có thể truyền tiếp thay vì bắt.

Lưu ý: `Future.cancel(true)` không bảo đảm giết một task không hợp tác, và `CompletableFuture.cancel(true)` không phải lệnh interrupt supplier bên dưới theo hợp đồng `CompletableFuture`. Nuốt interrupt khiến những giới hạn này càng khó kiểm soát.

### 9.1. Vì sao không đánh đồng interrupt với “lỗi nghiệp vụ”?

`InterruptedException` thường biểu thị luồng đang đợi bị yêu cầu ngắt, không chứng minh task nào đó thất bại về dữ liệu. Một phương thức có thể trả trạng thái canceled, propagate interrupt hoặc restore flag tùy API. Nhưng đừng bắt nó rồi trả `true` như thể yêu cầu đã hoàn thành.

Khi worker có side effect, dừng sau interrupt cũng không tự hoàn tác các thay đổi đã xảy ra; cần transaction, idempotency, compensation hoặc báo trạng thái partial/unknown nếu cần.

## 10. Catch rỗng bên trong vòng lặp tạo “lỗi vô hình” và vòng retry nóng

Các đoạn kiểu:

~~~java
while (true) {
    try {
        consumeMessage();
    } catch (Exception ignored) {
    }
}
~~~

là fragment minh họa với `consumeMessage` chưa định nghĩa. Nếu `consumeMessage` liên tục thất bại ngay lập tức, vòng lặp có thể tiêu tốn CPU, tạo hàng nghìn lỗi bị nuốt, không backoff, không báo metric và không dừng được theo shutdown policy. Nếu message lỗi gây retry vô hạn, tiến trình có thể kẹt cùng một bản tin.

Một worker có chủ đích thường cần:
- phân loại lỗi recoverable vs permanent;
- giới hạn retry, delay/backoff và jitter hợp lý;
- dead-letter queue hoặc trạng thái failure terminal cho sự kiện không thể xử lý;
- idempotency/deduplication khi message có thể được redeliver;
- ngắt hợp tác, deadline và cleanup;
- ghi nhận lỗi có mã/ID tương ứng.

Không biến “có vòng retry” thành lý do bỏ qua mọi exception. Retry mà không có giới hạn, backoff và trạng thái vẫn là một dạng nuốt lỗi kéo dài.

## 11. Catch trong background task: caller có thể không bao giờ biết

Một `Runnable` tự catch mọi exception rồi return bình thường sẽ khiến cơ chế điều phối thấy task **đã hoàn tất bình thường**.

~~~java
Future<?> f = executor.submit(() -> {
    try {
        process();
    } catch (Exception ignored) {
    }
});
f.get(); // Có thể trả bình thường dù process() đã thất bại.
~~~

Đây là fragment, giả định `executor`, `process` và các checked exception từ `get()` được khai báo/xử lý. `ExecutorService.submit` ghi exception **thoát khỏi task** vào `Future`. Nếu task đã tự bắt và nuốt exception, Future không thể tự phát hiện thất bại đã bị giấu bên trong lambda.

Tương tự với `CompletableFuture`:

~~~java
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> {
            try {
                return remoteRead();
            } catch (RuntimeException ignored) {
                return "";
            }
        });
~~~

Fragment này giả định `remoteRead()` trả String và không ném checked exception chưa xử lý. Nếu một chuỗi rỗng là dữ liệu hợp lệ, `future.join()` có thể trả một kết quả “thành công” giả, làm `exceptionally` hoặc `whenComplete` ở tầng ngoài không thấy lỗi.

**Thiết kế tốt:** Nếu muốn chuyển exception thành result, hãy trả `Failed(ErrorCode, cause)`, không biến nó thành một `Success("")`. Nếu không có cách recovery đúng, để exception đi ra khỏi task để `Future.get()` hoặc `CompletableFuture.join()` truyền thông tin lỗi qua ranh giới luồng.

### 11.1. Không bỏ Future sau khi submit

Ngay cả khi worker **không** catch rỗng, nếu caller gọi `submit()` rồi không giữ/đọc Future, exception có thể được lưu bên trong handle và không có ai xử lý. Kết quả giống một “lỗi vô hình” ở cấp ứng dụng. Phải có người sở hữu kết quả: caller đọc `get()`, coordinator tổng hợp, callback xử lý `exceptionally`/`handle` hoặc bộ giám sát job có kênh trạng thái bền vững khi cần.

`UncaughtExceptionHandler` không thay thế `Future.get()` cho mọi task do executor chạy; `submit` thường bắt lỗi bên trong cấu trúc Future. Chỉ có handler cho raw thread mà không có kênh kết quả cũng không đủ để báo success/failure cho caller.

## 12. Có nên log ở mọi `catch` không?

Không. Một anti-pattern khác là:

~~~java
catch (IOException e) {
    logger.error("Repository failed", e);
    throw e;
}
~~~

Đây là fragment với logger giả định. Nếu service phía trên, controller và handler đều lặp lại “log rồi rethrow”, **một lỗi thực** có thể tạo bốn stack trace giống nhau. Hệ thống metric có thể đếm lỗi nhiều lần, còn người điều tra khó xác định bản ghi nào là kết quả cuối.

Một chính sách tốt:
- Tầng **có thể phục hồi**: ghi metric recovery hoặc warning theo nhu cầu, rồi tiếp tục với kết quả thật.
- Tầng **chỉ chuyển lỗi**: giữ cause, thêm ngữ cảnh nếu cần; không nhất thiết log mỗi lần.
- **Ranh giới chịu trách nhiệm trả kết quả/giám sát**: log lỗi một lần ở level phù hợp, kèm request/job ID, operation type và stack trace khi cần.
- Nếu thao tác phụ (audit, cleanup, metrics) lỗi riêng: phân biệt nguồn lỗi và nơi quan sát, không che exception chính.

Dù không log ở mọi tầng, tín hiệu lỗi vẫn phải **đi tới nơi có khả năng hành động**. “Không log trùng” không đồng nghĩa “nuốt exception”.

### 12.1. Đừng chỉ log message mà bỏ stack/cause

Ví dụ:

~~~java
logger.warn("failed: {}", e.getMessage());
~~~

Đây là fragment minh họa (API logging phụ thuộc framework); nó có thể bỏ mất stack trace và cause. Nếu ứng dụng cần chẩn đoán, truyền `Throwable` đúng vị trí mà logging framework hỗ trợ và bảo đảm dữ liệu nhạy cảm được kiểm soát. Nhưng thông điệp người dùng không nên chứa raw stack trace, password, token hoặc dữ liệu cá nhân.

## 13. Side effect đã xảy ra trước lỗi không tự biến mất

Một ví dụ nghiệp vụ:

~~~text
begin operation
   |
   +-- ghi file output thành công
   +-- commit transaction DB thành công
   +-- gửi HTTP tới payment service
   +-- timeout nhận response -> IOException
   |
   +-- catch rỗng
   +-- trả "OK"
~~~

Ngay cả nếu thay catch rỗng bằng `throw`, phần file và database đã commit vẫn không tự quay lại, còn payment service **có thể đã xử lý yêu cầu** dù client timeout.

Bốn trạng thái cần phân biệt:
1. **Lỗi xảy ra** — phải có thông tin exception/result.
2. **Thay đổi đã xảy ra** — có thể trước khi exception xuất hiện.
3. **Rollback còn khả thi không** — tùy transaction/file/protocol.
4. **Kết quả bên ngoài có chắc chắn không** — timeout có thể là trạng thái chưa biết.

Vì vậy, đừng trả “thất bại, chưa có gì xảy ra” một cách tự động. Nếu chưa thể xác định kết quả remote, trạng thái `UNKNOWN`/`PENDING` với operation ID và quy trình đối soát thường đúng hơn. Muốn retry an toàn các lệnh có side effect, dùng idempotency key được **server hỗ trợ thật**, không chỉ gửi một header đặt tên hấp dẫn.

### 13.1. Transaction rollback cần ranh giới đúng

Nếu dùng JDBC với `autoCommit=false` và thao tác trong một transaction hợp lệ, khi lỗi trước commit có thể gọi `rollback()`. Nhưng:
- `rollback()` không thu hồi những thay đổi đã commit trước đó;
- không tự rollback HTTP/email/file ngoài transaction;
- rollback cũng có thể thất bại;
- mất kết nối ngay lúc commit có thể khiến kết quả commit **không chắc chắn**.

Nuốt `SQLException` rồi tiếp tục dùng connection có trạng thái transaction không rõ có thể làm dữ liệu sai hoặc khiến request sau bị ảnh hưởng. Cần transaction manager/policy xử lý rõ ràng.

## 14. `try-with-resources`, `finally` và lỗi cleanup

Khối `catch` rỗng cũng có thể xuất hiện ở code đóng tài nguyên:

~~~java
try {
    writeOutput();
} finally {
    try {
        resource.close();
    } catch (IOException ignored) {
    }
}
~~~

Đây là fragment với `writeOutput` và `resource` giả định. Nếu `close()` fail, hệ thống có thể không biết dữ liệu đệm đã được flush hay chưa, hoặc resource thực sự được đóng hay không. Có tình huống việc đóng là best effort, nhưng phải có **hợp đồng và kênh chẩn đoán hợp lý**.

Với resource triển khai `AutoCloseable`, try-with-resources thường rõ hơn:

~~~java
try (OutputStream out = Files.newOutputStream(path)) {
    out.write(payload);
}
~~~

Đây là fragment; cần import `OutputStream`/`Files`, và `path`/`payload` chưa được khai báo. Nếu body và `close()` cùng ném exception, try-with-resources gắn lỗi đóng vào exception chính dưới dạng **suppressed exception** theo quy tắc Java. Dùng `e.getSuppressed()` để chẩn đoán nếu cần. Nếu tự catch rỗng quanh `close()`, bạn có thể đánh mất lỗi phụ đáng chú ý.

Tuy nhiên **đóng tài nguyên không phải rollback**. `close()` hoặc flush có thể làm dữ liệu đã đệm thực sự được ghi, không phải tự khôi phục file trước đó. Khi dùng giao thức file tạm và publish, chỉ dọn file tạm thuộc quyền sở hữu của thao tác; không xóa file chính trong `finally` một cách tùy tiện.

## 15. Chỉ bỏ qua exception khi hợp đồng thật sự cho phép

Có những việc “không thiết yếu” như cập nhật cache best effort, cố xóa file tạm đã không còn tồn tại, hoặc gửi một metric phụ. Việc không làm request chính thất bại **có thể là chính sách hợp lệ**. Nhưng nên diễn đạt chính sách rõ ràng:

| Tình huống | Bỏ qua có thể đúng? | Điều kiện |
| --- | --- | --- |
| Cache warm-up phụ trợ | Có thể | Miss/failure không đổi kết quả chính, có observability phù hợp |
| Xóa file tạm đã được dọn nơi khác | Có thể với trường hợp cụ thể | Phân biệt “file không tồn tại” với permission/I/O thực sự |
| Ghi metric không thiết yếu | Có thể | Được chấp nhận mất metric; không phá kết quả chính, có giới hạn lỗi |
| Lưu dữ liệu nghiệp vụ | Thường không | Caller cần biết trạng thái lưu thất bại |
| Ghi audit bắt buộc | Thường không | Vi phạm yêu cầu pháp lý/nghiệp vụ nếu mất dấu |
| Trừ tiền, tạo đơn, publish event quan trọng | Không thể nuốt chung | Cần kết quả/cancellation/idempotency/đối soát rõ |
| `InterruptedException` của worker | Thường không | Cần tôn trọng tín hiệu shutdown/hủy |
| `Error` nghiêm trọng của JVM | Không | Không được xem là lỗi thường rồi chạy tiếp mù quáng |

Kể cả khi được phép bỏ qua, có thể dùng:
- metric tổng hợp số lần lỗi để không mất khả năng cảnh báo;
- log có **rate limit** khi cần;
- trạng thái degraded/best-effort được mô tả trong API;
- cơ chế fallback không làm thay đổi invariant;
- kênh audit bền vững nếu lỗi phụ trở thành yêu cầu bắt buộc.

Đừng ép mọi tình huống đều phải log stack trace mức ERROR; log quá nhiều cũng che sự cố chính.

### 15.1. Exception có thể bỏ qua cần đúng loại, đúng thời điểm

Ví dụ `Files.deleteIfExists(path)` trả `false` khi file không tồn tại, nên thường không cần catch `NoSuchFileException` riêng cho tình huống bình thường. Nếu có race hoặc filesystem trả exception khác, bạn vẫn phải xem đó là vấn đề tài nguyên. Catch `IOException` rộng rồi bỏ hết có thể che permission denied hoặc disk failure mà hệ thống cần biết.

Tương tự, một thao tác bị hủy theo yêu cầu của người dùng có thể trả trạng thái `CANCELLED` thay vì exception hiển thị ra UI. Nhưng không vì thế mà nuốt mọi exception không liên quan trong đường hủy.

## 16. Result type là lựa chọn thay thế cho việc che lỗi

Trong một API cần xử lý nhiều tình huống dự kiến, có thể thiết kế result có kiểu:

~~~java
sealed interface SaveOutcome
        permits Saved, Rejected, TemporarilyUnavailable {}

record Saved(String id) implements SaveOutcome {}
record Rejected(String reason) implements SaveOutcome {}
record TemporarilyUnavailable(String code, Throwable cause)
        implements SaveOutcome {}
~~~

Đây là **fragment Java 17**, chưa có class ứng dụng bao quanh hoặc hàm save. Nó cho thấy:
- `Saved`: hoàn tất thực sự.
- `Rejected`: từ chối do quy tắc nghiệp vụ đã biết.
- `TemporarilyUnavailable`: lỗi hạ tầng được phân loại, kèm cause **nội bộ**.

Nếu một API trả `SaveOutcome`, caller phải xét từng nhánh, không chỉ `outcome != null` rồi cho thành công. Với external response, không serialize thẳng `Throwable`, stack trace hay thông tin nhạy cảm; chỉ đưa mã lỗi và thông báo an toàn.

Một result type không có phép màu: nếu phương thức đã swallow `IOException` rồi luôn tạo `new Saved(id)`, nó vẫn sai. Typed result chỉ hữu ích khi implementation **đặt đúng trạng thái dựa trên sự thật của công việc**.

### 16.1. Checked exception và contract của tầng trên

Nếu Java API chuyên dùng exception để biểu thị lỗi I/O, khai báo `throws IOException` và để caller quyết định recovery thường đơn giản hơn một hierarchy mới. Ngược lại, với API phải tổng hợp nhiều task và phân loại retry/rejected/not found, result có cấu trúc có thể rõ ràng hơn.

Không có nguyên tắc “mọi exception đều phải chuyển thành Result” hoặc “mọi lỗi phải ném unchecked”. Lựa chọn đúng phụ thuộc mức độ dự kiến của lỗi, quyền quyết định của caller và khả năng giữ thông tin khi đi qua boundary.

## 17. “Catch rỗng vì không muốn crash” thường tạo crash khó chẩn đoán hơn

Lập luận phổ biến là “nếu để exception lan ra, request/thread sẽ chết; bắt rỗng giúp ứng dụng ổn định”. Nhưng nếu một bước quan trọng thất bại:
- bộ nhớ/cache có thể không đồng bộ với database;
- khóa/transaction có thể vẫn ở trạng thái sai;
- caller sử dụng dữ liệu giả;
- các bước tiếp theo ném `NullPointerException` xa nguyên nhân thật;
- retry có thể nhân đôi side effect;
- hệ thống monitoring không biết cần can thiệp.

Vì vậy **cô lập thất bại** khác với **che thất bại**. Một worker supervisor có thể bắt exception ở ranh giới task, ghi nhận `FAILED`, đảm bảo cleanup và tiếp tục nhận công việc khác. Đó là thiết kế ổn định hơn việc nuốt lỗi ngay tại tầng xử lý nghiệp vụ.

Ví dụ một job handler:

~~~java
try {
    processJob(job);
    markCompleted(job.id());
} catch (IOException e) {
    markFailed(job.id(), "IO_ERROR", e);
}
~~~

Đây là fragment với các hàm giả định. Chính sách hợp lý nếu `markFailed` có cơ chế ghi trạng thái đáng tin, `processJob` không đã commit kết quả trái ngược, và caller phân biệt trạng thái failure. Nếu `markFailed` cũng ném exception, hệ thống phải có quy tắc xử lý/khôi phục. Không được xem catch đã hoàn thành nhiệm vụ chỉ vì có lời gọi `markFailed` trên giấy.

## 18. Empty catch bị công cụ phát hiện như thế nào?

Static analysis có thể phát hiện:
- `catch (Exception e) {}` hoặc block chỉ chứa comment;
- biến tên `ignored`/`unused` mà không có chính sách xử lý;
- `catch` trả giá trị thành công bất chấp exception;
- empty catch của `InterruptedException`;
- catching `Throwable`, broad exception;
- log-and-rethrow gây duplicate reporting;
- bỏ qua `Future` quan trọng.

Các IDE và công cụ như Checkstyle, PMD, SpotBugs hoặc Sonar có các rule/inspection liên quan, tùy cấu hình và phiên bản. **Không phụ thuộc một rule ID cố định** nếu chưa kiểm tra phiên bản công cụ đang dùng. Linter có thể bỏ qua trường hợp chứa comment “ignored”; comment đó không chứng minh thiết kế an toàn.

Một chính sách code review hiệu quả là yêu cầu tác giả của `catch` nói rõ:
1. Exception này thuộc loại nào, tại sao nó phát sinh?
2. Có còn side effect hoặc state chưa biết không?
3. Ai cần biết điều đó?
4. Tại sao recovery/fallback/return/throw được chọn?
5. Test nào chứng minh cả nhánh lỗi và nhánh thành công?

Những câu hỏi trên tốt hơn một lệnh cấm máy móc mọi `catch` không có log.

## 19. Kiểm thử để bắt lỗi bị nuốt

Chỉ kiểm tra rằng phương thức “không ném exception” là quá yếu. Cần **tiêm lỗi** vào dependency và khẳng định trạng thái kết quả:

| Điểm tiêm lỗi | Điều cần kiểm tra |
| --- | --- |
| Lỗi trước khi ghi dữ liệu | API báo thất bại; không phát thông báo thành công |
| Lỗi sau khi ghi một phần | Caller nhận partial/failed/unknown đúng hợp đồng |
| IOException từ repository | Không bị biến thành `Optional.empty()` hay danh sách rỗng giả |
| Callback audit lỗi sau commit | Không tuyên bố DB đã rollback; có trạng thái đối soát |
| `InterruptedException` trong worker | Tác vụ kết thúc/khôi phục flag hoặc propagate đúng chính sách |
| Future task ném exception | Caller/worker manager quan sát `ExecutionException.getCause()` |
| Retry gặp lỗi liên tiếp | Không chạy vòng nóng vô hạn; có giới hạn/backoff/metric |
| Lỗi cleanup | Không che mất lỗi chính; giữ suppressed/ghi nhận phụ khi phù hợp |
| Fallback cũng lỗi | Lỗi được truyền tiếp hoặc result phản ánh recovery thất bại |
| Log/metrics phụ gặp lỗi | Không vô tình đổi thành công nghiệp vụ thành thất bại nếu policy best effort |

### 19.1. Ví dụ unit test thể hiện tín hiệu lỗi phải tồn tại

JUnit 5 fragment sau giả định class `EmptyCatchLessonDemo` ở trên có sẵn:

~~~java
var store = new EmptyCatchLessonDemo.EventStore();

IOException thrown = assertThrows(IOException.class,
        () -> EmptyCatchLessonDemo.honest(store));

assertEquals("order may be partially processed", thrown.getMessage());
assertEquals("audit unavailable", thrown.getCause().getMessage());
assertEquals(List.of("order-created"), store.snapshot());
~~~

Đây là **fragment không độc lập**: cần dependency JUnit Jupiter, import `java.io.IOException`, `java.util.List` và static imports `assertThrows`, `assertEquals`. Test chứng minh caller biết lỗi và biết cause **ngay cả khi state đã thay đổi**. Điều đó giúp phân biệt yêu cầu phát tín hiệu thất bại với yêu cầu khôi phục toàn bộ state.

Nếu API hứa “mỗi lần save lỗi phải không có dữ liệu mới”, test phía trên phải yêu cầu snapshot rỗng và implementation phải dùng transaction/staging để đạt lời hứa. Đừng sửa expected test thành “hợp lệ” chỉ vì catch đã nuốt exception.

### 19.2. Test mất tín hiệu do worker nuốt lỗi

Với một `Callable<T>`, cấu hình dependency giả ném exception. Nếu task catch rồi trả mặc định, `Future.get()` sẽ trả giá trị bình thường. Test nên yêu cầu giá trị Result.Failed hoặc `ExecutionException`, tùy hợp đồng. Với `CompletableFuture`, kiểm tra `join()` báo `CompletionException` hoặc trả nhánh `Failed` khi ứng dụng cố ý phân loại, chứ không chỉ xem `isDone()`.

Đặc biệt, nếu bạn chỉ assert task đã kết thúc trong thời hạn, một task thất bại âm thầm có thể vẫn được tính là success.

## 20. Checklist khi review mỗi khối `catch`

- [ ] Loại exception bắt có **cụ thể** và phù hợp tầng xử lý không?
- [ ] Nếu catch rỗng hoặc bỏ qua, có **lý do nghiệp vụ đã kiểm chứng** chứ không chỉ comment “ignore”?
- [ ] Có thể xảy ra side effect hoặc partial state trước exception không?
- [ ] Sau catch, có dòng nào thông báo **thành công giả** không?
- [ ] Caller còn khả năng phân biệt thành công, vắng mặt, thất bại và chưa biết không?
- [ ] Nếu dùng fallback, dữ liệu fallback có đáp ứng invariant và freshness không?
- [ ] Nếu retry, có giới hạn/backoff và idempotency không?
- [ ] Nếu chuyển exception, có giữ `cause`/stack trace và ngữ cảnh không?
- [ ] Nếu gặp `InterruptedException`, có propagate hoặc restore flag/thoát phù hợp không?
- [ ] Nếu lỗi bên trong Future/CompletableFuture, có người sở hữu việc quan sát thất bại không?
- [ ] Nếu resource cleanup lỗi, có giữ suppressed hoặc tín hiệu chẩn đoán đúng mức không?
- [ ] Có log ở ranh giới phù hợp, không log trùng ở mọi tầng và không lộ bí mật?
- [ ] Unit/integration tests có tiêm lỗi và kiểm tra **trạng thái sau lỗi** không?
- [ ] Nếu bỏ qua lỗi phụ là chính sách, đã ghi rõ tính **best effort** và cách đo/giám sát suy giảm chưa?

## 21. Bài tập vận dụng

Một hệ thống nhận yêu cầu đặt hàng và thực hiện ba bước:

~~~text
(1) Save order to database
(2) Publish OrderCreated event
(3) Send confirmation email
~~~

Trong source có:

~~~java
try {
    saveOrder(order);
    publishEvent(order);
    sendEmail(order);
} catch (Exception ignored) {
}
return "ORDER_CREATED";
~~~

Đây là fragment với các hàm, biến giả định.

Hãy phân tích và sửa theo các tình huống:

1. `saveOrder` ném exception **trước commit**. Caller nên nhận gì? Nếu transaction còn mở thì rollback ở đâu?
2. `saveOrder` đã commit nhưng `publishEvent` ném exception. Vì sao không được khẳng định “order chưa tạo”?
3. `publishEvent` thực tế đã gửi sự kiện, nhưng client timeout; retry thế nào để tránh nhân đôi effect?
4. `sendEmail` thất bại nhưng yêu cầu chỉ bắt buộc lưu đơn, email là best effort. Nên biểu diễn điều này trong contract và monitoring như thế nào?
5. Nếu dùng `ExecutorService.submit` để gửi email, ai giữ Future/quan sát lỗi? Nếu task bắt rỗng, caller có biết không?
6. Viết unit test cố tình ném `IOException` ở từng bước, assert outcome/cause/trạng thái. Thêm test lỗi từ callback cleanup để không che lỗi chính.
7. Thiết kế một phương án lưu dữ liệu nghiệp vụ và bản ghi outbox trong **cùng DB transaction** khi cần phát event sau commit; giải thích vì sao outbox vẫn cần idempotent consumer khi retry.

## 22. Tóm tắt nguyên tắc

**Catch rỗng không đồng nghĩa đã giải quyết ngoại lệ.** Nó thường chỉ cắt đứt đường báo thất bại từ nơi xảy ra lỗi đến caller/hệ thống điều phối. Thao tác đã xảy ra trước exception vẫn có thể còn nguyên; việc tiếp tục và trả “thành công” có thể làm trạng thái sai lan sang các bước sau.

Khi viết hoặc review một `catch`, hãy chọn một trong các hành động có **hợp đồng rõ ràng**: phục hồi thực sự, truyền exception kèm cause, trả lỗi có cấu trúc, đánh dấu transaction thất bại, hoặc bỏ qua một lỗi không thiết yếu đã được chứng minh là an toàn. Với interruption và tác vụ bất đồng bộ, đừng nuốt tín hiệu hủy hay bỏ quên lỗi trong Future. Quan trọng nhất là kiểm thử **nhánh thất bại và trạng thái sau lỗi**, thay vì chỉ thấy chương trình không crash rồi kết luận nó hoạt động đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-002">Bắt Exception hoặc Throwable quá rộng ở mọi tầng</a>

<details>
<summary>Xem chi tiết</summary>

# Bắt Exception hoặc Throwable quá rộng ở mọi tầng: sai lầm phổ biến trong Java

## 1. Bản chất của vấn đề

Java dùng exception để báo một lời gọi **không thể hoàn tất theo hợp đồng**. Khi gặp exception, JVM tìm `catch` thích hợp trên call stack của **luồng đang thực thi**, chạy các khối dọn dẹp cần thiết rồi chuyển điều khiển đến handler. Vì thế, không cần và cũng không nên đặt một `catch` chung trong mọi phương thức trung gian.

Anti-pattern thường gặp:

```java
try {
    performBusinessOperation();
} catch (Exception ex) {
    System.err.println(ex.getMessage());
    return null;
}
```

Đây là **mảnh code cố tình minh họa sai lầm**, giả sử `performBusinessOperation()` đã được định nghĩa và method bao quanh cho phép `return null`. Nó trông có vẻ an toàn vì không có exception vượt ra ngoài, nhưng có thể:

- Giấu lỗi I/O hoặc bug lập trình và biến một thất bại thành kết quả hợp lệ giả.
- Làm mất khả năng phân biệt lỗi input, lỗi trạng thái, lỗi phụ thuộc và lỗi cancellation.
- Ngăn caller thực hiện retry, rollback, hiển thị mã lỗi hoặc báo động phù hợp.
- Log dữ liệu nhạy cảm qua `getMessage()` nhưng vẫn không giữ đầy đủ stack trace.
- Làm hệ thống tiếp tục ở trạng thái không đúng hợp đồng.

Một biến thể nghiêm trọng hơn:

```java
try {
    performBusinessOperation();
} catch (Throwable error) {
    return null;
}
```

`Throwable` còn bao gồm `Error`; bắt rồi nuốt nó không đảm bảo JVM hoặc thành phần bên dưới còn vận hành an toàn.

**Nguyên tắc:** một tầng chỉ nên bắt loại lỗi mà nó **thật sự có trách nhiệm và khả năng xử lý**: phục hồi hợp lệ, dịch lỗi sang contract của tầng, cleanup/rollback ở phạm vi sở hữu, ghi nhận kết quả thất bại hoặc trả thông báo ở boundary. Các lỗi khác nên tiếp tục lan truyền.

“Không bắt ở đây” không đồng nghĩa “bỏ qua lỗi”. Nó có thể là cách để tầng phù hợp hơn quyết định chính sách.

## 2. Phân cấp exception quan trọng

```text
java.lang.Throwable
├── Error
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   ├── LinkageError
│   └── AssertionError
└── Exception
    ├── IOException
    │   └── FileNotFoundException
    ├── SQLException
    ├── InterruptedException
    ├── ExecutionException
    └── RuntimeException
        ├── IllegalArgumentException
        ├── IllegalStateException
        ├── NullPointerException
        └── IndexOutOfBoundsException
```

Đây là cây **rút gọn**, không liệt kê mọi class.

| Catch | Phạm vi bắt được | Lưu ý |
| --- | --- | --- |
| `catch (IOException ex)` | `IOException` và subclass như `FileNotFoundException` | Thích hợp nếu tầng xử lý lỗi I/O |
| `catch (RuntimeException ex)` | Runtime exceptions | Có thể vô tình nuốt bug `NullPointerException` |
| `catch (Exception ex)` | Checked và unchecked thuộc `Exception` | **Không** bắt `Error`, nhưng **có** bắt `InterruptedException` |
| `catch (Throwable ex)` | Cả `Exception` và `Error` | Không chứng minh có thể phục hồi mọi lỗi |
| `catch (InterruptedException ex)` | Tín hiệu ngắt chờ | Cần quyết định truyền tiếp hoặc khôi phục interrupt flag |

Checked exception yêu cầu caller xử lý hoặc khai báo tại compile time; các subclass của `RuntimeException` và `Error` không chịu yêu cầu ấy. Nhưng **checked/unchecked không tự quyết định nơi catch**. Đó là vấn đề contract và chính sách.

Đặc biệt, `catch (Throwable)` không có nghĩa “bắt được mọi trạng thái phá hủy JVM”: process có thể bị kill, máy mất điện hoặc runtime không còn tài nguyên để thực thi handler.

## 3. Vì sao bắt rộng ở mọi tầng làm kiến trúc yếu đi?

Giả sử hệ thống có:

```text
Controller -> OrderService -> OrderRepository -> Database
```

Nếu mọi tầng đều làm:

```java
try {
    callNextLayer();
} catch (Exception ex) {
    logger.error("failed", ex);
    throw new RuntimeException("failed");
}
```

thì một lỗi `SQLException` có thể trở thành chuỗi nhiều wrapper `RuntimeException` không rõ loại. Vì constructor mới không có `cause`, **lỗi gốc có thể mất hẳn**. Nếu mỗi tầng ghi log, một sự kiện tạo nhiều stack trace trùng, làm ồn dashboard và tăng nguy cơ rò dữ liệu từ cause.

Tệ hơn, nếu repository trả `null` khi DB không sẵn sàng, service có thể tưởng “không tìm thấy đơn hàng”. Tầng HTTP trả 404 thay vì 503, khiến client không retry dù lỗi thật chỉ tạm thời.

Một vài biểu hiện:

1. Mọi method có `try/catch (Exception)` gần như giống nhau, không làm công việc hữu ích.
2. Handler trả `null`, `false`, `0`, object mặc định hoặc trạng thái “success” khi không thể chắc chắn thành công.
3. Lỗi đầu vào, lỗi business và lỗi hạ tầng bị bọc thành cùng một class.
4. Exception chỉ được log message mà không giữ cause/stack, hoặc bị log ở quá nhiều tầng.
5. Tác vụ đang hủy cũng bị đưa vào vòng retry như mọi lỗi khác.
6. `Error` nghiêm trọng bị swallow để tiến trình “cứ chạy”.

Đừng tối ưu cho “không xuất hiện exception trên console” mà bỏ qua **tính đúng của dữ liệu và hành vi caller**.

## 4. Call stack đã tự lan truyền lỗi qua các tầng

Ví dụ khái niệm:

```java
static void repository() throws IOException {
    throw new IOException("dependency unavailable");
}

static void service() throws IOException {
    repository();
}

static void entryPoint() {
    try {
        service();
    } catch (IOException ex) {
        // Một nơi có trách nhiệm chuyển lỗi thành phản hồi phù hợp.
    }
}
```

Đây là **các phương thức minh họa** cần đặt trong class và import `java.io.IOException`. `service()` không cần catch chỉ để ném lại. Nếu nó không có nghiệp vụ phục hồi hoặc translation, để exception lan truyền là hợp lý.

Trong lúc exception đi ngược stack:

- `finally` có cơ hội cleanup theo quy tắc Java.
- `try-with-resources` đóng tài nguyên `AutoCloseable`.
- Monitor của vùng `synchronized` được giải phóng khi unwind.
- Các thay đổi đã làm lên object, file hay DB **không tự được rollback**.
- Một exception trong worker thread **không tự** sang `catch` ở luồng khác; cần `Future` hoặc kênh chuyển kết quả.

Một `catch` thừa không làm stack unwinding an toàn hơn. Nó chỉ bổ sung một chỗ có thể che lỗi, biến đổi trạng thái hoặc ném một exception mới.

## 5. Ba anti-pattern chính

### 5.1. Swallow rồi trả giá trị giả

```java
String loadOrder(String id) {
    try {
        return repository.fetch(id);
    } catch (Exception ignored) {
        return "ORDER_READY";
    }
}
```

**Mảnh code minh họa sai**, với `repository` đã định nghĩa. Nếu repository offline, caller vẫn nhận `ORDER_READY`. Nếu kiểu kết quả là `Optional<T>`, trả `Optional.empty()` cho lỗi kết nối cũng sai nếu empty có nghĩa “không có bản ghi”.

Chỉ dùng fallback khi giá trị đó **được nghiệp vụ cho phép** và caller có thể hiểu trạng thái degraded nếu cần. Ví dụ cache cũ chỉ chấp nhận được nếu hệ thống cho phép đọc stale; không được tùy tiện dùng nó cho thanh toán.

### 5.2. Mất nguyên nhân khi rethrow

```java
catch (Exception ex) {
    throw new RuntimeException("Operation failed");
}
```

Mất `cause`, khiến tầng ngoài không biết nguồn thất bại.

Mẫu tốt hơn ở **ranh giới cần dịch lỗi**:

```java
catch (IOException ex) {
    throw new DependencyUnavailableException("Storage unavailable", ex);
}
```

Đây là mảnh code cần exception domain `DependencyUnavailableException` tự định nghĩa. Constructor nhận `cause` để giữ mối liên hệ lỗi. Tuy vậy, **giữ cause vẫn chưa đủ** nếu mọi loại lỗi được bắt rồi ép thành một class không đúng ý nghĩa.

### 5.3. Log rồi ném lại ở mọi lớp

```java
catch (Exception ex) {
    logger.error("Operation failed", ex);
    throw ex;
}
```

Đây là mảnh code framework logging và cần chữ ký phù hợp với checked exception. Nếu caller cũng log, sẽ có nhiều sự kiện cho cùng một lỗi. Mỗi tầng chỉ nên log khi thực sự có **thông tin vận hành hoặc trách nhiệm quan sát riêng**; thông thường log tại boundary cuối cùng hoặc nơi trạng thái được quyết định.

## 6. Ví dụ Java 17+ độc lập, chạy được

Chương trình này minh họa cách **bắt các loại lỗi I/O có ngữ nghĩa khác nhau**, giữ cause, không che validation error, đồng thời cho thấy hệ quả của catch quá rộng và xử lý interruption.

Lưu file **`BroadCatchDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class BroadCatchDemo {
    static final class OrderNotFoundException extends RuntimeException {
        OrderNotFoundException(String id, Throwable cause) {
            super("Order not found: " + id, cause);
        }
    }

    static final class DependencyUnavailableException extends RuntimeException {
        DependencyUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class Repository {
        String fetch(String id) throws IOException {
            if ("missing".equals(id)) {
                throw new FileNotFoundException("no row");
            }
            if ("offline".equals(id)) {
                throw new IOException("connection failed");
            }
            return "ORDER:" + id;
        }
    }

    static final class OrderService {
        private final Repository repository = new Repository();

        String load(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            try {
                return repository.fetch(id);
            } catch (FileNotFoundException ex) {
                throw new OrderNotFoundException(id, ex);
            } catch (IOException ex) {
                throw new DependencyUnavailableException(
                        "Order repository unavailable", ex);
            }
        }
    }

    static String brokenFallback(OrderService service, String id) {
        try {
            return service.load(id);
        } catch (Exception ignored) {
            return "FAKE_SUCCESS";
        }
    }

    static void waitForWork(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            boolean clearedByAwait = !Thread.currentThread().isInterrupted();
            Thread.currentThread().interrupt();
            System.out.println("INTERRUPT: cleared=" + clearedByAwait
                    + " restored=" + Thread.currentThread().isInterrupted());
        }
    }

    public static void main(String[] args) {
        OrderService service = new OrderService();
        System.out.println("SUCCESS: " + service.load("42"));

        try {
            service.load("missing");
        } catch (OrderNotFoundException ex) {
            System.out.println("NOT_FOUND: cause="
                    + ex.getCause().getClass().getSimpleName());
        }

        try {
            service.load("offline");
        } catch (DependencyUnavailableException ex) {
            System.out.println("UNAVAILABLE: cause="
                    + ex.getCause().getClass().getSimpleName());
        }

        try {
            service.load("");
        } catch (IllegalArgumentException ex) {
            System.out.println("VALIDATION: "
                    + ex.getClass().getSimpleName());
        }

        System.out.println("BROAD_CATCH_BUG: "
                + brokenFallback(service, "offline"));

        Thread.currentThread().interrupt();
        waitForWork(new CountDownLatch(1));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 BroadCatchDemo.java
java BroadCatchDemo
```

Standard output đã kiểm chứng:

```text
SUCCESS: ORDER:42
NOT_FOUND: cause=FileNotFoundException
UNAVAILABLE: cause=IOException
VALIDATION: IllegalArgumentException
BROAD_CATCH_BUG: FAKE_SUCCESS
INTERRUPT: cleared=true restored=true
```

**Giải thích:**

1. `service.load("42")` thành công, trả kết quả có ý nghĩa.
2. `FileNotFoundException` được bắt **trước** `IOException` vì là subclass; service dịch thành `OrderNotFoundException` và giữ cause.
3. Các `IOException` còn lại được dịch thành `DependencyUnavailableException`. Caller có thể phân biệt lỗi dependency với not found.
4. Validation input rỗng diễn ra trước gọi repository; `IllegalArgumentException` không bị đổi nhầm thành lỗi dependency.
5. `brokenFallback("offline")` là **mã sai được đưa vào để đối chiếu**, trả `FAKE_SUCCESS` trong khi repository thực sự thất bại.
6. `CountDownLatch.await()` thấy luồng đã bị interrupt, ném `InterruptedException` và xóa cờ; handler đặt lại cờ và kết thúc công việc. Dòng cuối xác nhận `cleared=true restored=true`.
7. Các ID như `42`, `missing` là giả. Nếu ID thực liên quan dữ liệu cá nhân hoặc bí mật, không đưa nó vào exception message/log mặc định như demo đơn giản này.

Code dùng Java 17 không có thư viện ngoài, không truy cập DB hoặc mạng thật. Vì thế output có thứ tự xác định và không phụ thuộc thời gian scheduler.

## 7. Bắt lớp đủ cụ thể theo chính sách, không nhất thiết bắt lớp lá

“Bắt hẹp” không có nghĩa mỗi subclass phải có `catch` riêng. Nếu tất cả `IOException` từ một API có cùng cách xử lý, `catch (IOException)` hoàn toàn hợp lý:

```java
try {
    remoteClient.fetch();
} catch (IOException ex) {
    throw new DependencyUnavailableException("Remote dependency failed", ex);
}
```

Đây là mảnh code với `remoteClient` và exception domain đã định nghĩa. Nhưng nếu `FileNotFoundException` có nghĩa nghiệp vụ “tài nguyên không tồn tại” còn `SocketTimeoutException` có nghĩa “phụ thuộc chậm”, service có thể cần phân biệt.

Trong Java, thứ tự catch theo subclass trước superclass rất quan trọng:

```java
try {
    read();
} catch (FileNotFoundException ex) {
    handleMissing(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
}
```

Mảnh code cần import `java.io.FileNotFoundException` và `java.io.IOException` cùng các phương thức ứng dụng. Đặt `catch (IOException)` trước `catch (FileNotFoundException)` sẽ gây lỗi compile vì nhánh subclass không thể tới.

Có thể dùng multi-catch với các kiểu **không có quan hệ subclass** nếu xử lý giống nhau:

```java
catch (SocketTimeoutException | ConnectException ex) {
    markTemporaryFailure(ex);
}
```

Đây là mảnh code cần import `java.net.SocketTimeoutException`, `java.net.ConnectException`, và định nghĩa `markTemporaryFailure`. Không viết `catch (Exception | IOException ex)` vì `IOException` nằm dưới `Exception`.

Một điểm quan trọng khác là **phạm vi try**: nếu bạn chỉ muốn biến lỗi I/O thành lỗi dependency, chỉ đặt phần gọi I/O vào try thay vì ôm cả validation, tính toán business, notification và cleanup.

## 8. Các tầng nên sở hữu loại lỗi nào?

| Tầng | Trách nhiệm chính | Hành động khi lỗi |
| --- | --- | --- |
| Input validation | Xác nhận tham số và schema | Báo lỗi tham số cụ thể; không giả mạo lỗi hệ thống |
| Repository/adapter | Kết nối database, filesystem, HTTP client | Giữ hoặc dịch lỗi dependency có cause |
| Service/use case | Quy tắc nghiệp vụ và trạng thái | Xử lý lỗi domain, quyết định retry/compensation khi được phép |
| Controller/API boundary | Mã HTTP/response an toàn | Mã lỗi công khai, correlation ID, fallback 500 cho bất ngờ |
| Worker/job runner | Quản lý vòng đời tác vụ | Phân biệt FAILED, CANCELLED, retryable; lưu trạng thái |
| Framework/global handler | Xử lý thất bại không dự kiến | Báo lỗi và giữ hệ thống ở trạng thái được phép |

Đây là bảng **định hướng**, không phải bắt buộc một kiến trúc phải có đủ các lớp. Điều cần đạt là mỗi lỗi có **một nơi chịu trách nhiệm xử lý**, các tầng còn lại không che hoặc nhân bản log vô ích.

Một câu hỏi code review rất hiệu quả: **nếu bỏ catch ở tầng này, có mất một hành động cần thiết hay không?** Nếu không mất gì ngoài một dòng “failed”, có thể bỏ catch.

## 9. Khi nào catch(Exception) có thể hợp lý?

Không phải mọi `catch (Exception)` đều là anti-pattern. Nó có thể phù hợp ở **boundary cuối**, với điều kiện các loại lỗi dự kiến đã được xử lý cụ thể, phần unexpected được ghi nhận là thất bại và không bị biến thành success.

### HTTP/controller boundary

```java
try {
    return handler.handle(request);
} catch (InvalidRequestException ex) {
    return badRequest();
} catch (UpstreamUnavailableException ex) {
    return serviceUnavailable();
} catch (Exception ex) {
    return internalServerError();
}
```

Đây là mảnh code khái niệm cần định nghĩa các exception, `handler` và response helper. Handler rộng cuối cùng chỉ giải quyết lỗi **không dự đoán được**: trả response an toàn, không phơi stack trace hay raw message ra client, và ghi nhận sự kiện ở nơi có trách nhiệm.

### Transaction owner

Một thành phần sở hữu transaction có thể cần bắt lỗi để **thử rollback** và giữ lỗi ban đầu. Nó không được kết luận “đã rollback” chỉ vì handler chạy, nhất là khi rollback cũng ném exception.

### Worker/job boundary

Job runner có thể bắt lỗi bất ngờ để cập nhật trạng thái FAILED, không cho một sự cố biến thành job SUCCESS. Nếu worker hỗ trợ cancellation, xử lý `InterruptedException` hoặc cancellation riêng trước khi dùng fallback chung.

### Adapter legacy

Một API bên ngoài chỉ khai báo `throws Exception` có thể được bọc **một lần** ở adapter với contract mới. Không sao chép catch đó xuyên mọi layer của ứng dụng.

**Tiêu chuẩn:** có chính sách rõ, không nuốt lỗi, không giữ tiến trình chạy “mù” sau lỗi và không làm mất thông tin cần cho caller.

## 10. `catch (Throwable)` nguy hiểm hơn vì bao gồm Error

`Error` có thể chỉ lỗi nghiêm trọng của JVM hoặc linkage. Khi nhận `OutOfMemoryError`, ngay cả logger, cấu trúc dữ liệu dự phòng hoặc logic retry có thể không còn đủ tài nguyên. `StackOverflowError` có thể xảy ra khi đệ quy vượt khả năng stack. `LinkageError` có thể liên quan đến bytecode/classpath không tương thích.

Anti-pattern:

```java
catch (Throwable error) {
    logger.warn("ignored", error);
    return defaultValue;
}
```

Đây là mảnh code **không khuyến nghị**. Nó giả định sau bất kỳ tình trạng lỗi nào, vẫn có thể in log, tạo fallback và tiếp tục phục vụ request đúng đắn.

Có **ranh giới đặc biệt** có lý do quan sát `Throwable`, như harness kiểm thử hoặc plugin-host cần ghi nhận trạng thái thất bại của module. Nhưng hành động thường là **báo thất bại, cách ly, hoặc rethrow theo chính sách**, không hứa phục hồi tất cả. Nếu chỉ muốn cleanup bất kể đường rời, `finally` và `try-with-resources` thường là công cụ thích hợp hơn.

Không biến `catch(Throwable)` thành lớp bảo hiểm mọi nơi. Nó không thể chống mất điện, bị kill process hoặc hết tài nguyên nghiêm trọng, và có thể tạo cảm giác an toàn sai.

## 11. `InterruptedException` bị nuốt bởi catch(Exception)

`InterruptedException` thường là tín hiệu một luồng đang chờ được yêu cầu dừng/hủy. Các API như `Thread.sleep`, `BlockingQueue.take`, `CountDownLatch.await` và `Future.get` có thể ném nó. Khi ném exception này, nhiều API **xóa interrupt flag** của luồng đang chờ.

Anti-pattern:

```java
while (true) {
    try {
        process(queue.take());
    } catch (Exception ex) {
        logger.warn("retrying", ex);
    }
}
```

Đây là mảnh code giả định `queue`, `process` và logger thuộc ứng dụng. Khi thread bị interrupt trong `take()`, exception bị catch chung; vòng lặp tiếp tục và có thể lại block như chưa từng được yêu cầu dừng.

Nếu một phương thức có thể khai báo `throws InterruptedException`, thường nên **truyền tiếp**:

```java
void consume(BlockingQueue<Job> queue) throws InterruptedException {
    process(queue.take());
}
```

Đây là mảnh code cần import `BlockingQueue`, định nghĩa `Job` và `process`.

Nếu nằm trong `Runnable.run()` không thể ném checked `InterruptedException`:

```java
@Override
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            process(queue.take());
        }
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    } finally {
        releaseResources();
    }
}
```

Đây là mảnh code trong class có queue và cleanup. **Khôi phục cờ + kết thúc đường thực thi** thích hợp hơn khôi phục rồi quay lại `while(true)`, vì thao tác chờ tiếp theo có thể bị interrupt ngay, gây vòng lặp nóng.

Tuy nhiên không bắt buộc restore cờ ở mọi tầng nếu `InterruptedException` đã được truyền tiếp đúng hợp đồng, hoặc một tầng **sở hữu toàn bộ giao thức hủy** đã xử lý dứt điểm tín hiệu.

## 12. Bắt RuntimeException quá rộng có thể che lỗi lập trình

`RuntimeException` có cả lỗi domain không checked lẫn lỗi lập trình như `NullPointerException`, `IndexOutOfBoundsException`. Đoạn:

```java
try {
    return calculateInvoice();
} catch (RuntimeException ex) {
    return BigDecimal.ZERO;
}
```

là mảnh code sai nếu `calculateInvoice` trả `BigDecimal` và một bug không được phép biến thành hóa đơn `0`; cần import `java.math.BigDecimal`.

Nếu trường hợp thiếu dữ liệu là hợp lệ, API nên biểu diễn nó theo contract, chẳng hạn `Optional.empty()` hoặc một result domain. Nếu xảy ra NPE bất ngờ, nên để lỗi tới boundary cuối để điều tra, không tạo dữ liệu tài chính sai.

Tương tự, một bug lập trình cần sửa tại nguồn, không được coi mọi runtime exception là sự cố mạng tạm thời để retry.

## 13. Cleanup đúng cơ chế thay vì catch vô nghĩa

Một lý do khiến nhiều người bắt rộng là sợ rò tài nguyên. Nhưng nếu tài nguyên triển khai `AutoCloseable`, Java đã có `try-with-resources`:

```java
try (InputStream in = Files.newInputStream(path)) {
    return in.read();
}
```

Đây là mảnh code trong method khai báo `throws IOException` và có biến `Path path`, cần import `java.io.InputStream` và `java.nio.file.Files`. Tài nguyên được đóng khi rời khối theo hợp đồng, kể cả khi đọc lỗi.

Với lock:

```java
lock.lock();
try {
    mutateSharedState();
} finally {
    lock.unlock();
}
```

Đây là mảnh code trong class có `lock` và `mutateSharedState()`. `finally` phù hợp để nhả lock mà không cần catch rộng. Lưu ý nhả lock **không rollback** những thay đổi trước exception.

Khi thân `try-with-resources` và `close()` cùng lỗi, Java thường giữ lỗi từ close trong `getSuppressed()` của exception chính. Nếu tự viết cleanup và cleanup ném, cần bảo toàn lỗi gốc. Không thêm `catch (Throwable)` chỉ để tự triển khai một cơ chế cleanup vốn đã có.

## 14. Transaction, rollback và trạng thái sau lỗi

Exception không tự rollback DB, file hoặc mạng. Ví dụ:

```java
try {
    debitAccount();   // Có thể đã commit.
    sendReceipt();    // Có thể lỗi sau commit.
    return true;
} catch (Exception ex) {
    return false;
}
```

Đây là mảnh anti-pattern trong method trả boolean có các hàm nghiệp vụ đã định nghĩa. Nếu `debitAccount` đã commit và `sendReceipt` ném, method trả `false` nhưng tiền **có thể đã trừ**. Caller retry toàn bộ quy trình có thể trừ thêm lần nữa.

Trong transaction owner, cần rõ:

1. Những thao tác nào thực sự thuộc **cùng transaction**?
2. Lỗi xảy ra trước hay sau commit?
3. Nếu `rollback()` cũng thất bại, trạng thái đã được xác minh chưa?
4. Nếu `commit()` mất phản hồi, có thể biết chắc DB đã/chưa commit không?
5. Tác dụng phụ ở hệ thống khác có được bù trừ hoặc đối chiếu riêng không?
6. Retry có idempotency và giới hạn phù hợp không?

Một catch rộng dùng để **thử rollback rồi rethrow** có thể hợp lý trong phạm vi transaction owner. Nhưng catch không tự bảo đảm “đã hoàn tác mọi hệ thống”. Nếu rollback ném lỗi, giữ lỗi ban đầu và thông tin rollback (ví dụ `addSuppressed`) cùng trạng thái chưa chắc chắn.

## 15. Future, CompletableFuture và lỗi bất đồng bộ

Một `catch(Exception)` quanh câu nộp task:

```java
try {
    CompletableFuture<Integer> future =
            CompletableFuture.supplyAsync(() -> compute());
} catch (Exception ex) {
    // Chỉ bắt lỗi đồng bộ ở luồng đang nộp.
}
```

Đây là mảnh code với `compute` đã định nghĩa. Nếu `compute` ném exception về sau ở worker, nó được ghi vào trạng thái future, **không tự lan vào catch quanh câu tạo future**.

Khi caller quan sát:

```java
try {
    int result = future.get();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
} catch (ExecutionException ex) {
    handleTaskFailure(ex.getCause());
}
```

Đây là mảnh trong method `void`, có `future`, `handleTaskFailure`, import `ExecutionException`. Phân biệt `InterruptedException` của **thread caller đang chờ** với `ExecutionException` có cause từ worker. `CancellationException` có nghĩa hủy; `get(timeout, unit)` còn có thể ném `TimeoutException`, không có nghĩa task đã dừng.

Với `CompletableFuture.join()`, lỗi thường được báo dưới dạng `CompletionException`. Dùng `exceptionally` hoặc `handle` để phục hồi chỉ khi fallback có ý nghĩa nghiệp vụ; nếu cứ `exceptionally(ex -> null)` ở mọi stage thì pipeline có thể hoàn tất “thành công” với dữ liệu không hợp lệ.

Một job scheduler cũng cần biết tác vụ FAILED hay CANCELLED; catch rộng rồi trả bình thường có thể làm framework đánh dấu job là SUCCESS và tiếp tục lịch sai chính sách.

## 16. Logging exception: ít điểm log, đủ bối cảnh, không lộ dữ liệu

Nếu mỗi tầng log rồi rethrow, dashboard có thể có mười cảnh báo cho một lỗi. Chọn **một điểm quan sát cuối** cho từng request/task khi có thể, cộng metric/ngữ cảnh riêng ở những nơi thực sự cần.

Một policy hữu ích:

- Domain/API error code ổn định cho caller.
- Operation name thuộc allowlist, request/correlation ID an toàn.
- Thông tin retry, timeout, dependency category khi phù hợp.
- Throwable cause giữ nội bộ để chẩn đoán nếu được phép.
- Không đưa password, token, cookies, raw request body hoặc dữ liệu cá nhân vào exception message/log.

Đừng trả `ex.getMessage()` hay `ex.toString()` trực tiếp về HTTP response; dependency có thể đã chèn URL, header hoặc thông tin bí mật vào message. Ngược lại, đừng xóa cause ở mọi tầng một cách mù quáng vì sẽ khó điều tra. Phải **phân biệt dữ liệu giữ trong exception và dữ liệu được phép xuất ra sink**.

Một fallback boundary có thể gửi response 500 an toàn và ghi mã sự kiện; controller không cần log lại toàn bộ exception đã được ghi đầy đủ ở một nơi khác.

## 17. Lỗi checked và khai báo throws Exception quá rộng

Một mẫu song hành với catch quá rộng là:

```java
public void process() throws Exception {
    repositoryCall();
}
```

Đây là mảnh code với `repositoryCall` đã định nghĩa. Nó có thể hợp lệ trong một vài test hay entrypoint ngắn, nhưng API công khai chỉ khai báo `Exception` khiến caller không biết nên chuẩn bị xử lý điều kiện nào, thường dẫn tới `catch(Exception)` tiếp tục lan rộng.

Ví dụ hợp đồng hẹp hơn:

```java
void readConfig(Path path) throws IOException {
    Files.readString(path);
}
```

Đây là mảnh code cần import `java.nio.file.Path`, `java.nio.file.Files` và `java.io.IOException`. Nó biểu thị nhóm lỗi I/O phù hợp. Không cần liệt kê mọi subclass trừ khi caller có chính sách đặc hiệu.

Nếu “không tìm thấy dữ liệu” là trường hợp bình thường, cân nhắc `Optional<T>` hoặc result domain thay vì ném rồi catch ở nhiều tầng. Nhưng không nên đồng nhất lỗi kết nối DB với `Optional.empty()` vì **không biết có dữ liệu** khác hoàn toàn **biết là không có dữ liệu**.

## 18. Retry không thể áp dụng cho mọi Exception

Vòng lặp này đặc biệt nguy hiểm:

```java
while (true) {
    try {
        return chargeCustomer();
    } catch (Exception ignored) {
        // Retry ngay, không giới hạn.
    }
}
```

Đây là **mảnh anti-pattern**, giả định method trả kết quả tương ứng và `chargeCustomer()` có tác dụng phụ. Nó có thể retry bug validation, làm cạn CPU, hoặc tạo giao dịch trùng nếu lệnh trước **đã commit nhưng phản hồi thất lạc**.

Retry có trách nhiệm phải biết:

- Loại lỗi nào thực sự tạm thời.
- Lệnh có idempotency key hay không.
- Kết quả sau timeout là thất bại chắc chắn hay chưa xác định.
- Ngân sách retry, timeout, backoff/jitter.
- Có tín hiệu `InterruptedException`/cancellation từ caller không.
- Có tầng khác đã retry sẵn hay không, tránh nhân số lần gọi.

Một phương thức catch rộng rồi retry ở mọi tầng có thể tạo *retry storm*. Bắt cụ thể theo chính sách giúp tránh điều đó.

## 19. Khi nào nên log và rethrow?

Không cần catch chỉ để:

```java
try {
    call();
} catch (IOException ex) {
    throw ex;
}
```

Đây là mảnh code trong method có `throws IOException`; bỏ catch không đổi hành vi cơ bản.

Catch rồi rethrow có giá trị khi:

1. Dịch lỗi ở ranh giới adapter sang exception domain, **giữ cause**.
2. Cập nhật trạng thái FAILED/CANCELLED mà tầng dưới không sở hữu.
3. Thử rollback ở transaction owner.
4. Thêm bối cảnh **không nhạy cảm** cần thiết, không chỉ viết “failed”.
5. Ghi metric có ý nghĩa tại một nơi duy nhất.
6. Bảo đảm cleanup đặc thù không thể thể hiện rõ hơn qua `finally`/`try-with-resources`.

Nếu handler có thể ném lỗi khác trong khi xử lý, cần quyết định bảo toàn cause/suppressed và trạng thái sau failure. Đừng tuyên bố đã phục hồi khi rollback hoặc cleanup chưa chắc thành công.

## 20. Test phát hiện catch rộng gây sai hành vi

Các test cần tiêm lỗi để xem không chỉ loại exception, mà còn **trạng thái và tác dụng phụ sau lỗi**.

| Tình huống | Kỳ vọng |
| --- | --- |
| Input không hợp lệ | Error validation phù hợp, không gọi repository |
| Dữ liệu không tồn tại | Contract not found rõ ràng, khác unavailable |
| Repository offline | Lỗi dependency được báo, không có success giả |
| Bug `NullPointerException` | Không bị biến thành `0`, `null` hoặc success |
| Blocking worker bị interrupt | Không nuốt tín hiệu, worker kết thúc theo policy |
| Future hoàn tất exceptionally | Caller lấy đúng `getCause()` |
| Transaction thất bại giữa các bước | Rollback đúng phạm vi hoặc báo chưa xác minh |
| Timeout/commit không rõ kết quả | Không retry mù gây tác dụng phụ trùng |
| Cleanup hoặc rollback cũng lỗi | Lỗi chính và thông tin lỗi phụ được bảo toàn |
| Logger gặp Throwable nhạy cảm | Client/log rộng quyền không lộ token hoặc PII |
| `Error` nghiêm trọng | Không có catch chung nuốt rồi cho hệ thống chạy như success |

Với `BroadCatchDemo`, test quan trọng là `service.load("missing")` và `service.load("offline")` khác loại domain nhưng đều giữ cause, validation rỗng vẫn đúng loại, còn `brokenFallback` phải bị đánh dấu **sai có chủ đích** khi trả `FAKE_SUCCESS`.

Mọi test có worker cần timeout và cleanup, không để process treo. Kiểm thử rollback phải xét side effect, không chỉ `assertThrows`.

## 21. Checklist code review thực dụng

1. Mỗi `catch` phải có **lý do xử lý ở tầng đó**.
2. Nếu chỉ rethrow y nguyên và không bổ sung hành động, thường nên bỏ.
3. Bắt nhóm exception đủ hẹp theo chính sách; subclass trước superclass.
4. Không bắt `Throwable` đại trà; không mặc định có thể phục hồi từ `Error`.
5. Không biến validation, dependency, cancellation và bug thành cùng một exception mơ hồ.
6. Khi chuyển loại lỗi, giữ cause và mã lỗi có ý nghĩa.
7. Không trả kết quả giả để che lỗi không được phép fallback.
8. Đừng nuốt `InterruptedException`; truyền tiếp hoặc restore và dừng đúng đường.
9. Dùng `try-with-resources`/`finally` cho cleanup, không catch rộng vô nghĩa.
10. Rollback phải theo transaction boundary; catch không tự hoàn tác thay đổi đã commit.
11. Chỉ retry khi lỗi và thao tác đáp ứng chính sách retry an toàn.
12. Tránh log trùng ở nhiều tầng; không tiết lộ secret/PII qua message và cause.
13. Với future/async, quan sát kết quả bất đồng bộ, không dựa vào catch lúc submit.
14. Boundary cuối có thể có fallback cho unexpected `Exception` nhưng vẫn đánh dấu fail, trả lỗi an toàn.
15. Kiểm thử cả lỗi dự kiến, bug bất ngờ, interrupt và trạng thái sau thất bại.

**Kết luận:** `catch (Exception)` và `catch (Throwable)` không phải lớp bảo hiểm có thể dán vào mọi phương thức. Bắt quá rộng ở mọi tầng làm mất phân loại, che bug, nuốt tín hiệu hủy, gây log trùng và biến failure thành success giả. Hãy bắt ở **tầng sở hữu hành động xử lý**, cho lỗi không xử lý được tiếp tục lan truyền, và dùng đúng cơ chế cleanup, rollback, reporting và retry.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-003">Vừa ghi log vừa ném lại ở nhiều tầng</a>

<details>
<summary>Xem chi tiết</summary>

# Vừa ghi log vừa ném lại ở nhiều tầng: tránh nhân bản lỗi trong Java

> **Chủ đề:** Java Core → Exception → Sai lầm thường gặp. Bài này phân tích anti-pattern **log-and-rethrow**: một exception đi qua repository, service, controller hoặc worker nhưng mỗi tầng lại ghi lỗi rồi ném tiếp. Mục tiêu là thiết kế một **ranh giới ghi nhận lỗi có trách nhiệm**, giữ nguyên nguyên nhân và ngữ cảnh, tránh stack trace trùng lặp, nhưng vẫn không làm mất các sự kiện vận hành thực sự có giá trị.

## 1. Vấn đề: một thất bại có thể trở thành nhiều dòng ERROR

Giả sử việc lưu đơn hàng thất bại do database không sẵn sàng. Lỗi đi qua ba tầng:

~~~text
database call
   |
   v
Repository ── catch, ERROR, throw ──>
   |
   v
Service    ── catch, ERROR, throw ──>
   |
   v
Controller ── catch, ERROR, response
~~~

Nếu mỗi tầng đều ghi cùng stack trace, một thất bại thực tế có thể tạo ba sự kiện `ERROR`, nhiều bản stack trace và các thông báo chỉ khác nhau rất ít. Hậu quả không chỉ là tốn dung lượng log:

- **Đếm lỗi bị phóng đại:** dashboard nhóm theo log ERROR có thể trông như ba lỗi độc lập trong khi chỉ một request thất bại.
- **Khó xác định nơi xử lý cuối:** có log ở repository rồi service rồi controller, nhưng không rõ tầng nào đã quyết định retry, fallback hoặc trả lỗi ra bên ngoài.
- **Chi phí vận hành:** formatting stack trace, ghi log, gửi và lưu log tăng đáng kể nếu volume lớn.
- **Đánh mất mối quan hệ:** ba dòng không có correlation ID có thể bị hiểu nhầm là ba request.
- **Che dấu tín hiệu thật:** log lặp lại cạnh tranh với những lỗi độc lập, khiến cảnh báo ồn và khó phân loại.
- **Nguy cơ lộ dữ liệu:** cùng một message chứa thông tin nhạy cảm bị sao chép qua nhiều log sink.

Quy tắc thực hành: **khi chỉ chuyển exception lên tầng trên để nơi đó quyết định xử lý, thường không cần ghi ERROR và toàn bộ stack trace tại mỗi tầng trung gian**. Hãy bổ sung `cause`/ngữ cảnh và ghi log ở một ranh giới phù hợp với quyền sở hữu kết quả.

Quy tắc này không có nghĩa mỗi lỗi trên toàn bộ hệ thống chỉ được tạo đúng một dòng log. Một attempt retry hoặc một compensation có thể là **sự kiện riêng biệt** cần ghi; vấn đề là không nhân bản *cùng một failure event* mà không thêm thông tin mới.

## 2. Định nghĩa log-and-rethrow và ranh giới xử lý

**Log-and-rethrow** là mẫu:

~~~java
// Mã minh họa, log được cung cấp bởi thư viện logging của ứng dụng.
try {
    repository.save(order);
} catch (RuntimeException ex) {
    log.error("save failed", ex);
    throw ex;
}
~~~

Đoạn này giữ nguyên exception nên không làm mất `cause`, nhưng nếu tầng trên cũng bắt, ghi ERROR và ném tiếp, cùng một lỗi được ghi nhiều lần. Khi nhiều lớp cùng áp dụng mẫu này mặc định, lượng log tăng theo số tầng mà exception đi qua.

Phân biệt ba vai trò:

1. **Nơi phát hiện:** nơi API không thể hoàn tất và exception được tạo hoặc ném; có thể không biết request/business context đầy đủ.
2. **Nơi dịch ngữ nghĩa:** nơi đổi lỗi kỹ thuật thành exception miền nghiệp vụ/abstraction mới; có thể thêm operation, business ID và `cause` mà không cần log tại chỗ.
3. **Nơi quyết định kết quả:** tầng biên HTTP, consumer, scheduled job, CLI hoặc event loop, nơi quyết định retry, fallback, fail request hay stop job; thường là nơi hợp lý để ghi **failure cuối cùng**.

Nếu một tầng **thực sự xử lý** lỗi (ví dụ phục hồi bằng fallback và không ném tiếp), tầng ấy có thể ghi sự kiện kết quả riêng. Nếu tầng chỉ chuyển tiếp lỗi, đừng tự động ghi một bản ERROR đầy đủ.

## 3. Phản ví dụ: mỗi tầng log rồi lại throw

~~~java
// Trích đoạn các tầng của một hệ thống, chưa phải chương trình độc lập.
void saveInRepository(Order order) {
    try {
        jdbcWrite(order);
    } catch (SQLException ex) {
        log.error("Repository save failed", ex);
        throw new StorageException("write failed", ex);
    }
}

void placeOrder(Order order) {
    try {
        saveInRepository(order);
    } catch (StorageException ex) {
        log.error("Service placeOrder failed", ex);
        throw new OrderProcessingException("order failed", ex);
    }
}

void handleHttpRequest(Order order) {
    try {
        placeOrder(order);
    } catch (OrderProcessingException ex) {
        log.error("Request failed", ex);
        sendErrorResponse();
    }
}
~~~

Các kiểu `Order`, `StorageException`, `OrderProcessingException` và các hàm được giả định do ứng dụng định nghĩa; mã này chỉ minh họa anti-pattern. Trong flow trên:

- `Repository` log stack trace rồi chuyển thành exception lưu trữ.
- `Service` log chuỗi stack/cause đã được ghi rồi bọc lần nữa.
- `Controller` log cùng chuỗi lần thứ ba.
- Nếu cả ba dòng được tính là failure độc lập, tỷ lệ sự cố bị nhân lên.
- Thông điệp `"failed"` lặp lại không cung cấp correlation ID, error code hoặc trạng thái request.

Cần sửa hai vấn đề độc lập: **vị trí log** và **chất lượng context/cause**. Chỉ xóa log ở tầng thấp mà ném exception không có cause cũng chưa đủ để điều tra.

## 4. Java 17 chạy độc lập: ba log sai so với một log đúng

Ví dụ dùng `System.out` thay cho thư viện logging để có **stdout xác định**. Chương trình không mô phỏng chi phí stack trace, mà cho thấy rõ số lượng sự kiện ERROR và cách giữ cause qua các tầng.

Lưu thành tệp **`LogAndRethrowDemo.java`**:

~~~java
public class LogAndRethrowDemo {
    static final class StorageFailure extends RuntimeException {
        StorageFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class OrderFailure extends RuntimeException {
        private final String orderId;

        OrderFailure(String orderId, Throwable cause) {
            super("place order failed", cause);
            this.orderId = orderId;
        }

        String orderId() {
            return orderId;
        }

        String errorCode() {
            return "ORDER_WRITE_FAILED";
        }
    }

    static void writeToDatabase() {
        throw new IllegalStateException("connection lost");
    }

    static void badRepository() {
        try {
            writeToDatabase();
        } catch (IllegalStateException ex) {
            System.out.println("ERROR repository: " + ex.getMessage());
            throw new StorageFailure("save failed", ex);
        }
    }

    static void badService() {
        try {
            badRepository();
        } catch (StorageFailure ex) {
            System.out.println("ERROR service: " + ex.getMessage());
            throw new OrderFailure("order-42", ex);
        }
    }

    static void badBoundary() {
        try {
            badService();
        } catch (OrderFailure ex) {
            System.out.println("ERROR boundary: " + ex.getMessage());
            Throwable root = rootCause(ex);
            System.out.println("BAD root cause: "
                    + root.getClass().getSimpleName()
                    + ": " + root.getMessage());
        }
    }

    static void goodRepository() {
        try {
            writeToDatabase();
        } catch (IllegalStateException ex) {
            throw new StorageFailure("save failed", ex);
        }
    }

    static void goodService() {
        try {
            goodRepository();
        } catch (StorageFailure ex) {
            throw new OrderFailure("order-42", ex);
        }
    }

    static void goodBoundary() {
        try {
            goodService();
        } catch (OrderFailure ex) {
            System.out.println(
                    "ERROR event=place_order_failed operation=place_order"
                    + " requestId=req-8 orderId=" + ex.orderId()
                    + " code=" + ex.errorCode());

            Throwable root = rootCause(ex);
            System.out.println("GOOD root cause: "
                    + root.getClass().getSimpleName()
                    + ": " + root.getMessage());
        }
    }

    static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static void main(String[] args) {
        badBoundary();
        goodBoundary();
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac LogAndRethrowDemo.java
java LogAndRethrowDemo
~~~

**Stdout chính xác:**

~~~text
ERROR repository: connection lost
ERROR service: save failed
ERROR boundary: place order failed
BAD root cause: IllegalStateException: connection lost
ERROR event=place_order_failed operation=place_order requestId=req-8 orderId=order-42 code=ORDER_WRITE_FAILED
GOOD root cause: IllegalStateException: connection lost
~~~

### Diễn giải

- **Nhánh bad:** repository, service và boundary mỗi nơi ghi một sự kiện ERROR; ba dòng ERROR đều bắt nguồn từ một thao tác ghi database bị lỗi.
- **Nhánh good:** repository chỉ chuyển lỗi `IllegalStateException` thành `StorageFailure` có cause; service chỉ chuyển thành `OrderFailure` có business ID và cause; boundary ghi **một sự kiện ERROR** đủ operation, request ID, order ID và error code.
- Hàm `rootCause` đi qua chuỗi cause để minh họa rằng thông tin `IllegalStateException("connection lost")` vẫn có thể được truy hồi sau hai lớp bọc. Trong chương trình thật, exception tree có thể còn `suppressed` và wrapper riêng; đừng mặc định “cause sâu nhất là toàn bộ sự thật”.
- Hai lời gọi bad/good trong `main` là **hai lượt minh họa độc lập**, không phải hai lần xử lý cùng một request production.
- Dòng `GOOD root cause` là dòng giải thích cho người học, **không phải ERROR log thứ hai**. Trong production nên log toàn bộ `Throwable` một lần tại boundary để backend giữ stack trace, cause chain và suppressed exceptions.

Việc sử dụng `System.out` trong ví dụ không phải khuyến nghị triển khai logging production. Chọn logger có level, structured fields và cơ chế thu thập tập trung thích hợp.

## 5. Thiết kế mới: ném có ngữ cảnh, ghi ở nơi chịu trách nhiệm

Mẫu thiết kế thường phù hợp với ứng dụng theo tầng:

~~~text
Repository
  -> bắt lỗi kỹ thuật khi cần dịch abstraction
  -> throw StorageException(cause)

Service
  -> bổ sung business operation hoặc error code
  -> throw OrderException(cause)

Boundary
  -> phân loại lỗi / quyết định HTTP status hoặc job outcome
  -> log một failure event có context + Throwable
  -> trả response / đánh dấu tác vụ lỗi / chuyển lỗi đúng hợp đồng
~~~

Lợi ích:

- Mỗi ranh giới có chức năng rõ: chuyển nghĩa, bổ sung ngữ cảnh hay xử lý kết quả.
- Không cần log khi không có hành động vận hành độc lập.
- Log final có thể chứa requestId, traceId, business ID, error code và cause.
- Một failure cuối cùng dễ được đếm trong metrics/alert.
- Unit test có thể kiểm tra loại exception và cause mà không cần assert log text ở mỗi tầng.

**Chọn owner log bằng quyền xử lý lỗi, không chỉ bằng tên lớp.** Ở HTTP server, global exception handler thường là nơi xử lý lỗi không được giải quyết. Ở một CLI nhỏ, `main` có thể là nơi in lỗi. Ở worker, tầng quản lý lifecycle task hoặc consumer xử lý message có thể là boundary. Trong thư viện dùng lại, thường nên ném exception cho ứng dụng chủ quyết định thay vì tự ý ghi ERROR mặc định.

## 6. Đặt log tại boundary nào?

| Vị trí | Thường nên làm gì | Có nên ghi ERROR? |
|---|---|---|
| DAO/repository | Giữ cause khi chuyển lỗi công nghệ thành lỗi lưu trữ | Không mặc định nếu caller còn xử lý |
| Application service | Thêm operation, business ID, error code; truyền tiếp | Không mặc định nếu không sở hữu outcome |
| REST controller/global handler | Map lỗi sang response, ghi failure event cuối | Thường hợp lý khi lỗi đủ nghiêm trọng |
| Message consumer | Quyết định ack/retry/dead-letter; log outcome | Hợp lý nếu nó thực sự sở hữu xử lý message |
| Scheduled job boundary | Quyết định job failed, retry hoặc stop | Hợp lý cho failure cuối cùng |
| Background CompletableFuture | Giữ exceptional completion; chủ nhận kết quả quan sát | Chỉ log độc lập nếu kết quả có thể không có consumer |
| Low-level shared library | Throw có ngữ nghĩa, tài liệu hóa cause | Tránh spam/error log ở mọi caller |
| Retry attempt handler | Có thể ghi attempt event, metrics, backoff | WARN/DEBUG/metrics khi chưa phải failure cuối |

**Không áp đặt một boundary duy nhất cho toàn hệ thống phân tán.** Service A có thể log một request đến A đã lỗi và service B cũng log yêu cầu tương ứng vào B, vì chúng là hai đơn vị triển khai/owner khác nhau. Cần `traceId`, `spanId` và correlation ID để nối quan hệ, nhưng không phải mọi log từ nhiều service đều là nhân bản sai. Vấn đề là cùng một tầng hoặc cùng một luồng điều khiển local ném đi qua nhiều lớp mà mỗi lớp phát bản lỗi không có ý nghĩa mới.

## 7. Bảo toàn cause và loại exception

Loại bỏ log-and-rethrow ở mọi tầng **không có nghĩa bỏ nguyên nhân lỗi**. Repository có thể dịch lỗi kỹ thuật sang exception phù hợp hơn với tầng storage:

~~~java
try {
    jdbcWrite(order);
} catch (java.sql.SQLException original) {
    throw new OrderStorageException("order persistence failed", original);
}
~~~

Đây là mảnh minh họa; `OrderStorageException` cần constructor `(String, Throwable)` và `jdbcWrite` phải được khai báo. Nếu `SQLException` đã đúng hợp đồng, để nó lan truyền hoặc `throw original;` cũng phù hợp. Không tạo nhiều wrapper `RuntimeException("failed", error)` qua các tầng khi chúng không thêm ngữ nghĩa.

Điểm chính của bài **log ownership** là mỗi tầng giữ cause và cung cấp ngữ cảnh cần thiết, nhưng không ghi cùng một stack trace ở repository, service và controller nếu chỉ xử lý một thất bại. Bài về cause giải thích riêng cách tạo, bảo toàn và duyệt chuỗi ngoại lệ.

## 8. Thêm ngữ cảnh đúng nơi, tránh message chung chung

Một bản log final nên giúp trả lời: **thao tác gì**, **request/task nào**, **business entity nào**, **dependency nào**, **lỗi gì**.

~~~json
{
  "level": "ERROR",
  "event": "order_persist_failed",
  "operation": "place_order",
  "requestId": "req-8",
  "orderId": "order-42",
  "errorCode": "ORDER_WRITE_FAILED",
  "dependency": "orders-db",
  "exceptionType": "OrderFailure",
  "causeType": "IllegalStateException"
}
~~~

Đây là **schema minh họa**, không phải log sinh bởi ví dụ Java. Trong production, structured logger có thể ghi các field này riêng và đính kèm `Throwable` để giữ stack trace/cause. Tránh chỉ nối text thành một message không thể truy vấn theo field.

Đối chiếu các ID:

- `requestId`: tìm tất cả sự kiện trong một request cụ thể.
- `traceId`: nối hành trình qua các dịch vụ khi có distributed tracing.
- `orderId`: truy ngược các thao tác trên một đơn hàng; không thay thế request ID.
- `jobRunId`/`messageId`: cần cho worker, queue, batch.
- `attempt`: phân biệt một lần retry với kết quả cuối.
- `errorCode`: nhóm lỗi ổn định, không phải ID ngẫu nhiên.

**Giữ ngữ cảnh xuyên tầng mà không phải ghi nhiều lần.** Có thể truyền một immutable context object hoặc gắn các trường typed vào exception miền nghiệp vụ. MDC của logging framework tiện dụng nhưng cần cleanup và context propagation qua thread pool; nó không tự truyền qua mọi `CompletableFuture` hoặc executor.

## 9. Khi nào log trung gian vẫn đúng?

Không có luật tuyệt đối “chỉ log một lần”. Một log trung gian có thể đúng nếu nó mô tả **sự kiện vận hành riêng**:

### 9.1. Retry attempt có ý nghĩa

~~~text
WARN  event=dependency_attempt_failed operation=save_order attempt=1 willRetry=true
WARN  event=dependency_attempt_failed operation=save_order attempt=2 willRetry=true
ERROR event=order_save_failed operation=save_order attempts=3 final=true
~~~

Nếu application có hai lần lỗi tạm thời rồi lần cuối thất bại, các attempt là sự kiện tách biệt giúp phân tích backoff/circuit breaker. Tuy nhiên không cần ba stack trace giống hệt: có thể log thông tin nhẹ ở mức WARN/DEBUG, metrics theo attempt, và ghi stack/cause đầy đủ cho final failure. Nếu retry cuối thành công, cần ghi **outcome thành công** và không đếm các attempt tạm thời là request failure cuối.

### 9.2. Compensation hoặc rollback thất bại

Ví dụ chuyển tiền gây lỗi và bước bù/rollback cũng lỗi. Đây là **hai thất bại khác nhau**, không phải hai lần ghi lại một exception. Log event riêng cho compensation failure có thể rất quan trọng vì nó thay đổi trạng thái nghiệp vụ.

### 9.3. Lỗi được xử lý cục bộ

Tầng thấp có thể bắt lỗi provider A, dùng fallback provider B và thành công. Nó không ném cùng lỗi lên trên; một sự kiện `fallback_used` hoặc metrics là hợp lý khi vận hành cần biết. Không tự động dùng ERROR nếu fallback là chiến lược dự kiến và người dùng không bị ảnh hưởng.

### 9.4. Ranh giới quyền sở hữu khác nhau

Một microservice gọi service khác có thể log lỗi outbound dependency; service nhận request cũng log failure inbound ở phạm vi riêng. Mỗi sự kiện nên có message/operation rõ ràng, trace context và tránh trùng `eventId` hoặc gọi một lỗi thành ba incident độc lập.

**Tiêu chí:** log trung gian có trả lời câu hỏi mới mà bản final không trả lời được hay không? Nếu chỉ ghi `"failed"` + cùng stack trace, thường không đáng ghi.

## 10. Đừng biến mọi exception thành ERROR

Mức log phải dựa vào **tính kỳ vọng và ảnh hưởng thực**, không chỉ vào `catch` có mặt:

| Tình huống | Mức hợp lý thường cân nhắc | Ghi chú |
|---|---|---|
| Input client không hợp lệ, trả response 400 | INFO/WARN hoặc chỉ metrics | Có thể là flow dự kiến, tránh flood |
| Record đã được xử lý (idempotency) | DEBUG/INFO | Không nhất thiết là lỗi |
| Retry tạm thời, vẫn còn cơ hội phục hồi | WARN/DEBUG hoặc metrics | `attempt` và `willRetry` có giá trị |
| Dependency failure cuối, request không thành công | ERROR | Log final một lần đủ context |
| User hủy thao tác có chủ đích | DEBUG/INFO | `CancellationException` không nhất thiết sự cố |
| Vi phạm invariant hoặc coding bug | ERROR | Bảo toàn stack trace/cause |
| Timeout sau khi task có thể đã commit | WARN/ERROR theo tác động | Không suy đoán đã rollback |

Nếu logging quá rộng khiến error alert đếm mọi invalid input hoặc cancellation, nhóm vận hành có thể mất niềm tin vào dashboard. Ngược lại, không giảm mức nghiêm trọng của lỗi mất dữ liệu chỉ vì nó được bắt rồi retry một lần.

## 11. Lỗi log rồi ném lại trong vòng lặp hoặc batch

Một worker xử lý 100.000 bản ghi có thể gặp dữ liệu sai thường xuyên. Nếu mỗi record lỗi bị log stack trace ở tầng parse, tầng validate và tầng batch, khối lượng log tăng phi tuyến theo chuỗi xử lý lỗi.

Không nên:

~~~java
// Mã minh họa: parse, validate, batch xử lý cùng một lỗi.
try {
    processRecord(record);
} catch (ValidationException ex) {
    log.error("record error", ex);
    throw ex;
}
~~~

Nếu record invalid là kết quả dự kiến, có thể dùng một `ValidationResult` hoặc tập hợp lỗi theo mã để phục vụ người dùng, không cần exception/stack trace cho mọi bản ghi. Nếu một record lỗi khiến toàn batch thất bại, boundary batch có thể log `batchId`, `recordId`, `errorCode` và số lượng phần tử đã xử lý.

**Hiệu năng không phải lý do duy nhất:** triage được cải thiện khi log phản ánh “một batch thất bại” và những record liên quan, thay vì hàng chục nghìn dòng `ERROR` thiếu correlation.

## 12. Log-and-rethrow trong CompletableFuture và ExecutorService

Với công việc bất đồng bộ, một nguồn trùng log phổ biến:

~~~java
// Trích đoạn, serviceAsync trả CompletableFuture và log có sẵn.
CompletableFuture<String> stage =
        serviceAsync().whenComplete((value, error) -> {
            if (error != null) {
                log.error("async failed", error);
            }
        });

// Sau đó boundary vẫn gọi stage.join() và log cùng lỗi lần nữa.
~~~

`whenComplete` chủ yếu phục vụ **quan sát**, không mặc định chuyển lỗi thành kết quả thành công. Nếu upstream hoàn tất bất thường, stage phía sau thường cũng hoàn tất bất thường; boundary gọi `join()` có thể bắt `CompletionException` và log lần hai. Vì vậy:

- Nếu boundary chắc chắn nhận và xử lý kết quả, ưu tiên để pipeline truyền failure rồi log ở boundary.
- Nếu tác vụ hoàn toàn *fire-and-forget*, không có caller tiêu thụ Future, cần một **điểm giám sát lỗi có trách nhiệm** trong worker hoặc handler kết quả. Đừng dùng khái niệm “log ở cuối” khi thực sự không có ai quan sát cuối.
- `ExecutorService.submit` có thể lưu ngoại lệ trong `Future`; nếu không ai gọi `get()` hoặc kiểm tra future, lỗi có thể không được báo cho ứng dụng. Cần thiết kế owner của future/monitoring.
- `Thread.UncaughtExceptionHandler` giúp ghi nhận lỗi chưa bắt của thread trần, nhưng không thay thế cơ chế lỗi của `FutureTask` hoặc pipeline.
- Khi callback `whenComplete` còn làm thêm việc có thể ném exception, stage mới có thể mang lỗi khác; hãy xem API semantics trước khi đặt logging side effect ở đây.

### Bóc wrapper đúng mức

~~~java
// Mã minh họa, một phương thức có CompletableFuture<String> future.
try {
    String value = future.join();
    consume(value);
} catch (java.util.concurrent.CancellationException ex) {
    handleCancellation(ex);
} catch (java.util.concurrent.CompletionException ex) {
    Throwable cause = ex.getCause();
    handleFailure(cause == null ? ex : cause);
}
~~~

`consume`, `handleCancellation` và `handleFailure` là các hàm ứng dụng cần định nghĩa; đoạn này không độc lập. Không log cả `CompletionException` và `cause` thành **hai sự kiện ERROR** chỉ vì có hai object exception liên quan. Truyền đầy đủ `Throwable` có cause cho logger và thêm context tại ranh giới.

Với `Future.get()`, lỗi computation thường đi qua `ExecutionException`; `InterruptedException` báo luồng **đang chờ** bị ngắt, không phải lỗi nghiệp vụ từ task. Đừng bắt rộng rồi log mỗi exception như cùng một nguyên nhân.

## 13. Không để thao tác logging che exception gốc

Trong hầu hết trường hợp, logger được thiết kế để hoạt động ổn định, nhưng các thao tác phụ như serialize object, tính toán argument, gọi `toString()`, enrich context hoặc gửi sang external sink **có thể lỗi hoặc bị chặn**. Ví dụ xấu:

~~~java
// Minh họa; formatter và auditSink là các phụ thuộc của ứng dụng.
catch (RuntimeException original) {
    String payload = formatter.format(hugeMutableRequest);
    auditSink.send(payload); // Nếu ném, original có thể bị che.
    throw original;
}
~~~

Ở đây nếu `formatter` hoặc `auditSink` thất bại, có thể không bao giờ chạy `throw original;`. Nếu việc gửi audit là trách nhiệm nghiệp vụ bắt buộc, cần hợp đồng rõ ràng về **hai lỗi độc lập** và chính sách transaction/outbox; không được giả định logging không thể ảnh hưởng luồng điều khiển.

Cách tiếp cận:

- Tránh đưa computation phức tạp hoặc access dữ liệu mutable lớn vào lập luận log.
- Dùng structured fields tối thiểu, dữ liệu đã được xác thực và có ranh giới nhạy cảm.
- Với logger chuẩn có API tham số, truyền Throwable cho backend để giữ cause/stack trace.
- Không nuốt `InterruptedException` khi chờ gửi log; nếu không thể truyền tiếp, cân nhắc khôi phục interrupt flag và kết thúc theo chính sách.
- Nếu phải bắt một lỗi phát sinh trong cleanup liên quan, bảo toàn exception gốc và thông tin secondary failure (`addSuppressed` khi phù hợp) thay vì âm thầm thay thế.
- Tránh `System.out.println` làm cơ chế quản trị lỗi production duy nhất vì thiếu level, cấu trúc và luồng thu thập đáng tin.

## 14. Logger nhiều cấp với thư viện phổ biến

SLF4J, Log4j hoặc `java.util.logging` là các lựa chọn có hợp đồng API riêng. Ví dụ SLF4J (cần dependency và logging backend, **không phải chương trình JDK độc lập**):

~~~java
// log: org.slf4j.Logger; context và ex đã có trong scope.
log.error(
        "event=order_write_failed operation={} requestId={} orderId={}",
        context.operation(), context.requestId(), context.orderId(), ex);
~~~

Khi dùng SLF4J, cần kiểm tra overload/cách xử lý `Throwable` ở phiên bản cụ thể để đảm bảo argument cuối là exception được log làm stack trace, không chỉ là giá trị format. Nếu backend hỗ trợ structured key-value pairs, ưu tiên xuất field riêng để query thay vì nối một message dài.

### MDC và async context

Các framework logging có thể cung cấp MDC để đưa request/correlation ID vào mỗi sự kiện trong thread hiện tại. Nhưng:

- `ThreadLocal`/MDC không tự truyền sang mọi executor hoặc stage `CompletableFuture`.
- Thread pool tái sử dụng thread; context không được dọn trong `finally` có thể làm request B xuất log mang ID request A.
- MDC là **cách làm giàu log**, không phải nơi duy nhất lưu thông tin nghiệp vụ cần thiết cho exception contract.
- Nếu cần log tại boundary async, truyền context bất biến vào task hoặc dùng context propagation do framework hỗ trợ.

Không sửa yêu cầu “log một lần ở boundary” bằng cách bắt/log ở từng tầng chỉ vì tầng đó là nơi cuối cùng còn thấy requestId. Hãy thiết kế đường truyền context trước.

## 15. Bảo mật và tính ổn định của ngữ cảnh log

Ghi log ít lần hơn không giúp gì nếu mỗi log vẫn chứa secret. Những thứ **không nên đưa nguyên văn** vào exception message hoặc logging fields nếu không có cơ sở và bảo vệ rõ:

- Mật khẩu, API key, access token, refresh token, cookie, authorization header.
- Thông tin thẻ thanh toán đầy đủ hoặc mã bí mật xác minh.
- Toàn bộ request/response body, dữ liệu cá nhân, thông tin sức khỏe nếu không cần thiết.
- Connection URL chứa credential, SQL với literal nhạy cảm, các cấu trúc `toString()` có thể lộ secret.
- IDs do bên ngoài gửi mà chưa được kiểm tra độ dài, ký tự điều khiển hoặc nguy cơ log injection.

Ưu tiên `requestId`, `traceId`, `operation`, `errorCode`, `dependency` và business ID được cho phép ghi. Validation các ID từ client: giới hạn độ dài, character set, trim/control characters, chống giả dòng log và phân biệt namespace. Log có thể bị copy sang hệ thống khác với quyền truy cập rộng hơn, nên áp dụng retention và access control thích hợp.

Một `orderId` có thể liên kết tới người dùng khi kết hợp dữ liệu khác; không đồng nhất “ID” với “không nhạy cảm”.

## 16. Stack trace, cause và suppressed exceptions: ghi thế nào đủ?

Java `Throwable` lưu các thông tin hữu ích:

- **Type:** lớp exception.
- **Message:** mô tả tình huống, không nhất thiết là mã lỗi ổn định.
- **Stack trace:** những frame tại điểm ném/tạo theo semantics của Throwable.
- **Cause:** nguyên nhân được bọc bằng `Throwable.getCause()`.
- **Suppressed exceptions:** thường xuất hiện khi xử lý tài nguyên trong `try-with-resources` hoặc khi ứng dụng dùng `addSuppressed`.

Khi một tầng chỉ log `ex.getMessage()` rồi throw, bản log của tầng đó có thể không chứa đủ cause/stack, trong khi tầng cuối có thể nhận chuỗi lỗi hoàn chỉnh. Mục tiêu là **một sự kiện chẩn đoán đầy đủ**, không phải mỗi tầng một phần thông tin rời rạc cần ghép bằng tay.

Trong `try-with-resources`, lỗi đóng tài nguyên có thể bị đưa vào `getSuppressed()` của exception chính. Nếu bạn chỉ log root cause bằng `rootCause(ex).getMessage()`, thông tin cleanup có thể bị mất. Trong production, truyền chính object `Throwable` tới logger để backend có cơ hội hiển thị cả cấu trúc liên quan.

**Đừng chuyển mọi throwable về exception gốc sâu nhất rồi vứt các lớp wrapper.** Wrapper có thể chứa ID, error code và ý nghĩa abstraction. Root cause là một góc nhìn, không thay thế toàn bộ exception chain.

## 17. Giám sát và thống kê: một log không phải một failure count

Nhiều dashboard đếm số ERROR logs như thể đó là số request lỗi. Khi log-and-rethrow nhiều tầng, cách đếm này thường sai.

Chọn số liệu có ngữ nghĩa:

- `requests_total{status="failed",operation=...}`: đếm **request** đã có outcome thất bại.
- `job_runs_total{outcome="failed"}`: đếm một lần chạy job thất bại.
- `retry_attempts_total{dependency=...,result=...}`: đếm nỗ lực retry, không lẫn với failure cuối.
- `exceptions_observed_total` nếu có: phản ánh sự kiện exception được ghi nhận, không tự gán nghĩa bằng số giao dịch thất bại.

Đây là **tên metric minh họa**, không phải một schema tiêu chuẩn bắt buộc. Với metrics, đừng gắn `requestId` hoặc `orderId` làm label: cardinality rất lớn. ID cụ thể phù hợp hơn trong logs và traces; metric theo operation/errorCode/dependency giúp tổng hợp.

Khi log ERROR bị giảm từ ba còn một, không có nghĩa reliability tự tốt lên ba lần. **Đã giảm trùng log**, không phải giảm số exception thật. Tách riêng đánh giá observability và quality của code xử lý lỗi.

## 18. Kiểm thử log ownership và ngoại lệ

Một bộ test tốt xác nhận cả hành vi chương trình lẫn chiến lược ghi nhận:

| Tình huống | Điều cần assert |
|---|---|
| Repository ném lỗi kỹ thuật | Service nhận cause; không in ERROR trùng ở repository |
| Service bọc lỗi domain | Lớp wrapper có đúng error code/ID; cause không mất |
| Boundary xử lý failure cuối | Một failure event có operation/requestId/businessId và Throwable |
| Retry rồi thành công | Outcome cuối không bị tính failed; attempt metrics riêng |
| Retry hết lần | Failure cuối log một lần; attempt metadata đủ để điều tra |
| Fallback xử lý được lỗi | Không có error cuối nếu hợp đồng coi recovery là thành công |
| CompletableFuture thất bại | Có owner nhận exceptional completion, không mất lỗi/nhân log |
| Cancellation/interrupt | Không vô tình ghi ERROR cho hủy hợp lệ; tín hiệu interrupt được xử lý |
| Lỗi trong cleanup | Exception gốc còn, lỗi phụ được liên kết hoặc thông báo hợp lý |
| Hai request trên một thread pool | MDC/context không bị rò chéo request |
| Exception chứa thông tin nhạy cảm | Log/response không làm lộ dữ liệu ngoài chính sách |
| Logging backend thất bại | Chính sách ứng dụng không âm thầm làm mất thông tin lỗi chính |

Các unit test của ứng dụng có thể dùng test appender hoặc in-memory logger để **đếm đúng log event theo mã/tên**, không chỉ so khớp text. Đồng thời `assertThrows` và kiểm tra `getCause()`/error code cho các lớp chuyển đổi exception. Tách test chiến lược logging khỏi test hành vi xử lý request để tránh phụ thuộc chi tiết format của backend.

**Kiểm tra lỗi trên cả luồng async và caller:** nếu một callback đã log rồi boundary `join()` cũng log, test cần thấy có hai sự kiện trùng và giúp loại bỏ một trong hai. Nếu không có người tiêu thụ future, test phải chứng minh sự kiện failure vẫn tới kênh giám sát.

## 19. Quy trình rà soát code có nhiều catch/log

Khi thấy cùng exception đi qua nhiều `catch`, hãy lần lượt đặt câu hỏi:

1. **Đây là một lỗi hay nhiều lỗi khác nhau?** Xem original exception/cause, requestId, attempt, event type.
2. **Tầng nào sở hữu outcome?** Ai quyết định retry, fallback, trả response, đánh dấu job failed?
3. **Catch này có làm gì ngoài log và throw?** Nếu không, cân nhắc bỏ catch hoặc chỉ giữ mapping abstraction.
4. **Nếu bỏ log tầng này, context nào bị mất?** Chuyển context sang exception/result/MDC/tracing trước khi xóa.
5. **Có giữ `cause` không?** Khi bọc phải dùng constructor nhận cause; không tạo RuntimeException mới làm mất nguồn gốc.
6. **Thông tin trong log có hữu ích không?** Operation, ID, error code, dependency, attempt, trạng thái rollback/commit.
7. **Có nơi nào log final đầy đủ chưa?** Nếu có, kiểm tra log tầng khác có phản ánh sự kiện độc lập thực sự không.
8. **Severity phản ánh tác động không?** Validation/cancellation/retry dự kiến không luôn là ERROR.
9. **Có duplicate stack trace từ framework tự log không?** Ví dụ request error handler và servlet/container logger, hoặc background task logger.
10. **Có dữ liệu nhạy cảm trong message/cause/context không?** Gỡ bỏ/che đúng chỗ.
11. **Có mã async không được quan sát không?** Kiểm tra `Future`/`CompletableFuture` và kênh giám sát nếu fire-and-forget.
12. **Test và metrics có còn đúng sau refactor không?** Giảm log duplicate nhưng không bỏ mất thông tin về failure cuối.

## 20. Kết luận

**Vừa ghi log vừa ném lại ở nhiều tầng** là sai lầm thường gặp vì nhân bản một lỗi thành nhiều sự kiện ERROR, làm khó chẩn đoán, tăng chi phí logging và có thể khiến dashboard đếm sai. Khi tầng hiện tại chỉ **chuyển lỗi**, hãy ném tiếp hoặc bọc có `cause` và thêm context đúng ngữ nghĩa. Khi một tầng **sở hữu kết quả cuối**, hãy ghi một failure event đủ operation, correlation/business IDs, error code và toàn bộ Throwable.

**Không nhầm “log tại boundary” với “cấm mọi log trung gian”.** Retry attempt, fallback, compensation, audit hoặc các ranh giới dịch vụ khác nhau có thể là sự kiện riêng cần ghi — miễn là mỗi event có mục đích, severity và danh tính rõ ràng.

Quy tắc tự kiểm tra: *Nếu xóa dòng log ở tầng này, thông tin điều tra có mất không? Nếu có, có thể chuyển thông tin ấy thành ngữ cảnh và để boundary ghi một lần hay không?* Câu trả lời dẫn đến hệ thống xử lý exception dễ bảo trì và hệ thống log dễ tin cậy hơn.

### Tài liệu tham khảo

- [Java SE 17 — Throwable: cause, stack trace, suppressed exceptions](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html)
- [Java SE 17 — RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html)
- [Java SE 17 — java.util.logging.Logger](https://docs.oracle.com/en/java/javase/17/docs/api/java.logging/java/util/logging/Logger.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [SLF4J Manual — parameterized logging](https://www.slf4j.org/manual.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-004">Ném ngoại lệ mới nhưng làm mất cause</a>

<details>
<summary>Xem chi tiết</summary>

# Ném ngoại lệ mới nhưng làm mất `cause` trong Java

Khi xử lý lỗi, lập trình viên thường muốn chuyển một ngoại lệ kỹ thuật thành ngoại lệ có ý nghĩa hơn với tầng gọi. Ví dụ, `IOException("configuration file missing")` có thể được chuyển thành `IllegalStateException("Cannot start application")`. Đây là kỹ thuật **exception translation / exception wrapping** hợp lệ. Sai lầm xuất hiện khi tạo ngoại lệ mới **chỉ từ thông điệp** mà không gắn ngoại lệ gốc làm `cause`:

```java
catch (IOException error) {
    throw new IllegalStateException("Startup failed: " + error.getMessage());
}
```

Đoạn trên là mã minh họa trong một phương thức có thể ném `IOException`. Nó tạo một `IllegalStateException` với stack trace riêng, nhưng `getCause()` trả `null`. Người điều tra có thể biết việc khởi động thất bại mà không còn thấy chuỗi nguyên nhân, loại `IOException` gốc, stack trace tại nơi lỗi phát sinh hoặc các suppressed exceptions gắn với lỗi đó.

Cách bọc thường phù hợp hơn là:

```java
catch (IOException error) {
    throw new IllegalStateException("Startup failed", error);
}
```

Constructor nhận `Throwable cause` giữ **tham chiếu tới ngoại lệ gốc**. Stack trace của wrapper mô tả nơi chuyển đổi lỗi, còn `Caused by:` cho phép lần lại nơi ngoại lệ bên dưới xuất hiện. Bài này giải thích hậu quả của việc làm mất `cause`, những trường hợp nên bọc hoặc truyền tiếp ngoại lệ, và cách giữ chuỗi lỗi qua API, logger, tài nguyên và công việc bất đồng bộ.

## 1. Phân biệt thông điệp, cause và stack trace

Một đối tượng `Throwable` thường có những thông tin quan trọng sau:

| Thông tin | API | Tác dụng |
| --- | --- | --- |
| Loại ngoại lệ | `error.getClass()` | Cho biết đây là `IOException`, `SQLException`, `IllegalArgumentException`... |
| Thông điệp | `error.getMessage()` | Chuỗi ngắn mô tả lỗi; có thể là `null` |
| Stack trace | `error.getStackTrace()` | Thông tin lời gọi nơi stack được ghi nhận |
| Nguyên nhân | `error.getCause()` | Ngoại lệ bên dưới được liên kết khi tạo hoặc khởi tạo nguyên nhân |
| Lỗi phụ | `error.getSuppressed()` | Các ngoại lệ bổ sung, ví dụ lỗi `close()` trong `try-with-resources` |

`getMessage()` là **một chuỗi**, không phải bản sao của cả `Throwable`. Tạo `new IllegalStateException(error.getMessage())` không bảo toàn `getCause()`, stack trace của `error` hay suppressed exceptions của nó.

Một chuỗi nguyên nhân đúng có thể trông như:

```text
IllegalStateException: Cannot start application
  Caused by: IOException: configuration file missing
      at ConfigRepository.read(...)
      at StartupService.initialize(...)
```

Đây là dạng minh họa, không phải stdout bảo đảm về số dòng hoặc tên phương thức của mọi ứng dụng. Wrapper mang ngữ cảnh nghiệp vụ, còn cause giữ dấu vết kỹ thuật.

Khi cause bị bỏ:

```text
IllegalStateException: Cannot start application: configuration file missing
  at StartupService.initialize(...)
```

Thông điệp vẫn nhắc tới việc thiếu file, nhưng loại `IOException`, vị trí khởi phát chính xác và các nguyên nhân sâu hơn có thể đã biến mất khỏi đối tượng ngoại lệ mới. Một logger ở tầng cao **không thể tự suy ra lại** dữ liệu đã không được chuyển tiếp.

## 2. Bảng so sánh các cách ném lại

Giả sử đang ở trong `catch (IOException error)`:

| Mã | Đối tượng ném ra | Giữ được cause gốc? | Ý nghĩa |
| --- | --- | --- | --- |
| `throw error;` | Chính `IOException` đã bắt | Không tạo wrapper; giữ nguyên đối tượng và chuỗi cause sẵn có | Truyền nguyên lỗi khi hợp đồng cho phép |
| `throw new IllegalStateException("Cannot load");` | Wrapper mới | **Không** | Thay loại lỗi và mất nguyên nhân |
| `throw new IllegalStateException(error.getMessage());` | Wrapper mới | **Không** | Sao chép message nhưng mất thông tin ngoại lệ gốc |
| `throw new IllegalStateException("Cannot load", error);` | Wrapper mới | **Có** | Thêm ngữ cảnh và giữ `IOException` |
| `throw new RuntimeException(error);` | Wrapper mới | **Có** | Giữ cause; thông điệp mặc định thường suy từ `error.toString()` |
| `throw new IllegalStateException("Cannot load", error.getCause());` | Wrapper mới | Không giữ **đối tượng `error`** | Có thể vô tình bỏ một tầng lỗi hữu ích |
| `throw new IllegalStateException(error.toString());` | Wrapper mới | **Không** | Chỉ sao chép biểu diễn chuỗi, không giữ exception |

Các đoạn trong bảng là câu lệnh minh họa, không phải các chương trình hoàn chỉnh. Việc dùng `throw error;` phụ thuộc vào chữ ký `throws` và cơ chế kiểm tra checked exceptions. Java không cho tự do ném `IOException` ra khỏi một phương thức không được phép khai báo hoặc truyền ngoại lệ này.

**Quy tắc:** nếu phải tạo wrapper để thay đổi tầng trừu tượng, hãy ưu tiên constructor nhận cả **thông điệp có ngữ cảnh** và **`Throwable cause`**.

## 3. Ví dụ Java 17+: thấy trực tiếp dữ liệu bị mất

Tạo tệp **`LostCauseDemo.java`**:

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class LostCauseDemo {
    static void readConfiguration() throws IOException {
        throw new IOException("configuration file missing");
    }

    static void startWrong() {
        try {
            readConfiguration();
        } catch (IOException original) {
            throw new IllegalStateException(
                    "startup failed: " + original.getMessage());
        }
    }

    static void startCorrect() {
        try {
            readConfiguration();
        } catch (IOException original) {
            throw new IllegalStateException("startup failed", original);
        }
    }

    static void inspect(String label, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException error) {
            StringWriter buffer = new StringWriter();
            error.printStackTrace(new PrintWriter(buffer));
            boolean hasOriginalTrace = buffer.toString().contains(
                    "Caused by: java.io.IOException: configuration file missing");
            String causeType = error.getCause() == null
                    ? "null"
                    : error.getCause().getClass().getSimpleName();

            System.out.println(label + ".type="
                    + error.getClass().getSimpleName());
            System.out.println(label + ".cause=" + causeType);
            System.out.println(label + ".originalTrace="
                    + hasOriginalTrace);
        }
    }

    public static void main(String[] args) {
        inspect("wrong", LostCauseDemo::startWrong);
        inspect("correct", LostCauseDemo::startCorrect);
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac --release 17 LostCauseDemo.java
java LostCauseDemo
```

Đầu ra:

```text
wrong.type=IllegalStateException
wrong.cause=null
wrong.originalTrace=false
correct.type=IllegalStateException
correct.cause=IOException
correct.originalTrace=true
```

**Giải thích từng bước:**

1. `readConfiguration()` tạo và ném `IOException("configuration file missing")`.
2. `startWrong()` bắt lỗi nhưng chỉ chuyển nội dung thông điệp thành chuỗi mới. `IllegalStateException` của nó không giữ đối tượng `IOException`, nên `getCause() == null`.
3. `startCorrect()` tạo `IllegalStateException` với tham số thứ hai là **ngoại lệ gốc**. Vì vậy `getCause()` là `IOException`.
4. `printStackTrace(...)` trên wrapper đúng trình bày thêm khối `Caused by: ...`, cho phép thấy lỗi bên dưới. Chương trình kiểm tra bằng một đoạn chuỗi ổn định thay vì in toàn bộ stack có số dòng khác nhau theo môi trường.

Ví dụ chỉ cho thấy **mất nguyên nhân do cách tạo wrapper**; nó không đo hiệu năng, không chứng minh rằng mọi logger đều tự in stack trace, và không yêu cầu ứng dụng dùng `IllegalStateException` cho tất cả loại lỗi. Trong dự án thật, chọn loại exception theo hợp đồng API.

## 4. Tại sao mất cause là lỗi nghiêm trọng khi chẩn đoán?

### 4.1. Mất vị trí phát sinh lỗi thật

Wrapper được khởi tạo trong `catch`, nên stack trace mới thường bắt đầu tại nơi `new IllegalStateException(...)`. Nếu mất `cause`, phần stack gắn với `IOException` ban đầu không còn được tham chiếu để ghi ra cùng sự cố.

Chỉ nhìn vào wrapper có thể khiến người vận hành sửa nhầm tầng service, trong khi lỗi thực tế nằm ở thao tác đọc file, xử lý SQL hoặc gọi HTTP.

### 4.2. Mất loại ngoại lệ để phân loại và phục hồi

`IOException`, `SQLException`, `TimeoutException` hoặc một exception do SDK cung cấp có thể yêu cầu cách xử lý khác nhau. Nếu chỉ còn message trong wrapper, logic ở tầng cao không thể kiểm tra `getCause()` để nhận đúng loại, ví dụ phân biệt lỗi tạm thời có thể thử lại với lỗi dữ liệu không hợp lệ không nên thử lại.

Tuy nhiên, **không nên chỉ dùng tên lớp exception để quyết định retry**. Cần kiểm tra nguyên nhân, trạng thái, tính idempotent và chính sách hệ thống. Cause là thông tin hỗ trợ phân loại, không phải quyết định nghiệp vụ tự động.

### 4.3. Mất các nguyên nhân bên dưới và lỗi phụ

Một `IOException` có thể có cause riêng, ví dụ lỗi hệ thống tệp. Nó cũng có thể mang `getSuppressed()` do `try-with-resources`. Khi giữ toàn bộ đối tượng `IOException` làm cause, các quan hệ đó vẫn có thể được truy theo; khi chỉ sao chép message, chúng bị bỏ khỏi chuỗi ngoại lệ của wrapper mới.

### 4.4. Làm log và hệ thống giám sát thiếu bằng chứng

Các logger như JUL, SLF4J/Logback thường có cơ chế nhận đối tượng `Throwable` và định dạng stack, cause, suppressed. Nhưng chúng chỉ có thể hiển thị dữ liệu mà exception hiện tại còn giữ. Nếu cause đã bị cắt từ một tầng trung gian, log ở controller hoặc ở bộ xử lý lỗi toàn cục không thể khôi phục nó.

### 4.5. Khó kiểm thử và duy trì hợp đồng lỗi

Test có thể kiểm tra cả loại wrapper **và cause gốc**. Nếu chỉ kiểm tra message, một refactor có thể vô tình làm mất chuỗi nguyên nhân mà test vẫn xanh. Hợp đồng “lỗi tầng dữ liệu được chuyển thành lỗi tầng nghiệp vụ nhưng vẫn giữ nguyên nhân” nên là một điều kiện có thể kiểm chứng.

## 5. Truyền lỗi nguyên vẹn hay bọc bằng lỗi mới?

Không phải lúc nào cũng nên tạo wrapper. Có hai chiến lược chính:

**Truyền tiếp:** khi phương thức có thể khai báo `throws IOException` và tầng gọi đã có trách nhiệm xử lý.

```java
void load() throws java.io.IOException {
    repository.read();
}
```

Đoạn minh họa cần `repository` được khai báo và có phương thức `read()` phù hợp. Ở đây không tạo exception mới. Nếu tầng này không có ngữ cảnh mới và không muốn đổi loại ngoại lệ, việc bắt rồi ném lại đôi khi chỉ làm mã dài hơn.

**Dịch ngoại lệ:** khi muốn mô tả thất bại bằng thuật ngữ của tầng trừu tượng hiện tại, hoặc vì hợp đồng phương thức không cho checked exception lan ra.

```java
void initialize() {
    try {
        repository.read();
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "Cannot initialize configuration", cause);
    }
}
```

Đây là đoạn minh họa cần `repository`. Wrapper cung cấp loại lỗi và thông điệp phù hợp với tầng khởi tạo, trong khi nguyên nhân `IOException` vẫn được giữ.

Hãy dùng wrapper khi **mang thêm ý nghĩa**, không phải cứ đi qua một tầng là tạo một `RuntimeException("failed", previous)`. Nhiều tầng bọc vô ích tăng chi phí tạo stack và khiến trace khó đọc.

Không nên dựa vào thông điệp lỗi kỹ thuật để xây thông điệp hiển thị cho người dùng cuối. Thông điệp HTTP/UI nên an toàn và ổn định, có thể khác thông điệp trong log hoặc trong exception.

## 6. Các constructor giữ nguyên nhân

Trong Java, các lớp exception thường cung cấp một số constructor, ví dụ `IllegalStateException`:

```java
new IllegalStateException("operation failed", cause);
new IllegalStateException(cause);
```

Đây là các biểu thức minh họa với biến `cause` kiểu `Throwable`. Constructor hai tham số thường rõ ràng nhất vì vừa có ngữ cảnh nghiệp vụ vừa giữ nguyên nhân.

Một lớp exception tùy biến nên cân nhắc constructor cho phép chuyển cause:

```java
public final class ConfigurationLoadException extends RuntimeException {
    public ConfigurationLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **khai báo lớp hoàn chỉnh ở mức đoạn mã**, nhưng khi đặt vào một tệp Java độc lập tên lớp public phải khớp tên tệp (`ConfigurationLoadException.java`). Có thể bỏ `public` trong ví dụ nhúng vào lớp khác. Để sử dụng:

```java
try {
    configRepository.read();
} catch (java.io.IOException cause) {
    throw new ConfigurationLoadException("Cannot load application config", cause);
}
```

Đoạn sử dụng yêu cầu đối tượng `configRepository` và lớp `ConfigurationLoadException` tương ứng.

Nếu dùng constructor chỉ có message, ví dụ `new ConfigurationLoadException(cause.getMessage())`, lớp phải có constructor đó thì mới biên dịch; quan trọng hơn, cách đó vẫn **không tự gắn cause**.

Một số thư viện có hệ thống exception riêng (mã lỗi, trạng thái HTTP, retryability). Hãy sử dụng constructor/builder có trường nguyên nhân đúng theo API, thay vì phỏng đoán rằng đặt một trường `message` là tương đương.

## 7. `initCause()` không phải giải pháp ưu tiên và không phải setter

`Throwable.initCause(Throwable)` có thể gắn nguyên nhân sau khi tạo một throwable mà cause **chưa được khởi tạo**. Điều này hữu ích cho những lớp cũ thiếu constructor nhận `Throwable`, nhưng có các giới hạn quan trọng:

- Chỉ được khởi tạo cause **một lần**. Gọi `initCause` lần nữa ném `IllegalStateException`.
- `initCause(this)` không hợp lệ: một throwable không được làm nguyên nhân trực tiếp của chính mình.
- Nếu constructor đã khởi tạo cause, kể cả về `null`, việc gọi `initCause(...)` sau đó có thể bị từ chối.
- Không nên gọi `initCause` trên exception đã chia sẻ giữa request hoặc nhiều luồng để cố thay nguyên nhân.

Ví dụ **đoạn mã độc lập ở mức phương thức**:

```java
Exception error = new Exception("load failed");
error.initCause(new java.io.IOException("file missing"));

System.out.println(error.getCause().getClass().getSimpleName());
```

Đầu ra:

```text
IOException
```

Ưu tiên constructor có `cause` ngay khi tạo nếu lớp hỗ trợ. Điều đó làm quan hệ lỗi rõ ràng và tránh một khoảng thời gian object tồn tại mà chưa có nguyên nhân.

## 8. Cause không đồng nghĩa suppressed exception

Có hai kiểu quan hệ ngoại lệ khác nhau:

- **Cause:** lỗi A dẫn đến việc tạo báo cáo lỗi B. Ví dụ `IOException` khi đọc dữ liệu là cause của `IllegalStateException("startup failed")`.
- **Suppressed:** một lỗi phụ xảy ra trong quá trình xử lý/đóng tài nguyên trong khi một lỗi chính đã xuất hiện. Ví dụ `close()` thất bại sau khi đọc file đã ném lỗi.

Đừng thay quan hệ nhân-quả bằng `addSuppressed(error)` chỉ để “lưu cho có”. `getCause()` và `getSuppressed()` mang nghĩa khác nhau; các hệ thống xử lý và người đọc log cũng diễn giải chúng khác nhau.

### Ví dụ Java 17+: cause giữ cả suppressed exceptions

Tạo tệp **`CauseAndSuppressedDemo.java`**:

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CauseAndSuppressedDemo {
    static final class BrokenResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void readData() throws IOException {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IOException("read failed");
        }
    }

    static void process() {
        try {
            readData();
        } catch (IOException original) {
            throw new IllegalStateException("import failed", original);
        }
    }

    public static void main(String[] args) {
        try {
            process();
        } catch (IllegalStateException wrapper) {
            Throwable cause = wrapper.getCause();

            System.out.println("wrapper=" + wrapper.getMessage());
            System.out.println("cause=" + cause.getMessage());
            System.out.println("suppressedCount="
                    + cause.getSuppressed().length);
            System.out.println("suppressedMessage="
                    + cause.getSuppressed()[0].getMessage());

            StringWriter buffer = new StringWriter();
            wrapper.printStackTrace(new PrintWriter(buffer));
            String trace = buffer.toString();

            System.out.println("causeInTrace="
                    + trace.contains("Caused by: java.io.IOException: read failed"));
            System.out.println("suppressedInTrace="
                    + trace.contains(
                            "Suppressed: java.io.IOException: close failed"));
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 CauseAndSuppressedDemo.java
java CauseAndSuppressedDemo
```

Đầu ra:

```text
wrapper=import failed
cause=read failed
suppressedCount=1
suppressedMessage=close failed
causeInTrace=true
suppressedInTrace=true
```

Vì `readData()` ném `IOException("read failed")` khi thân `try` đang chạy, còn `close()` ném `IOException("close failed")`, Java giữ lỗi đọc làm ngoại lệ chính và gắn lỗi đóng thành suppressed. `process()` tạo wrapper **nhưng giữ chính ngoại lệ đọc làm cause**, nên từ wrapper vẫn truy được cả hai thất bại.

Nếu thay dòng trong `process()` bằng `throw new IllegalStateException(original.getMessage());`, wrapper sẽ mất đường dẫn tới cả `IOException("read failed")` lẫn `getSuppressed()` của nó. Đây là lý do sai lầm “chỉ sao chép thông điệp” đặc biệt có hại khi lỗi gồm nhiều tầng.

## 9. Lần theo chuỗi cause mà không làm mất ngữ cảnh

Khi điều tra lỗi, bắt đầu từ exception ngoài cùng rồi đi theo `getCause()`:

```java
Throwable cursor = failure;
while (cursor != null) {
    System.out.println(cursor.getClass().getName()
            + ": " + cursor.getMessage());
    cursor = cursor.getCause();
}
```

Đây là đoạn minh họa bên trong một phương thức có `Throwable failure`. Đối với một chuỗi nguyên nhân hợp lệ thông thường, cách duyệt giúp xem từng tầng. Tuy nhiên, nếu viết tiện ích phục vụ dữ liệu lỗi từ nguồn bất kỳ, nên có biện pháp chống chuỗi bất thường (ví dụ theo dõi đối tượng đã gặp và giới hạn chiều sâu) thay vì giả định mọi liên kết cause luôn có độ sâu hợp lý.

**Không phải lúc nào cause sâu nhất cũng là thông tin duy nhất quan trọng.** Ngoại lệ tầng trên như `PaymentValidationException` có thể chứa quy tắc nghiệp vụ thất bại, trong khi cause sâu nhất chỉ là `SQLException`. Khi chọn phản hồi hoặc chính sách retry, giữ thông tin từng tầng; khi log, ưu tiên chuyển cả `Throwable` để backend hỗ trợ trình bày toàn bộ chuỗi.

Nếu mã dùng một helper “unwrap” bỏ `CompletionException` hoặc `ExecutionException`, nên phân biệt **bọc kỹ thuật** với **ngoại lệ có ngữ nghĩa nghiệp vụ**. Bỏ mọi wrapper vô điều kiện có thể làm mất thông tin về thao tác đã thất bại.

## 10. Ảnh hưởng trong `CompletableFuture` và `ExecutorService`

Trong tác vụ bất đồng bộ, lỗi có thể được bọc thêm bởi API quan sát kết quả:

- `Future.get()` và `CompletableFuture.get()` thường ném `ExecutionException` khi tác vụ thất bại; `getCause()` liên hệ ngoại lệ từ tác vụ.
- `CompletableFuture.join()` thường ném `CompletionException` khi future hoàn tất thất bại; cause chứa lỗi của pipeline.
- Trong `CompletableFuture.supplyAsync`, nếu supplier muốn chuyển `IOException` checked thành ngoại lệ unchecked để báo lỗi, có thể ném `new CompletionException("loading failed", original)`. Điều này **giữ nguyên nhân**.
- Nếu supplier lại ném `new RuntimeException(original.getMessage())`, nguyên nhân bị mất từ **ngay trong worker**. `join()`/`get()` có bọc thêm bao nhiêu lớp cũng không phục hồi lại `IOException` đã bị bỏ.

Mã minh họa trong lambda của một `CompletableFuture`:

```java
CompletableFuture<String> loaded = CompletableFuture.supplyAsync(() -> {
    try {
        return readText();
    } catch (java.io.IOException original) {
        throw new java.util.concurrent.CompletionException(
                "Cannot read text asynchronously", original);
    }
});
```

Đây là đoạn mã **không chạy độc lập**: yêu cầu import `CompletableFuture` và hàm `readText()` được khai báo với kiểu kết quả `String` và có thể ném `IOException`.

Khi bắt `CompletionException` ở ranh giới gọi `join()`:

```java
try {
    loaded.join();
} catch (java.util.concurrent.CompletionException error) {
    Throwable underlying = error.getCause();
    // Phân loại lỗi nếu cần, đồng thời giữ chuỗi throwable khi log.
    throw error;
}
```

Đoạn minh họa cần `loaded` được khai báo như trên. Tùy pipeline, `getCause()` có thể là một ngoại lệ nghiệp vụ hoặc một wrapper khác. Việc kiểm tra từng tầng là cách đáng tin hơn đoán chiều sâu cause.

Trường hợp hủy (`CancellationException`) hoặc ngắt luồng đang chờ (`InterruptedException`) có ngữ nghĩa riêng; không nên bọc mọi lỗi hủy/ngắt thành một `RuntimeException` chung chỉ để tránh checked exception. Nếu bắt `InterruptedException` nhưng không thể ném tiếp và chủ đích kết thúc việc đang làm, cần cân nhắc khôi phục trạng thái bằng `Thread.currentThread().interrupt()` rồi rời tác vụ theo hợp đồng.

## 11. Ghi log: truyền cả `Throwable`, không chỉ message

Ngay cả khi wrapper đã giữ cause đúng, bạn vẫn có thể làm mất thông tin ở **tầng logging** nếu chỉ ghi chuỗi.

Với SLF4J (đoạn mã minh họa, cần `logger`, `orderId`, `error`):

```java
logger.error("Order {} failed: {}", orderId, error.getMessage());
```

Cách trên không chuyển đối tượng exception đến logger. Thay bằng:

```java
logger.error("Order {} failed", orderId, error);
```

Trong cách dùng SLF4J phổ biến, đối số `Throwable` cuối được nhận diện như ngoại lệ của sự kiện, để backend có thể in stack trace, cause và suppressed. Nếu không cần biến nội dung, có thể dùng `logger.error("Order failed", error)`.

Với `java.util.logging` (JUL):

```java
logger.log(java.util.logging.Level.SEVERE, "Order failed", error);
```

Đây là lời gọi trên một `java.util.logging.Logger logger`; overload có `Throwable` giữ tham chiếu ngoại lệ trong `LogRecord`. Ngược lại, `logger.severe(error.getMessage())` chỉ ghi chuỗi và không đính kèm exception.

Đừng nhầm việc **giữ cause trong đối tượng** với việc **backend chắc chắn hiển thị toàn bộ cause**. Định dạng thực tế còn phụ thuộc formatter, mức log, cấu hình, sampling và điểm đến. Cần xác minh cấu hình môi trường vận hành.

Với một lỗi đi qua nhiều tầng, tránh vừa log nguyên stack ở repository, vừa log thêm ở service, rồi log lại ở controller nếu không có lý do. Thường nên log một lần tại ranh giới xử lý có đủ ngữ cảnh, còn tầng trung gian chỉ chuyển tiếp hoặc thêm cause đúng cách.

## 12. Khi bọc lỗi, chọn thông điệp thế nào?

Một thông điệp tốt của wrapper nên trả lời **thao tác nào không thực hiện được**, thay vì chỉ lặp lại thông điệp kỹ thuật:

```java
throw new IllegalStateException(
        "Cannot initialize tax rules for region " + safeRegionCode,
        original);
```

Đây là đoạn minh họa với biến `safeRegionCode` và `original`. Wrapper thêm ngữ cảnh “khởi tạo luật thuế cho vùng nào” trong khi cause giữ lỗi ở tầng lưu trữ/mạng. Tránh tự xây thông điệp bằng mật khẩu, token, dữ liệu định danh nhạy cảm hoặc toàn bộ yêu cầu người dùng.

Không nhất thiết nối `original.getMessage()` vào message wrapper. Nếu hệ thống logging trình bày cause chain, thông điệp của lỗi gốc đã có trong phần `Caused by:`. Việc lặp message có thể gây dài dòng, khó tìm kiếm và tăng nguy cơ rò rỉ dữ liệu.

**Thông điệp đối với người dùng cuối** nên được tạo riêng theo hợp đồng lỗi: chẳng hạn một mã lỗi không nhạy cảm và hướng dẫn thao tác. Không trả stack trace, cause kỹ thuật hoặc cấu trúc ngoại lệ nội bộ trực tiếp cho API công khai.

## 13. Những ngoại lệ và phản ví dụ đáng lưu ý

### 13.1. Constructor không hỗ trợ cause

Một số lớp exception cũ hoặc của thư viện có thể chỉ hỗ trợ message. Nếu cần giữ nguyên nhân, kiểm tra xem có constructor khác, builder hoặc `initCause` phù hợp hay không. Không tự ý dùng reflection để thay trường cause; nếu cần, tạo wrapper mới có constructor nhận `Throwable` theo đúng hợp đồng của ứng dụng.

### 13.2. Không dùng `initCause` trên exception đã hoàn tất hoặc chia sẻ

Một `Throwable` có thể đã có cause, hoặc được nhiều luồng cùng sử dụng. Việc thử gán nguyên nhân sau khi đã phát hành có thể thất bại hoặc trộn ngữ cảnh. Tạo wrapper mới của **sự cố hiện tại** thường dễ suy luận hơn.

### 13.3. Bắt mọi `Exception` rồi biến thành cùng một lỗi

```java
try {
    performOperation();
} catch (Exception error) {
    throw new IllegalStateException("operation failed", error);
}
```

Đoạn này giữ được cause nhưng **không tự đảm bảo thiết kế tốt**: bắt quá rộng có thể che phân loại lỗi, đổi hợp đồng ngoại lệ, hoặc nuốt tín hiệu ngắt cần được xử lý đặc biệt. Chỉ bắt ở ranh giới mà bạn thật sự có chính sách chuyển đổi; tránh bắt `Throwable` bao gồm cả `Error` một cách tùy tiện.

### 13.4. Biến checked exception thành unchecked không có nghĩa là nguyên nhân biến mất

Nếu một API không muốn công bố `IOException` trong chữ ký, tạo `UncheckedIOException` là một lựa chọn chuyên biệt trong nhiều tình huống I/O:

```java
catch (java.io.IOException original) {
    throw new java.io.UncheckedIOException("Cannot load resource", original);
}
```

Đây là đoạn minh họa đặt trong một `catch`; constructor nhận `IOException` giúp giữ loại nguyên nhân gốc. Chọn loại ngoại lệ nhất quán với tầng trừu tượng của thư viện.

### 13.5. Dùng `throw new Exception(error.toString())` không bảo toàn stack

`Throwable.toString()` thường chứa tên lớp và message, nhưng vẫn chỉ là chuỗi. Nó không thay cho tham chiếu tới object gốc và không cho phép `getCause()` của wrapper truy về lỗi ban đầu.

### 13.6. Nếu muốn thêm stack của tầng gọi, wrapper có ích nhưng không được thay cause

Khi tạo wrapper, Java có thể ghi stack của **vị trí chuyển đổi ngoại lệ**, rất hữu ích để biết request đi qua tầng service nào. Giữ `original` làm cause giúp người điều tra có **cả** vị trí chuyển đổi lẫn vị trí sinh lỗi. Đừng xóa cause để “stack gọn hơn”; có thể định dạng hoặc lọc log ở công cụ quan sát, không cắt quan hệ lỗi tại nguồn.

## 14. Checklist rà soát code và quy trình sửa lỗi

Khi đọc một nhánh `catch`, hãy kiểm tra tuần tự:

1. **Có thật cần bắt ở đây?** Nếu tầng này không xử lý, không thêm ngữ nghĩa và hợp đồng cho phép, cân nhắc truyền nguyên ngoại lệ.
2. **Có cần đổi loại lỗi?** Nếu có, chọn wrapper phản ánh đúng ranh giới nghiệp vụ/kỹ thuật.
3. **Constructor có nhận `Throwable cause`?** Tránh chỉ truyền `error.getMessage()` hoặc `error.toString()`.
4. **Có giữ đúng ngoại lệ đang bắt không?** Đừng vô tình chọn `error.getCause()` rồi bỏ tầng `error` mang ngữ cảnh hữu ích.
5. **Có suppressed exceptions hoặc cause sâu hơn không?** Wrapper đúng phải giữ đường truy cập tới chúng.
6. **Có phục hồi hoặc xử lý đặc biệt cần thiết không?** Ví dụ ngắt luồng, hủy tác vụ, lỗi cần retry hoặc lỗi không nên retry.
7. **Logger có nhận cả throwable không?** Nếu chỉ log chuỗi, dữ liệu vẫn bị mất ở ranh giới ghi log.
8. **Thông điệp bổ sung có an toàn?** Không nhúng bí mật hoặc dữ liệu người dùng không được phép ghi.
9. **Test có khẳng định cause không?** Kiểm tra loại lỗi ngoài cùng, loại cause và thông điệp/cấu trúc cần thiết.
10. **Có log lặp hoặc wrapper vô ích không?** Tránh tạo thêm chuỗi lỗi quá sâu mà không cung cấp thông tin mới.

Một mẫu sửa trong code review:

```diff
 try {
     repository.save(entity);
 } catch (IOException error) {
-    throw new IllegalStateException("Save failed: " + error.getMessage());
+    throw new IllegalStateException("Save failed", error);
 }
```

Đoạn diff là minh họa; nó không phải lệnh được thực thi. Sự khác biệt nhỏ ở constructor tạo ra khác biệt lớn trong khả năng chẩn đoán.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `throw new RuntimeException(error.getMessage());` giữ lại những gì?  
**Đáp án:** Một thông điệp mới. Nó không tự đặt `getCause()` thành `error`, không giữ stack trace của `error` và không nối các suppressed exceptions từ `error`.

**Câu 2.** `throw new IllegalStateException("Cannot load", error);` có bao nhiêu đối tượng ngoại lệ?  
**Đáp án:** Ít nhất có wrapper mới và đối tượng `error` hiện có. `wrapper.getCause()` tham chiếu đến `error`; chúng không phải một đối tượng.

**Câu 3.** Nếu phương thức được phép khai báo `throws IOException` và không cần thêm thông tin, có nhất thiết phải tạo wrapper không?  
**Đáp án:** Không. Có thể truyền tiếp lỗi theo hợp đồng hoặc để ngoại lệ tự lan lên.

**Câu 4.** `Throwable.initCause` có dùng như setter để thay cause mỗi lần ném không?  
**Đáp án:** Không. Cause chỉ được khởi tạo một lần theo hợp đồng API.

**Câu 5.** Vì sao chỉ log `exception.getMessage()` không đủ, ngay cả khi wrapper đã gắn cause?  
**Đáp án:** Logger chỉ nhận chuỗi, nên không có đối tượng throwable để trình bày stack trace, `Caused by:` và suppressed exceptions.

**Câu 6.** Một `IOException` có suppressed `close failed`. Khi tạo `new IllegalStateException("load failed", original)`, liệu còn truy được lỗi đóng tài nguyên không?  
**Đáp án:** Có, qua `wrapper.getCause().getSuppressed()`; ví dụ `CauseAndSuppressedDemo` chứng minh điều đó.

**Câu 7.** Có nên luôn lấy cause sâu nhất rồi bỏ mọi wrapper khi trả lỗi không?  
**Đáp án:** Không. Wrapper có thể chứa ngữ cảnh nghiệp vụ rất quan trọng; cần phân biệt lớp bọc kỹ thuật với tầng lỗi có ý nghĩa.

**Câu 8.** Khi đổi `IOException` sang `UncheckedIOException`, làm sao không mất cause?  
**Đáp án:** Dùng constructor có đối số nguyên nhân, ví dụ `new UncheckedIOException("Cannot read", original)`.

**Câu 9.** Bọc lỗi có cause vẫn có thể sai trong tình huống nào?  
**Đáp án:** Bắt quá rộng, chọn loại wrapper không phù hợp, che `InterruptedException`, log trùng lặp hoặc đưa dữ liệu nhạy cảm vào message đều là lỗi thiết kế cần kiểm soát.

## 16. Tài liệu tham khảo

- [Java 17 — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, `initCause()`, stack trace và suppressed exceptions.
- [Java 17 — `IllegalStateException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalStateException.html): các constructor hỗ trợ thông điệp và cause.
- [Java 17 — `UncheckedIOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UncheckedIOException.html): cách chuyển `IOException` thành ngoại lệ unchecked mà vẫn giữ nguyên nhân.
- [Java 17 — `CompletionException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html) và [Java 17 — `ExecutionException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html): lớp bọc khi quan sát lỗi bất đồng bộ.

**Ghi nhớ:** tạo ngoại lệ mới chỉ với `error.getMessage()` hoặc `error.toString()` **không bảo toàn nguyên nhân**. Nếu cần bọc lỗi, hãy giữ chính đối tượng `Throwable` đã bắt trong constructor nhận `cause`; nếu không cần bọc, truyền tiếp ngoại lệ đúng hợp đồng. Stack trace của wrapper và chuỗi cause bổ sung cho nhau để người đọc tìm được cả nơi lỗi được chuyển đổi lẫn nơi lỗi thật phát sinh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-005">Return trong finally hoặc lỗi cleanup che lỗi chính</a>

<details>
<summary>Xem chi tiết</summary>

# `return` trong `finally` hoặc lỗi cleanup che lỗi chính

Trong Java, `finally` thường được dùng để dọn dẹp tài nguyên. Nhưng một `return`, `throw` hoặc một lời gọi `close()` ném ngoại lệ từ bên trong `finally` có thể **thay đổi hoàn toàn kết quả mà khối `try` hoặc `catch` đang chuẩn bị trả về**. Đặc biệt nguy hiểm là tình huống thao tác nghiệp vụ đã ném một ngoại lệ có giá trị chẩn đoán, nhưng thao tác cleanup lại thất bại và **che khuất lỗi nghiệp vụ ban đầu**.

Bài thuộc **Java Core – Exception / Sai lầm thường gặp**, minh họa trên **Java 17+**. Sau khi học, bạn cần trả lời được: `finally` chạy vào thời điểm nào, tại sao `return` trong `finally` có thể nuốt lỗi, exception nào được truyền ra khi cả `try` và `finally` đều ném, cách `try-with-resources` bảo toàn lỗi qua `Suppressed`, và cách tổ chức cleanup mà không mất nguyên nhân.

## 1. Tóm tắt nguyên tắc quan trọng nhất

**Không đặt `return` vào `finally`. Không để lỗi cleanup vô tình thay thế lỗi chính mà không có chính sách rõ ràng.** Ưu tiên:

- `try-with-resources` cho tài nguyên triển khai `AutoCloseable`, vì Java có cơ chế giữ ngoại lệ phụ bằng `addSuppressed`.
- `finally` chỉ thực hiện dọn dẹp cần thiết, **không `return` hoặc cố tình thay đổi kết quả** của `try`/`catch`.
- Nếu bắt buộc cleanup thủ công có thể ném lỗi, hãy quyết định cách **giữ primary exception** và **báo cáo cleanup failure**, thay vì để lỗi nào xảy ra cuối cùng mặc nhiên thắng.
- Giữ `cause` khi bọc exception giữa các tầng; giữ `suppressed` khi có nhiều thất bại độc lập trong quá trình dọn dẹp.

Một bảng ngắn về ý nghĩa:

| Kết quả của `try`/`catch` | Điều xảy ra trong `finally` | Điều caller quan sát |
|---|---|---|
| Hoàn thành bình thường | `finally` hoàn thành bình thường | Kết quả bình thường |
| Đang chuẩn bị `return x` | `finally` hoàn thành bình thường | Trả về `x` |
| Đang chuẩn bị `return x` | `finally` thực hiện `return y` | Trả về **`y`**, thay `x` |
| Đang chuẩn bị ném lỗi A | `finally` hoàn thành bình thường | Lỗi **A** tiếp tục được ném |
| Đang chuẩn bị ném lỗi A | `finally` ném lỗi B | Lỗi **B** được truyền ra; A **không tự được giữ** dưới `Suppressed` trong `try-finally` thường |
| Đang chuẩn bị `return x` | `finally` ném lỗi B | Caller nhận lỗi **B**, không nhận `x` |

Quy tắc kỹ thuật đằng sau: nếu `finally` **hoàn thành bình thường**, lý do hoàn thành đang chờ từ `try` hoặc `catch` được tiếp tục. Nếu `finally` **hoàn thành đột ngột** (chẳng hạn `return` hoặc `throw`), lý do hoàn thành mới của `finally` **thay thế** lý do trước.

## 2. `finally` thực sự chạy khi nào?

Trong cấu trúc:

```java
try {
    performWork();
} catch (Exception e) {
    handle(e);
} finally {
    cleanup();
}
```

`finally` thông thường chạy sau khi phần `try`/`catch` kết thúc, kể cả khi có `return` hoặc exception đang được truyền đi. Đoạn trên **chỉ là ví dụ trong một phương thức**: `performWork()`, `handle(...)` và `cleanup()` là các hàm do ứng dụng định nghĩa.

Tuy nhiên, không được khẳng định `finally` **luôn luôn** được thực thi bất kể điều kiện nào. Các tình huống như JVM dừng/`Runtime.halt`, tiến trình bị hệ điều hành kết thúc, hoặc luồng không bao giờ thoát khỏi đoạn mã đang chạy có thể khiến cleanup không được thực hiện. `finally` bảo đảm theo **điều khiển thực thi Java bình thường**, không phải cơ chế phục hồi an toàn tuyệt đối trước mọi loại sự cố.

Điểm cần nhớ: `finally` không chỉ dành cho luồng **thành công**; nó còn chạy khi đang `throw` hoặc đang chuẩn bị `return`. Do đó mọi điều khiển luồng bên trong nó phải được viết rất cẩn thận.

## 3. Vì sao `return` trong `finally` ghi đè `return` của `try`?

Xem phương thức:

```java
static int calculate() {
    try {
        return 10;
    } finally {
        return 20;
    }
}
```

Java đã đánh giá `10` và đang chuẩn bị trả về. Trước khi phương thức kết thúc, khối `finally` phải chạy; lệnh `return 20` tạo ra một **kết quả trả về mới**. Giá trị cuối mà caller nhận là **20**, không phải 10.

Một trường hợp còn nguy hiểm hơn:

```java
static String hideFailure() {
    try {
        throw new IllegalStateException("primary failure");
    } finally {
        return "success";
    }
}
```

Ngoại lệ `IllegalStateException` bị **nuốt**: caller nhận chuỗi `"success"` thay vì lỗi. Không có `catch` nào phục hồi nghiệp vụ ở đây; chỉ có một lệnh `return` sai vị trí đã vô tình xóa tín hiệu thất bại.

### Ví dụ Java 17 độc lập: hai kiểu ghi đè

Tạo tệp `FinallyReturnDemo.java`:

```java
public class FinallyReturnDemo {
    static int overrideValue() {
        try {
            System.out.println("try: return 10");
            return 10;
        } finally {
            System.out.println("finally: return 20");
            return 20;
        }
    }

    static String swallowException() {
        try {
            throw new IllegalStateException("business failed");
        } finally {
            return "apparently successful";
        }
    }

    public static void main(String[] args) {
        System.out.println("result = " + overrideValue());
        System.out.println("result after error = " + swallowException());
    }
}
```

Biên dịch/chạy:

```bash
javac FinallyReturnDemo.java
java FinallyReturnDemo
```

Kết quả chuẩn:

```text
try: return 10
finally: return 20
result = 20
result after error = apparently successful
```

Ví dụ cố ý chứa **anti-pattern** để chứng minh hành vi ngôn ngữ. Trong production, không dùng hai mẫu này. Khi bật cảnh báo của trình biên dịch bằng `javac -Xlint:finally FinallyReturnDemo.java`, công cụ có thể cảnh báo rằng `finally` không hoàn thành bình thường; nên coi cảnh báo này là dấu hiệu code cần sửa.

Tác hại thực tế: một request có thể ghi trạng thái “thành công” dù logic tạo đơn hàng đã thất bại; một tác vụ batch có thể trả kết quả mặc định dù đã mất dữ liệu; hoặc exception không được ghi nhận bởi hệ thống giám sát vì caller không thấy nó.

## 4. `return` trong `try`, cleanup trong `finally` đúng cách

Không phải `return` trong `try` là sai. Mẫu sau hoàn toàn hợp lý nếu cleanup không thay đổi kết quả:

```java
static int readValue() {
    try {
        return 10;
    } finally {
        System.out.println("cleanup");
    }
}
```

Caller nhận `10`, và `"cleanup"` được in trước khi phương thức trả về. `finally` ở đây **hoàn thành bình thường**, nên không ghi đè `return`.

Chương trình độc lập `SafeFinallyReturnDemo.java`:

```java
public class SafeFinallyReturnDemo {
    static int read() {
        try {
            System.out.println("work");
            return 7;
        } finally {
            System.out.println("cleanup");
        }
    }

    public static void main(String[] args) {
        int result = read();
        System.out.println("result = " + result);
    }
}
```

Chạy:

```bash
javac SafeFinallyReturnDemo.java
java SafeFinallyReturnDemo
```

Output:

```text
work
cleanup
result = 7
```

**Phân biệt quan trọng:** biểu thức sau `return` trong `try` được đánh giá **trước** khi chạy `finally`. Nếu trả tham chiếu tới một đối tượng mutable và `finally` **thay đổi chính đối tượng đó**, caller có thể thấy trạng thái đã thay đổi; đó là hiệu ứng mutation, **không phải lệnh `return` trong `finally` ghi đè giá trị**.

Ví dụ minh họa:

```java
static java.util.List<String> createList() {
    java.util.List<String> list = new java.util.ArrayList<>();
    try {
        list.add("work");
        return list;
    } finally {
        list.add("cleanup");
    }
}
```

Khi caller nhận danh sách, nó chứa `"work"` và `"cleanup"`; cùng một object được trả về. Mẫu này vẫn có thể gây bất ngờ nếu cleanup chỉnh sửa đối tượng trả về. Tốt nhất tách rõ trạng thái kết quả khỏi logic dọn dẹp.

## 5. Exception từ `finally` có thể che exception chính

Giả sử operation gặp lỗi trước:

```java
try {
    throw new IllegalArgumentException("invalid input");
} finally {
    throw new IllegalStateException("close failed");
}
```

Java sẽ truyền **`IllegalStateException("close failed")`** ra caller. `IllegalArgumentException("invalid input")` bị thay thế theo quy tắc điều khiển luồng. Với `try-finally` thủ công ở trên, Java **không tự động** thêm lỗi đầu tiên vào `getSuppressed()` của lỗi thứ hai.

Đó là lý do một log có thể chỉ hiện:

```text
java.lang.IllegalStateException: close failed
```

trong khi thật ra validation đã thất bại trước. Người điều tra có thể tốn thời gian sửa nhánh cleanup mà không biết thông tin lỗi ban đầu đã mất.

### Ví dụ Java 17 độc lập: lỗi thứ hai che lỗi đầu

Tệp `CleanupMasksPrimaryDemo.java`:

```java
public class CleanupMasksPrimaryDemo {
    static void doWork() {
        try {
            throw new IllegalArgumentException("primary: bad request");
        } finally {
            throw new IllegalStateException("cleanup: close failed");
        }
    }

    public static void main(String[] args) {
        try {
            doWork();
        } catch (RuntimeException e) {
            System.out.println("observed = "
                    + e.getClass().getSimpleName());
            System.out.println("message = " + e.getMessage());
            System.out.println("cause = " + e.getCause());
            System.out.println("suppressed count = "
                    + e.getSuppressed().length);
        }
    }
}
```

Chạy:

```bash
javac CleanupMasksPrimaryDemo.java
java CleanupMasksPrimaryDemo
```

Kết quả:

```text
observed = IllegalStateException
message = cleanup: close failed
cause = null
suppressed count = 0
```

Vì mã này **không hề nối hai exception** bằng constructor cause hay `addSuppressed`, caller không thể lấy lại `IllegalArgumentException` qua exception đã nhận. Đây không phải do logging framework cắt stack; đây là sự mất thông tin ở **logic xử lý exception**.

Sai lầm này không chỉ xảy ra khi viết `throw` trực tiếp trong `finally`. Một phương thức `close()`, `rollback()`, `flush()` hoặc `release()` được gọi tại đó có thể ném checked/unchecked exception và **ghi đè lỗi đang được truyền**, nếu không có cơ chế bảo toàn phù hợp.

## 6. Quy tắc quyết định exception nào thắng

Khái niệm “completion reason” giúp đọc những đường đi phức tạp:

1. `try` có thể hoàn thành bình thường, `return`, `break`, `continue` hoặc `throw`.
2. `catch` nếu khớp có thể thay đổi cách xử lý lỗi.
3. `finally` chạy trước khi phương thức rời cấu trúc.
4. Nếu `finally` hoàn thành bình thường, lý do hoàn thành từ `try`/`catch` được giữ.
5. Nếu `finally` hoàn thành đột ngột, lý do mới **chiếm ưu thế** so với lý do đang chờ.

Ví dụ nguy hiểm:

```java
static boolean work() {
    try {
        throw new IllegalStateException("failure");
    } catch (IllegalStateException e) {
        throw e;
    } finally {
        return true;
    }
}
```

Đây là **phương thức minh họa**, không nên dùng. Kể cả `catch` đã `throw e`, `finally` vẫn trả `true` và **hủy ngoại lệ đang được ném lại**.

Tương tự:

```java
static int read() {
    try {
        return 42;
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
```

Caller nhận exception, **không nhận số 42**.

**Kết luận:** đừng tin rằng `return`/`throw` ở đoạn trước luôn là kết quả cuối. Phải kiểm tra xem `finally` có thể thực hiện điều khiển luồng cạnh tranh hoặc ném lỗi không.

## 7. Tại sao `try-with-resources` thường là giải pháp tốt nhất?

Với tài nguyên `AutoCloseable`, Java có cú pháp:

```java
try (Resource resource = open()) {
    doWork(resource);
}
```

Đây là mảnh minh họa cần kiểu `Resource` và hai phương thức tương ứng. Java đóng resource khi ra khỏi phạm vi. Nếu thân `try` ném lỗi A và `close()` ném B, **A vẫn là primary**, B nằm trong `A.getSuppressed()`. Nếu chỉ đóng lỗi, B trở thành primary. Nhiều tài nguyên được đóng **ngược thứ tự khai báo**.

Điểm cần phân biệt với `finally` thủ công: một `throw` hoặc `return` trong `finally` có thể **thay** lỗi/kết quả đang chờ. Bài về *đóng tài nguyên thủ công thiếu nhánh thất bại* đi sâu vào open lỗi giữa chừng, close nhiều nguồn, ownership và kiểm thử mọi đường dọn dẹp.

## 8. Ví dụ Java 17 độc lập: primary và suppressed khi đóng hai tài nguyên

Tạo tệp `SuppressedOnCloseDemo.java`:

```java
import java.io.IOException;

public class SuppressedOnCloseDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close " + name);
        }
    }

    static void work() throws Exception {
        try (Resource first = new Resource("first");
             Resource second = new Resource("second")) {
            throw new IllegalArgumentException("body failed");
        }
    }

    public static void main(String[] args) {
        try {
            work();
        } catch (Exception e) {
            System.out.println("primary = "
                    + e.getClass().getSimpleName()
                    + ": " + e.getMessage());

            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("suppressed = "
                        + suppressed.getClass().getSimpleName()
                        + ": " + suppressed.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac SuppressedOnCloseDemo.java
java SuppressedOnCloseDemo
```

Output:

```text
primary = IllegalArgumentException: body failed
suppressed = IOException: close second
suppressed = IOException: close first
```

Đọc hành vi:

1. Thân `try` ném `IllegalArgumentException("body failed")`; đây là **primary exception**.
2. Java đóng `second` trước `first`.
3. Hai lần `close()` ném `IOException`.
4. Thay vì làm mất lỗi thân `try`, Java gắn cả hai lỗi `close()` vào **mảng suppressed** của primary.
5. Caller nhận lại primary và có thể xem các lỗi cleanup qua `e.getSuppressed()`.

Trong log do `printStackTrace()` tạo, bạn sẽ thấy nhánh thụt vào `Suppressed:` bên dưới exception chính. Điều đó không có nghĩa close error không quan trọng; nó chỉ thể hiện **vai trò phụ trong luồng thất bại cụ thể**.

### Khi thân `try` thành công nhưng `close()` thất bại

Tệp `CloseAsPrimaryDemo.java`:

```java
import java.io.IOException;

public class CloseAsPrimaryDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close " + name);
        }
    }

    static void work() throws Exception {
        try (Resource first = new Resource("first");
             Resource second = new Resource("second")) {
            System.out.println("body: success");
        }
    }

    public static void main(String[] args) {
        try {
            work();
        } catch (Exception e) {
            System.out.println("primary = " + e.getMessage());
            for (Throwable extra : e.getSuppressed()) {
                System.out.println("suppressed = " + extra.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac CloseAsPrimaryDemo.java
java CloseAsPrimaryDemo
```

Output:

```text
body: success
primary = close second
suppressed = close first
```

Vì không có lỗi ở thân `try`, lần đóng đầu tiên theo **thứ tự đóng ngược** (`second`) tạo primary exception. Lỗi đóng `first` trở thành suppressed của primary đó.

Không được diễn giải “primary” là “lỗi nghiệp vụ” trong mọi trường hợp. **Primary là exception được ưu tiên truyền ra theo quy tắc cấu trúc đang thực thi**. Nếu thân `try` hoàn thành, một lỗi đóng tài nguyên có thể là chính lỗi cần xử lý.

## 9. `Caused by` và `Suppressed` khác nhau như thế nào?

Dễ nhầm hai thành phần này khi đọc stack trace:

| Cơ chế | API | Ý nghĩa |
|---|---|---|
| **Cause** | `getCause()`, constructor nhận `Throwable cause` | Ngoại lệ trước đó làm phát sinh ngoại lệ đang xét, thường trong một chuỗi bọc ngữ cảnh |
| **Suppressed** | `getSuppressed()`, `addSuppressed(...)` | Lỗi bổ sung được giữ lại khi đang xử lý lỗi khác, ví dụ đóng tài nguyên sau khi thân try đã lỗi |

Một exception có thể có **một cause** và **nhiều suppressed**. Khi `try-with-resources` thấy body failure A và close failure B, B thường được đưa vào **suppressed của A**, không phải **cause của A**; cleanup B không gây ra việc body A lỗi trước đó.

Ví dụ stack trace (đường dẫn/số dòng là **minh họa**, không phải output cam kết của chương trình ở trên):

```text
java.lang.IllegalStateException: Không thể ghi báo cáo
    at app.ReportService.generate(ReportService.java:50)
    Suppressed: java.io.IOException: Không đóng được stream
        at app.ReportWriter.close(ReportWriter.java:18)
        ... 1 more
Caused by: java.io.IOException: Không ghi được dữ liệu
    at app.ReportWriter.write(ReportWriter.java:12)
    ... 1 more
```

Cách đọc đúng:

- Ngoại lệ ngoài cùng mô tả thao tác thất bại.
- `Caused by` nói về nguyên nhân đã được bọc lại.
- `Suppressed` ghi lỗi xảy ra bổ sung trong khi dọn dẹp.
- `... 1 more` là số stack frame trùng ở cuối được rút gọn so với nhánh bao ngoài; **không phải** một exception nữa.

Nếu `finally` thường ném exception mới và làm mất lỗi đầu, stack trace của caller có thể **không còn thông tin về lỗi đã bị thay thế**. Lúc ấy không thể mong `Caused by` hoặc `Suppressed` tự xuất hiện nếu mã chưa hề thiết lập chúng.

## 10. Cleanup thủ công: phải bảo toàn lỗi chính bằng chính sách tường minh

Không phải mọi tài nguyên đều triển khai `AutoCloseable`, và đôi khi cleanup có yêu cầu đặc biệt. Nếu phải dùng `finally` thủ công, đừng để lỗi cleanup tự động ghi đè lỗi trước.

Mẫu ý tưởng:

```text
Thực thi công việc
    |
    +-- thành công --------------------+
    |                                 |
    +-- thất bại: lưu primary         |
                                      v
                                  Chạy cleanup
                                      |
                         +------------+------------+
                         |                         |
                    cleanup OK                cleanup lỗi
                         |                         |
                 truyền kết quả/lỗi cũ    nếu đã có primary:
                                          gắn cleanup thành suppressed
                                         nếu chưa có primary:
                                          truyền cleanup failure
```

Ví dụ Java 17 độc lập áp dụng cho một loại checked exception cụ thể. Tệp `ManualCleanupPreserveDemo.java`:

```java
import java.io.IOException;

public class ManualCleanupPreserveDemo {
    static void work() throws IOException {
        throw new IOException("write failed");
    }

    static void cleanup() throws IOException {
        throw new IOException("close failed");
    }

    static void run() throws IOException {
        IOException primary = null;

        try {
            work();
        } catch (IOException e) {
            primary = e;
            throw e;
        } finally {
            try {
                cleanup();
            } catch (IOException closeError) {
                if (primary != null) {
                    primary.addSuppressed(closeError);
                } else {
                    throw closeError;
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (IOException e) {
            System.out.println("primary = " + e.getMessage());

            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println(
                        "suppressed = " + suppressed.getMessage());
            }
        }
    }
}
```

Lệnh:

```bash
javac ManualCleanupPreserveDemo.java
java ManualCleanupPreserveDemo
```

Kết quả:

```text
primary = write failed
suppressed = close failed
```

Mã giữ nguyên `IOException("write failed")` để truyền ra caller, đồng thời gắn lỗi đóng tài nguyên vào `getSuppressed()`. Nếu `work()` thành công mà `cleanup()` thất bại, nhánh `primary == null` sẽ ném lỗi cleanup như lỗi chính.

**Giới hạn quan trọng:** ví dụ này chỉ bắt **`IOException`** theo hợp đồng của `work()` và `cleanup()`. Nếu một `RuntimeException` hoặc `Error` khác xuất hiện trong `work()`, rồi `cleanup()` ném `IOException`, đoạn `finally` có thể che exception không thuộc loại đã lưu. Muốn xử lý toàn diện mọi `Throwable` thủ công sẽ phức tạp, cần xem xét `Error`, suppression bị vô hiệu hóa, self-suppression và chính sách hủy. Vì vậy **ưu tiên `try-with-resources`** khi có thể, thay vì sao chép một pattern hẹp cho mọi nơi.

Nếu cleanup có nhiều bước, cân nhắc mỗi bước ném exception thế nào và tránh để bước đầu lỗi làm bỏ qua toàn bộ các bước khác khi việc dọn dẹp tiếp tục là bắt buộc.

## 11. Gọi `close()` trong `finally` có thể làm mất thông tin như thế nào?

Mẫu cổ điển:

```java
Resource resource = acquire();

try {
    use(resource);
} finally {
    resource.close();
}
```

Đây là **mảnh mã minh họa**, giả định `Resource`, `acquire`, `use` và `close` đã tồn tại. Nếu `use` ném lỗi A và `close` ném lỗi B, **B có thể thay thế A**, vì `finally` hoàn thành bằng `throw B`. Cấu trúc thường **không tự tạo suppressed exceptions**.

Còn nếu `resource` là `AutoCloseable`, hãy dùng:

```java
try (Resource resource = acquire()) {
    use(resource);
}
```

Compiler tạo logic dọn tài nguyên và suppression theo hợp đồng của `try-with-resources`. Điều này vừa ngắn hơn, vừa giảm nguy cơ rơi mất primary error.

Một số vấn đề khác với cleanup thủ công:

- `resource` có thể chưa khởi tạo xong, dẫn tới `NullPointerException` trong `finally` nếu không kiểm tra.
- Nếu tài nguyên được mở **ngoài** `try`, lỗi trước khi vào `try` có thể khiến `finally` không chạy.
- Có thể có nhiều tài nguyên cần đóng **theo thứ tự ngược** với thứ tự mở.
- Thao tác close không nhất thiết idempotent; đóng hai lần có thể gây tác dụng phụ hoặc lỗi khác.
- Cleanup cần tránh gọi dịch vụ ngoài quá lâu, vì khi gặp lỗi chính hệ thống có thể đã quá tải.

`try-with-resources` xử lý tốt các vấn đề quản lý tài nguyên theo hợp đồng Java, nhưng **không tự bảo đảm** một `close()` luôn thành công hoặc mọi tác động nghiệp vụ đã được rollback.

## 12. Phân biệt resource cleanup, transaction rollback và khôi phục nghiệp vụ

Có những thao tác được gọi trong `finally` không chỉ là `close()`:

```java
try {
    updateDatabase();
} finally {
    rollbackIfNeeded();
}
```

Đây là mảnh minh họa. Nếu `updateDatabase()` ném một lỗi và `rollbackIfNeeded()` cũng ném, nguyên nhân đầu **có thể bị che**. Nhưng bỏ qua hoàn toàn lỗi rollback cũng có thể nguy hiểm: transaction hoặc kết nối có thể ở trạng thái không xác định.

Cách xử lý đúng tùy hợp đồng của transaction manager và thư viện:

- Giữ nguyên nhân tạo ra failure đầu tiên khi đó là thông tin quan trọng.
- Gắn hoặc ghi nhận lỗi cleanup/rollback bổ sung, phân biệt rõ hậu quả của nó.
- Nếu rollback failure có mức độ nghiêm trọng hơn, có thể cần **một policy lỗi tổng hợp**. Quyết định đó phải được tường minh, không phải tác dụng phụ vô tình của `finally`.
- Không tiếp tục như thành công khi giao dịch hoặc dọn dẹp có thể thất bại.
- Không tự viết logic transaction thủ công nếu framework/transaction manager đã cung cấp ranh giới quản lý và quy tắc lỗi phù hợp.

Một số thao tác cần **phát hiện trước** chúng có thể ném checked exception, ví dụ `IOException` hoặc `SQLException`. Đưa các phương thức này vào `finally` không xóa nghĩa vụ khai báo/bắt ngoại lệ. Nếu bất cẩn bắt quá rộng rồi ghi log chung mà không xử lý, lỗi vẫn có nguy cơ mất dấu.

## 13. Ngoại lệ trong `catch` rồi bị `finally` che

Không phải chỉ exception xuất phát từ `try` mới bị che. `catch` có thể chuyển đổi hoặc ném lại ngoại lệ, rồi `finally` ném lỗi khác:

```java
try {
    perform();
} catch (Exception e) {
    throw new IllegalStateException("operation failed", e);
} finally {
    riskyCleanup();
}
```

Đây là mảnh minh họa. Nếu `riskyCleanup()` ném exception B, caller có thể chỉ thấy B dù `catch` đã cẩn thận giữ cause từ `perform()`.

Điều đó dẫn đến một quy tắc mạnh: **bọc exception đúng cách vẫn chưa đủ nếu đường cleanup cuối cùng có thể ghi đè ngoại lệ đã bọc**. Hãy kiểm tra cả `finally`, hook đóng tài nguyên, callback kết thúc request, interceptor và các bước rollback ở tầng ngoài.

Ngược lại, nếu cleanup không ném, wrapper được tạo trong `catch` sẽ giữ cause như thiết kế.

## 14. Logging: tại sao “log rồi ném” không thay thế bảo toàn exception?

Có người cố tránh mất lỗi bằng cách:

```java
try {
    perform();
} catch (Exception e) {
    logger.error("business failed", e);
    throw e;
} finally {
    cleanup();
}
```

Đây là mảnh tích hợp có `logger` được định nghĩa ở môi trường thực. Nếu `cleanup()` lại ném lỗi, caller vẫn có thể chỉ nhận cleanup error. Log ở `catch` có thể giữ một phần thông tin nhưng:

1. Không bảo toàn **đối tượng Throwable và cause/suppressed** trong luồng exception mà caller nhận.
2. Có thể tạo bản log lỗi trùng nếu tầng ngoài cũng log cleanup error.
3. Nếu hệ thống log không phát được sự kiện, thông tin ban đầu có thể vẫn mất.
4. Không thay thế việc ra quyết định đúng về **primary** và **secondary failures**.

Chiến lược tốt hơn là **giữ exception chain**, rồi để ranh giới chịu trách nhiệm báo một sự kiện lỗi đầy đủ (tùy policy). `Throwable.printStackTrace()` và logger có overload nhận đối tượng exception sẽ hiển thị cause/suppressed nếu được cấu hình đúng.

Ví dụ log tích hợp kiểu SLF4J:

```java
logger.error("Không hoàn thành operation={}, requestId={}",
        operation, requestId, failure);
```

Đây chỉ là mảnh minh họa, cần logger tương thích và biến đã có. Truyền **Throwable** thay vì chỉ `failure.getMessage()` để không mất stack và suppressed. Tránh phát tán dữ liệu nhạy cảm trong message và stack cho người dùng cuối.

## 15. Thử nghiệm: không chỉ kiểm tra message của exception ngoài cùng

Khi viết test cho xử lý cleanup, hãy xác nhận cả **nguyên nhân chính** và **ngoại lệ phụ**, thay vì chỉ kiểm tra `assertThrows(...)` có ném ra bất cứ lỗi nào.

Ví dụ JUnit 5 (đoạn tích hợp, không độc lập vì yêu cầu thư viện kiểm thử):

```java
IOException failure = assertThrows(IOException.class,
        () -> runOperation());

assertEquals("write failed", failure.getMessage());
assertEquals(1, failure.getSuppressed().length);
assertEquals("close failed",
        failure.getSuppressed()[0].getMessage());
```

`runOperation()` là hàm do ứng dụng định nghĩa. Tương tự, với các lỗi được bọc, nên kiểm tra `getCause()` cùng loại/message phù hợp. Các phép kiểm tra này giúp phát hiện regression khi ai đó thay `try-with-resources` bằng `try-finally` thủ công hoặc thêm một `return` vào cleanup.

Một bộ test thực tế nên có các tình huống:

| Công việc chính | Cleanup | Kỳ vọng |
|---|---|---|
| Thành công | Thành công | Trả kết quả thành công |
| Thất bại A | Thành công | Propagate A |
| Thành công | Thất bại B | Báo B |
| Thất bại A | Thất bại B | Với TWR: A primary, B suppressed |
| Thất bại A | Hai lần cleanup lỗi B, C | Với TWR: A primary, B/C suppressed theo thứ tự đóng |
| Có `return` từ `try` | Cleanup hoàn thành bình thường | Giữ kết quả trả về |
| Có `return` từ `try` | Cleanup ném B | Báo B, không trả giá trị |
| Có lỗi từ `try` | `finally` chứa `return` | Anti-pattern: lỗi bị nuốt; test phải phát hiện và loại bỏ |

Test về **thứ tự đóng** nên được thiết kế bằng tài nguyên giả định có output xác định. Tránh dựa vào sleep hoặc trạng thái bên ngoài khi chỉ muốn kiểm tra semantics của `finally`.

## 16. `finally` trong tác vụ bất đồng bộ vẫn có cùng quy tắc

Một `Callable` hoặc `Runnable` chạy trong `ExecutorService` cũng có thể viết `finally` sai:

```java
java.util.concurrent.Callable<String> task = () -> {
    try {
        throw new IllegalStateException("task failed");
    } finally {
        return "done";
    }
};
```

Đây là mảnh Java minh họa, với `Callable` đã sử dụng tên đầy đủ. Nếu submit vào executor, `Future.get()` có thể nhận `"done"` như **kết quả thành công** dù logic chính đã ném `IllegalStateException`. Vì `finally` trả giá trị, ngoại lệ bị nuốt **trước khi executor kịp ghi nhận failure**.

Ngược lại:

```java
java.util.concurrent.Callable<String> task = () -> {
    try {
        throw new IllegalStateException("task failed");
    } finally {
        throw new IllegalArgumentException("cleanup failed");
    }
};
```

Nếu tác vụ được submit bình thường, `Future.get()` sẽ ném `ExecutionException` chứa **`IllegalArgumentException`** từ cleanup làm cause; lỗi đầu `IllegalStateException` đã bị thay thế.

Trong `CompletableFuture`, callback ném lỗi từ cleanup có thể khiến stage hoàn thành ngoại lệ. Caller quan sát qua `join()` thường thấy `CompletionException` với cause là lỗi đang được propagate. Không nên cho rằng wrapper của async tự động khôi phục primary exception đã mất trong logic `finally`.

Nguyên tắc áp dụng rộng: **đừng dùng `return` ở `finally` trong callable**, và **giữ secondary exception bằng suppression hoặc hợp đồng xử lý rõ ràng** trước khi để task kết thúc. Điều này càng quan trọng khi lỗi được truyền qua nhiều thread và ít có cơ hội xem log trong đúng worker ban đầu.

## 17. `finally` và interrupt/cancellation: đừng vô tình phá tín hiệu dừng

Trong ứng dụng đa luồng, cleanup có thể diễn ra khi thread nhận `InterruptedException`:

```java
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
} finally {
    cleanup();
}
```

Đây là mảnh minh họa trong phương thức `void`, với `queue` và `cleanup()` đã khai báo. Phương thức `cleanup()` cần có hợp đồng phù hợp để không ném lỗi không mong đợi và che hướng xử lý interrupt. Nếu cleanup có thao tác chờ có thể ngắt, cờ interrupt đã được khôi phục có thể khiến lời gọi chờ mới lập tức ném `InterruptedException`; cần thiết kế đường dừng và dọn dẹp có chủ đích.

Với Future bị hủy, việc cancel không bảo đảm cleanup hoàn tất ngay. Nếu worker bất ngờ nuốt exception bằng `return` trong `finally`, trạng thái thực thi và trạng thái điều phối có thể khác kỳ vọng. Cần tách **yêu cầu dừng**, **việc worker thoát**, và **tình trạng tài nguyên đã được dọn**.

Không nên ghi mọi `InterruptedException` thành `ERROR` nếu đây là tín hiệu shutdown dự kiến, nhưng cũng không nên loại bỏ một lỗi cleanup nghiêm trọng chỉ vì thread đang dừng.

## 18. Những hiểu nhầm hay gặp

### “`finally` dùng để cleanup nên có thể `return` trong đó”

Sai. `return` trong `finally` thay thế cả `return`/`throw` đang chờ, khiến caller nhận sai kết quả hoặc mất ngoại lệ. `finally` nên tập trung vào cleanup **không thay đổi điều khiển luồng**.

### “Đã `throw e` trong `catch` thì chắc chắn caller thấy `e`”

Sai nếu `finally` hoàn thành đột ngột bằng `return` hoặc `throw` mới. Phải xem toàn bộ đường xử lý.

### “Exception nào xảy ra trước sẽ tự nằm trong `Caused by`”

Không đúng. Cause cần được thiết lập bằng constructor/`initCause` hoặc cơ chế tương đương. Hai exception xuất hiện liên tiếp trong `try` và `finally` **không tự có quan hệ cause**.

### “Exception ở `finally` sẽ tự suppressed vào exception gốc”

Không đúng đối với **`try-finally` thủ công thông thường**. `try-with-resources` có cơ chế suppression cho lỗi `close()` theo hợp đồng, còn `finally` tự viết cần chính sách rõ ràng.

### “Suppressed là lỗi có thể bỏ qua”

Không nhất thiết. Lỗi đóng stream/transaction có thể làm mất dữ liệu, rò kết nối hoặc che hậu quả cleanup. `Suppressed` chỉ là vai trò trong quá trình truyền lỗi, không phải mức độ nghiêm trọng.

### “Có `getSuppressed()` rỗng nghĩa là cleanup đã thành công”

Không suy ra được. Có thể ứng dụng đã dùng `try-finally` sai và làm mất lỗi, hoặc không lưu ngoại lệ thứ cấp; log cũng có thể bị cắt. Chỉ thấy `getSuppressed().length == 0` không chứng minh cleanup an toàn.

### “Thêm `catch (Exception e)` xung quanh mọi cleanup là đủ”

Nếu catch xong rồi im lặng, cleanup failure biến mất. Nếu luôn ném exception mới, nó có thể che lỗi chính. Cần policy rõ: propagate cleanup failure khi không có primary, hoặc ghi nó làm suppressed khi đã có primary, và xem xét side effect cần xử lý riêng.

### “Tự đóng tài nguyên thủ công lúc nào cũng kiểm soát tốt hơn”

Với tài nguyên `AutoCloseable`, `try-with-resources` thường an toàn, ngắn và đúng quy tắc exception hơn. Viết lại logic thủ công dễ sai thứ tự close và mất suppressed exception.

### “Đã thấy `ExecutionException` trong `Future.get()` là biết lỗi gốc”

`ExecutionException` thường chỉ là wrapper của lỗi worker. Nếu worker đã làm mất exception chính do cleanup, `getCause()` chỉ cho thấy **lỗi đã tồn tại sau khi `finally` kết thúc**, không thể tự khôi phục lỗi bị nuốt.

## 19. Checklist review một khối `finally`

Khi đọc code, kiểm tra lần lượt:

1. `finally` có **`return`**, `throw` chủ động, hoặc logic điều khiển luồng bất thường không?
2. Có phương thức cleanup nào **có thể ném checked hoặc unchecked exception** không?
3. Nếu thân `try` đã ném A, cleanup ném B thì **caller sẽ thấy gì**? A còn được bảo toàn qua cause/suppressed không?
4. Tài nguyên có triển khai `AutoCloseable` để thay bằng `try-with-resources` không?
5. Có nhiều tài nguyên? Thứ tự đóng có đúng và các lỗi đóng tiếp theo có bị mất không?
6. Nếu **không có primary**, lỗi cleanup có được báo đúng (không bị nuốt) không?
7. Nếu cleanup thất bại, có nguy cơ ảnh hưởng transaction, file, socket, lock hoặc dữ liệu đã ghi không?
8. Logging có in **Throwable đầy đủ** kèm suppressed ở đúng boundary, tránh log-and-rethrow trùng không?
9. Trong async, exception sau cleanup có được ghi nhận đúng trong `Future`/`CompletableFuture` không?
10. Test có bao phủ **đồng thời lỗi ở thân try và lỗi cleanup**, thay vì chỉ test happy path không?

## 20. Hướng thiết kế theo tình huống

| Tình huống | Lựa chọn thường phù hợp |
|---|---|
| File, stream, JDBC resource có `AutoCloseable` | **`try-with-resources`**; xem cả cause và suppressed |
| Cleanup không thể ném và rất ngắn | `finally` thường, không `return`/`throw` |
| Cleanup có thể ném checked exception nhưng tài nguyên không phải AutoCloseable | Viết policy bảo toàn primary; cân nhắc adapter `AutoCloseable` |
| Cleanup quan trọng hơn hoặc tạo sự cố độc lập | Quy định rõ precedence/aggregation và cảnh báo, không dựa vào tình cờ “throw cuối thắng” |
| Method cần trả kết quả nhưng cleanup có thể thất bại | Xác định liệu kết quả còn hợp lệ khi cleanup lỗi; giữ thông tin lỗi theo hợp đồng |
| Task executor/Future | Tránh `return` trong `finally`; đừng để cleanup vô tình che worker failure |
| Pipeline nhiều layer | Giữ cause và suppressed, log ở ranh giới có trách nhiệm |

**Kết luận:** `finally` có ưu tiên trong đường hoàn thành của `try`/`catch`, nên `return` hoặc exception trong `finally` có thể **ghi đè kết quả và làm mất lỗi chính**. Hãy dùng `try-with-resources` cho tài nguyên thích hợp, chỉ thực hiện cleanup an toàn trong `finally`, và khi có nhiều ngoại lệ, phải giữ rõ **primary** cùng **suppressed** thay vì để lỗi xảy ra cuối cùng vô tình quyết định câu chuyện mà caller và log nhìn thấy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-006">Đóng tài nguyên thủ công thiếu nhánh thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng tài nguyên thủ công thiếu nhánh thất bại

## 1. Vấn đề: đường thành công có close, đường thất bại lại không

Một phương thức Java có thể sử dụng file, stream, socket, kết nối cơ sở dữ liệu, khóa hoặc một tài nguyên có vòng đời riêng. Khi công việc kết thúc, tài nguyên phải được thu hồi theo hợp đồng. **Sai lầm thường gặp** là chỉ gọi phương thức dọn dẹp ở **đường thành công**, quên rằng phần mở tài nguyên, thao tác chính, bước kiểm tra, câu lệnh return hoặc chính thao tác dọn dẹp đều có thể gặp lỗi.

Ví dụ:

~~~java
// Phản mẫu minh họa; openReader() trả về java.io.BufferedReader.
BufferedReader reader = openReader();
String firstLine = reader.readLine();  // Có thể ném IOException.
reader.close();                        // Không chạy nếu readLine() ném lỗi.
return firstLine;
~~~

Trong trường hợp bình thường, close được gọi. Nhưng nếu readLine() ném IOException, điều khiển thoát khỏi phương thức và **dòng close không được thực thi**. Tài nguyên có thể tiếp tục giữ file descriptor, buffer hoặc những dữ liệu nội bộ cần thu hồi. Nếu một phương thức tương tự được gọi nhiều lần, việc bỏ sót nhánh thất bại có thể tích lũy tài nguyên và khiến lỗi chỉ bộc lộ sau một thời gian vận hành.

Một cách sửa sơ sài khác là đưa close vào finally, nhưng **không xét close cũng có thể ném exception**. Khi phần thân và cleanup cùng lỗi, exception từ finally có thể che exception công việc chính, khiến log chỉ còn “close failed”, mất dấu “read failed”.

**Mục tiêu của bài:** bảo đảm những tài nguyên đã được mở thành công sẽ được thử đóng đúng lúc trên mọi đường rời phạm vi sở hữu; đồng thời giữ đầy đủ lỗi công việc và lỗi cleanup. Cách ưu tiên trong Java 17+ cho tài nguyên **AutoCloseable** là **try-with-resources**; cleanup thủ công chỉ nên dùng khi vòng đời tài nguyên hoặc hợp đồng API đòi hỏi logic đặc biệt.

## 2. Những nhánh phải xét khi đọc mã có tài nguyên

Đừng chỉ hỏi “có close không?”. Hãy lập danh sách **điểm phương thức có thể rời khỏi** và **trạng thái tài nguyên tại điểm đó**:

| Điểm xảy ra | Rủi ro nếu cleanup được viết thủ công sơ sài | Cách xét |
| --- | --- | --- |
| Mở tài nguyên đầu tiên thất bại | Không có tài nguyên hợp lệ để đóng; cố đóng biến null hoặc tài nguyên chưa sở hữu | Chỉ đóng những tài nguyên thực sự đã mở thành công |
| Mở tài nguyên thứ hai thất bại | Tài nguyên thứ nhất đã mở nhưng bị bỏ quên | Đóng tài nguyên đã mở trước |
| Thao tác chính ném checked exception | Chạy nhảy qua dòng close nằm sau thao tác | Đảm bảo cleanup ở mọi đường ném |
| Thao tác chính ném RuntimeException hoặc Error | Catch một loại checked exception không đủ | Ưu tiên cơ chế cleanup không phụ thuộc loại lỗi |
| Return sớm khi đã có tài nguyên | Bỏ qua close đặt cuối phương thức | Đóng trước khi return hoàn tất |
| Break/continue/nhánh điều kiện | Bỏ qua một hoặc nhiều bước cleanup | Gắn quyền sở hữu với phạm vi phù hợp |
| Cleanup cũng ném exception | Lỗi cleanup che mất lỗi công việc hoặc ngăn cleanup còn lại | Giữ primary, thu thập lỗi phụ |
| Hủy/interrupt tác vụ | Luồng rời thao tác chờ khi tài nguyên vẫn đang giữ | Cleanup và chính sách interruption cần nhất quán |
| Nhiều tài nguyên cùng dùng | Thứ tự đóng sai, chỉ đóng được một tài nguyên | Đóng theo thứ tự phù hợp và vẫn thử phần còn lại |
| Thao tác hoàn tất nhưng close/flush lỗi | Caller nhận “thành công” dù bước hoàn tất thất bại | Truyền lỗi cleanup theo hợp đồng |

Cốt lõi là **quyền sở hữu tài nguyên**: phương thức hay đối tượng nào có trách nhiệm đóng, tại thời điểm nào, và sau khi chuyển quyền sở hữu thì ai tiếp tục chịu trách nhiệm. Tên biến hoặc việc một object còn reachable không tự xác lập quyền sở hữu; phải đọc hợp đồng API.

## 3. Phản mẫu: close ở cuối không bảo vệ đường exception

Xét phương thức giả định:

~~~java
// Mã minh họa theo ngữ cảnh; không phải chương trình độc lập.
static byte[] readAll(Path path) throws IOException {
    InputStream input = Files.newInputStream(path);
    byte[] bytes = input.readAllBytes();
    input.close();
    return bytes;
}
~~~

Nếu **readAllBytes()** ném IOException hoặc OutOfMemoryError, **input.close()** không được gọi. Dù caller catch lỗi, trách nhiệm giải phóng tài nguyên bên trong phương thức đã bị bỏ sót.

Cách sửa bằng try-with-resources:

~~~java
// Minh họa phương thức Java 17+, với imports thích hợp.
static byte[] readAll(Path path) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
        return input.readAllBytes();
    }
}
~~~

Với **InputStream implements Closeable** (và qua đó implements AutoCloseable), Java gọi **close()** khi rời khối try, kể cả khi readAllBytes ném lỗi hoặc thân try đi qua return.

Nếu phần thân thất bại và close cũng thất bại, lỗi phần thân được giữ là **primary exception**; lỗi close được gắn vào danh sách **suppressed** trên primary. Việc đóng file có thể báo lỗi: người gọi cần biết cả kết quả công việc và việc thu hồi tài nguyên có thành công hay không.

Đây không phải bảo đảm rằng bytes chưa được đọc trước khi lỗi sẽ “được hoàn tác”; nó chỉ bảo đảm **vòng đời close được xử lý** và exception được báo theo hợp đồng.

## 4. Vì sao finally chưa đủ khi cleanup cũng có thể thất bại?

Một cách sửa cũ thường là:

~~~java
// Phản mẫu minh họa.
InputStream input = Files.newInputStream(path);
try {
    return input.readAllBytes();
} finally {
    input.close();
}
~~~

Mẫu này tốt hơn việc close ở cuối vì **close được thử khi phần thân lỗi hoặc return**. Tuy nhiên, nếu readAllBytes ném một IOException rồi close ném IOException khác, ngoại lệ phát sinh trong finally có thể **thay thế lỗi đọc ban đầu**. Người gọi chỉ thấy lỗi đóng tài nguyên mà không thấy đường lỗi khiến thao tác chính thất bại.

Nếu trong finally còn có **return**, nó có thể che cả exception đang chờ và kết quả đang chờ:

~~~java
// Phản mẫu minh họa; tuyệt đối không dùng như giải pháp cleanup.
try {
    performOperation();
} finally {
    return; // Có thể làm biến mất exception từ try.
}
~~~

**Không dùng return trong finally để “đảm bảo cleanup”**. Và đừng nghĩ rằng catch cleanup rồi bỏ qua là an toàn:

~~~java
// Phản mẫu minh họa.
try {
    resource.close();
} catch (Exception ignored) {
    // Lỗi cleanup bị nuốt, trạng thái thu hồi không rõ.
}
~~~

Khi việc close gặp lỗi, caller có thể cần biết tài nguyên có còn hợp lệ, dữ liệu có flush đủ hay không hoặc có nên retry. Giấu tất cả lỗi trong catch làm giảm khả năng phát hiện những trường hợp này.

## 5. Cơ chế chuẩn Java: try-with-resources

**Try-with-resources** được giới thiệu từ Java 7 và dùng được trong Java 17+. Mỗi tài nguyên khai báo trong dấu ngoặc phải là **AutoCloseable**:

~~~java
// Minh họa; openResource() trả về một AutoCloseable thích hợp.
try (var resource = openResource()) {
    performOperation(resource);
}
~~~

Java tự quản lý lời gọi **resource.close()** ở cuối phạm vi try. Những bảo đảm có liên quan tới bài này:

1. Nếu phần thân chạy thành công, close vẫn được gọi.
2. Nếu phần thân ném exception, close vẫn được thử.
3. Nếu phần thân đi qua return, close được thực hiện **trước khi phương thức hoàn tất return**.
4. Nếu có nhiều tài nguyên, chúng được đóng theo **thứ tự ngược khi khai báo**.
5. Nếu đang mở tài nguyên sau mà thất bại, những tài nguyên đã mở trước vẫn được đóng.
6. Nếu phần thân đã lỗi và close tiếp tục lỗi, lỗi close được ghi là **suppressed exception**, không che primary theo cơ chế chuẩn.
7. Nếu phần thân thành công nhưng close thất bại, lỗi close có thể được ném như exception chính của lời gọi.

Đây là cách giảm số lượng nhánh phải tự viết, đặc biệt khi một API có nhiều loại checked và unchecked exceptions. Cấu trúc cú pháp làm **quyền sở hữu và phạm vi đóng** rõ ràng hơn.

Cần hiểu rằng **AutoCloseable** khai báo **close() throws Exception**; **java.io.Closeable** có chữ ký **close() throws IOException**. Việc khai báo/bắt checked exception phụ thuộc **kiểu tĩnh** của tài nguyên và API mà phương thức gọi, không phải try-with-resources tự loại bỏ checked exceptions.

## 6. Ví dụ Java 17+ hoàn chỉnh: sáu đường rời và lỗi cleanup

Chương trình dưới đây sử dụng một loại tài nguyên giả để quan sát hành vi rõ ràng, không phụ thuộc file, database hoặc mạng thật. **DemoResource** in tên khi mở/đóng và có thể được cấu hình ném IOException trong close.

Sáu nhóm kịch bản được chạy tuần tự:

- **Không có finally**: công việc lỗi trước close, tài nguyên không được thử đóng.
- **Finally sai**: cleanup ném lỗi và che lỗi công việc.
- **Try-with-resources đúng**: giữ lỗi công việc làm primary và lỗi đóng làm suppressed.
- **Hai tài nguyên**: đóng ngược thứ tự và giữ mọi lỗi phụ.
- **Mở tài nguyên thứ hai thất bại**: vẫn đóng tài nguyên mở trước đó.
- **Return nhưng close lỗi**: không trả kết quả như thể thành công khi close thất bại.

Lưu file **CleanupBranchesDemo.java**:

~~~java
import java.io.IOException;

public class CleanupBranchesDemo {
    static final class DemoResource implements AutoCloseable {
        private final String name;
        private final boolean closeFails;

        DemoResource(String name, boolean closeFails) {
            this.name = name;
            this.closeFails = closeFails;
            System.out.println("open " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            if (closeFails) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    static DemoResource openSecondAndFail() throws IOException {
        System.out.println("opening second failed");
        throw new IOException("second open failed");
    }

    static void wrongWithoutFinally() throws IOException {
        DemoResource resource = new DemoResource("leaky", false);
        throw new IOException("work failed");
    }

    static void wrongWithFinally() throws IOException {
        DemoResource resource = new DemoResource("masked", true);
        try {
            throw new IOException("work failed");
        } finally {
            resource.close();
        }
    }

    static void correctWorkAndCloseFailure() throws IOException {
        try (DemoResource resource = new DemoResource("safe", true)) {
            throw new IOException("work failed");
        }
    }

    static void correctSeveralResources() throws IOException {
        try (DemoResource first = new DemoResource("first", true);
             DemoResource second = new DemoResource("second", true)) {
            throw new IOException("work failed");
        }
    }

    static void correctInitializationFailure() throws IOException {
        try (DemoResource first = new DemoResource("opened", true);
             DemoResource second = openSecondAndFail()) {
            System.out.println("unreachable");
        }
    }

    static String returnAfterClose() throws IOException {
        try (DemoResource resource = new DemoResource("return", true)) {
            return "done";
        }
    }

    static void show(IOException failure) {
        System.out.println("primary: " + failure.getMessage());
        for (Throwable secondary : failure.getSuppressed()) {
            System.out.println("suppressed: " + secondary.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("== No finally ==");
        try {
            wrongWithoutFinally();
        } catch (IOException failure) {
            show(failure);
        }
        System.out.println("== Incorrect finally ==");
        try {
            wrongWithFinally();
        } catch (IOException failure) {
            show(failure);
        }
        System.out.println("== Try-with-resources ==");
        try {
            correctWorkAndCloseFailure();
        } catch (IOException failure) {
            show(failure);
        }
        System.out.println("== Two resources ==");
        try {
            correctSeveralResources();
        } catch (IOException failure) {
            show(failure);
        }
        System.out.println("== Initialization failure ==");
        try {
            correctInitializationFailure();
        } catch (IOException failure) {
            show(failure);
        }
        System.out.println("== Return then close failure ==");
        try {
            System.out.println("result: " + returnAfterClose());
        } catch (IOException failure) {
            show(failure);
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 CleanupBranchesDemo.java
java CleanupBranchesDemo
~~~

Stdout đã được kiểm tra trực tiếp:

~~~text
== No finally ==
open leaky
primary: work failed
== Incorrect finally ==
open masked
close masked
primary: close masked failed
== Try-with-resources ==
open safe
close safe
primary: work failed
suppressed: close safe failed
== Two resources ==
open first
open second
close second
close first
primary: work failed
suppressed: close second failed
suppressed: close first failed
== Initialization failure ==
open opened
opening second failed
close opened
primary: second open failed
suppressed: close opened failed
== Return then close failure ==
open return
close return
primary: close return failed
~~~

### Đọc kết quả: No finally

Tài nguyên **leaky** được mở và chương trình ném **work failed**. Không có dòng **close leaky**. Đây là phản mẫu minh họa **thiếu nhánh đóng khi công việc lỗi**. Tài nguyên giả chỉ in log; trong hệ thống thật, một stream/socket/connection tương tự có thể giữ tài nguyên hạ tầng cho tới khi được giải phóng bằng cách khác, và không được dựa vào garbage collector để đảm bảo close đúng thời điểm.

### Đọc kết quả: Incorrect finally

Phần thân ném **work failed**, rồi finally gọi close trên **masked** và close ném **close masked failed**. Exception thoát ra là lỗi cleanup, khiến **work failed** bị che. Không có suppressed exception tự động trong mẫu finally này.

### Đọc kết quả: Try-with-resources

Tài nguyên **safe** được đóng khi thân try thất bại. **work failed** vẫn là primary; **close safe failed** nằm trong danh sách suppressed. Chương trình cho thấy cả hai thông tin cùng được giữ mà không phải tự viết catch/finally phức tạp.

### Đọc kết quả: Two resources

Java đóng **second** rồi **first**. Khi hai lần đóng đều ném IOException, phần thân **work failed** vẫn là primary, còn lỗi đóng second và first lần lượt được thêm vào suppressed. Đây là đường rất dễ bỏ sót nếu viết các lệnh close nối tiếp trong finally mà không có chiến lược bắt từng lỗi.

### Đọc kết quả: Initialization failure

Tài nguyên **opened** đã được tạo thành công. Mở tài nguyên tiếp theo bằng **openSecondAndFail()** ném **second open failed** trước khi có resource thứ hai. Try-with-resources vẫn gọi close trên **opened**. Nếu close của opened cũng lỗi, lỗi đó được suppressed lên exception khi mở thứ hai. Đó là cách tránh rò tài nguyên **đã mở trước khi quá trình khởi tạo đầy đủ hoàn thành**.

### Đọc kết quả: Return then close failure

Lệnh return **"done"** được đánh giá trong thân try, nhưng Java cần đóng resource trước khi phương thức thực sự hoàn tất. Close ném IOException, nên caller không nhận được **"done"**; thay vào đó catch in **close return failed**. Điều này quan trọng với các stream có thể flush/hoàn tất dữ liệu lúc đóng: đến được lệnh return không đồng nghĩa toàn bộ lời gọi thành công.

## 7. Lỗi khi mở tài nguyên thứ hai: nhánh thường bị quên

Một phản mẫu khởi tạo nhiều tài nguyên:

~~~java
// Minh họa theo ngữ cảnh; cả a và b cần được đóng.
Resource a = openA();
Resource b = openB(); // Có thể ném exception.
try {
    use(a, b);
} finally {
    b.close();
    a.close();
}
~~~

Nếu **openB()** ném exception, **khối try/finally chưa bắt đầu**, nên a không được đóng. Đây là lý do “có finally” vẫn chưa đủ nếu **phạm vi finally bắt đầu sau quá trình cấp phát có thể thất bại**.

Sửa bằng try-with-resources:

~~~java
// Minh họa: Resource implements AutoCloseable.
try (Resource a = openA();
     Resource b = openB()) {
    use(a, b);
}
~~~

Nếu a mở thành công rồi b không mở được, Java đóng a trên đường xử lý thất bại. Nếu close của a cũng ném lỗi, lỗi khởi tạo b vẫn được giữ là primary khi suppression hoạt động.

Nếu nhà cung cấp tài nguyên có factory với tác dụng phụ **trước khi trả về object**, nhưng factory thất bại và tự làm thất thoát tài nguyên nội bộ, try-with-resources bên ngoài **không thể đóng một object chưa được trả về**. Chính factory phải có cleanup khi nó cấp phát dở dang:

~~~java
// Minh họa logic bên trong factory; không phải chương trình độc lập.
ResourcePartial partial = openPartial();
try {
    finishInitialization(partial);
    return partial;
} catch (Exception failure) {
    // Cần cleanup partial theo cơ chế bảo toàn primary phù hợp.
    throw failure;
}
~~~

Không sao chép đoạn này như một factory hoàn chỉnh: comment đại diện cho bước cleanup còn cần được triển khai, và ví dụ chỉ minh họa **ranh giới trách nhiệm khi khởi tạo**. Trong code thật, ưu tiên thiết kế constructor/factory có bảo đảm exception safety rõ ràng hoặc sử dụng try-with-resources ngay bên trong factory khi có thể.

## 8. Lỗi thân try và lỗi close: primary khác suppressed

`try-with-resources` thường giữ **lỗi thân `try` A làm primary** khi `close()` ném thêm B. Người gọi xem `A.getSuppressed()` để thấy B. Nếu service bọc A thành C thì B vẫn nằm ở `C.getCause().getSuppressed()`, không tự động chuyển sang suppressed của C.

~~~text
body: throw A      -> primary A
close(): throw B   -> A.getSuppressed() chứa B
wrap A thành C     -> C.getCause() là A
~~~

Khi chỉ `close()` thất bại, chính lỗi close là primary. Nếu nhiều tài nguyên cùng đóng thất bại, lỗi close **đầu tiên theo thứ tự đóng ngược** có thể trở thành primary, các lỗi đóng sau được giữ như suppressed; Java vẫn thử đóng những tài nguyên còn lại.

Trong bài cleanup, **không được xem lỗi close luôn vô hại**: tùy API, thao tác đóng có thể flush/finalize hoặc xác nhận hoàn tất dữ liệu. Đọc hợp đồng resource và test hậu điều kiện của thao tác, không chỉ đếm exception.

## 9. Đóng nhiều tài nguyên: còn phải thử close tiếp theo

Phản mẫu:

~~~java
// Phản mẫu minh họa, khi đã giữ cả a và b.
finally {
    b.close(); // Nếu ném, a.close() không được chạy.
    a.close();
}
~~~

Một lệnh close ném exception sẽ bỏ qua lệnh kế tiếp trong cùng finally, trừ khi bạn chủ động bắt xử lý. Tài nguyên a có thể bị bỏ quên chỉ vì b close lỗi.

Với try-with-resources:

~~~java
// Minh họa tài nguyên triển khai AutoCloseable.
try (Resource a = openA();
     Resource b = openB()) {
    use(a, b);
}
~~~

Java đóng b trước, rồi a. Các close được xử lý theo cơ chế bảo toàn lỗi; lỗi từ close đầu không ngăn toàn bộ cơ chế tiếp tục thử đóng những tài nguyên đã mở còn lại.

**Lưu ý về thứ tự:** Đóng ngược khai báo thường phù hợp với phụ thuộc vòng đời. Ví dụ stream bọc một stream khác thường phải đóng wrapper trước hoặc để wrapper sở hữu close của stream dưới; **không nên đóng cùng tài nguyên hai lần** chỉ vì hai biến khác nhau tham chiếu vào nó. Một số wrapper close luôn tài nguyên nền; hãy đọc hợp đồng ownership.

### Thứ tự đóng không phải hình thức

Nếu B phụ thuộc vào A khi close, đặt **A trước B** trong khai báo để B đóng trước A:

~~~java
// Minh họa theo ngữ cảnh.
try (Resource connection = openConnection();
     Resource statement = openStatement(connection)) {
    use(statement);
}
~~~

Nếu A có nhiệm vụ sở hữu/đóng B tự động, khai báo cả A và B làm tài nguyên độc lập cần được cân nhắc để tránh đóng trùng không có chủ đích. **AutoCloseable** không mặc định cam kết close idempotent cho mọi loại tài nguyên.

## 10. Trường hợp biến null, factory thất bại và scope

Một cách viết cleanup thủ công thường dùng:

~~~java
// Minh họa theo ngữ cảnh.
Resource resource = null;
try {
    resource = openResource();
    work(resource);
} finally {
    resource.close(); // Nếu openResource thất bại, resource == null.
}
~~~

Nếu openResource ném lỗi trước khi gán biến, finally tiếp tục dereference null và có thể ném NullPointerException, che khuất lỗi mở tài nguyên.

Có thể phòng null:

~~~java
// Minh họa một nhánh cleanup thủ công.
finally {
    if (resource != null) {
        resource.close();
    }
}
~~~

Nhưng cách này vẫn chưa tự giải quyết vấn đề **close cũng ném lỗi**, và phải xét những exception của phần thân. Nếu tài nguyên là AutoCloseable, **try-with-resources** thường rõ hơn. Java sẽ **không gọi close nếu giá trị tài nguyên trong resource specification là null**; tuy nhiên thiết kế factory nên ưu tiên trả object hợp lệ hoặc ném lỗi rõ ràng để code không phải dựa vào null.

Nếu một thư viện chỉ trả handle đặc biệt và không triển khai AutoCloseable, cần bọc handle trong adapter quản lý ownership hoặc thiết kế cleanup thủ công có đầy đủ nhánh.

## 11. Cleanup thủ công khi API không có AutoCloseable

Không phải API nào cũng cung cấp AutoCloseable. Đôi khi phải dùng phương thức như **release()**, **unlock()**, **rollback()** hoặc một lệnh hủy riêng. Khi đó code cần xác định:

- **Khi nào quyền sở hữu bắt đầu?** Sau khi mở/lock thành công.
- **Khi nào quyền sở hữu kết thúc?** Sau khi cleanup thành công hoặc chuyển giao.
- **Loại lỗi nào có thể phát sinh?** Checked, unchecked, Error, interruption.
- **Lỗi nào là primary nếu nhiều bước thất bại?**
- **Có tiếp tục cleanup các tài nguyên khác được không?**
- **Nếu cleanup lỗi, trạng thái hệ thống còn tin cậy không?**

Một ví dụ an toàn tương đối với **Lock**:

~~~java
// Minh họa: lock là java.util.concurrent.locks.Lock.
lock.lock();
try {
    performOperation();
} finally {
    lock.unlock();
}
~~~

Khóa chỉ được đưa vào phạm vi finally **sau khi lock() thành công**. Nhưng nếu **unlock()** ném lỗi trong lúc thân try cũng lỗi, vấn đề che primary vẫn có thể phát sinh. Các Lock tiêu chuẩn thông thường không dùng checked exception cho unlock, nhưng có thể ném unchecked exception nếu sử dụng sai hợp đồng.

Với API interruptible:

~~~java
// Minh họa: java.util.concurrent.locks.Lock.
lock.lockInterruptibly();
try {
    performOperation();
} finally {
    lock.unlock();
}
~~~

Đặt **lockInterruptibly() trước try** để nếu nó ném InterruptedException trước khi lấy khóa, code không gọi unlock trên lock mình chưa sở hữu. Đây là một lỗi scope cleanup đặc biệt khó phát hiện nếu chỉ nhìn thấy có finally.

### Có thể dùng adapter AutoCloseable

Với một resource có **release()** nhưng không implements AutoCloseable, có thể tạo một adapter:

~~~java
// Minh họa trong phương thức, giả định handle đã acquire thành công.
try (AutoCloseable scope = () -> handle.release()) {
    use(handle);
}
~~~

Cần lưu ý **AutoCloseable.close()** có thể ném Exception, nên phương thức chứa code phải khai báo/bắt exception phù hợp. Nếu kiểu handle cần cleanup chính xác một lần hoặc có thể thất bại một phần, adapter phải tuân thủ contract đó; biểu thức lambda trên chỉ là minh họa ngắn cho mô hình.

## 12. Cleanup khi catch một kiểu lỗi nhưng bỏ sót kiểu khác

Một mẫu thủ công dễ thất bại:

~~~java
// Phản mẫu minh họa theo ngữ cảnh.
try {
    performOperation();
} catch (IOException failure) {
    resource.close();
    throw failure;
}
resource.close();
~~~

Code có thể xử lý IOException, nhưng nếu **performOperation() ném RuntimeException hoặc Error**, đường cleanup đó bị bỏ sót. Chỉ catch những loại đã nghĩ tới không chứng minh cleanup bao phủ mọi đường thất bại.

Kể cả đổi thành **catch(Exception)**, kiểu Error không bị bắt. Việc cố **catch(Throwable)** để tiếp tục chạy cũng rủi ro vì Error có thể đại diện tình trạng JVM nghiêm trọng, không phải điều kiện phục hồi bình thường.

**Try-with-resources** giải quyết việc đóng tài nguyên theo đường rời khối mà không phải viết từng catch riêng cho IOException, RuntimeException, Error hoặc return.

Nếu vẫn cần cleanup thủ công, hãy thiết kế theo *hợp đồng sở hữu tài nguyên* và tất cả điểm thoát có thể xảy ra; tránh tin rằng một catch đủ rộng là thay thế cho finally hoặc AutoCloseable.

## 13. Sửa bằng finally bảo toàn lỗi khi thực sự bắt buộc

Nếu API cleanup không phù hợp với AutoCloseable hoặc có ràng buộc đặc biệt, cần giữ lỗi công việc chính và đính kèm lỗi cleanup. Một mẫu **chỉ minh họa logic**, không phải trình thay thế tổng quát cho try-with-resources:

~~~java
// Minh họa phạm vi: resource implements AutoCloseable;
// work(resource) có thể ném IOException, còn close() ném IOException.
IOException primary = null;
try {
    work(resource);
} catch (IOException original) {
    primary = original;
    throw original;
} finally {
    try {
        resource.close();
    } catch (IOException cleanup) {
        if (primary != null && primary != cleanup) {
            primary.addSuppressed(cleanup);
        } else {
            throw cleanup;
        }
    }
}
~~~

Ở đây **primary** là chính object IOException đã bị bắt, không phải một bản copy chỉ chứa message. Khi cleanup ném IOException, **addSuppressed()** giữ lỗi phụ trên primary, rồi rethrow original từ catch tiếp tục có hiệu lực.

**Giới hạn nghiêm trọng của mẫu này:** Code chỉ catch IOException. Nếu work ném RuntimeException hoặc Error và close ném IOException, finally có thể che khuất lỗi work. Nếu tài nguyên có close() throws Exception rộng hơn, chữ ký đoạn này cũng chưa đủ. Vì vậy không nên copy mẫu này cho mọi tài nguyên rồi coi đã giải quyết xong mọi đường lỗi.

Nếu cần một quy tắc ưu tiên thực sự bao quát, có thể tổ chức code theo Throwable nhưng phải giữ đúng loại lỗi và tránh nuốt Error. Với close thuần AutoCloseable, **try-with-resources** vẫn là lựa chọn đơn giản và chính xác hơn.

### Tránh self-suppression

**Throwable.addSuppressed()** không cho phép thêm chính Throwable đó làm suppressed của nó. Nếu **primary == cleanup**, lệnh addSuppressed sẽ ném IllegalArgumentException. Đây là trường hợp hiếm nhưng phải được xét nếu cleanup và công việc có thể dùng chung một instance Throwable (ví dụ lỗi được tái sử dụng). Tài liệu Java cũng không bảo đảm suppression được bật trên mọi Throwable tùy chỉnh; nếu suppression bị vô hiệu hóa, addSuppressed không giữ lỗi phụ.

Không cố lưu mọi lỗi cleanup dưới dạng **cause** của primary: cause mô tả quan hệ nguyên nhân, còn suppressed mô tả lỗi phụ phát sinh trong quá trình xử lý/thu hồi. Dùng hai quan hệ đúng nghĩa giúp log dễ chẩn đoán hơn.

## 14. Đóng tài nguyên và giao dịch: đừng nhầm cleanup với rollback

Vấn đề “đủ nhánh cleanup” thường bị trộn với câu hỏi “dữ liệu có được khôi phục khi thất bại hay không?”. Đây là hai đảm bảo khác nhau.

**Đóng một stream** không xóa các byte đã ghi trước đó. **Đóng connection** không đồng nghĩa commit hay rollback thành công theo mọi hợp đồng JDBC/pool. **Release lock** không làm object tự quay về trạng thái trước khi sửa. **Hủy Future** không đảo ngược request mạng đã gửi.

Ví dụ với giao dịch JDBC thủ công, nếu connection thuộc quyền sở hữu của phạm vi này:

~~~java
// Mã minh họa theo ngữ cảnh; DataSource và SQLException đã import.
try (Connection connection = dataSource.getConnection()) {
    connection.setAutoCommit(false);
    try {
        doSqlWork(connection);
        connection.commit();
    } catch (SQLException workFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            workFailure.addSuppressed(rollbackFailure);
        }
        throw workFailure;
    }
}
~~~

Đây là ví dụ phục vụ phân tích đường lỗi **SQLException**, chưa phải class độc lập; **doSqlWork** là logic ứng dụng. Try-with-resources đảm bảo thử close connection trên các đường rời. Nhánh catch chủ động thử rollback nếu doSqlWork/commit ném SQLException; lỗi rollback được suppressed thay vì che lỗi chính. Nếu close còn lỗi, try-with-resources tiếp tục thêm lỗi close vào exception chính khi cơ chế suppression hoạt động.

**Những giới hạn phải nhớ:**

- Nếu doSqlWork có thể ném RuntimeException/Error, catch(SQLException) không thực hiện rollback cho những đường đó; cần thiết kế transaction boundary bao quát đúng hợp đồng ứng dụng.
- Nếu **setAutoCommit(false)** thất bại trước khi vào inner try, nhánh rollback ở trên không chạy; connection vẫn được thử close, nhưng phải phân tích trạng thái transaction riêng.
- Rollback có thể không thành công; việc gắn exception rollback vào suppressed **không bảo đảm transaction đã phục hồi**.
- Có framework quản lý transaction tự động; tự commit/rollback ngoài phạm vi sở hữu có thể phá hợp đồng framework.
- Sau lỗi transaction, connection có thể cần được đánh dấu không đáng tin hoặc loại khỏi pool theo cơ chế của datasource, không tiếp tục sử dụng như thể an toàn.

Với dữ liệu bên ngoài, **exception safety** và **state recovery** đòi hỏi chính sách transaction, idempotency hoặc compensation riêng. Try-with-resources chỉ quản lý vòng đời AutoCloseable và exception của close, không biến mọi tác động thành giao dịch nguyên tử.

## 15. Trả kết quả sớm, vòng lặp và chuyển quyền sở hữu

Các nhánh không ném exception cũng dễ bị quên:

~~~java
// Phản mẫu minh họa.
Resource resource = openResource();
if (skip(resource)) {
    return; // Bỏ qua close nếu viết nó ở cuối phương thức.
}
use(resource);
resource.close();
~~~

Với try-with-resources:

~~~java
// Minh họa cùng ngữ cảnh.
try (Resource resource = openResource()) {
    if (skip(resource)) {
        return;
    }
    use(resource);
}
~~~

Java vẫn đóng resource khi gặp return. Tương tự, rời khỏi phạm vi try qua break hoặc continue trong cấu trúc vòng lặp được sắp xếp phù hợp vẫn thực hiện cleanup.

**Nhưng đừng trả về chính tài nguyên rồi đóng nó trước khi caller dùng:**

~~~java
// Phản mẫu minh họa.
Resource openAndReturn() throws Exception {
    try (Resource resource = openResource()) {
        return resource; // Resource đã bị close trước khi caller nhận.
    }
}
~~~

Nếu nhiệm vụ phương thức là **chuyển quyền sở hữu resource cho caller**, nó không được tự đóng trước khi giao. Hợp đồng phải quy định caller chịu trách nhiệm đóng, ví dụ trả về một InputStream còn mở rồi caller đặt nó trong try-with-resources ở phía mình. Không áp dụng TWR một cách máy móc khi ownership thực sự cần chuyển giao.

Một trường hợp tương tự là trả về **Stream** từ API lazy: stream có thể còn cần đọc datasource sau khi phương thức trả về. Nếu datasource đã đóng trong phương thức, operation về sau có thể thất bại. Tài liệu API nên nói rõ quyền sở hữu và trách nhiệm close của caller.

## 16. Đóng khi task bị ngắt hoặc hủy

Trong concurrency, một tác vụ có thể rời một lệnh blocking qua **InterruptedException**. Nếu tài nguyên đang mở, cleanup vẫn phải chạy. Đặt resource trong try-with-resources giúp xử lý nhánh ném checked exception này cùng những nhánh lỗi khác.

~~~java
// Minh họa: resource implements AutoCloseable;
// waitForWork có thể ném InterruptedException.
try (Resource resource = openResource()) {
    waitForWork(resource);
}
~~~

Tuy nhiên, có hai mối quan tâm riêng:

1. **Bảo toàn thông tin InterruptedException:** Nếu method cho phép, để InterruptedException lan truyền; nếu không, boundary có thể cần khôi phục **Thread.currentThread().interrupt()** và kết thúc theo hợp đồng.
2. **Cleanup không tự cancel side effect:** Đóng resource không có nghĩa một request phía ngoài chắc chắn dừng hoặc rollback.

Cẩn thận nếu trong cleanup còn gọi API blocking có thể bị interrupt. Khi thread có interrupt flag, cleanup có thể thất bại ngay. Cần thiết kế timeout và chính sách xử lý lỗi cleanup, tránh đổi một đường yêu cầu dừng thành chờ vô hạn hoặc che lỗi gốc.

Nếu một Future bị **cancel(true)**, việc Future ở trạng thái canceled không chứng minh worker đã thôi sử dụng tài nguyên. Quy tắc sở hữu phải tính tới lúc worker thực sự kết thúc và lúc phạm vi cleanup chạy.

## 17. Khi nào close thất bại cần retry?

Không nên mặc định close có thể gọi lặp an toàn. **AutoCloseable** không bảo đảm mọi implementation **idempotent**; có loại close nhiều lần được phép, có loại dẫn đến lỗi hoặc tác dụng phụ khác.

Khi close ném exception:

- Đọc hợp đồng tài nguyên xem trạng thái sau thất bại có được xác định không.
- Không gọi close lần hai ngay lập tức chỉ vì muốn “chắc đã đóng”, nếu không hiểu hậu quả.
- Một số thao tác finish/flush/commit không phải cleanup thuần; retry có thể gây ghi trùng hoặc lỗi trạng thái.
- Nếu API cho phép và việc retry được thiết kế rõ, giới hạn số lần, backoff và log kết quả.
- Nếu tài nguyên còn khả năng sử dụng nguy hiểm, đánh dấu không đáng tin và chuyển cho tầng sở hữu cao hơn quyết định.

Với tài nguyên pool, đôi khi close trả kết nối về pool thay vì thật sự đóng socket. Close lỗi có thể yêu cầu pool loại bỏ handle. Điều này thuộc hợp đồng cụ thể của implementation; không tự suy ra mọi Connection.close đều xử lý như nhau.

## 18. Các đoạn cleanup có thể tự gây tác dụng phụ

Đừng xem cleanup là đoạn code “không có logic”:

- Một **Writer.close()** có thể flush buffer.
- Một **ZipOutputStream.close()** có thể cần hoàn tất cấu trúc dữ liệu nén.
- Một session hoặc transaction API có thể yêu cầu commit/rollback riêng.
- Một handler release khóa có thể ảnh hưởng quyền truy cập của thread khác.
- Một callback cleanup có thể ghi log, gửi metrics hoặc hủy lịch công việc.

Nếu close/cleanup thất bại, trạng thái cuối có thể không rõ ràng. Một ứng dụng ghi file có thể đã ghi được một phần dữ liệu; một request gửi xa có thể đã được xử lý. Không ghi kết quả **SUCCESS** chỉ vì xử lý chính đi tới cuối, nếu hợp đồng vẫn yêu cầu cleanup/finalization thành công.

Tương tự, không đánh dấu mọi lỗi cleanup là **FATAL**. Mức nghiêm trọng phụ thuộc loại tài nguyên, giai đoạn sử dụng, hệ quả trên dữ liệu và tính khả năng phục hồi. Tốt nhất là có hợp đồng rõ ràng về thế nào là một thao tác “hoàn thành”.

## 19. Ghi log lỗi chính và lỗi dọn dẹp

Một ứng dụng thường mắc hai lỗi đối lập:

**Chỉ ghi lỗi đầu:**

~~~java
// Phản mẫu minh họa.
logger.error("Operation failed: " + failure.getMessage());
~~~

Cách này có thể bỏ qua suppressed từ close, cause và stack trace. Trong Java logging framework hỗ trợ throwable, nên truyền đối tượng lỗi đầy đủ:

~~~java
// Minh họa với logger có API error(String, Throwable).
logger.error("Operation failed", failure);
~~~

Đoạn logger phụ thuộc framework tương ứng, không phải class Java SE độc lập. Trong production, xác minh formatter thực sự hiển thị **getCause()**, **getSuppressed()** và stack trace; pipeline JSON có thể cần cấu hình riêng.

**Ghi log mọi catch rồi throw lại:** Cùng một lỗi có thể bị log ở nhiều tầng, dẫn tới đếm trùng sự cố. Thường nên giữ cause/suppressed khi truyền lỗi và log một lần ở boundary phù hợp, bổ sung correlation ID, resource identifier và operation name theo chính sách bảo mật.

Nếu suppressed nằm trên cause của một wrapper, chỉ nhìn **wrapper.getSuppressed()** có thể không thấy nó. Hãy đọc toàn bộ cây exception, không chỉ một cấp.

## 20. Kiểm thử từng nhánh thay vì chỉ kiểm tra happy path

Một unit test cleanup chỉ gọi phương thức trên đường thành công không chứng minh exception safety. Cần có tài nguyên giả cho phép điều khiển **open**, **work**, **close** để tái hiện rõ từng đường lỗi.

Ma trận kiểm thử tối thiểu:

| Mở tài nguyên | Công việc | Close | Kỳ vọng |
| --- | --- | --- | --- |
| Thành công | Thành công | Thành công | Kết quả bình thường, close được gọi |
| Thành công | Thất bại A | Thành công | A được ném, close đã thử |
| Thành công | Thành công | Thất bại B | B được ném; không công bố thành công giả |
| Thành công | Thất bại A | Thất bại B | A primary, B suppressed |
| A mở thành công; B mở lỗi C | Không chạy | A đóng thành công | C là lỗi chính, A đã được đóng |
| A mở thành công; B mở lỗi C | Không chạy | A đóng lỗi D | C primary, D suppressed |
| A và B mở thành công | Thất bại C | B và A đóng lỗi D/E | C primary, suppressed D rồi E |
| Thành công | Return sớm | Đóng lỗi B | Không return bình thường nếu close bắt buộc báo lỗi |
| Thành công | RuntimeException | Close thành công | RuntimeException còn được ném, resource được đóng |
| Thành công | Error | Close có thể lỗi | TWR vẫn theo nguyên tắc cleanup và suppressed; không dùng catch(Throwable) để nuốt Error |

Với unit test dùng JUnit, tốt nhất dùng **assertSame** (identity) để kiểm tra primary là đúng exception instance từ work, và **assertArrayEquals** hoặc so identity từng suppressed để kiểm tra thứ tự. Nếu chỉ kiểm tra **assertThrows(IOException.class)**, test có thể PASS ngay cả khi lỗi **work failed** bị thay bằng **close failed**.

Chương trình chạy ở mục 6 đã trình bày sáu đường lỗi có đầu ra dễ theo dõi. Test thực tế nên thêm những tình huống không lỗi, lỗi unchecked, interrupted caller và trạng thái tài nguyên theo hợp đồng.

### Kiểm tra cleanup được gọi đúng lần

Một tài nguyên test có thể đếm số lần close:

~~~java
// Mã minh họa theo ngữ cảnh.
final class CountingResource implements AutoCloseable {
    int closes;

    @Override
    public void close() {
        closes++;
    }
}
~~~

Ở nơi sở hữu đúng tài nguyên, assert **closes == 1** sau đường thành công hoặc lỗi. Nhưng đừng khẳng định mọi API thực tế đều phải close đúng một lần theo logic nội bộ của wrapper; một wrapper có thể đóng tài nguyên bên dưới hoặc tổ chức vòng đời riêng. Test cần mô phỏng hợp đồng đang được dùng, không chỉ đếm lệnh gọi.

## 21. Checklist review code có tài nguyên

Khi xem một pull request chứa file, stream, connection, lock, session hoặc handle, hãy hỏi:

1. **Ai sở hữu tài nguyên?** Phương thức hiện tại, đối tượng dài hạn hay caller?
2. **Tài nguyên được mở ở đâu?** Có thể phát sinh lỗi giữa những lần mở không?
3. **Nếu thao tác chính ném exception**, close có được thử không?
4. **Nếu xảy ra return/break/continue**, close có chạy trước khi thoát phạm vi không?
5. **Nếu close ném exception**, lỗi chính có bị che mất không?
6. **Nếu có nhiều tài nguyên**, close đầu thất bại có ngăn các lần close sau không?
7. **Thứ tự đóng có đúng không**, có đóng trùng một resource thông qua wrapper và biến nền không?
8. **Nếu một resource chưa mở xong**, code có tránh gọi close/unlock trên handle chưa sở hữu không?
9. **Có cần rollback/compensation riêng không**, hay tác giả đang nhầm close với khôi phục dữ liệu?
10. **Nếu cleanup lỗi**, trạng thái resource còn tin cậy không, có cần đánh dấu hoặc loại bỏ nó không?
11. **Nếu bị interrupt/cancel**, cleanup và trạng thái thread có được xử lý đúng không?
12. **Logger có giữ cause/suppressed không**, hay chỉ ghi message?
13. **Unit test có bơm lỗi vào open/work/close** và xác minh primary/suppressed, trạng thái trước/sau không?

**Ưu tiên sửa:** Với tài nguyên AutoCloseable thuộc quyền sở hữu phạm vi, thay các đoạn open/use/close thủ công bằng **try-with-resources**. Chỉ dùng finally thủ công cho API hoặc semantics thật sự khác, kèm đầy đủ xử lý lỗi và test.

## 22. Những sai lầm nối tiếp thường bị bỏ qua

**Sai lầm 1 — đưa close xuống cuối cùng rồi tin là đủ.** Exception/return trước đó sẽ bỏ qua close. Gắn close với phạm vi bằng TWR hoặc finally đúng hợp đồng.

**Sai lầm 2 — có finally nhưng phạm vi bắt đầu quá muộn.** Nếu tài nguyên thứ hai mở lỗi trước khi vào try, tài nguyên thứ nhất rò. Khai báo nhiều resource trong TWR hoặc quản lý từng acquisition ngay khi thành công.

**Sai lầm 3 — finally đóng nhiều tài nguyên bằng lệnh nối tiếp.** Lỗi ở lần close đầu có thể bỏ qua close sau. Dùng nhiều resource TWR với thứ tự khai báo hợp lý.

**Sai lầm 4 — close exception che exception công việc.** Có thể làm nhóm debug sửa sai lỗi. TWR giữ primary/suppressed theo cơ chế chuẩn.

**Sai lầm 5 — catch cleanup rồi bỏ qua.** Không biết kết quả thu hồi, flush hoặc transaction. Phải có lý do nếu bỏ qua một lỗi cụ thể.

**Sai lầm 6 — chỉ catch IOException quanh work.** RuntimeException/Error vẫn làm code nhảy khỏi đường cleanup thủ công. TWR không cần dự đoán mọi class lỗi.

**Sai lầm 7 — close cả handle chưa sở hữu.** Với lockInterruptibly, unlock sau khi acquisition thất bại có thể ném IllegalMonitorStateException. Xác lập ownership trước khi đưa unlock vào finally.

**Sai lầm 8 — trả về một tài nguyên đã đóng.** TWR sẽ đóng trước return; chỉ dùng nếu trả về dữ liệu đã đọc, không phải handle caller còn cần dùng.

**Sai lầm 9 — coi close là rollback.** Dữ liệu bị sửa dở có thể còn đó. Cần transaction, idempotency hoặc compensation khi có side effect.

**Sai lầm 10 — quên lỗi close ở cuối thao tác thành công.** Nếu close báo lỗi, không tự ghi nhận SUCCESS khi hợp đồng yêu cầu đóng/flush hoàn tất.

**Sai lầm 11 — retry close vô điều kiện.** Không phải AutoCloseable nào cũng idempotent. Xem contract tài nguyên.

**Sai lầm 12 — thiếu test lỗi kép.** Assert loại exception không đủ nếu work và close cùng ném IOException; phải kiểm tra primary và suppressed cụ thể.

## 23. Quy trình cải tiến một đoạn cleanup thủ công

Một cách refactor có kiểm soát:

**Bước 1: Liệt kê resources và ownership.** Nếu wrapper đã sở hữu resource nền, tránh tạo hai đường đóng có thể trùng. Xác định API nào implements AutoCloseable hoặc Closeable.

**Bước 2: Vẽ các điểm acquisition.** Với mỗi resource, ghi rõ thời điểm mở thành công và tất cả đường có thể ném trong quá trình mở tiếp.

**Bước 3: Thống kê exit paths.** Exception checked/unchecked, early return, loop control, timeout, interruption và nhánh bình thường.

**Bước 4: Chọn phạm vi TWR.** Đưa việc acquisition vào dấu ngoặc try nếu phù hợp để Java bảo đảm cleanup cho những resource đã mở thành công.

**Bước 5: Thiết lập thứ tự khai báo.** Nếu tài nguyên B phụ thuộc A, khai báo A trước B để B đóng trước A; kiểm tra contract thật của wrapper.

**Bước 6: Xác định exception contract.** Caller sẽ nhận primary, suppressed và checked exceptions từ close thế nào? Khi thành công về work nhưng close lỗi, có coi lời gọi là thất bại không?

**Bước 7: Xử lý state recovery riêng.** Với transaction hoặc thao tác có side effect, quyết định rollback/commit/compensation và lỗi của chính các bước đó.

**Bước 8: Tạo test tiêm lỗi.** Bao gồm work fail, close fail, acquisition fail và nhiều close fail. Verify exact identity, order và close attempts.

**Bước 9: Quan sát môi trường thật.** Đảm bảo logs hiển thị cause/suppressed, theo dõi tài nguyên mở lâu, file descriptor, connection pool và timeout.

**Bước 10: Dọn mã cũ.** Loại bỏ close thủ công dư thừa khi TWR đã sở hữu tài nguyên; không để hai cơ chế đóng cùng một object mà không có hợp đồng.

## 24. Câu hỏi tự kiểm tra

**Nếu return nằm trong try-with-resources, có thể bỏ qua close không?**

Không. Close chạy trước khi phương thức hoàn tất return. Nếu close ném lỗi, kết quả return dự kiến có thể bị thay bởi exception, đúng như ví dụ runnable.

**Nếu openB() lỗi khi openA() đã thành công, try-with-resources có đóng A không?**

Có. Tài nguyên mở thành công trước đó được đóng trên đường thất bại khởi tạo tài nguyên kế tiếp. Nếu close A cũng lỗi, lỗi close có thể suppressed vào lỗi khởi tạo.

**Nếu cả work và close cùng ném IOException, catch bên ngoài nhận lỗi nào?**

Với TWR và suppression hoạt động, lỗi work là primary, còn lỗi close nằm trong **getSuppressed()**. Với finally thủ công không bảo toàn lỗi, close có thể che work.

**Nếu close lỗi mà work thành công, có thể return thành công không?**

Còn tùy cách tổ chức API và hợp đồng, nhưng TWR sẽ truyền close exception thay vì hoàn tất return bình thường khi close báo lỗi. Đừng bỏ lỗi chỉ để báo success.

**Nếu có hai resource, tại sao phải đóng ngược thứ tự?**

Vì resource mở sau thường phụ thuộc resource mở trước; TWR đóng từ phải sang trái. Đây là quy tắc của cú pháp Java, nhưng thiết kế vẫn phải xác định ownership và tránh close trùng.

**Nếu finally có null check, đã xử lý đủ chưa?**

Chưa. Null check chỉ tránh dereference handle chưa được gán; không giải quyết close failure, lỗi kép, thứ tự nhiều tài nguyên hoặc side effect cần rollback.

**Dùng try-with-resources có tự rollback transaction không?**

Không. Nó gọi close theo vòng đời AutoCloseable. Transaction state/commit/rollback phụ thuộc API và cơ chế quản lý giao dịch. Cần chính sách riêng.

**Có cần catch(Throwable) để đảm bảo cleanup không?**

Không cần nếu tài nguyên phù hợp AutoCloseable và dùng TWR. Catch(Throwable) tùy tiện có thể nuốt Error nghiêm trọng. Nếu phải quản lý thủ công, cần xác định rõ propagation và không giấu exception.

## 25. Tóm tắt quyết định kỹ thuật

**Đóng tài nguyên thủ công thiếu nhánh thất bại** là bug khi mã chỉ đóng trên happy path hoặc chỉ xử lý một vài loại lỗi. Việc thêm finally có thể sửa rò tài nguyên, nhưng chưa chắc giữ được **lỗi chính** khi cleanup cũng lỗi. Một lần mở tài nguyên sau thất bại, một close trong chuỗi thất bại hoặc return sớm đều là những nhánh cần được kiểm thử.

Đối với tài nguyên **AutoCloseable**, **try-with-resources** là lựa chọn chuẩn vì nó xử lý những nhánh này ngay trong cấu trúc ngôn ngữ: đóng những tài nguyên đã mở thành công, đóng theo thứ tự ngược, không bỏ qua cleanup khi thân try lỗi/return, giữ exception công việc làm primary và các lỗi close phụ dưới dạng suppressed.

Với API cần **unlock, rollback, cancel, release** theo semantics riêng, vẫn cần cleanup thủ công có chủ đích. Khi đó đừng chỉ kiểm tra “finally có chạy không”; phải kiểm tra **trách nhiệm sở hữu, mọi đường thoát, lỗi trong cleanup, trạng thái dữ liệu sau thất bại và khả năng quan sát**. Kiểm thử lỗi kép và đường khởi tạo thất bại thường phát hiện những sai sót mà happy-path test không thể nhìn thấy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-007">Dùng ngoại lệ để che việc kiểm tra điều kiện đơn giản</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng ngoại lệ để che việc kiểm tra điều kiện đơn giản — sai lầm thường gặp trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Nhận ra anti-pattern **dùng exception để điều khiển luồng xử lý bình thường** (*exception-driven control flow*), nhất là khi chỉ cần một điều kiện đơn giản.
- Phân biệt **trạng thái dự kiến trong nghiệp vụ** với **vi phạm hợp đồng/lỗi bất thường** cần ném ngoại lệ.
- Thay những khối `try/catch` quá rộng bằng guard clause, điều kiện rõ nghĩa, `Optional`, kết quả có cấu trúc hoặc API thao tác trực tiếp khi thích hợp.
- Biết khi nào việc **bắt exception lại là cách đúng**, chẳng hạn xử lý đầu vào cho `Integer.parseInt()`, thất bại I/O hay vi phạm tiền điều kiện công khai.
- Hiểu rủi ro **check-then-act** và *time-of-check to time-of-use (TOCTOU)*: kiểm tra trước không loại bỏ race condition hoặc ngoại lệ của thao tác thực tế.
- Giữ nguyên thông tin lỗi khi cần chuyển đổi ngoại lệ, tránh bắt `NullPointerException` hoặc `IndexOutOfBoundsException` chỉ để che lỗi lập trình.
- Đánh giá tác động đến hiệu năng, khả năng đọc, chẩn đoán và hợp đồng API một cách cân bằng.

Bài sử dụng Java 17+, tập trung vào Java Core Exception; ví dụ có thể biên dịch/chạy độc lập được ghi rõ tên tệp, lệnh chạy và stdout dự kiến.

## 2. Anti-pattern là gì?

Một chương trình có thể **chủ động thực hiện thao tác mà mình biết thường sẽ thất bại** rồi dựa vào exception để chọn nhánh bình thường, trong khi điều kiện quyết định đã có sẵn và có API để đọc rõ ràng.

Ví dụ: lấy phần tử đầu tiên của một danh sách, chấp nhận danh sách rỗng là trường hợp nghiệp vụ hợp lệ.

Cách viết dựa vào exception:

~~~java
String firstOrDefault(List<String> names) {
    try {
        return names.get(0);
    } catch (IndexOutOfBoundsException e) {
        return "Không có tên";
    }
}
~~~

Đây là **mã minh họa nằm trong lớp có import `java.util.List`**. Nếu danh sách rỗng, `get(0)` ném exception; code bắt nó rồi trả về giá trị mặc định.

Trong trường hợp này, danh sách rỗng là một **trạng thái dữ liệu dự kiến**, không phải sự cố. API của `List` đã cung cấp `isEmpty()`, nên điều kiện có thể biểu đạt trực tiếp:

~~~java
String firstOrDefault(List<String> names) {
    if (names.isEmpty()) {
        return "Không có tên";
    }
    return names.get(0);
}
~~~

Đây cũng là **mã minh họa**, cùng hợp đồng đầu vào như đoạn trước. Cách sau cho người đọc thấy ngay ý định: “Không có phần tử thì dùng giá trị mặc định”. Không phải đọc tên exception và phân tích đường lỗi mới hiểu được hành vi bình thường.

**Điều quan trọng:** câu “đừng dùng ngoại lệ khi chỉ cần kiểm tra điều kiện” không có nghĩa **mọi** ngoại lệ phải bị loại khỏi code. Ngoại lệ vẫn rất có giá trị để báo vi phạm hợp đồng, thất bại I/O, lỗi hạ tầng và những vấn đề người gọi cần xử lý ở tầng thích hợp.

## 3. Phân biệt ba loại tình huống

Một cách thiết kế hữu ích là hỏi: “Trạng thái này có phải một kết quả bình thường mà caller được phép gặp không?”

| Tình huống | Ý nghĩa | Hình thức thường phù hợp |
| --- | --- | --- |
| Danh sách có thể rỗng và đây là điều bình thường | Không có giá trị tại thời điểm tra cứu | `isEmpty()`, `Optional` hoặc kết quả rỗng |
| Map có thể không chứa khóa | Không tìm thấy ánh xạ | `get`, `containsKey`, `getOrDefault` theo ngữ nghĩa null |
| Input người dùng có thể sai định dạng số | Sai cú pháp theo parser được chọn | Bắt **`NumberFormatException` cụ thể** tại ranh giới parse và chuyển thành kết quả validation |
| Hàm yêu cầu số lượng dương theo hợp đồng | Caller vi phạm tiền điều kiện | Guard rồi ném `IllegalArgumentException` có ý nghĩa |
| Mở file có thể thất bại vì quyền truy cập, bị xóa hoặc lỗi thiết bị | Thao tác tài nguyên có thể thất bại độc lập việc kiểm tra trước | Gọi API thực tế và xử lý `IOException` theo hợp đồng |
| Truy cập array sai chỉ số do bug thuật toán | Lỗi lập trình | Sửa thuật toán; không tự động bắt rồi trả giá trị giả |
| Gọi API từ xa gặp timeout | Lỗi bên ngoài và có thể chưa rõ tác dụng phụ đã xảy ra | Timeout/cancellation, retry hoặc báo lỗi theo giao thức, không chỉ kiểm tra “mạng có sẵn” trước |
| Dữ liệu nghiệp vụ thiếu nhưng được phép | Một nhánh thông thường | Return type diễn đạt thiếu dữ liệu, không dùng throw/catch cho mọi trường hợp |

Cách phân loại phụ thuộc **hợp đồng API**. “Không tìm thấy” có thể là trạng thái bình thường của một hàm `find`, nhưng là vi phạm quy trình của hàm `requireExisting`. Hai hàm có thể dùng cách xử lý khác nhau mà đều đúng nếu tên, chữ ký và tài liệu của chúng nhất quán.

## 4. Vì sao try/catch không nên là if/else trá hình?

Một khối `catch` biểu đạt đường xử lý ngoại lệ. Khi biến nó thành nhánh bình thường cho một điều kiện đơn giản, có thể tạo các vấn đề:

1. **Ý định không rõ ràng.** Người đọc phải hiểu exception nào có thể phát sinh mới đoán được nhánh nghiệp vụ.
2. **Dễ che lỗi lập trình.** `catch (NullPointerException)` có thể nuốt NPE từ một lỗi logic khác trong cùng vùng `try`.
3. **Khó bảo trì.** Khi thêm một lời gọi mới vào `try`, nó có thể ném cùng kiểu exception nhưng với nguyên nhân khác.
4. **Sai tầng trách nhiệm.** Exception hạ tầng bị biến thành “không có dữ liệu”, làm caller tưởng thao tác thành công.
5. **Chi phí ở đường nóng.** Việc tạo/throw/catch exception thật sự xảy ra nhiều lần có thể gây overhead do object allocation, stack trace và điều khiển luồng.
6. **Làm méo hệ thống quan sát.** Số lượng exception rất cao có thể làm nhiễu metric và log nếu framework thu thập chúng.
7. **Che mất hợp đồng trạng thái.** Thay vì trả `Optional.empty()` hoặc `false`, code ngầm định “thao tác thất bại sẽ ném IndexOutOfBoundsException”.
8. **Khó phân loại lỗi thật.** Bắt `Exception` hoặc `RuntimeException` rộng có thể làm biến mất lỗi cần điều tra.

Nhưng **không** có quy tắc “viết if luôn nhanh hơn throw” áp dụng cho mọi trường hợp. Nếu lỗi hiếm, một API thiết kế ném ngoại lệ có thể đơn giản và đúng hợp đồng. Việc quyết định tối ưu hiệu năng cần dựa vào workload và đo đạc có kiểm soát.

## 5. Ví dụ đầu tiên: danh sách rỗng là một nhánh hợp lệ

### Cách không nên ưu tiên

~~~java
String firstName(List<String> names) {
    try {
        return names.get(0);
    } catch (IndexOutOfBoundsException ignored) {
        return "Không có tên";
    }
}
~~~

Đây là **mã minh họa không độc lập**. Nó vừa dùng exception làm điều kiện rỗng, vừa dễ tạo ấn tượng rằng tất cả lỗi chỉ số trong `try` đều có thể xem như “không có tên”.

### Cách rõ ràng hơn

~~~java
String firstName(List<String> names) {
    if (names.isEmpty()) {
        return "Không có tên";
    }
    return names.get(0);
}
~~~

Nếu API không muốn cung cấp một chuỗi mặc định có thể bị nhầm với dữ liệu thật, dùng một kiểu trả về diễn đạt “không có”:

~~~java
Optional<String> firstName(List<String> names) {
    return names.isEmpty()
            ? Optional.empty()
            : Optional.ofNullable(names.get(0));
}
~~~

Đây là **mã minh họa** dùng `java.util.Optional`. `Optional.ofNullable` trả empty nếu phần tử đầu tiên là null; điều đó phù hợp chỉ khi hợp đồng muốn coi phần tử null tương đương “không có tên”. Nếu null là một giá trị mang nghĩa khác, cần thiết kế result type chi tiết hơn.

**Không dùng Optional để che mọi vấn đề.** Nếu danh sách bắt buộc phải có phần tử, việc ném exception do vi phạm hợp đồng có thể là đúng. Khi đó nên kiểm tra điều kiện và ném một lỗi có ý nghĩa, thay vì dựa vào `get(0)` để tự phát sinh `IndexOutOfBoundsException` khó hiểu:

~~~java
String requireFirstName(List<String> names) {
    if (names.isEmpty()) {
        throw new IllegalArgumentException(
                "Danh sách tên không được rỗng");
    }
    return names.get(0);
}
~~~

Ba API `firstName`, `findFirstName` và `requireFirstName` có thể cùng tồn tại nếu chúng thực sự biểu đạt **ba hợp đồng khác nhau**. Quan trọng nhất là không biến một nhánh dự kiến thành lỗi hoặc nuốt lỗi không dự kiến.

## 6. Ví dụ Java 17 độc lập: guard, Optional và parse

Lưu tệp **GuardInsteadOfExceptionDemo.java**:

~~~java
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public class GuardInsteadOfExceptionDemo {

    static String firstUsingException(List<String> values) {
        try {
            return values.get(0);
        } catch (IndexOutOfBoundsException e) {
            return "EMPTY";
        }
    }

    static String firstUsingGuard(List<String> values) {
        return values.isEmpty() ? "EMPTY" : values.get(0);
    }

    static Optional<String> findCode(Map<String, String> codes, String key) {
        return Optional.ofNullable(codes.get(key));
    }

    static OptionalInt parseUserNumber(String input) {
        try {
            return OptionalInt.of(Integer.parseInt(input));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public static void main(String[] args) {
        List<String> empty = List.of();
        List<String> names = List.of("An", "Bình");
        Map<String, String> codes = Map.of("A", "Alpha");

        System.out.println("Exception-based empty: "
                + firstUsingException(empty));
        System.out.println("Guard empty: " + firstUsingGuard(empty));
        System.out.println("Guard nonempty: " + firstUsingGuard(names));

        System.out.println("Known code: "
                + findCode(codes, "A").orElse("MISSING"));
        System.out.println("Unknown code: "
                + findCode(codes, "B").orElse("MISSING"));

        System.out.println("Parse 42: "
                + parseUserNumber("42").orElse(-1));
        System.out.println("Parse invalid: "
                + parseUserNumber("abc").orElse(-1));
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 GuardInsteadOfExceptionDemo.java
java GuardInsteadOfExceptionDemo
~~~

**Stdout chính xác:**

~~~text
Exception-based empty: EMPTY
Guard empty: EMPTY
Guard nonempty: An
Known code: Alpha
Unknown code: MISSING
Parse 42: 42
Parse invalid: -1
~~~

### Phân tích

- Hai hàm lấy phần tử đầu tiên trả cùng kết quả ở ví dụ, nhưng `firstUsingGuard` biểu đạt nhánh rỗng bằng điều kiện có sẵn.
- `Map.get("B")` trả null vì khóa không tồn tại trong map được tạo ở đây; `Optional.ofNullable` chuyển điều đó thành kết quả empty rõ nghĩa.
- `parseUserNumber` **vẫn dùng try/catch** vì `Integer.parseInt` đã công bố `NumberFormatException` là cách báo cú pháp số không hợp lệ. Bắt chính exception này tại ranh giới parse để trả `OptionalInt.empty()` là một lựa chọn hợp lệ.
- Dùng `-1` chỉ để hiển thị trong `main`; một hệ thống có giá trị âm hợp lệ không nên dùng số âm làm sentinel lỗi nếu không có hợp đồng rõ ràng.

**Bài học:** sai lầm không nằm ở sự tồn tại của từ khóa `catch`. Sai lầm là **dùng ngoại lệ che một phép kiểm tra hiển nhiên hoặc nuốt lỗi mà hệ thống cần phân biệt**.

## 7. NullPointerException không phải tín hiệu “không có giá trị” đáng tin cậy

Mẫu nguy hiểm:

~~~java
int lengthOrZero(Map<String, String> values, String key) {
    try {
        return values.get(key).length();
    } catch (NullPointerException e) {
        return 0;
    }
}
~~~

Đây là **mã minh họa** với import `Map`. Trong tình huống khóa không tồn tại, `get(key)` trả null rồi `.length()` ném NPE. Nhưng cùng kiểu NPE có thể đến từ `values == null` hoặc một dòng khác được thêm vào khối `try`. Bắt NPE để trả 0 có thể che một lỗi thiết kế.

Thay bằng kiểm tra chính kết quả:

~~~java
int lengthOrZero(Map<String, String> values, String key) {
    String value = values.get(key);
    return value == null ? 0 : value.length();
}
~~~

Điều này xử lý đúng nếu hợp đồng đã chọn rằng **khóa không tồn tại và giá trị null đều cho độ dài 0**. Nhưng nếu cần phân biệt hai trạng thái, phải xét ngữ nghĩa của map.

### Phân biệt khóa không tồn tại và khóa tồn tại với giá trị null

Một số triển khai `Map` cho phép ánh xạ giá trị null:

~~~java
Map<String, String> values = new HashMap<>();
values.put("present-null", null);
~~~

Đây là **mã minh họa**; cần import `Map` và `HashMap`. Cả `values.get("missing")` lẫn `values.get("present-null")` đều trả null, nhưng:

- `values.containsKey("missing")` là false.
- `values.containsKey("present-null")` là true.

Tương tự, `getOrDefault(key, fallback)` chỉ dùng fallback khi **không có ánh xạ cho khóa**; nếu khóa đang ánh xạ tới null trong map cho phép null, kết quả có thể vẫn là null.

**Lưu ý concurrency:** trên map bị các luồng khác thay đổi, việc gọi `containsKey` rồi `get` không phải một thao tác nguyên tử. Không dùng phép kiểm tra này như bảo đảm rằng giá trị vẫn tồn tại ở lần đọc sau.

## 8. Bắt exception quá rộng có thể che lỗi lập trình

`catch (Exception)` có thể biến lỗi hạ tầng và cả bug thành một fallback nhìn như thành công:

~~~java
String loadName(User user) {
    try {
        return user.profile().displayName();
    } catch (Exception e) {
        return "UNKNOWN"; // Anti-pattern: mất phân biệt nguyên nhân.
    }
}
~~~

Đây là fragment cần class `User` và các phương thức của nó. Nếu không có profile là trạng thái dự kiến, API nên trả kết quả biểu đạt vắng mặt. Nếu NPE phá invariant thì phải sửa bug, không coi là “không có tên”.

**Phạm vi `try` cũng quan trọng:** bắt một `IndexOutOfBoundsException` quanh cả `validateInput()`, `findIndex()` và `items.get(index)` có thể nuốt lỗi cùng loại do phương thức khác gây ra. Thu hẹp đúng thao tác có hợp đồng dự kiến thất bại, hoặc dùng guard như `isEmpty()` khi trường hợp rỗng là bình thường. Với dữ liệu bị chia sẻ đồng thời, check-then-act còn cần giải quyết race theo contract riêng.

## 9. Guard clause có chủ đích khác với né tránh exception

**Guard clause** kiểm tra tiền điều kiện hoặc nhánh nghiệp vụ ở đầu phương thức:

~~~java
void register(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException(
                "quantity must be positive");
    }
    saveQuantity(quantity);
}
~~~

Đây là **mã minh họa** với `saveQuantity` do ứng dụng định nghĩa. Việc dùng if để **ném exception** là đúng khi `quantity <= 0` vi phạm hợp đồng của phương thức. Guard không nhất thiết luôn trả một giá trị mặc định.

Phân biệt:

- “Nếu danh sách rỗng thì không có gì để hiển thị” là **nhánh dữ liệu dự kiến**; có thể trả danh sách/Optional rỗng.
- “Đặt số lượng âm vào một đơn hàng” có thể là **input không hợp lệ**; ném `IllegalArgumentException` hoặc trả kết quả validation ở boundary.
- “Dữ liệu trong bộ nhớ bị phá invariant” là **lỗi lập trình/trạng thái**; cần ném một lỗi có ý nghĩa thay vì giả vờ thành công.

Code trở nên rõ hơn khi tên phương thức thể hiện hợp đồng: `findById` có thể trả `Optional`, còn `requireById` có thể ném lỗi “không tìm thấy” đã được công bố.

## 10. Java 17: thiết kế hai hợp đồng tìm kiếm khác nhau

Lưu tệp **LookupContractDemo.java**:

~~~java
import java.util.Map;
import java.util.Optional;

public class LookupContractDemo {

    record Item(String id, String name) {
    }

    static Optional<Item> find(
            Map<String, Item> items, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "id must not be blank");
        }
        return Optional.ofNullable(items.get(id));
    }

    static Item require(
            Map<String, Item> items, String id) {
        return find(items, id).orElseThrow(() ->
                new IllegalStateException("Item not found: " + id));
    }

    public static void main(String[] args) {
        Map<String, Item> items =
                Map.of("A", new Item("A", "Notebook"));

        System.out.println("Find A: "
                + find(items, "A").map(Item::name).orElse("MISSING"));
        System.out.println("Find B: "
                + find(items, "B").map(Item::name).orElse("MISSING"));

        try {
            require(items, "B");
        } catch (IllegalStateException e) {
            System.out.println("Require B: " + e.getMessage());
        }

        try {
            find(items, "");
        } catch (IllegalArgumentException e) {
            System.out.println("Blank ID: "
                    + e.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 LookupContractDemo.java
java LookupContractDemo
~~~

**Stdout chính xác:**

~~~text
Find A: Notebook
Find B: MISSING
Require B: Item not found: B
Blank ID: IllegalArgumentException
~~~

### Giải thích thiết kế

- `find` trả `Optional.empty()` cho một ID **hợp lệ nhưng không tồn tại**. Đây là nhánh dự kiến của API tìm kiếm.
- ID null/rỗng/trắng được xem là **vi phạm tiền điều kiện**; guard chủ động ném `IllegalArgumentException`.
- `require` biểu đạt hợp đồng khác: caller yêu cầu item phải tồn tại. Nếu không có, phương thức ném lỗi phù hợp thay vì âm thầm trả null.
- Caller không cần catch `NullPointerException` để suy ra “không có item”.
- Trong hệ thống lớn, exception của `require` có thể là một lớp domain riêng như `ItemNotFoundException`; ví dụ dùng JDK exception để không cần thêm nhiều lớp phụ.

Một API HTTP có thể ánh xạ `ItemNotFoundException` thành 404 nếu hợp đồng và quyền truy cập cho phép. Không mặc định mọi `IllegalStateException` ở mọi tầng là lỗi “not found”.

## 11. Parse dữ liệu: tại sao catch NumberFormatException có thể hợp lý?

`Integer.parseInt(String)` không cung cấp kết quả `OptionalInt` trực tiếp: nó trả một `int` hợp lệ hoặc ném `NumberFormatException` nếu cú pháp/miền giá trị không được chấp nhận.

Ở ranh giới nhập liệu, có thể chủ động chuyển cơ chế đó thành kết quả validation:

~~~java
OptionalInt parseOptionalInt(String input) {
    try {
        return OptionalInt.of(Integer.parseInt(input));
    } catch (NumberFormatException e) {
        return OptionalInt.empty();
    }
}
~~~

Đây là **mã minh họa** với import `java.util.OptionalInt`. Việc bắt exception cụ thể tại đây không giống việc gọi `list.get(0)` trên danh sách rỗng chỉ để bắt `IndexOutOfBoundsException`: API parser không cung cấp một phép kiểm tra tương đương đơn giản đảm bảo trùng chính xác mọi quy tắc parse.

**Cẩn thận với tiền kiểm tra tự chế:**

~~~java
if (input.matches("[0-9]+")) {
    return Integer.parseInt(input);
}
~~~

Đây là **mã minh họa chưa hoàn chỉnh**; nó không kiểm tra tràn số nguyên, có thể không chấp nhận dấu âm/dấu cộng mà parser chấp nhận, và có thể đưa ra quy tắc khác parser. Việc kiểm tra regex không bảo đảm `parseInt` sẽ không ném `NumberFormatException`. Nếu muốn hỗ trợ một định dạng hẹp hơn của nghiệp vụ, đó phải là **quy tắc validation có chủ đích**, không phải phỏng đoán thay thế hoàn toàn cho parser.

### Có cần tránh exception nếu tỷ lệ input sai rất cao?

Có thể đáng cân nhắc ở đường xử lý nóng, nhưng đừng vội viết lại thuật toán parse số chỉ để né exception. Những lựa chọn an toàn hơn:

- Xác định hợp đồng dữ liệu trước: định dạng số và phạm vi nào thực sự hợp lệ?
- Dùng một parser có API trả trạng thái/kết quả không ném exception nếu thư viện được chọn có hỗ trợ và phù hợp.
- Đo throughput, allocation và latency với tỷ lệ invalid input giống thực tế.
- Nếu tự viết parser, phải kiểm thử đầy đủ dấu, giới hạn `int`, Unicode, ký tự lạ và các trường hợp biên.
- Không nuốt lỗi ở tầng khác chỉ vì chuỗi input có thể sai.

**Không có quy tắc “mọi parse lỗi đều nên trả Optional.empty()”.** Nếu caller cần biết vì sao số sai (tràn, ký tự không hợp lệ, bắt buộc nhập), một kiểu `ValidationResult` chứa mã lỗi cụ thể có thể phù hợp hơn.

## 12. Khi không nên kiểm tra trước: check-then-act và TOCTOU

Đây là ngoại lệ quan trọng đối với lời khuyên “kiểm tra điều kiện trước, tránh dùng exception”.

Giả sử ứng dụng làm:

~~~java
if (Files.exists(path)) {
    return Files.readString(path);
}
return "";
~~~

Đây là **mã minh họa** với import `java.nio.file.Files`, biến `path` kiểu `Path`, và phương thức có hợp đồng checked exception phù hợp. Kiểm tra tồn tại **không bảo đảm** lời gọi `readString` sau đó thành công:

- File có thể bị xóa/đổi tên giữa hai thao tác.
- Quyền truy cập có thể thay đổi.
- Kiểm tra tồn tại có thể không kết luận được vì các hạn chế I/O.
- Đọc file có thể thất bại vì lỗi đĩa, encoding hoặc tài nguyên.
- Ngữ cảnh file có thể thay đổi giữa việc kiểm tra và sử dụng.

Vì vậy, đối với I/O, một cách thường đúng hơn là **thử thao tác thực tế rồi xử lý exception phù hợp**:

~~~java
try {
    return Files.readString(path);
} catch (NoSuchFileException e) {
    return "";
}
~~~

Đây là **mã minh họa** trong phương thức khai báo/xử lý `IOException` còn lại, với import `Files`, `NoSuchFileException` và `Path`. Nó chỉ đổi **lỗi file không tồn tại** thành kết quả rỗng nếu đó là hợp đồng của ứng dụng; các lỗi I/O khác tiếp tục được báo để tránh che sự cố.

Nếu ứng dụng cần phân biệt file không có với file tồn tại nhưng rỗng, **không dùng chuỗi rỗng làm cả hai trạng thái**. Có thể trả `Optional<String>` hoặc kiểu kết quả có cấu trúc, trong đó đã định nghĩa rõ trường hợp vắng mặt.

**Nguyên tắc:** một precheck không bảo đảm điều kiện vẫn đúng tới lúc sử dụng. Khi bản thân thao tác có thể thất bại do yếu tố bên ngoài, **exception là phần thiết yếu của hợp đồng**, không phải anti-pattern.

## 13. Concurrency: kiểm tra rồi hành động có thể tạo race

Giả sử một `ConcurrentHashMap` lưu các task. Một luồng làm:

~~~java
if (tasks.containsKey("job-1")) {
    Task task = tasks.get("job-1");
    task.run();
}
~~~

Đây là **mã minh họa**, giả định `tasks` là map concurrent và `Task` là kiểu nghiệp vụ. Dù từng phương thức `containsKey` và `get` có thread-safety riêng, thao tác **kiểm tra rồi lấy dữ liệu** chưa chắc nguyên tử: luồng khác có thể xóa khóa ở giữa. Khi ấy `task` có thể null và `task.run()` ném NPE.

Cách hợp lý nếu chỉ cần giá trị ở một thời điểm:

~~~java
Task task = tasks.get("job-1");
if (task != null) {
    task.run();
}
~~~

Đây là **mã minh họa**. Nó tránh cửa sổ giữa `containsKey` và `get`, nhưng không bảo đảm task không bị hủy hay thay đổi ở nơi khác trong khi chạy; các yêu cầu vòng đời riêng vẫn cần cơ chế phối hợp.

Nếu điều kiện và cập nhật **phải nguyên tử trên cùng key**, cân nhắc các thao tác atomic của `ConcurrentHashMap` như `computeIfAbsent`, `compute`, `putIfAbsent` hoặc `remove(key, value)` theo hợp đồng tương ứng. Đừng đặt callback blocking/chậm vào một thao tác atomic nếu nó làm tăng contention hoặc gây rủi ro reentrancy; đọc kỹ hợp đồng của từng phương thức.

### Cơ sở dữ liệu cũng có TOCTOU

Mã kiểu “kiểm tra xem email đã tồn tại chưa, nếu chưa thì chèn” không tránh được hai request cùng vượt qua kiểm tra rồi chèn trùng. Nếu cần duy nhất, cơ sở dữ liệu phải bảo vệ bằng **unique constraint**/transaction hoặc cơ chế đồng bộ phù hợp; ứng dụng xử lý lỗi xung đột theo hợp đồng.

Không có cách nào biến một điều kiện **thay đổi do tác nhân khác** thành bất biến chỉ bằng thêm một câu `if` trước thao tác.

## 14. Ví dụ Java 17 độc lập: minh họa race giữa containsKey và get

Chương trình này dùng hai luồng và `CountDownLatch` để đặt lịch có chủ đích: luồng đọc kiểm tra khóa tồn tại, sau đó luồng ghi xóa khóa **trước** khi luồng đọc gọi `get`. Vì đồng bộ hóa thứ tự đã được thiết lập, stdout không phụ thuộc vào tốc độ scheduler.

Lưu tệp **CheckThenActRaceDemo.java**:

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class CheckThenActRaceDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, String> tasks =
                new ConcurrentHashMap<>();
        tasks.put("job-1", "READY");

        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            boolean existed = tasks.containsKey("job-1");
            checked.countDown();

            try {
                removed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            String after = tasks.get("job-1");
            System.out.println("Precheck: " + existed);
            System.out.println("Read after removal: " + after);
        }, "reader");

        Thread writer = new Thread(() -> {
            try {
                checked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                // Dù có lỗi khi chờ, giải phóng latch để reader không treo.
                tasks.remove("job-1");
                removed.countDown();
            }
        }, "writer");

        reader.start();
        writer.start();

        reader.join();
        writer.join();

        System.out.println("Final containsKey: "
                + tasks.containsKey("job-1"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CheckThenActRaceDemo.java
java CheckThenActRaceDemo
~~~

**Stdout trong điều kiện không có interrupt ngoài ý muốn:**

~~~text
Precheck: true
Read after removal: null
Final containsKey: false
~~~

### Vì sao kết quả minh họa đúng race?

- Map ban đầu chứa `"job-1" -&gt; "READY"`.
- Reader gọi `containsKey` trước khi phát tín hiệu `checked.countDown()`, nên `existed` là true.
- Writer chỉ thực hiện thao tác xóa sau khi `checked.await()` hoàn tất, rồi báo `removed.countDown()`.
- Reader chỉ gọi `get` sau khi `removed.await()` hoàn tất. Vì khóa đã bị xóa, `get` trả null.
- Kết quả minh họa **một precheck có thể trở nên lỗi thời**, không phải chứng minh `ConcurrentHashMap` không thread-safe.
- `join()` bảo đảm cả hai luồng đã kết thúc trước khi main in trạng thái cuối.

Trong ví dụ, nhánh catch xử lý interrupt bằng khôi phục flag và kết thúc luồng. Đây không phải phần cốt lõi của bài, nhưng tránh việc nuốt yêu cầu hủy. Không dùng bản demo để suy rằng kiểm tra trước luôn nguy hiểm; đối với một danh sách **không bị thay đổi đồng thời**, `isEmpty()` trước `get(0)` là thiết kế bình thường.

## 15. API nào giúp biểu đạt trạng thái không cần exception?

Không có một công cụ duy nhất cho mọi tình huống. Hãy chọn theo ý nghĩa của kết quả.

| Nhu cầu | API/hình thức có thể phù hợp | Lưu ý |
| --- | --- | --- |
| Danh sách rỗng | `isEmpty()` | Nếu danh sách bị thay đổi đồng thời, cần hiểu cơ chế đồng bộ |
| Lấy phần tử đầu theo một stream hữu hạn | `stream().findFirst()` | Không bắt buộc tốt hơn `isEmpty()+get(0)` cho mọi `List` |
| Map không có ánh xạ | `get()`, `containsKey()`, `getOrDefault()` | Xác định ngữ nghĩa null và thay đổi đồng thời |
| Tìm bản ghi tùy chọn | `Optional&lt;T&gt;` | Có thể biểu đạt vắng mặt; không dùng nếu cần nhiều lý do thất bại |
| Kiểm tra tập rỗng | `Collection.isEmpty()` | Không đợi tới lúc iterator ném lỗi |
| Chuỗi null/blank | `value == null`, `String.isBlank()` | `isBlank` chỉ gọi sau khi đã biết không null |
| Chuyển đổi dữ liệu có parser ném lỗi | Bắt loại parse exception cụ thể ở boundary | Tránh tự tái tạo parser không đầy đủ |
| Kiểm tra tham số bắt buộc | Guard, `Objects.requireNonNull()` | Có thể cố ý ném exception do vi phạm hợp đồng |
| Truy cập tệp/tài nguyên | Thử thao tác và xử lý lỗi I/O phù hợp | Precheck không loại bỏ race |
| Kết quả có cả mã lỗi và dữ liệu | Record/Result type do ứng dụng định nghĩa | Tránh nhiều sentinel không phân biệt được |
| Tác vụ async | `CompletableFuture` với completion thành công/thất bại | Future lỗi cần được quan sát, không tự động đổi thành null |

### Optional là hợp đồng, không phải thuốc chữa mọi lỗi

Ví dụ một hàm `find` trả `Optional.empty()` nếu dữ liệu không tồn tại là hợp lý. Nhưng trả empty khi database offline sẽ **che sự khác biệt quan trọng** giữa “không tìm thấy” và “không thể kiểm tra”.

Nếu caller cần biết nhiều trường hợp: không tìm thấy, không được phép, timeout, hạ tầng lỗi, nên dùng result type giàu thông tin hoặc ném ngoại lệ có ý nghĩa theo API. Không gộp tất cả thành empty để code không cần catch.

## 16. Quy tắc “Look Before You Leap” và “Easier to Ask Forgiveness”

Hai phong cách thường được gọi bằng tên tiếng Anh:

- **LBYL — Look Before You Leap:** kiểm tra điều kiện rồi thực hiện thao tác.
- **EAFP — Easier to Ask Forgiveness than Permission:** thử thao tác rồi xử lý thất bại.

Trong Java, không nên áp dụng một khẩu hiệu cho mọi API.

**LBYL thường hợp lý** khi điều kiện là nhánh dữ liệu bình thường, không có race liên quan và check có ý nghĩa trực tiếp: `list.isEmpty()`, `input == null`, `quantity <= 0`.

**EAFP có thể hợp lý** khi API thao tác là nguồn sự thật, rủi ro race khiến precheck không đáng tin, hoặc API chỉ cung cấp exception cho thất bại thực tế: `Files.readString()`, network I/O, parser chuẩn hoặc constraint cơ sở dữ liệu.

**Điều quan trọng là hợp đồng**, không phải sở thích cú pháp. Không dùng exception làm công cụ rẽ nhánh thông thường khi đã có một điều kiện rõ ràng; đồng thời không dùng precheck làm “bùa hộ mệnh” để giả vờ thao tác tài nguyên không thể thất bại.

## 17. Ngoại lệ và hiệu năng: tối ưu đúng chỗ

Việc **ném exception thật sự** có thể tốn chi phí: tạo Throwable, có thể thu thập stack trace, chuyển điều khiển sang catch, và ghi log nếu ứng dụng chọn log. Nếu nó xảy ra ở đường xử lý bình thường hàng nghìn/hàng triệu lần, overhead có thể đáng kể.

Tuy nhiên:

- Chi phí phụ thuộc JVM, loại exception, độ sâu stack, tần suất, JIT và logger.
- Chỉ có khối `try` nhưng không ném lỗi **không đồng nghĩa** với trả chi phí stack trace ở mỗi lần chạy.
- Một JVM có thể tối ưu các mẫu cụ thể; không nên diễn giải rằng mọi đường exception luôn có cùng overhead.
- Đo benchmark có thể bị méo nếu logging ra console trong vòng lặp, không warm-up hoặc không dùng tải thực tế.
- Một phép guard có thể làm tăng việc đọc trạng thái/locking hoặc bị TOCTOU, nên không mặc nhiên nhanh hay đúng hơn mọi thao tác có exception.
- Với ngoại lệ **hiếm**, khả năng chẩn đoán có thể quan trọng hơn chênh lệch thời gian cực nhỏ.

Nếu nghi exception-driven control flow gây vấn đề hiệu năng:

1. Dùng profiler/metrics để xác nhận exception thật sự được ném nhiều lần.
2. Xác định trường hợp đó là trạng thái nghiệp vụ dự kiến hay lỗi hệ thống.
3. Thay API theo ngữ nghĩa thích hợp: điều kiện, Optional, kết quả có cấu trúc hoặc phép toán atomic.
4. Đo lại throughput, allocation rate, p95/p99 latency với cùng workload.
5. Xem lỗi/quan sát có bị che sau thay đổi không.
6. Nếu cần microbenchmark, có thể dùng JMH trong dự án cấu hình riêng; JMH không có sẵn chỉ bằng Java SE.

**Không thay đổi API chỉ vì một benchmark tổng hợp cho thấy câu if nhanh hơn throw.** Chất lượng hợp đồng, concurrency correctness và khả năng chẩn đoán vẫn là tiêu chí quyết định.

## 18. “Không ném exception” không đồng nghĩa xử lý lỗi đúng

Một cách đổi sai là bỏ catch và trả fallback cho mọi tình huống:

~~~java
String fetchProfile() {
    try {
        return remoteService.fetchProfile();
    } catch (RuntimeException ignored) {
        return "UNKNOWN";
    }
}
~~~

Đây là **mã minh họa không độc lập**. Dù có try/catch, vấn đề chính không chỉ là hiệu năng. Nó khiến caller nhận `UNKNOWN` và tưởng hệ thống đã xử lý thành công khi thực tế backend có thể lỗi.

Một cách sửa có chủ đích:

- Nếu không có profile là kết quả nghiệp vụ hợp lệ, trả một giá trị rỗng hoặc Optional khi **thực sự xác nhận không tồn tại**.
- Nếu hạ tầng không trả lời, tạo trạng thái lỗi riêng có thể quan sát, hoặc ném exception tầng API đã giữ cause.
- Nếu muốn dùng cache, phải có chính sách fallback rõ ràng; cân nhắc dữ liệu cũ có an toàn trong ngữ cảnh nghiệp vụ không.
- Nếu hoạt động có side effect, không khuyên retry vô điều kiện khi chưa rõ request trước có thành công không.

**Một API tốt cho caller biết ba câu chuyện khác nhau:** có dữ liệu, không có dữ liệu theo hợp đồng, hoặc không thể xác định kết quả vì lỗi.

## 19. Lỗi thường gặp khi refactor khỏi exception-driven control flow

### 19.1. Biến lỗi thật thành nhánh bình thường

Từ `catch (IOException) { return null; }` sang `if (!Files.exists(path)) return null;` không loại bỏ thất bại của `readString`. Lỗi quyền truy cập, thiết bị và race vẫn phải xử lý.

### 19.2. Kiểm tra null sau khi đã dereference

~~~java
String name = user.getName();
if (user == null) {
    return "UNKNOWN";
}
~~~

Đây là **mã minh họa cố ý sai**: nếu `user == null`, NPE xảy ra trước check. Guard phải đặt **trước** thao tác yêu cầu đối tượng khác null.

### 19.3. Trả sentinel xung đột với giá trị thật

Sử dụng `-1` hoặc chuỗi rỗng cho lỗi có thể gây lẫn với giá trị hợp lệ. Nếu miền giá trị có thể chứa các giá trị đó, dùng `Optional`, record hoặc kiểu kết quả có trạng thái riêng.

### 19.4. Dùng Optional.empty cho mọi loại lỗi

Một lỗi database, timeout hoặc permission bị biến thành “not found” có thể gây ra quyết định nghiệp vụ sai. Optional nên biểu đạt **vắng mặt**, không tự động biểu đạt mọi thất bại.

### 19.5. Bắt NullPointerException rồi tiếp tục

Khối `catch (NullPointerException)` có thể che lỗi lập trình. Nếu null hợp lệ, kiểm tra trực tiếp hoặc thiết kế kiểu dữ liệu phù hợp. Nếu null không hợp lệ, nên sửa invariant hoặc báo lỗi có ý nghĩa.

### 19.6. Bắt IndexOutOfBoundsException cho vòng lặp

~~~java
int i = 0;
try {
    while (true) {
        consume(items.get(i++));
    }
} catch (IndexOutOfBoundsException ignored) {
    // coi là hết danh sách
}
~~~

Đây là **mã minh họa cố ý không khuyến nghị**, với `items` và `consume` có sẵn. Khi vòng lặp biết kích thước, có thể dùng `for (int i=0; i<items.size(); i++)` hoặc for-each theo yêu cầu. Việc dùng exception để báo kết thúc làm khó đọc và có thể nuốt lỗi chỉ số do thao tác khác trong vùng `try`.

### 19.7. Kiểm tra map trước rồi tin kết quả mãi mãi

Trên cấu trúc dữ liệu concurrent, `containsKey` và `get` là hai thao tác. Nếu cần atomicity, phải chọn thao tác/hợp đồng thread-safety phù hợp.

### 19.8. Tự viết parser regex không tương đương API chuẩn

Regex đơn giản có thể không kiểm tra overflow, dấu âm hay phạm vi ký tự; code sau regex vẫn có thể ném `NumberFormatException`. Nên tôn trọng hợp đồng parser và chỉ kiểm tra thêm các ràng buộc riêng của nghiệp vụ.

### 19.9. Refactor làm mất cause

Nếu chuyển một lỗi I/O thành exception tầng API, dùng constructor nhận `Throwable cause`, thay vì chỉ sao chép message. Không biến “không nên dùng exception cho nhánh bình thường” thành “không nên giữ exception ở các đường lỗi thật”.

### 19.10. Thêm guard rải rác không có hợp đồng

Một số phương thức trả null, một số trả empty, một số ném `IllegalArgumentException` cho cùng một điều kiện. Hãy thống nhất tên API, document và kiểu trả về để caller không phải đoán.

### 19.11. Bắt Exception quá rộng

`catch (Exception)` có thể nuốt `InterruptedException` trong một phương thức chờ; tín hiệu hủy bị mất nếu code tiếp tục mà không có chính sách tương ứng. Luôn xem checked exception và cancel contract khi thay đổi vùng catch.

### 19.12. Đánh giá bằng performance trước khi xem correctness

Guard có thể tránh exception nhưng gây race; hoặc nó rẻ hơn một ít mà làm che lỗi. Correctness của hợp đồng là điều kiện tiên quyết trước khi tối ưu.

## 20. Checklist review code

Khi thấy một khối `try/catch` trong đường xử lý thường xuyên, hãy hỏi:

1. Điều kiện gây exception là **trường hợp nghiệp vụ dự kiến** hay **vi phạm hợp đồng/lỗi bất thường**?
2. Có API kiểm tra trực tiếp, rõ nghĩa và đủ an toàn như `isEmpty()` không?
3. Nếu dùng guard, điều kiện có thể thay đổi giữa check và thao tác không?
4. Có cần thao tác atomic, transaction hoặc xử lý thất bại của chính thao tác thay vì precheck không?
5. Khối `try` đã đủ hẹp để catch không nuốt lỗi từ các lệnh khác chưa?
6. Loại exception bắt có cụ thể không, hay đang bắt `Exception`/`RuntimeException` quá rộng?
7. Trường hợp thiếu dữ liệu có thể biểu đạt bằng `Optional` hoặc kiểu kết quả rõ nghĩa không?
8. Null, giá trị sentinel và empty có thể bị nhầm với dữ liệu hợp lệ không?
9. Đầu vào sai cú pháp do parser chuẩn báo có cần chuyển thành lỗi validation ở boundary không?
10. Khi thật sự có lỗi kỹ thuật, code có giữ cause và cung cấp thông tin chẩn đoán đủ không?
11. Thao tác có side effect có bị retry mù quáng khi catch không?
12. Nếu thay đổi code vì hiệu năng, đã đo đúng workload và kiểm tra correctness sau refactor chưa?

## 21. Bài tập tự luyện

### Bài tập 1: thay vòng lặp dùng exception

Viết lại đoạn duyệt danh sách bằng `for-each` hoặc vòng lặp theo index hợp lệ. Chứng minh một danh sách rỗng được xử lý bình thường mà không cần ném lỗi.

~~~java
int i = 0;
try {
    for (;;) {
        System.out.println(items.get(i++));
    }
} catch (IndexOutOfBoundsException e) {
    // Hết phần tử.
}
~~~

Đây là **mã minh họa cố ý cần cải tiến**, với `items` đã có trong ngữ cảnh.

### Bài tập 2: phân biệt “không tìm thấy” và “hệ thống lỗi”

Thiết kế một API lấy tài liệu: tài liệu không tồn tại được phép trả empty, còn lỗi đọc dữ liệu không được bị biến thành empty. Viết các kiểm thử minh họa cả hai nhánh.

### Bài tập 3: check-then-act trên Map

Giải thích vì sao `containsKey` rồi `get` không đảm bảo giá trị còn tồn tại khi map có nhiều writer. Nếu yêu cầu là “đăng ký task nếu chưa có”, hãy tìm hiểu `putIfAbsent` và cách tránh tạo hai task trùng.

### Bài tập 4: parser và kết quả validation

Thiết kế một hàm đọc số tuổi từ chuỗi. Cần phân biệt không nhập, sai định dạng, vượt phạm vi 0–150 và hợp lệ. Xem một `OptionalInt` có đủ biểu diễn tất cả yêu cầu không; nếu không, hãy dùng một record hoặc sealed result type chứa mã trạng thái.

## 22. Tóm tắt

- **Đừng dùng exception như một if/else trá hình** khi điều kiện đơn giản đã mô tả chính xác nhánh dữ liệu bình thường.
- Danh sách rỗng, khóa map không tồn tại hoặc một tra cứu tùy chọn thường có cách biểu đạt rõ hơn: `isEmpty()`, `get`, `Optional` hoặc result type.
- **Exception vẫn có chỗ đứng:** vi phạm hợp đồng, parser chuẩn báo lỗi, thất bại I/O, lỗi hạ tầng và các tình huống không thể bảo đảm bằng precheck.
- **Cẩn thận TOCTOU:** với file, map concurrent và database, kiểm tra trước không phải bảo đảm trạng thái còn đúng ở lúc sử dụng.
- Bắt exception ở **phạm vi hẹp nhất và đúng loại**, giữ nguyên cause khi chuyển lỗi và không dùng fallback che lỗi thật.
- Tối ưu hiệu năng chỉ sau khi hiểu hợp đồng và đo được tác động trên workload thực tế.

**Kết luận:** Việc thay exception-driven control flow bằng điều kiện đơn giản giúp code rõ ý và có thể giảm overhead khi lỗi bị ném thường xuyên. Nhưng điều kiện không thể thay thế cơ chế xử lý exception của mọi thao tác; lựa chọn đúng phải dựa trên **bản chất trạng thái, hợp đồng API và tính nguyên tử của thao tác**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-008">Trả null hoặc giá trị mặc định sau lỗi mà hợp đồng không nói rõ</a>

<details>
<summary>Xem chi tiết</summary>

# Trả null hoặc giá trị mặc định sau lỗi mà hợp đồng không nói rõ

## 1. Vấn đề: một giá trị “bình thường” có thể che một thất bại

Trong Java, khi một đoạn code bắt exception rồi trả `null`, `false`, `0`, chuỗi rỗng, danh sách rỗng, `Optional.empty()` hoặc một object mặc định, người gọi chỉ nhìn thấy **giá trị trả về**, không nhất thiết biết rằng việc thực thi đã thất bại.

Ví dụ:

~~~java
String loadUserName(String id) {
    try {
        return repository.findName(id);
    } catch (RuntimeException ignored) {
        return null;
    }
}
~~~

Đây là **fragment minh họa không độc lập**; `repository` chưa được định nghĩa. Nếu `findName` vốn trả `null` khi không có người dùng, thì cả hai tình huống sau trở nên không phân biệt được:

1. Truy vấn thành công, nhưng người dùng thật sự không tồn tại.
2. Truy vấn không thực hiện được vì database mất kết nối, sai cấu hình hoặc code ném exception.

Một caller có thể viết `if (name == null) return 404;`, khiến lỗi hạ tầng bị trình bày thành “người dùng không tồn tại”. Hậu quả có thể là trả thông báo sai, kích hoạt tạo bản ghi trùng, cache một kết quả âm giả hoặc bỏ qua một lỗi cần retry.

**Sai lầm cốt lõi không chỉ là việc trả `null`.** Vấn đề là **API không định nghĩa rõ khi nào giá trị đó hợp lệ, có nghĩa gì và liệu exception đã xảy ra hay không**. Một giá trị mặc định chỉ an toàn khi hợp đồng nêu điều kiện sử dụng và caller có thể phân biệt hoặc chấp nhận hệ quả của fallback.

Mục tiêu bài học:
- Phân biệt **thành công có dữ liệu**, **thành công nhưng không có dữ liệu**, **thất bại**, **hủy/quá hạn** và **fallback có chủ đích**.
- Biết vì sao `null`, `Optional.empty()`, `0`, `false`, `""` và collection rỗng không phải thông báo lỗi tổng quát.
- Chọn khi nào nên **truyền exception**, khi nào trả **result có kiểu**, và khi nào fallback là đúng hợp đồng.
- Giữ `cause`, mã lỗi và khả năng quan sát, không biến một sự cố nghiêm trọng thành “success”.
- Kiểm thử đường thất bại bằng fault injection, kể cả side effect đã xảy ra trước exception.

## 2. Phân loại kết cục trước khi thiết kế kiểu trả về

Một thao tác bất kỳ có thể rơi vào những nhóm sau:

| Kết cục | Ví dụ | Ý nghĩa cần truyền |
| --- | --- | --- |
| Thành công có kết quả | Tìm thấy khách hàng `A` | `Found(customer)` |
| Thành công không có kết quả | ID hợp lệ, truy vấn xong và không có bản ghi | `NotFound(id)` hoặc `Optional.empty()` theo contract |
| Từ chối nghiệp vụ dự kiến | Đơn hàng vi phạm hạn mức | `Rejected(reason)` hoặc exception nghiệp vụ đã định nghĩa |
| Lỗi thực thi | Database timeout, đọc file lỗi, bug trong callback | Exception hoặc `Failed(code, cause)` |
| Quá hạn chờ | Caller không nhận kết quả trước deadline | `TimeoutException`/`PENDING`/`UNKNOWN` theo phạm vi |
| Hủy | Tác vụ bị hủy theo yêu cầu | `Cancelled` hoặc `CancellationException` theo API |
| Fallback có kiểm soát | Dùng bản cache cũ khi nguồn chính lỗi, được policy cho phép | `Fallback(value, source, age)` hoặc metadata tương đương |

Một API không nhất thiết cần bảy class cho bảy trạng thái. Nhưng **phải trả lời được câu hỏi mỗi giá trị có ý nghĩa gì**, và trạng thái nào được phân biệt với trạng thái nào.

Ví dụ `Optional<User> findById(String id) throws IOException` là một hợp đồng tương đối rõ: nếu truy vấn thành công, trả `Optional.of(user)` hoặc `Optional.empty()`; nếu nguồn dữ liệu không đọc được, ném `IOException`. Trái lại, `Optional.empty()` cho cả “không thấy” và “không kết nối DB” làm `Optional` mất ý nghĩa.

### 2.1. Không có giá trị khác với không biết giá trị

Có một khác biệt rất lớn:

- **Không có:** hệ thống đã kiểm tra thành công và xác nhận không có bản ghi.
- **Không biết:** hệ thống chưa kiểm tra được do lỗi, timeout hoặc dữ liệu chưa đồng bộ.

`null`/`Optional.empty()` thường chỉ phù hợp với trường hợp thứ nhất khi hợp đồng xác định như vậy. Trường hợp thứ hai cần trạng thái lỗi/unknown hoặc cơ chế retry/đối soát. Nếu không phân biệt, caller có thể quyết định sai dù không có exception nào xuất hiện trong log.

## 3. Vì sao catch rồi trả `null` đặc biệt khó chẩn đoán?

~~~java
User getUser(String id) {
    try {
        return database.load(id);
    } catch (Exception e) {
        return null;
    }
}
~~~

Đây là fragment, `User`/`database` chưa định nghĩa. Việc catch rộng rồi trả `null` có ít nhất bốn nguy cơ:

1. **Nuốt nguyên nhân:** `IOException`, `SQLException`, `IllegalStateException` và lỗi lập trình khác đều bị ép thành một trạng thái `null`.
2. **Tạo exception thứ cấp:** code phía trên truy cập `user.getName()` và nhận `NullPointerException` ở nơi xa lỗi ban đầu; trace chỉ tới việc dereference `null`, không còn cause gốc.
3. **Giả thành công:** phương thức kết thúc bình thường và metric tự động có thể đánh dấu success dù dependency đã thất bại.
4. **Làm sai quy trình nghiệp vụ:** caller coi null là “chưa có dữ liệu” rồi tạo mới/ghi cache/ra quyết định không chính xác.

Việc đổi thành `catch (IOException e) { return null; }` có giảm độ rộng catch nhưng **vẫn làm mất trạng thái lỗi I/O** nếu hợp đồng không nêu rõ null cũng đại diện cho lỗi. Một comment như `// ignore` không phải điều kiện bảo đảm an toàn.

### 3.1. `null` có thể hợp lệ khi nào?

`null` có thể là giá trị được định nghĩa hợp pháp trong một số API legacy, framework binding hoặc giao thức dữ liệu. Khi đó tài liệu phải nêu rõ:
- Điều kiện nào làm hàm trả `null`?
- `null` là dữ liệu hợp lệ, không tìm thấy, chưa tải hay lỗi?
- Nếu operation thật sự thất bại, caller nhận exception hay trạng thái nào?
- Những lệnh có side effect trước khi trả null có thể đã được thực hiện chưa?
- Có annotation/nullability tooling và test xác minh contract không?

Một API công khai `@Nullable User find(String id)` có thể nêu null = không tồn tại; vậy lỗi database phải đi qua kênh khác, không được cũng trả null. Nullability annotation giúp diễn đạt **giá trị có thể null**, nhưng tự nó không nói hết ý nghĩa nghiệp vụ của null.

## 4. `false`, `0`, `""` và collection rỗng cũng có thể che lỗi

`null` chỉ là một trong các cách biến exception thành dữ liệu bình thường.

| Giá trị mặc định | Tình huống hợp lệ | Sự cố nếu cũng dùng nó khi lỗi |
| --- | --- | --- |
| `false` | Kiểm tra điều kiện và điều kiện không thỏa | Không phân biệt “không được phép” với “service kiểm tra quyền đã lỗi” |
| `0` | Tổng số bản ghi bằng 0, giá tiền hợp lệ bằng 0 | Lỗi tính tiền/đọc số liệu bị hiểu là giá trị 0 thật |
| `""` | Chuỗi nhập trống nhưng hợp lệ | Mất khả năng phân biệt lỗi tải dữ liệu |
| `List.of()` | Truy vấn thành công, danh sách không có phần tử | API đưa ra “không có dữ liệu” khi database không thể truy vấn |
| `Map.of()` | Cấu hình hợp lệ nhưng không có override | Lỗi đọc cấu hình làm ứng dụng bỏ qua chính sách quan trọng |
| `Optional.empty()` | Tìm kiếm hợp lệ không thấy giá trị | Lỗi thực thi bị giả thành absence |
| Object mặc định | Người dùng cố ý chọn thông số mặc định | Data corruption/deserialize error bị che mất |

Phản ví dụ:

~~~java
int loadAccountBalance(String accountId) {
    try {
        return backend.balance(accountId);
    } catch (RuntimeException ignored) {
        return 0;
    }
}
~~~

Đây là fragment minh họa; `backend` chưa định nghĩa. Trả `0` sau lỗi hạ tầng có thể dẫn tới từ chối sai giao dịch, phát thông báo “hết tiền”, hoặc báo cáo tài chính sai. Nếu domain cho phép tài khoản có số dư đúng bằng 0, giá trị này không còn là một sentinel duy nhất.

Với boolean, `false` đôi khi được chọn theo chính sách “deny by default” khi không thể kiểm tra quyền. Đó có thể là **quyết định bảo mật fail-closed đúng**, nhưng contract nên nêu rằng `false` bao gồm trường hợp không xác minh được, hoặc trả trạng thái `DENIED` và `UNAVAILABLE` riêng để có thể chẩn đoán. Không được coi fail-closed là lý do xóa log/metric sự cố authorization service.

## 5. Ví dụ Java 17 hoàn chỉnh: cùng null cho hai tình huống và cách sửa

Chương trình sau có ba tầng biểu diễn:

- `legacy` mô phỏng API cũ: trả `null` cho cả “không thấy” và lỗi I/O.
- `findOptional` trả `Optional.empty()` chỉ khi truy vấn **thật sự hoàn thành** mà không thấy dữ liệu, còn `IOException` được truyền ra.
- `lookup` trả một **sealed result type** gồm `Found`, `Missing` và `Failed`, phân biệt rõ các kết cục.
- `lookupWithCache` minh họa fallback có chủ đích và được dán nhãn riêng `Fallback`; giá trị cache ở đây là giả lập để kiểm chứng phân loại, không phải cache production.

Lưu thành file **`ExplicitFailureResultDemo.java`**:

~~~java
import java.io.IOException;
import java.util.Optional;

public class ExplicitFailureResultDemo {
    sealed interface LookupResult permits Found, Missing, Failed, Fallback {}

    record Found(String key, String value) implements LookupResult {}
    record Missing(String key) implements LookupResult {}
    record Failed(String key, FailureCode code, Throwable cause)
            implements LookupResult {}
    record Fallback(String key, String value, String source)
            implements LookupResult {}

    enum FailureCode { STORAGE_UNAVAILABLE }

    static String fetch(String key) throws IOException {
        return switch (key) {
            case "A" -> "Alice";
            case "B" -> null;
            case "DB_DOWN" -> throw new IOException("database unavailable");
            default -> null;
        };
    }

    static String legacy(String key) {
        try {
            return fetch(key);
        } catch (IOException ignored) {
            return null;
        }
    }

    static Optional<String> findOptional(String key) throws IOException {
        return Optional.ofNullable(fetch(key));
    }

    static LookupResult lookup(String key) {
        try {
            String value = fetch(key);
            return value == null ? new Missing(key) : new Found(key, value);
        } catch (IOException e) {
            return new Failed(key, FailureCode.STORAGE_UNAVAILABLE, e);
        }
    }

    static LookupResult lookupWithCache(String key) {
        LookupResult result = lookup(key);
        if (result instanceof Failed && key.equals("DB_DOWN")) {
            return new Fallback(key, "stale Alice", "cache");
        }
        return result;
    }

    static String describe(LookupResult result) {
        if (result instanceof Found found) {
            return "FOUND " + found.key() + "=" + found.value();
        }
        if (result instanceof Missing missing) {
            return "MISSING " + missing.key();
        }
        if (result instanceof Failed failed) {
            return "FAILED " + failed.key() + "=" + failed.code()
                    + " (" + failed.cause().getMessage() + ")";
        }
        if (result instanceof Fallback fallback) {
            return "FALLBACK " + fallback.key() + "=" + fallback.value()
                    + " from " + fallback.source();
        }
        throw new IllegalStateException("Unknown result");
    }

    public static void main(String[] args) {
        System.out.println("legacy missing is null: " + (legacy("B") == null));
        System.out.println("legacy outage is null: " + (legacy("DB_DOWN") == null));
        System.out.println(describe(lookup("A")));
        System.out.println(describe(lookup("B")));
        System.out.println(describe(lookup("DB_DOWN")));
        System.out.println(describe(lookupWithCache("DB_DOWN")));
        try {
            System.out.println("Optional missing empty: " + findOptional("B").isEmpty());
            findOptional("DB_DOWN");
        } catch (IOException e) {
            System.out.println("Optional outage: " + e.getMessage());
        }
    }
}
~~~

Dùng JDK 17 trở lên, đứng trong thư mục chứa file:

~~~bash
javac --release 17 ExplicitFailureResultDemo.java
java ExplicitFailureResultDemo
~~~

Stdout **đã được biên dịch/chạy kiểm chứng**:

~~~text
legacy missing is null: true
legacy outage is null: true
FOUND A=Alice
MISSING B
FAILED DB_DOWN=STORAGE_UNAVAILABLE (database unavailable)
FALLBACK DB_DOWN=stale Alice from cache
Optional missing empty: true
Optional outage: database unavailable
~~~

### 5.1. Giải thích output

- Hai dòng đầu đều `true`: caller nhận cùng `null` trong API legacy, không thể phân biệt vắng mặt với mất kết nối database.
- `FOUND A=Alice`: dữ liệu tồn tại và thao tác thành công.
- `MISSING B`: truy vấn được thực hiện hợp lệ, không có bản ghi.
- `FAILED DB_DOWN=STORAGE_UNAVAILABLE (...)`: lỗi I/O được đưa vào kết quả có kiểu `Failed`, kèm mã ổn định và `cause`.
- `FALLBACK ... from cache`: nhánh fallback là một kết quả **có nhãn**. Caller biết nó không phải dữ liệu tươi từ nguồn chính.
- `Optional missing empty: true`: `Optional.empty()` chỉ mô tả không có giá trị, không bị dùng làm mã lỗi.
- `Optional outage: database unavailable`: lỗi nguồn vẫn đi qua đường `IOException`; caller có thể xử lý/ghi nhận đúng.

Ví dụ đơn giản hóa bằng chuỗi `stale Alice` khi khóa `DB_DOWN` lỗi. Ứng dụng thật phải kiểm tra cache key, phiên bản, thời gian tạo, quyền truy cập và giới hạn staleness trước khi trả fallback. **Không lấy một dữ liệu cache tùy ý cho sai khách hàng** chỉ vì muốn cung cấp giá trị mặc định.

### 5.2. Tại sao dùng sealed interface và record?

Java 17 hỗ trợ `sealed interface` và `record` ổn định, cho phép mô tả những trường hợp kết quả được phép tồn tại:
- `Found` chứa dữ liệu trả về.
- `Missing` chứa khóa không có.
- `Failed` chứa mã lỗi và cause nội bộ.
- `Fallback` chứa giá trị cùng nguồn dự phòng.

Dùng `instanceof` pattern matching trong `describe` giúp phân biệt nhánh rõ ràng mà không cần sentinel `null`. Nếu thêm một loại kết quả khác, cần cập nhật logic xử lý tương ứng. Trong production, có thể lựa chọn sealed hierarchy với các record phong phú hơn, hoặc exception + `Optional` nếu nhu cầu thực sự đơn giản.

**Lưu ý bảo mật:** `Throwable cause` trong record chỉ phù hợp lưu/ghi nhận nội bộ. Không tuần tự hóa stack trace, thông tin kết nối hay dữ liệu nhạy cảm thẳng ra HTTP response hoặc message công khai. Chỉ xuất error code và thông tin an toàn theo hợp đồng API.

## 6. `Optional` không phải một container lỗi tổng quát

`Optional<T>` trong Java được thiết kế để biểu diễn **có hoặc không có một giá trị**. Nó không chứa reason hay cause.

Mẫu đúng nếu `Optional.empty()` = “không tìm thấy”:

~~~java
Optional<String> readName(String id) throws IOException {
    String name = readFromStorage(id);
    return Optional.ofNullable(name);
}
~~~

Đây là fragment, `readFromStorage` chưa khai báo. Nếu storage ném `IOException`, exception truyền ra; không bị tự đổi thành `Optional.empty()`. Với caller, điều đó cho phép:
- `Optional.of(value)`: có dữ liệu.
- `Optional.empty()`: truy vấn hoàn tất nhưng không có dữ liệu.
- `IOException`: không thể hoàn thành truy vấn.

Mẫu sai:

~~~java
Optional<String> readName(String id) {
    try {
        return Optional.ofNullable(readFromStorage(id));
    } catch (IOException ignored) {
        return Optional.empty();
    }
}
~~~

Ở đây `Optional.empty()` vừa chỉ absence vừa che lỗi I/O. Nếu API contract ban đầu không nêu điều này, đây là **thay đổi nghĩa** dữ liệu nguy hiểm.

### 6.1. `orElse` và `orElseGet` không tự phục hồi lỗi ở tầng dưới

Ví dụ:

~~~java
String label = optionalName.orElse("Anonymous");
~~~

Đây là fragment với `optionalName` đã có. `"Anonymous"` được dùng khi Optional rỗng, không phải “bắt mọi exception” từ quá trình tạo `optionalName`. Nếu người viết đã chuyển `IOException` thành Optional rỗng ở tầng dưới, `orElse` sẽ âm thầm tạo một “tên hợp lệ” giả.

Ngoài ra, `orElse(expression)` đánh giá argument theo quy tắc gọi phương thức Java, còn `orElseGet(supplier)` chỉ gọi supplier khi thiếu giá trị. Khác biệt này quan trọng nếu fallback tốn kém hoặc có side effect; nhưng **dùng lazy supplier không sửa được hợp đồng lỗi mơ hồ**.

### 6.2. `Optional.ofNullable` chỉ xem xét null, không biết nguồn null

`Optional.ofNullable(value)` tạo Optional rỗng khi `value == null`. Nó không nhận metadata nói null do “không tồn tại”, “deserialize lỗi”, “dependency không sẵn sàng” hay “lập trình viên đã catch exception”. Nếu hệ thống cần phân biệt nguyên nhân, phải thiết kế thông tin đó **trước bước chuyển sang Optional**.

## 7. `Map.get` và những API có giá trị mặc định riêng

`Map.get(key)` có thể trả `null` trong hai trường hợp:
- Không có mapping cho key.
- Có mapping nhưng value được lưu là `null` (với Map implementation cho phép null).

Với một Map cho phép null, muốn phân biệt có key hay không, dùng `containsKey(key)`:

~~~java
Map<String, String> settings = new HashMap<>();
settings.put("theme", null);
System.out.println(settings.containsKey("theme")); // true
System.out.println(settings.get("theme") == null); // true
~~~

Đây là fragment; cần import `java.util.Map` và `java.util.HashMap`. Đây là **mơ hồ thuộc hợp đồng dữ liệu của Map**, không phải exception. Khi một adapter cache hoặc repository trả null sau exception, độ mơ hồ còn tăng thêm: không chỉ “key không có hay value null”, mà cả “đã truy vấn thành công hay chưa”.

`Map.getOrDefault(key, defaultValue)` trả fallback khi không có mapping; nếu key tồn tại và được map tới null, behavior có thể giữ null. Không dùng `getOrDefault` như cơ chế “thử truy vấn thất bại thì tự khôi phục”; nó chỉ xử lý absence theo semantics của Map.

**Quy tắc:** hiểu sentinel mặc định của collection/API đã có trước khi tận dụng nó làm kênh lỗi mới.

## 8. Khi nào exception là hợp đồng phù hợp?

Không phải lỗi nào cũng cần chuyển thành `Failed` record. Với nhiều API Java, phương thức có thể:
- trả giá trị hợp lệ hoặc absence theo miền nghiệp vụ;
- **ném exception** nếu không thể hoàn thành thao tác.

Ví dụ:

~~~java
Optional<Invoice> findInvoice(String id) throws IOException {
    return invoiceStore.find(id);
}
~~~

Đây là fragment với `Invoice`, `invoiceStore` giả định. Nếu thao tác đọc lỗi, truyền `IOException` giữ kênh lỗi tách biệt. Nếu tầng trên muốn chuyển nó thành HTTP 503 hoặc kết quả `StorageUnavailable`, hãy làm ở ranh giới có khả năng quyết định chính sách.

Nếu exception là kết cục nghiệp vụ dự kiến (ví dụ ID không hợp lệ), có thể dùng checked exception, unchecked domain exception hoặc result type tùy convention hệ thống. Điều quan trọng là **caller có thể nhận biết và test những kết cục đó**.

### 8.1. Rethrow và wrap giữ nguyên cause

Khi không thể xử lý:

~~~java
try {
    return remoteRead();
} catch (IOException e) {
    throw e;
}
~~~

Fragment này giả định phương thức cho phép `throws IOException`. Nếu không thêm xử lý/ngữ cảnh, có thể bỏ try-catch để exception tự lan.

Nếu muốn bọc theo tầng dịch vụ:

~~~java
try {
    return remoteRead();
} catch (IOException e) {
    throw new IllegalStateException("Unable to read report", e);
}
~~~

Đây là fragment, `remoteRead` chưa định nghĩa. Dùng constructor chứa `cause` giúp caller/logging thấy nguyên nhân gốc. Tránh:

~~~java
catch (IOException e) {
    return null;
}
~~~

khi null không phải một nhánh lỗi được định nghĩa. Cũng tránh `throw new IllegalStateException("failed")` không cause vì mất đường trace từ lỗi I/O ban đầu.

### 8.2. Không catch rộng để biến mọi lỗi thành default

~~~java
try {
    return execute();
} catch (Exception e) {
    return defaultResult();
}
~~~

Đây là fragment. `Exception` có thể bao gồm IOException được dự kiến lẫn `NullPointerException`/`IllegalStateException` do bug. Khi dependency thêm một lỗi mới, đoạn catch rộng cũng biến nó thành fallback, làm lỗi lập trình bị che.

`catch (Throwable)` còn bắt các `Error` của JVM, thường không phù hợp để trả giá trị thành công. Ở ranh giới rất đặc biệt có thể bắt rộng để giám sát và shutdown có kiểm soát, nhưng vẫn phải **bảo toàn tín hiệu thất bại**, không mặc định trả success.

## 9. Thiết kế fallback đúng: giá trị, nguồn và độ tin cậy

Fallback là một **quyết định nghiệp vụ**, không phải thói quen của catch. Ví dụ một dashboard đọc dữ liệu thống kê có thể cho phép dùng cache 5 phút khi hệ thống nguồn tạm thời không sẵn sàng. Nhưng API cần mô tả:
- Fallback được phép cho loại lỗi nào?
- Có giá trị cache hợp lệ cho **đúng key/tenant** không?
- Cache cũ tối đa bao lâu? Có trường hợp không được dùng dữ liệu cũ (giá, tồn kho, quyền hạn, giao dịch)?
- Client có cần biết dữ liệu **degraded/stale** không?
- Có metric/log để vận hành biết nguồn chính thất bại không?
- Khi fallback cũng thất bại, caller thấy kết cục gì?
- Có side effect nào trước khi dùng fallback không?

Vì vậy, kiểu:

~~~java
record CachedValue<T>(
        T value,
        String source,
        java.time.Instant fetchedAt,
        boolean stale) {}
~~~

là một **fragment Java 17** có thể mang metadata. Với API trả `CachedValue<T>`, caller không phải suy đoán fallback từ một chuỗi rỗng hoặc null.

### 9.1. Ví dụ cấu hình: “thiếu” khác “sai”

Một cấu hình như `APP_PORT` có thể được định nghĩa:
- Biến môi trường **không có** → dùng giá trị mặc định 8080.
- Có nhưng chứa `"abc"` → cấu hình sai; báo lỗi startup.
- Có `"-1"` → giá trị ngoài miền; báo lỗi.
- Có `"0"` → có thể mang ý nghĩa đặc biệt (ví dụ yêu cầu server chọn ephemeral port) nếu contract cho phép; không nên tự đổi thành 8080.

Anti-pattern:

~~~java
try {
    return Integer.parseInt(rawPort);
} catch (NumberFormatException e) {
    return 8080;
}
~~~

Đây là fragment với `rawPort` giả định. Nó coi **giá trị cấu hình sai** như “không cấu hình”, khiến triển khai chạy sai cổng một cách âm thầm. Có thể chọn default khi *biến thật sự không tồn tại*, rồi kiểm tra giá trị hiện diện theo contract.

### 9.2. Fail-open và fail-closed phải được thể hiện rõ

Với quyết định bảo mật, một API trả `false` sau lỗi đôi khi thực thi chính sách **fail-closed** (từ chối khi không thể xác minh). Đây có thể là quyết định đúng. Nhưng nếu caller xem `false` là “người dùng chắc chắn không có quyền” thay vì “không thể xác minh quyền”, báo cáo và vận hành sẽ sai.

Có thể tách:
- `ALLOWED`;
- `DENIED`;
- `UNAVAILABLE`/`INDETERMINATE`.

Sau đó tầng API áp chính sách fail-closed cho `UNAVAILABLE` nhưng vẫn ghi nhận dependency lỗi. Không chuyển lỗi kiểm tra quyền thành `true` để “dịch vụ không bị gián đoạn”.

## 10. `CompletableFuture.exceptionally` có thể biến lỗi thành “success giả”

Một đoạn async phổ biến:

~~~java
CompletableFuture<String> stage =
        CompletableFuture.supplyAsync(() -> loadRemote())
                .exceptionally(error -> "");
~~~

Đây là **fragment**, `loadRemote()` chưa khai báo. Nếu supplier ném exception, stage mới hoàn thành **bình thường với chuỗi rỗng**. `whenComplete((value, error) -> ...)` đặt phía sau sẽ thấy `error == null`; metric success ở đó có thể tăng dù nguồn đã lỗi.

Với dữ liệu đọc có thể thực sự rỗng, `""` che toàn bộ cause. Một thiết kế rõ ràng hơn là:
- Để exceptional completion truyền tới caller, và xử lý nguyên nhân tại ranh giới phù hợp; hoặc
- Dùng `handle` chuyển cả success/failure thành `Outcome` có kiểu; hoặc
- Dùng `exceptionally` với fallback được **hợp đồng cho phép**, trả kèm metadata bằng kiểu dữ liệu của stage.

### 10.1. Sự khác nhau giữa `handle`, `exceptionally` và `whenComplete`

| API | Tác dụng chính | Bẫy cần tránh |
| --- | --- | --- |
| `exceptionally(fn)` | Tạo giá trị mới khi stage thất bại | Fallback không có nhãn khiến lỗi biến thành success giả |
| `handle((value,error) -> ...)` | Chuyển success/failure thành kết quả mới | Nếu trả object mặc định ở nhánh lỗi mà không mã trạng thái, vẫn mơ hồ |
| `whenComplete((value,error) -> ...)` | Quan sát completion, thường không thay đổi kết quả | Nó không tự “xử lý hết” exception; observer ném lỗi có thể làm stage mới lỗi |
| `thenApply(fn)` | Chuyển đổi giá trị khi upstream thành công | Không được gọi trên nhánh lỗi upstream |
| `get()` / `join()` | Quan sát kết quả và exceptional completion | Nếu task đã tự nuốt lỗi trước đó, không thể biết cause chỉ bằng Future |

Ví dụ typed outcome (fragment, `Outcome`/`Success`/`Failure` giả định có định nghĩa):

~~~java
CompletableFuture<Outcome> observed = source.handle((value, error) ->
        error == null
                ? new Success(value)
                : new Failure("REMOTE_ERROR", error));
~~~

Nếu một stage thành công với `null`, điều kiện `error == null` vẫn biểu thị completion bình thường. Không dùng `value != null` để kết luận success. Với lỗi bọc `CompletionException`, giữ nguyên cause chain hoặc tháo wrapper có kiểm soát; không giả định `error.getCause()` luôn khác null.

### 10.2. Future và executor cũng chịu cùng vấn đề

~~~java
Future<String> task = executor.submit(() -> {
    try {
        return loadRemote();
    } catch (IOException e) {
        return null;
    }
});
~~~

Đây là fragment với `executor` và `loadRemote` giả định. Vì `Callable` đã bắt exception và trả `null` bình thường, `task.get()` cũng có thể trả null, **không ném `ExecutionException`**. Executor không thể tự tái tạo lỗi mà task đã nuốt.

Đừng xem “Future completed normally” đồng nghĩa công việc nghiệp vụ thành công; nếu kiểu T cho phép `Failure`, caller phải kiểm tra trạng thái nghiệp vụ trong T.

## 11. Timeout và cancellation: không phải một giá trị trống bình thường

Nếu `future.get(timeout, unit)` ném `TimeoutException`, điều đó chỉ nói caller **chưa nhận được kết quả trong thời gian chờ**; task có thể vẫn chạy và tạo side effect. Trả `null`/`false`/`List.of()` cho timeout mà không nêu ý nghĩa làm caller dễ hiểu nhầm “không có dữ liệu”.

Ví dụ sai:

~~~java
try {
    return future.get(1, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    return null;
}
~~~

Đây là fragment cần những import/checked exception khác và một phương thức có kiểu trả phù hợp. Chỉ có một “không có kết quả kịp thời” không chứng minh request đã thất bại, bị hủy hoặc chưa xảy ra.

Tùy hệ thống:
- Có thể trả `PENDING(jobId)` và cho phép tra cứu kết quả.
- Có thể trả `UNKNOWN(operationId)` nếu remote side effect chưa thể xác nhận.
- Có thể ném/propagate timeout qua hợp đồng exception.
- Có thể hủy task do caller sở hữu, nếu task hỗ trợ cancellation và nghiệp vụ cho phép.

Cũng không được nuốt `InterruptedException` của thread đang đợi rồi trả giá trị mặc định “thành công”; thường cần truyền tiếp hoặc restore interrupt status và chấm dứt xử lý. `CancellationException` phải được phân loại theo hợp đồng, không biến tất cả cancellation thành dữ liệu rỗng hợp lệ.

## 12. HTTP và JSON: null/empty có thể làm sai giao thức

Khi API server bắt lỗi storage rồi trả response body có field `"user": null` và HTTP 200, client khó phân biệt:
- Người dùng thật sự không tồn tại.
- Người dùng tồn tại nhưng dữ liệu rỗng có chủ đích.
- Backend đọc dữ liệu thất bại.
- Request bị timeout hoặc token thiếu quyền.

Một hợp đồng API rõ hơn có thể chọn:
- HTTP **200** cho dữ liệu tìm thấy/thao tác thành công.
- HTTP **404** cho tài nguyên không tồn tại **sau kiểm tra hợp lệ**.
- HTTP **503** khi backend tạm thời không sẵn sàng.
- HTTP **500** cho lỗi nội bộ bất thường.
- HTTP **202** và job ID cho công việc được nhận nhưng chưa hoàn tất.

Các status là lựa chọn thông thường cần phù hợp semantics của API; không phải quy tắc ép mọi trường hợp. Với endpoint tìm kiếm danh sách, danh sách rỗng có thể là một **200 hợp lệ**; lỗi database không được giả thành danh sách rỗng chỉ để luôn trả HTTP 200.

### 12.1. JSON `null` có thể mang nhiều nghĩa

Trong JSON, cần phân biệt:
- Field **vắng mặt**.
- Field tồn tại và có giá trị `null`.
- Field có chuỗi rỗng `""`.
- Field có mảng `[]` hoặc object `{}`.
- Field có giá trị hợp lệ nhưng mặc định.

Không tự suy rằng serializer sẽ ánh xạ tất cả chúng theo cùng một quy tắc; đó là quyết định của DTO, serialization library và cấu hình. Với API patch/update, `null` có thể mang nghĩa **xóa giá trị**, trong khi field vắng mặt nghĩa **không thay đổi**. Nếu adapter nuốt lỗi rồi tạo DTO với null, bạn có thể vô tình thực hiện thao tác xóa không được yêu cầu.

Một error response có thể chứa mã ổn định, correlation ID và message an toàn; không xuất stack trace, JDBC URL, token hay dữ liệu người dùng ở phía public.

## 13. Database: “không có bản ghi” khác lỗi thực thi truy vấn

Với JDBC:
- `ResultSet.next() == false` sau khi statement được thực thi hợp lệ có thể biểu thị **không còn dòng kết quả**.
- `SQLException` khi chuẩn bị/truy vấn/đọc kết quả báo không hoàn thành theo hợp đồng.
- Connection timeout, transaction timeout và lỗi commit có thể có ngữ nghĩa riêng, đôi khi trạng thái đã thực thi **không chắc chắn**.

Anti-pattern:

~~~java
try {
    return queryCustomer(id);
} catch (SQLException ignored) {
    return null;
}
~~~

Đây là fragment; `queryCustomer` giả định. Nếu null đang có nghĩa “không có khách hàng”, caller có thể tạo một hàng mới vì tưởng ID chưa tồn tại, dù DB đang tạm thời lỗi.

Với giao dịch có side effect, việc nhận lỗi sau commit chưa chắc chứng minh transaction đã rollback. Đừng trả danh sách rỗng, `false` hoặc `UNKNOWN` một cách tùy tiện; chọn đúng trạng thái và biện pháp đối soát khi cần.

### 13.1. Negative caching làm lỗi kéo dài

Một cache có thể lưu kết quả **“không tìm thấy”** để giảm truy vấn lặp. Nhưng nếu một lỗi storage bị chuyển thành `Optional.empty()`, cache có thể lưu absence giả với TTL vài phút; kể cả DB phục hồi, ứng dụng vẫn trả không tồn tại.

Vì vậy nên định nghĩa:
- **Positive cache** cho `Found`.
- **Negative cache** chỉ cho `NotFound` đã xác nhận, thời hạn phù hợp.
- **Không cache như NotFound** các `Failed` hoặc `Unknown`.
- Nếu cache fallback sau lỗi, gắn trạng thái nguồn/degraded và thời hạn riêng.

Mẫu này cho thấy tác hại của default mơ hồ không dừng ở một response; nó có thể lan sang trạng thái cache và ảnh hưởng request về sau.

## 14. Parse/validate: vắng đầu vào khác dữ liệu không hợp lệ

Giả sử đọc cấu hình số lượng worker:

~~~java
int parseWorkers(String raw) {
    try {
        return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
        return 1;
    }
}
~~~

Đây là fragment hoàn chỉnh về cú pháp nhưng chưa có class bao quanh. Nếu contract “không cấu hình thì mặc định 1”, việc biến chuỗi `"xyz"` thành 1 che lỗi cấu hình. Cách hợp lý hơn:
1. Kiểm tra biến cấu hình **không tồn tại** → dùng 1.
2. Có giá trị → parse; nếu không phải số, ném một exception cấu hình có ngữ cảnh.
3. Kiểm tra phạm vi (ví dụ từ 1 đến 64).
4. Báo lỗi startup hoặc kết quả validation rõ ràng.

Với form người dùng, chuỗi rỗng có thể là input hợp lệ trong một số field, nhưng không thể tự dùng cho lỗi không đọc được body JSON hoặc lỗi encoding nếu contract phân biệt.

`Integer.parseInt` ném `NumberFormatException`; nó không trả 0 khi không parse được. Đừng thêm catch để biến thành 0 trừ khi spec thực sự định nghĩa rõ “parse fail = 0” và caller hiểu chính sách đó.

## 15. Mã lỗi có cấu trúc: thiết kế hợp đồng đủ dùng

Một hierarchy kiểu:

~~~java
sealed interface SaveOutcome
        permits Saved, Rejected, SaveFailed {}

record Saved(String id) implements SaveOutcome {}
record Rejected(String businessCode) implements SaveOutcome {}
record SaveFailed(String technicalCode, Throwable cause)
        implements SaveOutcome {}
~~~

Đây là **fragment Java 17**, không phải file chạy độc lập. Nó làm rõ rằng lưu thành công khác bị từ chối vì nghiệp vụ và khác thao tác lưu bị lỗi.

Khi API công khai, caller xử lý kết quả:
- `Saved` → xác nhận thành công.
- `Rejected` → hiển thị lý do nghiệp vụ.
- `SaveFailed` → error code, retry policy, ghi nhận sự cố.

Nếu typed result là chuẩn của module, đừng trộn `null` trả về trong một API và `SaveFailed` ở API tương đương mà không nói rõ. Một enum error code nên **ổn định**, không chỉ là chuỗi `Throwable.getMessage()` vốn có thể thay đổi theo driver, locale và phiên bản code.

### 15.1. Truyền exception hay Result?

| Chọn cách nào? | Khi nào hợp lý? | Trách nhiệm |
| --- | --- | --- |
| `T` + `throws IOException` | Lỗi thực thi ngoại lệ, caller cần biết | Giữ cause/stack; không nuốt |
| `Optional<T>` + exception | Chỉ có/missing là nhánh dữ liệu bình thường | Chỉ empty khi đã xác nhận vắng mặt |
| `Result<T, E>` | Có nhiều trạng thái dự kiến và caller cần phân loại | Trả đủ trạng thái, không đánh tráo mã |
| `Fallback<T>` | Policy cho phép dùng giá trị thay thế | Thể hiện nguồn, độ tươi, giới hạn |
| `null` có contract cụ thể | API legacy/framework yêu cầu null có ý nghĩa đơn nhất | Tài liệu và test rõ; lỗi khác đi kênh riêng |
| `boolean`/primitive mặc định | Kết quả thực sự nhị phân/giá trị mặc định có ý nghĩa | Không dùng nó để che lỗi khác cùng miền |

Không nhất thiết xây dựng framework Result quá lớn cho một phương thức đơn giản. Nguyên tắc cần giữ là **một kết cục quan trọng phải được nhận diện tại tầng ra quyết định**.

## 16. Backward compatibility: sửa API legacy mà không làm hỏng caller

Nếu một API đã công khai `User getUser(id)` và lâu nay có thể trả null, đổi ngay thành `Result<User>` có thể làm vỡ code sử dụng. Cách chuyển đổi an toàn hơn:
1. Xác minh ý nghĩa `null` hiện tại qua test/hợp đồng chính thức, không suy từ vài caller.
2. Tạo API mới tên rõ, ví dụ `findUser(id)` trả `Optional<User>` và truyền exception, hoặc `lookupUser(id)` trả typed result.
3. Viết adapter chuyển giữa mới và cũ khi cần, nhưng không làm mất thông tin lỗi quan trọng ở một API được quảng bá là an toàn.
4. Đánh dấu/ghi tài liệu deprecation nếu migration có lộ trình.
5. Cập nhật caller ở ranh giới HTTP, batch, async và UI để xử lý đủ nhánh.
6. Thêm metric theo dõi việc còn gọi API cũ trước khi loại bỏ.

Cẩn trọng: một adapter cũ buộc phải trả null có thể không diễn đạt được các nhánh mới. Nếu không thể thay signature, có thể cần truyền exception ra ngoài với kiểu phù hợp thay vì âm thầm trả null. Phương án thực tế phụ thuộc compatibility và quy tắc xử lý lỗi hiện có.

### 16.1. Annotation nullability chưa đủ

Annotation như `@Nullable`, `@NonNull` hay thông tin trong JSpecify/IDE có thể giúp static analysis phát hiện khả năng nhận null, nhưng:
- Không tự giải thích **vì sao** null xuất hiện.
- Không cam kết caller phân biệt null hợp lệ với lỗi đã bị nuốt.
- Không phải mọi annotation đều được runtime tự động kiểm tra.
- Không thay thế tài liệu về exception/result.

Nếu hợp đồng là “null = không tìm thấy”, còn “backend unavailable = exception”, ghi rõ cả hai trong Javadoc và test. Đừng chỉ thêm annotation vào một API đã mơ hồ rồi tuyên bố vấn đề được giải quyết.

## 17. Thu exception đầy đủ và giữ nguyên nhân khi báo lỗi

Giả sử code:

~~~java
try {
    return repository.load(id);
} catch (IOException e) {
    return fallback;
}
~~~

Đây là fragment. Nếu `fallback` là dữ liệu cũ được phép dùng, bạn vẫn cần:
- ghi metric lỗi của nguồn chính;
- phân biệt result degraded với result chính;
- giữ exception/cause trong log nội bộ khi phục vụ điều tra;
- đánh dấu đã dùng fallback trên trace khi có thể;
- xác định lỗi nào được phép phục hồi bằng fallback.

Không nên log đầy đủ stack trace ở **mọi tầng** rồi rethrow gây trùng lặp. Chọn ranh giới sở hữu reporting, truyền cause qua wrapper/result để không mất thông tin; với kênh bên ngoài, chỉ xuất message an toàn.

Mẫu bọc hợp lý:

~~~java
try {
    return repository.load(id);
} catch (IOException e) {
    throw new ServiceUnavailableException("User store unavailable", e);
}
~~~

Đây là fragment; `ServiceUnavailableException` có thể là một custom RuntimeException/checked exception và phải được định nghĩa trong project thật. Constructor mang `cause` cho phép tầng API chuyển sang mã lỗi phù hợp, log có stack trace gốc và correlation ID.

### 17.1. Đừng tạo lỗi thứ cấp thay nguyên nhân gốc

Nếu catch lỗi rồi trả `null`, caller có thể ném `NullPointerException` ở vị trí xa hơn. Log cuối chỉ thấy NPE mà không thấy `IOException`. Đây là lý do nên giữ cause ngay khi lỗi xảy ra, không chờ tới lúc một thao tác khác fail.

Nếu chuyển lỗi thành `Result.Failed`, có thể giữ `Throwable` **nội bộ** hoặc một error object có thông tin tracing phù hợp. Không làm message text trở thành hợp đồng parser của caller.

## 18. Kết quả mặc định và tính đúng đắn của retry

Một lỗi I/O được đổi thành kết quả mặc định sẽ ngăn caller kích hoạt retry theo chính sách. Ví dụ:
- API tìm kiếm trả `List.of()` → caller nghĩ thực sự không có đơn cần xử lý.
- Job scheduler nhận `true` → đánh dấu công việc DONE, không retry.
- ETL nhận `0` → ghi báo cáo “0 bản ghi”, bỏ sót cả batch.
- Permission service trả `false` → tất cả bị từ chối, nhưng không báo sự cố hạ tầng.
- Inventory service trả `0` → hệ thống đóng bán dù hàng thực tế có thể còn.

Ngược lại, nếu callback retry **sau một timeout không rõ trạng thái**, việc chạy lại có thể nhân đôi side effect. Vì thế cũng không nên tự động biến mọi lỗi thành `Retryable`.

Một chính sách đầy đủ thường cần các loại:
- `RETRYABLE_TRANSIENT` nếu có bằng chứng thao tác an toàn để retry.
- `PERMANENT_VALIDATION` nếu input cần sửa.
- `UNKNOWN_OUTCOME` nếu request có thể đã hoàn tất ở remote.
- `CANCELLED` nếu có yêu cầu hủy hợp lệ.
- `SUCCESS`/`NOT_FOUND` đúng hợp đồng.

Với thao tác có side effect, cần **operation ID/idempotency key được server thực thi đúng** và quy trình tra cứu/đối soát trạng thái. Một HTTP timeout không chứng minh server chưa commit; trả `null` hoặc `false` có thể che sự không chắc chắn đó.

## 19. Transaction và trạng thái một phần

Ví dụ quy trình:
1. Ghi đơn hàng vào database.
2. Gửi lệnh thanh toán tới remote.
3. Ghi event audit.

Nếu lỗi xảy ra ở bước 2 hoặc 3, việc catch rồi trả `Order.default()` có thể nói sai rằng đơn hàng chưa được tạo, dù DB đã commit. Kể cả khi ném exception đúng, side effect đã có **không tự rollback**.

Nên thiết kế ranh giới:
- Trong một JDBC transaction chưa commit, rollback khi lỗi phù hợp và biết trạng thái connection.
- Với file publish, staging/temp + atomic move khi hỗ trợ, và cleanup file tạm thuộc quyền sở hữu.
- Với HTTP/payment/email, dùng idempotency, outbox/saga/compensation hoặc reconciliation theo tính chất hệ thống.
- Nếu outcome chưa xác định, trả `UNKNOWN`/`PENDING` thay vì giá trị mặc định ngụ ý chắc chắn.

Đừng giấu exception vì sợ caller thấy “failed” và retry sai. Cách đúng là **truyền một trạng thái mô tả chính xác sự không chắc chắn** và thiết kế retry an toàn.

## 20. Batch và aggregate: rỗng không có nghĩa mọi task thành công

Một `List<Result>` rỗng có thể nghĩa là:
- Danh sách input rỗng.
- Mọi task hoàn thành nhưng không có dữ liệu.
- Bộ tổng hợp lỗi nên đã bỏ qua kết quả.
- Một exception làm vòng lặp dừng trước khi thêm phần tử.

Nếu code:

~~~java
List<Item> items = new ArrayList<>();
for (String id : ids) {
    try {
        items.add(loadItem(id));
    } catch (IOException ignored) {
    }
}
return items;
~~~

Đây là fragment giả định `Item`, `ids` và `loadItem` đã được khai báo/import. Caller nhận list có thể thiếu phần tử mà không biết những ID nào lỗi. Trong nghiệp vụ báo cáo/đối soát, đây là dữ liệu không đầy đủ nhưng được trình bày như kết quả cuối.

Các cách rõ ràng:
- Fail fast và truyền exception nếu yêu cầu toàn bộ batch phải thành công.
- Trả `BatchResult(successes, failures)` có danh sách ID lỗi và nguyên nhân/mã lỗi.
- Trả kết quả từng item `Found/Missing/Failed` theo thứ tự input.
- Nếu bỏ qua lỗi từng phần là chủ đích, ghi rõ `partial=true` và thống kê số task thất bại.

Trong xử lý nhiều `Future` hoặc `CompletableFuture.allOf`, không chỉ đợi completion rồi trả `List.of()` khi một future fail. Lỗi của task cần được quan sát/tổng hợp theo hợp đồng. `isDone()` bao gồm cả hoàn thành lỗi hoặc hủy; nó không phải cờ thành công nghiệp vụ.

## 21. Thiết kế chỉ số giám sát cho failure và fallback

Nếu mọi exception bị chuyển thành kết quả “bình thường”, monitoring dựa trên số HTTP 5xx, exception counters hoặc exceptional completion của Future sẽ bỏ sót nhiều sự cố.

Các chỉ số nên phân biệt:

| Chỉ số/trạng thái | Ý nghĩa |
| --- | --- |
| `primary_success` | Nguồn chính trả kết quả hợp lệ |
| `not_found` | Truy vấn hoàn thành, không có dữ liệu |
| `technical_failure` | Lỗi ở nguồn chính, chưa phục hồi |
| `fallback_used` | Nguồn chính thất bại, chính sách dùng fallback |
| `fallback_failed` | Fallback cũng không thể tạo kết quả |
| `unknown_outcome` | Không thể khẳng định kết quả cuối (timeout sau side effect) |
| `cancelled` | Công việc bị hủy theo hợp đồng |
| `partial_result` | Batch chỉ có một phần kết quả, còn lỗi chưa xử lý/được báo |

Không dùng ID request duy nhất làm label cardinality cao của metric; lưu ở log/tracing phù hợp. Không log dữ liệu cá nhân, token, stack trace ra response public. Với fallback, metric success cuối và metric primary failure **đều có thể tăng** tùy mục đích: một thước đo chất lượng nguồn chính, một thước đo khả năng phục hồi trải nghiệm người dùng.

### 21.1. Log và catch không nhất thiết là cùng tầng

Không phải mọi `catch` đều phải log ERROR. Nếu tầng thấp bắt để bọc exception và ném tiếp, có thể để tầng ranh giới log một lần. Nếu một nhánh fallback phục hồi được, có thể ghi metric/trace degraded thay vì in cùng stack trace nhiều nơi.

Điều bắt buộc là **tín hiệu thất bại hoặc recovery status không biến mất**, không phải số lượng dòng log.

## 22. Kiểm thử: tiêm lỗi và chứng minh các trạng thái không bị trộn

Một test chỉ chạy đường thành công là chưa đủ. Một ma trận nên gồm:

| Dữ liệu/điểm tiêm lỗi | Kết quả đúng kỳ vọng |
| --- | --- |
| Bản ghi có tồn tại | `Found(value)` |
| Truy vấn thành công không thấy | `Missing`/`Optional.empty()` |
| Database ném `IOException` | `Failed(STORAGE_UNAVAILABLE)` hoặc exception |
| Lỗi cấu hình/parsing | Báo invalid config; không tự default nếu giá trị đã được cung cấp |
| Nguồn chính lỗi nhưng cache hợp lệ | `Fallback(value, provenance)` nếu policy cho phép |
| Nguồn chính lỗi và không có cache | `Failed`, không có `Found("")` |
| Fallback quá hạn/khác tenant | Không trả fallback như dữ liệu hợp lệ |
| Task async ném exception | Future/stage giữ failure hoặc typed result rõ |
| Task bắt lỗi rồi trả `null` | Test phát hiện vi phạm contract |
| Caller timeout trước worker kết thúc | `UNKNOWN`/timeout đúng chính sách, không tự NotFound |
| Batch có một phần failure | Các failure được giữ trong báo cáo, không mất ID |
| API có side effect rồi lỗi | Không báo chắc chắn “chưa có gì xảy ra” nếu đã commit |

### 22.1. Ví dụ JUnit 5 cho result có kiểu

Đoạn sau là **fragment** cho project đã có JUnit Jupiter và class `ExplicitFailureResultDemo` ở ví dụ runnable. Cần static imports từ `org.junit.jupiter.api.Assertions`:

~~~java
var found = ExplicitFailureResultDemo.lookup("A");
assertInstanceOf(ExplicitFailureResultDemo.Found.class, found);

var missing = ExplicitFailureResultDemo.lookup("B");
assertInstanceOf(ExplicitFailureResultDemo.Missing.class, missing);

var failed = ExplicitFailureResultDemo.lookup("DB_DOWN");
var error = assertInstanceOf(ExplicitFailureResultDemo.Failed.class, failed);
assertEquals(ExplicitFailureResultDemo.FailureCode.STORAGE_UNAVAILABLE,
        error.code());
assertInstanceOf(IOException.class, error.cause());
~~~

Test này chứng minh ba nhánh không bị gộp. Với API dùng `Optional` cộng `throws IOException`, test phải xác nhận empty chỉ khi truy vấn hoàn tất bình thường và lỗi storage truyền exception.

### 22.2. Kiểm tra contract với mock/fake

Nếu repository thật khó tiêm lỗi, dùng fake dependency có thể:
- Trả bản ghi.
- Trả không có bản ghi.
- Ném đúng exception hạ tầng.
- Ném exception lập trình bất ngờ.
- Đợi quá deadline.
- Thực hiện side effect rồi ném.

Hãy kiểm tra **output + cause + state sau lỗi + trạng thái metric/job**, không chỉ assert “phương thức không crash”. Với code có tính đồng thời, dùng latch/future timeout để test có điều phối; không dựa vào `Thread.sleep` đoán scheduling.

### 22.3. Property/invariant test hữu ích

Một vài bất biến có thể kiểm tra:
- Nếu repository ném exception mà không có fallback được phép, API **không** trả `Found`/`NotFound`.
- Nếu `Fallback` được trả, metadata phải nói rõ nguồn và đáp ứng giới hạn tuổi dữ liệu.
- Nếu `Missing` được trả, không có lỗi đọc nguồn trong lượt thao tác tương ứng.
- Nếu caller nhận `SUCCESS`, mọi bước bắt buộc của contract đã hoàn thành hoặc có cơ chế cam kết bền vững.
- Nếu kết quả `UNKNOWN`, code không tự retry non-idempotent command với operation ID mới.
- Không dùng cùng một sentinel cho hai kết cục cần xử lý khác nhau.

Đây là các tính chất có thể áp vào contract test trong CI; không nên hardcode stack trace/số dòng của code source vào unit test chức năng.

## 23. Những anti-pattern cần nhận diện trong code review

### 23.1. `catch (Exception e) { return null; }`

Vấn đề: mất loại lỗi và cause, trộn nhiều kết cục. Khắc phục: bắt chính xác loại có thể xử lý; nếu không phục hồi, truyền exception hoặc typed result.

### 23.2. `catch (RuntimeException e) { return 0; }`

Vấn đề: lỗi lập trình cũng biến thành số 0. Với dữ liệu số quan trọng, sai số có thể lan vào tính tiền/báo cáo. Khắc phục: kiểm tra input và lỗi đúng ranh giới; không dùng 0 làm “lỗi” nếu 0 hợp lệ.

### 23.3. `exceptionally(error -> List.of())`

Vấn đề: CompletableFuture hoàn thành bình thường bằng list rỗng khi nguồn lỗi. Các observer sau đó có thể ghi nhận success. Khắc phục: giữ exceptional completion hoặc chuyển sang `Failure` có mã.

### 23.4. `Optional.empty()` từ mọi lỗi I/O

Vấn đề: mất khác biệt absence/unavailable, nguy cơ negative cache sai. Khắc phục: chỉ empty khi đã xác nhận vắng mặt.

### 23.5. `return false` từ thao tác lưu khi remote timeout

Vấn đề: `false` có thể ngụ ý đã thất bại chắc chắn, trong khi server đã commit. Khắc phục: mô hình `UNKNOWN`/operation ID và đối soát.

### 23.6. `return defaultConfig` sau lỗi parse

Vấn đề: cấu hình sai âm thầm được chấp nhận. Khắc phục: dùng default chỉ cho absence nếu contract định nghĩa; dữ liệu cấu hình invalid phải báo rõ.

### 23.7. Log `e.getMessage()` rồi return null

Vấn đề: log có thể không có stack/cause; caller vẫn thấy kết quả giả. Khắc phục: truyền trạng thái lỗi rõ ràng và log throwable ở điểm phù hợp khi cần.

### 23.8. `return null` trong `finally`

Fragment sai:

~~~java
String compute() {
    try {
        throw new IllegalStateException("calculation failed");
    } finally {
        return null;
    }
}
~~~

Đây là **mã Java hoàn chỉnh ở mức phương thức**, cần đặt trong một class để biên dịch. Một `return` trong `finally` có thể **che exception hoặc kết quả** từ khối `try`. Tránh đặt `return` trong `finally` để chỉ cung cấp default; dùng `finally` cho cleanup an toàn, không thay điều khiển kết quả chính một cách bất ngờ.

### 23.9. Dùng kết quả mặc định mà không công bố chế độ degraded

Vấn đề: caller không biết giá trị đến từ cache hay nguồn chính. Khắc phục: trả provenance/freshness hoặc một trạng thái rõ ràng phù hợp API.

### 23.10. Nuốt `InterruptedException` rồi trả success/default

Vấn đề: mất tín hiệu shutdown/cancellation, task có thể tiếp tục sai trạng thái. Khắc phục: propagate hoặc restore interrupt flag và kết thúc theo contract; không coi đây là lỗi nghiệp vụ không đáng kể.

## 24. Hướng dẫn quyết định nhanh

~~~text
Đang định catch exception rồi trả một giá trị mặc định?
|
+-- Giá trị đó có phải kết quả hợp lệ của thao tác?
|      +-- Có -> có nguy cơ trộn success/absence/failure
|      +-- Không -> cần contract nói rõ sentinel và bảo đảm caller kiểm tra
|
+-- Exception có thể được phục hồi thực sự?
|      +-- Không -> giữ exception/cause hoặc trả Failure rõ ràng
|      +-- Có -> thực hiện recovery có giới hạn và xác minh hậu điều kiện
|
+-- Fallback có được nghiệp vụ cho phép?
|      +-- Có -> trả value + provenance/status phù hợp
|      +-- Không -> không tạo success giả
|
+-- Có side effect hoặc remote timeout?
|      +-- Có -> có thể cần Partial/Pending/Unknown + operation ID
|      +-- Không -> kết quả thất bại theo API
|
+-- Ai cần biết kết quả cuối?
       +-- Caller / coordinator / metric / job store
       +-- bảo đảm thông tin thất bại không bị cắt ở catch
~~~

Đây là cây quyết định định hướng. Nếu một tình huống thật sự chỉ có hai trạng thái và default được contract định nghĩa đầy đủ, không cần ép vào một hierarchy phức tạp. Nhưng một khi caller cần phân biệt **không có dữ liệu** với **không thể đọc dữ liệu**, một sentinel duy nhất không còn đủ.

## 25. Checklist trước khi đưa API vào production

- [ ] `null`, giá trị rỗng, `0`, `false` và default object có **ý nghĩa được ghi rõ**.
- [ ] Absence được phân biệt với lỗi hạ tầng/timeout/cancellation.
- [ ] Không dùng `Optional.empty()` để che IOException/SQLException nếu empty nghĩa là NotFound.
- [ ] `catch` chỉ bắt những exception đúng chính sách; không nuốt bug nhờ catch quá rộng.
- [ ] Nếu không thể phục hồi, exception được truyền hoặc chuyển thành `Failure` có mã và cause.
- [ ] Fallback được phép theo nghiệp vụ, có nguồn, độ tươi và giới hạn rõ.
- [ ] Khi cache kết quả NotFound, chỉ cache một absence đã được xác nhận.
- [ ] `CompletableFuture.exceptionally`/`handle` không tự tạo “success giả” bằng giá trị mặc định không có nhãn.
- [ ] Future được quan sát đầy đủ, không bỏ qua exception chỉ vì task đã hoàn tất.
- [ ] Timeout/interrupt/cancel không bị chuyển thành kết quả thành công nếu chưa đủ bằng chứng.
- [ ] API HTTP phân biệt 404 thật với 503/500 và Pending khi cần.
- [ ] Có operation ID/idempotency khi side effect remote có thể hoàn tất dù caller timeout.
- [ ] Không xuất stack trace, bí mật hoặc cause thô ra response public.
- [ ] Monitoring theo dõi primary failure, fallback used, technical failure và unknown outcome.
- [ ] Test tiêm lỗi vào dependency chứng minh code không gộp các trạng thái cần phân biệt.
- [ ] Đã cân nhắc backward compatibility và tài liệu khi thay đổi kiểu trả về API legacy.

## 26. Bài tập thực hành

Một service nhận yêu cầu lấy giá sản phẩm, trả `BigDecimal`. Cách hiện tại:

~~~java
BigDecimal getPrice(String sku) {
    try {
        return priceRepository.get(sku);
    } catch (Exception ignored) {
        return BigDecimal.ZERO;
    }
}
~~~

Đây là **fragment** với `BigDecimal` và `priceRepository` giả định.

Hãy trả lời và triển khai:
1. Nếu sản phẩm có giá **0 đồng** hợp lệ, caller phân biệt thế nào với lỗi database?
2. Nếu `sku` không tồn tại, nên trả `Optional<BigDecimal>`, ném một `NotFoundException` hay result `Missing`? Chọn một contract và giải thích.
3. Nếu giá được dùng để thanh toán, có được lấy giá cache cũ khi nguồn lỗi không? Phải kiểm tra những invariant nào?
4. Nếu API HTTP trả 200 với giá 0 sau `SQLException`, các hệ thống phía dưới có thể mắc sai lầm nào?
5. Viết một sealed result type `PriceFound`, `PriceMissing`, `PriceUnavailable`; giữ error code ổn định và cause nội bộ.
6. Tạo fake repository cho ba tình huống found/missing/exception. Thêm test rằng exception **không được chuyển thành giá 0 hợp lệ**.
7. Giả sử lookup giá được chạy trong `CompletableFuture`: thay `exceptionally(e -> BigDecimal.ZERO)` bằng một stage trả result có kiểu hoặc giữ exceptional completion. Kiểm tra metric không đếm lỗi là thành công.
8. Nếu giá đã được dùng tạo đơn rồi lỗi gửi thông báo, giải thích vì sao không được trả giá 0/ngụ ý không có đơn hàng và cần trạng thái nào cho side effect đã xảy ra.

**Kết luận:** Một giá trị mặc định không tự mang ý nghĩa “thất bại”. Khi contract không nêu rõ, việc catch exception rồi trả `null`, `false`, `0`, `Optional.empty()` hoặc collection rỗng có thể biến lỗi thật thành dữ liệu có vẻ hợp lệ. Hãy **tách absence, failure, fallback và unknown**, giữ nguyên nhân lỗi, và kiểm thử đường thất bại để caller nhận đúng sự thật về kết quả của thao tác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-009">Thử lại mọi ngoại lệ mà không xét tính tạm thời và tác động lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Thử lại mọi ngoại lệ mà không xét tính tạm thời và tác động lặp

**Retry (thử lại)** là một cơ chế phục hồi khi thao tác có thể thất bại nhất thời. Tuy nhiên, cách xử lý kiểu `catch (Exception e) { retry(); }` có thể biến một lỗi nhỏ thành lỗi nghiêm trọng hơn: thử lại đầu vào vốn luôn sai, lặp thanh toán hoặc gửi email, làm nghẽn hệ thống đang quá tải, che mất bug lập trình và khiến một workflow không bao giờ dừng. Một exception **không tự chứng minh** rằng thất bại là tạm thời, và việc caller không nhận được phản hồi **không chứng minh** rằng thao tác chưa tạo ra tác động.

Để quyết định retry đúng trong Java, cần trả lời ba câu hỏi độc lập:

1. **Lỗi có khả năng phục hồi theo thời gian không?** Có cơ sở để tin rằng lần thử sau có thể thành công mà không đổi input hoặc trạng thái nghiệp vụ?
2. **Thử lại có an toàn đối với tác động lặp không?** Nếu lần đầu đã thực hiện một phần hoặc toàn bộ side effect, thao tác mới có tạo dữ liệu trùng, trừ tiền lần hai hay gửi thông báo nhiều lần không?
3. **Giới hạn và quyền hủy là gì?** Có deadline, số lần thử, backoff, jitter, ngân sách retry và tín hiệu interruption/cancellation rõ ràng không?

Câu trả lời của ba câu này quyết định retry policy; **không phải chỉ tên lớp `Exception`**. Bài này thuộc nhóm *Sai lầm thường gặp* của Java Core – Exception, tập trung vào phân loại lỗi, idempotency và thiết kế retry có kiểm soát trong Java 17+.

## 1. Vì sao `catch (Exception)` rồi retry vô điều kiện là sai?

Mã minh họa sau thể hiện anti-pattern:

```java
while (true) {
    try {
        sendPayment();
        break;
    } catch (Exception error) {
        // Sai: không phân loại lỗi, không giới hạn số lần,
        // không kiểm tra tác động lặp, không tôn trọng ngắt.
    }
}
```

Đây là đoạn **minh họa nguy hiểm**, không phải chương trình độc lập; `sendPayment()` do ứng dụng định nghĩa. Nó có thể gây:

- **Vòng lặp vô hạn:** nếu amount âm hoặc token không hợp lệ, gọi lại cùng dữ liệu không làm lỗi biến mất.
- **Tác động lặp:** nếu server đã ghi giao dịch nhưng response bị mất, retry có thể ghi giao dịch lần hai.
- **Retry storm:** khi dependency quá tải, mọi caller cùng thử lại nhanh làm dependency quá tải hơn.
- **Che bug:** `NullPointerException`, `ClassCastException` hoặc vi phạm invariant có thể bị xem nhầm là lỗi mạng.
- **Mất tín hiệu ngắt:** nếu bắt rộng và không xử lý đúng `InterruptedException`, shutdown có thể bị trì hoãn.
- **Tăng độ trễ:** một request tiếp tục chờ qua nhiều lần thử cho tới khi vượt deadline của người gọi.
- **Khó quan sát:** log chỉ xuất hiện ở lần cuối, hoặc mỗi attempt in cả stack trace làm log storm; nguyên nhân ban đầu bị che.
- **Rò rỉ tài nguyên:** mỗi attempt mở connection/stream rồi thất bại nhưng không cleanup đúng owner.

Một lỗi có thể được **bắt** không có nghĩa rằng code bắt lỗi đủ hiểu để **phục hồi**. Tầng retry nên hiểu loại thao tác, nguồn lỗi và ràng buộc tính nhất quán.

## 2. Phân loại theo khả năng khắc phục, không theo checked/unchecked

Java phân biệt checked và unchecked exception ở cơ chế biên dịch; nhưng **checked không đồng nghĩa transient**, cũng như unchecked không nhất thiết permanent.

| Loại tình huống | Ví dụ | Retry cùng input ngay có hợp lý? |
| --- | --- | --- |
| **Transient** — có thể đổi theo thời gian | Kết nối tới dịch vụ tạm gián đoạn, server đang quá tải, tranh chấp tài nguyên ngắn hạn | Có thể, nếu thao tác an toàn khi lặp và chính sách có backoff. |
| **Permanent / deterministic** | Thiếu trường bắt buộc, dữ liệu sai format, vi phạm quy tắc cố định, quyền truy cập bị từ chối | Thường không; cần sửa input/quyền/cấu hình. |
| **Ambiguous outcome** | Server có thể đã xử lý nhưng response/ack bị mất, client timeout sau khi gửi request | Chỉ retry an toàn khi có idempotency/deduplication hoặc cơ chế đối soát trạng thái. |
| **Cancellation/interruption** | Caller bị ngắt khi chờ, task bị hủy | Không nên chống lại yêu cầu dừng bằng retry tự động. |
| **Programming bug** | `NullPointerException` do invariant bị phá, lỗi cast, bug truy cập index | Không; sửa code, bảo toàn stack trace, cảnh báo phù hợp. |
| **Resource exhaustion** | Pool cạn, lỗi thiếu bộ nhớ, disk đầy | Phụ thuộc nguồn lực và chính sách; retry dồn dập thường làm tệ hơn. |

Một `IOException` có thể là mất kết nối thoáng qua, nhưng cũng có thể là đường dẫn không tồn tại, permission bị từ chối hoặc dữ liệu sai không thể sửa bằng retry. Một `SQLException` có thể báo deadlock có khả năng thử lại hoặc vi phạm unique constraint sẽ thất bại lặp đi lặp lại. **Không retry toàn bộ `IOException` hoặc `SQLException` chỉ vì chúng là checked exception.**

Ngược lại, một exception miền ứng dụng dạng unchecked, ví dụ `TemporaryCapacityException extends RuntimeException`, có thể đại diện cho lỗi transient được công khai rõ. Điều quan trọng là **hợp đồng API của lỗi**, không phải nhánh cây kế thừa duy nhất.

## 3. Transient failure là gì?

Một thất bại *transient* là tình huống có cơ sở để tin trạng thái gây lỗi **có thể thay đổi**, khiến thử lại có xác suất thành công. Ví dụ:

- Dịch vụ downstream trả tín hiệu quá tải tạm thời và yêu cầu thử lại sau.
- Kết nối bị reset trước khi gửi request trong một thao tác đọc an toàn, với điều kiện đã biết request chưa được server xử lý.
- Deadlock/serialization conflict có contract cho phép lặp **toàn bộ transaction**, không chỉ một câu lệnh rời rạc.
- Một tài nguyên do dịch vụ khác cập nhật có thể trở nên sẵn sàng sau một khoảng thời gian.

Tuy nhiên, từ “transient” không có nghĩa “retry ngay vô hạn”:

1. Dependency có thể còn đang lỗi — cần backoff.
2. Nhiều caller retry đồng thời — cần jitter và hạn chế tải.
3. Deadline của request có thể đã hết — retry tiếp không còn hữu ích.
4. Side effect có thể đã xảy ra trước khi exception lộ ra — cần xác định an toàn khi lặp.
5. Tăng retry đồng nghĩa tăng tổng số attempt, không phải luôn cải thiện availability.

Một timeout cũng **không tự chứng minh transient**: nó chỉ nói một thao tác chờ hoặc lời gọi đã không hoàn thành trong ngân sách thời gian. Kết quả phía remote có thể đang xử lý, đã commit hoặc đã thất bại. Retry policy phải xét thêm **mức độ không chắc chắn của outcome**.

## 4. Permanent failure: retry không thay đổi điều kiện thất bại

Một số lỗi chỉ có thể sửa bằng thay đổi dữ liệu, quyền hoặc chương trình:

- `IllegalArgumentException` do quantity âm.
- Lỗi schema/validation của payload mà lần retry vẫn gửi cùng bytes.
- Token hết hạn hoặc quyền không đủ; có thể cần cấp lại token theo một **workflow riêng**, không phải lặp vô hạn cùng request.
- Ràng buộc duy nhất của database bị vi phạm và dữ liệu không thay đổi.
- Endpoint hoặc cấu hình không tồn tại theo contract.
- Lỗi lập trình như dereference `null` không được phép.

Nếu caller retry cùng request nguyên trạng sau một trong các lỗi này, kết quả có thể chỉ lặp lại lỗi, tiêu tốn CPU/network và làm nhiễu metrics. Nên **fail fast với thông tin có thể hành động**: sửa input, xin quyền, cập nhật cấu hình hoặc sửa code.

Đôi khi một HTTP status như `404` có thể tạm thời trong hệ thống eventual consistency, nhưng có thể permanent ở API khác. Tương tự `409 Conflict` có thể là xung đột phiên bản giải quyết được bằng **đọc lại và tính toán lại**; retry y nguyên payload cũ chưa chắc có ích. Không gán toàn bộ status code thành transient/permanent khi thiếu contract.

## 5. Outcome không rõ: lỗi nguy hiểm nhất với thao tác có side effect

Giả sử client gửi lệnh “trừ 100.000 đồng”:

```text
Client                           Server
   |                               |
   |---- POST /payments ---------->|
   |                               | Ghi giao dịch thành công
   |                               | Commit dữ liệu
   |<---- phản hồi bị mất ----------X
   |
   +-- client timeout
   +-- retry POST /payments ------> ?
```

Nếu hệ thống chỉ nhìn `SocketTimeoutException` rồi thử lại, server có thể xử lý **hai giao dịch**. Timeout là lỗi ở đường truyền/quan sát; nó không khẳng định request chưa đến server. Đây là tình huống **ambiguous outcome**.

Các cách thiết kế an toàn hơn:

- **Idempotency key:** caller cấp một định danh ổn định cho **cùng một thao tác logic** qua các attempt. Server nhớ kết quả cho key đó và trả lại kết quả cũ thay vì làm tác động lần nữa.
- **Deduplication dựa trên business key:** số đơn hàng, transaction ID, operation ID duy nhất; enforce bằng transaction/unique constraint.
- **Đối soát/truy vấn trạng thái:** kiểm tra ID đã xử lý chưa trước khi quyết định gửi lại.
- **Transactional outbox/inbox:** ghi trạng thái nghiệp vụ và sự kiện theo giao dịch rồi xử lý giao nhận lặp với consumer idempotent.
- **Compensation:** có thể cần khi hệ thống không có giao dịch nguyên tử xuyên dịch vụ; thao tác bù không thay thế việc ngăn side effect trùng.

**Không được tạo idempotency key mới trên mỗi attempt của cùng operation**: làm vậy biến các attempt thành các lệnh logic độc lập trong mắt server. Cùng thao tác phải dùng **cùng key và payload phù hợp**. Một yêu cầu nghiệp vụ khác thực sự phải dùng key khác.

Idempotency key không phải “có chuỗi UUID là xong”. Server phải kiểm tra key **cùng payload/định danh khách hàng**, lưu trạng thái/kết quả đủ bền, xử lý tranh chấp đồng thời, xác định thời hạn lưu và ngăn reuse key với payload khác.

## 6. An toàn khi lặp: đọc, ghi và idempotency

**Idempotent** nghĩa là lặp cùng một operation nhiều lần có **tác động dự kiến lên trạng thái** như thực hiện một lần (theo contract quan sát được). Nó không nhất thiết đồng nghĩa số lần thực thi bằng một hoặc mọi response byte đều giống nhau.

Ví dụ:

| Operation | Khả năng retry | Điểm cần kiểm tra |
| --- | --- | --- |
| Đọc dữ liệu không có side effect | Thường an toàn hơn | Cache, snapshot, timeout, semantics đọc và tải xuống. |
| Đặt `status = ACTIVE` | Có thể idempotent về trạng thái | Audit/email phát sinh mỗi lần có thể vẫn là side effect lặp. |
| Tăng `counter = counter + 1` | **Không idempotent** nếu không có dedup | Lặp tạo tổng sai. |
| `POST` tạo một khoản thanh toán | Không tự an toàn | Cần idempotency key/dedup hoặc đối soát. |
| `PUT` thay thế trạng thái resource | Về semantics HTTP thường được thiết kế idempotent | Business hooks, version, side effects, conditional requests vẫn phải xét. |
| `DELETE` theo identifier | Có thể idempotent về trạng thái cuối | Response lần hai có thể khác; audit/notification cũng có thể lặp. |
| Gửi email/webhook | Không tự idempotent | Cần message ID, dedup ở downstream hoặc chấp nhận ít nhất một lần và xử lý trùng. |

Đừng suy rằng HTTP `GET` luôn an toàn bất chấp implementation vi phạm semantics, hoặc HTTP `POST` luôn không thể retry. Chính contract của endpoint và deduplication phía server quyết định.

Thao tác *at-least-once delivery* thường có thể nhận cùng sự kiện nhiều lần. Consumer phải idempotent nếu mục tiêu là trạng thái đúng qua sự kiện lặp. Từ “exactly once” trong hệ phân tán phải được hiểu dưới **ranh giới bảo đảm cụ thể**, không phải lời hứa chung rằng không bao giờ có retry hay xử lý trùng ở mọi tầng.

## 7. Ví dụ Java 17: chỉ retry lỗi transient

Ví dụ này tách rõ lỗi có thể thử lại và lỗi input không nên thử lại. Để stdout xác định, chương trình dùng bộ đếm số attempt và **không ngủ/backoff**; phần delay là cơ chế production phải bổ sung, không phải bằng chứng một policy production hoàn chỉnh.

Tạo file `SelectiveRetryDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SelectiveRetryDemo {

    private static final class TemporaryFailureException
            extends RuntimeException {
        TemporaryFailureException(String message) {
            super(message);
        }
    }

    private static <T> T retryTransient(
            String label, Supplier<T> action, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T value = action.get();
                System.out.println(label + " attempt " + attempt + ": SUCCESS");
                return value;
            } catch (TemporaryFailureException ex) {
                System.out.println(label + " attempt " + attempt + ": TRANSIENT");
                if (attempt == maxAttempts) {
                    throw ex;
                }
            } catch (IllegalArgumentException ex) {
                System.out.println(label + " attempt " + attempt + ": PERMANENT");
                throw ex;
            }
        }

        throw new AssertionError("unreachable");
    }

    public static void main(String[] args) {
        AtomicInteger readCalls = new AtomicInteger();

        String read = retryTransient("read", () -> {
            int number = readCalls.incrementAndGet();
            if (number <= 2) {
                throw new TemporaryFailureException("temporarily offline");
            }
            return "DATA";
        }, 3);
        System.out.println("read result: " + read);

        try {
            retryTransient("validate", () -> {
                throw new IllegalArgumentException("invalid input");
            }, 3);
        } catch (IllegalArgumentException ex) {
            System.out.println("validate stopped");
        }

        try {
            retryTransient("offline", () -> {
                throw new TemporaryFailureException("still offline");
            }, 2);
        } catch (TemporaryFailureException ex) {
            System.out.println("offline stopped");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SelectiveRetryDemo.java
java SelectiveRetryDemo
```

Stdout:

```text
read attempt 1: TRANSIENT
read attempt 2: TRANSIENT
read attempt 3: SUCCESS
read result: DATA
validate attempt 1: PERMANENT
validate stopped
offline attempt 1: TRANSIENT
offline attempt 2: TRANSIENT
offline stopped
```

Điểm cần học:

1. `TemporaryFailureException` ở ví dụ là **loại lỗi domain có hợp đồng cho phép thử lại**; tên class `RuntimeException`/`checked` không tự quyết định.
2. `read` thất bại hai lần rồi thành công ở lần thứ ba; toàn bộ operation được cho phép 3 attempt, **không phải 3 lần retry thêm**.
3. `validate` nhận lỗi permanent và dừng ngay ở attempt đầu, không lặp ba lần.
4. `offline` có giới hạn 2 attempt và truyền exception cuối ra ngoài; callback bên ngoài quyết định trả lỗi hoặc giám sát.
5. `Supplier.get()` có thể ném unchecked exception. Demo không bắt `Exception` tổng quát; những lỗi khác ngoài loại biết rõ sẽ truyền ra, tránh che bug.
6. Demo không có I/O, không có side effect và không thực hiện delay, deadline, jitter, circuit breaker hay interruption. Trong ứng dụng thật, chỉ dùng nó làm **minh họa phân loại và giới hạn**, không copy nguyên xi như retry framework production.

Với một operation có thể thay đổi trạng thái, **kiểm tra an toàn lặp trước khi gọi `retryTransient`**. Một exception loại temporary không làm cho thao tác ghi trở thành idempotent.

## 8. Ví dụ Java 17: idempotency key giữ side effect không bị lặp

Ví dụ mô phỏng việc server đã commit rồi client không nhận phản hồi. Client gọi lại bằng **cùng operation ID** và server trả kết quả đã ghi thay vì tăng counter lần nữa.

Tạo file `IdempotencyKeyDemo.java`:

```java
import java.util.HashMap;
import java.util.Map;

public class IdempotencyKeyDemo {

    private static final class OutcomeUnknownException
            extends RuntimeException {
        OutcomeUnknownException(String message) {
            super(message);
        }
    }

    private static final class PaymentService {
        private final Map<String, Integer> committed = new HashMap<>();
        private int charges;

        int charge(String operationId, int amount, boolean loseResponse) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }

            Integer previous = committed.get(operationId);
            if (previous != null) {
                if (previous != amount) {
                    throw new IllegalArgumentException(
                            "same key with different amount");
                }
                return previous;
            }

            charges++;
            committed.put(operationId, amount);

            if (loseResponse) {
                throw new OutcomeUnknownException("response lost after commit");
            }
            return amount;
        }

        int chargeCount() {
            return charges;
        }
    }

    public static void main(String[] args) {
        PaymentService service = new PaymentService();

        try {
            service.charge("order-42", 50, true);
        } catch (OutcomeUnknownException ex) {
            System.out.println("client: outcome unknown");
        }

        int result = service.charge("order-42", 50, false);
        System.out.println("retry result: " + result);
        System.out.println("charges: " + service.chargeCount());

        try {
            service.charge("order-42", 75, false);
        } catch (IllegalArgumentException ex) {
            System.out.println("key conflict: rejected");
        }

        service.charge("order-43", 50, false);
        System.out.println("charges after new order: " + service.chargeCount());
    }
}
```

Chạy với Java 17+:

```bash
javac -encoding UTF-8 IdempotencyKeyDemo.java
java IdempotencyKeyDemo
```

Kết quả:

```text
client: outcome unknown
retry result: 50
charges: 1
key conflict: rejected
charges after new order: 2
```

Giải thích:

- Lần đầu với `order-42` thực hiện `charges++` và lưu kết quả, **sau đó** giả lập mất response bằng `OutcomeUnknownException`.
- Retry bằng **cùng key `order-42` và amount 50** phát hiện kết quả đã có, trả `50` mà không tăng số lần charge.
- Cùng key nhưng amount khác bị từ chối; đó là bảo vệ chống reuse key sai payload.
- Một operation mới với `order-43` tăng counter lên 2, đúng vì đây là yêu cầu khác.
- **Đây chỉ là mô hình một luồng, trong bộ nhớ.** `HashMap`/counter không thread-safe, không được bảo vệ bởi transaction bền vững và sẽ mất dữ liệu khi restart. Production phải bảo đảm dedup có tính nguyên tử khi nhiều request cùng key đến đồng thời, khóa key theo tenant/context, lưu trạng thái/kết quả theo thời hạn và đồng bộ với commit nghiệp vụ.
- Ví dụ cũng giả định việc “charge” và ghi `committed` xảy ra như một bước logic; với DB/payment provider thật, hai hoạt động phải được phối hợp bằng transaction, unique constraint hoặc API idempotency của provider, không chỉ thêm một `HashMap` tại client.

**Một timeout sau commit không thể được giải quyết an toàn chỉ bằng việc retry cùng payload** nếu phía server không có hợp đồng idempotency/dedup. Ví dụ này minh họa chính điều kiện bảo đảm thêm mà server phải cung cấp.

## 9. Idempotency không phải chỉ là xử lý duplicate sau khi đã gây hại

Trong server đa luồng, mẫu kiểm tra rồi ghi không nguyên tử là lỗi:

```java
if (!processedIds.contains(id)) {
    performSideEffect();
    processedIds.add(id);
}
```

Đây là snippet minh họa anti-pattern. Hai request song song có thể cùng thấy ID chưa tồn tại và **cùng thực hiện side effect**, trước khi bên nào kịp thêm ID.

Cần thiết kế theo từng trường hợp:

- **Unique constraint + transaction:** key của operation và cập nhật trạng thái nghiệp vụ cùng một transaction, tránh trùng ở DB.
- **Lock/coordinator theo key:** tránh thực thi song song cùng operation; phải xử lý crash/restart và phân tán nhiều node.
- **Trạng thái `IN_PROGRESS`/`COMPLETED`/`FAILED`** có nghĩa rõ, tránh một request khác diễn lại khi attempt đầu chưa kết thúc.
- **Lưu response hoặc resource ID:** có thể trả kết quả nhất quán cho duplicate.
- **Quy tắc payload mismatch:** cùng key khác input bị reject thay vì xem là cùng thao tác.
- **Retention/expiration:** nếu key hết hạn mà caller vẫn retry, nguy cơ thực hiện lại phải được tài liệu hóa.
- **Tenant isolation:** key `order-42` của hai khách hàng không được đụng nhau nếu chúng là operation độc lập.

Trong message queue, consumer nên xử lý delivery trùng bằng message/operation ID. Retry và dedup là hai nửa của thiết kế; một bên thiếu có thể làm hệ thống đúng trong healthy path nhưng sai khi failure.

## 10. Backoff: đừng retry dồn dập

Ngay cả với lỗi transient và thao tác idempotent, retry liên tục ngay sau mỗi exception có thể tạo **retry storm**. Backoff tăng dần giúp dependency có thời gian hồi phục.

Cách đơn giản là exponential backoff có trần:

\[
d_k = \min(d_{\max},\, d_0 \times 2^{k-1})
\]

Trong đó `d_0` là delay ban đầu, `d_{\max}` là trần, `k` là lần **retry sau một attempt đã thất bại**, bắt đầu từ 1. Ví dụ `d_0 = 100 ms`, trần `500 ms`:

| Retry thứ | Delay chưa jitter |
| --- | ---: |
| 1 | 100 ms |
| 2 | 200 ms |
| 3 | 400 ms |
| 4 | 500 ms (capped) |
| 5 trở đi | 500 ms (capped) |

Không tính delay bằng phép nhân không kiểm soát nếu số attempt lớn vì có thể tràn số nguyên. Production nên dùng hàm giới hạn, dữ liệu `long` và ràng buộc thời gian phù hợp.

### Jitter chống đồng bộ retry

Nếu hàng nghìn client gặp lỗi cùng lúc và đều đợi chính xác 100 ms, sau 100 ms họ lại đồng loạt đánh vào server. **Jitter** thêm độ ngẫu nhiên vào delay để các attempt phân tán hơn.

Một chiến lược là *full jitter* với delay chọn ngẫu nhiên trong khoảng `[0, cappedDelay]`. Không có một công thức jitter duy nhất tối ưu cho mọi hệ thống; yêu cầu chính là có phân bố hợp lý, nằm trong deadline và được giám sát.

Đoạn code **minh họa**, không độc lập:

```java
long cappedDelayMillis = Math.min(maxDelayMillis, exponentialMillis);
long actualDelayMillis =
        ThreadLocalRandom.current().nextLong(cappedDelayMillis + 1);
Thread.sleep(actualDelayMillis);
```

Giả định `maxDelayMillis` và `exponentialMillis` hợp lệ, không âm, `cappedDelayMillis + 1` không tràn và có import `java.util.concurrent.ThreadLocalRandom`. `Thread.sleep` có thể ném `InterruptedException`; không nuốt interruption chỉ để hoàn thành retry. Trong hệ thống event-loop/async, ưu tiên scheduler thay vì block worker bằng `Thread.sleep`.

Cần ưu tiên hướng dẫn của server như HTTP `Retry-After` khi có và được API tin cậy hỗ trợ; vẫn áp dụng deadline, trần và kiểm tra an toàn lặp. Không bỏ qua retry hint rồi tạo backoff riêng trái hợp đồng.

## 11. Ví dụ Java 17: tính lịch backoff không chờ thật

Để minh họa delay có trần mà không phụ thuộc timing/scheduling, chương trình chỉ **tính và in lịch**, không gọi sleep hay network.

Tạo file `BackoffScheduleDemo.java`:

```java
public class BackoffScheduleDemo {

    static long cappedExponentialMillis(
            int retryNumber, long baseMillis, long maxMillis) {
        if (retryNumber < 1 || baseMillis < 0 || maxMillis < 0) {
            throw new IllegalArgumentException("invalid retry config");
        }

        long delay = Math.min(baseMillis, maxMillis);

        for (int i = 1; i < retryNumber; i++) {
            if (delay >= maxMillis) {
                break;
            }
            delay = delay > maxMillis / 2
                    ? maxMillis
                    : Math.min(maxMillis, delay * 2);
        }
        return delay;
    }

    public static void main(String[] args) {
        for (int retry = 1; retry <= 5; retry++) {
            System.out.println(
                "retry " + retry + ": "
                    + cappedExponentialMillis(retry, 100, 500) + " ms");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 BackoffScheduleDemo.java
java BackoffScheduleDemo
```

Stdout:

```text
retry 1: 100 ms
retry 2: 200 ms
retry 3: 400 ms
retry 4: 500 ms
retry 5: 500 ms
```

Hàm không triển khai jitter, retry policy đầy đủ hay scheduler; nó chỉ chứng minh quy tắc delay có trần với input mẫu. Trong production, thêm điều kiện không ngủ vượt **remaining deadline**, đo elapsed time bằng nguồn thời gian phù hợp và kiểm tra cancellation.

## 12. Retry limit, retry budget và deadline

Đừng chỉ giới hạn `maxAttempts`. Một chính sách retry thực tế thường cần **cả ba loại giới hạn**:

**1. Giới hạn attempt:** tổng số lần gọi (bao gồm lần đầu) không vượt mức cho phép. `maxAttempts=3` nghĩa là lần đầu + tối đa hai lần retry, không phải ba lần retry thêm.

**2. Deadline tổng:** chỉ tiếp tục nếu còn đủ thời gian để attempt và trả kết quả cho caller. Nếu request còn 50 ms nhưng backoff tối thiểu 200 ms, retry vô ích hoặc vi phạm deadline.

**3. Retry budget toàn hệ thống:** giới hạn số retry phát sinh so với lưu lượng thành công/hệ thống nhằm tránh khuếch đại tải lên dependency. Một dịch vụ có hàng nghìn request cùng retry 3 lần có thể gần như nhân bốn số cuộc gọi khi sự cố; cần giới hạn bổ sung theo host/tenant/operation hoặc global retry budget.

Ví dụ khái niệm:

```text
Request budget: 2.0 s
Attempt 1: 350 ms → transient failure
Backoff:   100 ms
Attempt 2: 400 ms → transient failure
Backoff:   200 ms
Remaining: 950 ms (trước overhead khác)
Attempt 3: chỉ bắt đầu nếu estimated timeout + xử lý response
           vẫn phù hợp với remaining deadline
```

Các thời gian trong ví dụ là giả định minh họa, không là số đo. Chính sách có thể dùng per-attempt timeout ≤ remaining deadline và **không cho phép retry** nếu việc gọi lại không còn lợi ích.

Không dùng riêng `Thread.sleep` để thực thi deadline: nó chỉ trì hoãn thread. Thời hạn phải được truyền/kiểm tra ở các tầng gọi network/DB hoặc workflow phù hợp.

## 13. Interruption và cancellation: retry không được chống shutdown

`InterruptedException` thường đại diện cho yêu cầu dừng một thao tác chờ. Nếu code retry nuốt exception này rồi tiếp tục `sleep`/gọi network, nó có thể làm shutdown không hoàn tất.

Ví dụ không nên áp dụng:

```java
for (;;) {
    try {
        doBlockingCall();
        break;
    } catch (InterruptedException ignored) {
        // Sai: bỏ tín hiệu ngắt và thử lại mãi.
    }
}
```

Đây là snippet minh họa, `doBlockingCall` giả định. Với API có thể khai báo `throws InterruptedException`, tốt nhất thường là truyền tiếp lên caller đủ quyền quyết định. Nếu phải bọc vào exception khác, thường cần **phục hồi interrupt flag**:

```java
try {
    doBlockingCall();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new OperationCancelledException("operation interrupted", ex);
}
```

`OperationCancelledException` là kiểu minh họa, chưa được định nghĩa trong đoạn độc lập. Điều cốt lõi là không coi interruption là transient failure để retry.

Một `Future.get(timeout)` ném `TimeoutException` chỉ báo **caller hết thời gian chờ**; task có thể vẫn chạy. `Future.cancel(true)` có thể yêu cầu interrupt worker theo implementation, nhưng không đảm bảo worker đã dừng hoặc cleanup hoàn tất. Nếu retry một task mới trong khi task cũ vẫn có side effect, cần idempotency/cancellation acknowledgement hoặc đối soát.

Với `CompletableFuture`, `orTimeout` có thể làm future completed exceptionally nhưng **không đảm bảo công việc nền được dừng**. `exceptionally(error -> retry().join())` có thể block thread callback và làm mất kiểm soát deadline; ưu tiên composition async và scheduler có giới hạn khi cần.

## 14. Phân loại IOException, HTTP, database và nghiệp vụ

Không nên viết policy kiểu:

```java
catch (IOException | RuntimeException ex) {
    // Retry tất cả.
}
```

Đây là snippet minh họa anti-pattern. Một chính sách tốt dựa vào **contract của dependency** và đặc điểm operation:

### HTTP/network

| Tín hiệu | Có thể xử lý | Điều kiện bổ sung |
| --- | --- | --- |
| HTTP 429 / tín hiệu rate limit | Thử lại sau khoảng server chỉ định khi phù hợp | `Retry-After`, quota, deadline, concurrency limit, idempotency. |
| HTTP 503 / server unavailable | Có thể transient | Backoff, jitter, tránh retry storm; write phải idempotent/dedup. |
| HTTP 500 | Có thể transient hoặc bug phía server | Không mặc nhiên retry POST có side effect; kiểm tra API contract. |
| HTTP 400 validation | Thường permanent với cùng payload | Sửa request, không tự retry y nguyên. |
| HTTP 401/403 | Thường cần xử lý authentication/authorization riêng | Refresh token chỉ nếu policy cho phép, không lặp vô hạn. |
| HTTP 404 | Có thể permanent hoặc tạm trong eventual consistency | Phụ thuộc resource workflow và hợp đồng. |
| HTTP 409 | Có thể là conflict yêu cầu đọc lại state | Retry nguyên trạng chưa chắc giúp gì; cần optimistic concurrency policy. |
| Connection lost / read timeout | Có thể outcome không rõ | Kiểm tra request có thể đã được xử lý, idempotency key hoặc đối soát. |

Không nên chỉ dùng tên `SocketTimeoutException` để kết luận an toàn khi retry: timeout lúc kết nối khác timeout lúc đợi response sau khi server đã nhận request. Điều kiện truyền dữ liệu và semantics phía remote mới quyết định.

### SQL/database

- **Deadlock/serialization conflict:** nhiều database cho phép thử lại toàn bộ transaction sau rollback theo loại lỗi/SQLState phù hợp. Cần đảm bảo transaction cũ đã rollback và mọi side effect ngoài DB không bị lặp sai.
- **Unique constraint violation:** với cùng dữ liệu thường permanent; nếu dùng unique key để dedup, có thể là tín hiệu truy vấn lại kết quả operation, **không** phải blindly retry insert.
- **Connection failure:** có thể transient nhưng outcome của transaction đôi khi không rõ khi mất connection gần thời điểm commit. Cần thiết kế recovery/transaction ID.
- **Syntax error/schema mismatch:** thường cần sửa code/migration; retry vô hạn không có ích.
- **Lock timeout:** có thể thử lại có giới hạn, nhưng cần hiểu transaction isolation, deadlock policy và tác động lên concurrent load.

JDBC cung cấp các subtype exception như `SQLTransientException` và `SQLNonTransientException` để gợi ý về khả năng retry. Dù vậy, nhà cung cấp driver và operation cụ thể vẫn quan trọng; **không xem subclass là quyền retry không điều kiện**, nhất là nếu dữ liệu đã commit hoặc thao tác gồm nhiều side effect.

### Nghiệp vụ

Một `InsufficientBalanceException` có thể không giải quyết được bằng retry vài mili giây với cùng tài khoản, trong khi `TemporaryInventoryLockException` có thể thử lại sau khi contention giảm. Hãy thiết kế các loại lỗi/mã lỗi nghiệp vụ theo ý nghĩa của chúng, không quyết định retry bằng kiểm tra mọi `RuntimeException`.

## 15. Không retry lỗi lập trình và lỗi tài nguyên nghiêm trọng

`NullPointerException`, `ClassCastException`, `AssertionError`, `OutOfMemoryError` thường không phải tín hiệu thích hợp để retry cùng code/path. Cố retry có thể gây:

- Lặp lại bug liên tục.
- Tạo thêm allocation trong tình trạng bộ nhớ cạn.
- Mất khả năng chẩn đoán do log storm.
- Giữ tài nguyên/cơ chế lock ở trạng thái không xác định.
- Làm health check báo hoạt động bình thường dù thành phần lõi đã hỏng.

Không nên bắt `Throwable` để retry. `Throwable` bao gồm `Error`; nhiều error biểu thị trạng thái runtime nghiêm trọng và không được thiết kế làm tín hiệu phục hồi nghiệp vụ.

Lỗi do `InterruptedException` thuộc kênh cancellation; trả quyền quyết định cho lifecycle owner. Một `CancellationException` từ `Future` cũng thường là trạng thái hủy cần được bảo lưu chứ không chuyển thành “lỗi tạm thời” để chạy lại.

Nếu một bug thật sự được phát hiện, sửa code và test; đừng thêm `catch(RuntimeException)` để làm triệu chứng biến mất.

## 16. Mọi attempt phải cleanup tài nguyên đúng owner

Nếu một attempt mở stream/connection/subscription rồi ném exception, retry sẽ mở thêm tài nguyên. Không cleanup attempt trước có thể làm rò rỉ nhanh hơn với mỗi vòng lặp.

Cấu trúc minh họa:

```java
for (int attempt = 1; attempt <= maxAttempts; attempt++) {
    try (ConnectionHandle owned = acquireConnection()) {
        return performOperation(owned);
    } catch (TransientConnectionException ex) {
        if (attempt == maxAttempts) {
            throw ex;
        }
        // Chỉ retry nếu operation an toàn khi lặp.
    }
}
throw new AssertionError("unreachable");
```

Đây là đoạn **minh họa**, với `ConnectionHandle` triển khai `AutoCloseable` và các symbol còn lại được định nghĩa trong ứng dụng. `try-with-resources` đóng handle của từng attempt **trước** khi quyết định retry. Không đóng pool dùng chung nếu connection được mượn; chỉ release/close handle theo contract pool.

Nếu `close()` cũng ném, cần giữ thông tin primary/suppressed exception. Nếu retry ngay sau lỗi cleanup có thể làm trạng thái không xác định, phải phân loại lỗi cleanup và tính an toàn lặp, không đơn giản xem nó là transient.

Trong hệ thống nhiều thao tác, hãy định nghĩa rõ **ranh giới retry**: thử lại cả transaction hay chỉ câu lệnh? Đã commit một phần chưa? Nếu một operation gồm ghi DB rồi gửi email, retry toàn operation có thể gửi email trùng dù DB đã rollback hay ngược lại.

## 17. Retry nhiều tầng tạo tải khuếch đại

Giả sử một request đi qua ba service A → B → C. Nếu mỗi tầng tự retry tối đa 3 attempt, trong trường hợp xấu, một request bên ngoài có thể tạo tới:

\[
3 \times 3 \times 3 = 27
\]

lần gọi tới thao tác sâu nhất, nếu mỗi tầng retry khi downstream báo lỗi. Đây là **giới hạn minh họa** trong cấu trúc ba tầng, không là con số bắt buộc cho mọi kiến trúc.

Khi dependency đang lỗi, multiplicative retries có thể làm sự cố trầm trọng. Các cách giảm:

- Quy định **tầng sở hữu retry** ở boundary phù hợp, tránh mỗi thư viện/adapter tự retry mù.
- Truyền tổng deadline và **retry budget** qua các tầng.
- Dùng circuit breaker/rate limiting/bulkhead khi cần, với chính sách có thể kiểm chứng.
- Giới hạn concurrency và độ dài queue.
- Tôn trọng `Retry-After`/backpressure của server.
- Thu metrics theo attempt, request và dependency; không chỉ đếm request lỗi.

Circuit breaker không phải phép màu sửa nghiệp vụ; nó chủ yếu giúp chặn bớt lưu lượng tới dependency có tín hiệu lỗi để giảm thiệt hại. Khi breaker mở, caller cần nhận lỗi/response rõ ràng thay vì tự tạo vòng lặp khác vượt qua breaker.

## 18. Retry, fallback và trạng thái “chưa biết”

Ba phản ứng sau khác nhau:

**Retry:** gọi lại tác vụ, có thể tăng số side effect; chỉ an toàn khi đủ điều kiện.

**Fallback:** trả kết quả dự phòng có hợp đồng rõ, ví dụ danh sách gợi ý trống khi recommendation service unavailable. Fallback không đồng nghĩa đã thực hiện thành công thao tác gốc.

**Unknown/pending:** chưa xác minh kết quả cuối của thao tác có side effect. Có thể cần trả operation ID để client query status, thay vì nói thành công/thất bại sai.

Ví dụ một API thanh toán nên phân biệt:

```text
SUCCESS     — giao dịch được xác nhận
FAILED      — đã biết giao dịch thất bại
PENDING     — đang xử lý/chưa có kết quả cuối
UNKNOWN     — không chắc remote đã commit hay chưa; cần đối soát
```

Đây là các trạng thái **minh họa**; tên và contract thực tế thuộc hệ thống ứng dụng. Retry bừa có thể biến UNKNOWN thành hai khoản ghi thành công, còn fallback `SUCCESS` giả có thể gây sai dữ liệu.

Đối với read-only operation, fallback cache có thể có nghĩa rõ nếu dữ liệu còn hợp lệ. Đối với write có tác động quan trọng, cần tránh “phục hồi bằng giá trị mặc định” che mất kết quả chưa được xác nhận.

## 19. Retry bất đồng bộ và CompletableFuture

Khi dùng `CompletableFuture`, lỗi có thể được lưu trong stage dưới dạng exceptional completion. Một chuỗi:

```java
serviceCall()
    .exceptionally(error -> serviceCall().join());
```

là **mảnh minh họa anti-pattern**: `serviceCall()` giả định trả `CompletableFuture<T>`. Lambda `exceptionally` có thể thực thi trên thread completion, gọi `join()` blocking và gây nghẽn/deadlock nếu executor thiếu thread. Nó cũng không phân loại lỗi, không có giới hạn attempt và có thể lặp side effect.

Đối với fallback sang một nguồn async khác, Java 17 có `exceptionallyCompose` để nối stage mà không phải block bằng `join()`. Tuy nhiên, `exceptionallyCompose` **tự nó không phải retry framework**: nếu dùng để gọi lại operation, vẫn cần phân loại lỗi, giới hạn attempts, deadline, jitter/backoff scheduler, cancellation và idempotency.

Một cách tổ chức thực tế là để bộ phận có quyền sở hữu workflow quản lý state machine retry (attempt count, last failure, remaining deadline), tạo attempt mới khi được phép và hoàn thành một `CompletableFuture` tổng sau khi success/final failure/cancel. Sử dụng scheduled executor có lifecycle rõ thay vì `Thread.sleep` blocking trong callback.

Khi caller của `Future.get(timeout)` bị `TimeoutException`, chỉ **lần chờ** hết hạn; stage/task nền có thể còn chạy. Đừng tạo attempt mới với side effect nếu chưa giải quyết quyền hủy, result tracking và idempotency.

## 20. Logging và quan sát retry đúng cách

Nếu mỗi attempt lỗi đều in stack trace đầy đủ rồi tầng ngoài lại log khi hết attempts, một request có thể sinh hàng chục bản ghi cùng nguyên nhân. Khi dependency lỗi diện rộng, điều này có thể tạo log storm và làm chậm hệ thống.

Nên ghi nhận:

| Metric/thuộc tính | Mục đích |
| --- | --- |
| `operation_id` / idempotency key an toàn | Nối các attempt của cùng một operation để đối soát. |
| `attempt_number` và `max_attempts` | Phân biệt lần đầu với retry. |
| `retry_reason` / mã lỗi | Hiểu vì sao policy cho phép retry. |
| `retry_outcome` | Thành công sau retry, exhausted, permanent, canceled, deadline exceeded. |
| Backoff delay | Phân tích latency và sự đồng bộ retry. |
| Retry amplification | Số attempt trên mỗi request, theo downstream. |
| Pending/unknown outcomes | Theo dõi các operation cần đối soát. |
| Circuit state và queue saturation | Nhận diện hệ thống đang được bảo vệ hay bị overload. |

Tầng retry có thể ghi metric cho **mỗi attempt**, nhưng stack trace đầy đủ thường nên log chọn lọc theo chính sách ở boundary có ngữ cảnh, không nhất thiết một lần cho mọi attempt. Khi log mẫu, vẫn cần metrics đếm toàn bộ lỗi để không đánh giá thấp sự cố.

Không dùng toàn bộ exception message, URL chứa token, payload thanh toán hoặc ID cá nhân làm metric label/log key; chọn mã lỗi và trường correlation được bảo vệ.

Khi một attempt cuối thất bại, giữ `cause` và đủ ngữ cảnh attempt để điều tra. Không bọc mọi lỗi thành `RuntimeException("retry failed")` rồi bỏ nguyên nhân gốc.

## 21. Tính nhất quán của retry với transaction

Giả sử transaction làm hai việc: ghi đơn hàng và phát sự kiện “order created”. Nếu ghi DB thành công nhưng phát sự kiện thất bại, retry cả luồng có thể tạo đơn trùng hoặc phát nhiều sự kiện.

Các phương án tùy kiến trúc:

- **Transaction DB đúng ranh giới:** với deadlock có thể rollback rồi thử lại *toàn transaction*. Cần biết phần việc nào nằm trong transaction.
- **Transactional outbox:** ghi business state và outbox event trong cùng transaction; worker phát event có thể retry gửi, consumer dùng dedup.
- **Optimistic concurrency:** mỗi lần conflict có thể cần đọc version mới, tính lại quyết định rồi ghi; retry nguyên payload cũ có thể cứ fail.
- **Idempotency token:** server biết cùng một operation ID đã xử lý và trả kết quả ổn định.
- **SAGA/compensation:** nếu nhiều hệ không thể có transaction chung, workflow điều phối trạng thái và bù khi cần; retry chỉ một hành động có rủi ro riêng.

Đừng thiết kế retry trước rồi mới hỏi “có side effect không?”. **Tính chất nghiệp vụ và giao dịch quyết định chính sách retry**, không phải ngược lại.

## 22. Trường hợp đáng retry nhưng không nên tự động retry ngay

Có những tình huống lỗi có thể hồi phục, nhưng tự động retry vẫn không phù hợp:

- Giao dịch chi tiền có outcome không rõ và không có idempotency key.
- Lỗi rate limit đòi đợi 60 giây nhưng deadline caller còn 2 giây.
- Dịch vụ đang trong maintenance với retry hint dài hơn ngân sách request.
- Caller đã hủy request hoặc service đang shutdown.
- Dependency có dấu hiệu quá tải, retry budget đã cạn.
- Operation có side effect không bù được hoặc vi phạm điều kiện duy nhất khi lặp.
- Lỗi có thể khắc phục bằng yêu cầu người dùng xác nhận thay đổi dữ liệu; retry tự động không làm được việc đó.
- Một attempt đã vượt thời hạn toàn workflow và công việc cũ có thể vẫn chạy.

Ở đây hành động đúng có thể là **trả trạng thái pending**, **đưa job vào hàng đợi bền vững**, **yêu cầu đối soát**, **cảnh báo vận hành** hoặc **dừng với lỗi có ý nghĩa**, không phải ném thêm một request lập tức.

## 23. Kiểm thử retry policy theo tình huống

Bộ kiểm thử tốt phải xác nhận cả hai điều: **lần nào được retry** và **lần nào tuyệt đối không được retry**.

| Tình huống | Kỳ vọng |
| --- | --- |
| Transient lỗi hai lần rồi thành công | Tổng 3 attempts, một kết quả thành công, không tiếp tục retry. |
| Permanent validation failure | Chỉ 1 attempt, truyền lỗi rõ ràng. |
| Transient tiếp tục lỗi đến giới hạn | Đúng `maxAttempts`, sau đó trả failure cuối. |
| Deadline hết trước attempt tiếp | Không gọi tiếp, báo deadline exhausted. |
| Backoff có trần | Delay không vượt trần và không vượt remaining deadline. |
| 1000 caller đồng thời transient failure | Jitter/limit giúp tránh cùng retry một lúc; đo số attempt và queue depth. |
| Caller/worker bị interruption | Kết thúc theo cancellation policy, phục hồi/truyền interrupt khi phù hợp. |
| Server commit nhưng response mất | Retry với cùng idempotency key không tạo thêm side effect. |
| Hai retry cùng key đồng thời | Server không chạy side effect hai lần; duplicate nhận kết quả hoặc trạng thái đúng. |
| Cùng key với payload khác | Bị từ chối/được xử lý theo contract, không lặng lẽ coi là trùng hợp lệ. |
| Idempotency key hết thời gian lưu | Có contract rõ về rủi ro và thời hạn retry. |
| Database deadlock | Retry đúng ranh giới transaction sau rollback, không nhân đôi tác động ngoài transaction. |
| Retry bị thiếu tài nguyên | Mỗi attempt cleanup tài nguyên đã acquire, không leak connection/permit. |
| Exception không được phân loại | Fail/propagate, không tự retry mọi `RuntimeException`. |
| Log/metrics của retry | Biết tổng attempts, outcomes, không spam stack trace hoặc lộ dữ liệu. |

Khi test concurrency/idempotency, dùng latch/barrier điều khiển hai request cùng key chạy đồng thời; đừng chỉ chạy tuần tự rồi kết luận server đã an toàn trong race.

Khi test backoff, tốt hơn nên **inject clock/scheduler** hoặc tính delay thuần để có test quyết định; không dựa vào `Thread.sleep(100)` và thời gian thực của CI. Đối với I/O thật, dùng fake adapter cho lỗi trước gửi, lỗi sau commit và mất response để kiểm tra outcome.

## 24. Những anti-pattern thường gặp

**Retry mọi `Exception` hoặc `Throwable`.** Sai vì permanent error, cancellation, bug và resource exhaustion có ý nghĩa khác nhau. Phân loại theo contract, không bắt rộng tùy tiện.

**Retry mọi HTTP 5xx hoặc `IOException` cho thao tác ghi.** Không đủ: request có thể đã commit, response có thể mất. Cần idempotency/dedup hoặc đối soát.

**Retry cùng payload khi validation fail.** Không thay đổi nguyên nhân; cần sửa input.

**Retry ngay, không delay.** Có thể làm quá tải dependency hơn, tạo đồng bộ giữa các client.

**Dùng backoff nhưng không có maxAttempts/deadline.** Có thể kéo dài workflow vô hạn và giữ tài nguyên.

**Retry ở nhiều tầng không phối hợp.** Tải có thể nhân lên theo tích số attempts ở mỗi tầng.

**Tạo idempotency key mới ở mỗi lần retry.** Biến duplicate thành operation mới; có nguy cơ thực hiện side effect nhiều lần.

**Dùng `HashMap` check-then-write trong server đa luồng như thể đã exactly-once.** Có race/crash/restart; cần atomic/durable dedup theo operation.

**Coi timeout là failure chắc chắn của server.** Timeout chỉ có thể báo mất khả năng quan sát kết quả.

**Nuốt `InterruptedException` để tiếp tục retry.** Chống lại shutdown/cancellation; có thể làm service treo.

**Dùng `CompletableFuture.exceptionally(...join())` để “retry async”.** Có thể block thread callback và thiếu policy đầy đủ.

**Ghi stack trace mọi attempt.** Log storm, tăng CPU/I/O và giảm khả năng quan sát.

**Không cleanup sau attempt lỗi.** Mỗi retry nhân thêm rò rỉ tài nguyên.

**Chỉ kiểm tra kết quả cuối mà không kiểm tra số attempts.** Một test “cuối cùng thành công” vẫn có thể che việc retry 1000 lần hoặc gửi email trùng.

**Nhầm retry và fallback.** Fallback là kết quả dự phòng, không chứng minh operation gốc đã thành công.

## 25. Bài tập tự kiểm tra

**Câu 1.** Một API trả `IllegalArgumentException("quantity must be positive")`. Caller retry 5 lần cùng quantity -2. Có ích không?

**Đáp án:** Không. Đây là lỗi đầu vào permanent; không có thay đổi nào giữa các lần gọi.

**Câu 2.** Client gửi lệnh chuyển tiền, server commit rồi response mất. Caller nhận `SocketTimeoutException`. Có thể retry ngay với request mới không?

**Đáp án:** Chưa an toàn. Outcome không rõ; cần idempotency key cố định cho cùng operation hoặc truy vấn/đối soát trạng thái. Gửi request mới không dedup có thể chuyển tiền hai lần.

**Câu 3.** Một operation read-only gặp `TemporaryServiceUnavailableException`. Caller còn deadline 3 giây, server gửi `Retry-After: 10` giây. Nên làm gì?

**Đáp án:** Không đợi rồi retry trong request 3 giây. Tôn trọng deadline, trả lỗi/trạng thái phù hợp hoặc chuyển sang workflow background có quyền sở hữu riêng nếu contract cho phép.

**Câu 4.** Một tầng A retry 3 attempts, B retry 3 và C retry 3. Khi mọi lần thất bại, C có thể nhận tối đa bao nhiêu attempts cho một request A?

**Đáp án:** Theo mô hình lồng nhau đơn giản có thể tới `3 × 3 × 3 = 27` attempts ở tầng sâu. Đây là lý do phải phối hợp retry budget và ownership.

**Câu 5.** `Future.get(timeout)` ném `TimeoutException`. Task nền đã ngừng và rollback chưa?

**Đáp án:** Không thể kết luận. Đây là timeout của thao tác chờ; task có thể đang chạy hoặc đã tạo side effect.

**Câu 6.** `retryTransient` trong `SelectiveRetryDemo` được gọi với `maxAttempts=3` và task lỗi transient hai lần rồi thành công. Có bao nhiêu lần retry *sau* lần gọi đầu?

**Đáp án:** Hai lần retry; tổng ba attempts.

**Câu 7.** Server dùng `processedIds.contains(id)` rồi `performSideEffect()` và `processedIds.add(id)`. Hai request cùng id chạy đồng thời có an toàn không?

**Đáp án:** Không. Cả hai có thể thấy ID chưa tồn tại và cùng thực hiện side effect. Cần dedup/transaction/lock có bảo đảm nguyên tử theo key và môi trường triển khai.

**Câu 8.** Một `InterruptedException` ném trong `Thread.sleep(backoff)`. Retry loop nên bắt rồi sleep lại đến hết attempts?

**Đáp án:** Không. Cần tôn trọng tín hiệu ngắt; truyền tiếp `InterruptedException` hoặc phục hồi cờ rồi chấm dứt theo lifecycle policy.

## 26. Checklist trước khi thêm retry vào Java API

- [ ] Đã phân loại lỗi transient, permanent, ambiguous outcome, cancellation và bug.
- [ ] Chỉ retry **những lỗi được API cho phép** và các operation an toàn khi lặp.
- [ ] Biết side effect đã xảy ra trước điểm lỗi hay chưa, hoặc có cơ chế idempotency/đối soát.
- [ ] Cùng operation dùng cùng idempotency key; server bảo đảm dedup nguyên tử và có lưu trạng thái phù hợp.
- [ ] Có maxAttempts rõ, phân biệt attempt tổng và số retry thêm.
- [ ] Có deadline tổng và timeout từng attempt không vượt remaining time.
- [ ] Có bounded backoff, jitter và tôn trọng retry hint phù hợp.
- [ ] Có retry budget, concurrency limit/circuit breaker khi dependency quá tải.
- [ ] Không nuốt interruption/cancellation; không retry khi caller đã dừng.
- [ ] Mỗi attempt cleanup owned resources và rollback/compensation đúng ranh giới.
- [ ] Không retry nhiều tầng không phối hợp.
- [ ] Ghi metrics theo request và attempt, không double-log full stack trace vô ích.
- [ ] Có test permanent error chỉ một attempt, transient hồi phục, exhaustion và timeout.
- [ ] Có test mất response **sau commit**, duplicate đồng thời và reuse key sai payload.
- [ ] Đã kiểm tra correctness, duplicate side effect, p95/p99 latency và tải dependency trong error burst.

## 27. Kết luận

**Không phải mọi exception đều nên retry.** Lỗi tạm thời có thể xứng đáng được thử lại, nhưng chỉ khi operation **an toàn khi lặp**, caller còn ngân sách thời gian, và hệ thống không bị khuếch đại tải. Lỗi đầu vào, bug lập trình và tín hiệu hủy thường cần dừng ngay; lỗi mạng sau khi gửi lệnh có side effect còn có thể là **kết quả chưa xác định**, không phải thất bại chắc chắn.

Retry đúng là một **chính sách nghiệp vụ và vận hành**: phân loại lỗi, giới hạn attempts/deadline, backoff có jitter, bảo đảm idempotency hoặc đối soát, cleanup theo ownership và giám sát từng attempt. Làm rõ những điều kiện này trước khi viết vòng lặp `catch/retry` sẽ giúp hệ thống phục hồi khi đáng phục hồi mà không tạo thêm dữ liệu trùng, lỗi dây chuyền hoặc mất khả năng chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-010">Để exception message trở thành định dạng dữ liệu mà mã khác phải phân tích</a>

<details>
<summary>Xem chi tiết</summary>

# Để exception message trở thành định dạng dữ liệu mà mã khác phải phân tích — một sai lầm trong Java

> **Phạm vi:** Java Core → Exception → Sai lầm thường gặp. Bài học giải thích vì sao `Throwable.getMessage()` thường là nội dung dành cho người đọc chứ không phải giao thức ổn định dành cho phần mềm; cách chuyển từ `contains`/`startsWith`/regex trên message sang **kiểu ngoại lệ, error code, các trường có kiểu, nguyên nhân gốc và result type**. Bao gồm ví dụ Java 17 chạy được, hợp đồng API, kiểm thử và chiến lược migration.

## 1. Một câu chữ có thể làm thay đổi cả logic nghiệp vụ

Giả sử một dịch vụ thanh toán gặp lỗi từ chối giao dịch và caller xử lý:

~~~java
// Phản ví dụ; charge() và respond... là các hàm do ứng dụng định nghĩa.
try {
    charge();
} catch (RuntimeException ex) {
    if (ex.getMessage().contains("DECLINED")) {
        return respondPaymentDeclined();
    }
    return respondInternalError();
}
~~~

Với message `"Payment DECLINED for pay-17"`, caller xử lý “bị từ chối”. Nhưng cùng loại lỗi với message `"Thanh toán bị từ chối: pay-17"`, `"Payment was declined"` hoặc `"Card issuer rejected request"` lại có thể bị phân loại thành lỗi hệ thống. Một thay đổi **câu chữ chẩn đoán** đã trở thành thay đổi **hành vi chương trình**.

Đây là anti-pattern **exception message as data contract**: không có trường `errorCode` được công bố, không có schema hay policy tương thích; một tầng vô tình dùng `getMessage()` như định dạng dữ liệu của tầng khác. Lỗi có thể ảnh hưởng HTTP status, retry, UI, cảnh báo, idempotency hoặc quyết định có đảo ngược giao dịch hay không.

**Nguyên tắc:** message phục vụ diễn giải và chẩn đoán; dữ liệu chương trình phải được định nghĩa và tiêu thụ bằng hợp đồng **có cấu trúc và có kiểu**.

## 2. Vì sao getMessage không phải một error code?

`Throwable` là một đối tượng chứa thông tin về lỗi. Những phần của nó có mục đích khác nhau:

| Thuộc tính | Ý nghĩa | Cách sử dụng đúng |
|---|---|---|
| Kiểu Java của exception | Phân loại theo hợp đồng API | `catch (SpecificException ex)`, `instanceof` |
| `getMessage()` | Câu mô tả lỗi, thường hướng tới người đọc | Hiển thị/chẩn đoán sau khi kiểm soát dữ liệu |
| `getLocalizedMessage()` | Mô tả có thể tùy biến theo ngôn ngữ | Không dùng quyết định logic máy |
| `getCause()` | Liên kết lỗi nguyên nhân khi được bọc | Theo dõi nguyên nhân, phân loại kiểu cause khi hợp đồng cho phép |
| `getStackTrace()` | Dấu vết thực thi | Debug, log; không dùng số dòng/tên file làm business code |
| `getSuppressed()` | Lỗi phụ bị suppress, ví dụ lỗi đóng tài nguyên | Chẩn đoán và cleanup; không parse text thành code |
| Trường `code`/`id` do custom exception định nghĩa | Dữ liệu domain có contract | Phân loại, mapping API, correlation |

JDK không có quy tắc chung rằng message của mọi `IllegalArgumentException`, `SQLException` hay `NumberFormatException` giữ nguyên qua các phiên bản. Nếu nhà cung cấp chỉ tài liệu hóa **loại exception và điều kiện ném**, caller không nên tự nâng một câu message thành API contract.

Điều này **không có nghĩa message vô ích**. Message tốt vẫn mô tả thao tác, điều kiện thất bại và context an toàn để con người điều tra. Nó chỉ không phải nguồn chân lý cho nhánh máy đọc, trừ khi nhà cung cấp chính thức định nghĩa grammar ổn định.

## 3. Những cách parse text dễ hỏng

### Dùng contains hoặc startsWith

~~~java
// Chỉ minh họa anti-pattern.
if (ex.getMessage().startsWith("ORDER_NOT_FOUND")) {
    // ...
}
~~~

Các thay đổi prefix, chữ hoa-thường, dấu cách, locale hoặc tầng wrapper đều có thể làm lệch kết quả. Nếu message null, lời gọi `contains` còn có thể ném `NullPointerException` và che lỗi ban đầu.

### Dùng split để lấy business ID

~~~java
// Không nên làm nguồn dữ liệu chính.
String paymentId = ex.getMessage().split(":")[1];
~~~

Message có thể thêm dấu hai chấm, xuống dòng, ID chứa delimiter hoặc thiếu phần tử; parser có thể tạo ID sai, `ArrayIndexOutOfBoundsException` hoặc dữ liệu không an toàn.

### Dùng regex để lấy một con số

~~~java
// Mã minh họa: regex phụ thuộc câu chữ.
java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("row (\d+)").matcher(ex.getMessage());
~~~

Kể cả regex hợp lệ, câu `"row 12 invalid"` có thể trở thành `"invalid record at line 12"`. Nếu vị trí hàng quan trọng, API nên có `rowNumber()` hoặc một `ValidationError` có field `position`, không bắt caller phân tích message.

### Dựa vào lời mô tả của dependency

Bản mới của thư viện có thể đổi `"Connection refused"` thành `"Failed to connect"` hoặc bọc ngoại lệ bằng message khác. Không nên dùng từ `"refused"` trong text để quyết định retry. Nếu SDK có HTTP status, vendor error code hoặc subtype chính thức, dùng những thuộc tính đó.

## 4. Ví dụ Java 17 hoàn chỉnh: bad vs good khi đổi message

Ví dụ sau mô phỏng cùng lỗi thanh toán bị từ chối nhưng hai câu message khác nhau. Cách sai đọc text, còn cách đúng đọc `FailureCode` và giữ `cause` của lỗi kỹ thuật.

Lưu thành **`ExceptionMessageContractDemo.java`**:

~~~java
import java.util.Objects;

public class ExceptionMessageContractDemo {
    enum FailureCode {
        PAYMENT_DECLINED,
        PROVIDER_UNAVAILABLE
    }

    static final class PaymentFailure extends RuntimeException {
        private final FailureCode code;
        private final String paymentId;

        PaymentFailure(FailureCode code, String paymentId,
                       String description, Throwable cause) {
            super(description, cause);
            this.code = Objects.requireNonNull(code, "code");
            this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        }

        FailureCode code() {
            return code;
        }

        String paymentId() {
            return paymentId;
        }
    }

    static int fragileHttpStatus(RuntimeException error) {
        // Sai: dựa vào thông điệp thay vì phân loại có cấu trúc.
        return error.getMessage().contains("DECLINED") ? 402 : 500;
    }

    static int stableHttpStatus(PaymentFailure error) {
        return switch (error.code()) {
            case PAYMENT_DECLINED -> 402;
            case PROVIDER_UNAVAILABLE -> 503;
        };
    }

    static String publicCode(PaymentFailure error) {
        return switch (error.code()) {
            case PAYMENT_DECLINED -> "payment_declined";
            case PROVIDER_UNAVAILABLE -> "provider_unavailable";
        };
    }

    public static void main(String[] args) {
        RuntimeException english =
                new IllegalStateException("Payment DECLINED for pay-17");
        RuntimeException vietnamese =
                new IllegalStateException("Thanh toán bị từ chối: pay-17");

        System.out.println("bad English: " + fragileHttpStatus(english));
        System.out.println("bad Vietnamese: " + fragileHttpStatus(vietnamese));

        PaymentFailure typed = new PaymentFailure(
                FailureCode.PAYMENT_DECLINED,
                "pay-17",
                "Thanh toán bị từ chối: pay-17",
                new IllegalStateException("gateway response 05"));

        System.out.println("good status: " + stableHttpStatus(typed));
        System.out.println("good code: " + publicCode(typed));
        System.out.println("good paymentId: " + typed.paymentId());

        Throwable cause = typed.getCause();
        System.out.println("good cause: "
                + cause.getClass().getSimpleName()
                + ": " + cause.getMessage());
    }
}
~~~

Biên dịch/chạy với JDK 17+:

~~~bash
javac ExceptionMessageContractDemo.java
java ExceptionMessageContractDemo
~~~

**Stdout chính xác:**

~~~text
bad English: 402
bad Vietnamese: 500
good status: 402
good code: payment_declined
good paymentId: pay-17
good cause: IllegalStateException: gateway response 05
~~~

Diễn giải:

1. `fragileHttpStatus` chỉ nhận diện chữ `DECLINED` tiếng Anh viết hoa. Bản tiếng Việt dẫn đến `500`, dù sự kiện nghiệp vụ không thay đổi.
2. `PaymentFailure` chứa mã lỗi ở enum `FailureCode` và định danh `paymentId` trong hai trường typed.
3. `stableHttpStatus` đọc `error.code()`. Khi cải thiện hoặc bản địa hóa message, quyết định không thay đổi.
4. `publicCode` ánh xạ enum nội bộ thành mã lỗi public có contract rõ nghĩa.
5. `getCause()` giữ exception kỹ thuật gốc mà tầng logging có thể dùng để điều tra; tầng HTTP không cần parse `"gateway response 05"`.
6. Mã trạng thái `402` chỉ minh họa **chính sách của API trong ví dụ**, không khẳng định mọi hệ thống thanh toán đều phải dùng HTTP 402. Ứng dụng thật chọn status dựa trên hợp đồng HTTP/domain đã công bố.
7. Không có framework hoặc thread nền trong chương trình; output ổn định, giúp tập trung vào thiết kế lỗi.

## 5. Typed exception: loại lỗi, mã và context riêng

Khi API vẫn cần ném exception, có thể mô hình hóa thông tin máy đọc bằng một lớp có trường riêng:

~~~java
// Trích đoạn khai báo Java 17; có thể đặt trong một lớp/top-level file.
enum OrderErrorCode {
    NOT_FOUND, ALREADY_PAID, STORAGE_UNAVAILABLE
}

final class OrderOperationException extends RuntimeException {
    private final OrderErrorCode code;
    private final String orderId;

    OrderOperationException(
            OrderErrorCode code, String orderId, Throwable cause) {
        super("order operation failed", cause);
        this.code = java.util.Objects.requireNonNull(code);
        this.orderId = orderId;
    }

    OrderErrorCode code() { return code; }
    String orderId() { return orderId; }
}
~~~

Ở đây `getMessage()` có thể đổi từ `"order operation failed"` sang `"unable to process order"` mà không phá việc đọc `code()` hoặc `orderId()`. Dùng constructor có `cause` đảm bảo trace của lỗi gốc không bị cắt bỏ.

Tuy nhiên, thêm quá nhiều subclass cho mọi error code cũng làm hierarchy khó quản lý. Có thể dùng một số exception type có ý nghĩa abstraction, kèm enum `code` cho phân loại chi tiết. Với API public giữa nhiều dịch vụ, mã lỗi dạng string có schema/version và khả năng xử lý mã chưa biết thường linh hoạt hơn một enum đồng bộ cứng.

### Bảo toàn ngữ nghĩa ở đúng tầng

- Repository biết lỗi kỹ thuật: `SQLException`, I/O exception, driver code.
- Service biết ngữ nghĩa domain: đơn hàng không tồn tại, thanh toán bị từ chối, nguồn phụ thuộc không khả dụng.
- Boundary biết cách map sang HTTP/queue/job outcome và thông điệp public an toàn.

Đừng buộc controller phân tích `SQLException.getMessage()` để quyết định trạng thái HTTP. Repository/service nên chuyển lỗi vào abstraction có cấu trúc, giữ `cause` và thông tin cần điều tra.

## 6. Có phải luôn nên dùng catch theo subtype?

`catch (SpecificException ex)` là cách có kiểu rõ ràng, phù hợp khi thư viện công bố subtype để phân biệt lỗi. Ví dụ:

~~~java
// Trích đoạn minh họa; các lớp/hàm thuộc ứng dụng giả định.
try {
    repository.load(orderId);
} catch (OrderNotFoundException ex) {
    return respondNotFound();
} catch (StorageUnavailableException ex) {
    return respondUnavailable();
}
~~~

Nếu nhiều mã lỗi nằm trong cùng loại domain exception, dùng `switch (ex.code())` có thể tốt hơn tạo nhiều class rỗng:

~~~java
// Trích đoạn; OrderOperationException đã được khai báo ở trên.
switch (ex.code()) {
    case NOT_FOUND -> respondNotFound();
    case ALREADY_PAID -> respondConflict();
    case STORAGE_UNAVAILABLE -> respondUnavailable();
}
~~~

Cần phân biệt **nguyên nhân thật** và **quyết định xử lý**. Một `TimeoutException` không tự bảo đảm tác vụ phía ngoài chưa commit; chính sách retry còn tùy idempotency, attempt budget và trạng thái hệ thống bên ngoài. Một message có chữ “timeout” càng không đủ để quyết định điều đó.

## 7. Result type cho tình huống bị từ chối thường xuyên

Với các kết quả dự kiến của UI/validation/import, exception đôi khi không phải kênh tốt nhất. Dùng sealed result type của Java 17:

~~~java
// Các khai báo kiểu minh họa trong một chương trình Java 17.
sealed interface ParseResult permits Accepted, Rejected { }

record Accepted(int value) implements ParseResult { }

enum RejectReason {
    EMPTY, NOT_A_NUMBER, OUT_OF_RANGE
}

record Rejected(RejectReason reason) implements ParseResult { }
~~~

Caller có thể xử lý theo `Rejected.reason()` thay vì ném `NumberFormatException` rồi parse message để biết “ký tự thứ 7 không hợp lệ”. Nếu cần vị trí, thêm một record field `int position`. Với kết quả chỉ cần có/không, `OptionalInt` hoặc `Optional<T>` có thể đơn giản hơn.

Đừng thay exception bằng `null` hoặc một string chung `"error"` và xem là đã giải quyết: điều đó thường tạo một hợp đồng còn mơ hồ. Mục tiêu là **biểu diễn chính xác thông tin caller cần xử lý**.

## 8. Message có thể thay đổi vì bản địa hóa

Một mã lỗi nên ổn định bất kể locale:

~~~text
code=payment_declined
vi-VN: "Phương thức thanh toán bị từ chối."
en-US: "The payment method was declined."
~~~

Việc thay bản dịch không được khiến `retryable`, HTTP status hay loại outcome đổi theo. Không viết:

~~~java
// Phản ví dụ.
if (ex.getLocalizedMessage().contains("từ chối")) {
    // Quyết định chính sách dựa trên ngôn ngữ hiển thị.
}
~~~

Thay vào đó, định nghĩa catalog `errorCode + locale → displayMessage` hoặc dùng `ResourceBundle`/i18n service ở boundary. Message hiển thị cho người dùng và message nội bộ có thể khác nhau. Đừng trả nguyên message của lỗi SQL, token hay path nội bộ ra API response.

## 9. Đừng parse message của JDK và thư viện

Ví dụ `Integer.parseInt(input)` ném `NumberFormatException` nếu chuỗi sai. Nếu cần biết chính input để hiển thị lỗi, bạn **đã có input**; không cần regex trích lại từ `ex.getMessage()`.

Với JDBC, `SQLException` có `getSQLState()` và `getErrorCode()` ngoài `getMessage()`. Các mã này cần được diễn giải theo contract SQLState/vendor và cấu hình triển khai, nhưng chúng có chủ đích hơn việc regex vào lời mô tả của driver.

Với HTTP client/SDK, dùng response status, provider error code hoặc typed error body khi được hỗ trợ. Một số thư viện không công bố mã máy đọc; trong trường hợp đó, hãy tìm API thích hợp hoặc đặt một adapter giới hạn rủi ro thay vì cho cả ứng dụng phụ thuộc vào wording.

**Không tự suy ra mã ổn định chỉ vì có chuỗi hoặc số.** Error code cũng cần tài liệu, mapping, version và policy đối với code mới.

## 10. Tạo response API với code ổn định và message có thể thay đổi

Một response JSON có cấu trúc nên tách:

~~~json
{
  "code": "payment_declined",
  "message": "Không thể thanh toán bằng phương thức đã chọn.",
  "requestId": "req-42",
  "details": {
    "paymentId": "pay-17"
  }
}
~~~

Đây là schema **minh họa** với ID giả. `code` dành cho client phân loại; `message` dành cho hiển thị; `requestId` giúp hỗ trợ điều tra; `details` là các trường có schema được phép công khai.

**Không ghép JSON bằng string từ `ex.getMessage()`**: câu chữ có thể chứa dấu nháy, newline, ký tự điều khiển hoặc thông tin nhạy cảm. Dùng serializer/JSON library hỗ trợ escape và schema versioning. Không công bố Throwable, stack trace, cause hay SQL nguyên văn cho client.

Phân biệt lỗi nội bộ với response public: mã public có thể đủ ổn định cho hệ sinh thái client, còn exception class/stack trace phục vụ chẩn đoán phía server.

## 11. Khi nào được phép parse một chuỗi lỗi?

Một chuỗi **có thể** là giao thức nếu nhà cung cấp **công bố grammar máy đọc rõ ràng**. Ví dụ một tool có output protocol versioned, một CSV lỗi với escaping chính thức hoặc một `message` mà contract nói rõ nó chứa structured encoding.

Điều kiện tối thiểu:

- Có tài liệu schema, version và cách mã hóa/escape.
- Có kiểm thử malformed input và unknown fields.
- Có chính sách tương thích khi thêm/sửa trường.
- Không thay grammar theo locale ngoài điều đã công bố.
- Có giới hạn kích thước và kiểm tra dữ liệu không đáng tin.
- Không có API typed tốt hơn bị bỏ qua.

Nếu một thư viện chỉ mô tả message là “một chuỗi mô tả exception”, không được tự suy ra đó là contract có thể parse. Nếu bắt buộc tích hợp một thư viện legacy chỉ trả text, hãy cô lập parser ở **một adapter** cùng test hồi quy theo phiên bản, không cho mọi service parse riêng. Không dùng parser mỏng manh này cho quyết định thanh toán/rollback quan trọng khi có thể tránh.

## 12. Logging: đọc structured fields, không regex trên message

Một sự kiện log có thể mang:

~~~json
{
  "event": "payment_failed",
  "operation": "authorize_payment",
  "requestId": "req-42",
  "paymentId": "pay-17",
  "errorCode": "payment_declined",
  "exceptionType": "PaymentFailure",
  "causeType": "IllegalStateException"
}
~~~

Log trên là **mẫu**, không phải stdout chương trình. Một structured logger nên xuất `errorCode`, operation và ID thành các trường độc lập, đồng thời ghi `Throwable` để giữ stack/cause. Không cần gửi nguyên exception vào text field rồi dùng regex lấy lại error code.

Không tạo label metric theo `getMessage()`: message có ID động, URL và dữ liệu thay đổi, gây cardinality cao và có thể lộ thông tin. Metric nên nhóm theo `operation`, `errorCode`, `dependency` hoặc nhóm ổn định; request ID thuộc log/tracing.

Nếu lỗi đi qua nhiều tầng, tránh ghi cùng stack trace ở repository, service và controller nếu mỗi tầng chỉ log rồi throw. Tầng dưới nên chuyển loại lỗi có cause; tầng sở hữu kết quả cuối log sự kiện đủ context. Các sự kiện retry/compensation riêng vẫn có thể có log độc lập khi cần.

## 13. Bảo mật: thông điệp exception không phải dữ liệu public an toàn

`Throwable.getMessage()` đôi khi chứa:

- API key hoặc access token nằm trong URL.
- Connection string, SQL có tham số nhạy cảm.
- Đường dẫn file nội bộ, hostname, tên cấu hình.
- Email, điện thoại, định danh khách hàng hoặc nội dung request.
- Raw error body do provider ngoài kiểm soát.

Không đưa message thẳng vào HTML/API response hoặc metric. Chỉ xuất `code` và message public an toàn do chính ứng dụng kiểm soát. Nếu giữ business ID trong typed exception, nó vẫn phải được xem xét quyền truy cập, redaction và dữ liệu nhạy cảm. **Typed không đồng nghĩa safe-to-log**.

Input dùng làm ID từ client có thể chứa dấu xuống dòng hoặc control chars; validate và giới hạn độ dài trước khi đưa vào logging fields để giảm rủi ro log injection.

## 14. Retry, timeout và cancellation không thể dựa vào từ khóa

Một đoạn code như `getMessage().toLowerCase().contains("timeout")` không trả lời được:

1. Timeout xảy ra ở caller, DB, queue hay HTTP transport?
2. Request đã commit phía server hay chưa?
3. Thao tác có idempotent không?
4. Tác vụ đã bị hủy hay chỉ hết thời gian chờ?
5. Retry budget và backoff còn không?
6. Exception có bị bọc bởi lỗi khác không?

Hãy phân loại dựa trên **loại exception/mã lỗi chính thức**, policy dependency và **trạng thái nghiệp vụ**. `CancellationException` và `InterruptedException` có ý nghĩa về hủy hợp tác, cần được xử lý theo contract của chúng; không thay thế bằng tìm chuỗi “cancelled”.

Nếu một request mạng timeout, không mặc định rằng nó chưa gây side effect; có thể cần idempotency key hoặc inquiry trạng thái trước khi retry. Error code typed giúp phân loại tốt hơn nhưng cũng không tự đem lại rollback.

## 15. Tương thích khi thêm error code vào API cũ

Giả sử code cũ chỉ ném `RuntimeException("ORDER_MISSING: id")` và client đã parse prefix. Đổi message ngay có thể phá client cũ. Migration nên gồm:

1. Khảo sát các nơi dùng `getMessage()` để làm logic, kể cả service, tests, alert, SDK và web client.
2. Thêm typed field hoặc error code vào API/server response, giữ cause.
3. Công bố tập code và ý nghĩa, định nghĩa policy với code chưa biết.
4. Chuyển caller sang field mới theo giai đoạn, viết test cho hai ngôn ngữ/thông điệp.
5. Giữ cơ chế legacy trong thời gian tương thích nếu contract thực tế đang có người dùng, nhưng không khuyến khích consumer mới.
6. Đo sự sử dụng, thông báo deprecation và xóa parser legacy theo quy trình versioning.
7. Không thay đổi semantics của success/failure hoặc retry chỉ vì chuyển định dạng.

Một client phân tán có thể nhận code mới không nằm trong enum của phiên bản cũ. Khi public protocol là string code, cần fallback an toàn như “unknown_error” và ghi request ID để điều tra; **không đoán code từ message**. Java `switch` trên enum nội bộ có thể là exhaustive, nhưng giao thức nhiều phiên bản vẫn phải tính khả năng mở rộng.

## 16. Bộ kiểm thử cần có

| Kịch bản | Kết quả cần kiểm chứng |
|---|---|
| Cùng error code, message tiếng Anh/Việt | Cùng branch, status và retry policy |
| Message thêm prefix, suffix, request ID | Logic vẫn đúng |
| Message null/rỗng | Không NPE vì parse message |
| Hai lỗi khác nhau có cùng một từ khóa | Không bị phân loại nhầm |
| Wrapper nhiều tầng | Cause và error code còn đúng |
| Error code mới chưa biết | Fallback an toàn, không phụ thuộc wording |
| Response JSON có dấu nháy/newline/Unicode | Serializer escape đúng |
| Lỗi chứa thông tin nhạy cảm | Public response/log redaction đúng chính sách |
| Timeout/cancel/thất bại thường | Phân biệt bằng contract, không regex |
| Monitoring | Trường code ổn định, không dùng toàn bộ message làm label |
| Refactor/thay bản dịch | Các test máy đọc không thay đổi |
| Legacy adapter | Parser bị cô lập, có test đặc tả và version |

Ví dụ JUnit 5 kiểm tra độc lập với câu chữ (cần dependency JUnit và đặt test trong cùng package của lớp demo):

~~~java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PaymentCodeTest {
    @Test
    void localizationDoesNotChangeStatus() {
        var en = new ExceptionMessageContractDemo.PaymentFailure(
                ExceptionMessageContractDemo.FailureCode.PAYMENT_DECLINED,
                "pay-17", "Payment declined", null);
        var vi = new ExceptionMessageContractDemo.PaymentFailure(
                ExceptionMessageContractDemo.FailureCode.PAYMENT_DECLINED,
                "pay-17", "Thanh toán bị từ chối", null);

        assertEquals(402, ExceptionMessageContractDemo.stableHttpStatus(en));
        assertEquals(402, ExceptionMessageContractDemo.stableHttpStatus(vi));
        assertEquals(en.code(), vi.code());
    }
}
~~~

Bài test **không chạy chỉ bằng `javac` JDK thuần** vì dùng JUnit 5. Kiểm thử nên assert `code`/status/typed fields, không assert toàn bộ câu message trừ trường hợp đang chủ động kiểm tra bản dịch hoặc wording dành cho người đọc.

## 17. Những sai lầm đặc biệt dễ bỏ qua

1. Dùng `contains("not found")` thay subtype `NoSuchFileException` hoặc error code.
2. Parse `SQLException.getMessage()` dù API có `getSQLState()`/`getErrorCode()`.
3. Trích `orderId` từ message thay vì truyền ID/context đi cùng request.
4. Dùng `getLocalizedMessage()` để điều khiển workflow; đổi locale làm đổi logic.
5. Bọc exception rồi bỏ `cause`, khiến adapter phải dò chuỗi ở tầng dưới.
6. Tạo error code bằng toàn bộ message, làm dashboard/metric có cardinality cao.
7. Trả message thô của exception làm JSON/HTTP response, làm lộ dữ liệu nội bộ.
8. Coi mọi `"timeout"` trong text là một lỗi có thể retry an toàn.
9. Đổi public contract từ throw sang future/result hoặc đổi error code không có kế hoạch tương thích.
10. Chấp nhận bất kỳ string code mới như enum code đã biết, không có fallback.
11. Dùng regex phức tạp không giới hạn trên message do bên ngoài điều khiển.
12. Quên trường hợp `getMessage()==null` hoặc message chứa delimiter/control characters.
13. Coi cause sâu nhất là toàn bộ ý nghĩa lỗi; lớp wrapper có thể mang business ID và error code quan trọng.
14. Ghi cả message nhạy cảm vào nhiều tầng log khi chỉ cần một failure event có code và context an toàn.

## 18. Checklist code review

Khi gặp đoạn code phân loại exception:

1. Có chỗ nào quyết định nhánh bằng `getMessage().contains`, `startsWith`, `split`, regex hay substring không?
2. Message có được API công bố là giao thức có schema chính thức không? Nếu không, thay bằng gì?
3. Kiểu exception/subtype và trường `code` có đủ biểu đạt điều caller cần không?
4. Có giữ `cause` khi chuyển lỗi qua tầng không?
5. Business ID, request ID và vị trí nhập liệu có được truyền qua field/context riêng không?
6. Mã public có policy version/unknown code và message localization riêng không?
7. Retry có xét idempotency/side effect thực tế không?
8. Logs và metrics phân nhóm theo code/operation thay vì message động chưa?
9. Message có thể chứa bí mật/PII không? Public response có redaction/escaping không?
10. Test có chứng minh đổi câu chữ **không đổi nhánh** không?

## 19. Tổng kết

**Không dùng exception message làm định dạng dữ liệu mà mã khác phải phân tích.** `Throwable.getMessage()` mô tả lỗi cho con người và có thể thay đổi vì wording, localization, context hay dependency version. Dùng nó như error code làm logic dễ hỏng và khó kiểm thử.

Tách rõ **message** (để đọc), **error code/exception type** (để máy phân loại), **typed fields** (để lấy dữ liệu), **cause** (để điều tra) và **public error response** (để client tiêu thụ theo schema). Một test đơn giản nhưng rất hiệu quả là thay message tiếng Anh thành tiếng Việt, giữ nguyên error code và yêu cầu kết quả xử lý **không đổi**.

### Tham khảo

- [Java SE 17 — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html)
- [Java SE 17 — RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html)
- [Java SE 17 — SQLException](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/SQLException.html)
- [Java SE 17 — ResourceBundle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ResourceBundle.html)
- [Java Language Specification `11 — Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-pitfalls-011">Bắt lỗi do sai lập trình rồi tiếp tục với trạng thái không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Bắt lỗi do sai lập trình rồi tiếp tục với trạng thái không hợp lệ

Trong Java, `catch` cho phép chương trình xử lý một ngoại lệ. Tuy nhiên, **bắt được ngoại lệ không có nghĩa chương trình đã phục hồi**. Một sai lầm nguy hiểm là bắt `NullPointerException`, `IllegalStateException`, `IndexOutOfBoundsException` hoặc một `RuntimeException` khác do lỗi lập trình, ghi một dòng log (hoặc bỏ qua), rồi vẫn tiếp tục cập nhật dữ liệu hay thông báo thành công như thể mọi điều kiện ban đầu còn đúng.

Hệ quả thường nghiêm trọng hơn lỗi đầu tiên: đối tượng có thể chỉ được cập nhật một nửa, đơn hàng chuyển sang trạng thái “đã xác nhận” dù chưa được xử lý, bộ đếm hoặc số dư mất cân bằng, giao dịch bị lặp lại, hoặc lỗi thực bị che bởi một ngoại lệ xuất hiện muộn hơn.

Nguyên tắc cốt lõi: **chỉ tiếp tục khi đã chứng minh được trạng thái sau lỗi vẫn hợp lệ và có một chiến lược phục hồi thực sự**. Nếu lỗi chứng tỏ một bất biến (*invariant*) hoặc giả định của chương trình đã bị phá vỡ, thường cần dừng thao tác hiện tại, chuyển thất bại đến ranh giới xử lý thích hợp và đưa trạng thái về một điểm nhất quán.

## 1. Phân biệt “đã bắt lỗi” với “đã phục hồi”

Xét một chuỗi thao tác:

```text
Bắt đầu yêu cầu
    |
    v
Đổi trạng thái A
    |
    v
Gặp lỗi lập trình tại bước B
    |
    +--> catch (RuntimeException error)
              |
              +--> bỏ qua / log rồi tiếp tục
                        |
                        v
               Đổi trạng thái C
                        |
                        v
              Trả kết quả thành công  <-- nguy hiểm
```

Bản thân `catch` chỉ chuyển điều khiển vào một nhánh xử lý. Nó **không tự hoàn tác** những thay đổi đã thực hiện trước ngoại lệ, không tự trả khóa hay hủy giao dịch nghiệp vụ, cũng không xác nhận rằng biến và tài nguyên đang ở trạng thái đúng.

Một hướng xử lý đúng phải trả lời được:

1. Lỗi là tình huống **được hợp đồng dự kiến** hay là dấu hiệu của bug?
2. Những thay đổi nào đã được thực hiện trước lúc lỗi xảy ra?
3. Có thể hoàn tác, hủy giao dịch hoặc chuyển sang trạng thái thất bại hợp lệ không?
4. Tác vụ có được phép trả fallback, thử lại hoặc tiếp tục với dữ liệu hiện có không?
5. Bằng cách nào tầng gọi biết một yêu cầu đã thất bại và không được coi là thành công?

Nếu chưa thể trả lời các câu hỏi trên, không nên dùng `catch` như một lối đi tắt để “cho chương trình chạy tiếp”.

## 2. Thế nào là lỗi do sai lập trình?

Một số ngoại lệ thường gợi ý giả định trong mã đã bị vi phạm:

| Ngoại lệ thường gặp | Ví dụ nguyên nhân | Điều cần kiểm tra |
| --- | --- | --- |
| `NullPointerException` | Mã truy cập đối tượng mà hợp đồng nội bộ yêu cầu không được `null` | Dữ liệu từ đâu ra, kiểm tra null ở đúng ranh giới chưa, biến có bị khởi tạo sai không |
| `IndexOutOfBoundsException` | Truy cập phần tử không tồn tại; giả định danh sách có đủ phần tử | Kích thước danh sách, vòng lặp, điều kiện biên |
| `ClassCastException` | Ép kiểu không đúng kiểu thực tế | Thiết kế kiểu dữ liệu, generics, hợp đồng đầu vào |
| `IllegalStateException` | Thao tác diễn ra khi đối tượng đang ở trạng thái không cho phép | Bất biến, thứ tự chuyển trạng thái và trách nhiệm sở hữu |
| `IllegalArgumentException` | Truyền đối số trái hợp đồng | Đây là lỗi bên gọi, hay đầu vào bên ngoài cần trả phản hồi hợp lệ? |
| `ArithmeticException` | Ví dụ chia số nguyên cho 0 | Điều kiện toán học, dữ liệu và nhánh tính toán |
| `AssertionError` | Một khẳng định nội bộ thất bại khi assertions được bật | Bất biến thiết kế; không biến nó thành thành công giả |

**Phân loại phải dựa trên hợp đồng, không chỉ tên lớp.** `NumberFormatException` là một `IllegalArgumentException`, nhưng khi parse văn bản do người dùng nhập, chuỗi sai định dạng có thể là tình huống *dự kiến* cần trả lỗi kiểm tra đầu vào. Ngược lại, `NullPointerException` ở giữa một thuật toán với tham số đã được kiểm tra có thể là bug thực sự.

`RuntimeException` là ngoại lệ unchecked; **unchecked không đồng nghĩa không được phép bắt**, cũng không đồng nghĩa mọi trường hợp đều là lỗi lập trình. Điểm quan trọng là phải có chiến lược xử lý tương ứng với nguyên nhân và trạng thái còn lại.

`Error` như `OutOfMemoryError`, `StackOverflowError` nói chung không thuộc nhóm ngoại lệ nghiệp vụ có thể phục hồi bằng cách bắt rộng và tiếp tục. Đặc biệt tránh `catch (Throwable error)` chỉ để bỏ qua mọi lỗi.

## 3. Bất biến và trạng thái nửa vời (*partial mutation*)

**Bất biến** là điều kiện phải đúng mỗi khi đối tượng ở một trạng thái được công bố là hợp lệ.

Ví dụ một kho giả định có tổng 5 đơn vị hàng:

\[
\text{available} + \text{reserved} = 5
\]

Một thao tác đặt trước 2 đơn vị hợp lệ sẽ chuyển từ `(available=5, reserved=0)` sang `(available=3, reserved=2)`. Nếu mã sửa `available` trước, rồi mới phát hiện đối số không hợp lệ và **nuốt ngoại lệ**, bất biến có thể bị phá vỡ.

Đoạn sai về ý tưởng:

```java
try {
    inventory.available -= quantity;
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity must be positive");
    }
    inventory.reserved += quantity;
} catch (IllegalArgumentException error) {
    // Bỏ qua; trường available đã bị sửa.
}
```

Đây là **mã minh họa** yêu cầu đối tượng `inventory` và biến `quantity` do ứng dụng khai báo, không phải chương trình độc lập. Với `quantity = -2`, phép trừ đã làm `available` **tăng** 2 trước khi ngoại lệ được ném. Nhánh `catch` không tự khôi phục số liệu.

Càng nguy hiểm nếu phía dưới còn đặt cờ `confirmed=true`, gửi sự kiện hoặc phát hành phản hồi HTTP `200 OK`: các hệ thống khác có thể hành động dựa trên **thành công giả**.

## 4. Ví dụ Java 17+ chạy được: catch rồi tiếp tục làm hỏng trạng thái

Tạo tệp **`InvalidStateAfterCatch.java`**:

```java
public class InvalidStateAfterCatch {
    static final class Inventory {
        final int capacity = 5;
        int available = 5;
        int reserved = 0;
        boolean confirmed;

        boolean invariantHolds() {
            return available >= 0
                    && reserved >= 0
                    && available + reserved == capacity;
        }
    }

    static void reserveWrong(Inventory inventory, int quantity) {
        try {
            inventory.available -= quantity; // Thay đổi trước khi kiểm tra.
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "quantity must be positive");
            }
            inventory.reserved += quantity;
            inventory.confirmed = true;
        } catch (IllegalArgumentException ignored) {
            // Sai: đánh dấu thành công mặc dù trạng thái đã bị sửa dở.
            inventory.confirmed = true;
        }
    }

    static void reserveCorrect(Inventory inventory, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        if (quantity > inventory.available) {
            throw new IllegalStateException("insufficient stock");
        }

        int nextAvailable = inventory.available - quantity;
        int nextReserved = inventory.reserved + quantity;
        if (nextAvailable < 0
                || nextReserved < 0
                || nextAvailable + nextReserved != inventory.capacity) {
            throw new IllegalStateException("inventory invariant broken");
        }

        inventory.available = nextAvailable;
        inventory.reserved = nextReserved;
        inventory.confirmed = true;
    }

    static void print(String label, Inventory inventory) {
        System.out.println(label + " available=" + inventory.available
                + " reserved=" + inventory.reserved
                + " confirmed=" + inventory.confirmed
                + " valid=" + inventory.invariantHolds());
    }

    public static void main(String[] args) {
        Inventory wrong = new Inventory();
        reserveWrong(wrong, -2);
        print("wrong", wrong);

        Inventory correct = new Inventory();
        try {
            reserveCorrect(correct, -2);
        } catch (IllegalArgumentException error) {
            System.out.println("rejected="
                    + error.getClass().getSimpleName());
        }
        print("afterReject", correct);

        reserveCorrect(correct, 2);
        print("afterSuccess", correct);
    }
}
```

Biên dịch, chạy với Java 17:

```bash
javac --release 17 InvalidStateAfterCatch.java
java InvalidStateAfterCatch
```

Đầu ra chính xác:

```text
wrong available=7 reserved=0 confirmed=true valid=false
rejected=IllegalArgumentException
afterReject available=5 reserved=0 confirmed=false valid=true
afterSuccess available=3 reserved=2 confirmed=true valid=true
```

**Phân tích:**

- `reserveWrong(..., -2)` thay đổi `available` thành `7`, sau đó mới phát hiện đối số sai. Ngoại lệ bị nuốt và `confirmed=true`. Tổng `available + reserved = 7` trái với `capacity=5`: mã đã báo thành công trong trạng thái không hợp lệ.
- `reserveCorrect(..., -2)` kiểm tra điều kiện trước mọi thay đổi. Lỗi được truyền tới tầng gọi, đối tượng giữ nguyên `(5,0,false)` và bất biến đúng.
- `reserveCorrect(..., 2)` tính giá trị dự kiến, kiểm tra bất biến rồi mới công bố thay đổi, cho kết quả `(3,2,true)`.

Ví dụ trên là mô hình **đơn luồng với các trường nằm trong bộ nhớ**. Nó chứng minh ý tưởng kiểm tra trước khi sửa, **không phải giao dịch nguyên tử** cho nhiều luồng hoặc hệ thống phân tán. Nếu có truy cập đồng thời, cần đồng bộ/atomic state phù hợp. Nếu đã gọi cơ sở dữ liệu hoặc dịch vụ ngoài, còn cần transaction, cơ chế hủy hoặc bù trừ thực tế.

## 5. Phục hồi thật sự cần những điều kiện nào?

Một nhánh `catch` có thể tiếp tục khi nó **biết rõ lỗi gì**, **có thể làm gì sau lỗi** và **duy trì các bất biến**.

Ví dụ các tình huống có thể được hợp đồng cho phép:

- Người dùng nhập số không đúng định dạng: bắt `NumberFormatException`, trả phản hồi yêu cầu sửa đầu vào, không cập nhật trạng thái nghiệp vụ.
- Một cache không sẵn sàng nhưng nguồn chính vẫn hoạt động: ghi cảnh báo, đọc từ nguồn chính, kiểm soát tác dụng phụ để tránh kết quả trùng.
- Đọc một tệp cấu hình tùy chọn không thành công: chọn mặc định được tài liệu hóa nếu mặc định đó **thực sự hợp lệ** với ứng dụng.
- Một thao tác được thiết kế idempotent thất bại tạm thời: có thể thử lại với giới hạn thời gian/số lần thử, nhưng phải kiểm tra lỗi, tài nguyên và tính an toàn của retry.

Ngược lại, nếu `IllegalStateException` chỉ ra số lượng đặt trước và tồn kho mất cân bằng, việc ghi log rồi tiếp tục “bán hàng” **không phải phục hồi**. Muốn phục hồi cần sửa hoặc tái dựng trạng thái theo quy tắc đã được kiểm chứng, không gán ngẫu nhiên một giá trị để vượt qua điều kiện kiểm tra.

Một bộ tiêu chí ngắn cho nhánh phục hồi:

1. **Ngoại lệ dự kiến và được phân loại hẹp.** Không dùng `catch (Exception ignored)` thay cho hợp đồng lỗi.
2. **Không có tác dụng phụ không được kiểm soát.** Những thay đổi trước lỗi đã được hoàn tác, bù trừ hoặc tồn tại trong một trạng thái thất bại được thiết kế rõ.
3. **Đầu ra trung thực.** Nếu chưa hoàn thành thao tác, không trả về kết quả báo “đã hoàn thành”.
4. **Có quan sát và kiểm thử.** Log/metric phản ánh việc dùng fallback hoặc retry; test kiểm tra cả trạng thái sau lỗi.
5. **Có giới hạn.** Nếu việc thử lại thất bại liên tiếp, dừng và chuyển lỗi lên thay vì tạo vòng lặp vô hạn.

## 6. Bắt đúng loại lỗi thay vì `catch (RuntimeException)` rồi mặc định thành công

Một lỗi phổ biến khác: dùng một `catch` quá rộng cho cả input không hợp lệ **và** bug.

```java
String parseWrong(String raw) {
    try {
        int quantity = Integer.parseInt(raw.trim());
        return "accepted:" + quantity;
    } catch (RuntimeException ignored) {
        return "accepted:0";
    }
}
```

Đoạn trên là một **phương thức minh họa**, không phải chương trình độc lập. Nó bắt cả `NumberFormatException` lẫn `NullPointerException` từ `raw.trim()`, rồi trả về một kết quả trông hợp lệ. Nếu một tầng nội bộ có hợp đồng `raw` không được `null`, caller vừa vi phạm hợp đồng nhưng sự cố bị biến thành “đã chấp nhận 0”.

Ở ranh giới nhận input từ bên ngoài, `null` cũng có thể là **input dự kiến cần từ chối**. Khi đó hãy kiểm tra rõ ràng ở bước validation, không để một `NullPointerException` tình cờ làm giao thức phân loại đầu vào.

### Ví dụ thứ hai: phân loại hẹp và dừng request sai hợp đồng

Tạo tệp **`NarrowCatchBoundary.java`**:

```java
import java.util.Objects;

public class NarrowCatchBoundary {
    static String wrong(String raw) {
        try {
            return "accepted:" + Integer.parseInt(raw.trim());
        } catch (RuntimeException ignored) {
            return "accepted:0";
        }
    }

    static String correct(String nonNullRaw) {
        Objects.requireNonNull(nonNullRaw, "nonNullRaw");
        try {
            return "accepted:" + Integer.parseInt(nonNullRaw);
        } catch (NumberFormatException expected) {
            return "invalid number";
        }
    }

    public static void main(String[] args) {
        System.out.println("wrongNull=" + wrong(null));
        System.out.println("valid=" + correct("5"));
        System.out.println("badFormat=" + correct("oops"));

        try {
            correct(null);
        } catch (NullPointerException unexpected) {
            System.out.println("requestFailed="
                    + unexpected.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac --release 17 NarrowCatchBoundary.java
java NarrowCatchBoundary
```

Đầu ra:

```text
wrongNull=accepted:0
valid=accepted:5
badFormat=invalid number
requestFailed=NullPointerException
```

Trong `correct`, `nonNullRaw` là **hợp đồng nội bộ**. Sai định dạng (`"oops"`) là tình huống mà phương thức chủ động phân loại và trả về `"invalid number"`; truyền `null` trái hợp đồng, nên `Objects.requireNonNull` báo lỗi và thao tác không giả vờ thành công. `main` đóng vai trò **ranh giới của bản demo**: bắt lỗi bất ngờ để minh họa rằng request bị thất bại; mã nghiệp vụ bên trong không tiếp tục.

Lưu ý: ví dụ trả các chuỗi chỉ để dễ quan sát. Trong ứng dụng thực, nên dùng kiểu kết quả rõ ràng (ví dụ `Result`, enum/mã lỗi, HTTP 400 với input sai, HTTP 500 với bug nội bộ), thay vì buộc người gọi phân tích chuỗi tiếng Anh.

## 7. Vì sao tiếp tục sau lỗi lập trình dễ gây lỗi dây chuyền?

Giả sử một chương trình có chuỗi trạng thái:

```text
CREATED -> VALIDATED -> RESERVED -> PAID -> CONFIRMED
```

Nếu bước `RESERVED` ném `IllegalStateException` vì mất cân bằng số lượng, nhưng catch lại đổi trạng thái thành `PAID`, các bước sau có thể:

- Trừ tiền khi chưa khóa tài nguyên/sản phẩm.
- Phát sự kiện cho hệ thống giao nhận dựa trên đơn hàng chưa đặt thành công.
- Ghi bảng lịch sử “đã thanh toán” nhưng bảng nghiệp vụ chính vẫn ở trạng thái “mới tạo”.
- Thực hiện retry từ tầng ngoài, trong khi một phần tác dụng phụ của lần thử đầu đã xảy ra.
- Gây thêm `NullPointerException` hoặc `ClassCastException` về sau, che mất lỗi ban đầu.

Lỗi thứ hai thường xuất hiện **xa nơi lỗi đầu tiên**, nên stack trace của nó khó giải thích nguyên nhân gốc. Giữ và chuyển lỗi đầu tiên tới ranh giới xử lý sẽ rút ngắn đường chẩn đoán.

Hãy thiết kế chuyển trạng thái theo những điểm công bố rõ ràng. Nếu chưa hoàn thành tất cả bước cần thiết, không chuyển đối tượng sang trạng thái cho phép bước tiếp theo. Khi việc thực thi có tác dụng phụ từ xa, trạng thái trung gian như `PENDING`, `FAILED` hoặc `REQUIRES_RECONCILIATION` có thể cần thiết; không đơn giản “quay lại ban đầu” khi kết quả thực tế bên ngoài chưa rõ.

## 8. Fail fast: dừng đúng phạm vi, không nhất thiết dừng toàn JVM

**Fail fast** nghĩa là phát hiện sự vi phạm giả định càng sớm càng tốt và **không tiếp tục dùng dữ liệu đã mất tính tin cậy**. Nó không có nghĩa luôn gọi `System.exit`, tắt toàn bộ JVM hoặc để mọi request khiến service chết.

Ví dụ trong web service:

1. Tầng xử lý phát hiện `IllegalStateException` không thể sửa.
2. Thao tác nghiệp vụ hiện tại dừng; transaction được đánh dấu rollback khi có transaction thích hợp.
3. Bộ xử lý lỗi ở ranh giới request ghi log cả `Throwable` với mã tương quan an toàn.
4. Người gọi nhận kết quả thất bại trung thực; hệ thống không công bố “thành công”.
5. Worker/request khác có trạng thái cô lập vẫn được phục vụ nếu nền tảng còn an toàn.

Nếu lỗi phá vỡ **bất biến toàn cục** hoặc làm thành phần không còn đáng tin (ví dụ bộ nhớ dùng chung bị hỏng), hành động có thể phải nghiêm khắc hơn: đưa thành phần ra khỏi lưu lượng, từ chối tác vụ mới hoặc khởi động lại có kiểm soát. Nhưng quyết định đó cần dựa trên mức lan truyền của sai lệch, không phải chỉ trên việc nhìn thấy một `RuntimeException`.

Nếu một request thất bại mà chưa có thay đổi dữ liệu, việc trả lỗi là đủ. Nếu dữ liệu đã bị sửa một phần, **trả lỗi không tự sửa dữ liệu**: còn cần rollback, phục hồi hoặc cách ly.

## 9. Transaction, rollback và các tác dụng phụ bên ngoài

Để tránh trạng thái nửa vời, thứ tự xử lý nên là:

```text
Validate input và invariant
        |
        v
Tính toán trạng thái dự kiến
        |
        v
Bắt đầu đơn vị công việc có thể rollback / transaction
        |
        v
Thực hiện thay đổi thuộc cùng ranh giới nguyên tử
        |
        +-- lỗi --> rollback / đánh dấu FAILED / bù trừ
        |
        v
Commit hoặc công bố hoàn tất
```

Đây là sơ đồ khái niệm, không cam kết rằng mọi tác dụng phụ đều thuộc một transaction.

**Đối với bộ nhớ đơn luồng:** có thể kiểm tra trước, tính các giá trị mới vào biến cục bộ rồi mới gán cho đối tượng. Nếu cập nhật nhiều đối tượng hoặc chạy nhiều luồng, cần giải pháp đồng bộ/atomic phù hợp; vài lệnh gán liên tiếp không tự trở thành nguyên tử.

**Đối với cơ sở dữ liệu:** sử dụng transaction có ranh giới rõ, rollback khi thất bại theo cấu hình công nghệ đang dùng. Không giả định `catch` rồi trả về thành công khiến framework vẫn rollback; trong một số framework, việc nuốt ngoại lệ làm transaction trông như đã hoàn tất bình thường.

**Đối với HTTP, thanh toán, message queue:** một thao tác bên ngoài có thể đã hoàn thành dù client nhận timeout/lỗi. Rollback cơ sở dữ liệu cục bộ không xóa được tác dụng phụ ở bên ngoài. Khi cần, thiết kế idempotency key, outbox, saga/compensating action hoặc trạng thái đối soát. Không retry bừa chỉ vì đã “bắt được lỗi”.

**Đối với tài nguyên:** dùng `try-with-resources` và `finally` để đóng những tài nguyên thuộc quyền sở hữu; đừng coi giải phóng tài nguyên là giấy phép tiếp tục nghiệp vụ với dữ liệu không hợp lệ.

## 10. Bắt lỗi ở ranh giới phù hợp và giữ nguyên nhân

Khi bug hoặc bất biến hỏng, hãy giữ thông tin để điều tra.

Ví dụ minh họa với SLF4J, không phải chương trình Java độc lập (`logger`, `requestId`, `service` được ứng dụng cung cấp):

```java
try {
    service.execute();
} catch (IllegalStateException error) {
    logger.error("Request {} failed with invalid state", requestId, error);
    throw error;
}
```

Đoạn trên ghi **đối tượng `Throwable`** thay vì chỉ `error.getMessage()`, và chuyển tiếp lỗi. Trong service nhiều tầng, tránh log cùng stack trace ở mọi tầng; nên xác định ai sở hữu logging ở ranh giới cuối để giảm log trùng.

Nếu cần chuyển sang loại lỗi có nghĩa hơn, **giữ cause**:

```java
try {
    repository.save(order);
} catch (java.io.IOException cause) {
    throw new IllegalStateException("Cannot persist order", cause);
}
```

Đây là mã minh họa cần `repository`, `order`. `cause` cho phép theo dấu lỗi gốc; `throw new IllegalStateException(cause.getMessage())` sẽ làm mất chuỗi nguyên nhân. Tuy nhiên, đừng bọc `IllegalStateException` không thể phục hồi thành một mã “người dùng nhập sai” nếu bản chất là bug nội bộ.

Trong một bộ xử lý lỗi toàn cục, có thể chuyển lỗi sang cấu trúc phản hồi **không chứa stack trace** cho người dùng và ghi chi tiết nội bộ bằng logger. Tránh để password, token, dữ liệu cá nhân hoặc nội dung input tùy ý lọt vào message/log.

## 11. `finally` và `try-with-resources` không có nghĩa được tiếp tục

`finally` thường chạy trên đường thoát của `try`/`catch`, kể cả khi có lỗi, nhưng **cleanup không phải phục hồi trạng thái nghiệp vụ**:

```java
try {
    persistBusinessChanges();
} catch (RuntimeException error) {
    logger.error("Persistence failed", error);
} finally {
    notifySuccess(); // Sai: vẫn chạy khi ghi dữ liệu lỗi.
}
```

Đây là mảnh minh họa có các phương thức/logger của ứng dụng. Chỉ phát sự kiện thành công sau khi thao tác thực sự hoàn tất hoặc commit. `try-with-resources` có thể đóng tài nguyên và giữ suppressed exceptions khi `close()` lỗi nhưng **không tự rollback tác dụng phụ**.

Ở bài này, việc dừng xử lý trạng thái **bất hợp lệ** là trọng tâm; cơ chế ưu tiên exception khi `finally` ném lỗi hoặc `close()` thất bại được trình bày trong các bài cleanup riêng.

## 12. Những `catch` quá rộng làm lỗi bị che khuất

Những mẫu đáng cảnh giác:

```java
catch (Exception ignored) {
    return null;
}
```

```java
catch (RuntimeException error) {
    return true;
}
```

```java
catch (Throwable error) {
    continue;
}
```

Đây là **các mảnh mã phản ví dụ**, không phải khối Java độc lập. Trong mỗi trường hợp, caller không thể phân biệt một hoạt động thành công với tình huống lỗi bị che; đặc biệt `catch (Throwable)` còn gồm `Error`, thường không nên coi là tín hiệu có thể xử lý như lỗi nghiệp vụ thông thường.

Trong vòng lặp xử lý hàng loạt, có thể có lý do **bỏ qua một bản ghi không hợp lệ**, nhưng phải giới hạn lỗi, cách ly bản ghi, ghi nhận thất bại và tiếp tục **với trạng thái được thiết kế cho việc bỏ qua đó**. Không bắt tất cả ngoại lệ rồi tiếp tục dùng chính object đang nghi hỏng.

Ví dụ một batch có thể phân loại lỗi định dạng cho **từng dòng độc lập**, ghi kết quả `INVALID_INPUT` cho dòng đó rồi xử lý dòng tiếp theo. Nhưng nếu một `NullPointerException` bất ngờ cho thấy bộ parser đang giữ trạng thái dùng chung bị lỗi, tiếp tục toàn batch có thể nhân rộng dữ liệu sai. Ranh giới cách ly quyết định hành vi phù hợp.

## 13. Các tác vụ bất đồng bộ và exception bị nuốt

Trong `ExecutorService` hoặc `CompletableFuture`, lỗi do sai lập trình có thể phát sinh ở worker nhưng chỉ được quan sát qua `Future.get()` hoặc `CompletableFuture.join()`.

Ví dụ minh họa:

```java
CompletableFuture<Order> result =
        CompletableFuture.supplyAsync(() -> processOrder(order));
```

Đây là mảnh mã cần khai báo `Order`, `order` và `processOrder`. Nếu lambda ném `IllegalStateException` thì future có thể hoàn tất thất bại. Nhưng nếu lambda tự bắt `RuntimeException`, trả `Order` giả thành công hoặc chuyển `confirmed=true`, future sẽ được xem là **hoàn tất bình thường**, làm người gọi không còn biết vấn đề.

Với `CompletableFuture`, `exceptionally(error -> fallback)` có thể chủ đích biến thất bại thành giá trị thành công. Chỉ làm vậy khi fallback **được cho phép bởi hợp đồng** và không che một bug làm dữ liệu mất nhất quán. Không đặt `exceptionally(error -> new Order("SUCCESS"))` trên mọi pipeline để chống lỗi chung.

Tương tự, `Future.isDone()` và `CompletableFuture.isDone()` chỉ nói rằng future đã hoàn tất (có thể bình thường, lỗi hoặc hủy), không chứng minh thay đổi nghiệp vụ đã thành công. Khi `join()` ném `CompletionException` hoặc `get()` ném `ExecutionException`, cần truy cause để tìm sự cố thật, không bắt lớp bọc rồi tự ý coi request đã xử lý xong.

## 14. Retry và fallback chỉ an toàn khi có điều kiện

Một lỗi tạm thời của dịch vụ ngoài có thể được retry, nhưng một lỗi lập trình như truy cập index sai, cast sai hoặc invariant hỏng thường **không biến mất** nhờ lặp lại với cùng dữ liệu. Retry vô điều kiện có thể tăng tải, tạo thao tác trùng và che lỗi.

Trước khi retry cần có:

- Loại lỗi hoặc mã trạng thái được xác định là tạm thời.
- Tác vụ được xác nhận an toàn để chạy lại hoặc có khóa idempotency.
- Chính sách giới hạn số lần thử, khoảng chờ, tổng deadline.
- Trạng thái sau lần thất bại trước được xác nhận hợp lệ.
- Log/metric để phân biệt lần thử lại với một thành công thật.

Fallback cũng vậy. Một cache lỗi nhưng nguồn chính còn tốt có thể trả dữ liệu từ nguồn chính; ngược lại, lỗi làm dữ liệu định giá không nhất quán không thể sửa bằng cách trả giá trị `0` rồi ghi đơn hàng thành công.

Nếu không chứng minh được tính an toàn của retry/fallback, cần **đưa thất bại lên tầng có thể quyết định** thay vì tự phục hồi một cách giả tạo.

## 15. Kiểm thử đường lỗi và bất biến, không chỉ kiểm tra ngoại lệ

Test tốt không chỉ xác nhận `"method threw IllegalArgumentException"`, mà còn xác nhận **đối tượng sau lỗi vẫn hợp lệ**, side effect đúng và caller nhận thất bại.

Với ví dụ `Inventory`, các kịch bản cần kiểm tra:

| Kịch bản | Kỳ vọng về lỗi/kết quả | Kỳ vọng về trạng thái |
| --- | --- | --- |
| `quantity = -2` | Từ chối `IllegalArgumentException` | Giữ `(available=5, reserved=0, confirmed=false)` |
| `quantity = 0` | Từ chối như input không hợp lệ | Không thay đổi dữ liệu |
| `quantity > available` | Không cho đặt trước | Không tạo lượng hàng âm hay tăng `reserved` |
| `quantity = 2` | Thành công | `(available=3, reserved=2, confirmed=true)` |
| Bug giữa thao tác sau khi có side effect | Request thất bại | Rollback, bù trừ, hoặc trạng thái FAILED rõ ràng |
| Hai thao tác đồng thời | Không vi phạm capacity | Bảo vệ tính nhất quán bằng cơ chế đồng bộ/transaction |

Trong JUnit 5 (đoạn minh họa cần import JUnit và khai báo lớp/trạng thái tương ứng), có thể viết:

```java
assertThrows(IllegalArgumentException.class,
        () -> reserveCorrect(state, -2));
assertEquals(5, state.available);
assertEquals(0, state.reserved);
assertFalse(state.confirmed);
assertTrue(state.invariantHolds());
```

Đây **không phải chương trình độc lập**; `assertThrows`, `assertEquals`, `assertFalse`, `assertTrue` là API JUnit 5. Java 17 không tích hợp sẵn JUnit. Mục đích là kiểm thử **đường lỗi và tính bất biến**, chứ không chỉ kiểm tra rằng một exception được ném.

Nếu mã cần rollback transaction, test tích hợp nên xác nhận dữ liệu DB không bị commit sau lỗi. Nếu có các thao tác bên ngoài, cần test kết quả retry, idempotency hoặc đối soát; một unit test trên object bộ nhớ chưa bao phủ toàn bộ tác dụng phụ.

## 16. Phân biệt bắt ở ranh giới request và nuốt lỗi trong nghiệp vụ

Một service có thể dùng bộ xử lý lỗi toàn cục để biến exception thành HTTP 500 hoặc một thông báo lỗi an toàn. Về cú pháp, đó vẫn là “bắt lỗi”; nhưng khác cơ bản với việc **nuốt lỗi ở giữa luồng nghiệp vụ**.

| Bắt ở tầng nghiệp vụ rồi chạy tiếp | Bắt ở ranh giới để kết thúc yêu cầu |
| --- | --- |
| Có thể giữ state không hợp lệ cho các bước sau | Không chạy các bước tiếp theo của yêu cầu thất bại |
| Dễ trả về thành công giả | Trả kết quả thất bại trung thực |
| Không tự rollback | Có thể kết hợp transaction rollback và phục hồi theo hợp đồng |
| Thường che nguyên nhân gốc | Có thể ghi `Throwable` và correlation ID |
| Rất khó phân biệt retry an toàn | Tầng điều phối có thể quyết định retry/fail dựa trên nguyên nhân |

Bắt ở tầng cuối **không tự sửa state đã bị thay đổi**: cần xác định boundary transaction, tài nguyên và cách xử lý side effect. Nhưng nó giữ được nguyên tắc không cho bước nghiệp vụ sau tiếp tục với dữ liệu sai.

Nếu một service đa tenant gặp bug chỉ trong một request, nên cách ly thất bại ở request hoặc worker tương ứng, giữ hoạt động khác không liên quan khi an toàn. Nếu một bất biến của state toàn cục đã hỏng, phải đánh giá sức khỏe toàn thành phần thay vì tiếp tục mù quáng.

## 17. Những lỗi thường gặp khi review mã

| Mẫu sai | Nguy cơ | Thay thế phù hợp |
| --- | --- | --- |
| `catch (NullPointerException e) {}` | Che lỗi giả định null, phần xử lý tiếp tục với dữ liệu chưa chắc đúng | Làm rõ hợp đồng null, sửa nguồn lỗi; thất bại nếu invariant không bảo đảm |
| `catch (RuntimeException e) { return true; }` | Trả thành công giả | Trả kết quả thất bại hoặc ném tiếp; chỉ fallback với lỗi dự kiến |
| Sửa object rồi mới kiểm tra điều kiện và nuốt lỗi | Trạng thái cập nhật một phần | Validate trước, tính state mới, commit tại điểm an toàn |
| `catch (Exception e)` chỉ để `System.out.println(e.getMessage())` | Lỗi bị che, mất stack/cause | Ghi đối tượng `Throwable` ở ranh giới xử lý và truyền thất bại |
| `finally { markSuccess(); }` | Đánh dấu thành công cả khi lỗi | Chỉ đánh dấu sau bước commit thành công |
| Bắt `Throwable` để ứng dụng “không bao giờ lỗi” | Có thể nuốt `Error` nghiêm trọng | Không bắt quá rộng; chỉ bắt tại ranh giới đủ trách nhiệm |
| Retry tất cả `IllegalStateException` | Bug lặp lại, tăng side effect | Chỉ retry lỗi tạm thời đã phân loại và an toàn |
| `exceptionally(e -> successValue)` cho mọi lỗi | `CompletableFuture` báo thành công dù bug | Chỉ phục hồi loại lỗi/case được hợp đồng cho phép |
| Test chỉ `assertThrows` nhưng không kiểm tra state | Không phát hiện partial mutation | Khẳng định cả trạng thái, giao dịch và side effect |
| Bắt ngoại lệ xong bỏ qua interrupted status | Không đáp ứng tín hiệu hủy hợp tác | Xử lý/khôi phục ngắt theo hợp đồng công việc |

## 18. Tự kiểm tra

**Câu 1.** Sau khi cập nhật một nửa object, phương thức ném `IllegalStateException`. Bắt rồi ghi log có tự khôi phục object không?  
**Đáp án:** Không. `catch` chỉ chuyển điều khiển. Cần rollback, bù trừ hoặc không công bố state không hợp lệ.

**Câu 2.** `NullPointerException` luôn là lỗi lập trình không được bắt?  
**Đáp án:** Không thể kết luận chỉ từ tên lớp. Nhưng nếu nó phản ánh giả định nội bộ bị vi phạm, việc nuốt lỗi rồi tiếp tục thường nguy hiểm. Kiểm tra input ở ranh giới và dùng chính sách lỗi rõ ràng.

**Câu 3.** Một parser bắt `NumberFormatException` và trả `INVALID_INPUT` cho người dùng có hợp lệ không?  
**Đáp án:** Có thể hợp lệ nếu chuỗi sai định dạng là tình huống dự kiến, trạng thái chưa bị cập nhật và caller nhận kết quả lỗi rõ ràng.

**Câu 4.** Vì sao `catch (RuntimeException error) { return 0; }` có thể che bug?  
**Đáp án:** Vì nhiều lỗi không liên quan, như `NullPointerException` và `IndexOutOfBoundsException`, đều thuộc `RuntimeException`. Trả 0 khiến caller không biết có sự cố bất ngờ.

**Câu 5.** `finally` có phù hợp để đặt `order.confirmed = true` không?  
**Đáp án:** Không, vì `finally` còn chạy khi thao tác thất bại. Nó phù hợp cho dọn dẹp, không phải xác nhận thành công vô điều kiện.

**Câu 6.** `CompletableFuture.exceptionally(e -> fallback)` có nguy cơ gì?  
**Đáp án:** Nó có thể biến cả bug thành kết quả hoàn tất bình thường. Chỉ dùng fallback khi biết trạng thái và loại lỗi cho phép.

**Câu 7.** Bắt lỗi tại controller rồi trả HTTP 500 có giống nuốt lỗi trong service không?  
**Đáp án:** Không. Controller có thể kết thúc request bằng thất bại trung thực; service nuốt lỗi rồi tiếp tục thì có thể gây corruption. Dù vậy controller vẫn cần transaction/compensation thích hợp nếu state đã sửa.

**Câu 8.** Trường hợp nào nên chủ động cho worker khác tiếp tục?  
**Đáp án:** Khi các worker có trạng thái được cách ly và sự cố không làm hỏng bất biến chung; worker thất bại phải được đánh dấu và xử lý riêng.

**Câu 9.** Một lỗi mạng có chắc retry an toàn không?  
**Đáp án:** Không. Phải xem request đã tạo side effect chưa, lỗi có tạm thời không, và có idempotency hay đối soát không.

**Câu 10.** Sau khi bắt exception, điều kiện tối thiểu để tiếp tục là gì?  
**Đáp án:** Có chính sách phục hồi được thiết kế rõ, trạng thái và tài nguyên vẫn ở tình trạng hợp lệ, tác dụng phụ được kiểm soát và đầu ra phản ánh trung thực kết quả.

## 19. Tài liệu tham khảo

- [Java 17 — `RuntimeException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): nhóm ngoại lệ unchecked thường cần phân loại theo hợp đồng.
- [Java 17 — `IllegalStateException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalStateException.html): ngoại lệ biểu diễn trạng thái không cho phép thao tác.
- [Java 17 — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): stack trace, cause, suppressed exceptions và cơ chế ném/bắt.
- [Java 17 — `CompletableFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): trạng thái hoàn tất bất thường, `exceptionally`, `handle`, `join`.
- [Java 17 — `Objects.requireNonNull`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull(T)): xác nhận hợp đồng đối tượng không null.

**Ghi nhớ:** một `catch` không chứng minh có thể phục hồi. Khi gặp lỗi do vi phạm giả định nội bộ, **đừng tiếp tục dùng trạng thái có thể đã sai hoặc báo thành công giả**. Hãy kiểm tra bất biến trước khi công bố thay đổi, dừng đúng đơn vị công việc, giữ lỗi gốc cho chẩn đoán và chỉ tiếp tục khi có chiến lược phục hồi được kiểm chứng.

</details>

- [Quay lại đầu trang](#back-to-top)
