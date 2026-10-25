<a id="back-to-top"></a>

# Chính sách thực thi

## Menu
- [Đặt ranh giới giao dịch bằng advice theo khả năng nền tảng](#execution-policies-001)
- [Thứ tự giữa giao dịch, thử lại và ghi nhận kết quả](#execution-policies-002)
- [Thử lại và yêu cầu tính lặp an toàn của thao tác](#execution-policies-003)
- [Bộ nhớ đệm và điều kiện lời gọi có thể bỏ qua thực thi gốc](#execution-policies-004)
- [Thiết kế khóa bộ nhớ đệm theo đúng ngữ cảnh và dữ liệu](#execution-policies-005)
- [Giới hạn thời gian và giới hạn khả năng dừng tác vụ](#execution-policies-006)
- [Giới hạn tốc độ hoặc số lời gọi đồng thời](#execution-policies-007)
- [Không dùng aspect để che giấu thiếu sót trong hợp đồng nghiệp vụ](#execution-policies-008)
- [Đánh giá ảnh hưởng của mỗi chính sách tới lỗi và tác dụng phụ](#execution-policies-009)

## <a id="execution-policies-001">Đặt ranh giới giao dịch bằng advice theo khả năng nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt ranh giới giao dịch bằng advice theo khả năng nền tảng trong AOP

## 1. Bài toán: giao dịch là chính sách xuyên suốt lời gọi

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP), nhiều phương thức nghiệp vụ có cùng một chính sách: trước khi thực hiện một đơn vị công việc phải mở hoặc tham gia giao dịch, khi thực hiện thành công thì xác nhận, khi xảy ra lỗi thuộc chính sách rollback thì hủy những thay đổi chưa được xác nhận. Nếu lặp đoạn mã này trong mọi phương thức, logic nghiệp vụ bị trộn với điều phối giao dịch, khó kiểm tra và dễ bỏ sót đường lỗi.

**Transaction demarcation** là việc xác định chính xác **bắt đầu**, **phạm vi tham gia**, **kết thúc** và **người sở hữu kết thúc** một giao dịch. Một *around advice* có thể đặt chính sách ấy quanh lời gọi đến phương thức đích (*join point*):

~~~text
Bên gọi
   |
   v
[ Transaction advice ]
   | - chọn chính sách
   | - bắt đầu/tham gia giao dịch
   v
[ Phương thức nghiệp vụ ]
   | - gọi các tài nguyên tham gia
   v
[ Transaction advice ]
   | - commit, rollback hoặc rời giao dịch theo vai trò
   v
Bên gọi nhận giá trị / ngoại lệ
~~~

Tuy nhiên, **advice không tự cung cấp transaction manager, isolation, rollback hay khả năng khôi phục dữ liệu**. AOP chỉ là cơ chế đặt chính sách ở một ranh giới chương trình. Việc transaction thực sự có hiệu lực phụ thuộc khả năng intercept lời gọi của nền tảng AOP, transaction manager, sự hỗ trợ của tài nguyên (cơ sở dữ liệu, hệ thống thông điệp...) và hợp đồng đồng bộ/bất đồng bộ của API.

Nội dung bài học tập trung AOP tổng quát, từ mức khái niệm đến các giới hạn nâng cao khi áp dụng chính sách giao dịch bằng advice, không phụ thuộc một framework ứng dụng cụ thể.

## 2. Trước hết phải xác định “giao dịch” có nghĩa gì

Một giao dịch thường được kỳ vọng có các thuộc tính ACID trong phạm vi được tài nguyên hỗ trợ:

- **Atomicity (tính nguyên tử):** các thay đổi trong cùng transaction thành công như một đơn vị hoặc được rollback theo hợp đồng.
- **Consistency (tính nhất quán):** các ràng buộc dữ liệu vẫn được duy trì khi chuyển trạng thái hợp lệ; ứng dụng vẫn phải thực thi quy tắc nghiệp vụ.
- **Isolation (tính cô lập):** mức độ các giao dịch đồng thời có thể nhìn thấy thay đổi của nhau phụ thuộc isolation level thực tế.
- **Durability (tính bền vững):** sau commit thành công, tài nguyên bảo đảm độ bền theo cấu hình và hợp đồng của nó.

Không phải bất kỳ thao tác nào cũng có thể rollback: lời gọi HTTP đã phát ra, email đã gửi hoặc file đã đẩy tới dịch vụ ngoài **không tự quay ngược** chỉ vì cơ sở dữ liệu rollback.

Cần phân biệt ba tầng trách nhiệm:

| Tầng | Trách nhiệm | Không nên tự nhận |
| --- | --- | --- |
| **AOP/advice** | Chọn điểm áp chính sách, gọi begin/commit/rollback, phân loại outcome và bảo toàn ngữ nghĩa lời gọi | Không tự biến thao tác không có transaction thành thao tác nguyên tử |
| **Transaction manager / coordinator** | Tạo, liên kết, tạm ngưng, resume, commit/rollback transaction; quản lý trạng thái | Không tự làm mọi tài nguyên bên ngoài tham gia |
| **Resource/participant** | Thực hiện truy cập dữ liệu và cơ chế giao dịch thật theo connection/session/transaction | Không tự chia sẻ transaction với tài nguyên khác nếu không có protocol phù hợp |

Nếu một nền tảng chỉ có cơ chế AOP để chặn lời gọi mà không có transaction manager hay resource có khả năng giao dịch, advice vẫn có thể ghi log hoặc chặn thao tác, nhưng **không thể bảo đảm transaction thực** chỉ bằng `begin()` và `commit()` giả lập.

## 3. Tại sao *around advice* phù hợp nhất?

Các loại advice có khả năng khác nhau:

| Loại advice | Thông tin thời điểm | Vai trò thích hợp |
| --- | --- | --- |
| Before | Trước phương thức đích | Kiểm tra chính sách, khởi tạo context khi nền tảng hỗ trợ |
| After returning | Khi phương thức đích trả bình thường | Ghi nhận return, có thể đóng phạm vi nếu sở hữu giao dịch và đã xác định rõ lifecycle |
| After throwing | Khi phương thức đích ném lỗi đồng bộ | Ghi nhận ngoại lệ, đánh dấu rollback theo policy |
| After/finally | Sau khi lời gọi kết thúc bất kể outcome | Dọn context; không mặc định mọi outcome đều commit |
| **Around** | Bao trọn logic gọi `proceed()` và nhận outcome | Đặt đầy đủ begin/participate → proceed → commit/rollback → cleanup |

Around advice hữu ích vì nó giữ quyền quyết định **có gọi đích hay không**, **gọi lúc nào**, **giữ trạng thái gì qua lời gọi**, **xử lý cả đường return và throw ra sao**. Trong thiết kế transaction thông thường, advice cần bảo đảm:

1. Chọn transaction policy trước `proceed()`.
2. Nếu cần giao dịch mới, yêu cầu manager tạo giao dịch; nếu đang có giao dịch, xác định tham gia/tách giao dịch theo propagation policy.
3. Đặt transaction context ở đúng tài nguyên và phạm vi thực thi.
4. Gọi `proceed()` **đúng một lần** trong đường đi thông thường, trừ khi hợp đồng chính sách chủ đích có retry.
5. Nếu thành công và advice sở hữu giao dịch: commit, xử lý commit failure.
6. Nếu ném lỗi: quyết định rollback hay commit theo chính sách và mức ownership.
7. Tháo ràng buộc transaction context, giải phóng hoặc trả connection, khôi phục transaction bị tạm ngưng, không che lỗi gốc.

**Không đồng nhất việc advice đã chạy với việc transaction đã được resource áp dụng.** Việc kết nối thật sự tham gia transaction mới quyết định rollback/commit có tác động dữ liệu hay không.

## 4. Ranh giới lời gọi phải trùng với ranh giới nghiệp vụ mong muốn

Một chính sách giao dịch cần định nghĩa rõ **đơn vị công việc**. Ví dụ chuyển tiền giữa hai tài khoản cùng cơ sở dữ liệu:

~~~text
transfer(A, B, amount)
   ├── kiểm tra A còn đủ tiền
   ├── giảm số dư A
   ├── tăng số dư B
   └── ghi lịch sử thay đổi
~~~

Nếu toàn bộ thao tác này phải thành công hoặc thất bại cùng nhau, một transaction có thể bao quanh `transfer(...)` **nếu cả ba thay đổi được thực hiện qua cùng tài nguyên tham gia transaction**. Ngược lại, nếu tách transaction ở `debit()` và `credit()` độc lập, commit của bước giảm tiền có thể đã xảy ra khi bước tăng tiền thất bại; hai transaction độc lập không tự trở thành một đơn vị nguyên tử.

Khi chọn method join point, hãy phân biệt:

- **Method-level boundary:** trước/sau lời gọi phương thức. Dễ hiểu, nhưng có thể quá nhỏ, quá lớn, hoặc không đi qua cơ chế interception.
- **Use-case boundary:** bao toàn bộ một thao tác nghiệp vụ có yêu cầu nhất quán. Thường là điểm thích hợp để lựa chọn method transactional.
- **Resource boundary:** connection/session/transaction thật. Phải khớp vòng đời resource với phương thức được bao.
- **Cross-system boundary:** qua nhiều dịch vụ/tài nguyên. Một method-level advice đơn lẻ không đủ bảo đảm tính nguyên tử phân tán.

Một giao dịch kéo dài qua nhiều lời gọi đích không nhất thiết là một transaction mới cho mỗi lời gọi. Nếu các lời gọi con cần cùng đơn vị nhất quán, transaction manager phải hỗ trợ cơ chế tham gia/gộp context phù hợp.

## 5. Khả năng nền tảng AOP quyết định phạm vi tác dụng

“AOP có thể đặt advice” không đồng nghĩa “mọi đường gọi đều được chặn”. Việc một method được instrument thực tế phụ thuộc kỹ thuật:

| Cơ chế AOP | Có thể can thiệp ở đâu? | Hệ quả với transaction boundary |
| --- | --- | --- |
| **Decorator thủ công** | Những lời gọi thực sự đi qua đối tượng bọc | Gọi trực tiếp đối tượng gốc sẽ bỏ qua advice |
| **Dynamic proxy theo interface** | Lời gọi qua proxy tới các method của interface được hỗ trợ | Phương thức ngoài interface hoặc gọi nội bộ trực tiếp thường không được intercept |
| **Proxy dựa trên subclass** | Các method có thể override theo giới hạn của ngôn ngữ/runtime | Method `final`, `private`, constructor, visibility và thiết kế lớp có thể hạn chế |
| **Bytecode weaving** | Các join point được bộ weaving và cấu hình hỗ trợ | Phạm vi có thể rộng hơn proxy, nhưng phụ thuộc build/load-time instrumentation và pointcut |
| **Instrumentation/runtime agent** | Điểm được runtime cho phép biến đổi và hook vào | Có ràng buộc class loader, module access, thứ tự instrumentation, chi phí và tương thích |

Ví dụ, với proxy:

~~~java
class AccountOperations {
    public void outer() {
        inner(); // Lời gọi trực tiếp trên this, không đi qua proxy ngoài
    }

    public void inner() {
        // Công việc cần transaction.
    }
}
~~~

Đây là đoạn minh họa, không phải chương trình độc lập. Nếu chỉ `inner()` được proxy instrument nhưng `outer()` gọi `this.inner()`, lời gọi này thường **không đi qua proxy**, nên advice ở đường proxy của `inner()` không chạy. Nếu weaving intercept được execution join point tương ứng thì kết quả có thể khác. Phải xác minh đúng cơ chế đang dùng, không suy bằng tên annotation hoặc pointcut.

Cũng cần xét việc gọi từ constructor, phương thức static, method không thể override, gọi qua reflection và callback do runtime khác kích hoạt; mọi trường hợp chỉ được coi là có transaction khi công cụ AOP thực sự intercept được và resource tham gia đúng context.

## 6. Cơ chế chọn chính sách và propagation

Trước khi tạo giao dịch, advice phải biết policy cho join point. Policy có thể đến từ cấu hình bên ngoài, metadata/annotation tùy chọn, quy ước tên method, hoặc pointcut. Metadata chỉ có ý nghĩa nếu bộ chọn điểm cắt và bộ thực thi transaction **thực sự đọc và áp dụng** nó.

Một tập policy điển hình:

- **Propagation:** tham gia transaction đang có, bắt đầu transaction mới, yêu cầu phải có hoặc yêu cầu không có transaction.
- **Isolation:** mức cô lập theo khả năng resource/driver; không tự phát sinh chỉ vì policy gọi tên nó.
- **Timeout:** hạn giao dịch do coordinator/resource có thể thực thi; khác với timeout đợi của bên gọi.
- **Read-only:** có thể là hint tối ưu hoặc ràng buộc thật tùy hệ thống; không mặc nhiên chặn mọi lệnh ghi.
- **Rollback rules:** lỗi nào kích hoạt rollback, lỗi nào cho phép commit; phải ghi rõ cả trường hợp checked/unchecked.
- **Naming/correlation:** nhận diện giao dịch để debug và gắn trace.
- **Owner:** lời gọi hiện tại **sở hữu** transaction mới hay chỉ **tham gia** transaction của bên ngoài.

Propagation cần đặc biệt thận trọng:

| Chính sách khái niệm | Nếu đã có transaction | Nếu chưa có transaction | Lưu ý |
| --- | --- | --- | --- |
| **Join-or-create** | Tham gia hiện có | Tạo transaction mới | Phương thức con thường không tự commit giao dịch thuộc bên ngoài |
| **Create-new** | Tạm ngưng transaction hiện có và tạo cái mới nếu manager hỗ trợ | Tạo mới | Transaction con độc lập; commit của nó không tự rollback khi outer rollback |
| **Require-existing** | Tham gia | Báo lỗi chính sách | Không âm thầm tạo transaction |
| **Run-without** | Thực thi ngoài transaction nếu có thể, tạm ngưng nếu cần | Thực thi bình thường | Không đảm bảo mọi resource thật sự vô giao dịch |
| **Nested-savepoint** | Tạo phạm vi savepoint trong transaction hiện tại nếu hỗ trợ | Có thể tạo mới theo thiết kế | Rollback về savepoint không tương đương transaction độc lập |

Đây là **khái niệm chính sách**, không phải các tên enum được tất cả nền tảng hỗ trợ. Nếu manager không thể suspend/resume, không có savepoint hay resource không hỗ trợ mức isolation được yêu cầu, advice cần xử lý theo hợp đồng (thường từ chối hoặc báo không hỗ trợ), không giả vờ giao dịch đã có đầy đủ tính năng.

### Ownership: ai có quyền commit?

Ví dụ lời gọi `placeOrder()` mở transaction rồi gọi `reserveInventory()` tham gia cùng transaction. Advice của `reserveInventory()` có thể “rời phạm vi” nhưng **không nên tự commit** transaction đang thuộc về `placeOrder()`. Nếu method con có lỗi cần rollback, manager có thể đánh dấu toàn giao dịch là *rollback-only*; outer không được commit nhầm. Ngược lại, với transaction mới độc lập theo policy create-new, advice sở hữu giao dịch đó và cần kết thúc chính nó.

Đây là khác biệt giữa **thoát khỏi một advice** và **kết thúc transaction mà advice có quyền sở hữu**.

## 7. Ví dụ Java 17: mô phỏng *around advice* bằng dynamic proxy

Ví dụ sau chạy độc lập trên Java 17+, thể hiện cách advice bắt đầu context, gọi nghiệp vụ, commit nếu thành công và rollback nếu có exception. Nó dùng `java.lang.reflect.Proxy` (AOP ở mức proxy theo interface) và một sổ cái trong bộ nhớ. **Đây là mô hình transaction giáo dục**: không sử dụng database/transaction manager thực, không bảo đảm ACID dưới truy cập đồng thời hoặc sự cố tiến trình. Trong hệ thống thật, các lời gọi `begin/commit/rollback` phải được thay bằng coordinator/resource hỗ trợ transaction.

Lưu thành tệp **`TransactionAdviceDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;

public class TransactionAdviceDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TxBoundary {}

    interface TransferService {
        @TxBoundary
        void transfer(String from, String to, int amount, boolean fail);
    }

    static final class Ledger {
        private final LinkedHashMap<String, Integer> committed =
                new LinkedHashMap<>();

        // Chỉ để minh họa transaction context cho một luồng.
        private final ThreadLocal<LinkedHashMap<String, Integer>> draft =
                new ThreadLocal<>();

        Ledger() {
            committed.put("Alice", 100);
            committed.put("Bob", 50);
        }

        void begin() {
            if (draft.get() != null) {
                throw new IllegalStateException("Nested transaction unsupported");
            }
            draft.set(new LinkedHashMap<>(committed));
            System.out.println("[tx] begin");
        }

        void move(String from, String to, int amount) {
            LinkedHashMap<String, Integer> data = draft.get();
            if (data == null) {
                throw new IllegalStateException("No active transaction");
            }
            if (amount <= 0 || data.get(from) < amount) {
                throw new IllegalArgumentException("Invalid transfer");
            }
            data.put(from, data.get(from) - amount);
            data.put(to, data.get(to) + amount);
        }

        void commit() {
            LinkedHashMap<String, Integer> data = draft.get();
            if (data == null) {
                throw new IllegalStateException("No active transaction");
            }
            committed.clear();
            committed.putAll(data);
            draft.remove();
            System.out.println("[tx] commit");
        }

        void rollback() {
            draft.remove();
            System.out.println("[tx] rollback");
        }

        String snapshot() {
            return "Alice=" + committed.get("Alice")
                    + ", Bob=" + committed.get("Bob");
        }
    }

    static final class TransferServiceImpl implements TransferService {
        private final Ledger ledger;

        TransferServiceImpl(Ledger ledger) {
            this.ledger = ledger;
        }

        @Override
        public void transfer(
                String from, String to, int amount, boolean fail) {
            System.out.println("[service] moving " + amount);
            ledger.move(from, to, amount);
            if (fail) {
                throw new IllegalStateException("Simulated failure");
            }
        }
    }

    static TransferService withAdvice(
            TransferService target, Ledger ledger) {

        return (TransferService) Proxy.newProxyInstance(
                TransferService.class.getClassLoader(),
                new Class<?>[] { TransferService.class },
                (proxy, method, args) -> {
                    if (!method.isAnnotationPresent(TxBoundary.class)) {
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    }

                    ledger.begin();
                    try {
                        Object result = method.invoke(target, args);
                        ledger.commit();
                        return result;
                    } catch (InvocationTargetException ex) {
                        ledger.rollback();
                        throw ex.getCause();
                    } catch (Throwable ex) {
                        ledger.rollback();
                        throw ex;
                    }
                });
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        TransferService service =
                withAdvice(new TransferServiceImpl(ledger), ledger);

        System.out.println("initial: " + ledger.snapshot());

        service.transfer("Alice", "Bob", 30, false);
        System.out.println("after success: " + ledger.snapshot());

        try {
            service.transfer("Alice", "Bob", 10, true);
        } catch (IllegalStateException ex) {
            System.out.println("caught: " + ex.getMessage());
        }

        System.out.println("after failure: " + ledger.snapshot());
    }
}
~~~

Chạy:

~~~bash
javac TransactionAdviceDemo.java
java TransactionAdviceDemo
~~~

Đầu ra:

~~~text
initial: Alice=100, Bob=50
[tx] begin
[service] moving 30
[tx] commit
after success: Alice=70, Bob=80
[tx] begin
[service] moving 10
[tx] rollback
caught: Simulated failure
after failure: Alice=70, Bob=80
~~~

**Tại sao kết quả này đúng?**

- `withAdvice(...)` trả một dynamic proxy thực thi `TransferService`. Mỗi lời gọi `transfer(...)` từ bên ngoài đi qua `InvocationHandler`.
- Advice phát hiện metadata `@TxBoundary` trên method **interface**, bắt đầu bản sao trạng thái bằng `ledger.begin()` rồi gọi target đúng một lần qua reflection.
- Ở lần đầu, target giảm Alice từ 100 xuống 70 và tăng Bob từ 50 lên 80 trên bản nháp. Vì không có exception, advice commit bản nháp vào trạng thái chính thức.
- Ở lần sau, target thay đổi bản nháp rồi cố ý ném `IllegalStateException`. Reflection bọc exception trong `InvocationTargetException`; advice **unwrap cause**, rollback bằng cách bỏ bản nháp rồi ném lại nguyên nhân gốc. Trạng thái chính thức vẫn `Alice=70, Bob=80`.
- `ThreadLocal` chỉ minh họa việc liên kết context tới luồng gọi; **không phải cơ chế truyền transaction an toàn qua executor hoặc hệ thống phân tán**. Demo cũng không hỗ trợ nested transaction, retry, connection pooling, đồng thời, crash recovery hoặc isolation thực.
- Với method không có `@TxBoundary`, proxy minh họa chỉ forward. Nếu một method của đối tượng đích tự gọi phương thức khác của chính nó, đường gọi trực tiếp ấy có thể đi qua `this` và bỏ qua proxy.
- Exception trong ví dụ là unchecked, phù hợp kiểu method interface đã khai báo. Với checked exception, hợp đồng method/proxy phải khai báo hoặc bao lỗi đúng quy tắc ngôn ngữ; rollback policy phải định nghĩa riêng.

**Lưu ý khi đem ý tưởng vào ứng dụng thật:** cần transaction manager điều phối resource, xử lý failure ở commit/rollback, ownership và cleanup ngay cả khi có exception; không dùng thao tác sao chép toàn bộ map làm cơ sở transaction production. Ví dụ dùng một luồng tuần tự chỉ để cho thấy vị trí *advice* và sự khác nhau giữa commit/rollback.

## 8. Một khung xử lý lỗi production cần cân nhắc

Pseudocode sau diễn tả các bước điều phối; không phải API Java sẵn có và không phải chương trình độc lập:

~~~text
policy = resolvePolicy(joinPoint)
scope  = transactionManager.enter(policy)

try:
    value = proceedExactlyOnce()
    transactionManager.finishNormally(scope)
    return value
catch failure:
    transactionManager.finishWithFailure(scope, failure, policy.rollbackRules)
    rethrow original failure
finally:
    transactionManager.leaveScopeAndRestorePreviousContext(scope)
~~~

`finishNormally` **không nhất thiết là commit**: nếu chỉ tham gia transaction do outer sở hữu thì đơn giản có thể trả quyền điều khiển, commit ở outer. Tương tự, `finishWithFailure` có thể rollback transaction do lời gọi sở hữu, hoặc đánh dấu rollback-only đối với transaction tham gia bên ngoài.

Để tránh mất thông tin lỗi, hệ thống phải định nghĩa rõ ưu tiên và cách kết hợp các lỗi:

- **Lỗi nghiệp vụ** do `proceed()` ném.
- **Lỗi rollback** phát sinh khi đang xử lý lỗi nghiệp vụ.
- **Lỗi commit** sau khi logic nghiệp vụ chạy xong.
- **Lỗi cleanup/resume** khi khôi phục context hoặc đóng tài nguyên.

Không để lỗi rollback thứ cấp âm thầm che mất lỗi nghiệp vụ gốc. Có thể giữ lỗi đầu tiên làm nguyên nhân chính và ghi lỗi phụ qua `Throwable.addSuppressed(...)` khi thích hợp. Riêng commit failure có thể để lại outcome **không chắc chắn** tùy protocol/tài nguyên; không được tự tuyên bố transaction chắc chắn rollback thành công chỉ vì `commit()` đã ném.

Nếu manager đã phân loại transaction bị rollback-only, advice phía ngoài không được coi `proceed()` return bình thường là đủ điều kiện commit. Quyết định cuối phụ thuộc trạng thái coordinator.

## 9. Rollback policy không đồng nghĩa “bất kỳ lỗi nào cũng rollback”

AOP cho phép áp một chính sách thống nhất, nhưng chính sách phải được định nghĩa chặt:

1. **Exception ném ra khỏi method:** có thể là lỗi nghiệp vụ hoặc lỗi lập trình. Cấu hình rõ rollback cho toàn bộ lỗi hay tập loại cụ thể.
2. **Exception đã bị bắt và xử lý bên trong method:** advice ở ngoài không nhìn thấy lỗi đó nếu method return bình thường. Nếu nghiệp vụ muốn rollback, phải đánh dấu rollback-only hoặc báo thất bại qua hợp đồng được advice hiểu.
3. **Checked và unchecked exceptions:** có nền tảng phân biệt mặc định, có nền tảng không; AOP tổng quát không áp một quy tắc chung cho mọi bộ máy.
4. **Commit failure:** xảy ra sau khi `proceed()` trả bình thường. Không thể đồng nhất “business logic return” với “transaction committed”.
5. **Rollback-only khi tham gia transaction:** method con có thể không sở hữu quyền commit nhưng làm transaction lớn hơn không thể commit.
6. **Failure không được gửi ra bằng exception:** một method trả kiểu kết quả `Result.failure(...)` vẫn có thể return bình thường. Advice cần được thiết kế để hiểu outcome dạng giá trị nếu đó là chính sách mong muốn.

Ví dụ với method trả mã lỗi thay vì ném:

~~~java
Result transfer(...) {
    if (insufficientFunds) {
        return Result.failure("Insufficient funds");
    }
    // ...
    return Result.success();
}
~~~

Đây là mã minh họa, không phải chương trình Java độc lập. Advice chỉ kiểm tra “method có ném exception không?” sẽ xem mọi `return` là bình thường, bất kể `Result.failure`. Nếu hợp đồng nghiệp vụ yêu cầu thất bại dạng giá trị làm rollback, policy phải có predicate đánh giá kết quả hoặc method phải dùng cơ chế đánh dấu rollback-only đúng khả năng manager.

Không dùng rollback như cơ chế chung để đảo ngược side effect đã phát ra ngoài tài nguyên tham gia giao dịch.

## 10. Giao dịch và lời gọi bất đồng bộ: ranh giới return rất dễ sai

Một method trả `CompletionStage<T>` có thể chỉ đặt lịch tác vụ rồi return handle:

~~~text
advice begin
    method returns CompletionStage<T>
advice commit
    ... worker đang chạy ...
    worker ghi dữ liệu / thất bại
    stage hoàn tất
~~~

Nếu advice commit **ngay khi method trả handle**, transaction có thể kết thúc trước khi worker thật sự truy cập tài nguyên. Transaction context gắn với luồng gọi bằng `ThreadLocal` không tự động có mặt trên worker thread; ngay cả khi truyền một reference context sang luồng khác, manager/resource có thể cấm dùng đồng thời hoặc dùng sau khi transaction kết thúc.

Không được giải quyết bằng cách chặn `stage.join()` trong advice mà không xét hợp đồng: điều đó phá tính không chặn, có thể giữ connection/lock lâu và gây deadlock hoặc cạn tài nguyên.

**Các lựa chọn hợp lệ phải dựa trên khả năng nền tảng:**

- **Giao dịch bên trong worker:** mở/kết thúc transaction tại phần thực thi thật trên worker, đặt quanh thao tác tài nguyên. Đây thường là ranh giới dễ hiểu nếu framework giao dịch chủ yếu đồng bộ và gắn với luồng.
- **Transaction scope bất đồng bộ được hỗ trợ thật:** chỉ nếu coordinator và resource cung cấp API quản lý transaction xuyên callback/continuation/luồng, mới có thể thiết kế advice gắn với tín hiệu hoàn tất. Phải kiểm tra context propagation, thread safety, ownership, timeout và cancellation.
- **Chia thành các transaction ngắn:** nếu tác vụ có nhiều giai đoạn kéo dài, mỗi giai đoạn ghi dữ liệu sử dụng transaction độc lập và phối hợp kết quả bằng cơ chế nghiệp vụ phù hợp.
- **Bên gọi nhận handle không đại diện cho commit:** ghi nhận `method.returned` riêng với `transaction.committed` và `operation.completed`. Không suy ba sự kiện trùng nhau.

Chỉ gắn `whenComplete(...)` để gọi commit về sau **không đủ an toàn** nếu transaction manager không hỗ trợ giữ transaction đang hoạt động xuyên thời gian và thread của callback. Transaction kéo dài vô hạn còn có thể giữ lock/connection và gây nghẽn hệ thống. Với publisher, return publisher có thể xảy ra trước subscribe; phải gắn transaction với vòng đời subscription và tài nguyên nếu nền tảng hỗ trợ, tránh tự subscribe trong aspect làm phát sinh thêm công việc.

## 11. Khi nào transaction kéo qua nhiều tài nguyên?

Một advice có thể yêu cầu một coordinator kết thúc transaction, nhưng nếu công việc chạm **nhiều tài nguyên độc lập**, coordinator phải có cơ chế điều phối thực tế. Một số tình huống:

| Tình huống | Giới hạn | Hướng thiết kế |
| --- | --- | --- |
| Nhiều bảng trên cùng database/connection tham gia transaction | Thường có thể atomic nếu tài nguyên/hợp đồng hỗ trợ | Dùng một transaction manager và kết nối tham gia |
| Hai database/resource độc lập | Commit bên A và B không tự nguyên tử | Protocol giao dịch phân tán phù hợp nếu thực sự cần và được hỗ trợ |
| Ghi database + gửi email | Email không rollback bằng transaction database | Outbox, xử lý bất đồng bộ và idempotency |
| Ghi database + gọi API dịch vụ khác | Lời gọi HTTP không tự tham gia transaction local | Saga/compensating action hoặc giao thức phối hợp rõ |
| Publish message sau khi commit database | Có khe hở giữa commit và publish | Transactional outbox hoặc tích hợp nguyên tử thực sự nếu khả năng nền tảng có |

**Two-phase commit** hoặc giao thức tương tự có thể phối hợp tài nguyên hỗ trợ và được cấu hình đúng, nhưng làm tăng độ phức tạp, phụ thuộc availability và xử lý heuristic failure. **Saga/compensation** chỉ tạo sự nhất quán nghiệp vụ theo nhiều bước; nó không giống ACID rollback trên toàn bộ hệ thống.

Do đó, transaction advice là **điểm áp chính sách** tốt, nhưng không phải công cụ biến mọi side effect thành một đơn vị atomic phân tán.

## 12. Ordering với các aspect khác

Nhiều aspect có thể cùng áp lên một join point: authorization, validation, retry, cache, tracing, metrics, transaction. Thứ tự nesting thay đổi kết quả:

~~~text
A. Retry ngoài transaction:
retry {
    transaction {
        call business
    }
}

B. Transaction ngoài retry:
transaction {
    retry {
        call business
    }
}
~~~

Ở cấu hình A, mỗi lần retry có thể có transaction độc lập: một attempt thất bại rollback trước khi attempt mới bắt đầu. Ở cấu hình B, nhiều attempt có thể nằm chung một transaction; nếu attempt đầu đã đánh dấu rollback-only hoặc resource rơi vào trạng thái không thể tiếp tục, retry trong transaction ấy có thể không đạt ý định. Chọn A hoặc B phải xuất phát từ chính sách nghiệp vụ, retryability của lỗi và khả năng rollback/restore của tài nguyên.

Các điểm ordering khác:

- **Authorization** thường phải xảy ra trước thao tác nghiệp vụ; có thể tránh mở transaction khi chưa được phép.
- **Validation** trước transaction có thể giảm thời gian giữ lock, nhưng validation dựa trên dữ liệu đồng thời đôi khi cần kiểm tra lại bên trong transaction.
- **Cache** có thể bỏ qua lời gọi đích; nếu aspect transaction nằm phía trong cache thì cache hit không bắt đầu transaction.
- **Logging/metrics** đặt ngoài transaction đo end-to-end bao cả commit; đặt trong transaction có thể không đo chi phí commit.
- **Audit/outbox** phải quyết định dữ liệu audit cần commit/rollback cùng nghiệp vụ hay độc lập.
- **Locking** nếu tham gia resource transaction thường được giải phóng theo trạng thái commit/rollback; dùng transaction quá rộng dễ gây tranh chấp.

Đừng chỉ quan tâm “advice có chạy không?”; phải vẽ rõ **thứ tự nesting, owner và thời điểm tài nguyên được giải phóng**.

## 13. Transaction context, luồng và tài nguyên

Transaction manager đồng bộ thường liên kết context với luồng hoặc một cấu trúc scoped context. Điều đó có hệ quả:

- **Self-invocation:** đường gọi bỏ qua proxy có thể không tạo/đổi policy.
- **Thread switch:** chuyển sang executor khác có thể làm mất context nếu không có cơ chế truyền hợp lệ.
- **Thread pool reuse:** phải cleanup trong mọi đường đi; `ThreadLocal` còn sót có thể ảnh hưởng công việc tiếp theo trên cùng worker thread.
- **Connection ownership:** việc begin/commit phải nhất quán với connection/session thật; sử dụng nhầm connection không tham gia transaction có thể auto-commit ngoài ý định.
- **Context suspension:** propagation create-new hoặc run-without có thể cần tạm ngưng và khôi phục transaction trước đó, không chỉ ghi đè biến trạng thái.
- **Cancellation/interruption:** không mặc định hủy thread đồng nghĩa rollback được mọi tài nguyên; cần đường cleanup chắc chắn.
- **Transaction timeout:** có thể được manager/resource thực thi, nhưng timeout chờ kết quả ở phía caller không nhất thiết chấm dứt tác vụ.

Khi advice sở hữu transaction mới, cần giải phóng tài nguyên sau commit/rollback. Nếu chỉ tham gia transaction đang có, advice phải tránh đóng context/connection thuộc sở hữu của outer. Việc khôi phục sai context có thể nguy hiểm hơn bản thân exception ban đầu.

## 14. Kiểm thử đúng hơn việc chỉ nhìn log

Đối với transaction aspect, một vài bài kiểm thử có giá trị hơn việc kiểm tra “log begin đã in”:

| Kiểm thử | Điều cần chứng minh |
| --- | --- |
| Thành công | Mọi thay đổi thuộc transaction commit đúng một lần |
| Exception đồng bộ ở giữa công việc | Không có partial commit; nguyên nhân lỗi không bị mất |
| Exception bị bắt và trả mã lỗi | Kết quả commit/rollback đúng với policy cho lỗi dạng giá trị |
| Method con tham gia transaction | Method con không tự kết thúc transaction thuộc outer |
| Self-invocation qua proxy | Hành vi phù hợp với giới hạn interception thực của nền tảng |
| Commit hoặc rollback bị lỗi | Outcome và lỗi phụ được phản ánh chính xác; cleanup vẫn chạy nếu có thể |
| Concurrent calls | Isolation/ownership và việc tách context hoạt động đúng theo khả năng resource |
| Chạy từ executor/async callback | Transaction áp vào điểm thực thi thật, không bị kết thúc quá sớm |
| Retry + transaction | Mỗi attempt nhận transaction đúng policy, không giữ transaction đã rollback-only |
| Tài nguyên ngoài transaction | Side effect không được quảng bá sai là có rollback nguyên tử |

**Kiểm tra dữ liệu sau rollback** phải đi qua tài nguyên thật nếu mục tiêu là kiểm chứng transaction production. Mock advice hoặc log cho thấy callback `rollback()` được gọi chưa đủ để chứng minh database không có partial write. Kiểm tra thêm thời gian giữ lock/connection, timeout và mối quan hệ với pool khi chạy tải thực tế.

## 15. Những lỗi thiết kế thường gặp

| Sai lầm | Vì sao không đúng | Cách điều chỉnh |
| --- | --- | --- |
| Nghĩ annotation/pointcut tự tạo transaction | Metadata không có tác dụng nếu không có interceptor/manager/resource | Xác minh toàn chuỗi hỗ trợ thực tế |
| Commit trong method con đang tham gia outer | Phá ownership và tính nguyên tử use-case | Commit ở phạm vi sở hữu transaction |
| Mở transaction ở lời gọi không đi qua proxy | Advice không chạy tại điểm đó | Chọn boundary đi qua proxy hoặc cơ chế weaving được hỗ trợ |
| Bọc transaction quá rộng quanh I/O dài | Giữ lock/connection lâu, tăng contention | Thu hẹp phần cần nhất quán, dùng phối hợp nhiều bước nếu cần |
| Dùng rollback để “thu hồi” email/HTTP | Side effect ở ngoài resource không tham gia | Outbox/compensation/idempotency |
| Commit ngay khi async method trả stage | Worker có thể chưa chạy hoặc chạy ở luồng khác | Đặt transaction quanh phần thực thi thật hoặc dùng manager async phù hợp |
| Giả định mọi exception đều rollback | Chính sách rollback khác nhau tùy cấu hình | Xác định rõ checked/unchecked, rollback-only, failure dạng giá trị |
| Nuốt exception trong advice | Caller nhận thành công giả; transaction có thể đã sai | Truyền nguyên nhân gốc, giữ lỗi phụ |
| Tạo transaction mới cho mọi method lồng nhau | Chia vụn một đơn vị nghiệp vụ thành nhiều commit | Chọn propagation/ownership rõ ràng |
| Retry trong transaction không thể dùng lại | Context rollback-only hoặc resource đã lỗi | Sắp xếp retry và transaction phù hợp |
| Tin rằng `readOnly` luôn cấm ghi | Nhiều resource chỉ coi đây là hint | Kiểm tra enforcement ở resource |
| Chỉ test advice được gọi | Không chứng minh dữ liệu đã rollback | Test trạng thái resource thật và failure modes |

## 16. Checklist triển khai

Trước khi đặt transaction bằng advice, hãy trả lời:

1. **Đơn vị nghiệp vụ nào cần nguyên tử?** Có phù hợp dùng một transaction local không?
2. **Join point có thật sự được intercept?** Qua interface proxy, subclass proxy hay weaving? Các lời gọi nội bộ có bị bỏ qua không?
3. **Ai cung cấp transaction manager và resource?** Có begin/commit/rollback thật không?
4. **Phương thức nào sở hữu transaction?** Khi lồng nhau, propagation, rollback-only và savepoint xử lý thế nào?
5. **Khi nào transaction kết thúc?** Sau return đồng bộ, hay sau một tín hiệu completion với coordinator hỗ trợ async thực sự?
6. **Rollback policy gồm những outcome nào?** Checked/unchecked exception, result báo lỗi, cancel, timeout?
7. **Có side effect ngoài transaction không?** Nếu có, cần outbox, compensation hay cơ chế khác?
8. **Có aspect khác chen vào không?** Thứ tự retry, cache, validation, audit và tracing ảnh hưởng commit ra sao?
9. **Context có an toàn qua thread và request không?** Resource có được đóng và context được khôi phục ở mọi đường đi không?
10. **Bằng chứng kiểm thử là gì?** Có kiểm tra rollback thực tế, concurrent isolation, commit failure và async path không?

## 17. Kết luận kỹ thuật

**Around advice** là công cụ tự nhiên để áp chính sách transaction quanh một lời gọi, nhưng tính đúng đắn nằm ở sự ăn khớp giữa **ranh giới nghiệp vụ**, **join point được nền tảng AOP hỗ trợ**, **transaction manager sở hữu scope**, **resource thật sự tham gia** và **thời điểm công việc hoàn tất**. Advice có thể điều phối begin/commit/rollback, không thể tự tạo atomicity vượt qua khả năng của nền tảng hoặc biến một lời gọi bất đồng bộ đã return thành công việc đã commit.

Thiết kế tốt phải làm rõ ownership, propagation, rollback policy, error handling, ordering với các aspect khác và giới hạn liên luồng/liên hệ thống. Chỉ sau khi những điều kiện đó được thỏa mãn mới có thể coi ranh giới transaction đặt bằng advice là có hiệu lực đúng theo hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-002">Thứ tự giữa giao dịch, thử lại và ghi nhận kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự giữa giao dịch, thử lại và ghi nhận kết quả trong AOP

## 1. Tại sao thứ tự advice quyết định hành vi?

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), nhiều **aspect** có thể tác động lên cùng một join point. Ví dụ, một lời gọi nghiệp vụ có thể đồng thời cần:

- **Giao dịch (transaction):** xác lập ranh giới commit hoặc rollback cho những thao tác thuộc giao dịch.
- **Thử lại (retry):** thực hiện lại khi gặp một số lỗi được xác định là có thể phục hồi.
- **Ghi nhận kết quả (result recording):** ghi log, metric, audit hoặc phát sự kiện về thành công và thất bại.

Mỗi concern có lý do tồn tại riêng, nhưng **thứ tự bao quanh** của các advice làm thay đổi phạm vi nhìn thấy, vòng đời tài nguyên, số lần thực thi nghiệp vụ và ý nghĩa của “thành công”.

Giả sử hành vi đích là **operation()**. Biểu thức:

~~~text
Retry(Transaction(operation))
~~~

có nghĩa: retry ở ngoài, mỗi lượt retry đi vào transaction mới. Ngược lại:

~~~text
Transaction(Retry(operation))
~~~

có nghĩa: mở một transaction bên ngoài, rồi thử lại nhiều lần bên trong transaction ấy. Hai biểu thức không tương đương.

Đây là ký pháp mô hình hóa thứ tự bao quanh, **không phải cú pháp khai báo của một thư viện AOP**. Công cụ AOP cụ thể có thể dùng precedence, priority, explicit ordering hoặc quy tắc weaving riêng. Cần kiểm tra thứ tự thực tế ở runtime thay vì suy đoán từ tên aspect hoặc thứ tự xuất hiện trong mã nguồn.

**Mục tiêu học tập:** từ một tập advice và một join point, xác định chính xác advice nào là lớp ngoài, lớp trong; transaction bắt đầu và kết thúc lúc nào; lỗi nào dẫn đến retry; trạng thái nào được ghi theo từng attempt và trạng thái nào thực sự là kết quả cuối.

## 2. Ba chính sách phải được xác định độc lập

### 2.1. Chính sách giao dịch

Một transaction cung cấp ranh giới thống nhất cho các thao tác tài nguyên có tham gia vào transaction đó. Trong trường hợp thông thường, thành công có nghĩa transaction đã **commit thành công**; thất bại cần xử lý **rollback** hoặc một trạng thái chưa chắc chắn tùy hệ thống.

Một advice bao giao dịch thường tuân theo trình tự khái niệm:

~~~text
transaction advice:
    begin transaction
    try:
        value = proceed()
        commit transaction
        return value
    catch failure:
        rollback nếu còn phù hợp
        rethrow failure
    finally:
        release/restore transaction context and resources
~~~

Đây là giản lược để học cách xếp lớp. Khi **commit thất bại**, một số hệ thống đã kết thúc transaction, một số có trạng thái kết quả không chắc chắn, và thao tác rollback sau đó có thể không còn hợp lệ. Implementation thực phải tuân theo hợp đồng transaction manager, tài nguyên và giao thức commit. Không giả định rằng cứ bắt được lỗi commit là chắc chắn không có thay đổi nào đã được ghi nhận.

Ngoài ra, transaction chỉ bao phủ tài nguyên thực sự tham gia. Một cuộc gọi HTTP hoặc email gửi từ trong transaction cơ sở dữ liệu không tự bị rollback theo dữ liệu cơ sở dữ liệu.

### 2.2. Chính sách retry

Retry quyết định **lỗi nào** được thử lại, **số lượt tối đa**, khoảng chờ, deadline, cancellation và cách báo lỗi cuối. Retry phải bảo toàn nguyên nhân lỗi cho người gọi khi không còn điều kiện thử tiếp.

Ví dụ:

~~~text
retry advice:
    for attempt in 1..maxAttempts:
        try:
            return proceed()
        catch failure:
            if failure not retryable:
                throw failure
            if attempt is last:
                throw failure
            wait according to backoff/deadline policy
~~~

Một vài lỗi có thể tạm thời như tranh chấp khóa, deadlock được báo có thể retry hoặc sự cố kết nối ngắn hạn; lỗi cú pháp, dữ liệu không hợp lệ hoặc bất biến nghiệp vụ bị vi phạm thường không thể sửa chỉ bằng gọi lại. Phân loại phải dựa trên **hợp đồng thực tế của hệ thống và ngữ nghĩa lỗi**, không dựa vào tên ngoại lệ một cách tùy tiện.

Retry không phải lúc nào cũng an toàn. Nếu lần trước đã gửi thanh toán hoặc commit thành công nhưng phản hồi bị mất, gọi lại có thể gây tác động phụ lặp. Vì vậy **idempotency** và trạng thái commit là một phần bắt buộc của thiết kế retry.

### 2.3. Chính sách ghi nhận kết quả

“Ghi nhận kết quả” có thể mang nhiều nghĩa:

- Ghi **mỗi attempt**: biết lần đầu thất bại, lần thứ hai thành công.
- Ghi **kết quả cuối của operation**: người gọi cuối cùng nhận thành công hay lỗi sau tất cả các attempt.
- Ghi **kết quả commit**: xác nhận transaction đã commit hay thất bại.
- Ghi **audit bền vững**: lưu bằng chứng được yêu cầu vào kho lưu trữ phù hợp.
- Phát **sự kiện hậu giao dịch**: hệ thống khác phải thấy thay đổi đã cam kết.

Những kết quả này không thể tráo đổi. Một attempt thất bại có thể nằm trong operation thành công; một hàm nghiệp vụ trả về bình thường có thể đi trước một lần commit thất bại; một bản ghi log được in ra không phải bằng chứng chắc chắn rằng commit đã xảy ra.

Muốn ghi nhận đúng, phải xác định **đối tượng được ghi**, **mốc thời gian**, **định nghĩa outcome**, **độ bền dữ liệu yêu cầu** và **ai chịu trách nhiệm khi ghi nhận thất bại**.

## 3. Cách đọc thứ tự khi advice lồng nhau

Có thể hình dung mỗi around advice như một hàm nhận vào hành vi phía trong:

~~~text
Outer(
    Middle(
        Inner(
            operation
        )
    )
)
~~~

Lượt đi vào: Outer → Middle → Inner → operation. Lượt trở về bình thường: operation → Inner → Middle → Outer. Ngoại lệ có thể đi qua các lớp ngược chiều, trừ khi một lớp bắt rồi xử lý hoặc biến đổi nó.

Một chi tiết quan trọng: **lớp bao ngoài nhìn thấy toàn bộ việc hoàn tất của lớp phía trong**. Do đó, nếu Transaction bao quanh operation và thực hiện commit trước khi trả về, một advice ghi kết quả đặt bên ngoài Transaction có thể đánh giá cả outcome của commit; advice đặt bên trong Transaction thường chưa biết commit cuối cùng thành công hay chưa.

Sơ đồ:

~~~text
Record(
  Transaction(
    operation
  )
)

vào: Record -> begin -> operation
ra thành công: operation -> commit -> Record thấy success
ra lỗi: operation/commit -> xử lý transaction -> Record thấy failure
~~~

So với:

~~~text
Transaction(
  Record(
    operation
  )
)

vào: begin -> Record -> operation
ra: operation -> Record ghi success -> commit
                                   ^ commit vẫn có thể thất bại
~~~

Đặt Record bên trong Transaction còn có thể khiến log hoặc ghi nhận “success” xảy ra trước commit, trong khi transaction có thể rollback về sau. Đối với log chẩn đoán, có thể chấp nhận nếu sự kiện được đặt tên là “business-returned” hoặc “attempt-finished-before-commit”; đối với báo cáo **đã cam kết**, đó là sai ranh giới.

## 4. Thứ tự giữa retry và transaction: lựa chọn nền tảng

### 4.1. Retry bao transaction: mỗi attempt có transaction riêng

~~~text
Retry(
  Transaction(
    operation
  )
)
~~~

Dòng thời gian khi attempt đầu thất bại có thể retry:

~~~text
Retry attempt #1
    Transaction T1: begin
        operation -> transient failure
    Transaction T1: rollback/close
Retry quyết định thử tiếp

Retry attempt #2
    Transaction T2: begin
        operation -> returns
    Transaction T2: commit
Retry returns success
~~~

Ý nghĩa:

1. Mỗi attempt có ranh giới transaction mới, nếu transaction advice thực sự tạo scope mới theo hợp đồng.
2. Lỗi được retry sẽ được nhìn thấy **sau khi** lớp transaction đã xử lý thất bại và trả quyền điều khiển ra ngoài.
3. Nếu commit được thực hiện trong lớp transaction, retry có thể quan sát cả lỗi commit được truyền ra.
4. Một attempt đã thất bại không khiến transaction đang dùng cho attempt sau bị giữ trong trạng thái rollback-only hoặc aborted.
5. Các tác động phụ ngoài transaction vẫn phải được quản lý để retry không tạo kết quả lặp.

Đây thường là cấu trúc phù hợp cho retry **toàn bộ một đơn vị công việc giao dịch**, đặc biệt khi một lỗi khiến transaction hiện hành không còn có thể tiếp tục. Tuy nhiên, vẫn cần điều kiện retry cụ thể, idempotency và kiểm tra trường hợp kết quả commit không chắc chắn.

**Điều kiện then chốt:** lớp Transaction phải thật sự tạo transaction độc lập cho mỗi attempt. Nếu cơ chế giao dịch ngầm tái sử dụng một transaction bao ngoài hiện hữu, biểu thức hình thức trên chưa chứng minh rằng mỗi attempt có một giao dịch mới. Phải kiểm tra chính sách join/create của giao dịch và môi trường thực thi.

### 4.2. Transaction bao retry: mọi attempt trong một transaction

~~~text
Transaction(
  Retry(
    operation
  )
)
~~~

Dòng thời gian:

~~~text
Transaction T1: begin
    Retry attempt #1
        operation -> failure
        Retry bắt lỗi
    Retry attempt #2
        operation -> returns
Transaction T1: commit?
~~~

Rủi ro không nằm ở cú pháp, mà ở **trạng thái transaction sau thất bại đầu tiên**:

- Một số hệ quản trị đánh dấu transaction là aborted sau một loại lỗi, nên những câu lệnh tiếp theo không thể chạy thành công cho đến khi rollback.
- Một cơ chế quản lý transaction có thể đánh dấu transaction **rollback-only**, kể cả khi lớp Retry bắt ngoại lệ. Lần commit cuối vì vậy vẫn thất bại hoặc dẫn đến rollback.
- Trạng thái trung gian của attempt trước có thể còn tác động tới attempt tiếp theo nếu chưa rollback đến một ranh giới hợp lệ.
- Lock hoặc resource có thể bị giữ xuyên suốt các attempt, tăng thời gian chờ và nguy cơ tranh chấp.

Cấu trúc này **không phải luôn sai**: đôi khi nhiều phép thử được thiết kế để cùng thuộc một giao dịch, hoặc có cơ chế savepoint phù hợp. Nhưng không được giả định “bắt được exception thì transaction sạch như trước”. Muốn dùng, phải chứng minh mọi lỗi được retry cho phép tiếp tục transaction theo hợp đồng và có chiến lược rollback/savepoint chính xác.

### 4.3. Savepoint và transaction lồng nhau

Savepoint có thể cho phép quay lại một trạng thái bên trong transaction hiện tại để thử một bước khác mà không bỏ cả transaction ngoài. Tuy nhiên:

- Savepoint không đồng nghĩa một transaction top-level độc lập.
- Khả năng recovery sau lỗi phụ thuộc loại lỗi và hệ quản trị tài nguyên.
- Một số lỗi làm cả transaction mất khả năng tiếp tục, bất kể có ý định rollback tới savepoint.
- Các tác động phụ bên ngoài transaction không được khôi phục nhờ savepoint.

Hãy chọn savepoint khi thật sự muốn giữ transaction ngoài và có bằng chứng runtime hỗ trợ; nếu mục tiêu là retry toàn bộ đơn vị giao dịch, tạo transaction mới mỗi attempt thường là mô hình rõ ràng hơn.

## 5. Đặt advice ghi nhận kết quả ở đâu?

Không có một vị trí Record duy nhất đúng cho mọi mục đích. Phải phân biệt **record final** và **record attempt**.

### 5.1. Ghi nhận kết quả cuối cùng sau retry và commit

~~~text
RecordFinal(
  Retry(
    Transaction(
      operation
    )
  )
)
~~~

RecordFinal ở ngoài cùng quan sát một lượt xử lý logic hoàn chỉnh. Nếu Retry kết thúc thành công sau attempt thứ ba và transaction cuối commit thành công, RecordFinal ghi **một lần success**. Nếu hết lượt hoặc gặp lỗi không được retry, RecordFinal ghi **một lần failure**.

Vì RecordFinal bao quanh Transaction, điều kiện “success” có thể bao gồm commit, với giả định transaction wrapper chỉ return sau khi commit được xác nhận.

RecordFinal **không tự có thông tin chi tiết về từng attempt** nếu Retry không xuất số attempt hoặc không có observer bên trong.

### 5.2. Ghi nhận từng attempt, bao gồm transaction outcome

~~~text
Retry(
  RecordAttempt(
    Transaction(
      operation
    )
  )
)
~~~

Mỗi lượt Retry đều đi qua RecordAttempt. RecordAttempt thấy commit/rollback outcome của attempt đó theo hợp đồng của Transaction. Ví dụ, attempt 1 thất bại, attempt 2 thành công thì có hai bản ghi.

Nếu chỉ sử dụng Recorder ở vị trí này mà dùng tổng số bản ghi failure để suy ra số operation thất bại, số liệu sẽ sai: một operation có thể thành công sau retry nhưng có một hoặc nhiều attempt lỗi.

### 5.3. Kết hợp hai mức quan sát

~~~text
RecordFinal(
  Retry(
    RecordAttempt(
      Transaction(
        operation
      )
    )
  )
)
~~~

Đây là mô hình tham khảo khi cần **cả outcome tổng thể lẫn từng lượt thử**:

- RecordFinal ghi đúng một outcome của operation tại mốc cuối.
- RecordAttempt ghi outcome của mỗi attempt sau phần xử lý transaction.
- Transaction sở hữu begin/commit/rollback của attempt.
- Retry quyết định có tạo attempt tiếp theo hay dừng.

Ví dụ attempt 1 thất bại, attempt 2 thành công:

~~~text
attempt=1 outcome=failure   (transaction T1 rollback)
attempt=2 outcome=success   (transaction T2 commit)
operation outcome=success   (retry kết thúc)
~~~

Bản ghi **attempt=failure** không mâu thuẫn với **operation=success**, vì chúng mô tả hai đơn vị đếm khác nhau.

Nếu metric phân biệt hai mức, đặt tên rõ như operation_attempts_total và operations_total (tên chỉ minh họa); không cộng chung thành “số request lỗi”.

### 5.4. Ghi nhận bên trong transaction: khi nào hợp lệ?

~~~text
Transaction(
  Record(
    operation
  )
)
~~~

Record nhìn thấy operation trả về nhưng chưa thấy commit. Vị trí này hợp lý nếu mục đích là log chẩn đoán *business returned*, quan sát những sự kiện trước commit hoặc ghi một phần dữ liệu nghiệp vụ **cùng transaction** để dữ liệu ấy rollback cùng dữ liệu chính.

Nhưng một audit ghi cùng transaction sẽ bị rollback nếu transaction rollback; nó **không thể** làm nguồn ghi nhận bền vững cho mọi lần thất bại. Nếu cần audit thất bại tồn tại dù giao dịch chính rollback, phải dùng một cơ chế lưu bền độc lập và cân nhắc tính nhất quán/bảo mật, chứ không chỉ đổi tên advice thành audit.

## 6. Ví dụ Java 17+ chạy được: thứ tự ghi nhận đầy đủ

Đoạn mã sau **mô phỏng chuỗi around advice bằng các wrapper Java thuần**, không phải implementation transaction database hay cơ chế weaving. Wrapper Transaction chỉ in begin/commit/rollback nhằm làm rõ thứ tự; attempt đầu cố ý ném lỗi tạm thời, attempt thứ hai thành công.

Tạo tệp **AdviceOrderDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public final class AdviceOrderDemo {
    @FunctionalInterface
    interface Work {
        String run();
    }

    static final class TransientFailure extends RuntimeException {
        TransientFailure() {
            super("temporary problem");
        }
    }

    static final AtomicInteger TRANSACTIONS = new AtomicInteger();

    static Work transaction(Work next) {
        return () -> {
            int id = TRANSACTIONS.incrementAndGet();
            System.out.println("tx " + id + " begin");
            try {
                String result = next.run();
                System.out.println("tx " + id + " commit");
                return result;
            } catch (RuntimeException failure) {
                System.out.println("tx " + id + " rollback");
                throw failure;
            }
        };
    }

    static Work retry(Work next, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        return () -> {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                System.out.println("retry attempt=" + attempt);
                try {
                    return next.run();
                } catch (TransientFailure failure) {
                    if (attempt == maxAttempts) {
                        throw failure;
                    }
                }
            }
            throw new AssertionError("unreachable");
        };
    }

    static Work recordAttempt(Work next) {
        return () -> {
            try {
                String result = next.run();
                System.out.println("attempt outcome=success");
                return result;
            } catch (RuntimeException failure) {
                System.out.println("attempt outcome=failure");
                throw failure;
            }
        };
    }

    static Work recordFinal(Work next) {
        return () -> {
            try {
                String result = next.run();
                System.out.println("operation outcome=success");
                return result;
            } catch (RuntimeException failure) {
                System.out.println("operation outcome=failure");
                throw failure;
            }
        };
    }

    static Work flakyBusiness() {
        AtomicInteger calls = new AtomicInteger();
        return () -> {
            int call = calls.incrementAndGet();
            System.out.println("business invocation=" + call);
            if (call == 1) {
                throw new TransientFailure();
            }
            return "OK";
        };
    }

    public static void main(String[] args) {
        Work operation = recordFinal(
                retry(
                        recordAttempt(
                                transaction(flakyBusiness())),
                        3));

        String result = operation.run();
        System.out.println("caller result=" + result);
    }
}
~~~

Chạy:

~~~bash
javac AdviceOrderDemo.java
java AdviceOrderDemo
~~~

Stdout chính xác:

~~~text
retry attempt=1
tx 1 begin
business invocation=1
tx 1 rollback
attempt outcome=failure
retry attempt=2
tx 2 begin
business invocation=2
tx 2 commit
attempt outcome=success
operation outcome=success
caller result=OK
~~~

Kết quả minh họa các điều cần quan sát:

1. Retry là lớp ở ngoài Transaction: attempt 1 dùng T1, attempt 2 dùng T2.
2. RecordAttempt đặt bên ngoài Transaction: ghi failure **sau rollback** và success **sau commit** của wrapper.
3. RecordFinal đặt bên ngoài Retry: chỉ ghi một success cuối, dù trước đó có thất bại tạm thời.
4. Retry bắt riêng TransientFailure. Các RuntimeException khác truyền lên ngay; không bị thử lại vô điều kiện.
5. RecordAttempt và RecordFinal ném lại lỗi, không thay thế outcome của hành vi đích.

Trong hệ thống thực, lệnh commit có thể ném ngoại lệ, có thể có rollback-only hoặc kết quả chưa chắc chắn; wrapper mô phỏng này không thay thế transaction manager. Việc ghi log đơn giản cũng không đảm bảo audit bền vững hay “exactly once”.

## 7. Cơ chế lỗi theo vị trí phát sinh

### 7.1. Lỗi trong phương thức nghiệp vụ

Nếu operation ném lỗi trước commit:

~~~text
RecordFinal -> Retry -> RecordAttempt -> Transaction -> operation throws
Transaction handles rollback
RecordAttempt records failed attempt
Retry classifies failure, either repeats or rethrows
RecordFinal records final outcome after Retry finishes
~~~

Nếu lỗi không retryable, không tạo attempt mới. Nếu retryable và chưa hết budget, lặp lại Transaction ở lượt mới. Dù retry quyết định thế nào, ghi kết quả cuối phải phản ánh trạng thái người gọi thực sự nhận được từ toàn bộ composition.

### 7.2. Lỗi tại commit

Có trường hợp operation **đã trả về bình thường**, nhưng transaction commit lỗi. Nếu Record ở bên trong Transaction, nó đã ghi success quá sớm. Nếu RecordAttempt bên ngoài Transaction, nó nhìn thấy lỗi commit được wrapper truyền ra.

Tuy nhiên, **commit exception không đồng nghĩa chắc chắn rollback**. Ví dụ, phản hồi xác nhận có thể bị mất sau khi hệ thống lưu trữ đã hoàn tất commit. Retry mù toàn bộ operation trong tình huống này có thể tạo bản ghi hoặc tác động phụ lần hai.

Cách xử lý phụ thuộc vào tính chất resource manager và giao thức commit: dùng idempotency key, khóa nghiệp vụ duy nhất, kiểm tra trạng thái theo một mã yêu cầu ổn định hoặc cơ chế reconciliation. Trong log, outcome có thể cần trạng thái **unknown/in-doubt** thay vì ép về failure có rollback chắc chắn.

### 7.3. Lỗi khi rollback

Rollback cũng có thể thất bại. Cần giữ thông tin về **lỗi nghiệp vụ ban đầu** và lỗi rollback để chẩn đoán. Không tùy tiện thay thế lỗi gốc bằng lỗi xuất hiện khi ghi log hoặc dọn dẹp.

Trong Java, khi dùng cơ chế đóng tài nguyên chuẩn, ngoại lệ thứ cấp có thể được lưu làm suppressed exception; còn việc kết hợp ngoại lệ nghiệp vụ và rollback phụ thuộc transaction API. Advice phải có chính sách bảo toàn nguyên nhân lỗi rõ ràng.

### 7.4. Lỗi trong advice ghi nhận

Nếu một recorder bên trong Retry ném ngoại lệ, Retry có thể vô tình coi lỗi **telemetry** là lỗi của operation và kích hoạt thêm attempt. Việc chỉ đặt đúng thứ tự lớp chưa đủ; phải xác định recorder là:

- **Best effort observation:** thất bại ghi log/metric không làm thay đổi kết quả nghiệp vụ; bắt và xử lý lỗi trong observer theo chính sách chẩn đoán.
- **Required durable write:** lỗi ghi audit là lỗi thực sự của operation; phải thiết kế vị trí transaction, retry và cơ chế bảo đảm dữ liệu một cách rõ ràng.

Không nên giữ nguyên recorder có thể throw tùy ý rồi đặt nó trong Retry mà không xét việc throw ấy khiến nghiệp vụ bị gọi lại.

### 7.5. Lỗi vĩnh viễn, timeout và cancellation

Một lỗi dữ liệu không hợp lệ thường nên kết thúc ngay. Timeout có thể là **hết thời gian chờ kết quả**, không chứng minh thao tác gốc đã ngừng; cancellation cũng có thể chỉ hủy việc chờ. Trước khi retry, cần biết lần trước có thể còn đang thực hiện, có khả năng commit và có thể tạo side effect hay không.

Nếu có deadline tổng thể, mọi khoảng backoff và thời gian của từng attempt phải nằm trong budget đó; không được chỉ tăng số lượt rồi kéo dài operation vô hạn.

## 8. Retry chỉ an toàn khi effect có cơ chế chống lặp

Thứ tự `Retry(Transaction(operation))` giúp tách giao dịch mỗi attempt **nếu transaction manager thật sự tạo scope mới**, nhưng **không** tự ngăn side effect ở nơi khác. Đặc biệt, attempt đầu có thể đã commit thanh toán trong khi response bị mất; attempt kế tiếp sẽ tạo hiệu ứng thứ hai nếu backend không nhận ra cùng **logical operation**.

~~~text
attempt=1  charge(key=PAY-104) → committed; response lost
attempt=2  charge(key=PAY-104) → return prior receipt; NO new charge
~~~

An toàn cần **idempotency key giữ nguyên qua attempts**, phạm vi khóa đúng tenant/operation, kiểm tra payload fingerprint khi sử dụng lại khóa, và lưu/cập nhật trạng thái đủ **nguyên tử, bền vững** ở nơi sở hữu effect. Correlation ID để trace và attempt ID để quan sát **không đương nhiên** là khóa chống trùng.

Nội dung chính của bài này là **thứ tự các advice và mốc ghi kết quả**: recorder cần ghi attempt khác với operation, còn lỗi commit không chắc chắn phải được đối soát trước retry. Đối với HTTP/email/message nằm ngoài giao dịch DB, áp dụng deduplication, outbox hoặc cơ chế phối hợp theo hợp đồng; rollback DB không tự thu hồi effect đó.

## 9. Ghi nhận kết quả bền vững sau commit

Một log in ra màn hình, bộ đếm metric và bản ghi audit trong database có **độ bảo đảm khác nhau**:

| Loại ghi nhận | Có thể ghi ở đâu? | Điều phải hiểu |
| --- | --- | --- |
| Log chẩn đoán per-attempt | Sau Transaction trong RecordAttempt | Ghi cả lỗi tạm thời; log có thể mất hoặc trùng tùy pipeline |
| Metric outcome cuối | Sau Retry trong RecordFinal | Một operation có một outcome; không dùng attempt failure làm operation failure |
| Audit là dữ liệu nghiệp vụ bắt buộc | Trong giao dịch thích hợp hoặc cơ chế lưu bền được thiết kế | Độ bền và rollback theo hợp đồng dữ liệu |
| Sự kiện công bố sau commit | Sau xác nhận commit hoặc qua cơ chế outbox | Không thể đảm bảo công bố một lần chỉ bằng gọi callback sau commit |
| Audit thất bại phải còn dù rollback | Kênh lưu bền độc lập với giao dịch bị rollback | Cần định nghĩa độ tin cậy, quyền truy cập và lỗi của kênh này |

### 9.1. Vì sao “ghi sau commit” vẫn có khe hở?

Xét:

~~~text
commit database -> process crash -> publish event
~~~

Database đã commit nhưng event không được gửi. Nếu đổi thứ tự:

~~~text
publish event -> process crash -> database rollback
~~~

Event đã đến nơi khác dù transaction chưa commit. Chỉ thay đổi thứ tự hai lời gọi **không tạo tính nguyên tử** giữa hai hệ thống độc lập.

### 9.2. Transactional outbox

Nếu cần dữ liệu đã commit cuối cùng phải dẫn đến một thông điệp, có thể dùng transactional outbox:

~~~text
Trong cùng database transaction:
    cập nhật dữ liệu nghiệp vụ
    lưu outbox record với event ID duy nhất
    commit cùng nhau

Sau commit:
    publisher đọc outbox
    gửi thông điệp
    đánh dấu trạng thái đã gửi theo cơ chế đáng tin cậy
    retry việc gửi khi cần

Consumer:
    xử lý idempotent/deduplicate bằng event ID
~~~

Outbox thu hẹp khe hở giữa dữ liệu nghiệp vụ và ý định phát sự kiện, nhưng publisher có thể gửi **nhiều lần** nếu crash giữa send và đánh dấu đã gửi. Vì vậy consumer cần xử lý trùng lặp hoặc một giao thức tương đương. “Exactly once” không tự xuất hiện chỉ vì có aspect và outbox.

**Lưu ý:** outbox là một chiến lược nghiệp vụ hạ tầng, không phải chức năng tự có của mọi advice. Một aspect có thể áp dụng chính sách, nhưng việc ghi outbox cần đúng transaction, schema và cơ chế phân phối.

## 10. Quan sát attempt và operation cho đúng thống kê

Giả sử 100 operation đến hệ thống. Có 20 operation phải retry một lần; trong 20 operation ấy, 18 cuối cùng thành công và 2 thất bại.

Khi đó các số liệu có thể là:

| Chỉ số | Diễn giải |
| --- | --- |
| operations_total | 100 operation gốc |
| operations_failed_total | 2 operation cuối cùng thất bại nếu 80 operation còn lại đều thành công |
| attempts_total | 120 lượt attempt |
| attempts_failed_total | Ít nhất 20 attempt đầu thất bại; số chính xác còn tùy outcome của lượt retry sau |
| retries_total | 20 lượt thử bổ sung |

Các giá trị giả định phục vụ phân biệt đơn vị đếm. Không lấy attempts_failed_total làm tỷ lệ lỗi cuối của người dùng. Có thể tính tỷ lệ cần retry từ số operation có attempt vượt 1, trong khi tỷ lệ lỗi cuối lấy từ RecordFinal.

Độ trễ cũng có hai định nghĩa:

- **Attempt duration:** thời gian của một lần Transaction + operation, có thể bao gồm commit/rollback.
- **Operation duration:** thời gian từ lúc vào RecordFinal đến lúc tất cả retry kết thúc, gồm cả backoff nếu policy có backoff.

Cùng một operation có thể có attempt nhanh nhưng operation tổng kéo dài. Metric nên có nhãn ổn định như operation, outcome, error_class đã chuẩn hóa; không dùng correlation ID hoặc idempotency key làm nhãn metric cardinality cao.

Một bản ghi nên thể hiện scope, ví dụ:

~~~text
event=attempt.finished operation=placeOrder attempt=2 outcome=success tx=committed
event=operation.finished operation=placeOrder attempts=2 outcome=success
~~~

Không cần ghi thông tin nhạy cảm vào log chỉ để tương quan; dùng ID và dữ liệu đã được kiểm soát.

## 11. Tương tác với thời gian và giới hạn retry

Một chính sách retry cần nhiều thứ hơn maxAttempts:

1. **Backoff:** khoảng nghỉ giữa attempt, có thể tăng dần; thêm jitter khi nhiều client có nguy cơ retry đồng thời.
2. **Deadline tổng:** giới hạn thời gian operation, không chỉ từng attempt.
3. **Timeout mỗi attempt:** hạn chế một lượt bị kẹt; phải hiểu hành vi của tài nguyên sau timeout.
4. **Phân loại lỗi:** thử lại khi có bằng chứng lỗi tạm thời và việc lặp an toàn.
5. **Cancellation:** dừng tạo attempt mới nếu operation đã bị hủy hoặc thread bị interrupt theo hợp đồng.
6. **Retry budget:** giới hạn lượng công việc bổ sung tránh gây quá tải khi dịch vụ downstream đang lỗi.

Nếu advice retry sử dụng Thread.sleep để chờ backoff trong một API đồng bộ, cần xử lý InterruptedException đúng ngữ nghĩa (thường khôi phục cờ interrupt và chấm dứt retry hoặc truyền tín hiệu lên theo hợp đồng), thay vì nuốt ngắt. Trong API bất đồng bộ, nên dùng cơ chế lập lịch phi chặn tương ứng.

**Đặc biệt tránh giữ transaction mở trong thời gian backoff** chỉ vì Transaction nằm ngoài Retry. Transaction dài có thể giữ lock và tài nguyên, làm nặng thêm nguyên nhân lỗi ban đầu.

## 12. Giá trị trả về, ngoại lệ và trạng thái nghiệp vụ

Advice thường dựa trên việc return/throw, nhưng không phải mọi thất bại nghiệp vụ đều được biểu diễn bằng exception:

~~~text
operation() -> Result(status = DECLINED)
~~~

Nếu Transaction coi return bình thường là điều kiện commit và Recorder coi return là success, hệ thống có thể vừa commit vừa ghi “success”, dù về nghiệp vụ kết quả là từ chối.

Phải thống nhất hợp đồng:

- **Technical success:** phương thức hoặc transaction hoàn tất theo giao thức mà không có lỗi kỹ thuật.
- **Business outcome:** APPROVED, DECLINED, NOT_FOUND, PARTIAL… tùy domain.
- **Final delivery outcome:** người gọi nhận được kết quả hay lỗi, có thể khác trạng thái hệ thống bên dưới trong trường hợp timeout/mất phản hồi.

Nếu một kết quả kiểu DECLINED cần rollback, adapter nghiệp vụ/transaction phải có chính sách tường minh. Nếu DECLINED là kết quả hợp lệ cần lưu lại thì commit có thể đúng. Không tự cho rằng mọi giá trị “false” phải rollback hoặc mọi return đều là thành công nghiệp vụ.

Retry cũng vậy: retry một kết quả nghiệp vụ chỉ khi hợp đồng rõ ràng cho phép; không mặc định retry tất cả exception hoặc tất cả mã trạng thái khác success.

## 13. Transaction propagation, transaction bao ngoài và thứ tự thực tế

Ngay cả khi thiết kế có vẻ là Retry(Transaction(operation)), kết quả còn phụ thuộc chính sách transaction khi đã tồn tại transaction bên ngoài:

~~~text
External transaction T0
    -> RecordFinal
       -> Retry
          -> Transaction wrapper
             -> operation
~~~

Nếu Transaction wrapper chỉ tham gia T0, mọi attempt có thể vẫn ở cùng T0. Muốn khởi tạo transaction mới mỗi attempt phải cấu hình/triển khai chính sách phù hợp với transaction manager và xác định hậu quả của việc tách transaction.

Cũng cần phân biệt:

- **Transaction nested** có thể dùng savepoint và vẫn thuộc một transaction top-level.
- **Transaction independent/new** có thể commit riêng, không tự rollback cùng transaction ngoài.
- **Distributed transaction** hoặc nhiều resource có thể có nhiều trạng thái commit khác nhau.
- **Tài nguyên ngoài giao dịch** không được bao phủ chỉ vì code nằm giữa begin và commit.

Đừng dùng tên advice, annotation hoặc vị trí trong source file làm chứng cứ về ranh giới thực. Kiểm tra scope và outcome ở runtime.

## 14. Điều gì xảy ra nếu có nhiều aspect chồng lên nhau?

Một điểm cắt có thể đồng thời bị ảnh hưởng bởi retry, transaction, cache, authorization, correlation, timeout và result recording. Khi đó thứ tự cần được xác định cùng nhau.

Ví dụ:

~~~text
CorrelationScope(
  RecordFinal(
    Authorization(
      Retry(
        RecordAttempt(
          Transaction(
            operation
          )
        )
      )
    )
  )
)
~~~

Ví dụ chỉ minh họa cách đọc thứ tự, **không đề xuất duy nhất** thứ tự cho authorization. Có hệ thống cần kiểm tra quyền một lần, có hệ thống cần kiểm tra lại mỗi attempt; lựa chọn ảnh hưởng semantics, chi phí và an toàn.

Tương tự, nếu Cache ở ngoài Transaction, một cache hit có thể khiến Transaction không chạy; điều đó có thể đúng khi không cần nghiệp vụ cập nhật, nhưng sai nếu audit hoặc invariant bắt buộc phải đi qua transaction. Nếu timeout ở ngoài Retry, nó thường là deadline toàn operation; nếu ở trong, timeout có thể được tính riêng từng attempt. Nếu correlation ở trong Retry, mỗi attempt có thể vô tình nhận correlation ID mới; nếu bên ngoài, có thể giữ chung ID và tạo attempt ID riêng.

**Bài học:** cần vẽ chuỗi bao quanh hoàn chỉnh cho join point thực tế, không đánh giá từng aspect tách rời.

Ngoài ra, ở một số kiểu AOP, advice chỉ áp dụng cho các join point thật sự được weaving hoặc dispatch qua cơ chế interception. Tự gọi phương thức trong cùng đối tượng, phương thức không thuộc phạm vi điểm cắt hoặc lời gọi đi tắt cơ chế interception có thể không kích hoạt advice tùy công cụ. Phải kiểm chứng đường gọi thực tế trước khi tin rằng transaction/retry/record đang hoạt động.

## 15. Bất đồng bộ làm thay đổi ranh giới “kết thúc”

Nếu một phương thức trả về CompletableFuture hoặc một handle bất đồng bộ, around advice theo **lượt thực thi phương thức** có thể kết thúc ngay lúc phương thức trả về handle, trong khi công việc vẫn đang diễn ra.

~~~text
RecordFinal(
  Retry(
    Transaction(
      methodReturningFuture
    )
  )
)
~~~

Biểu thức ấy **không chứng minh** transaction sống đến khi future hoàn tất, không chứng minh retry quan sát lỗi xảy ra về sau và không chứng minh RecordFinal nhìn thấy outcome cuối. Nếu methodReturningFuture trả về future bình thường nhưng future hoàn tất lỗi sau đó, các advice theo return/throw đồng bộ có thể đã báo “success” quá sớm.

Muốn áp dụng chính sách vào tác vụ bất đồng bộ thực sự, cần định nghĩa:

- **Ranh giới operation:** completion của future, callback cuối, hoặc terminal signal được API công bố.
- **Ranh giới transaction:** tài nguyên và thread nào thực hiện công việc; transaction được mở/đóng lúc nào, ai sở hữu nó.
- **Ranh giới retry:** mỗi attempt mới khởi tạo công việc và tài nguyên mới hay chỉ lấy lại handle cũ.
- **Ranh giới recorder:** ghi outcome cuối sau completion hợp đồng, không chỉ sau khi trả về future.
- **Truyền context:** correlation, cancellation và transaction context phải có cơ chế propagation thích hợp; không giả định ThreadLocal tự truyền sang executor khác.

Một transaction gắn với thread không thể tùy tiện giữ mở xuyên qua các callback chạy bất kỳ thread nào chỉ bằng cách trì hoãn return. Thiết kế cần cơ chế transaction thật sự hỗ trợ mô hình thực thi đang dùng, hoặc đặt transaction quanh phần công việc diễn ra tại worker.

## 16. Điều kiện đảm bảo mỗi kết quả chỉ được ghi một lần

Ngay cả RecordFinal nằm ngoài Retry, lời gọi có thể được thực hiện lại bởi client hoặc hệ thống hàng đợi, khiến toàn bộ composition được chạy lại. Vì vậy “một RecordFinal trên một lượt gọi” **khác** với “một bản ghi bền vững cho một hành động nghiệp vụ toàn cục”.

Có ít nhất ba mức đảm bảo:

1. **Một callback trong một lượt advice:** lập trình đúng scope để callback/observer không kích hoạt hai lần không chủ đích.
2. **Một outcome logic trong một execution:** recorder ngoài cùng chỉ ghi sau khi Retry kết thúc; tránh ghi final trong từng attempt.
3. **Một bản ghi nghiệp vụ bền vững qua retry/restart:** dùng khóa định danh nghiệp vụ, transaction, unique constraint, trạng thái bền và quy trình deduplication tương ứng.

Nếu hệ thống cần audit thất bại **và** audit thành công ngay cả khi tiến trình crash, một lời gọi log trong finally chưa đảm bảo điều đó. Cần cơ chế lưu trữ và xác nhận có thể kiểm tra lại sau sự cố.

## 17. Kiểm thử thứ tự: kiểm tra sự kiện, không chỉ kết quả trả về

Kết quả cuối “OK” chưa đủ chứng minh advice được xếp đúng. Cần kiểm tra **chuỗi sự kiện** và trạng thái giao dịch.

| Tình huống kiểm thử | Kỳ vọng của mô hình tham khảo |
| --- | --- |
| Thành công ngay attempt 1 | Một transaction commit, một attempt success, một final success |
| Attempt 1 lỗi retryable; attempt 2 thành công | T1 rollback, T2 begin/commit; hai attempt records, một final success |
| Lỗi không retryable | Không có attempt tiếp; rollback phù hợp, một final failure |
| Hết maxAttempts | Số lần gọi không vượt budget, final failure chỉ một lần |
| Lỗi tại commit | Attempt phải thể hiện commit failure/uncertain; không ghi committed success giả |
| Lỗi tại recorder best effort | Không vô tình gây retry nghiệp vụ |
| Recorder bắt buộc thất bại | Lỗi được xử lý theo hợp đồng về độ bền và transaction |
| Retry sau phản hồi commit bị mất | Không tạo side effect trùng nhờ idempotency/reconciliation |
| Transaction có sẵn bên ngoài | Xác minh thực tế transaction mới hay tham gia transaction cũ |
| Lời gọi trả về future | Không đánh đồng return handle với completion logic |

Một kỹ thuật hữu ích là tiêm fake/stub có khả năng ghi thứ tự sự kiện:

~~~text
events = [
  "retry-1",
  "tx-begin-1",
  "business-fail",
  "tx-rollback-1",
  "attempt-fail",
  "retry-2",
  "tx-begin-2",
  "business-ok",
  "tx-commit-2",
  "attempt-success",
  "operation-success"
]
~~~

Bài kiểm thử quan trọng ở đây là **quan hệ thứ tự và số lần xảy ra**. Trong thực tế, nên có thêm integration test với transaction manager và loại database sử dụng để xác nhận rollback-only, lock, commit failure và cấu hình propagation. Một fake in log không thể chứng minh transaction thật có đúng hành vi.

## 18. Cách ra quyết định khi thiết kế chính sách thực thi

Khi chuẩn bị nhiều advice cho một operation, lần lượt trả lời:

1. **Đơn vị retry là gì?** Một câu lệnh, một thao tác nghiệp vụ, hay toàn bộ giao dịch?
2. **Mỗi attempt cần transaction mới không?** Nếu có, phải bảo đảm transaction boundary được tạo lại thật sự.
3. **Lỗi nào retryable?** Phân loại theo hợp đồng, khả năng phục hồi và idempotency.
4. **Điều kiện thành công là gì?** Return bình thường, business result hợp lệ, commit xác nhận, hay phản hồi từ hệ thống downstream?
5. **Ghi nhận từng attempt hay chỉ final?** Nếu cả hai, dùng recorder ở hai tầng và đặt tên event khác nhau.
6. **Độ bền của bản ghi cần đến đâu?** Log/metric chẩn đoán, audit bắt buộc hay event hậu commit cần thiết kế khác nhau.
7. **Có side effect nằm ngoài transaction không?** Payment/email/message cần idempotency hoặc cơ chế riêng.
8. **Có transaction bao ngoài không?** Xem xét propagation, nested transaction, savepoint và trạng thái rollback-only.
9. **Join point có thực sự được instrument không?** Kiểm tra precedence, weaving, đường gọi và ranh giới bất đồng bộ.
10. **Trường hợp commit in-doubt sẽ xử lý thế nào?** Tránh retry mù; cần reconciliation nếu không biết kết quả.
11. **Khi observer lỗi thì sao?** Quy định best effort hay mandatory, tránh biến lỗi log thành retry nghiệp vụ ngoài ý muốn.
12. **Có thể quan sát trực tiếp chuỗi sự kiện không?** Dùng test xác minh số attempt, số commit, số final record và thời lượng.

## 19. Các lỗi phổ biến và cách sửa

**Lỗi 1: Transaction bao Retry nhưng kỳ vọng có transaction mới mỗi attempt.** Đây là nhầm ranh giới. Hãy đặt Retry ngoài wrapper tạo transaction, hoặc dùng cơ chế transaction explicit per-attempt có hợp đồng tương đương, rồi xác minh trong integration test.

**Lỗi 2: Ghi success của nghiệp vụ trước commit.** Advice ghi kết quả nằm trong Transaction có thể thấy operation trả về nhưng chưa biết commit. Hãy ghi trạng thái “returned” nếu chỉ đo lượt gọi; nếu cần “committed”, đặt observer ở vị trí nhìn thấy commit hoặc dùng cơ chế sau commit đủ tin cậy.

**Lỗi 3: Ghi final failure cho từng attempt lỗi.** Người dùng có thể vẫn nhận success sau retry. Hãy phân biệt RecordAttempt và RecordFinal, với outcome cuối chỉ được ghi sau Retry.

**Lỗi 4: Retry mọi RuntimeException.** Có thể retry cả lỗi dữ liệu, vi phạm nghiệp vụ và lỗi của chính hệ thống ghi log. Hãy giới hạn loại lỗi, max attempts, backoff, deadline và idempotency.

**Lỗi 5: Cho rằng rollback database thu hồi luôn side effect ngoài database.** Gửi email hoặc thanh toán ở attempt 1 có thể tồn tại sau rollback; attempt 2 có thể lặp tác động. Dùng outbox, idempotency key, deduplication hoặc điều phối tác vụ phù hợp.

**Lỗi 6: Cho rằng lỗi commit chứng minh operation chưa từng commit.** Phản hồi commit có thể bị mất. Hãy phân biệt failure chắc chắn với trạng thái unknown và dùng cơ chế kiểm tra/đối soát.

**Lỗi 7: Đặt recorder có thể throw bên trong Retry mà không phân biệt nguồn lỗi.** Một lỗi telemetry có thể làm operation bị thực thi lại. Định nghĩa recorder best effort hoặc bắt lỗi recorder riêng; nếu mandatory, thiết kế transaction và retry cho nó độc lập.

**Lỗi 8: Dùng metric failure của attempt làm failure rate của operation.** Hãy đo hai cấp độ và giữ mẫu số thống nhất.

**Lỗi 9: Retry bên trong transaction trong lúc chờ backoff dài.** Transaction có thể giữ resource/lock và làm tăng tải. Ưu tiên giải phóng transaction sau mỗi attempt khi chính sách yêu cầu giao dịch mới.

**Lỗi 10: Bỏ qua hợp đồng asynchronous.** Method return future không đồng nghĩa task hoàn tất. Áp dụng transaction, retry và recorder vào đúng vòng đời logic của tác vụ và đúng đơn vị thực thi.

## 20. Tình huống tự kiểm tra

**Tình huống A.** Một operation ném lỗi transient ở lần đầu, rồi trả về thành công ở lần thứ hai. Nếu dùng RecordFinal(Retry(RecordAttempt(Transaction(operation)))), có bao nhiêu record?

**Đáp án:** hai record ở cấp attempt (một failure, một success) và một record ở cấp operation (success). Transaction có hai lượt riêng nếu wrapper thật sự tạo mới mỗi lần. Final success chỉ xuất hiện sau khi commit của lượt thành công được xác nhận.

**Tình huống B.** Transaction(Retry(operation)) nhận một lỗi làm database đánh dấu transaction là aborted. Retry bắt lỗi và gọi lại ngay trong transaction ấy. Lần tiếp theo có chắc chạy thành công không?

**Đáp án:** không. Retry đã nuốt exception nhưng không làm transaction trở về trạng thái hợp lệ. Cần rollback transaction và tạo transaction mới, hoặc một cơ chế phục hồi/savepoint được tài nguyên thực sự hỗ trợ.

**Tình huống C.** Operation cập nhật database và gửi email trước commit. Attempt đầu gửi email thành công rồi lỗi DB rollback, attempt sau chạy lại. Có rủi ro nào?

**Đáp án:** email có thể được gửi hai lần vì email không tham gia rollback DB. Cần thiết kế idempotency, transactional outbox hoặc một chiến lược tách side effect theo hợp đồng.

**Tình huống D.** Business method trả về bình thường, nhưng commit ném lỗi. Recorder nằm ngay trong transaction wrapper và đã ghi “committed”. Đúng không?

**Đáp án:** sai. Recorder bên trong chỉ biết business method return, chưa biết commit. Nên quan sát transaction outcome ở bên ngoài hoặc ghi sự kiện mô tả đúng “business returned”.

**Tình huống E.** Một log exporter tạm thời không truy cập được, RecordAttempt ném lỗi và Retry gọi lại nghiệp vụ. Lỗi thiết kế ở đâu?

**Đáp án:** nguồn lỗi telemetry bị trộn với nguồn lỗi nghiệp vụ để retry. Cần chính sách best effort/mandatory tường minh và không cho lỗi log tự kích hoạt lặp nghiệp vụ ngoài dự kiến.

**Tình huống F.** Operation lần đầu đã commit nhưng client timeout và gửi lại cùng idempotency key. Retry an toàn cần gì?

**Đáp án:** xử lý idempotent ở phía thực thi, ví dụ unique key bền vững và trả lại kết quả đã có; timeout không phải bằng chứng rollback. Có thể cần reconciliation nếu không xác định được outcome.

## 21. Kết luận kỹ thuật

Thứ tự aspect là một phần của **hợp đồng thực thi**. Với một nghiệp vụ đồng bộ có retry toàn giao dịch, cấu trúc tham khảo rõ ràng là:

~~~text
RecordFinal(
  Retry(
    RecordAttempt(
      Transaction(
        operation
      )
    )
  )
)
~~~

Cấu trúc này cho phép mỗi attempt đi qua một transaction theo đúng chính sách tạo transaction; recorder bên trong retry ghi từng attempt **sau xử lý transaction**, recorder ngoài cùng ghi outcome **cuối cùng** sau toàn bộ retry. Tính đúng đắn vẫn phụ thuộc loại lỗi được retry, trạng thái commit, idempotency, độ bền của ghi nhận, transaction propagation và ranh giới thực thi bất đồng bộ.

Khi thiết kế, luôn xác định rõ **cái gì được lặp**, **cái gì được commit**, **khi nào được gọi là thành công**, và **mỗi bản ghi đại diện cho một attempt hay một operation**. Sau đó kiểm tra thứ tự sự kiện trên implementation thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-003">Thử lại và yêu cầu tính lặp an toàn của thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Thử lại và yêu cầu tính lặp an toàn của thao tác trong Aspect (AOP)

## 1. Mục tiêu và bài toán

**Retry (thử lại)** là một chính sách thực thi gọi lại một thao tác khi có lỗi được đánh giá là tạm thời. Với **Aspect-Oriented Programming (AOP)**, có thể đặt chính sách tại một `around advice` để nhiều phương thức cùng sử dụng các quy tắc về phân loại lỗi, số lần thử, thời gian chờ và ngân sách thời gian.

Tuy nhiên, tự động chạy lại một lời gọi mang rủi ro nghiêm trọng: lần trước **có thể đã thực hiện thành công hiệu ứng nghiệp vụ**, dù bên gọi vẫn nhận timeout, mất phản hồi hoặc ngoại lệ. Nếu lần gọi tiếp theo tạo thêm giao dịch, gửi thêm email hoặc trừ tiền thêm lần nữa, cơ chế retry đã thay đổi ý nghĩa nghiệp vụ.

Bài học giúp bạn:

- Phân biệt **retry**, **idempotency (tính lặp an toàn)**, **duplicate suppression** và bảo đảm **at-most-once / at-least-once / effectively-once**.
- Xác định lỗi nào có thể thử lại, lỗi nào cần dừng, và giới hạn retry bằng `maxAttempts`, backoff, jitter và deadline.
- Thiết kế advice bao quanh `proceed()` mà không làm mất nguyên nhân lỗi, không gọi lại tác vụ không an toàn và không nhân bội retry giữa các tầng.
- Thiết kế **idempotency key** và nơi lưu trạng thái để chịu được lời gọi trùng, request đồng thời, crash và kết quả không rõ ràng.
- Hiểu quan hệ của retry với giao dịch, API ngoài, message handler, luồng bất đồng bộ và observability.
- Viết và kiểm thử một interceptor AOP bằng Java 17.

Phạm vi là **AOP nói chung**: join point, pointcut, around advice, weaving hoặc proxy interception, không phụ thuộc một thư viện AOP cụ thể. Chi tiết API dùng để cài đặt advice có thể khác nhau, nhưng nguyên tắc về side effect và ranh giới trách nhiệm không đổi.

## 2. Ba khái niệm phải tách biệt

### 2.1. Retry không có nghĩa thao tác an toàn

Retry chỉ điều khiển việc **gọi lại**:

```text
caller -> advice -> operation
                  <- lỗi tạm thời
                  -> operation (lần nữa)
                  <- thành công / lỗi
```

Với tác vụ đọc dữ liệu hoặc tính toán thuần túy, thử lại thường dễ kiểm soát hơn. Với một lệnh ghi dữ liệu, tính an toàn của lần thử sau tùy vào **hợp đồng nghiệp vụ và thiết kế trạng thái**, không được suy ra chỉ từ kiểu ngoại lệ.

### 2.2. Idempotency là tính chất của kết quả tác động

Một thao tác `f` được gọi là **idempotent** khi áp dụng lặp lại có cùng hiệu quả quan sát được trên trạng thái mục tiêu như áp dụng một lần, với cùng dữ liệu/hợp đồng:

```text
f(f(state, command), command) = f(state, command)
```

Đây là mô hình khái niệm; phép so sánh cụ thể phải xác định cái gì được xem là **hiệu quả nghiệp vụ**. Ví dụ:

| Thao tác | Có an toàn khi gọi lại không? | Giải thích |
|---|---|---|
| Gán trạng thái tài khoản thành `ACTIVE` | Thường có, nếu hợp đồng và điều kiện ổn định | Gán cùng giá trị nhiều lần có thể không đổi trạng thái cuối |
| Tăng số dư thêm 100 | Không theo mặc định | Hai lần tăng tạo kết quả khác một lần |
| Tạo đơn hàng với ID mới mỗi lần | Không | Có thể tạo hai đơn |
| Tạo đơn với khóa nghiệp vụ duy nhất, trả lại kết quả cũ cho yêu cầu trùng | Có thể có | Cần kiểm soát cạnh tranh, payload, transaction và kho khóa |
| Đọc bản ghi | Thường an toàn về side effect | Nhưng kết quả có thể đổi giữa hai lần do cập nhật đồng thời |
| Gửi email | Không mặc định | Retry sau timeout có thể gửi hai email |

**Idempotent không đồng nghĩa trả về cùng mọi byte trong mọi lần gọi.** Ví dụ việc gán cùng trạng thái có thể trả timestamp khác nhau. Nếu API cam kết replay **cùng một kết quả** cho khóa cũ, phải quy định cụ thể cách lưu và trả kết quả trước.

Ngoài ra, một thao tác có trạng thái cuối giống nhau có thể vẫn phát hai event, hai email hoặc hai phí xử lý. Khi nói “lặp an toàn”, hãy xác định cả **side effect ngoài cơ sở dữ liệu**.

### 2.3. Các ngữ nghĩa giao hàng và “exactly once”

- **At-most-once:** một yêu cầu không bị thực thi quá một lần nhưng có thể bị mất, không có thử lại; không bảo đảm thành công.
- **At-least-once:** có thể lặp lại để tăng cơ hội hoàn tất; bên xử lý phải sẵn sàng tiếp nhận trùng.
- **Effectively-once:** nhiều lần tiếp nhận có thể xảy ra, nhưng cơ chế idempotency/deduplication khiến *hiệu ứng nghiệp vụ được bảo vệ* như thực thi một lần, trong phạm vi các giả định đã xác định.
- **Exactly-once:** cam kết mạnh, phụ thuộc hệ thống, giao thức, định nghĩa hiệu ứng và miền lỗi; một advice retry đơn thuần **không thể** đảm bảo điều đó với mọi cơ sở dữ liệu và dịch vụ bên ngoài.

Không suy diễn: “gọi lại cùng tham số” ⇒ “chỉ xử lý một lần”; “đã nhận HTTP thành công” ⇒ “không bao giờ có duplicate”; “có unique key” ⇒ “mọi side effect bên ngoài đều đúng một lần”.

## 3. Lỗi nào đáng để thử lại?

Khi một lời gọi thất bại, advice cần **phân loại nguyên nhân** và trạng thái hoàn tất. Một số nhóm:

| Nhóm lỗi | Ví dụ | Quyết định mặc định |
|---|---|---|
| Có khả năng tạm thời | Kết nối bị reset, dịch vụ báo quá tải, tranh chấp khóa có thể thử lại | Có thể retry **nếu** hợp đồng thao tác an toàn và còn ngân sách |
| Có yêu cầu retry sau khoảng cụ thể | Thông báo rate limit cùng thời điểm `Retry-After` hợp lệ | Cân nhắc chờ theo yêu cầu, trong deadline còn lại |
| Vĩnh viễn | Input không hợp lệ, thiếu quyền, trạng thái nghiệp vụ không cho phép | Không retry |
| Lỗi lập trình | `NullPointerException`, lỗi invariant, loại dữ liệu sai | Không retry mặc định; sửa nguyên nhân |
| Không rõ đã commit hay chưa | Timeout sau khi gửi lệnh ghi, mất phản hồi sau commit | **Chỉ retry khi có idempotency protocol** hoặc bước truy vấn trạng thái đáng tin |
| Hủy/gián đoạn | Caller cancel, deadline hết, thread interrupt | Dừng theo hợp đồng, giữ nguyên tín hiệu hủy |
| Thiếu tài nguyên kéo dài | Hệ thống quá tải hàng loạt | Có thể dùng backpressure/circuit-breaker; retry vô hạn làm nặng thêm tình hình |

Không nên bắt mọi `Exception` hoặc `Throwable` và retry vô điều kiện. `Error` nghiêm trọng như lỗi tài nguyên JVM không nên bị che bằng vòng lặp. Tương tự, không nên retry chỉ dựa vào câu chữ của `getMessage()`; dùng loại lỗi, trạng thái đã chuẩn hóa hoặc predicate được kiểm thử.

**Một lỗi timeout có ý nghĩa mơ hồ:** client ngừng chờ không chứng minh server chưa xử lý xong. Đây chính là lý do các lệnh ghi có side effect cần khóa idempotency hoặc cơ chế hỏi lại trạng thái có định danh.

## 4. Bốn giới hạn của chính sách retry

### 4.1. Số lần thử tối đa

`maxAttempts = 3` thường nghĩa là **1 lần ban đầu + tối đa 2 lần gọi lại**, không phải 3 lần retry sau lần đầu. Đặt tên rõ trong cấu hình và test off-by-one. Có thể thêm giới hạn số lần retry theo loại lỗi.

### 4.2. Exponential backoff có giới hạn trên

Lịch chờ đơn giản:

```text
delay(attempt) = min(maxDelay, initialDelay * 2^(attempt - 1))
```

Với `initialDelay = 100 ms` và `maxDelay = 2 s`, những khoảng chờ sau lần thất bại thứ 1, 2, 3 lần lượt là 100 ms, 200 ms, 400 ms (trước khi chạm trần). Triển khai cần chống overflow khi nhân/lũy thừa và chống cấu hình âm.

### 4.3. Jitter để tránh retry đồng loạt

Nếu hàng nghìn client cùng lỗi rồi cùng đợi chính xác 100 ms, chúng có thể đồng loạt gọi lại, tạo **thundering herd**. Jitter phân tán thời điểm thử:

```text
cappedDelay = min(maxDelay, initialDelay * 2^retryIndex)
sleepTime  = random(0, cappedDelay)  // full jitter
```

Một số hệ thống dùng equal jitter hoặc decorrelated jitter. Chọn công thức theo workload và quan sát hiệu quả; không nhầm giá trị ngẫu nhiên với cam kết retry luôn diễn ra sau đúng một khoảng cố định.

### 4.4. Deadline và retry budget

Ngoài số lần, dùng một deadline **end-to-end**. Trước mỗi lần thử và trước khi ngủ, kiểm tra thời gian còn lại. Cần tính cả thời gian của từng attempt, DNS, network, xử lý và backoff. Chỉ kiểm tra deadline trước lần đầu nhưng không hủy request đang chạy có thể khiến tổng thời gian vượt rất xa giới hạn.

Với đồng hồ trong JVM, dùng `System.nanoTime()` cho khoảng thời gian trôi qua (không dùng đồng hồ lịch để đo duration). Một timeout cấu hình trên client I/O hoặc cancellation riêng vẫn cần thiết để attempt đơn lẻ không chặn quá lâu.

**Budget cấp hệ thống:** hạn chế phần trăm truy cập dành cho retry, số retry đồng thời và kích thước queue để tránh khuếch đại một sự cố chung.

## 5. Advice triển khai retry như thế nào?

Một `around advice` lý tưởng bao quanh **đơn vị thực thi có thể lặp lại**, tức có thể gọi lại `proceed()` nếu cơ chế interception cho phép. Mỗi lần gọi lại là một attempt, nhưng lời gọi nghiệp vụ ở ngoài vẫn là **một logical operation**.

```text
Client
   |
   v
[Retry around advice]
   |-- attempt 1 -> proceed() -> temporary failure
   |-- backoff / budget check
   |-- attempt 2 -> proceed() -> success
   v
Result to client
```

Pseudocode độc lập công cụ:

```text
around(call):
    policy = lookupPolicy(call.method)
    verifyOperationIsEligibleForRetry(call, policy)
    deadline = start + policy.totalTimeout

    for attempt = 1 .. maxAttempts:
        ensureNotCancelledOrInterrupted()
        ensureWithinDeadline()
        try:
            return proceed()                    // gọi tác vụ gốc
        catch error:
            if !isRetryable(error) or attempt == maxAttempts:
                throw error
            wait = calculateBackoffWithJitter(attempt)
            if wait cannot fit in remaining deadline:
                throw error or policy timeout
            waitCancellable(wait)

    unreachable
```

Đây là **pseudocode**, không phải Java biên dịch được. Những điểm phải quy định:

- `proceed()` có được gọi lại nhiều lần trong công cụ AOP đang dùng hay không? Một số API/interceptor không cho phép replay lời gọi tùy ý, hoặc chỉ cho phép gọi một lần.
- **Các đối số có thể dùng lại được không?** Một `InputStream` đã đọc, iterator, publisher một lần hoặc request body không còn replay được sẽ làm attempt sau khác attempt đầu.
- **Định danh logic** và idempotency key phải giữ nguyên giữa các attempt; không tạo khóa mới mỗi lần.
- Nếu `proceed()` có thể thay đổi args, cần xác định có tạo bản sao an toàn hay không và không làm mất identity/correlation.
- Việc bắt lỗi để retry không được nuốt exception gốc, biến lỗi nghiệp vụ thành thành công giả hoặc vô tình gọi handler lỗi nhiều lần.
- Không nên sleep trên luồng event loop; lựa chọn scheduler bất đồng bộ với continuation thích hợp.

Với logging/tracing, ghi rõ **logical operation** và **attempt**. Ví dụ một span logic chứa các event `attempt=1,2`; nếu hệ thống dùng span con cho từng attempt, quan hệ giữa chúng cần giữ nhất quán. Không đếm hai attempt thành hai giao dịch người dùng đã hoàn thành.

## 6. Khi nào yêu cầu idempotency key?

Một **idempotency key** là danh tính ổn định của một yêu cầu nghiệp vụ logic, dùng để nhận ra các lần gửi lại. Khóa thường do client tạo trước lần gửi đầu và dùng lại *chính xác* cho những attempt/retry cùng một yêu cầu.

Một hợp đồng chặt phải trả lời:

1. **Phạm vi khóa:** key có duy nhất theo tenant, người dùng, operation, namespace và khoảng thời gian nào? Cùng key của hai tenant khác nhau không được vô tình va nhau.
2. **Ràng buộc payload:** cùng key nhưng tham số khác là một lỗi xung đột, không được trả nhầm kết quả của một yêu cầu khác. Có thể lưu fingerprint/canonical representation an toàn theo schema đã quy định.
3. **Thời gian giữ:** lưu khóa bao lâu? TTL phải phù hợp thời gian retry, delay queue và thời gian xử lý; nếu hết hạn quá sớm, retry muộn có thể gây side effect mới.
4. **Trạng thái:** đang thực hiện, đã thành công, thất bại đã quyết định, hay không rõ commit. Mỗi trạng thái có chính sách replay khác nhau.
5. **Kết quả trả lại:** trả cùng response, redirect đến trạng thái đã có hay trả tín hiệu `IN_PROGRESS`? Điều này thuộc hợp đồng API, không thể suy ra chung.
6. **Tương tranh:** hai request cùng key tới cùng lúc phải được khóa hoặc phân xử **nguyên tử** để không cùng thực hiện hiệu ứng.

Một bảng trạng thái minh họa:

| Trạng thái lưu theo key | Yêu cầu trùng đúng fingerprint | Ý nghĩa |
|---|---|---|
| `ABSENT` | Có thể giành quyền xử lý | Chưa thấy yêu cầu hoặc bản ghi đã hết hạn |
| `IN_PROGRESS` | Trả đang xử lý hoặc chờ có deadline | Tránh hai worker đồng thời tạo side effect |
| `SUCCEEDED` | Replay kết quả/định danh đã lưu | Không gọi lại phần tạo hiệu ứng |
| `FAILED_FINAL` | Replay lỗi cuối theo hợp đồng | Không tái thực thi lỗi vĩnh viễn |
| `UNKNOWN` | Đối soát trước khi quyết định | Chưa biết external side effect đã commit hay chưa |

Nếu lưu fingerprint, cần tránh đưa payload nhạy cảm vào kho key/log. Việc hash có bảo vệ quyền riêng tư hay không phụ thuộc miền dữ liệu và cách sử dụng; chọn thuật toán/phương án lưu phù hợp với loại dữ liệu. Khóa cũng cần giới hạn độ dài, xác thực cú pháp và không đưa dữ liệu bí mật thô vào metric label.

## 7. Lưu idempotency bền vững và xử lý chạy đồng thời

Một bộ nhớ `ConcurrentHashMap` có thể minh họa duplicate suppression trong **một tiến trình đang chạy**, nhưng sẽ mất nội dung khi restart và không phối hợp giữa nhiều instance. Để bảo vệ thao tác có giá trị cao, nơi lưu khóa phải có tính bền vững và ràng buộc nguyên tử phù hợp: unique constraint trong database, record trạng thái transactional, hoặc dịch vụ chuyên trách đáp ứng cùng bảo đảm.

Mô hình thường gặp khi **side effect nằm cùng cơ sở dữ liệu**:

```text
BEGIN TRANSACTION
  INSERT (tenant, operation, idempotency_key, payload_fingerprint, status)
  với UNIQUE(tenant, operation, idempotency_key)

  nếu key đã tồn tại:
      xác minh fingerprint
      lấy trạng thái / kết quả
      không thực thi thêm side effect

  nếu giành được key:
      ghi hiệu ứng nghiệp vụ
      lưu kết quả / trạng thái hoàn tất
COMMIT
```

Đây là kiến trúc khái niệm; transaction và unique constraint cần được thiết kế đúng mức cô lập của DB. Khi lưu key và effect **cùng một transaction**, sự kiện commit của hai thành phần có thể gắn với nhau. Nếu dùng hai kho lưu khác nhau hoặc gọi một hệ thống ngoài transaction, mô hình này không tự tạo nguyên tử xuyên biên.

Để giải quyết hai request cùng key, một mẫu phổ biến là **insert với unique constraint**, rồi xử lý xung đột bằng cách tra trạng thái và kiểm tra fingerprint; hoặc đặt row lock/lease có fencing token nếu công việc kéo dài. Cẩn trọng với trạng thái `IN_PROGRESS` bị bỏ lại sau crash: cần quy trình recovery, không đơn thuần xóa và chạy lại nếu có thể đã commit.

**Không coi cache là nguồn đúng duy nhất** khi sự cố mất cache có thể tạo giao dịch lặp. Nếu yêu cầu kinh doanh chỉ chịu được “best effort”, tài liệu phải nói rõ giới hạn này.

### 7.1. Commit đã xảy ra nhưng phản hồi mất

```text
Caller                   Service                   Database
  | ---- request(key=K) ---> |                         |
  |                          | ---- COMMIT order ----> |
  |          X mất phản hồi  |                         |
  | -- retry (key=K) ------> |                         |
  | <-- replay receipt ----- | (không tạo order lần nữa)|
```

Đây là tình huống điển hình mà retry “thành công” nếu có idempotency nhưng gây duplicate nếu không có. Nếu client tự sinh khóa **mới** mỗi attempt, server không biết hai request tương ứng cùng một logical operation.

### 7.2. Hiệu ứng bên ngoài cơ sở dữ liệu

Một transaction DB không thể rollback email đã gửi hoặc lệnh thanh toán mà nhà cung cấp đã chấp nhận. Cần phối hợp thêm một hoặc nhiều kỹ thuật tùy hệ thống:

- **Idempotency ở đích:** truyền cùng khóa cho dịch vụ bên ngoài nếu dịch vụ đó hỗ trợ và có hợp đồng xác định.
- **Transactional outbox:** ghi bản ghi tác vụ gửi đi cùng transaction nghiệp vụ; dispatcher có thể gửi lại, còn consumer/đích phải chống xử lý trùng.
- **Inbox/deduplication của consumer:** ghi định danh message đã xử lý và effect với ranh giới transaction phù hợp.
- **Đối soát trạng thái:** khi timeout sau khi gửi, tra cứu bằng reference duy nhất trước khi quyết định gửi lại.
- **Compensation:** nếu không thể bảo đảm hiệu ứng một lần, thiết kế nghiệp vụ bù trừ có kiểm soát; bù trừ không đồng nghĩa rollback nguyên tử.

Các kỹ thuật này thường tạo bảo đảm “hiệu ứng một lần” **trong phạm vi cụ thể**, không phải đúng một lần tuyệt đối trên mọi hệ thống.

## 8. Ví dụ Java 17: AOP retry và khóa idempotency

Chương trình sau dùng **JDK dynamic proxy** để tạo around interceptor cho lời gọi thông qua interface. Annotation `@Retryable` cấu hình tổng số attempts; interceptor chỉ retry loại lỗi `TemporaryFailure`. Một `Store` mô phỏng ghi kết quả với idempotency key, cố tình ném lỗi **sau khi đã ghi** ở lần gọi đầu để mô phỏng phản hồi bị mất.

Để output dễ kiểm chứng, `Sleeper` được tiêm vào và trong `main` chỉ **in thời gian backoff**, không ngủ thật. Đó là lựa chọn của demo, không phải cách quản lý thời gian khi vận hành production.

Lưu toàn bộ chương trình trong **RetryAspectDemo.java**:

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryAspectDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @interface Retryable {
        int maxAttempts() default 3;
    }

    static final class TemporaryFailure extends RuntimeException {
        TemporaryFailure(String message) { super(message); }
    }

    @FunctionalInterface
    interface Sleeper {
        void pause(long millis) throws InterruptedException;
    }

    interface Command {
        @Retryable(maxAttempts = 3)
        String execute(String idempotencyKey);
    }

    static final class Store implements Command {
        final ConcurrentHashMap<String, String> receipts = new ConcurrentHashMap<>();
        final AtomicInteger invocations = new AtomicInteger();
        final AtomicInteger effects = new AtomicInteger();

        public String execute(String idempotencyKey) {
            int number = invocations.incrementAndGet();
            String receipt = receipts.computeIfAbsent(idempotencyKey,
                    key -> "receipt-" + effects.incrementAndGet());
            if (number == 1) {
                throw new TemporaryFailure("acknowledgment lost");
            }
            return receipt;
        }
    }

    static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static <T> T withRetry(Class<T> api, T target, Sleeper sleeper) {
        return api.cast(Proxy.newProxyInstance(
                api.getClassLoader(), new Class<?>[] { api },
                (proxy, method, args) -> {
                    Retryable policy = method.getAnnotation(Retryable.class);
                    if (policy == null) return invoke(method, target, args);
                    int maxAttempts = policy.maxAttempts();
                    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts");
                    for (int attempt = 1; ; attempt++) {
                        try {
                            Object result = invoke(method, target, args);
                            System.out.println("attempt=" + attempt + " success");
                            return result;
                        } catch (TemporaryFailure ex) {
                            System.out.println("attempt=" + attempt + " transient");
                            if (attempt >= maxAttempts) throw ex;
                            long delay = Math.min(100L, 10L << Math.min(attempt - 1, 6));
                            sleeper.pause(delay);
                        }
                    }
                }));
    }

    public static void main(String[] args) {
        Store store = new Store();
        Command command = withRetry(Command.class, store,
                millis -> System.out.println("backoff_ms=" + millis));
        System.out.println("result=" + command.execute("request-42"));
        System.out.println("invocations=" + store.invocations.get());
        System.out.println("side_effects=" + store.effects.get());
        System.out.println("same_key=" + command.execute("request-42"));
        System.out.println("side_effects_after_same_key=" + store.effects.get());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 RetryAspectDemo.java
java RetryAspectDemo
```

Kết quả chính xác:

```text
attempt=1 transient
backoff_ms=10
attempt=2 success
result=receipt-1
invocations=2
side_effects=1
attempt=1 success
same_key=receipt-1
side_effects_after_same_key=1
```

Giải thích theo thứ tự:

1. `execute("request-42")` lần đầu dùng `computeIfAbsent` để ghi `receipt-1` và tăng số hiệu ứng lên 1, sau đó cố tình ném `TemporaryFailure`.
2. Advice bắt được lỗi thuộc loại cho phép retry; vì còn lượt, nó tính backoff 10 ms và gọi lại cùng `args`, trong đó khóa vẫn là `request-42`.
3. Lần thứ hai trả lại `receipt-1` đã tồn tại. Tổng lượt gọi là 2 nhưng số lần tạo hiệu ứng chỉ là 1.
4. Khi client gọi lại cùng khóa lần nữa, interceptor bắt đầu một logical invocation mới và có một attempt thành công; bộ lưu giữ khóa vẫn trả receipt cũ. Số hiệu ứng không tăng.

**Giới hạn bắt buộc của ví dụ:** bộ `ConcurrentHashMap` chỉ ở trong một JVM, không bền vững, chưa kiểm tra fingerprint khi cùng key nhưng khác payload, chưa xét trạng thái đang xử lý kéo dài, crash hoặc giao dịch ngoài. `computeIfAbsent` minh họa đồng bộ hóa việc tạo value trong map cho cùng key ở tình huống thành công, **không** thay thế transaction với database và dịch vụ ngoài. `TemporaryFailure` được tạo có chủ ý; trong thực tế một số timeout không có dấu hiệu chắc chắn về việc commit. Việc tiêm `Sleeper` fake có nghĩa demo kiểm tra luồng retry và phép tính delay, không kiểm tra đồng hồ thực hay hủy task.

Ngoài ra, handler minh họa chỉ áp dụng cho method annotation đặt trên interface `Command`; proxy không tự intercept lời gọi nội bộ bên trong đối tượng `Store`. `Method.invoke` có thể gói ngoại lệ nghiệp vụ bằng `InvocationTargetException` và hàm `invoke` đã tháo lớp này để policy kiểm tra đúng loại lỗi gốc.

## 9. Thứ tự advice và phạm vi giao dịch

Khi kết hợp retry với authorization, transaction, timeout, circuit breaker, tracing và audit, **thứ tự bọc** quyết định điều gì thực sự được chạy lại.

Hai cách đặt retry với transaction:

```text
A. Retry ngoài transaction (thường phù hợp nếu cần transaction mới mỗi attempt)
   Retry
      -> Begin Transaction
          -> Business Operation
      -> Commit/Rollback
   (khi lỗi được phép: attempt kế tiếp bắt đầu transaction mới)

B. Transaction ngoài retry
   Begin Transaction
      -> Retry
          -> Business Operation (nhiều lần trong cùng transaction)
   Commit/Rollback
```

Ở phương án B, một lỗi có thể khiến transaction hiện tại bị đánh dấu rollback-only hoặc mất khả năng tiếp tục; retry ngay trong cùng transaction có thể vô nghĩa hoặc nguy hiểm. Trong phương án A, cần chắc rằng sau rollback mọi resource/connection/lock của attempt cũ đã được giải phóng và thao tác thực sự có thể chạy lại. **Không có thứ tự nào đúng cho mọi môi trường**: hợp đồng transaction, loại lỗi và khả năng quan sát commit quyết định.

Các khía cạnh phối hợp khác:

- **Authorization:** mỗi attempt cần tuân thủ ngữ cảnh quyền hợp lệ; không cho retry để né kiểm tra quyền. Nếu auth context hết hạn, chính sách làm mới hoặc dừng phải rõ.
- **Rate limit:** quyết định giới hạn theo logical request hay từng attempt; mỗi attempt vẫn tiêu thụ tài nguyên thật.
- **Circuit breaker:** có thể đặt ngoài để bảo vệ logical call hoặc trong để chặn từng attempt, tùy mục đích đo/cắt. Tránh circuit bị đánh giá sai vì đếm mọi lần lặp thành request độc lập.
- **Timeout:** tổng deadline nằm ngoài retry; timeout từng attempt nằm phía trong. Nếu chỉ có timeout mỗi attempt, thời gian end-to-end có thể quá lớn.
- **Tracing/logging:** giữ một correlation/idempotency key cố định, thêm attempt index; không ghi secret của request như idempotency key vào nhãn metric có cardinality cao.
- **Audit:** “yêu cầu đã được tiếp nhận” khác “attempt đã thử” và “hiệu ứng đã commit”. Cần phân biệt để không nhân đôi audit nghiệp vụ.

Tránh hiện tượng **retry amplification**: tầng HTTP client retry 3 lần, tầng domain retry 3 lần và message worker retry 3 lần có thể dẫn tới tới 27 nỗ lực phía dưới cho một yêu cầu ban đầu. Cần thiết kế quyền sở hữu retry theo tầng, hoặc truyền deadline và retry budget dùng chung.

## 10. Bất đồng bộ, hủy và scheduling

Một around advice đồng bộ có thể nhận về `CompletableFuture<T>` **ngay lập tức** trước khi công việc thật hoàn tất. Nếu nó chỉ catch ngoại lệ từ `proceed()` thì sẽ không bắt được một future hoàn thành exceptionally sau đó.

Cần phân biệt:

1. **Retry quá trình tạo future:** `proceed()` ném lỗi ngay trước khi trả handle; có thể áp dụng vòng lặp đồng bộ theo chính sách.
2. **Retry công việc trong future:** future hoàn tất với lỗi; muốn retry phải có **factory** tạo attempt mới hoặc khả năng gọi lại thao tác theo đúng hợp đồng, gắn callback xử lý lỗi và lập lịch lần sau.
3. **Retry của publisher/stream lười:** công việc có thể bắt đầu lúc subscribe, retry có thể là resubscribe; stream có side effect hoặc đã phát một phần dữ liệu nên không đương nhiên lặp an toàn.
4. **Retry message handler:** phải xét cơ chế acknowledge/nack, visibility timeout, redelivery và idempotency ở consumer.

Trong thực thi bất đồng bộ, không dùng `Thread.sleep` trên event loop. Dùng scheduler với timer không chặn, kiểm soát deadline và hủy. Nếu caller cancel, cần quyết định có cancel attempt đang chạy không, có ngăn task đã lên lịch kế tiếp không, và side effect đã commit được đối soát thế nào.

**Tác vụ bị hủy không có nghĩa side effect đã rollback.** Cancellation thường chỉ là yêu cầu hủy; một lời gọi network hoặc bước commit có thể đang tiếp diễn. Vẫn cần idempotency/reconciliation khi kết quả không rõ.

Khi truyền trace/security context qua scheduler hoặc executor, phải chụp đúng ngữ cảnh cho từng attempt và khôi phục tại luồng chạy. Chuyển sang luồng khác không tự sao chép mọi `ThreadLocal`. Không giữ context của request cũ trong pool sau khi attempt kết thúc.

## 11. Tiêu chuẩn retry trước lời gọi có side effect

Để đánh giá một join point có thể gắn retry hay không, hãy lần lượt trả lời:

**Một: đây là loại thao tác gì?** Tính toán thuần, đọc, cập nhật cùng giá trị, tăng số lượng, tạo tài nguyên, gửi thông báo hay kết hợp nhiều bước? Có file/stream/iterator tiêu thụ một lần hay input không replay được không?

**Hai: khi lỗi, có thể đã xảy ra hiệu ứng chưa?** Phân biệt thất bại trước khi gửi request, thất bại có phản hồi rõ ràng chưa xử lý, thất bại sau commit, và timeout không rõ. Chỉ thông tin phía caller không đủ thì yêu cầu khóa idempotency hoặc đối soát.

**Ba: ai tạo và giữ khóa?** Khóa phải tồn tại xuyên attempt, retry qua network/queue và process restart khi hợp đồng đòi hỏi; không sinh key mới trong retry advice. Nếu key có nguồn từ dữ liệu người dùng, kiểm tra namespace/ownership để tránh va chạm giữa các principal.

**Bốn: bên nhận có deduplicate thực sự không?** Unique constraint, bản ghi kết quả, kiểm tra fingerprint và xử lý đồng thời phải có hành vi rõ. Nếu đích không hỗ trợ, retry write không được tự mặc định an toàn.

**Năm: ngân sách và cách dừng?** Số attempt, deadline, điều kiện lỗi, jitter, quota và cancellation được test cụ thể. Không “cứ thử đến khi thành công”.

**Sáu: trạng thái kết thúc nào được báo?** Phải phân biệt success, failed-final, cancelled và outcome-unknown. Không nên phát “thành công” chỉ vì một advice đã gửi lại request.

## 12. Các lỗi thiết kế phổ biến

| Dấu hiệu | Nguyên nhân sâu | Cách xử lý |
|---|---|---|
| Đơn hàng bị tạo đôi | Retry sau timeout với key khác hoặc không có key | Khóa logic ổn định, dedup bền vững ở nơi tạo effect |
| Trừ tiền hai lần | Thao tác tăng/chuyển tiền không idempotent | Idempotency contract phía dịch vụ thanh toán, tra cứu trạng thái |
| Cùng key nhưng nội dung khác nhận kết quả cũ | Không lưu/kiểm tra fingerprint | Trả lỗi xung đột, bắt buộc cùng request logic |
| Hai worker cùng xử lý một key | Chỉ check-then-insert thiếu tính nguyên tử | Unique constraint/locking và state transition có kiểm soát |
| Retry vô hạn và tạo tải đột biến | Không giới hạn attempt, deadline và backoff | Budget + capped backoff + jitter + breaker/rate limit |
| Không retry dù future lỗi | Advice chỉ catch lỗi khi tạo handle | Quan sát completion và tạo attempt mới theo hợp đồng |
| Retry một giao dịch đã hỏng | Transaction bao ngoài mọi attempt | Cân nhắc transaction riêng từng attempt |
| Retry che lỗi nghiệp vụ | Catch-all và phân loại sai | Chỉ retry whitelist transient, propagate nguyên nhân cuối |
| Chặn thread event loop | Backoff bằng sleep đồng bộ | Scheduler bất đồng bộ/cancellable |
| Mỗi lần thử tạo một trace logic riêng | Interceptor không giữ correlation | Giữ logical operation ID, thêm attempt metadata |
| Lỗi không thể tái hiện | Random jitter không kiểm soát trong test | Tiêm clock/sleeper/random, dùng lịch giả xác định |
| Đã retry nhưng không thể replay input | Stream/file/iterator dùng một lần | Tạo nguồn có thể phát lại hoặc dừng retry |

## 13. Kiểm thử trước khi đưa vào hệ thống

Các test quan trọng cần mô phỏng **ranh giới thất bại thực tế**:

1. **Transient trước side effect:** lần 1 thất bại, lần 2 thành công; assert số attempts, delay và kết quả.
2. **Lỗi vĩnh viễn:** dữ liệu không hợp lệ/không có quyền; assert chỉ một attempt, bảo toàn exception.
3. **Hết maxAttempts:** mọi lần đều lỗi được phép; assert dừng đúng mốc và trả lỗi cuối theo hợp đồng.
4. **Timeout sau commit:** ghi hiệu ứng thành công rồi mất response; gửi lại **cùng key**; assert không tăng số hiệu ứng.
5. **Cùng key nhưng payload khác:** phải trả lỗi xung đột, không tái sử dụng kết quả sai.
6. **Cạnh tranh:** hai hoặc nhiều worker gửi cùng key đồng thời; assert chỉ một worker thắng quyền tạo effect.
7. **Crash/restart:** mất process sau commit nhưng trước trả response; storage bền vững vẫn replay/đối soát được.
8. **TTL và retry trễ:** xác minh chính sách hết hạn và cửa sổ bảo vệ; không để việc dọn khóa âm thầm tạo duplicate trong khoảng cần bảo đảm.
9. **Tổng deadline:** attempt lâu + backoff không vượt budget được hứa ở lớp caller; timeout từng attempt có hiệu lực.
10. **Cancel/interrupt:** không phát thêm attempt sau hủy; thread interrupt không bị nuốt.
11. **Nested retries:** mô phỏng nhiều tầng, assert tải tối đa không bùng nổ.
12. **Async future:** exception xảy ra *sau* khi trả handle; wrapper thật sự quan sát completion, không báo thành công sớm.
13. **Nhiều side effect:** DB, outbox, message và API ngoài đều có thử nghiệm duplicate phù hợp.
14. **Metrics và audit:** thử lại tạo metric attempt, nhưng số logical operation hoặc business effect không bị đếm sai.

Đối với Java, dùng clock/scheduler/sleeper giả để test điều kiện dừng mà không cần chờ thực tế. Với concurrency test, dùng `CountDownLatch` hoặc barrier để tạo cuộc đua ổn định thay vì trông chờ `Thread.sleep`. Phân biệt test chứng minh đúng trong một JVM với test hệ thống nhiều instance, crash recovery hoặc network uncertainty.

## 14. Danh sách quyết định cho một retry advice

- [ ] Xác định rõ tập pointcut và kiểu phương thức mà advice thực sự intercept.
- [ ] Chỉ bật retry cho phương thức có hợp đồng replay rõ, hoặc có idempotency/đối soát đáng tin.
- [ ] Khóa idempotency được giữ nguyên giữa mọi attempt của cùng logical operation.
- [ ] Phân loại transient/permanent/unknown outcome bằng tiêu chí rõ và có test.
- [ ] `maxAttempts` (gồm lần đầu), delay, jitter, tổng deadline, timeout mỗi attempt và cancellation có giới hạn.
- [ ] Không retry tự động stream một lần, non-repeatable input hoặc lỗi nghiệp vụ vĩnh viễn.
- [ ] Nơi dedup kiểm tra fingerprint và xử lý hai request cùng key nguyên tử.
- [ ] Idempotency record và hiệu ứng nghiệp vụ ở ranh giới transaction phù hợp; hiệu ứng ngoài phải có cơ chế riêng.
- [ ] Thứ tự retry/transaction/timeout/tracing được kiểm thử theo từng attempt.
- [ ] Future, publisher và message handler dùng đúng tín hiệu hoàn tất và mô hình scheduling.
- [ ] Không biến log retry thành log chứa token, dữ liệu riêng tư hoặc metric có cardinality vô hạn.
- [ ] Có test kết quả **không rõ sau commit**, crash, concurrency và hủy.

## 15. Ghi nhớ

**Retry là chính sách lặp lại việc thực thi; idempotency là hợp đồng bảo vệ hiệu ứng của việc lặp đó.** Một AOP advice có thể tổ chức attempts, xử lý transient error, backoff, jitter và deadline rất tốt, nhưng không thể tự làm mọi thao tác ghi trở nên an toàn. Khi thao tác có side effect, tính đúng đắn nằm ở **khóa yêu cầu logic ổn định, kiểm soát trùng nguyên tử, ranh giới transaction, cơ chế đối soát và hợp đồng phía hệ thống đích**. Thiết kế hai phần này cùng nhau trước khi bật retry rộng rãi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-004">Bộ nhớ đệm và điều kiện lời gọi có thể bỏ qua thực thi gốc</a>

<details>
<summary>Xem chi tiết</summary>

# Bộ nhớ đệm và điều kiện lời gọi có thể bỏ qua thực thi gốc trong AOP

## 1. Câu hỏi trọng tâm

Một aspect trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP) thường được nghĩ đến như mã chạy trước hoặc sau phương thức: ghi log, đo thời gian, kiểm tra quyền. Tuy nhiên, **around advice** có một năng lực quan trọng hơn: quyết định **có tiếp tục lời gọi đến phương thức đích hay không**. Với bộ nhớ đệm (*cache*), nếu đã có kết quả phù hợp và còn hiệu lực, advice có thể trả về ngay **mà không thực thi phương thức gốc**.

Ví dụ, lời gọi `catalog.findLabel("A")` tốn thời gian đọc nguồn dữ liệu. Một advice có thể lưu kết quả của lần gọi đầu tiên. Ở lần gọi tiếp theo, nếu mọi yếu tố xác định kết quả không đổi và entry còn hợp lệ, advice trả giá trị đã lưu. Đây là *short-circuit* — rẽ nhánh kết thúc lời gọi sớm.

~~~text
Caller
  |
  v
Around advice
  |
  +--> Kiểm tra điều kiện bắt buộc mỗi lời gọi
  |
  +--> Được phép dùng cache?
  |       |
  |       +-- Không: BYPASS -> proceed() -> trả kết quả
  |
  +--> Tạo khóa, đọc entry
          |
          +-- HIT hợp lệ -> trả cached result
          |                  (không gọi target)
          |
          +-- MISS/stale -> proceed() -> target chạy
                                      |
                                      +--> nếu result được phép lưu: put
                                      +--> trả result
~~~

Mấu chốt không phải chỉ là “đã có dữ liệu trong map” mà là **có thể bỏ qua lời gọi gốc mà vẫn giữ đúng hợp đồng nghiệp vụ**. Caching sai có thể làm bỏ qua kiểm tra quyền, làm mất side effect bắt buộc, trả dữ liệu cũ hoặc sai tenant, nhân bản lỗi bất đồng bộ và che khuất thứ tự các aspect.

## 2. AOP can thiệp vào lời gọi ở mức nào?

Các khái niệm liên quan:

- **Join point:** điểm có thể được quan sát hoặc can thiệp, chẳng hạn một method call/execution theo khả năng công cụ.
- **Pointcut:** quy tắc chọn tập join point.
- **Advice:** hành vi chạy tại join point được chọn.
- **Around advice:** bao việc gọi đích, có thể quyết định gọi `proceed()`, trả giá trị thay thế hoặc ném ngoại lệ.
- **Proceed:** thao tác tiếp tục qua các advice tiếp theo hoặc target, tùy mô hình và API của nền tảng.
- **Aspect ordering:** thứ tự lồng nhau của nhiều advice, ảnh hưởng nhánh được thực thi khi cache hit.

Một *before advice* chạy trước target nhưng thông thường không tự thay thế return value. Một *after returning advice* nhận kết quả sau khi target đã chạy: nó có thể lưu kết quả vào cache nhưng không tiết kiệm được lần thực thi đó. **Around advice** hoặc một interceptor có quyền điều khiển invocation là chỗ phù hợp để **không gọi** target trên cache hit.

Pseudocode minh họa:

~~~text
around(invocation):
    if not eligible(invocation):
        return proceed()

    key = buildKey(invocation)
    entry = cache.find(key)

    if entry exists and entry is valid:
        return entry.value        # KHÔNG gọi proceed

    result = proceed()            # Target thực thi trên cache miss
    if mayStore(invocation, result):
        cache.put(key, result)
    return result
~~~

Đây là mã giả, không phải API Java có sẵn. Chính sách thực tế cần xử lý exception, null, trạng thái stale, quyền, đồng thời, timeout và kiểu return.

Một short-circuit còn có thể **bỏ qua những advice nằm phía trong caching advice**, vì nhánh đó không gọi `proceed()`. Do đó thứ tự cache với kiểm tra quyền, validation, transaction, audit, tracing và retry quyết định tính đúng đắn.

## 3. Phân biệt *hit*, *miss* và *bypass*

| Nhánh | Nguyên nhân | Target được gọi? | Có thể lưu kết quả mới? |
| --- | --- | --- | --- |
| **Hit** | Có entry hợp lệ đúng ngữ cảnh và policy | **Không** | Thường không; có thể cập nhật access metadata nếu policy cho phép |
| **Miss** | Lời gọi đủ điều kiện dùng cache nhưng không có entry dùng được | **Có** | Có nếu kết quả đạt điều kiện ghi |
| **Stale** | Entry tồn tại nhưng hết hạn hoặc sai version/quyền | **Có** hoặc chính sách refresh riêng | Có thể thay entry hợp lệ |
| **Bypass** | Policy quy định lời gọi không được dùng cache | **Có** | Tùy chính sách; thường không ghi |
| **Rejected** | Input không hợp lệ, thiếu quyền hoặc policy cấm lời gọi | Không nhất thiết | Không; có thể ném lỗi trước target |

**Miss khác bypass.** Với miss, hệ thống đã thử dùng cache nhưng không có dữ liệu. Với bypass, hệ thống chủ ý tránh đọc cache, chẳng hạn yêu cầu dữ liệu realtime hoặc lời gọi không thuộc diện tái sử dụng kết quả. Bypass vẫn chạy phương thức gốc, trong khi hit hợp lệ mới là nhánh tiêu biểu được phép bỏ qua thực thi gốc.

**Rejected không phải một loại cache miss.** Nếu caller không được quyền truy cập, không nên trả giá trị cached hay tự động chuyển sang target khi policy buộc phải chặn.

Một cache hit đúng phải thỏa ít nhất:

~~~text
selectedJoinPoint
AND mandatoryChecksPassed
AND requestEligibleForCache
AND stableCacheKeyAvailable
AND entryFound
AND entryNotExpired
AND versionMatches
AND contextAndPermissionsMatch
AND returnContractCompatible
~~~

Không phải mọi hệ thống cần lưu tất cả metadata trên entry, nhưng hệ thống phải có cách bảo đảm những điều kiện ảnh hưởng correctness.

## 4. Phương thức nào thích hợp để cache?

Lý tưởng là một phép tính có đầu ra xác định bởi input và một snapshot/version dữ liệu rõ ràng, không có side effect phải xảy ra trên mỗi lượt gọi. Ví dụ:

- Tra cứu tên sản phẩm dựa trên mã và phiên bản catalog.
- Tính một kết quả số học đắt đỏ nhưng xác định.
- Đọc dữ liệu tham chiếu với TTL/invalidation phù hợp.
- Chuyển đổi định dạng hoặc phân tích cấu trúc dữ liệu bất biến.
- Tạo view theo tham số + locale + quyền đã xác định.

Phương thức **không nên bị short-circuit bằng read cache thông thường** nếu việc chạy nó chính là tác động nghiệp vụ:

- `chargeCard(request)` thực hiện thanh toán.
- `reserveInventory(sku, amount)` trừ tồn kho.
- `sendEmail(message)` gửi thông báo.
- `incrementCounter()` tạo số thứ tự hoặc tăng thống kê.
- `recordAccess()` ghi audit bắt buộc ở mỗi lần gọi.
- `refreshLease()` gia hạn trạng thái.
- `consumeOneTimeToken()` tiêu thụ token.

Một giá trị return có thể giống nhau ở hai lượt gọi nhưng **hợp đồng quan sát được khác nhau** vì side effect. Bỏ qua target khi hit sẽ làm hành vi sai. Với lệnh ghi cần chống gọi trùng, hãy dùng hợp đồng **idempotency key + trạng thái xử lý bền vững**, không tự xem nó giống cache kết quả đọc.

### Tính thời điểm và dữ liệu biến thiên

Các phương thức đọc thời gian hiện tại, số ngẫu nhiên, trạng thái mạng, tỷ giá trực tiếp hoặc dữ liệu nguồn thường xuyên đổi có thể vẫn cache được nếu hợp đồng cho phép *staleness* cụ thể, nhưng phải đưa tham số quyết định output vào key hoặc có version/TTL rõ ràng. Nếu hệ thống hứa dữ liệu mạnh về nhất quán, một TTL tùy ý có thể không đáp ứng hợp đồng đó.

### Kiểm tra bắt buộc ở mọi lời gọi

Nếu kiểm tra quyền chỉ nằm trong method đích, cache hit bỏ qua target cũng bỏ qua quyền. Cần đặt kiểm tra quyền ở một ranh giới luôn thực thi, hoặc thiết kế policy đảm bảo hit không thể lộ dữ liệu trái quyền. Thêm tenant/user ID vào key có thể cần thiết nhưng **không tự thay thế việc kiểm tra quyền còn hiệu lực**. Tương tự với quota, input validation và audit bắt buộc.

## 5. Ví dụ Java 17: dynamic proxy thực sự bỏ qua target

Chương trình sau dùng `java.lang.reflect.Proxy` để mô phỏng around advice ở các lời gọi đi qua interface. Một annotation tự định nghĩa đánh dấu method được phép cache. Ba nhánh được thể hiện rõ: hit bỏ qua target, miss chạy target rồi lưu, bypass luôn chạy target.

**Lưu ý:** ví dụ đơn luồng cố ý dùng `HashMap` và bộ đếm thường để dễ kiểm tra output. Nó không cung cấp TTL, persistence, thread safety hoặc distributed caching. Kết quả nghiệp vụ không phụ thuộc bộ đếm: số lần tính chỉ được in qua dòng `[target]` để chứng minh có hay không thực thi. Điều này giúp cache hit giữ đúng hợp đồng kết quả cho cùng `code` trong ví dụ.

Lưu tệp **`CachingAdviceDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class CachingAdviceDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface CacheableResult {}

    interface CatalogService {
        @CacheableResult
        String lookup(String code);
    }

    static final class CatalogServiceImpl implements CatalogService {
        private int calls;

        @Override
        public String lookup(String code) {
            calls++;
            System.out.println("[target] lookup " + code + ", call=" + calls);
            return "VALUE(" + code + ")";
        }

        int executions() {
            return calls;
        }
    }

    static Object proceed(
            Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    static CatalogService withCachingAdvice(CatalogService target) {
        Map<String, Object> cache = new HashMap<>();

        return (CatalogService) Proxy.newProxyInstance(
                CatalogService.class.getClassLoader(),
                new Class<?>[] {CatalogService.class},
                (proxy, method, args) -> {
                    if (!method.isAnnotationPresent(CacheableResult.class)) {
                        return proceed(target, method, args);
                    }

                    String code = (String) args[0];
                    if (code == null || code.isBlank()) {
                        throw new IllegalArgumentException(
                                "code must not be blank");
                    }

                    if (code.startsWith("LIVE:")) {
                        System.out.println("[cache] bypass " + code);
                        return proceed(target, method, args);
                    }

                    String key = method.getName() + "|" + code;
                    if (cache.containsKey(key)) {
                        System.out.println("[cache] hit " + code);
                        return cache.get(key);
                    }

                    System.out.println("[cache] miss " + code);
                    Object result = proceed(target, method, args);
                    cache.put(key, result);
                    return result;
                });
    }

    public static void main(String[] args) {
        CatalogServiceImpl target = new CatalogServiceImpl();
        CatalogService service = withCachingAdvice(target);

        System.out.println("1=" + service.lookup("A"));
        System.out.println("2=" + service.lookup("A"));
        System.out.println("3=" + service.lookup("LIVE:X"));
        System.out.println("4=" + service.lookup("LIVE:X"));
        System.out.println("5=" + service.lookup("B"));
        System.out.println("[stats] target calls=" + target.executions());
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac CachingAdviceDemo.java
java CachingAdviceDemo
~~~

Đầu ra xác định:

~~~text
[cache] miss A
[target] lookup A, call=1
1=VALUE(A)
[cache] hit A
2=VALUE(A)
[cache] bypass LIVE:X
[target] lookup LIVE:X, call=2
3=VALUE(LIVE:X)
[cache] bypass LIVE:X
[target] lookup LIVE:X, call=3
4=VALUE(LIVE:X)
[cache] miss B
[target] lookup B, call=4
5=VALUE(B)
[stats] target calls=4
~~~

**Giải thích từng nhánh:**

1. `withCachingAdvice` trả proxy triển khai `CatalogService`. Chỉ lời gọi đi qua `service` mới được handler quan sát.
2. Handler nhận metadata `@CacheableResult` trên method interface. Annotation này chỉ là dấu hiệu cho handler; **tự annotation không thể làm cache tự chạy**.
3. Lần gọi `lookup("A")` thứ nhất: key chưa có, advice gọi `proceed`, target tính `VALUE(A)` rồi được lưu.
4. Lần thứ hai `lookup("A")`: cache hit, handler return `VALUE(A)`. Target không chạy, nên không xuất hiện dòng `[target]` mới và bộ đếm không tăng.
5. Hai lần `lookup("LIVE:X")` đi vào nhánh bypass: handler **không tra cache**, luôn gọi target, vì vậy bộ đếm tăng lên 2 và 3.
6. `lookup("B")` là miss khác: target chạy lần thứ tư. Năm lần gọi proxy nhưng **chỉ bốn lần chạy target** — chứng cứ trực tiếp của short-circuit.
7. `proceed` unwrap `InvocationTargetException` để truyền đúng lỗi gốc từ target. `cache.put` chỉ được gọi sau khi target return bình thường; lỗi ném trực tiếp không biến thành success entry.
8. Nhánh kiểm tra input nằm **trước cache lookup**, do đó một lời gọi input rỗng không được trả cached result để lẩn tránh validation.

Giới hạn: key đơn giản chỉ gồm tên method và `code` vì demo có một method cache. Với overload hoặc nhiều interface, cần định danh đầy đủ method/signature. Không dùng `HashMap` chia sẻ giữa các luồng ở production; thêm versioning, TTL, authorization context, capacity bound và invalidation theo yêu cầu.

## 6. Cache key là điều kiện để hit thật sự hợp lệ

Một **hit** chỉ hợp lệ nếu lời gọi hiện tại được phép dùng cùng kết quả với lời gọi trước: cùng thao tác, tham số, ngữ cảnh bảo mật/tenant, locale, version hoặc yếu tố khác có khả năng làm đổi output. Key của demo là `method.getName() + "|" + code` **chỉ dành cho một method và một context cố định**; nó không phù hợp cho hệ thống nhiều tenant, overload hay kết quả phụ thuộc quyền.

Ví dụ `quote(tenant, sku, currency, priceVersion)` cần khóa dạng tuple bất biến, có namespace và các thành phần liên quan. Nếu hai tenant có giá khác nhau nhưng dùng chung khóa `sku`, đó là **lỗi tính đúng và có thể rò dữ liệu**. Phân quyền vẫn phải được kiểm tra tại ranh giới thích hợp kể cả khi có cache; TTL/invalidation cần bảo đảm freshness.

Không chuẩn hóa input bằng `trim`/`lowercase` nếu domain phân biệt các giá trị đó. Không đưa token hoặc dữ liệu nhạy cảm nguyên văn vào cache key/log. Với mảng và object mutable, phải chọn biểu diễn canonical ổn định, không dựa vào identity hoặc hash thay đổi theo thời gian.

Phần thiết kế key theo ngữ cảnh có nhiều chiều, versioning, canonicalization và kiểm thử phân vùng được phân tích chuyên sâu ở bài riêng; ở đây key đủ chính xác là **tiền điều kiện** để advice được phép short-circuit.

## 7. Điều kiện đọc và điều kiện ghi không giống nhau

**Read condition** thường được đánh giá *trước* `proceed` để quyết định có dùng cache không. Ví dụ chỉ đọc cache với request chế độ standard, không đọc khi user yêu cầu realtime.

**Write condition** được đánh giá *sau* khi đã có kết quả thực để quyết định có tạo entry không. Ví dụ chỉ cache một kết quả thành công có kích thước phù hợp và dữ liệu đủ ổn định.

~~~text
if invalidRequest:
    rejectAccordingToContract

if mustBypassCache:
    return proceed()

key = buildKey()
entry = readValidEntry(key)

if entry found:
    return entry.value

result = proceed()

if mayCacheResult(result):
    store(key, result, ttl)

return result
~~~

Điều kiện “cho đọc cache” có thể dựa trên input và ngữ cảnh. Điều kiện “cho ghi” có thể cần nhìn cả output, loại lỗi, kích thước, chính sách riêng tư, version nguồn và outcome của giao dịch liên quan.

Một số kết quả cần chính sách riêng:

- **`null`:** cache có thể cần sentinel riêng, vì “không có entry” và “entry có payload null” khác nhau. `ConcurrentHashMap` không cho null value.
- **Not found:** *negative caching* có ích với truy vấn lặp, nhưng cần TTL/invalidation khi dữ liệu mới xuất hiện.
- **Error:** mặc định không xem exception là success entry. Một policy có thể cache một số lỗi xác định, nhưng lỗi mạng/timeout tạm thời thường không nên bị giữ lâu.
- **Mutable value:** cần immutable snapshot hoặc defensive copy nếu caller có thể sửa dữ liệu đã cache.
- **Return object identity:** cache cùng object có thể phá hợp đồng nếu API hứa object mới ở mỗi lần gọi.

Đặt write condition sai có thể khiến cả một failure tạm thời được lưu và trả lại như kết quả hợp lệ trong nhiều giờ.

## 8. TTL, invalidation và giữ dữ liệu mới

Một entry có thể đã đúng tại thời điểm tạo nhưng sai khi nguồn dữ liệu thay đổi. Chính sách lifecycle thường gồm:

- **TTL:** hết hạn sau một khoảng tính từ lúc ghi/refresh.
- **Time-to-idle:** hết hạn khi không ai truy cập trong thời gian quy định.
- **Explicit invalidation:** xóa hoặc thay entry khi có sự kiện cập nhật nguồn.
- **Versioned key:** chuyển sang key version mới khi dữ liệu/logic thay đổi.
- **Refresh-ahead:** tải lại dữ liệu trước khi hết hạn.
- **Stale-while-revalidate:** cố ý cho phép đọc stale trong một khoảng nhỏ và refresh nền, nếu API chấp nhận.

TTL dài tăng khả năng hit nhưng có thể tăng stale latency; TTL ngắn tăng loader traffic. Nếu hợp đồng đòi nhất quán mạnh, một TTL tùy ý không đủ để thay cơ chế version/invalidation phù hợp.

Một race quan trọng:

~~~text
A: bắt đầu load dữ liệu phiên bản V1
B: cập nhật nguồn sang V2 rồi invalidate cache
A: hoàn tất việc đọc V1 và ghi lại V1 vào cache
C: cache hit nhưng nhận V1 đã lỗi thời
~~~

Cần xem xét version check, generation token hoặc conditional write để ngăn loader cũ tái điền entry sau invalidation. “Đã gọi invalidate” không tự bảo đảm mọi request sau đó đều đọc dữ liệu mới trong hệ thống đồng thời.

## 9. Đồng thời: cache stampede và single-flight

Nếu nhiều caller đồng thời gặp miss trên cùng key, mỗi caller có thể gọi target:

~~~text
Caller A --- miss ---> expensive target
Caller B --- miss ---> expensive target
Caller C --- miss ---> expensive target
~~~

Hiện tượng này gọi là *cache stampede*. Các hướng xử lý tùy hạ tầng:

| Cách | Cơ chế | Điểm phải chú ý |
| --- | --- | --- |
| Per-key lock | Một loader cho mỗi key tại một instance | Deadlock, timeout, giải phóng lock |
| Single-flight | Những caller cùng key chia sẻ một computation đang chạy | Failure, cancellation và ownership |
| Atomic compute | Đồng bộ hóa tạo entry theo API cache | Hạn chế loader trong critical section, reentrancy |
| Early refresh | Refresh trước khi hết hạn | Chi phí refresh |
| TTL jitter | Phân tán thời điểm hết hạn | Không hoàn toàn loại bỏ đồng thời |
| Distributed lock/coordinator | Phối hợp trên nhiều instance | Kết nối mạng, TTL lock, partial failure |

`ConcurrentHashMap` bảo đảm an toàn cho các thao tác được tài liệu của nó quy định, nhưng nếu code tự `get` rồi `put` thành hai bước riêng, nhiều luồng vẫn có thể cùng nhìn thấy miss. `computeIfAbsent` có thể giúp tình huống loader đồng bộ cụ thể; callback tính toán không được vi phạm ràng buộc như cập nhật đệ quy cùng map. Với phép tính bất đồng bộ, cần mô hình cache in-flight riêng.

Nếu nhiều caller chia sẻ cùng một `CompletableFuture` đang tính, một caller hủy không nhất thiết được quyền hủy tác vụ phục vụ các caller khác. Quy tắc ownership của cancellation/timeout phải tường minh.

## 10. Cache đối với phương thức bất đồng bộ

Với `CompletionStage<T>` hoặc `Future<T>`, phương thức có thể trả về handle **trước khi công việc hoàn tất**. Nếu caching advice lưu handle ngay khi method return:

~~~text
stage = proceed()  // chỉ là handle
cache.put(key, stage)
return stage
~~~

thì nó đã cache **đối tượng đại diện**, chưa chứng minh đã cache payload thành công. Đây có thể là lựa chọn có chủ đích để chia sẻ một phép tính đang chạy (*single-flight*), nhưng cần xem xét:

- Stage có thể thất bại, bị hủy, treo lâu hoặc timeout.
- Lưu future lỗi mãi sẽ khiến các caller mới nhận lại lỗi cũ.
- Hủy handle dùng chung có thể ảnh hưởng người gọi khác.
- Callback hoàn tất có thể chạy trên luồng khác, cần an toàn đồng thời và truyền ngữ cảnh.
- Invalidation chạy trong lúc task chưa xong có thể bị loader cũ ghi đè kết quả stale.
- Thay handle trả cho caller có thể làm đổi identity, cancellation/exception propagation.

Một cách khác là **chỉ cache payload sau successful completion**. Khi hit, advice có thể trả một stage đã hoàn tất với payload cached **nếu hợp đồng cho phép**. Cần xử lý đúng các trường hợp failure/cancel/timeout và giữ tính không chặn. Không gọi `join()` trong advice chỉ để lấy kết quả thật nếu API hứa trả ngay: làm bên gọi chờ và có thể gây nghẽn/deadlock.

Với publisher hoặc luồng dữ liệu lazy, return publisher chưa chắc kích hoạt công việc. Cùng publisher có thể có nhiều subscriber; cache publisher object không đồng nghĩa cache toàn bộ dữ liệu phát hoặc terminal outcome. Nếu muốn replay/share stream, phải có chính sách rõ về subscription, demand, backpressure, memory, lỗi và hủy. Aspect tự subscribe để “đổ dữ liệu vào cache” có thể kích hoạt tác vụ mới và làm thay đổi hành vi của caller.

## 11. Aspect ordering: caching có thể bỏ qua những gì khác?

Giả sử có authorization, metrics và caching:

~~~text
Cách A
authorization {
    metrics {
        cache {
            target
        }
    }
}

Cách B
cache {
    authorization {
        metrics {
            target
        }
    }
}
~~~

**Cách A:** mọi caller phải qua authorization và metrics, kể cả cache hit. **Cách B:** cache hit có thể không chạy authorization/metrics nằm phía trong. Nếu authorization bắt buộc ở mỗi lượt truy cập, cách B có thể vi phạm chính sách.

Tương tự:

- **Validation ngoài cache:** kiểm tra đầu vào ở mỗi lần; validation chỉ nằm trong target có thể bị skip trên hit.
- **Audit ngoài cache:** ghi mỗi lượt đọc nếu hợp đồng yêu cầu; audit trong target chỉ ghi các lần miss/bypass.
- **Tracing/metrics ngoài cache:** quan sát cả hit và miss; đặt bên trong cache chỉ đo target executions.
- **Retry ngoài hoặc trong cache:** thay đổi phạm vi attempt, lỗi và cache loader được retry; retry target có side effect cần chính sách riêng.
- **Transaction ngoài cache:** cache hit có thể mở một transaction không cần thiết; đặt transaction trong cache thì hit bỏ qua transaction.
- **Cache ghi kết quả trước commit:** có thể để lộ trạng thái chưa được xác nhận. Nên phối hợp invalidation/refresh sau commit hoặc chiến lược consistency phù hợp khi dữ liệu nguồn cần giao dịch.

Không có một thứ tự aspect phổ quát cho mọi hệ thống. Phải xác định chính sách nào áp cho **mọi lời gọi**, chính sách nào chỉ áp cho **lần target thực thi**, và thứ tự nesting thực tế của công cụ AOP.

## 12. Phụ thuộc vào khả năng nền tảng

**Không phải mọi đường gọi đều đi qua advice**. Điều kiện intercept khác nhau theo kỹ thuật:

| Kỹ thuật | Khả năng áp short-circuit | Giới hạn thường gặp |
| --- | --- | --- |
| Decorator thủ công | Wrapper có thể trả trực tiếp mà không gọi đối tượng gốc | Caller phải dùng wrapper |
| Dynamic proxy theo interface | Invocation handler có thể không gọi target | Chỉ những method/đường gọi đi qua proxy |
| Proxy dựa trên subclass | Có thể override/intercept method đủ điều kiện | `final`, `private`, constructor và visibility có thể giới hạn |
| Bytecode weaving | Có thể thay đổi hành vi tại join point được hỗ trợ | Cần cấu hình weaving/pointcut và quyền truy cập bytecode đúng |
| Runtime instrumentation | Có thể intercept/skip khi hook hỗ trợ thay return | Tùy runtime, classloader, module access và cơ chế instrument |

**Self-invocation:** nếu một method trong implementation gọi `this.otherMethod()`, lời gọi nội bộ ấy có thể không đi qua proxy bao ngoài, khiến cache advice của `otherMethod()` không được kích hoạt. Weaving intercept execution join point có thể cho hành vi khác. Phải kiểm chứng công cụ và điểm cắt thực, không chỉ nhìn annotation.

Một short-circuit phải trả giá trị **tương thích kiểu khai báo và hợp đồng**. Không trả `null` cho primitive, không thay ngoại lệ bằng một giá trị tùy tiện, không phá identity/side-effect constraints. Với `void` mà method tạo side effect bắt buộc, bỏ body không phải tối ưu caching vô hại.

Điểm cắt còn có thể khác nhau giữa call/execution, overload, bridge/synthetic methods, constructor và callback do runtime khác kích hoạt. Quyết định caching cần đặt trên phạm vi interception đã được xác minh.

## 13. Quan sát và vận hành

Một aspect cache tốt nên phân loại metrics:

| Metric | Diễn giải |
| --- | --- |
| `cache.hit` | Trả entry hợp lệ, target không chạy |
| `cache.miss` | Lời gọi hợp lệ phải chạy target do không có entry |
| `cache.bypass` | Bỏ cache theo policy và gọi target |
| `cache.stale` | Entry tồn tại nhưng không đủ freshness/version |
| `cache.load_success` | Loader thật đã hoàn tất thành công |
| `cache.load_failure` | Loader thật thất bại |
| `cache.invalidation` | Entry bị loại vì dữ liệu thay đổi |
| `cache.eviction` | Entry bị loại vì giới hạn sức chứa |
| `cache.inflight_shared` | Caller dùng chung computation đang chạy nếu có single-flight |

Hit rate thường tính hit/(hit+miss) cho các lượt đủ điều kiện; bypass có thể được báo riêng để không hiểu nhầm hiệu quả cache. Tách **latency của lookup cache**, **latency của loader** và **latency từ góc caller**. Một cache hit nhanh nhưng trả sai quyền hoặc stale ngoài mức cho phép vẫn là lỗi nghiêm trọng.

Giới hạn vận hành: cache không bounded gây tăng bộ nhớ/GC; key nhiều biến thể gây cardinality cao; TTL đồng loạt gây stampede; distributed cache có thể mất kết nối; dữ liệu sensitive có thể bị lộ nếu cache tier không được bảo vệ. Cần chính sách rõ nếu cache không khả dụng: có hệ thống có thể *fail open* bằng cách gọi target; có hệ thống không được làm vậy vì cache chính là một phần policy bảo mật hoặc chống lạm dụng.

## 14. Kiểm thử hành vi quan trọng

| Tình huống | Kiểm chứng |
| --- | --- |
| Lần đầu với key mới | Miss, target chạy đúng một lần |
| Lần sau cùng key, entry còn hợp lệ | Hit, không chạy target, kết quả đúng |
| Input khác | Không dùng nhầm entry |
| Bypass | Luôn gọi target, không “hit giả” |
| Input không hợp lệ | Bị từ chối trước cache hit nếu contract yêu cầu |
| Entry hết TTL / đổi version | Target chạy lại hoặc refresh theo policy |
| Tenant/quyền thay đổi | Không nhận dữ liệu sai phạm vi |
| Exception từ target | Truyền nguyên nhân gốc, không tạo success entry |
| Null/not-found | Có policy phân biệt negative entry và miss |
| Mutable return | Caller không thể sửa entry dùng chung ngoài hợp đồng |
| Concurrent miss cùng key | Số lần loader chạy đúng chính sách |
| Async failure/cancellation | Không giữ forever một exceptional/cancelled handle ngoài ý định |
| Invalidation trong khi loader chạy | Không tái điền stale entry sai version |
| Self-invocation | Hiểu đúng đường gọi bỏ qua proxy |
| Caching + authorization/transaction | Ordering không phá kiểm tra/commit |
| Cache outage | Fallback theo policy, không lộ dữ liệu |

Trong ví dụ Java, `[stats] target calls=4` chứng minh 5 lời gọi proxy chỉ chạy target 4 lần. Nhưng **giá trị trả về đúng và số lần target đúng** chỉ là bước đầu; triển khai thật còn cần kiểm thử freshness, concurrency, security, partial failure và nguyên tắc giới hạn cache.

## 15. Các lỗi thiết kế thường gặp

1. Chỉ kiểm tra `cache.containsKey(key)` mà không xác minh TTL, version và quyền.
2. Tạo key chỉ từ một phần tham số trong khi output còn phụ thuộc tenant, locale hoặc config.
3. Cache method tạo side effect rồi bỏ qua việc nghiệp vụ bắt buộc phải làm mỗi lần.
4. Đặt authorization/validation trong target nhưng cache hit xảy ra bên ngoài.
5. Nghĩ một annotation tự mang đến interception và cache.
6. Không hiểu self-invocation qua proxy nên kỳ vọng hit/miss không đúng.
7. Dùng `HashMap` không an toàn giữa nhiều luồng.
8. Làm `get` rồi `put` tách rời và tưởng concurrent loader chỉ chạy một lần.
9. Cache `null`, “not found” và lỗi như nhau mà không có policy.
10. Giữ future lỗi hoặc bị hủy mãi như một completed success cache entry.
11. Trả mutable cached object khiến caller sửa dữ liệu cho người khác.
12. Cache dữ liệu chưa commit hoặc invalidate trước khi transaction hoàn tất mà không xét race.
13. Tự subscribe publisher chỉ để “cache kết quả” và làm phát sinh side effect.
14. Đặt mục tiêu hit rate cao nhưng không kiểm tra correctness và bảo mật.
15. Đánh đồng “cache hit bỏ gọi target” với idempotency của lệnh ghi.

## 16. Checklist thiết kế caching advice

Trước khi cho phép around advice bỏ qua thực thi gốc, hãy trả lời:

1. Việc trả kết quả đã lưu có **tương đương về hành vi** với thực thi target theo hợp đồng hay không?
2. Nền tảng AOP có thật sự intercept đúng lời gọi và hỗ trợ return mà không `proceed()` không?
3. Policy bắt buộc nào phải thực thi **trên mọi lời gọi**, kể cả hit?
4. Điều kiện nào quyết định hit/miss/bypass/rejected?
5. Cache key có đủ input, tenant, locale, version, mode và context phụ thuộc kết quả không?
6. TTL/invalidation/version có đáp ứng mức freshness được cam kết không?
7. Loại outcome nào được cache: success, null, not-found, failure, future in-flight?
8. Giá trị trả về có mutable hoặc identity-sensitive không?
9. Có chính sách stampede, single-flight, timeout và cancellation đúng khi đồng thời không?
10. Cache có làm đổi semantics của transaction, retry, security, audit, tracing hoặc metrics không?
11. Cache backend gặp lỗi thì phải gọi target, trả lỗi hay chính sách khác?
12. Kiểm thử đã xác nhận cả **output**, **số lần target chạy** và **quyền/freshness** chưa?

## 17. Kết luận

Trong AOP, **around advice** có thể bỏ qua thực thi gốc bằng cách trả một cached result thay vì gọi `proceed()`. Điều kiện tiên quyết là entry còn hợp lệ, kết quả đúng với input/ngữ cảnh và việc không chạy target không vi phạm side effect hay chính sách phải thực hiện mỗi lần. Cache hit, miss và bypass là những nhánh khác nhau; chỉ hit hợp lệ mới là short-circuit thông thường.

Để sử dụng an toàn, cần hiểu khả năng interception của nền tảng, thiết kế key và lifecycle đúng, kiểm tra quyền trước khi trả cache, điều phối aspect ordering, xử lý đồng thời và trạng thái bất đồng bộ. Khi đó caching advice mới thực sự tối ưu việc thực thi mà vẫn giữ nguyên hợp đồng nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-005">Thiết kế khóa bộ nhớ đệm theo đúng ngữ cảnh và dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế khóa bộ nhớ đệm theo đúng ngữ cảnh và dữ liệu trong AOP

## 1. Vấn đề: kết quả giống nhau chỉ khi các điều kiện tạo kết quả giống nhau

Bộ nhớ đệm (cache) giúp trả lại kết quả đã tính thay vì thực hiện lại một phép tính hoặc truy vấn tốn kém. Khi dùng Aspect-Oriented Programming (AOP), một *around advice* có thể chặn lời gọi phương thức, tìm giá trị đã lưu, trả ngay khi có cache hit hoặc gọi phương thức gốc rồi lưu kết quả khi cache miss.

Điểm khó không nằm ở phép tra cứu cache mà ở câu hỏi: **hai lời gọi nào được phép dùng chung một kết quả?** Nếu thiết kế khóa sai, cache có thể trả kết quả cũ, sai phiên bản, sai người dùng hoặc thậm chí làm lộ dữ liệu giữa những phạm vi truy cập khác nhau.

Ví dụ một phép tính báo giá:

- Cùng mã sản phẩm và tiền tệ, cùng bảng giá, cùng tenant và nhóm khách hàng: có thể dùng chung kết quả.
- Cùng mã sản phẩm nhưng tenant khác: có thể áp dụng bảng giá hoặc dữ liệu riêng.
- Cùng tenant nhưng quyền xem giá hoặc nhóm khách hàng khác: kết quả có thể khác.
- Cùng đầu vào nhưng phiên bản chính sách hoặc bảng giá thay đổi: cần phân biệt dữ liệu cũ và mới.
- Chuỗi mã sản phẩm có khoảng trắng/thường-hoa khác nhau: chỉ được coi như một giá trị nếu **quy tắc nghiệp vụ** xác nhận chúng tương đương.

Do đó, một khóa cache đúng phải mô tả **toàn bộ những yếu tố có thể ảnh hưởng đến kết quả**, theo hợp đồng của lời gọi được cache.

## 2. Khái niệm AOP cần dùng

- **Join point:** vị trí thực thi mà cơ chế AOP có thể quan sát hoặc can thiệp; ở bài này là ranh giới gọi một phương thức có thể cache.
- **Pointcut:** quy tắc chọn các join point đủ điều kiện dùng chính sách cache.
- **Advice:** logic chạy quanh lời gọi, kiểm tra cache, gọi phương thức gốc khi miss và ghi kết quả.
- **Aspect:** tập hợp pointcut và advice phục vụ cùng một chính sách xuyên suốt.
- **Weaving/interception:** cách liên kết aspect vào chương trình; tùy cơ chế cụ thể mà một số lời gọi nội bộ hoặc điểm thực thi có thể không đi qua advice.

Cache là **chính sách thực thi**: nó thay đổi việc có cần tính lại kết quả hay không, nhưng về nguyên tắc không được làm thay đổi ý nghĩa kết quả mà người gọi được phép nhận.

Một luồng *around advice* đồng bộ cơ bản:

~~~text
caller -> pointcut phù hợp -> cache advice
                                |
                      tạo khóa từ ngữ cảnh
                      và đầu vào đã chuẩn hóa
                                |
                         tra cứu cache
                          /          \
                       HIT            MISS
                        |               |
                 trả giá trị          proceed()
                                        |
                                   tính thành công?
                                     /      \
                                    có      không
                                    |        |
                                lưu cache  truyền lỗi
                                    |
                                trả kết quả
~~~

Đây là sơ đồ khái niệm, không có nghĩa mọi phương thức hay mọi giá trị đều nên được cache. Việc lưu kết quả lỗi, kết quả rỗng, huỷ tác vụ hoặc phản hồi từ hệ thống ngoài phải có chính sách riêng.

## 3. Mô hình khóa: phụ thuộc vào những gì?

Giả sử phương thức logic là:

~~~text
quote(context, productCode, currency) -> price
~~~

Một kết quả có thể phụ thuộc vào:

| Thành phần | Có thể ảnh hưởng kết quả như thế nào? | Cách đưa vào khóa |
| --- | --- | --- |
| Danh tính thao tác | Hai phương thức khác nhau có thể nhận cùng tham số nhưng trả loại dữ liệu khác | Namespace/operation cố định, như quote:v1 |
| Tenant hoặc vùng dữ liệu | Cùng ID sản phẩm nhưng ở kho dữ liệu khác | Tenant ID ổn định lấy từ ngữ cảnh đáng tin |
| Quyền hoặc nhóm giá | Người mua thường và VIP có giá khác | Nhóm quyền/giá đã tính từ chính sách hoặc dấu vân tay quyền |
| Đầu vào nghiệp vụ | SKU, số lượng, tiền tệ, bộ lọc, phân trang | Biểu diễn chuẩn hóa của **mọi** tham số liên quan |
| Phiên bản dữ liệu | Bảng giá, snapshot, cấu hình đã đổi | Version/revision đáng tin, hoặc invalidation khi thay đổi |
| Locale/múi giờ | Chuỗi hiển thị, lịch và phân đoạn ngày có thể khác | Locale/zone khi chúng thực sự ảnh hưởng kết quả |
| Feature flag hoặc thuật toán | Cùng dữ liệu nhưng nhánh xử lý/giải thuật khác | Phiên bản chính sách, flag hoặc namespace mới |
| Phạm vi quyền nhìn thấy | Một tập dữ liệu được lọc theo tài khoản | Phạm vi truy cập hoặc tài khoản, khi cache phân quyền |

Không cần đưa **mọi thông tin sẵn có** vào khóa: điều này làm giảm cache hit mà không tăng tính đúng. Ngược lại, thiếu một yếu tố có tác động đến kết quả là lỗi nghiêm trọng.

Một cách kiểm tra tính đúng:

> Nếu hai lời gọi có cùng khóa K, liệu kết quả dùng chung có đúng với **cả hai lời gọi** theo hợp đồng nghiệp vụ và an toàn truy cập không?

Viết dưới dạng bất biến:

~~~text
key(a) == key(b)
    => reusableResult(a, b) == true
~~~

Chiều ngược lại không bắt buộc: hai lời gọi khác khóa vẫn có thể tình cờ trả cùng giá trị; chúng chỉ không bị buộc phải dùng chung bản cache. Không đánh đồng “giá trị kết quả trùng nhau” với “có quyền chia sẻ bản cache”.

## 4. Tách ngữ cảnh nghiệp vụ, dữ liệu đầu vào và trạng thái thực thi

### 4.1. Ngữ cảnh ảnh hưởng kết quả

Ngữ cảnh (context) có thể nằm ngoài danh sách tham số của phương thức: tenant hiện tại, vai trò/quyền, địa bàn, phiên bản bảng giá, locale, múi giờ, cấu hình tính giá. Advice chỉ nhìn thấy được những gì được đưa tới join point hoặc được cung cấp qua cơ chế ngữ cảnh được thiết kế rõ ràng.

Ví dụ một phương thức chỉ nhận mã sản phẩm nhưng bên trong sử dụng tenant và nhóm giá của yêu cầu hiện tại. Nếu advice tạo khóa chỉ từ mã sản phẩm, hai yêu cầu của hai tenant có thể va vào cùng một bản cache dù nhận kết quả khác nhau.

**Giải pháp ưu tiên:** biểu diễn các yếu tố ảnh hưởng kết quả thành ngữ cảnh bất biến, có tên và được truyền rõ cho phép tính; nếu dùng ngữ cảnh ngầm, xây dựng bộ trích xuất có hợp đồng và kiểm thử đầy đủ. Dữ liệu như tenant ID hoặc scope phân quyền phải xuất phát từ nguồn tin cậy phía hệ thống, không đơn giản chấp nhận giá trị tự khai từ client.

### 4.2. Ngữ cảnh không ảnh hưởng kết quả

Request ID, trace ID, timestamp ghi log, tên luồng hoặc IP ngẫu nhiên thường giúp quan sát và tương quan lời gọi, nhưng không quyết định kết quả của một phép tính thuần. Đưa chúng vào khóa làm mỗi lời gọi sinh một khóa khác nhau, khiến cache gần như vô dụng.

Ngoại lệ là khi *chính hợp đồng nghiệp vụ* dùng yếu tố ấy để tính toán. Chẳng hạn giá vé thực sự phụ thuộc thời điểm đặt, hoặc chính sách theo vùng địa lý dùng IP đã được chuẩn hóa đáng tin; khi đó cần mô hình hóa nó thành đầu vào nghiệp vụ hoặc mốc thời gian có nghĩa, không tùy tiện dùng thời điểm gọi System.currentTimeMillis().

### 4.3. Chuẩn hóa dữ liệu phải nhất quán với ngữ nghĩa

Hai cách viết " pen " và "PEN" có thể cùng chỉ một SKU nếu domain xác nhận bỏ khoảng trắng biên và không phân biệt chữ hoa/thường. Nhưng mã nhạy chữ hoa, token mã hóa, định danh người dùng hoặc đường dẫn tệp có thể không cho phép làm vậy.

Nguyên tắc:

1. Xác định các phép biến đổi hợp lệ trong domain trước khi xây khóa.
2. Chuẩn hóa **một lần** ở ranh giới đầu vào, dùng cùng dạng chuẩn cho cả khóa và tính toán.
3. Dùng Locale.ROOT khi chuẩn hóa mã kỹ thuật không phụ thuộc ngôn ngữ; với từ ngữ hiển thị cho người dùng, xử lý locale theo hợp đồng.
4. Phân biệt null, chuỗi rỗng và giá trị thiếu nếu chúng có ý nghĩa khác nhau.
5. Với số thập phân, ngày, múi giờ, enum và danh sách, xác định chính xác dạng biểu diễn và quy tắc tương đương.

Không được chỉ chuẩn hóa khóa trong khi vẫn gửi đầu vào gốc có ý nghĩa khác nhau vào phương thức: điều đó tạo hai lời gọi cùng khóa nhưng kết quả thực tế khác nhau.

## 5. Cách biểu diễn khóa an toàn

### 5.1. Khóa cấu trúc bất biến

Một khóa là **tuple bất biến có kiểu**, gồm namespace/schema version, tenant, scope, version dữ liệu và các tham số chuẩn hóa. Trong Java, record thích hợp để minh họa do tạo sẵn equals() và hashCode() theo thành phần.

~~~text
CacheKey(
  schemaVersion = 1,
  operation = "quote",
  tenantId = "ACME",
  priceTier = "VIP",
  priceListVersion = 7,
  sku = "PEN",
  currency = "VND"
)
~~~

Biểu diễn này tránh việc nối chuỗi thủ công kiểu "AC" + "ME" và "A" + "CME" có thể cho ra cùng chuỗi. Nếu phải chuyển tuple sang khóa chuỗi cho hệ thống lưu trữ ngoài, dùng encoding có độ dài trường/kiểu rõ ràng hoặc serialization canonical có phiên bản.

### 5.2. Tránh khóa dựa trên toString() hoặc hash đơn giản

- **Object.toString():** không có hợp đồng biểu diễn ổn định cho mọi lớp; có thể chứa địa chỉ/identity hay thông tin nội bộ.
- **Object.hashCode():** không đảm bảo duy nhất; va chạm hash có thể xảy ra, và hash không tự mang ý nghĩa phân vùng dữ liệu.
- **Nối chuỗi không escape:** có thể tạo va chạm giữa những tuple khác nhau, đặc biệt khi giá trị chứa dấu phân cách.
- **Serialization không chuẩn:** thứ tự thuộc tính của map, định dạng số và timezone có thể tạo nhiều khóa cho một dữ liệu logic hoặc gộp sai dữ liệu.
- **Giá trị mutable:** object/collection thay đổi sau khi làm khóa có thể phá equals/hashCode và gây lỗi tra cứu.

Nếu khóa là byte hoặc string canonical, nên có schema version. Hash mật mã có thể thu gọn một khóa dài, nhưng bản hash vẫn có xác suất va chạm, không tự giải quyết sai sót do thiếu thành phần và không nên dùng như cơ chế che giấu dữ liệu nhạy cảm.

### 5.3. Danh sách, tập hợp và map

Khóa từ tập hợp cần biết **thứ tự có ý nghĩa không**. Với danh sách ưu tiên, giữ thứ tự; với tập các nhãn lọc không quan tâm thứ tự, sắp xếp theo chuẩn domain trước khi serialize. Phải phân biệt phần tử trùng, null và các kiểu dữ liệu có thể có cùng biểu diễn chuỗi. Với map, xác định thứ tự key chuẩn và cách mã hóa cả khóa lẫn giá trị; không dựa vào thứ tự duyệt tình cờ của cấu trúc mutable.

## 6. Ví dụ Java 17 chạy độc lập: khóa đúng ngữ cảnh và dữ liệu

Chương trình dùng một decorator để minh họa hành vi *around advice*. Trong hệ thống AOP thực, pointcut và weaving có thể gắn cùng logic quanh phương thức; ở đây cách bọc tường minh giúp chương trình chạy không cần thư viện AOP. **Chính sách đúng đắn của khóa** là nội dung cần học, không phải cú pháp của một framework.

Báo giá phụ thuộc vào tenant, nhóm giá, phiên bản bảng giá, SKU và tiền tệ. Request chuẩn hóa SKU/tiền tệ ngay lúc xây dựng; khóa là record bất biến. Bộ đếm của dịch vụ gốc giúp phân biệt số lần gọi từ client với số lần tính toán thực sự.

Tạo tệp **ContextCacheDemo.java**:

~~~java
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ContextCacheDemo {
    record QuoteContext(String tenantId, String priceTier, int catalogVersion) {
        QuoteContext {
            Objects.requireNonNull(tenantId);
            Objects.requireNonNull(priceTier);
            if (tenantId.isBlank()
                    || !(priceTier.equals("REGULAR") || priceTier.equals("VIP"))
                    || catalogVersion < 0) {
                throw new IllegalArgumentException("invalid context");
            }
        }
    }

    record QuoteRequest(String sku, String currency) {
        QuoteRequest {
            Objects.requireNonNull(sku);
            Objects.requireNonNull(currency);
            sku = sku.strip().toUpperCase(Locale.ROOT);
            currency = currency.strip().toUpperCase(Locale.ROOT);
            if (sku.isEmpty()
                    || !(currency.equals("VND") || currency.equals("USD"))) {
                throw new IllegalArgumentException("invalid request");
            }
        }
    }

    record CacheKey(
            int schemaVersion,
            String tenantId,
            String priceTier,
            int catalogVersion,
            String sku,
            String currency) {}

    interface QuoteService {
        int quote(QuoteContext context, QuoteRequest request);
    }

    static final class DomainQuoteService implements QuoteService {
        final AtomicInteger computations = new AtomicInteger();

        @Override
        public int quote(QuoteContext context, QuoteRequest request) {
            computations.incrementAndGet();
            int base = switch (request.sku()) {
                case "PEN" -> 100;
                case "BOOK" -> 200;
                default -> throw new IllegalArgumentException("unknown sku");
            };
            int tenantAdd = context.tenantId().equals("ACME") ? 0 : 500;
            int tierDiscount = context.priceTier().equals("VIP") ? 20 : 0;
            int amount = base + tenantAdd - tierDiscount
                    + context.catalogVersion();
            return request.currency().equals("VND") ? amount : amount / 20;
        }
    }

    // Decorator minh họa một around advice.
    static final class CachingAspect implements QuoteService {
        private final QuoteService target;
        private final ConcurrentHashMap<CacheKey, Integer> cache =
                new ConcurrentHashMap<>();

        CachingAspect(QuoteService target) {
            this.target = target;
        }

        @Override
        public int quote(QuoteContext context, QuoteRequest request) {
            CacheKey key = new CacheKey(
                    1, context.tenantId(), context.priceTier(),
                    context.catalogVersion(),
                    request.sku(), request.currency()
            );
            return cache.computeIfAbsent(
                    key, ignored -> target.quote(context, request));
        }
    }

    public static void main(String[] args) {
        DomainQuoteService origin = new DomainQuoteService();
        QuoteService service = new CachingAspect(origin);

        QuoteContext regular = new QuoteContext("ACME", "REGULAR", 1);
        System.out.println("first="
                + service.quote(regular, new QuoteRequest(" pen ", "vnd")));
        System.out.println("same="
                + service.quote(regular, new QuoteRequest("PEN", "VND")));
        System.out.println("vip="
                + service.quote(new QuoteContext("ACME", "VIP", 1),
                        new QuoteRequest("PEN", "VND")));
        System.out.println("tenant="
                + service.quote(new QuoteContext("BETA", "REGULAR", 1),
                        new QuoteRequest("PEN", "VND")));
        System.out.println("version="
                + service.quote(new QuoteContext("ACME", "REGULAR", 2),
                        new QuoteRequest("PEN", "VND")));
        System.out.println("computations=" + origin.computations.get());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 ContextCacheDemo.java
java ContextCacheDemo
~~~

Kết quả stdout xác định:

~~~text
first=101
same=101
vip=81
tenant=601
version=102
computations=4
~~~

**Diễn giải từng lời gọi:**

1. Lời gọi đầu với tenant ACME, REGULAR, catalogVersion=1 và SKU " pen " được chuẩn hóa thành "PEN": cache miss, tính 100 + 0 + 0 + 1 = 101.
2. Lời gọi thứ hai có cùng khóa sau chuẩn hóa: cache hit, kết quả vẫn 101 mà không tính lại.
3. Cùng SKU nhưng priceTier=VIP: khóa khác, giá 100 − 20 + 1 = 81.
4. Cùng SKU và nhóm REGULAR nhưng tenant=BETA: khóa khác, giá 100 + 500 + 1 = 601.
5. Cùng tenant và nhóm REGULAR nhưng catalogVersion=2: khóa khác, kết quả 102.
6. Có năm lần gọi qua wrapper nhưng chỉ **bốn lần** thực thi phương thức tính giá gốc.

Đây là ví dụ tính giá giả định để minh họa sự phụ thuộc, không phải quy tắc giá của một hệ thống thương mại thực. Thuộc tính catalogVersion phải do nguồn dữ liệu đáng tin quản lý; priceTier cũng cần được xác thực hoặc suy ra từ quyền/nhóm giá phía hệ thống. Không cho người dùng tự chọn context tùy ý rồi dùng nó như bằng chứng phân quyền.

### Vì sao dùng ConcurrentHashMap.computeIfAbsent()?

Với cùng một khóa, thao tác computeIfAbsent() giúp việc tìm và tạo entry nhất quán trong một ConcurrentHashMap. Hàm tính toán có thể chạy trong đường khóa/chồng chờ nội bộ, vì vậy nó nên ngắn, không thay đổi chính map và không gọi vòng lặp lại cùng key. Ví dụ chỉ tính toán bằng CPU đơn giản; với công việc chậm hoặc phụ thuộc mạng, cần cơ chế tải một lần theo khóa (*single-flight*), timeout và chính sách khi tải lỗi phù hợp.

Lỗi ném ra từ phép tính trong ví dụ **không được lưu** thành giá trị; ConcurrentHashMap cũng không hỗ trợ null làm giá trị cache. Nếu hợp đồng cần cache kết quả null, không-tìm-thấy hoặc lỗi, phải dùng kiểu biểu diễn kết quả rõ ràng và đặt TTL khác theo trường hợp. Ví dụ dùng map trong bộ nhớ, không có giới hạn số entry, TTL hoặc invalidation: đây là các yêu cầu quan trọng khi đưa vào vận hành.

## 7. Chọn điểm áp dụng AOP và hợp đồng cache

AOP giúp áp dụng cùng chính sách cho nhiều phương thức, nhưng pointcut phải đủ hẹp để chỉ chọn những thao tác có thể dùng lại kết quả. Trước khi gắn advice cần kiểm tra:

1. **Tính lặp lại:** cùng khóa ở thời gian còn hiệu lực có được phép trả cùng kết quả không?
2. **Không có tác dụng phụ cần lặp lại:** bỏ qua thực thi trên cache hit có làm mất ghi DB, audit bắt buộc, gửi message hoặc thay đổi trạng thái không?
3. **Hợp đồng lỗi:** exception, timeout, huỷ và null có cần cache không?
4. **Vòng đời dữ liệu:** bản ghi thay đổi lúc nào và được invalidation theo cơ chế nào?
5. **Giới hạn quan sát:** cơ chế weaving/proxy có áp dụng với mọi đường gọi mong muốn không?
6. **Độ tin cậy ngữ cảnh:** tenant, quyền và version lấy ở đâu, có ổn định trong suốt lời gọi không?

Một *around advice* cần bảo toàn return type, ngoại lệ và ngữ nghĩa đồng bộ/bất đồng bộ. Nó không được gọi phương thức gốc nhiều lần chỉ vì kỹ thuật kiểm tra cache, trừ khi hợp đồng chính sách chủ động quy định retry. Không thực hiện truy cập cache bằng khóa tạo từ toString() của toàn bộ arguments hoặc từ chuỗi tham số chưa được kiểm duyệt.

Đối với **phương thức trả Future/CompletionStage**, cache chính đối tượng Future không tự tương đương với cache **kết quả đã hoàn tất**. Future có trạng thái, cancellation, timeout và ngữ nghĩa chia sẻ; người gọi huỷ handle chung có thể ảnh hưởng người gọi khác. Muốn cache async cần thiết kế cách dùng chung công việc đang chạy, cách phân phối kết quả, lỗi, huỷ và TTL sau hoàn tất. Không gọi get()/join() bên trong advice chỉ để biến kết quả async thành giá trị cache đồng bộ.

Với các hàm trả Stream, Iterator, InputStream hoặc thực thể mutable, cần cân nhắc việc dữ liệu có bị tiêu thụ một lần hoặc bị người gọi sửa sau khi cache hay không. Có thể phải lưu một dạng snapshot bất biến hoặc copy khi đọc/ghi cache; không mặc định rằng một object có thể chia sẻ tự do.

## 8. Hiệu lực, TTL, invalidation và phiên bản hóa

Khóa mô tả sự tương đương giữa hai lời gọi, còn **vòng đời cache** quyết định kết quả có còn hợp lệ tại thời điểm lấy ra không. Hai vấn đề bổ sung nhau nhưng không thay thế nhau.

### 8.1. Time-to-live (TTL)

TTL đặt thời gian tối đa entry có thể được phục vụ hoặc có hiệu lực theo chính sách; nó là cách giới hạn độ cũ, không đảm bảo dữ liệu mới ngay lập tức khi nguồn thay đổi. Với dữ liệu tài chính, quyền truy cập hoặc tồn kho biến động nhanh, TTL dài có thể không chấp nhận được.

Cần nêu rõ TTL tính từ khi tải xong, từ lúc ghi, hay được gia hạn khi đọc. Cache hit cao với dữ liệu sai không phải thành công.

### 8.2. Invalidation theo thay đổi dữ liệu

Khi nguồn dữ liệu đổi, có thể xóa những khóa phụ thuộc, xóa theo nhóm hoặc tăng revision/version rồi dùng khóa mới. Ví dụ đưa catalogVersion vào khóa cho phép bản giá ở phiên bản 2 không dùng nhầm entry của phiên bản 1. Tuy nhiên entry cũ vẫn chiếm bộ nhớ: cần chính sách eviction, giới hạn dung lượng hoặc dọn theo phiên bản.

Nếu có cache cục bộ ở nhiều tiến trình, invalidation phải được lan truyền phù hợp. Một process không nhận sự kiện invalidation vẫn có thể tiếp tục trả entry cũ; hệ thống cần xác định mức nhất quán có thể chấp nhận.

### 8.3. Version của thuật toán và format khóa

Khi thay công thức tính nhưng tên phương thức và tham số giữ nguyên, cache cũ có thể không hợp lệ dù dữ liệu chưa đổi. Cần đổi **schemaVersion/algorithmVersion** hoặc namespace để tách kết quả của thuật toán mới. Đây là lý do ví dụ đưa schemaVersion=1 vào CacheKey.

**Không nên** dùng version cố định mãi mà không có quy trình tăng phiên bản khi ý nghĩa khóa thay đổi; cũng không nên dùng thời gian hiện tại làm version ở mỗi lời gọi vì vô hiệu hóa cache.

## 9. Bảo mật và phân vùng cache

Khóa cache cũng là ranh giới ngăn dữ liệu của những người gọi không có quyền xem chéo nhau. Nếu kết quả đã được lọc theo quyền, khóa dùng chung cần phản ánh **phạm vi quyền của kết quả**, không chỉ tham số hiển thị.

Các lựa chọn phổ biến:

- **Dữ liệu công khai, giống nhau với mọi người:** cache chia sẻ theo tham số và version có thể phù hợp.
- **Dữ liệu theo tenant:** khóa hoặc namespace phải phân vùng tenant; việc truy cập cache vẫn phải qua kiểm tra quyền.
- **Dữ liệu theo nhóm quyền:** dùng scope/role/entitlement version ổn định nếu mọi người trong nhóm được phép chia sẻ cùng kết quả.
- **Dữ liệu cá nhân hóa:** cân nhắc định danh tài khoản hoặc user trong khóa, hoặc bỏ cache chia sẻ nếu rủi ro quá lớn.

Điều quan trọng: **khóa cache không phải cơ chế phân quyền**. Kiểm tra authorization phải được thực hiện theo chính sách tại ranh giới cần thiết; cache hit không được trở thành đường vòng bỏ qua kiểm tra. Không đưa token đăng nhập, mật khẩu, cookie phiên, khóa bí mật hoặc thông tin nhạy cảm dạng thô vào khóa, đặc biệt nếu key được gửi tới cache phân tán và ghi log. Khi cần fingerprint của quyền, nó phải được thiết kế để ổn định, không làm lộ bí mật, và thay đổi khi quyền đổi.

Nếu dữ liệu có khả năng thay đổi do quyền truy cập bị thu hồi, việc giữ entry sau khi thu hồi không được làm lộ thông tin qua cache hit. Cần invalidation/TTL và kiểm tra quyền độc lập theo yêu cầu bảo mật.

## 10. Vấn đề đồng thời và tải đột biến

Khi nhiều luồng cùng gọi một key đang miss, nếu mỗi luồng đều chạy phép tính, hệ thống gặp **cache stampede**. Trong AOP, advice áp dụng rộng có thể khuếch đại vấn đề khi nhiều join point đồng thời cache một thao tác.

Các kỹ thuật tùy yêu cầu:

- **Single-flight theo khóa:** nhiều người đợi chung một lần tải; cần timeout, xử lý cancellation và không giữ lock quá lâu.
- **TTL có jitter:** giảm khả năng hàng loạt entry hết hạn cùng lúc.
- **Giới hạn bộ nhớ/eviction:** tránh tăng số key vô hạn do đầu vào có cardinality cao.
- **Stale-while-revalidate:** trả dữ liệu cũ trong giới hạn được chấp nhận và cập nhật nền, **chỉ khi domain cho phép dữ liệu cũ**.
- **Negative caching:** lưu kết quả không tìm thấy trong thời gian ngắn khi phù hợp, tránh biến lỗi hạ tầng tạm thời thành kết quả “không có dữ liệu” kéo dài.

Với cache phân tán, tính nguyên tử trong một ConcurrentHashMap của một JVM **không** đồng nghĩa với single-flight trên nhiều máy. Phải chọn giải pháp đồng bộ/tải lại ở quy mô triển khai thực tế. Một distributed lock cũng không tự bảo đảm tính đúng của khóa; cùng khóa vẫn phải biểu diễn đúng các điều kiện đầu vào.

## 11. Cách đo và xác minh chính sách cache bằng AOP

Aspect quan sát có thể đo cache hit, miss, thời gian tra cứu, thời gian tính toán nguồn, tỉ lệ lỗi tải cache và số lần eviction. Tên metric cần phân biệt rõ:

~~~text
cache.lookup{operation="quote",result="hit"}
cache.lookup{operation="quote",result="miss"}
cache.load.duration{operation="quote"}
cache.load.failure{operation="quote"}
~~~

Đây là nhãn metric minh họa. Không đưa SKU, tenant ID, request ID hoặc toàn bộ cache key vào label của hệ thống metric nếu chúng có cardinality lớn; dùng chúng trong log chẩn đoán có kiểm soát quyền truy cập khi thực sự cần. Cache hit rate cao có thể đi kèm kết quả sai nếu khóa thiếu ngữ cảnh, vì vậy cần kiểm thử tính đúng song song với kiểm thử hiệu năng.

### Các ca kiểm thử thiết yếu

| Trường hợp | Kết quả mong đợi |
| --- | --- |
| Cùng tenant, scope, version và input đã chuẩn hóa | Dùng chung entry; giá trị đúng |
| Khác tenant | Không chia sẻ kết quả nếu tenant ảnh hưởng dữ liệu |
| Khác nhóm quyền/giá | Tạo key khác khi kết quả phụ thuộc nhóm |
| Khác currency/locale/timezone có nghĩa | Tạo key khác hoặc dữ liệu đúng theo contract |
| Khác version dữ liệu/thuật toán | Không dùng entry của version cũ |
| Hai input nhìn giống nhưng domain phân biệt | Không bị gộp nhầm bởi bước chuẩn hóa |
| Input bằng null, rỗng, chứa ký tự phân tách | Không va chạm khóa hoặc bị từ chối rõ ràng |
| Exception khi compute | Truyền lỗi đúng; chỉ cache lỗi nếu có chính sách |
| Nhiều luồng cùng key | Không race dữ liệu; số phép tính theo chính sách |
| Quyền bị thu hồi | Cache hit không vượt qua kiểm tra quyền |
| Nguồn dữ liệu cập nhật | Invalidation hoặc version hóa hoạt động |
| Kết quả có mutable state | Người gọi này không sửa kết quả người khác |

Một bộ kiểm thử tốt nên có ít nhất một trường hợp chứng minh **thiếu thành phần khóa làm sai dữ liệu** rồi xác nhận đã sửa. Ví dụ bỏ tenant khỏi CacheKey sẽ khiến giá của BETA có thể nhận 101 từ ACME thay vì 601, nếu các trường còn lại giống nhau và ACME gọi trước.

## 12. Checklist thiết kế một cache aspect đúng

Trước khi bật cache cho một pointcut:

1. Xác định rõ operation được cache, đầu vào, output và khả năng có tác dụng phụ.
2. Liệt kê tất cả phụ thuộc ảnh hưởng kết quả: tham số, tenant, quyền, locale, version, trạng thái cấu hình và dữ liệu nguồn.
3. Phân định những phụ thuộc nào cần thành phần khóa, những phụ thuộc nào được xử lý bằng invalidation hoặc TTL.
4. Chuẩn hóa dữ liệu theo **ngữ nghĩa domain**, đảm bảo tính toán dùng cùng dạng chuẩn.
5. Thiết kế key bất biến, có kiểu hoặc canonical serialization có schema version; tránh nối chuỗi mơ hồ.
6. Xác định cách cache null, exception, timeout, negative result, Future và kết quả mutable.
7. Đảm bảo cache hit không bỏ qua authorization và không làm mất tác dụng phụ bắt buộc.
8. Thiết lập eviction, giới hạn dung lượng, TTL và chính sách invalidation theo yêu cầu nhất quán.
9. Kiểm thử các trường hợp phân vùng tenant/quyền, đổi version, concurrency và đầu vào tương đương/khác biệt.
10. Đo hit/miss và latency với nhãn ít biến thể, đồng thời theo dõi tính đúng bằng kiểm thử nghiệp vụ.

**Nguyên tắc kết luận:** AOP là vị trí thuận lợi để triển khai **cơ chế cache xuyên suốt**, nhưng khóa cache phải được xác định theo **hợp đồng dữ liệu và ngữ cảnh nghiệp vụ** của phép tính. Hai lời gọi chỉ nên dùng chung entry khi người gọi được phép nhìn cùng kết quả và dữ liệu đó vẫn còn hiệu lực. Khóa đúng, chính sách hết hạn đúng và ranh giới phân quyền đúng là ba điều kiện phải đi cùng nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-006">Giới hạn thời gian và giới hạn khả năng dừng tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thời gian và giới hạn khả năng dừng tác vụ trong AOP

## 1. Bài toán: áp dụng chính sách thời gian tại đúng ranh giới thực thi

Aspect-Oriented Programming (AOP) cho phép tách các chính sách xuyên suốt như đo thời gian, từ chối tác vụ quá hạn, truyền deadline, ghi nhận timeout, yêu cầu hủy và quản lý tài nguyên khỏi logic nghiệp vụ. Một around advice có thể thực hiện kiểm tra trước lời gọi, gọi phương thức đích, quan sát kết quả và xử lý lỗi theo hợp đồng đã định.

Tuy nhiên, việc đặt advice bao quanh lời gọi không tự trao cho advice khả năng ngắt một phương thức Java đang thực thi. Giả sử phương thức readData() chờ I/O trong 30 giây. Quy tắc “thời gian tối đa 2 giây” có thể có nhiều ngữ nghĩa:

- **Giới hạn thời gian đợi của caller:** sau 2 giây, caller nhận thông báo timeout và thôi chờ. Công việc nền có thể tiếp tục.
- **Yêu cầu hủy:** sau 2 giây, runtime gửi cancel/interrupt/token. Task có thể dừng nếu hợp tác hoặc API tài nguyên có hỗ trợ.
- **Xác nhận task đã dừng:** cần tín hiệu hoàn tất thực tế hoặc cơ chế termination thích hợp. Việc nhận timeout hoặc future bị đánh dấu canceled chưa phải bằng chứng.
- **Giới hạn tổng thời gian operation:** tính cả thời gian xếp hàng, nhiều lời gọi con, retry, callback và cleanup theo contract. Mỗi bước tự cấp lại 2 giây có thể làm tổng thời gian vượt xa ngân sách.
- **Giới hạn việc sử dụng tài nguyên:** số luồng, kết nối, tác vụ đang chờ hoặc queue phải được kiểm soát độc lập; caller ngừng chờ không có nghĩa tài nguyên đã được giải phóng.

Trong bài này, ta phân biệt việc **phát hiện quá hạn**, **thực thi timeout đối với caller**, **gửi tín hiệu hủy**, và **đảm bảo kết thúc công việc**. Đây là những trách nhiệm khác nhau của aspect, runtime, code nghiệp vụ và thư viện quản lý tài nguyên.

> Quy tắc nền: TIMEOUT, CANCEL REQUESTED, CANCELED FUTURE và TASK TERMINATED không được gộp thành cùng một trạng thái.

## 2. Những thuật ngữ cần dùng chính xác

**Join point:** điểm thực thi mà một aspect theo dõi/can thiệp, thường là lời gọi phương thức. Một join point ở entry/exit chưa chắc bao quát cả công việc bất đồng bộ phát sinh từ lời gọi.

**Around advice:** mã chạy bao quanh join point. Nó có thể ghi timestamp trước/sau, kiểm tra điều kiện đầu vào, chuyển kết quả hoặc lỗi theo một chính sách đã công bố.

**Timeout:** khoảng thời gian tối đa được chờ hoặc thực thi theo định nghĩa cụ thể, ví dụ timeout khi kết nối, đọc socket, đợi lock, lấy giá trị future, hoặc tổng deadline nghiệp vụ. Không được dùng từ timeout mà bỏ qua đối tượng đang bị giới hạn.

**Deadline:** mốc kết thúc ngân sách thời gian của một operation. Các bước con cần sử dụng phần **thời gian còn lại** thay vì mỗi bước được cấp lại toàn bộ ngân sách.

**Cancellation:** yêu cầu không tiếp tục thực hiện công việc. Có thể là token, interrupt, Future.cancel(), hủy subscription, đóng request handle, hoặc gửi thông điệp đến bộ quản lý tác vụ.

**Cooperative cancellation:** task chủ động kiểm tra tín hiệu hủy hoặc dùng API hỗ trợ ngắt, từ đó thoát và giải phóng tài nguyên.

**Termination acknowledgment:** dấu hiệu đủ tin cậy cho biết task đã đi ra khỏi phần thực thi được quản lý hoặc đã dọn xong tài nguyên cần thiết. Tùy yêu cầu, tín hiệu này khác hoàn toàn với việc future được đánh dấu canceled.

**Soft limit và hard limit:** soft limit có thể chỉ cảnh báo hoặc dừng chờ; hard limit hứa ràng buộc cứng về hành vi/nguồn lực. AOP trên cùng JVM không thể bảo đảm cưỡng chế dừng một đoạn mã Java bất kỳ không hợp tác mà vẫn an toàn với dữ liệu dùng chung.

## 3. Phân biệt kết thúc thời gian chờ và kết thúc thực thi

Một sơ đồ trạng thái thường gặp:

~~~text
Caller                 Around advice           Worker / tài nguyên
  |                           |                         |
  | gọi operation             |                         |
  +-------------------------->| submit work             |
  |                           +------------------------>|
  |                           | chờ kết quả             | đang chạy
  |                           |                         |
  |                           | quá thời gian           | vẫn có thể chạy
  |<--------------------------+ Timeout                 |
  |                           | request cancellation    |
  |                           +------------------------>|
  |                           |                         | thấy/ngó lơ tín hiệu
  |                           |                         | cleanup / hoàn tất
~~~

| Sự kiện quan sát | Điều có thể khẳng định | Điều chưa thể khẳng định |
| --- | --- | --- |
| Future.get(timeout) ném TimeoutException | Caller đã chờ quá thời lượng chỉ định để lấy kết quả | Task bị cancel hoặc thực tế đã dừng |
| Future.cancel(true) trả true | Yêu cầu chuyển future sang canceled được chấp nhận theo contract | Luồng đã hoàn tất; I/O đã đóng; side effect đã rollback |
| Future.isCancelled() trả true | Trạng thái future là canceled | Worker đã cleanup xong |
| Thread nhận interrupt | Đã có tín hiệu interrupt theo Java | Code không hợp tác bắt buộc phải ngừng |
| Completion observer nhận lỗi timeout | Đối tượng được quan sát mang trạng thái lỗi timeout | Thao tác thực tế bên dưới cũng đã ngừng |
| Task báo terminated/cleanup complete | Phần công việc được giám sát thực sự kết thúc theo hợp đồng của tín hiệu | Mọi tác vụ con không được quản lý cũng đã kết thúc |

Nếu caller timeout sau 100 ms nhưng task vẫn chạy 10 giây và ghi dữ liệu, caller không được giả định “timeout = không có side effect”. Điều này đặc biệt quan trọng với thanh toán, ghi database, gửi tin nhắn, thao tác file hoặc retry không có idempotency.

## 4. Đo thời gian sau lời gọi không phải cưỡng chế timeout

Một advice đồng bộ có thể đo thời gian như sau:

~~~java
// Fragment minh họa: invokeTarget và recordLatency đã tồn tại.
long started = System.nanoTime();
try {
    return invokeTarget();
} finally {
    long elapsed = System.nanoTime() - started;
    recordLatency(elapsed);
}
~~~

Nếu invokeTarget bị kẹt vô hạn, khối finally cũng chưa được chạy. Việc kiểm tra elapsed sau khi target trả về chỉ **đo thời gian đã trôi qua**; nó không ngắt target vào đúng mốc deadline.

Một cách sai khác là ném ngoại lệ khi nhận thấy target quá chậm sau khi nó đã thực hiện xong:

~~~java
// Anti-pattern nếu nghiệp vụ chưa chấp thuận semantics này.
long begin = System.nanoTime();
Result result = target();
if (System.nanoTime() - begin > budgetNanos) {
    throw new RuntimeException("late");
}
return result;
~~~

Nếu target đã chuyển tiền hoặc gửi thông điệp, lỗi giả tạo này có thể khiến caller retry và thực hiện side effect lần nữa. Nếu chính sách yêu cầu **observe-only**, chỉ ghi nhận vi phạm SLO, không thay kết quả. Nếu chính sách yêu cầu báo trễ sau hoàn tất, phải xác định rõ trước liệu caller được phép hiểu nó là thất bại nghiệp vụ hay không.

## 5. Các cấp độ chính sách thực thi

| Chính sách | Chức năng | Phạm vi bảo đảm |
| --- | --- | --- |
| Observe-only | Ghi độ trễ và phát cảnh báo khi quá hạn | Không giới hạn thời gian thực tế |
| Pre-invocation admission | Từ chối trước khi bắt đầu nếu deadline đã hết | Không khởi chạy thao tác mới qua join point đó |
| Bounded wait | Giới hạn thời gian caller đợi future hoặc tín hiệu | Caller được giải phóng khỏi chờ quá lâu |
| Cooperative cancellation | Gửi token/interrupt/cancel handle, task tự dừng | Dừng được khi task và API tài nguyên hợp tác |
| API-native timeout | Thiết lập timeout/deadline trực tiếp trên I/O/client/driver | Hành vi do API đó quy định |
| External isolation | Chạy mã nguy cơ treo trong process/container có thể kết thúc | Có thể thu hồi tài nguyên trong phạm vi cô lập; side effect ngoài vẫn cần quản lý |

Nên chọn cấp độ thấp nhất nhưng đáp ứng đúng yêu cầu. Nếu mục tiêu chỉ là thống kê độ trễ, việc chuyển toàn bộ lời gọi sang worker để enforce timeout không cần thiết và có thể đổi ngữ nghĩa. Nếu yêu cầu là caller luôn thôi chờ sau 200 ms, bounded wait là một công cụ; nếu yêu cầu CPU phải được trả lại trong hạn, bounded wait đơn độc là chưa đủ.

## 6. Java interrupt là lời đề nghị dừng hợp tác

Trong Java, Thread.interrupt() thường dùng để truyền yêu cầu ngắt. Khi luồng đang chờ trên API interruptible, như Thread.sleep(), CountDownLatch.await(), BlockingQueue.take() hoặc Object.wait(), lời chờ có thể kết thúc bằng InterruptedException. Với code đang tính toán CPU và không kiểm tra interrupt flag, tín hiệu đó có thể không làm thay đổi luồng điều khiển.

Một tác vụ tính toán chủ động kiểm tra tín hiệu:

~~~java
// Ví dụ fragment có thể đặt trong một class Java 17.
static long calculateUntilCancelled() {
    long result = 0;
    while (!Thread.currentThread().isInterrupted()) {
        result++;
        if (result == 1_000_000) {
            break; // Điểm dừng bình thường chỉ dùng để minh họa.
        }
    }
    return result;
}
~~~

Nếu phép tính kéo dài hơn, code cần chọn nhịp kiểm tra phù hợp: kiểm tra quá thưa làm cancel phản hồi chậm; quá dày có thể tốn overhead. Với API ném InterruptedException, có hai phương án phổ biến:

- Truyền tiếp InterruptedException khi chữ ký/hợp đồng cho phép.
- Nếu phải xử lý tại đây, có thể phục hồi interrupt flag bằng Thread.currentThread().interrupt() trước khi thoát hoặc để tầng trên xử lý.

Ví dụ:

~~~java
// Minh họa Runnable chờ dữ liệu từ blocking queue.
try {
    queue.take();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đoạn này cần biến queue từ chương trình xung quanh. Không nuốt InterruptedException rồi quay lại vòng lặp vô hạn nếu task được kỳ vọng có thể hủy. Không dùng Thread.stop() để ép dừng: dừng luồng ở điểm tùy ý là nguy hiểm, có thể làm hỏng trạng thái chia sẻ và các bất biến của đối tượng.

Một số I/O hay thư viện native có cơ chế timeout/close/cancel riêng; interrupt không phải lúc nào cũng dừng được chúng. Aspect có thể cấp deadline, nhưng nơi thực hiện I/O phải chuyển nó sang API hỗ trợ thật sự.

## 7. Ví dụ Java 17+ chạy được: chờ hết hạn, yêu cầu hủy, xác nhận interrupt

Ví dụ dưới đây sử dụng ExecutorService và CountDownLatch để tạo một task **đang chờ thực sự**. Có ba tín hiệu được kiểm tra riêng: timeout của caller, trạng thái future nhận yêu cầu cancel, và code worker nhận InterruptedException.

Tạo file **TimeoutPolicyDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeoutPolicyDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptSeen = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);

        Future<String> task = executor.submit(() -> {
            started.countDown();
            try {
                neverReleased.await();
                return "COMPLETED";
            } catch (InterruptedException interrupted) {
                interruptSeen.countDown();
                Thread.currentThread().interrupt();
                return "STOPPED";
            }
        });

        try {
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }

            try {
                task.get(30, TimeUnit.MILLISECONDS);
                throw new AssertionError("Unexpected completion");
            } catch (TimeoutException expected) {
                System.out.println("WAIT TIMED OUT");
                boolean accepted = task.cancel(true);
                System.out.println("CANCEL ACCEPTED: " + accepted);
            }

            if (!interruptSeen.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker did not acknowledge interrupt");
            }

            System.out.println("TASK SAW INTERRUPT");
            System.out.println("FUTURE CANCELED: " + task.isCancelled());
        } finally {
            executor.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac TimeoutPolicyDemo.java
java TimeoutPolicyDemo
~~~

Đầu ra trong điều kiện thực thi bình thường:

~~~text
WAIT TIMED OUT
CANCEL ACCEPTED: true
TASK SAW INTERRUPT
FUTURE CANCELED: true
~~~

**Phân tích:**

1. started.await bảo đảm worker đã khởi chạy trước khi caller bắt đầu chờ 30 ms. Task đang chờ latch không được nhả, vì thế future chưa thể hoàn tất bình thường.
2. Future.get(30, MILLISECONDS) ném TimeoutException. Đây là **lỗi của thao tác chờ**, không phải bằng chứng worker đã dừng.
3. cancel(true) yêu cầu chuyển future sang canceled và cố gửi interrupt cho worker đang chạy. Với task đang chờ interruptible, yêu cầu hủy được chấp nhận.
4. interruptSeen.await(...) xác minh **code worker đã quan sát InterruptedException**. Nếu worker không hợp tác, sẽ không có tín hiệu này chỉ nhờ future báo canceled.
5. task.isCancelled() kiểm tra trạng thái future; nó là tín hiệu khác với việc worker đã vào nhánh interrupt.
6. shutdownNow() trong finally gửi yêu cầu dừng các task còn lại và không tự bảo đảm một task tùy ý đã chết.

Ví dụ đặt thời gian chờ hữu hạn 5 giây để không treo trong môi trường worker bị thiếu tài nguyên. Nếu worker không được lên lịch hoặc không nhận interrupt trong 5 giây, chương trình chủ động ném IllegalStateException; do đó không nên diễn giải stdout trên như bảo đảm thời gian chính xác cho mọi môi trường.

Trong ví dụ, worker có thể return "STOPPED" sau khi nhận interrupt, nhưng future đã bị cancel, nên caller không nhận giá trị đó từ Future.get(). Trạng thái future và vòng đời code task là hai thứ khác nhau. Chương trình này minh họa cơ chế mà advice có thể phối hợp với executor, không phải một implementation weaving AOP hoàn chỉnh.

## 8. Future.get timeout không giống Future.cancel

Một API như Future.get(timeout, unit) trả kết quả nếu future hoàn tất trong thời gian chờ. Nếu hết hạn, phương thức ném TimeoutException. Theo contract, bản thân thao tác chờ có thời hạn **không tự động cancel** công việc.

~~~java
// Fragment minh họa; future, processValue và recordTimeout có sẵn.
try {
    String value = future.get(200, TimeUnit.MILLISECONDS);
    processValue(value);
} catch (TimeoutException timeout) {
    recordTimeout();
    // Tùy chính sách mới cân nhắc future.cancel(true).
}
~~~

Future.cancel(true) cố yêu cầu ngắt nếu task đang chạy; cancel(false) yêu cầu hủy future mà không gửi interrupt vào task đang chạy. Giá trị boolean từ cancel phản ánh việc hủy được chấp nhận theo trạng thái future; không phải lời hứa task đã ngừng CPU/I/O hoặc đã cleanup. Một future có thể không được tạo bởi FutureTask nên chi tiết cancellation phụ thuộc implementation.

Một lời gọi get có thể phát sinh InterruptedException nếu **chính thread đang chờ** bị interrupt; cũng có thể phát sinh ExecutionException nếu task kết thúc bằng ngoại lệ. Advice cần phân biệt rõ ba nhánh: timeout trong lúc chờ, ngắt caller và lỗi thật của task. Không chuyển mọi ngoại lệ thành TimeoutException.

## 9. CompletableFuture.orTimeout thay outcome của future, không cưỡng chế dừng worker

Trong Java 17, CompletableFuture.orTimeout(timeout, unit) sẽ sắp đặt để **chính CompletableFuture được gọi** hoàn thành exceptional bằng TimeoutException nếu chưa hoàn thành kịp. Nó không có cam kết gửi interrupt để dừng công việc nền tạo ra future.

~~~java
// Fragment minh họa; startAsyncWork tạo CompletableFuture<String>.
CompletableFuture<String> stage = startAsyncWork();
stage.orTimeout(100, TimeUnit.MILLISECONDS);
~~~

Hệ quả quan trọng:

- Future có thể ở trạng thái exceptional do timeout, dù thread đang chạy công việc vẫn tiếp tục xử lý.
- Nếu gọi completeOnTimeout(value, timeout, unit), future có thể nhận giá trị fallback dù kết quả thật chưa đến; đó là **thay đổi outcome hiển thị**, không phải hủy công việc.
- Khi công việc nền sau đó hoàn tất, future đã terminal có thể không nhận được kết quả mới, nhưng tác dụng phụ bên ngoài có thể đã xảy ra.
- Nếu một aspect gọi orTimeout lên future do target trả về, aspect **đang thay đổi trạng thái future gốc**, không chỉ quan sát. Cần có quyền theo contract.
- Cancellation của dependent stage không mặc nhiên lan thành cancellation của task upstream. Cần kiểm tra hợp đồng API cụ thể.

So sánh ngắn:

| API/cơ chế | Điều thay đổi khi hết hạn | Có ngắt công việc thực sự? |
| --- | --- | --- |
| Future.get(t, unit) | Caller không còn chờ; nhận TimeoutException | Không |
| Future.cancel(true) | Yêu cầu đánh dấu hủy và gửi interrupt nếu phù hợp | Không được bảo đảm |
| CompletableFuture.orTimeout | CompletableFuture có thể exceptional bằng TimeoutException | Không |
| CompletableFuture.completeOnTimeout | CompletableFuture có thể hoàn tất bằng fallback | Không |
| Timeout của HTTP/socket/driver | Thao tác I/O nhận deadline theo contract của tài nguyên | Phụ thuộc API và tầng thực thi |
| Task kiểm tra cancellation token | Task có thể tự thoát và cleanup | Phụ thuộc code hợp tác |

Vì thế, nếu một aspect muốn **đo timeout** mà không đổi contract, hãy quan sát thay vì vô tình áp dụng orTimeout. Nếu muốn caller thực sự nhận TimeoutException khi quá hạn, cần mô tả điều này trong chính sách và quyết định riêng có/không yêu cầu hủy công việc.

## 10. Deadline dùng chung qua các lời gọi lồng nhau

Giả sử operation có ngân sách 800 ms:

~~~text
request: deadline tổng 800 ms
   |
   +-- validate input       100 ms
   +-- call service A       450 ms
   +-- call service B       còn gần 250 ms, trừ overhead
   +-- cleanup / respond    cần dự trù thêm theo policy
~~~

Nếu mỗi bước con lại được cấp 800 ms, tổng thời gian vượt xa deadline. Một aspect ở entry có thể tạo context ngân sách; các advice tại tầng con lấy thời gian **còn lại**, hoặc từ chối khi ngân sách không đủ.

Trong cùng JVM, System.nanoTime() phù hợp để đo khoảng thời gian đã trôi qua vì không phụ thuộc chỉnh đồng hồ hệ thống:

~~~java
// Fragment minh họa; nextCall được khai báo ở chương trình xung quanh.
long budgetNanos = TimeUnit.MILLISECONDS.toNanos(800);
long startedNanos = System.nanoTime();

// ... chạy một số bước ...
long elapsedNanos = System.nanoTime() - startedNanos;
long remainingNanos = budgetNanos - elapsedNanos;

if (remainingNanos <= 0) {
    throw new IllegalStateException("Deadline exceeded before next step");
}
nextCall(remainingNanos, TimeUnit.NANOSECONDS);
~~~

Khi triển khai, cần kiểm tra budget âm/0, đơn vị đổi sang nanosecond, overflow trong phép cộng/chuyển đổi, và việc làm tròn gây timeout 0. NanoTime có ý nghĩa để so sánh **thời lượng trên cùng JVM**, không phải timestamp lịch có thể gửi giữa các máy.

Khi deadline truyền qua network/process khác, không truyền trực tiếp giá trị nanoTime của JVM hiện tại và suy đoán máy khác có cùng gốc thời gian. Có thể chuyển ngân sách còn lại hoặc một deadline theo thời gian thực với chính sách xử lý lệch đồng hồ, rồi xác định lại mốc monotonic khi vào từng process.

Nếu operation có deadline 800 ms còn một phương thức con có limit 300 ms, thời gian chờ hữu hiệu là min(thời gian còn lại của cha, 300 ms). Phương thức con không được vô tình kéo dài deadline cha. Hãy tính cả queue, retry, backoff, và nếu cần, phần thời gian dự trữ cho cleanup/response.

## 11. Deadline xuyên ranh giới bất đồng bộ

Một lời gọi phương thức có thể trả về CompletionStage, Future, callback handle, task ID hoặc Publisher trước khi công việc hoàn tất. Around advice tại lời gọi này chỉ biết **invocation đã trả về**. Để quan sát kết quả thật, cần hook đúng cơ chế:

| Mô hình | Ranh giới để theo dõi deadline/cancel |
| --- | --- |
| CompletionStage | Completion callback và timeout policy trên stage phù hợp |
| ExecutorService | Submission, task execution wrapper, future lifecycle |
| Callback API | Wrapper callback nhận result/error, timer và hủy đăng ký theo contract |
| Stream/Publisher | Mỗi subscription, tín hiệu terminal, cancellation handle |
| Queue/fire-and-forget | Consumer, operation ID, completion event hoặc task supervisor |
| I/O chuyên biệt | Request/read/connect timeout hoặc cancel handle của API |

Ví dụ timeline:

~~~text
t0: around advice bắt đầu lời gọi tạo future
t1: target trả future pending; advice trả future
t2: deadline của operation hết hạn; caller nhận timeout
t3: worker vẫn chạy; có hoặc không nhận yêu cầu hủy
t4: worker dừng hoặc hoàn tất muộn; cleanup thực hiện
~~~

Nếu đo latency ở t0 → t1 rồi gọi đó là “thời gian nghiệp vụ”, số đo sai. Nếu muốn biết khi nào tác vụ thật sự kết thúc, cần completion hook hoặc tín hiệu lifecycle khác. Nếu hệ thống cho phép timeout ở t2, ghi riêng **caller timed out** và **work terminated at t4**, không ép chúng vào cùng một mốc.

### Context propagation

ThreadLocal của caller không tự động đi theo tác vụ trên executor/callback khác. Một aspect có thể capture deadline/correlation ID lúc submit, truyền vào task hoặc callback, restore khi thực thi và cleanup trong finally. Không để context của request cũ rò sang request mới trên thread pool.

Một kỹ thuật thực tiễn là truyền cancellation token tường minh qua đối tượng context hoặc tham số công việc. Token cần an toàn khi truy cập đa luồng, và phải có điểm kiểm tra ở những công việc dài. Nếu caller đã rời đi, các callback muộn cần xem còn nên phát kết quả nữa không và xử lý race theo state machine.

## 12. Không dùng executor wrapper một cách vô điều kiện

Việc quanh mỗi target đồng bộ bằng cách chạy nó trên executor rồi chờ get(timeout) thay đổi execution semantics:

1. **Thread identity:** target chạy trên luồng khác; code phụ thuộc thread hiện tại có thể sai.
2. **Locking:** caller đang giữ một lock rồi đợi worker cần cùng lock có thể deadlock.
3. **ThreadLocal/context:** transaction context, tracing, security identity, locale có thể không đi theo.
4. **Exception:** checked exception từ task thường được bao bởi ExecutionException khi tiêu thụ future; không tự động giữ nguyên hợp đồng throw.
5. **Scheduling:** thứ tự side effect có thể thay đổi hoặc xen kẽ với lời gọi khác.
6. **Cancellation:** cancel(true) không bắt buộc worker ngừng nếu code không hợp tác.
7. **Lifecycle:** caller có thể đóng tài nguyên sau timeout trong khi worker vẫn sử dụng.
8. **Load:** số task chờ/còn chạy có thể tăng dù caller timeout nhanh.
9. **Reentrancy:** method được gọi lồng nhau và aspect nhiều tầng có thể tạo executor/task vô ích, thậm chí chờ vòng.
10. **Idempotency:** caller retry khi worker cũ chưa dừng có thể thực hiện cùng side effect nhiều lần.

Nên ưu tiên đặt timeout tại API I/O hoặc cơ chế bất đồng bộ sở hữu tác vụ. Khi thật sự phải chuyển thread để giới hạn thời gian chờ, hãy định nghĩa tường minh sự thay đổi contract, giới hạn số worker/queue và quản lý cleanup.

Cơ chế weaving có thể cho AOP thêm join point, nhưng không tự biến mã bất kỳ thành cancelable. Khả năng dừng vẫn phụ thuộc task và tài nguyên thực thi.

## 13. Cancel không có nghĩa rollback hoặc không có side effect

Giả sử task chuyển tiền:

~~~text
0 ms:  gửi lệnh chuyển tiền đến hệ thống khác
100ms: caller hết thời hạn đợi
120ms: future được yêu cầu cancel
300ms: hệ thống ngoài xác nhận tiền đã chuyển
~~~

Ở t2 caller có thể nhận timeout nhưng lệnh gửi đi từ t0 vẫn được phía ngoài xử lý. Retry ngay có thể chuyển tiền lần nữa nếu không có idempotency. Vì vậy:

- Dùng operation ID/idempotency key ở những nghiệp vụ có khả năng lặp.
- Khi timeout ở ranh giới mạng, cho phép trạng thái **không biết kết quả** nếu không có xác nhận.
- Có API truy vấn/reconcile trạng thái thật khi cần.
- Chỉ retry theo chính sách đã xét idempotency và retry budget.
- Không tuyên bố cancellation đã rollback transaction.
- Nếu task giữ tài nguyên độc quyền, đợi tín hiệu termination/cleanup thích hợp trước khi cấp phát lại.

Nếu code không thể hợp tác và cần đảm bảo không làm treo JVM, cách ly trong process/container với ranh giới quản lý tài nguyên có thể thích hợp hơn. Nhưng kết thúc process cũng không rollback tự động side effect trong database hoặc hệ thống từ xa.

## 14. Hủy hợp tác nên có giao thức rõ ràng

Trong các hệ thống yêu cầu an toàn cao, cancellation là một quy trình thay vì một lệnh:

1. **Request:** deadline watcher/caller yêu cầu hủy.
2. **Delivery:** interrupt, token hoặc native cancellation signal đến worker.
3. **Acknowledgment:** worker báo đã nhận yêu cầu, nếu giao thức hỗ trợ.
4. **Quiescence:** phần công việc cần dừng không còn chạy nữa.
5. **Cleanup:** tài nguyên được đóng hoặc chuyển giao an toàn.
6. **Final status:** kết quả cuối được công bố: succeeded, failed, canceled, timeout hoặc unknown.

Có race khi task hoàn tất gần đúng thời điểm timeout. Cancel có thể trả false vì công việc đã xong, hoặc future đã canceled trước khi cleanup xong. Policy phải quyết định: completion đúng mốc deadline được ưu tiên thế nào, caller nhìn thấy kết quả nào và việc bồi hoàn/đối soát được thực hiện ra sao.

Nếu caller đã timeout và không thể chờ vô hạn, có thể phát yêu cầu hủy rồi trả caller, đồng thời tiếp tục theo dõi cleanup từ một supervisor. Khi công việc không dừng sau khoảng grace hữu hạn, báo trạng thái “timeout; termination not confirmed” thay vì ghi “canceled and stopped”.

Không thay giao thức này bằng việc chỉ đọc Future.isCancelled().

## 15. Vị trí phù hợp của advice và hook

| Điểm chèn | Việc nên làm | Sai lầm cần tránh |
| --- | --- | --- |
| Trước method invocation | Kiểm tra deadline và quyết định có nhận lời gọi không | Cho rằng có thể ngắt target bất kỳ khi đang chạy |
| Around/after-returning | Đo invocation latency và bảo toàn kết quả | Nhầm method returned với async operation completed |
| After-throwing | Ghi nhận lỗi đồng bộ thực sự | Phân loại mọi exception thành timeout |
| Submit task | Gắn context/token, ghi queue timestamp | Xem submit accepted là task success |
| Task wrapper | Kiểm tra hủy trước khi chạy; đo execution; cleanup | Giải phóng permit khi caller hết chờ dù worker còn chạy |
| Future completion | Quan sát success/failure/cancellation thật ở stage được chọn | Cho rằng timeout stage ngắt worker |
| Callback invocation | Xem callback được gọi lúc nào; bảo toàn error channel | Giả định callback luôn chạy thread khác |
| Stream subscription | Quan sát onNext/onError/onComplete/cancel theo protocol | Tự subscribe thêm để đo và làm sinh side effect |
| Resource-native API | Cấu hình read/connect/request deadline | Chỉ đặt timeout ở outer advice mà không giới hạn I/O |

Nếu cơ chế AOP hỗ trợ pointcut trên nhiều method, chính sách cũng cần định nghĩa **bỏ qua/ghép các deadline lồng nhau** và tránh instrument chính infrastructure hẹn giờ, logging, scheduler của aspect, gây reentrancy.

## 16. Retry, circuit breaker, bulkhead và giới hạn tài nguyên

Timeout có liên hệ với những chính sách thực thi khác nhưng không thay thế chúng.

**Retry:** nếu operation được cấp 800 ms, mỗi retry phải dùng ngân sách còn lại sau attempt trước, network latency và backoff. Không cho retry vô hạn khi task trước chưa rõ đã dừng; tránh retry thao tác có side effect chưa idempotent.

**Circuit breaker:** có thể từ chối lời gọi mới khi phụ thuộc liên tục lỗi/chậm. Nó không tự dừng những tác vụ đang chạy. Breaker open và cancellation là hai chức năng khác nhau.

**Bulkhead/concurrency limit:** hạn chế số worker, connection hoặc operation đang chạy. Nếu permit biểu diễn việc worker đang chiếm tài nguyên, phải trả permit khi **worker thật sự kết thúc**, không phải ngay lúc caller timeout.

**Queue limit:** tránh số task đang chờ tăng vô hạn. Queue bị đầy có thể khiến executor ném RejectedExecutionException đồng bộ lúc submit; đây là lỗi admission, không phải lỗi timeout khi chạy.

**Rate limiter:** chỉ kiểm soát tần suất nhận request, không đảm bảo thời gian từng request.

**Fallback:** trả dữ liệu thay thế sau timeout là chính sách nghiệp vụ, có thể che việc worker vẫn đang chạy và làm caller hiểu nhầm. Cần telemetry riêng cho fallback và xác nhận side effect.

Một hệ thống có vẻ đáp ứng nhanh vì caller luôn timeout sau 100 ms, nhưng 1.000 task nền vẫn chạy 30 giây và chiếm mọi worker. Vì vậy, nên đo cả wait-timeout và in-flight work, queue depth, task chưa dừng, late completion, cancel accepted/ignored và thời gian cleanup.

## 17. Bảo toàn ngoại lệ và kết quả theo chính sách

Advice liên quan timeout dễ làm đổi hợp đồng phương thức nếu xử lý ngoại lệ thiếu phân biệt.

| Loại tình huống | Ý nghĩa | Cách xử lý cần quyết định |
| --- | --- | --- |
| Target ném RuntimeException | Lỗi nghiệp vụ đồng bộ | Ném lại theo contract; không tự bọc thành timeout |
| Future.get ném TimeoutException | Caller chờ quá hạn | Báo timeout, tùy policy mới yêu cầu hủy |
| Caller bị InterruptedException | Chính luồng chờ bị ngắt | Truyền hoặc khôi phục cờ interrupt và thoát theo hợp đồng |
| Task thất bại; get ném ExecutionException | Kết quả công việc exceptional | Xem cause, truyền/biến đổi có chủ đích |
| Future canceled; get ném CancellationException | Trạng thái hủy future | Phân loại cancellation; không coi là thành công |
| Executor từ chối task | Lỗi admission/scheduler | Xử lý theo capacity/backpressure policy |
| Telemetry observer bị lỗi | Lỗi của instrumentation | Cô lập theo chính sách quan sát, tránh che business outcome |

Không catch Throwable rồi gán mọi lỗi là DeadlineExceeded. Khi logging/metrics advice ném exception sau khi target đã thành công, nó có thể biến một lời gọi thành công thành thất bại; cần xử lý sự cố instrumentation riêng. Khi callback observation của CompletionStage ném lỗi, dependent stage có thể thất bại dù stage gốc thành công. Các hook đo đạc nên bảo toàn semantics của future nếu không được phép thay đổi contract.

Nếu API cung cấp timeout dưới dạng checked exception, signature của lời gọi phải cho phép nó hoặc có chính sách chuyển đổi được công bố. Không lạm dụng một ngoại lệ unchecked mới để né giao kèo và làm caller không biết khi nào an toàn để retry.

## 18. Trường hợp callback và stream nhiều sự kiện

Callback không nhất thiết chạy bất đồng bộ; nó có thể được gọi ngay trong thao tác đăng ký. Một hệ thống callback một lần cần quyết định trạng thái terminal khi timeout và result cùng xảy ra gần nhau: chỉ một nhánh được công bố nếu contract yêu cầu single completion. Việc bỏ qua callback đến muộn ở caller không tự ngăn tác dụng phụ của worker.

Với stream/Publisher, timeout có thể có nhiều ý nghĩa:

- Chờ phần tử đầu tiên quá hạn.
- Khoảng lặng giữa hai phần tử vượt hạn.
- Tổng thời gian một subscription vượt hạn.
- Subscription bị hủy bởi consumer.
- Tổng lifecycle của nguồn sự kiện nhiều subscription.

Các ý nghĩa này khác nhau và phải gắn với cơ chế/hook stream tương ứng. Một around advice trên hàm trả Publisher chỉ biết đối tượng Publisher đã được tạo, chưa biết subscriber nào hay khi nào kết thúc. Không tự subscribe từ advice chỉ để đợi dữ liệu; việc này có thể kích hoạt công việc phụ và thay đổi side effect.

## 19. Những sai lầm cần tránh

**Sai lầm 1: Sau khi return mới kiểm tra elapsed rồi coi là đã enforce timeout.** Đây chỉ là hậu kiểm và có thể ném lỗi sau side effect đã hoàn thành.

**Sai lầm 2: Nhận TimeoutException từ Future.get rồi ghi “worker stopped”.** Caller chỉ hết hạn đợi; worker vẫn có thể chạy.

**Sai lầm 3: Gọi cancel(true) rồi lập tức tái cấp phát tài nguyên độc quyền.** Future canceled chưa chắc worker đã cleanup.

**Sai lầm 4: Dùng Thread.stop để ép deadline.** Không an toàn cho dữ liệu chia sẻ; ưu tiên cooperative cancellation hoặc isolation.

**Sai lầm 5: Tạo executor trong mọi lời gọi advice.** Dễ đổi thread context, lock, thứ tự side effect và gây quá tải.

**Sai lầm 6: Mỗi downstream call lại có timeout mới đầy đủ.** Làm tổng thời gian vượt deadline; phải chia ngân sách còn lại.

**Sai lầm 7: Coi timeout là rollback.** Side effect từ task hoặc hệ thống ngoài có thể vẫn thực hiện; cần idempotency và reconcile.

**Sai lầm 8: Bắt InterruptedException rồi tiếp tục vòng lặp.** Có thể vô hiệu hóa cancellation/shutdown.

**Sai lầm 9: Cho rằng orTimeout dừng supplier.** CompletableFuture có thể terminal timeout trong khi supplier vẫn chạy.

**Sai lầm 10: Chỉ theo dõi caller latency, không theo dõi task còn chạy.** Số worker treo và queue dồn có thể tăng dù mọi caller “timeout nhanh”.

## 20. Kịch bản kiểm thử quan trọng

Hãy kiểm thử các tình huống phân biệt thật rõ:

1. Target hoàn tất trước deadline, caller nhận chính giá trị và trạng thái bình thường.
2. Task chờ quá thời gian, get timeout nhưng task chưa nhận cancel: worker vẫn có thể hoàn tất sau.
3. Task hợp tác: cancel(true) được chấp nhận, worker xử lý interrupt và phát termination signal.
4. Task không hợp tác: future báo canceled, worker vẫn chạy; không báo nhầm terminated.
5. Task hoàn tất và cancel đua nhau: trạng thái caller/worker nhất quán theo contract.
6. Task bị hủy trước khi được scheduler chạy: có thể không có callback execution.
7. Caller bị interrupt trước khi timeout: phân biệt InterruptedException với TimeoutException.
8. Lời gọi I/O có timeout riêng ngắn hơn tổng deadline: dùng giá trị ngắn hơn.
9. Two nested calls và retry: tổng thời gian không vô tình reset.
10. Caller timeout sau một side effect; retry có idempotency hoặc reconciliation.
11. Logger/observer ném lỗi: không che lỗi nghiệp vụ hoặc đổi stage outcome ngoài chính sách.
12. Task cleanup chậm: trạng thái future canceled xuất hiện trước cleanup; permit được giữ đúng lúc.
13. Executor quá tải: từ chối submit, không báo nhầm là execution timeout.
14. Context thay đổi luồng: deadline/cancellation token được propagation và cleanup đúng.
15. Stream nhiều subscription: mỗi subscription có timer, cancel và terminal state riêng.

Test không nên chỉ assert một TimeoutException; phải quan sát cả **trạng thái caller** lẫn **vòng đời execution/tài nguyên**. Khi có yêu cầu cứng về thời gian và tài nguyên, cần đo cả khả năng chịu tải khi hàng loạt task không hợp tác.

## 21. Câu hỏi tự đánh giá

**Câu 1.** Around advice gọi target trực tiếp trên một thread; sau khi target trả về nó phát hiện đã chạy 2 giây dù ngân sách 1 giây. Có thực thi timeout đúng hạn không?

**Đáp án:** Không. Nó chỉ đo sau khi target kết thúc. Nếu ném timeout sau side effect có thể khiến caller retry nhầm. Muốn giới hạn thực tế cần cơ chế hủy/deadline được target và tài nguyên hỗ trợ, hoặc giới hạn thời gian chờ caller với contract rõ.

**Câu 2.** Future.get(200, MILLISECONDS) ném TimeoutException. Task đã dừng chưa?

**Đáp án:** Chưa thể kết luận. Future không tự bị cancel chỉ vì thao tác get quá hạn. Caller phải quyết định riêng về yêu cầu hủy và quan sát termination nếu cần.

**Câu 3.** Future.cancel(true) trả true, nhưng worker chạy vòng lặp CPU không kiểm tra interrupt. Có điều gì bảo đảm worker dừng?

**Đáp án:** Không. Future có thể canceled, trong khi worker vẫn chạy. Cần cooperative check hoặc cơ chế isolation/resource-native cancel phù hợp.

**Câu 4.** Deadline tổng 800 ms, bước A tiêu 650 ms. Bước B được cấp timeout bao nhiêu?

**Đáp án:** Không quá thời gian còn lại (xấp xỉ 150 ms trừ overhead, chi phí response/cleanup theo policy), không cấp lại 800 ms.

**Câu 5.** CompletableFuture đã bị orTimeout hoàn thành exceptional, nhưng worker vẫn gửi dữ liệu sau đó. Caller retry có nguy cơ gì?

**Đáp án:** Side effect trùng lặp vì timeout future không tự dừng công việc. Cần idempotency, trạng thái operation và xác nhận từ hệ thống đích.

**Câu 6.** Một advice chuyển lời gọi đồng bộ sang worker thread để timeout. Caller đang giữ lock mà worker cần. Vấn đề là gì?

**Đáp án:** Có nguy cơ deadlock: caller đợi worker, worker đợi lock của caller. Thay thread còn có thể mất ThreadLocal và thay ordering; không chuyển execution tùy tiện.

**Câu 7.** Task đã bị cancel và future.isCancelled() bằng true. Có thể dùng lại ngay tài nguyên chỉ dành riêng cho task đó không?

**Đáp án:** Chưa chắc. Cần biết worker đã dừng và hoàn tất cleanup, nhất là khi tài nguyên có thể tiếp tục bị truy cập sau khi future đã canceled.

**Câu 8.** Một stream phát dữ liệu liên tục không có onComplete bình thường. Timeout toàn subscription và timeout chờ phần tử tiếp theo có giống nhau không?

**Đáp án:** Khác nhau. Cần xác định loại timer và hook theo stream: tổng vòng đời subscription, khoảng lặng giữa các phần tử, hoặc thời gian chờ phần tử đầu tiên.

## Kết luận

Trong AOP, **giới hạn thời gian là chính sách; dừng tác vụ là năng lực của code thực thi và mô hình bất đồng bộ/tài nguyên**. Around advice có thể tạo và truyền deadline, từ chối lời gọi khi hết ngân sách, đo độ trễ, giới hạn thời gian caller chờ và yêu cầu hủy. Nhưng advice không thể tự cưỡng chế dừng an toàn mã Java không hợp tác. Hãy phân biệt timeout, yêu cầu cancel, future canceled và termination thực tế; dùng cơ chế native của tác vụ/tài nguyên để áp dụng deadline; kiểm soát context, retry, concurrency và side effect; và kiểm chứng cả kết quả caller lẫn vòng đời worker.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-007">Giới hạn tốc độ hoặc số lời gọi đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tốc độ hoặc số lời gọi đồng thời bằng Aspect-Oriented Programming (AOP)

## 1. Tại sao đặt chính sách giới hạn ở một aspect?

Trong một hệ thống có nhiều điểm thực thi, không phải phương thức nào cũng được phép nhận vô hạn lời gọi. Một tác vụ có thể tiêu tốn connection, thread, CPU, bộ nhớ hoặc hạn ngạch dịch vụ bên ngoài. Khi nhiều nơi cần cùng chính sách kiểm soát tải, **Aspect-Oriented Programming (AOP)** cho phép áp dụng logic giới hạn ở những *join point* đã chọn thay vì chèn mã kiểm tra và giải phóng tài nguyên vào từng phương thức nghiệp vụ.

Có hai câu hỏi độc lập:

1. **Giới hạn tốc độ (rate limiting):** Trong một khoảng thời gian, hệ thống cho phép bao nhiêu yêu cầu bắt đầu? Ví dụ trung bình 10 yêu cầu/giây với khả năng chấp nhận một đợt ngắn 20 yêu cầu.
2. **Giới hạn số lời gọi đồng thời (concurrency limiting):** Tại một thời điểm, có tối đa bao nhiêu thao tác đang thực thi/chưa hoàn tất? Ví dụ chỉ năm thao tác đang sử dụng pool kết nối.

Chúng có thể áp dụng riêng hoặc kết hợp. Rate limiter điều tiết **nhịp bắt đầu**; concurrency limiter điều tiết **số tác vụ đang tồn tại**. Một aspect phù hợp sẽ chặn lời gọi tại đầu vào, ra quyết định nhận/từ chối/chờ, rồi chuyển tiếp hoặc dừng thao tác theo chính sách được công bố.

**Mục tiêu học tập:** hiểu thuật toán giới hạn, xác định đúng đơn vị đo và phạm vi điểm cắt, triển khai advice bảo toàn kết quả/exception/cancellation, kiểm soát bộ nhớ và thời gian chờ, và đánh giá được tác động trong đa luồng, bất đồng bộ hoặc nhiều instance.

## 2. Phân biệt hai giới hạn bằng ví dụ

Giả sử hệ thống cho phép tối đa **20 lời gọi mỗi giây** và tối đa **4 lời gọi đồng thời**.

- Nếu 20 lời gọi đến rất nhanh và mỗi lời gọi kết thúc trong 1 ms, concurrency có thể không vượt 4 nhưng rate limiter vẫn từ chối lời gọi vượt ngân sách đã cấp.
- Nếu chỉ 5 lời gọi đến trong một giây nhưng mỗi lời gọi mất 10 giây, rate có thể hợp lệ trong khi concurrency limiter phải chặn lời gọi thứ năm nếu bốn lời gọi trước vẫn chưa kết thúc.
- Nếu một lời gọi kết thúc nhưng ngay lập tức có lời gọi khác thay thế, số tác vụ đang chạy vẫn tối đa 4; giới hạn số lần bắt đầu trong giây là một trục kiểm soát khác.

| Thuộc tính | Rate limiter | Concurrency limiter |
| --- | --- | --- |
| Đối tượng giới hạn | Số lượt đến/được phép bắt đầu theo thời gian | Số thao tác chưa kết thúc |
| Đơn vị cấu hình | calls/s, calls/min, tokens/s, burst capacity | Số permit, slot hoặc in-flight tasks |
| Trạng thái chính | Token, cửa sổ thời gian, số lượt trong cửa sổ | Số permit đang giữ và số permit còn trống |
| Khi thao tác hoàn tất | Thường không hoàn lại token đã tiêu thụ | Trả lại permit đúng một lần |
| Khi không còn hạn ngạch | Từ chối hoặc chờ có hạn mức | Từ chối hoặc chờ có hạn mức |
| Rủi ro nếu thiếu cơ chế | Burst/spam và vượt quota | Cạn tài nguyên, nghẽn pool, OOM |
| Vấn đề khó | Định nghĩa thời gian, burst, phân vùng quota | Định nghĩa vòng đời, timeout, cancellation, deadlock |

Đối với một tác vụ đã hoàn tất, số slot được giải phóng có thể tăng ngay; với rate limiting, kết thúc nhanh **không tạo thêm ngân sách tốc độ**. Cần tránh nhầm lẫn này khi cấu hình cả hai.

### Giới hạn in-flight không đồng nghĩa giới hạn thread

Một tác vụ bất đồng bộ có thể rời thread gọi rồi tiếp tục chờ I/O trong tương lai. Nếu mục tiêu là giới hạn số request đang được xử lý, slot phải được giữ đến khi có trạng thái terminal, **không phải** đến khi phương thức trả về đối tượng `Future` hoặc `CompletionStage`. Ngược lại, nếu chính sách chỉ nhằm giới hạn số lần thread thực thi đoạn CPU đồng bộ, phạm vi giữ slot ngắn hơn có thể đúng. Trước khi code, luôn ghi rõ đơn vị và thời điểm “bắt đầu/kết thúc”.

## 3. Vị trí đặt aspect trong đường thực thi

Trong AOP nói chung:

- **Join point:** điểm có thể áp dụng can thiệp, ví dụ thực thi phương thức được chọn.
- **Pointcut:** biểu thức/chính sách chọn phương thức, loại thao tác, nhóm tài nguyên hoặc vùng code được kiểm soát.
- **Before advice:** có thể kiểm tra quota trước khi cho tác vụ chạy, nhưng khó tự bảo đảm trả permit trên mọi đường thoát nếu không có cơ chế phối hợp.
- **Around advice:** phù hợp để lấy permit/token, chuyển tiếp lời gọi gốc **đúng một lần**, quan sát thành công/exception, rồi trả permit trong `finally` khi vòng đời là đồng bộ.
- **After advice:** có thể quan sát kết thúc, nhưng phải gắn đúng với lần đã acquire và đường kết thúc thực tế.

Luồng tổng quát của một around advice đồng bộ:

~~~mermaid
flowchart TD
    A[Lời gọi đi qua pointcut] --> B{Còn slot đồng thời?}
    B -->|Không| R[Từ chối hoặc chờ có giới hạn]
    B -->|Có| C[Giữ slot]
    C --> D{Có token tốc độ?}
    D -->|Không| E[Trả slot rồi từ chối]
    D -->|Có| F[Gọi nghiệp vụ đúng một lần]
    F --> G[Trả kết quả hoặc chuyển tiếp lỗi gốc]
    G --> H[Trả slot trong finally]
~~~

Điều kiện **chính sách áp dụng đúng nơi** quan trọng không kém thuật toán. Một lời gọi trực tiếp bỏ qua proxy, lời gọi nội bộ không đi qua điểm chặn hoặc một đường API chưa được chọn sẽ không tự động chịu giới hạn. Với weaving ở mức bytecode, khả năng bắt các điểm này có thể khác. Phải xác minh điểm cắt thực tế bằng test, thay vì chỉ tin vào khai báo.

### Đặt giới hạn ở biên có ý nghĩa nghiệp vụ

Nếu mỗi request đi qua ba method A→B→C nhưng cả ba đều được cùng một limiter bảo vệ, một request có thể tiêu thụ ba token hoặc ba slot và tự chặn chính nó. Cần xác định **một đơn vị logic**:

- Theo user/API key: hạn ngạch một khách hàng.
- Theo endpoint/operation: bảo vệ một loại thao tác.
- Theo resource pool: bảo vệ database, hệ thống tệp hoặc dịch vụ ngoài.
- Theo request logic: mỗi request chỉ nhận một quyết định kiểm soát ở một biên nhất quán.
- Theo attempt: mỗi lần retry được xem là một lần gọi thật sự, có thể cần tính vào quota để tránh khuếch đại tải.

Pointcut quá rộng còn có thể chọn chính thư viện điều khiển quota, tạo đệ quy. Chọn phạm vi có chủ ý và loại trừ code enforcement/telemetry khỏi các điểm cần bảo vệ.

## 4. Các thuật toán giới hạn tốc độ

### 4.1 Fixed window counter

Chia thời gian thành các cửa sổ cố định, ví dụ từng phút. Mỗi cửa sổ đếm số yêu cầu; khi đạt N thì từ chối tiếp cho đến cửa sổ mới.

**Ưu điểm:** đơn giản, dễ triển khai và lưu trữ. **Nhược điểm:** có thể nhận gần 2N yêu cầu trong khoảng rất ngắn ở ranh giới hai cửa sổ: N lời gọi vào cuối cửa sổ cũ và N vào đầu cửa sổ mới. Vì vậy hạn mức “100/phút” không đảm bảo mọi khoảng 60 giây liên tục đều tối đa 100 lần.

### 4.2 Sliding window log hoặc sliding window counter

- **Sliding log:** giữ timestamp của các yêu cầu còn nằm trong khoảng trượt; trước khi quyết định, bỏ những timestamp đã hết hạn rồi kiểm đếm. Rõ ràng nhưng có thể dùng bộ nhớ tỷ lệ với số yêu cầu và số khóa.
- **Sliding window counter:** tổng hợp các cửa sổ nhỏ hơn hoặc nội suy giữa cửa sổ hiện tại và trước đó; giảm bộ nhớ nhưng có sai số phụ thuộc thuật toán.
- Phải công bố ranh giới bao gồm/không bao gồm, cách xử lý thời gian lùi và clock giữa các máy.

### 4.3 Token bucket

Một xô có dung lượng tối đa `capacity` và tốc độ nạp `rate` token/giây. Mỗi lần bắt đầu công việc tốn một hoặc nhiều token. Token tích lũy khi ít tải, đến tối đa dung lượng; do đó có thể hỗ trợ burst ngắn nhưng giữ tốc độ trung bình lâu dài.

Ví dụ `capacity = 5` và `rate = 2 tokens/s`:

- Ban đầu đầy 5 token, có thể nhận năm yêu cầu ngay.
- Yêu cầu thứ sáu ngay sau đó bị từ chối nếu chưa được nạp thêm token.
- Sau 0,5 giây không tiêu thụ token, xô có thêm khoảng một token.
- Sau thời gian chờ dài, xô không vượt quá 5 token.

Mô hình tính ở thời điểm `now`:

`tokens = min(capacity, tokens + elapsedSeconds × rate)`

Khi `tokens >= cost`, trừ `cost` và nhận; nếu không, từ chối hoặc tính thời gian đợi. Với token phân số dùng `double`, cần chú ý sai số dấu phẩy động và kiểm thử ranh giới; với quota quan trọng có thể dùng đại lượng nguyên và đơn vị thời gian đủ nhỏ. Dùng nguồn thời gian monotonic cho elapsed; thời gian lịch có thể nhảy do đồng bộ đồng hồ.

**Điểm mạnh:** hỗ trợ burst có giới hạn, tương đối dễ áp dụng. **Điểm yếu:** giới hạn trung bình và burst không cấm mọi đợt dồn trong khoảng rất ngắn; nếu yêu cầu một nhịp phát đều, cân nhắc cơ chế spacing/queue có giới hạn.

### 4.4 Leaky bucket và điều phối nhịp

Mô hình leaky bucket thường đưa yêu cầu vào một bộ đệm có sức chứa giới hạn và xử lý/cho phép rời đi theo nhịp đều. Nó giúp làm mượt tốc độ, nhưng thêm **queueing latency**, khả năng đầy hàng đợi và trách nhiệm hủy việc đang chờ. Một thuật toán “leaky bucket” cụ thể còn phụ thuộc cách định nghĩa của từng tài liệu/công cụ: nên kiểm tra nó là traffic shaper có queue hay cách tính tương đương timestamp.

**Không có thuật toán nào “tốt nhất” cho mọi workload.** Nếu muốn chấp nhận burst nhỏ rồi hồi phục, token bucket hợp lý; nếu cần trần số yêu cầu trong cửa sổ trượt, dùng window thích hợp; nếu cần phát với nhịp đều, xét scheduler/queue có giới hạn.

## 5. Giới hạn số lời gọi đồng thời bằng permit

Một semaphore đếm permit là cơ chế phù hợp cho tác vụ đồng bộ:

1. Xác định số slot tối đa `maxConcurrent`.
2. Khi vào advice, gọi `tryAcquire()` để chiếm một permit; nếu không có, từ chối ngay hoặc chờ theo timeout.
3. Chỉ sau khi lấy permit thành công mới cho nghiệp vụ chạy.
4. Dùng `finally` trả permit khi nghiệp vụ kết thúc hoặc ném lỗi.
5. Không trả permit nếu chưa lấy được; không trả hai lần; không giữ permit khi tác vụ đã kết thúc.

Bất biến lý tưởng ở thời điểm ổn định:

`0 <= inFlight <= maxConcurrent`

`availablePermits + inFlight = maxConcurrent`

Lưu ý đây là mô hình tổng quát khi một tác vụ giữ đúng một permit và không có thao tác thay đổi số permit khác. Khi nhiều thread đang acquire/release, snapshot của các bộ đếm do nhiều API cung cấp có thể chênh lệch trong khoảnh khắc.

### Fail-fast hay đợi?

**Fail-fast:** `tryAcquire()` trả về ngay. Ưu điểm là latency của yêu cầu bị từ chối có thể dự đoán, tránh tạo thêm queue. Nhược điểm là số lỗi “busy” tăng trong burst ngắn.

**Chờ có thời hạn:** `tryAcquire(timeout, unit)` có thể giảm tỷ lệ từ chối khi slot sắp được giải phóng, nhưng phải ràng buộc timeout, số thread chờ, cancellation và deadline của request. Một hệ thống tạo vô hạn thread để chờ permit chỉ di chuyển bài toán quá tải vào bộ nhớ và scheduler.

**Chờ vô hạn:** dễ gây nghẽn và làm thời gian phản hồi vượt hạn mức; không nên chọn mặc định cho API có latency SLO. Với event loop hoặc callback quan trọng, không nên block thread chỉ để chờ semaphore; dùng giải pháp bất đồng bộ với queue có giới hạn hoặc từ chối sớm.

### Fairness và ưu tiên

Một semaphore có thể dùng chính sách công bằng, nhưng công bằng không tự tạo thêm capacity và cũng không đồng nghĩa deadline luôn được đáp ứng. Fairness thường có thêm chi phí điều phối; với traffic nhiều mức ưu tiên, cần chính sách dành slot hoặc tách pool rõ ràng. Đừng để request chậm hoặc retry chiếm hết slot của tác vụ quan trọng mà không có chiến lược admission control.

## 6. Kết hợp hai giới hạn trong một around advice

Nếu cả rate và concurrency đều có hiệu lực, thứ tự kiểm tra ảnh hưởng semantics:

**Phương án A: kiểm tra rate rồi concurrency.** Dễ phản ánh mọi attempt vào rate quota, nhưng nếu bị concurrency từ chối thì token đã bị tiêu thụ; muốn hoàn token phải định nghĩa rõ tính công bằng và thuật toán rollback.

**Phương án B: giữ slot concurrency rồi kiểm tra rate.** Một yêu cầu bị từ chối vì hết slot không tiêu tốn token tốc độ; nếu thiếu token thì phải trả ngay slot. Cách này hữu ích khi rate quota chỉ dành cho yêu cầu thực sự được phép đi vào nhánh xử lý. Nhưng lúc nhiều thread tranh permit, thứ tự admission có thể khác thứ tự arrival.

Không có thứ tự nào mặc định phù hợp mọi quota; hãy xác định rõ **bị concurrency từ chối có tính vào hạn ngạch hay không**, và ai sở hữu quyền quyết định (client, endpoint, operation, tenant). Ví dụ Java dưới đây minh họa **Phương án B, fail-fast**, nhằm giữ code đơn giản và bảo toàn permit.

Các phản hồi bị từ chối cần có ý nghĩa:

- Trong API, có thể quy ước lỗi rate limit / busy, ví dụ phản hồi giao thức tương ứng và gợi ý thời gian retry khi ước tính được.
- Trong thư viện phương thức, có thể ném exception phân biệt `RateExceeded` và `CapacityExceeded` để caller xử lý.
- Không giả vờ một lệnh gọi đã thành công khi chưa chạy nghiệp vụ.
- Không thay lỗi nghiệp vụ thành “rate exceeded” sau khi nghiệp vụ đã bắt đầu, trừ khi có chính sách ngắt chủ động và hợp đồng rõ ràng.

## 7. Ví dụ Java 17+ chạy độc lập: token bucket + semaphore trong advice

Ví dụ tạo **JDK dynamic proxy** để minh họa around advice cho interface `Task`. Một token bucket dùng đồng hồ monotonic do chúng ta cung cấp, nên có thể kiểm thử nạp token bằng cách tăng clock ảo mà không dùng `Thread.sleep()`. Một semaphore giới hạn số lời gọi đang chạy; ví dụ thứ hai cố ý giữ một cuộc gọi đang bận bằng `CountDownLatch` để kiểm tra từ chối một cuộc gọi khác. Hai thử nghiệm dùng hai limiter độc lập.

**Tệp: `AopAdmissionDemo.java`**

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class AopAdmissionDemo {
    public interface Task {
        String execute(String id) throws InterruptedException;
    }

    static final class RateExceeded extends RuntimeException {
        RateExceeded() {
            super("rate limit");
        }
    }

    static final class CapacityExceeded extends RuntimeException {
        CapacityExceeded() {
            super("concurrency limit");
        }
    }

    static final class ManualClock implements LongSupplier {
        private final AtomicLong now = new AtomicLong();

        @Override
        public long getAsLong() {
            return now.get();
        }

        void advanceNanos(long nanos) {
            now.addAndGet(nanos);
        }
    }

    static final class TokenBucket {
        private final double capacity;
        private final double refillPerSecond;
        private final LongSupplier ticker;
        private double tokens;
        private long lastNanos;

        TokenBucket(double capacity, double refillPerSecond,
                    LongSupplier ticker) {
            if (!(capacity > 0) || !(refillPerSecond > 0)) {
                throw new IllegalArgumentException("positive limits required");
            }
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.ticker = ticker;
            this.tokens = capacity;
            this.lastNanos = ticker.getAsLong();
        }

        synchronized boolean tryConsume() {
            long now = ticker.getAsLong();
            // Với đồng hồ monotonic, elapsed bình thường luôn >= 0.
            if (now > lastNanos) {
                double elapsedSeconds =
                    (now - lastNanos) / 1_000_000_000.0;
                tokens = Math.min(
                    capacity, tokens + elapsedSeconds * refillPerSecond
                );
                lastNanos = now;
            }

            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }

    static final class AdmissionAdvice implements InvocationHandler {
        private final Object target;
        private final TokenBucket bucket;
        private final Semaphore slots;

        AdmissionAdvice(Object target, TokenBucket bucket, int concurrent) {
            if (concurrent <= 0) {
                throw new IllegalArgumentException("positive slots required");
            }
            this.target = target;
            this.bucket = bucket;
            this.slots = new Semaphore(concurrent);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
            }

            if (!slots.tryAcquire()) {
                throw new CapacityExceeded();
            }

            try {
                if (!bucket.tryConsume()) {
                    throw new RateExceeded();
                }

                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException error) {
                    // Bảo toàn nguyên nhân lỗi gốc của nghiệp vụ.
                    throw error.getCause();
                }
            } finally {
                // Đúng một release cho mỗi acquire thành công.
                slots.release();
            }
        }
    }

    static Task guarded(Task target, TokenBucket bucket, int concurrent) {
        return (Task) Proxy.newProxyInstance(
            Task.class.getClassLoader(),
            new Class<?>[] {Task.class},
            new AdmissionAdvice(target, bucket, concurrent)
        );
    }

    static final class HeldTask implements Task {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String execute(String id) throws InterruptedException {
            if ("hold".equals(id)) {
                entered.countDown();
                release.await();
            }
            return "ok-" + id;
        }
    }

    public static void main(String[] args) throws Exception {
        ManualClock ticker = new ManualClock();
        Task rateTask = guarded(
            id -> "ok-" + id,
            new TokenBucket(2, 2, ticker),
            2
        );

        System.out.println(rateTask.execute("first"));
        System.out.println(rateTask.execute("second"));

        try {
            rateTask.execute("third");
        } catch (RateExceeded error) {
            System.out.println("rate=blocked");
        }

        ticker.advanceNanos(500_000_000L);
        System.out.println(rateTask.execute("after-refill"));

        HeldTask held = new HeldTask();
        Task concurrentTask = guarded(
            held,
            new TokenBucket(100, 100, System::nanoTime),
            1
        );

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = pool.submit(
                () -> concurrentTask.execute("hold")
            );
            held.entered.await();

            try {
                concurrentTask.execute("other");
            } catch (CapacityExceeded error) {
                System.out.println("concurrency=blocked");
            }

            held.release.countDown();
            System.out.println(first.get());
            System.out.println(concurrentTask.execute("after-release"));
        } finally {
            held.release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac AopAdmissionDemo.java
java AopAdmissionDemo
~~~

**Stdout kỳ vọng** (bỏ qua môi trường làm gián đoạn chương trình bất thường):

~~~text
ok-first
ok-second
rate=blocked
ok-after-refill
concurrency=blocked
ok-hold
ok-after-release
~~~

Giải thích:

1. Xô của `rateTask` có dung lượng 2 token; hai lời gọi đầu được nhận. Lời gọi thứ ba xảy ra cùng thời điểm trên clock ảo nên bị từ chối.
2. Tăng clock ảo 500 triệu nano giây tương đương 0,5 giây. Với mức nạp 2 token/giây, xô nhận thêm một token; lời gọi tiếp theo được nhận.
3. `HeldTask` có một lời gọi được giữ chờ trên thread trong pool. Khi nó đã vào thân phương thức, semaphore của proxy chỉ có một slot và đang bận. Lời gọi từ main bị từ chối vì concurrency.
4. Khi mở latch, lời gọi đầu hoàn tất và `finally` giải phóng slot. Lời gọi sau đó được nhận.
5. Nếu nghiệp vụ ném exception, advice mở lớp bọc `InvocationTargetException` để caller thấy lỗi gốc; `finally` vẫn trả slot. Một yêu cầu bị rate từ chối sau khi giữ slot cũng trả slot đúng một lần.

**Giới hạn của ví dụ:** Đây là proxy trong cùng một JVM, chỉ bảo vệ những lời gọi **thực sự đi qua proxy**. Token bucket dùng `double` và một khóa `synchronized`, phù hợp để giải thích thuật toán nhưng có thể gây contention khi số thread lớn. `Semaphore` là giới hạn trong process; khi chạy nhiều instance mỗi instance sẽ có quota riêng. Chương trình minh họa phương thức **đồng bộ**; với future/stream, trả permit khi `method.invoke` kết thúc có thể quá sớm. Đây là các vấn đề cần xử lý trong hệ thống thật.

## 8. Lời gọi đồng bộ, bất đồng bộ và vòng đời permit

### Đồng bộ

Với lời gọi đồng bộ, `try/finally` là cách phổ biến và đáng tin:

~~~java
boolean acquired = semaphore.tryAcquire();
if (!acquired) {
    throw new CapacityExceeded();
}
try {
    return invokeOriginal();
} finally {
    semaphore.release();
}
~~~

Đây là **mã ý tưởng**, trong đó `semaphore` và `invokeOriginal()` là các thành phần do ứng dụng cung cấp. `finally` thực thi khi phương thức hoàn tất bình thường hoặc ném exception. Nếu sử dụng chờ permit có thể bị ngắt, giữ nguyên hoặc xử lý đúng trạng thái interruption theo hợp đồng của ứng dụng.

### CompletionStage hoặc future

Một phương thức có thể trả `CompletionStage<Result>` gần như lập tức trong khi tác vụ bên trong tiếp tục nhiều giây. Nếu lời khuyên thực thi trả permit ngay sau khi trả stage, hệ thống có thể nhận **vô hạn tác vụ đang chờ I/O**, dù semaphore chỉ có vài slot. Vì thế để giới hạn số **logical in-flight operations**, phải giữ slot đến completion thành công, exceptional completion hoặc cancellation theo semantics đã chọn.

Thiết kế khái niệm:

1. Lấy permit trước khi gọi phương thức gốc; nếu không lấy được, từ chối.
2. Gọi phương thức gốc đúng một lần và nhận stage.
3. Nếu lệnh gọi đồng bộ ném exception trước khi trả stage, release ngay rồi ném lại.
4. Gắn callback completion chỉ release một lần; nếu callback quan sát bị chạy lại hoặc có đường cleanup khác, dùng một bộ bảo vệ at-most-once.
5. Bảo đảm cancellation của wrapper (nếu có) truyền đúng theo hợp đồng và không trả permit trước thời điểm được coi là terminal thực tế.
6. Hạn chế callback nặng trên thread hoàn tất; phân biệt deadline đã hết với tác vụ gốc **đã thật sự dừng**.

Ví dụ ở mức khái niệm, **không phải chương trình độc lập**:

~~~java
if (!semaphore.tryAcquire()) {
    throw new CapacityExceeded();
}

CompletionStage<Result> stage;
try {
    stage = invokeOriginalReturningStage();
} catch (Throwable original) {
    semaphore.release();
    throw original;
}

if (stage == null) {
    semaphore.release();
    throw new IllegalStateException("Unexpected null stage");
}

stage.whenComplete((result, error) -> semaphore.release());
return stage;
~~~

Ở đây `semaphore`, `invokeOriginalReturningStage()`, `Result` và exception là các thành phần giả định. Code chỉ minh họa **giữ permit đến completion của stage**. Trong triển khai đầy đủ cần xử lý lỗi khi đăng ký callback, chính sách null stage, làm rõ cancellation/timeout và bảo đảm release đúng một lần khi có nhiều đường terminal. Đặc biệt, việc gọi `cancel(true)` trên một `CompletableFuture` không tự bảo đảm tác vụ nền bị dừng; không thể tuyên bố đã giải phóng năng lực xử lý gốc chỉ vì future đại diện chuyển sang trạng thái canceled.

### Stream hoặc Publisher

Một publisher có thể được đăng ký nhiều lần, phát nhiều phần tử, hủy giữa chừng hoặc kéo dài vô hạn. Giới hạn **số subscription đang hoạt động** khác giới hạn **số lần gọi phương thức tạo publisher**. Muốn giữ slot trong vòng đời subscription, aspect hoặc decorator phải bám vào `subscribe`, các tín hiệu terminal `onComplete`/`onError` và `cancel` theo định nghĩa kết thúc; cần bảo toàn `request(n)` và backpressure. Không tự `subscribe` thêm chỉ để đo hoặc chiếm slot.

Nếu một subscription không kết thúc và không hủy, slot có thể được giữ vô hạn. Vì thế phải định nghĩa thời hạn, chính sách idle timeout và cleanup; timeout quan sát không tự chứng minh nguồn đã dừng gửi dữ liệu.

## 9. Chính sách từ chối, chờ và retry

### Từ chối nhanh

Khi hết token hoặc permit, hệ thống có thể ném exception có kiểu riêng, trả kết quả domain biểu thị “busy”, hoặc ánh xạ sang mã phản hồi giao thức tại biên hệ thống. Cần phân biệt **từ chối trước khi nghiệp vụ bắt đầu** với **lỗi trong lúc nghiệp vụ chạy**.

Ở hệ thống nhận request qua HTTP, rate-limit rejection thường được biểu diễn bằng `429 Too Many Requests`; thiếu tài nguyên tạm thời có thể cần chính sách phản hồi riêng tùy thiết kế API, chẳng hạn `503 Service Unavailable`. Mã cụ thể phải nhất quán với hợp đồng. `Retry-After` chỉ nên đưa ra khi có thời gian chờ ước tính hợp lý; không bịa mốc hồi phục nếu điều kiện phụ thuộc tải không thể đoán.

### Chờ có giới hạn

Token bucket có thể tính thời gian còn thiếu token; semaphore có thể dùng `tryAcquire(timeout, unit)`. Đợi chỉ hợp lý khi:

- Request còn deadline đủ dài và client chưa hủy.
- Queue/nhóm thread chờ được **giới hạn**.
- Có cơ chế loại yêu cầu khỏi queue khi quá hạn hoặc khi client hủy.
- Thời gian chờ và thứ tự ưu tiên đã được định nghĩa.
- Trạng thái đầy hệ thống không tạo vòng lặp retry nội bộ làm mọi request chờ lâu hơn.

Không nên dùng một queue vô hạn như cách “làm mượt” rate limit: nó làm tăng latency, chiếm memory và che khuất overload.

### Retry không kiểm soát tạo bão lưu lượng

Nếu caller nhận từ chối rồi retry ngay, hệ thống có thể nhận **nhiều attempt hơn** so với original request. Client nên cân nhắc exponential backoff, jitter, deadline và giới hạn số lần thử khi nghiệp vụ cho phép. Server phải xác định retry tiêu thụ token mới hay được tính chung vào request logic theo quota; cả hai có hệ quả công bằng và capacity.

Việc advice âm thầm retry thao tác gốc có thể gây side effect lặp (ghi dữ liệu, thanh toán, gửi thông báo). Một advice chỉ dùng để giới hạn tải thường **không nên tự gọi lại** nghiệp vụ để “đợi slot”.

## 10. Phạm vi quota: toàn cục, theo nhóm hay theo khách hàng?

Mỗi limiter cần một **khóa nhận diện chính sách** (key). Ví dụ:

| Khóa | Mục tiêu | Vấn đề cần kiểm tra |
| --- | --- | --- |
| Toàn ứng dụng | Bảo vệ CPU/memory của một instance | Nhiều instance có thể vượt quota tổng |
| Per-operation | Bảo vệ một phương thức hoặc loại tài nguyên | Điểm cắt lồng nhau, nhiều overload |
| Per-client/API key | Công bằng quota giữa khách hàng | Xác thực danh tính, nhiều key, churn |
| Per-tenant | Ngăn một tenant chiếm hết tài nguyên | Quota chung cho tài khoản, bucket lớn |
| Per-external dependency | Bảo vệ tài nguyên downstream | Nhiều operation chia sẻ một quota thực |
| Theo nhóm ưu tiên | Dành capacity cho request quan trọng | Starvation, thay đổi ưu tiên động |

Không được dùng chỉ một IP như định danh đáng tin duy nhất nếu môi trường có proxy/NAT hoặc header giả mạo. Lấy danh tính từ nguồn xác thực, chuẩn hóa key và hạn chế số bucket được tạo. Một bucket mới cho mỗi user ID không bao giờ dọn có thể gây rò rỉ bộ nhớ; cân nhắc TTL, giới hạn kích thước map, chính sách thu hồi và đồng bộ hóa khi tạo bucket.

Một hạn ngạch **theo tenant** không đồng nghĩa với limit riêng trên từng instance. Nếu có 5 instance và mỗi instance cho 100 requests/s, tổng hệ thống có thể nhận gần 500 requests/s khi traffic phân bố đều, chứ không tự thành quota toàn cục 100 requests/s.

## 11. Nhiều instance và điều phối phân tán

Để thực sự áp dụng hạn mức chung trên nhiều JVM/process, trạng thái quota cần phối hợp ở nơi dùng chung hoặc sử dụng cơ chế phân bổ ngân sách có kiểm soát. Những lựa chọn thường gặp:

- **Bộ đếm/token bucket ở kho dữ liệu có thao tác nguyên tử:** thao tác kiểm tra và trừ quota phải atomic; mô hình read→if→write tách rời có thể tạo race, vượt hạn mức.
- **Cấp ngân sách cho từng instance:** giảm độ trễ truy cập coordinator nhưng có nguy cơ mất cân bằng, thừa hạn ngạch ở máy ít tải hoặc overshoot khi phân phối lại.
- **Gateway/admission layer:** giới hạn ở biên trước khi vào ứng dụng; vẫn có thể cần thêm giới hạn concurrency cục bộ để bảo vệ pool nội bộ.
- **Semaphore phân tán hoặc lease:** cần cơ chế gia hạn, thời hạn, xử lý process chết và tránh một permit bị sở hữu đồng thời khi lease hết hạn.

### Vấn đề clock và độ chính xác

Token bucket hoặc sliding window phân tán phải xác định clock nguồn; việc mỗi instance dùng `System.nanoTime()` riêng không cho ra mốc có thể so sánh giữa các process. Coordinator có thể giữ thời gian thống nhất hoặc dùng thuật toán có quy định về skew. Tính công bằng và giới hạn cứng toàn cục thường đánh đổi lấy latency, availability và chi phí giao tiếp mạng.

### Coordinator lỗi thì sao?

Một aspect có thể quyết định:

- **Fail-closed:** khi không lấy được quyết định quota thì từ chối, tránh vượt hạn mức nhưng có thể làm gián đoạn dịch vụ.
- **Fail-open có giám sát:** cho phép một lượng request nhất định đi qua khi coordinator lỗi, ưu tiên tính sẵn sàng nhưng chấp nhận rủi ro vượt quota.
- **Local fallback có ngân sách nhỏ:** giới hạn tạm thời từng instance, có thể chặn bớt mức overshoot nhưng phải định nghĩa tính nhất quán khi coordinator phục hồi.

Không có lựa chọn chung cho mọi hệ thống. Chẳng hạn quota theo hợp đồng thương mại, bảo vệ tài nguyên hữu hạn hoặc giới hạn truy cập chống lạm dụng có thể yêu cầu mức bảo đảm khác nhau. Ghi lại chính sách khi coordinator gián đoạn, timeout kết nối, retry, và cách xử lý event trễ.

## 12. Quản lý lock, contention và hiệu suất của advice

Chính sách giới hạn là một điểm nóng tiềm năng: mọi lời gọi cùng khóa sẽ cạnh tranh trên trạng thái quota.

- `synchronized` quanh một token bucket bảo đảm cập nhật nhất quán trong cùng JVM nhưng có thể thành bottleneck khi hàng nghìn thread dùng chung bucket.
- Cơ chế CAS/atomic có thể giảm một số contention, nhưng cần thiết kế đúng với thao tác gồm đọc thời gian, refill và trừ token; không thể chỉ thay counter bằng atomic rồi bỏ qua race.
- `Semaphore` điều phối permit nhưng có chi phí cạnh tranh; fairness có thể thay đổi thông lượng.
- Tạo quá nhiều per-key limiter làm tăng bộ nhớ; tạo quá ít limiter khiến các nhóm độc lập tranh chung lock và quota.
- Ghi log đồng bộ trên đường từ chối có thể trở thành điểm nghẽn đặc biệt khi đang bị spam.
- Thời gian chờ vào lock/queue và thời gian nghiệp vụ nên được đo riêng, để biết chính limiter có làm tăng latency không.

Ước lượng đơn giản: nếu advice mất trung bình 3 µs CPU cho mỗi lần gọi và có 100.000 lần gọi/giây, tổng thời gian xử lý bổ sung cỡ 0,3 CPU-second mỗi giây, **chưa tính** cache contention, GC, log, điều phối thread hoặc ảnh hưởng khi tải bão hòa. Đây là phép nhân minh họa, không phải một benchmark.

Nên đo hiệu ứng A/B ở các mức tải, gồm p95/p99 latency, achieved throughput, CPU, allocation/GC, số lần timeout, queue length và rate rejection. Tối ưu limiter mà làm sai tính chính xác của quota không phải cải tiến.

## 13. Quan sát và đánh giá chính sách

Một tập metrics hữu ích:

| Metric | Ý nghĩa |
| --- | --- |
| `admission_attempts_total` | Số lần qua advice tại điểm giới hạn |
| `admission_allowed_total` | Số lần được cấp phép và bắt đầu |
| `admission_rejected_total{reason="rate"}` | Số lần từ chối do hết token |
| `admission_rejected_total{reason="concurrency"}` | Số lần từ chối do hết slot |
| `admission_wait_seconds` | Phân phối thời gian chờ nếu hỗ trợ đợi |
| `admission_in_flight` | Số tác vụ hiện đang giữ permit |
| `admission_duration_seconds` | Thời lượng thực thi theo vòng đời đã định nghĩa |
| `admission_limiter_failures_total` | Lỗi trong chính cơ chế kiểm soát (coordinator, registry...) |
| `admission_active_keys` | Số bucket/key đang được lưu |
| `admission_queue_depth` | Số yêu cầu chờ trong queue có giới hạn |

Từ chối trước nghiệp vụ và lỗi nghiệp vụ nên có nhãn outcome **khác nhau**. Ví dụ tỷ lệ từ chối do quota bằng `rejected / attempts` trong một khoảng; tỷ lệ thất bại nghiệp vụ có thể lấy `business_failed / admitted_and_completed`. Không đưa rejection vào mẫu số lỗi nghiệp vụ mà không công bố rõ.

Một phản hồi quá tải có thể tăng khi cấu hình limiter phát huy tác dụng, trong khi ứng dụng vẫn khỏe; ngược lại, `admission_rejected_total = 0` không chứng minh hệ thống có đủ capacity nếu một đường gọi bypass pointcut.

Giảm cardinality: tránh gắn request ID, user ID tự do, token bí mật hoặc stack trace riêng vào từng metric series. Với tenant nhiều, cân nhắc tổng hợp theo cấp quota hoặc dùng log/trace phân quyền để drill down. Hạn chế log mọi rejection trong một đợt tấn công; metrics đếm tổng hợp thường an toàn tài nguyên hơn.

## 14. Kiểm thử đúng chức năng và góc cạnh

### Kiểm thử rate limiter

1. **Bucket vừa khởi tạo:** nhận đúng số token bằng capacity, từ chối yêu cầu kế tiếp khi chưa refill.
2. **Refill có kiểm soát:** dùng đồng hồ giả; sau 0,5 giây ở mức 2 token/s có thêm một token.
3. **Capacity:** sau khoảng idle dài không nạp vượt dung lượng xô.
4. **Boundary:** test đúng mốc cửa sổ, token phân số, độ trễ rất nhỏ, đồng hồ không tiến hoặc bị lệch theo mô hình triển khai.
5. **Concurrency trong một bucket:** nhiều thread chạy `tryConsume` đồng thời không được tiêu cùng một token hai lần.
6. **Per-key isolation:** tài khoản A hết quota không tự làm cạn quota B nếu chính sách tách biệt.
7. **Retry:** việc retry tạo attempt mới được tính theo đúng chính sách, không vô tình được miễn quota.

### Kiểm thử concurrency limiter

1. Giữ tối đa N lời gọi chưa hoàn tất, thử lời gọi thứ N+1 và xác nhận từ chối/đợi đúng cấu hình.
2. Khi một lời gọi hoàn tất, lời gọi mới có thể nhận permit.
3. Khi nghiệp vụ ném exception, permit được trả và lỗi gốc được chuyển tiếp.
4. Khi chính sách rate từ chối sau khi đã acquire slot, slot được trả ngay.
5. Khi có timeout, interruption, cancellation, bảo đảm cleanup phù hợp vòng đời và không release hai lần.
6. Với nhiều thread, không vượt `maxConcurrent` ở bất kỳ thời điểm quan sát hợp lệ; không phụ thuộc thời gian `sleep` để tạo trùng lặp khó tái hiện.
7. Với reentrant call hoặc điểm cắt lồng nhau, kiểm tra tránh tự deadlock và đếm kép; xác định có cho phép lệnh gọi nội bộ dùng chung permit hay không.

### Kiểm thử AOP và tích hợp

- Kiểm tra điểm cắt thật sự đi qua proxy/weaving và không có đường bypass khi gọi trực tiếp.
- Mỗi thao tác gốc được thực thi đúng một lần khi được nhận, không thực thi khi bị từ chối.
- Giá trị trả về, exception, side effect và context không bị biến đổi ngoài chính sách.
- Tác vụ async giữ slot tới terminal thật; không trả sớm khi trả future.
- Mọi queue có giới hạn, số thread chờ và độ trễ từ chối nằm trong ngân sách tài nguyên.
- Khi tăng instance, quota tổng có đúng như thiết kế không; test khi coordinator chậm hoặc mất kết nối.
- Khi áp lực tải lớn, liệu policy có tạo starvation, retry storm hoặc lỗi thứ cấp từ logging.

### Kiểm thử tải

Chạy các kịch bản sau với offered load và thời gian chạy rõ ràng: thấp hơn ngưỡng rate, gần ngưỡng rate, burst vượt capacity, tác vụ chậm khiến semaphore đầy, request bị cancel, lỗi nghiệp vụ, coordinator lỗi. Đo cả **offered rate** và **completed rate**, vì nếu bên phát tải bị chặn, hệ thống có thể trông khỏe dù chưa được thử đến đúng ngưỡng.

Ngoài throughput, theo dõi p95/p99, thời gian chờ, tỉ lệ accepted/rejected, số connection/thread, CPU, heap, queue và độ trễ downstream. Đặt mục tiêu trước khi thử: chẳng hạn không quá ba tác vụ sử dụng một resource có capacity ba, queue không tăng vô hạn, reject có phân loại rõ và request quan trọng không bị starvation.

## 15. Những sai lầm thường gặp

**“Rate limit 100/s nghĩa là luôn có tối đa 100 request cùng lúc.”** Sai. Một burst hoặc thời lượng chạy kéo dài có thể khiến số in-flight lớn hơn nhiều; cần concurrency limiter riêng.

**“Semaphore 10 slot nghĩa là không quá 10 request mỗi giây.”** Sai. Nếu mỗi request xong trong 1 ms, 10 slot có thể xử lý nhiều đợt trong một giây; không có trần tốc độ bắt đầu.

**“Dùng `finally` là đủ cho mọi phương thức async.”** Không chính xác. `finally` kết thúc khi phương thức trả future/publisher, có thể trước khi công việc thực sự xong.

**“Mỗi instance có 100 tokens/s tức toàn hệ thống có 100 tokens/s.”** Sai nếu có nhiều instance độc lập; phải thiết kế quota phối hợp hoặc ngân sách phân bổ.

**“Cho mọi request chờ đến khi được chạy sẽ tránh từ chối.”** Có thể gây backlog, latency vượt deadline và OOM. Chờ phải có giới hạn và có chính sách hủy.

**“Từ chối càng ít càng tốt.”** Nếu tất cả request đều được nhận đến khi tài nguyên sụp đổ, hệ thống không còn thực hiện được mục tiêu bảo vệ. Admission control chủ động có thể duy trì chất lượng phục vụ cho những request đã nhận.

**“Tắt logging là policy đủ nhanh.”** Chưa chắc: lock, registry, phân phối key, quota coordinator, serialization hoặc proxy dispatch có thể vẫn là nút nghẽn.

**“Dùng pointcut rất rộng sẽ tự bảo vệ toàn chương trình.”** Có thể đếm kép, tự khóa khi reentrant hoặc áp quota cho code infrastructure. Cần chọn biên nghiệp vụ phù hợp và kiểm tra đường gọi thật.

**“Từ chối do thiếu token cũng là lỗi xử lý nghiệp vụ.”** Từ chối admission xảy ra trước khi chạy nghiệp vụ; cần quan sát nó riêng với exception sinh trong nghiệp vụ.

## 16. Quy trình triển khai thực tế

1. **Xác định tài nguyên cần bảo vệ:** CPU, connection, hạn ngạch remote API, tenant SLA, queue hoặc một operation cụ thể.
2. **Viết hợp đồng:** giới hạn bắt đầu theo thời gian hay số in-flight; có burst không; ai dùng chung quota; lỗi/hủy được tính thế nào.
3. **Chọn thuật toán:** window, token bucket, scheduler hoặc semaphore dựa trên workload và yêu cầu về độ chính xác/công bằng.
4. **Chọn pointcut:** xác minh những lời gọi thực sự chịu policy; tránh đếm kép, bypass và recursion.
5. **Chọn cách từ chối/chờ:** fail-fast hoặc hàng đợi có giới hạn; định nghĩa lỗi/timeout/retry cho caller.
6. **Thiết kế vòng đời slot:** phương thức đồng bộ dùng `finally`; tác vụ async theo terminal; stream theo subscription; xử lý cancellation.
7. **Thiết kế trạng thái quota:** per-instance, per-key hoặc phối hợp phân tán, xử lý đồng hồ và race.
8. **Đo & kiểm thử:** các tình huống biên, parallel calls, load burst, resource slow, timeout, collector/coordinator lỗi, chi phí của advice.
9. **Triển khai theo giai đoạn:** bắt đầu với mức bảo vệ bảo thủ, theo dõi rejected/accepted, latency và mức dùng tài nguyên; hiệu chỉnh từ kết quả thực tế và yêu cầu dịch vụ.

## 17. Kết luận

Giới hạn tốc độ và giới hạn số lời gọi đồng thời giải quyết hai khía cạnh quá tải khác nhau. **Rate limiter** quản lý ngân sách bắt đầu công việc theo thời gian; **concurrency limiter** quản lý số công việc còn đang diễn ra. AOP có thể thực thi cả hai tại một biên chung bằng around advice, miễn là chính sách được áp dụng đúng điểm cắt, gọi nghiệp vụ đúng một lần, giữ nguyên kết quả/lỗi và quản lý token/permit theo vòng đời đã chọn. Với tác vụ bất đồng bộ hoặc hệ nhiều instance, định nghĩa vòng đời và trạng thái quota quan trọng hơn câu lệnh `tryAcquire()` đơn lẻ. Một thiết kế an toàn phải có giới hạn hàng đợi, chính sách lỗi, khả năng quan sát và kiểm thử tải để chứng minh hệ thống được bảo vệ thực sự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-008">Không dùng aspect để che giấu thiếu sót trong hợp đồng nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng Aspect để che giấu thiếu sót trong hợp đồng nghiệp vụ

## 1. Vấn đề cốt lõi

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming — **AOP**), một **Aspect** đóng gói hành vi xuất hiện ở nhiều vị trí thực thi: đo thời gian, ghi nhận sự kiện, quản lý ngữ cảnh truy vết, giới hạn tài nguyên hoặc áp dụng chính sách kỹ thuật đã được định nghĩa. **Advice** được chạy tại các **join point** do **pointcut** lựa chọn; tùy cơ chế AOP, advice có thể thực thi trước, sau hoặc bao quanh lời gọi/phần thực thi được chọn.

Khả năng chèn hành vi mà không sửa thân phương thức rất hữu ích, nhưng cũng nguy hiểm: chỉ một Aspect có thể thay đổi tham số, bỏ qua một lần gọi, nuốt exception, sửa giá trị trả về, tự retry hoặc làm phát sinh side effect. Nếu các thay đổi này ngầm “vá” một API không rõ nghĩa hay một mô hình nghiệp vụ thiếu điều kiện, người đọc mã nghiệp vụ sẽ không thấy hợp đồng thực sự đang được thực thi.

**Nguyên tắc:** hợp đồng nghiệp vụ phải tồn tại và được hiểu một cách tường minh ở nơi định nghĩa/người sử dụng API, dù việc kiểm tra một số điều kiện có được tái sử dụng qua cơ chế AOP hay không. Aspect không nên biến một thao tác sai theo hợp đồng thành một thao tác có vẻ thành công, hoặc bổ sung một nghĩa nghiệp vụ mới mà caller không hề biết.

Ví dụ, nếu chuyển tiền yêu cầu `amount > 0` và đủ số dư, một advice âm thầm biến `amount = -100` thành `100` không phải “xử lý kỹ thuật”; đó là đổi ý nghĩa lệnh chuyển tiền. Nếu một Aspect bắt lỗi `InsufficientFunds` rồi trả “thành công” mặc định, dữ liệu báo cáo và hành vi của khách hàng sẽ không còn khớp với số dư thực tế.

Bài này trình bày cách nhận diện ranh giới ấy trong **AOP nói chung**, cách thiết kế hợp đồng, cách phân loại chính sách thực thi, ví dụ Java 17+ chạy được và chiến lược kiểm thử đối với advice.

## 2. Hợp đồng nghiệp vụ là gì?

Hợp đồng mô tả điều bên gọi **được phép yêu cầu**, điều bên cung cấp **cam kết thực hiện**, và điều gì xảy ra khi yêu cầu không hợp lệ hoặc không thể hoàn thành. Nó bao gồm cả các quy tắc không được ghi bằng ngôn ngữ lập trình cụ thể.

### 2.1. Các lớp của hợp đồng

| Thành phần | Câu hỏi cần trả lời | Ví dụ trong chuyển tiền |
| --- | --- | --- |
| **Tiền điều kiện (preconditions)** | Lệnh đầu vào nào hợp lệ? | Số tiền dương, tài khoản nguồn/đích hợp lệ, không trùng nhau |
| **Bất biến (invariants)** | Trạng thái nào luôn phải được duy trì? | Không tạo số dư âm nếu sản phẩm cấm thấu chi; bảo toàn giá trị khi chuyển nội bộ |
| **Hậu điều kiện (postconditions)** | Sau thành công, điều gì phải đúng? | Ghi nợ nguồn và ghi có đích đúng số tiền |
| **Semantics của lỗi** | Thao tác thất bại được báo thế nào? | Lệnh không hợp lệ ném lỗi; thiếu tiền không chuyển phần còn lại |
| **Tính nguyên tử và side effect** | Khi lỗi ở giữa, phần việc nào được giữ? | Không ghi nợ nguồn nếu ghi có đích chưa thể thực hiện theo hợp đồng nguyên tử |
| **Idempotency** | Gửi lại cùng một lệnh có được tính lại không? | Cùng request key, cùng nội dung được trả lại kết quả cũ theo quy tắc đã công bố |
| **Quyền hạn** | Ai được thực hiện thao tác nào? | Chỉ chủ tài khoản hoặc chủ thể được ủy quyền |
| **Thời gian và đồng thời** | Timeout, thứ tự và cạnh tranh được xử lý ra sao? | Timeout không tự khẳng định giao dịch đã bị rollback |
| **Khả năng quan sát** | Caller nhìn thấy trạng thái nào? | Success, rejected, pending, unknown, duplicate |

Một phương thức có thể có hợp đồng bằng kiểu dữ liệu, tài liệu API, tên hành động, exception hoặc kết quả có cấu trúc. Hợp đồng không nhất thiết phải là một file tài liệu riêng, nhưng phải **có thể tìm thấy, kiểm chứng và hiểu** bởi người gọi lẫn người bảo trì.

### 2.2. “Chạy đúng” không có nghĩa “đúng hợp đồng”

Giả sử lệnh `reserve(item, quantity)` được gọi với `quantity = 0`. Một Aspect tự đổi thành `1` có thể làm thao tác “chạy được” và không ném lỗi, nhưng người gọi vừa bị đặt trước một hành vi khác với yêu cầu. Tương tự, nếu API hứa ném exception khi thiếu quyền, advice trả giá trị rỗng khiến lỗi quyền truy cập bị diễn giải thành “không có dữ liệu”.

Khi Aspect làm thay đổi dữ liệu quan trọng, sự thành công bề ngoài có thể che giấu:

- Thiết kế kiểu dữ liệu thiếu miền giá trị hợp lệ.
- Thiếu xác thực ở đầu vào thực tế (kể cả lời gọi nội bộ không qua advice).
- Sai bất biến ở tầng domain/service.
- Quy tắc idempotency không được khai báo.
- Lỗi side effect, transaction boundary, consistency hoặc timeout chưa có hợp đồng.
- Exception bị dịch sai từ “không được phép” sang “không tìm thấy” hoặc từ “chưa biết kết quả” sang “thất bại chắc chắn”.

Aspect không phải nơi thay thế việc định nghĩa các điểm trên.

## 3. Phân công trách nhiệm: domain, API và Aspect

Một cách kiểm tra thực tế là hỏi: **Nếu tắt cơ chế weaving hoặc gọi nghiệp vụ bằng một đường không đi qua Aspect, quy tắc đó có còn đúng không?** Với bất biến cốt lõi của domain, câu trả lời cần là **có**. Một Aspect có thể áp dụng thêm lớp kiểm tra ở ranh giới nhưng hệ thống không được mất tính đúng đắn của domain chỉ vì advice không chạy.

### 3.1. Quy tắc nên nằm ở domain/API

Các ví dụ điển hình:

- Tài khoản phải có đủ tiền trước khi ghi nợ.
- Không được đặt hàng với số lượng bằng 0 nếu hợp đồng yêu cầu số lượng dương.
- Trạng thái đơn hàng không được chuyển trực tiếp từ `CANCELLED` sang `SHIPPED`.
- Phép tính giá/thuế/chiết khấu phải đúng theo quy tắc nghiệp vụ.
- Yêu cầu tạo thanh toán cần khóa idempotency theo hợp đồng, không chỉ “đoán retry rồi tránh ghi trùng”.
- Đường chuyển đổi giữa thành công, từ chối và kết quả chưa rõ phải được mô tả cụ thể.

Những quy tắc này tác động đến **ý nghĩa** của hành động và dữ liệu; nên xuất hiện qua domain model, application service, API contract và bài kiểm thử tương ứng.

### 3.2. Mối quan tâm cắt ngang phù hợp với Aspect

Các ví dụ thường phù hợp hơn:

- Đo elapsed time, tăng counter, gắn trace/correlation context, ghi audit/diagnostic event theo chính sách đã công bố.
- Chuẩn hóa một cấu trúc telemetry không làm lộ tham số nhạy cảm.
- Kiểm soát kỹ thuật như rate limiting hoặc concurrency cap **khi contract đã nói rõ** lệnh có thể bị từ chối/quá tải.
- Quản lý tracing context, deadline hoặc cơ chế dọn dẹp tài nguyên thuộc lớp điều phối.
- Chuyển exception kỹ thuật thành một loại lỗi **đã nằm trong API contract** ở đúng ranh giới, giữ nguyên nguyên nhân và trạng thái nghiệp vụ.
- Thực thi chính sách kiểm tra truy cập **được đặc tả rõ** và được bao phủ bởi các đường gọi tin cậy.

Danh sách này không biến các chính sách thành “thuần kỹ thuật” trong mọi hệ thống. **Quyền truy cập, quota, deadline và transaction** đôi khi là một phần hợp đồng nghiệp vụ. Khi đó, hành vi nhìn thấy bởi caller phải được tài liệu hóa và kiểm thử như hợp đồng công khai, dù chi tiết thực thi có thể được tái sử dụng qua Aspect.

### 3.3. Bảng quyết định nhanh

| Nếu một advice đang… | Nguy cơ | Thiết kế tốt hơn |
| --- | --- | --- |
| Biến số tiền âm thành số dương | Đổi ngữ nghĩa lệnh | Kiểu `Money`/validation trong domain; trả lỗi rõ |
| Nuốt lỗi và trả đối tượng “OK” giả | Che lỗi và tạo thông tin sai | Giữ exception/kết quả lỗi theo hợp đồng |
| Retry thao tác có side effect | Có thể thực thi hai lần | Quy tắc retry và idempotency được đặc tả, chỉ retry thao tác an toàn |
| Đổi lỗi “chưa biết kết quả” thành “chắc chắn thất bại” | Caller gửi lại gây tác dụng kép | Trạng thái `UNKNOWN/PENDING` và cơ chế reconciliation |
| Tự mở/đóng transaction quanh một phần thao tác | Hiểu sai atomicity | Định nghĩa transaction boundary từ use case và nguồn dữ liệu |
| Tự thêm default value thiếu trong API | Che tiền điều kiện thiếu | API định nghĩa default rõ hoặc yêu cầu caller cung cấp |
| Tự gắn nhãn log và metric | Thường ít ảnh hưởng nghiệp vụ | Giữ telemetry độc lập và quan sát được lỗi của chính nó |
| Từ chối theo quota đã công bố | Là chính sách có thể nhìn thấy | Trả lỗi quá tải đã định nghĩa; nêu đơn vị tính/quota/retry-after |
| Kiểm tra quyền theo rule đã công bố | Có thể hợp lệ nhưng nhạy cảm | Giữ bảo vệ ở mọi đường truy cập, kiểm thử bypass và fail-closed đúng chính sách |

Điểm quyết định không phải tên gọi “business” hay “technical”, mà là **hợp đồng có thay đổi hay không, ai sở hữu quy tắc, người gọi có thể dự đoán và kiểm thử nó hay không**.

## 4. Advice có thể làm sai hợp đồng bằng cách nào?

### 4.1. Thay đổi tham số mà caller không biết

Around advice thường có khả năng thay lời gọi bằng một bộ tham số khác. Điều này hữu ích cho các hành động kỹ thuật rõ nghĩa, nhưng nguy hiểm nếu thao tác biến dữ liệu nghiệp vụ. Ví dụ sau là **đoạn minh họa anti-pattern, không phải chương trình độc lập**:

~~~java
// SAI: advice tu "chua" lenh chuyen tien sai nghia.
long amount = (long) args[2];
args[2] = Math.abs(amount);
return proceed(args);
~~~

Lệnh chuyển tiền `-100` đáng ra bị từ chối đã biến thành lệnh chuyển `100`. Ngoài ra, `Math.abs(Long.MIN_VALUE)` vẫn cho ra số âm do overflow, nên ngay cả “chuẩn hóa” này cũng không toàn vẹn. Giải pháp là để domain từ chối giá trị không hợp lệ và giải thích lỗi trong hợp đồng.

Một việc như trim một identifier theo chuẩn chung cũng chỉ được phép nếu hợp đồng nói rõ identifier được normalize thế nào. Không phải phép biến đổi “nhỏ” nào cũng vô hại: khoảng trắng, chữ hoa/thường và Unicode normalization có thể ảnh hưởng định danh, chữ ký hoặc khóa truy cập.

### 4.2. Nuốt, dịch sai hoặc làm mất nguyên nhân lỗi

Around advice có thể bắt exception; điều này không cấp quyền trả success giả. Xét lệnh `pay()` ném lỗi do không đủ tiền. Nếu advice bắt tất cả lỗi rồi trả `PaymentResult.success()`, caller sẽ xác nhận đơn hàng đã thanh toán dù chưa có tiền chuyển.

Dịch exception kỹ thuật có thể hợp lý ở **ranh giới đã quy định**: ví dụ một lỗi I/O đặc thù được đóng gói thành lỗi `STORAGE_UNAVAILABLE` theo API. Khi dịch, cần giữ `cause`, phân biệt lỗi tạm thời với lỗi do caller và không nuốt tín hiệu `InterruptedException`. `catch (Throwable)` rồi bỏ qua có thể vô tình che lỗi JVM nghiêm trọng.

Nếu một lần gọi bị timeout, có thể chưa biết side effect có được thực hiện ở hệ thống khác hay không. Không tùy tiện gán kết quả “không xảy ra”; hợp đồng có thể cần trạng thái “chưa rõ” và truy vấn/reconcile.

### 4.3. Retry làm thay đổi số lần thực hiện

Một advice retry có thể biến một lần gọi API thành nhiều lần chạy domain. Với lệnh đọc bất biến và an toàn, retry có thể hợp lệ trong ngân sách đã công bố. Với lệnh ghi:

~~~text
Caller gọi charge(100) một lần
   -> Advice gọi charge(100) lần 1: hệ thống từ xa đã ghi tiền,
      nhưng phản hồi bị mất
   -> Advice tự retry lần 2: hệ thống từ xa ghi tiền thêm lần nữa
   -> Caller nhận “thành công” nhưng bị tính tiền hai lần
~~~

Retry phải dựa vào **semantics của operation**, loại lỗi, deadline và idempotency token ổn định, chứ không chỉ việc một exception xuất hiện. Việc retry có thể nằm trong Aspect nhưng **chính sách retry, tính idempotent và tình huống kết quả chưa rõ phải có trong hợp đồng API**.

### 4.4. Cache che giấu trạng thái mới

Một Aspect cache không khai báo có thể trả dữ liệu stale cho nghiệp vụ đòi consistency mạnh, nhất là khi cập nhật quyền, tồn kho hoặc trạng thái giao dịch. Nếu domain cho phép eventual consistency hoặc stale-while-revalidate, hãy đưa tính chất đó vào hợp đồng: cache key, TTL, invalidation, chủ sở hữu và các lệnh buộc bypass.

Không giả định tất cả phương thức có cùng tên “get” đều có tính pure hoặc có thể cache. Các method getter đôi khi có side effect, context-dependent authorization hoặc dữ liệu theo thời gian.

### 4.5. Transaction và các ranh giới không cùng nhau

Advice bắt đầu và kết thúc một transaction kỹ thuật không tự giải quyết nguyên tử nghiệp vụ khi có nhiều nguồn dữ liệu, hàng đợi thông điệp hoặc dịch vụ từ xa. Nếu use case đòi “ghi đơn hàng và xuất sự kiện” có tính nhất quán, cần thiết kế outbox, compensation hoặc mô hình nhất quán tương ứng. Aspect bọc lời gọi không làm cho các side effect phân tán trở thành một transaction nguyên tử.

Trong lời gọi bất đồng bộ, transaction/resource context của luồng gọi có thể kết thúc trước khi worker hoàn tất. Cần định nghĩa rõ đơn vị công việc, luồng sở hữu tài nguyên và thời điểm kết thúc; không dùng around advice đồng bộ như lời hứa tự động “tất cả đều nằm trong một transaction”.

## 5. Ví dụ Java 17+: bảo toàn hợp đồng khi thêm Aspect quan sát

Ví dụ mô phỏng chuyển tiền đơn giản bằng số nguyên `long` biểu diễn **đơn vị nhỏ nhất của tiền**. Hợp đồng của `TransferService.transfer()`:

1. `amountCents` phải dương.
2. Hai tài khoản phải tồn tại, khác nhau và nguồn phải đủ số dư.
3. Nếu dữ liệu đầu vào sai, thao tác ném `IllegalArgumentException` và không sửa số dư.
4. Nếu không đủ tiền, thao tác ném `IllegalStateException` và không sửa số dư.
5. Thành công ghi nợ/ghi có đúng số tiền và trả một `Receipt`.
6. Aspect chỉ đo số lời gọi và số lỗi; không sửa input, không retry, không đổi kết quả hoặc exception.

Ví dụ chạy tuần tự để tập trung vào hợp đồng; một ledger dùng chung nhiều luồng cần bổ sung đồng bộ/transaction thích hợp. Tạo file `BusinessContractAspectDemo.java`:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class BusinessContractAspectDemo {
    interface TransferService {
        Receipt transfer(String from, String to, long amountCents);
    }

    record Receipt(String from, String to, long amountCents) { }

    static final class Ledger implements TransferService {
        private final Map<String, Long> balances = new HashMap<>();

        Ledger() {
            balances.put("A", 1_000L);
            balances.put("B", 100L);
        }

        @Override
        public Receipt transfer(String from, String to, long amountCents) {
            if (amountCents <= 0 || from == null || to == null
                    || from.equals(to) || !balances.containsKey(from)
                    || !balances.containsKey(to)) {
                throw new IllegalArgumentException("invalid transfer");
            }

            long available = balances.get(from);
            if (available < amountCents) {
                throw new IllegalStateException("insufficient funds");
            }

            // Tính và kiểm tra cả hai kết quả TRƯỚC khi thay đổi state:
            // nếu Math.addExact ném vì overflow, số dư vẫn nguyên.
            long newFrom = available - amountCents;
            long newTo = Math.addExact(balances.get(to), amountCents);
            balances.put(from, newFrom);
            balances.put(to, newTo);
            return new Receipt(from, to, amountCents);
        }

        long balance(String account) {
            return balances.get(account);
        }
    }

    static final class ObserveAdvice implements InvocationHandler {
        private final TransferService target;
        private final AtomicLong invocations = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();

        ObserveAdvice(TransferService target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            invocations.incrementAndGet();
            boolean completed = false;
            try {
                Object result = method.invoke(target, args);
                completed = true;
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } finally {
                if (!completed) {
                    failures.incrementAndGet();
                }
            }
        }

        String counters() {
            return "calls=" + invocations.get()
                    + ", failures=" + failures.get();
        }
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        ObserveAdvice advice = new ObserveAdvice(ledger);
        TransferService observed = (TransferService) Proxy.newProxyInstance(
                TransferService.class.getClassLoader(),
                new Class<?>[] { TransferService.class },
                advice);

        try {
            observed.transfer("A", "B", -50);
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        System.out.println("after rejected: A=" + ledger.balance("A")
                + ", B=" + ledger.balance("B"));

        Receipt result = observed.transfer("A", "B", 250);
        System.out.println("accepted: " + result);
        System.out.println("after accepted: A=" + ledger.balance("A")
                + ", B=" + ledger.balance("B"));
        System.out.println("observed: " + advice.counters());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac BusinessContractAspectDemo.java
java BusinessContractAspectDemo
~~~

Kết quả mong đợi (**stdout chính xác khi chạy tuần tự**):

~~~text
rejected: invalid transfer
after rejected: A=1000, B=100
accepted: Receipt[from=A, to=B, amountCents=250]
after accepted: A=750, B=350
observed: calls=2, failures=1
~~~

Ý nghĩa của ví dụ:

- Từ chối lệnh âm là **quy tắc của `Ledger`**, không dựa vào proxy. Gọi trực tiếp `ledger.transfer(...)` vẫn có cùng hợp đồng.
- Aspect quan sát không biến `-50` thành `50`, không giấu `IllegalArgumentException`, không ghi “thành công” giả.
- Với JDK dynamic proxy, `InvocationTargetException` do reflection tạo ra được bóc để caller nhận **nguyên nhân gốc**. Việc bóc wrapper này bảo toàn semantics exception, khác với việc thay nguyên nhân nghiệp vụ.
- `invocations=2` và `failures=1` chỉ là telemetry; không ảnh hưởng số dư.
- Mã chỉ mô tả ledger in-memory cho mục đích học tập. Phép cộng được kiểm tra **trước cả hai lần cập nhật**, nên nếu phép cộng bị overflow, ví dụ tuần tự chưa sửa bất cứ số dư nào. Trong sản phẩm thực, vẫn cần định nghĩa lỗi số học trong hợp đồng, xử lý concurrency, durability, transaction và idempotency phù hợp.
- Proxy chặn **các lời gọi đi qua interface proxy**. Nó không tự động chặn mọi self-call trong đối tượng mục tiêu hay mọi đường gọi khác.

Một nguyên tắc hữu ích khi review code là so sánh hai lối gọi: `ledger.transfer(...)` trực tiếp và `observed.transfer(...)` qua Aspect. **Dữ liệu trả về, thay đổi trạng thái và exception theo hợp đồng phải tương đương**, ngoại trừ những tác dụng kỹ thuật được định nghĩa rõ như telemetry hoặc từ chối theo quota công khai.

## 6. Tách business validation và cross-cutting validation

Không phải cứ có “validation” là bắt buộc viết tay trong từng method, và cũng không phải mọi validation có thể đưa vào một Aspect chung. Cần phân biệt:

### 6.1. Validation mang nghĩa nghiệp vụ

Ví dụ:

- `amountCents > 0`.
- Trạng thái `PAID` chỉ được chuyển thành `REFUNDED` theo chính sách hoàn tiền.
- Sản phẩm thuộc một phân khúc khách hàng cụ thể mới đủ điều kiện đặt mua.
- Yêu cầu hoàn tiền không vượt phần tiền thực sự đã thanh toán.

Các quy tắc này thường phụ thuộc entity, trạng thái và use case. Chúng phải được thể hiện ở domain model hoặc application service, được kiểm thử độc lập và vẫn đúng trên các đường chạy không có weaving.

### 6.2. Kiểm tra tại ranh giới có thể dùng lại

Một kiểm tra về kích thước payload, định dạng headers hoặc bộ quy tắc ủy quyền chung có thể được áp dụng bởi interceptor/Aspect để tránh lặp mã. Tuy vậy:

- Phải có **đặc tả công khai** ở API hoặc policy mà caller có thể hiểu.
- Điểm chặn phải bao phủ **mọi đường truy cập yêu cầu bảo vệ**; bypass không được trở thành lỗ hổng.
- Một rule an toàn quan trọng không nên chỉ phụ thuộc vào một proxy dễ bị gọi vượt.
- Kết quả từ chối có mã lỗi rõ, không bị biến thành dữ liệu “rỗng” hay success giả.
- Các bất biến domain vẫn được kiểm tra tại nơi sở hữu dữ liệu khi cần thiết.

Nếu Aspect thực thi authorization, nó đang triển khai một hợp đồng có thể quan sát được; việc logic chạy qua advice không khiến yêu cầu phân quyền trở thành chi tiết ẩn. Trong những hệ thống có nhiều đường vào, cần phân lớp ranh giới kiểm tra và bảo vệ domain ở nơi thích hợp.

## 7. Cách viết hợp đồng trước khi gắn Aspect

Một quy trình ra quyết định hữu ích:

1. **Nêu use case và trạng thái:** lệnh làm gì, entity nào chịu tác động, invariant nào phải luôn đúng.
2. **Định nghĩa API rõ ràng:** kiểu tham số/giá trị trả về, tiền điều kiện, các loại lỗi, sự kiện phụ, chính sách retry, timeout và idempotency.
3. **Viết bài kiểm thử hợp đồng không cần Aspect:** đặc biệt với dữ liệu xấu, race condition và lỗi giữa chừng.
4. **Liệt kê mối quan tâm cắt ngang:** telemetry, tracing, quota chung, context propagation hoặc mapping lỗi kỹ thuật phù hợp.
5. **Thiết kế advice chỉ trong quyền hạn đã mô tả:** nêu rõ có được đổi args, chặn lời gọi, trả fallback hay retry không; mặc định không thực hiện những việc đó.
6. **Kiểm tra chênh lệch hành vi trước/sau khi weave:** input, output, exception, side effect, số lần thực thi và latency.
7. **Đảm bảo đường gọi ngoài advice không phá bất biến:** bao gồm internal calls, đường chạy test, tác vụ async hoặc lời gọi trực tiếp.
8. **Đưa chính sách vào tài liệu vận hành và observability:** người bảo trì biết vì sao một lời gọi bị từ chối/quá hạn và xác định được policy nào gây ra.

Một hợp đồng tốt có thể dùng cấu trúc sau:

~~~text
Operation: transfer(from, to, amount)
Preconditions: from/to ton tai va khac nhau; amount > 0
Success: tru from, cong to, tra Receipt
Failure: invalid transfer / insufficient funds / unknown outcome
Side effects: so du thay doi dung mot lan khi thanh cong
Retry: chi duoc phep khi co idempotency key theo hop dong
Timeout: khong tu suy ra rollback neu ket qua chua ro
Cross-cutting: tracing, counters; quota neu duoc API cong bo
~~~

Đây là tài liệu gợi ý, không phải lời hứa rằng đoạn ledger demo đã hỗ trợ retry, idempotency key hay hệ thống phân tán. Các trường chỉ nên được gán giá trị đúng với API thực tế; khi chưa được thiết kế phải ghi rõ là chưa hỗ trợ.

## 8. Exception translation, fallback và API compatibility

### 8.1. Khi nào được dịch lỗi?

Việc chuyển một lỗi hạ tầng cụ thể sang lỗi API ổn định có thể giúp caller không phụ thuộc thư viện thấp tầng. Ví dụ, hợp đồng có mã `STORAGE_UNAVAILABLE` cho lỗi kết nối storage. Advice ở đúng ranh giới có thể ánh xạ lỗi tương ứng, giữ cause để phân tích, ghi metric và không mất thông tin thành công/thất bại.

Không nên chuyển mọi `RuntimeException` thành “không tìm thấy dữ liệu”, vì những exception đó có thể là bug, lỗi validation hay lỗi authorization. Cách dịch phải có bảng ánh xạ cụ thể, và các trường hợp không nhận diện phải có hướng xử lý rõ.

### 8.2. Fallback là một kết quả nghiệp vụ

Một Aspect trả dữ liệu cache khi downstream lỗi đang áp dụng semantics **stale data được chấp nhận**. Điều đó phải được caller biết: dữ liệu cũ đến mức nào, có dấu hiệu stale không, những operation nào được phép fallback. Nếu API hứa dữ liệu tức thời, fallback lén có thể gây quyết định sai.

Nếu fallback thay thế một lệnh ghi, đặc biệt cần thận trọng: “giả lập success” cho một thao tác chưa được xác nhận thường không thể chấp nhận. Một trạng thái `QUEUED` hoặc `PENDING` có thể phù hợp hơn **nếu đã nằm trong hợp đồng** và có cơ chế hoàn tất về sau.

### 8.3. Hợp đồng cũ và tương thích ngược

Thêm một Aspect tưởng chỉ để “chuẩn hóa” có thể là breaking change:

- Một exception trước đây được ném nay bị nuốt.
- Caller trước đây nhận `null` nay bị ném exception, hoặc ngược lại.
- Một lệnh thành công trước đây nay bị giới hạn tốc độ.
- Tác vụ bị retry và có side effect gấp đôi.
- Khi tracing advice bổ sung context vào thread-local, luồng async nhận trạng thái khác nếu cleanup không đúng.

Đánh giá thay đổi theo **observable behavior** và API versioning. Không coi việc mã của method gốc không đổi là bằng chứng “không có thay đổi hợp đồng”.

## 9. Đặc biệt nguy hiểm tại ranh giới bất đồng bộ

Một method có thể trả `Future<Result>` ngay khi công việc chưa kết thúc. Around advice đồng bộ có thể quan sát được “trả handle thành công” nhưng không phải “business operation thành công”.

Ví dụ, advice:

~~~java
// Minh hoa logic sai, khong la chuong trinh doc lap:
Object handle = proceed();
audit("business-success"); // SAI neu handle chi la Future chua hoan tat
return handle;
~~~

Nếu worker sau đó thất bại, audit “business-success” sai thực tế. Muốn báo trạng thái nghiệp vụ, gắn observer vào **completion phù hợp**, giữ nguyên semantics hủy/lỗi và tránh để lỗi telemetry làm hỏng handle.

Những bẫy khác:

- **Timeout không đồng nghĩa rollback:** caller ngừng chờ không chứng minh thao tác nền hoặc lệnh từ xa đã dừng.
- **Cancel không đồng nghĩa resource đã đóng:** worker có thể còn đang xử lý dọn dẹp.
- **Callback có thể chạy trên luồng khác:** context/authorization/transaction không tự chuyển theo `ThreadLocal`.
- **Retry async có thể nhân side effect:** cùng operation ID/idempotency key phải được giữ theo contract, và chỉ retry khi trạng thái cho phép.
- **Tác vụ fan-out:** một nhánh lỗi không tự quyết định toàn bộ operation thất bại hoặc rollback; cần chính sách tổ hợp được đặc tả.

Đừng để một Aspect biến “đã nhận yêu cầu” thành “đã hoàn tất nghiệp vụ”. Nếu API mang kiểu `CompletionStage<Receipt>`, hợp đồng phải nói rõ stage thành công khi nào, exception nào được phát, trách nhiệm của cancellation và thời điểm side effect trở nên visible.

## 10. Chính sách thực thi có thể là một phần hợp đồng

Một số cơ chế AOP được gọi là “execution policy”, nhưng ảnh hưởng trực tiếp đến caller. Cần làm rõ trước khi thực thi.

| Chính sách | Điều caller có thể quan sát | Cần công bố |
| --- | --- | --- |
| Timeout/deadline | Lỗi timeout, kết quả chưa biết, dừng chờ | Mốc tính thời gian, nghĩa của timeout đối với side effect |
| Retry | Thời gian tăng, lệnh có thể chạy nhiều lần | Số lần tối đa, điều kiện retry, idempotency |
| Quota/rate limit | Request bị từ chối | Đơn vị quota, phạm vi, lỗi khi hết quota, khả năng thử lại |
| Cache | Giá trị có thể cũ | TTL/consistency, invalidation, cách yêu cầu dữ liệu mới |
| Authorization | Request bị chặn | Quyền hạn, mã lỗi, phạm vi bảo vệ |
| Circuit breaker | Downstream không được gọi | Điều kiện từ chối tạm thời, lỗi trả về |
| Bulkhead | Lệnh bị từ chối khi hết slot | Hành vi khi quá tải, thời gian chờ/queue |
| Logging/tracing | Thêm tác dụng phụ về quan sát | Trường dữ liệu, chi phí, quyền truy cập, retention |
| Transaction boundary | Thành công/lỗi gắn với đơn vị commit | Atomicity thực sự, phạm vi nguồn dữ liệu và consistency |

Ví dụ, **quota 100 lời gọi/giây** có thể là hạn mức tính phí của sản phẩm hoặc một mức bảo vệ nội bộ. Nếu caller được hứa “mọi yêu cầu hợp lệ đều được xử lý”, quota ẩn thay đổi lời hứa đó. Nếu API công bố có thể trả `TOO_MANY_REQUESTS`, cùng cơ chế có thể trở thành triển khai hợp lệ của chính sách đã định.

Đặt tên policy rõ ràng, version và kiểm thử tương thích. Tránh các pointcut quá rộng như “mọi method bắt đầu bằng `save`” nếu các method đó có semantics khác nhau.

## 11. Những chi tiết nâng cao khi triển khai AOP

### 11.1. Chính xác của pointcut và phạm vi weaving

Tùy cơ chế AOP, join point có thể bao gồm method execution, method call, constructor hoặc truy cập field. Với proxy, chỉ những lời gọi đi qua proxy mới chịu interception; **self-invocation** thường không qua proxy đó. Với bytecode weaving, tập điểm chặn có thể rộng hơn và cần kiểm soát lớp/thư viện được weave.

Nếu một invariant chỉ được enforce ở Aspect, một đường gọi bypass sẽ tạo trạng thái sai. Đây là lý do các **bất biến cốt lõi cần được bảo vệ tại domain**, còn Aspect dùng để tái sử dụng hành vi tại những boundary đã xác định.

### 11.2. Thứ tự advice và composition

Giả sử các lớp advice áp dụng như sau:

~~~text
TraceAspect
  -> RetryAspect
       -> ValidateBoundaryAspect
            -> TransferService
~~~

Một lời gọi caller có thể tạo **một trace logic** nhưng nhiều lần validation và nhiều lần thực thi domain. Nếu đổi thứ tự:

~~~text
RetryAspect
  -> TraceAspect
       -> TransferService
~~~

Mỗi lần retry có thể tạo một trace hoặc span riêng tùy cách truyền context. Thứ tự khác nhau có thể làm thay đổi số lần chạy, transaction scope, accounting metric và loại lỗi nhìn thấy. Một hệ thống phải có quy tắc composition được thử nghiệm, không dựa vào trật tự ngẫu nhiên khi load class.

Nếu authorization advice chạy ngoài cache, nó kiểm tra cả cache hit. Nếu authorization chạy chỉ trong đường gọi dữ liệu gốc, cache hit có thể bypass. Đây không chỉ là lựa chọn thứ tự kỹ thuật: nó liên quan đến hợp đồng bảo vệ dữ liệu.

### 11.3. Advice là mã có lỗi và chi phí

Aspect có thể ném exception, deadlock, làm rò thread-local, quá tải I/O log hoặc giữ lock lâu. Hãy:

- Giữ phần telemetry trong phạm vi lỗi độc lập, không nuốt exception nghiệp vụ.
- Tránh gọi `proceed()` hai lần nếu đó không phải chính sách replay được phép.
- Với around advice, bảo đảm trả đúng kết quả hoặc truyền đúng lỗi khi không chủ đích thay đổi contract.
- Đặt cleanup tài nguyên của advice trong `finally`, đồng thời xác định tài nguyên thực sự thuộc ai.
- Đo overhead và áp dụng sampling/rate limiting phù hợp cho log, không làm mất metric cần thiết.
- Kiểm soát recursion: log advice gọi vào cùng join point có thể dẫn tới advice tự chặn chính mình.

### 11.4. Không nhầm chính sách doanh nghiệp với bảo vệ hạ tầng

Một business rule có thể đi xuyên nhiều module, nhưng việc nó “cross-cutting” không làm nó trở thành chi tiết vô hình. Ví dụ chính sách tenant isolation hoặc chống gian lận có thể được thực thi ở nhiều điểm. Việc đóng gói kiểm tra bằng Aspect có thể tái sử dụng được, **nhưng** nguồn chính sách, mục tiêu bảo vệ, trường hợp từ chối và kiểm thử bypass phải được công bố trong mô hình an ninh/nghiệp vụ.

Tương tự, một hạn mức nghiệp vụ có thể được tính từ trạng thái khách hàng/thỏa thuận, khác với limiter kỹ thuật bảo vệ CPU. Hai cơ chế cần tên metric, lỗi và quyền điều chỉnh khác nhau để tránh vận hành một công tắc kỹ thuật làm thay đổi giá trị hợp đồng khách hàng.

## 12. Kiểm thử hợp đồng khi có và không có Aspect

Một chiến lược kiểm thử mạnh gồm bốn lớp.

**Lớp 1 — Domain/API trực tiếp.** Gọi implementation không qua Aspect với input hợp lệ, bất hợp lệ và các trạng thái biên. Xác nhận tiền/hậu điều kiện, bất biến và exception. Đây là nền tảng để không giấu thiếu sót hợp đồng trong advice.

**Lớp 2 — Advice độc lập.** Dùng target giả ghi số lần gọi/arguments và cho trước kết quả/exception. Kiểm tra advice gọi đúng một lần, không mutate input, không nuốt lỗi, không trả giá trị khác hợp đồng; test cả lỗi phát sinh bên trong telemetry.

**Lớp 3 — Tương đương hành vi.** Chạy cùng bộ case qua implementation trực tiếp và qua proxy/weaving. So sánh result, loại exception, state change và side effect. Với các policy được công bố như quota, kiểm thử trường hợp bị từ chối riêng thay vì đòi tương đương tuyệt đối.

**Lớp 4 — Composition và integration.** Kiểm thử retry + transaction + tracing + authorization + cache (nếu áp dụng), caller vượt boundary, đường async và công việc có side effect. Dùng giả lập lỗi sau khi downstream đã commit để phát hiện double charge, lost update và lỗi “unknown outcome”.

### 12.1. Ma trận các tình huống cần có

| Kịch bản | Kỳ vọng |
| --- | --- |
| Lệnh hợp lệ, Aspect quan sát hoạt động | Kết quả/state giống lời gọi trực tiếp; telemetry tăng |
| Lệnh sai tiền điều kiện | Từ chối đúng loại lỗi; không thay đổi state |
| Thiếu quyền | Bị từ chối ở mọi đường truy cập được bảo vệ, không lộ dữ liệu |
| Advice telemetry bị lỗi | Không biến success/failure nghiệp vụ thành trạng thái khác ngoài hợp đồng |
| Retry sau timeout không rõ kết quả | Không tự thực thi lại side effect nếu thiếu bảo đảm idempotency |
| Cache hit với quyền đã bị thu hồi | Không vô tình bypass authorization |
| Exception từ target | Giữ type/cause hoặc ánh xạ theo hợp đồng đã mô tả |
| Self-call/đường gọi không qua proxy | Bất biến domain vẫn đúng |
| Callback async thất bại sau khi trả Future | Không ghi nhận success nghiệp vụ tại mốc trả Future |
| Chèn thêm Aspect mới | Kiểm thử regression cho thứ tự và số lần proceed |

### 12.2. Kiểm thử mutation của hợp đồng

Một kỹ thuật hữu ích là cố tình viết một advice sai trong môi trường kiểm thử: sửa một input âm thành dương, thay exception bằng success, tự retry lệnh ghi hoặc bỏ validation. Bộ kiểm thử hợp đồng **phải thất bại** khi semantics bị đổi. Nếu test vẫn xanh, coverage có thể chỉ đo nhánh code mà chưa đo kết quả nghiệp vụ thực.

Không nhất thiết cần một thư viện mutation testing: có thể tạo fake advice để minh họa từng hành vi sai, sau đó so sánh trạng thái và lỗi. Điều quan trọng là kiểm thử đúng bất biến độc lập với cách triển khai.

## 13. Dấu hiệu cảnh báo khi review thiết kế

Kiểm tra kỹ nếu thấy những phát biểu hoặc cách viết sau:

- “Chúng ta không cần validate trong domain, Aspect sẽ lo hết.” → Nguy cơ bypass/thiếu trách nhiệm sở hữu invariant.
- “Có lỗi thì Aspect trả object mặc định để màn hình không bị đỏ.” → Có thể tạo success giả hoặc che lỗi phân quyền.
- “Retry mọi exception ba lần cho chắc.” → Có thể nhân side effect và tăng tải khi downstream suy yếu.
- “Method gốc không đổi nên hợp đồng không đổi.” → Around advice có thể thay đổi observable behavior.
- “Timeout nghĩa là giao dịch bị hủy.” → Có thể chỉ là caller dừng chờ, side effect vẫn xảy ra.
- “Tất cả `save*` đều có transaction giống nhau.” → Các thao tác khác nhau có ranh giới consistency khác nhau.
- “Không cần test trực tiếp object vì production luôn dùng Aspect.” → Không bảo vệ invariant ở đường gọi bị bypass hoặc cấu hình weaving bị lỗi.
- “Aspect đã check quyền nên mọi đường truy cập đều an toàn.” → Phải chứng minh pointcut/boundary và kiểm thử unauthorized path.
- “Bị rate limit là lỗi kỹ thuật nên caller không cần biết.” → Nếu caller có thể bị từ chối, cần hợp đồng về lỗi và retry.
- “Advice ghi log toàn bộ để audit là đủ.” → Logging/telemetry không tự bảo đảm tính đầy đủ, độ bền, quyền truy cập hay compliance của audit.

Bất cứ khi nào một Aspect thay đổi điều caller **có thể quan sát**, hãy coi đó là thay đổi yêu cầu, không chỉ là refactor hạ tầng.

## 14. Checklist ra quyết định trước khi phát hành

Trước khi chấp nhận một Aspect mới, hãy trả lời rõ:

1. **Hợp đồng ở đâu?** Preconditions, invariants, postconditions, lỗi và side effect được định nghĩa trong API/domain nào?
2. **Advice thêm hành vi gì?** Chỉ telemetry hay còn mutate args, alter return, throw, suppress, retry, cache hoặc từ chối?
3. **Có đường bypass không?** Direct call, self-call, worker, endpoint khác, background job hoặc weaving configuration thay đổi sẽ thế nào?
4. **Ai sở hữu quyết định nghiệp vụ?** Domain/service hay policy được công bố? Ai có quyền sửa quy tắc và kiểm thử nó?
5. **Các lỗi được giữ đúng chưa?** Có phân biệt input invalid, thiếu quyền, lỗi tạm thời và kết quả không rõ?
6. **Side effect có an toàn khi advice chạy lặp không?** Có idempotency key, deadline và giới hạn retry đúng hợp đồng?
7. **Thứ tự các Aspect có kiểm thử không?** Đặc biệt authorization, retry, cache, transaction, metrics.
8. **Có kiểm thử trước/sau weaving không?** So sánh observable behavior với phép đo state/exception đáng tin.
9. **Tác vụ bất đồng bộ được theo dõi đúng mốc chưa?** Kết thúc handle khác hoàn tất nghiệp vụ; cleanup thuộc đúng lifecycle.
10. **Người vận hành có phân biệt được lỗi domain và lỗi policy không?** Telemetry nêu được nguồn từ chối hoặc thông số policy mà không rò dữ liệu nhạy cảm.

AOP mạnh khi giúp thực thi nhất quán một **hợp đồng đã được xác định**, giảm lặp logic cắt ngang và tăng khả năng quan sát. Nó trở thành rủi ro khi khiến người đọc method tưởng đang có một hợp đồng, còn runtime âm thầm áp dụng một hợp đồng khác. Vì vậy, hãy giữ **quy tắc đúng/sai của nghiệp vụ tường minh và có kiểm thử độc lập**, rồi dùng Aspect để triển khai phần chính sách cắt ngang phù hợp trong những ranh giới đã được khai báo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-policies-009">Đánh giá ảnh hưởng của mỗi chính sách tới lỗi và tác dụng phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá ảnh hưởng của mỗi chính sách tới lỗi và tác dụng phụ trong AOP

## 1. Mục tiêu: một chính sách thực thi không chỉ làm thay đổi luồng điều khiển

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), một **aspect** có thể áp dụng chính sách tại nhiều **join point** được chọn bằng **pointcut**. Ví dụ, quanh một thao tác nghiệp vụ có thể có advice kiểm tra quyền, mở giao dịch, thử lại lỗi tạm thời, đặt thời hạn chờ, dùng cache, giới hạn tải, ghi log hoặc ghi nhận kết quả.

Chính sách tưởng chừng chỉ bổ sung một hành động phụ nhưng có thể làm thay đổi nhiều đặc điểm quan sát được:

1. **Loại lỗi người gọi nhận được:** lỗi nguyên thủy được truyền tiếp, bị bọc, bị biến đổi, bị nuốt hoặc bị thay thế bởi lỗi của advice.
2. **Số lần hành vi đích chạy:** một lượt, nhiều lượt do retry, không lượt nào do cache hit/deny/open circuit, hoặc vẫn còn chạy sau timeout.
3. **Trạng thái tài nguyên:** transaction đã commit/rollback/chưa xác định, lock được giữ hoặc giải phóng, tài nguyên đã đóng đúng lúc chưa.
4. **Tác dụng phụ (side effects):** ghi dữ liệu, gửi email, gọi dịch vụ ngoài, phát thông điệp, ghi audit, tạo metric… có thể xảy ra một lần, nhiều lần, một phần hoặc không xảy ra.
5. **Thời điểm báo kết quả:** lời gọi trả về, transaction commit, tác vụ bất đồng bộ hoàn tất, hay tín hiệu từ hệ thống bên ngoài.

Bài này tập trung vào **cách đánh giá ảnh hưởng của từng chính sách đối với lỗi và tác dụng phụ**, sau đó ghép chúng thành một chuỗi advice có hành vi nhất quán. Khái niệm áp dụng cho AOP nói chung; cách khai báo thứ tự advice, loại join point và hành vi của cơ chế dệt mã phụ thuộc vào công cụ AOP cụ thể.

### Ba câu hỏi bắt buộc với mọi chính sách

Trước khi triển khai một advice, hãy hỏi:

- **Lỗi:** advice nhận, tạo, nuốt, bọc, chuyển đổi hoặc truyền tiếp những loại lỗi nào?
- **Tác dụng phụ:** proceed() có thể chạy bao nhiêu lần, phần nào có thể được hoàn tác, phần nào không?
- **Hợp đồng quan sát:** người gọi hoặc hệ thống đo lường thấy điều gì là thành công, thất bại, hủy bỏ hoặc kết quả chưa chắc chắn?

Ví dụ, nếu một advice retry gọi proceed() ba lần, tác dụng phụ bên trong có thể xuất hiện ba lần. Nếu một advice timeout ngừng *chờ* sau hai giây, công việc nền có thể tiếp tục tạo tác dụng phụ sau khi người gọi đã nhận lỗi timeout.

## 2. Mô hình đánh giá: join point, advice, policy và effect

**Join point** là điểm thực thi nơi cơ chế AOP có thể can thiệp, chẳng hạn thực thi một phương thức hoặc một lời gọi phương thức, tùy mô hình AOP. **Pointcut** là biểu thức chọn các join point; **advice** thực hiện chính sách khi join point được chọn.

Một around advice được hình dung như phép biến đổi một hành vi:

~~~text
P(action) = action mới được bao quanh bởi policy P

Ví dụ:
Retry(Transaction(action))
Transaction(Retry(action))
Record(Retry(Transaction(action)))
~~~

Các biểu thức trên mô tả **thứ tự lồng nhau**, không phải cú pháp nguồn của một thư viện. Khi advice ở ngoài gọi proceed(), nó chuyển quyền điều khiển vào lớp trong; khi proceed() trả về hoặc ném lỗi, lớp ngoài tiếp tục xử lý theo chính sách của mình.

Để đánh giá một policy, định nghĩa một bản mô tả tác động:

| Chiều đánh giá | Câu hỏi |
| --- | --- |
| Entry | Chính sách có cho chạy proceed() không? Có thể từ chối/chặn/bỏ qua không? |
| Execution count | proceed() được gọi 0, 1 hay nhiều lần? |
| Error channel | Lỗi đồng bộ, thất bại bất đồng bộ, lỗi commit, timeout, hủy có được nhìn thấy? |
| Error mapping | Chính sách rethrow, wrap, fallback, suppress hay tạo lỗi mới? |
| State & transaction | Policy chạy trong hay ngoài transaction? Có rollback-only, savepoint hoặc commit không chắc chắn? |
| Side effects | Effect nào xảy ra trước lỗi? Có được transaction hoàn tác thực sự không? |
| Ordering | Advice nào ở ngoài để thấy outcome cuối; advice nào ở trong để quan sát từng attempt? |
| Observability | Log, metric và audit đang đo một attempt, một invocation hay cả operation? |

Một policy an toàn trong một bối cảnh có thể không an toàn ở bối cảnh khác. Không thể khẳng định “retry luôn tốt”, “timeout luôn dừng tác vụ” hoặc “transaction sẽ rollback mọi thứ” mà chưa xác định ranh giới thực tế.

## 3. Phân loại lỗi trước khi đánh giá chính sách

Không nên gom toàn bộ trạng thái không thành công thành một loại exception chung.

### 3.1. Lỗi trước khi hành vi đích chạy

Ví dụ kiểm tra quyền thất bại, thiếu dữ liệu đầu vào hoặc giới hạn tần suất. Advice có thể **không gọi proceed()**. Như vậy side effect của hành vi đích không xảy ra, nhưng side effect do chính advice thực hiện (ghi log, tăng counter, truy vấn quyền) vẫn có thể xuất hiện.

### 3.2. Lỗi trong hành vi đích

Hành vi đã bắt đầu và có thể tác động một phần lên tài nguyên trước khi ném lỗi. Cần xác định các effect đó thuộc transaction nào và liệu cơ chế rollback có bao phủ chúng không.

### 3.3. Lỗi ở giai đoạn kết thúc

Một phương thức có thể trả về bình thường nhưng commit thất bại, ghi audit bắt buộc không thành công, hoặc gửi phản hồi thất bại. **Return bình thường của hàm nghiệp vụ** không chứng minh mọi nghĩa vụ sau đó đã hoàn tất.

### 3.4. Thất bại bất đồng bộ

Một phương thức trả về CompletionStage/Future/Publisher chưa hoàn tất. Exception xảy ra về sau không tự trở thành ngoại lệ được ném đồng bộ từ proceed(). Đánh giá phải tách **kết thúc lần gọi** khỏi **kết thúc tác vụ logic**.

### 3.5. Timeout, cancellation và kết quả chưa rõ

Timeout nhiều khi chỉ nói rằng người quan sát hết kiên nhẫn chờ; nó **không chứng minh** thao tác đã được hủy hoặc rollback. Một lỗi mạng đúng lúc commit có thể khiến kết quả trở thành **unknown/in-doubt**, cần kiểm tra trạng thái thay vì retry mù.

### 3.6. Lỗi của chính advice

Logging, metrics, cache, bộ kiểm tra quyền hoặc bộ quản lý transaction đều có thể ném lỗi. Phải phân biệt lỗi của **hành vi đích** và lỗi do **policy** tạo ra để không làm sai phân loại, retry ngoài ý muốn hoặc che mất nguyên nhân thực.

Một cách ghi outcome có ích:

~~~text
outcome:
  SUCCESS          hành vi hoàn tất theo hợp đồng
  BUSINESS_REJECT  kết quả hợp lệ nhưng nghiệp vụ từ chối
  RETRYABLE_ERROR  lỗi tạm thời, có thể xem xét thử lại
  PERMANENT_ERROR  lỗi không nên thử lại theo chính sách
  CANCELLED        có tín hiệu hủy theo API
  TIMED_OUT        quá thời hạn chờ đã định
  UNKNOWN          không thể khẳng định thành công hay thất bại
~~~

Đây chỉ là mô hình phân loại để tư duy. Trong hệ thống thật, outcome phụ thuộc hợp đồng API, transaction manager và trạng thái nghiệp vụ; không nhất thiết tất cả trạng thái đều biểu diễn bằng exception.

## 4. Bảng đánh giá các chính sách thường gặp

| Chính sách | Ảnh hưởng đến lỗi | Ảnh hưởng đến tác dụng phụ | Kiểm tra quan trọng |
| --- | --- | --- | --- |
| Kiểm tra đầu vào | Có thể từ chối trước proceed(); nếu đặt sai vị trí có thể bắt lỗi quá muộn | Thường tránh effect của hành vi đích khi từ chối | Validation có diễn ra trước mọi effect có liên quan không? |
| Authorization | Trả lỗi từ chối hoặc không gọi proceed() | Tránh effect khi không được phép; ghi audit từ chối vẫn có thể xảy ra | Có bị cache, fallback hoặc đường gọi khác vượt qua không? |
| Transaction | Commit, rollback hoặc báo trạng thái không chắc chắn | Hoàn tác các thay đổi trên tài nguyên tham gia transaction | Gọi ngoài hệ thống có thực sự tham gia transaction không? |
| Retry | Có thể biến lỗi attempt thành thành công cuối, hoặc ném lỗi cuối | Lặp proceed() và effect bên trong nhiều lần | Thao tác có idempotent không? Có giới hạn attempt/deadline không? |
| Timeout | Có thể tạo lỗi timeout dù công việc còn chạy | Effect có thể tiếp tục sau timeout | Timeout có hủy tác vụ thật không? Ai theo dõi kết quả về sau? |
| Circuit breaker | Có thể từ chối sớm khi mạch mở; giữ lỗi thực ở lượt probe | Không chạy hành vi đích khi bị chặn | Có phân biệt open-circuit rejection và backend failure không? |
| Rate limit/bulkhead | Từ chối hoặc xếp hàng, có thể phát sinh timeout lúc chờ | Giảm số lượt thực thi; tác vụ được nhận có thể còn đang chạy | Tài nguyên permit có được giải phóng ở mọi đường kết thúc? |
| Cache | Cache hit có thể bỏ qua hành vi đích; lỗi cache có thể gây lỗi mới | Bỏ qua cả effect trong action nếu hit; cache write có thể gây thêm effect | Hành vi được cache có thuần đọc hay có effect bắt buộc? |
| Fallback | Chuyển lỗi thành giá trị thay thế | Có thể che effect một phần đã xảy ra trước lỗi | Fallback có tương thích hợp đồng nghiệp vụ không? |
| Logging/metrics | Observer có thể ném lỗi, thay exception gốc nếu thiết kế sai | Tạo effect quan sát; có thể ghi lặp theo từng attempt | Best effort hay bắt buộc? Ghi theo attempt hay operation? |
| Audit bắt buộc | Lỗi audit có thể khiến toàn operation thất bại | Ghi dữ liệu bền, có thể cần transaction riêng/cùng transaction | Audit thất bại có tồn tại sau rollback không? |
| Idempotency/deduplication | Có thể trả kết quả đã lưu hoặc báo conflict | Ngăn effect trùng nếu bảo đảm atomic phù hợp | Key có đúng phạm vi nghiệp vụ, thời hạn và ràng buộc duy nhất không? |

Bảng này là công cụ đặt câu hỏi, không phải cam kết rằng mọi framework thực thi giống nhau. Phần dưới phân tích cơ chế và các tổ hợp quan trọng.

## 5. Chính sách validation và authorization: tác động trước proceed()

Một advice kiểm tra hợp lệ hoặc quyền truy cập thường quyết định:

~~~text
if request invalid or unauthorized:
    reject
else:
    proceed()
~~~

Khi từ chối **trước proceed()**, hành vi đích không chạy. Đây là điểm mạnh: ngăn các effect nghiệp vụ mà hành vi đích có thể tạo. Tuy nhiên vẫn có ba rủi ro:

1. **Điểm cắt không bao phủ đường gọi thật:** một lời gọi không đi qua interception có thể bỏ qua validation/authorization; phải kiểm tra cơ chế weaving và các đường gọi thực tế.
2. **Đặt sai vị trí:** nếu một advice tạo side effect hoặc gọi hàm nghiệp vụ nằm ngoài lớp kiểm tra quyền, side effect ấy vẫn có thể xảy ra dù bên trong bị từ chối.
3. **Ghi nhận và lỗi phụ:** tra cứu quyền hoặc ghi audit từ chối có thể thất bại. Cần quy định hành vi khi hệ thống cấp quyền không sẵn sàng, ưu tiên an toàn cho thao tác nhạy cảm.

Ví dụ:

~~~text
Authorization(Transaction(operation))
~~~

Từ chối quyền trước khi mở transaction ở lớp trong, giảm công việc tài nguyên. Với:

~~~text
Transaction(Authorization(operation))
~~~

transaction được mở trước khi biết yêu cầu có quyền hay không; tùy trường hợp có thể là chi phí không cần thiết. Cả hai đều có thể đúng về mặt chức năng khi không có effect nào trước authorization, nhưng khác chi phí và ranh giới thất bại.

**Không giả định một thứ tự duy nhất luôn tối ưu:** với một số operation, authorization phụ thuộc dữ liệu nhất quán bên trong transaction. Cần phân biệt kiểm tra nhận dạng chung và kiểm tra quyền gắn với bản ghi cụ thể.

## 6. Chính sách transaction: phân loại effect có thể rollback

Transaction không phải “cỗ máy hoàn tác mọi tác dụng phụ”. Ranh giới rollback chỉ bao phủ tài nguyên thực sự tham gia vào transaction theo hợp đồng của resource manager.

### 6.1. Effect nằm trong transaction

Ví dụ cập nhật hai bảng trong cùng một database transaction. Nếu transaction rollback, những cập nhật chưa commit được hoàn tác theo bảo đảm của database.

### 6.2. Effect ngoài transaction

Ví dụ trong cùng một lời gọi:

~~~text
begin database transaction
update order status
send email to customer
error occurs
rollback database
~~~

Email đã gửi không được “thu hồi” bởi rollback database. Nếu retry toàn bộ lời gọi, email có thể bị gửi thêm lần nữa.

Tương tự với cuộc gọi HTTP, thanh toán ngoài, thông điệp gửi trực tiếp sang broker, ghi file hoặc thao tác trên tài nguyên không tham gia transaction.

### 6.3. Lỗi tại commit

Một lời gọi nghiệp vụ có thể trả về thành công nhưng transaction commit xảy ra sau đó và thất bại. Nếu ghi kết quả “thành công đã cam kết” trước commit, log/audit có thể sai.

Một số failure tại commit có trạng thái **không chắc chắn**: phản hồi commit bị mất nhưng thay đổi đã được lưu bền. Retry một thao tác tạo đơn hàng mà không có khóa chống trùng có thể tạo hai đơn.

### 6.4. Lỗi rollback và cleanup

Rollback hoặc đóng tài nguyên cũng có thể ném lỗi. Transaction advice phải giữ đủ thông tin về lỗi ban đầu và lỗi phụ để chẩn đoán. Nếu advice dọn dẹp ném lỗi trong finally, lỗi đó có thể che mất lỗi đích nếu không được xử lý có chủ đích.

### 6.5. Câu hỏi đánh giá transaction

- Transaction bắt đầu trước hành vi nào và commit sau hành vi nào?
- Một transaction mới được tạo hay tham gia transaction có sẵn?
- Những resource nào tham gia cùng ranh giới atomicity?
- Có thể có rollback-only, savepoint, trạng thái aborted hoặc commit in-doubt không?
- Logging/audit/sự kiện ngoài transaction có cần được giữ hoặc chống trùng không?
- Sau khi transaction kết thúc, advice còn giữ connection/lock/context trên thread không?

## 7. Chính sách retry: lỗi bị thử lại và effect bị nhân số lượt

Một retry advice có thể gọi proceed() nhiều lần:

~~~text
for attempt in 1..maxAttempts:
    try:
        return proceed()
    catch error:
        if not retryable(error) or attempt is last:
            throw error
        apply backoff
~~~

Nếu hành vi đích có effect, số lần gọi proceed() là một phần của **hợp đồng thực thi**. Các effect sau có thể nhân theo số attempt: ghi mới một bản ghi không có unique key, phát thông điệp, gửi email, gọi thanh toán, tăng counter, ghi audit per-attempt.

Retry chỉ an toàn khi ít nhất một trong các điều kiện được đáp ứng:

- Hành vi có tính idempotent theo hợp đồng nghiệp vụ.
- Mỗi attempt bị lỗi đã hoàn tác được **toàn bộ effect có liên quan**, không còn effect ngoài transaction.
- Có deduplication/idempotency key bền vững, unique constraint hoặc cơ chế điều phối kiểm soát việc lặp.
- Operation và side effect có cơ chế đối soát khi outcome không chắc chắn.

**Retryable** không đồng nghĩa **retry safe**. Một lỗi mạng có thể chỉ là tạm thời, nhưng thao tác phía server đã hoàn tất trước khi kết nối đứt. Phải đánh giá cả khả năng phục hồi lỗi *và* an toàn khi lặp effect.

Các giới hạn thiết yếu:

1. Max attempts và retry budget để tránh vòng lặp vô hạn.
2. Backoff/jitter để không tạo retry storm khi hệ thống đang quá tải.
3. Deadline tổng để operation không vượt thời gian cam kết.
4. Phân loại lỗi rõ ràng, không retry lỗi dữ liệu hoặc policy/telemetry một cách vô điều kiện.
5. Xử lý cancellation và thread interruption đúng hợp đồng.
6. Phân biệt outcome của từng attempt với outcome cuối sau retry.

Một lỗi cố định như dữ liệu đầu vào không hợp lệ thường không thể được sửa bằng cách lặp lại hành vi giống hệt.

## 8. Thứ tự transaction và retry có thể đổi khả năng phục hồi

Hai chuỗi này có khác biệt cốt lõi:

~~~text
Retry(Transaction(operation))
~~~

~~~text
Transaction(Retry(operation))
~~~

### 8.1. Retry nằm ngoài Transaction

Mỗi lần proceed() đi vào Transaction có thể tạo một giao dịch mới:

~~~text
attempt 1 -> T1 begin -> operation lỗi -> T1 rollback
attempt 2 -> T2 begin -> operation thành công -> T2 commit
~~~

Mô hình này thường phù hợp cho việc retry **toàn bộ đơn vị công việc giao dịch** sau một lỗi khiến transaction không còn an toàn để tiếp tục. Nó cũng cho phép policy retry nhìn thấy cả lỗi được Transaction truyền ra ở commit/rollback.

Nhưng chỉ có transaction mới khi wrapper thực sự **tạo** giao dịch mới. Nếu policy propagation khiến wrapper luôn tham gia transaction ngoài, không thể kết luận mỗi attempt độc lập chỉ từ hình thức lồng nhau.

### 8.2. Transaction nằm ngoài Retry

~~~text
T1 begin
attempt 1 -> operation lỗi
attempt 2 -> operation thành công
T1 commit?
~~~

Một số hệ thống đánh dấu T1 là aborted/rollback-only sau lần lỗi đầu. Retry bắt exception không tự đặt T1 trở về trạng thái hợp lệ. Attempt thứ hai có thể thất bại hoặc commit cuối phải rollback.

Ngoài ra, khi Retry chờ backoff trong transaction, lock và tài nguyên có thể bị giữ lâu, làm nặng thêm tranh chấp.

### 8.3. Savepoint

Nếu muốn retry một phần trong cùng transaction, savepoint có thể phù hợp **nếu** resource manager hỗ trợ việc phục hồi sau loại lỗi đó. Savepoint không giống một transaction top-level mới, không đảm bảo khôi phục effect ngoài database và không cứu được mọi loại lỗi làm transaction bị hủy bỏ.

## 9. Chính sách timeout và cancellation: “không chờ nữa” khác “đã dừng”

Một timeout advice có thể áp đặt thời hạn cho operation. Nhưng cần phân biệt:

- **Timeout của người chờ:** caller ngừng chờ và nhận lỗi; tác vụ có thể vẫn chạy.
- **Timeout của lời gọi mạng:** kết nối hoặc request bị kết thúc ở phía caller; server có thể đã hoàn tất effect.
- **Timeout của transaction:** hệ thống có thể tự đánh dấu transaction rollback/đóng; hành vi cụ thể tùy resource manager.
- **Deadline cho chuỗi retry:** thời hạn của toàn operation, không chỉ từng attempt.

Tình huống:

~~~text
t=0   client gọi createPayment
t=2s  client timeout và trả lỗi cho người dùng
t=3s  server thực tế đã ghi thanh toán
t=4s  client retry mà không có idempotency key
~~~

Retry sau timeout có thể tạo thanh toán thứ hai. Vì vậy timeout cần đi kèm **cancellation semantics**, idempotency và cách kiểm tra outcome.

Một cancellation signal cũng không luôn dừng công việc: có API chỉ hoàn tất future với trạng thái hủy, trong khi underlying task đã bắt đầu vẫn tiếp tục. Không ghi “operation rolled back” chỉ vì cancellation được yêu cầu.

Với tác vụ async, around advice bao **phương thức trả về future** thường chỉ kết thúc lúc future được trả về; nó không tự chờ completion. Nếu policy cần đo timeout hoặc ghi outcome logic, phải theo dõi tín hiệu hoàn tất của API phù hợp và bảo đảm transaction/context có vòng đời tương thích.

## 10. Chính sách circuit breaker, bulkhead và rate limit: lỗi do từ chối

Những chính sách này thường có khả năng **không gọi proceed()**.

### Circuit breaker

Khi circuit mở, lời gọi có thể bị từ chối nhanh mà chưa chạm hệ thống đích. Đây là outcome khác với backend thực sự xử lý rồi thất bại. Trong trạng thái half-open, một số lượt probe được phép gọi để đánh giá khả năng phục hồi.

Điểm cần xác định là **lỗi nào tính vào bộ đếm breaker**. Nếu đếm cả từ chối do validation hoặc lỗi business hợp lệ, breaker có thể mở không đúng nguyên nhân. Nếu Retry nằm trong Breaker, một lượt call breaker có thể bao gồm nhiều attempts; nếu Retry nằm ngoài Breaker, mỗi attempt có thể đi qua breaker riêng và bị chặn tùy trạng thái. Sự khác biệt phải phù hợp mục tiêu quan sát.

### Bulkhead và giới hạn đồng thời

Một bulkhead có thể giới hạn số luồng, semaphore permit hoặc tác vụ được phép chạy. Khi không còn capacity, policy có thể xếp hàng hoặc từ chối.

Những rủi ro thường gặp:

- Nếu permit không được release trong finally hoặc callback hoàn tất đúng vòng đời, tải sẽ giảm dần do leak.
- Nếu giải phóng permit ngay khi phương thức trả về future, trong khi công việc async còn chạy, hệ thống có thể cho phép số tác vụ thực vượt giới hạn mong muốn.
- Retry ngay khi bị từ chối do quá tải có thể làm tải trầm trọng hơn.

### Rate limit

Rate limit thường giới hạn số lượt **được nhận** trong một cửa sổ, không chứng minh các lượt đã được nhận hoàn tất. Nếu nằm trong Retry, mỗi attempt có thể tiêu một quota; nếu ở ngoài, một operation với nhiều attempts có thể chỉ tiêu một quota. Cần quyết định chính sách muốn giới hạn **người dùng tạo operation** hay **tổng số request đi xuống downstream**.

Vì vậy không thể đánh giá rate limiter chỉ qua số lượng lỗi 429/từ chối; phải kiểm tra ranh giới tính quota và số effect thực sự xảy ra.

## 11. Chính sách cache và fallback: thành công có thể không đi qua nghiệp vụ đích

### 11.1. Cache

Ví dụ:

~~~text
Cache(operation)
~~~

Nếu cache hit, advice có thể trả giá trị **mà không gọi operation**. Điều này tốt cho truy vấn thuần đọc có chính sách freshness phù hợp, nhưng nguy hiểm nếu operation còn tạo effect bắt buộc như ghi audit hoặc cập nhật lần truy cập.

Một cache advice có thể làm biến đổi lỗi:

- Cache backend lỗi: fail-open để gọi origin, hay fail-closed báo lỗi?
- Origin lỗi: trả stale value hay truyền lỗi?
- Ghi cache thất bại: có làm thất bại operation đã chạy thành công không?
- Cache key thiếu tenant/permission: có nguy cơ đọc sai dữ liệu hoặc rò thông tin không?

Một cache miss kết hợp retry cũng có thể tạo thundering herd khi nhiều request đồng thời cùng gọi origin; cần đánh giá đồng bộ tải, thời hạn và khả năng quan sát.

### 11.2. Fallback

Fallback có thể chuyển exception thành giá trị thay thế:

~~~text
try:
    return proceed()
catch:
    return fallbackValue
~~~

Điều này chỉ hợp lệ khi **hợp đồng nghiệp vụ cho phép**. Trả “thành công rỗng” khi thao tác đặt hàng đã thất bại có thể che giấu dữ liệu chưa được lưu; trả dữ liệu cache hơi cũ cho màn hình tham khảo có thể chấp nhận nếu người dùng được thông tin rõ.

Một operation có thể đã tạo một phần effect rồi mới ném lỗi. Fallback không đảo ngược các effect đó, nên không được tự coi fallback success là nguyên trạng “chưa có gì xảy ra”.

Cần phân biệt **backend failed**, **fallback applied** và **end-user outcome**. Nếu metric chỉ nhìn thấy giá trị trả về cuối cùng, một failure được che bởi fallback có thể biến mất khỏi thống kê lỗi hạ tầng.

## 12. Chính sách ghi log, metric và audit: quan sát cũng tạo tác dụng phụ

Logging/metrics là concern quan sát, nhưng exporter và recorder vẫn có thể:

- Phân bổ bộ nhớ, giữ tham chiếu, tạo tác vụ nền.
- Gây block khi bộ đệm đầy hoặc kênh lưu trữ chậm.
- Ném exception, làm thay đổi luồng lỗi.
- Ghi trùng do retry hoặc advice áp dụng ở nhiều lớp.
- Ghi dữ liệu nhạy cảm nếu lấy quá nhiều thông tin ở join point.

Cần xác định hai loại:

**Best effort observer:** lỗi của việc ghi log/metric không được làm đổi kết quả nghiệp vụ ngoài ý muốn. Nếu cần, bắt exception của observer và ghi cảnh báo qua kênh dự phòng an toàn, tránh vòng lặp gọi chính recorder đang lỗi.

**Mandatory audit:** bắt buộc ghi bằng chứng theo yêu cầu nghiệp vụ hoặc quy định. Đây là một bước có thể làm operation thất bại; cần thiết kế giao dịch, độ bền và xử lý trùng lặp rõ ràng. Không thể gọi nó là “chỉ quan sát” rồi tùy tiện nuốt lỗi.

### 12.1. Recorder theo attempt và recorder theo operation

Một composition hữu ích:

~~~text
RecordFinal(
    Retry(
        RecordAttempt(
            Transaction(
                operation
            )
        )
    )
)
~~~

- RecordAttempt đặt ngoài Transaction để biết outcome của mỗi attempt **sau xử lý giao dịch**.
- Retry có thể bỏ qua lỗi attempt để tiếp tục attempt mới.
- RecordFinal ở ngoài Retry ghi **kết quả cuối** của cả operation sau tất cả attempts.

Nếu RecordAttempt ghi failure ở attempt đầu nhưng attempt thứ hai thành công, **operation cuối có success**. Đây không phải mâu thuẫn, bởi hai bản ghi có đơn vị đếm khác nhau.

### 12.2. Lỗi recorder có thể kích hoạt retry không mong muốn

Nếu RecordAttempt là lớp nằm **trong Retry** và nó ném lỗi sau khi Transaction đã commit, Retry có thể tưởng operation thất bại và gọi lại toàn bộ nghiệp vụ. Điều này đặc biệt nguy hiểm: **nghiệp vụ đã commit nhưng lại được chạy lần nữa chỉ vì log lỗi**.

Vì vậy, với best effort recorder, phải cô lập lỗi recorder. Với mandatory audit, cần xác định ranh giới transactional và idempotency từ đầu; không tùy tiện đặt recorder trong Retry rồi dùng phân loại exception chung.

### 12.3. Ghi event sau commit và khoảng trống do crash

Thứ tự:

~~~text
database commit
process crash
publish event
~~~

có thể làm event không bao giờ được gửi. Đổi thành publish trước commit lại có thể phát một event cho transaction cuối cùng bị rollback.

Nếu cần gắn cập nhật database và ý định gửi event, một giải pháp phổ biến là **transactional outbox**: trong cùng giao dịch lưu dữ liệu nghiệp vụ và outbox record; worker phát event sau commit và có khả năng retry. Vì lần gửi có thể bị lặp sau crash, consumer vẫn cần idempotency/deduplication. Outbox không tự tạo “exactly once” toàn hệ thống.

## 13. Ví dụ Java 17+: một retry có thể lặp side effect ngoài transaction

Ví dụ sau mô phỏng ba advice bằng các wrapper Java thuần. Nó cố tình tạo một **effect giả định ngoài transaction** trước khi ném lỗi tạm thời ở attempt đầu. Transaction wrapper chỉ ghi các sự kiện begin, commit, rollback để dễ quan sát: **đây không phải một transaction database thật** và bản ghi side effect trong ví dụ không được khôi phục khi rollback.

Tạo tệp **PolicyEffectDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public final class PolicyEffectDemo {
    @FunctionalInterface
    interface Action {
        String run();
    }

    static final class TransientFailure extends RuntimeException {
        TransientFailure() {
            super("temporary failure");
        }
    }

    static final List<String> events = new ArrayList<>();
    static int transactionNumber = 0;
    static int businessNumber = 0;

    static Action transaction(Action next) {
        return () -> {
            int tx = ++transactionNumber;
            events.add("tx " + tx + " begin");
            try {
                String result = next.run();
                events.add("tx " + tx + " commit");
                return result;
            } catch (RuntimeException failure) {
                events.add("tx " + tx + " rollback");
                throw failure;
            }
        };
    }

    static Action retry(Action next, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        return () -> {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                events.add("attempt " + attempt);
                try {
                    return next.run();
                } catch (TransientFailure failure) {
                    if (attempt == maxAttempts) {
                        throw failure;
                    }
                }
            }
            throw new AssertionError("unreachable");
        };
    }

    static Action recordFinal(Action next) {
        return () -> {
            try {
                String result = next.run();
                events.add("final success");
                return result;
            } catch (RuntimeException failure) {
                events.add("final failure");
                throw failure;
            }
        };
    }

    static Action business() {
        return () -> {
            int call = ++businessNumber;
            events.add("business " + call);
            // Giả lập một effect bên ngoài transaction của database.
            events.add("external send " + call);
            if (call == 1) {
                throw new TransientFailure();
            }
            return "OK";
        };
    }

    public static void main(String[] args) {
        Action combined = recordFinal(retry(transaction(business()), 2));
        String result = combined.run();

        for (String event : events) {
            System.out.println(event);
        }
        System.out.println("caller " + result);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PolicyEffectDemo.java
java PolicyEffectDemo
~~~

Stdout chính xác:

~~~text
attempt 1
tx 1 begin
business 1
external send 1
tx 1 rollback
attempt 2
tx 2 begin
business 2
external send 2
tx 2 commit
final success
caller OK
~~~

**Kết luận từ ví dụ:**

- Retry gọi hành vi đích **hai lần**.
- Transaction của attempt thứ nhất xử lý nhánh rollback, attempt thứ hai xử lý nhánh commit.
- “External send” xuất hiện **hai lần**; rollback trong mô hình không có cách tự thu hồi effect ấy.
- RecordFinal ghi **một success cuối** vì operation trả về thành công sau retry.
- Nếu external send là chuyển tiền thật, việc lặp như vậy có thể là lỗi nghiêm trọng. Hệ thống phải đưa vào idempotency/deduplication hoặc tổ chức effect theo một giao thức an toàn hơn.

Không nên dùng code demo này như transaction manager thật. Nó cũng không mô phỏng lỗi commit, thread interruption hay concurrency. Điểm cần học là **thứ tự wrapper quyết định số lần proceed() chạy, trong khi rollback chỉ có tác dụng trong phạm vi tài nguyên nó kiểm soát**.

## 14. Idempotency và bảo đảm không lặp effect

Một thao tác **idempotent** có thể được gọi nhiều lần với cùng ý định nghiệp vụ mà không làm nhân đôi effect theo hợp đồng.

Ví dụ tạo thanh toán:

~~~text
client dùng idempotencyKey = payment-request-104
attempt 1: phía xử lý commit nhưng phản hồi bị mất
attempt 2: cùng key -> hệ thống tìm thấy kết quả đã lưu
           -> trả lại kết quả trước đó, không tạo khoản thu mới
~~~

Để vận hành đúng dưới nhiều request đồng thời, cần cơ chế bền và atomic thích hợp, như unique constraint trên khóa nghiệp vụ cùng logic “tạo hoặc đọc kết quả đã tồn tại” trong transaction. Kiểm tra tồn tại ở một bước riêng rồi insert mà không có ràng buộc có thể vẫn xảy ra race.

Phân biệt:

| ID | Dùng để làm gì? |
| --- | --- |
| Correlation ID | Nhóm log/sự kiện của cùng một luồng xử lý |
| Attempt ID | Phân biệt mỗi lần retry |
| Idempotency key | Chống thực hiện trùng một ý định nghiệp vụ theo hợp đồng |
| Transaction ID | Nhận diện đơn vị giao dịch theo cơ chế tài nguyên |

Không tự lấy một correlation ID cho toàn phiên làm khóa idempotency cho mọi thao tác: một phiên có thể chứa nhiều thanh toán hợp lệ, còn một idempotency key phải xác định đúng **một ý định cần chống lặp**.

Một side effect đã thực hiện có thể không hoàn tác được hoàn toàn. Khi đó thiết kế có thể cần **compensation** (hành động bù) thay vì rollback nguyên tử. Ví dụ hoàn tiền sau khi thanh toán thành công là một nghiệp vụ mới có thể thất bại riêng; nó không tương đương việc thanh toán chưa từng xảy ra.

## 15. Chính sách lỗi cần giữ nguyên nguyên nhân và tính minh bạch

Một around advice thường có nhánh:

~~~text
try:
    return proceed()
catch failure:
    observe(failure)
    throw failure
finally:
    restore context
~~~

Nếu observe() hoặc restore() ném lỗi không kiểm soát, chúng có thể **che lỗi gốc**. Các vấn đề phổ biến:

- Ghi log thất bại làm một lời gọi trước đó thành công trở thành exception.
- Dọn dẹp tài nguyên thất bại che exception ban đầu.
- Fallback bắt quá rộng và biến lỗi lập trình nghiêm trọng thành kết quả giả bình thường.
- Retry bắt mọi RuntimeException, gồm lỗi validation hoặc lỗi của recorder, rồi lặp nghiệp vụ.
- Advice bọc tất cả exception thành một kiểu chung khiến caller mất khả năng phân biệt retryable và permanent.

Không nhất thiết phải luôn giữ nguyên cùng một instance exception: một số tầng API có chủ đích cần chuyển đổi kiểu lỗi. Nhưng phải **giữ causal chain**, ghi rõ hợp đồng error mapping và không đánh tráo business rejection với sự cố hạ tầng.

Với Java, ngoại lệ thứ cấp có thể được đính kèm dưới dạng suppressed exception khi thích hợp, thay vì làm mất nguyên nhân gốc. Với Errors hoặc cancellation, chính sách phải tuân theo hợp đồng runtime, không tùy tiện coi chúng là lỗi thường có thể retry.

## 16. Thứ tự các chính sách quyết định ai nhìn thấy lỗi nào

Xét:

~~~text
Retry(Fallback(operation))
~~~

Nếu Fallback bắt lỗi rồi trả một giá trị thay thế, Retry **không nhìn thấy lỗi** để thử lại. Ngược lại:

~~~text
Fallback(Retry(operation))
~~~

Retry có thể thử đủ số lượt trước, sau đó Fallback mới quyết định trả giá trị thay thế.

Tương tự:

~~~text
Timeout(Retry(operation))
~~~

có thể áp thời hạn tổng cho toàn chuỗi retry, trong khi:

~~~text
Retry(Timeout(operation))
~~~

có thể cho mỗi attempt một thời hạn riêng nhưng làm tổng thời gian lớn hơn dự kiến, nếu không có deadline tổng. Đây là mô hình logic; ngữ nghĩa timeout phụ thuộc implementation và loại tác vụ.

Với cache:

~~~text
Cache(Record(operation))
~~~

cache hit bỏ qua Record bên trong. Nếu cần metric cho **mọi yêu cầu kể cả cache hit**, recorder phải đặt ở lớp nhìn thấy mọi lượt yêu cầu hoặc instrument cache hit riêng. Nếu cache chỉ được phép khi operation có thể bỏ qua an toàn, cần đánh giá effect nghiệp vụ trước khi bật cache.

Với breaker:

~~~text
Retry(Breaker(operation))
~~~

retry có thể liên tục bị từ chối bởi breaker đang mở; tạo thêm attempt mà không chạy backend có thể không giúp ích. Với:

~~~text
Breaker(Retry(operation))
~~~

một lần gọi breaker có thể chứa nhiều backend attempts. Điều cần đo là **backend attempt failure** hay **operation failure**; xác định rõ trước khi chọn thứ tự.

Với recorder:

~~~text
RecordFinal(Retry(RecordAttempt(Transaction(operation))))
~~~

Recorder ngoài cùng thấy outcome cuối; recorder trong retry thấy từng attempt sau khi transaction hoàn tất. Nếu RecordAttempt là best effort, lỗi ghi nhận không được làm Retry chạy lại nghiệp vụ đã commit.

**Quy tắc:** vẽ chuỗi advice theo đúng thứ tự thực thi, liệt kê nơi lỗi có thể phát sinh và từng vị trí có thể bỏ qua proceed(), gọi lại proceed() hoặc biến đổi kết quả.

## 17. Ma trận thất bại và effect để review từng chính sách

Trước khi triển khai, lập bảng theo những failure point thực sự của operation:

| Thời điểm thất bại | Transaction | Retry | Timeout | Recorder/Audit | Tác dụng phụ cần xem |
| --- | --- | --- | --- | --- | --- |
| Bị chặn trước proceed() | Thường chưa cần transaction nếu đặt ở ngoài | Không nên retry vô điều kiện | Không nhất thiết có tác vụ nền | Ghi lý do từ chối theo chính sách | Không có effect của operation; advice vẫn có effect |
| Trong validation/business logic | Rollback những thay đổi có tham gia | Chỉ retry lỗi tạm thời và an toàn | Có thể đang chạy đến lúc deadline | Đánh dấu attempt failure, phân biệt business reject | Effect trước lỗi có thể còn tồn tại |
| Tại commit | Kết quả có thể failure hoặc unknown | Không retry mù nếu commit có thể đã thành công | Timeout có thể che outcome commit | Chỉ ghi committed khi xác nhận | Database có thể đã thay đổi |
| Khi publish ra hệ thống ngoài | DB commit không đồng nghĩa publish thành công | Retry publish có thể phát trùng | Timeout send không chứng minh không nhận | Audit/event cần cơ chế bền | Email/message/payment ngoài transaction |
| Khi observer/metrics lỗi | Thường không liên quan rollback business | Không cho lỗi telemetry tự retry business | Ghi log có thể gây nghẽn | Best effort hoặc mandatory phải rõ | Side effect ghi nhận có thể trùng/mất |
| Khi caller timeout/hủy | Tùy transaction manager/tác vụ | Ngừng tạo attempt nếu hết deadline | Có thể dừng chờ nhưng task còn chạy | Phân biệt timeout với actual failure | Effect có thể xảy ra sau phản hồi lỗi |

Một mẫu đánh giá theo policy trong review:

~~~text
Policy: Retry
Join point: submitPayment(...)
Scope: một payment operation
proceed() count: từ 1 đến 3
Retryable failures: các lỗi hạ tầng cụ thể đã được định nghĩa
Non-retryable: sai dữ liệu, từ chối nghiệp vụ
Transactional resource: database payment records
External effects: payment gateway call
Unknown outcome: có thể nếu timeout/loss of acknowledgment
Idempotency: payment request key, enforced at durable boundary
Logging: attempt riêng; final outcome riêng
Cancellation/deadline: không bắt đầu attempt mới sau deadline
QA: fault injection ở trước commit, tại commit và sau send
~~~

Thông tin trên là **một ví dụ phân tích**, không phải cam kết mọi hệ thanh toán đều có cùng quy tắc retry.

## 18. Đo lường tác động: metric đúng đơn vị

Một chính sách có thể làm tăng số attempt mà không làm tăng số operation gốc.

Ví dụ có 100 operation gốc; trong đó 15 operation cần retry thêm đúng một lần và tất cả cuối cùng đều thành công:

- **operations_total = 100**.
- **attempts_total = 115**.
- **operations_failed_total = 0**.
- **attempts_failed_total = 15** nếu mỗi attempt đầu trong nhóm retry thất bại và các attempt khác thành công.
- **operations_retried_total = 15**.

Nếu dùng số attempt lỗi để báo 15% operation cuối thất bại thì sai. Cần recorder ở đúng ranh giới:

- **Invocation duration:** thời gian một lần lời gọi join point trả về/throw.
- **Attempt duration:** thời gian của một attempt, có thể gồm transaction commit/rollback.
- **Operation duration:** thời gian của toàn operation, gồm backoff/retry.
- **Async completion duration:** thời gian đến tín hiệu hoàn tất logic, không phải chỉ đến khi trả về future.

Để tránh metric cardinality quá cao, không sử dụng correlation ID, attempt ID, idempotency key hoặc mã đơn hàng cá biệt làm nhãn time series. Những ID ấy phù hợp để truy vấn log và trace hơn.

## 19. Kiểm thử chính sách bằng fault injection

Để đánh giá đúng ảnh hưởng tới lỗi và side effect, kiểm tra **chuỗi sự kiện và số lần xảy ra**, không chỉ assert rằng kết quả cuối là "OK".

### Những kịch bản tối thiểu

1. **Thành công ngay attempt đầu:** transaction commit một lần; recorder final ghi success một lần.
2. **Lỗi transient trước side effect:** retry theo budget, kết quả cuối và số effect đúng kỳ vọng.
3. **Lỗi sau side effect nhưng trước commit:** xác minh side effect nào được rollback, side effect nào còn tồn tại; nếu retry thì không nhân đôi effect ngoài ý muốn.
4. **Lỗi tại commit:** phân biệt failure chắc chắn và unknown; không gắn nhãn committed success trước thời điểm xác nhận.
5. **Lỗi permanent:** không retry; người gọi thấy nguyên nhân rõ ràng.
6. **Timeout khi tác vụ vẫn chạy:** kiểm tra có effect xuất hiện sau thời điểm timeout không, cancellation được thực thi theo hợp đồng không.
7. **Recorder best effort gặp lỗi:** không làm business chạy lại hay làm mất lỗi gốc.
8. **Cache hit hoặc breaker mở:** proceed() được gọi 0 lần; metric request vẫn được cập nhật nếu cần.
9. **Concurrent requests cùng idempotency key:** chỉ một effect nghiệp vụ được phép theo hợp đồng, các lượt còn lại nhận kết quả hoặc conflict nhất quán.
10. **Transaction có sẵn ở lớp ngoài:** xác minh chính sách propagation, rollback-only và liệu Retry có thực sự dùng transaction mới.
11. **Async completion thất bại:** advice phải ghi outcome dựa trên completion signal nếu mục tiêu là theo dõi toàn operation.
12. **Nhiều aspect cùng match:** kiểm tra precedence/order được cấu hình thật, kể cả các đường gọi bypass instrumentation.

### Sổ sự kiện cho kiểm thử

Có thể tiêm một recorder đơn giản chỉ tích lũy event trong test:

~~~text
[
  "attempt=1",
  "tx=1 begin",
  "business=1",
  "external-effect=1",
  "tx=1 rollback",
  "attempt=2",
  "tx=2 begin",
  "business=2",
  "external-effect=2",
  "tx=2 commit",
  "final=success"
]
~~~

Sau đó assert **thứ tự**, **số lần side effect xảy ra**, **số transaction commit/rollback** và **outcome đúng cấp**. Với transaction thật, vẫn cần integration test với resource manager; một fake wrapper in sự kiện không chứng minh được rollback, lock và commit semantics của database thực.

### Bảng rủi ro để quyết định

Không nên chỉ gán “pass” cho toàn operation. Với mỗi effect, phân tích mức độ nghiêm trọng:

| Effect | Nếu lặp | Nếu mất | Nếu xảy ra sau timeout | Biện pháp |
| --- | --- | --- | --- | --- |
| Đọc dữ liệu | Thường tăng chi phí | Có thể thiếu kết quả | Có thể hao tài nguyên | Timeout, cache, rate limit |
| Ghi số dư/thanh toán | Có thể mất tiền hoặc thu trùng | Không hoàn tất nghĩa vụ | Người dùng thấy lỗi nhưng đã bị trừ tiền | Idempotency, reconciliation, giao dịch thích hợp |
| Gửi email/thông báo | Spam/thông báo trùng | Người dùng không nhận | Nhầm tình trạng thao tác | Outbox, event ID và consumer deduplication |
| Ghi audit bắt buộc | Trùng bản ghi gây sai phân tích | Mất dấu vết yêu cầu | Audit đến muộn/khó đối chiếu | Lưu bền, unique key, xử lý lỗi riêng |
| Log/metric chẩn đoán | Làm sai đếm hoặc tăng chi phí | Thiếu khả năng quan sát | Timestamp không phản ánh outcome chính | Phân biệt attempt/final, best effort, sampling hợp lý |

Bảng rủi ro là gợi ý kỹ thuật: mức độ và giải pháp cụ thể tùy nghiệp vụ, yêu cầu độ bền và hợp đồng với hệ thống ngoài.

## 20. Cách thiết kế mỗi policy an toàn và minh bạch

Một quy trình review thực dụng:

**Bước 1 — Định nghĩa outcome trước.** Thành công là method return, transaction committed, business approved hay async completion? Timeout/unknown có được phép là trạng thái riêng không?

**Bước 2 — Chỉ rõ tất cả effect.** Liệt kê database writes, messages, emails, payments, files, audit, cache writes và metric. Đánh dấu effect nào có thể rollback và effect nào cần idempotency hoặc compensation.

**Bước 3 — Ghi nhận thuộc tính advice.** Policy có thể gọi proceed() bao nhiêu lần, chặn proceed(), tạo lỗi mới hoặc đổi giá trị trả về không? Các điều kiện nào khiến policy bỏ qua hành vi đích?

**Bước 4 — Vẽ cây order.** Viết dạng Outer(Inner(operation)); đánh dấu các mốc entry, return, commit, rollback, retry và callback completion.

**Bước 5 — Kiểm tra failure paths.** Với mỗi nơi có thể lỗi, xem lớp nào bắt được lỗi, có retry không, effect nào đã xảy ra, recorder ghi ở cấp nào và người gọi nhận trạng thái gì.

**Bước 6 — Kiểm tra concurrency và async.** Transaction/context có đúng phạm vi thread/task không? Timeout có thực sự hủy task không? Callback có thể chạy sau khi lời gọi gốc return không?

**Bước 7 — Xác định chính sách cho uncertainty.** Khi không biết commit/publish đã diễn ra chưa, ưu tiên đối soát và idempotency thay cho lặp vô điều kiện.

**Bước 8 — Kiểm thử và đo lường.** Fault injection ở từng điểm, so chuỗi sự kiện và số effect. Khi chạy thực tế, metric về retry, commit failure, timeout và duplicate prevention giúp phát hiện policy gây tác hại.

## 21. Các lỗi thường gặp trong áp dụng AOP

**Lỗi 1: Gọi proceed() nhiều lần trong retry mà không đánh giá effect.** Kết quả là payment/message có thể bị nhân đôi. Cần phân loại lỗi và idempotency tại nơi tạo effect.

**Lỗi 2: Coi rollback database là rollback cả email hoặc HTTP.** Tác dụng phụ ngoài transaction vẫn còn; thiết kế outbox/deduplication hoặc compensation phù hợp.

**Lỗi 3: Lấy return bình thường làm bằng chứng commit.** Commit có thể thất bại sau khi hành vi đích return. Recorder committed-success phải quan sát ranh giới commit thật.

**Lỗi 4: Đặt recorder có thể throw trong retry.** Lỗi telemetry có thể khiến nghiệp vụ đã commit bị chạy lại. Cần cô lập lỗi best effort hoặc thiết kế recorder bắt buộc như một nghiệp vụ riêng.

**Lỗi 5: Giả định timeout là cancellation thành công.** Task có thể tiếp tục; retry có thể chồng lên task cũ. Cần xác nhận chính sách hủy và idempotency.

**Lỗi 6: Dùng fallback che mọi exception.** Lỗi nghiêm trọng bị biến thành dữ liệu có vẻ hợp lệ. Chỉ fallback với loại lỗi/hợp đồng được phép và ghi nhận backend failure tách biệt.

**Lỗi 7: Cache một hành vi có effect bắt buộc.** Cache hit bỏ qua operation nên effect bị mất. Chỉ cache phù hợp với semantics hoặc đưa effect bắt buộc ra ngoài nhánh cache.

**Lỗi 8: Đếm attempt failure thành operation failure.** Metric sai vì một operation có thể phục hồi. Đo cả hai cấp và giữ denominator nhất quán.

**Lỗi 9: Dùng ThreadLocal làm context vĩnh viễn của tác vụ async.** Thread được tái sử dụng, callback chạy trên thread khác. Capture/restore context theo scope thực thi; không kéo dài ThreadLocal của thread caller đến completion.

**Lỗi 10: Không xác minh aspect có thực sự intercept join point.** Một số đường gọi có thể bỏ qua interception theo mô hình AOP đang dùng. Kiểm tra weaving, precedence và đường gọi thực tế bằng test.

## 22. Bài tập tự kiểm tra

**Câu 1.** Retry(Transaction(operation)) có hai lượt, lượt đầu gọi dịch vụ thanh toán ngoài rồi lỗi database và rollback. Lượt sau thành công. Rollback có chắc loại bỏ lần gọi thanh toán đầu không?

**Đáp án:** Không. Nếu dịch vụ thanh toán không tham gia transaction, hiệu ứng đầu vẫn có thể tồn tại. Cần idempotency/đối soát theo ý định thanh toán.

**Câu 2.** Fallback(Retry(operation)) khác Retry(Fallback(operation)) ở điểm nào?

**Đáp án:** Với Fallback ở ngoài, Retry có thể thử đủ policy rồi Fallback mới biến lỗi cuối thành kết quả thay thế. Với Fallback ở trong, lỗi có thể bị chuyển thành kết quả ngay từng attempt, khiến Retry không còn thấy lỗi để tiếp tục.

**Câu 3.** Transaction(Retry(operation)) bắt được lỗi transient ở attempt 1 rồi tiếp tục trong cùng transaction. Có bảo đảm commit được không?

**Đáp án:** Không. Một số lỗi làm transaction bị aborted/rollback-only. Cần rollback và tạo transaction mới nếu policy yêu cầu, hoặc cơ chế savepoint được hỗ trợ cho chính lỗi đó.

**Câu 4.** Một circuit breaker đang mở trả lỗi ngay. Log “backend xử lý rồi thất bại” có đúng không?

**Đáp án:** Không. proceed() có thể chưa được gọi. Cần ghi outcome “short-circuited/rejected” và đo riêng tỷ lệ lỗi backend thật.

**Câu 5.** Advice gọi logger, logger ném exception sau khi operation đã commit. Retry ở ngoài nhìn thấy exception. Rủi ro?

**Đáp án:** Operation có thể bị gọi lại chỉ vì logger lỗi, tạo effect trùng. Observer best effort phải cô lập lỗi; audit bắt buộc cần hợp đồng transaction/idempotency riêng.

**Câu 6.** Future bị timeout ở phía caller, nhưng server đã ghi bản ghi. Outcome nên gắn nhãn “chắc chắn rollback” không?

**Đáp án:** Không. Timeout phía caller không chứng minh server rollback. Cần trạng thái unknown/in-doubt nếu chưa biết, và cơ chế kiểm tra kết quả theo khóa ổn định.

**Câu 7.** Một endpoint được cache trả kết quả trực tiếp mà không chạy nghiệp vụ. Điều gì cần xác minh?

**Đáp án:** Nghiệp vụ đích phải an toàn khi bỏ qua, dữ liệu cache phải đúng ngữ cảnh quyền/tenant và freshness; các effect bắt buộc không được đặt duy nhất trong nhánh bị cache bỏ qua.

**Câu 8.** Có 100 operation, 15 operation retry một lần rồi đều thành công, các operation khác thành công ngay. Số attempt và số operation thất bại cuối là bao nhiêu?

**Đáp án:** 115 attempts, 0 operation thất bại cuối. Nếu 15 attempt đầu trong nhóm retry đều ném lỗi, có 15 attempt failures, nhưng đó không phải final failures.

## 23. Ghi nhớ

Đánh giá một chính sách AOP phải bắt đầu từ **ranh giới thực thi, phân loại lỗi và danh sách tác dụng phụ**, sau đó xác định advice có thể gọi hoặc bỏ qua proceed() thế nào và tương tác ra sao với các advice khác.

- Transaction chỉ bảo đảm trong phạm vi tài nguyên tham gia và có thể có commit không chắc chắn.
- Retry có thể biến lỗi attempt thành success cuối nhưng cũng có thể nhân đôi side effect.
- Timeout/hủy không chứng minh task hoặc effect đã dừng.
- Cache, circuit breaker, rate limit, authorization và fallback có thể bỏ qua hoàn toàn hành vi đích hoặc đổi outcome người gọi quan sát được.
- Recorder phải phân biệt attempt với operation và không để lỗi telemetry tự gây retry nghiệp vụ.
- Idempotency, outbox, deduplication, compensation và reconciliation xử lý những rủi ro mà thứ tự advice đơn thuần không thể xóa bỏ.

Một policy chỉ được xem là đúng khi **lỗi được truyền hoặc biến đổi theo hợp đồng**, **tác dụng phụ được kiểm soát kể cả khi retry/timeout/crash**, và **log/metric phản ánh chính xác những gì thực sự đã xảy ra**.

</details>

- [Quay lại đầu trang](#back-to-top)
