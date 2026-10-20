<a id="back-to-top"></a>

# Điểm can thiệp

## Menu
- [Phân biệt vị trí gọi và vị trí thực thi phương thức](#join-points-001)
- [Điểm can thiệp khi khởi tạo đối tượng và thực thi hàm tạo](#join-points-002)
- [Đọc và ghi thuộc tính khi mô hình AOP hỗ trợ](#join-points-003)
- [Khởi tạo lớp, khởi tạo đối tượng và thứ tự thực thi](#join-points-004)
- [Điểm xử lý ngoại lệ và các giới hạn theo công cụ](#join-points-005)
- [Các điểm liên quan khóa hoặc đồng bộ khi được hỗ trợ](#join-points-006)
- [Thông tin tĩnh và ngữ cảnh động của một join point](#join-points-007)
- [Phân biệt điểm trong mã nguồn với điểm trong luồng thực thi](#join-points-008)
- [Những thao tác không thuộc mô hình join point của công cụ đang dùng](#join-points-009)

## <a id="join-points-001">Phân biệt vị trí gọi và vị trí thực thi phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt vị trí gọi và vị trí thực thi phương thức trong AOP

Trong lập trình hướng khía cạnh (*Aspect-Oriented Programming*, AOP), **điểm can thiệp** (*join point*) là một vị trí hoặc sự kiện trong quá trình thực thi mà cơ chế AOP có thể gắn hành vi bổ sung. Hai loại điểm can thiệp liên quan đến phương thức rất dễ bị nhầm:

- **Method call join point (điểm gọi phương thức):** gắn với **nơi một lời gọi được phát ra**, nhìn từ phía mã đang gọi (*caller*).
- **Method execution join point (điểm thực thi phương thức):** gắn với **việc phương thức đích thực sự bắt đầu chạy**, nhìn từ phía phương thức được gọi (*callee*).

Chúng có liên hệ vì một lời gọi bình thường thường dẫn đến một lần thực thi phương thức, nhưng **không phải cùng một ranh giới**. Ranh giới khác nhau dẫn tới phạm vi pointcut, ngữ cảnh quan sát, thứ tự advice và những lời gọi được bao phủ khác nhau.

> **Phạm vi bài:** Giải thích AOP nói chung, từ trực giác cơ bản đến những trường hợp nâng cao về đa hình, ngoại lệ, đệ quy và kỹ thuật weaving. Các tên `call` và `execution` bên dưới biểu diễn *loại join point*; cú pháp pointcut chính xác và tập join point được hỗ trợ phụ thuộc công cụ AOP.

## 1. Trực giác qua một lời gọi đơn giản

Giả sử có hai phương thức Java:

```java
class InvoiceService {
    void issueInvoice() {
        System.out.println("Issue invoice");
    }
}

class InvoiceController {
    void submit(InvoiceService service) {
        service.issueInvoice(); // [A] Vị trí gọi
    }
}
```

Đoạn này là mã minh họa, **không phải chương trình độc lập có `main`**.

- **[A]** nằm ở `InvoiceController.submit(...)`: đây là *call site*, nơi biểu thức `service.issueInvoice()` phát sinh yêu cầu gọi phương thức.
- Khi việc chuyển điều khiển thành công, JVM bắt đầu chạy thân `InvoiceService.issueInvoice()`: đây là *execution entry*, ranh giới vào phương thức đích.
- Khi phương thức kết thúc, luồng điều khiển quay về phía biểu thức gọi ở [A] (trừ trường hợp luồng thực thi bị kết thúc bất thường ở mức runtime).

Nếu đặt advice ở phía **call**, ta đang quan tâm đến **một lần đi qua vị trí phát sinh lời gọi**. Nếu đặt advice ở phía **execution**, ta quan tâm đến **một lần phương thức đích được thực thi**.

### Hình dung luồng điều khiển

```text
CALLER: InvoiceController.submit()       CALLEE: InvoiceService.issueInvoice()
----------------------------------       ------------------------------------
Trước biểu thức gọi
    |
    +-- [method CALL join point]
    |             |
    |             +------------------> [method EXECUTION join point]
    |                                     |
    |                                     +--> thân issueInvoice()
    |                                     |
    |             <------------------ rời execution (return/throw)
    |
    +-- rời call (return/throw)
    |
Tiếp tục trong submit() hoặc xử lý lỗi
```

Sơ đồ diễn tả trường hợp lời gọi đồng bộ thông thường. Một **around advice tại call** có thể bao quanh cả bước chuyển tới phương thức đích; một **around advice tại execution** bao quanh phần thực thi đích. Các khối advice thực tế được chèn bằng cơ chế weaving/interception của công cụ nên không nhất thiết tương ứng với một frame Java có tên rõ ràng.

## 2. Bảng so sánh hai loại join point

| Góc nhìn | Method call | Method execution |
| --- | --- | --- |
| Vị trí logic | Biểu thức/vị trí phát sinh lệnh gọi trong caller | Ranh giới vào phần thực thi phương thức đích |
| Bên sở hữu ngữ cảnh | Phía gọi; tại sao và ở đâu phát sinh lời gọi | Phía được gọi; phương thức nào thực sự chạy |
| Số vị trí tĩnh | Cùng một phương thức đích có thể được gọi từ nhiều call site | Một thân phương thức có thể phục vụ nhiều caller |
| Đa hình | Call site có thể khai báo qua interface/kiểu cha | Body thực thi phụ thuộc dispatch runtime |
| Điều kiện chọn | Có thể quan tâm tới nguồn gọi hoặc chữ ký được gọi | Có thể quan tâm tới phương thức/loại thực thi |
| Ngữ cảnh caller | Thường dễ liên hệ với vị trí gọi | Không mặc nhiên biết một caller tĩnh duy nhất |
| Ngữ cảnh callee | Có thể quan sát đối tượng được gọi, tùy công cụ | Gắn trực tiếp với target đang thực thi |
| Lời gọi nội bộ | Mỗi biểu thức gọi nội bộ có thể là call join point nếu công cụ weave được | Method execution có thể xảy ra dù lời gọi đến từ trong cùng đối tượng |
| Chiến lược triển khai | Cần hỗ trợ quan sát/chèn ở call site | Cần hỗ trợ quan sát/chèn ở execution entry |
| Dùng khi nào | Chính sách gắn với **nơi phát lời gọi** | Chính sách gắn với **mọi lần chạy của đích** |
| Tính phổ quát | Không phải engine AOP nào cũng hỗ trợ | Cũng không phải engine nào bao phủ mọi execution |

**Lưu ý quan trọng:** “Có call join point” không nhất thiết đồng nghĩa advice chỉ chạy một lần. Một call site nằm trong vòng lặp có thể phát sinh rất nhiều sự kiện gọi ở thời gian chạy. Tương tự, “có một thân phương thức” không có nghĩa nó chỉ có một sự kiện execution: mỗi lần thực thi là một sự kiện riêng.

## 3. Call site là gì, và vì sao nó thuộc phía caller?

**Call site** là một vị trí trong mã mà chương trình thực hiện lời gọi. Ví dụ:

```java
// Hai vị trí gọi tĩnh khác nhau.
class Checkout {
    void confirm(Billing billing) {
        billing.charge("ORDER-1");     // call site #1
    }
}

class BatchJob {
    void bill(Billing billing) {
        billing.charge("ORDER-2");     // call site #2
    }
}

interface Billing {
    void charge(String orderId);
}
```

Mã trên chỉ nhằm nhận diện vị trí gọi và **không biên dịch độc lập vì chưa có implementation/cấu hình chạy**.

Hai biểu thức `billing.charge(...)` thuộc hai caller khác nhau. Nếu một chính sách AOP chọn **vị trí gọi từ `Checkout`**, nó có thể áp dụng ở call site #1 nhưng không áp dụng ở #2, **ngay cả khi hai lời gọi đi tới cùng một implementation**. Ngược lại, nếu chính sách chọn *mọi lần thực thi* `BillingImpl.charge(...)`, nó có thể quan sát cả hai lời gọi vì cùng phương thức đích đã chạy.

### Một call site, nhiều lượt gọi

```java
for (String id : orderIds) {
    billing.charge(id); // Một vị trí tĩnh, nhiều sự kiện gọi động
}
```

Pointcut theo call site có thể khớp vị trí này và advice thực thi mỗi lần chương trình đi qua nó. Đừng đánh đồng **số vị trí trong source** với **số lần advice được kích hoạt**.

### Nhiều call site, một implementation

```text
Checkout.confirm() ---- call #1 -----+
                                     +--> BillingImpl.charge() execution
BatchJob.bill() ------- call #2 -----+
```

Hai call site tĩnh khác nhau có thể dẫn đến hai lần execution khác nhau của cùng phương thức. Nếu muốn chính sách gắn với *mọi đường đi vào một implementation*, execution thường là góc nhìn phù hợp hơn.

## 4. Execution là gì, và vì sao thuộc phía callee?

**Method execution join point** đại diện cho lần thực thi một phương thức đích. Khi một lệnh gọi chọn được implementation cụ thể và đi vào thân phương thức đó, phương thức thực hiện công việc của nó. Advice tại execution được xem là gắn với ranh giới chạy của phương thức.

Ví dụ:

```java
class BillingImpl implements Billing {
    @Override
    public void charge(String orderId) {
        validate(orderId);
        debit(orderId);
    }

    private void validate(String orderId) {
        // Kiểm tra dữ liệu.
    }

    private void debit(String orderId) {
        // Cập nhật trạng thái thanh toán.
    }
}
```

Đây là **mã minh họa**, không phải tệp độc lập; giả định interface `Billing` đã tồn tại.

Nếu công cụ hỗ trợ execution join point của `charge` và lựa chọn đúng phương thức đó, advice có thể chạy khi `charge` được thực thi bất kể caller xuất phát từ `Checkout`, `BatchJob` hay lớp khác.

Điều này đặc biệt hữu ích cho các concern gắn với **hợp đồng hành vi của phương thức**: đo thời gian thực thi một API, theo dõi tổng số lần xử lý, kiểm toán thao tác nghiệp vụ tại ranh giới thực thi, hoặc thực thi một guard trước khi thân phương thức chạy.

Tuy nhiên, nếu yêu cầu là “chỉ audit khi được gọi từ `Checkout`”, chỉ chọn execution của `charge` thường chưa đủ vì execution đó có thể được kích hoạt từ nhiều caller. Việc truy tìm caller qua stack là một kỹ thuật khác, có giới hạn riêng và không biến execution thành call site.

## 5. Thứ tự when call và execution cùng được can thiệp

Giả sử một cơ chế AOP đủ khả năng weave advice cho **cả** call site và execution entry của cùng một lời gọi đồng bộ. Nếu mỗi advice bao quanh và đều gọi tiếp đúng một lần, thứ tự khái niệm có thể là:

```text
call.before
    execution.before
        target.method() body
    execution.after-returning
call.after-returning
```

Khi target ném ngoại lệ:

```text
call.before
    execution.before
        target.method() throws
    execution.after-throwing
call.after-throwing
```

Với xử lý dạng `finally`, các bước dọn dẹp ở phía execution chạy khi rời execution và các bước tương ứng ở phía call chạy khi rời call. **Đây là một kịch bản minh họa theo nesting tự nhiên**, không phải cam kết một thứ tự chuẩn cho mọi hệ thống AOP. Nhiều aspect cùng khớp một join point còn có quy tắc precedence riêng.

### Call advice có thể thấy điều execution advice không thấy?

Trong một số mô hình weaving tại call site, advice bao quanh phần phát lời gọi nên có thể quan sát các lỗi xảy ra trước khi target thực sự được vào, chẳng hạn một lời gọi gặp đối tượng nhận `null`. Nhưng **chi tiết thời điểm đánh giá receiver, đối số và thời điểm bắt đầu join point phụ thuộc ngữ nghĩa ngôn ngữ cùng cơ chế weaving**; không thể khẳng định mọi công cụ sẽ bắt được cùng một lỗi ở cùng vị trí.

Nếu chưa vào được thân phương thức, **không có lần thực thi thân phương thức ấy** để execution advice quan sát. Do đó, không nên cho rằng một lỗi nhìn thấy ở caller luôn chứng minh target đã chạy.

## 6. Ví dụ Java 17+ chạy được: hai vị trí gọi, hai implementation

Ví dụ này cố tình đặt các dòng log ở **phía caller** và **đầu/cuối thân phương thức đích** để làm lộ hai ranh giới. Những dòng log có tiền tố `CALL`/`EXEC` là **mô phỏng thủ công vị trí quan sát**, không phải advice được tự động weave. Ví dụ dùng Java chuẩn, nên chạy độc lập mà không cần thư viện AOP.

Lưu tệp **`CallVsExecutionDemo.java`** bằng UTF-8:

```java
public class CallVsExecutionDemo {
    interface Formatter {
        String format(String text);
    }

    static final class UpperFormatter implements Formatter {
        @Override
        public String format(String text) {
            System.out.println("EXEC enter UpperFormatter.format");
            try {
                if ("fail".equals(text)) {
                    throw new IllegalArgumentException("invalid text");
                }
                String result = text.toUpperCase(java.util.Locale.ROOT);
                System.out.println("EXEC return UpperFormatter.format");
                return result;
            } catch (RuntimeException error) {
                System.out.println("EXEC throw UpperFormatter.format");
                throw error;
            } finally {
                System.out.println("EXEC finally UpperFormatter.format");
            }
        }
    }

    static final class BracketFormatter implements Formatter {
        @Override
        public String format(String text) {
            System.out.println("EXEC enter BracketFormatter.format");
            String result = "[" + text + "]";
            System.out.println("EXEC return BracketFormatter.format");
            return result;
        }
    }

    // Call site #1: phát lời gọi từ useInTitle().
    static String useInTitle(Formatter formatter) {
        System.out.println("CALL before useInTitle");
        try {
            String result = formatter.format("alpha");
            System.out.println("CALL return useInTitle");
            return result;
        } finally {
            System.out.println("CALL finally useInTitle");
        }
    }

    // Call site #2: duy nhất một biểu thức gọi, nhận nhiều
    // implementation runtime khác nhau qua tham số Formatter.
    static String useInReport(Formatter formatter, String value) {
        System.out.println("CALL before useInReport");
        try {
            String result = formatter.format(value);
            System.out.println("CALL return useInReport");
            return result;
        } catch (RuntimeException error) {
            System.out.println("CALL throw useInReport");
            throw error;
        } finally {
            System.out.println("CALL finally useInReport");
        }
    }

    public static void main(String[] args) {
        Formatter upper = new UpperFormatter();
        Formatter bracket = new BracketFormatter();

        System.out.println("RESULT " + useInTitle(upper));
        System.out.println("RESULT " + useInReport(upper, "beta"));
        System.out.println("RESULT " + useInReport(bracket, "gamma"));

        try {
            useInReport(upper, "fail");
        } catch (IllegalArgumentException error) {
            System.out.println("HANDLED " + error.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CallVsExecutionDemo.java
java CallVsExecutionDemo
```

**Đầu ra chính xác** (không tính những thông báo riêng của môi trường terminal):

```text
CALL before useInTitle
EXEC enter UpperFormatter.format
EXEC return UpperFormatter.format
EXEC finally UpperFormatter.format
CALL return useInTitle
CALL finally useInTitle
RESULT ALPHA
CALL before useInReport
EXEC enter UpperFormatter.format
EXEC return UpperFormatter.format
EXEC finally UpperFormatter.format
CALL return useInReport
CALL finally useInReport
RESULT BETA
CALL before useInReport
EXEC enter BracketFormatter.format
EXEC return BracketFormatter.format
CALL return useInReport
CALL finally useInReport
RESULT [gamma]
CALL before useInReport
EXEC enter UpperFormatter.format
EXEC throw UpperFormatter.format
EXEC finally UpperFormatter.format
CALL throw useInReport
CALL finally useInReport
HANDLED invalid text
```

### Phân tích kết quả

**Lần 1 — `useInTitle(upper)`:** lời gọi xảy ra ở call site #1. Việc dispatch đưa chương trình tới implementation `UpperFormatter.format`. Dòng `EXEC enter` chạy sau `CALL before`.

**Lần 2 — `useInReport(upper, "beta")`:** call site #2 khác #1 nhưng lại dẫn tới cùng phương thức `UpperFormatter.format`. Đây là bằng chứng một execution target có thể nhận lời gọi từ nhiều vị trí.

**Lần 3 — `useInReport(bracket, "gamma")`:** vẫn ở call site #2, nhưng runtime target đổi thành `BracketFormatter.format`. Đây là bằng chứng một call site có thể đưa đến nhiều implementation tùy đối tượng tại thời điểm chạy.

**Lần 4 — `useInReport(upper, "fail")`:** target ném `IllegalArgumentException`. Execution chạy phần xử lý lỗi và `finally` trước khi caller nhận ngoại lệ rồi chạy phần `catch/finally` của mình. Kết quả không có dòng `EXEC return` hay `CALL return` cho lần này.

**Giới hạn của ví dụ:** việc chèn thủ công `System.out.println(...)` không tạo ra join point AOP thật. Trong AOP, pointcut/advice nằm ngoài phương thức đích và được công cụ kết hợp vào vị trí phù hợp. Ví dụ làm rõ **ranh giới quan sát và thứ tự dòng điều khiển**, sau đó ta mới chọn đúng cơ chế can thiệp khi triển khai.

## 7. Đa hình: chữ ký lời gọi và phương thức thực chạy không luôn trùng nhau

Đây là một trong những khác biệt quan trọng nhất.

```java
interface Notifier {
    void send(String text);
}

class EmailNotifier implements Notifier {
    @Override
    public void send(String text) {
        System.out.println("email:" + text);
    }
}

class ConsoleNotifier implements Notifier {
    @Override
    public void send(String text) {
        System.out.println("console:" + text);
    }
}

void dispatch(Notifier notifier) {
    notifier.send("Hi");
}
```

Mã là **minh họa ý tưởng** đặt trong một phạm vi phù hợp, không phải tệp Java độc lập.

Ở caller, biểu thức `notifier.send("Hi")` được viết thông qua kiểu khai báo `Notifier`. Runtime quyết định implementation nào thực thi, dựa trên đối tượng thực tế. Vì vậy:

- Một **pointcut theo method call** có thể chọn lệnh gọi nhìn từ kiểu/interface `Notifier` hoặc thông tin call site mà cơ chế hỗ trợ.
- Một **pointcut theo method execution** có thể chọn execution của `EmailNotifier.send` và loại trừ `ConsoleNotifier.send`, nếu cơ chế hỗ trợ nhận diện execution tương ứng.
- Điều kiện khớp dựa trên **chữ ký khai báo tĩnh** và **loại đối tượng runtime** có thể cho kết quả khác nhau.

Đừng đồng nhất “method được nhắc trong source” với “implementation đã chạy”. Với **overriding**, dynamic dispatch có thể chọn override; với **overloading**, trình biên dịch lựa chọn overload dựa trên kiểu/đối số ở compile time, rồi quy tắc dispatch tương ứng được áp dụng. Khi viết pointcut, phải biết đang khớp *mẫu chữ ký nào* và cơ chế có xét runtime target hay không.

### Phương thức được thừa kế và interface

Trong thiết kế đối tượng, caller có thể biết một interface nhưng thân phương thức nằm ở class triển khai hoặc class cha. Nếu chỉ chọn execution theo một kiểu cụ thể, có thể bỏ lọt phương thức được kế thừa/override hoặc phương thức mặc định (*default method*) tùy cách công cụ ánh xạ chữ ký và bytecode. Đừng suy luận pointcut chỉ dựa vào tên hiển thị trong IDE; hãy kiểm thử những implementation và đường gọi thực tế.

## 8. Phân biệt khi có nhiều caller, wrapper và chuỗi lời gọi

Một luồng đồng bộ có thể gồm nhiều cấp:

```text
UI.submit()
  -> Controller.placeOrder()    [call ở UI]
       -> Service.reserve()     [call ở Controller]
            -> Database.save()  [call ở Service]
```

Ở đây có ít nhất ba vị trí gọi khác nhau và ba lần execution có thể quan sát, tùy loại join point và phạm vi weaving. Một aspect chọn **call từ `Controller` vào `Service`** có ý nghĩa “chính sách tại ranh giới tầng caller này”. Một aspect chọn **execution của `Service.reserve`** có nghĩa “mỗi lần service này thực hiện nghiệp vụ”, bất kể ai gọi nó.

Nếu thêm một client khác, chẳng hạn batch job gọi trực tiếp `Service.reserve`, pointcut dựa trên *call từ Controller* có thể bỏ qua lượt batch. Pointcut execution ở service có thể vẫn áp dụng. Chọn loại join point phải xuất phát từ **ý nghĩa chính sách** chứ không chỉ vì cú pháp biểu thức ngắn.

### Call-side concern và execution-side concern

- **Call-side:** giám sát mọi lời gọi từ một thành phần sang một API ngoài, thu thập thống kê phụ thuộc, giới hạn hoạt động do một caller phát ra hoặc áp dụng policy ở boundary caller cụ thể.
- **Execution-side:** đo tổng thời gian của một phương thức đích, audit mọi lần thực thi một hành vi, gắn dữ liệu trace cho một nhóm implementation, hoặc kiểm tra bất biến ở ranh giới method body.

Đây là những ví dụ định hướng thiết kế, không khẳng định tất cả công cụ AOP đều hỗ trợ mọi tiêu chí pointcut tương ứng.

## 9. Lời gọi nội bộ trong cùng đối tượng (*self-invocation*)

Giả sử:

```java
class PriceCalculator {
    void calculate() {
        normalize();
    }

    void normalize() {
        // Công việc nội bộ.
    }
}
```

Có một biểu thức gọi `normalize()` bên trong `calculate()`; việc vào thân `normalize()` là một sự kiện execution. Nếu một công cụ có khả năng **weave trực tiếp cả call site nội bộ và execution entry** trong bytecode, về nguyên tắc hai loại điểm can thiệp có thể được nhận diện.

Nhưng nếu chương trình dùng **proxy đứng ngoài đối tượng**, lời gọi `calculate()` đi qua proxy không khiến `this.normalize()` trong target tự động quay ra proxy. Khi đó lời gọi nội bộ có thể **không bị interception**, dù `normalize()` vẫn thực thi bình thường.

Phải phân biệt:

1. **Bản chất join point:** mã nội bộ vẫn có một lời gọi và một execution.
2. **Khả năng quan sát của công cụ:** proxy, weaving lúc biên dịch và weaving bytecode có phạm vi can thiệp khác nhau.
3. **Đường đi runtime:** việc lời gọi thực sự đi qua lớp trung gian hay không quyết định advice của interceptor có chạy hay không.

Kết luận “self-invocation luôn bỏ qua AOP” là quá rộng. Nó đúng với những chiến lược can thiệp *phụ thuộc hoàn toàn vào đường gọi qua proxy*; không phải định luật cho mọi kỹ thuật AOP.

## 10. Đệ quy và số lượng join point động

Với phương thức đệ quy:

```java
static int factorial(int n) {
    return n <= 1 ? 1 : n * factorial(n - 1);
}
```

Có một **call site tự gọi** bên trong thân phương thức, nhưng khi chạy `factorial(4)`, chương trình vào thân `factorial` với `n = 4, 3, 2, 1`: bốn lần execution. Call site đệ quy bên trong phương thức chỉ được đánh giá ở các lượt `n = 4, 3, 2`, tức ba lần phát sinh lời gọi đệ quy; ngoài ra còn có lời gọi ban đầu từ nơi bắt đầu `factorial(4)`.

Hai hệ quả:

- Advice tại **execution** của `factorial` có thể được kích hoạt cho từng lượt vào thân nếu cơ chế bao phủ cả bốn lần.
- Advice tại **call site đệ quy** có thể kích hoạt cho ba lượt tự gọi; lời gọi đầu tiên đến `factorial(4)` thuộc một call site khác.

Nếu advice dùng around rồi **gọi tiếp nhiều lần**, lượng execution có thể tăng so với logic ban đầu. Nếu advice của một join point tự tạo lời gọi khớp chính pointcut, có nguy cơ reentrancy/đệ quy không chủ ý; cần giới hạn phạm vi và thiết kế guard.

### Lưu ý về số lượng

Không phải quy tắc “một call event bằng đúng một execution event” luôn đúng. Một advice ở call join point có thể chặn không gọi đích, thay kết quả hoặc thử lại theo chính sách; lỗi cũng có thể phát sinh trước khi vào thân phương thức. Tương tự, một execution join point có thể bắt nguồn từ reflection hoặc các cơ chế triệu gọi không có call site Java dạng thông thường mà source code của bạn dễ chỉ ra. **Số lần advice thực tế phải kiểm chứng theo ngữ nghĩa cơ chế đang dùng.**

## 11. Nếu ngoại lệ xảy ra trước khi vào thân phương thức thì sao?

Khi đọc log, rất dễ suy luận nhầm “caller đã thực hiện lời gọi” thành “callee đã bắt đầu chạy”.

Xét:

```java
InvoiceService service = null;
service.issueInvoice();
```

Lệnh ở caller là một **biểu thức gọi** về mặt cú pháp, nhưng quá trình thực hiện sẽ ném `NullPointerException` vì receiver null, **không chạy thân `InvoiceService.issueInvoice()`**. Một cơ chế được weave tại call site có thể có điểm quan sát khác với cơ chế được weave tại execution entry; thứ tự evaluation và cách chèn advice quyết định điều gì quan sát được.

Một tình huống khác:

```java
service.issueInvoice(buildInvoiceData());
```

Nếu `buildInvoiceData()` ném ngoại lệ trong quá trình đánh giá đối số, lời gọi đến `issueInvoice(...)` không hoàn tất bước chuẩn bị và thân của nó không bắt đầu chạy. Nếu cần theo dõi chính xác “đã thử gọi API” so với “API thật sự được vào”, phải định nghĩa rõ ranh giới đo lường và kiểm thử ngoại lệ xảy ra ở từng phía.

Các trường hợp như lỗi truy cập class, linkage error, reflection hoặc native call cũng cần phân tích theo ngữ nghĩa của JVM và cơ chế AOP cụ thể; không coi tất cả lỗi quanh biểu thức gọi là ngoại lệ do thân target ném ra.

## 12. Khi dùng proxy/interceptor, ta đang quan sát ranh giới nào?

Một **runtime proxy** nhận lời gọi từ caller thay cho target, chạy logic bổ sung rồi chuyển tiếp. Nó thường quan sát **lời gọi đi qua proxy** và có thể thiết lập một ranh giới interception hữu ích. Tuy nhiên, không nên đồng nhất điều đó một cách tự động với *mọi method call join point ở mọi nơi* hoặc *mọi execution join point trong bytecode target*.

```text
caller -> proxy (interception) -> target.method()   [đi qua trung gian]
caller -----------------------> target.method()   [đi thẳng]
target.method() -> this.helper()                    [gọi nội bộ]
```

Chỉ đường đầu tiên chắc chắn đi qua chính proxy trong sơ đồ. Hai đường còn lại có thể bỏ qua interception ở proxy. Nếu muốn chọn các **call site tĩnh** từ caller cụ thể, proxy dùng chung trước target thường không tự phân biệt được chính xác từng vị trí source call. Nếu muốn mọi **execution** kể cả self-invocation, đôi khi cần kỹ thuật weaving khác.

Trong Java, `java.lang.reflect.Proxy` dựa trên interface và chuyển các lời gọi trên proxy tới `InvocationHandler`. Đây là cơ chế interceptor chuẩn để học về chain advice, nhưng **không phải bằng chứng rằng Java SE tự hỗ trợ đầy đủ pointcut `call`/`execution`**. Hai loại join point ấy thuộc mô hình AOP và chỉ hiện hữu như điểm có thể weave khi công cụ có cơ chế hỗ trợ tương ứng.

## 13. Weaving ảnh hưởng đến việc quan sát call/execution

| Cơ chế | Góc nhìn thực tế | Khả năng/giới hạn cần lưu ý |
| --- | --- | --- |
| **Compile-time weaving** | Biến đổi chương trình khi biên dịch | Có thể can thiệp call site hoặc execution entry nếu công cụ hỗ trợ và mã cần weave nằm trong phạm vi build. |
| **Post-compile weaving** | Biến đổi bytecode đã biên dịch | Cần bảo đảm artifact đúng đã được biến đổi; call site nằm ở caller có thể cần weave cả caller. |
| **Load-time weaving** | Biến đổi class khi được JVM nạp | Class loader, thứ tự nạp và danh sách class được weave ảnh hưởng phạm vi can thiệp. |
| **Runtime proxy/interception** | Quan sát lời gọi qua đối tượng trung gian | Không tự động phủ mọi call site hay lời gọi nội bộ. |

Điểm đặc biệt quan trọng là **mã sở hữu vị trí can thiệp**:

- Muốn can thiệp **call site**, hệ thống có thể phải instrument/weave phần mã caller nơi chứa lệnh gọi.
- Muốn can thiệp **execution entry**, hệ thống có thể cần instrument/weave class định nghĩa thân phương thức.
- Nếu chỉ kiểm soát một bên (ví dụ thư viện caller hoặc target) thì tính khả thi của điểm can thiệp có thể rất khác nhau.

Ví dụ, một ứng dụng không sở hữu bytecode của một số caller có thể gặp giới hạn khi muốn weave call site trong caller ấy. Mặt khác, instrument execution của một API do mình sở hữu có thể dễ quản lý hơn vì nó tập trung tại implementation. Đây là hệ quả về **vị trí cần chèn mã**, không phải cam kết rằng một kỹ thuật nhất định luôn hỗ trợ mọi loại pointcut.

## 14. Ngữ cảnh `this`, `target` và caller/callee

Một số mô hình AOP cung cấp khả năng bind đối tượng *hiện tại* và *đối tượng đích* của join point. Tên như `this` và `target` có thể xuất hiện trong ngôn ngữ pointcut, nhưng ý nghĩa chính xác phụ thuộc loại join point và công cụ.

Với mô hình call/execution định hướng đối tượng cổ điển:

- Tại **method call**, *caller-side this* có thể là đối tượng đang thực thi mã phát lời gọi (nếu call site nằm trong một phương thức instance); *target* là receiver mà lời gọi hướng đến.
- Tại **method execution**, đối tượng đang thực thi thân method và receiver đích thường là cùng instance của callee trong lời gọi instance thông thường.
- Với **phương thức static**, không có receiver instance theo nghĩa của lời gọi instance. Không mặc định `this`/`target` luôn tồn tại hay luôn mang đối tượng cùng kiểu.
- Với **proxy**, những khái niệm đối tượng proxy, target thực và runtime receiver cần được kiểm tra theo API cụ thể; không áp đặt ánh xạ chung cho mọi công cụ.

Những khác biệt đó giải thích vì sao pointcut theo **caller type** thường cần vị trí call, còn pointcut theo **implementation type** thường tự nhiên hơn ở execution. Tuy vậy, nhiều hệ thống có thêm khả năng suy diễn/giới hạn theo ngữ cảnh runtime, nên đây là định hướng lựa chọn, không phải quy tắc bất biến.

## 15. Reflection, method handles và lời gọi gián tiếp

Một phương thức có thể được thực thi mà source code caller không chứa biểu thức trực tiếp `target.method()`.

Ví dụ reflection:

```java
java.lang.reflect.Method method =
        InvoiceService.class.getDeclaredMethod("issueInvoice");
method.invoke(service);
```

Đây là **đoạn minh họa đặt trong phạm vi có biến `service`, có thể ném checked exceptions**.

Biểu thức Java trực tiếp được viết ở đây là `method.invoke(service)`. Bên trong cơ chế reflection, runtime có thể dẫn đến việc thân `InvoiceService.issueInvoice` thực sự chạy. Một cơ chế **execution weaving** trên thân `issueInvoice` có thể quan sát lần thực thi ấy. Một pointcut **call** tại biểu thức source `service.issueInvoice()` ban đầu thì không thể giả định đã khớp vì biểu thức đó không tồn tại tại vị trí reflection trên.

Các API như method handles, proxy hay callback có đường gọi riêng. Khi cần bảo đảm thu thập mọi lượt chạy của một implementation, hãy xác nhận execution weaving có bao phủ cả các cách triệu gọi gián tiếp đang dùng. Không suy luận rằng mọi lệnh `Method.invoke` đều được coi như một static call join point của phương thức đích theo cùng quy tắc.

## 16. Bất đồng bộ: return của lời gọi chưa chắc là kết thúc công việc

Ví dụ khái niệm:

```java
java.util.concurrent.CompletableFuture<String> result =
        java.util.concurrent.CompletableFuture.supplyAsync(
                () -> expensiveCalculation()
        );
```

Đây là **mã minh họa**, giả định có `expensiveCalculation()` trả về `String`.

Lời gọi `supplyAsync(...)` ở caller có thể trả về một `CompletableFuture` trước khi computation thực tế xong. Việc công việc chạy trên luồng khác là một execution khác, với call/execution boundary khác. Do đó:

- Đo thời gian quanh **call tới hàm khởi tạo tác vụ** thường đo thời gian **gửi/lên lịch**, không đo trọn thời gian xử lý bất đồng bộ.
- Quan sát **execution của task** có thể đo thời gian chạy task, nếu cơ chế có thể can thiệp đúng execution ấy.
- Muốn đo **end-to-end latency**, thường cần ghép ngữ cảnh giữa thời điểm nhận/gửi yêu cầu và thời điểm tác vụ hoàn tất.
- Không mặc định `ThreadLocal` của caller tự truyền nguyên vẹn qua executor hoặc callback bất đồng bộ.

Điều này cho thấy phân biệt call/execution không chỉ là hai vị trí code: nó giúp tránh đặt metric, timeout hoặc tracing ở **sai ranh giới thời gian**.

## 17. Lựa chọn pointcut phù hợp với yêu cầu thực tế

Hãy đặt câu hỏi về **đối tượng chịu trách nhiệm** và **phạm vi chính sách**.

| Yêu cầu | Hướng phù hợp hơn | Vì sao |
| --- | --- | --- |
| “Ghi tất cả lời gọi do `Checkout` gửi tới API thanh toán” | Call join point tại caller tương ứng | Quan tâm đến nguồn phát lời gọi. |
| “Đo mỗi lần `ChargeService.charge` thật sự thực thi” | Execution join point ở callee | Quan tâm đến mọi lượt chạy của target. |
| “Theo dõi mỗi lần một module gọi thư viện bên ngoài” | Call join point nếu có thể weave caller | Nguồn lời gọi là yếu tố chính của policy. |
| “Áp dụng audit cho mọi lần thực thi hàm phát hành dữ liệu” | Execution join point nếu hỗ trợ đầy đủ | Chính sách gắn với hành vi đích. |
| “Chỉ đo thời gian queue một tác vụ async” | Call quanh API submit/schedule | Mục tiêu là độ trễ gửi tác vụ. |
| “Đo thời gian xử lý thật của tác vụ async” | Execution của task hoặc quan sát completion | Mục tiêu là công việc hoàn tất. |
| “Chặn mọi đường đi vào một operation, kể cả gọi nội bộ” | Xác minh execution weaving hoặc cơ chế bảo vệ ở chính target | Proxy ở một đầu vào có thể bỏ qua lời gọi nội bộ. |

Bảng chỉ ra **hướng lựa chọn ngữ nghĩa**, chưa đủ để kết luận có thể triển khai; công cụ cụ thể phải cung cấp điểm can thiệp và khả năng bind context cần thiết.

### Ba câu hỏi trước khi viết pointcut

1. **Chính sách thuộc phía nào?** Nơi caller quyết định gọi, hay hành vi callee phải thực hiện?
2. **Cần bao phủ những đường đi nào?** Caller khác, reflection, lời gọi nội bộ, đa hình, callback, async?
3. **Cơ chế can thiệp nằm ở đâu?** Có weave được caller không, có weave được implementation không, hay chỉ có proxy?

Nếu không trả lời được ba câu này, một pointcut khớp đúng ở bản demo vẫn có thể bỏ lọt các trường hợp thực tế.

## 18. Kiểm thử để tránh nhầm hai loại điểm can thiệp

Một bộ kiểm thử AOP chất lượng phải có **ca khớp** và **ca không khớp**, cũng như phân biệt số sự kiện call/execution.

### Ma trận kiểm thử quan trọng

| Tình huống | Điều phải chứng minh |
| --- | --- |
| Hai caller khác nhau cùng gọi một implementation | Call advice theo caller chỉ khớp nơi định nghĩa; execution advice ở đích có thể khớp cả hai. |
| Một call site nhận hai implementation | Loại execution thực tế có thể khác nhau do dynamic dispatch. |
| Caller gọi trực tiếp target hoặc đi qua proxy | Những đường bypass proxy có được bao phủ không? |
| Phương thức tự gọi phương thức khác trên `this` | Hệ thống có hỗ trợ interception nội bộ hay execution weaving không? |
| Target ném ngoại lệ | Advice return/throw/finally nằm đúng ranh giới, không đổi lỗi trái contract. |
| Receiver null hoặc lỗi đánh giá đối số | Target đã vào thân chưa; call instrumentation đo được giai đoạn nào? |
| Reflection/callback | Có execution được nhìn thấy dù không có direct source call tương ứng không? |
| Nhiều luồng và async | Metric/context có theo đúng lần chạy không? |
| Đệ quy | Advice áp dụng theo *mỗi sự kiện động*, không phải mỗi dòng source. |
| Nhiều aspect cùng khớp | Thứ tự advice và precedence có xác định và được test không? |

Có thể thêm **counter** cho call và execution advice rồi chạy các đường thử trên. Nhưng cần diễn giải counter đúng: một around advice có quyền bỏ qua hoặc gọi lại `proceed`, nên số lần quan sát có thể khác nhau một cách hợp lệ theo contract. Trong hệ thống quan sát thuần túy, nên bảo đảm advice không vô tình lặp thao tác.

### Kiểm tra hai chiều

- **False negative:** Những lượt gọi/thực thi cần được theo dõi nhưng không kích hoạt advice.
- **False positive:** Những lượt gọi/thực thi không thuộc chính sách nhưng vẫn kích hoạt advice.

Test chỉ xác minh rằng “aspect có chạy” thường không phát hiện được hai lỗi này. Hãy kiểm tra cả tập điểm **được chọn** và **bị loại**.

## 19. Các lỗi tư duy thường gặp

1. **“Một call site bằng một execution.”** Một vị trí gọi có thể được thực hiện nhiều lần; một lần lời gọi có thể không vào được thân đích; advice có thể chặn/retry tùy contract.
2. **“Call và execution chỉ khác tên.”** Chúng thuộc hai phía của ranh giới chuyển điều khiển, vì vậy ngữ cảnh và tập can thiệp khác nhau.
3. **“Chọn call của interface chính là chọn execution của implementation.”** Đa hình cho phép một call site dẫn đến nhiều implementation.
4. **“Một implementation chỉ có một caller.”** Nhiều class và nhiều vị trí có thể gọi cùng một method.
5. **“Self-invocation không tạo join point.”** Về mặt mô hình nó vẫn có call và execution; giới hạn nằm ở cơ chế interception đang dùng.
6. **“Proxy tự động hỗ trợ mọi loại pointcut.”** Proxy nhìn thấy lời gọi qua nó; method-call weaving tại mọi call site và method-execution weaving là hai khả năng khác.
7. **“Có stack trace caller nên execution cũng tương đương call.”** Stack có thể chịu ảnh hưởng reflection, tail behavior, inlining, async và hạ tầng runtime; truy dấu stack không thay đổi bản chất join point.
8. **“Lời gọi trả về nghĩa là công việc async đã xong.”** API gửi tác vụ có thể return trước khi task thực sự hoàn tất.
9. **“Mọi exception nhìn thấy ở call đều do target ném.”** Receiver null hoặc lỗi đánh giá đối số có thể khiến thân đích không hề được vào.
10. **“Pointcut đúng khi build là đủ.”** Phải kiểm chứng đường gọi runtime, tập class thực sự được weave và thứ tự advice.

## 20. Bài tập củng cố

1. **Đổi implementation:** Trong `CallVsExecutionDemo`, đổi đối số của `useInTitle` từ `upper` sang `bracket`. Call site nào giữ nguyên, execution target nào thay đổi?
2. **Tạo caller mới:** Viết một hàm thứ ba gọi `Formatter.format`. Nếu chỉ chọn call site của `useInReport`, lời gọi mới có thuộc phạm vi không? Nếu chọn execution `UpperFormatter.format` thì sao?
3. **Kiểm tra lời gọi nội bộ:** Thêm `UpperFormatter.normalize` và gọi nó bên trong `format`. Phân biệt sự tồn tại của join point với khả năng bắt được lời gọi ấy bằng proxy.
4. **Thử ngoại lệ:** Dùng `useInReport(upper, "fail")` để liệt kê chính xác thứ tự các sự kiện ở hai ranh giới; giải thích vì sao không có “return”.
5. **Phân tích đệ quy:** Với `factorial(4)`, đếm số lần vào execution và số lần đi qua call site đệ quy, chưa kể nơi gọi ban đầu.
6. **Thiết kế metric:** Muốn đo thời gian thực thi công việc async thật sự, tại sao chỉ đo quanh lời gọi `supplyAsync(...)` là chưa đủ?
7. **Thiết kế pointcut:** Hãy nêu một chính sách thực tế đòi hỏi ngữ cảnh caller và một chính sách khác chỉ cần biết callee; giải thích vì sao không nên dùng cùng một loại join point cho cả hai.

**Tự kiểm tra:** Nếu hai caller gọi cùng một method, call join point có giống nhau không? Nếu một caller gọi qua interface và runtime chọn hai implementation, execution có giống nhau không? Một method có thể chạy qua reflection mà không có biểu thức gọi trực tiếp trong source không? Lỗi xảy ra trước method entry có được tính là execution của target không?

## Kết luận

**Method call** trả lời câu hỏi **“lời gọi xuất phát ở đâu?”**, còn **method execution** trả lời **“phương thức nào thực sự chạy?”**. Chúng nối tiếp nhau trong lời gọi đồng bộ đơn giản nhưng phân biệt rõ khi có nhiều caller, dynamic dispatch, lời gọi nội bộ, reflection, lỗi trước method entry và bất đồng bộ. Chọn loại join point theo mục tiêu chính sách, sau đó kiểm chứng giới hạn weaving/interception thực tế, sẽ giúp aspect tác động **đúng vị trí, đủ phạm vi và đúng ngữ nghĩa**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-002">Điểm can thiệp khi khởi tạo đối tượng và thực thi hàm tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Điểm can thiệp khi khởi tạo đối tượng và thực thi hàm tạo trong AOP

> **Mục tiêu học tập:** Phân biệt điểm can thiệp tại **nơi yêu cầu tạo đối tượng** với điểm can thiệp **khi hàm tạo được thực thi**; hiểu mối quan hệ giữa các join point này và vòng đời khởi tạo đối tượng; nhận biết phạm vi của pointcut/advice, thứ tự thực thi, xử lý ngoại lệ và các trường hợp không thể chặn bằng một cơ chế AOP nhất định.

## 1. Vì sao quá trình tạo đối tượng cũng có thể là điểm can thiệp?

Khi nói đến AOP (Aspect-Oriented Programming), người học thường hình dung advice chạy trước hoặc sau một phương thức nghiệp vụ. Tuy nhiên, việc tạo đối tượng cũng có những ranh giới thực thi đáng quan sát: chương trình yêu cầu tạo một đối tượng, một constructor được gọi, các bước khởi tạo diễn ra và đối tượng có thể được trả về cho nơi gọi.

Ví dụ một ứng dụng muốn thu thập số lần tạo đối tượng thuộc nhóm cần theo dõi, ghi vết các lần tạo thất bại, đo chi phí khởi tạo hoặc xác minh một chính sách tại ranh giới tạo tài nguyên. Nếu cùng một quy tắc được chèn thủ công vào nhiều nơi viết `new` hoặc nhiều constructor, mã dễ bị phân tán và đan xen với nghiệp vụ. Mô hình AOP cho phép mô tả **join point** liên quan đến những hoạt động này khi cơ chế được dùng có hỗ trợ.

Cần tránh kết luận rằng cứ có `new` là mọi AOP đều chặn được. Tập loại join point và vị trí advice được chạy **phụ thuộc mô hình AOP và kỹ thuật weaving**. Bài học này giải thích ngữ nghĩa chung, có ví dụ Java 17 quan sát vòng đời, và phân biệt rõ mã Java minh họa với weaving thực sự.

## 2. Các thuật ngữ cần tách bạch

- **Đối tượng (object):** một thể hiện của lớp có trạng thái và danh tính riêng, được cấp phát và khởi tạo theo quy tắc ngôn ngữ/runtime.
- **Hàm tạo (constructor):** đơn vị khởi tạo gắn với việc thiết lập một thể hiện mới; trong Java, constructor không có kiểu trả về và không phải một phương thức thông thường.
- **Class instance creation expression:** biểu thức như `new Order(...)` ở **nơi gọi**.
- **Constructor invocation:** hành động yêu cầu chạy một constructor; trong Java còn có lời gọi chuyển tiếp `this(...)` hoặc `super(...)` bên trong constructor.
- **Constructor execution:** quá trình thực hiện logic khởi tạo của một constructor cụ thể.
- **Join point:** vị trí/hoạt động trong mô hình thực thi mà AOP có thể nhận diện và gắn hành vi, nếu được hỗ trợ.
- **Pointcut:** điều kiện lựa chọn tập join point cần can thiệp.
- **Advice:** hành vi bổ sung chạy trước, sau hoặc bao quanh join point theo mô hình tương ứng.
- **Weaving:** cách kết hợp hành vi aspect vào chương trình hoặc luồng thực thi.

**“Khởi tạo đối tượng”** theo nghĩa rộng có thể bao trùm nhiều bước hơn **“chạy thân constructor”**. Tùy ngôn ngữ AOP, các điểm như *constructor call*, *constructor execution* và *object initialization* có thể được coi là những loại join point khác nhau, có phạm vi và thời điểm khác nhau.

## 3. Điểm can thiệp tại nơi gọi hàm tạo: constructor call

Xét Java:

```java
Order order = new Order("A-100");
```

Trong nhiều mô hình AOP, ta có thể quan tâm đến join point **constructor call**: vị trí mã yêu cầu tạo một `Order`. Đó là góc nhìn **từ phía nơi gọi**.

Một pointcut khái niệm có thể mô tả:

```text
Chọn những nơi gọi constructor của Order với đối số định dạng hợp lệ.
```

Nếu cơ chế hỗ trợ advice ở constructor-call join point, chính sách có thể bao quanh thao tác tạo ở một số call site nhất định:

```text
Caller
  |
  |  trước yêu cầu tạo
  v
  new Order(...)
  |
  |  sau khi biểu thức new hoàn tất bình thường
  v
  Caller nhận tham chiếu Order
```

**Những điều cần hiểu:**

1. **Ranh giới call site:** đây là điểm trong luồng của **bên yêu cầu tạo đối tượng**. Nó có thể dùng để đo tổng thời gian của biểu thức tạo đối tượng (tùy cách advice đo và chỗ được chèn).
2. **Không đồng nghĩa với mọi đối tượng được cấp phát:** một pointcut dựa trên vị trí gọi cụ thể hoặc cấu trúc `new` chỉ bao phủ các call site mà nó khớp và cơ chế có thể can thiệp.
3. **Không đồng nghĩa với thân constructor:** một biểu thức `new Child(...)` có thể kéo theo chuỗi thực thi constructor ở lớp cha và lớp con.
4. **Kết quả thành công và thất bại khác nhau:** nếu quá trình tạo ném ngoại lệ, bên gọi **không nhận được tham chiếu mới theo nhánh hoàn tất bình thường**; advice chỉ chạy “sau khi thành công” sẽ không được chạy trong nhánh lỗi đó.

Điểm quan trọng là **nơi gọi** và **nơi định nghĩa constructor** là hai vị trí khác nhau trong chương trình. Một constructor có thể được gọi từ nhiều nơi; từng call site có thể được lựa chọn theo các quy tắc khác nhau khi cơ chế AOP hỗ trợ.

## 4. Điểm can thiệp khi thực thi hàm tạo: constructor execution

Giả sử lớp có constructor:

```java
final class Order {
    private final String id;

    Order(String id) {
        this.id = id;
    }
}
```

Ở đây **constructor execution** nói đến điểm thực thi của constructor `Order(String)`. Đây là góc nhìn **từ phía phần tử đang được thực hiện**.

Một pointcut có thể diễn đạt:

```text
Chọn việc thực thi constructor Order(String).
```

Với những cơ chế hỗ trợ join point loại này, mọi đường tạo đi tới đúng constructor đó có thể là ứng viên, kể cả khi đến từ nhiều call site; song **mức độ bao phủ thực tế phải xác minh bằng kỹ thuật weaving**, không thể suy từ biểu thức pointcut đơn lẻ.

**Không tự suy rằng advice ở “trước constructor execution” chạy trước khi mọi phần của đối tượng được khởi tạo.** Trong Java, việc gọi constructor lớp cha, chạy khởi tạo trường và khối khởi tạo của lớp diễn ra theo trình tự do ngôn ngữ quy định. Một mô hình AOP có thể định nghĩa vùng constructor execution riêng so với vùng object initialization, và bị ràng buộc bởi quy tắc an toàn của JVM về tham chiếu `this` chưa khởi tạo. Thời điểm advice chính xác phải được kiểm tra theo ngữ nghĩa cơ chế được dùng.

## 5. So sánh các loại điểm can thiệp

| Góc nhìn | Constructor call | Constructor execution | Object initialization (nếu mô hình hỗ trợ riêng) |
|---|---|---|---|
| Trọng tâm | **Nơi yêu cầu** tạo đối tượng | **Việc thực thi** một constructor | **Quá trình khởi tạo** một thể hiện |
| Vị trí khái niệm | Phía caller/call site | Phía constructor đích | Ranh giới vòng đời khởi tạo được mô hình định nghĩa |
| Ví dụ mô tả | “Chọn những chỗ gọi `new Order(...)`” | “Chọn chạy `Order(String)`” | “Chọn giai đoạn khởi tạo `Order`” |
| Nhiều nơi tạo dùng cùng constructor | Có thể là nhiều call join point | Cùng định nghĩa constructor được thực thi nhiều lần | Liên quan từng quá trình khởi tạo thể hiện |
| Với kế thừa / `this(...)` | Cần xác định chính xác nơi/lời gọi được mô hình xem là join point | Có thể có nhiều constructor execution trong chuỗi | Cần xác định ngữ nghĩa giai đoạn khởi tạo |
| Kết quả sau khi thành công | Bên gọi nhận kết quả của biểu thức tạo | Constructor cụ thể kết thúc bình thường | Quá trình khởi tạo hoàn tất theo phạm vi định nghĩa |

Cột cuối không phải một “loại join point bắt buộc” của mọi AOP. Các mô hình khác nhau có thể định nghĩa ít loại hơn hoặc tổ chức chúng theo cách khác.

### 5.1. Constructor call và method call không giống nhau

Trong Java, `new Order(...)` là biểu thức tạo thể hiện và gọi constructor. `order.submit()` là lời gọi phương thức thông thường. Một công cụ chỉ có cơ chế chặn **lời gọi phương thức qua đối tượng trung gian** không đương nhiên nhìn thấy biểu thức `new`.

### 5.2. Constructor execution và method execution không giống nhau

Constructor không kế thừa theo cơ chế override như phương thức instance; `new Child(...)` được ràng buộc với constructor phù hợp theo quy tắc ngôn ngữ. Vì vậy, các giải thích về dispatch động của phương thức không thể bê nguyên sang constructor execution.

## 6. Trình tự khởi tạo đối tượng trong Java: phải hiểu trước khi gắn advice

Một biểu thức `new Child()` thông thường không chỉ chạy vài câu lệnh trong thân `Child()`. JVM/JLS còn điều phối việc cấp phát, giá trị mặc định, chuyển qua constructor lớp cha, các trường và khối khởi tạo, rồi mới hoàn tất.

Với đối tượng Java thông thường, có thể hình dung:

```text
Bắt đầu biểu thức tạo đối tượng (new Child(...))
  |
  |  đánh giá các đối số theo quy tắc ngôn ngữ
  |  cấp phát thể hiện, trường có giá trị mặc định ban đầu
  v
Chạy chuỗi constructor theo kế thừa / chuyển tiếp
  |
  |  constructor cha và quá trình khởi tạo cha
  |  khởi tạo trường/instance initializer của lớp con ở bước thích hợp
  |  chạy thân các constructor liên quan
  v
Hoàn tất bình thường → biểu thức new trả về tham chiếu
Hoặc ném ngoại lệ → biểu thức new không trả tham chiếu theo nhánh thành công
```

Sơ đồ cố ý chỉ thể hiện **thứ tự tương đối**; các bước chính xác với constructor `this(...)`, `super(...)`, lớp lồng và kế thừa phải tuân theo Java Language Specification. Không nên đồng nhất “thực thi thân constructor” với toàn bộ thời gian từ đánh giá đối số đến khi tham chiếu được trả về.

### 6.1. Ví dụ Java 17 chạy được: kế thừa và chuyển tiếp constructor

Lưu tệp **`ConstructionOrderDemo.java`**:

```java
public class ConstructionOrderDemo {
    static class Base {
        int baseValue = initializeBase();

        Base() {
            System.out.println("Base constructor body");
        }

        private int initializeBase() {
            System.out.println("Base field initializer");
            return 1;
        }
    }

    static class Child extends Base {
        int childValue = initializeChild();

        Child() {
            this("standard");
            System.out.println("Child() body");
        }

        Child(String label) {
            super();
            System.out.println("Child(String) body: " + label);
        }

        private int initializeChild() {
            System.out.println("Child field initializer");
            return 2;
        }
    }

    public static void main(String[] args) {
        System.out.println("Before new Child()");
        Child child = new Child();
        System.out.println("After new: " + child.baseValue + ", " + child.childValue);
    }
}
```

Biên dịch và chạy:

```bash
javac ConstructionOrderDemo.java
java ConstructionOrderDemo
```

**Stdout chính xác:**

```text
Before new Child()
Base field initializer
Base constructor body
Child field initializer
Child(String) body: standard
Child() body
After new: 1, 2
```

**Giải thích:**

- `main` in `Before new Child()` trước khi gọi biểu thức `new Child()`.
- Constructor `Child()` chuyển tiếp qua `this("standard")`; `Child(String)` gọi `super()`.
- Trường của `Base` được khởi tạo, sau đó thân `Base()` chạy.
- Phần khởi tạo trường của `Child` diễn ra **một lần** trong chuỗi constructor hợp lệ, không lặp lại chỉ vì có `this(...)`.
- `Child(String)` in thông báo, rồi quay về thân `Child()`.
- Chỉ sau khi toàn bộ chuỗi hoàn tất, `main` in `After new: 1, 2`.

**Bài học AOP:** Một biểu thức `new Child()` ở phía caller tạo ra **một lần tạo thể hiện** nhưng liên quan đến việc thực thi **nhiều constructor** (`Base()`, `Child(String)`, `Child()`) trong chuỗi khởi tạo. Nếu một chính sách dựa trên constructor execution được áp dụng ở nhiều mức, số lần advice xuất hiện có thể khác dự đoán “mỗi `new` chỉ có một constructor”.

Đây là quan sát Java thuần; code không tự thực hiện weaving. Mốc advice thực tế phụ thuộc join point mà mô hình AOP định nghĩa.

## 7. Minh họa hai ranh giới: bao bên ngoài nơi gọi và quan sát bên trong constructor

Để quan sát dễ hơn, ví dụ sau dùng **Java thuần**:

- Hàm `atCreationSite(...)` là một wrapper ở **bên ngoài** biểu thức `new`, mô phỏng hành vi bao quanh một vị trí gọi tạo đối tượng.
- Những câu lệnh `[constructor] ...` được viết **bên trong** constructor để mô phỏng quan sát tại phần thân constructor.

**Đây là mô hình trực quan phục vụ học tập, không phải một hệ AOP tự weaving.** Trong hệ AOP thực, một aspect có thể gắn advice bằng weaving theo loại join point được hỗ trợ.

Lưu tệp **`ConstructorBoundaryDemo.java`**:

```java
import java.util.function.Supplier;

public class ConstructorBoundaryDemo {
    static final class Ticket {
        private static int nextId;
        final int id;

        Ticket(boolean reject) {
            System.out.println("[constructor] enter");
            try {
                if (reject) {
                    System.out.println("[constructor] reject");
                    throw new IllegalArgumentException("invalid");
                }
                id = ++nextId;
                System.out.println("[constructor] assigned " + id);
            } finally {
                System.out.println("[constructor] exit");
            }
        }
    }

    static <T> T atCreationSite(Supplier<T> constructorCall) {
        System.out.println("[site] before");
        try {
            T value = constructorCall.get();
            System.out.println("[site] success");
            return value;
        } catch (RuntimeException ex) {
            System.out.println("[site] failure: " + ex.getMessage());
            throw ex;
        }
    }

    public static void main(String[] args) {
        try {
            Ticket first = atCreationSite(() -> new Ticket(false));
            System.out.println("Ticket ready: " + first.id);

            atCreationSite(() -> new Ticket(true));
        } catch (IllegalArgumentException ex) {
            System.out.println("Caller caught: " + ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac ConstructorBoundaryDemo.java
java ConstructorBoundaryDemo
```

**Stdout chính xác:**

```text
[site] before
[constructor] enter
[constructor] assigned 1
[constructor] exit
[site] success
Ticket ready: 1
[site] before
[constructor] enter
[constructor] reject
[constructor] exit
[site] failure: invalid
Caller caught: invalid
```

### 7.1. Có thể rút ra điều gì?

**Lần tạo thành công:** Wrapper ở nơi gọi ghi `[site] before`, constructor thực hiện phần bên trong, sau đó wrapper mới nhận kết quả và ghi `[site] success`. Hai điểm quan sát phục vụ **hai ranh giới khác nhau**.

**Lần tạo thất bại:** Constructor ném `IllegalArgumentException`, nhưng `finally` trong thân constructor vẫn chạy sau khi phần `try` đã bắt đầu; wrapper bắt ngoại lệ và ghi `[site] failure`. Phần `Ticket` không được trả về theo nhánh thành công.

**Hợp đồng của dấu hiệu “after”:** `[constructor] exit` trong `finally` chỉ báo rằng chương trình thoát phạm vi `try` thuộc thân constructor, **không bảo đảm khởi tạo thành công**. Nếu cần thống kê số đối tượng tạo thành công, phải ghi tại nhánh thực sự hoàn tất thành công.

### 7.2. Giới hạn của ví dụ

`atCreationSite` chỉ có tác dụng khi **nơi gọi chủ động dùng wrapper**. Nó không tự chặn mọi `new Ticket(...)` khác. Những lệnh in trong constructor là mã được viết thủ công, không thể coi là advice tự động. Đặc biệt, `try/finally` ở thân constructor **không quan sát được** lỗi xảy ra trước khi thân đó được chạy, chẳng hạn trong chuỗi gọi constructor lớp cha hoặc quá trình khởi tạo trường; vì vậy không được tuyên bố ví dụ đã đo toàn bộ vòng đời khởi tạo.

Ví dụ có `final int id`. Nếu constructor ném ngoại lệ trước khi gán `id`, Java không yêu cầu nhánh ném đó hoàn tất khởi tạo như nhánh trả về bình thường. Nhánh thành công luôn gán giá trị cho `id` trước khi constructor kết thúc.

## 8. Pointcut cho khởi tạo và thực thi constructor nên mô tả gì?

Khi thiết kế một pointcut liên quan đến constructor, nên trả lời các câu hỏi sau:

1. **Loại join point:** chọn *constructor call*, *constructor execution* hay một loại *initialization* (nếu có)?
2. **Loại đối tượng:** chọn constructor của lớp nào, lớp con nào, nhóm kiểu nào?
3. **Chữ ký constructor:** có bao nhiêu tham số, kiểu nào, phạm vi truy cập nào?
4. **Ngữ cảnh động:** có xét đối số thực tế hoặc caller không, và mô hình có cung cấp dữ liệu đó không?
5. **Các đường tạo khác nhau:** đối tượng được tạo qua `new` trực tiếp, một factory, reflection hay con đường đặc biệt khác?
6. **Thời điểm advice:** trước, sau khi thành công, sau khi lỗi, hoặc quanh điểm chọn?
7. **Điều gì được bảo toàn:** kết quả khởi tạo, ngoại lệ, số lần tạo, trạng thái cuối cùng?

Ví dụ pseudocode **độc lập công cụ, không phải cú pháp Java hoặc một thư viện AOP cụ thể**:

```text
aspect ConstructionObservability:
    pointcut selectedCreationSites:
        constructor-call(Order(String)) AND caller-in(BatchImporter)

    pointcut selectedExecutions:
        constructor-execution(Order(String))

    before selectedCreationSites:
        record("requested from importer")

    after-success selectedExecutions:
        record("Order constructor completed successfully")

    after-failure selectedExecutions:
        record("Order constructor failed")
```

Tác dụng minh họa: pointcut đầu tiên chọn **nơi gọi** từ một nhóm caller; pointcut thứ hai chọn **thực thi** của một constructor. Phạm vi phủ có thể khác nhau đáng kể. Các tên `constructor-call`, `after-success`… chỉ là ngôn ngữ diễn đạt trong bài, không cam kết một công cụ nào chấp nhận đúng cú pháp đó.

### 8.1. Điều kiện về chữ ký không phải điều kiện về giá trị

Constructor `Order(String code)` có chữ ký nhận `String`; điều kiện chọn “constructor nhận một `String`” có thể khớp với `new Order("A")`, `new Order("")` và `new Order(null)`. Nếu muốn chỉ chọn mã bắt đầu bằng `"VIP-"`, cần một điều kiện **động** kiểm tra giá trị, khi AOP có cung cấp ngữ cảnh tương ứng.

### 8.2. Cẩn thận với overload

```java
Order()
Order(String code)
Order(String code, int quantity)
```

Ba constructor này là những chữ ký khác nhau. Pointcut chọn `Order(String)` không tự bao phủ cả ba. Nếu một constructor gọi `this(...)` để chuyển tiếp, số lần execution join point và ngữ nghĩa của constructor call join point còn phụ thuộc đúng loại điểm mà mô hình nhận diện.

### 8.3. Điều kiện về kiểu và kế thừa

Một object được tạo bằng `new PremiumOrder(...)` có thể thực thi constructor của cả lớp cha `Order` và lớp con. Pointcut “constructor khai báo trong `Order`” và pointcut “tạo một đối tượng runtime thuộc loại `PremiumOrder`” không đồng nghĩa. Thậm chí ở những thời điểm rất sớm của quá trình khởi tạo, không được tùy tiện truy cập trạng thái của lớp con như thể đã hoàn tất.

## 9. Phân biệt before, after-returning, after-throwing và around khi xây dựng đối tượng

Những kiểu advice phổ biến có thể mang ý nghĩa khác nhau tại join point constructor:

| Advice khái niệm | Điều cần phân biệt |
|---|---|
| **Before** | Xảy ra trước mốc mà mô hình gọi là join point; không mặc nhiên đứng trước mọi bước cấp phát/khởi tạo |
| **After successful completion** | Chỉ khi phần hoạt động được bao quan sát là hoàn tất bình thường |
| **After exceptional completion** | Khi phần hoạt động được bao kết thúc bằng ngoại lệ thuộc phạm vi được cơ chế quan sát |
| **After/finally** | Chạy khi rời phạm vi được bao, bất kể thành công hay lỗi theo semantics của công cụ |
| **Around** | Có thể bao việc tiếp tục thực thi ở join point; quyền thay thế/chặn/đổi kết quả phụ thuộc tính năng và ràng buộc của công cụ |

**Ví dụ khác biệt:** Nếu constructor gán trường rồi kết thúc bình thường nhưng caller ném lỗi ngay sau `new`, một after-success gắn vào constructor execution vẫn có thể thành công, còn khối `try` lớn của caller có thể thất bại. Ngược lại, nếu đánh giá đối số của `new Order(computeId())` ném lỗi **trước khi constructor bắt đầu**, advice chỉ gắn vào constructor execution không có lý do gì phải chạy cho lần đó.

Điều này đặc biệt quan trọng khi đo số “lần yêu cầu tạo”, số “lần constructor bắt đầu”, số “lần hoàn tất”, và số “lần tạo thất bại”: đây là **những số đo khác nhau**.

## 10. Trường hợp đối số lỗi trước khi constructor được thực thi

Hãy xem đoạn Java:

```java
static String getId() {
    throw new IllegalStateException("cannot prepare id");
}

Order item = new Order(getId());
```

Đây là **mã minh họa rời**, không phải chương trình độc lập. Trong ngữ nghĩa Java, biểu thức đối số `getId()` được đánh giá trước khi lời thực thi constructor `Order(String)` tương ứng bắt đầu. Vì `getId()` ném ngoại lệ, thân constructor của `Order` không chạy.

Một điều kiện AOP về **constructor execution** vì thế không thể suy ra rằng có một lần thực thi constructor để advice xử lý trong trường hợp này. Với một **constructor-call join point**, việc bao gồm hay loại trừ giai đoạn đánh giá đối số còn tùy **định nghĩa chính xác của mô hình** và vị trí weaving. Cần kiểm chứng ở cấp ngữ nghĩa/công cụ, không tự giả định advice sẽ thấy mọi lỗi phát sinh từ toàn biểu thức `new`.

## 11. Các con đường tạo đối tượng không chỉ có một từ khóa new ở caller

Java hỗ trợ nhiều cách có được đối tượng. Không phải cách nào cũng hiển thị bằng `new SomeClass(...)` ngay tại mã của caller:

- **Factory method:** caller gọi `OrderFactory.create(...)`; bên trong factory có thể dùng `new`, tái sử dụng thể hiện hoặc trả về một loại đối tượng khác.
- **Reflection:** `Constructor.newInstance(...)` có thể gọi constructor thích hợp qua cơ chế phản chiếu, dù caller không viết biểu thức `new Order(...)` trực tiếp.
- **Cloning:** đối tượng có thể được sao chép theo cơ chế clone; constructor thông thường của lớp không nhất thiết chạy theo cách như `new`.
- **Deserialization:** trong một số cơ chế khôi phục đối tượng, không phải mọi constructor của lớp được khôi phục đều chạy theo chuỗi tạo thể hiện thông thường.
- **Cơ chế runtime/native khác:** mô hình nhận diện join point có thể không quan sát được mọi con đường cấp phát.

Do đó, hai phát biểu phải tách biệt:

1. “Tôi đã chọn **mọi vị trí mã chứa `new Order(...)`** mà công cụ nhìn thấy.”
2. “Tôi đã chọn **mọi lần một đối tượng `Order` xuất hiện trong hệ thống**.”

Phát biểu thứ hai **rộng hơn** và không tự được suy ra từ phát biểu thứ nhất. Nếu yêu cầu là đếm mọi đối tượng thực sự được cấp phát, có thể cần công cụ instrumentation/runtime chuyên biệt hoặc điểm quản lý vòng đời tập trung khác, chứ không thể đảm bảo chỉ bằng một pointcut constructor cụ thể.

## 12. Kế thừa và lời gọi this/super: vì sao số lượng join point dễ bị hiểu sai?

Trong Java, constructor của lớp con cần phối hợp với constructor lớp cha. Ngoài ra, một constructor có thể **chuyển tiếp** sang constructor khác của cùng lớp bằng `this(...)`.

Ví dụ ở phần 6 có một biểu thức `new Child()` nhưng đi qua:

```text
new Child()
  └── Child()
      └── Child(String)
          └── Base()
```

Đây là mô hình quan hệ chuyển tiếp (thứ tự **thực hiện thân** sau lời gọi chuyển tiếp nhìn khác thứ tự mũi tên). Một pointcut chọn constructor execution của `Base()` có thể phù hợp tại lần tạo `Child`, dù mã nguồn caller chỉ trực tiếp yêu cầu `Child()`.

Nếu mục tiêu là “một sự kiện cho mỗi đối tượng `Child` khởi tạo thành công”, việc gắn cùng advice vào **mọi constructor execution** của cả chuỗi có nguy cơ đếm nhiều lần. Nếu mục tiêu là “đo riêng chi phí thực thi mỗi constructor”, quan sát từng constructor lại có ý nghĩa.

**Thiết kế theo đại lượng cần đo**, chẳng hạn:

| Đại lượng | Ranh giới quan sát cần nghĩ đến |
|---|---|
| Bao nhiêu yêu cầu tạo `Child` từ một nhóm caller? | Constructor call ở các nơi gọi tương ứng |
| Bao nhiêu lần `Child(String)` thực thi? | Constructor execution của `Child(String)` |
| Bao nhiêu thể hiện `Child` được khởi tạo thành công? | Điểm hoàn tất khởi tạo thích hợp, cần tránh đếm chuỗi constructor nhiều lần |
| Mất bao lâu để caller nhận một đối tượng sẵn dùng? | Ranh giới bao toàn quá trình tạo được định nghĩa rõ |
| Chi phí một constructor cụ thể? | Ranh giới execution của constructor đó và các phần thực sự được tính vào số đo |

## 13. Trạng thái this chưa hoàn chỉnh và rủi ro của advice khi tạo đối tượng

Việc can thiệp vào constructor nhạy cảm hơn quan sát một phương thức chạy trên đối tượng đã sẵn sàng.

### 13.1. Đọc trường quá sớm

Ngay sau cấp phát, các trường instance có giá trị mặc định; các lệnh khởi tạo trường và thân constructor chưa chắc đã chạy. `final` có thể chưa nhận giá trị được thiết lập trong constructor. Nếu advice chạy ở một mốc sớm được cơ chế cho phép và đọc `this.id` như trạng thái hoàn chỉnh, kết quả có thể sai hoặc gây lỗi.

### 13.2. Để this thoát ra ngoài trong constructor

Một anti-pattern là công bố `this` từ constructor vào callback, bộ lắng nghe, tập đối tượng toàn cục hoặc luồng khác trước khi khởi tạo xong. Khi đó, mã khác có thể quan sát đối tượng ở trạng thái dở dang.

Aspect chạy quá sớm cũng có thể vô tình khiến `this` “thoát” ra, ví dụ đẩy tham chiếu đối tượng vào hệ thống sự kiện. Nguyên tắc: chỉ công bố thể hiện khi hợp đồng đã cho phép và vòng đời thích hợp đã hoàn tất.

### 13.3. Gọi phương thức có thể override trong constructor

Khi một constructor gọi phương thức instance có thể được override, việc dispatch có thể đến phương thức của lớp con ngay cả khi trạng thái lớp con chưa hoàn chỉnh. Nếu advice can thiệp vào đường gọi đó, thứ tự thực thi còn phức tạp hơn. Không dùng advice làm lý do để gọi thêm phương thức overridable trên đối tượng đang xây dựng.

### 13.4. Thay đổi kết quả construction

Một around advice có thể có khả năng điều phối việc tiếp tục, thay thế hoặc bỏ qua hoạt động gốc ở một số mô hình. Nhưng constructor và đối tượng mới có các ràng buộc ngôn ngữ/runtime: không thể mặc định áp dụng mọi thao tác biến đổi vốn có ở method advice cho constructor advice.

Phải kiểm tra kỹ: công cụ có hỗ trợ around cho loại join point này không, có cho phép thay đối tượng hay không, giá trị trả về được định nghĩa thế nào và việc gọi phần gốc nhiều lần có hợp pháp/đúng hợp đồng không. **Không phỏng đoán rằng around constructor luôn tương đương around một hàm trả về đối tượng.**

## 14. Exception, tài nguyên và sự khác nhau giữa “thoát” và “tạo thành công”

Có ít nhất ba trường hợp cần phân biệt:

1. **Lỗi trước khi vào constructor đích:** đánh giá đối số hoặc phần xử lý chuẩn bị có thể thất bại.
2. **Lỗi trong chuỗi khởi tạo:** constructor cha, initializer hoặc constructor đích có thể ném ngoại lệ.
3. **Constructor hoàn tất, caller thất bại về sau:** đối tượng có thể đã được khởi tạo nhưng quy trình nghiệp vụ lớn hơn vẫn thất bại.

Nếu một constructor mở tài nguyên rồi ném ngoại lệ, Java không tự biến advice “after” thành thao tác giải phóng tài nguyên đúng đắn. Mã khởi tạo vẫn cần giữ các bảo đảm an toàn của chính nó, ví dụ giải phóng tài nguyên đã mở khi khởi tạo không thành công. Advice quan sát tốt phải tránh che giấu ngoại lệ gốc hoặc ném ngoại lệ log làm biến dạng nguyên nhân thất bại.

**Sai lầm phổ biến:** coi bộ đếm “số lần constructor execution bắt đầu” là “số đối tượng sử dụng được”. Đếm theo mục tiêu: yêu cầu tạo, thực thi bắt đầu, khởi tạo thành công, hoặc đối tượng được công bố cho phần còn lại của hệ thống đều có ngữ nghĩa riêng.

## 15. Weaving và giới hạn khả năng can thiệp

AOP không bắt buộc có cùng một kiến trúc triển khai. Tùy mô hình:

- **Weaving ở mức nguồn/bytecode hoặc quá trình biên dịch:** có thể nhận diện những loại điểm mà cơ chế trung gian chỉ dựa trên lời gọi method không chặn được, trong phạm vi được hỗ trợ.
- **Weaving khi nạp lớp:** có thể chỉnh mã tại giai đoạn tải lớp nếu runtime/công cụ có hỗ trợ và các lớp liên quan được xử lý đúng phạm vi.
- **Cơ chế trung gian ở thời điểm chạy:** thường chỉ quan sát các lời gọi đi qua ranh giới trung gian đã đặt; không tự nhìn thấy mọi biểu thức `new`.

Các kỹ thuật có hạn chế và chi phí riêng. Trong Java, một dynamic proxy tạo bằng `java.lang.reflect.Proxy` **là proxy cho lời gọi phương thức của giao diện**; bản thân nó không tự chặn mọi lần `new` hay tự thêm advice vào thân constructor. Một factory hoặc wrapper có thể tập trung lời gọi tạo đối tượng, nhưng đó là **một ranh giới gọi có chủ đích**, không có nghĩa một pointcut constructor tự được áp dụng ở mọi nơi.

**Trước khi khẳng định độ bao phủ, phải xác minh ba lớp:**

```text
Cú pháp/điều kiện pointcut
          ∩
Loại join point công cụ hỗ trợ
          ∩
Phạm vi mã/đường thực thi thực sự được weaving
          =
Tập join point mà advice có thể được kích hoạt
```

Pointcut đúng ý tưởng nhưng loại join point không được hỗ trợ hoặc đường gọi không được weaving thì sẽ không tạo ra hành vi mong đợi.

## 16. Một số tình huống nâng cao

### 16.1. Tạo đối tượng trong vòng lặp lớn

Một pointcut áp dụng vào constructor của đối tượng cực kỳ thường xuyên được tạo có thể khiến advice gây chi phí đáng kể. Chi phí này bao gồm kiểm tra điều kiện, ghi log, tạo chuỗi, đo thời gian hoặc đồng bộ dữ liệu thống kê.

Một chiến lược là chọn đối tượng có ý nghĩa quan sát rõ và sampling thích hợp. Nếu cần đếm chính xác tất cả lần tạo, phải kiểm chứng rằng phép đo không làm đổi hành vi hoặc làm quá tải chương trình. Không kết luận hiệu năng AOP từ một phép đo chỉ có `System.out.println`.

### 16.2. Constructor lồng nhau và tạo đối tượng gián tiếp

Constructor `Order()` có thể tự tạo một `Address()`, `LineItem()` hoặc đối tượng khác. Nếu pointcut rộng chọn mọi constructor, một yêu cầu tạo đối tượng nghiệp vụ có thể sinh nhiều sự kiện advice.

Hãy xác định bạn muốn thống kê **đối tượng gốc**, **tất cả thể hiện phụ thuộc**, hay **lần gọi constructor của một loại cụ thể**; tránh gộp chúng thành cùng một số liệu.

### 16.3. Các bất biến (invariants) của đối tượng

Một constructor có thể bảo đảm đối tượng không được công bố nếu dữ liệu không hợp lệ. Pointcut chọn constructor để log điều kiện có thể hợp lý; nhưng advice không nên làm mất hợp đồng “đối tượng chỉ tồn tại ở trạng thái hợp lệ sau khi tạo xong”.

Đặc biệt với bất biến liên quan `final` fields, an toàn luồng và kiểm soát truy cập, cần tránh quan sát/thay đổi trạng thái ở mốc mà lớp chưa hoàn tất khởi tạo.

### 16.4. Factory trả đối tượng có sẵn

```java
Order order = factory.getOrCreate("A-100");
```

Một lần gọi `getOrCreate` không đồng nghĩa một lần constructor được thực thi: factory có thể trả về thể hiện cache. Nếu yêu cầu là “đếm yêu cầu cấp đối tượng”, pointcut ở factory có thể hợp lý hơn. Nếu yêu cầu là “đếm lần tạo thể hiện mới”, điểm tạo thực tế mới liên quan.

### 16.5. Reflection và đường tạo đặc biệt

Một công cụ AOP có thể quan sát việc thực thi constructor được gọi qua reflection nhưng không thấy cùng call site `new` như biểu thức nguồn; hoặc có thể có giới hạn khác. Với cloning và một số deserialization, logic constructor của lớp có thể không chạy như tạo đối tượng thông thường. Vì vậy, một pointcut constructor không bao giờ được mô tả như máy đếm đối tượng tuyệt đối khi chưa kiểm tra tất cả đường tạo cần thiết.

## 17. Hướng dẫn chọn điểm can thiệp đúng mục tiêu

| Yêu cầu | Điều cần chọn/đo | Lưu ý |
|---|---|---|
| Ghi thông tin về lần **yêu cầu** tạo `Order` từ một caller | Call site được xác định | Phụ thuộc các nơi gọi thực sự được weaving |
| Ghi hoạt động của `Order(String)` | Constructor execution | Có thể nhận cả những đường gọi gián tiếp phù hợp |
| Ghi một sự kiện **khởi tạo `Order` thành công** | Hoàn tất đúng vòng đời khởi tạo | Tránh đếm nhiều constructor trong một chuỗi |
| Quan sát tham số hợp lệ | Điều kiện về giá trị đối số ở ranh giới có dữ liệu | Không nhầm kiểu tham số với giá trị; xử lý `null` |
| Đo tổng thời gian caller nhận đối tượng | Ranh giới bao đủ biểu thức tạo cần quan sát | Cần rõ phần đánh giá đối số và khởi tạo có nằm trong phép đo không |
| Bắt thất bại trong khởi tạo | Nhánh ngoại lệ của vùng thích hợp | Lỗi ở giai đoạn sớm có thể xảy ra trước constructor đích |
| Quan sát mọi thể hiện được cấp phát | Cơ chế quan sát cấp phát phù hợp runtime | Constructor pointcut chưa chắc đủ vì đường tạo đặc biệt |

Việc chọn loại join point nên xuất phát từ **câu hỏi cần trả lời**, không xuất phát từ tên advice có sẵn. Trước khi triển khai, diễn đạt một câu rõ ràng: “Tôi đang quan sát một **lần gọi** hay một **lần thực thi**, và như thế nào mới được tính là **thành công**?”

## 18. Bài tập tự kiểm tra

### Bài 1: Tính số lần thực thi constructor

Xét cấu trúc:

```java
class Parent {
    Parent() {}
}

class Child extends Parent {
    Child() {
        this(7);
    }

    Child(int value) {
        super();
    }
}
```

Với `new Child()`, có mấy constructor body của những lớp này được thực thi?

**Đáp án:** ba constructor body: `Parent()`, `Child(int)`, `Child()`, theo thứ tự thực thi thân tương ứng. Đây là **một lần tạo đối tượng `Child`**, không phải ba đối tượng độc lập.

### Bài 2: Phân biệt nơi gọi và nơi thực thi

Hai phương thức `checkout()` và `importBatch()` đều gọi `new Order("A")`. Muốn gắn hành vi **chỉ khi nơi gọi là `importBatch()`**, nên ưu tiên mô tả điểm can thiệp nào nếu mô hình AOP hỗ trợ?

**Đáp án:** điều kiện constructor-call kết hợp ranh giới caller/call site tương ứng. Một pointcut chỉ nói “constructor execution của `Order(String)`” chưa phân biệt được hai nơi gọi nếu không thêm điều kiện ngữ cảnh.

### Bài 3: Điểm khởi tạo thất bại

Nếu `new Order(loadCode())` mà `loadCode()` ném ngoại lệ trước khi trả về, điều gì chắc chắn đúng?

**Đáp án:** constructor `Order(String)` không được thực thi do việc đánh giá đối số thất bại; advice chỉ gắn vào constructor execution đó không thể coi đây là một lần constructor đã bắt đầu. Việc một cơ chế AOP quan sát được sự kiện call thất bại ở giai đoạn nào phải theo semantics cụ thể.

### Bài 4: Kiểm chứng phạm vi AOP

Một dynamic proxy của giao diện `OrderService` chặn được `service.placeOrder()`, nhưng yêu cầu mới là bắt mọi `new Order(...)`. Có thể chỉ thêm điều kiện pointcut và đảm bảo đạt yêu cầu không?

**Đáp án:** không. Trước hết phải xác định cơ chế có **hỗ trợ join point constructor call/execution** không; proxy chặn method qua giao diện không tự chặn `new`. Cần chọn kỹ thuật weaving, điểm factory, hoặc cơ chế quan sát vòng đời phù hợp.

### Bài 5: Đếm lần tạo thành công

Với một biểu thức `new Child()` có chuỗi `Child() → Child(String) → Parent()`, nếu advice tăng một bộ đếm ở đầu *mỗi constructor execution*, số tăng có nhất thiết bằng số đối tượng `Child` khởi tạo thành công?

**Đáp án:** không. Một lần tạo có thể có nhiều constructor execution, và một trong các bước khởi tạo có thể ném ngoại lệ. Bộ đếm ở đầu execution biểu diễn số lần bắt đầu thực thi các constructor được chọn, không đồng nghĩa số thể hiện khởi tạo hoàn tất.

## 19. Ghi nhớ

- **Constructor call** tập trung vào **nơi yêu cầu tạo** đối tượng; **constructor execution** tập trung vào **việc thực thi** constructor cụ thể; **object initialization** có thể là phạm vi riêng nếu mô hình hỗ trợ.
- **Một biểu thức `new` không đồng nghĩa chỉ một constructor execution**, nhất là với `this(...)`, `super(...)` và kế thừa.
- Thân constructor không luôn bao phủ toàn bộ quá trình tạo; lỗi có thể xảy ra khi đánh giá đối số, trong constructor cha hoặc initializer.
- Advice “sau khi thoát” và advice “sau khi thành công” mang ý nghĩa khác nhau. Một lần bắt đầu không đồng nghĩa một đối tượng đã khởi tạo xong.
- **Pointcut là điều kiện chọn; advice là hành vi; weaving quyết định cách kết hợp**, và khả năng hỗ trợ constructor join point khác nhau giữa các cơ chế AOP.
- Không tự cho rằng proxy lời gọi phương thức có thể chặn `new`; không tự cho rằng mọi đối tượng đều đi qua constructor thông thường.
- Khi can thiệp vào khởi tạo, phải bảo toàn thứ tự hợp lệ, ngoại lệ gốc, bất biến của đối tượng và tránh công bố `this` khi chưa hoàn chỉnh.

Cách tư duy bền vững là xác định chính xác **đang muốn quan sát yêu cầu tạo, từng lần thực thi constructor, hay đối tượng đã hoàn tất khởi tạo**. Từ đại lượng đó mới lựa chọn join point, pointcut và lời khuyên xử lý phù hợp với cơ chế AOP cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-003">Đọc và ghi thuộc tính khi mô hình AOP hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc và ghi thuộc tính khi mô hình AOP hỗ trợ

Một đối tượng thường lưu trạng thái trong các **trường dữ liệu (fields)** và cung cấp cách truy cập trạng thái qua **thuộc tính (properties)** hoặc phương thức. Một số mô hình lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP) cho phép chọn **điểm đọc** và **điểm ghi** trường dữ liệu làm điểm can thiệp (join point). Khi đó, khía cạnh (aspect) có thể thực hiện hành vi bổ sung ngay quanh việc lấy hoặc gán một giá trị, ví dụ ghi nhận truy cập, quan sát thay đổi trạng thái hay kiểm tra một chính sách kỹ thuật.

Điểm mấu chốt của bài: **không phải cơ chế AOP nào cũng hỗ trợ điểm đọc/ghi trường dữ liệu**. Một cơ chế chỉ bao quanh lời gọi phương thức có thể theo dõi `getScore()` và `setScore(...)`, nhưng không vì thế mà theo dõi được lệnh `object.score` hoặc `object.score = ...`. Ta cần phân biệt loại điểm nối, đường thực thi thực tế và cơ chế kết hợp aspect (weaving).

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Phân biệt trường dữ liệu, thuộc tính, getter/setter và **sự kiện đọc/ghi** ở cấp ngôn ngữ.
- Xác định khi nào một thao tác đọc hoặc ghi được mô hình AOP coi là join point.
- Giải thích pointcut chọn **field get** hoặc **field set** và advice có thể chạy trước, sau hoặc bao quanh thao tác được chọn như thế nào.
- Nhận diện khác biệt giữa chặn **truy cập trường trực tiếp** và chặn **lời gọi phương thức truy cập**.
- Hiểu vai trò của compile-time, binary và load-time weaving đối với khả năng can thiệp vào bytecode truy cập trường.
- Tránh các lỗi thường gặp về điểm can thiệp bị bỏ sót, lặp can thiệp, vòng lặp trong advice, trạng thái bất biến, hiệu năng và đồng thời.
- Kiểm thử một chính sách theo dõi đọc/ghi mà không vô tình đổi kết quả nghiệp vụ.

## 2. Phân biệt field, property và thao tác truy cập

Trong Java, **field** là biến được khai báo trong lớp hoặc đối tượng:

```java
class Device {
    int temperature;
}
```

Ở nơi có quyền truy cập, biểu thức `device.temperature` đọc field; câu lệnh `device.temperature = 25` ghi field. Đây là **truy cập trường trực tiếp**.

Một đối tượng cũng có thể bảo vệ trạng thái qua phương thức:

```java
class Device {
    private int temperature;

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int value) {
        if (value < -50 || value > 150) {
            throw new IllegalArgumentException("Ngoài giới hạn");
        }
        temperature = value;
    }
}
```

Ở đây có hai lớp ý nghĩa:

1. Người sử dụng gọi `device.getTemperature()` hoặc `device.setTemperature(25)`: đó là **lời gọi phương thức**.
2. Bên trong phương thức, JVM thực hiện lệnh đọc hoặc ghi `temperature`: đó là **truy cập field**.

Một mô hình AOP có khả năng chọn điểm nối **method execution/method call** có thể quan sát lớp thứ nhất. Một mô hình hỗ trợ **field get/field set** có thể quan sát lớp thứ hai. Hai lớp có thể cùng xuất hiện trong một lần gọi; nếu đồng thời áp khía cạnh ở cả hai, sự kiện có thể bị ghi nhận hai lần nếu thiết kế không tính đến.

### 2.1. “Thuộc tính” không nhất thiết tương ứng một field

Từ *property* thường diễn tả một đặc điểm logic của đối tượng. Trong Java, property có thể được biểu diễn qua cặp getter/setter theo quy ước đặt tên, nhưng không nhất thiết có field cùng tên:

```java
class Rectangle {
    private final int width;
    private final int height;

    Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getArea() {
        return width * height;
    }
}
```

`getArea()` cung cấp thuộc tính suy ra từ hai field. Không có field `area` để một pointcut chọn theo thao tác đọc `area`. Tương tự, một setter có thể cập nhật nhiều field, gửi sự kiện hoặc từ chối ghi. Chặn lời gọi getter/setter và chặn bytecode đọc/ghi field vì vậy **không thể thay thế nhau một cách máy móc**.

### 2.2. Đọc và ghi là hai hành vi khác nhau

| Trường hợp | Ví dụ | Hành vi quan sát |
| --- | --- | --- |
| Đọc field | `int x = device.temperature;` | Lấy giá trị hiện tại của field |
| Ghi field | `device.temperature = 25;` | Đưa một giá trị mới vào field |
| Gọi getter | `int x = device.getTemperature();` | Chạy phương thức, có thể bao gồm một hay nhiều lần đọc field |
| Gọi setter | `device.setTemperature(25);` | Chạy phương thức, có thể kiểm tra, biến đổi rồi ghi field |
| Đọc thuộc tính tính toán | `rectangle.getArea();` | Chạy logic tính giá trị, không đòi hỏi field cùng tên |

Với Java, một biểu thức có cả đọc và ghi như `counter.value++` thường cần **đọc giá trị hiện tại rồi ghi giá trị mới**. Nếu mô hình và điểm biến đổi tương ứng hỗ trợ cả hai, một câu lệnh nguồn có thể tạo **hơn một điểm can thiệp**. Không nên đếm số sự kiện bằng số dòng mã.

## 3. Điểm đọc/ghi trong mô hình AOP

### 3.1. Join point: điểm có thể can thiệp

**Join point** là vị trí thực thi được mô hình AOP nhận diện và cho phép gắn hành vi bổ sung. Với một cơ chế hỗ trợ truy cập trường, nó có thể bao gồm:

- **Field get:** thời điểm mã đang lấy một giá trị từ field.
- **Field set:** thời điểm mã đang gán giá trị cho field.
- Các loại điểm khác, nếu công nghệ hỗ trợ, như thực thi phương thức, tạo đối tượng, xử lý ngoại lệ.

Không nên suy luận rằng một công nghệ hỗ trợ field get thì tự động hỗ trợ mọi dạng quan sát đọc trạng thái. Chẳng hạn, một lời gọi reflection, một thao tác native, một truy cập qua phương thức đặc biệt hoặc một đường thực thi không nằm trong phạm vi biến đổi có thể có quy tắc khác. **Tập join point do cơ chế AOP và cách weaving xác định**, không chỉ do cú pháp Java.

### 3.2. Pointcut: chọn những thao tác nào

**Pointcut** là quy tắc chọn join point. Với truy cập field, một pointcut có thể dựa trên:

- Tên field, kiểu dữ liệu, lớp khai báo và phạm vi lớp/gói;
- Field tĩnh hoặc thuộc đối tượng, nếu mô hình cho phép phân biệt;
- Thao tác đọc hay ghi;
- Ngữ cảnh của đường thực thi, đối tượng đang được truy cập hoặc những điều kiện động được cơ chế hỗ trợ;
- Quy tắc loại trừ mã của chính khía cạnh và các phần không cần quan sát.

Pointcut quá rộng như “mọi truy cập mọi field” có thể tạo lượng advice rất lớn và dễ quan sát cả trạng thái nội bộ của thư viện. Pointcut quá hẹp có thể bỏ sót đường ghi quan trọng. Tốt hơn nên xác định rõ **field nào**, **thao tác nào** và **ở ranh giới nào** thực sự cần chính sách.

### 3.3. Advice: hành vi cần kết hợp

**Advice** là hành vi được thực hiện tại điểm đã chọn, ví dụ:

| Kiểu advice | Khi áp dụng vào đọc/ghi | Điểm cần lưu ý |
| --- | --- | --- |
| Trước (before) | Ghi nhận sắp đọc hoặc sắp ghi | Với ghi, việc “sắp ghi” chưa chứng minh gán đã thành công |
| Sau thành công (after returning) | Ghi nhận thao tác đã kết thúc bình thường | Chỉ phản ánh kết quả theo đường thành công, không phải mọi nhánh lỗi |
| Sau lỗi (after throwing) | Theo dõi ngoại lệ liên quan điểm truy cập | Truy cập field đơn giản có ít đường lỗi hơn một phương thức nghiệp vụ |
| Cuối cùng (after/finally) | Thu dọn bối cảnh đo đạc trên nhiều nhánh | Ngữ nghĩa chính xác phụ thuộc mô hình advice |
| Bao quanh (around) | Có thể điều phối thao tác đọc/ghi hoặc kết quả | Có nguy cơ ngăn thao tác, đổi giá trị, thực hiện lặp hoặc gây lỗi |

Một số công nghệ chỉ hỗ trợ một tập con kiểu advice cho từng loại điểm nối hoặc có quy tắc ràng buộc kiểu kết quả/đối số. Không giả định mọi advice đều hợp lệ với mọi field.

### 3.4. Weaving: biến quy tắc thành hành vi thực

Một mô tả pointcut và advice không tự nhiên làm mọi phép đọc/ghi trong chương trình được giám sát. **Weaving** kết hợp khía cạnh vào chương trình sao cho tại những điểm được hỗ trợ, hành vi bổ sung chạy đúng chỗ.

Một số công cụ biến đổi mã nguồn hoặc bytecode tại giai đoạn biên dịch/sau biên dịch; một số cho phép biến đổi trước khi JVM định nghĩa lớp; một số chỉ tổ chức các bộ chặn lời gọi quanh đối tượng. Những lựa chọn này ảnh hưởng trực tiếp đến việc field get/set có được quan sát hay không.

## 4. Khi field get/set là join point, chuyện gì xảy ra?

Giả sử một chương trình có:

```java
class Meter {
    int reading;
}

Meter meter = new Meter();
meter.reading = 42;
int current = meter.reading;
```

Đây là **đoạn mã minh họa**, không phải một tệp chạy độc lập.

Nếu công cụ có thể can thiệp vào truy cập trường và pointcut chọn đúng `Meter.reading`, quy tắc được hiểu theo ý tưởng:

```text
meter.reading = 42
         |
         v
[Pointcut có chọn field set của Meter.reading?]
         |
        Có
         |
         v
[Advice trước ghi, nếu có]
         |
         v
[Thao tác ghi giá trị 42]
         |
         v
[Advice sau ghi, nếu có]

int current = meter.reading
                   |
                   v
[Pointcut có chọn field get của Meter.reading?]
                   |
                  Có
                   |
                   v
[Advice trước đọc, nếu có]
                   |
                   v
[Thao tác lấy giá trị]
                   |
                   v
[Advice sau đọc, nếu có]
```

Một lớp biến đổi bytecode có thể chèn lệnh phục vụ advice quanh chính các lệnh truy cập field tương ứng. Điều đó khác với việc yêu cầu tất cả người gọi viết `audit(...)` thủ công.

### 4.1. Field read và giá trị quan sát được

Khi một thao tác đọc đã có kết quả, **after returning** có thể giúp quan sát giá trị trả về *nếu công nghệ hỗ trợ kết nối kết quả vào advice*. Nhưng giá trị ấy không nhất thiết là “giá trị mới nhất trên toàn hệ thống” trong môi trường nhiều luồng; cách nhìn thấy giá trị còn phụ thuộc mô hình bộ nhớ.

Nếu **around advice** được phép thay kết quả của phép đọc, chương trình có thể nhìn thấy giá trị khác với giá trị thực đang lưu. Đây là thay đổi có thể ảnh hưởng quy tắc nghiệp vụ và bất biến, không còn là quan sát thuần túy.

### 4.2. Field write và trạng thái trước/sau

Trước khi ghi, một advice có thể thấy thông tin về field, đối tượng hoặc giá trị định ghi **nếu điểm nối và cơ chế hỗ trợ truyền dữ liệu đó**. Tuy nhiên:

- Giá trị định ghi chưa chắc trở thành giá trị đã lưu: thao tác có thể bị chặn, lỗi hoặc biến đổi;
- Đọc lại field để tìm giá trị cũ có thể làm nảy sinh **một join point đọc khác**, dẫn đến ghi log lặp hoặc vòng lặp nếu không loại trừ;
- Trong môi trường đồng thời, đọc “giá trị cũ” ở một thời điểm rồi so sánh với “giá trị mới” ở thời điểm khác **không bảo đảm hai giá trị tạo thành một chuyển trạng thái nguyên tử**;
- Một advice chạy sau thao tác ghi không tự biến thao tác thành một giao dịch có thể hoàn tác.

Nếu cần sự kiện “trạng thái đã chuyển chính xác từ A sang B”, hãy xác định thao tác cập nhật nguyên tử hoặc ranh giới nghiệp vụ tạo sự kiện đó. Một quan sát field set đơn thuần chưa cung cấp toàn bộ cam kết này.

### 4.3. Cùng một trạng thái có nhiều đường truy cập

Một field riêng tư có thể được ghi trong constructor, setter, phương thức nội bộ, hoặc qua cơ chế truy cập đặc biệt. Trường hợp nào phát sinh join point và thực sự được weaving phải được xác minh.

Chẳng hạn:

```java
class Account {
    private int balance;

    Account(int openingBalance) {
        this.balance = openingBalance;
    }

    void deposit(int amount) {
        this.balance += amount;
    }

    int balance() {
        return this.balance;
    }
}
```

Trong đoạn trên, `deposit` thường có cả đọc và ghi `balance`; constructor có ghi; phương thức `balance()` có đọc. Nếu bộ weaving chọn thao tác field get/set và có quyền biến đổi mã tương ứng, có thể có nhiều điểm can thiệp. Nếu cơ chế chỉ quan sát lời gọi `deposit()` hay `balance()`, đó là **điểm can thiệp phương thức**, với khả năng bao quát khác.

## 5. Phân biệt hai đường kỹ thuật: field weaving và proxy getter/setter

| Tiêu chí | Field get/set weaving (khi hỗ trợ) | Chặn getter/setter qua proxy/interceptor |
| --- | --- | --- |
| Điểm được chọn | Thao tác đọc/ghi một field trong phần mã có thể can thiệp | Lời gọi phương thức như `getValue()` hoặc `setValue(...)` |
| Người gọi trực tiếp truy cập field | Có thể được can thiệp nếu đường bytecode đó đã được kết hợp và được hỗ trợ | Không đi qua getter/setter nên không bị proxy getter/setter quan sát |
| Tự gọi phương thức trong đối tượng | Field access bên trong có thể được can thiệp khi mã chứa access đã được biến đổi | Lời gọi qua `this` thường không đi qua proxy ngoài |
| Getter tính toán giá trị | Không có field logic tương ứng thì không tồn tại field get để chọn | Vẫn có thể quan sát lời gọi getter |
| Phạm vi kỹ thuật | Thường đòi hỏi cơ chế weaving sửa mã/bytecode trong phạm vi hỗ trợ | Có thể tổ chức động theo đối tượng đại diện hoặc chuỗi bộ chặn |
| Ngữ nghĩa | Quan sát một truy cập lưu trữ cụ thể | Quan sát một thao tác do API của đối tượng định nghĩa |
| Rủi ro thường gặp | Quá nhiều join point, hiệu năng, recursion, bỏ sót đường truy cập không biến đổi | Đường gọi bypass, proxy khác identity, setter không ghi đúng một field |

**Không đồng nhất** “phương thức `getX()` được gọi” với “field `x` đã được đọc”. Một getter có thể trả về hằng số, tính toán, đọc bộ nhớ đệm, gọi dịch vụ khác hoặc ném lỗi; một setter có thể từ chối gán hoặc thay đổi nhiều field.

### 5.1. Một giới hạn căn bản của JDK dynamic proxy

Trong Java, JDK dynamic proxy thông thường nhận cuộc gọi **qua những phương thức giao diện**. Nó không tự can thiệp lệnh `obj.field`, `obj.field = value`, constructor hay toàn bộ hành vi nội bộ của lớp đích.

Vì vậy một ví dụ chỉ sử dụng proxy cho getter/setter **không phải bằng chứng đã weaving các điểm field get/set**. Ta có thể học cách tách hành vi quan sát từ proxy, nhưng phải diễn giải chính xác lớp hành vi đang được quan sát.

## 6. Ví dụ Java 17 chạy được: quan sát truy cập qua getter/setter

Ví dụ dùng JDK dynamic proxy để minh họa một phương án **theo dõi truy cập thuộc tính qua API phương thức**. Phần dữ liệu thật vẫn nằm trong lớp `Score`, còn lớp bọc chỉ ghi nhận hoạt động. Ví dụ không yêu cầu thư viện ngoài JDK.

Lưu vào tệp `PropertyAccessDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PropertyAccessDemo {
    interface ScorePort {
        int getScore();
        void setScore(int next);
    }

    static final class Score implements ScorePort {
        private int score = 10;

        @Override
        public int getScore() {
            return score;
        }

        @Override
        public void setScore(int next) {
            if (next < 0) {
                throw new IllegalArgumentException("Điểm phải không âm");
            }
            score = next;
        }
    }

    static Object invoke(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static ScorePort observe(ScorePort target) {
        return (ScorePort) Proxy.newProxyInstance(
                ScorePort.class.getClassLoader(),
                new Class<?>[] { ScorePort.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    boolean reading = name.equals("getScore");
                    boolean writing = name.equals("setScore");

                    if (reading) {
                        System.out.println("[READ] begin");
                    }
                    if (writing) {
                        System.out.println("[WRITE] requested=" + args[0]);
                    }

                    try {
                        Object result = invoke(method, target, args);
                        if (reading) {
                            System.out.println("[READ] ok");
                        }
                        if (writing) {
                            System.out.println("[WRITE] ok");
                        }
                        return result;
                    } catch (Throwable ex) {
                        if (writing) {
                            System.out.println("[WRITE] rejected");
                        }
                        throw ex;
                    }
                });
    }

    public static void main(String[] args) {
        Score real = new Score();
        ScorePort observed = observe(real);

        System.out.println("Điểm ban đầu: " + observed.getScore());
        observed.setScore(12);
        System.out.println("Điểm mới: " + observed.getScore());

        try {
            observed.setScore(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("Lỗi: " + ex.getMessage());
        }

        System.out.println("Đọc trực tiếp: " + real.getScore());
    }
}
```

Biên dịch và chạy bằng Java 17 hoặc mới hơn:

```bash
javac PropertyAccessDemo.java
java PropertyAccessDemo
```

Đầu ra:

```text
[READ] begin
[READ] ok
Điểm ban đầu: 10
[WRITE] requested=12
[WRITE] ok
[READ] begin
[READ] ok
Điểm mới: 12
[WRITE] requested=-1
[WRITE] rejected
Lỗi: Điểm phải không âm
Đọc trực tiếp: 12
```

**Giải thích chính xác cơ chế:**

1. `Score` sở hữu trạng thái và **bất biến nghiệp vụ**: điểm không được âm. Proxy không thay đổi quy tắc này.
2. `observe(real)` tạo đối tượng đại diện. Người gọi sử dụng `observed` sẽ đi qua logic ghi nhận trước và sau lời gọi getter/setter.
3. `method.invoke(...)` dùng reflection chuyển lời gọi tới đối tượng thật. `InvocationTargetException` được bóc để giữ lại ngoại lệ `IllegalArgumentException` do `Score.setScore` ném ra.
4. Việc ghi thất bại in `[WRITE] rejected`, và lỗi vẫn đến người gọi. Giá trị trong `Score` giữ nguyên `12`; lần đọc trực tiếp cuối cho thấy điều đó.
5. `real.getScore()` gọi trực tiếp đối tượng đích nên không đi qua proxy, vì vậy **không phát sinh** dòng `[READ]`. Đây là ranh giới can thiệp của proxy; không phải lỗi trong ví dụ.
6. Ở đây các pointcut đơn giản được mô phỏng bằng tên phương thức; ví dụ **không chặn trực tiếp lệnh đọc/ghi field `score`**.

Trong ứng dụng thật, ghi mọi giá trị thuộc tính vào log có thể để lộ thông tin nhạy cảm. Các thông tin như mật khẩu, token, dữ liệu cá nhân hoặc khóa bí mật phải được loại bỏ hay che chắn theo chính sách thích hợp.

## 7. Ví dụ mô hình có field join point: cú pháp AspectJ để đối chiếu

Để nhìn thấy **một công nghệ thực sự biểu đạt điểm đọc/ghi field**, ta có thể dùng cú pháp khía cạnh của AspectJ như một ví dụ cụ thể. Cú pháp `get(...)` và `set(...)` trong phần này thuộc **mô hình điểm nối của AspectJ**; đó không phải cú pháp Java tiêu chuẩn và không phải cam kết mọi hệ AOP đều hỗ trợ.

Mã đối tượng đích, tệp `FieldDemo.java`:

```java
public class FieldDemo {
    public int score;

    public static void main(String[] args) {
        FieldDemo example = new FieldDemo();
        example.score = 7;
        System.out.println("Score=" + example.score);
    }
}
```

Khía cạnh, tệp `ScoreFieldAspect.aj`:

```java
public aspect ScoreFieldAspect {
    before(): set(int FieldDemo.score) {
        System.out.println("[FIELD] set");
    }

    before(): get(int FieldDemo.score) {
        System.out.println("[FIELD] get");
    }
}
```

Đây là **hai tệp dùng bộ biên dịch/weaver AspectJ phù hợp**, không phải chương trình có thể biên dịch `ScoreFieldAspect.aj` bằng `javac` thông thường. Nếu khía cạnh được weaving vào đúng các thao tác trong `FieldDemo.main`, đầu ra là:

```text
[FIELD] set
[FIELD] get
Score=7
```

Nếu chỉ biên dịch và chạy `FieldDemo.java` bằng Java thuần, không có bước weaving, đầu ra chỉ là:

```text
Score=7
```

Điểm cần quan sát: `example.score = 7` là một lần **ghi field trực tiếp**, còn `example.score` trong phép in là **đọc field trực tiếp**. Ở ví dụ này, việc chọn chúng dựa vào kiểu field và lớp khai báo, không dựa vào tên getter/setter. `public` được dùng để ví dụ ngắn, **không phải khuyến nghị công khai trạng thái đối tượng** trong thiết kế thông thường.

### 7.1. Cần điều kiện nào để ví dụ field weaving có hiệu lực?

- Có bộ weaving thực sự hiểu và hỗ trợ loại join point `get`/`set`;
- Đã cung cấp aspect và mã chứa thao tác truy cập vào đúng quy trình kết hợp;
- Điểm truy cập được công cụ nhận diện và không bị loại trừ theo cấu hình;
- Artifact hoặc lớp đã được kết hợp là bản chương trình thật sự được chạy.

Không nên gán đầu ra `[FIELD]...` cho Java/JVM mặc định: chính **cơ chế aspect** tạo ra hành vi bổ sung này.

### 7.2. Điều gì không được suy ra từ ví dụ?

Ví dụ không chứng minh mọi lệnh reflection, `VarHandle`, lệnh native hoặc truy cập do thư viện bên ngoài sinh ra đều phát sinh cùng các join point. Nó cũng không chứng minh mọi bộ weaving đều có quyền sửa mọi lớp, mọi module hoặc mọi đoạn bytecode.

Một số cơ chế kết hợp mã có thể chặn các lệnh đọc/ghi field trong bytecode đã được weaving; một cơ chế chỉ bọc phương thức sẽ không tự đạt khả năng đó. **Phạm vi can thiệp phải được kiểm chứng bằng đường chạy thực tế của công cụ được chọn.**

## 8. Áp dụng vào những mối quan tâm nào?

Không phải mọi phép đọc/ghi thuộc tính đều có ý nghĩa như nhau. Việc chọn AOP hợp lý khi một **chính sách cắt ngang** cần áp dụng nhất quán ở nhiều điểm, còn bất biến và quy tắc nghiệp vụ vẫn có chỗ sở hữu rõ ràng.

| Nhu cầu | Có thể làm bằng advice khi hỗ trợ | Giới hạn cần giữ |
| --- | --- | --- |
| Đếm số lần đọc một nhóm field để phân tích hiệu năng | Tăng bộ đếm mỗi lần field get được quan sát | Không suy ra số lần đọc vật lý từ bộ nhớ/JIT hoặc mọi đường truy cập |
| Theo dõi truy cập trạng thái nhạy cảm | Ghi loại thao tác và bối cảnh tối thiểu | Không ghi giá trị bí mật; cần kiểm soát quyền ở ranh giới đáng tin cậy |
| Chẩn đoán thay đổi trạng thái | Ghi dấu vết lúc field set | Không tự bảo đảm giao dịch hoặc sự kiện nghiệp vụ đã xác nhận |
| Thực thi một chính sách kỹ thuật tại nhiều setter | Chặn lời gọi setter trong phạm vi được chọn | Không thay bất biến miền nghiệp vụ hoặc bỏ sót đường ghi khác |
| Đo tần suất truy cập | Ghi số lượng, lớp/field, khoảng thời gian | Tránh overhead lớn và quan sát trùng |
| Thu thập sự kiện vận hành | Phát thông tin kỹ thuật từ điểm đã hỗ trợ | Bảo vệ độ tin cậy và tránh làm thao tác chính hỏng vì log |

### 8.1. Phân biệt kiểm tra miền nghiệp vụ với quan sát kỹ thuật

Ví dụ “số dư không được âm” là bất biến cần được bảo vệ bởi phần sở hữu dữ liệu, giao dịch hoặc ranh giới miền nghiệp vụ thích hợp. Đưa điều kiện này **chỉ vào field-set advice** có thể không đủ nếu có đường ghi ngoài phạm vi weaving, đặc biệt khi dữ liệu còn thay đổi trong cơ sở dữ liệu hoặc hệ thống khác.

Ngược lại, việc đếm số lần truy cập cho mục đích theo dõi vận hành là ứng viên tự nhiên cho một hành vi cắt ngang, với điều kiện chi phí và việc chọn đúng điểm được kiểm soát.

### 8.2. Ghi nhận field set không đồng nghĩa sự kiện nghiệp vụ

Một nghiệp vụ “đơn hàng chuyển sang đã thanh toán” thường cần thông tin danh tính đơn hàng, khoản tiền, trạng thái giao dịch, thời điểm xác nhận và độ tin cậy của việc phát sự kiện. Một field có thể được gán nhiều lần khi dựng, khôi phục hoặc sao chép đối tượng. Do đó `set(status)` ở cấp bộ nhớ không nhất thiết là **sự kiện chuyển trạng thái đã hoàn tất**.

Để phát sự kiện nghiệp vụ chính xác, hãy xuất phát từ thao tác miền nghiệp vụ hoặc ranh giới giao dịch đáng tin, rồi dùng khía cạnh cho phần hạ tầng phù hợp nếu cần.

## 9. Trường hợp nâng cao và các giới hạn khó nhận thấy

### 9.1. Đọc/ghi field `final`, bất biến và khởi tạo

Một field `final` có quy tắc gán theo ngôn ngữ và quá trình khởi tạo riêng. Một khía cạnh quan sát không nên mặc định có thể **thay một thao tác ghi bất biến** hoặc sửa field sau khi xây dựng đối tượng. Kể cả khi một công cụ báo có thể chọn điểm ghi trong giai đoạn khởi tạo, quyền thay đổi kết quả vẫn bị giới hạn bởi ngữ nghĩa Java và khả năng của bộ weaving.

Với đối tượng bất biến, các giá trị thường được thiết lập một lần rồi chỉ được đọc; việc ghi log mọi lần đọc có thể gây chi phí lớn mà không cung cấp thông tin nghiệp vụ mới.

### 9.2. Trường `static`, trạng thái dùng chung và số lượng sự kiện

Field `static` thuộc về lớp, không thuộc từng instance. Nếu pointcut chọn cả các truy cập `static`, bối cảnh join point có thể không có đối tượng nhận cụ thể. Advice không nên giả định luôn tìm được `this` hay đối tượng đích.

Một field được đọc trong vòng lặp lớn cũng có thể tạo **rất nhiều lần thực thi advice**. Nếu mỗi lần còn ghi log đồng bộ hoặc truy cập mạng, chính chính sách theo dõi có thể làm thay đổi rõ hiệu năng.

### 9.3. Đồng thời, `volatile` và Java Memory Model

Một truy cập field được quan sát bằng advice không tự thay đổi tính nguyên tử hoặc quy tắc hiển thị bộ nhớ của phép đọc/ghi gốc. Với field thường, luồng khác có thể không thấy cập nhật như mong đợi nếu chương trình thiếu quan hệ đồng bộ đúng. Với `volatile`, Java quy định những bảo đảm nhìn thấy và thứ tự nhất định, nhưng **`count++` vẫn không phải phép tăng nguyên tử** chỉ vì field là `volatile`.

Một advice định “đọc cũ rồi ghi mới” ở hai thời điểm không tự khóa được các luồng khác; số đo có thể không tạo thành ảnh chụp trạng thái nhất quán. Khi cần bất biến đồng thời, hãy dùng cấu trúc phù hợp như khóa, thao tác nguyên tử hoặc điều phối giao dịch, tùy miền vấn đề.

### 9.4. Advice tự đọc/ghi cùng field

Xét một advice cho mọi lần đọc `x`, rồi bên trong advice lại đọc `x` để ghi log. Nếu thao tác bên trong cũng khớp pointcut và được weaving, advice có thể tiếp tục gọi chính nó.

Cách phòng tránh có thể gồm:

- Loại trừ mã nằm trong chính aspect hoặc phần hạ tầng theo dõi khỏi pointcut, theo khả năng ngôn ngữ;
- Chọn một tập field hẹp và không thực hiện truy cập đó trong advice;
- Dùng cơ chế bảo vệ tái nhập (reentrancy) khi thực sự cần và thiết kế rõ phạm vi theo luồng/tác vụ;
- Chỉ lấy thông tin join point được cơ chế cung cấp thay vì đọc lại field nhiều lần.

Bảo vệ tái nhập cũng phải cẩn trọng với đa luồng và bất đồng bộ. Một biến cờ toàn cục dùng chung không phải lời giải an toàn cho mọi tình huống.

### 9.5. Hai aspect trên cùng một điểm

Một truy cập có thể khớp cả aspect đo thời gian và aspect kiểm toán. Nếu khía cạnh A bao quanh B, kết quả thường có cấu trúc **A(B(truy cập))**; đổi thứ tự có thể đổi điểm quan sát và cách xử lý lỗi. Không được mặc định thứ tự do tên tệp hoặc thứ tự khai báo quyết định.

Với advice chỉ quan sát, nên giữ nguyên kết quả đọc, giá trị ghi và ngoại lệ. Nếu around advice thực sự thay đổi giá trị, phải quy định rõ việc thay đổi và kiểm tra tác động lên những advice khác.

### 9.6. Reflection, `VarHandle`, native access và đường truy cập ngoài phạm vi

Những đường truy cập ngoài cú pháp field trực tiếp có thể thực hiện thông qua API đặc biệt. Một bộ weaving dựa trên **điểm bytecode của mã đã được biến đổi** không nhất thiết nhìn thấy thao tác diễn ra trong mã thư viện, JVM hoặc native. Tùy công cụ, một phần những đường ấy có thể can thiệp được, nhưng **không được giả định** chỉ vì tên field giống nhau.

Khi chính sách là điều kiện an toàn quan trọng, phải thống kê các đường có thể chạm trạng thái và kiểm tra trường hợp bypass. Nếu không thể kiểm soát chúng, cần một ranh giới bảo vệ khác.

### 9.7. Ảnh hưởng của compiler/JIT và phạm vi đo đạc

Source code có thể được biên dịch thành các lệnh bytecode nhất định; JVM có thể tối ưu trong lúc thực thi. Advice thêm hành vi quan sát được có thể thay đổi tối ưu và chi phí. Việc đếm advice ở các field join point **không tương đương phép đo số lần truy cập RAM hoặc CPU cache**.

Không sử dụng số đếm get/set như thước đo hiệu năng cấp phần cứng. Muốn phân tích hiệu năng, phải có thí nghiệm và công cụ đo phù hợp với mục đích.

## 10. Kiểm thử việc can thiệp đọc/ghi có hiệu lực

Một chính sách field get/set cần được kiểm tra bằng **mã thực sự thực thi thao tác truy cập** và trong đúng cấu hình weaving. Chỉ thấy pointcut được khai báo là chưa đủ.

| Ca kiểm thử | Điều cần xác minh |
| --- | --- |
| Ghi field được chọn | Advice ghi chạy đúng số lần và đúng thời điểm |
| Đọc field được chọn | Advice đọc chạy đúng số lần, không thay kết quả ngoài dự kiến |
| Field ngoài pointcut | Không phát sinh advice ngoài phạm vi |
| Getter có nhiều lần đọc field | Phân biệt số lần gọi getter với số field get |
| Setter từ chối dữ liệu | Không báo “ghi thành công” nếu setter thất bại |
| Ghi trong constructor/đường nội bộ | Quan sát đúng phạm vi mà cơ chế weaving hỗ trợ |
| Lời gọi qua proxy và bypass proxy | Hiểu điểm nào chỉ là method interception |
| Đọc/ghi đồng thời | Không lẫn bối cảnh, không hứa tính nguyên tử sai |
| Advice tự tham chiếu field | Không có vòng lặp hay đếm tăng bất thường |
| Chạy bằng artifact khác hoặc thiếu weaving | Phát hiện chính sách không được áp dụng |

### 10.1. Quy trình chẩn đoán khi thiếu sự kiện

1. **Phân loại thao tác:** đây là field get/set hay lời gọi getter/setter?
2. **Kiểm tra mô hình join point:** công cụ có hỗ trợ loại thao tác đang xét không?
3. **Kiểm tra pointcut:** tên field, kiểu, nơi khai báo, điều kiện động và loại trừ có khớp thực tế không?
4. **Kiểm tra phạm vi weaving:** mã thực hiện truy cập có được biên dịch/biến đổi/nạp đúng quy trình không?
5. **Kiểm tra đường gọi:** có bypass proxy, đọc trong lớp chưa được weaving, hoặc dùng API truy cập đặc biệt không?
6. **Kiểm tra nhiều khía cạnh:** có ghi trùng, chặn trước thao tác hay lỗi trong advice không?
7. **Kiểm tra kết quả:** số đo, giá trị sau ghi, ngoại lệ và ảnh hưởng đối với chức năng nghiệp vụ có đúng không?

### 10.2. Phân biệt quan sát với kiểm soát

Nếu mục tiêu là **chẩn đoán**, việc ghi nhận thiếu một nhóm truy cập có thể làm số liệu sai. Nếu mục tiêu là **kiểm soát quyền**, bỏ sót một đường truy cập có thể nghiêm trọng hơn: một lớp advice không bao trùm tất cả đường ghi không nên được xem là ranh giới bảo vệ duy nhất.

Từ đó, hãy chọn mô hình AOP dựa trên phạm vi và độ tin cậy yêu cầu, không chỉ dựa trên vẻ tiện lợi của cú pháp pointcut.

## 11. Lỗi thường gặp và cách sửa

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Coi mọi getter là một field get | Hiểu sai số lượng và vị trí join point | Tách khái niệm lời gọi phương thức và truy cập lưu trữ |
| Cho rằng proxy chặn được `obj.field` | Đường trực tiếp không được quan sát | Chọn cơ chế thật sự hỗ trợ field access hoặc thay API truy cập |
| Ghi “đã thay đổi” ngay trước field set | Có thể báo sự kiện chưa thành công | Phân biệt ý định ghi, thực thi ghi và xác nhận nghiệp vụ |
| Dùng pointcut chọn toàn bộ field | Số sự kiện quá lớn, rò rỉ dữ liệu | Giới hạn field/miền và loại trừ dữ liệu nhạy cảm |
| Advice tự đọc lại field đang bị giám sát | Recursion, lặp log, tràn stack | Giảm phạm vi pointcut hoặc loại trừ mã advice |
| Coi field set là giao dịch nguyên tử | Báo chuyển trạng thái sai dưới tải đồng thời | Đặt tính nhất quán ở nơi sở hữu bất biến |
| Dùng advice đổi kết quả đọc mà không xét nghiệp vụ | Người gọi nhìn thấy giá trị bất ngờ | Chỉ thay kết quả theo hợp đồng rõ, ưu tiên giữ nguyên với chính sách quan sát |
| Không kiểm tra đường đặc biệt | Thiếu sự kiện hoặc bỏ qua kiểm soát | Thống kê và thử các đường thực thi được hỗ trợ |
| Dựa vào tên aspect để suy ra khả năng | Hiểu sai mô hình của công cụ | Xác minh join point thực, giai đoạn weaving và artifact |

## 12. Bài tập vận dụng có đáp án

### Bài 1: Đếm các điểm can thiệp

Cho đoạn mã:

```java
class Box {
    int value;
}

Box box = new Box();
box.value = 3;
box.value++;
System.out.println(box.value);
```

Nếu **mô hình có hỗ trợ field get/set** và toàn bộ mã truy cập trên được weaving, có bao nhiêu điểm truy cập `value` được quan sát theo ý tưởng bytecode thông thường?

**Gợi ý đáp án:** Câu gán `box.value = 3` tạo một field set. Câu `box.value++` cần đọc giá trị cũ rồi ghi giá trị tăng lên, nên thường có một field get và một field set. Câu in `box.value` có một field get. Tổng: **2 lần đọc, 2 lần ghi**. Đây là cách phân tích mã Java trong trường hợp tiêu chuẩn và weaving phù hợp, không phải cam kết mọi công cụ/đường chạy có cùng số event nếu phạm vi điểm nối khác nhau.

### Bài 2: Phân biệt property và field

Một lớp khai báo `private int price`, nhưng `getPrice()` trả về `price + tax`. Pointcut chọn **lời gọi getter** có tương đương pointcut chọn **đọc field `price`** không?

**Đáp án:** Không. Getter trả về giá đã cộng thuế, còn field chứa giá gốc; getter có thể đọc nhiều field hoặc thực hiện tính toán. Hai pointcut có phạm vi và dữ liệu quan sát khác nhau.

### Bài 3: Tình huống proxy

Một chương trình dùng proxy để ghi log khi `setStatus(...)` được gọi. Phương thức `resetInternal()` trên đối tượng đích tự gán `this.status = "NEW"`. Vì sao không thấy log của setter?

**Đáp án:** Vì mã đang ghi field trực tiếp trong lớp, không gọi setter qua proxy. Proxy theo dõi lời gọi setter không tự theo dõi lệnh gán field; chỉ một cơ chế có điểm can thiệp field thực sự và weaving đúng phạm vi mới có thể quan sát đường này.

### Bài 4: Sự kiện nghiệp vụ

Một hệ thống phát sự kiện “Đơn đã giao” từ advice mỗi khi field `status` được ghi thành `DELIVERED`. Có thể xem đây là bằng chứng đơn hàng đã giao thành công không?

**Đáp án:** Chưa đủ. Trạng thái trong bộ nhớ có thể là tạm thời, được khôi phục từ dữ liệu cũ hoặc bị sửa trong tác vụ không thành công. Cần xác định sự kiện nghiệp vụ ở ranh giới có tính nhất quán và xác nhận tương ứng.

### Bài 5: Đồng thời

Hai luồng cùng thay đổi một field `counter`; advice đọc “giá trị trước” và “giá trị sau” để ghi nhận. Điều đó có bảo đảm mỗi log là một lần chuyển trạng thái duy nhất không?

**Đáp án:** Không. Hai lần đọc trong advice có thể xen kẽ với thay đổi của luồng khác. Phải thiết kế thao tác cập nhật nguyên tử hoặc đồng bộ phù hợp nếu yêu cầu một cặp trước–sau chính xác.

## 13. Ghi nhớ

- Một số mô hình AOP coi **đọc field (get)** và **ghi field (set)** là join point; nhiều cơ chế khác chỉ chặn **lời gọi phương thức**.
- **Field access** khác **getter/setter invocation**; một thuộc tính logic thậm chí có thể không có field tương ứng.
- Pointcut xác định **đúng vị trí đọc/ghi**, advice xác định **hành vi kèm theo**, weaving làm hành vi ấy có hiệu lực tại các điểm thực sự được hỗ trợ.
- Theo dõi field set **không tự bảo đảm** bất biến nghiệp vụ, giao dịch, tính nguyên tử, quyền truy cập hoặc sự kiện miền nghiệp vụ đã xác nhận.
- Muốn dùng cơ chế này an toàn, phải kiểm tra **phạm vi weaving, đường bypass, vòng lặp trong advice, đồng thời, dữ liệu nhạy cảm và chi phí đo đạc**.
- Khi điểm đọc/ghi không được công nghệ hỗ trợ, hãy dùng ranh giới phương thức hoặc một thiết kế tường minh phù hợp thay vì giả định hệ AOP tự động thấy mọi truy cập trạng thái.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-004">Khởi tạo lớp, khởi tạo đối tượng và thứ tự thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo lớp, khởi tạo đối tượng và thứ tự thực thi trong AOP

## 1. Vì sao khởi tạo là một nhóm điểm can thiệp đặc biệt?

Trong Aspect-Oriented Programming (AOP), **join point** là một điểm có thể được nhận diện trong quá trình thực thi để áp dụng hành vi bổ sung (**advice**) theo một quy tắc chọn (**pointcut**). Ngoài phương thức thông thường, vòng đời lớp và đối tượng tạo ra một nhóm join point đáng chú ý: **khởi tạo lớp**, **lời gọi constructor**, **thực thi constructor**, **giai đoạn trước khởi tạo đối tượng** và **giai đoạn khởi tạo đối tượng**.

Sự khác biệt quan trọng là **class initialization** diễn ra theo vòng đời của một lớp đã được nạp bởi một class loader, còn **object initialization** diễn ra cho từng đối tượng. Một lớp có thể được khởi tạo một lần rồi tạo hàng triệu đối tượng; vì thế advice tại điểm khởi tạo lớp và advice quanh constructor có phạm vi, tần suất, trạng thái đối tượng và rủi ro rất khác nhau.

Hãy đặt ba câu hỏi trước khi thiết kế một aspect liên quan đến khởi tạo:

1. **Cần quan sát một lớp trở nên sẵn sàng hay mỗi lần tạo ra một đối tượng?**
2. **Cần chặn ở phía gọi lệnh tạo đối tượng, bên trong constructor hay ở toàn bộ giai đoạn khởi tạo?**
3. **Tại thời điểm advice chạy, dữ liệu của đối tượng đã hợp lệ chưa, và có thể truy cập an toàn không?**

Các tên join point và khả năng chọn bằng pointcut phụ thuộc ngôn ngữ/công cụ AOP. Một số bộ dệt bytecode hỗ trợ nhiều điểm vòng đời; một hệ thống chỉ chặn phương thức qua proxy có thể **không** cung cấp join point ở khối static initializer hay constructor. Bài này trình bày mô hình AOP nói chung, dùng Java 17 làm nền tảng để hiểu trình tự thực thi và nêu rõ giới hạn khi diễn giải theo công cụ cụ thể.

## 2. Bốn việc thường bị gọi nhầm là “khởi tạo”

Trước khi nói đến advice, cần phân biệt chính xác những hoạt động ở runtime Java:

| Hoạt động | Ý nghĩa | Phạm vi | Có đồng nghĩa với chạy constructor không? |
|---|---|---|---|
| **Loading (nạp lớp)** | Tìm/đọc hoặc tạo biểu diễn nhị phân để JVM có định nghĩa của lớp | Theo danh tính lớp và class loader | Không |
| **Linking (liên kết)** | Xác minh, chuẩn bị và có thể giải quyết các tham chiếu ký hiệu | Theo lớp | Không |
| **Class initialization** | Chạy các biểu thức khởi tạo trường static và khối static initializer của lớp theo quy tắc ngôn ngữ | Mỗi lần một lớp được khởi tạo trong danh tính runtime tương ứng | Không |
| **Object creation và initialization** | Cấp phát đối tượng, thiết lập giá trị mặc định, chạy chuỗi constructor và các bước khởi tạo instance | Theo từng đối tượng | Có liên quan trực tiếp |

**Loading không tự đồng nghĩa với initialization.** Một lớp có thể đã được nạp, chuẩn bị và chưa chạy phần static. Tương tự, **class initialization không tạo sẵn một instance**: việc gọi một phương thức static có thể khiến lớp được khởi tạo mà chưa gọi constructor nào.

Ngược lại, một biểu thức **new SomeClass(...)** thường yêu cầu lớp liên quan được khởi tạo trước khi đối tượng đầu tiên được tạo. Khi lớp đó đã khởi tạo xong, các lần tạo instance tiếp theo không chạy lại phần static chỉ vì gọi constructor lần nữa.

### 2.1. Danh tính lớp trong JVM

Từ góc độ JVM, tên lớp đơn thuần chưa đủ xác định một danh tính runtime: **tên nhị phân + class loader định nghĩa lớp** là yếu tố quan trọng. Hai class loader khác nhau có thể định nghĩa hai lớp có cùng tên; mỗi định nghĩa có quá trình khởi tạo riêng. Vì vậy, diễn đạt “static initializer chạy đúng một lần trên toàn ứng dụng” là thiếu chính xác trong hệ thống có nhiều class loader.

Cách diễn đạt chặt hơn: một lớp được khởi tạo theo quy tắc của JVM cho **mỗi danh tính lớp**; JVM đồng bộ hóa để không có hai luồng tùy ý cùng thực hiện khởi tạo một lớp như hai lần độc lập. Những lần truy cập sau khi khởi tạo thành công sử dụng trạng thái đã khởi tạo.

## 3. Class initialization: khi nào xảy ra và thứ tự nào?

Trong Java, các tình huống **active use** điển hình khiến lớp được khởi tạo trước khi tiếp tục gồm:

- Tạo một instance của lớp bằng **new**.
- Gọi một phương thức **static** được khai báo trong lớp.
- Gán hoặc đọc trường **static** được khai báo trong lớp, ngoại trừ một số truy cập hằng compile-time theo quy tắc ngôn ngữ.
- Một số thao tác phản xạ hay cơ chế runtime có ngữ nghĩa yêu cầu khởi tạo lớp.

Khi khởi tạo một lớp, JVM phải khởi tạo các superclass cần thiết trước; trong Java hiện đại, các superinterface khai báo default method cũng có thể nằm trong chuỗi khởi tạo bắt buộc. Sau đó, các biểu thức khởi tạo **static field** và **static initializer block** trong từng lớp chạy theo **thứ tự văn bản của lớp đó**, khi chúng được thực thi như một phần của quá trình khởi tạo.

Ví dụ lớp:

~~~java
class Counter {
    static int first = announce("static field");
    static {
        announce("static block");
    }

    static int announce(String message) {
        System.out.println(message);
        return 1;
    }
}
~~~

Đây là đoạn Java minh họa, chưa phải chương trình độc lập có **main**. Lần khởi tạo **Counter** sẽ in:

~~~text
static field
static block
~~~

Những lần gọi constructor của **Counter** về sau không tự chạy lại hai dòng static. Nếu chưa có hoạt động thực sự khiến lớp khởi tạo, chỉ việc một tên lớp xuất hiện trong mã nguồn không bảo đảm các dòng đã xuất hiện.

### 3.1. Khác biệt giữa “đã nạp” và “đã khởi tạo”

Chương trình Java 17 sau cho thấy tham chiếu hằng compile-time và việc nạp không yêu cầu khởi tạo ngay, trong khi đọc một trường static không phải hằng lại có thể kích hoạt initialization.

Tạo file **LoadingVsInitializing.java**:

~~~java
public class LoadingVsInitializing {
    static class Marker {
        static final int CONSTANT = 7;
        static int value = init();

        static int init() {
            System.out.println("Marker initialized");
            return 9;
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        System.out.println("constant=" + Marker.CONSTANT);

        Class.forName(
                Marker.class.getName(),
                false,
                Marker.class.getClassLoader());

        System.out.println("loaded without initialization");
        System.out.println("value=" + Marker.value);
        System.out.println("again=" + Marker.value);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 LoadingVsInitializing.java
java LoadingVsInitializing
~~~

Stdout:

~~~text
constant=7
loaded without initialization
Marker initialized
value=9
again=9
~~~

Giải thích từng điểm:

1. **Marker.CONSTANT** là constant variable với giá trị biết được ở compile time; ở ví dụ này đọc hằng không bắt buộc khởi tạo **Marker**.
2. **Class.forName(name, false, loader)** yêu cầu tìm/nạp lớp theo API nhưng cờ **false** chỉ rõ không khởi tạo lớp chỉ bởi thao tác này. Biểu thức **Marker.class** cũng không buộc chạy static initialization của **Marker**.
3. Đọc **Marker.value** khiến lớp được khởi tạo; lệnh **init()** chạy rồi in đúng một lần.
4. Lần đọc **Marker.value** tiếp theo chỉ lấy giá trị 9 đã được tính.

Điểm cần nhớ với AOP: nếu pointcut nhắm vào **class initialization**, advice tương ứng không được suy diễn là chạy lúc lớp đơn thuần được nhắc tên, lúc reflection chỉ nạp lớp, hay mỗi lần đọc một trường. Nó gắn với **sự kiện khởi tạo lớp được công cụ hỗ trợ**, và sự kiện này có thời điểm xác định theo ngữ nghĩa JVM.

## 4. Object creation: từ cấp phát đến constructor hoàn tất

Đối với một biểu thức tạo đối tượng Java thông thường, nên hình dung các giai đoạn:

1. **Đánh giá biểu thức tạo và đối số** theo quy tắc đánh giá của Java; nếu việc đánh giá đối số thất bại, có thể chưa có đối tượng để khởi tạo.
2. **Đảm bảo lớp cần thiết đã được khởi tạo** trước khi thực sự sử dụng theo quy tắc JVM.
3. **Cấp phát vùng nhớ cho instance**, các trường instance nhận giá trị mặc định: số bằng 0, boolean bằng false, reference bằng null...
4. **Chạy chuỗi constructor** qua lời gọi **this(...)** hoặc **super(...)**; mỗi cấp trong hệ kế thừa thực hiện các bước khởi tạo thích hợp.
5. **Khởi tạo instance field và initializer block** trong lớp liên quan theo thứ tự văn bản tại vị trí được ngôn ngữ xác định.
6. **Thực thi thân constructor** sau các bước cần thiết; khi thành công, caller nhận reference đến đối tượng đã xây dựng xong theo lời gọi đó.

Bản mô tả trên là **sơ đồ sư phạm**, không phải danh sách instruction hay mốc join point phổ quát cho mọi weaver. Khi nghiên cứu thứ tự rất chi tiết, cần dựa vào đặc tả ngôn ngữ và quy ước của công cụ AOP cụ thể.

### 4.1. Khác biệt giữa cấp phát và khởi tạo thành công

Có một đối tượng đã được cấp phát không có nghĩa mọi bất biến của đối tượng đều đúng. Trong quá trình constructor, nhiều trường có thể còn giá trị mặc định; superclass đã chạy một phần, trong khi subclass chưa chạy khối khởi tạo riêng.

Đây là lý do advice chạy ở giai đoạn rất sớm **không nên tự ý truy cập trạng thái con**, gọi phương thức có thể bị override, hoặc đăng ký reference chưa hoàn tất vào một registry dùng chung.

Một phương thức có thể được dispatch động đến subclass dù constructor của superclass đang chạy. Nếu method bị override dựa vào trường chỉ được khởi tạo sau này, kết quả có thể bất ngờ. Weaving không loại bỏ quy tắc dispatch này.

### 4.2. Constructor chaining: this(...) khác super(...)

- **this(...)** chuyển sang constructor khác trong cùng lớp. Nó không có nghĩa tạo thêm một đối tượng khác.
- **super(...)** gọi constructor superclass trong quá trình xây dựng đối tượng hiện tại.
- Một lần **new Child()** có thể khiến nhiều constructor thực thi do chuỗi **this/super**, nhưng kết quả là **một instance Child**, trừ các thao tác cấp phát khác do mã chủ động thực hiện.

Ở Java 17, lời gọi constructor tường minh dạng **this(...)**/**super(...)** nằm ở vị trí được ngôn ngữ quy định. Khi tham gia một vòng đời tạo đối tượng có aspect, cần phân biệt **constructor được gọi từ biểu thức new** với các constructor được thực thi do chaining.

## 5. Ví dụ Java 17: thứ tự superclass, subclass, static và instance

Chương trình sau có hai lớp lồng nhau **Parent** và **Child**. Mỗi lớp có static field initializer, static block, instance field initializer, instance block và constructor. Lớp con có hai constructor nối nhau qua **this("default")**.

Tạo file **InitializationOrderDemo.java**:

~~~java
public class InitializationOrderDemo {
    static int traceValue(String event, int value) {
        System.out.println(event);
        return value;
    }

    static void trace(String event) {
        System.out.println(event);
    }

    static class Parent {
        static int parentStatic = traceValue("Parent.static field", 1);
        static {
            trace("Parent.static block");
        }

        int parentField = traceValue("Parent.instance field", 2);
        {
            trace("Parent.instance block");
        }

        Parent() {
            trace("Parent.constructor");
        }
    }

    static class Child extends Parent {
        static int childStatic = traceValue("Child.static field", 3);
        static {
            trace("Child.static block");
        }

        int childField = traceValue("Child.instance field", 4);
        {
            trace("Child.instance block");
        }

        Child() {
            this("default");
            trace("Child.constructor no-args");
        }

        Child(String source) {
            super();
            trace("Child.constructor arg=" + source);
        }
    }

    public static void main(String[] args) {
        trace("main: begin");
        trace("main: first object");
        new Child();
        trace("main: second object");
        new Child("explicit");
        trace("main: end");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 InitializationOrderDemo.java
java InitializationOrderDemo
~~~

Stdout chính xác:

~~~text
main: begin
main: first object
Parent.static field
Parent.static block
Child.static field
Child.static block
Parent.instance field
Parent.instance block
Parent.constructor
Child.instance field
Child.instance block
Child.constructor arg=default
Child.constructor no-args
main: second object
Parent.instance field
Parent.instance block
Parent.constructor
Child.instance field
Child.instance block
Child.constructor arg=explicit
main: end
~~~

Giải thích:

- Trước lần tạo **Child** đầu tiên, **Parent** được khởi tạo ở phần static rồi đến **Child**. Trong mỗi lớp, static field initializer và static block chạy theo thứ tự khai báo.
- Tạo **Child** gọi chuỗi constructor; phần instance của **Parent** chạy trước thân **Parent()**, rồi phần instance của **Child** chạy trước thân **Child(String)**.
- **Child()** ủy quyền cho **Child("default")**. Khi constructor nhận tham số kết thúc, điều khiển quay lại thân **Child()**, vì thế dòng **Child.constructor no-args** nằm sau dòng **Child.constructor arg=default**.
- Ở lần tạo thứ hai, các dòng static không lặp; các dòng instance và constructor lặp vì đó là **một đối tượng mới**.
- Kết quả này quan sát **quy tắc khởi tạo Java thuần**, chưa áp dụng aspect. Việc thêm advice có thể tạo thêm dòng hoặc tác dụng phụ tại một số giai đoạn, tùy bộ dệt và pointcut.

Sơ đồ của lần **new Child()** đầu:

~~~text
main: first object
    ↓
Class initialization
    Parent: static field → static block
    Child:  static field → static block
    ↓
Object initialization
    Parent: instance field → instance block → Parent()
    Child:  instance field → instance block → Child(String)
    Child:  phần thân còn lại của Child()
    ↓
Quay lại caller
~~~

Sơ đồ thứ hai:

~~~text
main: second object
    ↓
Không chạy lại static initializers đã hoàn tất
    ↓
Parent instance initialization + Parent()
    ↓
Child instance initialization + Child(String)
    ↓
Quay lại caller
~~~

Các log từ **traceValue** chỉ để nhìn rõ tiến trình; thứ tự thực sự còn phụ thuộc mã khác chạy trong initializer, constructor và superclass. Không nên suy ra rằng class initializer của mọi lớp trong toàn chương trình có một thứ tự toàn cục duy nhất, bất kể luồng và quan hệ phụ thuộc.

## 6. Các loại join point liên quan đến khởi tạo

Điều quan trọng nhất của bài là **mỗi loại join point mô tả một thời điểm khác nhau**. Trong các mô hình AOP có hỗ trợ điểm vòng đời chi tiết, có thể gặp các nhóm sau.

| Loại join point | Nhắm vào điều gì? | Điểm nhìn | Trạng thái đối tượng |
|---|---|---|---|
| **Class/static initialization** | Thực thi initializer ở mức lớp | Lớp đang được khởi tạo | Không gắn với một target instance cụ thể |
| **Constructor call** | Một lời gọi tạo đối tượng tại phía caller | Ranh giới nơi yêu cầu đối tượng mới | Có thể chỉ quan sát reference sau khi lời gọi tạo thành công |
| **Constructor execution** | Thực thi phần constructor cụ thể | Bên trong constructor được chọn | Đối tượng có thể chưa hoàn tất tất cả bước của subclass |
| **Object pre-initialization** | Khoảng trước phần khởi tạo instance của một lớp trong chuỗi xây dựng | Vòng đời đối tượng trước giai đoạn khởi tạo của lớp đó | Không được giả định trường instance đã hợp lệ |
| **Object initialization** | Giai đoạn thực hiện khởi tạo instance của lớp trong chuỗi constructor | Vòng đời object | Trạng thái thay đổi dần; chưa nhất thiết có đủ bất biến tại mọi điểm |
| **Field set/get** | Thao tác gán/đọc một trường, nếu mô hình hỗ trợ | Vị trí truy cập trường | Không đồng nhất với toàn bộ sự kiện constructor/initialization |

**Tính khả dụng phụ thuộc công cụ.** Một mô hình AOP chỉ intercept lời gọi interface thường không có đủ các join point trên. Một số ngôn ngữ/bộ dệt chi tiết còn phân biệt **constructor call** từ biểu thức tạo đối tượng với lời gọi **this/super** trong chuỗi constructor. Không được suy luận rằng mọi constructor chaining đều tạo thêm một constructor-call join point giống như **new**.

### 6.1. Constructor call: ai đang yêu cầu đối tượng?

Đây là góc nhìn từ mã caller, chẳng hạn:

~~~java
Child value = new Child();
~~~

Một pointcut nhắm vào lời gọi constructor có thể dùng để quan sát **vị trí yêu cầu đối tượng**, đối số tại biên gọi và kết quả reference khi lời gọi thành công, nếu ngôn ngữ AOP cung cấp những khả năng đó.

Khi có nhiều nơi gọi **new Child()**, một pointcut chọn call site có thể chỉ bao phủ một số caller hoặc một vùng mã xác định. Chặn một factory duy nhất cũng tương tự về góc nhìn: nó quan sát lời gọi qua factory, nhưng không tự bảo đảm quan sát lệnh **new** viết trực tiếp ở nơi khác.

**Thận trọng:** constructor call không phải là sự kiện static initialization và cũng không phải toàn bộ hoạt động bên trong constructor. Không nên mô tả nó là “phương thức constructor chuẩn bị chạy tại thân constructor” mà bỏ qua phía caller.

### 6.2. Constructor execution: constructor nào thực sự chạy?

Điểm thực thi constructor tập trung vào constructor được thực hiện. Trong một chuỗi **Child() → Child(String) → Parent()**, nhiều thân constructor có thể thực thi trên **cùng một đối tượng**.

Nếu advice đếm mỗi constructor execution là một đối tượng mới, số đếm có thể bị tăng sai. Đếm **số lượt thực thi constructor** không nhất thiết bằng **số instance được tạo**.

Mặt khác, advice gắn với execution cần biết thời điểm chính xác công cụ coi là “bắt đầu thực thi”: một số mô hình không bao quát các bước **this/super** theo cùng cách như phần thân constructor. Khi cần theo dõi toàn bộ vòng đời, chọn constructor execution đơn thuần có thể chưa đủ.

### 6.3. Object pre-initialization và object initialization

Hai khái niệm này cần được hiểu là **các giai đoạn vòng đời**, không phải tên khác của constructor call.

Một mô hình chi tiết như AspectJ phân biệt:

- **Pre-initialization:** khoảng trước khi phần khởi tạo instance của một lớp chạy, gắn với quá trình bắt đầu chuỗi constructor và bước chuẩn bị đi lên superclass.
- **Initialization:** khoảng khởi tạo một lớp trong quá trình tạo object, bao gồm các initializer instance và đường constructor liên quan sau khi superclass phù hợp đã được xử lý.

Những mô tả này phụ thuộc định nghĩa hình thức của từng ngôn ngữ AOP; ở ví dụ cụ thể, phạm vi một join point có thể chứa các join point khác. Nếu công cụ chỉ hỗ trợ method execution thì không thể tự lấy các điểm này bằng cách đổi tên pointcut.

**Không nên ghi một thứ tự advice tuyệt đối** như “pre-initialization → initialization → constructor execution” cho mọi công cụ hoặc mọi chuỗi constructor. Hãy xác minh thứ tự lồng nhau, advice precedence và phạm vi do công cụ định nghĩa.

### 6.4. Static initialization là join point của lớp, không phải của đối tượng

Khi khởi tạo lớp, có thể chưa có object nào được tạo. Vì vậy advice tại điểm này không thể mặc nhiên yêu cầu **this** của một target object để đọc trường instance.

Một bộ dệt cho phép can thiệp static initializer có thể nhận biết class đang khởi tạo; nhưng chính sự kiện đó không đồng nghĩa được gọi lại mỗi lần **new**. Nếu đặt logic kiểm toán “mỗi đơn hàng được tạo” tại static initialization của lớp đơn hàng, kết quả sẽ sai quy mô ngay từ thiết kế.

## 7. Cách đọc mô hình pointcut và advice tại các thời điểm khác nhau

Giả sử cần quan sát lớp **Child** và việc tạo instance của nó. Sau đây là **giả mã AOP độc lập công cụ**, không phải mã Java để biên dịch:

~~~text
aspect ConstructionTrace
    pointcut classReadyStage =
        static-initialization of Child

    pointcut callerCreatesChild =
        constructor-call Child(...)

    pointcut constructorsRunning =
        constructor-execution Child(...)

    before classReadyStage:
        report("initializing Child type")

    before callerCreatesChild:
        report("caller requests Child")

    after-returning callerCreatesChild:
        report("caller received Child")

    before constructorsRunning:
        report("a Child constructor executes")
end
~~~

Ý nghĩa logic:

- **static-initialization of Child** hướng đến khởi tạo lớp Child. Nếu lớp đã khởi tạo từ trước thì một lần tạo object sau không tạo thêm sự kiện khởi tạo lớp.
- **constructor-call Child(...)** quan sát biên caller yêu cầu Child, không có nghĩa quan sát mọi lời gọi **this/super** theo cùng kiểu.
- **constructor-execution Child(...)** có thể quan sát từng constructor được thực thi, kể cả các constructor trong chuỗi theo quy tắc của mô hình.
- **after-returning constructor-call** chỉ chạy khi thao tác tạo từ điểm caller quan sát trả về thành công; nếu constructor ném ngoại lệ, không có reference mới được trả về theo đường đó.

Giả mã này **không dự đoán chính xác thứ tự các dòng** nếu chưa xác định điểm weave, cách xử lý chuỗi constructor và precedence của các advice. Sự an toàn nằm ở chỗ phân biệt vai trò từng điểm; trong hệ thống thực, kiểm tra thứ tự bằng một chương trình nhỏ có thiết lập weaver cụ thể.

### 7.1. Đếm số đối tượng đúng chỗ

Bài toán: mỗi instance được tạo thành công cần tăng một counter đúng một lần.

- Counter gắn với **static initialization** chỉ tăng theo số lần lớp được khởi tạo, không theo số object.
- Counter gắn với **mọi constructor execution** có nguy cơ tính cả các bước **this(...)** hoặc nhiều constructor liên quan đến cùng object.
- Counter gắn với **constructor call từ những vị trí được chọn** có thể đếm thành công tại biên caller; nhưng có thể bỏ sót những lệnh tạo ngoài phạm vi pointcut.
- Một điểm theo dõi gắn với **hoàn tất khởi tạo object** được ngôn ngữ AOP hỗ trợ có thể thích hợp hơn; vẫn phải kiểm tra ý nghĩa “một lần” theo ranh giới lớp/superclass và quy tắc chọn.

Trường hợp muốn số lượng **đối tượng sống trong bộ nhớ tại thời điểm hiện tại** là vấn đề khác: “đã tạo thành công” không đồng nghĩa “còn sống”, và việc theo dõi lifetime/GC không thể suy ra chỉ từ constructor advice.

## 8. Thứ tự advice so với thứ tự khởi tạo Java

Có hai lớp quy tắc cần giữ riêng:

1. **Quy tắc nền của ngôn ngữ:** superclass được xử lý trước phần instance subclass; static initialization có trình tự riêng; constructor chaining chạy theo quy định Java.
2. **Quy tắc của mô hình AOP:** pointcut chọn join point nào, advice được dệt ở đâu, và advice chạy trước/sau/bao quanh với precedence nào.

Một phép đo tại constructor call có thể bắt đầu trước khi một constructor execution join point được thực hiện; nhưng thứ tự tương đối với initialization của lớp, đánh giá đối số, hoặc các advice khác cần xác định trong công cụ tương ứng. Bộ dệt có thể tạo cấu trúc lồng nhau, nên nhật ký thường có dạng:

~~~text
before của một biên gọi
    trước/gần điểm thực thi được chọn
        hành vi khởi tạo gốc
    sau điểm thực thi được chọn
after của một biên gọi
~~~

Đây là **sơ đồ ý tưởng**, không phải stdout chuẩn chung của mọi hệ thống AOP.

### 8.1. Advice after có nhiều ngữ nghĩa

Đừng dùng một từ “after” cho mọi trạng thái mà không phân biệt:

| Kiểu hành vi | Khi nào có ý nghĩa? | Rủi ro nếu hiểu sai |
|---|---|---|
| **Before** | Trước join point mà mô hình định nghĩa | Object có thể chưa hợp lệ |
| **After returning** | Join point kết thúc theo đường bình thường | Dễ nhầm với “giao dịch nghiệp vụ đã commit” |
| **After throwing** | Join point hoàn tất bằng ngoại lệ | Phải hiểu throwable ở ranh giới nào |
| **After/finally** | Khi rời join point theo ngữ nghĩa hỗ trợ, kể cả thành công/lỗi | Có thể ghi “đã tạo” dù constructor thất bại |
| **Around** | Bao lấy và có thể điều phối join point | Có thể bỏ qua, lặp hoặc thay đổi ngữ nghĩa xây dựng object |

Một callback chạy khi rời constructor không chứng minh toàn bộ quá trình tạo object đã hoàn tất nếu vẫn còn constructor trong chuỗi phía ngoài phải chạy. Nếu mục tiêu là “đối tượng hoàn tất xây dựng”, hãy chọn **đúng ranh giới** và thử nghiệm ca constructor chaining.

### 8.2. Thứ tự nhiều aspect không được suy từ tên file

Giả sử cả **ValidationTrace** và **ConstructionMetric** cùng chọn constructor. Nếu mọi advice đều có side effect, thứ tự chúng chạy có thể làm thay đổi log, metric và cả kết quả nếu một advice ném ngoại lệ.

Không có quy tắc chung “file nào được biên dịch trước sẽ chạy advice trước”. Precedence/order là phần thuộc ngữ nghĩa và cấu hình công cụ. Khi cần bảo đảm, khai báo thứ tự hợp lệ theo công cụ và viết kiểm thử xác minh đúng hành vi kỳ vọng.

## 9. Sơ đồ so sánh ba quan sát khác nhau trên một thao tác new

Xét một caller:

~~~java
Child child = new Child();
~~~

Có thể đặt các điểm quan sát khái niệm ở ba phạm vi:

~~~text
CALLER
   │
   ├─ A. constructor-call boundary (nếu cơ chế hỗ trợ)
   │       │
   │       ├─ Class initialization, khi lớp chưa initialized
   │       │
   │       └─ Cấp phát và chuỗi constructor của một object
   │               ├─ B. pre-initialization / initialization
   │               │       của các lớp trong chuỗi
   │               └─ C. constructor-execution
   │                       của các constructor được thực thi
   │
   └─ Caller nhận Child reference nếu thành công
~~~

**Sơ đồ chỉ thể hiện phạm vi quan sát, không áp đặt rằng A luôn xảy ra trước static initializer trong mọi hình thức biểu diễn join point.** Một bộ dệt có thể mô tả các biên và điểm thực thi theo thứ tự riêng, phù hợp với ngữ nghĩa cụ thể.

Dưới góc nhìn thiết kế:

- A giúp gắn hành vi theo **vị trí yêu cầu** một đối tượng.
- B giúp quan sát **vòng đời khởi tạo** ở cấp object/class.
- C giúp gắn hành vi theo **constructor được thực thi**.

Nếu policy nhằm bảo đảm “mọi object thuộc kiểu X đều được khởi tạo hợp lệ”, chặn một số caller chưa chắc đủ. Nếu policy nhằm ghi lại “caller nào yêu cầu tạo object”, quan sát thân constructor có thể thiếu ngữ cảnh caller mong muốn.

## 10. Tình huống đặc biệt: constructor thất bại

Một constructor có thể ném ngoại lệ trước khi hoàn tất. Khi đó:

- Không có reference đã xây dựng thành công được trả về từ biểu thức **new** cho caller.
- Một số initializer hoặc constructor của superclass/subclass có thể đã thực thi một phần và tạo side effect.
- Advice kiểu finally ở điểm đã đi qua vẫn có thể chạy, tùy join point và điểm cắt.
- Advice **after-returning** ở một ranh giới không hoàn tất bình thường không nên bị hiểu như đã ghi nhận tạo thành công.

Giả sử nghiệp vụ ghi sự kiện “khởi tạo thành công” ở advice after-finally. Nếu constructor ném lỗi, event đó trở thành dữ liệu sai. Cần dùng điều kiện thành công và điểm quan sát phù hợp, thường là ranh giới **trả về object đã khởi tạo thành công** theo cơ chế hỗ trợ.

Ngược lại, để thu thập metric “có bao nhiêu lượt thử tạo object, kể cả lỗi”, một điểm ghi ở phía caller trước khi tạo hoặc advice before có thể hợp lý hơn, miễn nó thực sự bao phủ các lượt thử mong muốn.

Không nên dùng một counter duy nhất cho cả “bắt đầu tạo”, “constructor đã chạy” và “đối tượng tạo xong”. Ba chỉ số này đo ba sự kiện khác nhau.

## 11. Tình huống đặc biệt: lớp khởi tạo thất bại

Static field initializer hoặc static block có thể ném lỗi. Trong Java, lỗi khởi tạo có các quy tắc lan truyền và trạng thái lớp riêng; ví dụ một ngoại lệ không phải Error phát sinh trong static initialization có thể được bọc dưới **ExceptionInInitializerError**; các lần dùng lớp đã đánh dấu khởi tạo lỗi có thể gặp **NoClassDefFoundError**.

Từ góc nhìn AOP:

- Advice quanh một static initializer **không được giả định lớp luôn khởi tạo thành công**.
- Việc advisor hoặc advice **chạm đến chính lớp chưa khởi tạo xong** có thể tạo phụ thuộc vòng hoặc quan sát trạng thái chưa ổn định.
- Phải phân biệt lỗi của bộ dệt/agent, lỗi trong static initializer gốc và lỗi do advice được thêm vào.
- Nếu advice ghi metric, hãy dùng nhãn biểu thị **started**, **succeeded** hoặc **failed**, thay vì một nhãn “initialized” không rõ tình trạng.

Khác với lỗi constructor của một instance, lỗi khởi tạo lớp có thể ảnh hưởng **các lần sử dụng cùng định nghĩa lớp về sau**. Đây là lý do advice trong class initialization nên có ít side effect và ít phụ thuộc vòng đời lớp khác nhất có thể.

## 12. Tính an toàn của advice trong giai đoạn khởi tạo

Giai đoạn khởi tạo nhạy cảm vì target có thể ở trạng thái chưa ổn định. Những điều sau cần được xem xét trước khi gắn advice:

### 12.1. Không phát tán đối tượng chưa hoàn tất

Nếu một constructor hoặc advice đưa **this** vào một registry, callback, executor hay listener công khai trước khi khởi tạo xong, mã khác có thể nhìn thấy các trường ở giá trị mặc định hoặc bất biến chưa được thiết lập. Hiện tượng này thường được gọi là **this escape**.

Advice ghi thông tin kỹ thuật nên tránh tham chiếu đến object qua nhiều luồng hoặc gọi mã bên ngoài khi object còn xây dựng dở. Nếu chỉ cần biết kiểu, chữ ký constructor hay thời gian, hãy ưu tiên metadata ít rủi ro thay vì đọc toàn bộ trạng thái.

### 12.2. Không dựa vào method override trong constructor

Khi superclass constructor gọi một phương thức có thể override, Java có thể dispatch đến override của subclass, ngay cả lúc trường instance subclass chưa khởi tạo. Một advice tại constructor execution gọi thêm một phương thức có thể override có thể tạo đường gọi nhạy cảm tương tự.

Không nên dùng lời gọi đó để “xác thực object đã sẵn sàng” nếu target chưa hoàn tất.

### 12.3. Kiểm soát reentrancy

Advice có thể tạo đối tượng mới, ghi log, khởi tạo một helper hoặc nạp một lớp khác. Những hành động đó có thể khớp lại với cùng pointcut và khiến advice chạy lồng nhau.

Ví dụ một aspect theo dõi **mọi constructor** nhưng chính bộ ghi log của aspect cũng tạo object; nếu không loại trừ phần hạ tầng quan sát hoặc không giới hạn phù hợp, điểm can thiệp có thể phát sinh rất nhiều lần ngoài dự kiến.

### 12.4. An toàn đa luồng

Class initialization có cơ chế đồng bộ của JVM; nhưng **advice của bạn vẫn phải tuân theo chính sách đồng bộ đối với trạng thái mà nó chia sẻ**. Khi nhiều object cùng được tạo trên nhiều luồng, advice tại constructor có thể chạy đồng thời. Một biến đếm dùng chung với phép tăng không nguyên tử có thể sai.

Nếu cần metric số lần, dùng cấu trúc an toàn luồng phù hợp và xác định rõ event đếm là trước, sau hay thành công.

### 12.5. Độ ổn định của công cụ

Static initialization và constructor có thể xuất hiện trong nhiều thư viện hạ tầng, kể cả chính thư viện logging/tracing. Pointcut quá rộng dễ tạo tác động dây chuyền, chi phí khởi động lớn hoặc vòng lặp instrumentation.

Hãy chọn tập kiểu rõ ràng, thử trên đường khởi động thật, và kiểm tra sự tương thích giữa cơ chế weaving với class loader cũng như những dạng class mà công cụ hỗ trợ.

## 13. Những giới hạn khi chỉ dùng proxy hoặc factory

Một proxy thông thường làm việc bằng cách nhận **lời gọi phương thức** qua đối tượng đại diện. Với constructor, vấn đề là **đối tượng phải được tạo trước khi có một instance proxy của nó** theo nhiều thiết kế proxy phổ biến.

Một điểm chặn ở factory:

~~~java
interface Factory<T> {
    T create();
}

static <T> T observeCreation(Factory<T> factory) {
    System.out.println("factory: before");
    try {
        return factory.create();
    } finally {
        System.out.println("factory: after");
    }
}
~~~

Đây là **đoạn mã minh họa**, chưa phải chương trình Java độc lập. Caller có thể gọi **observeCreation(() -> new Child())** để bao quanh một đường tạo object. Lời gọi đó ghi begin/end của **factory boundary**; nó không chứng minh static initializer, constructor execution hay object pre-initialization đã được dệt.

Những giới hạn cần nhớ:

1. Nếu code tạo đối tượng **trực tiếp** bằng new mà không đi qua factory, interceptor tại factory không quan sát được đường đó.
2. Factory có thể trả về một object có sẵn hoặc lấy từ pool; một lượt gọi factory chưa chắc tương đương một lần cấp phát/khởi tạo object.
3. Nếu khởi tạo lớp xảy ra trước khi factory được gọi, thời gian đo tại factory không bao gồm class initialization trước đó.
4. Chặn một phương thức **init()** tự đặt tên không đồng nghĩa chặn ngữ nghĩa khởi tạo object của Java.
5. Với bytecode weaving có join point constructor/static initialization thực sự, tập điểm có thể khác, nhưng phải xác minh tính tương thích của bộ dệt và đường thực thi.

Đây là khác biệt cơ bản giữa **chặn một API tạo đối tượng** và **can thiệp vào ngữ nghĩa khởi tạo của ngôn ngữ**.

## 14. Nên đặt concern nào ở giai đoạn nào?

| Nhu cầu | Điểm quan sát đáng cân nhắc | Lý do và điều kiện |
|---|---|---|
| Đo chi phí khởi tạo lớp | Join point class/static initialization nếu hỗ trợ | Đếm theo vòng đời lớp; không suy ra số object |
| Đo thời gian tạo instance theo một API công khai | Biên constructor call hoặc factory nếu bao phủ đúng caller | Gắn với lượt yêu cầu, có thể lấy thời điểm nhận reference |
| Theo dõi constructor nào hoạt động | Constructor execution | Phân biệt overload/chaining; không mặc định mỗi execution là một object |
| Kiểm tra trạng thái sau khi tạo instance thành công | Biên hoàn tất khởi tạo theo mô hình được hỗ trợ | Tránh đọc trạng thái quá sớm |
| Thu metric cho cả lần thử tạo thất bại | Before ở biên yêu cầu + advice lỗi thích hợp | Tách attempted/succeeded/failed |
| Áp dụng quy tắc miền khi object hình thành | Constructor, factory hoặc đối tượng cộng tác rõ ràng | Quy tắc bất biến nên hiện rõ trong thiết kế miền |
| Tự động ghi dấu nhiều vùng của chương trình | Pointcut phù hợp ở các join point được bộ dệt hỗ trợ | Kiểm tra coverage, thứ tự, khả năng tránh reentrancy |

AOP hữu ích nhất khi concern thực sự xuyên cắt nhiều nơi, có phạm vi chọn ổn định và có khả năng xác minh. Đưa toàn bộ logic nghiệp vụ khởi tạo sang aspect có thể khiến object trông như đã hoàn tất trong mã gốc nhưng thực tế phụ thuộc vào advice ẩn, khó kiểm thử hơn.

## 15. Quy trình kiểm tra thứ tự thực thi khi dùng AOP

Bắt đầu từ ví dụ Java thuần đã kiểm chứng. Sau khi có bộ dệt cụ thể, thêm aspect với phạm vi **rất hẹp**, chẳng hạn chỉ một lớp và một loại constructor, rồi làm theo các bước:

1. **Ghi baseline:** lưu stdout/các assertion của chương trình Java trước khi dệt. Xác định rõ lần tạo thứ nhất, thứ hai, trường hợp **this(...)** và superclass.
2. **Đặc tả từng pointcut:** chọn đúng nhóm static initialization, constructor call, constructor execution hay initialization; ghi rõ có được công cụ hỗ trợ không.
3. **Thêm một advice một lần:** chạy lại, xác định điểm xuất hiện của thông điệp advice trong trace.
4. **Thử constructor chaining:** so sánh số sự kiện với số instance và số thân constructor được gọi.
5. **Thử đường lỗi:** cho constructor hoặc initializer thất bại theo kịch bản kiểm thử riêng; xác nhận after-returning/after-throwing/finally đúng nghĩa.
6. **Thử nhiều aspect:** thêm precedence có chủ đích; kiểm tra thứ tự cũng như kết quả.
7. **Thử đường ngoài phạm vi:** tạo đối tượng qua caller khác hoặc qua factory/proxy khác, phát hiện đường đi không được quan sát.
8. **Kiểm tra side effect:** đảm bảo advice không làm object bị phát tán sớm, tăng metric hai lần, ném lỗi mới hoặc thay đổi chức năng ngoài yêu cầu.

Với dệt mã ở compile/load time, chỉ chạy bài kiểm thử trên đúng artifact và JVM/configuration có bộ dệt hoạt động. Gọi thủ công hàm của aspect trong một unit test **không chứng minh** advice đã được áp dụng tại join point thực tế.

### 15.1. Ma trận kiểm thử tối thiểu

| Tình huống | Kỳ vọng cần viết thành assertion |
|---|---|
| Lần sử dụng active đầu của lớp | Static initialization đúng phạm vi, đúng số lần |
| Lần tạo instance thứ hai | Không chạy lại static, có khởi tạo instance |
| Constructor có **this(...)** | Không nhầm các constructor execution với các object mới |
| Constructor của superclass | Phần superclass trước phần instance subclass theo Java |
| Constructor ném ngoại lệ | Không ghi sự kiện “tạo thành công” sai |
| Caller không thuộc pointcut | Không kích hoạt advice ngoài phạm vi |
| Hai aspect cùng áp dụng | Thứ tự ổn định và kết quả đúng |
| Nhiều luồng cùng tạo object | Metric và trạng thái chia sẻ an toàn |
| Class loader khác | Hiểu đúng danh tính lớp và vòng đời khởi tạo độc lập |

## 16. Câu hỏi vận dụng

**Câu 1.** Một điểm đo static initializer ghi nhận 1 lần, nhưng đã có 300 instance được tạo. Có lỗi không?

**Đáp:** Không nhất thiết. Static initialization đo vòng đời lớp, còn constructor/object initialization gắn với các lượt tạo instance. Đếm khác nhau là đúng nếu metric được định nghĩa khác nhau.

**Câu 2.** **new Child()** gọi tiếp **this("default")** và **super()**. Nếu instrument ba constructor execution liên quan, có phải đã tạo ba object?

**Đáp:** Không. Nhiều constructor nằm trong quá trình tạo cùng một instance; phải phân biệt số lần thực thi constructor với số object cấp phát.

**Câu 3.** Chỉ gọi **Class.forName(typeName, false, loader)** có chắc chạy khối static chưa?

**Đáp:** Không; tham số **false** yêu cầu không kích hoạt khởi tạo chỉ vì thao tác này.

**Câu 4.** Một advice before tại constructor đọc toàn bộ trường subclass và gửi **this** sang một luồng khác. Rủi ro gì?

**Đáp:** Các trường có thể còn giá trị mặc định/chưa hợp lệ; object escape khỏi quá trình xây dựng khiến luồng khác quan sát trạng thái chưa hoàn tất.

**Câu 5.** Muốn thống kê số lần caller yêu cầu tạo object qua một factory. Nên chặn static initialization hay biên factory?

**Đáp:** Biên factory; đồng thời cần nhớ một lần gọi factory có thể trả về object sẵn có thay vì cấp phát mới. Tên metric phải phản ánh sự kiện thực đo.

**Câu 6.** Một pointcut constructor-call không ghi log khi object được tạo tại nơi khác. Điều này có chứng minh weaver không hoạt động?

**Đáp:** Chưa. Có thể pointcut chỉ bao phủ một tập caller, hoặc đường thực thi không đi qua cơ chế chặn. Cần kiểm tra phạm vi điểm chọn và cơ chế weave cụ thể.

**Câu 7.** Đọc một **static final int** là hằng compile-time luôn phải khởi tạo lớp khai báo nó?

**Đáp:** Không. Quy tắc constant variable của Java có thể khiến giá trị được dùng mà không khởi tạo lớp.

## 17. Sơ đồ ghi nhớ toàn bộ bài

~~~text
CLASS LIFECYCLE
    load ──► link ──► initialize class (static field/block)
                             │
                             │ thường trước lần new đầu tiên
                             ▼
OBJECT LIFECYCLE
    yêu cầu tạo object ──► cấp phát, giá trị mặc định
                             │
                             ▼
                  chuỗi constructor super / this
                             │
                             ▼
                  instance initializer + constructor body
                             │
                             ▼
                     object tạo thành công
                             │
                             ▼
                     caller nhận reference
~~~

**AOP có thể nhìn các mốc khác nhau của sơ đồ trên** nếu công cụ hỗ trợ: static initialization ở cấp lớp, constructor call từ phía caller, constructor execution ở phía callee, các giai đoạn pre-initialization/initialization ở vòng đời instance. Không có một điểm “khởi tạo” duy nhất đại diện cho mọi sự kiện.

Muốn triển khai đúng, hãy chỉ rõ **sự kiện nào cần quan sát, pointcut chọn loại join point nào, bộ dệt thực sự hỗ trợ đến đâu, advice chạy vào thời điểm nào, object đã có trạng thái gì và tác dụng phụ nào được phép**. Trình tự gốc của Java là cơ sở để đánh giá kết quả sau khi dệt; lời khẳng định về thứ tự advice cần kiểm chứng theo cơ chế AOP đang dùng.

## Tài liệu đối chiếu

- [Java Language Specification, Java SE 17, Chapter 12 — Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): phân biệt loading, linking, class initialization và object creation.
- [AspectJ Programming Guide — Join Points](https://eclipse.dev/aspectj/doc/released/progguide/semantics-joinPoints.html): một ví dụ mô hình AOP có định nghĩa tường minh constructor call/execution, static initialization, pre-initialization và initialization; không được suy rộng mọi khả năng đó sang mọi công cụ AOP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-005">Điểm xử lý ngoại lệ và các giới hạn theo công cụ</a>

<details>
<summary>Xem chi tiết</summary>

# Điểm xử lý ngoại lệ và các giới hạn theo công cụ

## 1. Vì sao điểm xử lý ngoại lệ là một điểm can thiệp đáng tìm hiểu?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), một **join point (điểm nối/điểm can thiệp)** là vị trí trong luồng thực thi mà cơ chế AOP có thể cho phép gắn thêm hành vi. Các điểm liên quan đến ngoại lệ đặc biệt quan trọng vì chúng quyết định *lỗi được quan sát lúc nào*, *ai thực sự nhận lỗi*, và *lỗi đã được xử lý hay vẫn tiếp tục lan truyền*.

Java phân biệt rõ hai hiện tượng:

- **Một thao tác kết thúc bằng ngoại lệ:** ngoại lệ thoát khỏi lời gọi/phương thức đang được quan sát; advice kiểu **after throwing** có thể được kích hoạt ở ranh giới này nếu công nghệ AOP hỗ trợ.
- **Một exception handler được đi vào:** luồng thực thi đến phần `catch` phù hợp với ngoại lệ; ở những cơ chế có hỗ trợ **exception-handler join point**, chính điểm bước vào handler này có thể là nơi advice chạy.

Đây là **hai điểm khác nhau**. Một ngoại lệ có thể đã được bắt và xử lý bên trong phương thức, khiến phương thức trả về bình thường: handler đã chạy nhưng **không có after throwing tại ranh giới phương thức ấy**. Ngược lại, một ngoại lệ thoát khỏi phương thức không nhất thiết có handler nằm trong phương thức đó.

Đề bài này tập trung vào việc hiểu đúng **điểm xử lý ngoại lệ**, những gì advice có thể biết ở đó, và những **giới hạn tùy công cụ** (proxy, weaving, join point model, kiểu ngoại lệ, bất đồng bộ).

## 2. Ôn lại vòng đời ngoại lệ ở Java

Xét chương trình ý niệm:

~~~java
String readValue() {
    try {
        return load(); // có thể ném IllegalStateException
    } catch (IllegalStateException problem) {
        return "fallback";
    } finally {
        release();
    }
}
~~~

Luồng chạy gồm các bước:

1. `load()` được gọi trong `try`.
2. Nếu `load()` thành công, khối `catch` bị bỏ qua, `finally` vẫn thực thi trước khi phương thức trả về.
3. Nếu `load()` ném `IllegalStateException`, Java tìm khối `catch` tương thích theo thứ tự khai báo; handler tương ứng được đi vào.
4. Handler ở ví dụ trả về `"fallback"`, sau đó `finally` chạy; với điều kiện `release()` không ném lỗi, phương thức **trả về bình thường**.
5. Nếu không có handler phù hợp trong phương thức, hoặc handler ném lại lỗi, ngoại lệ tiếp tục lan lên các lời gọi phía ngoài.
6. Nếu `release()` trong `finally` ném ngoại lệ, chính ngoại lệ mới có thể thay đổi kết quả quan sát được, thậm chí che lỗi trước đó. Vì thế cần xem toàn bộ cấu trúc xử lý.

Trong Java, lựa chọn handler dựa trên kiểu ngoại lệ và tính tương thích với khai báo `catch`. Ví dụ, `catch (RuntimeException e)` có thể nhận `IllegalArgumentException` hoặc `IllegalStateException`, vì chúng là kiểu con của `RuntimeException`. Với nhiều `catch`, thứ tự và ràng buộc của ngôn ngữ quyết định handler nào hợp lệ và được chọn.

**Điểm handler không phải nơi ngoại lệ được tạo ra bằng `throw`.** Nếu một câu lệnh `throw` xuất hiện trong hàm A, còn `catch` xử lý ở hàm B phía ngoài, vị trí tạo ngoại lệ và vị trí vào handler nằm ở hai phần khác nhau của chương trình.

## 3. Phân biệt bốn loại sự kiện cần quan sát

| Sự kiện | Khi xảy ra | Điểm quan sát phù hợp | Điều không thể suy ra |
| --- | --- | --- | --- |
| **Throw** | Mã thực hiện `throw` hoặc thao tác gây ra ngoại lệ | Điểm ném (nếu cơ chế hỗ trợ) | Chưa biết cuối cùng có được xử lý hay không |
| **Handler entry** | Luồng chuyển vào `catch` phù hợp | Exception-handler join point (nếu có) | Chưa biết handler sẽ nuốt, ném lại hay đổi lỗi |
| **Exceptional method exit** | Ngoại lệ thoát khỏi phương thức đang quan sát | Advice `after throwing` tại method execution/call | Không thấy các lỗi đã xử lý hoàn toàn bên trong |
| **Normal method exit** | Phương thức trả kết quả, kể cả kết quả dự phòng sau `catch` | Advice `after returning` | Không chứng minh bên trong chưa từng có ngoại lệ |

Ví dụ:

~~~text
Caller
  |
  +-> operation()
         |
         +-> riskyCall() ---- ném IllegalStateException ----+
         |                                                   |
         +<------------------- catch IllegalStateException <-+
         |                     [handler entry]
         |                     return "fallback"
         |
  <------ "fallback" (normal return)
~~~

Tại `operation()`, có **handler entry**, rồi **normal return**. Một aspect chỉ quan sát `after throwing` tại ranh giới `operation()` sẽ không nhận được sự kiện lỗi này. Điều đó không có nghĩa aspect vận hành sai; pointcut đơn giản là nhắm tới **một loại điểm can thiệp khác**.

Nếu handler thực hiện `throw e;`, lỗi có thể tiếp tục thoát ra và tạo thêm sự kiện exceptional exit ở ranh giới bên ngoài. Nếu handler đổi thành ngoại lệ khác, lời gọi bên ngoài nhìn thấy ngoại lệ mới, còn tại handler entry ban đầu có thể quan sát ngoại lệ cũ. Cần quyết định **muốn đo “đã xảy ra lỗi”, “đã xử lý lỗi”, hay “tác vụ cuối cùng thất bại”** trước khi chọn join point.

## 4. Exception-handler join point thực chất là gì?

Với một cơ chế AOP hỗ trợ điểm xử lý ngoại lệ, **exception-handler join point** thường đại diện cho thời điểm điều khiển chuyển đến một handler `catch` được chọn trong chương trình. Đây là điểm theo *cấu trúc xử lý ngoại lệ*, chứ không chỉ là việc một phương thức trả về hoặc ném lỗi.

Mô hình khái niệm:

~~~text
try {
    perform();
} catch (SpecificProblem problem) {
    // [handler join point: khi catch này được đi vào]
    recover(problem);
}
~~~

Một advice gắn vào điểm này có thể được thiết kế để ghi nhận loại lỗi, tên vùng xử lý (nếu cơ chế cung cấp), thông tin truy vết hoặc thống kê số lần handler được sử dụng. Nhưng phải kiểm tra tài liệu của công cụ để biết các chi tiết quan trọng:

- **Có hỗ trợ loại join point “handler” hay không?** Một công cụ chỉ bao phương thức bằng proxy thường không có điểm can thiệp ở bên trong khối `catch`.
- **Có thể chọn handler theo kiểu ngoại lệ không?** Nếu có, quy tắc chọn dựa trên kiểu khai báo `catch`, kiểu lỗi thực tế, hay một tiêu chí khác?
- **Advice chạy ở vị trí chính xác nào?** Trước thân handler, sau handler, hay bao quanh xử lý? Không tự giả định mọi kiểu advice đều khả dụng ở handler join point.
- **Advice được cung cấp những giá trị nào?** Có thấy biến ngoại lệ, chữ ký phương thức chứa handler, target và các biến cục bộ khác không? Các biến này không mặc nhiên được công cụ cung cấp.
- **Nếu advice ném lỗi thì sao?** Nó có thể làm gián đoạn việc phục hồi mà handler dự định thực hiện; đây là thay đổi ngữ nghĩa chương trình.
- **Handler do trình biên dịch tạo và handler ngầm định có được tính không?** Công nghệ bytecode và cách biên dịch có thể ảnh hưởng các vị trí thực sự có thể quan sát.

Chính vì thế, cách nói chính xác là: **handler là một loại join point có thể được hỗ trợ trong một số mô hình AOP**. Không phải mọi AOP engine đều có khả năng chặn `catch`, và không thể coi một phương thức `after throwing` là tương đương với handler join point.

## 5. Giới hạn theo cách kết hợp aspect vào chương trình

### 5.1. Cơ chế dựa trên proxy

Một proxy nằm giữa caller và target có thể quan sát sự kiện **lời gọi qua proxy**: tham số, trả về, ngoại lệ thoát ra từ lời gọi. Nhưng nó thường không nhìn được từng câu lệnh bên trong target, bao gồm `try`, `catch` và các handler nội bộ.

Ví dụ, phương thức `fetch()` ném lỗi rồi tự `catch` và trả `"cached"`. Với proxy bao quanh `fetch()`, kết quả chỉ là **một lời gọi thành công trả `"cached"`**. Proxy không biết chắc handler có chạy, trừ khi target chủ động phát sự kiện hoặc có cơ chế can thiệp bên trong khác.

Các giới hạn quen thuộc của proxy còn gồm:

- Lời gọi đi thẳng vào target, bỏ qua proxy, không được can thiệp.
- Lời gọi nội bộ giữa các phương thức qua `this` có thể không đi qua proxy (**self-invocation**).
- Loại phương thức can thiệp được phụ thuộc kỹ thuật proxy: interface, class, khả năng override và các ràng buộc truy cập. Không áp dụng một giả định cho mọi kiểu proxy.
- Một proxy method-call không tự động thêm join point cho constructor, truy cập field, lệnh `throw` hoặc khối `catch`.

### 5.2. Cơ chế weaving ở mức bytecode/mã biên dịch

Một cơ chế weaving có thể chèn hành vi ở phạm vi sâu hơn, bao gồm các điểm bên trong thân phương thức **nếu mô hình join point của nó hỗ trợ**. Khả năng này cần kiểm tra rõ chứ không thể suy ra chỉ từ tên “weaver”.

Các yếu tố phụ thuộc công cụ gồm thời điểm kết hợp (biên dịch, hậu biên dịch hoặc nạp lớp), lớp nào nằm trong phạm vi weaving, thư viện bên thứ ba có được biến đổi hay không, các lớp do JDK quản lý và các giới hạn về module/classloader.

Một join point tồn tại trên lý thuyết nhưng nằm trong lớp **không được weaving** vẫn không tạo advice. Điều này thường gây hiểu nhầm “pointcut đã đúng nhưng không có log”.

### 5.3. Giới hạn cú pháp pointcut và kiểu advice

Không phải mọi ngôn ngữ pointcut đều có toán tử chọn handler; không phải kiểu advice nào cũng áp dụng được lên mọi loại join point. Nếu cần tham chiếu ngoại lệ trong `catch`, phải xác minh cú pháp binding tương ứng. Không dùng cú pháp của một công nghệ để suy đoán rằng công nghệ khác hỗ trợ tương tự.

## 6. Ví dụ Java 17+: chứng minh handler entry khác exceptional exit

Ví dụ chạy độc lập dưới đây dùng **Java thuần**. Dòng `HANDLER` được gọi *tường minh trong `catch`* để mô phỏng sự kiện “đi vào handler”. Các dòng `RETURN` và `THROWING` được ghi bởi **JDK dynamic proxy**, đại diện cho điều proxy có thể quan sát ở ranh giới phương thức. Đây là mô phỏng cơ chế, không phải triển khai một bộ weaving handler join point.

Tạo file `ExceptionJoinPointDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ExceptionJoinPointDemo {
    interface Work {
        String recover();
        String rethrow();
        String fail();
        String succeed();
    }

    static final class Core implements Work {
        private void onHandlerEntry(String where, Exception error) {
            System.out.println("HANDLER " + where + ": " + error.getMessage());
        }

        @Override
        public String recover() {
            try {
                throw new IllegalStateException("offline");
            } catch (IllegalStateException error) {
                onHandlerEntry("recover", error);
                return "cached";
            }
        }

        @Override
        public String rethrow() {
            try {
                throw new IllegalStateException("unavailable");
            } catch (IllegalStateException error) {
                onHandlerEntry("rethrow", error);
                throw error;
            }
        }

        @Override
        public String fail() {
            throw new IllegalArgumentException("invalid");
        }

        @Override
        public String succeed() {
            return "done";
        }
    }

    static Work observeCalls(Work target) {
        return (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(), new Class<?>[] { Work.class },
                (proxy, method, args) -> invokeObserved(target, method, args));
    }

    static Object invokeObserved(Work target, Method method, Object[] args)
            throws Throwable {
        try {
            Object result = method.invoke(target, args);
            System.out.println("RETURN " + method.getName() + ": " + result);
            return result;
        } catch (InvocationTargetException wrapper) {
            Throwable error = wrapper.getCause();
            System.out.println("THROWING " + method.getName() + ": "
                    + error.getClass().getSimpleName());
            throw error;
        }
    }

    public static void main(String[] args) {
        Work work = observeCalls(new Core());
        System.out.println("CALL recover: " + work.recover());
        try {
            work.rethrow();
        } catch (IllegalStateException error) {
            System.out.println("CALLER rethrow: " + error.getMessage());
        }
        try {
            work.fail();
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER fail: " + error.getMessage());
        }
        System.out.println("CALL succeed: " + work.succeed());
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 ExceptionJoinPointDemo.java
java ExceptionJoinPointDemo
~~~

Kết quả tiêu chuẩn đã được đối chiếu sau khi biên dịch:

~~~text
HANDLER recover: offline
RETURN recover: cached
CALL recover: cached
HANDLER rethrow: unavailable
THROWING rethrow: IllegalStateException
CALLER rethrow: unavailable
THROWING fail: IllegalArgumentException
CALLER fail: invalid
RETURN succeed: done
CALL succeed: done
~~~

Phân tích chính xác bốn trường hợp:

1. **`recover()`:** Có ngoại lệ `IllegalStateException`, handler `catch` được đi vào và trả giá trị dự phòng. Proxy quan sát **RETURN**, không có **THROWING** tại ranh giới `recover()`.
2. **`rethrow()`:** Handler được đi vào rồi ném lại ngoại lệ ban đầu. Proxy quan sát **THROWING** khi ngoại lệ thoát khỏi phương thức. Một handler join point và một sự kiện exceptional exit có thể cùng xuất hiện trong một lời gọi.
3. **`fail()`:** Ngoại lệ thoát khỏi phương thức mà không có handler nội bộ. Proxy quan sát **THROWING**, còn caller bắt ngoại lệ ở mức cao hơn.
4. **`succeed()`:** Không có ngoại lệ. Proxy chỉ quan sát **RETURN**.

Phương thức `invokeObserved()` bóc lớp `InvocationTargetException` được tạo bởi reflection, rồi ném lại nguyên nhân gốc. Nếu không bóc, log có thể nhầm ngoại lệ nghiệp vụ thành lỗi của API phản chiếu. Ví dụ dùng các ngoại lệ unchecked để không cần khai báo `throws` trong interface; checked exception cần hợp đồng `throws` hoặc cách chuyển đổi rõ ràng phù hợp với proxy.

Điểm quan trọng nhất: **proxy không hề phát hiện trực tiếp câu lệnh `catch`**. Ta chủ động gọi `onHandlerEntry()` trong mã target để minh họa vị trí handler. Muốn can thiệp vào handler không sửa trực tiếp mã nghiệp vụ, phải chọn công cụ có khả năng tương ứng và bảo đảm vùng mã ấy thực sự được kết hợp.

## 7. Catch handler, after throwing, finally và “handled” khác nhau ra sao?

Một advice kiểu **after throwing** dùng để quan sát khi join point đang theo dõi kết thúc bằng ngoại lệ, ví dụ lần thực thi phương thức vừa ném lỗi ra ngoài. **Handler join point** là thời điểm một khối `catch` cụ thể bắt đầu xử lý. **Finally** là cơ chế Java đảm bảo chạy khối dọn dẹp trong phạm vi `try` phù hợp; nó không đồng nghĩa với “một handler đã xử lý ngoại lệ”.

Một kịch bản phức tạp:

~~~java
String load() {
    try {
        return readPrimary();
    } catch (IllegalStateException original) {
        logFailure(original);
        return readFallback(); // có thể ném ngoại lệ khác
    } finally {
        closeResource(); // cũng có thể ném ngoại lệ
    }
}
~~~

Đoạn này là **mã minh họa không độc lập**: `readPrimary()`, `logFailure()`, `readFallback()` và `closeResource()` đại diện cho các thao tác ứng dụng.

Từ đoạn mã này không thể kết luận mọi lần vào `catch` đều dẫn đến trả về thành công. `readFallback()` có thể lỗi, hoặc `closeResource()` có thể thay đổi kết quả khi kết thúc. Một advice quan sát **entry vào handler** chỉ biết handler vừa bắt đầu; một advice quan sát **exceptional exit** ở cuối `load()` biết ngoại lệ cuối cùng thoát ra; một advice quan sát **normal return** chỉ biết có kết quả cuối cùng. Các sự kiện có ý nghĩa khác nhau.

Nếu ứng dụng dùng `try-with-resources`, Java cũng có cơ chế quản lý ngoại lệ khi đóng tài nguyên và có thể gắn ngoại lệ đóng thành **suppressed exception** của lỗi chính. Khi muốn audit nguyên nhân thực tế, cần kiểm tra cả `Throwable.getSuppressed()` trong những trường hợp phù hợp, thay vì chỉ nhìn thông điệp của ngoại lệ cuối.

## 8. Những giới hạn khác thường bị bỏ sót

### 8.1. Checked exception, unchecked exception và `Error`

`catch (Exception e)` không bao quát mọi `Throwable` vì `Error` không là lớp con của `Exception`. Chọn handler cần hiểu hệ phân cấp kiểu ngoại lệ. Một advice `after throwing` có thể được cấu hình lọc các loại lỗi khác nhau tùy công cụ. Không đưa ra giả định “mọi thất bại đều là Exception” khi thiết kế logging hoặc metrics.

Khi proxy xử lý checked exception không nằm trong chữ ký `throws` của interface, cơ chế có thể phải bọc nó theo quy tắc proxy của Java. Phần mềm gọi qua proxy có thể thấy **kiểu lỗi khác với lỗi gốc**. Cần giữ rõ hợp đồng `throws`, kiểm tra loại ngoại lệ thực tế caller quan sát, và phân biệt lỗi của target với lỗi phát sinh từ advice.

### 8.2. Re-throw, đổi lỗi và ngoại lệ do advice phát sinh

Nếu handler `throw original;` thì lời gọi bên ngoài có thể nhận lại nguyên nhân cũ. Nếu handler `throw new DomainFailure(original);` thì sự kiện bên ngoài phản ánh ngoại lệ mới với `cause` là lỗi cũ. Nếu advice tự ném lỗi khi ghi audit, lỗi này cũng có thể khiến phương thức kết thúc khác đi.

Điểm handler có thể dùng để quan sát ngoại lệ được bắt, **không bảo đảm biết trước handler sẽ nuốt hay ném lại**. Muốn biết kết quả cuối, cần quan sát thêm ranh giới hoàn tất của thao tác hoặc có event sau khi handler xử lý, nếu công nghệ hỗ trợ.

### 8.3. Bất đồng bộ: lỗi không nhất thiết thoát tại điểm gọi ban đầu

Phương thức có thể trả `CompletableFuture` ngay lập tức rồi tác vụ chạy sau đó mới thất bại. Khi ấy, method `after returning` ở thời điểm tạo Future có thể đã chạy, trong khi kết quả thực của tác vụ chưa có. Ngoại lệ có thể được lưu trong Future và chỉ hiện ra khi chờ/đọc kết quả hoặc trong callback xử lý hoàn tất.

Nếu muốn đo lỗi cuối cùng của tác vụ, cần quan sát điểm hoàn tất Future hoặc nơi ngoại lệ được xử lý bất đồng bộ. Không thể chỉ đặt `after throwing` lên phương thức trả Future rồi mặc định thu được mọi lỗi của tác vụ bên trong.

### 8.4. Handler do công cụ nhìn thấy và handler do người viết mong đợi

Trình biên dịch có thể biến đổi mã điều khiển ngoại lệ, tạo cấu trúc phục vụ `finally` hoặc quản lý tài nguyên. Một số công cụ chỉ phát hiện handler trong class đã weaving; một số chỉ can thiệp ranh giới phương thức; một số có giới hạn với lớp thư viện, classloader, module hoặc bytecode được tối ưu hóa.

Do đó, khi kiểm thử handler join point, cần đối chiếu **các khối `catch` thực sự được công cụ quan sát** chứ không suy luận từ cú pháp Java. Nếu cơ chế hỗ trợ pointcut theo kiểu handler, vẫn phải kiểm tra kiểu thực tế và phạm vi biên dịch/weaving trước khi tin rằng mọi handler được khớp.

### 8.5. Chính sách dữ liệu và lỗi phụ

Một handler có thể chứa thông tin nhạy cảm trong `message`, `cause` hoặc tham số của lời gọi. Hành vi audit tại handler nên ghi thông tin tối thiểu, tránh in toàn bộ payload hoặc stack trace không kiểm soát. Đồng thời, nếu chính cơ chế ghi sự kiện thất bại, hệ thống phải có chính sách không để lỗi phụ vô tình che lỗi nghiệp vụ hay phá luồng phục hồi.

## 9. Chọn loại điểm can thiệp theo mục tiêu

| Mục tiêu quan sát | Nên xem xét điểm nào? | Vì sao? |
| --- | --- | --- |
| Biết một khối `catch` cụ thể có được sử dụng | Handler entry nếu engine hỗ trợ, hoặc instrumentation tường minh | Sự kiện xảy ra ngay khi bắt đầu xử lý |
| Biết lời gọi API trả lỗi cho caller | Exceptional exit / `after throwing` ở ranh giới API | Đo thất bại theo hợp đồng caller |
| Biết số lần xảy ra lỗi dù được phục hồi nội bộ | Điểm ném hoặc điểm handler đủ rộng, có kiểm soát | Lỗi có thể bị xử lý và không thoát ra ngoài |
| Biết tác vụ cuối cùng thành công hay không | Ranh giới hoàn tất tác vụ hoặc completion của Future | Một `catch` không quyết định kết quả cuối cùng |
| Đảm bảo cleanup trên cả nhánh lỗi và thành công | `finally` hoặc advice tương đương trong phạm vi phù hợp | Hành vi cần chạy trên mọi đường rời phạm vi |
| Theo dõi chính sách phục hồi cụ thể | Handler kết hợp kết quả xử lý nếu có khả năng quan sát | Entry handler chưa chứng minh phục hồi thành công |

Một quy trình áp dụng thực tế:

1. **Viết rõ sự kiện muốn đếm:** số lần phát sinh lỗi, số lần vào handler, số lần phương thức thất bại, hay số tác vụ cuối cùng thất bại?
2. **Chọn điểm theo ngữ nghĩa sự kiện**, không theo tên advice nghe có vẻ giống nhau.
3. **Kiểm tra khả năng của công cụ:** join point nào được hỗ trợ, tên pointcut và advice hợp lệ, ngữ cảnh ngoại lệ bind được, phạm vi weaving/proxy.
4. **Thiết kế ranh giới quan sát:** tránh đếm nhiều lần một lỗi khi nó bị ném qua nhiều lớp; quyết định có phân biệt lỗi được phục hồi và lỗi chưa xử lý hay không.
5. **Kiểm thử tại đường chạy thật:** kiểm tra handler có nuốt lỗi, handler ném lại, không có handler nội bộ, lỗi trong `finally`, và đường bất đồng bộ nếu ứng dụng dùng.
6. **Giữ hành vi quan sát an toàn:** không tự đổi exception, kết quả và số lần xử lý nếu yêu cầu chỉ là audit hoặc metrics.

## 10. Các trường hợp kiểm thử cần có

| Trường hợp | Handler nội bộ? | Phương thức cuối cùng ra sao? | Điều cần quan sát |
| --- | --- | --- | --- |
| Không lỗi | Không | Return bình thường | Không có handler event |
| Lỗi được phục hồi | Có | Return dự phòng | Handler event, không có exceptional exit tại ranh giới đó |
| Lỗi bị ném lại | Có | Thoát bằng ngoại lệ | Handler event rồi exceptional exit |
| Không có handler phù hợp bên trong | Không | Thoát bằng ngoại lệ | Exceptional exit, caller có thể bắt ngoài |
| Handler tự gây lỗi mới | Có | Có thể thoát bằng lỗi mới | Phân biệt lỗi ban đầu và lỗi cuối |
| `finally` gây lỗi | Có hoặc không | Có thể đổi lỗi cuối | Nguyên nhân gốc và lỗi cleanup |
| Trả Future, lỗi xuất hiện sau | Có thể không ở call ban đầu | Phương thức trả Future bình thường; task thất bại sau | Quan sát completion, không chỉ method exit |
| Gọi target trực tiếp bỏ qua proxy | Tùy target | Theo nghiệp vụ | Không kỳ vọng advice chỉ gắn trên proxy xuất hiện |

Đừng chỉ chạy unit test trên hàm advice. Với AOP, phải kiểm tra **điểm can thiệp thực sự được chọn và chạy**. Cách dễ nhất là kiểm tra cùng một target ở hai đường: gọi qua ranh giới có advice và gọi trực tiếp; đồng thời kiểm tra số lần handler thật sự được thực thi.

## 11. Lỗi hiểu sai thường gặp

- **“Có throw thì chắc chắn có handler join point tại đó.”** Không đúng; `throw` và `catch` là hai vị trí khác nhau.
- **“After throwing sẽ bắt được mọi lỗi đã từng xảy ra trong phương thức.”** Không đúng nếu lỗi được xử lý nội bộ và phương thức trả bình thường.
- **“Có handler join point nghĩa là advice biết recovery thành công.”** Không đúng; vào `catch` mới là bắt đầu xử lý.
- **“Proxy chặn được cả catch bên trong target.”** Thông thường proxy chỉ nhìn ranh giới lời gọi; cần hỗ trợ weaving hoặc phát sự kiện tường minh để nhìn trong handler.
- **“Bất kỳ công cụ AOP nào cũng có handler pointcut.”** Mô hình join point và ngôn ngữ pointcut phụ thuộc công cụ.
- **“Bắt Exception tức là bắt mọi Throwable.”** `Error` không thuộc nhánh `Exception`.
- **“Một after throwing trên hàm trả Future quan sát luôn lỗi bất đồng bộ.”** Future có thể chỉ báo lỗi về sau.
- **“Ghi log trong advice không thể ảnh hưởng chương trình.”** Lỗi logging, sửa tham số, biến đổi exception hoặc gọi tiếp sai có thể đổi hành vi thật.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `read()` bắt `IllegalStateException` bên trong rồi trả giá trị dự phòng. `After throwing` ở ranh giới `read()` có chạy vì lỗi đó không?  
**Trả lời:** Không, nếu `read()` cuối cùng trả về bình thường. Handler bên trong đã chạy nhưng ngoại lệ không thoát khỏi ranh giới quan sát.

**Câu 2.** Handler đã nhận ngoại lệ và thực hiện `throw e;`. Lúc này có thể quan sát những điểm nào?  
**Trả lời:** Handler entry ở vị trí `catch` nếu cơ chế hỗ trợ; sau đó exceptional exit tại các ranh giới mà lỗi đi qua và có advice phù hợp.

**Câu 3.** Vì sao lời gọi qua dynamic proxy không đủ quan sát mọi `catch`?  
**Trả lời:** Proxy can thiệp ở giao diện lời gọi. Một handler xử lý hoàn toàn bên trong target không tạo sự kiện bắt lỗi ở ranh giới phương thức.

**Câu 4.** Nếu muốn đo tỷ lệ *tác vụ bất đồng bộ cuối cùng thất bại*, nên theo dõi gì?  
**Trả lời:** Trạng thái completion cuối cùng của tác vụ/Future, phân biệt với thời điểm phương thức tạo tác vụ trả về.

**Câu 5.** Khi chọn công cụ AOP để can thiệp vào handler, cần xác minh tối thiểu những gì?  
**Trả lời:** Công cụ có hỗ trợ handler join point; pointcut chọn đúng handler; advice/binding ngoại lệ được hỗ trợ; mã chứa handler thuộc phạm vi weaving; và kết quả thực tế được kiểm chứng qua chạy chương trình.

## 13. Ghi nhớ

**Điểm xử lý ngoại lệ** là nơi chương trình *bắt đầu xử lý* một ngoại lệ, khác với **điểm ném lỗi** và **lần thoát phương thức bằng ngoại lệ**. Khả năng can thiệp trực tiếp tại handler phụ thuộc mô hình join point và kỹ thuật mà công cụ AOP hỗ trợ. Khi cần theo dõi lỗi chính xác, hãy bắt đầu từ sự kiện muốn quan sát, chọn đúng ranh giới, xác minh khả năng proxy/weaving, và kiểm thử đủ cả phục hồi, ném lại, lỗi cleanup cùng trường hợp bất đồng bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-006">Các điểm liên quan khóa hoặc đồng bộ khi được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Các điểm liên quan khóa hoặc đồng bộ khi được hỗ trợ trong AOP

Các chương trình đa luồng thường có những đoạn cần **loại trừ tương hỗ** (mutual exclusion), bảo vệ trạng thái dùng chung hoặc điều phối thứ tự thực thi. Khi muốn ghi nhận thời gian chờ khóa, phát hiện vùng đồng bộ chạy quá lâu hoặc kiểm toán các điểm tranh chấp, ta có thể đặt câu hỏi dưới góc nhìn **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**: liệu những thời điểm liên quan đến khóa có thể được xem là **join point** để một aspect can thiệp không?

Câu trả lời là **có thể, nếu mô hình join point và cơ chế triển khai AOP thật sự hỗ trợ**. Không phải mọi hệ thống AOP nhìn thấy việc lấy/trả monitor, và quan sát một lời gọi phương thức đồng bộ cũng không đồng nghĩa quan sát chính xác từng sự kiện lấy/trả khóa. Bài này phân tích các điểm có thể quan sát, ý nghĩa của chúng, cách chọn điểm, truyền ngữ cảnh, ghi số liệu và các giới hạn an toàn.

## 1. Ôn lại: join point phụ thuộc cơ chế triển khai

Trong AOP:

- **Join point (điểm nối)** là một vị trí hoặc sự kiện thực thi mà một cơ chế AOP cho phép liên kết hành vi bổ sung.
- **Pointcut** chọn tập join point thỏa quy tắc.
- **Advice** chạy khi join point tương ứng được chọn, có thể đọc ngữ cảnh và thực hiện hành vi trước, sau hoặc bao quanh theo khả năng của cơ chế.
- **Weaving** là việc kết hợp hành vi khía cạnh với chương trình; có thể diễn ra ở những thời điểm và bằng kỹ thuật khác nhau.

Một mô hình AOP có thể chỉ hỗ trợ chặn lời gọi thông qua đối tượng đại diện (proxy). Một mô hình khác có thể biến đổi mã hoặc bytecode để nhìn thấy những điểm nằm sâu hơn trong phương thức. Vì vậy, hai cơ chế dùng cùng thuật ngữ “aspect” nhưng **tập điểm có thể can thiệp không nhất thiết giống nhau**.

Đối với khóa, đây là sự khác biệt quyết định: **thao tác lấy/trả khóa thường xảy ra bên trong thân phương thức**, nằm ngoài phạm vi quan sát của cơ chế chỉ chặn lời gọi ở biên đối tượng.

## 2. “Khóa” trong Java là những cơ chế nào?

Để không nhầm join point với một hành vi đồng bộ cụ thể, cần phân biệt ít nhất hai nhóm cơ chế Java.

### 2.1 Monitor gắn với `synchronized`

Mỗi đối tượng Java gắn với monitor. Một luồng vào vùng `synchronized (lockObject)` phải lấy monitor của đối tượng; khi rời vùng này, monitor được nhả, kể cả lúc thoát vì ngoại lệ.

~~~java
private final Object gate = new Object();

void update() {
    synchronized (gate) {
        // Vùng được bảo vệ bởi monitor của gate
    }
}
~~~

Đây là **đoạn mã minh họa bên trong một lớp**, chưa phải chương trình độc lập. Với phương thức `synchronized` không static, monitor liên quan tới đối tượng `this`; với phương thức `static synchronized`, monitor liên quan tới đối tượng `Class` của lớp đó.

Về mặt triển khai JVM, khối `synchronized` thường thể hiện qua thao tác `monitorenter` và `monitorexit` trong bytecode, còn phương thức khai báo `synchronized` có thể biểu thị tính đồng bộ thông qua cờ của phương thức. **Không nên mặc định một cơ chế chỉ tìm lệnh `monitorenter` sẽ bao phủ cả phương thức `synchronized`.**

Monitor của Java là **reentrant**: cùng luồng có thể lấy lại monitor mình đang nắm. Mỗi lần lấy phải tương ứng với việc nhả; một lần nhả ở đoạn lồng nhau chưa chắc làm monitor hoàn toàn tự do nếu luồng vẫn còn mức giữ khóa khác.

### 2.2 Khóa tường minh qua API

Java còn có các công cụ khóa tường minh, như `ReentrantLock` trong `java.util.concurrent.locks`:

~~~java
lock.lock();
try {
    // Vùng được bảo vệ
} finally {
    lock.unlock();
}
~~~

Đây cũng là **đoạn minh họa**, giả sử `lock` đã được khởi tạo. Gọi `lock()`, `tryLock()` và `unlock()` là các **lời gọi phương thức API**. Chúng không đồng nhất với các sự kiện monitor nội tại của `synchronized`. Một cơ chế AOP quan sát được lời gọi API nhất định chưa chắc quan sát được lúc một monitor được lấy hoặc nhả; ngược lại cũng vậy.

Đặc biệt, `tryLock()` có thể trả về `false`, nghĩa là **không lấy được khóa**. Với `lockInterruptibly()` hoặc `tryLock(timeout, unit)`, còn có đường bị ngắt hoặc hết thời gian chờ. Phải phân biệt **đã gọi API khóa** với **đã sở hữu khóa**.

## 3. Các điểm khóa/đồng bộ có thể quan tâm

Tùy cơ chế hỗ trợ, mô hình quan sát có thể phân biệt nhiều sự kiện:

| Điểm/sự kiện khái niệm | Ý nghĩa | Điều cần kiểm tra |
| --- | --- | --- |
| **Bắt đầu yêu cầu lấy khóa** | Luồng chuẩn bị thử giành quyền vào vùng bảo vệ | Có thể đo thời gian chờ từ đây không? |
| **Lấy khóa thành công** | Luồng đã đạt quyền thực thi trong vùng bảo vệ | Có đúng là đã lấy thành công, hay chỉ vừa gọi `lock()`? |
| **Đang ở trong vùng đồng bộ** | Chạy mã khi một khóa đã được giữ | Đo độ dài vùng bảo vệ và phát hiện thao tác chậm |
| **Chuẩn bị nhả khóa** | Luồng sắp rời phạm vi bảo vệ hoặc gọi thao tác nhả | Advice có làm phát sinh việc tốn thời gian trong vùng khóa không? |
| **Đã nhả khóa / thoát vùng** | Một bước nhả đã hoàn tất | Trong trường hợp reentrant, liệu khóa đã hoàn toàn tự do chưa? |
| **Không giành được khóa / bị ngắt** | Một lần cố gắng không thành công | Không ghi nhầm là đã sở hữu và không gọi nhả khóa sai |
| **Chờ tín hiệu đồng bộ** | Luồng tạm ngừng và chờ điều kiện | Có thể phải nhả rồi giành lại khóa; không nhầm với chờ lấy khóa ban đầu |

**Đây là bảng phân tích ngữ nghĩa**, không phải danh mục join point được mọi bộ AOP hỗ trợ. Một số cơ chế có thể bộc lộ sự kiện gần thao tác monitor; một số chỉ quan sát được lời gọi phương thức hoặc mã được chủ động bọc; một số không cung cấp loại điểm cần thiết.

Với monitor, “bắt đầu đợi”, “giành được”, “bắt đầu rời” và “đã nhả” không nhất thiết là bốn join point độc lập trong một API. Không nên tự sáng tạo biểu thức pointcut `lock(...)` hay `unlock(...)` rồi khẳng định đó là cú pháp AOP phổ quát.

## 4. Hai trục cần tách: điểm thực thi và trạng thái đồng bộ

Một **join point** chỉ mô tả vị trí mà cơ chế có thể can thiệp. **Trạng thái khóa** là sự thật về quyền sở hữu hoặc việc chờ tài nguyên tại thời điểm chạy.

Ví dụ, nếu chặn lời gọi `lock.lock()`:

1. Advice **before call** chạy khi lời gọi sắp bắt đầu; lúc này luồng có thể chưa có khóa.
2. Thân lời gọi `lock()` có thể đợi luồng khác nhả khóa.
3. Advice **after returning** chạy sau khi `lock()` trả về bình thường; với `ReentrantLock.lock()`, lời gọi bình thường quay về khi luồng đã giành được khóa.
4. Với `tryLock()`, **after returning không có nghĩa đã giành được khóa**; phải kiểm tra giá trị boolean trả về.
5. Với `unlock()`, cần phân biệt “sắp gọi”, “đã gọi xong” và **số lần giữ khóa còn lại**; hoàn tất một lần `unlock()` trên khóa reentrant chưa chắc mở hoàn toàn cho luồng khác.

Tương tự, nếu chặn điểm thực thi một **phương thức có `synchronized`**, phạm vi mà advice bao quanh so với lúc JVM lấy/trả monitor phụ thuộc vị trí advice được kết hợp. Không thể suy bằng tên loại join point rằng phép đo đã bao gồm thời gian chờ khóa.

### 4.1 Bốn thời điểm quan trọng cho một vùng monitor

~~~text
Luồng yêu cầu đi vào vùng synchronized
               |
          [Có thể phải chờ]
               |
           LẤY MONITOR
               |
       Chạy khối được bảo vệ
               |
        RỜI VÙNG ĐỒNG BỘ
               |
           NHẢ MONITOR
               |
           Tiếp tục chạy
~~~

Cách đọc thời gian:

- **Thời gian chờ khóa (acquisition wait):** từ lúc cố giành tới lúc đã giành được. Có thể gần bằng không khi không tranh chấp.
- **Thời gian giữ khóa (hold time):** từ lúc giành tới lúc nhả. Bao gồm mã chạy trong vùng bảo vệ và các thời gian chờ thực sự xảy ra khi còn giữ khóa.
- **Thời gian thao tác tổng thể:** có thể gồm các bước khác trước/sau vùng khóa; không đồng nhất với một trong hai số đo trên.

Mốc “đã giành được” phải nằm **sau** khi thao tác lấy khóa thành công. Mốc “đã nhả” phải nằm **sau** thao tác nhả tương ứng. Nếu hệ thống AOP chỉ can thiệp vào phương thức bên ngoài, ta có thể đo độ trễ của *phương thức*, nhưng không được gọi số đó là **thời gian chờ monitor chính xác** khi chưa chứng minh được ranh giới đo.

## 5. Ví dụ Java 17: quan sát thủ công các mốc khóa

Ví dụ này sử dụng các **hook được đặt bằng tay** để giải thích ý nghĩa mốc sự kiện, không phải một cơ chế AOP tự động cài advice vào bytecode. Nó cho thấy những nơi mà một công cụ có đủ khả năng can thiệp *có thể* muốn quan sát.

Tạo file `LockPointsDemo.java`:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class LockPointsDemo {
    private final Object monitor = new Object();
    private final ReentrantLock explicitLock = new ReentrantLock();
    private int counter;

    void updateWithMonitor() {
        System.out.println("MONITOR attempt");
        try {
            synchronized (monitor) {
                System.out.println("MONITOR acquired");
                try {
                    counter++;
                    System.out.println("MONITOR body=" + counter);
                } finally {
                    System.out.println("MONITOR leaving-body");
                }
            }
        } finally {
            System.out.println("MONITOR region-finished");
        }
    }

    void updateWithExplicitLock() {
        System.out.println("LOCK attempt");
        explicitLock.lock();
        System.out.println("LOCK acquired");
        try {
            counter++;
            System.out.println("LOCK body=" + counter);
        } finally {
            System.out.println("LOCK before-unlock");
            explicitLock.unlock();
            System.out.println("LOCK after-unlock");
        }
    }

    public static void main(String[] args) {
        LockPointsDemo demo = new LockPointsDemo();
        demo.updateWithMonitor();
        demo.updateWithExplicitLock();
        System.out.println("RESULT " + demo.counter);
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac LockPointsDemo.java
java LockPointsDemo
~~~

Stdout chính xác của chương trình đơn luồng này:

~~~text
MONITOR attempt
MONITOR acquired
MONITOR body=1
MONITOR leaving-body
MONITOR region-finished
LOCK attempt
LOCK acquired
LOCK body=2
LOCK before-unlock
LOCK after-unlock
RESULT 2
~~~

**Giải thích các mốc:**

- `MONITOR attempt` chạy **trước** khối `synchronized`; nó không khẳng định đã lấy được khóa.
- `MONITOR acquired` chạy **trong** khối sau khi JVM đã lấy monitor thành công.
- `MONITOR leaving-body` chạy trong `finally` **vẫn ở trong vùng monitor**, trước khi JVM rời khối.
- `MONITOR region-finished` thuộc `finally` bên ngoài khối. Trong ví dụ, `monitor` luôn khác null, nên một khi vùng được vào, việc rời khối (kể cả lỗi trong thân) kéo theo nhả mức khóa tương ứng trước khi chạy `finally` này.
- `LOCK attempt` là mốc trước lời gọi `lock()`; `LOCK acquired` chạy khi lời gọi trả về bình thường.
- `LOCK before-unlock` là mốc khi luồng vẫn đang nắm khóa; `LOCK after-unlock` nằm sau khi `unlock()` hoàn tất bình thường.
- `RESULT 2` thể hiện cả hai thao tác tăng biến đếm, không hề chứng minh công cụ AOP đã can thiệp.

Ví dụ chỉ có **một luồng**, nên không mô phỏng tranh chấp. Nó ưu tiên trình bày thứ tự mốc rõ ràng. Khi thêm nhiều luồng, thứ tự log giữa các luồng sẽ phụ thuộc lịch chạy, số đo thời gian không cố định và log quá nhiều bên trong vùng khóa có thể thay đổi chính hiện tượng cần quan sát.

## 6. Nếu có hỗ trợ join point khóa, aspect sẽ làm gì?

Bảng dưới tách ba trách nhiệm AOP khỏi cơ chế đồng bộ:

| Trách nhiệm AOP | Câu hỏi | Ví dụ áp dụng cho khóa |
| --- | --- | --- |
| **Chọn điểm (pointcut)** | Quan sát vị trí nào? | Những điểm thử lấy hoặc nhả khóa trong các thành phần được theo dõi |
| **Binding ngữ cảnh** | Advice nhận dữ liệu nào? | Loại thao tác, thông tin vị trí mã, định danh quan sát của khóa, luồng liên quan, thời điểm |
| **Advice** | Làm gì tại điểm được chọn? | Bắt đầu/dừng đồng hồ, tăng bộ đếm tranh chấp, ghi cảnh báo nếu vùng khóa kéo dài |
| **Weaving** | Kết hợp xử lý bằng cách nào? | Cơ chế tương thích với join point mong muốn: bọc lời gọi, biến đổi mã hoặc công cụ quan sát thích hợp |

Ví dụ mô tả chính sách *bằng giả mã khái niệm*, không phải pointcut hợp lệ của mọi công cụ:

~~~text
when attemptAcquire(lock):
    startWaitTimer(currentOperation, lock)

when acquired(lock):
    stopWaitTimer(currentOperation, lock)
    startHoldTimer(currentOperation, lock)

when releaseCompleted(lock):
    stopHoldTimer(currentOperation, lock)
~~~

Mô hình trên chỉ đúng nếu hệ thống thực sự cung cấp đủ các sự kiện với thứ tự, tính ghép cặp và ngữ nghĩa đã nêu. Với khóa reentrant, một luồng có thể giành cùng khóa nhiều lần; một cặp thời điểm đơn giản có thể **đếm sai thời gian giữ khóa** khi chưa theo dõi mức lồng nhau. Với phép lấy khóa thất bại, không được phát sinh sự kiện “đã giành được”; với `tryLock` trả `false`, cần kết thúc phép thử mà không mở bộ đếm giữ khóa.

Không nên đưa dữ liệu có độ phân biệt cực lớn như danh tính của mọi đối tượng khóa vào nhãn metrics công khai theo mặc định: số lượng nhãn có thể tăng quá lớn. Một hệ thống nên có chiến lược lấy mẫu, gom nhóm và dọn dẹp trạng thái theo vòng đời phép đo.

## 7. Khi không có join point monitor: có thể quan sát điều gì?

Nếu cơ chế AOP chỉ chặn lời gọi qua giao diện hoặc phương thức, vẫn có thể:

- Đo tổng thời gian một phương thức xử lý, dù bên trong có dùng `synchronized` hay khóa tường minh.
- Theo dõi lời gọi tới một API khóa *nếu* những lời gọi đó đi qua điểm can thiệp của cơ chế.
- Chủ động đưa thao tác khóa vào một wrapper do chương trình sử dụng, đặt hook trước và sau việc lấy/nhả khóa.
- Dùng các công cụ quan sát runtime hoặc phân tích luồng độc lập khi cần chẩn đoán tranh chấp sâu hơn.

Nhưng phải nói chính xác **đang đo cái gì**. Đo thời gian `service.update()` không tự động là đo thời gian chờ khóa; đo `lock()` không tự động bao trùm `synchronized`; đếm số lần gọi `unlock()` không nhất thiết là đếm số lần khóa trở nên tự do.

Đặc biệt, `synchronized` là cấu trúc đồng bộ có ngữ nghĩa của ngôn ngữ/JVM. Một proxy bên ngoài phương thức không thể tự “nhìn vào” từng thao tác monitor trong mọi khối lồng nhau. Muốn quan sát chính các thao tác này cần khả năng ở cấp phù hợp, chẳng hạn instrument mã hoặc bytecode tại đúng vị trí; **khả năng quan sát sự kiện runtime** cũng không đồng nghĩa **khả năng thực thi advice tùy ý tại sự kiện đó**.

Một API AOP có thể định nghĩa tên hằng hoặc cấu trúc mô tả sự kiện khóa nhưng phiên bản công cụ đang dùng vẫn không có quy tắc chọn hoặc weaving tương ứng. Vì thế phải kiểm tra **danh mục join point được hỗ trợ trong phiên bản và chế độ triển khai thực tế**, thay vì suy từ tên API.

## 8. Trường hợp nâng cao cần đặc biệt chú ý

### 8.1 Reentrancy và cân bằng lấy/nhả

Với `synchronized` và `ReentrantLock`, cùng luồng có thể tái nhập khóa đang giữ. Trạng thái “đang giữ” không nhất thiết chuyển về “không giữ” sau *một* lần thoát vùng trong cùng chuỗi lồng nhau.

Một aspect thống kê cần hiểu ngữ nghĩa của bộ đếm sở hữu hoặc theo dõi các cặp acquisition/release lồng nhau. Nếu chỉ đếm mỗi lần “nhả” là một lần mở khóa hoàn toàn, báo cáo thời gian giữ và số lần tranh chấp sẽ bị sai.

### 8.2 `wait`, `notify` và `Condition.await`

`Object.wait()` làm luồng tạm nhả monitor của **đối tượng được wait**, chờ rồi giành lại monitor đó trước khi tiếp tục. `notify()` hoặc `notifyAll()` đánh thức các luồng đang chờ theo quy tắc của monitor nhưng **không lập tức nhả monitor** của luồng đang gọi.

Với `Condition.await()` gắn với một khóa phù hợp, việc chờ thường bao gồm nhả rồi giành lại khóa theo hợp đồng của điều kiện. Vì vậy, đo “thời gian một đoạn phương thức đang chạy” không thể tự coi tất cả thời gian đó là “thời gian đang giữ khóa”. Muốn tính chính xác phải biết những pha chờ nào làm khóa được nhả tạm thời.

### 8.3 Lấy khóa thất bại, timeout, interrupt

Với khóa tường minh:

- `tryLock()` có thể trả `false`; không được gọi `unlock()` khi lần thử đó không sở hữu khóa.
- `tryLock(timeout, unit)` có thể thất bại vì hết thời gian hoặc ném `InterruptedException`.
- `lockInterruptibly()` có thể bị ngắt trong khi chờ; `lock()` có ngữ nghĩa phản ứng với ngắt khác.

Advice phải thể hiện đúng các nhánh kết quả. Bộ thống kê “attempt”, “acquired”, “failed” và “released” là các đại lượng khác nhau, không nên cộng/trừ máy móc mà bỏ qua reentrancy và các trường hợp thoát bất thường.

### 8.4 Tránh tạo tranh chấp mới trong advice

Advice chạy ở điểm khóa có thể tự ghi log đồng bộ, lấy khóa phụ, gửi dữ liệu mạng hoặc cấp phát bộ nhớ. Điều đó làm vùng giữ khóa dài thêm và có thể tạo chu kỳ khóa dẫn tới deadlock. Đặc biệt, không tùy tiện lấy một khóa thứ hai trong advice đang chạy khi khóa thứ nhất còn được giữ: thứ tự khóa khác nhau giữa các luồng có thể gây bế tắc.

Nên ưu tiên thu thập sự kiện ngắn, giới hạn overhead, sử dụng thời gian đo đơn điệu, xử lý kết xuất bất đồng bộ ở nơi thích hợp và tránh phụ thuộc lặp lại vào chính điểm đang instrument. Cần đề phòng **advice tự kích hoạt lại chính nó** nếu thao tác ghi log hoặc thống kê cũng thỏa pointcut.

### 8.5 Can thiệp không được phá ngữ nghĩa đồng bộ

AOP có thể dùng để quan sát hoặc phối hợp chính sách, nhưng không thể coi việc viết một advice là bằng chứng chương trình đã đồng bộ đúng. Mã cần bảo vệ vẫn phải dùng khóa và nguyên tắc thiết kế hợp lệ. Không được tùy ý chuyển `unlock` sang luồng khác với khóa yêu cầu đúng chủ sở hữu, bỏ qua nhánh `finally`, hoán đổi thứ tự lấy khóa hoặc gọi phần nghiệp vụ nhiều lần mà không hiểu hậu quả.

Việc nhả rồi giành lại khóa cũng liên quan đến **quan hệ bộ nhớ** giữa các luồng. Những bảo đảm hiển thị dữ liệu đến từ ngữ nghĩa đồng bộ của ngôn ngữ/thư viện; việc log một join point không tự tạo hoặc thay thế đúng các bảo đảm đó.

## 9. Phân biệt các đại lượng khi phân tích performance

| Đại lượng | Cần mốc nào? | Không nên suy nhầm thành |
| --- | --- | --- |
| Số lần **thử** lấy khóa | Mỗi yêu cầu/attempt | Số lần lấy thành công |
| Số lần **lấy thành công** | Kết quả acquisition hợp lệ | Số luồng khác nhau hoặc số lần khóa trở nên tự do |
| **Thời gian chờ** | Từ attempt đến acquired hoặc kết thúc thất bại | Thời gian chạy toàn bộ vùng bảo vệ |
| **Thời gian giữ** | Từ acquired tới release tương ứng, có xét lồng nhau/chờ điều kiện | Thời gian CPU thực sử dụng |
| **Độ trễ thao tác** | Mốc đầu/cuối nghiệp vụ | Chỉ riêng thời gian contention |
| Số lần **timeout/interrupt** | Nhánh kết quả tương ứng | Một lần nhả khóa |

**Ví dụ suy luận:** một thao tác mất 120 ms, trong đó 80 ms chờ khóa, 35 ms ở trong vùng khóa và 5 ms ở ngoài. Nếu chỉ đặt around advice tại phương thức, ta có thể thấy khoảng 120 ms nhưng không thể tách 80/35/5 ms nếu thiếu các mốc khóa tương ứng. Ngược lại, nếu đo ngay trong `synchronized` thì đã qua giai đoạn chờ lấy monitor; số đo đó không phản ánh 80 ms chờ trước khi vào.

Các số trong tình huống này là **giả định để minh họa cách đọc số liệu**, không phải output của một chương trình đo cụ thể.

## 10. Các lỗi hiểu sai thường gặp

1. **“Có advice cho phương thức thì mặc nhiên có advice cho monitor.”** Sai. Biên lời gọi/phương thức và thao tác monitor là hai loại điểm khác nhau; khả năng hỗ trợ phải được xác minh.
2. **“Thấy `lock()` được gọi nghĩa là đã có khóa.”** Sai trước khi lời gọi hoàn tất; với `tryLock` còn phải xét kết quả `true/false`.
3. **“Thấy `unlock()` là khóa đã hoàn toàn tự do.”** Sai với reentrancy nếu số lần giữ còn dương.
4. **“Sau khi `notify()` thì luồng gọi đã nhả monitor.”** Sai; việc gửi tín hiệu không tự kết thúc vùng giữ monitor.
5. **“`finally` bên trong `synchronized` chạy sau khi nhả monitor.”** Sai: nó nằm trong vùng đồng bộ; chỉ sau khi rời khối mới có thao tác nhả tương ứng của khối đó.
6. **“Mọi đo lường quanh phương thức đều đo được contention.”** Sai khi thiếu mốc trước/sau acquisition hoặc cơ chế không nhìn thấy khóa bên trong.
7. **“Thêm advice đo/log không ảnh hưởng tranh chấp.”** Sai: chính advice thêm thời gian và đôi khi lấy thêm khóa.
8. **“Một kiểu pointcut khóa luôn có trong mọi hệ thống AOP.”** Sai. Các sự kiện và cú pháp phụ thuộc mô hình join point, công cụ và phiên bản.

## 11. Kiểm thử đúng phạm vi và ngữ nghĩa

Khi thiết kế aspect quan sát khóa, hãy xác nhận:

- **Khả năng hỗ trợ:** đúng loại join point có tồn tại và được phép weave trong chế độ đang dùng? Có bao phủ cả khối `synchronized`, phương thức đồng bộ, và khóa tường minh hay không?
- **Đường thành công:** attempt → acquired → release được ghi đúng thứ tự cho từng lần lấy thực.
- **Đường thất bại:** timeout, `false`, interrupt và ngoại lệ không được ghi nhầm là đã lấy.
- **Đường lồng nhau:** cùng luồng lấy lại khóa nhiều lần không làm bộ đo đánh dấu “khóa hoàn toàn tự do” quá sớm.
- **Đường chờ điều kiện:** `wait`/`await` có thể làm thay đổi tình trạng sở hữu tạm thời; phép đo hold time cần xử lý phù hợp.
- **Tính an toàn:** advice không lấy thêm khóa gây deadlock, không thay kết quả/ngoại lệ của nghiệp vụ và không giữ dữ liệu ngữ cảnh lâu hơn cần thiết.
- **Tính đo lường:** overhead thấp và rõ ràng; khi chạy dưới contention thật, metrics phản ánh đúng loại số đo đã công bố.

Một kiểm thử chỉ thấy log “LOCK acquired” xuất hiện là chưa đủ. Cần kiểm chứng điểm đặt log thật sự nằm **sau acquisition**, cùng luồng sở hữu đúng khóa và mọi đường thất bại đều được phản ánh trung thực.

## 12. Bài tập tư duy

Một nhóm muốn cảnh báo khi “mọi vùng `synchronized` giữ monitor trên 50 ms”. Công cụ AOP hiện tại chỉ bọc lời gọi vào các phương thức công khai.

**Câu hỏi 1 — Có đủ thông tin không?** Chưa. Đo thời gian toàn phương thức không chỉ đo riêng các vùng `synchronized`; một phương thức có thể có nhiều vùng đồng bộ, hoặc chờ monitor trước khi vào vùng.

**Câu hỏi 2 — Cần khả năng gì?** Cần quan sát được các mốc giành và nhả monitor (hoặc có hook tin cậy ngay sát ranh giới), kết hợp đúng cặp mốc và tính đến trường hợp lồng nhau hay chờ điều kiện.

**Câu hỏi 3 — Nếu công cụ không hỗ trợ thì sao?** Có thể instrument thủ công vùng quan trọng, dùng wrapper có phạm vi rõ ràng với khóa tường minh hoặc dùng công cụ chẩn đoán runtime phù hợp. Phải đặt lại tên đại lượng nếu chỉ đo được tổng độ trễ phương thức.

**Câu hỏi 4 — Có nên gửi log mạng ngay trước khi nhả monitor không?** Không nên. Nó kéo dài thời gian giữ khóa và có thể tạo tranh chấp phụ. Thu thập số liệu tối thiểu tại điểm đo rồi xử lý chúng ngoài vùng nóng là hướng tiếp cận thường phù hợp hơn.

**Ghi nhớ:** điểm liên quan khóa và đồng bộ chỉ trở thành **join point có thể can thiệp** khi mô hình AOP hỗ trợ rõ ràng. Khi phân tích, phải tách **lúc thử lấy**, **lúc lấy thành công**, **lúc đang giữ**, **lúc nhả**, **lúc thử thất bại** và **lúc chờ điều kiện**. Làm được điều đó giúp aspect đo lường đúng sự kiện, bảo toàn ngữ nghĩa khóa và tránh đưa rủi ro mới vào chương trình đa luồng.

## Tài liệu tham khảo kỹ thuật

- [Java SE 17, JLS §14.19 — `synchronized` statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.19)
- [Java SE 17, JLS §17.1 — Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1)
- [Java SE 17 — `ReentrantLock` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)
- [AspectJ Programming Guide — Join Points and Pointcuts](https://eclipse.dev/aspectj/doc/latest/progguide/language.html) (tài liệu tham khảo về mô hình join point; cú pháp và khả năng hỗ trợ phải xác minh theo công cụ đang dùng).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-007">Thông tin tĩnh và ngữ cảnh động của một join point</a>

<details>
<summary>Xem chi tiết</summary>

# Thông tin tĩnh và ngữ cảnh động của một join point trong AOP

## 1. Vấn đề cốt lõi: một điểm được mô tả trước, nhiều lần xuất hiện khi chạy

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **join point** là điểm trong mô hình thực thi mà cơ chế AOP có thể quan sát hoặc can thiệp. Ví dụ: một lần thực thi phương thức, một vị trí gọi phương thức, hoặc một lần đọc trường dữ liệu, nếu mô hình join point của công cụ hỗ trợ loại đó.

Khi phân tích một join point, cần tách hai câu hỏi:

1. **Thông tin tĩnh (static information):** đây là loại điểm nào, gắn với thành phần mã nào, chữ ký nào, vị trí mã nào? Những đặc điểm này có thể xác định từ cấu trúc chương trình và mô hình weaving trước khi biết dữ liệu cụ thể của từng lần chạy.
2. **Ngữ cảnh động (dynamic context):** lần thực thi hiện tại đang xảy ra trên đối tượng nào, với tham số gì, trong điều kiện nào, kết thúc bằng kết quả hay ngoại lệ nào? Những thông tin này chỉ có thể biết tại thời điểm thích hợp của một lần chạy cụ thể.

Ví dụ, cùng một phương thức:

```java
String transfer(String account, long amount) {
    return account + ":" + amount;
}
```

Đây là **đoạn minh họa** trong một kiểu Java, không phải chương trình độc lập. Chữ ký `transfer(String,long):String` không đổi giữa các lần gọi. Nhưng `transfer("A", 100)` và `transfer("B", 500)` có tham số và kết quả khác nhau.

Nếu một aspect ghi log phương thức, thông tin tĩnh giúp xác định **đang áp dụng vào phương thức nào**; ngữ cảnh động giúp xác định **lần gọi nào**, **dữ liệu nào**, và **đã xảy ra điều gì**.

**Mấu chốt:** tĩnh không có nghĩa “không bao giờ thay đổi trong suốt đời phần mềm”; nó nghĩa là đặc điểm của **vị trí/kiểu join point trong phiên bản mã đang xét**, tương đối độc lập với từng lần xuất hiện động. Khi thay mã, nạp lớp khác hoặc thay cấu hình weaving, tập thông tin tĩnh có thể thay đổi.

## 2. Khái niệm join-point shadow và lần xuất hiện động

Trong các mô hình AOP có phân biệt hai tầng, **join-point shadow** là vị trí hoặc cấu trúc mã tĩnh *có thể phát sinh* một hay nhiều join point khi chạy. Mỗi lần luồng điều khiển thực sự đi qua vị trí đó có thể tạo ra **một lần xuất hiện động (dynamic occurrence)**.

Minh họa:

```java
for (int i = 1; i <= 3; i++) {
    gateway.pay("ORDER-" + i, i * 100);
}
```

Đây là mã minh họa, trong đó `gateway` giả định là đối tượng có phương thức `pay`. Lệnh gọi `gateway.pay(...)` xuất hiện **một vị trí tĩnh** nhưng vòng lặp tạo ra **ba lần gọi động**:

```text
Vị trí tĩnh: gateway.pay(String, int)
   |
   +-- Lần 1: pay("ORDER-1", 100)
   +-- Lần 2: pay("ORDER-2", 200)
   +-- Lần 3: pay("ORDER-3", 300)
```

Tùy mô hình, vị trí trên có thể đại diện một *method-call join point shadow*; khi phần thân `pay` được chạy, có thể phát sinh một *method-execution join point* khác ở phía phương thức được gọi.

Hai hệ quả cần nhớ:

- Không được suy ra **số lần advice chạy** chỉ từ số vị trí mã nguồn. Một shadow có thể được đi qua nhiều lần, không lần nào hoặc lồng nhau trong đệ quy.
- Không phải shadow tiềm năng nào cũng được advice xử lý. Pointcut có thể chọn theo điều kiện tĩnh hoặc kiểm tra thêm điều kiện động; phạm vi weaving còn phụ thuộc công cụ.

Thuật ngữ *shadow* không bắt buộc có trong mọi API AOP, nhưng cách phân biệt **vị trí trong mã** và **lần thực thi cụ thể** là nền tảng để hiểu chủ đề này.

## 3. Những gì thường được xem là thông tin tĩnh

Thông tin tĩnh mô tả hình dạng/định danh của join point. Cụ thể đến đâu phụ thuộc loại join point và metadata mà công cụ cung cấp.

| Thông tin tĩnh | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Loại join point (kind)** | Sự kiện nào đang được mô hình hóa | Method call, method execution, field read |
| **Chữ ký (signature)** | Tên, kiểu khai báo, danh sách kiểu tham số, kiểu trả về nếu áp dụng | `PaymentService.pay(String,long):Receipt` |
| **Kiểu khai báo (declaring type)** | Kiểu mà thành viên/chữ ký được khai báo | `PaymentService` |
| **Modifier/visibility** | Các thuộc tính như `public`, `static` nếu được biểu diễn | `public` |
| **Vị trí mã (source location)** | Tệp/dòng/vị trí được liên kết với shadow nếu có | `Checkout.java:42` |
| **Đặc tính cấu trúc khác** | Quan hệ kiểu, annotation, kiểu field, constructor… | Phương thức có dấu đánh dấu kiểm toán |

Một số lưu ý quan trọng:

1. **Source location không luôn tồn tại.** Mã tạo tự động, lớp biên dịch không kèm thông tin gỡ lỗi, mã native hoặc nền tảng không lưu ánh xạ nguồn có thể không cung cấp tệp/dòng.
2. **Chữ ký của điểm gọi và điểm thực thi có thể khác góc nhìn.** Một call join point thường gắn với vị trí gọi; execution join point gắn với phần thân được thực thi. Đừng mặc định chữ ký/đối tượng nhìn từ bên gọi luôn trùng với đích thực tế.
3. **Kiểu khai báo khác kiểu runtime.** Giao diện `PaymentGateway` có thể khai báo `pay`, còn lúc chạy đối tượng thực tế là `RemotePaymentGateway`.
4. **Cùng tên không đủ định danh phương thức.** Java có overload, nên `pay(String)` và `pay(String,long)` là hai chữ ký khác nhau.
5. **Thông tin tĩnh là metadata, không phải bảo đảm can thiệp.** Việc công cụ đọc được chữ ký chưa chứng minh mọi đường gọi đã được weaving.

### 3.1. “Tĩnh” tương đối với chính join point đang xét

Nếu một ứng dụng có cùng chữ ký phương thức ở hai lớp khác nhau thì thông tin tĩnh của chúng vẫn khác bởi **kiểu khai báo**. Nếu một phương thức xuất hiện ở nhiều vị trí gọi, các **call shadows** có thể có source location khác nhau nhưng cùng nhắm tới chữ ký liên quan.

Điều này quan trọng khi yêu cầu là “theo dõi *mọi lần thực thi của một phương thức*” so với “theo dõi *lời gọi xuất phát từ một tệp/đoạn mã cụ thể*”. Hai yêu cầu có thể cần chọn loại join point và tiêu chí tĩnh khác nhau.

## 4. Những gì thường thuộc ngữ cảnh động

Ngữ cảnh động thuộc về **một lần xuất hiện cụ thể** của join point. Tùy loại điểm và kiểu advice, hệ thống có thể cung cấp các dữ liệu sau:

| Ngữ cảnh động | Ví dụ | Thời điểm/giới hạn |
| --- | --- | --- |
| **Đối tượng đang thực thi (nếu có)** | Instance của `CheckoutService` | Không phải join point nào cũng có một đối tượng |
| **Đối tượng đích nhận thao tác (nếu có)** | Instance của `PaymentGatewayImpl` | Khác đối tượng bên gọi trong nhiều trường hợp |
| **Giá trị tham số** | `["A-01", 500]` | Có khi bắt đầu lời gọi; có thể bị thay đổi bởi advice/cơ chế |
| **Thông tin thread hoặc execution context** | Thread hiện tại, trace ID | Có thể thay đổi khi chuyển luồng; không luôn tự động lan truyền |
| **Giá trị trả về** | Một `Receipt` | Chỉ biết sau khi hoàn tất bình thường ở điểm tương ứng |
| **Ngoại lệ thoát ra** | `IllegalArgumentException` | Chỉ có trên đường thất bại mà ranh giới quan sát nhận thấy |
| **Thời điểm/duration** | Bắt đầu và thời gian xử lý | Phải tự đo/ghi bằng advice hoặc cơ chế quan sát |
| **Trạng thái runtime liên quan** | Dữ liệu dùng trong điều kiện chọn điểm | Tùy hợp đồng ứng dụng và công cụ |

Không phải mọi mục đều là trường API có sẵn trên đối tượng `JoinPoint`. Ví dụ, **thời gian thực thi** thường phải được đo bằng cách lấy mốc bắt đầu/kết thúc, và **trace ID** có thể nằm trong ngữ cảnh riêng của ứng dụng. Bảng phân biệt thông tin *có thể thu thập/quan sát* với những trường mặc định mà một API nhất định hứa cung cấp.

### 4.1. Giá trị thay đổi trong một lần chạy

Tham số là ngữ cảnh động ngay cả khi **kiểu tham số** là thông tin tĩnh. Một tham số tham chiếu đến đối tượng mutable có thể thay đổi trạng thái trong khi thao tác chạy. Tương tự, giá trị trả về chưa có ở thời điểm before advice, còn ngoại lệ chưa có cho tới khi nhánh ném lỗi thực sự xuất hiện.

Ví dụ:

```text
Static:
  kind = method execution
  signature = OrderService.place(String, int):String

Lần gọi #1:
  target = OrderServiceImpl instance X
  args = ["ORD-7", 2]
  outcome = return "ACCEPTED:ORD-7"

Lần gọi #2:
  target = OrderServiceImpl instance Y
  args = ["ORD-8", -1]
  outcome = throw IllegalArgumentException
```

Bảng thông tin tĩnh của hai lần gọi **có thể giống nhau**, nhưng kết quả động khác hẳn. Tên instance X/Y chỉ là nhãn minh họa; không nên đưa địa chỉ bộ nhớ ngẫu nhiên vào log hay coi identity hash là danh tính nghiệp vụ.

## 5. Thời điểm các thông tin xuất hiện trong luồng advice

Một mô hình đơn giản cho thao tác đồng bộ:

```text
[Có thông tin tĩnh về điểm được chọn]
                 |
                 v
Join point bắt đầu một lần thực thi
  |  + Có tham số thực tế / đối tượng phù hợp (nếu loại điểm hỗ trợ)
  |  + Before advice có thể quan sát ngữ cảnh bắt đầu
  |
  v
Thực thi phần gốc (hoặc lớp around tiếp theo)
  |
  +---- trả về bình thường ----> Có giá trị trả về cho nhánh thành công
  |                                  |
  |                                  +--> After returning advice
  |
  +---- ném ngoại lệ ----------> Có ngoại lệ thoát ra tại ranh giới
                                     |
                                     +--> After throwing advice

Các bước kết thúc/finally của chuỗi advice (nếu được hỗ trợ)
                 |
                 v
Bên gọi nhận kết quả hoặc ngoại lệ sau can thiệp
```

Sơ đồ khái niệm này không ấn định thứ tự mặc định cho mọi công cụ và không bảo đảm tất cả advice chạy trong mọi tình huống. **Around advice** có thể không gọi tiếp phần gốc; nó cũng có thể thay đổi tham số, kết quả hoặc ngoại lệ nếu giải pháp AOP cho phép.

Từ đó rút ra nguyên tắc:

- **Before:** hợp lý để dùng metadata tĩnh, giá trị tham số và đối tượng của lần bắt đầu. Không nên đòi “kết quả đã trả” khi thao tác chưa chạy.
- **After returning:** có thể gắn kết quả với chữ ký và tham số của lần chạy thành công.
- **After throwing:** có thể gắn ngoại lệ thoát ra với điểm và lần thực thi thất bại.
- **Around:** có thể thấy nhiều giai đoạn, đo thời lượng hoặc thay đổi luồng; phải xác định rõ hành vi khi không `proceed`.

**Không có kiểu dữ liệu “result hoặc exception” duy nhất trong mọi join-point API.** Kết quả và ngoại lệ thường do advice hoặc cơ chế interception cung cấp ở giai đoạn kết thúc thích hợp.

## 6. Ví dụ Java 17 chạy được: một chữ ký tĩnh, hai ngữ cảnh động

Java thông thường không tự có cú pháp aspect/pointcut thống nhất. Ví dụ dưới đây sử dụng **proxy giao diện của JDK** để quan sát các lời gọi đi qua proxy. `java.lang.reflect.Method` cung cấp một số metadata tương ứng thông tin tĩnh; `target`, `arguments` và kết quả/ngoại lệ của từng lời gọi là phần ngữ cảnh động được handler thu thập.

Đây là **mô hình minh họa interception có giới hạn**, không phải hiện thực của mọi loại join point AOP. Nó chỉ quan sát các lời gọi được chuyển qua proxy đang tạo.

Lưu vào `JoinPointContextDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Collectors;

public class JoinPointContextDemo {

    interface Operation {
        String execute(String requestId, int units);
    }

    static final class DefaultOperation implements Operation {
        @Override
        public String execute(String requestId, int units) {
            System.out.println("business: " + requestId + " x " + units);
            if (units < 0) {
                throw new IllegalArgumentException("units must be nonnegative");
            }
            return requestId + ":" + units;
        }
    }

    public static void main(String[] args) {
        Operation target = new DefaultOperation();

        Operation proxy = (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[] { Operation.class },
                (ignoredProxy, method, arguments) -> invoke(target, method, arguments)
        );

        System.out.println("caller: " + proxy.execute("JOB-7", 2));

        try {
            proxy.execute("BAD", -1);
        } catch (IllegalArgumentException ex) {
            System.out.println("caller caught: " + ex.getMessage());
        }
    }

    private static Object invoke(Operation target, Method method, Object[] arguments)
            throws Throwable {
        String parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));

        String signature = method.getDeclaringClass().getSimpleName()
                + "." + method.getName()
                + "(" + parameterTypes + "):"
                + method.getReturnType().getSimpleName();

        System.out.println("static: " + signature);
        System.out.println("dynamic: target=" + target.getClass().getSimpleName()
                + ", args=" + Arrays.toString(arguments));

        try {
            Object result = method.invoke(target, arguments);
            System.out.println("outcome: return " + result);
            return result;
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            System.out.println("outcome: throw " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
            throw cause;
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac JoinPointContextDemo.java
java JoinPointContextDemo
```

Đầu ra xác định:

```text
static: Operation.execute(String,int):String
dynamic: target=DefaultOperation, args=[JOB-7, 2]
business: JOB-7 x 2
outcome: return JOB-7:2
caller: JOB-7:2
static: Operation.execute(String,int):String
dynamic: target=DefaultOperation, args=[BAD, -1]
business: BAD x -1
outcome: throw IllegalArgumentException: units must be nonnegative
caller caught: units must be nonnegative
```

### 6.1. Giải thích sự khác nhau giữa các dòng

- Hai lần đều in `static: Operation.execute(String,int):String`. Tại handler, đối tượng `Method` mô tả cùng phương thức giao diện, vì vậy tên, kiểu tham số và kiểu trả về không thay đổi giữa hai lời gọi.
- `target=DefaultOperation` là loại đối tượng thực được chuyển tiếp tới trong ví dụ; đây là **thông tin runtime** được suy từ `target`. Việc cả hai lần có cùng loại target không biến nó thành chữ ký tĩnh.
- Mảng tham số `[JOB-7, 2]` và `[BAD, -1]` khác nhau; đây là **ngữ cảnh động**.
- Sau khi phần nghiệp vụ chạy, kết quả thành công `JOB-7:2` chỉ có ở lần thứ nhất. Lần thứ hai phát sinh `IllegalArgumentException`; handler gỡ lớp bọc `InvocationTargetException` do reflection tạo ra rồi ném lại lỗi gốc.
- Caller nhận kết quả hoặc bắt ngoại lệ **sau khi** cơ chế chặn hoàn tất phần xử lý của nó.

### 6.2. Chính xác thì ví dụ mô phỏng điều gì?

Proxy minh họa cách tách **metadata tương đối ổn định** khỏi **dữ liệu theo invocation**. Nó không có bộ pointcut tổng quát, không cho phép mặc nhiên can thiệp vào field access hay mọi lời gọi nội bộ, và `Method` trong handler là phương thức tương ứng trên giao diện proxy, không nhất thiết là mọi metadata của một execution join point trong công cụ weaving thực thụ.

Cũng không nên suy rằng `getDeclaringClass()` luôn trả về **lớp cài đặt thực tế**. Ở ví dụ này, nó trả `Operation` vì handler nhận `Method` của giao diện; `target.getClass().getSimpleName()` trả `DefaultOperation`. Đây chính là lý do cần phân biệt **declaring type** với **runtime target type**.

### 6.3. Điều không được suy từ kết quả

Ví dụ không chứng minh mọi công cụ AOP đều thu được source location, caller object, tên tham số nguồn, giá trị trả về ở before advice, hay toàn bộ ngữ cảnh của cuộc gọi được thực thi bên ngoài proxy. Nó chỉ chứng minh những gì handler JDK đang thực sự quan sát.

## 7. Static signature, declaring type và runtime target khác nhau thế nào?

Trong Java, tính đa hình khiến cùng một chữ ký nhìn từ bên gọi có thể dẫn tới các hiện thực khác nhau.

```java
interface Formatter {
    String format(String value);
}

final class UpperFormatter implements Formatter {
    public String format(String value) {
        return value.toUpperCase(java.util.Locale.ROOT);
    }
}

final class LowerFormatter implements Formatter {
    public String format(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
```

Đoạn trên chỉ minh họa kiểu và cài đặt; không có `main` riêng. Với lời gọi `Formatter formatter = ...; formatter.format("AbC")`:

- Chữ ký quan sát từ kiểu giao diện có thể là `Formatter.format(String):String`.
- Nếu đối tượng thực là `UpperFormatter`, kết quả là `"ABC"`.
- Nếu đối tượng thực là `LowerFormatter`, kết quả là `"abc"`.
- Tùy kiểu join point, thông tin tĩnh có thể gắn với **vị trí lời gọi**, **khai báo trên giao diện** hoặc **điểm thực thi của cài đặt**.

Đừng áp đặt một cách diễn giải duy nhất cho toàn bộ mô hình AOP. Trước khi thiết kế pointcut/advice, phải xác định rõ: **đang quan sát method call hay method execution, và metadata do giải pháp cụ thể trả về mang ngữ nghĩa nào?**

### 7.1. Overload và override không giống nhau

- **Overload:** `pay(String)` và `pay(String,long)` có các danh sách kiểu tham số khác nhau; chỉ dùng tên `pay` là không đủ để định danh.
- **Override:** lớp con ghi đè một phương thức có cùng chữ ký tương ứng; kiểu runtime có thể quyết định phần thực thi thật sự.
- **Generic/bridge methods:** trình biên dịch Java có thể sinh phương thức cầu nối để giữ ngữ nghĩa đa hình sau type erasure. Công cụ chặn/weaving có thể thấy metadata mức bytecode khác với hình dung ở mã nguồn.

Vì vậy, đừng xây các chính sách quan trọng chỉ từ chuỗi tên phương thức; hãy hiểu cách xác định chữ ký và điểm thực thi của nền tảng.

## 8. Đối tượng bên gọi, đối tượng đích và đối tượng hiện tại

Một mô hình join point có thể phân biệt nhiều vai trò:

- **Caller:** thành phần kích hoạt một lời gọi, nếu loại join point cho phép nhận diện.
- **Current/this object:** đối tượng gắn với phần mã đang chạy ở một phía của điểm thực thi, tùy định nghĩa ngữ nghĩa của công cụ.
- **Target object:** đối tượng nhận thao tác hoặc đối tượng đích liên quan đến join point.
- **Proxy/interceptor object:** đối tượng trung gian mà caller có thể thấy ở một số cơ chế triển khai.

Chúng có thể trùng nhau hoặc khác nhau. Với lời gọi `checkoutService.checkout()` bên trong có `gateway.charge()`, `checkoutService` có thể là phía gọi còn `gateway` là đích, trong khi một proxy có thể đứng giữa.

Ví dụ sơ đồ:

```text
CheckoutService instance X
    |
    | gateway.charge("ORD-7")
    v
Gateway proxy P (nếu có)
    |
    | chuyển tiếp lời gọi
    v
GatewayImpl instance Y
```

Nếu advice nhận được các đối tượng đó, phải biết công cụ mô tả chúng ở **loại join point nào**. Không nên giả định `this`, `target`, proxy và caller luôn là một đối tượng. Ở điểm `static` hoặc một loại sự kiện không gắn với instance, một số vai trò thậm chí không tồn tại.

Quan trọng hơn, **identity của đối tượng** khác **loại của đối tượng**: `GatewayImpl` có thể dùng chung cho hàng nghìn lần gọi; từng instance khác nhau vẫn có cùng tên lớp. Đừng dựa vào `toString()` mặc định hoặc địa chỉ bộ nhớ thay cho mã nghiệp vụ/trace ID ổn định.

## 9. Ngữ cảnh kết quả và ngoại lệ không có cùng vòng đời

Cùng một lần thực thi có thể được quan sát tại nhiều giai đoạn:

```text
Before:
  static  = kind/signature/location
  dynamic = target + args + context khả dụng

Proceed:
  phần nghiệp vụ gốc chạy

After returning:
  kết quả trả về + context gắn với lần thực thi

After throwing:
  ngoại lệ thoát ra + context gắn với lần thực thi

After/finally:
  hành vi kết thúc theo ngữ nghĩa advice của công cụ
```

Ví dụ phương thức:

```java
String lookup(int code) {
    if (code < 0) {
        throw new IllegalArgumentException("bad code");
    }
    return "VALUE-" + code;
}
```

Đây là đoạn minh họa, không độc lập có `main`. Một advice ở thời điểm trước khi chạy có thể biết `code=-1` và chữ ký, nhưng **chưa có ngoại lệ thoát ra**. Một advice quan sát sau khi ném lỗi có thể biết ngoại lệ `IllegalArgumentException` vì khi đó đường thực thi đã thất bại.

### 9.1. Ngoại lệ được bắt bên trong không phải ngoại lệ thoát khỏi join point

```java
String safeLookup(int code) {
    try {
        return lookup(code);
    } catch (IllegalArgumentException ex) {
        return "FALLBACK";
    }
}
```

Nếu join point đang quan sát là **method execution của `safeLookup`**, nó có thể kết thúc bình thường với `"FALLBACK"`. Ngoại lệ phát sinh tại `lookup` đã được xử lý trước khi thoát khỏi `safeLookup`. Muốn quan sát chính exception handler hay lần thực thi `lookup`, cần điểm can thiệp tương ứng **nếu công cụ hỗ trợ**.

### 9.2. Advice cũng có thể thay đổi thứ mà lớp ngoài nhìn thấy

Một around advice có thể bắt ngoại lệ rồi trả fallback, hoặc tự ném ngoại lệ mới. Advice ở vòng ngoài khi đó thấy kết quả/ngoại lệ sau chuyển đổi, có thể khác kết quả/ngoại lệ gốc. Vì thế, khi phân tích **dynamic outcome**, phải xác định **ranh giới đang quan sát**: phần thân nghiệp vụ, một lớp advice bên trong hay toàn bộ chuỗi advice.

## 10. Điều kiện tĩnh và điều kiện động trong pointcut

Một pointcut có thể kết hợp điều kiện dựa trên cấu trúc chương trình và điều kiện dựa trên lần chạy. Cách biểu đạt cụ thể tùy giải pháp AOP.

Mã giả (không phải cú pháp Java hoặc cú pháp chung cho mọi công cụ):

```text
pointcut largeTransfer:
    kind == METHOD_EXECUTION
    AND signature == TransferService.transfer(String,long)
    AND runtimeArgument("amount") > 1000

before largeTransfer(context):
    recordAudit(context.signature, context.arguments)
```

Phân tích:

- `kind == METHOD_EXECUTION` là điều kiện theo **loại join point**, thuộc cấu trúc tĩnh.
- `signature == ...` là điều kiện **theo chữ ký**.
- `amount > 1000` là điều kiện **theo giá trị động** của một lần thực thi.
- `recordAudit` sử dụng cả metadata tĩnh và dữ liệu động.

Cùng một shadow có thể đi qua nhiều lần:

| Lần gọi | Chữ ký | Amount | Pointcut có khớp? |
| --- | --- | ---: | --- |
| #1 | `transfer(String,long)` | 100 | Không |
| #2 | `transfer(String,long)` | 1001 | Có |
| #3 | `transfer(String,long)` | 5000 | Có |

Bảng là kết quả **theo quy tắc mã giả** và giả định điểm thực thi đã được công cụ can thiệp. Không phải mọi công cụ đều hỗ trợ chọn join point dựa vào giá trị tham số hoặc tên tham số nguồn.

### 10.1. Điều kiện động được kiểm tra khi nào?

Tùy cơ chế, một phần pointcut có thể được xác định lúc biên dịch/nạp mã, nhưng điều kiện phụ thuộc tham số/chủ thể vẫn phải kiểm tra **ở lần chạy liên quan**. Điều này có thể phát sinh chi phí khi điểm tĩnh xuất hiện rất thường xuyên.

Không nên giả định “pointcut đã khớp một lần” thì mọi invocation sau cũng khớp. Ngược lại, một chính sách có điều kiện động kiểm tra sai thời điểm có thể đọc trạng thái đã thay đổi và tạo ra kết quả khó giải thích.

### 10.2. Kiểu tham số tĩnh khác giá trị tham số động

Với chữ ký `save(List<String> items)`:

- Kiểu khai báo được lưu ở bytecode Java chịu ảnh hưởng của **type erasure**; generic type information có thể cần cơ chế metadata khác để đọc.
- Giá trị động `items` là **một đối tượng danh sách cụ thể** ở từng invocation, có thể rỗng hoặc có hàng trăm phần tử.
- Suy ra số phần tử từ **chữ ký** là sai; muốn biết `items.size()` phải quan sát đối tượng ở lần chạy phù hợp.

## 11. Nhiều luồng, đệ quy và trạng thái theo invocation

Thông tin tĩnh có thể dùng chung cho vô số lần thực thi, nhưng ngữ cảnh động của mỗi lần phải được quản lý đúng ranh giới.

### 11.1. Nhiều luồng dùng cùng phương thức

Hai thread cùng gọi `transfer`:

```text
Thread A:  static S  | args ["A", 100]  | start A ------- end A
Thread B:  static S  | args ["B", 900]  |    start B -------- end B
```

Cả hai có thể có chữ ký tĩnh S như nhau. Nhưng trạng thái `startTime`, `requestId`, `outcome` không được đặt trong một biến instance dùng chung rồi ghi đè tùy ý. Nếu không, duration và trace có thể nhầm qua lại giữa các lần gọi.

Một around advice đồng bộ thường có thể giữ biến local cho từng lời gọi; nếu dùng bộ nhớ dùng chung, cần thiết kế khóa hoặc cấu trúc dữ liệu phù hợp.

### 11.2. Đệ quy: cùng chữ ký, nhiều frame sống đồng thời

```text
factorial(3)
   -> factorial(2)
      -> factorial(1)
      <- result 1
   <- result 2
<- result 6
```

Cùng kiểu điểm thực thi `factorial(int)` có nhiều lần xuất hiện lồng nhau. Nếu advice ghi mốc thời gian bằng biến dùng chung, một lần gọi sâu có thể ghi đè lần gọi ngoài. Thông tin động phải được gắn với **đúng invocation/frame**, không chỉ với chữ ký.

### 11.3. Ngữ cảnh thread-local không luôn đi theo tác vụ

Một số hệ thống dùng `ThreadLocal` cho trace ID hoặc thông tin tạm thời. Nếu một thao tác chuyển sang worker thread hoặc dùng mô hình bất đồng bộ, context đó **không tự được mang qua mọi ranh giới**. Cần cơ chế truyền ngữ cảnh rõ ràng theo tác vụ nếu thiết kế yêu cầu.

## 12. Bất đồng bộ: kết quả trả về khác thời điểm công việc hoàn tất

Giả sử một phương thức `startExport()` trả về `CompletableFuture<Report>`:

```text
Gọi startExport()
   |
   | static = startExport():CompletableFuture
   | dynamic args = dữ liệu request
   v
Method return -> Future chưa hoàn tất
   |
   | ... công việc tiếp tục ...
   v
Future hoàn tất với Report hoặc lỗi
```

Nếu join point là **method execution của `startExport`**, advice sau khi phương thức trả về có thể chỉ thấy **đối tượng Future**, không thấy báo cáo cuối cùng. Ngoại lệ từ tác vụ bất đồng bộ có thể được biểu diễn trong Future thay vì thoát trực tiếp khỏi phương thức khởi tạo.

Muốn ghi nhận kết quả cuối cùng, cần quan sát thêm ranh giới completion của tác vụ (callback/completion stage hoặc điểm phù hợp khác). Không được đánh đồng:

- **Return value động của method execution:** đối tượng Future.
- **Kết quả động của tác vụ bất đồng bộ:** giá trị Report hoặc lỗi ở thời điểm hoàn thành về sau.

Điều này cũng ảnh hưởng tới đo thời gian, log và truyền ngữ cảnh qua luồng.

## 13. Dữ liệu tĩnh không bảo đảm lúc chạy sẽ can thiệp được

Một hệ thống AOP có thể thực hiện weaving khi biên dịch, khi nạp mã hoặc chặn lời gọi tại thời điểm chạy. Các phương thức này không có cùng tập join point.

- **Compile-time weaving:** mã thực thi có thể đã được kết hợp với advice khi tạo bản build.
- **Load-time weaving:** mã có thể được thay đổi khi thành phần được nạp.
- **Runtime interception:** lời gọi có thể cần đi qua đối tượng trung gian cụ thể.

Một tình huống dễ nhầm: phản xạ Java có thể đọc metadata của `method.execute()`, nhưng nếu ứng dụng gọi trực tiếp `target.execute()` mà không đi qua proxy ở ví dụ, handler **không chạy**, cho dù chữ ký vẫn tồn tại.

Vậy cần phân biệt hai câu hỏi:

1. **Điểm này mô tả được bằng metadata hay không?**
2. **Cơ chế AOP hiện tại có thực sự can thiệp được ở đường chạy này hay không?**

Chỉ câu thứ hai mới trả lời chuyện advice có chạy trong invocation đang xét. Các biến thể như self-invocation, gọi từ mã thư viện, code được nạp động hoặc điểm field access cần được xác minh riêng theo công cụ.

## 14. Dùng static information và dynamic context để logging, audit, metrics

### 14.1. Thiết kế một bản ghi có cấu trúc

Một bản ghi quan sát hữu ích có thể tách phần định danh điểm thực thi và phần dữ liệu theo lần gọi:

```json
{
  "joinPoint": {
    "kind": "method-execution",
    "signature": "PaymentService.pay(String,long)"
  },
  "invocation": {
    "correlationId": "req-204",
    "durationMs": 18,
    "status": "success"
  }
}
```

Đây là **mẫu dữ liệu minh họa**, không phải đầu ra của một API mặc định. `durationMs` và `correlationId` phải do ứng dụng/cơ chế đo tạo ra, không phải cứ có join point là tự phát sinh.

Thông thường nên dùng:

- Metadata tĩnh để **phân nhóm** theo phương thức, loại điểm, thành phần.
- Dynamic context để **liên kết lần gọi**, đếm thành công/thất bại và đo thời lượng.
- Một định danh correlation riêng để gắn các log thuộc cùng yêu cầu/tác vụ.
- Bộ nhãn metrics có cardinality kiểm soát được; không dùng các giá trị ngẫu nhiên như từng request ID làm nhãn metrics có số lượng vô hạn.

### 14.2. Bảo vệ dữ liệu nhạy cảm

Các tham số và kết quả động có thể chứa mật khẩu, token, dữ liệu cá nhân hoặc thông tin thanh toán. Một aspect tự động `toString()` toàn bộ `args` hoặc `result` có thể vô tình làm rò rỉ dữ liệu.

Do đó cần:

1. Chỉ ghi các trường phục vụ mục đích quan sát đã xác định.
2. Loại bỏ hoặc che dữ liệu nhạy cảm trước khi ghi.
3. Giới hạn độ dài và cấu trúc của bản ghi để tránh dữ liệu quá lớn, vòng tham chiếu hoặc `toString()` ném lỗi.
4. Xác định vòng đời và quyền truy cập dữ liệu log.
5. Không để telemetry thay đổi hợp đồng nghiệp vụ chỉ vì một bản ghi bị lỗi, trừ khi chính sách hệ thống có chủ ý yêu cầu như vậy.

### 14.3. Phân biệt metadata tĩnh với giá trị được suy diễn

Tên tệp/dòng, chữ ký và kiểu join point là những thông tin gắn với cấu trúc mã. Nhưng việc gắn nhãn `status=success` có thể phụ thuộc vào cách ứng dụng hiểu “thành công”: trả về bình thường chưa chắc đồng nghĩa giao dịch phân tán đã được hoàn tất ở hệ thống bên ngoài.

Advice cần chọn **ranh giới nghiệp vụ đúng** trước khi suy diễn trạng thái động thành một sự kiện nghiệp vụ.

## 15. Những lỗi thường gặp

**“Static information chỉ là tên phương thức.”** Chưa đủ. Cần xét loại join point, chữ ký, kiểu khai báo, vị trí mã và những metadata khác nếu phù hợp.

**“Đối tượng target là một phần của chữ ký tĩnh.”** Thường sai. Chữ ký chỉ mô tả phương thức/kiểu; đối tượng đích cụ thể là dữ liệu của invocation. Kiểu runtime cũng có thể khác declaring type.

**“Một shadow ứng với đúng một lần advice chạy.”** Sai. Một vị trí mã có thể chạy không lần nào, nhiều lần hoặc đệ quy; pointcut động còn có thể lọc một số lần.

**“Before advice đã biết kết quả và ngoại lệ.”** Không theo nghĩa thông thường. Kết quả và lỗi thoát ra chỉ biết khi phần được quan sát đã diễn ra, trừ các giá trị giả lập/tiên đoán của chính advice.

**“Có source location trong mã nguồn thì lúc chạy luôn đọc được vị trí ấy.”** Không bảo đảm. Tùy debug metadata, công cụ, cách tạo và biến đổi mã.

**“Method call và method execution có cùng bộ ngữ cảnh.”** Không nhất thiết. Phía gọi và phía thân thực thi có thể có vai trò đối tượng, vị trí tĩnh và thông tin khác nhau.

**“Mọi giá trị runtime đều là trường bắt buộc của JoinPoint API.”** Không. Thread context, duration, correlation ID hoặc kết quả hoàn tất bất đồng bộ có thể cần cơ chế riêng.

**“Metadata của phương thức chứng minh advice đã chạy.”** Sai. Phải kiểm tra điểm có nằm trong phạm vi weaving/interception và có khớp pointcut khi chạy hay không.

**“Ghi tất cả args/result sẽ giúp debug tốt nhất.”** Không an toàn và có thể rất tốn kém. Nên chọn lọc/che dữ liệu và kiểm soát độ lớn.

## 16. Cách tự kiểm chứng trên một chương trình

Khi cần phân tích thông tin tĩnh và động của một join point cụ thể, thực hiện theo thứ tự:

1. **Chọn đúng loại điểm:** method call, execution, constructor, field access… tùy công cụ.
2. **Xác định thông tin tĩnh:** chữ ký đầy đủ, declaring type, loại join point, vị trí mã nếu có.
3. **Liệt kê những giá trị động thật sự có sẵn:** đối tượng, args, kết quả/ngoại lệ và context theo từng thời điểm advice.
4. **Phân biệt phía gọi và phía đích:** đặc biệt khi có interface, override, proxy hoặc nhiều đường gọi.
5. **Đánh giá pointcut:** điều kiện tĩnh có chọn đúng shadow; điều kiện động có chọn đúng invocation.
6. **Kiểm tra đường thực thi:** điểm đã được weaving/chặn thật chưa; có đường gọi trực tiếp hoặc nội bộ bỏ qua interception không.
7. **Thử ít nhất hai invocation khác dữ liệu:** kiểm tra static metadata ổn định và dynamic values tương ứng.
8. **Thử đường ngoại lệ và bất đồng bộ nếu có:** xác minh advice nhận điều gì ở từng giai đoạn.
9. **Thử đồng thời/đệ quy nếu liên quan:** tránh trộn trạng thái của các lần gọi.

Bảng kiểm thử minh họa:

| Ca | Static metadata | Dynamic context | Kỳ vọng |
| --- | --- | --- | --- |
| `execute("X",1)` | Cùng signature | Args X,1; trả kết quả | Truy xuất được dữ liệu riêng lần X |
| `execute("Y",2)` | Cùng signature | Args Y,2; trả kết quả | Không lẫn với lần X |
| `execute("BAD",-1)` | Cùng signature | Args BAD,-1; ngoại lệ | Quan sát đúng đường lỗi |
| Lời gọi qua đường khác | Có thể cùng signature | Invocation khác | Advice chỉ chạy nếu đường đó được hỗ trợ |
| Hai lời gọi đồng thời | Metadata dùng chung | Context khác nhau | Không lẫn dữ liệu |
| Future chưa hoàn thành | Signature trả Future | Return là Future | Không báo tác vụ hoàn tất sớm |

## 17. Câu hỏi tự luyện

**1. Hai lần gọi cùng một phương thức có thể có static information khác nhau không?**

Nếu chúng là cùng loại join point ở cùng vị trí/định nghĩa tĩnh thì phần metadata tương ứng thường giống nhau; tuy nhiên hai **method-call shadows ở vị trí khác nhau** hoặc khác phần khai báo/điểm thực thi có thể có thông tin tĩnh khác. Phải xác định đang so sánh *đúng loại điểm* và *đúng vị trí* nào.

**2. Nếu một biến có kiểu `Formatter` nhưng trỏ tới `UpperFormatter`, đâu là dữ liệu tĩnh và động?**

Kiểu/chữ ký được dùng để mô tả điểm gọi có thể dựa trên `Formatter.format(String)` ở phía mã gọi. Đối tượng runtime `UpperFormatter` là ngữ cảnh động. Nếu quan sát điểm **execution** của phần cài đặt, metadata tĩnh của điểm đó có thể gắn với `UpperFormatter.format(String)`.

**3. Một pointcut chọn phương thức `transfer(String,long)` khi amount > 1000. Hai lần gọi 100 và 5000 có cùng static signature nhưng advice chạy mấy lần?**

Theo quy tắc giả định, advice chỉ chạy ở lần 5000 **nếu** join point được hỗ trợ và pointcut thực sự đánh giá giá trị động như mô tả.

**4. Vì sao after-returning trên phương thức trả Future không bảo đảm biết kết quả cuối cùng của tác vụ?**

Vì tại thời điểm phương thức trả về, Future có thể chưa hoàn tất. Đây là kết quả của *method execution*, khác với giá trị/lỗi của công việc được Future biểu diễn về sau.

**5. Vì sao không nên lưu một biến `currentArgument` chung trong aspect cho mọi lần gọi?**

Vì nhiều invocation có thể đồng thời hoặc lồng nhau; trạng thái chung có thể bị ghi đè, dẫn tới ngữ cảnh sai. Cần lưu theo invocation/tác vụ hoặc đồng bộ đúng mục đích.

**6. Nếu hệ thống đọc được `Method.getName()` nhưng advice không chạy, điều gì cần kiểm tra?**

Cần kiểm tra mô hình join point, pointcut, đường đi qua proxy/weaving, phạm vi lớp được áp dụng, điều kiện động và thứ tự/khả năng dừng của các advice bên ngoài. Đọc metadata chưa đủ chứng minh đường gọi bị chặn.

## 18. Kết luận

Đối với một join point, hãy luôn nhìn theo **hai tầng bổ sung**:

- **Thông tin tĩnh** định danh loại điểm thực thi, cấu trúc/chữ ký, kiểu khai báo và vị trí trong mã khi có. Nó giúp xác định *điểm nào có khả năng được chọn* và hỗ trợ mô tả pointcut.
- **Ngữ cảnh động** gắn với *mỗi lần điểm ấy thực sự diễn ra*: đối tượng, tham số, trạng thái, kết quả, ngoại lệ và những thông tin quan sát theo thời gian phù hợp. Nó giúp aspect ứng xử/ghi nhận đúng cho từng invocation.

Một vị trí tĩnh có thể phát sinh nhiều lần chạy, hai lần chạy cùng chữ ký có thể có đối tượng và kết quả khác nhau, và không phải mọi đường gọi đều được cơ chế AOP can thiệp. Hiểu rõ ranh giới **static shadow — dynamic occurrence — pointcut — advice — weaving** giúp xây dựng aspect chính xác, kiểm thử được và không làm mất ngữ nghĩa nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-008">Phân biệt điểm trong mã nguồn với điểm trong luồng thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt điểm trong mã nguồn với điểm trong luồng thực thi

## Mục tiêu bài học

Khi học Aspect-Oriented Programming (**AOP**), rất dễ nhìn vào một dòng mã như `service.process()` rồi nghĩ rằng đó chính là “một join point”. Cách nói này thiếu một lớp phân biệt quan trọng: **vị trí viết trong chương trình** là một mô tả tĩnh, còn **lần thực sự xảy ra của thao tác khi chương trình chạy** là sự kiện động. Một vị trí trong mã có thể được thực thi không lần nào, một lần hoặc hàng triệu lần; một lần chạy có thể đi qua nhiều điểm can thiệp khác nhau.

Sau bài này, bạn sẽ:

- Phân biệt **source location / call site / vị trí mã tĩnh** với **runtime event / lần xảy ra của join point**.
- Hiểu khái niệm **join-point shadow** trong những mô hình AOP có định nghĩa thuật ngữ đó, và không mặc định mọi công cụ dùng cùng thuật ngữ.
- Phân biệt **method call** với **method execution**, kể cả khi cú pháp nguồn khiến chúng trông giống nhau.
- Dự đoán số lần advice chạy khi có vòng lặp, nhánh điều kiện, đa hình, đệ quy, bất đồng bộ và lời gọi đi vòng qua proxy.
- Thiết kế pointcut và kiểm thử theo **đường chạy thực tế**, tránh đếm dòng mã hoặc số method declaration để suy ra số lần can thiệp.
- Giải thích được ranh giới giữa những gì AOP chọn ở **mức cấu trúc chương trình** và những gì cần kiểm tra tiếp ở **runtime**.

Bài học nói về AOP **nói chung**. Các điểm can thiệp mà một ngôn ngữ hay công cụ cụ thể hỗ trợ có thể khác nhau; không suy rộng khả năng của một kiểu proxy sang mọi kỹ thuật AOP.

## 1. Hai câu hỏi khác nhau: “ở đâu?” và “đã xảy ra lúc nào?”

Xét mã:

```java
for (int i = 0; i < 3; i++) {
    service.execute(i);   // một vị trí lời gọi trong mã nguồn
}
```

Có **một biểu thức lời gọi** `service.execute(i)` trong phần thân vòng lặp, nhưng nếu vòng lặp chạy đủ ba lần và không bị ngắt trước lời gọi, sẽ có **ba lần gọi xảy ra khi thực thi**.

Nếu đổi điều kiện thành `i < 0`, vẫn còn nguyên dòng mã ấy nhưng không có lần gọi nào xảy ra. Nếu gọi `execute` từ nhiều vị trí khác nữa, số vị trí mã tăng; số lần thực thi vẫn phụ thuộc đường đi, dữ liệu và lịch chạy.

| Góc nhìn | Điểm trong mã nguồn | Điểm trong luồng thực thi |
| --- | --- | --- |
| Câu hỏi | “Thao tác được viết/biểu diễn ở chỗ nào?” | “Một thao tác cụ thể đã xảy ra khi nào?” |
| Tính chất | Tương đối tĩnh theo phiên bản chương trình hoặc artifact. | Động, phụ thuộc input, nhánh, vòng lặp, lịch chạy, exception. |
| Ví dụ | Biểu thức `service.execute(i)` trong một thân vòng lặp. | Lượt `execute(0)`, `execute(1)`, `execute(2)` thực sự xảy ra. |
| Số lượng | Một call site ở đoạn mã đã cho. | Có thể bằng 0, 1, 3 hoặc rất nhiều lượt. |
| Dữ liệu sẵn có | Chữ ký, kiểu tĩnh, modifier, metadata, cấu trúc mã. | Đối tượng thực, giá trị đối số, thread/context, thời điểm, kết quả/lỗi. |
| Công việc kiểm chứng | Phân tích cấu trúc hoặc bytecode, xem pointcut dự kiến chọn vị trí nào. | Chạy chương trình và quan sát đúng các sự kiện có/không được advice xử lý. |

**Ý chính:** Vị trí mã là **ứng viên có thể phát sinh sự kiện**, không phải bằng chứng rằng sự kiện đã xảy ra. Đây là nền tảng để hiểu join point trong AOP.

## 2. Join point là gì trong sự phân biệt này?

Trong nhiều mô hình AOP, **join point** là một điểm hoặc sự kiện có ý nghĩa xác định trong **luồng thực thi** mà công cụ có thể chèn hành vi hoặc quan sát, chẳng hạn “một lần thực thi phương thức `save`” hay “một lần gọi phương thức `save`”. **Pointcut** là quy tắc chọn các join point quan tâm; **advice** là hành vi được áp dụng ở những điểm được chọn; **weaving** là cách kết hợp advice với chương trình.

Điều quan trọng là từ “điểm” không bắt buộc có nghĩa “một dòng source”. Một lần thực thi method có thể bắt đầu tại bytecode đã biên dịch, chạy qua hàng loạt statement, và kết thúc bằng return hoặc throw. Một join point được công cụ mô hình hóa như sự kiện có thể có **ngữ cảnh**, **thời gian tồn tại** hoặc khả năng bao quanh một vùng thực thi. Cách biểu diễn cụ thể là hợp đồng của công cụ.

### 2.1. Join-point shadow là khái niệm hữu ích nhưng không phổ quát

Trong một số hệ thống AOP có phân biệt tĩnh/động, **join-point shadow** là vị trí trong mã (thường được xem ở source hoặc bytecode) **có thể tạo ra join point khi được thực thi**.

Ví dụ với vòng lặp `service.execute(i)`:

- **Shadow tiềm năng:** vị trí thao tác gọi `execute` được công cụ nhận diện trong mã.
- **Join-point occurrences:** những lần thao tác ấy thực sự diễn ra ở runtime.
- **Pointcut match:** quy tắc có thể chọn shadow theo thông tin tĩnh, có thể cần kiểm tra bổ sung theo ngữ cảnh runtime.
- **Advice executions:** những lần advice thực sự chạy sau khi cơ chế can thiệp được kích hoạt và các điều kiện chọn được thỏa.

Không phải mọi công cụ AOP công bố khái niệm *shadow*, và không phải loại join point nào cũng có một địa chỉ dòng source tương ứng duy nhất. Với proxy thuần runtime, ta có thể quan sát lời gọi qua proxy mà không cần một bộ chọn vị trí nguồn. Thuật ngữ shadow nên dùng **khi mô hình đang học hỗ trợ nó**, không dùng như tiêu chuẩn bắt buộc của mọi AOP.

### 2.2. Không đồng nhất số shadow, join point và lần chạy advice

Số liệu này có thể khác nhau:

```text
01 vị trí lời gọi trong mã
       |
       | vòng lặp chạy 10 lần
       v
10 lần gọi ở runtime
       |
       | chỉ 6 lần thỏa điều kiện động của pointcut
       v
06 lần advice được kích hoạt
```

Sơ đồ có tính minh họa: một công cụ có thể chọn ở mức method execution thay vì call site, hoặc không có điều kiện động. Nó nhấn mạnh **ba phép đếm khác nhau**. Không dùng phép đếm dòng mã để dự báo lượng log, overhead hay số lần advice.

## 3. Method call và method execution là hai loại join point khác nhau

Xét một đoạn chương trình:

```java
class ReportService {
    void generate() {
        save("Q4");       // vị trí gọi
    }

    void save(String name) {
        System.out.println(name); // phần thân được thực thi
    }
}
```

Nếu một mô hình AOP hỗ trợ phân biệt hai loại sau:

- **Method-call join point:** sự kiện ở phía thao tác **gọi** `save("Q4")` từ `generate()`. Vị trí liên quan nằm ở phía caller.
- **Method-execution join point:** sự kiện khi **phần thực thi của `save`** bắt đầu/chạy theo định nghĩa của mô hình. Vị trí liên quan nằm ở phía callee.

Lược đồ:

```text
generate()
    |
    |  call-site: save("Q4")  <-- method-call join point (nếu được hỗ trợ)
    v
save(name)
    |                     <-- method-execution join point (nếu được hỗ trợ)
    v
in "Q4"
```

Một lần gọi thông thường có thể liên quan cả sự kiện call và execution trong những mô hình hỗ trợ cả hai. Tuy nhiên, chúng **không phải cùng join point**. Nếu `save` được gọi từ ba call site khác nhau, ba vị trí call tĩnh có thể dẫn đến các lượt thực thi của cùng thân method. Một call site trong vòng lặp có thể làm phát sinh nhiều lượt call và execution.

Đặc biệt, các kỹ thuật chỉ chặn lời gọi qua **proxy** không nhất thiết hiện thực được một join-point model phân biệt call/execution theo cấu trúc source. Ở đó, cách mô tả chính xác hơn là **handler chạy khi có lời gọi đi qua proxy**, chứ không suy luận rằng công cụ hỗ trợ mọi call site hoặc mọi method execution trong chương trình.

### 3.1. Đa hình khiến “dòng gọi” và “thân code chạy” càng khác nhau

Xét:

```java
interface Operation {
    void run();
}

class FastOperation implements Operation {
    public void run() {
        System.out.println("fast");
    }
}

class SafeOperation implements Operation {
    public void run() {
        System.out.println("safe");
    }
}
```

Biểu thức `operation.run()` tại một call site có kiểu tĩnh `Operation`, nhưng đối tượng runtime có thể là `FastOperation` hoặc `SafeOperation`. Vì vậy:

- Một vị trí gọi tĩnh **không xác định duy nhất** thân triển khai nào sẽ được chạy.
- Một pointcut dựa vào **chữ ký tĩnh** có thể chọn khác với pointcut phụ thuộc **loại target runtime**.
- Advice ở phía method-execution có thể gắn với một triển khai cụ thể, trong khi call site có thể dispatch đến nhiều triển khai khác nhau.
- Kết quả quan sát chịu ảnh hưởng các quy tắc overload, override, dynamic dispatch, bridge method và giới hạn của cơ chế AOP cụ thể.

Cần tránh lập luận “dòng này gọi interface X, vậy advice trên implementation Y chắc chắn sẽ chạy”; phải chứng minh receiver thực và đường can thiệp tại runtime.

### 3.2. Static dispatch và dynamic dispatch là hai trục khác

Một lệnh gọi `static` không có cơ chế virtual dispatch tương tự một instance method, nhưng nó vẫn có thể có **vị trí lời gọi** và **lần xảy ra lúc runtime**. Công cụ có chặn được hay không phụ thuộc vào join-point model và cách triển khai. Tương tự, `private`, `final` hay constructor không thể bị chặn bằng mọi kiểu subclass proxy, nhưng có thể thuộc tập điểm mà một số cơ chế weaving hỗ trợ.

Do đó, luôn tách hai câu hỏi: “Điểm nào xuất hiện theo ngữ nghĩa thực thi?” và “Cơ chế AOP hiện tại **quan sát được** điểm nào?”.

## 4. Bốn tình huống làm số điểm runtime thay đổi

### 4.1. Điều kiện: một vị trí có thể không được thực thi

```java
if (enabled) {
    service.save();
}
```

Nếu `enabled == false`, biểu thức vẫn có trong source nhưng **không phát sinh lời gọi**. Advice không thể “ghi nhận sự kiện đã xảy ra” cho lời gọi vốn không diễn ra. Có thể một advice khác chạy ở điểm bao quanh `if` hoặc ở caller nếu công cụ hỗ trợ loại điểm ấy, nhưng đó là join point khác.

### 4.2. Vòng lặp: một vị trí phát sinh nhiều lượt

```java
for (int i = 0; i < 100; i++) {
    service.save();
}
```

Có một call site ở đoạn mã nhưng có thể có 100 lần gọi. Nếu advice đo thời gian chạy mỗi lần, lượng dữ liệu/chi phí sẽ tính theo **100 sự kiện runtime**, không theo một dòng source.

### 4.3. Đệ quy: một thân phương thức có nhiều lần được kích hoạt

```java
static int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}
```

Với `factorial(4)`, phần thân được vào cho `n = 4, 3, 2, 1`: **bốn lần thực thi** của cùng method. Có một biểu thức gọi đệ quy tại phần thân, được thực hiện **ba lần** ở lượt `n = 4, 3, 2`; lời gọi ban đầu tới `factorial(4)` xuất phát từ một vị trí khác. Vì thế count của *call* tại riêng vị trí đệ quy khác count *execution* của method.

Cũng không thể mặc định một proxy thuần runtime chặn lời gọi đệ quy nội bộ. Với wrapper/proxy thường gặp, lời gọi trên `this` hoặc gọi trực tiếp method trong target có thể đi **ngoài** wrapper. Weaving và các cơ chế khác có khả năng can thiệp khác. Phải kiểm tra call path thật.

### 4.4. Thread và bất đồng bộ: sự kiện còn phụ thuộc lịch chạy

Một call site gửi task có thể chạy một lần, nhưng task được thực hiện sau đó, ở thread khác, thậm chí không hoàn tất do cancellation. Một method trả `Future` hoặc một handle không đồng nghĩa kết quả công việc đã hoàn thành.

```text
Thread A                         Worker thread B
    |                                  |
    | submit(task)                      |
    |---- call xảy ra ----> queue ------+--> task.run() bắt đầu
    |                                  |
    +-- submit trả handle               +--> task.run() kết thúc
```

Nếu advice chọn điểm `submit`, nó quan sát **giai đoạn gửi**. Nếu chọn điểm `task.run` hoặc completion, nó quan sát **giai đoạn thực thi/hoàn tất** (với điều kiện công cụ nhìn thấy các điểm ấy). Không đồng nhất ba thời điểm. Thứ tự các sự kiện giữa thread có thể khác nhau, nên log cần thông tin tương quan và đồng hồ phù hợp; không suy ra thứ tự thời gian chỉ từ thứ tự dòng source ở các thread khác nhau.

## 5. Pointcut có thể xét tĩnh, xét động hoặc kết hợp cả hai

Một pointcut chọn **loại sự kiện nào** theo một số thông tin:

| Thông tin | Thường xác định được từ cấu trúc chương trình | Có thể cần dữ liệu runtime |
| --- | --- | --- |
| Method name, chữ ký, modifier | Có. | Thường không, trừ cơ chế đặc thù. |
| Package, class, inheritance khai báo | Có thể phân tích tĩnh nếu toàn bộ kiểu có sẵn. | Một số quan hệ kiểu/receiver cần xét thêm. |
| Annotation có trong artifact | Có thể xác định lúc build/weaving hoặc đọc metadata. | Nếu công cụ chọn tại runtime, phải thực hiện tra cứu tương ứng. |
| Kiểu thực của receiver | Không nhất thiết biết từ kiểu khai báo tại call site. | Có thể cần kiểm tra object thực. |
| Đối số có giá trị cụ thể | Không biết đầy đủ ở thời điểm phân tích source. | Phải quan sát từng lần gọi. |
| Thread ID, request context, luồng điều khiển | Không gắn cố định với vị trí source. | Cần ngữ cảnh của sự kiện runtime. |
| Kết quả/exception | Chưa xuất hiện trước khi thực thi. | Chỉ biết sau khi điểm liên quan trả về hoặc ném lỗi. |

Không phải mọi công cụ hỗ trợ tất cả loại matcher. Một engine có thể **lọc tĩnh** một số vị trí ứng viên rồi chèn kiểm tra điều kiện động cho từng lượt thực thi. Một proxy runtime đơn giản có thể chọn theo `Method` và args ngay trong handler. Hệ thống khác có thể kết hợp điều kiện ngay lúc biên dịch/weaving.

Giả mã mô tả một pointcut, **không phải cú pháp của thư viện cụ thể**:

```text
select:
    methodExecution belongsTo("operations")
    AND methodHasMarker("Observed")
    AND runtimeArgument("enabled") == true
```

Giả sử trong mã có **một vị trí** method execution phù hợp tên/marker, nhưng nó chạy 10 lần và chỉ 4 lần có `enabled = true`. Nếu engine thực sự hỗ trợ kiểm tra đối số như trên và advice được cài đúng, advice có thể chạy **4 lần**. Không thể lấy số điểm tĩnh nhân một hằng số tùy ý để suy ra số lần can thiệp.

### 5.1. Khi đọc biểu thức pointcut cần trả lời bốn câu hỏi

1. **Nó chọn loại join point gì?** Call, execution, field access, constructor hay event do wrapper cung cấp?
2. **Phần nào cố định trong chương trình?** Chữ ký, package, metadata hay vị trí bytecode?
3. **Phần nào được đánh giá theo từng lần xảy ra?** Đối tượng, đối số, context, return/throw?
4. **Lời gọi có thực sự đi qua cơ chế AOP không?** Proxy, weaving, instrumenter, compile transform đã được áp vào artifact đang chạy chưa?

Bỏ câu số 4 là nguyên nhân phổ biến của nhận định sai rằng “pointcut trông có vẻ khớp thì advice chắc chắn phải chạy”.

## 6. Thực hành Java 17: một call site, nhiều lần runtime, nhiều implementation

Ví dụ dưới dùng **Java thuần**, chưa cài engine AOP, để bạn thấy rõ khác biệt giữa một biểu thức gọi trong source và từng lần thực thi. Các dòng `CALL` và `EXECUTE` là log do ta chủ động đặt, **không phải advice tự động**.

Tạo tệp `SourceVsRuntimeDemo.java`:

```java
public class SourceVsRuntimeDemo {
    interface Handler {
        String handle(int value);
    }

    static final class DoubleHandler implements Handler {
        @Override
        public String handle(int value) {
            System.out.println("EXECUTE DoubleHandler.handle " + value);
            return "double=" + (value * 2);
        }
    }

    static final class SquareHandler implements Handler {
        @Override
        public String handle(int value) {
            System.out.println("EXECUTE SquareHandler.handle " + value);
            return "square=" + (value * value);
        }
    }

    static void dispatch(Handler handler, int count) {
        for (int i = 1; i <= count; i++) {
            System.out.println("CALL dispatch site value=" + i);
            String result = handler.handle(i);
            System.out.println("RESULT " + result);
        }
    }

    public static void main(String[] args) {
        dispatch(new DoubleHandler(), 2);
        dispatch(new SquareHandler(), 1);
        dispatch(new DoubleHandler(), 0);
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac SourceVsRuntimeDemo.java
java SourceVsRuntimeDemo
```

Kết quả chuẩn:

```text
CALL dispatch site value=1
EXECUTE DoubleHandler.handle 1
RESULT double=2
CALL dispatch site value=2
EXECUTE DoubleHandler.handle 2
RESULT double=4
CALL dispatch site value=1
EXECUTE SquareHandler.handle 1
RESULT square=1
```

Phân tích theo đúng từng phép đếm:

- Trong `dispatch` có **một biểu thức** `handler.handle(i)` đóng vai trò call site nguồn.
- `dispatch(DoubleHandler, 2)` cho hai lượt, `dispatch(SquareHandler, 1)` cho một lượt, `dispatch(DoubleHandler, 0)` không cho lượt nào. Tổng cộng có **ba lần gọi runtime** từ chính call site ấy.
- Runtime dispatch chọn body `DoubleHandler.handle` **hai lần**, `SquareHandler.handle` **một lần**; cùng một call site, nhưng khác target implementation.
- Không thể suy luận “một dòng `handle` = một lần advice” hoặc “interface `Handler` = một body duy nhất”.
- Nếu mô hình AOP có join point dạng **method-call**, điểm phía caller có thể là ba occurrence từ một vị trí `handler.handle(i)`. Nếu có **method-execution**, ta có hai occurrence thực thi body `DoubleHandler.handle` và một occurrence `SquareHandler.handle`. Đây là diễn giải khái niệm theo mô hình **nếu có hỗ trợ hai loại join point**; ví dụ Java trên không tự tạo AOP.

**Tự thử:** Đổi `dispatch(new SquareHandler(), 1)` thành `dispatch(new SquareHandler(), 3)`. Vị trí call-site không đổi nhưng sẽ có thêm hai lần gọi; hãy dự đoán toàn bộ stdout trước khi chạy.

## 7. Ví dụ cơ chế AOP một phần: proxy quan sát lần gọi chứ không “sở hữu dòng source”

JDK cung cấp **dynamic proxy theo interface**. Handler có thể chạy mỗi khi caller gọi phương thức thông qua đối tượng proxy. Kỹ thuật này minh họa một điểm quan trọng: **một cơ chế can thiệp có thể nhận runtime event mà không có biểu thức chọn địa chỉ source call site**.

Để đơn giản, demo dùng một interface `Gateway`, một target thật và một proxy. Handler chỉ ghi sự kiện cho phương thức `send`. Đây là **minh họa một cơ chế theo tư duy AOP**, không phải một engine AOP đầy đủ; nó không định nghĩa tập pointcut hoặc join-point shadow của mọi công nghệ.

Tạo tệp `RuntimeInterceptionDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class RuntimeInterceptionDemo {
    interface Gateway {
        String send(String message);
    }

    static final class RealGateway implements Gateway {
        @Override
        public String send(String message) {
            if (message.isBlank()) {
                throw new IllegalArgumentException("empty message");
            }
            System.out.println("TARGET send " + message);
            return "ACK " + message;
        }
    }

    static Gateway observing(Gateway target) {
        return (Gateway) Proxy.newProxyInstance(
                Gateway.class.getClassLoader(),
                new Class<?>[] { Gateway.class },
                (proxy, method, methodArgs) -> {
                    if (!method.getName().equals("send")) {
                        return method.invoke(target, methodArgs);
                    }
                    System.out.println("ADVICE before send");
                    try {
                        Object result = method.invoke(target, methodArgs);
                        System.out.println("ADVICE returning send");
                        return result;
                    } catch (InvocationTargetException ex) {
                        System.out.println("ADVICE throwing send");
                        throw ex.getCause();
                    }
                }
        );
    }

    public static void main(String[] args) {
        Gateway target = new RealGateway();
        Gateway proxy = observing(target);

        for (int i = 1; i <= 2; i++) {
            System.out.println("CALL through proxy " + i);
            System.out.println("RESULT " + proxy.send("M" + i));
        }

        System.out.println("CALL direct");
        System.out.println("RESULT " + target.send("M3"));

        try {
            proxy.send("");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac RuntimeInterceptionDemo.java
java RuntimeInterceptionDemo
```

Stdout kỳ vọng:

```text
CALL through proxy 1
ADVICE before send
TARGET send M1
ADVICE returning send
RESULT ACK M1
CALL through proxy 2
ADVICE before send
TARGET send M2
ADVICE returning send
RESULT ACK M2
CALL direct
TARGET send M3
RESULT ACK M3
ADVICE before send
ADVICE throwing send
CALLER caught empty message
```

### 7.1. Đếm đúng các sự kiện

- Biểu thức `proxy.send("M" + i)` có **một vị trí nguồn**, được thực thi hai lần; handler quan sát **hai** lượt.
- Biểu thức `target.send("M3")` là vị trí khác, nhưng **không qua proxy**, nên không có dòng advice.
- Biểu thức `proxy.send("")` là một vị trí nữa và tạo thêm **một** lần handler được kích hoạt ở đường exception.
- Tổng số lần `RealGateway.send` chạy: **bốn**; tổng số lần handler ghi `ADVICE before send`: **ba**. Sự khác nhau đến từ **đường gọi**, không phải do source code của `send` thay đổi.
- `Method.invoke` bọc exception do target ném trong `InvocationTargetException`; handler truyền tiếp `ex.getCause()` để caller nhận `IllegalArgumentException` gốc.

### 7.2. Điều gì xảy ra nếu có self-invocation?

Nếu `RealGateway.send` tự gọi một phương thức khác trên `this`, lời gọi đó **không tự đi qua proxy** ở ví dụ này. Một số cơ chế weaving có thể can thiệp điểm nằm bên trong target; không được suy từ hạn chế của ví dụ proxy ra mọi công nghệ AOP.

### 7.3. Tại sao handler không đủ để tìm “dòng source đã gọi”?

Handler nhận `Method`, args và đối tượng target mà mã thiết kế cho nó nắm giữ. Nó **không tự được cung cấp một ánh xạ chính xác** từ sự kiện tới dòng source của caller. Có thể dùng stack trace hoặc công cụ instrumentation để hỗ trợ truy xuất, nhưng line numbers có thể không tồn tại hoặc không còn 1:1 sau compile, tạo mã, inlining hoặc tối ưu. Một bộ AOP có hỗ trợ method-call join point tại source/bytecode có thể nắm vị trí khác, tùy thiết kế.

## 8. Từ source đến bytecode: đừng mặc định ánh xạ một-một

Trong thực tế, mã được biên dịch hoặc biến đổi:

```text
Mã nguồn
  -> biểu diễn compiler / bytecode / mã được sinh
  -> linking, class loading, instrumentation, JIT (nếu có)
  -> sự kiện thực thi được quan sát
```

Tùy ngôn ngữ và công cụ:

- Một biểu thức nguồn có thể tạo **nhiều instruction hoặc helper method**. Lambda, generic bridge, coroutine state machine, synthetic accessors và code generation có thể làm cấu trúc runtime khác source.
- Một method source có thể không còn cấu trúc dễ nhận diện sau inlining/tối ưu. Công cụ weaving hoặc profiler có cách bảo toàn/nghiệm lại ngữ nghĩa riêng; không suy ra số sự kiện từ bytecode một cách tùy tiện.
- Một vị trí bytecode có thể được thực thi nhiều lần. Một lần dynamic event có thể không có thông tin dòng source nếu artifact không chứa debug symbols.
- Cơ chế source weaving và bytecode weaving có thể mô tả “vị trí tĩnh” ở tầng khác nhau; proxy thuần runtime lại quan sát qua **đối tượng đại diện**.

Vì thế, từ “điểm trong mã nguồn” trong lời giải thích thường là lối nói dễ học cho **vị trí tĩnh có thể xác định**, nhưng khi làm kỹ thuật thực tế cần chỉ rõ tầng đang xét: source syntax, AST, bytecode, instruction, method descriptor hay runtime proxy entry.

### Phân biệt nơi đặt hook với nơi xảy ra kết quả

Một advice around có thể bao quanh cả method execution; pointcut chọn từ chữ ký tĩnh nhưng advice đo một **khoảng thời gian runtime**. Nếu method ném exception ở dòng sâu trong thân, cùng join point execution đó kết thúc theo đường throwing. Đừng nghĩ mỗi nhánh return/throw đòi hỏi một “dòng source mới” để advice có thể quan sát, trừ khi công cụ mô hình hóa chúng thành những join point khác.

## 9. Từ một vị trí tĩnh đến nhiều sự kiện khi áp dụng pointcut

Giả sử có pointcut mô tả:

```text
execution method whose name is "send"
AND runtime argument message is nonempty
```

Đây là **giả mã**, không phải cú pháp hợp lệ chung. Trong một công cụ hỗ trợ bộ điều kiện này:

1. Quy tắc theo tên/chữ ký có thể thu hẹp tập điểm **theo cấu trúc chương trình**.
2. Kiểm tra đối số không rỗng cần dùng giá trị ở **mỗi lần gọi runtime**.
3. Advice được áp dụng cho các lần thỏa **toàn bộ điều kiện**, qua cơ chế weaving/proxy đã được kích hoạt.
4. Hai lần thực thi cùng method có thể cho kết quả matching khác nhau vì args khác.

Bảng đối chiếu:

| Tình huống | Cùng một vị trí tĩnh? | Có sự kiện runtime? | Advice có nhất thiết chạy? |
| --- | --- | --- | --- |
| Method không bao giờ được gọi | Có vị trí khả dĩ. | Không. | Không có sự kiện tương ứng để áp dụng advice. |
| Call site trong loop chạy 5 lần | Một call site. | Có thể 5 lần call. | Tùy loại pointcut, điều kiện và đường can thiệp. |
| Method được gọi hai lần với args khác nhau | Có thể cùng call site/method. | Hai sự kiện. | Có thể chọn một, cả hai hoặc không sự kiện nào. |
| Target được gọi trực tiếp, không qua proxy | Source method vẫn tồn tại. | Có execution thật. | Handler trên proxy không chạy cho đường gọi đó. |
| Method được weave vào bytecode | Vị trí trong artifact đã được biến đổi. | Mỗi lần thực thi có thể kích hoạt hook. | Còn phụ thuộc pointcut, điều kiện và triển khai thực tế. |
| Gửi task async rồi task chưa chạy | Call site submit có thể đã xảy ra. | Sự kiện submit có; task execution có thể chưa. | Phụ thuộc chọn điểm submit hay task execution/completion. |

Nếu mục tiêu là **đếm số lần xử lý**, phải định nghĩa rõ bạn đếm **invocation attempted**, **execution entered**, **completed successfully**, **thrown**, hay **logical operation**. Chúng khác nhau khi retry, cancellation, timeout, lỗi trước target, hoặc kết quả trả về là handle bất đồng bộ.

## 10. Hậu quả thiết kế: chọn pointcut ở sai tầng tạo ra lỗi khó nhận ra

### 10.1. Muốn áp dụng vào một nhóm method nhưng chọn theo call site

Một chính sách đo thời gian của `save` có thể cần bao phủ mọi lần thực thi của method. Nếu pointcut chỉ chọn vài vị trí **gọi** trong module A mà lời gọi từ module B bị bỏ qua, kết quả đếm có thể thiếu. Ngược lại, một chính sách gắn với caller cụ thể có thể cần method-call join point để biết ngữ cảnh phía caller.

**Đặt câu hỏi nghiệp vụ kỹ thuật:** Muốn chặn “hành vi method được thực thi” hay “caller ở một nơi cụ thể thực hiện lời gọi”? Sau đó mới chọn loại join point.

### 10.2. Log và overhead phụ thuộc số sự kiện, không phải số dòng

Pointcut khớp một call site trong hot loop có thể tạo triệu advice executions mỗi giây. Một dòng log ở mỗi lượt có thể gây áp lực I/O lớn. Phải benchmark theo **tần suất runtime**, sampling và workload thật. Đếm có bao nhiêu dòng source được weave chỉ giúp hiểu **phạm vi vị trí**, không đủ để ước lượng tổng overhead.

### 10.3. Điểm tĩnh trông khớp nhưng advice không chạy

Những nguyên nhân cần xét:

- Nhánh code chưa được đi qua; event chưa xảy ra.
- Dữ liệu runtime không thỏa điều kiện pointcut.
- Lời gọi đi trực tiếp vào target thay vì qua proxy.
- Pointcut đặt ở loại join point khác (call thay vì execution, hoặc ngược lại).
- Đối tượng runtime/overload/implementation thực không như giả định.
- Aspect chưa được đăng ký, class chưa được weave hoặc artifact chạy khác artifact được kiểm tra.
- Giới hạn ngôn ngữ/công cụ: `final`, `private`, constructor, module, classloader hoặc loại join point chưa hỗ trợ.

Kiểm tra từng nguyên nhân bằng quan sát có kiểm soát; không kết luận chỉ từ việc biểu thức pointcut “nhìn đúng”.

### 10.4. Advice chạy nhiều lần hơn dự tính

Những nguyên nhân:

- Một call site được lặp hoặc tái gọi bởi recursion.
- Thao tác bị retry do hệ thống khác hoặc do chính around advice gọi tiếp nhiều lần.
- Có nhiều caller dẫn đến cùng method execution.
- Nhiều advice/instance của aspect khớp cùng sự kiện.
- Nested wrapper hoặc instrumentation đôi có thể làm một logical operation đi qua nhiều lớp.
- Tác vụ được chạy lại sau lỗi, timeout hoặc cơ chế delivery ít nhất một lần.

Khi điều tra, cần gắn correlation ID cho **logical operation** và phân biệt nó với invocation ID cho **mỗi lần chạy**. Không nên gộp chúng thành một con số nếu nghiệp vụ cần phân biệt.

## 11. Cách kiểm thử ranh giới source/runtime

Một bộ test tốt cần vừa chứng minh **positive matching** vừa chứng minh **negative matching**, đồng thời kiểm tra số occurrence thực tế.

| Ca kiểm thử | Thiết lập | Kỳ vọng cần xác nhận |
| --- | --- | --- |
| Zero occurrence | Nhánh bỏ qua call site hoặc loop 0 lượt. | Không có advice do sự kiện đó gây ra. |
| One occurrence | Gọi một lần qua đường được can thiệp. | Advice chạy đúng số lượt và đúng thứ tự đã thiết kế. |
| Many occurrences | Gọi cùng call site N lượt. | Số lần advice tỷ lệ với những occurrence thực sự thỏa rule, không theo số dòng. |
| Polymorphic target | Cùng call site nhưng hai implementation. | Kết quả chọn theo chữ ký tĩnh/kiểu runtime đúng hợp đồng. |
| Direct target vs proxy | Gọi qua proxy và trực tiếp. | Xác nhận giới hạn đường can thiệp của proxy. |
| Method-call vs execution | Dùng công cụ hỗ trợ cả hai loại. | Quan sát đúng nơi caller/callee, không lẫn các loại. |
| Recursion/nested call | Gọi method đệ quy hoặc method khác trong target. | Đếm và thứ tự rõ; đặc biệt kiểm tra self-invocation. |
| Dynamic filter | Đổi giá trị args/context trên cùng call site. | Chỉ những occurrence thỏa predicate được chọn. |
| Exception path | Target ném lỗi. | Advice after-throwing/finally hoạt động theo hợp đồng; caller nhận lỗi đúng. |
| Async | Gửi task rồi hoàn tất ở thời điểm sau. | Tách submit event, execute event, completion event. |
| Weaving/config mismatch | Chạy đúng artifact sản xuất. | Xác nhận hook thật sự được cài, không chỉ khớp ở bản demo. |

Nên lấy bằng chứng từ output, metric, trace hoặc bộ đếm trong **môi trường có cơ chế AOP thật**, không chỉ từ unit test gọi thẳng vào đối tượng target. Với những công cụ hỗ trợ công bố join-point matching hoặc inspect bytecode, kết hợp thông tin đó với test runtime để xác nhận cả “nơi có khả năng tác động” và “nơi đã xảy ra sự kiện”.

### Một phép kiểm chứng bằng số

Giả sử có hai method:

- `A.save()` được gọi 10 lượt và thỏa pointcut ở cả 10.
- `B.save()` được gọi 7 lượt nhưng 3 lượt có args không thỏa điều kiện động.
- Hai method được gọi bằng đường AOP đang can thiệp đầy đủ, mỗi occurrence được chọn chỉ chạy một advice duy nhất, không có retry.

Khi đó số lần advice kỳ vọng là **10 + (7 − 3) = 14**, dù số method declaration chỉ bằng 2. Kết quả 14 dựa trên giả thiết đường chạy và điều kiện rõ ràng; thay dữ liệu, scheduling, retry hoặc cấu hình thì phép đếm phải tính lại.

## 12. Các lỗi tư duy phổ biến

**“Có một lời gọi trong source thì sẽ có một join point.”** Chưa đủ. Cần nêu loại join point và hiểu một vị trí có thể tạo 0, 1 hoặc nhiều occurrence.

**“Join point chính là số dòng code.”** Không. Line number chỉ là thông tin vị trí phục vụ người đọc/debug; nhiều statement có thể cùng dòng, một statement có thể trải nhiều dòng, compiler có thể sinh code và loại bỏ thông tin debug.

**“Method-call và method-execution tương đương.”** Không. Một bên gắn với phía gọi, một bên với lúc thực thi body; tập điểm được chọn và ngữ cảnh khác nhau.

**“Pointcut khớp chữ ký là advice nhất định chạy.”** Sai nếu runtime không đi qua điểm đó, điều kiện động sai hoặc cơ chế can thiệp không được kích hoạt/bao phủ đường gọi.

**“Mọi lượt thực thi một method đều đi qua interface proxy.”** Sai: còn đường gọi trực tiếp vào target và lời gọi nội bộ. Phạm vi phụ thuộc mô hình AOP cụ thể.

**“Đếm aspect advice bằng số method có annotation.”** Sai: đó có thể chỉ là số ứng viên, trong khi số advice executions phụ thuộc từng lượt thực thi và điều kiện.

**“Sau khi async method trả về là tác vụ đã xong.”** Sai với các API trả handle trước thời điểm completion. Cần đặt hook tại đúng loại sự kiện muốn đo.

**“Một kết quả nghiệp vụ đồng nghĩa một lần invocation.”** Không nhất thiết: retry, idempotency, phân tán, batching và cancellation có thể tạo quan hệ nhiều-một hoặc không có kết quả.

## 13. Bài tập tự đánh giá

### Câu 1 — Loop và condition

```java
for (int i = 0; i < 6; i++) {
    if (i % 2 == 0) {
        task.run(i);
    }
}
```

Có bao nhiêu biểu thức gọi `task.run(i)` trong đoạn mã, và bao nhiêu lần gọi thực sự diễn ra nếu vòng lặp chạy bình thường? **Đáp án:** một vị trí mã; ba lần gọi với `i = 0, 2, 4`.

### Câu 2 — Đa hình

Một biến kiểu `Processor` nhận lần lượt đối tượng `SafeProcessor` và `FastProcessor` ở hai vòng lặp. Cùng một biểu thức `processor.process()` được gọi 5 lần trên mỗi đối tượng. Có bao nhiêu vị trí call site và bao nhiêu occurrence? **Đáp án:** một call site trong giả thiết đoạn code chỉ viết biểu thức đó một lần; 10 occurrence; body thực thi được quyết định bởi target runtime.

### Câu 3 — Đệ quy

`factorial(4)` có bao nhiêu lần vào phần thân `factorial` và bao nhiêu lần thực thi biểu thức gọi đệ quy `factorial(n - 1)`? **Đáp án:** bốn lượt execution của thân method, ba lượt call tại biểu thức đệ quy. Lời gọi ban đầu xuất phát từ caller.

### Câu 4 — Proxy

Một lời gọi `proxy.save()` được handler ghi nhận, nhưng `target.save()` không được ghi nhận. Điều này có mâu thuẫn với thực tế cả hai chạy cùng method nghiệp vụ không? **Đáp án:** không. Handler được gắn vào **đường gọi qua proxy**, không tự chặn mọi execution của target.

### Câu 5 — Điểm đặt đo thời gian

Bạn cần đo toàn bộ thời gian hoàn tất job bất đồng bộ, nhưng advice after-returning chỉ quan sát lúc phương thức gửi job trả `Future`. Nên làm gì? **Đáp án:** xác định và instrument thêm sự kiện hoàn tất của future/task; phân biệt submit latency với end-to-end latency và xử lý exception/cancellation.

### Câu 6 — Kiểm chứng pointcut

Một marker xuất hiện trên method nhưng không thấy advice. Nêu ít nhất ba khả năng khác nhau. **Đáp án gợi ý:** marker đặt trên vị trí metadata mà matcher không đọc; đường gọi bỏ qua proxy; method chưa từng được chạy; điều kiện runtime không thỏa; engine chưa kích hoạt hoặc artifact chưa được weave.

## Kết luận

Trong AOP, **vị trí mã tĩnh** trả lời “chương trình có thể tạo ra điểm can thiệp ở đâu?”, còn **sự kiện runtime** trả lời “điều gì đã thật sự xảy ra tại lần chạy này?”. Với các mô hình hỗ trợ khái niệm *join-point shadow*, shadow nối giữa hai góc nhìn; với proxy hay kỹ thuật khác, ranh giới quan sát được xác định theo đường gọi và hợp đồng triển khai. Một source call site có thể tạo vô số lần runtime, có thể chọn nhiều implementation và có thể bị bỏ qua hoàn toàn. Vì vậy, trước khi đếm advice hoặc đặt pointcut, hãy xác định **loại join point, tầng mã đang xét, điều kiện động và con đường thật mà chương trình chạy qua**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="join-points-009">Những thao tác không thuộc mô hình join point của công cụ đang dùng</a>

<details>
<summary>Xem chi tiết</summary>

# Những thao tác không thuộc mô hình join point của công cụ đang dùng

> **Mục tiêu:** Hiểu vì sao một hoạt động xảy ra trong chương trình nhưng không nhất thiết là **join point** mà một công cụ lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP) nhận diện được; phân biệt giới hạn của **mô hình join point**, **pointcut**, **weaving** và **đường thực thi**; tự xác định vùng được can thiệp, vùng không thể can thiệp, cũng như cách chọn một cơ chế phù hợp để không bỏ sót hành vi quan trọng.

## 1. Một hành vi có thật trong chương trình chưa chắc là join point

Một chương trình Java có thể tạo đối tượng, gọi phương thức, đọc/ghi trường, chuyển luồng thực thi, ném và bắt ngoại lệ, đồng bộ hóa, gọi mã native, gửi yêu cầu ra mạng, hoặc thay đổi dữ liệu ở hệ thống bên ngoài. Tất cả đều là những hoạt động thật. Nhưng **không phải mọi hoạt động đều là join point của công cụ AOP đang dùng**.

Trong AOP, **join point** là một điểm hoặc sự kiện thuộc mô hình thực thi mà cơ chế AOP có khả năng xác định và cho phép kết hợp hành vi bổ sung (*advice*), khi mã liên quan nằm trong phạm vi xử lý của cơ chế.

Điểm phân biệt then chốt:

- **Hoạt động thực tế:** bất kỳ công việc nào hệ thống thực hiện.
- **Join point theo mô hình:** loại điểm/sự kiện mà công cụ AOP **định nghĩa là có thể can thiệp**.
- **Join point được pointcut chọn:** điểm thuộc mô hình và thỏa biểu thức lựa chọn.
- **Join point được weaving/đường thực thi bao phủ:** điểm mà cơ chế kết hợp aspect **thật sự** có khả năng quan sát trong chương trình đang chạy.
- **Advice đã chạy:** kết quả quan sát được khi mọi điều kiện về mô hình, lựa chọn, triển khai và luồng thực thi đều được đáp ứng.

Ví dụ, một cơ chế chỉ chặn **lời gọi phương thức qua proxy của giao diện** sẽ không tự biến câu lệnh gán trường `order.status = "PAID"` thành join point mà proxy nhìn thấy. Việc mở rộng pointcut từ “một phương thức” thành “mọi phương thức” **không làm cho gán trường trở thành lời gọi phương thức qua proxy**.

Ngược lại, một mô hình AOP có hỗ trợ join point kiểu **field set** có thể can thiệp vào phép gán trường theo điều kiện và phạm vi weaving phù hợp. Vì vậy, ta không thể kết luận chung rằng “AOP không chặn được gán trường”; kết luận đúng phải là **“cơ chế cụ thể đang xét có hỗ trợ loại join point đó không?”**.

## 2. Bốn lớp quyết định khả năng can thiệp

Có thể đánh giá một hoạt động từ ngoài vào trong theo bốn câu hỏi:

```text
Hoạt động cần quan sát
         |
         v
(1) Loại hoạt động thuộc JOIN POINT MODEL?
         |
         v
(2) POINTCUT có chọn đúng điểm đó?
         |
         v
(3) Mã/đường thực thi nằm trong WEAVING SCOPE?
         |
         v
(4) Khi chạy, sự kiện có đi qua cơ chế can thiệp
    và thỏa những điều kiện động?
         |
         v
Advice có thể được kích hoạt
```

Đây là **mô hình phân tích khái niệm**. Một công cụ thực tế có thể thực hiện các bước theo thứ tự khác hoặc tối ưu một số bước, nhưng bốn câu hỏi vẫn hữu ích để chẩn đoán.

| Lớp | Câu hỏi | Ví dụ nguyên nhân không chạy advice |
|---|---|---|
| **Join point model** | Công cụ có hỗ trợ *loại* điểm này? | Chỉ hỗ trợ method invocation qua proxy, không hỗ trợ field write |
| **Pointcut** | Điểm có thỏa điều kiện lựa chọn? | Pointcut chỉ chọn `save*`, còn phương thức là `commit()` |
| **Weaving scope** | Mã liên quan đã nằm trong phạm vi cơ chế xử lý? | Một thư viện/mô-đun bị loại khỏi phạm vi weaving |
| **Runtime path / dynamic match** | Lần chạy cụ thể có đi qua ranh giới can thiệp và thỏa điều kiện? | Lời gọi trực tiếp tới đối tượng đích thay vì đi qua proxy; đối số không khớp |

**Điều kiện tiên quyết:** Nếu một hoạt động **không thuộc join point model**, không có pointcut nào trong **chính mô hình đó** có thể chọn nó. Muốn bao phủ hành vi, có thể cần thay đổi ranh giới thiết kế, một cơ chế weaving khác, một công cụ instrumentation khác hoặc quan sát sự kiện ở cấp phù hợp hơn.

## 3. Join point khác với từng câu lệnh và từng tác dụng phụ

Sai lầm phổ biến là suy luận: “Trong phương thức có 15 dòng mã, vậy chắc chắn có 15 join point.”

Join point không phải khái niệm đồng nghĩa với **dòng mã**, **biểu thức**, **instruction của JVM** hay **tác dụng phụ nghiệp vụ**.

Ví dụ:

```java
total += price;
order.status = "PAID";
System.out.println("Paid");
```

Đây là **mã minh họa rời**, không phải chương trình chạy độc lập. Trong đó:

- `total += price` thực hiện phép đọc/tính toán/gán tùy hình thức biến, nhưng không nhất thiết là join point của mọi AOP.
- `order.status = "PAID"` là phép ghi trường; chỉ có thể là join point kiểu **field set** nếu mô hình và phạm vi weaving hỗ trợ.
- `System.out.println(...)` có lời gọi phương thức. Một cơ chế hỗ trợ method call hoặc method execution **có thể** chọn nó, nhưng vẫn phải xét nơi mã được weaving và điều kiện pointcut.

Tương tự, thông tin “đã gửi email”, “đã ghi vào cơ sở dữ liệu” hay “đã hoàn tất giao dịch” **không tự là join point**. Đó là sự kiện hoặc kết quả có ý nghĩa nghiệp vụ; join point chỉ có thể đại diện cho một ranh giới kỹ thuật phù hợp nếu ta xác định và công cụ có khả năng quan sát.

## 4. Những loại join point mà các mô hình AOP có thể khác nhau

Không có một danh sách loại join point duy nhất áp dụng cho mọi cơ chế. Một mô hình AOP ở mức mã/bytecode có thể phong phú hơn một cơ chế dựa trên việc chuyển lời gọi qua đối tượng trung gian.

| Hoạt động trong chương trình | Khả năng trong các mô hình AOP | Câu hỏi phải xác minh |
|---|---|---|
| **Thực thi phương thức** (*method execution*) | Được nhiều mô hình hỗ trợ theo cách khác nhau | Chọn tại thân phương thức hay tại proxy bao quanh lời gọi? |
| **Lời gọi phương thức** (*method call*) | Có mô hình coi là join point riêng; có mô hình không | Có thể can thiệp tại **call site** không? |
| **Gọi constructor** (*constructor call*) | Chỉ một số cơ chế hỗ trợ | Cơ chế có xử lý biểu thức tạo thể hiện không? |
| **Thực thi constructor** (*constructor execution*) | Chỉ một số cơ chế hỗ trợ | Có nhận diện khi constructor thực sự chạy không? |
| **Khởi tạo đối tượng** (*initialization*) | Có mô hình định nghĩa riêng | Phạm vi khởi tạo và thứ tự advice là gì? |
| **Đọc trường** (*field get*) | Không phải khả năng mặc định của mọi công cụ | Có weaving được truy cập trường hay chỉ method? |
| **Ghi trường** (*field set*) | Tương tự field get | Phép gán trực tiếp, reflection hoặc access đặc biệt có được bao phủ? |
| **Xử lý ngoại lệ** (*exception handler*) | Một số mô hình có loại join point này | Can thiệp tại khối `catch` hay chỉ quanh method bị lỗi? |
| **Khởi tạo lớp, thao tác đồng bộ hóa hoặc sự kiện thấp hơn** | Tùy mô hình và phần mở rộng | Loại sự kiện có nằm trong đặc tả thực tế? |
| **Sự kiện ngoài tiến trình** (thông điệp, bản ghi DB, thay đổi filesystem do tiến trình khác) | Không tự trở thành join point của chương trình Java | Cần quan sát ở API, giao thức hoặc hệ thống sở hữu sự kiện? |

**Không nên học bảng như một tập quy tắc cứng** kiểu “field access luôn không thể chặn”. Cùng một hoạt động có thể là join point ở một mô hình nhưng không tồn tại trong mô hình khác.

### 4.1. Method call khác method execution

- **Call**: vị trí chương trình **phát sinh lời gọi**.
- **Execution**: việc phần thân của phương thức đích **được thực thi**.

Một phương thức có thể được gọi từ nhiều vị trí. Với weaving ở call site, quy tắc chọn caller có thể có ý nghĩa; với weaving ở execution, chính phương thức thực thi là trung tâm. Không nên giả định công cụ có cả hai loại chỉ vì nó “chặn được method”.

### 4.2. Phép đọc/ghi trường khác lời gọi getter/setter

```java
account.balance = 100;
int x = account.balance;
```

Nếu chương trình thay bằng `account.setBalance(100)` và `account.getBalance()`, ta đã **thay đổi ranh giới thiết kế** sang lời gọi method. Một công cụ chặn method có thể can thiệp vào getter/setter khi lời gọi đi qua cơ chế của nó, nhưng **không vì thế** can thiệp vào mọi phép gán/đọc trường trực tiếp khác.

Getter/setter cũng không mặc nhiên đại diện cho **mọi cách** thay đổi trạng thái: còn khởi tạo trong constructor, truy cập trực tiếp, reflection, hoặc những cơ chế khác được ngôn ngữ/runtime cho phép.

### 4.3. Exception handler khác “sau khi ném ngoại lệ”

Một advice chạy khi **method kết thúc bằng ngoại lệ** là hành vi quanh một join point method được lựa chọn. Nó không đồng nghĩa với join point tại **khối xử lý ngoại lệ**.

Nếu mô hình có join point *exception handler*, đó là một loại điểm riêng liên quan nơi ngoại lệ được xử lý. Việc “chặn mọi câu lệnh `throw`” lại là yêu cầu khác nữa và không tự suy ra từ việc hỗ trợ after-throwing advice.

## 5. Vì sao giới hạn của proxy hay bị hiểu thành giới hạn của AOP?

Trong Java, một cách tạo điểm can thiệp là đưa lời gọi qua **đối tượng trung gian**. Java Dynamic Proxy chuẩn cho phép tạo đối tượng triển khai một hoặc nhiều giao diện và xử lý các lời gọi **phương thức giao diện** qua `InvocationHandler`.

Nếu caller gọi qua proxy, handler có cơ hội can thiệp. Nếu caller giữ tham chiếu tới đối tượng đích và gọi trực tiếp, handler **không tự chạy**.

Ví dụ về các ranh giới:

```text
caller --> proxy(TaskService) --> handler --> target.execute()
                 |
                 +--> điểm quan sát qua proxy

caller ------------------------> target.execute()
                                      |
                                      +--> không qua handler của proxy

target.execute() --> this.internal()
                         |
                         +--> không phải một lời gọi mới qua proxy
```

Đây là giới hạn của **đường đi qua đối tượng trung gian**, không phải định nghĩa chung của AOP.

Một cơ chế weaving trực tiếp vào mã có thể quan sát các loại join point khác; mặt khác, chính cơ chế đó vẫn có thể bị giới hạn bởi các lớp được weaving, tình trạng nạp lớp, cấu hình và loại điểm mà nó định nghĩa.

## 6. Ví dụ Java 17: những thao tác proxy thực sự quan sát

Chương trình sau sử dụng Java Dynamic Proxy để **minh họa một tập điểm can thiệp giới hạn**. Nó không phải framework AOP và không tự triển khai các loại join point nâng cao.

Lưu file `JoinPointCoverageDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class JoinPointCoverageDemo {
    interface TaskService {
        void execute(String task);
        void ping();
    }

    static final class TaskServiceImpl implements TaskService {
        private int completed;

        TaskServiceImpl() {
            System.out.println("target.constructor");
        }

        @Override
        public void execute(String task) {
            System.out.println("target.execute " + task);
            internal();
            completed++;
        }

        @Override
        public void ping() {
            System.out.println("target.ping");
        }

        private void internal() {
            System.out.println("target.internal");
        }

        static void staticCheck() {
            System.out.println("target.staticCheck");
        }
    }

    public static void main(String[] args) {
        TaskServiceImpl target = new TaskServiceImpl();

        TaskService proxy = (TaskService) Proxy.newProxyInstance(
                TaskService.class.getClassLoader(),
                new Class<?>[] {TaskService.class},
                (proxyObject, method, values) -> {
                    System.out.println("intercept " + method.getName());
                    try {
                        return method.invoke(target, values);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );

        System.out.println("-- proxy path --");
        proxy.execute("A");
        proxy.ping();

        System.out.println("-- direct path --");
        target.execute("B");
        TaskServiceImpl.staticCheck();

        System.out.println("completed " + target.completed);
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac JoinPointCoverageDemo.java
java JoinPointCoverageDemo
```

**Stdout dự kiến chính xác:**

```text
target.constructor
-- proxy path --
intercept execute
target.execute A
target.internal
intercept ping
target.ping
-- direct path --
target.execute B
target.internal
target.staticCheck
completed 2
```

### 6.1. Phân tích từng hoạt động

| Hoạt động | Có dòng `intercept` của handler? | Lý do |
|---|---|---|
| `new TaskServiceImpl()` | Không | Java Dynamic Proxy được tạo **sau** khi target đã được xây dựng; nó không bọc constructor của target |
| `proxy.execute("A")` | **Có** | Lời gọi phương thức giao diện đi qua proxy |
| `proxy.ping()` | **Có** | Lời gọi phương thức giao diện đi qua proxy |
| `internal()` được gọi bên trong `execute()` | Không có intercept mới | Đường gọi nội bộ tới phương thức private trên target không đi qua proxy |
| `target.execute("B")` | Không | Caller dùng trực tiếp tham chiếu target |
| `TaskServiceImpl.staticCheck()` | Không | Lời gọi static không đi qua phương thức giao diện của proxy |

**Điều quan trọng:** việc không có dòng `intercept` cho một thao tác ở ví dụ **chỉ chứng minh giới hạn của cơ chế Java Dynamic Proxy đang triển khai**. Nó không chứng minh rằng mọi công cụ AOP đều không thể quan sát constructor, method private hoặc method static.

`completed` cuối cùng bằng `2` cho thấy cả `execute("A")` và `execute("B")` **đều chạy nghiệp vụ**, dù handler chỉ quan sát một đường gọi. Không được suy từ “advice không chạy” thành “thao tác nghiệp vụ không xảy ra”.

### 6.2. Vì sao ví dụ có `InvocationTargetException`?

Trong đoạn `method.invoke(target, values)`, nếu phương thức đích ném lỗi, lời gọi phản chiếu có thể bọc nguyên nhân trong `InvocationTargetException`. Handler tháo lớp bọc bằng `e.getCause()` để phần ngoài nhận lỗi gốc. Đây là đặc điểm của reflection trong **ví dụ minh họa**, không phải định nghĩa chung của advice.

Để xác nhận một cơ chế thực tế có bảo toàn ngoại lệ, return value và số lần gọi nghiệp vụ hay không, cần viết kiểm thử theo hợp đồng đó, nhất là khi có around advice.

## 7. Điều kiện pointcut không thể tự mở rộng tập loại join point

Giả sử mô hình AOP có tập loại join point được hỗ trợ:

```text
SupportedTypes = {method execution}
```

Ta tạo một pointcut rất rộng:

```text
P = chọn mọi phương thức phù hợp
```

**Dù P chọn mọi phương thức, nó không làm xuất hiện loại `field set` hay `constructor call` mới.** Biểu thức pointcut lựa chọn trên miền mà mô hình cung cấp, tương tự một bộ lọc chỉ chọn được các phần tử có trong tập đầu vào.

Cần tách hai kết luận:

- **“Không có join point loại đó”**: không thể lựa chọn bằng pointcut trong mô hình hiện có.
- **“Join point loại đó có, nhưng pointcut không chọn”**: có thể xem lại biểu thức, cấu hình điều kiện hoặc giá trị động.

Một cách chẩn đoán hợp lý là kiểm tra loại join point **trước**, rồi mới xét biểu thức lựa chọn.

## 8. Weaving scope: join point có hỗ trợ nhưng mã vẫn chưa được xử lý

Một công cụ AOP có hỗ trợ method execution ở mức mã không có nghĩa mọi lớp trong JVM đều đã được weaving.

Các nguyên nhân thực tế có thể bao gồm:

- Lớp thuộc mô-đun/thư viện nằm **ngoài phạm vi weaving** được cấu hình.
- Lớp được nạp trước khi cơ chế can thiệp phù hợp được kích hoạt; việc xử lý lại lớp có thể không được hỗ trợ.
- Môi trường runtime hoặc cách đóng gói không cho phép biến đổi một số lớp.
- Mô hình chỉ áp dụng đối với đối tượng được tạo và quản lý qua ranh giới định trước.
- Một đường thực thi đi thẳng đến đối tượng đích thay vì lớp trung gian.
- Điểm đang xét thuộc đoạn mã native hoặc ngoài tiến trình, vượt ra khỏi dạng biểu diễn mà cơ chế xử lý.

Các nguyên nhân này cần được xác minh **trên chương trình đang chạy**. Chỉ nhìn pointcut rồi nhận định “mọi lời gọi đều được chặn” là chưa đủ.

### 8.1. Join point tĩnh có trong chương trình nhưng không phát sinh khi chạy

Có những pointcut chọn một vị trí mã hợp lệ, nhưng nhánh chứa vị trí đó **không được thực thi** trong một ca thử cụ thể. Khi đó advice không chạy là điều đúng.

Ví dụ `if (false)` trong một đoạn giả định có thể khiến lời gọi không xảy ra; không nên kết luận do lỗi weaving. Phải tách **điểm được định nghĩa** khỏi **lần xuất hiện động của điểm**.

### 8.2. Không phải mọi lần “gọi cùng tên” đều là cùng loại điểm

Một lời gọi được tạo bằng reflection, method handle, generated code hoặc kỹ thuật runtime đặc biệt có thể đi qua đường thực thi khác lời gọi trực tiếp trong source. Một cơ chế weaving ở **constructor/method execution** có thể quan sát thân mã được weaving ngay cả khi caller dùng reflection; một cơ chế đặt tại **call site tĩnh** có thể có phạm vi khác. Không gán cùng kết luận cho hai cơ chế chỉ vì cả hai đều “làm AOP”.

## 9. Private, static, final: không có một quy tắc phủ định chung

Ba từ khóa Java `private`, `static`, `final` thường xuất hiện trong các câu “AOP không chặn được…” nhưng cần hiểu lý do kỹ thuật.

### 9.1. Private method

Phương thức private **không thể bị override từ lớp con** theo cơ chế đa hình thông thường. Vì vậy, một cơ chế chỉ chặn bằng cách override phương thức ở subclass sẽ có giới hạn với private method. Một cơ chế weaving trực tiếp ở điểm thực thi mã có thể có khả năng khác.

### 9.2. Static method

Static method gắn với lớp, không được dispatch đa hình theo tham chiếu instance như phương thức interface. Proxy của phương thức interface không thể tự đại diện cho mọi lời gọi static. Công cụ weaving ở call site hoặc method execution có thể xem xét một khả năng khác, tùy mô hình.

### 9.3. Final method và final class

Nếu một cách can thiệp yêu cầu tạo subclass và override một phương thức, thì **final method không thể override**, còn **final class không thể bị kế thừa**. Đây là hạn chế của cơ chế dựa trên subclass.

**Không kết luận rằng final method tuyệt đối không thể được quan sát.** Một proxy giao diện có thể chặn lời gọi ở lớp đại diện **trước khi chuyển tới phương thức triển khai** ngay cả khi triển khai có modifier final; một cơ chế weaving bytecode lại có ranh giới khác.

### 9.4. Self-invocation

Một phương thức được gọi bằng `this.otherMethod()` hoặc lời gọi ngầm `otherMethod()` từ bên trong đối tượng có thể bỏ qua proxy bên ngoài. Nhưng đây là kết quả của **đường gọi qua proxy**, không phải một đặc tính chung khẳng định AOP không quan sát lời gọi nội bộ.

Khi xem một hạn chế, hãy luôn hỏi: **“Nó là giới hạn của ngôn ngữ Java, của join point model hay chỉ của một chiến lược chặn?”**

## 10. Constructor, cấp phát và khởi tạo đối tượng

Yêu cầu “theo dõi mọi đối tượng mới” thường bị diễn giải quá rộng.

```java
Invoice invoice = new Invoice();
```

Đoạn này có thể liên quan đến:

- Biểu thức cấp phát/tạo thể hiện.
- Nơi gọi constructor (*constructor call*) theo mô hình AOP hỗ trợ loại điểm này.
- Việc thực thi một hoặc nhiều constructor trong chuỗi khởi tạo.
- Quá trình khởi tạo trường và trạng thái đối tượng.
- Trường hợp constructor ném ngoại lệ khiến biểu thức tạo không hoàn tất bình thường.

Một mô hình chỉ hỗ trợ method execution thông thường **chưa chắc** hỗ trợ constructor call hoặc constructor execution. Một cơ chế hỗ trợ constructor execution cũng **không tự trở thành máy đếm toàn bộ cấp phát trong JVM**.

Một số đường tạo đối tượng còn có thể khác `new` trực tiếp: factory trả đối tượng có sẵn, reflection, sao chép đối tượng hoặc các cơ chế tái tạo trạng thái. Phạm vi chặn theo constructor phải được diễn đạt bằng **loại join point được hỗ trợ** và **đường tạo cụ thể**.

### 10.1. Ví dụ về ranh giới không tương đương

Giả sử `OrderFactory.getOrCreate(id)` trả thể hiện đã lưu trong bộ nhớ đệm. Một lời gọi factory **không đồng nghĩa** một lần constructor chạy. Nếu muốn đo “bao nhiêu yêu cầu lấy đơn hàng”, chọn ranh giới factory có thể phù hợp hơn. Nếu muốn đo “bao nhiêu thể hiện mới được tạo”, cần quan sát ranh giới tạo thực sự.

Một pointcut tên `*create*` trên method chỉ là quy tắc dựa trên tên; nó không chứng minh phương thức đó thực sự tạo đối tượng.

## 11. Field access: khác biệt giữa thay đổi trạng thái và lời gọi method

Một chương trình có thể thay đổi trạng thái qua nhiều con đường:

```java
record.count++;
record.count = 10;
record.updateCount(10);
```

Đây là **mã minh họa rời**. Hai câu đầu truy cập trường trực tiếp (theo quy tắc truy cập hợp lệ trong ngữ cảnh); câu cuối là lời gọi method. Với một cơ chế AOP chỉ hỗ trợ method invocation qua proxy, **chỉ câu cuối** có thể đi qua điểm chặn của proxy nếu đối tượng được gọi qua proxy và phương thức thuộc giao diện được bọc.

Một mô hình hỗ trợ `get`/`set` join point có thể chọn các truy cập trường. Nhưng vẫn cần xét sự khác biệt giữa bytecode truy cập trường thông thường với những cơ chế phản chiếu hoặc truy cập bộ nhớ khác. Không suy từ việc hỗ trợ field set ở một đường đi rằng mọi thay đổi bộ nhớ đều được quan sát.

**Hệ quả thiết kế:** Nếu tính đúng đắn hoặc an toàn dựa vào việc chặn mọi thay đổi của một bất biến, chính sách nên đặt ở ranh giới sở hữu trạng thái và có kiểm thử chứng minh phạm vi, thay vì chỉ dựa vào pointcut trên một số setter.

## 12. Ngoại lệ: ném, lan truyền, bắt và xử lý là các mốc khác nhau

Ví dụ:

```java
try {
    service.perform();
} catch (IllegalStateException error) {
    recover(error);
}
```

Một cơ chế AOP có thể quan sát việc `perform()` thoát với ngoại lệ qua advice gắn vào method execution. Nhưng đó **không tự là** join point của câu `throw` gây ra ngoại lệ, cũng không tự là join point tại `catch`.

Cần tách:

1. **Nơi phát sinh ngoại lệ** (một lệnh `throw` hoặc runtime ném).
2. **Đường lan truyền** qua các phương thức.
3. **Nơi bắt/xử lý** ngoại lệ.
4. **Kết quả của một phương thức** (thoát bình thường hoặc ném lỗi).

Nếu cần quan sát “mọi exception handler”, phải xác minh mô hình có loại join point tương ứng. Nếu chỉ cần biết thao tác nghiệp vụ thất bại, việc quan sát ranh giới method với after-throwing advice (nếu được hỗ trợ) có thể là lựa chọn hợp lý hơn.

Một advice bắt lỗi cũng không bảo đảm xử lý được lỗi ở ngoài vùng áp dụng, chẳng hạn trước khi vào phương thức được quan sát hoặc trong một tiến trình khác.

## 13. Bất đồng bộ, callback và luồng thực thi

Giả sử một phương thức `startJob()` trả về ngay sau khi đưa tác vụ vào hàng đợi, còn công việc thực sự được chạy sau trên một luồng khác.

Nếu pointcut chọn **thực thi `startJob()`**, advice bao quanh phương thức đó thường chỉ quan sát thời gian từ lúc vào đến khi trả về hoặc ném lỗi. Nó **không mặc nhiên đo đến lúc tác vụ hoàn thành**.

Tương tự, những sự kiện có ý nghĩa ở luồng khác, callback, event loop hoặc quá trình xử lý ngoài tiến trình có thể là những thao tác **không thuộc loại join point** của điểm được chọn ban đầu.

Để quan sát một vòng đời bất đồng bộ, cần định nghĩa rõ:

- Mốc **bắt đầu lập lịch**.
- Mốc **bắt đầu thực thi công việc**.
- Mốc **hoàn tất/thất bại của công việc**.
- Cách liên kết ngữ cảnh, chẳng hạn mã tương quan qua các luồng/tiến trình.
- Những ranh giới có thể can thiệp bằng AOP và những ranh giới cần cơ chế quan sát khác.

Không được hiểu “đã chặn method trả về future” là “đã chặn sự kiện future hoàn tất”, trừ khi có cơ chế quan sát tương ứng.

## 14. Hoạt động ngoài tiến trình và giới hạn của join point nội bộ

Một lời gọi `sendMessage()` có thể được aspect quan sát, nhưng việc một hệ thống khác **nhận**, **xử lý** hoặc **ghi dữ liệu** là sự kiện ở ranh giới khác.

Ví dụ:

```text
Ứng dụng A:
  method sendMessage()       ← có thể là method join point ở A
         |
         v
      Mạng / broker
         |
         v
Ứng dụng B:
  xử lý thông điệp           ← không tự là join point của A
         |
         v
      Cơ sở dữ liệu
```

Advice trước/sau `sendMessage()` chỉ cho ta tín hiệu ở **vùng chương trình quan sát được**. Nó không chứng minh giao dịch đã được hệ thống B xử lý thành công. Muốn xác minh kết quả cuối, cần thiết kế event, acknowledgement, trace phân tán hoặc cơ chế quan sát thích hợp.

Điều này cũng đúng với hành vi do hệ điều hành, database trigger, tác vụ native hoặc tiến trình khác thực hiện: những sự kiện đó không tự nằm trong join point model của một công cụ AOP Java chỉ vì Java từng gọi một API liên quan.

## 15. Pointcut không chọn được loại điểm chưa được mô hình định nghĩa

Có hai tình huống rất khác nhau:

**Tình huống A:** Công cụ có method execution join point; pointcut chỉ chọn phương thức `saveOrder`, nên không chọn `cancelOrder`.

- Đây là **vấn đề điều kiện chọn**.
- Có thể sửa pointcut nếu nghiệp vụ yêu cầu thêm `cancelOrder`.

**Tình huống B:** Công cụ chỉ có method invocation qua proxy; yêu cầu mới là chặn phép gán `order.status = "DONE"` trực tiếp.

- Đây là **vấn đề loại điểm/ranh giới can thiệp**.
- Không thể khắc phục chỉ bằng cách dùng pointcut có wildcard rộng hơn.

Một nhóm thứ ba cũng cần phân biệt: công cụ **có** loại join point cần thiết, pointcut **đúng**, nhưng lớp hoặc đường thực thi **không được weaving/không đi qua ranh giới**. Lúc đó phải kiểm tra phạm vi triển khai và runtime path.

## 16. Những dấu hiệu dễ dẫn đến kết luận sai

### 16.1. “Advice không in log” không chứng minh pointcut sai

Có thể điểm cần theo dõi không xảy ra trong ca thử, log bị lọc, advice ném lỗi trước khi ghi, code chạy ngoài phạm vi weaving, hoặc ranh giới không được hỗ trợ. Cần kiểm tra từng tầng.

### 16.2. “Phương thức cùng tên” không chứng minh cùng join point

Overload, receiver, kiểu tham số, metadata, class loader và đường dispatch có thể khác nhau. Pointcut lựa chọn dựa trên điều kiện cụ thể, không dựa vào việc người đọc thấy hai tên giống nhau.

### 16.3. “Một thao tác đã được audit” không chứng minh mọi đường đi đều được audit

Một lời gọi qua proxy được ghi nhận, nhưng gọi trực tiếp có thể không. Cùng một nghiệp vụ có thể được kích hoạt từ API, batch, callback hoặc nội bộ mà đi qua các ranh giới khác nhau.

### 16.4. “Không thể chặn final/private/static” không phải kết luận chung

Phải nói rõ công cụ đang dùng là proxy giao diện, subclass proxy hay weaving mã; các loại điểm được hỗ trợ sẽ khác nhau. Không được suy luận vượt quá cơ chế.

### 16.5. “Có support” không đồng nghĩa “bật mặc định”

Một công cụ có thể hỗ trợ loại join point nào đó, nhưng cấu hình weaving, quyền biến đổi lớp, điều kiện phạm vi hoặc cách đóng gói hiện tại chưa kích hoạt nó. Hỗ trợ về lý thuyết cần được chứng minh bằng ca thử thực tế.

## 17. Quy trình chẩn đoán khi một thao tác không bị can thiệp

Có thể dùng quy trình sau để điều tra có hệ thống:

1. **Mô tả chính xác thao tác:** method call hay execution, constructor, field access, exception handler, object allocation, hoạt động bất đồng bộ hay sự kiện bên ngoài?
2. **Xác nhận mô hình join point:** đọc danh sách loại join point công cụ/cấu hình thực sự hỗ trợ; tránh suy từ tên AOP chung.
3. **Xác nhận điểm ứng viên:** vị trí mã/kiểu thành phần/call site nào đáng lẽ tạo join point?
4. **Kiểm tra pointcut:** biểu thức có chọn đúng chữ ký, kiểu, metadata và điều kiện động của sự kiện đó không?
5. **Kiểm tra weaving:** mã có nằm trong phạm vi được xử lý? Lớp có được nạp hay biến đổi đúng cách? Những đường gọi nào được áp dụng?
6. **Kiểm tra runtime path:** lời gọi có đi qua proxy/interceptor nếu cơ chế yêu cầu? Có self-invocation hay tham chiếu gốc không? Nhánh có thực sự được chạy?
7. **Kiểm tra hành vi advice:** có chạy nhưng log bị ẩn hoặc lỗi phát sinh? Có advice khác ảnh hưởng thứ tự/luồng điều khiển không?
8. **Đối chiếu ca dương và âm:** chuẩn bị ít nhất một sự kiện **phải khớp** và một sự kiện **không được khớp**; chứng minh hành vi nghiệp vụ vẫn đúng.
9. **Quyết định giải pháp:** sửa pointcut, mở rộng weaving, đổi ranh giới đặt chính sách hoặc chọn cơ chế quan sát thích hợp hơn.

Có thể ghi một bảng hỗ trợ ngắn như sau cho **cơ chế thực tế đã xác minh**, không nên ghi từ giả định:

| Thao tác yêu cầu | Mô hình hỗ trợ? | Pointcut chọn? | Weaving bao phủ? | Đã quan sát khi chạy? |
|---|---|---|---|---|
| Phương thức `submit()` qua ranh giới được quản lý | Cần xác minh | Cần kiểm tra | Cần kiểm tra | Cần kiểm thử |
| `this.submit()` bên trong target | Cần xác minh | Cần kiểm tra | Cần kiểm tra | Cần kiểm thử |
| `new Order()` | Cần xác minh | Cần kiểm tra | Cần kiểm tra | Cần kiểm thử |
| Ghi trường trực tiếp | Cần xác minh | Cần kiểm tra | Cần kiểm tra | Cần kiểm thử |
| Sự kiện được xử lý ở tiến trình khác | Không thuộc tự động join point nội bộ | Tùy ranh giới mới | Tùy công cụ khác | Cần observability liên tiến trình |

Bảng trên không coi “chưa chứng minh” là “không hỗ trợ”. Chỉ khẳng định âm khi có đặc tả, bằng chứng kỹ thuật hoặc ca kiểm thử đủ phạm vi.

## 18. Thiết kế giải pháp khi hành vi cần quan sát nằm ngoài mô hình

Có nhiều lựa chọn, tùy trách nhiệm và ranh giới hệ thống.

### 18.1. Dùng một ranh giới method rõ ràng

Nếu một thao tác trực tiếp trên trạng thái không có join point tương ứng, có thể **tổ chức lại thiết kế** sao cho thay đổi trạng thái đi qua một API có tên và hợp đồng rõ, như `order.markPaid()`. Khi đó, cơ chế AOP hỗ trợ method có thể áp dụng ở ranh giới được chọn.

Điều kiện là **mọi đường ghi hợp lệ** phải tuân thủ ranh giới đó. Nếu các nơi khác vẫn gán trường trực tiếp, không thể tuyên bố có bao phủ tuyệt đối.

### 18.2. Đặt chính sách tại factory hoặc decorator

Nếu cần kiểm soát việc tạo đối tượng mà công cụ không hỗ trợ constructor join point, một factory hoặc decorator có thể là ranh giới tập trung. Nó giúp tổ chức trách nhiệm rõ ràng, nhưng cũng chỉ bao phủ những nơi **chọn dùng** factory/decorator.

### 18.3. Chọn cơ chế weaving hỗ trợ đúng loại điểm

Khi cần field get/set, constructor call/execution, exception handler hoặc các sự kiện thấp hơn, có thể cần một công cụ AOP hoặc instrumentation có mô hình điểm can thiệp rộng hơn. Phải đánh giá chi phí, khả năng deploy, giới hạn runtime, tính bảo trì và quan hệ với công cụ debug.

Không giả định chuyển công cụ là đủ: vẫn phải xác minh điểm thực tế trong môi trường chạy.

### 18.4. Quan sát ở cấp có ngữ nghĩa phù hợp hơn

Một nhu cầu đo **mỗi message thực sự được xử lý**, **mỗi transaction commit**, hoặc **mỗi đối tượng runtime được cấp phát** đôi khi phù hợp hơn với tracing, hook của hạ tầng, event, agent runtime hoặc telemetry có chuyên môn phù hợp. AOP ở method có thể là một phần của giải pháp nhưng không thay thế mọi nguồn chứng cứ.

## 19. Những giới hạn quan trọng với an toàn và tính đúng đắn

### 19.1. Không đặt bảo đảm an toàn tuyệt đối trên vùng phủ chưa chứng minh

Nếu một chính sách kiểm soát quyền hoặc kiểm toán **bắt buộc** chỉ được gắn bằng một pointcut có khả năng bỏ sót đường gọi, hệ thống có thể tạo **khoảng trống chính sách**.

Nên tổ chức kiểm tra ở **ranh giới đáng tin cậy** và có kiểm thử âm/dương cho mọi entry point liên quan. AOP có thể giúp giảm lặp nhưng không thay thế thiết kế hợp đồng bảo mật và phân vùng trách nhiệm.

### 19.2. Thứ tự advice và thực thi nhiều lần

Một join point có thể phù hợp với nhiều aspect. Thứ tự bao ngoài/bao trong làm thay đổi đại lượng đo hoặc việc một advice có quan sát được ngoại lệ do advice khác phát sinh không. Với around advice, việc bỏ qua hoặc thực thi lại phần gốc có thể khiến số lần nghiệp vụ khác số lần join point mà người đọc mong đợi.

Phải kiểm tra **kết quả quan sát được** và **số lần thực hiện hành vi gốc**, không chỉ sự xuất hiện của một dòng log.

### 19.3. Điều kiện động và ngữ cảnh thực thi

Một pointcut có thể hỗ trợ điều kiện dựa trên đối số hoặc ngữ cảnh thread. Điều kiện sai ở một lần chạy không có nghĩa loại join point không được hỗ trợ. Trong xử lý bất đồng bộ, dữ liệu ngữ cảnh có thể không tự chuyển qua luồng; cần xác định đúng thời điểm và nguồn dữ liệu mà pointcut đánh giá.

### 19.4. Chi phí của việc cố bao phủ mọi thứ

Weaving rộng, ghi log mọi lần field access hoặc theo dõi từng thao tác tần suất cao có thể gây chi phí đáng kể và làm giảm khả năng đọc log. Xác định mục đích đo trước khi mở rộng loại điểm; không mặc định càng nhiều join point càng tốt.

## 20. Bài tập tự kiểm tra

**Bài 1 — Gán trường trực tiếp.** Một công cụ chỉ hỗ trợ method invocation qua proxy. Lập trình viên đặt pointcut “mọi method” nhưng vẫn không thấy log khi `object.flag = true`.

**Đáp án:** phép gán trường trực tiếp không phải lời gọi method qua proxy. Đó là **giới hạn mô hình/ranh giới**, không phải lỗi wildcard. Muốn chặn phải đổi điểm áp dụng hoặc cơ chế.

**Bài 2 — Hai cách gọi cùng nghiệp vụ.** `proxy.execute("A")` có advice; `target.execute("A")` không có. Có được kết luận pointcut về `execute` sai?

**Đáp án:** không. Cùng method nhưng đường gọi khác nhau; chỉ một đường đi qua proxy. Đây là **vấn đề đường thực thi**.

**Bài 3 — Constructor.** Có thể thêm pointcut tên “all new objects” vào cơ chế chỉ hỗ trợ method invocation và bảo đảm đếm mọi lần cấp phát đối tượng không?

**Đáp án:** không. Phải xác nhận có loại join point constructor/object allocation thích hợp; hơn nữa mọi cách tạo đối tượng chưa chắc đi qua một constructor-call join point duy nhất.

**Bài 4 — Lỗi được bắt.** Advice chạy khi `calculate()` thoát bằng ngoại lệ. Vậy mọi khối `catch` trong chương trình đều được can thiệp?

**Đáp án:** không. After-throwing advice ở method và join point của exception handler (nếu được hỗ trợ) là hai khái niệm khác nhau.

**Bài 5 — Công việc bất đồng bộ.** Advice quanh `enqueueJob()` ghi “completed” khi method trả về, nhưng tác vụ có thể còn chạy một giờ. Vấn đề ở đâu?

**Đáp án:** **sai ranh giới ngữ nghĩa**. Thời điểm method trả về chỉ phản ánh việc enqueue hoàn tất (theo hợp đồng của method), không phản ánh vòng đời job. Cần quan sát điểm hoàn tất job.

**Bài 6 — Final method.** Một người nói “AOP không can thiệp được vào phương thức final”. Bạn cần hỏi gì trước?

**Đáp án:** “Công cụ dùng subclass override, proxy giao diện, weaving vào mã hay cơ chế khác?” Giới hạn của subclass không phải quy tắc phổ quát cho tất cả mô hình AOP.

**Bài 7 — Lớp bên ngoài phạm vi weaving.** Công cụ hỗ trợ method execution, pointcut đúng chữ ký, nhưng lớp đích không được weaving. Thuộc tầng nào?

**Đáp án:** **weaving scope**. Cần kiểm tra vùng xử lý mã, thời điểm nạp lớp và cách công cụ áp dụng biến đổi, thay vì mở rộng pointcut một cách mù quáng.

## 21. Checklist trước khi tuyên bố một thao tác được AOP bao phủ

1. Tôi đã định nghĩa **chính xác loại hoạt động** cần quan sát chưa?
2. Loại đó có thuộc **join point model** của cơ chế đang dùng không?
3. **Pointcut** có chọn nó với đúng chữ ký và điều kiện động không?
4. **Weaving scope** có bao gồm mã/lớp liên quan không?
5. Mọi đường thực thi cần thiết có đi qua **ranh giới can thiệp** không?
6. Tôi đã phân biệt **lời gọi**, **thực thi**, **hoàn tất thành công** và **sự kiện nghiệp vụ cuối cùng** chưa?
7. Tôi có ca kiểm thử cho đường **phải được can thiệp** và đường **không được can thiệp** không?
8. Khi advice chạy, kết quả nghiệp vụ, ngoại lệ và số lần thực thi gốc có được giữ đúng hợp đồng không?
9. Nếu thao tác không có join point phù hợp, tôi đã chọn **ranh giới khác hoặc công cụ khác** thay vì chỉ sửa tên pointcut chưa?

## 22. Ghi nhớ

**Mô hình join point đặt ra miền sự kiện có thể lựa chọn. Pointcut chỉ lọc các điểm trong miền đó. Weaving quyết định phạm vi mã mà cơ chế thực sự xử lý, còn đường thực thi và điều kiện động quyết định sự kiện nào được quan sát lúc chạy.**

Một lời gọi method, một biểu thức `new`, một phép gán trường, một khối `catch`, một callback bất đồng bộ và một sự kiện ngoài tiến trình **không mặc nhiên là cùng loại điểm can thiệp**. Không thấy advice có thể là hành vi đúng, giới hạn mô hình, điều kiện không khớp, thiếu weaving hoặc đường gọi đi ngoài ranh giới.

Khi xử lý yêu cầu AOP, hãy xuất phát từ **thao tác thực sự cần quan sát** rồi xác minh khả năng của công cụ. Nếu loại hoạt động nằm ngoài join point model, thay đổi pointcut không tạo thêm loại điểm mới; cần điều chỉnh cách tổ chức ranh giới hoặc dùng cơ chế thích hợp.


</details>

- [Quay lại đầu trang](#back-to-top)
