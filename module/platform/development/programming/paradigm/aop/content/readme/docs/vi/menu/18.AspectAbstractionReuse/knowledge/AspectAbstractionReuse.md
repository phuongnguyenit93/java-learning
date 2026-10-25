<a id="back-to-top"></a>

# Trừu tượng và tái sử dụng

## Menu
- [Aspect trừu tượng và phần hành vi dùng chung](#aspect-abstraction-reuse-001)
- [Pointcut trừu tượng làm điểm tùy biến phạm vi](#aspect-abstraction-reuse-002)
- [Kế thừa aspect khi mô hình ngôn ngữ cho phép](#aspect-abstraction-reuse-003)
- [Tham số hóa chính sách thay vì chép lại advice](#aspect-abstraction-reuse-004)
- [Tách cơ chế can thiệp khỏi dịch vụ xử lý dùng chung](#aspect-abstraction-reuse-005)
- [Thiết kế giao diện cấu hình rõ ràng cho aspect](#aspect-abstraction-reuse-006)
- [Giới hạn phụ thuộc vào tên gói và chữ ký nội bộ](#aspect-abstraction-reuse-007)
- [Đóng gói aspect theo một mối quan tâm có trách nhiệm rõ](#aspect-abstraction-reuse-008)
- [Đánh giá mức tái sử dụng so với độ phức tạp](#aspect-abstraction-reuse-009)

## <a id="aspect-abstraction-reuse-001">Aspect trừu tượng và phần hành vi dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Aspect trừu tượng và phần hành vi dùng chung trong AOP

## 1. Vấn đề: cùng một hành vi, nhiều phạm vi áp dụng

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **aspect** là mô-đun tập hợp những mối quan tâm xuyên suốt như logging, metrics, audit, validation, policy hoặc kiểm tra hợp đồng. **Pointcut** chọn những **join point** mà công cụ AOP có thể nhận diện; **advice** cung cấp hành vi tại những điểm được chọn.

Giả sử ứng dụng có ba nhóm thao tác:

- Thao tác đơn hàng phải ghi thông tin bắt đầu/kết thúc.
- Thao tác thanh toán phải ghi thông tin tương tự, nhưng với tiêu chí chọn khác.
- Thao tác báo cáo chỉ cần đo thời gian, dùng lại một số bước quan sát.

Nếu tạo ba aspect gần như giống hệt, mã có thể lặp lại:

```text
Aspect A: chọn Order -> before -> run -> after/exception -> cleanup
Aspect B: chọn Payment -> before -> run -> after/exception -> cleanup
Aspect C: chọn Report -> before -> run -> after/exception -> cleanup
```

Một hướng trừu tượng hóa hợp lý là **tách hành vi dùng chung khỏi quyết định phạm vi**:

```text
             AbstractTracingAspect
             + common advice logic
             + common helper methods
             + abstract selection contract
                        ^
                        |
            +-----------+-----------+
            |                       |
    OrderTracingAspect      PaymentTracingAspect
    + pointcut for Order    + pointcut for Payment
    + optional hook         + optional hook
```

Nhờ đó:

1. Phần **chung** được viết, kiểm tra và bảo trì tập trung.
2. Phần **thay đổi** được hiện thực ở từng aspect cụ thể.
3. Consumer vẫn hiểu từng aspect được áp dụng cho những operation nào.
4. Hành vi xử lý return, exception và cleanup nhất quán, nếu chính sách yêu cầu.

Trong những ngôn ngữ AOP có hỗ trợ **aspect inheritance**, cấu trúc này có thể được biểu diễn bằng **aspect trừu tượng (abstract aspect)**. Nhưng **không phải mọi framework hoặc AOP engine đều hỗ trợ abstract aspect**; ở hệ thống dùng interceptor/proxy, ý tưởng tương tự có thể được triển khai qua strategy, abstract base class, composition hoặc template method.

**Phạm vi của bài:** kiến thức Aspect/AOP tổng quát từ cơ bản đến nâng cao. Những đoạn cú pháp AspectJ được ghi rõ là ví dụ của **một công cụ cụ thể**, không được coi là cú pháp AOP phổ quát hoặc mã Java 17 thuần.

## 2. Định nghĩa “aspect trừu tượng”

Một **abstract aspect** là cấu trúc mô-đun được khai báo trừu tượng trong một ngôn ngữ AOP có hỗ trợ, nhằm chia sẻ:

- Các method/hook/field chung.
- Các pointcut đã xác định hoàn toàn.
- Các **abstract pointcut** để aspect con cung cấp biểu thức lựa chọn.
- Advice dựa trên những pointcut được mở rộng hoặc hiện thực.
- Những quy tắc tổ chức khác nếu công cụ cho phép.

Một aspect cụ thể (*concrete aspect*) hoàn thành các phần trừu tượng cần thiết và tham gia cơ chế áp dụng advice thực tế.

Ví dụ khái niệm:

```text
abstract aspect BaseAudit:
    abstract selectedOperations()

    before selectedOperations():
        auditStart()

    afterReturning selectedOperations():
        auditSuccess()

concrete aspect OrderAudit extends BaseAudit:
    selectedOperations() = execution of order operations
```

Đây là **mã giả**, không phải cú pháp của mọi AOP language.

### 2.1. Abstract aspect không giống một aspect “bị tắt”

Một aspect trừu tượng có thể chứa advice hợp lệ về mặt cú pháp nhưng không được sử dụng độc lập như một **concrete aspect đầy đủ**. Việc advice trừu tượng được đưa vào runtime khi nào còn phụ thuộc mô hình inheritance/instantiation của engine.

Trong AspectJ code-style, cần **concrete aspect** kế thừa và hoàn thiện abstract pointcut để áp dụng advice; không thể nghĩ rằng khai báo một abstract aspect duy nhất sẽ tự động weave ra cùng hành vi như một concrete aspect có lựa chọn hợp lệ.

### 2.2. Abstract aspect và abstract class Java

Chúng có sự tương đồng về mục đích **dùng lại thiết kế**, nhưng khác về trách nhiệm:

| Đặc điểm | Java abstract class | Abstract aspect (ở công cụ hỗ trợ) |
| --- | --- | --- |
| Phần trừu tượng | Abstract method | Có thể có abstract pointcut và abstract method |
| Phần dùng chung | Field, method, template method | Helper, pointcut, advice, state |
| Phạm vi ảnh hưởng | Object/interface/hợp đồng Java | Những join point được công cụ AOP chọn |
| Triển khai vào runtime | Gọi method bình thường | Compiler/weaver/interception theo ngữ nghĩa công cụ |
| Tạo instance | Qua subclass concrete và `new` (nếu hợp lệ) | Aspect instance thường do runtime/weaver quản lý |
| Tự áp advice vào code nghiệp vụ | Không | Có thể sau khi cấu hình/build/weave đúng |

Một Java abstract class chỉ dùng làm cơ sở cho interceptor **không tự động** là abstract aspect thật.

## 3. Vị trí của abstract aspect trong hệ thống khái niệm AOP

Ba cấu phần có trách nhiệm khác nhau:

**Join point:** một điểm/sự kiện trong mô hình thực thi, như method execution hoặc method call, nếu engine hỗ trợ.

**Pointcut:** biểu thức hay predicate chọn join points. Nó trả lời “ở đâu/khi nào aspect có thể tác động”.

**Advice:** “làm gì tại điểm được chọn”, với các loại before, after returning, after throwing, around hay hình thức tương ứng.

**Abstract aspect:** một **cách tổ chức mô-đun**, không phải một loại join point mới. Nó có thể nắm giữ pointcut/advice/hook chung, để những concrete aspects lựa chọn phạm vi chuyên biệt.

Sơ đồ:

```text
Abstract aspect defines:
    selected()     <-- extension point
    before selected(): commonBefore()
    after selected(): commonAfter()

Concrete aspect A:
    selected() = join points of Service A

Concrete aspect B:
    selected() = join points of Service B
```

Hai aspect có thể tái sử dụng cùng hành vi nhưng chọn các join point **khác nhau**. Nếu hai tập giao nhau, **cả hai concrete aspects có thể áp advice lên cùng join point**; đây là vấn đề phải kiểm soát, không được mặc định hành vi chỉ chạy một lần vì mã nguồn chung nằm ở một base aspect.

## 4. Lợi ích của việc gom hành vi dùng chung

### 4.1. Giảm trùng lặp có ý nghĩa

Khi nhiều aspect cùng dùng:

- Cùng cách ghi trace start/end.
- Cùng cách phân loại lỗi.
- Cùng quy tắc bảo vệ dữ liệu trước khi logging.
- Cùng cơ chế lưu và khôi phục ngữ cảnh.
- Cùng quy ước tên metric, invocation ID hoặc audit ID.

ta có thể sửa ở một nơi thay vì sao chép cùng bug-fix vào nhiều aspect.

### 4.2. Một hợp đồng quan sát nhất quán

Nếu mọi nhóm nghiệp vụ đều cần:

```text
before: emit attempt
after success: emit completed
after error: emit failure
finally: release context
```

việc giữ phần dùng chung sẽ giúp tránh tình trạng một aspect quên cleanup hoặc phân loại exception khác aspect kia.

### 4.3. Cho phép phạm vi thay đổi độc lập

Một pointcut chọn Order execution có thể thay đổi mà không sửa logic audit chung. Ngược lại, quy định mask dữ liệu nhạy cảm có thể thay đổi tập trung mà không cần sửa từng pointcut.

### 4.4. Tăng khả năng kiểm thử

Có thể test **hợp đồng chung** một lần theo các tình huống nền tảng và test **mỗi concrete aspect** về matching, exclusion và tác động kết hợp.

Nhưng tái sử dụng không tự loại bỏ nhu cầu integration tests: compiler, precedence, dynamic guards, proxy bypass và build configuration vẫn có thể khác nhau giữa những concrete aspects.

## 5. Thiết kế phần biến đổi: abstract pointcut

Một **abstract pointcut** biểu thị một điểm mở rộng: base aspect biết hành vi sẽ chạy ở những join point “được chọn”, nhưng **chưa cố định tập được chọn**.

Mã giả:

```text
Base:
    abstract scope()

    before scope():
        sharedBefore()

Concrete A:
    scope() = call sites of A

Concrete B:
    scope() = executions of B
```

Cần phân biệt:

1. **Chữ ký pointcut:** tên và danh sách dữ liệu nó có thể expose/bind.
2. **Biểu thức lựa chọn cụ thể:** tập join point của một concrete aspect.
3. **Điều kiện runtime:** `args`, `target`, `this`, control flow hoặc guard nếu engine hỗ trợ.
4. **Join-point kind:** call và execution khác nhau; một base advice không làm hai loại tương đương.

### 5.1. Pointcut không phải method kiểm tra dữ liệu Java

Một abstract pointcut của AspectJ không đơn giản là:

```java
abstract boolean isSelected(Method method);
```

Đây chỉ là **một ý tưởng chiến lược Java**, có thể dùng cho proxy/interceptor thuần. Abstract pointcut của một AOP language thường tham gia vào **quá trình chọn join point** với semantics của compiler/weaver, không tương đương lời gọi hàm Java trả boolean.

### 5.2. Đừng tự mở rộng phạm vi khi hiện thực pointcut

Nếu base aspect chỉ cho phép hành vi audit tại các operation nghiệp vụ, concrete aspect không nên định nghĩa pointcut wildcard đến cả logger/audit sink nếu điều đó khiến aspect tự kích hoạt.

Một bản contract nên ghi rõ:

- Inclusions.
- Exclusions.
- Join-point kind.
- Dynamic binding.
- Weaving/proxy boundary.
- Các đường bypass và các điểm không thể advice.
- Điều kiện precedence/instantiation.

## 6. Cú pháp AspectJ: abstract aspect và abstract pointcut

**AspectJ** là ví dụ về một ngôn ngữ AOP có cú pháp *code-style aspect* và hỗ trợ aspect extension.

Ví dụ khung:

```java
public abstract aspect BaseTrace {
    protected abstract pointcut operations();

    before(): operations() {
        System.out.println("trace:before");
    }
}
```

Một aspect cụ thể:

```java
public aspect OrderTrace extends BaseTrace {
    protected pointcut operations():
        execution(* OrderService.*(..));
}
```

Hai đoạn này là **cú pháp AspectJ**, không phải Java source có thể biên dịch bằng `javac`. Compiler/weaver phải hỗ trợ file `.aj`, Java language level và bytecode version phù hợp.

Trong mô hình này:

- `BaseTrace` khai báo **cách xử lý chung**.
- `operations()` là điểm mở rộng chưa có biểu thức lựa chọn.
- `OrderTrace` cung cấp pointcut cụ thể.
- Advice từ base aspect được **kế thừa** vào concrete aspect theo quy tắc AspectJ.
- `execution(* OrderService.*(..))` là **mẫu riêng công cụ** chọn execution của những method phù hợp thuộc `OrderService`; không thể lấy mẫu này áp lên mọi engine.

### 6.1. Ngữ nghĩa liên quan từ đặc tả AspectJ

Một vài ràng buộc đáng nhớ:

- Aspect có thể **extends class** và **implements interfaces**, nhưng không được tạo với `new` như class thông thường; aspect instances do cơ chế AOP quản lý.
- Class Java thông thường **không được extends một aspect**.
- Aspect có thể extends một aspect khác **chỉ khi aspect cha là abstract** theo quy tắc code-style AspectJ.
- Fields, methods và pointcuts có thể được thừa kế; advice/declared behaviors của base được áp dụng theo semantics của engine.
- Một concrete aspect phải hoàn thiện những thành phần abstract còn thiếu.
- Aspect instantiation model được **kế thừa từ parent aspect** trong AspectJ; không mặc định mọi abstract aspect trở thành singleton.
- Thứ tự advice giữa subaspect và superaspect có những quy tắc precedence riêng.

Đây là **quy tắc công cụ**, không phải đặc tả AOP chung.

## 7. Tái sử dụng logic xử lý qua helper/hook

Ngoài abstract pointcut, một base aspect có thể khai báo **helper methods**:

```text
BaseTracing:
    abstract selected()
    before selected(): logStart()
    after selected(): logExit()

    logStart(): format trace event
    logExit(): format completion event
```

Các helper có thể:

- Chuẩn hóa tên operation.
- Mask hoặc redact thông tin nhạy cảm.
- Tạo metric labels.
- Lưu start time.
- Tạo sự kiện audit.
- Phân loại lỗi và lựa chọn mức độ log.
- Dọn context sau mỗi invocation.

### 7.1. Hook cho biến thể hợp lệ

Một base aspect có thể giữ quy trình chung nhưng cung cấp hook:

```text
formatOperationName(joinPoint)
shouldSample(joinPoint)
errorCategory(error)
recordEvent(event)
```

Một concrete aspect có thể tùy biến một số hook, với điều kiện ngôn ngữ/công cụ hỗ trợ và hook có semantics rõ ràng.

### 7.2. Không biến mọi helper thành abstract

Nếu một helper chỉ có một cách thực hiện đúng, để nó concrete. Quá nhiều abstract hooks làm base aspect trở thành một framework nhỏ khó hiểu, dễ phát sinh bug do các subclass kết hợp logic không tương thích.

Một tiêu chí tốt: chỉ tạo extension point cho **sự khác biệt có ý nghĩa**, đã xuất hiện ở nhiều consumer hoặc chắc chắn thuộc hợp đồng trừu tượng.

## 8. Template Method và Strategy là hai cách nghĩ hữu ích

**Template Method**: base class/aspect xác định thứ tự xử lý, subclass chỉ thay một vài bước.

```text
before -> validateContext -> emitStart
proceed -> business or inner advice
afterReturn/afterThrow -> emitOutcome
finally -> cleanup
```

Nếu một lớp thay đổi việc đi qua các bước này tùy ý, hợp đồng chung có thể không còn đúng.

**Strategy**: phần lựa chọn hoặc xử lý biến đổi được truyền vào qua một đối tượng chính sách thay vì inheritance.

```text
ReusableAdvice(SelectionStrategy, FormattingStrategy, Sink)
```

Ưu điểm của composition:

- Có thể cấu hình nhiều cách chọn mà không tăng số subclass.
- Test strategy riêng.
- Giảm coupling giữa phần xử lý và tập join points.
- Không phụ thuộc engine hỗ trợ abstract aspect inheritance.

**Khác biệt:** abstract aspect là một **khả năng của mô hình AOP/công cụ**; Template Method và Strategy là **mẫu thiết kế** có thể dùng để triển khai mục tiêu tương tự ngay cả khi engine không hỗ trợ syntax kế thừa aspect.

## 9. Ví dụ hoàn chỉnh với AspectJ: base advice + concrete selection

Ví dụ này gồm một class Java bình thường và hai file `.aj`. Nó minh họa **một concrete aspect thừa hưởng advice chung** và chọn method của mình.

**Tệp `Jobs.java`:**

```java
public class Jobs {
    public void run() {
        System.out.println("business:run");
    }

    public void skip() {
        System.out.println("business:skip");
    }
}
```

**Tệp `AbstractJobTrace.aj`:**

```java
public abstract aspect AbstractJobTrace {
    protected abstract pointcut selected();

    before(): selected() {
        System.out.println("trace:before:"
                + thisJoinPointStaticPart.getSignature().getName());
    }

    after() returning: selected() {
        System.out.println("trace:returned:"
                + thisJoinPointStaticPart.getSignature().getName());
    }

    after() throwing(Throwable error): selected() {
        System.out.println("trace:threw:"
                + error.getClass().getSimpleName());
    }
}
```

**Tệp `ConcreteJobTrace.aj`:**

```java
public aspect ConcreteJobTrace extends AbstractJobTrace {
    protected pointcut selected():
        execution(void Jobs.run());
}
```

**Tệp `Demo.java`:**

```java
public class Demo {
    public static void main(String[] args) {
        Jobs jobs = new Jobs();
        jobs.run();
        jobs.skip();
    }
}
```

### 9.1. Mối quan hệ giữa ba thành phần

- `Jobs` là nghiệp vụ bình thường, không biết `AbstractJobTrace` hoặc `ConcreteJobTrace`.
- `AbstractJobTrace` chứa advice chung, **không cố định** những execution được chọn.
- `ConcreteJobTrace` định nghĩa tập execution theo `selected()`.
- `Jobs.run` thuộc phạm vi và có advice.
- `Jobs.skip` không thuộc phạm vi.
- Nếu thay pointcut ở concrete aspect bằng một lựa chọn rộng hơn, cùng advice sẽ áp lên những điểm khác — không cần sửa body advice.

### 9.2. Build và chạy

**Chỉ minh họa lệnh cho môi trường đã cài compiler AspectJ phù hợp Java 17**:

```bash
ajc -17 -d out Jobs.java AbstractJobTrace.aj ConcreteJobTrace.aj Demo.java
java -cp "out:<path-to-aspectjrt.jar>" Demo
```

Trên Windows, dấu phân cách classpath là `;` thay vì `:`. `<path-to-aspectjrt.jar>` phải được thay bằng đường dẫn runtime thật. Đây **không phải** chương trình chỉ cần cài JDK là chạy được; cần `ajc` đúng phiên bản và thiết lập classpath thích hợp.

**Đầu ra theo ngữ nghĩa ví dụ khi weave đúng:**

```text
trace:before:run
business:run
trace:returned:run
business:skip
```

Không có `trace` cho `skip`, vì concrete pointcut không chọn execution đó.

### 9.3. Ranh giới của ví dụ

- Đây là **một** concrete aspect, vì vậy không có xung đột precedence do nhiều concrete aspects trong demo.
- `after() throwing` đã được định nghĩa, nhưng demo chưa tạo đường lỗi.
- Nếu method `run` ném, after-returning không chạy trên đường lỗi ấy; after-throwing có thể chạy nếu match và lỗi được truyền ra theo semantics tương ứng.
- Việc chọn execution khác chọn call site; đừng thay đổi join-point kind mà không xem kết quả mong muốn.
- Chỉ khi class/module nằm trong phạm vi build/weave thích hợp thì advice mới được áp dụng.
- Ví dụ không đưa ra cam kết mọi AspectJ version đều hỗ trợ Java 17; phải chọn toolchain tương thích.

## 10. Tại sao nên giữ pointcut trừu tượng đủ rõ?

Một abstract pointcut là **hợp đồng mở rộng**. Tên của nó phải chỉ ra đúng mục tiêu.

Tên kém rõ:

```text
abstract pointcut stuff();
abstract pointcut anything();
```

Tên tốt hơn:

```text
abstract pointcut selectedBusinessOperations();
abstract pointcut auditedMutations();
abstract pointcut measuredExecutions();
```

Nếu pointcut cần bind dữ liệu, phải thiết kế chữ ký và các ràng buộc binding rõ ràng. Không nên tự khẳng định mọi cấu hình concrete XML hoặc mọi loại pointcut runtime đều hỗ trợ expose args/state như nhau.

### 10.1. Binding và advice parameters

Một before advice có thể muốn truy cập:

- Join-point signature.
- Target hoặc receiver.
- Arguments.
- Annotation/metadata.
- Runtime context/correlation ID.

Tùy mô hình, abstract pointcut có thể được định nghĩa với tham số/binding. Những concrete pointcut phải giữ tương thích binding để advice lấy được dữ liệu đúng.

Một abstract pointcut không expose state sẽ đơn giản hơn cho nhiều hình thức cấu hình tự động. Khi cần dynamic binding phức tạp, xem cơ chế **kế thừa aspect bằng code** thay vì kỳ vọng mọi cấu hình deployment đều hiện thực được.

### 10.2. Không khiến consumer phải hiểu implementation helper

Consumer chỉ cần biết: aspect áp dụng ở đâu, thay đổi điều gì và có thể gây exception/side effect nào. Chi tiết như class `EventFormatter` hoặc một private helper nên được tách thành tài liệu triển khai.

## 11. Ví dụ Java 17 độc lập: trừu tượng hóa interceptor tương đương về thiết kế

Java 17 thuần **không có cú pháp abstract aspect / abstract pointcut weaving tích hợp**. Ví dụ sau dùng **abstract class + wrapper** để minh họa **tách hành vi chung khỏi phạm vi được chọn**.

Các yêu cầu kiểm chứng:

1. Hai concrete policies cùng kế thừa một xử lý enter/return/throw/finally.
2. Policy ngoài áp cho **mọi action**.
3. Policy trong chỉ áp cho action bắt đầu bằng `A` hoặc đúng `FAIL`.
4. Advice của lớp ngoài và trong có thứ tự lồng nhau rõ ràng.
5. Một action không khớp policy trong vẫn được policy ngoài quan sát.
6. Một exception lan qua hai policy, finally được thực thi đúng.
7. Caller gọi raw target trực tiếp thì **không đi qua wrapper**, làm rõ giới hạn proxy/adapter.

Tạo file `AbstractPolicyDemo.java`:

```java
public class AbstractPolicyDemo {
    @FunctionalInterface
    interface Work {
        String run(String action);
    }

    abstract static class BasePolicy {
        private final String id;

        BasePolicy(String id) {
            this.id = id;
        }

        protected abstract boolean matches(String action);

        protected void before(String action) {
            System.out.println(id + ":before:" + action);
        }

        protected void afterReturning(String action, String result) {
            System.out.println(id + ":return:" + action + ":" + result);
        }

        protected void afterThrowing(
                String action, RuntimeException error) {
            System.out.println(id + ":throw:" + action + ":"
                    + error.getClass().getSimpleName());
        }

        protected void afterFinally(String action) {
            System.out.println(id + ":finally:" + action);
        }

        final Work wrap(Work next) {
            return action -> {
                if (!matches(action)) {
                    return next.run(action);
                }

                before(action);
                try {
                    String value = next.run(action);
                    afterReturning(action, value);
                    return value;
                } catch (RuntimeException error) {
                    afterThrowing(action, error);
                    throw error;
                } finally {
                    afterFinally(action);
                }
            };
        }
    }

    static final class AllActions extends BasePolicy {
        AllActions() {
            super("all");
        }

        @Override
        protected boolean matches(String action) {
            return true;
        }
    }

    static final class SelectedActions extends BasePolicy {
        SelectedActions() {
            super("selected");
        }

        @Override
        protected boolean matches(String action) {
            return action.startsWith("A") || "FAIL".equals(action);
        }
    }

    static void call(String label, Work work, String action) {
        System.out.println("case:" + label);
        try {
            System.out.println("caller:" + work.run(action));
        } catch (RuntimeException error) {
            System.out.println("caller:error:"
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
    }

    public static void main(String[] args) {
        Work target = action -> {
            System.out.println("business:" + action);
            if ("FAIL".equals(action)) {
                throw new IllegalStateException("operation failed");
            }
            return "done:" + action;
        };

        Work managed = new AllActions().wrap(
                new SelectedActions().wrap(target));

        call("both", managed, "A1");
        call("outer-only", managed, "skip");
        call("error", managed, "FAIL");
        call("direct-target", target, "A2");
    }
}
```

Chạy với JDK 17 hoặc mới hơn:

```bash
javac AbstractPolicyDemo.java
java AbstractPolicyDemo
```

**Stdout xác định:**

```text
case:both
all:before:A1
selected:before:A1
business:A1
selected:return:A1:done:A1
selected:finally:A1
all:return:A1:done:A1
all:finally:A1
caller:done:A1
case:outer-only
all:before:skip
business:skip
all:return:skip:done:skip
all:finally:skip
caller:done:skip
case:error
all:before:FAIL
selected:before:FAIL
business:FAIL
selected:throw:FAIL:IllegalStateException
selected:finally:FAIL
all:throw:FAIL:IllegalStateException
all:finally:FAIL
caller:error:IllegalStateException:operation failed
case:direct-target
business:A2
caller:done:A2
```

### 11.1. Điều gì được tái sử dụng?

`BasePolicy.wrap` hiện thực chung chu trình:

```text
matches?
  no -> next.run()
  yes ->
      before()
      try next.run()
      on success: afterReturning()
      on RuntimeException: afterThrowing(), rethrow
      finally: afterFinally()
```

`AllActions` và `SelectedActions` chỉ xác định **chiến lược lựa chọn** khác nhau. Nếu thay đổi format sự kiện hoặc cách dọn context chung trong `BasePolicy`, cả hai sẽ dùng hành vi đó mà không phải copy mã.

### 11.2. Vì sao hai policy có thể chạy cùng một operation?

`A1` khớp cả `AllActions` lẫn `SelectedActions`. Hai wrapper là **hai instance độc lập** trong chuỗi; vì vậy caller thấy hai cặp trước/sau.

Tái sử dụng base code **không loại bỏ hiện tượng double instrumentation** khi selection sets chồng lấn.

### 11.3. Vì sao after của policy trong xuất hiện trước?

`AllActions` là wrapper ngoài, `SelectedActions` là wrapper trong:

```text
AllActions(
    SelectedActions(
        Business
    )
)
```

Trên đường về, phần trong hoàn tất trước phần ngoài. Đây là **thứ tự đã được định nghĩa bằng lồng wrapper Java**. Nó minh họa logic tổng quát nhưng không phải tuyên bố thứ tự của mọi compiler AOP.

### 11.4. Vì sao lỗi có hai sự kiện throw?

`FAIL` khiến business ném `IllegalStateException`. Policy trong ghi lỗi rồi ném lại; policy ngoài bắt lỗi do continuation bên trong ném và cũng ghi lỗi. Finally mỗi tầng được thực thi.

Nếu log cả hai sự kiện, cần phân biệt **hai lần quan sát cùng một exception** với **hai exception độc lập**. Trace ID và span context giúp tránh đếm sai.

### 11.5. Vì sao direct target không có advice?

`call("direct-target", target, "A2")` gọi `Work` target trực tiếp, không đi qua wrapper. Đây là **giới hạn của cách triển khai dựa trên wrapping**. Trong một cơ chế weaving trực tiếp vào method execution, phạm vi áp dụng có thể khác.

### 11.6. Chương trình không phải một AspectJ abstract aspect

Đây là Java 17 thuần, nên:

- Không có pointcut DSL.
- Không tự động nhận join point.
- Không có bytecode weaving.
- Caller phải dùng `managed` reference.
- `BasePolicy` chỉ là **thiết kế trừu tượng hóa interceptor**, không phải `abstract aspect` theo ngữ nghĩa công cụ.
- Giới hạn bắt `RuntimeException` thuộc lựa chọn ví dụ, không phải đặc tả mọi loại after-throwing advice.

## 12. Abstract pointcut và concrete pointcut: chú ý loại join point

Một abstract aspect có thể định nghĩa advice chung:

```text
before selectedOperation():
    emitStart()
```

Nhưng concrete aspects chọn:

```text
A: execution of method
B: call to method
```

Với công cụ phân biệt `call` và `execution`, hai lựa chọn quan sát ở **vị trí khác nhau**:

- **Call join point:** tại chỗ gọi, có thể phụ thuộc call site được weave.
- **Execution join point:** tại phần thực thi thân method, có thể bao cả direct invocations sau khi class được weave.

Điều này ảnh hưởng:

- Receiver/target.
- Runtime binding.
- Exceptions.
- Overhead.
- Phạm vi khi code ở library/JDK.
- Việc một lời gọi nội bộ `this.inner()` được can thiệp hay không.
- Khả năng coverage khi chỉ một số module được weave.

**Đừng gộp mọi concrete pointcut chỉ vì chúng cùng implement tên `selected()`.** Một base advice có thể cần những giả định tối thiểu về join-point kind để hoạt động đúng với tất cả.

## 13. Reuse và aspect lifecycle

Trong các hệ thống AOP có aspect instances, một base aspect có thể có fields dùng để lưu state. Nhưng state ấy có ngữ nghĩa theo **aspect instantiation model**.

Những mô hình phổ biến trong AOP nâng cao có thể là:

- Một aspect instance dùng chung.
- Một instance gắn với target object.
- Một instance theo một đối tượng ở call site/receiver.
- Một instance theo control-flow scope.
- Một instance theo một request hoặc một vùng ngữ cảnh do engine cung cấp.

**Không có mặc định chung cho mọi công cụ.**

Trong AspectJ, các mô hình như `issingleton()`, `perthis(...)`, `pertarget(...)`, `percflow(...)` hoặc `percflowbelow(...)` được đặc tả. Theo tài liệu AspectJ, tiêu chí instantiation của concrete aspect được **thừa kế từ parent aspect**. Đây là chi tiết quan trọng khi thiết kế base aspect có mutable state.

### 13.1. Cấm hiểu sai “base aspect có một field chung”

Một field ở abstract aspect có thể trở thành state của **mỗi concrete aspect instance** theo lifecycle của engine; không thể khẳng định đó luôn là field chung cho toàn chương trình.

Một `static` field trong mã lại có semantics khác. Điều này đặc biệt ảnh hưởng counters, caches, correlation IDs và mutable policy config.

### 13.2. Không để trạng thái invocation vào field singleton chung

Sai lầm phổ biến:

```text
baseAspect.startTime = now()

outer enters: startTime=T0
inner enters: startTime=T1
outer exits: duration=now()-T1 (sai)
```

Ngay cả khi chỉ một thread, nested invocation có thể ghi đè mốc. Cần dùng local frame per advice invocation, một stack đúng ownership, hoặc context tương ứng.

### 13.3. Thread safety và reentrancy

Nhiều concrete aspects có thể tồn tại đồng thời; một concrete aspect có thể phục vụ nhiều thread. State mutable phải được quản lý theo contract:

- Invocation-local variables.
- Immutable metadata.
- Thread-local có cleanup nếu phù hợp.
- Context propagation khi async.
- Synchronization/atomic state cho counters dùng chung.
- Tránh gọi logger/metric client cũng nằm trong pointcut của chính mình.

Kế thừa code không tự đem lại thread safety.

## 14. Aspect extension trong AspectJ: quy tắc quan trọng

Tài liệu AspectJ mô tả:

1. **Aspects may extend classes** và implement interfaces, nhưng vẫn có ràng buộc aspect instantiation.
2. **Classes may not extend aspects.**
3. **Aspects extending aspects:** aspect có thể kế thừa abstract aspect; concrete aspect không thể được dùng làm superaspect theo mô hình code-style thông thường.
4. Fields, methods, pointcuts (và advice theo ngữ nghĩa inheritance) có thể được dùng lại.
5. Một concrete aspect cần hoàn thành những abstract declarations còn lại.
6. Việc advice được áp dụng gắn với **concrete aspect instance và điểm được match**, không chỉ tồn tại khai báo base.

### 14.1. Không xây cây kế thừa sâu không cần thiết

```text
BaseAspect
   |
PolicyAspect
   |
TracingAspect
   |
OrderTracingAspect
```

Càng nhiều tầng, càng khó biết pointcut đã thay đổi ở đâu, method hook được override ở tầng nào, và advice đến từ aspect nào. Một cây kế thừa dài làm tăng cognitive load và nguy cơ đổi behavior không chủ ý.

Ưu tiên:

- Một hoặc vài base aspects thật sự ổn định.
- Interface hoặc composition cho policy phụ trợ.
- Concrete aspect mỏng, có pointcut rõ.
- Integration test cho behavior tổng hợp.

## 15. “Inherited advice” không có nghĩa chỉ một lần advice

Giả sử base aspect:

```text
AbstractAudit:
    before selection: emitEvent()
```

Hai concrete aspects cùng mở rộng base:

```text
ConcreteA -> selection of operation X
ConcreteB -> selection of operation X
```

Nếu cả hai đều được weave/activated, operation X có thể nhận **hai lần advice**. Đây không phải lỗi kế thừa của ngôn ngữ; đó là kết quả **hai concrete aspects cùng match**.

Để tránh double-count:

- Kiểm tra tập pointcut A và B có giao nhau không.
- Nếu có giao nhau có chủ đích, định nghĩa ý nghĩa hai event và tên aspect nguồn.
- Nếu không có chủ đích, thu hẹp scope/exclusions.
- Gắn invocation/correlation ID và aspect ID khi ghi log.
- Không dùng một cờ global đơn giản bỏ mọi nested advice để che duplicate.
- Có test đúng số lượng trước/sau/throw events.

### 15.1. Một base aspect có thể tái sử dụng nhưng vẫn cần policy riêng

Audit event được dùng chung về format không đồng nghĩa cùng một số lượng event hoặc cùng retention level. Hành vi giống nhau về **mẫu xử lý** vẫn có thể khác hợp đồng về **phạm vi**, **bảo mật** và **side effects**.

## 16. Precedence: thứ tự giữa concrete aspects và các advice

Khi nhiều concrete aspects cùng áp dụng ở một join point, cần biết **precedence**.

Trong AspectJ, tài liệu định nghĩa:

- Có thể khai báo quan hệ precedence bằng `declare precedence`.
- Nếu subaspect và superaspect cùng ảnh hưởng một join point, subaspect có precedence cao hơn superaspect theo quy tắc mặc định liên quan.
- Các concrete aspects độc lập không có precedence quan hệ rõ ràng có thể có thứ tự **không được bảo đảm**.
- Một around advice precedence cao hơn có thể quyết định có `proceed` xuống advice thấp hơn không.

**Không viết code dựa trên tên class/alphabetical/source order** trừ khi engine chính thức bảo đảm.

### 16.1. Before và after có thể đảo thứ tự quan sát

Với mô hình:

```text
Outer(
    Inner(
        Business
    )
)
```

Thứ tự:

```text
Outer.before
Inner.before
Business
Inner.after
Outer.after
```

Đây là đặc tính của **lớp bao**. Không có nghĩa “aspect ưu tiên cao hơn thì tất cả các câu lệnh của nó đều xuất hiện sớm hơn”.

### 16.2. Conrete aspect trong `declare precedence`

Ví dụ cú pháp AspectJ:

```java
declare precedence: SecurityAudit, OrderTrace, *;
```

Đây là fragment ngôn ngữ công cụ, không phải Java thuần. Quan hệ precedence có ý nghĩa trên các concrete aspects được chọn; khai báo chỉ nêu tên abstract aspects có thể không áp đúng như mong đợi với những concrete aspects phát sinh.

Đặc tả AspectJ còn hỗ trợ type patterns có hậu tố `+` khi muốn chọn subtype/descendant aspect theo phạm vi được cho phép.

## 17. Hành vi chung trên đường lỗi

Để dùng chung logic một cách đúng đắn, cần phân biệt:

- **Before advice:** có thể chặn target nếu ném lỗi trước khi tiếp tục.
- **After returning advice:** chỉ áp dụng khi phần được quan sát kết thúc bình thường.
- **After throwing advice:** áp dụng khi phần được quan sát kết thúc bằng exception phù hợp.
- **Finally/after advice:** được thực hiện trong phạm vi mà công cụ xác định.
- **Around advice:** có thể bắt lỗi, đổi lỗi, trả fallback hoặc gọi lại continuation nếu ngôn ngữ cho phép.

Một base aspect chỉ ghi lỗi trong `after throwing` không tự bảo đảm business state được rollback. Nếu after logging tự ném lỗi, caller có thể nhận lỗi mới chứ không còn thấy lỗi gốc.

### 17.1. Khi kết hợp aspect dùng chung với retry

```text
RetryAround(
    SharedAuditAdvice(
        Business
    )
)
```

Với nhiều attempts, audit bên trong có thể chạy nhiều lần. Nếu đảo:

```text
SharedAuditAdvice(
    RetryAround(
        Business
    )
)
```

audit bên ngoài có thể chỉ quan sát outcome tổng thể của một logical request. Hai thứ tự phục vụ hợp đồng khác nhau.

**Không cho rằng dùng chung base aspect tự động thống nhất semantics retry.** Contract cần phân biệt request count, attempt count và actual business execution count.

## 18. Dùng chung advice quanh method trả bất đồng bộ

Với Java `CompletableFuture<Result>`:

```text
Caller -> advised method -> returns Future
                       -> task completes later
```

Nếu base aspect dùng after-returning tại method execution, nó có thể chỉ ghi **method returned Future**, không phải **logical task succeeded**.

Một concrete aspect chọn async method cần quyết định:

- Ghi sự kiện khi method trả Future hay khi Future hoàn tất?
- Có xử lý exceptional completion không?
- Correlation ID có được truyền qua executor không?
- Khi cancellation/timeout, có ghi khác success/failure không?
- Một completion callback của chính aspect có gây reentrancy hoặc lỗi làm đổi outcome không?

Một base aspect giả định mọi method đều synchronous sẽ **không tự đúng** khi subclass concrete chọn điểm async. Có thể cần base strategy riêng hoặc hợp đồng chung rõ về completion boundary.

## 19. Tái sử dụng với interface, helper class và composition

Có ít nhất bốn cấp tái sử dụng:

| Cấp | Cách làm | Tính di động |
| --- | --- | --- |
| Common utility | Helper định dạng event, mask data, error mapping | Thường dùng được với Java thường |
| Template interceptor | Abstract Java class giữ order before/after/finally | Dùng khi có wrapper/interceptor |
| Strategy/composition | Truyền matcher, sink, error translator | Ít ràng buộc kế thừa, dễ test |
| Abstract aspect | Aspect base + abstract pointcut/inherited advice | Phụ thuộc AOP language/compiler hỗ trợ |

Đôi khi giải pháp tốt nhất là **kết hợp**:

- Base aspect chỉ phụ trách pointcut và advice boundary.
- Business-independent formatting nằm ở utility/strategy.
- Concrete aspect chỉ quyết định scope.
- Những ràng buộc bảo mật/giao dịch được bảo vệ ở boundary đáng tin.

Tránh đặt mọi thứ vào một base aspect khổng lồ; nó dễ thành “god aspect” áp chính sách trái ngược cho nhiều miền nghiệp vụ.

## 20. Tránh tự can thiệp khi dùng chung helper

Một shared logging helper có thể vô tình thuộc pointcut của chính concrete aspect. Ví dụ:

```text
ConcreteOrderTrace.before
  -> BaseTrace.logStart
      -> Logger.write
          -> ConcreteOrderTrace.before
              -> ...
```

Các phương án:

1. **Chọn pointcut hẹp** theo operation thật sự cần can thiệp.
2. Loại helper/logger/sink theo loại join point và type phù hợp.
3. Dùng lexical exclusion khi công cụ hỗ trợ, nhưng hiểu khác biệt call/execution.
4. Dùng dynamic control-flow guard nếu helper cũng cần được advice trong ngữ cảnh khác.
5. Đặt guard phạm vi ngắn quanh emission, cleanup bằng `finally`.
6. Kiểm thử nested business calls vẫn được advice, không loại quá rộng.

Một base aspect dùng chung mà tự gọi logger rộng có thể lan lỗi recursion tới **tất cả concrete aspects**, làm lỗi nghiêm trọng hơn so với bản sao nhỏ ở một điểm.

## 21. Hợp đồng trừu tượng và nguyên tắc thay thế

Base aspect nên công bố:

- Preconditions của hook implementations.
- Những side effects base sẽ luôn thực hiện.
- Quy tắc masking/tracing.
- Guarantee của return/exception.
- Quy tắc giữ cause/suppressed.
- Tối đa bao nhiêu lần gọi continuation.
- Yêu cầu thread safety và reentrancy.
- Các join-point kinds được cho phép.
- Những operation **không được** chọn vì sẽ tạo vòng hoặc bypass security.

Nếu concrete aspect override helper mà ném một exception mới hoặc bỏ cleanup, nó có thể làm **hợp đồng base mất hiệu lực**.

### 21.1. Không siết precondition âm thầm

Ví dụ một base validator cho phép mọi request có ID hợp lệ, nhưng concrete aspect đổi hook để từ chối thêm một loại request hợp lệ mà không cập nhật public API contract. Đây có thể là breaking behavior change.

### 21.2. Không làm yếu postcondition của common advice

Nếu base logging promise “không thay return/exception target”, concrete hook không nên bỏ exception gốc và trả false success. Nếu cần biến đổi behavior, nên tách thành **một chính sách khác được mô tả công khai**.

### 21.3. Không dùng abstract aspect để che dependency ẩn

Một concrete aspect không nên phải đoán rằng base secretly đọc một thread-local, sử dụng service locator hoặc gọi network. Dependencies chung phải được tài liệu hóa và kiểm thử failure mode.

## 22. Phiên bản hóa base aspect: tác động dây chuyền

Một thay đổi ở base aspect có thể ảnh hưởng mọi concrete aspect đang dùng nó.

Ví dụ:

- Đổi tên hoặc signature abstract pointcut.
- Đổi hook từ return void thành trả giá trị khác.
- Thêm mandatory abstract hook.
- Đổi error translation.
- Bổ sung một advice mới trong base.
- Đổi order giữa before/after/around.
- Đổi default sampling.
- Đổi aspect instantiation model hoặc state ownership.
- Nới rộng base pointcut làm nhiều join points cùng match.
- Đổi visibility helper được concrete aspects gọi.

### 22.1. Source compatibility khác behavior compatibility

Một concrete aspect vẫn biên dịch sau khi base được sửa nhưng logging có thể tăng gấp đôi hoặc loại exception công khai thay đổi. Regression tests phải xét **event order**, **target call count**, **return/throw** và **state** chứ không chỉ compiler báo pass.

### 22.2. Cấu hình weaving là một phần artifact

Nếu consumer runtime nạp class chưa weave hoặc chỉ một số aspect được kích hoạt, behavior có thể khác build/test. Khi phát hành shared aspect module, cần ghi rõ:

- Compiler/weaver version tương thích.
- Java language/bytecode target.
- Danh sách concrete aspects mong đợi.
- Quy tắc precedence.
- Artifact/source weaving scope.
- Module/classloader assumptions.
- Release notes cho breaking pointcut changes.

## 23. Khi nào không nên dùng abstract aspect?

Không phải mọi khác biệt nhỏ đều cần inheritance.

**Không nên ưu tiên abstract aspect nếu:**

- Chỉ có một aspect cụ thể và chưa có nhu cầu reuse rõ.
- Chỉ muốn chia sẻ vài utility methods.
- Engine đang dùng chỉ hỗ trợ proxy/interceptor, không hỗ trợ aspect inheritance.
- Các concrete aspects có error semantics trái ngược.
- Các nhóm nghiệp vụ có lifecycle hoặc completion boundary khác nhau.
- Một base pointcut buộc tất cả concrete aspects dùng cùng join-point kind dù không phù hợp.
- Nhu cầu cần thay đổi policy lúc runtime thường xuyên; strategy có thể linh hoạt hơn.
- Base aspect phải chứa quá nhiều type checks/branching theo tên concrete aspect.
- Một chuỗi kế thừa sâu làm khó hiểu thứ tự advice.
- Tái sử dụng làm các module phụ thuộc chặt vào compiler/weaver đặc thù ngoài chủ đích.

**Nguyên tắc:** trừu tượng hóa phần **ổn định về mặt ý nghĩa**, không chỉ dòng code giống nhau ngẫu nhiên.

## 24. Các mẫu thiết kế hay dùng

### 24.1. Template Advice

Base cung cấp advice chung, concrete aspect cung cấp selection và một vài hooks.

Ưu điểm: nhất quán quy trình. Nhược điểm: coupling giữa base và subclasses.

### 24.2. Policy Strategy

Base logic gọi object policy:

```text
ReusableInterceptor(
   Matcher,
   EventSink,
   ErrorPolicy,
   SamplingPolicy
)
```

Ưu điểm: dễ unit-test/tùy biến. Nhược điểm: phải quản lý object/configuration và lựa chọn điểm intercept riêng.

### 24.3. Role-specific mixin + advice

Nếu công cụ hỗ trợ **inter-type declaration**, có thể bổ sung role/state cho target và dùng advice để cập nhật tại join points. Đây là bài toán **structural crosscutting** khác với abstract aspect; có thể kết hợp nhưng không đồng nhất.

### 24.4. Marker/annotation-driven selection

Concrete aspect hoặc strategy có thể chọn operation dựa trên annotation/metadata. Phải hiểu:

- Annotation retention/visibility.
- Nơi annotation được kế thừa hoặc không.
- Method bridge/generics.
- Annotation trên interface khác annotation trên implementation.
- Runtime proxy/weaver chọn method nào.

Đừng nghĩ một marker annotation là bảo đảm mọi engine đều sẽ áp advice.

## 25. Kiểm thử base aspect và concrete aspects theo lớp

Một chiến lược test tốt gồm ba mức.

**Mức 1 — Shared logic unit tests:**

- Helper format/mask.
- Error classification.
- Lifecycle của context.
- Guard reset sau exception.
- Idempotency nếu có.

**Mức 2 — Concrete scope tests:**

- Pointcut chọn đúng positive cases.
- Pointcut loại đúng negative cases.
- Không match logger/audit sink vô tình.
- Call vs execution đúng với use case.
- Dynamic conditions hoạt động khi arguments thay đổi.

**Mức 3 — Integration/weaving tests:**

- Advice base thực sự áp dụng qua concrete aspect.
- Compiler/weaver và runtime setup đúng.
- Hai concrete aspects cùng match có được phát hiện.
- Return/error precedence và exception wrapping đúng.
- Direct target/proxy bypass được kiểm tra nếu sử dụng wrapping.
- Thread, nested invocation, async completion đúng contract.

### 25.1. Test được tái sử dụng không thay thế test theo phạm vi

Một base aspect có thể đạt 100% branch coverage trong unit tests nhưng concrete pointcut `execution(* *(..))` áp quá rộng. Scope là **một phần của correctness**; mỗi concrete aspect cần chứng cứ riêng.

### 25.2. Negative tests đặc biệt quan trọng

Không chỉ assert “operation A có log”. Hãy assert:

- Operation không thuộc scope **không có log**.
- Logger/helper không tự bị aspect xử lý.
- Target không bị gọi khi security precondition từ chối.
- Một method có annotation tương tự nhưng sai type không bị chọn.
- Khi nhiều aspects cùng match, số lần advice đúng chính sách.

## 26. Ma trận kiểm thử đề xuất

| Ca | Kỳ vọng |
| --- | --- |
| Base aspect trừu tượng chưa có concrete aspect | Không tự giả định advice được triển khai |
| Concrete pointcut chọn một method | Chỉ method đó nhận inherited advice |
| Concrete pointcut không chọn method khác | Method kia không nhận inherited advice |
| Hai concrete aspects chọn cùng method | Phát hiện overlap và số lần advice |
| Before advice chung | Chạy ở pha đúng cho các join points được chọn |
| After returning chung | Chỉ quan sát normal completion thuộc scope |
| After throwing chung | Quan sát exception được chọn theo type binding |
| Around advice chung | Proceed/short-circuit đúng contract |
| Concrete hook tùy biến | Không phá return/error/cleanup guarantee |
| Helper chung ném lỗi | Chính sách best-effort/fail-closed rõ |
| Reentrant/nested invocation | Context từng invocation độc lập |
| Multi-thread | Không trộn mutable state qua requests |
| Retry | Phân biệt operation logic và từng attempt |
| Async Future | Không nhầm return Future với completion |
| Pointcut trùng logger | Không sinh self-interception vô hạn |
| Missing class weaving | Phát hiện cấu hình thiếu, không silently pass |
| Incorrect compiler version | Build báo lỗi rõ ràng |
| Precedence đổi | Event order và behavior đúng tài liệu |
| Base upgrade | Regression tests của mọi concrete aspect |
| Raw target bypass proxy | Nếu cơ chế wrapping, test đúng giới hạn |
| Null/invalid args | Các điều kiện trước được áp tại ranh giới đúng |

### 26.1. Event collector tốt hơn chỉ xem stdout

Trong bài minh họa, stdout giúp hiểu thứ tự. Trong test thực, thu event có cấu trúc:

```text
aspectId
joinPointId
invocationId
parentInvocationId
phase
outcome
attemptId
```

rồi so khớp số lần và thứ tự. Điều này hữu ích với nested calls và nhiều aspects, đồng thời tránh dùng timestamp đơn thuần làm bằng chứng một total order trong hệ thống async.

## 27. Gỡ lỗi khi inherited advice không chạy

Hãy đi theo thứ tự:

1. Có concrete aspect nào **thực sự được kích hoạt** không?
2. Nó có kế thừa đúng base abstract aspect không?
3. Nó đã hiện thực toàn bộ abstract pointcuts/methods chưa?
4. Pointcut cụ thể có chọn đúng loại join point không?
5. Target/caller code có nằm trong **phạm vi weaving** không?
6. Nếu dùng proxy, caller có giữ proxy reference không?
7. Dynamic guard/binding có đúng không?
8. Advice ưu tiên cao hơn có short-circuit trước khi xuống chain không?
9. Có helper/pointcut exclusions loại nhầm method không?
10. Có nhiều aspect cùng match rồi log bị ghi đè/lọc không?
11. Aspect instance có được tạo đúng lifecycle không?
12. Build/deployment có nạp đúng binary/configuration không?

Đừng giải quyết chỉ bằng cách đổi pointcut thành wildcard rộng. Nó có thể làm advice chạy được trên một ca test nhưng gây recursion và can thiệp nhầm ở production.

## 28. Gỡ lỗi khi advice chạy nhiều lần

Những nguyên nhân thường gặp:

- Hai concrete aspects kế thừa cùng base và scope giao nhau.
- Một operation vừa được chọn ở method call vừa ở method execution.
- Around advice có retry `proceed` nhiều lần.
- Nested business methods đều được advice.
- Logging helper lại thuộc pointcut.
- Caller đi qua hai tầng proxy/wrapper.
- Một test/app đã kích hoạt cùng aspect bằng nhiều cơ chế weaving/configuration.
- Cấu hình quan sát ghi trùng event dù target chỉ chạy một lần.

Để phân biệt, thu:

- Concrete aspect ID.
- Join-point kind và signature.
- Receiver/target identity nếu được phép.
- Invocation/attempt ID.
- Business execution counter.
- Advice enter/exit counter.
- Event sink acknowledgement (nếu có).

**Số dòng log nhiều hơn không luôn chứng minh business chạy nhiều lần.** Tương tự, một lần business execution có thể nhận nhiều lời advice hợp lệ.

## 29. Những hiểu nhầm thường gặp

**“Abstract aspect là interface Java đổi tên.”** Không. Nó có thể chứa advice, pointcut, lifecycle và semantics weaving riêng.

**“Chỉ cần khai báo abstract aspect là advice chạy.”** Không theo mô hình AspectJ code-style thông thường; cần concrete aspect và weaving phù hợp.

**“Mọi AOP engine đều hỗ trợ abstract aspect.”** Không. Một số chỉ có interceptor chain hoặc decorators.

**“Abstract pointcut là Java method boolean.”** Không trong AspectJ; đó là một declaration được compiler/weaver diễn giải để chọn join points.

**“Kế thừa code chung bảo đảm mỗi operation chỉ bị advice một lần.”** Sai nếu nhiều concrete aspects match hoặc nhiều wrapper được lồng nhau.

**“Concrete aspect có thể luôn extends bất kỳ concrete aspect.”** Không trong mô hình AspectJ code-style được đặc tả; aspect-to-aspect extension yêu cầu parent abstract.

**“Method helper chung là thread-safe vì nằm trong base.”** Không. Mutable state và lifecycle quyết định thread safety.

**“Một field trong base abstract aspect luôn shared singleton.”** Không thể khẳng định; tùy instantiation model và static/instance field.

**“Subaspect precedence cao hơn nghĩa mọi after đều chạy trước.”** Không; nesting và advice kinds quyết định thứ tự vào/ra.

**“Một concrete pointcut là call, concrete khác là execution vẫn có cùng coverage.”** Không; đây là hai loại join point khác nhau.

**“Một advice trả thành công sau khi Future được tạo nghĩa tác vụ async đã thành công.”** Sai; method-return khác task completion.

**“Tái sử dụng là gom mọi policy vào một base aspect.”** Không. Một base có quá nhiều behavior làm coupling cao và khó kiểm thử.

**“Dùng abstract class wrapper Java là làm abstract aspect thật.”** Không: Java wrapper cần reference đi qua nó, không tự weave join points.

**“Đổi base hook signature không ảnh hưởng consumer nếu API nghiệp vụ giữ nguyên.”** Sai; concrete aspects có thể không compile hoặc thay behavior.

**“Không có log nghĩa business không chạy.”** Sai khi không match, bypass, short-circuit hoặc logger lỗi.

**“Một aspect chung về logging đủ làm security boundary.”** Không nếu caller còn đường bypass hoặc policy có thể bị vô hiệu hóa; cần bảo vệ bất biến ở ranh giới tin cậy.

## 30. Bài tập tự luyện

### Bài 1 — Phân tách chung và riêng

Order và Payment cùng cần log start/end, nhưng chọn method bằng biểu thức khác nhau. Nên đặt phần nào ở base, phần nào ở concrete aspect?

**Gợi ý đáp án:** Base chứa advice/hook chung; concrete aspect định nghĩa pointcut cho từng miền, thêm policy riêng nếu thực sự cần.

### Bài 2 — Abstract pointcut

Một base aspect có `abstract pointcut selected()` nhưng chưa có concrete aspect. Có thể khẳng định mọi business method đã được advice không?

**Gợi ý:** Không. Phải có concrete implementation, build/weaving và pointcut matching phù hợp.

### Bài 3 — Kế thừa aspect

Trong AspectJ code-style, một concrete aspect muốn extends một concrete aspect khác. Điều gì cần kiểm tra?

**Gợi ý:** Theo tài liệu, aspect có thể extends **abstract aspect**, không dùng concrete aspect làm superaspect bình thường; nên refactor shared logic vào abstract base hoặc composition.

### Bài 4 — Điểm chọn chồng lấn

Hai concrete aspects kế thừa cùng base, cả hai chọn `execution(* Order.submit(..))`. Có thể nhận một hay nhiều sự kiện before?

**Gợi ý:** Có thể nhận nhiều advice, mỗi concrete aspect có một ứng dụng riêng. Kiểm tra precedence và số lần chính xác.

### Bài 5 — State trong singleton aspect

Base giữ `long startedAt`; outer method được advice rồi gọi inner method cũng được advice. Tại sao duration outer có thể sai?

**Gợi ý:** Inner ghi đè state của outer. Dùng local frame per invocation hoặc context phù hợp.

### Bài 6 — Policy giới hạn lỗi

Base audit giữ nguyên exception gốc; concrete hook lại ném lỗi sink mà không giữ `cause`. Có còn đúng hợp đồng base không?

**Gợi ý:** Không nếu đã cam kết transparent error propagation. Phải cô lập lỗi phụ trợ hoặc công bố thay đổi failure policy.

### Bài 7 — Async

Một concrete aspect chọn method trả `CompletableFuture` nhưng inherited after-returning advice ghi “thành công” ngay khi method trả. Điều này đúng không?

**Gợi ý:** Chỉ đúng nếu “thành công” có nghĩa **trả Future thành công**. Nếu muốn task success, phải quan sát completion riêng.

### Bài 8 — Không có abstract aspect trong công cụ đang dùng

Bạn có interface proxy và một base Java abstract interceptor; có đạt mục tiêu tái sử dụng không?

**Gợi ý:** Có thể tái sử dụng logic thiết kế, nhưng không gọi đó là language-level abstract aspect hay tự giả định mọi method được weave.

### Bài 9 — Override pointcut rộng quá

Concrete aspect định nghĩa pointcut bao toàn bộ `execution(* *(..))` và inherited logging advice gọi logger bị chọn. Nguy cơ?

**Gợi ý:** Self-interception/recursion, overhead và can thiệp nhầm; cần scope/exclusions phù hợp.

### Bài 10 — Release base aspect

Một bản cập nhật base thêm after-throwing advice. API business không đổi. Có cần regression tests không?

**Gợi ý:** Có. Exception propagation, event count, performance và behavior của mọi concrete aspects có thể thay đổi.

## 31. Checklist thiết kế và triển khai

- [ ] Có ít nhất hai use cases thật sự chia sẻ logic/hợp đồng.
- [ ] Đã tách phần dùng chung và phần biến thiên có ý nghĩa.
- [ ] Engine thực sự hỗ trợ abstract aspect hay cần pattern tương đương.
- [ ] Các abstract pointcuts có tên và contract rõ.
- [ ] Concrete aspect hiện thực chính xác join-point kinds và scope.
- [ ] Có exclusions tránh logger/metric/audit sink recursion.
- [ ] Không chọn wildcard toàn chương trình ngoài chủ đích.
- [ ] Đã kiểm tra binding của tham số và dynamic conditions.
- [ ] Đã kiểm tra visibility/access rules của aspect language.
- [ ] Hiểu lifecycle/instantiation model của base và concrete aspects.
- [ ] Không dùng mutable singleton field làm state per invocation.
- [ ] Hooks giữ contract về return/exception/cleanup.
- [ ] Không cho concrete aspects phá pre/postconditions của public API ngoài chủ đích.
- [ ] Phân biệt direct call/proxy bypass và weaving coverage.
- [ ] Có policy precedence khi nhiều aspects match.
- [ ] Có test overlap của concrete selections.
- [ ] Có test before, normal return, throwing, finally và short-circuit.
- [ ] Có test nested/reentrant operations và cleanup sau lỗi.
- [ ] Có test retry, số lần target chạy và side effects.
- [ ] Có test async completion nếu concrete scope có Future/task.
- [ ] Có test concurrency nếu aspect giữ state.
- [ ] Có error taxonomy và quy tắc cause/suppressed nếu lỗi phụ trợ ném.
- [ ] Có unit tests của helper/strategy chung.
- [ ] Có compile/weave integration tests cho concrete aspects.
- [ ] Có source/binary/deployment compatibility tests khi nâng cấp base.
- [ ] Không đặt mọi dependency/hành vi vào một “god aspect”.
- [ ] Đã công bố cấu hình, target operations và known limitations cho consumer.
- [ ] Có release notes cho những thay đổi scope/hook/precedence.
- [ ] Không dựa vào tên class hoặc source order thay cấu hình precedence chính thức.
- [ ] Đã đối chiếu compiler/runtime version với tài liệu công cụ.

## 32. Tài liệu tham khảo và ranh giới áp dụng

Các tài liệu sau là nguồn ngữ nghĩa cho **AspectJ như một ví dụ AOP có abstract aspects**. Chúng **không** biến cú pháp hay giới hạn của AspectJ thành chuẩn chung của mọi engine:

- [Eclipse AspectJ Programming Guide — Aspects](https://eclipse.dev/aspectj/doc/released/progguide/semantics-aspects.html): aspect extension, abstract parent, instantiation lifecycle.
- [Eclipse AspectJ Programming Guide — Static crosscutting](https://eclipse.dev/aspectj/doc/released/progguide/semantics-declare.html): abstract aspect mẫu, abstract pointcuts và precedence của concrete aspects.
- [Eclipse AspectJ Programming Guide — Advice](https://eclipse.dev/aspectj/doc/released/progguide/semantics-advice.html): before, after, around, exception handling và precedence.
- [Eclipse AspectJ Development Guide — Concrete Aspects](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html): một hình thức biến abstract pointcut thành concrete aspect qua cấu hình deploy, cùng giới hạn riêng của cơ chế này.
- [Java SE 17 — Language Specification](https://docs.oracle.com/javase/specs/jls/se17/html/index.html): abstract class, inheritance, method overriding và exception semantics dùng trong ví dụ Java thuần.

Khi triển khai production, cần đọc tài liệu **đúng phiên bản compiler/weaver và runtime** thực tế, rồi kiểm chứng bằng build/test trong môi trường tương tự deployment.

## 33. Kết luận

**Aspect trừu tượng là một cách tái sử dụng hành vi cắt ngang khi mô hình AOP hỗ trợ kế thừa/abstraction ở cấp aspect.** Base aspect giữ advice và helper ổn định; concrete aspect định nghĩa pointcut/hook phù hợp miền nghiệp vụ. Mục tiêu là tránh trùng lặp mà không trộn lẫn những phạm vi có hợp đồng khác nhau.

Có ba điều phải giữ rõ:

1. **Tái sử dụng hành vi** không đồng nghĩa **mọi concrete aspect đều chọn cùng join points**.
2. **Một base advice** không đồng nghĩa **mỗi join point chỉ nhận advice một lần** khi nhiều concrete aspects cùng match.
3. **Abstract aspect ở ngôn ngữ AOP** khác **abstract class/interceptor wrapper Java**: cơ chế áp dụng, lifecycle và weaving boundary khác nhau.

Một thiết kế tốt phải nêu rõ **scope, lifecycle, precedence, extension contract và error semantics**; dùng tests dương/âm cho từng concrete aspect và tests tổng hợp khi có nhiều aspect. Nếu engine không hỗ trợ abstract aspects, hãy dùng **composition, strategy hoặc template interceptor** mà vẫn trung thực về khả năng can thiệp thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-002">Pointcut trừu tượng làm điểm tùy biến phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut trừu tượng làm điểm tùy biến phạm vi trong AOP

## 1. Vì sao cần một pointcut trừu tượng?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một **aspect** đóng gói mối quan tâm cắt ngang bằng **pointcut** (chọn những join point cần áp dụng) và **advice** (hành vi xảy ra khi join point khớp). Chẳng hạn, một advice có thể đo thời gian, ghi audit, theo dõi lỗi hoặc chặn thao tác không hợp lệ.

Khi viết những aspect cho nhiều khu vực của hệ thống, ta dễ gặp tình huống:

- **Hành vi advice giống nhau**: cùng định dạng log, cùng đồng hồ đo, cùng chính sách xử lý exception.
- **Phạm vi áp dụng khác nhau**: module A cần can thiệp vào các thao tác đọc; module B chỉ áp dụng cho thao tác xóa; module C dùng quy tắc tên phương thức, annotation hoặc một điều kiện theo ngữ cảnh.
- **Không muốn sao chép advice** cho từng module vì mỗi bản sao có thể dần khác nhau, khó cập nhật và khó kiểm thử.

Một **pointcut trừu tượng (abstract pointcut)** là ý tưởng **để trống phần lựa chọn join point tại một định nghĩa AOP trừu tượng**, rồi yêu cầu một định nghĩa cụ thể hơn cung cấp quy tắc lựa chọn. Nhờ đó:

> **Hành vi cắt ngang dùng chung được định nghĩa một lần; phạm vi được chuyên biệt hóa ở điểm mở rộng.**

Ở mô hình hỗ trợ abstract aspect và pointcut inheritance, abstract aspect có thể định nghĩa advice dùng lại và khai báo một pointcut chưa có expression cụ thể. Concrete aspect cung cấp expression tương ứng. Một số hệ thống AOP không có cú pháp abstract pointcut trực tiếp nhưng vẫn đạt ý tưởng tương tự bằng **pointcut provider, selector strategy, predicate hoặc cấu hình interception**.

**Quan trọng:** “pointcut trừu tượng” không có nghĩa “một pointcut mơ hồ chạy ở mọi nơi”. Nó là **một hợp đồng chưa được cụ thể hóa**; một aspect được triển khai hữu hiệu chỉ khi quy tắc matching và cơ chế can thiệp đã được xác định.

> **Phạm vi:** Bài này đề cập AOP cơ bản và nâng cao nói chung. Ví dụ ký pháp `abstract aspect`/`pointcut` chỉ minh họa một *mô hình ngôn ngữ AOP có hỗ trợ khái niệm ấy*, không phải cú pháp chuẩn bắt buộc cho mọi công cụ. Demo Java 17+ phía sau mô phỏng cơ chế mở rộng bằng Java Dynamic Proxy để dễ chạy và kiểm chứng, **không tự nhận là một trình biên dịch pointcut đầy đủ**.

## 2. Ôn nhanh: join point, pointcut và advice

Ba tầng cần phân biệt:

| Khái niệm | Câu hỏi | Minh họa |
| --- | --- | --- |
| **Join point** | Một vị trí/sự kiện nào có thể được can thiệp? | Method call/execution, constructor hoặc event do engine hỗ trợ |
| **Pointcut** | Những join point **nào** được chọn? | Chọn execution của method đọc, chọn caller context hoặc argument phù hợp |
| **Advice** | Khi khớp, hành vi **gì** được thực hiện? | Before log, around đo latency, after-throwing ghi lỗi |

```text
Tập join point mà engine có thể quan sát
    |
    +-- J1: Repository.fetch(...)
    +-- J2: Repository.erase(...)
    +-- J3: MessageSender.send(...)
    +-- J4: Service.calculate(...)
    |
    +--> Pointcut P(J) [true / false]
             |
          J được chọn
             |
          Advice dùng chung
```

Một pointcut không nhất thiết chỉ kiểm tên method. Nó có thể kết hợp **type/signature, target, argument type/value, annotation, dynamic control flow**, tùy khả năng của engine.

### “Định nghĩa pointcut” không phải “gọi phương thức”

Một pointcut được khai báo bằng ký pháp trông như một method/function trong một số ngôn ngữ AOP, nhưng nó là **quy tắc chọn join point**, không phải một business method mà caller gọi để thực hiện side effect. Advice sẽ được áp dụng ở những join point được runtime/weaver chọn; không vì có một tên pointcut trong class mà Java tự gọi pointcut ấy.

### Một join point có thể khớp nhiều pointcut

Các pointcut có thể trùng phạm vi. Nếu hai concrete aspects cùng áp dụng advice tới một execution, **có thể có hai advice activations**, theo quy tắc precedence/ordering của engine. Việc tách điểm tùy biến không tự ngăn advice chạy hai lần.

## 3. Abstract pointcut là hợp đồng mở rộng phạm vi

Một hình dung:

```text
         AbstractAuditAspect
         +--------------------------+
         | abstract pointcut scope  |
         | before advice            |
         | after / around advice    |
         | error handling           |
         +--------------------------+
                      ^
              concrete specialization
               /                \
              v                  v
        ReadAuditAspect     SensitiveAuditAspect
        scope = read()      scope = selected delete()
               \                /
                v              v
             Cùng logic advice,
            tập join point khác nhau
```

Abstract aspect giữ **logic chung** nhưng không quyết định đầy đủ scope. Concrete aspect xác định một quy tắc **khớp join point** cho abstract pointcut đó.

### So với abstract method thông thường

Có nét tương tự nguyên tắc *Template Method* trong hướng đối tượng:

- Lớp cha định nghĩa khung hành vi.
- Một điểm mở rộng được lớp con cụ thể hóa.
- Khung hành vi dùng giá trị/quy tắc do lớp con cung cấp.

Nhưng điểm khác:

- Abstract method trong Java là hợp đồng **gọi phương thức**; abstract pointcut trong AOP là hợp đồng **matching join points**.
- Một concrete pointcut expression có thể chứa static/dynamic predicates và binding.
- Engine quyết định thời điểm kiểm và áp dụng advice; không nhất thiết “advice gọi một hàm tên scope” như Java method bình thường.
- Pointcut selection cần xét **weaving/interception coverage**, khác với method dispatch ở một object thông thường.

Vì vậy so sánh Template Method giúp hiểu **điểm tùy biến**, nhưng không thay thế semantics AOP.

## 4. Phác thảo cú pháp AOP mang tính khái niệm

Một mô hình ngôn ngữ hỗ trợ abstract pointcut có thể được diễn đạt:

```text
abstract aspect AuditTemplate {
    abstract pointcut selectedOperation(String id);

    before(String id): selectedOperation(id) {
        writeAuditBefore(id);
    }

    after(String id): selectedOperation(id) {
        writeAuditAfter(id);
    }
}

aspect ReadAudit extends AuditTemplate {
    pointcut selectedOperation(String id):
        execution(readOperation(String)) AND args(id);
}

aspect SensitiveDeleteAudit extends AuditTemplate {
    pointcut selectedOperation(String id):
        execution(deleteOperation(String))
        AND args(id)
        AND runtimeValueCondition(id);
}
```

Đây **không phải mã Java hoặc DSL có cam kết biên dịch**. Các tên `execution`, `args`, `before`, `after` diễn đạt ý tưởng, không phải một cú pháp phổ quát. Một engine có thể hỗ trợ abstract pointcut nhưng không cho phép mọi phép binding/điều kiện runtime được biểu diễn theo cách trên; một engine khác chỉ hỗ trợ interceptor selectors do ứng dụng cấu hình.

Điểm cần thấy:

1. **`AuditTemplate` chứa advice một lần**.
2. **`selectedOperation` chưa được định nghĩa tại template**.
3. **`ReadAudit` và `SensitiveDeleteAudit` chọn các tập join point khác nhau**.
4. **Cùng một advice** được áp vào tập join point do concrete definition xác định.
5. **Binding `id` phải tương thích** với dữ liệu mà advice mong đợi.

### Nếu không có cú pháp abstract pointcut?

Có thể thiết kế tương đương bằng:

```text
Abstract advice/interceptor template
          |
          +--> Strategy: matches(joinPoint, args)
          |
          +--> Advice handler when matched
```

Java interface `PointcutSelector`, một `Predicate<Invocation>` hoặc một selector cấu hình cũng thể hiện **separation of concern between matching and action**. Nhưng đừng gọi mọi predicate Java là một “pointcut ngôn ngữ” nếu nó chỉ kiểm lời gọi đã đi vào handler; đó là **một mô phỏng runtime ở cấp interceptor**, không phải khả năng dệt join point của engine.

## 5. Chuyên biệt hóa phạm vi mà không sao chép advice

Xét một audit concern: mỗi method được chọn cần:

- Ghi event trước khi thực hiện.
- Gọi target.
- Ghi success và một số dữ liệu kết quả.
- Nếu target throw, ghi failure rồi giữ nguyên Throwable theo hợp đồng.
- Không làm biến đổi những method **không được chọn**.

Nếu viết ba aspects tách biệt có nội dung advice giống hệt, ta có rủi ro:

- Một aspect sửa logic xử lý exception, hai aspect còn giữ bản cũ.
- Nhãn, dữ liệu bảo mật hoặc định dạng log không đồng nhất.
- Những lỗi về retry/cleanup bị sao chép.
- Cần viết cùng bộ test logic cho nhiều bản advice.
- Khi thay đổi chính sách, khó chắc mọi bản sao được cập nhật.

Với abstract pointcut:

```text
                 Shared audit advice
          before -> proceed -> after / error
                          |
            +-------------+-------------+
            |             |             |
       Scope READ      Scope DELETE     Scope annotated
```

Tập join point có thể khác nhau; **logic đo/ghi nhận được chuẩn hóa**. Điều này làm **điểm biến thiên** rõ ràng hơn và hỗ trợ tái sử dụng.

### Tái sử dụng không đồng nghĩa “chỉ có một instance”

Một abstract aspect được chuyên biệt hóa thành hai concrete aspects có thể dẫn tới **hai instance aspect**, hai chính sách instantiation hoặc hai advice chains. Chúng tái sử dụng **định nghĩa/logic**, không bắt buộc chia sẻ một object singleton duy nhất. Instance scope tùy engine.

## 6. Phân biệt abstract aspect, abstract pointcut và concrete advice

- **Abstract aspect:** một định nghĩa aspect chưa thể dùng như một concrete aspect độc lập theo mô hình của ngôn ngữ, vì có điểm mở rộng chưa được cung cấp hoặc được đánh dấu abstract.
- **Abstract pointcut:** một pointcut được khai báo như hợp đồng nhưng chưa có rule matching cụ thể ở mức đó.
- **Concrete pointcut:** expression/selector đã xác định cách chọn join point.
- **Concrete advice:** hành vi before/after/around thực tế có thể nằm trong abstract aspect và được thừa kế/tái sử dụng theo semantics engine.
- **Abstract helper method:** một method OOP bình thường chưa có implementation, không tự trở thành pointcut dù đặt trong class aspect.

Những khái niệm này tương tác nhưng **không thể dùng thay thế nhau**.

### Một abstract pointcut có thể không có binding?

Có. Nếu advice không cần dữ liệu từ join point, pointcut có thể chỉ làm nhiệm vụ xác định phạm vi. Khi advice cần đối số, target, method metadata hoặc context, **hợp đồng binding** phải được bảo toàn trong concrete definition.

## 7. Binding là phần thiết yếu của hợp đồng pointcut

Một abstract pointcut có thể có tham số để advice nhận dữ liệu:

```text
abstract pointcut selected(String customerId);

before(String customerId): selected(customerId) {
    audit(customerId);
}
```

Concrete definition phải chọn những join point có thể **cung cấp giá trị tương ứng** cho `customerId` theo semantics engine.

### Vì sao chỉ match tên method chưa đủ?

Giả sử hệ thống có overload:

```java
// Mã minh họa:
void save(String id);
void save(long id);
void save(String id, boolean overwrite);
```

Nếu advice mong `String customerId`, một concrete pointcut match **mọi method tên save** nhưng lại cố bind `long id` vào `String` có thể không compile, không match hoặc thất bại ở runtime, tùy engine/cách provider viết.

Phải làm rõ:

- Signature/overload được chọn.
- Binding type và vị trí argument.
- Primitive vs wrapper, null và subtype.
- Join point có target hay không.
- Binding từ `this`/`target`/`args` theo định nghĩa hỗ trợ.
- Điều kiện runtime có thể thay đổi tại từng invocation.

**Abstract pointcut là contract không chỉ về “bao nhiêu join points”, mà còn về dữ liệu mà advice có thể sử dụng tại các join points đã chọn.**

### Đừng suy rằng binding đến từ biến trùng tên

Việc một parameter advice và một business parameter cùng tên `id` không tự tạo binding theo tên Java. Một engine có thể dùng explicit binding semantics, positional argument, type annotations hoặc metadata do weaving cung cấp.

## 8. Quy tắc specialization: thay thế, bổ sung và giao nhau

Một concrete aspect có thể xác định phạm vi theo các kiểu logic:

- **Exact specialization:** chọn đúng một operation hoặc signature.
- **Union:** cho nhiều nhóm hoạt động.
- **Intersection:** operation phù hợp **và** có annotation **và** thuộc module được phép.
- **Difference/exclusion:** chọn broad scope rồi loại code hạ tầng hoặc helper mà advice không nên intercept.
- **Dynamic filter:** chọn dựa trên runtime value hoặc cflow nếu engine hỗ trợ.

Ký pháp tập hợp:

```text
P_read = {J | operation(J) = fetch}

P_delete = {J | operation(J) = erase}

P_sensitive = P_delete ∩ {J | argument0(J) startsWith "SEC-"}

P_safe = P_broad \ P_infrastructure
```

### Chuyên biệt hóa không nhất thiết chỉ thu hẹp

Nếu lớp con định nghĩa lại một abstract pointcut bằng **union lớn hơn**, advice có thể chạy tại **nhiều join point hơn**. Từ “specialization” nói về **cụ thể hóa điểm mở rộng**, không bảo đảm toán học rằng tập mới luôn là tập con của một tập cũ, vì abstract pointcut không nhất thiết có tập gốc nào.

Một concrete pointcut cố ý mở rộng scope cần được review giống thay đổi quan trọng về behavior và performance.

## 9. Ví dụ Java 17+ hoàn chỉnh — cùng advice, hai pointcut cụ thể

Dưới đây là một chương trình Java SE tự chạy được. Nó dùng **Java Dynamic Proxy** để mô phỏng AOP qua interceptor.

**Thiết kế:**

- `AbstractAuditAspect` giữ **một** implementation advice chung: before, invoke target, after, error.
- Method `protected abstract boolean matches(Method,Object[])` là một **selector trừu tượng** tương tự vai trò abstract pointcut.
- `ReadAudit` chỉ chọn operation `fetch`.
- `SensitiveDeleteAudit` chỉ chọn operation `erase` khi đối số bắt đầu bằng `"SEC-"`.
- Cả hai dùng **cùng code advice**, không copy logic logging.
- Target có một đường lỗi `erase("SEC-FAIL")` để chứng minh advice giữ đúng error type và message.

**Lưu ý chính xác:** Trong Java SE, `matches(...)` **là abstract Java method**, không phải abstract pointcut khai báo theo một DSL AOP. Demo thể hiện **cơ chế tùy biến phạm vi của advice** ở tầng proxy/interceptor; không giả làm language-level abstract pointcut hoặc hỗ trợ execution join points ngoài proxy.

### 9.1. Tệp `AbstractPointcutDemo.java`

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

public class AbstractPointcutDemo {

    public interface Operations {
        String fetch(String id);
        String erase(String id);
    }

    static final class Repository implements Operations {
        @Override
        public String fetch(String id) {
            System.out.println("target.fetch " + id);
            return "value:" + id;
        }

        @Override
        public String erase(String id) {
            System.out.println("target.erase " + id);
            if ("SEC-FAIL".equals(id)) {
                throw new IllegalStateException("storage refused");
            }
            return "removed:" + id;
        }
    }

    static abstract class AbstractAuditAspect {
        // Hợp đồng "pointcut" ở mô hình interceptor này:
        // concrete aspect quyết định một invocation có thuộc scope.
        protected abstract boolean matches(
                Method method, Object[] arguments);

        protected abstract String label();

        // Template advice chung; các lớp con không lặp lại logic.
        public final Operations advise(Operations target) {
            Objects.requireNonNull(target);

            return (Operations) Proxy.newProxyInstance(
                    Operations.class.getClassLoader(),
                    new Class<?>[]{Operations.class},
                    (proxy, method, args) -> {
                        Object[] actual = args == null
                                ? new Object[0] : args;

                        if (!matches(method, actual)) {
                            return invokeTarget(method, target, args);
                        }

                        String id = (String) actual[0];
                        System.out.println(
                                label() + ".before "
                                + method.getName() + " " + id);

                        try {
                            Object result = invokeTarget(
                                    method, target, args);

                            System.out.println(
                                    label() + ".after "
                                    + method.getName() + " " + result);

                            return result;
                        } catch (Throwable original) {
                            System.out.println(
                                    label() + ".error "
                                    + method.getName() + " "
                                    + original.getClass().getSimpleName());
                            throw original;
                        }
                    });
        }

        private static Object invokeTarget(
                Method method, Operations target, Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapped) {
                throw wrapped.getCause();
            }
        }
    }

    static final class ReadAudit extends AbstractAuditAspect {
        @Override
        protected boolean matches(
                Method method, Object[] arguments) {
            return "fetch".equals(method.getName());
        }

        @Override
        protected String label() {
            return "readAudit";
        }
    }

    static final class SensitiveDeleteAudit
            extends AbstractAuditAspect {
        @Override
        protected boolean matches(
                Method method, Object[] arguments) {
            return "erase".equals(method.getName())
                    && arguments.length == 1
                    && arguments[0] instanceof String id
                    && id.startsWith("SEC-");
        }

        @Override
        protected String label() {
            return "sensitiveAudit";
        }
    }

    public static void main(String[] args) {
        Operations target = new Repository();

        System.out.println("== read scope ==");
        Operations auditedReads =
                new ReadAudit().advise(target);

        System.out.println("caller "
                + auditedReads.fetch("A"));
        System.out.println("caller "
                + auditedReads.erase("SEC-9"));

        System.out.println("== sensitive delete scope ==");
        Operations auditedDeletes =
                new SensitiveDeleteAudit().advise(target);

        System.out.println("caller "
                + auditedDeletes.fetch("B"));
        System.out.println("caller "
                + auditedDeletes.erase("PUBLIC-1"));
        System.out.println("caller "
                + auditedDeletes.erase("SEC-9"));

        try {
            auditedDeletes.erase("SEC-FAIL");
        } catch (IllegalStateException original) {
            System.out.println(
                    "caller caught " + original.getMessage());
        }
    }
}
```

### 9.2. Biên dịch và chạy

Yêu cầu JDK 17 hoặc mới hơn:

```bash
javac --release 17 -encoding UTF-8 AbstractPointcutDemo.java
java AbstractPointcutDemo
```

**Standard output dự kiến theo logic chương trình:**

```text
== read scope ==
readAudit.before fetch A
target.fetch A
readAudit.after fetch value:A
caller value:A
target.erase SEC-9
caller removed:SEC-9
== sensitive delete scope ==
target.fetch B
caller value:B
target.erase PUBLIC-1
caller removed:PUBLIC-1
sensitiveAudit.before erase SEC-9
target.erase SEC-9
sensitiveAudit.after erase removed:SEC-9
caller removed:SEC-9
sensitiveAudit.before erase SEC-FAIL
target.erase SEC-FAIL
sensitiveAudit.error erase IllegalStateException
caller caught storage refused
```

Output được suy ra từ luồng gọi và cấu trúc if/try/catch. Trong một ứng dụng thực, nên thực thi bằng JDK để kiểm chứng, đồng thời viết integration test với chính cơ chế AOP được dùng.

### 9.3. Vì sao thao tác đọc được audit trong scope thứ nhất?

`ReadAudit.matches` trả `true` cho method tên `fetch`. Handler chạy chung template:

```text
readAudit.before fetch A
target.fetch A
readAudit.after fetch value:A
```

Trong khi đó `erase("SEC-9")` **không match** pointcut này. Handler vẫn gọi target để bảo toàn hoạt động nghiệp vụ, nhưng **không chạy logic advice audit**.

**Không match pointcut không có nghĩa phải bỏ qua target.** Với around/interceptor chain, code cần tiếp tục invocation đúng contract, trừ khi có policy chủ động chặn.

### 9.4. Vì sao scope thứ hai chỉ audit một số thao tác xóa?

`SensitiveDeleteAudit.matches` kiểm:

1. Method tên `erase`.
2. Có đúng một argument.
3. Argument đầu là `String`.
4. Giá trị `String` bắt đầu bằng `SEC-`.

Vì vậy:

- `fetch("B")`: không match.
- `erase("PUBLIC-1")`: không match.
- `erase("SEC-9")`: match, chạy before/after.
- `erase("SEC-FAIL")`: match, chạy before rồi error.

**Đây là dynamic argument-value selection**. Một engine AOP chỉ hỗ trợ pointcut static metadata có thể yêu cầu advice guard hoặc một interceptor selector runtime riêng để kiểm giá trị. Không suy rằng mọi ngôn ngữ abstract pointcut đều có sẵn hàm `startsWith` trong pointcut expression.

### 9.5. Vì sao logic advice được tái sử dụng thật?

Hai concrete classes chỉ chứa:

- `matches`: chọn scope.
- `label`: nhãn quan sát.

Chúng **không chứa bản sao** của before/after/error handling hoặc đoạn Reflection unwrap. Nếu đổi cách ghi event ở `AbstractAuditAspect.advise`, cả hai scope dùng logic mới — miễn là chúng được chạy qua template ấy.

### 9.6. Vì sao ngoại lệ không bị biến đổi ngoài ý muốn?

`Repository.erase("SEC-FAIL")` ném `IllegalStateException("storage refused")`. Reflection bọc Throwable của target trong `InvocationTargetException`; helper `invokeTarget` lấy `wrapped.getCause()` rồi throw đúng Throwable gốc.

Advice ghi `sensitiveAudit.error erase IllegalStateException` rồi `throw original`. Caller bắt `IllegalStateException` với message như ban đầu.

**Bài này tập trung vào scope selection**, nhưng demo vẫn tôn trọng hợp đồng exception. Pointcut thay đổi *nơi* advice chạy, không phải giấy phép tự động đổi outcome của target.

### 9.7. Một target, hai proxy, không có nghĩa hai scope được áp đồng thời

`auditedReads` và `auditedDeletes` là **hai proxy khác nhau** cùng tham chiếu `target`. Khi gọi `auditedReads`, chỉ template/selector của `ReadAudit` chạy. Khi gọi `auditedDeletes`, chỉ selector của `SensitiveDeleteAudit` chạy. Demo **không ghép hai aspects vào cùng một advice chain**.

Nếu lồng proxy hoặc dùng weaver áp nhiều concrete aspects lên cùng join point, thứ tự và khả năng advice chạy nhiều lần sẽ khác. **Tái sử dụng template không tự quyết định precedence.**

### 9.8. Giới hạn của Java Dynamic Proxy trong demo

- Chỉ intercept method được gọi qua `Operations` proxy reference.
- Lời gọi raw `target.fetch(...)` không chạy advice.
- Lời gọi nội bộ trong target qua `this` không tự đi qua wrapper proxy.
- Proxy không trực tiếp biểu diễn constructor/field access execution join points.
- `matches` chạy sau khi method đã đi qua proxy; nó không phải một static weaver pointcut parser.
- Không có cflow, weaving-time specialization hoặc abstract aspect compiler trong demo.
- Không tự chứng minh mọi engine cho phép subclass concrete aspect như ở Java classes.
- Nếu phương thức có overload, `matches` nên dùng signature đầy đủ thay vì chỉ method name.
- Nếu exception là checked, cần xem declared throws và contract của Java Dynamic Proxy.

**Đây là minh họa đúng về thiết kế reusable advice + customizable scope trong Java SE, không là bằng chứng của một DSL pointcut cụ thể.**

## 10. Điểm tùy biến chỉ thay scope, không nên thay hợp đồng hành vi tùy tiện

Mục tiêu tái sử dụng là **giữ ổn định một tập bất biến hành vi**:

- Method được chọn có advice before/after/error nhất quán.
- Method không được chọn vẫn hoạt động bình thường.
- Outcome target được giữ hoặc được biến đổi **theo một policy riêng có contract**.
- Logging/metric chỉ chứa dữ liệu được phép.
- Resource và context được dọn đúng dù exception xảy ra.

Nếu các concrete aspects đồng thời override quá nhiều method advice, template có thể mất ý nghĩa. Khi đó cân nhắc:

- Tách **policy object** hoặc **strategy** cho phần thực sự thay đổi.
- Tách nhiều abstract templates khi hành vi đã thực sự khác nhau.
- Đưa các biến thể vào metadata/parameter có kiểu rõ.
- Không dùng inheritance quá sâu chỉ để thay một điều kiện Boolean nhỏ.

**Điểm mở rộng có ích nhất là điểm có phạm vi trách nhiệm rõ và contract dễ kiểm thử.**

## 11. Inheritance và khả năng “override” pointcut

Trong một ngôn ngữ AOP hỗ trợ abstract pointcut, concrete specialization thường cần thỏa:

- Concrete definition có tên/hợp đồng tương ứng.
- Dữ liệu binding và kiểu tương thích.
- Kiểu aspect/concrete declaration hợp lệ theo engine.
- Quy tắc visibility/inheritance cho phép advice sử dụng pointcut.
- Pointcut expression đủ cụ thể để chọn join point thực tế.
- Engine có thể weave những join point được pointcut chọn.

**Không nên áp quy tắc Java override method 1:1 sang mọi AOP language.** Một số công cụ cho phép override named pointcut; một số có cơ chế specialization khác; một số pointcut definitions có tính static/declarative không hoạt động như virtual dispatch của Java methods.

### Thay thế hay cộng dồn?

Khi concrete definition cung cấp một pointcut mới, cần xác định trong engine:

- Nó **thay** định nghĩa abstract (định nghĩa vốn chưa có expression).
- Nó có **bổ sung** những pointcut cụ thể ở lớp cha khác không?
- Advice được **thừa kế** hay được áp riêng trên mỗi aspect?
- Có thể có nhiều concrete descendants hoạt động cùng lúc không?

Không mặc định “một abstract pointcut được cụ thể hóa” nghĩa là engine sẽ chỉ tạo một advice chain trong toàn tiến trình. Hai concrete aspects có thể cùng tồn tại và cùng match một join point.

## 12. Abstract pointcut là điểm mở rộng, không phải công tắc tắt toàn bộ AOP

Nếu định nghĩa pointcut cụ thể luôn false hoặc chọn tập rỗng:

```text
scope(J) = false for all eligible J
```

advice liên quan không chạy ở join point nào của scope ấy. Điều này khác:

- Engine không hỗ trợ/dệt loại join point cần chọn.
- Class target không thuộc weaving scope.
- Caller bypass proxy.
- Một around advice bên ngoài chặn `proceed`.
- Advice có chạy nhưng logger/sink bị disable.

Trong debugging, **“không thấy advice” chưa đủ kết luận “pointcut abstract không được override”**. Cần quan sát matching, interception boundary, advice selection, ordering và output sink độc lập.

### Một concrete scope có thể nguy hiểm vì quá rộng

Một selector như “mọi method của mọi class” có thể bắt cả:

- Logging/sink methods được advice gọi.
- Runtime helper của weaver/interceptor.
- Constructors hoặc lifecycle helpers ngoài dự kiến.
- Methods cực nóng trong chương trình.
- Security-sensitive methods mà advice không được phép thay outcome.

Điều đó có thể gây **advice-induced recursion**, event storm hoặc tác động hiệu năng lớn. Nên có **scope rõ và exclusion cụ thể**, tránh một expression rộng thiếu kiểm soát.

## 13. Pointcut composition và khả năng tái sử dụng ở nhiều cấp

Một abstract pointcut có thể được concretize bằng cách ghép **những named pointcuts khác**:

```text
businessOperation(J) = inBusinessModule(J) AND publicMethod(J)

auditable(J) = businessOperation(J)
               AND NOT insideAuditInfrastructure(J)

selectedRead(J) = auditable(J) AND namedFetch(J)
```

Tách những khái niệm như `businessOperation` hay `insideAuditInfrastructure` ra named pointcuts có thể hữu ích nếu chúng là quy tắc tái sử dụng độc lập.

Nhưng có hai dạng tái sử dụng khác nhau:

1. **Named pointcut composition:** các expression dùng lại nhau như các khối logic.
2. **Abstract pointcut specialization:** một advice/template cố tình **chưa định nghĩa scope**, để một concrete aspect cung cấp sau.

Một hệ thống có thể hỗ trợ một dạng mà không hỗ trợ đầy đủ dạng kia. Không đánh đồng việc đặt tên một expression với việc có hợp đồng abstract pointcut.

## 14. Static selection và dynamic selection

Điểm nối giữa concrete pointcut và advice có thể gồm:

**Static predicates:** type name, method signature, visibility, annotation, caller/callee pattern; phần lớn có thể đánh giá bằng metadata khi weave hoặc khi tạo proxy.

**Dynamic predicates:** argument type/value, receiver object, runtime state, dynamic control flow; có thể cần kiểm **ở từng occurrence** của join point.

```text
Static filter (có thể tối ưu trước)
   method name = erase
       |
Runtime condition
   first argument starts with "SEC-"
       |
Apply advice
```

### Tối ưu và correctness

Nếu một engine đã xác định method chắc chắn không khớp static part, nó có thể bỏ qua dynamic check. Nhưng một proxy handler như demo nhận method invocation rồi mới gọi `matches`. Hiệu năng hai mô hình có thể khác rõ.

Không cache kết quả **runtime argument-value matching** theo **method name** mà quên argument values. Nếu `erase("SEC-9")` match nhưng `erase("PUBLIC-1")` không match, cache `"erase" → true` sẽ làm sai scope.

### Type erasure, overload và bridge methods

Trong Java:

- Generic type erasure có thể làm runtime signature khác source-level parameterization.
- Method overload có cùng tên nhưng type arguments khác.
- Bridge/synthetic methods có thể tồn tại do compiler tạo cho generics/covariant return.
- Proxy interface Method có thể không cùng DeclaringClass với implementation method metadata.
- Annotation có thể xuất hiện ở interface, implementation hoặc override với quy tắc khác.

Một concrete pointcut chuyên biệt hóa bằng reflection cần nêu rõ **metadata được đọc ở đâu**. Với engine weaving, semantics signature/annotation matching cũng phải được kiểm thử theo công cụ thực tế.

## 15. Binding target, receiver, argument: đừng gộp làm một

Một abstract pointcut có thể muốn ràng buộc:

- **`this`** theo nghĩa object của ngữ cảnh join point.
- **`target`** theo nghĩa receiver/target, khi có.
- **`args`** theo runtime argument values.
- **Method/execution metadata**, annotation, caller location.

Những tên này có semantics phụ thuộc **loại join point và engine**. Với method call, caller context và target receiver có thể là **hai object khác nhau**. Với static method có thể không có target instance. Với constructors hoặc asynchronous callbacks, “current receiver” có thể không giống một business target được dự đoán.

Nếu advice template đòi binding một target object cụ thể, concrete pointcut phải chọn **tập join point thực sự có binding ấy**. Không mặc định mọi join point đều có `target` non-null.

### Null/primitive/varargs

Nếu selector dựa trên `args`:

- `null` không cung cấp runtime subtype cụ thể.
- Primitive/wrapper có boxing tùy API/interceptor.
- Varargs ở Java thường thể hiện qua array ở runtime.
- Một số interceptor API nhận `Object[]` argument values với quy tắc riêng.
- Binding vào một loại không tương thích có thể không match hoặc lỗi theo engine.

**Phải kiểm tra signature và binding contract, không chỉ điều kiện string trong advice.**

## 16. Điểm tùy biến phạm vi và điểm tùy biến dữ liệu là hai việc

Giả sử advice audit chung cần một `String id`. Một concrete pointcut chọn `delete` nhưng trong module khác id là một `UUID` hoặc nested field trong request object. Có hai hướng:

1. **Giữ hợp đồng `String id`:** concrete specialization phải cung cấp một binding có dạng String phù hợp nếu ngôn ngữ hỗ trợ.
2. **Tách selection khỏi extraction:** concrete aspect cung cấp scope và một extractor/metadata provider riêng để chuyển join point data sang event model dùng chung.

Điều này tránh dùng một abstract pointcut với signature phi thực tế rồi buộc mọi method cũng nhận `String` giống nhau.

### Không làm mất semantics dữ liệu

Một ID do caller đưa tới có thể không phải canonical business ID. Nếu audit hoặc policy dựa trên nó, cần xác minh identity sau normalization/authorization. Pointcut binding chỉ đưa giá trị vào advice; **không bảo đảm dữ liệu đáng tin hay đã được xác thực**.

## 17. Phạm vi advice và hợp đồng bảo mật

Abstract pointcut rất hữu ích khi tái sử dụng **logging/timing/audit**, nhưng cũng có rủi ro nếu dùng để chọn các **enforcement checks**.

Ví dụ template chung:

```text
checkPermission(target, currentPrincipal);
proceed();
```

Concrete scope xác định **những thao tác nào bắt buộc phải kiểm quyền**. Nếu một concrete pointcut thiếu một overload, một execution path hoặc một callback worker, enforcement có thể bị bypass.

**Không được xem pointcut chỉ là tối ưu chọn log khi scope của nó là một security boundary.**

Cần:

- Xác định danh sách entry points cần được bảo vệ.
- Kiểm cả proxy/raw target, self-invocation, reflection, callback/async.
- Kiểm pointcut trên overload/inheritance/module boundaries.
- Có defense-in-depth ở ranh giới đáng tin.
- Không dùng sampling hoặc “root-only guard” để lược bỏ kiểm tra bắt buộc.
- Test coverage bằng lời gọi từ những đường thực mà ứng dụng cho phép.

### Advice chung không tự có trust level chung

Một concrete aspect áp template lên một package không biến các method ấy thành trusted. Context/current principal phải được cung cấp và xác thực từ hệ thống phù hợp; pointcut chỉ chọn **nơi logic chạy**, không xác minh nội dung identity.

## 18. Tương tác với proxy, self-invocation và weaving

Một concrete pointcut có thể chọn **method execution**, nhưng nếu triển khai AOP qua wrapper proxy, điều mà runtime intercept thường là **lời gọi đi qua proxy**.

```text
caller -> proxy.fetch() -> advice -> target.fetch()
                           |
                    selector.matches()

target.this.otherMethod()  [có thể bypass wrapper proxy]
```

Nếu code target gọi `this.otherMethod()` trực tiếp, Java Dynamic Proxy bên ngoài target **không được gọi lần thứ hai**. Concrete scope dù “chọn method otherMethod” cũng không thể tự khiến handler chạy trên đường bypass đó.

Một bytecode weaver hỗ trợ execution join points có thể can thiệp trực tiếp vào callee body (khi đã weave), và coverage có thể khác. **Abstract pointcut customization không tạo join points mới ngoài những điểm engine có thể quan sát**.

### Loại join point là phần của khả năng áp dụng

Nếu concrete pointcut chọn constructor call, nhưng runtime chỉ hỗ trợ method calls qua interface proxy, selector không thể biến proxy thành công cụ can thiệp constructor. Cần chọn engine/pointcut model phù hợp, hoặc thiết kế lại boundary.

## 19. Khi dùng nhiều concrete aspects cùng một template

Giả sử:

```text
AbstractTimingAspect
    |
    +-- BusinessTiming:  all business execution
    |
    +-- SlowQueryTiming: selected database query execution
```

Một database query nằm trong business execution có thể **khớp cả hai scopes**. Tùy cơ chế, có thể ghi hai durations hoặc tạo hai nested advice activations.

Những câu hỏi bắt buộc:

1. Có hai concrete aspect instances cùng active không?
2. Chúng áp cùng advice body hay hai bản được chuyên biệt hóa?
3. Precedence/order do ai quyết định?
4. Around advice có nesting thế nào?
5. Metric có bị đếm hai lần không?
6. Nếu một advice không proceed, advice còn lại có chạy không?
7. Có state dùng chung bị ghi đè không?
8. Exception sẽ đi qua những layer mapping nào?

**Tái sử dụng bằng inheritance có thể làm tăng số advice occurrences** nếu các concrete scopes chồng lấn. Đó là behavior cần được thiết kế và test, không phải lỗi hiển nhiên của abstraction.

## 20. Abstract pointcut và vòng đời của aspect instance

Một số mô hình AOP cho phép aspect instance dùng chung hoặc liên kết với target/object/control flow. Điểm tùy biến scope **khác trục** với instantiation policy:

| Quyết định | Câu hỏi |
| --- | --- |
| Pointcut scope | Join point nào match? |
| Advice kind | Before/after/around làm gì? |
| Aspect lifecycle | Một instance hay nhiều instance, gắn với đâu? |
| Invocation state | Dữ liệu tạm của lời gọi được lưu ở đâu? |
| Interception coverage | Có thật sự quan sát được join point đó không? |
| Advice precedence | Nhiều aspects khớp thì thứ tự nào? |

Nếu một template advice dùng field `currentId` mutable trên singleton aspect, hai concrete scopes không khắc phục lỗi data race/reentrancy. Dù có per-target aspect, hai calls trên cùng target vẫn có thể đồng thời chia sẻ state.

**Giữ trạng thái per-invocation trong local variables hoặc invocation context**, dùng shared immutable config và concurrency-safe metrics cho dữ liệu thực sự dùng chung. Không dùng abstract pointcut để che một lỗi quản lý state.

## 21. Kiểu dữ liệu và hợp đồng của around advice

Một around advice có quyền chặn, thay return hoặc throw tùy engine. Nếu template chung áp lên nhiều signatures:

- `void` vs reference return vs primitive return có khác biệt.
- Checked exceptions của API caller có thể khác.
- Method async trả `CompletableFuture<T>` có semantics completion riêng.
- Một generic binding/return phải tôn trọng runtime cast và type erasure.

Một template around trả `null` khi skip target có thể chạy với reference return nhưng sẽ gây vấn đề cho primitive return. Một template catch mọi exception rồi trả fallback có thể phá contract của nhiều methods được concrete pointcut mới mở rộng lựa chọn.

**Khi concrete scope mở rộng, phải kiểm tra *hợp đồng của các method mới được chọn* chứ không chỉ syntax của expression.**

### Advice template tối thiểu hóa can thiệp outcome

Với concern quan sát như timing/logging, ưu tiên preserve return/exception:

```text
around:
    start timer
    try:
        return proceed()
    finally:
        record duration
```

Đây là pseudocode AOP. Việc recorder thất bại có thể che target exception nếu `finally` ném lỗi; phải có error policy phù hợp. Nếu advice thực sự thay outcome, policy ấy là một phần contract chứ không chỉ là “pointcut customization”.

## 22. Abstract pointcut và configuration ngoài thời gian biên dịch

Có hai cách thay đổi scope khác nhau:

1. **Compile/weave-time specialization:** concrete pointcut expression được xác định khi aspect được biên dịch/dệt, không đổi theo từng request một cách tùy tiện.
2. **Runtime selection:** interceptor/pointcut provider đọc cấu hình hoặc argument values để quyết định match khi invocation xảy ra, trong giới hạn engine.

Một **abstract pointcut theo ngôn ngữ AOP** không đồng nghĩa một cấu hình có thể **hot reload scope** trong khi chương trình đang chạy. Đổi concrete aspect code có thể đòi build/weave lại hoặc khởi động runtime; dynamic selector lại có thể cập nhật config theo snapshot.

### Khi cần reload runtime

Tách:

- **Immutable policy snapshot**: tập operation/condition được phép.
- **Safe publication**: `volatile` hoặc `AtomicReference` tùy yêu cầu Java.
- **Per-invocation selection**: chọn snapshot một lần nếu cần consistency.
- **Matching semantics**: dynamic condition nào có thể đánh giá lúc runtime?
- **Weaving scope**: các join points chưa được instrument không tự xuất hiện khi reload.

Đừng hứa “thay file config là pointcut bytecode tự mở rộng sang mọi class” nếu runtime AOP không hỗ trợ.

## 23. Hiệu năng: scope càng rộng càng đắt, nhưng có hai lớp chi phí

Một concrete pointcut rộng có thể làm tăng:

- Số join point candidates được dệt.
- Chi phí dynamic predicate per invocation.
- Số lần advice thực thi.
- Allocation của advice context, log event, trace span.
- Locking/metric sink contention.
- Chi phí xử lý exception/unwind.
- Tác động lên hot methods hoặc recursive execution.

Một abstract pointcut **không bắt buộc khiến mỗi invocation phải gọi một method Java virtual `matches`**. Trong mô hình bytecode weaving, expression có thể được compile/optimize để giảm runtime check. Ngược lại Java proxy demo luôn thực hiện selector mỗi handler invocation.

**Đánh giá performance của actual engine và actual scope**, không suy số overhead từ hình thức “abstract” của pointcut.

### Narrow scope một cách có nghĩa

Không nên chọn “mọi public method” khi yêu cầu chỉ là audit thao tác xóa có tác động. Giới hạn theo operation/type/signature, dùng exclusions cho helper/runtime methods và dynamic condition chỉ khi cần.

Nhưng cũng không thu hẹp scope đến mức bỏ sót entry point bắt buộc. Với audit/security, correctness coverage quan trọng hơn chi phí một vài checks.

## 24. Thiết kế module dễ thay đổi bằng abstract pointcut

Một cấu trúc có thể tổ chức theo ba lớp:

```text
Concern contract:
    AuditTemplate
       - event shape
       - exception/return semantics
       - safe logger
       - abstract pointcut "auditScope"

Domain-specific aspect:
    ProductAudit
       - auditScope = product mutation operations

Infrastructure composition:
    proxy/weaver setup
       - enables selected aspects
       - precedence
       - instance lifecycle
       - metrics/security policy
```

Cách tổ chức này tách:

- **Concern ownership:** nhóm sở hữu audit/tracing behavior.
- **Domain scope ownership:** nhóm biết method/module nào thuộc nghiệp vụ.
- **Runtime ownership:** nhóm kiểm soát weaving/proxy, deployment, context propagation.

Không buộc domain team sao chép định dạng log hoặc viết lại exception management, và không buộc người viết core audit biết tên mọi domain method.

### Một rủi ro: thay đổi method name làm scope lặng lẽ rỗng

Nếu concrete pointcut dựa vào pattern tên `erase*`, một lần rename có thể khiến advice ngừng áp dụng dù business behavior vẫn cần audit. Giải pháp gồm:

- Dựa trên contract/interface/annotation có chính sách, nếu phù hợp.
- Viết integration tests xác nhận selected join points.
- Dùng build-time validation/crosscutting reports nếu engine cung cấp.
- Kiểm tra output instrumented trong môi trường test.
- Phân biệt “chắc chắn không match” với “không được weave”.

## 25. Cách tránh scope quá mơ hồ

Một concrete pointcut tốt nên trả lời được:

1. **Đối tượng nào?** Target type/interface/module?
2. **Hoạt động nào?** Signature/constructor/event?
3. **Loại join point nào?** Call hay execution?
4. **Bối cảnh nào?** Runtime args, annotations, cflow?
5. **Excluded paths nào?** Logging/metrics sink, helper, startup?
6. **Số lần kỳ vọng?** Một operation, mỗi attempt hay mỗi execution?
7. **Proxy/weaver có thật sự quan sát đường đó không?**
8. **Điều gì thay đổi nếu method có overload?**
9. **Điều gì xảy ra với subclass, override và generics?**
10. **Mỗi match có cung cấp bindings advice cần không?**
11. **Nếu hai concrete aspects overlap thì ai chạy trước?**
12. **Nếu target throw, advice giữ contract ra sao?**

Nếu không trả lời được, pointcut expression có thể “đúng cú pháp nhưng sai ý nghĩa”.

## 26. Unit test selector và integration test advice

Tách hai tầng kiểm thử:

**Unit test concrete selector**

- `fetch("A")` match scope read.
- `erase("SEC-9")` không match scope read.
- `erase("SEC-9")` match scope sensitive delete.
- `erase("PUBLIC-1")` không match scope sensitive delete.
- `erase(null)` có xử lý đúng theo contract không?
- Signature overload có bị chọn nhầm không?

**Integration test thực qua interceptor/weaver**

- Advice được gọi chính xác ở join point được chọn.
- Target vẫn chạy khi selector không match.
- Return/Throwable được preserve theo contract.
- Raw target/proxy self-invocation được hiểu đúng.
- Nhiều concrete aspects chồng lấn có precedence đúng.
- Advice không tự instrument logging sink gây recursion.
- Async worker/callback thuộc phạm vi nào?
- Các module/class loaders cần thiết có được weave không?
- Bảo mật/audit bắt buộc không bị bỏ sót.

### Chỉ test `matches` chưa đủ

Một selector trả true trong unit test nhưng **không bao giờ được gọi ở runtime** nếu caller không đi qua proxy hoặc execution chưa được weave. Ngược lại, một advice có thể chạy ở method do pattern quá rộng dù selector unit test chỉ kiểm vài ví dụ đơn giản.

**Coverage của pointcut và correctness của advice là hai loại test riêng.**

## 27. Ma trận chọn pointcut cho cùng một template

| Concrete scope | Điều kiện điển hình | Rủi ro cần kiểm |
| --- | --- | --- |
| Method đọc | Operation name/signature | Method rename, overload, cache read ngoài proxy |
| Method ghi/xóa | Mutation entry points | Bypass, self-call, side effects |
| Theo annotation | Annotation trên method/type | Interface vs implementation annotation, inheritance |
| Theo target type | Subclass/implements interface | Runtime subtype, proxy type, loader |
| Theo tham số | Argument type/value | Null, boxing, runtime-only matching |
| Theo tenant/request | Verified context + scope | Security, propagation, stale context |
| Theo control flow | Join point trong flow gốc | Async boundary, nested flow, recursion |
| Theo source module | Package/module naming | Refactor làm scope đổi im lặng |
| Theo exception outcome | Around/after-throwing behavior | Type contract, retry/translation |
| Theo profile/config | Runtime selector snapshot | Reload, cache invalidation, weaving coverage |

**Điểm tùy biến không nhất thiết phải nằm ở một concrete inheritance tree.** Nếu hệ thống cần hàng trăm tổ hợp động, composition strategies hoặc cấu hình có thể đơn giản hơn đa cấp subclassing.

## 28. Lỗi thiết kế phổ biến

1. **“Abstract pointcut là một method Java có thể gọi.”** Không, đó là hợp đồng chọn join point trong mô hình AOP hỗ trợ.
2. **“Abstract aspect nghĩa là advice chưa được viết.”** Advice có thể đã được định nghĩa đầy đủ; chỉ scope trừu tượng.
3. **“Concrete pointcut mặc nhiên chỉ thu hẹp scope.”** Có thể chọn tập rộng khác hoàn toàn.
4. **“Tái sử dụng advice nghĩa là chỉ có một instance aspect.”** Logic reuse và lifecycle khác nhau.
5. **“Khai báo abstract pointcut là được weave tất cả method.”** Cần concrete definition và weaving scope.
6. **“Một pointcut tên giống nhau ở mọi engine có cùng binding semantics.”** Không có cú pháp phổ quát.
7. **“Tên parameter trùng nhau là binding tự động.”** Phải theo contract engine.
8. **“Match method name là đủ với overload.”** Cần signature khi semantics khác.
9. **“Dynamic argument value luôn đánh giá lúc weave-time.”** Giá trị runtime có thể chỉ biết khi gọi.
10. **“Đổi concrete scope không thể đổi hành vi.”** Caller có thể được thêm/bớt advice và outcome/latency thay đổi.
11. **“Hai subclasses không thể áp cùng join point.”** Scopes có thể overlap.
12. **“Template advice chung bảo đảm không chạy hai lần.”** Precedence/composition cần được kiểm.
13. **“Per-target aspect bảo đảm per-invocation state an toàn.”** Hai calls trên cùng target có thể concurrent.
14. **“Thêm synchronized vào singleton sửa state correlation.”** Chọn đúng scope per-call quan trọng hơn.
15. **“Pointcut có thể thay security boundary.”** Bypass raw/proxy/async cần phòng vệ thích hợp.
16. **“Callback được đăng ký trong method được chọn thì callback cũng được chọn.”** Registration khác execution.
17. **“Call pointcut tương đương execution pointcut.”** Hai vị trí runtime khác nhau.
18. **“Self-invocation trong wrapper proxy chắc được can thiệp.”** Có thể bypass.
19. **“Abstract pointcut làm dynamic config reload tự động.”** Phụ thuộc runtime và weaving model.
20. **“Một map cache kết quả match theo method name luôn đúng.”** Dynamic args/loader/revision có thể khác.
21. **“Một Java Predicate chính là cú pháp abstract pointcut native.”** Predicate chỉ mô phỏng khái niệm matching.
22. **“Pointcut rộng chỉ gây thêm log, không tác động correctness.”** Advice có thể chặn, retry, alter exception.
23. **“Không có log nghĩa là không match pointcut.”** Có thể thiếu interception hoặc sink bị tắt.
24. **“Một compiled aspect dùng được qua mọi class loader.”** Weaving/type identity có thể khác.
25. **“Aspect inheritance giống 100% Java method override.”** Semantics DSL và weaving có thể khác.
26. **“Tất cả return types đều phù hợp một around advice trả null.”** Primitive/non-null contract khác.
27. **“Một concrete aspect luôn thay thế aspect khác.”** Nhiều aspects có thể cùng active.
28. **“Không cần test khi sửa pointcut expression.”** Đây là thay đổi phạm vi hành vi.
29. **“Logging helper ngoài scope theo trực giác.”** Nếu pattern rộng, advice có thể tự gọi lại mình.
30. **“Cụ thể hóa pointcut bảo đảm áp vào mọi async task của request.”** Cflow/ThreadLocal không tự xuyên thread.

## 29. Câu hỏi nâng cao: một abstract pointcut có nên biết domain không?

Có hai xu hướng:

**Template tối thiểu:** `AuditTemplate` chỉ khai báo `scope(...)` và xử lý event generic, không biết `Product` hay `Payment`.

**Template domain-aware:** một abstract template có thể biết một abstraction domain chung như `BusinessOperation`, nhưng cụ thể hóa subtypes vẫn để concrete definitions.

Không có một mức trừu tượng tối ưu cho mọi hệ thống. Nếu template phải chứa nhiều `if (module == ...)` hoặc hiểu hàng loạt DTO riêng của từng domain, abstraction đã có dấu hiệu **đưa variability ngược vào core**.

### Một hướng khác: composition hơn inheritance

```java
// Chỉ minh họa ý tưởng strategy, không phải ứng dụng chạy độc lập.
interface InvocationSelector {
    boolean matches(Invocation invocation);
}

final class AuditInterceptor {
    private final InvocationSelector selector;
    // Logic advice chung; selector được inject.
}
```

Composition phù hợp khi runtime cần kết hợp selectors linh hoạt. Abstract aspect inheritance phù hợp khi ngôn ngữ AOP hỗ trợ và việc chuyên biệt hóa có cấu trúc ổn định.

Hai hướng đều đạt mục tiêu **tách where khỏi what**, nhưng khác về runtime semantics, lifecycle, tooling và khả năng biểu đạt pointcut.

## 30. Bài tập thực hành

1. Chạy `AbstractPointcutDemo.java` và đối chiếu output của từng lời gọi.
2. Vì sao `ReadAudit` và `SensitiveDeleteAudit` không sao chép mã `try/catch` của template?
3. Thêm `Operations.update(String id)`; thiết kế concrete scope chỉ áp dụng cho update và giữ nguyên template.
4. Thử đổi `ReadAudit.matches` thành match mọi method. Những dòng log nào được thêm và vì sao?
5. Sửa `SensitiveDeleteAudit` để chỉ match `SEC-` nhưng không match `SEC-FAIL`; caller có còn nhận lỗi không?
6. Thêm overload method có hai arguments vào interface; vì sao match theo `getName()` có thể quá rộng?
7. Tạo hai proxies lồng nhau, một read selector bên ngoài và một broad selector bên trong. Dự đoán thứ tự before/after khi cả hai match.
8. Viết một named pointcut khái niệm `businessOperation`, rồi dùng nó để dựng `selectedMutation` bằng intersection.
9. Thiết kế abstract pointcut cần bind một `String id`; nếu domain dùng `UUID`, bạn sẽ thích ứng thế nào?
10. So sánh abstract pointcut với một abstract Java method; điều gì giống và khác về runtime execution?
11. Pointcut chọn annotation trên implementation method; nếu proxy chỉ có `Method` từ interface, metadata có thể khác ra sao?
12. Thử gọi raw target thay vì proxy trong demo. Vì sao selector không được gọi?
13. Tạo một target method gọi nội bộ một method khác; wrapper proxy thấy những lời gọi nào?
14. Thiết kế integration test bảo đảm method nhạy cảm được audit từ mọi đường đi bắt buộc.
15. Với một pointcut dynamic theo argument, vì sao cache kết quả chỉ theo method name có thể sai?
16. Tách một template logging ra hai concrete aspects có scopes giao nhau. Metric counting bị ảnh hưởng thế nào?
17. Thêm một aspect đo thời gian around, kiểm inclusive duration khi nested advice và target gọi lồng nhau.
18. Xây một selector theo immutable config snapshot và đổi scope runtime; phân biệt với compile-time pointcut specialization.
19. Vẽ trình tự matching → advice → target → result/throw khi scope có match và không match.
20. Thiết kế một test phân biệt “pointcut không match” với “join point chưa được instrument”.
21. Đề xuất exclusions để advice audit không tự intercept audit writer của mình.
22. Với một async method trả `CompletableFuture`, xác định phần nào được advice sync quan sát và phần nào cần observer hoàn tất.
23. Phân tích concrete scope có thể vi phạm security contract khi chỉ match method public nhưng code có đường raw/internal bypass.
24. So sánh inheritance và strategy composition cho một sản phẩm có hàng trăm policy scopes tùy tenant.
25. Lập checklist review khi sửa expression của một concrete pointcut đã dùng trong hệ thống production.

**Tự kiểm tra:** Abstract pointcut là gì, khác named pointcut thông thường ra sao? Tại sao advice đã có thể được viết đầy đủ khi pointcut chưa cụ thể? Binding nào phải được concrete specialization bảo toàn? Tập join points của hai concrete aspects có thể chồng lấn không? Vì sao một selector Java trong proxy chỉ là mô phỏng? Một pointcut cụ thể có tự vượt qua self-invocation proxy bypass không? Khi scope thay đổi, loại kiểm thử nào cần thực hiện để bảo đảm không bỏ sót audit/security?

## Kết luận

**Pointcut trừu tượng là một điểm tùy biến phạm vi của AOP:** thay vì lặp lại cùng advice cho nhiều module, ta giữ **hành vi cắt ngang dùng chung** và để mỗi concrete specialization cung cấp **điều kiện chọn join point** tương ứng. Cách thiết kế này giúp chuẩn hóa logging, timing, audit và xử lý lỗi, đồng thời đưa kiến thức domain về phạm vi áp dụng ra khỏi logic concern dùng chung.

Tuy nhiên, abstract pointcut không làm thay nhiệm vụ của pointcut matching chính xác, binding đúng kiểu, weaving/proxy coverage, aspect lifecycle, advice ordering và kiểm thử hợp đồng caller. Hãy xác định rõ **what (advice làm gì)**, **where (pointcut chọn đâu)**, **when (join point runtime nào xuất hiện)** và **how (engine nào can thiệp)**. Khi bốn yếu tố đó được kiểm chứng, pointcut trừu tượng trở thành một cơ chế tái sử dụng mạnh mà không che đi các giới hạn thực tế của AOP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-003">Kế thừa aspect khi mô hình ngôn ngữ cho phép</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa aspect khi mô hình ngôn ngữ cho phép trong Aspect/AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), aspect đóng gói những mối quan tâm cắt ngang (*cross-cutting concerns*) bằng **pointcut**, **advice** và đôi khi có trạng thái hoặc thành phần hỗ trợ. Khi nhiều aspect làm những việc gần giống nhau — cùng ghi trace, cùng đo thời gian, cùng áp một quy tắc kiểm tra nhưng lên các nhóm join point khác nhau — việc sao chép toàn bộ pointcut và advice vào từng aspect dễ tạo lỗi bảo trì.

Một số **ngôn ngữ và mô hình AOP** cho phép **kế thừa aspect** (*aspect inheritance*), nhất là thông qua **abstract aspect** làm lớp cơ sở và những concrete aspect chuyên biệt hóa. Trong mô hình như vậy, phần cha có thể định nghĩa các hành vi dùng chung và những điểm mở rộng; phần con cung cấp pointcut hoặc phương thức hỗ trợ cụ thể.

Tuy nhiên, **không phải hệ AOP nào cũng cho phép kế thừa aspect**. Có công nghệ chỉ hỗ trợ interceptor qua proxy, có công nghệ dùng cấu hình hoặc annotation, có công nghệ hỗ trợ mã khai báo aspect riêng, và có những ràng buộc đặc biệt: class nào được phép làm cha, aspect nào được phép bị kế thừa, kiểu pointcut nào là abstract, cách advice được truyền xuống, cách instance được tạo và các quy tắc precedence.

Điểm then chốt của bài: **kế thừa dùng để tái sử dụng và chuyên biệt hóa một hợp đồng AOP rõ ràng, không phải cách tự động hoán đổi advice hoặc kết hợp mọi aspect thành một cây kế thừa**. Cần phân biệt **thừa kế hành vi**, **chọn join point**, **vòng đời instance** và **thứ tự nhiều aspect chạy tại cùng điểm**.

Bài tập trung vào **Aspect/AOP cơ bản và nâng cao nói chung**. Phần cú pháp AspectJ được chú thích như một ví dụ riêng cho ngôn ngữ có hỗ trợ; phần Java 17 chạy được bằng JDK thuần mô phỏng ý tưởng **abstract base policy + concrete specializations** qua chuỗi interceptor, không tuyên bố JDK proxy là một bộ aspect weaver đầy đủ.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

1. Giải thích vì sao người ta trừu tượng hóa những hành vi cắt ngang giống nhau thay vì sao chép nhiều aspect.
2. Phân biệt **abstract aspect**, **concrete aspect**, **abstract pointcut**, advice được định nghĩa ở cha và method hỗ trợ có thể override.
3. Hiểu kế thừa aspect **phụ thuộc khả năng của mô hình ngôn ngữ**; không coi mọi bộ AOP là một hệ kế thừa giống Java class.
4. Xác định những phần nên cố định ở cha và những phần nên để con chuyên biệt hóa: tập join point, định dạng nhãn, chính sách lỗi, lựa chọn trạng thái.
5. Phân biệt **kế thừa declaration** với **advice precedence** và quan hệ lồng nhau của around advice.
6. Đánh giá **kế thừa và chính sách tạo aspect instance** theo singleton/per-target/per-flow khi công nghệ hỗ trợ.
7. Nhận diện những giới hạn của `private`/`protected`, module/package, abstract contract, constructor và trạng thái mutable.
8. So sánh inheritance với **composition, delegation, helper service, named pointcut và reusable interceptor**.
9. Biên dịch, chạy và giải thích ví dụ Java 17 với một abstract interceptor cùng hai concrete specializations, giá trị trả về và ngoại lệ được bảo toàn.
10. Thiết kế kiểm thử tránh duplicate advice, pointcut chọn sai, proxy bypass, precedence không rõ và coupling giữa aspect cha–con.

## 2. Những khái niệm phải tách riêng

| Khái niệm | Vai trò | Ví dụ |
| --- | --- | --- |
| **Aspect definition** | Khai báo mối quan tâm cắt ngang | `TracingAspect` |
| **Abstract aspect** | Bản thiết kế chưa cụ thể hóa hoàn toàn, không dùng trực tiếp như concrete instance theo mô hình hỗ trợ | `BaseTracing` |
| **Concrete aspect** | Một aspect có đủ thông tin để được áp dụng/khởi tạo theo chính sách của công nghệ | `OrderTracing` |
| **Abstract pointcut** | Hợp đồng chọn join point còn chờ phần con cung cấp điều kiện | “Nhóm operation nào phải trace?” |
| **Concrete pointcut** | Điều kiện chọn join point thực tế | “Execution của `OrderService.place(..)`” |
| **Advice** | Hành vi before/after/around gắn với điểm phù hợp | Ghi start, ghi result, giữ exception |
| **Support method/field** | Phần code và state hỗ trợ advice | Formatter, counter |
| **Instantiation policy** | Quy định số aspect instance và phạm vi mỗi instance | Shared hoặc per-flow nếu hỗ trợ |
| **Advice precedence** | Thứ tự tương đối của nhiều advice/aspect tại một join point | Auth bao ngoài Trace |
| **Structural introduction** | Bổ sung member/quan hệ type nếu công nghệ hỗ trợ | Inter-type method |
| **Composition/delegation** | Dùng một đối tượng/cơ chế khác để chia sẻ hành vi | `AuditSink` hoặc `Policy` |

**Kế thừa aspect không đồng nghĩa việc target class được kế thừa**. Ví dụ `OrderTracing extends BaseTracing` mô tả quan hệ giữa **hai định nghĩa aspect**, chứ không làm `OrderService` kế thừa `BaseTracing`.

Và **kế thừa aspect không phải kế thừa giao diện API của đối tượng đích**. Một aspect áp dụng advice ở execution của `order.place()` không tự làm `Order` có thêm method hay field; khả năng sửa cấu trúc là một cơ chế khác.

## 3. Vì sao cần trừu tượng hóa aspect?

Giả sử có ba nghiệp vụ:

```text
OrderService.place()    -> cần tracing
InvoiceService.create() -> cần tracing
ShippingService.send()  -> cần tracing
```

Nếu mỗi aspect viết lại phần ghi trước/sau và xử lý lỗi, chúng dễ khác nhau vô ý:

- Nhóm A log thời gian cả nhánh throw, nhóm B chỉ log sau return.
- Nhóm C bắt `Exception` rồi trả fallback, gây thay đổi hợp đồng nghiệp vụ.
- Nhãn log của từng nhóm không nhất quán.
- Một aspect giữ đối số trong field singleton còn những aspect khác dùng biến cục bộ.
- Khi đổi phương thức đặt correlation ID hoặc che dữ liệu nhạy cảm, phải sửa nhiều nơi.
- Một nhóm retry nhầm khi logging thất bại.

Một **base abstraction** có thể chịu trách nhiệm cho bất biến chung:

```text
on selected join point:
    begin observation
    try:
        result = proceed()
        record normal result
        return result
    catch original error:
        record failure
        rethrow original error
    finally:
        release local observation state
```

Các concrete aspects chỉ xác định **những join point nào thuộc phạm vi** hoặc **nhãn/policy nào phù hợp**. Như vậy tránh nhân bản khung điều khiển vốn khó bảo trì.

### 3.1. Trừu tượng hóa không phải gom mọi chính sách vào một cha

Một base aspect làm cả xác thực, cache, retry, transaction và audit có thể trở thành một “siêu aspect” với nhiều flag tùy chỉnh. Khi đó các concrete aspects bị kéo vào những phụ thuộc và thứ tự phức tạp, thậm chí một subclass vô tình vô hiệu kiểm tra bắt buộc.

Chỉ gom các phần thực sự có **bất biến và lifecycle chung**. Những mối quan tâm khác nhau nên được **kết hợp qua composition/precedence/coordinator** khi cần, thay vì cố ép vào cùng một cây kế thừa.

## 4. Mô hình abstract aspect và concrete aspect

Trong mô hình ngôn ngữ hỗ trợ kiểu này:

```text
AbstractObservationAspect
    |
    +-- shared advice: before/around/after
    +-- helper: normalizeName(), safeRecord()
    +-- abstract pointcut: observedOperations()
    |
    +-- OrderObservationAspect
    |     +-- observedOperations = OrderService.place(..)
    |
    +-- InvoiceObservationAspect
          +-- observedOperations = InvoiceService.create(..)
```

Phần cha định nghĩa “**quan sát hoạt động đã được chọn như thế nào**”. Phần con định nghĩa “**hoạt động nào được chọn**”. Phải kiểm tra ngữ nghĩa của công cụ để biết advice được kế thừa/buộc vào concrete pointcut theo cách nào.

### 4.1. Vì sao abstract aspect không nên áp dụng độc lập?

Abstract aspect có thể còn **abstract pointcut chưa được định nghĩa** hoặc contract chưa được đáp ứng. Nếu runtime tự xem nó là một concrete aspect bên cạnh mọi subclass, lời gọi có thể bị ghi lặp. Trong mô hình abstract-aspect inheritance phù hợp, chỉ các định nghĩa **concrete hợp lệ** mới được áp dụng như những aspect hoàn chỉnh theo instantiation policy.

Không suy rằng một abstract aspect không thể chứa advice: nó có thể có advice **đã viết sẵn** nhưng phải có concrete context để áp dụng.

### 4.2. Pointcut là hợp đồng chuyên biệt hóa

Một abstract pointcut tương tự một “hook” để concrete aspect cung cấp phạm vi join point. Điều khác với abstract Java method là **pointcut mô tả một tập sự kiện trong chương trình**, không chỉ trả một giá trị bằng một lời gọi hàm thông thường.

Nếu con định nghĩa một pointcut **quá rộng**, advice chung ở cha có thể chạy tại hàng nghìn điểm và gây đệ quy khi nó ghi log qua một method cũng được chọn. Nếu định nghĩa **quá hẹp**, những điểm cần bảo vệ có thể không khớp.

### 4.3. Concrete aspect phải đáp ứng những gì?

Tùy mô hình, phần con có thể phải:

- Cung cấp tất cả abstract pointcuts còn thiếu;
- Cài những abstract helper methods được cha yêu cầu;
- Đáp ứng quy tắc truy cập `public`/`protected`/package/private;
- Tuân thủ hạn chế về superclass và instantiation policy;
- Không tạo member signatures xung đột;
- Cung cấp một pointcut hợp lệ trong những join point được weaver hỗ trợ;
- Được đưa vào phạm vi biên dịch/weaving và đóng gói đúng.

Không được coi một khai báo aspect “extends” thành công ở source là bằng chứng mọi advice đã được áp dụng đúng trong artifact runtime.

## 5. Ví dụ cú pháp AspectJ: abstract pointcut và inherited advice

Dưới đây là **minh họa thuộc ngôn ngữ AspectJ**; đây không phải code `javac` thường biên dịch được. Giả định dự án có lớp `demo.OrderService` chứa phương thức `place(..)`, lớp `demo.InvoiceService` chứa `create(..)` và dùng compiler/weaver phù hợp.

```java
// File: BaseObservation.aj
package demo;

public abstract aspect BaseObservation {
    protected abstract pointcut selected();

    before(): selected() {
        System.out.println("OBSERVE before");
    }

    after() returning: selected() {
        System.out.println("OBSERVE returned");
    }

    after() throwing(Throwable error): selected() {
        System.out.println("OBSERVE failed "
                + error.getClass().getSimpleName());
    }
}
```

Hai concrete specializations:

```java
// File: OrderObservation.aj
package demo;

public aspect OrderObservation extends BaseObservation {
    protected pointcut selected():
        execution(* demo.OrderService.place(..));
}
```

```java
// File: InvoiceObservation.aj
package demo;

public aspect InvoiceObservation extends BaseObservation {
    protected pointcut selected():
        execution(* demo.InvoiceService.create(..));
}
```

**Ý nghĩa:**

1. `BaseObservation` là **abstract aspect** chứa advice và khai báo `selected()` chưa được giải nghĩa cụ thể.
2. `OrderObservation` và `InvoiceObservation` cung cấp pointcut ở các join point khác nhau.
3. Khung trước/sau/nhánh lỗi được định nghĩa **một lần**.
4. Vì pointcut của hai con chọn nhóm phương thức khác nhau, một lời gọi `OrderService.place(..)` thường chỉ khớp phần specialization tương ứng; nếu các pointcut **chồng lên nhau**, có thể có nhiều advice từ nhiều concrete aspects cùng chạy.
5. `after returning` chỉ biểu thị **method returned normally**; không được suy “nghiệp vụ đã commit” chỉ từ việc không có exception.

**Lưu ý ngữ pháp:** Đây là các tệp `.aj`; `pointcut`, `before`, `after`, `returning` và `execution(* ...)` thuộc cú pháp AspectJ, không phải từ khóa AOP chung hoặc code Java tiêu chuẩn. Cần kiểm tra chính xác quy tắc visibility và override abstract pointcut theo phiên bản toolchain dùng thật.

### 5.1. Một root có thể được chọn bởi hai concrete aspects

Nếu `OrderObservation` chọn `execution(* OrderService.*(..))` còn `InvoiceObservation` lại chọn một pattern chung bao gồm `OrderService.place(..)`, cùng join point có thể có hai bộ advice cùng chạy.

Kế thừa từ cùng abstract base **không tự loại các điểm giao nhau**. Công cụ có thể tạo instance cho từng concrete aspect và áp từng advice; phải xem **phạm vi pointcut và precedence**, không nghĩ rằng “chỉ chạy lớp con gần nhất”.

### 5.2. Advice inheritance không phải Java method overriding

Một khai báo `before(): selected() { ... }` không phải một Java method tên `before` để subclass dùng `@Override` rồi thay thân tùy ý. Ngôn ngữ AOP có quy tắc riêng về advice declaration, inheritance và precedence.

Nếu muốn phần con thay đổi hành vi có kiểm soát, thiết kế **method hook rõ ràng** hoặc inject một policy/delegate, thay vì giả định có thể override trực tiếp bất kỳ advice nào như overriding method.

## 6. Kế thừa và giới hạn của ngôn ngữ

Không có một luật kế thừa aspect áp dụng cho mọi công nghệ AOP:

| Câu hỏi | Cần xác minh |
| --- | --- |
| Có hỗ trợ `abstract aspect` không? | Một số ngôn ngữ có, nhiều proxy frameworks không có khai báo này |
| Aspect con được extends loại nào? | Abstract aspect, class thông thường hoặc interface tùy quy tắc |
| Concrete aspect có cho subclass tiếp không? | Có mô hình hạn chế việc mở rộng concrete aspect |
| Advice từ cha có kế thừa không? | Ngữ nghĩa khai báo và phạm vi concrete aspect |
| Pointcut abstract phải được con định nghĩa thế nào? | Tên, signature, modifier, tham số binding |
| Abstract helper Java methods có được dùng không? | Nếu ngôn ngữ hỗ trợ thành phần Java trong aspect |
| State/instantiation policy có được kế thừa không? | Theo mô hình và các ràng buộc riêng |
| Có nhiều base aspects không? | Java-class-like inheritance có thể chỉ có một superclass |
| Private method/field của cha truy cập được không? | Visibility và đặc quyền riêng của ngôn ngữ |
| Có xung đột tên giữa cha/con không? | Overload, overriding, hiding, ITD collision |
| Có cần weave subclass và target riêng? | Toolchain và giới hạn join point |
| Abstract aspect có được đóng gói thành library không? | Binary compatibility và weaver support |

Trong một mô hình như AspectJ, nên đọc **các hạn chế riêng của aspect inheritance** thay vì áp đặt hoàn toàn quy tắc subclass của class Java thường. Ví dụ, không được mặc định mọi concrete aspect được phép có concrete aspect subclass; tính hợp lệ phải xem chính sách ngôn ngữ.

### 6.1. Không nhầm nhiều interface với nhiều superclass

Java hỗ trợ một lớp kế thừa một superclass trực tiếp nhưng có thể triển khai nhiều interfaces. Điều này **không có nghĩa** một aspect có thể extends nhiều base aspects để “nhận mọi advice”. Nhiều mối quan tâm dùng chung nên có thể được **ghép các aspect riêng**, dùng helper/delegate hoặc chiến lược composition.

### 6.2. Kế thừa class thông thường không đồng nghĩa aspect hợp lệ

Một AOP language có thể cho phép aspect kế thừa những lớp hỗ trợ nhất định, nhưng không tự biến mọi `class BaseLogger` thành base aspect mang pointcut/advice. Cần biết **metadata/cú pháp** nào đưa một type vào mô hình aspect.

Tương tự, việc một Java class `extends` một helper không bảo đảm pointcut của helper tồn tại hoặc được kích hoạt.

## 7. Template method và các điểm mở rộng an toàn

Mẫu thiết kế **Template Method** là một cách tư duy hiệu quả:

```text
Base aspect:
    chọn pointcut theo contract
    bắt đầu quan sát
    invoke business exactly once
    ghi kết quả hoặc lỗi
    dọn state trong finally

Concrete aspect:
    cung cấp tập join point
    cung cấp nhãn/context hợp lệ
    có thể định nghĩa chính sách trình bày
```

**Bất biến nên khóa ở base:** advice quan sát không nuốt lỗi của target, không gọi `proceed` lặp nếu không có chủ đích, không rò state ra ngoài scope và không tự ghi dữ liệu nhạy cảm.

**Điểm mở rộng nên đủ nhỏ:** lựa chọn pointcut, nhãn, cách lấy metadata không nhạy cảm, định dạng kết quả quan sát. Nếu một subclass cần thay đổi hoàn toàn semantics retry hoặc transaction, đó có thể là **một aspect khác**, không nên gắn vào cùng base chỉ vì tên “logging”.

### 7.1. Hook được gọi từ advice phải có hợp đồng lỗi

Giả sử base advice gọi một method `formatLabel()` do con override. Nếu con ném lỗi, lỗi này xảy ra **trong advice**, có thể khiến target không chạy hoặc che kết quả target.

Một base class có thể cung cấp vùng bảo vệ với chính sách cụ thể cho **lỗi quan sát tùy chọn**, nhưng không nên bắt mọi `Throwable` quanh toàn bộ `proceed` rồi biến cả lỗi nghiệp vụ thành “logger offline”.

### 7.2. Không lấy state per-request làm field singleton

Nếu base aspect có field mutable `currentRequestId` và concrete aspect được dùng chung nhiều thread, người gọi A/B có thể ghi đè nhau. Dù field được kế thừa đúng cú pháp, **vòng đời và thread safety** vẫn phải đúng.

Với dữ liệu chỉ thuộc một invocation, dùng biến cục bộ. Nếu phải chia sẻ nhiều join points trong dynamic flow, dùng mô hình state/scope phù hợp (per-flow nếu công nghệ hỗ trợ, hoặc context rõ ràng). Kế thừa không tự cấp state độc lập cho mỗi request.

## 8. Đặt tên pointcut, member và khả năng tương thích

Trong một hệ có nhiều lớp aspect:

```text
BaseObservation
  |
  +-- FinanceObservation
  +-- WarehouseObservation
  +-- UserObservation
```

Tên `selected()`, `onSuccess()`, `counter` có thể được kế thừa hoặc che/override theo semantics của ngôn ngữ. Nếu một subclass khai báo lại một thành phần cùng tên nhưng sai signature, kết quả có thể là **một declaration mới** chứ không phải override như dự tính.

### 8.1. Tách namespace và ownership

- Định nghĩa một contract rõ cho abstract pointcut và các tham số binding.
- Đặt tên helper method nêu rõ ý nghĩa thay vì `handle`, `run` quá chung.
- Dùng `protected` cho hook thật sự dành cho subclass nếu ngôn ngữ hỗ trợ; giữ helper nội bộ là private khi thích hợp.
- Cố định các API không muốn con đổi theo khả năng của ngôn ngữ, tránh vô tình phá invariants.
- Không để nhiều concrete aspects cùng giới thiệu method/field cùng signature vào một target nếu công cụ không có quy tắc an toàn rõ.
- Kiểm tra tên method của target sau khi thư viện nâng cấp: một pointcut wildcard hoặc introduction có thể chọn/xung đột thành phần mới.

### 8.2. Overload và pointcut binding

Một abstract pointcut có thể cần binding đối số, ví dụ một `orderId`, nhưng concrete pointcut chọn những phương thức có signature khác nhau. Khi đó contract binding có thể không phù hợp:

```text
Base requires: selected(String id)
Child A chooses: work(String)
Child B chooses: work(int)
```

Nếu `Child B` không thể cung cấp một `String` theo semantics của pointcut language, không thể chỉ đổi tên tham số rồi coi là đúng. Cần **hợp đồng type rõ** hoặc dùng một abstraction trung gian cho metadata quan sát.

### 8.3. Generics và erasure

Một helper Java method trong aspect có thể chịu những luật Java về erasure: `process(List<String>)` và `process(List<Integer>)` không phải hai overload hợp lệ trong cùng class sau erasure. Khi mở rộng base helper, phải tính cả bridge methods, covariant return và visibility.

Đừng biến một xung đột Java type system thành “thứ tự aspect” — advice precedence không giải quyết duplicate method declarations.

## 9. Vòng đời instance không tự được suy từ cây kế thừa

Một điểm dễ sai: thấy `OrderObservation extends BaseObservation` rồi nghĩ runtime tạo:

```text
one BaseObservation instance + one OrderObservation instance
```

Không thể suy vậy. Trong mô hình kế thừa OO, instance subclass thường **chứa phần state được kế thừa từ base**, không nhất thiết có một object base riêng như sibling aspect. Abstract aspect thường không được áp dụng như một concrete aspect độc lập.

Với **hai concrete subclasses** `OrderObservation` và `InvoiceObservation`, chúng có thể có **những aspect instances riêng** theo instantiation policy của từng concrete aspect. Tuy nhiên phạm vi và số lượng object còn phụ thuộc công nghệ AOP.

### 9.1. Shared state trong field của base

Nếu base định nghĩa `protected long counter`, mỗi concrete aspect instance có một field state thuộc instance đó theo semantics OO phù hợp. Nhưng nếu base dùng `static long counter`, dữ liệu static có thể được chia sẻ trong phạm vi class/loader, không trở thành per-subclass counter tự động.

Với nhiều thread, counter mutable không đồng bộ có thể mất cập nhật. Dùng cấu trúc concurrency đúng khi dữ liệu là aggregate toàn cục; dùng biến invocation-local khi đó là dữ liệu một lời gọi.

### 9.2. Per-control-flow và nested roots

Một số mô hình AOP hỗ trợ instantiation theo dynamic control flow (những khái niệm như `percflow`). Khi abstract/concrete aspects có điều kiện flow, phải xác minh:

- Chính sách instantiation được khai báo trên type nào;
- Có được con kế thừa/đổi chính sách ấy không;
- Mỗi root tạo những activation/instances nào;
- Nếu root lồng nhau, state dùng instance của root nào;
- Khi root ném lỗi, binding được dỡ lúc nào;
- Có async task khác thread tham gia flow không hay cần propagation riêng.

Không tự đồng nhất “inherit aspect” với “inherit dynamic call stack”. Kế thừa là **quan hệ type**, còn cflow là **quan hệ lời gọi runtime**.

### 9.3. Khởi tạo và cleanup

Một constructor hoặc field initializer trên base có thể chạy khi tạo concrete aspect instance theo những quy tắc cụ thể, nhưng không dùng điều đó làm chứng cứ rằng mọi target invocation có một instance mới.

Tài nguyên được base tạo ra như file, socket, listener hoặc cache cần **owner, cleanup và lifecycle**. Một concrete aspect bị tắt/weaver không áp dụng không nhất thiết kích hoạt destructor hay hoàn nguyên những đăng ký đã tạo.

## 10. Kế thừa advice khác precedence giữa nhiều aspect

Giả sử có hai concrete aspects:

```text
SecurityAspect     -> guards execution of transfer
TracingAspect      -> observes execution of transfer
```

Quan hệ `TracingAspect extends BaseObservation` **không quyết định** Security sẽ bao ngoài hay nằm trong Trace. Khi cùng một join point có nhiều advice, precedence/order là hợp đồng riêng.

Trong mô hình quanh:

```text
Security(Trace(Target))
```

Security có thể từ chối trước khi Trace chạy. Còn:

```text
Trace(Security(Target))
```

Trace có thể quan sát cả lần bị từ chối. Hai cấu trúc có mục tiêu khác nhau, và kế thừa abstract tracing base **không thay đổi** mục tiêu ấy.

### 10.1. Before/after đảo chiều theo around nesting

Nếu A bao ngoài B:

```text
A.before
  B.before
    Target
  B.after
A.after
```

Muốn “A chạy trước B” phải làm rõ là **before** hay **after**. Đừng dùng sự tồn tại của `extends` như một phép topological sort cho toàn bộ advice.

### 10.2. Advice ở cha và con chồng điểm

Một concrete subclass có thể bổ sung **advice riêng**, bên cạnh advice được thừa kế theo công nghệ. Nếu cả hai chọn cùng join point, có thể xảy ra nhiều đoạn xử lý. Không suy rằng advice của subclass **override** advice trong base chỉ vì dùng pointcut giống nhau.

Muốn hành vi chắc chắn, test số lần advice/target được gọi và thứ tự, hoặc gộp bất biến trong một **template/hook có hợp đồng**.

## 11. Ví dụ Java 17 chạy được: abstract policy và hai concrete subclasses

Chương trình sau mô phỏng **tái sử dụng một khung interception qua kế thừa**:

- `AbstractTraceAspect` định nghĩa **template** cho before, return, throw và finally, đồng thời **bảo toàn đúng một lần gọi `proceed`**.
- Hai subclass `AllOperationsAspect` và `PriorityOperationsAspect` chuyên biệt hóa **điều kiện chọn** và **nhãn**.
- `ServicePort` là giao diện nghiệp vụ; `WorkService` là target thật.
- `Proxy` và chain chỉ chặn lời gọi đi **qua proxy**, không bổ sung một aspect language riêng hoặc bytecode weaving.
- Những lần gọi bắt đầu bằng `vip-` được cả hai aspects chọn, do đó **hai lớp observation chạy lồng nhau**, không phải “subclass ghi đè advice cha”.
- Khi target ném `IllegalArgumentException`, cả advice quan sát thích hợp đều truyền lại ngoại lệ gốc.

Đây là **mô hình áp dụng template-method reuse**, không tuyên bố một abstract Java class chính là abstract aspect của mọi ngôn ngữ AOP.

Tạo file `AspectInheritanceDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class AspectInheritanceDemo {
    interface ServicePort {
        String process(String name);
    }

    static final class WorkService implements ServicePort {
        @Override
        public String process(String name) {
            System.out.println("TARGET " + name);
            if ("bad".equals(name)) {
                throw new IllegalArgumentException("invalid");
            }
            return "done:" + name;
        }
    }

    @FunctionalInterface
    interface Proceeding {
        Object proceed() throws Throwable;
    }

    static abstract class AbstractTraceAspect {
        protected abstract boolean select(Method method, Object[] args);
        protected abstract String label();

        // Template is final: subclasses cannot silently omit proceed.
        public final Object around(
                Method method, Object[] args, Proceeding next)
                throws Throwable {
            if (!select(method, args)) {
                return next.proceed();
            }

            String operation = (String) args[0];
            System.out.println(label() + " before " + operation);
            try {
                Object result = next.proceed();
                System.out.println(label() + " return " + result);
                return result;
            } catch (Throwable error) {
                System.out.println(label() + " throw "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println(label() + " finally");
            }
        }
    }

    static final class AllOperationsAspect extends AbstractTraceAspect {
        @Override
        protected boolean select(Method method, Object[] args) {
            return method.getName().equals("process")
                    && method.getParameterCount() == 1;
        }

        @Override
        protected String label() {
            return "ALL";
        }
    }

    static final class PriorityOperationsAspect
            extends AbstractTraceAspect {
        @Override
        protected boolean select(Method method, Object[] args) {
            return method.getName().equals("process")
                    && args[0] instanceof String name
                    && name.startsWith("vip-");
        }

        @Override
        protected String label() {
            return "VIP";
        }
    }

    static Object callTarget(
            Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Object invokeAt(
            int position,
            List<AbstractTraceAspect> aspects,
            Method method,
            Object[] args,
            Object target) throws Throwable {
        if (position == aspects.size()) {
            return callTarget(method, target, args);
        }

        AbstractTraceAspect aspect = aspects.get(position);
        return aspect.around(method, args,
                () -> invokeAt(position + 1, aspects,
                        method, args, target));
    }

    static ServicePort wrap(ServicePort target,
                            List<AbstractTraceAspect> aspects) {
        return (ServicePort) Proxy.newProxyInstance(
                ServicePort.class.getClassLoader(),
                new Class<?>[] { ServicePort.class },
                (proxy, method, args) ->
                        invokeAt(0, aspects, method, args, target));
    }

    public static void main(String[] args) {
        ServicePort proxy = wrap(
                new WorkService(),
                List.of(new AllOperationsAspect(),
                        new PriorityOperationsAspect()));

        System.out.println("CALL " + proxy.process("ordinary"));
        System.out.println("CALL " + proxy.process("vip-1"));
        try {
            proxy.process("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 AspectInheritanceDemo.java
java AspectInheritanceDemo
```

**Stdout dự kiến chính xác:**

```text
ALL before ordinary
TARGET ordinary
ALL return done:ordinary
ALL finally
CALL done:ordinary
ALL before vip-1
VIP before vip-1
TARGET vip-1
VIP return done:vip-1
VIP finally
ALL return done:vip-1
ALL finally
CALL done:vip-1
ALL before bad
TARGET bad
ALL throw IllegalArgumentException
ALL finally
CALLER invalid
```

### 11.1. Vì sao `ordinary` chỉ chạy ALL?

`AllOperationsAspect.select` chọn mọi lời gọi `process` đúng dạng, còn `PriorityOperationsAspect.select` chỉ chọn tên bắt đầu `"vip-"`. Vì `ordinary` không phải `vip-`, VIP bỏ qua việc ghi log và gọi tiếp đúng một lần. Target chạy và trả `done:ordinary`; ALL ghi return rồi finally.

### 11.2. Vì sao `vip-1` có hai lớp observation?

Cả ALL và VIP đều chọn cùng join point. Chúng **không phải** quan hệ “ALL là instance cha của VIP”; chúng là **hai concrete interceptor instances** cùng kế thừa template `AbstractTraceAspect`.

Chuỗi đặt ALL ngoài VIP: `ALL(VIP(Target))`. Thứ tự vì vậy là ALL before, VIP before, target, VIP return/finally, ALL return/finally. Nếu đảo danh sách, thứ tự log khác nhưng hai chính sách vẫn có thể độc lập về code.

### 11.3. Vì sao ngoại lệ gốc được bảo toàn?

`WorkService.process("bad")` ném `IllegalArgumentException("invalid")`. Phần `callTarget` bóc `InvocationTargetException` của reflection, rồi `AbstractTraceAspect` ghi loại lỗi và **ném lại chính lỗi đó**. Vì VIP không chọn `bad`, chỉ ALL ghi lỗi, và caller bắt đúng `IllegalArgumentException`.

Trong ví dụ, `label()` và `System.out.println` không ném lỗi ở những đường đã xét. Với observer thực tế, lỗi từ formatter/exporter cần được xử lý riêng theo hợp đồng, không catch rộng quanh `next.proceed()` rồi bỏ luôn lỗi của nghiệp vụ.

### 11.4. Vì sao template `final` có ích?

`around` được đánh dấu `final`, nên hai subclass không thể âm thầm viết một bản điều khiển riêng làm mất `finally`, nuốt lỗi target hoặc gọi tiếp nhiều lần. Phần chúng thay đổi chỉ là **`select` và `label`**.

Đây là một **quy tắc thiết kế cho ví dụ Java**. Không suy từ nó rằng mọi ngôn ngữ AOP hỗ trợ `final advice` với cú pháp tương tự.

### 11.5. Giới hạn cần hiểu rõ

- Đây là Java thuần với **JDK dynamic proxy và chain tự viết**, không phải native aspect inheritance/weaving.
- JDK proxy chỉ áp trên lời gọi đi qua giao diện; gọi trực tiếp `new WorkService().process(...)` bypass toàn bộ logic.
- `select` của ALL đơn giản và không kiểm tra sâu kiểu tham số; với API nhiều overload cần đối chiếu đầy đủ signature/declaring type.
- Ví dụ không có field state mutable per-call; do đó tránh vấn đề ghi đè state khi proxy dùng đồng thời.
- Trong mô hình thực, có thể có nhiều loại advice, join point và instantiation policy khác mà ví dụ này không mô phỏng.
- `@Override` ở subclass override **Java helper hooks**, không override advice declaration của một AOP language.
- Nếu handler áp chung cho `Object` methods như `equals`/`hashCode`, cần hợp đồng riêng; demo chỉ gọi `process`.
- Nếu `select` hoặc `label` của subclass lỗi, target có thể không được gọi hoặc bị đổi kết quả; cần review contract.

## 12. So sánh inheritance, composition và delegation

Kế thừa hữu ích khi cần một contract **“là một phiên bản chuyên biệt của cùng hành vi chung”**. Composition phù hợp khi nhiều chính sách **khác bản chất** cần hoạt động cùng nhau.

| Cách tái sử dụng | Điểm mạnh | Cạm bẫy |
| --- | --- | --- |
| **Abstract aspect + concrete aspects** | Chia sẻ advice/hook, specialization pointcut | Phụ thuộc mạnh vào base và quy tắc inheritance của tool |
| **Named pointcut definitions** | Tái sử dụng tập join point | Không tự chia sẻ body advice hoặc lifecycle |
| **Helper service** | Dùng chung formatter/metric sink/validation logic | Vẫn phải quản lý lỗi và DI/ownership |
| **Composition of aspects** | Mối quan tâm độc lập, precedence minh bạch | Nhiều advice có thể chồng nhau |
| **Delegation to policy object** | Thay thuật toán qua interface, ít coupling với type hierarchy | Cần chọn và truyền đúng policy |
| **Interceptor/decorator chain** | Hành vi và thứ tự thấy rõ trong code/config | Phụ thuộc phạm vi interception (ví dụ proxy) |
| **Copy/paste nhiều aspects** | Ban đầu đơn giản | Dễ phân kỳ semantics khi sửa |
| **Structural introduction** | Bổ sung API/type khi được hỗ trợ | Có thể gây xung đột member, binary/API compatibility |

### 12.1. Khi nên ưu tiên abstract aspect

- Nhiều use cases chia sẻ **một khung advice với cùng luật return/throw/cleanup**.
- Khác biệt chính là **pointcut, nhãn hoặc phương thức hỗ trợ nhỏ**.
- Ngôn ngữ AOP và toolchain có **hợp đồng kế thừa rõ, được kiểm thử**.
- Bản base có thể ổn định lâu dài mà không buộc con cập nhật liên tục.
- Concrete aspects có thể được kiểm thử riêng và không tự tạo chồng pointcut nguy hiểm.

### 12.2. Khi nên ưu tiên composition

- Một aspect cần audit, một aspect cần retry, một aspect cần transaction, khác nhau về semantics.
- Hai chính sách phải bật/tắt và phiên bản hóa độc lập.
- Cần làm rõ precedence hơn là quan hệ type.
- Cần hỗ trợ công nghệ AOP không có native abstract aspect inheritance.
- Có nhiều biến thể tổ hợp; một cây subclass sẽ phát triển thành vô số lớp.
- Một phần logic nên có thể dùng trong nghiệp vụ không có AOP, qua interface/helper.

Kế thừa không phải mục tiêu; **bảo toàn hợp đồng và giảm trùng lặp có ý nghĩa** mới là mục tiêu.

## 13. Thiết kế abstract pointcut tốt

Một abstract pointcut nên có tên phản ánh **hợp đồng ngữ nghĩa**, ví dụ `businessOperations()` thay vì `anyMethod()`.

### 13.1. Định nghĩa tập chọn và tập loại trừ

Nếu base có advice ghi telemetry, nên xác định:

- Những execution/call join points nào được phép chọn?
- Code của logger/exporter có bị pointcut chọn ngược làm advice tái nhập không?
- Có chọn getter/setter vô hại hoặc hot path không?
- Có chọn overload/implementation mới không?
- Có cần thêm điều kiện type/annotation/argument binding không?
- Có đang chọn constructor, field access, internal call mà công nghệ không hỗ trợ không?

Một concrete aspect định nghĩa `selected(): execution(* *(..))` theo wildcard quá rộng có thể vô tình áp advice lên cả logger, tăng overhead hoặc stack overflow vì recursive tracing.

### 13.2. Sử dụng điều kiện phủ định có chủ đích

Một số pointcut languages cho phép `&&`, `||`, `!` hoặc named pointcuts. Có thể mô tả ý tưởng:

```text
selectedBusinessOperations()
  AND NOT insideTelemetryInfrastructure()
```

Đây là **giả mã**, không phải Java chuẩn. Đừng dùng bộ lọc phủ định mơ hồ dựa vào tên lớp tình cờ; cần test khi package refactor hoặc thêm lớp mới.

### 13.3. Kiểm tra điểm giao nhau giữa các concrete subclasses

Nếu một base có ba concrete subclasses, kiểm thử cả các **giao nhau của pointcut sets**:

```text
ObservedByOrder ∩ ObservedByInvoice
ObservedByOrder ∩ ObservedByGeneral
ObservedByInvoice ∩ ObservedByGeneral
```

Một điểm nằm trong hai tập có thể nhận advice hai lần từ hai concrete aspects theo mô hình công cụ. Nếu điều đó có chủ đích, cần tài liệu về thứ tự và dữ liệu. Nếu không, thu hẹp pointcuts hoặc chuyển thành một aspect với policy rõ.

## 14. Thư viện aspect dùng chung và module hóa

Một nhóm có thể phát hành **abstract aspect base** như thư viện, còn ứng dụng cung cấp concrete specializations. Điều này tạo khả năng tái sử dụng giữa nhiều module, nhưng thêm trách nhiệm về **phiên bản hóa**:

- Nếu base thêm một abstract pointcut mới, các concrete aspects cũ có thể trở nên không hợp lệ khi biên dịch lại.
- Nếu base đổi visibility/signature của hook, subclass cũ có thể không còn override hợp lệ.
- Nếu base đổi advice type từ before sang around, semantics return/exception/timing có thể thay đổi.
- Nếu base đổi phạm vi pointcut từ hẹp sang rộng, những điểm mới có thể bị instrument ngoài ý định.
- Nếu base đổi state từ local sang field singleton, concurrency/retention có thể xuất hiện.
- Nếu base đưa một declaration mới cùng tên member ở subclass, có thể xuất hiện conflict.
- Nếu artifact chỉ chứa base hoặc chỉ chứa concrete declaration thiếu dependency, weaving có thể thất bại hoặc advice không được áp.

Cần **compatibility matrix** giữa base version, concrete specializations và AOP compiler/weaver version; không xem “cùng major Java version” là đủ.

### 14.1. JPMS và class loader

Khi base aspect/concrete aspect/target nằm ở module hoặc class loader khác nhau, phải đánh giá ranh giới truy cập, exports/opens, reflection và khả năng weaving ở môi trường thật.

Một class có tên giống nhau trong hai loaders không tự là cùng runtime type. Một concrete aspect được biên dịch với một bản interface nhưng target runtime dùng bản khác có thể gặp `ClassCastException`, `NoSuchMethodError` hoặc lỗi liên kết tùy tình huống.

### 14.2. Không biến base thành kho giữ request

Một base aspect chia sẻ field mutable để giữ `lastArgs`, `currentUser`, `target` hoặc payload lớn có thể vô tình giữ object sau invocation. Nếu concrete aspects dùng các instance sống dài, vấn đề tồn tại ở **mọi subclass**.

Thiết kế tốt đặt dữ liệu theo invocation trong local variables, chỉ giữ aggregate state ở mức instance khi thật sự cần và có thread safety.

## 15. Xung đột override, visibility và pointcut contract

Một lớp con chỉ thật sự override một Java method nếu signature và luật Java phù hợp. Tương tự, một concrete aspect chỉ thật sự đáp ứng abstract pointcut nếu **tên, tham số/binding, modifier và loại declaration** đúng luật của ngôn ngữ AOP đó.

Sai lầm thường gặp:

- Con khai báo `selected(String)` khi cha đòi `selected()`: đây có thể là declaration khác, không đáp ứng hook.
- Con làm hẹp visibility của hook mà contract yêu cầu `protected` hoặc `public`.
- Một helper method `static` được tưởng là override instance method.
- Dùng return type không tương thích ở overridden helper method.
- Thay tên tham số/kiểu binding không khớp pointcut thực tế.
- Mong một advice do con khai báo “thay thế” inherited advice mà không kiểm tra semantics.
- Trộn member introduction vào cùng target làm collision.
- Tưởng `private` field của base tự có thể bị con truy cập trực tiếp.

**Mẹo thiết kế:** đặt `@Override` trên Java helper hooks nơi ngôn ngữ hỗ trợ và dùng compile-time validation cho abstract pointcut contracts. Đối với pointcut/advice language, dùng cú pháp của công cụ và test đúng artifact.

## 16. Kế thừa không tự giải quyết lỗi luồng điều khiển

Một around advice ở base có thể gọi `proceed`; các concrete aspects có thể kế thừa bản đó. Điều không nên làm là nghĩ chỉ cần “base chạy trước con” để bảo đảm an toàn.

### 16.1. Short-circuit

Nếu một aspect bên ngoài không gọi `proceed` mà trả cache hit, phần advice ở các aspect bên trong có thể không chạy. Kế thừa từ cùng base không ngăn điều đó.

Nếu bắt buộc có authorization trước mọi truy cập, cần đặt chính sách ở **ranh giới đáng tin**; không dựa vào việc các aspect cùng kế thừa `BasePolicy`.

### 16.2. Retry và số lần chạy

Nếu một around advice của subclass retry bằng cách gọi `proceed` nhiều lần, target có thể chạy nhiều lần. Một base trace dùng `before`/after có thể đếm **attempt** hoặc **logical request** tùy thứ tự nesting. Kế thừa không tự chuẩn hóa đơn vị đo.

### 16.3. Lỗi observer so với lỗi target

Nếu base phục vụ observer tùy chọn, nó nên cô lập **lỗi do chính observer** theo hợp đồng; không nuốt các ngoại lệ gốc từ target. Nếu concrete subclass thay formatter và formatter throw, base phải quyết định có chặn invocation không.

Nếu base phục vụ policy bắt buộc như authorization, fail-open mặc định sẽ nguy hiểm. Vì vậy một base “universal around” cho cả telemetry và guard là trừu tượng quá rộng.

## 17. Ma trận kiểm thử kế thừa aspect

| Kịch bản | Cần chứng minh |
| --- | --- |
| Một concrete subclass định nghĩa pointcut hợp lệ | Advice kế thừa chạy tại các join point mong đợi |
| Join point ngoài pointcut subclass | Không chạy inherited advice của subclass đó |
| Abstract aspect chưa được cụ thể hóa | Không được áp dụng như concrete policy độc lập sai ý định |
| Hai subclasses có pointcuts tách biệt | Không có sự kiện thừa hoặc trùng |
| Hai subclasses có pointcuts giao nhau | Số advice/instance và order rõ |
| Concrete subclass thiếu abstract pointcut | Build phát hiện contract chưa được đáp ứng |
| Helper hook override hợp lệ | Gọi đúng implementation của con |
| Hook signature/visibility sai | Compile/build phát hiện thay vì âm thầm tạo hàm khác |
| Target return bình thường | Advice giữ đúng result |
| Target throw | Advice giữ nguyên exception/cause theo hợp đồng |
| Observer helper throw | Chính sách xử lý lỗi riêng, không nuốt target |
| Pointcut chọn overload khác | Không match ngoài ý định |
| Recursive/self-invocation | Không tạo advice loop/bypass trái thiết kế |
| Aspect state dùng đa luồng | Không lẫn dữ liệu invocation |
| Per-target/per-flow instantiation nếu hỗ trợ | Số instance và scope được xác minh |
| Precedence với aspect khác | Before/after/around order đúng |
| Proxy target gốc được gọi trực tiếp | Phát hiện đường bypass |
| Module/class loader khác | Access/linking theo hợp đồng |
| Build woven và deployment artifact | Chính artifact cuối có advice cần thiết |
| Thư viện base nâng phiên bản | Concrete specializations còn tương thích |
| Thêm một concrete aspect mới | Không làm thay semantics các subclass cũ |

### 17.1. Property tests hữu ích

Với aspect thuần quan sát, những bất biến nên kiểm tra:

- Mỗi invocation target chạy **đúng số lần** mà hợp đồng định nghĩa.
- Return/exception của target không bị thay chỉ vì logging.
- Các join point không chọn **không** sinh log.
- Không có context/ThreadLocal/registration bị giữ sau return/throw.
- Khi hai concrete aspects cùng áp, mỗi aspect được ghi theo ý định và thứ tự đã công bố.
- Dữ liệu người dùng không bị giữ trong singleton base.

### 17.2. Không chỉ test output của phần con

Một concrete aspect có thể “hoạt động” trong unit test nhưng inherited advice không thực sự được woven trong ứng dụng do build/runtime nạp lớp khác. Phải kiểm tra **độ phủ của weaver và artifact triển khai**, không chỉ đối tượng Java được khởi tạo trong unit test.

## 18. Những sai lầm thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Coi mọi AOP có aspect inheritance | API/cú pháp không tồn tại | Xác minh capability của công nghệ |
| Coi abstract aspect là một concrete singleton tự chạy | Advice chạy thừa hoặc dự đoán sai instance | Hiểu instantiation policy thực tế |
| Nhầm abstract pointcut với abstract Java method | Contract binding không hợp lệ | Áp đúng cú pháp/ngữ nghĩa AOP |
| Nghĩ con override advice bằng cách khai báo advice mới | Advice chồng nhau và số lượt chạy sai | Dùng hook được hỗ trợ, test precedence |
| Dùng `extends` để giải quyết order của nhiều aspects | Bảo mật/timing/audit sai | Quy định precedence riêng |
| Base chọn mọi phương thức rồi con không lọc | Đệ quy và overhead lớn | Abstract pointcut hẹp, test phạm vi |
| Kế thừa field `currentRequest` trong shared aspect | Race và giữ request lâu | Local/context scope phù hợp |
| Static counter được coi là per-subclass | Dữ liệu bị dùng chung ngoài ý muốn | Instance state rõ hoặc key riêng |
| Đổi signature hook không kiểm tra con | Compile/build break | Semantic versioning, contract tests |
| Để nhiều concrete subclasses chọn cùng effect | Logging/audit trùng | Kiểm tra pointcut intersections |
| Coi kế thừa abstract base là mixin vào target | Caller không có API mới | Tách structural introduction khỏi inheritance |
| Dùng proxy rồi nghĩ target đã được weave | Lời gọi trực tiếp bypass | Kiểm tra đúng đối tượng và artifact |
| Chia sẻ một base giữa guard và observer | Chính sách lỗi xung đột | Tách mối quan tâm theo bất biến |
| Không test phần throw/cleanup | Rò scope hoặc che lỗi gốc | Test return, throw, timeout/cancel khi phù hợp |
| Không tính version của AOP compiler | Nguồn chạy khác nhau giữa môi trường | Chốt toolchain và test end-to-end |

## 19. Bài tập vận dụng có đáp án

### Bài 1 — Abstract pointcut để làm gì?

Bạn có năm concrete tracing aspects có cùng logic ghi trước/sau nhưng chọn năm nhóm method execution khác nhau. Nên lặp lại advice ở cả năm?

**Đáp án:** Nếu ngôn ngữ cho phép, có thể đặt **advice chung** trong abstract aspect và để mỗi concrete aspect cung cấp abstract pointcut tương ứng. Nếu công nghệ không hỗ trợ, tái sử dụng helper/interceptor qua composition hoặc delegation.

### Bài 2 — Abstract aspect có tự áp toàn ứng dụng?

Một abstract base chỉ khai báo `selected()` chưa định nghĩa. Có nên coi nó là một aspect độc lập chạy advice với mọi điểm?

**Đáp án:** Không. Đó là một hợp đồng còn thiếu concrete specialization. Cách instantiation tùy công cụ nhưng không nên giả định runtime tạo một concrete instance từ abstract definition.

### Bài 3 — Hai con chọn cùng method

Hai concrete aspects cùng kế thừa `BaseObservation` và cả hai chọn `OrderService.place(..)`. Advice sẽ chỉ chạy một lần vì chung base?

**Đáp án:** Không thể kết luận vậy. Có thể có hai concrete aspects cùng áp lên join point. Cần kiểm tra số instance, số advice và precedence; nếu trùng không có chủ đích, sửa pointcut hoặc hợp nhất chính sách.

### Bài 4 — Advice override

Một con định nghĩa `before()` mới trong khai báo aspect để “đè” `before()` của cha. Có phải Java overriding không?

**Đáp án:** Không. Advice declarations có cú pháp/ngữ nghĩa riêng, không phải Java method cùng tên được override bằng `@Override`. Muốn một điểm thay đổi, dùng abstract pointcut hoặc method hook/strategy được ngôn ngữ cho phép.

### Bài 5 — Shared field

Base aspect có `String currentUser`; hai concrete aspects dùng cùng logic trong các thread. Có an toàn cho dữ liệu theo từng lời gọi không?

**Đáp án:** Không mặc định. Field thuộc aspect instances với lifetime cụ thể, không phải per-invocation. Có thể race/retention, đặc biệt với shared instances; dùng context ngắn hạn hoặc biến local.

### Bài 6 — Sự khác nhau với precedence

`TraceAspect extends AbstractTrace`, còn `AuthAspect` là aspect riêng. Kế thừa có bảo đảm Trace chạy sau Auth?

**Đáp án:** Không. Order giữa Trace và Auth là vấn đề **precedence/chain nesting** riêng, phải được cấu hình và kiểm thử.

### Bài 7 — Pointcut quá rộng

Con định nghĩa `selected()` để chọn cả logger được advice gọi. Nguy cơ gì?

**Đáp án:** Advice có thể tự kích hoạt lại qua logger, tạo recursion, log storm hoặc quá tải. Thu hẹp join point và loại phần hạ tầng quan sát một cách có chủ đích.

### Bài 8 — Java 17 demo

Vì sao `vip-1` in hai cặp before/return/finally, còn `ordinary` chỉ in một?

**Đáp án:** Cả `AllOperationsAspect` và `PriorityOperationsAspect` chọn `vip-1`, nhưng chỉ ALL chọn `ordinary`. Hai concrete objects chia sẻ template qua kế thừa chứ không “override advice”.

### Bài 9 — Proxy bypass

Khi gọi trực tiếp `new WorkService().process("vip-1")` thay vì qua proxy trong demo, có được hai lớp logging không?

**Đáp án:** Không. Trong demo, chain nằm trong JDK proxy. Target gốc không được weave; caller đi ngoài proxy không kích hoạt logic interceptor.

### Bài 10 — Abstract hook được sửa ở base

Phiên bản mới của base thêm một abstract method/hook bắt buộc. Điều gì cần làm với các concrete aspects cũ?

**Đáp án:** Kiểm tra compatibility và bổ sung implementation theo contract. Đây có thể là thay đổi làm hỏng source/binary compatibility và cần kiểm thử build/weaving ở tất cả module sử dụng.

### Bài 11 — Khi nào bỏ inheritance?

Một abstract aspect chứa nhiều nhánh cấu hình `isAudit`, `isSecurity`, `isCache` và các subclasses liên tục phải override cleanup lẫn retry semantics. Nên tiếp tục thêm hooks?

**Đáp án:** Nên xem lại trừu tượng. Các chính sách có bất biến/lifecycle khác nhau nên tách thành các aspects độc lập và phối hợp qua precedence/coordinator hoặc dùng composition, thay vì cố giữ một base quá rộng.

## 20. Ghi nhớ

- **Kế thừa aspect chỉ áp dụng khi mô hình ngôn ngữ/công cụ hỗ trợ.** Không mặc định mọi AOP có `abstract aspect`, `extends` hay inherited advice.
- **Abstract aspect** giúp chia sẻ logic; **concrete aspect** cung cấp pointcut/hook cụ thể theo contract.
- **Abstract pointcut là hợp đồng lựa chọn join point**, không phải một Java abstract method thông thường.
- **Advice inheritance không có nghĩa Java method overriding**; lớp con có advice mới có thể làm phát sinh thêm hành vi, cần test.
- **Kế thừa không quyết định precedence**, không tự tạo per-request instance và không thay thế pointcut coverage.
- Giữ phần bất biến về **return/throw/cleanup** ở base; để subclasses chuyên biệt hóa những gì thực sự cần thay đổi.
- State mutable của base vẫn chịu **lifetime và concurrency** của concrete instance; không lưu request trong shared field.
- Khi việc tái sử dụng không phù hợp type hierarchy, dùng **composition, delegation, helper service hoặc reusable interceptor chain**.
- Kiểm thử trên **công cụ và artifact thực**, gồm pointcuts chồng nhau, ngoại lệ, instance scope, module access, proxy bypass và nâng phiên bản thư viện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-004">Tham số hóa chính sách thay vì chép lại advice</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số hóa chính sách thay vì chép lại advice trong AOP

## 1. Bài toán: một cơ chế, nhiều biến thể chính sách

Trong **Aspect-Oriented Programming (AOP)**, một **aspect** mô-đun hóa mối quan tâm xuyên cắt bằng **pointcut** (quy tắc chọn join point) và **advice** (hành vi bổ sung tại điểm được chọn). Những concern phổ biến như ghi nhật ký, kiểm tra quyền, theo dõi hiệu năng, retry, audit, rate limiting hoặc kiểm tra đầu vào thường cần những thay đổi nhỏ theo từng nhóm thao tác.

Ví dụ cùng một cơ chế ghi nhận lời gọi:

- Với thao tác tạo dữ liệu: ghi bắt đầu, thành công và thất bại.
- Với thao tác xóa: bắt buộc ghi audit, nhưng ẩn payload nhạy cảm.
- Với thao tác đọc: có thể chỉ theo dõi một tỷ lệ nhỏ hoặc không ghi.
- Với thao tác tính toán nội bộ: chỉ ghi lỗi quan trọng.
- Với thao tác có dữ liệu nhạy cảm: chỉ ghi tên operation và mã lỗi được phép, không in secret.

Cách ngây thơ là chép toàn bộ advice:

~~~text
Aspect_AuditCreate:
    before create -> log
    after create -> log success
    on error -> log failure

Aspect_AuditDelete:
    before delete -> log
    after delete -> log success
    on error -> log failure

Aspect_AuditUpdate:
    before update -> log
    after update -> log success
    on error -> log failure
~~~

Ba đoạn gần giống nhau dễ dẫn tới:

1. **Thay đổi không đồng đều:** sửa xử lý exception ở Create nhưng quên Delete.
2. **Khác biệt ẩn:** cùng quy tắc redaction nhưng có một bản sao bỏ sót.
3. **Test bị nhân đôi:** phải kiểm tra cùng logic ở nhiều places.
4. **Precedence khó quản lý:** nhiều aspects cùng chặn một join point có thể tạo log trùng.
5. **Tăng coupling:** chi tiết triển khai advice và chính sách nghiệp vụ bị trộn lẫn.
6. **Rủi ro bảo mật:** một advice copy có thể log dữ liệu nhạy cảm sai chuẩn.

Giải pháp kiến trúc là **tham số hóa chính sách**: giữ một cơ chế interception tái sử dụng được, còn những điểm thay đổi được biểu diễn bằng **Policy/Strategy/Configuration** có hợp đồng rõ ràng.

~~~text
               Join point / operation
                        |
                        v
                Reusable advice engine
                    /         \
                   /           \
       Policy for CREATE     Policy for DELETE
        audit + redact       audit + fail-closed
                   \           /
                    \         /
                 Same around skeleton
~~~

**Ý chính:** Không biến mọi khác biệt thành một advice mới. Nếu các biến thể chỉ khác **quy tắc chọn operation, dữ liệu được ghi, ngưỡng, quyết định hoặc cách biểu diễn**, hãy đưa chúng vào cấu hình/chính sách. Nếu chúng khác **hợp đồng điều khiển luồng hoặc semantics**, cần cân nhắc các mechanisms/advice riêng thay vì nhồi tất cả vào một bộ cờ.

Bài này trình bày AOP cơ bản và nâng cao nói chung; các ví dụ Java 17 minh họa around-advice composition mà không đòi hỏi một framework ứng dụng hoặc bộ dệt cụ thể.

## 2. Phân biệt mechanism và policy

**Mechanism (cơ chế):** thao tác có tính lặp lại và chịu trách nhiệm cho vòng đời interception.

Ví dụ:

- Nhận thông tin join point đã chọn.
- Gọi proceed đúng số lần theo contract.
- Đo thời gian hoặc thu thập result/error.
- Bảo đảm cleanup trong finally.
- Gọi một policy object để quyết định cách xử lý.
- Bảo toàn exception propagation và result type.
- Cung cấp hooks trước/sau có thứ tự rõ.

**Policy (chính sách):** quyết định khác nhau giữa các hoàn cảnh:

- Operation nào được quan sát?
- Sự kiện success/failure nào cần ghi?
- Có redaction message/error/payload không?
- Ngưỡng thời gian nào cần báo cáo?
- Có được retry lỗi này không, retry bao nhiêu lần?
- Ai được phép tiếp tục execution?
- Fail-open hay fail-closed khi một dependency gặp lỗi?
- Khi nào record audit phải bền vững?

Một nguyên tắc:

~~~text
Mechanism biết CÁCH chạy an toàn.
Policy quyết định KHI NÀO và THEO QUY TẮC NÀO chạy.
~~~

Tuy nhiên ranh giới không tuyệt đối: cơ chế có thể cần các hooks hỗ trợ nhiều policies, và policy không nên điều khiển các hành động làm mất invariant cơ bản.

### 2.1. Tư duy Template Method / Strategy

Một **around advice** có thể cung cấp template:

~~~text
if policy.selects(joinPoint):
    policy.before(context)

try:
    result = proceed()
    policy.afterSuccess(context, result)
    return result
catch error:
    policy.afterFailure(context, error)
    rethrow original error
finally:
    policy.cleanup(context)
~~~

Đây là **giả mã**, không phải Java độc lập và cần thiết kế kỹ policy exception handling. Nó minh họa chỗ hành vi cố định và chỗ thay đổi.

**Strategy:** truyền một policy object hoặc function quyết định, không viết lại toàn bộ skeleton.

**Template:** thống nhất vòng đời try/catch/finally/proceed, giữ những bất biến chung.

**Configuration:** các dữ liệu đơn giản như tập operation, mức log, ngưỡng, danh sách lỗi có thể dùng immutable value object.

### 2.2. Khi nào không nên tham số hóa?

Nếu một advice để thực hiện **authorization bắt buộc** còn advice khác chỉ là **telemetry best-effort**, chúng có exception/lifecycle contracts khác nhau. Gộp vào một class có 20 boolean flags có thể khiến policy khó đọc và tạo trạng thái config không hợp lệ.

Đừng đánh đổi ba đoạn dễ hiểu thành một “siêu advice” với nhiều nhánh mà không có invariant chung. **Tái sử dụng thực chất** là giảm lặp về semantics, không chỉ giảm số dòng mã.

## 3. Trục tham số hóa thứ nhất: phạm vi chọn join point

Có ít nhất hai tầng:

1. **Pointcut selection:** engine AOP chọn các join point có thể được advise, dựa trên method/type/call/execution/annotation/runtime condition nếu được hỗ trợ.
2. **Policy selection:** sau khi đã vào mechanism, chính sách quyết định có xử lý operation cụ thể và theo mức độ nào.

Ví dụ:

~~~text
Static candidate pointcut:
  public operations in a service boundary

Runtime policy:
  create -> AUDIT
  delete -> AUDIT
  read   -> NO-OP
~~~

Một runtime operation filter không **thay thế hoàn toàn pointcut**; nó chỉ trả lời điều kiện trong vùng candidate đã bị interception.

### 3.1. Vì sao nên thu hẹp static candidate set?

Nếu weaver chặn cả triệu lời gọi helper nhỏ nhưng policy thường trả “không xử lý”, overhead kiểm tra vẫn có thể đáng kể. Nên chọn pointcut đủ hẹp khi thông tin tĩnh có thể biểu đạt requirement, rồi dùng policy cho những điều kiện còn cần tính động.

### 3.2. Pointcut động cũng có vai trò

Một số policy cần actor, args, target state hoặc dynamic control flow. Khi đó pointcut/guard có thể phụ thuộc runtime. Nhưng runtime checks có chi phí và có thể gắn với asynchronous boundaries. Đừng dùng một operation-name string filter đơn giản như bằng chứng rằng ta đã thực hiện đủ **authorization** cho mọi đường gọi.

### 3.3. Không tự gắn policy vào method name mơ hồ

Operation name có thể bị overload, rename hoặc reuse. Một mapping theo một string tự đặt cần có contract rõ, validation và tests. Với API quan trọng, sử dụng **operation identity** được quản trị, metadata type-safe hoặc explicit mapping thay vì phụ thuộc Method.toString và cấu trúc tên tùy tiện.

## 4. Trục thứ hai: quyết định hành động tại các điểm lifecycle

Một cơ chế logging tái sử dụng có thể tách:

| Giai đoạn | Cơ chế chung | Policy quyết định |
|---|---|---|
| Entry | Nhận event context | Có log attempt không? |
| Before proceed | Chuẩn bị trace / check | Log field nào, có sampling không? |
| Normal return | Nhận kết quả | Có log success không; result có được xem không? |
| Exception | Nhận lỗi | Có log failure không; redact thế nào? |
| Finally | Khôi phục context | Có emit metrics, dọn resource ra sao? |
| Commit outcome | Một boundary riêng nếu có transaction | Có ghi committed event không? |
| Async completion | Callback/completion boundary | Success của task được định nghĩa thế nào? |

Cơ chế phải giữ đúng thứ tự những bước **bắt buộc**, policy mới quyết định những gì có thể tùy biến.

### 4.1. “Không log failure” khác “nuốt failure”

Nếu policy tắt log failure, **exception vẫn phải được propagate** theo hợp đồng của target/advice. Logging policy không được biến một business failure thành return OK trừ khi aspect thực sự có trách nhiệm làm exception translation.

### 4.2. “Log success” có nghĩa nào?

After-returning của method nghiệp vụ không nhất thiết nghĩa transaction đã commit. Nếu cần sự kiện **committed**, phải quan sát transaction boundary thích hợp hoặc dùng cơ chế ghi outbox/audit có semantics bền vững; không sửa tên field trong policy để giả vờ biết trạng thái commit.

## 5. Biểu diễn Policy bằng giá trị bất biến

Một policy tốt thường là **immutable**, có validation và có tên dễ hiểu. Ví dụ:

~~~java
record Policy(
    String name,
    Set<String> operations,
    boolean logSuccess,
    boolean logFailure,
    boolean redactFailure
) {
    Policy {
        Objects.requireNonNull(name);
        Objects.requireNonNull(operations);
        if (name.isBlank()) {
            throw new IllegalArgumentException("blank-policy-name");
        }
        operations = Set.copyOf(operations);
    }
}
~~~

Đây là đoạn Java minh họa (imports được cung cấp trong chương trình đầy đủ ở phần sau). Constructor compact bảo vệ name/operations và copy tập operations thành một set không cho mutate qua reference bên ngoài.

**Điểm cần hiểu:** Java record không tự đảm bảo deep immutability nếu một component chứa mutable object. Với Set.copyOf, collection component được cố định ở mức tập các String bất biến. Nếu policy chứa một mutable collaborator, phải thiết kế riêng vòng đời/thread safety của collaborator.

### 5.1. Policy IDs

Tên/chỉ số policy phục vụ audit và diagnostics. Một id như AUDIT hay TRACE nên có nghĩa quản trị được, không dùng giá trị user input chưa kiểm chứng để chọn một policy đặc quyền.

### 5.2. Ràng buộc hợp lệ

Một số combinations phải bị từ chối khi tạo policy:

- Permission policy không được cấu hình “allow tất cả” khi thiếu policy.
- Audit bắt buộc không được vô tình disable bằng một flag logging chung.
- Redaction bắt buộc không được tùy ý tắt với dữ liệu nhạy cảm.
- Timeout/retry count phải nằm trong range hợp lệ.
- Operation identifiers không được null hoặc chứa ký tự rác.
- Policy version phải có semantics rõ cho các requests đang chạy.

**Validation tại boundary** giúp lỗi cấu hình xuất hiện sớm thay vì chỉ bộc lộ dưới traffic.

## 6. Khi flags bắt đầu bùng nổ: chuyển sang kiểu diễn tả mạnh hơn

Một record với ba booleans có thể phù hợp cho demo, nhưng 12 booleans tạo ra rất nhiều combinations khó hiểu.

Ví dụ:

~~~text
Policy(
  true, false, true, false, true, true, false, ...
)
~~~

Không ai dễ kiểm toán rằng combination này có được phép hay không.

Có thể thay bằng các lựa chọn thể hiện meaning:

~~~java
enum OutcomeMode {
    SUCCESS_ONLY, FAILURE_ONLY, BOTH, NONE
}

enum FailureDetailMode {
    REDACTED, ERROR_CLASS_ONLY
}
~~~

Đây là đoạn minh họa, không phải chương trình hoàn chỉnh. Khi cần nhiều loại policy, sử dụng sealed hierarchy, strategy interfaces hoặc các value objects riêng theo concern.

### 6.1. Tách policy data và policy behavior

Một ngưỡng 200ms, danh sách operations hoặc enum logLevel thường là dữ liệu cấu hình.

Một policy quyết định “có retry lỗi X với idempotency key Y không” có thể là một function/Strategy vì nó phải xét exception, attempt và context.

Không cố nhồi mọi quyết định phức tạp vào boolean flags chỉ để có một config object.

## 7. Java 17 runnable: một around engine, hai chính sách

Chương trình dưới đây là **Java 17 thuần**, mô phỏng reusable around advice bằng một hàm nhận:

- Operation identifier.
- Một immutable Policy.
- Continuation được biểu diễn bằng Callable.
- Cách in success/failure/exit theo policy.

Hai policy cụ thể:

- **AUDIT:** chọn create và delete, log success/failure, redact chi tiết lỗi.
- **TRACE:** chỉ chọn create, log success, không log failure.

Khi hai wrappers cùng được áp vào create, output bộc lộ **precedence lồng nhau**. Khi operation không được policy chọn, mechanism vẫn gọi continuation **đúng một lần** và không emit event. Khi continuation throw, policy có thể quyết định log hay không, nhưng exception vẫn được ném lại.

**Quan trọng:** Đây là simulation của around chain để học policy parameterization, **không phải một implementation của pointcut engine, transaction, security audit sink hoặc compiler weaving**.

Tạo file **ParameterizedAdviceDemo.java**:

~~~java
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

public class ParameterizedAdviceDemo {
    record Policy(String name,
                  Set<String> operations,
                  boolean logSuccess,
                  boolean logFailure,
                  boolean redactFailure) {
        Policy {
            Objects.requireNonNull(name);
            Objects.requireNonNull(operations);
            if (name.isBlank()) {
                throw new IllegalArgumentException("blank-policy-name");
            }
            operations = Set.copyOf(operations);
        }
    }

    static String around(String operation, Policy policy,
                         Callable<String> proceed) throws Exception {
        if (!policy.operations().contains(operation)) {
            return proceed.call();
        }

        System.out.println("enter:" + policy.name() + ":" + operation);
        try {
            String result = proceed.call();
            if (policy.logSuccess()) {
                System.out.println("success:" + policy.name() + ":" + operation);
            }
            return result;
        } catch (Exception error) {
            if (policy.logFailure()) {
                String detail = policy.redactFailure()
                    ? "redacted" : error.getClass().getSimpleName();
                System.out.println("failure:" + policy.name() + ":" + detail);
            }
            throw error;
        } finally {
            System.out.println("exit:" + policy.name() + ":" + operation);
        }
    }

    @FunctionalInterface
    interface Scenario {
        String run() throws Exception;
    }

    static void test(String label, Scenario scenario) {
        System.out.println("=== " + label + " ===");
        try {
            System.out.println("result:" + scenario.run());
        } catch (Exception e) {
            System.out.println("caught:" + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Policy audit = new Policy("AUDIT",
                Set.of("create", "delete"), true, true, true);
        Policy trace = new Policy("TRACE",
                Set.of("create"), true, false, false);

        test("create", () ->
                around("create", audit, () ->
                    around("create", trace, () -> "CREATED")));
        test("delete", () ->
                around("delete", audit, () ->
                    around("delete", trace, () -> "DELETED")));
        test("read", () ->
                around("read", audit, () ->
                    around("read", trace, () -> "READ")));
        test("error", () ->
                around("create", audit, () ->
                    around("create", trace, () -> {
                        throw new IllegalStateException("secret-disk-path");
                    })));
    }
}
~~~

Biên dịch và chạy với JDK 17:

~~~bash
javac --release 17 ParameterizedAdviceDemo.java
java ParameterizedAdviceDemo
~~~

**Stdout đã biên dịch/chạy kiểm chứng:**

~~~text
=== create ===
enter:AUDIT:create
enter:TRACE:create
success:TRACE:create
exit:TRACE:create
success:AUDIT:create
exit:AUDIT:create
result:CREATED
=== delete ===
enter:AUDIT:delete
success:AUDIT:delete
exit:AUDIT:delete
result:DELETED
=== read ===
result:READ
=== error ===
enter:AUDIT:create
enter:TRACE:create
exit:TRACE:create
failure:AUDIT:redacted
exit:AUDIT:create
caught:IllegalStateException
~~~

### 7.1. Một mechanism được cấu hình khác nhau

Không có phiên bản khác nhau của around method. Cả AUDIT và TRACE dùng **cùng skeleton**, nhưng operation sets và flags làm behavior khác.

- Create: hai policies cùng active.
- Delete: chỉ AUDIT active.
- Read: cả hai policies skip việc logging nhưng vẫn proceed.
- Error: TRACE không log failure; AUDIT log redacted detail.

Điều này thể hiện ưu điểm rõ nhất của **policy parameterization**: sửa cách catch/rethrow hoặc cleanup ở đúng một mechanism.

### 7.2. Error message nhạy cảm không bị in

Business exception có message "secret-disk-path". AUDIT redact nên chỉ in **failure:AUDIT:redacted**. Code test() chỉ in exception class, không in message.

**Nhưng đây chỉ là redaction tối giản ở một log line.** Không có đảm bảo toàn bộ hệ thống không log secret ở logger khác, stack trace collector hoặc diagnostic handler. Redaction thực tế cần xem schema fields, access control, retention, exceptions và structured logging.

### 7.3. TRACE không log lỗi, nhưng vẫn throw

Câu lệnh catch trong around luôn rethrow error sau khi policy xét logging. Vì thế caller cuối in caught:IllegalStateException. Việc cấu hình logFailure=false **không thay kết quả nghiệp vụ**.

### 7.4. Finally chạy từ trong ra ngoài

Khi create thành công, exit TRACE chạy trước exit AUDIT. Khi lỗi, finally của TRACE vẫn chạy rồi AUDIT thấy exception. Điều này minh họa precedence của **hai wrapper lồng nhau**.

### 7.5. Vì sao ví dụ dùng Callable?

Callable<String> biểu diễn một continuation có thể throw Exception, cho phép Java demo mà không cần công cụ weave. Trong AOP thực, proceed có contract riêng với kiểu trả về, arguments, exception propagation và số lần gọi.

### 7.6. Vì sao demo chưa đủ để production?

- System.out không phải audit sink bền vững.
- Tập operation string chưa là type-safe pointcut.
- Không có request context, data classification hoặc policy registry.
- catch(Exception) không catch mọi Throwable/Error.
- Không có transaction commit semantics.
- Policy selection chưa giải quyết authentication/authorization.
- Không có runtime refresh, metrics, async propagation hoặc sampling.
- Không xử lý logger failure thay đổi exception gốc ở các công cụ logging thực.

Chương trình chứng minh **khả năng tái sử dụng cơ chế**, không chứng minh tính đúng của một sản phẩm audit/security hoàn chỉnh.

## 8. Tách cơ chế khỏi concern-specific semantics

Một logging advice và một retry advice đều có thể dùng around, nhưng **không nên dùng một tham số retryCount trong logging engine để làm nó ngầm retry target**.

Ví dụ retry có contract:

~~~text
around retry:
    for attempt in 1..maxAttempts:
        try:
            return proceed()
        catch retriableError:
            decide delay/backoff
    throw lastError
~~~

Ngược lại logging có contract:

~~~text
around logging:
    record attempt
    try:
        result = proceed()
        record completion
        return result
    catch error:
        record failure
        rethrow
~~~

Nếu trộn chúng thành một advice với nhiều flags, dễ vô tình biến một request thành nhiều executions hoặc che exception sai policy.

Một lựa chọn tốt là **một generic around template cho instrumentation**, cùng các **Strategy policies theo concern**, còn Retry/Transaction/Security giữ boundaries rõ.

## 9. Thiết kế interface chính sách thay vì nhiều bản sao advice

Nếu logic quyết định phức tạp hơn vài giá trị:

~~~java
interface EventPolicy {
    boolean selects(OperationContext context);
    Event toSuccessEvent(OperationContext context, Object result);
    Event toFailureEvent(OperationContext context, Throwable error);
}
~~~

Đây là code minh họa, cần định nghĩa OperationContext và Event.

Một reusable advice chỉ gọi policy khi tới giai đoạn tương ứng. Concrete policies có thể là:

- SecurityAuditPolicy.
- PerformanceEventPolicy.
- BusinessOperationTracePolicy.

Nhưng **không phải mọi EventPolicy implementation đều có cùng bảo đảm độ bền**. Mechanism cần nêu rõ event sink, failure policy, thread safety và versioning.

### 9.1. Có nên tạo class riêng cho từng policy?

Nếu khác biệt chỉ là dữ liệu, tạo một instance immutable cấu hình thường đủ. Nếu khác biệt là thuật toán (vd. policy threshold theo percentile hoặc exception classifier nhiều rules), class/Strategy riêng có thể rõ hơn.

Phân biệt **reuse behavior** và **reuse data shape** để tránh cả duplicate code lẫn một switch statement quá lớn.

### 9.2. Function parameters

Java có thể truyền Predicate, Function, BiPredicate hoặc functional interfaces riêng để cấu hình policy. Khi policy phải được audit, versioning hoặc serialization, một named class/record có thể dễ theo dõi hơn một lambda vô danh.

Không tùy tiện serialize arbitrary lambdas chỉ để lưu policy; một declarative policy schema có thể dễ quản trị và validate hơn.

## 10. Phân biệt pointcut tái sử dụng và advice tái sử dụng

Một số AOP languages cho phép **named pointcuts**, **abstract pointcuts** hoặc **abstract aspects** để tách nơi nào được match khỏi hành vi áp dụng.

Về mặt kiến trúc:

~~~text
Abstract mechanism:
    around(selectedOperations):
        common intercept logic

Concrete binding A:
    selectedOperations = calls for billing
    policy = billingAudit

Concrete binding B:
    selectedOperations = calls for inventory
    policy = inventoryAudit
~~~

Đây là giả mã để biểu diễn thiết kế, không phải cú pháp hợp lệ chung của mọi AOP language.

### 10.1. Named pointcut

Một pointcut được đặt tên giúp tránh lặp lại một expression dài trên nhiều advices. Tuy nhiên trùng pointcut và trùng advice body là **hai vấn đề khác**:

- Named pointcut giảm lặp quy tắc chọn join points.
- Shared advice mechanism/policy Strategy giảm lặp xử lý return/error/cleanup.

### 10.2. Abstract aspect hoặc template

Một abstract aspect có thể định nghĩa skeleton, còn concrete aspect chọn pointcut hoặc policy trong phạm vi ngôn ngữ hỗ trợ. Các chi tiết như method override, kiểu generic và context binding khác nhau giữa engines.

Không suy rằng mọi AOP language đều cho override pointcut như override Java method; kiểm tra semantics công cụ nếu dùng thật.

### 10.3. Một aspect hay nhiều aspect instances?

Một reusable mechanism không nhất thiết chỉ có một aspect instance. Nếu engine tạo aspect per-target hoặc per-flow, mỗi instance có thể có policy/config riêng. Ngược lại, shared aspect instance có thể dùng cùng immutable Policy cho nhiều threads.

**Policy object bất biến** giúp tránh race ở cấu hình, nhưng không bảo đảm state của target hay sink cũng thread-safe.

## 11. Context nên là input tường minh

Chính sách đôi khi cần:

- Operation ID.
- Actor/tenant đã được xác thực.
- Method metadata.
- Target identity.
- Result category.
- Exception class.
- Correlation/request ID.
- Attempt number.
- Elapsed time.
- Policy version.

Đóng gói vào một context object với hợp đồng rõ:

~~~java
record OperationContext(
    String operation,
    String correlationId,
    String actorId,
    int attempt
) {}
~~~

Đây là snippet minh họa, không phải implementation xác thực actor. Chỉ nên đưa dữ liệu cần cho policy; không biến context thành một global mutable bag.

### 11.1. Thông tin đáng tin và không đáng tin

Một request parameter như "role=admin" do client gửi không tự là authenticated actor role. Security policies cần context có nguồn tin cậy, được kiểm tra authorization theo từng resource và được áp trước side effect không được phép.

Tham số hóa security bằng một boolean "allowed" do caller có thể tự điều khiển là **đưa quyết định quyền cho bên không đáng tin**.

### 11.2. Capture immutable snapshot

Nếu policy cần dùng cùng actor, tenant và policy version xuyên suốt operation, capture một immutable snapshot tại boundary phù hợp. Đọc các mutable global fields tại nhiều điểm có thể tạo inconsistent decisions trong cùng invocation.

## 12. Policy lookup: tĩnh, theo operation hay theo invocation?

Có một số cách resolve policy:

| Cách | Đặc điểm | Rủi ro |
|---|---|---|
| Inject immutable Policy cố định | Dễ test, không phải lookup mỗi call | Khó thay đổi runtime nếu cần |
| Immutable map operation → Policy | Chọn theo key, cấu hình rõ | Key mapping sai hoặc missing policy |
| Registry có version | Có thể reload policy | In-flight invocation dùng version nào? |
| Policy function theo context | Linh hoạt theo actor/operation | Cần threat model và purity |
| Annotation/metadata mapping | Dễ gắn với type/method | Reflection/caching/class loader cần quản lý |
| Remote policy service | Quyết định cập nhật tập trung | Network, timeouts, failure semantics |

Một lời gọi nên xác định rõ **policy được resolve lúc nào**, có được thay đổi giữa before và after hay không, và exception nào đến từ policy lookup.

### 12.1. Missing policy: fail-open hay fail-closed?

- Logging best-effort thiếu config có thể dùng một conservative default theo chính sách vận hành.
- Authorization hoặc audit bắt buộc thiếu config có thể phải **fail-closed**.
- Một registry lỗi không nên tự động biến "no policy" thành "allow" đối với những operation nhạy cảm.

Default policy là **quyết định nghiệp vụ/bảo mật**, không chỉ kỹ thuật.

### 12.2. Policy version cho trace

Ghi policy ID/version nếu cần audit. Khi điều tra hai requests xử lý khác nhau, biết hai invocations dùng rule versions nào thường quan trọng hơn biết chỉ method name.

## 13. Dynamic reload và tính nhất quán trong một lời gọi

Nếu application muốn đổi policy mà không khởi động lại, một registry có thể giữ **volatile reference đến immutable PolicySet**:

~~~text
PolicySet version 5
    |
reload creates a NEW immutable PolicySet version 6
    |
publish new reference atomically
~~~

Một around advice có thể capture snapshot tại entry và dùng snapshot cho toàn bộ before/success/failure/finally.

Nếu mỗi hook lại đọc registry mới, một invocation có thể:

~~~text
before under version 5
after under version 6
~~~

Điều này có thể gây event schema khác nhau hoặc exception policy inconsistency.

### 13.1. Atomic reference không thay thế compound correctness

Dùng volatile/AtomicReference để publish immutable snapshot giúp visibility, nhưng nếu version update phải tương quan với resource state, transaction hoặc distributed policy propagation, cần giao thức consistency phù hợp. Đừng suy “có volatile” là đã bảo đảm cùng version trên mọi processes.

### 13.2. Không mutate PolicySet đang được dùng

Nếu một shared HashMap policy bị mutate trực tiếp trong lúc nhiều invocations đọc, có data race hoặc inconsistent view. Dùng immutable copy + atomic swap, hoặc một concurrent map và semantics cập nhật rõ, thay vì mutate object đang được chia sẻ.

## 14. Policy composition: nhiều quy tắc đồng thời

Một operation có thể cần audit, tracing và performance metrics. Hai lựa chọn:

1. Nhiều aspects khác nhau, mỗi aspect có policy riêng.
2. Một instrumentation mechanism kết hợp nhiều event policies theo trình tự có kiểm soát.

Ví dụ:

~~~text
AuditPolicy + TracePolicy + PerformancePolicy
              |
              v
        CompositePolicy
              |
              v
         Around engine
~~~

Để composition an toàn cần quyết định:

- Thứ tự gọi các policies.
- Mỗi policy có thể throw không?
- Khi policy A từ chối, có gọi policy B không?
- Có phát trùng record không?
- Lỗi của telemetry policy có làm fail nghiệp vụ không?
- Có resource state riêng cần cleanup không?
- Có được reorder vì tối ưu hiệu năng không?

### 14.1. Không trộn policy quyết định với policy chỉ quan sát

Authorization policy có thể **từ chối proceed**. Logging policy lý tưởng **chỉ quan sát** và không đổi business result trừ khi contract về audit failure quy định rõ.

Khi một CompositePolicy có cả hai, phải nêu rõ stage ra quyết định trước khi làm side effects, và stage instrumentation sau. Không giao toàn quyền gọi proceed cho một plugin policy không đáng tin.

### 14.2. Precedence của nhiều around advices

~~~text
Audit(Trace(Business))
~~~

Khi thành công: Audit.before → Trace.before → Business → Trace.after → Audit.after.

Khi lỗi: inner finally/exception handler chạy trước outer. Đổi thứ tự có thể làm audit thấy hoặc không thấy transaction commit exception, hoặc làm đo latency bao gồm/không bao gồm logging. **Tham số hóa không loại bỏ nhu cầu xác định precedence rõ ràng.**

## 15. Truyền result và exception cho policy an toàn

Một policy logging thường không cần full result object. Nếu toSuccessEvent nhận result và gọi toString vô điều kiện, có thể:

- Lộ dữ liệu nhạy cảm.
- Gây chi phí serialization lớn.
- Kích hoạt lazy-loading hoặc side effects.
- Gặp exception trong toString.
- Tạo recursion nếu logger cũng được aspect match.

Thay vì truyền raw result cho mọi policy, có thể tạo một **whitelisted result summary** theo domain và data classification.

### 15.1. Exception policy

Một failure policy có thể chọn:

- Log exception class, không log message.
- Log error code cố định.
- Redact stack trace paths/secrets.
- Group errors để đo metrics.
- Retry một nhóm lỗi được xác định.
- Translate exception ở một boundary được công bố.

**Không mặc định mọi error cần log raw stack trace.** Một policy rõ giúp mọi operations áp cùng redaction rules.

### 15.2. Giữ lỗi gốc khi policy thất bại

Nếu logging policy throw trong lúc target đã throw, nó có thể che original exception. Có thể yêu cầu best-effort logging failure, giữ original error và lưu secondary diagnostics theo contract; audit bắt buộc có thể cần fail-closed.

Với try/finally, cleanup có thể throw và thay original error nếu không quản lý. Khi có AutoCloseable, Java try-with-resources cung cấp suppressed exceptions cho lỗi close phát sinh đồng thời với lỗi body.

Không có một exception policy thích hợp cho tất cả concerns: định nghĩa riêng cho logging, security, transaction, retry và audit.

## 16. Retry policy là ví dụ cần phân biệt semantics

Một retry policy có thể gồm:

- Maximum attempts.
- Backoff strategy.
- Predicate chọn retriable exception.
- Deadline/cancellation.
- Idempotency/side-effect rules.
- Attempt ID/correlation.
- Transaction per attempt.

Cơ chế retry dùng policy cho **điều kiện tiếp tục hoặc dừng**. Nó có thể gọi proceed **nhiều lần** nếu AOP engine hỗ trợ và contract cho phép.

### 16.1. Không dùng lại advice code bằng cách copy/paste

Nếu ba operations retry cùng một điều kiện lỗi và chỉ khác maxAttempts, nên có một retry mechanism và ba policies. Khi sửa overflow/backoff/cancellation, chỉ thay một implementation.

### 16.2. Nhưng policy không thể tạo idempotency từ con số

Một thao tác charge đã gửi request tới hệ thống bên ngoài không trở nên an toàn để retry chỉ vì RetryPolicy.maxAttempts = 3. Cần idempotency key và xác định result của attempt trước.

### 16.3. Transaction scope và retry order

~~~text
Retry(Transaction(Business))
~~~

Có thể tạo transaction mới mỗi attempt nếu transaction aspect mở scope mỗi lần proceed.

~~~text
Transaction(Retry(Business))
~~~

Có thể retry nhiều lần bên trong một transaction đang bị rollback-only hoặc giữ locks lâu. Cần chọn precedence phù hợp invariant. Một policy parameterization tốt phải làm rõ **nơi cơ chế retry bao transaction**.

## 17. Performance policy và ngưỡng đo

Một timer advice có thể chia sẻ cùng skeleton, thay đổi policy về:

- Operation nào được đo.
- Ngưỡng slow call (ms).
- Success/failure event schema.
- Sampling rate.
- Tagging/correlation.
- Độ phân giải và clock source.

### 17.1. Đo đúng boundary

Nếu muốn đo elapsed time của Business, ghi time ngay trước/ sau proceed. Nếu muốn đo **toàn bộ transaction completion**, timer phải bao cả transaction wrapper. Không chỉ sửa threshold để giải quyết boundary semantics.

### 17.2. Clock

Với elapsed duration trong Java, ưu tiên một monotonic time source như System.nanoTime thay vì lấy chênh lệch wall-clock timestamps. Policy định nghĩa threshold, mechanism chọn clock và thực hiện đo nhất quán.

### 17.3. Sampling và chi phí

Nếu sampling policy chỉ chọn 1% requests, có thể giảm cost của expensive event serialization, nhưng runtime interceptor/check vẫn có chi phí. Nếu metric/audit bắt buộc thì không được sampling làm mất sự kiện quan trọng theo policy.

Sampling phải xác định theo request ID, trace ID hoặc probabilistic random để không tạo bias ngoài ý muốn; không dùng một shared mutable Random không an toàn theo cách sai.

## 18. Authorization policy: mức độ nguy hiểm khi tham số hóa sai

Policy tái sử dụng có thể làm authorization rules dễ kiểm toán, nhưng cũng có thể làm **một sai sót áp dụng rộng**.

Các nguyên tắc:

- Policy decision phải dựa identity đã authenticated, operation và resource.
- Một caller không đáng tin không được chọn policy ID để nâng quyền.
- Missing policy xử lý fail-closed khi yêu cầu.
- Denial trước side effect không được phép.
- Resource-level authorization có thể cần kiểm tra bên trong transaction để tránh TOCTOU.
- Việc log denial phải không rò token, password hoặc PII.
- Một authorization policy có thể ném exception/return decision, nhưng không được tùy tiện swallow denial ở advice ngoài.

### 18.1. Policy reuse không thay scope coverage

Một pointcut chỉ match các calls đi qua proxy có thể bị bypass bởi một direct target reference hoặc self-invocation tùy mechanism. Dù policy authorization hoàn hảo, operation không được intercept sẽ không được bảo vệ bởi policy đó.

Vì vậy review phải kiểm tra **tập join points được bao phủ**, không chỉ nội dung policy object.

### 18.2. Security policy per tenant

Nếu mỗi tenant có rule khác, sử dụng mapping tenant ID → immutable policy có xác thực và authorization của tenant context. Không để mutable singleton field currentTenant đổi theo thread, gây context mixing giữa requests.

## 19. Thread safety và state ownership

Một reusable advice có thể được nhiều threads gọi đồng thời. Chính sách bất biến thường dễ chia sẻ nhưng mechanism vẫn có thể chứa state:

~~~text
Shared around engine
    Policy immutable     -> safe to share as value
    Mutable event buffer -> needs synchronization or thread-safe sink
    Current request ID   -> must not be shared mutable field
    Retry attempt index  -> per invocation local variable
~~~

Nếu advice lưu currentPolicy vào một mutable field để đổi theo mỗi invocation:

~~~text
Thread A sets currentPolicy = AUDIT
Thread B sets currentPolicy = TRACE
Thread A proceeds expecting AUDIT, sees TRACE
~~~

Đây là context mixing. Tốt hơn là truyền Policy/reference qua local variable hoặc per-invocation context.

### 19.1. Per-target aspect cũng có thể concurrent

Một aspect instance gắn mỗi target không mặc nhiên thuộc một thread. Một target có thể được nhiều requests gọi. Introduced mutable fields, caches hoặc counters cần thread-safety tùy yêu cầu.

### 19.2. ThreadLocal không thay policy registry

ThreadLocal có thể lưu request context cho helper cùng thread, nhưng không phải nơi mặc định tốt để lưu mọi policy. Nó cần cleanup trong finally và không tự theo async work. Một policy immutable/versioned trong registry với per-request snapshot thường rõ hơn.

### 19.3. Async propagation

Nếu advice trả CompletableFuture khi task chưa hoàn tất, after returning của wrapper có thể chỉ nhìn thấy future được tạo. Chính sách đo completion của task phải gắn vào completion boundary, truyền context hợp lệ và cleanup state khi task kết thúc; không coi scheduling success là business success.

## 20. Lựa chọn cấu hình tĩnh hay policy nạp runtime

| Tiêu chí | Compile/configure-time policy | Runtime-loaded policy |
|---|---|---|
| Khi đổi rule | Thường cần reconfigure/build/deploy | Có thể reload |
| Type safety | Có cơ hội kiểm tra nhiều thứ sớm | Cần schema validation |
| Độ phức tạp runtime | Thường thấp hơn | Lookup, versioning, consistency |
| Khả năng audit | Dễ gắn với artifact version | Cần policy ID/version logs |
| Rủi ro | Rule lỗi lan qua deploy | Reload sai giữa chừng, stale nodes |
| Testing | Fixtures cố định | Cần old/new/in-flight cases |

Không phải càng dynamic càng tốt. Nếu policy là invariant quan trọng và ít đổi, explicit immutable config lúc deploy có thể an toàn và dễ hiểu hơn remote policy resolution mỗi lần gọi.

### 20.1. Runtime policy thay đổi ngoài ý muốn

Nếu registry dùng một Map mutable và nhiều threads update trực tiếp, có thể thiếu happens-before hoặc thấy inconsistent data. Dùng snapshot publication hoặc synchronization đúng.

### 20.2. Không nạp code tùy ý từ nguồn không đáng tin

Nếu policy dưới dạng expressions/scripts hoặc plugins, cần sandbox/security review, validation, resource limits và khả năng audit. Cơ chế advice có quyền chặn/mở business operation nên policy code có thể thành attack surface.

## 21. Thiết kế để tránh policy explosion

Một hệ thống quá tham số hóa có thể tạo:

~~~text
Policy A -> 12 flags
Policy B -> 12 flags
Policy C -> 12 flags
... mọi tổ hợp đều có thể được tạo
~~~

Nếu flags tương tác, số combinations tăng nhanh và khó test hết. Giải pháp:

1. Nhóm cấu hình theo concern: logging, retry, security, metrics.
2. Sử dụng enums/named modes cho mutually exclusive choices.
3. Ràng buộc combinations không hợp lệ trong constructor/factory.
4. Dùng sealed policies nếu cần các variants có cấu trúc khác nhau.
5. Tách aspects có **control-flow contract** khác nhau.
6. Định nghĩa immutable defaults được review, không phụ thuộc những field optional khó hiểu.
7. Có unit tests cho từng policy mode và integration tests cho composition.

**Tái sử dụng không có nghĩa mọi thứ phải có cùng interface.** Một interface quá tổng quát như Policy.execute(Object[]) rất khó type-check, document và test.

## 22. So sánh trước và sau refactor

### 22.1. Trước: lặp advice

~~~text
CreateAdvice:
    log start
    try proceed, log success, catch log error, rethrow
    finally cleanup

DeleteAdvice:
    log start
    try proceed, log success, catch log error, rethrow
    finally cleanup

UpdateAdvice:
    log start
    try proceed, log success, catch log error, rethrow
    finally cleanup
~~~

Sửa logic cleanup hoặc redaction phải sửa ba nơi.

### 22.2. Sau: shared mechanism và policy mapping

~~~text
LoggingMechanism:
    captures invocation
    calls proceed once
    handles success/error/finally consistently

PolicyRegistry:
    create -> AUDIT_CREATE
    delete -> AUDIT_DELETE
    update -> AUDIT_UPDATE
~~~

Các policies khác nhau theo operation scope, event types, redaction và severity; tất cả dùng cùng tested execution skeleton.

### 22.3. Lợi ích được kiểm chứng thế nào?

- Số bản copy của try/catch/finally giảm.
- Một fix exception propagation áp dụng cho mọi variants.
- Unit tests policy logic độc lập với AOP weaving.
- Integration tests xác minh pointcut binding và advice precedence.
- Review security rules đơn giản hơn vì redaction policy được định nghĩa nhất quán.
- Có thể thêm operation mới bằng validated policy entry khi semantics giữ nguyên.

### 22.4. Trường hợp refactor có thể thất bại

Nếu CreateAdvice trước đây bắt exception và trả fallback còn DeleteAdvice phải rethrow, gộp thành một LoggingMechanism luôn rethrow có thể thay business contract. Cần tách concern hoặc mô hình hóa chính sách exception translation một cách tường minh, rồi kiểm tra caller contract.

Không chỉ so số dòng mã để tuyên bố refactor tương đương.

## 23. Thiết kế kiểm thử theo hai tầng

### 23.1. Unit test mechanism

Với một fake continuation có đếm số lần gọi:

- Policy không chọn operation: proceed một lần, không emit event.
- Success được chọn: proceed một lần, đúng order before/success/finally.
- Failure được chọn: exception được preserve/rethrow.
- Log failure bị tắt: exception vẫn thoát.
- Redaction bật: không có error message nhạy cảm.
- Logging sink throw: exception policy được thực hiện như thiết kế.
- Around short-circuit chỉ khi concern cho phép và có contract.
- Finally vẫn chạy khi proceed throw.
- Policy immutable không bị đổi khi caller mutate collection ban đầu.

### 23.2. Unit test policies

- Operation allowlist/denylist.
- Mode log success/failure.
- Redaction theo data classification.
- Threshold/sampling.
- Missing policy defaults.
- Policy version và validation.
- Security deny/allow theo trusted context.
- Retry classifier và max attempts.
- Invalid combinations bị reject khi khởi tạo.

### 23.3. Integration test AOP binding

- Pointcut thực sự match đúng methods/operations.
- Không áp advice sai vào helper không thuộc scope.
- Precedence giữa Audit/Trace/Transaction phù hợp semantics.
- Direct target/self-invocation không bypass bảo mật ngoài dự kiến.
- Generic/overloaded methods có pointcut đúng.
- Nested calls không tạo log trùng ngoài policy.
- Exception từ target/advice được quan sát đúng boundary.
- Async completion/context propagation đúng.

### 23.4. Regression test cho policy reload

- Request đang chạy dùng snapshot policy nào?
- Version cũ được dọn/giữ bao lâu?
- Invalid new policy có bị reject mà không phá old policy?
- Nhiều nodes có log đúng version đang dùng?
- Thay đổi config không tạo data race hoặc leak state.

## 24. Ma trận tình huống thực hành

| Tình huống | Chính sách | Behavior kỳ vọng |
|---|---|---|
| Create thành công | AUDIT, TRACE | Hai policies active theo precedence |
| Delete thành công | AUDIT | Chỉ event của AUDIT |
| Read không chọn | AUDIT/TRACE đều skip | Proceed, không emit log |
| Create throw | AUDIT redact failure | Lỗi vẫn được rethrow |
| Failure logging disabled | TRACE | Không log failure nhưng không nuốt lỗi |
| Policy config thiếu | Security fail-closed | Không tự allow operation |
| Request chứa secret | Redaction mandatory | Không log raw token/payload |
| Policy immutable | Set.copyOf | Caller không mutate selection set |
| Policy version đổi | Snapshot | Một invocation dùng version nhất quán |
| Retry attempt | RetryPolicy | Không vượt limit, idempotency rõ |
| Async task | CompletionPolicy | Không log task success ngay khi schedule |
| Logging sink down | Explicit failure mode | Không ngẫu nhiên che original error |
| Multiple aspects | Precedence configured | Log/transaction semantics đúng |
| Method overload | Pointcut method identity | Không lẫn các overloads |
| Direct target call | Coverage tested | Không bypass required enforcement |
| Shared mutable collaborator | Thread safety | Không race/context mixing |
| Rate limit exact | Atomic reservation | Không check-then-increment race |
| Audit committed event | Transaction-outcome policy | Không gọi success trước commit |
| New operation | Validated registry entry | Không phải copy advice skeleton |

## 25. Các lỗi thường gặp

**Lỗi 1 — Chép nguyên advice chỉ để thay một ngưỡng.** Sửa chung sẽ không đồng bộ.

**Lỗi 2 — Chỉ dùng named pointcut mà vẫn copy/paste toàn bộ advice body.** Chưa giải quyết duplication của mechanism.

**Lỗi 3 — Dùng một giant aspect có hàng chục booleans.** Tạo nhiều tổ hợp khó kiểm chứng.

**Lỗi 4 — Policy có thể gọi proceed tùy ý khi thực sự chỉ cần ghi log.** Làm thay control-flow contract.

**Lỗi 5 — logFailure=false nghĩa là nuốt exception.** Không; log selection và business error propagation độc lập.

**Lỗi 6 — Logging success đồng nghĩa transaction committed.** Không nếu boundary chưa bao commit.

**Lỗi 7 — Một immutable Policy khiến toàn bộ system thread-safe.** Target/sink/collaborators có thể mutable.

**Lỗi 8 — Dùng mutable field currentPolicy trong shared advice.** Có thể mix policies giữa threads.

**Lỗi 9 — Mỗi hook đọc một policy version khác.** In-flight invocation bị inconsistent.

**Lỗi 10 — Để user input chọn authorization policy đặc quyền.** Tạo privilege escalation.

**Lỗi 11 — Thiếu policy thì allow tất cả.** Không phù hợp với fail-closed security requirements.

**Lỗi 12 — Log raw result hoặc exception message theo một flag tiện lợi.** Có thể làm lộ dữ liệu.

**Lỗi 13 — Shared retry mechanism mà không xét idempotency.** Có thể nhân đôi side effects.

**Lỗi 14 — Gắn runtime guard vào mọi helper.** Tăng overhead candidate checking không cần thiết.

**Lỗi 15 — Runtime filter thay thế pointcut coverage.** Một method không bị intercept sẽ không qua policy.

**Lỗi 16 — Gộp audit bắt buộc và telemetry best-effort thành cùng failure mode.** Hai concerns có contracts khác nhau.

**Lỗi 17 — Config loading không validation.** Lỗi chỉ xuất hiện ở runtime sau deploy.

**Lỗi 18 — Tái sử dụng advice mà không kiểm soát precedence.** Thứ tự có thể làm log outcomes sai.

**Lỗi 19 — Thay operation name mà quên policy registry.** Coverage có thể bị mất im lặng.

**Lỗi 20 — Chỉ unit test policy object, không test actual weaving/proxy boundary.** Có thể không hề có invocation được bảo vệ.

## 26. Quy trình thiết kế một advice tái sử dụng

1. **Liệt kê các bản advice đang trùng nhau** và chỉ ra những dòng khác nhau.
2. **Xác định invariant chung**: số lần proceed, exception propagation, resource cleanup, event semantics.
3. **Tách những khác biệt thành policy fields hoặc Strategy methods**.
4. **Chọn representation có nghĩa**: named enum, record, interface thay vì nhiều flags lẫn lộn.
5. **Validate policy khi tạo** và document defaults/missing policy behavior.
6. **Thu hẹp pointcut candidate set** bằng tiêu chí tĩnh nếu có thể.
7. **Xác định runtime policy lookup** theo operation và trusted context.
8. **Capture immutable policy/context snapshot** mỗi invocation khi cần.
9. **Giữ mechanism stateless** hoặc bảo vệ shared mutable collaborators.
10. **Thiết kế exception policy** rõ cho business failure, policy failure, logger failure.
11. **Kiểm tra các concerns làm đổi control flow** như retry/security riêng.
12. **Cấu hình precedence** với transaction, logging, security và các aspects khác.
13. **Viết unit tests** cho mechanism và cho từng policy độc lập.
14. **Viết integration tests** cho pointcut, weaving, proxy, self-invocation, async.
15. **Đo chi phí runtime** sau khi đã chứng minh correctness.
16. **Ghi decision record** để người sau biết vì sao sử dụng parameterization và ranh giới của nó.

## 27. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** Ba advice khác nhau chỉ ở danh sách methods và mức log, nên chọn gì?

**Đáp:** Xem xét một reusable advice mechanism với ba policy configs, sau khi chứng minh lifecycle/exception semantics chung.

**Câu 2.** Một named pointcut có giải quyết việc ba advice copy cùng try/catch không?

**Đáp:** Không hoàn toàn; nó tái sử dụng pointcut selection, còn thân advice cần mechanism abstraction.

**Câu 3.** Policy logFailure=false có nghĩa throw từ target được nuốt không?

**Đáp:** Không. Chính sách ghi log độc lập với exception propagation.

**Câu 4.** Tại sao nên dùng Set.copyOf với tập operations?

**Đáp:** Để policy không bị mutate qua collection reference do caller cung cấp; đây là một phần của immutable policy design.

**Câu 5.** Java record có mặc định deep immutable không?

**Đáp:** Không, object references bên trong vẫn có thể trỏ mutable objects.

**Câu 6.** Nếu có 20 booleans trong policy, nên làm gì?

**Đáp:** Xem xét enum/named modes, Strategy hoặc tách concerns; validate invalid combinations.

**Câu 7.** Một policy registry chỉ return "allow" khi không tìm thấy config có an toàn không?

**Đáp:** Không với authorization bắt buộc; missing policy cần explicit fail-closed contract nếu yêu cầu.

**Câu 8.** Vì sao policy version cần được capture đầu invocation?

**Đáp:** Để before/after/failure trong cùng operation không vô ý dùng rules khác nhau sau reload.

**Câu 9.** Nếu policy logging throw khi business đã throw, rủi ro là gì?

**Đáp:** Có thể che original error hoặc đổi transaction behavior; cần xử lý failure của sink theo contract.

**Câu 10.** Có thể dùng cùng mechanism cho Logging và Retry không?

**Đáp:** Có thể chia sẻ vài utilities, nhưng retry có thể gọi proceed nhiều lần còn logging thường quan sát một continuation; không gộp khác biệt control flow vô tội vạ.

**Câu 11.** Vì sao đặt policy lookup bên trong shared mutable field currentPolicy nguy hiểm?

**Đáp:** Các threads có thể ghi chồng và áp sai policy của request khác.

**Câu 12.** Dynamic policy selection có thay pointcut không?

**Đáp:** Không hoàn toàn; nó chỉ xử lý những joins mà mechanism thực sự được áp vào.

**Câu 13.** Nếu chỉ cần đo elapsed time toàn operation, có cần copy một advice cho từng method không?

**Đáp:** Thường không; một around mechanism và threshold/operation policy là phù hợp.

**Câu 14.** Ghi success sau method return có chứng minh DB commit không?

**Đáp:** Không nếu transaction completion nằm ngoài boundary được quan sát.

**Câu 15.** Làm sao kiểm tra refactor policy giữ behavior cũ?

**Đáp:** Characterization tests trước/sau, unit tests cho mechanism/policies và integration tests cho pointcut/precedence/exception paths.

**Câu 16.** Tái sử dụng mechanism có buộc mọi policy cùng failure mode không?

**Đáp:** Không. Failure mode là contract phải cấu hình/tách riêng một cách rõ, đặc biệt giữa telemetry và audit/security.

## 28. Sơ đồ ghi nhớ

~~~text
     Nhiều advice có logic gần giống nhau?
                    |
                    v
         Khác nhau do POLICY hay SEMANTICS?
               /                \
           POLICY              SEMANTICS
              |                   |
              v                   v
      Shared around engine      Tách mechanisms
              |                / security / retry
              v
      Validated immutable
         Policy objects
        /       |       \
       v        v        v
   Selection  Events  Redaction
       \        |       /
        +-------+------+
                |
                v
      One consistent lifecycle
   before -> proceed -> success/failure
                |
                v
       finally cleanup / rethrow
                |
                v
       Tests + versioning + metrics
       pointcuts / context / async
~~~

**Kết luận:** Tham số hóa chính sách trong AOP là tách **quy trình interception có thể dùng chung** khỏi **những quyết định thay đổi theo operation hoặc ngữ cảnh**. Nó giúp giảm duplication, thống nhất redaction, exception handling và lifecycle. Tuy nhiên phải phân biệt điểm chọn join point với runtime policy selection, giữ semantics của proceed và exception, dùng immutable policy snapshots, bảo vệ shared state, xét precedence/async/transaction và không nhồi những concerns có control-flow contracts khác nhau vào một siêu advice. Thiết kế tốt là **dễ hiểu, dễ test và đúng hợp đồng**, không đơn thuần có ít dòng code hơn.

## Tài liệu tham khảo

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): pointcut, advice, around, abstract pointcuts và aspect abstraction trong một ngôn ngữ AOP.
- [Java SE 17 — Records](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html): nền tảng record values.
- [Java SE 17 — Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html): unmodifiable sets và Set.copyOf.
- [Java SE 17 — Callable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html): continuation abstraction minh họa.
- [Java Language Specification SE 17 — Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): exception propagation và contracts.
- [Java Language Specification SE 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): memory model, synchronization và safe publication.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-005">Tách cơ chế can thiệp khỏi dịch vụ xử lý dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Tách cơ chế can thiệp khỏi dịch vụ xử lý dùng chung trong AOP

> **Mục tiêu học tập:** Thiết kế một aspect sao cho cơ chế **chọn điểm can thiệp, nhận quyền điều khiển và chuyển tiếp lời gọi** được tách khỏi phần **xử lý dùng chung**. Người học sẽ hiểu vai trò của pointcut, advice, join point adapter, dữ liệu ngữ cảnh chuẩn hóa và shared service; cách dùng lại một handler ở nhiều điểm can thiệp mà không phụ thuộc vào cú pháp weaving; cách bảo toàn return value/exception, xử lý đồng thời, phạm vi state, reentrancy, async và kiểm thử. Nội dung là AOP tổng quát cơ bản và nâng cao; các API Java 17 minh họa nguyên lý bằng chương trình tự chạy, không giả định một engine AOP hoặc ngôn ngữ pointcut cụ thể.

## 1. Bài toán: cùng một cách xử lý, nhiều ranh giới can thiệp

Giả sử một ứng dụng cần ghi lại những sự kiện kỹ thuật mỗi khi một thao tác quan trọng bắt đầu, trả kết quả hoặc thất bại. Các thao tác có thể xuất hiện ở nhiều nơi:

- Method thực thi một truy vấn dữ liệu.
- Method hoàn tất một phép tính nghiệp vụ.
- Callback được chạy sau một tác vụ bất đồng bộ.
- Một ranh giới xử lý lệnh do ứng dụng tự định nghĩa.

Nếu mỗi advice tự thực hiện định dạng log, phân loại lỗi, tạo correlation ID, kiểm tra chính sách ghi và gọi event sink, cùng một logic sẽ bị sao chép. Khi yêu cầu thay đổi cách xử lý sự kiện, người viết phải sửa nhiều aspect.

Ngược lại, nếu đưa nguyên object join point của một công cụ vào một dịch vụ xử lý chung, dịch vụ đó lại phụ thuộc vào API AOP, loại join point và cơ chế weaving. Người viết đã **chuyển chỗ sự phụ thuộc** chứ chưa tách nó.

Mục tiêu của bài:

~~~text
Nhiều ranh giới can thiệp
        |
        v
Những advice/adapter mỏng
        |
        v
Dữ liệu ngữ cảnh chuẩn hóa
        |
        v
Shared Processing Service
        |
        v
Policy / formatter / event sink / metrics backend
~~~

**Đường phụ thuộc phải đi từ cơ chế can thiệp tới hợp đồng xử lý dùng chung**, không buộc dịch vụ dùng chung phải biết pointcut engine nào gọi nó.

## 2. Phân biệt pointcut, advice và shared service

| Thành phần | Trách nhiệm | Điều không nên ôm đồm |
|---|---|---|
| **Join point** | Điểm/sự kiện runtime mà mô hình AOP hỗ trợ can thiệp | Không tự là một record xử lý nghiệp vụ tổng quát |
| **Pointcut** | Chọn những join point phù hợp | Không tự thay thế thuật toán xử lý sự kiện |
| **Weaver/interceptor** | Kết hợp advice vào đường thực thi | Không nên trở thành nơi xử lý chính sách tùy ý |
| **Advice/adapter** | Nhận invocation, rút metadata, gọi handler và chuyển tiếp | Không sao chép toàn bộ xử lý dùng chung |
| **Shared service/handler** | Áp dụng logic chuẩn hóa, phân loại, phát sự kiện hoặc thực thi một tác vụ dùng chung | Không nhất thiết biết đối tượng join point hoặc cú pháp pointcut |
| **Sink/port** | Nhận kết quả xử lý qua một hợp đồng | Không cần biết lời gọi đến từ loại weaving nào |

Từ **“dịch vụ”** trong bài chỉ một thành phần phần mềm có API rõ ràng, không nhất thiết là dịch vụ mạng hoặc một container bên ngoài.

### 2.1. Advice không phải một tầng nghiệp vụ mới mặc định

Advice là **điểm kết nối với luồng thực thi**. Nó có thể bao quanh, chặn, thay tham số, thay kết quả hoặc xử lý lỗi nếu cơ chế hỗ trợ. Tuy nhiên việc advice có quyền làm điều đó **không có nghĩa** mọi chính sách xử lý đều nên được viết trực tiếp trong advice.

Một advice dùng cho quan sát thuần túy thường cần phần thân ngắn, trong khi logic phân loại, chuẩn hóa và gửi sự kiện có thể đặt vào các thành phần dùng chung để kiểm thử độc lập.

## 3. Ba lớp hợp lý: lựa chọn, thích ứng, xử lý

**Lớp 1 — Selection/interception mechanism:** chứa định nghĩa điểm được chọn và đặc tả kiểu advice; phụ thuộc vào khả năng của hệ AOP.

**Lớp 2 — Adapter:** chuyển các dữ kiện ở join point sang một hợp đồng ổn định, ví dụ operation name, phase, outcome và một correlation key. Adapter chịu trách nhiệm ranh giới return/throw của lời gọi.

**Lớp 3 — Shared processor:** làm việc trên các DTO/value objects độc lập với engine. Bộ xử lý có thể được gọi bởi adapter A, adapter B, unit test hoặc một application component bình thường.

~~~text
AOP selector ──> MethodAdviceAdapter ─┐
                                      │
Callback wrapper ──> CallbackAdapter ─┼─> ObservationProcessor
                                      │           |
Manual invocation ────────────────────┘           v
                                                EventSink
~~~

Không phải mọi hệ thống cần đúng ba class vật lý. Đây là **phân tách trách nhiệm**, có thể hiện thực bằng vài class nhỏ hoặc nhiều module tùy quy mô.

## 4. Tại sao không cho handler nhận JoinPoint trực tiếp?

Một thiết kế coupling cao:

~~~text
SharedHandler.process(rawJoinPoint)
~~~

Handler phải biết:

- Cách engine biểu diễn signature.
- Cách lấy target và argument array.
- Các loại call/execution/constructor join points được phép.
- API proceed và trạng thái can thiệp.
- Quy tắc class loading, proxy hoặc weaving.
- Kiểu exception được API của engine bọc.
- Quyền truy cập raw arguments, có thể chứa dữ liệu nhạy cảm.

Một API ổn định hơn:

~~~text
SharedHandler.handle(
    Event(
        operation = "Catalog.load",
        phase = "FAILURE",
        failureType = "IOException",
        correlationId = "R-42"
    )
)
~~~

Event là **dữ liệu đã chuẩn hóa**. Handler có thể hoạt động khi không có môi trường weaving, giúp unit test chỉ việc tạo Event.

### 4.1. Chỉ copy metadata cần thiết

Không cần đưa cả target object, raw arguments hoặc toàn bộ Throwable tới mọi handler nếu nó chỉ cần tên operation, phase và error class. Việc thu hẹp DTO làm giảm coupling, memory retention, cardinality và nguy cơ lộ dữ liệu.

Một thiết kế chuyên sâu có thể truyền thông tin lỗi dưới dạng error record đã kiểm duyệt, đồng thời giữ Throwable gốc ở ranh giới sở hữu lỗi. Không nên tự động lưu Throwable trong một hàng đợi sống lâu chỉ để ghi metrics.

## 5. Hướng phụ thuộc: từ adapter tới hợp đồng chung

Một kiến trúc mong muốn:

~~~text
AOP-specific advice adapter
         depends on
     SharedService port
         implemented by
  ConcreteSharedProcessor
         depends on
       Sink interfaces
~~~

Không mong muốn:

~~~text
SharedProcessor
     imports a weaver's raw JoinPoint class
     casts advice-specific subclasses
     calls proceed()
     decides weaving precedence
~~~

Việc gọi proceed, quyết định có short-circuit hay không và bảo toàn kết quả của lời gọi thuộc về **hợp đồng can thiệp**. SharedProcessor xử lý **thông điệp/đối tượng xử lý ổn định**; nếu cần điều phối nghiệp vụ, hãy tách riêng một policy rõ ràng thay vì giấu điều khiển luồng trong một method handleEvent không có mô tả.

### 5.1. Hợp đồng ổn định không nhất thiết là hợp đồng tối giản cực đoan

DTO chỉ có một String message có thể quá yếu: handler phải parse text rồi suy phase hoặc failure type. Hợp đồng tốt nên có **những trường có ý nghĩa** nhưng không mang API cụ thể của engine.

Ví dụ:

~~~text
Observation {
    eventKind
    operation
    stage
    outcome
    correlation
    errorCategory?
    elapsedNanos?
}
~~~

Những trường không cần thiết không nên tạo ra chỉ cho đẹp. Cấu trúc phụ thuộc mục tiêu quan sát hay policy đang được hiện thực.

## 6. Một concern dùng chung có thể có nhiều adapter

Cùng một EventProcessor có thể nhận:

- MethodStarted và MethodFinished từ method-execution advice.
- CallbackStarted và CallbackCompleted từ wrapper quanh callback execution.
- OperationSubmitted từ interceptor quanh scheduling API.
- JobCompleted từ một thành phần ứng dụng chủ động phát event.

Sự đa dạng đó làm rõ **reusability**: mỗi adapter biết cách trích thông tin từ **boundary của nó**, còn processor không cần biết sự kiện đến từ một join point nào.

**Không phải mọi boundary ở trên đều được mọi công cụ AOP hỗ trợ như join point.** Có thể dùng một wrapper thủ công nếu model không có join point phù hợp; nguyên tắc phân tách vẫn được giữ.

## 7. Không gắn processing service với cú pháp pointcut

Ví dụ sai ở mức thiết kế:

~~~text
Service.recordFailure(pointcutExpression, rawJoinPoint, proceedHandle)
~~~

Handler vừa phải hiểu cách chọn điểm, vừa phải tham gia quyền điều khiển. Mỗi lần thay engine, đổi loại join point hoặc đổi cách matching, handler có nguy cơ cần sửa.

Ví dụ ổn định hơn:

~~~text
Advice:
  nếu được gọi tại điểm phù hợp:
    event = mapJoinPointToEvent(...)
    sharedProcessor.accept(event)
    tiếp tục theo advice contract
~~~

Sự khác nhau: pointcut xác định **khi nào gọi**, adapter xác định **thông tin gì được gửi**, processor xác định **xử lý thông tin đó thế nào**.

## 8. Mở rộng xử lý mà không thay mọi advice

Một lợi ích của tách lớp:

- Thay event sink từ console sang collector khác: sửa implementation processor/sink.
- Thêm quy tắc redaction: sửa normalizer hoặc processor có trách nhiệm.
- Đổi cách lấy operation name ở một loại join point: sửa adapter của loại đó.
- Thay đổi pointcut chọn nhiều hoặc ít method: sửa selection mechanism.
- Thêm adapter cho callback: tái sử dụng processor đã có.

Điều kiện là **hợp đồng Event/Processor ổn định về semantics**. Nếu nhét dữ liệu không có nghĩa chung vào một field String, các thay đổi vẫn có thể lan rộng.

### 8.1. Tách trách nhiệm không đồng nghĩa phải tạo quá nhiều interface

Trong một ứng dụng nhỏ, một record Event, một interface EventProcessor, một implementation và một adapter có thể đủ. Không cần xây một bus sự kiện đa tầng nếu chưa có yêu cầu thực tế.

Mục tiêu là **giảm coupling và làm rõ ownership**, không phải tối đa hóa số class.

## 9. Quyết định để cơ chế hay dịch vụ thực hiện việc gì

| Quyết định | Nơi phù hợp hơn |
|---|---|
| Chọn method execution theo signature | Pointcut/selection |
| Trích tên operation từ signature | Adapter |
| Đếm số lần handler được gọi | Handler/metrics processing |
| Nên gọi continuation hay short-circuit? | Advice hoặc policy điều khiển riêng được chỉ định |
| Chuyển exception thành metadata an toàn | Adapter/error mapper ở boundary |
| Có cần redaction trường nhạy cảm? | Normalizer/processor theo hợp đồng |
| Sử dụng một sink hay nhiều sinks | Processor/composition |
| Ai sở hữu transaction rollback? | Contract xử lý nghiệp vụ/transaction riêng |
| Xử lý lỗi sink bằng fail-open hay fail-closed? | Policy rõ ở ranh giới giữa adapter và processor |
| Có truyền context sang worker không? | Adapter ở async boundary/context scope owner |

Bảng chỉ mô tả một cách phân quyền trách nhiệm **hợp lý**, không phải luật ép mọi ứng dụng phải có cùng cách phân lớp.

## 10. Ví dụ Java 17 số 1: advice mỏng và processor dùng chung

Chương trình hoàn chỉnh này mô phỏng một quanh advice nhận tên thao tác và một continuation. Adapter chỉ:

1. Chuyển event BEGIN cho processor.
2. Gọi phần gốc đúng một lần.
3. Báo RETURN hoặc THROW theo kết quả.
4. Trả lại đúng value hoặc ném lại đúng exception.

Processor nhận Event đã chuẩn hóa và in kết quả. Cùng processor có thể được gọi trực tiếp ở ngoài advice, chứng minh nó **không phụ thuộc vào join point API**.

**Lưu ý:** Đây là Java thuần mô phỏng advice. Không có parser pointcut, bytecode weaving hay một engine AOP tự động.

Lưu file **InterceptionSeparationDemo.java**:

~~~java
import java.io.IOException;

public class InterceptionSeparationDemo {
    record Event(String phase, String operation, String detail) {}

    interface EventProcessor {
        void handle(Event event);
    }

    static final class ConsoleProcessor implements EventProcessor {
        @Override
        public void handle(Event event) {
            System.out.println("[processor] "
                    + event.phase() + " "
                    + event.operation() + " "
                    + event.detail());
        }
    }

    @FunctionalInterface
    interface Work {
        String run() throws IOException;
    }

    static final class AdviceAdapter {
        private final EventProcessor processor;

        AdviceAdapter(EventProcessor processor) {
            this.processor = processor;
        }

        String around(String operation, Work work) throws IOException {
            processor.handle(new Event("BEGIN", operation, "-"));
            try {
                String result = work.run();
                processor.handle(
                        new Event("RETURN", operation, result));
                return result;
            } catch (IOException | RuntimeException error) {
                processor.handle(new Event(
                        "THROW", operation,
                        error.getClass().getSimpleName()
                                + ": " + error.getMessage()));
                throw error;
            }
        }
    }

    static String load(String key) throws IOException {
        System.out.println("[core] load " + key);
        if (key.equals("missing")) {
            throw new IOException("not found");
        }
        return "data-" + key;
    }

    public static void main(String[] args) {
        EventProcessor processor = new ConsoleProcessor();
        AdviceAdapter adapter = new AdviceAdapter(processor);

        try {
            String value = adapter.around(
                    "Catalog.load", () -> load("ok"));
            System.out.println("[caller] " + value);
        } catch (IOException error) {
            System.out.println("[caller] " + error.getMessage());
        }

        try {
            adapter.around(
                    "Catalog.load", () -> load("missing"));
        } catch (IOException error) {
            System.out.println("[caller] "
                    + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }

        processor.handle(
                new Event("MANUAL", "Maintenance", "ready"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17:

~~~bash
javac InterceptionSeparationDemo.java
java InterceptionSeparationDemo
~~~

**Stdout chính xác:**

~~~text
[processor] BEGIN Catalog.load -
[core] load ok
[processor] RETURN Catalog.load data-ok
[caller] data-ok
[processor] BEGIN Catalog.load -
[core] load missing
[processor] THROW Catalog.load IOException: not found
[caller] IOException: not found
[processor] MANUAL Maintenance ready
~~~

### 10.1. Vì sao processor không cần biết join point?

ConsoleProcessor chỉ xử lý Event. Nó không dùng method signature API, target, proxy, continuation hoặc bộ dệt nào. Một unit test có thể gọi processor.handle(new Event(...)) mà không dựng môi trường AOP.

Đây là **reusability theo hợp đồng xử lý**, không phải chỉ việc nhiều advice cùng copy/paste một đoạn log.

### 10.2. Vì sao adapter nên giữ nhiệm vụ chuyển tiếp?

AdviceAdapter có trách nhiệm gọi work.run và giữ return/throw contract. Nếu handler tự gọi work.run, handler sẽ phụ thuộc cơ chế continuation và phải hiểu từng loại điểm can thiệp.

Đối với **observer thuần**, việc handler không điều khiển proceed giúp tránh lỗi gọi phần gốc hai lần hoặc vô tình không gọi phần gốc.

### 10.3. Processor có thể được gọi ngoài AOP

Dòng MANUAL Maintenance ready được phát trực tiếp từ main thông qua cùng EventProcessor. Đó là bằng chứng cụ thể rằng logic xử lý **dùng lại được ở một ranh giới không có AOP**.

Trong ứng dụng thật, một event thủ công nên có phân loại để tránh lẫn với method execution event; ví dụ có eventKind tường minh.

### 10.4. Giới hạn liên quan lỗi của processor

ConsoleProcessor trong ví dụ không cố tình ném lỗi. Nếu processor.handle tự ném RuntimeException ở nhánh RETURN, catch của adapter có thể bắt **lỗi processor** như thể đó là lỗi từ work, rồi tiếp tục gọi processor để phát THROW. Nếu processor lại lỗi, exception khác có thể che exception đầu.

Điều này cho thấy: **tách class thôi chưa đủ; phải thiết kế ranh giới lỗi của observer với lời gọi nghiệp vụ**. Phần sau xây dựng một chính sách best-effort rõ ràng để tránh gây false failure hoặc masking ngoài dự kiến.

## 11. Đừng lẫn “độc lập về API” với “độc lập về failure”

Một handler không dùng JoinPoint nhưng vẫn có thể:

- Ném RuntimeException từ sink.
- Block trong I/O.
- Gây deadlock nếu callback gọi lại điểm đang được advice.
- Dùng shared mutable state không an toàn.
- Đọc dữ liệu nhạy cảm không phù hợp.
- Kéo dài thời gian của mọi method được quan sát.

Vì vậy còn cần ranh giới về **failure policy, latency policy và state ownership**, bên cạnh ranh giới loại dữ liệu.

## 12. Một processor có thể chuyên về policy nhưng không nên tùy ý thay business outcome

Shared service có thể:

- Phân loại event.
- Chuẩn hóa operation naming.
- Kiểm duyệt metadata.
- Ghi metric.
- Xử lý event hợp lệ/không hợp lệ.
- Gửi tới một hoặc nhiều event sinks.
- Quản lý batching hoặc buffering khi được thiết kế.

Nếu nó phải **quyết định business operation có được tiếp tục hay không**, đó không còn là một event processor quan sát thuần. Một cách rõ ràng hơn là định nghĩa một **PolicyDecision** làm kết quả của policy service, rồi để advice diễn giải quyết định và gọi/không gọi continuation theo hợp đồng can thiệp.

Ví dụ khái niệm:

~~~text
Advice:
    input = normalize(joinPointContext)
    decision = policy.evaluate(input)
    if decision is REJECT:
        return or throw according to API contract
    else:
        proceed exactly once
~~~

Cần giữ khác biệt giữa **side-effect observation handler** và **control policy service**. Hai loại có thể cùng được chia sẻ, nhưng không nên dùng một tên handle chung mơ hồ che đi quyền thay đổi luồng thực thi.

## 13. Contract của event phải có semantics rõ

Các trường thường hữu ích:

| Trường | Ý nghĩa | Nguồn phù hợp |
|---|---|---|
| eventKind | Invocation, callback, submission... | Adapter |
| operation | Tên ổn định mô tả việc được xử lý | Adapter/normalizer |
| phase | BEGIN, RETURN, THROW, COMPLETE... | Advice boundary |
| outcome | SUCCESS, FAILURE, RECOVERED... theo hợp đồng | Owner của outcome |
| correlationId | Tương quan giữa nhiều events | Context scope |
| errorCategory | Nhóm lỗi đã chuẩn hóa | Error mapper |
| elapsedNanos | Duration ở ranh giới đo xác định | Timer owner |
| attempt | Lần thử của retry nếu có | Retry owner |

Không phải event nào cũng có đủ các trường. Nên định nghĩa **schema version** hoặc vài event types riêng khi semantics khác nhau rõ rệt, thay vì bắt tất cả vào một record khổng lồ với nhiều null không được mô tả.

Một processor dễ tái sử dụng vì nó nhận **hợp đồng có ý nghĩa ổn định**, không phải vì nó nhận Object rồi tự đoán mọi thứ bằng instanceof.

## 14. Shared service có thể dùng lại ở nhiều loại adapter

Một adapter cho method execution biết cách gọi continuation của method. Một adapter cho callback execution biết cách gọi callback. Chúng có thể khác nhau về method signature và exception model, nhưng tạo cùng loại Observation cho một shared handler.

~~~text
MethodAdapter:
  before target.run()
  after target.run()

CallbackAdapter:
  before callback.run()
  after callback.run()

Both:
  SharedProcessor.handle(Observation)
~~~

Không nên gom cả hai vào một advice khổng lồ kiểm tra “nếu là callback thì làm A, nếu là method thì làm B” khi ranh giới return/throw khác nhau. Tách các **adapter nhỏ**, dùng cùng **processor chung**.

## 15. Ví dụ Java 17 số 2: một service, hai loại adapter

Chương trình sau có:

- Một **SharedObservationService** ghi sự kiện theo cấu trúc.
- Một **MethodAdapter** gọi một supplier rồi phát BEGIN/RETURN/THROW.
- Một **CallbackAdapter** chạy một Runnable rồi phát BEGIN/RETURN/THROW.
- Một bộ nhớ event để in lại, chứng minh tất cả sự kiện đi qua cùng service.

Callback trong ví dụ được **gọi đồng bộ có chủ đích** để stdout xác định; nó minh họa **ranh giới thực thi callback**, không mô phỏng một hệ lập lịch async.

Lưu file **SharedHandlerReuseDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SharedHandlerReuseDemo {
    record Observation(String source, String operation, String phase) {}

    interface ObservationService {
        void accept(Observation event);
    }

    static final class SharedObservationService
            implements ObservationService {
        private final List<Observation> events = new ArrayList<>();

        @Override
        public void accept(Observation event) {
            events.add(event);
        }

        void printSnapshot() {
            for (Observation event : List.copyOf(events)) {
                System.out.println("[event] "
                        + event.source() + " "
                        + event.operation() + " "
                        + event.phase());
            }
        }
    }

    static final class MethodAdapter {
        private final ObservationService service;

        MethodAdapter(ObservationService service) {
            this.service = service;
        }

        String invoke(String name, Supplier<String> operation) {
            service.accept(new Observation("METHOD", name, "BEGIN"));
            try {
                String result = operation.get();
                service.accept(
                        new Observation("METHOD", name, "RETURN"));
                return result;
            } catch (RuntimeException error) {
                service.accept(new Observation(
                        "METHOD", name,
                        "THROW:" + error.getClass().getSimpleName()));
                throw error;
            }
        }
    }

    static final class CallbackAdapter {
        private final ObservationService service;

        CallbackAdapter(ObservationService service) {
            this.service = service;
        }

        void invoke(String name, Runnable callback) {
            service.accept(new Observation("CALLBACK", name, "BEGIN"));
            try {
                callback.run();
                service.accept(
                        new Observation("CALLBACK", name, "RETURN"));
            } catch (RuntimeException error) {
                service.accept(new Observation(
                        "CALLBACK", name,
                        "THROW:" + error.getClass().getSimpleName()));
                throw error;
            }
        }
    }

    public static void main(String[] args) {
        SharedObservationService service =
                new SharedObservationService();
        MethodAdapter methods = new MethodAdapter(service);
        CallbackAdapter callbacks = new CallbackAdapter(service);

        String value = methods.invoke("Catalog.find", () -> {
            System.out.println("[target] find");
            return "item-1";
        });

        callbacks.invoke("onComplete", () ->
                System.out.println("[callback] got " + value));

        try {
            callbacks.invoke("onError", () -> {
                throw new IllegalStateException("callback failed");
            });
        } catch (IllegalStateException error) {
            System.out.println("[caller] " + error.getMessage());
        }

        service.printSnapshot();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SharedHandlerReuseDemo.java
java SharedHandlerReuseDemo
~~~

**Stdout chính xác:**

~~~text
[target] find
[callback] got item-1
[caller] callback failed
[event] METHOD Catalog.find BEGIN
[event] METHOD Catalog.find RETURN
[event] CALLBACK onComplete BEGIN
[event] CALLBACK onComplete RETURN
[event] CALLBACK onError BEGIN
[event] CALLBACK onError THROW:IllegalStateException
~~~

### 15.1. Một service không cần biết lời gọi gốc ra sao

ObservationService chỉ có accept(Observation). Nó không gọi Supplier.get hoặc Runnable.run và không phụ thuộc vào một JoinPoint object. Hai adapter tự chịu trách nhiệm **tiếp tục thao tác đúng contract** của mình.

### 15.2. Cùng service giữ một luồng event

Cả MethodAdapter và CallbackAdapter sử dụng **cùng instance** SharedObservationService. Vì vậy printSnapshot in cả sự kiện METHOD và CALLBACK, theo thứ tự các invocation được gọi trong demo.

Trong môi trường đồng thời, ArrayList chưa phải thread safe. Dùng một implementation handler có concurrency model phù hợp hoặc serialize access nếu nhiều thread cùng gọi accept.

### 15.3. Callback registration và callback execution vẫn phải tách

Ở ví dụ, callbacks.invoke thực sự gọi Runnable.run, do đó event CALLBACK BEGIN/RETURN phản ánh **thời điểm callback được thực thi**. Một advice chỉ thấy method dùng để **đăng ký callback** không tự có cùng semantics.

Nếu callback chạy trên executor, adapter/handler phải được đặt ở boundary callback thực thi, đồng thời quản lý context/cancellation tương ứng.

### 15.4. Đừng cho handler tự quyết định gọi continuation

Nếu SharedObservationService nhận luôn Runnable rồi tự gọi run, nó có thể trở thành tầng điều phối, khó biết ai có quyền retry hoặc short-circuit. Trong minh họa, **adapter giữ quyền gọi** và handler **chỉ xử lý Observation**.

Nếu yêu cầu thật sự là policy orchestration, nên tạo interface riêng như ExecutionPolicy hoặc RetryCoordinator với hợp đồng rõ.

## 16. Chú ý lỗi của service ở đường thành công

Hai chương trình minh họa dùng handler bình thường, nên một lối viết đơn giản là:

~~~text
try:
    result = proceed()
    processor.handle(RETURN)
    return result
catch:
    processor.handle(THROW)
    rethrow
~~~

Nhưng nếu processor.handle(RETURN) tự ném lỗi, catch có thể tưởng đó là lỗi từ proceed và phát THROW giả. Lúc đó một **failure của observer** bị ghi như failure của business operation.

Một cách khắc phục là **giới hạn try/catch quanh đúng lời gọi Original**:

~~~text
try:
    result = proceed()
catch businessError:
    safelyObserve(THROW, businessError)
    throw businessError

safelyObserve(RETURN, null)
return result
~~~

Phân tách hai loại failure giúp metric không báo “business operation failed” chỉ vì sink lỗi.

### 16.1. Không tự chọn fail-open cho mọi concern

Nếu service chỉ gửi metrics best-effort, có thể quyết định bỏ qua lỗi sink (sau khi chẩn đoán bằng kênh phù hợp). Nếu service thực thi kiểm soát an toàn hoặc audit bắt buộc, một chính sách fail-closed có thể được yêu cầu.

Vì vậy failure isolation là **hợp đồng rõ**, không chỉ catch(RuntimeException) rồi luôn nuốt. Cần phân biệt **lỗi hệ thống quan sát** với **lỗi nghiệp vụ** trong cả log, metrics và kết quả caller.

## 17. Bảo toàn exception và return value

Một advice quan sát thuần nên bảo toàn:

1. **Số lần Original được gọi**: đúng một lần khi không có chính sách điều khiển khác.
2. **Giá trị trả về**: cùng dữ liệu, không tự thay thành fallback.
3. **Loại và identity của exception** khi đã thỏa contract.
4. **Cause và suppressed** nếu có chuyển loại/cleanup.
5. **Các side effects của Original**: không lặp do advice vô tình retry.

Một handler muốn ghi message về exception có thể nhận một ErrorSummary đã chuẩn hóa, trong khi adapter vẫn ném lại lỗi gốc. Đừng bắt tất cả Exception rồi trả null chỉ để handler không phải xử lý lỗi.

### 17.1. Error cũng là đường điều khiển cần cân nhắc

Một observer có thể chọn dùng finally để cleanup/ghi duration, nhưng không nên bắt Throwable rồi cố biến mọi lỗi VM thành success. Trong tình huống tài nguyên cạn, chính logger có thể không hoạt động. Không có bảo đảm một sự kiện quan sát được phát thành công sau mọi dạng lỗi.

## 18. Một adapter không nên vừa sở hữu fallback vừa sở hữu metric policy

Nếu cùng một advice:

- Validate input.
- Retry.
- Chuyển exception thành fallback.
- Đo latency.
- Redact arguments.
- Gửi event.
- Mở transaction.

thì chỉ tách một class ObservationService chưa đủ làm thiết kế dễ hiểu. Cần chia **mối quan tâm** thành những hợp đồng có ownership:

~~~text
Guard decision
Retry policy
Transaction boundary
Observation
Error mapping
~~~

Mỗi phần nên có trách nhiệm rõ; precedence giữa các aspect cần phản ánh ngữ nghĩa, không dựa vào thứ tự tên class. Shared processor không nên dần trở thành một nơi chứa mọi chính sách không liên quan.

## 19. Tách xử lý chung khỏi business service

Một bài toán dễ hiểu sai: “Tách cơ chế can thiệp khỏi dịch vụ xử lý dùng chung” **không có nghĩa phải chuyển thuật toán nghiệp vụ vào aspect**.

Dịch vụ nghiệp vụ vẫn nên thể hiện nghiệp vụ:

~~~text
CatalogService.load
PaymentService.pay
InventoryService.reserve
~~~

Shared ObservationProcessor xử lý **quan sát**, không tự quyết định số tiền thanh toán hay tồn kho, trừ khi một API policy được thiết kế chuyên biệt yêu cầu điều đó.

Một aspect không nên biến toàn bộ business code thành callback để đưa vào một “universal service” khó đọc. AOP thích hợp cho những chức năng xuyên suốt có ranh giới lựa chọn rõ.

## 20. Shared service không bắt buộc là singleton

Việc nhiều adapter tái sử dụng một interface không buộc mọi invocation chia sẻ **cùng instance**.

Các scope có thể có:

| Scope của processor | Khi phù hợp | Nguy cơ |
|---|---|---|
| Stateless singleton | Xử lý thuần hoặc đẩy qua thread-safe sink | Phải bảo đảm thread safety |
| Per-request | Bộ tích lũy trong một logical request | Cần propagation và cleanup |
| Per-invocation | State tạm riêng mỗi lời gọi | Tạo nhiều objects nếu hot path |
| Per-target | State gắn object đích | Không đồng nghĩa per-request |
| Per-thread | State theo worker thread | Thread pool reuse và async boundary |
| External shared store | Events được lưu ngoài process | Backpressure, idempotency, failure semantics |

**Reuse về interface/implementation không đồng nghĩa share mutable state vô điều kiện.**

Một processor stateless có thể được nhiều adapter dùng chung mà không tạo nguy cơ trộn request. Một processor tích lũy List<Event> như ví dụ thứ hai cần được giới hạn concurrency và lifetime.

## 21. Concurrency và quyền sở hữu state

Nếu nhiều threads gọi shared handler đồng thời, phải biết những object nào mutable:

- Bộ đếm sự kiện.
- Hàng đợi.
- Cache metadata.
- Bộ lọc sampling.
- Bộ định dạng có buffer nội bộ.
- Correlation maps.
- Batch accumulator.
- Các kết nối/sessions tới sink.

Một handler dùng HashMap hoặc ArrayList không thread safe trong demo tuần tự không tự thành thread safe khi được gọi từ các advice của server nhiều thread.

Các cách thiết kế:

- Stateless processor với immutable events.
- Thread-safe queue hoặc collection phù hợp.
- Serial executor với bounded capacity.
- Lock có phạm vi được xác định.
- Per-invocation buffers nếu thật sự cần.
- Backpressure hoặc sampling khi khối lượng tăng.

Không che rủi ro bằng synchronized toàn bộ handler nếu việc ghi I/O chậm hoặc có khả năng gọi lại các điểm được advice: điều đó có thể gây contention hoặc deadlock.

## 22. Context propagation không thuộc processor một cách tùy tiện

Adapter ở ranh giới can thiệp biết:

- Điểm nào đang được gọi.
- Thread/scope hiện tại.
- Có continuation hay không.
- Khi nào method return hoặc Future được trả.

Shared handler thường không nên tự đọc một ThreadLocal không được khai báo trong hợp đồng rồi suy requestId; như vậy hidden dependency có thể khiến unit test sai và async callbacks mất correlation.

Tốt hơn là adapter hoặc context extractor tạo một **immutable context snapshot đã được kiểm duyệt**, rồi đưa vào Event.

~~~text
Advice extracts ContextSnapshot
        |
        v
Observation(Event, ContextSnapshot)
        |
        v
SharedProcessor
~~~

Với async, việc một method trả Future không nghĩa công việc đã hoàn tất. Adapter cho scheduling và adapter cho completion là những boundary khác nhau; processor chỉ nhận event đã chỉ rõ loại.

## 23. Nested calls và reentrancy

Giả sử processor ghi sự kiện bằng cách gọi một method mà pointcut cũng chọn:

~~~text
Advice for businessMethod
   -> SharedProcessor.handle
      -> EventSink.write
         -> Advice for EventSink.write
            -> SharedProcessor.handle
               -> ...
~~~

Điều này có thể tạo vòng recursion không mong muốn.

Một số giải pháp:

- Loại trừ các method hạ tầng của processor/sink khỏi pointcut nếu mô hình cho phép.
- Tránh để handler gọi lại trực tiếp same intercepted operation.
- Dùng một guard reentrancy được quản lý theo execution scope khi cần.
- Đảm bảo guard được phục hồi trong finally, kể cả lỗi.
- Kiểm thử nested invocations và lời gọi từ nhiều threads.

Một boolean static chung như isLogging có thể vô tình chặn sự kiện của **thread khác**, vì vậy không nên dùng làm guard per-invocation thiếu thiết kế.

## 24. Đừng nhầm callback registration với callback execution

Khi một API đăng ký callback, advice quanh **registration** chỉ biết callback được đưa vào hệ thống. Nó không tự biết callback:

- Đã bắt đầu chạy.
- Đã hoàn tất.
- Đã ném lỗi.
- Bị hủy trước khi chạy.
- Chạy trên thread nào.
- Có bị gọi qua một wrapper khác hay không.

Nếu shared processor dùng chung cho METHOD và CALLBACK, các adapter cần đặt **eventKind** và **phase** đúng. Không được gọi một event REGISTRATION là CALLBACK_SUCCESS.

Một callback wrapper có thể tạo event ở thời điểm thực thi; nhưng không được hứa mọi công cụ AOP tự can thiệp lambda/async callback nếu không có join point model phù hợp.

## 25. Timing service và sự khác nhau về boundary

Một DurationProcessor chỉ tính đúng latency khi adapter cung cấp hai mốc tương ứng:

~~~text
Method execution:
   enter method -> return/throw method

Async task:
   task starts -> task completes

Logical request:
   request accepted -> final completion
~~~

Nếu một method trả CompletableFuture, thời điểm return handle khác thời điểm future completion. Dịch vụ dùng chung có thể xử lý DurationEvent từ cả ba loại, nhưng **adapter phải ghi đúng loại và interval**.

Tốt hơn dùng nguồn thời gian monotonic để đo duration trong process; không dùng timestamp wall-clock tùy tiện rồi coi mọi chênh lệch đều là latency đáng tin.

Handler không nên tự lấy System.nanoTime ở thời điểm batch processing và nghĩ đó là thời điểm task thực sự kết thúc. Nếu event bị buffer, thời điểm handler nhận event có thể trễ hơn ranh giới sự kiện.

## 26. Trạng thái per-request và vòng đời context

Nếu cần correlation ID, processor có thể nhận nó qua Event. Không nên lưu currentRequestId trong một singleton processor field vì nhiều requests có thể chạy đồng thời.

ThreadLocal có thể phù hợp trong call stack đồng bộ nhưng:

- Cần remove/restore khi scope kết thúc.
- Thread pool tái sử dụng thread.
- Async work có thể chạy thread khác.
- Nested scope phải restore previous.
- Cancellation và failure paths phải có cleanup thích hợp.

**Một service xử lý dùng chung không tự là context owner.** Nếu nó chỉ nhận Event có correlation ID tường minh, code và test thường dễ kiểm chứng hơn.

## 27. Adapter có thể chuẩn hóa dữ liệu, processor có thể chuẩn hóa chính sách

Có hai kiểu chuẩn hóa không nên trộn:

**Chuẩn hóa kỹ thuật (adapter):**

- Tên phương thức thành operation code.
- Signature thành metadata định dạng trung lập.
- Một lỗi runtime thành error category/summary.
- Method execution thành eventKind METHOD.
- Callback execution thành eventKind CALLBACK.

**Chuẩn hóa chính sách (processor):**

- Lọc những event không cần lưu.
- Tính mức độ ưu tiên kỹ thuật.
- Redact theo dữ liệu đã được phân loại.
- Áp dụng sampling.
- Định tuyến event theo loại.
- Tổng hợp metrics hoặc chuyển sang sink phù hợp.

Vị trí cụ thể của một phép chuẩn hóa phụ thuộc hệ thống; nguyên tắc là **adapter biết cơ chế, processor biết chính sách xử lý chung**.

## 28. Fail-open và fail-closed phải là lựa chọn có chủ đích

Một observation processor ghi metrics lỗi có thể chọn fail-open: nếu metric sink lỗi, không làm hỏng business operation. Một audit bắt buộc có thể chọn fail-closed, nhưng cần một **contract tường minh** và thử nghiệm đặc biệt.

Nguy cơ của processor dùng chung:

- Nếu nó ném lỗi sau business success, caller có thể thấy failure mới.
- Nếu nó ném lỗi trong catch/finally, lỗi processor có thể che lỗi gốc.
- Nếu nó block quá lâu, tất cả intercepted calls chậm theo.
- Nếu có policy retry sink không giới hạn, một business call có thể kẹt.
- Nếu nó nuốt mọi Throwable, có thể che sự cố nghiêm trọng.

Một lời giải kiến trúc hợp lý thường có **ObserverFailurePolicy** hoặc cơ chế tương đương để phân biệt lỗi handler với lỗi continuation. Không có một chính sách chung áp dụng được cho logging best-effort và an toàn/kiểm toán bắt buộc.

## 29. Khi xử lý bất đồng bộ, tránh truyền raw JoinPoint qua hàng đợi

Một advice có thể phát event vào queue để processor chạy sau. Nếu đẩy **nguyên object join point**, target hoặc argument array mutable sang queue:

- Dữ liệu có thể bị thay đổi trước khi handler xử lý.
- Raw arguments có thể chứa token hoặc thông tin riêng tư.
- Target có thể bị giữ tham chiếu lâu hơn yêu cầu.
- Hệ xử lý phải phụ thuộc API engine.
- Nếu join point chỉ hợp lệ trong một execution scope, dùng muộn có thể sai hợp đồng.

Nên tạo **immutable snapshot** ngay tại ranh giới can thiệp, chỉ bao gồm những trường có ý nghĩa, đã redaction và an toàn để chuyển qua thread.

Nếu event sink bất đồng bộ, cần biết sự kiện nào phải đảm bảo durable delivery và chính sách overflow/backpressure ra sao. Một phương thức accept() trả bình thường chưa chắc event đã được ghi bền vững.

## 30. Versioning hợp đồng giữa adapters và processor

Khi số adapter tăng, Event contract trở thành một giao diện chung. Nếu muốn thêm phase CANCELLED hoặc field callbackId:

- Xác định nghĩa và default của field mới.
- Không làm handler cũ hiểu nhầm CANCELLED thành FAILURE.
- Phân biệt unknown value với empty value.
- Duy trì mapping nhất quán giữa các adapter.
- Có test schema compatibility.
- Nếu event được gửi qua network hoặc lưu dài hạn, cần cân nhắc version.

Không chỉ đổi String phase ở một advice rồi để processor khác parse bằng điều kiện mơ hồ. Một enum hoặc kiểu event được tài liệu hóa có thể làm lỗi hiện sớm hơn; nếu sự kiện cần tương thích nhiều phiên bản, thiết kế serialization/schema rõ.

## 31. Tách kiểm thử service và kiểm thử adapter

Một lợi ích lớn là có **hai tầng test độc lập**.

**Test SharedProcessor:**

- Với Event BEGIN/RETURN/THROW cho trước, processor tạo output chính xác.
- Policy redaction đúng.
- Không phát event ngoài allowlist.
- Concurrent calls có behavior phù hợp.
- Handler failures theo fail-open/fail-closed contract.
- Không cần dựng join point engine.

**Test Adapter:**

- Pointcut/join point phù hợp.
- Trích operation metadata đúng.
- Tạo đúng phase.
- Gọi continuation đúng số lần.
- Bảo toàn return/throw của Original.
- Truyền context snapshot đúng.
- Không để scope rò ở exceptional path.
- Không phát callback-completion khi chỉ quan sát callback registration.

**Integration test:** bật đúng các aspects/engines có thật rồi xác minh end-to-end wiring, selection, ordering và độ bao phủ của proxy/weaving.

## 32. Mock handler để kiểm tra advice chính xác

Khi adapter chỉ phụ thuộc vào interface EventProcessor, test có thể cung cấp một spy implementation ghi Event vào List hoặc tăng counter.

Một test có thể assert:

~~~text
originalCount == 1

observedEvents:
  BEGIN
  RETURN

returnedValue == originalValue
~~~

Ở exceptional case:

~~~text
original throws E
observedEvents:
  BEGIN
  THROW
caller receives same E
~~~

Đây là pseudocode cho assertions, không phải Java runnable snippet.

Cần kiểm tra cả chuyện **service được gọi sai thứ tự** hoặc một event trùng do retry, nested advice, reflection wrapper, double proxy.

## 33. Testing processor mà không cần AOP

Một processor nhận Event có thể được test trực tiếp:

~~~text
given:
  Event(kind="METHOD", operation="Catalog.load", phase="THROW")

when:
  processor.handle(event)

then:
  sink stores expected safe classification
~~~

Nhờ vậy người học có thể kiểm tra thuật toán xử lý, phân loại lỗi hoặc schema mà không cần chạy bytecode weaving.

Điều này không phủ nhận integration test: nó chỉ làm unit test **nhanh hơn và ít coupling hơn**. Các giới hạn của pointcut scope và callback path vẫn cần được xác minh khi các lớp được kết hợp thật.

## 34. Kiểm thử nhiều adapter dùng cùng handler

Phải kiểm tra:

1. Adapter A cho METHOD và adapter B cho CALLBACK cùng gọi một processor.
2. Event.source/eventKind được gắn đúng.
3. Không lẫn completion của task với registration.
4. Handler không cần kiểu dữ liệu cụ thể của AOP engine.
5. Event của hai adapter không trùng key ngoài ý muốn.
6. Nếu service có shared state, đồng thời không gây race.
7. Nếu processor lọc event, hai adapter hiểu cùng một schema.
8. Nếu một adapter bị short-circuit, handler không bịa event Original đã chạy.
9. Nếu một adapter retry, số events được giải thích theo request/attempt.
10. Nếu callback bị hủy, có event đúng nghĩa và không giả SUCCESS.

Một handler tái sử dụng được không chỉ khi **code compile**, mà còn khi **semantics không mâu thuẫn** giữa các nguồn sự kiện.

## 35. Perf: chi phí của adapter và processor

Tách lớp có thể cải thiện bảo trì nhưng không tự đảm bảo nhanh. Chi phí có thể gồm:

- Pointcut dynamic matching.
- Interceptor dispatch.
- Tạo DTO/immutable snapshot.
- Đọc reflection signature.
- Định dạng string.
- Ghi queue và lock.
- Event serialization.
- I/O tới sink.
- GC pressure.
- Retry/backpressure.

Một phương thức hot path được gọi hàng triệu lần có thể không phù hợp gửi event giàu metadata mỗi invocation.

Các cách tối ưu hợp lý:

- Điểm chọn đủ hẹp.
- Cache metadata tĩnh đúng vòng đời.
- Tránh dựng strings nếu event bị sampling bỏ qua.
- Dùng snapshot tối thiểu.
- Có bounded queue/backpressure.
- Tránh log raw arguments.
- Đo chi phí với tất cả aspects đang bật.
- Phân biệt latency của business path và thời gian xử lý ở background.

Không đổ lỗi mọi overhead cho “AOP” nếu nguồn chính là một handler làm I/O đồng bộ.

## 36. Khi nào không cần AOP?

Nếu chỉ có một lời gọi nghiệp vụ cần handler, gọi shared service trực tiếp có thể đơn giản hơn:

~~~text
BusinessService:
  eventProcessor.handle(...)
  doWork()
~~~

Nhưng nếu cùng concern xuất hiện ở nhiều ranh giới và muốn tránh lặp code kết nối, advice có thể là lựa chọn hợp lý.

Không nên dùng AOP chỉ để gọi một service dùng chung mà ứng dụng vốn đã có một API rõ và kiểm soát được. Mấu chốt là **có lợi ích của một selection/interception layer hay không**.

## 37. Những phản ví dụ quan trọng

**Phản ví dụ 1 — Processor nhận raw join point.** Không thật sự độc lập khỏi AOP engine; đổi engine có thể phải sửa processor.

**Phản ví dụ 2 — Advice chỉ có một dòng nhưng mọi logic nằm trong static utility phụ thuộc ThreadLocal.** Vẫn có hidden dependency, khó test và dễ rò context.

**Phản ví dụ 3 — Handler tự gọi proceed.** Handler nay sở hữu control flow; khó dùng cho callback với hợp đồng khác.

**Phản ví dụ 4 — Processor chứa currentUser mutable singleton field.** Nhiều requests có thể trộn dữ liệu.

**Phản ví dụ 5 — Tách class rồi bắt mọi exception và trả null.** Hợp đồng nghiệp vụ vẫn bị phá.

**Phản ví dụ 6 — Processor ghi cùng một loại event cho callback registration và execution.** Số liệu không có ý nghĩa.

**Phản ví dụ 7 — Shared service tự đọc toàn bộ raw arguments.** Tăng coupling và nguy cơ bảo mật.

**Phản ví dụ 8 — AOP pointcut quan sát method submit được coi là đo xong async task.** Ranh giới sai.

**Phản ví dụ 9 — Hai adapters cùng có retry policy ẩn.** Original có thể chạy nhiều lần ngoài dự kiến.

**Phản ví dụ 10 — Handler lưu events bằng ArrayList rồi dùng từ nhiều worker.** Không có thread safety dù interface rất sạch.

**Phản ví dụ 11 — Sink exception xảy ra sau work.run rồi bị log như business THROW.** Dữ liệu telemetry báo sai và có thể che kết quả gốc.

**Phản ví dụ 12 — Một pointcut quá rộng chọn cả processor.handle.** Tạo reentrancy/recursion.

**Phản ví dụ 13 — DTO chỉ gồm một String.** Processor phải parse văn bản không có schema, không ổn định khi tái sử dụng.

**Phản ví dụ 14 — EventProcessor dùng để kiểm tra quyền và bị đặt ở vị trí chỉ quan sát sau khi nghiệp vụ chạy.** Policy bắt buộc cần đúng boundary, không chỉ chung interface.

**Phản ví dụ 15 — Đo throughput của handler riêng rồi suy toàn bộ overhead trong weaving là như vậy.** Cần đo cả điểm can thiệp thực tế và khối lượng event.

## 38. Bảng chọn kiến trúc theo nhu cầu

| Nhu cầu | Cách tiếp cận nên xem xét |
|---|---|
| Chỉ thêm log ở nhiều method | Thin advice + shared observation handler |
| Xử lý một event ở cả advice và ứng dụng thường | Shared EventProcessor độc lập AOP |
| Nhiều loại join point có metadata khác | Adapter riêng + Event DTO chuẩn |
| Chặn lời gọi theo policy | Policy service + advice điều phối rõ |
| Retry với điều kiện nghiệp vụ | Retry coordinator có hợp đồng idempotency |
| Async callback instrumentation | Wrapper/adapter ở callback execution boundary |
| Context propagation | Snapshot + scope owner ở từng thread/boundary |
| Cần hiệu năng trên hot path | Sampling, bounded queue, tối giản DTO, đo thực tế |
| Ghi audit bắt buộc | Failure/consistency policy tường minh |
| Đang có chỉ một nơi dùng handler | Gọi service trực tiếp có thể đơn giản hơn |

Bảng chỉ định hướng quyết định, không quy định một engine hoặc syntax cụ thể.

## 39. Ma trận kiểm thử hoàn chỉnh

| Ca | Điều cần xác minh |
|---|---|
| Một method khớp pointcut | Adapter vào đúng ranh giới |
| Method không khớp | Handler không nhận event ngoài ý muốn |
| Original trả bình thường | Giá trị được giữ nguyên |
| Original ném checked exception | Caller nhận tín hiệu lỗi đúng contract |
| Original ném RuntimeException | Không bị nuốt |
| Shared processor lỗi khi RETURN | Không gán nhầm là business failure |
| Shared processor lỗi khi THROW | Không che primary ngoài policy |
| Pointcut chọn cả EventSink | Không gây vòng advice vô hạn |
| Hai adapters dùng cùng processor | Schema thống nhất |
| Callback đăng ký mà chưa chạy | Không phát CALLBACK_RETURN giả |
| Async task thất bại sau method return | Completion được quan sát tại boundary phù hợp |
| Retry nhiều attempts | Event count phân biệt request/attempt |
| Nested scope | Context cha được phục hồi |
| Thread pool tái sử dụng | Không có stale context |
| Handler stateless singleton | Concurrent calls đúng |
| Handler có mutable cache | Thread safety và lifecycle phù hợp |
| Queue đầy | Backpressure/failure policy rõ |
| Dữ liệu nhạy cảm | Redaction/allowlist hiệu lực |
| Woven/proxy path khác nhau | Adapter coverage được chứng minh |
| Original bị gọi qua đường không có advice | Không tuyên bố luôn được quan sát |

## 40. Checklist xây một shared handling service

- [ ] Có định nghĩa rõ đó là **observation**, **policy** hay **orchestration** service.
- [ ] Không buộc handler import raw join point types khi không cần.
- [ ] Advice giữ việc gọi continuation/proceed theo hợp đồng.
- [ ] DTO có semantics ổn định, không chỉ một String mơ hồ.
- [ ] Có eventKind/phase rõ cho những boundary khác nhau.
- [ ] Handler được gọi lại ở nhiều adapter mà không copy code.
- [ ] Không giữ raw target/arguments/Throwable không cần thiết.
- [ ] Return value và exception của Original được bảo toàn nếu là observer.
- [ ] Có failure policy riêng cho handler/sink.
- [ ] Không log handler failure thành business failure.
- [ ] Có policy cho processor latency, queue và backpressure.
- [ ] Không có singleton mutable request state không an toàn.
- [ ] Có context propagation và cleanup cho async path.
- [ ] Pointcut loại trừ mã hạ tầng tạo recursion khi cần.
- [ ] Có tests processor độc lập khỏi AOP.
- [ ] Có tests adapter trên normal/exceptional paths.
- [ ] Có integration tests cho proxy/weaving/scheduling path thật.
- [ ] Có metrics và audit semantics rõ: request, attempt, task hay callback.

## 41. Bài tập tự kiểm tra

### Câu 1 — Vị trí của proceed

SharedObservationService muốn xử lý Event. Nên để nó gọi proceed trực tiếp không?

**Đáp án:** nếu service chỉ quan sát, không nên. Advice/adapter chịu trách nhiệm continuation và gửi Event đến processor. Nếu yêu cầu điều phối, tạo một interface policy/orchestrator có hợp đồng riêng.

### Câu 2 — Cùng logic trên hai điểm can thiệp

Một method-execution advice và một callback adapter đều cần chuẩn hóa Event. Có nên copy/paste toàn bộ xử lý vào mỗi adapter?

**Đáp án:** không. Mỗi adapter chuyển metadata đặc thù sang một DTO chuẩn rồi gọi shared processor.

### Câu 3 — Raw join point trong handler

Tại sao SharedProcessor.handle(rawJoinPoint) có thể làm giảm tái sử dụng?

**Đáp án:** processor phụ thuộc API công cụ, loại điểm và vòng đời join point. Một DTO chuẩn làm processor độc lập hơn.

### Câu 4 — Proxy và target

Một adapter chỉ chạy khi caller đi qua proxy. Có thể khẳng định mọi call trực tiếp tới target đều được quan sát không?

**Đáp án:** không. Phải kiểm thử interception scope thực tế.

### Câu 5 — Lỗi của processor

Original trả thành công, nhưng processor ghi RETURN tự ném RuntimeException. Có được log business THROW không?

**Đáp án:** không nếu đó chỉ là lỗi observer. Cần phân biệt nguồn lỗi và áp dụng failure policy đã định.

### Câu 6 — Async completion

Method trả Future bình thường, nhưng task lỗi sau đó. Event METHOD_RETURN có phải task success không?

**Đáp án:** không. Task completion cần event riêng ở đúng boundary.

### Câu 7 — Singleton service

Một EventProcessor singleton lưu currentRequestId trong field mutable. Nguy cơ?

**Đáp án:** nhiều requests/threads dùng chung state và trộn dữ liệu; nên truyền context theo Event hoặc scope thích hợp.

### Câu 8 — Handler ArrayList

Một SharedObservationService dùng ArrayList để ghi events, nhiều threads gọi accept đồng thời. Có chắc đúng không?

**Đáp án:** không. ArrayList không bảo đảm an toàn cho writes đồng thời; cần thiết kế state/collection phù hợp.

### Câu 9 — Xử lý policy

Một policy service trả REJECT. Ai cần chịu trách nhiệm không gọi continuation?

**Đáp án:** advice hoặc một lớp điều phối có hợp đồng rõ với pointcut/interception boundary; không giấu trong một observation handler.

### Câu 10 — Reentrancy

Processor gửi event bằng một method cũng bị cùng pointcut chọn. Có thể xảy ra gì?

**Đáp án:** vòng advice/processor lặp lại vô hạn. Cần scope exclusion hoặc guard reentrancy phù hợp.

### Câu 11 — Dữ liệu event

Handler chỉ cần phase và operation. Có nên chuyển toàn bộ mảng args chưa kiểm duyệt?

**Đáp án:** không. Chỉ dùng snapshot tối thiểu, đã lọc dữ liệu nhạy cảm.

### Câu 12 — Unit test

Có thể kiểm tra phân loại Event mà không chạy weaving không?

**Đáp án:** có, nếu processor nhận DTO/interface ổn định. Nhưng vẫn cần integration test cho điểm can thiệp và thứ tự thực tế.

### Câu 13 — Request vs attempt

Một retry khiến Original chạy ba lần. Handler nhận ba event FAILURE và một event REQUEST_COMPLETE. Số sự kiện có chắc sai?

**Đáp án:** không; nếu loại sự kiện khác nhau theo attempt/request thì có thể đúng. Cần tên và định nghĩa rõ.

### Câu 14 — Khi nào không cần advice?

Chỉ có một method muốn dùng SharedProcessor và không cần chọn điểm tự động. Có bắt buộc dùng AOP không?

**Đáp án:** không. Gọi service trực tiếp thường đơn giản hơn.

## 42. Tổng kết

**Tách cơ chế can thiệp khỏi dịch vụ xử lý dùng chung** là một cách tổ chức AOP để pointcut/weaver chỉ quyết định **khi nào và ở đâu advice được vào**, advice/adapter quyết định **cách chuyển dữ liệu và giữ hợp đồng lời gọi**, còn shared processor quyết định **cách xử lý dữ liệu đã chuẩn hóa**.

Những nguyên tắc quan trọng:

- **Chọn điểm khác xử lý sự kiện.** Pointcut và processor không nên bị trộn vào một class lớn.
- **Advice mỏng nhưng có trách nhiệm.** Nó phải bảo toàn return/throw và không gọi Original sai số lần.
- **Dùng DTO trung lập thay raw JoinPoint** khi logic xử lý không thực sự cần API engine.
- **Một shared service có thể phục vụ nhiều adapters**, kể cả một đường gọi trực tiếp không dùng AOP.
- **Reusability không tự bảo đảm thread safety.** State, vòng đời và concurrency cần hợp đồng rõ.
- **Failure của observation service không phải business failure.** Phải có fail-open/fail-closed policy có chủ đích.
- **Async registration, execution và completion là những boundary khác nhau.** Dùng eventKind/phase phù hợp.
- **Pointcut nên tránh can thiệp chính hạ tầng xử lý sự kiện** nếu nó tạo recursion.
- **Kiểm thử chia tầng**: processor độc lập, adapter theo contract, rồi integration trong runtime thật.
- **Không tự dùng AOP ở mọi nơi**: khi gọi trực tiếp một service là rõ ràng hơn thì đó có thể là lựa chọn hợp lý.

**Câu hỏi tự đánh giá thiết kế:** *Nếu ngày mai thay công cụ can thiệp hoặc thêm một loại join point mới, tôi có thể giữ nguyên dịch vụ xử lý dùng chung và chỉ sửa hoặc thêm một adapter mỏng hay không?*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-006">Thiết kế giao diện cấu hình rõ ràng cho aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế giao diện cấu hình rõ ràng cho aspect trong AOP

## 1. Vấn đề cần giải quyết

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một **aspect** đóng gói logic xuyên suốt như logging, metrics, audit, authorization, transaction, cache, validation hay retry. Một **pointcut** lựa chọn những **join point** phù hợp với mô hình của công cụ AOP; **advice** thực hiện hành vi tại các điểm đã chọn.

Khi chỉ có một aspect và một điểm áp dụng cố định, hard-code giá trị có vẻ nhanh:

```text
TraceAspect:
  target = "mọi method"
  enabled = true
  logFullArgs = true
  failureMode = "ignore"
  maxRetry = 5
```

Nhưng ngay khi nhiều nhóm nghiệp vụ cùng dùng logic, cách ấy tạo ra vấn đề:

- Không rõ ai có quyền chọn phạm vi áp dụng.
- Một thay đổi pointcut có thể vô tình áp advice lên logger, audit sink hoặc cả method bảo mật.
- Cấu hình theo chuỗi `"failmode"` dễ sai chính tả mà chỉ phát hiện lúc chạy.
- “Tắt logging” bị hiểu nhầm là “tắt validation bắt buộc”.
- Caller không biết một lỗi audit có chặn nghiệp vụ hay không.
- Các aspect dùng chung helper nhưng tự hiểu khác nhau về thứ tự, exception, timeout.
- Cập nhật config giữa các thread có thể làm một invocation thấy nửa cũ nửa mới.
- Giá trị mặc định có thể vô tình thay đổi contract khi nâng cấp.
- Chỉ nhìn file cấu hình không đủ biết class nào đã được weave.

**Giao diện cấu hình aspect rõ ràng** là một hợp đồng định nghĩa **những lựa chọn hợp lệ**, **ý nghĩa**, **mặc định**, **giới hạn**, **thời điểm có hiệu lực** và **đường kiểm chứng** của từng lựa chọn, sao cho việc thay đổi cấu hình không tạo hành vi bất ngờ.

Trong bài này, “giao diện” có thể là Java typed API, record/configuration object, properties/XML/YAML có schema, builder, một API quản trị hoặc một biểu thức pointcut được công cụ hỗ trợ. **Không có một định dạng cấu hình thống nhất cho mọi engine AOP.** Mục tiêu là thiết kế contract độc lập với công cụ, rồi ánh xạ chính xác sang cơ chế can thiệp thực tế.

## 2. Mục tiêu học tập

Kết thúc bài, người học có thể:

1. Phân chia cấu hình aspect thành **phạm vi**, **hành vi**, **thứ tự**, **lifecycle** và **vận hành**.
2. Thiết kế các **kiểu có ý nghĩa**, enum, immutable value object và các điều kiện kiểm tra hợp lệ.
3. Phân biệt cấu hình một **pointcut** với cấu hình **policy của advice**.
4. Mô tả rõ hành vi mặc định, trạng thái disabled và failure modes.
5. Quản lý cấu hình của **abstract aspect** và những concrete aspects tái sử dụng hành vi chung.
6. Không nhầm tính năng **proxy interception** với **compile-time/load-time weaving**.
7. Hiểu vì sao precedence, retry, timeout, context và log masking thuộc contract.
8. Thiết kế và kiểm thử cập nhật cấu hình an toàn khi có concurrent invocation.
9. Biết giới hạn của cấu hình pointcut qua XML ở một công cụ có hỗ trợ.
10. Viết một ví dụ Java 17 runnable để kiểm tra cấu hình typed, lựa chọn operation, best-effort/fail-closed, đường ngoại lệ và bypass.

## 3. Một giao diện cấu hình tốt phải trả lời điều gì?

Một bản mô tả config cần có ít nhất:

| Lĩnh vực | Câu hỏi |
| --- | --- |
| Identity | Cấu hình thuộc aspect nào? Có version và owner không? |
| Applicability | Operation/join point nào được chọn? Bao gồm/exclude gì? |
| Binding | Advice cần args, target, signature, annotation hay runtime context nào? |
| Behavior | Advice chỉ quan sát hay được chặn, sửa args, retry, trả fallback? |
| Failure | Lỗi của dependency phụ trợ có ảnh hưởng return/exception target không? |
| Ordering | Nếu nhiều aspect cùng match, ai ở ngoài/ở trong? |
| Lifecycle | Config là immutable, per instance, per request hay global? |
| Defaults | Không cung cấp option thì hành vi cụ thể là gì? |
| Validation | Giá trị nào hợp lệ, kiểm tra ở lúc nào? |
| Security | Ai được sửa scope hoặc disable aspect? Có dữ liệu nhạy cảm không? |
| Change model | Config áp ngay, chỉ với invocation mới hay phải restart/reweave? |
| Evidence | Có cách xem *effective configuration* và kiểm tra coverage không? |

**Điểm mấu chốt:** một giao diện được gọi là rõ ràng khi *người dùng không cần đọc implementation để hiểu hậu quả hành vi*. Tên `enabled` hoặc `timeout=1000` không đủ nếu không biết **enable cái gì**, **1000 đơn vị gì**, và nó được áp ở đâu.

## 4. Phân tách năm nhóm cấu hình

### 4.1. Cấu hình phạm vi

Ví dụ:

```text
aspectId = order-tracing
joinPointKind = method-execution
include = OrderService.submit, OrderService.cancel
exclude = TraceSink.write
boundary = woven-method-execution
```

Đây là schema minh họa, **không phải cú pháp cấu hình của một công cụ AOP cụ thể**.

Định nghĩa phạm vi cần biết **join-point kind** và **interception boundary**. Một câu “áp cho mọi service” không đủ xác nhận runtime coverage.

### 4.2. Cấu hình hành vi

```text
emitStart = true
emitSuccess = true
emitFailure = true
captureArguments = allowlisted-fields-only
onObserverFailure = BEST_EFFORT
```

Các lựa chọn này không quyết định method có match pointcut hay không; chúng quyết định **advice làm gì khi đã được kích hoạt**.

### 4.3. Cấu hình thứ tự

```text
outermost: Authorization
middle: Transaction
innermost: DiagnosticTrace
```

Đây là sơ đồ precedence dự kiến, không phải lời khẳng định engine nào cũng hỗ trợ cùng cú pháp. Vị trí của advice ảnh hưởng chuyện có thấy lỗi, thấy kết quả đã biến đổi, hoặc có bị short-circuit không.

### 4.4. Cấu hình lifecycle và context

```text
configScope = application-snapshot
invocationState = per-invocation
traceContext = propagate-with-task
guardContext = local-emission-only
```

Đừng trộn **configuration data** vốn tương đối ổn định với **state của một invocation** như start time, current depth hoặc correlation ID.

### 4.5. Cấu hình vận hành

```text
observerTimeout = PT250MS
maxAttempts = 1
maxEventBytes = 4096
failurePolicy = BEST_EFFORT
```

Đây là các **ví dụ giá trị** để minh họa ý nghĩa trường, **không phải khuyến nghị mặc định áp dụng cho mọi hệ thống**. Cần chọn số từ nhu cầu nghiệp vụ, chi phí thực tế và mô hình lỗi.

## 5. Nguyên tắc thiết kế: tách selection khỏi policy

Một cấu trúc tốt có thể gồm:

```text
AspectDefinition
  |
  +-- SelectionSpec
  |     joinPointKind
  |     includeRules
  |     excludeRules
  |     dynamicConditions
  |
  +-- AdvicePolicy
  |     enabled
  |     eventKinds
  |     failureMode
  |     transformOrObserve
  |
  +-- OrderingSpec
  |     relativePrecedence
  |
  +-- RuntimeSpec
        timeouts
        sampling
        contextPropagation
        lifecycle
```

**Selection** trả lời “ở đâu”; **policy** trả lời “làm gì”.

Tách riêng có lợi:

- Có thể tái sử dụng cùng audit behavior cho Order và Payment với selection khác nhau.
- Có thể giữ nguyên pointcut nhưng thay cách ghi event.
- Có thể review thay đổi scope như thay đổi về coverage.
- Có thể review thay đổi exception/fallback như thay đổi hợp đồng API.
- Có thể khóa một số lựa chọn trong profile production mà vẫn cho phép đổi sampling.

### 5.1. Những option dễ đặt sai nhóm

```text
enableAudit = false
```

Câu này mơ hồ. Nó có thể có nghĩa:

1. Không weave audit aspect.
2. Pointcut không chọn invocation nào.
3. Advice vẫn chạy nhưng không ghi event.
4. Ghi event nội bộ nhưng không gửi sink.
5. Chỉ tắt metrics, giữ audit bắt buộc.

Giao diện rõ nên dùng tên/hợp đồng riêng cho các ý nghĩa này, ví dụ `auditSinkEnabled`, `traceSamplingEnabled`, `aspectScopeEnabled`, và giới hạn quyền tắt bảo vệ bắt buộc.

## 6. Chọn kiểu dữ liệu thay vì chuỗi mơ hồ

Ví dụ cấu hình kém:

```text
mode = "beSt-effrot"
retry = "-2"
timeout = "100"
scope = "*"
```

Nếu mọi thứ là `String`, lỗi có thể xuất hiện xa nơi người vận hành nhập cấu hình.

Phương án cải thiện:

- `enum FailureMode { BEST_EFFORT, FAIL_CLOSED }`.
- `Duration` cho timeout, kèm ràng buộc dương và giới hạn.
- Số nguyên đã validate cho max attempts.
- `Set<OperationId>` thay cho một chuỗi ngăn cách bằng dấu phẩy không rõ escaping.
- `boolean` cho các option thật sự hai trạng thái.
- Một object `SelectionSpec` mô tả include/exclude rõ.
- Một enum `ConfigStage` khi cần phân biệt compile/weave/load/runtime.

### 6.1. Không phải mọi boolean đều dễ hiểu

```text
strict = true
```

“Strict” nghĩa là gì? Có thể là validation nghiêm, audit fail-closed, điểm chọn hẹp, hoặc chống fallback.

Tên tốt hơn:

```text
rejectInvalidArguments = true
onAuditSinkFailure = FAIL_CLOSED
allowFallback = false
```

Dù vậy, muốn hiểu đúng vẫn phải có **tài liệu ngữ nghĩa**, không chỉ tên biến đẹp.

### 6.2. Dùng đơn vị rõ ràng

```text
timeoutMillis = 500
```

tốt hơn `timeout=500`; nhưng với Java API, `Duration.ofMillis(500)` còn truyền đạt đơn vị rõ và hỗ trợ kiểm hợp lệ.

Một config typed nên từ chối `Duration.ZERO` hoặc âm nếu timeout bắt buộc dương, thay vì chấp nhận rồi biến thành “không timeout” không có tài liệu.

## 7. Immutable configuration và snapshot nhất quán

Một config thường được đọc rất nhiều lần. Nếu thay từng field trên object dùng chung:

```text
Thread A: reads enabled=true
Thread B: sets failureMode=FAIL_CLOSED
Thread A: reads failureMode=FAIL_CLOSED
Thread B: sets enabled=false
```

một invocation có thể thấy tổ hợp trạng thái **chưa bao giờ được công bố như một phiên bản config hoàn chỉnh**.

Cách thiết kế an toàn hơn:

1. Đọc input cấu hình thô.
2. Parse và validate **toàn bộ**.
3. Tạo một immutable `AspectConfig`.
4. Công bố atomically một reference tới config mới.
5. Mỗi invocation lấy **một snapshot** khi bắt đầu và dùng xuyên suốt.

Trong Java, một `record` với các component immutable/được copy phù hợp giúp làm object cấu hình rõ hơn. Tuy nhiên **record chỉ shallowly immutable**: nếu chứa `Set` hoặc `List` mutable do caller giữ, cần defensive copy như `Set.copyOf`.

### 7.1. Config immutable khác invocation state

Không đặt `currentDepth`, `startedAt`, `lastException` hoặc `retryAttempt` vào `AspectConfig`. Chúng thuộc mỗi lần gọi, có vòng đời khác.

### 7.2. Atomic publication không sửa được mọi thứ

Thay reference config atomically không tự thay **điểm đã weave** hoặc rút/đổi method signature. Nó chỉ giúp những behavior **đọc config runtime** nhìn bản snapshot nhất quán. Scope weaving có thể đã được quyết định sớm hơn.

## 8. Kiểm tra giá trị ngay tại ranh giới cấu hình

Một validator nên từ chối:

- `null` trong tập field bắt buộc.
- Tên operation rỗng hoặc chỉ whitespace.
- Duplicate IDs không rõ có cho phép hay không.
- Enum không được hỗ trợ.
- Timeout âm hoặc quá lớn ngoài policy.
- Retry attempts bằng 0/âm khi hợp đồng yêu cầu ít nhất một lần.
- Bật fallback nhưng không có fallback policy hợp lệ.
- Bật audit bắt buộc nhưng không có sink/config credentials cần thiết.
- Phạm vi include/exclude mâu thuẫn theo quy tắc policy.
- Precedence cycle giữa nhiều aspects.
- Cấu hình đòi capture data không được phép.
- Một rule dựa trên feature A nhưng feature A bị tắt.

### 8.1. Parse, validate, activate là ba pha

```text
raw input
   -> parse syntax
   -> semantic validation
   -> construct immutable config
   -> verify compatibility with deployed weaver
   -> publish/activate
```

Parse thành công không có nghĩa **pointcut chọn được join point** hoặc aspect có quyền chạy tại runtime. Validation semantic và integration verification vẫn cần.

### 8.2. Kiểm dependency giữa option

Ví dụ:

```text
if retryEnabled:
    maxAttempts >= 2
    operation must be safe to retry or protected by idempotency
```

Điều kiện thứ hai không thể chứng minh chỉ từ một boolean. Nó cần API metadata/hợp đồng nghiệp vụ hoặc danh sách operation cho phép. Interface tốt không hứa rằng cấu hình đúng cú pháp đồng nghĩa retry an toàn.

## 9. Mặc định phải được công bố, không phụ thuộc tình cờ

Ví dụ một policy tracing có thể chọn:

| Option | Mặc định minh họa | Điều phải giải thích |
| --- | --- | --- |
| enabled | false | Tắt emission hay tắt interception? |
| selected operations | empty set | Không chọn thao tác nào khi chưa khai báo |
| failure mode | BEST_EFFORT | Lỗi observer không làm lỗi business trong phạm vi cho phép |
| argument logging | disabled | Không log toàn bộ dữ liệu đầu vào |
| sampling | 100% hoặc 0% tùy mục tiêu | Đơn vị phần trăm và cách quyết định |
| max attempts | 1 | Không tự retry, tránh nhân đôi side effect |
| output redaction | mandatory | Không cho tắt khi chứa thông tin nhạy cảm |
| config reload | restart-required | Bảo đảm khi nào có hiệu lực |

Đây là **những lựa chọn gợi ý để bàn về contract**, không phải “default đúng cho mọi aspect”. Với authorization, “disabled by default” có thể cực kỳ nguy hiểm; chính sách bảo mật phải được thiết kế trên trust boundary và fail-safe behavior.

### 9.1. Đặt lại mặc định là thay đổi tương thích

Nếu release mới đổi `maxAttempts=1` thành `3`, một operation trước đây chạy một lần có thể chạy đến ba lần khi lỗi. Dù caller không thay config, behavior public **đã thay đổi**.

Vì vậy version của config schema và các giá trị mặc định phải thuộc release notes.

## 10. Scope: cấu hình chỉ định join-point kind thật sự

Một câu `include="OrderService.*"` chưa nói được:

- Chọn **method call** hay **method execution**?
- Có tính constructor, field access, getter không?
- Có method private/final/static không?
- Có self-invocation không?
- Target binary có nằm trong weaving scope không?
- Có runtime guard dựa trên args/annotation không?
- Có lựa chọn method inherited/bridge/generic không?

**Không chuẩn hóa các câu hỏi này bằng suy đoán.** Một mô hình AOP có thể hỗ trợ call sites, một mô hình khác chỉ intercept interface proxy, mô hình khác có bytecode weaving.

### 10.1. Hai chiều coverage

1. **Pointcut match:** logic chọn join point có đúng không?
2. **Execution reachability:** invocation có thực sự đi qua boundary được can thiệp không?

Với proxy interface, target được tạo bằng `new` và dùng trực tiếp có thể bỏ qua checker. Với compile-time weaving vào execution body, phạm vi có thể khác.

### 10.2. Include/exclude và ưu tiên

Nếu cùng một operation nằm trong `include` và `exclude`, config phải quy định kết quả. Một quy tắc dễ giải thích là **exclude thắng include**, nhưng không tự nhận mọi tool dùng thứ tự này.

Nếu dùng matcher phức tạp, cần có API mô tả match result và lý do:

```text
operation=OrderService.cancel
selected=false
reason=excluded-by-audit-internals
```

Ví dụ là output chẩn đoán thiết kế, không phải output từ một engine cụ thể.

## 11. Cấu hình hành vi advice: observation hay transformation?

Một aspect **quan sát** được kỳ vọng:

```text
Target returns R -> caller receives R
Target throws E -> caller receives E
Target execution count is not changed
```

Nhưng nếu observer ném lỗi, cách triển khai sơ suất có thể phá tính trong suốt. Vì vậy cần option `onObserverFailure` với contract rõ.

Một aspect **thay đổi** hành vi có thể:

- Từ chối trước khi gọi target.
- Sửa args truyền xuống.
- Thay result trả lên.
- Đổi exception thành domain exception.
- Trả fallback.
- Retry `proceed` nhiều lần.
- Điều chỉnh transaction/bảo mật context.

**Không đặt tất cả dưới một option chung `mode=ENABLED`.** Với transformation, mỗi lựa chọn cần ràng buộc bảo toàn API contract và các test tương ứng.

### 11.1. Fallback không chỉ là một giá trị

Nếu target cập nhật dữ liệu nhưng fallback trả `"OK"` sau lỗi từ audit sink, caller có thể hiểu sai giao dịch đã được commit. Cấu hình fallback phải mô tả loại outcome, thời điểm sử dụng và trạng thái thật đã xác minh được.

### 11.2. Retry không phải một tính năng logging

Chọn `retryAttempts=3` có thể nhân side effects, độ trễ và số event. Nó cần semantics riêng: loại lỗi được retry, backoff, idempotency, transaction boundary, cancellation và time budget. Đừng đặt retry vào tracing aspect chỉ vì cơ chế quanh-advice hỗ trợ `proceed` nhiều lần.

## 12. Error modes: BEST_EFFORT và FAIL_CLOSED

### 12.1. BEST_EFFORT

Một observer phụ trợ như metrics exporter có thể thất bại nhưng caller vẫn nhận outcome target. Policy:

```text
On observer error:
  record a safe local diagnostic
  do not alter target return/exception
  do not re-run target
```

Cần giới hạn: nếu helper ghi diagnostic lại dùng chính sink hỏng, có thể tạo recursion; nếu target chưa chạy, việc observer thất bại vẫn cần policy rõ.

### 12.2. FAIL_CLOSED

Khi không hoàn thành bước bắt buộc trước target, một policy có thể **từ chối thực hiện target**. Ví dụ ghi audit trước khi thực hiện thao tác nhạy cảm được yêu cầu bởi nghiệp vụ.

```text
On mandatory pre-operation audit failure:
  do not execute target
  propagate a documented failure
```

**Không tự suy rằng FAIL_CLOSED luôn rollback.** Nếu bắt lỗi phụ trợ xảy ra **sau khi target chạy**, caller có thể nhận exception trong khi side effect đã xảy ra. Hợp đồng phải chia theo **phase**.

### 12.3. Đừng bắt tất cả RuntimeException rồi gọi là observer error

Ví dụ sai:

```text
try:
    emitStart()
    result = target()
    emitSuccess()
    return result
catch anyError:
    if bestEffort: return fallbackSuccess
```

Catch trên bao cả **business error** và observer error; hành vi best-effort có thể nuốt lỗi nghiệp vụ. Cần giới hạn phạm vi catch tới đúng call observer, và truyền business error theo hợp đồng.

## 13. Ví dụ Java 17 chạy được: cấu hình typed, immutable và hai failure modes

Ví dụ sau không dùng weaver bên ngoài. Nó là một **interceptor/decorator viết bằng Java**, minh họa **cách thiết kế interface cấu hình**, không phải một abstract aspect hay pointcut compiler.

Các cấu hình:

- `AspectConfig` là Java record và copy tập operation thành set immutable.
- `FailureMode` là enum, không nhận chuỗi tùy ý.
- `enabled` quyết định có áp wrapper behavior hay không.
- `selected` là tập operation được aspect áp dụng.
- `traceEnabled` quyết định có gửi event sang observer hay không.
- Lỗi observer dưới `BEST_EFFORT` được xử lý trong **chính hàm emit**, không nuốt business exception.
- `FAIL_CLOSED` cho phép lỗi observer trước target chặn lời gọi.
- Direct target call bỏ qua wrapper.
- Một config invalid bị từ chối khi tạo, trước khi chạy nghiệp vụ.

Tạo file `AspectConfigDemo.java`:

```java
import java.util.Objects;
import java.util.Set;

public class AspectConfigDemo {
    enum FailureMode {
        BEST_EFFORT,
        FAIL_CLOSED
    }

    record AspectConfig(
            boolean enabled,
            Set<String> selected,
            boolean traceEnabled,
            FailureMode failureMode) {

        AspectConfig {
            Objects.requireNonNull(selected, "selected");
            Objects.requireNonNull(failureMode, "failureMode");
            selected = Set.copyOf(selected);

            for (String operation : selected) {
                if (operation.isBlank()) {
                    throw new IllegalArgumentException(
                            "selected contains blank operation");
                }
            }
        }
    }

    @FunctionalInterface
    interface Work {
        String run(String operation);
    }

    @FunctionalInterface
    interface Observer {
        void emit(String event);
    }

    static void emit(AspectConfig config,
                     Observer observer,
                     String event) {
        if (!config.traceEnabled()) {
            return;
        }

        try {
            observer.emit(event);
        } catch (RuntimeException error) {
            if (config.failureMode() == FailureMode.FAIL_CLOSED) {
                throw error;
            }
            System.out.println("aspect:observer-failure:" + event);
        }
    }

    static Work configured(
            Work target, AspectConfig config, Observer observer) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(observer, "observer");

        return operation -> {
            if (!config.enabled()
                    || !config.selected().contains(operation)) {
                return target.run(operation);
            }

            System.out.println("aspect:enter:" + operation);
            try {
                emit(config, observer, "start:" + operation);

                String result;
                try {
                    result = target.run(operation);
                } catch (RuntimeException businessError) {
                    System.out.println("aspect:business-error:"
                            + operation + ":"
                            + businessError.getClass().getSimpleName());
                    emit(config, observer, "error:" + operation);
                    throw businessError;
                }

                emit(config, observer, "success:" + operation);
                System.out.println("aspect:return:" + operation);
                return result;
            } finally {
                System.out.println("aspect:exit:" + operation);
            }
        };
    }

    static void call(String label, Work work, String operation) {
        System.out.println("case:" + label);
        try {
            System.out.println("caller:" + work.run(operation));
        } catch (RuntimeException error) {
            System.out.println("caller:error:"
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
    }

    public static void main(String[] args) {
        Work target = operation -> {
            System.out.println("business:" + operation);
            if ("FAIL".equals(operation)) {
                throw new IllegalStateException("operation failed");
            }
            return "ok:" + operation;
        };

        Observer observer = event -> {
            if ("start:FAULT".equals(event)) {
                throw new IllegalStateException("sink offline");
            }
            System.out.println("observer:" + event);
        };

        Set<String> selected =
                Set.of("SAVE", "FAIL", "FAULT");

        AspectConfig bestEffort = new AspectConfig(
                true, selected, true, FailureMode.BEST_EFFORT);
        AspectConfig failClosed = new AspectConfig(
                true, selected, true, FailureMode.FAIL_CLOSED);
        AspectConfig disabled = new AspectConfig(
                false, selected, true, FailureMode.FAIL_CLOSED);

        Work managed = configured(target, bestEffort, observer);
        Work strict = configured(target, failClosed, observer);
        Work noAdvice = configured(target, disabled, observer);

        call("selected", managed, "SAVE");
        call("unselected", managed, "SKIP");
        call("business-error", managed, "FAIL");
        call("best-effort-sink", managed, "FAULT");
        call("fail-closed-sink", strict, "FAULT");
        call("disabled", noAdvice, "SAVE");
        call("direct-target", target, "SAVE");

        System.out.println("case:invalid-config");
        try {
            new AspectConfig(
                    true, Set.of(" "),
                    true, FailureMode.BEST_EFFORT);
        } catch (IllegalArgumentException error) {
            System.out.println("config:error:" + error.getMessage());
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AspectConfigDemo.java
java AspectConfigDemo
```

**Stdout chính xác cho chương trình:**

```text
case:selected
aspect:enter:SAVE
observer:start:SAVE
business:SAVE
observer:success:SAVE
aspect:return:SAVE
aspect:exit:SAVE
caller:ok:SAVE
case:unselected
business:SKIP
caller:ok:SKIP
case:business-error
aspect:enter:FAIL
observer:start:FAIL
business:FAIL
aspect:business-error:FAIL:IllegalStateException
observer:error:FAIL
aspect:exit:FAIL
caller:error:IllegalStateException:operation failed
case:best-effort-sink
aspect:enter:FAULT
aspect:observer-failure:start:FAULT
business:FAULT
observer:success:FAULT
aspect:return:FAULT
aspect:exit:FAULT
caller:ok:FAULT
case:fail-closed-sink
aspect:enter:FAULT
aspect:exit:FAULT
caller:error:IllegalStateException:sink offline
case:disabled
business:SAVE
caller:ok:SAVE
case:direct-target
business:SAVE
caller:ok:SAVE
case:invalid-config
config:error:selected contains blank operation
```

### 13.1. Vì sao dùng record và `Set.copyOf`?

`AspectConfig` có các component được gắn như state cuối của record. Trong compact constructor, `Set.copyOf(selected)` tạo một tập không thể sửa qua reference trả về. Nếu caller ban đầu có một `HashSet` rồi sửa nó, config đã tạo không phải tự đổi theo.

Cần ghi nhớ: immutable container không đồng nghĩa immutable mọi element. Với `Set<String>`, `String` immutable nên phù hợp. Nếu selected chứa object mutable, cần xem defensive copy sâu/identity semantics.

### 13.2. Vì sao selected=SKIP không có advice?

Operation `"SKIP"` không thuộc `Set.of("SAVE","FAIL","FAULT")`, nên wrapper chuyển tiếp thẳng đến target. Điều đó mô phỏng **điều kiện lựa chọn runtime**. Trong một engine có pointcut DSL, matching có thể diễn ra ở giai đoạn compile/weave khác.

### 13.3. Vì sao lỗi nghiệp vụ không bị nuốt trong BEST_EFFORT?

`target.run("FAIL")` ném `IllegalStateException`. Nó được bắt ở khối try **chỉ bao target**, ghi phân loại `business-error` rồi ném lại lỗi gốc. `emit` chỉ bắt lỗi phát sinh từ `observer.emit`.

Một interface cấu hình ghi `BEST_EFFORT` phải nói rõ **best effort của observer**, chứ không có nghĩa “nuốt mọi exception trong chuỗi AOP”.

### 13.4. Vì sao FAULT khác nhau giữa hai chế độ?

Observer cố ý ném `IllegalStateException("sink offline")` ở sự kiện `start:FAULT`.

- Với **BEST_EFFORT**, emit bắt lỗi và in `aspect:observer-failure:start:FAULT`; business vẫn thực thi và caller nhận `ok:FAULT`.
- Với **FAIL_CLOSED**, lỗi phát ra trước target nên caller nhận lỗi sink, **không có dòng `business:FAULT` ở ca này**.

Việc in dòng `aspect:exit:FAULT` trên đường fail-closed là do `finally` bao toàn bộ khối trong wrapper. Nó không chứng minh business đã chạy.

### 13.5. Disabled và direct target

Trường hợp disabled trả về từ wrapper ngay qua nhánh không áp advice. Trường hợp direct target hoàn toàn không dùng wrapper. Output giống nhau, nhưng nguyên nhân khác:

- Disabled: lời gọi đi qua wrapper, policy nói bỏ instrumentation.
- Direct: lời gọi không đi qua wrapper.

Trong production, đặc biệt với authorization hoặc validation bắt buộc, cả hai đường cần chính sách bảo vệ rõ.

### 13.6. Không coi ví dụ Java này là một AOP engine

Nó **không** tự chọn method execution/call join points từ bytecode, không hỗ trợ pointcut DSL, không weave class, không xử lý self-invocation/proxy infrastructure hay multiple-advice precedence. Nó chỉ cho thấy **một thiết kế giao diện cấu hình hợp lệ, typed và có thể kiểm thử**.

### 13.7. Giới hạn failure modes của demo

Nếu observer trên đường `success` ném lỗi dưới FAIL_CLOSED, business có thể đã hoàn tất nhưng caller sẽ nhận exception. Đây là hậu quả quan trọng cần ghi trong hợp đồng. Một giao diện production có thể phải phân tách lỗi ở **before**, **after normal return** và **after exceptional completion** thay vì dùng cùng một `FailureMode` cho cả ba giai đoạn.

## 14. Cấu hình như một phần của hợp đồng public API

Một config có thể đổi behavior caller nhìn thấy mà **không đổi chữ ký method**.

Ví dụ:

- Từ `BEST_EFFORT` sang `FAIL_CLOSED`: một lỗi sink mới có thể chặn target.
- Từ `maxAttempts=1` sang `3`: operation có thể chạy nhiều lần.
- Từ `traceEnabled=false` sang `true`: có thêm I/O/latency và rủi ro dữ liệu nhạy cảm.
- Đổi pointcut: một method trước đây không được advice giờ bị từ chối bởi validation.
- Đổi precedence: một authorization advice có thể chạy trước transaction thay vì sau.
- Đổi default sampling: số event/chi phí và khả năng audit thay đổi.
- Đổi exception translation: caller phải xử lý loại lỗi mới.
- Đổi scope từ execution sang call: coverage và self-invocation có thể thay đổi.

**Đừng xem config là “chuyện nội bộ” nếu nó thay lời hứa public.** Version hóa và review nó như thay đổi API/behavior.

## 15. Giao diện cấu hình cho abstract aspect và concrete aspect

Khi dùng một **abstract aspect** để tái sử dụng phần hành vi chung:

```text
AbstractAudit:
  abstract selectOperations()
  common audit-before/after/throw logic

OrderAudit extends AbstractAudit:
  selectOperations = Order-related join points

PaymentAudit extends AbstractAudit:
  selectOperations = Payment-related join points
```

Base aspect cung cấp hợp đồng ổn định, concrete aspect cung cấp phạm vi/chính sách chuyên biệt.

Một mô hình config rõ có thể tách:

```text
SharedAuditConfig:
  event schema
  redaction
  sink behavior
  error mode

OrderAuditSelection:
  order scope include/exclude

PaymentAuditSelection:
  payment scope include/exclude
```

### 15.1. Đừng biến base aspect thành “god aspect”

Nếu base vừa chọn mọi operation, vừa quyết định authorization, transaction, retry, log formatter, error translation và sink configuration, mọi concrete aspect sẽ bị coupling với quá nhiều điều không liên quan.

Nên để base thể hiện những **bất biến của behavior dùng chung**, còn các chính sách tùy biến ở những component/hook/strategy rõ ràng.

### 15.2. Một abstract pointcut là một phần của config contract

Ở ngôn ngữ hỗ trợ, concrete aspect có thể hiện thực abstract pointcut bằng biểu thức lựa chọn khác. Cần biết:

- Pointcut có tham số/binding hay không.
- Có cho phép execution/call/constructor hay chỉ một tập join-point kind?
- Các dynamic guards có ý nghĩa gì?
- Mỗi concrete aspect được kích hoạt theo lifecycle nào?
- Có bao nhiêu concrete aspects có thể chọn cùng một method?
- Precedence giữa chúng được công bố ở đâu?

Một pointcut biến thiên là **configuration boundary có hậu quả đúng đắn**, không chỉ là nơi để tùy ý viết wildcard.

## 16. Ví dụ cấu hình công cụ cụ thể: AspectJ và `aop.xml`

Một khả năng của **AspectJ load-time weaving** là dùng `META-INF/aop.xml` để xác định những aspects được dùng, những types được weave và một số option của weaver.

Phần này **chỉ minh họa cú pháp của công cụ đó**. Nó không phải định dạng cấu hình AOP chung và không thể chạy chỉ với `javac`.

Ví dụ abstract aspect theo cú pháp **AspectJ code-style**:

```java
package demo;

public abstract aspect AbstractTrace {
    public abstract pointcut scope();

    before(): scope() {
        System.out.println("trace:before");
    }
}
```

Một file `META-INF/aop.xml` minh họa:

```xml
<aspectj>
  <aspects>
    <concrete-aspect
        name="demo.ConfiguredOrderTrace"
        extends="demo.AbstractTrace">
      <pointcut
          name="scope"
          expression="execution(* demo.OrderService.*(..))"/>
    </concrete-aspect>
  </aspects>
  <weaver>
    <include within="demo..*"/>
  </weaver>
</aspectj>
```

Ý nghĩa:

1. `AbstractTrace` khai báo một abstract pointcut `scope()`.
2. `concrete-aspect` chỉ định một concrete aspect được tạo dựa trên abstract aspect.
3. `pointcut name="scope"` cung cấp biểu thức cụ thể.
4. `weaver/include` giới hạn types được weave, **khác với** biểu thức pointcut.
5. Class `demo.OrderService` cần thực sự tồn tại, và build/deployment phải hỗ trợ weaving.

Không được coi snippet này là một “file cấu hình chạy được ngay” nếu chưa có class target, compiler/weaver, runtime library và VM agent thích hợp.

### 16.1. Những giới hạn quan trọng của cơ chế này

Theo tài liệu chính thức về concrete aspects trong `aop.xml` của AspectJ:

- Parent aspect phải **abstract**.
- Những abstract pointcut được cung cấp theo cơ chế này phải là loại đơn giản **không expose state/bind arguments** như những pointcut có `args()`, `this()`, `target()` hay `if()`.
- Concrete aspect định nghĩa trong XML phải hoàn thành **tất cả abstract pointcuts**.
- Cơ chế XML này không hiện thực arbitrary **abstract methods** của parent aspect; nếu base có abstract methods thì cần mô hình kế thừa bằng code phù hợp.
- Aspect name được dùng tạo một concrete type; không được xung đột với class name đã có.
- Availability và weaving scope phụ thuộc classloader/configuration.
- Muốn inheritance phức tạp, có thể cần **regular aspect inheritance**, thay vì XML.

Đây là giới hạn **công cụ cụ thể**, không phải ràng buộc của mọi cách tổ chức abstract aspects.

### 16.2. Ba lớp lựa chọn khác nhau

Trong ví dụ AspectJ:

- `<aspect>` hoặc `<concrete-aspect>`: **những aspect nào** được giới thiệu cho weaver.
- `<weaver><include .../>`: **những type nào** nằm trong phạm vi weaving.
- `<pointcut expression="..."/>`: **những join point nào** được advice chọn khi weaving/runtime conditions phù hợp.

Tập chọn ở một tầng rộng **không bảo đảm** tầng khác cũng bao phủ. “Pointcut khớp trong lý thuyết” không tương đương “advice chạy trên binary production”.

### 16.3. Khi nào dùng cấu hình theo deployment?

Khi phạm vi weaving/selection thay đổi theo deployment và công cụ hỗ trợ, external configuration có thể tiện. Nhưng những khác biệt ảnh hưởng type binding, argument capture hoặc requirement của advice vẫn có thể buộc thay source/build.

Cần version `aop.xml` cùng artifact, không đổi tùy ý trên production mà không regression test.

## 17. Không trộn cấu hình build-time với runtime feature flags

Một số thuộc tính quyết định **class nào được weave trước khi load**. Một số chỉ là policy được advice đọc **khi invocation xảy ra**.

Ví dụ:

| Thuộc tính | Thường gắn với pha | Hệ quả |
| --- | --- | --- |
| Join-point kind được compiler hỗ trợ | Language/toolchain | Không thể thêm một loại mới chỉ bằng flag |
| Class include/exclude ở load-time weaving | Before class definition | Class đã nạp có thể không được can thiệp lại |
| Pointcut được compile thành advice | Compile/weave | Phải giữ compiler/runtime tương thích |
| Sampling rate của logger | Runtime | Có thể thay nếu advice đã hỗ trợ đọc policy |
| Sink endpoint/timeout | Runtime hoặc startup | Phụ thuộc lifecycle client/sink |
| FailureMode runtime | Invocation snapshot | Thay đổi error behavior quan sát được |
| New interface/method introduction | Structural weaving | Không phải feature flag runtime đơn giản |

### 17.1. Nạp class và giới hạn cập nhật

Đừng hứa “config đổi thì mọi class vừa chạy sẽ được can thiệp ngay”. Trong nhiều mô hình weaving, chỉ những class **chưa được định nghĩa cho VM** mới có thể được biến đổi ở load-time. JVM class redefinition/retransformation có những hạn chế riêng, đặc biệt với **đổi cấu trúc type**.

### 17.2. Snapshot theo invocation

Với runtime-configurable advice, nên có quy tắc:

```text
Each invocation obtains one effective config version on entry.
That version is used until the invocation reaches its documented completion boundary.
```

Nếu async continuation hoàn tất sau này, cần quyết định dùng config snapshot ban đầu hay bản mới khi completion callback chạy. Cả hai có thể hợp lý, nhưng phải **được công bố**.

## 18. Mô hình precedence cần rõ ràng

Khi có nhiều aspect, thứ tự làm thay đổi kết quả.

```text
Authorization(
    Transaction(
        Audit(
            Business
        )
    )
)
```

Theo lồng nhau đó:

- Authorization có thể từ chối trước Transaction.
- Transaction có thể bắt exception từ phần bên trong.
- Audit có thể thấy outcome của Business trước một số biến đổi ở lớp ngoài.
- Cleanup chạy theo phạm vi cụ thể của mỗi advice.
- Nếu Authorization không tiếp tục, Audit không chạy trừ khi một lớp ngoài khác có trách nhiệm ghi rejected attempts.

**Không có một precedence mặc định chung cho mọi AOP engine.** Một giao diện cấu hình nên mô tả quan hệ như `before/after` hoặc một thứ tự tổng đã kiểm tra.

### 18.1. Kiểm chu trình quan hệ

Nếu operator cấu hình:

```text
A before B
B before C
C before A
```

đó là một chu trình ràng buộc. Validator nên từ chối thay vì “chọn đại” một thứ tự.

### 18.2. Precedence giữa abstract và concrete aspects

Với ngôn ngữ có aspect inheritance, có thể có nhiều concrete aspects kế thừa cùng base và cùng chọn một join point. Base được viết một lần **không đồng nghĩa advice chạy một lần**. Config phải nhận diện từng concrete aspect và mô tả overlap/precedence.

## 19. Scope exclusions để tránh aspect tự kích hoạt

Logging advice gọi logger, logger lại có thể khớp pointcut. Với cấu hình quá rộng:

```text
Business.method
  -> TraceAdvice
      -> TraceSink.write
          -> TraceAdvice
              -> TraceSink.write ...
```

Các chiến lược:

1. Chọn scope nghiệp vụ hẹp ngay từ đầu.
2. Loại chính xác logger/audit sink/formatting helpers nếu không cần advice.
3. Dùng lexical exclusion nếu engine hỗ trợ.
4. Dùng control-flow exclusion khi cần phân biệt cùng helper được gọi từ advice và business.
5. Dùng reentrancy guard hẹp theo invocation/scope; cleanup bằng `finally`.
6. Test nested business calls vẫn được instrument nếu chúng thuộc hợp đồng.

Không dùng config đơn giản kiểu `skipAllNestedCalls=true` để chữa recursion mà không hiểu tác động: nó có thể tắt cả audit/authorization của operation lồng nhau hợp lệ.

## 20. Thiết kế thay đổi config an toàn trong nhiều luồng

Một hệ thống có thể muốn đổi sampling hoặc timeout khi đang chạy.

Một trình tự triển khai hợp lý:

```text
Read candidate configuration
  -> Parse
  -> Validate syntax
  -> Validate semantic dependencies
  -> Check security authorization of actor changing config
  -> Build immutable snapshot
  -> Record candidate version
  -> Publish atomically
  -> Observe metrics and rollback if necessary
```

Nếu bất kỳ bước nào thất bại, **giữ config cũ đang có hiệu lực** theo chính sách. Không phát hành nửa config.

### 20.1. Ví dụ Java pseudo-architecture

```java
// Fragment minh họa, không phải chương trình độc lập.
private final java.util.concurrent.atomic.AtomicReference<AspectConfig>
        current = new java.util.concurrent.atomic.AtomicReference<>();

AspectConfig snapshot = current.get();
// Dùng cùng snapshot trong một invocation.

AspectConfig validated = parseAndValidate(candidate);
current.set(validated);
// Publish bản hoàn chỉnh, không sửa field lẻ trên object đã công bố.
```

Lưu ý: đoạn này giả định `AspectConfig` và `parseAndValidate` được định nghĩa ở nơi khác. Việc đặt một AtomicReference chỉ giúp công bố **reference** an toàn; nó không xử lý đồng bộ của sink/retry counters, cũng không tự đổi pointcut đã weave.

### 20.2. Cần version và rollback

Một snapshot nên có version/identifier để log và truy vết, chẳng hạn:

```text
policyId = trace-orders
schemaVersion = 2
revision = 2026-10-20.1
activatedAt = known deployment time
```

Đây là dữ liệu minh họa, không phải bản ghi production thật. Cần giữ quan hệ giữa **source config**, **validated snapshot** và **effective config ở runtime**.

### 20.3. Không hứa strong atomicity của side effects

Nếu đổi endpoint của sink khi đang có event inflight, một phần event có thể đi endpoint cũ, phần khác đi endpoint mới. Atomic swap config không tự bảo đảm việc gửi event, transaction hoặc network I/O là atomic.

## 21. Bảo mật của giao diện cấu hình

Config aspect có thể làm thay đổi một **security boundary**. Do đó:

- Không để người dùng không có quyền tắt authorization aspect.
- Không cho phép pointcut wildcard rộng trên mã chứa dữ liệu nhạy cảm nếu chưa được review.
- Không cho phép bật `logFullArgs` ngoài policy dữ liệu.
- Phân tách quyền thay sampling và quyền đổi fail-open/fail-closed.
- Không ghi secrets trực tiếp trong log config/diagnostics.
- Có audit cho những lần sửa policy quan trọng.
- Validate URI/endpoint/certificates phù hợp trước khi sử dụng.
- Xem config từ nguồn ngoài là input không tin cậy; không nối thẳng vào biểu thức có thể thực thi code tùy ý nếu engine không bảo đảm sandbox.
- Nếu parser hỗ trợ regex, phải đánh giá chi phí, phạm vi và điều kiện tránh catastrophic backtracking.
- Nếu một aspect bảo vệ bất biến cốt lõi, không dựa duy nhất vào toggle có thể bị vô hiệu hóa dễ dàng.

**Configuration is code-like policy** ở những hệ thống mà nó điều khiển quyền, ngoại lệ và phạm vi can thiệp. Review và kiểm thử thay đổi config là một phần phát triển an toàn.

## 22. Cấu hình logging và dữ liệu nhạy cảm

Một interface tốt có thể chỉ cho phép:

```text
captureArguments = NONE | ALLOWLIST
captureResult = STATUS_ONLY
sensitiveFieldPolicy = REDACT
maxRecordedBytes = 4096
```

Không mặc định log `args.toString()` hoặc serialize toàn bộ object graph, vì:

- Có thể lộ token, mật khẩu, thông tin cá nhân.
- `toString()`/getter có thể làm I/O hoặc bị aspect chọn, gây self-interception.
- Object graph lớn/có vòng có thể gây memory/CPU cao.
- Dữ liệu đầu vào có thể chứa nội dung không tin cậy gây log injection.
- Thay đổi serializer có thể tự làm đổi behavior.

### 22.1. Một giới hạn size không thay quy tắc bảo mật

Cắt log xuống 4096 bytes **không làm an toàn** một token nằm ở 32 bytes đầu. Cần masking/redaction đúng trường, trước khi áp size limit hoặc xuất event.

### 22.2. Ghi rõ mức bảo đảm của observer

```text
emitEvent()
```

có thể nghĩa gửi vào bộ đệm, đẩy queue, broker ack hoặc lưu bền vững. Contract phải phân biệt các mốc. Không hứa exactly-once hay durable auditing nếu sink không cung cấp.

## 23. Retry, timeout và idempotency

Đây là nhóm cấu hình dễ gây tác dụng phụ lớn.

### 23.1. Retry attempts

Không chỉ có `maxAttempts`. Cần ít nhất:

- Retry-on exception classification.
- Backoff policy.
- Idempotency condition/key.
- Time budget tổng.
- Cancellation.
- Interaction với transaction.
- Logging mỗi attempt hay mỗi logical request.
- Có thể retry lỗi phát sinh **sau khi business đã hoàn thành** không?

Nếu một after advice ném lỗi sau commit, retry ngoài có thể lặp mutation. Chỉ retry các trường hợp **được chứng minh an toàn**.

### 23.2. Timeout

Cần phân biệt:

- Timeout của việc gửi metric/audit.
- Timeout của business.
- Timeout đợi lock.
- Timeout của Future/task completion.
- Timeout network request.
- Deadline tổng cho một logical operation.

Một timeout exception không tự bảo đảm side effect chưa xảy ra hoặc đã được rollback.

### 23.3. Sampling

Nếu `sampling=10%`, phải nói:

- Là 10% của requests, attempts hay join point invocations?
- Quyết định theo random, hash stable key hay mỗi event?
- Có áp cho failures không?
- Security audit có được sampling không?
- Cách đồng nhất sampling trong distributed trace ra sao?

Không xem sampling là option vô hại nếu consumer/vận hành dựa vào completeness của audit.

## 24. Hợp đồng cấu hình khi có async

Một method có thể trả `CompletableFuture<Result>`:

```text
method execution returns Future
  -> task continues
  -> Future completes later
```

Nếu advice after-returning chạy khi method trả Future, một `emitSuccess=true` có thể chỉ ghi **Future được trả**, không phải công việc hoàn tất.

Giao diện nên nêu:

- `completionBoundary = METHOD_RETURN | ASYNC_COMPLETION` (nếu engine/implementation hỗ trợ).
- Exception nằm ở method invocation hay completion stage?
- Context được truyền qua executor như thế nào?
- Cancellation/timeout có được coi là failure không?
- Snapshot config cũ hay mới dùng khi completion xảy ra?
- Lỗi trong callback quan sát có làm stage mới thất bại không?
- Observer có quyền thay completion result không?

Không đưa enum này vào cấu hình nếu implementation **không thể thực hiện**: option đẹp nhưng không có hiệu lực sẽ tạo contract giả.

## 25. Phân biệt schema config và cú pháp pointcut của công cụ

Một schema độc lập công cụ có thể khai báo:

```yaml
aspect:
  id: order-trace
  enabled: true
  selection:
    kind: METHOD_EXECUTION
    include:
      - OrderService.submit
      - OrderService.cancel
    exclude:
      - InternalTraceSink.write
  behavior:
    traceEnabled: true
    observerFailureMode: BEST_EFFORT
  version: 1
```

**YAML trên chỉ là ví dụ cấu trúc dữ liệu**, không phải schema được AOP engine nào nhận trực tiếp.

Cần một adapter/compiler/translator có trách nhiệm:

1. Parse YAML.
2. Validate config semantics.
3. Ánh xạ `METHOD_EXECUTION` và include/exclude sang pointcut thực sự được hỗ trợ.
4. Xác minh module/class loading/weaving scope.
5. Áp policy runtime phù hợp.
6. Cung cấp diagnostics về **effective selection** và warnings.

**Không đưa một ngôn ngữ pointcut tùy ý vào config string rồi coi đó là một giao diện chuẩn.** DSL có thể có ngữ nghĩa tinh vi về overload, package wildcard, annotation, dynamic conditions, caller/target hoặc control flow.

## 26. Mẫu tài liệu cấu hình có thể tái sử dụng

Một trang reference nên có:

```markdown
# Aspect Configuration Contract

## Identity
- Aspect ID:
- Config schema version:
- Owner:
- Purpose:

## Applicability
- Join-point kinds:
- Include patterns:
- Exclude patterns:
- Dynamic guards:
- Weaving/proxy boundary:
- Known bypass paths:

## Behavior
- Observes or transforms:
- Before behavior:
- Normal completion behavior:
- Exceptional completion behavior:
- Continuation/proceed semantics:
- Return/exception transparency:

## Configuration fields
| Key | Type | Default | Required? | Constraints | Meaning |
| --- | --- | --- | --- | --- | --- |

## Ordering
- Precedence rules:
- Known overlaps:

## State and lifecycle
- Config lifetime:
- Invocation state:
- Concurrent reload semantics:
- Async context and completion boundary:

## Failure semantics
- Observer failure mode:
- Business exception handling:
- Retry/idempotency:
- Timeout:
- Cleanup policy:

## Security and operations
- Who may change:
- Redaction policy:
- Logging/diagnostics:
- Effective config visibility:
- Rollback procedure:

## Compatibility
- Breaking changes:
- Migration:
- Verification tests:
```

Đây là **template cho người triển khai điền giá trị**, không phải một policy cụ thể. Khi công bố artifact production, phải thay tất cả mục trống bằng nội dung có thể kiểm chứng; không coi ô trống là lời hứa mặc định.

### 26.1. Đừng ép quá nhiều option vào một constructor dài

Một constructor 20 boolean khó đọc và dễ truyền nhầm vị trí. Có thể dùng:

- **Typed record nhóm theo chức năng**.
- Builder có validation sau cùng.
- Static factories với tên có ý nghĩa.
- Nhiều cấu hình nhỏ composable.
- Enum thay cho cụm boolean mâu thuẫn.

Nhưng builder không sửa được semantic contract kém: tên rõ, default rõ và tổ hợp hợp lệ vẫn cần thiết.

## 27. Đánh giá một API cấu hình kém và cách cải thiện

### 27.1. Cách làm mơ hồ

```java
configure("*", true, false, 3, 250, "ignore");
```

Vấn đề:

- `"*"` match cái gì?
- `true/false` chỉ những chính sách nào?
- `3` là số attempt hay số event?
- `250` là milliseconds hay seconds?
- `"ignore"` bỏ lỗi observer hay lỗi business?
- Config áp trên mọi target hay chỉ proxy?
- Không có constraints.

### 27.2. Tường minh hơn

```java
// Fragment minh họa API; không phải chương trình độc lập.
AspectConfig config = new AspectConfig(
    true,
    Set.of("SAVE", "CANCEL"),
    true,
    FailureMode.BEST_EFFORT
);
```

Mẫu này cải thiện thông qua kiểu `Set<String>` và enum, nhưng vẫn còn điểm yếu: ba tham số boolean/string không nêu rõ trong call site nếu người đọc không mở constructor. Với API lớn, builder/static factories hoặc cấu hình record phân nhóm sẽ dễ đọc hơn.

### 27.3. Tính nhất quán quan trọng hơn số dòng

```text
selectionPolicy = selected business mutations
observerPolicy = best effort
capturePolicy = status only
reloadPolicy = invocation snapshot
```

Một cấu hình có thêm vài dòng nhưng giảm khả năng hiểu nhầm sẽ tiết kiệm nhiều chi phí bảo trì hơn một hàm ngắn khó đọc.

## 28. Những lỗi cấu hình thường gặp

**“enabled=false nghĩa toàn bộ code được bảo vệ vẫn an toàn.”** Không. Nếu advice cung cấp validation/authorization bắt buộc, tắt nó có thể bỏ protection.

**“Pointcut khớp nghĩa advice luôn chạy.”** Không nếu class chưa được weave, caller đi vòng proxy, guard sai hoặc continuation bị short-circuit.

**“Mọi option đều đổi được nóng.”** Không. Weaving/class loading và thay type structure có những giới hạn.

**“Config record luôn immutable sâu.”** Không. Những component chứa collections mutable cần defensive copy.

**“BEST_EFFORT nghĩa nuốt mọi lỗi.”** Không. Nó phải được giới hạn ở chính dependency phụ trợ được định nghĩa.

**“FAIL_CLOSED nghĩa mọi mutation được rollback.”** Sai. Nếu failure phát sinh sau side effect, state có thể đã thay đổi.

**“Timeout ném exception nghĩa server chưa làm gì.”** Không. Kết quả có thể chưa xác định.

**“Retry=3 chỉ ảnh hưởng độ trễ.”** Sai. Nó có thể nhân side effects và số lần audit.

**“Thêm option mới với default an toàn là luôn backward compatible.”** Không nếu default đổi behavior được consumer/vận hành dựa vào.

**“Log full args giúp debug tốt hơn nên luôn bật.”** Không. Có rủi ro lộ dữ liệu, vòng instrumentation và overhead.

**“Precedence có thể đoán từ tên class.”** Không nên. Phải công bố hoặc cấu hình khi engine hỗ trợ.

**“Một chính sách áp cho tất cả concrete aspects vì chúng cùng extends base.”** Không. Từng concrete pointcut và lifecycle có thể khác.

**“YAML là cấu hình AOP chuẩn.”** Không. Nó chỉ có hiệu lực nếu có adapter/engine được xây để hiểu schema đó.

**“Chỉ cần test parse config.”** Không. Cần cả tests actual selection, return/throw, side effects, concurrency và deployment.

**“Một global boolean là cách dễ nhất để dynamic enable.”** Nguy hiểm. Nó gây race, không đảm bảo per-invocation snapshot, và có thể thay đổi security boundary.

## 29. Ma trận kiểm thử cấu hình

| Test | Điều cần kiểm |
| --- | --- |
| Thiếu required key | Báo lỗi ở config boundary trước invocation |
| Field null hoặc blank | Báo lỗi xác định, không bỏ qua silently |
| Enum không hợp lệ | Parse/validation từ chối |
| Timeout âm | Không activate config |
| Retry attempts invalid | Từ chối hoặc áp constraint được tài liệu hóa |
| Include operation hợp lệ | Chỉ operation đúng phạm vi được advice |
| Excluded operation | Không xuất advice ngoài chủ đích |
| Pointcut chọn logger | Guard/exclusion ngăn recursion |
| Config disabled | Behavior đúng nghĩa disabled được công bố |
| BEST_EFFORT observer failure | Kết quả/lỗi business không bị đổi |
| FAIL_CLOSED pre-operation failure | Target không chạy |
| FAIL_CLOSED post-operation failure | Contract nói rõ khả năng side effect đã xảy ra |
| Business failure | Không bị best-effort nuốt mất |
| Two concurrent invocations | Mỗi invocation thấy snapshot config nhất quán |
| Hot reload invalid candidate | Giữ config cũ, không publish partial |
| Hot reload valid candidate | Invocation mới dùng version mới theo contract |
| Two concrete aspects match | Event count/precedence đúng |
| Proxy direct target | Được nhận diện là bypass |
| Class outside weaving scope | Không giả định advice hiện diện |
| Async completion | Success không bị ghi nhầm tại Future creation |
| Retry attempts | Không nhân side effects ngoài policy |
| Masking | Không log thông tin nhạy cảm |
| Regression config version | Mọi defaults/migrations đúng release notes |

### 29.1. Test cả những thứ không được xảy ra

Một test chỉ xem “đã có log ở SAVE” chưa đủ. Cần assert:

- Không có log ở operation không chọn.
- Không gọi target nếu pre-operation mandatory sink fail theo FAIL_CLOSED.
- Không chạy target hai lần vì observer error.
- Không swallow `IllegalStateException` của business.
- Không ghi dữ liệu nhạy cảm.
- Không bỏ advice của invocation khác do một cờ guard toàn cục.
- Không làm mất state khi reload config.

### 29.2. Cấu hình và kết quả phải cùng phiên bản

Trong integration test, log effective config revision và danh tính aspect/pointcut. Nếu test đọc một config nhưng runtime đang dùng một artifact khác, kết quả pass giả sẽ khó nhận ra.

## 30. Bài tập kèm đáp án

### Bài 1 — Trộn scope và behavior

Một cấu hình có `enabled=false` nhưng security policy vẫn phải hoạt động. Nên làm gì?

**Đáp án:** Phân biệt việc tắt telemetry/observer khỏi tắt security enforcement. Security policy bắt buộc phải được bảo vệ ở trust boundary phù hợp và không phó mặc cho một toggle chung.

### Bài 2 — Sai đơn vị timeout

Cùng key `timeout=1000`, đội A hiểu mili giây, đội B hiểu giây. Là lỗi gì?

**Đáp án:** Hợp đồng cấu hình thiếu đơn vị. Dùng `Duration` hoặc tên trường/định dạng rõ, validate phạm vi và tài liệu defaults.

### Bài 3 — Cấu hình đang cập nhật

Một invocation đọc `enabled` trước khi reload và `failureMode` sau reload. Hậu quả?

**Đáp án:** Có thể thấy tổ hợp không nhất quán. Tạo immutable snapshot và dùng một reference cho cả invocation.

### Bài 4 — Best effort và lỗi nghiệp vụ

Observer có lỗi, còn target ném `IllegalArgumentException`. Có được nuốt lỗi target khi `BEST_EFFORT` không?

**Đáp án:** Không theo hợp đồng “best effort của observer”. Catch phải giới hạn vào observer call.

### Bài 5 — Pointcut và class loading

Biểu thức pointcut được thay ở config nhưng target class đã được load ở một mô hình LTW không hỗ trợ weave lại. Có chắc advice áp ngay?

**Đáp án:** Không. Cần đối chiếu pha hiệu lực và khả năng weaver/class loader; có thể cần restart/redeploy.

### Bài 6 — Trạng thái sau fail-closed

Business đã ghi dữ liệu rồi after audit thất bại. Caller nhận exception. Có chắc dữ liệu chưa được ghi?

**Đáp án:** Không. Không có rollback tự động; exceptional guarantee phải chỉ ra trạng thái thật và cách kiểm tra/idempotency.

### Bài 7 — Retry và operation không idempotent

Config đổi maxAttempts từ 1 sang 3 cho operation tạo hóa đơn. Có nguy hiểm không?

**Đáp án:** Có thể tạo hóa đơn trùng khi lỗi xảy ra sau side effect. Phải có phân loại lỗi, idempotency và test trước khi bật.

### Bài 8 — Concrete aspects dùng chung

Hai concrete aspects kế thừa base audit, cùng match một method. Vì mã base chỉ viết một lần, method chỉ có một event?

**Đáp án:** Không. Hai concrete aspects có thể cùng áp advice; cần precedence/overlap policy và test số lần.

### Bài 9 — Cấu hình XML aspect

Một abstract pointcut cần bind `args()` nhưng cơ chế concrete-aspect qua XML cụ thể chỉ hỗ trợ abstract pointcut đơn giản. Có thể đặt expression vào XML và chắc chắn chạy?

**Đáp án:** Không. Phải theo giới hạn cơ chế concrete aspect của tool, hoặc dùng code-style inheritance/solution khác.

### Bài 10 — Schema version

Một field mới được thêm nhưng mặc định đổi chính sách xử lý lỗi. Có cần major version không?

**Đáp án:** Phụ thuộc quy ước versioning, nhưng đây là thay đổi behavior có thể breaking. Phải được công bố, đánh giá compatibility và migration, không chỉ dựa vào việc có thêm field.

## 31. Quy trình thiết kế config aspect từng bước

1. **Xác định consumer của cấu hình:** lập trình viên, operator hay platform owner.
2. **Chốt contract public:** aspect chỉ quan sát hay thay đổi behavior?
3. **Phân loại scope:** join-point kind, include/exclude, guard và boundary.
4. **Chọn type:** enum, Duration, Set of IDs, immutable records hoặc config objects.
5. **Ghi tất cả defaults và constraints:** đặc biệt các trường tùy chọn.
6. **Xác định failure policies theo phase:** before, normal return, exceptional return và cleanup.
7. **Rà soát thay đổi security/transaction/idempotency:** không cho policy nguy hiểm bị bật tùy tiện.
8. **Chốt ordering/preference:** xác định precedence/overlaps.
9. **Chốt vòng đời config:** startup, deployment, per-invocation snapshot hoặc dynamic reload.
10. **Phân biệt cấu hình engine và cấu hình advice runtime.**
11. **Thiết kế validation atomic:** parse → validate → publish, giữ bản cũ nếu thất bại.
12. **Cung cấp effective-config diagnostics** với redaction phù hợp.
13. **Viết test dương/âm:** selected và excluded operations.
14. **Viết test lỗi:** business failure, sink failure trước/sau target, cleanup.
15. **Viết test concurrency/async:** nếu scope thực tế dùng multi-thread/Future.
16. **Kiểm thử packaging/weaving cuối:** source và classloader thực sự đúng.
17. **Theo dõi khi rollout:** sự kiện/latency và policy revision.
18. **Đánh giá compat khi nâng cấp:** không đổi default/behavior âm thầm.
19. **Ghi tài liệu migration:** khi xóa/đổi option hoặc giới hạn mới.
20. **Thiết kế rollback:** quay về config trước mà không đổi semantics bất ngờ.

## 32. Checklist công bố cấu hình

- [ ] Có tên aspect, version, owner và mục đích.
- [ ] Có schema/type rõ, không phụ thuộc nhiều chuỗi tùy ý.
- [ ] Phân biệt selection, advice behavior, ordering và runtime operation.
- [ ] Có include/exclude và join-point kind được tài liệu hóa.
- [ ] Có weaving/proxy boundary và bypass paths.
- [ ] Biết option nào cần restart/reweave.
- [ ] Có default rõ cho từng field, cả những trường không cung cấp.
- [ ] Có validation ở construction/activation boundary.
- [ ] Có kiểm tra tổ hợp option mâu thuẫn.
- [ ] Có defensive copy cho mutable collections.
- [ ] Có một immutable snapshot nhất quán per invocation.
- [ ] Có failure mode riêng cho observer, không nuốt business error.
- [ ] Phân biệt fail-closed trước và sau target.
- [ ] Không hứa rollback vì một exception từ advice.
- [ ] Không bật retry cho operation không idempotent ngoài chủ đích.
- [ ] Timeout có đơn vị và completion boundary.
- [ ] Không đưa dữ liệu nhạy cảm vào args/result log.
- [ ] Có masking và retention policy phù hợp.
- [ ] Có precedence/overlap policy rõ khi nhiều aspects match.
- [ ] Không biến config thành cách bypass security bắt buộc.
- [ ] Có test pointcut selection, negative cases và proxy bypass.
- [ ] Có test business error, observer error và finally cleanup.
- [ ] Có test hot reload/concurrent invocation nếu áp dụng.
- [ ] Có test async completion nếu scope chọn async operations.
- [ ] Có effective-config version/diagnostics.
- [ ] Có release notes và migration cho changes to defaults/contracts.
- [ ] Có bằng chứng build/weave/classloader đúng với production.
- [ ] Có phương án rollback/disable telemetry an toàn khi vận hành.
- [ ] Không gọi cấu hình XML/YAML tự thiết kế là chuẩn phổ quát.
- [ ] Có kiểm thử tổng hợp cho tất cả concrete aspects tái sử dụng base.

## 33. Tài liệu đối chiếu

Các tài liệu sau cung cấp ngữ nghĩa cho **một công cụ AOP cụ thể** và Java 17; không tự biến cú pháp đó thành chuẩn của mọi AOP engine:

- [Eclipse AspectJ – Load-Time Weaving và cấu hình `META-INF/aop.xml`](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html): sections của cấu hình, `concrete-aspect`, abstract pointcut limitations, weaving/classloader scope.
- [Eclipse AspectJ – Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): pointcut/advice, aspect inheritance và precedence.
- [Eclipse AspectJ – Pointcuts](https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html): type patterns, join-point kinds và điều kiện chọn.
- [Java SE 17 – `Objects`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html): kiểm null và tiền điều kiện ở API.
- [Java SE 17 – `Set`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html): `Set.copyOf`, tập không sửa được qua interface.
- [Java SE 17 – `Record Classes`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10): cấu trúc record và compact constructor.
- [Java SE 17 – `AtomicReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): cơ sở cho atomic config publication.

## 34. Kết luận

**Thiết kế giao diện cấu hình rõ ràng cho aspect không chỉ là tạo một file có vài khóa và giá trị.** Đó là việc công bố một **hợp đồng lựa chọn và hành vi**: can thiệp ở đâu, khi nào, theo thứ tự nào, lỗi được xử lý ra sao, state nào là per invocation, config nào được phép đổi và thay đổi có tác động gì đến caller.

Ba nguyên tắc quan trọng nhất:

1. **Phân biệt scope với behavior:** pointcut/weaving lựa chọn join points; runtime policy quy định advice làm gì sau khi được kích hoạt.
2. **Có typed/validated/immutable config:** giá trị mặc định, constraints và snapshot nhất quán giúp tránh lỗi vận hành khó tái hiện.
3. **Tôn trọng public contract và giới hạn engine:** một option đổi failure mode, precedence, retry hoặc class coverage có thể là breaking change. Cấu hình không thể tự cung cấp một join-point kind hoặc cơ chế weaving mà công cụ không hỗ trợ.

Dùng **Java record/enum/validation** hoặc giải pháp tương đương để diễn đạt policy; dùng **tài liệu engine cụ thể** để thực hiện pointcut và weaving; và dùng **kiểm thử có cả selected, excluded, error và bypass paths** để chứng minh hệ thống vận hành đúng những gì giao diện cấu hình cam kết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-007">Giới hạn phụ thuộc vào tên gói và chữ ký nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn phụ thuộc vào tên gói và chữ ký nội bộ

## 1. Vấn đề: pointcut chạy đúng hôm nay nhưng biến mất sau một lần refactor

Trong **Aspect-Oriented Programming (AOP)**, một aspect tập trung hóa hành vi xuyên suốt như tracing, logging, metrics, kiểm soát điều kiện, retry hoặc audit. Để áp dụng advice, một **pointcut** phải xác định đúng các **join point** mà cơ chế AOP hỗ trợ. Với Java, những join point liên quan đến lời gọi/thực thi phương thức thường được mô tả hoặc lọc bằng tên kiểu, tên gói, tên phương thức, modifiers, argument types hay metadata.

Cách nhanh nhất là viết một quy tắc kiểu:

~~~text
Ví dụ giả mã pointcut (không phải cú pháp thống nhất của mọi AOP engine):
    within package com.example.orders.internal
    AND method name startsWith "handle"
    AND first parameter type = com.example.orders.internal.LegacyCommand
~~~

Cấu hình trông tiện, nhưng có những phụ thuộc ẩn:

- Đổi `com.example.orders.internal` thành `com.example.sales.engine` làm selector cũ mất match.
- Đổi helper `handlePayment(...)` thành `runChargeStep(...)` khiến metrics không còn chạy.
- Thay class `LegacyCommand` bằng interface `ChargeRequest` thay đổi signature dù ý nghĩa use case vẫn như cũ.
- Một overload mới có cùng tên `handlePayment` được bổ sung, làm selector quá rộng hoặc sai type.
- Một class khác vô tình đáp ứng wildcard pattern, khiến advice được áp vào nơi không thuộc chính sách.
- Một generated/bridge method làm số join points hay signature quan sát được khác dự tính.
- Một caller gọi thẳng target hoặc một self-call không qua proxy, nên pointcut dù “đúng mẫu” vẫn không được kích hoạt.

Vì thế: **giới hạn phụ thuộc vào tên gói và chữ ký nội bộ** có nghĩa là thiết kế pointcut và abstraction theo **ý nghĩa/capability/hợp đồng ổn định**, thay vì ghép kiến trúc AOP chặt vào chi tiết triển khai dễ đổi.

> **Nguyên tắc trung tâm:** Điểm mà một aspect cần quan sát hoặc áp chính sách nên được xác định bằng **ranh giới nghiệp vụ hoặc kỹ thuật được công bố**, không phải tình cờ nằm ở một package hay mang tên helper nào. Nếu vẫn phải dùng package/signature matching, hãy xem đó là một dependency cần được tài liệu hóa, kiểm tra và bảo trì.

### Kết quả học tập

Sau bài, bạn có thể:

1. Phân biệt **semantic boundary** với **lexical/structural selector**.
2. Hiểu coupling do package name, class name, method signature, visibility và call/execution join-point kind.
3. Đánh giá trade-off giữa **annotation marker**, **interface/role-based selection**, **named pointcut composition** và **package patterns**.
4. Phát hiện thay đổi khi rename/move package, overload, generic erasure, bridge method hoặc thêm implementation.
5. Tránh nhầm **điểm được matcher chọn** với **điểm thực sự được engine intercept**.
6. Thiết kế reusable pointcut theo stable vocabulary và áp nguyên tắc ownership/versioning.
7. Hiểu giới hạn của annotation inheritance, interface metadata, runtime reflection và build/weaving.
8. Kiểm soát advice order, result/error contract, security và side effects khi selector mở rộng.
9. Viết **Java 17 runnable demo** chọn interface method bằng annotation thay vì tên class implementation.
10. Lập regression tests để refactor không làm mất hoặc làm tăng ngoài ý muốn độ phủ advice.

Bài thuộc kiến thức AOP cơ bản và nâng cao nói chung. **Không có một cú pháp pointcut duy nhất cho mọi engine**. Các biểu thức so khớp được trình bày dưới dạng giả mã hoặc thiết kế trừu tượng trừ khi ghi rõ đó là Java runnable code.

## 2. Những dạng coupling nào thường phát sinh?

### 2.1. Package coupling

Aspect chọn “mọi method trong `company.application.impl`”. Quy tắc gắn chính sách vào **vị trí lưu source/type trong namespace**. Package có thể được tách theo bounded context, đổi tên, gom module, chuyển từ package `impl` sang `adapter`, hoặc tái cấu trúc mà behavior API vẫn giữ.

**Nguy cơ:** refactor package làm thiếu match; hoặc chuyển thêm class mới vào vùng match khiến advice áp quá rộng.

### 2.2. Concrete-type coupling

Aspect chỉ chọn methods thuộc `LegacyPaymentService`. Sau khi code thay bằng `DefaultPaymentGateway` hoặc hai implementations, policy không tự mở rộng theo vai trò “payment gateway”.

**Nguy cơ:** chính sách được định nghĩa theo concrete class thay vì capability mà contract yêu cầu.

### 2.3. Signature coupling

Aspect phụ thuộc `charge(String,long)`, nhưng API đổi sang `charge(ChargeRequest)`, overload `charge(String,BigDecimal)`, hoặc generic erasure làm cách runtime phản ánh method khác.

**Nguy cơ:** selector trượt khỏi operation gốc hoặc chọn cả những methods mới không đúng ý định.

### 2.4. Internal-helper coupling

Aspect đặt tại private/internal method `preCommit()`. Một lần inline, split, rename hoặc điều chỉnh thuật toán làm method biến mất nhưng *public operation* vẫn hoạt động đúng.

**Nguy cơ:** việc bảo trì implementation vô tình sửa semantics của một concern xuyên suốt.

### 2.5. Call-path/tool coupling

Selector có thể nhắm đúng interface method, nhưng cơ chế interception cụ thể chỉ thấy những lời gọi đi qua proxy. Một call nội bộ `this.work()` có thể không qua handler. Một weaver nhìn method-execution join point lại có phạm vi khác.

**Nguy cơ:** developer nhìn pattern và cho rằng chính sách “bao phủ toàn bộ”, mặc dù interception model không đáp ứng.

### 2.6. Metadata coupling

Đưa `@Observed` vào interface tạo một hợp đồng mới; aspect phụ thuộc vị trí annotation, retention và metadata-resolution policy. Annotation ổn định hơn tên helper trong nhiều trường hợp, nhưng **không miễn phí coupling**. Nó phải có owner, versioning và tests.

## 3. Selector theo cú pháp khác selector theo ý nghĩa

Một **lexical selector** có thể viết bằng các thuộc tính dễ nhìn trong source: namespace, chữ ký, tên class. Một **semantic selector** biểu diễn “operation này thuộc capability X”, “method này là public domain boundary” hoặc “invocation này được đánh dấu cho audit”.

Bảng so sánh:

| Cơ sở chọn điểm | Điều nó thật sự nói | Độ ổn định khi refactor | Rủi ro |
| --- | --- | --- | --- |
| Package `*.impl.*` | Mã hiện nằm trong một vùng namespace. | Thấp nếu package chỉ là chi tiết tổ chức. | Rename/move tạo false negative. |
| Concrete class | Loại runtime/declaration cụ thể. | Thấp khi thay implementation. | Bỏ sót implementations mới. |
| Method name wildcard | Một quy tắc tên. | Trung bình/thấp tùy naming convention. | False positive/negative. |
| Exact method signature | Một declaration chính xác. | Tốt khi signature là published contract. | Dễ vỡ nếu ký hiệu nội bộ thay. |
| Interface/role | Một abstraction được công bố. | Tốt khi interface ổn định. | Phụ thuộc proxy/weaver, override metadata. |
| Annotation/marker | Một ý định được tác giả công bố. | Tốt nếu marker được quản trị. | Missing/overused marker, metadata lookup. |
| Named pointcut | Một khái niệm selection dùng lại. | Tốt nếu định nghĩa trung tâm ổn định. | Định nghĩa bên trong vẫn có thể giòn. |
| Runtime context predicate | Điều kiện dựa flow/arguments/context. | Hữu ích cho semantic policy. | Overhead, context validity, nondeterminism. |

Không phải lúc nào package là sai và annotation là đúng. Nếu chính sách **cố ý áp cho mọi class ở một module boundary**, và package là một phần của kiến trúc được bảo vệ, package matching có thể thích hợp. Nhưng cần khẳng định package boundary là contract, không phải thư mục ngẫu nhiên.

## 4. Đi từ “tên method” đến “hợp đồng được công bố”

Giả sử có business operation `submitOrder`, còn implementation hiện tại gọi các helpers:

~~~text
Public API: OrderSubmission.submit(order)
       |
       +-- DefaultSubmitter.normalizeFields(...)
       +-- DefaultSubmitter.reserveSlots(...)
       +-- DefaultSubmitter.persistInternal(...)
~~~

Nếu concern là **latency của một logical submission**, điểm thích hợp thường là **public use-case boundary** `OrderSubmission.submit`, thay vì `persistInternal`:
- Helper có thể được gọi hai lần hoặc thay thế khi đổi database.
- Helper có thể biến mất sau khi tối ưu.
- Một lần `submit` có thể vẫn phải trả kết quả dù bước persist được gộp.
- Metrics ở helper dễ đếm attempts/thành phần, không phải logical submissions.

Nếu concern lại là **storage-attempt latency**, internal method có thể là điểm đo đúng — nhưng khi đó metric phải được ghi rõ là **storage attempt**, và selection cần owner của storage module bảo trì cùng code nội bộ.

### 4.1. Cùng một workflow có nhiều ranh giới hợp lệ

~~~text
SubmitOrder API
  -> Permission check
  -> Retry policy
       -> Storage attempt
  -> Return receipt
~~~

Một aspect muốn đo **logical call** nên quanh boundary SubmitOrder. Một aspect đo **mỗi retry attempt** nên ở storage-attempt boundary. Không có một pointcut duy nhất đúng cho mọi metric; trước tiên phải xác định đơn vị quan sát.

### 4.2. Tránh chọn điểm chỉ vì có sẵn `public`

Một public method có thể là helper hoặc implementation detail của một library. Một API boundary cũng có thể là interface method do proxy bảo vệ chứ không đồng nghĩa mọi public methods trong package. Chọn điểm theo **hợp đồng và chức năng**, sau đó mới biểu diễn bằng khả năng selector của công cụ.

### 4.3. Stable boundary cần có người sở hữu

Nếu `OrderSubmission.submit` là contract chính thức, một thay đổi breaking signature phải đi qua review/versioning. Điều này khiến pointcut đáng tin hơn nhiều so với `normalizeFields` không có lời hứa ổn định.

## 5. Interface/role-based selection

Một hướng giảm coupling là chọn join points gắn với một **interface hoặc role** ổn định:

~~~java
interface PaymentPort {
    Receipt charge(ChargeRequest request);
}
~~~

Đây là **mã minh họa**, các types `Receipt`/`ChargeRequest` chưa được khai báo, không phải chương trình độc lập.

Nếu `PaymentPort` định nghĩa operation mà AOP cần theo dõi, công cụ có thể chọn method theo:
- Declaration của interface.
- Implementation của interface.
- Những proxy implements interface.
- Annotation metadata đặt trên interface method.

Tuy nhiên, **engine phải cho biết** nó match declaration nào, tại call site hay execution, và metadata được tìm ở đâu.

### 5.1. Lợi ích

- Đổi `LegacyPaymentImpl` thành `ModernPaymentImpl` vẫn giữ role `PaymentPort`.
- Đổi package implementation không nhất thiết sửa interface contract.
- Có thể thêm một implementation mới và dùng cùng concern.
- Compiler có thể giúp kiểm method contract.
- Tests có thể dùng interface-level test fixtures.

### 5.2. Giới hạn

- Một interface quá rộng có thể kéo advice vào operations không phù hợp.
- Một class implements nhiều interfaces; cùng method signature có thể có nhiều nguồn metadata.
- Implementation có thể thêm methods ngoài interface mà selector không chọn.
- Interface annotations không tự được Java reflection kế thừa sang implementation methods.
- Một JDK proxy chỉ có những method được expose bởi interfaces của nó, không tự bao tất cả concrete methods.
- Direct calls hoặc self-invocations có thể bypass proxy.

**Thiết kế đúng:** tách một interface theo capability có ý nghĩa, đặt contract ổn định và test những operations được chọn/không chọn.

## 6. Annotation-based selection: ý định rõ, nhưng phải quản lý metadata

Một marker kiểu:

~~~java
// Minh họa; thiếu các imports và API đầy đủ.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface ObservedOperation { }
~~~

cho phép developer nói rõ “operation này được quan sát”, thay vì “bất kỳ method tên `execute*`”. Khi có nhiều implementations, một marker trên **interface method** có thể trở thành selection contract nếu interceptor nhìn vào interface declaration.

### 6.1. Annotation không tự lan mọi nơi

Với Java reflection:
- `@Retention(RUNTIME)` giúp annotation sẵn ở runtime reflection theo mục tiêu khai báo.
- `@Target(METHOD)` giới hạn nơi annotation được đặt.
- `@Inherited` chỉ có semantics inheritance cho **class-level annotations**, không tự làm method annotations từ interface appear trên implementation method.
- Một `Method` lấy từ interface và một `Method` lấy từ implementation là hai metadata views khác nhau.
- Một AOP engine có thể tìm annotation theo convention riêng, nhưng phải dựa vào tài liệu và tests của engine đó.

Nếu marker đặt trên interface, handler dùng `method.getDeclaringClass()` hoặc `isAnnotationPresent` phải chắc chắn `method` thực sự đại diện interface declaration, không lầm tưởng mọi execution join point nhìn thấy annotation giống nhau.

### 6.2. Annotation vừa là API vừa là policy binding

Một `@ObservedOperation` công bố intent. Khi thêm annotation cho method mới, toàn bộ instrumentation có thể áp dụng; khi xóa annotation, coverage thay đổi. Do đó marker nên được quản lý:
- Đặt tên phản ánh semantics, không phản ánh tên team/package cũ.
- Có owner và documentation.
- Có tests kiểm coverage.
- Review annotation changes như thay đổi behavior.
- Không thêm hàng loạt bằng wildcard mà không hiểu side effects.

### 6.3. Không lạm dụng một marker cho nhiều concerns khác nhau

Nếu cùng marker dùng để enable logging, retry, transaction, authorization và masking, một thay đổi annotation có thể bật cả loạt policies. Tách vocabulary theo ý nghĩa:
- Boundary cho tracing/metric.
- Idempotent operation được phép retry.
- Audited operation theo hợp đồng lưu bền.
- Protected operation theo security policy đáng tin.

Một marker không tự bảo đảm các yêu cầu ấy; annotation chỉ là **tín hiệu selection**, còn policy phải được hiện thực và kiểm thử.

## 7. Named pointcut: trừu tượng hóa việc chọn join point

Nếu nhiều aspects đều dùng cùng một pattern:

~~~text
aspect Trace:
    select methods under package x.y.impl

aspect Metrics:
    select methods under package x.y.impl

aspect Audit:
    select methods under package x.y.impl
~~~

một refactor cần sửa cả ba. Một số engine cho phép định nghĩa **named pointcut** hoặc một abstraction equivalent:

~~~text
pointcut LogicalOperations:
    method matches stable boundary role or explicit marker

Trace:   select LogicalOperations
Metrics: select LogicalOperations
Audit:   select LogicalOperations
~~~

Đây là **giả mã**, không mô tả cú pháp chuẩn. Lợi ích:
- Một nơi quản lý selection contract.
- Các aspects có ngôn ngữ chung về “logical operation”.
- Dễ có tests kiểm matcher.
- Giảm duplicate wildcard patterns.
- Giảm nguy cơ một aspect vẫn bám tên package cũ.

### 7.1. Trừu tượng hóa không làm quy tắc sai thành đúng

Nếu `LogicalOperations` bên trong vẫn đơn giản là `package(*.impl)`, nó vẫn coupling với package; chỉ là coupling được **đóng gói tập trung**. Muốn bền vững, cần review nội dung semantic của matcher.

### 7.2. Tách các role theo cấp độ

Một kiến trúc có thể định nghĩa:
- `BusinessOperation`: logical business API entry.
- `RepositoryAttempt`: một lần truy cập dữ liệu.
- `ExternalCall`: lời gọi tới dependency.
- `AuditedMutation`: operation sửa dữ liệu cần audit.

Các role này hữu ích hơn một pointcut `allMethods` quá rộng. Nếu một operation thuộc hai roles, thứ tự aspects và policy cần được khai rõ.

### 7.3. Named pointcut có lifecycle/version

Nếu hai modules dùng một named selector, đổi định nghĩa có thể đổi logging/metrics/audit đồng thời. Cần integration tests và thông tin về tương thích, không coi nó chỉ là refactor “không đổi hành vi”.

## 8. Java 17 runnable: theo interface marker, không theo tên implementation

Ví dụ này dùng **JDK dynamic proxy** để minh họa một chiến lược selection có mục đích rõ. Nó không khẳng định đây là syntax hoặc thuật toán của mọi AOP engine.

**Ý tưởng:**
- Interface `Gateway` công bố method `execute(String)` có marker `@Boundary`.
- `LegacyEngine` và `RefactoredEngine` có tên class và cách xử lý nội bộ khác nhau nhưng cùng implement `Gateway`.
- Một proxy interceptor đọc annotation của **interface method** mà proxy nhận, không kiểm package hay tên class cụ thể của implementation.
- `ping()` không có marker nên vẫn delegate nhưng không có log advice.
- Khi target ném `IllegalArgumentException`, interceptor gỡ `InvocationTargetException` và rethrow cause.
- Bộ đếm `matched` tính **mọi invocation được đánh dấu** bao gồm cả invocation kết thúc bằng lỗi.

Tạo file **`StableBoundaryDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class StableBoundaryDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Boundary { }

    public interface Gateway {
        @Boundary
        String execute(String id);

        String ping();
    }

    static final class LegacyEngine implements Gateway {
        @Override
        public String execute(String id) {
            System.out.println("TARGET legacyHandler " + id);
            if ("BAD".equals(id)) {
                throw new IllegalArgumentException("invalid id");
            }
            return "OK:" + id;
        }

        @Override
        public String ping() {
            System.out.println("TARGET ping");
            return "PONG";
        }
    }

    static final class RefactoredEngine implements Gateway {
        @Override
        public String execute(String id) {
            System.out.println("TARGET renamedPipeline " + id);
            if ("BAD".equals(id)) {
                throw new IllegalArgumentException("invalid id");
            }
            return "OK:" + id;
        }

        @Override
        public String ping() {
            System.out.println("TARGET ping");
            return "PONG";
        }
    }

    static Gateway observe(Gateway target, AtomicInteger matched) {
        return (Gateway) Proxy.newProxyInstance(
                Gateway.class.getClassLoader(),
                new Class<?>[] { Gateway.class },
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "GatewayProxy";
                            default -> throw new UnsupportedOperationException(
                                    method.getName());
                        };
                    }

                    boolean selected = method.isAnnotationPresent(Boundary.class);
                    if (selected) {
                        matched.incrementAndGet();
                        System.out.println("ADVICE before " + method.getName());
                    }

                    try {
                        Object value = method.invoke(target, arguments);
                        if (selected) {
                            System.out.println("ADVICE after " + method.getName());
                        }
                        return value;
                    } catch (InvocationTargetException wrapped) {
                        Throwable cause = wrapped.getCause();
                        if (selected) {
                            System.out.println("ADVICE error " + method.getName()
                                    + " " + cause.getClass().getSimpleName());
                        }
                        throw cause;
                    }
                }
        );
    }

    public static void main(String[] args) {
        AtomicInteger selectedCount = new AtomicInteger();
        Gateway oldVersion = observe(new LegacyEngine(), selectedCount);
        Gateway newVersion = observe(new RefactoredEngine(), selectedCount);

        System.out.println("OLD=" + oldVersion.execute("A"));
        System.out.println("NEW=" + newVersion.execute("B"));
        System.out.println("PING=" + oldVersion.ping());

        try {
            newVersion.execute("BAD");
        } catch (IllegalArgumentException error) {
            System.out.println("ERROR=" + error.getMessage());
        }

        System.out.println("MATCHED=" + selectedCount.get());
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac StableBoundaryDemo.java
java StableBoundaryDemo
~~~

**Stdout kỳ vọng:**

~~~text
ADVICE before execute
TARGET legacyHandler A
ADVICE after execute
OLD=OK:A
ADVICE before execute
TARGET renamedPipeline B
ADVICE after execute
NEW=OK:B
TARGET ping
PING=PONG
ADVICE before execute
TARGET renamedPipeline BAD
ADVICE error execute IllegalArgumentException
ERROR=invalid id
MATCHED=3
~~~

### 8.1. Vì sao đổi tên implementation không phá selector?

Handler xét annotation trên `method` được JDK proxy đưa tới: đây là method thuộc interface `Gateway`. Cả hai targets đều implement cùng interface contract nên selector dựa trên `@Boundary` của `execute` vẫn match, bất kể class concrete tên `LegacyEngine` hay `RefactoredEngine`.

Trong demo, hai implementation classes cùng nằm trong một source file; **không mô phỏng refactor package thật**. Tuy nhiên, điều kiện matching không chứa package/tên class implementation, nên một lần đổi tên/refactor hợp lệ giữ interface contract sẽ không cần sửa selector này.

### 8.2. Vì sao `ping()` không xuất hiện ADVICE before/after?

`ping()` không có `@Boundary` tại interface declaration. Handler vẫn gọi target (nên in `TARGET ping`) nhưng không emit instrumentation. Điều này minh họa **positive/negative coverage**: điểm được chọn và điểm không được chọn đều cần test.

### 8.3. Vì sao `MATCHED=3` chứ không phải 2?

Có ba lời gọi `execute`: A thành công, B thành công và BAD ném exception. Bộ đếm tăng khi advice thấy method được chọn, **trước khi gọi target**, nên nó đo số lần attempted boundary invocations, không phải success count.

Nếu muốn đo thành công, tăng counter ở đường `method.invoke` hoàn tất bình thường. Nếu muốn đo cả result/error độc lập, dùng counters riêng.

### 8.4. Lỗi từ target có được giữ nguyên không?

Khi `RefactoredEngine.execute("BAD")` ném `IllegalArgumentException`, `Method.invoke` bọc trong `InvocationTargetException`. Handler lấy `getCause()`, log lỗi rồi ném lại cause. Caller bắt đúng loại `IllegalArgumentException` và thấy message `invalid id`. Chỉ là **instrumentation**, không chuyển lỗi thành value giả.

### 8.5. Giới hạn của ví dụ

- JDK dynamic proxy intercept **các lời gọi đi qua proxy**, không tất cả executions nội bộ của target.
- Interface marker chỉ được đọc trực tiếp từ `Method` mà proxy nhận; method annotation trên concrete implementation không tự bị Java reflection “merge” vào đó.
- `method.getDeclaringClass() == Object.class` xử lý riêng ba Object methods cơ bản với identity semantics của proxy. Đây không phải equality policy phù hợp cho mọi domain.
- Code chạy tuần tự; `AtomicInteger` chỉ minh họa counter an toàn nếu nhiều threads cập nhật, không làm target tự thread-safe.
- Không có annotation processor, aspect compiler, bytecode weaving hay DSL pointcut trong chương trình.
- Không có methods trùng signature giữa nhiều interfaces; nếu có, cần kiểm method-resolution behavior của proxy cụ thể.
- Không chứng minh “mọi operation quan trọng đều đã được annotate”; đó là trách nhiệm thiết kế và coverage tests.
- Không đo transaction commits, async completions hoặc retries; bộ đếm chỉ đo proxy invocation match.

### 8.6. Bài tập mở rộng demo

Thêm một implementation `RemoteEngine` thuộc package/module khác nhưng vẫn implement `Gateway`; tạo proxy bằng cùng factory và xác nhận `execute` được quan sát. Nếu đổi tên helper private bên trong `RemoteEngine`, selector vẫn phải không đổi.

Thử bỏ `@Boundary` khỏi interface method và xác nhận `MATCHED=0` nhưng target methods vẫn chạy. Thử đặt marker **chỉ** trên implementation method và lưu ý proxy matcher trong demo sẽ không match nếu interface method không có marker. Đây là một bài test hữu ích về metadata lookup contract.

## 9. Signature nội bộ: vì sao một lần refactor có thể đổi join-point set?

### 9.1. Rename nội bộ

Method `calculateInternalTotal(Order)` đổi thành `recalculateTotals(Order)`. Nếu selector so exact name, matcher ngừng chọn. Chức năng nghiệp vụ vẫn đúng, nên functional tests không tự phát hiện metrics/audit mất.

### 9.2. Move giữa packages

Method `PaymentHelper.authorize` chuyển từ `legacy.impl` sang `payment.internal`. Một wildcard `within legacy.impl..` mất match. Ngoài ra, package boundary còn ảnh hưởng Java visibility, reflection access và JPMS exports/opens.

### 9.3. Thêm overload

Ví dụ trước có `process(String)`, nay thêm `process(Request)`. Một selector theo tên `process` có thể match cả hai. Nếu chỉ `process(Request)` biểu diễn operation công khai, thì overload cũ có thể trở thành helper, nhưng metrics/audit sẽ bị đếm gấp đôi nếu cả hai cùng chạy.

### 9.4. Thay parameter representation

Đổi `int amountInCents` thành `Money amount` có thể là một refactor làm tốt type safety. Nếu pointcut bám `charge(int)`, nó sẽ trượt dù operation `charge` vẫn tồn tại trong interface mới.

### 9.5. Generic erasure/bridge methods

Java generic APIs có thể sinh **bridge methods** để giữ overriding semantics sau type erasure, tùy compilation. Một matcher dựa reflection vào runtime method set có thể thấy synthetic/bridge entries; một engine AOP có thể có logic riêng để bỏ qua/gộp chúng.

Đừng giả định các methods trông giống nhau ở source tương ứng 1–1 với join points ở bytecode hoặc reflection. Cần test với generic interface implementations, covariant returns và bridge methods khi concern có nguy cơ bị đếm hai lần.

### 9.6. Private/final/static methods

Một số interception models dựa proxy có giới hạn với private, final hoặc static methods; một weaver khác có thể hỗ trợ những join-point kinds khác. Một selector khớp tên private helper chưa chắc là một điểm có thể intercept trong công cụ hiện tại.

**Ranh giới của bài:** nói đúng về Java và cơ chế AOP nói chung; khả năng match/intercept thực tế phải xác minh trên engine/toolchain đã chọn, không suy mọi engine như nhau.

## 10. Binary API khác internal source signature

Một **public interface method** có thể là phần của compatibility contract. Một private helper có thể đổi tên bất kỳ lúc nào khi refactor.

Nhưng “public” không tự có nghĩa stable:
- Một public method có thể nằm trong internal package không cam kết compatibility.
- Một interface public có thể vẫn được xem là internal API của module.
- Một method package-private có thể là architectural boundary được một team quản lý nhưng không phù hợp cross-module use.
- Generated methods và bridge methods có thể public ở bytecode mà không phải API dành cho callers.

Một stable selector cần một **semantic contract** do người quản trị công bố, không chỉ một modifier.

### 10.1. Tạo vocabulary ranh giới

Ví dụ một hệ thống dùng các khái niệm được quản trị:
- `BusinessCommand`
- `QueryBoundary`
- `ExternalDependencyCall`
- `AuditedMutation`
- `RetryableRead`

Mỗi từ cần định nghĩa:
- Những operations nào thuộc nhóm?
- Những operations nào cố ý bị loại?
- Method return-handle hay task-completion có được tính?
- Có đếm internal calls?
- Có cần annotation/interface?
- Ai phê duyệt thêm/bớt?
- Có dùng để bảo mật hay chỉ instrumentation?

### 10.2. Tránh “tập hợp tất cả mọi thứ”

Nếu một pointcut rộng áp tracing+audit+retry+authorization cho mọi method có tên `execute`, một method mới có thể vô tình nhận retry cho lệnh không idempotent. Selector càng rộng, policy càng cần tách role và review.

### 10.3. Contract thay đổi thì selector cũng có thể cần thay

Giới hạn coupling không có nghĩa pointcut **không bao giờ thay đổi**. Nếu published business boundary thật sự đổi (chia use case hoặc đổi semantics), advice placement phải được xem lại một cách có chủ đích. Mục tiêu là **chỉ đổi khi contract đổi**, không đổi vì một helper rename.

## 11. Chọn call hay execution join point có ảnh hưởng lớn

Một số AOP models phân biệt:
- **Call join point:** điểm có lời gọi tới method.
- **Execution join point:** điểm method body được thực thi.
- **Initialization/field/constructor events:** khi engine hỗ trợ.

Nếu muốn đo một logical operation, call/execution có thể cho số occurrences khác nhau:
- Một method được gọi nhiều lần từ các callers.
- Một proxy có thể thấy call qua wrapper nhưng không thấy direct call.
- AOP tại execution có thể thấy invocation nội bộ mà call-site pointcut không chọn.
- Async calls return stage sớm nhưng actual task tiếp tục.
- Retry làm cùng execution xảy ra nhiều lần.

Vì vậy, **ổn định tên method không đủ**; phải chọn **join-point kind** phù hợp với semantics.

### 11.1. Self-invocation

Trong delegating proxy:

~~~text
Caller -> proxy.outer()
             -> target.outer()
                    -> this.inner()
~~~

Handler thường không thấy `inner()` vì target gọi trực tiếp `this.inner()`. Nếu pointcut cho `inner` cần bắt mọi method-executions, proxy-only may be insufficient. Không sửa bằng cách đổi wildcard package; cần xem **interception boundary**.

### 11.2. Aspect weaving có thể quan sát khác proxy

Source/bytecode weaving có thể chèn code tại execution/call sites theo loại join points được engine hỗ trợ. Nhưng weaving scope, classloader và generated artifacts vẫn quyết định độ phủ. Không tuyên bố dệt trực tiếp luôn bao hết mọi đường gọi.

### 11.3. Chọn ranh giới tin cậy

Nếu concern là security/authorization **bắt buộc**, cần bảo vệ ở trusted boundary mà mọi access đi qua. Không dựa độc nhất vào pointcut có thể bị bỏ qua khi caller giữ target gốc hoặc module khác không được weave. Một stable interface marker không tự tạo một security boundary.

## 12. Mở rộng pointcut mà không tạo implicit dependencies mới

### 12.1. Tách matcher và policy

Một lớp chọn join points không nên đồng thời hardcode:
- Business eligibility.
- Authorization.
- Audit sink.
- Retry type filter.
- Exception mapping.

Nếu tất cả chồng vào một wildcard, thay pattern là thay tất cả. Một thiết kế compositional dùng:
- Selection contract: **điểm nào được chọn**.
- Advice: **làm gì tại điểm đó**.
- Policy: **điều kiện nào được phép thay outcome**.
- Ordering: **khung xử lý lồng nhau ra sao**.
- Testing: **khả năng match/intercept/side effects**.

### 12.2. Pointcut composition

Một số engine hỗ trợ kết hợp điều kiện:

~~~text
BusinessBoundary
AND NOT InstrumentationHelpers
AND NOT HealthChecks
AND WithinAllowedDeploymentModule
~~~

Giả mã này cho thấy:
- Positive selector theo role.
- Negative selector loại instrumentation recursion/health checks.
- Deployment scope tránh can thiệp nhầm module.

Khi có một naming convention ổn định cho **module boundary**, điều kiện package/module có thể là một phần nhỏ của composite selector thay vì toàn bộ định nghĩa.

### 12.3. Explicit exclusion thay vì đoán theo tên

Nếu mọi class có hậu tố `Helper` đều bị loại, một business service tên `RiskHelper` có thể bị bỏ sót; nếu dùng `!startsWith("internal")`, dễ bị lệch khi team đổi naming conventions. Khi có thể, dùng typed markers/roles chính thức hoặc metadata của deployment.

### 12.4. Đặt fallback semantics khi metadata thiếu

Nếu một method mới không có annotation, nên:
- Không match và có test phát hiện thiếu coverage?
- Fail build/lint khi published boundary thiếu marker?
- Có quy tắc mặc định match theo interface?

Tùy concern. Không nên để một unannotated method âm thầm vượt policy bảo mật bắt buộc.

## 13. Scope, module và package boundary có thể là hợp đồng thật

Không phải mọi package dependency đều cần loại bỏ. Có thể một module áp chuẩn: “mọi class trong namespace `company.adapters.outbound` là outbound adapter”. Khi đó package là **architecture boundary được enforced**, không đơn thuần source layout.

### 13.1. Điều kiện để package-based policy đáng tin

1. Naming/packaging convention được tài liệu hóa.
2. CI có architecture tests/lint ngăn class sai vùng.
3. Mỗi lần move package được review như đổi behavior.
4. Matcher positive/negative rõ, tránh wildcard quá rộng.
5. Có tests trên compile/runtime artifact thực.
6. Toolchain dùng đúng classloader và module boundaries.
7. Có owner chịu trách nhiệm versioning.
8. Không dùng package như security trust boundary duy nhất.

### 13.2. Java packages và JPMS modules

Package name là không gian tên, còn module descriptors có thể kiểm soát exports/opens và reflective access. Một aspect phụ thuộc reflection vào implementation package có thể bị ảnh hưởng khi modules thay quyền mở, dù matcher theo tên vẫn nghĩ method đúng.

Nếu source/bytecode weaving cần truy cập thành viên không public, phải kiểm công cụ xử lý modules, classloaders và access restrictions thế nào. Không suy rằng regex package hợp lệ làm reflective invocation hợp lệ.

### 13.3. Tái tổ chức module có thể là thay đổi semantics

Nếu team chuyển code từ `internal` sang `public-api` vì chính sách ownership thay đổi, pointcut đáng lẽ được review. Mục tiêu là **không bị vỡ ngoài ý muốn**, chứ không ép selector cố phủ cả các boundary đã thay về nghĩa.

## 14. Annotation và interface cũng có rủi ro về độ ổn định

Một selector dựa trên annotation còn có các giới hạn:

### 14.1. Marker được đặt sai

Một developer annotate private helper thay vì published operation, dẫn đến instrumentation sai đơn vị. Cần coverage tests và documentation.

### 14.2. Marker bị copy-paste

Annotation `@Retryable` có thể bị copy vào một command non-idempotent, dẫn tới side effects nhân đôi. Đặt marker đúng không tự chứng minh policy an toàn; cần kiểm business semantics.

### 14.3. Marker chỉ ở implementation

Nếu proxy interceptor chọn bằng interface method reflection, marker trên implementation method không được tự thấy. Ngược lại, một weaver xét target execution declaration có thể không thấy annotation của interface. Cần định nghĩa **metadata lookup rule** tường minh.

### 14.4. Type hierarchy quá rộng

Một base interface được đặt marker có thể kéo toàn bộ subclasses/implementations vào pointcut khi không phải tất cả operations đều thuộc concern. Tách marker method-level hoặc specialized interface theo use case.

### 14.5. Annotation versioning

Đổi tên marker, package của annotation hoặc `@Retention` có thể là một breaking contract đối với selectors và processors. Một bước relocation cần deprecation/compatibility plan, không chỉ rename IDE.

### 14.6. Dynamic proxies và sealed interfaces

JDK proxies chỉ được tạo với interfaces hợp lệ cho runtime proxy generation; không suy mọi sealed interface đều có thể được proxy implement tùy ý. Nếu runtime class proxy không tạo được, pointcut strategy dựa proxy không giải quyết được boundary. Adapter/explicit composition hoặc cơ chế AOP khác có thể cần thiết, tùy ràng buộc Java.

## 15. Refactoring an toàn cho những pointcut hiện có

Một module lâu năm có thể đã có hàng chục selectors theo `com.old.project.impl..`. Không nhất thiết phải đổi hết sang annotations trong một commit. Có thể chuyển theo lộ trình có bằng chứng.

### Bước 1 — Inventory pointcuts

Liệt kê:
- Pointcut và advice dùng chúng.
- Positive/negative patterns.
- Những classes/methods thực được chọn.
- Concern là metrics, tracing, audit, security hay transformation.
- Những hiệu ứng khi pointcut không match hoặc match thừa.
- Engine/join-point kind/compile vs load time.

Không chỉ đếm patterns; cần hiểu **vì sao** mỗi pattern có mặt.

### Bước 2 — Xác định semantic boundary

Từ danh sách match thực:
- Chúng có cùng domain role không?
- Có một public API/interface stable tương ứng không?
- Một số helper bị match tình cờ không?
- Chúng là logical operations hay internal attempts?
- Có module boundary được governance không?

Định nghĩa một vocabulary hợp đồng trước khi thay selector.

### Bước 3 — Chọn cơ chế biểu đạt

Cân nhắc:
- Interface/role.
- Annotation.
- Named pointcut composition.
- Một module-level boundary thực sự ổn định.
- Một context predicate có validation và lifecycle.
- Instrumentation trực tiếp ở API boundary nếu tool không hỗ trợ semantic selector.

Không nhất thiết chỉ dùng một tiêu chí.

### Bước 4 — Xác lập tập match chuẩn (golden set)

Chạy kiểm thử/inspection để xác định:
- Những join points bắt buộc được match.
- Những join points tuyệt đối không được match.
- Những join points optional.
- Số occurrences kỳ vọng theo workload.
- Những direct-target/internal calls hiện bị bypass.

Golden set là một **specification**, không đơn giản snapshot matcher cũ vì matcher cũ có thể đã sai.

### Bước 5 — So sánh selectors cũ/mới

Trong một môi trường kiểm thử, so coverage:
- Mới thêm matches nào?
- Mới bỏ matches nào?
- Những thay đổi ấy có chủ đích không?
- Advice order và số side effects có đổi không?
- Dynamic binding/cflow có làm tăng overhead?

Tránh bật cả hai advices sản xuất một lúc nếu chúng gây duplicate audit/retry; nếu cần giai đoạn so sánh, dùng read-only match diagnostics.

### Bước 6 — Refactor và chạy regression tests

Thử:
- Rename implementation classes.
- Move implementation packages.
- Thay private helpers.
- Thêm implementation mới.
- Thêm overload.
- Thay generic type/covariant return.
- Direct target vs proxy paths.
- Exception/async/cancellation.
- Module/classloader build variants.

### Bước 7 — Theo dõi deployment

Sau khi phát hành, đo:
- Expected boundary invocations.
- Join point match counts.
- Tỷ lệ missing instrumentation.
- Duplicate events.
- Latency và error changes.
- Policy bypass detection theo thiết kế.

Một selector “ổn định” vẫn cần bảo trì và kiểm tra khi hệ thống phát triển.

## 16. Coverage tests: điều kiện tối thiểu để hạn chế coupling

Việc thay một wildcard bằng annotation chỉ dịch rủi ro từ “rename package” sang “quên marker”. Giải pháp là **test coverage như một hợp đồng**.

### 16.1. Positive tests

Bảo đảm advice chạy:
- Trên interface boundary chính.
- Với implementation cũ/mới.
- Sau khi implementation đổi package.
- Với subclass hoặc proxy hợp lệ.
- Trên cả normal return và exception.
- Với generic/overloaded method theo semantics đã công bố.

### 16.2. Negative tests

Bảo đảm advice **không chạy**:
- Health check/diagnostic method ngoài policy.
- Internal helper chỉ dùng trong implementation.
- Methods khác tên nhưng cùng package.
- Object methods `equals`, `hashCode`, `toString` nếu không thuộc concern.
- Một newly added public helper chưa được đánh dấu.
- Method ở tenant/module không được phép.

### 16.3. Test refactor giả lập

Một contract test có thể tạo hai implementations cùng stable interface nhưng tên class khác nhau, giống demo Java. Với AOP engine thật, có thể tạo test fixtures trong hai packages/module khác nhau và xác nhận selector matched set không đổi.

### 16.4. Test thực sự kích hoạt, không chỉ tính biểu thức

Một metadata matcher có thể trả true cho method, nhưng proxy chưa được tạo/đăng ký hoặc call path đi trực tiếp target. Integration test cần **gọi qua đúng runtime boundary** và assert advice event thực sự được emit.

### 16.5. Test kiểu lỗi, không chỉ số lần log

Nếu logging advice throw trong failure path, nó có thể che lỗi target. Nếu around advice retry làm external command chạy lại, code có thể có đúng match count nhưng sai domain contract. Kiểm cả **result, exception, side effects và order**.

## 17. Runtime discovery, bridge/synthetic và compiler-generated code

### 17.1. Reflection nhận những declarations nào?

`Class.getMethods()` trả public methods gồm inherited public methods. `getDeclaredMethods()` trả methods được khai báo trực tiếp trong class, không bao gồm inherited. Hai API không trả cùng tập.

Một runtime matcher sử dụng một API nhưng tài liệu pointcut mô tả API khác sẽ khó debug. Khi tạo matcher riêng, cần hiểu chính xác metadata source và method identity.

### 17.2. Bridge methods

Generics và covariant overriding có thể sinh bridge methods. `Method.isBridge()` và `Method.isSynthetic()` hỗ trợ kiểm tra metadata khi dùng reflection; nhưng việc loại/gộp bridge methods phải đúng mục tiêu.

Không viết một rule “bỏ tất cả synthetic methods” mà chưa hiểu engine cần thấy chúng để preserve dispatch.

### 17.3. Lambda/generated types

Tên class của lambda, generated proxy, anonymous class hoặc code generation có thể không ổn định/được công bố. Dùng regular expression trên tên runtime classes như một business boundary rất dễ giòn. Quan tâm đến role/interface/metadata ở boundary, không hardcode generated class name.

### 17.4. Build artifact thay source view

Pointcut matching tại bytecode có thể phản ánh compiled descriptors khác source-level generic type names. Nếu tiêu chí phụ thuộc generic parameterization, cần chọn công cụ/metadata hỗ trợ và test hiện thực, không giả định JVM runtime giữ đầy đủ generic type semantics trong mọi dispatch.

## 18. Aspect ordering: giảm coupling ở selector chưa đủ

Một operation có thể đồng thời được chọn bởi:
- Authorization.
- Cache.
- Retry.
- Metrics.
- Trace.
- Audit.

Nếu trừu tượng hóa pointcut khiến chúng cùng match ở một boundary mới, **thứ tự** có thể thay đổi kết quả:

~~~text
Auth(Cache(Target))
versus
Cache(Auth(Target))
~~~

Với một cache short-circuit, authorization nằm bên trong có thể bị bypass nếu cache trả thẳng. Ngược lại, `Metrics(Retry(Target))` và `Retry(Metrics(Target))` đo logical call và per-attempt khác nhau.

Đó là **hệ quả hành vi**, không chỉ chuyện hiệu suất.

### 18.1. Stable matcher không đảm bảo stable ordering

Một selector có thể không đổi sau refactor nhưng aspect registration/precedence có thể thay khi thêm module, đổi build hoặc thay classloader. Khai ordering contract một cách tường minh theo engine và test effective chain.

### 18.2. Overmatching nguy hiểm hơn missing metrics trong một số policy

Nếu một pointcut mở rộng đột ngột chọn mọi methods trong `*.service.*`, một retry aspect có thể lặp thao tác ghi dữ liệu; một logging aspect có thể xuất secrets; một transaction aspect có thể mở/đóng resource sai scope.

Vì vậy cần negative coverage mạnh đặc biệt cho advices **thay outcome hoặc side effects**.

### 18.3. Security cần trusted enforcement

Một permission aspect hoạt động tốt trên proxy có thể không bảo vệ direct calls. Dù selector dựa annotation ổn định, nó không chứng minh mọi entry path đi qua trusted boundary. Authorization cần được thiết kế như một invariant bảo mật, không chỉ như một naming convention.

## 19. Performance: pattern matching và metadata lookup

Một pointcut rộng có thể tăng số join points được instrument, tạo overhead cho:
- Advice invocation.
- Reflection/metadata resolution.
- Context/binding allocation.
- Tracing/metric events.
- Lock contention/aggregation.
- Log I/O và serializing args/results.
- Class loading/weaving work.

### 19.1. Không đoán chi phí chỉ từ loại selector

“Annotation matching luôn chậm hơn package matching” hoặc ngược lại không phải kết luận tổng quát: nhiều engines resolve selectors ở compile/load time hoặc cache metadata. Hiệu năng phụ thuộc pointcut model, caching, JIT, số joins thực và advice body.

### 19.2. Chi phí chính thường nằm ở hành vi advice

Một advice ghi JSON của mọi arguments vào disk có thể tốn nhiều hơn so metadata matcher. Một selector quá rộng có thể khiến logging khối lượng lớn, nhiều secrets hoặc metrics cardinality cao. Tối ưu hóa nên bắt đầu bằng **đúng phạm vi**, rồi đo CPU/allocation/latency.

### 19.3. Cache metadata có rủi ro lifecycle

Nếu tự tạo `Map<Method,Descriptor>`, có thể giữ references tới classes/classloaders lâu hơn khi hot reload/plugin unload. Chọn registry owner/eviction/weak association thích hợp, không biến tối ưu metadata thành memory leak.

### 19.4. Semantic boundary giúp giảm noise

Chọn đúng một public operation thay vì mười internal helpers thường làm traces rõ nghĩa hơn, giảm event volume và giảm rủi ro nhầm metric per-attempt với per-request. Nhưng nếu cần low-level profiling, internal join points vẫn có giá trị khi được bảo trì theo module.

## 20. Ma trận quyết định: package, signature, interface hay annotation?

| Tình huống | Cơ chế nghiêng về | Điều cần kiểm |
| --- | --- | --- |
| Public business API ổn định | Interface/role boundary. | Proxy/weaving coverage, method semantics. |
| Nhiều types không chung hierarchy nhưng cùng concern | Marker annotation hoặc named selection. | Governance/retention/lookup. |
| Internal storage attempts được module owner công bố | Một named internal boundary/signature. | Refactor tests, owner/version. |
| Chỉ chọn operations trong một layer được enforce | Package/module plus architecture checks. | Rename/move review và exclusions. |
| Không thể sửa third-party source | Adapter/instrumentation ở boundary sở hữu. | API availability, deployment/classloader. |
| Privacy/security enforcement bắt buộc | Trusted entrypoint/domain boundary. | Direct calls, bypass, authorization tests. |
| Generated/proxy implementation tên runtime thay đổi | Interface/contract metadata. | Không dựa generated class name. |
| Generic methods bị bridge/erasure | Stable logical method identity. | Synthetic/bridge behavior engine. |
| Async task completion metric | Async completion hook/policy. | Không nhầm method return với task end. |
| Debug profiling tạm theo private helper | Selector nội bộ có vòng đời ngắn. | Không quảng bá thành production contract. |

**Không có một chiến lược thắng mọi ô.** Khi lựa chọn, luôn viết rõ **selector quản trị nghĩa gì**, **ai chịu trách nhiệm bảo trì**, và **cách kiểm thử drift**.

## 21. Lộ trình kiến trúc hóa một module AOP tái sử dụng

### 21.1. Tầng vocabulary

Định nghĩa một số khái niệm ổn định:
- Logical business operation.
- External dependency attempt.
- Auditable mutation.
- Retryable idempotent read.

Các khái niệm này nên được document kèm examples và anti-examples.

### 21.2. Tầng binding contract

Chọn cách biểu diễn:
- Interface method declaration.
- Typed annotation.
- Explicit API wrapper.
- Engine-specific named pointcut.
- Stable module/package boundary (khi được enforce).

Không để business concern phải biết implementation package từng team.

### 21.3. Tầng advice độc lập

Advice nhận context có type/contract phù hợp, không nên hardcode:
- Package prefix của caller.
- Concrete service class.
- Private helper name.
- Generated proxy class name.
- Một thứ tự tự phát giữa các aspects.

Một observer nên trả nguyên result/error trừ khi tài liệu có chính sách transformation. State per-invocation nên giữ local, shared metrics dùng concurrent primitives đúng semantics.

### 21.4. Tầng integration test

Chạy test matrix trên nhiều implementations, packages và refactor fixtures. Có snapshot về những operations matched nhưng tránh snapshot mọi chi tiết generated bytecode không ổn định.

### 21.5. Tầng vận hành

Theo dõi coverage/drift, instrumentation failures, performance và security policy. Khi một interface được thay bằng API version mới, review migration cùng aspects chứ không giả định wildcard sẽ “tự bắt”.

## 22. Các lỗi phổ biến và cách sửa

**“Pointcut match được tất cả methods trong package `impl`, vậy là đủ.”** Không nếu package là chi tiết tổ chức và thay đổi theo refactor. Chọn semantic boundary hoặc enforce package architecture.

**“Đổi tên helper là refactor không thể ảnh hưởng behavior.”** Có thể ảnh hưởng behavior của aspect nếu selector bám tên helper. Viết refactor regression tests.

**“Annotation đã đặt ở interface nên reflection trên implementation luôn thấy.”** Sai về Java method annotation inheritance. Kiểm lookup site.

**“Một interface marker chắc chắn đồng nghĩa method được intercept.”** Không. Proxy creation, weaver scope, call path và join-point kind đều quan trọng.

**“Chỉ cần đổi pointcut sang public methods là ổn.”** Không. Public có thể bao helper, overload hoặc method có semantics khác.

**“Matcher theo method name không thay khi thêm overload.”** Không; thêm overload có thể làm matcher match thêm method.

**“Generic type xuất hiện đầy đủ trong runtime method descriptor.”** Java có erasure và bridge methods; phải xem metadata source/engine.

**“Một pointcut trừu tượng hóa sẽ không còn coupling.”** Không nếu bên trong vẫn là hardcoded package.

**“Thêm annotation chỉ ảnh hưởng logging.”** Có thể ảnh hưởng nhiều advices nếu cùng marker dùng cho retry/audit/security. Tách policy vocabulary.

**“Callback async fail giống method invocation throw.”** Không. Method có thể return stage bình thường và stage fail về sau.

**“Metrics count luôn là success count.”** Không nếu tăng counter trước `proceed()`; đó có thể là attempted invocation count.

**“Đặt authorization trên một proxy nghĩa là mọi method gốc được bảo vệ.”** Không nếu có direct-target/self-invocation bypass.

**“Tăng wildcard scope chỉ khiến hệ thống đo được nhiều hơn.”** Có thể kích hoạt side effects như retries, masking hoặc resource handling trên operations không phù hợp.

**“Class name generated proxy là API bền vững.”** Không. Tránh matching theo generated names.

**“Một test pass sau rename có nghĩa mọi đường đều có instrumentation.”** Không. Cần coverage dương/âm, failure/async/direct calls và integration artifact.

## 23. Bài tập vận dụng kèm đáp án

### Câu 1 — Package refactor

Aspect đo mọi method trong `billing.impl`. Team chuyển implementation sang `billing.engine` nhưng API `BillingPort` giữ nguyên. Vì sao metrics biến mất?

**Đáp án:** Matcher phụ thuộc package path, không phụ thuộc semantic boundary. Chuyển selection sang stable interface/role hoặc có named selector được governance và test trên package rename.

### Câu 2 — Internal helper rename

Một pointcut match `saveInternal`, method bị inline vào `save`. Business behavior không đổi nhưng audit không chạy. Hướng cải thiện?

**Đáp án:** Đặt audit ở operation boundary thật sự (ví dụ public commit/event) hoặc tạo explicit contract cho storage attempt; không dựa internal helper tồn tại mãi.

### Câu 3 — Annotation trên interface

`Gateway.execute` có annotation, implementation override không khai annotation. Java reflection trên implementation method có tự thấy annotation interface không?

**Đáp án:** Không mặc định. Trong demo, handler xét interface `Method` do proxy nhận nên thấy marker. Engine khác phải có metadata lookup rõ.

### Câu 4 — Overload

`process(String)` được đo. Team thêm `process(Request)`; pointcut chỉ match name `process`. Rủi ro?

**Đáp án:** Nó có thể match thêm một operation không thuộc policy hoặc đo hai lần khi overload này gọi overload kia. Test đúng từng signature/role.

### Câu 5 — Bridge methods

Một class generic override method tạo bridge method. Có thể log method twice không?

**Đáp án:** Có thể phụ thuộc engine/matcher. Kiểm sự hiện diện của bridge/synthetic methods và join-point semantics, không giả định một method source chỉ cho một event trong mọi model.

### Câu 6 — Direct target

Một test chỉ gọi service qua proxy thấy advice chạy. Caller khác giữ target gốc, gọi trực tiếp không có advice. Điểm sai ở đâu?

**Đáp án:** Interception coverage/boundary, không phải tên package. Nếu policy bắt buộc, phải kiểm soát đường truy cập ở trusted boundary hoặc dùng cơ chế phù hợp.

### Câu 7 — Retry marker

Một developer copy marker retry sang method ghi tiền không idempotent. Marker-based selector có làm nó an toàn không?

**Đáp án:** Không. Marker chỉ chọn join point; idempotency và allowed failure conditions là domain policy cần được review/test.

### Câu 8 — Package là kiến trúc

Một project enforce mọi outbound adapters trong `app.outbound`, có architecture tests. Có phải pointcut package luôn xấu?

**Đáp án:** Không. Khi package là contract được quản trị, chọn theo package có thể hợp lý. Vẫn cần monitor moves, exclusions và tool scope.

### Câu 9 — Async success

`submitAsync()` trả future pending. Around advice báo success ngay sau `proceed()` return. Nó đã đo đúng task completion chưa?

**Đáp án:** Chưa. Nó chỉ quan sát method return-handle; phải hook terminal outcome nếu metric là task success.

### Câu 10 — Shared named pointcut

Trace, audit và retry đều dùng một named pointcut `OperationBoundary`. Team mở rộng selector để thêm query method. Rủi ro?

**Đáp án:** Cả ba concerns có thể cùng được áp, kể cả retry/audit không mong muốn. Phân tách selection vocabulary hoặc policy predicates và test negative cases.

### Câu 11 — Refactor generic API

API đổi `load(String)` thành `load(Key)`, selector hardcode `String` mất match. Có phải luôn cần giữ `String` để AOP chạy?

**Đáp án:** Không. Pointcut nên thay theo published contract, hoặc chọn role/marker được quản trị. Không giữ API kém type-safe chỉ vì selector cũ.

### Câu 12 — Security và marker

Một method cần authorization nhưng quên `@Protected`. Có thể coi “không match” là cho phép không?

**Đáp án:** Không mặc định; trusted authorization boundary phải bảo vệ invariant. Có thể fail build/lint khi published operations thiếu policy metadata và có integration tests cho bypass paths.

### Câu 13 — Generated class name

Aspect match tên class bắt đầu bằng một prefix do proxy generator tạo. Sau nâng JDK, tên thay. Kết luận?

**Đáp án:** Generated class naming không phải semantic contract. Chọn interface/API boundary hoặc tool-provided metadata ổn định.

### Câu 14 — Metric logical/attempt

Một operation retry ba lần, `Metrics` đặt trong retry. Count=3 có đúng không?

**Đáp án:** Có nếu metric là **attempts**. Sai nếu dashboard gắn nhãn **logical operations**. Thiết kế pointcut và aspect order theo đơn vị đo.

## 24. Checklist thiết kế và review pointcut bền vững

1. **Mục tiêu:** Concern thực sự muốn chọn logical boundary nào?
2. **Abstraction:** Có interface/role/annotation được công bố không?
3. **Ownership:** Ai có quyền đổi contract selector?
4. **Package:** Nếu dùng package matcher, package có phải architecture boundary được enforce không?
5. **Signature:** Có hardcode method/parameter types nội bộ dễ refactor không?
6. **Overloads:** Thêm method cùng tên có làm selector match thừa không?
7. **Generics:** Bridge/synthetic/covariant behavior đã được xét chưa?
8. **Join-point kind:** Call/execution/proxy method invocation loại nào được engine hỗ trợ?
9. **Visibility:** Private/static/final methods có intercept được theo mechanism không?
10. **Metadata:** Annotation nằm trên interface, implementation hay wrapper? Lookup rule là gì?
11. **Retention:** Marker có tồn tại ở compile/load/runtime phase cần thiết không?
12. **Named selector:** Có thể gom selection logic để tái sử dụng mà vẫn giữ semantic contract không?
13. **Positive coverage:** Các operations bắt buộc được chọn chưa?
14. **Negative coverage:** Health/internal helpers/object methods đã được loại đúng chưa?
15. **Direct paths:** Direct target và self-invocation có bypass không?
16. **Ordering:** Retry/cache/security/metrics/audit có thứ tự đúng không?
17. **Errors:** Advice có bảo toàn exception/result không?
18. **Side effects:** Selector mở rộng có nhân số lần target chạy ngoài ý định không?
19. **Async:** Return-handle và task completion có được phân biệt không?
20. **Security:** Policy bắt buộc có nằm ở trusted boundary không?
21. **Concurrency:** Aspect dùng shared state có thread-safe không?
22. **Performance:** Match count, logging overhead, cardinality và p99 có được đo không?
23. **Build:** Compile/weaving/configuration đúng artifact/classloader/module chưa?
24. **Refactor tests:** Rename/move/overload/impl swap có giữ intended coverage không?
25. **Versioning:** Annotation/interface/named pointcut có deprecation/migration plan không?
26. **Documentation:** Những exceptions và rules đặc biệt có được giải thích cho contributors không?
27. **Rollback:** Nếu selector overmatches production, có cách thu hẹp/rollback an toàn không?

## Kết luận

Giới hạn phụ thuộc vào **tên gói và chữ ký nội bộ** là một yêu cầu về **trừu tượng hóa và tái sử dụng AOP**. Thay vì đặt concern vào nơi implementation tình cờ có method tên `doInternal`, hãy xác định một **ranh giới có ý nghĩa và được công bố**: interface role, marker annotation, named pointcut semantic, hoặc một module/package boundary có governance rõ. Khi implementation được đổi tên, chia helper, đổi package hoặc thêm implementation mới, pointcut nên chỉ cần thay nếu **hợp đồng thật sự đổi**, chứ không vỡ vì những refactor vô hại.

Không có kỹ thuật nào miễn phí coupling. Interface/annotation là một hợp đồng mới phải được sở hữu và kiểm thử; package patterns vẫn phù hợp nếu package chính là boundary được enforce; proxy-only interception vẫn có đường bypass; bytecode weaving vẫn chịu phạm vi build/classloader. Muốn aspect vừa **tái sử dụng**, vừa **đáng tin**, hãy kết hợp **semantic selectors + explicit ownership + positive/negative coverage tests + integration checks trên runtime thật**, và luôn giữ return/error, side effects, concurrency cùng security invariants theo hợp đồng đã công bố.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-008">Đóng gói aspect theo một mối quan tâm có trách nhiệm rõ</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng gói aspect theo một mối quan tâm có trách nhiệm rõ

## 1. Bài toán: aspect cũng có thể trở thành một khối quá tải

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, **aspect** là đơn vị đóng gói một **mối quan tâm cắt ngang (cross-cutting concern)**, thường được mô tả bằng **pointcut** chọn các **join point** và **advice** quyết định hành vi tại những điểm được chọn. AOP giúp loại bỏ phần code cắt ngang bị sao chép ở nhiều method nghiệp vụ, nhưng bản thân aspect vẫn cần có ranh giới thiết kế đúng.

Một lỗi kiến trúc phổ biến là tạo một `CommonAspect` hoặc `GlobalAspect` làm mọi việc:

```text
MegaAspect:
    - authorization
    - audit
    - logging
    - request tracing
    - timing metrics
    - retry and timeout
    - cache
    - transaction
    - exception translation
```

Các hành vi ấy thường **có chủ sở hữu chính sách, độ tin cậy và lý do thay đổi khác nhau**. Nếu audit cần thay schema mà phải sửa cùng code với retry, hoặc đổi timing metric khiến authorization test thất bại, aspect đang có cohesion thấp và coupling cao.

**Nguyên tắc cốt lõi:** *Một aspect nên thể hiện một trách nhiệm rõ, có hợp đồng quan sát hoặc can thiệp được mô tả ngắn gọn, có pointcut đúng phạm vi và có thể kiểm thử độc lập.*

Một cấu trúc có thể dễ hiểu hơn:

```text
AuthorizationAspect    -- Quyết định một operation được phép hay từ chối.
AuditAspect            -- Ghi sự kiện audit theo hợp đồng.
TimingAspect           -- Đo thời lượng và outcome.
RetryAspect            -- Điều khiển các attempt theo chính sách.
ErrorMappingAspect     -- Chuyển lỗi tại ranh giới abstraction.
```

Các aspect có thể cùng khớp một method, nhưng **không vì thế mà chúng là một concern duy nhất**.

> **Phạm vi:** Bài này trình bày AOP cơ bản và nâng cao nói chung. Cú pháp pointcut/advice, những loại join point được hỗ trợ, chính sách tạo aspect instance và weaving phụ thuộc từng engine. Ví dụ Java 17+ bên dưới dùng Java Dynamic Proxy làm một interceptor đơn giản để **minh họa tổ chức trách nhiệm**, không phải một ngôn ngữ AOP đầy đủ hoặc một cú pháp pointcut chuẩn cho mọi runtime.

## 2. SRP trong AOP là “một lý do thay đổi”, không phải “một advice”

Nguyên tắc trách nhiệm đơn nhất (**Single Responsibility Principle – SRP**) không quy định một aspect chỉ được có một method hay một advice. Tiêu chí thiết kế tốt hơn là **những thành phần của aspect có cùng lý do thay đổi chủ đạo không**.

Ví dụ một `AuditAspect` hoàn toàn có thể bao gồm:

- Pointcut chọn các thao tác cần ghi audit.
- Before advice tạo bản ghi bắt đầu.
- After-returning advice ghi outcome thành công.
- After-throwing advice ghi thất bại.
- Cơ chế tạo event theo một schema audit chung.
- Dependency `AuditRecorder` chịu trách nhiệm lưu event.

Tất cả phục vụ **một hợp đồng audit**. Một aspect có ba advice vẫn có thể rất đúng SRP.

Ngược lại, một aspect chỉ có một around advice nhưng bên trong lại kiểm quyền, thử lại network, cập nhật cache, commit transaction và ghi audit có thể **vi phạm SRP nặng**, dù mã chỉ có một method.

### Một câu hỏi kiểm tra

Hãy hoàn thành câu:

> “Aspect này chịu trách nhiệm **[mục tiêu]** đối với **[tập join point]**, với hợp đồng **[outcome/failure policy]**.”

Nếu cần liệt kê nhiều mục tiêu không liên quan bằng “và nó còn...”, nên cân nhắc tách concerns.

## 3. Phân biệt “cross-cutting” và “one concern”

**Cross-cutting** chỉ nói rằng một behavior được sử dụng ở nhiều nơi trong cấu trúc phần mềm. Nó **không** có nghĩa mọi behavior xuất hiện ở nhiều nơi đều thuộc cùng một aspect.

| Concern | Mục đích | Ràng buộc riêng |
| --- | --- | --- |
| **Logging** | Chẩn đoán kỹ thuật | Lọc mức độ, masking, volume |
| **Audit** | Bằng chứng hành động, chủ thể và kết quả | Retention, durability, access control |
| **Tracing** | Quan hệ các operation | Trace/span IDs, context propagation |
| **Timing/metrics** | Đo lượng, độ trễ, tỉ lệ lỗi | Counter, histogram, label cardinality |
| **Authorization** | Cho phép hoặc từ chối thao tác | Principal đã xác minh, tài nguyên, fail-closed |
| **Retry** | Kiểm soát các lần thử | Deadline, budget, idempotency |
| **Cache** | Dùng lại kết quả | Key, TTL, consistency, invalidation |
| **Transaction** | Ranh giới thay đổi nhất quán | Begin, commit, rollback |
| **Exception mapping** | Giữ abstraction contract | Type, cause, caller expectations |

### Logging và audit không đồng nghĩa

Logging có thể cho phép sampling, buffer hoặc best-effort delivery. Audit bắt buộc có thể cần lưu bền vững, giữ đúng identity và ghi cả hành động bị từ chối. Nếu gom hai concern chỉ vì “đều in sự kiện”, một lỗi mất log có thể vô tình làm mất bằng chứng audit hoặc một lỗi audit bắt buộc lại bị nuốt như log phụ trợ.

### Timing và tracing cũng không đồng nghĩa

Một trace span có duration, nhưng tracing còn có parent/links và propagation qua task boundaries; timing metric thường cần aggregate durations với labels có cardinality thấp. Chúng có thể chia sẻ một clock helper, nhưng policy ownership khác nhau.

### Validation, authorization và nghiệp vụ

Validation có thể kiểm tính hợp lệ của input; authorization kiểm quyền; nghiệp vụ quyết định một thao tác có hợp lệ theo domain rule. Nếu một aspect gom cả ba, dễ nhầm lỗi “sai dữ liệu” thành “không có quyền” hoặc chuyển nhầm trách nhiệm vốn cần nằm trong domain service.

## 4. Pointcut và advice cùng phục vụ một trách nhiệm

Một aspect thường gói:

```text
             Concern contract
                   |
          +--------+--------+
          |                 |
       Pointcut            Advice
     Chọn ở đâu?        Làm hành vi gì?
          |                 |
          +--------+--------+
                   |
                 Aspect
```

**Pointcut** phải chọn **đúng tập join point** cho concern. **Advice** chỉ xử lý behavior theo hợp đồng của concern.

Một aspect `TimingAspect` có thể match các method xử lý request; advice của nó tạo timestamp local, gọi tiếp target rồi ghi elapsed duration. Nó không cần quyết định request có được phép hay không hoặc có phải retry.

Một `AuditAspect` có thể chọn các thao tác tạo/sửa/xóa dữ liệu nhạy cảm; advice của nó tạo audit event đúng schema. Nó không cần đếm mọi method execution để đo performance.

### Một concern có thể dùng nhiều named pointcuts

```text
mutationOperation =
      createOperation
  OR  updateOperation
  OR  deleteOperation
```

Nếu tất cả cùng thuộc hợp đồng audit, chúng có thể tạo thành một audit pointcut tổng hợp. **Số lượng pointcuts không phải thước đo số trách nhiệm**.

## 5. Thiết kế từ lý do thay đổi thực tế

Hãy thử ba yêu cầu mới:

- “Thêm bộ lọc dữ liệu nhạy cảm cho audit event.”
- “Đổi histogram bucket và đơn vị latency.”
- “Điều chỉnh thời gian backoff khi dependency timeout.”

Nếu ba yêu cầu cùng phải sửa một `CrossCuttingAspect`, có khả năng aspect đó gộp policy không liên quan.

Khi tách:

```text
Audit change  -> AuditAspect / AuditRecorder / AuditPolicy
Metric change -> TimingAspect / MetricsRegistry
Retry change  -> RetryAspect / RetryPolicy
```

Mỗi concern có test và owner riêng. Các aspects **vẫn phải được test khi composition**, nhưng code của một policy không bắt buộc chứa logic của policy khác.

### Những lý do thay đổi khác nhau có thể cùng xảy ra

Một lần phát hành có thể yêu cầu vừa sửa audit vừa sửa metrics. Sự trùng thời điểm ấy **không chứng minh chúng là một trách nhiệm**. Hãy xem chúng có cần thay cùng nhau vì một hợp đồng duy nhất hay chỉ được yêu cầu trong một đợt triển khai.

## 6. Cohesion và coupling của aspect

**Cohesion cao**: pointcut, advice, state, event model và dependencies của aspect đều phục vụ một concern.

**Coupling thấp**: thay đổi nội bộ concern A không buộc hiểu/sửa implementation concern B.

Các dấu hiệu cohesion tốt:

- Advice có mục đích mô tả được.
- Những helper trực tiếp hỗ trợ một hợp đồng.
- Tên field phản ánh state đúng owner.
- Có thể thử behavior bằng fake dependencies.
- Exception và failure policy rõ.

Các dấu hiệu coupling xấu:

- Một around advice chứa nhiều `if (featureEnabled)` thuộc các lĩnh vực không liên quan.
- Shared field `currentRequestId` bị cả audit lẫn timing cùng ghi.
- Một helper vừa là logger vừa có retry policy vừa sửa exception.
- Có nhiều module owner thay cùng class một cách thường xuyên.
- Không test timer nếu thiếu database audit.

**Một aspect nhỏ vẫn có thể cohesion thấp; một aspect dài vẫn có thể cohesion cao** nếu tất cả dòng code cùng phục vụ một contract.

## 7. Tách aspect không có nghĩa xóa bỏ thứ tự tương tác

Giả sử trên một method áp dụng:

```text
Caller
  |
  AuthorizationAspect
    |
    AuditAspect
      |
      TimingAspect
        |
        Business Method
```

Đây chỉ là **một ví dụ precedence**, không phải quy tắc bắt buộc.

**Authorization ở ngoài Audit:** nếu authorization từ chối trước `proceed`, Audit ở trong có thể không thấy denied attempt.

**Audit ở ngoài Authorization:** có thể ghi nhận cả denied attempt, nếu audit contract yêu cầu.

**Retry ở ngoài Timing:** mỗi attempt có thể chạy một TimingAspect bên trong, metric đếm per-attempt.

**Timing ở ngoài Retry:** duration có thể bao gồm mọi attempts và backoff.

**Mapping ở trong Retry:** retry có thể thấy exception đã map thay vì lỗi dependency gốc.

**Transaction ở trong Retry:** mỗi attempt có thể mở transaction riêng. Đảo thứ tự có thể đổi semantics.

Vì vậy tách concerns làm rõ ownership, nhưng **ordering vẫn là một hợp đồng liên-aspect quan trọng**.

## 8. GodAspect không được sửa chỉ bằng cách tách thành private methods

Một đoạn giả mã:

```text
around everyMethod:
    checkPermission()
    readCache()
    startTimer()
    beginTransaction()
    retry(...)
    audit(...)
    updateCache()
    endTimer()
    translateException()
```

Nếu tách thành mười method private nhưng vẫn giữ **một aspect là chủ sở hữu mọi policy và state**, SRP chưa được cải thiện đáng kể.

Một refactor thiết kế tốt hơn:

1. Xác định từng concern và failure policy.
2. Xác định join points và những trường hợp cần bao phủ.
3. Tách advice và state theo concern.
4. Dùng interface cho dependency recording/config.
5. Cấu hình/ghi rõ precedence cần thiết.
6. Kiểm thử outcome, số target executions và side effects sau refactor.

Không chỉ nhìn source code sạch hơn; phải xem **runtime behavior vẫn đúng không**.

## 9. Một ví dụ Java 17+ chạy được: hai aspects, hai trách nhiệm

Dưới đây là một chương trình Java SE độc lập sử dụng **Java Dynamic Proxy** để mô phỏng việc composition các interceptor:

- **`AuditAspect`:** chỉ chọn `transfer` và ghi ba event: bắt đầu, thành công hoặc thất bại.
- **`TimingAspect`:** quan sát mọi lời gọi qua proxy, đo duration và tăng shared metrics.
- **`LedgerImpl`:** chỉ chứa logic nghiệp vụ, không biết hai aspect.
- **`Metrics`:** giữ counter tổng hợp thread-safe bằng `LongAdder`.
- Hai proxy được lồng **Audit bên ngoài, Timing bên trong**, nên cùng một `transfer` có cả audit và timing.

Nội dung audit trong demo **không in mã tham chiếu riêng tư**. Demo chỉ phục vụ mục tiêu học về responsibility và ordering, không triển khai một audit sink đáng tin cậy ở production.

### 9.1. Tệp `AspectResponsibilityDemo.java`

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.LongAdder;

public class AspectResponsibilityDemo {
    interface Ledger {
        String transfer(String privateReference, int units);
        int balance();
    }

    static final class LedgerImpl implements Ledger {
        @Override
        public String transfer(String privateReference, int units) {
            // Không in privateReference ra output.
            System.out.println("ledger.transfer");
            if (units <= 0) {
                throw new IllegalArgumentException(
                        "units must be positive");
            }
            return "receipt:" + units;
        }

        @Override
        public int balance() {
            System.out.println("ledger.balance");
            return 100;
        }
    }

    static final class Metrics {
        final LongAdder calls = new LongAdder();
        final LongAdder failures = new LongAdder();
        final LongAdder totalNanos = new LongAdder();
    }

    static Object invoke(
            Ledger target, Method method, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Ledger proxy(
            Ledger target,
            java.lang.reflect.InvocationHandler handler) {
        return (Ledger) Proxy.newProxyInstance(
                Ledger.class.getClassLoader(),
                new Class<?>[]{Ledger.class},
                handler);
    }

    // Một trách nhiệm: đo thời gian và outcome.
    static final class TimingAspect {
        private final Metrics metrics;

        TimingAspect(Metrics metrics) {
            this.metrics = metrics;
        }

        Ledger wrap(Ledger target) {
            return proxy(target, (p, method, args) -> {
                String operation = method.getName();
                long started = System.nanoTime();
                System.out.println("timing.start " + operation);

                try {
                    Object result = invoke(target, method, args);
                    System.out.println("timing.success " + operation);
                    return result;
                } catch (Throwable original) {
                    metrics.failures.increment();
                    System.out.println(
                            "timing.failure " + operation + " "
                            + original.getClass().getSimpleName());
                    throw original;
                } finally {
                    metrics.calls.increment();
                    metrics.totalNanos.add(
                            System.nanoTime() - started);
                    System.out.println("timing.end " + operation);
                }
            });
        }
    }

    // Một trách nhiệm: audit thao tác được chọn.
    static final class AuditAspect {
        Ledger wrap(Ledger target) {
            return proxy(target, (p, method, args) -> {
                boolean auditable =
                        "transfer".equals(method.getName())
                        && method.getParameterCount() == 2;

                if (!auditable) {
                    return invoke(target, method, args);
                }

                System.out.println("audit.request transfer");
                try {
                    Object result = invoke(target, method, args);
                    System.out.println("audit.success transfer");
                    return result;
                } catch (Throwable original) {
                    System.out.println(
                            "audit.failure transfer "
                            + original.getClass().getSimpleName());
                    throw original;
                }
            });
        }
    }

    public static void main(String[] args) {
        Metrics metrics = new Metrics();
        Ledger business = new LedgerImpl();

        Ledger timed = new TimingAspect(metrics).wrap(business);
        Ledger observed = new AuditAspect().wrap(timed);

        System.out.println("== successful transfer ==");
        System.out.println("caller "
                + observed.transfer("private-reference", 5));

        System.out.println("== read balance ==");
        System.out.println("caller balance=" + observed.balance());

        System.out.println("== failed transfer ==");
        try {
            observed.transfer("private-reference", -2);
        } catch (IllegalArgumentException error) {
            System.out.println("caller error " + error.getMessage());
        }

        System.out.println("metrics calls=" + metrics.calls.sum()
                + " failures=" + metrics.failures.sum());
    }
}
```

### 9.2. Lệnh biên dịch/chạy

Dùng JDK 17+ và lưu đúng tên tệp `AspectResponsibilityDemo.java`:

```bash
javac --release 17 -encoding UTF-8 AspectResponsibilityDemo.java
java AspectResponsibilityDemo
```

**Standard output** (các giá trị nanoseconds thay đổi theo môi trường nên không được in trong ví dụ):

```text
== successful transfer ==
audit.request transfer
timing.start transfer
ledger.transfer
timing.success transfer
timing.end transfer
audit.success transfer
caller receipt:5
== read balance ==
timing.start balance
ledger.balance
timing.success balance
timing.end balance
caller balance=100
== failed transfer ==
audit.request transfer
timing.start transfer
ledger.transfer
timing.failure transfer IllegalArgumentException
timing.end transfer
audit.failure transfer IllegalArgumentException
caller error units must be positive
metrics calls=3 failures=1
```

### 9.3. Vì sao transfer được audit và timing?

`observed` là Audit proxy. Nó nhận `transfer`, selector trả true rồi ghi `audit.request`. Audit gọi `timed`, tức Timing proxy, nên Timing tạo local `started` và ghi `timing.start`. Target trả receipt, Timing ghi success/end trước, rồi Audit ghi success sau.

Từng component biết **đủ về trách nhiệm của mình**:

- Audit biết operation được chọn, không biết metrics histogram.
- Timing biết method bắt đầu/kết thúc, không lưu raw private reference.
- Target biết nghiệp vụ, không import aspect.
- Shared Metrics đếm aggregate, không giữ current request state.

### 9.4. Vì sao balance chỉ có timing?

`AuditAspect` chỉ chọn `transfer` hai parameters. `balance()` không khớp, nên proxy gọi tiếp target (vẫn là Timing proxy) **mà không phát audit event**. Timing tiếp tục chạy.

**Không match pointcut không có nghĩa bỏ qua target.** Interceptor vẫn phải gọi tiếp với đúng return/exception contract nếu không có chính sách cố ý chặn.

### 9.5. Khi target ném lỗi

`transfer` với `units=-2` ném `IllegalArgumentException`. Helper unwrap `InvocationTargetException` từ Reflection và ném lại cause gốc. Timing ghi failure và `finally`, Audit ghi failure rồi rethrow. Caller nhận message `units must be positive`.

**Một Throwable được hai aspects quan sát không phải hai business failures**. Metric shared chỉ tăng một failure cho một Timing invocation.

### 9.6. Vì sao dùng local timer và shared counter?

Mốc `started` thuộc **một lần advice invocation**, nên là local variable. Nếu dùng field `currentStart` trên singleton TimingAspect, hai calls concurrent hoặc nested có thể ghi đè nhau.

Bộ đếm `calls`/`failures` phục vụ **tổng số liệu**, nên có shared lifecycle và dùng `LongAdder` phù hợp. Lưu ý `LongAdder.sum()` không nhất thiết là atomic snapshot trong khi concurrent updates còn diễn ra; ở demo mọi lời gọi đã hoàn tất trước khi in.

### 9.7. Thứ tự composition là hợp đồng

`Audit outside Timing` tạo event:

```text
audit.request
  timing.start
    ledger.transfer
  timing.end
audit.success / audit.failure
```

Nếu đảo vị trí proxy, timing có thể đo cả overhead audit. Đừng kết luận hai aspects hoàn toàn độc lập về runtime ordering chỉ vì source code tách biệt.

### 9.8. Giới hạn của demo

- Java Dynamic Proxy chỉ intercept invocation qua interface proxy.
- Raw target call hoặc self-invocation của target có thể bypass wrapper.
- `matches` ở đây là một condition trong handler, không phải DSL pointcut/weaver.
- `System.out.println` không phải audit durable hoặc chuẩn compliance.
- Demo không triển khai auth, retry, transaction, async completion.
- Object methods (`toString`, `equals`, `hashCode`) trên proxy có thể cần cách xử lý riêng nếu dùng.
- Failure từ metric sink/audit recorder thật phải có policy; demo chỉ in stdout và tăng counter.
- Kết quả biên dịch/chạy kiểm chứng cách tổ chức và thứ tự **trong cơ chế này**, không đại diện mọi AOP runtime.

## 10. Những mối quan tâm cùng áp dụng một join point không phải cùng sở hữu nhau

Một API `transfer` có thể cần:

1. Authorization trước mutation.
2. Audit request/outcome.
3. Timing.
4. Transaction.
5. Retry một dependency bên trong nếu có.
6. Exception translation ở public boundary.

Tất cả liên quan đến thao tác `transfer`, nhưng mỗi concern có **đối tượng chịu trách nhiệm khác nhau**. Có thể có một **composition root** cấu hình chain, còn mỗi aspect chỉ sở hữu logic của mình.

### Một thứ tự minh họa

```text
API boundary
  -> AuthAspect
     -> AuditAspect
        -> TimingAspect
           -> TransactionAspect
              -> Target
```

Không thể khẳng định đây là thứ tự đúng cho mọi nghiệp vụ. Nếu Audit nằm trong Auth, denied attempt có thể không được ghi; nếu Audit ngoài Auth, nó có thể ghi cả denied attempts. Nếu transaction nằm ngoài timing, metric có thể bao gồm commit; nếu ở trong, duration khác.

**Thứ tự là một yêu cầu nghiệp vụ/vận hành**, không chỉ lựa chọn sắp xếp cho đẹp.

## 11. Phạm vi của aspect quyết định correctness

Một aspect nhỏ với pointcut sai vẫn nguy hiểm. Ví dụ `AuthorizationAspect` chỉ match method tên `delete` mà quên overload `delete(String,boolean)` có thể cho phép một đường bypass.

Xem các yếu tố:

- **Join point kind:** call hay execution?
- **Method signature:** tên, overload, return, parameter types.
- **Target type:** interface, implementation, subclass, bridge method.
- **Metadata:** annotation ở interface hay method implementation.
- **Argument binding:** null, subtype, primitive/boxing, varargs.
- **Dynamic control flow:** có cần giới hạn theo cflow hay request context không?
- **Interception coverage:** class/method được weave hay caller chỉ đi qua proxy?
- **Self-invocation:** method nội bộ gọi `this` có bypass không?
- **Async:** worker/callback execution được instrument ở boundary nào?
- **Runtime class loader/module:** có thuộc phạm vi weaving không?

**Pointcut là một phần hợp đồng của concern**, không phải một filter ngẫu nhiên.

### Security không được phụ thuộc một đường proxy có thể bypass

Nếu authorization là bắt buộc, cần đảm bảo mọi entry point không tin cậy đều được bảo vệ; chỉ có một proxy wrapper không chứng minh không ai có reference tới raw target. Defense-in-depth ở boundary tin cậy có thể cần thiết.

## 12. Một aspect có thể có nhiều hooks nhưng vẫn một trách nhiệm

Một `TraceContextAspect` có thể:

- Trên entry: lấy/gán trace context.
- Trước `proceed`: mở span.
- Khi target throw: gắn failure status.
- Trong `finally`: đóng scope/restore context.
- Với callback async: truyền context qua carrier có lifecycle rõ.

Dù có nhiều hooks, nó vẫn có một concern: **quản lý trace scope**. Cần phân biệt **giữa cùng concern gồm nhiều bước** với **trộn nhiều concerns độc lập**.

Tương tự, AuditAspect có thể chọn các operation tạo/cập nhật/xóa và dùng chung event schema. Việc có ba pointcuts không biến nó thành ba trách nhiệm nếu audit policy thống nhất.

## 13. Helper và dependency: tách implementation chi tiết nhưng giữ ownership

Một aspect không nên tự chứa mọi I/O. Có thể viết:

```text
AuditAspect
    |
    +--> AuditRecorder (interface)
            |
            +--> DurableRecorder
            +--> TestRecorder
```

AuditAspect quyết định **khi nào tạo event**. Recorder quyết định **cách ghi event** theo audit contract. Có thể mock/fake recorder trong unit tests.

Một TimingAspect có thể dùng `Clock` abstraction và `MetricRecorder`. Một RetryAspect dùng `RetryPolicy` và scheduler/backoff helper.

**Không biến shared helper thành GodService**: một helper vừa lưu audit, quyết định quyền và quản lý transaction sẽ tái tạo coupling dưới tên gọi khác.

### Shared mutable helper vẫn có thể sai

Hai aspects dù là hai class khác nhau nhưng cùng ghi vào một singleton helper có field `currentId` vẫn có nguy cơ race. SRP ở cấp class không tự sửa lỗi ownership/correlation sâu trong object graph.

## 14. State và vòng đời phải khớp trách nhiệm

| Dữ liệu | Owner đúng thường gặp | Không nên mặc định |
| --- | --- | --- |
| Thời điểm bắt đầu method | Invocation-local trong TimingAspect | Shared field `start` |
| Attempt count | Một operation retry | Singleton field `currentAttempt` |
| Trace ID | Request/task context đã truyền đúng | Aspect-global `currentTraceId` |
| Total calls | Shared metrics recorder | Local counter bị bỏ sau call |
| Audit policy | Immutable config snapshot | Mutable config bị sửa giữa lời gọi |
| Target metadata cache | Shared concurrency-safe cache có key phù hợp | Map không cleanup/không tính loader |
| Current Throwable | Advice invocation local | Shared field `lastError` |
| Audit write queue | Audit recorder có concurrency contract | Request object giữ mãi trên aspect |

### Reentrancy không cần nhiều thread

Nếu một advice gọi helper rồi helper tạo một join point cũng được instrument, advice có thể tái nhập ngay trên **cùng thread**. Một field `currentId` chung vẫn bị ghi đè. `volatile` hay `synchronized` đơn giản trên setter/getter không biến nó thành state per-invocation.

Giải pháp thường là **local variables**, invocation context hoặc scope stack đúng vòng đời, không là một slot shared.

## 15. Failure policy của mỗi concern phải độc lập

Điều gì xảy ra nếu concern tự thất bại?

**Timing metric sink lỗi:** nhiều hệ thống không muốn việc ghi metric phụ trợ làm fail business operation. Nhưng vẫn cần ghi nhận lỗi vận hành phù hợp, không nuốt mọi Throwable nghiêm trọng.

**Audit bắt buộc lỗi:** có thể cần fail-closed, hoặc cần event durable trước commit. Đây là hợp đồng compliance riêng.

**Authorization policy không tải được:** không nên tự “allow” vì một lỗi infrastructure.

**Retry lỗi:** phải phân biệt transient/permanent, deadline, cancel và idempotency; retry không tự là recovery an toàn.

**Exception translation:** chỉ mapping tại abstraction boundary, giữ `cause` và declared checked/unchecked contract.

Một catch block kiểu “catch mọi Exception rồi return null” trong aspect tổng hợp có thể phá các hợp đồng này cùng lúc.

## 16. `finally`, exception và return contract

Một advice quan sát nên mặc định **bảo toàn return value và Throwable của target**. Ví dụ timing:

```java
// Pseudocode around advice, không phải Java SE API chuẩn.
long start = System.nanoTime();
try {
    return proceed();
} finally {
    recordDuration(System.nanoTime() - start);
}
```

Nếu `recordDuration` tự throw, caller có thể thấy lỗi metric thay vì exception target. Policy của TimingAspect phải xử lý phù hợp.

Nếu AuditAspect lại yêu cầu ghi bền vững mới cho phép completion, policy xử lý lỗi audit khác timing là **một lý do mạnh để tách concerns**.

### After-throwing khác around

After-throwing advice có thể quan sát Throwable nhưng không nhất thiết được quyền biến failure thành return value. Around advice có thể kiểm soát `proceed`/return/throw nếu engine hỗ trợ. Chọn loại advice theo trách nhiệm, không kỳ vọng một hook quan sát có quyền đổi outcome vô hạn.

### Checked exception cần giữ contract

Một proxy/interface method có declared checked exceptions cụ thể. Nếu handler ném checked Throwable ngoài declared throws, Java Dynamic Proxy có thể bọc thành `UndeclaredThrowableException`. Aspect chỉ đo thời gian không nên tự wrap mọi exception rồi bất ngờ phá caller expectations.

## 17. Async boundaries cần được phân biệt theo trách nhiệm

Một method trả `CompletableFuture<T>` có thể return một future chưa hoàn tất. Nếu TimingAspect đo tới method return, nó đo **submission latency**, không nhất thiết **operation completion latency**.

```text
t0: method starts
t1: returns future
t2: future completes

submissionDuration = t1 - t0
completionDuration = t2 - t0
```

Một timing concern muốn đo end-to-end operation có thể cần callback/stage instrumentation và per-operation context.

AuditAspect có thể cần event khi operation thật sự commit, không khi future object được tạo. TracingAspect cần context propagation xuyên executor. AuthorizationAspect cần kiểm tại boundary thực hiện side effect nếu quyền có thể thay đổi.

**Các concern khác nhau có cách định nghĩa “operation kết thúc” khác nhau.** Không gộp chúng vào một after-returning advice rồi gọi mọi thứ là success.

## 18. Pointcut và behavior được tái sử dụng thế nào?

Một số mô hình tái sử dụng:

1. **Named pointcut composition:** ghép predicates để xác định scope.
2. **Abstract aspect/abstract pointcut:** định nghĩa advice chung, để concrete aspects chuyên biệt hóa pointcut nếu ngôn ngữ hỗ trợ.
3. **Selector strategy:** inject policy/selector vào interceptor.
4. **Recorder abstraction:** advice gọi interface, implementation thay đổi độc lập.
5. **Shared immutable config:** nhiều invocations đọc một snapshot đã validate.

### Tái sử dụng theo trách nhiệm, không theo cú pháp

Hai aspects có vài dòng `System.nanoTime()` giống nhau không bắt buộc phải kế thừa một GodAspect. Có thể chia sẻ clock abstraction hoặc utility nhỏ. Nếu policy và scope khác nhau, giữ aspects riêng thường dễ hiểu.

Ngược lại, nếu hai concrete audit scopes có cùng behavior và failure policy, abstract pointcut là một điểm mở rộng hợp lý để tránh sao chép advice. Nhưng nó không tự giải quyết weaving scope, ordering hoặc checked return contract.

## 19. Cấu hình và thay đổi runtime

Một TimingAspect có thể nhận immutable policy:

```java
// Một data holder Java 17+ minh họa.
record TimingPolicy(String revision, boolean enabled) { }
```

Nếu cần hot reload nhiều field config, một `AtomicReference<TimingPolicy>` hoặc `volatile` snapshot reference có thể công bố nguyên một object bất biến. Invocation có thể chọn snapshot một lần rồi dùng xuyên suốt để tránh trộn version before/after.

AuditAspect có policy schema/retention riêng; RetryAspect có budget/backoff policy riêng. **Không dùng một global mutable map không owner rõ ràng** để mọi aspect cùng update.

### Freshness khác consistency

Snapshot-at-entry cho một invocation có consistency tốt, nhưng authorization/token revocation có thể cần kiểm tra quyền mới nhất ở đúng security boundary. Không biến một nguyên tắc config thuận tiện thành quy tắc security luôn đúng.

## 20. Hiệu năng và nguy cơ aspect quá rộng

Một GodAspect match mọi method có thể tạo overhead:

- Dynamic pointcut evaluation mỗi call.
- Argument binding/context allocation.
- Metric/log event ngay ở hot methods.
- Retry và exception wrapping ngoài ý muốn.
- Recursive interception, log storm, stack growth.
- Sink contention và memory retention.

Tách aspects cho phép scope chính xác, nhưng **số lớp advice có thể tăng** nếu nhiều aspects cùng chọn một join point.

Không tối ưu bằng cách bỏ qua security/audit checks bắt buộc; cân nhắc sampling chỉ cho observability được phép. Cần đo performance bằng workload thực và kiểm chứng target execution count, advice activation count, volume events.

### Logging helper tự bị instrument

Nếu pointcut chọn cả audit sink, lời gọi `AuditAspect → AuditRecorder.record` có thể kích hoạt lại chính audit aspect, tạo recursion. Cần **scope/exclusions rõ** hoặc cơ chế guard đúng contract. Một guard chung không được vô tình tắt authorization trên nested business operation.

## 21. Test từng aspect và test composition riêng

### 21.1. Unit test pointcut

- Method đúng scope được chọn.
- Method ngoài scope không được chọn.
- Overload, null, type erasure, annotation metadata xử lý đúng.
- Runtime argument-value selection không được cache sai theo method name.
- Raw/proxy routes được phân biệt.

### 21.2. Unit test advice

- Caller nhận đúng return value.
- Original Throwable được preserve khi concern chỉ quan sát.
- Per-invocation state không lẫn giữa concurrent/nested calls.
- Recorder failure được xử lý theo policy.
- Sensitive input không bị đưa vào log sai.
- Cleanup được thực hiện trên return và throw.

### 21.3. Integration test composition

- Precedence của hai aspects đúng.
- Một target success được quan sát theo đúng order.
- Một Throwable không thành nhiều business failures giả.
- Audit không bị skip bởi cache/authorization khi contract bắt buộc.
- Retry count và transaction boundary đúng.
- Async completion được phân biệt với method return.
- Tất cả target executions bắt buộc đều thực sự được instrument.

### 21.4. Contract test phía caller

Không chỉ kiểm advice có log; phải kiểm **số lần target chạy, output và side effects caller thực sự nhận**, bao gồm exception path, self-invocation, worker callbacks và class-loader boundaries.

## 22. Ma trận kiểm thử trách nhiệm

| Kịch bản | AuditAspect | TimingAspect | Kết quả cần đúng |
| --- | --- | --- | --- |
| Transfer thành công | Một success event | Một call metric | Receipt đúng |
| Balance thành công | Không audit mutation | Một call metric | Balance đúng |
| Transfer lỗi | Một failure event | Một failure metric | Original exception |
| Metric sink lỗi | Không sửa audit policy | Theo metrics failure policy | Không che target lỗi ngoài ý muốn |
| Audit sink lỗi | Theo audit contract | Không sở hữu lỗi audit | Có thể fail-closed |
| Two calls concurrent | Per-call event tách biệt | Shared counter an toàn | Không cross-talk |
| Nested execution | Không shared current ID | Start local từng invocation | Scope unwind chính xác |
| Raw target bypass | Proxy có thể không audit | Proxy có thể không timing | Phải phát hiện coverage thiếu |
| Cache hit ngoài audit | Có nguy cơ thiếu event | Tùy order | Contract được test |
| Authorization deny | Ghi denied hay không tùy order | Tùy order | Không bypass quyền |
| Retry ba attempts | Audit attempt/operation có định nghĩa | Duration count có định nghĩa | Target attempts đúng |
| Future pending | Audit outcome cần completion logic | Submission/completion khác nhau | Không báo success giả |
| Config reload | Dùng đúng version policy | Snapshot nhất quán | Không mixed-version |
| Method overload mới | Recheck scope | Recheck selection | Không bỏ sót |
| Sensitive references | Redact/không xuất secrets | Labels an toàn | Không lộ dữ liệu |

## 23. Quy trình refactor một aspect quá tải

Một hướng làm có kiểm soát:

**Bước 1 — Inventory:** liệt kê advice, pointcut, fields, dependencies, return/throw transformations và side effects hiện tại.

**Bước 2 — Gom theo policy owner:** audit, timing, auth, retry, transaction, cache, error mapping.

**Bước 3 — Viết contract cho từng concern:** scope, failure policy, data ownership, timing/async semantics.

**Bước 4 — Tách helpers:** recorder/clock/policy interface; tránh shared mutable request state.

**Bước 5 — Chuyển per-call state ra local/invocation context:** không dùng singleton fields `currentId`, `start`, `attempt`.

**Bước 6 — Tách advice và pointcut:** mỗi aspect chỉ chọn operation phục vụ concern của mình.

**Bước 7 — Thiết kế precedence công khai:** authorization, audit, retry, cache, timing, transaction và mapping không sắp tùy tiện.

**Bước 8 — Chạy regression tests:** so sánh number of target calls, outcome, event count, exception identity và effect order.

**Bước 9 — Kiểm tra coverage thực:** weaving vs proxy, self-invocation, overload, async, class loader.

**Bước 10 — Đo overhead và failure behavior:** ghi nhận memory/log volume, audit durability, metrics correctness.

**Refactor structure chỉ được xem thành công nếu semantics nghiệp vụ và concern contracts vẫn đúng.**

## 24. Những anti-pattern dễ nhận biết

1. **“Một aspect chỉ nên có một advice.”** SRP nói về trách nhiệm.
2. **“Gộp mọi cross-cutting code sẽ tái sử dụng tốt hơn.”** Gộp policy khác làm coupling.
3. **“Aspect càng nhiều càng tốt.”** Quá nhiều aspects vụn làm ordering phức tạp.
4. **“Đổi tên GodAspect thành CommonAspect là đủ.”** Không thay ownership.
5. **“Tách thành private methods là xong SRP.”** Chính sách vẫn chung owner.
6. **“Cùng một join point nghĩa là cùng một concern.”** Nhiều aspects có thể khớp cùng method.
7. **“Logger thay thế audit.”** Durable/audit semantics khác logging.
8. **“Metric recording không bao giờ có failure.”** Sink cũng có thể throw.
9. **“Audit recorder failure luôn có thể bỏ qua.”** Tùy compliance.
10. **“Retry có thể wrap mọi operation không cần idempotency.”** Có thể lặp side effects.
11. **“Timing outside và inside retry tương đương.”** Có total vs attempt duration.
12. **“Authorization inside cache vẫn bảo vệ cache hit.”** Cache bên ngoài có thể skip proceed.
13. **“Pointcut chỉ match tên method là đủ.”** Overload/types/binding có thể khác.
14. **“Một aspect nhỏ không thể có security bug.”** Scope thiếu có thể bypass.
15. **“Một shared startNanos field sẽ đúng nhờ volatile.”** Sai per-invocation correlation.
16. **“ThreadLocal tự đi theo future.”** Async propagation cần explicit.
17. **“Hai aspects tách biệt không có runtime coupling.”** Precedence và nested proceed tạo liên hệ.
18. **“Return future nghĩa là tác vụ xong.”** Completion là boundary khác.
19. **“Aspect phù hợp điều phối mọi nghiệp vụ.”** Domain workflow nên explicit khi cần.
20. **“Aspect outcome không cần test nếu chỉ log.”** Logging failure có thể che exception.
21. **“Có annotation trên method là proxy nhất định thấy.”** Metadata và weaving scope cần test.
22. **“Dùng một helper có currentRequest là an toàn vì aspects đã tách.”** Shared mutable helper vẫn rủi ro.
23. **“Không có log đồng nghĩa advice không chạy.”** Sampling/sink cũng ảnh hưởng.
24. **“Số failure events bằng số business failures.”** Một Throwable có thể được nhiều layers quan sát.
25. **“Đổi pointcut không ảnh hưởng behavior.”** Scope thay đổi nơi policy chạy.
26. **“Java Dynamic Proxy tương đương mọi execution weaver.”** Coverage khác nhau.
27. **“Một test helper đủ chứng minh advice.”** Cần integration qua interception.
28. **“Aspect lớn luôn sai.”** Cohesion và ownership mới là tiêu chí.
29. **“Aspect nhỏ luôn đúng.”** Một method ngắn vẫn có hai policies độc lập.
30. **“Bảo mật chỉ cần được instrument một phần.”** Kiểm boundary đầy đủ theo contract.

## 25. Checklist thiết kế trước khi thêm một aspect

### Trách nhiệm

- Có một câu mô tả concern rõ không?
- Có một policy owner chính không?
- Điều gì là lý do thay đổi độc lập?
- Những behavior trong aspect có cùng failure contract không?

### Pointcut và coverage

- Chọn đúng call hay execution join points?
- Có bỏ sót overload, inheritance, self-invocation, async worker?
- Có vô tình match helper/recorder của chính aspect không?
- Có kiểm cả path đi qua proxy và path bypass nếu contract yêu cầu?

### Advice và outcome

- Khi success có preserve return value?
- Khi failure có giữ original Throwable/cause khi cần?
- Nếu có retry/cache/authorization, số `proceed` calls đúng không?
- Có explicit policy khi recorder/metric/helper lỗi?

### State và lifecycle

- Per-invocation state được giữ local/đúng context?
- Shared configuration có immutable snapshot?
- Shared counters/cache có thread-safe và lifecycle đúng?
- Context qua executor có capture/restore/remove?

### Composition

- Thứ tự tương tác với các aspects khác đã quyết định chưa?
- Audit các denied/cached attempts ra sao?
- Timing tính total operation hay từng attempt?
- Transaction và retry có quan hệ đúng?

### Verification

- Có unit tests cho selector và advice?
- Có integration tests trên runtime interception thật?
- Có contract tests cho caller và side effects?
- Có test concurrency/reentrancy, error paths và async completion?

## 26. Bài tập thực hành

1. Biên dịch và chạy `AspectResponsibilityDemo.java`, đối chiếu stdout.
2. Vì sao `balance()` chỉ chạy TimingAspect?
3. Đảo thứ tự hai proxy, dự đoán thứ tự events của transfer.
4. Thêm method `refund`; khi nào nên audit nó cùng `transfer`?
5. Tạo overload `transfer`, kiểm vì sao selector chỉ so method name không luôn đủ.
6. Thử gọi raw target thay vì wrapper; quan sát advice nào không chạy.
7. Chuyển `started` thành field dùng chung rồi gọi nested/concurrent; giải thích sai correlation.
8. Thêm fake `AuditRecorder`, viết test khi recorder thành công và thất bại.
9. Đề xuất policy khi audit bắt buộc fail-closed nhưng timing metric best-effort.
10. Tạo AuthorizationAspect từ chối transfer; xếp trong/ngoài AuditAspect rồi kiểm denied event.
11. Tạo RetryAspect chỉ retry lỗi transient; so sánh duration khi Timing ở trong/ngoài Retry.
12. Tạo cache advice có thể skip proceed; phát hiện audit coverage của cache hit.
13. Viết một contract test rằng return value và exception của LedgerImpl không bị hai aspects làm đổi.
14. Tạo test xác minh một Throwable được audit và timing quan sát nhưng caller vẫn chỉ thấy một business failure.
15. Chia GodAspect thành các concerns, liệt kê lý do thay đổi của từng class.
16. Thiết kế một pointcut không match AuditRecorder của chính nó.
17. Xác định dữ liệu nào có thể ghi trong audit event mà không lộ raw reference.
18. Viết integration test cho method overload và callback worker nhằm phát hiện scope bị bỏ sót.
19. Cho một method trả `CompletableFuture` pending; timing đến return và completion khác nhau ra sao?
20. Đề xuất một shared Clock helper dùng được cho nhiều concerns nhưng không chứa request state.
21. Tạo một immutable TimingPolicy với revision; reload khi call đang chạy và xác định version được dùng.
22. Thiết kế ma trận regression khi đổi precedence của authorization/audit/timing/transaction.
23. Tạo một aspect có nhiều advice nhưng vẫn một policy owner, giải thích vì sao hợp SRP.
24. Tạo một aspect chỉ có một advice nhưng trộn cache và auth, giải thích vì sao vi phạm SRP.
25. Xây checklist review một aspect: **what, where, state owner, failure policy, precedence, verification**.

**Tự kiểm tra:** Vì sao một aspect có thể có nhiều advice mà vẫn một trách nhiệm? Audit khác logging ở điểm nào? Tại sao tách aspects vẫn phải quản lý precedence? Trạng thái mốc thời gian thuộc invocation hay singleton? Vì sao một failure có thể được ghi hai lần nhưng vẫn chỉ là một lỗi nghiệp vụ? Một aspect nhỏ nhưng chọn pointcut sai có đúng SRP không? Những tình huống nào nên giữ logic nghiệp vụ explicit thay vì chuyển vào aspect?

## Kết luận

**Đóng gói aspect theo một mối quan tâm có trách nhiệm rõ** là cách biến AOP thành công cụ module hóa thực sự thay vì chỉ chuyển code lặp sang một nơi khác. Một aspect tốt có **mục tiêu, pointcut, advice behavior, data ownership và failure policy nhất quán**; có thể chứa nhiều advice nếu chúng cùng phục vụ một contract.

Tách audit, timing, authorization, retry và các concerns độc lập giúp giảm coupling, tăng cohesion và dễ kiểm thử, nhưng **không xóa bỏ trách nhiệm kiểm tra thứ tự advice, ranh giới interception, cách xử lý exception và async**. Điểm đến không phải số aspect nhiều hay ít, mà là mỗi concern **có thể được hiểu, thay đổi, kiểm thử và vận hành riêng**, đồng thời tương tác dự đoán được khi cùng áp dụng một join point.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-abstraction-reuse-009">Đánh giá mức tái sử dụng so với độ phức tạp</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá mức tái sử dụng so với độ phức tạp trong Aspect/AOP

**Aspect-Oriented Programming (AOP)** giúp đóng gói những mối quan tâm cắt ngang — logging, tracing, metrics, kiểm toán, kiểm tra quyền, transaction, retry hoặc quản lý tài nguyên — thay vì rải cùng logic vào nhiều phương thức nghiệp vụ. Một lời hứa hấp dẫn là **tái sử dụng**: một aspect hoặc một khung advice có thể áp dụng cho nhiều join point. Nhưng mỗi lớp trừu tượng bổ sung cũng có giá: người đọc phải hiểu pointcut, thứ tự aspect, binding, phạm vi weaving, vòng đời instance, các đường short-circuit và tác động của advice lên kết quả hoặc ngoại lệ.

Vì vậy, câu hỏi của bài không phải “nên tạo bao nhiêu aspect?”, mà là: **mức tái sử dụng thực sự mang lại có lớn hơn chi phí hiểu, thay đổi, kiểm thử và vận hành trừu tượng đó không?**

Một bộ ghi log đơn giản dùng chung ở 20 phương thức có thể tiết kiệm nhiều công sức và giảm sai khác. Nhưng một “siêu aspect” nhận hàng chục cờ cấu hình như `isAudit`, `doRetry`, `secure`, `transactional`, `cache` có thể tạo ra hàng trăm tổ hợp hành vi khó kiểm chứng. Tái sử dụng nhiều dòng mã không đồng nghĩa cải thiện thiết kế nếu phải trả bằng **coupling ngầm và độ phức tạp hành vi**.

Bài này bàn về **Aspect/AOP cơ bản và nâng cao nói chung**. Các ngôn ngữ/công cụ có khả năng pointcut, advice, kế thừa aspect, weaving, proxy và instantiation khác nhau. Ví dụ Java 17 sử dụng **JDK dynamic proxy với interceptor tái sử dụng**, không giả định mọi hệ AOP có cùng API.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

1. Phân biệt **tái sử dụng logic**, **tái sử dụng định nghĩa pointcut**, **tái sử dụng cấu hình** và **tái sử dụng hợp đồng vận hành**.
2. Nhìn thấy chi phí của abstraction qua số điểm mở rộng, sự phụ thuộc giữa aspect, phạm vi ảnh hưởng khi sửa và độ khó debug.
3. Quyết định khi nào nên để code lặp nhỏ, khi nào nên trích helper/service, khi nào nên tạo aspect hoặc abstract aspect.
4. Đo lợi ích theo số lỗi tránh được, thời gian thay đổi, độ nhất quán, khả năng kiểm thử và tác động lên runtime.
5. Đánh giá **pointcut quá rộng**, join point bị bỏ sót, advice precedence, retry, short-circuit và state theo lời gọi.
6. Phân biệt **những concern thực sự cùng một hợp đồng** với concern chỉ tình cờ có cùng vài dòng code.
7. Sử dụng một ma trận chấm điểm hoặc tính toán chi phí giả định mà **không ngụy tạo độ chính xác**.
8. Viết Java 17 dùng một khung observation tái sử dụng nhưng cấu hình phạm vi đơn giản và giữ nguyên return/throw.
9. Kiểm thử các trường hợp bình thường, lỗi, chồng pointcut, cấu hình sai, đường bypass và tải cao.
10. Biết khi nào nên **dừng trừu tượng hóa**, tách aspect lớn và thay kế thừa bằng composition.

## 2. “Tái sử dụng” trong AOP gồm những gì?

Cần tách ít nhất năm cấp:

| Cấp | Thành phần được dùng chung | Ví dụ | Rủi ro |
| --- | --- | --- | --- |
| **Logic thuần** | Hàm định dạng, phép đo, serializer | `formatOperationName()` | Thường thấp nếu API rõ |
| **Advice template** | Khung before/proceed/after/finally | `observe(next)` | Hợp đồng lỗi hoặc retry bị dùng chung quá mức |
| **Pointcut definition** | Tập join point được đặt tên | “Execution của các thao tác xử lý đơn” | Wildcard kéo thêm join point ngoài ý định |
| **Aspect abstraction** | Abstract aspect, base class, policy hooks | Cùng khung trace, khác nhóm method | Kế thừa, instance scope và version coupling |
| **Cross-cutting architecture** | Bộ policy, cấu hình và thứ tự áp dụng xuyên module | Pipeline Auth → Audit → Target | Coupling/precedence, trạng thái, hiệu ứng và độ phủ |

**Giảm số dòng** chỉ là một phần rất nhỏ của giá trị. Tái sử dụng có thể cho lợi ích lớn hơn: thay một quy tắc lọc dữ liệu nhạy cảm ở một nơi; thống nhất nghĩa của metric; sửa bug bảo toàn ngoại lệ trên toàn bộ điểm can thiệp; tăng khả năng kiểm tra một policy bảo mật.

Ngược lại, một abstraction có thể có rất ít dòng nhưng nhiều **trạng thái ẩn**: “Nếu `attemptNumber > 1`, trừ khi `cacheHit`, bỏ `afterReturn` nếu `async`, trừ phương thức thuộc package X...”. Tính phức tạp này không hiện ra khi chỉ đếm LOC.

### 2.1. Tái sử dụng mã không phải tái sử dụng ngữ nghĩa

Hai advice đều ghi một dòng `System.out.println` nhưng khác hợp đồng:

- Advice A ghi **mọi yêu cầu truy cập**, kể cả bị từ chối.
- Advice B chỉ ghi **giao dịch đã cam kết bền vững**.
- Advice C chỉ là **trace hỗ trợ**, được phép bỏ mẫu khi exporter down.

Không nên gộp A/B/C thành một observer chung chỉ vì cùng gọi một logger. Chính sách về thời điểm chạy, retry, lỗi backend, nguồn dữ liệu và mức bắt buộc **không giống nhau**.

Một abstraction tốt chia sẻ **phần bất biến thực sự giống nhau** và để các khác biệt quan trọng hiện rõ, thay vì giấu chúng sau những cờ khó đọc.

## 3. Chi phí có thể xuất hiện ở đâu?

Một aspect tái sử dụng cần được xem ở toàn vòng đời:

1. **Thiết kế:** xác định pointcut và contract đủ tổng quát.
2. **Xây dựng:** viết weaver/configuration, binding, aspect base, helper và tests.
3. **Hiểu và bảo trì:** developer cần truy ngược đường chạy qua advice trước khi nhìn target.
4. **Thay đổi:** sửa một abstraction có thể tác động tới nhiều module.
5. **Kiểm thử:** tất cả nhóm join point, return/throw, order và đường không khớp.
6. **Tích hợp:** phiên bản Java, build plugin, class loader, module, woven artifact.
7. **Vận hành:** overhead, logs/metrics, lỗi do aspect, startup, hot reload.
8. **Gỡ bỏ:** nếu trừu tượng sai, phải tháo nó ở bao nhiêu caller và cấu hình?

Một câu “chỉ cần một aspect cho mọi thứ” thường chỉ tính **chi phí viết**, bỏ qua phần còn lại.

### 3.1. Độ phức tạp tại điểm gọi

Trong lập trình thông thường, lời gọi `service.place()` thường đủ để biết đi tới một method. Với AOP, có thể tồn tại:

```text
Caller
  -> proxy or woven join point
     -> authorization aspect
        -> retry aspect
           -> transaction aspect
              -> metrics aspect
                 -> target
```

Một lỗi có thể phát sinh **trước khi target được gọi**, trong target, sau return hoặc trong finally. Nếu những aspect được tái sử dụng toàn ứng dụng, một lần sửa pointcut có thể ảnh hưởng rất nhiều đường gọi.

Điểm cân bằng là **lợi ích tập trung chính sách** có vượt chi phí hiểu chuỗi ấy hay không.

## 4. Dấu hiệu có lợi từ tái sử dụng

Một concern là ứng viên tốt nếu thỏa nhiều điều kiện:

- Có ít nhất vài điểm cùng một **bất biến**, không chỉ giống vài từ khóa.
- Cần sửa đồng loạt để tránh sai khác về bảo mật hoặc tính đúng.
- Có **đầu vào/đầu ra rõ** từ join point; ít cần state bí mật từ aspect khác.
- Advice không tùy tiện thay đổi kết quả, ngoại lệ và số lần gọi target.
- Tập join point có thể chọn **chính xác và kiểm thử được**.
- Policy có ownership và vòng đời độc lập với nghiệp vụ.
- Chi phí test chung thấp hơn việc duy trì nhiều bản sao.
- Những biến thể có thể diễn đạt bằng **ít tham số có nghĩa**.
- Team có thể hiểu được scope/precedence mà không cần lần theo nhiều lớp kế thừa.
- Công cụ weaving/proxy đã có trong hệ thống và hoạt động ổn định.

Ví dụ hợp lý: mọi request được phép mang cùng **correlation metadata** và có chuẩn ghi log an toàn. Một library nhỏ xử lý chuẩn hóa tên, redaction và vòng đời scope có thể giảm lỗi hơn nhiều bản tự viết.

### 4.1. Một thay đổi được nhân rộng là lợi ích hay rủi ro?

Khi thay một advice dùng chung, tất cả join point liên quan nhận thay đổi. Đó là:

- **Lợi ích** nếu điều đó là yêu cầu thống nhất.
- **Rủi ro lan truyền** nếu các nhóm dùng abstraction thực ra cần contract khác.

Tính tổng quát phải đi đôi với **phạm vi sở hữu rõ ràng** và regression test đủ mạnh.

## 5. Những tín hiệu của trừu tượng hóa quá mức

Cảnh báo khi một abstract aspect/policy:

1. Có nhiều cờ Boolean điều khiển các nhánh không liên quan.
2. Dùng nhiều subclass chỉ để đổi một literal rất nhỏ.
3. Phải đọc 5–7 file mới thấy được một đường `proceed`.
4. Dùng wildcard pointcut rộng vì không biết domain nào sẽ áp.
5. Cần ghi ngoại lệ/thành phần “trường hợp riêng” thường xuyên.
6. Có thứ tự precedence thay đổi theo môi trường, khó test.
7. Cần static/ThreadLocal context để truyền dữ liệu ngầm giữa các aspect.
8. Cùng một exception được xử lý theo nhiều chính sách trái nhau trong một template.
9. Chuyển từ một framework/toolchain khác trở nên quá đắt vì mọi business logic phụ thuộc API AOP.
10. Thêm một tính năng nhỏ làm nảy sinh nhiều tổ hợp cấu hình và test hơn giá trị nó tạo.
11. Khi debug phải tắt nhiều aspect mới cô lập được nguyên nhân.
12. Một “base aspect” được kế thừa vì tiện, dù không có quan hệ **is-a** về ngữ nghĩa.

Không phải có nhiều tham số là luôn xấu; vấn đề là **các trục biến thiên tương tác lẫn nhau**. Ví dụ 5 cờ độc lập có thể tạo tối đa 32 tổ hợp. Nếu mỗi tổ hợp có ý nghĩa khác về retry/audit/security, phải có cách kiểm soát các kết hợp hợp lệ, không chỉ có unit test từng cờ.

## 6. Tách ba loại chi phí: cố định, biên và thay đổi

Một cách đánh giá hữu ích:

```text
Tổng chi phí ước tính =
  thiết kế + triển khai ban đầu
  + tích hợp/weaving
  + kiểm thử/đào tạo
  + bảo trì trên các lần thay đổi dự kiến
  + chi phí vận hành và rủi ro lỗi
```

Đây là **mô hình tư duy**, không phải công thức tài chính có độ chính xác tự động. Có thể dùng giờ công, độ phức tạp định tính hoặc số lỗi đã quan sát; tránh cộng tùy ý “điểm kỹ thuật” rồi giả vờ là tiền thật.

### 6.1. Chi phí ban đầu

Tách thành một aspect dùng chung có thể cần:

- Chốt hợp đồng observation/guard.
- Định nghĩa pointcut theo loại join point được tool hỗ trợ.
- Cấu hình thứ tự, binding, exception handling.
- Thêm build/weaving, compatibility tests.
- Đào tạo team đọc lời gọi xuyên advice.

Nếu hệ chỉ có **hai điểm áp** và logic rất nhỏ, chi phí ban đầu có thể lớn hơn việc giữ hai đoạn code rõ ràng.

### 6.2. Chi phí thêm một điểm áp dụng

Với một abstraction đã ổn định, thêm điểm thứ 20 có thể rất rẻ: chỉ cần thêm một rule pointcut và test coverage. Nhưng nếu mỗi điểm phải tạo subclass, thêm flags và đặc biệt hóa thứ tự, **chi phí biên không còn thấp**.

Hãy đo: “Để áp cùng chính sách cho một method mới, có bao nhiêu file cần sửa, bao nhiêu test cần bổ sung, và có bao nhiêu thành phần khác có thể bị ảnh hưởng?”

### 6.3. Chi phí sửa quy tắc chung

Nếu một chính sách redaction cần thay ở 15 nơi độc lập, lỗi sửa sót dễ xảy ra. Một helper/aspect dùng chung có lợi. Tuy vậy nếu chỉ 6 trong 15 nơi cần luật mới, một abstraction áp tất cả có thể tạo **breaking change**; cần phân biệt policy versions hoặc tách trách nhiệm.

## 7. Ví dụ tính toán giả định: khi nào hòa vốn?

Giả sử một nhóm cần cùng một cơ chế telemetry ở N nhóm method. Đây là **số liệu giả định phục vụ học**, không phải benchmark.

| Hạng mục | Viết riêng ở từng nơi | Một abstraction dùng chung |
| --- | --- | --- |
| Chi phí ban đầu | 1,5 giờ cho mỗi nhóm | 10 giờ thiết kế, test và tích hợp |
| Chi phí nối mỗi nhóm tiếp theo | 1,5 giờ | 0,5 giờ cấu hình và test |
| Chi phí một lần thay đổi chung | 1 giờ cho mỗi nhóm | 3 giờ sửa + regression |
| Phụ thuộc runtime/tooling | Ít | Có, phải kiểm soát |
| Rủi ro sửa sót hàng loạt | Cao hơn khi N lớn | Giảm, nhưng blast radius tăng |

Nếu chỉ tính **thiết lập ban đầu**:

```text
Separate(N) = 1.5 × N
Reusable(N) = 10 + 0.5 × N
Break-even: 1.5N = 10 + 0.5N  => N = 10
```

N = 3: viết riêng khoảng **4,5 giờ**, abstraction khoảng **11,5 giờ** theo giả định. N = 20: viết riêng **30 giờ**, abstraction **20 giờ**.

Nhưng nếu có ba lần đổi chung, mô hình cần cộng chi phí đổi mỗi nhánh hoặc một lần sửa abstraction. Nếu một cú sửa abstraction tạo regression diện rộng, con số “3 giờ” có thể quá lạc quan. Bởi vậy đây là **công cụ hỏi đúng câu hỏi**, không phải quy tắc “có 10 điểm thì phải dùng AOP”.

### 7.1. Chi phí không đồng nhất theo điểm can thiệp

Một method chạy mỗi ngày và một method chạy hàng triệu lần/giây có chi phí runtime khác nhau. Cùng một advice đo trace có thể chấp nhận được ở nhánh I/O chậm nhưng gây overhead rõ ở hot path.

Một lời gọi có dữ liệu đã cho phép logging khác với một lời gọi có credential/payload nhạy cảm. **Đếm số join point không đủ** để quyết định dùng chung.

### 7.2. Ước lượng theo khoảng, không theo một con số

Nếu không có số thực, ghi rõ khoảng:

- Thiết lập abstraction: **8–14 giờ** giả định.
- Test mỗi điểm bổ sung: **0,3–1 giờ** giả định.
- Số lần thay đổi policy trong năm: **1–5** giả định.

Làm kịch bản tốt/xấu để thấy kết luận có đảo không. Nếu chỉ một thay đổi nhỏ trong giả định khiến kết luận đảo chiều, hãy chọn giải pháp **dễ đảo ngược**: helper method hoặc policy object trước khi đưa thêm cơ chế weaving phức tạp.

## 8. Các thước đo kỹ thuật tốt hơn số dòng code

| Thước đo | Cách quan sát | Vì sao hữu ích |
| --- | --- | --- |
| **Số lỗi lặp lại** | Bug cùng nguyên nhân ở nhiều nơi | Tái sử dụng có thể xóa một lớp sai khác |
| **Thời gian thay đổi quy tắc chung** | Từ yêu cầu đến test/deploy | Đo lợi ích bảo trì |
| **Blast radius** | Số module/join point bị ảnh hưởng bởi một sửa đổi | Đánh giá rủi ro tập trung |
| **Độ rõ của ownership** | Ai được sửa chính sách và pointcut? | Giảm cấu hình xung đột |
| **Pointcut precision** | Điểm mong muốn chọn đủ, điểm không mong muốn loại đúng | Đánh giá chất lượng áp dụng |
| **Advice overlap** | Join points bị nhiều aspect cùng chọn | Dự báo trùng hành vi và precedence |
| **Số cấu hình hợp lệ** | Những tổ hợp policy thực sự được hỗ trợ | Đo độ phức tạp kiểm thử |
| **Khó khăn debug** | Số bước/tool cần truy ra effect | Đo chi phí nhận thức |
| **Runtime overhead** | Latency, allocations, GC và throughput | Kiểm soát hot path |
| **Phạm vi phụ thuộc tooling** | Compiler/weaver/runtime/plugins | Đánh giá lock-in và build fragility |
| **Tốc độ mở rộng** | Nơi phải sửa để thêm một join point | Đánh giá chi phí biên |
| **Tỷ lệ test hợp đồng** | Return/throw/async/bypass/overlap | Đánh giá an toàn của abstraction |

### 8.1. “Số dòng giảm” không tự động có nghĩa chất lượng tăng

Một triển khai dùng một method dài với nhiều nhánh điều kiện có thể ít dòng hơn ba aspect nhỏ, nhưng khó hiểu hơn. Đánh giá **độ phức tạp cyclomatic/cognitive**, số trạng thái có thể đi qua và khả năng người mới giải thích kết quả.

### 8.2. Đếm đúng đơn vị

Một lời gọi business có thể có nhiều **attempts** do retry, nhiều **advice executions** do pointcut overlap, và chỉ một **committed effect** hoặc không có. Nếu muốn đo lợi ích reusable aspect, cần định nghĩa thứ đang đếm: logical request, invocation, attempt, successful return hay commit.

## 9. Điểm quan trọng nhất của AOP: độ chính xác pointcut

Một reusable advice có giá trị chỉ khi nó chạy tại đúng những điểm cần thiết. Tập các join point được chọn có thể biểu diễn:

```text
Expected = tập join point chính sách phải áp
Selected = tập join point pointcut thực tế chọn

Misses = Expected - Selected
Unexpected = Selected - Expected
```

- **Misses** làm thiếu instrumentation hoặc thiếu chính sách bắt buộc.
- **Unexpected** tạo overhead, log sai, recursion hoặc thay đổi nghiệp vụ ngoài phạm vi.
- **Overlap** với những aspect khác có thể nhân đôi advice hay đổi thứ tự.

Có thể dùng một bảng test để liệt kê điểm **phải khớp** và **không được khớp**, thay vì chỉ thử một đường happy path.

### 9.1. Wildcard có chi phí ẩn

Một pointcut kiểu “mọi phương thức trong mọi service” có thể rất dễ tái sử dụng. Nhưng khi nhóm thêm một method nội bộ được gọi hàng triệu lần, nó cũng bị instrument. Nếu advice ghi I/O log, hệ có thể chậm đột ngột dù code nghiệp vụ mới không trực tiếp import aspect.

Nên ưu tiên **phạm vi hẹp, có chủ đích** và sử dụng marker/contract tường minh khi phù hợp. Với thao tác nhạy cảm, không chỉ dựa vào tên class/package để kết luận đã bảo vệ mọi đường effect.

### 9.2. Join point kind và khả năng công cụ

Cùng từ “intercept phương thức” có thể có hai mục tiêu: **method call** tại caller và **method execution** tại target. Một JDK proxy chỉ áp cho lời gọi **qua interface proxy**; nó không tự xử lý mọi field access, constructor, private method hay self-invocation trên target.

Lợi ích tái sử dụng phải tính cả **độ phủ thực tế** và chi phí build/deploy woven artifact. Một pointcut “đẹp” nhưng không bao phủ đường chạy quan trọng làm thiết kế sai.

## 10. Sự phức tạp do nhiều aspect phối hợp

Một aspect độc lập có thể tương đối đơn giản. Khi nhiều aspect cùng chọn một điểm:

```text
Metrics(Authorization(Cache(Retry(Transaction(Target)))))
```

thì thứ tự có thể thay đổi ý nghĩa:

- Metrics ngoài Authorization có thể đếm cả lời gọi bị từ chối; bên trong không thấy yêu cầu bị short-circuit.
- Cache nằm ngoài Authorization có thể trả cache hit mà guard bên trong không chạy.
- Retry ngoài Transaction có thể tạo transaction riêng mỗi attempt; cấu trúc đảo có thể thử nhiều lần trong cùng transaction.
- Audit sau target nhưng trước outer transaction commit không có bằng chứng “đã cam kết”.
- Một fallback có thể nuốt ngoại lệ mà observer ngoài muốn thấy.

**Tái sử dụng một khung around advice không làm những phụ thuộc này tự biến mất.** Cần contract về thứ tự, scope, kết quả và lỗi cho từng tổ hợp thật sự dùng.

### 10.1. Có nên kế thừa một “siêu aspect” chứa tất cả?

Thông thường nên thận trọng. Một base chung cho authentication, retry, audit, cache và metrics khiến subclass phải hiểu tất cả nhánh bất chấp nó chỉ cần một chức năng. Có nhiều trạng thái không hợp lệ, bảo trì khó và rủi ro fail-open.

Hãy tách những concern có **bất biến khác nhau** thành components riêng; phối hợp bằng precedence rõ hoặc coordinator cho nghiệp vụ quan trọng. Dùng inheritance khi có một **template hành vi thống nhất**, dùng composition khi các chính sách chỉ cộng tác.

### 10.2. Không dùng advice precedence thay cho giao thức atomic

Nếu bắt buộc lưu nghiệp vụ và audit bền vững cùng nhau, chỉ đổi thứ tự Transaction/Audit không bảo đảm atomicity với hệ thống bên ngoài. Có thể cần transaction boundary, outbox, idempotency và đối soát. Đây là **ngữ nghĩa nghiệp vụ**, không phải thứ có thể trừu tượng hóa hết bằng pointcut.

## 11. Chọn hình thức tái sử dụng phù hợp

Không phải mọi tái sử dụng đều cần một abstract aspect.

| Lựa chọn | Khi phù hợp | Điểm cần đề phòng |
| --- | --- | --- |
| **Giữ code trực tiếp** | Ít nơi, logic ngắn, ít thay đổi, khác nghĩa | Trùng lặp có thể lan rộng sau này |
| **Hàm/helper chung** | Cùng phép xử lý thuần, không cần chặn join point | Tránh helper trở thành nơi giữ state ngầm |
| **Strategy/policy object** | Thay một thuật toán trong contract rõ | Nhiều cấu hình và dependency tiềm ẩn |
| **Decorator/interceptor** | Muốn wrapper ở ranh giới do caller lựa chọn | Đường đi ngoài wrapper có thể bypass |
| **Aspect riêng** | Concern cắt ngang nhiều điểm, pointcut ổn định | Coverage, tooling, thứ tự |
| **Abstract aspect** | Chung advice skeleton, concrete pointcuts khác | Kế thừa phụ thuộc ngôn ngữ, hooks và lifecycle |
| **Named pointcuts** | Chia sẻ tập join point, không nhất thiết cùng advice | Tập điểm có thể quá rộng |
| **Composition nhiều aspects** | Chính sách tách biệt, phối hợp theo contract | Precedence/overlap |
| **Coordinator nghiệp vụ** | Cần bất biến, transaction hoặc workflow chặt | Không nên giấu logic nghiệp vụ cốt lõi trong aspect |

### 11.1. Quy tắc ra quyết định theo biến thiên

Nếu phần **giống nhau** là một thuật toán nhỏ: trích **helper**.

Nếu phần giống nhau là **khung điều khiển advice** còn khác biệt chỉ ở pointcut/label: một **base aspect** hoặc interceptor template có thể hợp lý.

Nếu các phần cần **thứ tự phối hợp** nhưng nghiệp vụ khác nhau: **composition** và contract precedence.

Nếu có **nguyên tử, audit bền vững, retry hiệu ứng**, cân nhắc một **coordinator/service** ở ranh giới nghiệp vụ thay vì cố tổng quát hóa advice.

### 11.2. Một abstraction dễ thay thế thường thắng sớm

Khi chưa rõ hai điểm có thật sự cùng nghĩa, có thể giữ hai thành phần độc lập nhưng dùng chung **một hàm thuần**. Sau khi các yêu cầu thay đổi thực sự cho thấy một bất biến lặp lại, mới đưa lên abstraction cấp aspect.

Đây không phải chống tái sử dụng; nó là **giảm cam kết sớm vào một trừu tượng chưa có bằng chứng**.

## 12. Ví dụ Java 17: tái sử dụng khung advice, không gộp mọi chính sách

Để thấy mức tái sử dụng hợp lý, ta xây một bộ interception rất nhỏ bằng **JDK dynamic proxy**:

- Một `Rule` chứa **nhãn** và điều kiện chọn operation.
- Một hàm `invokeAt` dùng chung khung `before → proceed → returned/throw → finally` cho mọi rule phù hợp.
- Rule `BASE` áp với mọi lời gọi `run`; rule `FIN` chỉ áp khi operation bắt đầu bằng `bill-`.
- Ở operation `bill-1`, hai rule **cùng khớp** nên sinh hai lớp log lồng nhau; đó không phải lỗi Java inheritance.
- Với operation `bad`, target ném ngoại lệ; chương trình **giữ lỗi gốc**, không trả fallback giả.
- Một lời gọi **trực tiếp target gốc** không đi qua proxy; đây là giới hạn được kiểm chứng, không giấu sau API.
- Ví dụ không chứa retry/auth/audit/transaction: chúng không chia sẻ cùng hợp đồng quan sát.

**Đây là ví dụ tái sử dụng logic interceptor với Java thuần, không phải định nghĩa native pointcut/advice của một ngôn ngữ AOP.**

Lưu thành `ReuseComplexityDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;

public class ReuseComplexityDemo {
    public interface WorkPort {
        String run(String operation);
    }

    public static final class WorkService implements WorkPort {
        int calls;

        @Override
        public String run(String operation) {
            calls++;
            System.out.println("TARGET " + operation);
            if ("bad".equals(operation)) {
                throw new IllegalArgumentException("invalid:bad");
            }
            return "ok:" + operation;
        }
    }

    record Rule(String tag, Predicate<String> selected) {}

    static Object invokeTarget(
            Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Object invokeAt(
            int index, List<Rule> rules,
            Method method, Object target, Object[] args)
            throws Throwable {
        if (index == rules.size()) {
            return invokeTarget(method, target, args);
        }

        Rule rule = rules.get(index);
        String operation = (String) args[0];
        if (!rule.selected().test(operation)) {
            return invokeAt(index + 1, rules, method, target, args);
        }

        System.out.println(rule.tag() + " before " + operation);
        try {
            Object result = invokeAt(
                    index + 1, rules, method, target, args);
            System.out.println(rule.tag() + " returned " + result);
            return result;
        } catch (Throwable error) {
            System.out.println(rule.tag() + " threw "
                    + error.getClass().getSimpleName());
            throw error;
        } finally {
            System.out.println(rule.tag() + " finally");
        }
    }

    static WorkPort wrap(WorkPort target, List<Rule> rules) {
        return (WorkPort) Proxy.newProxyInstance(
                WorkPort.class.getClassLoader(),
                new Class<?>[] { WorkPort.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("run")
                            || method.getParameterCount() != 1) {
                        throw new UnsupportedOperationException(
                                method.getName());
                    }
                    return invokeAt(0, rules, method, target, args);
                });
    }

    public static void main(String[] args) {
        WorkService target = new WorkService();
        WorkPort observed = wrap(target, List.of(
                new Rule("BASE", op -> true),
                new Rule("FIN", op -> op.startsWith("bill-"))));

        System.out.println("CALL " + observed.run("ship-1"));
        System.out.println("CALL " + observed.run("bill-1"));

        try {
            observed.run("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CAUGHT " + error.getMessage());
        }

        System.out.println("DIRECT " + target.run("bill-2"));
        System.out.println("TOTAL_TARGET_CALLS " + target.calls);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ReuseComplexityDemo.java
java ReuseComplexityDemo
```

**Stdout dự kiến chính xác:**

```text
BASE before ship-1
TARGET ship-1
BASE returned ok:ship-1
BASE finally
CALL ok:ship-1
BASE before bill-1
FIN before bill-1
TARGET bill-1
FIN returned ok:bill-1
FIN finally
BASE returned ok:bill-1
BASE finally
CALL ok:bill-1
BASE before bad
TARGET bad
BASE threw IllegalArgumentException
BASE finally
CAUGHT invalid:bad
TARGET bill-2
DIRECT ok:bill-2
TOTAL_TARGET_CALLS 4
```

### 12.1. Những gì được tái sử dụng

Logic `before/proceed/returned/throw/finally` nằm trong **một** hàm `invokeAt`. Hai rule không lặp lại điều khiển ngoại lệ; chúng chỉ cung cấp **nhãn và tập operation**. Nếu muốn sửa dạng thông báo, có thể sửa khung chung.

**Giá trị:** nhất quán trong cách ghi return/throw, không quên finally giữa hai implementations.

**Chi phí:** mọi lời gọi qua proxy cần đi qua danh sách rule và call recursion. Người đọc phải biết `invokeAt` và `Predicate` mới hiểu vì sao operation `bill-1` có hai lớp log.

### 12.2. Vì sao `ship-1` chỉ có BASE?

Rule BASE chọn mọi operation; rule FIN chỉ chọn tên bắt đầu `bill-`. Khi `ship-1` đi vào FIN, rule không khớp và lập tức chuyển tiếp, không in log FIN. Target được gọi một lần và trả `ok:ship-1`.

### 12.3. Vì sao `bill-1` có hai bộ log?

Cả BASE và FIN cùng chọn `bill-1`. Chuỗi tương đương:

```text
BASE(FIN(Target))
```

Nó chạy BASE before, FIN before, target, FIN returned/finally, rồi BASE returned/finally. **Overlap được chủ động phơi bày**, không lén deduplicate vì các rule dùng chung hàm.

### 12.4. Vì sao nhánh lỗi giữ nguyên `IllegalArgumentException`?

Target ném lỗi sau khi tăng counter. Reflection bọc lỗi trong `InvocationTargetException`; `invokeTarget` lấy `getCause()`. Hàm chung in lỗi, rồi **ném lại cùng throwable**. Caller bắt đúng loại lỗi gốc và thấy thông điệp `invalid:bad`.

Nếu triển khai observer thực sự có một exporter dễ lỗi, cần **tách phạm vi catch của exporter** khỏi phạm vi `proceed`; nếu không, lỗi từ observer có thể che lỗi target. Ví dụ chỉ dùng `System.out` trong đường thực thi dự kiến.

### 12.5. Vì sao lời gọi trực tiếp không có log?

`target.run("bill-2")` dùng đối tượng WorkService gốc, không đi qua proxy. Việc ta xây một reusable interceptor **không biến toàn bộ lớp target thành woven class** và không bảo vệ tất cả đường gọi. Đây là chi phí kiến trúc cần tính nếu tái sử dụng qua proxy: đảm bảo caller có đúng reference và kiểm thử coverage.

### 12.6. Hạn chế để tránh áp dụng sai

- Điều kiện FIN dựa trên **prefix của dữ liệu operation**, không phải một native pointcut về method signature. Một thiết kế AOP thực nên chọn join point đúng theo loại method/annotation/type được hỗ trợ.
- Handler chỉ phục vụ method `run(String)` ở các đường của demo. Nó chưa thiết kế đầy đủ `equals`, `hashCode`, `toString` và mọi overload có thể có.
- `Predicate` là code Java thông thường, có thể ném exception hoặc chiếm nhiều CPU; rule không tự vô hại.
- Không có retry, transaction, audit bền vững hay kiểm tra quyền. Đừng nhét chúng vào chung template observation mà không thiết kế contract mới.
- Không có weaving vào body target, field access hay constructor; direct calls và self-invocation có thể bypass.
- Trạng thái counter chỉ tăng trong chương trình chạy tuần tự này; với concurrent calls cần xác định tính thread-safe nếu dùng counter trong sản xuất.
- Nếu muốn chỉ **một** log cho mỗi logical request, hai rule overlap có thể không phù hợp; khi đó phải quy định rõ precedence/deduplication.

## 13. Ví dụ phản chứng: trừu tượng quá rộng

Giả sử một nhóm tạo một aspect dùng chung:

```text
UniversalAspect(config):
    if config.cacheEnabled and hit: return cachedValue
    if config.checkPermission: authorize()
    if config.retry: loop:
        if config.transaction: beginTransaction()
        runTarget()
    if config.audit: audit()
    if config.metrics: emitMetric()
```

Đây là **giả mã thiết kế cần cảnh giác**, không phải một mẫu áp dụng mặc định. Những nhánh tưởng độc lập lại tương tác:

- Cache hit có bỏ qua kiểm tra quyền không?
- Retry có mở transaction riêng mỗi attempt?
- Audit xảy ra trước hay sau commit?
- Nếu metrics backend lỗi sau target, caller có thấy lỗi không?
- Có thể trả cached value cho operation ghi không?
- Những flags được bật/tắt ở runtime thì trạng thái transaction xử lý thế nào?

Nếu một abstraction đòi trả lời quá nhiều câu hỏi thuộc **những hợp đồng khác nhau**, hãy tách nó ra. Có thể dùng helper chung cho logging format, nhưng để authorization, retry, transaction, audit là các policies tách biệt với điều phối rõ.

### 13.1. “Một chỗ để sửa” có thể là một điểm lỗi tập trung

Tập trung logic đúng giúp sửa lỗi đồng loạt. Tập trung mọi thứ vào một hệ thống điều kiện có thể khiến một thay đổi nhỏ ảnh hưởng hàng nghìn lời gọi không liên quan. Cần **đánh giá phạm vi tác động trước mỗi thay đổi**, có canary/feature rollout phù hợp và regression tests trên những điểm can thiệp quan trọng.

## 14. Tái sử dụng qua kế thừa aspect: khi nào đáng giá?

Nếu ngôn ngữ AOP hỗ trợ **abstract aspect** và **abstract pointcut**, một base aspect có thể định nghĩa advice skeleton, còn concrete aspects cung cấp pointcuts cho từng miền.

Mô hình:

```text
AbstractObservationAspect
  + shared advice: enter/return/throw/finally
  + abstract selectedOperations()

OrderObservation
  + selectedOperations() = OrderService operations

InvoiceObservation
  + selectedOperations() = InvoiceService operations
```

**Lợi ích:** định nghĩa khung advice một lần, các concrete specializations chỉ cấu hình phạm vi.

**Chi phí:** phụ thuộc cơ chế inheritance của ngôn ngữ, version của compiler/weaver, binding signatures, visibility, instantiation policy và giao nhau giữa pointcuts.

Không mặc định một subclass có thể **override advice** như override Java method; lời khai báo advice, việc kế thừa nó và precedence của các aspect có luật ngữ nghĩa riêng theo công cụ. Nếu chỉ cần tái sử dụng formatter, **helper/delegate** có thể ít rủi ro hơn một base aspect.

### 14.1. Cây kế thừa càng sâu, hợp đồng càng khó thấy

Một cây năm tầng có thể ép concrete aspect phải hiểu cả năm nhóm hooks. Khi sửa base, tất cả hậu duệ bị ảnh hưởng. Thường nên ưu tiên **một hoặc rất ít tầng**, giữ các điểm mở rộng ít và được kiểm thử, rồi dùng composition cho khác biệt độc lập.

### 14.2. State kế thừa không tự tách theo invocation

Field `currentRequest` trên base, nếu concrete instance dùng chung giữa các request, có thể gây race và giữ dữ liệu lâu. Điều kiện `cflow` chọn join point cũng **không tự tạo instance per-flow**; nếu ngôn ngữ có `percflow` hoặc cơ chế instantiation tương tự, phải xác minh riêng.

Một abstracion dùng chung tốt không nên đòi mọi concrete child giữ request object trong một mutable field suốt đời.

## 15. Chi phí kiểm thử tăng theo số chiều hành vi

Một rule mới có thể tương tác với:

- Nhánh target **return/throw**;
- Có/không có authorization;
- Cache **hit/miss**;
- Retry **1/N attempts**;
- Transaction **commit/rollback**;
- Advice lỗi **trước/sau effect**;
- Path qua **proxy/direct target**;
- Lời gọi **sync/async**;
- Một/nhiều aspects cùng khớp;
- Một hoặc nhiều thread.

Không phải lúc nào cũng cần test **mọi tổ hợp Cartesian**, nhưng phải tìm những trục có **tương tác ngữ nghĩa**. Pairwise testing có thể giúp phủ cấu hình thông thường, nhưng không thay thế các kịch bản có rủi ro cao như “retry sau commit” hoặc “cache bypass quyền”.

### 15.1. Ma trận kiểm thử tối thiểu cho aspect quan sát

| Kịch bản | Điều cần xác minh |
| --- | --- |
| Một điểm được chọn | Advice chạy đúng theo contract |
| Một điểm không được chọn | Không chạy advice ngoài ý định |
| Hai concrete rules cùng khớp | Số lời gọi và thứ tự biết trước |
| Target trả bình thường | Giá trị trả đúng |
| Target ném ngoại lệ | Loại/cause ban đầu giữ nguyên |
| Advice logging ném lỗi | Hợp đồng xử lý lỗi telemetry đúng |
| Advice trong nested around | Before/after order đúng |
| Target được gọi trực tiếp | Phát hiện đường bypass nếu dùng proxy |
| Pointcut chọn nhiều overload | Không khớp nhầm chữ ký |
| Recursive call | Không tạo advice loop ngoài ý muốn |
| Singleton aspect đồng thời | Không ghi đè state per-request |
| Async method trả future | Không nhầm return future với completion |
| Cấu hình rule không hợp lệ | Bị từ chối rõ, không âm thầm mất guard |
| Artifact sau weaving | Code đang chạy thực sự chứa advice cần thiết |

### 15.2. Chỉ test “có log” là thiếu

Nếu một trace aspect chạy hai lần rồi target cũng chạy hai lần vì advice retry sai, test chỉ assert `contains("TRACE")` vẫn pass. Cần kiểm tra **số lần target được gọi, kết quả, ngoại lệ, tác dụng phụ** và **negative selection**.

## 16. Hiệu năng: đo phần nào?

Một lời gọi AOP có thể gồm:

```text
T_total =
  interception dispatch
  + pointcut/binding checks
  + advice body
  + target execution
  + downstream telemetry/IO
```

Đây là mô hình phân rã tương đối, không có bảo đảm tổng thời gian thực luôn cộng đơn giản dưới concurrency. Để đánh giá:

1. Lấy **baseline** không bật concern tương ứng nhưng cùng workload.
2. Bật từng concern riêng để xác định chi phí.
3. Bật tổ hợp được phép trong cấu hình thật.
4. Đo latency distribution, throughput, allocations, GC, CPU và network nếu phù hợp.
5. Tách chi phí pointcut/dispatch khỏi chi phí I/O logger/metrics.
6. Đo cả **no-match** và **match**, warmup JIT trước benchmark ổn định.
7. So sánh hiệu năng với lợi ích lỗi được tránh và tính nhất quán chính sách.

Không tuyên bố “AOP luôn nhanh/chậm hơn code trực tiếp”. Kết quả phụ thuộc **công nghệ, advice, số điểm và workload**.

### 16.1. Benchmark cần tránh sai số lớn

- Không chạy một lần ngắn rồi kết luận; JIT/warmup ảnh hưởng.
- Không đo benchmark trong khi in hàng nghìn dòng `System.out` rồi quy overhead về pointcut.
- Không so sánh hai nhánh có semantics khác nhau: một nhánh có I/O, nhánh kia không.
- Không bỏ qua concurrency và tỷ lệ điểm thực sự khớp pointcut.
- Không chỉ dùng trung bình nếu tail latency quan trọng.
- Với logging, thử các chế độ backend khỏe/chậm/down theo contract.

### 16.2. Mức tái sử dụng cao có thể tạo “hot-path surprise”

Một pointcut dùng lại khắp ứng dụng có thể vô tình bao cả getter nhỏ được gọi rất nhiều lần. Chỉ cần thêm một phép reflection, allocation hoặc serialize object ở advice, chi phí cộng dồn trở nên đáng kể. Một phương án có thể là **điểm chọn hẹp và sampling** phù hợp, không phải vội thay toàn bộ kiến trúc.

## 17. Góc nhìn bảo mật và rủi ro vận hành

Một concern cắt ngang có thể là **điều kiện bắt buộc**, không phải tùy chọn.

- **Authorization:** không xác minh được quyền thì không tự cho phép. Nếu proxy bị bypass, chính sách không còn được đảm bảo.
- **Audit bắt buộc:** không coi việc target trả bình thường là bằng chứng audit đã được ghi bền vững.
- **Privacy/redaction:** không lưu toàn bộ arguments/target trong singleton aspect nếu chỉ cần tên thao tác.
- **Retry:** không tự lặp effect không idempotent; xử lý trạng thái không chắc chắn sau timeout.
- **Resource cleanup:** dùng owner rõ, finally/AutoCloseable; đừng dựa vào GC.
- **Observability phụ trợ:** nếu fail-open cho exporter, chỉ được phép bỏ tín hiệu hỗ trợ theo hợp đồng; không che lỗi từ target.

Một reusable aspect làm thiếu một kiểm soát bắt buộc ở nhiều nơi là **rủi ro lan truyền lớn**, không phải chỉ lỗi cấu hình nhỏ. Cần có security review và test độ phủ ở điểm thật sự tạo effect.

## 18. Chỉ số định tính để đánh giá một thiết kế

Có thể chấm từng mục **Thấp / Trung bình / Cao**, có giải thích:

| Tiêu chí | Thấp (thuận lợi) | Cao (cần thận trọng) |
| --- | --- | --- |
| **Biến thể hành vi** | Một contract, vài tham số | Nhiều nhánh với semantics khác nhau |
| **Phụ thuộc thứ tự** | Độc lập | Nhiều precedence bắt buộc |
| **Pointcut variability** | Tập điểm ổn định | Wildcard/overload thay đổi liên tục |
| **State coupling** | Local/bất biến | Global, ThreadLocal, nhiều aspect trao đổi state |
| **Chi phí debug** | Trace rõ đường gọi | Chuỗi advice sâu, khó truy nguyên |
| **Blast radius** | Một module/nhóm nghiệp vụ | Nhiều dịch vụ nhạy cảm |
| **Tooling dependency** | Không thêm cơ chế mới | Cần weaving/runtime phức tạp |
| **Chi phí kiểm thử** | Ít nhánh độc lập | Nhiều tổ hợp có thể sai |
| **Khả năng tháo bỏ** | Chuyển về helper dễ | Mã nghiệp vụ phụ thuộc sâu vào AOP |
| **Lợi ích nhất quán** | Ít điểm cần đồng bộ | Nhiều điểm cùng một nghĩa và hay đổi |

Không cộng tổng các nhãn này như một “điểm chất lượng tuyệt đối”. Hãy nhìn **điểm nghẽn**: một rủi ro bảo mật cao có thể khiến thiết kế không phù hợp dù các mục khác rất đẹp.

## 19. Quy trình quyết định sáu bước

### Bước 1 — Viết bất biến, không viết công cụ trước

Ví dụ: “Mọi operation được đánh dấu là nhạy cảm phải được kiểm quyền trước effect; thiếu quyết định thì không được chạy.” Đây là **bất biến**. Chỉ sau đó mới chọn aspect, proxy, domain service hay một cơ chế khác.

### Bước 2 — Kiểm kê các vị trí thực sự cùng contract

Liệt kê **join point dự kiến**, phương thức nào cần áp, loại nào phải loại, dữ liệu đầu vào cần thiết, các nhánh return/throw. Đừng gộp chỉ vì hai đoạn log giống nhau.

### Bước 3 — Chọn cấp tái sử dụng thấp nhất đủ đáp ứng

Nếu một helper thuần giải quyết phần lặp, dùng helper. Nếu cần chặn nhiều join point thật sự, cân nhắc aspect. Nếu cần nhiều specialization cùng skeleton, xem abstract aspect khi ngôn ngữ hỗ trợ.

### Bước 4 — Vẽ luồng điều khiển và trách nhiệm lỗi

Phác họa thứ tự advice, vị trí short-circuit, retry, transaction, audit; chỉ rõ **mỗi thành phần có thể thay return/exception không** và cleanup thuộc owner nào.

### Bước 5 — Ước lượng và kiểm chứng bằng thí điểm

Chọn một nhóm method đại diện. Đo số file thay đổi, effort review, tính dễ debug, precision của pointcut và overhead. Đưa kết quả sang nhóm khác **chỉ khi** contract giống nhau.

### Bước 6 — Quy định điều kiện dừng hoặc đảo ngược

Ví dụ: “Nếu thêm nhóm nghiệp vụ thứ ba phải bổ sung hơn ba flags mới hoặc thay đổi semantics của base, tách abstraction”; “Nếu pointcut không bao phủ đường critical, chuyển guard về ranh giới nghiệp vụ đáng tin”.

Điều kiện dừng giúp tránh tâm lý tiếp tục dùng một framework phức tạp chỉ vì đã đầu tư công sức trước đó.

## 20. So sánh ba trường hợp thực tế về kiến trúc

### Trường hợp A — Chuẩn hóa correlation và logging

Có nhiều method dùng cùng quy tắc correlation ID, giới hạn dữ liệu log và timestamp. Ý nghĩa tương đối đồng nhất.

**Khuyến nghị thiết kế:** một helper/context owner chung và một aspect quan sát hoặc interceptor template có phạm vi rõ; dùng biến cục bộ/context đúng lifecycle. Đo overhead và tránh lưu request trong field dài hạn.

**Lợi ích:** sửa chính sách redaction một nơi, tránh bỏ sót cleanup. **Chi phí:** cấu hình pointcut, propagation qua async và giám sát exporter down.

### Trường hợp B — Cache và authorization

Hai concern đều có dạng around method, nhưng Cache có thể short-circuit còn Auth là điều kiện bắt buộc trước khi đọc dữ liệu nhạy cảm.

**Khuyến nghị thiết kế:** **không** gộp vào một base chỉ vì cả hai có `proceed`. Dùng policies riêng, quyết định order theo threat model và kiểm tra cache-hit path. Nếu target gốc có đường gọi khác, bảo vệ effect ở ranh giới đáng tin.

**Lợi ích:** contract rõ và có thể thay cache mà không thay quyền. **Chi phí:** cần test thứ tự và cache key theo phạm vi quyền.

### Trường hợp C — Audit đã commit và tracing tùy chọn

Cả hai có thể ghi một event, nhưng audit phải có bằng chứng effect đã cam kết; tracing có thể bị drop khi exporter quá tải.

**Khuyến nghị thiết kế:** chia sẻ hàm serialize/format **nếu có cùng schema an toàn**, nhưng giữ **hai chính sách lỗi và lifecycle khác nhau**. Với audit bền vững, cân nhắc outbox/coordinator theo nhu cầu.

**Lợi ích:** tránh biến một lỗi telemetry thành mất audit bắt buộc hoặc ngược lại khiến mọi giao dịch lỗi khi trace sink down.

## 21. Những sai lầm phổ biến

| Sai lầm | Vì sao sai | Cách cải thiện |
| --- | --- | --- |
| “Càng ít dòng càng tốt” | Bỏ qua complexity ẩn trong pointcut/config | Đo cost of change và test |
| Gộp các concern chỉ vì cùng `proceed` | Nghĩa retry/auth/audit khác nhau | Tách theo bất biến |
| Abstract aspect có quá nhiều flags | Tổ hợp cấu hình bùng nổ | Strategy nhỏ/composition |
| Dùng wildcard quá rộng | Match ngoài ý định, overhead, recursion | Pointcut hẹp và negative tests |
| Cho rằng kế thừa quyết định precedence | Trật tự advice là hợp đồng riêng | Test thứ tự/nesting |
| Coi `cflow` tự tạo instance per-request | State có thể chia sẻ sai | Kiểm tra instantiation policy |
| Lưu request trong singleton aspect | Race và memory retention | Local/scoped state |
| Chỉ test success | Không thấy exception/cleanup/short-circuit | Test return, throw, failure |
| Tái sử dụng một fallback cho mọi lỗi | Nuốt guard errors hoặc target errors | Phân loại policy bắt buộc/tùy chọn |
| Coi proxy là weaver toàn ứng dụng | Direct target/self-invocation bypass | Xác minh coverage |
| Coi audit = log | Mất cam kết bền vững, sai trạng thái commit | Contract và durable recording |
| Dùng retry mù để chữa lỗi advice | Tạo effect trùng | Idempotency và đối soát |
| Đo performance bằng log I/O rồi kết luận pointcut chậm | Không cô lập nguyên nhân | Benchmark từng thành phần |
| Không test version/build artifact | Weaving thiếu hoặc thay semantics | Artifact-level integration tests |
| Trì hoãn tách abstraction sai | Sunk cost, nhiều nhánh đặc biệt | Định nghĩa exit criteria |

## 22. Bài tập vận dụng và đáp án

### Bài 1 — Hai điểm có cùng log

Chỉ hai method cùng in `BEGIN` và `END`; method A bắt buộc rethrow lỗi, method B trả fallback. Có nên chia sẻ một around template?

**Đáp án:** Chưa nên gộp contract điều khiển thành một nếu semantics lỗi khác nhau. Có thể chia sẻ **formatter** nhỏ, còn nhánh lỗi phải tách hoặc có policy cực kỳ rõ.

### Bài 2 — Hai mươi method cùng policy

Có 20 method cần cùng một chuẩn che dữ liệu nhạy cảm trước khi log. Có thay đổi policy thường xuyên. Giá trị tái sử dụng chính là gì?

**Đáp án:** Một chính sách redaction thống nhất, giảm sửa sót và có test hợp đồng chung. Pointcut phải bao phủ đúng 20 method, không tự ghi payload nguyên dạng.

### Bài 3 — Overlap

Hai concrete observation aspects dùng chung base, cả hai chọn `invoice.create()`. Có chắc chỉ có một log vì chung base không?

**Đáp án:** Không. Một join point có thể nhận hai advice/concrete instances. Cần xem pointcut intersection và precedence.

### Bài 4 — Chi phí hòa vốn

Trong mô hình giả định, viết riêng tốn `1.5N` giờ, abstraction tốn `10 + 0.5N` giờ. N bao nhiêu thì hai bên bằng nhau?

**Đáp án:** N = 10. Đây chỉ là hòa vốn **chi phí ban đầu theo giả định**, không phải quy tắc quyết định mọi dự án.

### Bài 5 — Thứ tự

`Cache(Authorization(Target))` có thể return từ cache trước khi gọi Auth. Việc gom hai policy vào một class có tự sửa sai đó không?

**Đáp án:** Không. Phải bảo vệ điều kiện quyền ở mọi đường trả dữ liệu, và kiểm tra actual order/coverage; cấu trúc class không tự bảo đảm an toàn.

### Bài 6 — Đánh giá runtime

Một điểm nóng gọi 2 triệu lần/phút, một advice có reflection và serialization. Tái sử dụng cùng advice như các endpoint I/O chậm có ổn không?

**Đáp án:** Phải đo với workload thực, cả match/no-match, allocations và tail latency. Có thể cần pointcut riêng hoặc telemetry sampling, không quyết bằng số nơi reuse.

### Bài 7 — Giữ trạng thái

Một base aspect lưu `lastRequest` cho mỗi concrete child. Có đồng nghĩa mỗi request được cách ly không?

**Đáp án:** Không. Aspect instances có lifecycle riêng; nếu instance shared, field có thể bị nhiều lời gọi dùng chung và giữ request ngoài vòng đời. Dùng local/per-invocation context.

### Bài 8 — Java 17 demo

Vì sao lời gọi `bill-1` có cả BASE và FIN, còn `ship-1` chỉ có BASE?

**Đáp án:** Vì hai Rule cùng khớp prefix `bill-`, nhưng chỉ BASE khớp `ship-1`. Logic `invokeAt` được dùng chung, không tự loại overlap.

### Bài 9 — Lỗi nghiệp vụ

Khi target trong demo ném lỗi `bad`, có được trả `null` để giữ observer “an toàn” không?

**Đáp án:** Không theo contract hiện tại. Observer thuần quan sát phải rethrow lỗi gốc; một fallback là thay đổi semantics và phải được thiết kế riêng.

### Bài 10 — Proxy bypass

Demo gọi `target.run("bill-2")` trực tiếp và không có log. Điều này nói gì về việc dùng reusable AOP qua proxy làm guard bắt buộc?

**Đáp án:** Độ phủ chỉ gồm đường **qua proxy**. Không đặt bảo mật bắt buộc duy nhất ở proxy nếu caller có thể tới effect bằng đường khác; cần kiểm soát ranh giới đáng tin.

### Bài 11 — Tại sao không gộp audit và telemetry?

Cả hai phát event, nhưng audit bắt buộc bền vững còn telemetry cho phép drop. Có nên có một `emit(event)` dùng chung?

**Đáp án:** Có thể dùng chung serializer an toàn nếu contract giống, nhưng **không gộp chính sách delivery/failure**. Nhầm lẫn có thể gây mất audit hoặc làm giao dịch không cần thiết thất bại.

### Bài 12 — Khi nào xóa một abstraction?

Mỗi lần thêm domain lại phải thêm một flag, một thứ tự đặc biệt và nhiều exception handlers riêng. Tái sử dụng còn hiệu quả không?

**Đáp án:** Đây là dấu hiệu trừu tượng sai. Đo chi phí thay đổi và rủi ro rồi cân nhắc tách thành policy riêng, helper thuần hoặc coordinator cho những tương tác bắt buộc.

## 23. Checklist trước khi chốt

1. Các điểm dùng chung có **cùng bất biến** hay chỉ giống cú pháp?
2. Tập join point đã có **positive/negative coverage test** chưa?
3. Cơ chế AOP hỗ trợ đúng loại method call/execution/field/constructor cần áp chưa?
4. Có state nào bị chia sẻ giữa thread/target/request ngoài ý muốn không?
5. Nếu hai aspects cùng khớp, **order và số lần target chạy** có được kiểm chứng?
6. Nếu aspect lỗi, chính sách **fail-open/fail-closed** đúng chức năng chứ?
7. Nếu target lỗi, original result/exception có được bảo toàn?
8. Logic nào đáng đặt ở **helper**, logic nào cần **aspect**, logic nào phải ở **domain coordinator**?
9. Để thêm một domain mới, cần sửa bao nhiêu files/configs/tests?
10. Tác động lên **latency, memory, GC, throughput** đã được đo tại hot path?
11. Khi dependency hoặc compiler/weaver đổi version, có artifact-level test không?
12. Có đường **direct target/self-invocation/async** bypass chính sách không?
13. Có tiêu chí định lượng/định tính để biết khi nào phải tách abstraction?
14. Tài liệu có nêu rõ **những ngữ nghĩa không được tái sử dụng** không?

## 24. Ghi nhớ

- **Tái sử dụng tốt là tái sử dụng một hợp đồng đúng**, không phải tối thiểu số dòng code.
- Trong AOP, **pointcut precision, advice precedence, flow semantics, state lifetime và weaving coverage** là những chi phí thiết kế quan trọng.
- Dùng **helper hoặc strategy** khi chỉ cần chia sẻ thuật toán; dùng **aspect/interceptor** khi có concern thật sự cắt ngang; dùng **coordinator** cho bất biến nghiệp vụ chặt.
- **Abstract aspect** đáng giá khi ngôn ngữ hỗ trợ và các concrete specializations chia sẻ skeleton rõ, không phải mọi mối quan tâm đều nên kế thừa nhau.
- Đừng gộp telemetry, authorization, retry và durable audit chỉ vì chúng đều có `proceed`.
- Một thay đổi dùng chung có thể **giảm lỗi lặp**, đồng thời tăng **blast radius**; cần test, ownership và rollout phù hợp.
- Ra quyết định bằng **chi phí vòng đời, phạm vi đúng, khả năng kiểm thử, độ dễ hiểu và các phép đo thực**, không bằng số lượng classes hay LOC.
- Nếu abstraction ngày càng nhiều flags, exceptions, precedence rules và đường bypass, hãy **đánh giá lại và tách** thay vì tiếp tục đầu tư theo thói quen.


</details>

- [Quay lại đầu trang](#back-to-top)
