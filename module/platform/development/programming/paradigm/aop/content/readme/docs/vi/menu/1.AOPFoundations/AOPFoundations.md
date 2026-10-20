<a id="back-to-top"></a>

# Tư duy AOP

## Menu
- [Lập trình hướng khía cạnh (AOP) là gì](#aop-foundations-001)
- [Mối quan tâm cốt lõi và mối quan tâm cắt ngang](#aop-foundations-002)
- [Mã bị phân tán (scattering) và mã bị đan xen (tangling)](#aop-foundations-003)
- [Vì sao ghi nhật ký, đo thời gian hoặc kiểm soát truy cập dễ lặp lại](#aop-foundations-004)
- [Tách hành vi cắt ngang khỏi luồng nghiệp vụ](#aop-foundations-005)
- [Quan hệ giữa AOP, lập trình thủ tục và lập trình hướng đối tượng](#aop-foundations-006)
- [So sánh AOP với hàm dùng chung, đối tượng cộng tác và thiết kế theo thành phần](#aop-foundations-007)
- [Lợi ích, đánh đổi và những bài toán không nên dùng AOP](#aop-foundations-008)
- [Lộ trình từ khái niệm đến cơ chế và ứng dụng](#aop-foundations-009)

## <a id="aop-foundations-001">Lập trình hướng khía cạnh (AOP) là gì</a>

<details>
<summary>Xem chi tiết</summary>

# Lập trình hướng khía cạnh (AOP) là gì?

**Aspect-Oriented Programming (AOP)**, hay *lập trình hướng khía cạnh*, là cách tổ chức phần mềm để gom và áp dụng những hành vi xuất hiện ở nhiều nơi — chẳng hạn ghi log, đo thời gian, kiểm tra quyền truy cập, theo dõi lỗi — quanh các điểm thực thi được chọn. Những hành vi ấy thường được gọi là **mối quan tâm xuyên suốt** (*cross-cutting concerns*). AOP bổ sung cho cách tổ chức chương trình theo lớp, đối tượng và hàm; mục tiêu là giữ logic nghiệp vụ dễ đọc, đồng thời làm cho quy tắc chung nhất quán và có nơi quản lý rõ ràng.

Một cách hình dung: phương thức `transferMoney()` chịu trách nhiệm chuyển tiền. Việc ghi nhận thời gian chạy hoặc lưu vết một yêu cầu có thể cần thiết nhưng không tạo nên nghiệp vụ “chuyển tiền”. Thay vì sao chép mã ghi log vào mọi phương thức, ta định nghĩa quy tắc “bao quanh các lần thực thi đã chọn” để hệ thống tự áp dụng hành vi chung.

> **Phân biệt ngay từ đầu:** AOP là một mô hình lập trình; không đồng nghĩa với một thư viện, một cơ chế chặn lời gọi, hay một cú pháp cụ thể. Khả năng chặn những điểm thực thi nào, và thời điểm kết hợp mã AOP vào chương trình, phụ thuộc công cụ triển khai.

## 1. Vì sao AOP xuất hiện?

Giả sử ứng dụng có các nghiệp vụ tạo đơn hàng, thanh toán và tìm kiếm. Cả ba đều cần ghi log thời gian xử lý:

```java
// Mã minh họa: phần ghi thời gian bị lặp trong nhiều phương thức.
long start = System.nanoTime();
try {
    return executeBusinessOperation();
} finally {
    long elapsedNanos = System.nanoTime() - start;
    recordDuration(elapsedNanos);
}
```

Nếu viết đoạn này trong hàng chục phương thức, một thay đổi về định dạng log, chính sách loại trừ dữ liệu nhạy cảm hoặc cách tính số liệu buộc ta sửa nhiều chỗ. Cấu trúc code sẽ có hai lớp trách nhiệm đan vào nhau: **làm việc nghiệp vụ** và **thực hiện chính sách kỹ thuật dùng chung**.

Hai triệu chứng điển hình:

- **Scattering (phân tán):** cùng một quan tâm xuất hiện ở nhiều lớp hoặc phương thức.
- **Tangling (đan xen):** một phương thức chứa cả nghiệp vụ và nhiều xử lý phụ trợ, làm khó hiểu luồng chính.

AOP giải quyết chúng bằng cách mô tả “làm gì” và “làm ở đâu” tương đối độc lập. Ví dụ: “trước và sau khi chạy các phương thức xử lý đơn hàng, ghi mốc thời gian và kết quả”. Việc kết hợp hai phần này được gọi là *weaving*.

Tuy vậy, **không phải mọi đoạn mã dùng lại nhiều lần đều nên thành aspect**. Phép tính giá, thuật toán xác thực chữ ký hoặc quy tắc nghiệp vụ có đầu vào/đầu ra rõ ràng thường phù hợp với hàm, lớp, đối tượng dịch vụ hay thành phần được gọi tường minh. AOP có ích nhất khi hành vi cần bám theo *ranh giới thực thi* của nhiều vị trí.

## 2. Mối quan hệ giữa OOP và AOP

**Lập trình hướng đối tượng (OOP)** tổ chức dữ liệu và hành vi theo các đối tượng, trách nhiệm và mối quan hệ. Ví dụ, `OrderService` chứa quy trình đặt hàng và `OrderRepository` lưu dữ liệu đơn hàng. Điều này vẫn là nền tảng thiết kế.

**AOP** cung cấp thêm một cách mô-đun hóa theo *quy tắc áp dụng xuyên qua nhiều đối tượng*. Một aspect về đo thời gian có thể tác động lên các phương thức được chọn từ nhiều lớp. AOP không làm biến mất các lớp nghiệp vụ và cũng không thay thế interface, composition hay dependency injection.

| Câu hỏi | Tổ chức thông thường | Tổ chức theo AOP |
| --- | --- | --- |
| “Phần mềm thực hiện nghiệp vụ gì?” | Phương thức/lớp thực hiện nghiệp vụ | Vẫn do phương thức/lớp thực hiện |
| “Tất cả thao tác này cần ghi log như thế nào?” | Từng nơi gọi hàm ghi log hoặc wrapper | Aspect chọn điểm áp dụng và gắn lời khuyên |
| “Luồng chạy có nhìn thấy toàn bộ trong mã nghiệp vụ?” | Thường tường minh hơn | Có thể bị bổ sung hành vi bên ngoài |
| “Tác động ở vị trí nào?” | Vị trí gọi hàm trực tiếp | Do tập điểm thực thi được chọn quyết định |

Đổi lại khả năng tách biệt, AOP có chi phí về **khả năng quan sát luồng điều khiển**: đọc riêng một phương thức có thể chưa thấy tất cả công việc diễn ra trước, sau hoặc quanh phương thức đó. Bởi vậy aspect cần có phạm vi rõ, tên dễ hiểu và cách kiểm chứng được.

## 3. Sáu thuật ngữ cốt lõi

### 3.1. Join point — điểm có thể tham gia

**Join point** là một sự kiện/vị trí được mô hình AOP cho phép tác động: thường gặp nhất là *thực thi phương thức*. Tùy công cụ, nó còn có thể là *lời gọi phương thức*, khởi tạo đối tượng, truy cập trường dữ liệu, bắt ngoại lệ hoặc các điểm khác.

Ví dụ, việc đi vào thân `PaymentService.pay()` có thể là một join point kiểu **method execution**. Trong một số mô hình, vị trí mã thực hiện lệnh `paymentService.pay()` cũng có thể là một join point kiểu **method call**. Hai khái niệm này khác nhau: execution thuộc lần chạy của phương thức; call thuộc chỗ phát sinh lời gọi. Không phải công cụ AOP nào cũng hỗ trợ cả hai.

### 3.2. Pointcut — quy tắc chọn join point

**Pointcut** mô tả tập join point mà aspect cần xử lý. Một pointcut có thể dựa vào tên, kiểu, chữ ký, annotation, phạm vi package hoặc điều kiện thực thi; mức hỗ trợ phụ thuộc kỹ thuật weaving.

Ví dụ ở mức ý tưởng:

- Chọn các lần thực thi phương thức công khai thuộc nhóm thao tác thanh toán.
- Chọn những phương thức được đánh dấu bằng annotation `@Audited`.
- Chọn các thao tác có ngữ cảnh yêu cầu hợp lệ để thu thập trace.

**Pointcut là bộ lọc; join point là một điểm thực thi cụ thể.** Cú pháp `execution(...)` hoặc các biểu thức tương tự là cú pháp của từng công cụ, không phải cú pháp bắt buộc của toàn bộ AOP.

### 3.3. Advice — hành vi bổ sung

**Advice** là đoạn xử lý chạy liên quan đến join point đã chọn. Các loại phổ biến:

| Loại advice | Thời điểm/ý nghĩa |
| --- | --- |
| **Before** | Chạy trước thao tác được chọn. |
| **After returning** | Chạy khi thao tác hoàn thành bình thường; có thể xem kết quả nếu công cụ hỗ trợ. |
| **After throwing** | Chạy khi thao tác kết thúc bằng ngoại lệ; phạm vi loại ngoại lệ bắt được tùy công cụ. |
| **After/finally** | Chạy khi rời thao tác, dù bình thường hay có ngoại lệ. |
| **Around** | Bao quanh thao tác, có thể chủ động quyết định khi nào tiếp tục, có gọi tiếp hay không, và kết quả/ngoại lệ trả ra. |

Cần phân biệt **after returning** với **finally**: một lời gọi ném lỗi sẽ không đi qua nhánh “trả về bình thường”, nhưng vẫn có thể đi qua phần dọn dẹp “finally”.

### 3.4. Aspect — đơn vị đóng gói quy tắc

Một **aspect** thường kết hợp pointcut với một hoặc nhiều advice để thể hiện một quan tâm xuyên suốt, ví dụ `ExecutionTiming` hoặc `AuditTrail`. Nó là đơn vị tổ chức mã/khai báo, không nhất thiết là kiểu class đặc biệt trong mọi triển khai.

### 3.5. Target — đối tượng/thao tác đích

**Target** là phần chương trình gốc mà advice được áp dụng lên. Nếu một proxy đứng trước `InventoryService`, service thật thường là target; proxy đóng vai trò trung gian chuyển tiếp lời gọi. Khi weaving ở mức bytecode, có thể không tồn tại một đối tượng proxy độc lập.

### 3.6. Weaving — kết hợp hành vi

**Weaving** là quá trình gắn advice vào các join point được chọn. Có thể hình dung:

```text
Người gọi
   |
   v
Điểm thực thi được chọn
   |
   +--> Advice trước
   |
   +--> Logic nghiệp vụ gốc
   |
   +--> Advice sau / xử lý lỗi / dọn dẹp
   |
   v
Kết quả hoặc ngoại lệ quay về người gọi
```

Sơ đồ chỉ là trường hợp đơn giản. Với **around advice**, logic nghiệp vụ có thể không chạy, chạy một lần, hoặc (nếu thiết kế cho phép) được gọi nhiều lần; các thành phần có thể được lồng nhau và thay đổi thứ tự thực tế.

## 4. AOP vận hành như thế nào?

Một đường đi phổ biến khi có nhiều advice:

1. Chương trình đi đến một join point, chẳng hạn gọi một thao tác của đối tượng.
2. Cơ chế AOP xác định join point này có khớp pointcut hay không.
3. Các advice áp dụng được xếp thành chuỗi theo quy tắc thứ tự của hệ thống.
4. Advice ở phía ngoài thực thi phần **before**, sau đó chuyển tiếp vào thành phần kế tiếp hoặc nghiệp vụ gốc.
5. Khi thành phần bên trong hoàn tất, phần **after returning**, **after throwing** hoặc **finally** được thực thi tương ứng.
6. Người gọi nhận kết quả cuối cùng hoặc ngoại lệ đã được chuyển tiếp/biến đổi.

Ví dụ một chuỗi lồng nhau:

```text
Caller
  -> Audit around: bắt đầu
      -> Timing around: bắt đầu
          -> Business operation
      <- Timing around: kết thúc
  <- Audit around: kết thúc
Caller nhận kết quả
```

Ở đây `Audit` và `Timing` cùng áp dụng lên một thao tác. Vì advice có thể lồng nhau, **thứ tự quyết định** việc đo thời gian bao gồm hay không bao gồm chi phí ghi audit. Không có một thứ tự advice phổ quát áp dụng cho mọi thư viện; phải xem quy tắc precedence/order của cơ chế đang dùng.

### Ví dụ nghiệp vụ: đo thời gian một thao tác

Tưởng tượng `calculateQuote()` chỉ quan tâm công thức tính báo giá. Một aspect đo thời gian có thể:

- Ghi mốc `System.nanoTime()` ngay trước khi chuyển tiếp thao tác.
- Cho phép thao tác chạy và giữ nguyên giá trị trả về.
- Trong nhánh `finally`, tính độ dài khoảng thời gian bằng nanosecond.
- Gửi metric với tên thao tác đã được chuẩn hóa và thông tin ngữ cảnh cho phép.

**Vì sao dùng `System.nanoTime()`?** Đây là đồng hồ phù hợp để đo khoảng thời gian đã trôi qua trong cùng tiến trình; không sử dụng để suy ra giờ lịch hoặc dấu thời gian UTC. Đo “toàn bộ lời gọi” cũng không đồng nghĩa “chỉ thời gian CPU”, và metric tổng thể có thể bao gồm thời gian chờ I/O hoặc xử lý của advice khác.

## 5. Những cách triển khai AOP

AOP là ý tưởng; mỗi kỹ thuật triển khai đánh đổi phạm vi join point, thời điểm can thiệp và chi phí vận hành.

| Kỹ thuật | Diễn ra lúc nào? | Đặc trưng |
| --- | --- | --- |
| **Compile-time weaving** | Khi biên dịch | Kết hợp advice vào mã đầu ra; có thể tác động ở nhiều vị trí theo công cụ biên dịch. |
| **Post-compile/binary weaving** | Sau khi đã có bytecode, trước khi chạy | Biến đổi artifact đã biên dịch; cần quản lý tương thích bản build. |
| **Load-time weaving** | Khi JVM nạp class | Có thể dùng bộ biến đổi bytecode/class loader/agent; cần bảo đảm quy tắc nạp lớp và cấu hình nhất quán. |
| **Runtime proxy/interception** | Khi lời gọi đi qua lớp trung gian lúc chạy | Dễ hình dung; chỉ bắt được các lời gọi thực sự đi qua điểm trung gian đó. |

### 5.1. Proxy và giới hạn của nó

Một **proxy** là đối tượng nhận lời gọi thay cho target, thực hiện hành vi bổ sung rồi chuyển tiếp. Trong Java, `java.lang.reflect.Proxy` tạo proxy động dựa trên **interface**. Nếu đối tượng gọi trực tiếp target hoặc một phương thức khác gọi nội bộ qua `this` mà không quay qua proxy, interceptor bên ngoài có thể không được kích hoạt.

Ví dụ:

```text
caller -> proxy -> target.publicOperation()    [được trung gian quan sát]

caller -> target.publicOperation()             [bỏ qua proxy]

target.publicOperation() -> this.helper()      [lời gọi nội bộ không quay qua proxy]
```

Với dynamic proxy dựa trên interface, chỉ những phương thức của giao diện proxy được gọi qua proxy mới đi vào handler. Cách tạo lớp con trung gian có giới hạn khác, chẳng hạn không thể override một phương thức `final`. Weaving ở mức bytecode có thể bao phủ nhiều tình huống hơn, nhưng không mặc nhiên hỗ trợ mọi join point hay miễn phí về hiệu năng.

**Bài học thiết kế:** không kết luận “đã khai báo aspect” là “mọi lời gọi liên quan đều bị chặn”. Phải hiểu cơ chế kết hợp và đường gọi thực tế.

### 5.2. Interception và instrumentation khác nhau thế nào?

- **Interception** tập trung vào việc chặn/chuyển tiếp lời gọi tại một ranh giới; proxy là một cách làm.
- **Instrumentation** là bổ sung mã/quan sát vào chương trình, có thể bằng bytecode transformation hoặc các cơ chế khác.
- **AOP** là mô hình biểu diễn các mối quan tâm và điểm áp dụng. Một công cụ AOP có thể dùng interception hoặc instrumentation để hiện thực.

Ba khái niệm có liên hệ nhưng không đồng nghĩa.

## 6. Ví dụ Java 17+ chạy được: AOP tối giản bằng dynamic proxy

Ví dụ dưới đây tự xây một lớp advice quanh lời gọi phương thức bằng API chuẩn Java. Nó **minh họa Before, After returning, After throwing và Finally**. Đây là một cách thực hiện interception nhỏ gọn; chưa phải một ngôn ngữ pointcut hay công cụ weaving hoàn chỉnh.

Lưu thành tệp `AopBasicsDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AopBasicsDemo {
    public interface GreetingService {
        String greet(String name);
        void reject();
    }

    public static class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "Xin chào, " + name;
        }

        @Override
        public void reject() {
            throw new IllegalArgumentException("Từ chối thao tác");
        }
    }

    public static void main(String[] args) {
        GreetingService target = new GreetingServiceImpl();

        GreetingService service = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(),
                new Class<?>[]{GreetingService.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return callTarget(method, target, arguments);
                    }

                    System.out.println("[before] " + method.getName());
                    try {
                        Object result = callTarget(method, target, arguments);
                        System.out.println("[after-returning] " + method.getName());
                        return result;
                    } catch (Throwable error) {
                        System.out.println("[after-throwing] " + method.getName());
                        throw error;
                    } finally {
                        System.out.println("[finally] " + method.getName());
                    }
                });

        System.out.println(service.greet("Lan"));

        try {
            service.reject();
        } catch (IllegalArgumentException error) {
            System.out.println("[caller] " + error.getMessage());
        }
    }

    private static Object callTarget(
            Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp (terminal lưu tệp bằng UTF-8):

```bash
javac -encoding UTF-8 AopBasicsDemo.java
java AopBasicsDemo
```

Kết quả chuẩn:

```text
[before] greet
[after-returning] greet
[finally] greet
Xin chào, Lan
[before] reject
[after-throwing] reject
[finally] reject
[caller] Từ chối thao tác
```

Giải thích từng điểm:

1. `GreetingServiceImpl` là **target** chứa nghiệp vụ thật.
2. Đối tượng do `Proxy.newProxyInstance(...)` trả về là **proxy** triển khai interface `GreetingService`; người gọi làm việc với proxy.
3. `InvocationHandler` đóng vai trò **around interceptor**: in dòng before, gọi phương thức gốc, xử lý hai kết thúc thành công/thất bại rồi chạy finally.
4. `Method.invoke(...)` bọc ngoại lệ ném từ phương thức đích trong `InvocationTargetException`. `callTarget` mở lớp bọc để người gọi nhận lại `IllegalArgumentException` ban đầu.
5. `method.getDeclaringClass() == Object.class` tránh gắn log demo vào các lời gọi `Object` như `toString()`. Trong hệ thống thực, cách xử lý `equals`/`hashCode`/`toString` phải được thiết kế nhất quán.

Ví dụ cố ý áp dụng handler cho mọi phương thức interface, tức **pointcut rất rộng**. Một ứng dụng thực tế có thể kiểm tra metadata của `method` để chỉ xử lý tập phương thức cần thiết. **Không suy ra** rằng mọi công cụ AOP dùng reflection, và ví dụ không minh họa weaving vào lời gọi nội bộ hoặc truy cập trường.

## 7. Thiết kế pointcut sao cho đúng phạm vi

Pointcut tốt phải đủ ổn định khi mã được tái cấu trúc. Một số chiến lược:

- **Theo chữ ký hoặc kiểu:** phù hợp khi các thao tác được chọn có một giao diện/contract rõ ràng. Cần chú ý overload và phương thức được kế thừa.
- **Theo annotation:** dễ diễn đạt ý định như “thao tác này cần audit”; nhưng phải quy định annotation có hiệu lực ở đâu, có được kế thừa không và công cụ đọc annotation trên interface hay implementation.
- **Theo package/module:** thuận tiện cho chính sách phủ rộng, nhưng dễ kéo cả phương thức không mong muốn vào phạm vi khi tổ chức package thay đổi.
- **Theo điều kiện tại thời điểm chạy:** có thể cần để xét đối số, trạng thái hoặc ngữ cảnh; tốn chi phí đánh giá và phải kiểm soát tính nhất quán khi chạy song song.

Điểm cần kiểm tra là **khớp thừa** (*false positive*, advice chạy ở nơi không cần) và **khớp thiếu** (*false negative*, nơi cần nhưng không có advice). Tên phương thức chỉ là một tín hiệu; việc đặt tên lại có thể vô tình phá quy tắc nếu pointcut phụ thuộc vào tên.

Một cách kiểm thử hữu ích: lập bảng các phương thức *phải* được áp dụng và *không được* áp dụng, gọi qua đúng đường đi thực tế, rồi xác minh advice có/không chạy tương ứng.

## 8. Mức nâng cao: around advice và chuỗi advice

**Around advice** có sức mạnh lớn vì nó kiểm soát việc tiếp tục thao tác. Pseudocode sau mô tả mô hình chung, **không phải mã Java độc lập**:

```text
around(joinPoint):
    begin = monotonicClock()
    try:
        result = proceed(joinPoint)
        return result
    finally:
        recordDuration(monotonicClock() - begin)
```

Lệnh `proceed` là khái niệm “đi tiếp tới advice bên trong hoặc thao tác gốc”. Tên hàm/cú pháp thay đổi theo công cụ. Nếu không gọi `proceed`, nghiệp vụ có thể không chạy; nếu gọi hai lần, nghiệp vụ có thể thực thi hai lần. Điều đó đặc biệt nguy hiểm với thanh toán, ghi dữ liệu, gửi thông báo và những thao tác có tác dụng phụ.

**Thứ tự và nesting.** Cho hai advice `A` và `B` đều bao quanh `operation`:

```text
A.before
  B.before
    operation
  B.after
A.after
```

Đây là cấu trúc ngăn xếp: phần vào đi từ ngoài vào trong; phần ra đi ngược lại. Nếu `B` ném ngoại lệ, `A` chỉ chạy phần xử lý phù hợp với ngoại lệ/`finally`, không tự động chạy “after returning”.

**Biến đổi kết quả và lỗi.** Một around advice có thể thay thế kết quả, ánh xạ ngoại lệ, chặn thao tác hoặc thử lại. Nhưng quyền kỹ thuật không đồng nghĩa hành vi ấy luôn đúng. Chẳng hạn, retry một thao tác không idempotent có thể tạo tác dụng phụ trùng lặp. Khi một aspect thay đổi semantics của nghiệp vụ, contract phải mô tả rõ để người dùng API và người viết test biết điều gì có thể xảy ra.

**Tái nhập và đệ quy.** Nếu advice ghi log thông qua một phương thức cũng khớp chính pointcut của nó, hệ thống có thể ghi log lặp vô hạn. Thiết kế có thể giới hạn phạm vi pointcut, loại trừ thành phần hạ tầng của advice, hoặc kiểm soát guard theo ngữ cảnh. Tránh dùng biến static chung để đánh dấu “đang chạy” trong môi trường đa luồng.

## 9. Những mối quan tâm phù hợp với AOP

### 9.1. Logging, tracing và metrics

Aspect có thể tạo một luồng quan sát nhất quán: tên thao tác, trạng thái thành công/thất bại, thời gian chạy và mã tương quan. Tuy nhiên:

- Không tự động ghi toàn bộ đối số hoặc giá trị trả về: chúng có thể chứa mật khẩu, token hoặc dữ liệu cá nhân.
- Không đưa giá trị có số lượng khác biệt không giới hạn (như ID từng đơn hàng) vào nhãn metric, vì có thể tạo **cardinality** rất lớn.
- Thời gian đo tại ranh giới aspect là thời gian quan sát được từ ranh giới đó; chưa hẳn phản ánh riêng thời gian xử lý lõi.
- Khi chuyển luồng hoặc chạy bất đồng bộ, ngữ cảnh trace có thể cần được truyền rõ ràng; dữ liệu gắn với thread không luôn đi theo công việc.

### 9.2. Kiểm soát quyền truy cập

Advice có thể kiểm tra chính sách trước khi cho phép một thao tác tiếp tục. Tuy nhiên, **ranh giới bảo mật phải thật sự bao phủ mọi đường truy cập**. Nếu có đường gọi bỏ qua proxy hoặc một endpoint không khớp pointcut, kiểm tra quyền chỉ đặt ở aspect có thể bị né tránh. Những điều kiện bảo mật cốt lõi cần được áp dụng tại điểm tin cậy phù hợp và được kiểm thử theo các đường gọi thực tế.

### 9.3. Quản lý tài nguyên, giao dịch và retry

Các bước mở/đóng ngữ cảnh, đo thời gian, thực hiện chính sách giao dịch hoặc retry có thể được bao quanh thao tác. Cần xác định chính xác **phạm vi commit/rollback**, điều kiện lỗi, idempotency, thời hạn chờ và hành vi khi hủy tác vụ. Không mặc định rằng “đặt advice trước/sau” tự bảo đảm tính nguyên tử giao dịch hay thu hồi tài nguyên đúng trong mọi trường hợp.

### 9.4. Audit và tuân thủ

Audit ghi nhận *ai thực hiện thao tác nào, khi nào, kết quả gì* ở ranh giới được chọn. Audit khác log chẩn đoán: có thể cần tính toàn vẹn, truy vết và quy tắc lưu giữ riêng. Nếu audit là yêu cầu bắt buộc, phải cân nhắc điều gì xảy ra khi ghi audit thất bại và liệu nghiệp vụ có được phép hoàn tất hay không.

## 10. Các giới hạn và lỗi thường gặp

1. **Cho rằng mọi phương thức đều là join point.** Tập join point phụ thuộc cơ chế triển khai; proxy dựa trên interface khác weaving bytecode.
2. **Gọi thẳng target nên bỏ qua advice.** Khi dùng proxy, lời gọi phải đi qua proxy. Lời gọi nội bộ qua `this` cũng có thể bỏ qua interceptor.
3. **Dùng pointcut quá rộng.** Phương thức không liên quan bị ghi log, bị đo hoặc bị thay đổi hành vi, gây nhiễu và hao tài nguyên.
4. **Giấu quy tắc nghiệp vụ cốt lõi vào aspect.** Người bảo trì không nhìn thấy điều kiện thiết yếu ngay ở nơi sử dụng; behavior trở nên khó dự đoán.
5. **Bắt và nuốt ngoại lệ.** Advice khiến API báo thành công giả hoặc làm mất stack trace. Chỉ biến đổi ngoại lệ khi đó là contract có chủ đích.
6. **Dùng around advice nhưng quên tiếp tục.** Nghiệp vụ không chạy hoặc chạy nhiều lần; đặc biệt nguy hiểm với thao tác có tác dụng phụ.
7. **Bỏ qua precedence.** Hai advice thay đổi cách đo thời gian, ngữ cảnh bảo mật hoặc transaction boundary, tạo kết quả khác nhau dù cùng pointcut.
8. **Ghi dữ liệu nhạy cảm vào log.** AOP làm logging phủ rộng nên tác hại của một chính sách ghi dữ liệu sai cũng lan rộng.
9. **Lạm dụng reflection/đánh giá điều kiện động.** Advice chạy trên đường nóng có thể làm tăng độ trễ đáng kể; cần đo trên tải thực.
10. **Không kiểm thử hành vi khi lỗi và chạy đồng thời.** Một aspect “đúng lúc thành công” có thể rò ngữ cảnh, bỏ dọn dẹp hoặc làm sai số liệu khi có ngoại lệ và nhiều luồng.

## 11. Khi nào nên và không nên dùng AOP?

**Nên cân nhắc AOP** khi một chính sách thật sự xuyên qua nhiều thành phần, có ranh giới áp dụng rõ, có thể biểu diễn bằng pointcut ổn định, và lợi ích từ việc quản lý tập trung lớn hơn chi phí hiểu luồng chạy. Ví dụ điển hình: đo thời gian cho một nhóm API, thống nhất audit ở ranh giới dịch vụ, hay thu thập trace ở điểm ra/vào.

**Ưu tiên lời gọi tường minh hoặc composition** khi hành vi là bước nghiệp vụ quan trọng, thứ tự thực hiện phải hiển thị ngay trong mã, hoặc quyết định cần được kiểm soát riêng theo từng trường hợp. Một wrapper/decorator bình thường đôi khi đủ để tái sử dụng code mà không cần một cơ chế chọn join point.

Có thể áp dụng cả hai: giữ quy trình nghiệp vụ tường minh, gom thao tác kỹ thuật lặp lại vào thành phần riêng, rồi dùng AOP có phạm vi hẹp để áp dụng chúng ở những ranh giới phù hợp.

### Checklist trước khi đưa một aspect vào dự án

- **Concern:** Quy tắc đang tách ra là hành vi xuyên suốt hay là bước nghiệp vụ thiết yếu?
- **Join points:** Chính xác những đường gọi nào được bao phủ? Có đường nào bỏ qua cơ chế AOP?
- **Pointcut:** Khi refactor, thêm overload hoặc di chuyển package, tập khớp có còn đúng?
- **Advice:** Có cần thay đổi kết quả/ngoại lệ hay chỉ quan sát? Có dùng `finally` để dọn dẹp?
- **Order:** Những advice khác kết hợp theo thứ tự nào, thứ tự ấy có ảnh hưởng semantics không?
- **Vận hành:** Chi phí, recursion, reentrancy, concurrency và dữ liệu nhạy cảm được xử lý ra sao?
- **Kiểm thử:** Đã có ca thành công, lỗi, không khớp pointcut, lời gọi nội bộ và chạy đồng thời chưa?

## 12. Tự luyện tập để hiểu bản chất

Dựa trên `AopBasicsDemo.java` ở trên:

1. Sửa handler để chỉ ghi log cho phương thức `greet` bằng cách kiểm tra `method.getName()`; khi gọi `reject`, xác minh không còn các dòng advice. Đây là **mô phỏng pointcut**, chưa phải bộ diễn giải biểu thức pointcut.
2. Thêm phép đo `System.nanoTime()` trong khối `finally`. Không in một con số thời gian “dự đoán” cố định: thời gian chạy biến thiên từng lần.
3. Gọi trực tiếp `target.greet("Lan")` và so sánh với `service.greet("Lan")` để thấy đường gọi có hoặc không đi qua proxy.
4. Tạo một phương thức trong implementation gọi nội bộ đến phương thức khác; quan sát rằng lần gọi qua `this` không tạo thêm lượt callback của dynamic proxy.
5. Viết test cho hai nhánh thành công và ném lỗi, rồi kiểm tra số lần mỗi advice được gọi và ngoại lệ ban đầu có giữ nguyên không.

**Câu hỏi tự kiểm tra:** “Một pointcut là join point hay điều kiện chọn join point?”, “After returning có chạy khi target ném ngoại lệ không?”, “Tại sao quanh một lời gọi có thể có nhiều advice lồng nhau?”, “Tại sao đi thẳng vào target lại bỏ qua proxy?”, “Khi nào ghi log trong aspect có thể trở thành rủi ro bảo mật?”

## Kết luận

AOP tách các **mối quan tâm xuyên suốt** thành những **aspect** và áp dụng chúng lên **join point** được chọn bằng **pointcut**, thông qua các **advice** được kết hợp bằng cơ chế **weaving** hoặc trung gian tương đương. Sức mạnh của AOP là tăng tính nhất quán cho những chính sách chung; điểm cần kiểm soát là phạm vi khớp, đường gọi thực tế, thứ tự advice và sự thay đổi hành vi khi có lỗi. Hiểu rõ các ranh giới đó quan trọng hơn việc thuộc cú pháp của bất kỳ thư viện AOP cụ thể nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-002">Mối quan tâm cốt lõi và mối quan tâm cắt ngang</a>

<details>
<summary>Xem chi tiết</summary>

# Mối quan tâm cốt lõi và mối quan tâm cắt ngang trong AOP

Một chương trình vừa phải **làm đúng nghiệp vụ**, vừa phải đáp ứng những yêu cầu vận hành như theo dõi, kiểm soát truy cập, kiểm toán và xử lý lỗi. Lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP) giúp ta suy nghĩ rõ hơn về hai nhóm mối quan tâm đó, nhất là khi cùng một quy tắc xuất hiện ở nhiều nơi.

Bài này tập trung vào cách **nhận diện, phân biệt và tổ chức** mối quan tâm cốt lõi với mối quan tâm cắt ngang. Các thuật ngữ AOP và một ví dụ Java sẽ minh họa cách tách trách nhiệm; chúng không biến mọi quy tắc dùng chung thành một khía cạnh.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Giải thích “mối quan tâm” (concern) là gì và vì sao nó khác một hàm hay một lớp.
- Nhận diện mối quan tâm cốt lõi (core concern) và mối quan tâm cắt ngang (cross-cutting concern) theo **trách nhiệm và bối cảnh**, thay vì theo tên gọi.
- Chỉ ra hai vấn đề khi mã xử lý một mối quan tâm nằm rải rác hoặc lẫn vào mã của mối quan tâm khác.
- Mô tả AOP có thể gom chính sách dùng chung quanh những điểm thực thi được chọn như thế nào.
- Phân biệt phần nghiệp vụ cần giữ trong miền nghiệp vụ với phần hạ tầng có thể tách ra; đánh giá những giới hạn và rủi ro khi tách.

## 2. “Mối quan tâm” là gì?

Trong thiết kế phần mềm, **mối quan tâm** là một yêu cầu, quy tắc, trách nhiệm hoặc góc nhìn mà hệ thống phải xử lý. Nó trả lời câu hỏi: “Điều gì cần được bảo đảm hoặc thực hiện?”. Một mối quan tâm có thể trải qua nhiều lớp, nhiều hàm, thậm chí nhiều tiến trình.

Ví dụ ở ứng dụng đặt vé:

- **Giữ chỗ:** kiểm tra ghế còn trống và chuyển ghế sang trạng thái đã giữ.
- **Tính tiền:** áp dụng giá vé, ưu đãi và thuế theo quy tắc kinh doanh.
- **Kiểm toán:** ghi nhận ai đã thực hiện thao tác nhạy cảm và kết quả.
- **Theo dõi:** ghi nhận thời điểm, thời lượng và nguyên nhân thất bại của yêu cầu.
- **Kiểm soát truy cập:** chỉ cho phép người dùng thích hợp thực hiện một thao tác.

Một phương thức có thể phục vụ nhiều mối quan tâm. Ngược lại, một mối quan tâm có thể cần phối hợp nhiều phương thức. Vì thế, **concern là cách chia trách nhiệm**, không đồng nghĩa với “mỗi concern phải có đúng một class”.

### 2.1. Mối quan tâm cốt lõi (core concern)

Đây là trách nhiệm trực tiếp hiện thực **mục đích chính của một chức năng hoặc hệ thống**. Nếu bỏ nó, chức năng không còn làm đúng việc mà người dùng cần.

Với đặt vé: tìm suất chiếu, xác nhận ghế còn chỗ, áp dụng quy tắc giá, giữ ghế và phát hành vé là các công việc cốt lõi. Chúng thể hiện nghiệp vụ cụ thể; không thể thay bằng một thao tác ghi log dùng chung.

Một hệ thống có thể có nhiều mối quan tâm cốt lõi. “Cốt lõi” thường được xác định theo **ranh giới đang xét**: tính giá là cốt lõi của dịch vụ tính giá, nhưng với một công cụ chỉ đo hiệu năng thì chính việc đo đạc có thể là mục đích cốt lõi của công cụ đó.

### 2.2. Mối quan tâm cắt ngang (cross-cutting concern)

Đây là trách nhiệm có **cùng một bản chất hoặc chính sách áp dụng qua nhiều phần chức năng**, nên mã thực hiện nó dễ xuất hiện lặp lại giữa các thành phần vốn có nhiệm vụ chính khác nhau.

Các ví dụ thường gặp:

| Mối quan tâm | Biểu hiện cắt ngang | Điều cần phân biệt |
| --- | --- | --- |
| Ghi log vận hành | Ghi bắt đầu, kết thúc, lỗi của nhiều thao tác | Nội dung sự kiện nghiệp vụ quan trọng có thể thuộc miền nghiệp vụ |
| Theo dõi, đo thời gian, truy vết | Đo nhiều lời gọi, liên kết yêu cầu qua các lớp | Không được làm sai lệch kết quả hay rò rỉ dữ liệu nhạy cảm |
| Kiểm toán | Ghi ai thực hiện hành động được kiểm soát | Quy tắc **sự kiện nào bắt buộc ghi** thường gắn với nghiệp vụ và pháp lý |
| Xác thực/kiểm soát truy cập | Áp chính sách chung tại nhiều điểm vào | Quy tắc quyền sở hữu, hạn mức hoặc trạng thái cụ thể có thể là quy tắc cốt lõi |
| Quản lý ranh giới giao dịch | Bắt đầu, hoàn tất hoặc hoàn tác quanh một đơn vị công việc | Ranh giới nhất quán và bất biến nghiệp vụ cần được xác định đúng |
| Chính sách chịu lỗi | Timeout, giới hạn thử lại ở nhiều lời gọi ngoài hệ thống | Không được thử lại vô điều kiện thao tác có tác dụng phụ |

**Cắt ngang không có nghĩa là phụ, tùy chọn hoặc ít quan trọng.** Kiểm soát truy cập và kiểm toán có thể mang tính sống còn. Tính “cắt ngang” nói về phạm vi tác động và xu hướng phân tán mã, không phải thứ hạng ưu tiên.

### 2.3. Hai chiều của một chức năng

Hãy tưởng tượng chức năng “Đặt vé” và “Hoàn vé” là hai dòng công việc theo chiều dọc. Cả hai đều cần một số chính sách theo chiều ngang:

| Chính sách / chức năng | Đặt vé | Hoàn vé |
| --- | --- | --- |
| Quy tắc nghiệp vụ riêng | Kiểm tra ghế, tính giá, phát hành | Kiểm tra điều kiện hoàn, tính tiền hoàn |
| Ghi log kỹ thuật | Có thể dùng chung | Có thể dùng chung |
| Đo thời lượng | Có thể dùng chung | Có thể dùng chung |
| Ghi nhận thao tác nhạy cảm | Cần xác định sự kiện cần ghi | Cần xác định sự kiện cần ghi |
| Kiểm tra quyền | Điều kiện của hành động đặt vé | Điều kiện của hành động hoàn vé |

Những ô “dùng chung” là ứng viên để gom thành một chính sách; những ô chứa quy tắc riêng của từng hành động vẫn cần quyết định của nghiệp vụ. Bảng chỉ giúp hình dung hai chiều trách nhiệm, **không tự chứng minh mọi dòng ngang đều nên viết bằng AOP**.

## 3. Vì sao việc phân biệt này quan trọng?

Không tách được hai loại trách nhiệm dễ dẫn đến hai vấn đề kinh điển.

### 3.1. Mã bị rải rác (scattering)

Giả sử 20 thao tác đều cần ghi thời điểm bắt đầu, kết quả và thời gian chạy. Nếu mỗi thao tác tự cài bộ ghi giống nhau, chính sách theo dõi bị rải ở 20 nơi. Muốn thay định dạng log hoặc bổ sung mã tương quan yêu cầu, người bảo trì phải tìm và sửa nhiều đoạn.

Hệ quả: dễ bỏ sót một luồng, sai lệch chính sách giữa các nơi và phát sinh lỗi khi thay đổi hàng loạt.

### 3.2. Trách nhiệm bị đan xen (tangling)

Trong một hàm đặt vé, các dòng mã có thể xen kẽ kiểm tra ghế, ghi log, kiểm tra quyền chung, đo thời gian, mở giao dịch, tính tiền và xử lý lỗi kỹ thuật.

Hệ quả: đọc mã khó thấy trình tự nghiệp vụ; kiểm thử nghiệp vụ phải đối diện nhiều phụ thuộc không cần thiết; một thay đổi hạ tầng có nguy cơ tác động mã miền nghiệp vụ.

Hai vấn đề khác nhau: **scattering** là *một trách nhiệm nằm ở nhiều chỗ*; **tangling** là *nhiều trách nhiệm đan vào một chỗ*. Chúng thường xảy ra cùng nhau nhưng không luôn luôn.

### 3.3. Mục tiêu thiết kế

Mục tiêu là **tách những lý do thay đổi**: thay quy tắc tính vé nên chủ yếu sửa miền nghiệp vụ; thay cách theo dõi thao tác nên chủ yếu sửa chính sách theo dõi. Đây là biểu hiện của nguyên tắc phân tách mối quan tâm (separation of concerns, SoC).

Tách mã không có nghĩa tách mọi tương tác: nghiệp vụ vẫn cần thông tin định danh, quyết định phân quyền hoặc kết quả giao dịch khi chúng là dữ kiện cốt yếu. Mối liên hệ phải được biểu đạt qua hợp đồng rõ ràng.

## 4. AOP giúp thực thi chính sách cắt ngang như thế nào?

AOP bổ sung một cơ chế **chọn điểm áp dụng và thực hiện hành vi** mà không buộc mỗi phương thức nghiệp vụ chép cùng chính sách. Để đọc được cơ chế này, cần phân biệt:

- **Join point:** sự kiện có thể can thiệp (chẳng hạn thực thi phương thức), trong phạm vi mà công cụ hỗ trợ.
- **Pointcut:** điều kiện chọn *những join point nào* cần áp dụng; chọn quá rộng thì chạy thừa, quá hẹp thì bỏ sót.
- **Advice:** hành vi áp dụng *trước, sau, khi lỗi hoặc bao quanh* sự kiện đã chọn.
- **Aspect:** đơn vị tập trung chính sách cùng cách chọn và thực hiện hành vi ấy.
- **Weaving/interception:** kỹ thuật kết hợp hành vi với đường chạy, qua mã được biến đổi hoặc ranh giới trung gian tùy công cụ.

Ví dụ, hệ thống đặt vé có thể áp dụng bộ ghi thời gian chung cho các thao tác đã đánh dấu, trong khi nghiệp vụ giữ việc kiểm tra ghế và phát hành vé. Ý nghĩa của “đã áp dụng” phải được **kiểm chứng trên đúng đường gọi**, không chỉ bằng sự tồn tại của pointcut.

```text
Người gọi
  → [Điểm được cơ chế AOP quan sát]
      → [Chính sách trước, nếu áp dụng]
          → [Nghiệp vụ giữ chỗ / hoàn vé]
      ← [Chính sách theo kết quả hoặc ngoại lệ]
  ← Kết quả hoặc lỗi theo hợp đồng
```

Mô hình trên không ấn định một thứ tự cho mọi advice. **Trước, sau thành công, sau lỗi và dọn dẹp bất kể kết quả** có ý nghĩa khác nhau; advice bao quanh còn có thể thay đổi việc có thực hiện nghiệp vụ hay không. Khi kiểm toán, kiểm tra quyền và ranh giới giao dịch cùng áp dụng, thứ tự phải được quy định: ghi thành công *trước* khi kết quả thực sự bền vững không đồng nghĩa đã hoàn tất nghiệp vụ.

Cũng không nên tách “mọi thứ có thể dùng chung” vào aspect: **miền nghiệp vụ vẫn quyết định quy tắc hoàn vé, trạng thái ghế, quyền sở hữu tài nguyên và những sự kiện bắt buộc**. Aspect có thể hỗ trợ cơ chế quan sát hay thực thi một kiểm tra chung, nhưng ranh giới tin cậy và đường gọi ngoại lệ phải được kiểm thử đầy đủ.

## 5. Ví dụ Java 17: giữ nghiệp vụ riêng, bọc chính sách kiểm toán

Ví dụ dùng **dynamic proxy có sẵn trong JDK** để minh họa một cách thực hiện hành vi cắt ngang ở thời gian chạy. Đây là một kỹ thuật bọc lời gọi giao diện, **không phải toàn bộ khả năng của AOP**.

Tạo tệp `ConcernDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ConcernDemo {
    public interface OrderPort {
        String place(String itemCode);
    }

    public static final class OrderService implements OrderPort {
        @Override
        public String place(String itemCode) {
            if (itemCode == null || itemCode.isBlank()) {
                throw new IllegalArgumentException("Mã hàng không hợp lệ");
            }
            return "Đã đặt " + itemCode;
        }
    }

    public static OrderPort withAudit(OrderPort target) {
        return (OrderPort) Proxy.newProxyInstance(
            OrderPort.class.getClassLoader(),
            new Class<?>[] { OrderPort.class },
            (proxy, method, args) -> {
                System.out.println("[AUDIT] bắt đầu " + method.getName());
                try {
                    Object result = method.invoke(target, args);
                    System.out.println("[AUDIT] thành công " + method.getName());
                    return result;
                } catch (InvocationTargetException ex) {
                    System.out.println("[AUDIT] thất bại " + method.getName());
                    throw ex.getCause();
                } finally {
                    System.out.println("[AUDIT] kết thúc " + method.getName());
                }
            }
        );
    }

    public static void main(String[] args) {
        OrderPort orders = withAudit(new OrderService());

        System.out.println(orders.place("A17"));
        try {
            orders.place(" ");
        } catch (IllegalArgumentException ex) {
            System.out.println("Lỗi: " + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac ConcernDemo.java
java ConcernDemo
```

Đầu ra:

```text
[AUDIT] bắt đầu place
[AUDIT] thành công place
[AUDIT] kết thúc place
Đã đặt A17
[AUDIT] bắt đầu place
[AUDIT] thất bại place
[AUDIT] kết thúc place
Lỗi: Mã hàng không hợp lệ
```

**Đọc ví dụ theo trách nhiệm:**

1. `OrderService.place` nắm quy tắc cốt lõi: mã hàng hợp lệ thì nhận đặt, nếu không thì báo lỗi. Đây là ví dụ đơn giản hóa; hệ thống thực còn cần kiểm tra tồn kho, trạng thái, tính nhất quán và nhiều quy tắc khác.
2. `withAudit` áp chính sách ghi nhận cho lời gọi qua `OrderPort`. Mọi lần `orders.place(...)` đều đi qua lớp bọc, kể cả khi phương thức đích ném lỗi.
3. `InvocationTargetException` là lớp bọc do reflection tạo ra; ném lại `ex.getCause()` để người gọi nhận lỗi `IllegalArgumentException` gốc. Khối `finally` vẫn in dòng kết thúc.
4. Dùng `new OrderService().place(...)` trực tiếp sẽ **không** đi qua proxy. Tương tự, một lời gọi từ phương thức đích sang phương thức khác trên chính `this` không tự đi vòng qua proxy. Cơ chế sử dụng và ranh giới can thiệp phải được hiểu rõ.

Đây là log minh họa với `System.out`, chưa phải hệ thống kiểm toán bền vững. Sự kiện kiểm toán thật thường đòi hỏi danh tính có thể tin cậy, dấu thời gian, chính sách lưu trữ, tránh lộ dữ liệu nhạy cảm và cơ chế ghi phù hợp độ tin cậy cần thiết. **Việc quyết định một sự kiện kinh doanh nào bắt buộc được kiểm toán vẫn cần dựa trên quy tắc nghiệp vụ.**

### 5.1. Nếu không dùng một cơ chế AOP thì sao?

Bạn vẫn có thể áp dụng SoC qua **Decorator**, hàm bậc cao, interceptor, middleware hoặc cấu trúc dịch vụ chuyên trách. Một lớp `AuditedOrderPort` tự triển khai `OrderPort`, ủy quyền cho `OrderService` và ghi sự kiện trước/sau cũng tách trách nhiệm hợp lệ.

AOP đặc biệt hữu ích khi cần **khai báo một chính sách và áp nhất quán tại nhiều điểm chọn**. Nhưng không phải việc tách mã nào cũng cần AOP: nếu chỉ có một chỗ rõ ràng, một lời gọi trực tiếp hoặc Decorator dễ đọc có thể phù hợp hơn.

## 6. Quy tắc phân loại: nhìn vào vai trò thay vì tên gọi

Một yêu cầu không có nhãn “cốt lõi” hay “cắt ngang” cố định cho mọi hệ thống. Hãy xét lần lượt:

1. **Mục đích:** Nếu bỏ trách nhiệm này, tác vụ chính có còn đúng theo yêu cầu của người dùng hay nghiệp vụ không?
2. **Phạm vi:** Cùng một chính sách có áp dụng cho nhiều tác vụ thuộc những chức năng khác nhau không?
3. **Lý do thay đổi:** Chính sách có thay đổi tương đối độc lập với quy tắc từng chức năng không?
4. **Dữ kiện cần thiết:** Có thể xác định thời điểm và dữ liệu cần dùng tại ranh giới áp dụng mà không phải ẩn logic nghiệp vụ vào chính sách chung không?
5. **Tính quan sát được:** Người đọc có thể dễ dàng tìm ra nơi chính sách được áp dụng, thứ tự thực thi và kết quả của nó không?

**Ví dụ về vùng ranh giới dễ nhầm:**

| Yêu cầu | Phần có thể cắt ngang | Phần thường thuộc cốt lõi |
| --- | --- | --- |
| Chỉ người dùng hợp lệ được thao tác | Kiểm tra danh tính, chính sách phân quyền chung tại điểm vào | Quy tắc “chỉ chủ vé chưa sử dụng được hoàn vé” |
| Bảo đảm dữ liệu nhất quán | Cơ chế tạo/đóng ranh giới giao dịch | Quyết định những bước nào phải thành công cùng nhau và bất biến cần giữ |
| Ghi nhận thay đổi | Thu thập, truyền, lưu sự kiện theo chính sách chung | Định nghĩa sự kiện “Vé đã được xuất” và dữ kiện nghiệp vụ của nó |
| Kiểm tra dữ liệu | Quy tắc kỹ thuật lặp lại như dữ liệu bắt buộc hoặc giới hạn kích thước | Quy tắc “không hoàn vé sau giờ chiếu” |
| Xử lý lỗi | Chuẩn hóa định dạng lỗi và theo dõi lỗi kỹ thuật | Quyết định lỗi nào cho phép khôi phục, hoàn tiền, hủy giữ chỗ |
| Thử lại | Cơ chế timeout, backoff và số lần thử | Quyết định thao tác có an toàn khi thực thi lặp hay không |

Ví dụ: “mọi yêu cầu đều phải có mã tương quan” là một chính sách kỹ thuật dùng chung. “Một giao dịch chuyển tiền không được làm mất tổng số dư” là bất biến nghiệp vụ; không nên giấu bất biến đó trong một khía cạnh áp dụng theo tên phương thức.

### 6.1. Cắt ngang không đồng nghĩa với lặp mã

Hai hàm cùng gọi `calculatePrice()` chưa đủ để kết luận việc tính giá là cross-cutting. Đó có thể là **miền nghiệp vụ dùng chung**. Lặp mã đơn thuần có thể được xử lý bằng hàm, lớp hoặc mô-đun dùng chung, không cần cơ chế chèn hành vi theo điểm nối.

Ngược lại, một chính sách kỹ thuật mới chỉ áp dụng tại vài điểm vẫn có thể là mối quan tâm cắt ngang nếu nó đi qua nhiều ranh giới chức năng và có lý do thay đổi riêng.

### 6.2. Ranh giới quan trọng hơn vị trí tệp

Đặt log vào một tệp `AuditAspect` không tự bảo đảm SoC nếu tệp đó chứa quy tắc giá vé, điều kiện hoàn vé hoặc quyết định nghiệp vụ ẩn. Ngược lại, một lớp nghiệp vụ gọi rõ ràng đến thành phần kiểm toán qua giao diện có thể là thiết kế sạch dù không sử dụng aspect.

Hãy đánh giá **ai sở hữu quy tắc**, ai chỉ thực hiện cơ chế và hợp đồng nào nối hai bên; tên lớp hay annotation không chứng minh chất lượng thiết kế.

## 7. Các điểm nâng cao khi tổ chức mối quan tâm cắt ngang

### 7.1. Điểm can thiệp và khả năng nhìn thấy hành vi

Hành vi cắt ngang chạy ngoài thân phương thức đích nên người chỉ đọc một hàm có thể không thấy nó. Nếu chọn điểm quá rộng, chính sách có thể ảnh hưởng thao tác ngoài dự kiến; nếu chọn quá hẹp, một đường gọi bị bỏ sót.

Cần có quy ước đặt tên rõ, cấu hình truy xuất được, tài liệu về điểm áp dụng và kiểm thử ở **ranh giới thực sự**. Không nên coi việc đính nhãn hay khai báo quy tắc chọn điểm là bằng chứng hành vi đã chạy.

### 7.2. Thứ tự và kết quả lỗi

Giả sử có kiểm tra quyền, giao dịch, kiểm toán và đo thời gian quanh cùng một thao tác. Việc kiểm tra quyền trước hay sau tác vụ sẽ quyết định có kịp ngăn tác vụ hay không. Việc ghi kiểm toán trước hay sau khi giao dịch xác nhận ảnh hưởng ý nghĩa của “thành công”.

Nếu một hành vi bao quanh bỏ qua lời gọi tiếp, gọi nhiều lần hoặc sửa kết quả, nó có thể thay đổi nghiệp vụ. Cần định nghĩa rõ hành vi nào được phép, đặc biệt khi có lỗi. **Giữ nguyên ngoại lệ và ngữ nghĩa trả về** là mặc định tốt đối với khía cạnh chỉ dùng để quan sát.

### 7.3. Tính nguyên tử, tác dụng phụ và thử lại

Timeout hoặc retry là chính sách thường dùng chung, nhưng thử lại một lời gọi đã gây tác dụng phụ có thể tạo hai đơn hàng hoặc hai lần trừ tiền. Trước khi đặt chính sách thử lại quanh một thao tác, phải biết thao tác có **tính lặp an toàn (idempotency)** hay không, có khóa chống trùng hoặc mã yêu cầu duy nhất hay không, và phạm vi giao dịch được xác định như thế nào.

Cơ chế chung thực hiện retry; **nghiệp vụ xác định điều kiện được retry**. Hai trách nhiệm phối hợp, không thay thế nhau.

### 7.4. Bối cảnh thực thi và bất đồng bộ

Theo dõi yêu cầu có thể cần mã tương quan, danh tính hoặc thông tin truy vết. Khi công việc chuyển sang luồng khác, hàng đợi hoặc tác vụ bất đồng bộ, bối cảnh có thể không tự truyền theo. Một khía cạnh đọc dữ liệu gắn với luồng hiện tại nhưng không có cơ chế truyền bối cảnh sẽ ghi thiếu hoặc sai thông tin.

Chính sách theo dõi phải xác định lúc nào bối cảnh được tạo, truyền, khôi phục và xóa; tránh nhầm bối cảnh giữa các yêu cầu chạy đồng thời. Đây là vấn đề của ranh giới thực thi, không chỉ của cú pháp aspect.

### 7.5. Kiểm soát truy cập và độ tin cậy

Kiểm tra quyền chỉ ở lớp bọc có thể thiếu nếu tồn tại đường gọi khác không đi qua điểm can thiệp. Quy tắc quan trọng còn cần bảo vệ tại ranh giới đáng tin cậy và kiểm thử các đường truy cập khác nhau. Các bất biến sở hữu tài nguyên hoặc trạng thái nghiệp vụ phải có nơi thực thi chắc chắn.

Việc ghi log/kiểm toán cũng cần hạn chế mật khẩu, mã truy cập, dữ liệu cá nhân hoặc toàn bộ tham số nhạy cảm. Tái sử dụng một chính sách rộng làm tăng phạm vi tác động nếu nó thu thập dữ liệu không phù hợp.

### 7.6. Chi phí vận hành và kiểm thử

Mỗi lớp can thiệp có thể thêm lời gọi, cấp phát, đo đạc, truyền bối cảnh hoặc tương tác bên ngoài. Quan trọng hơn, nó thêm **hành vi ẩn đối với mã nghiệp vụ**. Khi kiểm thử, ngoài thử hàm cốt lõi, phải thử điểm áp dụng, thứ tự, nhánh thành công/lỗi và tình huống bỏ qua proxy hoặc weaving.

Một khía cạnh gián tiếp quá phức tạp có thể gây khó khăn lớn hơn phần lặp mã mà nó giải quyết. Vì vậy cần đánh giá chi phí này theo số điểm áp dụng và yêu cầu chất lượng thực tế.

## 8. Khi nào nên, khi nào không nên tách bằng AOP?

**AOP là ứng viên phù hợp** khi cùng một chính sách tương đối đồng nhất phải áp dụng tại nhiều điểm, có điều kiện chọn điểm rõ ràng, thực hiện quanh ranh giới hỗ trợ được và có thể kiểm thử độc lập. Ví dụ: ghi thời lượng của một nhóm lời gọi hoặc gắn mã tương quan cho các điểm vào được quy định.

**Ưu tiên cách thể hiện tường minh** khi hành vi là quy tắc nghiệp vụ cần người đọc nhìn thấy trong luồng chính, khi các điểm áp dụng quá khác nhau, khi thứ tự tác dụng phụ cần kiểm soát từng bước hoặc khi một hàm gọi thành phần dùng chung đã đủ rõ.

Một số lỗi thiết kế thường gặp:

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Xem mọi logic dùng chung là “aspect” | Che khuất quy tắc nghiệp vụ | Phân biệt tái sử dụng chức năng với chính sách cắt ngang |
| Chọn điểm áp dụng quá rộng | Hành vi chạy ở nơi không cần thiết | Thu hẹp quy tắc và kiểm thử từng đường gọi |
| Ẩn quyết định nghiệp vụ trong advice | Khó tìm nguyên nhân và khó bảo vệ bất biến | Giữ quyết định ở miền nghiệp vụ, dùng khía cạnh cho cơ chế chung |
| Nuốt lỗi hoặc sửa giá trị trả về khi chỉ theo dõi | Kết quả khác kỳ vọng của người gọi | Bảo toàn kết quả và chuyển tiếp lỗi gốc |
| Quên lời gọi nội bộ/bỏ qua lớp bọc | Chính sách không chạy trên một số đường | Xác minh thực tế cơ chế proxy hoặc weaving |
| Không xét thứ tự các khía cạnh | Log, giao dịch và quyền sai ngữ nghĩa | Quy định thứ tự và kiểm thử lỗi/thành công |
| Áp retry chung lên thao tác ghi | Tác dụng phụ bị nhân đôi | Phân loại thao tác, thiết lập idempotency và ranh giới an toàn |

## 9. Bài tập phân loại theo bối cảnh

Trong hệ thống đặt vé, hãy xác định **phần cốt lõi**, **phần có thể cắt ngang** và lý do:

1. Tính giá vé theo loại ghế và khuyến mãi.
2. Ghi thời lượng xử lý cho mọi yêu cầu qua cổng API.
3. Chỉ cho hoàn vé khi còn ít nhất 60 phút trước giờ chiếu.
4. Gắn mã truy vết xuyên suốt thao tác đặt vé và hoàn vé.
5. Thử lại việc gọi nhà cung cấp thanh toán sau một lỗi mạng.
6. Mọi thao tác đổi trạng thái vé phải phát sinh sự kiện `TicketStatusChanged`.

**Gợi ý đáp án:**

1. **Cốt lõi.** Công thức giá là quy tắc sản phẩm; có thể tái sử dụng trong mô-đun riêng nhưng không mặc nhiên là khía cạnh.
2. **Có thể cắt ngang.** Cùng một chính sách đo thời gian qua nhiều điểm vào.
3. **Cốt lõi.** Quy tắc hoàn vé quyết định quyền lợi và trạng thái nghiệp vụ; không nên chỉ ẩn trong điểm can thiệp kỹ thuật.
4. **Có thể cắt ngang.** Thu thập và truyền mã truy vết là chính sách quan sát nhiều chức năng, với điều kiện bối cảnh được truyền đúng.
5. **Pha trộn.** Cơ chế thử lại có thể dùng chung, nhưng thao tác thanh toán có được phép thử lại hay không phụ thuộc idempotency, trạng thái giao dịch và hợp đồng nhà cung cấp.
6. **Pha trộn.** “Khi nào và với dữ liệu gì phải có sự kiện” là yêu cầu nghiệp vụ; cơ chế phát hoặc vận chuyển sự kiện có thể là hạ tầng dùng chung. Cần thiết kế để không đánh mất sự kiện khi thay đổi trạng thái.

**Tự kiểm tra:** Với mỗi ví dụ, hãy trả lời thêm “Ai sở hữu quy tắc?”, “Nó phải chạy tại ranh giới nào?”, “Nếu đi đường gọi khác thì sao?” và “Lỗi của nó có được phép làm thất bại nghiệp vụ không?”. Những câu hỏi này thường quan trọng hơn việc đặt tên một lớp là “aspect”.

## 10. Ghi nhớ

- **Cốt lõi** nói hệ thống phải làm gì đúng với nghiệp vụ; **cắt ngang** nói một chính sách cần được áp dụng xuyên qua nhiều phần của hệ thống.
- **Scattering** làm một trách nhiệm rải ở nhiều nơi; **tangling** khiến các trách nhiệm đan xen trong cùng một nơi.
- AOP hỗ trợ diễn đạt **điểm áp dụng + hành vi chung** để tách một số mối quan tâm cắt ngang; khả năng thực tế tùy cơ chế kết hợp khía cạnh.
- **Phân loại phụ thuộc bối cảnh và quyền sở hữu quy tắc.** Dùng chung không tự động là cắt ngang; cắt ngang cũng không có nghĩa kém quan trọng.
- Trước khi chọn AOP, phải xét điểm can thiệp, thứ tự, lỗi, bất đồng bộ, bảo mật, idempotency và khả năng kiểm thử. Một thiết kế tách mối quan tâm tốt vẫn phải giữ luồng nghiệp vụ rõ ràng và đúng đắn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-003">Mã bị phân tán (scattering) và mã bị đan xen (tangling)</a>

<details>
<summary>Xem chi tiết</summary>

# Mã bị phân tán (scattering) và mã bị đan xen (tangling) trong tư duy AOP

> **Mục tiêu học tập:** Nhận diện hai dạng vấn đề thiết kế thường khiến một hệ thống khó bảo trì; giải thích vì sao các mối quan tâm xuyên suốt (*cross-cutting concerns*) tạo ra chúng; thấy cách tư duy lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP) tách phần hành vi dùng chung khỏi nghiệp vụ; và đánh giá được lợi ích, giới hạn, rủi ro khi áp dụng.

## 1. Từ một chức năng nghiệp vụ đến nhiều mối quan tâm

Một phương thức nghiệp vụ thường có trách nhiệm chính, chẳng hạn xác nhận đơn hàng, tính phí hoặc cập nhật tồn kho. Trong ứng dụng thực tế, trước và sau thao tác đó còn có các việc khác:

- **Ghi vết/quan sát:** ghi thời điểm bắt đầu, kết thúc, lỗi và mã tương quan của lời gọi.
- **Đo thời gian:** thu thập thời lượng thực thi cho nhiều nhóm thao tác.
- **Kiểm soát truy cập:** áp dụng chính sách kiểm tra quyền tại những ranh giới xác định.
- **Giao dịch, kiểm toán hoặc áp dụng chính sách lỗi:** thực thi một quy tắc nhất quán tại nhiều vị trí phù hợp.

Những việc này là các **mối quan tâm (concerns)** riêng biệt với quy tắc nghiệp vụ. Khi cùng một mối quan tâm cần xuất hiện tại nhiều điểm của hệ thống, nó có tính chất **xuyên suốt** (*cross-cutting*).

Ví dụ, yêu cầu “ghi nhận mọi thao tác xử lý đơn hàng được quan sát” không thuộc riêng phép tính tiền hay việc xuất kho. Nếu xử lý trực tiếp trong từng phương thức, phần ghi vết có thể lan ra nhiều lớp, còn mỗi phương thức lại chứa cả mã nghiệp vụ lẫn mã ghi vết.

Đó là hai góc nhìn của một vấn đề tổ chức mã:

- **Scattering:** nhìn **theo chiều một mối quan tâm**, mã thực hiện nó nằm rải rác ở nhiều vị trí.
- **Tangling:** nhìn **theo chiều một đơn vị mã**, nhiều mối quan tâm khác nhau bị trộn vào cùng đơn vị đó.

Hai hiện tượng thường xuất hiện cùng nhau, nhưng **không đồng nghĩa** và không bắt buộc cùng tồn tại.

## 2. Scattering: một mối quan tâm bị phân tán ở nhiều nơi

**Scattering** xảy ra khi mã phục vụ một trách nhiệm chung bị phân phối qua nhiều lớp, phương thức hoặc mô-đun. Để thay đổi chính sách chung đó, người bảo trì phải tìm và cập nhật nhiều điểm.

Hình dung có 20 phương thức nghiệp vụ đều tự ghi:

```text
trace("begin", operation);
... nghiệp vụ ...
trace("end", operation);
```

Phần ghi vết nằm ở 20 vị trí. Nếu quy định mới yêu cầu bổ sung mã tương quan hoặc ghi lỗi với cùng định dạng, người phát triển phải sửa nhiều nơi. Có điểm được cập nhật, có điểm bị quên, có điểm dùng định dạng khác: chính sách quan sát trở nên thiếu nhất quán.

**Dấu hiệu nhận biết scattering:**

- Cùng một quy tắc kỹ thuật hoặc đoạn xử lý được lặp lại trong nhiều phương thức.
- Một thay đổi về chính sách yêu cầu sửa hàng loạt tệp không liên quan về nghiệp vụ.
- Khi thêm tính năng nghiệp vụ, lập trình viên phải nhớ chép thêm một “nghi thức” chung.
- Việc đánh giá mức độ bao phủ của chính sách đòi hỏi tìm kiếm thủ công nhiều vị trí.

**Lưu ý:** hai đoạn mã trông giống nhau chưa chắc là scattering cần xử lý bằng AOP. Nếu chúng biểu diễn **hai quy tắc nghiệp vụ khác nhau**, gom lại chỉ vì giống cú pháp có thể gây ràng buộc sai. Cần xác định chúng có thực sự cùng một trách nhiệm và cùng một lý do thay đổi hay không.

## 3. Tangling: nhiều mối quan tâm đan vào cùng một đơn vị mã

**Tangling** xảy ra khi mã của các trách nhiệm khác nhau nằm xen nhau trong một phương thức, lớp hoặc luồng xử lý. Một hàm đặt hàng có thể đồng thời thực hiện:

```text
kiểm tra quyền → ghi trace → kiểm tra dữ liệu → tính tiền
              → đo thời gian → ghi kiểm toán → trả kết quả
```

Ở đây mã nghiệp vụ, quan sát, phân quyền và đo lường bị đan vào một luồng. Kết quả là:

- Khó đọc luồng nghiệp vụ cốt lõi do các thao tác phụ chen giữa.
- Việc thay đổi một chính sách phụ dễ đụng vào mã nghiệp vụ.
- Kiểm thử một hành vi phải thiết lập nhiều phụ thuộc không liên quan.
- Dễ thiếu xử lý trên nhánh ngoại lệ, nhánh trả về sớm hoặc luồng bất đồng bộ.
- Ranh giới trách nhiệm không rõ: lớp nghiệp vụ biết quá nhiều chi tiết quan sát hoặc hạ tầng.

**Tangling không chỉ là “hàm dài”.** Một phương thức ngắn vẫn có thể chứa trách nhiệm không liên quan; ngược lại, một thuật toán nghiệp vụ dài nhưng thống nhất về trách nhiệm chưa chắc bị tangling.

## 4. Cùng một ví dụ Java: vấn đề nhìn từ hai chiều

Đây là chương trình **Java 17+ hoàn chỉnh**, mô phỏng hai nghiệp vụ đơn giản. Mỗi phương thức vừa trả kết quả nghiệp vụ vừa trực tiếp ghi trace.

Lưu tệp `BeforeSeparation.java`:

```java
public class BeforeSeparation {
    static class BillingService {
        String pay() {
            System.out.println("[trace] begin pay");
            String result = "PAID";
            System.out.println("[trace] end pay");
            return result;
        }
    }

    static class ShippingService {
        String ship() {
            System.out.println("[trace] begin ship");
            String result = "SHIPPED";
            System.out.println("[trace] end ship");
            return result;
        }
    }

    public static void main(String[] args) {
        BillingService billing = new BillingService();
        ShippingService shipping = new ShippingService();

        System.out.println(billing.pay());
        System.out.println(shipping.ship());
    }
}
```

Chạy:

```bash
javac BeforeSeparation.java
java BeforeSeparation
```

Kết quả chuẩn:

```text
[trace] begin pay
[trace] end pay
PAID
[trace] begin ship
[trace] end ship
SHIPPED
```

Phân tích theo **scattering**: trách nhiệm “ghi begin/end” được sao chép trong `BillingService` và `ShippingService`. Khi có thêm nghiệp vụ thứ ba, người viết có thể phải sao chép tiếp.

Phân tích theo **tangling**: bên trong từng phương thức, câu lệnh trace chen vào luồng xử lý và phép trả kết quả. Nếu nghiệp vụ có nhiều nhánh trả về hoặc ném ngoại lệ, các câu trace viết thủ công cũng dễ mất cân đối. Ví dụ mã trên **không bảo đảm ghi kết thúc nếu một câu lệnh ở giữa ném ngoại lệ**.

Bởi vậy, cùng một ví dụ cho thấy **một trách nhiệm bị phân tán qua nhiều nơi** và **nhiều trách nhiệm đan trong từng nơi**.

## 5. Bước tách đầu tiên: đưa chính sách dùng chung ra ranh giới

Một cách cải thiện đơn giản là tập trung hành vi ghi vết vào một hàm bao (*wrapper*) và giữ nghiệp vụ chỉ xử lý kết quả của nó. Đây là minh họa **tư duy tách mối quan tâm bằng Java thuần**, chưa phải cơ chế AOP tự động áp dụng vào các điểm thực thi.

Lưu tệp `AfterSeparation.java`:

```java
import java.util.function.Supplier;

public class AfterSeparation {
    static class BillingService {
        String pay() {
            return "PAID";
        }
    }

    static class ShippingService {
        String ship() {
            return "SHIPPED";
        }
    }

    static <T> T trace(String operation, Supplier<T> action) {
        System.out.println("[trace] begin " + operation);
        try {
            return action.get();
        } finally {
            System.out.println("[trace] end " + operation);
        }
    }

    public static void main(String[] args) {
        BillingService billing = new BillingService();
        ShippingService shipping = new ShippingService();

        System.out.println(trace("pay", billing::pay));
        System.out.println(trace("ship", shipping::ship));
    }
}
```

Chạy:

```bash
javac AfterSeparation.java
java AfterSeparation
```

Kết quả chuẩn:

```text
[trace] begin pay
[trace] end pay
PAID
[trace] begin ship
[trace] end ship
SHIPPED
```

Đối chiếu:

| Tiêu chí | Trước khi tách | Sau khi tách qua wrapper |
|---|---|---|
| Vị trí xử lý trace | Nằm trong từng phương thức nghiệp vụ | Nằm trong hàm `trace` |
| Độ tập trung của chính sách | Lặp ở nhiều nơi | Một nơi định nghĩa |
| Khả năng đọc nghiệp vụ | Trace chen vào mã nghiệp vụ | Phương thức nghiệp vụ tập trung vào kết quả |
| Điểm áp dụng chính sách | Trong phương thức, dễ bỏ sót | Tại từng lời gọi wrapper, **vẫn phải nhớ dùng** |
| Nhánh ngoại lệ | Có thể thiếu log kết thúc | `finally` chạy khi `action.get()` kết thúc bình thường hoặc bằng ngoại lệ |

**Hai giới hạn cần hiểu rõ:**

1. Dù thân phương thức nghiệp vụ sạch hơn, nơi gọi vẫn phải chủ động viết `trace(...)`. Người gọi bỏ quên wrapper thì chính sách không được áp dụng. Mức độ scattering ở **điểm áp dụng** vì vậy chưa chắc được giải quyết triệt để.
2. Dòng `[trace] end` ở `finally` chỉ có nghĩa là **đã rời phạm vi thực thi**, không chứng minh lời gọi thành công. Nếu cần tách thành công/thất bại thì chính sách trace phải phân biệt kết quả bình thường với ngoại lệ. `Supplier<T>` cũng chỉ phù hợp trực tiếp cho tác vụ không khai báo checked exception; ví dụ không phải một cơ chế bao phủ mọi loại lời gọi.

Điểm học quan trọng: **tách chỗ định nghĩa một chính sách** và **tập trung cách chọn những nơi áp dụng chính sách** là hai việc khác nhau. Tư duy AOP đưa việc chọn điểm áp dụng thành một cấu phần thiết kế rõ ràng.

## 6. AOP xử lý scattering và tangling theo cơ chế nào?

**Aspect-Oriented Programming (AOP)** mô-đun hóa những mối quan tâm cắt ngang nhiều phần của chương trình. Thay vì chèn thủ công cùng một đoạn kỹ thuật vào từng đơn vị nghiệp vụ, ta mô tả **điểm nào cần áp dụng** và **hành vi nào cần thực hiện** ở các điểm đó.

Các thuật ngữ nền tảng:

| Thuật ngữ | Ý nghĩa trong tư duy AOP | Liên hệ với ví dụ |
|---|---|---|
| **Concern** | Một trách nhiệm hoặc mục đích xử lý có thể xác định | Ghi trace, tính tiền |
| **Cross-cutting concern** | Concern cần xuất hiện tại nhiều ranh giới hoặc điểm xử lý | Trace ở nhiều nghiệp vụ |
| **Join point** | Một điểm trong quá trình chạy/cấu trúc chương trình mà một mô hình AOP có thể tác động | Thực thi phương thức `pay()` |
| **Pointcut** | Quy tắc chọn một tập join point | Chọn thực thi `pay()` và `ship()` |
| **Advice** | Hành vi được thực hiện tại các join point được chọn | Ghi begin/end quanh lời gọi |
| **Aspect** | Mô-đun kết hợp chính sách/pointcut/advice liên quan | Khía cạnh quan sát lời gọi |
| **Weaving** | Quá trình kết hợp hành vi của aspect với mã/chương trình đích | Nối trace vào các điểm đã chọn |

**Mô hình khái niệm:**

```text
   Mã nghiệp vụ                 Mô-đun aspect
  ┌────────────────┐          ┌─────────────────────────┐
  │ pay()          │          │ Pointcut: chọn pay/ship  │
  │ ship()         │          │ Advice: trace begin/end  │
  └────────────────┘          └─────────────────────────┘
            \                         /
             \_____ weaving ________/
                       |
                       v
           Lời gọi thực thi có trace
           mà không cần chèn trace
           vào thân từng phương thức
```

Pseudocode dưới đây chỉ diễn đạt **ý tưởng**, không phải cú pháp của một ngôn ngữ AOP cụ thể:

```text
aspect Tracing:
    pointcut observed = execution(pay) OR execution(ship)

    around observed:
        log("begin", currentOperation)
        try:
            return proceed()  // tiếp tục phần xử lý gốc
        finally:
            log("end", currentOperation)
```

Khi quy tắc chọn điểm thực thi và hành vi trace được mô-đun hóa tập trung:

- **Giảm scattering:** định nghĩa trace và quy tắc chọn các nghiệp vụ áp dụng nằm trong một mô-đun chính sách, không sao chép trong từng phương thức.
- **Giảm tangling:** thân `pay()`, `ship()` không phải chứa những câu lệnh quan sát không thuộc logic nghiệp vụ.
- **Tăng khả năng thay đổi đồng bộ:** đổi định dạng hoặc chính sách trace tại đúng nơi sở hữu nó.

Tuy nhiên, **aspect không làm chi phí phối hợp biến mất**. Hành vi thực thi còn phụ thuộc cấu hình pointcut, loại join point mà cơ chế AOP hỗ trợ, thời điểm weaving và thứ tự áp dụng advice. Mã nghiệp vụ có thể sạch hơn trong khi đường đi thực thi trở nên khó nhìn bằng cách chỉ mở một tệp nguồn.

### 6.1. Before, after và around khác nhau ra sao?

- **Before:** hành vi chạy trước join point đã chọn; hữu ích cho ghi nhận đầu vào hoặc kiểm tra điều kiện trước khi tiến hành.
- **After:** hành vi chạy khi thoát join point; một số mô hình phân biệt hoàn tất bình thường, thoát do ngoại lệ và trường hợp cuối cùng bất kể kết quả.
- **Around:** bao quanh lời gọi, thường điều khiển việc tiếp tục thực thi gốc qua một cơ chế tương tự `proceed()`; linh hoạt nhưng có ảnh hưởng lớn nhất đến luồng điều khiển.

Không được suy từ cái tên “after” rằng nó luôn có cùng nghĩa trong mọi công cụ. Khi cần log đúng “thành công”, “thất bại”, “rời phạm vi”, hãy định nghĩa riêng từng trạng thái và kiểm chứng trên công cụ triển khai thực tế.

### 6.2. Weaving diễn ra lúc nào?

Tùy công cụ và mô hình thực thi, việc kết hợp aspect có thể được thực hiện khi biên dịch, khi xử lý mã đã biên dịch, khi tải lớp hoặc tại thời điểm chạy bằng cơ chế trung gian. Những cách này có **phạm vi join point, giới hạn kỹ thuật và chi phí** khác nhau. Vì thế AOP là **cách tổ chức mối quan tâm**, không đồng nhất với một kỹ thuật chặn lời gọi duy nhất.

## 7. Phân biệt scattering và tangling trong ca thực tế

| Quan sát | Nhấn mạnh scattering | Nhấn mạnh tangling |
|---|---|---|
| “Mỗi lớp lại có một đoạn ghi kiểm toán giống nhau.” | Có: một chính sách nằm nhiều nơi | Có thể có nếu đoạn kiểm toán chen vào nghiệp vụ |
| “Một phương thức dài chứa kiểm tra quyền, tính giá, gửi thông báo và ghi số liệu.” | Chưa thể kết luận chỉ từ một phương thức | Có dấu hiệu: nhiều trách nhiệm bị ghép vào một nơi |
| “Đổi format log phải sửa 12 lớp.” | Rất rõ | Có thể là hệ quả kèm theo |
| “Muốn hiểu tính giá phải lần qua nhiều nhánh kiểm soát kỹ thuật.” | Chưa đủ bằng chứng | Rất rõ |
| “Một số thao tác có audit, một số thao tác quên audit.” | Có dấu hiệu phân tán việc áp dụng | Không bắt buộc |

Có thể xem cấu trúc hệ thống như một ma trận:

```text
                       Đơn vị mã
Mối quan tâm       Billing    Shipping    Inventory
----------------------------------------------------
Nghiệp vụ             X           X            X
Tracing               X           X            X
Kiểm toán             X                        X
Đo thời gian          X           X
```

Đọc **theo hàng**: concern Tracing xuất hiện qua nhiều đơn vị mã → nguy cơ **scattering**. Đọc **theo cột**: Billing chứa nghiệp vụ, Tracing, kiểm toán, đo thời gian → nguy cơ **tangling**.

Ma trận chỉ là cách soi cấu trúc, không phải tiêu chí máy móc “cứ có hai dấu X là mã xấu”. Một số ranh giới kỹ thuật hoặc nghiệp vụ **cần phối hợp rõ ràng** và việc gom quá mức có thể làm mất ngữ nghĩa.

## 8. Vì sao hai hiện tượng làm tăng chi phí thay đổi?

### 8.1. Tăng số điểm phải chỉnh sửa

Khi một chính sách chung rải ở nhiều nơi, phạm vi thay đổi lớn hơn phạm vi lý do thay đổi. Sửa một yêu cầu trace nhưng phải rà soát toàn bộ các nhóm nghiệp vụ làm tăng nguy cơ thiếu sót và sai lệch giữa các bản sao.

### 8.2. Làm luồng nghiệp vụ khó kiểm chứng

Nếu điều kiện nghiệp vụ bị bao quanh bởi nhiều câu lệnh kỹ thuật, người đọc khó xác định đoạn nào làm thay đổi trạng thái nghiệp vụ. Kiểm thử cũng có thể bị buộc khởi tạo thêm logger, bộ đo, kiểm soát quyền và trạng thái hạ tầng.

### 8.3. Khiến đường lỗi thiếu nhất quán

Một phương thức có thể trả sớm hoặc ném ngoại lệ. Mã trace viết nối tiếp ở cuối hàm chỉ chạy nếu chương trình đi tới dòng đó. Việc lặp cấu trúc này ở nhiều phương thức khiến lỗi cùng loại cũng phân tán.

### 8.4. Làm chính sách xuyên suốt khó kiểm toán

Một quy tắc kiểm toán hoặc quan sát được xem là đáng tin chỉ khi biết rõ **điểm nào được bao phủ, điểm nào loại trừ và điều gì xảy ra khi chính sách thất bại**. Càng nhiều vị trí chép thủ công, phạm vi bao phủ càng khó chứng minh.

## 9. Khi nào AOP là lựa chọn phù hợp?

AOP đáng cân nhắc khi một hành vi có **chính sách chung, ranh giới áp dụng có thể mô tả ổn định và ít phụ thuộc vào chi tiết nghiệp vụ bên trong**. Ví dụ ghi trace nhất quán tại các thao tác công khai của một nhóm dịch vụ là ứng viên dễ hiểu.

Trái lại, logic “khách hàng hạng đặc biệt được giảm 7% khi mua loại hàng A” là **quy tắc nghiệp vụ**, thường nên được mô hình hóa thành hành vi hoặc chính sách nghiệp vụ có tên rõ ràng. Che giấu quy tắc cốt lõi trong một advice chung có thể khiến kết quả tính toán bất ngờ.

| Cách tiếp cận | Điểm mạnh | Điểm cần chú ý |
|---|---|---|
| **Hàm dùng chung** | Tập trung định nghĩa, đơn giản, dễ theo dấu | Từng nơi vẫn phải gọi |
| **Decorator/wrapper** | Ranh giới rõ, dễ chủ động ghép và kiểm thử | Phải bảo đảm mọi đường gọi cần thiết đi qua lớp bao |
| **Interceptor/middleware** | Phù hợp với một pipeline hoặc giao thức cụ thể | Chỉ bao phủ các điểm mà pipeline quản lý |
| **AOP với pointcut/advice** | Tập trung cả hành vi và quy tắc chọn điểm áp dụng | Có thể tạo phụ thuộc ngầm, khó lần dấu và phụ thuộc giới hạn weaving |

Các lựa chọn này không loại trừ lẫn nhau. Tư duy AOP giúp **nhận diện trách nhiệm xuyên suốt**; quyết định triển khai nên dựa trên nơi phát sinh hành vi, khả năng quan sát, nhu cầu kiểm thử và độ phức tạp thực tế.

## 10. Góc nhìn nâng cao: rủi ro khi “tách sạch” mã nghiệp vụ

### 10.1. Pointcut quá rộng hoặc quá hẹp

Ví dụ một pointcut chọn mọi phương thức có tên bắt đầu bằng `save` có thể vô tình áp dụng vào thao tác không thuộc chính sách quan sát dự kiến (**quá rộng**) và bỏ qua thao tác cùng ý nghĩa nhưng mang tên khác (**quá hẹp**).

Nên ưu tiên ranh giới có ngữ nghĩa ổn định và ghi rõ tập thao tác được bao phủ. Sau khi đổi tên, tách lớp hoặc bổ sung phương thức mới, cần đánh giá lại pointcut.

### 10.2. Hành vi ngầm và thứ tự aspect

Khi nhiều aspect cùng áp dụng tại một join point, thứ tự của chúng có thể ảnh hưởng đến kết quả. Ví dụ kiểm soát truy cập nên xảy ra ở đâu so với đo thời gian hoặc ghi kiểm toán? Nếu đo thời gian nằm bên ngoài một lớp bao khác, số đo bao gồm chi phí của lớp đó; nếu nằm bên trong thì không.

Hãy định nghĩa rõ thứ tự có ý nghĩa và viết kiểm thử ở **ranh giới quan sát được**, thay vì giả định mọi thứ đều hoán đổi được.

### 10.3. Lời gọi nội bộ và phạm vi có thể chặn

Không phải mọi mô hình weaving đều nhìn thấy cùng một loại join point. Với kỹ thuật trung gian ở ranh giới đối tượng, một lời gọi **tự thực hiện bên trong đối tượng** có thể không đi qua lớp trung gian; cơ chế weaving ở mức mã có thể có phạm vi khác. Vì vậy, “đã cấu hình pointcut” **không tự chứng minh** rằng mọi lối đi đều được bao phủ.

Khi thiết kế, cần chỉ rõ đối tượng mục tiêu, cách lời gọi đi vào, loại join point được hỗ trợ và các trường hợp bỏ qua.

### 10.4. Thay đổi ngữ nghĩa do advice quanh lời gọi

Advice dạng around có thể bỏ qua, thay đổi tham số, đổi ngoại lệ, biến đổi giá trị trả về hoặc gọi phần xử lý gốc nhiều lần, tùy cơ chế. Đây là sức mạnh và cũng là nguy cơ. Một advice tự động thử lại thao tác thanh toán có tác dụng phụ, chẳng hạn, cần quy tắc chống thực hiện trùng và hợp đồng lỗi rõ ràng.

Điều kiện quan trọng của một aspect quan sát thuần túy là **không vô ý làm thay đổi kết quả nghiệp vụ**: giá trị trả về, ngoại lệ và số lần thực thi gốc phải được giữ nguyên theo hợp đồng đã chọn.

### 10.5. Tác vụ bất đồng bộ và phạm vi ngữ cảnh

Một lời gọi kết thúc có thể chỉ trả về đối tượng đại diện cho tác vụ chưa hoàn thành. Advice chạy quanh **lời gọi tạo tác vụ** không đương nhiên đo đến lúc tác vụ hoàn tất. Tương tự, thông tin tương quan theo luồng thực thi có thể không tự chuyển sang luồng khác.

Cần quyết định đang đo **thời gian gọi**, **thời gian hoàn tất công việc** hay **thời gian trọn một yêu cầu**. Những phạm vi này khác nhau; đặt advice sai điểm khiến số liệu đúng kỹ thuật nhưng sai ý nghĩa.

### 10.6. Chi phí, bảo mật dữ liệu và khả năng chẩn đoán

Một chính sách quan sát có thể tạo thêm chi phí I/O, cấp phát, ghi log hoặc thu thập dữ liệu nhạy cảm. Tách thành aspect không loại bỏ các chi phí đó. Chỉ ghi dữ liệu cần thiết, hạn chế thông tin bí mật, có khả năng bật/tắt hoặc điều chỉnh mức chi tiết hợp lý và đo đạc trước khi đưa ra kết luận về hiệu năng.

Ngoài kiểm thử nghiệp vụ, cần có cách xác định **aspect nào đã áp dụng vào join point nào** để điều tra khi đường thực thi không khớp với mã nguồn nhìn thấy.

## 11. Quy trình cải thiện thiết kế mà không che giấu nghiệp vụ

1. **Liệt kê mối quan tâm:** phân biệt quy tắc nghiệp vụ (tính tiền, xuất kho) với quy tắc xuyên suốt (trace, kiểm toán, đo thời gian).
2. **Lập bản đồ xuất hiện:** tìm các điểm lặp cùng chính sách (**scattering**) và các phương thức chứa nhiều trách nhiệm (**tangling**). Xác định nơi bắt đầu/kết thúc mỗi thao tác.
3. **Chọn một chính sách có hợp đồng rõ:** ví dụ trace phải ghi tên thao tác, thời điểm bắt đầu, cách kết thúc, và không đổi giá trị/ngoại lệ nghiệp vụ.
4. **Tách phần định nghĩa hành vi:** đưa chính sách khỏi thân nghiệp vụ, trước tiên bằng hàm hay wrapper nếu phù hợp; cân nhắc pointcut/advice khi quy tắc áp dụng thực sự xuyên suốt.
5. **Xác định phạm vi bao phủ:** nêu rõ thao tác được áp dụng, loại trừ, lời gọi nội bộ, trường hợp đồng bộ/bất đồng bộ và đường ngoại lệ.
6. **Kiểm thử trước–sau:** bảo toàn kết quả nghiệp vụ, kiểm tra cả lỗi và tác dụng phụ, xác minh chính sách xuất hiện đúng một lần tại các điểm dự định.
7. **Đánh giá độ rõ ràng sau tách:** người đọc có xác định được lý do một aspect chạy hay không? Nếu quá khó quan sát, cần ranh giới hoặc tên gọi rõ hơn.

**Không nên chuyển mọi dòng dùng chung sang aspect.** Mục tiêu là giảm số lý do thay đổi trong một đơn vị mã và giảm số nơi phải chỉnh sửa cho một chính sách, trong khi giữ được khả năng giải thích luồng chạy.

## 12. Bài tập tự kiểm tra

Xét ba tình huống:

**A.** Mười phương thức tự chèn đoạn “bắt đầu → xử lý → kết thúc”; một phương thức quên ghi khi trả về sớm.

**B.** Chỉ có một phương thức xuất hóa đơn, nhưng trong đó trộn tính thuế, in thông báo chẩn đoán, kiểm tra chính sách truy cập và cập nhật số liệu quan sát.

**C.** Quy tắc khuyến mãi cho đơn hàng bị nhân bản tại hai màn hình, nhưng hai màn hình có điều kiện bán hàng khác nhau.

**Gợi ý trả lời:**

- **A:** dấu hiệu rõ của **scattering** vì một chính sách quan sát phải lặp trên nhiều điểm; có thể kèm **tangling** trong từng phương thức. Cần định nghĩa rõ hành vi tại nhánh trả về sớm và ngoại lệ.
- **B:** dấu hiệu rõ của **tangling** vì nhiều trách nhiệm bị đặt trong cùng luồng; từ một phương thức đơn lẻ **chưa đủ kết luận scattering**.
- **C:** trước khi gom, phải làm rõ hai bản sao cùng một chính sách hay khác nhau về quy tắc nghiệp vụ. Không tự động chọn AOP chỉ vì thấy mã trùng.

Có thể kiểm tra thiết kế bằng bốn câu hỏi: **(1)** Một chính sách chung có đang được chép nhiều nơi không? **(2)** Một phương thức có chứa nhiều lý do độc lập để thay đổi không? **(3)** Sau khi tách, điều gì quyết định điểm áp dụng? **(4)** Giá trị trả về, ngoại lệ và số lần thực thi nghiệp vụ có còn đúng hợp đồng không?

## 13. Ghi nhớ

**Scattering** là *một mối quan tâm trải ra nhiều đơn vị mã*; **tangling** là *nhiều mối quan tâm đan vào một đơn vị mã*. Chúng là hai chiều độc lập để đánh giá cấu trúc chương trình. AOP đưa ra cách mô-đun hóa mối quan tâm xuyên suốt bằng **aspect**, mô tả **join point/pointcut**, gắn **advice** thông qua **weaving**. Một thiết kế tốt vừa giảm việc lặp chính sách, vừa làm nghiệp vụ dễ hiểu, đồng thời luôn kiểm chứng ranh giới áp dụng, đường lỗi, thứ tự và hành vi thực thi thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-004">Vì sao ghi nhật ký, đo thời gian hoặc kiểm soát truy cập dễ lặp lại</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao ghi nhật ký, đo thời gian hoặc kiểm soát truy cập dễ lặp lại?

Một ứng dụng có các chức năng khác nhau như tạo đơn hàng, xuất báo cáo, chuyển tiền, cập nhật hồ sơ. Mỗi chức năng giải quyết một nghiệp vụ riêng, nhưng trước, trong hoặc sau khi thực hiện, ta thường phải làm các việc giống nhau: **kiểm tra ai có quyền gọi, ghi nhận chuyện gì xảy ra và đo thời gian thực thi**. Khi số chức năng tăng, những đoạn mã này xuất hiện lặp lại trên nhiều lớp, nhiều phương thức.

Đây là một động lực hình thành **lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP)**: tách các mối quan tâm dùng chung, cắt ngang nhiều luồng nghiệp vụ khỏi phần thực hiện nghiệp vụ. Bài này giải thích nguyên nhân sự lặp lại, hậu quả thiết kế, tư duy AOP và giới hạn của cách tiếp cận đó.

## 1. Tại sao cùng một chính sách xuất hiện ở nhiều nơi?

Giả sử hệ thống có ba yêu cầu:

- **Nghiệp vụ:** tạo đơn hàng, xuất báo cáo và duyệt thanh toán phải thực hiện đúng quy tắc riêng.
- **Vận hành:** các thao tác quan trọng cần ghi nhận lúc bắt đầu, kết thúc và thất bại; quản trị viên muốn biết thời gian chạy.
- **Truy cập:** chỉ chủ thể đáp ứng chính sách phân quyền được thực hiện một số thao tác nhạy cảm.

Thuật toán tạo đơn hàng không giống xuất báo cáo. Tuy nhiên, cả hai đều có **ranh giới thực thi**: nhận lời gọi → quyết định có cho phép hay không → bắt đầu công việc → trả kết quả hoặc phát sinh lỗi → kết thúc. Đây là những vị trí tự nhiên để đặt log, đồng hồ đo và kiểm tra quyền.

Sự lặp lại thường bắt nguồn từ bốn yếu tố:

1. **Chính sách áp dụng rộng.** Quy tắc “ghi log khi thất bại”, “đo độ trễ thao tác quan trọng” và “kiểm tra quyền trước hành động nhạy cảm” không thuộc riêng một chức năng.
2. **Điểm triển khai thuận tiện nằm ngay trong phương thức.** Người viết phương thức biết rõ nơi bắt đầu, kết thúc, tham số và kết quả, nên dễ chèn các lệnh phụ trợ trực tiếp tại đó.
3. **Các chức năng được tạo độc lập.** Nhiều lập trình viên làm nhiều chức năng; nếu thiếu điểm áp dụng chung, mỗi người phải nhớ triển khai chính sách giống nhau.
4. **Ngoại lệ và đường gọi đa dạng.** Một thao tác có thể thành công, ném ngoại lệ, bị từ chối hoặc được gọi từ nhiều điểm vào. Mỗi phương thức viết thủ công phải xử lý lại các trường hợp này.

Lặp lại ở đây là **lặp chính sách và cách phối hợp hành vi**. Những câu log mang tên phương thức khác nhau vẫn có thể là bản sao của cùng quy tắc “ghi START trước và END sau”.

## 2. Nhìn thấy sự lặp lại trong Java

File `Before.java` dưới đây là chương trình Java 17+ chạy độc lập. Hai nghiệp vụ khác nhau nhưng cùng lặp kiểm tra quyền, log và đo thời gian.

~~~java
public class Before {
    static void createOrder(String role) {
        if (!"ADMIN".equals(role)) {
            throw new SecurityException("Access denied");
        }
        long started = System.nanoTime();
        System.out.println("START createOrder");
        try {
            System.out.println("BUSINESS create order");
        } finally {
            System.out.println("END createOrder; elapsedNs="
                    + (System.nanoTime() - started));
        }
    }

    static void exportReport(String role) {
        if (!"ADMIN".equals(role)) {
            throw new SecurityException("Access denied");
        }
        long started = System.nanoTime();
        System.out.println("START exportReport");
        try {
            System.out.println("BUSINESS export report");
        } finally {
            System.out.println("END exportReport; elapsedNs="
                    + (System.nanoTime() - started));
        }
    }

    public static void main(String[] args) {
        createOrder("ADMIN");
        exportReport("ADMIN");
    }
}
~~~

Chạy:

~~~bash
javac Before.java
java Before
~~~

Mỗi thao tác in dòng `START`, tiếp theo là `BUSINESS`, cuối cùng là `END`. Số nanosecond thay đổi ở mỗi lần chạy. Đo chênh lệch bằng `System.nanoTime()` phù hợp hơn việc lấy hiệu hai thời điểm lịch. Khối `finally` bảo đảm ghi dòng kết thúc khi phần nghiệp vụ ném lỗi.

Có một quyết định quan trọng: kiểm tra quyền diễn ra **trước** khi bắt đầu đo và log. Do đó, lời gọi bị từ chối sẽ không có log `START` hoặc số đo. Nếu yêu cầu của hệ thống là thống kê cả lần bị từ chối, thứ tự trên cần thay đổi. Điều này cho thấy **thứ tự thực thi hành vi dùng chung cũng là chính sách**.

Với hai phương thức, sự lặp trông nhỏ. Với hàng trăm phương thức, muốn bổ sung correlation ID, đổi định dạng log, đo thêm thời gian lỗi hoặc thay quy tắc quyền thì phải sửa nhiều nơi. Một phương thức quên `finally` hoặc quên kiểm tra quyền có thể khiến dữ liệu vận hành thiếu nhất quán, thậm chí tạo lỗ hổng.

## 3. Scattering và tangling: hai mặt của cùng bài toán

AOP dùng hai khái niệm quan trọng:

**Scattering (phân tán):** *một* mối quan tâm xuất hiện ở *nhiều* vị trí. Ví dụ logic đo thời gian bị rải trong 80 phương thức. Muốn sửa một chính sách, ta phải tìm và sửa nhiều bản sao; việc bỏ sót rất dễ xảy ra.

**Tangling (đan xen):** *một* phương thức trộn *nhiều* mối quan tâm. Ví dụ phương thức chuyển tiền vừa kiểm tra số dư và ghi dữ liệu, vừa log, đo thời gian, kiểm tra quyền, thống kê. Người đọc khó thấy phần nào tạo nên hành vi nghiệp vụ thật sự.

| Góc nhìn | Dấu hiệu | Hệ quả |
| --- | --- | --- |
| Scattering | Cùng một quy tắc log/đo thời gian/phân quyền nằm trong nhiều phương thức | Khó đổi đồng loạt, dễ thiếu hoặc lệch chính sách |
| Tangling | Một phương thức trộn logic nghiệp vụ với nhiều lệnh hạ tầng | Khó hiểu, khó kiểm thử, khó truy vết nguyên nhân lỗi |
| Cả hai | Nhiều phương thức đều trộn những chính sách giống nhau | Chi phí bảo trì tăng theo quy mô và số lần thay đổi |

Có thể tự phân biệt bằng hai câu hỏi: **“mối quan tâm này đang nằm ở bao nhiêu nơi?”** là scattering; **“phương thức này phải gánh bao nhiêu mối quan tâm?”** là tangling.

## 4. Vì sao đây là các mối quan tâm cắt ngang?

**Concern (mối quan tâm)** là một trách nhiệm hoặc yêu cầu của phần mềm. Quy tắc tính thuế đơn hàng là một concern nghiệp vụ cụ thể. Trái lại, chính sách “ghi log thất bại” áp dụng được cho cả đơn hàng, báo cáo và nhiều thao tác khác: nó **cắt ngang (cross-cutting)** các chức năng.

Ba mối quan tâm đang xét có mục tiêu khác nhau:

| Mối quan tâm | Câu hỏi | Điểm can thiệp thường gặp | Rủi ro nếu chèn thủ công |
| --- | --- | --- | --- |
| Ghi nhật ký | Ai làm gì, khi nào, kết quả hay lỗi gì? | Trước/sau lời gọi, khi xảy ra ngoại lệ | Thiếu bối cảnh, log trùng, lộ dữ liệu nhạy cảm |
| Đo thời gian | Từ điểm A đến điểm B mất bao lâu? | Ngay trước và sau đoạn cần đo, kể cả lúc lỗi | Sai phạm vi đo, bỏ sót đường lỗi, nhầm elapsed với CPU |
| Kiểm soát truy cập | Chủ thể này có được phép tác động lên tài nguyên này không? | Trước hành động cần bảo vệ | Bỏ sót một đường gọi, tin quyền tự khai, quyết định sai đối tượng |

Ba mối quan tâm cùng **xuất hiện nhiều nơi**, nhưng không có nghĩa chúng áp dụng *giống hệt nhau ở mọi nơi*. Một getter đơn giản có thể không cần log hay đo thời gian; thao tác xóa dữ liệu nhạy cảm có thể đòi hỏi chính sách quyền chặt hơn thao tác xem danh mục công khai.

Một chính sách cắt ngang vì thế có hai phần cần trả lời rõ:

- **Làm gì?** Ví dụ ghi trạng thái lỗi với mã tương quan, đo elapsed time hoặc kiểm tra quyền.
- **Ở đâu và lúc nào?** Ví dụ các thao tác ghi dữ liệu, trước một hành động nhạy cảm, sau khi lời gọi thất bại.

Tách biệt hai câu hỏi này là bước chuyển từ **lặp lệnh ở từng phương thức** sang **quản lý chính sách ở một nơi**.

## 5. Trích xuất helper giúp được gì, còn thiếu gì?

Có thể bắt đầu bằng việc trích xuất hàm tiện ích:

~~~java
long started = Metrics.begin("exportReport");
try {
    // Nghiệp vụ xuất báo cáo
} finally {
    Metrics.end("exportReport", started);
}
~~~

Đây là **mã minh họa, không phải chương trình chạy độc lập**: `Metrics` đại diện một helper giả định. Helper giúp tập trung công thức và định dạng thống kê, nhưng mỗi phương thức vẫn phải **tự gọi đúng helper**, đặt `try/finally` đúng vị trí và nhớ xử lý đường lỗi. Phần gắn chính sách vào điểm thực thi vẫn phân tán.

Những cách tổ chức phổ biến có phạm vi khác nhau:

- **Helper:** tái sử dụng thuật toán, nhưng lời gọi vẫn do từng phương thức đặt.
- **Wrapper/decorator:** đặt hành vi dùng chung quanh đối tượng hoặc giao diện; các lời gọi phải đi qua đối tượng được bọc.
- **Kiểm soát tại ranh giới hệ thống:** xử lý một số chính sách tại một điểm vào hoặc tầng tài nguyên; chỉ bao quát các luồng đi qua ranh giới đó.
- **AOP:** xác định riêng *điểm áp dụng* và *hành vi cắt ngang*, rồi kết hợp chúng theo một cơ chế cụ thể.

Điểm cần giải quyết không chỉ là “xóa đoạn code giống nhau” mà còn là **“bảo đảm chính sách được áp dụng nhất quán ở đúng nơi”**.

## 6. Các khái niệm AOP giải quyết nhu cầu đó thế nào?

Ở mức tư duy AOP nói chung:

- **Join point (điểm nối):** điểm trong quá trình thực thi mà cơ chế AOP có thể tác động, ví dụ lời gọi hoặc thực thi một phương thức. Loại join point hỗ trợ tùy cơ chế.
- **Pointcut (quy tắc chọn điểm):** mô tả *join point nào* cần áp dụng chính sách. Chẳng hạn chọn các thao tác nghiệp vụ nhạy cảm, thay vì mọi phương thức.
- **Advice (hành vi bổ sung):** đoạn xử lý ở trước, sau, khi lỗi hoặc bao quanh một join point. Advice bao quanh có thể tiếp tục lời gọi, quan sát kết quả hoặc ngăn lời gọi.
- **Aspect (khía cạnh):** mô-đun tổ chức một mối quan tâm cắt ngang cùng các quy tắc chọn điểm và hành vi liên quan.
- **Weaving (kết hợp):** kết nối hành vi khía cạnh với chương trình tại những điểm đã chọn. Tùy cơ chế, việc này có thể diễn ra qua biến đổi mã/bytecode hoặc qua đối tượng đại diện lúc chạy.

Một luồng thực thi khả dĩ:

~~~text
Bên gọi
   |
   v
[Điểm thực thi được chọn]
   |
   +--> Kiểm tra quyền
   |      +--> Không đủ quyền: từ chối, không chạy nghiệp vụ
   |
   +--> Log bắt đầu + khởi động đồng hồ
   |
   +--> Chạy nghiệp vụ
   |      +--> Thành công: ghi trạng thái thành công
   |      +--> Lỗi: ghi trạng thái thất bại; truyền lỗi
   |
   +--> Dừng đồng hồ và log kết thúc (kể cả khi lỗi)
   |
   v
Bên gọi nhận kết quả hoặc lỗi
~~~

Sơ đồ chỉ mô tả **một thứ tự chính sách**. Nếu muốn ghi nhận cả yêu cầu bị từ chối, phép ghi log phải bao ngoài kiểm tra quyền. Nếu muốn đo toàn bộ thời gian đi qua lớp bảo vệ, điểm khởi động đồng hồ cũng phải thay đổi.

Nhờ định nghĩa hành vi và vị trí áp dụng riêng, AOP có thể giảm cả scattering và tangling: logic nghiệp vụ vẫn mô tả nghiệp vụ, còn quy tắc dùng chung được quản lý tập trung. Dù vậy, AOP không tự động bảo đảm mọi đường gọi đều được bao phủ hoặc chính sách bảo mật luôn đúng.

## 7. Minh họa Java 17: tập trung logic tại một proxy

Ví dụ dùng **dynamic proxy chuẩn của Java** để diễn giải tư duy AOP ở mức lời gọi qua giao diện. Đây là một kỹ thuật bọc lời gọi, **không phải một hệ thống AOP đầy đủ** với mọi kiểu join point.

Lưu thành `AopConceptDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class AopConceptDemo {
    public interface Task {
        void run();
    }

    public static class OrderTask implements Task {
        @Override
        public void run() {
            System.out.println("BUSINESS create order");
        }
    }

    public static class ReportTask implements Task {
        @Override
        public void run() {
            System.out.println("BUSINESS export report");
        }
    }

    static Task wrap(Task target, String role) {
        return (Task) Proxy.newProxyInstance(
                Task.class.getClassLoader(),
                new Class<?>[] {Task.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(target,
                                args == null ? new Object[0] : args);
                    }

                    String name = target.getClass().getSimpleName()
                            + "." + method.getName();

                    if (!"ADMIN".equals(role)) {
                        System.out.println("DENIED " + name);
                        throw new SecurityException("Access denied");
                    }

                    long started = System.nanoTime();
                    System.out.println("START " + name);
                    try {
                        Object result = method.invoke(target,
                                args == null ? new Object[0] : args);
                        System.out.println("SUCCESS " + name);
                        return result;
                    } catch (InvocationTargetException e) {
                        System.out.println("ERROR " + name);
                        throw e.getCause();
                    } finally {
                        System.out.println("END " + name + "; elapsedNs="
                                + (System.nanoTime() - started));
                    }
                });
    }

    public static void main(String[] args) {
        Task order = wrap(new OrderTask(), "ADMIN");
        Task report = wrap(new ReportTask(), "ADMIN");
        order.run();
        report.run();
    }
}
~~~

Chạy:

~~~bash
javac AopConceptDemo.java
java AopConceptDemo
~~~

Dạng đầu ra (hai số `elapsedNs` không cố định):

~~~text
START OrderTask.run
BUSINESS create order
SUCCESS OrderTask.run
END OrderTask.run; elapsedNs=<số nanosecond>
START ReportTask.run
BUSINESS export report
SUCCESS ReportTask.run
END ReportTask.run; elapsedNs=<số nanosecond>
~~~

Hai cụm `<số nanosecond>` là **ký hiệu minh họa**, không phải stdout nguyên văn; chương trình thực tế in số nguyên sau dấu `=`.

Ở đây, cả hai lời gọi `run()` đi qua proxy. Đoạn xử lý chung kiểm tra quyền, ghi log, đo thời gian và chuyển lời gọi đến đối tượng thật. Trong `OrderTask` và `ReportTask`, chỉ còn phần nghiệp vụ; các chính sách phụ trợ không cần sao chép vào mỗi lớp nữa.

**Giới hạn cần hiểu của ví dụ:**

1. Proxy chỉ can thiệp khi bên gọi **thực sự gọi qua proxy** và giao diện `Task`. Gọi trực tiếp `new OrderTask().run()` bỏ qua đoạn xử lý chung.
2. Lời gọi nội bộ từ một phương thức đến phương thức khác của chính đối tượng qua `this` thường không đi qua proxy. Không thể mặc định mọi lời gọi trong hệ thống đã được bảo vệ.
3. Chuỗi `role` chỉ là dữ liệu đơn giản để minh họa. Hệ thống thật phải lấy danh tính từ nguồn **đã xác thực**, đánh giá quyền với hành động và tài nguyên cụ thể; không tin vai trò do người gọi tự khai.
4. Phương thức gọi bằng reflection có thể ném `InvocationTargetException`, chứa lỗi thực của nghiệp vụ. `throw e.getCause()` bảo toàn lỗi gốc thay vì trả một lỗi bọc khác.
5. Đồng hồ chỉ đo **thời gian lời gọi đồng bộ bên trong proxy**. Nếu phương thức khởi phát một tác vụ bất đồng bộ rồi trả về, đây không phải thời gian tác vụ hoàn tất.

## 8. Ba mối quan tâm trông giống nhau nhưng cần chính sách riêng

### 8.1 Ghi nhật ký

Log nên có cấu trúc, tên thao tác, mã tương quan, trạng thái và loại lỗi phù hợp. Log quá chi tiết ở mọi getter/setter sẽ gây nhiễu; log toàn bộ tham số có thể lộ mật khẩu, token, thông tin cá nhân hoặc dữ liệu nhạy cảm. Chính sách ghi log cần giới hạn loại dữ liệu, mức độ và tần suất.

Nhật ký kỹ thuật kiểu “phương thức bắt đầu/kết thúc” **không thay thế sự kiện nghiệp vụ** như “đơn hàng đã thanh toán” hoặc “hợp đồng được phê duyệt”: ý nghĩa sự kiện cần được xác định ở nơi biết nghiệp vụ.

### 8.2 Đo thời gian

Đầu tiên phải định nghĩa **đo cái gì**: chỉ thân phương thức hay cả bước xác thực, có tính thời gian gọi mạng/cơ sở dữ liệu, có tính thời gian chờ hàng đợi không? Elapsed time là khoảng thời gian trôi qua, không đồng nghĩa CPU time. `System.nanoTime()` thích hợp đo chênh lệch trong một tiến trình.

Muốn đo cả đường lỗi thì dừng đồng hồ trong `finally` hoặc cơ chế tương đương. Với công việc bất đồng bộ, muốn biết thời gian hoàn tất phải quan sát đúng sự kiện hoàn tất của tác vụ, không chỉ lúc hàm trả về.

### 8.3 Kiểm soát truy cập

Kiểm tra quyền phải xảy ra **trước tác dụng phụ cần bảo vệ**. Một cơ chế chung giúp thống nhất kiểm tra ở nhiều điểm vào, nhưng nếu có đường gọi không qua điểm đó thì vẫn có khả năng bỏ sót. Cần kiểm thử cả đường được phép, đường bị từ chối, lời gọi trực tiếp/nội bộ và các điểm vào khác.

Bất biến nghiệp vụ cốt lõi (ví dụ không cho phép chi vượt hạn mức, hoặc chỉ chủ sở hữu được thay đổi một tài nguyên) có thể cần được bảo đảm sâu hơn ở ranh giới miền hoặc dữ liệu. **Tách chính sách dùng chung không có nghĩa giao toàn bộ an toàn cho lớp proxy.**

### 8.4 Thứ tự, ngoại lệ và chi phí

Thứ tự **kiểm tra quyền → đo thời gian → log** khác với **đo thời gian → log → kiểm tra quyền** về phạm vi quan sát, sự kiện ghi nhận và mức tiết lộ thông tin. Advice bao quanh phải quy định rõ các nhánh: trước lời gọi, sau thành công, khi lỗi và cuối cùng. Không được vô tình nuốt ngoại lệ, gọi nghiệp vụ hai lần hoặc ghi “SUCCESS” khi nghiệp vụ thất bại.

Một chuỗi nhiều aspect cũng có chi phí: thứ tự phối hợp phức tạp, điểm thực thi thực tế khó quan sát hơn từ nguồn mã và phép đo có thêm overhead. Việc chọn pointcut quá rộng tạo nhiễu; quá hẹp thì bỏ sót.

## 9. Khi nào nên nghĩ đến AOP?

Tư duy AOP phù hợp khi một chính sách có **cùng ý nghĩa ở nhiều ranh giới**, có quy tắc chọn điểm áp dụng rõ ràng, có thể kiểm thử được phạm vi và thứ tự can thiệp, đồng thời việc tách ra vẫn giữ hợp đồng nghiệp vụ (kết quả, ngoại lệ, tác dụng phụ) đúng như trước.

Không nên đưa vào aspect mọi đoạn mã trùng nhau. Quyết định giá bán, chuyển trạng thái đơn hàng, điều kiện của một giao dịch và các quy tắc miền có thể giống ở hình thức nhưng thay đổi độc lập theo nghiệp vụ. Gom chúng thành một chính sách cắt ngang có thể khiến thiết kế khó hiểu và phụ thuộc sai.

Khi thiết kế, hãy hỏi:

- Đây có thật sự là **một chính sách dùng chung**, hay chỉ là các đoạn code giống nhau tình cờ?
- Các điểm nào cần được áp dụng, điểm nào phải loại trừ? Có đường gọi nào có thể bỏ qua không?
- Thứ tự can thiệp, nhánh ngoại lệ và thời gian đo có được định nghĩa rõ không?
- Có giữ nguyên hành vi nghiệp vụ và bao quát đúng các hành động nhạy cảm không?
- Có cách kiểm thử và quan sát chính sách đang hoạt động trong runtime không?

## 10. Tự kiểm tra

Một hệ thống có 40 phương thức, mỗi phương thức đều tự đặt log `START/END`, đồng hồ đo và kiểm tra quyền.

**Hỏi:** Scattering nằm ở đâu? **Đáp:** cùng một chính sách được lặp trong 40 phương thức.

**Hỏi:** Tangling nằm ở đâu? **Đáp:** từng phương thức chứa cả mã nghiệp vụ lẫn log, đo thời gian, kiểm tra quyền.

**Hỏi:** Trích xuất helper đã giải quyết hết chưa? **Đáp:** chưa; 40 phương thức vẫn phải gọi helper và đặt đúng điểm bắt đầu/kết thúc.

**Hỏi:** AOP hỗ trợ phần nào? **Đáp:** tổ chức hành vi phụ trợ và quy tắc chọn điểm thực thi riêng, để áp dụng nhất quán mà giảm mã gắn thủ công ở từng nghiệp vụ.

**Hỏi:** Điều gì phải kiểm chứng sau khi tách? **Đáp:** tất cả điểm cần bảo vệ được bao phủ; kết quả, lỗi, thời gian đo và thứ tự log/quyền đúng; đường bỏ qua proxy hoặc đường bất đồng bộ được xử lý theo yêu cầu thực tế.

**Ghi nhớ:** những thao tác này dễ lặp lại vì chúng phục vụ **chính sách xuyên qua nhiều chức năng** trong khi cách viết thủ công đặt chúng ngay trong từng phương thức. AOP giúp nhìn ra và quản lý **scattering** và **tangling**. Lợi ích chỉ trọn vẹn khi việc chọn điểm áp dụng, thứ tự thực thi, ngoại lệ và ranh giới bảo mật đều được thiết kế và kiểm tra cẩn thận.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-005">Tách hành vi cắt ngang khỏi luồng nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Tách hành vi cắt ngang khỏi luồng nghiệp vụ bằng AOP

## 1. Vấn đề cần giải quyết

Trong một ứng dụng, **luồng nghiệp vụ** là chuỗi hành động tạo ra kết quả mà người dùng hoặc hệ thống yêu cầu: kiểm tra điều kiện của đơn hàng, tính tiền, đặt chỗ, xác nhận giao dịch, cập nhật trạng thái. Ngoài các bước đó, nhiều thao tác kỹ thuật cần xuất hiện ở nhiều nơi: ghi nhật ký, đo thời gian, kiểm soát truy cập, theo dõi lỗi, gắn mã truy vết, hoặc xác định ranh giới giao dịch.

Những thao tác lặp lại qua nhiều chức năng được gọi là **hành vi cắt ngang (cross-cutting concerns)**. Chẳng hạn, cả `placeOrder()`, `cancelOrder()` và `refund()` đều có thể cần kiểm tra quyền và ghi audit, dù mỗi hàm xử lý một quy tắc nghiệp vụ riêng.

Nếu nhúng mọi hành vi kỹ thuật vào từng hàm nghiệp vụ, chúng ta dễ gặp:

- **Trộn trách nhiệm:** khó nhận ra hàm thực sự làm gì vì mã ghi log, đo thời gian và xử lý lỗi bao quanh mã nghiệp vụ.
- **Phân tán và lặp:** một thay đổi trong chuẩn audit phải sửa nhiều nơi; có chỗ quên ghi hoặc ghi không nhất quán.
- **Ràng buộc không cần thiết:** mã nghiệp vụ phụ thuộc trực tiếp vào công cụ logging, tracing, metrics hoặc cơ chế điều phối kỹ thuật.
- **Khó kiểm thử và bảo trì:** kiểm thử quy tắc đặt hàng bị kéo theo nhiều tác dụng phụ; thêm một chính sách chung có nguy cơ làm hỏng nhiều luồng.
- **Sai lệch chính sách:** mỗi nhóm tự đặt thứ tự kiểm quyền, thực thi, ghi kết quả, khiến cùng một loại hành động được xử lý khác nhau.

**Mục tiêu của AOP (Aspect-Oriented Programming)** là mô-đun hóa những hành vi cắt ngang, định nghĩa *ở đâu* chúng cần áp dụng và *khi nào* chúng chạy, để mã nghiệp vụ tập trung vào nghiệp vụ. AOP bổ sung cho thiết kế hướng đối tượng; nó không thay thế việc chia lớp, phân lớp trách nhiệm hoặc thiết kế miền.

## 2. Nhận diện đúng ranh giới giữa nghiệp vụ và hành vi cắt ngang

| Loại trách nhiệm | Ví dụ | Nơi thường phù hợp |
| --- | --- | --- |
| Nghiệp vụ cốt lõi | Số lượng mua phải dương; kho còn đủ hàng; tính tổng tiền theo chính sách bán hàng | Phương thức, đối tượng và dịch vụ miền |
| Hành vi cắt ngang | Đo độ trễ của lời gọi; ghi sự kiện audit; gắn trace ID; ghi nhận lỗi chung | Aspect hoặc cơ chế bao quanh điểm thực thi |
| Trách nhiệm phối hợp | Chọn bước tiếp theo; gọi nhiều dịch vụ theo thứ tự; quản lý kết quả tác vụ | Lớp điều phối/use case; chỉ dùng aspect nếu thực sự là chính sách cắt ngang |

Không phải cứ một đoạn mã xuất hiện ở nhiều hàm thì phải chuyển thành aspect. Ví dụ, quy tắc **“không được hủy đơn đã giao”** là bất biến nghiệp vụ: nó phải ở nơi quản lý trạng thái đơn hàng. Việc **“mỗi thao tác hủy đơn cần ghi lại ai đã thực hiện và kết quả ra sao”** có thể là chính sách audit dùng chung.

Tương tự, kiểm tra đăng nhập tại một ranh giới kỹ thuật có thể là hành vi cắt ngang; nhưng quyết định người mua có được hủy **đơn hàng cụ thể** hay không thường cần chính trạng thái và quyền sở hữu của đơn, vì vậy không nên giấu toàn bộ quyết định miền trong một aspect khó nhìn thấy.

Có thể tự hỏi ba câu:

1. Nếu bỏ phần kỹ thuật này đi, thuật toán nghiệp vụ còn mô tả đúng bài toán không?
2. Có thể áp dụng **cùng một chính sách** tại nhiều điểm mà không cần biết chi tiết tính toán của từng nghiệp vụ không?
3. Có thể nêu rõ vị trí áp dụng, thứ tự chạy và hành vi khi thất bại không?

Nếu câu trả lời chủ yếu là “có”, đây là ứng viên tốt để tách thành hành vi cắt ngang.

## 3. Các khái niệm AOP giúp việc tách có thể thực thi

- **Aspect:** mô-đun chứa một mối quan tâm cắt ngang, ví dụ audit hoặc đo thời gian. Một aspect có thể chứa chính sách chọn điểm áp dụng và logic bổ sung.
- **Join point (điểm nối):** một vị trí thực thi có thể bị can thiệp, thường là lời gọi hoặc lần thực thi phương thức; tập join point thực tế tùy vào cơ chế AOP.
- **Pointcut (bộ chọn điểm nối):** điều kiện chọn *những join point nào* được áp dụng, chẳng hạn “mọi lần thực thi thao tác tạo đơn hàng”. Pointcut quá rộng dễ gây tác dụng phụ; quá hẹp khiến sót luồng.
- **Advice (hành vi được chèn):** phần mã thực thi tại join point đã chọn.
- **Target:** đối tượng hoặc phần mã nghiệp vụ gốc.
- **Weaving (kết hợp):** quá trình gắn aspect vào luồng thực thi; có thể được thực hiện bằng mã bao, proxy, biến đổi bytecode khi biên dịch hoặc khi nạp lớp, tùy công nghệ.

Các vị trí advice thường được mô tả như sau:

| Loại advice | Thời điểm / trách nhiệm |
| --- | --- |
| **Before** | Trước khi xử lý mục tiêu; phù hợp kiểm tra điều kiện kỹ thuật hoặc ghi sự kiện bắt đầu |
| **After returning** | Khi mục tiêu hoàn thành bình thường; chỉ có kết quả thành công |
| **After throwing** | Khi mục tiêu ném ngoại lệ; thường để ghi nhận thất bại rồi bảo toàn lỗi gốc |
| **After / finally** | Khi mục tiêu kết thúc, dù thành công hay lỗi; phù hợp dọn dẹp hoặc đo thời gian |
| **Around** | Bao lấy toàn bộ lời gọi; có thể thực hiện trước/sau, đọc kết quả, xử lý lỗi và quyết định có gọi tiếp hay không |

Trong `around`, thao tác chuyển quyền điều khiển tới mã gốc thường được gọi là **proceed**. Nếu không gọi `proceed`, nghiệp vụ có thể không chạy; gọi nhiều lần có thể khiến nghiệp vụ chạy nhiều lần. Đây là quyền rất mạnh, phải dùng có chủ đích.

Hình dung luồng xử lý:

~~~text
Người gọi
  |
  v
[Pointcut có chọn lần gọi này?] -- không --> [Nghiệp vụ]
  |
  có
  v
[Before / phần trước của Around]
  |
  v
[Nghiệp vụ gốc (proceed)]
  | thành công                 | ngoại lệ
  v                            v
[After returning]           [After throwing]
  |                            |
  +------------+---------------+
               v
      [After / finally]
               |
               v
        Trả kết quả hoặc ném lỗi gốc
~~~

Đây là mô hình ý niệm: từng cơ chế AOP có quy tắc riêng về thứ tự các advice và cách biểu diễn điểm nối; không suy ra rằng mọi cách triển khai đều có cùng khả năng can thiệp.

## 4. Ví dụ Java 17+: nghiệp vụ độc lập, audit và metrics nằm ngoài

Xét thao tác tạo đơn hàng. Điều kiện số lượng mua phải dương thuộc miền nghiệp vụ; audit và đo thời gian là chính sách cắt ngang. Ví dụ dùng **JDK dynamic proxy** để minh họa một kiểu kết hợp tại ranh giới lời gọi qua interface. Nó không phải một bộ máy AOP tổng quát: pointcut ở đây chỉ là một `Predicate<Method>` do ta định nghĩa.

Tạo tệp `AopBoundaryDemo.java`:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

public class AopBoundaryDemo {
    interface Checkout {
        String placeOrder(String item, int quantity);
        String status();
    }

    // Chỉ xử lý quy tắc nghiệp vụ.
    static final class CheckoutCore implements Checkout {
        @Override
        public String placeOrder(String item, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            System.out.println("BUSINESS: order accepted");
            return item + " x" + quantity;
        }

        @Override
        public String status() {
            return "READY";
        }
    }

    // Nơi thu thập dữ liệu đo lường.
    static final class Metrics {
        static int calls;
        static long totalNanos;

        static void record(long elapsedNanos) {
            calls++;
            totalNanos += elapsedNanos;
        }
    }

    // Bao lời gọi được chọn bằng một chính sách dùng chung.
    static final class CrossCuttingHandler implements InvocationHandler {
        private final Object target;
        private final Predicate<Method> pointcut;

        CrossCuttingHandler(Object target, Predicate<Method> pointcut) {
            this.target = target;
            this.pointcut = pointcut;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (!pointcut.test(method)) {
                return invokeTarget(method, args);
            }

            System.out.println("AUDIT: begin");
            long started = System.nanoTime();
            try {
                Object result = invokeTarget(method, args);
                System.out.println("AUDIT: success");
                return result;
            } catch (Throwable cause) {
                System.out.println("AUDIT: failed");
                throw cause;
            } finally {
                Metrics.record(System.nanoTime() - started);
            }
        }

        private Object invokeTarget(Method method, Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapped) {
                throw wrapped.getCause();
            }
        }
    }

    public static void main(String[] args) {
        Checkout core = new CheckoutCore();
        Predicate<Method> pointcut = method ->
                method.getName().equals("placeOrder")
                && method.getParameterCount() == 2;

        Checkout observed = (Checkout) Proxy.newProxyInstance(
                Checkout.class.getClassLoader(),
                new Class<?>[] { Checkout.class },
                new CrossCuttingHandler(core, pointcut));

        System.out.println("STATUS: " + observed.status());
        System.out.println("RESULT: " + observed.placeOrder("Book", 2));
        try {
            observed.placeOrder("Book", 0);
        } catch (IllegalArgumentException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
        System.out.println("METRIC: measured calls = " + Metrics.calls);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 AopBoundaryDemo.java
java AopBoundaryDemo
~~~

Kết quả chuẩn:

~~~text
STATUS: READY
AUDIT: begin
BUSINESS: order accepted
AUDIT: success
RESULT: Book x2
AUDIT: begin
AUDIT: failed
ERROR: quantity must be positive
METRIC: measured calls = 2
~~~

**Cách đọc kết quả:**

1. `status()` không khớp pointcut nên đi thẳng tới `CheckoutCore.status()`, không tạo audit hay metrics.
2. `placeOrder("Book", 2)` khớp: audit bắt đầu → nghiệp vụ chạy → audit thành công → kết quả trả về cho người gọi.
3. `placeOrder("Book", 0)` cũng khớp: nghiệp vụ ném `IllegalArgumentException` → audit thất bại → metrics vẫn ghi trong `finally` → ngoại lệ gốc tới người gọi.
4. `InvocationTargetException` là lớp bọc của lời gọi phản chiếu; `invokeTarget()` bóc lỗi này để người gọi nhận nguyên nhân gốc. Phần đo thời gian dùng `System.nanoTime()`, không in số nano giây vì mỗi lần chạy khác nhau.

Điểm then chốt: **`CheckoutCore` không biết audit, metrics, proxy hoặc pointcut tồn tại**. Chính nơi lắp ghép `core` với `CrossCuttingHandler` quyết định chính sách cắt ngang được áp dụng; người gọi tương tác qua cùng interface `Checkout`.

Ví dụ này cố ý minh họa một chính sách kỹ thuật nhỏ, không đại diện cho toàn bộ tính năng của AOP. JDK dynamic proxy chỉ can thiệp lời gọi được thực hiện **thông qua proxy và interface**; một đối tượng `CheckoutCore` được gọi trực tiếp sẽ không đi qua `CrossCuttingHandler`.

## 5. Ba mức độ tách trách nhiệm

**Mức 1 — Trộn trong nghiệp vụ.** Mỗi hàm tự ghi audit, tự đo thời gian, tự xử lý chính sách lặp lại. Mã dễ viết lúc đầu nhưng khó thay đổi nhất quán.

**Mức 2 — Bao thủ công bằng decorator hoặc wrapper.** Một lớp triển khai cùng interface và chuyển tiếp tới lớp gốc. Điều này đã giúp tách trách nhiệm; đổi lại phải viết/lắp wrapper cho các điểm cần bao. Không phải mọi decorator đều là AOP; decorator là kỹ thuật thiết kế có thể hiện thực hóa ý tưởng tách hành vi.

**Mức 3 — Mô tả aspect, pointcut và advice.** Cơ chế AOP lựa chọn join point theo quy tắc và gắn hành vi thông qua proxy hoặc weaving. Có thể tái sử dụng cùng chính sách trên nhiều mục tiêu mà mã nghiệp vụ không trực tiếp gọi aspect.

Độ tự động hóa cao hơn không mặc nhiên tạo ra thiết kế tốt hơn. Một wrapper rõ ràng có thể thích hợp cho vài lời gọi; aspect có ý nghĩa khi chính sách đủ thống nhất, phạm vi áp dụng đủ rõ và lợi ích giảm lặp lớn hơn chi phí hiểu luồng thực thi.

## 6. Thiết kế pointcut và thứ tự advice cho đúng

Một pointcut hữu ích thường chọn theo **vai trò hoặc hợp đồng** thay vì ghép tên tùy tiện. Ví dụ, chọn các thao tác đã được đánh dấu là cần audit có thể ổn định hơn việc chọn mọi phương thức bắt đầu bằng `save`. Tuy nhiên, dấu đánh dấu phải được đặt nhất quán và người đọc cần tra ra được điểm nào bị áp dụng.

Quy trình thực tế:

1. **Khoanh vùng nghiệp vụ:** liệt kê các hành động cần giữ độc lập và các bất biến chỉ miền nghiệp vụ mới quyết định được.
2. **Liệt kê chính sách cắt ngang:** audit, metrics, bảo mật ở ranh giới, tracing, quản lý tài nguyên; ghi rõ đầu vào, đầu ra và hành vi khi lỗi.
3. **Chọn join point:** xác định lời gọi hoặc lần thực thi nào có ý nghĩa, kể cả nhánh thất bại và đường gọi bất đồng bộ.
4. **Viết pointcut hẹp và kiểm tra được:** mô tả tiêu chí chọn, liệt kê vài trường hợp *phải khớp* và *không được khớp*.
5. **Đặt thứ tự advice:** chẳng hạn kiểm quyền trước thao tác; ghi audit kết quả sau thao tác; đo thời gian trong `finally`. Nếu có nhiều advice `around` lồng nhau, thứ tự ngoài–trong ảnh hưởng thời điểm ghi nhận và loại ngoại lệ được quan sát.
6. **Kiểm thử tại ranh giới thật:** gọi qua entry point đã được can thiệp, không chỉ gọi lớp gốc; xác nhận cả thành công, ngoại lệ và đường không khớp pointcut.

Ví dụ chính sách tổng quát cho một thao tác nhạy cảm:

~~~text
Caller
  -> Authorize (trước khi cho phép tiến hành)
    -> Measure duration (bắt đầu đo)
      -> Execute business operation
    <- Measure duration (finally, dù thành công hay lỗi)
  <- Audit result (thành công / thất bại)
<- Return hoặc throw
~~~

Đây chỉ là một cách sắp xếp. Nếu audit bắt buộc ghi cả thao tác **bị từ chối trước khi chạy nghiệp vụ**, audit phải bao ngoài bước kiểm quyền hoặc phải có đường ghi từ chối riêng. Nếu đo thời gian phục vụ SLA của người dùng, điểm bắt đầu/kết thúc có thể cần bao gồm cả kiểm quyền; nếu muốn đo riêng nghiệp vụ, phạm vi lại khác. Thứ tự phải xuất phát từ ý nghĩa dữ liệu đo/ghi.

## 7. Những góc cạnh nâng cao thường bị bỏ sót

### 7.1. Ranh giới can thiệp không đồng nhất

Cơ chế proxy thường chỉ thấy lời gọi đi qua đối tượng đại diện. Nếu một phương thức của lớp gốc gọi trực tiếp một phương thức khác trên chính `this`, lời gọi nội bộ có thể bỏ qua proxy (**self-invocation**). Các cơ chế weaving ở mức bytecode có phạm vi khác; cần kiểm tra chính xác loại join point mà công nghệ sử dụng hỗ trợ.

Tương tự, không thể mặc định rằng phương thức `private`, `final`, constructor, truy cập trường hoặc các lời gọi bất đồng bộ đều được can thiệp như nhau. Hãy mô tả giới hạn của cơ chế triển khai trước khi dựa vào aspect cho một bảo đảm quan trọng.

### 7.2. Ngoại lệ, kết quả và tính trong suốt

Một advice quan sát lý tưởng không được vô tình đổi giá trị trả về, loại ngoại lệ hoặc số lần thực thi nghiệp vụ. Nếu một advice `around` bắt và nuốt lỗi, người gọi có thể hiểu nhầm tác vụ đã thành công. Nếu aspect ghi log ném thêm lỗi, nó có thể che lỗi nghiệp vụ gốc. Với audit/metrics, nên có chính sách rõ cho trường hợp hạ tầng ghi nhận gặp lỗi.

Các advice **có chủ đích thay đổi hành vi** (kiểm quyền, retry, timeout) phải công bố hợp đồng: điều kiện từ chối, ngoại lệ nào được ném, khi nào dừng và có cho phép thực thi lại hay không.

### 7.3. Retry và tính lặp an toàn

Một aspect retry dùng `proceed` nhiều lần có thể lặp cả thanh toán, đặt hàng hoặc gửi thông báo. Retry chỉ nên áp dụng khi có điều kiện xác định lỗi tạm thời, giới hạn số lần và cơ chế bảo đảm an toàn lặp (idempotency) phù hợp. Không biến “mọi ngoại lệ” thành “thử lại”.

### 7.4. Dữ liệu nhạy cảm và ngữ cảnh thực thi

Audit/tracing không có nghĩa là ghi toàn bộ tham số, token, thông tin cá nhân hoặc payload vào log. Chỉ giữ dữ liệu cần thiết, che nội dung nhạy cảm và định rõ thời hạn lưu. Khi luồng đi qua thread, tác vụ bất đồng bộ hoặc ranh giới tiến trình, ngữ cảnh như correlation ID không tự nhiên luôn theo sang nơi mới; phải có cơ chế truyền ngữ cảnh được thiết kế rõ.

### 7.5. Hiệu năng, độ quan sát và gỡ lỗi

Advice chạy ở mọi join point khớp nên pointcut quá rộng có thể tăng overhead, tạo quá nhiều sự kiện hoặc vô tình lặp log. Aspect cũng khiến luồng chạy thực tế khác cấu trúc hiển thị trong mã nghiệp vụ. Cần tài liệu ngắn về pointcut, thứ tự advice và cách bật/tắt quan sát; đo overhead ở tải đại diện trước khi mở rộng phạm vi.

### 7.6. Giao dịch và ranh giới tài nguyên

Mở/đóng giao dịch hoặc dọn dẹp tài nguyên thường có tính cắt ngang, nhưng phạm vi giao dịch phải gắn với **đơn vị công việc** phù hợp. Đặt ranh giới quá nông có thể tách một nghiệp vụ thành nhiều phần không nhất quán; đặt quá rộng có thể giữ tài nguyên lâu. Hành vi rollback khi ngoại lệ cũng cần định nghĩa minh bạch, không được suy luận từ tên aspect.

## 8. Kiểm thử việc tách có thực sự đúng

Dùng ba nhóm kiểm thử khác nhau:

| Bài kiểm tra | Thao tác | Điều cần khẳng định |
| --- | --- | --- |
| Nghiệp vụ thuần | Gọi trực tiếp `CheckoutCore.placeOrder()` | Quy tắc số lượng và kết quả không phụ thuộc audit/metrics |
| Can thiệp đúng điểm | Gọi `placeOrder()` qua proxy, rồi gọi `status()` | Chỉ phương thức khớp pointcut mới được ghi nhận |
| Thành công và thất bại | Gọi số lượng dương và không dương qua proxy | Audit ghi đúng trạng thái; metrics chạy trên cả hai nhánh; ngoại lệ gốc được bảo toàn |

Khi nhiều aspect cùng áp dụng, thêm kiểm thử **thứ tự** và **số lần gọi nghiệp vụ**; khi có retry, bảo mật hoặc giao dịch, kiểm thử các trường hợp bị từ chối, timeout, lỗi hạ tầng và tính nhất quán dữ liệu.

Một tiêu chí thiết kế dễ kiểm tra: **thay đổi định dạng audit hoặc công cụ đo thời gian không đòi sửa `CheckoutCore`; thay đổi quy tắc số lượng đặt hàng không đòi sửa `CrossCuttingHandler`.** Nếu hai phía vẫn buộc phải biết chi tiết triển khai của nhau, việc tách chưa đạt.

## 9. Sai lầm thường gặp và cách tránh

- **Nhầm mọi logic dùng chung thành cross-cutting:** giữ bất biến và quyết định miền trong mô hình nghiệp vụ; chỉ tách chính sách thật sự chung.
- **Pointcut bằng tên quá rộng:** vô tình can thiệp cả getter, hàm tiện ích hoặc lời gọi không mong muốn; hãy kiểm tra tập khớp và trường hợp loại trừ.
- **Cho rằng aspect luôn chạy:** cuộc gọi trực tiếp, self-invocation hoặc loại join point không được hỗ trợ có thể bỏ qua cơ chế can thiệp.
- **Dùng `around` cho mọi thứ:** ưu tiên advice đơn giản hơn khi chỉ cần quan sát trước/sau; `around` làm tăng nguy cơ thay đổi luồng điều khiển.
- **Nuốt ngoại lệ hoặc sửa kết quả ngoài ý muốn:** bảo toàn hợp đồng đầu ra; việc biến đổi phải là chính sách được chỉ định rõ.
- **Phụ thuộc thứ tự ngầm:** xác định thứ tự aspect và đo xem advice ngoài/đóng có chạy cả khi lỗi hay không.
- **Đưa quá nhiều dữ liệu vào audit:** phân loại và loại bỏ dữ liệu nhạy cảm trước khi ghi.
- **Ẩn chính sách quan trọng:** các điều kiện bảo mật, retry và giao dịch cần được ghi rõ tại ranh giới áp dụng, có kiểm thử bao phủ.

## 10. Ghi nhớ

AOP giải quyết bài toán **mô-đun hóa hành vi cắt ngang** bằng cách tách ba quyết định: **mã nghiệp vụ làm gì**, **chính sách kỹ thuật làm gì** và **chính sách được áp dụng ở những điểm thực thi nào**. Khi ba quyết định này rõ ràng, nghiệp vụ dễ đọc và kiểm thử hơn; cùng một chính sách có thể được duy trì nhất quán. Điểm cần kiểm soát là tính chính xác của pointcut, thứ tự advice, phạm vi weaving/proxy và việc bảo toàn kết quả hoặc ngoại lệ của luồng gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-006">Quan hệ giữa AOP, lập trình thủ tục và lập trình hướng đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa AOP, lập trình thủ tục và lập trình hướng đối tượng

## 1. Bức tranh tổng thể

**Lập trình thủ tục (procedural programming)**, **lập trình hướng đối tượng (object-oriented programming — OOP)** và **lập trình hướng khía cạnh (aspect-oriented programming — AOP)** trả lời ba nhóm câu hỏi khác nhau khi tổ chức phần mềm:

- **Thủ tục:** Những bước xử lý nào cần thực hiện, theo trình tự và luồng điều khiển nào?
- **Hướng đối tượng:** Dữ liệu và hành vi thuộc trách nhiệm của đối tượng nào? Các đối tượng phối hợp với nhau ra sao?
- **Hướng khía cạnh:** Một chính sách hoặc hành vi chung xuất hiện ở nhiều vị trí nên được mô tả và gắn vào các điểm thực thi nào?

Ba cách tiếp cận **không tạo thành một chuỗi thay thế nhau**. Một ứng dụng hướng đối tượng vẫn dùng thuật toán và thủ tục trong các phương thức; AOP có thể bổ sung cơ chế tách các mối quan tâm trải rộng qua nhiều chức năng. Tùy ngôn ngữ và công cụ, AOP có thể kết hợp với chương trình hướng đối tượng hoặc chương trình tổ chức theo hàm/thủ tục.

Ví dụ, khi xử lý đơn hàng, quy tắc tính tổng tiền là **nghiệp vụ cốt lõi**. Ghi nhận thời gian thực thi, tạo dấu vết kiểm toán và áp dụng một chính sách quan sát chung có thể là các **mối quan tâm xuyên suốt** (*cross-cutting concerns*) vì chúng xuất hiện ở nhiều thao tác như tạo đơn, hoàn tiền và hủy đơn. AOP tập trung vào cách biểu đạt và kết hợp những mối quan tâm xuyên suốt đó.

## 2. Từng mô hình tổ chức chương trình như thế nào?

### 2.1. Lập trình thủ tục: tổ chức theo bước và hàm

Chương trình được chia thành các thủ tục/hàm. Luồng điều khiển chủ yếu được thể hiện qua lời gọi hàm, điều kiện, vòng lặp và cấu trúc dữ liệu. Ví dụ dưới đây là **mã giả**, nhằm trình bày luồng xử lý:

```text
procedure checkout(order):
    requirePermission(order)
    recordAudit("checkout started")
    result = chargePayment(order)
    recordAudit("checkout finished")
    return result

procedure refund(order):
    requirePermission(order)
    recordAudit("refund started")
    result = reversePayment(order)
    recordAudit("refund finished")
    return result
```

Điểm mạnh là dễ theo dõi thứ tự xử lý trực tiếp ở mỗi thủ tục, phù hợp với các thuật toán và tác vụ có luồng tuần tự rõ ràng. Tuy nhiên, nếu hàng chục thủ tục đều phải thực hiện cùng một chính sách kiểm toán, kiểm tra quyền hoặc đo thời gian, mã chung dễ **lặp lại hoặc phân tán**.

Có thể tách phần chung thành các hàm như `recordAudit()` hay `requirePermission()`; điều này giảm trùng lặp bên trong logic chung, nhưng mỗi thủ tục vẫn cần **gọi chúng đúng chỗ**, trừ khi sử dụng thêm một cơ chế bao bọc hoặc tự động kết hợp khác. Đây là vấn đề về *vị trí áp dụng chính sách*, không phải nhược điểm tuyệt đối của lập trình thủ tục.

### 2.2. OOP: tổ chức theo dữ liệu, trạng thái và trách nhiệm

OOP gom dữ liệu và hành vi liên quan vào đối tượng, sử dụng đóng gói, trừu tượng, đa hình và quan hệ giữa các kiểu để quản lý độ phức tạp. Ví dụ, `CheckoutService` chịu trách nhiệm thanh toán, còn `RefundService` chịu trách nhiệm hoàn tiền. Mỗi phương thức bên trong đối tượng vẫn có các bước thực thi tuần tự.

Mẫu Java 17 dưới đây đặt cạnh nhau một hàm xử lý kiểu thủ tục và hai phương thức hướng đối tượng; phần kiểm toán được viết tại từng điểm gọi để làm rõ vấn đề phân tán:

```java
public class CheckoutDemo {
    static void checkoutProcedural(String orderId) {
        System.out.println("[procedural:audit] start");
        System.out.println("[procedural] checkout " + orderId);
        System.out.println("[procedural:audit] end");
    }

    static final class CheckoutService {
        void checkout(String orderId) {
            System.out.println("[audit] start checkout");
            System.out.println("checkout " + orderId);
            System.out.println("[audit] end checkout");
        }
    }

    static final class RefundService {
        void refund(String orderId) {
            System.out.println("[audit] start refund");
            System.out.println("refund " + orderId);
            System.out.println("[audit] end refund");
        }
    }

    public static void main(String[] args) {
        checkoutProcedural("P-01");
        new CheckoutService().checkout("O-02");
        new RefundService().refund("O-03");
    }
}
```

Lưu tệp dưới tên `CheckoutDemo.java` rồi chạy:

```bash
javac CheckoutDemo.java
java CheckoutDemo
```

Kết quả chính xác:

```text
[procedural:audit] start
[procedural] checkout P-01
[procedural:audit] end
[audit] start checkout
checkout O-02
[audit] end checkout
[audit] start refund
refund O-03
[audit] end refund
```

Mẫu này chủ ý tối giản: chưa có thanh toán thật, lỗi mạng hay giao dịch dữ liệu. Nó cho thấy OOP có thể tách trách nhiệm thanh toán và hoàn tiền vào các đối tượng, nhưng các dòng kiểm toán vẫn nằm rải rác trong nhiều phương thức.

OOP đã có các công cụ giảm lặp như kế thừa, composition, decorator, interceptor hoặc helper. Chúng đủ tốt trong nhiều tình huống. Tuy nhiên, nếu một quy tắc phải gắn ở nhiều lớp không có cùng quan hệ kế thừa, dùng chung superclass dễ gây gắn kết không cần thiết; dùng decorator hoặc interceptor thủ công lại đòi hỏi quản lý nơi bọc và nơi gọi. AOP đưa ra một cách diễn đạt khác cho quy tắc trải rộng này.

### 2.3. AOP: tổ chức theo mối quan tâm xuyên suốt và điểm áp dụng

AOP mô tả một hành vi chung thành **aspect** và định nghĩa **những điểm thực thi** nơi hành vi đó được áp dụng. Trong ví dụ, mã xử lý thanh toán và hoàn tiền tập trung vào nghiệp vụ, còn quy tắc kiểm toán được mô tả một lần trong aspect.

Minh họa dưới đây là **mã giả AOP trung lập công cụ**, không phải cú pháp Java hay chương trình có thể biên dịch trực tiếp:

```text
aspect OperationAudit:
    pointcut trackedOperations =
        execution(CheckoutService.checkout)
        OR execution(RefundService.refund)

    around trackedOperations(invocation):
        audit("start " + invocation.name)
        try:
            return proceed(invocation)
        finally:
            audit("end " + invocation.name)
```

Trong mã giả, `pointcut` chọn hai điểm thực thi, `around` bao phần xử lý, và `proceed` tiếp tục gọi thao tác gốc. Khi cơ chế **weaving** phù hợp đã kết hợp aspect với mã chương trình, bên gọi vẫn gọi `checkout()` hoặc `refund()`, còn logic kiểm toán được thực thi tại các điểm được chọn. `finally` minh họa ý định ghi nhận kết thúc kể cả khi thao tác gốc ném lỗi; hành vi cụ thể vẫn tùy loại advice và công cụ AOP.

Chỉ viết ra một mô tả aspect **chưa đủ để chương trình Java tự động chạy nó**. Cần có cơ chế nhận diện điểm nối và kết hợp advice vào quá trình thực thi. Các công cụ AOP khác nhau có cú pháp, loại điểm nối và giới hạn khác nhau.

## 3. AOP bổ sung chiều trách nhiệm nào cho thủ tục và OOP?

Để so sánh ba mô hình, chỉ cần một bộ thuật ngữ đủ dùng: **join point** là sự kiện mà công cụ AOP có thể can thiệp, **pointcut** chọn tập sự kiện, **advice** là hành vi được thực hiện, **aspect** gom chính sách cùng cách áp dụng, và **weaving/interception** đưa hành vi vào đường chạy. Nghiệp vụ gốc là **target**; loại sự kiện được hỗ trợ phụ thuộc kỹ thuật triển khai.

Trong thủ tục hoặc OOP, người gọi hay đối tượng thường thể hiện trực tiếp cách phối hợp hành vi:

```text
Caller → auditStart() → checkout() → auditEnd()
```

Với AOP, quy tắc áp dụng được đặt tách khỏi thân phương thức:

```text
Caller → [advice trước] → [checkout gốc] → [advice kết thúc]
               ↑               ↑
      pointcut chọn join point phù hợp
```

Với cách thứ hai, phần nghiệp vụ vẫn được tổ chức bằng hàm hoặc đối tượng như trước; AOP **không** tạo ra thuật toán thanh toán hay quyết định quy tắc hoàn tiền. Nó chỉ cung cấp một trục mô-đun hóa khác cho chính sách chung.

Advice có thể chạy **before**, **after returning**, **after throwing**, **after/finally** hoặc **around** theo công cụ. Đặc biệt, `around` có thể quyết định có tiếp tục phần xử lý bên trong hay không; quên tiếp tục hoặc tiếp tục nhiều lần có thể bỏ sót hay lặp side effect. Vì vậy việc áp dụng advice phải bảo toàn hợp đồng kết quả/ngoại lệ trừ khi chính sách thay đổi hợp đồng có chủ đích.

Cũng cần phân biệt thời điểm kết hợp: **compile-time weaving**, **load-time weaving**, biến đổi bytecode sau biên dịch và **runtime proxy/interception** là các cách thực hiện khác nhau. Proxy chỉ quan sát những đường gọi đi qua trung gian phù hợp; weaving mã có giới hạn khác. Không được suy luận “đã khai báo aspect” là mọi lời gọi, constructor hoặc truy cập trường đều bị can thiệp.

## 4. Quan hệ bổ sung giữa ba cách tiếp cận

| Tiêu chí | Thủ tục | Hướng đối tượng | Hướng khía cạnh |
| --- | --- | --- | --- |
| Đơn vị tổ chức nổi bật | Hàm/thủ tục và chuỗi bước | Đối tượng, lớp, giao diện, trách nhiệm | Aspect, pointcut, advice |
| Câu hỏi thiết kế chủ đạo | Xử lý theo các bước nào? | Trách nhiệm thuộc thành phần nào? | Hành vi chung cần áp dụng tại đâu? |
| Đường đi của xử lý | Chủ yếu thể hiện bằng lời gọi trực tiếp | Thể hiện bằng phương thức và cộng tác đối tượng | Có thể được bổ sung tại các điểm nối bởi weaving |
| Chia sẻ hành vi chung | Gọi hàm dùng chung | Composition, đa hình, decorator, helper | Khai báo chính sách và các điểm áp dụng |
| Thế mạnh | Thuật toán tuyến tính, luồng xử lý tường minh | Mô hình hóa miền, đóng gói, khả năng mở rộng theo trách nhiệm | Tách chính sách lặp qua nhiều thành phần |
| Rủi ro nếu lạm dụng | Thủ tục lớn; nhiều nơi phải gọi cùng helper | Quá nhiều lớp, kế thừa hoặc wrapper chỉ để tái sử dụng một mảnh hành vi | Luồng thực thi khó nhận ra, pointcut quá rộng, thứ tự advice phức tạp |

Bảng chỉ mô tả **trọng tâm**, không phải ranh giới tuyệt đối. Chẳng hạn, một ứng dụng thủ tục vẫn có thể chia module hợp lý; OOP có thể áp dụng interceptor; AOP vẫn cần các hàm, đối tượng và thuật toán hiện hữu để thực hiện nghiệp vụ.

Có thể nhìn cùng một xử lý theo ba lớp:

```text
Luồng bước nghiệp vụ:         kiểm tra đơn -> tính tiền -> thanh toán
Trách nhiệm hướng đối tượng: OrderService / PricingPolicy / PaymentGateway
Chính sách xuyên suốt:        phân quyền, audit, đo thời gian
```

Lớp thứ ba **không định nghĩa lại** cách tính tiền hay trách nhiệm của từng đối tượng. Nó quy định hành vi chung sẽ được thực hiện tại những điểm cụ thể trong luồng đã có.

### 4.1. Vì sao đóng gói OOP chưa giải quyết hết cross-cutting concerns?

Đóng gói gom **dữ liệu và hành vi thuộc cùng trách nhiệm** vào một đối tượng. Nhưng một chính sách như đo thời gian có thể áp dụng cho rất nhiều lớp độc lập. Nếu đặt đo thời gian trong từng lớp, ta làm mờ trách nhiệm chính của chúng; nếu gom tất cả vào một lớp cha, ta dễ ép các lớp độc lập vào quan hệ kế thừa không tự nhiên.

Đây là khác biệt giữa hai chiều phân rã:

- **Phân rã theo chức năng/trách nhiệm:** thanh toán, hoàn tiền, quản lý tồn kho.
- **Phân rã theo chính sách xuyên suốt:** kiểm toán, quan sát, quy tắc kiểm soát truy cập dùng chung.

AOP cung cấp một cơ chế mô-đun hóa theo chiều thứ hai, song không loại bỏ nhu cầu thiết kế theo chiều thứ nhất.

### 4.2. AOP có thể kết hợp với chương trình thủ tục không?

Về ý tưởng, **có**: nếu nền tảng cho phép can thiệp các điểm thực thi như lời gọi hàm, aspect có thể áp dụng lên các hàm/thủ tục. Tuy vậy, khả năng này không mặc nhiên tồn tại trong mọi ngôn ngữ hay trình biên dịch. Trong hệ sinh thái Java, AOP thường được diễn giải qua các điểm nối gắn với kiểu, đối tượng và phương thức, nên OOP và AOP hay xuất hiện cùng nhau.

Điểm cần nhớ là: **AOP độc lập về mục đích mô-đun hóa mối quan tâm xuyên suốt, nhưng phụ thuộc vào cơ chế hỗ trợ cụ thể để hiện thực trên chương trình thực tế.**

## 5. Một tình huống đầy đủ: thêm kiểm toán và đo thời gian

Giả sử hệ thống có `checkout()`, `refund()` và `cancelOrder()`. Yêu cầu mới:

- Ghi nhận tên thao tác, thời điểm bắt đầu và kết quả thành công/thất bại.
- Đo thời gian thực thi của cả ba thao tác.
- Không thay đổi quy tắc tính phí, hoàn tiền hay hủy đơn.
- Các thao tác mới cùng nhóm phải được áp dụng chính sách nhất quán.

**Cách thủ tục:** thêm lời gọi các hàm `auditStart`, `auditEnd`, `timerStart`, `timerStop` tại từng thủ tục. Cần dùng cấu trúc kiểm soát lỗi phù hợp để ghi nhận trường hợp thất bại; nếu quên một đường trả về hoặc ngoại lệ, số liệu có thể sai.

**Cách OOP thuần:** đặt nghiệp vụ trong các dịch vụ và sử dụng decorator, composition hoặc interceptor do chương trình lắp ghép tường minh. Cách này thường dễ theo dõi và kiểm thử khi số điểm áp dụng nhỏ, nhưng tăng công cấu hình/bọc khi chính sách trải qua nhiều đối tượng.

**Cách AOP:** khai báo tập các điểm thực thi hợp lệ và một advice bao quanh. Thay vì thêm lệnh đo ở từng phương thức, cơ chế weaving gắn hành vi đo vào các điểm đã chọn. Ví dụ ý tưởng:

```text
around selectedBusinessOperation(context):
    start = monotonicClock()
    status = "SUCCESS"
    try:
        return proceed(context)
    catch error:
        status = "FAILURE"
        throw error
    finally:
        duration = monotonicClock() - start
        recordMetric(context.operationName, status, duration)
```

Đây tiếp tục là **mã giả**. Đồng hồ tăng đơn điệu phù hợp hơn đồng hồ lịch để đo khoảng thời gian. Việc ném lại ngoại lệ giữ nguyên tín hiệu lỗi cho bên gọi; advice nên tránh tự ý nuốt lỗi hoặc thay đổi kết quả trừ khi đó chính là chính sách được thiết kế.

Xét ở cấp độ logic, kết quả mong muốn có dạng:

```text
Bên gọi
  -> advice bắt đầu đo
      -> checkout/refund/cancelOrder thực thi quy tắc nghiệp vụ gốc
  <- advice đo kết thúc, lưu trạng thái và thời gian
Bên gọi nhận kết quả/ngoại lệ theo hợp đồng nghiệp vụ
```

Lưu ý: nếu thao tác trả về một đối tượng biểu diễn công việc bất đồng bộ, thời điểm phương thức **trả về** có thể xảy ra trước thời điểm công việc **hoàn tất**. Muốn đo toàn bộ tác vụ cần hiểu vòng đời tác vụ và điểm nối mà cơ chế AOP thực sự hỗ trợ; đo lời gọi phương thức đơn thuần không bảo đảm đo toàn bộ quá trình bất đồng bộ.

## 6. Khi nào chọn thủ tục, OOP, AOP hoặc kết hợp?

- **Dùng thủ tục/hàm** khi trọng tâm là một thuật toán, pipeline dữ liệu hay tác vụ ngắn với luồng điều khiển trực tiếp. Tách helper nếu phần xử lý được tái sử dụng.
- **Dùng OOP** khi cần quản lý trách nhiệm, bất biến dữ liệu, trạng thái và tương tác giữa thành phần. Đây là lựa chọn tổ chức nghiệp vụ phổ biến trong Java.
- **Dùng composition/decorator/interceptor tường minh** khi một hành vi chung chỉ bao vài đối tượng, thứ tự gọi phải dễ nhận ra trong mã, hoặc cần chủ động kiểm soát từng đường đi.
- **Cân nhắc AOP** khi cùng một chính sách ổn định cần áp dụng nhất quán qua nhiều điểm thực thi, việc khai báo điểm áp dụng làm mã nghiệp vụ rõ hơn và công cụ có thể can thiệp chính xác các điểm cần thiết.

Ví dụ phù hợp với AOP gồm telemetry chung, audit tại ranh giới thao tác, đo hiệu năng, hoặc các quy tắc kỹ thuật dùng chung. Tuy nhiên, **không phải mọi logic lặp đều nên thành aspect**. Phép tính thuế, quyết định hạn mức tín dụng hay quy tắc chọn phương thức thanh toán là nghiệp vụ cốt lõi; chúng nên có nơi sở hữu rõ ràng trong thiết kế chức năng, ngay cả khi nhiều nơi sử dụng.

Một câu hỏi lựa chọn thực tế: *Nếu bỏ cơ chế can thiệp tự động, chính sách này có còn là bước nghiệp vụ mà người đọc bắt buộc phải nhìn thấy để hiểu tính đúng đắn của phương thức không?* Nếu có, việc đưa toàn bộ chính sách đó ra khỏi luồng nghiệp vụ có thể làm thiết kế khó hiểu hoặc nguy hiểm. Ví dụ, kiểm tra điều kiện hoàn tiền đặc thù cho đơn hàng thường thuộc miền nghiệp vụ; một chính sách ghi audit nhất quán cho mọi lần hoàn tiền có thể là ứng viên cross-cutting tốt hơn.

## 7. Những hệ quả thiết kế nâng cao

### 7.1. Thứ tự và tính kết hợp của nhiều aspect

Một điểm thực thi có thể khớp nhiều pointcut. Ví dụ, cùng một thao tác chịu kiểm tra quyền, đo thời gian và kiểm toán. Thứ tự bao quanh quyết định điều gì được đo và ghi:

```text
Audit(Authorization(Timing(businessOperation)))
```

Ở thứ tự này, audit có thể ghi cả lần bị từ chối quyền, trong khi timer chỉ đo thao tác vượt qua kiểm tra quyền. Nếu đổi thành `Authorization(Audit(Timing(...)))`, yêu cầu bị từ chối có thể không đi qua audit. **Đừng suy ra thứ tự mặc định** khi chưa xác minh cơ chế sắp xếp advice của công cụ; hãy đặt thứ tự rõ ràng và kiểm thử các đường thành công, lỗi và từ chối.

### 7.2. Pointcut quá rộng hoặc quá hẹp

Một pointcut chọn mọi phương thức theo mẫu tên quá chung có thể bắt cả thao tác không liên quan; một pointcut quá cụ thể có thể bỏ sót các thao tác mới. Điều này gây ra hai loại lỗi đối xứng: **áp dụng sai nơi** và **không áp dụng nơi cần thiết**.

Tốt nhất nên xác định rõ ranh giới ngữ nghĩa (nhóm thao tác, kiểu hoặc dấu đánh dấu có nghĩa), kiểm tra tập điểm thực thi khớp với pointcut, và có bài kiểm thử chứng minh một điểm **phải được can thiệp** cũng như một điểm **không được can thiệp**. Tên phương thức một mình có thể không đủ để diễn đạt chính sách bảo mật hoặc nghiệp vụ.

### 7.3. Tác động đến hợp đồng của phương thức

Advice có thể tạo thêm ngoại lệ, đổi kết quả, bỏ qua lời gọi gốc hoặc tăng chi phí thực thi. Với các thao tác có tác dụng phụ, gọi `proceed` nhiều lần có thể gây trừ tiền hai lần hoặc ghi dữ liệu hai lần. Khi xây dựng aspect, cần xác định rõ:

1. Advice có được phép ngăn thao tác gốc không?
2. Nó có được phép sửa tham số, giá trị trả về hoặc ngoại lệ không?
3. Nó phải chạy một lần hay có chủ ý thử lại?
4. Nếu bản thân logging/metrics/audit gặp lỗi, hành vi nghiệp vụ phải ra sao?

Những quyết định này thuộc **hợp đồng ứng dụng**. AOP chỉ cung cấp điểm và cơ chế để kết hợp hành vi; nó không tự bảo đảm an toàn nghiệp vụ.

### 7.4. Ranh giới kỹ thuật và khả năng quan sát

Một số cách hiện thực dùng đối tượng trung gian nên chỉ can thiệp được các cuộc gọi đi qua trung gian ấy; lời gọi nội bộ có thể đi đường khác. Các cơ chế biến đổi mã có thể có tập join point rộng hơn, nhưng đòi hỏi quy trình biên dịch/nạp phù hợp. Vì thế, cần kiểm chứng trên công cụ thực tế thay vì suy đoán rằng một pointcut đã bao phủ mọi trường hợp.

AOP cũng có thể khiến người đọc một phương thức không nhìn thấy ngay toàn bộ hành vi khi chạy. Nên có quy ước đặt tên, tài liệu hóa điểm áp dụng, log/trace rõ ràng, và kiểm thử tích hợp cho thứ tự advice, ngoại lệ, hiệu năng và sự tương tác giữa aspect.

### 7.5. Tránh nhầm giữa tách mã và tách trách nhiệm

Chuyển các dòng `checkPermission()` hay `audit()` ra ngoài phương thức khiến thân phương thức ngắn hơn, nhưng **không tự động cải thiện thiết kế**. Một aspect có thể trở thành nơi chứa quá nhiều chính sách không liên quan; pointcut có thể tạo phụ thuộc ngầm vào cấu trúc lớp hoặc tên phương thức; lạm dụng AOP có thể làm thay đổi hành vi khó truy tìm hơn so với lời gọi tường minh.

Quy tắc thực hành là: mỗi aspect nên diễn đạt một chính sách xuyên suốt có trách nhiệm rõ, điểm áp dụng có thể giải thích được và tác động lên kết quả/ngoại lệ được kiểm soát.

## 8. Những nhầm lẫn thường gặp

**“AOP là phiên bản mới thay thế OOP.”** Sai. OOP tổ chức trách nhiệm và trạng thái; AOP giải quyết thêm chiều mô-đun hóa các hành vi xuyên suốt. Nhiều hệ thống dùng đồng thời cả hai.

**“OOP không thể tái sử dụng logging hoặc bảo mật.”** Sai. OOP có composition, helper, decorator, interceptor và nhiều cách tổ chức khác. AOP thuận lợi khi muốn mô tả **chính sách và phạm vi áp dụng** tập trung.

**“Aspect giống hệt một hàm tiện ích.”** Không hẳn. Hàm tiện ích cần được gọi hoặc bọc tại những nơi sử dụng; aspect kết hợp **hành vi + quy tắc chọn điểm áp dụng + cơ chế weaving**.

**“Có pointcut thì mọi phương thức khớp tên đều được can thiệp.”** Không bảo đảm. Phải xét loại join point, thời điểm weaving, phạm vi mã và các giới hạn cụ thể của công cụ.

**“AOP chỉ thêm logging nên không thể thay đổi logic.”** Sai. Around advice có thể bỏ qua, lặp lại, sửa tham số hoặc tác động đến kết quả nếu công cụ cho phép. Vì vậy cần kỷ luật thiết kế.

**“Cứ có mã xuất hiện ở nhiều nơi thì đưa vào aspect.”** Sai. Nghiệp vụ cốt lõi nên ở nơi chịu trách nhiệm nghiệp vụ. Chỉ dùng aspect khi sự tách biệt giúp bảo toàn ngữ nghĩa và làm rõ thiết kế.

## 9. Tự kiểm tra hiểu bài

**Tình huống 1:** Năm dịch vụ độc lập đều cần ghi thời gian thực thi và tỷ lệ thất bại. Đây có phải ứng viên AOP không?

Có, nếu cùng một quy tắc đo có thể được áp dụng nhất quán ở các ranh giới mà công cụ hỗ trợ. Một decorator/interceptor tường minh cũng có thể đủ tốt; lựa chọn dựa trên độ bao phủ, tính minh bạch và chi phí vận hành.

**Tình huống 2:** Hai kiểu đơn hàng tính phí vận chuyển khác nhau. Có nên tạo aspect để quyết định mức phí?

Thông thường không. Đây là quy tắc nghiệp vụ cần được biểu đạt bằng chính sách tính phí, mô hình đối tượng hoặc hàm miền phù hợp. Chỉ phần quan sát/kiểm toán chung cho những phép tính ấy mới là ứng viên AOP rõ ràng.

**Tình huống 3:** Advice đo thời gian chỉ chạy sau khi phương thức trả về một tác vụ bất đồng bộ. Nó đã đo hết thời gian xử lý chưa?

Chưa chắc. Nó có thể chỉ đo thời gian tạo và trả về tác vụ. Muốn đo đến lúc hoàn tất cần theo dõi vòng đời bất đồng bộ và dùng điểm can thiệp phù hợp.

**Tình huống 4:** Một pointcut chọn đúng `checkout()`, nhưng kiểm tra quyền không xảy ra ở một đường gọi. Điều gì cần xác minh?

Kiểm tra đường gọi có đi qua cơ chế can thiệp hay không, loại join point được hỗ trợ, phạm vi weaving, điều kiện khớp pointcut và thứ tự các advice. Không nên kết luận chỉ từ tên phương thức.

## 10. Kết luận

- **Thủ tục** diễn đạt các bước xử lý và luồng điều khiển.
- **OOP** phân chia dữ liệu, hành vi và trách nhiệm giữa các đối tượng.
- **AOP** tập trung mô-đun hóa và áp dụng các mối quan tâm xuyên suốt tại những điểm thực thi được lựa chọn.

Một thiết kế tốt có thể kết hợp cả ba: thuật toán rõ ràng trong phương thức, trách nhiệm nghiệp vụ rõ ràng giữa đối tượng, và chính sách kỹ thuật xuyên suốt được áp dụng nhất quán. Giá trị của AOP nằm ở việc giảm phân tán chính sách **khi cơ chế áp dụng chính xác, kiểm chứng được và không che khuất quy tắc nghiệp vụ quan trọng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-007">So sánh AOP với hàm dùng chung, đối tượng cộng tác và thiết kế theo thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh AOP với hàm dùng chung, đối tượng cộng tác và thiết kế theo thành phần

## 1. Vấn đề thiết kế: một mối quan tâm xuất hiện ở nhiều nơi

Trong một hệ thống, mã nghiệp vụ trả lời câu hỏi **chương trình làm gì**: tạo đơn hàng, tính giá, ghi nhận thanh toán, cập nhật trạng thái. Bên cạnh đó còn các mối quan tâm như ghi nhật ký, đo thời gian, kiểm toán, kiểm soát truy cập hoặc chuẩn hóa cách xử lý lỗi. Khi cùng một quy tắc xuất hiện ở nhiều đường thực thi, ta gọi đó là **mối quan tâm xuyên cắt** (*cross-cutting concern*).

Giả sử có ba thao tác tạo đơn hàng, hủy đơn hàng và tra cứu đơn hàng. Cả ba cần ghi lại thời điểm bắt đầu/kết thúc, nhưng mỗi thao tác vẫn có quy tắc nghiệp vụ riêng. Có ít nhất bốn cách tổ chức:

1. Gọi **hàm dùng chung** tại các vị trí cần ghi nhận.
2. Cho đối tượng nghiệp vụ làm việc với **đối tượng cộng tác** có trách nhiệm ghi nhận.
3. Ghép các **thành phần** thực hiện nghiệp vụ và các lớp bao ngoài theo thứ tự rõ ràng.
4. Khai báo quy tắc **AOP** chọn những điểm thực thi thích hợp rồi gắn hành vi ghi nhận tại đó.

Bốn cách này đều có thể loại bỏ việc sao chép *thân hàm* ghi nhật ký. Khác biệt chính là **ai quyết định lúc nào hành vi được thực thi, sự phụ thuộc được biểu diễn ở đâu, và có thể kiểm tra luồng điều khiển bằng cách nào**.

Hai biểu hiện cần phân biệt:

- **Scattering (phân tán):** cùng một quy tắc xuất hiện rải rác ở nhiều lớp hoặc phương thức; sửa quy tắc phải chạm nhiều nơi.
- **Tangling (đan xen):** một phương thức trộn nhiều trách nhiệm, khiến phần nghiệp vụ khó đọc và khó kiểm thử.

Tách thành hàm dùng chung có thể giải quyết sự lặp của thuật toán ghi nhật ký nhưng vẫn để lại các lời gọi rải rác. AOP có thể tập trung cả quy tắc lẫn điểm áp dụng, nhưng đổi lại tạo thêm một tầng điều khiển cần được quản lý rõ ràng.

## 2. Hàm dùng chung: tái sử dụng thuật toán, lời gọi vẫn tường minh

**Cách hoạt động.** Đặt thao tác lặp lại vào một hàm tiện ích hoặc một hàm bậc cao nhận hành động cần chạy. Mã gọi tự quyết định khi nào gọi hàm.

~~~java
static <T> T trace(java.util.function.Supplier<T> action) {
    System.out.println("start");
    try {
        return action.get();
    } finally {
        System.out.println("end");
    }
}

// Ví dụ bên trong một phương thức khác:
String result = trace(() -> orderService.place("A"));
~~~

Đây là **đoạn minh họa**, chưa phải chương trình độc lập. Từ khóa **finally** khiến dòng kết thúc được in ngay cả khi hành động ném ngoại lệ; nó không tự chuyển ngoại lệ thành thành công.

**Ưu điểm:**

- Quan hệ nguyên nhân–kết quả hiện ngay tại vị trí gọi; IDE có thể lần theo hàm một cách trực tiếp.
- Dễ hiểu với Java thuần, không cần cơ chế dệt mã hay bộ chọn điểm thực thi.
- Rất phù hợp cho thao tác nhỏ, chỉ vài nơi dùng, hoặc trường hợp mỗi lời gọi cần lựa chọn tham số riêng.
- Hàm bậc cao có thể bảo toàn giá trị trả về và ngoại lệ, nếu phần bao ngoài được viết cẩn thận.

**Giới hạn:**

- Người lập trình có thể quên gọi hàm tại một đường đi mới.
- Nếu quy tắc phải bao quanh toàn bộ thao tác thì chỉ gọi một hàm ghi log ở đầu không đủ; cần tự tổ chức khối try/finally.
- Thay đổi phạm vi áp dụng thường buộc sửa nhiều điểm gọi.
- Mã nghiệp vụ vẫn nhìn thấy kiến thức về concern, dù thân hàm thực sự đã được tái sử dụng.

**Nên chọn khi:** phạm vi nhỏ, lời gọi có ý nghĩa nghiệp vụ tại đúng vị trí đó, hoặc việc thể hiện hành động rõ ràng trong mã quan trọng hơn khả năng áp dụng hàng loạt.

## 3. Đối tượng cộng tác: tách trách nhiệm và khai báo phụ thuộc

**Cách hoạt động.** Thay vì đưa hành vi dùng chung vào chính lớp nghiệp vụ, truyền vào một đối tượng có giao diện rõ ràng: **Audit**, **Metrics**, **Clock**, **AccessPolicy**... Đối tượng nghiệp vụ gọi cộng tác viên khi có sự kiện cần xử lý.

~~~java
interface Audit {
    void record(String event);
}

final class Checkout {
    private final Audit audit;

    Checkout(Audit audit) {
        this.audit = audit;
    }

    void complete(String orderId) {
        // Kiểm tra điều kiện và hoàn tất đơn hàng...
        audit.record("order-completed:" + orderId);
    }
}
~~~

Đây là **đoạn minh họa**. Một triển khai Audit có thể ghi ra màn hình, lưu vào kho kiểm toán hoặc làm đối tượng giả trong bài kiểm thử.

Đặc điểm quan trọng là lớp **Checkout** biểu đạt một **sự kiện nghiệp vụ có ý nghĩa**: đơn đã hoàn tất. Sự kiện này khác với yêu cầu kỹ thuật “ghi dấu mỗi khi bất kỳ phương thức nào được gọi”. Không nên tự động chuyển tất cả sự kiện nghiệp vụ sang AOP, vì lúc phát sinh sự kiện thường là một quyết định thuộc mô hình nghiệp vụ.

**Ưu điểm:**

- Có hợp đồng rõ ràng; có thể thay triển khai bằng đối tượng giả, đối tượng ghi nhớ sự kiện hoặc bộ gửi thông điệp.
- Dễ kiểm thử điều kiện phát sinh sự kiện, thứ tự lời gọi và dữ liệu được truyền.
- Quan hệ phụ thuộc hiện ra trong constructor; áp dụng nguyên tắc đảo ngược phụ thuộc.
- Xử lý được tình huống cần dữ liệu miền cụ thể, chẳng hạn lý do hủy đơn hoặc loại phê duyệt.

**Giới hạn:**

- Nơi cần concern vẫn phải khai báo cộng tác viên và gọi nó đúng lúc.
- Nếu mọi phương thức trong hàng trăm lớp đều có cùng lời gọi kỹ thuật, việc thêm cộng tác viên vào từng lớp có thể thành mã lặp.
- Giao diện quá rộng hoặc “một đối tượng làm mọi tiện ích” sẽ biến thành điểm phụ thuộc khó bảo trì.
- Đối tượng cộng tác chỉ tạo khả năng tái sử dụng trách nhiệm; nó không tự động chặn mọi phương thức phù hợp.

**Nên chọn khi:** concern cần được nghiệp vụ chủ động kích hoạt, cần truyền dữ liệu ngữ nghĩa của miền, hoặc hợp đồng cộng tác là một phần quan trọng trong thiết kế.

## 4. Thiết kế theo thành phần: lắp ghép hành vi bằng cấu trúc tường minh

**Cách hoạt động.** Mỗi thành phần thực hiện một trách nhiệm; hệ thống tạo luồng làm việc bằng cách lắp chúng lại. Ví dụ dùng mẫu **Decorator**: nhiều đối tượng cùng thực hiện giao diện **OrderService**, mỗi lớp bao ngoài ủy quyền cho thành phần bên trong.

~~~text
Caller
  → MetricOrderService
      → AuditedOrderService
          → CoreOrderService
~~~

Thành phần đo đạc có thể chạy trước/sau khi ủy quyền. Thành phần kiểm toán ghi sự kiện; thành phần lõi tạo đơn. Đây là **composition**: quan hệ lắp ghép có thể quan sát được từ mã khởi tạo hoặc cấu hình.

Không nên đồng nhất *thiết kế theo thành phần* với riêng Decorator. Thiết kế này còn bao gồm việc phân tách các module, sử dụng interface, thành phần dịch vụ, pipeline hoặc middleware, và lắp ghép hàm. Decorator chỉ là một ví dụ dễ thấy về cách bổ sung hành vi quanh một lời gọi.

**Ưu điểm:**

- Danh sách và thứ tự thành phần thường xác định rõ tại điểm lắp ghép.
- Có thể tạo nhiều biến thể pipeline mà không sửa lõi: có đo đạc, không đo đạc, có kiểm toán...
- Mỗi lớp bao ngoài là đơn vị kiểm thử độc lập; cấu hình thành phần có thể được kiểm tra.
- Không cần điểm cắt ẩn hoặc cơ chế weaving; phù hợp với các ranh giới dịch vụ, giao diện hoặc pipeline.

**Giới hạn:**

- Mọi đường gọi phải đi qua chuỗi thành phần đã lắp; truy cập trực tiếp đối tượng lõi sẽ bỏ qua các lớp bao ngoài.
- Cần thiết kế ranh giới, giao diện và nơi lắp ghép đủ ổn định.
- Tổ hợp nhiều wrapper có thể phức tạp; thứ tự wrapper làm thay đổi hành vi.
- Không tự động chạm đến mọi thao tác bên trong thành phần (ví dụ truy cập trường dữ liệu, thao tác nội bộ) chỉ vì nó tồn tại.

**Nên chọn khi:** luồng xử lý có các bước rõ ràng, hành vi thêm vào có tính thành phần, hoặc cần cấu hình linh hoạt mà vẫn giữ đường đi của lời gọi tương đối dễ nhìn.

## 5. AOP: tập trung quy tắc *và* điều kiện áp dụng

**Aspect-Oriented Programming (AOP)** là cách mô-đun hóa mối quan tâm xuyên cắt bằng việc mô tả **hành vi bổ sung** cùng với **tập điểm thực thi** nơi hành vi đó có hiệu lực. AOP thường được sử dụng bên cạnh thiết kế hướng đối tượng và composition, chứ không thay thế yêu cầu chia nhỏ đối tượng, xác định trách nhiệm hay xây dựng giao diện.

Các thuật ngữ nền tảng:

| Thuật ngữ | Ý nghĩa |
|---|---|
| **Join point** | Một điểm trong quá trình thực thi mà cơ chế AOP có thể nhận diện và tác động; tập điểm được hỗ trợ tùy công cụ, chẳng hạn thực thi phương thức, lời gọi phương thức hoặc truy cập trường. |
| **Pointcut** | Biểu thức/quy tắc chọn một tập join point theo tên, chữ ký, annotation, ngữ cảnh hoặc điều kiện được cơ chế hỗ trợ. |
| **Advice** | Mã chạy trước, sau, khi trả về thành công, khi có ngoại lệ, hoặc bao quanh join point. |
| **Aspect** | Đơn vị gói concern gồm pointcut và advice, có thể kèm trạng thái/cấu hình. |
| **Target** | Đối tượng hoặc phần mã mang hành vi gốc được chọn. |
| **Weaving (dệt)** | Quá trình gắn hoặc kết hợp hành vi aspect vào đường thực thi của chương trình. |

**Luồng khái niệm:**

~~~text
Lời gọi đến hành vi gốc
     ↓
Có khớp pointcut không?
     ├─ Không → Chạy hành vi gốc
     └─ Có  → Chạy advice thích hợp
                  ↓
              Chạy/bao quanh hành vi gốc
                  ↓
              Xử lý kết quả hoặc ngoại lệ
~~~

*Không phải mọi advice đều chạy theo một trình tự cố định.* Advice **before** chạy trước join point; **after-returning** liên quan tới hoàn tất bình thường; **after-throwing** liên quan tới hoàn tất bằng ngoại lệ; **after/finally** là hành vi kết thúc trong những mô hình hỗ trợ; **around** có quyền điều phối việc thực hiện hành vi gốc. Một advice **around** có thể quyết định không tiếp tục, tiếp tục một lần hoặc (nếu mô hình hỗ trợ) nhiều lần; thay đổi số lần gọi có thể thay đổi ngữ nghĩa nghiêm trọng.

Ví dụ **giả mã độc lập công cụ**, không phải cú pháp Java có thể biên dịch:

~~~text
aspect ExecutionTrace
    pointcut selectedOperations =
        execution(method in applicationService with @Observed)

    before selectedOperations:
        recordStart()

    afterFinally selectedOperations:
        recordEnd()
end
~~~

Ví dụ thể hiện sự khác biệt: lớp nghiệp vụ không cần trực tiếp gọi **recordStart** và **recordEnd**. Tập phương thức áp dụng được khai báo bằng pointcut. Cú pháp pointcut và khả năng chọn annotation/field/constructor phụ thuộc vào công cụ AOP cụ thể.

### 5.1. Weaving xảy ra ở đâu?

- **Compile-time weaving:** công cụ tích hợp hành vi aspect khi biên dịch nguồn thành bytecode hoặc mã thực thi.
- **Post-compile/binary weaving:** biến đổi mã đã biên dịch ở một bước sau biên dịch.
- **Load-time weaving:** chèn/biến đổi hành vi khi lớp được tải vào môi trường thực thi.
- **Runtime interception:** đối tượng đại diện, bộ chặn lời gọi hoặc cơ chế tương đương điều phối lời gọi khi chạy.

Đây là các họ kỹ thuật, không phải những khả năng chắc chắn luôn cùng có trong một công cụ. Đặc biệt, **proxy chỉ bắt được những lời gọi thực sự đi qua proxy**. Một hệ thống dệt mã ở cấp bytecode có thể quan sát những loại join point khác, tùy khả năng của bộ dệt. Bởi vậy không thể suy luận rằng “đã viết pointcut thì mọi lời gọi liên quan đều bị bắt” nếu chưa biết cơ chế áp dụng cụ thể.

### 5.2. Tính xuyên cắt khác sự tái sử dụng hàm ở điểm nào?

Giả sử chính sách thay đổi từ “ghi nhận hai phương thức” sang “ghi nhận mọi thao tác có đánh dấu @Observed”:

- **Hàm dùng chung:** phải bảo đảm mỗi lời gọi thích hợp đều bao quanh thao tác bằng hàm.
- **Đối tượng cộng tác:** phải thêm/kiểm tra việc phát sự kiện từ từng nơi.
- **Composition:** phải cấu hình mọi đường đi cần thiết qua component/wrapper tương ứng.
- **AOP:** có thể cập nhật pointcut hoặc gắn annotation tại biên thích hợp; việc áp dụng sẽ do cơ chế AOP thực hiện tại các join point thực sự được hỗ trợ.

AOP vì vậy đặc biệt có giá trị khi **một chính sách kỹ thuật thống nhất cắt ngang nhiều thành phần** và ranh giới áp dụng có thể diễn đạt ổn định. Nếu ranh giới mơ hồ hoặc pointcut phụ thuộc tên hàm rất dễ đổi, lợi ích có thể giảm mạnh.

## 6. Cùng một nghiệp vụ, bốn cách triển khai bằng Java 17

Ví dụ sau chạy được bằng Java 17 trở lên. Nó minh họa hàm dùng chung, đối tượng cộng tác, composition qua Decorator và cơ chế chặn lời gọi bằng **JDK dynamic proxy**. Proxy ở phần cuối là ví dụ cụ thể về *một cơ chế interception*, **không đồng nghĩa với toàn bộ khả năng AOP** (không có ngôn ngữ pointcut tổng quát và không tự dệt mọi join point).

Tạo file **CompareApproaches.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

public class CompareApproaches {
    interface OrderService {
        String place(String id);
    }

    static class CoreOrderService implements OrderService {
        public String place(String id) {
            return "created:" + id;
        }
    }

    static class TraceFunctions {
        static <T> T around(Supplier<T> action) {
            System.out.println("[trace] start");
            try {
                return action.get();
            } finally {
                System.out.println("[trace] end");
            }
        }
    }

    interface Audit {
        void record(String event);
    }

    static class ConsoleAudit implements Audit {
        public void record(String event) {
            System.out.println("[audit] " + event);
        }
    }

    static class AuditedOrderService implements OrderService {
        private final OrderService next;
        private final Audit audit;

        AuditedOrderService(OrderService next, Audit audit) {
            this.next = next;
            this.audit = audit;
        }

        public String place(String id) {
            audit.record("place:" + id);
            return next.place(id);
        }
    }

    static class MetricOrderService implements OrderService {
        private final OrderService next;

        MetricOrderService(OrderService next) {
            this.next = next;
        }

        public String place(String id) {
            System.out.println("[metric] begin");
            try {
                return next.place(id);
            } finally {
                System.out.println("[metric] end");
            }
        }
    }

    public static void main(String[] args) {
        OrderService core = new CoreOrderService();

        String a = TraceFunctions.around(() -> core.place("A"));
        System.out.println(a);

        OrderService collaborator =
                new AuditedOrderService(core, new ConsoleAudit());
        System.out.println(collaborator.place("B"));

        OrderService components = new MetricOrderService(
                new AuditedOrderService(core, new ConsoleAudit()));
        System.out.println(components.place("C"));

        OrderService observed = (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[]{OrderService.class},
                (proxy, method, actualArgs) -> {
                    System.out.println("[crosscut] before " + method.getName());
                    try {
                        return method.invoke(core, actualArgs);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    } finally {
                        System.out.println("[crosscut] after " + method.getName());
                    }
                });
        System.out.println(observed.place("D"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 CompareApproaches.java
java CompareApproaches
~~~

Kết quả chuẩn:

~~~text
[trace] start
[trace] end
created:A
[audit] place:B
created:B
[metric] begin
[audit] place:C
[metric] end
created:C
[crosscut] before place
[crosscut] after place
created:D
~~~

**Đọc kết quả theo từng đoạn:**

1. **A:** nơi gọi chọn hàm **TraceFunctions.around** và truyền hành động vào. Lõi không ghi log, nhưng điểm dùng hàm vẫn do caller quyết định.
2. **B:** **AuditedOrderService** có cộng tác viên **Audit** được truyền vào constructor; bản thân wrapper gọi cộng tác viên khi thao tác bắt đầu. Trong ứng dụng thực, cộng tác viên có thể được đặt trực tiếp ở lớp nghiệp vụ nếu đó là một sự kiện miền.
3. **C:** hai thành phần được ghép: **MetricOrderService → AuditedOrderService → CoreOrderService**. Thứ tự bao ngoài quyết định thứ tự log.
4. **D:** cuộc gọi đến **observed.place** đi qua JDK proxy, nên bộ chặn chạy trước/sau lời gọi đến **core.place**. Nếu gọi thẳng **core.place**, bộ chặn của proxy này không chạy. JDK dynamic proxy ở đây làm việc qua interface **OrderService**.

Ví dụ là thí nghiệm phân biệt **nơi đặt chính sách**, không phải bài đo hiệu năng. Những dòng ghi nhận đơn giản ở đây không đại diện cho cách triển khai kiểm toán, đo thời gian hoặc bảo mật thực tế.

## 7. Bảng đối chiếu các đặc tính thiết kế

| Tiêu chí | Hàm dùng chung | Đối tượng cộng tác | Composition / component | AOP |
|---|---|---|---|---|
| **Nơi khai báo hành vi** | Hàm, helper, wrapper function | Interface và đối tượng triển khai | Thành phần độc lập/wrapper | Aspect/advice |
| **Nơi xác định phạm vi áp dụng** | Từng lời gọi | Nơi đối tượng chủ động gọi cộng tác viên | Đường lắp ghép/cấu hình pipeline | Pointcut + các join point được hỗ trợ |
| **Sự hiện diện tại mã nghiệp vụ** | Lời gọi rõ ràng | Phụ thuộc và lời gọi rõ ràng | Có thể ẩn khỏi lõi, hiện tại lắp ghép | Thường không có lời gọi trực tiếp trong lõi |
| **Mức tự động áp dụng** | Thấp; cần gọi | Thấp đến trung bình; tùy cấu trúc | Theo tuyến đường đi qua component | Cao trong phạm vi pointcut/weaving |
| **Đổi chính sách chung** | Đổi thân hàm dễ, đổi tập nơi gọi khó hơn | Đổi implementation dễ, đổi nơi phát sự kiện có thể tốn công | Đổi/reorder wrapper ở nơi lắp ghép | Đổi advice/pointcut tập trung, nhưng phải kiểm tra ảnh hưởng |
| **Khả năng lần theo luồng** | Trực tiếp | Trực tiếp qua interface | Qua chuỗi component | Cần cả mã gốc, cấu hình và pointcut |
| **Kiểm thử đơn vị** | Hàm và caller | Cộng tác viên giả, tương tác | Từng component + chuỗi tích hợp | Aspect riêng + kiểm thử weaving/interception thực |
| **Nguy cơ quên áp dụng** | Quên gọi tại điểm mới | Quên phát sự kiện | Đường đi tắt bỏ wrapper | Pointcut không khớp, lớp không được weave, lời gọi bỏ qua proxy |
| **Điểm mạnh điển hình** | Tái sử dụng đơn giản | Hợp đồng miền và testability | Cấu trúc linh hoạt, rõ thứ tự | Chính sách xuyên nhiều điểm thực thi |
| **Rủi ro đặc trưng** | Lặp lời gọi; tangling | Phụ thuộc lan rộng | Chuỗi wrapper phức tạp | Phụ thuộc ngầm; pointcut quá rộng; thứ tự advice |

Các ô trong bảng là khuynh hướng thường gặp, không phải định luật. Chẳng hạn composition vẫn có thể khó lần theo khi cấu hình quá động; AOP vẫn có thể rất rõ nếu pointcut, phạm vi và hiệu ứng được đặt tên và kiểm thử tốt.

## 8. Tình huống nào nên chọn cách nào?

### Tình huống A — Chuẩn hóa một phép tính dùng ở nhiều nơi

Nhiều màn hình cùng cần chuẩn hóa mã đơn hàng hoặc tính quy tắc làm tròn. Chọn **hàm dùng chung** (hoặc đối tượng chuyên trách phép tính nếu có trạng thái/phụ thuộc). Đây là **thuật toán tái sử dụng**, chưa chắc là concern xuyên cắt ở cấp thực thi; đặt nó vào advice sẽ làm mờ nơi phép tính được gọi và có thể đổi ngữ nghĩa.

### Tình huống B — Hoàn tất giao dịch phải phát sự kiện nghiệp vụ

Khi đơn hàng chuyển từ **đang xử lý** sang **hoàn tất**, nghiệp vụ phải phát sự kiện chứa mã đơn, người duyệt, thời điểm và lý do. Chọn **đối tượng cộng tác**, hoặc thành phần phát sự kiện có giao diện rõ ràng. Sự kiện gắn với **chuyển trạng thái có ý nghĩa** chứ không chỉ với tên phương thức. Một advice “sau khi phương thức kết thúc” không đủ chứng minh trạng thái đã cam kết thành công; nếu phương thức trả về nhưng công việc khác vẫn có thể thất bại thì điểm gắn advice chưa đúng.

### Tình huống C — Dịch vụ cần pipeline kiểm tra, chuyển đổi, đo đạc

Có một cửa vào ổn định, các bước có thể sắp theo thứ tự và bật/tắt tùy cấu hình. Chọn **composition** với Decorator/middleware/pipeline. Kiểm thử sự khác nhau giữa các thứ tự là trực quan: đặt bước kiểm soát truy cập trước hoặc sau bước cache có thể tạo ra ý nghĩa khác nhau.

### Tình huống D — Theo dõi hàng trăm thao tác ở các module

Muốn đo khoảng thời gian thực thi của mọi thao tác được đánh dấu; chính sách ghi nhận không làm thay đổi quyết định nghiệp vụ. Nếu môi trường hỗ trợ join point phù hợp, **AOP** có thể giảm scattering. Điểm cắt nên dựa trên một hợp đồng ổn định, ví dụ annotation khai báo mục đích, hoặc một nhóm API được định nghĩa rõ. Cần kiểm thử rằng thao tác mới thực sự khớp và có advice chạy.

### Tình huống E — Kiểm soát quyền truy cập hoặc giao dịch có ảnh hưởng đúng/sai

Có thể dùng một cơ chế chặn tại biên làm lớp thực thi chính sách nhất quán. Tuy nhiên vẫn phải xác định chính xác **ranh giới tin cậy**, đường đi không được chặn, ngoại lệ, gọi bất đồng bộ và hành vi khi thất bại. Quy tắc cho phép thao tác cụ thể, tính hợp lệ dữ liệu và các bất biến miền thường cần hiện diện tại nơi sở hữu chúng. Một pointcut đặt tên theo lớp không tự tạo ra bảo đảm bảo mật.

## 9. Những khác biệt sâu hơn khi dự án phát triển

### 9.1. Mức liên kết: phụ thuộc tường minh và phụ thuộc ngầm

Với cộng tác viên, constructor cho thấy lớp phụ thuộc vào **Audit**. Với composition, điểm lắp ghép cho thấy lớp đo đạc bao lớp nghiệp vụ. Với AOP, phụ thuộc ngược lại có thể tồn tại ở **cấu hình thực thi**, dù mã lõi không gọi aspect.

Điều đó làm giảm coupling trong *mã nguồn của lõi*, nhưng không xóa sự phụ thuộc vào thứ tự, tên phương thức, annotation, loader hoặc cơ chế weaving. Hãy mô tả đó là **dịch chuyển nơi khai báo coupling**: từ lời gọi trực tiếp sang hợp đồng áp dụng aspect.

### 9.2. Mức bao phủ: chặn ở biên so với điểm thực thi nội bộ

Ví dụ một phương thức gọi phương thức khác trên chính đối tượng:

~~~java
void outer() {
    inner();
}

void inner() {
    // Công việc cần theo dõi
}
~~~

Nếu hệ thống chỉ chặn cuộc gọi **qua một proxy bên ngoài**, lời gọi nội bộ **inner()** có thể không đi qua proxy nên không kích hoạt bộ chặn của inner. Một cơ chế weaving khác có thể quan sát thực thi của inner, nhưng điều đó phải được xác minh theo loại join point mà nó hỗ trợ. Tương tự, phương thức private, static, final, constructor hay truy cập trường có thể được hỗ trợ khác nhau ở từng cơ chế.

**Bài học:** đừng thiết kế chính sách theo giả định rằng có AOP thì tất cả đường đi đều bị chặn. Xác định cụ thể **join point được hỗ trợ, đối tượng thực nhận cuộc gọi, phạm vi lớp được biến đổi và cơ chế áp dụng**.

### 9.3. Thứ tự chồng nhiều hành vi

Giả sử có hai concern: đo thời gian và retry. Nếu đo thời gian bao ngoài retry, kết quả có thể bao gồm toàn bộ các lần thử. Nếu retry bao ngoài đo thời gian, mỗi lần thử có thể được ghi riêng. Cùng một cặp hành vi nhưng thứ tự bao có thể cho ra dữ liệu khác nhau.

~~~text
Timer(Retry(Core))  → một khoảng thời gian của cả chuỗi thử lại
Retry(Timer(Core))  → một khoảng thời gian cho từng lần thử
~~~

Điều này đúng khi các wrapper/advice có hành vi tương đương với sơ đồ trên; kết quả thực tế phụ thuộc cách chúng được triển khai và điều phối. Đối với AOP, khi nhiều aspect chọn cùng join point, phải định nghĩa và kiểm tra precedence theo cơ chế đang dùng. Không nên dựa vào thứ tự ngẫu nhiên.

### 9.4. Kết quả, ngoại lệ và tính nguyên tử

Một đoạn **after** chạy không có nghĩa thao tác đã thành công. Phân biệt ít nhất ba hoàn cảnh:

- Hoàn tất bình thường và trả kết quả.
- Ném ngoại lệ.
- Đã trả về tại biên quan sát nhưng công việc bất đồng bộ vẫn tiếp tục.

Một thao tác đo đạc thường nên dùng **finally** để đóng span. Kiểm toán thành công có thể chỉ được ghi khi kết quả nghiệp vụ đã xác nhận. Advice bao quanh cũng cần truyền đúng ngoại lệ gốc và kết quả về caller, tránh nuốt lỗi hoặc vô tình đổi kiểu lỗi.

Nếu advice tự mở giao dịch, rollback, retry hoặc gửi thông điệp, điều đó đã tác động đến tính đúng đắn chứ không chỉ tính quan sát. Hãy định nghĩa rõ thời điểm cam kết, khả năng lặp lại, idempotency và điều kiện thất bại. Những yêu cầu này không tự được bảo đảm bởi cú pháp AOP.

### 9.5. Chi phí hiệu năng và khả năng chẩn đoán

Mỗi cách có chi phí khác nhau: gọi hàm trực tiếp, gọi qua interface, chuỗi wrapper, bộ chặn phản xạ hoặc mã đã được weave. Chi phí phụ thuộc cơ chế, tối ưu JVM, số lớp advice, lượng I/O của logging, tần suất gọi và cấu hình thực tế. Không có cơ sở kết luận mặc định rằng “AOP luôn chậm” hoặc “AOP không có chi phí”.

Mốc thực tế: một lời ghi log ra mạng/ổ đĩa thường mang ý nghĩa hiệu năng khác nhiều so với một lần gọi hàm; chỉ phép đo với workload phù hợp mới trả lời được. Khi chẩn đoán, phải nhớ rằng stack trace và trình gỡ lỗi có thể hiển thị proxy, bộ chặn hoặc mã được weave, khiến đường đi khác với những gì nhìn thấy trong lớp nghiệp vụ.

### 9.6. Tính minh bạch dữ liệu và ranh giới an toàn

Aspect quan sát tham số và giá trị trả về có thể vô tình ghi mật khẩu, token, thông tin nhận dạng hoặc dữ liệu nhạy cảm. Chọn loại dữ liệu được phép thu thập và cách che/mã hóa theo chính sách thực tế. Nếu advice có trạng thái chia sẻ, phải xét concurrency, reentrancy, lưu context theo luồng và việc chuyển context qua tác vụ bất đồng bộ.

Không nên giả định dữ liệu gắn với một luồng sẽ theo sang một luồng khác. Với xử lý bất đồng bộ, bước quan sát quanh thao tác tạo tác vụ có thể kết thúc trước khi công việc thực sự hoàn thành; điểm theo dõi phải phù hợp với vòng đời của tác vụ.

## 10. Cách kết hợp các phương pháp mà không chồng trách nhiệm

Các phương pháp không loại trừ lẫn nhau. Một kiến trúc có thể phân vai:

~~~text
Application boundary
   → Aspect quan sát thời gian/tracing tại những join point được hỗ trợ
   → Component điều phối yêu cầu
       → Domain service
           → Collaborator Audit: ghi sự kiện miền có ý nghĩa
           → Shared functions: chuẩn hóa dữ liệu và tính toán thuần
       → Decorator/pipeline: cache hoặc biến đổi có cấu hình
~~~

Ví dụ trách nhiệm:

- **Hàm thuần:** kiểm tra định dạng, tính thuế, chuyển đổi dữ liệu nhất quán.
- **Cộng tác viên:** gửi thông báo “đơn được duyệt”, tra cứu chính sách được mô hình miền chủ động yêu cầu.
- **Thành phần:** ghép đường xử lý thành các bước có thứ tự, cache, adapter hoặc middleware.
- **Aspect:** đo thời gian ở những biên ổn định, thống kê lời gọi, log kỹ thuật có chính sách chung.

Nếu vừa có wrapper đo thời gian vừa có aspect đo cùng một sự kiện, rất dễ **đếm kép**. Mỗi concern nên có nơi sở hữu, danh sách biên áp dụng và tên metric/log thống nhất. Khi phối hợp, kiểm thử end-to-end cần xác nhận chính xác **một lần** ghi nhận trên mỗi biến cố mong muốn.

## 11. Thiết kế pointcut có khả năng bảo trì

Một pointcut rộng kiểu “tất cả phương thức trong toàn bộ ứng dụng” có thể vô tình áp dụng cho đường đi nhạy cảm về hiệu năng hoặc cho API không nên bị quan sát. Ngược lại, pointcut dựa trên mẫu tên dễ bỏ sót các lớp được đổi tên.

Nên làm rõ:

1. **Ý định:** đang chọn theo ranh giới kiến trúc, metadata đánh dấu hay chữ ký kỹ thuật?
2. **Phạm vi:** chọn những loại join point nào; có ngoại lệ cần loại trừ không?
3. **Tính ổn định:** khi đổi tên/refactor, tập join point phải thay đổi theo yêu cầu nào?
4. **Quan hệ giữa aspects:** precedence, các advice chồng nhau và hành vi khi có lỗi.
5. **Kiểm chứng:** có bài kiểm thử chứng minh join point mong muốn được áp dụng, join point ngoài phạm vi không bị áp dụng không?

Một annotation đánh dấu chủ ý có thể rõ hơn quy tắc tên trong nhiều trường hợp, nhưng bản thân annotation không mang tính “ma thuật”: phải có cơ chế AOP đọc nó và áp dụng advice, đồng thời không để cùng một annotation gánh nhiều chính sách không liên quan.

## 12. Kiểm thử: phải xác minh cả chức năng gốc lẫn cơ chế áp dụng

| Loại kiểm thử | Điều cần chứng minh |
|---|---|
| **Hàm dùng chung** | Giá trị trả về, ngoại lệ, tác dụng phụ và số lần gọi hành động. |
| **Cộng tác viên** | Khi nào được gọi/không gọi, tham số đúng, phản ứng với lỗi cộng tác. |
| **Composition** | Các lớp nhận đúng ủy quyền; thứ tự của pipeline; đường đi ngoài pipeline có được chặn như yêu cầu không. |
| **AOP unit-level** | Logic advice và bộ chọn theo biểu thức được mô hình hỗ trợ; hành vi trên kết quả/ngoại lệ. |
| **AOP integration-level** | Weaving/interception thực xảy ra với lớp và lời gọi đã định; không áp dụng nhầm; cùng nhiều aspect có thứ tự đúng. |

Với AOP, gọi thẳng phương thức xử lý trong aspect rồi thấy nó đúng **chưa đủ**. Bài kiểm thử tích hợp phải đi qua đường thực thi thực tế mà chương trình sử dụng, để phát hiện lỗi không được weave, không đi qua proxy, pointcut không khớp, hoặc advice chạy hai lần. Ngược lại, test nghiệp vụ lõi nên xác nhận nghiệp vụ đúng ngay cả khi hệ thống quan sát được thay bằng giả lập phù hợp.

Cần thêm các ca đối chứng: lời gọi thành công, ném ngoại lệ, lời gọi lồng nhau, hai aspect cùng khớp, đổi tên phương thức, xử lý bất đồng bộ và đường đi trực tiếp bỏ qua interceptor (nếu cơ chế có giới hạn đó).

## 13. Những ngộ nhận và lỗi thiết kế thường gặp

**“AOP chỉ là một cách viết hàm tiện ích.”** Sai ở cấp mô hình: hàm tiện ích cần nơi gọi tường minh; AOP có pointcut và weaving/interception để áp dụng hành vi ở những join point phù hợp.

**“Tách concern sang cộng tác viên là đã giải quyết hoàn toàn xuyên cắt.”** Mới tách phần thực hiện hành vi; lời gọi và quy tắc chọn nơi gọi có thể vẫn rải rác.

**“Dùng AOP thì không cần kiến trúc thành phần.”** AOP không quyết định ranh giới miền, trách nhiệm đối tượng, hợp đồng API hoặc cấu trúc module. Hệ thống có aspect vẫn cần thiết kế cơ sở tốt.

**“Aspect đảm bảo không thể bị bỏ qua.”** Sai nếu điểm cắt không khớp, vùng weaving thiếu, lời gọi đi tắt qua đối tượng gốc hoặc loại join point không được hỗ trợ.

**“Logging sau khi hàm chạy nghĩa là giao dịch đã hoàn tất.”** Không luôn đúng; kết thúc phương thức, hoàn tất bất đồng bộ và cam kết nghiệp vụ có thể ở các thời điểm khác nhau.

**“Advice là chỗ phù hợp để đặt mọi quy tắc nghiệp vụ.”** Nếu chính sách cần hiểu bất biến miền, điều kiện chuyển trạng thái hoặc dữ liệu sự kiện nghiệp vụ, việc giấu nó trong pointcut có thể làm hệ thống khó giải thích và khó kiểm chứng.

**“Cứ càng nhiều aspect càng ít mã.”** Các aspect chồng nhau tạo thêm chi phí đọc, debug, xác định thứ tự, hiệu năng và bảo trì. Mục tiêu là phân định trách nhiệm rõ ràng, không tối đa hóa số lượng aspect.

## 14. Câu hỏi quyết định trước khi chọn giải pháp

Hãy trả lời lần lượt:

1. **Đây là thuật toán dùng lại, sự kiện nghiệp vụ, một bước trong pipeline hay một chính sách xuyên cắt?** Câu trả lời xác định nơi khai báo tự nhiên của hành vi.
2. **Hành vi cần xuất hiện ở từng nơi gọi cụ thể hay ở mọi join point thuộc một tập đã biết?** Từng nơi gọi phù hợp với hàm/cộng tác viên; tập rộng, ổn định là ứng viên cho AOP.
3. **Ai sở hữu thời điểm áp dụng?** Nghiệp vụ sở hữu chuyển trạng thái; composition sở hữu pipeline; pointcut sở hữu phạm vi quan sát kỹ thuật.
4. **Có thể chứng minh bao phủ và thứ tự không?** Nếu không thể xác minh đường gọi, weaving và precedence, việc ẩn hành vi sau aspect có thể gây rủi ro.
5. **Thất bại của concern có làm thất bại nghiệp vụ không?** Nếu có, phải xác định hợp đồng ngoại lệ, retry, tính nguyên tử và trạng thái.
6. **Người bảo trì sẽ tìm nguyên nhân ở đâu?** Đảm bảo tài liệu, công cụ debug và test thể hiện được điểm áp dụng thực tế.

**Quy tắc thực hành:** bắt đầu từ ranh giới trách nhiệm rõ ràng. Dùng hàm cho phép tính tái sử dụng, cộng tác viên cho hành vi được nghiệp vụ chủ động gọi, composition cho các bước lắp ghép, và AOP cho chính sách xuyên cắt có tập điểm áp dụng rõ ràng, cơ chế hỗ trợ xác định và phép kiểm thử bao phủ. Khi phối hợp nhiều cách, ghi rõ nơi sở hữu mỗi concern.

## 15. Tự kiểm tra khả năng vận dụng

**Bài 1.** Năm thao tác đều gọi chung một thuật toán chuẩn hóa mã. Có nên tạo pointcut chỉ để chạy thuật toán này không? Giải thích nơi thuật toán được gọi và việc ai lựa chọn thời điểm áp dụng.

**Bài 2.** Dịch vụ vừa được bọc trong Decorator đo thời gian vừa nằm trong pointcut đo thời gian. Sự cố nào có thể xảy ra với metric, và sửa bằng cách xác định quyền sở hữu concern ra sao?

**Bài 3.** Một advice ghi thành công khi phương thức trả về, nhưng phương thức đó chỉ tạo tác vụ chạy nền. “Trả về thành công” đã đủ chứng minh tác vụ thành công chưa? Cần đổi điểm quan sát sang đâu?

**Bài 4.** Trong ví dụ Java 17, thay **observed.place("D")** bằng **core.place("D")** thì hai dòng crosscut có xuất hiện không? Giải thích dựa trên đường đi qua proxy.

**Gợi ý đáp án:** (1) thường dùng hàm/cộng tác viên vì đó là phép tính có lời gọi mang ý nghĩa rõ ràng; (2) metric có thể đếm hai lần, cần một biên đo thống nhất; (3) chưa, cần quan sát thời điểm hoàn tất thật của tác vụ; (4) không, vì cuộc gọi đã bỏ qua đối tượng đại diện.

---

**Kết luận về nguyên lý:** AOP khác biệt bởi khả năng mô tả một chính sách và **tập join point** cần áp dụng nó; hàm dùng chung tập trung thuật toán, đối tượng cộng tác tập trung trách nhiệm, còn composition tập trung cấu trúc lắp ghép. Giá trị của AOP nằm ở việc giảm những lời gọi kỹ thuật rải rác tại các biên được định nghĩa rõ. Sự đánh đổi là phải chủ động quản lý sự phụ thuộc ngầm, phạm vi pointcut, thứ tự advice và việc kiểm chứng interception/weaving.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-008">Lợi ích, đánh đổi và những bài toán không nên dùng AOP</a>

<details>
<summary>Xem chi tiết</summary>

# Lợi ích, đánh đổi và những bài toán không nên dùng AOP

## Mục tiêu bài học

Sau bài này, bạn có thể:

- Nhận ra một *cross-cutting concern* (mối quan tâm xuyên suốt) và giải thích vì sao AOP có thể giúp xử lý nó.
- Cân nhắc lợi ích về tổ chức mã nguồn, tính nhất quán và khả năng thay đổi chính sách so với chi phí về luồng chạy, kiểm thử, hiệu năng và độ tin cậy.
- Phân biệt một chính sách kỹ thuật phù hợp với AOP và một quy tắc nghiệp vụ cần xuất hiện tường minh trong mã.
- Lựa chọn có cơ sở giữa AOP, lời gọi trực tiếp, decorator, interceptor và các cơ chế có biên rõ ràng khác.

## 1. Đặt AOP đúng vị trí

**Aspect-Oriented Programming (AOP)** giúp tập trung chính sách dùng chung xuất hiện ở nhiều điểm của chương trình, nhằm giảm mã phân tán (*scattering*) và trách nhiệm đan xen với nghiệp vụ (*tangling*). Chẳng hạn, bộ đo thời gian có thể chọn một nhóm thao tác rồi ghi độ trễ khi chúng kết thúc, thay vì mỗi phương thức tự đặt đồng hồ.

Ở mức tối thiểu, **join point** là sự kiện được cơ chế hỗ trợ, **pointcut** là điều kiện chọn sự kiện, **advice** là hành vi áp dụng, **aspect** là đơn vị chứa chính sách, và **weaving/interception** là cách kết hợp hành vi vào đường thực thi. AOP là mô hình chứ không phải một công nghệ duy nhất: proxy và dệt bytecode có các giới hạn khác nhau.

**Điều kiện để lợi ích có thật:** chính sách phải có cùng ý nghĩa tại những điểm đã chọn, pointcut phải đủ chính xác, và toàn bộ đường gọi, kết quả, lỗi, side effect phải được kiểm chứng. Chỉ giảm số dòng mã không bảo đảm thiết kế tốt hơn.

## 2. AOP có thể mang lại lợi ích gì?

### 2.1. Giảm mã lặp cho chính sách xuyên suốt

Giả sử nhiều nghiệp vụ cần cùng bộ quy tắc ghi nhận thông tin: thời điểm, loại thao tác, mã tương quan và kết quả thành công/thất bại. Sao chép đoạn ghi nhận vào từng phương thức dễ khiến phiên bản không đồng nhất, khó sửa và khó phát hiện chỗ bị quên. Đặt chính sách trong một aspect thích hợp giúp sửa một nơi và áp dụng có hệ thống.

Nhưng **ít dòng mã hơn không tự động đồng nghĩa với thiết kế tốt hơn**. Nếu hai thao tác có yêu cầu kiểm toán khác nhau về dữ liệu nhạy cảm, điều kiện thành công hoặc mức độ bắt buộc, gộp chúng vào cùng một aspect có thể che mất sự khác biệt.

### 2.2. Tách chính sách kỹ thuật khỏi câu chuyện nghiệp vụ

Một phương thức xử lý đơn hàng nên dễ đọc như các bước xử lý đơn hàng. Việc đo độ trễ hay gắn mã theo dõi thường là chính sách quan sát, không phải bước quyết định đơn có hợp lệ hay không. Tách hành vi này giúp mã nghiệp vụ dễ đọc hơn và giảm điểm cần sửa khi thay đổi công cụ quan sát.

Cần giữ ranh giới: **quyết định giảm giá, tính phí, xác định quyền sở hữu dữ liệu hay chuyển trạng thái đơn hàng là hành vi nghiệp vụ**, kể cả khi quy tắc xuất hiện ở nhiều nơi. Việc “dùng lại được” không đủ để biến nó thành aspect.

### 2.3. Tăng tính nhất quán tại biên có hợp đồng rõ

Các điểm vào/ra được xác định ổn định là nơi chính sách kỹ thuật có khả năng áp dụng đồng đều: chuẩn hóa thông tin log, đếm số lần gọi, gắn dấu vết, hoặc kiểm tra quy định đầu vào thống nhất. AOP hữu ích nhất khi:

1. Các điểm áp dụng được định nghĩa bằng tiêu chí ổn định, có thể kiểm tra.
2. Hành vi bổ sung có cùng ý nghĩa tại mọi điểm được chọn.
3. Có thể mô tả hợp đồng trước/sau mà không cần biết toàn bộ bên trong nghiệp vụ.
4. Nhóm phát triển có khả năng quan sát và kiểm thử mã được kết hợp sau weaving.

### 2.4. Thay đổi hoặc tắt chính sách tập trung

Khi cần đổi định dạng log hay cách ghi đo lường, thay đổi ở một aspect có thể giảm số vị trí sửa thủ công. Việc bật/tắt có kiểm soát cũng hữu ích cho đo đạc. Tuy nhiên, tính tập trung kéo theo **phạm vi tác động rộng**: một lỗi trong aspect có thể ảnh hưởng hàng trăm lời gọi cùng lúc. Cần có chủ sở hữu, kiểm thử tích hợp và kế hoạch khôi phục tương xứng.

## 3. Các đánh đổi thường bị đánh giá thấp

### 3.1. Luồng điều khiển không còn hiển thị đầy đủ tại nơi gọi

Nhìn vào `service.execute()` không cho biết trước và sau nó còn chạy advice nào, thứ tự gì, có bị thay tham số/kết quả hoặc chặn ngoại lệ hay không. Người bảo trì phải hiểu cả phương thức, pointcut, thứ tự aspect và cơ chế weaving.

Hệ quả thực tế:

- Stack trace có thêm lớp proxy hoặc frame do công cụ sinh ra; gỡ lỗi có thể khó hơn.
- Một thay đổi tên lớp, package, annotation hoặc chữ ký phương thức có thể làm pointcut đổi phạm vi mà mã nghiệp vụ vẫn biên dịch.
- Khi nhiều aspect cùng bao quanh một điểm, thứ tự có thể thay đổi kết quả. Hai aspect độc lập trên giấy chưa chắc giao hoán trong thực thi.

Ví dụ, nếu aspect A “ghi nhận thành công” chạy **trước** khi aspect B hoàn tất lưu trạng thái, log thành công có thể xuất hiện ngay cả khi B thất bại. Phải xác định rõ “thành công” nghĩa là advice hoàn thành, phương thức trả về hay giao dịch đã được xác nhận.

### 3.2. Hành vi lỗi và hợp đồng phương thức có thể thay đổi

Advice dạng *around* có quyền gọi tiếp, gọi nhiều lần hoặc không gọi phần nghiệp vụ tùy cơ chế. Nếu viết sai, nó có thể:

- Bỏ qua một thao tác bắt buộc hoặc thực hiện lại tác vụ có tác dụng phụ.
- Nuốt ngoại lệ, biến thất bại thành giá trị mặc định hoặc đổi kiểu ngoại lệ ngoài ý muốn.
- Làm thay đổi cách xử lý `finally`, thời điểm giải phóng tài nguyên, thứ tự commit hoặc phản hồi tới bên gọi.
- Làm rò rỉ dữ liệu nếu ghi nguyên tham số, kết quả, token hoặc thông tin định danh vào log.

Cần coi advice bao quanh như mã điều khiển quan trọng, có hợp đồng và kiểm thử riêng. Đừng mặc định mọi hành vi có thể thêm “trong suốt”.

### 3.3. Chi phí hiệu năng và độ phức tạp vận hành

Can thiệp vào lời gọi có thể thêm chi phí chọn điểm, tạo proxy, đi qua nhiều advice, truy xuất ngữ cảnh, phân bổ bộ nhớ và ghi log/I/O. Mức chi phí thực tế tùy cơ chế, cấu hình, mức tối ưu của JVM và đường chạy. Không nên khẳng định AOP luôn chậm đáng kể hoặc luôn miễn phí.

Ở đường nóng (*hot path*) chạy hàng triệu lần, thêm một thao tác tưởng nhỏ vào mọi lần gọi vẫn có thể trở thành nút thắt. Ngược lại, với một tác vụ cần truy vấn mạng, chi phí lời gọi trung gian có thể không đáng kể. **Đo độ trễ, throughput và cấp phát trên workload thật** trước khi kết luận.

### 3.4. Khả năng áp dụng phụ thuộc cơ chế

Trong mô hình proxy, lời gọi đi thẳng vào đối tượng đích có thể bỏ qua advice; lời gọi nội bộ `this.otherMethod()` có thể không đi qua proxy. Proxy dựa trên interface có giới hạn khác với proxy dựa trên lớp. Trong mô hình weaving vào bytecode, tập join point và giới hạn lại khác. Phương thức `final`, `private`, constructor, lambda, lời gọi bất đồng bộ hay lớp do công cụ sinh ra đều cần được đánh giá theo công nghệ cụ thể.

Điểm nguy hiểm là **tưởng chính sách đã bao phủ toàn hệ thống nhưng thực tế có đường gọi không được chặn**. Đây là rủi ro lớn nếu dùng aspect như lớp bảo vệ duy nhất cho dữ liệu hoặc hành động nhạy cảm.

### 3.5. Khó kiểm thử, khó suy luận về concurrency và thứ tự

Unit test gọi trực tiếp một đối tượng có thể không kích hoạt aspect; chỉ integration test qua cơ chế wiring/weaving thực mới cho thấy hành vi đầy đủ. Các tác vụ bất đồng bộ còn có vấn đề truyền context giữa thread, callback hoặc điểm tiếp tục xử lý. Dựa vào `ThreadLocal` mà không có chiến lược truyền/đóng context có thể làm mất mã tương quan hoặc để sót trạng thái trên thread tái sử dụng.

Ngoài kiểm thử “aspect có chạy”, cần kiểm thử cả **nơi aspect không được chạy**, đường lỗi, thứ tự nhiều aspect và trường hợp thực thi đồng thời.

### 3.6. Thay đổi diện rộng tạo rủi ro bảo trì

Một pointcut quá rộng như “mọi phương thức public” có thể bao gồm cả mã mới thêm về sau. Thay đổi một advice ảnh hưởng nhiều đội hoặc module. Khi tiêu chí chọn dựa vào tên phương thức, việc refactor không kèm kiểm thử phạm vi có thể mở rộng/thu hẹp chính sách âm thầm.

Hướng kiểm soát tốt hơn là phạm vi nhỏ, tiêu chí rõ, contract được công bố, test xác nhận tập điểm được chọn và review tác động trước khi đổi advice.

## 4. Những bài toán **không nên dùng AOP**

| Bài toán | Vì sao AOP thường không phù hợp | Hướng phù hợp hơn |
| --- | --- | --- |
| Tính giá, chiết khấu, hạn mức, điều kiện chuyển trạng thái | Đây là quyết định nghiệp vụ cần thấy và giải thích trực tiếp trong luồng chính; mỗi tình huống có thể khác nhau. | Domain service, policy/strategy, state machine hoặc lời gọi tường minh. |
| Điều phối quy trình gồm nhiều bước có thứ tự, rollback/bù trừ riêng | Hành vi quanh một join point không thể thay cho mô hình trạng thái và điều phối toàn quy trình. | Workflow/orchestrator, saga hoặc logic điều phối tường minh. |
| Chỉ có một hoặc hai nơi cùng cần thêm xử lý | Độ phức tạp pointcut/weaving có thể lớn hơn lượng trùng lặp loại bỏ được. | Lời gọi hàm rõ ràng, helper hoặc decorator nhỏ. |
| Mỗi lời gọi cần một ngoại lệ khác nhau | Một aspect dùng chung dễ chứa nhiều nhánh đặc biệt, làm chính sách khó hiểu và khó kiểm soát. | Interface rõ trách nhiệm, strategy hoặc cấu hình tường minh ngay tại nơi sử dụng. |
| Kiểm tra quyền phụ thuộc dữ liệu và chủ thể của từng thao tác | Gắn một pointcut theo tên phương thức không đảm bảo mọi đường truy cập đều được bảo vệ; quy tắc sở hữu là bất biến nghiệp vụ. | Kiểm tra tại biên tin cậy và trong logic quyết định quyền; AOP chỉ có thể là lớp bổ sung đã được chứng minh phạm vi. |
| Ghi kiểm toán bắt buộc phải thành công trước khi trả kết quả | Một advice kiểu “cố gắng ghi rồi bỏ qua lỗi” có thể làm mất bằng chứng; cần quy định rõ tính bền vững, thứ tự và xử lý lỗi. | Thiết kế giao dịch, outbox hoặc cơ chế ghi kiểm toán có hợp đồng đảm bảo cụ thể. |
| Retry mọi ngoại lệ một cách tự động | Retry có thể thực hiện nhiều lần một tác dụng phụ như gửi tiền hay phát sự kiện, gây trùng lặp và bão tải. | Retry tường minh tại biên I/O, có phân loại lỗi, idempotency key, backoff, giới hạn lần thử. |
| Cache mọi lời gọi chỉ vì muốn tăng tốc | Phương thức có side effect, phụ thuộc ngữ cảnh hoặc dữ liệu thay đổi không có semantics cache an toàn. | Thiết kế cache tường minh với khóa, TTL, bất biến và chiến lược vô hiệu hóa. |
| Xử lý vòng lặp rất nóng hoặc đường cực kỳ nhạy độ trễ | Can thiệp mọi lần gọi có thể làm tăng chi phí hoặc tạo I/O không kiểm soát. | Đo lường bằng profiling/sampling, xử lý theo lô hoặc instrumentation được đánh giá riêng. |
| Che giấu hành vi có ảnh hưởng hợp đồng API | Người gọi không biết được dữ liệu bị sửa, lỗi bị nuốt hoặc thao tác có thể bị thực hiện lại. | API tường minh, middleware có hợp đồng rõ hoặc hàm bao có tên cụ thể. |

**Dấu hiệu cảnh báo mạnh:** Nếu muốn hiểu tại sao kết quả nghiệp vụ là X mà bắt buộc phải mở một aspect “ẩn” mới thấy quy tắc quyết định X, có khả năng quy tắc đó đang nằm sai chỗ.

## 5. Ví dụ Java 17: tiện lợi của can thiệp tập trung và giới hạn của proxy

Ví dụ dưới dùng **JDK dynamic proxy** để minh họa một phần ý tưởng AOP: chặn lời gọi qua interface và áp dụng hành vi audit trước/sau tại một điểm chung. Đây là ví dụ chạy độc lập bằng Java chuẩn, **không phải bộ AOP hoàn chỉnh**: không có ngôn ngữ pointcut hay weaving bytecode. Nó hữu ích để thấy ranh giới giữa chính sách xuyên suốt và logic nghiệp vụ.

Tạo tệp `AopTradeoffDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class AopTradeoffDemo {
    interface OrderService {
        String place(String orderId);
    }

    static class DefaultOrderService implements OrderService {
        @Override
        public String place(String orderId) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("orderId required");
            }
            System.out.println("BUSINESS place " + orderId);
            return "OK " + orderId;
        }
    }

    public static void main(String[] args) {
        OrderService target = new DefaultOrderService();
        OrderService audited = (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[] { OrderService.class },
                (proxy, method, methodArgs) -> {
                    System.out.println("AUDIT before " + method.getName());
                    try {
                        Object result = method.invoke(target, methodArgs);
                        System.out.println("AUDIT success " + method.getName());
                        return result;
                    } catch (InvocationTargetException ex) {
                        System.out.println("AUDIT failure " + method.getName());
                        throw ex.getCause();
                    }
                }
        );

        System.out.println(audited.place("ORD-7"));
        try {
            audited.place("");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac AopTradeoffDemo.java
java AopTradeoffDemo
```

Kết quả chuẩn:

```text
AUDIT before place
BUSINESS place ORD-7
AUDIT success place
OK ORD-7
AUDIT before place
AUDIT failure place
CALLER caught orderId required
```

Phân tích:

- `DefaultOrderService` giữ quy tắc nghiệp vụ: mã đơn không được trống. Quy tắc này **không chuyển vào advice** chỉ vì mọi đơn đều cần nó.
- Logic ghi nhận đặt tại một nơi. Cùng cơ chế bọc có thể dùng cho nhiều lời gọi qua interface đã chọn mà không lặp đoạn audit trong phương thức nghiệp vụ.
- `Method.invoke` bọc ngoại lệ do phương thức đích ném trong `InvocationTargetException`. `throw ex.getCause()` giữ lại nguyên nhân để bên gọi nhận `IllegalArgumentException`, thay vì vô tình đổi hợp đồng lỗi.
- `AUDIT success` chỉ chứng minh lời gọi trả về bình thường. Nó **không chứng minh** dữ liệu đã được lưu bền vững hay giao dịch đã commit; yêu cầu audit thực tế phải xác định điều kiện thành công.
- Chỉ lời gọi qua `audited` được chặn. Gọi trực tiếp `target.place(...)` bỏ qua handler; lời gọi tự gọi trong lớp đích cũng không tự chuyển qua đối tượng proxy này. Đây là giới hạn của **cách proxy trong ví dụ**, không phải mọi dạng AOP.
- Handler trong ví dụ chưa xử lý phân loại phương thức, lọc dữ liệu nhạy cảm, truy vết bất đồng bộ hoặc ghi audit bền vững. Không dùng nguyên xi làm chính sách bảo mật/kiểm toán production.

Một bài toán **không phù hợp** để chuyển sang handler trên: khi tính tổng tiền đơn hàng, quy tắc “khách hạng A được giảm 10% nếu đơn thỏa điều kiện” nên nằm ở mô hình chính sách giá và được gọi tường minh trong tiến trình chốt giá. Nếu che trong advice của `place()`, hành vi tính tiền trở nên khó truy vết, khó thử các trường hợp biên và dễ bị bỏ qua khi đường xử lý đổi.

## 6. Chọn AOP hay phương án khác?

| Cách tổ chức | Nên cân nhắc khi | Đánh đổi chính |
| --- | --- | --- |
| **Lời gọi trực tiếp / helper** | Số điểm áp dụng ít; tác động tới kết quả nghiệp vụ cần hiển thị. | Có thể lặp lời gọi ở nhiều nơi. |
| **Decorator hoặc wrapper tường minh** | Muốn thêm hành vi cho một interface/đối tượng với chuỗi bọc rõ ràng. | Phải truyền và cấu hình wrapper; phạm vi gắn với đối tượng cụ thể. |
| **Interceptor ở một biên có sẵn** | Hành vi thuộc ranh giới vào/ra cụ thể, như một pipeline xử lý yêu cầu hoặc thông điệp. | Phụ thuộc contract và phạm vi của pipeline. |
| **AOP theo pointcut/advice** | Cùng một chính sách kỹ thuật xuất hiện ở nhiều điểm, tiêu chí chọn ổn định và cơ chế weaving đã được kiểm chứng. | Luồng thực thi ít tường minh hơn; phạm vi tác động, thứ tự và kiểm thử phức tạp hơn. |
| **Domain service / policy / workflow** | Có quyết định nghiệp vụ, trạng thái, quyền theo đối tượng hoặc nhiều bước bù trừ. | Phải mô hình hóa và gọi hành vi một cách rõ ràng. |

Không phải mọi concern xuyên suốt đều dùng AOP tốt nhất. Ví dụ, gắn ID truy vết ở biên tiếp nhận rồi chuyển theo context có hợp đồng rõ thường dễ quản lý hơn so với một pointcut bắt mọi phương thức. Hãy chọn cơ chế ở **biên tự nhiên của trách nhiệm**.

### Bộ câu hỏi ra quyết định

Trước khi viết một aspect, tự trả lời:

1. **Chính sách hay nghiệp vụ?** Có thể thay đổi hoặc bỏ hành vi này mà kết quả nghiệp vụ vẫn đúng không? Nếu không, ưu tiên mô hình hóa nghiệp vụ tường minh.
2. **Có đủ điểm giống nhau?** Có thực sự nhiều điểm cùng nhu cầu và cùng điều kiện áp dụng, hay chỉ vài điểm nhìn có vẻ giống?
3. **Ranh giới xác định được không?** Có thể mô tả chính xác join point cần chọn và join point phải loại trừ? Refactor có làm quy tắc mất tác dụng âm thầm?
4. **Hợp đồng có được bảo toàn?** Advice có giữ nguyên tham số, kết quả, ngoại lệ, thứ tự tác dụng phụ, context và các bất biến quan trọng?
5. **Độ tin cậy phù hợp không?** Nếu logging/metrics thất bại, nghiệp vụ có được tiếp tục không? Nếu audit hoặc kiểm tra quyền thất bại, phải xử lý thế nào? Câu trả lời phải là thiết kế rõ ràng.
6. **Có chứng minh bằng thử nghiệm không?** Có thể đo overhead và viết kiểm thử xác nhận cả trường hợp được chọn, không được chọn, lỗi, bất đồng bộ và nhiều advice?
7. **Người bảo trì có tìm ra hành vi không?** Điểm áp dụng, thứ tự và chủ sở hữu aspect có dễ tra cứu từ mã và tài liệu?

Nếu nhiều câu trả lời vẫn mơ hồ, bắt đầu với giải pháp tường minh quy mô nhỏ thường giúp làm rõ hợp đồng; chỉ khái quát thành aspect khi đã thấy điểm chung ổn định.

## 7. Nếu vẫn chọn AOP, triển khai thế nào để giảm rủi ro?

- **Pointcut hẹp, có ý nghĩa:** chọn biên/nhóm chức năng thật sự cần chính sách; tránh bắt toàn hệ thống theo quy ước tên dễ vỡ. Với annotation hoặc marker, cần quy định ai có quyền gắn và gắn ở đâu.
- **Advice có một trách nhiệm:** ví dụ đo thời gian, ghi log hoặc áp chính sách riêng; tránh trộn log, retry, thay dữ liệu và phân quyền vào một advice khó kiểm tra.
- **Hợp đồng ngoại lệ và side effect rõ:** phân biệt thành công của phương thức với commit bền vững. Với retry, chỉ lặp khi thao tác có tính idempotent hoặc có cơ chế chống trùng lặp đã được kiểm chứng.
- **Thứ tự nhiều advice được xác định:** mô tả thứ tự trước/sau/around, đặc biệt với transaction, audit, đo thời gian và xử lý lỗi; thử nghiệm thứ tự quan sát được.
- **Kiểm thử trên cơ chế thực:** chạy integration test qua wiring hoặc weaving thực tế; có ca positive, negative, thất bại, nested call, concurrency và đường asynchronous phù hợp.
- **Đo tác động:** so sánh latency, throughput, lượng log và cấp phát trước/sau bật aspect trên tải gần thực tế; tránh log dữ liệu nhạy cảm.
- **Bảo đảm khả năng quan sát:** tài liệu hóa điểm áp dụng, tên aspect và cách xác định advice nào đang chạy; giữ thông tin correlation/trace qua các đường bất đồng bộ cần thiết.
- **Giới hạn phạm vi lỗi:** phát hành có kiểm soát, kiểm tra các điểm chịu tác động và chuẩn bị cách vô hiệu hóa an toàn nếu loại chính sách cho phép. Với chính sách fail-closed như kiểm soát quyền, không được tắt tùy tiện để “khôi phục” hệ thống.

## 8. Tự đánh giá qua bốn tình huống

**Tình huống A:** 40 thao tác I/O cần cùng một bộ đo thời gian và phát số liệu, không làm thay đổi giá trị trả về. Các điểm cần đo được định nghĩa ổn định. **AOP đáng cân nhắc**, sau khi so sánh với instrumentation tại biên I/O, kiểm thử phạm vi và đo overhead.

**Tình huống B:** Hệ thống đặt vé yêu cầu giữ chỗ, thanh toán và hoàn tác giữ chỗ khi thanh toán thất bại. **Không dùng AOP để ẩn orchestration**: trạng thái và bù trừ là phần lõi của quy trình, nên cần workflow/điều phối tường minh.

**Tình huống C:** Mọi lệnh “xóa tài liệu” phải kiểm tra người gọi có sở hữu tài liệu đó hay không. **Không coi một pointcut là bảo đảm duy nhất**: quyền phụ thuộc đối tượng dữ liệu và mọi đường truy cập. Đặt bất biến kiểm quyền tại biên tin cậy và logic nghiệp vụ; chỉ thêm AOP như một lớp kỹ thuật nếu chứng minh được bao phủ.

**Tình huống D:** Một tiện ích toán học nhỏ được gọi trong vòng lặp chặt hàng triệu lần; muốn log từng lần để điều tra hiệu năng. **Tránh chặn/log tất cả lượt gọi**: I/O làm méo kết quả đo và có thể áp đảo thời gian tính toán. Dùng profiling hoặc sampling có giới hạn.

## Kết luận

AOP mạnh khi **một chính sách kỹ thuật có nghĩa giống nhau** cần áp dụng nhất quán trên nhiều điểm thực thi, và tập điểm đó có thể xác định, kiểm thử, quan sát được. Giá đổi lại là luồng thực thi ít hiển thị tại nơi gọi, nhiều ràng buộc về thứ tự, ngoại lệ, hiệu năng, concurrency và khả năng bảo trì. Quy tắc nghiệp vụ, bảo đảm dữ liệu quan trọng và tiến trình nhiều bước nên được mô hình hóa rõ ràng. Câu hỏi quan trọng nhất trước khi chọn AOP là: **“Hành vi này có thực sự là chính sách độc lập với nghiệp vụ, và ta có chứng minh được phạm vi tác động của nó không?”**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-foundations-009">Lộ trình từ khái niệm đến cơ chế và ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Lộ trình từ khái niệm đến cơ chế và ứng dụng AOP

## Bạn sẽ học được gì?

Aspect-Oriented Programming (**AOP**, lập trình hướng khía cạnh) là cách tổ chức những hành vi có xu hướng xuất hiện tại nhiều điểm thực thi của chương trình thành các mô-đun riêng, thường gọi là **aspect**. Để sử dụng đúng, cần học theo trình tự: **vì sao cần AOP → AOP mô tả điểm can thiệp như thế nào → cơ chế can thiệp hoạt động ra sao → viết và kiểm thử → quyết định khi nào áp dụng**. Biết cú pháp của một công cụ mà chưa hiểu chuỗi này dễ dẫn tới pointcut quá rộng, hành vi ẩn hoặc suy luận sai về những lời gọi thực sự được xử lý.

Sau bài học, bạn có thể:

1. Nhận ra *cross-cutting concern* (mối quan tâm xuyên suốt), hiện tượng *scattering* (mã một chính sách bị rải rác) và *tangling* (mã chính sách đan xen nghiệp vụ).
2. Giải thích chính xác **join point**, **pointcut**, **advice**, **aspect**, **target**, **weaving**, cũng như giới hạn tùy từng cơ chế AOP.
3. Đọc một luồng thực thi có advice chạy trước/sau/bao quanh; xác định điều gì xảy ra khi phương thức trả về hoặc ném lỗi.
4. Phân biệt proxy dựa trên interface, proxy dựa trên lớp và weaving vào mã; thực hành một minh họa Java 17 chạy được.
5. Tự thiết kế và kiểm thử một chính sách xuyên suốt, từ phạm vi lựa chọn đến thứ tự, ngoại lệ, chi phí và rủi ro vận hành.
6. Chọn AOP cho bài toán phù hợp và từ chối AOP khi quy tắc cần thể hiện tường minh trong mô hình nghiệp vụ.

Bài học dùng **AOP như một kỹ thuật lập trình chung**, độc lập với sản phẩm hoặc thư viện cụ thể. Ký pháp được ghi là *giả mã* chỉ nhằm giải thích khái niệm; cú pháp pointcut và tập join point thực tế phụ thuộc vào cơ chế triển khai.

## 1. Bản đồ học tập: đi từ vấn đề đến giải pháp

| Chặng | Câu hỏi cần trả lời | Kết quả cần tự chứng minh |
| --- | --- | --- |
| **0. Nền tảng** | Interface, lời gọi phương thức, decorator, reflection và ngoại lệ hoạt động thế nào? | Giải thích được call stack, hợp đồng phương thức và một wrapper thông thường. |
| **1. Nhận diện vấn đề** | Vì sao cùng một chính sách xuất hiện ở nhiều chức năng? | Phân biệt scattering/tangling với logic nghiệp vụ cần giữ tường minh. |
| **2. Nắm mô hình AOP** | Pointcut chọn gì, advice làm gì, aspect chứa gì? | Giải thích được join point, pointcut, advice, target và weaving. |
| **3. Thiết kế điểm áp dụng** | Phạm vi nào phải khớp, phải loại trừ và hợp đồng advice là gì? | Viết được ca dương/âm và sơ đồ success/error path. |
| **4. Hiểu cách kết hợp** | Lời gọi nào thực sự đi qua advice? | So sánh proxy với weaving, chỉ ra đường đi được hoặc bị bỏ qua. |
| **5. Thực hành Java** | Làm sao thêm chính sách mà không đổi logic chính? | Chạy demo Java, xác nhận marker, exception và phạm vi áp dụng. |
| **6. Nâng cao** | Nhiều advice, recursion, lỗi, bất đồng bộ và hiệu năng thì sao? | Dự đoán thứ tự, bảo toàn ngữ nghĩa và kiểm thử edge cases. |
| **7. Ứng dụng** | Khi nào dùng AOP, khi nào chọn thiết kế khác? | Đề xuất chính sách, cơ chế, ma trận kiểm thử và phương án thay thế. |

**Cách học:** Không chuyển chặng chỉ vì đã thuộc định nghĩa. Mỗi chặng có một câu hỏi hoặc phép thử đầu ra; nếu chưa giải thích được trường hợp biên, quay lại chặng liên quan.

## 2. Chặng 0 — Nền tảng cần có trước khi nhìn vào AOP

AOP không thay thế kiến thức hướng đối tượng hoặc luồng thực thi Java. Đặc biệt cần hiểu:

- **Đối tượng và interface:** Bên gọi có thể nắm một interface còn phía sau là đối tượng thật hoặc đối tượng bọc (*wrapper*). Kết quả phụ thuộc đối tượng nào nhận lời gọi.
- **Decorator:** Một đối tượng cài cùng interface, xử lý trước/sau rồi ủy quyền cho đối tượng gốc. Đây là ví dụ dễ hiểu về thêm hành vi tại biên, dù chưa có hệ thống pointcut.
- **Call stack:** Phương thức có thể trả về bình thường, ném exception hoặc gọi các phương thức khác; thứ tự chạy và cleanup quyết định ngữ nghĩa.
- **Reflection và dynamic dispatch:** Java có thể tìm và gọi phương thức tại runtime, nhưng cần hiểu kiểu phương thức, đối tượng đích và việc đóng gói ngoại lệ.
- **Hợp đồng phương thức:** Input, output, exception, side effect và điều kiện thành công quan trọng hơn việc viết ít dòng mã.

**Bài tự kiểm tra:** Nếu `x.transfer()` gọi `this.validate()`, hãy chỉ ra đối tượng nhận từng lời gọi. Nếu thay `x` bằng wrapper, bạn có chắc lần gọi nội bộ cũng đi qua wrapper không? Câu trả lời phải dựa vào sơ đồ đối tượng thực tế, không dựa vào tên phương thức.

## 3. Chặng 1 — Thấy vấn đề trước khi đề xuất aspect

Hãy tưởng tượng ứng dụng có nhiều thao tác như `send()`, `importData()`, `calculate()`. Mỗi thao tác đều cần ghi lại thời gian, mã tương quan và trạng thái lỗi. Nếu chép cùng đoạn xử lý vào từng hàm, sẽ có hai dạng khó khăn:

- **Scattering:** Cùng một chính sách ghi đo lường nằm ở nhiều vị trí; thay đổi định dạng phải sửa nhiều nơi và dễ bỏ sót.
- **Tangling:** Một hàm xử lý nhập dữ liệu phải xen kẽ logic chính với log, đo thời gian, xử lý thống kê; người đọc khó nhận ra đâu là nghiệp vụ.

Một thiết kế có thể tách nhóm hành vi này thành module độc lập, rồi định nghĩa **tập điểm thực thi cần áp dụng**. Đó là động cơ cốt lõi để nghĩ tới AOP.

Nhưng không phải mọi phần mã bị lặp đều là mối quan tâm xuyên suốt phù hợp với aspect. Chẳng hạn, “đơn hàng vượt hạn mức phải được phê duyệt” có thể xuất hiện tại nhiều tuyến xử lý, nhưng đó là **quyết định nghiệp vụ**; nó cần hiện rõ trong domain policy hoặc workflow, vì ảnh hưởng trực tiếp tính đúng đắn và có thể phụ thuộc ngữ cảnh dữ liệu.

### Cách nhận diện một ứng viên AOP

Hãy viết thành câu: “Tại **những điểm X**, mỗi lần **sự kiện Y** xảy ra, thực hiện **chính sách Z** với **hợp đồng H**.”

Ví dụ hợp lý:

> Tại các điểm gọi thao tác lưu trữ được đánh dấu, khi mỗi thao tác bắt đầu/kết thúc, ghi số lần gọi và trạng thái thành công/thất bại; việc đo không thay đổi dữ liệu lưu và không được làm lộ tham số nhạy cảm.

Nếu không xác định được X, Y, Z, H một cách rõ ràng, có thể chưa đủ điều kiện để thiết kế aspect an toàn.

**Đầu ra chặng 1:** Liệt kê ít nhất ba nơi có cùng chính sách kỹ thuật và một ngoại lệ rõ ràng **không** nên bị áp dụng. Đây sẽ là dữ liệu kiểm thử pointcut ở chặng sau.

## 4. Chặng 2 — Mô hình khái niệm: thành phần nào chịu trách nhiệm gì?

| Khái niệm | Trả lời câu hỏi | Chú ý |
| --- | --- | --- |
| **Concern** | Cần giải quyết vấn đề gì? | Một concern xuyên suốt có thể là quan sát độ trễ, audit hoặc chính sách kỹ thuật chung. |
| **Aspect** | Chính sách xuyên suốt được đóng gói ở đâu? | Có thể kết hợp logic advice với biểu thức chọn điểm và cấu hình liên quan. |
| **Target** | Mã/đối tượng nghiệp vụ gốc là gì? | Logic đích vẫn mang trách nhiệm chính của chức năng. |
| **Join point** | Điểm can thiệp có thể quan sát hoặc xử lý trong luồng thực thi là gì? | Ví dụ một lần *execution* của phương thức; một số cơ chế hỗ trợ thêm *call*, truy cập field, constructor... |
| **Pointcut** | Chọn tập join point nào? | Có thể mô tả theo chữ ký, loại, package, marker, ngữ cảnh... tùy công cụ. |
| **Advice** | Khi điểm được chọn xảy ra, chạy hành vi gì và lúc nào? | Có thể là before, after, around và các biến thể khi return/throw. |
| **Weaving** | Advice được kết hợp với mã chạy bằng cách nào? | Có thể ở compile time, load time hoặc runtime tùy kỹ thuật. |
| **Introduction / inter-type declaration** | Có mở rộng cấu trúc/khả năng của kiểu hay không? | Chỉ có ở một số công nghệ AOP; là chủ đề nâng cao, không phải tính năng bắt buộc. |

### Một dòng thời gian mẫu

Giả sử có một join point là lúc thực thi `AccountService.transfer(...)`. Một advice quan sát có thể tạo luồng:

```text
Bên gọi
   |
   v
[Before advice] -> [Thực thi transfer] -> [After returning advice]
                                    |
                                    +-> nếu ném lỗi: [After throwing advice]
   |
   v
Bên gọi nhận giá trị hoặc exception
```

Đây là **sơ đồ khái niệm**, chưa mô tả thứ tự của nhiều aspect hoặc cleanup. Advice kiểu “after/finally” thường được diễn giải là chạy khi join point kết thúc bằng cả hai đường; advice chỉ dành cho *returning* và advice chỉ dành cho *throwing* có điều kiện kích hoạt khác nhau. Tên loại advice, thứ tự cụ thể và khả năng can thiệp ngoại lệ phụ thuộc công cụ.

### Phân biệt “điểm có thể can thiệp” và “điểm đã chọn”

Giả sử chương trình có 100 lần thực thi phương thức. Cơ chế AOP hỗ trợ điểm thực thi phương thức sẽ xem mỗi lần đó như một join point tiềm năng. Một pointcut chỉ chọn 15 điểm thỏa điều kiện. Advice chỉ chạy tại những điểm thực sự được chọn **và đi qua con đường can thiệp hợp lệ**.

Không nên đồng nhất:

- “Công cụ có thể can thiệp vào phương thức” với “mọi phương thức đều bị can thiệp”.
- “Một biểu thức pointcut khớp chữ ký” với “mọi đường gọi runtime đều chắc chắn chạy advice”.
- “Join point thực thi” với “join point gọi phương thức”. Với công nghệ phân biệt `call` và `execution`, chúng là hai vị trí và hai ngữ nghĩa khác nhau.

**Đầu ra chặng 2:** Khi nhìn thấy một aspect, bạn phải mô tả được **nó chọn điểm nào**, **hành vi gì**, **diễn ra lúc nào**, và **được kết hợp qua cơ chế gì**.

## 5. Chặng 3 — Thiết kế pointcut và advice trước khi viết mã

### 5.1. Pointcut là hợp đồng phạm vi

Một điểm cắt tốt cần có cả **tập được chọn** lẫn **tập phải loại trừ**. Giả mã tiêu chí:

```text
chọn join point nếu:
    là execution của thao tác công khai tại biên lưu trữ
    VÀ đã được đánh dấu cần đo
    VÀ không thuộc mã đo lường
```

Các công nghệ khác nhau biểu diễn quy tắc bằng biểu thức chữ ký, annotation/marker, quan hệ kiểu, đối tượng runtime hoặc mã cấu hình. Ký pháp ví dụ ở trên **không phải biểu thức pointcut dùng nguyên xi trong một thư viện**.

Đánh giá pointcut theo bốn câu hỏi: có chọn đủ điểm mong muốn, có chọn nhầm chỗ không, refactor có làm thay đổi tập điểm âm thầm không, và có cách nào kiểm thử được phạm vi này? Một pointcut rất rộng có thể làm logic kỹ thuật xuất hiện ở những vùng chưa từng được xét tới.

### 5.2. Advice trả lời chuyện gì xảy ra tại điểm đã chọn

| Kiểu hành vi | Cách hiểu | Ví dụ thích hợp |
| --- | --- | --- |
| **Before** | Trước khi join point thực thi. | Bắt đầu thu thập dấu vết; kiểm tra điều kiện kỹ thuật không thay đổi nghiệp vụ. |
| **After returning** | Sau khi thực thi kết thúc bình thường. | Ghi số lần thành công sau khi có kết quả trả về. |
| **After throwing** | Khi join point kết thúc bằng exception. | Ghi loại lỗi, duy trì exception ban đầu. |
| **After / finally** | Dọn dẹp hoặc ghi nhận bất kể bình thường hay lỗi. | Kết thúc context đo đạc. |
| **Around** | Bao quanh quá trình thực thi; có thể quyết định cách hoặc có gọi tiếp hay không tùy cơ chế. | Đo khoảng thời gian tổng thể, bọc một hoạt động idempotent bằng retry có kiểm soát. |

Với advice kiểu around, khái niệm **proceed** thường là thao tác tiếp tục vào phần bên trong của chuỗi xử lý, có thể gồm advice tiếp theo và target. Một implementation có thể không gọi, gọi một lần hoặc gọi nhiều lần; mỗi lựa chọn đều ảnh hưởng hợp đồng của chương trình.

Ví dụ giả mã:

```text
around operation:
    start = monotonicClock()
    try:
        return proceed()         // chuyển quyền thực thi vào bên trong
    finally:
        recordDuration(monotonicClock() - start)
```

Mục đích của `finally` là đo cả đường lỗi. Khi đo độ trễ, nên dùng nguồn thời gian **đơn điệu** (*monotonic*) phù hợp, thay vì lấy thời gian đồng hồ lịch có thể bị chỉnh trong khi chạy. Nếu hàm ghi đo trong `finally` cũng ném lỗi, nó có thể che mất lỗi gốc; phải quyết định chính sách xử lý lỗi ghi đo.

### 5.3. Hợp đồng quan trọng hơn việc advice chạy thành công

Trước khi bật một advice hãy ghi ra:

- **Đầu vào:** Có đọc hoặc sửa tham số không? Có dữ liệu nhạy cảm cần loại khỏi log không?
- **Đầu ra:** Có đổi giá trị trả về không? Điều kiện ghi “thành công” là phương thức trả về hay cam kết bền vững?
- **Ngoại lệ:** Có giữ nguyên loại/nguyên nhân exception? Nếu logging lỗi thì phương thức chính ra sao?
- **Tác dụng phụ:** Có thể bị chạy lặp do retry? Một lần gọi phải được thực thi đúng một lần hay có cơ chế idempotency?
- **Context:** Metadata, correlation ID, transaction hoặc thread context được tạo và xóa ở đâu?

**Đầu ra chặng 3:** Mô tả pointcut theo tiêu chí kiểm chứng được, vẽ được success/error path, và giải thích được vì sao advice không thay đổi hợp đồng ngoài phần chính sách đã công bố.

## 6. Chặng 4 — Cơ chế thực thi: từ cấu hình đến bytecode và lời gọi

Một lỗi phổ biến khi học AOP là nghĩ rằng chỉ cần viết aspect thì mọi lệnh gọi khớp tên sẽ tự động chạy advice. Thực tế phải hiểu **đường đi của lời gọi** và **cách mã can thiệp được chèn vào**.

### 6.1. Proxy dựa trên interface

Bên gọi giữ tham chiếu một đối tượng proxy cài cùng interface với target. Khi bên gọi gọi phương thức **qua proxy**, proxy chạy handler/advice rồi mới ủy quyền đến target.

```text
client -> proxy [advice] -> target.method(...)
                  ^                 |
                  +---- result/error+
```

Ưu điểm là có thể thêm hành vi ở runtime, dễ hiểu từ góc nhìn một wrapper. Giới hạn: một lời gọi đi trực tiếp tới target không thông qua proxy; lời gọi nội bộ trên `this` ở target thường cũng bỏ qua proxy. Với proxy dựa trên interface, chỉ các phương thức giao diện được lộ qua proxy. Đây là giới hạn **của mô hình proxy tương ứng**.

### 6.2. Proxy dựa trên lớp

Một số cơ chế tạo lớp con hoặc đối tượng tương đương để chặn phương thức. Phạm vi khác proxy interface; hành vi `final`, `private`, constructor, khả năng tạo lớp con và dispatch tùy cách triển khai. Không nên suy ra rằng cơ chế dựa trên lớp sẽ can thiệp được **mọi** phương thức của **mọi** đối tượng.

### 6.3. Weaving vào mã

Một hướng khác kết hợp hành vi của aspect với mã lớp, có thể diễn ra:

- **Compile-time:** khi biên dịch mã nguồn/bytecode.
- **Post-compile/binary weaving:** trên bytecode đã biên dịch trước khi nạp hoặc phân phối.
- **Load-time:** khi lớp được nạp vào runtime.
- **Runtime transformation:** khi môi trường hỗ trợ sửa/biến đổi mã tại thời điểm thực thi phù hợp.

Các thời điểm này là **các khả năng kỹ thuật**, không có nghĩa mọi công cụ đều hỗ trợ tất cả. Weaving có thể cung cấp tập join point rộng hơn mô hình proxy đơn giản, nhưng thêm yêu cầu build, class loading, công cụ quan sát và tương thích.

### 6.4. Bảng so sánh cơ chế

| Tiêu chí | Proxy theo interface | Proxy dựa trên lớp | Weaving bytecode |
| --- | --- | --- | --- |
| Nơi lời gọi được chặn | Qua tham chiếu proxy thực sự. | Qua điểm dispatch có thể được lớp đại diện can thiệp. | Ở điểm được đưa logic vào bytecode. |
| Điểm mạnh | Rõ ràng về contract interface, có thể minh họa bằng JDK. | Có thể dùng trong một số tình huống không có interface. | Có thể hỗ trợ nhiều dạng join point hơn. |
| Điểm cần kiểm tra | Gọi trực tiếp target, self-invocation, phương thức không thuộc interface. | Quy tắc override, `final`, `private`, constructor, khả năng tạo proxy. | Thứ tự weaving, classloader, build, tương thích bytecode và công cụ debug. |
| Điều cần chứng minh | Call path có đi qua proxy không? | Method có bị chặn theo đúng cơ chế không? | Bytecode đang chạy đã được weave và pointcut có khớp không? |

**Đầu ra chặng 4:** Với một lời gọi cụ thể, vẽ được `caller → [proxy/weaving?] → target`, thay vì kết luận chỉ bằng tên phương thức hay annotation.

## 7. Chặng 5 — Thực hành Java 17: mô phỏng AOP bằng dynamic proxy

Ví dụ sau dùng **JDK dynamic proxy** để làm rõ quan hệ giữa điểm được chọn và advice. Một marker `@Observed` được đặt trên phương thức **interface**; handler chỉ in quan sát khi đúng marker. Chúng ta chủ động giữ quy tắc nghiệp vụ trong target. Đây là **minh họa AOP ở mức cơ chế**, không phải engine AOP hoàn chỉnh và không cung cấp ngôn ngữ pointcut phổ quát.

Tạo tệp `AopLearningPath.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AopLearningPath {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed { }

    interface PaymentService {
        @Observed
        String pay(String reference);
        String health();
    }

    static final class DefaultPaymentService implements PaymentService {
        @Override
        public String pay(String reference) {
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("reference required");
            }
            System.out.println("BUSINESS pay " + reference);
            return "PAID " + reference;
        }

        @Override
        public String health() {
            System.out.println("BUSINESS health");
            return "UP";
        }
    }

    static PaymentService observed(PaymentService target) {
        return (PaymentService) Proxy.newProxyInstance(
                PaymentService.class.getClassLoader(),
                new Class<?>[] { PaymentService.class },
                (proxy, method, args) -> invoke(target, method, args)
        );
    }

    static Object invoke(PaymentService target, Method method, Object[] args)
            throws Throwable {
        boolean selected = method.isAnnotationPresent(Observed.class);
        if (selected) {
            System.out.println("ADVICE before " + method.getName());
        }
        try {
            Object result = method.invoke(target, args);
            if (selected) {
                System.out.println("ADVICE success " + method.getName());
            }
            return result;
        } catch (InvocationTargetException ex) {
            if (selected) {
                System.out.println("ADVICE failure " + method.getName());
            }
            throw ex.getCause();
        }
    }

    public static void main(String[] args) {
        PaymentService target = new DefaultPaymentService();
        PaymentService proxied = observed(target);

        System.out.println("RESULT " + proxied.pay("P-17"));
        System.out.println("RESULT " + proxied.health());

        try {
            proxied.pay("");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }
    }
}
```

Chạy trên JDK 17 trở lên:

```bash
javac AopLearningPath.java
java AopLearningPath
```

Đầu ra kỳ vọng, theo đúng thứ tự:

```text
ADVICE before pay
BUSINESS pay P-17
ADVICE success pay
RESULT PAID P-17
BUSINESS health
RESULT UP
ADVICE before pay
ADVICE failure pay
CALLER caught reference required
```

### Đọc chương trình theo từng thành phần

1. **Target:** `DefaultPaymentService` thực hiện `pay` và `health`. Luật `reference` không được rỗng là nghiệp vụ, nên nằm trong target.
2. **Join point minh họa:** lần gọi phương thức interface qua đối tượng `proxied`; khả năng chặn cụ thể là do dynamic proxy.
3. **Pointcut minh họa:** điều kiện `method.isAnnotationPresent(Observed.class)`. Trong ví dụ chỉ `pay` được gắn marker tại **phương thức interface** nên `health` không phát sự kiện advice.
4. **Advice minh họa:** các dòng `ADVICE before/success/failure`. Handler cũng gọi target kể cả với `health`, nhưng không ghi advice ở điểm không được chọn.
5. **Cơ chế kết hợp:** đối tượng trả từ `Proxy.newProxyInstance` đảm nhận việc chuyển lời gọi qua handler. Không có weaving vào bytecode target trong ví dụ.
6. **Ngoại lệ:** `Method.invoke` bọc exception của phương thức đích trong `InvocationTargetException`; `throw ex.getCause()` truyền tiếp lỗi gốc tới caller. Trong phiên bản thực tế còn cần chính sách rõ về lỗi do chính advice gây ra.

**Thử nghiệm đối chứng:**

- Đổi dòng `proxied.pay("P-17")` thành `target.pay("P-17")`: nghiệp vụ vẫn thực thi nhưng sẽ không có dòng `ADVICE ...` cho lời gọi đó, vì bỏ qua proxy.
- Bỏ annotation `@Observed` tại `PaymentService.pay`: handler vẫn chuyển lời gọi, nhưng quy tắc lựa chọn không khớp, nên không in dòng `ADVICE ...`.
- Gọi `proxied.health()`: không có advice, chứng minh **điểm phải loại trừ** cũng được kiểm thử.
- Đưa marker **chỉ** vào phương thức triển khai mà không sửa cách tìm annotation: `method` mà dynamic proxy nhận là phương thức interface, vì vậy `isAnnotationPresent` trên `method` không tự dò marker ở implementation. Đây là ví dụ về sự khác nhau giữa **metadata ở đâu** và **pointcut thực sự kiểm tra ở đâu**.

### Tại sao đây vẫn chưa phải bộ AOP đầy đủ?

Handler ở đây chỉ chặn lời gọi qua interface, dùng một predicate Java để chọn phương thức và ghi log đồng bộ. Một hệ thống AOP tổng quát còn có thể hỗ trợ biểu thức pointcut, nhiều advice có thứ tự, join point khác phương thức, weaving, binding ngữ cảnh và quản lý lifecycle. Do đó, không dùng kết quả demo để kết luận giới hạn hoặc khả năng của **mọi** cơ chế AOP.

**Đầu ra chặng 5:** Chạy được chính xác ví dụ, giải thích từng dòng kết quả và dự đoán đúng kết quả khi thay một trong ba yếu tố: cách gọi, marker hoặc tình huống ném lỗi.

## 8. Chặng 6 — Học nâng cao: khi một aspect không còn đơn giản

### 8.1. Nhiều advice và thứ tự lồng nhau

Giả sử advice A và B đều là around tại cùng một join point. Một thứ tự có thể là:

```text
A.before
  B.before
    target
  B.after
A.after
```

Nếu B chạy trước A ở phía ngoài, thứ tự quan sát đổi khác. Các cơ chế AOP khác nhau có quy tắc về *precedence/order* và cách xác định tie-breaker. Không nên dựa vào thứ tự ngẫu nhiên hoặc thứ tự tình cờ xuất hiện trong mã.

Đặc biệt, **đo thời gian** ở ngoài lớp retry sẽ đo toàn bộ các lần thử, còn ở bên trong lớp retry có thể đo từng lần; cả hai đều đúng cho mục đích khác nhau. Tương tự, audit “thành công” trước commit không mang cùng nghĩa với audit sau khi dữ liệu đã được lưu bền vững.

### 8.2. Reentrancy, recursion và tự gọi

Một aspect có thể vô tình gọi vào phương thức cũng khớp chính pointcut của nó, tạo vòng lặp:

```text
advice -> logger -> method được pointcut chọn -> advice -> ...
```

Giải pháp có thể là pointcut loại trừ module/loại phương thức phục vụ đo lường, hoặc thiết kế tầng quan sát không tái kích hoạt chính nó. Kỹ thuật “cờ đang thực thi” cũng có rủi ro nếu context không được xóa đúng cách. Với recursion hay self-invocation, trước hết phải xác minh **cơ chế can thiệp**: proxy đơn giản và weaving vào bytecode có hành vi khác nhau.

### 8.3. Around advice có thể thay đổi ngữ nghĩa

Một around advice gọi `proceed()` hai lần có thể tạo ra hai tác dụng phụ. Vì vậy, **retry không thể thêm tùy tiện** cho thanh toán, chuyển khoản, gửi thông báo hoặc cập nhật trạng thái. Cần phân loại lỗi có thể thử lại, giới hạn số lần, backoff, timeout tổng, idempotency và bảo đảm ngừng đúng hạn.

Nếu advice đổi tham số, giá trị trả về hoặc exception, nó đang mở rộng hợp đồng. Điều này có thể hợp lý ở một biên kỹ thuật đã công bố, nhưng nguy hiểm nếu người đọc nghiệp vụ không biết. Đối với phần lớn advice quan sát, thiết kế an toàn hơn là không sửa kết quả hay che lỗi gốc.

### 8.4. Tài nguyên, exception và lỗi của chính advice

Phân biệt ít nhất ba lỗi:

1. **Target lỗi:** Advice cần ghi nhận rồi cho lỗi đi qua hay chuyển sang một kiểu lỗi có hợp đồng rõ?
2. **Chính advice lỗi:** Log/metrics hỏng thì nghiệp vụ có tiếp tục không? Với audit bắt buộc, fail-open có thể sai yêu cầu.
3. **Weaving/configuration lỗi:** Aspect không được áp dụng ở những nơi cần thiết, hoặc áp dụng quá rộng; đây là lỗi phạm vi, có thể không tạo exception nào.

Nếu advice mở tài nguyên, cần cleanup trên mọi đường thoát. Nếu advice ghi log nguyên input/output, cần kiểm soát dữ liệu cá nhân và bí mật. “Aspect dùng chung” có thể khuếch đại một lỗi nhỏ trên nhiều chức năng.

### 8.5. Concurrency và asynchronous execution

Đừng giả định *sau khi phương thức trả về* đồng nghĩa *sau khi công việc thực tế hoàn tất*. Một phương thức trả về đối tượng biểu diễn kết quả trong tương lai có thể trả về ngay, còn thao tác chạy sau. Advice quanh lời gọi đó chỉ quan sát **giai đoạn gọi/đăng ký công việc**, trừ khi cơ chế được thiết kế riêng để quan sát thời điểm hoàn tất asynchronous.

Thông tin theo thread (ví dụ `ThreadLocal`) không tự động trở thành ngữ cảnh nghiệp vụ xuyên suốt mọi luồng thực thi. Chuyển tác vụ sang executor, callback hoặc runtime bất đồng bộ có thể cần chiến lược truyền context, làm sạch và kiểm thử riêng. Đây là giới hạn cần xem xét ở thiết kế observability và tracing.

### 8.6. Hiệu năng và phạm vi ảnh hưởng

Mỗi điểm được can thiệp có thể phát sinh proxy dispatch, matching/binding, đo thời gian, cấp phát, serialization hoặc I/O. Tác động lớn nhất có thể đến từ log và truy xuất hệ thống bên ngoài, không nhất thiết từ bản thân lời gọi advice. Hãy benchmark khi cần, đo ở tải gần thực tế và tránh áp một advice nặng lên hot path.

Pointcut rộng cũng là **phạm vi triển khai rộng**: một thay đổi nhỏ có thể làm nhiều luồng chức năng cùng đổi hành vi. Kiểm thử phạm vi và khả năng truy xuất “tại sao advice chạy ở đây?” là yêu cầu bảo trì, không phải phần trang trí.

### 8.7. Khả năng mở rộng mô hình

Một số công nghệ hỗ trợ *introduction/inter-type declarations*, *join point context binding*, *annotation matching*, hoặc *per-object/per-control-flow aspect lifecycle*. Đây là công cụ nâng cao, nhưng chỉ nên học sau khi nắm vững tính đúng đắn của pointcut, advice và weaving. Chúng làm tăng sức biểu đạt đồng thời làm tăng tải nhận thức và yêu cầu kiểm thử.

**Đầu ra chặng 6:** Với hai aspect, bạn vẽ được thứ tự trước/sau; với một lỗi của target hoặc advice, bạn chỉ ra được caller nhận gì; với tác vụ async, bạn xác định đúng “kết thúc lời gọi” và “kết thúc công việc” khác nhau thế nào.

## 9. Chặng 7 — Chuyển thành ứng dụng có kiểm chứng

### 9.1. Bài tập tổng hợp: đo thời gian và lỗi cho thao tác I/O

**Đề bài:** Một nhóm phương thức I/O đã được đánh dấu cần thu số lần gọi, thời lượng và số lần thất bại. Logic nghiệp vụ không được đổi; phương thức không có marker không được đo. Không ghi dữ liệu nhạy cảm.

Làm theo các bước:

1. **Xác định concern:** Một chính sách quan sát thống nhất; không phải chính sách quyết định dữ liệu hợp lệ.
2. **Xác định target:** Các phương thức I/O phục vụ nghiệp vụ.
3. **Định nghĩa pointcut:** Đánh dấu rõ nhóm điểm cần đo và nhóm loại trừ, đặc biệt code nội bộ của bộ đo.
4. **Thiết kế advice:** Trước lúc gọi lưu thời điểm bắt đầu; khi hoàn tất đo độ trễ, tăng bộ đếm success hoặc failure; exception gốc phải tới caller.
5. **Chọn cơ chế:** Proxy nếu các lời gọi đều đi qua biên interface phù hợp; cân nhắc weaving nếu cần chặn các điểm runtime không qua proxy, và có khả năng quản lý build/class loading.
6. **Thiết lập ngữ nghĩa:** Nếu phương thức trả future/handle, metric là độ trễ tạo tác vụ hay thời gian tác vụ hoàn tất? Chọn và đặt tên rõ.
7. **Kiểm thử:** Có ca được chọn, không được chọn, target ném lỗi, target được gọi trực tiếp, lời gọi nội bộ, nhiều aspect và async nếu ứng dụng có.
8. **Đo và rà soát:** Benchmark trước/sau, kiểm soát sampling/cardinality, kiểm tra rò rỉ dữ liệu và tài liệu hóa phạm vi.
9. **Triển khai có giám sát:** Quan sát tỷ lệ lỗi, độ trễ và khối lượng log/metric sau thay đổi.

### 9.2. Ma trận chọn kỹ thuật

| Nhu cầu | Điểm bắt đầu hợp lý | Khi nào AOP đáng cân nhắc? |
| --- | --- | --- |
| Đo thời gian nhiều phương thức độc lập | Instrumentation ở biên, decorator hoặc helper dùng chung. | Có nhiều join point ổn định, cùng chính sách và cần áp dụng nhất quán. |
| Gắn mã truy vết cho xử lý đầu vào | Interceptor hoặc wrapper tại biên tiếp nhận, truyền context có chủ đích. | Cần mở rộng đến những điểm thực thi xác định, chứng minh được context và phạm vi. |
| Ghi kiểm toán có yêu cầu bền vững | Thiết kế luồng audit, transaction/outbox phù hợp hợp đồng. | AOP chỉ có thể hỗ trợ gắn hook nếu ngữ nghĩa bền vững và bao phủ đã được kiểm chứng. |
| Kiểm tra quyền theo đối tượng dữ liệu | Tầng quyết định quyền và bất biến nghiệp vụ tường minh. | Có thể dùng để bổ trợ một kiểm tra kỹ thuật ở biên; không là bảo vệ duy nhất khi có đường truy cập bỏ qua. |
| Cache kết quả hàm thuần | Memoization/decorator có khóa và quy tắc invalidation rõ. | Nhiều điểm có cùng cache semantics và phương thức không có side effect nguy hiểm. |
| Điều phối nhiều bước nghiệp vụ | Domain service, state machine, workflow/orchestrator. | Thường không dùng AOP để che quyết định trạng thái hoặc bù trừ. |
| Retry thao tác mạng | Retry tại client/boundary với idempotency, timeout và backoff. | Chỉ khi pointcut khớp chính xác tập thao tác có thể retry và hợp đồng được thử nghiệm. |

Chọn công cụ từ **yêu cầu bảo toàn hành vi**, không từ mong muốn áp dụng một mô hình lập trình.

### 9.3. Bài kiểm tra phạm vi và hành vi

Với bất kỳ aspect nào chuẩn bị dùng, phải có tối thiểu:

| Trường hợp | Kỳ vọng |
| --- | --- |
| Điểm thuộc pointcut | Advice chạy đúng số lần, đúng loại và đúng thứ tự đã công bố. |
| Điểm không thuộc pointcut | Không có side effect của advice. |
| Target trả về bình thường | Bên gọi nhận giá trị đúng; advice success xử lý như thiết kế. |
| Target ném exception | Exception đến caller đúng hợp đồng; cleanup chạy. |
| Advice tự gặp lỗi | Hành vi tiếp tục/dừng đã được xác định và có kiểm thử. |
| Gọi trực tiếp target hoặc self-call | Kết quả có/không được can thiệp khớp với cơ chế thực tế; không suy đoán. |
| Có nhiều advice | Thứ tự và ý nghĩa kết quả được kiểm chứng. |
| Có cạnh tranh hoặc async | Context và sự kiện “hoàn tất” được đo đúng mốc. |
| Refactor chữ ký/package/marker | Tập điểm được chọn không tự thay đổi ngoài dự kiến. |

**Đầu ra chặng 7:** Một bài thực hành hoàn chỉnh cần có mô tả chính sách, phương án cơ chế, bảng ca kiểm thử và giải thích vì sao không vi phạm hợp đồng nghiệp vụ.

## 10. Các ngộ nhận thường gặp và cách tự sửa

**“AOP là công cụ tự động thêm log.”** Ghi log chỉ là một ứng dụng. Cốt lõi của AOP là mô hình hóa và kết hợp một hành vi ở nhiều join point theo quy tắc chọn điểm.

**“Gắn marker là chắc chắn advice chạy.”** Marker chỉ là dữ liệu để có thể chọn. Cần có cơ chế AOP thực sự được kích hoạt, điểm được chọn và đường thực thi đi qua can thiệp.

**“Proxy luôn bắt mọi lời gọi của target.”** Sai với các mô hình proxy thông dụng nếu đường gọi bỏ qua proxy hoặc diễn ra nội bộ trong target. Phải vẽ lại đường gọi thực.

**“Before/after không thể ảnh hưởng nghiệp vụ.”** Advice có thể ném lỗi, tiêu tốn tài nguyên, thay đổi ngữ cảnh hoặc làm trễ xử lý. Around còn có thể không gọi hoặc gọi lại target.

**“AOP luôn chậm.”** Overhead tùy cơ chế, nội dung advice và tần suất kích hoạt; quyết định bằng phép đo. Một lời ghi log đồng bộ ở hot path có thể đắt hơn nhiều so với dispatch trung gian.

**“Code ít lặp đi nên thiết kế sẽ tốt.”** Chỉ tốt nếu chính sách thật sự đồng nhất, phạm vi được kiểm soát và người bảo trì có thể truy vết. Ẩn quy tắc nghiệp vụ trong advice làm giảm tính rõ ràng.

**“Test target trực tiếp là đủ.”** Unit test target không chứng minh weaving, matching, precedence và propagation. Cần integration test qua cơ chế chạy thật, cộng negative test xác nhận các điểm không được chọn.

## 11. Tự đánh giá cuối lộ trình

Tự trả lời trước khi xem gợi ý:

1. Một chương trình có 20 phương thức cùng ghi latency. Đó là cơ hội AOP trong điều kiện nào?
2. Một pointcut chọn theo marker. Bạn chứng minh thế nào rằng một phương thức không có marker **không** bị advice tác động?
3. Vì sao `target.foo()` và `proxy.foo()` có thể cho hành vi advice khác nhau?
4. Nếu `around` vô tình gọi phần nghiệp vụ hai lần, các thao tác nào đặc biệt nguy hiểm?
5. Phương thức trả future ngay, nhưng tác vụ chạy thêm năm giây. “After returning” đã đo được toàn bộ thời gian tác vụ chưa?
6. Khi thêm AOP vào kiểm tra quyền, làm cách nào tránh sai lầm cho rằng mọi đường truy cập đã được bảo vệ?
7. Hai advice có thứ tự A ngoài B hoặc B ngoài A. Vì sao kết quả audit/latency có thể khác?

**Gợi ý đối chiếu:**

1. Khi cùng một chính sách quan sát có ý nghĩa nhất quán, tập điểm chọn rõ, có kiểm thử và overhead chấp nhận được.
2. Đặt negative test gọi đúng điểm đó qua cơ chế AOP thật, xác nhận không có side effect; kiểm tra cả marker được tìm ở interface hay implementation.
3. Proxy chỉ can thiệp lời gọi đi qua nó; target trực tiếp không nhất thiết được chặn.
4. Thanh toán, chuyển tiền, ghi dữ liệu không idempotent, gửi sự kiện/thông báo có tác dụng phụ.
5. Chưa, nếu advice chỉ bao quanh lời gọi tạo future; cần instrumentation tại thời điểm future hoàn tất khi mục tiêu là đo toàn bộ tác vụ.
6. Mô hình hóa kiểm tra quyền tại biên tin cậy và bất biến nghiệp vụ; test các tuyến truy cập, không coi pointcut là tuyến bảo vệ duy nhất.
7. Advice bên ngoài quan sát một phạm vi thời gian và trạng thái khác advice bên trong; định nghĩa “thành công” cũng có thể khác trước/sau commit hoặc retry.

## 12. Cách đi tiếp sau bài học

Nếu **chưa phân biệt được join point/pointcut/advice**, quay lại chặng 2 và viết ba ví dụ của riêng mình. Nếu **không dự đoán đúng kết quả Java**, quay lại chặng 4–5 để kiểm tra proxy và lời gọi thực tế. Nếu **chạy demo đúng nhưng chưa biết thiết kế hệ thống**, học tiếp chặng 6–7: exception, thứ tự advice, context bất đồng bộ, phạm vi và benchmark.

Một người đã nắm vững AOP có thể giải thích được cả **nơi advice sẽ chạy, nơi nó không chạy và vì sao**. Đó là bước chuyển quan trọng từ ghi nhớ thuật ngữ sang hiểu cơ chế và ứng dụng có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)
