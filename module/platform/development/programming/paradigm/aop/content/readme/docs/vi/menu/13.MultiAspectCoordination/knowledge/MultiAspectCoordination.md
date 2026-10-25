<a id="back-to-top"></a>

# Phối hợp nhiều aspect

## Menu
- [Một join point đồng thời phù hợp với nhiều aspect](#multi-aspect-coordination-001)
- [Thứ tự ưu tiên và quy tắc phân xử khi có xung đột](#multi-aspect-coordination-002)
- [Chuỗi advice bao quanh và thứ tự tháo lớp khi trả về](#multi-aspect-coordination-003)
- [Quan hệ thứ tự giữa advice trước, sau và khi có lỗi](#multi-aspect-coordination-004)
- [Phụ thuộc ngữ nghĩa giữa bảo mật, giao dịch và ghi nhật ký](#multi-aspect-coordination-005)
- [Thiết kế thứ tự tường minh thay vì dựa vào chi tiết triển khai](#multi-aspect-coordination-006)
- [Ngăn quan hệ phụ thuộc vòng giữa các aspect](#multi-aspect-coordination-007)
- [Tách aspect độc lập khỏi aspect phải phối hợp](#multi-aspect-coordination-008)
- [Kiểm thử hành vi tổng thể thay vì chỉ từng advice](#multi-aspect-coordination-009)

## <a id="multi-aspect-coordination-001">Một join point đồng thời phù hợp với nhiều aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Một join point đồng thời phù hợp với nhiều aspect trong AOP

> **Mục tiêu:** Hiểu hiện tượng nhiều aspect đồng thời chọn **cùng một join point** trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP). Nắm được cơ chế pointcut overlap, advice composition, thứ tự ưu tiên, chuỗi around/proceed, short-circuit, retry, return/exception và tác động phụ khi nhiều chính sách cùng quan sát hoặc điều chỉnh một thao tác. Biết thiết kế ca kiểm thử chứng minh chính xác số lần advice và phần gốc thực thi, đồng thời không giả định mọi công cụ AOP có cùng ngữ nghĩa mặc định.

## 1. Tình huống: một thao tác đồng thời cần ba chính sách

Một phương thức nghiệp vụ nhận yêu cầu thanh toán:

~~~text
PaymentService.pay(orderId, amount)
~~~

Có ba mối quan tâm xuyên suốt:

- **Tracing:** ghi nhận điểm bắt đầu/kết thúc để theo dõi yêu cầu.
- **Validation:** kiểm tra một điều kiện đầu vào khi thao tác được chọn.
- **Audit:** phát sự kiện quan sát ở những thời điểm quan trọng.

Nếu pointcut của ba aspect đều khớp **method execution join point** của pay, cả ba có thể đóng góp advice tại điểm đó. Điều này không có nghĩa ba advice tự động chạy song song; cách chúng được kết hợp còn phụ thuộc mô hình AOP và precedence.

~~~text
                  pay(orderId, amount)
                           |
               +-----------+-----------+
               |           |           |
             Trace       Validate     Audit
               |           |           |
               +-----------+-----------+
                           |
                        Business
~~~

Sơ đồ chỉ thể hiện **tập chính sách cùng quan tâm một điểm**, chưa thể hiện thứ tự hoặc quyền điều khiển.

Câu hỏi đúng cần đặt ra là: **Những advice nào thực sự được chạy, theo trình tự nào, mỗi advice quan sát ranh giới nào, và nếu một advice không gọi tiếp thì điều gì xảy ra?**

## 2. Những thành phần AOP phải phân biệt

| Thuật ngữ | Ý nghĩa đối với bài này |
|---|---|
| **Join point** | Điểm hoặc sự kiện thuộc mô hình thực thi có thể can thiệp |
| **Pointcut** | Điều kiện chọn tập join point |
| **Advice** | Hành vi bổ sung được áp dụng tại điểm đã chọn |
| **Aspect** | Đơn vị tổ chức một mối quan tâm xuyên suốt, có thể chứa nhiều advice |
| **Weaving / interception** | Cơ chế kết hợp hành vi aspect vào đường thực thi |
| **Precedence / ordering** | Quy tắc quyết định cách sắp xếp advice khi nhiều lời khai cùng khớp |

Một aspect có thể chứa nhiều advice; vì vậy **ba aspect khớp không nhất thiết bằng ba lần advice invocation**. Một pointcut có thể khớp nhưng advice bên trong lại không chạy vì lớp bao ngoài đã short-circuit.

Tương tự, **một method name không phải danh tính duy nhất của join point**. Cần biết loại điểm (call/execution...), chữ ký, vị trí và lần xuất hiện động cụ thể.

## 3. Tập giao của nhiều pointcut

Đặt:

- **P_A:** tập các join point mà pointcut của Aspect A chọn.
- **P_B:** tập các join point mà pointcut của Aspect B chọn.
- **J:** một join point cụ thể được quan sát.

Khi J thuộc P_A và đồng thời thuộc P_B, J nằm trong **giao của P_A và P_B**.

~~~text
J ∈ P_A
J ∈ P_B
────────────
J ∈ P_A ∩ P_B
~~~

Không cần P_A và P_B hoàn toàn giống nhau. Ví dụ:

~~~text
Aspect A: mọi method execution của PaymentService
Aspect B: chỉ method execution có tên pay trong PaymentService
~~~

Một execution của pay có thể được cả hai chọn, còn preview có thể chỉ được A chọn.

### 3.1. Overlap không mặc nhiên là lỗi

Một operation được tracing, đo hiệu năng và audit cùng lúc là yêu cầu thông thường. Điều cần kiểm soát không phải xóa mọi vùng giao, mà là **nghĩa của mỗi advice** và cách phối hợp của chúng.

Overlap trở thành rủi ro khi hai aspect cùng phát một sự kiện audit không phân biệt, cùng biến đổi đối số trái ngược nhau, cùng retry, hoặc cùng sở hữu một resource mà không thống nhất ownership.

## 4. Matching tĩnh và điều kiện động

Pointcut có thể dùng điều kiện tĩnh trên tên phương thức, kiểu khai báo hoặc chữ ký. Một số mô hình còn cho phép điều kiện phụ thuộc đối số, target hoặc ngữ cảnh runtime.

Ví dụ:

~~~text
A: chọn mọi lần execution pay(...)
B: chọn execution pay(...) khi amount > 100
~~~

Với amount=10, A có thể được chọn nhưng B không. Với amount=150, cả hai có thể phù hợp.

Cần phân biệt:

1. **Điều kiện cấu trúc** cho biết điểm có thể được chọn.
2. **Điều kiện động** quyết định một lần xuất hiện cụ thể có thỏa quy tắc hay không.
3. **Dòng điều khiển thực** cho biết advice có được chạy tới hay không.

Nếu around advice phía ngoài không gọi continuation, advice phía trong có thể không được vào dù pointcut của nó phù hợp.

## 5. Nhiều aspect không tự động là nhiều thread

Một nhầm lẫn phổ biến là nghĩ các aspect khớp cùng một join point sẽ chạy đồng thời trên các luồng khác nhau.

Trong một chuỗi around đồng bộ, cách hình dung thường hữu ích là **các lớp bao lồng nhau**:

~~~text
A enters
    B enters
        Original executes
    B exits
A exits
~~~

Đây là một chuỗi điều khiển có thứ tự. Một advice có thể chủ động tạo tác vụ bất đồng bộ, nhưng đó là hành vi riêng của advice và có hợp đồng completion/context khác, không phải hệ quả tất yếu của overlap.

### 5.1. Cùng khớp không phải cùng thời điểm

Một before advice quan sát khi vào; một after-returning advice chỉ có dữ liệu hoàn tất bình thường; một after-throwing advice quan sát lỗi đi qua ranh giới tương ứng. Cùng pointcut không có nghĩa mọi advice phát log vào cùng một lúc.

## 6. Thứ tự ưu tiên là phần của ngữ nghĩa

Nếu hai aspect cùng có around advice, thứ tự bao ngoài và bên trong có thể ảnh hưởng kết quả.

~~~text
Caller
  |
  A.before
    |
    B.before
      |
      Original
      |
    B.after
    |
  A.after
  |
Caller
~~~

Với lớp bao đồng bộ đi hết đường normal return, thứ tự vào là **A rồi B**, nhưng thứ tự ra là **B rồi A**.

Không có quy tắc chung cho mọi công cụ AOP rằng aspect được khai báo đầu file, đặt tên alphabet trước hoặc được biên dịch trước sẽ luôn bao ngoài. Cần xác minh **precedence semantics thực tế**.

### 6.1. Những câu hỏi về precedence

- Aspect nào phải ghi nhận yêu cầu **kể cả bị chặn**?
- Aspect nào chỉ cần quan sát **lần phần gốc thực sự chạy**?
- Chính sách nào được phép sửa dữ liệu hoặc trả fallback?
- Exception do phần gốc ném có thể bị advice khác chuyển đổi không?
- Retry nên ở **trong hay ngoài** một transaction boundary?
- Metrics cần đo cả cache hit hay chỉ execution thật?

Nếu trả lời chưa rõ, đổi thứ tự cho tới khi log trông hợp lý không phải cách thiết kế vững chắc.

## 7. Những loại advice khi nhiều aspect cùng khớp

| Loại advice | Ý nghĩa điển hình | Điều dễ nhầm |
|---|---|---|
| **Before** | Xử lý trước phần được chọn | Một around ngoài có thể chặn trước |
| **After-returning** | Quan sát hoàn tất bình thường | Không nhất thiết là thành công nghiệp vụ |
| **After-throwing** | Quan sát lỗi đi ra khỏi ranh giới | Lỗi có thể đã được aspect trong biến đổi |
| **After/finally** | Xử lý kết thúc theo hợp đồng | Không phải mọi lối thoát đều có return value |
| **Around** | Bao quanh và điều khiển phần tiếp theo | Proceed không nhất thiết gọi thẳng phần gốc |

Mỗi công cụ có thể quy định thứ tự trong và giữa các nhóm advice khác nhau. Bảng là **mô hình học tập**, không phải danh sách cú pháp bắt buộc.

## 8. Around advice và chuỗi continuation

Với ba around advice A, B và C, một thứ tự có thể là:

~~~text
A.around
   |
   +-- proceed → B.around
                     |
                     +-- proceed → C.around
                                       |
                                       +-- proceed → Original
~~~

**Proceed** (hoặc cơ chế tiếp tục tương đương) biểu diễn việc chuyển quyền điều khiển tới **phần còn lại của chuỗi**, có thể gồm nhiều advice khác trước khi tới phần gốc.

Hệ quả:

- A gọi proceed một lần chưa chứng minh Original chạy một lần.
- B có thể không tiếp tục, khiến C và Original không chạy.
- B có thể thử lại phần trong, khiến C và Original chạy nhiều lần khi công cụ cho phép.
- C có thể biến đổi kết quả hoặc lỗi trước khi B/A quan sát.

Cần phân biệt **số lần advice được vào**, **số lần continuation được gọi** và **số lần Original thực thi**.

## 9. Short-circuit: aspect trong có thể không chạy

Một around advice có thể từ chối yêu cầu trước khi gọi tiếp:

~~~text
Guard:
    nếu amount < 0:
        từ chối, không gọi tiếp
    ngược lại:
        proceed
~~~

Giả sử chuỗi:

~~~text
Trace → Guard → Audit → Original
~~~

Nếu Guard chặn, Trace và Guard đã được vào, nhưng Audit và Original **không chạy**. Audit không xuất hiện không phải bằng chứng điểm không thuộc pointcut của Audit.

### 9.1. Tác động tới kiểm toán

Nếu Audit phải ghi **tất cả yêu cầu, kể cả từ chối**, đặt Audit ở phía trong một Guard short-circuit có thể bỏ sót các sự kiện từ chối.

Nếu Audit chỉ ghi **những lần thực thi phần gốc**, vị trí phía trong Guard có thể đúng.

Đừng gọi một thứ tự là “tốt hơn” khi chưa xác định mục tiêu audit cần theo dõi **request**, **approved attempt** hay **business execution**.

## 10. Các aspect cùng biến đổi kết quả

Hai phép biến đổi theo thứ tự khác nhau có thể cho kết quả khác nhau.

~~~text
Original returns 5

AddOne(Double(5)) = 11
Double(AddOne(5)) = 12
~~~

Trong thực tế, A có thể chuẩn hóa result còn B lọc trường nhạy cảm. Nếu thay thứ tự, B có thể nhận dữ liệu trước hoặc sau khi A biến đổi.

Một policy chỉ nhằm quan sát thường nên **bảo toàn return value**; nếu được phép biến đổi phải khai báo rõ:

- Kiểu trả về hợp lệ.
- Trách nhiệm sở hữu phép biến đổi.
- Thứ tự với các aspect khác.
- Điều kiện khi không gọi proceed.
- Các tác dụng phụ và lời hứa với caller.

Không được suy rằng advice của các aspect độc lập sẽ **giao hoán** chỉ vì chúng được đặt trong các class khác nhau.

## 11. Phân biệt lỗi phần gốc với lỗi advice

Giả sử Original ném E. Inner advice có thể:

- Ghi nhận E rồi ném lại E.
- Bắt E rồi thay bằng E2.
- Bắt E rồi trả fallback bình thường.
- Tự ném lỗi mới trong cleanup.

Outer advice nhìn thấy **điều mà inner truyền ra**, không bắt buộc còn là E gốc.

~~~text
Outer A
    Inner B
        Original throws E
    B catches E and returns fallback
A sees normal return
~~~

Một after-throwing advice ở ngoài **không có lý do phải thấy E** nếu E đã được xử lý trong phần bên trong và không thoát ra ranh giới của nó.

### 11.1. Lỗi logger che lỗi gốc

Nếu Original ném E rồi advice finally gọi logger và logger ném F, caller có thể thấy F thay vì E. Aspect quan sát cần chính sách lỗi tường minh. Với logging best-effort thường muốn tránh phá nghiệp vụ; với audit bắt buộc có thể cần chính sách khác, không đơn giản bỏ qua mọi lỗi.

## 12. Ví dụ Java 17 số 1: ba aspect cùng trên một operation

Ví dụ dưới đây mô phỏng ba around aspect: **Trace**, **Guard**, **Audit**. Người viết tự lắp chain với thứ tự rõ ràng, không sử dụng một weaver hoặc pointcut parser. Nó giúp quan sát chính xác việc nhiều advice cùng quan tâm một operation được kết hợp ra sao.

**Đây là Java thuần minh họa AOP composition, không phải cú pháp một công cụ AOP cụ thể.**

Lưu **MultipleAspectsDemo.java**:

~~~java
import java.util.List;

public class MultipleAspectsDemo {
    @FunctionalInterface
    interface Next {
        String proceed();
    }

    interface Around {
        String invoke(String operation, int amount, Next next);
    }

    static final class TraceAspect implements Around {
        @Override
        public String invoke(String operation, int amount, Next next) {
            System.out.println("[trace] enter " + operation);
            try {
                String result = next.proceed();
                System.out.println("[trace] return " + result);
                return result;
            } catch (RuntimeException error) {
                System.out.println("[trace] throw "
                        + error.getClass().getSimpleName());
                throw error;
            }
        }
    }

    static final class GuardAspect implements Around {
        @Override
        public String invoke(String operation, int amount, Next next) {
            if (amount < 0) {
                System.out.println("[guard] blocked");
                throw new IllegalArgumentException("negative amount");
            }
            System.out.println("[guard] passed");
            return next.proceed();
        }
    }

    static final class AuditAspect implements Around {
        @Override
        public String invoke(String operation, int amount, Next next) {
            System.out.println("[audit] before");
            try {
                String result = next.proceed();
                System.out.println("[audit] success");
                return result;
            } catch (RuntimeException error) {
                System.out.println("[audit] failure "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println("[audit] finally");
            }
        }
    }

    static String original(String operation, int amount) {
        System.out.println("[business] " + operation + " " + amount);
        if (amount == 0) {
            throw new IllegalStateException("zero amount");
        }
        return operation + ":" + amount;
    }

    static String runChain(
            List<Around> chain, int index,
            String operation, int amount) {
        if (index == chain.size()) {
            return original(operation, amount);
        }
        Around current = chain.get(index);
        return current.invoke(
                operation, amount,
                () -> runChain(chain, index + 1, operation, amount));
    }

    static void runCase(
            List<Around> chain, String operation, int amount) {
        System.out.println("== amount " + amount + " ==");
        try {
            String result = runChain(chain, 0, operation, amount);
            System.out.println("[caller] result " + result);
        } catch (RuntimeException error) {
            System.out.println("[caller] caught "
                    + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        List<Around> chain = List.of(
                new TraceAspect(),
                new GuardAspect(),
                new AuditAspect());

        runCase(chain, "pay", 2);
        runCase(chain, "pay", -1);
        runCase(chain, "pay", 0);
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac MultipleAspectsDemo.java
java MultipleAspectsDemo
~~~

**Stdout chính xác:**

~~~text
== amount 2 ==
[trace] enter pay
[guard] passed
[audit] before
[business] pay 2
[audit] success
[audit] finally
[trace] return pay:2
[caller] result pay:2
== amount -1 ==
[trace] enter pay
[guard] blocked
[trace] throw IllegalArgumentException
[caller] caught IllegalArgumentException: negative amount
== amount 0 ==
[trace] enter pay
[guard] passed
[audit] before
[business] pay 0
[audit] failure IllegalStateException
[audit] finally
[trace] throw IllegalStateException
[caller] caught IllegalStateException: zero amount
~~~

### 12.1. Đường thành công

Với amount=2, thứ tự là Trace enter → Guard passed → Audit before → Original → Audit success/finally → Trace return → Caller.

Trace **vào trước**, nhưng Trace **ra sau** Audit do nó bao ngoài chuỗi.

### 12.2. Đường bị Guard chặn

Với amount=-1, Guard ném IllegalArgumentException trước khi gọi next. Không có dòng Audit và không có dòng Business. Trace vẫn ghi lỗi vì Trace là lớp ngoài Guard.

Nếu hoán đổi Guard thành lớp ngoài Trace, ở nhánh từ chối có thể không có dòng Trace. Đây là một khác biệt **ngữ nghĩa quan sát**, không chỉ khác thứ tự trang trí.

### 12.3. Đường Original ném lỗi

Với amount=0, Original được gọi rồi ném IllegalStateException. Audit ghi failure và finally, sau đó Trace nhìn thấy cùng lỗi được truyền ra và Caller bắt được. Hai aspect cùng quan sát lỗi ở ranh giới của mình.

### 12.4. Vì sao đây chưa phải một engine AOP?

Hàm runChain được gọi thủ công với danh sách đã sắp xếp. Không có weaving vào mã method, không có ngôn ngữ pointcut và không có engine chọn join point. Chương trình chỉ minh họa **continuation composition** để người học dễ thấy hiệu ứng lồng nhau.

Trong hệ AOP thật, phải kiểm tra cách engine chọn advice và precedence theo đặc tả, thay vì cho rằng cách lắp List này là quy tắc chung.

## 13. Thay order làm thay số sự kiện

So sánh hai chuỗi:

~~~text
Chuỗi X: Trace → Guard → Audit → Original
Chuỗi Y: Guard → Trace → Audit → Original
~~~

Khi Guard chặn:

- Ở X, Trace đã được vào và có thể ghi một sự kiện từ chối.
- Ở Y, Trace chưa được vào nên không có sự kiện đó.

Điều này quyết định **sự kiện được cam kết ghi**. Khi cần observability hoặc audit của mọi yêu cầu, phải bố trí ranh giới quan sát bao được cả nhánh bị từ chối.

Khi chỉ cần đo chi phí của những lượt phần gốc đã chạy, đặt metric phía trong một policy chặn sớm có thể có ý nghĩa hơn.

## 14. Retry có thể khiến aspect trong chạy nhiều lần

Một around advice có thể được phép gọi continuation nhiều lần. Nếu A nằm ngoài Retry và B nằm trong Retry:

~~~text
A
  Retry
     → B → Original lần 1: lỗi
     → B → Original lần 2: thành công
~~~

A có thể chạy một lần, trong khi B và Original chạy hai lần.

Đây là lý do quan trọng nhất để phân biệt:

- **Một lời gọi từ caller**.
- **Một lần advice của lớp ngoài**.
- **Một lần Retry advice**.
- **Một lần attempt phía trong**.
- **Một lần Original thực sự thực thi**.

Những số này không nhất thiết bằng nhau.

### 14.1. Retry không tự an toàn

Nếu attempt 1 đã gửi một lệnh chuyển tiền rồi nhận lỗi mạng, attempt 2 có thể gửi lệnh lần nữa. Một policy retry cần giới hạn loại lỗi được retry, khả năng idempotent, transaction semantics và số lần tối đa.

Một around advice retry có thể làm lặp audit/log/metrics ở **bên trong**. Điều này có thể đúng nếu mỗi attempt phải có event riêng, nhưng sai nếu một event chỉ được phát mỗi yêu cầu ở bên ngoài.

## 15. Ví dụ Java 17 số 2: Retry ở giữa hai aspect khác

Chương trình này minh họa **Trace bên ngoài**, **RetryOnce ở giữa**, **Observer bên trong**. Original cố ý lỗi ở attempt đầu và thành công ở attempt thứ hai.

Lưu **NestedRetryAspectsDemo.java**:

~~~java
import java.util.List;

public class NestedRetryAspectsDemo {
    @FunctionalInterface
    interface Next {
        String proceed();
    }

    interface Around {
        String invoke(Next next);
    }

    static final class Trace implements Around {
        @Override
        public String invoke(Next next) {
            System.out.println("[trace] enter");
            String result = next.proceed();
            System.out.println("[trace] return " + result);
            return result;
        }
    }

    static final class RetryOnce implements Around {
        @Override
        public String invoke(Next next) {
            System.out.println("[retry] attempt 1");
            try {
                return next.proceed();
            } catch (IllegalStateException failure) {
                System.out.println("[retry] caught "
                        + failure.getClass().getSimpleName());
            }
            System.out.println("[retry] attempt 2");
            return next.proceed();
        }
    }

    static final class Observer implements Around {
        @Override
        public String invoke(Next next) {
            System.out.println("[observer] enter");
            try {
                String result = next.proceed();
                System.out.println("[observer] return " + result);
                return result;
            } catch (RuntimeException failure) {
                System.out.println("[observer] throw "
                        + failure.getClass().getSimpleName());
                throw failure;
            } finally {
                System.out.println("[observer] finally");
            }
        }
    }

    static final class Business {
        private int calls;

        String execute() {
            int n = ++calls;
            System.out.println("[business] attempt " + n);
            if (n == 1) {
                throw new IllegalStateException("temporary");
            }
            return "ok-" + n;
        }

        int calls() {
            return calls;
        }
    }

    static String run(List<Around> chain, int index, Next original) {
        if (index == chain.size()) {
            return original.proceed();
        }
        return chain.get(index).invoke(
                () -> run(chain, index + 1, original));
    }

    public static void main(String[] args) {
        Business business = new Business();
        List<Around> chain = List.of(
                new Trace(),
                new RetryOnce(),
                new Observer());

        String result = run(chain, 0, business::execute);
        System.out.println("[caller] " + result);
        System.out.println("[count] original=" + business.calls());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NestedRetryAspectsDemo.java
java NestedRetryAspectsDemo
~~~

**Stdout chính xác:**

~~~text
[trace] enter
[retry] attempt 1
[observer] enter
[business] attempt 1
[observer] throw IllegalStateException
[observer] finally
[retry] caught IllegalStateException
[retry] attempt 2
[observer] enter
[business] attempt 2
[observer] return ok-2
[observer] finally
[trace] return ok-2
[caller] ok-2
[count] original=2
~~~

### 15.1. Số lần của từng thành phần

| Thành phần | Số lần được vào |
|---|---:|
| Trace | 1 |
| RetryOnce | 1 |
| Observer | 2 |
| Original | 2 |

RetryOnce vào một lần, nhưng gọi continuation trong **hai attempts**. Observer nằm bên trong continuation nên được vào hai lần.

### 15.2. Đổi vị trí Retry làm thay đổi kết quả quan sát

Nếu Retry được đưa **ra ngoài Trace**, việc retry có thể khiến Trace được vào hai lần. Khi Trace ở ngoài Retry, nó ghi một entry cho cả request.

Sự khác biệt này phải phản ánh đúng **đơn vị quan sát**: một request hay một attempt.

### 15.3. Không đồng nhất continuation với gọi Original trực tiếp

Trong chương trình, RetryOnce gọi next, không tự gọi business.execute. Next chuyển tới Observer rồi mới tới Original. Đó là điều cốt lõi khi nhiều quanh advice cùng chọn điểm: các lớp trung gian có thể được chạy lại.

## 16. Before của hai aspect có thứ tự nào?

Nếu A-before và B-before cùng khớp J, cả hai có thể chạy trước phần gốc khi phần điều khiển thật sự tới chúng. Nhưng thứ tự A rồi B hay B rồi A phụ thuộc **quy tắc của engine**.

Không nên viết B-before giả định A-before luôn đã đặt correlation ID, trừ khi công cụ có quy tắc precedence rõ và kiểm thử bảo đảm quan hệ đó.

Nếu các aspect phụ thuộc chặt nhau, hãy cân nhắc một context owner duy nhất và cung cấp dữ liệu theo interface rõ, tránh để những aspect độc lập tranh quyền khởi tạo.

## 17. After của hai aspect: cần quan sát đúng exit

Với chuỗi quanh advice lồng nhau:

~~~text
A before
  B before
    Original
  B after
A after
~~~

Thứ tự after có thể **ngược** thứ tự before. Nhưng các công cụ khác nhau có semantics riêng cho after-returning, after-throwing, finally và thứ tự advice trong cùng aspect.

Khi thử nghiệm, nên xác minh:

- Advice nào thực sự bao ngoài?
- Ai nhìn thấy return value trước khi bị biến đổi?
- Exception nào đi qua ranh giới của ai?
- Cleanup lỗi có thay lỗi gốc không?
- Có advice nào chỉ chạy ở normal return nhưng test lại dùng exceptional exit?

Không kết luận thứ tự exit chỉ từ tên loại advice.

## 18. Normal return không đồng nghĩa nghiệp vụ thành công

Một method có thể trả kết quả bình thường biểu thị DECLINED hoặc INVALID. Một after-returning advice có thể ghi “returned normally”, trong khi policy nghiệp vụ phân loại trạng thái là “failed”.

Hai mô tả không mâu thuẫn nếu ranh giới của chúng khác nhau.

Tương tự, một method trả về Future/CompletionStage có thể đã hoàn tất **lời gọi Java** trong khi công việc bất đồng bộ còn chạy. Nếu tracing muốn đo end-to-end task, phải quan sát completion event, không chỉ return của method.

Một aspect không nên tự suy thành công nghiệp vụ chỉ từ tên method hoặc việc không thấy exception.

## 19. Một method có thể liên quan nhiều join point

Một số mô hình AOP phân biệt:

- **Method call join point:** hoạt động ở call site bên caller.
- **Method execution join point:** hoạt động khi phần thân method được thực thi.

Hai aspect in cùng method name chưa chắc cùng khớp đúng một join point. Một aspect có thể chọn call site, aspect kia chọn execution.

~~~text
Caller call site ----> Target method execution
     Aspect A                    Aspect B
~~~

Đây là **hai loại điểm** liên quan cùng operation, không mặc định là một điểm duy nhất.

Để phân tích overlap phải xác định loại join point, chữ ký đầy đủ, declaring type, thời điểm và ngữ cảnh runtime phù hợp, không chỉ dòng log có tên pay.

## 20. Một aspect có thể có nhiều advice khớp cùng J

Giả sử Aspect A có:

- Before advice trên pay.
- After-returning advice trên pay.
- Around advice trên pay.

Aspect B lại có một around advice trên pay. Tổng cộng có **bốn khai báo advice** liên quan, không phải chỉ hai do có hai aspect.

Các câu hỏi cần kiểm tra:

1. Quy tắc giữa advice của **cùng aspect** là gì?
2. Quy tắc precedence **giữa các aspect** là gì?
3. Có advice nào không chạy ở exceptional exit?
4. Có short-circuit hoặc multiple proceed?
5. Có policy chỉ chọn một số lần gọi do dynamic condition không?

Không xây sơ đồ chỉ có A rồi B nếu trong A còn nhiều advice cần được sắp xếp.

## 21. Hai aspect cùng thay đổi đối số

Nếu Aspect A chuẩn hóa số liệu và Aspect B xác nhận chữ ký của dữ liệu đầu vào, thứ tự có thể ảnh hưởng kết quả.

~~~text
Original input
    |
    A normalizes
    |
    B validates transformed input
    |
    Original
~~~

Khác với B kiểm tra **input gốc** rồi A mới chuẩn hóa.

Nếu một engine cho phép truyền đối số mới xuống continuation, cần quy định rõ:

- Aspect nào nhìn thấy raw input?
- Aspect nào nhìn thấy transformed input?
- Validation diễn ra trước hay sau biến đổi?
- Ai chịu trách nhiệm bảo toàn hợp đồng kiểu?
- Có dữ liệu nhạy cảm nào không nên chuyển xuống một policy quan sát?

Không phải mọi engine đều cho phép thay tham số theo cùng cách; cần tuân thủ API và join point model tương ứng.

## 22. Cùng ghi log không nhất thiết là sự kiện bị lặp

Nếu tracing ghi ENTER còn audit ghi APPROVED, đó là hai sự kiện khác semantics. Nếu hai aspect cùng ghi **một** sự kiện AUDIT_COMPLETED không phân biệt, người sử dụng có thể đếm trùng.

Các nguyên nhân của “hai log cho một thao tác” có thể gồm:

- Hai aspect cùng ghi một loại event.
- Hai advice trong một aspect.
- Hai lớp proxy lồng nhau.
- Một retry ở ngoài logging.
- Một lời gọi business lồng nhau hoặc recursive.
- Một logger/sink phát hai bản ghi.

Không thể chỉ nhìn số log để kết luận Original chạy hai lần.

### 22.1. Định nghĩa event ownership

Một cách phối hợp tốt là quy định **một owner chịu trách nhiệm phát từng loại event**. Các aspect khác có thể cung cấp metadata hoặc phát event khác loại. Khi cần chống trùng ở mức request, phải có một correlation key và hợp đồng uniqueness phù hợp, không dựa trên method name ngắn.

## 23. Shared context giữa các aspect

Nhiều aspect có thể cần chung correlation ID, operation descriptor hoặc một snapshot ngữ cảnh. Tuy nhiên dùng **biến static mutable currentRequest** tạo nguy cơ trộn dữ liệu giữa nhiều thread.

Nên xác định:

- State nào là **per-invocation**.
- State nào là **per-object** nếu công cụ hỗ trợ.
- State nào là **per-control-flow**.
- State nào là **singleton**.
- Context nào cần lan truyền qua async boundary.

Với lời gọi lồng nhau, một ThreadLocal đơn giản phải có giao thức **lưu và phục hồi** trong finally, tránh làm mất context của lời ngoài. Với async, ThreadLocal không tự đảm bảo lan truyền sang thread khác.

Không đưa toàn bộ raw join point hoặc mảng đối số mutable vào nhiều aspect khi chỉ cần các trường đã chuẩn hóa và che dữ liệu nhạy cảm.

## 24. Transaction, resource và thứ tự policy

Hai around aspect cùng kiểm soát transaction/resource có thể tạo cấu trúc:

~~~text
Transaction A starts
    Transaction B starts
        Original executes
    Transaction B ends
Transaction A ends
~~~

Sơ đồ không đủ để suy rằng có hai transaction thật hoặc rollback/commit hoạt động theo kiểu nào. Nó phụ thuộc transaction manager, propagation semantics và chính sách.

Cần làm rõ:

- Ai thực sự sở hữu transaction boundary?
- Retry nằm trong hay ngoài transaction?
- Có aspect nào chỉ tham gia transaction đã có?
- Nếu inner lỗi và outer catch, trạng thái transaction có còn hợp lệ?
- Cleanup của mỗi resource phải chạy khi nào?

Không mặc định thứ tự around nesting tương đương atomicity hoặc isolation.

## 25. Tính giao hoán: đổi thứ tự có làm thay kết quả không?

Hai policy độc lập **có thể giao hoán** trong những điều kiện hẹp, ví dụ cả hai chỉ tăng những counter độc lập, không ném lỗi, không short-circuit và không thay return.

Nhưng một chính sách retry, cache, transform hoặc exception conversion thường có thể làm thứ tự trở nên quan trọng.

Phân tích:

~~~text
A(B(J))  versus  B(A(J))
~~~

So sánh về:

- Return value và ngoại lệ caller thấy.
- Số lần Original thật sự chạy.
- Tác dụng phụ.
- Audit/metrics/tracing events.
- Resource/transaction scope.
- Dữ liệu mà mỗi policy nhìn thấy.
- Chi phí thực thi.

Nếu hai thứ tự khác nhau theo yêu cầu quan trọng, cần **khai báo và kiểm thử thứ tự** hoặc tái thiết kế để loại bỏ phụ thuộc.

## 26. Rủi ro recursion khi các aspect gọi chéo nhau

Một audit advice gọi AuditSink.write, nhưng pointcut logging lại chọn cả AuditSink.write. Có thể sinh vòng:

~~~text
Audit advice
   → AuditSink.write
       → Audit advice
           → AuditSink.write
               → ...
~~~

Một aspect khác có thể khiến vòng lặp phức tạp hơn, ví dụ metrics gọi logger còn logger được tracing advice bao lại.

Biện pháp:

- Loại trừ mã hạ tầng của aspect khỏi pointcut khi phù hợp.
- Tránh gọi lại chính lời gọi đang bị chặn thay cho continuation.
- Nếu dùng guard reentrancy, định nghĩa scope rõ và cleanup trong finally.
- Không dùng một boolean global cho mọi thread.
- Kiểm thử cả đường ném ngoại lệ và lời gọi lồng nhau.

Một vòng recursion không nhất thiết là lỗi weaving; nó có thể là hiệu ứng **đúng theo pointcut nhưng sai theo thiết kế nghiệp vụ quan sát**.

## 27. Lỗi advice có thể phá nghiệp vụ gốc

Một aspect dùng để logging nhưng tự ném exception khi định dạng đối số. Nếu advice chạy trước Original, nghiệp vụ có thể **không được gọi**. Nếu logger ném trong finally, lỗi gốc có thể bị che.

Do đó cần định nghĩa chính sách:

- Logging/metrics **best-effort** có được phép thất bại ảnh hưởng caller không?
- Audit bắt buộc nên fail-open hay fail-closed trong điều kiện nào?
- Ai sở hữu quyết định **không gọi proceed**?
- Có được phép convert exception không?
- Có cần ghi nhận thất bại của sink qua kênh riêng không?

Một bộ catch-all bỏ mọi lỗi không thích hợp cho mọi loại aspect, nhất là chính sách kiểm soát bắt buộc. Nhưng một observer thuần túy cũng không nên ngẫu nhiên biến operation thành thất bại chỉ vì logger thiếu tài nguyên.

## 28. Hiệu năng của nhiều aspect cùng khớp

Mỗi advice có thể thêm:

1. Kiểm tra pointcut động.
2. Dispatch qua proxy/bytecode hook.
3. Đọc signature hoặc runtime args.
4. Tạo context snapshot.
5. Logging hoặc metrics I/O.
6. Lock hoặc synchronized state.
7. Allocation và GC.
8. Retry hoặc recursive invocation ngoài ý muốn.

Chi phí tổng **không đơn giản luôn bằng tổng chi phí riêng lẻ**. Các aspect có thể tranh lock, nhân số attempts, lặp serialization hoặc tăng áp lực GC.

Để đánh giá đúng, đo trên **composition đầy đủ với precedence thật**, workload hợp lý và ranh giới instrumentation thực tế. Không chỉ benchmark từng aspect độc lập rồi suy tổng là chính xác trong mọi tình huống.

### 28.1. Không lấy mọi đối số để log

Khi nhiều aspect cùng truy cập raw arguments, khả năng lộ token, account ID hoặc dữ liệu cá nhân tăng. Nên có allowlist, masking, giới hạn kích thước và tránh toString trên object tùy ý.

Sở hữu metadata join point không đồng nghĩa được phép lưu vô hạn mọi dữ liệu runtime quan sát được.

## 29. Ma trận kiểm thử phối hợp nhiều aspect

| Ca | Điều cần xác minh |
|---|---|
| J khớp cả A và B | Cả hai có thể chạy khi chain không bị chặn |
| J chỉ khớp A | B không chạy |
| Điều kiện động B sai | Không nhầm với lỗi weaving |
| A bao ngoài B | Enter/exit theo đúng precedence |
| Outer Guard chặn | Inner không chạy ngoài chủ đích |
| Original trả bình thường | Return value bảo toàn nếu chỉ observer |
| Original ném lỗi | Mỗi lớp thấy lỗi theo ranh giới của nó |
| Inner bắt lỗi và trả fallback | Outer có thể thấy normal return |
| Inner chuyển exception | Outer thấy exception đã chuyển |
| Inner biến đổi kết quả | Outer nhận giá trị mới |
| Retry gọi next hai lần | Số lần inner advice và Original phù hợp |
| Audit yêu cầu một event mỗi request | Không bị nhân theo số attempts |
| Gọi trực tiếp target | Độ bao phủ đúng cơ chế proxy/weaving |
| Lời gọi lồng nhau | Context không bị ghi đè |
| Đồng thời nhiều thread | Không trộn state hoặc correlation |
| Async return | Đúng mốc hoàn tất cần đo |
| Logger trong finally bị lỗi | Chính sách bảo toàn lỗi gốc |
| Thêm aspect thứ ba | Không phá giả định về order/side effects |

Một unit test của từng advice **không thay thế** integration test toàn bộ chain. Cần cả **test dương và âm của pointcut** cùng kiểm tra số lần phần gốc thực thi.

## 30. Quy trình chẩn đoán khi thiếu hoặc thừa advice

1. Xác định **loại join point** chính xác.
2. Xác nhận operation và chữ ký thực tế.
3. Liệt kê các pointcut cùng khớp về cấu trúc.
4. Kiểm tra điều kiện động ở lần gọi cụ thể.
5. Kiểm tra phạm vi weaving hoặc proxy path.
6. Liệt kê mọi advice, không chỉ tên aspect.
7. Ghi precedence/ordering thực tế.
8. Kiểm tra short-circuit.
9. Kiểm tra multiple proceed/retry.
10. Kiểm tra exception conversion hoặc fallback.
11. Phân biệt original count với advice count.
12. Kiểm tra log sink có bị lọc, lỗi hoặc nhân bản.
13. Kiểm tra recursion và calls nội bộ.
14. Kiểm tra state dùng chung, concurrency, async.
15. Rút thành một ca tái hiện tối thiểu, sửa từng biến một.

Không sửa cùng lúc pointcut, ordering và error handling: khi nhiều biến thay đổi, rất khó xác định nguyên nhân thực.

## 31. Sai lầm thường gặp

**Sai lầm 1: Hai aspect cùng khớp thì cả hai chắc chắn chạy.** Around ngoài có thể short-circuit trước khi vào inner.

**Sai lầm 2: Thứ tự phụ thuộc tên file hay thứ tự class.** Không phải quy tắc AOP chung; phải xem precedence được engine cam kết.

**Sai lầm 3: Proceed gọi một lần thì Original chạy một lần.** Inner có thể bỏ qua hoặc retry.

**Sai lầm 4: Every after chạy theo thứ tự before.** Wrapper lồng nhau thường có exit đảo lại, còn loại after advice chịu semantics riêng.

**Sai lầm 5: Cùng tên method nghĩa là cùng join point.** Call và execution khác loại; overload và call site còn khác nhau.

**Sai lầm 6: Hai dòng audit có nghĩa hai business executions.** Có thể do hai aspect hoặc một advice phát hai event.

**Sai lầm 7: Đặt audit bên trong guard nhưng vẫn muốn ghi mọi yêu cầu từ chối.** Guard có thể chặn trước khi audit được vào.

**Sai lầm 8: Đo latency ở trong cache rồi gọi là client latency.** Ranh giới đo không bao cả cache-hit.

**Sai lầm 9: Retry luôn an toàn.** Side effect có thể xảy ra trước khi lỗi được ném; retry có thể nhân giao dịch.

**Sai lầm 10: Không thấy advice là lỗi pointcut.** Có thể do proxy bypass, class ngoài weaving, outer short-circuit hoặc log bị lọc.

**Sai lầm 11: Cho mọi aspect đọc raw args.** Tăng rủi ro quyền riêng tư và chi phí.

**Sai lầm 12: Dùng static currentRequest giữa nhiều aspect.** Lời gọi đồng thời có thể trộn dữ liệu.

**Sai lầm 13: Test mỗi aspect riêng lẻ là đủ.** Phụ thuộc thứ tự và số lần sẽ chỉ xuất hiện khi kết hợp.

**Sai lầm 14: Aspect quan sát không thể gây lỗi nghiệp vụ.** Advice/cleanup tự ném exception có thể thay hành vi caller thấy.

## 32. Checklist thiết kế trước khi thêm aspect mới

1. Join point nào được chọn và thuộc loại điểm nào?
2. Những aspect nào đã chọn điểm đó?
3. Pointcut của aspect mới có giao với những pointcut nào?
4. Có điều kiện động làm số aspect khớp thay đổi theo input không?
5. Precedence được công cụ bảo đảm hay đang dựa mặc định?
6. Những around advice nào có thể short-circuit?
7. Những advice nào có quyền gọi continuation nhiều lần?
8. Return value có bị thay đổi không?
9. Exception có bị bắt, chuyển đổi hoặc che mất không?
10. Audit đang theo **request**, **attempt** hay **execution**?
11. Metrics đo tổng thời gian ngoài hay thời gian của một phần trong?
12. Có event nào bị phát trùng bởi hai aspect?
13. Context được tạo và truyền theo phạm vi nào?
14. Có nguy cơ self-invocation, recursion hoặc logging loop?
15. Có dữ liệu nào không được lộ cho aspect khác không?
16. Có integration test cho cả chain, đường lỗi và đường thành công không?
17. Có test short-circuit và retry kiểm tra chính xác số lần Original không?
18. Có kiểm thử concurrency và async khi phù hợp không?
19. Có đo overhead với tất cả aspect đang bật không?
20. Có thể giải thích mỗi log/metric từ đúng ranh giới quan sát không?

## 33. Câu hỏi tự kiểm tra

### Câu 1 — Giao pointcut

A chọn mọi execution trên PaymentService, B chọn execution pay. Một lần execution pay có thể được cả hai chọn?

**Đáp án:** có. Nhưng nếu outer advice chặn trước, một trong các advice phía trong có thể không chạy.

### Câu 2 — Thứ tự enter/exit

A bao ngoài B theo around wrapper. Original thành công. Thứ tự thông thường?

**Đáp án:** A enter, B enter, Original, B exit, A exit.

### Câu 3 — Guard trước Audit

Guard từ chối mà không gọi continuation, Audit nằm trong. Có Audit event không?

**Đáp án:** thông thường không. Muốn audit yêu cầu bị từ chối phải dùng boundary phù hợp.

### Câu 4 — Retry bên trong Trace

Trace ngoài, Retry giữa, Observer trong. Retry gọi phần trong hai lần. Trace/Observer vào bao nhiêu lần ở ví dụ?

**Đáp án:** Trace một lần, Observer hai lần, Original hai lần.

### Câu 5 — Return transformation

Original trả 5; AddOne cộng một, Double nhân đôi. Đổi thứ tự có thể thay kết quả?

**Đáp án:** có: AddOne(Double(5))=11, Double(AddOne(5))=12.

### Câu 6 — Exception conversion

Original ném lỗi E, Inner bắt rồi trả fallback. Outer after-throwing có chắc thấy E?

**Đáp án:** không. Outer có thể chỉ thấy normal return của Inner.

### Câu 7 — Hai dòng log

Một request sinh hai log. Có chắc Original chạy hai lần?

**Đáp án:** không. Có thể hai aspect khác nhau hoặc nhiều advice ghi sự kiện cho cùng execution.

### Câu 8 — Call và execution

A chọn method call; B chọn method execution của cùng tên pay. Có chắc cùng chọn đúng một join point?

**Đáp án:** không khi mô hình phân biệt các loại điểm. Đây có thể là hai join point liên quan nhưng khác nhau.

### Câu 9 — Precedence mặc định

A được đặt tên đứng trước B theo alphabet. Có chắc A chạy trước B?

**Đáp án:** không. Thứ tự cần được engine AOP định nghĩa và kiểm thử.

### Câu 10 — Retry và idempotency

Một around retry gọi next hai lần, attempt đầu đã có side effect trước khi lỗi. Rủi ro?

**Đáp án:** side effect có thể bị lặp; cần policy retry, transaction và idempotency phù hợp.

### Câu 11 — Timing và cache

Timing ở ngoài Cache, Cache trả sớm. Timing vẫn ghi thời lượng còn Original không chạy. Có sai không?

**Đáp án:** không nếu metric là độ trễ ranh giới ngoài. Nếu metric được hứa là thời gian Original thì cách đo không đúng phạm vi.

### Câu 12 — Shared static context

A ghi currentUser vào biến static, B đọc; hai thread chạy đồng thời. Rủi ro?

**Đáp án:** dữ liệu các invocation có thể trộn; cần context theo scope thích hợp và quy tắc truyền/cleanup rõ.

## 34. Kết luận

**Một join point đồng thời phù hợp với nhiều aspect** nghĩa là có nhiều pointcut cùng chọn một điểm trong miền join point của mô hình AOP. Để hiểu hành vi cuối cùng, còn phải phân tích **thứ tự advice, cấu trúc bao quanh, điều kiện động, quyền tiếp tục, ngoại lệ và những biến đổi được phép**.

Các nguyên tắc quan trọng:

- **Pointcut overlap là bình thường**, nhưng các chính sách cần có trách nhiệm riêng và hợp đồng phối hợp.
- **Match không bảo đảm mọi advice thực thi**, do short-circuit và phạm vi runtime.
- **Precedence ảnh hưởng cả enter và exit**, đôi khi ảnh hưởng kết quả, lỗi và phạm vi quan sát.
- **Proceed không mặc nhiên gọi thẳng Original**; nó có thể đi qua các advice phía trong.
- **Retry có thể nhân số invocation của inner advice và Original** mà outer advice vẫn chỉ chạy một lần.
- **Exception và return value có thể được chuyển đổi nhiều lớp**; mỗi aspect quan sát dữ liệu ở ranh giới riêng.
- **Request, attempt, advice invocation và business execution** là các đơn vị cần phân biệt khi ghi metrics và audit.
- **Không dùng thứ tự không được cam kết** làm điều kiện đúng đắn; phải kiểm thử toàn bộ chain trong môi trường và cơ chế thực tế.
- **Bảo vệ dữ liệu và bảo toàn hành vi nghiệp vụ** vẫn là trách nhiệm khi nhiều aspect cùng tác động.

**Câu hỏi quyết định trước khi thêm aspect:** *Nếu aspect này được chèn vào chuỗi, nó có thể chặn, lặp, biến đổi hoặc làm thay đổi những gì các aspect khác và caller quan sát hay không?*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-002">Thứ tự ưu tiên và quy tắc phân xử khi có xung đột</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự ưu tiên và quy tắc phân xử khi có xung đột giữa nhiều aspect

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một **join point** có thể được chọn bởi nhiều **pointcut** và chịu tác động của nhiều **advice** thuộc các **aspect** khác nhau. Chẳng hạn một lần gọi nghiệp vụ có thể đồng thời được bảo vệ bởi aspect kiểm tra quyền, cache, transaction, retry, audit và tracing. Các aspect ấy đều có lý do tồn tại, nhưng **thứ tự phối hợp quyết định những hành vi nào chạy trước, hành vi nào có thể bị bỏ qua và kết quả/ngoại lệ cuối cùng bên gọi nhận được**.

Nếu không xác định thứ tự ưu tiên (**precedence**) và quy tắc phân xử xung đột, chương trình có thể vẫn biên dịch thành công nhưng:
- Trả dữ liệu cache trước khi kiểm tra quyền.
- Ghi audit thiếu sự kiện do một lớp bên ngoài short-circuit.
- Đo thời gian với phạm vi không khớp tên metric.
- Nuốt ngoại lệ khiến transaction không thấy lỗi để rollback.
- Retry toàn bộ thao tác có tác dụng phụ mà không bảo đảm idempotency.
- Gọi validation sau khi dữ liệu đã bị ghi hoặc đã phát sự kiện.
- Có kết quả khác nhau khi đổi cách lắp ghép aspect hoặc cấu hình build.

Bài này trình bày cách suy luận **thứ tự ưu tiên, thứ tự vào/ra, xung đột selection và behavior, ràng buộc bắt buộc, tie/cycle, fallback và kiểm thử** trong AOP nói chung. **Không tồn tại một cú pháp khai báo precedence hay quy ước số priority phổ quát**. Ví dụ Java 17 sử dụng các wrapper thuần Java để biểu diễn cơ chế kết hợp một cách minh bạch; đó là mô hình minh họa, không phải khẳng định mọi bộ weaver đều triển khai như vậy.

## 1. Khái niệm nền: nhiều aspect cùng tác động một join point

Các thuật ngữ cần tách riêng:

- **Join point:** điểm trong hoạt động của chương trình mà một mô hình AOP có thể can thiệp, chẳng hạn method call hoặc method execution nếu được hỗ trợ.
- **Pointcut:** biểu thức chọn những điểm cần được can thiệp; các pointcut khác nhau có thể chọn cùng một điểm.
- **Advice:** hành vi thực thi tại join point đã chọn, ví dụ before, after returning, after throwing, after/finally hoặc around.
- **Aspect:** đơn vị tập hợp pointcut/advice và có thể có state/lifecycle riêng.
- **Weaving:** cách đưa các advice vào chương trình thông qua compiler, biến đổi binary, lúc nạp lớp hoặc proxy/wrapper.
- **Precedence / order:** quan hệ ưu tiên xác định các advice được kết hợp, sắp xếp hoặc vào chuỗi thực thi theo hợp đồng của nền tảng.
- **Conflict resolution:** chính sách xác định phải làm gì khi nhiều aspect cùng match, có ràng buộc trái ngược, priority bằng nhau, chặn continuation hoặc thay đổi kết quả/ngoại lệ.

Ví dụ một nghiệp vụ `AccountService.transfer(...)` có thể match các concern:

~~~text
Authorization: kiểm tra chủ thể và quyền trên tài khoản
Validation:    kiểm tra yêu cầu hợp lệ
Transaction:   xác lập ranh giới ghi dữ liệu
Audit:         ghi dấu vết được phép thu thập
Tracing:       tạo một span cho lời gọi
Retry:         thử lại một số lỗi tạm thời
~~~

**Cùng match không có nghĩa tất cả đều luôn chạy**. Nếu một around advice ngoài cùng không gọi tiếp, những advice bên trong có thể không được vào; nếu một advice bắt ngoại lệ rồi trả fallback, các lớp ngoài có thể thấy nhánh thành công thay vì lỗi.

## 2. Phân biệt bốn loại thứ tự

| Khái niệm | Câu hỏi | Có đồng nhất với thứ tự chạy không? |
| --- | --- | --- |
| **Thứ tự xử lý khi build** | Weaver đọc aspect A hay B trước? | Không mặc định; bộ dệt có thể dùng precedence riêng |
| **Thứ tự ghép advice** | Aspect nào bao ngoài/được xét trước? | Quyết định cấu trúc, nhưng phải biết loại advice |
| **Thứ tự chạy ở đường vào** | Before/đầu around nào thấy invocation trước? | Thường ngoài → trong ở mô hình wrapper |
| **Thứ tự chạy ở đường ra** | After/finally nào hoàn tất trước? | Thường trong → ngoài ở wrapper lồng nhau |

Lấy chuỗi around:

~~~text
A(B(C(Business)))
~~~

Nếu cả A, B, C đều gọi continuation đúng một lần và không chặn:

~~~text
A.before
B.before
C.before
Business
C.after
B.after
A.after
~~~

Kết quả là **thứ tự vào và thứ tự ra đảo nhau**. Nếu C ném lỗi, after-success của C và các lớp ngoài không tự chạy; code xử lý error/finally mới có cơ hội được gọi theo cách đã viết.

**Cẩn thận với advice không phải around:** một ngôn ngữ AOP có thể quy định cách xếp before và after độc lập, ví dụ dựa trên precedence hoặc thứ tự khai báo trong cùng aspect. Không tự lấy sơ đồ wrapper làm tiêu chuẩn cho mọi công cụ. Muốn xác định chính xác, phải đọc hợp đồng precedence của mô hình đang dùng và kiểm tra hành vi thực.

## 3. Priority là quy tắc kết hợp, không tự là quy tắc số

Một hệ thống có thể mô tả:

~~~text
AUTH > TX > AUDIT > METRICS
~~~

Dấu `>` ở đây được **quy ước cục bộ** là “bao ngoài/được ưu tiên cao hơn trong chuỗi đang xét”. Không có nghĩa:
- Giá trị priority số lớn luôn ưu tiên hơn số nhỏ.
- Aspect được compile trước sẽ chạy before trước.
- Các loại advice đều dùng cùng một phép sắp xếp.
- Advice thuộc cùng aspect được sắp giống lời gọi tới các aspect khác.
- Một aspect ưu tiên cao sẽ tự luôn chạy trên mọi đường gọi, kể cả đường không đi qua join point.

Có mô hình dùng thứ tự khai báo, có mô hình dùng số priority, có mô hình dùng quan hệ trước/sau hoặc đặc tả định hướng riêng. Điều quan trọng là **định nghĩa lại bằng lời ngữ nghĩa chính xác của priority trước khi dùng**.

### 3.1 Ưu tiên ở cấp aspect và advice có thể khác nhau

Một aspect có thể có nhiều advice cùng match một join point. Quy tắc thứ tự **giữa các aspect** có thể khác quy tắc **giữa các advice trong một aspect**; một số ngôn ngữ còn có quy tắc về kiểu advice, vị trí khai báo hoặc sự không xác định khi hai advice cùng điều kiện.

Vì thế cần ghi rõ:
- Thứ tự precedence chỉ áp dụng **khác aspect** hay cả **nội bộ một aspect**?
- Around ngoài có gọi `proceed` hay không?
- Before được ghép vào bên nào so với around?
- After-returning, after-throwing và finally có thứ tự nào sau khi lời gọi kết thúc?
- Khi equal priority hoặc không khai báo, thứ tự có được bảo đảm không?

Nếu công cụ không đưa ra thứ tự chắc chắn cho trường hợp quan trọng, đó là **rủi ro thiết kế**, không nên trông chờ hệ thống hiện tại “tình cờ chạy đúng”.

### 3.2 Dựng một quy tắc có thể kiểm chứng

Một mô hình tự thiết kế có thể dùng:
1. Mỗi rule được gán **mức độ ưu tiên khác nhau**.
2. Mức số **cao hơn** (theo quy ước riêng) nghĩa là **outermost**.
3. Sort giảm dần theo priority.
4. Ghép wrapper từ phần tử **cuối lên đầu** để bảo đảm outermost đúng ý.
5. Nếu hai rule cùng priority, **fail fast** khi không có chính sách tie-break được phê duyệt.
6. Kiểm tra các ràng buộc an ninh/tác dụng phụ sau khi ghép.

Đây là **quy tắc của một builder minh họa**, không phải luật chung của AOP. Mục tiêu là tạo kết quả ổn định và để người đọc có thể xác minh thứ tự từ code.

## 4. Thứ tự lồng nhau quyết định ai được chạy

Một chuỗi:

~~~text
Outer(Middle(Inner(Business)))
~~~

có ba trường hợp quan trọng:

**Đường thành công:** Outer vào trước, Inner chạy gần nghiệp vụ nhất; khi quay về, Inner kết thúc trước Outer.

**Đường lỗi:** advice đã vào có thể thấy ngoại lệ theo đường ngược; nếu Inner chuyển ngoại lệ thành kết quả thì Outer có thể không còn thấy lỗi.

**Đường short-circuit:** nếu Outer trả sớm mà không gọi Middle, Middle/Inner/Business **không chạy**; bản thân Outer có thể vẫn chạy phần cleanup do nó tự tổ chức.

Do đó câu “A có priority cao hơn B” phải được giải nghĩa cụ thể: A có bao ngoài B không? A có quyền bỏ qua B không? A có thể đổi lỗi thành kết quả không?

### 4.1 Thứ tự xây dựng wrapper có thể ngược thứ tự chạy

Giả sử:

~~~text
chain = Business
chain = Trace(chain)
chain = Cache(chain)
chain = Auth(chain)
~~~

Kết quả:

~~~text
Auth(Cache(Trace(Business)))
~~~

`Auth` được **thêm cuối**, nhưng ở **ngoài cùng** và vào trước. Nếu builder dùng kiểu `chain = Wrap(newAdvice, chain)`, không thể lấy thứ tự duyệt danh sách làm thứ tự BEFORE runtime nếu chưa biết builder duyệt từ đầu hay cuối.

Một bộ weaver khác có thể phân tích tất cả aspect rồi lập graph precedence trước khi tạo artifact, không chạy vòng lặp wrapper như trên.

## 5. Xung đột có những loại nào?

### 5.1 Xung đột thứ tự trước/sau

A cần chạy trước B, nhưng precedence hiện hành đưa B ra ngoài A. Ví dụ kiểm tra quyền phải trước cache trả dữ liệu cá nhân; nếu cache ở ngoài và trả hit thì phân quyền có thể bị bypass.

### 5.2 Xung đột selection

Hai pointcut chồng nhau và cùng áp dụng advice vào một phương thức, dẫn tới:
- Logging/metrics đếm hai lần.
- Hai validation rules có hành vi khác.
- Hai transaction/retry wrappers không được thiết kế chạy lồng nhau.
- Một pointcut quá rộng chọn cả mã helper của advice, gây reentrancy.
- Một helper method hoặc bridge method bị chọn thêm hoặc bỏ sót theo bộ weaver.

**Selection conflict khác order conflict.** Đổi precedence không giải quyết một pointcut đã chọn sai tập join point; cần sửa quy tắc match hoặc phân tách trách nhiệm.

### 5.3 Xung đột thay đổi dữ liệu

Một advice chuẩn hóa tham số thành dạng X; advice khác kỳ vọng dạng gốc Y. Nếu chạy theo thứ tự khác, dữ liệu nghiệp vụ/khóa cache có thể khác. Một advice sửa kết quả trả về cũng có thể làm outer advice thấy response khác.

Không phải mọi nền tảng AOP đều cho phép around advice thay args hoặc gọi tiếp nhiều lần. Cần kiểm tra hợp đồng continuation và kiểu.

### 5.4 Xung đột xử lý ngoại lệ

Ví dụ:
- ErrorTranslator chuyển ngoại lệ hạ tầng thành lỗi nghiệp vụ.
- Transaction advice cần nhìn thấy lỗi để rollback.
- Retry advice chọn một số loại lỗi tạm thời.
- Audit advice muốn ghi nhận cả lỗi và thành công.

Nếu Translator nuốt ngoại lệ **trước khi** Transaction có thể quan sát, transaction có thể xem lần gọi là thành công. Ngược lại, nếu Retry nằm ngoài Transaction và mỗi attempt tạo một transaction mới, ngữ nghĩa khác với Retry nằm trong một transaction kéo dài. Không thể quyết định chỉ từ tên aspect; phải biết **error path và side effects**.

### 5.5 Xung đột trạng thái

Hai aspect có thể cùng sửa một cache, registry, thread context hoặc thông tin tracing. Priority không tự giải quyết race condition. Thứ tự đồng bộ trong một thread không bảo đảm toàn vẹn khi nhiều thread chạy song song; cần thread-safety, ownership và lifecycle của state.

## 6. Các quy tắc phân xử có thể áp dụng

Một **bộ quy tắc phân xử** nên được viết như một phần của thiết kế, không dựa vào may rủi. Các nhóm quy tắc hữu ích:

| Quy tắc | Cách hiểu | Trường hợp dùng |
| --- | --- | --- |
| **Bắt buộc trước (must precede)** | A phải ở ngoài hoặc chạy kiểm tra trước hành vi B cụ thể | Authorization trước cache trả dữ liệu |
| **Bắt buộc sau (must follow)** | B chỉ được chạy sau khi A hoàn tất điều kiện | Audit sau khi biết outcome |
| **Bao phạm vi (must surround)** | A phải bao toàn bộ logic B, kể cả nhánh lỗi/cleanup | Đo latency end-to-end |
| **Không đồng tồn tại (mutual exclusion)** | Hai aspect không được cùng instrument một điểm theo một cách | Hai cơ chế retry không tương thích |
| **Gộp trách nhiệm** | Hai chức năng quá phụ thuộc để sắp độc lập | Validation và chuẩn hóa chung một precondition |
| **Phân tách phạm vi** | Sửa pointcut để loại overlap không mong muốn | Tránh logging hai lần |
| **Từ chối khi thiếu thứ tự** | Thiếu/đụng priority làm cấu hình fail fast | Security/transaction cần thứ tự bảo đảm |
| **Ràng buộc thứ tự bộ phận** | Chỉ công bố những cặp thật sự có quan hệ | Metrics và audit có thể độc lập |
| **Khả năng thử lại có kiểm soát** | Cho phép retry khi lỗi thuộc nhóm và thao tác idempotent | Temporary failure |
| **Bảo toàn hợp đồng** | Không biến đổi return/throw trừ khi chính sách cho phép | Logging/metrics chỉ quan sát |

**Quan trọng:** “ưu tiên cao nhất” không phải lúc nào cũng đồng nghĩa “được phép bỏ qua mọi thứ khác”. Với audit hoặc security bắt buộc, cần một bất biến: dù cache hit, deny, retry hay fallback, **mỗi đường đi phải đáp ứng nghĩa vụ tương ứng**.

### 6.1 Fail-fast hay tự chọn tie-break?

Với xung đột priority bằng nhau có hai hướng:
- **Fail fast:** từ chối khởi động/xây dựng cấu hình nếu thứ tự cần thiết nhưng không rõ. Dễ phát hiện vấn đề sớm, đặc biệt ở auth/transaction.
- **Tie-break ổn định:** ví dụ dựa trên thứ tự khai báo tường minh hoặc một ID ổn định, nhưng chỉ khi tie **được phép về ngữ nghĩa**. Một tie-break tùy ý có thể che lỗi thiết kế.

**Không nên dùng thứ tự của hash map hoặc kết quả reflection không có hợp đồng như tie-break cho an ninh.** Kết quả nhìn có vẻ ổn định ở một lần chạy không phải bằng chứng được bảo đảm qua phiên bản/JVM/build.

### 6.2 Khi nào không cần thứ tự toàn phần?

Nếu hai advice chỉ độc lập phát ra số liệu theo cơ chế an toàn, không biến đổi kết quả, và thứ tự không ảnh hưởng ý nghĩa, có thể chỉ cần một **partial order** giữa những cặp có phụ thuộc.

Ví dụ:

~~~text
Auth must precede Cache
Transaction must surround Mutation
Tracing may be outer or inner depending on metric scope
~~~

Một partial order hợp lệ không nhất thiết xác định vị trí duy nhất cho mọi aspect. Nhưng ở nơi kết quả quan sát có thể khác, như tracing scope, phải **định nghĩa rõ metric muốn đo** trước khi chấp nhận nhiều thứ tự.

## 7. Quan hệ ưu tiên dưới dạng đồ thị và xử lý chu trình

Mỗi ràng buộc `A -> B` có thể biểu thị “A phải nằm ngoài/đi trước B” trong ngữ cảnh đã quy ước. Tập ràng buộc tạo một **đồ thị có hướng**.

Ví dụ hợp lệ:

~~~text
Authorization -> Cache
Authorization -> BusinessEffects
Transaction   -> BusinessEffects
Audit         -> BusinessEffects
~~~

Có thể tạo một thứ tự topo nếu đồ thị **không có chu trình**. Thứ tự topo có thể không duy nhất nếu một vài cặp không có quan hệ; mọi kết quả hợp lệ phải vẫn bảo toàn các cạnh ràng buộc.

Ví dụ mâu thuẫn:

~~~text
A -> B
B -> C
C -> A
~~~

Không tồn tại thứ tự tuyến tính thỏa cả ba ràng buộc. Một builder đúng nên **phát hiện cycle và báo lỗi cấu hình** thay vì lờ một cạnh, chọn theo tên alphabet hoặc âm thầm tạo hành vi không phù hợp.

### 7.1 Thuật toán phân xử khái niệm

~~~text
input: tập aspect + tập ràng buộc bắt buộc
validate pointcut overlap and incompatible rules
construct directed precedence graph
if graph contains a cycle:
    reject configuration and report the cycle
compute topological order under the documented convention
for unconstrained ties:
    use an approved explicit stable policy or require more constraints
compose selected advice into an effective chain
assert protected invariants through success/error/short-circuit paths
~~~

Đây là **giả mã**, không phải API của một bộ AOP nào. Nếu một cơ chế chỉ hỗ trợ **priority số**, có thể phải ánh xạ những quan hệ cần thiết sang số không trùng; nếu nó không cho bảo đảm một ràng buộc bắt buộc, cần sửa kiến trúc hoặc chuyển trách nhiệm cần bảo vệ sang một ranh giới khác.

### 7.2 Không tạo chu trình do mô tả nhầm ngữ nghĩa

Một yêu cầu như “Tracing phải chạy trước business và cũng phải chạy sau business” **không phải** quan hệ ưu tiên có chu trình: đó có thể là một around advice có phần before và after. Hãy biểu diễn yêu cầu **bao phạm vi**, không tạo hai cạnh Tracing -> Business và Business -> Tracing rồi coi là conflict.

Đồ thị mô tả **vị trí lớp**, trong khi một lớp có thể có cả pha vào và pha ra. Phân biệt hai khái niệm giúp tránh báo chu trình giả.

## 8. Ví dụ Java 17: precedence, tie và short-circuit

Ví dụ Java dưới đây **không dùng trình biên dịch AOP đặc biệt**. Nó dựng một chuỗi wrapper có ngữ nghĩa quanh advice bằng Java 17, với **quy ước cục bộ priority số lớn hơn => wrapper ngoài hơn**. Builder sort theo priority giảm dần rồi bọc **từ cuối danh sách về đầu**.

Ba concern:
- `AUTH`: kiểm tra yêu cầu; từ chối ID `deny` trước khi cho chạy tiếp.
- `CACHE`: trả sớm với ID `cached`; nếu không hit, tiếp tục.
- `TRACE`: log before/after/error/finally khi đi tới lớp đó.

Ví dụ có cấu hình **an toàn** `AUTH > CACHE > TRACE`, một cấu hình **rủi ro** `CACHE > AUTH > TRACE`, và kiểm tra **priority trùng nhau**. Kết quả thể hiện tại sao thứ tự lắp ghép không phải chi tiết trang trí.

Lưu file `AspectPrecedenceDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AspectPrecedenceDemo {
    @FunctionalInterface
    interface Action {
        String run(String id);
    }

    @FunctionalInterface
    interface Wrapper {
        Action wrap(Action next);
    }

    record Rule(String name, int priority, Wrapper factory) {}

    static Action business() {
        return id -> {
            System.out.println("BUSINESS " + id);
            if ("fail".equals(id)) {
                throw new IllegalStateException("failed");
            }
            return "OK:" + id;
        };
    }

    static Action authorize(Action next) {
        return id -> {
            System.out.println("AUTH CHECK");
            if ("deny".equals(id)) {
                System.out.println("AUTH REJECT");
                throw new SecurityException("forbidden");
            }
            return next.run(id);
        };
    }

    static Action cache(Action next) {
        return id -> {
            if ("cached".equals(id)) {
                System.out.println("CACHE HIT");
                return "CACHED:cached";
            }
            System.out.println("CACHE MISS");
            return next.run(id);
        };
    }

    static Action trace(Action next) {
        return id -> {
            System.out.println("TRACE BEFORE");
            try {
                String result = next.run(id);
                System.out.println("TRACE AFTER");
                return result;
            } catch (RuntimeException error) {
                System.out.println("TRACE ERROR "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println("TRACE FINALLY");
            }
        };
    }

    static Action compose(List<Rule> specs) {
        List<Rule> ordered = new ArrayList<>(specs);
        ordered.sort(
                Comparator.comparingInt(Rule::priority).reversed()
                        .thenComparing(Rule::name));

        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).priority()
                    == ordered.get(i).priority()) {
                throw new IllegalArgumentException(
                        "ambiguous priority");
            }
        }

        System.out.println("ORDER " + ordered.stream()
                .map(Rule::name)
                .collect(Collectors.joining(">")));

        Action result = business();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            result = ordered.get(i).factory().wrap(result);
        }
        return result;
    }

    static void scenario(String name, Action action, String id) {
        System.out.println("CASE " + name);
        try {
            System.out.println("RESULT " + action.run(id));
        } catch (RuntimeException error) {
            System.out.println("CALLER "
                    + error.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Action safe = compose(List.of(
                new Rule("TRACE", 10, AspectPrecedenceDemo::trace),
                new Rule("CACHE", 20, AspectPrecedenceDemo::cache),
                new Rule("AUTH", 30, AspectPrecedenceDemo::authorize)));

        scenario("safe-normal", safe, "ok");
        scenario("safe-cached", safe, "cached");
        scenario("safe-denied", safe, "deny");
        scenario("safe-error", safe, "fail");

        Action risky = compose(List.of(
                new Rule("AUTH", 20, AspectPrecedenceDemo::authorize),
                new Rule("TRACE", 10, AspectPrecedenceDemo::trace),
                new Rule("CACHE", 30, AspectPrecedenceDemo::cache)));
        scenario("risky-cached", risky, "cached");

        System.out.println("CASE duplicate-priority");
        try {
            compose(List.of(
                    new Rule("AUTH", 10, AspectPrecedenceDemo::authorize),
                    new Rule("CACHE", 10, AspectPrecedenceDemo::cache)));
        } catch (IllegalArgumentException error) {
            System.out.println("CONFIG " + error.getMessage());
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac AspectPrecedenceDemo.java
java AspectPrecedenceDemo
~~~

Stdout dự kiến **theo chính chương trình Java 17 ở trên**:

~~~text
ORDER AUTH>CACHE>TRACE
CASE safe-normal
AUTH CHECK
CACHE MISS
TRACE BEFORE
BUSINESS ok
TRACE AFTER
TRACE FINALLY
RESULT OK:ok
CASE safe-cached
AUTH CHECK
CACHE HIT
RESULT CACHED:cached
CASE safe-denied
AUTH CHECK
AUTH REJECT
CALLER SecurityException
CASE safe-error
AUTH CHECK
CACHE MISS
TRACE BEFORE
BUSINESS fail
TRACE ERROR IllegalStateException
TRACE FINALLY
CALLER IllegalStateException
ORDER CACHE>AUTH>TRACE
CASE risky-cached
CACHE HIT
RESULT CACHED:cached
CASE duplicate-priority
CONFIG ambiguous priority
~~~

### 8.1 Đọc kết quả: trường hợp bình thường

Trong `safe-normal`, thứ tự `AUTH > CACHE > TRACE` dẫn tới:

~~~text
AUTH CHECK -> CACHE MISS -> TRACE BEFORE -> BUSINESS
                                     |
                                    return
                                     v
                              TRACE AFTER
                              TRACE FINALLY
~~~

`AUTH` chạy trước cache; `TRACE` ở bên trong chỉ đo **lời gọi thực sự đi tiếp qua cache**. Nếu mục tiêu tracing là đo tổng thời gian từ lúc caller gọi tới lúc cache trả về, vị trí `TRACE` này **không đáp ứng** yêu cầu, vì nhánh cache hit không đi qua nó.

### 8.2 Đọc kết quả: cache hit an toàn và không an toàn

`safe-cached` vẫn in `AUTH CHECK` **trước** `CACHE HIT`. `risky-cached` in duy nhất `CACHE HIT`: Authorization bên trong **không được gọi**.

Trong demo, chuỗi cache giả trả giá trị cố định cho một ID và **không xử lý dữ liệu người dùng thật**. Đây chỉ là minh họa thứ tự. Trong hệ thống thật, cache key, policy và quyền truy cập còn phải chính xác theo principal/tenant/tài nguyên.

### 8.3 Đọc kết quả: từ chối và ngoại lệ

Với `safe-denied`, AUTH ném `SecurityException` trước continuation; các lớp CACHE, TRACE, BUSINESS không được vào. Nếu audit bắt buộc ghi **mọi lần từ chối**, audit phải đặt ở ranh giới phù hợp (ví dụ bao ngoài kiểm tra quyền) hoặc được gọi tường minh trong chính nhánh từ chối.

Với `safe-error`, TRACE đã vào và thấy `IllegalStateException`, nên in `TRACE ERROR` rồi `TRACE FINALLY`. Không có `TRACE AFTER` vì target không trả về bình thường.

### 8.4 Priority trùng là một quyết định thiết kế

Builder của ví dụ **không âm thầm đoán** giữa hai rule cùng số: nó ném `IllegalArgumentException("ambiguous priority")`. Đây là quy ước bảo vệ chính demo, **không phải hành vi mặc định bắt buộc của mọi công cụ AOP**.

Ở một hệ thống thực, có thể cho phép tie giữa hai advice độc lập; nhưng với tương tác an ninh/giao dịch, nên yêu cầu thứ tự rõ hoặc chứng minh hai thứ tự đều giữ hợp đồng. Thứ tự tên alphabet chỉ là một chính sách khi được **công bố và phê duyệt**, không tự có giá trị bảo mật.

### 8.5 Hạn chế cần nhớ

- Mẫu này chỉ **dựng wrapper Java thuần**, không khai báo pointcut của một bộ weaver.
- `priority` cao ở ngoài là **quy tắc tự đặt**; không suy ra cú pháp/ký hiệu tương tự ở mọi AOP.
- Trong demo, CACHE cố tình short-circuit và AUTH chỉ từ chối ID `deny`; không phải xác thực/phân quyền thật.
- Không có transaction, retry, audit store, logging backend, dữ liệu nhạy cảm hoặc concurrency production.
- Không chứng minh mọi `before`, `after` độc lập ở một ngôn ngữ AOP sẽ xếp như các method wrapper này.
- Nếu gọi `business()` trực tiếp, không có wrapper; phạm vi proxy/delegation khác với biến đổi bytecode trực tiếp ở method execution.

## 9. Phân xử xung đột giữa authorization, cache và audit

Đây là tình huống dễ tạo lỗi bảo mật.

### 9.1 Authorization phải bảo vệ cả cache hit

Nếu cache có dữ liệu phụ thuộc danh tính hoặc quyền, chuỗi:

~~~text
Cache(Authorize(Business))
~~~

cho phép cache ở ngoài trả kết quả mà không gọi `Authorize` bên trong. Thiết kế này **không đáp ứng** bất biến “mọi lần đọc được bảo vệ bởi kiểm tra quyền trước khi trả dữ liệu” trừ khi chính Cache có một cơ chế xác thực/phân quyền tương đương và chứng minh được.

Một cấu trúc rõ ràng hơn:

~~~text
Authorize(Cache(Business))
~~~

Nhưng vẫn phải đảm bảo mọi caller cần bảo vệ đi qua outer boundary; nếu có một lời gọi trực tiếp target hoặc cache không được bảo vệ, thứ tự wrapper không cứu được.

### 9.2 Audit cần ghi gì?

Nếu yêu cầu audit là **mọi quyết định cho phép/từ chối**, đặt Audit bên trong Authorization và chỉ để nó ghi khi tiếp tục là không đủ:

~~~text
Authorization(Audit(Business))
~~~

Nhánh từ chối có thể không gọi Audit. Phải xác định:
- Audit ghi **request attempt** hay **business success**?
- Cần ghi cả **denied requests** không?
- Có cần ghi cache hit/fallback/exception không?
- Dữ liệu tối thiểu nào được phép giữ lại?
- Event phải phát trước/đồng thời/sau tác dụng phụ theo tiêu chuẩn nào?

Không nên tự log token hoặc raw arguments chỉ vì một aspect được đặt ở ngoài. **Thứ tự đúng và data minimization là hai yêu cầu riêng**.

### 9.3 Không biến kiểm soát quyền thành “best effort”

Một advice bảo mật chỉ ghi cảnh báo rồi `proceed` khi thiếu quyền không tạo ra bảo đảm chặn. Với các ranh giới an ninh, hãy định nghĩa **fail-closed** khi không thể xác minh quyền (theo chính sách nghiệp vụ phù hợp) và kiểm thử mọi đường thoát.

## 10. Transaction, retry, ngoại lệ và precedence

Một tương tác phức tạp thường có dạng:

~~~text
Retry(Transaction(Business))
Transaction(Retry(Business))
~~~

Nếu Retry nằm ngoài Transaction và gọi continuation ba lần, **mỗi lần thử có thể đi qua vùng Transaction mới**, nếu Transaction thực sự tạo/kết thúc một giao dịch khi continuation được gọi. Nếu Transaction nằm ngoài Retry, các lần thử có thể diễn ra **trong một ranh giới giao dịch**, thay đổi khả năng rollback/commit.

Không được kết luận chắc chắn từ hai cái tên `Retry` và `Transaction`: cần biết implementation thật, cơ chế truyền transaction, loại lỗi nào được retry, và các ràng buộc khi thao tác đã gây tác dụng phụ.

### 10.1 Các nhánh lỗi phải được định nghĩa

Giả mã:

~~~text
around retry:
    for attempt in 1..max:
        try:
            return proceed()
        catch transientError:
            if canRetrySafely(attempt): continue
            else: throw
~~~

Nếu advice Retry **nuốt mọi exception** mà không phân loại, nó có thể lặp lại lỗi validation cố định hoặc tác dụng phụ đã thành công ở lần đầu nhưng response bị mất. Cần:
- Chỉ retry lỗi có khả năng tạm thời.
- Giới hạn attempts và backoff.
- Có deadline/cancellation.
- Bảo đảm idempotency hoặc deduplication khi cần.
- Ghi rõ số lần gọi logic khác số lần target thực thi.

### 10.2 Exception translation và rollback

Giả sử:

~~~text
Transaction(Translate(Business))
Translate(Transaction(Business))
~~~

Ở cấu trúc đầu, nếu `Translate` bắt một lỗi rồi trả **fallback thành công**, Transaction bên ngoài có thể xem lần gọi là success và commit, trừ khi policy transaction có tín hiệu rollback độc lập.

Ở cấu trúc thứ hai, Transaction có thể thấy lỗi gốc, quyết định rollback, rồi Translate bên ngoài mới chuyển lỗi sang response phù hợp.

Đây là **khả năng theo logic lồng nhau**, không phải cam kết mọi transaction manager sẽ hành xử như vậy. Nếu transaction được đánh dấu rollback-only hoặc có các quy tắc khác, kết quả cần được kiểm chứng bằng test thực.

### 10.3 Khi nào đặt metrics ngoài/ trong?

~~~text
Metrics(Retry(Transaction(Business)))
Retry(Transaction(Metrics(Business)))
~~~

Mẫu đầu có thể đo **toàn bộ thời gian** gồm nhiều attempt; mẫu sau có thể ghi một metric cho mỗi attempt hoặc chỉ đoạn business, tùy vị trí.

Tên metric nên phân biệt:
- Request-level latency.
- Attempt-level latency.
- Transaction-level latency.
- Target-execution latency.

Không thể quyết định thứ tự metrics chỉ dựa vào nguyên tắc “ưu tiên số lớn nhất”. Cần bắt đầu từ **câu hỏi quan sát**.

## 11. Xung đột giá trị trả về, args và short-circuit

Một around advice có thể có khả năng:
- Chuyển đổi args trước `proceed` khi mô hình hỗ trợ.
- Gọi `proceed` một hoặc nhiều lần nếu hợp đồng cho phép.
- Trả giá trị cache, fallback hoặc kết quả đã được biến đổi.
- Chuyển exception thành kết quả.
- Ném một exception mới.

Nếu hai advice cùng muốn **sửa args**, thứ tự có thể ảnh hưởng giá trị thật đến target. Một filter chuẩn hóa `trim` rồi một filter mã hóa khác với làm ngược thứ tự khi phép biến đổi không giao hoán.

Nếu hai advice cùng muốn **sửa result**, outer advice nhận kết quả mà inner đã biến đổi; kết quả cuối có thể phụ thuộc thứ tự. Với kiểu trả về primitive, generic, nullability hoặc checked exceptions, bất kỳ biến đổi nào cũng phải giữ hợp đồng mà caller kỳ vọng.

### 11.1 Phép biến đổi có thể không giao hoán

Ký hiệu hai phép xử lý `A` và `B`:

~~~text
A(B(x)) != B(A(x))   (trong một số tình huống)
~~~

Đây không phải định lý rằng **mọi** advice không giao hoán. Logging độc lập có thể cho cùng kết quả nghiệp vụ theo cả hai thứ tự, nhưng kết quả log/latency có thể vẫn khác.

Cần xác định **điều gì được phép thay đổi**:
- Chỉ thứ tự quan sát?
- Số metric?
- Giá trị trả về?
- Loại ngoại lệ?
- Side effects?
- Trạng thái transaction?
- Quyền truy cập và audit?

Nếu câu trả lời vượt quá sự khác biệt cho phép, precedence là một phần của **hợp đồng chương trình**, không thể để mặc định.

## 12. Weaving order khác advice precedence

Trong compile-time hoặc post-compile weaving, một bộ weaver có thể xử lý các aspect theo một thứ tự nội bộ; nhưng thứ tự ấy **không mặc định** quyết định thứ tự advice runtime. Công cụ có thể xây graph precedence độc lập rồi mới kết hợp mã.

Ngược lại, nếu dùng code wrapper thủ công:

~~~text
chain = Target
chain = A(chain)
chain = B(chain)
~~~

`B` bao ngoài `A`, vì mỗi lần thêm tạo lớp ngoài mới. Một người đọc thứ tự lệnh A rồi B mà kết luận `A.before` chạy trước B sẽ sai.

Thêm một phép biến đổi bytecode khác có thể làm thay đổi **tập join point** mà weaver sau nhìn thấy. Đây là xung đột **transformation order** chứ chưa chắc xung đột **advice precedence**.

### 12.1 Dệt nhiều lần có thể làm thêm advice?

Một số công cụ hỗ trợ reweaving có kiểm soát, số khác từ chối hoặc có hành vi khác. Không mặc định `W(W(P)) == W(P)` hay mặc định luôn có double logging. Phải theo hợp đồng của công cụ, giữ đầu vào unwoven và kiểm tra artifact.

### 12.2 Runtime proxy có thể bỏ qua advice

Một chuỗi interceptor nằm trên proxy chỉ nhận những lời gọi **qua proxy**. Direct-call target hoặc self-invocation trên target có thể bypass, bất kể priority đã khai báo. Với weaving trực tiếp method execution, phạm vi có thể khác.

**Quy tắc ưu tiên không thể sửa một join point chưa từng được instrument.**

## 13. Tránh chu trình và tie giả khi phối hợp aspect

**Chu trình precedence** khác với hai aspect có priority bằng nhau. Nếu A phải bao ngoài B, B bao ngoài C và C bao ngoài A trong **cùng một chuỗi**, không thể dựng một thứ tự thỏa cả ba. Bộ phối hợp nên từ chối cấu hình mâu thuẫn thay vì âm thầm bỏ một ràng buộc; cách phát hiện chu trình và xử lý các vòng khởi tạo/runtime được thực hành riêng khi phân tích phụ thuộc vòng.

**Tie** chưa chắc là lỗi: nếu hai advice thật sự độc lập theo hợp đồng, hệ thống có thể dùng tie-break đã công bố hoặc cho phép thứ tự không xác định khi mọi kết quả hợp lệ đều tương đương. Nếu chính sách phụ thuộc thứ tự, phải thêm ràng buộc tường minh, không lấy tên lớp, thứ tự tìm file hoặc thời điểm đăng ký để chữa cháy.

### 13.1. Phân loại ràng buộc thay vì đặt một bảng priority cứng

Khi review, phân biệt **bất biến bắt buộc** (quyền, toàn vẹn, commit/audit theo nghĩa vụ), **hợp đồng chức năng** (validation, caching, retry) và **quan sát/tối ưu** (tracing, metrics). Đây là nhóm câu hỏi để phát hiện xung đột, **không phải trật tự chung**: audit bắt buộc hoặc metric end-to-end đôi khi phải bao ngoài chính sách khác. Chỉ tuyên bố hai aspect có thể hoán đổi khi đã kiểm chứng không làm thay đổi những bất biến liên quan.

## 14. Test matrix bắt buộc cho nhiều aspect

| Trường hợp | Điều cần xác minh |
| --- | --- |
| Một join point có hai before advice | Thứ tự đã xác định và không lặp ngoài ý muốn |
| Around A bao Around B | Đường vào/ra đúng mô hình precedence |
| Bên ngoài short-circuit | Lớp bên trong không chạy; nghĩa vụ vẫn được bảo vệ |
| Nghiệp vụ thành công | Return value và side effects đúng |
| Nghiệp vụ ném lỗi | After-success không chạy sai; after-error/finally đúng |
| Inner bắt lỗi trả fallback | Outer thấy success hay lỗi theo hợp đồng |
| Cache hit trước authentication | Không trả dữ liệu trái quyền |
| Authorization deny | Target không gây tác dụng phụ; audit bắt buộc vẫn có |
| Retry nhiều attempts | Không lặp side effects trái ý, transaction đúng |
| Transaction rollback | Exception translation không che mất tín hiệu rollback |
| Pointcut overlap | Không instrument trùng hoặc thiếu điểm |
| Hai advice đồng priority | Có tie-break được phê duyệt hoặc fail fast |
| Ràng buộc precedence có chu trình | Phát hiện và báo cấu hình không hợp lệ |
| Đổi thứ tự dựng proxy | Kết quả theo đúng thuật toán bọc |
| Recompile/reweave/redeploy | Artifact/runtime dùng precedence mới |
| Direct-call và self-invocation | Biết phạm vi proxy có chặn hay không |
| Concurrent invocations | State aspect không race, thứ tự cục bộ mỗi call hợp lệ |
| Async return | Phân biệt return của handle với completion thực tế |
| Log/metrics/audit | Không ghi dữ liệu nhạy cảm và không đếm sai phạm vi |

### 14.1 Test thứ tự bằng danh sách sự kiện

Trong bài test đồng bộ, dùng một danh sách event thay vì nhìn log không bảo đảm đồng bộ:

~~~text
expected_success:
  AUTH.before
  TX.begin
  METRIC.start
  business
  METRIC.end
  TX.commit
  AUTH.after
~~~

Đây là **expected trace minh họa một cấu trúc cụ thể**, không phải thứ tự mặc định của mọi AOP. Với nhiều thread, không kiểm tra một tổng thứ tự log toàn hệ thống nếu hợp đồng chỉ yêu cầu thứ tự **trong mỗi invocation**; dùng correlation ID và assert từng chuỗi riêng.

### 14.2 Test phủ định cần thiết

Không chỉ chứng minh điều **phải xảy ra**, mà còn điều **không được xảy ra**:
- Không có business side effect sau `AUTH DENY`.
- Không trả cached private data nếu không kiểm tra quyền.
- Không commit khi transaction phải rollback.
- Không phát audit "SUCCESS" cho một attempt lỗi.
- Không lặp ghi tiền do retry.
- Không ghi token/secret vào log từ arguments.
- Không có advice lặp hai lần vì overlap sai.
- Không có một precedence tùy hứng khi các rule xung đột.

## 15. Chẩn đoán khi thứ tự thực tế khác mong muốn

Hãy đi từ **cấu trúc** tới **hành vi**:

1. Xác minh runtime đang dùng **artifact, proxy hoặc aspect config phiên bản nào**.
2. Xác minh **loại join point** đang xét: call, execution hay loại công cụ hỗ trợ.
3. Liệt kê **pointcut nào thật sự match**, không chỉ pointcut dự kiến.
4. Liệt kê **advice type** và các rule precedence/tie-break.
5. Xem **effective interceptor chain** sau khi resolve, nếu nền tảng cung cấp.
6. Đặt dấu vết an toàn cho **entry, proceed, exit, throw và finally**.
7. Kiểm tra có lớp nào return trước, cache hit, deny, fallback hoặc retry không.
8. Đối chiếu **kết quả cuối và side effects** với hợp đồng nghiệp vụ.
9. So sánh đường success, error, async và direct/proxy call.
10. Ghi regression test cho từng ràng buộc bắt buộc.

Một trace trông “ngược” có thể là đúng: around ngoài chạy BEFORE trước nhưng AFTER sau. Đừng sửa cấu hình chỉ để đảo thứ tự vài dòng log nếu như vậy làm phá nghĩa vụ bảo mật.

## 16. Các ngộ nhận thường gặp

1. **“Aspect được biên dịch trước có precedence cao hơn.”** Không có quy tắc chung như vậy.
2. **“Priority số nhỏ luôn trước.”** Quy ước số phụ thuộc nền tảng, cần đọc hợp đồng.
3. **“Một join point khớp nhiều aspect thì tất cả chắc chắn chạy.”** Around ngoài có thể short-circuit.
4. **“Thứ tự after giống thứ tự before.”** Trong wrapper lồng nhau, thường đi ra theo hướng ngược.
5. **“Equal priority thì không sao, Java luôn giữ thứ tự khai báo.”** Không có bảo đảm chung của AOP.
6. **“Đổi precedence sửa được pointcut match sai.”** Selection và composition khác nhau.
7. **“Audit nằm trong Auth vẫn thấy mọi lần bị từ chối.”** Không nếu Auth trả/throw trước khi gọi tiếp.
8. **“Cache và Auth có thể đảo tùy ý.”** Cache hit ở ngoài có thể bypass Auth bên trong.
9. **“Retry và Transaction luôn giao hoán.”** Có thể thay đổi ranh giới giao dịch và side effects.
10. **“ExceptionTranslator trả fallback không ảnh hưởng rollback.”** Có thể che tín hiệu lỗi.
11. **“Mọi advice chỉ đọc đều không có xung đột.”** Thứ tự log/metric, số lần và việc thu thập dữ liệu vẫn có thể khác.
12. **“Một priority chung giải quyết mọi chiều phụ thuộc.”** Có thể cần graph ràng buộc hoặc sửa kiến trúc.
13. **“Có cycle thì chọn ngẫu nhiên một thứ tự vẫn chạy được.”** Nó không thỏa tất cả bất biến.
14. **“Đảo file aspect có nghĩa đảo runtime advice order.”** Tùy mô hình weaving, không được suy.
15. **“Proxy có precedence đúng thì mọi đường gọi đều chịu advice.”** Direct target/self-invocation có thể bypass.
16. **“Trace không thấy advice nghĩa advice không match.”** Log sampling, short-circuit hoặc đường gọi khác cũng có thể gây ra.
17. **“Thứ tự trong một aspect giống thứ tự giữa các aspect.”** Cần kiểm tra quy tắc riêng.
18. **“Priority cao đồng nghĩa an toàn.”** Chỉ một thứ tự thỏa các bất biến cụ thể mới có ý nghĩa.

## 17. Checklist thiết kế quy tắc phân xử

Trước khi chốt cấu hình nhiều aspect:

- [ ] Xác định chính xác **tập join point** của từng aspect; loại bỏ overlap không chủ ý.
- [ ] Phân loại **advice type**: before, after-returning, after-throwing, finally, around.
- [ ] Xác định advice nào có thể **không proceed**, retry, sửa args/result hoặc chuyển exception.
- [ ] Viết các **bất biến bảo mật, transaction, audit và side effects** bằng ngôn ngữ kiểm chứng được.
- [ ] Biểu diễn ràng buộc thứ tự theo quan hệ **must precede / must surround**, không chỉ đặt số.
- [ ] Xác định **quy ước số priority** nếu công cụ dùng số: hướng tăng/giảm, ý nghĩa outer.
- [ ] Giải quyết equal priorities bằng policy được phê duyệt hoặc fail fast.
- [ ] Phát hiện **chu trình** ràng buộc và báo lỗi sớm.
- [ ] Đảm bảo cấu trúc đáp ứng cả nhánh **success/error/short-circuit/cache-hit**.
- [ ] Xác minh **effective chain** khi dùng proxy và tập điểm khi dùng bytecode weaving.
- [ ] Kiểm tra **direct/self calls** và các điểm không được instrument.
- [ ] Phân biệt request-level, attempt-level và target-level metrics.
- [ ] Giữ log/tracing/audit theo nguyên tắc **ít dữ liệu nhất**.
- [ ] Viết regression tests cho order, outcome, rollback, idempotency và cleanup.
- [ ] Pin version công cụ/cấu hình nếu build/reweave có thể đổi rule precedence.
- [ ] Có quy trình rollback khi thay đổi priority tác động nghiệp vụ.

## 18. Bài tập ôn tập

**Câu 1.** Có `A(B(Business))`. Khi cả A và B là around advice chuyển tiếp một lần, lớp nào chạy phần before trước và phần after sau?

**Đáp:** A.before chạy trước B.before; khi quay về bình thường, B.after chạy trước A.after. Không được đọc tên A/B rồi suy cùng thứ tự cho mọi loại advice trong công cụ khác.

**Câu 2.** Cache ngoài Authorization và cache hit không proceed. Authorization bên trong có chạy không?

**Đáp:** không qua đường này. Nếu dữ liệu cần bảo vệ, phải đưa kiểm tra quyền ra ranh giới không thể bypass hoặc có cơ chế kiểm tra tương đương đáng tin.

**Câu 3.** Hai aspect cùng priority và đều tác động đến kết quả. Có thể dùng thứ tự tên lớp làm tie-break không?

**Đáp:** chỉ khi đó là policy tường minh, được phê duyệt và chứng minh giữ hợp đồng. Với bất biến bắt buộc, nên yêu cầu thứ tự rõ hoặc fail fast.

**Câu 4.** A phải ngoài B, B ngoài C, C ngoài A. Có sắp được không?

**Đáp:** không; đồ thị có chu trình. Cần sửa các ràng buộc/kiến trúc chứ không bỏ ngẫu nhiên một cạnh.

**Câu 5.** Vì sao `Retry(Transaction(Business))` có thể khác `Transaction(Retry(Business))`?

**Đáp:** ranh giới transaction có thể được tạo cho mỗi attempt hoặc bao nhiều attempts; điều này ảnh hưởng rollback, retry side effects và idempotency. Cần test theo implementation thực tế.

**Câu 6.** Tracing đặt trong Cache, cache hit trả sớm. Metric đo toàn bộ request có đủ không?

**Đáp:** không. Tracing bên trong không thấy cache hit; nếu cần request-level latency, phải đặt span ngoài cache hoặc có kênh đo tương đương.

**Câu 7.** Một ErrorTranslator nuốt lỗi bên trong Transaction và trả success. Nguy cơ gì?

**Đáp:** Transaction có thể xem execution đã thành công và commit khi đáng lẽ rollback. Kết quả còn tùy policy transaction cụ thể, nên cần kiểm chứng lỗi/rollback.

**Câu 8.** Pointcut chọn quá rộng làm advice chạy hai lần. Có thể sửa chỉ bằng precedence không?

**Đáp:** không chắc. Cần phân tích overlap, loại join point, generated/bridge methods và số layer instrument; sửa selection hoặc loại lặp đúng chỗ.

## 19. Kết luận

**Phối hợp nhiều aspect** không chỉ là gom các advice vào cùng join point. Ta phải phân xử **ai được vào trước, ai bao ai, ai có quyền dừng continuation, ai quan sát success/error, và bất biến nào bắt buộc đúng ở mọi đường đi**.

Bốn ý quan trọng:

1. **Precedence là quan hệ ngữ nghĩa**; số priority và thứ tự tệp chỉ có ý nghĩa nếu cơ chế AOP định nghĩa rõ.
2. **Around nesting có hai chiều vào/ra**, nên “ưu tiên cao hơn” không đồng nghĩa chạy cả before lẫn after trước.
3. **Xung đột phải được phân loại**: overlap pointcut, biến đổi dữ liệu/kết quả, ngoại lệ, state, short-circuit, security, transaction và audit.
4. **Phân xử phải kiểm chứng được**: khai báo các ràng buộc, xử lý tie/cycle, fail fast khi không an toàn và test nhánh thành công/lỗi/cache-hit/retry cùng tác dụng phụ.

**Ghi nhớ:** thứ tự aspect đúng là thứ tự bảo toàn **hợp đồng của chương trình**, không phải thứ tự khiến vài dòng log trông thuận mắt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-003">Chuỗi advice bao quanh và thứ tự tháo lớp khi trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi advice bao quanh và thứ tự tháo lớp khi trả về

## 1. Bài toán: nhiều aspect bao cùng một điểm thực thi

Trong **Aspect-Oriented Programming (AOP)**, một **pointcut** chọn các *join point* mà công nghệ AOP có thể can thiệp; **advice** là hành vi chạy ở các điểm được chọn. Một aspect có thể chứa một hay nhiều pointcut/advice. Khi **nhiều aspect hoặc nhiều around advice cùng áp dụng lên một lời gọi**, chúng thường kết hợp thành **chuỗi bao quanh (nested advice chain)**.

Hãy hình dung một thao tác `placeOrder()` cần các concern:

- **Tracing**: mở và đóng một phạm vi quan sát.
- **Authorization**: kiểm tra quyền truy cập.
- **Retry**: thử lại theo chính sách có giới hạn.
- **Audit**: ghi sự kiện bắt đầu, thành công hoặc thất bại.
- **Metrics**: đo latency.

Mỗi concern có thể được cài bằng một around advice, nhưng kết quả cuối không chỉ phụ thuộc **có những advice nào**. Nó còn phụ thuộc **thứ tự lồng nhau**: advice nào ở ngoài, advice nào ở trong, advice nào quyết định gọi `proceed()`, và advice nào xử lý **giá trị trả về hoặc ngoại lệ khi luồng rút ra**.

Ý tưởng trọng tâm:

> Với chuỗi around advice lồng nhau, đi vào theo thứ tự **ngoài → trong → target**; khi các lời gọi chuyển tiếp hoàn tất bình thường, luồng rút ra theo thứ tự **target → trong → ngoài**. Mỗi advice có thể thực hiện hành vi sau `proceed()`, biến đổi kết quả, chốt `finally`, hoặc ném lại/biến đổi ngoại lệ theo hợp đồng riêng.

Điều này thường được gọi là **nested wrappers**, **interceptor chain** hoặc “mô hình củ hành”: các lớp vào theo chiều này và tháo ra theo chiều ngược lại.

**Quan trọng:** không phải mọi engine AOP có cú pháp `proceed` giống nhau, cũng không có một quy tắc thứ tự các aspect phổ quát. Mô hình này đúng khi chuỗi đã được ghép thành các around wrappers lồng nhau. Precedence thực tế phải căn cứ vào **quy tắc và cấu hình của công cụ đang dùng**.

## 2. Các khái niệm cần phân biệt

| Khái niệm | Ý nghĩa và vai trò |
| --- | --- |
| **Join point** | Một vị trí/sự kiện được mô hình AOP hỗ trợ, ví dụ method execution |
| **Pointcut** | Quy tắc chọn tập join point |
| **Advice** | Hành vi thực hiện tại join point được chọn |
| **Around advice** | Advice có thể làm việc trước/sau, chuyển tiếp hoặc chủ động thay đổi luồng gọi |
| **`proceed()`** | Thao tác chuyển tới lớp bên trong/target theo API của công cụ |
| **Outer advice** | Advice bao bên ngoài những lớp còn lại |
| **Inner advice** | Advice được gọi bởi một outer advice và đứng gần target hơn |
| **Precedence** | Quy tắc quyết định thứ tự và quan hệ lồng các advice |
| **Unwinding** | Quá trình rút ngăn xếp khi kết quả hoặc lỗi đi ngược lên |
| **Normal completion** | Một lớp hoàn tất bằng cách trả một giá trị hợp lệ |
| **Exceptional completion** | Một lớp kết thúc bằng ngoại lệ thoát ra |
| **Short-circuit** | Advice chủ động trả kết quả hoặc ném lỗi mà không gọi lớp kế tiếp |
| **Reentrant/retry** | Lớp điều khiển có thể đi vào chuỗi dưới nhiều lần hoặc gọi tiếp nhiều lần |

**Thứ tự khai báo trong source không nhất thiết là precedence.** Một engine có thể dùng chỉ thị order, type hierarchy, độ ưu tiên pointcut, thứ tự interceptor đăng ký hoặc những quy tắc khác. Chỉ khi **đã xác định outer/inner thật sự**, ta mới suy ra thứ tự vào và thứ tự rút ra.

## 3. Mô hình củ hành với ba lớp

Giả sử có ba around advice được lồng như sau:

~~~text
Caller
  |
  v
[A OUTER]
  |
  +-- A.before
  |
  +-- proceed() ---------> [B MIDDLE]
  |                         |
  |                         +-- B.before
  |                         |
  |                         +-- proceed() ----> [C INNER]
  |                         |                     |
  |                         |                     +-- C.before
  |                         |                     +-- proceed() --> TARGET
  |                         |                     +-- C.after / finally
  |                         |
  |                         +-- B.after / finally
  |
  +-- A.after / finally
  |
  v
Caller nhận result hoặc exception
~~~

Với target trả bình thường và cả ba advice đều chuyển tiếp **đúng một lần**:

~~~text
A before
B before
C before
TARGET
C after
B after
A after
CALLER
~~~

Sự đối xứng này là hệ quả của **hàm bao hàm hàm**:

~~~text
A( B( C( TARGET ) ) )
~~~

Ta **không** gọi `A.after` ngay sau `A.before`, bởi vì `A` đang chờ `B` hoàn tất trong `proceed()`. Tương tự, `B` đang chờ `C`, và `C` đang chờ target.

Nếu advice chứa `finally`, `finally` của lớp trong sẽ thực hiện khi scope của lớp trong rời đi, **trước** khi `proceed()` tương ứng trả hoặc ném lỗi lên lớp ngoài. Vì vậy thứ tự của riêng các `finally` cũng thường **trong → ngoài**.

## 4. Nhìn qua call stack để không nhầm “tháo lớp”

Ở thời điểm cả ba advice đã vào và target đang chạy, ngăn xếp thực thi có thể trông như sau:

~~~text
TOP
  target.run()
  C.around()  -- đang chờ target
  B.around()  -- đang chờ C.proceed
  A.around()  -- đang chờ B.proceed
  caller()
BOTTOM
~~~

Khi `target.run()` trả một kết quả:

1. Kết quả trở lại **C**, không đi thẳng tới caller.
2. C có thể xử lý kết quả, trả result mới hoặc gốc; trước khi rời C thì `finally` của C chạy.
3. Result trở lại **B**; B lại có quyền xử lý result, rồi rời scope B.
4. Result trở lại **A**; A xử lý, rồi rời scope A.
5. Caller cuối cùng mới nhận giá trị.

Đây là ý nghĩa thực dụng của “thứ tự tháo lớp khi trả về”. Cùng một result **có thể có ba phiên bản** tại ba ranh giới khác nhau.

Ví dụ:

~~~text
TARGET -> "ok"
INNER  -> "ok!"
OUTER  -> "[ok!]"
CALLER -> "[ok!]"
~~~

Nếu advice chỉ quan sát mà không biến đổi thì các ranh giới có thể thấy cùng giá trị, nhưng thứ tự callback vẫn đảo chiều.

### 4.1. Vì sao `after` khác `finally`?

Trong Java:

~~~java
try {
    Object result = next.proceed();
    observeReturn(result);
    return result;
} finally {
    releaseScope();
}
~~~

Đây là đoạn minh họa, chưa độc lập vì `next`, `observeReturn` và `releaseScope` là các thành phần do ứng dụng cung cấp.

Nếu `next.proceed()` trả bình thường, `observeReturn` chạy rồi `finally` mới giải phóng scope. Nếu `next.proceed()` ném exception, `observeReturn` **bị bỏ qua**, nhưng `releaseScope` trong `finally` vẫn chạy khi phạm vi rời đi.

Vì thế:

- **After returning logic**: chạy trên đường bình thường của lớp mà nó quan sát.
- **After throwing logic**: chạy trên đường exceptional exit được bắt hoặc được engine cung cấp.
- **Finally-style logic**: chạy khi phạm vi kết thúc theo cả hai đường thông thường của Java.

Không nên gọi mọi đoạn code “sau target” là after-returning, vì `finally` có ngữ nghĩa khác.

## 5. Đường trả về bình thường: outer thấy kết quả đã được inner xử lý

Giả sử:

~~~text
A around: add "[" + value + "]"
B around: uppercase(value)
C around: add "!"
TARGET:   return "hello"
~~~

Với cách lồng `A(B(C(TARGET)))`:

~~~text
TARGET produces "hello"
C sees "hello", returns "hello!"
B sees "hello!", returns "HELLO!"
A sees "HELLO!", returns "[HELLO!]"
CALLER sees "[HELLO!]"
~~~

Kết quả **không tương đương** với việc đổi thứ tự tự do. Nếu `uppercase` và thêm ngoặc có những quy tắc riêng, hai phép biến đổi có thể hoặc không thể giao hoán. Đặc biệt các phép biến đổi như mã hóa, nén, ký số hoặc chuẩn hóa dữ liệu thường **không giao hoán**.

Đây là lý do precedence phải là một **phần của hợp đồng**, không phải chi tiết do runtime tình cờ quyết định.

### 5.1. Giá trị target, giá trị observed và giá trị caller

Có thể đặt ba tên:

- `rawResult`: giá trị target trả.
- `innerResult`: giá trị sau một hoặc nhiều advice bên trong.
- `callerResult`: giá trị sau advice ngoài cùng.

Một advice ở lớp trong có thể ghi `rawResult=hello`; lớp ngoài log `innerResult=HELLO!`; caller thấy `[HELLO!]`.

Nếu audit cần ghi **kết quả đã được công bố tới caller**, nên đặt audit ở ranh giới quan sát thích hợp; nếu audit cần ghi **result gốc của target**, phải đặt bên trong lớp biến đổi phù hợp. Không dùng tên `originalResult` cho kết quả vốn đã đi qua các transformer khác.

### 5.2. Tác dụng phụ trên object mutable

Ngay cả khi advice “trả cùng object” nhưng thay đổi nội dung của `List` hoặc DTO mutable, giá trị caller quan sát có thể đã thay đổi. Đây là một **biến đổi trạng thái**, không còn là quan sát thuần túy.

Với advice chỉ đo/log, ưu tiên không sửa result và không gọi `toString()` có thể gây I/O, lộ dữ liệu hoặc ném exception; chỉ ghi metadata được phép.

## 6. Đường ngoại lệ: tháo lớp cũng đi từ trong ra ngoài

Giả sử target ném `IllegalArgumentException`. Nếu cả ba around advice đều chỉ ghi lỗi rồi **ném lại nguyên exception**:

~~~text
A before
B before
C before
TARGET -> throws IllegalArgumentException
C catches/observes error
C finally
B catches/observes error
B finally
A catches/observes error
A finally
CALLER catches IllegalArgumentException
~~~

Điểm khác biệt so với normal return:

- Những câu lệnh chỉ chạy khi `proceed()` trả bình thường bị **bỏ qua**.
- Các `catch` thích hợp có thể quan sát hoặc biến đổi exception.
- Các `finally` được thực thi khi rời scope (trừ những tình huống dừng tiến trình bất thường không bảo đảm cleanup).
- Một lớp ngoài có thể nhận **ngoại lệ đã biến đổi** bởi lớp trong, không nhất thiết lỗi gốc từ target.
- Nếu một lớp **bắt lỗi và trả fallback**, những lớp ngoài hơn có thể nhìn thấy **normal return** thay vì exceptional exit.

Vì vậy lời khẳng định “target lỗi thì tất cả after-throwing advices chắc chắn chạy” là **quá mạnh**. Nếu một advice bên trong phục hồi bằng fallback, lớp ngoài không còn thấy exception ở ranh giới `proceed` tương ứng.

### 6.1. Ví dụ phục hồi thay đổi kịch bản

~~~text
OUTER audit
  INNER fallback
    TARGET -> throws IOException

INNER catch -> return "cached-value"
OUTER proceed() returns "cached-value"
OUTER after-returning runs
CALLER receives "cached-value"
~~~

Nếu audit bên ngoài ghi “target thành công” chỉ từ việc `proceed()` trả bình thường, nó có thể diễn giải sai **nghiệp vụ gốc đã thất bại nhưng được fallback khôi phục**. Cần phân biệt `normal completion of wrapper` với `business success of target`.

### 6.2. Khi `finally` tự ném exception

Nếu một advice đang tháo lớp vì lỗi nghiệp vụ mà `finally` tự ném lỗi metrics/cleanup, caller có thể nhận **lỗi mới thay vì lỗi gốc**. Dọn dẹp không tự an toàn chỉ vì đặt trong `finally`.

Với tài nguyên Java `AutoCloseable` dùng qua `try-with-resources`, exception do `close()` có thể được gắn **suppressed** khi đã có exception chính. Nhưng nếu tự viết `finally` ném một exception khác, hợp đồng và kết quả có thể khác. Hãy lựa chọn chính sách rõ, thử cả đường success và failure.

## 7. Thứ tự cố định của cấu trúc và thứ tự tùy thuộc precedence

Đối với một chuỗi wrapper **đã xây dựng xong**, Java call stack và `try/finally` tuân theo thứ tự vào/rút ra tương ứng. Nhưng câu hỏi **advice nào là outer** không có một câu trả lời toàn cục cho tất cả AOP engines.

Có thể tồn tại:

- Explicit order/priority của advice hoặc aspect.
- Thứ tự các interceptor khi đăng ký vào chain.
- Quy tắc phân giải ưu tiên khi nhiều aspect cùng chọn một join point.
- Thứ tự của những method được sinh hoặc woven vào call/execution.
- Quy tắc riêng giữa các advice cùng aspect hoặc cùng precedence.
- Trường hợp thứ tự không được bảo đảm, có thể thay đổi giữa versions/configs.

**Không suy ra precedence từ tên class, tên file, thứ tự khai báo hoặc thứ tự dependency** trừ khi tài liệu công nghệ thực sự quy định. Đặc biệt không coi số priority nhỏ luôn là outer: một số hệ thống định nghĩa chiều ưu tiên khác.

Khi tài liệu dự án ghi `Trace → Auth → Business`, cần nói rõ đó là **thứ tự vào** hay **thứ tự lúc rút ra**. Một cách biểu diễn ít mơ hồ:

~~~text
OUTER Trace {
    MIDDLE Auth {
        INNER Metrics {
            Business
        }
    }
}
~~~

Khi đó ý nghĩa “before” và “after” đã rõ mà không cần biết API sắp xếp của công cụ nào.

## 8. Viết code rõ ràng theo cấu trúc `try / catch / finally`

Mẫu ý niệm cho một around advice:

~~~java
Object around(Proceed next) throws Throwable {
    before();
    try {
        Object value = next.proceed();
        afterReturning(value);
        return value;
    } catch (Throwable error) {
        afterThrowing(error);
        throw error;
    } finally {
        cleanup();
    }
}
~~~

**Đây là Java pseudocode minh họa**: `Proceed` và các hàm callback chưa được định nghĩa. Code chạy độc lập đầy đủ ở phần 10.

Ngữ nghĩa cần hiểu:

1. `before()` chạy trước vùng `try`. Nếu chính `before()` ném lỗi, `finally` bên trong cấu trúc này **chưa được vào**, nên không chạy. Nếu cần cleanup sau khi trước-call khởi tạo tài nguyên một phần, hãy bố trí `try` bao quanh phần cần bảo đảm.
2. `next.proceed()` là ranh giới gọi lớp trong. Nó có thể return hoặc throw.
3. `afterReturning(value)` trong `try` chỉ chạy nếu `proceed` return.
4. Nếu **chính `afterReturning` ném exception**, `catch (Throwable)` cũng sẽ bắt nó; không được nhầm với “target throw”. Trong design cần phân biệt nguồn lỗi hoặc chia các vùng `try/catch` cho rõ.
5. `cleanup()` trong `finally` chạy khi scope rời đi theo cơ chế Java bình thường.
6. Nếu `cleanup` lỗi, caller có thể mất result/exception ban đầu; cần policy cho cleanup.

### 8.1. Tránh catch mọi `Throwable` chỉ để ghi log

Một ví dụ chạy thử có thể bắt `Throwable` để **ném lại nguyên lỗi** và quan sát thứ tự. Trong production, `Error` cũng là `Throwable`: không nên nuốt `Error` hoặc biến mọi lỗi thành success. Nếu advice chỉ đo duration, có thể chỉ cần `try/finally` thay vì catch rộng.

### 8.2. `return` trong `finally` là một bẫy

~~~java
try {
    return "business-result";
} finally {
    return "overridden"; // Không làm như thế.
}
~~~

Đây là minh họa Java; nó cho thấy `return` trong `finally` có thể **thay thế kết quả trước đó**; nếu `try` đang ném exception, nó còn có thể **nuốt luôn exception**. Around advice cleanup nên thực hiện giải phóng/quan sát, không dùng `return` trong `finally`.

## 9. Vai trò của lời gọi `proceed`

Trong nhiều cách hiện thực AOP, `proceed` đại diện cho **thao tác chuyển tiếp vào thành phần bên trong chuỗi**. Nó không luôn trực tiếp gọi target ngay; trước target có thể còn nhiều lời gọi advice khác.

Ba trường hợp:

### 9.1. Gọi đúng một lần

~~~text
OUTER before
  proceed() --> INNER --> TARGET
OUTER after
~~~

Đây là dạng thường dùng cho logging, metrics và kiểm quyền khi đã cho phép truy cập.

### 9.2. Không gọi lần nào: short-circuit

~~~text
CACHE checks key
  hit -> return cached value immediately
  miss -> proceed()
~~~

Nếu cache nằm giữa OUTER và INNER, một cache hit có thể khiến **INNER và target không chạy**. Nhưng những advice **ngoài** cache vẫn có thể được kích hoạt và thấy cache result.

Vì vậy số lần `afterReturning` của OUTER không phải số lần target đã thực thi, trừ khi chain đảm bảo mọi đường đều gọi target.

### 9.3. Gọi nhiều lần: retry hoặc fan-out

~~~text
RETRY around
  attempt 1 -> proceed() -> exception
  attempt 2 -> proceed() -> value
  return value
~~~

Một outer advice bao retry có thể được vào một lần, trong khi inner advice và target chạy **hai lần**. Số lượng log/metrics tùy vị trí của advice:

- Timer **ngoài retry** đo toàn bộ request bao gồm mọi attempt và backoff.
- Timer **bên trong retry** có thể ghi một mẫu cho mỗi attempt.
- Audit **ngoài retry** có thể ghi outcome cuối; audit **bên trong** có thể ghi mỗi attempt.

Không giả định mọi công cụ AOP cho phép gọi `proceed` nhiều lần hoặc thay đổi arguments giống nhau; đó là capability/hợp đồng của engine và tác vụ. Với method có side effects, retry sai có thể tạo giao dịch trùng.

## 10. Ví dụ Java 17 hoàn chỉnh: outer, inner, ngoại lệ và short-circuit

Để học chính xác **vòng vào và thứ tự tháo lớp**, chương trình sau dùng các hàm Java lồng nhau, **không phụ thuộc vào framework AOP**:

- `Proceed` biểu thị lời gọi sang lớp kế tiếp.
- `Around` là hợp đồng của từng advice.
- `Layer` ghi `enter`, `returning`, `throwing` và `finally`.
- `execute()` lắp danh sách theo **thứ tự từ ngoài vào trong**, nhưng **duyệt ngược khi xây** để tạo được đúng chain.
- Lớp INNER thêm dấu `!` vào result; lớp OUTER thêm ngoặc vuông `[ ]`.
- Có một ca target ném `IllegalArgumentException` và một ca `CACHE` chủ động trả kết quả mà **không gọi `next.proceed()`**.
- Bộ đếm `TARGET_CALLS` chứng minh lượt cache hit không gọi target.

Tạo file `AroundChainDemo.java`:

~~~java
import java.util.List;
import java.util.function.UnaryOperator;

public final class AroundChainDemo {
    @FunctionalInterface
    interface Proceed { String proceed() throws Throwable; }

    @FunctionalInterface
    interface Around { String apply(Proceed next) throws Throwable; }

    static final class Core {
        int calls;
        String greet(String input) {
            calls++;
            System.out.println("TARGET greet " + input);
            if ("bad".equals(input)) {
                throw new IllegalArgumentException("invalid input");
            }
            return "core:" + input;
        }
    }

    record Layer(String name, UnaryOperator<String> transform) implements Around {
        @Override
        public String apply(Proceed next) throws Throwable {
            System.out.println(name + " enter");
            try {
                String result = next.proceed();
                System.out.println(name + " returning " + result);
                return transform.apply(result);
            } catch (Throwable error) {
                System.out.println(name + " throwing "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println(name + " finally");
            }
        }
    }

    static String execute(List<Around> outerToInner, Proceed core) throws Throwable {
        Proceed chain = core;
        for (int i = outerToInner.size() - 1; i >= 0; i--) {
            Around layer = outerToInner.get(i);
            Proceed next = chain;
            chain = () -> layer.apply(next);
        }
        return chain.proceed();
    }

    public static void main(String[] args) throws Throwable {
        Core core = new Core();
        Around outer = new Layer("OUTER", value -> "[" + value + "]");
        Around inner = new Layer("INNER", value -> value + "!");
        List<Around> normalChain = List.of(outer, inner);

        System.out.println("CALLER " +
                execute(normalChain, () -> core.greet("A")));

        try {
            execute(normalChain, () -> core.greet("bad"));
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER caught " + error.getMessage());
        }

        Around cacheHit = next -> {
            System.out.println("CACHE short-circuit");
            return "stored:B";
        };
        System.out.println("CALLER " +
                execute(List.of(outer, cacheHit, inner),
                        () -> core.greet("B")));
        System.out.println("TARGET_CALLS " + core.calls);
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AroundChainDemo.java
java AroundChainDemo
~~~

Đầu ra **đã đối chiếu bằng biên dịch và chạy thực**:

~~~text
OUTER enter
INNER enter
TARGET greet A
INNER returning core:A
INNER finally
OUTER returning core:A!
OUTER finally
CALLER [core:A!]
OUTER enter
INNER enter
TARGET greet bad
INNER throwing IllegalArgumentException
INNER finally
OUTER throwing IllegalArgumentException
OUTER finally
CALLER caught invalid input
OUTER enter
CACHE short-circuit
OUTER returning stored:B
OUTER finally
CALLER [stored:B]
TARGET_CALLS 2
~~~

### 10.1. Trường hợp thành công: giải thích từng dòng

Lớp OUTER thực hiện `enter` trước rồi gọi `next.proceed()`; kết quả là lớp INNER bắt đầu. INNER gọi core và thấy `"core:A"`. INNER in `returning core:A`, tạo result mới `"core:A!"`, chạy `finally` của INNER rồi trả sang OUTER. OUTER nhận **result đã được INNER biến đổi**, in `returning core:A!`, tạo `"[core:A!]"`, chạy `finally` của OUTER rồi caller mới in `CALLER [core:A!]`.

Điểm cần thuộc: **`finally` của INNER xuất hiện trước `returning` của OUTER.** Không có một “vòng after của tất cả advice” chạy tách khỏi call stack; đó là các hàm lồng nhau đang lần lượt trả điều khiển.

### 10.2. Trường hợp ngoại lệ

Lần gọi `core.greet("bad")` tăng counter và ném `IllegalArgumentException`. INNER ghi `throwing`, thực thi `finally` rồi ném lại. OUTER nhận lỗi từ `proceed`, ghi `throwing` và `finally` rồi cũng ném lại. Caller bắt đúng `IllegalArgumentException` gốc.

**Không có dòng `returning`** ở cả INNER lẫn OUTER bởi chúng không nhận được normal result. Nếu một advice xử lý exception và trả fallback, kịch bản của lớp ngoài có thể thay đổi thành normal completion.

### 10.3. Trường hợp short-circuit

Chuỗi thứ ba được xây `OUTER → CACHE → INNER → TARGET`. CACHE trả `"stored:B"` mà không gọi next:

~~~text
OUTER enter
CACHE short-circuit
OUTER returning stored:B
OUTER finally
CALLER [stored:B]
~~~

Không có `INNER enter` và cũng không có `TARGET greet B`. Vì core chỉ từng chạy với `A` và `bad`, `TARGET_CALLS 2`.

**Hệ quả:** một advice ngoài cache không nên lấy “số lần được gọi” làm “số lần target đã thực thi”. Các chính sách audit/metrics phải xác định event đang ghi là **invocation tới public boundary** hay **execution thực tế của target**.

### 10.4. Đây có phải ví dụ bytecode weaving không?

Không. `execute()` xây chain theo code Java, để quan sát trực tiếp quy tắc lồng và rút ra. Một công nghệ AOP có thể hiện thực cấu trúc tương đương bằng proxy hoặc woven bytecode, nhưng chương trình này **không sửa class file hoặc tự chọn join point theo pointcut language**.

Điểm quan trọng được kiểm chứng là **ngữ nghĩa hàm bao quanh và Java call stack**, không phải cú pháp của một engine đặc biệt. Khi chuyển sang công nghệ thật, cần thêm integration test xác minh **precedence và join point coverage**.

## 11. Tại sao code lắp chain phải duyệt ngược?

Trong ví dụ:

~~~java
Proceed chain = core;
for (int i = outerToInner.size() - 1; i >= 0; i--) {
    Around layer = outerToInner.get(i);
    Proceed next = chain;
    chain = () -> layer.apply(next);
}
~~~

Danh sách người đọc cung cấp là `[OUTER, INNER]` theo thứ tự **muốn nhìn thấy khi bắt đầu gọi**. Nhưng một wrapper cần nhận **lớp ở bên trong** làm next. Vì thế:

1. Khởi đầu `chain = target`.
2. Lấy INNER từ cuối danh sách và tạo `INNER(target)`.
3. Lấy OUTER và tạo `OUTER(INNER(target))`.
4. Gọi outermost closure.

Nếu duyệt xuôi mà mỗi bước bọc ngoài chain hiện có, bạn sẽ tạo `INNER(OUTER(target))` thay vì mong muốn. Trong các interceptor frameworks có thể có quy ước order khác; hãy xác minh cách chúng lắp chain thực sự, không áp công thức duyệt ngược máy móc lên mọi API.

### 11.1. Closure cần chụp next ở mỗi vòng

`Proceed next = chain` tạo biến cục bộ cố định cho lambda của vòng hiện tại. Nếu vô tình dùng một tham chiếu mutable chia sẻ đến `chain` sau khi thay nó, closure có thể gọi nhầm chính mình hoặc lớp không mong muốn, gây recursion vô hạn. Chú ý này là vấn đề **kỹ thuật ghép wrapper**, không phải một quy luật AOP mới.

### 11.2. Chuỗi trên được xây lại mỗi lần hay tái sử dụng?

Ví dụ dựng chain mỗi lần `execute` được gọi. Trong ứng dụng thực, engine có thể xây interceptor chain một lần rồi tái sử dụng cho nhiều invocations. Nếu handler giữ trạng thái mutable dùng chung, thread-safety và lifecycle phải được xem xét. **Thứ tự vào/rút ra theo nesting không thay đổi**, nhưng số object advice và cấu hình runtime có thể khác.

## 12. Nhiều loại advice: before, after-returning, after-throwing, finally

Một AOP engine có thể cung cấp cả những advice chuyên dụng ngoài `around`. Khi chúng được kết hợp tại một join point, ý nghĩa thông thường:

| Hành vi | Khi nào chạy về mặt ngữ nghĩa? |
| --- | --- |
| **Before** | Trước khi tiến vào công việc mà advice quan sát |
| **After returning** | Khi ranh giới đang quan sát hoàn tất bình thường |
| **After throwing** | Khi ranh giới đang quan sát hoàn tất ngoại lệ |
| **After/finally** | Khi rời ranh giới theo cả normal/exceptional exit nếu engine hỗ trợ |
| **Around** | Có thể kiểm soát chuyển tiếp và các hành vi ở cả hai phía |

Tuy nhiên, thứ tự chính xác giữa advice **của những aspect khác nhau** vẫn do precedence quyết định. Không nên suy ra: “mọi before chạy trước mọi around” hoặc “mọi after-returning chạy sau mọi finally” nếu không hiểu chúng được dệt/bọc thế nào.

### 12.1. Advice ngoài và advice trong có thể thấy outcome khác nhau

Giả sử INNER around catch exception và trả fallback. Ở ranh giới INNER có exceptional target; ở ranh giới OUTER có normal return. Một after-throwing gắn vào target execution và một after-returning gắn vào outer call boundary **có thể đều hợp lệ** trong cùng một workflow.

Vì vậy một dashboard gộp mọi `afterReturning` thành “target success count” có thể báo sai. Hãy gắn outcome với **loại join point và ranh giới cụ thể**.

### 12.2. Một advice sau trả về có thể ném lỗi

Nếu target return bình thường, nhưng after-returning callback tự ném exception, lớp ngoài có thể thấy exceptional completion. Nếu business response đã được tạo trước đó, caller vẫn có thể nhận lỗi callback thay vì result nếu callback chạy đồng bộ trong chain. Ngược lại, nếu callback chỉ được đưa vào một hàng đợi async, caller có thể nhận result trước khi callback lỗi. Không áp một kịch bản chung cho mọi tool.

## 13. Phạm vi quan sát khi có cache, authorization, retry và metrics

Một ví dụ có nhiều concern:

~~~text
TRACE (outer)
  AUTH
    CACHE
      RETRY
        METRICS (inner)
          BUSINESS
~~~

**Đây chỉ là một thứ tự minh họa**, không phải thiết kế nên áp dụng máy móc.

### 13.1. Authorization ở ngoài cache hay trong cache?

Nếu `AUTH` **ngoài** `CACHE`, mọi request phải đi qua kiểm quyền trước khi cache có thể trả. Nếu chuyển cache ra ngoài auth, cache hit có thể short-circuit trước khi kiểm quyền, làm mất bước enforcement. Với dữ liệu nhạy cảm, thứ tự này có thể tạo lỗ hổng, trừ khi cache key và ranh giới bảo mật đã được thiết kế đầy đủ.

Không dùng `CACHE` short-circuit để bỏ qua một policy bắt buộc ngoài ý định.

### 13.2. Metrics ngoài hay trong retry?

- **Metrics ngoài retry:** báo elapsed end-to-end, kể cả backoff và nhiều lần thử.
- **Metrics trong retry:** mỗi attempt có metric riêng.
- **Metrics ngoài cache:** có thể đo cả cache hit và cache miss.
- **Metrics trong cache:** bỏ qua các lượt hit nếu cache không gọi next.

Nên dùng tên như `request_duration` và `attempt_duration` thay vì gọi cả hai là `method_duration` không phân biệt.

### 13.3. Audit có được chạy khi cache hit không?

Nếu audit cần ghi **mỗi yêu cầu API**, hãy đặt ở ngoài lớp có thể short-circuit. Nếu audit cần ghi **mỗi target execution**, đặt ở tầng nhìn thấy business thật và kiểm tra đường bypass. Hai mục tiêu là khác nhau; không có một vị trí luôn đúng.

### 13.4. Tracing và span nesting

Một trace span ở outer layer có thể bao các span con ở inner layers. Khi scope đóng theo thứ tự **inner trước, outer sau**, cây span có cấu trúc hợp lý. Nhưng với async operations, nếu method return Future trước task completion, một `finally` trên method có thể đóng span quá sớm; cần tách method invocation scope và task completion scope theo contract.

### 13.5. Transaction, retry và tính idempotent

Nếu target có side effects, retry có thể lặp lại việc ghi dữ liệu. Vị trí transaction boundary và retry quyết định mỗi attempt có transaction riêng hay toàn bộ workflow chung một scope. Điều này ảnh hưởng consistency và idempotency, không thể quyết định chỉ từ câu “outer hơn sẽ after sau hơn”. Cần hiểu khả năng rollback, external I/O và giới hạn retry.

## 14. Thứ tự advice không giao hoán: ví dụ phân tích

Hai transformer:

~~~text
A: encrypt(result)
B: compress(result)
~~~

Các chuỗi thường không tương đương:

~~~text
encrypt(compress(data))
compress(encrypt(data))
~~~

Nén sau khi mã hóa thường rất khác nén trước khi mã hóa. Tương tự:

- Normalize trước hay sau validate có thể thay outcome.
- Masking trước hay sau audit thay thông tin được ghi.
- Authorization trước hay sau cache thay mức bảo vệ.
- Retry ngoài hay trong transaction thay số lần/thời điểm commit.
- Timeout ngoài hay trong retry thay deadline áp dụng.

Vì vậy, với nhiều aspect, phải viết **thứ tự cấu trúc có ý nghĩa nghiệp vụ** rồi kiểm thử. Nếu hai advice là phép toán giao hoán và stateless, thứ tự có thể ít quan trọng hơn; nhưng đừng suy từ hai ví dụ đơn giản rằng mọi advice có thể sắp xếp tùy ý.

## 15. Runtime exceptions, cancellation và resource cleanup

### 15.1. Kết quả `null` và phương thức `void`

Một method `String load()` có thể **trả `null` bình thường**; `null` không có nghĩa target ném exception. Lớp after-returning nên xử lý `null` an toàn.

Một method `void reset()` **không có return value**, nhưng vẫn có normal completion hoặc exceptional completion. Một adapter proxy dùng `Object` có thể biểu diễn `void` qua `null` theo hợp đồng Reflection; điều đó không biến `void` thành `String` result.

### 15.2. Primitive và result transformation

Nếu method khai báo `int` mà around wrapper trả `null` qua adapter, việc unboxing có thể lỗi. Advice có nhiệm vụ **biến đổi result** phải tôn trọng return type của join point. Với method generic/bridge, cần xác minh metadata thực tế ở ranh giới call/execution.

### 15.3. Cancellation và timeout

Hủy task có thể thể hiện bằng một exception, một trạng thái Future hoặc việc dừng chuyển tiếp. Một around advice đồng bộ không tự nhìn được mọi sự kiện cancellation của tác vụ bất đồng bộ. `finally` lúc method return chỉ đóng scope lời gọi đồng bộ; nó không chờ tất cả công việc con hoàn tất.

### 15.4. Cleanup thất bại

Một `finally` ghi metric qua network có thể mất thời gian hoặc ném lỗi, làm caller nhận exception không phải từ target. Với advice quan sát, thường cần policy giảm ảnh hưởng lên nghiệp vụ, nhưng những concern bắt buộc như audit có thể chọn fail-closed. Chính sách phải minh bạch và có tests.

## 16. Lời gọi lồng nhau không đồng nghĩa một lời gọi có nhiều aspect

Có hai nguồn “lồng” khác nhau:

1. **Advice nesting:** nhiều wrappers cùng áp dụng lên **một** join point hoặc ranh giới call.
2. **Business nesting:** target A gọi target B, mỗi target lại có chain advice riêng.

Ví dụ:

~~~text
Caller -> Aouter -> Ainner -> methodA()
                               |
                               +-> Bouter -> Binner -> methodB()
~~~

Đầu ra tổng thể có thể xen kẽ các events của hai chains. Việc `Bouter` chạy trong lúc `Ainner` chưa return không có nghĩa engine đảo precedence; đó là do **business method A gọi method B**.

Tương tự, recursion có thể tạo nhiều lần vào **cùng một chain**. Hãy gắn `invocationId`, `parentSpanId` hoặc call-depth với logs khi điều tra.

### 16.1. Đếm advice entry và target execution riêng

Một lớp outer được vào một lần có thể gọi inner hai lần do retry. Một cache có thể được vào một lần rồi không gọi inner lần nào. Do đó:

~~~text
outer advice entries = 1
inner advice entries = 0, 1 hoặc nhiều lần
target executions    = 0, 1 hoặc nhiều lần
~~~

Tương quan giữa ba số đếm phụ thuộc cấu trúc và logic, không thể mặc định đều bằng nhau.

### 16.2. Double weaving và hai pointcut cùng chọn

Nếu một class đã được instrument ở nhiều tầng, hai advice cùng loại có thể chạy **ngoài ý định**, tạo hai event giống nhau. Nhưng hai lời gọi after-returning khác nhau không tự chứng minh double weaving: có thể thuộc hai joins (call/execution), hai nghiệp vụ lồng nhau hoặc hai policies hợp lệ.

Chẩn đoán cần kết hợp aspect identity, join point kind, signature, invocation ID, target count và instrumentation layer.

## 17. Khi proxy hoặc weaving quyết định độ phủ chuỗi

Một chuỗi advice **chỉ tồn tại trên những đường mà hệ thống can thiệp được**.

- **Interface proxy:** caller phải gọi qua proxy để handler nhận lời gọi tương ứng.
- **Subclass proxy:** còn bị giới hạn bởi khả năng override và dispatch.
- **Bytecode weaving:** method execution có thể được instrument ngay trong body nếu công cụ hỗ trợ và class nằm trong phạm vi.
- **Load-time weaving:** cần transformer hoạt động trước khi class được định nghĩa hoặc cơ chế retransformation được hỗ trợ riêng.
- **Self-invocation:** gọi `this.method()` có thể bỏ qua proxy ngoài; một execution join point đã woven có thể vẫn chạy.

Cần phân biệt **độ phủ** và **thứ tự**: pointcut khớp rồi được engine can thiệp là điều kiện đầu; precedence mới xác định chain trên đường ấy. Nếu một lớp bị bypass do gọi raw target, không có “after” nào của lớp ấy để tháo ra.

Không giả định mọi AOP engine đều hỗ trợ cùng các loại method `private`, `final`, `static`, constructor, field access hoặc exception handler.

## 18. Thread safety và state trong nhiều advice

Khi chain được tái sử dụng, các advice có thể phục vụ nhiều threads đồng thời. Lỗi phổ biến:

~~~java
class UnsafeTimingAdvice {
    long started;

    void before() {
        started = System.nanoTime();
    }

    long elapsed() {
        return System.nanoTime() - started;
    }
}
~~~

Đây là đoạn mã minh họa, không phải chương trình có `main`. Nếu thread A và B gọi `before()` xen kẽ trên cùng instance, `elapsed()` có thể dùng mốc của thread khác. **Thứ tự vào/rút ra trong một invocation không tự tạo thread safety giữa các invocations.**

Một thiết kế tốt dùng **local variable per call**, hoặc per-invocation context. Cẩn trọng với:

- Shared metric counters: dùng cấu trúc atomic/concurrent phù hợp.
- Mutable transform state: không chia sẻ object không thread-safe nếu handler dùng chung.
- `ThreadLocal`: phải restore/remove trong `finally` khi thread pool tái sử dụng thread.
- Async propagation: `ThreadLocal` không tự truyền qua mọi executor/task.
- Retry: nếu một advice được reentrant, state của lần thử trước không được làm bẩn lần sau.
- Cleanup: dừng advice mới và chờ in-flight operations trước khi đóng tài nguyên chung.

Một chain có thể **lồng đúng** nhưng **sai kết quả** vì race hoặc state leakage. Kiểm thử concurrency phải bổ sung cho test order đơn luồng.

## 19. Kiểm thử thứ tự theo một hợp đồng rõ

### 19.1. Ma trận kịch bản

| Kịch bản | Kỳ vọng |
| --- | --- |
| Hai advice, target trả bình thường | `outer.enter → inner.enter → target → inner.return/finally → outer.return/finally` |
| Target ném lỗi | `inner.throwing/finally` trước `outer.throwing/finally`, nếu cả hai chỉ rethrow |
| INNER biến đổi result | OUTER nhận result đã biến đổi |
| OUTER biến đổi result | Caller nhận result sau OUTER |
| Cache hit nằm giữa OUTER và INNER | OUTER chạy; INNER và target không chạy |
| Authorization ở ngoài cache, bị từ chối | Cache và target không chạy |
| Retry bao ngoài INNER | INNER và target có thể chạy nhiều lần |
| Metrics ngoài retry | Đo cả các attempts và khoảng chờ |
| Metrics trong retry | Có metric cho từng attempt được phép |
| INNER phục hồi exception | OUTER có thể thấy normal return |
| `finally` tự ném lỗi | Xác minh chính sách bảo toàn/suppression ngoại lệ |
| Target return `null` | Normal return, không nhầm exceptional |
| Return `CompletableFuture` pending | Method wrapper rút ra trước task completion |
| Caller bypass proxy | Không kỳ vọng proxy handler chạy |
| Class đã woven | Xác minh đúng join points thực sự instrument |
| Nhiều requests đồng thời | Per-invocation state không ghi đè nhau |

### 19.2. Tránh assertion chỉ dựa vào dòng log

Một test mạnh hơn nên dùng **event list** được ghi theo thứ tự:

~~~text
expectedNormal = [
    "OUTER.enter",
    "INNER.enter",
    "TARGET.call",
    "INNER.return",
    "INNER.finally",
    "OUTER.return",
    "OUTER.finally"
]
~~~

Đây là **pseudocode assertion**. Trên Java, có thể dùng `List<String> events` và so sánh với danh sách kỳ vọng, hoặc giữ stdout được ghi nhận tất định như ví dụ đã chạy. Khi test trong môi trường đồng thời, không nên đòi event global có thứ tự tuyệt đối nếu nhiều invocation chạy xen kẽ; cần gom theo invocation ID.

### 19.3. Kiểm đúng thứ tự lẫn đúng phạm vi

Unit test `Layer.apply()` chỉ chứng minh code trong advice. Với AOP engine thật, phải có integration test xác minh **pointcut chọn đúng, precedence đúng, lời gọi qua đúng proxy/weaving boundary**, sau đó mới so chuỗi events. Nếu lớp không được instrument, nó sẽ không thể có `enter/return/finally` trong test dù function logic hoàn toàn chính xác.

## 20. Checklist thiết kế nhiều aspect trước khi triển khai

1. **Vẽ chain có tên lớp ngoài/trong.** Không chỉ liệt kê danh sách aspect mà không nói thứ tự.
2. **Định nghĩa event đang quan sát:** request, attempt, target execution hay task completion?
3. **Chọn precedence theo mục tiêu nghiệp vụ:** authorization, cache, retry, transaction, tracing và audit có ranh giới khác nhau.
4. **Xác định từng advice có gọi `proceed` không:** có thể short-circuit hoặc retry không?
5. **Đặt chính sách biến đổi result rõ:** lớp nào được phép sửa return value/exception?
6. **Kiểm cả normal lẫn exceptional path:** `afterReturning` khác `finally`.
7. **Định nghĩa cleanup:** dọn theo thứ tự trong→ngoài; không để lỗi sink che lỗi target ngoài chủ đích.
8. **Không dùng tên “after” mơ hồ:** phân biệt after-returning, after-throwing và finally-style.
9. **Kiểm tra pointcut coverage:** proxy bypass, self-invocation, method modifiers và phạm vi weaving.
10. **Kiểm soát state lifetime:** shared handler không chứa mutable per-call state.
11. **Phân biệt sync và async:** khi Future return, scope nào thực sự đã hoàn tất?
12. **Đo overhead:** mỗi wrapper, reflection, logging và I/O có chi phí.
13. **Đếm target invocations:** đặc biệt với retry và cache.
14. **Tránh double instrumentation:** nếu hai policies tương đương ở hai tầng, xác định ownership.
15. **Integration test precedence thật:** thứ tự tùy engine không được suy từ thứ tự source.
16. **Tài liệu hóa điều kiện lỗi:** fallback/timeout/denial và expected caller outcome.

## 21. Những lỗi hiểu sai thường gặp

1. **“After của OUTER chạy trước after của INNER vì OUTER được gọi trước.”** Sai với nested wrappers bình thường; OUTER phải chờ INNER trả.
2. **“Mỗi advice after đều chạy dù target throw.”** Sai với after-returning; `finally` mới là cơ chế cleanup cho cả hai đường thông thường.
3. **“Một `afterReturning` luôn thấy raw target result.”** Sai nếu lớp trong đã biến đổi result.
4. **“Một `afterThrowing` ngoài luôn thấy target exception.”** Sai nếu lớp trong đã catch và trả fallback.
5. **“Cả ba `finally` chạy sau khi caller nhận result.”** Sai: `finally` xảy ra trước khi từng layer thực sự trả điều khiển cho caller.
6. **“Một `proceed()` chắc chắn gọi ngay target.”** Sai: có thể còn nhiều advice bên trong.
7. **“Mọi around advice đều bắt buộc gọi `proceed()` đúng một lần.”** Tùy engine/policy; cache có thể bỏ qua, retry có thể lặp.
8. **“Outer advice entries bằng business calls.”** Không khi short-circuit, retry hoặc multiple call paths.
9. **“Priority nhỏ hơn luôn ở ngoài.”** Phụ thuộc quy ước công cụ.
10. **“Đổi thứ tự aspect chỉ đổi vị trí log.”** Có thể đổi permission, outcome, value và consistency.
11. **“Cleanup trong `finally` không bao giờ hỏng.”** Chính cleanup có thể ném lỗi, block hoặc side effect.
12. **“`return` trong `finally` là cách giữ kết quả.”** Nó có thể nuốt result/exception trước đó.
13. **“Biến đổi object mutable là quan sát thuần túy.”** Nó có thể tác động caller.
14. **“Method trả Future là toàn workflow đã complete.”** Không; task có thể còn chạy.
15. **“Hai log giống nhau chứng minh double weaving.”** Có thể do các join points hợp lệ hoặc events delivery trùng.
16. **“Thử advice trực tiếp chứng minh precedence runtime.”** Không; phải test chain/instrumentation thật.
17. **“Một chain đúng thứ tự thì chắc thread-safe.”** Không; shared fields vẫn có thể race.
18. **“Proxy AOP tự thấy mọi method nội bộ.”** Không đối với nhiều mô hình wrapper bên ngoài.
19. **“Mọi engine hỗ trợ cùng một cú pháp và ngữ nghĩa around advice.”** Không; phải kiểm tra tài liệu công nghệ đang dùng.
20. **“Bất kỳ cache hit nào cũng có thể bỏ qua authorization.”** Nguy hiểm nếu policy bắt buộc không được bảo đảm.
21. **“Trả fallback trong INNER không ảnh hưởng outcome của OUTER.”** Thực tế OUTER thấy normal return nếu exception đã được phục hồi.
22. **“Một phép đo latency ở OUTER và INNER có thể cộng mà không trùng.”** Outer thường bao gồm thời gian inner; cộng như duration độc lập có thể double-count.

## 22. Câu hỏi tự kiểm tra

**Câu 1.** `A(B(C(target)))`, target trả bình thường. `after` của advice nào chạy trước?  
**Đáp án:** C trước, tiếp đến B rồi A, nếu mỗi around wrapper chuyển tiếp một lần và after tương ứng được đặt sau lời `proceed`.

**Câu 2.** INNER biến `"x"` thành `"x!"`; OUTER thêm ngoặc vuông. Caller thấy gì?  
**Đáp án:** `"[x!]"` vì outer nhận kết quả đã đi qua inner.

**Câu 3.** INNER short-circuit trả cached result. OUTER có thể chạy `afterReturning` không?  
**Đáp án:** Có; OUTER nhận một normal return từ INNER, dù target không chạy.

**Câu 4.** Target ném exception, INNER ghi lỗi rồi `finally`. OUTER chạy ở nhánh nào?  
**Đáp án:** Nếu INNER ném lại thì OUTER thấy exceptional completion; nếu INNER chuyển lỗi thành fallback thì OUTER thấy normal return.

**Câu 5.** Vì sao `finally` của INNER thường xuất hiện trước câu lệnh sau `proceed` ở OUTER?  
**Đáp án:** OUTER đang chờ INNER hoàn tất; INNER phải rời scope và chạy `finally` trước khi trả điều khiển.

**Câu 6.** Có thể suy ra ai outer từ tên class hoặc thứ tự khai báo trong source không?  
**Đáp án:** Không có quy tắc chung. Phải xác định precedence theo công cụ và kiểm thử đường gọi thật.

**Câu 7.** Outer retry gọi `proceed` hai lần; inner metrics đo mỗi attempt hay cả request?  
**Đáp án:** Nếu metrics nằm bên trong retry, mỗi lần `proceed` chạy lại nó có thể tạo mẫu theo attempt; metrics ngoài retry đo toàn bộ request.

**Câu 8.** `finally` tự `return` một giá trị khác có an toàn không?  
**Đáp án:** Không. Nó có thể thay return hoặc nuốt exception trước đó.

**Câu 9.** Trong demo Java, vì sao `TARGET_CALLS 2` dù có ba lần gọi `execute`?  
**Đáp án:** Ca thứ ba cache short-circuit, không gọi tới Core; chỉ A và bad tới target.

**Câu 10.** Method trả `CompletableFuture` chưa hoàn tất. Around `finally` chạy khi nào?  
**Đáp án:** Nếu scope đo method invocation thông thường, nó chạy khi method trả Future, không đợi completion của task.

**Câu 11.** Đặt cache ngoài authorization có thể ảnh hưởng gì?  
**Đáp án:** Cache hit có thể bỏ qua kiểm quyền nếu chính sách không được kiểm ở ranh giới khác. Thứ tự cần dựa trên hợp đồng bảo mật.

**Câu 12.** Khi hai advice cho hai join point khác nhau cùng chạy, có chắc double weaving?  
**Đáp án:** Không. Có thể là hai events hợp lệ; cần phân biệt identity của join point, aspect và logical operation.

## 23. Tổng kết

**Chuỗi around advice được hiểu bằng sự lồng nhau của các lời gọi.** Lớp ngoài chạy trước trên đường vào, lớp trong chạy trước trên đường trả kết quả hoặc tháo ngăn xếp ngoại lệ. Mỗi lớp có thể quan sát hoặc biến đổi result, ném lại/khôi phục lỗi và cleanup trong `finally`. Nếu một lớp **không gọi `proceed`**, những lớp phía trong có thể không chạy; nếu **gọi nhiều lần**, lớp trong và target có thể thực thi nhiều lần.

Điều thiết yếu khi phối hợp nhiều aspect không phải ghi nhớ thứ tự A/B/C máy móc, mà là **định nghĩa outer/inner theo precedence thật, xác định ranh giới quan sát và kiểm thử normal/exceptional/short-circuit/retry paths**. Với các concern nhạy cảm như kiểm quyền, audit, cache hay transaction, đổi thứ tự có thể thay đổi cả tính đúng đắn nghiệp vụ. Hãy bảo toàn kết quả và ngoại lệ của caller khi advice chỉ quan sát, quản lý `finally`/resource cleanup cẩn thận, tách method return khỏi asynchronous completion, và kiểm chứng thực tế qua đúng proxy/weaving join point.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-004">Quan hệ thứ tự giữa advice trước, sau và khi có lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ thứ tự giữa advice trước, sau và khi có lỗi trong phối hợp nhiều aspect

## 1. Mục tiêu của bài học

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một **aspect** đóng gói hành vi xuyên suốt như kiểm toán, kiểm tra quyền, logging, đo thời gian, retry và điều phối giao dịch. **Pointcut** chọn các **join point** thuộc mô hình thực thi được công cụ AOP hỗ trợ; **advice** xác định việc cần làm tại điểm đã chọn.

Khi **nhiều aspect cùng áp dụng tại một join point**, chỉ biết “advice A và advice B đều khớp” là chưa đủ. Thứ tự hiệu lực có thể quyết định:

- Ai được kiểm tra quyền trước?
- Advice nào quan sát kết quả nghiệp vụ gốc và advice nào chỉ thấy kết quả đã biến đổi?
- Logging xảy ra trước hay sau khi một advice khác chuyển đổi exception?
- Một before advice ném lỗi có ngăn một advice khác chạy không?
- After returning, after throwing và after/finally chạy trong nhánh nào?
- Một after advice tự ném lỗi có thể khiến caller nhận gì?
- Advice bên ngoài có thấy lỗi phát sinh từ advice bên trong không?
- Một around advice không gọi tiếp có làm mất những advice dự kiến không?

Bài này cung cấp một **mô hình suy luận theo lớp bao (nested execution)**, các bảng đường đi thành công/thất bại, ví dụ Java 17 độc lập và chiến lược kiểm thử. **Không có một thứ tự cố định áp dụng cho mọi công cụ AOP**; khi nói về một quy tắc precedence riêng, bài nêu rõ ngữ cảnh công cụ.

## 2. Thuật ngữ cần phân biệt

| Thuật ngữ | Ý nghĩa | Ảnh hưởng tới thứ tự |
| --- | --- | --- |
| **Join point** | Điểm/sự kiện mà cơ chế AOP có thể can thiệp | Xác định ranh giới cùng được advice |
| **Pointcut** | Quy tắc chọn join point | Nhiều pointcut có thể cùng khớp |
| **Before advice** | Hành vi trước phần tiếp tục | Có thể chặn phần bên trong nếu ném lỗi |
| **After returning advice** | Hành vi trên đường hoàn tất bình thường | Chỉ chạy khi phần được quan sát trả bình thường theo ngữ nghĩa của nó |
| **After throwing advice** | Hành vi trên đường thoát bằng lỗi phù hợp | Có thể thấy lỗi nghiệp vụ hoặc lỗi từ tầng advice trong |
| **After/finally advice** | Hành vi ở điểm kết thúc | Thường có vai trò tương tự finally tại ranh giới được bao |
| **Around advice** | Bao quanh và điều khiển phần được tiếp tục | Có thể gọi, bỏ qua, gọi lại hoặc biến đổi kết quả nếu công cụ cho phép |
| **Precedence/priority** | Quan hệ ưu tiên giữa các advice/aspect | Xác định ai bao ai và ai chạy trước ở từng giai đoạn |
| **Proceed/continuation** | Chuyển điều khiển tới phần có thứ tự thấp hơn hoặc thao tác gốc | Quyết định có tới phần nghiệp vụ hay không |
| **Caller** | Mã ngoài quan sát return hoặc exception | Chỉ thấy kết quả sau các lớp advice |

**Ưu tiên cao hơn** không có nghĩa “mọi đoạn mã của aspect đó luôn chạy sớm hơn mọi đoạn mã của aspect ưu tiên thấp”. Trong mô hình bao quanh, **phần vào** có thể đi từ ngoài vào trong, còn **phần kết thúc** trở ra từ trong ra ngoài.

## 3. Mô hình xếp lớp: chìa khóa để hiểu thứ tự

Giả sử hai aspect cùng áp dụng cho thao tác `placeOrder()`:

- **Aspect A** ở lớp ngoài.
- **Aspect B** ở lớp trong.
- `BusinessOperation` là phần xử lý cuối.

Sơ đồ:

```text
Caller
  |
  v
Aspect A: enter / before
  |
  v
Aspect B: enter / before
  |
  v
BusinessOperation
  |
  v
Aspect B: after / exit
  |
  v
Aspect A: after / exit
  |
  v
Caller
```

Biểu thức tư duy:

```text
A(B(BusinessOperation))
```

Khi mọi tầng **chuyển tiếp bình thường**, thứ tự điển hình là:

```text
A.before
B.before
business
B.afterReturning
B.afterFinally
A.afterReturning
A.afterFinally
```

Nhưng không được đọc dòng trên như một đặc tả chung cho mọi DSL. Đó là **một chuỗi minh họa có thứ tự A ngoài, B trong**, mỗi lớp có các hook nhất định. Một công cụ có thể đặt một advice ở ranh giới khác hoặc có quy tắc precedence riêng.

### 3.1. Phân biệt thứ tự “đi vào” và “đi ra”

Hình dung `A` và `B` như hai cặp ngoặc:

```text
A.before (
    B.before (
        business()
    ) B.after
) A.after
```

Phần before xuất hiện theo hướng **ngoài → trong**. Phần after xuất hiện theo hướng **trong → ngoài**. Đây là một tính chất của **lồng nhau có thứ tự xác định**, không đơn thuần là danh sách các hàm chạy nối đuôi nhau.

### 3.2. Khi có ba aspect

Với `Security(Audit(Metrics(Business)))`:

```text
Security.before
Audit.before
Metrics.before
Business
Metrics.after
Audit.after
Security.after
```

Nếu `Security.before` từ chối trước khi tiếp tục, `Audit`, `Metrics` và `Business` có thể không được vào. Nhưng cleanup hoặc after của **một lớp ngoài đã bắt đầu bao quanh đường đi** vẫn có thể chạy tùy phạm vi của nó.

## 4. Before advice: thứ tự và quyền chặn phần tiếp tục

**Before advice** chạy trước phần mà nó quan sát. Nếu có nhiều before advice, công cụ có thể dùng precedence để quyết định thứ tự.

Trong một mô hình thứ tự ưu tiên:

```text
A.before -> B.before -> business
```

Nếu `A.before` ném `AccessDenied`:

```text
A.before -> throw AccessDenied
            B.before: chưa chạy
            business: chưa chạy
```

Nếu `B.before` ném lỗi:

```text
A.before
B.before -> throw ValidationFailure
            business: chưa chạy
            A.outerCatch/outerFinally: có thể quan sát khi unwinding
```

**Hai lưu ý:**

1. Việc một before advice ném lỗi có ngăn advice khác hay không phụ thuộc **vị trí thực tế trong chain**.
2. Không thể khẳng định “mọi after advice đều chạy dù before đầu tiên thất bại”. Nếu phần mà after bao chưa được bắt đầu, hook đó có thể không có cơ hội chạy.

### 4.1. Before được dùng để từ chối có chủ đích

Một advice bảo mật có thể phải chặn thao tác trước khi mã nghiệp vụ thực thi. Khi đó **sự vắng mặt của log business** là đúng. Kiểm thử cần xác minh target chạy **0 lần**, không chỉ assert loại exception.

### 4.2. Before chỉ dùng telemetry phải xử lý lỗi riêng

Nếu before chỉ ghi metric nhưng chính metric client ném lỗi, nó có thể làm gián đoạn nghiệp vụ ngoài ý định. Chính sách có thể yêu cầu:

- Metrics tùy chọn: giữ đường nghiệp vụ, báo lỗi phụ trợ an toàn.
- Audit bắt buộc: có thể fail-closed nếu không ghi được chứng cứ cần thiết.
- Kiểm soát quyền: lỗi kiểm tra không được mặc định chuyển thành “cho phép”.

Đây là quyết định thiết kế, không tự suy từ loại advice.

## 5. After returning: chạy sau khi phần được quan sát trả bình thường

Một **after returning advice** gắn với đường kết thúc **bình thường**. Nếu phần tiếp tục ném lỗi phù hợp, advice này thường **không chạy** cho đường thất bại đó.

Trong mô hình `A(B(Business))` thành công:

```text
A.before
B.before
Business -> return "OK"
B.afterReturning
A.afterReturning
Caller -> "OK"
```

Nếu business ném lỗi và không có tầng trong nào phục hồi, hai after returning trong mô hình trên sẽ không chạy.

### 5.1. “Trả bình thường” là tương đối theo lớp

Ví dụ:

```text
A(
    B(
        Business -> throw KnownFailure
    )
)
```

Nếu `B.around` bắt `KnownFailure` và trả giá trị fallback hợp lệ, thì từ góc nhìn `A`, `proceed` đã **trả bình thường**. Lúc đó after returning ở lớp ngoài **có thể chạy**, dù thân nghiệp vụ ban đầu ném exception.

Vì vậy đừng khẳng định “after returning chạy thì business chắc chắn thành công ở tầng gốc”. Nó chỉ cho biết **đường được advice đó quan sát** trả bình thường theo ngữ nghĩa engine.

### 5.2. After returning có thể tự thất bại

Giả sử business đã tạo đơn và trả ID, sau đó advice đo metric ném `MetricFailure`. Caller có thể nhận exception thay vì order ID, dù side effect đã xảy ra.

Đây là rủi ro quan trọng với retry: **caller nhận lỗi không đồng nghĩa nghiệp vụ chưa hoàn thành**. Khi phối hợp nhiều aspect, lỗi hậu xử lý của lớp trong có thể đến after throwing ở lớp ngoài.

## 6. After throwing: nhìn nhánh lỗi theo đúng ranh giới

**After throwing advice** được kích hoạt khi phần thực thi nó quan sát thoát bằng một exception **phù hợp điều kiện loại lỗi**, theo ngữ nghĩa công cụ.

Ví dụ mô hình:

```text
A.before
B.before
Business -> throw BusinessFailure
B.afterThrowing(BusinessFailure)
A.afterThrowing(BusinessFailure)
Caller catches BusinessFailure
```

Nhiều công cụ cho phép binding exception để ghi kiểu/message hoặc context.

Nhưng trong phối hợp nhiều aspect có ba biến thể cần phân biệt.

### 6.1. Advice trong ném lỗi mới thay vì ném lỗi gốc

```text
Business -> throw BusinessFailure
B.afterThrowing -> throw AuditFailure
A.afterThrowing -> sees AuditFailure
Caller -> AuditFailure
```

Tùy thiết kế, lỗi `BusinessFailure` có thể bị mất nếu `B` không giữ nó trong `cause` hoặc `suppressed`.

### 6.2. Advice trong trả fallback

```text
Business -> throw BusinessFailure
B.around catches -> return FALLBACK
A.afterReturning(FALLBACK)
Caller -> FALLBACK
```

Tại lớp ngoài, không còn nhánh thrown từ continuation nên after throwing ở lớp ngoài có thể không chạy; thay vào đó after returning phù hợp với kết quả đã được phục hồi.

### 6.3. Lỗi có thể từ advice, không phải business

```text
Business -> return "OK"
B.afterReturning -> throw MetricFailure
A.afterThrowing -> sees MetricFailure
```

Đừng dùng tên sự kiện “business-error” chỉ vì after throwing của lớp ngoài thấy lỗi; cần biết nguồn lỗi trong chain.

### 6.4. Bộ lọc theo kiểu lỗi

Nếu một after throwing chỉ nhận `IOException`, còn lỗi là `IllegalArgumentException`, advice đó có thể không khớp. Khi nhiều after throwing bắt các kiểu khác nhau, tập advice thực sự chạy phụ thuộc quan hệ kiểu exception, không chỉ precedence.

## 7. After/finally: khái niệm “luôn chạy” phải có ranh giới

Một **after/finally advice** thường mô tả công việc khi join point hoặc phần được bao quanh kết thúc **bình thường hoặc bằng exception**.

Với lớp đã được vào:

```text
try:
    proceed()
finally:
    finalizeForThisLayer()
```

Đây là **mã giả**; phạm vi `try` và vị trí before của mỗi engine cần được xét cụ thể.

Ví dụ khi business thất bại:

```text
A.before
B.before
Business -> throw
B.afterThrowing
B.afterFinally
A.afterThrowing
A.afterFinally
Caller -> exception
```

Nếu `B.before` ném lỗi **trước khi đi vào vùng try/finally của B**, `B.afterFinally` trong một implementation thủ công **sẽ không chạy**. Ngược lại, `A.afterFinally` vẫn có thể chạy vì lớp A đã thiết lập finally bao quanh lời gọi B.

**“Finally luôn chạy” chỉ đúng trong phạm vi điều khiển thật sự đã được thiết lập**, với những giới hạn ngôn ngữ/runtime tương ứng; không có lời hứa rằng mọi after advice trong cả hệ thống đều chạy sau bất cứ lỗi nào.

### 7.1. Một cleanup thất bại có thể che lỗi cũ

Trong Java:

```java
static void example() {
    try {
        throw new IllegalArgumentException("original");
    } finally {
        throw new IllegalStateException("cleanup");
    }
}
```

Đây là **mảnh Java minh họa**. Caller nhận `IllegalStateException("cleanup")`; lỗi `IllegalArgumentException("original")` không tự động được gắn vào cause/suppressed.

Khi nhiều aspect đều làm cleanup, một lỗi ở lớp ngoài có thể che lỗi của lớp trong. Cần thiết kế **ưu tiên lỗi chính/phụ** và chính sách bảo toàn nguyên nhân rõ ràng.

## 8. Precedence là thứ tự ưu tiên, không phải thứ tự tất cả các dòng log

Giả sử `A` có precedence cao hơn `B` tại cùng join point.

Với **mô hình nested advice** thông thường:

- Ở đường **vào**, `A.before` có thể chạy trước `B.before`.
- Ở đường **ra**, after của `B` có thể chạy trước after của `A`.
- `A.around` có thể có quyền quyết định có tiếp tục xuống phần `B` không.
- `A` có thể nhìn thấy kết quả hoặc lỗi đã được `B` biến đổi.

Hãy nghĩ đến **quyền bao phần thực thi bên dưới** hơn là bảng “A luôn trước B”.

Ví dụ:

```text
A [entry]
    B [entry]
        business
    B [exit]
A [exit]
```

Để biết thứ tự thật trong một công cụ cần đọc:

1. Quy tắc precedence giữa các **aspect khác nhau**.
2. Quy tắc giữa các advice **trong cùng aspect**.
3. Quy tắc cho before / after returning / after throwing / finally / around.
4. Thứ tự khi nhiều pointcut cùng khớp.
5. Trường hợp không có khai báo thứ tự rõ: công cụ có bảo đảm hay không?

### 8.1. Precedence không phải tổng thứ tự trong mọi mô hình

Một hệ thống có thể chỉ khai báo:

```text
Security trước Metrics
Audit trước Metrics
```

Nhưng **không nói** Security và Audit ai trước. Đó là **partial order**, không đủ để khẳng định một thứ tự duy nhất.

Nếu hai advice không có quan hệ precedence được xác định, kết quả có thể phụ thuộc công cụ hoặc không được bảo đảm. Không viết ứng dụng phụ thuộc vào thứ tự ngẫu nhiên của build, danh sách component hay thứ tự reflection.

## 9. Một công cụ AOP dùng để đối chiếu ngữ nghĩa precedence

Để minh họa việc **thứ tự là ngữ nghĩa được công cụ quy định**, có thể xem **AspectJ** như **một ví dụ cụ thể** (không phải quy tắc chung của mọi hệ thống AOP).

Trong ngữ nghĩa advice của công cụ này:

- Có thể khai báo quan hệ ưu tiên giữa các aspect bằng `declare precedence`.
- Một advice precedence cao hơn bao quanh việc chạy các advice precedence thấp hơn ở cùng join point theo các quy tắc của công cụ.
- Before ở precedence cao có thể ngăn advice thấp hơn nếu ném exception.
- After returning / after throwing / after thông thường hoạt động sau khi phần có precedence thấp hơn hoàn tất theo điều kiện kết thúc.
- Với advice nằm **trong cùng một aspect**, thứ tự khai báo và loại advice có quy tắc riêng; không suy từ “cùng aspect” rằng luôn là thứ tự viết trước.
- Khi hai aspect không có quan hệ precedence được xác định, thứ tự giữa chúng có thể **không được xác định** theo ngữ nghĩa của chính công cụ này.

Ví dụ **cú pháp đặc thù AspectJ**, không phải mã Java 17 thuần và không biên dịch bằng `javac` đơn lẻ:

```java
declare precedence: SecurityAspect, AuditAspect, MetricsAspect;
```

Đoạn trên minh họa khai báo ưu tiên, không phải một chương trình độc lập. Khi dùng DSL khác, phải sử dụng cơ chế cấu hình thứ tự tương ứng với công cụ đó, không sao chép cú pháp trên.

**Quan trọng:** không nên lấy quy tắc của một công cụ rồi áp đặt lên mọi engine proxy, compiler-based weaving hoặc interception middleware.

Nguồn ngữ nghĩa để đối chiếu:
- [Eclipse AspectJ — Advice và advice precedence](https://eclipse.dev/aspectj/doc/released/progguide/semantics-advice.html)
- [Eclipse AspectJ — Khai báo precedence](https://eclipse.dev/aspectj/doc/released/progguide/semantics-declare.html)

## 10. Around advice là ranh giới có sức ảnh hưởng mạnh nhất

Một around advice có thể bao toàn bộ phần còn lại:

```text
around A:
    A.enter
    result = proceed()
    A.exit
    return result
```

`proceed` là ký hiệu có nghĩa **tiếp tục xuống phần có precedence phù hợp**, không mặc định chỉ gọi trực tiếp body nghiệp vụ.

Những tình huống đặc biệt:

### 10.1. Không gọi `proceed`

```text
A.around:
    if forbidden:
        return REJECTED
    else:
        return proceed()
```

Đây là mã giả. Nếu `A` không gọi `proceed`, những advice được xếp **bên trong** và business có thể không chạy.

### 10.2. Gọi `proceed` rồi biến đổi kết quả

```text
B.business returns 10
A.around proceed() returns 10
A.around returns 20
Caller sees 20
```

After returning **ngoài A** có thể thấy 20; after returning **bên trong A** có thể đã thấy 10. Không được nói mọi advice quan sát cùng một giá trị.

### 10.3. Bắt exception và trả fallback

```text
B/business throws TemporaryFailure
A.around catches -> return CACHED
Outside afterReturning -> sees CACHED
```

Một advice ngoài có thể coi nhánh này hoàn tất bình thường trong khi business gốc đã thất bại.

### 10.4. Retry `proceed`

Nếu mô hình cho phép, calling `proceed` nhiều lần có thể thực thi nhiều tầng advice và phần gốc nhiều lần. Mỗi lần còn có thể kích hoạt before/after tương ứng ở **bên trong**.

Với giao dịch không idempotent, retry vì lỗi metrics sau khi business đã thành công có thể tạo trùng side effect. Cần xác định điểm nào có thể an toàn thử lại.

## 11. Bảng tóm tắt thứ tự theo nhánh trong mô hình A ngoài – B trong

| Tình huống | Đường đi minh họa | Ghi chú |
| --- | --- | --- |
| Thành công | A.before → B.before → Business → B.afterReturning → A.afterReturning | Các after/finally thích hợp còn chạy theo phạm vi |
| Business ném lỗi | A.before → B.before → Business throws → B.afterThrowing → A.afterThrowing | Không chạy afterReturning của nhánh lỗi ấy |
| A.before ném lỗi | A.before throws | B và business có thể chưa bắt đầu |
| B.before ném lỗi | A.before → B.before throws → A xử lý lỗi | B.after có chạy hay không tùy ranh giới setup của B |
| B afterReturning ném lỗi | Business success → B afterReturning throws → A afterThrowing | Caller có thể thấy lỗi B |
| B afterThrowing ném lỗi mới | Business throws E → B throws F → A thấy F | E có thể bị che |
| B.around trả fallback | Business throws → B returns fallback → A afterReturning | Outer thấy normal return |
| A.around không proceed | A short-circuit → caller | Advice bên trong và body có thể không chạy |
| Cleanup ngoài ném lỗi | B/Business kết thúc → A finally throws G | Caller có thể thấy G thay vì outcome trước |
| Guard động không khớp một advice | Advice đó không chạy | Không suy ra mọi advice đều chạy chỉ từ precedence |

Bảng giả định các advice đã được sắp xếp theo lớp A ngoài, B trong và loại advice phù hợp. Nó không thay thế việc kiểm tra order do engine thật công bố.

## 12. Ví dụ Java 17 độc lập: mô phỏng chuỗi nhiều aspect

Java thuần không tích hợp ngôn ngữ pointcut và advice weaving. Để thấy **trật tự sự kiện một cách xác định**, ví dụ sau dùng **hàm wrapper thủ công** làm vai trò của các lớp advice.

Điểm thiết kế:

- `A` là lớp ngoài; `B` là lớp trong.
- Mỗi lớp ghi `before`, `afterReturning`, `afterThrowing`, `afterFinally`.
- `B.before` có thể từ chối trước khi bước vào vùng `try/finally` của B.
- Business có thể thành công hoặc ném lỗi.
- `B.afterReturning` có thể tự ném lỗi, và lỗi ấy đi ra tới catch của A.
- Catch của mỗi lớp chỉ bao **continuation**, để không gán nhãn sai chính lỗi `afterReturning` của lớp đó thành lỗi phần tiếp tục.

Tạo file `AdviceOrderDemo.java`:

```java
public class AdviceOrderDemo {
    interface Operation {
        String call(String mode);
    }

    static Operation layer(String name, Operation next) {
        return mode -> {
            System.out.println(name + ":before");

            if ("block".equals(mode) && "B".equals(name)) {
                throw new SecurityException("denied");
            }

            try {
                String result;
                try {
                    result = next.call(mode);
                } catch (RuntimeException error) {
                    System.out.println(name + ":afterThrowing:"
                            + error.getClass().getSimpleName());
                    throw error;
                }

                System.out.println(name + ":afterReturning");

                if ("postfail".equals(mode) && "B".equals(name)) {
                    throw new IllegalArgumentException("after failed");
                }

                return result;
            } finally {
                System.out.println(name + ":afterFinally");
            }
        };
    }

    public static void main(String[] args) {
        Operation business = mode -> {
            System.out.println("business:" + mode);
            if ("fail".equals(mode)) {
                throw new IllegalStateException("business failed");
            }
            return "OK:" + mode;
        };

        Operation chain = layer("A", layer("B", business));

        for (String mode : new String[] {
                "ok", "fail", "block", "postfail"
        }) {
            System.out.println("case:" + mode);
            try {
                System.out.println("caller:result:" + chain.call(mode));
            } catch (RuntimeException error) {
                System.out.println("caller:error:"
                        + error.getClass().getSimpleName()
                        + ":" + error.getMessage());
            }
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac AdviceOrderDemo.java
java AdviceOrderDemo
```

**Stdout chính xác của chương trình:**

```text
case:ok
A:before
B:before
business:ok
B:afterReturning
B:afterFinally
A:afterReturning
A:afterFinally
caller:result:OK:ok
case:fail
A:before
B:before
business:fail
B:afterThrowing:IllegalStateException
B:afterFinally
A:afterThrowing:IllegalStateException
A:afterFinally
caller:error:IllegalStateException:business failed
case:block
A:before
B:before
A:afterThrowing:SecurityException
A:afterFinally
caller:error:SecurityException:denied
case:postfail
A:before
B:before
business:postfail
B:afterReturning
B:afterFinally
A:afterThrowing:IllegalArgumentException
A:afterFinally
caller:error:IllegalArgumentException:after failed
```

### 12.1. Vì sao case `ok` có thứ tự này?

`A` in before rồi gọi `B`. `B` in before rồi gọi business. Business trả bình thường, nên `B` in afterReturning và afterFinally trước khi trả về `A`. Sau đó `A` in afterReturning và afterFinally.

Đây là mô hình **before ngoài trước, after trong trước** khi các tầng được bao quanh đúng thứ tự.

### 12.2. Vì sao case `fail` không in afterReturning?

Business ném `IllegalStateException`. `B` bắt ngoại lệ thoát từ `next.call`, in afterThrowing rồi ném lại. Finally của B in before khi lỗi lan lên `A`. Sau đó `A` bắt, in afterThrowing, chạy finally và để caller bắt lỗi gốc.

### 12.3. Vì sao case `block` không in `B.afterFinally`?

`B` ném `SecurityException` **trước khi vào khối try/finally của B**. Do đó finally của B **chưa được thiết lập**. `A` đã bước vào khối try bao lời gọi B nên catch/finally của A vẫn chạy.

Điều này minh họa quy tắc: **không thể nói after/finally của mọi advice luôn chạy nếu một advice trước đã chặn tiến trình**. Cần xem advice nào đã được tiến tới và ranh giới setup tương ứng.

### 12.4. Vì sao case `postfail` chỉ có `A.afterThrowing`?

Trong B, đoạn xử lý lỗi chỉ bao `next.call(mode)`; nó **không bao** chính `B.afterReturning`. Vì vậy khi B afterReturning in rồi ném `IllegalArgumentException`, catch của B không xử lý nhầm lỗi của chính mình. Nhưng continuation của A **bao toàn bộ B**, nên catch của A nhận lỗi ấy và in afterThrowing.

Điều cần hiểu: **đường lỗi ở mỗi lớp phản ánh kết quả phần được lớp đó quan sát**, không mặc định mọi lớp cùng nhìn thấy lỗi của cùng một nguồn.

### 12.5. Những gì chương trình không chứng minh

- Nó **không** tự phân tích pointcut hoặc tạo join point shadow.
- Nó **không** chứng minh AspectJ hay bất kỳ compiler AOP nào phát ra code y hệt.
- Nó **không** quy định thứ tự giữa advice nằm trong một aspect thật khi công cụ có quy tắc riêng.
- Nó chỉ minh họa **một thứ tự lồng nhau được khai báo rõ bằng Java**.

## 13. Phân biệt thứ tự “đăng ký”, “ưu tiên” và “thực thi”

Ba loại thứ tự thường bị nhầm:

### 13.1. Thứ tự đăng ký/khởi tạo

Một aspect hoặc interceptor có thể được đăng ký trước hay sau. Đây là chuyện tạo component, không tự bảo đảm ai bao ai khi runtime.

### 13.2. Thứ tự ưu tiên có hiệu lực

Công cụ có thể giải quyết precedence từ annotation, cấu hình, DSL, thứ bậc kế thừa, thứ tự khai báo hoặc quy tắc khác. Một số cặp có quan hệ rõ; số khác có thể không được đảm bảo.

### 13.3. Thứ tự sự kiện thực tế

Khi chạy, thứ tự còn phụ thuộc nhánh trả về, exception, guard, việc quanh-advice có proceed hay không và dữ liệu runtime.

Ví dụ `A` được ưu tiên cao hơn `B` nhưng trong ca lỗi:

```text
A.before
B.before
Business throws
B.afterThrowing
A.afterThrowing
```

Khi quan sát log, `B` xuất hiện sau business nhưng **trước** `A` trong phần thoát. Điều đó **không mâu thuẫn** với việc `A` là lớp ngoài ưu tiên cao.

## 14. Nhiều advice bên trong cùng một aspect

Một aspect có thể có **nhiều advice cùng khớp một join point**, thậm chí nhiều before, after hoặc around.

Đừng suy rằng:

- Chúng luôn chạy theo thứ tự tên method.
- Thứ tự source file tự quyết định ở mọi công cụ.
- Nhóm before/after có cùng quy tắc sort.
- Advice được khai báo cùng aspect được chạy đồng thời.
- Một method advice gắn nhiều pointcut sẽ được gọi đúng một lần bất kể số matching được mô hình hóa.

Trong **AspectJ** chẳng hạn, có quy tắc riêng khi **hai advice thuộc cùng aspect**: một số trường hợp liên quan `after` được xét khác với trường hợp không có after. Đặc tả cũng nói về các tình huống quan hệ precedence không xác định giữa các aspect.

Với công cụ dùng reflection để tìm advice methods, thứ tự xuất hiện của các method reflection **không nên được coi là cam kết về declaration order** trừ khi tài liệu công cụ bảo đảm.

**Khuyến nghị:** nếu thứ tự trong cùng một aspect ảnh hưởng correctness, hãy gom các bước phụ thuộc thứ tự thành **một đơn vị điều phối rõ ràng**, hoặc khai báo precedence qua cơ chế chính thức và kiểm thử nó.

## 15. Advice trước và sau có thể không “đối xứng”

Một hệ thống có thể có:

- `Security.before` nhưng **không có** Security.after.
- `Audit.afterReturning` và `Audit.afterThrowing` nhưng không có before.
- `Metrics.around` với `finally` đo thời lượng.
- `Transaction.around` mở và đóng phạm vi công việc.
- `Recovery.around` bắt một lỗi nhất định và trả fallback.

Vì thế không phải mỗi `before` đều có đúng một `after` “cùng cặp”.

Ngay cả khi có cặp, advice sau có thể chỉ áp dụng đường bình thường, chỉ đường lỗi, hoặc cả hai. Thứ tự quan sát phải gắn với **loại advice**, không tạo ra quy tắc “đảo ngược tất cả dòng log trước/sau” quá đơn giản.

## 16. After returning, after throwing, after finally: ma trận đường thoát

| Kết quả phần tiếp tục | After returning | After throwing | After/finally |
| --- | --- | --- | --- |
| Trả giá trị bình thường | Có thể chạy nếu điều kiện khớp | Không cho lỗi không tồn tại | Có thể chạy nếu advice/range đã thiết lập |
| Ném exception thuộc loại phù hợp | Không trên nhánh ấy | Có thể chạy | Có thể chạy |
| Ném exception không hợp type filter | Không trên nhánh ấy | Advice có filter không khớp sẽ không chạy | Có thể chạy |
| Around bên trong bắt lỗi, trả fallback | Với lớp ngoài là return bình thường | Không tại lớp ngoài nếu không có lỗi thoát | Có thể chạy |
| Before bên trong ném lỗi trước body | Tùy advice nào đã được vào | Lớp ngoài có thể quan sát lỗi | Tùy phạm vi finally thực tế |
| After returning bên trong tự ném | Lớp ngoài không nhận return bình thường | Lớp ngoài có thể nhận lỗi advice | Finally lớp đã thiết lập có thể chạy |
| Cleanup bên trong tự ném | Kết quả trước có thể bị che | Lớp ngoài có thể bắt cleanup error | Các finally ngoài có thể vẫn chạy |

**Lưu ý về chữ “có thể”:** bảng diễn tả điều kiện logic và ranh giới của mô hình. Đúng loại event chưa đủ nếu pointcut không khớp, runtime guard sai hoặc advice không nằm trên đường đã được thực thi.

## 17. Lỗi từ advice làm thay đổi thứ tự các advice còn lại

Tình huống:

```text
A.before
B.before
Business returns OK
B.afterReturning throws MetricFailure
A.afterThrowing receives MetricFailure
A.afterFinally
Caller receives MetricFailure
```

Những hệ quả:

1. Business **đã thành công**, nhưng caller nhận lỗi.
2. `A.afterReturning` không chạy trong mô hình này, dù business đã return.
3. `A.afterThrowing` chạy do **lỗi phát sinh từ B**, không từ business.
4. Nếu caller retry, side effect business có thể bị nhân đôi.
5. Nếu `A` chuyển đổi lỗi tiếp, exception cuối cùng có thể còn khác `MetricFailure`.

Vì vậy khi logging exception, cần ghi thêm **phase/source** (business, advice before, advice after, cleanup) nếu có chứng cứ, không gắn nhãn “business failed” cho mọi exception thoát từ continuation.

## 18. Giữ nguyên nhân lỗi qua nhiều tầng: cause và suppressed

Có ba lựa chọn thường gặp:

**Truyền tiếp nguyên dạng:**

```java
throw original;
```

Giữ đối tượng exception gốc. Đây là **mảnh Java**, giả định biến `original` đang được bắt và hợp đồng cho phép ném lại nó.

**Chuyển đổi có cause:**

```java
throw new IllegalStateException("audit failure", original);
```

Caller nhận lỗi mới, nhưng `getCause()` giúp truy nguyên lỗi trước.

**Giữ lỗi phụ bằng suppressed:**

```java
primary.addSuppressed(secondary);
throw primary;
```

Đây là mảnh Java, giả định cả hai là `Throwable` khác nhau và kiểu ném hợp lệ. `addSuppressed` không tự ném lại; cần thao tác `throw` sau đó.

### 18.1. Khi hai tầng đều ném lỗi

Giả sử business ném `E1`, advice B ném `E2` và advice A ném `E3`. Nếu mỗi tầng thay thế lỗi trước bằng lỗi mới **không có cause**, caller có thể chỉ thấy `E3`.

Điều đó làm chẩn đoán sai: lỗi `E1` quan trọng nhất có thể biến mất.

Một chính sách error model nên phân biệt:

- Lỗi nghiệp vụ chính.
- Lỗi của công việc quan sát/cleanup.
- Lỗi của cơ chế interception.
- Lỗi đang được dịch để công bố API.
- Thông tin chẩn đoán có thể ghi lại mà không tiết lộ bí mật.

### 18.2. Không phải mọi lỗi phụ trợ đều có thể bỏ qua

Metrics tùy chọn khác audit bắt buộc và kiểm soát quyền. Nếu business thất bại và audit cũng không lưu được, có thể cần một trạng thái lỗi tổng hợp hoặc cơ chế phục hồi được thiết kế riêng.

Không tùy tiện nuốt `Throwable` hoặc biến mọi lỗi thành kết quả thành công.

## 19. Transaction và advice order

Một ứng dụng có thể có nhiều mối quan tâm cùng áp dụng cho một operation:

```text
Authorization
   -> Transaction
       -> Audit
           -> Business
```

Ở đường thành công, ví dụ một thứ tự hiệu lực mong muốn là:

```text
authorize
open transaction
record audit intent
business
record audit outcome
commit/finish transaction
return caller
```

Nhưng một thiết kế khác có thể chủ đích ghi audit **sau commit**. Hai thiết kế mang ý nghĩa khác nhau về tính bền vững và nhất quán.

Cần quyết định:

- Audit thuộc cùng giao dịch hay kho dữ liệu khác?
- Khi business ném lỗi, transaction sẽ rollback theo chính sách nào?
- Advice audit có nhìn thấy lỗi gốc trước hay sau error translation?
- Khi commit thất bại **sau business return**, caller thấy lỗi nào?
- Logging `afterReturning` nằm trước hay sau commit của một wrapper bên ngoài?
- Có yêu cầu tránh side effect lặp lại khi caller retry?

Không có bảo đảm “hễ advice ném RuntimeException thì mọi side effect đều rollback”. Một email, HTTP request hoặc hệ thống ngoài giao dịch có thể đã xử lý yêu cầu.

## 20. Security và quan hệ ưu tiên

Kiểm tra quyền thường là hành vi **cần diễn ra trước** những thao tác không đáng tin, nhưng vị trí thật còn phụ thuộc mục tiêu chính sách.

Ví dụ:

```text
Security.before
Tracing.before
Business
Tracing.after
Security.after
```

Trong mô hình này, nếu Security từ chối, Tracing có thể **không có event của lần từ chối** vì nó chưa được vào. Nếu audit an ninh cần ghi **mọi attempt kể cả bị từ chối**, có thể cần một lớp ngoài Security:

```text
SecurityAudit(
    Security(
        Business
    )
)
```

Khi đó SecurityAudit có thể ghi nhận hành vi từ chối ở đường lỗi, với điều kiện audit wrapper đã vào và xử lý exception đúng.

Điều này cho thấy “aspect quan trọng nhất phải ưu tiên cao nhất” **chưa đủ để quyết định tất cả thứ tự**: hãy xuất phát từ **yêu cầu quan sát**, đường dữ liệu và ranh giới tin cậy.

### 20.1. Không dựa duy nhất vào proxy dễ bypass để bảo vệ bất biến

Nếu AOP chạy qua proxy, lời gọi trực tiếp target có thể đi vòng chuỗi advice. Một chính sách bảo mật bắt buộc phải được bảo vệ ở boundary phù hợp và kiểm thử cả đường bypass, không chỉ thứ tự khi đã ở trong chain.

## 21. Retry và thứ tự các lớp

Giả sử:

```text
RetryAround(
    MetricsAround(
        BusinessOperation
    )
)
```

Nếu Metrics ném lỗi **sau khi business đã thành công**, RetryAround có thể bắt lỗi đó và chạy lại `proceed`, khiến business thực hiện thêm lần nữa.

Đảo thứ tự thành:

```text
MetricsAround(
    RetryAround(
        BusinessOperation
    )
)
```

có thể thay đổi phạm vi đo metric, số lần log và loại lỗi mà retry quan sát. Ví dụ outer Metrics đo toàn bộ thời gian của mọi attempt, còn inner Metrics có thể đo từng attempt riêng, tùy cách code được triển khai.

Câu hỏi thiết kế:

- Retry được phép xử lý những kiểu lỗi nào?
- Lỗi sau commit/side effect có được phép retry không?
- Retry chạy lại cả chuỗi advice hay chỉ phần business?
- Metrics đếm request logic hay số attempt?
- Transaction được mở cho mỗi attempt hay bao toàn bộ?
- Có idempotency key hoặc cách kiểm tra trạng thái đã hoàn tất không?

**Thứ tự aspect là một phần của thiết kế nghiệp vụ**, không chỉ là cấu hình thẩm mỹ.

## 22. Những lưu ý riêng cho bất đồng bộ

Với Java, một method có thể trả `CompletableFuture<Result>`:

```text
Caller
 -> Advice trước
 -> Method returns Future
 -> Advice sau method-return
 -> Task completes later (success/failure)
```

Một after returning tại **method execution** có thể chạy khi Future **được trả ra**, không phải khi công việc bên trong Future hoàn tất.

Nếu task về sau ném lỗi, nó có thể được biểu diễn bằng **exceptional completion** ở stage tương ứng. Một after throwing quanh lời gọi tạo Future không tự động nhìn thấy lỗi ấy.

Muốn quan sát completion:

- Phải gắn handler tại đúng stage và hiểu việc handler tạo stage mới.
- Context/correlation ID cần được truyền qua task/thread.
- Callback quan sát có thể tự ném lỗi, tạo một nhánh lỗi khác.
- Không ghi “operation succeeded” chỉ vì method trả Future bình thường.
- Thứ tự completion giữa hai task độc lập không tự bảo đảm bởi precedence của advice tại method entry.

Với nhiều aspect bất đồng bộ, cần phân biệt **thứ tự đăng ký callback** và **thứ tự callback chạy thực tế** do API completion/thread scheduler quy định.

## 23. Thứ tự advice khi có nhiều join point khác nhau

Precedence của các advice **cùng một join point** không đồng nghĩa tổng thứ tự trên **mọi sự kiện của chương trình**.

Ví dụ một method `checkout` gọi `reserve` rồi `charge`. Advice cùng loại ở `reserve` và `charge` có thể xen kẽ theo call stack, không nhất thiết mọi before của toàn bộ chương trình chạy trước mọi after.

```text
checkout before
  reserve before
  reserve business
  reserve after
  charge before
  charge business
  charge after
checkout after
```

Nếu recursion hoặc async có mặt, log nhiều join point càng dễ xen kẽ.

Cần giữ **join-point identity** và **invocation ID** để phân tích thứ tự, không kết luận từ một stream log thiếu ngữ cảnh.

## 24. Kiểm thử thứ tự: không chỉ kiểm tra có log

Kiểm thử đúng nên thu event vào một danh sách với thứ tự xác định cho **một invocation đồng bộ**.

Ma trận đề xuất:

| Ca | Kỳ vọng kiểm thử |
| --- | --- |
| Thành công | Before ngoài → before trong → business → after trong → after ngoài |
| Business ném lỗi | Không có after returning trên nhánh thất bại; after throwing phù hợp chạy |
| Before ngoài từ chối | Inner/business chưa chạy |
| Before trong từ chối | Outer có thể nhận lỗi; không giả định inner finally đã thiết lập |
| Inner after returning lỗi | Outer thấy lỗi advice, không nhầm nguồn business |
| Inner after throwing lỗi | Outer thấy ngoại lệ mới/cause đúng |
| Around trong trả fallback | Outer thấy kết quả bình thường |
| Around ngoài không proceed | Inner/business không chạy |
| Cleanup lỗi sau business failure | Cause/suppressed/primary theo chính sách |
| Hai aspect không có precedence rõ | Test phát hiện không được phép phụ thuộc thứ tự ngẫu nhiên |
| Retry bao ngoài | Số lần business và advice phù hợp; không lặp side effect ngoài chủ đích |
| Transaction + audit | Quan sát đúng trước/sau commit, rollback và ngoại lệ |
| Async | Không nhầm return Future với task completion |
| Concurrent | Event nhóm theo invocation, không trộn request |

### 24.1. Event collector trong kiểm thử

Nên thu event như:

```text
A.before
B.before
business
B.afterReturning
B.afterFinally
A.afterReturning
A.afterFinally
```

rồi so sánh với expected list. Tránh phụ thuộc timestamp wall-clock để chứng minh thứ tự khi các event có thể có cùng độ phân giải thời gian.

### 24.2. Kiểm thử một phương thức không được advice

Ngoài ca dương tính, cần ca âm tính: điểm không khớp pointcut không được advice, dù aspect có precedence cao. Precedence **chỉ có ý nghĩa khi lời advice thực sự cùng áp dụng**.

### 24.3. Kiểm thử đường exception identity

Nếu policy là truyền tiếp nguyên ngoại lệ, so sánh identity `caught == original` khi phù hợp. Nếu policy là wrap, kiểm tra `getCause()`. Nếu cleanup thêm suppressed, kiểm tra `getSuppressed()`.

## 25. Làm sao tìm thứ tự khi kết quả chạy khác dự đoán?

Quy trình chẩn đoán:

1. **Xác định join point**: call, execution hoặc loại nào công cụ hỗ trợ?
2. **Liệt kê aspect thực sự khớp**: không phán đoán từ tên class.
3. **Kiểm tra pointcut/guard động**: advice không chạy có thể do không khớp.
4. **Xác định precedence có hiệu lực**: ưu tiên giữa aspect, trong cùng aspect, trường hợp không xác định.
5. **Vẽ lớp ngoài/trong**: ai gọi `proceed` tới ai?
6. **Đánh dấu mọi đường throw/return**: before fail, business fail, after fail, cleanup fail.
7. **Tìm advice short-circuit**: có quanh-advice không gọi tiếp không?
8. **Xác định loại after**: returning, throwing hay finally; binding type có khớp không?
9. **Phân biệt nguồn exception**: từ body, advice trong hay advice hiện tại.
10. **Kiểm tra proxy/weaving reachability**: lời gọi có thực sự đi qua lớp can thiệp không?
11. **Kiểm tra async/concurrency**: log nhiều invocation có bị xen kẽ không?
12. **Tái hiện với event collector nhỏ** và kiểm chứng chuỗi thực tế.

Khi chưa rõ nguyên nhân, không sửa cấu hình priority chỉ để “log nhìn đẹp hơn”. Nó có thể đổi ranh giới transaction, quyền kiểm soát truy cập và xử lý ngoại lệ.

## 26. Các hiểu nhầm phổ biến

**“Aspect ưu tiên cao luôn chạy mọi advice trước aspect ưu tiên thấp.”** Không. Với nesting, after có thể chạy theo chiều ngược của trước.

**“Sau khi business xong, mọi after advice đều chạy.”** Không. After returning/throwing có điều kiện theo đường thoát; pointcut và phạm vi còn quyết định.

**“After throwing chắc chắn thấy lỗi từ business.”** Không. Lỗi có thể do advice bên trong ném.

**“Business trả thành công nghĩa outer after returning chắc chắn chạy.”** Không nếu một advice trong ném lỗi sau business return.

**“Before ném lỗi thì mọi finally của mọi aspect vẫn chạy.”** Không. Chỉ những ranh giới đã được thiết lập và có semantics phù hợp mới có thể cleanup.

**“Đảo priority chỉ thay đổi thứ tự log.”** Không. Nó có thể thay đổi retry, transaction, security và loại lỗi cuối.

**“Thứ tự declaration luôn là thứ tự run.”** Không thể mặc định; phải xem quy tắc tool và loại advice.

**“Cùng một aspect thì thứ tự các advice chắc chắn ổn định.”** Không được tự suy; một số công cụ có quy tắc riêng hoặc trường hợp không xác định.

**“After finally luôn giữ exception gốc.”** Không. Cleanup ném lỗi có thể che nguyên nhân ban đầu.

**“Một after-returning trên Future nghĩa tác vụ thành công.”** Không. Đó có thể chỉ là return của method, không phải completion.

**“Tất cả advice khớp cùng pointcut luôn chạy.”** Không nếu around ngoài short-circuit, before chặn, guard không khớp hoặc đường gọi bypass proxy.

**“Đo thứ tự qua timestamp log toàn cục là đủ.”** Không trong concurrent/async; cần invocation ID và test per-call.

## 27. Bài tập tự luyện

### Bài 1 — Thành công với hai lớp

`A(B(Business))`, cả A và B có before/after returning/after finally; body trả bình thường. Hãy viết thứ tự.

**Gợi ý:** A.before → B.before → Business → B.afterReturning → B.afterFinally → A.afterReturning → A.afterFinally.

### Bài 2 — Lỗi từ business

Body ném `DomainFailure` và không có fallback. Những after nào chạy?

**Gợi ý:** after throwing của lớp đã vào và có type filter khớp, cùng các finally thích hợp; after returning trên nhánh đó không chạy.

### Bài 3 — Before lớp trong từ chối

A đã vào vùng try, nhưng B.before ném trước khi B thiết lập finally. Ai thấy lỗi, có chắc B.finally chạy không?

**Gợi ý:** A có thể catch và finally; B.finally của vùng chưa vào không chạy trong cấu trúc đã nêu.

### Bài 4 — After returning của B ném

Body trả `OK`, B.afterReturning ném `MetricFailure`. A có thể chạy advice nào?

**Gợi ý:** A có thể quan sát thrown branch từ phần B; không mặc định A.afterReturning chạy chỉ vì body thành công.

### Bài 5 — B bắt và chuyển lỗi thành fallback

Business ném `ReadFailure`, B.around trả `CACHED`. A thấy gì?

**Gợi ý:** phần tiếp tục của A trả bình thường; A.afterReturning có thể nhận `CACHED`.

### Bài 6 — Tracing quanh security

Nếu audit cần ghi lại cả request bị Security từ chối, nên đặt audit ngoài hay trong lớp Security?

**Gợi ý:** một lớp audit ngoài security có thể thấy attempt/từ chối, miễn nó thực sự đã bắt đầu và không bị bỏ qua bởi cơ chế khác.

### Bài 7 — Lỗi cleanup

Business ném `E1` và finally ném `E2`. Caller nhận gì trong Java finally thủ công nếu không bảo toàn `E1`?

**Gợi ý:** lỗi cleanup `E2` có thể thay thế `E1`. Cần chính sách cause/suppressed khi muốn giữ cả hai.

### Bài 8 — Retry và metrics

RetryOutside(MetricsInside(Business)), business đã tạo đơn nhưng metrics ném lỗi. Rủi ro?

**Gợi ý:** retry bao cả metrics có thể gọi business lần nữa, nhân đôi đơn hàng. Cần phân loại lỗi và idempotency.

### Bài 9 — Thứ tự không xác định

Hai aspect không có quan hệ precedence được công cụ bảo đảm, nhưng ứng dụng yêu cầu Audit luôn bao Security. Nên làm gì?

**Gợi ý:** khai báo quan hệ thứ tự tường minh hoặc thiết kế lại thành một orchestration rõ ràng và kiểm thử; đừng dựa vào thứ tự tình cờ.

## 28. Checklist áp dụng khi phối hợp nhiều aspect

- [ ] Đã liệt kê chính xác các join point và advice cùng khớp.
- [ ] Phân biệt before, after returning, after throwing, after/finally và around.
- [ ] Xác định precedence giữa những aspect có quan hệ phụ thuộc.
- [ ] Đã kiểm tra quy tắc thứ tự **trong cùng một aspect** của công cụ.
- [ ] Không phụ thuộc vào thứ tự không được công cụ bảo đảm.
- [ ] Có sơ đồ ai ngoài, ai trong, ai gọi `proceed`.
- [ ] Có kiểm thử cả đường return và đường throw.
- [ ] Có test before advice chặn trước khi body chạy.
- [ ] Có test advice sau tự ném exception sau khi business thành công.
- [ ] Có test exception filtering cho after throwing.
- [ ] Giữ cause/suppressed theo chính sách khi nhiều tầng cùng thất bại.
- [ ] Có chính sách rõ với audit, authorization và transaction.
- [ ] Retry chỉ áp dụng lỗi hợp lệ, kiểm soát idempotency.
- [ ] Phân biệt method return và async task completion.
- [ ] Trace có invocation ID để phân biệt log đa luồng.
- [ ] Có ca âm tính cho điểm không khớp hoặc đường proxy bypass.
- [ ] Có cơ chế kiểm tra chuỗi event theo từng invocation.
- [ ] Không để logic log/metrics làm mất lỗi chính ngoài ý định.
- [ ] Đã đo overhead của nhiều tầng interception khi cần.
- [ ] Có tài liệu về những thứ tự ảnh hưởng correctness, không chỉ hiệu năng.

## 29. Tổng kết

**Quan hệ thứ tự giữa advice trước, sau và khi có lỗi phải được hiểu như một chuỗi thực thi lồng nhau, không chỉ một danh sách hàm có số thứ tự.** Với A bao B, đường vào thường đi A → B → business, còn đường ra thường đi business → B → A. Nhưng nhánh **return**, **throw**, **fallback**, **short-circuit** và **lỗi do chính advice tạo ra** có thể thay đổi những advice nào thực sự chạy.

Một thiết kế AOP đáng tin cần xác định **precedence chính thức**, **ranh giới proceed**, **kiểu after advice**, **nguồn exception**, **tác dụng phụ** và **đường thực thi thật**. Kiểm thử nên xác minh cả thứ tự event lẫn kết quả/ngoại lệ mà caller quan sát, đặc biệt khi phối hợp security, transaction, retry, metrics và audit.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-005">Phụ thuộc ngữ nghĩa giữa bảo mật, giao dịch và ghi nhật ký</a>

<details>
<summary>Xem chi tiết</summary>

# Phụ thuộc ngữ nghĩa giữa bảo mật, giao dịch và ghi nhật ký khi phối hợp nhiều aspect

## 1. Vì sao thứ tự aspect làm thay đổi kết quả?

Trong **Aspect-Oriented Programming (AOP)**, một method nghiệp vụ có thể cần đồng thời ba concern xuyên cắt:

- **Security:** xác thực chủ thể, kiểm tra quyền và từ chối thao tác trái phép.
- **Transaction:** tạo ranh giới làm việc với tài nguyên có commit/rollback và tính nhất quán dữ liệu.
- **Logging/Audit:** ghi nhận attempt, quyết định cho phép/từ chối, kết quả, ngoại lệ và sự kiện đã commit.

Một aspect có **pointcut** để chọn những **join point** và **advice** để bổ sung hành vi trước/sau/bao quanh điểm được chọn. Các concern có thể được mô tả độc lập ở mã nguồn nhưng **không độc lập về mặt ngữ nghĩa khi được kết hợp**.

Ba kiểu lồng nhau:

~~~text
Security(Logging(Transaction(Business)))
Logging(Security(Transaction(Business)))
Transaction(Logging(Security(Business)))
~~~

Có thể tạo hành vi khác nhau khi cùng nhận một request không có quyền:

- Security ngoài cùng từ chối trước khi Logging hoặc Transaction chạy, nên có thể **không có log denial** từ Logging nằm bên trong.
- Logging ngoài cùng có thể ghi attempt và denial trong khi Transaction không bắt đầu.
- Transaction ngoài cùng có thể **mở transaction cho một request sẽ bị từ chối**.
- Với request hợp lệ, nếu Logging ở **trong** Transaction, nó có thể ghi success khi Business trả về **trước khi commit thật xảy ra**.

Ngoài ra, nếu Logging catch và che một exception, Transaction ở bên ngoài có thể nhìn thấy normal return thay vì lỗi, từ đó đưa ra quyết định commit khác. Nếu Retry làm quanh Transaction, số transaction có thể nhiều hơn số request. Nếu transaction commit outcome không chắc chắn, log success hay retry cũng phải thận trọng.

**Thông điệp chính:** Thứ tự advice không chỉ ảnh hưởng trace và performance. Nó thay đổi **hợp đồng bảo mật, tính đúng của transaction outcome, ý nghĩa audit, exception propagation và khả năng quan sát**.

Đây là kiến thức AOP cơ bản và nâng cao nói chung; khả năng join point và quy tắc precedence của mỗi ngôn ngữ/bộ dệt khác nhau. Ví dụ Java 17 bên dưới là chương trình runnable mô phỏng **around composition**, không phải một trình biên dịch aspect thực.

## 2. Ôn lại join point, pointcut, advice và precedence

| Khái niệm | Ý nghĩa |
|---|---|
| **Join point** | Điểm thực thi mà ngôn ngữ AOP hỗ trợ nhận diện, ví dụ method call hoặc method execution |
| **Pointcut** | Quy tắc chọn tập join point cần áp dụng concern |
| **Before advice** | Chạy trước điểm, có thể ném lỗi từ chối |
| **After returning** | Quan sát join point được bao hoàn tất bình thường |
| **After throwing** | Quan sát ngoại lệ thoát khỏi join point được bao |
| **After/finally** | Hoạt động ở các đường rời nếu cơ chế hỗ trợ |
| **Around advice** | Bao toàn bộ continuation, có thể gọi hoặc không gọi tiếp |
| **Proceed** | Lệnh/contract chuyển quyền sang bước tiếp theo |
| **Precedence/order** | Xác định aspect nào nằm ngoài và aspect nào nằm trong |

Ký hiệu **A(B(Core))** nghĩa là lời gọi đến A trước, A có thể gọi tiếp B, rồi B gọi Core. Khi Core trả về bình thường, kết quả đi từ trong ra ngoài:

~~~text
Caller
  -> A.before
       -> B.before
            -> Core
       <- B.after
  <- A.after
Caller nhận result
~~~

Một around advice có thể short-circuit không gọi tiếp. Ví dụ Security từ chối ngay khi vào A sẽ khiến **không có hoạt động nào của B và Core**. Vì vậy không thể giả định mọi advice cùng khớp pointcut chắc chắn đều chạy.

Không tự suy thứ tự từ tên aspect, thứ tự khai báo hay thứ tự file. Một ngôn ngữ AOP có thể có precedence rules khác, và thứ tự before/after/around có thể phụ thuộc cách dệt. **Cần thiết lập hoặc xác minh precedence rõ ràng và kiểm tra integration tests.**

### 2.1. Chỉ so sánh cùng một boundary

Trong bài này biểu thức lồng nhau giả định các wrapper cùng bao **một operation**. Nếu Security được weave ở method execution, Logging ở call site và Transaction ở proxy, đó có thể là **các join point khác nhau** với thứ tự do đường thực thi quyết định. Trước khi chọn precedence, phải biết concern nào chặn loại join point nào và caller đi qua những lớp nào.

## 3. Security: bảo mật gồm nhiều nghĩa

**Authentication** xác định danh tính và độ tin cậy của actor. **Authorization** xác định actor có quyền thực hiện operation trên tài nguyên/đối tượng cụ thể hay không.

Một ví dụ đơn giản:

~~~text
actor = admin  -> cho phép
actor = guest  -> từ chối
~~~

Đây chỉ là policy minh họa. Trong hệ thống thực, quyền có thể phụ thuộc scope, tenant, owner, số dư, trạng thái bản ghi, version hoặc policy bên ngoài.

### 3.1. Thứ tự bảo mật và side effect

Một điều kiện an toàn căn bản là **không được thực hiện side effect nghiệp vụ không được phép**. Nếu Security nằm ngoài Business và có thể từ chối trước proceed, Business sẽ không chạy trên đường đó. Nhưng nếu caller có đường trực tiếp tới target mà bypass proxy, Security aspect tại proxy có thể không được kích hoạt.

Độ bao phủ của Security phải được kiểm chứng với direct call, self-invocation, callback và nhiều proxy, không chỉ happy path.

### 3.2. Kiểm tra trước transaction có luôn đúng?

Không luôn. Nếu quyền phụ thuộc vào **dữ liệu mutable**, kiểm tra quyền quá sớm có thể gặp **TOCTOU** (*time-of-check to time-of-use*):

~~~text
T1 kiểm tra record.owner = Alice -> cho phép
T2 sửa record.owner = Bob
T1 bắt đầu transaction và cập nhật record
~~~

Khi ấy quyết định ở T1 có thể đã lạc hậu trước lúc update. Một thiết kế có thể chia thành:

1. **Coarse check** bên ngoài transaction: danh tính, role hoặc scope không phụ thuộc record mutable.
2. **Resource-level authorization** bên trong transaction: kiểm tra owner/version/state trên dữ liệu nhất quán theo isolation/locking/optimistic concurrency hợp lý.
3. **Update có điều kiện** và database constraints bảo vệ invariant cuối.

Chỉ đưa check vào trong transaction **không tự bảo đảm không có race**; còn phải chọn isolation và conflict handling đúng. Vì vậy câu “Security luôn phải ngoài Transaction” không phải định luật phổ quát.

### 3.3. Denial có cần được audit?

Nếu audit mọi lần từ chối là bắt buộc, đặt Logging duy nhất bên trong Security có thể khiến denial không bao giờ vào Logging. Có thể dùng một **safe request audit** ngoài Security hoặc cho Security phát một security-decision event riêng.

Khi log trước authorization, **không ghi toàn bộ request payload, token, password, cookie hay thông tin nhạy cảm**. Chỉ ghi dữ liệu tối thiểu cần cho audit như correlation ID, operation label, actor ID hợp lệ nếu có, decision code và thời điểm.

## 4. Transaction: return khác commit

Một transaction advice dạng khái niệm:

~~~text
BEGIN
    result = proceed()
    COMMIT nếu policy cho phép
    return result
nếu lỗi:
    thực hiện xử lý rollback thích hợp
    ném lại lỗi
~~~

Đây là giả mã. Transaction thực có isolation, propagation, connection/resource state, rollback-only, nested/savepoint, commit failure và nhiều loại exception.

### 4.1. Đừng gọi mọi return là thành công

Xét:

~~~text
Transaction(
    Logging(
        Business
    )
)
~~~

Business trả OK. Logging bên trong ghi **SUCCESS**. Sau đó Transaction ngoài cùng **mới tiến hành COMMIT**. Nếu commit thất bại, log có thể báo thành công cho một thay đổi chưa được xác nhận bền vững.

Nên phân biệt:

- **BUSINESS_RETURNED:** method nghiệp vụ trả bình thường.
- **COMMIT_ATTEMPTED:** bắt đầu commit.
- **COMMIT_CONFIRMED:** transaction manager/storage báo commit thành công.
- **ROLLBACK_CONFIRMED:** rollback được xác nhận theo API.
- **OUTCOME_UNKNOWN:** không chắc commit có hoàn tất hay chưa.

Logging ngoài Transaction thường có thể quan sát transaction wrapper hoàn tất bình thường hay ném exception. Nhưng khi commit outcome chưa rõ, một wrapper ném lỗi **không tự chứng minh rollback đã hoàn tất**.

### 4.2. Rollback theo chính sách, không chỉ theo loại exception

Không có quy tắc AOP chung rằng mọi Throwable đều rollback:

- Một số transaction policy rollback trên một nhóm exception nhất định.
- Một số checked errors có thể được xử lý mà vẫn commit.
- Một advice trong cùng chain có thể swallow exception và trả fallback.
- Một rollback-only flag có thể được set ngay cả khi exception được catch.
- Một commit failure có thể xảy ra sau khi business method đã hoàn tất.

Phải định nghĩa **lỗi nào rollback**, **lỗi nào commit**, **lỗi nào trạng thái không xác định** và **ai chịu trách nhiệm báo cho caller**.

### 4.3. Bẫy catch-and-return

~~~text
Transaction(
    LoggingSwallows(
        Business throws
    )
)
~~~

Nếu LoggingSwallows catch lỗi rồi trả default value như một kết quả bình thường, Transaction ở ngoài có thể nghĩ operation thành công. Nếu transaction không được đánh dấu rollback-only bằng một cơ chế khác, nó có thể commit những thay đổi một phần.

**Không dùng Logging advice để nuốt lỗi nghiệp vụ**, trừ khi thay đổi exception contract là một quyết định có chủ đích, có test transaction policy tương ứng.

## 5. Logging: operational trace, audit và event committed khác nhau

Logging có thể phát những record ở thời điểm khác nhau:

| Event | Câu hỏi nó trả lời |
|---|---|
| ATTEMPT | Một request đã tiến đến boundary chưa? |
| AUTH_ALLOWED/AUTH_DENIED | Quyết định cho phép/từ chối là gì? |
| EXECUTION_STARTED | Body nghiệp vụ đã bắt đầu chưa? |
| BUSINESS_RETURNED | Method đã trả bình thường chưa? |
| COMMIT_CONFIRMED | Giao dịch đã được báo commit thành công chưa? |
| ROLLBACK/OUTCOME_UNKNOWN | Rollback hoặc kết quả không chắc chắn? |
| ERROR | Lỗi nào được quan sát tại boundary? |

**Console log không tương đương audit log bền vững.** Một operational logger có thể ghi disk, stdout, queue hoặc collector với semantics riêng. Audit theo yêu cầu bảo mật/pháp lý thường cần độ bền, quyền truy cập, retention và khả năng phát hiện chỉnh sửa.

### 5.1. Logging nằm ngoài Security

Ưu điểm: có thể thấy cả attempt bị Security từ chối, không phải mở Transaction nếu Security từ chối trước proceed.

Rủi ro: logging quá sớm có thể ghi dữ liệu không được phép, lộ thông tin hoặc tạo overhead dưới lưu lượng tấn công. Cần **redaction, rate limiting theo policy, data minimization** và log message được cấu trúc rõ ràng.

### 5.2. Logging nằm trong Transaction

Nếu Logging chỉ in ra console hay gửi remote message, thông điệp đó **không tự rollback cùng database**. Nếu Logging insert một audit row trong cùng transaction, row có thể rollback với nghiệp vụ, đồng nghĩa denial hoặc failure record bị mất.

Vị trí advice không tự xác định **độ bền của bản ghi**; còn phụ thuộc sink và transaction mà sink tham gia.

### 5.3. Logging nằm ngoài Transaction

Logging ngoài Transaction có thể biết continuation đã commit thành công theo API hoặc throw khi commit fail. Nhưng write vào log sink vẫn có thể thất bại sau khi database đã commit. Do đó không có **atomicity mặc định** giữa business DB commit và ghi log ngoài DB.

## 6. Ba thứ tự điển hình và sự khác biệt

Ký hiệu:

- S = Security
- L = Logging
- T = Transaction
- B = Business

| Composition | Denial | Có transaction khi deny? | Có log denial từ L? | Success log trước hay sau commit wrapper? |
|---|---|---|---|---|
| **S(L(T(B)))** | S từ chối trước proceed | Không | Không | L sau T nếu được allow |
| **L(S(T(B)))** | L thấy attempt rồi S từ chối | Không | Có | L sau T nếu được allow |
| **T(L(S(B)))** | T begin, L attempt, S từ chối | Có | Có | L trước T.commit |

Đây là kết quả theo **mô hình wrapper cùng boundary**, với S ném lỗi khi deny, T begin trước proceed, L ghi khi vào/ra. Không phải thứ tự mặc định của một engine AOP bất kỳ.

### 6.1. S(L(T(B))) — Security ngoài cùng

~~~text
Request denied:
    Security.check -> DENY -> Caller
    Không chạy Logging hoặc Transaction bên trong
~~~

Hiệu quả từ chối sớm, nhưng không đáp ứng yêu cầu audit denial nếu chỉ có Logging ở trong Security.

Đường success:

~~~text
Security.check
-> Logging.attempt
-> Transaction.begin
-> Business
-> Transaction.commit
-> Logging.success
-> Caller
~~~

### 6.2. L(S(T(B))) — Logging ngoài cùng

~~~text
Request denied:
    Logging.attempt
    -> Security.check -> DENY
    -> Logging.failure
    -> Caller
~~~

Không mở transaction cho denial, đồng thời ghi được rejection nếu Logging catch exception. Tuy nhiên **an toàn dữ liệu ghi log phải được thiết kế**.

Đường success:

~~~text
Logging.attempt
-> Security.check
-> Transaction.begin
-> Business
-> Transaction.commit
-> Logging.success
~~~

### 6.3. T(L(S(B))) — Transaction ngoài cùng

~~~text
Request denied:
    Transaction.begin
    -> Logging.attempt
    -> Security.check -> DENY
    -> Logging.failure
    -> Transaction.rollback-attempt
    -> Caller
~~~

Có thể mở transaction không cần thiết. Với success, Logging bên trong T có thể báo business success **trước** commit. Điều này không nên được gọi là **committed success**.

### 6.4. Không có một thứ tự tối ưu chung

Nếu quyền phụ thuộc dữ liệu trong database, một phương án nhiều tầng có thể tốt hơn:

~~~text
Safe request audit
    -> Coarse authentication/authorization
        -> Transaction
            -> Resource-level authorization
                -> Business update
~~~

Cần xác minh pointcut, consistency, isolation và độ bền audit. Đây là một mô hình tư duy, không phải yêu cầu mọi hệ thống phải dùng.

## 7. Java 17 runnable: mô phỏng ba thứ tự và hai loại lỗi

Chương trình dùng **Java 17 thuần**, mô phỏng quanh một operation bằng các **wrapper functions**. Nó không chạy AOP compiler hoặc tạo transaction database thật. Mục đích là làm rõ **semantics của precedence**, short-circuit và exception flow.

Các wrapper:

- Security kiểm tra actor == admin và throw SecurityException nếu không hợp lệ.
- Transaction in begin, thực thi continuation, in commit nếu không lỗi; với lỗi in **rollback-attempt** rồi rethrow.
- Logging in attempt; nếu thành công in success; nếu continuation throw thì in failure rồi rethrow.
- Business in core: execute và có thể ném lỗi mô phỏng.
- Có lỗi commit giả lập để phân biệt body return với transaction completion.

Tạo file **AspectSemanticsDemo.java**:

~~~java
public class AspectSemanticsDemo {
    record Request(String actor, boolean failBody, boolean failCommit) {}

    @FunctionalInterface
    interface Work {
        String run(Request request);
    }

    static Work core() {
        return request -> {
            System.out.println("core: execute");
            if (request.failBody()) {
                throw new IllegalStateException("business-failed");
            }
            return "OK";
        };
    }

    static Work security(Work next) {
        return request -> {
            System.out.println("security: check");
            if (!"admin".equals(request.actor())) {
                throw new SecurityException("denied");
            }
            return next.run(request);
        };
    }

    static Work transaction(Work next) {
        return request -> {
            System.out.println("tx: begin");
            try {
                String result = next.run(request);
                if (request.failCommit()) {
                    throw new IllegalStateException("commit-failed");
                }
                System.out.println("tx: commit");
                return result;
            } catch (RuntimeException e) {
                System.out.println("tx: rollback-attempt");
                throw e;
            }
        };
    }

    static Work logging(Work next) {
        return request -> {
            System.out.println("log: attempt");
            try {
                String result = next.run(request);
                System.out.println("log: success " + result);
                return result;
            } catch (RuntimeException e) {
                System.out.println("log: failure " + e.getMessage());
                throw e;
            }
        };
    }

    static void scenario(String name, Work pipeline, Request request) {
        System.out.println("=== " + name + " ===");
        try {
            System.out.println("caller: " + pipeline.run(request));
        } catch (RuntimeException e) {
            System.out.println("caller: " + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Request denied = new Request("guest", false, false);
        Request success = new Request("admin", false, false);
        Request businessFail = new Request("admin", true, false);
        Request commitFail = new Request("admin", false, true);

        Work securityOuter = security(logging(transaction(core())));
        Work loggingOuter = logging(security(transaction(core())));
        Work transactionOuter = transaction(logging(security(core())));

        scenario("SEC-LOG-TX denied", securityOuter, denied);
        scenario("LOG-SEC-TX denied", loggingOuter, denied);
        scenario("TX-LOG-SEC denied", transactionOuter, denied);
        scenario("LOG-SEC-TX success", loggingOuter, success);
        scenario("SEC-LOG-TX body failure", securityOuter, businessFail);
        scenario("LOG-SEC-TX commit failure", loggingOuter, commitFail);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AspectSemanticsDemo.java
java AspectSemanticsDemo
~~~

**Stdout đã được kiểm chứng bằng Java 17:**

~~~text
=== SEC-LOG-TX denied ===
security: check
caller: SecurityException
=== LOG-SEC-TX denied ===
log: attempt
security: check
log: failure denied
caller: SecurityException
=== TX-LOG-SEC denied ===
tx: begin
log: attempt
security: check
log: failure denied
tx: rollback-attempt
caller: SecurityException
=== LOG-SEC-TX success ===
log: attempt
security: check
tx: begin
core: execute
tx: commit
log: success OK
caller: OK
=== SEC-LOG-TX body failure ===
security: check
log: attempt
tx: begin
core: execute
tx: rollback-attempt
log: failure business-failed
caller: IllegalStateException
=== LOG-SEC-TX commit failure ===
log: attempt
security: check
tx: begin
core: execute
tx: rollback-attempt
log: failure commit-failed
caller: IllegalStateException
~~~

### 7.1. Vì sao thứ tự thay đổi log denial?

Ở **SEC-LOG-TX denied**, Security throw trước khi gọi Logging, nên không có log attempt/denied.

Ở **LOG-SEC-TX denied**, Logging in attempt, Security throw, Logging catch và in failure denied. Transaction không được chạy.

Ở **TX-LOG-SEC denied**, Transaction begin trước khi kiểm tra quyền, nên có rollback-attempt dù nghiệp vụ chưa thực hiện.

### 7.2. Success phải sau transaction wrapper

Ở **LOG-SEC-TX success**, thứ tự:

~~~text
Logging.attempt
Security.check
Transaction.begin
Business
Transaction.commit
Logging.success
~~~

Về mặt continuation, Logging nằm ngoài T nhìn thấy commit wrapper đã hoàn tất. Tuy nhiên console log này **không là audit record atomic với DB commit**.

### 7.3. Body failure khác commit failure

Trong **body failure**, Core ném IllegalStateException trước khi hoàn tất; transaction wrapper in rollback-attempt, rồi Logging in failure.

Trong **commit failure**, Core đã trả OK nhưng wrapper gặp lỗi ở bước commit giả lập, nên Logging ở ngoài chỉ thấy failure. Trong database thật, commit error **có thể có kết quả không xác định**, và rollback-attempt không chứng minh rollback thành công.

### 7.4. Giới hạn của demo

Không có database, broker, distributed transaction, bytecode weaver hay một engine pointcut thật. Kết quả đo được chứng minh **wrapper ordering và exception propagation**, không chứng minh atomic commit/rollback hoặc default precedence của AOP tool.

## 8. Logger nằm trước commit có thể báo sai outcome

Xét:

~~~text
Transaction(
    Logging(
        Business
    )
)
~~~

Business return OK -> Logging ghi success -> Transaction mới commit. Nếu commit throw, success log đã xảy ra **quá sớm**.

Do đó log schema nên phân biệt:

~~~text
ATTEMPT
BUSINESS_RETURNED
COMMIT_ATTEMPTED
COMMIT_CONFIRMED
COMMIT_FAILED
OUTCOME_UNKNOWN
~~~

Đừng đặt tên event **TRANSACTION_COMMITTED** tại advice chỉ nhìn thấy Business returned. Đừng nhầm after-returning advice của method nghiệp vụ với “after commit” khi Transaction là một wrapper khác ở bên ngoài.

### 8.1. Sink log nào có thể rollback?

- Console/file: thường ngoài database transaction.
- Remote log collector: có thể lưu hoặc fail độc lập.
- Database audit row trong cùng transaction: có thể bị rollback với business update.
- Message gửi trực tiếp đến broker: không tự bị DB rollback.
- Outbox row trong cùng database transaction: có thể commit/rollback cùng business row khi thực sự tham gia cùng transaction.

Nếu rollback cũng xóa security-denial audit row, chính sách ghi nhận denial có thể bị phá. Nếu remote log sống sau rollback, “business success” ghi sớm có thể sai sự thật.

## 9. Transactional outbox: gắn event với commit

Khi một transaction thay đổi business data và phải gửi audit/business event sang hệ thống khác, có hai khoảng lỗi:

**Gửi sau commit:**

~~~text
UPDATE database -> COMMIT -> PUBLISH event
~~~

Process có thể crash giữa commit và publish; dữ liệu đã thay đổi nhưng không có event.

**Gửi trước commit:**

~~~text
PUBLISH event -> UPDATE database -> COMMIT
~~~

Nếu database rollback, event có thể nói về dữ liệu chưa từng commit.

**Transactional outbox** ghi business update và một outbox row trong **cùng transaction database**:

~~~text
BEGIN
    resource authorization
    update business rows
    insert outbox event with stable event ID
COMMIT
    |
    v
relay đọc outbox rows committed
    |
    v
publish (có retry)
    |
    v
consumer idempotent/deduplicate
~~~

Khi cùng transaction, outbox row không commit nếu business update rollback. Relay có thể publish trùng nếu crash sau gửi nhưng trước xác nhận. Vì thế **outbox không bảo đảm exactly-once end-to-end**; consumer idempotency và deduplication vẫn cần.

**Phân biệt** security denial audit với committed business event: denial có thể cần ghi vào kênh audit riêng không bị rollback bởi transaction nghiệp vụ, còn business event thường cần atomicity với thay đổi dữ liệu.

## 10. TOCTOU: authorization và transaction cần phối hợp

Nếu resource ownership hoặc hạn mức thay đổi theo thời gian, quyết định quyền có thể không còn đúng giữa check và update. Đề xuất khái niệm:

~~~text
Safe attempt logger
  |
  v
Authentication + coarse permission
  |
  +-- DENY -> safe audit, không vào business transaction
  |
  v
Transaction begin
  |
  v
Read resource under intended consistency/isolation
  |
  v
Resource-level authorization
  |
  +-- DENY -> xử lý transaction, audit denial theo policy
  |
  v
Conditional update + outbox record
  |
  v
Commit
~~~

Cần xác định isolation, optimistic locking, row locks hoặc constraints. Một transaction vẫn có thể gặp concurrent anomaly nếu isolation không đủ. **AOP precedence không tự thay thế thiết kế nhất quán database**.

### 10.1. Nếu security policy nằm ngoài transaction thì sao?

Có thể phù hợp với claim/token checks hoặc thông tin tương đối bất biến. Nhưng quyền theo record mutable có thể cần **revalidation** ngay tại update. Thiết kế chỉ check một lần ở ngoài cần chứng minh được không có TOCTOU theo threat model.

## 11. Exception propagation và quyết định rollback

Các tầng quanh nhau có thể ném, bắt, đổi hoặc nuốt exception.

**Security throw:** nếu Security ngoài T, T chưa chạy; nếu T ngoài Security, T đã begin và có thể thấy exception.

**Business throw:** Transaction nhận lỗi nếu exception đi qua không bị nuốt. Rollback behavior phụ thuộc policy.

**Logging throw:** operational logger có thể thay exception gốc hoặc khiến outer Transaction rollback, dù Business chạy thành công. Cần policy cố ý: fail-open cho observability không thiết yếu, hoặc fail-closed cho audit bắt buộc.

**Exception bị nuốt:** Logging nằm trong T catch lỗi và trả fallback có thể làm outer T tưởng continuation thành công. Đừng biến một logging advice thành nơi quyết định kết quả nghiệp vụ không rõ ràng.

**Commit throw:** Logging ở trong T đã chạy trước commit, nên không tự quan sát được commit failure; Logging ở ngoài T nhận lỗi wrapper nhưng không mặc nhiên biết kết quả durable.

### 11.1. Giữ lỗi gốc và ghi lỗi thứ cấp

Khi log failure cũng gặp lỗi, cần không che exception gốc một cách vô tình. Một chính sách vận hành có thể ghi lỗi logger bằng kênh dự phòng, thêm suppressed exception nếu phù hợp hoặc trả một error có cấu trúc rõ. Với security audit bắt buộc, có thể cần từ chối operation, nhưng quyết định đó phải được thiết kế/test, không tùy tiện.

## 12. After advice và ranh giới quan sát

**After returning** của Business chỉ biết Business method return. Nó **không biết transaction ngoài cùng đã commit**.

**After throwing** chỉ thấy exception thoát ra từ join point được bao; nếu một interceptor khác catch và return fallback thì không nhất thiết nhìn thấy lỗi ấy.

**After/finally** có thể chạy khi continuation return hoặc throw; nhưng advice finally cũng có thể throw và che lỗi gốc.

**Around** nhìn thấy toàn bộ continuation nó bao. Nếu muốn quan sát transaction wrapper normal completion, Logging around cần nằm ngoài transaction boundary hoặc đăng ký một callback sau commit do transaction mechanism cung cấp nếu có.

**Không dùng tên loại advice thay thế việc xác định chính xác điểm được chọn và thứ tự thực thi.**

## 13. Retry, idempotency và nhiều transaction

Giả sử thêm **Retry**:

~~~text
Logging(Retry(Transaction(Business)))
~~~

Logging ngoài có thể ghi một request attempt, trong khi Retry tạo nhiều transaction attempts.

~~~text
Retry(Logging(Transaction(Business)))
~~~

Logging nằm trong Retry có thể ghi một attempt/failure cho mỗi lần thử.

~~~text
Transaction(Retry(Business))
~~~

Retry ở trong một transaction có thể gặp transaction đã bị đánh dấu rollback-only và có thể giữ lock lâu hơn cần thiết. Không có thứ tự “an toàn” chung khi chưa biết loại lỗi và side effects.

Nếu commit outcome uncertain, retry có thể tạo một bản ghi/thanh toán/gửi message trùng. Cần **idempotency key**, database constraint hoặc reconciliation để nhận diện request đã được xử lý. Transaction không tự rollback email, HTTP request hoặc event publish đã được gửi trước đó.

Log nên phân biệt **request ID** và **attempt ID**. Một request có ba attempts không phải ba yêu cầu nghiệp vụ mới.

## 14. Async và context propagation

Một aspect có thể bắt đầu transaction trong thread A, nhưng Business chuyển công việc sang thread B.

~~~text
Caller thread:
    Security -> Transaction begin -> schedule async task -> return
    Transaction commit/close

Worker thread:
    async operation vẫn đang chạy
~~~

Nếu method trả một future/task handle, Logging after returning có thể thấy **scheduling thành công** chứ không phải hoàn tất nghiệp vụ. Transaction của caller có thể không bao toàn bộ công việc ở thread khác.

Không giả định thread-local security/transaction context tự chuyển qua async worker. Cần biết công cụ có context propagation hay không, dữ liệu nào được phép truyền, khi nào future hoàn tất, và transaction của từng phần công việc bắt đầu/kết thúc ở đâu.

Nếu operation thật sự bất đồng bộ, định nghĩa event theo **task accepted / task completed / task failed** và kiểm thử completion, không gán chung success cho mọi bước.

## 15. Multiple join points và logging hai lần

Security có thể chặn execution, Transaction gắn ở proxy, Logging chọn call site. Khi đó một request có thể sinh nhiều join points:

~~~text
Caller [call-site logging]
   |
   v
Proxy [authorization]
   |
   v
Woven target execution [transaction + logging?]
   |
   v
Business method
~~~

Nếu cả Logging ở call site và Logging ở execution đều ghi “operation success” không phân biệt, metric có thể bị đếm hai lần. Nếu target tự gọi một method có execution advice, còn có event nội bộ.

Cần log **join point kind, method signature, correlation ID** và đếm riêng request, attempt, transaction, target execution. Thứ tự event phải kiểm tra trên **đường runtime thực**, không chỉ trên danh sách advice.

### 15.1. Proxy bypass

Một Security aspect chỉ gắn vào proxy có thể không chạy nếu caller giữ target reference và gọi trực tiếp, hoặc target tự gọi method khác trên chính mình mà không quay qua proxy. Với chính sách bảo mật bắt buộc, cần kiểm tra bao phủ cả direct/self-invocation/callback hoặc chọn một cơ chế instrument khác phù hợp.

## 16. Kiểm soát độ bền và tính an toàn của log

Một audit pipeline đáng tin cần hỏi:

1. Ai được phép tạo audit record?
2. Request bị từ chối có bắt buộc được ghi không?
3. Ghi metadata nào là đủ? Có lộ secret không?
4. Record là attempt, authorization decision, business return hay commit outcome?
5. Sink thuộc cùng DB transaction hay độc lập?
6. Có thể mất record khi process crash/network partition không?
7. Có thể nhận duplicate do retries/outbox relay không?
8. Có retention, access control và tamper evidence phù hợp không?
9. Correlation/request/attempt/event IDs được sinh ở đâu?
10. Audit sink lỗi thì **fail-open** hay **fail-closed**?
11. Có giới hạn log flood trên denied requests không?
12. Có thể liên kết event với policy version hay transaction outcome không?

Không log raw credentials, session tokens, authentication secrets hoặc object.toString không kiểm soát. Dữ liệu audit và quyền truy cập audit cũng là một phần của thiết kế bảo mật.

## 17. Chi phí vận hành: thứ tự nào cũng có giá

- Transaction ngoài Security có thể tạo transaction không cần thiết cho denied requests.
- Logging ngoài Security có thể nhận lưu lượng rejected request lớn và phải redaction/rate limit.
- Resource-level authorization trong transaction có thể thêm truy vấn hoặc lock nhưng là chi phí bảo đảm consistency.
- Ghi remote audit đồng bộ có thể tăng latency và trở thành điểm lỗi.
- Outbox thêm bảng, relay, retention và deduplication nhưng hỗ trợ coupling bền vững với commit.
- Retry mở nhiều transaction có thể tăng lock contention và nguy cơ side effect trùng.

Không chọn precedence chỉ vì ít lời gọi advice nhất. **Đầu tiên xác định invariants và yêu cầu audit**, sau đó đo throughput, p95/p99 latency, transaction duration, queue lag và tỷ lệ error.

## 18. Checklist thiết kế precedence

1. **Invariant:** Không cập nhật dữ liệu khi không có quyền; không ghi commit success trước commit.
2. **Audit semantics:** Denial/attempt có cần bền vững không?
3. **Data minimization:** Logging ngoài Security có an toàn không?
4. **Authorization inputs:** Quyền có phụ thuộc database record mutable không?
5. **Transaction isolation:** Có chống TOCTOU/races theo yêu cầu không?
6. **Composition:** Aspect nào ngoài, aspect nào trong, precedence được khai báo/test ra sao?
7. **Rollback policy:** Exception nào rollback, exception nào không, lỗi bị swallow thì sao?
8. **Commit outcome:** Đã phân biệt confirmed/failed/unknown chưa?
9. **External side effects:** Có outbox, idempotency và retry policy không?
10. **Async:** Transaction/security context có theo đúng operation không?
11. **Logging failure:** Có che lỗi gốc hoặc đổi business result ngoài ý muốn không?
12. **Coverage:** Có direct call/proxy bypass/nested join points không?
13. **Diagnostics:** Request ID, attempt ID và policy ID có truy vết được không?
14. **Test cases:** Denial, business failure, commit failure, logger failure và concurrency đều được kiểm chứng.

## 19. Ma trận kiểm thử tối thiểu

| Kịch bản | Điều phải xác minh |
|---|---|
| Unauthorized request | Business không thực thi và không tạo side effect |
| S(L(T(B))) denied | L/T không chạy nếu S short-circuit |
| L(S(T(B))) denied | L ghi denial an toàn, T không begin |
| T(L(S(B))) denied | T bắt đầu trước S; rollback policy rõ |
| Authorized success | Thứ tự commit/success log như định nghĩa |
| Business body throws | T rollback theo policy; log failure chính xác |
| Commit fails | Không ghi committed success; xử lý unknown outcome |
| Logger throws | Exception policy rõ, không vô ý che lỗi nghiệp vụ |
| Inner advice swallows error | T không commit sai vì thấy normal return |
| Permission changed concurrently | Resource-level authorization không TOCTOU |
| Outbox transaction rolls back | Không có committed event row |
| Relay publishes twice | Consumer idempotent |
| Async task fails after return | Không log completion success quá sớm |
| Retry on uncertain commit | Không nhân đôi side effect |
| Proxy bypass/self-call | Security policy có được bao phủ không |
| Multiple logging join points | Tránh đếm trùng metric |
| Denied request with secrets | Audit không ghi token/password |
| Audit sink outage | Fail-open/closed đúng yêu cầu |

## 20. Lỗi phổ biến

**Lỗi 1 — Xem thứ tự aspect chỉ là chuyện hiệu năng.** Nó có thể thay đổi security coverage, transaction và outcome logs.

**Lỗi 2 — Cho Security luôn phải ngoài Transaction.** Quyền phụ thuộc dữ liệu mutable có thể cần recheck trong transaction.

**Lỗi 3 — Đặt Transaction ngoài tất cả để “chắc chắn”.** Có thể mở transaction cho denied requests.

**Lỗi 4 — Logging trong T ghi success trước commit.** Success ấy chỉ có nghĩa Business returned, không phải committed.

**Lỗi 5 — Logging ngoài T luôn là atomic audit.** DB commit và log sink độc lập có thể fail ở hai thời điểm.

**Lỗi 6 — Audit trong DB transaction luôn giữ denial.** Rollback có thể xóa audit row cùng transaction.

**Lỗi 7 — Rollback-attempt chứng minh rollback hoàn tất.** Outcome có thể không chắc chắn.

**Lỗi 8 — Mọi exception tự động rollback.** Phụ thuộc transaction policy.

**Lỗi 9 — Nuốt exception trong Logging không nguy hiểm.** Có thể làm Transaction ngoài commit sai.

**Lỗi 10 — Logger failure không thể đổi kết quả nghiệp vụ.** Advice throw có thể ảnh hưởng caller/rollback.

**Lỗi 11 — Log mọi arguments trước security là bình thường.** Có thể làm lộ dữ liệu.

**Lỗi 12 — Retry vô điều kiện khi commit fail.** Có thể tạo duplicate side effects.

**Lỗi 13 — Outbox bảo đảm exactly-once toàn hệ thống.** Relay/consumer vẫn cần dedup và idempotency.

**Lỗi 14 — Async method trả bình thường nghĩa công việc đã xong.** Có thể chỉ schedule task.

**Lỗi 15 — Tất cả aspects có chung điểm và luôn một event.** Call/execution, proxy và retries có thể tạo nhiều event.

**Lỗi 16 — Precedence mặc định có thể suy từ tên file.** Phải tra semantics công cụ và test.

## 21. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** Với S(L(T(B))), bị từ chối trước proceed, L có chạy không?

**Đáp:** Không trong mô hình này; S short-circuit trước L.

**Câu 2.** Với L(S(T(B))), request bị deny có vào transaction không?

**Đáp:** Không nếu S throw trước gọi T; L có thể ghi denial.

**Câu 3.** Với T(L(S(B))), denial có thể mở transaction không?

**Đáp:** Có vì T begin trước S.

**Câu 4.** Logging trong T ghi success sau Business return, vậy DB đã commit chưa?

**Đáp:** Chưa chắc; T.commit còn ở phía ngoài.

**Câu 5.** Logging ngoài T thấy T return, log bền vững được đảm bảo không?

**Đáp:** Không; log sink vẫn có thể thất bại độc lập với database.

**Câu 6.** Vì sao authorization có thể phải kiểm tra trong transaction?

**Đáp:** Khi quyền gắn với state mutable cần isolation/version validation để tránh TOCTOU.

**Câu 7.** Có nên kiểm tra coarse role trước transaction dù resource check cần ở bên trong?

**Đáp:** Có thể có ích để từ chối sớm; resource-level check vẫn cần khi dữ liệu mutable.

**Câu 8.** Nếu một interceptor catch lỗi business rồi return fallback, T ở ngoài có thể làm gì?

**Đáp:** Nó có thể commit nếu policy dựa normal return và không có rollback-only flag.

**Câu 9.** Commit failure có chắc rollback được không?

**Đáp:** Không; outcome có thể unknown, cần idempotency/reconciliation.

**Câu 10.** Outbox có giải quyết audit denial không?

**Đáp:** Không mặc định; denial có thể không có committed business transaction hoặc outbox row bị rollback, cần kênh phù hợp.

**Câu 11.** Vì sao cần request ID và attempt ID riêng?

**Đáp:** Một request có thể được retry nhiều lần, tạo nhiều transaction/advice events.

**Câu 12.** Một async operation đã schedule nhưng chưa hoàn tất, ghi success hoàn tất có đúng không?

**Đáp:** Không; chỉ nên ghi scheduled/accepted trừ khi quan sát được completion.

**Câu 13.** Khi Security nằm ở proxy, target self-invocation có thể bypass không?

**Đáp:** Có trong một số proxy models; phải test đúng mechanism.

**Câu 14.** Có một thứ tự Security/Transaction/Logging luôn đúng cho mọi hệ thống không?

**Đáp:** Không. Phải dựa vào invariants, nguồn authorization, audit durability, transaction scope và join points.

## 22. Sơ đồ ghi nhớ

~~~text
                      REQUEST
                         |
                         v
                Safe request logging
                         |
                         v
               Coarse security check
                  |             |
                DENY           ALLOW
                  |             |
                  v             v
              Safe audit    Transaction begin
                                 |
                                 v
                      Resource authorization
                                 |
                        +--------+--------+
                        |                 |
                      DENY               ALLOW
                        |                 |
                 error/rollback      Business update
                                          |
                                          v
                                  Outbox record
                                          |
                                          v
                                    Commit attempt
                                      |       |
                                      v       v
                                  Confirmed  Unknown/failed
                                      |       |
                                      v       v
                                 Durable     Reconciliation/
                                 delivery    error policy
~~~

**Kết luận:** Security, Transaction và Logging là ba mối quan tâm xuyên cắt có **phụ thuộc ngữ nghĩa rõ ràng**. Security quyết định quyền và điểm dừng, Transaction quyết định ranh giới nhất quán/commit/rollback, Logging phải gọi đúng tên những sự kiện nó thật sự biết và đảm bảo dữ liệu ghi được bảo vệ. Chọn precedence từ **invariant bảo mật và tính đúng dữ liệu**, kiểm chứng trên đường lỗi/retry/async/commit, sau đó mới tối ưu overhead.

## Tài liệu tham khảo

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): join point, pointcut, around advice và precedence trong một ngôn ngữ AOP cụ thể.
- [Java SE 17 — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): exception propagation, cause và suppressed exceptions.
- [Java Language Specification — Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): semantics của exception.
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html): lưu outbox row cùng database transaction rồi relay sự kiện.
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html): log nhạy cảm, security events và audit reliability.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-006">Thiết kế thứ tự tường minh thay vì dựa vào chi tiết triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế thứ tự tường minh thay vì dựa vào chi tiết triển khai

## 1. Vấn đề: khi nhiều aspect cùng can thiệp vào một điểm

Trong **Aspect-Oriented Programming (AOP)**, một **join point** được pointcut chọn có thể đồng thời phù hợp với nhiều aspect. Chẳng hạn một lời gọi xử lý đơn hàng có thể cần tracing, đo latency, retry lỗi tạm thời, caching, audit, kiểm tra quyền và quản lý tài nguyên. Không phải chỉ cần từng advice **đúng khi chạy riêng** là cả hệ thống sẽ đúng khi ghép chúng lại: **thứ tự phối hợp** có thể thay đổi những điều mà mỗi advice quan sát, thời điểm nó chạy, số lần target được thực thi và kết quả cuối cùng.

Giả sử hai advice đều hoạt động khi dùng riêng:

- `Retry` có thể tiếp tục vào target nhiều lần khi gặp một loại lỗi tạm thời.
- `Metrics` đếm các lần gọi và đo khoảng thời gian.

Nếu Metrics nằm **bên ngoài** Retry, nó có thể đo một **logical operation** gồm tất cả attempts và backoff. Nếu Metrics nằm **bên trong** Retry, nó có thể chạy **mỗi attempt**. Cả hai thiết kế đều có thể hợp lý, nhưng mang **nghĩa khác nhau**.

Bởi vậy, trọng tâm bài học là:

> **Định nghĩa quan hệ thứ tự bằng yêu cầu ngữ nghĩa có thể kiểm chứng, khai báo quan hệ ấy rõ ràng, và thất bại sớm nếu nhiều advice không thể được sắp thành một chuỗi nhất quán. Không dựa vào tên class, thứ tự khai báo, thứ tự quét package, cấu trúc collection hay tình cờ của classloader.**

### Mục tiêu

Sau bài này, bạn có thể:

1. Phân biệt **thứ tự advice**, **thứ tự aspect instance được tạo**, **thứ tự đăng ký**, **thứ tự weaving** và **thứ tự sự kiện runtime**.
2. Hiểu cách nhiều around advice **lồng nhau** và vì sao hành vi trước, sau, lỗi và `finally` có thứ tự khác nhau.
3. Xây **quan hệ ràng buộc tường minh** như `Trace bao ngoài Retry`, `Retry bao ngoài MetricAttempt` theo mục tiêu đo.
4. Thiết kế precedence bằng **priority**, **partial order/đồ thị phụ thuộc**, **nhóm concern** và chính sách xử lý tie/cycle.
5. Phân tích tình huống phức tạp: retry, cache, authorization, transaction, audit, short-circuit, error handling, async completion và nested calls.
6. Tránh các lỗi như dựa vào thứ tự reflection, load class, tên file hay trật tự các phần tử trong `HashMap`.
7. Chạy chương trình **Java 17** hoàn chỉnh, tự sắp chuỗi interceptors với priority rõ ràng và cho thấy metric “mỗi attempt” khác metric “mỗi logical call”.
8. Kiểm thử cấu hình thứ tự theo cả **success, failure, skip, retry, concurrency và bất đồng bộ**.

Phạm vi là **AOP cơ bản và nâng cao nói chung**. Cú pháp đặt precedence, quy tắc mặc định và ý nghĩa priority cụ thể **không giống nhau giữa mọi AOP engine**. Các ví dụ thứ tự trong bài sử dụng **chính sách được định nghĩa tường minh**, không áp nó như một chuẩn của mọi công cụ.

## 2. Những loại thứ tự không được đánh đồng

Một rule đặt **aspect A ở ngoài B** không đồng nghĩa mọi dòng log của A phải xuất hiện trước B. Để thiết kế một thứ tự **tường minh**, trước tiên tách các cấp sau:

| Thứ tự | Dữ liệu đang nói tới | Có thể dùng làm hợp đồng precedence? |
| --- | --- | --- |
| **Source/khởi tạo** | Thứ tự file, class declaration, tạo instance | Không tự bảo đảm thứ tự advice |
| **Đăng ký** | Interceptor được thêm vào registry theo thứ tự nào | Chỉ khi API công bố quy tắc sắp xếp |
| **Build/weaving** | Trình biên dịch/bộ dệt xử lý các input theo thứ tự nào | Có thể ảnh hưởng artifact, không mặc định quyết định runtime nesting |
| **Aspect precedence / advice order** | Quan hệ bắt buộc giữa những lớp advice được chọn | Là đối tượng cần đặc tả theo engine, kể cả advice khác loại |
| **Runtime events** | Trình tự enter, return, throw, finally, short-circuit và retry | Hệ quả của thứ tự kết hợp **cùng** luồng điều khiển |
| **Business effects** | Quyền, giao dịch, ghi dữ liệu, commit, phát sự kiện | Không được suy từ vị trí một log đơn lẻ |

**Coverage khác order.** Dù precedence được mô tả chính xác, advice vẫn không chạy nếu pointcut không khớp, join point ngoài weaving scope, caller bỏ qua proxy, điều kiện runtime sai hoặc một advice ở ngoài không gọi continuation. Sửa thứ tự không chữa được việc chọn sai tập điểm hay sai đường gọi.

Tương tự, với before, after-returning, after-throwing, finally và around, ý nghĩa của “trước” hoặc “sau” gắn với **ranh giới cụ thể**. Cần kiểm thử trên cả normal return, exception và short-circuit; không xem thứ tự từng xuất hiện trong một lần chạy là cam kết của công cụ AOP.

## 3. Mô hình nền tảng: thứ tự bao ngoài và bao trong

Giả sử có hai around advice A và B. Ta tuyên bố **A bao ngoài B**:

~~~text
caller
  |
  v
A.before
  |
  +--> A.proceed()
          |
          v
       B.before
          |
          +--> B.proceed()
                  |
                  v
                 target
          |
       B.after
  |
A.after
  |
caller
~~~

Nếu target trả bình thường, cả A và B gọi `proceed` đúng một lần, thứ tự quan sát:

~~~text
A before
B before
TARGET
B after
A after
~~~

**Nhận xét cốt lõi:** *Before ở tầng ngoài chạy trước before tầng trong, nhưng after ở tầng ngoài chạy sau after tầng trong.* Vì vậy một rule “aspect A ưu tiên trước B” cần làm rõ nó nói về **nesting/precedence** chứ không phải mọi dòng log của A đều đứng trước mọi dòng log của B.

### 3.1. Trên đường lỗi

Nếu target ném `E` và cả hai advice chỉ ghi nhận rồi ném lại:

~~~text
A before
B before
TARGET throws E
B catch E
B finally
A catch E
A finally
CALLER receives E
~~~

Nếu B **bắt E rồi trả fallback**, A có thể chỉ thấy **normal return**. Nếu B biến đổi E thành lỗi E2, A thấy E2. **Lỗi quan sát ở advice ngoài có thể đã được advice trong chuyển đổi.** Do đó thứ tự là một phần của hợp đồng error semantics.

### 3.2. Nếu advice ngoài không gọi `proceed`

~~~text
A around:
    if cacheHit:
        return cachedValue
    return proceed()
~~~

Khi `cacheHit=true`, B và target có thể **không chạy**. Không có nghĩa B sai priority; B đơn giản chưa nhận occurrence ở nhánh này.

Nếu B là kiểm tra quyền **bắt buộc** và nằm trong một cache có thể return sớm, đó có thể là lỗi kiến trúc nghiêm trọng. Cần thiết kế sao cho policy bắt buộc không thể bị bỏ qua hoặc được bảo vệ ở trusted boundary độc lập.

### 3.3. Nếu advice gọi tiếp nhiều lần

~~~text
A around retry:
    for attempt in attempts:
        try:
            return proceed()
        catch transientError:
            maybeRetry()
~~~

Nếu B nằm trong A, **B có thể chạy theo mỗi attempt**. Nếu B nằm ngoài A, B có thể chỉ chạy **một lần cho logical operation**. Ngữ nghĩa này cũng phụ thuộc engine có cho phép gọi continuation nhiều lần hay không và việc inner advice được tái chạy như thế nào. Không tự áp dụng pseudocode retry cho mọi engine.

## 4. Viết mục tiêu ngữ nghĩa trước khi đặt priority

Một thiết kế tốt không bắt đầu bằng “cho aspect X priority 1, Y priority 2” khi chưa biết **vì sao**.

Thay vào đó, viết requirement có thể test:

- “Trace tổng đo từ lúc caller đi vào boundary tới khi retry kết thúc.”
- “Metric `attempts` tăng một lần cho mỗi lần target được thử.”
- “Authorization phải được kiểm tra trước khi bất kỳ đường nào tạo side effect nhạy cảm.”
- “Audit thành công chỉ được ghi sau khi điều kiện xác nhận thành công được thỏa, không chỉ khi target return tạm thời.”
- “Cache hit có được tính là một request logic hay một target invocation?”
- “Cleanup span xảy ra cả success và failure; với async phải theo task completion nếu span được định nghĩa end-to-end.”
- “Lỗi của telemetry không được che lỗi nghiệp vụ nếu telemetry được công bố là best effort.”

Sau đó mới dịch thành quan hệ:

~~~text
TraceTotal OUTER_THAN Retry
Retry OUTER_THAN MetricPerAttempt
Authorization OUTER_THAN CacheWithEarlyReturn
~~~

Đây là **cú pháp biểu diễn quan hệ**, không phải cú pháp cấu hình của bất kỳ công cụ nào. Trong một hệ thống thật, vài ràng buộc có thể được giải quyết bằng ordering, còn vài ràng buộc liên quan bảo mật/commit phải được thực hiện tại boundary tường minh hơn.

### 4.1. Tách “bắt buộc chạy” khỏi “chạy theo thứ tự”

Nếu requirement là “authorization không bao giờ bị bỏ qua”, chỉ đặt Authorization ngoài Cache **chưa chứng minh** toàn bộ ứng dụng được bảo vệ: direct target call, self-invocation qua một proxy hoặc một entry point khác có thể bypass cả hai. Cần bảo vệ ở biên tin cậy và có negative tests.

Nếu requirement là “audit ghi sau commit”, chỉ đặt Audit vào sau body của method chưa đủ nếu commit có thể xảy ra muộn hơn hoặc future mới báo hoàn tất về sau. Thứ tự advice phải kết hợp với **định nghĩa đúng của sự kiện hoàn thành**.

### 4.2. Cần đặt tên metric theo phạm vi

Hai cái tên rõ nghĩa:
- `logical_operation_duration`: bao retry, backoff và sự kiện hoàn thành đã xác định.
- `attempt_duration`: đo mỗi lượt target attempt.

Nếu đều đặt `duration`, khi đổi thứ tự retry và metric mà dashboard không đổi tên, những số liệu trước/sau không còn so sánh đúng về semantics.

## 5. Các cách khai báo precedence tường minh

### 5.1. Thứ tự tổng bằng một danh sách đã sắp

Nếu chỉ có vài interceptors tại một boundary, có thể khai báo:

~~~text
outermost -> [TraceTotal, Authorization, Retry, MetricPerAttempt] -> target
~~~

Ưu điểm: dễ review và tạo test. Đánh đổi: khi có nhiều aspect độc lập và module mở rộng, danh sách toàn cục dài, dễ có phụ thuộc không mong muốn.

**Chú ý:** thứ tự danh sách chỉ có ý nghĩa nếu **composition API thật sự quy định** “phần tử đầu là ngoài cùng” hoặc “phần tử đầu chạy trước”. Tài liệu hóa quy tắc và test, không phó mặc cho helper constructor.

### 5.2. Priority bằng số hoặc thứ hạng

Một chính sách nội bộ có thể quy định:

~~~text
priority thấp hơn = lớp ngoài hơn
TraceTotal:        10
Authorization:     20
Retry:             30
MetricPerAttempt:  40
~~~

Đây là **chính sách tùy định nghĩa trong bài học**. Một engine khác có thể hiểu số lớn hơn là ưu tiên cao hơn hoặc sắp thứ tự before và after khác nhau.

Cần xác định:
1. Độ ưu tiên nhỏ/lớn tương ứng **outer/inner** hay chỉ “được xem xét trước”?
2. Cấu hình priority trùng nhau có bị từ chối không?
3. Nếu chỉ một aspect khớp join point, priority có tạo side effect nào không?
4. Nếu một advice ngoài short-circuit, những advice trong có chạy không?
5. Khi lỗi/return, thứ tự unwind được engine quy định thế nào?

**Khuyến nghị thiết kế:** Với những cặp advice có quan hệ ngữ nghĩa bắt buộc, nên **fail fast** khi priority trùng/không xác định hơn là silently dựa vào tên class hay đăng ký trước.

### 5.3. Quan hệ thứ tự bộ phận (partial order)

Trong hệ thống lớn, không phải mọi cặp aspect đều cần quan hệ. Có thể ghi:

~~~text
TraceTotal      -> Retry
Retry           -> MetricPerAttempt
Authorization   -> Cache
~~~

Dấu `A -> B` nghĩa là **A phải nằm ngoài B** trong ví dụ. Đây là đồ thị có hướng các ràng buộc, không nhất thiết tạo một thứ tự duy nhất cho các node không có quan hệ.

Nếu cần chuyển sang chuỗi tuyến tính, có thể dùng **topological sort**. Nhưng cần phân biệt:
- Một **partial order hợp lệ** chỉ nêu những cặp bắt buộc.
- Một **total order triển khai** phải chọn cách sắp tất cả phần tử được áp dụng.
- Các phần tử **không có ràng buộc** có thể đổi chỗ theo một tie-break được định nghĩa, hoặc hệ thống yêu cầu bổ sung precedence.

Không tự suy rằng topological sort luôn chỉ có **một** kết quả. Với nhiều node không liên hệ, thường có nhiều thứ tự hợp lệ.

### 5.4. Phát hiện cycle thay vì đoán

Ví dụ:

~~~text
A OUTER_THAN B
B OUTER_THAN C
C OUTER_THAN A
~~~

Đây là một chu trình mâu thuẫn, không có một nesting tuyến tính thỏa cả ba. Hệ thống cần báo **configuration error**, không chỉ chọn “last one wins” tình cờ.

Trong CI, nên có một phép kiểm tra graph:
- Các tên aspect/advice có tồn tại?
- Có phụ thuộc bị bỏ lửng?
- Có chu trình?
- Có hai rule mâu thuẫn?
- Có node không được xác định khi policy yêu cầu total order?
- Có thay đổi ordering khi feature flag/tập aspect áp dụng đổi không?

### 5.5. Khi nào dùng nhóm concern?

Có thể quy định các **phase/boundary** thay vì một bảng priority dài:

~~~text
RequestBoundary
  └─ Governance (auth, quota)
      └─ LogicalOperation (trace, retry)
          └─ AttemptBoundary (metric, transient-IO)
              └─ Target
~~~

Cách này hữu ích khi số aspect lớn. Nhưng “nhóm” phải có nghĩa **được hiện thực và kiểm tra**; không chỉ là thư mục source hoặc tên package đẹp.

## 6. Những thứ không nên là căn cứ precedence

### 6.1. Tên file và tên class

Đặt `01Tracing`, `02Retry` hoặc prefix alphabet không bảo đảm mọi engine sắp theo tên. Refactor tên class có thể vô tình làm đổi behavior nếu bạn đã dùng một quy tắc tự chế thiếu tài liệu.

Nếu một application **cố ý** sắp theo tên, đó phải được định nghĩa là hợp đồng thứ tự, có test; vẫn nên cân nhắc dùng priority/constraint có ý nghĩa ngữ nghĩa hơn.

### 6.2. Thứ tự reflection

Java reflection trả về tập method/field qua API có các quy tắc riêng và không cung cấp một **hợp đồng tổng quát về thứ tự khai báo source** để dùng làm precedence. Đừng dựa vào “ở JDK máy tôi method A xuất hiện trước B” để sắp advice.

### 6.3. Thứ tự HashMap/HashSet

Một `HashMap` hoặc `HashSet` không có hợp đồng iteration order cần thiết cho precedence. Ngay cả khi output trông ổn định trong vài lần chạy, nó không nên được dùng như cấu hình thứ tự ngầm định.

### 6.4. Thứ tự classloader hoặc module scan

Cấu hình scanning có thể phụ thuộc classpath, loader, artifact, build tool và incremental changes. Việc class được discover hoặc load trước **không tự mang nghĩa nghiệp vụ** rằng advice phải bao ngoài hoặc bao trong advice khác.

### 6.5. Thứ tự weaving

Một công cụ có thể biến đổi bytecode qua nhiều pass và lắp hook ở những chỗ khác nhau. Dù thứ tự các pass ảnh hưởng output trong một engine, **thứ tự biến đổi** không phải lúc nào cũng bằng **thứ tự runtime nested advice**. Nếu behavior dựa trên ordering của pass, cần tài liệu engine và test bytecode/runtime tương ứng.

### 6.6. “Vừa chạy thử thấy đúng”

Một test chỉ chứng minh behavior với artifact/config/runtime ấy. Nếu ordering không được khai báo và hỗ trợ như hợp đồng, phiên bản mới, module mới, classpath khác hay một refactor có thể làm đổi kết quả. Test là **bằng chứng xác minh hợp đồng**, không thay thế việc định nghĩa hợp đồng.

## 7. Ví dụ Java 17: hai thứ tự hợp lệ, hai phạm vi metric khác nhau

Ví dụ sau không dùng một engine weaving hoặc DSL pointcut cụ thể. Nó tạo một **interceptor chain tường minh** bằng Java 17, rồi sắp theo priority với chính sách:

> **Số priority nhỏ hơn = advice ở ngoài hơn. Priority trùng nhau = configuration error.**

Mỗi lần chạy một `WorkTarget` mới. Target thất bại với `TransientFailure` trong attempt đầu và thành công ở attempt thứ hai. `Retry` cho phép tối đa hai attempts. `Metric` in `begin/end` trong `finally` để quan sát mỗi lần đoạn bên trong nó chạy; đây là marker **phạm vi**, không phải công cụ đo số nano giây.

Chương trình chạy hai trường hợp:

- **Case 1:** Trace( Retry( Metric( Target ))) → Metric xuất hiện **mỗi attempt**.
- **Case 2:** Trace( Metric( Retry( Target ))) → Metric xuất hiện **một lần cho logical call**.

Tạo file **`ExplicitAdviceOrderDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExplicitAdviceOrderDemo {
    @FunctionalInterface
    interface Work {
        String execute();
    }

    @FunctionalInterface
    interface Next {
        String run();
    }

    @FunctionalInterface
    interface Interceptor {
        String around(Next next);
    }

    record Stage(String name, int priority, Interceptor interceptor) {}

    static final class TransientFailure extends RuntimeException {
        TransientFailure() {
            super("temporary outage");
        }
    }

    static final class WorkTarget implements Work {
        int attempts;

        @Override
        public String execute() {
            attempts++;
            System.out.println("TARGET attempt " + attempts);
            if (attempts == 1) {
                throw new TransientFailure();
            }
            return "OK";
        }
    }

    static Interceptor trace() {
        return next -> {
            System.out.println("TRACE enter");
            try {
                String result = next.run();
                System.out.println("TRACE return " + result);
                return result;
            } catch (RuntimeException error) {
                System.out.println("TRACE throw "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println("TRACE finally");
            }
        };
    }

    static Interceptor retry() {
        return next -> {
            for (int attempt = 1; attempt <= 2; attempt++) {
                System.out.println("RETRY attempt " + attempt);
                try {
                    return next.run();
                } catch (TransientFailure error) {
                    System.out.println("RETRY observed transient");
                    if (attempt == 2) {
                        throw error;
                    }
                }
            }
            throw new AssertionError("unreachable");
        };
    }

    static Interceptor metric() {
        return next -> {
            System.out.println("METRIC begin");
            try {
                return next.run();
            } finally {
                System.out.println("METRIC end");
            }
        };
    }

    static Work compose(Work target, List<Stage> stages) {
        List<Stage> sorted = new ArrayList<>(stages);
        sorted.sort(Comparator.comparingInt(Stage::priority));

        Set<Integer> priorities = new HashSet<>();
        for (Stage stage : sorted) {
            if (!priorities.add(stage.priority())) {
                throw new IllegalArgumentException(
                        "duplicate priority " + stage.priority());
            }
        }

        Work current = target;
        for (int index = sorted.size() - 1; index >= 0; index--) {
            Interceptor interceptor = sorted.get(index).interceptor();
            Work inner = current;
            current = () -> interceptor.around(inner::execute);
        }
        return current;
    }

    static void demonstrate(String title, List<Stage> stages) {
        System.out.println(title);
        Work work = compose(new WorkTarget(), stages);
        System.out.println("CALLER " + work.execute());
    }

    public static void main(String[] args) {
        demonstrate("CASE 1: per attempt", List.of(
                new Stage("Metric", 30, metric()),
                new Stage("Trace", 10, trace()),
                new Stage("Retry", 20, retry())
        ));

        demonstrate("CASE 2: logical call", List.of(
                new Stage("Retry", 30, retry()),
                new Stage("Metric", 20, metric()),
                new Stage("Trace", 10, trace())
        ));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac ExplicitAdviceOrderDemo.java
java ExplicitAdviceOrderDemo
~~~

**Stdout kỳ vọng, đúng thứ tự:**

~~~text
CASE 1: per attempt
TRACE enter
RETRY attempt 1
METRIC begin
TARGET attempt 1
METRIC end
RETRY observed transient
RETRY attempt 2
METRIC begin
TARGET attempt 2
METRIC end
TRACE return OK
TRACE finally
CALLER OK
CASE 2: logical call
TRACE enter
METRIC begin
RETRY attempt 1
TARGET attempt 1
RETRY observed transient
RETRY attempt 2
TARGET attempt 2
METRIC end
TRACE return OK
TRACE finally
CALLER OK
~~~

### 7.1. Vì sao danh sách khai báo không quyết định kết quả?

Trong Case 1, `List.of` được viết theo thứ tự `Metric, Trace, Retry`. Nhưng `compose` sắp theo số priority `10, 20, 30`, tạo chuỗi **Trace → Retry → Metric → Target**. Vì vậy output bắt đầu bằng `TRACE enter`, không phải `METRIC begin`.

Đây là cách tránh một phụ thuộc ngầm vào thứ tự khai báo. **Định nghĩa hợp đồng số nhỏ hơn ở ngoài** là phần thiết kế; việc sử dụng `Comparator.comparingInt` hiện thực trực tiếp hợp đồng đó.

### 7.2. Vì sao phải dựng chain từ trong ra ngoài?

Sau khi đã sắp `Trace(10), Retry(20), Metric(30)`, vòng lặp đi **từ cuối danh sách về đầu**:
- Khởi đầu là `Target`.
- Bọc `Metric(Target)`.
- Bọc `Retry(Metric(Target))`.
- Bọc `Trace(Retry(Metric(Target)))`.

Kết quả là lời gọi `work.execute()` đi vào **Trace trước**. Nếu code lắp wrapper từ đầu tới cuối, phần cuối có thể thành lớp ngoài cùng và **đảo ngược ý nghĩa** của priority; phải test builder, không chỉ test comparator.

### 7.3. Vì sao Case 1 ghi Metric hai lần?

`Retry` ở ngoài `Metric`, nên mỗi lần `Retry` gọi `next.run()` đều đi vào `Metric`. Attempt 1 ném `TransientFailure`, `finally` của Metric vẫn in `METRIC end`; Retry bắt exception loại cho phép và thử lại. Attempt 2 thành công, Metric chạy thêm một lần.

Như vậy một logical operation thành công có thể tạo:
- **Hai lần đo attempt**.
- **Một lần return bình thường** ở Trace.
- **Một lần failure nội bộ** đã được Retry xử lý.

Nếu metric “số lỗi nghiệp vụ cuối” được tăng ở Metric theo attempt đầu, dashboard có thể báo một failure attempt dù caller nhận kết quả OK. Điều đó không sai nếu metric đặt tên theo **attempt**, nhưng sai nếu được quảng cáo là failure của logical call.

### 7.4. Vì sao Case 2 chỉ ghi Metric một lần?

Metric ở ngoài Retry. Nó gọi `next.run()` một lần, nhưng continuation đó là **Retry**; Retry tự gọi target hai lần. Vì thế Metric mở một scope trước Retry và đóng scope sau khi Retry đã kết thúc thành công.

Đó là phạm vi một logical call. Nếu đo thời gian thật, nó có thể bao gồm cả backoff, queueing và những đoạn bên trong Retry. Một metric per-attempt được đặt trong Retry có phạm vi khác; cần định nghĩa tên và histogram rõ ràng.

### 7.5. Vì sao `TRACE return OK` chỉ xuất hiện một lần mỗi case?

Trace nằm ngoài cả Metric và Retry. Nó chỉ quan sát kết quả cuối của `next.run()`. Retry đã xử lý lỗi tạm thời của attempt 1 rồi cuối cùng trả OK; Trace không tự biết đã có một failure nội bộ trừ khi có thông tin attempt khác.

Tương tự, nếu Retry hết attempts và ném lại, Trace có thể in `TRACE throw TransientFailure` theo branch trong code. Một advice bên ngoài quan sát **kết cục sau khi advice bên trong đã áp dụng chính sách**.

### 7.6. Vì sao duplicate priority bị từ chối?

`compose` tạo `HashSet<Integer>` để kiểm tra trùng sau khi sort. Nếu hai Stage cùng priority, chương trình ném `IllegalArgumentException` thay vì sử dụng thứ tự phần tử hoặc tính ổn định của sort như một tie-break ngầm.

Không phải mọi hệ thống **bắt buộc** cấm tie. Một thiết kế khác có thể có secondary key tường minh như phase, tên định danh bất biến hoặc quan hệ `before/after`. Nhưng nếu cặp advice có quan hệ nghiệp vụ quan trọng, **từ chối cấu hình thiếu thứ tự** thường an toàn hơn việc vô tình đổi semantics sau một refactor.

### 7.7. Giới hạn có chủ đích của chương trình

- Đây là interceptor chain viết bằng Java thuần, **không dệt bytecode** và không có pointcut DSL.
- Target chỉ có một method; các lớp `Stage` đều áp dụng cho method ấy, không có filtering phức tạp.
- Retry chỉ bắt một exception type cụ thể và chạy hai attempts, không có backoff, timeout hay idempotency key.
- `WorkTarget` được tạo mới cho từng case nên attempt count mỗi case bắt đầu lại từ 1.
- `METRIC begin/end` minh họa phạm vi, không đo duration thực.
- Không có concurrency hoặc async trong ví dụ; phần kiểm thử nâng cao cần bổ sung các trường hợp đó.
- Không suy từ thứ tự trong ví dụ rằng một engine khác có cùng cách hiểu số priority.

## 8. Thiết kế order cho retry, tracing và metrics

### 8.1. Hai cấp quan sát nên được tách tên

Nhiều hệ thống cần **cả hai** loại số liệu:

~~~text
TraceTotal(
    LogicalMetrics(
        Retry(
            AttemptMetrics(
                Target
            )
        )
    )
)
~~~

Ví dụ:
- `LogicalMetrics`: một lần đo cho một logical request, bao cả retry.
- `AttemptMetrics`: một lần đo cho mỗi attempt mà Retry thực hiện.
- `TraceTotal`: trace span của toàn boundary đang định nghĩa.

Một công cụ AOP có thể biểu diễn hai metrics bằng hai advice instances hoặc một hệ thống tracing/metric chuyên dụng. Điều quan trọng không phải số object aspect mà là **phạm vi mỗi sự kiện**.

### 8.2. Trong retry có thể có nhiều tầng advice khác

Nếu Retry bao ngoài `AuditAttempt` hoặc một metric per-attempt, audit/metric có thể chạy nhiều lần. Nếu nó bao ngoài transaction scope, mỗi attempt có thể nhận một transaction riêng, **nếu** cơ chế transaction và retry được thiết kế như vậy.

Đừng mặc định thứ tự nesting tạo ra semantics commit/rollback mong muốn: transaction resource, propagation, isolation và external side effects có hợp đồng độc lập. Test phải chứng minh actual commit và effect count, không chỉ thứ tự advice.

### 8.3. Một retry lỗi tạm thời không nhất thiết an toàn

Ví dụ yêu cầu gửi tiền tới hệ thống ngoài. Attempt 1 có thể đã commit nhưng response bị mất; Retry thực hiện attempt 2 sẽ nhân side effect. Dù advice order hoàn toàn đúng, hệ thống vẫn sai nếu thiếu idempotency key, retry policy và khả năng kiểm tra trạng thái.

**Ordering đúng là cần thiết nhưng không đủ cho correctness.**

## 9. Cache và authorization: thứ tự có thể trở thành ranh giới bảo mật

Giả sử Cache có thể return sớm mà không gọi `proceed()`.

**Thiết kế 1:**

~~~text
Authorization(
    Cache(
        Target
    )
)
~~~

Nếu mọi invocation cần kiểm tra quyền được bảo đảm đi qua wrapper Authorization, quyền được kiểm tra trước cả cache hit. Nhưng điều này vẫn không bảo đảm tất cả đường truy cập ứng dụng đều đi qua wrapper; phải bảo vệ trusted boundary.

**Thiết kế 2:**

~~~text
Cache(
    Authorization(
        Target
    )
)
~~~

Ở nhánh cache hit, Cache có thể return ngay và **bỏ qua Authorization bên trong**. Nếu quyền được xác định theo user/tenant và giá trị cache được chia không đúng, có nguy cơ trả dữ liệu không được phép.

### 9.1. Trường hợp caching có thể đặt ngoài authorization?

Trong một hệ thống được thiết kế chặt chẽ, dữ liệu công khai hoặc cache gắn với một boundary đã được xác thực và khóa cache chứa đúng phân vùng quyền có thể tạo một thiết kế khác. Không khẳng định mọi Cache phải luôn ở trong mọi Auth advice. Câu hỏi đúng là:

- Auth được bảo đảm tại boundary nào?
- Cache có chứa dữ liệu người dùng hoặc tenant không?
- Cache hit có được phép bỏ qua target và inner advice không?
- Có kiểm tra quyền trên mọi đường vào, kể cả direct target/proxy bypass?
- Có thay đổi quyền sau khi cache entry được tạo không?

Thứ tự explicit giúp review, nhưng **policy security bắt buộc phải được chứng minh ở trust boundary**, không chỉ bằng sơ đồ nesting.

### 9.2. Metric cache hit khác metric target execution

~~~text
RequestMetrics(
    Cache(
        TargetMetrics(
            Target
        )
    )
)
~~~

Trong mô hình này:
- `RequestMetrics` đếm cả cache hit và cache miss.
- `TargetMetrics` chỉ chạy khi Cache tiếp tục vào Target.
- Một lần cache hit có thể có 1 request nhưng 0 target executions.

Nếu đảo vị trí hai metric, các dashboard sẽ có ý nghĩa khác. Không đặt cùng một nhãn “requests” cho những phạm vi không tương đương.

## 10. Transaction, audit và thời điểm “thành công”

Transaction và audit là ví dụ cho thấy thứ tự wrapper có thể cần nhưng chưa đủ.

~~~text
Audit(
    Transaction(
        Target
    )
)
~~~

Nếu `Transaction` chỉ return sau khi commit thành công, Audit ở ngoài có thể quan sát outcome sau commit. Nhưng có mô hình transaction mà commit xảy ra ở nơi khác; có async API trả future trước commit; có chuyện audit sink không lưu bền cùng transaction. **Không thể lấy dòng log “after Transaction” làm bằng chứng commit đã hoàn tất** khi chưa xác nhận hợp đồng.

Ngược lại:

~~~text
Transaction(
    Audit(
        Target
    )
)
~~~

Audit ở trong transaction boundary có thể ghi “success” khi Target return, trước khi transaction commit thất bại. Nếu audit được ghi ra hệ thống ngoài giao dịch, kết quả audit và dữ liệu bền vững có thể bất nhất.

### 10.1. Phải đặt tên sự kiện muốn audit

Có nhiều mốc khác nhau:
1. Request được nhận.
2. Authorization được kiểm tra.
3. Target body đã chạy.
4. Transaction chuẩn bị commit.
5. Commit đã hoàn tất theo hệ lưu trữ.
6. Event đã được phát/lưu bền.
7. Caller nhận kết quả.
8. Task bất đồng bộ cuối cùng completed.

Với hệ thống phức tạp, các mốc có thể tách nhau đáng kể. Xác định **audit event** cần nói về mốc nào rồi đặt logic ở điểm có thể chứng minh mốc đó. Đôi khi cần transactional outbox, domain events hoặc transaction callbacks thay vì chỉ đảo priority advice.

### 10.2. Không tạo side effect tại một advice “before” chỉ vì nó đứng đầu

Một advice before có thể chạy trước khi Target bị từ chối bởi một advice trong. Ghi “thanh toán thành công” ở before là sai tên event. Advice before có thể ghi “bắt đầu attempt” hoặc “request được nhận”, nhưng không được tự suy thành kết quả cuối.

### 10.3. Retry và transaction

Một policy cần mỗi attempt có một transaction riêng phải có **retry bao ngoài transaction boundary** theo cấu trúc phù hợp:

~~~text
Retry(
    Transaction(
        Target
    )
)
~~~

Nhưng điều đó chỉ diễn đạt phạm vi wrapper; transaction thật có mở/đóng mỗi attempt hay không phụ thuộc implementation, propagation và database behavior.

Nếu:

~~~text
Transaction(
    Retry(
        Target
    )
)
~~~

thì nhiều attempts có thể diễn ra trong cùng transaction context; điều này có thể ảnh hưởng rollback/lock/isolation. Đừng cho rằng hai cấu trúc tương đương chỉ vì caller cuối cùng cùng nhận OK.

## 11. Xử lý ngoại lệ: advice nào thấy lỗi nào?

Giả sử `A(B(Target))`.

- Target ném `E`.
- B bắt E, rồi **ném lại E** → A có thể thấy E.
- B bắt E, rồi **ném E2** → A thấy E2.
- B bắt E, rồi **trả fallback** → A có thể thấy normal return.
- B `finally` tự ném F → F có thể thay thế E mà A nhìn thấy.
- B không gọi continuation → Target không chạy, kết quả phụ thuộc B.

Như vậy một thứ tự đã được khai báo không chỉ quyết định logging: nó có thể quyết định **exception type/cause** tới caller.

### 11.1. Best-effort telemetry

Nếu logging/metrics chỉ nhằm quan sát, thường cần bảo đảm lỗi telemetry không vô tình làm mất kết quả/exception của nghiệp vụ. Một `finally` ghi metric mà tự ném exception có thể **che lỗi gốc**. Cần có chính sách lỗi rõ cho instrumentation.

Không tự dùng `catch(Throwable)` rồi nuốt mọi lỗi: Java `Throwable` gồm các `Error` nghiêm trọng và có thể phá tính đúng đắn. Việc quan sát rồi ném lại nguyên lỗi khác với thay mọi lỗi bằng fallback.

### 11.2. After-throwing ở tầng ngoài không phải nhật ký mọi lỗi bên trong

Nếu Retry bắt `TransientFailure` ở attempt đầu và thành công attempt hai, Trace ở ngoài Retry thấy normal return. Nếu cần thống kê transient failures, phải ghi ở attempt boundary hoặc trong Retry, không mong after-throwing ở outer ghi những lỗi đã được xử lý.

### 11.3. Đừng đếm cùng lỗi nhiều lần

Một Throwable có thể đi qua ba advice nested; cả ba có thể log “failure”. Dashboard cần phân biệt:
- Failure **ở từng boundary**.
- Failure **của attempt**.
- Failure **của logical operation**.
- Failure **của transaction commit**.

Không thể cộng thẳng tất cả thành tổng số request thất bại.

## 12. Async: thứ tự advice không tự mở rộng thời gian sống

Method Java có thể trả `CompletionStage<T>` trước khi task hoàn tất. Xét:

~~~text
Trace(
    Retry(
        Target.fetchAsync()
    )
)
~~~

Nếu advice Trace đo tới lúc `proceed()` **trả future**, nó chỉ đo invocation/dispatch latency; không tự bao trọn việc future completed. Tương tự, một catch quanh `proceed()` không tự bắt lỗi future hoàn tất exceptionally **sau khi method return bình thường**.

### 12.1. Hai trục độc lập

- **Nesting precedence:** Trace nằm ngoài hay trong Retry?
- **Completion semantics:** thời điểm nào được xem là logical operation completed?

Thay priority không sửa nghĩa “method return stage” so với “task hoàn tất”. Cần hook theo completion/error/cancellation của abstraction phù hợp.

### 12.2. Original stage và dependent stage

Với `CompletionStage`, `whenComplete` tạo một dependent stage. Nếu around advice đính callback rồi trả **stage gốc**, caller vẫn giữ stage gốc; lỗi callback có thể xuất hiện ở dependent stage bị bỏ qua. Nếu advice trả **dependent stage**, lỗi instrumentation trong callback có thể tác động tới outcome caller.

Vì thế thứ tự giữa Trace, Retry và CompletionObserver cần xét cả:
- Stage nào được trả về?
- Completion callback có chạy trước hay sau callback khác?
- Có callback nào thay result/exception không?
- Context/ThreadLocal có được truyền đúng thread không?
- Cancellation/timeout có propagation xuống task thật không?

Không suy rằng source ordering của callback luôn xác định thứ tự callback runtime trên nhiều threads. Dùng scheduler/serialization hoặc explicit coordination nếu một quan hệ happens-before là bắt buộc.

### 12.3. Callback và context lifecycle

Một aspect tracing ngoài retry có thể muốn giữ trace span đến khi logical task completed. Nếu dùng `ThreadLocal` ở caller rồi đóng span trong `finally` khi method trả future, span có thể đóng quá sớm. Nhưng giữ ThreadLocal caller mở mãi tới completion cũng có thể rò context vào request kế tiếp của thread pool.

Cần chuyển **snapshot tối thiểu** theo task và mở/restore scope phù hợp khi callback chạy, không dựa vào ordering của advice để tự động truyền context xuyên async boundary.

## 13. Nhiều join points và lồng lời gọi: thứ tự ở đâu?

Một operation ngoài có thể gọi nhiều methods có aspect riêng:

~~~text
caller -> A.outer()
            |
            +-> B.inner()
            |
            +-> C.helper()
~~~

Ngoài **thứ tự advice trên cùng một join point**, còn có **thứ tự các method executions khác nhau**. Nếu A.outer gọi B.inner trước C.helper, log interleave là do control flow business, không chỉ precedence.

Một advice được chọn ở execution của `outer` có thể bao một call stack chứa nhiều join points. Khi B.inner xảy ra, advice của nó được lồng vào giữa advice outer đang hoạt động. Không nên so log toàn hệ thống rồi tưởng mọi aspect có một total order toàn cục.

### 13.1. Self-invocation và bypass proxy

Trong mô hình delegating proxy, target.outer gọi `this.inner()` có thể không qua proxy handler dành riêng cho inner. Cho dù đã khai precedence rõ `AspectX OUTER_THAN AspectY`, nếu inner handler không được gọi, thứ tự đó không có hiệu lực cho internal invocation.

Cần test call paths: proxy invocation, direct target, self-call, async callback và interface/class dispatch theo công cụ.

### 13.2. Pointcut runtime conditions

Nếu advice B chỉ áp khi argument hợp lệ, có những invocation A chạy mà B không chạy. Bản đồ thứ tự chỉ áp cho **tập advice thực sự được kích hoạt trên occurrence cụ thể**. Nếu aspect ngoài short-circuit, tập bên trong không được kích hoạt.

### 13.3. Nhiều targets và threads

Hai targets có thể chạy ở hai threads. Dù trong mỗi invocation thứ tự `Trace → Retry → Metric` là xác định, các dòng log giữa hai invocations **có thể xen kẽ**. Đừng đòi thứ tự tổng giữa hai request đồng thời trừ khi nghiệp vụ thật sự yêu cầu synchronization.

## 14. Kiểm thử ordering như một hợp đồng

### 14.1. Test sơ đồ thứ tự, không chỉ test “cả hai advice đã chạy”

Một test chỉ assert có log `TRACE` và `METRIC` không phát hiện Metric đã từ per-attempt chuyển thành per-logical. Cần kiểm tra **đúng thứ tự tương đối** và **số lần chạy**.

Ví dụ với target fail một lần rồi success:

| Cấu hình | Trace enter | Retry attempts | Metric begin/end | Kết quả caller |
| --- | --- | --- | --- | --- |
| `Trace(Retry(Metric(Target)))` | 1 lần. | 2 attempts. | 2 cặp. | OK. |
| `Trace(Metric(Retry(Target)))` | 1 lần. | 2 attempts. | 1 cặp. | OK. |

Một output giống nhau ở caller không chứng minh instrumentation đúng phạm vi.

### 14.2. Ma trận QA tối thiểu

| Ca | Điều kiểm tra |
| --- | --- |
| **Single aspect** | Khi chỉ còn một advice, không phụ thuộc thứ tự lớp không liên quan. |
| **Two aspects** | Before đi từ ngoài vào trong, after/finally unwind từ trong ra ngoài. |
| **Three around aspects** | Nesting đúng total/partial order được chọn. |
| **Duplicate priority** | Fail fast hoặc dùng tie-break được công bố, không im lặng. |
| **Cycle precedence** | Báo config error, không tạo ordering bất khả thi. |
| **Target success** | Result/call count chính xác. |
| **Target exception** | Các catch/finally theo chiều unwind, exception contract được giữ. |
| **Inner advice fallback** | Outer thấy normal return, không báo target failure thành final failure. |
| **Outer short-circuit** | Inner/target không chạy theo hợp đồng skip. |
| **Retry two attempts** | Metric per-attempt chạy 2, metric per-logical chạy 1. |
| **Cache hit** | Authorization/policy bắt buộc không bị bỏ qua; request/target metrics tách đúng. |
| **Transaction commit failure** | Audit không ghi success ở thời điểm chưa cam kết. |
| **Async failure** | Method return-handle khác terminal completion; observer nhìn đúng stage. |
| **Nested method calls** | Phân biệt nested join points với precedence trên cùng join point. |
| **Proxy bypass** | Không suy log từ handler khi caller gọi target trực tiếp. |
| **Concurrency** | Ordering trong từng invocation đúng, không giả định global log order. |
| **Upgrade** | Config/tập advisor thay đổi vẫn thỏa quan hệ đã khai. |

### 14.3. Unit test cho sorting algorithm

Nếu có composition builder, test ít nhất:

1. Input stages được viết **không theo thứ tự**; output vẫn theo priority.
2. Một stage ngoài có `try/finally`; lệnh `finally` chạy cuối phạm vi của nó.
3. Priority trùng sinh lỗi đúng nhãn.
4. Danh sách rỗng trả target nguyên semantics.
5. Một stage mới được chèn không làm đảo quan hệ bắt buộc giữa các stage cũ.
6. Vòng lặp wrap từ cuối về đầu đúng ý nghĩa “priority nhỏ = ngoài”.

Với đồ thị quan hệ:
- Test topological sort.
- Test cycle detection.
- Test node không tồn tại.
- Test partial order nhiều thứ tự hợp lệ.
- Nếu yêu cầu deterministic total order, test tie-break rõ ràng.

### 14.4. Integration test với engine thật

Một comparator tùy viết đúng chưa chứng minh engine AOP đã áp dụng thứ tự ấy. Cần chạy test với **đúng engine, runtime version, weaving/proxy config và artifact**; ghi sự kiện tại advice boundaries.

Nếu engine tự có precedence language hoặc annotation, kiểm tra:
- Priority được đọc ở nơi nào?
- Giá trị mặc định là gì?
- Thứ tự across-aspects và within-one-aspect có khác nhau không?
- Matching, classloader và creation có ảnh hưởng không?
- Có advisory warnings khi order mơ hồ không?

Không dùng tài liệu/logic của một engine để khẳng định engine khác giống nó.

## 15. Phát hiện thay đổi thứ tự ngoài ý muốn

### 15.1. Snapshot thứ tự hiệu lực

Trong môi trường có nhiều plugins/config modules, nên có một báo cáo ở startup hoặc build:

~~~text
Join-point group: service-operation
Effective outer-to-inner chain:
  TraceTotal
  Authorization
  Retry
  AttemptMetrics
  Target
~~~

Báo cáo cần bắt nguồn từ **kết quả config thực tế**, không chỉ liệt kê tên aspect tìm thấy trong source. Nếu pointcut là động, mô tả những nhóm join point đại diện và xác minh runtime.

### 15.2. Diff khi thêm một aspect mới

Một aspect mới có thể:
- Nằm ngoài Retry nên chỉ chạy một lần.
- Nằm trong Retry nên chạy mỗi attempt.
- Short-circuit trước Authorization.
- Bắt và chuyển lỗi trước Audit.
- Đóng span trước completion.
- Lấy lock trước một wrapper khác và tạo deadlock.

Do vậy review một aspect mới phải xem **điều kiện matching + precedence + dữ liệu/side effects** chứ không chỉ nội dung advice.

### 15.3. Đảm bảo ổn định qua build/refactor

Các dấu hiệu cần cảnh giác:
- Thêm module khiến classpath scan khác.
- Đổi tên/package aspect.
- Nâng phiên bản compiler/bytecode weaver.
- Thay cách dựng proxy/interceptor chain.
- Chuyển từ một collection có order sang collection không có order.
- Thêm annotation thứ tự mà không xử lý tie.
- Dời method từ component A sang B làm call path đổi.

Một suite test ordering như mục 14 giúp phát hiện thay đổi semantics trước production.

## 16. Sai lầm phổ biến và cách sửa

**“Aspect A được khai báo trước nên advice A luôn chạy trước.”** Không có hợp đồng chung. Dùng precedence tường minh và kiểm chứng.

**“Số priority càng nhỏ thì advice luôn chạy trước ở mọi công cụ.”** Chỉ đúng nếu engine/chính sách bạn chọn có định nghĩa như vậy. Cần đọc contract.

**“A ưu tiên trước B nghĩa là mọi log A đều xuất hiện trước mọi log B.”** Sai với wrapper lồng: `A.before` trước `B.before`, nhưng `A.after` sau `B.after`.

**“Nếu cả hai advice chạy thì thứ tự không quan trọng.”** Sai: cache, retry, auth, metrics và audit có thể khác semantics theo thứ tự.

**“Đảo order giữa Retry và Metrics chỉ đổi số dòng log.”** Không chỉ vậy. Nó đổi đơn vị đo và số lần observer được gọi.

**“Có trace outer nghĩa là mọi exception bên trong đều được trace outer đánh dấu failure.”** Không: retry/fallback có thể xử lý lỗi rồi trả bình thường.

**“Thứ tự wrapper bảo đảm transaction đã commit khi method return.”** Không mặc định; phải xác minh transaction boundary và async/commit lifecycle.

**“Topological sort chỉ tạo một chuỗi duy nhất.”** Không nếu partial order để lại những cặp độc lập.

**“Cycle trong precedence có thể được xử lý bằng chọn aspect khởi tạo trước.”** Không; cycle mâu thuẫn, cần báo lỗi cấu hình.

**“HashMap iteration đang ổn định nên dùng để tạo advice order.”** Không nên vì không có hợp đồng thứ tự phù hợp.

**“Dùng tên class làm tie-break luôn vô hại.”** Không nếu tên class thay đổi qua refactor; cần xác định tie-break có ý nghĩa và được test.

**“Chỉ cần sửa priority để bắt được self-invocation.”** Không nếu internal call bypass proxy/handler.

**“Priority giải quyết quyền truy cập bắt buộc.”** Không thể bảo đảm nếu có đường bypass hoặc short-circuit khác; cần security invariant ở trusted boundary.

**“AOP engine lúc nào cũng hỗ trợ gọi `proceed` nhiều lần để retry.”** Không. Cần xác minh capability và semantics.

**“Một lời gọi async return stage thành công nghĩa là logical task thành công.”** Sai: future có thể fail về sau.

**“Unit test sorting đủ, không cần test engine/runtime thật.”** Chưa đủ; phải test cấu hình thực sự được áp dụng ở join point.

## 17. Bài tập vận dụng

### Bài 1 — Hai quanh advice

`A(B(Target))`, cả hai có before/after, Target trả bình thường. Viết thứ tự bốn lời ghi.

**Đáp án:** `A.before`, `B.before`, `B.after`, `A.after`.

### Bài 2 — Retry và Metrics

Một target thất bại lần 1 và thành công lần 2. `Retry(Metric(Target))` có bao nhiêu lượt Metric nếu Retry gọi tiếp lại đúng hai lần?

**Đáp án:** Hai lượt Metric (mỗi attempt). `Metric(Retry(Target))` thường chỉ có một lượt Metric bao ngoài logical operation.

### Bài 3 — Nhánh cache hit

`Cache(Authorization(Target))`, Cache return ngay khi hit. Authorization bên trong có chạy không?

**Đáp án:** Không ở nhánh cache return sớm không gọi continuation. Nếu việc kiểm tra quyền bắt buộc, cần đổi kiến trúc/boundary để không có đường bỏ qua, không chỉ tin vào thứ tự.

### Bài 4 — Nhiều lỗi trong một request

Retry xử lý hai transient failures rồi success. Trace ngoài chỉ ghi success. Có nghĩa request chưa từng có attempt failure?

**Đáp án:** Không. Trace ngoài ghi final outcome; cần metric/log ở attempt boundary để thấy failures bên trong.

### Bài 5 — Cycle

Có yêu cầu A ngoài B, B ngoài C, C ngoài A. Có thể sắp một chuỗi tuyến tính thỏa cả ba không?

**Đáp án:** Không. Cần sửa ràng buộc và fail fast.

### Bài 6 — Source order

Aspect X đứng đầu trong file; Y đứng sau. Trong runtime thấy Y.before chạy trước X.before. Đây chắc chắn là bug engine?

**Đáp án:** Không thể kết luận. Source declaration order không nhất thiết là precedence contract. Xem cấu hình và tài liệu engine.

### Bài 7 — Transaction và audit

Một advice Audit ở ngoài Transaction, nhưng target trả future chưa hoàn tất còn commit diễn ra sau. Audit thấy method return bình thường có được kết luận đã commit không?

**Đáp án:** Không. Phải xác định commit/completion event thật; ordering của method invocation không tự bao lifecycle async.

### Bài 8 — Concurrent invocations

Hai threads chạy cùng chuỗi `Trace(Retry(Target))`, log xen kẽ. Việc dòng TRACE của request B xuất hiện giữa hai attempts của A có chứng minh precedence sai?

**Đáp án:** Không. Ordering được bảo đảm **trong phạm vi từng invocation**, không mặc nhiên sắp mọi request theo một thứ tự toàn cục.

### Bài 9 — Trùng priority

Hai interceptors cùng số priority 20 và có thứ tự business bắt buộc. Nên làm gì?

**Đáp án:** Định nghĩa secondary precedence tường minh hoặc báo lỗi cấu hình; không dựa vào thứ tự quét/discovery tình cờ.

### Bài 10 — Refactor gọi trực tiếp target

Một test ordering pass khi gọi qua proxy, nhưng một code path mới gọi target thẳng. Sửa priority có khắc phục được không?

**Đáp án:** Không. Phải sửa coverage/wiring hoặc dùng mechanism có hook phù hợp rồi test call path mới.

### Bài 11 — Which metric is which?

`RequestMetric(Cache(TargetMetric(Target)))`. Một cache hit tạo bao nhiêu RequestMetric, TargetMetric nếu mỗi wrapper gọi một lần?

**Đáp án:** RequestMetric một lần, TargetMetric không chạy nếu Cache return mà không gọi continuation.

### Bài 12 — Lỗi callback đo lường

Một advice trả `stage.whenComplete(callback)`, callback đôi lúc ném lỗi. Chỉ đảo priority callback với Trace có giải quyết tính trong suốt không?

**Đáp án:** Không. Phải xét stage nào được trả ra, lỗi callback được xử lý thế nào và contract completion; ordering không thay thế error handling.

## 18. Checklist thiết kế thứ tự advice dùng trong review

1. **Boundary:** Advice áp vào join point nào, qua proxy hay weaving, có bypass không?
2. **Concern:** Advice chỉ quan sát hay có thể skip, retry, mutate result hoặc exception?
3. **Scope đo:** Logical operation, target invocation hay attempt?
4. **Outer/inner:** Đã biểu diễn chính xác lớp ngoài và lớp trong chưa?
5. **Before/after:** Đã hiểu unwind order trên return, throw, finally chưa?
6. **Short-circuit:** Advice nào có thể không gọi `proceed`; chính sách quan trọng nào bị bỏ qua?
7. **Retry:** Ai được gọi nhiều lần; target idempotent và timeout tổng có rõ không?
8. **Cache:** Hit/miss tác động tới Authorization, metrics và target execution thế nào?
9. **Transaction:** Commit/rollback xảy ra ở đâu, có thuộc async lifecycle không?
10. **Audit:** “Success” mang nghĩa method return, commit hay task completion?
11. **Exceptions:** Advice trong có thể catch/transform/swallow lỗi không?
12. **Priorities:** Hướng so sánh số priority có rõ, có collision/cycle không?
13. **Partial order:** Những cặp nào thực sự cần precedence, cặp nào độc lập?
14. **Tie handling:** Có fail-fast hoặc deterministic explicit tie-break không?
15. **Construction:** Builder có đảo wrapper order ngoài ý muốn không?
16. **Runtime:** Engine thật có áp ordering đã khai, kể cả after advice và call paths không?
17. **Async:** Observer nào nhìn terminal completion và stage gốc/phụ thuộc?
18. **Concurrency:** Có yêu cầu ordering trong một call hay across requests? State có thread-safe?
19. **Versioning:** Thêm module/refactor/upgrade có đổi effective chain không?
20. **Evidence:** Có positive/negative tests cùng artifact và báo cáo effective order không?
21. **Security:** Critical invariants có nằm ở trusted boundary đáng tin, không chỉ proxy priority?
22. **Operations:** Có metrics/trace names phản ánh đúng attempt vs logical success/failure?

## Kết luận

Khi nhiều aspect cùng chọn một join point, **thứ tự là một phần của ngữ nghĩa hệ thống**, không phải chi tiết trang trí. Một advice ngoài sẽ thực hiện phần “before” trước advice trong và phần “after/finally” sau advice trong; short-circuit có thể bỏ qua phần bên trong; retry có thể làm advice bên trong chạy nhiều lần; xử lý lỗi của tầng trong có thể thay đổi outcome mà tầng ngoài nhìn thấy.

Thiết kế đáng tin cậy bắt đầu bằng **quy tắc nghiệp vụ và quan sát có thể kiểm chứng**: ai phải nằm ngoài ai, ai được chạy theo attempt, ai phải chạy cho logical operation, policy nào không được skip, và thế nào là completion thật. Chỉ sau đó mới khai precedence bằng **danh sách, priority hoặc đồ thị ràng buộc tường minh**, với chính sách xử lý tie/cycle rõ. Xác nhận bằng test ở cả lớp composition và AOP engine/runtime thực tế; không dựa vào tên class, source order, reflection, classloader hoặc kết quả tình cờ của một lần chạy. Đặc biệt, một ordering đúng không thay thế việc chứng minh **độ phủ interception, idempotency, transaction consistency và async completion semantics**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-007">Ngăn quan hệ phụ thuộc vòng giữa các aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Ngăn quan hệ phụ thuộc vòng giữa các aspect

Khi **nhiều aspect** cùng tham gia một chương trình **Aspect-Oriented Programming (AOP)**, chúng có thể phụ thuộc nhau về thứ tự advice, dịch vụ được gọi, instance được khởi tạo hoặc callback lúc chạy. Nếu những quan hệ ấy tạo thành một **vòng phụ thuộc**, hệ thống có thể không dựng được cấu hình, không khởi tạo được đối tượng, chạy advice đệ quy vô hạn, phát sinh log storm hoặc deadlock. Chỉ sắp lại thứ tự khai báo chưa chắc xử lý được nguyên nhân.

**Mục tiêu bài học:** nhận ra những dạng chu trình khác nhau, biểu diễn quan hệ đúng ngữ nghĩa, phát hiện trước bằng **DAG/topological sort** khi thích hợp, ngăn advice tự chặn đường hỗ trợ của nó, thiết kế phụ thuộc một chiều và kiểm thử các đường thành công, lỗi, retry và bất đồng bộ. Kiến thức áp dụng cho **AOP cơ bản và nâng cao nói chung**; cú pháp pointcut, precedence và mô hình instantiation cụ thể phụ thuộc cơ chế triển khai.

## 1. Nhắc lại các thành phần liên quan

- **Join point:** điểm chương trình có thể được can thiệp, ví dụ method call hoặc method execution khi mô hình hỗ trợ.
- **Pointcut:** điều kiện lựa chọn những join point được áp dụng advice.
- **Advice:** logic before, after, around hoặc dạng khác chạy tại điểm được chọn.
- **Aspect:** đơn vị gom pointcut/advice và có thể sở hữu trạng thái hoặc dependency.
- **Weaving:** kết hợp advice vào chương trình qua biên dịch, bytecode, load-time hoặc proxy/wrapper.
- **Precedence:** thứ tự ưu tiên/phạm vi lồng nhau giữa nhiều advice cùng khớp một điểm.
- **Continuation/proceed:** khả năng một around advice cho phép phần xử lý bên trong tiếp tục.

Aspect A chọn cùng join point với aspect B **không đủ để tạo vòng**. Vòng chỉ xuất hiện khi có một đường phụ thuộc **quay trở lại node đã đi qua**, theo loại quan hệ đang xét.

Ví dụ:

~~~text
AUTH -> TRANSACTION -> AUDIT -> AUTH
~~~

Nếu mỗi mũi tên đều có nghĩa "aspect bên trái bắt buộc bao ngoài aspect bên phải", đây là vòng precedence bất khả thi. Nhưng nếu A.before, B.before rồi B.after, A.after, đó có thể là một stack lồng nhau hoàn toàn hợp lệ, không phải vòng.

## 2. Phân loại các dạng phụ thuộc vòng

| Loại vòng | Cạnh có nghĩa gì? | Hậu quả điển hình | Hướng xử lý |
| --- | --- | --- | --- |
| **Precedence** | A bắt buộc ở ngoài/ưu tiên trước B | Không có thứ tự thỏa mọi ràng buộc | Phân tích invariant, bỏ ràng buộc sai, phát hiện cycle |
| **Khởi tạo** | A cần instance của B ngay khi tạo | Constructor recursion hoặc không xây xong object | Dependency inversion, tách lõi/sink độc lập |
| **Runtime call/reentrancy** | Advice A gọi method làm A được gọi lại | StackOverflowError, log storm, timeout | Giới hạn pointcut, tách helper và sink |
| **Event/callback** | A phát event khiến listener gọi lại A | Sự kiện lặp vô hạn, queue quá tải | Event flow một chiều, dedup, termination |
| **Lock/deadlock** | A giữ L1 chờ L2, B giữ L2 chờ L1 | Nhiều thread đứng chờ | Quy định lock order, không callback trong critical section |
| **Retry/fallback** | A retry qua B, B fallback lại A | Lặp side effect, hết deadline | Attempt budget, idempotency, loại cycle |
| **Weaving/instrumentation** | Code helper được advice chặn lại | Instrumentation tự kích hoạt | Phạm vi exclude, kiểm thử bytecode và runtime |

Mỗi loại cần **bằng chứng và giải pháp riêng**. Một bộ phát hiện precedence cycle không thể tự phát hiện mọi call cycle tại runtime; một `ThreadLocal` guard không giải quyết được constructor dependency cycle.

## 3. Chu trình precedence: không có thứ tự tuyến tính hợp lệ

Quy ước rõ:

~~~text
A -> B  nghĩa là  A phải bao ngoài B
~~~

Khi có:

~~~text
AUTH -> TX
TX -> AUDIT
AUDIT -> AUTH
~~~

muốn thỏa tất cả, AUTH phải ở ngoài TX, TX ở ngoài AUDIT, rồi AUDIT ở ngoài AUTH. Không có lớp outermost hợp lệ.

**Đừng âm thầm chọn thứ tự tên lớp hoặc thứ tự build** để che lỗi. Nếu quan hệ là ràng buộc bắt buộc của bảo mật, transaction và audit, cần báo **configuration error** trước khi phục vụ request.

### 3.1 Có thể đang mô hình hóa sai một yêu cầu

Một yêu cầu như "Audit phải chạy trước khi nghiệp vụ bắt đầu và sau khi biết kết quả" **không có nghĩa** vừa có cạnh `AUDIT -> BUSINESS` vừa có cạnh `BUSINESS -> AUDIT`. Một **around advice** có thể bao nghiệp vụ:

~~~text
AUDIT.before
    BUSINESS
AUDIT.after or AUDIT.error
~~~

Đường vào và đường ra là hai pha của một wrapper; bản thân cấu trúc lồng nhau **không có cycle**.

Nếu "Transaction phải bắt đầu trước nghiệp vụ và commit sau nghiệp vụ", đó cũng thường là quan hệ **surround**, không phải hai ràng buộc precedence mâu thuẫn.

### 3.2 Partial order thay vì sắp mọi aspect

Một chương trình có thể cần:

~~~text
AUTH -> CACHE
TRANSACTION -> MUTATION
METRICS may be independent
AUDIT needs to see denied and successful outcomes
~~~

Hai dòng đầu biểu diễn vị trí, còn hai dòng sau đòi hỏi một **hợp đồng quan sát**. Không nhất thiết cần một thứ tự toàn phần cho mọi aspect. **Partial order** giúp tránh tạo cạnh không có cơ sở, giảm nguy cơ chu trình giả.

## 4. Đồ thị có hướng và phát hiện vòng

Một **directed acyclic graph (DAG)** là đồ thị có hướng **không có chu trình**. Với các cạnh "A phải đi trước B", có thể tạo một **topological order** khi đồ thị là DAG.

Ví dụ:

~~~text
AUTH -> CACHE -> TRACE
   \--------------^
~~~

Thứ tự hợp lệ: `AUTH, CACHE, TRACE`.

Ví dụ có vòng:

~~~text
A -> B
B -> C
C -> A
~~~

Bất kỳ thứ tự nào cũng phá ít nhất một cạnh.

### 4.1 DFS ba trạng thái

Một thuật toán kiểm tra vòng:
- **WHITE / 0:** chưa thăm.
- **GRAY / 1:** đang đi vào node, vẫn nằm trong recursion stack.
- **BLACK / 2:** đã duyệt xong mọi cạnh đi ra.

Khi DFS gặp một node **GRAY**, có đường quay lại node đang xử lý → **cycle**. Nếu gặp node **BLACK**, đó có thể chỉ là một **dependency chung** (hai aspect cùng cần một aspect thứ ba), không phải cycle.

Muốn báo lỗi hữu ích, giữ stack và in đường vòng: `AUTH -> TX -> AUDIT -> AUTH`, không chỉ nói "có lỗi thứ tự".

### 4.2 Thuật toán Kahn

Cách khác:
1. Tính indegree của mỗi node.
2. Đưa các node indegree=0 vào hàng đợi.
3. Lấy từng node, thêm vào kết quả, giảm indegree các node kế.
4. Nếu không thể lấy đủ số node, đồ thị có cycle.

DFS và Kahn đều có độ phức tạp `O(V + E)` với danh sách kề hợp lý, V là số node, E là số cạnh. DFS thuận tiện để hiển thị đường cycle; Kahn thuận tiện để phân tích những node còn bị khóa.

### 4.3 Chỉ chạy topological sort trên đúng đồ thị

Không nên gộp bừa:
- Cạnh precedence "A phải ngoài B".
- Cạnh dependency "A cần B để khởi tạo".
- Cạnh runtime "A có thể gọi B".
- Cạnh state "A viết vào state của B".

Một vòng gọi runtime có thể **có điều kiện dừng hợp lệ**, còn vòng precedence bắt buộc không thể thỏa trong một chuỗi tuyến tính. Phải **gắn nhãn và phân loại cạnh trước khi phân tích**.

## 5. Java 17: phát hiện vòng trước khi ghép advice

Ví dụ độc lập dùng Java 17. Mỗi `Rule` chứa tên aspect và danh sách các aspect **phải nằm bên trong nó**. `A -> B` nghĩa A bao ngoài B. DFS từ chối cycle và cạnh tới node không tồn tại, rồi tạo thứ tự topo và dựng các wrapper.

Tạo file `AspectCycleDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AspectCycleDemo {
    record Rule(String name, List<String> mustWrap) {}

    @FunctionalInterface
    interface Action {
        String run(String id);
    }

    static void visit(
            String name,
            Map<String, List<String>> graph,
            Map<String, Integer> state,
            List<String> path,
            List<String> finished) {
        int current = state.getOrDefault(name, 0);

        if (current == 1) {
            int begin = path.indexOf(name);
            List<String> cycle =
                    new ArrayList<>(path.subList(begin, path.size()));
            cycle.add(name);
            throw new IllegalArgumentException(
                    "cycle " + String.join(" -> ", cycle));
        }
        if (current == 2) {
            return;
        }

        state.put(name, 1);
        path.add(name);
        for (String inner : graph.get(name)) {
            if (!graph.containsKey(inner)) {
                throw new IllegalArgumentException(
                        "unknown aspect " + inner);
            }
            visit(inner, graph, state, path, finished);
        }
        path.remove(path.size() - 1);
        state.put(name, 2);
        finished.add(name);
    }

    static List<String> resolve(List<Rule> rules) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Rule rule : rules) {
            if (graph.putIfAbsent(
                    rule.name(), List.copyOf(rule.mustWrap())) != null) {
                throw new IllegalArgumentException(
                        "duplicate aspect " + rule.name());
            }
        }

        Map<String, Integer> state = new HashMap<>();
        List<String> finished = new ArrayList<>();
        for (String name : graph.keySet()) {
            visit(name, graph, state, new ArrayList<>(), finished);
        }
        Collections.reverse(finished);
        return List.copyOf(finished);
    }

    static Action wrap(String name, Action next) {
        return id -> {
            System.out.println(name + " before");
            try {
                String result = next.run(id);
                System.out.println(name + " after");
                return result;
            } catch (RuntimeException error) {
                System.out.println(name + " error");
                throw error;
            } finally {
                System.out.println(name + " finally");
            }
        };
    }

    static Action chain(List<String> outerFirst) {
        Action current = id -> {
            System.out.println("BUSINESS " + id);
            if ("fail".equals(id)) {
                throw new IllegalStateException("failure");
            }
            return "done:" + id;
        };
        for (int i = outerFirst.size() - 1; i >= 0; i--) {
            current = wrap(outerFirst.get(i), current);
        }
        return current;
    }

    static void scenario(Action action, String id) {
        System.out.println("CASE " + id);
        try {
            System.out.println("RESULT " + action.run(id));
        } catch (RuntimeException error) {
            System.out.println("CALLER "
                    + error.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        List<Rule> valid = List.of(
                new Rule("AUTH", List.of("CACHE", "TRACE")),
                new Rule("CACHE", List.of("TRACE")),
                new Rule("TRACE", List.of()));

        List<String> order = resolve(valid);
        System.out.println("ORDER " + String.join(">", order));
        Action action = chain(order);
        scenario(action, "ok");
        scenario(action, "fail");

        System.out.println("CASE cycle");
        try {
            resolve(List.of(
                    new Rule("X", List.of("Y")),
                    new Rule("Y", List.of("Z")),
                    new Rule("Z", List.of("X"))));
        } catch (IllegalArgumentException error) {
            System.out.println("REJECT " + error.getMessage());
        }

        System.out.println("CASE unknown");
        try {
            resolve(List.of(
                    new Rule("AUDIT", List.of("MISSING"))));
        } catch (IllegalArgumentException error) {
            System.out.println("REJECT " + error.getMessage());
        }
    }
}
~~~

Chạy bằng **JDK 17 trở lên**:

~~~bash
javac AspectCycleDemo.java
java AspectCycleDemo
~~~

Stdout chính xác của ví dụ:

~~~text
ORDER AUTH>CACHE>TRACE
CASE ok
AUTH before
CACHE before
TRACE before
BUSINESS ok
TRACE after
TRACE finally
CACHE after
CACHE finally
AUTH after
AUTH finally
RESULT done:ok
CASE fail
AUTH before
CACHE before
TRACE before
BUSINESS fail
TRACE error
TRACE finally
CACHE error
CACHE finally
AUTH error
AUTH finally
CALLER IllegalStateException
CASE cycle
REJECT cycle X -> Y -> Z -> X
CASE unknown
REJECT unknown aspect MISSING
~~~

### 5.1 Vì sao kết quả `AUTH>CACHE>TRACE`?

DFS với các cạnh:

~~~text
AUTH -> CACHE
AUTH -> TRACE
CACHE -> TRACE
~~~

đưa TRACE vào danh sách hoàn tất trước CACHE và AUTH; đảo danh sách này được thứ tự topo `AUTH, CACHE, TRACE`. Builder dựng wrapper **từ trong ra ngoài**:

~~~text
current = Business
current = TRACE(current)
current = CACHE(current)
current = AUTH(current)
~~~

Khi gọi `action.run("ok")`, AUTH.before chạy đầu tiên và AUTH.finally chạy cuối cùng. Đây là ngữ nghĩa của **wrapper mô phỏng**, không phải một quy tắc số priority chung cho mọi bộ AOP.

### 5.2 Vì sao `X -> Y -> Z -> X` bị từ chối?

DFS bắt đầu với X, đi tới Y, Z, rồi thấy X **đang nằm trên stack** (GRAY). Chu trình được báo tường minh; chương trình không tạo wrapper từ cấu hình trái ngược.

Lỗi `unknown aspect MISSING` phát sinh vì một ràng buộc tới node không có trong danh sách. Không âm thầm bỏ qua cạnh bị thiếu, tránh tạo chain khác mục đích.

### 5.3 Giới hạn ví dụ

- `Rule` là cấu trúc của chương trình demo, **không phải cú pháp pointcut/precedence phổ quát**.
- Chương trình dùng Java wrapper; không dệt bytecode bằng công cụ AOP.
- Nó phát hiện **vòng precedence tĩnh**, không tự phát hiện vòng constructor/service hoặc đệ quy runtime.
- Mỗi wrapper gọi `next.run` đúng một lần. Một around advice thật có thể short-circuit hoặc retry nếu mô hình hỗ trợ.
- Các nhãn AUTH/CACHE/TRACE chỉ để mô phỏng thứ tự; không chứa kiểm tra quyền, cache, hay truyền trace thật.
- Nếu một mô hình AOP có quy tắc ưu tiên khác cho before/after độc lập, không suy thứ tự đó từ chương trình wrapper.

## 6. Vòng khởi tạo: aspect A cần B, B lại cần A

Ví dụ:

~~~text
AuditAspect constructor -> MetricsService
MetricsService constructor -> TracingAspect
TracingAspect constructor -> AuditAspect
~~~

Nếu mọi dependency đều cần **ngay khi xây dựng object**, không thể hoàn tất A khi chưa có B, không thể có B khi chưa có C, và không thể có C khi chưa có A. Một hệ thống có thể phát hiện lỗi lúc khởi tạo, hoặc thiếu kiểm tra khiến lời gọi constructor lặp.

### 6.1 Sửa bằng tách dependency một chiều

Một kiến trúc tốt hơn:

~~~text
AuditAspect ------MetricsAspect -----+--> DiagnosticsSink
TracingAspect ----/
~~~

Mỗi aspect phụ thuộc vào một interface **nhận sự kiện tối thiểu**. `DiagnosticsSink` không được gọi ngược vào các method mà những aspect này chặn.

Ví dụ abstraction:

~~~java
interface MetricRecorder {
    void count(String eventName);
}
~~~

Đây là **mảnh khai báo**, không phải chương trình độc lập. Một implementation có thể gửi metric qua bộ đệm hoặc hệ thống quan sát, nhưng phải có hợp đồng không tạo đệ quy về chính aspect phát dữ liệu.

### 6.2 Lazy injection không phải phép xóa vòng

Nếu hoãn khởi tạo B đến khi invocation đầu tiên gọi vào A, nhưng A vẫn gọi B và B vẫn gọi A, chu trình runtime **vẫn còn**. Lazy có thể thay **thời điểm phát sinh lỗi**, không thay ý nghĩa đường phụ thuộc.

Một phương án đúng cần **đổi cấu trúc cạnh**: đưa logic chung xuống một dịch vụ cấp thấp, chuyển tương tác sang event một chiều hoặc tách trách nhiệm.

### 6.3 Tránh nghiệp vụ trong constructor

Aspect constructor không nên tùy tiện gửi audit, gọi API, kích hoạt weaving hoặc truy cập một service đang ở trạng thái **chưa sẵn sàng**. Bootstrap nên tập trung vào cấu hình dependency. Giai đoạn xử lý request có thể khởi động sau khi các thành phần cần thiết đã được xây dựng và kiểm tra.

## 7. Vòng runtime: advice tự gọi lại chính nó

Một nguy cơ phổ biến là pointcut quá rộng:

~~~text
Service.handle()
   -> LoggingAdvice
        -> LogService.write()
             -> LoggingAdvice
                  -> LogService.write()
                       -> ...
~~~

Nếu `LogService.write` cũng là method được pointcut LoggingAdvice chọn, chính việc ghi log kích hoạt một lượt ghi log mới. Đây là **reentrancy/call cycle**, dù precedence DAG không có lỗi.

Tác động có thể gồm:
- `StackOverflowError`, CPU tăng và log storm.
- Nhiều event cho một invocation khiến metrics sai.
- Tăng latency hoặc timeouts.
- Lộ dữ liệu nếu log tiếp tục in arguments/context nhạy cảm.
- Exception từ bộ log che mất lỗi gốc của nghiệp vụ.
- Nguy cơ OOM khi một sự kiện phát ra nhiều sự kiện khác.

### 7.1 Ngăn bằng giới hạn pointcut

Ưu tiên chỉ chọn **ranh giới nghiệp vụ cần quan sát**, và tách:
- Mã helper của advice.
- Bộ ghi log/metric hoặc exporter.
- Hạ tầng dispatch event.
- InvocationHandler/proxy internals.
- Thao tác quản lý aspect và lifecycle.

Ví dụ giả mã scope:

~~~text
include: application.publicBusinessOperations
exclude: logging.sinks, tracing.exporters,
         metrics.collectors, aspect.helpers
~~~

Không có cú pháp chung để biểu diễn hai dòng trên; cần dùng quy tắc của công cụ thực tế. **Loại infrastructure khỏi logging advice** không có nghĩa cho phép infrastructure bỏ qua các yêu cầu bảo mật bắt buộc ở ranh giới khác.

### 7.2 Đừng gọi proxy chính nó để “proceed”

Một handler có thể mắc lỗi:

~~~text
handle(proxy.method):
    return proxy.method()
~~~

Nếu `proxy.method` lại gọi chính handler, ta có vòng vô hạn. Một proxy kiểu delegation thường phải gọi **target hoặc interceptor kế tiếp**. Nếu cần retry, phải có số lần thử hữu hạn và tránh lặp side effects.

### 7.3 Self-invocation khác vòng phụ thuộc

Trong mô hình proxy delegation:

~~~text
Client -> Proxy.outer() -> Target.outer()
                                  |
                                  +--> this.inner() trên target
~~~

Lời gọi `this.inner()` có thể **bypass proxy**, khiến advice cho inner không chạy. Đây là vấn đề **thiếu coverage**, không phải reentrancy. Ngược lại, khi handler gọi `proxy.outer()` từ chính handler, mới có nguy cơ **tự tái nhập**.

Một cơ chế subclass proxy có thể có dispatch khác; phải phân tích đường gọi thật, không đồng nhất mọi proxy hoặc mọi loại method execution.

## 8. Sử dụng guard đúng mức, không che lỗi bảo mật

Một guard theo thread có thể chặn **logging advice** tự ghi log khi đang ở trong đường log:

~~~text
if alreadyLoggingOnCurrentThread:
    proceedWithoutThisOptionalLog()
else:
    markLogging()
    try:
        recordMinimalEvent()
        proceed()
    finally:
        restorePreviousMark()
~~~

Đây là **giả mã**. Guard thích hợp **chỉ khi được phép bỏ log phụ trợ của invocation tái nhập**. Nó không thay điểm cắt, không kiểm tra cycle tĩnh và không tự xử lý các đường async chuyển thread.

**Không dùng guard để tự bỏ qua authorization, validation hoặc audit bắt buộc.** Một policy bảo mật chỉ cần xuất hiện ở lần gọi tái nhập là đủ để việc skip trở thành lỗ hổng. Kiểm soát quyền phải dựa trên ranh giới đáng tin và được kiểm thử cả đường direct/proxy call.

### 8.1 ThreadLocal và async

`ThreadLocal` bảo vệ trạng thái **của một Java thread**, không tự đi theo một request logic qua executor hoặc remote call. Nếu vòng event được chuyển qua thread/process khác, guard có thể không còn thấy marker. Cần một thiết kế theo **logical flow**, hop limit hoặc event deduplication phù hợp khi thật sự cần; đồng thời đảm bảo không truyền token/PII không cần thiết.

### 8.2 Guard phải khôi phục trạng thái

Với nested calls, đơn giản `set(false)` trong `finally` có thể xóa marker của một phạm vi ngoài nếu không xem xét giá trị trước đó. Cần **snapshot và restore**, hoặc dùng cơ chế scope/stack phù hợp. Điều này quan trọng khi thread pool tái sử dụng thread.

## 9. Event và callback tạo cycle gián tiếp

Không phải mọi vòng đều trực tiếp:

~~~text
Aspect A updates Store
Store notifies Listener B
Listener B calls Service C
Service C passes through Aspect A
Aspect A updates Store again
~~~

Chu trình này có thể chạy **đồng bộ hoặc bất đồng bộ**. Nếu dùng hàng đợi, stack có thể không tràn nhưng số lượng event vẫn tăng không giới hạn.

Biện pháp:
- Xác định event nào được phép **phát sinh event thứ cấp**.
- Dùng event ID/correlation và deduplication theo hợp đồng.
- Không cho listener phát lại cùng loại event về nguồn mà không có điều kiện dừng.
- Áp dụng deadline, retry budget và TTL cho workflow.
- Tránh callback không kiểm soát trong khi giữ lock.
- Có giới hạn queue/backpressure và cảnh báo số event trên một business operation.
- Kiểm tra thất bại của sink không gọi lại **chính sink bị lỗi** để báo lỗi mãi.

### 9.1 Cycle qua khóa

Nếu:
- Thread 1 giữ `L1`, advice A gọi B và chờ `L2`.
- Thread 2 giữ `L2`, advice B gọi A và chờ `L1`.

Đó là **wait-for cycle**, có thể gây deadlock. Cần thống nhất thứ tự lấy khóa, không gọi callback tùy ý khi giữ lock, rút ngắn critical section và test concurrency. Precedence order trong một request không bảo đảm thứ tự khóa giữa nhiều thread.

## 10. Không nhầm vòng với các tương tác đúng

### 10.1 Nhiều aspect cùng khớp một điểm

~~~text
Authorization(Business)
Tracing(Business)
~~~

Hai aspect cùng chọn business không chứng minh có cycle. Chỉ khi có một **quan hệ quay lại** (ví dụ Tracing gọi method bị Tracing chọn lại) hoặc các ràng buộc precedence trái ngược mới có vấn đề vòng.

### 10.2 Advice trước/sau theo hai chiều

Trong chuỗi:

~~~text
A(B(C(Business)))
~~~

đường vào `A.before -> B.before -> C.before`, còn khi kết thúc thành công `C.after -> B.after -> A.after`. Đó là kết quả tự nhiên của lời gọi lồng nhau.

### 10.3 Node phụ thuộc chung

Đồ thị:

~~~text
A -> C
B -> C
C -> (none)
~~~

không chứa cycle; hai node cùng phụ thuộc C. DFS phải phân biệt gặp node **đã xong** với gặp node **đang thăm**.

### 10.4 Đệ quy hợp lệ có base case

Một thuật toán tìm kiếm đệ quy có thể quay lại cùng function theo chiều sâu giảm dần và dừng khi đạt điều kiện cơ sở. AOP không cấm mọi recursion; cần xác định **termination invariant**, chiều sâu và nguy cơ advice nhân đôi quan sát. Nhưng một advice log gọi logger mà logger lại kích hoạt chính advice thường không có base case nghiệp vụ hữu ích.

## 11. Phân tích đặc biệt với retry, fallback và transaction

Giả sử:

~~~text
Retry(Transaction(Business))
Transaction(Retry(Business))
~~~

Đây không phải cycle chỉ vì Retry và Transaction bao nhau. Hai cấu trúc khác nhau về **ranh giới transaction cho mỗi attempt**. Nếu retry được cài để gọi continuation lại nhiều lần, cần kiểm soát idempotency.

Vòng xuất hiện khi:
- Retry A gọi fallback B.
- Fallback B lại gọi endpoint bị Retry A chọn.
- Không có giới hạn attempts/hop/deadline.

Hoặc một ErrorTranslator khi gặp lỗi gửi event tới một service mà chính nó chặn, làm sinh lỗi và event lặp.

Một thiết kế đúng cần:
1. Phân loại lỗi có thể retry.
2. Giới hạn số lần và tổng thời gian.
3. Chặn lặp side effect nhạy cảm.
4. Đảm bảo rollback/commit theo hợp đồng.
5. Phát audit/metric theo đơn vị **request** hay **attempt** đã định nghĩa.
6. Kiểm tra không có dependency ngược qua logging/error reporting.

## 12. Khi có nhiều cơ chế weaving

Compile-time/post-compile weaving có thể thêm helper vào bytecode. Nếu một weaver khác có pointcut quá rộng, nó có thể chọn helper đã thêm và tạo instrumentation lặp. Một load-time agent hoặc proxy runtime cũng có thể chặn đường đi tới helper theo cấu hình.

**Không tự khẳng định** mọi weaver đều nhận diện helper là join point; điều đó phụ thuộc ngôn ngữ pointcut và quy tắc loại trừ synthetic/generated methods.

Các bước kiểm chứng:
- Xác nhận **tập join point thực tế** được chọn.
- Phân biệt method `call` và `execution` nếu nền tảng hỗ trợ.
- Kiểm tra code helper do weaver sinh có bị chọn lại không.
- Kiểm tra class loader, artifact và phiên bản công cụ.
- Giữ đầu vào unwoven; không reweave tùy tiện nếu chưa rõ hợp đồng.
- Đếm số lần advice chạy tại một business event để phát hiện instrument hai lần.
- So sánh clean/incremental build khi thay pointcut/precedence.

Việc đảo thứ tự đọc file hoặc build **không tự làm hợp lệ** chu trình precedence vốn bất khả thi. Nếu ba ràng buộc vẫn mâu thuẫn, phải sửa ràng buộc chứ không đổi thứ tự compiler.

## 13. Cách thiết kế không vòng

### 13.1 Tách theo lớp phụ thuộc một chiều

~~~text
Business operations
         |
         v
Interception / policy boundary
         |
         v
Small recorder / validation interfaces
         |
         v
Independent infrastructure sinks
~~~

Những component tầng dưới không được gọi lại ranh giới tầng trên qua đường bị instrument tương tự, trừ khi có hành vi tái nhập được thiết kế và kiểm chứng.

### 13.2 Extract service dùng chung

Nếu aspect A gọi B vì cần format dữ liệu, còn B gọi A vì cần audit, thường nên trích một formatter hoặc event publisher riêng:

~~~text
A ---> SharedFormatter <--- B
A ---> SafeEventSink  <--- B
~~~

A và B phụ thuộc **xuống dưới**, không trực tiếp gọi lẫn nhau để giải quyết việc của mình.

### 13.3 Hợp nhất những bất biến không thể tách

Nếu hai aspect đều yêu cầu "tôi phải ở ngoài aspect kia" để quản lý cùng một transaction hoặc tài nguyên, có thể đây là một quy tắc điều phối quá gắn chặt để phân thành hai wrappers độc lập. Cân nhắc một orchestrator giữ ownership tài nguyên, hoặc chuyển logic bất biến vào tầng nghiệp vụ cốt lõi rồi để aspect chỉ quan sát.

Không gom mọi concern thành một giant aspect theo thói quen; chỉ hợp nhất nơi **không thể giải quan hệ phụ thuộc bằng ranh giới rõ ràng**.

### 13.4 Fail fast và chính sách rollback

Một builder nên từ chối:
- Đồ thị precedence có chu trình.
- Dependency construction không thể giải quyết.
- Aspect/rule tham chiếu node không tồn tại.
- Configuration để logging advice instrument chính logger theo đường vòng nguy hiểm đã biết.
- Retry không có termination policy.
- Priority tie mà thứ tự có ảnh hưởng an ninh nhưng chưa được giải quyết.

Lỗi nên chỉ rõ **đường vòng**, rule liên quan và đề xuất tách dependency. Giữ cấu hình đã chạy đúng trước đó để rollback nếu một bản mới tạo vòng.

## 14. Ma trận kiểm thử

| Tình huống | Điều phải chứng minh |
| --- | --- |
| Precedence DAG hợp lệ | Sinh order thỏa mọi cạnh |
| A→B→C→A | Từ chối và báo đường cycle |
| A→C và B→C | Không nhận nhầm là cycle |
| Cạnh trỏ node thiếu | Từ chối cấu hình |
| Tên aspect trùng | Không ghi đè âm thầm |
| Construction cycle | Phát hiện trước xử lý request |
| Logging self-observation | Không StackOverflowError/log storm |
| Proxy handler gọi proxy chính nó | Không reentry ngoài dự kiến |
| Self-invocation trên target | Không nhầm bypass với recursion |
| Event listener callback | Không phát event vô hạn |
| Retry/fallback | Attempts hữu hạn, side effects an toàn |
| Cache hit/authorization deny | Không bypass nghĩa vụ bảo mật |
| Exception/finally | Không che lỗi gốc, cleanup chạy |
| Concurrency/locks | Không deadlock do wait-for cycle |
| Async callback | Không quên giới hạn theo logical flow |
| Multiple weaving tools | Không instrument helper hoặc lặp advice ngoài ý định |
| Build/redeploy | Phiên bản precedence mới thật sự được dùng |
| Diagnostic logging | Không thu thập token/PII không cần thiết |

**Test chống vòng** nên dùng dữ liệu giả và giới hạn hữu hạn về số lời gọi/event để fail sớm. Không cần cố ý chạy đến `StackOverflowError` mới chứng minh có recursion. Với logger, có thể assert **một invocation nghiệp vụ tạo số event cố định**; với precedence, assert thứ tự advice và đường lỗi; với locks, dùng kiểm thử concurrency thích hợp.

## 15. Checklist kiểm tra trước production

- [ ] Xác định chính xác mỗi aspect, pointcut, advice type và vòng đời instance.
- [ ] Phân loại cạnh precedence, constructor, runtime call, callback, state và lock riêng biệt.
- [ ] Với precedence, xây DAG và tìm cycle bằng DFS/Kahn.
- [ ] In đường vòng cụ thể trong thông báo lỗi; không chọn thứ tự ngẫu nhiên.
- [ ] Phân biệt before/after nesting với cycle thật.
- [ ] Kiểm tra scope pointcut không chọn helper của chính advice gây reentry.
- [ ] Kiểm tra logging/metric sinks là dependency một chiều.
- [ ] Không để proxy handler gọi lại chính proxy khi nó cần chuyển tiếp vào target.
- [ ] Không dùng suppression guard để bỏ qua authorization hoặc audit bắt buộc.
- [ ] Xác định termination của retry, fallback, event dispatch và async workflow.
- [ ] Không gọi callback chưa kiểm soát trong lúc giữ lock.
- [ ] Bảo đảm event ID, dedup, TTL, quota và backpressure khi cần.
- [ ] Test nhiều aspects cùng match, short-circuit, error/finally và direct/proxy paths.
- [ ] Kiểm tra class loader, weaving scope và generated/bridge methods theo mô hình thực tế.
- [ ] Test clean/incremental build và artifact/runtime configuration.
- [ ] Đo overhead, log volume, stack depth và số event/attempt cho một request.
- [ ] Giữ khả năng rollback về graph và cấu hình đã xác minh.

## 16. Câu hỏi ôn tập

**Câu 1.** A phải bao ngoài B, B ngoài C, C lại ngoài A. Thay tên class có giải được không?

**Đáp:** không. Đây là cycle precedence. Cần sửa quan hệ bắt buộc/kiến trúc.

**Câu 2.** A và B cùng phụ thuộc C có phải cycle không?

**Đáp:** không. Hai cạnh hội tụ ở C không quay lại A hoặc B.

**Câu 3.** Logging advice ghi qua một method cũng được chính pointcut chọn. Tại sao nguy hiểm?

**Đáp:** việc ghi log lại kích hoạt advice, làm event được ghi lặp đến khi cạn tài nguyên. Cần sửa scope hoặc tách sink không được instrument.

**Câu 4.** Dùng một cờ `ThreadLocal` để bỏ qua advice đang tái nhập có đủ bảo vệ vòng qua event bus không?

**Đáp:** không. Event có thể chạy trên thread/process khác. Ngoài ra không được dùng guard để bỏ qua kiểm tra bảo mật bắt buộc.

**Câu 5.** A.before -> B.before -> B.after -> A.after có phải vòng dependency?

**Đáp:** không. Đây là ngữ nghĩa bình thường của hai around wrappers lồng nhau.

**Câu 6.** Vì sao một builder nên phát hiện cạnh tới aspect không tồn tại?

**Đáp:** nếu bỏ cạnh đi, chuỗi tạo ra có thể vi phạm invariant; cần báo cấu hình không hợp lệ thay vì âm thầm thay ý định.

**Câu 7.** Vòng A gọi B, B gọi A qua callback nhưng không nằm trong constructor có được bộ kiểm tra dependency khởi tạo phát hiện chắc chắn không?

**Đáp:** không. Phải kiểm tra runtime interaction graph và đường callback.

**Câu 8.** Hai aspect lấy khóa theo thứ tự khác nhau, điều gì có thể xảy ra?

**Đáp:** deadlock vì mỗi thread giữ một khóa và chờ khóa còn lại. Dùng thống nhất lock order và tránh callback trong critical section.

## 17. Kết luận

**Ngăn quan hệ phụ thuộc vòng giữa các aspect** không có nghĩa "cấm mọi lồng nhau" mà là **ngăn các ràng buộc bất khả thi và các đường gọi không có điểm dừng đáng tin**. Có ba câu hỏi phải trả lời đúng:

1. **Thứ tự bắt buộc có thỏa được không?** Nếu là precedence, hãy kiểm tra DAG và fail fast khi có cycle.
2. **Các aspect có phụ thuộc vòng khi tạo object không?** Nếu có, tách một interface/service nền độc lập và hướng dependency một chiều.
3. **Khi chạy, một advice có gọi lại chính điểm bị nó chặn không?** Nếu có, sửa scope/helper/sink, xác định termination và bảo vệ các nghĩa vụ không được phép bỏ.

Sau khi loại vòng, hãy kiểm chứng **tập join point, thứ tự advice, exception, short-circuit, retry, event, concurrency và kết quả nghiệp vụ**. Một sơ đồ dependency sạch mới là điều kiện cần; bài test runtime đúng và bảo toàn an ninh mới chứng minh được kiến trúc phối hợp nhiều aspect đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-008">Tách aspect độc lập khỏi aspect phải phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Tách aspect độc lập khỏi aspect phải phối hợp trong AOP

Khi một chương trình sử dụng nhiều **aspect** trong lập trình hướng khía cạnh (*Aspect-Oriented Programming*, **AOP**), câu hỏi kiến trúc không chỉ là “aspect nào chạy trước?”. Cần xác định **những aspect có thể hoạt động độc lập** và **những aspect phải có hợp đồng phối hợp** để bảo toàn ngữ nghĩa nghiệp vụ, bảo mật, vòng đời tài nguyên và kết quả của lời gọi.

Một aspect ghi nhận số lần gọi có thể **độc lập về dữ liệu và mục đích** với aspect ghi trace. Trong khi đó, một aspect kiểm toán được thiết kế để chỉ ghi nhận *giao dịch đã qua xác thực* có thể phải nhận kết quả xác thực từ một aspect khác. Nếu chúng không có hợp đồng rõ, việc hoán đổi thứ tự, bật/tắt riêng hoặc xử lý ngoại lệ có thể làm mất sự kiện kiểm toán, ghi sự kiện không đúng hoặc khiến chương trình trả kết quả không còn tin cậy.

**Ý chính:** “Tách độc lập” là giảm những phụ thuộc **không cần thiết**, không phải tuyên bố mọi aspect có thể đảo thứ tự mà không đổi gì. “Phải phối hợp” là đưa những phụ thuộc **thực sự bắt buộc** thành một hợp đồng được diễn đạt, kiểm thử và kiểm soát vòng đời; không phải để các aspect bí mật đọc/ghi cùng một biến toàn cục.

Bài này giải thích kiến thức AOP cơ bản và nâng cao nói chung. Các công nghệ AOP khác nhau về join point, pointcut, precedence, `proceed`, binding và việc dùng weaving hay proxy; ví dụ Java 17 xây một **chuỗi interceptor qua JDK dynamic proxy** để làm rõ nguyên tắc thiết kế, không giả định mọi weaver có hành vi giống proxy đó.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

1. Nhận diện aspect **độc lập về chính sách/dữ liệu** và aspect có **phụ thuộc thứ tự, kết quả, trạng thái hoặc vòng đời**.
2. Phân biệt **khả năng hoán đổi thứ tự** với **độc lập về module** và **không cần phối hợp về mặt nghiệp vụ**.
3. Viết hợp đồng cho quan hệ trước–sau, điều kiện chạy, quyền thay đổi đầu vào/kết quả và lỗi.
4. Xây một **đồ thị phụ thuộc có hướng** để kiểm tra precedence thay vì dựa vào thứ tự ngẫu nhiên.
5. Thiết kế ngữ cảnh theo **từng invocation** hoặc giao diện phối hợp tường minh, tránh field global/`ThreadLocal` không được dọn.
6. Phân tích sự tương tác giữa logging, metrics, authorization, cache, retry, transaction, audit và tài nguyên.
7. Biết khi nào nên tách aspect, khi nào cần một điều phối chung hoặc gộp hai chính sách có bất biến chặt.
8. Viết và chạy ví dụ Java 17 chứng minh một hợp đồng phối hợp bằng trạng thái invocation, đồng thời kiểm tra misordering thất bại có kiểm soát.
9. Kiểm thử cả đường thành công, từ chối, lỗi, short-circuit, retry, bypass và concurrency.

## 2. Các khái niệm nền

| Thuật ngữ | Vai trò |
| --- | --- |
| **Join point** | Điểm trong mô hình AOP có thể can thiệp, như thực thi phương thức khi công nghệ hỗ trợ |
| **Pointcut** | Điều kiện lựa chọn những join point cần áp dụng |
| **Advice** | Hành vi trước, sau, bao quanh hoặc theo nhánh lỗi của join point |
| **Aspect** | Đóng gói chính sách cắt ngang và những advice/pointcut liên quan |
| **Weaving/composition** | Kết hợp aspect với chương trình bằng compiler, bytecode hoặc đường gọi/proxy phù hợp |
| **Precedence/order** | Thứ tự có ý nghĩa khi nhiều aspect áp cùng điểm |
| **Invocation context** | Dữ liệu của một lượt gọi: phương thức, đối số, target, metadata và trạng thái nếu cơ chế cung cấp |
| **Proceed/next** | Tiếp tục tới phần bên trong chuỗi nếu loại advice/công cụ cho phép |
| **Short circuit** | Advice trả kết quả hoặc ném lỗi mà không chuyển tiếp đến phần còn lại |
| **Contract/invariant** | Quy tắc phải đúng dù thứ tự hoặc thành phần triển khai thay đổi |

Một **pointcut đúng** chỉ chứng minh điểm được lựa chọn theo bộ lọc; không tự chứng minh thứ tự hoặc việc **đường gọi thực tế có đi qua cơ chế can thiệp**. Với proxy, người gọi trực tiếp đối tượng gốc có thể bypass; với weaving bytecode, phạm vi lại phụ thuộc loại điểm và lớp đã được xử lý.

## 3. Độc lập có nhiều mức

Không nên dùng “độc lập” như một thuộc tính Boolean duy nhất. Một aspect có thể độc lập ở chiều này nhưng phụ thuộc ở chiều khác.

| Chiều đánh giá | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Chính sách** | Aspect có nhiệm vụ riêng, không biết logic của aspect khác? | Bộ đếm lời gọi |
| **Dữ liệu** | Có cần đọc dữ kiện do aspect khác tạo? | Audit yêu cầu kết quả xác thực |
| **Thứ tự** | Hoán đổi hai aspect có bảo toàn các bất biến được yêu cầu? | Timer đo tổng request vs từng attempt |
| **Luồng điều khiển** | Một aspect có thể ngăn aspect khác chạy? | Cache trả sớm trước authorization |
| **Lỗi** | Aspect có thay ngoại lệ hoặc chuyển lỗi thành fallback? | Retry hoặc exception mapper |
| **Vòng đời** | Aspect có chia sẻ tài nguyên/scope với aspect khác? | Transaction và resource cleanup |
| **Triển khai** | Có bật/tắt, phiên bản, cấu hình và kiểm thử riêng được không? | Metrics phụ trợ |
| **An toàn** | Nếu thiếu một aspect, hệ có còn thỏa bất biến quan trọng? | Quyền truy cập, kiểm toán bắt buộc |

**Một aspect có thể độc lập về mã nguồn nhưng không hoán đổi thứ tự được.** Ví dụ Timing không gọi trực tiếp Retry và ngược lại, nhưng `Timing(Retry(Target))` có thể đo **toàn bộ chuỗi thử lại**, trong khi `Retry(Timing(Target))` có thể đo **từng attempt**. Cả hai có thể độc lập về module; ý nghĩa metric lại phụ thuộc vị trí.

Vì thế trước khi phân loại, hãy viết **đơn vị quan sát** (*logical request*, *attempt*, *committed effect*) và **bất biến nghiệp vụ**.

## 4. Aspect độc lập: những đặc điểm thường có

Một aspect dễ tách độc lập khi:

- Nó sở hữu một mục tiêu rõ ràng, ví dụ ghi một chỉ số theo lời gọi, không điều khiển nghiệp vụ.
- Nó dùng dữ liệu **có sẵn tại join point** hoặc một hợp đồng chung ổn định, thay vì yêu cầu đọc field riêng của aspect khác.
- Nó không đổi đối số, không gọi `proceed` nhiều lần, không tùy tiện thay giá trị trả về hoặc ngoại lệ.
- Khi bị bỏ đi, nghiệp vụ cốt lõi vẫn đúng theo yêu cầu; nếu chính sách là bắt buộc, phải có một cơ chế khác bảo đảm yêu cầu ấy.
- Nó không giữ request/target trong trạng thái sống lâu ngoài nhu cầu thực tế.
- Nó có thể được kiểm thử bằng hợp đồng đầu vào/đầu ra của chính mình.
- Nó công bố chính xác **scope đo đạc**: lời gọi đã vào proxy, lời gọi đã khớp pointcut, attempt hay effect được cam kết.

Ví dụ một **counter** đếm số lần gọi tới pointcut có thể không cần biết nội dung của Trace. Tuy nhiên khi đặt bên trong Authorization, nó không đếm các request bị từ chối **trước** Authorization; do đó quyết định vị trí vẫn phải nằm trong hợp đồng của metric.

### 4.1. “Chỉ quan sát” cũng có thể làm sai chương trình

Một advice ghi log có thể ném lỗi khi `toString()` của đối số thất bại, chặn luồng vì I/O hoặc vô tình đưa dữ liệu nhạy cảm vào log. Một bộ đo thời gian có thể lưu object request vào static list và gây retention. Nếu logic quan sát được thiết kế là không ảnh hưởng nghiệp vụ, phải có chính sách cho **lỗi của cơ chế quan sát** và cho lượng dữ liệu được giữ.

“Observer độc lập” không đồng nghĩa “đương nhiên vô hại”.

### 4.2. Một ví dụ quan hệ độc lập

```text
Caller
  |
  v
Trace (xác định correlation ID)
  |
  v
Counter (đếm lời gọi)
  |
  v
Target
```

Nếu Trace không phụ thuộc Counter, Counter không phụ thuộc Trace và cả hai chuyển tiếp đúng một lần, chúng thường có thể phát triển riêng. Nhưng nếu Counter được yêu cầu chỉ đếm các **target execution thành công**, nó phải được đặt hoặc thiết kế phù hợp với định nghĩa thành công. Sự độc lập về *implementations* không thay thế tài liệu về **điều kiện đo**.

## 5. Aspect phải phối hợp: những dấu hiệu nhận biết

Một quan hệ phối hợp tồn tại khi có ít nhất một trong các tình huống:

1. **Phụ thuộc thứ tự bắt buộc:** chính sách B chỉ hợp lệ sau khi A đã thực hiện một kiểm tra hoặc mở scope.
2. **Phụ thuộc dữ liệu:** B phải dùng kết quả của A, ví dụ danh tính đã xác thực hoặc transaction identifier.
3. **Phụ thuộc trạng thái:** B cần biết một tác vụ đã chuẩn bị, đã cam kết, đã rollback hay chưa.
4. **Phụ thuộc vòng đời:** A mở tài nguyên mà B phải dùng/đóng trong đúng phạm vi.
5. **Phụ thuộc luồng lỗi:** A quyết định retry/fallback, B cần quan sát kết quả cuối hay từng attempt.
6. **Phụ thuộc độ phủ:** A phải bảo đảm kiểm soát quyền trước mọi con đường tới thao tác nhạy cảm; B không thể được đặt ở chỗ bypass chính sách đó.
7. **Phụ thuộc nguyên tử:** hai hành vi phải nhất quán trong một transaction hoặc qua một giao thức rõ, không thể chỉ dựa vào thứ tự callback.

Một ví dụ có chủ ý: **Authorization** đặt dấu xác minh nội bộ trong context của lượt gọi; **CommittedAudit** chỉ ghi sự kiện “đã qua kiểm tra quyền và đã tạo effect” nếu dấu ấy tồn tại và target trả kết quả xác nhận commit. Hai aspect ấy phải phối hợp theo **hợp đồng do kiến trúc thiết kế**, chứ không phải tất cả audit trong AOP đều mặc định phải phụ thuộc authorization.

### 5.1. Phụ thuộc thực sự và phụ thuộc do code viết kém

Hai aspect có thể “phải phối hợp” chỉ vì chúng cùng đọc/ghi một static Boolean như `lastAuthorized`. Đây thường là **phụ thuộc ngẫu nhiên và nguy hiểm**: hai request đồng thời sẽ tranh chấp cùng field.

Thay vì công nhận static field là hợp đồng, nên:

- Đưa dữ liệu vào một đối tượng context **theo invocation**;
- Định nghĩa rõ ai tạo, ai đọc, điều kiện hợp lệ và thời hạn tồn tại;
- Chỉ cấp quyền cập nhật cho thành phần được chỉ định;
- Nếu thông tin bắt buộc, **fail closed** khi thiếu context hoặc cấu hình sai;
- Kiểm thử concurrency và các đường không được aspect bao phủ.

Tốt hơn nữa, nếu hai chính sách phải luôn thành công/thất bại cùng nhau, cân nhắc một **đơn vị điều phối** thay vì hai interceptor ngầm kết nối.

## 6. Precedence: thứ tự là hợp đồng, không phải chi tiết trang trí

Trong chuỗi around advice, một cấu trúc:

```text
Outer(Inner(Target))
```

thường chạy theo trình tự:

```text
Outer before
    Inner before
        Target
    Inner after / catch / finally
Outer after / catch / finally
```

Nếu Outer không gọi `proceed`, cả Inner và Target có thể không chạy. Nếu Outer retry bằng cách gọi tiếp nhiều lần, Inner và Target có thể được thực thi lặp. Một số công nghệ giới hạn số lần hoặc ngữ nghĩa `proceed`, vì vậy không mặc định mọi mô hình AOP đều hỗ trợ retry bằng cách đó.

### 6.1. Quan hệ phụ thuộc có hướng

Giả sử một nghiệp vụ được thiết kế yêu cầu:

```text
Authorize trước khi chạy operation
Transaction scope bao quanh operation
CommittedAudit chỉ ghi sau khi biết effect đã commit
```

Ta có thể mô hình hóa các ràng buộc:

```text
Authorization -> Operation (must authorize before sensitive effect)
Transaction  -> Operation (must establish transaction scope)
CommitResult -> CommittedAudit (must have confirmed outcome)
```

Các mũi tên mô tả **phụ thuộc ngữ nghĩa**, không phải tuyên bố một thứ tự tuyến tính duy nhất cho mọi AOP. Với một chuỗi around, “A bao ngoài B” có nghĩa A chạy phần before trước B, nhưng phần after của A chạy **sau** phần after của B. Cần xét **đúng vị trí trong before/proceed/after**, không chỉ một danh sách tên.

### 6.2. Khi nào cần đồ thị DAG?

Nếu có nhiều quan hệ `A trước B`, `B trước C` và `A trước D`, có thể biểu diễn thành **đồ thị có hướng không chu trình (DAG)** rồi kiểm tra một thứ tự hợp lệ. Khi xuất hiện vòng như `A phải hoàn tất trước B` và `B phải hoàn tất trước A` trong cùng giai đoạn, thiết kế có thể mâu thuẫn.

Không giải bằng cách chọn precedence tùy ý: có thể cần tách **prepare** và **finalize**, phát hành một kết quả bất biến, hoặc gộp hành vi trong một coordinator.

### 6.3. Một bảng ví dụ về mức phụ thuộc

| Cặp aspect | Quan hệ thường phải xem | Có thể hoán đổi tự do không? |
| --- | --- | --- |
| Trace và Counter đơn giản | Độc lập về state, cùng quan sát lời gọi | Về nghiệp vụ thường được, nhưng chi tiết log/count/timing có thể khác |
| Authorization và Cache | Cache có thể short-circuit quyền ở bên trong | Không nếu mọi cache hit phải được xác thực |
| Retry và Metrics | Một request hay mỗi attempt | Chỉ khi hợp đồng metric chấp nhận |
| Retry và Transaction | Mỗi attempt có transaction riêng hay dùng chung | Phụ thuộc trạng thái transaction và điều kiện retry |
| Transaction và CommitAudit | Lúc nào có bằng chứng commit | Phải phối hợp theo hợp đồng commit |
| Validation và Normalization | Normalization có đổi dữ liệu được xác thực? | Cần quy tắc về input canonical |
| Redaction và Logging | Không được ghi dữ liệu thô nhạy cảm | Cần phối hợp chính sách bảo vệ dữ liệu |
| Rate-limit và Retry | Retry có tiêu thụ quota mỗi attempt? | Cần định nghĩa đơn vị giới hạn |
| Cache và Business Operation | Có thể trả sớm thay vì thực hiện effect | Chỉ hợp lệ với những thao tác cho phép short circuit |

Một kết luận khác cũng quan trọng: **phối hợp không nhất thiết là dependency mã nguồn**. Hai aspect có thể không import nhau nhưng phải tuân theo một contract về precedence và dữ liệu.

## 7. Dữ liệu phối hợp nên đi qua đâu?

Một số lựa chọn, từ rõ ràng tới dễ gây lỗi:

### 7.1. Kết quả hoặc ngoại lệ của phần chuỗi bên trong

Một advice quanh có thể quan sát `result` của `proceed`, hoặc `Throwable` nếu lời gọi bị ném lỗi. Đây là **đường phối hợp tự nhiên** nếu thông tin cần thiết nằm trong hợp đồng phương thức đích.

Nhưng “phương thức trả bình thường” **không đồng nghĩa nghiệp vụ đã commit**. Một `Result.failure(...)` vẫn có thể là return bình thường; một lời gọi trả `CompletableFuture` chưa chắc công việc đã hoàn tất. Audit phải hiểu trạng thái nghiệp vụ thực, không suy đoán từ nhánh after-returning.

### 7.2. Invocation-scoped context

Khi hai interceptor cần dữ liệu trung gian, có thể dùng một context theo lượt gọi:

```text
context {
    requestId
    authorizationDecision
    operationStatus
    attemptNumber
}
```

Đây là **mô hình dữ liệu**, không phải API chuẩn của AOP. Phải quy định **ai cấp dữ liệu, ai được đọc, giá trị mặc định, giai đoạn hợp lệ và thời điểm dọn**. Một map `String → Object` không có kiểm soát kiểu có thể tạo xung đột tên/cast sai; với hợp đồng quan trọng nên dùng kiểu rõ ràng và giới hạn quyền sửa.

Context của request không được coi là một “kho tin cậy” chỉ vì có một cờ `authorized=true`; phải tin cậy **nguồn xác thực** và mọi đường tới effect.

### 7.3. Service hoặc giao diện phối hợp

Nếu hai aspect cần một giao thức nghiêm ngặt, có thể trích thành một interface rõ, ví dụ:

```text
Decision authorize(Command)
Receipt executeAuthorized(Decision, Command)
void recordConfirmed(Receipt)
```

Đây là **giả mã hợp đồng**, không phải Java chạy độc lập. Một coordinator điều phối thứ tự, kiểm tra bất biến và chuyển những dữ kiện đã xác nhận giữa các bước.

Một coordinator thường dễ kiểm thử hơn khi có chuỗi phụ thuộc sâu, nhiều rollback và nghĩa vụ bảo mật/kiểm toán. Dùng AOP để tổ chức hành vi cắt ngang, nhưng **không giấu quy trình nghiệp vụ cốt lõi trong những thứ tự interceptor khó nhìn thấy**.

### 7.4. Event với danh tính và trạng thái rõ

Một cặp aspect có thể phối hợp qua sự kiện kiểu `Authorized`, `Committed` hoặc `Failed` nếu sự kiện thật sự thể hiện trạng thái đáng tin. Tuy nhiên publish async có thể giao lại, mất phản hồi hoặc đến sai thứ tự theo mô hình phân tán.

Khi cần bảo đảm, hãy định nghĩa delivery, idempotency, retention và đối soát. Không suy rằng hai advice publish và consume một event là có transaction nguyên tử.

## 8. Ví dụ Java 17: độc lập Trace/Metrics, phối hợp Authorization/Audit

Chương trình dưới đây tự tạo một **interceptor chain** với JDK dynamic proxy:

- **Trace** ghi trước/sau/lỗi và không biết nội bộ Authorization, Audit hay Metrics.
- **Metrics** đếm lượt vào proxy cho thao tác `place`, không cần kết quả riêng từ aspect khác.
- **Authorization** kiểm tra token đơn giản rồi đặt dấu đã xác minh trong state **của lượt gọi**.
- **CommittedAudit** yêu cầu state đã xác minh **trước khi** cho chuỗi chạy tiếp, sau đó chỉ in sự kiện với kết quả `Receipt` do target trả.
- Một cấu hình sai đặt **Audit trước Authorization** bị từ chối rõ ràng thay vì âm thầm chạy.
- Lời gọi trực tiếp target minh họa đường **bypass proxy**: đây là lý do những biện pháp kiểm soát quyền thật phải được bảo vệ tại ranh giới đáng tin cậy, không chỉ dựa vào một proxy có thể bị bỏ qua.

**Giới hạn:** Ví dụ giả định target tạo kết quả `Receipt` chỉ khi thao tác đơn giản đã hoàn tất trong bộ nhớ; nó **không** cài đặt giao dịch cơ sở dữ liệu, audit bền vững hoặc một cơ chế bảo mật sản xuất. Token `"allow"` là dữ liệu học tập, không phải xác thực thật. Không dùng mẫu này làm dịch vụ tài chính mà chưa bổ sung hợp đồng nghiệp vụ, xác thực tại ranh giới và lưu trạng thái thích hợp.

Lưu toàn bộ vào tệp `AspectCoordinationDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AspectCoordinationDemo {
    record Receipt(String id, int commitNumber) {}

    interface OrderPort {
        Receipt place(String token, String id);
        String inspect(String id);
    }

    static final class OrderService implements OrderPort {
        private final AtomicInteger commits = new AtomicInteger();

        @Override
        public Receipt place(String token, String id) {
            int n = commits.incrementAndGet();
            System.out.println("TARGET committed " + id + "#" + n);
            return new Receipt(id, n);
        }

        @Override
        public String inspect(String id) {
            return "VIEW:" + id;
        }

        int commitCount() {
            return commits.get();
        }
    }

    static final class State {
        boolean authorized;
    }

    @FunctionalInterface
    interface Terminal {
        Object call() throws Throwable;
    }

    @FunctionalInterface
    interface Interceptor {
        Object around(Invocation invocation) throws Throwable;
    }

    static final class Invocation {
        final Method method;
        final Object[] args;
        final State state;
        final List<Interceptor> chain;
        final int position;
        final Terminal terminal;

        Invocation(Method method, Object[] args, State state,
                   List<Interceptor> chain, int position,
                   Terminal terminal) {
            this.method = method;
            this.args = args;
            this.state = state;
            this.chain = chain;
            this.position = position;
            this.terminal = terminal;
        }

        Object proceed() throws Throwable {
            if (position == chain.size()) {
                return terminal.call();
            }
            Interceptor next = chain.get(position);
            Invocation remainder = new Invocation(
                    method, args, state, chain, position + 1, terminal);
            return next.around(remainder);
        }
    }

    static final class Trace implements Interceptor {
        @Override
        public Object around(Invocation invocation) throws Throwable {
            System.out.println("TRACE enter place");
            try {
                Object result = invocation.proceed();
                System.out.println("TRACE return");
                return result;
            } catch (Throwable error) {
                System.out.println("TRACE error "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println("TRACE finally");
            }
        }
    }

    static final class Metrics implements Interceptor {
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public Object around(Invocation invocation) throws Throwable {
            System.out.println("METRIC attempt=" + attempts.incrementAndGet());
            return invocation.proceed();
        }

        int count() {
            return attempts.get();
        }
    }

    static final class Authorization implements Interceptor {
        @Override
        public Object around(Invocation invocation) throws Throwable {
            if (!"allow".equals(invocation.args[0])) {
                System.out.println("AUTH denied");
                throw new SecurityException("denied");
            }
            System.out.println("AUTH passed");
            invocation.state.authorized = true;
            return invocation.proceed();
        }
    }

    static final class CommittedAudit implements Interceptor {
        @Override
        public Object around(Invocation invocation) throws Throwable {
            if (!invocation.state.authorized) {
                throw new IllegalStateException(
                        "audit requires authorization");
            }
            Object result = invocation.proceed();
            if (result instanceof Receipt receipt) {
                System.out.println("AUDIT committed " + receipt.id()
                        + "#" + receipt.commitNumber());
            }
            return result;
        }
    }

    static Object invokeTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static OrderPort wrap(OrderPort target, List<Interceptor> chain) {
        return (OrderPort) Proxy.newProxyInstance(
                OrderPort.class.getClassLoader(),
                new Class<?>[] { OrderPort.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("place")) {
                        return invokeTarget(method, target, args);
                    }
                    Object[] values = args.clone();
                    State state = new State();
                    Terminal terminal = () -> invokeTarget(
                            method, target, values);
                    Invocation start = new Invocation(
                            method, values, state, chain, 0, terminal);
                    return start.proceed();
                });
    }

    public static void main(String[] args) {
        OrderService service = new OrderService();
        Metrics metrics = new Metrics();
        OrderPort protectedPort = wrap(service, List.of(
                new Trace(), metrics, new Authorization(),
                new CommittedAudit()));

        System.out.println("READY");
        Receipt ok = protectedPort.place("allow", "R-1");
        System.out.println("CALLER " + ok.id() + "#" + ok.commitNumber());

        try {
            protectedPort.place("deny", "R-2");
        } catch (SecurityException error) {
            System.out.println("CALLER_ERROR " + error.getMessage());
        }

        System.out.println("INSPECT " + protectedPort.inspect("R-1"));
        System.out.println("COMMITS " + service.commitCount());
        System.out.println("ATTEMPTS " + metrics.count());

        OrderPort wrongOrder = wrap(service, List.of(
                new CommittedAudit(), new Authorization()));
        try {
            wrongOrder.place("allow", "R-3");
        } catch (IllegalStateException error) {
            System.out.println("MISORDER " + error.getMessage());
        }
        System.out.println("COMMITS_AFTER_MISORDER "
                + service.commitCount());

        Receipt direct = service.place("allow", "R-4");
        System.out.println("DIRECT " + direct.id() + "#"
                + direct.commitNumber());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 AspectCoordinationDemo.java
java AspectCoordinationDemo
```

Đầu ra dự kiến chính xác:

```text
READY
TRACE enter place
METRIC attempt=1
AUTH passed
TARGET committed R-1#1
AUDIT committed R-1#1
TRACE return
TRACE finally
CALLER R-1#1
TRACE enter place
METRIC attempt=2
AUTH denied
TRACE error SecurityException
TRACE finally
CALLER_ERROR denied
INSPECT VIEW:R-1
COMMITS 1
ATTEMPTS 2
MISORDER audit requires authorization
COMMITS_AFTER_MISORDER 1
TARGET committed R-4#2
DIRECT R-4#2
```

### 8.1. Vì sao Trace và Metrics được xem là tách độc lập?

Cả hai chỉ làm việc với `Invocation` và `proceed()`, không đọc cờ xác thực hoặc dữ liệu riêng của CommittedAudit. Chúng không có dependency code với Authorization/Audit. Khi chuyển từ `Trace(Metrics(...))` sang `Metrics(Trace(...))`, kết quả nghiệp vụ cơ bản trong ca đơn giản vẫn có thể giữ nguyên.

Tuy nhiên, **chi tiết thứ tự log và số liệu tính toán** có thể đổi theo vị trí, đặc biệt khi đặt Metrics **sau** Authorization thì request bị từ chối sẽ không đi tới Metrics. Đây là sự khác biệt giữa **không cần state của nhau** và **bảo đảm hoàn toàn có thể hoán đổi**.

### 8.2. Vì sao Authorization và CommittedAudit cần phối hợp?

Trong thiết kế cố ý của ví dụ, CommittedAudit phải thấy `state.authorized=true` **trước khi chuyển tiếp**. Authorization là thành phần duy nhất đặt dấu này sau khi kiểm tra. Mỗi lời gọi `place` tạo một `State` mới; thông tin của `R-1` không rò sang `R-2`.

Nếu đảo thứ tự thành `CommittedAudit(Authorization(Target))`, CommittedAudit kiểm tra khi chưa có dấu xác minh và ném `IllegalStateException` **trước khi target chạy**. Dòng `COMMITS_AFTER_MISORDER 1` cho thấy lời gọi `R-3` không tạo thêm effect trong chương trình mẫu.

Đây là một **hợp đồng kiến trúc cụ thể**. Trong hệ thật, cờ Boolean mutable do interceptor truyền không phải chứng cứ an toàn độc lập cho quyền truy cập; cần nguồn xác thực đáng tin, mô hình kiểm soát quyền tại ranh giới nghiệp vụ và kiểu dữ liệu có bảo vệ phù hợp.

### 8.3. Vì sao audit không ghi cho request bị từ chối?

Authorization không gọi tiếp khi token không hợp lệ, vì vậy CommittedAudit nằm bên trong không được chạy. **Đây không có nghĩa sự kiện từ chối không cần audit**: nếu chính sách bắt buộc ghi cả truy cập bị từ chối, phải dùng một **DenialAudit** thích hợp ở ranh giới ngoài hoặc một giao thức audit có hai loại sự kiện rõ ràng.

Không đổi vị trí CommittedAudit lên ngoài Authorization chỉ để “có log” nếu điều đó phá hợp đồng **chỉ ghi commit đã qua kiểm tra**.

### 8.4. Vì sao `inspect` không có interceptor log?

Handler cố ý chỉ áp chain cho phương thức có tên `place`. `inspect` đi qua proxy nhưng được chuyển tiếp trực tiếp đến target. Nếu hệ thống có overload cùng tên hoặc nhiều phương thức nhạy cảm, bộ lọc chỉ theo tên là **quá hẹp hoặc quá rộng tùy trường hợp**; phải kiểm tra đầy đủ chữ ký và phạm vi.

### 8.5. Vì sao lời gọi trực tiếp là vấn đề nghiêm trọng?

`service.place("allow", "R-4")` bypass toàn bộ proxy, nên có dòng `TARGET committed` mà không có `AUDIT committed`. Điều này minh họa lỗi **coverage**, không chỉ lỗi precedence.

Với bất biến bảo mật hoặc kiểm toán bắt buộc, không nên xem một proxy tùy chọn là hàng rào duy nhất. Cần bảo vệ effect ở **ranh giới đáng tin**, kiểm soát cách cấp tham chiếu/đường gọi và kiểm thử những đường vòng tương ứng. Một weaver khác có phạm vi join point khác, nhưng cũng phải xác minh coverage thực.

### 8.6. Giới hạn kỹ thuật của ví dụ

- `Invocation` và `State` được viết thủ công, không phải API chuẩn của mọi AOP.
- `Receipt` xác nhận hoàn tất thao tác **trong bộ nhớ ví dụ**; không có database transaction, durable audit hay distributed commit.
- Trace/Metrics không xử lý lỗi I/O của logging hoặc metric backend; hệ thật cần chính sách lỗi của hệ quan sát.
- `State` thuộc từng invocation, tránh dùng field singleton cho dữ liệu người gọi.
- `args.clone()` chỉ sao chép mảng, không sao chép sâu từng đối tượng.
- JDK proxy chỉ intercept lời gọi qua giao diện, không tự chặn field access, constructor, phương thức private hoặc self-invocation trên target.
- Ví dụ không có retry; nếu thêm retry, cần xử lý các attempt, commit trùng và idempotency trước khi tuyên bố audit chính xác.

## 9. Tách theo sự kiện “đã cam kết”, không theo exception đơn giản

Một cặp transaction và audit là ví dụ điển hình cần đặc biệt thận trọng:

```text
Audit(Transaction(Target))
Transaction(Audit(Target))
```

Ở cấu trúc đầu, Audit ngoài có thể chạy sau khi Transaction đã trả về hoặc ném lỗi. Ở cấu trúc sau, Audit bên trong có thể ghi khi target đã trả về nhưng **transaction bên ngoài chưa commit**. Nếu commit sau đó thất bại, bản ghi audit “đã commit” có thể sai.

Ngay cả cấu trúc Audit ngoài cũng **không đủ** để bảo đảm audit luôn bền vững hoặc transaction commit toàn bộ hệ thống ngoài. Một transaction nội bộ trả thành công có thể còn phải phát event ra mạng; audit sink có thể thất bại riêng.

Nếu yêu cầu là “ghi audit bền vững **cùng** hiệu ứng nghiệp vụ”, cần thiết kế **ranh giới transaction, outbox hoặc cơ chế phối hợp đáng tin** thích hợp. Thứ tự aspect là một phần, không thay thế atomicity/đối soát.

### 9.1. “After-returning” không phải “business success”

Một phương thức trả `Result(status=REJECTED)` là nhánh trả về bình thường nhưng chưa tạo giao dịch. Một phương thức trả `Future<Result>` có thể kết thúc phần đồng bộ khi future chưa hoàn tất. Một audit aspect phải dùng **kết quả đã được xác nhận** theo hợp đồng cụ thể, không chỉ dựa vào việc không thấy exception.

### 9.2. Trạng thái không chắc chắn sau timeout

Một lần gửi yêu cầu có thể đã tạo effect ở bên nhận rồi mất phản hồi. Nếu aspect Retry gọi lại `proceed`, nó có thể tạo effect thứ hai. Audit dựa trên số lần reply thành công có thể thiếu hoặc thừa so với số effect thực sự.

Sự phối hợp an toàn cần nhận diện một **logical request**, idempotency key, quy tắc retry, khả năng đối soát và ranh giới commit. Không giải quyết được bằng cách đảo Logging và Retry.

## 10. Có nên cho các aspect biết trực tiếp nhau không?

Có một phổ thiết kế:

| Mức gắn kết | Hình thức | Khi nào cân nhắc |
| --- | --- | --- |
| **Độc lập hoàn toàn** | Chỉ dùng join point/proceed, không biết nhau | Logging và một số chỉ số |
| **Hợp đồng precedence** | Aspect không import nhau nhưng được đặt theo thứ tự rõ | Authorization và Cache |
| **Shared invocation contract** | Dùng dữ liệu theo một lần gọi, có kiểu/ownership | Verified identity cho audit |
| **Service/coordinator trung gian** | Cùng gọi qua một giao diện chính thức | Transaction + audit bền vững, nhiều bước |
| **Direct dependency** | Aspect A gọi public API của aspect B | Chỉ khi có lý do rõ, tránh coupling ngầm |
| **Một aspect thống nhất** | Gộp các hành vi không thể tách trách nhiệm | Bất biến phải duy trì nguyên tử trong một scope |

Không phải gắn kết càng thấp luôn càng tốt. Nếu có một **bất biến không thể bảo đảm khi tách**, gộp chính sách hoặc tạo coordinator có thể giúp rõ ràng và kiểm thử được. Ngược lại, gộp mọi trace/metric/cache/security vào một “siêu aspect” làm hệ khó tái sử dụng, đổi thứ tự và phân tích lỗi.

### 10.1. Quy tắc quyết định

Hỏi theo thứ tự:

1. Hai aspect có thể vận hành mà không đọc kết quả hay state của nhau không?
2. Nếu hoán đổi thứ tự, những bất biến quan trọng có đổi không?
3. Nếu một aspect short-circuit, aspect kia có còn **bắt buộc** chạy không?
4. Nếu một aspect lỗi, thành phần kia phải fail-open, fail-closed hay ghi trạng thái không chắc chắn?
5. Có trạng thái cần tồn tại xuyên nhiều join point không? Ai sở hữu nó?
6. Nếu hai aspect cùng chạy trong retry, số lần effect có thay đổi không?
7. Khi bật/tắt riêng từng aspect, hệ thống còn đúng hợp đồng không?
8. Việc phối hợp có thuộc ngữ nghĩa nghiệp vụ cốt lõi đến mức phải **đưa ra khỏi AOP** không?

Nếu câu 2–8 bộc lộ phụ thuộc thật, hãy ghi nó thành contract và test, thay vì chỉ đặt thứ tự thủ công trong cấu hình.

## 11. Nguy cơ phụ thuộc vòng và thứ tự không thể thỏa

Xét hai lời hứa:

- A phải kết thúc hoàn toàn trước khi B bắt đầu.
- B phải kết thúc hoàn toàn trước khi A bắt đầu.

Nếu chúng áp cùng một lần gọi và không có phân chia giai đoạn, đây là **vòng phụ thuộc không thể thỏa**. Trong around advice, trước/sau tạo một cấu trúc lồng:

```text
A.before -> B.before -> target -> B.after -> A.after
```

Ta có thể yêu cầu `A.before` trước `B.before` nhưng đồng thời `B.after` trước `A.after` mà không mâu thuẫn. Vì thế đồ thị cần mô tả **sự kiện/giai đoạn** chứ không chỉ nút “A”, “B” mơ hồ.

### 11.1. Chia một aspect thành prepare/finalize

Một cách giải quan hệ phức tạp là chia chính sách thành:

```text
Authorization.prepare
Operation.execute
Commit.confirm
Audit.finalize
```

Các điểm có thể có hợp đồng dữ liệu bất biến và lỗi rõ ràng. Trong các giao dịch quan trọng, đôi khi quy trình này thích hợp hơn như **orchestrator nghiệp vụ**, trong khi aspect chỉ đóng vai trò observation hoặc guard ở các ranh giới thích hợp.

### 11.2. Đừng dựa vào thứ tự phát hiện tệp

Thứ tự lớp được nạp, file được liệt kê hay aspect được đăng ký không nên dùng thay cho hợp đồng precedence trừ khi công nghệ có **bảo đảm chính thức**. Khi thay công cụ/phiên bản hoặc refactor package, phải chạy test cho thứ tự và coverage.

## 12. Short-circuit và ảnh hưởng tới aspect bên trong

Nếu Cache bao ngoài Authorization:

```text
Cache(Authorization(Target))
```

một cache hit có thể trả kết quả ngay và **Authorization không chạy**. Nếu kết quả cache dành riêng cho người dùng, bỏ qua quyền có thể tạo lỗi bảo mật.

Nếu Authorization bao ngoài Cache:

```text
Authorization(Cache(Target))
```

kiểm tra quyền có thể chạy trước khi cache trả dữ liệu. Nhưng còn phải xét key cache có bao gồm tenant/user và dữ liệu có bị stale hay không. Chỉ đổi thứ tự không tự bảo đảm cấu hình cache an toàn.

Tương tự, một interceptor `Fallback` trả giá trị sau lỗi có thể khiến aspect ngoài thấy **return bình thường** thay vì ngoại lệ gốc. Nếu mục tiêu là log lỗi đích, điểm quan sát phải nằm **bên trong** fallback hoặc có một tín hiệu kết quả tường minh.

### 12.1. Hai loại audit thường khác nhau

- **Attempt/denial audit:** ghi nhận có ai thử thao tác, kể cả bị chặn.
- **Committed-effect audit:** ghi nhận thao tác đã xác nhận hoàn tất.

Chúng có thể dùng chung hạ tầng ghi sự kiện nhưng không nên ép về một nhánh advice duy nhất mà không định nghĩa rõ. Một aspect ghi nhận **tất cả attempts** có thể nằm ngoài Authorization; một aspect **confirmed commit** cần kết quả đáng tin ở phía sau.

## 13. Concurrency, ngữ cảnh và vòng đời

### 13.1. Shared field gây lẫn các lượt gọi

Một singleton aspect có:

```java
Object currentRequest;
```

không nên dùng field đó để phối hợp request A và B. Hai luồng có thể ghi đè state, và trường còn giữ tham chiếu tới request sau khi lời gọi xong.

State phối hợp nên được truyền qua **invocation context** hoặc một scope được định nghĩa rõ. Nếu dùng `ThreadLocal`, phải lưu/khôi phục parent cho nested scope và `remove()` khi kết thúc trên thread sống lâu.

### 13.2. Async không giữ nguyên call stack

Một phương thức trả `CompletableFuture` rồi việc thật xảy ra trên executor. Context lưu theo thread gọi không tự truyền sang worker thread. Nếu cố giữ toàn bộ object request trong callback để truyền thông tin, có thể tạo retention hoặc truy cập đồng thời không an toàn.

Cần tách **correlation metadata bất biến** khỏi tài nguyên mutable, xác định khi nào task hoàn tất, ai dọn context và khi nào audit/metrics ghi sự kiện. Vòng đời logical request có thể rộng hơn một lần method invocation đồng bộ.

### 13.3. Resource scope có owner

Nếu một aspect mở connection/lock/span còn aspect khác dùng hoặc đóng nó, cần hợp đồng:

- Ai tạo;
- Ai được phép dùng;
- Có thể chia sẻ qua retry/async không;
- Ai đóng trên return, throw, cancel và timeout;
- Nếu đóng lỗi, xử lý ngoại lệ gốc thế nào;
- Có cần scope riêng cho từng attempt không.

Không đưa logic “ai rảnh thì đóng” vào nhiều advice độc lập. Ownership không rõ là nguồn lỗi double-close, leak hoặc dùng resource đã đóng.

## 14. Lỗi quan sát và fail-open/fail-closed

Không phải aspect nào lỗi cũng nên phá nghiệp vụ. Nhưng quyết định phải có căn cứ:

| Chính sách | Khi bản thân aspect lỗi | Gợi ý thiết kế |
| --- | --- | --- |
| Ghi metric phụ trợ | Có thể cần giữ nghiệp vụ chạy, tùy mức quan trọng | Tách lỗi telemetry và hạn chế tác động |
| Trace/log phụ trợ | Tương tự, chú ý dữ liệu nhạy cảm | Không để logger gây lỗi ngoài ý muốn |
| Authorization bắt buộc | Không được bỏ qua kiểm tra | Fail closed ở ranh giới đáng tin |
| Audit bắt buộc theo quy định | Không thể bỏ qua mất sự kiện một cách im lặng | Durable recording/coordinator và chiến lược lỗi |
| Cache tối ưu | Có thể rơi về đường tính toán gốc nếu an toàn | Không dùng fallback để bỏ qua quyền |
| Retry | Không được tự chạy vô hạn hoặc lặp effect | Chính sách lỗi, deadline, idempotency |

“Fail-open” và “fail-closed” chỉ có ý nghĩa trong **hợp đồng cụ thể**; không mặc định tất cả logging đều là optional, hay tất cả audit có thể hoàn thành ở background mà vẫn giữ nghĩa vụ.

## 15. Kiểm thử tính độc lập và sự phối hợp

Một bộ test nên tách **kết quả nghiệp vụ** khỏi **dấu vết của aspect**.

| Kịch bản | Điều cần chứng minh |
| --- | --- |
| Chỉ Trace bật | Kết quả/ngoại lệ gốc đúng; không có lệ thuộc Metrics |
| Chỉ Metrics bật | Bộ đếm theo đơn vị đã định đúng |
| Trace và Metrics hoán đổi | Bất biến nghiệp vụ giữ nguyên; semantics đo được đánh giá theo contract |
| Authorization rồi CommitAudit | Có tín hiệu xác thực trước khi audit được phép chạy |
| Đảo Audit trước Authorization | Cấu hình sai bị phát hiện trước tác dụng phụ |
| Token không hợp lệ | Target không chạy theo đường proxy được bảo vệ |
| Cache hit | Những guard bắt buộc không bị bypass |
| Target ném ngoại lệ | Log/audit không ghi nhầm commit |
| Transaction commit thất bại | Không ghi `committed` khi chưa có xác nhận |
| Retry nhiều lần | Phân biệt request/attempt/effect; không ghi audit trùng |
| Lời gọi trực tiếp target | Phát hiện đường bypass ngoài proxy |
| Nhiều lời gọi đồng thời | State không lẫn giữa người gọi |
| Lời gọi bất đồng bộ | Dữ liệu và sự kiện completion ở đúng lifecycle |
| Aspect phụ trợ bị lỗi | Kết quả theo đúng chính sách fail-open/closed |
| Tắt aspect bắt buộc | Hệ không âm thầm tiếp tục trái invariant |
| Thay đổi precedence | Các hợp đồng thứ tự vẫn được thỏa |

### 15.1. Property quan trọng hơn một thứ tự log cố định

Đối với những aspect được xem là độc lập, kiểm thử nên tập trung **invariants**:

- Mỗi lần gọi target có tác dụng phụ đúng số lần;
- Giá trị/ngoại lệ được bảo toàn nếu chỉ quan sát;
- Không dùng dữ liệu người gọi khác;
- Không có resource còn mở sau ranh giới bắt buộc;
- Kết quả nghiệp vụ không phụ thuộc việc hoán đổi thứ tự giữa các observer khi điều kiện đã định rõ.

Đối với aspect phải phối hợp, cần test **preconditions/postconditions**, không chỉ khẳng định A in dòng log trước B.

### 15.2. Mutation test cho thứ tự

Một cách phát hiện hợp đồng thiếu là **cố ý đảo** hai interceptor hoặc tắt một interceptor trong môi trường test. Nếu hệ vẫn cho effect chạy dù bỏ Authorization bắt buộc, coverage/hợp đồng chưa đủ.

Trong ví dụ Java 17, đặt `CommittedAudit` trước `Authorization` tạo lỗi `MISORDER` trước target — một tín hiệu hữu ích, nhưng không đủ để bảo đảm các đường khác không bypass proxy.

## 16. Các sai lầm phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Mọi aspect đều được coi độc lập vì nằm ở file khác | Phụ thuộc order/state bị ẩn | Kiểm kê invariant và precedence |
| Mọi aspect đều gọi trực tiếp nhau | Coupling cao, dễ tạo dependency cycle | Dùng contract/context/coordinator |
| Cho rằng đổi thứ tự observer luôn vô hại | Metric/timing có thể đổi nghĩa | Định nghĩa unit đo và scope |
| Đặt cache ngoài authorization thiếu kiểm tra | Bypass quyền khi cache hit | Bảo vệ quyền trước mọi đường trả kết quả |
| Audit `after-returning` được coi luôn là commit | Ghi sai trạng thái nghiệp vụ | Dùng confirmed outcome và durable strategy |
| Shared static field `authorized` | Lẫn trạng thái giữa requests | Context theo invocation, nguồn tin cậy |
| Một aspect nuốt exception của aspect khác | Che lỗi bắt buộc | Hợp đồng xử lý lỗi tường minh |
| Retry vô hạn hoặc mọi exception | Tải tăng và effect trùng | Giới hạn, filter, idempotency |
| Không kiểm tra đường bypass proxy | Thiếu chính sách ở đường gọi trực tiếp | Kiểm chứng coverage và ranh giới đáng tin |
| Tách tài nguyên khỏi owner cleanup | Leak/double-close | Chủ sở hữu rõ, finally/close |
| Lưu request vào aspect singleton | Retention và lộ dữ liệu | Context ngắn hạn hoặc dữ liệu tối thiểu |
| Đo `SUCCESS` ngoài retry rồi suy một effect | Bỏ qua duplicate commit | Đếm attempts và actual effects riêng |
| Phụ thuộc thứ tự phát hiện ngẫu nhiên | Hành vi thay khi refactor/deploy | Precedence được quy định và test |
| Gộp mọi chính sách vào một aspect lớn | Khó hiểu và khó kiểm thử | Tách trách nhiệm không phụ thuộc, gom chặt những phần thật sự không thể tách |

## 17. Checklist thiết kế

Trước khi cho hai aspect áp vào một điểm:

1. Viết **mục tiêu riêng** của từng aspect bằng một câu.
2. Xác định **join point, pointcut và cơ chế weaving/proxy** thực tế.
3. Liệt kê **dữ liệu đầu vào/đầu ra**, owner và phạm vi vòng đời.
4. Phân loại quan hệ: độc lập, precedence-only, shared data, shared resource hay atomic coordination.
5. Quy định thứ tự tương đối ở **before/proceed/after/throw**, không chỉ tên aspect.
6. Kiểm tra short-circuit, retry, fallback và kết quả/ngoại lệ.
7. Với effect nhạy cảm, xác định **ranh giới tin cậy** và cách tránh đường bypass.
8. Đặt state theo invocation hoặc context có lifecycle rõ; tránh static mutable request state.
9. Xác định ai dọn tài nguyên ở đường return/throw/cancel/async.
10. Cân nhắc coordinator nếu phụ thuộc tạo vòng hoặc đòi atomicity.
11. Test các hoán vị hợp lệ và cấu hình sai, đồng thời test ngoài pointcut.
12. Đo đơn vị thích hợp: logical request, attempt, successful return, committed effect.

## 18. Bài tập vận dụng và đáp án

### Bài 1 — Độc lập hay chỉ không import nhau?

Trace và Retry không gọi API của nhau. Nhưng Trace phải đo **mỗi attempt**, không phải tổng một logical request. Có thể đặt Trace ngoài Retry tùy ý không?

**Đáp án:** Không. Chúng độc lập về mã nhưng yêu cầu đo theo từng attempt khiến Trace cần ở bên trong chuỗi Retry trong mô hình retry có `proceed` chạy lại phần đuôi, hoặc phải nhận attempt event theo hợp đồng khác.

### Bài 2 — Cache và quyền

Một Cache interceptor có thể trả ngay mà không `proceed`. Authorization nằm bên trong. Rủi ro?

**Đáp án:** Cache hit có thể bỏ qua Authorization. Nếu quyền phải được kiểm tra với mọi lần truy cập, cần bảo vệ trước cache hoặc định nghĩa một ranh giới quyền không thể bypass; key cache cũng phải có scope quyền phù hợp.

### Bài 3 — Audit và commit

Một audit aspect chỉ thấy target trả bình thường nên ghi “payment committed”. Tại sao chưa đủ?

**Đáp án:** Return bình thường không tự là bằng chứng commit; kết quả có thể biểu thị từ chối, transaction ngoài chưa commit hoặc tác vụ async chưa hoàn thành. Phải có **tín hiệu nghiệp vụ đáng tin** và ranh giới durable phù hợp.

### Bài 4 — Dependency graph

A cần chuẩn bị dữ liệu trước B; B cần chạy trước operation; C chỉ ghi metrics độc lập. Quan hệ cứng nào cần ghi?

**Đáp án:** `A.prepare → B → operation`. C có thể là module riêng nhưng vị trí đo phải được định nghĩa, ví dụ ngoài B để đếm attempts bị từ chối. Không cần thêm dependency A↔C nếu chúng không dùng dữ liệu của nhau.

### Bài 5 — Shared static field

Authorization ghi `AspectState.authorized=true`, Audit đọc static field. Hai yêu cầu song song có an toàn không?

**Đáp án:** Không. Request B có thể đọc cờ do request A đặt, dẫn tới lỗi nghiêm trọng. State phải có ownership và phạm vi per-invocation/per-request đáng tin; với bảo mật cần kiểm soát ranh giới thực sự.

### Bài 6 — Misordering

Trong ví dụ Java 17, vì sao đưa CommittedAudit lên trước Authorization khiến lời gọi bị từ chối?

**Đáp án:** CommittedAudit kiểm tra precondition `state.authorized` trước khi chuyển tiếp; Authorization chưa chạy nên state mới vẫn false. Nó ném `IllegalStateException`, chặn target và làm lộ cấu hình sai thay vì im lặng.

### Bài 7 — Lỗi từ observer

Một aspect Metrics chỉ đo thời gian nhưng backend metrics ném lỗi. Có nên mặc định cho lỗi đó đi ra client?

**Đáp án:** Tùy hợp đồng. Với metrics phụ trợ, thường cần cô lập lỗi quan sát để không thay đổi kết quả nghiệp vụ; với chính sách bắt buộc, có thể cần fail-closed. Việc xử lý phải được thiết kế có chủ đích và test.

### Bài 8 — Retry và transaction

Với `Retry(Transaction(Target))` và `Transaction(Retry(Target))`, điều gì cần xác định trước khi hoán đổi?

**Đáp án:** Transaction được mở một lần hay mỗi attempt, trạng thái transaction sau lỗi có thể tái sử dụng không, effect ngoài transaction có an toàn retry không và commit được quan sát khi nào. Không có một đáp án đúng chung.

### Bài 9 — Tách bằng coordinator

Hai aspect A và B cần quyết định chung để ghi dữ liệu bền vững nguyên tử, đồng thời mỗi aspect phụ thuộc kết quả chưa có của bên kia. Nên làm gì?

**Đáp án:** Cần xem lại ranh giới thiết kế; thường nên có một service/coordinator và giao thức commit nhất quán, không cố giải bằng thứ tự ngẫu nhiên hoặc shared mutable flags trong interceptor.

### Bài 10 — Coverage

Authorization aspect đã chạy ở các integration test thông qua proxy. Có thể khẳng định mọi đường tới target được kiểm tra quyền chưa?

**Đáp án:** Không. Các lời gọi trực tiếp target, self-invocation hoặc đường nằm ngoài phạm vi weaver/proxy có thể không qua aspect. Cần kiểm soát ranh giới tin cậy và kiểm thử các đường bypass.

## 19. Ghi nhớ

- **Aspect độc lập** là aspect có trách nhiệm và dữ liệu riêng, không cần kết quả/trạng thái của aspect khác; điều đó **không tự bảo đảm hoán đổi thứ tự vô điều kiện**.
- **Aspect phải phối hợp** khi phụ thuộc precedence, state, kết quả, lỗi, vòng đời tài nguyên, độ phủ bảo mật hoặc tính nguyên tử.
- Xác định thứ tự bằng **hợp đồng sự kiện và giai đoạn**, xét cả before/proceed/after, không chỉ liệt kê tên.
- Dùng **invocation context hoặc giao diện phối hợp rõ** thay cho static request state; tránh giữ object ngắn hạn ngoài vòng đời.
- **Short-circuit, retry, fallback và transaction** là nơi phụ thuộc giữa aspect dễ lộ rõ nhất.
- **After-returning không đồng nghĩa committed**, và proxy không chứng minh được mọi đường gọi đều được bảo vệ.
- Nếu hai chính sách cần một bất biến nguyên tử hoặc tạo dependency cycle, cân nhắc **coordinator/biên nghiệp vụ** thay vì cố gắng giải mọi thứ bằng aspect order.
- Kiểm thử **thứ tự, cấu hình sai, kết quả/ngoại lệ, số lần effect, concurrency và đường bypass** trước khi coi việc tách/ phối hợp là an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-aspect-coordination-009">Kiểm thử hành vi tổng thể thay vì chỉ từng advice</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử hành vi tổng thể thay vì chỉ từng advice

## 1. Vấn đề: mọi advice đều đúng riêng lẻ, hệ thống vẫn có thể sai

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, **pointcut** chọn *join point* có thể can thiệp và **advice** là hành vi bổ sung chạy tại điểm đã chọn. Khi nhiều aspect phối hợp trên một lời gọi, chúng tạo nên một tổ hợp có thể chứa **authorization, caching, retry, tracing, audit, metrics** hoặc xử lý ngoại lệ.

Kiểm thử riêng lẻ một hàm `authorize()` chứng minh nó có thể từ chối người dùng không được phép. Kiểm thử riêng `cache()` chứng minh nó trả lại giá trị đã lưu. Kiểm thử riêng `audit()` chứng minh nó ghi đúng sự kiện khi nhận kết quả. **Không kiểm thử nào trong ba điều đó chứng minh cấu trúc kết hợp đã an toàn**.

Xét hai chuỗi:

~~~text
ĐÚNG THEO CHÍNH SÁCH MINH HỌA:
Caller -> AUTH -> CACHE -> AUDIT -> BUSINESS

NGUY HIỂM NẾU CACHE TRẢ KẾT QUẢ TRƯỚC KHI KIỂM QUYỀN:
Caller -> CACHE -> AUTH -> AUDIT -> BUSINESS
~~~

Ở chuỗi thứ hai, **cache hit** có thể trả trực tiếp cho caller mà **không đi qua AUTH**. Mọi unit test riêng lẻ của `AUTH` và `CACHE` vẫn có thể PASS, nhưng hợp đồng end-to-end đã thất bại.

Tư duy của bài:

> Đối tượng cần kiểm thử không chỉ là từng advice, mà là **hành vi toàn bộ lời gọi qua đường can thiệp thật**, bao gồm thứ tự áp dụng, result/exception caller nhìn thấy, số lần nghiệp vụ chạy, side effects, đường bị bỏ qua và khả năng xảy ra lỗi.

Đây là chủ đề trong ngữ cảnh **phối hợp nhiều aspect**, không phải lời kêu gọi bỏ unit test. Unit test vẫn cần thiết để khoanh vùng lỗi trong từng thành phần; **integration/behavior tests bổ sung** để chứng minh các thành phần hợp tác đúng.

## 2. Phân biệt các lớp kiểm thử

| Lớp kiểm thử | Đối tượng xác nhận | Điều chưa được chứng minh |
| --- | --- | --- |
| **Unit test advice** | Logic nội bộ của một advice khi được cấp context/result giả lập | Advice có được engine gọi tại join point thật không? |
| **Pointcut matching test** | Bộ chọn match đúng/không match những signature cụ thể | Đường gọi runtime có đi qua proxy/weaver không? |
| **Integration test AOP** | Target + nhiều advice + proxy/weaving + thứ tự và kết quả | Toàn hệ thống nhiều service/external components có giống production không? |
| **End-to-end/system test** | Một workflow hoàn chỉnh qua entry point đáng tin, side effects và kết quả quan sát | Chưa chắc cô lập được lỗi từ từng advice |
| **Concurrency/stress test** | Tính ổn định khi nhiều lời gọi, retry, timeout và race | Không thay thế được các assertion ngữ nghĩa ở từng ca |
| **Deployment/compatibility test** | Artifact cuối, agent, classloader, JVM flags, policy version | Không chứng minh toàn bộ dữ liệu nghiệp vụ sản xuất |

Một test lý tưởng không cố gộp mọi việc thành một ca khổng lồ. Tạo **nhiều ca độc lập**, mỗi ca xác nhận một bất biến tổng thể, rồi thêm vài kịch bản end-to-end có ràng buộc thực tế.

### 2.1. Unit test quan trọng nhưng không đủ

Ví dụ, unit test `AuditAdvice.afterReturning("receipt:A")` có thể thành công. Nhưng khi chạy thật:

- Điểm can thiệp có thể **không khớp** method implementation.
- Caller có thể giữ **target gốc** thay vì proxy.
- Advice nằm **sau một cache short-circuit**, không chạy trên cache hit.
- Retry có thể khiến audit chạy **mỗi attempt** thay vì một lần trên toàn request.
- Advice bên trong có thể biến đổi kết quả trước khi audit đọc.
- Một advice khác ném exception sau target return, khiến caller thấy lỗi dù audit đã ghi success.

Do đó cần thử **cả đường invocation thật**, không chỉ gọi trực tiếp method của advice.

## 3. Viết “hợp đồng tổng thể” trước khi viết testcase

Trước khi kiểm thử, xác định các yêu cầu bằng câu có thể đo được. Với dịch vụ xử lý đơn hàng:

1. **Mọi caller chưa có quyền đều bị từ chối**, kể cả khi cache đã có dữ liệu.
2. **Mỗi cache miss hợp lệ** gọi target đúng một lần, nếu không có retry.
3. **Một cache hit** trả kết quả phù hợp mà không gọi lại target.
4. **Ngoại lệ nghiệp vụ** không bị một advice quan sát tự đổi thành thành công.
5. **Kết quả thất bại không được cache** nếu đó là chính sách.
6. **Audit per-execution** chỉ xảy ra khi target thật chạy; **audit per-request** có thể xảy ra dù cache hit — đó là hai hợp đồng khác nhau.
7. **Thứ tự event** phải nhất quán với precedence đã được công bố.
8. **Advice chỉ quan sát** phải giữ nguyên result, exception và số lần gọi target.
9. **Các join point không được chọn** không có side effect từ aspect.
10. **Cấu hình triển khai** phải thật sự kích hoạt đúng chain dự kiến.

Một test có thể kiểm tra **kết quả caller nhận**, **trace events** và **business execution counter**. Cả ba cung cấp những bằng chứng khác nhau. Thấy đúng kết quả nhưng target bị gọi hai lần có thể vẫn sai (ví dụ thao tác trừ tiền). Có đúng số target calls nhưng audit sinh hai events cũng có thể sai.

### 3.1. Đơn vị nào được đếm?

Phải phân biệt:

| Đơn vị | Ví dụ |
| --- | --- |
| **Logical request** | Một yêu cầu xử lý đơn hàng từ người dùng |
| **Method invocation** | Một lần gọi qua API boundary |
| **Target execution** | Một lần thân phương thức nghiệp vụ thực sự chạy |
| **Attempt** | Một lượt thử trong retry |
| **Advice entry** | Một lần một handler/advice cụ thể được vào |
| **Event emission** | Một event audit/metrics được phát đi |
| **Event delivery** | Một lần event đến consumer/sink |

`requestCount=1` không đồng nghĩa `targetExecutionCount=1` khi có retry; `auditCount=1` không đồng nghĩa `eventDeliveredCount=1` nếu queue redeliver.

Đặt tên biến test theo đúng đơn vị: `businessCalls`, `attempts`, `auditEntries`, `logicalRequests`, tránh biến `count` mơ hồ.

## 4. Điểm nguy hiểm nhất: thứ tự advice đổi semantics

Giả sử hai handler đều đúng khi đứng riêng:

~~~text
AUTH:
  nếu actor không có quyền -> ném SecurityException
  nếu có quyền -> proceed()

CACHE:
  cache hit -> trả kết quả ngay
  cache miss -> proceed() rồi lưu kết quả trả bình thường
~~~

Hai cách lắp chain:

~~~text
Safe chain:
AUTH(
    CACHE(
        BUSINESS
    )
)

Unsafe chain:
CACHE(
    AUTH(
        BUSINESS
    )
)
~~~

Với cache hit đã tồn tại, ở chuỗi unsafe, `CACHE` có thể return mà không gọi `AUTH`. Đây là một **lỗi bảo mật do composition**, không phải một bug của từng hàm handler.

### 4.1. Test phải có trạng thái chuẩn bị phù hợp

Một testcase chỉ gửi request chưa có cache entry sẽ đi qua `CACHE miss → AUTH`, làm cả hai thứ tự trông có vẻ an toàn. Để lộ lỗi, phải:

1. Dùng một actor có quyền để tạo cache entry.
2. Gửi lại cùng resource ID bằng actor không có quyền.
3. Assert request thứ hai bị từ chối **kể cả khi cache đã có giá trị**.
4. Đếm target executions và audit events để biết cache đã short-circuit ở đâu.

Đây là ví dụ của **state-dependent integration test**: trạng thái trước ca kiểm thử là một phần bắt buộc của đặc tả.

### 4.2. Có cách thiết kế cache ngoài authorization an toàn không?

Có thể trong một số hệ thống, nếu cache boundary **đã tích hợp bảo vệ tương đương**, cache key bao gồm principal/permission scope, hoặc caller không thể truy cập resource trái quyền qua entry point ấy. Nhưng không được giả định tính an toàn chỉ vì “cache hit nhanh”. Mọi cách thiết kế phải chứng minh **authorization invariant** bằng test, bao gồm thay đổi quyền, hết hạn quyền, key collision, invalidate và đa tenant.

Với bài minh họa, chọn `AUTH` ngoài `CACHE` để dễ thấy bảo đảm “mỗi request phải qua authorization trước khi được đáp ứng”.

## 5. Around advice lồng nhau: assert cả đường vào và đường rút ra

Một chuỗi `A(B(C(target)))` bình thường đi:

~~~text
A.enter
B.enter
C.enter
TARGET
C.returning
B.returning
A.returning
CALLER
~~~

Đây là **thứ tự event** với giả định mỗi layer `proceed()` đúng một lần và target return bình thường. Lớp trong hoàn tất trước lớp ngoài vì outer đang chờ `proceed`.

Khi có ngoại lệ:

~~~text
A.enter
B.enter
C.enter
TARGET throws
C.throwing
C.finally
B.throwing
B.finally
A.throwing
A.finally
CALLER sees original exception
~~~

Đây là sơ đồ khái niệm; event nào thực sự xuất hiện phụ thuộc code và loại advice. Ví dụ `after-returning` không chạy trên exceptional completion, còn `finally` chạy khi rời scope theo ngữ nghĩa Java thông thường.

**Test đúng cần khẳng định cả thứ tự và loại outcome**. Không chỉ kiểm tra “có event A và B” vì hai event đúng nhưng thứ tự sai có thể thay đổi bảo mật, caching, result transformation hoặc transaction.

### 5.1. Asserting event list

Một cách thuận tiện khi kiểm thử đơn luồng là ghi sự kiện vào `List<String> events` và so với danh sách kỳ vọng:

~~~text
expected = [
  "AUTH allow alice",
  "CACHE miss O1",
  "AUDIT before O1",
  "BUSINESS O1",
  "AUDIT success O1"
]
~~~

Không nên chỉ assert `events.size()==5`; một chain sai vẫn có thể in đúng 5 events nhưng thứ tự hoặc loại event sai. Trong môi trường đồng thời, cần gắn invocation/correlation ID để không yêu cầu **mọi request toàn hệ thống** có thứ tự tuyệt đối.

## 6. Kết quả trả về và ngoại lệ: bảo toàn hợp đồng caller

Những tình huống dễ bị bỏ sót:

- **Reference return `null`:** method có thể hoàn tất bình thường và trả `null`. Không tự coi là exceptional completion.
- **`void`:** không có result object nghiệp vụ nhưng vẫn có thể hoàn tất bình thường.
- **Primitive `int`/`boolean`:** nếu around adapter trả `null` không tương thích, việc unboxing có thể thất bại.
- **`Optional.empty()`:** một object container rỗng, không phải thiếu normal return.
- **`Result.failure(...)`:** có thể là normal Java return nhưng biểu thị thất bại miền.
- **Target exception:** advice quan sát không tự thay bằng lỗi mới, trừ khi hợp đồng ghi rõ.
- **Advice exception:** target có thể đã chạy thành công nhưng caller nhận lỗi audit/metrics; cần nhận diện nguồn.
- **Fallback:** inner advice bắt lỗi rồi trả một result hợp lệ, khiến outer advice nhìn **normal completion**.

Do đó, testcase cần assert **kiểu và thông điệp ngoại lệ** khi hợp đồng yêu cầu, không chỉ `assertThrows(Exception.class)` mơ hồ. Với logic xử lý lỗi có chủ đích, cần assert policy chuyển đổi và xác định ranh giới nào nhận fallback.

### 6.1. Những phép biến đổi result không giao hoán

Giả sử `Normalize` và `Encode` cùng là around advices. Kết quả của:

~~~text
Encode(Normalize(result))
~~~

không nhất thiết giống:

~~~text
Normalize(Encode(result))
~~~

Vì vậy test phải kiểm **giá trị cuối caller nhìn thấy**, không chỉ test từng hàm transform. Với object mutable, một advice trả cùng reference nhưng sửa fields cũng là thay đổi trạng thái.

## 7. Cache, retry và short-circuit: đếm execution chính xác

### 7.1. Cache hit

Một cache hit có thể không gọi `proceed` tới lớp trong; test phải assert:

- Có result đúng.
- Những advice **ngoài cache** vẫn có thể chạy.
- Những advice **trong cache** không chạy nếu bị short-circuit.
- Target execution count không tăng.
- Không bỏ qua một chính sách bắt buộc ngoài ý định.

Một assertion như “target được gọi ít nhất một lần” quá yếu; với cache hit lần thứ hai, kỳ vọng thường là **không tăng**.

### 7.2. Retry

Với retry có hai attempts:

~~~text
Outer timing entry: 1
Inner target executions: 2
Inner audit entries: có thể 2
Outer timing completion: 1
~~~

Điều này không nhất thiết là double weaving. Khi test cần biết **retry policy**, những loại lỗi nào được retry, tối đa bao nhiêu lần, có backoff hay không, và side effects có idempotent không.

Không test retry bằng cách mặc định mọi exception đều được thử lại: một `SecurityException` do từ chối quyền thường không nên biến thành lỗi được retry nhiều lần. Thêm test **denial does not retry**.

### 7.3. Multiple proceeds

Trong một around advice, gọi `proceed()` hai lần có thể khiến target chạy hai lần. Nếu đó không phải retry/fan-out có chủ đích, đây là lỗi nguy hiểm với thao tác có side effects.

Test không chỉ assert output cuối; phải assert **businessCalls** và những side effects có thể đo. Nếu giao dịch tạo hai bản ghi rồi trả result giống nhau, chỉ test return value sẽ bỏ sót lỗi.

### 7.4. Audit per-attempt khác audit per-request

Nếu audit phải tạo một event cho **mỗi request**, nên có test khi cache hit và khi retry. Nếu audit phải ghi **mỗi target execution**, test có thể dự kiến event bằng số attempts. Trước khi viết assertion “audit phải xuất hiện đúng một lần”, cần xác định đơn vị đo.

## 8. Ví dụ Java 17 chạy độc lập và có assertion tự động

Chương trình sau dựng một interceptor chain bằng Java thuần, **không dùng một AOP framework cụ thể và không tự nhận là bytecode weaving**. Nó giúp kiểm chứng một bài toán phối hợp nhiều aspect:

- `authorization`: chỉ actor `alice` được phép.
- `caching`: cache theo `orderId`, lưu duy nhất các kết quả return bình thường.
- `auditing`: ghi before/success/failure cho các lời gọi đi qua nó.
- `business`: thực thi logic chính, có biến đếm độc lập và nhánh exception.
- `run()`: xây chain theo thứ tự **ngoài → trong** bằng cách duyệt danh sách từ cuối.
- `expectEvents`: so sánh toàn bộ chuỗi event **theo đúng thứ tự**, không chỉ số lượng.

Các testcase bao gồm **authorized cache miss**, **authorized cache hit**, **unauthorized cache hit bị từ chối**, **business exception không bị cache**, và một test **cố ý lắp thứ tự unsafe để chứng minh lỗ hổng**. Test unsafe là một phép đối chứng trong môi trường cô lập, **không phải thiết kế sản xuất được đề xuất**.

Tạo file `AopIntegrationTestDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AopIntegrationTestDemo {
    record Request(String actor, String orderId) {}

    @FunctionalInterface
    interface Proceed {
        String call() throws Exception;
    }

    @FunctionalInterface
    interface Around {
        String apply(Request request, Proceed next) throws Exception;
    }

    @FunctionalInterface
    interface Business {
        String execute(Request request) throws Exception;
    }

    static final class Harness {
        final List<String> events = new ArrayList<>();
        final Map<String, String> cache = new HashMap<>();
        int businessCalls;

        final Around authorization = (req, next) -> {
            if (!"alice".equals(req.actor())) {
                events.add("AUTH deny " + req.actor());
                throw new SecurityException("access denied");
            }
            events.add("AUTH allow " + req.actor());
            return next.call();
        };

        final Around caching = (req, next) -> {
            String cached = cache.get(req.orderId());
            if (cached != null) {
                events.add("CACHE hit " + req.orderId());
                return cached;
            }
            events.add("CACHE miss " + req.orderId());
            String result = next.call();
            cache.put(req.orderId(), result);
            return result;
        };

        final Around auditing = (req, next) -> {
            events.add("AUDIT before " + req.orderId());
            try {
                String result = next.call();
                events.add("AUDIT success " + req.orderId());
                return result;
            } catch (Exception ex) {
                events.add("AUDIT failure " + ex.getClass().getSimpleName());
                throw ex;
            }
        };

        final Business business = req -> {
            businessCalls++;
            events.add("BUSINESS " + req.orderId());
            if ("bad".equals(req.orderId())) {
                throw new IllegalStateException("storage unavailable");
            }
            return "receipt:" + req.orderId();
        };

        String run(Request request, List<Around> outsideToInside)
                throws Exception {
            Proceed chain = () -> business.execute(request);
            for (int i = outsideToInside.size() - 1; i >= 0; i--) {
                Around layer = outsideToInside.get(i);
                Proceed inside = chain;
                chain = () -> layer.apply(request, inside);
            }
            return chain.call();
        }

        void expectEvents(String label, String... expected) {
            List<String> want = List.of(expected);
            if (!events.equals(want)) {
                throw new AssertionError(label + " expected " + want
                        + " but was " + events);
            }
            events.clear();
            System.out.println("PASS " + label);
        }
    }

    static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        Harness h = new Harness();
        List<Around> safe = List.of(h.authorization, h.caching, h.auditing);
        Request aliceA = new Request("alice", "O1");

        equal("receipt:O1", h.run(aliceA, safe));
        h.expectEvents("authorized miss",
                "AUTH allow alice", "CACHE miss O1", "AUDIT before O1",
                "BUSINESS O1", "AUDIT success O1");
        equal(1, h.businessCalls);

        equal("receipt:O1", h.run(aliceA, safe));
        h.expectEvents("authorized hit", "AUTH allow alice", "CACHE hit O1");
        equal(1, h.businessCalls);

        try {
            h.run(new Request("blocked", "O1"), safe);
            throw new AssertionError("expected SecurityException");
        } catch (SecurityException ex) {
            equal("access denied", ex.getMessage());
        }
        h.expectEvents("unauthorized blocked", "AUTH deny blocked");
        equal(1, h.businessCalls);

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                h.run(new Request("alice", "bad"), safe);
                throw new AssertionError("expected IllegalStateException");
            } catch (IllegalStateException ex) {
                equal("storage unavailable", ex.getMessage());
            }
            h.expectEvents("failure attempt " + (attempt + 1),
                    "AUTH allow alice", "CACHE miss bad",
                    "AUDIT before bad", "BUSINESS bad",
                    "AUDIT failure IllegalStateException");
        }
        equal(false, h.cache.containsKey("bad"));
        equal(3, h.businessCalls);

        List<Around> unsafe = List.of(h.caching, h.authorization, h.auditing);
        equal("receipt:O1", h.run(new Request("blocked", "O1"), unsafe));
        h.expectEvents("unsafe ordering exposed", "CACHE hit O1");
        equal(3, h.businessCalls);
        System.out.println("FINAL businessCalls=3 cachedOrders=" + h.cache.size());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AopIntegrationTestDemo.java
java AopIntegrationTestDemo
~~~

Đầu ra chính xác đã xác minh bằng chạy thực:

~~~text
PASS authorized miss
PASS authorized hit
PASS unauthorized blocked
PASS failure attempt 1
PASS failure attempt 2
PASS unsafe ordering exposed
FINAL businessCalls=3 cachedOrders=1
~~~

### 8.1. Vì sao `authorized miss` là một integration test?

Ca đầu không chỉ thử một advice. Nó thực thi toàn chuỗi `AUTH → CACHE → AUDIT → BUSINESS` rồi đồng thời xác nhận:

- Actor hợp lệ được phép.
- Cache chưa có dữ liệu nên là miss.
- Audit before chạy **trước** business.
- Business chạy một lần.
- Audit success chạy **sau** business.
- Caller nhận `receipt:O1`.

Tất cả sự kiện phải đúng thứ tự. Nếu `audit` được lắp sai vị trí, có thể kết quả caller vẫn giống nhưng event list sẽ khác và test báo lỗi.

### 8.2. `authorized hit` bảo vệ số lần thực thi thật

Ca hai gửi lại cùng `O1`, kỳ vọng:

~~~text
AUTH allow alice
CACHE hit O1
~~~

Không có `AUDIT before` hoặc `BUSINESS` do audit nằm **bên trong cache**. `businessCalls` giữ nguyên bằng `1`. Điều này đúng theo policy của demo: audit là **per-target-execution**, không phải audit mọi API request.

Nếu yêu cầu audit mỗi lần caller yêu cầu dữ liệu, phải đổi vị trí/thiết kế audit và **cập nhật expected events tương ứng**; không đánh giá logic cũ bằng một hợp đồng mới.

### 8.3. `unauthorized blocked` kể cả khi cache đã có giá trị

Ca thứ ba gửi `actor="blocked"` và cùng `orderId="O1"`. Vì `AUTH` đứng ngoài cache, lời gọi bị từ chối ngay. Test xác nhận đúng `SecurityException`, đúng thông điệp, chỉ có `AUTH deny blocked`, không có `CACHE hit`, `AUDIT` hoặc `BUSINESS`.

Đây là **hợp đồng end-to-end về bảo mật**, không thể chứng minh chỉ bằng unit test `authorization` với một `next` giả.

### 8.4. Hai lần `bad` vẫn là cache miss

Target `business` với `orderId="bad"` ném `IllegalStateException`. `AUDIT` ghi failure rồi ném lại; `CACHE` không nhận một normal result để `put`. Vì thế thử lại `bad` lần thứ hai vẫn là **CACHE miss**, và `businessCalls` từ `1` lên `3` sau hai attempts.

Điều được test là **hai lần gọi độc lập** có cùng input lỗi; đây **không phải** một retry advice tự động. Nếu ứng dụng dùng around retry, cần test policy số attempts, backoff và loại lỗi được retry riêng.

### 8.5. `unsafe ordering exposed` cố ý xác nhận một lỗi thiết kế

Ca cuối đảo thứ tự sang:

~~~text
CACHE -> AUTH -> AUDIT -> BUSINESS
~~~

Vì cache đã có `O1` từ yêu cầu trước, actor `blocked` nhận ngay `receipt:O1` mà `AUTH` **không được gọi**. Test kiểm chứng trạng thái sai một cách rõ ràng để minh họa rằng **thứ tự các advice là yêu cầu chức năng**, không chỉ yêu cầu logging.

Đoạn này là **negative demonstration** trong chương trình học. Trong hệ thống thực, kết quả “người không có quyền nhận receipt” phải khiến test bảo mật **FAIL** và pipeline triển khai bị chặn, thay vì trở thành behavior được chấp nhận.

### 8.6. Giới hạn cần hiểu

- `HashMap`, `ArrayList` và `int businessCalls` được dùng trong **test đơn luồng**. Chúng không tự thread-safe; không sao chép thành shared handler cho production đa luồng.
- `authorization` chỉ so actor với chuỗi cố định để dễ tái hiện. Hệ thống thật cần principal/permission policy đáng tin cậy; không dùng chính actor người gọi tự khai để bảo vệ tài nguyên.
- `cache` chỉ minh họa kết quả non-null và một cache key đơn giản. Với multi-tenant data, quyền truy cập và cache key phải được thiết kế cẩn thận.
- `run()` xây chain bằng Java closures. Nó **không thực hiện bytecode weaving**, không có pointcut language tổng quát, không chứng minh engine AOP thật đã can thiệp vào target.
- Không có retry advice hoặc persistence thật; mỗi event và state đều nằm trong bộ nhớ test.
- Test unsafe chỉ được chạy ở ngữ cảnh cô lập với dữ liệu giả, nhằm chứng minh hậu quả của một chuỗi có thứ tự sai.

## 9. Kiểm thử điểm khớp, điểm không khớp và đường bypass

Một test tích hợp về AOP phải xác nhận **có advice khi cần** và **không có advice khi không được chọn**.

Ví dụ:

| Tình huống | Mục tiêu assertion |
| --- | --- |
| Public method có marker được chọn | Advice chạy đúng một lần |
| Method không có marker | Không có event của advice ấy |
| Overload khác signature | Không match nhầm nếu pointcut yêu cầu signature cụ thể |
| Method trả `void` | Advice quan sát normal completion mà không đòi result object |
| Method ném exception | Advice đúng nhánh exceptional, caller nhận lỗi theo contract |
| Caller đi qua proxy | Handler được kích hoạt |
| Caller giữ raw target | Xác nhận có/không có interception theo cơ chế thật |
| `this.method()` nội bộ | Kiểm tra self-invocation của mô hình proxy/weaving cụ thể |
| Class thuộc module khác | Kiểm tra weaving/classloader scope có bao gồm không |
| Bridge/synthetic method | Không đếm duplicate logical event ngoài ý định |
| Final/private/static/constructor | Chỉ test những điểm engine thật sự hỗ trợ |

**Đừng suy rằng pointcut được parse hợp lệ đồng nghĩa advice chạy.** Có thể pointcut không match, target nằm ngoài weaving scope, proxy bị bypass hoặc class đã nạp trước transformer.

### 9.1. Độ phủ proxy khác độ phủ biến đổi mã

Một JDK interface proxy thông thường nhận những lời gọi **qua proxy interface**. Gọi thẳng target hoặc gọi nội bộ qua `this.method()` có thể bỏ qua wrapper. Một engine dệt method execution vào bytecode có thể quan sát những đường ấy nếu class/join point được hỗ trợ và instrument.

Vì vậy, integration test cần mô phỏng **đúng đường caller thật**; gọi thẳng advice method để kiểm logic không chứng minh độ phủ.

### 9.2. Negative assertions quan trọng với bảo mật

Nếu một method không được phép bypass authorization, đừng chỉ assert request có quyền hoạt động. Cần assert **mọi đường thiếu quyền bị chặn**, kể cả cache hit, callback, gọi thẳng target theo các API cho phép, và đường khởi tạo object khác.

Nếu AOP là một lớp bảo vệ bắt buộc nhưng tồn tại đường bypass không thể kiểm soát, nên củng cố enforcement tại một ranh giới tin cậy khác thay vì chỉ dựa vào kết quả một unit test.

## 10. Kịch bản kiểm thử tổng hợp theo nhiều aspect

Dưới đây là ma trận gợi ý. Không cần ép tất cả vào một test duy nhất; chọn ca nhỏ nhưng kiểm được bất biến phối hợp.

| Ca | Setup | Assertions tổng thể |
| --- | --- | --- |
| 1. Authorized cache miss | Cache rỗng | Auth trước cache, target=1, audit đúng, return đúng |
| 2. Authorized cache hit | Cache đã có | Auth vẫn chạy, target không tăng, result đúng |
| 3. Unauthorized cache hit | Đã warm cache bằng actor hợp lệ | Deny, không đọc lộ result, không target |
| 4. Business exception | Target ném lỗi | Lỗi gốc truyền về, audit failure, không lưu cache |
| 5. Retry allowed | Lỗi transient với số attempts cố định | Target count và audit per-attempt đúng |
| 6. Retry forbidden | Security/validation failure | Không retry lỗi không được phép |
| 7. Around short-circuit | Cache hit hoặc deny | Các lớp trong không chạy, lớp ngoài theo hợp đồng vẫn chạy |
| 8. Two result transforms | Target trả giá trị xác định | Caller thấy đúng composition order |
| 9. After callback failure | Sink audit ném lỗi | Caller outcome theo fail-open/fail-closed đã định |
| 10. Nested business call | Method A gọi B | Tách invocation IDs, không nhầm advice nesting |
| 11. Direct target call | Bypass proxy được kiểm soát | Coverage được phát hiện, không giả định advice chạy |
| 12. Double instrumentation | Proxy và woven advice cùng policy | Đếm event để phát hiện duplicate ngoài ý định |
| 13. Asynchronous success | Future pending rồi completed | Phân biệt method return và task completion |
| 14. Asynchronous failure | Future completes exceptionally | Completion event và lỗi đúng policy |
| 15. Concurrent cache miss | Nhiều requests một key | Không race/bypass, business calls theo cache contract |
| 16. Dynamic config change | Bật/tắt một advice | Chuyển trạng thái trong giới hạn đã định |
| 17. Deployment change | Agent/classloader khác | Đúng join point, đúng precedence trên artifact cuối |

Các expected counts nên **tham số hóa theo chính sách**, ví dụ “cache stampede được phép thực thi nhiều lần để tối ưu đơn giản” khác “single-flight chỉ chạy một lần cho mỗi key”.

## 11. Test đôi và test thật: dùng fake ở đâu?

Một **fake target** hoặc **spy** giúp tạo kết quả có thể dự đoán:

- Trả `"ok"` để thử after-returning.
- Ném `IllegalArgumentException` để thử after-throwing.
- Ghi số lần thực thi để phân biệt duplicate advice và duplicate business call.
- Trả `CompletableFuture` pending để kiểm lifecycle.
- Ghi event trace để xác minh thứ tự.

Nhưng một spy **thay thế toàn bộ weaver/proxy bằng cách gọi lời advice thủ công** sẽ bỏ qua vấn đề rất quan trọng: advice có thật sự được cài vào join point hay không?

Cách làm cân bằng:

1. Dùng **fake nghiệp vụ** để cô lập AOP logic.
2. Chạy lời gọi **qua chính proxy hoặc woven artifact sẽ dùng**.
3. Assert fake target execution count + advice events + caller outcome.
4. Có thêm một số test với **implementation thật và các dependencies quan trọng**, đặc biệt nơi có transaction, caching state hoặc external side effects.

Đừng mock mọi interceptor cùng một lúc trong integration test rồi khẳng định đã kiểm tra sự phối hợp của chúng.

### 11.1. Kiểm thử result/exception không bị spy làm sai

Nếu spy trả `null` trong khi method khai báo primitive, proxy adapter có thể gặp lỗi không có trong nghiệp vụ thật. Fake phải giữ đúng hợp đồng kiểu trả về và ngoại lệ. Nếu muốn thử edge case này thì đặt tên test rõ ràng là **hợp đồng không hợp lệ**, không dùng nó như giả lập normal path.

## 12. Trạng thái test và tính độc lập

Bộ cache, registry, static aspect state hoặc thread-local context có thể tồn tại qua nhiều test. Nếu testcase phụ thuộc thứ tự chạy ngẫu nhiên, một lần xanh không chứng minh hệ thống đúng.

Thực hành tốt:

- Mỗi test tự tạo target/chain/config và tự thiết lập cache state cần thiết.
- Nếu test cần cache hit, **chủ động warm cache** trong chính test hoặc fixture rõ ràng.
- Reset counters, event lists và subscriptions khi kết thúc một ca.
- Không sử dụng cùng mutable singleton giữa những tests chạy song song nếu không có isolation.
- Không dựa vào `Thread.sleep(20)` để chứng minh event async đã hoàn tất.
- Dùng đồng bộ hóa test có deadline và assertion có nguyên nhân khi cần.
- Ghi rõ những input cấu hình ảnh hưởng precedence và pointcut.
- Với random/property tests, giữ seed để tái hiện lỗi.

Trong ví dụ Java, `Harness` giữ state theo một phiên test có chủ đích và `events.clear()` sau mỗi assertion. Trong một bộ test thật, có thể chia thành nhiều fixtures riêng để tránh test sau phụ thuộc vào test trước, **trừ khi đang kiểm cụ thể quy trình warm-cache rồi deny**.

## 13. Tính đúng đắn về concurrency và reentrancy

Unit test đơn luồng của `AuditAdvice` không chứng minh behavior khi nhiều request xen kẽ.

Các vấn đề nên kiểm:

### 13.1. Shared mutable advice state

Một singleton-like aspect lưu `startedAt` trong field chung có thể bị hai invocation ghi đè. Nếu advice đo thời gian:

~~~java
long started = System.nanoTime();
try {
    return next.proceed();
} finally {
    metrics.observe(System.nanoTime() - started);
}
~~~

Đây là **pseudocode Java không độc lập** (`next` và `metrics` thuộc hạ tầng test/app). Local variable `started` tách theo invocation; không cần field mutable singleton.

Test: hai lời gọi đồng thời, ép xen kẽ qua barrier, xác minh mỗi duration tương ứng đúng invocation thay vì chỉ assert “có metric”.

### 13.2. Cache stampede

Hai thread cùng request cache key còn trống có thể cùng thấy miss và gọi target hai lần. Đây có thể là behavior chấp nhận được hoặc vi phạm single-flight contract. Nếu policy yêu cầu một execution, test phải kiểm **số thực thi tối đa**, không chỉ final cache value.

### 13.3. Retry cộng timeout

Timeout ngoài retry khác timeout mỗi attempt. Cần assert giới hạn thời gian/attempts theo cấu trúc thực, tránh test dựa trên clock quá nhạy. Sử dụng clock giả hoặc controlled scheduler nếu architecture hỗ trợ.

### 13.4. Reentrancy và `ThreadLocal`

Một advice có thể được gọi lồng trên cùng thread do recursion hoặc callback. State dùng chung phải phân biệt invocation depth. Nếu dùng `ThreadLocal`, cần restore/remove trong `finally` để thread pool không giữ context của request trước.

### 13.5. Race trong bật/tắt interceptor

Khi cấu hình runtime thay đổi, một invocation đang chạy có thể tiếp tục dùng policy đã được đọc ở đầu. Test cần định nghĩa **điểm tuyến tính hóa** hoặc mức consistency kỳ vọng, không mặc định mọi in-flight call đổi trạng thái tức thì.

## 14. Async: method completion không phải task completion

Một method:

~~~java
CompletableFuture<String> loadAsync() {
    return new CompletableFuture<>();
}
~~~

trả về **một Future object** mà tác vụ có thể còn pending. Sau lời gọi method, một after-returning advice có thể chạy dù task chưa xong.

Nếu audit quan tâm kết quả **tác vụ**, cần gắn với completion/cancellation:

~~~java
CompletableFuture<String> future = startAsync();
future.whenComplete((value, error) -> {
    if (error == null) recordSuccess(value);
    else recordFailure(error);
});
~~~

Hai đoạn trên là **minh họa, không phải chương trình độc lập**, vì `startAsync` và `record...` cần định nghĩa.

Trong integration tests:

1. Tạo Future chưa hoàn tất.
2. Gọi API, assert **method-return event** đã xảy ra nhưng **task-completion event** chưa xảy ra.
3. Hoàn tất Future với value, assert completion thành công theo policy.
4. Tạo Future mới và hoàn tất exceptional, assert lỗi đúng loại.
5. Kiểm cancellation, timeout và callback lỗi nếu contract yêu cầu.
6. Nếu context chuyển thread, xác minh correlation ID/permissions không bị mất.

Không dùng `join()` ngay trong after-returning để biến API async thành synchronous chỉ nhằm thuận tiện testing.

## 15. Side effects, transaction và tính idempotent

Khi business operation có side effects thật, return value thường chưa đủ làm bằng chứng.

Một around retry có thể khiến thao tác ghi DB chạy hai lần. Nếu transaction advice bọc retry khác chiều, số transaction hoặc phạm vi rollback có thể thay đổi. Nếu audit trong retry, event có thể ghi mỗi attempt.

Các ca nên kiểm:

- Số dòng hoặc aggregate domain được tạo thực tế.
- Không commit hai lần khi thao tác phải exactly-once ở tầng nghiệp vụ.
- Khi lỗi, các side effects có được rollback/compensate theo hợp đồng?
- Audit event thể hiện event miền đã commit hay attempt chưa commit?
- External calls có bị lặp khi retry?
- Cache được invalidated đúng sau write thành công?
- Advice lỗi có thể ngăn transaction completion?
- Trường hợp partial success có được biểu diễn minh bạch?

**Không thể suy ra exactly-once business semantics từ việc một advice chỉ chạy một lần.** Một message queue có thể redeliver, network client có thể retry, target có thể tự gọi dịch vụ ngoài nhiều lần. End-to-end tests phải chọn ranh giới đủ rộng theo yêu cầu thực.

## 16. Metamorphic testing: thay đổi một yếu tố, giữ các bất biến

Một kỹ thuật hữu ích là **metamorphic testing**: tạo cặp hoặc bộ kịch bản khác nhau ở một khía cạnh và assert các thuộc tính phải giữ nguyên.

### Ví dụ 1 — bật/tắt observer

Nếu metrics advice chỉ quan sát, bật hoặc tắt nó phải không đổi `businessCalls`, kết quả trả về, ngoại lệ và side effect nghiệp vụ. Chỉ metrics output được phép khác.

### Ví dụ 2 — có/không cache

Hai lời gọi hợp lệ cùng resource cần trả kết quả cùng ngữ nghĩa; khi cache có hiệu lực, số target executions có thể giảm. Nhưng authorization phải vẫn đúng trong cả hai chế độ.

### Ví dụ 3 — thay precedence có chủ đích

Với AUTH ngoài CACHE vs CACHE ngoài AUTH, một test denial sau warm cache phải có kết quả khác nếu chuỗi sai. Đây là test phát hiện **chính sách thứ tự**.

### Ví dụ 4 — thay đổi loại exception

Gửi `IllegalArgumentException` vs `IllegalStateException` từ fake target để kiểm advice retry chỉ retry những lỗi được phép và giữ nguyên lỗi không retry.

### Ví dụ 5 — thay đổi join point

Gọi method được chọn vs một overload không được chọn để bảo đảm không match quá rộng.

### Ví dụ 6 — đổi thread/scheduler

Một workflow chạy cùng dữ liệu theo hai lịch xen kẽ không được làm lẫn context hoặc trả dữ liệu của người dùng khác.

Phương pháp này phát hiện sự tương tác khó mô tả bằng một expected stdout cứng, nhưng **không loại bỏ** nhu cầu kiểm thử kết quả chính xác tại các ca cốt lõi.

## 17. Kiểm thử kiến trúc AOP thật: proxy, weaving và triển khai

Ví dụ Java ở phần 8 chỉ xây Java chain. Với một công nghệ AOP thực tế, phải thêm kiểm thử cấu trúc deployment:

| Mô hình | Điều phải xác minh |
| --- | --- |
| **Interface proxy** | Caller nhận proxy, không giữ raw target ngoài ý định |
| **Subclass proxy** | Method có thể override, final/private không bị hiểu sai |
| **Compile-time weaving** | Artifact cuối là artifact đã dệt; test chạy trên bản ấy |
| **Binary weaving** | Raw/woven output không bị trộn; không dệt lặp ngoài ý định |
| **Load-time weaving** | Transformer/agent hoạt động trước khi class mục tiêu được định nghĩa |
| **Retransformation** | Runtime/agent hỗ trợ và thay đổi trong giới hạn hợp lệ |
| **Nhiều classloaders** | Class thật được test thuộc đúng defining loader |
| **Nhiều aspect cùng match** | Precedence và số lần advice đúng contract |
| **Cấu hình theo môi trường** | Agent, JVM flags, policy version và wiring tương đương release |

Một artifact đã dệt trong build nhưng test lại dùng raw classes sẽ cho kết quả không phản ánh production. Một unit test của handler qua `new Handler().invoke(...)` cũng không kiểm tra proxy thực được cấp cho caller.

### 17.1. Một test “sentinel join point”

Có thể thiết kế một method đặc trưng, ít side effects, được chọn bởi pointcut thật. Trong integration test của bản phát hành, gọi qua đường thực và assert advice marker chạy đúng một lần. Sentinel test giúp phát hiện agent không bật, proxy không được đăng ký hoặc policy bị thiếu.

Nhưng một sentinel duy nhất không chứng minh **toàn bộ** join points đều đúng. Cần thêm tests theo nhóm pointcut và các đường âm (negative coverage).

## 18. Quan sát thất bại và quản lý thông tin test

Khi testcase fail, thông báo nên trả lời:

- **Lời gọi nào?** Method/signature, principal giả, resource ID kiểm thử.
- **Kỳ vọng gì?** Result/exception, events và counters.
- **Thực tế gì?** Diff của event list, counts và outcome.
- **Aspect nào chạy?** Identity và vị trí outer/inner.
- **Join point nào?** Call/execution nếu công cụ phân biệt.
- **Dùng artifact/config nào?** Build hash, policy version, agent/proxy settings.
- **Có async/concurrency không?** Invocation ID, thread/task ID ở mức không nhạy cảm.

Không cần ghi token thật, mật khẩu, thông tin thanh toán hoặc toàn bộ payload khách hàng để debug AOP. Với log/metrics, giới hạn số loại label tránh cardinality tăng vô hạn.

### 18.1. Ghi sự kiện đáng tin

Một event list đồng bộ trong test đơn luồng rất hữu ích. Nhưng trong production, việc ghi log có thể bất đồng bộ, được batch hoặc vận chuyển qua queue. Nếu test muốn khẳng định **advice entry count**, nên đo tại điểm entry của advice chứ không chỉ đếm dòng log ở sink, tránh nhầm với retry/redelivery.

### 18.2. Chẩn đoán thứ tự thiếu nhất quán

Nếu test thỉnh thoảng fail vì event order thay đổi, có hai khả năng: thứ tự đúng phải được bảo đảm nhưng implementation có race, hoặc test đang áp một thứ tự không hề thuộc contract. Phải xác minh trước khi “sửa” bằng cách tăng timeout hoặc tắt chạy song song.

## 19. Một quy trình kiểm thử phối hợp nhiều aspect có thể áp dụng

**Bước 1 — Vẽ sơ đồ chain thực:** viết rõ outer→inner, những đoạn có thể short-circuit, retry hoặc biến đổi result.

**Bước 2 — Viết invariant ở cấp caller:** ai được truy cập, kết quả hợp lệ, ngoại lệ, số side effects và trạng thái cuối.

**Bước 3 — Phân biệt đơn vị event:** per-request, per-attempt, per-execution, per-task-completion.

**Bước 4 — Chọn đối tượng test:** target fake đủ controllable nhưng invocation đi qua chính cơ chế AOP cần kiểm.

**Bước 5 — Tạo ca normal path:** target return thành công; assert precedence, result và counts.

**Bước 6 — Tạo ca exceptional path:** target throw, advice throw, fallback, cleanup/finally nếu có.

**Bước 7 — Tạo ca short-circuit:** cache hit, deny, circuit breaker; assert inner layers có/không chạy theo contract.

**Bước 8 — Tạo ca repeated execution:** retry, nested calls, recursion; đếm target và advice entries riêng.

**Bước 9 — Test âm về pointcut/coverage:** nonmatching method, raw target call, self-invocation, overload, classloader.

**Bước 10 — Test concurrency/async theo nhu cầu:** state isolation, lifecycle, timeout, cancellation và context propagation.

**Bước 11 — Test artifact cuối:** cùng code, policy, weaver/proxy registration và JVM flags của release.

**Bước 12 — Tạo failure report dễ hiểu:** expected/actual event list, caller outcome, counters và config.

**Bước 13 — Giữ regression suite:** bất cứ thay đổi precedence, pointcut hay engine version đều phải chạy lại các bất biến tổng thể.

Quy trình này kết hợp **độ chính xác của unit tests** với **sức phát hiện lỗi tương tác của integration tests**.

## 20. Các lỗi thường gặp khi chỉ test từng advice

1. **Test `AUTH` riêng nhưng không test cache hit của actor bị từ chối:** có thể bỏ sót authorization bypass.
2. **Test `CACHE` bằng đúng result nhưng không đếm target executions:** có thể cache không hoạt động hoặc retry vô ý.
3. **Test `AUDIT` bằng số dòng log:** dễ nhầm event phát trùng ở sink với advice chạy trùng.
4. **Không assert thứ tự:** đúng tập event nhưng sai precedence.
5. **Không test exception path:** success xanh nhưng advice có thể nuốt/đổi lỗi gốc.
6. **Không test short-circuit:** lớp trong có thể bị bỏ qua hợp lệ hoặc bỏ qua sai.
7. **Không kiểm caller result:** audit thành công nhưng return bị transformer đổi ngoài ý định.
8. **Không kiểm side effects:** target chạy hai lần dù caller nhận một kết quả.
9. **Không phân biệt call và execution join point:** có thể đếm sai khi cả hai cùng khớp.
10. **Không kiểm đường gọi trực tiếp bỏ qua proxy:** pointcut đúng nhưng AOP không hoạt động ở đường dùng thực.
11. **Giả định mọi final/private/static method được instrument như nhau:** phạm vi phụ thuộc kỹ thuật.
12. **Mock luôn proxy/weaver trong integration test:** bỏ mất điều quan trọng cần kiểm.
13. **Dùng state cache từ testcase trước:** test chạy độc lập thất bại hoặc phụ thuộc thứ tự.
14. **Coi Future return là task success:** thiếu nhánh completion exceptional/cancellation.
15. **Dùng shared `ArrayList` trong test concurrency:** làm hỏng event collection và tạo false failures.
16. **Bắt `Exception` chung chung:** có thể chấp nhận lỗi sai loại, che regression.
17. **Đếm advice entries như đếm business requests:** sai khi retry, recursion, cache và short-circuit.
18. **Chỉ test happy path:** không phát hiện denial, timeout hoặc dọn dẹp khi lỗi.
19. **Thay policy mà không cập nhật expected invariants:** test xanh có thể phản ánh hợp đồng cũ.
20. **Bỏ qua artifact/config thực tế:** test trên raw code không chứng minh woven/proxy production behavior.
21. **Cho rằng hai log giống nhau là double weaving:** có thể do hai join points hoặc delivery.
22. **Cố làm mọi assertion trong một test dài:** khó cô lập nguyên nhân và tái hiện lỗi.
23. **Không kiểm negative coverage:** advice vô tình chặn thêm method không nên chặn.
24. **Không phân biệt business success với normal method return:** một `Result.failure` vẫn có thể trả bình thường.
25. **Bỏ qua classloader/agent startup:** LTW có thể không chạy dù cấu hình pointcut hợp lệ.

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Unit test của `AUTH` và `CACHE` đều PASS. Vì sao người không có quyền vẫn có thể nhận dữ liệu?  
**Trả lời:** Nếu cache đứng ngoài auth, cache hit có thể short-circuit trước bước kiểm quyền. Phải test chuỗi tổng thể sau khi cache đã được warm.

**Câu 2.** Trong chuỗi `AUTH→CACHE→AUDIT→BUSINESS`, khi cache hit, audit có chạy không?  
**Trả lời:** Trong ví dụ, không. Audit nằm bên trong cache và cache hit không gọi `proceed`.

**Câu 3.** Hai lần gọi `bad` cùng throw, business count tăng hai; có chắc retry advice không?  
**Trả lời:** Không. Có thể là hai lời gọi độc lập, như ví dụ. Cần biết invocation/attempt IDs.

**Câu 4.** Vì sao test vừa assert return value vừa assert business count?  
**Trả lời:** Return đúng không bảo đảm target chỉ chạy đúng số lần; side effects có thể lặp.

**Câu 5.** Target ném lỗi nhưng một inner fallback trả kết quả, outer after-returning có thể chạy không?  
**Trả lời:** Có. Outer thấy normal return ở ranh giới của nó dù target trước đó thất bại.

**Câu 6.** Một unit test gọi `audit.afterReturning()` trực tiếp có chứng minh weaving hoạt động không?  
**Trả lời:** Không. Cần integration test gọi qua đúng proxy/woven join point.

**Câu 7.** Vì sao “audit đúng một lần” là yêu cầu chưa đủ rõ?  
**Trả lời:** Cần xác định đúng một lần trên mỗi request, attempt, target execution hay task completion.

**Câu 8.** Test chỉ chạy lúc cache rỗng có phát hiện lỗi auth bị bypass khi cache hit không?  
**Trả lời:** Không chắc. Phải chủ động tạo trạng thái cache hit với actor khác quyền.

**Câu 9.** Tại sao một điểm `after-returning` không đủ chứng minh async task đã thành công?  
**Trả lời:** Method có thể chỉ trả một Future pending; task completion xảy ra sau đó.

**Câu 10.** Proxy bị caller bỏ qua thì test handler trực tiếp có phát hiện không?  
**Trả lời:** Không; test phải dùng object graph/đường caller thật.

**Câu 11.** Khi hai advice đều đúng riêng nhưng đổi thứ tự làm data leak, lỗi nằm ở đâu?  
**Trả lời:** Ở **composition/precedence** và việc thiếu bảo đảm invariant ở toàn workflow.

**Câu 12.** Cần gì ngoài event list để kiểm một action có side effects?  
**Trả lời:** Result/exception caller nhìn thấy, số lần target thật chạy và trạng thái domain/external side effects sau lời gọi.

## 22. Tổng kết

**Kiểm thử nhiều aspect phải đánh giá cả hành vi tổng thể, không chỉ cộng kết quả unit test của từng advice.** Thứ tự outer/inner, `proceed()`, cache hit, retry, fallback, exception, async completion và ranh giới proxy/weaving có thể thay đổi **những gì caller nhìn thấy, những gì target thực thi và những gì hệ thống ghi nhận**.

Hãy bắt đầu bằng các invariant ở cấp workflow, tạo test vừa xác minh **outcome, event order, business execution count, side effects và negative coverage**, rồi thử cả các nhánh normal, exceptional, short-circuit và nhiều attempts. Dùng fake để dễ kích thích edge cases, nhưng vẫn cần gọi qua **cơ chế AOP thực sự** trong integration test. Cuối cùng, chạy tests trên cấu hình và artifact gần với release thật để phát hiện khác biệt từ precedence, classloader, agent, proxy wiring hoặc bytecode weaving. Mục tiêu không phải có thật nhiều test của từng aspect riêng biệt, mà là **chứng minh các aspect phối hợp bảo toàn đúng hợp đồng nghiệp vụ và an toàn khi tương tác**.

</details>

- [Quay lại đầu trang](#back-to-top)
